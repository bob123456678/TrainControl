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
 * Checks a user can run against a configuration before trusting trains to it.
 *
 * These are the questions that are obvious once asked and invisible until then: a terminus nothing can
 * leave, a station no train can reach, a run somebody has closed in both directions.  Every one of them
 * produces a configuration that loads, validates and runs - and then quietly never sends a train
 * somewhere, which is the kind of fault people spend an evening chasing on the baseboard.
 *
 * Reported as a list of findings rather than as a pass or a fail.  A layout mid-setup is expected to
 * have some of these, and a check that says only "no" is a check people stop running.
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

        Finding(Severity severity, String messageKey, String subject, TileKey tile)
        {
            this.severity = severity;
            this.messageKey = messageKey;
            this.subject = subject;
            this.tile = tile;
        }

        public Severity getSeverity()
        {
            return severity;
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

        @Override
        public String toString()
        {
            return severity + " " + messageKey + " [" + subject + "]"
                + (tile == null ? "" : " at " + tile);
        }
    }

    public static final String STATION_REACHES_NOTHING = "autosetup.ui.checkStationReachesNothing";
    public static final String STATION_UNREACHABLE = "autosetup.ui.checkStationUnreachable";
    public static final String TERMINUS_STRANDED = "autosetup.ui.checkTerminusStranded";
    public static final String POINT_ISOLATED = "autosetup.ui.checkPointIsolated";
    public static final String RUN_CLOSED_BOTH_WAYS = "autosetup.ui.checkRunClosedBothWays";
    public static final String NO_STATIONS = "autosetup.ui.checkNoStations";
    public static final String ONE_STATION = "autosetup.ui.checkOneStation";
    public static final String UNNAMED_POINT = "autosetup.ui.checkUnnamedPoint";
    public static final String UNNAMED_STATION = "autosetup.ui.checkUnnamedStation";
    public static final String UNLABELLED_STATION = "autosetup.ui.checkUnlabelledStation";
    public static final String MAY_TURN_ON_DEAD_END = "autosetup.ui.checkMayTurnOnDeadEnd";
    public static final String REVERSING_LEADS_NOWHERE = "autosetup.ui.checkReversingLeadsNowhere";
    public static final String ARRIVAL_TRAPPED = "autosetup.ui.checkArrivalTrapped";
    public static final String CAPTION_COVERED = "autosetup.ui.checkCaptionCovered";
    public static final String HOME_NEEDS_REVERSIBLE = "autosetup.ui.checkHomeNeedsReversible";

    /**
     * A square trains reverse at, on a layout that measures track, whose own track is not measured.
     */
    public static final String COPY_NO_WAY_OUT = "autosetup.ui.checkCopyNoWayOut";
    public static final String COPY_NO_WAY_IN = "autosetup.ui.checkCopyNoWayIn";
    public static final String REVERSAL_NEEDS_LENGTH = "autosetup.ui.checkReversalNeedsLength";
    public static final String FACING_IMPOSSIBLE = "autosetup.ui.checkFacingImpossible";

    public static final String SIGNAL_GONE = "autosetup.ui.checkProtectingSignalGone";

    public static final String NO_SIGNAL_PAIRED = "autosetup.ui.checkNoProtectingSignal";

    /**
     * A station no train can arrive at any more.
     *
     * Every way in barred, which the editor refuses to do but a diagram edit can arrive at from the
     * other side: bar one of two ways in, then delete the track that reached the other.
     */
    public static final String NO_ARRIVALS_LEFT = "autosetup.ui.checkNoArrivalsLeft";

    /**
     * One locomotive recorded as standing in two places.
     *
     * An ERROR, and the strongest kind: the running model does not skip the second placement, it
     * refuses the whole configuration - so a setup carrying this builds, loads, and then answers every
     * path with "configuration is invalid and must be reloaded", naming nothing that would lead anyone
     * back to the placement.  Reported here so it is visible before that happens, on the square that
     * can be cleared to fix it.
     */
    public static final String DUPLICATE_LOCOMOTIVE = "autosetup.ui.checkDuplicateLocomotive";

    /**
     * A train standing on the railway with no length recorded (FR-046).
     *
     * Autonomy refuses a platform to a train too long for it, and that comparison needs a number at
     * both ends. A locomotive with no length is never refused anything - it does not fail the rule, it
     * opts out of it - so a railway can be carefully set up with maximum lengths everywhere and still
     * run as though they were not there.
     *
     * A warning rather than an error, because a layout where nothing is ever close to too long is a
     * perfectly good layout and this is a reminder, not a refusal.
     */
    public static final String NO_TRAIN_LENGTH = "autosetup.ui.checkNoTrainLength";

    /**
     * The other end of the same sum: a station that will take a train of any length (FR-046).
     *
     * Said separately because the two are set in different places by different people - the length on
     * the locomotive, the maximum on the platform - and knowing which half is missing is the whole of
     * knowing what to do about it.
     */
    public static final String NO_MAX_TRAIN_LENGTH = "autosetup.ui.checkNoMaxTrainLength";

    /**
     * Two included pages carrying the same s88 sensor (OB-150).
     *
     * Adam: "when turning ON 'page 3 - top parking', there is no autonomy error telling me that its s88
     * sensors are duplicative with ones on page 1.  This config should not be possible to run."
     *
     * An ERROR, because it is not a preference. One sensor on two squares becomes two Points after the
     * reduction, and nothing downstream can say which of them a train is standing on - the railway
     * cannot be modelled correctly in that state, however carefully everything else is set up.
     *
     * `excludeRepeatedSensorPages` shuts a repeating page when a configuration is created, and OB-130
     * settled that a legacy import may do the same. Neither covers a page switched back on by hand
     * afterwards, which is the case this is for.
     */
    public static final String DUPLICATE_SENSOR_PAGE = "autosetup.ui.checkDuplicateSensorPage";

    private AutonomyChecks()
    {
    }

    /**
     * Runs every check.
     *
     * @param graph the tile graph, for the problems the diagram itself has
     * @param reducer the reduction, already run
     * @return everything found, most serious first
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer)
    {
        return run(graph, reducer, Collections.<TileKey>emptySet());
    }

    /**
     * @param termini the Points the user marked as termini
     *
     * A terminus used to be inferred here as "has no outgoing edge", which is a different thing from
     * what the user marked - so a marked terminus that reaches no station got the generic message, and
     * an ordinary dead end got the terminus one.  The flag lives in the configuration, so it is passed
     * in rather than guessed at.
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini)
    {
        return run(graph, reducer, termini, null);
    }

    /**
     * @param labelledStations the stations a caption of the track diagram is showing, by their squares,
     *        or null to skip that check.  Passed in rather than read here: the checks know about the
     *        setup, and which square carries which caption is the session’s business.
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations)
    {
        return run(graph, reducer, termini, labelledStations, Collections.<TileKey>emptySet());
    }

    /**
     * @param mayTurnOnDeadEnd squares set to "trains MAY change direction here" that have only one way
     *        in, where the choice cannot mean anything
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd,
            Collections.<TileKey>emptySet());
    }

    /**
     * @param trapped squares a train can reach from some direction and then not leave, because the only
     *        way on from there is back the way it came and it has not been told it may turn round
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd, trapped,
            Collections.<TileKey, TileKey>emptyMap());
    }

    /**
     * @param coveredCaptions squares where a station’s caption and the user’s own diagram text
     *        want the same square, as caption square to the station it names
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped,
        Map<TileKey, TileKey> coveredCaptions)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd, trapped,
            coveredCaptions, Collections.<TileKey, String>emptyMap());
    }

    /**
     * @param placedLocomotives which locomotive the configuration records standing on each square.
     *        Passed in for the same reason the captions are: this knows about the derived graph, and
     *        what is standing where belongs to the configuration.
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped,
        Map<TileKey, TileKey> coveredCaptions, Map<TileKey, String> placedLocomotives)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd, trapped,
            coveredCaptions, placedLocomotives, Collections.<TileKey, Boolean>emptyMap());
    }

    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped,
        Map<TileKey, TileKey> coveredCaptions, Map<TileKey, String> placedLocomotives,
        Map<TileKey, Boolean> shutStations)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd, trapped,
            coveredCaptions, placedLocomotives, shutStations,
            Collections.<TileKey>emptySet(), Collections.<TileKey>emptySet());
    }

    /**
     * @param shutStations which stations have had every arrival side barred.  Worked out by the
     *        session, which knows both the restrictions and how a square splits; this only reports it.
     * @param mayTurn / @param mustTurn the turn sets the station reachability walk needs to match the
     *        runtime.  Only the fullest form threads them through; the shorter overloads pass empty
     *        sets, which is the same convenience-default pattern the rest of these arguments follow -
     *        and only the session's own check() uses the fullest form, so the checks a person actually
     *        sees are the split-aware ones.
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped,
        Map<TileKey, TileKey> coveredCaptions, Map<TileKey, String> placedLocomotives,
        Map<TileKey, Boolean> shutStations, Set<TileKey> mayTurn, Set<TileKey> mustTurn)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd, trapped,
            coveredCaptions, placedLocomotives, shutStations, mayTurn, mustTurn,
            Collections.<TileKey>emptySet());
    }

    /**
     * @param homes the squares an authored home locomotive lives at
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped,
        Map<TileKey, TileKey> coveredCaptions, Map<TileKey, String> placedLocomotives,
        Map<TileKey, Boolean> shutStations, Set<TileKey> mayTurn, Set<TileKey> mustTurn,
        Set<TileKey> homes)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd, trapped,
            coveredCaptions, placedLocomotives, shutStations, mayTurn, mustTurn, homes,
            Collections.<TileKey>emptySet(), Collections.<TileKey>emptySet(),
            Collections.<TileKey>emptySet(),
            // No bars, which is what this shorter form has always meant.
            Collections.<TileKey, Set<TilePorts.Side>>emptyMap());
    }

    /**
     * @param signalsGone the stations whose paired protecting signal no longer resolves to an accessory
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped,
        Map<TileKey, TileKey> coveredCaptions, Map<TileKey, String> placedLocomotives,
        Map<TileKey, Boolean> shutStations, Set<TileKey> mayTurn, Set<TileKey> mustTurn,
        Set<TileKey> homes, Set<TileKey> signalsGone, Set<TileKey> stationsWithoutSignal,
        Set<TileKey> facingsImpossible, Map<TileKey, Set<TilePorts.Side>> barred)
    {
        return run(graph, reducer, termini, labelledStations, mayTurnOnDeadEnd, trapped,
            coveredCaptions, placedLocomotives, shutStations, mayTurn, mustTurn, homes, signalsGone,
            stationsWithoutSignal, facingsImpossible, barred,
            Collections.<TileKey>emptySet(), Collections.<TileKey>emptySet(),
            Collections.<TileKey, String>emptyMap(), Collections.<TileKey>emptySet(),
            Collections.<TileKey, String>emptyMap(), Collections.<TileKey, String>emptyMap());
    }

    /**
     * @param withoutTrainLength squares whose locomotive has no length recorded (FR-046)
     * @param repeatedSensorPages included pages repeating another included page's s88 (OB-150)
     * @param withoutMaxLength station squares with no maximum train length
     *
     * The two halves of the length comparison, supplied rather than derived: one lives on the
     * Locomotive in the model and the other is a point property, and neither is visible from the graph.
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> labelledStations, Set<TileKey> mayTurnOnDeadEnd, Set<TileKey> trapped,
        Map<TileKey, TileKey> coveredCaptions, Map<TileKey, String> placedLocomotives,
        Map<TileKey, Boolean> shutStations, Set<TileKey> mayTurn, Set<TileKey> mustTurn,
        Set<TileKey> homes, Set<TileKey> signalsGone, Set<TileKey> stationsWithoutSignal,
        Set<TileKey> facingsImpossible, Map<TileKey, Set<TilePorts.Side>> barred,
        Set<TileKey> withoutTrainLength, Set<TileKey> withoutMaxLength,
        Map<TileKey, String> repeatedSensorPages, Set<TileKey> reversalsWithoutLength,
        Map<TileKey, String> copiesWithNoWayOut, Map<TileKey, String> copiesWithNoWayIn)
    {
        List<Finding> findings = new ArrayList<>();

        findings.addAll(checkFacings(reducer, facingsImpossible));

        findings.addAll(checkDuplicateLocomotives(placedLocomotives));
        findings.addAll(checkLengths(reducer, withoutTrainLength, withoutMaxLength, placedLocomotives));
        findings.addAll(checkRepeatedSensorPages(repeatedSensorPages));

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

        findings.addAll(checkReversingGoesSomewhere(reducer, mayTurn, mustTurn, barred));
        findings.addAll(checkTrappedArrivals(reducer, trapped));
        findings.addAll(checkCoveredCaptions(reducer, coveredCaptions));
        findings.addAll(checkStations(reducer, termini, mayTurn, mustTurn, barred));
        findings.addAll(checkStationLabels(reducer, labelledStations));
        findings.addAll(checkIsolatedPoints(reducer));
        findings.addAll(checkClosedRuns(graph, reducer));
        findings.addAll(checkHomesThatNeedReversing(reducer, homes, mustTurn));
        findings.addAll(checkReversalsWithoutLength(reducer, reversalsWithoutLength));
        findings.addAll(checkBadCopies(copiesWithNoWayOut, COPY_NO_WAY_OUT));
        findings.addAll(checkBadCopies(copiesWithNoWayIn, COPY_NO_WAY_IN));
        findings.addAll(checkProtectingSignals(reducer, signalsGone));

        findings.addAll(checkStationsWithoutSignals(reducer, stationsWithoutSignal));

        Collections.sort(findings, new java.util.Comparator<Finding>()
        {
            @Override
            public int compare(Finding a, Finding b)
            {
                return a.getSeverity().ordinal() - b.getSeverity().ordinal();
            }
        });

        return findings;
    }

    /**
     * Can trains actually get between the stations?
     *
     * A train may pass through a non-station but only ever stop at a station, so this is the question the
     * layout exists to answer - and a station that can reach nothing is not a station anybody can use,
     * however well connected its track is.
     */
    /**
     * Has everything been given a name?
     *
     * A name still carrying what the reducer invented - the page and the coordinate - works and means
     * nothing: autonomy names the place in every message it prints, and "1 - Main 12,7" in a running
     * log tells the reader nothing about where their train is.
     *
     * How much that matters depends on what the place IS.  A station is somewhere a train is SENT, and
     * its name is what the user picks it by and what the arrival is announced as, so an unnamed one is
     * blocking.  A plain point is somewhere a train passes; its name appears in a path and nowhere a
     * user has to choose from, so that is worth saying and not worth refusing over - a layout has far
     * more of them, and stopping a setup running until every last one is named would mean naming forty
     * squares nobody will ever read.
     */
    private static List<Finding> checkNames(GraphReducer reducer)
    {
        List<Finding> findings = new ArrayList<>();

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (point.getName() != null
                    && !point.getName().equals(GraphReducer.generatedName(point.getTile()))) continue;

            // A station without a name is an ERROR, which is what the paragraph above always said it
            // was - "an unnamed one is blocking" - and what the code did not do.  It is the name the
            // user picks a destination by and the name an arrival is announced as, and "1 - Main 12,7"
            // is neither.  Adam asked for it to refuse rather than grumble, having lived with the
            // grumble.
            //
            // A plain point without one stays a NOTICE: trains pass it, its name appears in a path and
            // nowhere anybody chooses from, and a layout has dozens of them.  Listing those beside real
            // problems buried the real problems.
            findings.add(new Finding(
                point.isStation() ? Severity.ERROR : Severity.NOTICE,
                point.isStation() ? UNNAMED_STATION : UNNAMED_POINT,
                String.valueOf(point.getTile()), point.getTile()));
        }

        return findings;
    }

    /**
     * "May change direction" where there is nowhere else to go.
     *
     * A square no arriving train can carry on from cannot offer the choice the word "may" describes:
     * every way in leads back out the way it came, so they all turn whatever the setting says.
     *
     * Judged per ARRIVAL since OB-123, not by counting the ways in. A square with two ways in and one
     * way out forces a turn on the train that came in by the departure side and offers a real choice
     * to the other - so counting lines called it pointless when it was not. This sentence said "one
     * line runs into it" for a day after the rule stopped meaning that.  Worth saying because the
     * setting reads as a choice the user has made and is not one - and because if they wanted the
     * choice, the square is not the one they think it is.
     */
    private static List<Finding> checkTurning(GraphReducer reducer, Set<TileKey> mayTurnOnDeadEnd)
    {
        List<Finding> findings = new ArrayList<>();

        for (TileKey tile : mayTurnOnDeadEnd)
        {
            ReducedPoint point = reducer.getPoints().get(tile);

            findings.add(new Finding(Severity.NOTICE, MAY_TURN_ON_DEAD_END,
                point == null ? String.valueOf(tile) : point.getName(), tile));
        }

        return findings;
    }

    /**
     * A square trains turn round at, from which nothing can be reached.
     *
     * Adam, closing OB-113: "We need to add a warning if a reversing point leads to nothing else."
     * That report was a route he expected and did not get, and the cause was the reversing point - so
     * the setup could already have told him.
     *
     * **The hole this fills is specifically one the other checks open.** `ARRIVAL_TRAPPED` fires when
     * a train could reach a square and then not leave it, and its own wording tells the user what to
     * do: "Either set 'trains may change direction here', or open the way ahead." Setting the flag
     * silences it - whether or not the way ahead was ever opened. So the advice this application gives
     * can turn a loud problem into a silent one, and that is the case here.
     *
     * `TERMINUS_STRANDED` and `STATION_REACHES_NOTHING` say the same thing for STATIONS and only for
     * stations. A reversing point that is not a station had nothing watching it at all.
     *
     * Reachability is asked of the reducer with the same turn sets the station walk uses, so this
     * agrees with what the runtime will actually do rather than with plain tile adjacency - a
     * distinction the station check above had to learn the hard way.
     *
     * A NOTICE, not a warning. It is a real fault when it is one, but "reaches no station" is also
     * true of a reversing point on a spur somebody is still drawing, and this list has been made
     * useless before by ordinary things listed beside real problems.
     *
     * @param reducer the reduced graph
     * @param mayTurn squares where a train may change direction
     * @param mustTurn squares where it must
     * @param barred arrival sides closed off, passed through to reachableTiles so a station reachable
     *               only by a barred side is not counted as reachable (DOC-C17: added by 3a52ec14 for
     *               OB-120 and missing from this list ever since)
     * @return one finding per reversing square that leads nowhere
     */
    private static List<Finding> checkReversingGoesSomewhere(GraphReducer reducer,
        Set<TileKey> mayTurn, Set<TileKey> mustTurn, Map<TileKey, Set<TilePorts.Side>> barred)
    {
        List<Finding> findings = new ArrayList<>();

        Set<TileKey> reversing = new LinkedHashSet<>();

        reversing.addAll(mayTurn);
        reversing.addAll(mustTurn);

        Set<TileKey> stationTiles = new LinkedHashSet<>();

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (point.isStation()) stationTiles.add(point.getTile());
        }

        for (TileKey tile : reversing)
        {
            ReducedPoint point = reducer.getPoints().get(tile);

            if (point == null) continue;

            // Stations are the other two checks' business, and saying it twice about one square is
            // how a list of findings stops being read.
            if (point.isStation()) continue;

            // WITH the red arrows (OB-120, finished 2026-08-27 after a reviewer found it).
            //
            // This asked the barred-less form, so the findings saw runs no train can take: bars only
            // ever remove them, and a station reachable ONLY by a barred side counted as reachable.
            Set<TileKey> reachable = reducer.reachableTiles(tile, mayTurn, mustTurn, barred);

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
     * A station paired with a signal that is no longer there.
     *
     * The pairing is stored against the signal's SQUARE and resolved to an accessory at build time, so
     * a signal deleted from the diagram - or a setup imported against a different layout - leaves a
     * pairing pointing at nothing.  Everything downstream then does the safe thing quietly: the build
     * omits the accessory, and the refresh returns without sending a command, because a placement must
     * not fail over a signal.
     *
     * Quietly is the problem.  The operator set that pairing because they wanted a platform protected,
     * and nothing anywhere told them it had stopped being.
     *
     * @param signalsGone the station squares whose pairing no longer resolves
     */
    /**
     * A station with no signal paired to it at all.
     *
     * A NOTICE rather than a warning, because it is a perfectly ordinary way to run a railway - most
     * layouts protect some platforms and not others, and a station without a signal simply has no
     * signal. What it is not is a station whose pairing was lost, and there was no way to tell those
     * two apart from the outside: both are silent.
     *
     * Adam asked for it after accepting that pairings can only be audited one station at a time. This
     * does not fix that - it is a list of the ones that have none, which is the half of the audit a
     * check can do without being told what the layout is supposed to look like.
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
            ReducedPoint point = reducer.getPoints().get(tile);

            findings.add(new Finding(Severity.NOTICE, NO_SIGNAL_PAIRED,
                point == null ? String.valueOf(tile) : point.getName(), tile));
        }

        return findings;
    }

    /**
     * A train recorded facing a way its square cannot hold.
     *
     * The build has to put it somewhere, so it uses the first copy of the square - which may point the
     * other way, and the train appears to have turned round on its own.  Nothing here can work out
     * which way it really points; only the operator can, and until now nothing told them to look.
     */
    private static List<Finding> checkFacings(GraphReducer reducer, Set<TileKey> facingsImpossible)
    {
        List<Finding> findings = new ArrayList<>();

        if (facingsImpossible == null) return findings;

        for (TileKey tile : facingsImpossible)
        {
            ReducedPoint point = reducer.getPoints().get(tile);

            findings.add(new Finding(Severity.WARNING, FACING_IMPOSSIBLE,
                point == null ? String.valueOf(tile) : point.getName(), tile));
        }

        return findings;
    }

    private static List<Finding> checkProtectingSignals(GraphReducer reducer, Set<TileKey> signalsGone)
    {
        List<Finding> findings = new ArrayList<>();

        if (signalsGone == null) return findings;

        for (TileKey tile : signalsGone)
        {
            ReducedPoint point = reducer.getPoints().get(tile);

            findings.add(new Finding(Severity.WARNING, SIGNAL_GONE,
                point == null ? String.valueOf(tile) : point.getName(), tile));
        }

        return findings;
    }

    /**
     * A square trains reverse at, whose track has no recorded length (Adam, 2026-09-01).
     *
     * "Add notices to the autonomy editor to add track lengths between stations and switches that
     * accept reversal (if any other track length is set anywhere)."
     *
     * A train reversing into a berth shorter than itself stands across the switch behind it, and
     * `isPathClear` refuses that - but only where the track has been measured, because unmeasured
     * track is unknown rather than short. So the squares this names are exactly the ones where the
     * guard cannot do its job: somewhere a train turns round, with nothing recorded about how much
     * room it has.
     *
     * **Only on a layout that measures track at all**, which is his condition and a good one: a
     * railway that records no lengths has decided not to model them, and a notice on every reversing
     * square would be a list of things that are not wrong.
     *
     * A WARNING, not an error. Nothing here is broken - the guard simply cannot protect this square
     * until somebody says how long it is.
     *
     * @param reversalsWithoutLength the squares, decided by the session
     */
    /**
     * A square that emits an arrival copy nothing can leave, or nothing can reach.
     *
     * Adam, 2026-09-02: "we need a warning for instances like the previous version of this."  He had
     * just built a parking spur whose reverse copy came out with two ways in and none out - somewhere
     * autonomy may send a train that no train can ever depart from.  BottomMainB had the same shape
     * earlier that day and cost an evening: Return Home was chased for a fault that was in the diagram.
     *
     * **Every other check here asks about the SQUARE, and that is why none of them catch it.**  A
     * square is emitted as one Point per side a train can arrive by, and the copies do not share their
     * edges.  LowerFront is perfectly reachable and its westbound copy has no way in at all; the square
     * passes every reachability test in this file while the copy is a trap.  A rule about copies cannot
     * be enforced by looking at squares.
     *
     * An ERROR rather than a warning for the no-way-out case: a train sent there is stuck, and nothing
     * downstream refuses the trip.  The no-way-in case is a WARNING - it costs nothing until somebody
     * leaves a train there, and then Return Home will not move it, because the staging planner never
     * moves a locomotive off a square it could not put it back on.
     *
     * @param copies the squares, mapped to the offending copy, decided by the session
     * @param key which of the two messages to report
     */
    private static List<Finding> checkBadCopies(Map<TileKey, String> copies, String key)
    {
        List<Finding> findings = new ArrayList<>();

        if (copies == null) return findings;

        Severity severity = COPY_NO_WAY_OUT.equals(key) ? Severity.ERROR : Severity.WARNING;

        for (Map.Entry<TileKey, String> entry : copies.entrySet())
        {
            findings.add(new Finding(severity, key, entry.getValue(), entry.getKey()));
        }

        return findings;
    }

    private static List<Finding> checkReversalsWithoutLength(GraphReducer reducer,
        Set<TileKey> reversalsWithoutLength)
    {
        List<Finding> findings = new ArrayList<>();

        if (reversalsWithoutLength == null) return findings;

        for (TileKey tile : reversalsWithoutLength)
        {
            ReducedPoint point = reducer.getPoints().get(tile);

            findings.add(new Finding(Severity.WARNING, REVERSAL_NEEDS_LENGTH,
                point == null ? String.valueOf(tile) : point.getName(), tile));
        }

        return findings;
    }

    /**
     * A home on a square every train must turn round at.
     *
     * Such a square is emitted as turning copies only, and a turning station copy is a TERMINUS -
     * which HomeStaging.canRest refuses to a locomotive that cannot reverse.  So the home is perfectly
     * good for a reversible locomotive and impossible for any other, and the only way to find that out
     * used to be Return Home reporting IMPOSSIBLE with no mention of the square.
     *
     * Not an error, and not something to fix in the setup: a berth every train must back out of is a
     * real place, and a home there is a reasonable thing to want.  It is said out loud so the
     * limitation is read here rather than discovered at the end of a session.
     *
     * @param homes the squares carrying an authored home
     * @param mustTurn the squares where turning round is compulsory
     */
    private static List<Finding> checkHomesThatNeedReversing(GraphReducer reducer,
        Set<TileKey> homes, Set<TileKey> mustTurn)
    {
        List<Finding> findings = new ArrayList<>();

        if (homes == null || mustTurn == null) return findings;

        for (TileKey tile : homes)
        {
            if (!mustTurn.contains(tile)) continue;

            ReducedPoint point = reducer.getPoints().get(tile);

            if (point == null || !point.isStation()) continue;

            findings.add(new Finding(Severity.WARNING, HOME_NEEDS_REVERSIBLE,
                point.getName(), tile));
        }

        return findings;
    }

    /**
     * Squares a train can reach and then not leave.
     *
     * A train arriving somewhere is pointing away from where it came from, and it can only carry on
     * forwards - so a square whose only way on is back the way the train came is the end of that train's
     * day, unless it has been told trains may turn round there.  This used not to be sayable: with one
     * Point per sensor the graph could not tell arriving-and-continuing from arriving-and-backing-out,
     * so the reversal was always available and the trap never appeared.  It appears now because the
     * graph finally distinguishes them, which makes this a case somebody has to decide rather than a new
     * fault in the railway.
     *
     * Usually the answer is one of two things: the square IS a terminus and wants marking as one, or a
     * branch off it is shut that should be open.
     */
    private static List<Finding> checkTrappedArrivals(GraphReducer reducer, Set<TileKey> trapped)
    {
        List<Finding> findings = new ArrayList<>();

        for (TileKey tile : trapped)
        {
            ReducedPoint point = reducer.getPoints().get(tile);

            // A NOTICE on a square trains only pass through.  "Could not go on" warns about a place a
            // train can be SENT and then be stuck; nothing is ever sent to a plain point, so there the
            // same sentence is a remark about the shape of the track rather than something to fix.
            findings.add(new Finding(
                point != null && point.isStation() ? Severity.WARNING : Severity.INFO,
                ARRIVAL_TRAPPED,
                point == null ? String.valueOf(tile) : point.getName(), tile));
        }

        return findings;
    }

    /**
     * A station’s caption and the user’s own writing on the same square.
     *
     * The square belongs to the diagram, because the diagram is the user’s drawing before it is
     * autonomy’s data - so the text is what gets drawn and the caption is what is hidden.  Nothing is
     * deleted either way: this says which station has gone quiet and where, and the answer is to move
     * one of the two.
     *
     * It became possible to say at all only once a caption stopped BEING text.  While it was a label,
     * the two could not share a square: writing one over the other simply destroyed it.
     */
    private static List<Finding> checkCoveredCaptions(GraphReducer reducer,
        Map<TileKey, TileKey> covered)
    {
        List<Finding> findings = new ArrayList<>();

        if (covered == null) return findings;

        for (Map.Entry<TileKey, TileKey> entry : covered.entrySet())
        {
            ReducedPoint station = reducer.getPoints().get(entry.getValue());

            findings.add(new Finding(Severity.WARNING, CAPTION_COVERED,
                station == null ? String.valueOf(entry.getValue()) : station.getName(),
                entry.getKey()));
        }

        return findings;
    }


    /**
     * Included pages that repeat another included page's sensors (OB-150).
     *
     * The subject is a PAGE rather than a square, and there is no tile to jump to: the fault is that
     * two squares somewhere carry one address, and naming one of them would send the reader to an
     * innocent-looking sensor. The page is the thing that can be switched back off.
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
            // WITH ITS SQUARE (MT-223).  This passed null, and the note beside it explained that the
            // fault was about a page rather than any one square - which was true while the message
            // said only "this page repeats something". Once it named a specific s88, the finding was
            // about one square, the caller knew which, and a finding that names a square and cannot
            // take you to it is the worst of both.
            findings.add(new Finding(Severity.ERROR, DUPLICATE_SENSOR_PAGE,
                repeat.getValue(), repeat.getKey()));
        }

        return findings;
    }
    /**
     * Trains with no length, and stations with no maximum (FR-046).
     *
     * Handed the answers rather than working them out: a station's maximum is a point property the
     * session holds, and a train's length is on the Locomotive in the model, which nothing in this
     * class can reach. Both arrive as sets of squares for the same reason every other check here takes
     * one - the caller knows things about the configuration that the graph does not.
     *
     * @param withoutTrainLength squares holding a locomotive whose length is not set
     * @param withoutMaxLength station squares with no maximum train length
     * @param placed which locomotive stands where, for naming the train in the message
     * @return one warning per square
     */
    private static List<Finding> checkLengths(GraphReducer reducer, Set<TileKey> withoutTrainLength,
        Set<TileKey> withoutMaxLength, Map<TileKey, String> placed)
    {
        List<Finding> findings = new ArrayList<>();

        for (TileKey square : withoutTrainLength)
        {
            // Named, because "a train has no length" sends the reader looking at seven of them. The
            // square is what the editor jumps to; the name is what says which train to go and measure.
            String standing = placed == null ? null : placed.get(square);

            findings.add(new Finding(Severity.WARNING, NO_TRAIN_LENGTH,
                standing == null ? "" : standing, square));
        }

        for (TileKey square : withoutMaxLength)
        {
            // The station's name as the subject, the way every other finding about a station does it -
            // the editor jumps to the square, and the sentence needs something to be about.
            GraphReducer.ReducedPoint point = reducer == null ? null : reducer.getPoints().get(square);

            findings.add(new Finding(Severity.WARNING, NO_MAX_TRAIN_LENGTH,
                point == null ? String.valueOf(square) : point.getName(), square));
        }

        return findings;
    }

    /**
     * Is any locomotive recorded as standing in two places?
     *
     * One locomotive, one place - which the running layout enforces when a train MOVES, because it
     * leaves where it was.  The configuration is a different store and nothing enforced it there, so a
     * placement made by hand could sit alongside one an import had already written.
     *
     * The consequence is out of all proportion to the cause: fromJSON refuses the whole layout, and
     * every path afterwards is answered with "configuration is invalid" - which names neither the
     * locomotive nor the square, and points at nothing the reader did.
     *
     * Reported against the SECOND square and any after it, rather than against both.  One of them is
     * where the train is meant to be and the other is the leftover; the check cannot tell which, but
     * listing them all as errors would say the setup has two problems when it has one, and clearing
     * either fixes it.
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
                // The subject is the locomotive, and the editor shows the SQUARE instead whenever a
                // finding carries one - which it must here, or there is nothing to jump to.  So the
                // message is written to read against the square, and the name is carried for anything
                // that wants it rather than for that sentence.
                findings.add(new Finding(Severity.ERROR, DUPLICATE_LOCOMOTIVE,
                    entry.getKey(), squares.get(extra)));
            }
        }

        return findings;
    }

    /**
     * Is any station shut to trains from every direction?
     *
     * The editor will not let somebody tick the last way in, so this is for the ways round it: a
     * diagram edited after the restriction was set, a track removed, a setup written by hand.  The
     * consequence is quiet and total - the station simply never appears as a destination again - so it
     * is worth saying out loud.
     */
    private static List<Finding> checkArrivalsLeft(GraphReducer reducer, Map<TileKey, Boolean> shut)
    {
        List<Finding> findings = new ArrayList<>();

        if (shut == null) return findings;

        for (Map.Entry<TileKey, Boolean> entry : shut.entrySet())
        {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;

            // By name, like every other check.  The editor substitutes the square's description for
            // the subject of a finding that carries a tile, so this is what anything else sees - and
            // "main:3,1" is not something a user can look for on their own diagram.
            ReducedPoint point = reducer == null ? null : reducer.getPoints().get(entry.getKey());

            // INFORMATION, not an error, and Adam settled why (MT-078): "We should let the user know a
            // train can't come in in any way (warning). If manual only, it's info."
            //
            // Barring every side stops AUTONOMY sending a train here; it does not stop a person driving
            // one in, because a bar is advisory - autonomy will not route into a barred side and a hand
            // dispatch may. So the station is still reachable, and a finding that blocks the whole
            // setup from starting over a platform the operator can still use is the wrong answer.
            //
            // The case Adam calls a warning - nothing can come in ANY way - is a different condition
            // and already has one: POINT_ISOLATED, where no track reaches the square at all.
            findings.add(new Finding(Severity.INFO, NO_ARRIVALS_LEFT,
                point == null || point.getName() == null || point.getName().trim().isEmpty()
                    ? entry.getKey().toString() : point.getName(),
                entry.getKey()));
        }

        return findings;
    }

    /**
     * Is every station shown on the track diagram?
     *
     * The diagram is where the user watches trains, and a station with no label on it is a place they
     * cannot see - autonomy will announce arrivals at a platform that is nowhere on screen.
     *
     * An error, on the author's instruction.  This was a warning on the argument that the railway runs
     * perfectly well unlabelled and merely cannot be read - true of the trains, and beside the point
     * for the person watching them.  A setup whose stations cannot be found on the diagram is not one
     * anybody can supervise, and calling that "worth checking" put it below things that matter less.
     *
     * A station still carrying its generated name is skipped, because it already has a warning of its
     * own and naming it is the step that has to come first - two rows about one sensor, one of which
     * cannot be acted on yet, is a list nobody finishes.
     */
    private static List<Finding> checkStationLabels(GraphReducer reducer, Set<TileKey> labelled)
    {
        List<Finding> findings = new ArrayList<>();

        if (labelled == null) return findings;

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (!point.isStation() || point.getName() == null) continue;

            if (point.getName().equals(GraphReducer.generatedName(point.getTile()))) continue;

            // Asked by SQUARE, not by name.  Matching the caption's text against the Point's name was
            // the same fragile join that let a caption look live while naming a station that had been
            // renamed - and it reported a perfectly well labelled station as unlabelled the moment its
            // name changed.
            if (!labelled.contains(point.getTile()))
            {
                findings.add(new Finding(Severity.ERROR, UNLABELLED_STATION,
                    point.getName(), point.getTile()));
            }
        }

        return findings;
    }

    private static List<Finding> checkStations(GraphReducer reducer, Set<TileKey> termini,
        Set<TileKey> mayTurn, Set<TileKey> mustTurn, Map<TileKey, Set<TilePorts.Side>> barred)
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
            // cannot run at all - there is nowhere for anything to go.  Reporting it as a warning put
            // it beside things that merely want attention, and let the setup look startable.
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

        // Reachability the way the RUNTIME has it, honouring the arrival-side split - not the plain
        // tile adjacency, which lets a run cross between the two arms of a double curve and so reports
        // a route Layout.bfs never takes.  Worked out once per station and read from below for both
        // directions; the old code walked the graph again inside the reverse loop.
        Map<TileKey, Set<TileKey>> reach = new LinkedHashMap<>();

        for (ReducedPoint station : stations)
        {
            reach.put(station.getTile(),
                // WITH the red arrows, which is what the comment above has always claimed (OB-120).
                reducer.reachableTiles(station.getTile(), mayTurn, mustTurn, barred));
        }

        for (ReducedPoint station : stations)
        {
            Set<TileKey> reachable = reach.get(station.getTile());

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
                // A terminus that cannot be left is the specific case worth naming: a train sent there
                // is stuck, and the layout will look like it simply stopped using that station.
                findings.add(new Finding(Severity.WARNING,
                    isTerminus(reducer, station, termini)
                        ? TERMINUS_STRANDED : STATION_REACHES_NOTHING,
                    station.getName(), station.getTile()));
            }
        }

        // and the other direction: a station nothing can reach can never be a destination
        for (ReducedPoint station : stations)
        {
            boolean reachable = false;

            for (ReducedPoint other : stations)
            {
                if (other == station) continue;

                if (reach.get(other.getTile()).contains(station.getTile()))
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
     * A Point with no edges at all.
     *
     * The reducer already leaves out sensors with nothing beside them, so anything here is a sensor whose
     * track exists but whose every connection has been closed - which is a decision somebody made and may
     * not have meant.
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
     * Track that has been closed in both directions.
     *
     * Almost always a mis-click: somebody meant to make a run one way and cycled past it.  The tile is
     * still drawn, so nothing looks wrong, and the route through it simply stops existing.
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

    // The tile-adjacency reachability that used to live here (adjacency + reachableFrom) is gone.  It
    // walked reducer.getEdges() as a plain Point-to-Point graph, which ignores the arrival-side split
    // and so let a run cross between the two arms of a double curve - reporting a station pair
    // reachable that the runtime never routes.  reducer.reachableTiles answers the split-aware
    // question instead, which is the same one Layout.bfs and the editor's path test ask.
}
