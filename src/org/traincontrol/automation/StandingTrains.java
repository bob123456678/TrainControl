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

    /**
     * @param owner the railway whose trains these walk
     */
    StandingTrains(Layout owner)
    {
        this.owner = owner;
    }

    /**
     * Where a standing train's road says its tail goes from here.
     *
     * The road's edge ENDING at this square first: a road a train drove runs towards it, so that edge names where it
     * came from - and when a road passes a square twice, the later arrival wins.  Only where no edge ends here, the edge
     * STARTING here: a road the operator gave is made of the rails that exist, and on a one-way rail laid the other way
     * the edge starts at the square.  Both by place, so a train turned onto the other lane's copy still follows the road
     * it drove in on the first.
     *
     * @param road the train's road
     * @param here the square the walk has reached
     * @param walked the names walked so far
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
     * The square a train's road goes back to from the platform, where that is one of the rails on its arrival side
     * - so a fork right behind the platform is decided by the road, and a road that names nothing there
     * leaves the side to decide as it always has.
     *
     * @param arrivedFrom the side the train came in by
     * @param arrivedAlong the road it came along
     * @param here the Point it stands on - the walk's first hop
     * @param back every edge touching it
     * @param walked the names walked so far
     * @return the square to go back to, or null for the side alone
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

    /**
     * One square, whatever copy of it this is: the block a split square's copies share, or the Point's own name.
     *
     * @param point a Point
     * @return the key
     */
    private static String placeKey(Point point)
    {
        // THE SQUARE AND THE LANE.  A turning copy runs on the lane of the copy it turns - "X (eastbound,
        // reverse)" beside "X (eastbound)" - and is the same road.  The copies for a square's two arrival sides are its
        // two ends, which a balloon reaches from one junction by different track: a split, where the tail stops.
        if (point.getBlock() == null) return "point " + point.getName();

        String lane = point.getName() == null ? "" : point.getName().replace(", reverse)", ")").replace(" (reverse)", "");

        return "block " + point.getBlock() + " lane " + lane;
    }

    /**
     * Whether the walk has already been on this square, under any of its names.
     *
     * `walked` holds Point names, and a split square is several Points - so a name test says a copy the walk never
     * stood on is new ground when its sibling is where the walk just came from.
     *
     * @param point a Point
     * @param walked the names walked so far
     * @return whether it, or another copy of its square, has been walked
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

    /**
     * The body of `Layout.walkStandingTrains(Map, Map)`, whose javadoc says what it does.
     */
    void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places)
    {
        walkStandingTrains(covered, places, null);
    }

    /**
     * The body of `Layout.walkStandingTrains(Map, Map, Predicate)`, whose javadoc says what it does.
     */
    void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places,
        java.util.function.Predicate<Locomotive> which)
    {
        // ONE TAIL PER TRAIN, AND A RUNNING ONE IS ANCHORED WHERE IT IS (MT-438).  Adam: *"There is no ambiguity - the tail is
        // certain at departure and shouldn't change.  You also know which way the train went when it started running.  Just
        // unlock the rest of the diagram once the tail by length is far enough away."*
        //
        // **A locked path reserves every point on it** (`Point.reserve`, which deliberately does not sweep, because that
        // reservation holds a junction behind the train against a second train reaching it another way), so while a run is
        // going the locomotive is the occupant of several Points at once; walking a tail from EVERY one of them anchored
        // phantom claims at the destination it has not reached and at a junction it passed ten minutes ago, each choosing its
        // road from whatever arrival side that Point carries.  A train has one body in one place: for a running locomotive that
        // is its last MILESTONE, and the road behind it is the part of its path it has already driven.  The walk then spends
        // the train's length back along that road and stops, the arithmetic `tailHasProvablyPassed` uses to hand an edge back,
        // read generously here (the head at the far end of its milestone's square, where the release does not count that
        // square until the next sensor answers), so the release is never ahead of this walk.
        Set<Locomotive> alreadyWalked = new LinkedHashSet<>();

        for (Point standing : owner.points.values())
        {
            Locomotive loc = standing.getCurrentLocomotive();

            if (loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0) continue;

            if (which != null && !which.test(loc)) continue;

            // ONCE EACH.  A train that is NOT running occupies exactly one Point - `setLocomotive`
            // sweeps it off every other - so this changes nothing for a standing train; it is the
            // reserved Points of a running one that this stops walking twice.
            if (!alreadyWalked.add(loc)) continue;

            Point anchor = standing;
            String side = standing.getArrivedFrom();
            List<Edge> road = standing.getArrivedAlong();

            // THE PATH IT IS TAKING, WHICHEVER MAP HOLDS IT YET: `configureAndLockPath` claims `takingPath` and reserves
            // every Point on the road before it returns, and `activeLocomotives` is written only afterwards, so in that window the
            // locomotive holds several Points and has no path here, and the one-tail-per-train rule would walk ONE arbitrary
            // reservation and leave its real tail claimed by nobody.  `getActiveAccs` has the same union for the same window
            //.  The claims first, see `pathHeldBy`.
            List<Edge> running = owner.pathHeldBy(loc);

            if (running != null && !running.isEmpty())
            {
                Point head = theHeadOfTheRun(loc, running);

                if (head != null)
                {
                    // ANCHORED AT THE HEAD, and what that Point itself records is the starting point for the road, not what some
                    // other reserved Point happens to carry.
                    anchor = head;
                    side = head.getArrivedFrom();
                    road = head.getArrivedAlong();

                    List<Edge> driven = whatItHasDriven(running, head);

                    if (!driven.isEmpty())
                    {
                        // The road it drove, and the side it came in by at the head taken from the leg
                        // it drove to get there rather than from whatever an earlier arrival left.
                        road = driven;
                        side = owner.entrySideOf(driven.get(driven.size() - 1), head);
                    }
                }
            }

            // EVERY SQUARE THE BODY LIES OVER IS CHARGED, the one it stands on first (OB-278; OB-244 asked it of the milestone), so
            // a running train anchored at a milestone and a train at rest at its destination are walked alike.
            walkOneTail(anchor, loc, side, road, covered, places);
        }
    }

    /**
     * Where a running train is now: its last milestone, by place (MT-438).
     *
     * The milestones are the Points its own run has reported reaching, so the last one is the head.
     * Falls back to the Point of the path's first edge where nothing has been reported yet - a train
     * that has been given a path and has not reached anything is still at the start of it.
     *
     * @param loc the locomotive
     * @param path the path it is running
     * @return the Point to anchor its tail at, or null when neither can be had
     */
    private Point theHeadOfTheRun(Locomotive loc, List<Edge> path)
    {
        Point reached = owner.lastMilestone(loc);

        // ITS OWN FALLBACK, and not `whereTheTrainIs`'s: a train given a path that has reported nothing is at the start of that
        // path, and asking which Point holds it would hand back one of the reservations that path has just taken.
        return reached != null ? reached : path.get(0).getStart();
    }

    /**
     * The part of a path a train has already driven, ending at the head (MT-438).
     *
     * **By PLACE, not by object.** A square is several Points and a run may report a milestone on a
     * different copy of one than the path names - which is the same reason `cameFromAlong` compares
     * this way.
     *
     * @param path the whole path
     * @param head where the train is now
     * @return the edges up to and including the one that ends at the head, or empty where the head is
     *         the start of the path
     */
    private List<Edge> whatItHasDriven(List<Edge> path, Point head)
    {
        List<Edge> driven = new java.util.ArrayList<>();

        if (head == null) return driven;

        // THE HEAD IS THE START OF THE PATH, so nothing has been driven yet - AND BY IDENTITY ONLY: by place,
        // a first leg that REVERSES IN PLACE ends at another copy of the start square, so the head is a different Point on the
        // same piece of track and the train HAS driven that leg; answering "nothing driven" there sends the caller back to the
        // stale road the Point recorded on an earlier run (MT-438).
        if (path.get(0) != null && path.get(0).getStart() == head)
        {
            return driven;
        }

        for (Edge leg : path)
        {
            if (leg.getStart() == null || leg.getEnd() == null) break;

            driven.add(leg);

            // IDENTITY FIRST, PLACE SECOND: `isSamePlaceAs` matches every copy of a square, so a path that passes one
            // twice would be truncated at the EARLIER copy and the side read off the wrong leg.  The place test stays as the
            // fallback, because a milestone can be reported on a different copy than the path names (`cameFromAlong` compares
            // this way, and its javadoc says the LATER arrival wins).
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

        // The head is not on the path at all (a milestone from an earlier run, or a path replaced underneath this one): nothing
        // can be said about which way it came, so nothing is returned and the caller keeps what the Point itself records.
        return new java.util.ArrayList<>();
    }

    /**
     * One standing train's tail, walked (OB-228) - the body `walkStandingTrains` runs for each train, lifted out so a train that
     * is not standing there yet can be walked by the same code.
     *
     * The Return Home planner puts trains down in a plan before any of them moves, and asked only the railway as it is it could
     * not see the tail a moved train would leave.  Handing the side and road in, rather than reading them off the Point, is what
     * lets the planner ask about a train it has only imagined, without writing anything onto the running layout.
     *
     * @param standing the Point the train stands on
     * @param loc the train
     * @param arrivedFrom the side it came in by, or null
     * @param arrivedAlong the road it came along, or null
     * @param covered filled with every covered edge and this train
     * @param places filled with every claimed place and this train
     */
    private void walkOneTail(Point standing, Locomotive loc, String arrivedFrom, List<Edge> arrivedAlong,
        Map<Edge, Locomotive> covered, Map<String, Locomotive> places)
    {
        walkOneTail(standing, loc, arrivedFrom, arrivedAlong, covered, places, null);
    }

    /**
     * The body of `Layout.walkOneTail(Point, Locomotive, String, List, Map, Map, Map)`, whose javadoc says what it does.
     */
    void walkOneTail(Point standing, Locomotive loc, String arrivedFrom, List<Edge> arrivedAlong,
        Map<Edge, Locomotive> covered, Map<String, Locomotive> places, Map<String, Integer> reach)
    {
        int remaining = loc.getTrainLength();

        final Point standingHere = standing;

        Point here = standing;

        Set<String> walked = new LinkedHashSet<>();

        walked.add(here.getName());

        // WHAT THIS TRAIN HAS ALREADY PAID FOR: two hops share the square the walk turned at (the end of one edge, a
        // place on the next); charged twice, the tail stops short of where it really reaches and the track under its back end
        // is claimed by nobody, the permitting side of the guard between two trains.
        Set<String> spent = new LinkedHashSet<>();

        while (remaining > 0)
        {
            // EVERY WAY IN, in both directions, because a tail is not directional.  The graph encodes facing as one-way edges, so
            // "incoming" means "a train travelling this way could arrive by it", and a train that has been TURNED lies across
            // track no incoming edge names (Adam's 2-8-4, turned to face east having arrived from the west, has its tail on the
            // westbound rail, an outgoing edge of the copy it stands on).  So the candidates are every edge that touches this
            // point, and `arrivedFrom` picks between them; a train lying across a rail fouls it whichever way traffic runs.
            List<Edge> back = owner.getNeighborsAndIncoming(here);

            if (back == null || back.isEmpty()) break;

            Edge segment = null;

            if (here == standingHere && arrivedFrom != null)
            {
                // THE SIDE IT CAME IN BY, which is the whole point of recording it.  Only the first hop can be chosen this way;
                // past that the train is somewhere it never stopped, and the deterministic rule below takes over.
                //
                // AND THE ROAD, where the train has one and more than one rail comes in on that side: a fork right
                // behind the platform is asked about, and the side alone took whichever rail it met first.
                Point roadBack = roadBackAtTheFirstHop(arrivedFrom, arrivedAlong, here, back, walked);

                // The first rail arriving by that side, kept in case none comes from a plain copy (below).
                Edge arriving = null;

                for (Edge candidate : back)
                {
                    Point other = candidate.getStart() == here
                        ? candidate.getEnd() : candidate.getStart();

                    if (other == null) continue;

                    // THE BUILD'S SIDE where there is one - see `entrySideOf`: the written value and this comparison have to come
                    // from the same place (this asked where the neighbour LIES and the doors offered the side the track comes in
                    // by, which differ on a curve).
                    if (roadBack != null && !other.isSamePlaceAs(roadBack)) continue;

                    String cameInBy = owner.entrySideOf(candidate, here);

                    if (cameInBy == null
                        || !arrivedFrom.equalsIgnoreCase(cameInBy))
                    {
                        continue;
                    }

                    // AND THE COPY THAT ARRIVES HERE, where the rail is written both ways: a piece of rail is two `Edge`
                    // objects, one per direction, and at a berth both can report the same way in.  `getNeighborsAndIncoming` lists
                    // the OUTGOING ones first, and an edge's places are the path plus the square it ARRIVES at, so the copy running
                    // away from the berth has the track behind and never the berth itself: the square a train was standing on was
                    // claimed by nobody (seen at TopMainR0Park).  The train arrived along the copy that ENDS here, which is what
                    // `arrivedFrom` records, and which carries the standing square, the first place the body is spent on.  The other
                    // copy is still taken when there is no arriving one, as at a square a train has been turned on: it lies across
                    // that rail whichever way traffic runs, and half an answer beats none.
                    //
                    // AND FROM THE NEIGHBOUR'S PLAIN COPY, where its turning twin also arrives by this side.  At a
                    // neighbour trains may turn at, its plain copy and its turning copy both have a rail in here, and
                    // `getNeighborsAndIncoming` sorts them by name (a turning copy is named by the opposite heading plus ", reverse",
                    // so it came first whenever the plain heading was southbound or westbound).  Every rail from a turning copy leads
                    // back here, so the walk would stop at it.  The plain copy first, the rule `copyFacing` and `placementCopy` use.
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

                // NO ROAD, AND THE RAILS BY THAT SIDE PART BEHIND THE PLATFORM: STOP AT THE SWITCH (MT-477; Adam: "stop at the
                // switch").  Every other fork stops the tail where the roads part ("end locking at the switch"), but with no road
                // to follow, the side rule took the first rail by that side and laid the tail up it, claiming track the train may
                // not be on and none of the rail it may be on.  So the squares every such rail shares are claimed, up to and
                // including the switch, and the walk stops there.
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

                // Recorded, but naming a side no track leaves by - a stale value after an edit.
                // Nothing can be said, and guessing would block the wrong rail.
                if (segment == null) break;
            }
            else
            {
                // THE FORK RULE, for every hop after the first and for a train nobody has told us about.  One way means the tail
                // certainly lies there; several means the graph cannot say which, and blocking either would stop a train that
                // could have run.  Counted over DISTINCT neighbours rather than edges: a square joined to one other by a pair of
                // one-way rails is still one way back.
                Set<String> neighbours = new LinkedHashSet<>();

                for (Edge candidate : back)
                {
                    Point other = candidate.getStart() == here
                        ? candidate.getEnd() : candidate.getStart();

                    if (other == null) continue;

                    // NOT THE WAY WE CAME: every point on a straight run has two neighbours, and counting the one just walked would
                    // make every ordinary tile look like a fork.  BY PLACE: a split square's copies are one square,
                    // and the walk has been on it if it has been on any of them.
                    if (walkedAPlaceLike(other, walked)) continue;

                    // COUNTED BY PLACE.  Adam's rule is to stop where the track SPLITS - "end locking at the
                    // switch" - and a square's lane copy and turning copy are one piece of track under two names, not a split.
                    if (neighbours.add(placeKey(other))) segment = candidate;
                }

                // PAST A JUNCTION, THE ROAD IT CAME IN ON (MT-335).  Adam: *"With 75 407 DB at bottommaina, length 5 ... EN57-947
                // may still be manually sent to botommainb, and the orange blocked track is not extended to the segment between
                // tunnel and bottommaina pre."*  A train that was DRIVEN here follows the route it arrived along, through the
                // junction, where the rule below ("end locking at the switch and call it a day") would stop.  The route is the
                // edge that ended where the walk is now; the candidate taken is the one reaching back to where that edge started,
                // matched by place rather than by object, because the walk may stand on a different copy of the square than the
                // route passed through.  A train with no road (placed by hand and not asked, or answered Not Known) keeps the fork
                // rule; a hand-placed train can be given one (behaviour.md 5c, FR-085).
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

                // AT THE SQUARE IT STANDS ON, THE RAIL THAT ARRIVES THERE, as the side rule above takes it:
                // `getNeighborsAndIncoming` lists the rails LEAVING a Point first, whose places begin after the square the train
                // stands on, so the square would be neither claimed nor spent.  The read below spends it off an arriving
                // rail only where the square has a block, which the build writes only on a split square; on a square emitted once
                // (a dead end's turning copy among them) the tail would reach one square further back than the train lies.  The
                // rail arriving here ends with the square, so it is read from the end and the square is spent first.
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

            // THE SQUARE IT STANDS ON, WHERE THE RAIL TAKEN DOES NOT ARRIVE AT IT.  OB-278 spends the standing square
            // first by reading the first hop's places from the end, which holds the standing square only when the hop ARRIVES
            // at this copy.  A train turned where it stands and re-stood on the other arrival's copy (the idle drain does that
            // after a turn at a square trains may turn at) has no rail arriving by that side, so the walk takes the one running
            // away, whose places begin after the square: it would never be spent, and the tail would reach one square further
            // back than the train lies.  So the square is read off a rail that does arrive here, claimed, and spent, before
            // anything behind it is.
            //
            // ONLY WHERE THAT PLACE IS PROVABLY THIS SQUARE.  A built edge's places end with the square it arrives at, but a
            // hand-written graph's places are the track alone, so its last place is the rail behind, and spending it as "the
            // square" would use up the train on track not yet walked.  A split square's copies carry the square as their block,
            // and a built place names the same tile; a graph that does not say which place is the square has no square to spend.
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

            // THE MEASUREMENT RULE.  Nothing can be said about an unmeasured segment, including how much of the train would still
            // be left after it.  One answered 0 throughout is measured, at no length (Adam's words at `measuredRouteIn`): it is
            // claimed, costs nothing, and the walk goes on behind it, which is what lets the route in count past it
            // (`Edge.isMeasured`).
            //
            // `getLength()` is the path PLUS the square the edge arrives at, and on the first hop that square is the one the train
            // stands on, which is track and counts (OB-278).  A berth measured on an otherwise unmeasured approach is therefore a
            // measured segment: a train that fits on the berth is spent there and claims nothing behind, and one longer than it
            // claims the unmeasured squares behind for nothing, the refusing side `whyABerthCannotHoldIt` takes on the same approach.
            if (segment.getLength() <= 0 && !segment.isMeasured()) break;

            // BOTH DIRECTIONS OF THE SAME RAIL: the graph writes one piece of rail as two `Edge` objects, one
            // per direction, and `isPathClear` looks the covered set up by Edge identity, so covering A -> B alone left B -> A
            // clear and a train routed the other way over the same metal was cleared to run into the one standing on it.
            // `behaviour.md` 5c says a tail fouls the rail whichever way traffic runs.
            //
            // ON A BUILT GRAPH THIS LOOP SELDOM MATCHES: the two directions of a rail there usually run between
            // different COPIES of its squares, because facing is one-way edges, so no edge ends where this one starts and
            // starts where it ends.  The reverse rail is refused by place instead - `anotherTailOn`.  This loop
            // covers it where both directions join the same two Points, as on a hand-written graph.
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

            // AND HOW FAR ALONG IT THE TAIL ACTUALLY REACHES (OB-207): spend the train's remaining length across the edge's places
            // from the end the tail comes in by (the places are ordered from the edge's start and their lengths sum to its
            // length).  A place is claimed BEFORE its length is spent, so a tail that just reaches into one claims it, and an
            // unmeasured place the train lies over for nothing is claimed too.
            List<String> ids = segment.getPlaceIds();

            List<Integer> spans = segment.getPlaceLengths();

            // What the places walk actually charged, which is what this hop costs.  Negative until the places are walked
            // at all, so the edge's own length can stand in for a configuration that does not carry them.
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

                    // THE SQUARE THE TRAIN STANDS ON IS SPENT FIRST, like every square behind it (OB-278; Adam: *"the 2 length
                    // tile with the s88 consumes 2 units of the train"*, and asked where: *"Everywhere"*).  The station's SIZE, the
                    // maximum train length typed on it, is the allowance `whyTooLongForThisRoute` asks; the length measured on the
                    // square is rail.
                    //
                    // AND NOT TWICE: the square the walk turned at is the end of the last edge and a place on this one;
                    // charging it again shortens the tail by its length and leaves the track under the train's back end unclaimed.
                    if (!spent.add(ids.get(at))) continue;

                    left -= Math.max(0, spans.get(at));

                    chargedHere += Math.max(0, spans.get(at));

                    if (left <= 0) break;
                }
            }

            // ONE BUDGET, NOT TWO: what the finer places walk charged IS what the hop costs.  A second arithmetic over
            // the same train (spending the whole edge) agreed with it only while every square was measured, the standing square
            // was treated alike by both, and no square belonged to two hops, and each of those failed in turn.
            // The edge's own length is kept for a configuration that carries no places, where there is nothing finer.
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

    /**
     * The body of `Layout.anotherTailOn`, whose javadoc says what it does.
     */
    static Locomotive anotherTailOn(Edge edge, Locomotive mover, Map<String, Locomotive> claimed)
    {
        for (String place : edge.getPlaceIds())
        {
            Locomotive on = claimed.get(place);

            if (on != null && !on.equals(mover)) return on;
        }

        return null;
    }

    /**
     * The body of `Layout.tailLiesOn`, whose javadoc says what it does.
     */
    static boolean tailLiesOn(Edge edge, Locomotive who, Map<String, Locomotive> claimed)
    {
        for (String place : edge.getPlaceIds())
        {
            if (who.equals(claimed.get(place))) return true;
        }

        return false;
    }

    /**
     * The body of `Layout.claimUpToWhereTheRailsPart`, whose javadoc says what it does.
     */
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
     * Records how far behind the head a claimed place begins, the first time it is claimed (OB-294).
     *
     * @param reach where it is written, or null when nobody asked
     * @param place the place claimed
     * @param behindTheHead the body spent before it
     */
    private static void reachedAt(Map<String, Integer> reach, String place, int behindTheHead)
    {
        if (reach != null && !reach.containsKey(place)) reach.put(place, behindTheHead);
    }

    /**
     * Whether a Point is a copy trains pass through without turning - neither a terminus nor a reversing point.
     *
     * The build emits the turning copy of a square trains may turn at as a terminus, so this is how the tail walk tells a
     * square's plain copy from its turning twin - the rule `AutonomySession.copyFacing` and `placementCopy` use.
     *
     * @param point the Point, or null
     * @return true for a plain copy
     */
    private static boolean isPlainCopy(Point point)
    {
        return point != null && !point.isTerminus() && !point.isReversing();
    }
}
