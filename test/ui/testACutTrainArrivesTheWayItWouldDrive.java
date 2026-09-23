package ui;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.ArrivalSidePrompt;
import org.traincontrol.gui.FacingPrompt;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A cut train is pasted facing the way it would arrive if it drove there, and stands on that copy (Adam, OB-270).
 *
 * *"Trains should not inadvertently change direction when pasted, so a loc going west from bottomsecondary should
 * always face east when pasted on bottommaina."*  And, asked whether a cut train keeps the compass heading it was cut
 * with (MT-368) or takes the one it would have after driving there: *"it should be east.  no train should
 * inadvertently change direction when pasted."*
 *
 * **Two defects, one gesture.**  The paste kept the heading the train was CUT with - west - because a train off the
 * railway had nowhere for the walk to start; and it put the train on whichever copy of the square came first
 * (`getAutonomyPointForTile`, "any copy will do") while recording a heading, so the record and the copy could
 * disagree.  Now the walk starts from the square the train was cut from, and the train is put on the copy that faces
 * the heading recorded - both chosen over copies a train may stand on.
 *
 * **On the frozen railway with BottomMainA's east bar lifted**, which is how it stood at `2958fcf3`: Adam's follow-up
 * edit of the same day barred arrivals from the east there again, leaving it one placeable copy, and a square with one
 * copy has one answer however the paste reasons.  The claim needs two copies facing opposite ways, and this is the
 * square his report was about.
 *
 * Driven through the window's own key door: a real Control+X over BottomSecondary, then Control+V over BottomMainA.
 *
 * @author Adam
 */
public class testACutTrainArrivesTheWayItWouldDrive
{
    private static final String OUR_TRAIN = "cut and paste probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;

    private static TileKey mainA;
    private static TileKey secondary;

    private static Locomotive train;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the paste door belongs to a window, and a window needs a display");
        }

        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup on this railway");

        mainA = square("BottomMainA");
        secondary = square("BottomSecondary");

        // BOTTOMMAINA WITH BOTH PLAIN COPIES PLACEABLE AGAIN - see the class comment.
        SwingUtilities.invokeAndWait(() -> session.setBarredArrivals(mainA, java.util.Collections.<Side>emptySet()));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration did not build");

        // NOTHING OF HIS STANDING ABOUT, so the walk and the landing are about this class's train alone.
        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }

        train = model.newMM2Locomotive(OUR_TRAIN, 2395);

        assertNotNull(train, "could not create this class's train");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            FacingPrompt.answerForTests(null);
            ArrivalSidePrompt.answerForTests(null);
            TailCrossedPrompt.answerForTests(null);

            if (model != null) model.deleteLoc(OUR_TRAIN);
        }
        catch (Exception alreadyGone)
        {
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The fixture: BottomMainA has two copies a train may stand on, facing opposite ways.
     */
    @Test
    public void testBottomMainAHoldsBothHeadings()
    {
        Map<String, Side> placeable = placeableCopiesOf(mainA);

        assertTrue(placeable.containsValue(Side.E) && placeable.containsValue(Side.W),
            "BottomMainA's copies a train may stand on are " + placeable + " - with one heading there, the paste has"
            + " one answer whatever it reasons, and this class cannot fail");
    }

    /**
     * Cut going west at BottomSecondary, pasted on BottomMainA: it stands facing east, and the setup says east.
     *
     * MUTATION: dropping the walk from the cut square - the paste keeping the heading it was cut with - lands it
     * facing west; dropping the copy choice leaves it on whichever copy came first while the record says east.
     *
     * @throws Exception from the window
     */
    @Test(dependsOnMethods = "testBottomMainAHoldsBothHeadings")
    public void testACutTrainGoingWestLandsFacingEast() throws Exception
    {
        Point from = null;

        for (String copy : session.facingsFor(secondary).keySet())
        {
            Point point = layout.getPoint(copy);

            if (point != null && point.isDestination()) from = point;
        }

        assertNotNull(from, "no copy of BottomSecondary a train may stand on");

        final String fromName = from.getName();

        SwingUtilities.invokeAndWait(() -> layout.moveLocomotive(OUR_TRAIN, fromName, false));

        assertTrue(layout.getLocomotiveLocation(train) != null, "the train could not be put on BottomSecondary");

        // CONTROL+X, through the door.
        assertEquals(gesture(secondary, KeyEvent.VK_X), Boolean.TRUE, "Control+X over BottomSecondary was not taken");

        assertNull(layout.getLocomotiveLocation(train), "Control+X left the train on the railway, so this is not a cut");

        // GOING WEST, as he described it: the heading the cut remembered.  Set here rather than read, because what
        // the claim is about is a train cut facing west - and west is also the heading BottomMainA's westbound copy
        // holds, which is exactly what let the cut heading through.
        Field cutFacing = TrainControlUI.class.getDeclaredField("cutFacing");
        cutFacing.setAccessible(true);
        cutFacing.set(ui, Side.W);

        // CONTROL+V, with every question answered so nothing waits for a person.
        FacingPrompt.answerForTests(null);

        List<Side> sides = session.unbarredArrivalSides(mainA);

        ArrivalSidePrompt.answerForTests(sides == null || sides.isEmpty() ? null : sides.get(0).name());

        TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);

        assertEquals(gesture(mainA, KeyEvent.VK_V), Boolean.TRUE, "Control+V over BottomMainA was not taken");

        Point standing = layout.getLocomotiveLocation(train);

        assertNotNull(standing, "the paste put the train nowhere");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.E,
            "a train cut going west at BottomSecondary stands on " + standing.getName() + " at BottomMainA.  Adam:"
            + " \"a loc going west from bottomsecondary should always face east when pasted on bottommaina\" - the route"
            + " loops round, and the heading it was cut with is not the one it would arrive with");

        assertEquals(session.getFacing(mainA), Side.E,
            "the setup records BottomMainA's facing as " + session.getFacing(mainA) + " while the train stands on "
            + standing.getName() + " - the record and the copy it stands on disagree");
    }

    /** Presses a key over a square, through the diagram's own gesture door. */
    private static Object gesture(TileKey over, int key) throws Exception
    {
        final Method door = TrainControlUI.class.getDeclaredMethod("locomotiveGestureOnDiagram", int.class,
            boolean.class);

        door.setAccessible(true);

        final Object[] handled = new Object[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui.setHoveredDiagramTile(over.getPage(), over.getX(), over.getY());

                handled[0] = door.invoke(ui, key, true);
            }
            catch (Exception refused)
            {
                handled[0] = refused;
            }
        });

        pump();

        return handled[0];
    }

    /** The copies of a square a train may stand on, with the way each faces. */
    private static Map<String, Side> placeableCopiesOf(TileKey square)
    {
        Map<String, Side> out = new LinkedHashMap<>();

        for (Map.Entry<String, Side> copy : session.facingsFor(square).entrySet())
        {
            Point point = layout.getPoint(copy.getKey());

            if (point != null && point.isDestination()) out.put(copy.getKey(), copy.getValue());
        }

        return out;
    }

    /** The square the setup gives this name. */
    private static TileKey square(String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new SkipException("this railway has no " + name);
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
