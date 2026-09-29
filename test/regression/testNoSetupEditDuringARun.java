package regression;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import static regression.testTheImportDoorReadsAnOldFile.answeringYes;
import static regression.testTheImportDoorReadsAnOldFile.closeTheEditor;
import static regression.testTheImportDoorReadsAnOldFile.dispatchATrain;
import static regression.testTheImportDoorReadsAnOldFile.itemCalled;
import static regression.testTheImportDoorReadsAnOldFile.logged;
import static regression.testTheImportDoorReadsAnOldFile.openTheEditorFor;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import static regression.testTheImportDoorReadsAnOldFile.pointsHeldBy;
import static regression.testTheImportDoorReadsAnOldFile.putTheFolderBack;
import static regression.testTheImportDoorReadsAnOldFile.startAnsweringYes;

/**
 * No setup edit is made while trains run, at any door (Adam, 2026-09-28: *"There should be no setup edit possible during a
 * run"*), and nothing written for a train under way moves it off its path - RLV12's findings, on the live snapshot's
 * window.
 *
 * Its own class because the import door's ran out of its 512 MB with these seven beside it: every window a test opens
 * keeps its threads, and the class then died in TestNG's report with no summary to say so (2026-09-29).  The window, the
 * dispatch and the answering are that class's helpers, shared.
 *
 * @author Adam
 */
public class testNoSetupEditDuringARun
{
    /**
     * No train is sent from the Auto tab while the editor is open (RLV12-B1).  Start, Execute Timetable, Return Home and
     * the diagram's menu refuse while an editor holds the diagram, and the rule that no setup edit is made during a run
     * rests on it, since the editor cannot be opened while trains run.  The Auto tab's double-click never asked: an editor
     * opened at rest stayed open for the run it sent, and a square clicked in it wrote the setup and left an edit waiting
     * against the running railway.  Refused, and said, as those doors say it.
     *
     * MUTATION: take the editor question out of the Auto tab's double-click, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheAutoTabSendsNoTrainWhileTheEditorIsOpen() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            // THE EDITOR, opened at rest on a square of the main page
            TileKey square = null;

            for (TileKey tile : session.getGraph().getTiles().keySet())
            {
                if (square == null && "1 - Main".equals(tile.getPage())) square = tile;
            }

            assertNotNull(square, "precondition: no square on the main page");

            openTheEditorFor(ui[0], square, Collections.<TileKey>emptyList());

            assertTrue(ui[0].isLayoutEditorOpen(), "precondition: the editor did not open");

            // THE AUTO TAB'S LIST OF PATHS for a standing train, as the window built it
            java.lang.reflect.Field panelField = TrainControlUI.class.getDeclaredField("autoLocPanel");
            java.lang.reflect.Field pathsField = org.traincontrol.gui.AutoLocomotiveStatus.class.getDeclaredField("paths");
            java.lang.reflect.Field listField =
                org.traincontrol.gui.AutoLocomotiveStatus.class.getDeclaredField("locAvailPaths");
            java.lang.reflect.Field locField =
                org.traincontrol.gui.AutoLocomotiveStatus.class.getDeclaredField("locomotive");

            panelField.setAccessible(true);
            pathsField.setAccessible(true);
            listField.setAccessible(true);
            locField.setAccessible(true);

            javax.swing.JList<?> chosen = null;
            org.traincontrol.base.Locomotive sent = null;

            long until = System.currentTimeMillis() + 30000;

            while (chosen == null && System.currentTimeMillis() < until)
            {
                SwingUtilities.invokeAndWait(() -> { });

                for (Component c : ((javax.swing.JPanel) panelField.get(ui[0])).getComponents())
                {
                    if (chosen != null || !(c instanceof org.traincontrol.gui.AutoLocomotiveStatus)) continue;

                    List<?> paths = (List<?>) pathsField.get(c);
                    javax.swing.JList<?> list = (javax.swing.JList<?>) listField.get(c);

                    if (paths != null && !paths.isEmpty() && list.getModel().getSize() > 0)
                    {
                        chosen = list;
                        sent = (org.traincontrol.base.Locomotive) locField.get(c);
                    }
                }

                if (chosen == null) Thread.sleep(250);
            }

            assertNotNull(chosen, "precondition: no train on the Auto tab offers a path");

            startAnsweringYes(asked, going);

            final javax.swing.JList<?> list = chosen;

            // THE DOUBLE-CLICK, through the list's own listener
            SwingUtilities.invokeAndWait(() ->
            {
                // The Auto tab is not the tab showing, so its list was never laid out: given a size to be clicked at
                list.setSize(400, Math.max(100, list.getPreferredSize().height));

                java.awt.Rectangle cell = list.getCellBounds(0, 0);

                list.dispatchEvent(new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, cell.x + cell.width / 2, cell.y + cell.height / 2, 2, false,
                    java.awt.event.MouseEvent.BUTTON1));
            });

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            until = System.currentTimeMillis() + 5000;

            while (!railway.isAlreadyUnderway(sent) && System.currentTimeMillis() < until) Thread.sleep(100);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(railway.isAlreadyUnderway(sent), "the Auto tab sent " + sent.getName() + " with the editor open -"
                + " the editor then stays open through the run, and a square clicked in it edits the setup under the"
                + " trains (RLV12-B1)");

            assertTrue(asked.contains(I18n.t("autosetup.ui.menuEditorOpen")), "the Auto tab did not say why it sent"
                + " nothing: " + asked);
        }
        finally
        {
            going.set(false);

            stopWhatTheClaimSent(ui[0]);

            try
            {
                if (ui[0] != null) closeTheEditor(ui[0]);
            }
            finally
            {
                putTheFolderBack(folderWas);

                if (ui[0] != null)
                {
                    final TrainControlUI closing = ui[0];

                    SwingUtilities.invokeAndWait(() -> closing.dispose());
                }

                if (sandbox != null) sandbox.close();
            }
        }
    }

    /** Stops every train a claim sent, and ends the threads driving them. */
    private static void stopWhatTheClaimSent(TrainControlUI ui)
    {
        org.traincontrol.automation.Layout railway = ui == null || ui.getModel() == null ? null
            : ui.getModel().getAutoLayoutIfLoaded();

        if (railway != null) railway.stopLocomotives();

        for (java.util.Map.Entry<Thread, StackTraceElement[]> t : Thread.getAllStackTraces().entrySet())
        {
            for (StackTraceElement e : t.getValue())
            {
                if ("executePath".equals(e.getMethodName()))
                {
                    t.getKey().interrupt();

                    break;
                }
            }
        }
    }

    /**
     * The diagram's own Edit Locomotive item, opened at rest, refuses and says so when clicked after a run began
     * (RLV12-C3).  No factory builds it, so round 13's refusal missed it: the dialog opened, and OK wrote the placement,
     * the facing and the tail into the setup and saved it while trains ran.
     *
     * MUTATION: let the item open its dialog while trains run, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsEditLocomotiveRefusesOnceTrainsRun() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();
            final String inUse = session.getStore().getActiveConfiguration();
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // A STATION WITH A STANDING TRAIN
            org.traincontrol.automation.Point at = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (at == null && p.isDestination() && p.getCurrentLocomotive() != null
                    && session.getStationIndex().squareOf(p.getName()) != null)
                {
                    at = p;
                }
            }

            assertNotNull(at, "precondition: no station with a train standing on it");

            final TileKey square = session.getStationIndex().squareOf(at.getName());
            final String standing = at.getCurrentLocomotive().getName();

            // THE DIAGRAM'S MENU, BUILT AT REST, as showFor builds it
            java.lang.reflect.Constructor<?> make = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu")
                .getDeclaredConstructors()[0];

            make.setAccessible(true);

            final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    menu[0] = (javax.swing.JPopupMenu) make.newInstance(ui[0], null, square, null);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            java.lang.reflect.Method label = org.traincontrol.gui.GraphLocAssign.class.getDeclaredMethod("menuLabelFor",
                org.traincontrol.automation.Point.class);

            label.setAccessible(true);

            final javax.swing.JMenuItem edit = itemCalled(menu[0], (String) label.invoke(null, at));

            assertNotNull(edit, "precondition: the diagram's menu at " + square + " has no Edit Locomotive item");

            // WHAT THE DOOR WRITES, taken out first so that a write shows
            session.getStore().getConfiguration(inUse).getJSONObject("points").getJSONObject(square.toString())
                .remove(AutonomyBuilder.FACING);

            // THEN A RUN BEGINS - another train
            final Object[] dispatched = dispatchATrain(ui[0], standing);

            driving = (Thread) dispatched[3];

            org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.NOT_KNOWN);

            List<String> asked = answeringYes(() -> edit.doClick());

            org.json.JSONObject after = session.getStore().getConfiguration(inUse).getJSONObject("points")
                .getJSONObject(square.toString());

            assertFalse(after.has(AutonomyBuilder.FACING), "the diagram's Edit Locomotive, opened at rest and clicked while "
                + dispatched[0] + " was under way, wrote " + standing + "'s facing into the setup - no setup edit is made"
                + " during a run (RLV12-C3): " + after);

            assertTrue(asked.contains(I18n.t("autolayout.errorCannotEditWhileRunning")), "the diagram's Edit Locomotive,"
                + " clicked during a run, did not say why it did nothing: " + asked);
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            if (driving != null) driving.interrupt();

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A tail question answered once trains run writes nothing, and the log says why (RLV12-C3).  The question waits on
     * the diagram with the window live - for up to half an hour - and Start or a hand send in the wait is not refused; its
     * answer then wrote the road into the setup and onto the running railway.  Every door that asks it takes where to
     * write from `whereTheAnswerGoes`, so that is where the answer is dropped.
     *
     * MUTATION: let `whereTheAnswerGoes` answer while trains run, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATailAnsweredOnceTrainsRunIsNotWritten() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // A STANDING TRAIN, with the side and the road the placement left
            org.traincontrol.automation.Point at = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (at == null && p.isDestination() && p.getCurrentLocomotive() != null) at = p;
            }

            assertNotNull(at, "precondition: no station with a train standing on it");

            final org.traincontrol.base.Locomotive standing = at.getCurrentLocomotive();
            final String side = at.getArrivedFrom();
            final List<org.traincontrol.automation.Edge> road = at.getArrivedAlong();

            assertEquals(org.traincontrol.gui.TailCrossedPrompt.whereTheAnswerGoes(railway, at, standing, side, road, true),
                at, "precondition: at rest, the answer about " + standing.getName() + " does not go to " + at.getName());

            final Object[] dispatched = dispatchATrain(ui[0], standing.getName());

            driving = (Thread) dispatched[3];

            assertTrue(at.getCurrentLocomotive() == standing, "precondition: the dispatch moved " + standing.getName());

            assertEquals(org.traincontrol.gui.TailCrossedPrompt.whereTheAnswerGoes(railway, at, standing, side, road, true),
                null, "a tail question about " + standing.getName() + " answered once " + dispatched[0] + " was under way"
                + " was written into the setup and onto the railway - no setup edit is made during a run (RLV12-C3)");

            String before = logged();

            org.traincontrol.gui.TailCrossedPrompt.noteADroppedAnswer(ui[0].getModel(), standing.getName(), at.getName(),
                org.traincontrol.gui.TailCrossedPrompt.Answer.NOT_ASKED);

            String said = logged().substring(before.length());

            assertTrue(said.contains(I18n.f("autolayout.ui.logTailAnswerDroppedRunning", standing.getName(), at.getName())),
                "the log did not say the answer was dropped because trains began running: " + said);
        }
        finally
        {
            if (driving != null) driving.interrupt();

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Following a direction change leaves a train under way on its path (RLV12-C4).  `moveOntoFacingCopy` stands a train
     * on another copy of its square, which sweeps it off every other point - the whole of a locked path.  RLV11-C3's rule
     * kept one caller from reaching a train under way; the direction follow's `flipFacing` still did, and stood it on a
     * square in the middle of its path.  The rule is in the primitive now.
     *
     * MUTATION: stand a train under way on another copy, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testFollowingADirectionChangeLeavesATrainUnderWayOnItsPath() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            final Object[] sent = sendATrainFromASquareWithTwoFacings(railway, session);

            driving = (Thread) sent[1];

            final org.traincontrol.base.Locomotive train = (org.traincontrol.base.Locomotive) sent[0];

            final int before = pointsHeldBy(railway, train);

            assertTrue(before >= 2, "precondition: " + train.getName() + " under way holds " + before + " points");

            SwingUtilities.invokeAndWait(() -> session.flipFacing(train.getName(), railway));

            assertEquals(pointsHeldBy(railway, train), before, "following a direction change stood " + train.getName()
                + ", under way, on another copy of a square on its path - sweeping it off the rest of its path, which then"
                + " reads free (RLV12-C4)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A direction change followed after a run began writes nothing (RLV12-C4).  The Central Station's thread decides at
     * rest and posts the work to the event thread, which ran it later without asking again - so a hand send queued ahead
     * of it was under way when it landed, and the setup was written during the run.  The posted work asks again.
     *
     * MUTATION: let the posted work follow the change while trains run, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testADirectionChangeFollowedOnceTrainsRunWritesNothing() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();
            final String inUse = session.getStore().getActiveConfiguration();
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            final Object[] sent = sendATrainFromASquareWithTwoFacings(railway, session);

            driving = (Thread) sent[1];

            final org.traincontrol.base.Locomotive train = (org.traincontrol.base.Locomotive) sent[0];

            final String before = session.getStore().getConfiguration(inUse).toString();

            final java.lang.reflect.Method follow = TrainControlUI.class.getDeclaredMethod("followTheTurn", String.class);

            follow.setAccessible(true);

            final Object[] moved = new Object[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    moved[0] = follow.invoke(ui[0], train.getName());
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(moved[0], null, "a direction change followed while " + train.getName() + " was under way wrote its"
                + " facing on " + moved[0] + " into the setup - no setup edit is made during a run (RLV12-C4)");

            assertEquals(session.getStore().getConfiguration(inUse).toString(), before, "a direction change followed during"
                + " a run changed the setup (RLV12-C4)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Sends a standing train, on a square with two facings, off along a path away, and waits until it is under way; it
     * waits on a sensor nothing here sets.
     *
     * @return the train and the thread driving it
     */
    private static Object[] sendATrainFromASquareWithTwoFacings(org.traincontrol.automation.Layout railway,
        AutonomySession session) throws Exception
    {
        org.traincontrol.automation.Point at = null;
        List<org.traincontrol.automation.Edge> path = null;

        for (org.traincontrol.automation.Point p : railway.getPoints())
        {
            org.traincontrol.base.Locomotive l = p.getCurrentLocomotive();

            if (at != null || l == null || railway.isAlreadyUnderway(l)) continue;

            TileKey square = session.getStationIndex().squareOf(p.getName());

            if (square == null || session.facingChoices(square).size() != 2) continue;

            List<org.traincontrol.automation.Edge> away = null;

            for (List<org.traincontrol.automation.Edge> candidate : railway.getPossiblePaths(l, false))
            {
                if (away == null && candidate != null && candidate.size() >= 2 && candidate.get(0).getStart() == p)
                {
                    away = candidate;
                }
            }

            if (away == null) continue;

            at = p;
            path = away;
        }

        assertNotNull(at, "precondition: no standing train on a square with two facings, with a path away");

        final org.traincontrol.base.Locomotive train = at.getCurrentLocomotive();
        final List<org.traincontrol.automation.Edge> route = path;

        Thread driving = new Thread(() -> railway.executePath(route, train, 30, null), "dispatched by the claim");

        driving.setDaemon(true);
        driving.start();

        long until = System.currentTimeMillis() + 60000;

        while (!railway.getActiveLocomotives().containsKey(train) && driving.isAlive() && System.currentTimeMillis() < until)
        {
            Thread.sleep(50);
        }

        Thread.sleep(1000);

        assertTrue(railway.isAlreadyUnderway(train), "precondition: " + train.getName() + " did not get under way");

        return new Object[] {train, driving};
    }

    /**
     * Every kind of setup item on the diagram refuses once trains run - a plain item, a check box, a choice of one and a
     * direction (RLV12-C7).  The refusal went into four factories, and the claim for it clicked a check box: the other
     * three could lose theirs with nothing going red.
     *
     * MUTATION: take the refusal out of any one factory, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testEveryKindOfSetupItemRefusesOnceTrainsRun() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        Thread driving = null;

        // ONE ANSWERING THREAD FOR EVERY CLICK: one per click leaves the last one's thread a final look round after it is
        // told to stop, and that look took the next click's refusal into the last click's list
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // A STATION, and a square with branches: their setup menus, opened at rest
            TileKey station = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                TileKey s = p.isDestination() ? session.getStationIndex().squareOf(p.getName()) : null;

                if (station == null && s != null) station = s;
            }

            assertNotNull(station, "precondition: no station on his railway");

            final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[2];
            final TileKey at = station;

            SwingUtilities.invokeAndWait(() -> menu[0] = ui[0].buildAutonomyTileMenu(at));

            TileKey branches = null;
            javax.swing.JMenuItem plain = null;
            javax.swing.JMenuItem direction = null;

            for (TileKey tile : session.getGraph().getTiles().keySet())
            {
                if (branches != null || !"1 - Main".equals(tile.getPage()) || session.getRoutes(tile).size() < 2) continue;

                final TileKey candidate = tile;

                SwingUtilities.invokeAndWait(() -> menu[1] = ui[0].buildAutonomyTileMenu(candidate));

                javax.swing.JMenuItem all = itemOfKind(menu[1], I18n.t("autosetup.ui.menuRouteNone"),
                    javax.swing.JMenuItem.class, false);
                javax.swing.JMenuItem one = itemOfKind(menu[1], I18n.t("autosetup.ui.menuRouteNone"),
                    javax.swing.JRadioButtonMenuItem.class, false);

                if (all != null && one != null)
                {
                    branches = tile;
                    plain = all;
                    direction = one;
                }
            }

            assertNotNull(branches, "precondition: no square on the main page whose menu sets all branches and one");

            javax.swing.JMenuItem check = itemCalled(menu[0], I18n.t("autosetup.ui.menuAutoDestination"));
            javax.swing.JMenuItem choice = null;

            for (String kind : new String[] {"autosetup.ui.menuCanStop", "autosetup.ui.menuCanTraverse",
                "autosetup.ui.menuNeither"})
            {
                javax.swing.JMenuItem radio = itemOfKind(menu[0], I18n.t(kind), javax.swing.JRadioButtonMenuItem.class,
                    false);

                if (choice == null && radio != null) choice = radio;
            }

            assertNotNull(check, "precondition: the setup menu of " + station + " has no item for whether autonomy may"
                + " choose it");
            assertNotNull(choice, "precondition: the setup menu of " + station + " offers no other use of it");

            // THEN A RUN BEGINS
            final Object[] dispatched = dispatchATrain(ui[0], null);

            driving = (Thread) dispatched[3];

            final Object[][] clicks = {
                {"a plain item (all branches: " + I18n.t("autosetup.ui.menuRouteNone") + ")", plain, branches},
                {"a check box (" + check.getText() + ")", check, station},
                {"a choice of one (" + choice.getText() + ")", choice, station},
                {"a direction (" + direction.getText() + ")", direction, branches}};

            startAnsweringYes(asked, going);

            for (Object[] click : clicks)
            {
                final javax.swing.JMenuItem item = (javax.swing.JMenuItem) click[1];
                final String before = setupOf(session, (TileKey) click[2]);

                final int from = asked.size();

                SwingUtilities.invokeAndWait(() -> item.doClick());

                for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

                final List<String> said = new ArrayList<>(asked.subList(from, asked.size()));

                assertEquals(setupOf(session, (TileKey) click[2]), before, click[0] + " of the setup menu, opened at rest,"
                    + " wrote the setup when clicked while " + dispatched[0] + " was under way - no setup edit is made during"
                    + " a run (RLV12-C7): " + said);

                assertTrue(said.contains(I18n.t("autolayout.errorCannotEditWhileRunning")), click[0] + " of the setup"
                    + " menu, clicked during a run, did not say why it did nothing: " + said);

                assertFalse((Boolean) declined.get(ui[0]), click[0] + " of the setup menu, clicked during a run, left an"
                    + " edit waiting against the running railway");
            }
        }
        finally
        {
            going.set(false);

            if (driving != null) driving.interrupt();

            if (ui[0] != null) declined.set(ui[0], false);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The first item anywhere in this menu with this text, of exactly this class, selected or not as asked. */
    private static javax.swing.JMenuItem itemOfKind(java.awt.Container menu, String text,
        Class<? extends javax.swing.JMenuItem> kind, boolean selected)
    {
        if (menu == null) return null;

        java.awt.Component[] parts = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component part : parts)
        {
            if (part instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = itemOfKind((javax.swing.JMenu) part, text, kind, selected);

                if (found != null) return found;
            }
            else if (part != null && part.getClass() == kind && text.equals(((javax.swing.JMenuItem) part).getText())
                && ((javax.swing.JMenuItem) part).isSelected() == selected)
            {
                return (javax.swing.JMenuItem) part;
            }
        }

        return null;
    }

    /** What the setup says of a square: its record in the configuration in use, and each of its routes' directions. */
    private static String setupOf(AutonomySession session, TileKey square)
    {
        org.json.JSONObject points = session.getStore().getConfiguration(session.getStore().getActiveConfiguration())
            .optJSONObject("points");

        StringBuilder out = new StringBuilder(String.valueOf(points == null ? null : points.opt(square.toString())));

        for (org.traincontrol.automationui.TileGraph.RouteId route : session.getRoutes(square).keySet())
        {
            out.append(' ').append(route).append('=').append(session.getGraph().getDirection(square, route));
        }

        return out.toString();
    }

    /**
     * A turn owed to a train still locking its path from where it turned is not written (RLV12-C7).  RLV11-C3's rule asks
     * `isAlreadyUnderway`, which counts a train that has claimed its path and not yet set off; the claim for it waited
     * until the train was running, so a rule that asked only whether it was running passed it.
     *
     * MUTATION: ask only whether the train is running, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATurnOwedToATrainStillLockingItsPathIsNotWritten() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        org.traincontrol.automation.Layout locked = null;
        List<org.traincontrol.automation.Edge> route = null;
        org.traincontrol.base.Locomotive train = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // A STANDING TRAIN ON A SPLIT SQUARE whose recorded side has a copy of its own, not this one, and a path away
            org.traincontrol.automation.Point at = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                org.traincontrol.base.Locomotive l = p.getCurrentLocomotive();

                if (at != null || l == null || p.getArrivedFrom() == null || railway.isAlreadyUnderway(l)) continue;

                TileKey square = session.getStationIndex().squareOf(p.getName());

                if (square == null) continue;

                org.traincontrol.automationui.TilePorts.Side came = null;

                for (org.traincontrol.automationui.TilePorts.Side s : org.traincontrol.automationui.TilePorts.Side.values())
                {
                    if (s.name().equals(p.getArrivedFrom())) came = s;
                }

                if (came == null) continue;

                org.traincontrol.automation.Point target = session.copyFacing(square, came, railway);

                if (target == null || target == p) continue;

                List<org.traincontrol.automation.Edge> away = null;

                for (List<org.traincontrol.automation.Edge> candidate : railway.getPossiblePaths(l, false))
                {
                    if (away == null && candidate != null && candidate.size() >= 2 && candidate.get(0).getStart() == p)
                    {
                        away = candidate;
                    }
                }

                if (away == null) continue;

                at = p;
                route = away;
            }

            assertNotNull(at, "precondition: no standing train on a split square whose side has another copy, with a"
                + " path away");

            train = at.getCurrentLocomotive();

            java.util.Map<String, String> owed = new java.util.HashMap<>();

            owed.put(train.getName(), at.getName());

            railway.restoreReversalsOnArrival(owed);

            // IT CLAIMS ITS PATH from where it turned, and has not set off
            assertTrue(railway.configureAndLockPath(route, train), "precondition: " + train.getName() + "'s path would not"
                + " lock");

            locked = railway;

            assertTrue(railway.isAlreadyUnderway(train) && !railway.getActiveLocomotives().containsKey(train),
                "precondition: " + train.getName() + " is not still locking its path");

            final int before = pointsHeldBy(railway, train);

            assertTrue(before >= 2, "precondition: " + train.getName() + " locking its path holds " + before + " points");

            // AND THE SETUP'S RECORD OF THAT SQUARE, which the rule keeps unwritten where the sweep is refused beneath it
            final String square = session.getStationIndex().squareOf(at.getName()).toString();
            final String inUse = session.getStore().getActiveConfiguration();
            final String recorded = String.valueOf(session.getStore().getConfiguration(inUse).getJSONObject("points")
                .opt(square));

            final java.lang.reflect.Method write = TrainControlUI.class.getDeclaredMethod("writeTheTurnsOwed");

            write.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    write.invoke(ui[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(pointsHeldBy(railway, train), before, "writing the turns stood " + train.getName() + ", locking"
                + " its path from where it turned, on another copy of that square - sweeping it off the path it had"
                + " claimed (RLV12-C7)");

            assertEquals(String.valueOf(session.getStore().getConfiguration(inUse).getJSONObject("points").opt(square)),
                recorded, "writing the turns wrote a facing for " + train.getName() + " on " + square + ", which it is"
                + " locking its path away from - it owes nothing until it arrives (RLV11-C3, RLV12-C7)");
        }
        finally
        {
            if (locked != null) locked.unlockPath(route, train);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * No train is sent from the diagram's destinations while the editor is open (RLV12-B1; Adam, 2026-09-29: *"can all
     * the checks go through a single door?"*).  The diagram's menu is built only with no editor open, and its destination
     * items asked nothing about one when clicked - so a menu opened before the editor and clicked after it sent the train
     * under it, as the Auto tab's list did.  Both hand doors send through one door now, and it asks.
     *
     * MUTATION: take the editor question out of the one door, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsDestinationsSendNoTrainWhileTheEditorIsOpen() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // A STANDING TRAIN WITH SOMEWHERE TO GO, and the diagram's menu for it - gathered and built at rest, as showFor
            // gathers and builds it
            Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

            java.lang.reflect.Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class,
                org.traincontrol.automation.Point.class);
            final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructors()[0];

            gather.setAccessible(true);
            make.setAccessible(true);

            org.traincontrol.automation.Point at = null;
            TileKey square = null;
            javax.swing.JMenuItem go = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (go != null || !p.isDestination() || p.getCurrentLocomotive() == null) continue;

                final TileKey here = session.getStationIndex().squareOf(p.getName());

                if (here == null) continue;

                final Object options = gather.invoke(null, ui[0], p);

                if (options == null) continue;

                final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        menu[0] = (javax.swing.JPopupMenu) make.newInstance(ui[0], null, here, options);
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new IllegalStateException(e);
                    }
                });

                javax.swing.JMenuItem found = firstDestination(menu[0]);

                if (found != null)
                {
                    at = p;
                    square = here;
                    go = found;
                }
            }

            assertNotNull(go, "precondition: no standing train's menu offers it a destination");

            final org.traincontrol.base.Locomotive train = at.getCurrentLocomotive();

            // THEN THE EDITOR OPENS
            openTheEditorFor(ui[0], square, Collections.<TileKey>emptyList());

            assertTrue(ui[0].isLayoutEditorOpen(), "precondition: the editor did not open");

            startAnsweringYes(asked, going);

            final javax.swing.JMenuItem item = go;

            SwingUtilities.invokeAndWait(() -> item.doClick());

            long until = System.currentTimeMillis() + 5000;

            while (!railway.isAlreadyUnderway(train) && System.currentTimeMillis() < until) Thread.sleep(100);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(railway.isAlreadyUnderway(train), "the diagram's destination " + item.getText() + ", from a menu"
                + " built before the editor opened, sent " + train.getName() + " with the editor open (RLV12-B1)");

            assertTrue(asked.contains(I18n.t("autosetup.ui.menuEditorOpen")), "the diagram's destination did not say why"
                + " it sent nothing: " + asked);
        }
        finally
        {
            going.set(false);

            stopWhatTheClaimSent(ui[0]);

            try
            {
                if (ui[0] != null) closeTheEditor(ui[0]);
            }
            finally
            {
                putTheFolderBack(folderWas);

                if (ui[0] != null)
                {
                    final TrainControlUI closing = ui[0];

                    SwingUtilities.invokeAndWait(() -> closing.dispose());
                }

                if (sandbox != null) sandbox.close();
            }
        }
    }

    /** The first destination anywhere in this menu - an item reading "-> " and a station's name. */
    private static javax.swing.JMenuItem firstDestination(java.awt.Container menu)
    {
        if (menu == null) return null;

        java.awt.Component[] parts = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component part : parts)
        {
            if (part instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = firstDestination((javax.swing.JMenu) part);

                if (found != null) return found;
            }
            else if (part instanceof javax.swing.JMenuItem && String.valueOf(((javax.swing.JMenuItem) part).getText())
                .startsWith("-> "))
            {
                return (javax.swing.JMenuItem) part;
            }
        }

        return null;
    }

    /**
     * A send queued ahead of the editor's build is not sent (RLV13-C2).  The editor is asked for on the event thread and
     * built in a task posted after it, and the one gate asked only whether the editor's window existed - so a send that
     * ran between the two passed it, and the editor opened over the run it started.  The event thread is held here, as a
     * slow refresh holds it, with an Auto tab double-click and the editor's request queued behind it.
     *
     * The send is queued AHEAD of the editor's request, so the gate finds no editor on its way; what refuses here is
     * the editor's build, which looks at the railway again and opens no editor over a run -
     * testTheGateRefusesWhileAnEditorIsOnItsWay reaches the gate's own "on its way".
     *
     * MUTATION: let the editor's posted build skip its second look at the railway, with the gate seeing only an
     * editor already built, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testASendQueuedAheadOfTheEditorsBuildIsNotSent() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            TileKey square = null;

            for (TileKey tile : session.getGraph().getTiles().keySet())
            {
                if (square == null && "1 - Main".equals(tile.getPage())) square = tile;
            }

            assertNotNull(square, "precondition: no square on the main page");

            final Object[] offered = anAutoTabListOfPaths(ui[0]);

            final javax.swing.JList<?> list = (javax.swing.JList<?>) offered[0];
            final org.traincontrol.base.Locomotive sent = (org.traincontrol.base.Locomotive) offered[1];

            startAnsweringYes(asked, going);

            SwingUtilities.invokeAndWait(() -> list.setSize(400, Math.max(100, list.getPreferredSize().height)));

            // THE EVENT THREAD HELD, with the operator's two clicks queued behind it: the double-click, then the editor
            holdTheEventThread(1500);

            SwingUtilities.invokeLater(() ->
            {
                java.awt.Rectangle cell = list.getCellBounds(0, 0);

                list.dispatchEvent(new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, cell.x + cell.width / 2, cell.y + cell.height / 2, 2, false,
                    java.awt.event.MouseEvent.BUTTON1));
            });

            final TrainControlUI window = ui[0];
            final TileKey at = square;

            SwingUtilities.invokeLater(() -> window.openAutonomyEditor(at, Collections.<TileKey>emptyList()));

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            long until = System.currentTimeMillis() + 10000;

            while (!(ui[0].isLayoutEditorOpen() && railway.isAlreadyUnderway(sent)) && System.currentTimeMillis() < until)
            {
                Thread.sleep(200);
            }

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(ui[0].isLayoutEditorOpen() && railway.isAlreadyUnderway(sent), "a send queued ahead of the"
                + " editor's build sent " + sent.getName() + ", and the editor then opened over the run - a square"
                + " clicked in it edits the setup under the trains (RLV13-C2): asked " + asked);
        }
        finally
        {
            going.set(false);

            stopWhatTheClaimSent(ui[0]);

            try
            {
                if (ui[0] != null) closeTheEditor(ui[0]);
            }
            finally
            {
                putTheFolderBack(folderWas);

                if (ui[0] != null)
                {
                    final TrainControlUI closing = ui[0];

                    SwingUtilities.invokeAndWait(() -> closing.dispose());
                }

                if (sandbox != null) sandbox.close();
            }
        }
    }

    /**
     * The one gate counts an editor on its way as open (RLV13-C2).  Edit Layout sets `editorOnItsWay` before it posts the
     * editor's build, and until that build has run the gate must refuse every door in the editor's own words.  The
     * build's second look at the railway does not make this redundant: a hand send hands its train to a thread and
     * returns, so the railway can still read idle when the build asks.
     *
     * testASendQueuedAheadOfTheEditorsBuildIsNotSent cannot reach this: its send is queued ahead of the editor's
     * request, so no editor is on its way when the gate is asked, and the build's second look is what refuses there.
     *
     * MUTATION: let the gate see only an editor already built, or let the editor's question forget the editor on its
     * way, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheGateRefusesWhileAnEditorIsOnItsWay() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final String editorsWords = I18n.t("autosetup.ui.menuEditorOpen");

            for (boolean start : new boolean[] {false, true})
            {
                String door = start ? "Start" : "a hand send";

                // THE CONTROL: with no editor asked for, the gate does not answer in the editor's words
                String atRest = askTheGate(ui, false, start);

                assertFalse(editorsWords.equals(atRest), "precondition: with no editor open or asked for, the gate"
                    + " already refused " + door + " for an editor: " + atRest);

                // AN EDITOR ON ITS WAY: asked for, its build posted and not yet run
                assertEquals(askTheGate(ui, true, start), editorsWords, "with the editor asked for and its build"
                    + " not yet run, the one gate let " + door + " through - the train then runs under the editor"
                    + " the build opens (RLV13-C2)");
            }
        }
        finally
        {
            try
            {
                if (ui != null) askTheGate(ui, false, false);
            }
            finally
            {
                putTheFolderBack(folderWas);

                if (ui != null)
                {
                    final TrainControlUI closing = ui;

                    SwingUtilities.invokeAndWait(() -> closing.dispose());
                }

                if (sandbox != null) sandbox.close();
            }
        }
    }

    /**
     * Start's worker looks again, at its event-thread moment, for an editor that has come since the press (RLV13-C2).  An
     * armed route makes the worker ask the operator before it comes back, and the editor asked for just after Start is
     * built while that question waits - the railway still reads idle, so the build's own second look lets it open.  When
     * the worker comes back the editor is open, and only the worker's look stops the trains starting under it.
     *
     * MUTATION: let Start's worker start the trains without asking again whether an editor has come, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testStartsWorkerLooksAgainOnceTheEditorHasOpened() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        final String probe = "RLV13-C2 armed route";

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            TileKey square = null;

            for (TileKey tile : session.getGraph().getTiles().keySet())
            {
                if (square == null && "1 - Main".equals(tile.getPage())) square = tile;
            }

            java.lang.reflect.Field startField = TrainControlUI.class.getDeclaredField("startAutonomy");

            startField.setAccessible(true);

            final javax.swing.JButton start = (javax.swing.JButton) startField.get(ui[0]);

            assertTrue(start.isEnabled(), "precondition: Start is not offered on his railway");

            // AN ARMED ROUTE, so Start's worker asks the operator before it comes back to the event thread
            assertTrue(ui[0].getModel().newRoute(probe, new ArrayList<>(), 0,
                org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "precondition: the probe route was not made");

            ((org.traincontrol.marklin.MarklinRoute) ui[0].getModel().getRoute(probe)).enable();

            startAnsweringYes(asked, going);

            holdTheEventThread(1500);

            SwingUtilities.invokeLater(() -> start.doClick());

            final TrainControlUI window = ui[0];
            final TileKey at = square;

            SwingUtilities.invokeLater(() -> window.openAutonomyEditor(at, Collections.<TileKey>emptyList()));

            final String refused = I18n.t("autosetup.ui.menuEditorOpen");

            long until = System.currentTimeMillis() + 15000;

            while (!asked.contains(refused) && !(ui[0].isLayoutEditorOpen() && ui[0].getModel().isAutonomyRunning())
                && System.currentTimeMillis() < until)
            {
                Thread.sleep(200);
            }

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(asked.contains(I18n.t("route.ui.confirmConditionalRoutesActiveProceed")), "precondition: Start's"
                + " worker did not ask about the armed route, so nothing held it while the editor was built: asked "
                + asked);

            assertTrue(ui[0].isLayoutEditorOpen(), "precondition: the editor was not built while Start's worker waited,"
                + " so this claim did not reach the worker's own look: asked " + asked);

            assertFalse(ui[0].getModel().isAutonomyRunning(), "Start's worker came back to the event thread with the"
                + " editor open and started the trains under it (RLV13-C2): asked " + asked);
        }
        finally
        {
            going.set(false);

            stopWhatTheClaimSent(ui[0]);

            try
            {
                if (ui[0] != null) closeTheEditor(ui[0]);
            }
            finally
            {
                if (ui[0] != null && ui[0].getModel().getRoute(probe) != null) ui[0].getModel().deleteRoute(probe);

                putTheFolderBack(folderWas);

                if (ui[0] != null)
                {
                    final TrainControlUI closing = ui[0];

                    SwingUtilities.invokeAndWait(() -> closing.dispose());
                }

                if (sandbox != null) sandbox.close();
            }
        }
    }

    /**
     * Start pressed ahead of the editor's request does not run under the editor (RLV13-C2).  Start's click asks the gate
     * and starts its worker, and the worker comes back to the event thread for the atomic-routes gate before it starts
     * the trains - behind an editor asked for in between, whose request found nothing running yet.  So the worker asks
     * again there.
     *
     * In this claim's order the worker is back before the editor's build, and the build's own second look at the
     * railway is what refuses; testStartsWorkerLooksAgainOnceTheEditorHasOpened holds the worker on a question
     * so that the build runs first.
     *
     * MUTATION: let Start's worker start the trains without asking again whether an editor has come, with the
     * editor's build skipping its second look, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testStartQueuedAheadOfTheEditorsBuildDoesNotRunUnderIt() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            TileKey square = null;

            for (TileKey tile : session.getGraph().getTiles().keySet())
            {
                if (square == null && "1 - Main".equals(tile.getPage())) square = tile;
            }

            java.lang.reflect.Field startField = TrainControlUI.class.getDeclaredField("startAutonomy");

            startField.setAccessible(true);

            final javax.swing.JButton start = (javax.swing.JButton) startField.get(ui[0]);

            assertTrue(start.isEnabled(), "precondition: Start is not offered on his railway");

            startAnsweringYes(asked, going);

            holdTheEventThread(1500);

            SwingUtilities.invokeLater(() -> start.doClick());

            final TrainControlUI window = ui[0];
            final TileKey at = square;

            SwingUtilities.invokeLater(() -> window.openAutonomyEditor(at, Collections.<TileKey>emptyList()));

            long until = System.currentTimeMillis() + 15000;

            while (!(ui[0].isLayoutEditorOpen() && ui[0].getModel().isAutonomyRunning())
                && System.currentTimeMillis() < until)
            {
                Thread.sleep(200);
            }

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(ui[0].isLayoutEditorOpen() && ui[0].getModel().isAutonomyRunning(), "Start, pressed ahead of an"
                + " editor asked for in the same busy moment, started the trains under the editor (RLV13-C2): asked "
                + asked);
        }
        finally
        {
            going.set(false);

            stopWhatTheClaimSent(ui[0]);

            try
            {
                if (ui[0] != null) closeTheEditor(ui[0]);
            }
            finally
            {
                putTheFolderBack(folderWas);

                if (ui[0] != null)
                {
                    final TrainControlUI closing = ui[0];

                    SwingUtilities.invokeAndWait(() -> closing.dispose());
                }

                if (sandbox != null) sandbox.close();
            }
        }
    }

    /**
     * An editor asked for at rest is not built over a run that began before its build ran, and the Edit button comes back
     * (RLV13-C2).  The request asked whether trains run and posted the build, which asked nothing.  Return Home's
     * planning stands in for the run here: it is what makes the window busy with nothing yet moving.
     *
     * MUTATION: let the posted build open the editor without asking again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEditorIsNotBuiltOverARunBegunBeforeItsBuild() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        final java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        staging.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            TileKey square = null;

            for (TileKey tile : session.getGraph().getTiles().keySet())
            {
                if (square == null && "1 - Main".equals(tile.getPage())) square = tile;
            }

            startAnsweringYes(asked, going);

            final TrainControlUI window = ui[0];
            final TileKey at = square;

            // THE REQUEST, and then - before its posted build runs - a run begins
            SwingUtilities.invokeAndWait(() ->
            {
                window.openAutonomyEditor(at, Collections.<TileKey>emptyList());

                try
                {
                    staging.set(window, true);
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(1000);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(ui[0].isLayoutEditorOpen(), "an editor asked for at rest was built over a run that began before"
                + " its build ran (RLV13-C2)");

            assertTrue(asked.contains(I18n.t("autolayout.errorCannotEditWhileRunning")), "the editor's build, refused"
                + " once a run had begun, did not say why: " + asked);

            java.lang.reflect.Field button = TrainControlUI.class.getDeclaredField("editLayoutButton");

            button.setAccessible(true);

            assertTrue(((javax.swing.JButton) button.get(ui[0])).isEnabled(), "the editor's build refused, and did not"
                + " give the Edit button back");
        }
        finally
        {
            going.set(false);

            if (ui[0] != null) staging.set(ui[0], false);

            try
            {
                if (ui[0] != null) closeTheEditor(ui[0]);
            }
            finally
            {
                putTheFolderBack(folderWas);

                if (ui[0] != null)
                {
                    final TrainControlUI closing = ui[0];

                    SwingUtilities.invokeAndWait(() -> closing.dispose());
                }

                if (sandbox != null) sandbox.close();
            }
        }
    }

    /**
     * The diagram's Edit Locomotive refuses at OK once a run has begun while its dialog was open (RLV13-C9).  The run is
     * begun from the answering thread, which is how one starts while the dialog holds the window: the scripting door,
     * `requestStartAutonomy`, is on no window.
     *
     * MUTATION: take the refusal at OK out, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsEditLocomotiveRefusesAtOkOnceARunBegins() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final Thread[] driving = new Thread[1];

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();
            final String inUse = session.getStore().getActiveConfiguration();
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            org.traincontrol.automation.Point at = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (at == null && p.isDestination() && p.getCurrentLocomotive() != null
                    && session.getStationIndex().squareOf(p.getName()) != null)
                {
                    at = p;
                }
            }

            assertNotNull(at, "precondition: no station with a train standing on it");

            final TileKey square = session.getStationIndex().squareOf(at.getName());
            final String standing = at.getCurrentLocomotive().getName();

            java.lang.reflect.Constructor<?> make = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu")
                .getDeclaredConstructors()[0];

            make.setAccessible(true);

            final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    menu[0] = (javax.swing.JPopupMenu) make.newInstance(ui[0], null, square, null);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            java.lang.reflect.Method label = org.traincontrol.gui.GraphLocAssign.class.getDeclaredMethod("menuLabelFor",
                org.traincontrol.automation.Point.class);

            label.setAccessible(true);

            final javax.swing.JMenuItem edit = itemCalled(menu[0], (String) label.invoke(null, at));

            assertNotNull(edit, "precondition: the diagram's menu at " + square + " has no Edit Locomotive item");

            session.getStore().getConfiguration(inUse).getJSONObject("points").getJSONObject(square.toString())
                .remove(AutonomyBuilder.FACING);

            org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.NOT_KNOWN);

            // THE ANSWERING: the Edit dialog's OK only after a run has begun; every other dialog answered and recorded
            final TrainControlUI window = ui[0];

            Thread answering = new Thread(() ->
            {
                java.util.Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

                while (going.get())
                {
                    try
                    {
                        Thread.sleep(150);
                    }
                    catch (InterruptedException stop)
                    {
                        return;
                    }

                    for (java.awt.Window w : java.awt.Window.getWindows())
                    {
                        if (!w.isShowing() || !(w instanceof javax.swing.JDialog)) continue;

                        javax.swing.JOptionPane pane = findPane(((javax.swing.JDialog) w).getContentPane());

                        if (pane == null || !handled.add(pane)) continue;

                        if (pane.getMessage() instanceof java.awt.Component && driving[0] == null)
                        {
                            try
                            {
                                driving[0] = (Thread) dispatchATrainFrom(window, standing)[3];
                            }
                            catch (Exception e)
                            {
                                asked.add("could not begin a run: " + e);
                            }
                        }
                        else
                        {
                            asked.add(String.valueOf(pane.getMessage()));
                        }

                        final Object[] options = pane.getOptions();

                        SwingUtilities.invokeLater(() -> pane.setValue(options != null && options.length > 0
                            ? options[0] : Integer.valueOf(javax.swing.JOptionPane.OK_OPTION)));
                    }
                }
            }, "answering at OK");

            answering.setDaemon(true);
            answering.start();

            SwingUtilities.invokeAndWait(() -> edit.doClick());

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertNotNull(driving[0], "precondition: no run began while the Edit dialog was open: " + asked);

            org.json.JSONObject after = session.getStore().getConfiguration(inUse).getJSONObject("points")
                .getJSONObject(square.toString());

            assertFalse(after.has(AutonomyBuilder.FACING), "the diagram's Edit Locomotive, answered OK after a run began"
                + " while its dialog was open, wrote " + standing + "'s facing into the setup (RLV13-C9): " + after);

            assertTrue(asked.contains(I18n.t("autolayout.errorCannotEditWhileRunning")), "the diagram's Edit Locomotive,"
                + " answered OK once a run had begun, did not say why it did nothing: " + asked);
        }
        finally
        {
            going.set(false);

            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            if (driving[0] != null) driving[0].interrupt();

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The first option pane anywhere in this container. */
    private static javax.swing.JOptionPane findPane(java.awt.Container container)
    {
        for (java.awt.Component c : container.getComponents())
        {
            if (c instanceof javax.swing.JOptionPane) return (javax.swing.JOptionPane) c;

            if (c instanceof java.awt.Container)
            {
                javax.swing.JOptionPane found = findPane((java.awt.Container) c);

                if (found != null) return found;
            }
        }

        return null;
    }

    /** The import door's dispatch, leaving this train standing: its name, where it set off, where it is bound, the thread. */
    private static Object[] dispatchATrainFrom(TrainControlUI ui, String notThis) throws Exception
    {
        return testTheImportDoorReadsAnOldFile.dispatchATrain(ui, notThis);
    }

    /** An Auto tab list offering a standing train a path, and the train. */
    private static Object[] anAutoTabListOfPaths(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field panelField = TrainControlUI.class.getDeclaredField("autoLocPanel");
        java.lang.reflect.Field pathsField = org.traincontrol.gui.AutoLocomotiveStatus.class.getDeclaredField("paths");
        java.lang.reflect.Field listField = org.traincontrol.gui.AutoLocomotiveStatus.class.getDeclaredField("locAvailPaths");
        java.lang.reflect.Field locField = org.traincontrol.gui.AutoLocomotiveStatus.class.getDeclaredField("locomotive");

        panelField.setAccessible(true);
        pathsField.setAccessible(true);
        listField.setAccessible(true);
        locField.setAccessible(true);

        long until = System.currentTimeMillis() + 30000;

        while (System.currentTimeMillis() < until)
        {
            SwingUtilities.invokeAndWait(() -> { });

            for (Component c : ((javax.swing.JPanel) panelField.get(ui)).getComponents())
            {
                if (!(c instanceof org.traincontrol.gui.AutoLocomotiveStatus)) continue;

                List<?> paths = (List<?>) pathsField.get(c);
                javax.swing.JList<?> list = (javax.swing.JList<?>) listField.get(c);

                if (paths != null && !paths.isEmpty() && list.getModel().getSize() > 0)
                {
                    return new Object[] {list, locField.get(c)};
                }
            }

            Thread.sleep(250);
        }

        throw new AssertionError("precondition: no train on the Auto tab offers a path");
    }

    /**
     * Sets whether an editor is on its way, as Edit Layout does before it posts the editor's build, and asks the one gate
     * - both on the event thread, where the doors ask it.
     */
    private static String askTheGate(final TrainControlUI ui, final boolean editorAskedFor, final boolean start)
        throws Exception
    {
        final java.lang.reflect.Field onItsWay = TrainControlUI.class.getDeclaredField("editorOnItsWay");

        final java.lang.reflect.Method gate = TrainControlUI.class.getDeclaredMethod("whyNoTrainMayBeSent", boolean.class);

        onItsWay.setAccessible(true);

        gate.setAccessible(true);

        final Object[] said = new Object[1];

        final Exception[] failed = new Exception[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                onItsWay.setBoolean(ui, editorAskedFor);

                said[0] = gate.invoke(ui, start);
            }
            catch (ReflectiveOperationException e)
            {
                failed[0] = e;
            }
        });

        if (failed[0] != null) throw failed[0];

        return (String) said[0];
    }

    /** Holds the event thread for a while, as a slow refresh does, so what is queued behind it runs in one go. */
    private static void holdTheEventThread(final long millis)
    {
        SwingUtilities.invokeLater(() ->
        {
            try
            {
                Thread.sleep(millis);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * A train's length is not changed while trains run (RSA-A1; Adam, MT-141: *"Never allow any modifications to a
     * running layout.  This includes locomotive database"*).  The locomotive's Autonomy train length item wrote the
     * length straight onto the running train, 0 included, and a non-atomic run hands the track behind a train back by that
     * number.  Refused at the click, and again at OK for a dialog that was open when a run began; at rest it writes.
     *
     * MUTATION: let the dialog's answer, or the click, through while trains run, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATrainsLengthIsNotChangedWhileTrainsRun() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        org.traincontrol.automation.Layout railway = null;

        org.traincontrol.base.Locomotive loc = null;

        Integer was = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            railway = ui.getModel().getAutoLayout();

            loc = ui.getModel().getLocByName(ui.getModel().getLocList().get(0));

            was = loc.getTrainLength();

            final int other = was == null || was != 7 ? 7 : 8;

            final TrainControlUI window = ui;
            final org.traincontrol.base.Locomotive train = loc;

            startAnsweringYes(asked, going);

            final String refused = I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning");

            // TRAINS RUNNING, as Return Home's planning makes the railway busy
            railway.setStagingInProgress(true);

            try
            {
                // AT OK: the dialog's answer, for a dialog opened at rest
                SwingUtilities.invokeAndWait(() -> window.applyTrainLength(train, other));

                assertEquals(loc.getTrainLength(), was, "a train's length was written while trains ran (RSA-A1): asked "
                    + asked);

                assertTrue(asked.contains(refused), "the length was refused without saying why: asked " + asked);

                // AT THE CLICK: no length is asked for at all
                asked.clear();

                SwingUtilities.invokeAndWait(() -> window.promptTrainLength(train, null));

                assertFalse(asked.contains(I18n.t("autolayout.ui.trainLength")), "a train's length was asked for while"
                    + " trains ran (RSA-A1): asked " + asked);

                assertTrue(asked.contains(refused), "the click was refused without saying why: asked " + asked);
            }
            finally
            {
                railway.setStagingInProgress(false);
            }

            // THE CONTROL: at rest the same door writes
            SwingUtilities.invokeAndWait(() -> window.applyTrainLength(train, other));

            assertEquals(loc.getTrainLength(), Integer.valueOf(other), "precondition: the door writes nothing even at"
                + " rest, so this claim cannot tell a refusal from a broken door");
        }
        finally
        {
            going.set(false);

            if (loc != null) loc.setTrainLength(was);

            putTheFolderBack(folderWas);

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The Auto tab sends no train while Return Home owns the railway (RSA-C2).  It asked only whether autonomy's own flag
     * was up, and that is down while Return Home plans - so a train sent by hand then was dispatched under the planner,
     * and the press of Return Home was spent on positions the railway no longer had.  A second hand send while another
     * runs stays allowed from the Auto tab (Adam, 2026-08-31, OB-164: *"The user can rely on full autonomy or the panels
     * to send trains more clearly."*).
     *
     * MUTATION: let the Auto tab's double-click ask only whether autonomy is running, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheAutoTabSendsNothingWhileReturnHomeOwnsTheRailway() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            startAnsweringYes(asked, going);

            Object[] offered = anAutoTabListOfPaths(ui[0]);

            final org.traincontrol.base.Locomotive sent = (org.traincontrol.base.Locomotive) offered[1];

            // RETURN HOME PLANNING: the railway owned by a staging flow, nothing yet running
            railway.setStagingInProgress(true);

            try
            {
                doubleClickTheFirstPath((javax.swing.JList<?>) offered[0]);

                Thread.sleep(3000);

                assertFalse(railway.isAlreadyUnderway(sent), "the Auto tab sent " + sent.getName() + " by hand while"
                    + " Return Home was planning (RSA-C2): asked " + asked);
            }
            finally
            {
                railway.setStagingInProgress(false);
            }

            // THE CONTROL: at rest the same double-click sends it
            offered = anAutoTabListOfPaths(ui[0]);

            final org.traincontrol.base.Locomotive control = (org.traincontrol.base.Locomotive) offered[1];

            doubleClickTheFirstPath((javax.swing.JList<?>) offered[0]);

            long until = System.currentTimeMillis() + 15000;

            while (!railway.isAlreadyUnderway(control) && System.currentTimeMillis() < until) Thread.sleep(200);

            assertTrue(railway.isAlreadyUnderway(control), "precondition: the double-click sends nothing even at rest, so"
                + " this claim cannot tell a refusal from a broken door: asked " + asked);
        }
        finally
        {
            going.set(false);

            stopWhatTheClaimSent(ui[0]);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** A double-click on the first path of an Auto tab list, as the operator makes it. */
    private static void doubleClickTheFirstPath(final javax.swing.JList<?> list) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            list.setSize(400, Math.max(100, list.getPreferredSize().height));

            java.awt.Rectangle cell = list.getCellBounds(0, 0);

            list.dispatchEvent(new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, cell.x + cell.width / 2, cell.y + cell.height / 2, 2, false,
                java.awt.event.MouseEvent.BUTTON1));
        });
    }
}
