package ui;

import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Unavailable While Occupied is answered with stations, by the list and by a click alike.
 *
 * Adam, MT-376, 2026-09-13, twice: *"works, but exclude non-station points from the list entirely"*, and
 * then *"if there are no stations on the diagram yet, the 'choose on diagram' button should be greyed out
 * to match the message."*
 *
 * **The two are one rule at two doors.**  The list became stations only; the click on the diagram went on
 * accepting any square autonomy routes over.  A greyed button beside "nothing to choose" is only true if
 * the click it stands for would find nothing either - so the click now refuses what the list leaves out,
 * with the list's own exception for an entry already stored.
 *
 * On the frozen railway (OB-111).  The dialog itself is modal and is not opened; what it shows is built by
 * `nothingToPickButton`, and the rule a click is judged by is `whyNotABlocker`, both asked directly.
 *
 * MUTATION: delete the station clause from `whyNotABlocker` and the first claim fails.
 *
 * @author Adam
 */
public class testOnlyAStationHoldsAnotherBack
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        editor = built[0];
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (editor != null)
            {
                final LayoutEditor closing = editor;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A click on a square that is not a station is refused; a click on another station is not.
     */
    @Test
    public void testAClickRefusesWhatTheListLeavesOut()
    {
        TileKey station = null, otherStation = null, plain = null;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (session.getStore().isStation(tile))
            {
                if (station == null) station = tile;
                else if (otherStation == null && !station.equals(session.getCaptionTarget(tile))) otherStation = tile;
            }
            else if (plain == null)
            {
                plain = tile;
            }
        }

        if (station == null || otherStation == null || plain == null)
        {
            throw new SkipException("the frozen railway has no two stations and a plain sensor");
        }

        assertFalse(session.getStore().getBlockingPoints(station).contains(plain),
            "precondition: the plain sensor is already stored against the station, which is the exception");

        AutonomyEditorPanel panel = editor.getAutonomyPanel();

        assertNull(panel.whyNotABlockerForTest(station, otherStation),
            "precondition: a click on another station is refused, so the rule refuses everything and the"
            + " claim below proves nothing");

        assertNotNull(panel.whyNotABlockerForTest(station, plain),
            "a click on " + plain + ", which is not a station, was accepted as holding " + station + " back."
            + " The list offers stations only - Adam, MT-376: \"exclude non-station points from the list"
            + " entirely\" - so the click is a second door onto the same setting that lets through what"
            + " the first refuses");
    }

    /**
     * With nothing to list, the Pick on the Diagram button is shown greyed.
     */
    @Test
    public void testThePickButtonIsGreyedWhenThereIsNothingToPick()
    {
        javax.swing.JButton button = AutonomyEditorPanel.nothingToPickButton();

        assertEquals(button.getText(), I18n.t("autosetup.ui.optionPickBlockerOnDiagram"),
            "precondition: the button does not carry the option's words, so it is not the one shown");

        assertFalse(button.isEnabled(),
            "the Pick on the Diagram button beside \"nothing to choose\" is enabled. Adam, MT-376: \"the"
            + " 'choose on diagram' button should be greyed out to match the message\"");
    }
}
