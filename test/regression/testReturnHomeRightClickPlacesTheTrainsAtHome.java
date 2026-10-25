package regression;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import static regression.testTheImportDoorReadsAnOldFile.closeTheEditor;
import static regression.testTheImportDoorReadsAnOldFile.logged;
import static regression.testTheImportDoorReadsAnOldFile.openTheEditorFor;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import static regression.testTheImportDoorReadsAnOldFile.putTheFolderBack;

/**
 * Right-clicking Return Home offers to put the trains at their homes without moving them (FR-129; Adam: "make it so that
 * when the return home button is right clicked, an option to just teleport the locomotives back (same as what the
 * autonomy right click menu allows) is shown.  Show a confirmation window that asks if the user wants to do this, and
 * confirms they will manually move the trains back"; and "the option should only be allowed when nothing is running").
 *
 * On the live snapshot's window.  The item is Place All Trains at Their Homes, which the editor's Bulk Tools menu offers
 * on the editor's session; here it lands on the live setup and is saved with it, as a placement made from the track
 * diagram is.  One question, `whyTheTrainsCannotBePlacedAtTheirHomes`, grays the item and refuses its press, asked again
 * after the confirmation.
 *
 * @author Adam
 */
public class testReturnHomeRightClickPlacesTheTrainsAtHome
{
    /** A right-click on the button, delivered as the mouse delivers it - to a grayed button too. */
    private static void rightClick(final TrainControlUI ui) throws Exception
    {
        final javax.swing.JButton button = returnHomeButton(ui);

        SwingUtilities.invokeAndWait(() -> button.dispatchEvent(new java.awt.event.MouseEvent(button,
            java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1, true, java.awt.event.MouseEvent.BUTTON3)));
    }

    private static javax.swing.JButton returnHomeButton(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("returnHomeButton");

        field.setAccessible(true);

        return (javax.swing.JButton) field.get(ui);
    }

    /** The menu a right-click on the button opens, which holds Run Trains to Their Homes and, below it, Place All. */
    private static JPopupMenu theMenu(TrainControlUI ui) throws Exception
    {
        JPopupMenu last = ui.returnHomePopupForTest();

        rightClick(ui);

        JPopupMenu popup = ui.returnHomePopupForTest();

        assertNotNull(popup, "a right-click on Return Home opened no menu");

        assertNotSame(popup, last, "a right-click on Return Home, with the button "
            + (returnHomeButton(ui).isEnabled() ? "enabled" : "greyed") + ", opened no new menu");

        assertEquals(popup.getComponentCount(), 2, "the menu holds more or less than the two items");

        return popup;
    }

    /** Place All Trains at Their Homes, with the count of homes, in the popup the last right-click opened. */
    private static JMenuItem theItem(TrainControlUI ui) throws Exception
    {
        return itemSaying(theMenu(ui), I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome",
            ui.getAutonomySession().homesToPlace().size()));
    }

    /** Run Trains to Their Homes, in the popup the last right-click opened. */
    private static JMenuItem theRunItem(TrainControlUI ui) throws Exception
    {
        return itemSaying(theMenu(ui), I18n.t("autolayout.ui.menuRunTrainsToTheirHomes"));
    }

    /** The item of a menu that says this, found by its words rather than its place (FR-136 Phase 6). */
    private static JMenuItem itemSaying(JPopupMenu menu, final String text)
    {
        JMenuItem item = support.Components.find(menu, JMenuItem.class, i -> text.equals(i.getText()));

        assertNotNull(item, "the Return Home menu has no item saying \"" + text + "\": " + support.Components.all(menu,
            JMenuItem.class, null).stream().map(JMenuItem::getText).collect(java.util.stream.Collectors.toList()));

        return item;
    }

    /** A tooltip's words, without the markup that wraps it. */
    private static String plain(String tip)
    {
        return tip == null ? null : tip.replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&");
    }

    private static void closeDown(TrainControlUI ui, support.LayoutSandbox sandbox, String folderWas) throws Exception
    {
        putTheFolderBack(folderWas);

        if (ui != null) SwingUtilities.invokeAndWait(() -> ui.dispose());

        if (sandbox != null) sandbox.close();
    }

    /**
     * The menu offers the item with the number of homes, on a button that is enabled and on one that is grayed - Swing
     * delivers the mouse to a disabled button, and a right-click that did nothing would not say why.
     *
     * MUTATION: open the menu only when the button is enabled, or leave the count out of the label, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARightClickOffersToPlaceTheTrainsAtTheirHomes() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            int homes = ui.getAutonomySession().homesToPlace().size();

            assertTrue(homes > 0, "precondition: no train of the live snapshot has a home");

            for (boolean enabled : new boolean[] {true, false})
            {
                final boolean on = enabled;
                final javax.swing.JButton button = returnHomeButton(ui);

                SwingUtilities.invokeAndWait(() -> button.setEnabled(on));

                JMenuItem item = theItem(ui);

                assertEquals(item.getText(), I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes),
                    "the menu does not offer Place All Trains at Their Homes, with its count, with the button "
                    + (enabled ? "enabled" : "greyed"));

                assertTrue(item.isEnabled(), "the item is greyed at rest, with homes to place and the button "
                    + (enabled ? "enabled" : "greyed") + ": " + item.getToolTipText());

                // ABOVE it, and the button's own state and reason (FR-129)
                JMenuItem run = (JMenuItem) ui.returnHomePopupForTest().getComponent(0);

                assertEquals(run.getText(), I18n.t("autolayout.ui.menuRunTrainsToTheirHomes"),
                    "the first item of the menu is not Run Trains to Their Homes, above Place All Trains at Their Homes");

                assertEquals(run.isEnabled(), enabled, "Run Trains to Their Homes is not enabled exactly when the button is,"
                    + " with the button " + (enabled ? "enabled" : "greyed"));

                if (!enabled)
                {
                    assertEquals(run.getToolTipText(), button.getToolTipText(),
                        "the greyed Run Trains to Their Homes does not say what the button says");
                }
            }
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * The item is grayed, with the reason, while autonomy, Return Home or a route runs, while an editor is open and when no
     * train has a home - and a press that arrives after one of them began says why and changes nothing.
     *
     * MUTATION: leave any one of the cases out of `whyTheTrainsCannotBePlacedAtTheirHomes`, or let the press skip the
     * question, and this fails naming it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheItemIsGreyedWithItsReasonUnlessNothingIsRunning() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final String probe = "FR-129 probe route";

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final MarklinControlStation model = (MarklinControlStation) ui.getModel();
            final AutonomySession session = ui.getAutonomySession();
            final Layout layout = model.getAutoLayout();

            assertNull(ui.whyTheTrainsCannotBePlacedAtTheirHomes(), "precondition: at rest, with homes, the item is refused");

            final String running = I18n.t("ui.main.tooltip.placeAtHomesWhileRunning");

            // AUTONOMY, OR A TIMETABLE: the railway's own running flag
            java.lang.reflect.Field flag = Layout.class.getDeclaredField("running");

            flag.setAccessible(true);
            flag.setBoolean(layout, true);

            try
            {
                greyedWith(ui, running, "autonomy running");
            }
            finally
            {
                flag.setBoolean(layout, false);
            }

            // RETURN HOME, as the window marks it on the railway
            layout.setStagingInProgress(true);

            try
            {
                greyedWith(ui, running, "Return Home running");
            }
            finally
            {
                layout.setStagingInProgress(false);
            }

            // A ROUTE, run by hand with autonomy idle
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

            assertTrue(model.newRoute(probe, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "precondition: the probe route could not be made");

            assertTrue(model.getRoute(probe).setExecuting(), "precondition: the probe route would not run");

            try
            {
                greyedWith(ui, running, "a route running");
            }
            finally
            {
                model.getRoute(probe).stopExecuting();
            }

            assertNull(ui.whyTheTrainsCannotBePlacedAtTheirHomes(), "precondition: the railway is at rest again");

            // A PRESS THAT ARRIVES AFTER A RUN BEGAN: the menu was built at rest
            final Map<TileKey, String> before = new LinkedHashMap<>(session.placementsAutonomyWillWrite());

            final JMenuItem stale = theItem(ui);

            assertTrue(stale.isEnabled(), "precondition: the item is greyed at rest");

            flag.setBoolean(layout, true);

            List<String> asked;

            try
            {
                asked = answering(message -> 0, () -> stale.doClick());
            }
            finally
            {
                flag.setBoolean(layout, false);
            }

            assertEquals(asked, Collections.singletonList(running), "the item pressed during a run did not say why it did"
                + " nothing, or asked first: " + asked);

            assertEquals(session.placementsAutonomyWillWrite(), before, "the item pressed during a run changed the setup");

            // NO TRAIN WITH A HOME
            session.clearEveryHome();

            greyedWith(ui, I18n.t("autosetup.ui.infoNoHomesToPlace"), "no train having a home");

            // AN EDITOR HOLDING THE SETUP
            TileKey square = null;

            for (TileKey tile : session.getGraph().getTiles().keySet())
            {
                if (square == null && "1 - Main".equals(tile.getPage())) square = tile;
            }

            assertNotNull(square, "precondition: no square on the main page");

            openTheEditorFor(ui, square, Collections.<TileKey>emptyList());

            try
            {
                assertTrue(ui.isLayoutEditorOpen(), "precondition: the editor did not open");

                greyedWith(ui, I18n.t("autosetup.ui.menuEditorOpen"), "an editor open");
            }
            finally
            {
                closeTheEditor(ui);
            }
        }
        finally
        {
            if (ui != null && ui.getModel().getRoute(probe) != null) ui.getModel().deleteRoute(probe);

            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * Run Trains to Their Homes is the button: pressed in the same state it gets the same answer through the same door.
     * An open editor refuses both with the one sentence, and the item does not start a run the button would not.
     *
     * MUTATION: have the item do anything but call the button's `requestReturnToHome`, and the two answers differ.
     *
     * @throws Exception from the window
     */
    @Test
    public void testRunTrainsToTheirHomesIsTheButtonItself() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final javax.swing.JButton button = returnHomeButton(ui);

            SwingUtilities.invokeAndWait(() -> button.setEnabled(true));

            TileKey square = null;

            for (TileKey tile : ui.getAutonomySession().getGraph().getTiles().keySet())
            {
                if (square == null && "1 - Main".equals(tile.getPage())) square = tile;
            }

            assertNotNull(square, "precondition: no square on the main page");

            openTheEditorFor(ui, square, Collections.<TileKey>emptyList());

            try
            {
                assertTrue(ui.isLayoutEditorOpen(), "precondition: the editor did not open");

                final JMenuItem run = theRunItem(ui);

                assertTrue(run.isEnabled(), "precondition: the item is greyed with the button enabled");

                List<String> byButton = answering(message -> 0, () -> button.doClick());
                List<String> byItem = answering(message -> 0, () -> run.doClick());

                assertEquals(byButton.size(), 1, "precondition: the button, with an editor open, did not refuse once: "
                    + byButton);

                assertEquals(byItem, byButton, "Run Trains to Their Homes did not meet the same refusal as the button");
            }
            finally
            {
                closeTheEditor(ui);
            }
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /** The window's question and the right-click item both give this reason, and the item is grayed. */
    private static void greyedWith(TrainControlUI ui, String reason, String when) throws Exception
    {
        assertEquals(ui.whyTheTrainsCannotBePlacedAtTheirHomes(), reason, "the question does not give its reason for " + when);

        JMenuItem item = theItem(ui);

        assertFalse(item.isEnabled(), "the item is offered with " + when);

        assertTrue(item.getToolTipText() != null && item.getToolTipText().contains(reason),
            "the greyed item does not say why with " + when + ": " + item.getToolTipText());
    }

    /**
     * Answering No at the confirmation changes nothing, and the confirmation says the trains must be moved by hand.
     *
     * MUTATION: let No fall through to the placement, or drop the by-hand sentence from the text, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testNoLeavesTheSetupAsItWas() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final AutonomySession session = ui.getAutonomySession();

            Map<TileKey, String> homes = session.homesToPlace();

            String stray = aTrainWithNoHome(ui, homes);

            session.clearEveryPlacement();
            session.placeLocomotive(homes.keySet().iterator().next(), stray);

            final Map<TileKey, String> before = new LinkedHashMap<>(session.placementsAutonomyWillWrite());

            final JMenuItem item = theItem(ui);

            int logSize = logged().length();

            List<String> asked = answering(message -> 1, () -> item.doClick());

            assertEquals(asked.size(), 1, "pressing the item asked " + asked.size() + " questions: " + asked);

            assertTrue(asked.get(0).contains(I18n.f("autolayout.ui.confirmPlaceEveryTrainAtHomeByHand", 0, 0)
                .split("\n")[2]), "the confirmation does not say the trains must be moved by hand: " + asked);

            assertEquals(session.placementsAutonomyWillWrite(), before, "answering No changed the setup");

            assertFalse(logged().substring(logSize).contains(saidPlaced(homes.size())),
                "answering No said the trains were placed");
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * Answering Yes puts each homed train on its home and takes the others off, on the live setup, and the file on disk has
     * the same; the result is said in the log.
     *
     * MUTATION: skip the save, or the placement, or leave a train off the names the rebuild is told, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testYesPutsTheTrainsOnTheirHomesAndSavesIt() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final AutonomySession session = ui.getAutonomySession();

            Map<TileKey, String> homes = new LinkedHashMap<>(session.homesToPlace());

            String stray = aTrainWithNoHome(ui, homes);

            // THE TRAINS ELSEWHERE: a train with no home on a home, and the homed trains nowhere
            session.clearEveryPlacement();
            session.placeLocomotive(homes.keySet().iterator().next(), stray);

            assertEquals(session.placementsAutonomyWillWrite().get(homes.keySet().iterator().next()), stray,
                "precondition: the stray train was not placed");

            final JMenuItem item = theItem(ui);

            int logSize = logged().length();

            List<String> asked = answering(message -> 0, () -> item.doClick());

            assertEquals(asked.size(), 1, "pressing the item asked " + asked.size() + " questions: " + asked);

            for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertHomed(session.placementsAutonomyWillWrite(), homes, stray, "the live setup");

            // AND THE FILE A RELOAD READS
            List<org.traincontrol.base.LayoutDiagram> pages = new ArrayList<>();

            for (String page : ui.getModel().getLayoutList()) pages.add(ui.getModel().getLayout(page));

            AutonomySession reloaded = new AutonomySession(sandbox.getFolder());

            reloaded.open(pages);

            assertHomed(reloaded.placementsAutonomyWillWrite(), homes, stray, "the setup read back from disk");

            assertTrue(logged().substring(logSize).contains(saidPlaced(homes.size())), "the result was not said in the log");
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /**
     * The track diagram's own Bulk Tools item is the same door as Return Home's: refused while anything runs, asking with
     * the by-hand sentence, and saying its result in the log rather than on a banner the track diagram does not have.
     *
     * MUTATION: let the track diagram's item ask the editor's question and say its result on the editor's banner, as it
     * did, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheTrackDiagramsBulkToolsItemIsTheSameDoor() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final TrainControlUI window = ui;
            final AutonomySession session = ui.getAutonomySession();
            final Layout layout = ((MarklinControlStation) ui.getModel()).getAutoLayout();

            Map<TileKey, String> homes = new LinkedHashMap<>(session.homesToPlace());

            String stray = aTrainWithNoHome(ui, homes);

            session.clearEveryPlacement();
            session.placeLocomotive(homes.keySet().iterator().next(), stray);

            // WHILE A RUN IS UNDER WAY: grayed with Return Home's reason
            java.lang.reflect.Field flag = Layout.class.getDeclaredField("running");

            flag.setAccessible(true);
            flag.setBoolean(layout, true);

            try
            {
                JMenuItem running = trackDiagramsPlaceItem(window, session);

                assertFalse(running.isEnabled(), "the track diagram's item is offered while autonomy runs");

                assertTrue(running.getToolTipText() != null
                    && running.getToolTipText().contains(I18n.t("ui.main.tooltip.placeAtHomesWhileRunning")),
                    "the track diagram's greyed item does not say why: " + running.getToolTipText());
            }
            finally
            {
                flag.setBoolean(layout, false);
            }

            // AT REST: the by-hand question, and the result in the log
            final JMenuItem item = trackDiagramsPlaceItem(window, session);

            assertTrue(item.isEnabled(), "precondition: the track diagram's item is greyed at rest");

            int logSize = logged().length();

            List<String> asked = answering(message -> 0, () -> item.doClick());

            String byHand = I18n.f("autolayout.ui.confirmPlaceEveryTrainAtHomeByHand", 987654, 876543);

            byHand = byHand.substring(byHand.indexOf("876543") + "876543".length());

            assertEquals(asked.size(), 1, "the track diagram's item asked " + asked.size() + " questions: " + asked);

            assertTrue(asked.get(0).endsWith(byHand), "the track diagram's item did not ask with the by-hand sentence: "
                + asked.get(0));

            for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertHomed(session.placementsAutonomyWillWrite(), homes, stray, "the live setup");

            assertTrue(logged().substring(logSize).contains(saidPlaced(homes.size())),
                "the track diagram's item did not say its result in the log");
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /** Place All Trains at Their Homes, from the Bulk Tools menu of the track diagram's right-click on some square. */
    private static JMenuItem trackDiagramsPlaceItem(final TrainControlUI ui, AutonomySession session) throws Exception
    {
        final String label = I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", session.homesToPlace().size());

        final List<TileKey> squares = new ArrayList<>(session.getGraph().getTiles().keySet());

        final JMenuItem[] found = new JMenuItem[1];

        SwingUtilities.invokeAndWait(() ->
        {
            for (TileKey square : squares)
            {
                JPopupMenu menu = found[0] == null ? ui.buildAutonomyTileMenu(square) : null;

                if (menu != null) found[0] = itemNamed(menu.getSubElements(), label);
            }
        });

        assertNotNull(found[0], "no square's right-click on the track diagram offers " + label);

        return found[0];
    }

    private static JMenuItem itemNamed(javax.swing.MenuElement[] elements, String label)
    {
        for (javax.swing.MenuElement element : elements)
        {
            if (element instanceof JMenuItem && label.equals(((JMenuItem) element).getText())) return (JMenuItem) element;

            JMenuItem inside = itemNamed(element.getSubElements(), label);

            if (inside != null) return inside;
        }

        return null;
    }

    /**
     * A run that begins while the confirmation is on screen stops the placement: the question is asked again after it.
     *
     * MUTATION: take the second question out of the panel's door, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARunThatBeginsWhileTheConfirmationIsUpStopsTheChange() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field flag = Layout.class.getDeclaredField("running");

        flag.setAccessible(true);

        Layout layout = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final AutonomySession session = ui.getAutonomySession();

            layout = ui.getModel().getAutoLayout();

            final Layout running = layout;

            Map<TileKey, String> homes = session.homesToPlace();

            session.clearEveryPlacement();
            session.placeLocomotive(homes.keySet().iterator().next(), aTrainWithNoHome(ui, homes));

            final Map<TileKey, String> before = new LinkedHashMap<>(session.placementsAutonomyWillWrite());

            final JMenuItem item = theItem(ui);

            List<String> asked = answering(message ->
            {
                // THE RUN BEGINS WHILE THE CONFIRMATION IS UP
                if (!message.equals(I18n.t("ui.main.tooltip.placeAtHomesWhileRunning")))
                {
                    try
                    {
                        flag.setBoolean(running, true);
                    }
                    catch (IllegalAccessException e)
                    {
                        throw new IllegalStateException(e);
                    }
                }

                return 0;
            }, () -> item.doClick());

            assertEquals(asked.size(), 2, "expected the confirmation and then the refusal: " + asked);

            assertEquals(asked.get(1), I18n.t("ui.main.tooltip.placeAtHomesWhileRunning"),
                "the placement was not refused, or not said, once a run began under the confirmation: " + asked);

            assertEquals(ui.getAutonomySession().placementsAutonomyWillWrite(), before,
                "a run began while the confirmation was up and the setup was changed all the same");
        }
        finally
        {
            if (layout != null) flag.setBoolean(layout, false);

            closeDown(ui, sandbox, folderWas);
        }
    }

    /** Waits until the window's Return Home triage has answered every ask, then lets the event thread paint it. */
    private static void awaitTheButton(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Method triage = TrainControlUI.class.getDeclaredMethod("awaitReturnHomeTriage", long.class);

        triage.setAccessible(true);

        assertTrue((Boolean) triage.invoke(ui, 30000L), "precondition: the Return Home triage did not settle");

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });

        triage.invoke(ui, 30000L);

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * Return Home does not say "already on its home" over a train that is paused away from its home (OB-355; Adam: "'every
     * locomotive is already at its home' is shown, and return home is greyed out, even if the reason is that locomotives are
     * inactive but not on their stations.").  With every other train home and one homed train paused away, the button is
     * grayed and says the train is paused and how to set it going - the menu's Run Trains to Their Homes the same;
     * set going, the button is offered; with every train home, it says they are.
     *
     * MUTATION: let the triage count a paused train as home without saying so (answer ALREADY_HOME for it), and the first
     * reason fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAPausedTrainAwayFromHomeIsNotSaidToBeHome() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        TrainControlUI ui = null;

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui = openTheWindow();

            final TrainControlUI window = ui;
            final AutonomySession session = ui.getAutonomySession();

            // EVERY HOMED TRAIN ON ITS HOME, by the menu's own item
            Map<TileKey, String> homes = new LinkedHashMap<>(session.homesToPlace());

            final JMenuItem place = theItem(ui);

            answering(message -> 0, () -> place.doClick());

            for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

            final Layout layout = ui.getModel().getAutoLayout();

            org.traincontrol.base.Locomotive train = ui.getModel().getLocByName(homes.values().iterator().next());

            org.traincontrol.automation.Point home = layout.getHomeStation(train);

            assertNotNull(home, "precondition: " + train.getName() + " has no home");

            awaitTheButton(ui);

            final javax.swing.JButton button = returnHomeButton(ui);
            final String atHome = I18n.t("autolayout.ui.infoAllLocomotivesAlreadyHome");

            // EVERY TRAIN HOME: the at-home sentence
            assertFalse(button.isEnabled(), "precondition: Return Home is offered with every homed train on its home: "
                + button.getToolTipText());

            assertEquals(plain(button.getToolTipText()), atHome, "with every train home, the button does not say so");

            // ONE AWAY, ACTIVE: offered.  Any other station it will go to.
            boolean away = false;

            for (org.traincontrol.automation.Point there : layout.getPoints())
            {
                if (away) break;

                if (there.getName().equals(home.getName())) continue;

                if (!layout.moveLocomotive(train.getName(), there.getName(), false)) continue;

                org.traincontrol.automation.Point now = layout.getLocomotiveLocation(train);

                if (now != null && !now.isSamePlaceAs(home) && layout.triageReturnToHome() == null) away = true;
                else layout.moveLocomotive(train.getName(), home.getName(), false);
            }

            assertTrue(away, "precondition: " + train.getName() + " could not be stood away from its home");

            SwingUtilities.invokeAndWait(() -> window.trainsPausedChanged());

            awaitTheButton(ui);

            assertTrue(button.isEnabled(), "Return Home is not offered with " + train.getName() + " active and away from"
                + " its home: " + button.getToolTipText());

            // PAUSED, through the door the Is Active tick and the bulk tools use
            final List<org.traincontrol.base.Locomotive> paused = Collections.singletonList(train);

            SwingUtilities.invokeAndWait(() -> window.setLocomotivesActive(session, paused, false));

            awaitTheButton(ui);

            assertFalse(button.isEnabled(), "Return Home is offered with the only train away from home paused");

            assertFalse(atHome.equals(plain(button.getToolTipText())), "the button says every locomotive is home over the paused "
                + train.getName() + ", which stands away from its home");

            String why = I18n.f("autolayout.ui.infoAwayLocomotivesPaused", train.getName());

            // WRAPPED, not one line across the screen (Adam on MT-756: "The tooltip is much too wide")
            assertTrue(button.getToolTipText().contains("width"), "the paused reason is not wrapped: "
                + button.getToolTipText());

            assertEquals(plain(button.getToolTipText()), why, "the paused train away from its home is not the reason given");

            assertTrue(why.contains(train.getName()), "the reason does not name the paused train: " + why);

            JMenuItem run = theRunItem(ui);

            assertFalse(run.isEnabled(), "Run Trains to Their Homes is offered while the button is greyed");

            assertEquals(plain(run.getToolTipText()), why, "Run Trains to Their Homes does not give the button's reason");

            // SET GOING AGAIN: offered
            SwingUtilities.invokeAndWait(() -> window.setLocomotivesActive(session, paused, true));

            awaitTheButton(ui);

            assertTrue(button.isEnabled(), "Return Home is not offered once " + train.getName() + " is set going: "
                + button.getToolTipText());
        }
        finally
        {
            closeDown(ui, sandbox, folderWas);
        }
    }

    /** What the log says of this many trains put on their homes, up to the number taken off. */
    private static String saidPlaced(int homes)
    {
        String said = I18n.f("autosetup.ui.infoTrainsPlacedAtHome", homes, 987654);

        return said.substring(0, said.indexOf("987654"));
    }

    /** A train of the roster that is nobody's home. */
    private static String aTrainWithNoHome(TrainControlUI ui, Map<TileKey, String> homes)
    {
        for (String name : ui.getModel().getLocList())
        {
            if (!homes.containsValue(name)) return name;
        }

        throw new AssertionError("precondition: every train of the live snapshot has a home");
    }

    private static void assertHomed(Map<TileKey, String> placed, Map<TileKey, String> homes, String stray, String where)
    {
        for (Map.Entry<TileKey, String> home : homes.entrySet())
        {
            assertEquals(placed.get(home.getKey()), home.getValue(), home.getValue() + " is not on its home in " + where);
        }

        assertFalse(placed.containsValue(stray), stray + ", which has no home, is still on the layout in " + where);
    }

    // ---------------------------------------------------------------- answering the dialogs

    /**
     * Runs this on the event thread, answering every question by the option the function chooses for its text; what was
     * asked, in order.  A message dialog is answered OK.
     */
    private static List<String> answering(Function<String, Integer> choose, Runnable onTheEventThread) throws Exception
    {
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        Thread answering = new Thread(() ->
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

                    final JOptionPane pane = find(((JDialog) window).getContentPane());

                    if (pane == null || !handled.add(pane)) continue;

                    final String message = String.valueOf(pane.getMessage());

                    asked.add(message);

                    final Object[] options = pane.getOptions();
                    final int option = choose.apply(message);

                    SwingUtilities.invokeLater(() -> pane.setValue(options != null && options.length > option
                        ? options[option] : Integer.valueOf(JOptionPane.OK_OPTION)));
                }
            }
        }, "answering");

        answering.setDaemon(true);
        answering.start();

        final CountDownLatch done = new CountDownLatch(1);

        try
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    onTheEventThread.run();
                }
                finally
                {
                    done.countDown();
                }
            });

            assertTrue(done.await(60, TimeUnit.SECONDS), "the press did not finish; asked: " + asked);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            return new ArrayList<>(asked);
        }
        finally
        {
            going.set(false);

            answering.join(1000);
        }
    }

    private static JOptionPane find(Container container)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JOptionPane) return (JOptionPane) child;

            if (child instanceof Container)
            {
                JOptionPane found = find((Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }
}
