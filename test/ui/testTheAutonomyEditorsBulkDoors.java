package ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * Three doors of the autonomy setup, pressed as the user presses them and answered both ways: Bulk Tools' Allow Every
 * Path (in the editor, and on the track diagram's own menu, which saves at once), Pair with a Link... on a link's
 * right-click menu, and the Autonomy menu's Delete This Layout's Whole Autonomy Setup.
 *
 * Each claim opens its own sandboxed railway (the frozen copy, or the single-switch scenario for the deletion), builds
 * the menu the user would open, answers the real dialog, and reads the setup in memory and then back from the files.
 */
public class testTheAutonomyEditorsBulkDoors
{
    private static final String PAGE = "1 - Main";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;

    // ---------------------------------------------------------------- Allow Every Path

    /**
     * In the editor, Allow Every Path asks how many squares it will open, and a Yes opens them all: no square is left
     * one-way or closed, the diagram's directions read both ways, and Save writes it.
     *
     * MUTATION: drop `session.allowEveryPath()` from `allowEveryPath`, and the Yes opens nothing.
     */
    @Test
    public void testAllowEveryPathAnsweredYesOpensEverySquare() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", true);

            Map<TileKey, List<RouteId>> shut = someSquaresAreShut();

            int count = shut.size();

            List<String> asked = press(editorBulkItem(count), message -> yes());

            assertEquals(asked, Collections.singletonList(I18n.f("autolayout.ui.confirmAllowEveryPath" + I18n.countForm(count),
                count)), "the editor's question is not the one that says how many squares and that Cancel undoes it");

            assertEquals(session.routesNotOpenBothWays().size(), 0, "squares are still one-way or closed after Yes: "
                + session.routesNotOpenBothWays().keySet());

            for (Map.Entry<TileKey, List<RouteId>> square : shut.entrySet())
            {
                for (RouteId road : square.getValue())
                {
                    assertEquals(session.getGraph().getDirection(square.getKey(), road), Direction.BOTH,
                        square.getKey() + "'s road " + road + " is not drawn both ways after Yes");
                }
            }

            AbstractButton again = editorBulkItem(Components.onTheEventThread(() -> session.routesNotOpenBothWays().size()),
                false);

            assertFalse(again.isEnabled(), "Allow Every Path is still offered with nothing left to open");

            save();

            assertEquals(readBack().routesNotOpenBothWays().size(), 0, "Save did not keep the opened squares");
        }
        finally
        {
            close();
        }
    }

    /**
     * Answered No, it opens nothing: the same squares are shut in the editor and in the files after Save.
     *
     * MUTATION: in `allowEveryPath`, go back only when the dialog was closed (`== CLOSED_OPTION`), and No falls through
     * to the opening; this and the diagram's No below go red.
     */
    @Test
    public void testAllowEveryPathAnsweredNoOpensNothing() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", true);

            Map<TileKey, List<RouteId>> shut = someSquaresAreShut();

            press(editorBulkItem(shut.size()), message -> no());

            assertEquals(session.routesNotOpenBothWays(), shut, "No changed what is shut");

            save();

            assertEquals(readBack().routesNotOpenBothWays(), shut, "after No and Save, the files differ");
        }
        finally
        {
            close();
        }
    }

    /**
     * The warning says Cancel undoes it, and it does: after a Yes, the editor's Cancel (answered Yes to throwing the
     * changes away) puts every square back as it was.
     *
     * MUTATION: in the editor's Cancel, skip `autonomy.restoreSetup`, and the opened squares stay open.
     */
    @Test
    public void testCancelingTheEditorShutsThemAgain() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", true);

            Map<TileKey, List<RouteId>> shut = someSquaresAreShut();

            press(editorBulkItem(shut.size()), message -> yes());

            assertEquals(session.routesNotOpenBothWays().size(), 0, "precondition: Yes opened nothing");

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
            {
                Components.press(Components.withText(editor, I18n.t("ui.cancel")));

                assertTrue(Components.waitFor(() -> !editor.isDisplayable(), 15000), "Cancel did not close the editor");
            }

            assertEquals(session.routesNotOpenBothWays(), shut, "Cancel did not put the squares back as they were");
        }
        finally
        {
            close();
        }
    }

    /**
     * On the track diagram's own menu there is no Cancel to undo it, so the question says the opening is saved at once,
     * and a Yes writes the setup there and then.
     *
     * MUTATION: in `onThisSurface`, give the diagram the editor's wording (`return key;`), and the question promises a
     * Cancel it does not have.  The write itself goes out three ways (the panel's change callback, the item's
     * `refresh()` and the rebuild), so removing any one leaves it green.
     */
    @Test
    public void testTheDiagramsMenuSavesAtOnceAfterYes() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", false);

            Map<TileKey, List<RouteId>> shut = someSquaresAreShut();

            int count = shut.size();

            List<String> asked = press(diagramBulkItem(count), message -> yes());

            assertEquals(asked, Collections.singletonList(I18n.f(
                "autolayout.ui.confirmAllowEveryPathAtOnce" + I18n.countForm(count), count)),
                "the diagram's question does not say the opening is saved at once");

            assertEquals(readBack().routesNotOpenBothWays().size(), 0, "the files still have shut squares, so Yes was not"
                + " saved");
        }
        finally
        {
            close();
        }
    }

    /** And a No on the diagram's menu leaves the files as they were. */
    @Test
    public void testTheDiagramsMenuLeavesTheFilesAfterNo() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", false);

            Map<TileKey, List<RouteId>> shut = someSquaresAreShut();

            press(diagramBulkItem(shut.size()), message -> no());

            assertEquals(readBack().routesNotOpenBothWays(), shut, "No on the diagram's menu changed the files");
        }
        finally
        {
            close();
        }
    }

    // ---------------------------------------------------------------- Pair with a Link

    /**
     * Pair with a Link... lists every other link of the kind, and pairing with the one chosen makes the two partners of
     * each other, whatever either was paired with.
     *
     * MUTATION: in `pairFromList`, pair with `candidates.get(0)` whichever is chosen, and the first listed is paired.
     */
    @Test
    public void testPairingWithTheLinkChosenFromTheList() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", true);

            List<TileKey> links = linksOnTheRailway();

            TileKey from = links.get(0);

            List<TileKey> others = new ArrayList<>(links.subList(1, links.size()));

            TileKey chosen = others.get(others.size() - 1);

            List<String> listed = new ArrayList<>();

            press(tileItem(from, I18n.t("autosetup.ui.menuPairLink")), pickingFromTheList(chosen, listed));

            assertEquals(listed.size(), others.size(), "the list does not offer each other link once: " + listed);

            for (TileKey other : others)
            {
                assertTrue(containsOne(listed, other.toString()), other + " is not in the list: " + listed);
            }

            assertFalse(containsOne(listed, from.toString()), "the list offers the link its own partner: " + listed);

            assertEquals(session.getStore().getPortalPartner(from), chosen, "the link is not paired with the one chosen");
            assertEquals(session.getStore().getPortalPartner(chosen), from, "a pairing is not mutual");

            save();

            assertEquals(readBack().getStore().getPortalPartner(from), chosen, "Save did not keep the pairing");
        }
        finally
        {
            close();
        }
    }

    /**
     * Canceling the list pairs nothing: every link keeps its partner.
     *
     * MUTATION: in `pairFromList`, go on to pair when the dialog is canceled (`== CLOSED_OPTION` in place of
     * `!= OK_OPTION`), and the first listed is paired.
     */
    @Test
    public void testCancelingTheListPairsNothing() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", true);

            List<TileKey> links = linksOnTheRailway();

            Map<TileKey, TileKey> before = partnersOf(links);

            press(tileItem(links.get(0), I18n.t("autosetup.ui.menuPairLink")),
                message -> Answering.press(I18n.t("ui.cancel")));

            assertEquals(partnersOf(links), before, "Cancel changed who is paired with whom");
        }
        finally
        {
            close();
        }
    }

    /**
     * A tunnel is offered only the tunnels of its own page, and never a link: like pairs with like.
     *
     * MUTATION: drop the `component.getType() != from.getType()` filter, and every square of the railway is listed.
     */
    @Test
    public void testATunnelIsOfferedOnlyTheOtherTunnelsOfItsPage() throws Exception
    {
        needsADisplay();

        try
        {
            open("live-snapshot", true);

            List<TileKey> tunnels = new ArrayList<>();

            for (Map.Entry<TileKey, LayoutDiagramComponent> tile : session.getGraph().getTiles().entrySet())
            {
                if (tile.getValue() != null && tile.getValue().getType() == LayoutDiagramComponent.componentType.TUNNEL)
                {
                    tunnels.add(tile.getKey());
                }
            }

            assertTrue(tunnels.size() >= 2, "precondition: the frozen railway has no pair of tunnels: " + tunnels);

            TileKey from = tunnels.get(0);

            List<String> listed = new ArrayList<>();

            press(tileItem(from, I18n.t("autosetup.ui.menuPairLink")), pickingFromTheList(null, listed));

            int onItsPage = 0;

            for (TileKey other : tunnels)
            {
                if (!other.equals(from) && other.getPage().equals(from.getPage())) onItsPage++;
            }

            assertEquals(listed.size(), onItsPage, "the tunnel is offered something other than the tunnels of its page: "
                + listed);
        }
        finally
        {
            close();
        }
    }

    // ---------------------------------------------------------------- Delete This Layout's Whole Autonomy Setup

    /**
     * The Autonomy menu's delete asks first, with No as the answer the Enter key gives, and No (or closing the question)
     * changes nothing: the configurations and the files stay, and the railway stays built.
     *
     * MUTATION: in `deleteEverything`, go back only for a closed-box answer (`answer < -5`) and No deletes the setup;
     * give the dialog `YES_NO_OPTS[0]` as its default and the default is Yes.
     */
    @Test
    public void testDeleteEverythingAnsweredNoChangesNothing() throws Exception
    {
        needsADisplay();

        try
        {
            open("single-switch", false);

            List<String> names = new ArrayList<>(session.getStore().getConfigurationNames());

            assertFalse(names.isEmpty(), "precondition: the railway has no configuration to delete");

            model.parseAuto(session.buildConfiguration());

            Object[] defaultWas = new Object[1];

            List<String> asked = press(deleteItem(), message ->
            {
                defaultWas[0] = defaultOptionOfTheQuestionOnScreen();

                return no();
            });

            assertEquals(asked, Collections.singletonList(I18n.f("autosetup.ui.confirmDeleteSetup", names.size())),
                "the delete did not ask, or asked something else");

            assertEquals(String.valueOf(defaultWas[0]), String.valueOf(TrainControlUI.YES_NO_OPTS[1]),
                "the question's default answer is not No");

            assertSetupStands(names);

            press(deleteItem(), message -> Answering.press("no such button, so the question is closed as its box does"));

            assertSetupStands(names);
        }
        finally
        {
            close();
        }
    }

    /**
     * Yes removes the whole setup: no configuration is left in the store, the setup files and their folder are gone,
     * the running railway is dropped, and the track diagram's own files are untouched.
     *
     * MUTATION: drop `session.getStore().deleteEverything()` from `deleteEverything`, and the files remain.
     */
    @Test
    public void testDeleteEverythingAnsweredYesLeavesAnEmptyStore() throws Exception
    {
        needsADisplay();

        try
        {
            open("single-switch", false);

            List<String> names = new ArrayList<>(session.getStore().getConfigurationNames());

            File autonomy = new File(sandbox.getFolder(), "config/autonomy");

            File page = new File(sandbox.getFolder(), "config/gleisbilder/1 - Junction.cs2");

            long pageBytes = page.length();

            assertTrue(autonomy.isDirectory() && autonomy.list().length > 0, "precondition: the sandbox has no setup files");

            model.parseAuto(session.buildConfiguration());

            assertTrue(model.hasAutoLayout(), "precondition: no railway is built from the setup");

            press(deleteItem(), message -> yes());

            Components.settle();

            AutonomySession now = ui.getAutonomySession();

            assertTrue(now == null || now.getStore().getConfigurationNames().isEmpty(), "configurations remain in the store:"
                + " " + (now == null ? "" : now.getStore().getConfigurationNames()));

            assertFalse(autonomy.isDirectory(), "the setup files are still in " + autonomy + ": "
                + java.util.Arrays.toString(autonomy.list()));

            assertFalse(model.hasAutoLayout(), "the railway built from the deleted setup is still there");

            assertTrue(page.isFile() && page.length() == pageBytes, "the track diagram's page was touched");

            assertFalse(names.isEmpty(), "precondition: there was no configuration to delete");
        }
        finally
        {
            close();
        }
    }

    // ---------------------------------------------------------------- the railway

    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the autonomy editor needs a display");
    }

    /** A sandboxed copy of a scenario with the window's setup open, and the editor on the main page where asked. */
    private static void open(String scenario, boolean withEditor) throws Exception
    {
        // Inside a try, so a failure part way through puts the layout preference back (OB-111).
        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor(scenario));

            model = support.TestStation.simulatedInDebug();

            SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

            ui.setViewListener(model, new CountDownLatch(1));

            session = ui.getAutonomySession();

            assertTrue(session != null && session.getStore().getActiveConfiguration() != null,
                "precondition: " + scenario + " opened with no autonomy configuration");

            if ("live-snapshot".equals(scenario)) shutTwoStraights();

            if (!withEditor) return;

            final LayoutDiagram page = model.getLayout(PAGE);

            assertNotNull(page, "precondition: no page " + PAGE);

            editor = Components.onTheEventThread(() ->
            {
                LayoutEditor built = new LayoutEditor(page, 30, ui, 0);

                built.render();
                built.setAutonomyMode(session);

                return built;
            });

            Components.settle();
        }
        catch (Throwable failed)
        {
            close();

            throw failed;
        }
    }

    private static void close() throws Exception
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
            editor = null;
            ui = null;
            model = null;
            session = null;

            if (sandbox != null) sandbox.close();

            sandbox = null;
        }
    }

    /**
     * The setup made to shut something for sure and saved, before any editor opens on it: a straight of the main page
     * closed and another made one-way, on top of whatever the frozen railway already shuts.
     */
    private static void shutTwoStraights() throws Exception
    {
        List<TileKey> straights = new ArrayList<>();

        for (Map.Entry<TileKey, LayoutDiagramComponent> tile : session.getGraph().getTiles().entrySet())
        {
            if (PAGE.equals(tile.getKey().getPage()) && tile.getValue() != null
                && tile.getValue().getType() == LayoutDiagramComponent.componentType.STRAIGHT) straights.add(tile.getKey());
        }

        assertTrue(straights.size() >= 2, "precondition: the main page has no two straights to shut");

        session.setDirection(Collections.singleton(straights.get(0)), Direction.NONE);
        session.setDirection(Collections.singleton(straights.get(1)), Direction.TOWARD_B);

        session.save();
    }

    /** The squares that are one-way or closed now, which are at least the two made so. */
    private static Map<TileKey, List<RouteId>> someSquaresAreShut()
    {
        Map<TileKey, List<RouteId>> shut = Components.onTheEventThread(() -> session.routesNotOpenBothWays());

        assertTrue(shut.size() >= 2, "precondition: nothing is shut, so Allow Every Path has nothing to open");

        return new LinkedHashMap<>(shut);
    }

    /** The setup read back from the files, as a restart reads it. */
    private static AutonomySession readBack() throws Exception
    {
        AutonomySession reread = new AutonomySession(sandbox.getFolder());

        reread.open(support.LayoutSandbox.wiredPages(model));

        return reread;
    }

    /** The editor's Save, pressed. */
    private static void save() throws Exception
    {
        press(Components.withText(editor, I18n.t("layout.ui.saveChanges")), message -> null);
    }

    // ---------------------------------------------------------------- the menus

    /** Bulk Tools' Allow Every Path as the editor builds it, for this many shut squares. */
    private static AbstractButton editorBulkItem(int count) throws Exception
    {
        return editorBulkItem(count, true);
    }

    private static AbstractButton editorBulkItem(int count, boolean mustBeOn) throws Exception
    {
        JMenu bulk = Components.onTheEventThread(() -> editor.getAutonomyPanel().buildBulkMenuForTest());

        AbstractButton item = Components.withText(bulk, I18n.f("autolayout.ui.menuAllowEveryPath", count));

        assertNotNull(item, "Bulk Tools has no \"" + I18n.f("autolayout.ui.menuAllowEveryPath", count) + "\"");

        if (mustBeOn) assertTrue(item.isEnabled(), "Allow Every Path is greyed: " + item.getToolTipText());

        return item;
    }

    /** The same on the track diagram's right-click menu of a square. */
    private static AbstractButton diagramBulkItem(int count) throws Exception
    {
        TileKey square = session.getGraph().getTiles().keySet().iterator().next();

        javax.swing.JPopupMenu menu = Components.onTheEventThread(() -> ui.buildAutonomyTileMenu(square));

        assertNotNull(menu, "precondition: the diagram offers no menu on " + square);

        AbstractButton item = Components.withText(menu, I18n.f("autolayout.ui.menuAllowEveryPath", count));

        assertNotNull(item, "the diagram's Bulk Tools has no \"" + I18n.f("autolayout.ui.menuAllowEveryPath", count) + "\"");

        return item;
    }

    /** An item of a square's right-click menu in the editor. */
    private static AbstractButton tileItem(final TileKey square, String text) throws Exception
    {
        javax.swing.JPopupMenu menu = Components.onTheEventThread(() -> editor.getAutonomyPanel().buildTileMenu(square, null));

        assertNotNull(menu, "precondition: " + square + " has no menu");

        AbstractButton item = Components.withText(menu, text);

        assertNotNull(item, "the menu of " + square + " has no \"" + text + "\"");

        return item;
    }

    /** The Autonomy menu's delete item, in the menu opened as the user opens it. */
    private static AbstractButton deleteItem() throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("autonomyMenu");

        field.setAccessible(true);

        final JMenu menu = (JMenu) field.get(ui);

        assertNotNull(menu, "precondition: the window has no Autonomy menu");

        Components.onTheEventThread(() ->
        {
            for (javax.swing.event.MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);

            return null;
        });

        AbstractButton item = Components.withText(menu, I18n.t("autosetup.ui.menuDeleteSetup"));

        assertNotNull(item, "the Autonomy menu has no \"" + I18n.t("autosetup.ui.menuDeleteSetup") + "\"");

        return item;
    }

    // ---------------------------------------------------------------- the questions

    private static Answering.Answer yes()
    {
        return Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0]));
    }

    private static Answering.Answer no()
    {
        return Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1]));
    }

    /** Presses the item, answers each question by the rule, and returns what was asked once the click has finished. */
    private static List<String> press(AbstractButton item, Function<String, Answering.Answer> rule) throws Exception
    {
        final CountDownLatch done = new CountDownLatch(1);

        try (Answering answering = Answering.start(rule))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    item.doClick();
                }
                finally
                {
                    done.countDown();
                }
            });

            assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS), "\"" + item.getText() + "\" did not finish;"
                + " it asked " + answering.asked());

            Components.settle();

            return answering.asked();
        }
    }

    /** The option the question on screen has as its default, which Enter would press. */
    private static Object defaultOptionOfTheQuestionOnScreen()
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!window.isShowing()) continue;

                JOptionPane pane = Components.find(window, JOptionPane.class, null);

                if (pane != null) return pane.getInitialValue();
            }

            return null;
        });
    }

    /**
     * Answers Pair with a Link...: lists what the combo box offers into `listed`, then picks the entry for this link
     * (the first when none is given) and presses OK.
     */
    private static Function<String, Answering.Answer> pickingFromTheList(final TileKey wanted, final List<String> listed)
    {
        return message ->
        {
            Components.onTheEventThread(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!window.isShowing()) continue;

                    JOptionPane pane = Components.find(window, JOptionPane.class, null);

                    if (pane == null) continue;

                    @SuppressWarnings("unchecked")
                    JComboBox<String> box = Components.find(pane, JComboBox.class, null);

                    if (box == null) continue;

                    for (int at = 0; at < box.getItemCount(); at++)
                    {
                        listed.add(box.getItemAt(at));

                        if (wanted != null && box.getItemAt(at).contains(wanted.toString())) box.setSelectedIndex(at);
                    }
                }

                return null;
            });

            return Answering.pressAt(0);
        };
    }

    private static boolean containsOne(List<String> listed, String key)
    {
        int found = 0;

        for (String each : listed)
        {
            if (each.contains(key)) found++;
        }

        return found == 1;
    }

    // ---------------------------------------------------------------- the links

    /** The links of the railway that pair with each other (portals of the kind the first is), the first being paired. */
    private static List<TileKey> linksOnTheRailway()
    {
        List<TileKey> links = new ArrayList<>();

        TileKey first = null;

        for (Map.Entry<TileKey, LayoutDiagramComponent> tile : session.getGraph().getTiles().entrySet())
        {
            LayoutDiagramComponent component = tile.getValue();

            if (component == null || !component.isLink()) continue;

            if (first == null && session.getStore().getPortalPartner(tile.getKey()) != null) first = tile.getKey();
        }

        assertNotNull(first, "precondition: no link is paired on the frozen railway");

        links.add(first);

        for (Map.Entry<TileKey, LayoutDiagramComponent> tile : session.getGraph().getTiles().entrySet())
        {
            LayoutDiagramComponent component = tile.getValue();

            if (component != null && component.isLink() && component.getType() == session.getGraph().getTiles().get(first).getType()
                && !tile.getKey().equals(first)) links.add(tile.getKey());
        }

        assertTrue(links.size() >= 3, "precondition: fewer than three links of one kind: " + links);

        return links;
    }

    private static Map<TileKey, TileKey> partnersOf(List<TileKey> links)
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (TileKey link : links) out.put(link, session.getStore().getPortalPartner(link));

        return out;
    }

    /** The configurations and the files are as they were: nothing was deleted. */
    private static void assertSetupStands(List<String> names)
    {
        assertEquals(session.getStore().getConfigurationNames(), names, "the configurations changed");

        assertSame(ui.getAutonomySession(), session, "the window dropped its setup");

        assertTrue(new File(sandbox.getFolder(), "config/autonomy/setup.json").isFile(), "the setup file is gone");

        assertTrue(model.hasAutoLayout(), "the railway built from the setup is gone");
    }
}
