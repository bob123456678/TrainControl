package ui;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
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
import org.traincontrol.gui.autonomy.ArrivalSidePrompt;
import org.traincontrol.gui.autonomy.FacingPrompt;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train pasted onto a may-reverse square faces the way the operator said, on the railway itself.
 *
 * Adam, MT-394: "Pasting 75 407 DB on BottomMainB makes it face east regardless of the user's choice.  Check how this
 * passed our internal tests."  The claim written for the paste read the SOURCE for the lines that write the answer into
 * the setup; nothing asked where the train ended up.  The railway encodes a train's direction by which copy of a square
 * it stands on, and the paste had already chosen that copy before asking (`getAutonomyPointForTile`: "with none
 * [occupied], any copy will do"), which on an empty BottomMainB is the east one.
 *
 * So this drives a real paste through the window's own key door, with the two questions answered by the test (a modal
 * dialog hung this suite once), and asks the running layout which copy the train stands on.  Asked both ways, so
 * whichever copy a broken paste lands on, one claim is about the other heading and fails.
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

        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new AssertionError("no autonomy setup on this railway");

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration did not build");

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (SQUARE_NAME.equals(session.getStore().getPointName(key))) square = key;
        }

        if (square == null) throw new AssertionError("this railway has no " + SQUARE_NAME);

        assertTrue(session.mayTurnTiles().contains(square), SQUARE_NAME + " is not a may-reverse square on this railway,"
            + " so no facing question is put there");

        Map<String, Side> copies = session.facingsFor(square);

        assertTrue(copies.containsValue(Side.E) && copies.containsValue(Side.W), SQUARE_NAME
            + " does not hold both east and west here: " + copies);

        train = model.getLocByName("75 407 DB");

        if (train == null && !model.getLocList().isEmpty())
        {
            train = model.getLocByName(model.getLocList().get(0));
        }

        if (train == null) throw new AssertionError("this railway has no locomotives");
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
     */
    @Test
    public void testChosenEastItFacesEast() throws Exception
    {
        assertEquals(pasteFacing(Side.E), Side.E,
            "a train pasted onto " + SQUARE_NAME + " was told to face EAST and is standing on a copy"
            + " facing another way, so the paste is not following the answer in either direction");
    }

    /**
     * A train pasted onto a square that is no station says it can only be placed at a station, and asks nothing first
     * (OB-365).  Through the user's own keys: Control+X over the square while it is a station puts the train on the
     * clipboard, and Control+V over it once it is none is refused.  Real dialogs, answered by
     * {@link support.Answering}, which keeps what each one said: the only thing asked is the notice.  The train stays
     * on the clipboard for the next square.
     *
     * MUTATION: take the station check out of the paste door in TrainControlUI and the questions are asked again, so
     * this fails.
     */
    @Test
    public void testPastingOnASquareThatIsNoStationSaysSoAndAsksNothing() throws Exception
    {
        clearTheSquareAndTheTrain();

        final Point standOn = session.getStationIndex().speakerAt(layout, square);

        assertNotNull(standOn, "precondition: " + SQUARE_NAME + " has no Point to stand the train on");

        SwingUtilities.invokeAndWait(() -> layout.moveLocomotive(train.getName(), standOn.getName(), false));

        try (support.Answering answering = support.Answering.start(message -> null))
        {
            pressOver(square, KeyEvent.VK_X);
        }

        for (Point point : layout.getPoints())
        {
            assertTrue(point.getCurrentLocomotive() != train, "precondition: Control+X over " + SQUARE_NAME
                + " did not take the train off " + point.getName());
        }

        // The may-reverse square demoted: it keeps its turning copies and is no station, which is the square that
        // could be asked both questions and then refused.
        session.setStation(square, false);

        try
        {
            rebuild();

            assertTrue(session.mayTurnTiles().contains(square), "precondition: " + SQUARE_NAME + " is no longer a"
                + " may-reverse square once it is no station, so no question is put there");

            Point speaker = session.getStationIndex().speakerAt(layout, square);

            assertNotNull(speaker, "precondition: " + SQUARE_NAME + " has no Point once it is no station");

            assertTrue(ArrivalSidePrompt.wouldAsk(layout, speaker, true, session.unbarredArrivalSides(square))
                || FacingPrompt.wouldAsk(session.facingsFor(square).values()), "precondition: no question is put at "
                + SQUARE_NAME + " once it is no station, so this claim would assert nothing");

            // The rebuild stands the snapshot's trains again, this one among them.
            clearTheSquareAndTheTrain();

            FacingPrompt.answerForTests(null);
            ArrivalSidePrompt.answerForTests(null);

            List<String> asked;

            try (support.Answering answering = support.Answering.start(message -> null))
            {
                pressOver(square, KeyEvent.VK_V);

                asked = answering.asked();
            }

            assertEquals(asked, java.util.Collections.singletonList(I18n.t("autolayout.ui.errorTrainOnlyAtStation")),
                "pasting a train onto " + SQUARE_NAME + ", which is no station, should show the one notice and ask"
                + " nothing (OB-365), but the dialogs shown were: " + asked);

            for (Point point : layout.getPoints())
            {
                assertTrue(point.getCurrentLocomotive() != train, "the train was put on " + point.getName()
                    + ", though " + SQUARE_NAME + " is no station");
            }

            Field cut = TrainControlUI.class.getDeclaredField("cutLocomotive");
            cut.setAccessible(true);

            assertSame(cut.get(ui), train, "the refused paste took the train off the clipboard");
        }
        finally
        {
            session.setStation(square, true);

            rebuild();
        }
    }

    /**
     * Builds the railway again from the setup, as the other claims here found it.
     */
    private static void rebuild() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        pump();
    }

    /**
     * The pointer enters the square on the main window's diagram and a key is pressed there with Control held, as the
     * keyboard sends it, in one event-thread turn so the real pointer cannot move the hover between the two.
     */
    private static void pressOver(TileKey target, int keyCode) throws Exception
    {
        LayoutLabel over = null;

        for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(target))
        {
            if (label.getComponent() != null) over = label;
        }

        assertNotNull(over, "the main window draws no square at " + target + " to point at");

        Field tab = TrainControlUI.class.getDeclaredField("KeyboardTab");
        tab.setAccessible(true);

        final Component keyboard = (Component) tab.get(ui);
        final LayoutLabel square = over;

        SwingUtilities.invokeAndWait(() ->
        {
            long now = System.currentTimeMillis();

            square.dispatchEvent(new MouseEvent(square, MouseEvent.MOUSE_ENTERED, now, 0, square.getWidth() / 2,
                square.getHeight() / 2, 0, false));

            keyboard.dispatchEvent(new KeyEvent(keyboard, KeyEvent.KEY_PRESSED, now, InputEvent.CTRL_DOWN_MASK, keyCode,
                KeyEvent.CHAR_UNDEFINED));
            keyboard.dispatchEvent(new KeyEvent(keyboard, KeyEvent.KEY_RELEASED, now + 1, InputEvent.CTRL_DOWN_MASK,
                keyCode, KeyEvent.CHAR_UNDEFINED));
        });

        pump();
    }

    /**
     * Both questions name the square the way the diagram does, not by the copy the paste happened to pick: a split
     * square's Points are named with a heading ("BottomMainB (eastbound)") and both questions are ABOUT direction, so
     * the name put an arbitrary answer in the question (on an empty square any copy will do).
     *
     * MUTATION: name the square by its Point again in either question and this fails.
     */
    @Test
    public void testBothQuestionsNameTheSquare() throws Exception
    {
        pasteFacing(Side.W);

        assertEquals(FacingPrompt.lastAskedAboutForTests(), SQUARE_NAME, "the facing question named the square"
            + " by one of its copies, whose name states a direction the operator is being asked about (GUI-C7)");

        // THE ARRIVAL-SIDE QUESTION, put where it is asked: this paste does not ask it (the train fits and the walk
        // answers), so it is asked here of one of the square's copies directly, as the paste and the right-click
        // Place door ask it.
        Point copy = null;

        for (Point point : layout.getPoints())
        {
            if (point.getName().startsWith(SQUARE_NAME + " (")) copy = point;
        }

        assertNotNull(copy, "precondition: " + SQUARE_NAME + " has no copy named with a heading, so there is nothing"
            + " for this claim to catch");

        // Two ways in, given as the build's sides are, so the question is really put: what this asks about is the
        // name it is put with, not which squares it is put at.
        List<Side> sides = java.util.Arrays.asList(Side.E, Side.W);

        assertTrue(ArrivalSidePrompt.wouldAsk(layout, copy, true, sides), "precondition: the arrival-side question"
            + " is not asked at " + copy.getName() + ", so this claim would assert nothing");

        ArrivalSidePrompt.forPlacement(layout, copy, null, true, null, sides);

        assertEquals(ArrivalSidePrompt.lastAskedAboutForTests(), SQUARE_NAME, "the arrival-side question named"
            + " the square as " + ArrivalSidePrompt.lastAskedAboutForTests() + ", one of its copies, whose name states"
            + " a direction the operator is being asked about (GUI-C7)");
    }

    /**
     * A train pasted onto a station at rest shows the locomotive a run draws there, facing the way it was put, and
     * taken off by the cut it shows none (Adam: "when a train is standing somewhere, can we show its locomotive icon on
     * top of the station in the track diagram viewer, while maintaining editability? same icon as when a run is
     * started").
     *
     * Through the window's own key doors.  Each writes the setup, and the rebuild that follows tells the diagram.
     *
     * MUTATION: leave the parked trains out of the monitor's picture, and this fails.
     */
    @Test
    public void testAPastedTrainShowsItsLocomotiveOnTheStation() throws Exception
    {
        final org.traincontrol.gui.autonomy.DiagramMonitorDriver driver = ui.getDiagramMonitorDriver();

        // THE DIAGRAM WATCHING THIS RAILWAY, whatever the overlay's tick box was left at
        SwingUtilities.invokeAndWait(() ->
        {
            driver.bind(session);
            driver.setEnabled(true);
            driver.start();
        });

        assertEquals(pasteFacing(Side.W), Side.W, "precondition: the paste did not stand the train facing west");

        org.traincontrol.automationui.TileOverlay shown = theMarkOn(true);

        assertTrue(shown != null && shown.isParked(), "a train pasted onto " + SQUARE_NAME + " at rest shows no"
            + " locomotive there (Adam, 2026-10-01): " + shown);

        assertEquals(shown.getFacing(), Side.W, "the locomotive on " + SQUARE_NAME + " does not face the way the"
            + " train was put");

        // AND CUT, by the same door's Control+X
        final Method gesture = TrainControlUI.class.getDeclaredMethod("locomotiveGestureOnDiagram", int.class,
            boolean.class);

        gesture.setAccessible(true);

        final Object[] handled = new Object[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui.setHoveredDiagramTile(square.getPage(), square.getX(), square.getY());

                handled[0] = gesture.invoke(ui, KeyEvent.VK_X, true);
            }
            catch (Exception refused)
            {
                handled[0] = refused;
            }
        });

        assertEquals(handled[0], Boolean.TRUE, "precondition: the cut door did not take Control+X over " + SQUARE_NAME
            + ": " + handled[0]);

        org.traincontrol.automationui.TileOverlay after = theMarkOn(false);

        assertTrue(after == null || !after.hasTrain(), "the locomotive stayed on " + SQUARE_NAME + " after the train"
            + " was cut from it: " + after);
    }

    /**
     * What the diagram shows on the square, once it shows a train there or not as asked - or what it shows after five
     * seconds of not.
     */
    private static org.traincontrol.automationui.TileOverlay theMarkOn(boolean aTrain) throws Exception
    {
        org.traincontrol.automationui.TileOverlay seen = null;

        for (int tries = 0; tries < 50; tries++)
        {
            pump();

            seen = ui.getDiagramTileRegistry().overlayAt(square);

            if ((seen != null && seen.hasTrain()) == aTrain) return seen;

            Thread.sleep(100);
        }

        return seen;
    }

    /**
     * Pastes the train onto the square with both questions answered, and says which way it now faces (null when it is
     * on none of the copies).
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
