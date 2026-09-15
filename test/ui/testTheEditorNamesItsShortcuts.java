package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
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
 * The autonomy editor's keyboard shortcuts are named where their actions are offered.
 *
 * Adam, OB-214, 2026-09-13: *"in the autonomy editor, Set Segment Length needs a tooltip that says
 * 'Control+E'.  change for other missing tooltip hints."*
 *
 * **Which keys were missing**, read off `LayoutEditor`'s key handler against every tooltip: Control+E
 * (Segment Length), Control+S (Rename), Control+H (Home) and Control+G (Track Lengths).  Control+L, D and K
 * were already on their toggles, and Control+B on Max Train Length since 2026-09-12.
 *
 * **Asked of the real menu**, through `buildTileMenu` - the method the right-click calls - on a station of
 * the frozen railway, and of the item that carries each action rather than of the menu as a whole, so a
 * hint put on the wrong item fails.
 *
 * MUTATION: delete `lengthItem.setToolTipText(SHORTCUT_LENGTH)` and the first claim fails.
 *
 * @author Adam
 */
public class testTheEditorNamesItsShortcuts
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;
    private static LayoutDiagram page;

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
            throw new SkipException("no autonomy configuration, so there is no editor menu to open");
        }

        page = model.getLayout(PAGE);

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
     * A station's right-click menu names Control+E, Control+S and Control+H on the items they do.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheStationMenuNamesItsKeys() throws Exception
    {
        final TileKey station = aStationOnThePage();

        final List<javax.swing.JMenuItem> items = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JPopupMenu menu = editor.getAutonomyPanel().buildTileMenu(station,
                page.getComponent(station.getX(), station.getY()));

            collect(menu.getSubElements(), items);
        });

        assertTrue(items.size() > 0, "precondition: the station's menu came back empty");

        javax.swing.JMenuItem length = byText(items, I18n.t("autosetup.ui.menuSetLength"));

        assertNotNull(length, "precondition: " + station + " offers no Segment Length item");

        assertEquals(length.getToolTipText(), AutonomyEditorPanel.SHORTCUT_LENGTH,
            "Segment Length does not say Control+E. Adam, OB-214: \"Set Segment Length needs a tooltip"
            + " that says Control+E\"");

        javax.swing.JMenuItem rename = byText(items, I18n.t("autosetup.ui.menuRename"));

        assertNotNull(rename, "precondition: " + station + " offers no Rename item");

        assertEquals(rename.getToolTipText(), AutonomyEditorPanel.SHORTCUT_NAME,
            "Rename does not say Control+S, the key that names the square under the pointer");

        boolean homeNamed = false;

        String homeNone = I18n.t("autosetup.ui.menuHomeNone");
        String homeFor = I18n.f("autosetup.ui.menuHomeFor", "\u0000").split("\u0000")[0];

        for (javax.swing.JMenuItem item : items)
        {
            String text = item.getText();

            if (text == null || !(text.equals(homeNone) || (!homeFor.isEmpty() && text.startsWith(homeFor))))
            {
                continue;
            }

            homeNamed |= AutonomyEditorPanel.SHORTCUT_HOME.equals(item.getToolTipText());
        }

        assertTrue(homeNamed, "the Home item on a station's menu does not say Control+H");

        // AND CONTROL+N, on the item that shows a station's name on a square (FR-086, Adam on MT-397: "let's add a hotkey
        // for 'show station name here' too").  Its tooltip already says what a caption is, so the key is added to it.
        javax.swing.JMenuItem showHere = null;

        for (javax.swing.JMenuItem item : items)
        {
            String text = item.getText();

            if (text != null && (text.equals(I18n.t("autosetup.ui.menuShowStationHere"))
                || text.equals(I18n.t("autosetup.ui.menuShowStationHereNamed"))
                || text.equals(I18n.t("autosetup.ui.menuStationShowsItself"))))
            {
                showHere = item;
            }
        }

        assertNotNull(showHere, "precondition: " + station + " offers no Show a Station Name Here item");

        assertTrue(showHere.getToolTipText() != null
            && showHere.getToolTipText().contains(AutonomyEditorPanel.SHORTCUT_STATION),
            "Show a Station Name Here does not say Control+N: " + showHere.getToolTipText());
    }

    /**
     * The Track Lengths toggle names Control+G, as the three toggles beside it name theirs.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheTrackLengthsToggleNamesItsKey() throws Exception
    {
        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("showLengths");

        field.setAccessible(true);

        final javax.swing.JCheckBox toggle = (javax.swing.JCheckBox) field.get(editor.getAutonomyPanel());

        final String[] tip = new String[1];

        SwingUtilities.invokeAndWait(() -> tip[0] = toggle.getToolTipText());

        assertTrue(tip[0] != null && tip[0].contains(AutonomyEditorPanel.SHORTCUT_LENGTHS),
            "the Track Lengths toggle does not say Control+G: " + tip[0]);
    }

    // ---------------------------------------------------------------------------------------------

    private static TileKey aStationOnThePage()
    {
        for (TileKey tile : session.getStore().getNamedTiles())
        {
            if (PAGE.equals(tile.getPage()) && session.getStore().isStation(tile)
                && page.getComponent(tile.getX(), tile.getY()) != null)
            {
                return tile;
            }
        }

        throw new SkipException("no station on " + PAGE);
    }

    private static void collect(javax.swing.MenuElement[] elements, List<javax.swing.JMenuItem> into)
    {
        for (javax.swing.MenuElement element : elements)
        {
            if (element instanceof javax.swing.JMenuItem) into.add((javax.swing.JMenuItem) element);

            collect(element.getSubElements(), into);
        }
    }

    private static javax.swing.JMenuItem byText(List<javax.swing.JMenuItem> items, String text)
    {
        for (javax.swing.JMenuItem item : items)
        {
            if (text.equals(item.getText())) return item;
        }

        return null;
    }
}
