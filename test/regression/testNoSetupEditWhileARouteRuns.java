package regression;

import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import static regression.testTheImportDoorReadsAnOldFile.itemCalled;
import static regression.testTheImportDoorReadsAnOldFile.logged;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import static regression.testTheImportDoorReadsAnOldFile.putTheFolderBack;

/**
 * No setup edit is made while a route runs with autonomy idle, at any door that refuses one while autonomy is busy (Adam,
 * 2026-09-28: *"There should be no setup edit possible during a run"*; asked whether those doors should also refuse while
 * a route runs: *"yes, refuse"*).  One question, `TrainControlUI.somethingIsRunning` as Return Home's place-at-home item
 * asks it (FR-129), and each door keeps its own way of refusing - greyed, hidden, or said at the click.
 *
 * On the live snapshot's window, with a route of one accessory command marked running as `MarklinRoute.execRoute` marks
 * it, so nothing is sent; and each door asked again at rest, where it does not refuse.
 *
 * @author Adam
 */
public class testNoSetupEditWhileARouteRuns
{
    private static final String PROBE = "setup edit probe route";

    /** The sentence a door that named only autonomy now shows while something else runs. */
    private static final String ANY_RUN = "ui.main.tooltip.placeAtHomesWhileRunning";

    /**
     * The track diagram's right-click menu leaves out Place, Remove, Edit Locomotive, Is Active, the Facing menu and
     * Autonomy Setup while a route runs, and offers them at rest - Start Autonomy, which is not a setup edit, both times.
     *
     * MUTATION: ask `isAutonomyBusy` alone for any of them again, and this fails naming it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsMenuOffersNoSetupEditWhileARouteRuns() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final Point standing = aStandingTrain(ui);
            final TileKey square = ui.getAutonomySession().getStationIndex().squareOf(standing.getName());
            final String train = standing.getCurrentLocomotive().getName();
            final String active = makeAnotherTrainActive(ui, train);

            final TrainControlUI window = ui;
            final javax.swing.JMenu[] facing = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> facing[0] = window.buildAutonomyFacingMenu(square));

            java.lang.reflect.Method label = Class.forName("org.traincontrol.gui.GraphLocAssign")
                .getDeclaredMethod("menuLabelFor", Point.class);

            label.setAccessible(true);

            Map<String, String> setupItems = new java.util.LinkedHashMap<>();

            setupItems.put("Place", I18n.f("layout.ui.menuPlaceLocomotive", active));
            setupItems.put("Remove", I18n.f("layout.ui.menuRemoveLocomotive", train));
            setupItems.put("Edit Locomotive", (String) label.invoke(null, standing));
            setupItems.put("Is Active", I18n.f("autolayout.ui.menuLocomotiveActive", train));
            setupItems.put("Autonomy Setup", I18n.t("autosetup.ui.menuAutonomySetup"));

            if (facing[0] != null) setupItems.put("Facing", facing[0].getText());

            String start = I18n.t("autolayout.ui.menuStartAutonomy");

            // AT REST
            List<String> rest = texts(diagramMenu(ui, square, standing));

            for (Map.Entry<String, String> item : setupItems.entrySet())
            {
                assertTrue(rest.contains(item.getValue()), "precondition: the right-click menu on " + square + " has no "
                    + item.getKey() + " at rest: " + rest);
            }

            assertTrue(rest.contains(start), "precondition: the right-click menu offers no Start Autonomy at rest: " + rest);

            // WHILE A ROUTE RUNS
            List<String> running;

            aRouteRuns(ui, true);

            try
            {
                running = texts(diagramMenu(ui, square, standing));
            }
            finally
            {
                aRouteRuns(ui, false);
            }

            for (Map.Entry<String, String> item : setupItems.entrySet())
            {
                assertFalse(running.contains(item.getValue()), "the right-click menu on " + square + " offers "
                    + item.getKey() + " while a route runs - Adam: \"There should be no setup edit possible during a"
                    + " run\": " + running);
            }

            assertTrue(running.contains(start), "the right-click menu left out Start Autonomy while a route runs, which is"
                + " not a setup edit: " + running);
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * Items the diagram offered at rest refuse when clicked once a route runs, and say why - Place, Remove, and a setup
     * item - and the setup item works at rest.
     *
     * MUTATION: take the refusal out of the right-click Place or Remove, or let `refusedWhileRunning` in either menu ask
     * autonomy alone, and this fails naming it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testItemsOfferedAtRestRefuseOnceARouteRuns() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final Answering answering = new Answering();

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final AutonomySession session = ui.getAutonomySession();
            final Point standing = aStandingTrain(ui);
            final TileKey square = session.getStationIndex().squareOf(standing.getName());
            final String train = standing.getCurrentLocomotive().getName();
            final String active = makeAnotherTrainActive(ui, train);

            // OPENED AT REST
            final javax.swing.JPopupMenu diagram = diagramMenu(ui, square, standing);
            final TrainControlUI window = ui;
            final javax.swing.JPopupMenu[] deep = new javax.swing.JPopupMenu[1];

            final JMenuItem place = itemCalled(diagram, I18n.f("layout.ui.menuPlaceLocomotive", active));
            final JMenuItem remove = itemCalled(diagram, I18n.f("layout.ui.menuRemoveLocomotive", train));

            assertTrue(place != null && remove != null, "precondition: the menu on " + square + " lacks Place or Remove");

            // A SETUP ITEM THAT IS LIVE AT REST: whether autonomy may choose a station, on the first that offers it
            JMenuItem live = null;
            TileKey other = null;

            List<TileKey> stations = new ArrayList<>();

            for (TileKey key : session.getStore().getNamedTiles())
            {
                if (session.getStore().isStation(key)) stations.add(key);
            }

            stations.sort(java.util.Comparator.comparing(TileKey::toString));

            for (TileKey candidate : stations)
            {
                if (live != null) break;

                SwingUtilities.invokeAndWait(() -> deep[0] = window.buildAutonomyTileMenu(candidate));

                JMenuItem item = deep[0] == null ? null : itemCalled(deep[0], I18n.t("autosetup.ui.menuAutoDestination"));

                if (item != null && item.isEnabled())
                {
                    live = item;
                    other = candidate;
                }
            }

            assertNotNull(live, "precondition: no station's setup menu offers whether autonomy may choose it, live");

            final JMenuItem choose = live;
            final TileKey chosen = other;

            final Layout railway = ui.getModel().getAutoLayout();
            final org.traincontrol.base.Locomotive placing = ui.getModel().getLocByName(active);
            final Point wasAt = railway.getLocomotiveLocation(placing);
            final String setupBefore = session.isAutoDestination(chosen) + " " + setupOf(session, chosen);
            final String refusal = I18n.t("autolayout.errorCannotEditWhileRunning");

            answering.start();

            // THEN A ROUTE RUNS
            aRouteRuns(ui, true);

            try
            {
                Object[][] clicks = {{"Place", place}, {"Remove", remove},
                    {"a setup item (" + choose.getText() + ")", choose}};

                for (Object[] click : clicks)
                {
                    final JMenuItem item = (JMenuItem) click[1];

                    int from = answering.count();

                    SwingUtilities.invokeAndWait(item::doClick);

                    settle();

                    List<String> said = answering.since(from);

                    assertTrue(said.contains(refusal), click[0] + ", offered at rest and clicked while a route runs, did"
                        + " not say why it did nothing: " + said);
                }

                assertEquals(railway.getLocomotiveLocation(placing), wasAt, "Place, clicked while a route runs, moved "
                    + active);

                assertEquals(railway.getLocomotiveLocation(standing.getCurrentLocomotive() == null
                    ? ui.getModel().getLocByName(train) : standing.getCurrentLocomotive()), standing, "Remove, clicked"
                    + " while a route runs, took " + train + " off " + square);

                assertEquals(session.isAutoDestination(chosen) + " " + setupOf(session, chosen), setupBefore, "a setup"
                    + " item, clicked while a route runs, wrote the setup of " + chosen);
            }
            finally
            {
                aRouteRuns(ui, false);
            }

            // AT REST, the setup item does what it says - from a menu opened now, since the refused click left the old
            // menu's tick turned
            SwingUtilities.invokeAndWait(() -> deep[0] = window.buildAutonomyTileMenu(chosen));

            final JMenuItem again = itemCalled(deep[0], I18n.t("autosetup.ui.menuAutoDestination"));

            int from = answering.count();

            SwingUtilities.invokeAndWait(again::doClick);

            settle();

            assertFalse(answering.since(from).contains(refusal), "a setup item refused at rest: " + answering.since(from));

            assertNotEquals(session.isAutoDestination(chosen) + " " + setupOf(session, chosen), setupBefore,
                "precondition: the setup item wrote nothing at rest, so its refusal while a route runs shows nothing");
        }
        finally
        {
            answering.stop();

            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * Delete over a station on the diagram takes nothing off while a route runs, and says why naming the route; at rest it
     * takes the train off.
     *
     * MUTATION: let the diagram's keys ask `isAutonomyBusy` alone again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsKeysWaitForARoute() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final Point standing = aStandingTrain(ui);
            final TileKey square = ui.getAutonomySession().getStationIndex().squareOf(standing.getName());
            final org.traincontrol.base.Locomotive train = standing.getCurrentLocomotive();
            final Layout railway = ui.getModel().getAutoLayout();

            aRouteRuns(ui, true);

            try
            {
                assertEquals(pressOver(ui, square, java.awt.event.KeyEvent.VK_DELETE), Boolean.TRUE, "precondition:"
                    + " Delete over " + square + " was not taken");

                assertEquals(railway.getLocomotiveLocation(train), standing, "Delete over " + square + " took "
                    + train.getName() + " off while a route runs");

                assertTrue(logged().contains(I18n.t(ANY_RUN)), "Delete over " + square + ", refused while a route runs,"
                    + " did not say a route is why");
            }
            finally
            {
                aRouteRuns(ui, false);
            }

            pressOver(ui, square, java.awt.event.KeyEvent.VK_DELETE);

            assertNull(railway.getLocomotiveLocation(train), "precondition: Delete over " + square + " left "
                + train.getName() + " there at rest");
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * Neither editor opens while a route runs - the Edit door says why, and Open Full Editor is greyed with it - and the
     * track editor's Autonomy tab and its Save refuse; at rest the autonomy editor may open and the tab is live.
     *
     * MUTATION: let `whyAutonomyEditorCannotOpen`, `openLayoutEditor`, the editor's mode tab or its Save ask autonomy
     * alone again, and this fails naming it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsWaitForARoute() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final Answering answering = new Answering();

        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final TrainControlUI window = ui;
            final String refusal = I18n.t("autolayout.errorCannotEditWhileRunning");
            final org.traincontrol.base.LayoutDiagram page = ui.getModel().getLayout("1 - Main");

            assertNotNull(page, "precondition: no page 1 - Main");

            assertNull(ui.whyAutonomyEditorCannotOpen(), "precondition: the autonomy editor may not open at rest");

            assertTrue(modeTab(ui, page, editor).isEnabled(), "precondition: the track editor's Autonomy tab is greyed"
                + " at rest");

            closeTheProbe(editor);

            answering.start();

            aRouteRuns(ui, true);

            try
            {
                assertEquals(ui.whyAutonomyEditorCannotOpen(), "autolayout.errorCannotEditWhileRunning", "the autonomy"
                    + " editor may open while a route runs, and Open Full Editor is live");

                for (final Boolean autonomy : new Boolean[] {Boolean.TRUE, Boolean.FALSE})
                {
                    int from = answering.count();

                    SwingUtilities.invokeAndWait(() -> window.openLayoutEditor(null, autonomy, null));

                    settle();

                    assertFalse(ui.isLayoutEditorOpen(), "the " + (autonomy ? "autonomy" : "track") + " editor opened"
                        + " while a route runs");

                    assertTrue(answering.since(from).contains(refusal), "the Edit door for the "
                        + (autonomy ? "autonomy" : "track") + " editor did not say why it stays shut while a route runs: "
                        + answering.since(from));
                }

                javax.swing.AbstractButton tab = modeTab(ui, page, editor);

                assertFalse(tab.isEnabled(), "the track editor's Autonomy tab is live while a route runs");

                assertTrue(String.valueOf(tab.getToolTipText()).contains(refusal), "the track editor's Autonomy tab,"
                    + " greyed while a route runs, does not say why: " + tab.getToolTipText());

                // ITS SAVE, pressed while a route runs
                final java.lang.reflect.Method save = org.traincontrol.gui.LayoutEditor.class
                    .getDeclaredMethod("saveButtonActionPerformed", java.awt.event.ActionEvent.class);

                save.setAccessible(true);

                int from = answering.count();

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        save.invoke(editor[0], (Object) null);
                    }
                    catch (ReflectiveOperationException failed)
                    {
                        throw new IllegalStateException(failed);
                    }
                });

                settle();

                assertTrue(answering.since(from).contains(refusal), "the track editor's Save did not refuse while a"
                    + " route runs: " + answering.since(from));
            }
            finally
            {
                aRouteRuns(ui, false);
            }
        }
        finally
        {
            answering.stop();

            if (editor[0] != null)
            {
                final org.traincontrol.gui.LayoutEditor closing = editor[0];

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * The Autonomy and Layout menus grey what changes the setup or the diagram while a route runs, saying a route is one
     * reason, and give it back at rest.
     *
     * MUTATION: let either menu's guard ask autonomy alone again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheMenusAreGreyedWhileARouteRuns() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        String[] layoutItems = {"chooseLocalDataFolderMenuItem", "modifyLocalLayoutMenu", "editPageMenu"};

        String[] autonomyItems = {I18n.t("autosetup.ui.btnManage"), I18n.t("autosetup.ui.btnExcludePage")};

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            String why = I18n.t(ANY_RUN);

            Map<String, JMenuItem> autonomyAtRest = autonomyMenuItems(ui);
            Map<String, Object[]> layoutAtRest = layoutMenuAsOpened(ui, layoutItems);

            for (String name : autonomyItems)
            {
                assertTrue(autonomyAtRest.get(name) != null && autonomyAtRest.get(name).isEnabled(), "precondition: "
                    + name + " is greyed or missing on the Autonomy menu at rest");
            }

            for (String name : layoutItems)
            {
                assertTrue((Boolean) layoutAtRest.get(name)[0], "precondition: " + name + " is greyed at rest");
            }

            aRouteRuns(ui, true);

            try
            {
                Map<String, JMenuItem> autonomy = autonomyMenuItems(ui);

                for (String name : autonomyItems)
                {
                    assertFalse(autonomy.get(name).isEnabled(), name + " is live on the Autonomy menu while a route runs");

                    assertTrue(String.valueOf(autonomy.get(name).getToolTipText()).contains(why), name + ", greyed"
                        + " while a route runs, does not say a route is why: " + autonomy.get(name).getToolTipText());
                }

                Map<String, Object[]> layout = layoutMenuAsOpened(ui, layoutItems);

                for (String name : layoutItems)
                {
                    assertFalse((Boolean) layout.get(name)[0], name + " is live on the Layout menu while a route runs");

                    assertTrue(String.valueOf(layout.get(name)[1]).contains(why), name + ", greyed while a route runs,"
                        + " does not say a route is why: " + layout.get(name)[1]);
                }
            }
            finally
            {
                aRouteRuns(ui, false);
            }

            Map<String, JMenuItem> autonomyAfter = autonomyMenuItems(ui);
            Map<String, Object[]> layoutAfter = layoutMenuAsOpened(ui, layoutItems);

            for (String name : autonomyItems)
            {
                assertTrue(autonomyAfter.get(name).isEnabled(), name + " stayed greyed once the route stopped");
            }

            for (String name : layoutItems)
            {
                assertEquals(layoutAfter.get(name)[0], layoutAtRest.get(name)[0], name + " did not come back once the"
                    + " route stopped");

                assertEquals(layoutAfter.get(name)[1], layoutAtRest.get(name)[1], name + " kept the run's tooltip once"
                    + " the route stopped");
            }
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * The configuration doors, Import, Delete Everything, a page ticked out, and the page doors refuse while a route runs,
     * saying why before anything is asked, and none of them changes the setup or the pages; at rest they ask their own
     * questions instead.
     *
     * MUTATION: let any of them ask autonomy alone again, and this fails naming it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheSetupAndPageDoorsWaitForARoute() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final Answering answering = new Answering();

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final TrainControlUI window = ui;
            final AutonomySession session = ui.getAutonomySession();
            final Object panel = ui.getAutonomyViewerPanel();
            final String refusal = I18n.t("autolayout.errorCannotEditWhileRunning");
            final String anyRun = I18n.t(ANY_RUN);

            // A PAGE'S TICK, from the menu opened at rest
            JMenuItem pages = autonomyMenuItems(ui).get(I18n.t("autosetup.ui.btnExcludePage"));

            assertTrue(pages instanceof javax.swing.JMenu && ((javax.swing.JMenu) pages).getItemCount() > 0,
                "precondition: the Autonomy menu has no page to tick");

            final JMenuItem tick = ((javax.swing.JMenu) pages).getItem(0);

            final Object autonomyMenu = autonomyMenu(ui);

            final java.lang.reflect.Method deleteEverything = autonomyMenu.getClass()
                .getDeclaredMethod("deleteEverything", AutonomySession.class);

            deleteEverything.setAccessible(true);

            // EACH DOOR, by name, with the sentence it refuses with
            Map<String, Object[]> doors = new java.util.LinkedHashMap<>();

            for (String door : new String[] {"duplicate", "rename", "initialize", "delete"})
            {
                doors.put("Autonomy: " + door, new Object[] {refusal, (Runnable) () -> call(panel, door)});
            }

            doors.put("Autonomy: Import", new Object[] {anyRun, (Runnable) () -> call(panel, "importConfiguration")});
            doors.put("Autonomy: Delete Everything", new Object[] {refusal, (Runnable) () -> invoke(deleteEverything,
                autonomyMenu, session)});
            doors.put("Autonomy: a page's tick", new Object[] {refusal, (Runnable) tick::doClick});
            doors.put("Layout: Delete Page", new Object[] {anyRun, (Runnable) () -> callUi(window,
                "deleteLayoutMenuItemActionPerformed", new Class<?>[] {java.awt.event.ActionEvent.class}, (Object) null)});
            doors.put("Layout: Rename Page", new Object[] {anyRun, (Runnable) () -> callUi(window,
                "duplicateOrRenameCurrentLayout", new Class<?>[] {String.class, boolean.class, boolean.class, boolean.class},
                "renamed while a route runs", true, false, false)});
            doors.put("Layout: Duplicate Page", new Object[] {anyRun, (Runnable) () -> callUi(window,
                "duplicateOrRenameCurrentLayout", new Class<?>[] {String.class, boolean.class, boolean.class, boolean.class},
                "copied while a route runs", false, true, false)});
            doors.put("Layout: Combine Linked Pages", new Object[] {anyRun, (Runnable) () -> callUi(window,
                "combineLinkedPages", new Class<?>[0])});
            doors.put("Layout: Switch to the Central Station's Layout", new Object[] {anyRun, (Runnable) () -> callUi(
                window, "switchCSLayoutMenuItemActionPerformed", new Class<?>[] {java.awt.event.ActionEvent.class},
                (Object) null)});
            doors.put("Layout: Choose Local Data Folder", new Object[] {anyRun, (Runnable) () -> callUi(window,
                "chooseLocalDataFolderMenuItemActionPerformed", new Class<?>[] {java.awt.event.ActionEvent.class},
                (Object) null)});
            doors.put("Layout: Initialize Local Layout", new Object[] {anyRun, (Runnable) () -> callUi(window,
                "initializeLocalLayoutMenuItemActionPerformed", new Class<?>[] {java.awt.event.ActionEvent.class},
                (Object) null)});

            final String configurations = String.valueOf(session.getStore().getConfigurationNames()) + " "
                + session.getStore().getActiveConfiguration() + " " + session.getStore().getExcludedPages();

            final List<String> pagesBefore = new ArrayList<>(ui.getModel().getLayoutList());

            answering.start();

            aRouteRuns(ui, true);

            try
            {
                for (Map.Entry<String, Object[]> door : doors.entrySet())
                {
                    int from = answering.count();

                    SwingUtilities.invokeAndWait((Runnable) door.getValue()[1]);

                    settle();

                    List<String> said = answering.since(from);

                    assertEquals(said, Collections.singletonList((String) door.getValue()[0]), door.getKey()
                        + " did not refuse, before asking anything, while a route runs - Adam: \"There should be no"
                        + " setup edit possible during a run\"");
                }

                assertEquals(String.valueOf(session.getStore().getConfigurationNames()) + " "
                    + session.getStore().getActiveConfiguration() + " " + session.getStore().getExcludedPages(),
                    configurations, "a door changed the configurations or the pages autonomy uses while a route runs");

                assertEquals(new ArrayList<>(ui.getModel().getLayoutList()), pagesBefore, "a page door changed the pages"
                    + " while a route runs");
            }
            finally
            {
                aRouteRuns(ui, false);
            }

            // AT REST, the doors that only ask first ask - answered No or Cancel by the answering thread
            for (String name : new String[] {"Autonomy: duplicate", "Autonomy: rename", "Layout: Delete Page"})
            {
                int from = answering.count();

                SwingUtilities.invokeAndWait((Runnable) doors.get(name)[1]);

                settle();

                List<String> said = answering.since(from);

                assertFalse(said.contains(refusal) || said.contains(anyRun), name + " refused at rest: " + said);

                assertFalse(said.isEmpty(), "precondition: " + name + " asked nothing at rest");
            }
        }
        finally
        {
            answering.stop();

            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * The autonomy editor's gestures write nothing once a route runs, and say why on the message strip: the left-click that
     * steps a square's arrows, the One-Way tool's two clicks, the keyboard shortcuts over a square (Control+H, S, E, N and
     * B), dragging a station's label, and the page's Leave Out box.  The editor was opened at rest, as the Edit door allows,
     * and the route begins after.
     *
     * MUTATION: take `heldByARun` out of `tileClicked`, of one of the five shortcuts, of `moveCaption` or of
     * `setPageExcluded`, and this fails naming the gesture.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsGesturesWaitForARoute() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final Answering answering = new Answering();

        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final TrainControlUI window = ui;
            final AutonomySession session = ui.getAutonomySession();
            final org.traincontrol.base.LayoutDiagram page = ui.getModel().getLayout("1 - Main");
            final String refusal = I18n.t("autolayout.errorCannotEditWhileRunning");

            assertNotNull(page, "precondition: no page 1 - Main");

            // OPENED AT REST
            SwingUtilities.invokeAndWait(() ->
            {
                editor[0] = new org.traincontrol.gui.LayoutEditor(page, 30, window, 0);

                editor[0].render();

                editor[0].setAutonomyMode(session);
            });

            settle();

            final org.traincontrol.gui.AutonomyEditorPanel panel = editor[0].getAutonomyPanel();

            assertNotNull(panel, "precondition: the editor did not open in autonomy mode");

            // A PLAIN TRACK SQUARE WITH ONE ROAD, and the two squares a run through it is drawn between
            final TileKey[] run = aRunThroughOneSquare(session, page.getName());
            final TileKey square = run[1];

            // A STATION, and a label of its that may be dragged to another square
            final TileKey station = aStationOn(session, page.getName());
            final TileKey[] drag = aLabelThatMoves(session, panel, page);

            final org.json.JSONObject before = session.snapshotSetup();

            final java.lang.reflect.Field button = org.traincontrol.gui.AutonomyEditorPanel.class
                .getDeclaredField("oneWayButton");

            button.setAccessible(true);

            final java.lang.reflect.Field excludeBox = org.traincontrol.gui.AutonomyEditorPanel.class
                .getDeclaredField("excludePage");

            excludeBox.setAccessible(true);

            answering.start();

            aRouteRuns(ui, true);

            try
            {
                // THE ARROW CLICK, with no tool armed
                String said = saidBy(panel, () -> panel.tileClicked(square, page.getComponent(square.getX(), square.getY()),
                    false));

                assertTrue(session.snapshotSetup().similar(before), "a left click on " + square + " in the autonomy editor"
                    + " changed the setup while a route runs");

                assertTrue(said.contains(refusal), "the arrow click, while a route runs, did not say why on the message"
                    + " strip: " + said);

                // THE ONE-WAY TOOL'S TWO CLICKS, its question answered with the first way round
                SwingUtilities.invokeAndWait(() -> ((javax.swing.AbstractButton) fieldOf(button, panel)).doClick());

                answering.pickFirstOption(true);

                for (final TileKey end : new TileKey[] {run[0], run[2]})
                {
                    said = saidBy(panel, () -> panel.tileClicked(end, page.getComponent(end.getX(), end.getY()), false));
                }

                answering.pickFirstOption(false);

                assertTrue(session.snapshotSetup().similar(before), "the One-Way tool, used in the autonomy editor while a"
                    + " route runs, changed the setup");

                assertTrue(said.contains(refusal), "the One-Way tool, while a route runs, did not say why on the message"
                    + " strip: " + said);

                // THE KEYS over a station
                Map<String, Runnable> keys = new java.util.LinkedHashMap<>();

                keys.put("Control+H", () -> panel.promptHomeFor(station));
                keys.put("Control+S", () -> panel.promptNameFor(station));
                keys.put("Control+E", () -> panel.promptLengthFor(station));
                keys.put("Control+N", () -> panel.showStationNameFor(station));
                keys.put("Control+B", () -> panel.promptMaxTrainLengthFor(station));

                for (Map.Entry<String, Runnable> key : keys.entrySet())
                {
                    said = saidBy(panel, key.getValue());

                    assertTrue(said.contains(refusal), key.getKey() + " over " + station + ", while a route runs, did not"
                        + " say why on the message strip: " + said);

                    assertTrue(session.snapshotSetup().similar(before), key.getKey() + " over " + station + " changed the"
                        + " setup while a route runs");
                }

                // A STATION'S LABEL DRAGGED to another square
                final boolean[] moved = new boolean[1];

                said = saidBy(panel, () -> moved[0] = panel.moveCaption(drag[0], drag[1],
                    page.getComponent(drag[1].getX(), drag[1].getY())));

                assertFalse(moved[0], "a station's label was dragged to another square while a route runs");

                assertTrue(said.contains(refusal), "dragging a label, while a route runs, did not say why on the message"
                    + " strip: " + said);

                assertTrue(session.snapshotSetup().similar(before), "dragging a label changed the setup while a route runs");

                // THE PAGE'S LEAVE OUT BOX
                said = saidBy(panel, () -> ((javax.swing.AbstractButton) fieldOf(excludeBox, panel)).doClick());

                assertTrue(said.contains(refusal), "the Leave This Page Out box, while a route runs, did not say why on the"
                    + " message strip: " + said);

                assertFalse(((javax.swing.AbstractButton) fieldOf(excludeBox, panel)).isSelected(), "the Leave This Page"
                    + " Out box stays ticked though the setup was not changed");

                assertTrue(session.snapshotSetup().similar(before), "the Leave This Page Out box changed the setup while a"
                    + " route runs");
            }
            finally
            {
                answering.pickFirstOption(false);

                aRouteRuns(ui, false);
            }

            // AT REST the arrow click writes, so its refusal above hides something
            SwingUtilities.invokeAndWait(() -> ((javax.swing.AbstractButton) fieldOf(button, panel)).doClick());

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(square, page.getComponent(square.getX(), square.getY()),
                false));

            settle();

            assertFalse(session.snapshotSetup().similar(before), "precondition: the arrow click wrote nothing at rest, so"
                + " its refusal while a route runs shows nothing");

            // AND THE DRAG moves the label at rest
            final boolean[] movedAtRest = new boolean[1];

            SwingUtilities.invokeAndWait(() -> movedAtRest[0] = panel.moveCaption(drag[0], drag[1],
                page.getComponent(drag[1].getX(), drag[1].getY())));

            assertTrue(movedAtRest[0], "precondition: the label would not move at rest, so its refusal while a route runs"
                + " shows nothing");
        }
        finally
        {
            answering.stop();

            if (editor[0] != null)
            {
                final org.traincontrol.gui.LayoutEditor closing = editor[0];

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * What the editor's message strip says after a gesture: it is cleared to a dash first, so only the gesture's own
     * words are read.
     */
    private static String saidBy(final org.traincontrol.gui.AutonomyEditorPanel panel, final Runnable gesture)
        throws Exception
    {
        final java.lang.reflect.Method say = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("say",
            javax.swing.JLabel.class, String.class);

        say.setAccessible(true);

        final java.lang.reflect.Field hint = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("hint");

        hint.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                say.invoke(panel, hint.get(panel), "-");
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }

            gesture.run();
        });

        settle();

        StringBuilder out = new StringBuilder(String.valueOf(((javax.swing.JLabel) hint.get(panel)).getText()));

        java.lang.reflect.Field banner = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("messageBanner");

        banner.setAccessible(true);

        Object strip = banner.get(panel);

        if (strip != null)
        {
            java.lang.reflect.Field saying = strip.getClass().getDeclaredField("saying");

            saying.setAccessible(true);

            out.append(" | ").append(saying.get(strip));
        }

        return out.toString();
    }

    private static Object fieldOf(java.lang.reflect.Field field, Object on)
    {
        try
        {
            return field.get(on);
        }
        catch (IllegalAccessException failed)
        {
            throw new IllegalStateException(failed);
        }
    }

    /** A station of the setup on a page. */
    private static TileKey aStationOn(AutonomySession session, String page)
    {
        List<TileKey> stations = new ArrayList<>();

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (page.equals(key.getPage()) && session.getStore().isStation(key)) stations.add(key);
        }

        stations.sort(java.util.Comparator.comparing(TileKey::toString));

        if (stations.isEmpty()) throw new SkipException("no station on " + page);

        return stations.get(0);
    }

    /**
     * A station's label and a square it may be dragged to, by the editor's own rule.
     *
     * @return the square the label is on, and the square it may go to
     */
    private static TileKey[] aLabelThatMoves(AutonomySession session, org.traincontrol.gui.AutonomyEditorPanel panel,
        org.traincontrol.base.LayoutDiagram page)
    {
        List<TileKey> tiles = new ArrayList<>(session.getGraph().getTiles().keySet());

        tiles.sort(java.util.Comparator.comparing(TileKey::toString));

        for (TileKey from : tiles)
        {
            if (!page.getName().equals(from.getPage()) || session.getCaptionTarget(from) == null) continue;

            for (TileKey to : tiles)
            {
                if (page.getName().equals(to.getPage()) && !to.equals(from)
                    && panel.canDropCaption(from, to, page.getComponent(to.getX(), to.getY())))
                {
                    return new TileKey[] {from, to};
                }
            }
        }

        throw new SkipException("no station label on " + page.getName() + " that may be dragged anywhere");
    }

    /**
     * Three squares in a row of plain track on a page, the middle with one road, drawn so that a one-way run between the
     * outer two changes the middle - found by drawing it and putting the setup back.
     *
     * @param session the setup
     * @param page the page
     * @return the squares, outer, middle, outer
     */
    private static TileKey[] aRunThroughOneSquare(AutonomySession session, String page)
    {
        List<TileKey> squares = new ArrayList<>();

        for (TileKey tile : session.getGraph().getTiles().keySet())
        {
            if (page.equals(tile.getPage())) squares.add(tile);
        }

        squares.sort(java.util.Comparator.comparing(TileKey::toString));

        for (TileKey a : squares)
        {
            for (TileKey b : squares)
            {
                if (Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) != 2) continue;

                List<TileKey> path = session.getGraph().findUndirectedPath(a, b);

                if (path == null || path.size() != 3) continue;

                TileKey middle = path.get(1);

                if (session.getRoutes(middle).size() != 1 || !session.canCarryDirection(middle)) continue;

                org.json.JSONObject was = session.snapshotSetup();

                int changed = session.setOneWayRun(a, b);

                boolean wrote = !session.snapshotSetup().similar(was);

                session.restoreSetup(was);

                if (changed > 0 && wrote) return new TileKey[] {a, middle, b};
            }
        }

        throw new SkipException("no run of three plain squares on " + page);
    }

    /**
     * The Auto tab's settings, the route activations, the timetable's doors and a train's direction change leave the
     * setup alone while a route runs with autonomy idle, and say why with the sentence that names a route (C3; Adam,
     * 2026-09-28: *"There should be no setup edit possible during a run"*; of a route: *"yes, refuse"*).  The timetable's
     * right-click menu greys its items with that sentence.
     *
     * MUTATION: ask `isAutonomyBusy` alone again in any of them, and this fails naming it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheAutoTabAndTheTimetableWaitForARoute() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final Answering answering = new Answering();

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final TrainControlUI window = ui;
            final AutonomySession session = ui.getAutonomySession();
            final Layout railway = ui.getModel().getAutoLayout();
            final String anyRun = I18n.t(ANY_RUN);

            final java.lang.reflect.Field sliderField = TrainControlUI.class.getDeclaredField("maxActiveTrains");

            sliderField.setAccessible(true);

            final javax.swing.JSlider trains = (javax.swing.JSlider) sliderField.get(ui);

            final int trainsWas = railway.getMaxActiveTrains();
            final boolean routesWas = railway.isActivateRoutes();
            final boolean captureWas = railway.isTimetableCapture();
            final List<Integer> activationsWas = new ArrayList<>(railway.getActivateRouteIDs());
            final org.traincontrol.automation.Layout.PathPreference ruleWas = railway.getPathPreference();

            // THE DOORS, each with what it must leave alone
            Map<String, Runnable> doors = new java.util.LinkedHashMap<>();

            doors.put("the trains-at-once slider", () ->
            {
                trains.setValue(trainsWas == trains.getMaximum() ? trainsWas - 1 : trainsWas + 1);

                callUi(window, "maxActiveTrainsMouseReleased", new Class<?>[] {java.awt.event.MouseEvent.class}, (Object) null);
            });

            doors.put("the routing rule", () ->
            {
                javax.swing.JComboBox<?> rules = (javax.swing.JComboBox<?>) fieldOf("algorithmType", window);

                rules.setSelectedIndex((rules.getSelectedIndex() + 1) % rules.getItemCount());
            });

            doors.put("the specified routes box", () -> callUi(window, "toggleSpecifiedRoutesMouseReleased",
                new Class<?>[] {java.awt.event.MouseEvent.class}, (Object) null));

            doors.put("the route list", () ->
            {
                javax.swing.JList<?> list = (javax.swing.JList<?>) fieldOf("autoRouteList", window);

                if (list.getModel().getSize() > 0) list.setSelectedIndex(0);

                callUi(window, "autoRouteListMouseReleased", new Class<?>[] {java.awt.event.MouseEvent.class}, (Object) null);
            });

            doors.put("the timetable capture box", () -> callUi(window, "timetableCaptureActionPerformed",
                new Class<?>[] {java.awt.event.ActionEvent.class}, (Object) null));

            doors.put("Clear Timetable", window::clearTimetable);
            doors.put("Restart Timetable", window::restartTimetable);
            doors.put("Delete Entry", () -> window.deleteTimetableEntry(null));
            doors.put("Change Delay", () -> window.updateTimetableDelay(null));

            answering.start();

            aRouteRuns(ui, true);

            try
            {
                for (Map.Entry<String, Runnable> door : doors.entrySet())
                {
                    int from = answering.count();

                    SwingUtilities.invokeAndWait(door.getValue());

                    settle();

                    List<String> said = answering.since(from);

                    assertEquals(said, Collections.singletonList(anyRun), door.getKey() + " did not refuse, before asking"
                        + " anything, with the sentence that names a route, while a route runs");
                }

                assertEquals(railway.getMaxActiveTrains(), trainsWas, "the trains-at-once slider wrote the setup while a"
                    + " route runs");

                assertEquals(railway.getPathPreference(), ruleWas, "the routing rule changed while a route runs");

                assertEquals(railway.isActivateRoutes(), routesWas, "the specified routes box wrote the setup while a route"
                    + " runs");

                assertEquals(railway.getActivateRouteIDs(), activationsWas, "the route list wrote the setup while a route"
                    + " runs");

                assertEquals(railway.isTimetableCapture(), captureWas, "the capture box wrote the setup while a route runs");

                // THE TIMETABLE'S RIGHT-CLICK MENU: every item greyed, with that sentence
                for (javax.swing.JMenuItem item : timetableMenuItems(window))
                {
                    assertFalse(item.isEnabled(), item.getText() + " is live in the timetable's menu while a route runs");

                    assertEquals(item.getToolTipText(), anyRun, item.getText() + " is greyed in the timetable's menu"
                        + " without saying a route is why");
                }

                // A TRAIN TURNED ROUND BY HAND is not followed into the setup
                Point standing = aTrainOnASquareWithTwoFacings(window, session);

                final org.json.JSONObject before = session.snapshotSetup();
                final java.lang.reflect.Method follow = TrainControlUI.class.getDeclaredMethod("followTheTurn", String.class);

                follow.setAccessible(true);

                final Object[] followed = new Object[1];

                final String train = standing.getCurrentLocomotive().getName();

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        followed[0] = follow.invoke(window, train);
                    }
                    catch (ReflectiveOperationException failed)
                    {
                        throw new IllegalStateException(failed);
                    }
                });

                assertNull(followed[0], "a train turned round by hand was followed into the setup while a route runs");

                assertTrue(session.snapshotSetup().similar(before), "a train turned round by hand changed the setup while a"
                    + " route runs");
            }
            finally
            {
                aRouteRuns(ui, false);
            }

            // AT REST the menu is live
            for (javax.swing.JMenuItem item : timetableMenuItems(window))
            {
                assertTrue(item.isEnabled(), item.getText() + " is greyed in the timetable's menu at rest");
            }

            // AND THE TURN IS FOLLOWED at rest, so its refusal above hides something
            final org.json.JSONObject unturned = session.snapshotSetup();
            final String trainTurned = aTrainOnASquareWithTwoFacings(window, session).getCurrentLocomotive().getName();
            final java.lang.reflect.Method turn = TrainControlUI.class.getDeclaredMethod("followTheTurn", String.class);

            turn.setAccessible(true);

            final Object[] turned = new Object[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    turned[0] = turn.invoke(window, trainTurned);
                }
                catch (ReflectiveOperationException failed)
                {
                    throw new IllegalStateException(failed);
                }
            });

            assertNotNull(turned[0], "precondition: a turn was not followed at rest, so its refusal while a route runs shows"
                + " nothing");

            assertFalse(session.snapshotSetup().similar(unturned), "precondition: following a turn wrote nothing at rest");
        }
        finally
        {
            answering.stop();

            closeDown(ui, sandbox, folderWas);
        }
    }

    private static Object fieldOf(String name, Object on)
    {
        try
        {
            java.lang.reflect.Field field = on.getClass().getDeclaredField(name);

            field.setAccessible(true);

            return field.get(on);
        }
        catch (ReflectiveOperationException failed)
        {
            throw new IllegalStateException(failed);
        }
    }

    /** A train standing on a square with two facings it may take, as a hand-turned train is followed on. */
    private static Point aTrainOnASquareWithTwoFacings(TrainControlUI ui, AutonomySession session)
    {
        for (Point point : ui.getModel().getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square != null && session.facingChoices(square).size() == 2) return point;
        }

        throw new SkipException("no train stands on a square with two facings");
    }

    /** The items of the timetable's right-click menu, built now. */
    private static List<javax.swing.JMenuItem> timetableMenuItems(final TrainControlUI ui) throws Exception
    {
        final List<javax.swing.JMenuItem> out = new ArrayList<>();

        final Class<?> menuClass = Class.forName("org.traincontrol.gui.RightClickTimetableMenu$RightClickMenu");

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(
            org.traincontrol.gui.RightClickTimetableMenu.class, TrainControlUI.class, java.awt.event.MouseEvent.class);

        make.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) make.newInstance(
                    new org.traincontrol.gui.RightClickTimetableMenu(ui), ui, null);

                for (Component child : menu.getComponents())
                {
                    if (child instanceof javax.swing.JMenuItem) out.add((javax.swing.JMenuItem) child);
                }
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        return out;
    }

    // ---------------------------------------------------------------- the route

    /**
     * Marks the probe route running, as `MarklinRoute.execRoute` marks it, or stops it - making it first.
     *
     * @param ui the window
     * @param runs whether it runs
     */
    private static void aRouteRuns(TrainControlUI ui, boolean runs)
    {
        MarklinControlStation model = (MarklinControlStation) ui.getModel();

        if (model.getRoute(PROBE) == null)
        {
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

            assertTrue(model.newRoute(PROBE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "precondition: the probe route could not be made");
        }

        if (runs)
        {
            assertTrue(model.getRoute(PROBE).setExecuting(), "precondition: the probe route would not run");
        }
        else
        {
            model.getRoute(PROBE).stopExecuting();
        }

        assertFalse(model.isAutonomyRunning(), "precondition: autonomy is running, so this is not a route alone");
    }

    private static void closeDown(TrainControlUI ui, support.LayoutSandbox sandbox, String folderWas) throws Exception
    {
        try
        {
            putTheFolderBack(folderWas);

            if (ui != null && ui.getModel().getRoute(PROBE) != null)
            {
                ui.getModel().getRoute(PROBE).stopExecuting();

                ui.getModel().deleteRoute(PROBE);
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    // ---------------------------------------------------------------- the railway

    /** A train standing on a station of his railway. */
    private static Point aStandingTrain(TrainControlUI ui)
    {
        AutonomySession session = ui.getAutonomySession();

        for (Point point : ui.getModel().getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() != null && point.isDestination()
                && session.getStationIndex().squareOf(point.getName()) != null
                && session.getStore().isStation(session.getStationIndex().squareOf(point.getName())))
            {
                return point;
            }
        }

        throw new SkipException("no train stands on a station of the snapshot");
    }

    /** Makes a train other than this one the active locomotive, as the window makes one; its name. */
    private static String makeAnotherTrainActive(TrainControlUI ui, String notThis) throws Exception
    {
        if (ui.getActiveLoc() != null && !notThis.equals(ui.getActiveLoc().getName())) return ui.getActiveLoc().getName();

        String other = null;

        for (String name : ui.getModel().getLocList())
        {
            if (other == null && !name.equals(notThis)) other = name;
        }

        assertNotNull(other, "precondition: the snapshot has one train");

        // NOT TWICE: mapping the active train to its button again takes it off
        ui.mapLocToCurrentButton(other);

        for (long end = System.currentTimeMillis() + 10000; (ui.getActiveLoc() == null
            || !other.equals(ui.getActiveLoc().getName())) && System.currentTimeMillis() < end; ) settle();

        assertTrue(ui.getActiveLoc() != null && other.equals(ui.getActiveLoc().getName()), "precondition: " + other
            + " could not be made the active locomotive");

        return other;
    }

    /** What the setup says of a square: its record in the configuration in use. */
    private static String setupOf(AutonomySession session, TileKey square)
    {
        org.json.JSONObject points = session.getStore().getConfiguration(session.getStore().getActiveConfiguration())
            .optJSONObject("points");

        return String.valueOf(points == null ? null : points.opt(square.toString()));
    }

    // ---------------------------------------------------------------- the doors

    /** The track diagram's right-click menu on this square, as a right-click builds it. */
    private static javax.swing.JPopupMenu diagramMenu(final TrainControlUI ui, final TileKey square, Point standing)
        throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final java.lang.reflect.Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class,
            Point.class);

        gather.setAccessible(true);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
            TileKey.class, TileKey.class, Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

        make.setAccessible(true);

        final Object options = gather.invoke(null, ui, standing);

        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                menu[0] = (javax.swing.JPopupMenu) make.newInstance(ui, square, square, options);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        return menu[0];
    }

    /** The text of every item at the top of a menu. */
    private static List<String> texts(java.awt.Container menu)
    {
        List<String> out = new ArrayList<>();

        for (Component part : menu.getComponents())
        {
            if (part instanceof JMenuItem) out.add(((JMenuItem) part).getText());
        }

        return out;
    }

    /** Presses a key over a square, through the diagram's own gesture door; whether it was taken. */
    private static Object pressOver(final TrainControlUI ui, final TileKey over, final int key) throws Exception
    {
        final java.lang.reflect.Method door = TrainControlUI.class.getDeclaredMethod("locomotiveGestureOnDiagram",
            int.class, boolean.class);

        door.setAccessible(true);

        final Object[] handled = new Object[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui.setHoveredDiagramTile(over.getPage(), over.getX(), over.getY());

                handled[0] = door.invoke(ui, key, false);
            }
            catch (ReflectiveOperationException refused)
            {
                handled[0] = refused;
            }
        });

        settle();

        return handled[0];
    }

    /** The track editor's Autonomy tab, in an unshown track editor built now on this page; the last one is disposed. */
    private static javax.swing.AbstractButton modeTab(final TrainControlUI ui,
        final org.traincontrol.base.LayoutDiagram page, final org.traincontrol.gui.LayoutEditor[] editor) throws Exception
    {
        closeTheProbe(editor);

        final java.lang.reflect.Method mount = org.traincontrol.gui.LayoutEditor.class.getDeclaredMethod("mountSidebar");

        mount.setAccessible(true);

        // ITS SIDEBAR, which `render` mounts - without showing the window or taking the diagram for editing
        SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new org.traincontrol.gui.LayoutEditor(page, 30, ui, 0);

            try
            {
                mount.invoke(editor[0]);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        java.lang.reflect.Field field = org.traincontrol.gui.LayoutEditor.class.getDeclaredField("autonomyModeButton");

        field.setAccessible(true);

        return (javax.swing.AbstractButton) field.get(editor[0]);
    }

    /** Disposes the track editor `modeTab` built, if there is one: an editor window counts as an open editor. */
    private static void closeTheProbe(final org.traincontrol.gui.LayoutEditor[] editor) throws Exception
    {
        if (editor[0] == null) return;

        final org.traincontrol.gui.LayoutEditor closing = editor[0];

        editor[0] = null;

        SwingUtilities.invokeAndWait(closing::dispose);

        settle();
    }

    /** The window's Autonomy menu. */
    private static Object autonomyMenu(TrainControlUI ui) throws Exception
    {
        for (int i = 0; i < ui.getJMenuBar().getMenuCount(); i++)
        {
            javax.swing.JMenu menu = ui.getJMenuBar().getMenu(i);

            if (menu != null && I18n.t("autosetup.ui.menuAutonomy").equals(menu.getText())) return menu;
        }

        throw new AssertionError("no Autonomy menu");
    }

    /** The Autonomy menu's items by text, opened as the operator opens it. */
    private static Map<String, JMenuItem> autonomyMenuItems(final TrainControlUI ui) throws Exception
    {
        final javax.swing.JMenu autonomy = (javax.swing.JMenu) autonomyMenu(ui);

        final Map<String, JMenuItem> items = new java.util.LinkedHashMap<>();

        SwingUtilities.invokeAndWait(() ->
        {
            autonomy.setSelected(true);

            try
            {
                for (Component part : autonomy.getMenuComponents())
                {
                    if (part instanceof JMenuItem) items.put(((JMenuItem) part).getText(), (JMenuItem) part);
                }
            }
            finally
            {
                autonomy.setSelected(false);
            }
        });

        return items;
    }

    /** The Layout menu opened as the operator opens it: of each item named, whether it is live and its tooltip. */
    private static Map<String, Object[]> layoutMenuAsOpened(final TrainControlUI ui, String[] names) throws Exception
    {
        final java.lang.reflect.Field menu = TrainControlUI.class.getDeclaredField("layoutMenu");

        menu.setAccessible(true);

        final javax.swing.JMenu layout = (javax.swing.JMenu) menu.get(ui);

        SwingUtilities.invokeAndWait(() ->
        {
            layout.setSelected(true);
            layout.setSelected(false);
        });

        settle();

        Map<String, Object[]> out = new java.util.LinkedHashMap<>();

        for (String name : names)
        {
            java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField(name);

            field.setAccessible(true);

            JMenuItem item = (JMenuItem) field.get(ui);

            assertNotNull(item, "precondition: the window has no " + name);

            out.put(name, new Object[] {item.isEnabled(), item.getToolTipText()});
        }

        return out;
    }

    private static void call(Object on, String method)
    {
        try
        {
            on.getClass().getMethod(method).invoke(on);
        }
        catch (ReflectiveOperationException failed)
        {
            throw new IllegalStateException(failed);
        }
    }

    private static void invoke(java.lang.reflect.Method method, Object on, Object... args)
    {
        try
        {
            method.invoke(on, args);
        }
        catch (ReflectiveOperationException failed)
        {
            throw new IllegalStateException(failed);
        }
    }

    private static void callUi(TrainControlUI ui, String method, Class<?>[] types, Object... args)
    {
        try
        {
            java.lang.reflect.Method door = TrainControlUI.class.getDeclaredMethod(method, types);

            door.setAccessible(true);

            door.invoke(ui, args);
        }
        catch (ReflectiveOperationException failed)
        {
            throw new IllegalStateException(failed);
        }
    }

    private static void settle() throws Exception
    {
        for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * Closes every question as it is asked - No, Cancel, or the file chooser's Cancel - and keeps what each said.
     */
    private static final class Answering
    {
        private final List<String> said = Collections.synchronizedList(new ArrayList<String>());

        private final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean();

        private volatile boolean first;

        private Thread thread;

        /** Whether a question with answers to choose from is answered with the first, rather than closed. */
        void pickFirstOption(boolean on)
        {
            first = on;
        }

        void start()
        {
            going.set(true);

            thread = new Thread(() ->
            {
                java.util.Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

                while (going.get())
                {
                    try
                    {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException stop)
                    {
                        return;
                    }

                    for (Window window : Window.getWindows())
                    {
                        if (!window.isShowing() || !(window instanceof JDialog)) continue;

                        final JOptionPane pane = find(((JDialog) window).getContentPane(), JOptionPane.class);

                        if (pane != null && handled.add(pane))
                        {
                            said.add(String.valueOf(pane.getMessage()));

                            final Object[] options = pane.getOptions();

                            final Object answer = first && options != null && options.length > 0 ? options[0]
                                : Integer.valueOf(JOptionPane.CLOSED_OPTION);

                            SwingUtilities.invokeLater(() -> pane.setValue(answer));
                        }

                        final javax.swing.JFileChooser chooser = find(((JDialog) window).getContentPane(),
                            javax.swing.JFileChooser.class);

                        if (chooser != null && handled.add(chooser))
                        {
                            said.add("chooser: " + ((JDialog) window).getTitle());

                            SwingUtilities.invokeLater(chooser::cancelSelection);
                        }
                    }
                }
            }, "closing every question");

            thread.setDaemon(true);
            thread.start();
        }

        /** How many questions have been asked so far, once the last has had time to be seen. */
        int count() throws Exception
        {
            Thread.sleep(300);

            return said.size();
        }

        /** What was asked since then, once the last has had time to be seen. */
        List<String> since(int from) throws Exception
        {
            Thread.sleep(400);

            synchronized (said)
            {
                return new ArrayList<>(said.subList(from, said.size()));
            }
        }

        void stop() throws Exception
        {
            going.set(false);

            if (thread != null) thread.join(1000);
        }
    }

    private static <T> T find(java.awt.Container container, Class<T> type)
    {
        for (Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof java.awt.Container)
            {
                T found = find((java.awt.Container) child, type);

                if (found != null) return found;
            }
        }

        return null;
    }
}
