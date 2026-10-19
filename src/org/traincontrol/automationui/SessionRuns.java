package org.traincontrol.automationui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * The session keeps its fields and its public methods; each public method here is one line there.
 */
final class SessionRuns
{
    private final AutonomySession owner;

    /**
     * @param owner the session whose graph the runs are on
     */
    SessionRuns(AutonomySession owner)
    {
        this.owner = owner;
    }

    /**
     * The body of `AutonomySession.flowMarks`, whose javadoc says what it does.
     */
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

    /**
     * The body of `AutonomySession.runs`, whose javadoc says what it does.
     */
    Map<TileKey, Run> runs()
    {
        return owner.graph == null ? Collections.<TileKey, Run>emptyMap() : owner.graph.runs();
    }

    /**
     * The body of `AutonomySession.runLeaders`, whose javadoc says what it does.
     */
    Map<TileKey, TileKey> runLeaders()
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Run> entry : runs().entrySet())
        {
            for (TileKey tile : entry.getValue().getTiles())
            {
                out.put(tile, entry.getKey());
            }
        }

        return out;
    }

    /**
     * The body of `AutonomySession.setRunDirection`, whose javadoc says what it does.
     */
    int setRunDirection(TileKey leader, RouteId routeId, Direction direction)
    {
        Run run = runs().get(leader);

        // Not part of a run at all - a lone tile, or a point.  Set it and nothing else.
        if (run == null)
        {
            owner.setDirection(leader, routeId, direction);
            return 1;
        }

        // Both ways and closed mean the same on every tile, so they go on directly.
        if (direction == Direction.BOTH || direction == Direction.NONE)
        {
            owner.setDirection(new LinkedHashSet<>(run.getTiles()), direction);
            return run.getTiles().size();
        }

        Route route = owner.graph.getRoutes(leader).get(routeId);

        if (route == null) return 0;

        Side toward = direction == Direction.TOWARD_A ? route.getA() : route.getB();

        Landing landing = owner.graph.landing(leader, toward);

        // Which way along the run the user pointed.  The run's OWN tiles are walked, not a path re-derived from its two ends
        // (see applyOneWay); the boundaries say which way the first and last tiles go, a null boundary (track that simply
        // stops) is left out, and the tile beside it is still recorded, from its road (OB-361).
        List<TileKey> path = new ArrayList<>();

        if (run.getStart() != null) path.add(run.getStart());

        int first = path.size();

        path.addAll(run.getTiles());

        int last = path.size() - 1;

        if (run.getEnd() != null) path.add(run.getEnd());

        boolean towardEnd = landing == null
            || !run.getTiles().isEmpty() && landing.getTile().equals(nextAfter(run, leader));

        if (!towardEnd)
        {
            Collections.reverse(path);

            int was = first;

            first = path.size() - 1 - last;
            last = path.size() - 1 - was;
        }

        int changed = owner.recordAlong(path, first, last).size();

        return owner.directionsTouched() ? changed : 0;
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

    /**
     * The body of `AutonomySession.staticAnnotations`, whose javadoc says what it does.
     */
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
                if (allowedToo) annotation.withAllowedDirections();

                if (!arrows) annotation.withoutRestrictions();
            }

            out.put(tile, annotation);
        }

        return out;
    }
}
