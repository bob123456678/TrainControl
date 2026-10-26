package org.traincontrol.automation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * Whether a train fits a route: the room at its end, a berth that would leave it across another road, and a loop
 * that would run it into its own tail (FR-136).
 *
 * Rules of the route and the train alone, so every method here is static.  The railway keeps every public method
 * and every one a test reaches by name; each method here is the body of a one-line method there, or a helper only
 * these call.  Nothing here takes a lock.
 */
final class TrainFit
{
    private TrainFit()
    {
    }

    /** The body of `Layout.whyABerthCannotHoldIt`. */
    static String whyABerthCannotHoldIt(List<Edge> path, Locomotive loc)
    {
        if (path == null || path.isEmpty() || loc == null) return null;

        if (loc.getTrainLength() == null || loc.getTrainLength() <= 0) return null;

        Edge approach = path.get(path.size() - 1);

        Point berth = approach.getEnd();

        // STATIONS ARE EXEMPT.
        if (berth == null || berth.isAutoDestination()) return null;

        List<String> ids = approach.getPlaceIds();

        List<Integer> spans = approach.getPlaceLengths();

        if (ids.isEmpty() || ids.size() != spans.size()) return null;

        // AND NOTHING IS KNOWN ABOUT AN APPROACH NOBODY HAS MEASURED.  The walk below spends the train's length
        // backwards over the places and, against all zeroes, never runs out: it would claim the WHOLE approach and
        // refuse the berth against any road sharing any part of it, a refusal invented from the absence of a
        // measurement.  This is the room rule's own answer (`walkStandingTrains` claims nothing on an unmeasured
        // segment) and Adam's ruling on MT-364: "a stretch is only indeterminate when ALL of it is zero".  THE BERTH'S
        // OWN SQUARE COUNTS (OB-278): it is rail the train stands on and is spent first.
        boolean anyMeasured = false;

        for (int n = 0; n < spans.size(); n++)
        {
            Integer span = spans.get(n);

            if (span != null && span > 0) anyMeasured = true;
        }

        // AND AN APPROACH ANSWERED 0 THROUGHOUT IS KNOWN, at no length: the walk claims it all for nothing and refuses
        // where it reaches another road.  The room walk and the route in read it the same way (`Edge.isMeasured`).
        boolean answeredThroughout = true;

        for (String id : ids)
        {
            if (!approach.isPlaceAnswered(id)) answeredThroughout = false;
        }

        if (!anyMeasured && !answeredThroughout) return null;

        Set<String> claimed = new LinkedHashSet<>();

        // HOW MUCH OF WHAT IS CLAIMED WAS NEVER MEASURED: an unmeasured place costs the walk nothing and is claimed
        // anyway, so the refusal also says so, pointing the operator at the piece Mass Assign Lengths left at zero.
        int unmeasured = 0;

        int left = loc.getTrainLength();

        for (int n = ids.size() - 1; n >= 0; n--)
        {
            claimed.add(ids.get(n));

            // THE BERTH'S OWN SQUARE FIRST, and spent like the rest (OB-278): the same order and arithmetic as
            // `walkOneTail`.  The last place is the square being arrived at (`placesAlong` orders them from the edge's
            // start).  AN ANSWERED 0 IS NOT COUNTED AS MISSING (Adam: "stop listing answered zeros as missing"): it is
            // read as nothing, and claimed.
            if ((spans.get(n) == null || spans.get(n) <= 0) && !approach.isPlaceAnswered(ids.get(n))) unmeasured++;

            left -= spans.get(n) == null ? 0 : Math.max(0, spans.get(n));

            if (left <= 0) break;
        }

        for (Edge sharing : approach.getLockEdges())
        {
            if (sharing == null || !sharing.getLockEdges().contains(approach)) continue;

            if (berth.isSamePlaceAs(sharing.getStart()) || berth.isSamePlaceAs(sharing.getEnd()))
            {
                continue;
            }

            for (String place : sharing.getPlaceIds())
            {
                if (!claimed.contains(place)) continue;

                String why = I18n.f("autolayout.errorBerthWouldFoulAnotherRoad", loc.getName(),
                    Layout.placeNameOf(berth), Layout.placeNameOf(sharing), loc.getTrainLength());

                // AND WHY THE WHOLE APPROACH WAS CLAIMED, when that is what happened.
                return unmeasured > 0
                    ? why + ". " + I18n.f("autolayout.errorBerthApproachPartlyUnmeasured", unmeasured)
                    : why;
            }
        }

        return null;
    }

    /** The body of `Layout.whyItWouldMeetItsOwnTail(List, Locomotive, Map)`. */
    static String whyItWouldMeetItsOwnTail(List<Edge> path, Locomotive loc, Map<String, Integer> reach)
    {
        if (path == null || path.isEmpty() || loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0)
        {
            return null;
        }

        int length = loc.getTrainLength();

        // Indexed below, and the planner hands in linked lists.
        if (!(path instanceof java.util.RandomAccess)) path = new ArrayList<>(path);

        // Where the tail must have reached, along the journey, before each place is free of the train again.
        Map<String, Integer> freeOnceTheTailPasses = new HashMap<>();

        // And how far the ROUTE had run when the head left each place, and in which of its edges. A place of the body
        // was left before the journey began, in no edge (-1).
        Map<String, Integer> routeRunWhenLeft = new HashMap<>();
        Map<String, Integer> edgeWhenLeft = new HashMap<>();

        // And where in that edge the head left each place - after any repeats of the same square that follow it.
        Map<String, Integer> leftAt = new HashMap<>();

        // WHAT MASS ASSIGN WOULD ASK FOR, place by place along the route (OB-297): the key of the piece, switch or
        // shared square each place lies in that the build marked as still wanting a length, or one key per edge with
        // nothing measured on it on a configuration built before the marks.  NOT THE SQUARE: a square with no length
        // inside a measured piece is how a short piece drawn over several squares is stored, not a gap.  The note
        // counts the way round's keys, once each.
        List<String> askedAlong = new ArrayList<>();

        // Where each edge's places start in that list.
        List<Integer> startsAt = new ArrayList<>();

        // AND WHETHER EACH PLACE IS MEASURED (OB-300), in the same order: a length, or answered 0 on purpose.
        List<Boolean> measuredAlong = new ArrayList<>();

        // LEAVING OVER ITS OWN BODY: the first place the route goes to, other than the square the train stands on, is
        // one its body lies on (a train turned where it stands).  The first only, or a route looping back within one
        // edge would match.
        String firstOut = null;

        for (String place : path.get(0).getPlaceIds())
        {
            if (!Layout.isTheSquareOf(place, path.get(0).getStart()))
            {
                firstOut = place;

                break;
            }
        }

        boolean leavesOverItsBody = reach != null && firstOut != null && reach.containsKey(firstOut);

        if (reach != null && !leavesOverItsBody)
        {
            for (Map.Entry<String, Integer> lying : reach.entrySet())
            {
                freeOnceTheTailPasses.put(lying.getKey(), -lying.getValue());
                routeRunWhenLeft.put(lying.getKey(), 0);
                edgeWhenLeft.put(lying.getKey(), -1);
                leftAt.put(lying.getKey(), -1);
            }
        }

        int travelled = 0;

        String previous = null;

        // THE TIGHTEST RETURN, and where, and how much of its way round has no length.
        Integer tightest = null;
        Edge tightestOn = null;
        int tightestUnmeasured = 0;

        for (int i = 0; i < path.size(); i++)
        {
            Edge edge = path.get(i);

            List<String> ids = edge.getPlaceIds();
            List<Integer> spans = edge.getPlaceLengths();

            // A configuration that describes no places says nothing about where the train is.
            if (ids.size() != spans.size()) return null;

            startsAt.add(askedAlong.size());

            for (int at = 0; at < ids.size(); at++) askedAlong.add(askedFor(edge, i, at));

            for (int at = 0; at < ids.size(); at++)
            {
                measuredAlong.add((spans.get(at) != null && spans.get(at) > 0) || edge.isPlaceAnswered(ids.get(at)));
            }

            for (int at = 0; at < ids.size(); at++)
            {
                String place = ids.get(at);

                // The same square twice in a row is one hop ending where the next begins, not a return to it.
                if (place.equals(previous)) continue;

                previous = place;

                Integer free = freeOnceTheTailPasses.get(place);

                // JUDGED WHERE THE WAY ROUND IS MEASURED (OB-300): some length run on it since the head left the place,
                // or every place on it answered 0 on purpose (a loop answered 0 all the way round is refused to any
                // train lying on it).
                if (free != null && (travelled - routeRunWhenLeft.get(place) > 0
                    || measuredThroughout(measuredAlong, edgeWhenLeft.get(place) < 0 ? -1
                        : startsAt.get(edgeWhenLeft.get(place)) + leftAt.get(place), startsAt.get(i) + at)))
                {
                    int wayRound = travelled - free;

                    if (tightest == null || wayRound < tightest)
                    {
                        tightest = wayRound;
                        tightestOn = edge;
                        tightestUnmeasured = 0;

                        // What Mass Assign would ask for on the way round (OB-297).
                        int leftIn = edgeWhenLeft.get(place);

                        tightestUnmeasured = askedForOnTheWayRound(askedAlong,
                            leftIn < 0 ? -1 : startsAt.get(leftIn) + leftAt.get(place), startsAt.get(i) + at);
                    }
                }

                travelled += spans.get(at) == null ? 0 : Math.max(0, spans.get(at));

                freeOnceTheTailPasses.put(place, travelled);
                routeRunWhenLeft.put(place, travelled);
                edgeWhenLeft.put(place, i);

                // Left after the last of any repeats of the same square that follow it here.
                int leftAtIndex = at;

                while (leftAtIndex + 1 < ids.size() && ids.get(leftAtIndex + 1).equals(place)) leftAtIndex++;

                leftAt.put(place, leftAtIndex);
            }

            // A TURN ON THE WAY: the body is ahead of the train from here, and the question starts again.
            Point turn = edge.getEnd();

            if (i + 1 < path.size() && turn != null && turn.isReversing())
            {
                freeOnceTheTailPasses.clear();
                routeRunWhenLeft.clear();
                edgeWhenLeft.clear();
                leftAt.clear();
            }
        }

        if (tightest == null || length <= tightest) return null;

        String why = I18n.f("autolayout.errorWouldMeetItsOwnTail", loc.getName(), Layout.placeNameOf(tightestOn), tightest, length);

        // AND WHAT IS NOT MEASURED ON THE WAY ROUND: measured, it may be long enough.  After a full stop: the sentence
        // before it has none.
        return tightestUnmeasured > 0 ? why + ". " + I18n.f("autolayout.errorOwnTailPartlyUnmeasured", tightestUnmeasured)
            : why;
    }

    /**
     * What Mass Assign Lengths would ask a length for at this place of an edge, as a key, or null (OB-297).  The build
     * marks each place in a piece, switch or shared square still wanting a length with that thing's key
     * (`Edge.pieceToMeasure`), so the note counts exactly the editor's list; a configuration built before the marks
     * counts each edge with nothing measured on it as one thing.
     *
     * @param index the edge's place in the route, which keys an edge of the old reading
     * @param at the place's index in the edge
     */
    static String askedFor(Edge edge, int index, int at)
    {
        if (edge.knowsPiecesToMeasure()) return edge.pieceToMeasure(edge.getPlaceIds().get(at));

        // NOR ON A LEG ANSWERED THROUGHOUT (OB-300): measured track of no length, which a refusal must not send the
        // operator to measure again.
        if (edge.isMeasured()) return null;

        for (Integer span : edge.getPlaceLengths())
        {
            if (span != null && span > 0) return null;
        }

        return "edge " + index;
    }

    /**
     * Whether the way round between two points of the route is measured throughout (OB-300): at least one place
     * strictly between them, every one with a length or answered 0 on purpose.  `Edge.isMeasured`'s question, asked of
     * places because a way round begins and ends inside a leg.
     *
     * @param left where the place was left, -1 for before the journey began
     * @param back where the route comes back to it
     */
    static boolean measuredThroughout(List<Boolean> measuredAlong, int left, int back)
    {
        int from = Math.max(0, left + 1);

        if (from >= back) return false;

        for (int g = from; g < back; g++)
        {
            if (g >= measuredAlong.size() || !measuredAlong.get(g)) return false;
        }

        return true;
    }

    /**
     * How many things Mass Assign would ask for over the places strictly between two points of the route, each once
     * (OB-297).
     *
     * @param left where the place was left, -1 for before the journey began
     * @param back where the route comes back to it
     */
    static int askedForOnTheWayRound(List<String> askedAlong, int left, int back)
    {
        java.util.Set<String> keys = new java.util.HashSet<>();

        for (int g = Math.max(0, left + 1); g < back && g < askedAlong.size(); g++)
        {
            if (askedAlong.get(g) != null) keys.add(askedAlong.get(g));
        }

        return keys.size();
    }

    /** The body of `Layout.whyTooLongForThisRoute`. */
    static String whyTooLongForThisRoute(List<Edge> path, Locomotive loc)
    {
        if (path == null || path.isEmpty() || loc == null) return null;

        Point ending = path.get(path.size() - 1).getEnd();

        if (ending == null) return null;

        // THE STATION'S STATED CAPACITY.  `Point.validateTrainLength` is the rule; it answers true for anything that is
        // not a destination and for a limit of zero.
        if (!ending.validateTrainLength(loc))
        {
            return I18n.f("autolayout.errorTrainLengthTooLong", Layout.placeNameOf(ending));
        }

        // AND THE TRACK, WHERE THE TRAIN COMES TO REST (MT-333; the loop below skips a square it only passes).  Adam:
        // "This should only apply if lengths are specified - and edges are already locked as trains pass through in
        // non-dynamic mode.  So it's really about implementing the same mechanic."
        //
        // ONE WALK, ASKED OF EVERY PREFIX: `measuredRoomAtTheEndOf` answers about the square a path ENDS at, so asking
        // it of path[0..i] answers "if the train stood here, would its tail be clear of the switch behind it".  The
        // destination is the last iteration rather than a second rule, so the two cannot disagree.
        //
        // NULL IS NOT A REFUSAL: an unmeasured stretch answers "I cannot judge this" and is passed over; the measured
        // stretches still bind.  THE MESSAGE NAMES THE SQUARE THAT IS SHORT, with a different message for a square on
        // the way than for the berth, so the operator measures the right stretch.  Copied into an ArrayList: `subList`
        // is asked for `path.size()` times and callers hand in linked lists.
        if (loc.getTrainLength() == null || loc.getTrainLength() <= 0) return null;

        List<Edge> ordered = new java.util.ArrayList<>(path);

        for (int i = 0; i < ordered.size(); i++)
        {
            boolean berth = i == ordered.size() - 1;

            Point here = ordered.get(i).getEnd();

            // A SQUARE THE TRAIN TURNS AT IS JUDGED LIKE A BERTH (Adam: "Refused both because of the berth (3<4) and
            // the track length that potentially couldn't fit the train while reversing").  The train stands still at
            // the reversing point with its body stretched back over the approach, so a shorter approach leaves it
            // hanging off the measured track.
            boolean comesToRest = berth || (here != null && here.isReversing());

            // A SQUARE THE TRAIN ONLY PASSES IS NOT JUDGED (MT-333): a train passing a square does not stand there.
            // What is judged is where a train comes to rest: the destination, and a square it turns round at.
            if (!comesToRest) continue;

            Integer room = measuredRoomAtTheEndOf(ordered.subList(0, i + 1), loc);

            if (room == null || loc.getTrainLength() <= room) continue;

            // AND A STATION AUTONOMY MAY CHOOSE TAKES A TRAIN ITS MEASURED ROUTE IN HOLDS (FR-087).  The kind of square
            // decides, because of how long the train stays: at a station autonomy may choose a train is PASSING,
            // standing across the points only until its next route, a cost accepted at a platform and refused at a
            // parking berth, where a train is STAYING.  `whyABerthCannotHoldIt` is the other half.
            //
            // Safety is not what changed: a standing train's tail claims the places it lies on and `isPathClear`
            // refuses any route over them (OB-207), so this spends only the ability of OTHER trains to get past.  It
            // only ever ALLOWS: the room rule above has already refused, and the bound is the measured route in, never
            // back past a turn.  A train that turns round at the destination is deliberately not excluded.
            // `isAutoDestination` is "Can Be Chosen In Full Autonomy".
            if (berth && Layout.theApproachItselfHoldsIt(ordered.subList(0, i + 1), loc))
            {
                continue;
            }

            if (here == null || berth)
            {
                // THE BOUND THAT REFUSED: at a station autonomy may choose, the route in was asked too, and it is the
                // larger of the two; quoting the room past the switch said the square holds less than it does.
                int refusedAt = berth && ending != null && ending.isAutoDestination()
                    ? Math.max(room, Layout.measuredRouteIn(ordered)) : room;

                return I18n.f("autolayout.errorTrainTooLongForBerth", loc.getName(),
                    Layout.placeNameOf(ending), refusedAt, loc.getTrainLength());
            }

            return I18n.f("autolayout.errorTrainTooLongOnTheWay", loc.getName(), Layout.placeNameOf(here),
                room, loc.getTrainLength(), Layout.placeNameOf(ending));
        }

        return null;
    }

    /** The body of `Layout.measuredRoomAtTheEndOf`. */
    static Integer measuredRoomAtTheEndOf(List<Edge> path, Locomotive loc)
    {
        if (loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0) return null;

        if (path == null || path.isEmpty()) return null;
        // FROM THE LAST SWITCH, NOT FROM THE START OF THE ROUTE (Adam: "between the switch and the station, the length
        // must be >= length of the train", and for the direction over the points "let's use any direction, that way we
        // are guaranteed to be safe").  So this walks the route BACKWARDS and stops at the first edge that crosses a
        // switch, adding only the part of that edge after it.  The last switch binds: a train that fits between it and
        // the station fits between any earlier switch and the station too.  An unmeasured edge still answers null (this
        // guard acts on measurements, not their absence); only the edges it counts are asked.
        int room = 0;

        // WHETHER ANY MEASURED TRACK WAS COUNTED: a room of 0 answered on purpose is a room, not an unknown (Adam: "We
        // need to find a way to allow trains in atomic mode in as well if the total track lengths allow").
        boolean counted = false;

        for (int i = path.size() - 1; i >= 0; i--)
        {
            Edge segment = path.get(i);

            // AND A REVERSAL STOPS THE COUNT, exactly as a switch does (Adam: "it can be either the reversal or the
            // berth, depending on where switches are.  both need to be long enough.").  So BOTH bounds apply, each over
            // its own stretch, and this is the first: a train that turns part way along comes to rest on the track
            // after the turn only.  The second is at the turn itself, in `whyTooLongForThisRoute`, which judges a
            // reversing square as it judges a berth (`core.testNonReversibleTrains` carries his figures and verdict).
            // The END of this segment is where the train turned.  The last edge of whatever list is handed in is
            // exempt, because its end is the square being asked about; that holds for a prefix as well as a whole
            // route.  ANY reversing point counts: it turns every locomotive.
            if (i < path.size() - 1 && segment.getEnd() != null && segment.getEnd().isReversing())
            {
                return counted ? room : null;
            }

            if (segment.crossesASwitch())
            {
                // BOUNDED BUT UNMEASURED (-1), and what has already been counted still stands (Adam: "Generally, allow
                // it.").  Two units of measured berth already prove a four-unit train does not fit; an unmeasured
                // stretch behind them cannot unprove it.  `room > 0` separates the halves of the ruling: nothing
                // measured at all is no evidence either way.
                if (segment.getRoomAtTheEnd() < 0)
                {
                    // THE SEGMENT BOUNDS THE STRETCH INSIDE IT: the room that matters is the track after the last
                    // switch, which is part of this segment and so cannot be longer than it.  The bound over-states the
                    // real room, so it only ever REFUSES more and never admits anything new.
                    int bound = room + segment.getLength();

                    return bound > 0 ? bound : null;
                }

                return room + segment.getRoomAtTheEnd();
            }

            // AN UNMEASURED SEGMENT ENDS THE COUNT, it does not cancel it: it contributes no room and no answer.  A
            // SEGMENT ANSWERED 0 ON PURPOSE IS MEASURED: it adds nothing and the count walks on.
            if (segment.getLength() <= 0 && !segment.isMeasured()) return counted ? room : null;

            room += Math.max(0, segment.getLength());

            counted = true;
        }

        // No switch anywhere on the route, so nothing bounds where the train may stand except the route itself.
        return room;
    }
}
