package core;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * The editor measures a platform against the track behind it, and says the figure a train is refused above.
 *
 * One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136 Phase 3): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder
 * every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testThePlatformNoticeKnowsWhatATrainIsRefusedAt extends AutonomySessionFixture
{

    /**
     * A stretch answered 0 on purpose is not reported as a reversal without a length (Adam, 2026-09-23: *"stop
     * listing answered zeros as missing"*).
     *
     * The same railway as the claim below, at its last step - measured elsewhere, nothing on this run-in - where the
     * notice fires.  Every square of the run-in answered 0 then silences it; the rules still read those squares as
     * unmeasured, so the guard stays exactly as blind as the notice used to say.
     */
    @Test
    public void testAReversalWhoseStretchIsAnsweredZeroIsNotAsked() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Lengths");

        TileKey turns = new TileKey("main", 1, 1);

        session.setStation(turns, true);
        session.setPointProperty(turns, "canReverse", Boolean.TRUE);
        session.setTileLength(new TileKey("main", 4, 1), 7);
        session.rebuild();

        assertTrue(session.reversalsWithoutLength().containsKey(turns),
            "CONTROL: nothing on the run-in is measured and the notice did not fire, so the claim below would pass by"
            + " the notice never firing");

        for (int x = 1; x <= 3; x++) session.getStore().answerTileLengthZero(new TileKey("main", x, 1));

        assertFalse(session.reversalsWithoutLength().containsKey(turns),
            "every square of the run-in was answered 0 on purpose and the editor still asks for its length: "
            + session.reversalsWithoutLength());
    }

    /**
     * The editor asks for lengths where trains reverse - but only on a layout that measures track.
     *
     * Adam: "Add notices to the autonomy editor to add track lengths between stations and switches
     * that accept reversal (if any other track length is set anywhere)."
     *
     * The guard that stops a train being backed over the switch behind its berth can only refuse what
     * it can measure, so these are the squares where it is blind: somewhere trains turn round, with
     * nothing recorded about how much room is there.
     *
     * **The second half is the half worth testing.** A railway that records no lengths at all has
     * decided not to model them, and listing every reversing square on it would be a page of notices
     * about something nobody is attempting. The same fixture is asked twice, and the only difference
     * between the two answers is one length recorded somewhere else entirely.
     *
     * MUTATION: dropping the measuresAnything test reports the square in both halves; dropping the
     * isTurnAround test reports squares nothing reverses at.
     */
    @Test
    public void testTheEditorAsksForLengthsWhereTrainsReverse() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Lengths");

        TileKey turns = new TileKey("main", 1, 1);

        session.setStation(turns, true);
        session.setPointProperty(turns, "canReverse", Boolean.TRUE);
        session.rebuild();

        assertTrue(session.isTurnAround(turns),
            "the fixture did not take: this square must be one trains turn round at");

        // NOTHING MEASURED ANYWHERE: the notice stays away.
        assertFalse(session.reversalsWithoutLength().containsKey(turns),
            "a layout that records no track lengths at all was asked to record one here, which is a "
            + "notice about something nobody on that railway is trying to do");

        // ONE LENGTH, ON A SQUARE OUTSIDE THIS STRETCH, so the layout is one that measures track
        // without that measurement being about the run-in under test.
        //
        // 4,1 is the sensor at the far end; the stretch behind the reversal at 1,1 is 3,1, 2,1 and
        // 1,1 itself.  This used to measure 2,1, which IS in the stretch - harmless while the notice
        // waited for every square, and the thing being measured once it waits for any (MT-364).
        session.setTileLength(new TileKey("main", 4, 1), 7);

        assertTrue(session.reversalsWithoutLength().containsKey(turns),
            "the layout measures track now, and the square where a train turns round - the one place "
            + "the length rule cannot do its job without a number - was not asked about");

        // ONE ENTRY, NOT ONE PER SQUARE (OB-171).
        //
        // Adam: "warnings ... fire on many tiles along a line.  Dedupe them, one per segment between a
        // switch and a station."  The map is the reversal square mapped to how many squares its guard
        // still needs measured, so a run of ten unmeasured squares is one notice saying ten - not ten
        // notices saying the same sentence.
        assertEquals(session.reversalsWithoutLength().size(), 1,
            "one square trains turn at, and the notice is not one entry: "
            + session.reversalsWithoutLength());

        // AND IT GOES ONCE THE STRETCH IS MEASURED AT ALL (Adam, MT-364, 2026-09-12).
        //
        // This used to assert the opposite - that the notice stays until EVERY square of the run-in
        // has a number - on the reasoning that "the guard needs every square before it can judge
        // anything".  That reasoning was true when it was written and stopped being true on
        // 2026-09-06, when Adam ruled that a stretch is only indeterminate if ALL of it is zero: a
        // half-measured run-in makes the guard PESSIMISTIC rather than blind, and it still binds.
        //
        // He met the consequence on his own railway: *"I set the track at 10,10 to length 2, so now we
        // know that the track to TunnelLongPark is 2.  But it is still asking for the tile at 10,9
        // (TunnelLongPark) to get a length."*  The reversal square was asked for on its own account,
        // before the walk was consulted, so measuring the track behind it could never have helped.
        session.setTileLength(turns, 5);

        assertFalse(session.reversalsWithoutLength().containsKey(turns),
            "the stretch behind this square carries a measurement and the editor is still asking for "
            + "the rest of it. Adam, MT-364: \"it is still asking for the tile at 10,9 to get a "
            + "length\" - the guard judges what it can see, and a notice about a stretch it can "
            + "already judge is a list you can never empty: " + session.reversalsWithoutLength());

        // AND THE CASE THE NOTICE EXISTS FOR IS UNTOUCHED: nothing measured in the stretch at all.
        //
        // Without this the claim above is satisfied by a notice that has stopped firing entirely,
        // which is the direction this change could most easily go wrong in.
        session.setTileLength(turns, 0);

        assertTrue(session.reversalsWithoutLength().containsKey(turns),
            "the layout measures track elsewhere and NOTHING on this run-in has a number, so the guard "
            + "is blind here rather than pessimistic - and the notice said nothing: "
            + session.reversalsWithoutLength());
    }

    /**
     * The editor says when a platform is told to hold more train than its track measures.
     *
     * Adam, 2026-09-11: *"Let's add an autonomy editor notice that alerts the user if a run-in is
     * shorter than the berth length, that way they can decide if it makes sense or not.  for example, a
     * station of length 4 may have two segments of tracks on either side of a switch, of length 2.  that
     * is acceptable and its limitations are understood."*
     *
     * **Two numbers set by two different hands.**  `maxTrainLength` is what somebody typed on the
     * platform; the measured room is what the track says, counted back from the platform to whichever of
     * the last switch and a reversal is met first.  Where the second is smaller the first never binds,
     * and a train inside the stated maximum is refused by a rule quoting a number nobody typed.
     *
     * **His own example is the first half of this test**, because it is the case that is NOT a fault: a
     * four-unit platform whose track is two stretches of two either side of a switch.  Nothing is wrong
     * with that railway - the geometry is finer than one number per station - and the notice exists so he
     * can say so and move on, which is why it is a NOTICE and not a warning.
     *
     * **The second half is the reversal**, which is the branch the switch case cannot reach: an edge
     * crossing no switch does not bound anything on its own, because the guard carries on walking back
     * through earlier edges - unless the train turns round where that edge starts, which is where the
     * walk stops under Adam's other ruling of the same day.
     *
     * **And three silences, each about a different missing half**, because a notice that fires when it
     * cannot know anything is the wall this list has twice been called.
     *
     * MUTATIONS: comparing against the edge's whole length rather than `getRoomAtTheEnd` loses the
     * switch case; taking the `isTurnAround` test out reports every station with a short arriving edge,
     * most of which the guard never judges on that edge; dropping the `room > 0` test reports every
     * unmeasured approach; and reporting the room as `{2}` and the maximum as `{3}` swaps the sentence
     * round, which the two assertions on the numbers catch.
     */
    @Test
    public void testTheEditorSaysWhenAPlatformHoldsLessTrackThanItClaims() throws Exception
    {
        session.open(Arrays.asList(platformBehindASwitch()));
        session.initialize("Short");

        TileKey platform = new TileKey("main", 5, 1);
        TileKey start = new TileKey("main", 1, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.rebuild();

        // NOTHING MEASURED: a railway that records no track lengths has decided not to model them, and
        // there is nothing to compare the maximum against.
        assertFalse(runInNotice() != null,
            "a railway that measures no track was told its platforms are shorter than their maxima, "
            + "which is a comparison with one side missing");

        // HIS EXAMPLE: a platform of four whose track is two either side of the switch.
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding notice =
            runInNotice();

        assertNotNull(notice, "a platform set to hold four units of train has two units of track "
            + "between it and the switch behind it, so a three-unit train is refused there whatever "
            + "the maximum says - and nothing told the operator (Adam, 2026-09-11)");

        assertEquals(notice.getSubject(), session.pointNameForTile(platform),
            "the notice is about some other square: " + notice);

        assertEquals(notice.getCount(), 4,
            "the first number in the sentence is the maximum somebody typed, which is 4 here");

        assertEquals(notice.getDetail(), 2,
            "the second number is the room the guard will actually measure, which is the two units "
            + "beyond the switch - not the four units of track on the edge");

        assertEquals(notice.getSeverity(),
            org.traincontrol.automationui.AutonomyChecks.Severity.NOTICE,
            "Adam's own example of this is a railway with nothing wrong with it - \"that is acceptable "
            + "and its limitations are understood\" - so it does not belong among the warnings");

        // AND IT GOES WHEN THE TRACK BEYOND THE SWITCH IS LONG ENOUGH.  The stretch before the switch
        // is left short on purpose: it is not the stretch the rule measures, so it must not matter.
        session.setTileLength(new TileKey("main", 4, 1), 4);
        session.rebuild();

        assertFalse(runInNotice() != null,
            "four units of track beyond the switch hold the four-unit train the platform claims, and "
            + "the notice is still asking about it");

        // A PLATFORM WITH NO STATED MAXIMUM IS NOT ASKED ABOUT: that is NO_MAX_TRAIN_LENGTH's sentence,
        // and a comparison needs two numbers.
        session.setPointProperty(platform, "maxTrainLength", 0);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertFalse(runInNotice() != null,
            "a platform with no stated maximum was compared against one");

        // AND AN UNMEASURED STRETCH IS UNKNOWN, NOT SHORT - the doctrine the guard itself follows.
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.setTileLength(new TileKey("main", 4, 1), 0);
        session.rebuild();

        assertFalse(runInNotice() != null,
            "the track beyond the switch has no length recorded and the platform was reported as "
            + "short. Unmeasured is unknown, not zero - and REVERSAL_NEEDS_LENGTH is the notice that "
            + "asks for a number where one is needed");

        // THE OTHER BRANCH: no switch between the two squares, and the train turns round at the far end.
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("ShortTurn");

        TileKey turns = new TileKey("main", 1, 1);
        TileKey berth = new TileKey("main", 4, 1);

        session.setStation(turns, true);
        session.setPointName(turns, "Turns");
        session.setPointProperty(turns, "canReverse", Boolean.TRUE);
        session.setStation(berth, true);
        session.setPointName(berth, "Berth");
        session.setPointProperty(berth, "maxTrainLength", 9);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 3, 1), 2);
        session.rebuild();

        assertTrue(session.isTurnAround(turns),
            "the fixture did not take: trains must turn round at the far end of this run");

        org.traincontrol.automationui.AutonomyChecks.Finding afterATurn =
            runInNotice();

        assertNotNull(afterATurn, "a platform set to hold nine units is four units of track from the "
            + "square trains turn round at, and the walk stops at a reversal just as it stops at a "
            + "switch (Adam, 2026-09-11) - so nine can never be reached there and nothing said so");

        assertEquals(afterATurn.getSubject(), session.pointNameForTile(berth),
            "the notice is about the wrong end of the run: " + afterATurn);

        assertEquals(afterATurn.getDetail(), 4,
            "the room is the four measured units between the turn and the platform");

        assertEquals(afterATurn.getThird(), 4, "a train setting off from the turn and longer than the four units to the"
            + " platform is refused there, and the notice does not give that figure (TDA-C10)");

        // AND THE SAME RUN WITH NOTHING TURNING AT ITS FAR END IS NOT ASKED ABOUT, because there the
        // guard walks on back through earlier edges and this edge bounds nothing.
        session.setPointProperty(turns, "canReverse", Boolean.FALSE);
        session.rebuild();

        assertFalse(session.isTurnAround(turns), "the fixture did not take: nothing should turn here now");

        assertFalse(runInNotice() != null,
            "an edge that crosses no switch and starts nowhere trains turn round bounds nothing - the "
            + "guard carries on back through the route - so the number this would quote is not the "
            + "number that would refuse the train");
    }

    /**
     * The first finding carrying a message key, or null.
     *
     * @param messageKey the check to look for
     * @return the finding, or null when nothing raised it
     */
    /** The run-in notice, whichever of its two sentences it has - the berth's, or a platform autonomy chooses (MT-555). */
    private org.traincontrol.automationui.AutonomyChecks.Finding runInNotice()
    {
        org.traincontrol.automationui.AutonomyChecks.Finding berth =
            findingFor(org.traincontrol.automationui.AutonomyChecks.RUN_IN_SHORTER_THAN_THE_BERTH);

        if (berth != null) return berth;

        // A platform's sentence, with the refusing figure where there is one.
        org.traincontrol.automationui.AutonomyChecks.Finding platform = findingFor(RUN_IN_AT_A_PLATFORM);

        return platform != null ? platform : findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);
    }

    /** The platform's own sentence (MT-555), by its key so the claims compile before it exists. */
    private static final String RUN_IN_AT_A_PLATFORM = "autosetup.ui.checkRunInShorterThanThePlatform";

    /** The same, with the figure a train is refused above on the shortest way in, by its key likewise. */
    private static final String RUN_IN_AT_A_PLATFORM_REFUSED = "autosetup.ui.checkRunInShorterThanThePlatformRefused";

    /**
     * The platform notice names the figure a train is refused above, where there is one (Adam, 2026-09-24).
     *
     * *"Add the refusing figure where there is one."*  `Layout.whyTooLongForThisRoute` admits a train longer than the
     * room past the switch only as far as the measured route in holds (FR-087, `theApproachItselfHoldsIt`), so a train
     * that sets off from the station just behind is refused above that route's length - on his railway, a four-unit train
     * from TopR1ParkShort at TopMainR1Inter.  Here the way in from 1,1 measures 4 (2, the switch, 2) and the platform is
     * set to 6: a train of 5 stands across the switch coming from further back, and is refused coming from 1,1.
     *
     * MUTATION: leave the figure out of the finding, or leave a station copy behind out of it, and this fails.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    public void testThePlatformNoticeNamesTheFigureATrainIsRefusedAbove() throws Exception
    {
        session.open(Arrays.asList(platformBehindASwitch()));
        session.initialize("Refused");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(new TileKey("main", 1, 1), true);
        session.setPointName(new TileKey("main", 1, 1), "Behind");
        session.setStation(platform, true);
        session.setPointName(platform, "Platform");
        session.setPointProperty(platform, "maxTrainLength", 6);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding notice = findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);

        assertNotNull(notice, "the platform is set to 6 and the way in from the station behind it measures 4, so a train"
            + " of 5 from there is refused - and the notice does not say so: " + session.check());

        assertEquals(notice.getDetail(), 2, "the room past the switch is still the notice's {3}");

        java.lang.reflect.Method third = notice.getClass().getMethod("getThird");

        assertEquals(third.invoke(notice), Integer.valueOf(4), "the figure a train is refused above is the way in from"
            + " 1,1: 2, the switch's 0, and 2");

        // AND WHERE THERE IS NONE: the maximum within the shortest way in, the sentence is the one without a figure.
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.rebuild();

        assertNull(findingFor(RUN_IN_AT_A_PLATFORM_REFUSED), "a platform set to 4, whose every way in measures 4, was"
            + " given a figure no train within its maximum is refused above");

        assertNotNull(findingFor(RUN_IN_AT_A_PLATFORM), "precondition: the room past the switch is 2 and the maximum 4,"
            + " so the notice without a figure is still due");
    }

    /**
     * No refusing figure from a station no train is started at facing the platform's way (found measuring the
     * frozen railway: Tunnel's notice said 3, from BottomInnerOtherside, where no route the railway runs into Tunnel
     * measures under 6).
     *
     * The square behind the platform is a station, but trains may not arrive at it from the west, so its copy heading
     * for the platform is no station and no train is started there - and nothing further back is a station either.  A
     * figure read off the square said a train setting off from it is refused above 4.
     *
     * MUTATION: take a square that is a station as one a train sets off from facing either way, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testNoRefusingFigureFromACopyNoTrainStartsAt() throws Exception
    {
        session.open(Arrays.asList(platformBehindAStationAndASwitch()));
        session.initialize("NoFigure");

        TileKey behind = new TileKey("main", 3, 1);
        TileKey platform = new TileKey("main", 7, 1);

        session.setStation(behind, true);
        session.setPointName(behind, "Behind");
        session.setBarredArrivals(behind, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.setStation(platform, true);
        session.setPointName(platform, "Platform");
        session.setPointProperty(platform, "maxTrainLength", 6);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.setTileLength(new TileKey("main", 6, 1), 2);
        session.rebuild();

        assertNotNull(runInNotice(), "precondition: the room past the switch is 2 and the maximum 6, so a run-in notice"
            + " is due: " + session.check());

        assertNull(findingFor(RUN_IN_AT_A_PLATFORM_REFUSED), "no train is started at the copy of the station behind"
            + " that heads for the platform - trains may not arrive at it from the west - and the notice gives a figure"
            + " for a train setting off from it: " + session.check());

        // THE CONTROL: the west side open, that copy is a station, and a train set off from it is refused above 4.
        session.setBarredArrivals(behind, java.util.EnumSet.noneOf(org.traincontrol.automationui.TilePorts.Side.class));
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding refused = findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);

        assertTrue(refused != null && refused.getThird() == 4, "with the west side open a train set off from the station"
            + " behind is refused above 4, the way in from it, and the notice does not say so: " + session.check());
    }

    /**
     * The refusing figure counts back over a sensor no train is started at, as the route in does: read
     * off one leg, it was given only where a station stood straight behind the platform - and on his railway a `...Pre`
     * sensor stands behind most of them.
     *
     * The station is two sensors back: 2 of track to the sensor, then 1, the switch and 2 to the platform.  A train set
     * off from it is refused above 5; the platform is set to 6.
     *
     * MUTATION: read the figure off the leg straight into the platform only, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheRefusingFigureCountsBackOverASensor() throws Exception
    {
        session.open(Arrays.asList(platformBehindAStationAndASwitch()));
        session.initialize("BackOverASensor");

        TileKey start = new TileKey("main", 1, 1);
        TileKey platform = new TileKey("main", 7, 1);

        session.setStation(start, true);
        session.setPointName(start, "Start");

        // A DEAD END TRAINS TURN AT: otherwise a train that came in there can never leave towards the platform, and the
        // build has no way in from it at all.
        session.setPointProperty(start, "canReverse", Boolean.TRUE);
        session.setStation(platform, true);
        session.setPointName(platform, "Platform");
        session.setPointProperty(platform, "maxTrainLength", 6);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 1);
        session.setTileLength(new TileKey("main", 6, 1), 2);
        session.rebuild();

        assertNotNull(runInNotice(), "precondition: the room past the switch is 2 and the maximum 6, so a run-in notice"
            + " is due: " + session.check());

        org.traincontrol.automationui.AutonomyChecks.Finding refused = findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);

        assertTrue(refused != null && refused.getThird() == 5, "a train set off from the station two sensors back is"
            + " refused above 5 - 2, then 1, the switch and 2 - and the notice does not say so: " + session.check());
    }

    /**
     * The refusing figure over built railways written by hand, one shape each:
     * the walk that reads it off the built railway, asked directly.
     *
     * - **A loop walked whole**: a leg first reached inside a loop of sensors is not remembered as having no way in.
     * - **A turn stops it**: behind a copy trains turn at, nothing counts, as the route in stops there.
     * - **A switched-off point is no way in**: the railway runs no route through one.
     * - **No way in from the platform itself**, round a loop from another of its copies.
     * - **A way in with no length hides nothing**: the railway refuses no train on it, and the measured way's figure stands.
     * - **Never under the room**: a way in with nothing measured after its switch is not the notice's, and gives none.
     *
     * MUTATION: remember a leg a loop cut short, count on past a turn, walk through a switched-off point or from the
     * platform's own copy, take a 0 as the least, or read a way in with no room after its switch - and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testTheRefusingFigureWalksTheBuiltRailwayAsTheRouteInDoes() throws Exception
    {
        // A LOOP WALKED WHOLE: S -> A -> B -> C -> A, and B -> P1, C -> P2, every leg 1.
        java.util.Map<String, Integer> loop = figures(
            new String[][] {{"S", "station"}, {"A", ""}, {"B", ""}, {"C", ""}, {"P1", "station"}, {"P2", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "B", 1, null}, {"B", "C", 1, null}, {"C", "A", 1, null},
                {"B", "P1", 1, 1}, {"C", "P2", 1, 1}});

        assertEquals(loop.get("P1"), Integer.valueOf(3), "P1's way in from S, over A and B, is 3: " + loop);
        assertEquals(loop.get("P2"), Integer.valueOf(4), "P2's way in from S, over A, B and C, is 4 - the leg B -> C was"
            + " remembered as having no way in, because the loop cut it short while P1 was walked (ADA2-C3): " + loop);

        // A TURN STOPS IT: S -> T (5), T turns, T -> X -> P, 1 each.
        java.util.Map<String, Integer> turn = figures(
            new String[][] {{"S", "station"}, {"T", "reversing"}, {"X", ""}, {"P", "station"}},
            new Object[][] {{"S", "T", 5, null}, {"T", "X", 1, null}, {"X", "P", 1, 1}});

        assertEquals(turn.get("P"), Integer.valueOf(2), "a train that turned at T lies back no further than T, so the way in"
            + " is 2 - the walk counted on past the turn (ADD2-C5): " + turn);

        // A SWITCHED-OFF POINT IS NO WAY IN: S -> A (off) -> P, 1 each, and S2 -> P, 5.
        java.util.Map<String, Integer> off = figures(
            new String[][] {{"S", "station"}, {"A", "off"}, {"S2", "station"}, {"P", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "P", 1, 1}, {"S2", "P", 5, 1}});

        assertEquals(off.get("P"), Integer.valueOf(5), "no route runs through a switched-off point, and the figure came"
            + " over one (ADA2-C3): " + off);

        // NO WAY IN FROM THE PLATFORM ITSELF: Q is another copy of P's square; Q -> Y -> P, 1 each, and S -> P, 6.
        java.util.Map<String, Integer> self = figures(
            new String[][] {{"P", "station"}, {"Q", "station=P"}, {"Y", ""}, {"S", "station"}},
            new Object[][] {{"Q", "Y", 1, null}, {"Y", "P", 1, 1}, {"S", "P", 6, 1}});

        assertEquals(self.get("P"), Integer.valueOf(6), "a route from a platform round to itself is no journey, and the"
            + " figure came from the platform's other copy (ADA2-C3): " + self);

        // A WAY IN WITH NO LENGTH HIDES NOTHING: S -> P measured 4; T, a copy trains turn at, -> P with no length.
        java.util.Map<String, Integer> zero = figures(
            new String[][] {{"S", "station"}, {"T", "terminus"}, {"P", "station"}},
            new Object[][] {{"S", "P", 4, 2}, {"T", "P", 0, null}});

        assertEquals(zero.get("P"), Integer.valueOf(4), "the railway refuses no train on a way in with no length, and"
            + " it took the platform's figure with it (ADA2-C1): " + zero);

        // NEVER UNDER THE ROOM: S -> P measures 3 before its switch and nothing after; T turns, T -> P measures 4.
        java.util.Map<String, Integer> room = figures(
            new String[][] {{"S", "station"}, {"T", "terminus"}, {"P", "station"}},
            new Object[][] {{"S", "P", 3, -1}, {"T", "P", 4, null}});

        assertEquals(room.get("P"), Integer.valueOf(4), "a way in with nothing measured after its switch is none of the"
            + " notice's, and its 3 made the figure less than the room the notice quotes (ADA2-C2): " + room);

        // IN WHATEVER ORDER THE BUILD LISTS THE LEGS: here the leg into the platform first, the one behind it after.
        java.util.Map<String, Integer> order = figures(
            new String[][] {{"S", "station"}, {"X", ""}, {"P", "station"}},
            new Object[][] {{"X", "P", 1, 1}, {"S", "X", 1, null}});

        assertEquals(order.get("P"), Integer.valueOf(2), "the walk stopped before it had worked back to the station"
            + " behind, with the legs listed into the platform first: " + order);

        // AN ANSWERED 0 IS PASSED, adding nothing, as the railway's route in passes it (Adam, 2026-09-25); a 0 nobody
        // answered still ends the walk.  S -1-> A -0-> B -1-> P.
        java.util.Map<String, Integer> answered = figures(
            new String[][] {{"S", "station"}, {"A", ""}, {"B", ""}, {"P", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "B", 0, null, true}, {"B", "P", 1, 1}});

        assertEquals(answered.get("P"), Integer.valueOf(2), "the walk stops at a leg answered 0, where the railway's"
            + " route in counts on past it: " + answered);

        java.util.Map<String, Integer> unanswered = figures(
            new String[][] {{"S", "station"}, {"A", ""}, {"B", ""}, {"P", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "B", 0, null}, {"B", "P", 1, 1}});

        assertEquals(unanswered.get("P"), Integer.valueOf(1), "CONTROL: the walk counts on past a leg nobody answered: "
            + unanswered);
    }

    /**
     * The refusing figures of a built railway written by hand: points as {name, kind} - kind "station", "reversing",
     * "terminus" (a station trains turn at), "off" (switched off), "station=X" (a station that is another copy of X's
     * square), or "" - and legs as {start, end, length, room at the end or null}, with true after them for a leg whose one
     * place was answered 0.
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Integer> figures(String[][] points, Object[][] legs) throws Exception
    {
        org.json.JSONObject built = new org.json.JSONObject();
        org.json.JSONArray pointList = new org.json.JSONArray();
        org.json.JSONArray edgeList = new org.json.JSONArray();

        java.util.Map<String, TileKey> named = new java.util.LinkedHashMap<>();

        int x = 0;

        for (String[] point : points)
        {
            org.json.JSONObject json = new org.json.JSONObject();

            json.put("name", point[0]);
            json.put("station", point[1].startsWith("station") || point[1].equals("terminus"));

            if (point[1].equals("reversing")) json.put("reversing", true);
            if (point[1].equals("terminus")) json.put("terminus", true);
            if (point[1].equals("off")) json.put("active", false);

            pointList.put(json);

            named.put(point[0], point[1].startsWith("station=") ? named.get(point[1].substring(8))
                : new TileKey("main", ++x, 0));
        }

        for (Object[] leg : legs)
        {
            org.json.JSONObject json = new org.json.JSONObject();

            json.put("start", leg[0]);
            json.put("end", leg[1]);
            json.put("length", leg[2]);

            if (leg[3] != null) json.put("roomAtTheEnd", leg[3]);

            if (leg.length > 4 && Boolean.TRUE.equals(leg[4]))
            {
                org.json.JSONObject place = new org.json.JSONObject();

                place.put("at", "main:" + leg[0] + leg[1]);
                place.put("length", 0);
                place.put("answered", true);

                json.put("places", new org.json.JSONArray().put(place));
            }

            edgeList.put(json);
        }

        built.put("points", pointList);
        built.put("edges", edgeList);

        java.lang.reflect.Method read = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "refusingFigures", org.json.JSONObject.class, java.util.Map.class);

        read.setAccessible(true);

        java.util.Map<TileKey, Integer> bySquare = (java.util.Map<TileKey, Integer>) read.invoke(session, built, named);

        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, TileKey> point : named.entrySet())
        {
            if (bySquare.containsKey(point.getValue())) out.put(point.getKey(), bySquare.get(point.getValue()));
        }

        return out;
    }

    /** A sensor, a station square, a switch with its branch, and a platform, west to east. */
    private LayoutDiagram platformBehindAStationAndASwitch() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 11, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 5, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        // The branch off the switch, lying north-south so that it has a port facing the switch at all.
        page.addComponent(componentType.FEEDBACK, 5, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.getComponent(5, 1).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, 7, org.traincontrol.base.Accessory.accessoryType.SWITCH, accessoryDecoderType.MM2,
            "Switch 7", false, 0));

        page.setPageId("1");

        return page;
    }

    /**
     * The sentence with the refusing figure is in all eight languages and names it as {4}.
     *
     * @throws Exception reading the bundles
     */
    @Test
    public void testTheRefusingFigureIsInEveryLanguage() throws Exception
    {
        java.io.File[] bundles = new java.io.File("src/org/traincontrol/resources").listFiles(
            (dir, name) -> name.startsWith("messages") && name.endsWith(".properties"));

        assertTrue(bundles != null && bundles.length == 8, "precondition: the eight message bundles are not where"
            + " this looks for them");

        for (java.io.File bundle : bundles)
        {
            java.util.Properties read = new java.util.Properties();

            try (java.io.InputStream in = new java.io.FileInputStream(bundle))
            {
                read.load(in);
            }

            String sentence = read.getProperty(RUN_IN_AT_A_PLATFORM_REFUSED);

            assertNotNull(sentence, bundle.getName() + " has no " + RUN_IN_AT_A_PLATFORM_REFUSED);

            assertTrue(sentence.contains("{2}") && sentence.contains("{3}") && sentence.contains("{4}"), bundle.getName()
                + ": the sentence names the maximum {2}, the room {3} and the refusing figure {4}: " + sentence);
        }
    }

    /**
     * A side trains may not arrive by is not an approach, and its run-in is not compared with the maximum (MT-552).
     *
     * Adam, 2026-09-24: *"RampDown and BottomMainPost should not have the ... warning as worded, since both sides are
     * measured (and moreover, they only accept arrivals from one side)."*  Every arriving edge was judged, the barred
     * one too - and no train comes in that way, so nothing about its track can refuse one.
     *
     * The control first: with both sides open the short side is reported, so the fixture can say it.
     *
     * MUTATION: judge every arriving edge in `runInsShorterThanTheBerth` again, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testABarredApproachIsNotJudgedForItsRunIn() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("Barred");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertNotNull(runInNotice(), "precondition: two units of track between the platform and the switch on its west"
            + " side, and a maximum of 4, is not reported - so this fixture cannot show the notice at all");

        session.setBarredArrivals(platform, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertNull(runInNotice(), "the platform takes no arrivals from the west, and the west side's run-in is still"
            + " compared with its maximum - Adam, MT-552: \"they only accept arrivals from one side\"");
    }

    /**
     * The half-measured warning says how many squares have no length, in every language (found with MT-552).
     *
     * A finding renders {0} as the square, {1} as its subject - here the station's name - {2} as its count and {3} as its
     * detail.  The sentence put {1} where it meant the count, so Adam's list read *"BottomMainPost can refuse trains ...
     * BottomMainPost squares leading to it still have no length"*.
     *
     * MUTATION: put {1} back in any one language, and this fails.
     *
     * @throws Exception reading the bundles
     */
    @Test
    public void testTheHalfMeasuredWarningCountsTheSquares() throws Exception
    {
        String key = org.traincontrol.automationui.AutonomyChecks.HALF_MEASURED_APPROACH;

        java.io.File[] bundles = new java.io.File("src/org/traincontrol/resources").listFiles(
            (dir, name) -> name.startsWith("messages") && name.endsWith(".properties"));

        assertTrue(bundles != null && bundles.length == 8, "precondition: the eight message bundles are not where"
            + " this looks for them");

        for (java.io.File bundle : bundles)
        {
            java.util.Properties read = new java.util.Properties();

            try (java.io.InputStream in = new java.io.FileInputStream(bundle))
            {
                read.load(in);
            }

            String sentence = read.getProperty(key);

            assertNotNull(sentence, bundle.getName() + " has no " + key);

            assertTrue(sentence.contains("{2}") && !sentence.contains("{1}"), bundle.getName() + ": the half-measured"
                + " warning names the station where it means the number of squares with no length - {1} is the"
                + " subject, {2} the count: " + sentence);
        }
    }

    /**
     * A side trains may not stop on is still asked to be measured for a turn there (Adam, 2026-09-24).
     *
     * *"Arrivals THAT STOP THERE should only be allowed from the configured side(s).  Turning shouldn't need to factor
     * this in, since the former would govern the behavior."*  A barred side keeps its turning copy, so a train may come
     * in that way and turn - and the room rule judges that turn over the track after the last switch, which is what the
     * reversal notice asks to be measured.  `e09fe989` skipped the barred side here along with the two berth checks,
     * which are right to: nothing stops there.
     *
     * MUTATION: skip the barred side in `reversalsWithoutLength` again, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testABarredApproachIsStillAskedToBeMeasuredForATurn() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("BarredTurn");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "canReverse", Boolean.TRUE);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 6, 1), 1);
        session.rebuild();

        assertTrue(session.isTurnAround(platform), "precondition: trains may not turn at the platform");

        assertTrue(session.reversalsWithoutLength().containsKey(platform), "precondition: the west side's track after"
            + " the switch, measured not at all, is not asked for - so this fixture cannot show the notice at all");

        session.setBarredArrivals(platform, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertTrue(session.reversalsWithoutLength().containsKey(platform), "the platform takes no train that STOPS"
            + " from the west, and a train may still come in that way and turn - yet the west side's track is no longer"
            + " asked for (Adam, TDA-C8: \"Turning shouldn't need to factor this in\"): " + session.reversalsWithoutLength());
    }

    /**
     * A side trains may not arrive by is not an approach, and its unmeasured track is not a half-measured one (MT-552).
     *
     * The same note: RampDown and BottomMainPost were told they *"can refuse trains that would otherwise fit"* for the
     * side nothing arrives by.
     *
     * MUTATION: judge every arriving edge in `stationsWithAHalfMeasuredApproach` again, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testABarredApproachIsNotJudgedAsHalfMeasured() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("BarredHalf");

        TileKey berth = new TileKey("main", 5, 1);

        session.setStation(berth, true);
        session.setAutoDestination(berth, false);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "precondition: a berth whose west"
            + " approach is measured in part is not reported - so this fixture cannot show the warning at all");

        session.setBarredArrivals(berth, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "the berth takes no arrivals from"
            + " the west, and it is still warned that the west approach is half measured - Adam, MT-552: \"they only"
            + " accept arrivals from one side\"");
    }

    /**
     * At a station autonomy may choose, a train longer than the run-in stands across the switch and blocks the railway
     * behind it; at a parking berth it is refused - and the notice says which (MT-555).
     *
     * Adam, 2026-09-24: *"Notices work, but they are wrong.  'so a train longer than x is refused' - 'so a train
     * longer than x may block other parts of the layout...'"*  `Layout.whyTooLongForThisRoute` refuses it at a berth
     * and, at a station autonomy may choose, admits it as far as the measured route in holds (FR-087).
     *
     * MUTATION: word both kinds of station the same, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAPlatformIsToldALongTrainMayBlockAndABerthThatItIsRefused() throws Exception
    {
        session.open(Arrays.asList(platformBehindASwitch()));
        session.initialize("Wording");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertTrue(session.isAutoDestination(platform), "precondition: a new station is not one autonomy may choose");

        assertEquals(findingsAbout(RUN_IN_AT_A_PLATFORM, platform), 1, "a station autonomy may choose is not told that a"
            + " train longer than its run-in may block the railway - Adam, MT-555: \"so a train longer than x may block"
            + " other parts of the layout\"");

        assertEquals(findingsAbout(org.traincontrol.automationui.AutonomyChecks.RUN_IN_SHORTER_THAN_THE_BERTH, platform),
            0, "a station autonomy may choose is told a train longer than its run-in is refused, and it is admitted");

        assertFalse(org.traincontrol.util.I18n.t(RUN_IN_AT_A_PLATFORM).contains("refused"), "the platform's sentence"
            + " still says the train is refused: " + org.traincontrol.util.I18n.t(RUN_IN_AT_A_PLATFORM));

        session.setAutoDestination(platform, false);
        session.rebuild();

        assertEquals(findingsAbout(org.traincontrol.automationui.AutonomyChecks.RUN_IN_SHORTER_THAN_THE_BERTH, platform),
            1, "a parking berth is not told that a train longer than its run-in is refused, which it is");
    }

    /**
     * A home on a square every train turns at is warned about only at a parking berth no route can back a train into
     * (Adam, 2026-10-11, of BottomMainC: *"we get a 'every train must turn around... and would have to back out...'
     * warning on BottomMainC, but it is an active terminus that would forbid non reversing trains.  The warning should
     * only be shown for a parking spot (terminus, not chooseable by autonomy) without a way to reverse in"*).  A way to
     * reverse in is another terminus that leads straight to the berth: Return Home rests a train that cannot reverse
     * there and drives it home tail first - Adam's two-step parking (*"That's why we started the 2 step process
     * for parking, which is OK in my opinion."*) - and with one, MT-552's *"Non-reversible trains can still be backed in
     * there"* holds.  A reversing point on the way is not one: no door drives a route on through it and back.
     *
     * A line B - X - R: B the home, every train turning there.  Active: no warning.  A parking berth with R a plain
     * sensor: the warning.  R a reversing point: still the warning.  R a terminus - a train rested there is backed into
     * B: no warning.
     *
     * MUTATION: warn at an active terminus again, or at a berth a train can back into, or at no berth at all, or count a
     * reversing point on the way as a way in, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAHomeIsWarnedAboutTurningOnlyAtABerthNoTrainCanBackInto() throws Exception
    {
        session.open(Arrays.asList(aBerthWithARunPast()));
        session.initialize("Home");

        TileKey berth = new TileKey("main", 1, 1);
        TileKey far = new TileKey("main", 7, 1);

        session.setStation(berth, true);
        session.setPointProperty(berth, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);
        session.setHome(berth, "Test Loc");
        session.rebuild();

        String key = org.traincontrol.automationui.AutonomyChecks.HOME_NEEDS_REVERSIBLE;

        assertEquals(findingsAbout(key, berth), 0, "a home on a terminus autonomy may choose is warned that a locomotive"
            + " which cannot reverse is driven in nose first - Adam: \"it is an active terminus that would forbid non"
            + " reversing trains\"");

        session.setAutoDestination(berth, false);
        session.rebuild();

        assertEquals(findingsAbout(key, berth), 1, "a home on a parking berth no route can back a train into is not"
            + " warned about - Adam: \"The warning should only be shown for a parking spot ... without a way to reverse"
            + " in\"");

        // A reversing point beyond it is not a way in: no door drives a route on through one and back
        session.setPointFlag(far, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, true);
        session.rebuild();

        assertEquals(findingsAbout(key, berth), 1, "a reversing point beyond a parking berth is taken for a way to back a"
            + " train in, which no door drives: Return Home drives the train straight in, nose first (RSA66-C3)");

        // A terminus beyond it is: Return Home rests the train there and backs it home (Adam's two-step parking)
        session.setPointName(far, "HomeFar");
        session.setStation(far, true);
        session.rebuild();

        assertEquals(findingsAbout(key, berth), 0, "a home on a parking berth a train can be backed into - rested at the"
            + " terminus beyond it, as Return Home does - is still warned about turning round");
    }

    /**
     * A way to back a train into a berth counts the turns on the way.  A
     * train rested on a terminus sets off turned, and each reversing point it passes turns it again; an odd count brings
     * it in tail first - backed in.  A reversing point passed inline (MT-245's S - reversing M - terminus T, which Return
     * Home drives) is a way in; one on a stub, which turns the train back where it came from, is not, nor is a rest
     * followed by one turn, which brings it in nose first again.  Asked of small graphs written as the build writes them.
     *
     * MUTATION: drop the stub rule, ignore a reversing point's turn, seed a rest unturned, count any turn rather than an
     * odd number, or walk through a square out of service, and this fails.
     */
    @Test
    public void testAWayToBackInCountsTheTurnsOnTheWay()
    {
        // inline: S - M (reversing) - T
        assertTrue(backedInto(new String[][] {{"S", ""}, {"M", "reversing"}, {"T", "terminus"}},
            new String[][] {{"S", "M"}, {"M", "T"}}).contains("T"), "a reversing point passed inline, as Return Home"
            + " drives it (MT-245), is not counted as a way to back a train into the berth beyond it");

        // a stub: X - R (reversing) and back, then X - B
        assertFalse(backedInto(new String[][] {{"X", ""}, {"R", "reversing"}, {"B", "terminus"}},
            new String[][] {{"X", "R"}, {"R", "X"}, {"X", "B"}}).contains("B"), "a reversing point on a stub - turned"
            + " straight back where it came from, which no door drives - is counted as a way in (RSA66-C3)");

        // a rest: R2 (terminus) - X - B
        assertTrue(backedInto(new String[][] {{"R2", "terminus"}, {"X", ""}, {"B", "terminus"}},
            new String[][] {{"R2", "X"}, {"X", "B"}}).contains("B"), "a rest on another terminus that leads straight to the"
            + " berth is not counted as a way in - Adam's two-step parking");

        // direct only: X - B
        assertFalse(backedInto(new String[][] {{"X", ""}, {"B", "terminus"}}, new String[][] {{"X", "B"}}).contains("B"),
            "a berth reached only nose first is counted as one a train can be backed into");

        // a rest and two turns: tail first
        assertTrue(backedInto(new String[][] {{"T2", "terminus"}, {"R1", "reversing"}, {"R2", "reversing"},
            {"B", "terminus"}}, new String[][] {{"T2", "R1"}, {"R1", "R2"}, {"R2", "B"}}).contains("B"), "a rest and two"
            + " turns - three in all, tail first - is not counted as a way in");

        // a rest and one turn: nose first again
        assertFalse(backedInto(new String[][] {{"T2", "terminus"}, {"R1", "reversing"}, {"B", "terminus"}},
            new String[][] {{"T2", "R1"}, {"R1", "B"}}).contains("B"), "a rest and one turn - nose first again - is"
            + " counted as a way to back a train in");

        // a reversing point out of service is not passed
        assertFalse(backedInto(new String[][] {{"S", ""}, {"M", "reversing shut"}, {"T", "terminus"}},
            new String[][] {{"S", "M"}, {"M", "T"}}).contains("T"), "a reversing point out of service is passed on the"
            + " way in");
    }

    /**
     * The berths a train can be backed into, on a graph written as the build writes one: each point {name, kind}, kind
     * holding "terminus", "reversing" and "shut" as it is; each edge {start, end}.  Each point on its own square.
     */
    private static java.util.Set<String> backedInto(String[][] points, String[][] edges)
    {
        org.json.JSONArray pointList = new org.json.JSONArray();
        java.util.Map<String, TileKey> tiles = new java.util.HashMap<>();

        int x = 1;

        for (String[] point : points)
        {
            org.json.JSONObject json = new org.json.JSONObject().put("name", point[0]);

            if (point[1].contains("terminus")) json.put("terminus", true);
            if (point[1].contains("reversing")) json.put("reversing", true);
            if (point[1].contains("shut")) json.put("active", false);

            pointList.put(json);

            tiles.put(point[0], new TileKey("main", x++, 1));
        }

        org.json.JSONArray edgeList = new org.json.JSONArray();

        for (String[] edge : edges) edgeList.put(new org.json.JSONObject().put("start", edge[0]).put("end", edge[1]));

        java.util.Set<TileKey> squares = org.traincontrol.automationui.AutonomySession.berthsATrainCanBackInto(
            new org.json.JSONObject().put("points", pointList).put("edges", edgeList), tiles);

        java.util.Set<String> names = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, TileKey> tile : tiles.entrySet())
        {
            if (squares.contains(tile.getValue())) names.add(tile.getKey());
        }

        return names;
    }

    /** B (1,1) - X (4,1) - R (7,1), track between: a berth at one end of a run past it. */
    private LayoutDiagram aBerthWithARunPast() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * And the station-capacity notice is gated the same way.
     *
     * The two halves of the length rule are `placedTrainsWithoutLength` and `stationsWithoutMaxLength`,
     * and only the reversal notice ever got Adam's condition: *"if any other track length is set
     * anywhere"*.  So a railway that models no lengths at all had every one of its stations listed -
     * thirty of them on his - beside the real problems, on the list this feature's own javadoc twice
     * says is made useless by exactly that.
     *
     * The same fixture, asked twice, with one length recorded somewhere else entirely between them.
     *
     * MUTATION this catches: removing the `measuresAnyTrack()` gate reports the station in both halves.
     */
    @Test
    public void testTheStationCapacityNoticeWaitsForALayoutThatMeasures() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Capacity");

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);
        session.rebuild();

        assertTrue(session.getStore().isStation(station),
            "the fixture did not take: this square must be a station");

        // NOTHING MEASURED ANYWHERE: no notice.
        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(station)),
            "a layout that records no lengths at all was asked for a station capacity, which is a "
            + "notice about something nobody on that railway is trying to do");

        // One length, on a different square, and the layout is one that measures.
        session.setTileLength(new TileKey("main", 2, 1), 7);

        assertTrue(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(station)),
            "the layout measures track now, and a station with no capacity was not asked about - so "
            + "the half of the length rule that stops a train being sent somewhere too short for it "
            + "has nothing to work with and nothing says so");
    }

    /**
     * The station-capacity notice does not need a measured tile.
     *
     * This check was gated on `measuresAnyTrack()`, which is Adam's condition for the REVERSAL
     * notice - and there the gate and the notice are about the same data, tile lengths. Station
     * capacity is not that data: `Point.validateTrainLength` compares the station's typed maximum
     * against the locomotive's own authored length, and no tile length enters into it.
     *
     * So the gate was a precondition that lost its subject on the way over, and somebody who authors
     * train lengths and station maxima but has never measured track got no notices at all - about the
     * feature they are actively using.
     *
     * MUTATION this catches: putting `measuresAnyTrack()` back in place of `modelsAnyLength()`.
     */
    @Test
    public void testTheStationCapacityNoticeDoesNotNeedAMeasuredTile() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Capacity");

        TileKey withMaximum = new TileKey("main", 1, 1);
        TileKey without = new TileKey("main", 4, 1);

        session.setStation(withMaximum, true);
        session.setStation(without, true);
        session.rebuild();

        // NOTHING MEASURED, AND NO MAXIMUM ANYWHERE: this railway does not model lengths.
        assertTrue(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH).isEmpty(),
            "a railway that models no lengths at all was asked for station capacities, which is a "
            + "notice about something nobody on that railway is trying to do: "
            + subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH));

        // One station is given a maximum.  No track is measured, and none needs to be.
        session.setPointProperty(withMaximum, "maxTrainLength", 8);
        session.rebuild();

        assertTrue(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(without)),
            "this railway models lengths - a station carries a maximum - and the station that does "
            + "not was never asked about.  The gate was asking whether any TRACK is measured, which "
            + "station capacity does not use: "
            + subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH));

        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(withMaximum)),
            "the station that HAS a maximum is being asked for one");
    }
}
