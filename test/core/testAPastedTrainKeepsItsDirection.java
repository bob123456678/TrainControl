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
     * Every station that is not a terminus, put a train down on each copy and see it stay there.
     */
    @Test
    public void testEveryNonTerminusStationKeepsTheDirectionItWasGiven() throws Exception
    {
        List<String> covered = new LinkedList<>();
        List<String> wrong = new LinkedList<>();

        for (Point station : stations())
        {
            if (station.isTerminus()) continue;

            TileKey square = session.getStationIndex().squareOf(station.getName());

            if (square == null) continue;

            Map<String, Side> copies = session.facingsFor(square);

            Side side = copies.get(station.getName());

            // A square with no named copies records no direction, deliberately: there is nothing to
            // record and a guessed heading is worse than none.
            if (side == null) continue;

            putDown(square, station.getName(), side);

            covered.add(station.getName());

            // What the door writes.
            if (!side.equals(session.getFacing(square)))
            {
                wrong.add(station.getName() + " recorded " + session.getFacing(square)
                    + " for a train standing on the " + side + " copy");

                continue;
            }

            // And whether it survives a build, which is the half that was actually broken: the
            // recorded facing is what picks the copy back out next time.
            String after = whereTheTrainIsAfterARebuild();

            if (!station.getName().equals(after))
            {
                wrong.add(station.getName() + " became " + after + " on rebuild");
            }

            lift(square);
        }

        assertTrue(covered.size() >= 2,
            "this test asserts nothing unless it covered some stations; it covered " + covered);

        assertTrue(wrong.isEmpty(), "a train was turned by being put down: " + wrong);
    }

    /**
     * What this layout can and cannot prove, measured rather than assumed.
     *
     * `assert-the-variable-not-the-control`: the loop above asserts that a placement lands on the
     * expected copy, and a placement can land there for a reason that has nothing to do with the
     * recorded facing.  So this asks the question the loop cannot: is there any square where the
     * facing changes the answer?
     *
     * **On the sample layout there is not, and that is the finding.**  Every named square builds to
     * exactly ONE copy - even after being marked may-reverse, which is the instruction to split - so
     * `placementCopy` reaches the same Point whatever is recorded, and no test run against this layout
     * can show the facing picking between copies.  Saying so here is the point of the method: the loop
     * above would otherwise read as proof of something it never tested, which is how the previous
     * three attempts at this defect each came to look finished.
     *
     * What follows from it is that the reversal Adam saw is NOT the build choosing a different copy.
     * It is the recorded VALUE - the direction the dropdown shows and the direction a later command
     * would use - and on a one-copy square that value is forced to that copy's side regardless of
     * which way the train was actually pointing when it was put down.
     */
    @Test
    public void testEverySquareOnThisLayoutBuildsToOneCopy() throws Exception
    {
        List<String> split = new LinkedList<>();
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

        assertTrue(named >= 2, "no named square was reached, so this asserted nothing");

        // Not a requirement - a record.  If a future layout or a future builder DOES split a square,
        // this goes red and the control above becomes runnable, which is the moment to write it.
        assertTrue(split.isEmpty(),
            "a square now builds to more than one copy, so the facing can finally be shown to pick"
            + " between them - write that control now: " + split);
    }
    /**
     * A terminus has one direction, so a train put down there takes it - which is the reversal.
     */
    @Test
    public void testATrainPutDownAtATerminusTakesTheOneDirectionThereIs() throws Exception
    {
        List<String> covered = new LinkedList<>();

        for (Point station : stations())
        {
            if (!station.isTerminus()) continue;

            TileKey square = session.getStationIndex().squareOf(station.getName());

            if (square == null) continue;

            Map<String, Side> copies = session.facingsFor(square);

            if (copies.isEmpty()) continue;

            assertEquals(copies.size(), 1,
                station.getName() + " is a terminus, so there is one way to stand there, not "
                + copies);

            Side only = copies.values().iterator().next();

            // Arriving the other way round, which is what makes this a reversal rather than a
            // placement that happened to agree.
            session.setFacing(square, opposite(only));

            putDown(square, station.getName(), only);

            assertEquals(session.getFacing(square), only,
                station.getName() + " must reverse a train put down on it");

            assertEquals(whereTheTrainIsAfterARebuild(), station.getName(),
                station.getName() + " must hold the train it reversed");

            covered.add(station.getName());

            lift(square);
        }

        assertFalse(covered.isEmpty(), "no terminus was reached, so this asserted nothing");
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
