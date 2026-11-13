package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.Display;
import support.Reflect;
import support.Teardown;

/**
 * One-Way Run and Name Everything are on the autonomy editor's Bulk Tools menu, not in its column (OB-217): "move 'one
 * way run' and 'name everything' into the bulk tools menu, available only in the autonomy editor itself (not track
 * diagram)."
 *
 * Both halves are asked of the real panel: the menu through `buildBulkMenuForTest` (the same `bulkTools()` the
 * right-click menu calls), and the column as the panel's own component tree.
 *
 * Not asserted: the track diagram's menus leave them out, as that host serves the same panel with no page; `bulkTools`
 * keys the two items on `page != null` in one place.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
 *
 * MUTATION: delete the `if (page != null)` block at the top of `bulkTools` and the first claim fails;
 * put `panel.add(row(oneWayButton))` back in the column and the second does.
 *
 * @author Adam
 */
public class testBulkToolsHoldsTheWholeLayoutTools
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
        Display.needed("the autonomy editor and its menus need a display");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration, so there is no bulk tool to open");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null) throw new AssertionError("no page " + PAGE);

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
        Teardown.close(sandbox, model, editor, ui);
    }

    /** The autonomy editor's Bulk Tools menu offers One-Way Run and Name Everything. */
    @Test
    public void testTheEditorsBulkToolsOffersBoth() throws Exception
    {
        final List<String> labels = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu bulk = editor.getAutonomyPanel().buildBulkMenuForTest();

            for (int i = 0; i < bulk.getItemCount(); i++)
            {
                javax.swing.JMenuItem item = bulk.getItem(i);

                if (item != null && item.getText() != null) labels.add(item.getText());
            }
        });

        assertTrue(labels.size() > 0, "precondition: the Bulk Tools menu came back empty");

        assertTrue(labels.contains(I18n.t("autosetup.ui.toolOneWay")),
            "the autonomy editor's Bulk Tools menu has no One-Way Run: " + labels + ". Adam, OB-217:"
            + " \"move 'one way run' and 'name everything' into the bulk tools menu\"");

        assertTrue(labels.contains(I18n.t("autosetup.ui.btnNameEverything")),
            "the autonomy editor's Bulk Tools menu has no Name Everything: " + labels);
    }

    /**
     * The Bulk Tools items stand in four groups behind dividers: the map's paths and names, the lengths (assigned, then
     * cleared), where the trains stand, and whether they run.
     *
     * MUTATION: put the items back in one run of nine behind one divider, in `AutonomyEditorPanel.bulkTools`, and this
     * fails.
     */
    @Test
    public void testTheBulkToolsItemsAreGroupedBehindDividers() throws Exception
    {
        final List<List<String>> groups = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            List<String> group = new ArrayList<>();

            for (java.awt.Component part : editor.getAutonomyPanel().buildBulkMenuForTest().getMenuComponents())
            {
                if (part instanceof javax.swing.JPopupMenu.Separator)
                {
                    groups.add(group);

                    group = new ArrayList<>();
                }
                else if (part instanceof javax.swing.JMenuItem) group.add(((javax.swing.JMenuItem) part).getText());
            }

            groups.add(group);
        });

        String[][] expected = {
            {"autosetup.ui.toolOneWay", "autosetup.ui.btnNameEverything", "autolayout.ui.menuAllowEveryPath"},
            {"autosetup.ui.menuMassAssignLengths", "autosetup.ui.menuMassAssignMaxTrainLengths",
                "autosetup.ui.menuMassAssignTrainLengthsCounted", "autolayout.ui.menuClearAllTrackLengths",
                "autolayout.ui.menuClearAllMaxTrainLengths"},
            {"autolayout.ui.menuHomeEveryTrainWhereItStands", "autolayout.ui.menuPlaceEveryTrainAtHome",
                "autolayout.ui.menuClearAllHomeLocomotives", "autolayout.ui.menuClearLocomotives"},
            {"autosetup.ui.menuActivateAllTrains", "autosetup.ui.menuDeactivateAllTrains"}};

        assertEquals(groups.size(), expected.length, "the Bulk Tools items are not in " + expected.length + " groups: "
            + groups);

        for (int g = 0; g < expected.length; g++)
        {
            assertEquals(groups.get(g).size(), expected[g].length, "group " + (g + 1) + " is not " + expected[g].length
                + " items: " + groups);

            for (int i = 0; i < expected[g].length; i++)
            {
                // The label with its count where it has one
                String[] around = I18n.f(expected[g][i], "@@").split("@@", -1);
                String text = groups.get(g).get(i);

                assertTrue(text.startsWith(around[0]) && text.endsWith(around[around.length - 1]), "group " + (g + 1)
                    + " item " + (i + 1) + " is \"" + text + "\", not " + expected[g][i] + ": " + groups);
            }
        }
    }

    /** And the column no longer carries them as buttons. */
    @Test
    public void testTheColumnNoLongerCarriesThem() throws Exception
    {
        final Object[] parents = new Object[2];

        SwingUtilities.invokeAndWait(() ->
        {
            AutonomyEditorPanel panel = editor.getAutonomyPanel();

            parents[0] = parentOf(panel, "oneWayButton");
            parents[1] = parentOf(panel, "nameAll");
        });

        assertNull(parents[0],
            "One-Way Run is still mounted in the editor's column as well as on the menu - Adam, OB-217,"
            + " asked for it to be moved");

        assertNull(parents[1],
            "Name Everything is still mounted in the editor's column as well as on the menu");
    }

    /**
     * An empty square's menu has Bulk Tools too (MT-474): "when right clicking an empty square, show the bulk tools
     * menu option."  An empty square opens the station-label menu, built through `buildTileMenu`, the door the
     * right-click itself calls.
     *
     * MUTATION: take `bulkTools()` back off `buildTextMenu` and this fails.
     */
    @Test
    public void testAnEmptySquaresMenuHasBulkTools() throws Exception
    {
        final List<String> labels = new ArrayList<>();
        final String[] square = new String[1];

        SwingUtilities.invokeAndWait(() ->
        {
            LayoutDiagram page = model.getLayout(PAGE);

            org.traincontrol.automationui.TileGraph.TileKey empty = null;

            for (int x = 0; x < 40 && empty == null; x++)
            {
                for (int y = 0; y < 40 && empty == null; y++)
                {
                    if (page.getComponent(x, y) == null) empty = new org.traincontrol.automationui.TileGraph.TileKey(PAGE, x, y);
                }
            }

            if (empty == null) return;

            square[0] = empty.toString();

            javax.swing.JPopupMenu menu = editor.getAutonomyPanel().buildTileMenu(empty, null);

            if (menu == null) return;

            for (java.awt.Component item : menu.getComponents())
            {
                if (item instanceof javax.swing.JMenuItem) labels.add(((javax.swing.JMenuItem) item).getText());
            }
        });

        assertTrue(square[0] != null, "precondition: " + PAGE + " has no empty square in its first forty columns");

        assertTrue(labels.contains(I18n.t("autosetup.ui.menuShowStationHere")), "precondition: the menu of the empty"
            + " square " + square[0] + " is not the station-label menu an empty square opens: " + labels);

        assertTrue(labels.contains(I18n.t("autosetup.ui.menuBulkTools")), "right-clicking the empty square " + square[0]
            + " offers no Bulk Tools: " + labels + ".  Adam, MT-474: \"when right clicking an empty square, show the bulk"
            + " tools menu option.\"");
    }

    /** The Swing parent of one of the panel's own button fields, read by reflection. */
    private static Object parentOf(AutonomyEditorPanel panel, String field)
    {
        try
        {
            java.awt.Component button = (java.awt.Component) Reflect.get(AutonomyEditorPanel.class, panel, field);

            assertTrue(button != null, "precondition: the panel built no " + field);

            return button.getParent();
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the panel has no field " + field, e);
        }
    }
}
