package org.traincontrol.automation;

import java.util.LinkedList;
import java.util.List;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * Why a train cannot start, and why a station is not offered to it or cannot be reached: the sentences Why not Moving?
 * shows (FR-136).
 *
 * Nothing here takes a lock: a caller holding the railway's monitor still holds it.  A call from one of these methods
 * to another stays here, never out through the railway's delegator, which would come straight back.
 */
final class LayoutExplanations
{
    private final Layout owner;

    LayoutExplanations(Layout owner)
    {
        this.owner = owner;
    }

    /** The body of `Layout.explainCannotStart(Locomotive, boolean)`. */
    String explainCannotStart(Locomotive loc, boolean byHand)
    {
        if (!byHand) return explainCannotStart(loc);

        // BY HAND, ONLY WHAT STOPS A HAND ROUTE.  A route picked by hand may start from a copy that is no station and
        // from a square switched off while autonomy is not running (`isPathClear` fences both behind `isAutoRunning`),
        // so then they are autonomy's reasons, not the operator's (OB-225).  While autonomy runs they are the hand's
        // too.  The pause is autonomy's alone.
        if (loc == null) return null;

        if (owner.getLocomotiveLocation(loc) == null) return I18n.t("autolayout.why.notOnGraph");

        return owner.isAutoRunning() ? whyTheStartIsRefused(loc) : null;
    }

    /** The body of `Layout.explainCannotStart(Locomotive)`. */
    String explainCannotStart(Locomotive loc)
    {
        if (loc == null) return null;

        if (loc.isAutonomyPaused()) return I18n.t("autolayout.why.paused");

        String refused = whyTheStartIsRefused(loc);

        if (refused != null) return refused;

        return whyItReachesNoStation(owner.getLocomotiveLocation(loc));
    }

    /**
     * Why autonomy will never start a train standing here: nothing it may choose can be reached from this copy
     * (OB-299).  Autonomy's reason only: a train sent by hand may go to a berth autonomy never chooses.
     *
     * @return the reason, or null where a station can be reached
     */
    private String whyItReachesNoStation(Point at)
    {
        if (at == null || owner.canReachAnyDestination(at)) return null;

        // THE OTHER WAY, where it reaches a station: turn it round where a train may be started there; otherwise say
        // why not, and only what would help.
        Point refused = null;

        for (Point other : owner.getPoints())
        {
            if (other == at || !other.isSamePlaceAs(at) || !owner.canReachAnyDestination(other)) continue;

            // The start rules asked of the square a train stands on (`whyNoTrainIsStartedFrom`)
            if (whyNoTrainIsStartedFrom(other) == null) return I18n.f("autolayout.why.startReachesNoStation", Layout.placeNameOf(at));

            if (refused == null) refused = other;
        }

        if (refused != null)
        {
            // Switched off, or no station: autonomy starts no train that way
            if (!owner.isABarredCopyOfAStation(refused))
            {
                return I18n.f("autolayout.why.startReachesNoStationOtherWayRefused", Layout.placeNameOf(at));
            }

            // BARRED WHERE EVERY TRAIN TURNS: the reason said, and not "open that side": opened there, a terminus
            // reached two ways is the error that refuses the whole setup.
            if (owner.turnsEveryTrainAt(refused))
            {
                return I18n.f("autolayout.why.startReachesNoStationOtherWayBarredMustTurn", Layout.placeNameOf(at));
            }

            return I18n.f("autolayout.why.startReachesNoStationOtherWayBarred", Layout.placeNameOf(at),
                I18n.t("autosetup.ui.menuArrivalsGroup"));
        }

        // NEITHER WAY: what a reachable station needs - switched on, Can Be Chosen ticked, and the side a train would
        // arrive on open, unless every train turns round there.  The exception is in the words because this sentence
        // does not know which station is meant.
        return I18n.f("autolayout.why.startReachesNoStationEitherWay", Layout.placeNameOf(at),
            I18n.t("autosetup.ui.menuAutoDestination"), I18n.t("autosetup.ui.menuArrivalsGroup"));
    }

    /** The start rules alone - where the train is, and what the square it stands on allows - without the pause. */
    private String whyTheStartIsRefused(Locomotive loc)
    {
        Point at = owner.getLocomotiveLocation(loc);

        if (at == null) return I18n.t("autolayout.why.notOnGraph");

        return whyNoTrainIsStartedFrom(at);
    }

    /**
     * Why autonomy starts no train from this copy of a square, or null where it may: the square's own start rules,
     * asked of a train standing there and of the copy Why not Moving? would turn one round onto - one rule for both.
     */
    String whyNoTrainIsStartedFrom(Point at)
    {
        // FACING THE WAY TRAINS MAY NOT ARRIVE, AT A STATION: named as the square, with what to do - turn it round, or
        // open that side.
        if (owner.isABarredCopyOfAStation(at))
        {
            // NOT "OPEN THAT SIDE" WHERE EVERY TRAIN TURNS (Adam: "offer the other two remedies only"): a terminus
            // reached from two sides is the terminus error, whose own remedy is to close one.
            if (owner.turnsEveryTrainAt(at)) return I18n.f("autolayout.why.startFacingBarredMustTurn", Layout.placeNameOf(at));

            return I18n.f("autolayout.why.startFacingBarred", Layout.placeNameOf(at), I18n.t("autosetup.ui.menuArrivalsGroup"));
        }

        if (!at.isDestination()) return I18n.f("autolayout.why.startNotStation", Layout.placeNameOf(at));

        if (!at.isActive()) return I18n.f("autolayout.why.startInactive", Layout.placeNameOf(at));

        return null;
    }

    /** The body of `Layout.whyNotOfferedToOperator`. */
    String whyNotOfferedToOperator(Point end, Locomotive loc)
    {
        if (end == null || !end.isActive()) return I18n.t("autolayout.why.inactive");

        if (loc != null && end.excludes(loc)) return I18n.f("autolayout.why.excluded", loc.getName());

        if (loc != null && !loc.isReversible() && strandsATrainThatCannotReverse(end))
        {
            return I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", loc.getName());
        }

        return null;
    }

    /**
     * A terminus a train that cannot reverse would be stranded at (OB-205), unless it is a berth: a square autonomy
     * never chooses.  It is a question about the square, not this copy of it: `isTerminus()` cannot be read for this,
     * so ask whether any way of standing on the square does not turn a train round (MT-367).
     */
    boolean strandsATrainThatCannotReverse(Point end)
    {
        return (end.isTerminus() || end.isReversing())
            && end.isAutoDestination()
            && (!owner.hasAWayThrough(end) || owner.noCopyFacesOnFromAnyWayIn(end));
    }

    /** The body of `Layout.whyNoRouteFitsTo`. */
    String whyNoRouteFitsTo(Locomotive loc, Point start, Point end)
    {
        if (loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0) return null;

        String why = null;

        try
        {
            List<Edge> path;
            List<List<Edge>> seenPaths = new LinkedList<>();

            do
            {
                path = owner.bfs(start, end, seenPaths);

                if (path == null) break;

                seenPaths.add(path);

                // BOTH PHYSICAL REFUSALS, NOT ONE (MT-262: a physical refusal outranks a preference): the train does
                // not fit down the route, or it fits but its body would lie across a road something else needs.
                // `isPathClear` and the staging planner ask both; asking only the first reported a berth refused on the
                // second as "Set not to be chosen automatically".  The berth rule is asked only of a route the train
                // fits down, because the length refusal is the blunter fact.
                String tooLong = Layout.whyTooLongForThisRoute(path, loc);

                String foulsARoad = tooLong == null ? Layout.whyABerthCannotHoldIt(path, loc) : null;

                // AND THE THIRD: round a loop into its own tail (OB-294), as physical as the other two.
                String ownTail = tooLong == null && foulsARoad == null ? owner.whyItWouldMeetItsOwnTail(path, loc) : null;

                // A route it fits down whose berth can hold it is a route the operator can be sent along, whatever the
                // others say.
                if (tooLong == null && foulsARoad == null && ownTail == null) return null;

                why = tooLong != null ? tooLong : foulsARoad != null ? foulsARoad : ownTail;

            } while (path != null);
        }
        catch (Exception e)
        {
            // The caller is explaining, not deciding: a search that threw is reported by the reason the caller already
            // had.
            return null;
        }

        return why;
    }
}
