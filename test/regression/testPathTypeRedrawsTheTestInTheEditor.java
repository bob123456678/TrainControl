package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
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
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Switching Path Type redraws the path test in the real autonomy editor (OB-225).
 *
 * Adam, on MT-434, 2026-09-15: *"Does not work.  Changes don't happen when switching, and in manual mode, I still get
 * reasons like 'tunnellongpark will never be chosen in autonomy'."*  The note is written only while Auto is selected,
 * so seeing it on Manual means the test was never run again.
 *
 * **Why the first claim missed it.**  `core.testManualOnlyPathsAreADifferentColour.testSwitchingPathTypeRedrawsTheTestedRoute`
 * drove `applyTest` on a panel with no page and no window, and compared the route against an empty substring.  This
 * class asks the editor Adam uses: a `LayoutEditor` in autonomy mode on the frozen snapshot, Test a Path pressed, the
 * two squares clicked through `tileClicked` - what the grid calls - and the radio clicked.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).  The station made manual-only is put back.
 *
 * @author Adam
 */
public class testPathTypeRedrawsTheTestInTheEditor
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration on the snapshot");
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
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
     * Test a Path to a station autonomy will never choose, then Manual: the note goes, and Auto brings it back.
     */
    @Test
    public void testSwitchingPathTypeRedrawsTheTestInTheEditor() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        assertNotNull(page, "precondition: no page " + PAGE);

        TileKey[] pair = aConnectedPairOfStations();

        assertNotNull(pair, "precondition: no two stations on " + PAGE + " with a route between them");

        final TileKey from = pair[0];
        final TileKey to = pair[1];

        org.json.JSONObject asFound = session.snapshotSetup();

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            session.setAutoDestination(to, false);

            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            settle();

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();

            // ARMED AS A PERSON ARMS IT, and the squares clicked through the door the grid uses.
            final javax.swing.AbstractButton test = (javax.swing.AbstractButton) field(panel, "testButton");

            SwingUtilities.invokeAndWait(() ->
            {
                test.doClick();
                panel.tileClicked(from, page.getComponent(from.getX(), from.getY()), false);
                panel.tileClicked(to, page.getComponent(to.getX(), to.getY()), false);
            });

            settle();

            String note = org.traincontrol.util.I18n.t("autosetup.ui.testNotAnAutoDestination");
            String noteStart = note.substring(0, note.indexOf("{0}"));

            String onAuto = shown(panel);

            assertTrue(onAuto.contains(noteStart),
                "precondition: on Auto the tested route to a station autonomy will never choose does not say so: " + onAuto);

            final javax.swing.AbstractButton manual = (javax.swing.AbstractButton) field(panel, "pathTypeManual");

            SwingUtilities.invokeAndWait(manual::doClick);

            settle();

            String onManual = shown(panel);

            assertFalse(onManual.contains(noteStart),
                "Path Type switched to Manual in the editor and the Auto verdict is still on screen - the test was not run"
                + " again.  Adam, MT-434: 'in manual mode, I still get reasons like tunnellongpark will never be chosen in"
                + " autonomy'.  It says: " + onManual);

            assertTrue(onManual.contains(describe(panel, to)),
                "Path Type switched to Manual and the tested route is no longer named on screen: " + onManual);

            final javax.swing.AbstractButton auto = (javax.swing.AbstractButton) field(panel, "pathTypeAuto");

            SwingUtilities.invokeAndWait(auto::doClick);

            settle();

            assertTrue(shown(panel).contains(noteStart),
                "switching back to Auto did not put the Auto verdict back: " + shown(panel));
        }
        finally
        {
            if (built[0] != null)
            {
                final LayoutEditor closing = built[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            session.restoreSetup(asFound);
        }
    }

    /** What the editor shows the operator: the hint line and the banner across the top, together. */
    private static String shown(AutonomyEditorPanel panel) throws Exception
    {
        StringBuilder out = new StringBuilder();

        Object hint = field(panel, "hint");

        if (hint instanceof javax.swing.JLabel && ((javax.swing.JLabel) hint).getText() != null)
        {
            out.append(((javax.swing.JLabel) hint).getText());
        }

        // THE BANNER KEEPS WHAT IT IS SAYING AS TEXT (AutonomyBanner.saying), not in child labels: every message the
        // panel says goes there when the editor has one, which Adam's editor does.
        Object banner = field(panel, "messageBanner");

        if (banner != null)
        {
            Object saying = field(banner, "saying");

            if (saying != null) out.append(" | ").append(saying);
        }

        return out.toString();
    }

    private static void collectText(java.awt.Container container, StringBuilder out)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JLabel && ((javax.swing.JLabel) child).getText() != null)
            {
                out.append(" | ").append(((javax.swing.JLabel) child).getText());
            }

            if (child instanceof java.awt.Container) collectText((java.awt.Container) child, out);
        }
    }

    private static String describe(AutonomyEditorPanel panel, TileKey tile) throws Exception
    {
        java.lang.reflect.Method describe = AutonomyEditorPanel.class.getDeclaredMethod("describeTile", TileKey.class);

        describe.setAccessible(true);

        String text = String.valueOf(describe.invoke(panel, tile));

        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** Two stations on the page, joined by a route the reducer finds, the second one autonomy may choose today. */
    private static TileKey[] aConnectedPairOfStations()
    {
        java.util.List<TileKey> stations = new java.util.ArrayList<>();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getStore().isStation(tile)
                && !session.manualOnlyStations().contains(tile))
            {
                stations.add(tile);
            }
        }

        for (TileKey a : stations)
        {
            for (TileKey b : stations)
            {
                if (a.equals(b)) continue;

                if (session.getReducer().findPath(a, b, session.mayTurnTiles(), session.mandatoryTurnTiles(),
                    session.barredArrivals(), session.shutTiles()) != null)
                {
                    return new TileKey[] { a, b };
                }
            }
        }

        return null;
    }

    private static Object field(Object on, String name) throws Exception
    {
        Class<?> type = on.getClass();

        while (type != null)
        {
            try
            {
                java.lang.reflect.Field f = type.getDeclaredField(name);

                f.setAccessible(true);

                return f.get(on);
            }
            catch (NoSuchFieldException notHere)
            {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
