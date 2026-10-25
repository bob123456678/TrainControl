package org.traincontrol.automation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.Locomotive;

/**
 * Where each standing train's body lies: the walk behind every standing or running train, one train's tail, and
 * whether a tail lies on a piece of track (FR-136).
 *
 * The railway keeps its fields, its monitor and every public and `synchronized` method; each method here is the body
 * of a one-line method there, or a private helper only these call.  Nothing here takes a lock: the walks run under
 * the railway's monitor where their callers hold it, and a call back into the railway re-enters it.
 */
final class StandingTrains
{
    private final Layout owner;

    StandingTrains(Layout owner)
    {
        this.owner = owner;
    }

    /**
     * Where a standing train's road says its tail goes from here: the road's edge ENDING at this square first (a road a
     * train drove runs towards it; when a road passes a square twice the later arrival wins), else the edge STARTING
     * here (a road the operator gave, on a one-way rail laid the other way).  Both by place, so a train turned onto the
     * other lane's copy still follows its road.
     *
     * @return the Point the tail goes back to, or null when the road says nothing here
     */
    private Point cameFromAlong(List<Edge> road, Point here, Set<String> walked)
    {
        if (road == null) return null;

        Point cameFrom = null;

        for (Edge driven : road)
        {
            if (driven.getStart() == null || driven.getEnd() == null) continue;

            if (driven.getEnd().isSamePlaceAs(here) && !walkedAPlaceLike(driven.getStart(), walked))
            {
                cameFrom = driven.getStart();
            }
        }

        if (cameFrom != null) return cameFrom;

        for (Edge driven : road)
        {
            if (driven.getStart() == null || driven.getEnd() == null) continue;

            if (driven.getStart().isSamePlaceAs(here) && !walkedAPlaceLike(driven.getEnd(), walked))
            {
                cameFrom = driven.getEnd();
            }
        }

        return cameFrom;
    }

    /**
     * The square a train's road goes back to from the platform, where that is one of the rails on its arrival side, so
     * a fork right behind the platform is decided by the road; null leaves the side to decide.
     */
    private Point roadBackAtTheFirstHop(String arrivedFrom, List<Edge> arrivedAlong, Point here, List<Edge> back,
        Set<String> walked)
    {
        if (arrivedAlong == null || arrivedFrom == null || back == null) return null;

        Point roadBack = cameFromAlong(arrivedAlong, here, walked);

        if (roadBack == null) return null;

        for (Edge candidate : back)
        {
            Point other = candidate.getStart() == here ? candidate.getEnd() : candidate.getStart();

            if (other == null || !other.isSamePlaceAs(roadBack)) continue;

            String cameInBy = owner.entrySideOf(candidate, here);

            if (cameInBy != null && arrivedFrom.equalsIgnoreCase(cameInBy)) return roadBack;
        }

        return null;
    }

    /** One square, whatever copy of it this is: the block a split square's copies share, or the Point's own name. */
    private static String placeKey(Point point)
    {
        // A turning copy runs on the lane of the copy it turns and is the same road.  A square's two arrival sides are
        // its two ends, which a balloon reaches from one junction by different track: a split, where the tail stops.
        if (point.getBlock() == null) return "point " + point.getName();

        String lane = point.getName() == null ? "" : point.getName().replace(", reverse)", ")").replace(" (reverse)", "");

        return "block " + point.getBlock() + " lane " + lane;
    }

    /**
     * Whether the walk has been on this square under any of its names: `walked` holds Point names and a split square is
     * several Points, so a name test would call a sibling copy new ground.
     */
    private boolean walkedAPlaceLike(Point point, Set<String> walked)
    {
        if (point == null) return false;

        if (walked.contains(point.getName())) return true;

        for (String name : walked)
        {
            Point there = owner.getPoint(name);

            if (there != null && there.isSamePlaceAs(point)) return true;
        }

        return false;
    }

    /** The body of `Layout.walkStandingTrains(Map, Map)`. */
    void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places)
    {
        walkStandingTrains(covered, places, null);
    }

    /** The body of `Layout.walkStandingTrains(Map, Map, Predicate)`. */
    void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places,
        java.util.function.Predicate<Locomotive> which)
    {
        // ONE TAIL PER TRAIN, AND A RUNNING ONE IS ANCHORED WHERE IT IS (MT-438).  Adam: "There is no ambiguity - the
        // tail is certain at departure and shouldn't change."  A locked path reserves every point on it
        // (`Point.reserve`), so a running locomotive occupies several Points at once, and walking a tail from each
        // anchored phantom claims at a destination it has not reached.  A running train's body is at its last MILESTONE
        // and the road behind it is the part of its path already driven; the walk spends the train's length back along
        // that, read generously (the head at the far end of its milestone's square) so `tailHasProvablyPassed` never
        // releases an edge ahead of this walk.
        Set<Locomotive> alreadyWalked = new LinkedHashSet<>();

        for (Point standing : owner.points.values())
        {
            Locomotive loc = standing.getCurrentLocomotive();

            if (loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0) continue;

            if (which != null && !which.test(loc)) continue;

            // ONCE EACH: a train that is not running occupies one Point, so only a running one's reserved Points are
            // walked twice.
            if (!alreadyWalked.add(loc)) continue;

            Point anchor = standing;
            String side = standing.getArrivedFrom();
            List<Edge> road = standing.getArrivedAlong();

            // THE PATH IT IS TAKING, WHICHEVER MAP HOLDS IT YET: `configureAndLockPath` reserves every Point before
            // `activeLocomotives` is written, so in that window the locomotive holds several Points and has no path
            // here (`getActiveAccs` has the same union).  The claims first, see `pathHeldBy`.
            List<Edge> running = owner.pathHeldBy(loc);

            if (running != null && !running.isEmpty())
            {
                Point head = theHeadOfTheRun(loc, running);

                if (head != null)
                {
                    // ANCHORED AT THE HEAD: what that Point records is the road's starting point, not what another
                    // reserved Point carries.
                    anchor = head;
                    side = head.getArrivedFrom();
                    road = head.getArrivedAlong();

                    List<Edge> driven = whatItHasDriven(running, head);

                    if (!driven.isEmpty())
                    {
                        // The road it drove, and the side it came in by at the head taken from the leg it drove to get
                        // there.
                        road = driven;
                        side = owner.entrySideOf(driven.get(driven.size() - 1), head);
                    }
                }
            }

            // EVERY SQUARE THE BODY LIES OVER IS CHARGED, the one it stands on first (OB-278), so a running train
            // anchored at a milestone and a train at rest at its destination are walked alike.
            walkOneTail(anchor, loc, side, road, covered, places);
        }
    }

    /**
     * Where a running train is now: its last milestone, by place (MT-438).  Falls back to the Point of the path's first
     * edge where nothing has been reported yet.
     */
    private Point theHeadOfTheRun(Locomotive loc, List<Edge> path)
    {
        Point reached = owner.lastMilestone(loc);

        // ITS OWN FALLBACK, not `whereTheTrainIs`'s, which would hand back one of the reservations the path just took.
        return reached != null ? reached : path.get(0).getStart();
    }

    /**
     * The part of a path a train has already driven, ending at the head (MT-438); empty where the head is the start of
     * the path.  By PLACE, not object: a run may report a milestone on a different copy of a square than the path
     * names.
     */
    private List<Edge> whatItHasDriven(List<Edge> path, Point head)
    {
        List<Edge> driven = new java.util.ArrayList<>();

        if (head == null) return driven;

        // THE HEAD IS THE START OF THE PATH, so nothing is driven yet - BY IDENTITY ONLY: by place, a first leg that
        // REVERSES IN PLACE ends at another copy of the start square and HAS been driven.
        if (path.get(0) != null && path.get(0).getStart() == head)
        {
            return driven;
        }

        for (Edge leg : path)
        {
            if (leg.getStart() == null || leg.getEnd() == null) break;

            driven.add(leg);

            // IDENTITY FIRST, PLACE SECOND: `isSamePlaceAs` matches every copy, so a path passing a square twice would
            // be truncated at the earlier copy.  The place test stays as fallback, since a milestone can be reported on
            // a different copy.
            if (leg.getEnd() == head) return driven;
        }

        // NO COPY OF IT MATCHED BY IDENTITY, so ask by place - the later match, as `cameFromAlong` does.
        List<Edge> byPlace = new java.util.ArrayList<>();

        for (int at = 0; at < path.size(); at++)
        {
            Edge leg = path.get(at);

            if (leg.getStart() == null || leg.getEnd() == null) break;

            if (leg.getEnd().isSamePlaceAs(head)) byPlace = new java.util.ArrayList<>(path.subList(0, at + 1));
        }

        if (!byPlace.isEmpty()) return byPlace;

        // The head is not on the path (a milestone from an earlier run, or a replaced path): nothing can be said about
        // its way, so the caller keeps what the Point itself records.
        return new java.util.ArrayList<>();
    }

    /**
     * One standing train's tail, walked (OB-228): the body `walkStandingTrains` runs for each train, so a train not
     * standing there yet can be walked by the same code.  The side and road are handed in rather than read off the
     * Point, so the Return Home planner can ask about an imagined train without writing onto the running layout.
     *
     * @param covered filled with every covered edge and this train
     * @param places filled with every claimed place and this train
     */
    private void walkOneTail(Point standing, Locomotive loc, String arrivedFrom, List<Edge> arrivedAlong,
        Map<Edge, Locomotive> covered, Map<String, Locomotive> places)
    {
        walkOneTail(standing, loc, arrivedFrom, arrivedAlong, covered, places, null);
    }

    /** The body of `Layout.walkOneTail(Point, Locomotive, String, List, Map, Map, Map)`. */
    void walkOneTail(Point standing, Locomotive loc, String arrivedFrom, List<Edge> arrivedAlong,
        Map<Edge, Locomotive> covered, Map<String, Locomotive> places, Map<String, Integer> reach)
    {
        int remaining = loc.getTrainLength();

        final Point standingHere = standing;

        Point here = standing;

        Set<String> walked = new LinkedHashSet<>();

        walked.add(here.getName());

        // WHAT THIS TRAIN HAS ALREADY PAID FOR: two hops share the square the walk turned at; charged twice, the tail
        // stops short and the track under its back end is claimed by nobody.
        Set<String> spent = new LinkedHashSet<>();

        while (remaining > 0)
        {
            // EVERY WAY IN, in both directions, because a tail is not directional: the graph encodes facing as one-way
            // edges, and a TURNED train lies across track no incoming edge names (Adam's 2-8-4, turned to face east,
            // has its tail on the westbound rail).  So every edge touching this point is a candidate and `arrivedFrom`
            // picks.
            List<Edge> back = owner.getNeighborsAndIncoming(here);

            if (back == null || back.isEmpty()) break;

            Edge segment = null;

            if (here == standingHere && arrivedFrom != null)
            {
                // THE SIDE IT CAME IN BY, for the first hop only; past that the deterministic rule below takes over.
                // AND THE ROAD, where more than one rail comes in on that side: a fork right behind the platform is
                // asked about.
                Point roadBack = roadBackAtTheFirstHop(arrivedFrom, arrivedAlong, here, back, walked);

                // The first rail arriving by that side, kept in case none comes from a plain copy (below).
                Edge arriving = null;

                for (Edge candidate : back)
                {
                    Point other = candidate.getStart() == here
                        ? candidate.getEnd() : candidate.getStart();

                    if (other == null) continue;

                    // THE BUILD'S SIDE where there is one (`entrySideOf`): the written value and this comparison must
                    // come from the same place.
                    if (roadBack != null && !other.isSamePlaceAs(roadBack)) continue;

                    String cameInBy = owner.entrySideOf(candidate, here);

                    if (cameInBy == null
                        || !arrivedFrom.equalsIgnoreCase(cameInBy))
                    {
                        continue;
                    }

                    // AND THE COPY THAT ARRIVES HERE, where the rail is written both ways: a rail is two `Edge`
                    // objects, and at a berth both can report the same way in.  `getNeighborsAndIncoming` lists
                    // OUTGOING ones first, and the copy running away from the berth has the track behind and never the
                    // berth itself, leaving the square a train stands on claimed by nobody.  The train arrived along
                    // the copy that ENDS here; the other is still taken when there is no arriving one, as at a square a
                    // train was turned on.
                    //
                    // AND FROM THE NEIGHBOR'S PLAIN COPY: where trains may turn, its plain and turning copies both have
                    // a rail in here, and the turning copy sorts first by name but every rail from it leads back here,
                    // so the walk would stop at it.  The plain copy first, the rule `copyFacing` and `placementCopy`
                    // use.
                    if (candidate.getEnd() == here)
                    {
                        if (isPlainCopy(candidate.getStart()))
                        {
                            segment = candidate;
                            arriving = null;

                            break;
                        }

                        if (arriving == null) arriving = candidate;

                        continue;
                    }

                    if (segment == null) segment = candidate;
                }

                // No arriving rail from a plain copy: the first arriving rail, as before, over one running away.
                if (arriving != null) segment = arriving;

                // NO ROAD, AND THE RAILS BY THAT SIDE PART BEHIND THE PLATFORM: STOP AT THE SWITCH (MT-477; Adam: "stop
                // at the switch").  The side rule took the first rail and laid the tail up it, claiming track the train
                // may not be on; so the squares every such rail shares are claimed, up to and including the switch.
                if (roadBack == null)
                {
                    // Every rail arriving by that side - gathered apart from the loop above, which stops at the first.
                    List<Edge> arrivingBySide = new ArrayList<>();

                    for (Edge candidate : back)
                    {
                        if (candidate.getEnd() != here || candidate.getStart() == null) continue;

                        String cameInBy = owner.entrySideOf(candidate, here);

                        if (cameInBy != null && arrivedFrom.equalsIgnoreCase(cameInBy)) arrivingBySide.add(candidate);
                    }

                    if (claimUpToWhereTheRailsPart(arrivingBySide, loc, places, spent, remaining, reach)) break;
                }

                // Recorded, but naming a side no track leaves by (stale after an edit): guessing would block the wrong
                // rail.
                if (segment == null) break;
            }
            else
            {
                // THE FORK RULE, for every later hop and for a train nobody told us about.  One way back means the tail
                // certainly lies there; several means the graph cannot say, and blocking either would stop a train that
                // could have run.  Counted over DISTINCT neighbors: a square joined to one other by a pair of one-way
                // rails is one way back.
                Set<String> neighbours = new LinkedHashSet<>();

                for (Edge candidate : back)
                {
                    Point other = candidate.getStart() == here
                        ? candidate.getEnd() : candidate.getStart();

                    if (other == null) continue;

                    // NOT THE WAY WE CAME: counting it would make every ordinary tile a fork.  BY PLACE: the walk has
                    // been on a split square if it has been on any of its copies.
                    if (walkedAPlaceLike(other, walked)) continue;

                    // COUNTED BY PLACE.  Adam's rule is to stop where the track SPLITS ("end locking at the switch"); a
                    // square's lane copy and turning copy are one piece of track.
                    if (neighbours.add(placeKey(other))) segment = candidate;
                }

                // PAST A JUNCTION, THE ROAD IT CAME IN ON (MT-335).  A train that was DRIVEN here follows the route it
                // arrived along, through the junction where the fork rule would stop: the edge that ended where the
                // walk is now, and the candidate reaching back to where it started, matched by place because the walk
                // may stand on a different copy.  A train with no road (placed by hand, or answered Not Known) keeps
                // the fork rule; a hand-placed train can be given one (behaviour.md 5c, FR-085).
                if (neighbours.size() > 1 && arrivedAlong != null)
                {
                    // Where the road says it came from - see `cameFromAlong`, shared with the first hop.
                    Point cameFrom = cameFromAlong(arrivedAlong, here, walked);

                    if (cameFrom != null)
                    {
                        for (Edge candidate : back)
                        {
                            Point other = candidate.getStart() == here
                                ? candidate.getEnd() : candidate.getStart();

                            if (other != null && other.isSamePlaceAs(cameFrom))
                            {
                                segment = candidate;

                                neighbours.clear();
                                neighbours.add(placeKey(other));

                                break;
                            }
                        }
                    }
                }

                if (neighbours.size() != 1) break;

                // AT THE SQUARE IT STANDS ON, THE RAIL THAT ARRIVES THERE: `getNeighborsAndIncoming` lists rails
                // LEAVING a Point first, whose places begin after the standing square, so it would be neither claimed
                // nor spent and the tail would reach one square further back than the train lies.  The arriving rail is
                // read from the end, the square spent first.
                if (here == standingHere && segment != null && segment.getEnd() != here)
                {
                    Point towards = segment.getStart() == here ? segment.getEnd() : segment.getStart();

                    Edge fromATwin = null;

                    for (Edge candidate : back)
                    {
                        if (candidate.getEnd() == here && candidate.getStart() != null
                            && candidate.getStart().isSamePlaceAs(towards))
                        {
                            // THE PLAIN COPY FIRST, as the side rule above takes it.
                            if (isPlainCopy(candidate.getStart()))
                            {
                                segment = candidate;
                                fromATwin = null;

                                break;
                            }

                            if (fromATwin == null) fromATwin = candidate;
                        }
                    }

                    if (fromATwin != null) segment = fromATwin;
                }
            }

            // THE SQUARE IT STANDS ON, WHERE THE RAIL TAKEN DOES NOT ARRIVE AT IT (OB-278 spends it by reading the
            // first hop's places from the end, which holds it only when the hop arrives at this copy).  A train turned
            // where it stands and re-stood on the other arrival's copy has no rail arriving by that side, so the square
            // is read off a rail that does arrive here, claimed and spent before anything behind it.
            //
            // ONLY WHERE THAT PLACE IS PROVABLY THIS SQUARE: a hand-written graph's places are the track alone, so its
            // last place is the rail behind, and spending it as "the square" would use up the train on track not yet
            // walked.  A split square's copies carry the square as their block.
            if (here == standingHere && segment.getEnd() != here && here.getBlock() != null)
            {
                for (Edge arriving : owner.getIncomingEdges(here))
                {
                    if (arriving.getEnd() != here) continue;

                    List<String> in = arriving.getPlaceIds();
                    List<Integer> inSpans = arriving.getPlaceLengths();

                    if (in.isEmpty() || in.size() != inSpans.size()) continue;

                    String own = in.get(in.size() - 1);

                    if (!Layout.isTheSquareOf(own, here)) continue;

                    reachedAt(reach, own, loc.getTrainLength() - remaining);

                    places.put(own, loc);

                    if (spent.add(own)) remaining -= Math.max(0, inSpans.get(inSpans.size() - 1));

                    break;
                }

                // A train no longer than its own square lies on nothing behind it.
                if (remaining <= 0) break;
            }

            // THE MEASUREMENT RULE.  Nothing can be said about an unmeasured segment.  One answered 0 throughout is
            // measured, at no length (`Edge.isMeasured`): claimed, free, and the walk goes on behind it, which lets the
            // route in count past it.
            //
            // `getLength()` is the path PLUS the square the edge arrives at, which on the first hop is the one the
            // train stands on and counts (OB-278).  So a berth measured on an unmeasured approach is a measured
            // segment: a train that fits is spent there and claims nothing behind (the refusing side
            // `whyABerthCannotHoldIt` takes the same approach).
            if (segment.getLength() <= 0 && !segment.isMeasured()) break;

            // BOTH DIRECTIONS OF THE SAME RAIL: the graph writes a rail as two `Edge` objects and `isPathClear` looks
            // the covered set up by Edge identity, so covering A -> B alone left B -> A clear (`behaviour.md` 5c: a
            // tail fouls the rail whichever way traffic runs).  On a built graph this loop seldom matches, since the
            // two directions usually run between different COPIES of its squares; the reverse rail is refused by place
            // instead (`anotherTailOn`).
            covered.put(segment, loc);

            for (Edge sameRail : owner.edges.values())
            {
                if (sameRail == segment) continue;

                if (sameRail.getStart() == segment.getEnd()
                    && sameRail.getEnd() == segment.getStart())
                {
                    covered.put(sameRail, loc);
                }
            }

            // AND HOW FAR ALONG IT THE TAIL REACHES (OB-207): spend the remaining length across the edge's places from
            // the end the tail comes in by.  A place is claimed BEFORE its length is spent, so an unmeasured place the
            // train lies over is claimed too.
            List<String> ids = segment.getPlaceIds();

            List<Integer> spans = segment.getPlaceLengths();

            // What the places walk charged: negative until they are walked, so the edge's own length stands in for a
            // configuration that does not carry them.
            int chargedHere = -1;

            if (!ids.isEmpty() && ids.size() == spans.size())
            {
                boolean fromTheEnd = (segment.getEnd() == here);

                int left = remaining;

                chargedHere = 0;

                for (int step = 0; step < ids.size(); step++)
                {
                    int at = fromTheEnd ? ids.size() - 1 - step : step;

                    reachedAt(reach, ids.get(at), loc.getTrainLength() - left);

                    places.put(ids.get(at), loc);

                    // THE SQUARE THE TRAIN STANDS ON IS SPENT FIRST, like every square behind it (OB-278; Adam: "the 2
                    // length tile with the s88 consumes 2 units of the train").  The station's SIZE is the allowance
                    // `whyTooLongForThisRoute` asks; the length measured on the square is rail.  AND NOT TWICE: the
                    // square the walk turned at is the end of the last edge and a place on this one.
                    if (!spent.add(ids.get(at))) continue;

                    left -= Math.max(0, spans.get(at));

                    chargedHere += Math.max(0, spans.get(at));

                    if (left <= 0) break;
                }
            }

            // ONE BUDGET, NOT TWO: what the finer places walk charged IS what the hop costs; the edge's own length is
            // kept for a configuration that carries no places.
            remaining -= chargedHere >= 0 ? chargedHere : segment.getLength();

            Point next = segment.getStart() == here ? segment.getEnd() : segment.getStart();

            // A loop of track would otherwise be walked for ever by a long enough train.
            if (next == null || !walked.add(next.getName())) break;

            here = next;
        }
    }

    /**
     * The tail a train would leave standing at the end of this road, walked with the road's last rail as its side
     * (OB-228).
     */
    void tailAlong(Point standing, Locomotive loc, List<Edge> road, Map<Edge, Locomotive> covered,
        Map<String, Locomotive> places)
    {
        if (standing == null || loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0) return;

        if (road == null || road.isEmpty()) return;

        walkOneTail(standing, loc, owner.entrySideOf(road.get(road.size() - 1), standing), road, covered, places);
    }

    /** The body of `Layout.anotherTailOn`. */
    static Locomotive anotherTailOn(Edge edge, Locomotive mover, Map<String, Locomotive> claimed)
    {
        for (String place : edge.getPlaceIds())
        {
            Locomotive on = claimed.get(place);

            if (on != null && !on.equals(mover)) return on;
        }

        return null;
    }

    /** The body of `Layout.tailLiesOn`. */
    static boolean tailLiesOn(Edge edge, Locomotive who, Map<String, Locomotive> claimed)
    {
        for (String place : edge.getPlaceIds())
        {
            if (who.equals(claimed.get(place))) return true;
        }

        return false;
    }

    /** The body of `Layout.claimUpToWhereTheRailsPart`. */
    static boolean claimUpToWhereTheRailsPart(List<Edge> rails, Locomotive loc, Map<String, Locomotive> places,
        Set<String> spent, int remaining, Map<String, Integer> reach)
    {
        Set<List<String>> roads = new LinkedHashSet<>();

        for (Edge rail : rails)
        {
            List<String> ids = rail.getPlaceIds();

            if (ids.isEmpty() || ids.size() != rail.getPlaceLengths().size() || !rail.isMeasured()) return false;

            roads.add(ids);
        }

        if (roads.size() < 2) return false;

        List<String> ids = rails.get(0).getPlaceIds();
        List<Integer> spans = rails.get(0).getPlaceLengths();

        int left = remaining;

        for (int step = 0; step < ids.size() && left > 0; step++)
        {
            int at = ids.size() - 1 - step;

            String place = ids.get(at);

            for (List<String> road : roads)
            {
                if (road.size() <= step || !road.get(road.size() - 1 - step).equals(place)) return true;
            }

            reachedAt(reach, place, loc.getTrainLength() - left);

            places.put(place, loc);

            if (spent.add(place)) left -= Math.max(0, spans.get(at));
        }

        return true;
    }

    /**
     * Records how far behind the head a claimed place begins, the first time it is claimed (OB-294); `reach` is null
     * when nobody asked.
     */
    private static void reachedAt(Map<String, Integer> reach, String place, int behindTheHead)
    {
        if (reach != null && !reach.containsKey(place)) reach.put(place, behindTheHead);
    }

    /**
     * Whether a Point is a copy trains pass through without turning.  The build emits the turning copy of a square as a
     * terminus, so this tells a plain copy from its turning twin (as `AutonomySession.copyFacing` and `placementCopy`
     * do).
     */
    private static boolean isPlainCopy(Point point)
    {
        return point != null && !point.isTerminus() && !point.isReversing();
    }
}
