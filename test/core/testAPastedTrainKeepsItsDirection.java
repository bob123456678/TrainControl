package core;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
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
     * The door reads the heading BEFORE it moves the train, and hands it to that rule.
     *
     * `extracted-rule-moves-the-bug-to-the-call`: everything above tests the rule, and the rule was
     * never the hard part.  Both defects lived at the call - once by recording nothing, once by
     * recording the wrong thing - so the call is asserted as a call.
     *
     * MUTATION: move the `headingBeforeTheMove` assignment below `moveLocomotive` and it reads null,
     * which is the state that produced the shipped bug.
     */
    @Test
    public void testTheDoorReadsTheHeadingBeforeItMovesTheTrain() throws Exception
    {
        String door = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int read = door.indexOf("headingBeforeTheMove = getAutonomySession() == null");

        assertTrue(read > 0,
            "the door no longer reads the heading before moving the train. Read afterwards it is"
            + " always null, and the paste falls back to whichever copy the layout arbitrarily chose"
            + " - which is SPEC-A1, and the symptom Adam reported four times.");

        int moved = door.indexOf(
            "moveLocomotive(placing.getName(), point.getName(), false)", read);

        assertTrue(moved > read,
            "the heading is read after the move that clears it, so it is always null");

        assertTrue(door.contains("AutonomySession.facingAfterAPaste("),
            "the door no longer decides the facing through the rule every assertion above tests");
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
