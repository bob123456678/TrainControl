package core;

import java.util.LinkedHashSet;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * A station whose track runs out is drawn as a terminus whatever its turning setting (FR-118), and where the setting
 * reads otherwise its Changing Direction menu says which way the track runs out (OB-356).
 *
 * Adam, 2026-10-17: *"ST60rear is shown as a square (terminus) even though it is configured as a normal stations with
 * trains not turning around there."*  The track beyond that station stops on the next square, so no train can go
 * through it: the drawing was right, and nothing said why.
 *
 * On `single-switch`: `WestEnd` (1,3) is against a buffer stop to the west and is set here so trains never turn there,
 * `Approach` (3,3) has track both ways, and `MainPlatform` (7,3) is against a buffer stop to the east and set so trains
 * must turn there.
 *
 * @author Adam
 */
public class testADeadEndSaysWhyItIsATerminus
{
    private static support.Scenario scenario;

    private static AutonomySession session;

    private static TileKey westEnd, approach, mainPlatform;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("single-switch");

        scenario.getModel().stop();

        session = scenario.getSession();

        westEnd = scenario.tile(1, 3);
        approach = scenario.tile(3, 3);
        mainPlatform = scenario.tile(7, 3);

        // WestEnd set as the reported station was: trains never change direction there
        session.setPointProperty(westEnd, AutonomyBuilder.MUST_REVERSE, null);

        assertFalse(session.isTurnAround(westEnd), "precondition: WestEnd still turns trains round");
        assertTrue(session.isMustTurnAround(mainPlatform), "precondition: MainPlatform is not a compulsory turn");
        assertFalse(session.isTurnAround(approach), "precondition: Approach turns trains round");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    /**
     * The drawing: a station with track both ways is the ordinary station, and one whose track runs out is a terminus
     * facing that way - whether or not trains are set to turn there.
     */
    @Test
    public void testOnlyAStationWhoseTrackRunsOutIsDrawnAsATerminus()
    {
        assertNull(session.staticAnnotationFor(approach).getBadge().getDeadEnd(), "Approach, with track both ways, is"
            + " drawn as a terminus");
        assertFalse(session.staticAnnotationFor(approach).getBadge().isTerminus(), "Approach, set so trains never turn"
            + " there, is drawn as a terminus");

        assertEquals(session.staticAnnotationFor(mainPlatform).getBadge().getDeadEnd(), Side.E, "MainPlatform, against"
            + " its buffer stop to the east, is not drawn as a terminus facing it");

        assertEquals(session.staticAnnotationFor(westEnd).getBadge().getDeadEnd(), Side.W, "WestEnd, whose track runs"
            + " out to the west, is not drawn as a terminus because trains are set not to turn there - but no train can"
            + " go through it (FR-118)");
    }

    /**
     * The reason: the dead end set so trains never turn says on its Changing Direction menu which way its track runs
     * out; the station with track both ways and the dead end already set to turn say nothing.
     *
     * MUTATION: drop the note from `AutonomyEditorPanel.buildTileMenu`, and the first assertion fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testTheMenuSaysWhichWayTheTrackRunsOut() throws Exception
    {
        String west = I18n.f("autosetup.ui.menuTurnDeadEnd", I18n.t("autosetup.ui.sideW"));
        String east = I18n.f("autosetup.ui.menuTurnDeadEnd", I18n.t("autosetup.ui.sideE"));

        org.traincontrol.gui.AutonomyEditorPanel panel = panel();

        assertTrue(turningMenu(panel, westEnd).contains(west), "WestEnd's Changing Direction menu does not say its"
            + " track runs out to the west, which is why it is drawn as a terminus: " + turningMenu(panel, westEnd));

        Set<String> through = turningMenu(panel, approach);

        assertFalse(through.contains(west) || through.contains(east), "Approach, with track both ways, says its"
            + " track runs out: " + through);

        assertFalse(turningMenu(panel, mainPlatform).contains(east), "MainPlatform, set so every train turns there,"
            + " explains a terminus its own setting already says it is");
    }

    private static org.traincontrol.gui.AutonomyEditorPanel panel() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session,
            scenario.getPages().get(0).getName(), () -> { }));

        return panel[0];
    }

    /** The text of every item on a square's Changing Direction menu. */
    private static Set<String> turningMenu(final org.traincontrol.gui.AutonomyEditorPanel panel, final TileKey square)
        throws Exception
    {
        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> menu[0] = panel.buildTileMenu(square, null));

        javax.swing.JMenu turning = null;

        for (java.awt.Component item : menu[0].getComponents())
        {
            if (item instanceof javax.swing.JMenu
                && I18n.t("autosetup.ui.menuTurningGroup").equals(((javax.swing.JMenu) item).getText()))
            {
                turning = (javax.swing.JMenu) item;
            }
        }

        assertNotNull(turning, "no Changing Direction menu on " + square);

        Set<String> texts = new LinkedHashSet<>();

        for (java.awt.Component item : turning.getMenuComponents())
        {
            if (item instanceof javax.swing.JMenuItem) texts.add(((javax.swing.JMenuItem) item).getText());
        }

        return texts;
    }
}
