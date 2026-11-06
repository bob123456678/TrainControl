package ui;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Mouse;
import support.RouteListDoors;

/**
 * What the track editor's doors have the main window say, said over the editor (OB-324): Open in Route Editor, and a
 * layout index that cannot be read at a page door.  The same doors in the main window say it over the main window.
 */
public class testTheEditorsDoorsAskOverThem
{
    private static final String PAGE = "1 - Main";

    private static final String ROUTE = "OB-324 route";

    private static final long PATIENCE_MS = 20000;

    private support.LayoutSandbox sandbox;
    private MarklinControlStation model;
    private TrainControlUI ui;
    private LayoutEditor editor;
    private Process holding;
    private File letGo;

    @BeforeMethod
    public void openTheWindow() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        assertTrue(Components.waitFor(() -> model.getLayoutList().size() == 5, PATIENCE_MS),
            "precondition: the railway does not have its five pages: " + model.getLayoutList());

        Components.settle();
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheWindow() throws Exception
    {
        try
        {
            letTheIndexGo();

            AskingWindows.closeEveryDialog();

            if (model != null && model.getRoute(ROUTE) != null) model.deleteRoute(ROUTE);

            if (editor != null)
            {
                final LayoutEditor closing = editor;

                Components.onTheEventThread(() ->
                {
                    closing.dispose();

                    return null;
                });
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                Components.onTheEventThread(() ->
                {
                    closing.dispose();

                    return null;
                });
            }

            if (model != null) model.stop();
        }
        finally
        {
            editor = null;
            ui = null;
            model = null;

            if (sandbox != null) sandbox.close();

            sandbox = null;
        }
    }

    private void openTheEditor() throws Exception
    {
        Components.onTheEventThread(() ->
        {
            ui.openLayoutEditor(PAGE, false, null);

            return null;
        });

        assertTrue(Components.waitFor(() ->
        {
            for (java.awt.Frame frame : java.awt.Frame.getFrames())
            {
                if (frame instanceof LayoutEditor && frame.isShowing()) editor = (LayoutEditor) frame;
            }

            return editor != null;
        }, PATIENCE_MS), "the track editor did not open");

        Components.settle();
    }

    /** A route for the page's first route tile, whose square the editor shows. */
    private LayoutLabel aRouteTileInTheEditor() throws Exception
    {
        final LayoutLabel[] tile = new LayoutLabel[1];

        assertTrue(Components.waitFor(() ->
        {
            tile[0] = Components.find(editor, LayoutLabel.class,
                label -> label.isShowing() && editor.getGridX(label) >= 0 && label.getComponent() != null
                    && label.getComponent().isRoute());

            return tile[0] != null;
        }, PATIENCE_MS), "precondition: the page has no route tile");

        int id = tile[0].getComponent().getAddress();

        if (model.getRoute(id) == null)
        {
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

            assertTrue(model.newRoute(ROUTE, id, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "precondition: the route was not added");
        }

        return tile[0];
    }

    /**
     * Open in Route Editor, refused while the track editor holds the diagram, says so over the track editor, with the
     * main window minimized too, and opens no route editor.
     *
     * MUTATION: `ui.editRoute(...)` alone in `LayoutEditorRightclickMenu`, and it hangs off the main window.
     */
    @Test
    public void testOpenInRouteEditorIsRefusedOverTheEditor() throws Exception
    {
        openTheEditor();

        LayoutLabel tile = aRouteTileInTheEditor();

        for (boolean minimized : new boolean[] {false, true})
        {
            AskingWindows.minimize(ui, minimized);

            JPopupMenu menu = Mouse.rightClick(tile, PATIENCE_MS);

            assertNotNull(menu, "the square's menu did not open");

            try (Answering answering = Answering.start(message -> null))
            {
                Mouse.choose(AskingWindows.itemOf(menu, I18n.t("layout.ui.openInRouteEditor")));

                AskingWindows.assertSaid(AskingWindows.onlyQuestion(answering, "Open in Route Editor said nothing"), editor,
                    I18n.t("autosetup.ui.menuEditorOpen"));
            }

            Components.settle();

            assertTrue(RouteListDoors.editorsShowing().isEmpty(), "a route editor opened over the open track editor");
        }

        AskingWindows.minimize(ui, false);
    }

    /**
     * Edit Route on the Routes tab, with the track editor open, says so over the main window.
     *
     * MUTATION: in `RunGates.refuseWhileEditorOpen`, the open editor whoever asked, and it hangs off the editor.
     */
    @Test
    public void testEditRouteFromTheMainWindowIsRefusedOverTheMainWindow() throws Exception
    {
        openTheEditor();

        String name = model.getRoute(aRouteTileInTheEditor().getComponent().getAddress()).getName();

        JPopupMenu menu = RouteListDoors.rightClick(ui, name);

        AbstractButton edit = Components.withText(menu, I18n.t("route.ui.menuEditRoute"));

        assertNotNull(edit, "precondition: the route's menu has no Edit Route");

        try (Answering answering = Answering.start(message -> null))
        {
            Mouse.choose((javax.swing.JMenuItem) edit);

            AskingWindows.assertSaid(AskingWindows.onlyQuestion(answering, "Edit Route said nothing"), ui,
                I18n.t("autosetup.ui.menuEditorOpen"));
        }
    }

    /** A page door on the strip under the Page heading, by what its icon says. */
    private AbstractButton onTheStrip(String key)
    {
        AbstractButton door = Components.find(editor, AbstractButton.class,
            each -> "toolBarButton".equals(each.getClientProperty("JButton.buttonType"))
                && I18n.t(key).equals(each.getToolTipText()));

        assertNotNull(door, "the strip has no " + key);

        return door;
    }

    /** Holds the layout index locked from a JVM of its own, as a sync client's lock does, so that reading it fails. */
    private void lockTheIndex() throws Exception
    {
        File index = new File(sandbox.getFolder(), "config/gleisbild.cs2");

        File ready = new File(sandbox.getFolder(), "ready");

        letGo = new File(sandbox.getFolder(), "stop");

        holding = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp",
            System.getProperty("java.class.path"), "support.HoldsALock", index.getPath(), ready.getPath(), letGo.getPath())
            .redirectErrorStream(true).redirectOutput(new File(sandbox.getFolder(), "holding.log")).start();

        assertTrue(Components.waitFor(ready::isFile, PATIENCE_MS), "precondition: the index could not be locked");
    }

    private void letTheIndexGo() throws Exception
    {
        if (holding == null) return;

        java.nio.file.Files.write(letGo.toPath(), new byte[] {1});

        holding.waitFor();

        holding = null;
    }

    /**
     * What a page door said of the locked index, its last word, over this window.  From an editor, stepping out reloads
     * the layout, which meets the lock and goes back to the station's, so the door finds it has no local layout.
     */
    private static void assertTheIndexMessageOver(Answering answering, Window over) throws Exception
    {
        Answering.Question said = awaitTheIndexMessage(answering);

        for (Answering.Question each : answering.questions())
        {
            assertSame(each.owner, over, "\"" + each.message + "\" hangs off the wrong window");
        }

        assertIsTheIndexMessage(said, over);
    }

    /** The door's last word, once it has said what it says of the locked index. */
    private static Answering.Question awaitTheIndexMessage(Answering answering)
    {
        final String saving = I18n.f("layout.ui.errorSavingLayoutWithMessage", "").trim();

        final String notLocal = I18n.t("layout.ui.errorEditingOnlySupportedForLocalFiles");

        assertTrue(Components.waitFor(() -> answering.questions().stream()
            .anyMatch(each -> each.message.startsWith(saving) || each.message.equals(notLocal)), PATIENCE_MS),
            "the door said " + answering.asked());

        Components.settle();

        List<Answering.Question> all = answering.questions();

        return all.get(all.size() - 1);
    }

    private static void assertIsTheIndexMessage(Answering.Question said, Window over)
    {
        final String notLocal = I18n.t("layout.ui.errorEditingOnlySupportedForLocalFiles");

        IOException why = LayoutDiagram.getUnreadableIndex();

        AskingWindows.assertSaid(said, over, said.message.equals(notLocal) ? notLocal
            : I18n.f("layout.ui.errorSavingLayoutWithMessage", why.getMessage()));
    }

    /** Delete Current Page from this button, answered Yes, on a page whose index cannot be read. */
    private void assertDeleteMeetsTheIndex(AbstractButton door, Window over) throws Exception
    {
        lockTheIndex();

        final String confirm = I18n.f("layout.ui.confirmDeleteLayoutPageAreYouSure", PAGE);

        try (Answering answering = Answering.start(message -> confirm.equals(message)
            ? Answering.press(I18n.t("ui.yes")) : null))
        {
            Components.press(door);

            assertTheIndexMessageOver(answering, over);
        }

        Components.settle();

        assertTrue(new File(sandbox.getFolder(), "config/gleisbilder/" + PAGE + ".cs2").isFile(),
            "the page went though the index could not be read");
    }

    /**
     * Delete Current Page from the track editor, with the index unreadable, says so over the editor.
     *
     * MUTATION: `this` for `over` at `settleAbsentPages` in `deleteConfirmedPage`, and it hangs off the main window.
     */
    @Test
    public void testAnUnreadableIndexIsSaidOverTheEditor() throws Exception
    {
        openTheEditor();

        assertDeleteMeetsTheIndex(onTheStrip("ui.main.toolbar.deleteCurrentPage"), editor);
    }

    /**
     * And from the Layouts menu, over the main window.
     *
     * MUTATION: `null` for the window in `showOnTheEventThread`, and it hangs off nothing.
     */
    @Test
    public void testAnUnreadableIndexFromTheLayoutsMenuIsSaidOverTheMainWindow() throws Exception
    {
        Components.select(pageList(), PAGE);

        Components.settle();

        Field item = TrainControlUI.class.getDeclaredField("deleteLayoutMenuItem");

        item.setAccessible(true);

        assertDeleteMeetsTheIndex((AbstractButton) item.get(ui), ui);
    }

    private javax.swing.JComboBox<?> pageList() throws Exception
    {
        Field list = TrainControlUI.class.getDeclaredField("LayoutList");

        list.setAccessible(true);

        return (javax.swing.JComboBox<?>) list.get(ui);
    }

    /**
     * Duplicate Current Page from the track editor, whose worker meets the unreadable index, says so over the editor.
     *
     * MUTATION: `this.ui` for `over` at `settleTheAbsentPages` in `duplicateOrRenameCurrentLayout`, and it hangs off the
     * main window.
     */
    @Test
    public void testAnUnreadableIndexAtDuplicateIsSaidOverTheEditor() throws Exception
    {
        openTheEditor();

        lockTheIndex();

        try (Answering answering = Answering.start(message -> null))
        {
            Components.press(onTheStrip("ui.main.toolbar.duplicateCurrentPage"));

            assertTheIndexMessageOver(answering, editor);
        }

        assertFalse(model.getLayoutList().stream().anyMatch(page -> page.endsWith(" copy")),
            "the page was made though the index could not be read: " + model.getLayoutList());
    }

    /**
     * Combine Linked Pages from the track editor, whose worker meets the unreadable index, says so over the editor.
     *
     * MUTATION: `this.ui` for the editor at `settleTheAbsentPages` in `combineOnAWorker`, and it hangs off the main
     * window.
     */
    @Test
    public void testAnUnreadableIndexAtCombineIsSaidOverTheEditor() throws Exception
    {
        openTheEditor();

        lockTheIndex();

        final String prompt = I18n.t("layout.ui.promptCombinedPageName");

        try (Answering answering = Answering.start(message -> message.startsWith(prompt)
            ? Answering.type("OB-324 combined") : Answering.pressAt(0)))
        {
            Components.press(Components.withText(editor, I18n.t("layout.ui.menuCombineLinkedPages")));

            assertTheIndexMessageOver(answering, editor);
        }

        assertFalse(model.getLayoutList().contains("OB-324 combined"),
            "the page was made though the index could not be read");
    }

    /**
     * Moving a page from the track editor, with the index unreadable, says so over the editor.
     *
     * MUTATION: `this.ui` for `from == null ? this.ui : from` at `settleTheAbsentPages` in `reorder`, and it hangs off the
     * main window.
     */
    @Test
    public void testAnUnreadableIndexAtAPageMoveIsSaidOverTheEditor() throws Exception
    {
        openTheEditor();

        lockTheIndex();

        try (Answering answering = Answering.start(message -> null))
        {
            Components.press(onTheStrip("layout.ui.tooltipMovePageDown"));

            assertTheIndexMessageOver(answering, editor);
        }
    }

    /**
     * An editor gone by the time its worker has something to say gives way to the main window: the window goes as the name
     * is answered, ahead of whatever the door posts next.
     *
     * MUTATION: `over` for `over.isShowing() ? over : this` in `showOnTheEventThread`, and it hangs off a window that is
     * not there.
     */
    @Test
    public void testAnEditorGoneBeforeItsMessageGivesWayToTheMainWindow() throws Exception
    {
        openTheEditor();

        lockTheIndex();

        final String prompt = I18n.t("layout.ui.promptCombinedPageName");

        try (Answering answering = Answering.start(message ->
        {
            if (!message.startsWith(prompt)) return Answering.pressAt(0);

            // Posted from a post, so that it lands behind the press of OK this answer posts
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> editor.setVisible(false)));

            return Answering.type("OB-324 combined");
        }))
        {
            Components.press(Components.withText(editor, I18n.t("layout.ui.menuCombineLinkedPages")));

            assertIsTheIndexMessage(awaitTheIndexMessage(answering), ui);
        }
    }
}
