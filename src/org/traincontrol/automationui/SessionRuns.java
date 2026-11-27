package org.traincontrol.automationui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.Landing;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.Run;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * A session's runs of plain track, a direction set along one, and the marks the diagrams draw for them (FR-136).
 *
 * The session keeps its fields and its public methods, each a one-line call to a method here.
 */
final class SessionRuns
{
    private final AutonomySession owner;

    SessionRuns(AutonomySession owner)
    {
        this.owner = owner;
    }

    /** The body of `AutonomySession.flowMarks`. */
    Map<TileKey, TileAnnotation.Mark> flowMarks()
    {
        Map<TileKey, TileAnnotation.Mark> out = new LinkedHashMap<>();

        if (owner.reducer == null || owner.graph == null) return out;

        for (GraphReducer.ReducedEdge edge : owner.reducer.getEdges())
        {
            List<GraphReducer.TileStep> path = edge.getPath();

            if (path.isEmpty()) continue;

            // the middle of the run, so the arrow is not crowded against either sensor
            int at = path.size() / 2;

            TileKey tile = path.get(at).getTile();

            // where a train standing here is heading next
            TileKey next = at + 1 < path.size() ? path.get(at + 1).getTile() : edge.getEnd();
            TileKey previous = at > 0 ? path.get(at - 1).getTile() : edge.getStart();

            Side toward = owner.graph.sideTowardNeighbour(tile, next);
            Side from = owner.graph.sideTowardNeighbour(tile, previous);

            if (toward == null || from == null) continue;

            Route route = owner.graph.getRoutes(tile).get(path.get(at).getRouteId());

            if (route == null || !route.touches(toward) || !route.touches(from)) continue;

            Direction direction = route.getA() == toward ? Direction.TOWARD_A : Direction.TOWARD_B;

            TileAnnotation.Mark existing = out.get(tile);

            // the opposing run over the same track: one arrow with two heads, not two arrows
            out.put(tile, existing != null && existing.getDirection() != direction
                ? new TileAnnotation.Mark(route.getA(), route.getB(), Direction.BOTH)
                : new TileAnnotation.Mark(route.getA(), route.getB(), direction));
        }

        return out;
    }

    /** The body of `AutonomySession.runs`. */
    Map<TileKey, Run> runs()
    {
        return owner.graph == null ? Collections.<TileKey, Run>emptyMap() : owner.graph.runs();
    }

    /** The body of `AutonomySession.runLeaders`. */
    Map<TileKey, TileKey> runLeaders()
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Run> entry : runs().entrySet())
        {
            for (TileKey tile : entry.getValue().getTiles()) out.put(tile, entry.getKey());
        }

        return out;
    }

    /** The body of `AutonomySession.setRunDirection`. */
    int setRunDirection(TileKey leader, RouteId routeId, Direction direction, boolean alongTheLine)
    {
        Run run = owner.graph == null ? null : owner.graph.runOf(leader);

        // Not part of a run at all - a lone tile, or a point.  Set it and nothing else.
        if (run == null)
        {
            owner.setDirection(leader, routeId, direction);
            return 1;
        }
        Map<TileKey, Map<RouteId, Direction>> further = alongTheLine
            ? alongTheLine(leader, routeId, direction) : Collections.<TileKey, Map<RouteId, Direction>>emptyMap();
        further.forEach(owner::recordSquare);

        // Both ways and closed mean the same on every tile, so they go on directly.
        if (direction == Direction.BOTH || direction == Direction.NONE)
        {
            owner.setDirection(new LinkedHashSet<>(run.getTiles()), direction);
            return run.getTiles().size() + further.size();
        }

        Route route = owner.graph.getRoutes(leader).get(routeId);
        if (route == null) return 0;
        Side toward = direction == Direction.TOWARD_A ? route.getA() : route.getB();
        Landing landing = owner.graph.landing(leader, toward);

        // Which way along the run the user pointed.
        boolean towardEnd = landing == null
            || !run.getTiles().isEmpty() && landing.getTile().equals(nextAfter(run, leader));

        int changed = owner.recordAlongTheRun(run, towardEnd).size() + further.size();
        return owner.directionsTouched() ? changed : 0;
    }

    /** What a run's new direction carries on to past the run, each road by square: ways it newly allows (MT-1068). */
    Map<TileKey, Map<RouteId, Direction>> alongTheLine(TileKey clicked, RouteId routeId, Direction direction)
    {
        Map<TileKey, Map<RouteId, Direction>> out = new LinkedHashMap<>();
        Route road = owner.graph == null || owner.graph.runOf(clicked) == null ? null
            : owner.graph.getRoutes(clicked).get(routeId);
        if (road == null) return out;
        // Each way along the run, named by the end of the square clicked it leaves by: allowed before, now, newly
        Side[] ends = {road.getA(), road.getB()};
        Landing[] past = new Landing[2];
        boolean[] was = {true, true};
        Set<TileKey> seen = new HashSet<>();
        for (int i = 0; i < 2; i++)
        {
            List<Step> half = new ArrayList<>();
            past[i] = plainTrack(new Step(clicked, routeId, road, ends[1 - i]), half, seen);
            for (Step step : half)
            {
                was[i] &= step.lets(true, false);
                was[1 - i] &= step.lets(false, true);
            }
        }
        boolean[] now = {direction == Direction.BOTH || direction == Direction.TOWARD_A,
            direction == Direction.BOTH || direction == Direction.TOWARD_B};
        boolean[] fresh = {now[0] && !was[0], now[1] && !was[1]};
        for (int i = 0; i < 2 && (fresh[0] || fresh[1]); i++)
        {
            carryOn(past[i], seen, new boolean[] {fresh[i], fresh[1 - i]}, now[i], now[1 - i], out);
        }
        return out;
    }

    /** Walks from a run's end to a station or a split, setting in `plan` each road forbidding a way `need` names. */
    private void carryOn(Landing at, Set<TileKey> seen, boolean[] need, boolean out, boolean back,
        Map<TileKey, Map<RouteId, Direction>> plan)
    {
        while (at != null && !seen.contains(at.getTile()))
        {
            TileKey tile = at.getTile();
            LayoutDiagramComponent part = owner.graph.getTiles().get(tile);
            Side toe = part == null ? null : TilePorts.deriveToe(part.getType(), part.getOrientation());
            List<Step> piece = new ArrayList<>();
            Landing next;
            if (owner.graph.runOf(tile) != null)
            {
                next = plainTrack(entering(tile, at.getEntrySide(), null), piece, seen);
            }
            else if (part != null && part.isFeedback() && !owner.store.isStation(tile))
            {
                // A plain sensor is passed where it lets the way through; its own direction is its window's to set
                Step through = entering(tile, at.getEntrySide(), null);
                if (through == null || !through.lets(need)) return;
                seen.add(tile);
                at = owner.graph.landing(tile, through.out);
                continue;
            }
            else if (toe != null && toe != at.getEntrySide() && !part.isFeedback() && !part.isThreeWay()
                && owner.graph.getRoutes(tile).size() == 2 && owner.graph.crossingPartner(tile) == null)
            {
                // A switch entered by a branch: the one road on to its toe
                piece.add(entering(tile, at.getEntrySide(), toe));
                seen.add(tile);
                next = owner.graph.landing(tile, toe);
            }
            else return;
            if (piece.contains(null)) return;
            // Track that already lets the way is passed as it is; only a switch's blades can refuse a direction
            for (Step step : piece.stream().allMatch(s -> s.lets(need)) ? Collections.<Step>emptyList() : piece)
            {
                if (!TileGraph.directionIsPossible(step.direction(out, back), step.road)) return;
                plan.computeIfAbsent(step.tile, k -> new LinkedHashMap<>()).put(step.roadId, step.direction(out, back));
            }
            at = next;
        }
    }

    /** Adds `first` and the plain track after it to `steps`, to a square no plain track or seen: where it lands. */
    private Landing plainTrack(Step first, List<Step> steps, Set<TileKey> seen)
    {
        for (Step step = first; step != null; )
        {
            steps.add(step);
            seen.add(step.tile);
            Landing next = owner.graph.landing(step.tile, step.out);
            if (next == null || seen.contains(next.getTile()) || owner.graph.runOf(next.getTile()) == null) return next;
            step = entering(next.getTile(), next.getEntrySide(), null);
        }
        return null;
    }

    /** A square's one road entered by a side (and left by `exit` when given), as a step; null if not exactly one. */
    private Step entering(TileKey tile, Side entry, Side exit)
    {
        Step found = null;
        for (Map.Entry<RouteId, Route> each : owner.graph.getRoutes(tile).entrySet())
        {
            Route r = each.getValue();
            if (r.getA() == r.getB() || !r.touches(entry) || exit != null && r.other(entry) != exit) continue;
            if (found != null) return null;
            found = new Step(tile, each.getKey(), r, entry);
        }
        return found;
    }

    /** A square of the line and the road it is crossed by, walking away from the square clicked. */
    private final class Step
    {
        final TileKey tile;
        final RouteId roadId;
        final Route road;
        final Side in;
        final Side out;

        Step(TileKey tile, RouteId roadId, Route road, Side in)
        {
            this.tile = tile;
            this.roadId = roadId;
            this.road = road;
            this.in = in;
            this.out = road.other(in);
        }

        /** Whether trains may cross here each way `need` names: {outward, back}. */
        boolean lets(boolean... need)
        {
            for (int way = 0; way < 2; way++)
            {
                Side to = way == 0 ? out : in;
                if (need[way] && owner.graph.exits(tile, to == out ? in : out).stream()
                    .noneMatch(exit -> exit.getSide() == to)) return false;
            }
            return true;
        }

        /** This road's direction for trains allowed outward, back, or both. */
        Direction direction(boolean outward, boolean back)
        {
            Side toward = outward ? out : in;
            return outward && back ? Direction.BOTH : road.getA() == toward ? Direction.TOWARD_A : Direction.TOWARD_B;
        }
    }

    /**
     * The tile after this one along a run, or the run's far boundary when it is the last.
     */
    private TileKey nextAfter(Run run, TileKey tile)
    {
        int at = run.getTiles().indexOf(tile);

        if (at < 0) return run.getEnd();

        return at + 1 < run.getTiles().size() ? run.getTiles().get(at + 1) : run.getEnd();
    }

    /** The body of `AutonomySession.staticAnnotations`. */
    Map<TileKey, TileAnnotation> staticAnnotations(java.util.Collection<TileKey> squares, boolean arrows,
        boolean allowedToo)
    {
        Map<TileKey, TileAnnotation> out = new LinkedHashMap<>();

        boolean either = arrows || allowedToo;

        for (TileKey tile : squares)
        {
            // NOT A SQUARE THAT FOLLOWS ITS RUN, as the editor draws none there (OB-361)
            boolean follows = either && owner.followsItsRun(tile);

            boolean drawn = either && !follows;

            TileAnnotation annotation = owner.staticAnnotationFor(tile, drawn, arrows && !follows);

            if (annotation != null && drawn)
            {
                // A station open every side says nothing in green, on the diagram and in Station Settings' picture alike
                if (allowedToo) annotation.withAllowedDirections().withoutOpenGreen();

                if (!arrows) annotation.withoutRestrictions();
            }

            out.put(tile, annotation);
        }

        return out;
    }
}
