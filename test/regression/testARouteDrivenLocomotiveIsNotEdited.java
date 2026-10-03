package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;

/**
 * A locomotive a running route drives is neither deleted nor renamed, and the refusal names the route (OB-287, CS3-B1).
 *
 * Adam, on MT-464, 2026-09-22: *"make an automated test for this"*.  `core.testAdvancedRoutes` holds the half about the
 * route finishing its commands, and `runningRouteDriving` - the question - is claimed there too; nothing asked whether
 * the doors ask it.  Deleting a locomotive rewrites every route's commands, and renaming it rewrites every command that
 * names it, under a route part-way along its list.
 *
 * The two doors a person uses are driven here on a real window: the refusal is a message dialog, which this closes; had
 * the door gone on, the dialog it shows next is a different one, which this also closes without answering yes.  The
 * third door, the Central Station's name proposal, sits inside a loop over a modal list, so it is read.
 *
 * MUTATION: take the refusal out of either door, and the matching claim fails.
 *
 * @author Adam
 */
public class testARouteDrivenLocomotiveIsNotEdited
{
    private static final String LOC = "OB-287 loc";
    private static final String ROUTE = "OB-287 route";

    /** A second locomotive, for a multi-unit the route's locomotive is ticked into */
    private static final String UNIT = "OB-287 unit";

    /**
     * The delete door and the edit door each refuse, naming the route, and leave the locomotive as it was; and the
     * multi-unit door (Adam, 2026-10-03) - from the locomotive database's menu and the keyboard's, and asked again at OK
     * of a locomotive ticked into another's multi-unit.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testBothDoorsRefuseWhileTheRouteRuns() throws Exception
    {
        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            // OPENED INSIDE THE TRY (TSX-B8, OB-111): anything thrown between the open and the close would leave the
            // layout preference pointing at a folder under %TEMP%.
            sandbox = support.LayoutSandbox.open();

            model = MarklinControlStation.init(null, true, false, false, false);

            final MarklinControlStation m = model;
            final TrainControlUI[] made = new TrainControlUI[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    made[0] = new TrainControlUI();
                    made[0].setViewListener(m, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            ui = made[0];

            model.newMM2Locomotive(LOC, 63);

            // A ROUTE THAT DRIVES IT, and stays running long enough to ask both doors: one function command, with a
            // pause after it.
            List<RouteCommand> commands = new ArrayList<>();

            RouteCommand fires = RouteCommand.RouteCommandFunction(LOC, 1, true);

            fires.setDelay(20000);

            commands.add(fires);

            model.newRoute(ROUTE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            final MarklinControlStation running = model;

            new Thread(() -> running.execRoute(ROUTE)).start();

            long armed = System.currentTimeMillis() + 5000;

            while (!model.getRoute(ROUTE).isExecuting() && System.currentTimeMillis() < armed) Thread.sleep(20);

            assertTrue(model.getRoute(ROUTE).isExecuting(), "precondition: the route never started");

            assertNotNull(model.runningRouteDriving(LOC), "precondition: the model does not say the running route drives"
                + " the locomotive, so no door has anything to ask");

            String refusal = I18n.f("loc.ui.errorLocomotiveDrivenByARunningRoute", LOC, ROUTE);

            final TrainControlUI window = ui;

            // THE DELETE DOOR.
            String said = askAndClose(() -> window.deleteLoc(LOC));

            assertEquals(said, refusal, "the delete door did not refuse a locomotive a running route drives, naming the"
                + " route (OB-287, CS3-B1)");

            assertNotNull(model.getLocByName(LOC), "the delete door deleted a locomotive a running route drives");

            // THE EDIT DOOR, which renames and re-addresses.
            final org.traincontrol.base.Locomotive loc = model.getLocByName(LOC);

            said = askAndClose(() -> window.changeLocAddress(loc, null));

            assertEquals(said, refusal, "the edit door did not refuse a locomotive a running route drives, naming the"
                + " route (OB-287, CS3-B1)");

            assertNotNull(model.getLocByName(LOC), "the edit door renamed a locomotive a running route drives");

            // THE MULTI-UNIT DOOR, from the locomotive database's menu (Adam, 2026-10-03) ...
            final javax.swing.JPopupMenu[] database = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() -> database[0] = new org.traincontrol.gui.RightClickSelectorMenu(window, null, loc));

            final javax.swing.JMenuItem fromTheDatabase = multiUnitItemIn(database[0]);

            assertNotNull(fromTheDatabase, "the locomotive database's menu offers no multi-unit item");

            // WITH A DIVIDER ABOVE IT (Adam, 2026-10-03)
            int at = java.util.Arrays.asList(database[0].getComponents()).indexOf(fromTheDatabase);

            assertTrue(at > 0 && database[0].getComponent(at - 1) instanceof javax.swing.JSeparator, "the locomotive"
                + " database's multi-unit item has no divider above it");

            said = askAndClose(() -> fromTheDatabase.doClick());

            assertEquals(said, refusal, "the multi-unit door, from the locomotive database, did not refuse a locomotive a"
                + " running route drives, naming the route (OB-287)");

            // ... and from the keyboard's, on its own level beside Manage Locomotive
            final java.lang.reflect.Field current = TrainControlUI.class.getDeclaredField("currentButton");

            current.setAccessible(true);

            final javax.swing.JButton button = (javax.swing.JButton) current.get(window);

            assertNotNull(button, "precondition: no key is the current one");

            SwingUtilities.invokeAndWait(() -> window.mapLocToCurrentButton(LOC));

            final Class<?> listenerClass = Class.forName("org.traincontrol.gui.RightClickMenuListener");

            final Object listener = listenerClass.getConstructor(TrainControlUI.class, javax.swing.JButton.class)
                .newInstance(window, button);

            final java.lang.reflect.Constructor<?> make = Class.forName(
                "org.traincontrol.gui.RightClickMenuListener$RightClickMenu").getDeclaredConstructor(listenerClass,
                TrainControlUI.class);

            make.setAccessible(true);

            final javax.swing.JPopupMenu[] keyboard = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    keyboard[0] = (javax.swing.JPopupMenu) make.newInstance(listener, window);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            final javax.swing.JMenuItem fromTheKeyboard = multiUnitItemIn(keyboard[0]);

            assertNotNull(fromTheKeyboard, "the keyboard's menu offers no multi-unit item beside Manage Locomotive");

            said = askAndClose(() -> fromTheKeyboard.doClick());

            assertEquals(said, refusal, "the multi-unit door, from the keyboard, did not refuse a locomotive a running route"
                + " drives, naming the route (OB-287)");

            // AND ASKED AGAIN AT OK: another locomotive's multi-unit, with the driven one ticked into it
            model.newMM2Locomotive(UNIT, 64);

            final org.traincontrol.base.Locomotive unit = model.getLocByName(UNIT);

            said = tickAndOk(() -> window.changeLinkedLocomotives(unit), LOC);

            assertEquals(said, refusal, "the multi-unit dialog's OK took in a locomotive a running route drives (OB-287)");

            assertFalse(unit.getLinkedLocomotiveNames().containsKey(LOC), "a locomotive a running route drives was made a"
                + " member of a multi-unit");
        }
        finally
        {
            closeEveryDialog();

            if (model != null)
            {
                long giveUp = System.currentTimeMillis() + 30000;

                while (model.getRoute(ROUTE) != null && model.getRoute(ROUTE).isExecuting()
                    && System.currentTimeMillis() < giveUp)
                {
                    Thread.sleep(100);
                }

                try { model.deleteRoute(ROUTE); } catch (Exception ignored) { }
                try { model.deleteLoc(UNIT); } catch (Exception ignored) { }
                try { model.deleteLoc(LOC); } catch (Exception ignored) { }
            }

            if (ui != null)
            {
                final TrainControlUI window = ui;

                SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The Central Station's name proposal asks it too, of both names, before it renames.
     *
     * @throws Exception from the file
     */
    @Test
    public void testTheNameProposalAsksItFirst() throws Exception
    {
        String source = new String(Files.readAllBytes(new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()),
            StandardCharsets.UTF_8);

        int current = source.indexOf("if (refuseWhileARouteDrivesIt(this, currentName)) continue;");
        int proposed = source.indexOf("if (refuseWhileARouteDrivesIt(this, newName)) continue;");

        assertTrue(current > 0 && proposed > 0, "the Central Station's name proposal does not ask about both names"
            + " (CS3-B1, SVB-C2)");

        int renames = source.indexOf("renameLoc(", proposed);

        assertTrue(renames > proposed, "the name proposal renames before it asks (OB-287)");
    }

    /**
     * Runs a door on the event thread, and closes the first message it shows without answering yes.
     *
     * @param door the door
     * @return the message, or null when none was shown
     * @throws Exception from the wait
     */
    private static String askAndClose(Runnable door) throws Exception
    {
        final java.util.concurrent.atomic.AtomicBoolean returned = new java.util.concurrent.atomic.AtomicBoolean(false);

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                door.run();
            }
            finally
            {
                returned.set(true);
            }
        });

        String said = null;

        long giveUp = System.currentTimeMillis() + 10000;

        while (said == null && !returned.get() && System.currentTimeMillis() < giveUp)
        {
            Thread.sleep(100);

            final String[] found = new String[1];

            SwingUtilities.invokeAndWait(() -> found[0] = closeTheFirstQuestion());

            said = found[0];
        }

        giveUp = System.currentTimeMillis() + 10000;

        // AND ANY QUESTION A DOOR THAT WENT ON WOULD ASK NEXT - closed, never answered yes.
        while (!returned.get() && System.currentTimeMillis() < giveUp)
        {
            Thread.sleep(100);

            SwingUtilities.invokeAndWait(() -> closeTheFirstQuestion());
        }

        assertTrue(returned.get(), "precondition: the door never returned");

        return said;
    }

    /**
     * The multi-unit item at a menu's own level - set, edit or view - or null.
     *
     * @param menu the menu
     * @return the item
     */
    private static javax.swing.JMenuItem multiUnitItemIn(javax.swing.JPopupMenu menu)
    {
        java.util.Set<String> texts = new java.util.HashSet<>(java.util.Arrays.asList(I18n.t("loc.ui.menuSetAsMultiUnit"),
            I18n.t("loc.ui.menuEditMultiUnitLocomotives"), I18n.t("loc.ui.menuViewMultiUnitLocomotives")));

        for (java.awt.Component part : menu.getComponents())
        {
            if (part instanceof javax.swing.JMenuItem && !(part instanceof javax.swing.JMenu)
                && texts.contains(((javax.swing.JMenuItem) part).getText()))
            {
                return (javax.swing.JMenuItem) part;
            }
        }

        return null;
    }

    /**
     * Opens the multi-unit dialog through a door, ticks one locomotive in it and presses OK; then the first message after
     * the OK, closed without answering yes.
     *
     * @param door the door
     * @param member the locomotive to tick
     * @return the message after OK, or null when none was shown
     * @throws Exception from the wait
     */
    private static String tickAndOk(Runnable door, String member) throws Exception
    {
        final java.util.concurrent.atomic.AtomicBoolean returned = new java.util.concurrent.atomic.AtomicBoolean(false);

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                door.run();
            }
            finally
            {
                returned.set(true);
            }
        });

        // THE DIALOG: its message is the panel of locomotives
        final boolean[] ticked = new boolean[1];

        long giveUp = System.currentTimeMillis() + 10000;

        while (!ticked[0] && !returned.get() && System.currentTimeMillis() < giveUp)
        {
            Thread.sleep(100);

            SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!(window instanceof JDialog) || !window.isShowing()) continue;

                    JOptionPane pane = paneIn((java.awt.Container) window);

                    if (pane == null || !(pane.getMessage() instanceof java.awt.Container)) continue;

                    javax.swing.JCheckBox box = boxFor((java.awt.Container) pane.getMessage(), member);

                    if (box == null) continue;

                    box.setSelected(true);

                    pane.setValue(pane.getOptions()[0]);

                    window.dispose();

                    ticked[0] = true;

                    return;
                }
            });
        }

        assertTrue(ticked[0], "precondition: the multi-unit dialog did not offer " + member);

        String said = null;

        giveUp = System.currentTimeMillis() + 10000;

        while (said == null && !returned.get() && System.currentTimeMillis() < giveUp)
        {
            Thread.sleep(100);

            final String[] found = new String[1];

            SwingUtilities.invokeAndWait(() -> found[0] = closeTheFirstQuestion());

            said = found[0];
        }

        giveUp = System.currentTimeMillis() + 10000;

        while (!returned.get() && System.currentTimeMillis() < giveUp)
        {
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(() -> closeTheFirstQuestion());
        }

        assertTrue(returned.get(), "precondition: the multi-unit door never returned");

        return said;
    }

    /** The tick box naming this locomotive in the multi-unit dialog's panel, or null. */
    private static javax.swing.JCheckBox boxFor(java.awt.Container container, String name)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JCheckBox && name.equals(((javax.swing.JCheckBox) child).getToolTipText()))
            {
                return (javax.swing.JCheckBox) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.JCheckBox found = boxFor((java.awt.Container) child, name);

                if (found != null) return found;
            }
        }

        return null;
    }

    /** Closes the first showing option pane, as its X does, and says what it said; null when none is showing. */
    private static String closeTheFirstQuestion()
    {
        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (!(window instanceof JDialog) || !window.isShowing()) continue;

            JOptionPane pane = paneIn((java.awt.Container) window);

            if (pane == null) continue;

            String message = String.valueOf(pane.getMessage());

            pane.setValue(JOptionPane.CLOSED_OPTION);

            window.dispose();

            return message;
        }

        return null;
    }

    private static JOptionPane paneIn(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof JOptionPane) return (JOptionPane) child;

            if (child instanceof java.awt.Container)
            {
                JOptionPane found = paneIn((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static void closeEveryDialog() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()) window.dispose();
            }
        });
    }
}
