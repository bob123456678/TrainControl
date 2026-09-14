package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * One-Way Run and Name Everything are on the autonomy editor's Bulk Tools menu, not in its column.
 *
 * Adam, OB-217, 2026-09-13: *"move 'one way run' and 'name everything' into the bulk tools menu,
 * available only in the autonomy editor itself (not track diagram).  update 'path type' to use the blue
 * label style, and move it below 'why not moving' as it belongs.  Also, add a 'page settings' label
 * above 'exclude page'."*
 *
 * **Both halves are asked of the real panel:** the menu is built through `buildBulkMenuForTest`, which
 * calls the same `bulkTools()` the right-click menu does, and the column is the panel's own component
 * tree.  A move that added the items and left the buttons would pass the first claim and fail the
 * second; one that removed the buttons and forgot the menu fails the first.
 *
 * **Not asserted: the track diagram's menus leave them out.**  That host serves the same panel with no
 * page, and building one needs the whole main window's diagram tab; `bulkTools` keys the two items on
 * `page != null` in one place, read with the code.
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
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor and its menus need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no bulk tool to open");
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
     * The autonomy editor's Bulk Tools menu offers One-Way Run and Name Everything.
     *
     * @throws Exception from the event thread
     */
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
     * And the column no longer carries them as buttons.
     *
     * @throws Exception from the event thread
     */
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

    /** The Swing parent of one of the panel's own button fields, read by reflection. */
    private static Object parentOf(AutonomyEditorPanel panel, String field)
    {
        try
        {
            java.lang.reflect.Field f = AutonomyEditorPanel.class.getDeclaredField(field);

            f.setAccessible(true);

            java.awt.Component button = (java.awt.Component) f.get(panel);

            assertTrue(button != null, "precondition: the panel built no " + field);

            return button.getParent();
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the panel has no field " + field, e);
        }
    }
}
