package org.traincontrol.automation;

import java.util.LinkedList;
import java.util.List;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * Why a train cannot start, and why a station is not offered to it or cannot be reached: the sentences Why not
 * Moving? shows (FR-136).
 *
 * The railway keeps its fields, its monitor and every public and `synchronized` method; each method here is the body
 * of a one-line method there, or a private helper only these call.  Nothing here takes a lock: a caller holding the
 * railway's monitor still holds it, and a call back into the railway re-enters it.
 *
 * A call from one of these methods to another stays here.  `explainCannotStart(Locomotive, boolean)` ends in this
 * class's own `explainCannotStart(Locomotive)`, where it was the railway's before the move.  That is on purpose: the
 * literal `owner.explainCannotStart(loc)` would go out to the railway's delegator and come straight back here, and
 * nothing overrides that delegator (Layout has no subclass).
 */
final class LayoutExplanations
{
    private final Layout owner;

    /**
     * @param owner the railway whose trains and stations these explain
     */
    LayoutExplanations(Layout owner)
    {
        this.owner = owner;
    }

    /**
     * The body of `Layout.explainCannotStart(Locomotive, boolean)`, whose javadoc says what it does.
     */
    String explainCannotStart(Locomotive loc, boolean byHand)
    {
        if (!byHand) return explainCannotStart(loc);

        // BY HAND, ONLY WHAT STOPS A HAND ROUTE.  A route picked by hand may start from a copy that is no station
        // and from a square switched off while autonomy is not running - `isPathClear` fences those two rules behind
        // `isAutoRunning` - so then they are autonomy's reasons and not the operator's (Adam, OB-225: "in manual mode, I
        // still get reasons like ... will never be chosen in autonomy").  While autonomy runs they are the hand's too.
        // The pause is autonomy's alone: nothing a hand send passes through reads it.
        if (loc == null) return null;

        if (owner.getLocomotiveLocation(loc) == null) return I18n.t("autolayout.why.notOnGraph");

        return owner.isAutoRunning() ? whyTheStartIsRefused(loc) : null;
    }

    /**
     * The body of `Layout.explainCannotStart(Locomotive)`, whose javadoc says what it does.
     */
    String explainCannotStart(Locomotive loc)
    {
        if (loc == null) return null;

        if (loc.isAutonomyPaused()) return I18n.t("autolayout.why.paused");

        String refused = whyTheStartIsRefused(loc);

        if (refused != null) return refused;

        return whyItReachesNoStation(owner.getLocomotiveLocation(loc));
    }

    /**
     * Why autonomy will never start a train standing here: nothing it may choose can be reached from this copy (OB-299).
     *
     * A copy of a station can have a way out and still reach no station autonomy may choose - everything past it is
     * sensors, turning points or parking - and a train stood there facing that way is never started.  Why not Moving?
     * listed every station's own reason instead, which says nothing about the copy.  Since the right-click Place keeps a
     * train's heading over every copy it could leave by (OB-296), a train can be put down on one.
     *
     * The remedy depends on the square's other copies.  Where one reaches a station, and a train may be started from it,
     * turning the train round puts it there.  Where none does, turning would not help, and what would is a station
     * reachable from here that autonomy may choose - `canReachAnyDestination` asks for one it may choose, in service and
     * not a turning copy.
     *
     * A COPY A TRAIN MAY BE STARTED FROM.  Offered wherever another copy reached a station, "turn it round" could
     * send the operator onto a copy that is switched off, or barred, or no station - whose own refusal then sent him back.
     *
     * Autonomy's reason only: a train sent by hand may go to a berth autonomy never chooses, so the hand's question,
     * `explainCannotStart(loc, true)`, does not ask it.
     *
     * @param at the copy the train stands on, or null
     * @return the reason, or null where a station can be reached
     */
    private String whyItReachesNoStation(Point at)
    {
        if (at == null || owner.canReachAnyDestination(at)) return null;

        // THE OTHER WAY, where it reaches a station: turn it round where a train may be started there; otherwise say
        // why not, and only what would help - "whichever way a train faces" is false of it.
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

            // BARRED WHERE EVERY TRAIN TURNS: the reason said, and not "open that side" - opened
            // there, a terminus reached two ways is the error that refuses the whole setup.
            if (owner.turnsEveryTrainAt(refused))
            {
                return I18n.f("autolayout.why.startReachesNoStationOtherWayBarredMustTurn", Layout.placeNameOf(at));
            }

            return I18n.f("autolayout.why.startReachesNoStationOtherWayBarred", Layout.placeNameOf(at),
                I18n.t("autosetup.ui.menuArrivalsGroup"));
        }

        // NEITHER WAY: what a reachable station needs - switched on, Can Be Chosen ticked, and the side a train would arrive
        // on open, unless every train turns round there.  The exception is in the words because this sentence
        // does not know which station is meant: where trains pass through, a closed side is the remedy; where
        // every train turns round, opening it is the terminus error.
        return I18n.f("autolayout.why.startReachesNoStationEitherWay", Layout.placeNameOf(at),
            I18n.t("autosetup.ui.menuAutoDestination"), I18n.t("autosetup.ui.menuArrivalsGroup"));
    }

    /**
     * The start rules alone - where the train is, and what the square it stands on allows - without the pause.
     *
     * @param loc the locomotive
     * @return the reason, or null
     */
    private String whyTheStartIsRefused(Locomotive loc)
    {
        Point at = owner.getLocomotiveLocation(loc);

        if (at == null) return I18n.t("autolayout.why.notOnGraph");

        return whyNoTrainIsStartedFrom(at);
    }

    /**
     * Why autonomy starts no train from this copy of a square, or null where it may: the square's own start rules, asked
     * of a train standing there and of the copy Why not Moving? would turn one round onto - one rule
     * for both, so one added here reaches "turn it round" too.
     *
     * @param at the copy
     * @return the reason, or null
     */
    private String whyNoTrainIsStartedFrom(Point at)
    {
        // FACING THE WAY TRAINS MAY NOT ARRIVE, AT A STATION: named as the square, with what to do - turn it
        // round, or open that side.  It was "standing on BottomMainA (westbound), which is not a station": the copy's
        // name, a station called not one, and no way out.
        if (owner.isABarredCopyOfAStation(at))
        {
            // NOT "OPEN THAT SIDE" WHERE EVERY TRAIN TURNS (Adam: *"offer the other two remedies only"*): a terminus
            // reached from two sides is the terminus error, whose own remedy is to close one.
            if (owner.turnsEveryTrainAt(at)) return I18n.f("autolayout.why.startFacingBarredMustTurn", Layout.placeNameOf(at));

            return I18n.f("autolayout.why.startFacingBarred", Layout.placeNameOf(at), I18n.t("autosetup.ui.menuArrivalsGroup"));
        }

        if (!at.isDestination()) return I18n.f("autolayout.why.startNotStation", Layout.placeNameOf(at));

        if (!at.isActive()) return I18n.f("autolayout.why.startInactive", Layout.placeNameOf(at));

        return null;
    }

    /**
     * The body of `Layout.whyNotOfferedToOperator`, whose javadoc says what it does.
     */
    String whyNotOfferedToOperator(Point end, Locomotive loc)
    {
        if (end == null || !end.isActive()) return I18n.t("autolayout.why.inactive");

        if (loc != null && end.excludes(loc)) return I18n.f("autolayout.why.excluded", loc.getName());

        // A TERMINUS A TRAIN THAT CANNOT REVERSE WOULD BE STRANDED AT (OB-205; Adam: *"75 407 DB can go from Tunnel to BottomMainC manually, even
        // though it is not reversible and this is not a parking berth (excluded from autonomy).  it should not be allowed to be chosen."*).  This
        // narrows the terminus ruling quoted at `isPathClear`, made for a BERTH: a square autonomy never chooses (`isAutoDestination` off) is
        // somewhere the operator deliberately puts a train away; an ordinary terminus is a place a non-reversible train cannot leave.
        //
        // **IT IS A QUESTION ABOUT THE SQUARE, NOT ABOUT THIS COPY OF IT** (MT-367; Adam: *"EN57-947 is not allowed to go to BottomMainB, even
        // though it 'may' reverse (is not a terminus).  It is correctly barred from BottomMainC, a terminus."*).  `isTerminus()` cannot be read for
        // this (`AutonomyBuilder` emits the turning copy of a MAY-turn square with `terminus: true`), so ask whether there is ANY way to stand on
        // the square without being turned round: `getBlock` says which Points are one piece of track, and a way through is a copy that is neither a
        // terminus nor a reversing point.  The copy asked about is tested the same way (Adam: *"still offered it for 2-8-4 3505"*), since a turning
        // copy is a "terminus" or a "reversing point" by whether a train may stop there, and to this train both mean you must turn round here.
        //
        // AND ASKED OF THE WAYS INTO THIS COPY: a copy whose ways in have no copy facing on turns a train there whatever was answered.
        if (loc != null && !loc.isReversible() && (end.isTerminus() || end.isReversing())
            && end.isAutoDestination()
            && (!owner.hasAWayThrough(end) || owner.noCopyFacesOnFromAnyWayIn(end)))
        {
            return I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", loc.getName());
        }

        return null;
    }

    /**
     * The body of `Layout.whyNoRouteFitsTo`, whose javadoc says what it does.
     */
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

                // BOTH PHYSICAL REFUSALS, NOT ONE: MT-262's ruling is that a physical refusal outranks a preference, and
                // there are two of them: the train does not fit down the route, and the train fits but its body would lie across a
                // road something else needs.  `isPathClear` asks both, and so does the staging planner; asking only the first reported
                // a berth the railway refuses on the second as "Set not to be chosen automatically", autonomy's preference answering
                // an operator about his own send.  The berth rule is asked only of a route the train fits down, because the length
                // refusal is the blunter fact.
                String tooLong = Layout.whyTooLongForThisRoute(path, loc);

                String foulsARoad = tooLong == null ? Layout.whyABerthCannotHoldIt(path, loc) : null;

                // AND THE THIRD: round a loop into its own tail (OB-294), as physical as the other two.
                String ownTail = tooLong == null && foulsARoad == null ? owner.whyItWouldMeetItsOwnTail(path, loc) : null;

                // A route it fits down whose berth can hold it is a route the operator can be sent
                // along, so there is nothing to report whatever the others say.
                if (tooLong == null && foulsARoad == null && ownTail == null) return null;

                why = tooLong != null ? tooLong : foulsARoad != null ? foulsARoad : ownTail;

            } while (path != null);
        }
        catch (Exception e)
        {
            // The caller is explaining, not deciding.  A search that threw is reported by the reason
            // the caller already had, which is better than replacing it with an exception message.
            return null;
        }

        return why;
    }
}
