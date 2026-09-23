package ui;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train pasted onto a may-reverse square faces the way the operator said, on the railway itself.
 *
 * Adam, MT-394, 2026-09-13: **"Pasting 75 407 DB on BottomMainB makes it face east regardless of the
 * user's choice.  Check how this passed our internal tests."**
 *
 * **How it passed.**  The paste asked the question and wrote the answer into the setup, and the claim
 * written for it read the SOURCE for the lines that do that - they were there.  Nothing asked where the
 * train ended up.  The railway encodes a train's direction by which copy of a square it stands on, and
 * the paste had already chosen that copy before asking anything: `getAutonomyPointForTile`, "with none
 * [occupied], any copy will do".  On an empty BottomMainB that is the east one.
 *
 * **So this drives a real paste**, through the window's own key door, with the two questions answered by
 * the test instead of by a dialog - a modal dialog in a test hung this suite once - and asks the running
 * layout which copy the train is standing on.  Asked both ways: whichever copy a broken paste lands on,
 * one of the two claims is about the other heading and fails.
 *
 * @author Adam
 */
public class testAPastedTrainFacesTheWayTheOperatorChose
{
    private static final String SQUARE_NAME = "BottomMainB";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;

    private static TileKey square;
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

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration did not build");

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (SQUARE_NAME.equals(session.getStore().getPointName(key))) square = key;
        }

        if (square == null) throw new SkipException("this railway has no " + SQUARE_NAME);

        if (!session.mayTurnTiles().contains(square))
        {
            throw new SkipException(SQUARE_NAME + " is not a may-reverse square on this railway, so no"
                + " facing question is put there");
        }

        Map<String, Side> copies = session.facingsFor(square);

        if (!copies.containsValue(Side.E) || !copies.containsValue(Side.W))
        {
            throw new SkipException(SQUARE_NAME + " does not hold both east and west here: " + copies);
        }

        train = model.getLocByName("75 407 DB");

        if (train == null && !model.getLocList().isEmpty())
        {
            train = model.getLocByName(model.getLocList().get(0));
        }

        if (train == null) throw new SkipException("this railway has no locomotives");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            // The dialogs come back, whatever happened above.
            FacingPrompt.answerForTests(null);
            ArrivalSidePrompt.answerForTests(null);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Chosen west, it stands facing west.
     *
     * @throws Exception from the window
     */
    @Test
    public void testChosenWestItFacesWest() throws Exception
    {
        assertEquals(pasteFacing(Side.W), Side.W,
            "a train pasted onto " + SQUARE_NAME + " was told to face WEST and is standing on a copy"
            + " facing another way. Adam, MT-394: \"makes it face east regardless of the user's choice\"."
            + " The answer reached the setup and not the railway, which records direction by which copy"
            + " of the square a train stands on.");
    }

    /**
     * And chosen east, it stands facing east - the control, and the other half of "regardless".
     *
     * @throws Exception from the window
     */
    @Test
    public void testChosenEastItFacesEast() throws Exception
    {
        assertEquals(pasteFacing(Side.E), Side.E,
            "a train pasted onto " + SQUARE_NAME + " was told to face EAST and is standing on a copy"
            + " facing another way, so the paste is not following the answer in either direction");
    }

    /**
     * Both questions name the square the way the diagram does, not by the copy the paste happened to pick (GUI-C7).
     *
     * A split square's Points are named with a heading - "BottomMainB (eastbound)" - and both questions asked here
     * are ABOUT direction, so the name put an answer in the question, and an arbitrary one: the Point is picked before
     * either answer, and on an empty square any copy will do.
     *
     * MUTATION: name the square by its Point again in either question and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testBothQuestionsNameTheSquare() throws Exception
    {
        pasteFacing(Side.W);

        assertEquals(FacingPrompt.lastAskedAboutForTests(), SQUARE_NAME, "the facing question named the square"
            + " by one of its copies, whose name states a direction the operator is being asked about (GUI-C7)");

        assertEquals(ArrivalSidePrompt.lastAskedAboutForTests(), SQUARE_NAME, "the arrival-side question named"
            + " the square by one of its copies, whose name states a direction the operator is being asked about"
            + " (GUI-C7)");
    }

    /**
     * Pastes the train onto the square with both questions answered, and says which way it now faces.
     *
     * @param chosen the heading the facing question is answered with
     * @return the heading of the copy the train is standing on, or null when it is on none of them
     * @throws Exception from the window
     */
    private static Side pasteFacing(Side chosen) throws Exception
    {
        clearTheSquareAndTheTrain();

        FacingPrompt.answerForTests(chosen);

        // The tail question comes first at a may-reverse square; any side the square offers will do,
        // because this class is about the heading, not about the tail.
        List<Side> sides = session.unbarredArrivalSides(square);

        ArrivalSidePrompt.answerForTests(sides == null || sides.isEmpty() ? null : sides.get(0).name());

        // WHAT CONTROL+X LEAVES BEHIND, set directly: the train on the clipboard and no remembered
        // heading, so the facing comes from the question and nothing else.
        Field cut = TrainControlUI.class.getDeclaredField("cutLocomotive");
        cut.setAccessible(true);

        Field cutFacing = TrainControlUI.class.getDeclaredField("cutFacing");
        cutFacing.setAccessible(true);

        final Method gesture = TrainControlUI.class.getDeclaredMethod(
            "locomotiveGestureOnDiagram", int.class, boolean.class);

        gesture.setAccessible(true);

        final Object[] handled = new Object[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                cut.set(ui, train);
                cutFacing.set(ui, null);

                ui.setHoveredDiagramTile(square.getPage(), square.getX(), square.getY());

                handled[0] = gesture.invoke(ui, KeyEvent.VK_V, true);
            }
            catch (Exception refused)
            {
                handled[0] = refused;
            }
        });

        pump();

        assertEquals(handled[0], Boolean.TRUE,
            "the diagram's paste door did not take Control+V over " + SQUARE_NAME + ": " + handled[0]);

        Point standing = null;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == train) standing = point;
        }

        assertNotNull(standing,
            "the paste over " + SQUARE_NAME + " put the train nowhere, so the door declined - and this"
            + " claim would otherwise report the heading of a train that was never placed");

        return session.facingsFor(square).get(standing.getName());
    }

    /** Takes the chosen train off wherever it is, and anything off the square, on the running layout. */
    private static void clearTheSquareAndTheTrain() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (Point point : layout.getPoints())
            {
                if (point.getCurrentLocomotive() == train) layout.moveLocomotive(null, point.getName(), true);
            }

            for (String copy : session.facingsFor(square).keySet())
            {
                Point point = layout.getPoint(copy);

                if (point != null && point.getCurrentLocomotive() != null)
                {
                    layout.moveLocomotive(null, copy, true);
                }
            }
        });

        pump();
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
