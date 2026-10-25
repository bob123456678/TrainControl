package org.traincontrol.automationui;

import org.traincontrol.base.LayoutDiagramComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.GraphReducer.ReducedPoint;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * Checks a user can run against a configuration before trusting trains to it: questions that are obvious once asked and
 * invisible until then (a terminus nothing can leave, a station no train can reach), each producing a configuration
 * that loads and runs and then quietly never sends a train somewhere.  Reported as a list of findings rather than pass
 * or fail, since a layout mid-setup is expected to have some.
 *
 * @author Adam
 */
public class AutonomyChecks
{
    /**
     * How much a finding matters.
     */
    public static enum Severity
    {
        /**
         * Autonomy cannot be built at all until this is fixed.
         */
        ERROR,

        /**
         * It will build and run, but something will not work the way it looks like it should.
         */
        WARNING,

        /**
         * Tidiness.  Nothing behaves differently; the setup is simply harder to read than it could be.
         *
         * Last on purpose - findings are sorted by this ordinal, so a notice never comes above
         * something that will actually go wrong.
         */
        NOTICE,

        /**
         * Worth knowing, not worth fixing.
         */
        INFO
    }

    /**
     * One thing worth telling the user, in terms they can act on.
     */
    public static class Finding
    {
        private final Severity severity;
        private final String messageKey;
        private final String subject;
        private final TileKey tile;
        private final int count;
        private final int detail;
        private final int third;
        private final List<TileKey> related;
        private TileKey about;

        Finding(Severity severity, String messageKey, String subject, TileKey tile)
        {
            this(severity, messageKey, subject, tile, 0);
        }

        Finding(Severity severity, String messageKey, String subject, TileKey tile, List<TileKey> related)
        {
            this(severity, messageKey, subject, tile, 0, 0, 0, related);
        }

        /**
         * The same, with a number the message may put in a sentence (OB-171).
         *
         * @param count how many things this finding is about, or zero when it is not about a number
         */
        Finding(Severity severity, String messageKey, String subject, TileKey tile, int count)
        {
            this(severity, messageKey, subject, tile, count, 0);
        }

        /**
         * The same, with a SECOND number, because one finding is a comparison (`RUN_IN_SHORTER_THAN_THE_BERTH`: what
         * the station was told it can hold against what the track measures).
         *
         * @param detail the second number, or zero when this finding is not a comparison
         */
        Finding(Severity severity, String messageKey, String subject, TileKey tile, int count,
            int detail)
        {
            this(severity, messageKey, subject, tile, count, detail, 0);
        }

        /**
         * The same, with a THIRD number: the figure a train is refused above at a platform.
         *
         * @param third the third number, or zero when this finding has none
         */
        Finding(Severity severity, String messageKey, String subject, TileKey tile, int count,
            int detail, int third)
        {
            this(severity, messageKey, subject, tile, count, detail, third, null);
        }

        Finding(Severity severity, String messageKey, String subject, TileKey tile, int count,
            int detail, int third, List<TileKey> related)
        {
            this.related = related == null ? Collections.<TileKey>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(related));
            this.severity = severity;
            this.messageKey = messageKey;
            this.subject = subject;
            this.tile = tile;
            this.count = count;
            this.detail = detail;
            this.third = third;
        }

        /**
         * The third number, rendered as `{4}` - after the other two, so no existing message changes.
         *
         * @return the number, or zero when this finding has none
         */
        public int getThird()
        {
            return third;
        }

        /**
         * The second number, rendered as `{3}` after the count, so no existing message changes.
         *
         * @return the number, or zero when this finding is not a comparison
         */
        public int getDetail()
        {
            return detail;
        }

        /**
         * How many things this finding is about (the squares still needing a length, today), rendered as `{2}`.
         *
         * @return the number, or zero when this finding is not about one
         */
        public int getCount()
        {
            return count;
        }

        public Severity getSeverity()
        {
            return severity;
        }

        /**
         * The square the finding is ABOUT, the one a sentence names as `{0}` (OB-341).  It may be filed on another
         * square: a station whose name is drawn twice on another page is filed on the first square it is drawn on,
         * where the reader looks.
         *
         * @return the square it is about; its own square unless it was given another
         */
        public TileKey getAbout()
        {
            return about != null ? about : tile;
        }

        /**
         * Says what the finding is about, where that is not the square it is filed on.
         *
         * @param station the square it is about
         * @return this finding
         */
        Finding about(TileKey station)
        {
            this.about = station;

            return this;
        }

        /**
         * A message bundle key, so presentation stays with whatever is presenting.
         * @return
         */
        public String getMessageKey()
        {
            return messageKey;
        }

        /**
         * What the finding is about - usually a Point name.
         * @return
         */
        public String getSubject()
        {
            return subject;
        }

        /**
         * Where to look on the diagram, or null if it is not about one place.
         * @return
         */
        public TileKey getTile()
        {
            return tile;
        }

        /**
         * The squares it is about beyond its own, which a click on it outlines too - a guard finding's signal, where
         * its own square is the station (MT-505).
         * @return never null; empty for most findings
         */
        public List<TileKey> getRelated()
        {
            return related;
        }

        @Override
        public String toString()
        {
            return severity + " " + messageKey + " [" + subject + "]"
                + (tile == null ? "" : " at " + tile);
        }
    }

    public static final String STATION_REACHES_NOTHING = "autosetup.ui.checkStationReachesNothing";
    public static final String STATION_UNREACHABLE = "autosetup.ui.checkStationUnreachable";

    /**
     * A station set to No - Nothing Can Pass (FR-101), said once in place of the two reachability sentences, which
     * would send the reader to the directions for a square switched out on purpose.
     */
    public static final String STATION_CLOSED = "autosetup.ui.checkStationClosed";
    public static final String TERMINUS_STRANDED = "autosetup.ui.checkTerminusStranded";
    public static final String POINT_ISOLATED = "autosetup.ui.checkPointIsolated";
    public static final String RUN_CLOSED_BOTH_WAYS = "autosetup.ui.checkRunClosedBothWays";

    /**
     * A run of plain track whose squares point opposite ways, so no train can pass it (OB-361).  A WARNING about the
     * run's first square with its squares as the related ones; the direction correction leaves it, as closing it would
     * hide which way was meant.
     */
    public static final String RUN_POINTS_OPPOSITE_WAYS = "autosetup.ui.checkRunPointsOppositeWays";
    public static final String NO_STATIONS = "autosetup.ui.checkNoStations";
    public static final String ONE_STATION = "autosetup.ui.checkOneStation";
    public static final String UNNAMED_POINT = "autosetup.ui.checkUnnamedPoint";
    public static final String UNNAMED_STATION = "autosetup.ui.checkUnnamedStation";
    public static final String UNLABELLED_STATION = "autosetup.ui.checkUnlabelledStation";
    public static final String MAY_TURN_ON_DEAD_END = "autosetup.ui.checkMayTurnOnDeadEnd";
    public static final String REVERSING_LEADS_NOWHERE = "autosetup.ui.checkReversingLeadsNowhere";
    public static final String ARRIVAL_TRAPPED = "autosetup.ui.checkArrivalTrapped";

    /**
     * A trapped arrival whose advice leaves out closing arrivals, because a station's last open way in cannot be shut;
     * otherwise `ARRIVAL_TRAPPED` (`isArrivalTrapped` answers for either).
     */
    public static final String ARRIVAL_TRAPPED_NO_CLOSING = "autosetup.ui.checkArrivalTrappedNoClosing";
    public static final String CAPTION_COVERED = "autosetup.ui.checkCaptionCovered";

    /**
     * A station whose name the track diagram draws in more than one place on one page (OB-341).  What counts is what is
     * DRAWN, by the rule the diagram uses (`AutonomySession.isCaptionDrawn`), and PER PAGE: a caption beside the
     * station on each of two pages is nothing to report.  An ERROR, as an unlabeled station is.  One finding per
     * station, filed on the first square its name is drawn twice on and ABOUT the station (`Finding.getAbout`), with
     * those squares as its related ones and their number as its count.
     */
    public static final String STATION_NAMED_TWICE = "autosetup.ui.checkStationNamedTwice";

    /**
     * One WARNING per sensor address that sensor squares in two or more places share (FR-139).  The places are the
     * squares, each once, named by page and square; a page left out of autonomy has none, and address zero or less is
     * no address.
     */
    public static final String SENSOR_ADDRESS_SHARED = "autosetup.ui.checkSensorAddressShared";

    public static final String HOME_NEEDS_REVERSIBLE = "autosetup.ui.checkHomeNeedsReversible";

    /**
     * A square trains reverse at, on a layout that measures track, whose own track is not measured.
     */
    public static final String REVERSAL_NEEDS_LENGTH = "autosetup.ui.checkReversalNeedsLength";

    /**
     * The same square, when the track leading to it has to be measured as well (OB-171).
     */
    public static final String REVERSAL_NEEDS_LENGTH_RUN = "autosetup.ui.checkReversalNeedsLengthRun";

    /**
     * The three ways one ARRIVAL at a station can be a trap while the square looks healthy: a square is emitted as one
     * Point per side a train can arrive by, and the copies do not share their edges.  Ordered by how stuck the train
     * is: no track out, no track in, and track out that reaches no other station.  All three fire only where the SQUARE
     * is healthy; a stuck square is said by the square-level checks.
     */
    public static final String COPY_NO_WAY_OUT = "autosetup.ui.checkCopyNoWayOut";
    public static final String COPY_NO_WAY_IN = "autosetup.ui.checkCopyNoWayIn";
    public static final String COPY_REACHES_NOTHING = "autosetup.ui.checkCopyReachesNothing";
    public static final String FACING_IMPOSSIBLE = "autosetup.ui.checkFacingImpossible";
    public static final String TRAIN_FACES_TRACK_TOWARDS_IT = "autosetup.ui.checkTrainFacesTrackTowardsIt";
    public static final String STATION_AT_THE_END_OF_A_LINE = "autosetup.ui.checkStationAtTheEndOfALine";

    public static final String SIGNAL_GONE = "autosetup.ui.checkProtectingSignalGone";

    public static final String NO_SIGNAL_PAIRED = "autosetup.ui.checkNoProtectingSignal";

    /**
     * One signal as both guards of one station, which the setters refuse and a loaded file can still carry.
     * A WARNING: the setup runs, and neither guard means what it was paired for.
     */
    public static final String GUARD_IS_BOTH = "autosetup.ui.checkGuardIsBoth";

    /**
     * An entry guard no route into its station commands.  A NOTICE, as all four guard notices:
     * the setup is the operator's to get right, and this is only a pointer.  `AutonomySession.guardNotices` reads all four
     * off the built railway.
     */
    public static final String GUARD_OFF_THE_WAY_IN = "autosetup.ui.checkGuardOffTheWayIn";

    /** An exit guard no route commands at all: it shows only whether its station is occupied. */
    public static final String EXIT_GUARD_OFF_THE_WAY = "autosetup.ui.checkExitGuardOffTheWay";

    /** An exit guard routes to other stations command, so they turn it green over a train standing there. */
    public static final String EXIT_GUARD_CROSSED = "autosetup.ui.checkExitGuardCrossed";

    /**
     * An exit guard a route into its station commands - a home signal: the arriving route turns it green, and
     * the arrival's claim can turn it red in front of that train.  The entry guard is the signal on the way in.
     */
    public static final String EXIT_GUARD_ON_THE_WAY_IN = "autosetup.ui.checkExitGuardOnTheWayIn";

    /**
     * A square unavailable while another is occupied, station or not.  A NOTICE: the restriction
     * is the operator's own setting, and this says where it is - it is kept when a station is made pass-through, and on
     * such a square it is otherwise easy to forget.
     */
    public static final String UNAVAILABLE_WHILE_OCCUPIED = "autosetup.ui.checkUnavailableWhileOccupied";

    /**
     * A station no train can arrive at any more: every way in barred, which a diagram edit can arrive at from the other
     * side.
     */
    public static final String NO_ARRIVALS_LEFT = "autosetup.ui.checkNoArrivalsLeft";

    /**
     * A station every train must turn round at, with track reaching it from more than one side (MT-361).  An ERROR: a
     * square marked "must turn round" is emitted as one Point per arrival side, each leaving the way it came, so two
     * ways in become two termini and the diagram and the marking disagree.  STATIONS ONLY: a must-turn square that is
     * not a station is a plain reversing point, an ordinary mid-layout turn-round (the `BACK_mid` of
     * `core.testNonReversibleTrains`).  Barred arrivals do not count, as shutting a side is one of the remedies the
     * message names.  Counted as sides, not arriving edges, since two edges can reach one square by the same side.
     */
    public static final String TERMINUS_WITH_TWO_WAYS_IN = "autosetup.ui.checkTerminusTwoWaysIn";

    /**
     * One locomotive recorded as standing in two places.  An ERROR, the strongest kind: the running model refuses the
     * whole configuration rather than skipping the second placement, naming nothing that leads back to it.
     */
    public static final String DUPLICATE_LOCOMOTIVE = "autosetup.ui.checkDuplicateLocomotive";

    /**
     * A train standing on the railway with no length recorded (FR-046): a locomotive with no length opts out of the
     * platform-length refusal entirely.  A warning rather than an error, as a layout where nothing is close to too long
     * is a good layout.
     */
    public static final String NO_TRAIN_LENGTH = "autosetup.ui.checkNoTrainLength";

    /**
     * A station told it can hold more train than its track measures.  Two rules can refuse a train a platform, set by
     * different hands: the station's `maxTrainLength` is a number somebody typed, and the measured run in is what the
     * track says, counted back from the platform to whichever of the last switch and a reversal is met first.  For a
     * parking berth the figure is the berth rule's where that stops first (at a crossing between the berth and its
     * switch, or on a leg with no switch), and 0 where every square before that stop was answered 0.  Where the
     * measured run in is smaller the stated maximum never binds.
     *
     * A NOTICE and not a warning, by ruling: a four-unit platform whose track is 2 + 2 across a switch has nothing
     * wrong with it.  Reported per STATION, not per arrival, with the smallest room any approach has, which is the one
     * that binds.
     */
    public static final String RUN_IN_SHORTER_THAN_THE_BERTH = "autosetup.ui.checkRunInShorterThanTheBerth";

    /**
     * The same finding at a station autonomy may choose, where the train is not refused (MT-555):
     * `Layout.whyTooLongForThisRoute` admits a longer train there as far as the measured route in holds (FR-087), so
     * one sentence could not be true of both.
     */
    public static final String RUN_IN_SHORTER_THAN_THE_PLATFORM = "autosetup.ui.checkRunInShorterThanThePlatform";

    /**
     * The same, with the figure a train is refused above on the shortest way in, where there is one.
     * `Layout.whyTooLongForThisRoute` admits a longer train only as far as the measured route in holds it (FR-087), so
     * a sentence without the figure is untrue of a train setting off close behind.  `{4}` is the figure.
     */
    public static final String RUN_IN_SHORTER_THAN_THE_PLATFORM_REFUSED =
        "autosetup.ui.checkRunInShorterThanThePlatformRefused";

    /**
     * A parking berth whose track before its stop - the switch, turnout or crossing behind it - was all answered 0, so it
     * takes no train that comes in that way.  Its own sentence says the effective specified length of the
     * track is 0.  A WARNING, as the half-measured berth is: trains are refused there today.
     */
    public static final String BERTH_TRACK_GIVEN_NO_LENGTH = "autosetup.ui.checkBerthTrackGivenNoLength";

    /**
     * The other end of the same sum: a station that will take a train of any length (FR-046).  Said separately because
     * the length (on the locomotive) and the maximum (on the platform) are set in different places.
     */
    public static final String NO_MAX_TRAIN_LENGTH = "autosetup.ui.checkNoMaxTrainLength";

    /**
     * Two included pages carrying the same s88 sensor (OB-150).  An ERROR: one sensor on two squares becomes two Points
     * after the reduction and nothing can say which a train is on.  `excludeRepeatedSensorPages` shuts a repeating page
     * at creation (and a legacy import, OB-130); this covers a page switched back on by hand afterwards.
     */
    public static final String DUPLICATE_SENSOR_PAGE = "autosetup.ui.checkDuplicateSensorPage";

    /**
     * A berth whose approach is measured in part and not in whole.  A WARNING rather than a notice, because while it
     * stands the berth can refuse a train that fits: the berth rule judges as soon as anything on the approach is
     * measured.  CAN refuse, not does: the claim becomes a refusal only where a lock edge shares one of the claimed
     * places, and `whyABerthCannotHoldIt` returns at once for a locomotive with no train length.
     */
    public static final String HALF_MEASURED_APPROACH = "autosetup.ui.checkHalfMeasuredApproach";

    private AutonomyChecks()
    {
    }

    // ONE ENTRY POINT, no convenience overloads: the only call site is `AutonomySession.check()`, and a default that
    // silently drops an argument is how a defect happens twice (an empty `barred` map made a check blind to barred
    // arrivals, OB-120).

    /**
     * @param arrivalsClosable the trapped squares whose trapped sides the arrivals menu can close
     * @param withoutTrainLength squares whose locomotive has no length recorded, to that locomotive (FR-046)
     * @param repeatedSensorPages included pages repeating another included page's s88 (OB-150)
     * @param withoutMaxLength station squares with no maximum train length
     * @param shortRunIns stations whose measured run in is shorter than their stated maximum, each
     *        mapped to {the stated maximum, the smallest measured room}, and at a platform the
     *        figure a train is refused above third, where there is one
     * @param berthsGivenNoRoom parking berths whose track before the stop was all answered 0
     * @param terminiWithTwoWaysIn stations every train must turn round at that have more than one
     *        unbarred way in, mapped to how many (MT-361)
     * @param berthsBackedInto the squares a train can be backed into - reached tail first, after a rest on another
     *        terminus or a reversing point passed on the way - off the build; null where the setup does not build
     * @param reach each square to the other stations a train there can be sent to, off the build (`BuiltReach`); null
     *        where the setup does not build, and then nothing is said about reachability
     *
     * The two halves of the length comparison are supplied rather than derived: neither is visible from the graph.
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Map<TileKey, Set<TilePorts.Side>> trapped,
        Set<TileKey> arrivalsClosable, Map<TileKey, TileKey> coveredCaptions, Map<TileKey, String> placedLocomotives,
        Map<TileKey, Boolean> shutStations, Set<TileKey> mayTurn, Set<TileKey> mustTurn,
        Set<TileKey> homes, Set<TileKey> signalsGone, Set<TileKey> stationsWithoutSignal,
        Set<TileKey> facingsImpossible, Map<TileKey, Set<TileKey>> reach,
        Set<TileKey> closed,
        Map<TileKey, String> withoutTrainLength, Set<TileKey> withoutMaxLength,
        Map<TileKey, int[]> shortRunIns, Set<TileKey> berthsGivenNoRoom, Map<TileKey, Integer> halfMeasured,
        Map<TileKey, Integer> terminiWithTwoWaysIn,
        Map<TileKey, String> repeatedSensorPages, Map<TileKey, Integer> reversalsWithoutLength,
        Set<TileKey> notAutoDestinations, Set<TileKey> berthsBackedInto,
        Map<TileKey, String> copiesWithNoWayOut, Map<TileKey, String> copiesWithNoWayIn,
        Map<TileKey, String> copiesReachingNoStation,
        Map<TileKey, Map<TileKey, String>> guardsOnBothLists, Map<TileKey, Map<TileKey, String>> guardsOffTheWayIn,
        Map<TileKey, Map<TileKey, String>> exitGuardsOffTheWay, Map<TileKey, Map<TileKey, String>> exitGuardsCrossed,
        Map<TileKey, Map<TileKey, String>> exitGuardsOnTheWayIn, Map<TileKey, String> restrictions)
    {
        List<Finding> findings = new ArrayList<>();

        findings.addAll(checkFacings(reducer, facingsImpossible));

        findings.addAll(checkDuplicateLocomotives(placedLocomotives));
        findings.addAll(checkLengths(reducer, withoutTrainLength, withoutMaxLength, shortRunIns,
            berthsGivenNoRoom, halfMeasured, placedLocomotives, notAutoDestinations));
        findings.addAll(checkRepeatedSensorPages(repeatedSensorPages));
        findings.addAll(checkTermini(reducer, terminiWithTwoWaysIn));

        findings.addAll(checkArrivalsLeft(reducer, shutStations));

        // whatever the diagram itself is unhappy about - scissors, unaddressed switches, turntables
        for (TileGraph.Problem problem : graph.getProblems())
        {
            findings.add(new Finding(
                problem.isBlocking() ? Severity.ERROR : Severity.WARNING,
                problem.getMessageKey(), String.valueOf(problem.getTile()), problem.getTile()));
        }

        for (TileGraph.Problem problem : reducer.getProblems())
        {
            findings.add(new Finding(
                problem.isBlocking() ? Severity.ERROR : Severity.WARNING,
                problem.getMessageKey(), String.valueOf(problem.getTile()), problem.getTile()));
        }

        findings.addAll(checkNames(reducer));
        findings.addAll(checkTurning(reducer, mayTurnOnDeadEnd));

        findings.addAll(checkReversingGoesSomewhere(reducer, mayTurn, mustTurn, reach, notAutoDestinations));
        findings.addAll(checkTrappedArrivals(reducer, trapped, arrivalsClosable));
        findings.addAll(checkCoveredCaptions(reducer, coveredCaptions));
        findings.addAll(checkStations(reducer, termini, mustTurn, closed, reach));
        findings.addAll(checkStationLabels(reducer, labelledStations));
        findings.addAll(checkIsolatedPoints(reducer));
        findings.addAll(checkClosedRuns(graph, reducer));
        findings.addAll(checkHomesThatNeedReversing(reducer, homes, mustTurn, notAutoDestinations, berthsBackedInto));
        findings.addAll(checkReversalsWithoutLength(reducer, reversalsWithoutLength));
        findings.addAll(checkBadCopies(copiesWithNoWayOut, COPY_NO_WAY_OUT));
        findings.addAll(checkBadCopies(copiesWithNoWayIn, COPY_NO_WAY_IN));
        findings.addAll(checkBadCopies(copiesReachingNoStation, COPY_REACHES_NOTHING));
        findings.addAll(checkProtectingSignals(reducer, signalsGone));

        findings.addAll(checkStationsWithoutSignals(reducer, stationsWithoutSignal));

        findings.addAll(checkGuards(guardsOnBothLists, GUARD_IS_BOTH, Severity.WARNING));
        findings.addAll(checkGuards(guardsOffTheWayIn, GUARD_OFF_THE_WAY_IN, Severity.NOTICE));
        findings.addAll(checkGuards(exitGuardsOffTheWay, EXIT_GUARD_OFF_THE_WAY, Severity.NOTICE));
        findings.addAll(checkGuards(exitGuardsCrossed, EXIT_GUARD_CROSSED, Severity.NOTICE));
        findings.addAll(checkGuards(exitGuardsOnTheWayIn, EXIT_GUARD_ON_THE_WAY_IN, Severity.NOTICE));

        // Every restriction, where it is: the square is `{0}`, what it watches `{1}`.
        if (restrictions != null)
        {
            for (Map.Entry<TileKey, String> held : restrictions.entrySet())
            {
                findings.add(new Finding(Severity.NOTICE, UNAVAILABLE_WHILE_OCCUPIED, held.getValue(), held.getKey()));
            }
        }

        bySeverity(findings);

        return findings;
    }

    /**
     * The findings in order of severity, the worst first.
     *
     * @param findings the findings, sorted in place
     */
    static void bySeverity(List<Finding> findings)
    {
        Collections.sort(findings, new java.util.Comparator<Finding>()
        {
            @Override
            public int compare(Finding a, Finding b)
            {
                return a.getSeverity().ordinal() - b.getSeverity().ordinal();
            }
        });
    }

    /** What a finding calls a square: its point's name, or the square itself where it has no point. */
    private static String nameOf(GraphReducer reducer, TileKey tile)
    {
        ReducedPoint point = reducer == null ? null : reducer.getPoints().get(tile);

        return point == null ? String.valueOf(tile) : point.getName();
    }

    /**
     * A WARNING per square where a standing train faces track that runs only towards it: it cannot
     * leave that way - smaller than a refusal, and judged near the train.
     *
     * @param reducer the reduction, for each square's name
     * @param squares the squares
     * @return the findings
     */
    static List<Finding> checkTrainsFacingTrackTowardsThem(GraphReducer reducer, Set<TileKey> squares)
    {
        List<Finding> findings = new ArrayList<>();

        for (TileKey tile : squares)
        {
            findings.add(new Finding(Severity.WARNING, TRAIN_FACES_TRACK_TOWARDS_IT,
                nameOf(reducer, tile), tile));
        }

        return findings;
    }

    /**
     * A WARNING per run of plain track whose squares point opposite ways (OB-361): `{0}` is its first square, `{1}` its
     * last, and its squares are the related ones.
     *
     * @param runs each run's first square to its squares, in order
     * @return the findings
     */
    static List<Finding> checkRunsPointingOppositeWays(Map<TileKey, List<TileKey>> runs)
    {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<TileKey, List<TileKey>> run : runs.entrySet())
        {
            TileKey last = run.getValue().get(run.getValue().size() - 1);

            findings.add(new Finding(Severity.WARNING, RUN_POINTS_OPPOSITE_WAYS, last.getX() + "," + last.getY(),
                run.getKey(), run.getValue()));
        }

        return findings;
    }

    /**
     * An ERROR per station at the end of a line trains may not turn at: a train sent there could never
     * leave.
     *
     * @param reducer the reduction, for each square's name
     * @param squares the squares
     * @return the findings
     */
    static List<Finding> checkStationsAtTheEndOfALine(GraphReducer reducer, Set<TileKey> squares)
    {
        List<Finding> findings = new ArrayList<>();

        for (TileKey tile : squares)
        {
            findings.add(new Finding(Severity.ERROR, STATION_AT_THE_END_OF_A_LINE,
                nameOf(reducer, tile), tile));
        }

        return findings;
    }

    /**
     * An ERROR per station whose name the track diagram draws in more than one place (OB-341), filed on the first of
     * the squares it is drawn twice on, in reading order, and ABOUT the station, whose name is the sentence's {0}.  The
     * subject is those squares in words ("(6,16) and (8,16)"), with the page added to one on a page other than the
     * station's.
     *
     * @param shown each station's square, to the squares of the pages that draw its name more than once
     * @return the findings
     */
    static List<Finding> checkStationsNamedTwice(Map<TileKey, List<TileKey>> shown)
    {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<TileKey, List<TileKey>> station : shown.entrySet())
        {
            List<String> squares = new ArrayList<>();

            for (TileKey square : station.getValue())
            {
                squares.add("(" + (square.getPage().equals(station.getKey().getPage())
                    ? square.getX() + "," + square.getY() : GraphReducer.generatedName(square)) + ")");
            }

            findings.add(new Finding(Severity.ERROR, STATION_NAMED_TWICE, andJoined(squares), station.getValue().get(0),
                station.getValue().size(), 0, 0, station.getValue()).about(station.getKey()));
        }

        return findings;
    }

    /**
     * One WARNING per sensor address that sensor squares in two or more places share (FR-139): a train reported by one
     * of them cannot be told from the other.  It changes nothing about how trains run.
     *
     * @param shared each shared address, to the squares that carry it, in reading order
     * @return the findings
     */
    static List<Finding> checkSensorAddressesShared(Map<Integer, List<TileKey>> shared)
    {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<Integer, List<TileKey>> address : shared.entrySet())
        {
            List<String> places = new ArrayList<>();

            for (TileKey square : address.getValue())
            {
                places.add("\"" + square.getPage() + "\" (" + square.getX() + "," + square.getY() + ")");
            }

            findings.add(new Finding(Severity.WARNING, SENSOR_ADDRESS_SHARED, andJoined(places),
                address.getValue().get(0), address.getValue().size(), address.getKey(), 0, address.getValue()));
        }

        return findings;
    }

    /**
     * Has everything been given a name?  A name still carrying what the reducer invented (page and coordinate) tells
     * the reader nothing in a running log.  A station is somewhere a train is SENT and picked by name, so an unnamed
     * one is blocking; a plain point only appears in a path, so it is worth saying and not worth refusing over (a
     * layout has dozens).
     */
    private static List<Finding> checkNames(GraphReducer reducer)
    {
        List<Finding> findings = new ArrayList<>();

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (point.getName() != null
                    && !point.getName().equals(GraphReducer.generatedName(point.getTile()))) continue;

            // A station without a name is an ERROR (the user picks a destination by it); a plain point stays a NOTICE,
            // as dozens of them listed beside real problems would bury those.
            findings.add(new Finding(
                point.isStation() ? Severity.ERROR : Severity.NOTICE,
                point.isStation() ? UNNAMED_STATION : UNNAMED_POINT,
                String.valueOf(point.getTile()), point.getTile()));
        }

        return findings;
    }

    /**
     * "May change direction" where there is nowhere else to go: every way in leads back out the way it came, so they
     * all turn whatever the setting says.  Judged per ARRIVAL (OB-123), not by counting the ways in: a square with two
     * ways in and one way out forces a turn on the train that came in by the departure side and offers a real choice to
     * the other.
     */
    private static List<Finding> checkTurning(GraphReducer reducer, Set<TileKey> mayTurnOnDeadEnd)
    {
        List<Finding> findings = new ArrayList<>();

        for (TileKey tile : mayTurnOnDeadEnd)
        {
            findings.add(new Finding(Severity.NOTICE, MAY_TURN_ON_DEAD_END,
                nameOf(reducer, tile), tile));
        }

        return findings;
    }

    /**
     * A square trains turn round at, from which nothing can be reached (OB-113).  It fills a hole the other checks
     * open: `ARRIVAL_TRAPPED` advises "set 'trains may change direction here'", which silences it whether or not the
     * way ahead was ever opened, and `TERMINUS_STRANDED` and `STATION_REACHES_NOTHING` watch stations only.
     * Reachability is the build's, as the station checks read it.  A NOTICE, not a warning: "reaches no station" is
     * also true of a reversing point on a spur somebody is still drawing.
     *
     * @param reducer the reduced graph
     * @param mayTurn squares where a train may change direction
     * @param mustTurn squares where it must
     * @param reach each square to the stations reachable from it, or null to say nothing
     * @return one finding per reversing square that leads nowhere
     */
    private static List<Finding> checkReversingGoesSomewhere(GraphReducer reducer,
        Set<TileKey> mayTurn, Set<TileKey> mustTurn, Map<TileKey, Set<TileKey>> reach,
        Set<TileKey> notAutoDestinations)
    {
        List<Finding> findings = new ArrayList<>();

        if (reach == null) return findings;

        Set<TileKey> reversing = new LinkedHashSet<>();

        reversing.addAll(mayTurn);
        reversing.addAll(mustTurn);

        // SOMEWHERE AUTONOMY WOULD ACTUALLY SEND A TRAIN, not merely a station: `Layout.isSendableDestination` requires
        // `isActive() && isAutoDestination() && !isReversing()`, and the set handed in asks the two a SQUARE can
        // answer.  A reversing point whose only reachable station is a parking berth is useless in the run.  The
        // runtime's other two clauses take a locomotive, and this notice is drawn on a square with no train in the
        // question (FR-058).  A notice, as it is the one severity that can carry the many squares that are not
        // automatic destinations without drowning the list.
        Set<TileKey> stationTiles = new LinkedHashSet<>();

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (!point.isStation()) continue;

            if (notAutoDestinations.contains(point.getTile())) continue;

            // NOT ASKED AGAIN HERE (OB-195): `notAutoDestinations` comes from `stationsAutonomyWillNotChoose`, a
            // question about a SQUARE.  `reversing` here is may-turn and must-turn together, and re-applying it would
            // have the stricter rule override the narrower: a compulsory turn autonomy may choose IS somewhere a
            // reversing square's run can lead.  Trusted once, from the parameter, which is where the decision was made.

            stationTiles.add(point.getTile());
        }

        for (TileKey tile : reversing)
        {
            ReducedPoint point = reducer.getPoints().get(tile);

            if (point == null) continue;

            // Stations are the other two checks' business, and saying it twice about one square is
            // how a list of findings stops being read.
            if (point.isStation()) continue;

            Set<TileKey> reachable = reach.getOrDefault(tile, Collections.<TileKey>emptySet());

            boolean reachesAStation = false;

            for (TileKey station : stationTiles)
            {
                if (reachable.contains(station))
                {
                    reachesAStation = true;
                    break;
                }
            }

            if (!reachesAStation)
            {
                findings.add(new Finding(Severity.NOTICE, REVERSING_LEADS_NOWHERE,
                    point.getName() == null ? String.valueOf(tile) : point.getName(), tile));
            }
        }

        return findings;
    }

    /**
     * A station with no signal paired to it at all.  A NOTICE rather than a warning: most layouts protect some
     * platforms and not others.  It lists the stations that have none, the half of the pairing audit a check can do
     * without being told what the layout should look like.
     *
     * @param reducer for the station's own name
     * @param stationsWithoutSignal the station squares carrying no pairing
     */
    private static List<Finding> checkStationsWithoutSignals(GraphReducer reducer,
        Set<TileKey> stationsWithoutSignal)
    {
        List<Finding> findings = new ArrayList<>();

        if (stationsWithoutSignal == null) return findings;

        for (TileKey tile : stationsWithoutSignal)
        {
            findings.add(new Finding(Severity.NOTICE, NO_SIGNAL_PAIRED,
                nameOf(reducer, tile), tile));
        }

        return findings;
    }

    /**
     * A train recorded facing a way its square cannot hold.  The build puts it on the first copy trains may arrive at
     * (`startableCopy`), which may point the other way; only the operator can say which way it really points.
     */
    private static List<Finding> checkFacings(GraphReducer reducer, Set<TileKey> facingsImpossible)
    {
        List<Finding> findings = new ArrayList<>();

        if (facingsImpossible == null) return findings;

        for (TileKey tile : facingsImpossible)
        {
            // AN ERROR: the train cannot be stood on a copy facing another way, so nothing is sent until it is put
            // right
            findings.add(new Finding(Severity.ERROR, FACING_IMPOSSIBLE,
                nameOf(reducer, tile), tile));
        }

        return findings;
    }

    /**
     * One finding per station and signal: the station is `{0}` and the signal's name `{1}`.
     *
     * @param guards the stations, against the names of their signals the finding is about
     * @param key the message
     * @param severity how loudly
     */
    private static List<Finding> checkGuards(Map<TileKey, Map<TileKey, String>> guards, String key, Severity severity)
    {
        List<Finding> findings = new ArrayList<>();

        if (guards == null) return findings;

        // Each about its station, and naming the signal's square as well, so a click shows the signal (MT-505)
        for (Map.Entry<TileKey, Map<TileKey, String>> station : guards.entrySet())
        {
            for (Map.Entry<TileKey, String> signal : station.getValue().entrySet())
            {
                findings.add(new Finding(severity, key, signal.getValue(), station.getKey(),
                    Collections.singletonList(signal.getKey())));
            }
        }

        return findings;
    }

    private static List<Finding> checkProtectingSignals(GraphReducer reducer, Set<TileKey> signalsGone)
    {
        List<Finding> findings = new ArrayList<>();

        if (signalsGone == null) return findings;

        for (TileKey tile : signalsGone)
        {
            findings.add(new Finding(Severity.WARNING, SIGNAL_GONE,
                nameOf(reducer, tile), tile));
        }

        return findings;
    }

    /**
     * A square trains reverse at, whose track has no recorded length.  A train reversing into a berth shorter than
     * itself stands across the switch behind it, and `isPathClear` refuses that only where the track has been measured,
     * unmeasured track being unknown rather than short.  The squares are decided by the session
     * (`AutonomySession.reversalsWithoutLength`, where how they differ from where the guard cannot do its job is
     * written once).  Only on a layout that measures track at all.  A WARNING, not an error: nothing is broken, the
     * guard simply cannot protect this square until it is measured.
     *
     * @param reversalsWithoutLength the squares, decided by the session
     */
    private static List<Finding> checkReversalsWithoutLength(GraphReducer reducer,
        Map<TileKey, Integer> reversalsWithoutLength)
    {
        List<Finding> findings = new ArrayList<>();

        if (reversalsWithoutLength == null) return findings;

        for (Map.Entry<TileKey, Integer> reversal : reversalsWithoutLength.entrySet())
        {
            TileKey tile = reversal.getKey();

            String name = nameOf(reducer, tile);

            // ONE SQUARE, OR A RUN OF THEM (OB-171): the count is every square still without a length that this
            // reversal's guard needs.  Two messages rather than one with a number in it, so no "1 squares".
            int missing = reversal.getValue() == null ? 1 : reversal.getValue();

            findings.add(new Finding(Severity.WARNING,
                missing > 1 ? REVERSAL_NEEDS_LENGTH_RUN : REVERSAL_NEEDS_LENGTH, name, tile, missing));
        }

        return findings;
    }

    /**
     * A square that emits an arrival copy nothing can leave, or nothing can reach.  Every other check here asks about
     * the SQUARE, so none catch it: a square is emitted as one Point per side a train can arrive by and the copies do
     * not share their edges, so a square can pass every reachability test while one copy is a trap.  ALL THREE ARE
     * WARNINGS: `errorCount() > 0` refuses to start autonomy at all.
     *
     * @param copies the squares, mapped to the offending copy, decided by the session
     * @param key which of the two messages to report
     */
    private static List<Finding> checkBadCopies(Map<TileKey, String> copies, String key)
    {
        List<Finding> findings = new ArrayList<>();

        if (copies == null) return findings;

        // A WARNING, all three: errorCount() > 0 refuses to start autonomy at all.  Nothing downstream refuses the trip
        // either, so the train really can be sent there: filter at selection, never refuse at execution, and tell the
        // operator what the diagram says.
        Severity severity = Severity.WARNING;

        for (Map.Entry<TileKey, String> entry : copies.entrySet())
        {
            findings.add(new Finding(severity, key, entry.getValue(), entry.getKey()));
        }

        return findings;
    }


    /**
     * A home on a square every train must turn round at: such a square is emitted as turning copies only, and a turning
     * station copy is a TERMINUS.  THIS NOTICE IS THE ONLY WARNING THERE IS: Return Home is manual operation, so
     * staging does not refuse a terminus to a locomotive that cannot reverse; it is driven into the berth NOSE FIRST
     * and has to back out, which nothing in the software will ask.  Not an error and not something to fix, so the
     * wording is about backing out rather than being refused.
     *
     * ONLY AT A PARKING BERTH NO ROUTE CAN BACK A TRAIN INTO (MT-552): a terminus autonomy may choose is neither chosen
     * for nor offered to a locomotive that cannot reverse, and at a parking berth a train can be backed into (rested
     * first on another terminus, or turned at a reversing point on the way, as Return Home does) non-reversible trains
     * can still be backed in.
     *
     * @param homes the squares carrying an authored home
     * @param mustTurn the squares where turning round is compulsory
     * @param notAutoDestinations the stations autonomy never chooses - the parking berths
     * @param berthsBackedInto the squares a route can back a train into; null where the setup does not build, and then
     *        nothing is said
     */
    private static List<Finding> checkHomesThatNeedReversing(GraphReducer reducer,
        Set<TileKey> homes, Set<TileKey> mustTurn, Set<TileKey> notAutoDestinations, Set<TileKey> berthsBackedInto)
    {
        List<Finding> findings = new ArrayList<>();

        if (homes == null || mustTurn == null) return findings;

        for (TileKey tile : homes)
        {
            if (!mustTurn.contains(tile)) continue;

            // a parking berth, or nothing to say
            if (notAutoDestinations == null || !notAutoDestinations.contains(tile)) continue;

            // and one no route backs a train into
            if (berthsBackedInto == null || berthsBackedInto.contains(tile)) continue;

            ReducedPoint point = reducer.getPoints().get(tile);

            if (point == null || !point.isStation()) continue;

            findings.add(new Finding(Severity.WARNING, HOME_NEEDS_REVERSIBLE,
                point.getName(), tile));
        }

        return findings;
    }

    /**
     * Squares a train can reach and then not leave: a train arriving is pointing away from where it came from, so a
     * square whose only way on is back is the end of its day unless trains may turn round there.  Usually the square IS
     * a terminus and wants marking, or a branch off it is shut that should be open.
     *
     * @param closable the squares whose trapped sides the arrivals menu can close, the only ones told to
     */
    private static List<Finding> checkTrappedArrivals(GraphReducer reducer, Map<TileKey, Set<TilePorts.Side>> trapped,
        Set<TileKey> closable)
    {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<TileKey, Set<TilePorts.Side>> square : trapped.entrySet())
        {
            TileKey tile = square.getKey();

            // A STATION'S, as `AutonomySession` gathers them: "could not go on" warns about a place a train can be SENT
            // and then be stuck, and nothing is sent to a plain point (OB-361).
            //
            // ITS SUBJECT IS THE SIDE THE TRAIN COMES IN BY, in words (OB-201): the square is {0} wherever a finding is
            // shown, and the side is {1}.
            //
            // CLOSING ARRIVALS IS OFFERED ONLY WHERE THE EDITOR CAN DO IT: on a station, for sides that are not its
            // last open way in (the menu refuses to leave none); there the advice is the other two.
            findings.add(new Finding(Severity.WARNING,
                closable != null && closable.contains(tile) ? ARRIVAL_TRAPPED : ARRIVAL_TRAPPED_NO_CLOSING,
                sidesInWords(square.getValue()), tile));
        }

        return findings;
    }

    /**
     * Whether a finding is a trapped arrival, whichever advice it gives.
     *
     * @param messageKey a finding's key
     * @return true for `ARRIVAL_TRAPPED` and `ARRIVAL_TRAPPED_NO_CLOSING`
     */
    public static boolean isArrivalTrapped(String messageKey)
    {
        return ARRIVAL_TRAPPED.equals(messageKey) || ARRIVAL_TRAPPED_NO_CLOSING.equals(messageKey);
    }

    /**
     * Sides in words, as the arrivals menu names them: "west", "west and north", "west, north and east".
     *
     * @param sides the sides
     * @return the words
     */
    static String sidesInWords(java.util.Collection<TilePorts.Side> sides)
    {
        List<String> words = new ArrayList<>();

        for (TilePorts.Side side : sides) words.add(org.traincontrol.util.I18n.t("autosetup.ui.side" + side.name()));

        return andJoined(words);
    }

    /**
     * Words in a list the way a sentence has it: "a", "a and b", "a, b and c".
     *
     * @param words the words
     * @return the list, empty for none
     */
    private static String andJoined(List<String> words)
    {
        if (words.size() < 2) return words.isEmpty() ? "" : words.get(0);

        return String.join(", ", words.subList(0, words.size() - 1)) + " "
            + org.traincontrol.util.I18n.t("route.ui.joinAnd") + " " + words.get(words.size() - 1);
    }

    /**
     * A station's caption and the user's own writing on the same square.  The grid draws the CAPTION and not the text
     * (MT-017), so it is the text that is hidden; nothing is deleted, and the answer is to move the caption or clear
     * the text.
     */
    private static List<Finding> checkCoveredCaptions(GraphReducer reducer,
        Map<TileKey, TileKey> covered)
    {
        List<Finding> findings = new ArrayList<>();

        if (covered == null) return findings;

        for (Map.Entry<TileKey, TileKey> entry : covered.entrySet())
        {
            findings.add(new Finding(Severity.WARNING, CAPTION_COVERED,
                nameOf(reducer, entry.getValue()),
                entry.getKey()));
        }

        return findings;
    }


    /**
     * Included pages that repeat another included page's sensors (OB-150).  The subject is a PAGE, the thing that can
     * be switched back off, rather than a square: naming one of the two squares would send the reader to an
     * innocent-looking sensor.
     *
     * @param pages the offending page names, worked out by the caller against the store's own list of
     *        what is included
     * @return one error per page
     */
    private static List<Finding> checkRepeatedSensorPages(Map<TileKey, String> repeats)
    {
        List<Finding> findings = new ArrayList<>();

        if (repeats == null) return findings;

        for (Map.Entry<TileKey, String> repeat : repeats.entrySet())
        {
            // WITH ITS SQUARE (MT-223): the message names a specific s88, and a finding that names a square and cannot
            // take you to it is the worst of both.
            findings.add(new Finding(Severity.ERROR, DUPLICATE_SENSOR_PAGE,
                repeat.getValue(), repeat.getKey()));
        }

        return findings;
    }
    /**
     * Trains with no length, and stations with no maximum (FR-046).  Handed the answers as sets of squares rather than
     * worked out, as every other check here is: the maximum is a point property the session holds and a train's length
     * is on the Locomotive, which nothing in this class can reach.
     *
     * @param withoutTrainLength squares holding a locomotive whose length is not set, to that locomotive
     * @param withoutMaxLength station squares with no maximum train length
     * @param placed which locomotive stands where, for naming the train in the message
     * @return one warning per square
     */
    private static List<Finding> checkLengths(GraphReducer reducer, Map<TileKey, String> withoutTrainLength,
        Set<TileKey> withoutMaxLength, Map<TileKey, int[]> shortRunIns, Set<TileKey> berthsGivenNoRoom,
        Map<TileKey, Integer> halfMeasured, Map<TileKey, String> placed, Set<TileKey> notAutoDestinations)
    {
        List<Finding> findings = new ArrayList<>();

        // THE BERTH GIVEN NO ROOM: it takes no train that comes in over the track answered 0 - said first with the
        // half-measured berth, because both stop trains today.
        if (berthsGivenNoRoom != null)
        {
            for (TileKey berth : berthsGivenNoRoom)
            {
                findings.add(new Finding(Severity.WARNING, BERTH_TRACK_GIVEN_NO_LENGTH,
                    nameOf(reducer, berth), berth));
            }
        }

        // THE BERTH THAT TAKES NOTHING AT ALL, said first because it is the one that stops trains today.
        if (halfMeasured != null)
        {
            for (Map.Entry<TileKey, Integer> station : halfMeasured.entrySet())
            {
                findings.add(new Finding(Severity.WARNING, HALF_MEASURED_APPROACH,
                    nameOf(reducer, station.getKey()),
                    station.getKey(), station.getValue()));
            }
        }

        for (Map.Entry<TileKey, String> train : withoutTrainLength.entrySet())
        {
            // Named, because "a train has no length" sends the reader looking at seven of them: the square is what the
            // editor jumps to, the name says which train to measure (the one standing there, which after a run is not
            // always the one recorded).
            TileKey square = train.getKey();

            String standing = train.getValue();

            findings.add(new Finding(Severity.WARNING, NO_TRAIN_LENGTH,
                standing == null ? "" : standing, square));
        }

        for (TileKey square : withoutMaxLength)
        {
            // The station's name as the subject, the way every other finding about a station does it -
            // the editor jumps to the square, and the sentence needs something to be about.
            findings.add(new Finding(Severity.WARNING, NO_MAX_TRAIN_LENGTH,
                nameOf(reducer, square), square));
        }

        // AND THE TWO OF THEM DISAGREEING, which is the case where both are filled in.  The stated
        // maximum first, because it is the number the reader typed; the measured room second, because
        // it is the one that will actually refuse the train.
        if (shortRunIns != null)
        {
            for (Map.Entry<TileKey, int[]> station : shortRunIns.entrySet())
            {
                // REFUSED AT A BERTH, STANDING ACROSS THE SWITCH AT A PLATFORM (MT-555): the sentence says which.
                boolean berth = notAutoDestinations != null && notAutoDestinations.contains(station.getKey());

                // AND AT A PLATFORM, THE FIGURE A TRAIN IS REFUSED ABOVE, where there is one.
                int refused = !berth && station.getValue().length > 2 ? station.getValue()[2] : 0;

                findings.add(new Finding(Severity.NOTICE,
                    berth ? RUN_IN_SHORTER_THAN_THE_BERTH
                        : refused > 0 ? RUN_IN_SHORTER_THAN_THE_PLATFORM_REFUSED : RUN_IN_SHORTER_THAN_THE_PLATFORM,
                    nameOf(reducer, station.getKey()),
                    station.getKey(), station.getValue()[0], station.getValue()[1], refused));
            }
        }

        return findings;
    }

    /**
     * Stations that must turn every train round and have more than one way in (MT-361).  Handed the answer, like every
     * other check here that needs to know something about the setup: whether a square is a station lives on the store.
     *
     * @param reducer for the names
     * @param termini the squares, mapped to how many unbarred sides reach them
     * @return one error per square
     */
    private static List<Finding> checkTermini(GraphReducer reducer, Map<TileKey, Integer> termini)
    {
        List<Finding> findings = new ArrayList<>();

        if (termini == null) return findings;

        for (Map.Entry<TileKey, Integer> each : termini.entrySet())
        {
            findings.add(new Finding(Severity.ERROR, TERMINUS_WITH_TWO_WAYS_IN,
                nameOf(reducer, each.getKey()), each.getKey(),
                each.getValue()));
        }

        return findings;
    }

    /**
     * Is any locomotive recorded as standing in two places?  The running layout enforces one place per locomotive when
     * a train MOVES, but the configuration is a different store, so a hand placement could sit beside one an import
     * wrote.  The consequence is out of all proportion: fromJSON refuses the whole layout and every path is answered
     * with "configuration is invalid".  Reported against the SECOND square and any after it: the check cannot tell
     * which is the leftover, and listing all would say the setup has two problems when it has one.
     */
    private static List<Finding> checkDuplicateLocomotives(Map<TileKey, String> placed)
    {
        List<Finding> findings = new ArrayList<>();

        if (placed == null) return findings;

        Map<String, List<TileKey>> where = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> entry : placed.entrySet())
        {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            if (entry.getValue().trim().isEmpty()) continue;

            if (!where.containsKey(entry.getValue()))
            {
                where.put(entry.getValue(), new ArrayList<TileKey>());
            }

            where.get(entry.getValue()).add(entry.getKey());
        }

        for (Map.Entry<String, List<TileKey>> entry : where.entrySet())
        {
            List<TileKey> squares = entry.getValue();

            if (squares.size() < 2) continue;

            for (int extra = 1; extra < squares.size(); extra++)
            {
                // The subject is the locomotive, but the editor shows the SQUARE instead whenever a finding carries
                // one, so the message reads against the square and the name is carried for anything that wants it.
                findings.add(new Finding(Severity.ERROR, DUPLICATE_LOCOMOTIVE,
                    entry.getKey(), squares.get(extra)));
            }
        }

        return findings;
    }

    /**
     * Is any station shut to trains from every direction?  The editor will not let somebody tick the last way in, so
     * this is for the ways round it (a diagram edited after the restriction, a track removed, a setup written by hand);
     * the station would quietly never appear as a destination again.
     */
    private static List<Finding> checkArrivalsLeft(GraphReducer reducer, Map<TileKey, Boolean> shut)
    {
        List<Finding> findings = new ArrayList<>();

        if (shut == null) return findings;

        for (Map.Entry<TileKey, Boolean> entry : shut.entrySet())
        {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;

            // By name, like every other check: the editor substitutes the square's description for the subject of a
            // finding that carries a tile.
            ReducedPoint point = reducer == null ? null : reducer.getPoints().get(entry.getKey());

            // INFORMATION, not an error (MT-078): barring every side stops AUTONOMY sending a train here but not a
            // person driving one in, because a bar is advisory, so blocking the whole setup over a platform the
            // operator can still use would be wrong.  Nothing can come in ANY way is a different condition and already
            // has POINT_ISOLATED.
            findings.add(new Finding(Severity.INFO, NO_ARRIVALS_LEFT,
                point == null || point.getName() == null || point.getName().trim().isEmpty()
                    ? entry.getKey().toString() : point.getName(),
                entry.getKey()));
        }

        return findings;
    }

    /**
     * Is every station shown on the track diagram?  A station with no label is a place the user cannot see, and
     * autonomy would announce arrivals at it.  An error, on the author's instruction.  A station still carrying its
     * generated name is skipped, because naming it comes first and two rows about one sensor is a list nobody finishes.
     */
    private static List<Finding> checkStationLabels(GraphReducer reducer, Set<TileKey> labelled)
    {
        List<Finding> findings = new ArrayList<>();

        if (labelled == null) return findings;

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (!point.isStation() || point.getName() == null) continue;

            if (point.getName().equals(GraphReducer.generatedName(point.getTile()))) continue;

            // Asked by SQUARE, not by name: matching the caption's text against the Point's name is a fragile join.
            if (!labelled.contains(point.getTile()))
            {
                findings.add(new Finding(Severity.ERROR, UNLABELLED_STATION,
                    point.getName(), point.getTile()));
            }
        }

        return findings;
    }

    private static List<Finding> checkStations(GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> mustTurn, Set<TileKey> closed, Map<TileKey, Set<TileKey>> reach)
    {
        List<Finding> findings = new ArrayList<>();

        List<ReducedPoint> stations = new ArrayList<>();

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (point.isStation()) stations.add(point);
        }

        if (stations.isEmpty())
        {
            // An error, not a warning.  Autonomy moves trains between stations, so a setup with none
            // cannot run at all - there is nowhere for anything to go.
            findings.add(new Finding(Severity.ERROR, NO_STATIONS, "", null));
            return findings;
        }

        if (stations.size() == 1)
        {
            // trains have nowhere to go, which is not an error but is certainly not what was meant
            findings.add(new Finding(Severity.WARNING, ONE_STATION,
                stations.get(0).getName(), stations.get(0).getTile()));
            return findings;
        }

        Set<TileKey> stationTiles = new LinkedHashSet<>();

        for (ReducedPoint station : stations)
        {
            stationTiles.add(station.getTile());
        }

        // A STATION NOTHING CAN PASS IS SAID TO BE, and left out of the two sentences below (FR-101).
        for (ReducedPoint station : stations)
        {
            if (closed != null && closed.contains(station.getTile()))
            {
                findings.add(new Finding(Severity.WARNING, STATION_CLOSED, station.getName(), station.getTile()));
            }
        }

        if (reach == null) return findings;

        for (ReducedPoint station : stations)
        {
            if (closed != null && closed.contains(station.getTile())) continue;

            Set<TileKey> reachable = reach.getOrDefault(station.getTile(), Collections.<TileKey>emptySet());

            boolean reachesAStation = false;

            for (TileKey other : stationTiles)
            {
                if (!other.equals(station.getTile()) && reachable.contains(other))
                {
                    reachesAStation = true;
                    break;
                }
            }

            if (!reachesAStation)
            {
                // A terminus that cannot be left is the specific case worth naming: a train sent there is stuck.  A
                // COMPULSORY TURN IS A TERMINUS HERE.  `isTerminus` reads the authored flag, which setting a square's
                // flags clears, and falls back to "nothing leaves it", never true of a compulsory turn, whose turning
                // copy departs.
                findings.add(new Finding(Severity.WARNING,
                    isTerminus(reducer, station, termini) || mustTurn.contains(station.getTile())
                        ? TERMINUS_STRANDED : STATION_REACHES_NOTHING,
                    station.getName(), station.getTile()));
            }
        }

        // and the other direction: a station nothing can reach can never be a destination
        for (ReducedPoint station : stations)
        {
            if (closed != null && closed.contains(station.getTile())) continue;

            boolean reachable = false;

            for (ReducedPoint other : stations)
            {
                if (other == station) continue;

                if (reach.getOrDefault(other.getTile(), Collections.<TileKey>emptySet()).contains(station.getTile()))
                {
                    reachable = true;
                    break;
                }
            }

            if (!reachable)
            {
                findings.add(new Finding(Severity.WARNING, STATION_UNREACHABLE,
                    station.getName(), station.getTile()));
            }
        }

        return findings;
    }

    /**
     * A Point with no edges at all: a sensor whose track exists but whose every connection has been closed, a decision
     * somebody may not have meant (the reducer already leaves out sensors with nothing beside them).
     */
    private static List<Finding> checkIsolatedPoints(GraphReducer reducer)
    {
        List<Finding> findings = new ArrayList<>();

        Set<TileKey> touched = new HashSet<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            touched.add(edge.getStart());
            touched.add(edge.getEnd());
        }

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (!touched.contains(point.getTile()))
            {
                findings.add(new Finding(Severity.WARNING, POINT_ISOLATED,
                    point.getName(), point.getTile()));
            }
        }

        return findings;
    }

    /**
     * Track closed in both directions: almost always a mis-click, and the tile is still drawn so nothing looks wrong.
     */
    private static List<Finding> checkClosedRuns(TileGraph graph, GraphReducer reducer)
    {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : graph.getTiles().entrySet())
        {
            Map<TileGraph.RouteId, TilePorts.Route> routes = graph.getRoutes(entry.getKey());

            if (routes.isEmpty()) continue;

            boolean anyOpen = false;

            for (TileGraph.RouteId routeId : routes.keySet())
            {
                if (graph.getDirection(entry.getKey(), routeId) != TileGraph.Direction.NONE)
                {
                    anyOpen = true;
                    break;
                }
            }

            if (!anyOpen)
            {
                findings.add(new Finding(Severity.INFO, RUN_CLOSED_BOTH_WAYS,
                    String.valueOf(entry.getKey()), entry.getKey()));
            }
        }

        return findings;
    }

    private static boolean isTerminus(GraphReducer reducer, ReducedPoint station, Set<TileKey> termini)
    {
        // What the user marked comes first; a dead end is the fallback for a Point nobody marked.
        if (termini.contains(station.getTile())) return true;

        for (ReducedEdge edge : reducer.getEdges())
        {
            if (edge.getStart().equals(station.getTile())) return false;
        }

        return true;
    }

    // Reachability is never worked out here: the reduction's edges know no arrival sides, turn sets or bars, and
    // re-applying them by hand is a second answer that can drift from the build's.  `BuiltReach` reads the build.
}
