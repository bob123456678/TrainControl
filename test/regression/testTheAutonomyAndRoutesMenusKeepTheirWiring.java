package regression;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import support.Components;
import support.Reflect;

/**
 * The Autonomy and Routes menus after the menu review: every item that moved, was renamed or is new reaches the door
 * it reached before, found in the real menu by its text and clicked, and each menu offers what it offered before,
 * changed only as the answers say.
 *
 * On a copy of the frozen railway in debug, because the doors write the setup and the debug export is part of the
 * census.
 *
 * @author Adam
 */
public class testTheAutonomyAndRoutesMenusKeepTheirWiring
{
    /** A second configuration, so the Configuration submenu has a choice to make. */
    private static final String SPARE = "D50 spare";

    private static final String DIVIDER = "---";

    /** The page the editor is opened on. */
    private static final String PAGE = "1 - Main";

    /** One claim's window over its own copy of the frozen railway, with a configuration loaded. */
    private interface Claim
    {
        void on(TrainControlUI ui) throws Exception;
    }

    private static void onItsOwnWindow(Claim claim) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot")));

            TrainControlUI ui = sandboxed.hold(openTheWindow());

            assertNotNull(ui.getAutonomySession(), "precondition: the window has no autonomy session");

            String kept = ui.getAutonomySession().getStore().getActiveConfiguration();

            assertNotNull(kept, "precondition: the frozen railway has no configuration");

            if (ui.getActiveDiagramConfiguration() == null)
            {
                SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(kept, false));
            }

            assertTrue(Components.waitFor(() -> ui.getActiveDiagramConfiguration() != null, 30000),
                "precondition: no configuration is loaded");

            claim.on(ui);
        }
        finally
        {
            support.Mouse.closeMenus();

            support.EditorsOpen.closeAll();

            sandboxed.close();
        }
    }

    // ---------------------------------------------------------------- one Manage list

    /**
     * The Autonomy menu's Manage Configurations and the Autonomy tab's Manage button hold one list, in the answer's
     * order, and the tab's debug-only Export Raw Graph stays after it.
     *
     * MUTATION: put Import... and Export... back in the Configuration submenu, or build the tab's list by hand again,
     * and this fails.
     */
    @Test
    public void testTheMenuAndTheTabHoldOneManageList() throws Exception
    {
        onItsOwnWindow(ui ->
        {
            List<String> list = Arrays.asList(t("autosetup.ui.menuNewConfiguration"),
                t("autosetup.ui.menuRenameConfiguration"), t("autosetup.ui.menuDeleteConfiguration"), DIVIDER,
                t("autosetup.ui.btnImportConfiguration"), t("autosetup.ui.btnExportConfiguration"), DIVIDER,
                t("autosetup.ui.menuUnloadAutonomy"), t("autosetup.ui.menuDeleteSetup"));

            assertEquals(shape(theManageSubmenu(ui).getMenuComponents()), list,
                "the Autonomy menu's Manage Configurations is not the one list (D50)");

            List<String> tab = new ArrayList<>(list);

            tab.addAll(Arrays.asList(DIVIDER, t("autosetup.ui.menuExportRawGraph")));

            assertEquals(shape(theTabsManageList(ui).getComponents()), tab,
                "the Autonomy tab's Manage button is not the same list, with the debug export after it (D50)");
        });
    }

    /**
     * Each item of both Manage lists, clicked, reaches its door: New Configuration... asks for the copy's name,
     * Rename... for the configuration's new one, Delete... which to delete, Import... for a file to open, Export... for
     * one to save, deleting the whole setup asks first, and Stop Using Autonomy unloads.
     *
     * MUTATION: wire an item of the shared list to another item's action, and this fails.
     */
    @Test
    public void testEveryManageItemReachesItsDoor() throws Exception
    {
        onItsOwnWindow(ui ->
        {
            AutonomySession session = ui.getAutonomySession();

            String loaded = ui.getActiveDiagramConfiguration();

            session.getStore().createConfiguration(SPARE, loaded);
            session.getStore().save();

            try (Doors doors = new Doors())
            {
                for (String where : new String[] {"menu", "tab"})
                {
                    // TO ITS DOOR, THEN AWAY: every question is closed unanswered
                    String said = "the " + where + "'s ";

                    assertTrue(doors.click(manageItem(ui, where, "autosetup.ui.menuNewConfiguration")).startsWith(
                        t("autosetup.ui.menuNewConfiguration") + " | " + t("autosetup.ui.promptConfigurationName")
                            + " | "), said + "New Configuration... does not ask for the copy's name: " + doors.seen);

                    assertTrue(doors.click(manageItem(ui, where, "autosetup.ui.menuRenameConfiguration"))
                        .endsWith(" | " + t("autosetup.ui.promptConfigurationName") + " | " + loaded), said
                        + "Rename... does not ask for a new name for " + loaded + ": " + doors.seen);

                    assertTrue(doors.click(manageItem(ui, where, "autosetup.ui.menuDeleteConfiguration")).startsWith(
                        t("autosetup.ui.titleDeleteConfiguration") + " | "
                            + t("autosetup.ui.promptWhichConfigurationToDelete") + " | "),
                        said + "Delete... does not ask which configuration: " + doors.seen);

                    assertEquals(doors.click(manageItem(ui, where, "autosetup.ui.btnImportConfiguration")),
                        "open a file", said + "Import... does not ask for a file to open");

                    assertEquals(doors.click(manageItem(ui, where, "autosetup.ui.btnExportConfiguration")),
                        "save a file", said + "Export... does not ask where to save");

                    assertEquals(doors.click(manageItem(ui, where, "autosetup.ui.menuDeleteSetup")),
                        t("autosetup.ui.menuDeleteSetup") + " | "
                            + I18n.f("autosetup.ui.confirmDeleteSetup", session.getStore().getConfigurationNames().size()),
                        said + "deleting the whole setup does not ask first");

                    assertTrue(session.getStore().getConfigurationNames().containsAll(Arrays.asList(loaded, SPARE)),
                        "a question closed unanswered deleted a configuration: " + doors.seen);

                    // STOP USING AUTONOMY, then loaded again for the next list
                    final JMenuItem stop = manageItem(ui, where, "autosetup.ui.menuUnloadAutonomy");

                    SwingUtilities.invokeLater(stop::doClick);

                    assertTrue(Components.waitFor(() -> ui.getActiveDiagramConfiguration() == null, 30000),
                        said + "Stop Using Autonomy did not unload " + loaded);

                    SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(loaded, false));

                    assertTrue(Components.waitFor(() -> loaded.equals(ui.getActiveDiagramConfiguration()), 30000),
                        "precondition: " + loaded + " would not load again");
                }
            }
        });
    }

    /**
     * The Configuration submenu lists only the configurations, one choice each, and choosing each loads it.
     *
     * MUTATION: leave Import... in the submenu, or load another configuration than the one chosen, and this fails.
     */
    @Test
    public void testEachConfigurationInTheSubmenuLoadsIt() throws Exception
    {
        onItsOwnWindow(ui ->
        {
            AutonomySession session = ui.getAutonomySession();

            String loaded = ui.getActiveDiagramConfiguration();

            session.getStore().createConfiguration(SPARE, loaded);
            session.getStore().save();

            List<String> names = new ArrayList<>(session.getStore().getConfigurationNames());

            assertEquals(shape(theConfigurationSubmenu(ui).getMenuComponents()), names,
                "the Configuration submenu holds more than the configurations (D50)");

            try (Doors doors = new Doors())
            {
                for (final String name : new String[] {SPARE, loaded})
                {
                    JMenuItem choice = null;

                    for (Component part : theConfigurationSubmenu(ui).getMenuComponents())
                    {
                        if (part instanceof JRadioButtonMenuItem && name.equals(((JMenuItem) part).getText()))
                        {
                            choice = (JMenuItem) part;
                        }
                    }

                    assertNotNull(choice, "the Configuration submenu has no " + name);

                    SwingUtilities.invokeLater(choice::doClick);

                    assertTrue(Components.waitFor(() -> name.equals(ui.getActiveDiagramConfiguration()), 30000),
                        "choosing " + name + " in the Configuration submenu did not load it: "
                            + ui.getActiveDiagramConfiguration() + ", " + doors.seen);
                }
            }
        });
    }

    /**
     * Census: the Autonomy menu offers what it offered before, changed only as the answers say: Documentation is called
     * Autonomy Guide, and Import... and Export... are in Manage Configurations, once each.  Besides those,
     * the configurations, the pages to edit and the pages to tick, each once.
     *
     * MUTATION: offer Import... in both submenus, or drop Stop Using Autonomy, and this fails.
     */
    @Test
    public void testTheAutonomyMenuOffersWhatItDid() throws Exception
    {
        onItsOwnWindow(ui ->
        {
            // BEFORE the change, with the debug export a debug window shows
            List<String> before = new ArrayList<>(Arrays.asList("autosetup.ui.btnImportConfiguration",
                "autosetup.ui.btnExportConfiguration", "autosetup.ui.menuNewConfiguration",
                "autosetup.ui.menuRenameConfiguration", "autosetup.ui.menuDeleteConfiguration",
                "autosetup.ui.menuUnloadAutonomy", "autosetup.ui.menuDeleteSetup", "autosetup.ui.menuGlobalSettings",
                "autosetup.ui.menuExportRawGraph", "ui.main.documentation"));

            // renamed
            before.set(before.indexOf("ui.main.documentation"), "ui.menu.autonomyGuide");

            List<String> expected = new ArrayList<>();

            for (String key : before) expected.add(t(key));

            AutonomySession session = ui.getAutonomySession();

            expected.addAll(session.getStore().getConfigurationNames());

            for (LayoutDiagram page : session.getPages())
            {
                if (!session.getStore().getExcludedPages().contains(page.getName())) expected.add(page.getName());

                expected.add(page.getName());
            }

            assertOffers(theAutonomyMenu(ui).getMenuComponents(), expected, "the Autonomy menu");
        });
    }

    /**
     * Census: the Autonomy tab's Manage button offers what it did, plus Stop Using Autonomy and deleting the whole setup,
     * which the one list brings from the menu.
     *
     * MUTATION: build the tab's list by hand again, and this fails.
     */
    @Test
    public void testTheTabsManageButtonOffersWhatItDidAndTheMenusTwo() throws Exception
    {
        onItsOwnWindow(ui ->
        {
            List<String> expected = new ArrayList<>();

            // BEFORE the change, with the debug export
            for (String key : new String[] {"autosetup.ui.menuNewConfiguration", "autosetup.ui.menuRenameConfiguration",
                "autosetup.ui.menuDeleteConfiguration", "autosetup.ui.btnImportConfiguration",
                "autosetup.ui.btnExportConfiguration", "autosetup.ui.menuExportRawGraph"}) expected.add(t(key));

            // the menu's two
            expected.add(t("autosetup.ui.menuUnloadAutonomy"));
            expected.add(t("autosetup.ui.menuDeleteSetup"));

            assertOffers(theTabsManageList(ui).getComponents(), expected, "the Autonomy tab's Manage button");
        });
    }

    // ---------------------------------------------------------------- Routes

    /**
     * Routes reads New Route... / Find Route... / divider / Export Routes... / Import Routes....  New Route...
     * opens the route editor for a new route, as the Add Route button does; Find Route... asks for a name; Export and
     * Import are the items they were, with their handlers.  Census: what it offered before, plus New Route....
     *
     * MUTATION: wire New Route... to anything but the Add Route button's handler, or drop it, and this fails.
     */
    @Test
    public void testTheRoutesMenuReadsAndActsAsAnswered() throws Exception
    {
        onItsOwnWindow(ui ->
        {
            JMenu routes = menuCalled(ui, "ui.main.toolbar.routes");

            assertEquals(shape(routes.getMenuComponents()), Arrays.asList(t("route.ui.menuNewRoute"),
                t("route.ui.menuFindRoute"), DIVIDER, t("ui.main.toolbar.export"), t("ui.main.toolbar.tooltip.import")),
                "the Routes menu does not read as answered (D51)");

            // CENSUS: before (inventory), then New Route... added
            List<String> expected = new ArrayList<>(Arrays.asList(t("route.ui.menuFindRoute"),
                t("ui.main.toolbar.export"), t("ui.main.toolbar.tooltip.import")));

            expected.add(0, t("route.ui.menuNewRoute"));

            assertOffers(routes.getMenuComponents(), expected, "the Routes menu");

            // EXPORT AND IMPORT ARE THE GENERATED ITEMS, whose handlers nothing here touched
            assertTrue(itemCalled(routes, t("ui.main.toolbar.export"))
                == Reflect.get(TrainControlUI.class, ui, "exportRoutesMenuItem"), "Export Routes... is not the item"
                + " that exports the routes");

            assertTrue(itemCalled(routes, t("ui.main.toolbar.tooltip.import"))
                == Reflect.get(TrainControlUI.class, ui, "importRoutesMenuItem"), "Import Routes... is not the item"
                + " that imports them");

            try (Doors doors = new Doors())
            {
                assertEquals(doors.click(itemCalled(routes, t("route.ui.menuFindRoute"))),
                    t("route.ui.menuFindRoute") + " | " + t("route.ui.promptFindRoute") + " | null",
                    "Find Route... does not ask for a name");

                // NEW ROUTE..., AS THE ADD ROUTE BUTTON
                SwingUtilities.invokeLater(itemCalled(routes, t("route.ui.menuNewRoute"))::doClick);

                assertTrue(Components.waitFor(() -> aRouteEditorTitled(t("route.ui.dialogAddNewRoute")), 20000),
                    "New Route... did not open the route editor on a new route: " + doors.seen);

                support.RouteEditing.closeEditors();

                AbstractButton add = Components.withText(ui, t("ui.main.addRoute"));

                assertNotNull(add, "precondition: the Routes tab has no Add Route button");

                SwingUtilities.invokeLater(add::doClick);

                assertTrue(Components.waitFor(() -> aRouteEditorTitled(t("route.ui.dialogAddNewRoute")), 20000),
                    "precondition: Add Route did not open the route editor on a new route");

                support.RouteEditing.closeEditors();
            }
        });
    }

    // ---------------------------------------------------------------- an editor open

    /**
     * With an editor open, Layouts and Autonomy both start with Go to the Opened Editor and a divider; it brings the
     * editor back; every item the editor grays says "Close the editor to use this."; Autonomy Guide stays live; New
     * Route... refuses as Add Route does.  Closed, Layouts hides the two and gives each item its own tooltip back.
     *
     * MUTATION: drop the divider, leave a grayed item without the reason, or keep the reason after the editor closes,
     * and this fails.
     */
    @Test
    public void testWithAnEditorOpenBothMenusLeadBackToIt() throws Exception
    {
        onItsOwnWindow(ui ->
        {
            JMenu layouts = menuCalled(ui, "ui.main.toolbar.layouts");

            open(layouts);

            java.util.Map<JMenuItem, String> tipsAtRest = new java.util.IdentityHashMap<>();

            for (Component part : layouts.getMenuComponents())
            {
                if (part instanceof JMenuItem) tipsAtRest.put((JMenuItem) part, ((JMenuItem) part).getToolTipText());
            }

            SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor(PAGE, false, null));

            assertTrue(Components.waitFor(() -> support.EditorsOpen.editorOn(PAGE) != null, 20000),
                "precondition: the editor did not open on " + PAGE);

            final LayoutEditor editor = support.EditorsOpen.editorOn(PAGE);

            String why = t("autosetup.ui.tooltipCloseTheEditor");

            for (JMenu menu : new JMenu[] {layouts, theAutonomyMenu(ui)})
            {
                open(menu);

                List<Component> shown = new ArrayList<>();

                for (Component part : menu.getMenuComponents()) if (part.isVisible()) shown.add(part);

                String name = menu.getText();

                assertTrue(shown.get(0) instanceof JMenuItem && t("autosetup.ui.menuGoToOpenEditor")
                    .equals(((JMenuItem) shown.get(0)).getText()) && shown.get(0).isEnabled(), name + " does not start"
                    + " with Go to the Opened Editor: " + shape(menu.getMenuComponents()));

                assertTrue(shown.get(1) instanceof JSeparator, name + " has no divider under Go to the Opened Editor");

                for (Component part : shown)
                {
                    if (!(part instanceof JMenuItem) || part.isEnabled() || part.getFont().isBold()) continue;

                    assertTrue(String.valueOf(((JMenuItem) part).getToolTipText()).contains(why), name + "'s grayed "
                        + ((JMenuItem) part).getText() + " does not say why: " + ((JMenuItem) part).getToolTipText());
                }

                // IT BRINGS THE EDITOR BACK
                SwingUtilities.invokeAndWait(() -> editor.setVisible(false));

                SwingUtilities.invokeLater(((JMenuItem) shown.get(0))::doClick);

                assertTrue(Components.waitFor(editor::isVisible, 10000), name + "'s Go to the Opened Editor did not"
                    + " bring the editor back");
            }

            JMenuItem guide = itemCalled(theAutonomyMenu(ui), t("ui.menu.autonomyGuide"));

            assertTrue(guide != null && guide.isEnabled(), "with an editor open, the Autonomy Guide is grayed");

            // NEW ROUTE... REFUSES AS ADD ROUTE DOES
            try (Doors doors = new Doors())
            {
                assertEquals(doors.click(itemCalled(menuCalled(ui, "ui.main.toolbar.routes"), t("route.ui.menuNewRoute"))),
                    t("autosetup.ui.menuEditorOpen"), "with an editor open, New Route... did not refuse as Add Route"
                        + " does");

                assertFalse(aRouteEditorTitled(t("route.ui.dialogAddNewRoute")), "with an editor open, New Route..."
                    + " opened the route editor");
            }

            // CLOSED: the two hidden, and every tooltip back
            SwingUtilities.invokeAndWait(editor::dispose);

            assertTrue(Components.waitFor(() -> !ui.isLayoutEditorOpen(), 10000), "precondition: the editor would not"
                + " close");

            open(layouts);

            List<String> shown = new ArrayList<>();

            for (Component part : layouts.getMenuComponents())
            {
                if (part.isVisible() && part instanceof JMenuItem) shown.add(((JMenuItem) part).getText());
            }

            assertFalse(shown.contains(t("autosetup.ui.menuGoToOpenEditor")), "Go to the Opened Editor stays after the"
                + " editor closed");

            assertFalse(layouts.getMenuComponent(0).isVisible() || layouts.getMenuComponent(1).isVisible(),
                "Go to the Opened Editor or its divider stays after the editor closed");

            for (java.util.Map.Entry<JMenuItem, String> was : tipsAtRest.entrySet())
            {
                assertEquals(was.getKey().getToolTipText(), was.getValue(), was.getKey().getText() + " did not get its"
                    + " own tooltip back once the editor closed");
            }
        });
    }

    // ---------------------------------------------------------------- the doors

    /**
     * Watches for the program's dialogs and closes each unanswered, keeping what it was: a file chooser's kind, or a
     * question's title, words and starting text.
     */
    private static final class Doors implements AutoCloseable
    {
        final List<String> seen = Collections.synchronizedList(new ArrayList<>());

        private final AtomicBoolean going = new AtomicBoolean(true);

        Doors()
        {
            final Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

            Thread watching = new Thread(() -> support.DialogWatch.until(going::get, 50, (JDialog dialog) ->
            {
                final JFileChooser chooser = Components.inside(dialog.getContentPane(), JFileChooser.class);

                if (chooser != null)
                {
                    if (!handled.add(chooser)) return;

                    seen.add(chooser.getDialogType() == JFileChooser.SAVE_DIALOG ? "save a file" : "open a file");

                    SwingUtilities.invokeLater(chooser::cancelSelection);

                    return;
                }

                final JOptionPane pane = Components.inside(dialog.getContentPane(), JOptionPane.class);

                if (pane == null || !handled.add(pane)) return;

                seen.add(pane.getWantsInput()
                    ? dialog.getTitle() + " | " + pane.getMessage() + " | " + pane.getInitialSelectionValue()
                    : pane.getOptions() != null ? dialog.getTitle() + " | " + pane.getMessage()
                    : String.valueOf(pane.getMessage()));

                SwingUtilities.invokeLater(() -> pane.setValue(Integer.valueOf(JOptionPane.CLOSED_OPTION)));
            }), "menu wiring doors");

            watching.setDaemon(true);
            watching.start();
        }

        /** Clicks the item and returns the first dialog it shows. */
        String click(final JMenuItem item) throws Exception
        {
            assertNotNull(item, "precondition: no such item");

            assertTrue(item.isEnabled(), "precondition: " + item.getText() + " is grayed");

            final int before = seen.size();

            SwingUtilities.invokeLater(item::doClick);

            assertTrue(Components.waitFor(() -> seen.size() > before, 20000), item.getText() + " showed nothing");

            Components.drain(6);

            return seen.get(before);
        }

        @Override
        public void close()
        {
            going.set(false);
        }
    }

    // ---------------------------------------------------------------- the menus

    private static String t(String key)
    {
        return I18n.t(key);
    }

    /** The texts of these parts in order, a divider as `---`. */
    private static List<String> shape(Component[] parts)
    {
        List<String> out = new ArrayList<>();

        for (Component part : parts)
        {
            if (part instanceof JSeparator) out.add(DIVIDER);
            else if (part instanceof JMenuItem) out.add(((JMenuItem) part).getText());
        }

        return out;
    }

    /** Every item that acts, in every submenu: what the menu offers. */
    private static void leavesOf(Component[] parts, List<String> into)
    {
        for (Component part : parts)
        {
            if (part instanceof JMenu) leavesOf(((JMenu) part).getMenuComponents(), into);
            else if (part instanceof JMenuItem) into.add(((JMenuItem) part).getText());
        }
    }

    /** Census: the items that act, in every submenu, are the expected ones, none lost, added or doubled. */
    private static void assertOffers(Component[] parts, List<String> expected, String what)
    {
        List<String> leaves = new ArrayList<>();

        leavesOf(parts, leaves);

        List<String> wanted = new ArrayList<>(expected);

        Collections.sort(wanted);
        Collections.sort(leaves);

        assertEquals(leaves, wanted, what + " offers something lost, added or doubled");
    }

    private static JMenuItem itemCalled(JMenu menu, String text)
    {
        return testTheImportDoorReadsAnOldFile.itemCalled(menu, text);
    }

    private static JMenu menuCalled(TrainControlUI ui, String key) throws Exception
    {
        JMenuBar bar = (JMenuBar) Reflect.get(TrainControlUI.class, ui, "mainMenuBar");

        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            if (bar.getMenu(i) != null && t(key).equals(bar.getMenu(i).getText())) return bar.getMenu(i);
        }

        throw new AssertionError("precondition: the menu bar has no " + t(key));
    }

    /** Opens the menu as the user does, so it is built and guarded as they see it. */
    private static void open(final JMenu menu) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
        });
    }

    private static JMenu theAutonomyMenu(TrainControlUI ui) throws Exception
    {
        JMenu menu = menuCalled(ui, "autosetup.ui.menuAutonomy");

        open(menu);

        return menu;
    }

    private static JMenu submenuCalled(JMenu menu, String text)
    {
        for (Component part : menu.getMenuComponents())
        {
            if (part instanceof JMenu && text.equals(((JMenu) part).getText())) return (JMenu) part;
        }

        throw new AssertionError("the " + menu.getText() + " menu has no " + text + ": " + shape(menu.getMenuComponents()));
    }

    private static JMenu theManageSubmenu(TrainControlUI ui) throws Exception
    {
        return submenuCalled(theAutonomyMenu(ui), t("autosetup.ui.btnManage"));
    }

    private static JMenu theConfigurationSubmenu(TrainControlUI ui) throws Exception
    {
        return submenuCalled(theAutonomyMenu(ui), I18n.f("autosetup.ui.menuConfigurations",
            ui.getActiveDiagramConfiguration()));
    }

    /**
     * The list the Autonomy tab's Manage button shows, from the method its press calls: the panel is built, as the
     * home of the menu's actions, but no longer shown, so the button cannot be pressed on screen.
     */
    private static JPopupMenu theTabsManageList(TrainControlUI ui) throws Exception
    {
        final AbstractButton manage = Components.withText(ui.getAutonomyViewerPanel(), t("autosetup.ui.btnManage"));

        assertNotNull(manage, "the Autonomy tab has no Manage button");

        final java.lang.reflect.Method shown = Reflect.method(org.traincontrol.gui.AutonomyViewerPanel.class,
            "manageMenu");

        return Components.onTheEventThread(() -> (JPopupMenu) shown.invoke(ui.getAutonomyViewerPanel()));
    }

    /** The item of this text on the Autonomy menu's Manage list, or on the tab's. */
    private static JMenuItem manageItem(TrainControlUI ui, String where, String key) throws Exception
    {
        Component[] parts = "menu".equals(where) ? theManageSubmenu(ui).getMenuComponents()
            : theTabsManageList(ui).getComponents();

        for (Component part : parts)
        {
            if (part instanceof JMenuItem && t(key).equals(((JMenuItem) part).getText())) return (JMenuItem) part;
        }

        throw new AssertionError("the " + where + "'s Manage list has no " + t(key) + ": " + shape(parts));
    }

    private static boolean aRouteEditorTitled(final String title)
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof org.traincontrol.gui.RouteEditorFrame && window.isShowing()
                    && title.equals(((java.awt.Frame) window).getTitle())) return true;
            }

            return false;
        });
    }
}
