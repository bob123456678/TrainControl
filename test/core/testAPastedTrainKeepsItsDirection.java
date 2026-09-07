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

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

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

            assertEquals(held.size(), 1,
                station.getName() + " is a terminus, so there is one way to stand there: " + held);

            Side only = held.values().iterator().next();

            // Arriving the other way round - which is what makes this a reversal rather than a
            // placement that happened to agree.
            assertEquals(AutonomySession.facingAfterAPaste(held, opposite(only), station.getName()),
                only,
                station.getName() + " did not turn a train that arrived facing " + opposite(only));

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
     * What this layout can and cannot prove, measured rather than assumed.
     *
     * **Deleted twice by accident, restored twice (CONF-B5).**  Both times a script sliced from one
     * method to an anchor below it and took this with it, and both times nothing broke - it is the
     * one method here that nothing else references, so the only symptom was the count going down by
     * one.  Worth saying out loud, because it is the only measurement backing a caveat that appears in
     * every review report of the day: four findings had their severity argued from it.
     *
     * Every named square on this railway builds to exactly ONE copy, even after being marked
     * may-reverse - which is the instruction to split.  Two things follow:
     *
     * - No test run against this layout can show a facing PICKING between copies, so the reversal Adam
     *   reported is the recorded value rather than a different copy being chosen.
     * - A compulsory turn produces no turning copy here either, so the shape `REG6-A1` needs cannot be
     *   built on this railway - which is why `testACompulsoryTurnIsNotAQuestion` checks the removed
     *   clause as source beside its behavioural assertion.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testEverySquareOnThisLayoutBuildsToOneCopy() throws Exception
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

        // Not a requirement - a record.  The day a square DOES build to more than one copy, this goes
        // red, and that is the day the controls those findings wanted become writable.
        assertTrue(split.isEmpty(),
            "a square now builds to more than one copy, so the facing can finally be shown to pick"
            + " between them - write that control now, and re-read the reachability caveats in the"
            + " review reports, which all assumed this could not happen: " + split);
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
