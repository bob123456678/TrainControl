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
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.SourceOf;

/**
 * A locomotive a running route drives is neither deleted nor renamed, and the refusal names the route (OB-287; Adam:
 * "make an automated test for this").  `core.testAdvancedRoutes` holds the half about the route finishing its
 * commands and the `runningRouteDriving` question; nothing asked whether the doors ask it.
 *
 * The doors a person uses are driven here on a real window, closing the dialogs the refusal and a wrongly open door
 * show.  The third door, the Central Station's name proposal, sits inside a loop over a modal list, so it is read.
 *
 * MUTATION: take the refusal out of either door, and the matching claim fails; leave the member list out of
 * `LocomotiveSettingsFrame.MultiUnitTab` while autonomy runs, and the Central Station list claim fails.
 *
 * @author Adam
 */
public class testARouteDrivenLocomotiveIsNotEdited
{
    private static final String LOC = "OB-287 loc";
    private static final String ROUTE = "OB-287 route";

    /** A second locomotive, for a multi-unit the route's locomotive is ticked into. */
    private static final String UNIT = "OB-287 unit";

    /** A Central Station multi-unit with the route's locomotive among its members. */
    private static final String CS_UNIT = "OB-287 CS unit";

    /**
     * The delete door and the Locomotive Settings window's Save each refuse, naming the route, and leave the
     * locomotive as it was; and the multi-unit tab (from the locomotive database's menu and the keyboard's, and asked
     * again at Save of a locomotive ticked into another's multi-unit).
     */
    @Test
    public void testBothDoorsRefuseWhileTheRouteRuns() throws Exception
    {
        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            // OPENED INSIDE THE TRY (OB-111): a throw between the open and the close would leave the layout preference
            // pointing at a folder under %TEMP%.
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulated();

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

            // THE EDIT DOOR, the Locomotive Settings window's Save, which renames and re-addresses.
            final org.traincontrol.base.Locomotive loc = model.getLocByName(LOC);

            assertTrue(support.SettingsWindow.open(window, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null),
                "precondition: the window did not open on a locomotive a running route drives");

            support.SettingsWindow.type("settings.name", LOC + " renamed");

            said = pressSave();

            assertEquals(said, refusal, "Save did not refuse a rename of a locomotive a running route drives, naming the"
                + " route (OB-287, CS3-B1)");

            assertNotNull(model.getLocByName(LOC), "Save renamed a locomotive a running route drives");

            support.SettingsWindow.closeIfOpen();

            // THE MULTI-UNIT DOORS now open the Locomotive Settings window on its Multi-Unit tab, and the refusal is asked
            // once, at Save (FR-048): from the locomotive database's menu ...
            model.newMM2Locomotive(UNIT, 64);

            final javax.swing.JPopupMenu[] database = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() -> database[0] = new org.traincontrol.gui.locomotive.RightClickSelectorMenu(window, null, loc));

            assertNull(multiUnitItemIn(database[0]), "the locomotive database's menu still offers a multi-unit item of its own");

            final javax.swing.JMenuItem settings = (javax.swing.JMenuItem) support.Components.withText(database[0],
                I18n.t("loc.ui.menuLocomotiveSettings"));

            assertNotNull(settings, "the locomotive database's menu offers no Locomotive Settings...");

            SwingUtilities.invokeAndWait(() -> window.openLocomotiveSettings(loc, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null));

            said = savingAMemberAsksOnce(UNIT);

            assertEquals(said, refusal, "Save, from the locomotive database's window, did not refuse a locomotive a running"
                + " route drives, naming the route (OB-287)");

            // ... and from the keyboard's menu, whose item opens the same window on the same tab
            final java.lang.reflect.Field current = TrainControlUI.class.getDeclaredField("currentButton");

            current.setAccessible(true);

            final javax.swing.JButton button = (javax.swing.JButton) current.get(window);

            assertNotNull(button, "precondition: no key is the current one");

            SwingUtilities.invokeAndWait(() -> window.mapLocToCurrentButton(LOC));

            final Class<?> listenerClass = Class.forName("org.traincontrol.gui.locomotive.RightClickMenuListener");

            final Object listener = listenerClass.getConstructor(TrainControlUI.class, javax.swing.JButton.class)
                .newInstance(window, button);

            final java.lang.reflect.Constructor<?> make = Class.forName(
                "org.traincontrol.gui.locomotive.RightClickMenuListener$RightClickMenu").getDeclaredConstructor(listenerClass,
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

            assertNotNull(fromTheKeyboard, "the keyboard's menu offers no multi-unit item");

            support.SettingsWindow.closeIfOpen();

            SwingUtilities.invokeAndWait(fromTheKeyboard::doClick);

            assertNotNull(support.SettingsWindow.awaited(), "the keyboard's multi-unit item opened no window");
            assertEquals(support.SettingsWindow.tabShowing(), 3, "the keyboard's multi-unit item did not open the Multi-Unit tab");

            said = savingAMemberAsksOnce(UNIT);

            assertEquals(said, refusal, "Save, from the keyboard's window, did not refuse a locomotive a running route"
                + " drives, naming the route (OB-287)");

            support.SettingsWindow.closeIfOpen();

            // AND ASKED AGAIN AT OK: another locomotive's multi-unit, with the driven one ticked into it
            final org.traincontrol.base.Locomotive unit = model.getLocByName(UNIT);

            assertTrue(support.SettingsWindow.open(window, unit, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null),
                "precondition: the window did not open on the multi-unit's own tab");

            said = savingAMemberAsksOnce(LOC);

            support.SettingsWindow.closeIfOpen();

            assertEquals(said, refusal, "Save took in a locomotive a running route drives (OB-287)");

            assertFalse(unit.getLinkedLocomotiveNames().containsKey(LOC), "a locomotive a running route drives was made a"
                + " member of a multi-unit");

            // BUT A CENTRAL STATION MULTI-UNIT'S LIST, which changes nothing, is shown while the route drives a member:
            // only the window's Save asks
            model.newMM2Locomotive(CS_UNIT, 65);

            model.changeLocAddress(CS_UNIT, 65, org.traincontrol.marklin.MarklinLocomotive.decoderType.MULTI_UNIT);

            final org.traincontrol.base.Locomotive centralStations = model.getLocByName(CS_UNIT);

            java.util.Map<String, Double> members = new java.util.HashMap<>();

            members.put(LOC, 1.0);

            centralStations.setModelMultiUnitLocomotives(members);

            said = openingSaysNothing(window, centralStations);

            assertNull(said, "a Central Station multi-unit's list, which changes nothing, was refused while a route drives"
                + " a member (RSA30-C8): " + said);

            // AND WHILE AUTONOMY IS BUSY, when the window opens locked: the list is still shown, ticked and gray, with
            // the strip saying why.  Staging stands in for a run here, as the model counts it among them.
            model.getAutoLayout().setStagingInProgress(true);

            try
            {
                assertTrue(model.isAutonomyRunning(), "precondition: the model does not read autonomy as busy");

                assertTrue(support.SettingsWindow.open(window, centralStations, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null),
                    "the window did not open on a Central Station multi-unit while autonomy was busy");

                support.Components.settle();

                javax.swing.JLabel strip = support.SettingsWindow.part("settings.strip", javax.swing.JLabel.class);

                assertTrue(support.Components.onTheEventThread(strip::isVisible), "no strip while autonomy was busy");

                java.util.List<javax.swing.JCheckBox> listed = support.Components.all(
                    support.SettingsWindow.showing().getContentPane(), javax.swing.JCheckBox.class,
                    b -> LOC.equals(b.getToolTipText()));

                assertEquals(listed.size(), 1, "a Central Station multi-unit's list was not shown while autonomy was busy");
                assertTrue(support.Components.onTheEventThread(listed.get(0)::isSelected), "the member is not ticked");
                assertFalse(support.Components.onTheEventThread(support.SettingsWindow.save()::isEnabled),
                    "Save is lit on a Central Station multi-unit's read-only list");

                support.SettingsWindow.closeIfOpen();
            }
            finally
            {
                model.getAutoLayout().setStagingInProgress(false);
            }
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
                try { model.deleteLoc(CS_UNIT); } catch (Exception ignored) { }
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

    /** The Central Station's name proposal asks it too, of both names, before it renames. */
    @Test
    public void testTheNameProposalAsksItFirst() throws Exception
    {
        String source = SourceOf.declaration("private void checkForRenameMenuItemActionPerformed(");

        int current = source.indexOf("if (refuseWhileARouteDrivesIt(this, currentName)) continue;");
        int proposed = source.indexOf("if (refuseWhileARouteDrivesIt(this, newName)) continue;");

        assertTrue(current > 0 && proposed > 0, "the Central Station's name proposal does not ask about both names"
            + " (CS3-B1, SVB-C2)");

        int renames = source.indexOf("renameLoc(", proposed);

        assertTrue(renames > proposed, "the name proposal renames before it asks (OB-287)");
    }

    /** The locomotive and route of this claim. */
    private static final String SYNCED = "GSR-B2 loc";
    private static final String SYNCED_ROUTE = "GSR-B2 route";

    /**
     * A Central Station sync does not re-address a locomotive a running route drives (OB-287): a route sends its
     * commands by NAME with delays between them, so a sync landing part-way along would send the rest to a different
     * decoder.  The delete and edit doors refuse the same change; the sync is the door nobody opens by hand.
     *
     * The sync's address step is asked directly, with the Central Station's description of the locomotive at another
     * address, because the whole sync needs a station on the network.  Autonomy is idle throughout.
     *
     * MUTATION: drop `|| this.runningRouteDriving(l.getName()) != null` from `adoptCentralStationAddress`, and this
     * fails.
     */
    @Test
    public void testASyncDoesNotReAddressALocomotiveARouteDrives() throws Exception
    {
        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;

        try
        {
            // OPENED INSIDE THE TRY (OB-111).
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulated();

            model.newMM2Locomotive(SYNCED, 66);

            // A ROUTE THAT DRIVES IT, running long enough to ask: one function command, with a pause after it.
            List<RouteCommand> commands = new ArrayList<>();

            RouteCommand fires = RouteCommand.RouteCommandFunction(SYNCED, 1, true);

            fires.setDelay(10000);

            commands.add(fires);

            model.newRoute(SYNCED_ROUTE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            final MarklinControlStation running = model;

            new Thread(() -> running.execRoute(SYNCED_ROUTE)).start();

            long armed = System.currentTimeMillis() + 5000;

            while (!model.getRoute(SYNCED_ROUTE).isExecuting() && System.currentTimeMillis() < armed) Thread.sleep(20);

            assertNotNull(model.runningRouteDriving(SYNCED), "precondition: the model does not say the running route"
                + " drives the locomotive, so there is nothing for the sync to wait for");

            assertFalse(model.isAutonomyRunning(), "precondition: autonomy is busy, so the sync would wait for it and"
                + " this would say nothing about the route");

            // THE CENTRAL STATION'S DESCRIPTION: the same locomotive and decoder, at another address
            org.traincontrol.marklin.MarklinLocomotive fromTheStation = new org.traincontrol.marklin.MarklinLocomotive(
                model, 67, org.traincontrol.marklin.MarklinLocomotive.decoderType.MM2, SYNCED);

            model.adoptCentralStationAddress(fromTheStation,
                new ArrayList<org.traincontrol.marklin.MarklinLocomotive>());

            assertEquals(model.getLocByName(SYNCED).getAddress(), 66, "a Central Station sync moved a locomotive a"
                + " running route drives to another address - the route's remaining commands go to whatever decoder"
                + " answers there, another train or none (GSR-B2)");
        }
        finally
        {
            if (model != null)
            {
                long giveUp = System.currentTimeMillis() + 30000;

                while (model.getRoute(SYNCED_ROUTE) != null && model.getRoute(SYNCED_ROUTE).isExecuting()
                    && System.currentTimeMillis() < giveUp)
                {
                    Thread.sleep(100);
                }

                try { model.deleteRoute(SYNCED_ROUTE); } catch (Exception ignored) { }
                try { model.deleteLoc(SYNCED); } catch (Exception ignored) { }

                model.stop();
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Runs a door on the event thread, and closes the first message it shows without answering yes; null when none was
     * shown.
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

        return closeUntilTheDoorReturns(returned, "the door");
    }

    /**
     * The Multi-Unit tab of the open window: ticks a member and presses Save; the first thing said, answered with its
     * first button.
     */
    private static String savingAMemberAsksOnce(String member) throws Exception
    {
        support.SettingsWindow.tickMember(member);

        return pressSave();
    }

    /**
     * Opens the window on a multi-unit's own tab and gives the first thing said, or null when nothing was.  The window
     * is closed again.
     */
    private static String openingSaysNothing(TrainControlUI window, final org.traincontrol.base.Locomotive unit) throws Exception
    {
        try (support.Answering answering = support.Answering.start(message -> support.Answering.pressAt(0)))
        {
            assertTrue(support.SettingsWindow.open(window, unit, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null),
                "precondition: the window did not open on a Central Station multi-unit");

            support.Components.settle();

            assertEquals(support.SettingsWindow.tabShowing(), 3, "the window did not show the Multi-Unit tab");

            assertFalse(support.Components.onTheEventThread(support.SettingsWindow.save()::isEnabled),
                "Save is lit on a Central Station multi-unit's read-only list");

            String said = answering.asked().isEmpty() ? null : answering.asked().get(0);

            support.SettingsWindow.closeIfOpen();

            return said;
        }
    }

    /** Presses the open window's Save; the first thing said, answered with its first button. */
    private static String pressSave() throws Exception
    {
        try (support.Answering answering = support.Answering.start(message -> support.Answering.pressAt(0)))
        {
            support.Components.press(support.SettingsWindow.save());

            assertTrue(answering.awaitAsked(1, 8000), "Save said nothing");

            support.Components.settle();

            return answering.asked().get(0);
        }
    }

    /** The multi-unit item at a menu's own level (set, edit or view), or null. */
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

    /** The two locomotives of OB-301's claim. */
    private static final String EDITED = "OB-301 edited";
    private static final String TAKEN = "OB-301 taken";

    /**
     * A name the edit door refuses changes nothing, not the address either (OB-301).
     *
     * MUTATION: apply the address before the name is asked, and this fails.
     */
    @Test
    public void testARefusedNameChangesNothing() throws Exception
    {
        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulated();

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

            model.newMM2Locomotive(EDITED, 64);
            model.newMM2Locomotive(TAKEN, 65);

            final org.traincontrol.base.Locomotive edited = model.getLocByName(EDITED);
            final TrainControlUI window = ui;

            // ANOTHER TRAIN'S ADDRESS, AND A NAME THAT IS TAKEN
            assertTrue(support.SettingsWindow.open(window, edited, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null),
                "precondition: the window did not open");

            support.SettingsWindow.type("settings.address", "65");
            support.SettingsWindow.type("settings.name", TAKEN);

            String said = pressSave();

            assertEquals(said, I18n.f("loc.ui.errorLocomotiveAlreadyExists", TAKEN), "precondition: the door did not refuse"
                + " a name another locomotive has");

            assertEquals(model.getLocByName(EDITED).getAddress(), 64, "Save refused the name and kept the new"
                + " address - after the check, so the sweep that takes a clash off the railway never ran (OB-301)");
        }
        finally
        {
            closeEveryDialog();

            if (model != null)
            {
                try { model.deleteLoc(EDITED); } catch (Exception ignored) { }
                try { model.deleteLoc(TAKEN); } catch (Exception ignored) { }
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

    /** Closes each message a running door shows, never answering yes; the first one's text, or null if none showed. */
    private static String closeUntilTheDoorReturns(java.util.concurrent.atomic.AtomicBoolean returned, String door)
        throws Exception
    {
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

        assertTrue(returned.get(), "precondition: " + door + " never returned");

        return said;
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
        support.SettingsWindow.closeIfOpen();

        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()) window.dispose();
            }
        });
    }
}
