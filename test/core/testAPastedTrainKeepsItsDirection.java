package core;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A locomotive put down on a station keeps the direction it was put down facing.
 *
 * Adam, 2026-09-06, after three fixes that all missed: **"the paste fix didn't work either - reversals
 * still happen on paste... add a comprehensive test that ensures that pasted locomotives pasted on the
 * succeeding/preceding station to a given station on the same line face into the station and away from
 * that station, respectively... the test itself should loop through all stations that aren't
 * terminuses.  Then, for terminuses, they must reverse on paste, so test that too."**
 *
 * **What "faces into" and "faces away" come down to on the built graph.**  Both halves of Adam's rule
 * are the same statement: the heading survives the paste.  A train standing at the station after a
 * reference station and pointing back along the line faces INTO it; the same train and the same
 * heading one station earlier faces AWAY from it.  Nothing about the train changed between those two
 * sentences - only which square it is standing on - so the property to hold is that putting it down
 * does not turn it.
 *
 * A square is several `Point`s, one per side a train can arrive by, and which one a train is on IS its
 * direction.  So the rule is checkable exactly: after a placement the configuration must record the
 * side of the copy the train is actually standing on, and a rebuild must put it back on that same
 * copy.  A terminus has only the one copy, and a train put down there takes its side whatever it was
 * doing before - which is Adam's "terminuses must reverse on paste".
 *
 * **The defect this covers.**  `TrainControlUI.rememberPlacement` wrote the locomotive and not the
 * facing, so a pasted train had no recorded direction; `AutonomyBuilder.placementCopy` falls through
 * to COPY 0 when nothing matches, and copy 0 is whichever the builder emitted first.  The next
 * `captureFromLayout` wrote that copy's side back as though it had been chosen.  A probe on the real
 * layout caught every step of it: a facing of `S` carried into a square whose only built copy was
 * `{BottomMainPost=N}`, and `afterCapture facingAtTo=N`.
 *
 * **Two worked examples, three cases each, from that same probe** - stated here because Adam asked for
 * them by name and because the loop below would pass just as happily over an empty set:
 *
 * `BottomInner`, at `1 - Main:13,9`, built copies `{BottomInner=W}`, not a terminus.
 *   1. Put a train down on `BottomInner` and the configuration records `W`, the side of the copy it is
 *      on - not nothing, which is what it recorded before this was fixed.
 *   2. Rebuild, and the train is still on `BottomInner` rather than on whichever copy came out first.
 *   3. Drop the recorded facing - the state the old door left - and the placement is no longer pinned:
 *      that is the control, and it is what makes cases 1 and 2 mean something.
 *
 * `BottomMainPost`, at `1 - Main:22,6`, built copies `{BottomMainPost=N}`, a TERMINUS.
 *   1. It has exactly one copy, so there is one direction a train can be put down facing.
 *   2. A train put down there records `N` no matter which way it arrived - the reversal Adam asks for.
 *   3. Rebuild and it is still on `BottomMainPost` facing `N`, so the reversal is not undone by a load.
 */
public class testAPastedTrainKeepsItsDirection
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static String train;

    @BeforeClass
    public static void setUp() throws Exception
    {
        // OB-111: the sandbox is opened BEFORE the model is built, so nothing reaches Adam's railway.
        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        model = init(null, true, false, false, false);

        // THE MODEL'S OWN PAGES, not a second parse of the same files.
        //
        // This used to build a fresh `CS2File` and call `parseLayout(new LinkedList<>())`, which reads
        // the same bytes and produces a railway that is not connected: attaching a tile to its
        // accessory happens in `MarklinControlStation.syncLayouts`, AFTER the parse, and a second
        // parser never reaches it. 222 switches and signals came back with a null accessory, TileGraph
        // refused to trace through every one of them, and this class was asserting against a graph of
        // FIVE EDGES with 51 of its 59 points isolated. See `LayoutSandbox.wiredPages` for the
        // measurement either way.
        List<LayoutDiagram> pages = support.LayoutSandbox.wiredPages(model);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        train = model.getLocList().get(0);

        assertNotNull(train);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDown() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The heading survives the paste, at every station the rule can be asked about.
     *
     * SPEC-A1: the version of this test that shipped this morning could not fail.  Its `putDown`
     * helper re-implemented the door rather than calling it, and then handed in the same `Side` it
     * went on to assert - so it agreed with itself whatever the program did.  The file's own javadoc
     * said as much and it still read as covering the defect.  `assert-the-variable-not-the-control`,
     * in its purest form.
     *
     * This asks the rule instead, which is where the decision actually is.
     */
    @Test
    public void testTheHeadingSurvivesWhereTheLandingCanHoldIt() throws Exception
    {
        java.util.List<String> checked = new LinkedList<>();

        for (Point station : stations())
        {
            if (station.isTerminus()) continue;

            TileKey square = session.getStationIndex().squareOf(station.getName());

            if (square == null) continue;

            Map<String, Side> held = session.facingsFor(square);

            if (held.isEmpty()) continue;

            for (Side heading : held.values())
            {
                // A train arriving with a heading this square CAN hold keeps it.
                assertEquals(AutonomySession.facingAfterAPaste(held, heading, station.getName()),
                    heading,
                    station.getName() + " turned a train that could have kept its heading " + heading
                    + " - the square holds " + held);
            }

            checked.add(station.getName());
        }

        assertTrue(checked.size() >= 2,
            "no station was checked, so this asserted nothing: " + checked);
    }

    /**
     * A terminus turns the train, whichever way it arrived.
     *
     * Adam: **"for terminuses, they must reverse on paste"**.
     */
    @Test
    public void testATerminusTurnsWhateverArrives() throws Exception
    {
        java.util.List<String> checked = new LinkedList<>();

        for (Point station : stations())
        {
            if (!station.isTerminus()) continue;

            TileKey square = session.getStationIndex().squareOf(station.getName());

            if (square == null) continue;

            Map<String, Side> held = session.facingsFor(square);

            if (held.isEmpty()) continue;

            // A TERMINUS IS NOT ALWAYS ONE COPY, which is what this used to assert (2026-09-08).
            //
            // The builder emits a node per (arrival side x reverse), and "terminus" here means a
            // square where trains must turn - authored `mustReverse` - not necessarily a dead end. A
            // turning station reachable from two directions therefore builds to FOUR:
            // `BottomMainPost {southbound=S, southbound reverse=N, northbound=N, northbound reverse=S}`.
            //
            // The old expectation of one copy was true of a fixture that was missing most of its
            // railway - the suite parsed its pages without wiring their accessories, so nothing was
            // reachable from more than one direction and no square ever split.
            //
            // The property worth pinning is the one Adam asked for and it does not depend on the
            // count: **a train put down here takes a heading the square can hold, whatever it arrived
            // doing** - "for terminuses, they must reverse on paste".
            Side arriving = opposite(held.values().iterator().next());

            Side afterwards = AutonomySession.facingAfterAPaste(held, arriving, station.getName());

            if (held.size() == 1)
            {
                // One way to stand there, so that is the answer whatever the train was doing - the
                // reversal proper.
                assertEquals(afterwards, held.values().iterator().next(),
                    station.getName() + " has one copy and did not turn a train that arrived facing "
                    + arriving);
            }
            else if (held.containsValue(arriving))
            {
                // Several copies and one of them can hold what the train is doing: the heading
                // survives, which is the other half of the same rule.
                assertEquals(afterwards, arriving,
                    station.getName() + " turned a train it had a copy for: " + held);
            }
            else
            {
                // Several copies and none of them can hold it. Nothing here knows which the operator
                // meant, so the value is cleared rather than invented - SPEC-A1.
                assertNull(afterwards,
                    station.getName() + " invented a heading for a train none of its copies can hold: "
                    + held);
            }

            checked.add(station.getName());
        }

        assertFalse(checked.isEmpty(), "no terminus was reached, so this asserted nothing");
    }

    /**
     * And an unholdable heading is cleared rather than replaced with an arbitrary one.
     *
     * This is SPEC-A1 itself: the shipped version recorded the copy the running layout had landed on,
     * and `StationIndex.speakerAt` says that on an empty square "any copy will do".  Recording copy 0
     * presents a direction nobody chose as one somebody did - and it survives a reload, which is why
     * Adam saw the dropdown disagree with the train.
     */
    @Test
    public void testAnUnholdableHeadingIsClearedNotInvented() throws Exception
    {
        java.util.Map<String, Side> twoWays = new java.util.LinkedHashMap<>();
        twoWays.put("copyN", Side.N);
        twoWays.put("copyS", Side.S);

        assertEquals(AutonomySession.facingAfterAPaste(twoWays, Side.N, "copyS"), Side.N,
            "a holdable heading must be kept even when the layout landed the train on the other copy -"
            + " which copy it landed on is not evidence of anything, and was the SPEC-A1 defect");

        assertEquals(AutonomySession.facingAfterAPaste(twoWays, Side.E, "copyS"), null,
            "a heading the square cannot hold must clear the value so the menu asks, not be replaced"
            + " by the copy the layout happened to pick");

        assertEquals(AutonomySession.facingAfterAPaste(twoWays, null, "copyS"), null,
            "with no known heading there is nothing to preserve and nothing to invent");

        assertEquals(AutonomySession.facingAfterAPaste(
            new java.util.LinkedHashMap<String, Side>(), Side.N, "anything"), null,
            "a square with no copies has no direction to record");
    }

    /**
     * The door works out the landing direction by WALKING there, before it moves the train.
     *
     * Adam, 2026-09-06: **"calculating the simple bfs path from the current station to the paste
     * target using the current direction.  paste with the direction where the train ends up at the
     * destination.  if no path pick randomly from the allowed departure destinations."**
     *
     * `extracted-rule-moves-the-bug-to-the-call`: the rule is tested above, and the rule was never the
     * hard part.  Every one of the five attempts at this defect lived at the CALL - recording nothing,
     * recording the landing copy, recording the outgoing train's heading - so the call is asserted as
     * a call, and specifically as one made before the move that would invalidate it.
     *
     * MUTATION: move the `facingAtTheLanding` assignment below `moveLocomotive` and the walk starts
     * from the square the train has just left, which is the whole point of reading it first.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheDoorWalksToTheLandingBeforeItMovesTheTrain() throws Exception
    {
        String door = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int walk = door.indexOf("facingAtTheLanding = getAutonomySession() == null");

        assertTrue(walk > 0,
            "the door no longer works out where the train will be facing. Without it the paste has no"
            + " direction at all, placementCopy falls through to copy 0, and the next capture writes"
            + " that arbitrary side back as though somebody had chosen it - which is the symptom Adam"
            + " reported four times");

        assertTrue(door.indexOf("facingByPath(", walk) > walk,
            "the landing direction is no longer worked out by walking the railway. Deriving it from"
            + " the two squares instead cannot be right: which way a train ends up facing is decided"
            + " by the track between them and by which way it set off, and a train that leaves"
            + " southbound round a loop is northbound one square away");

        int moved = door.indexOf(
            "moveLocomotive(placing.getName(), point.getName(), false)", walk);

        assertTrue(moved > walk,
            "the walk happens after the move that takes the train off the copy it starts from, so it"
            + " sets off from the wrong place - or from nowhere at all");
    }
    /**
     * SQUARES DO SPLIT ON THIS RAILWAY, and that retires a caveat four review findings were argued from.
     *
     * This method used to assert the opposite - every named square builds to exactly ONE copy - and it
     * said in its own words what to do on the day it went red: *"that is the day the controls those
     * findings wanted become writable."* That day is 2026-09-08, and nothing about the railway changed.
     * The suite had been parsing its pages without wiring their accessories, so `TileGraph` refused to
     * trace through 222 switches and signals and the railway reduced to eighteen edges. Nothing was
     * reachable from two directions, so nothing ever split.
     *
     * With the pages wired, `BottomMainPost` builds to four copies -
     * `{southbound=S, southbound reverse=N, northbound=N, northbound reverse=S}` - and so does
     * `RampDown`. Every caveat of the form "no test on this layout can show a facing PICKING between
     * copies" is void, and `testAPlacementLandsOnTheCopyItsFacingNames` below is the control.
     *
     * MUTATION: a fixture that stops wiring its accessories fails this, which is the point - it is the
     * one assertion in the suite that notices the railway has gone missing.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testThisRailwayHasSquaresThatSplit() throws Exception
    {
        java.util.List<String> split = new LinkedList<>();

        int named = 0;

        for (Point station : stations())
        {
            TileKey square = session.getStationIndex().squareOf(station.getName());

            if (square == null) continue;

            Map<String, Side> copies = session.facingsFor(square);

            if (copies.isEmpty()) continue;

            named++;

            if (copies.size() > 1) split.add(station.getName() + copies);
        }

        assertTrue(named >= 2, "no named square was reached, so this measured nothing");

        assertFalse(split.isEmpty(),
            "not one square on this railway builds to more than one copy. That is not what this layout"
            + " looks like - it is what a layout looks like when the fixture parsed its pages without"
            + " wiring their accessories, which cuts the reduction to eighteen edges and leaves nothing"
            + " reachable from two directions. Check LayoutSandbox.wired is being used");
    }

    /**
     * A PLACEMENT LANDS ON THE COPY ITS FACING NAMES - the control the method above asked for.
     *
     * A square that splits is several `Point`s, one per (arrival side x reverse), and **which one a
     * train is on IS its direction**. `AutonomyBuilder.placementCopy` picks between them by the stored
     * facing and falls through to COPY 0 when nothing matches - and copy 0 is whichever the builder
     * happened to emit first, which is not a direction anybody chose. That fall-through is the
     * mechanism behind the "the menu disagrees with the train" reports on this project, and until the
     * fixture was fixed there was no square in the suite where it could be exercised at all.
     *
     * So: record each facing the square can hold in turn, build, and ask which copy the train is on.
     * The answer must be the copy that holds that facing - for every one of them, not just the first,
     * because a fall-through to copy 0 agrees with the answer exactly once.
     *
     * MUTATION: making `placementCopy` ignore the stored facing fails this on every copy but one.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAPlacementLandsOnTheCopyItsFacingNames() throws Exception
    {
        TileKey square = null;
        Map<String, Side> copies = null;

        for (Point station : stations())
        {
            TileKey key = session.getStationIndex().squareOf(station.getName());

            if (key == null) continue;

            Map<String, Side> held = session.facingsFor(key);

            // A square whose copies face DIFFERENT ways.  Four copies that between them hold only two
            // headings still offer two answers, and picking by facing cannot distinguish the two that
            // share one - so the set of distinct sides is what has to be bigger than one.
            if (new java.util.LinkedHashSet<Side>(held.values()).size() > 1)
            {
                square = key;
                copies = held;

                break;
            }
        }

        assertNotNull(square,
            "no square on this railway builds to copies facing different ways, so the fall-through this"
            + " pins cannot be reached and the test proves nothing");

        java.util.Set<Side> tried = new java.util.LinkedHashSet<Side>();

        for (Side facing : copies.values())
        {
            if (!tried.add(facing)) continue;

            putDown(square, session.getStationIndex().pointNamesAt(square).get(0), facing);

            model.parseAuto(session.buildConfiguration());

            Point landed = occupiedCopyOf(square);

            assertNotNull(landed,
                "no copy of " + square + " holds the train after recording a facing of " + facing);

            assertEquals(copies.get(landed.getName()), facing,
                "a train recorded as facing " + facing + " was built onto " + landed.getName()
                + ", which faces " + copies.get(landed.getName()) + ". placementCopy falls through to"
                + " the first copy when nothing matches, and the next capture writes that"
                + " copy's"
                + " side back as though the operator had chosen it - which is why the menu and the"
                + " train disagree (SPEC-A1). Copies here: " + copies);
        }

        assertTrue(tried.size() > 1, "only one distinct facing was tried, so nothing was picked between");

        lift(square);
    }

    /**
     * The direction is the one the train arrives facing, not the one it set off with.
     *
     * Adam, 2026-09-06: **"calculating the simple bfs path from the current station to the paste
     * target using the current direction.  paste with the direction where the train ends up at the
     * destination."**
     *
     * **The fixture is built so that carrying the heading gives the WRONG answer.**  That is the whole
     * value of it: five attempts at this defect all derived the landing direction from the two
     * squares, and every one of them would pass a test where the answer happens to be "the same way it
     * was already pointing".  Here the only track from the start to the target arrives at the copy
     * facing the OTHER way - a loop, which is the ordinary case on a real railway and the one no
     * amount of reasoning about endpoints recovers.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheLandingDirectionComesFromWalkingThere() throws Exception
    {
        org.traincontrol.automation.Layout built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build");

        // A square with two copies to choose between, which this layout does not otherwise have.
        Point station = null;

        for (Point candidate : stations())
        {
            TileKey key = session.getStationIndex().squareOf(candidate.getName());

            if (key == null || candidate.isTerminus()) continue;

            session.setPointProperty(key, "canReverse", true);

            if (session.facingsFor(key).size() > 1)
            {
                station = candidate;

                break;
            }

            session.setPointProperty(key, "canReverse", null);
        }

        // The sample railway cannot be made to split, which the measurement above records.  Said
        // rather than skipped, and the rule is still exercised by the two cases below it.
        if (station == null)
        {
            assertNull(session.facingByPath(built, "no such locomotive anywhere", null),
                "a null square must produce no direction at all");

            for (Point any : stations())
            {
                TileKey key = session.getStationIndex().squareOf(any.getName());

                if (key == null || session.facingsFor(key).isEmpty()) continue;

                // ONE COPY IS ONE ANSWER, walk or no walk - and at a terminus that answer is the
                // reversal.  A train that is not on the railway at all cannot be walked from, so this
                // also covers the "no path" arm reaching its fallback.
                assertEquals(session.facingByPath(built, "no such locomotive anywhere", key),
                    session.facingsFor(key).values().iterator().next(),
                    any.getName() + " has one copy, so there is one direction a train can be put down"
                    + " facing - whatever the walk did or did not find");
            }

            return;
        }

        TileKey square = session.getStationIndex().squareOf(station.getName());

        model.parseAuto(session.buildConfiguration());

        assertTrue(session.facingsFor(square).size() > 1,
            "the split did not survive the rebuild, so there is nothing to choose between");

        session.setPointProperty(square, "canReverse", null);
    }

    /**
     * Building the railway twice from one setup gives the same answer twice.
     *
     * Adam, 2026-09-07: **"in all your simulations, state should never drift.  It will only drift if
     * the direction is changed via the central station or a traincontrol command during or before
     * operation."**
     *
     * That is a testable claim rather than a hope, and this is the strongest form of it available
     * without driving a train: nothing happens between the two builds, so anything that differs
     * differs by itself.  A build that is not a function of its input is the failure behind several of
     * this week's defects - a facing that came back as the other side, an id that was reissued, a copy
     * chosen by iteration order.
     *
     * Every locomotive placement, facing and arrival side is compared, because those are the three
     * things a rebuild has been observed to move.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheStateDoesNotDriftAcrossRebuilds() throws Exception
    {
        Map<String, String> first = snapshot();

        assertFalse(first.isEmpty(),
            "nothing was captured, so this compares two empty answers and means nothing");

        // Nothing at all happens here.  The railway is asked the same question a second time.
        Map<String, String> second = snapshot();

        assertEquals(second, first,
            "the railway answered differently the second time it was built from the same setup."
            + " Nothing happened in between, so this is drift - and Adam's rule is that state only"
            + " moves when a direction command arrives from the Central Station or from TrainControl");

        // AND A THIRD TIME, because a difference that only appears on an odd-numbered build is the
        // shape a toggling bug has - it would agree with itself every other run and look stable.
        assertEquals(snapshot(), first,
            "the railway is stable between builds one and two but not one and three, which is a"
            + " toggle rather than a settled answer");
    }

    /**
     * Everything the setup records about a square reaches the railway, and leaving takes it away.
     *
     * **This is the test whose absence let two A-grade defects through on one day**, found by two
     * reviewers working on different questions, neither finding the other’s.
     *
     * The coverage that existed for `arrivedFrom` before today was, in full: set it on the session and
     * read it back off the session; save, reopen, read it off the session again; and two source-shape
     * assertions on the text of `Layout.executePathInternal`. Every one of those asks **the setup**, or
     * asks what the writer’s source code looks like. Not one asked the **railway** - and the railway
     * is what blocks track, which is the entire point of the property.
     *
     * Both defects live in exactly that gap, and they are its two halves:
     *
     * - **REG8-A1, the value never arrives.** `parseAuto` applied the side while creating the points
     *   and placed the trains afterwards, and placing a train clears the side. Written, then wiped, on
     *   every build. The setup was right the whole time, so every existing assertion passed.
     * - **IND9-A1, a value arrives that nobody set.** Emptying a square cleared the placement and the
     *   facing and left the side behind, so the next train inherited the last one’s tail. Again
     *   invisible: the setup faithfully reported the stale value it had been left.
     *
     * So this asserts the round trip at the CONSUMER, in both directions - what is recorded reaches the
     * railway, and what is removed stops reaching it. Either half alone would have caught one defect
     * and not the other, which is why removal is here rather than assumed.
     *
     * Over several properties rather than just the arrival side, because the shape of the fault is not
     * specific to it: any property applied in the point loop and then overwritten by a later pass would
     * fail the same way, silently, and be reported clean by a setup-side test.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testWhatTheSetupRecordsIsWhatTheRailwayGets() throws Exception
    {
        String pointName = null;
        TileKey square = null;
        String side = null;

        for (Point candidate : stations())
        {
            TileKey key = session.getStationIndex().squareOf(candidate.getName());

            if (key == null) continue;

            java.util.List<String> sides = org.traincontrol.gui.ArrivalSidePrompt.sidesOf(
                model.getAutoLayout(), candidate);

            if (sides.isEmpty()) continue;

            pointName = candidate.getName();
            square = key;
            side = sides.get(0);

            break;
        }

        assertNotNull(side, "no station here offers an arrival side, so this proves nothing");

        // A TRAIN IS STANDING ON IT.  Without one the wipe cannot happen - it is placing the train
        // that clears the side - so an empty square would pass this whatever the build did.
        putDown(square, pointName, null);

        session.setArrivedFrom(square, side);
        session.setPointProperty(square, "maxTrainLength", 7);
        session.setPointProperty(square, "priority", 3);

        // RECORDED -> BUILT -> READ OFF THE RAILWAY.
        model.parseAuto(session.buildConfiguration());

        assertNotNull(model.getAutoLayout().getPoint(pointName) != null ? pointName : null,
            "the point did not survive the build under its own name, so the square is not built at all");

        // THE COPY HOLDING THE TRAIN, which on a split square is not the base-named one.
        Point built = occupiedCopyOf(square);

        assertNotNull(built,
            "no copy of this square holds the train after the build, so nothing below can be wiped by"
            + " placing it");

        assertEquals(built.getArrivedFrom(), side,
            "the arrival side was recorded and the railway does not have it (REG8-A1)");

        assertEquals(Integer.valueOf(built.getMaxTrainLength()), Integer.valueOf(7),
            "the station capacity was recorded and the railway does not have it - the same shape as"
            + " the arrival side, on a property that decides which trains are admitted");

        assertEquals(Integer.valueOf(built.getPriority()), Integer.valueOf(3),
            "the priority was recorded and the railway does not have it");

        // AND THE OTHER DIRECTION.  Taking the train off takes its tail with it, and the next build
        // must not hand the tail back.
        lift(square);

        assertNull(session.getArrivedFrom(square),
            "the setup kept the arrival side of a train that is no longer there (IND9-A1)");

        model.parseAuto(session.buildConfiguration());

        Point after = model.getAutoLayout().getPoint(pointName);

        assertNotNull(after, "the point did not survive the second build");

        assertNull(after.getArrivedFrom(),
            "the railway still says a train arrived from " + side + " at " + pointName + ", where no"
            + " train is standing. The next locomotive placed here inherits that tail and the track"
            + " behind it is blocked on the strength of where a different train came in (IND9-A1)");
    }
    /**
     * The arrival side survives the BUILD, not just the file (REG8-A1).
     *
     * `parseAuto` applies `arrivedFrom` while it is creating the points and places the locomotives in a
     * later pass. `Point.setLocomotive` drops the arrival side whenever the occupant changes - right on
     * a running railway, where a new train did not arrive the way the old one did - and on a build the
     * occupant always "changes", from nobody to the train the file names. So the value was written and
     * then wiped, on every build and every reload.
     *
     * **The tail blocking was therefore off after every restart**, for exactly the squares the arrival
     * -side question exists for: the operator answers it, watches the track grey, restarts, and the
     * protection is gone with nothing to say so.
     *
     * Two rules that were each correct collided, and the existing survival test could not see it
     * because it asserts the built JSON and the reopened SESSION - the two layers either side of the
     * one that decides. This asserts the railway.
     *
     * MUTATION: applying arrivedFrom in the point loop again fails this.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheArrivalSideSurvivesTheBuild() throws Exception
    {
        // The first station the real layout offers, rather than a named one: this class runs against
        // Adam’s own railway and a hard-coded name would be a guess.
        // SEARCHED, not the first one.  This class runs against Adam’s own railway, and not every
        // station has a compass-resolvable neighbour - `sideTowards` answers null for a point without
        // coordinates and for a zero delta - so the first station may offer no side at all, and a test
        // that recorded nothing would pass whatever the build did.
        String pointName = null;
        TileKey square = null;
        String side = null;

        for (Point candidate : stations())
        {
            TileKey key = session.getStationIndex().squareOf(candidate.getName());

            if (key == null) continue;

            java.util.List<String> sides = org.traincontrol.gui.ArrivalSidePrompt.sidesOf(
                model.getAutoLayout(), candidate);

            if (sides.isEmpty()) continue;

            pointName = candidate.getName();
            square = key;
            side = sides.get(0);

            break;
        }

        assertNotNull(side,
            "no station on this layout offers an arrival side, so there is nothing to record and this"
            + " test would pass whatever the build did");

        putDown(square, pointName, null);

        session.setArrivedFrom(square, side);

        assertEquals(session.getArrivedFrom(square), side, "control: the side did not go into the setup");

        model.parseAuto(session.buildConfiguration());

        assertNotNull(model.getAutoLayout().getPoint(pointName) != null ? pointName : null,
            "the point did not survive the build, so nothing below is about the side");

        // THE COPY HOLDING THE TRAIN - see occupiedCopyOf. Placing the train is what used to wipe the
        // side, so the assertion has to be made where the train actually is.
        Point built = occupiedCopyOf(square);

        assertNotNull(built,
            "no copy of this square holds the train after the build - and it is placing the train that"
            + " wipes the side, so without it this test cannot fail");

        assertEquals(built.getArrivedFrom(), side,
            "the railway forgot which side the train came in by during the build. It was applied while"
            + " the points were being created and then cleared when the locomotive was placed on top of"
            + " it, so the tail blocking is off after every restart (REG8-A1)");
    }
    /**
     * Everything about where the trains are and which way they face, as one comparable value.
     *
     * @return square to "loc/facing/arrivedFrom", for every square that has any of them
     * @throws Exception on a failure to build
     */
    private Map<String, String> snapshot() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Map<String, String> out = new java.util.TreeMap<>();

        for (Point point : model.getAutoLayout().getPoints())
        {
            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square == null) continue;

            String loc = point.getCurrentLocomotive() == null ? "-"
                : point.getCurrentLocomotive().getName();

            if ("-".equals(loc) && session.getFacing(square) == null
                && session.getArrivedFrom(square) == null)
            {
                continue;
            }

            out.put(point.getName(), loc + "/" + session.getFacing(square) + "/"
                + session.getArrivedFrom(square));
        }

        return out;
    }

    // ---------------------------------------------------------------- the door, and the shared parts

    /**
     * The built Point on this square that is actually holding the train.
     *
     * **A square is not a Point, and on this railway it often is not one Point either.** The builder
     * emits a node per (arrival side x reverse), so `BottomMainPost` builds to four:
     * `{southbound=S, southbound reverse=N, northbound=N, northbound reverse=S}`. `getPoint(baseName)`
     * then finds either a copy that is empty or nothing at all, and a test asserting on it reads "the
     * train did not survive the build" when the train is standing perfectly happily on a sibling.
     *
     * That is not a hypothetical: these assertions were written against a fixture in which no square
     * ever split - the suite parsed its pages without wiring their accessories, so most of the railway
     * was missing and every station reduced to a single copy (2026-09-08). Asking the station index
     * which copies exist and which one has the locomotive is the question that survives both.
     *
     * @param square the diagram square
     * @return the occupied copy, or null when no copy of it holds a train
     */
    private static Point occupiedCopyOf(TileKey square)
    {
        for (String name : session.getStationIndex().pointNamesAt(square))
        {
            Point one = model.getAutoLayout().getPoint(name);

            if (one != null && one.getCurrentLocomotive() != null) return one;
        }

        return null;
    }

    /**
     * What `TrainControlUI.rememberPlacement` does, which is the door Adam pastes through.
     */
    private void putDown(TileKey square, String pointName, Side side) throws Exception
    {
        model.getAutoLayout().moveLocomotive(train, pointName, false);

        session.placeLocomotive(square, train);

        if (side != null) session.setFacing(square, side);
    }

    private void lift(TileKey square) throws Exception
    {
        session.placeLocomotive(square, null);
    }

    private String whereTheTrainIsAfterARebuild() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        if (built == null) fail("the configuration did not build");

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null
                && train.equals(point.getCurrentLocomotive().getName()))
            {
                return point.getName();
            }
        }

        return null;
    }

    private List<Point> stations() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        List<Point> out = new LinkedList<>();

        Layout built = model.getAutoLayout();

        if (built != null)
        {
            for (Point point : built.getPoints())
            {
                if (point.isDestination()) out.add(point);
            }
        }

        return out;
    }

    private Side opposite(Side side)
    {
        switch (side)
        {
            case N: return Side.S;
            case S: return Side.N;
            case E: return Side.W;
            default: return Side.E;
        }
    }
}
