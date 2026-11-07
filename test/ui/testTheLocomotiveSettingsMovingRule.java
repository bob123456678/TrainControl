package ui;

import java.awt.Component;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.LocomotiveSettings;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.RightClickSelectorMenu;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Mouse;
import support.SettingsWindow;

/**
 * A locomotive is not edited while the power is on and it, or a multi-unit it belongs to, is running (FR-048): the
 * window will not open from any door, Delete is refused, an open window grays Save, the trash can and the picture's
 * marks and says why, and Save asks again at the click.  Quick actions stay allowed.  Each claim acts through the real
 * door and asserts what was said and what the locomotive holds.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 */
public class testTheLocomotiveSettingsMovingRule
{
    private static final String NAME = "MR train";
    private static final String UNIT = "MR unit";
    private static final String THIRD = "MR third";
    private static final long WAIT = 8000;

    private static JButton key;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static MarklinLocomotive loc;
    private static MarklinLocomotive unit;
    private static MarklinLocomotive third;
    private static boolean stationWasUnset;
    private static support.SettingsFixture fixture;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        fixture = support.SettingsFixture.open();

        model = fixture.model;
        ui = fixture.ui;

        // The picture's Clear syncs with the station afterwards: pointed at nowhere
        stationWasUnset = support.TestStationAddress.get() == null;

        if (stationWasUnset) support.TestStationAddress.set(support.TestStationAddress.NOWHERE);

        Components.settle();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            SettingsWindow.closeIfOpen();

            if (fixture != null)
            {
                SettingsWindow.power(ui, model, false);
                fixture.forget(NAME, UNIT, THIRD);
            }

            if (fixture != null) fixture.shutDown();
        }
        finally
        {
            if (stationWasUnset) support.TestStationAddress.set(null);

            if (fixture != null) fixture.closeSandbox();
        }
    }

    @BeforeMethod
    public void lay() throws Exception
    {
        fixture.quiet();

        SettingsWindow.power(ui, model, false);

        fixture.forget(NAME, UNIT, THIRD);

        loc = model.newMFXLocomotive(NAME, 3501);
        unit = model.newMFXLocomotive(UNIT, 3502);
        third = model.newMFXLocomotive(THIRD, 3503);

        key = SettingsWindow.showOnKeyA(ui, loc);
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        fixture.quiet();

        loc.setSpeed(0);
        unit.setSpeed(0);

        SettingsWindow.power(ui, model, false);
    }

    private static String stop(String name)
    {
        return I18n.f("loc.ui.stopToEdit", name);
    }

    /** Power on and the train running, as the station would report it. */
    private static void run(MarklinLocomotive who) throws Exception
    {
        SettingsWindow.power(ui, model, true);

        who.setSpeed(40);

        Components.onTheEventThread(() ->
        {
            ui.repaintLoc(true, null);

            return null;
        });

        Components.settle();
    }

    /**
     * Every door that opens the window says this and opens nothing: the key menu grays its two entries with it as
     * their tooltip, and the name, a function button and the database tile say it when pressed.
     */
    private static void everyDoorSays(String reason) throws Exception
    {
        JPopupMenu menu = SettingsWindow.rightClickKey(key);

        for (String text : new String[] { I18n.t("loc.ui.menuLocomotiveSettings"), I18n.t("loc.ui.menuSetAsMultiUnit") })
        {
            AbstractButton item = Components.withText(menu, text);

            assertNotNull(item, "the key's menu has no \"" + text + "\"");
            assertFalse(Components.onTheEventThread(item::isEnabled), "\"" + text + "\" is lit");
            assertEquals(Components.onTheEventThread(item::getToolTipText), reason, "\"" + text + "\" does not say why it is gray");
        }

        Mouse.closeMenus();

        Component name = (Component) field("ActiveLocLabel");

        assertEquals(SettingsWindow.asked(() -> Mouse.rightClick(name, 500)), Arrays.asList(reason), "the name said the wrong thing");

        javax.swing.JToggleButton f3 = SettingsWindow.functionButton(ui, 3);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> f3.isEnabled() && f3.isShowing()), WAIT));

        assertEquals(SettingsWindow.asked(() -> Mouse.rightClick(f3, 500)), Arrays.asList(reason), "a function button said the wrong thing");

        final JPopupMenu[] tile = new JPopupMenu[1];

        SwingUtilities.invokeAndWait(() -> tile[0] = new RightClickSelectorMenu(ui, null, loc));

        AbstractButton settings = Components.withText(tile[0], I18n.t("loc.ui.menuLocomotiveSettings"));

        assertTrue(Components.onTheEventThread(settings::isEnabled), "the tile's entry is gray: only the key menu grays");

        assertEquals(SettingsWindow.asked(() -> Components.onTheEventThread(() ->
        {
            settings.doClick(0);

            return null;
        })), Arrays.asList(reason), "the tile said the wrong thing");

        assertNull(SettingsWindow.showing(), "the window opened");
    }

    // The doors

    /**
     * Every door says to stop the train, and none opens the window: the key menu grays both its entries and gives the
     * reason as their tooltip; the name, the database tile and a function button say it when pressed.
     *
     * MUTATION: leave the moving rule out of `openLocomotiveSettings`, or out of the key menu's `grayWhileMoving`, and
     * this fails.
     */
    @Test
    public void testAMovingTrainIsNotOpenedFromAnyDoor() throws Exception
    {
        run(loc);

        everyDoorSays(stop(NAME));
    }

    /**
     * Delete follows the rule from every door: the tile's entry and the Control+Delete path say to stop the train and
     * delete nothing.
     *
     * MUTATION: take the moving test out of `LocomotiveActions.deleteLoc`, and this fails.
     */
    @Test
    public void testAMovingTrainIsNotDeleted() throws Exception
    {
        run(loc);

        final JPopupMenu[] tile = new JPopupMenu[1];

        SwingUtilities.invokeAndWait(() -> tile[0] = new RightClickSelectorMenu(ui, null, loc));

        AbstractButton delete = Components.withText(tile[0], I18n.t("loc.ui.menuDeleteFromDatabase"));

        assertEquals(SettingsWindow.asked(() -> Components.onTheEventThread(() ->
        {
            delete.doClick(0);

            return null;
        })), Arrays.asList(stop(NAME)), "the tile's Delete did not refuse a moving train");

        assertEquals(SettingsWindow.asked(() -> Components.onTheEventThread(() ->
        {
            ui.deleteLoc(NAME);

            return null;
        })), Arrays.asList(stop(NAME)), "Control+Delete's path did not refuse a moving train");

        assertNotNull(model.getLocByName(NAME), "a moving train was deleted");
    }

    /**
     * The rule needs both halves: a train moving with the power off, or the power on and the train at rest, opens.
     *
     * MUTATION: test only the speed, or only the power, in `LocomotiveSettings.isMoving`, and this fails.
     */
    @Test
    public void testPowerOffOrAtRestOpens() throws Exception
    {
        SettingsWindow.power(ui, model, false);
        loc.setSpeed(40);

        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "the power is off and it did not open");

        SettingsWindow.closeIfOpen();

        loc.setSpeed(0);
        SettingsWindow.power(ui, model, true);

        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "the train is at rest and it did not open");
    }

    /**
     * A locomotive in a multi-unit that is moving is moving: the member is refused while its unit runs, and a train
     * outside the unit is not.
     *
     * MUTATION: look at the locomotive's own speed only in `isMoving`, and this fails.
     */
    @Test
    public void testAMemberOfAMovingMultiUnitIsRefused() throws Exception
    {
        Map<String, Double> members = new HashMap<>();

        members.put(NAME, 1.0);
        unit.setLinkedLocomotives(members);

        run(unit);

        // A unit sets its members going too: the member itself is put back at rest, so only its unit is moving
        loc.setSpeed(0);

        assertTrue(unit.getSpeed() > 0 && loc.getSpeed() == 0, "precondition: the unit is not the only one moving");

        assertEquals(SettingsWindow.asked(() -> SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null)),
            Arrays.asList(stop(NAME)), "the member of a moving multi-unit was not refused");

        assertTrue(SettingsWindow.open(ui, third, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "a train outside the unit was refused");
    }

    // An open window

    /**
     * A train that starts while the window is open: a strip says so, Save, the trash can and the picture's marks gray,
     * what was typed stays, and everything comes back when the train stops.
     *
     * MUTATION: leave `refresh` out of the repaint hook in `TrainControlUI.repaintLoc`, and this fails.
     */
    @Test
    public void testATrainThatStartsGraysTheWindowAndKeepsTheTyping() throws Exception
    {
        loc.setLocalImageURL("file:/mr-nothing.png");

        SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null);
        SettingsWindow.awaited();

        SettingsWindow.type("settings.name", "MR typed");

        assertFalse(Components.onTheEventThread(SettingsWindow.part("settings.strip", JLabel.class)::isVisible),
            "the strip is up for a train at rest");
        assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled));

        run(loc);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(SettingsWindow.part("settings.strip", JLabel.class)::isVisible), WAIT),
            "no strip came up when the train started");
        assertEquals(Components.onTheEventThread(SettingsWindow.part("settings.strip", JLabel.class)::getText),
            I18n.f("loc.ui.settingsMovingStrip", NAME));

        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save is lit for a moving train");

        for (String name : new String[] { "settings.trash", "settings.wrench", "settings.revert" })
        {
            assertFalse(Components.onTheEventThread(SettingsWindow.part(name, Component.class)::isEnabled),
                name + " is lit for a moving train");
        }

        assertEquals(Components.onTheEventThread(() -> SettingsWindow.part("settings.name", JTextField.class).getText()),
            "MR typed", "the typing was lost when the train started");

        loc.setSpeed(0);

        Components.onTheEventThread(() ->
        {
            ui.repaintLoc(true, null);

            return null;
        });

        assertTrue(Components.waitFor(() -> !Components.onTheEventThread(SettingsWindow.part("settings.strip", JLabel.class)::isVisible), WAIT),
            "the strip stayed up after the train stopped");
        assertTrue(Components.waitFor(() -> Components.onTheEventThread(SettingsWindow.save()::isEnabled), WAIT),
            "Save stayed gray after the train stopped");
    }

    /**
     * Save asks again at the click, so the graying is not the guard: with the window not yet told, Save still refuses
     * and writes nothing.
     *
     * MUTATION: drop the moving test from `LocomotiveSettingsFrame.write`, and this fails.
     */
    @Test
    public void testSaveAsksAgainAtTheClick() throws Exception
    {
        SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null);
        SettingsWindow.awaited();

        SettingsWindow.type("settings.name", "MR renamed");

        // The train starts and the window is never told
        SettingsWindow.power(ui, model, true);
        loc.setSpeed(40);

        assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "precondition: Save was grayed by something else");

        java.util.List<String> said;

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(SettingsWindow.save());

            assertTrue(answering.awaitAsked(1, WAIT), "Save said nothing for a moving train");

            said = answering.asked();
        }

        assertEquals(said, Arrays.asList(stop(NAME)));
        assertEquals(loc.getName(), NAME, "Save renamed a moving train");
        assertNotNull(SettingsWindow.showing(), "the refused Save closed the window");
    }

    /**
     * The picture's marks are refused for a moving train and work for one at rest.
     *
     * MUTATION: leave the moving test out of the marks' `moveAllowed`, and this fails.
     */
    @Test
    public void testThePicturesMarksFollowTheRule() throws Exception
    {
        loc.setLocalImageURL("file:/mr-nothing.png");

        SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null);
        SettingsWindow.awaited();

        // Not told: the click itself is where the rule is asked
        SettingsWindow.power(ui, model, true);
        loc.setSpeed(40);

        Component revert = SettingsWindow.part("settings.revert", Component.class);

        assertTrue(Components.onTheEventThread(revert::isVisible), "precondition: the mark that clears your own picture is not shown");

        assertEquals(SettingsWindow.asked(() -> Mouse.click(revert)), Arrays.asList(stop(NAME)), "the mark did not refuse a moving train");
        assertEquals(loc.getLocalImageURL(), "file:/mr-nothing.png", "a moving train's picture was cleared");

        loc.setSpeed(0);
        SettingsWindow.power(ui, model, false);

        Mouse.click(revert);

        assertTrue(Components.waitFor(() -> loc.getLocalImageURL() == null, WAIT), "the mark did not clear the picture of a train at rest");
    }

    /**
     * The strip takes its room from the tabs: the window keeps its size when a train starts, and the buttons at the
     * bottom stay inside it.
     *
     * MUTATION: call `pack()` when the strip shows, in `LocomotiveSettingsFrame.refresh`, and this fails.
     */
    @Test
    public void testTheStripDoesNotResizeTheWindow() throws Exception
    {
        SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null);
        SettingsWindow.awaited();

        LocomotiveSettingsFrame window = SettingsWindow.showing();

        java.awt.Dimension before = Components.onTheEventThread(window::getSize);

        run(loc);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(SettingsWindow.part("settings.strip", JLabel.class)::isVisible), WAIT));

        assertEquals(Components.onTheEventThread(window::getSize), before, "the window was resized when the strip came up");

        assertTrue(Components.onTheEventThread(() ->
        {
            window.getContentPane().doLayout();

            java.awt.Rectangle save = javax.swing.SwingUtilities.convertRectangle(SettingsWindow.save().getParent(),
                SettingsWindow.save().getBounds(), window.getContentPane());

            return window.getContentPane().getBounds().contains(save);
        }), "the Save button is clipped with the strip up");
    }

    // Autonomy, and a route

    private static final String AUTONOMY = "autolayout.ui.errorCannotEditLocomotivesWhileRunning";

    /** Autonomy running, as the model counts it: staging stands in for a run. */
    private static void autonomy(boolean running)
    {
        model.getAutoLayout().setStagingInProgress(running);
    }

    /**
     * While autonomy runs no door opens the window, for any locomotive: the key menu grays its entries and gives the
     * reason, the name, a function button and the database tile say so, and Delete refuses.
     *
     * MUTATION: leave autonomy out of `LocomotiveSettings.why`, and this fails.
     */
    @Test
    public void testAutonomyKeepsTheWindowFromEveryDoor() throws Exception
    {
        autonomy(true);

        try
        {
            assertTrue(model.isAutonomyRunning(), "precondition: the model does not read autonomy as running");

            everyDoorSays(I18n.t(AUTONOMY));

            assertEquals(SettingsWindow.asked(() -> Components.onTheEventThread(() ->
            {
                ui.deleteLoc(NAME);

                return null;
            })), Arrays.asList(I18n.t(AUTONOMY)), "Delete did not refuse while autonomy runs");

            assertNotNull(model.getLocByName(NAME));
        }
        finally
        {
            autonomy(false);
        }
    }

    /**
     * A window open when autonomy starts says why and grays Save, the trash can and the marks, keeps what was typed,
     * and Save asks again at the click; when the run ends the window is usable again.
     *
     * MUTATION: stop asking the rule every second in `LocomotiveSettingsFrame` (drop `watch.start()`), and this fails.
     */
    @Test
    public void testAWindowOpenWhenAutonomyStartsGraysAndComesBack() throws Exception
    {
        SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null);
        SettingsWindow.awaited();

        SettingsWindow.type("settings.name", "MR typed");

        JLabel strip = SettingsWindow.part("settings.strip", JLabel.class);

        autonomy(true);

        try
        {
            assertTrue(Components.waitFor(() -> Components.onTheEventThread(strip::isVisible), WAIT), "no strip came up when autonomy started");
            assertEquals(Components.onTheEventThread(strip::getText), I18n.t("loc.ui.settingsAutonomyStrip"));
            assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save is lit while autonomy runs");
            assertFalse(Components.onTheEventThread(SettingsWindow.part("settings.trash", Component.class)::isEnabled));
            assertEquals(SettingsWindow.text("settings.name"), "MR typed", "the typing was lost");

            // Save asks again at the click, the window not being told
            java.lang.reflect.Method save = LocomotiveSettingsFrame.class.getDeclaredMethod("save");

            save.setAccessible(true);

            LocomotiveSettingsFrame window = SettingsWindow.showing();

            assertEquals(SettingsWindow.asked(() -> Components.onTheEventThread(() ->
            {
                try
                {
                    return save.invoke(window);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            })), Arrays.asList(I18n.t(AUTONOMY)));

            assertEquals(loc.getName(), NAME, "Save renamed the locomotive while autonomy runs");
        }
        finally
        {
            autonomy(false);
        }

        assertTrue(Components.waitFor(() -> !Components.onTheEventThread(strip::isVisible), WAIT), "the strip stayed up after the run");
        assertTrue(Components.waitFor(() -> Components.onTheEventThread(SettingsWindow.save()::isEnabled), WAIT), "Save stayed gray after the run");
        assertEquals(SettingsWindow.text("settings.name"), "MR typed");
    }

    /**
     * A stopped locomotive a running route drives opens the window, and so does another; the route's refusal comes at
     * Save for its name, address and multi-unit, and not for the preset speed.
     *
     * MUTATION: put a running route into `LocomotiveSettings.why`, or leave the route question out of
     * `LocomotiveActions.writeNameAndAddress`, and this fails.
     */
    @Test
    public void testARouteDoesNotKeepTheWindowButRefusesItsIdentityAtSave() throws Exception
    {
        java.util.List<org.traincontrol.base.RouteCommand> commands = new java.util.ArrayList<>();

        org.traincontrol.base.RouteCommand fires = org.traincontrol.base.RouteCommand.RouteCommandFunction(NAME, 1, true);

        fires.setDelay(20000);

        commands.add(fires);

        model.newRoute("MR route", commands, 0, org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        new Thread(() -> model.execRoute("MR route")).start();

        try
        {
            assertTrue(Components.waitFor(() -> model.getRoute("MR route").isExecuting(), WAIT), "precondition: the route never started");

            assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "a stopped locomotive a route drives did not open");
            SettingsWindow.awaited();

            assertFalse(Components.onTheEventThread(SettingsWindow.part("settings.strip", JLabel.class)::isVisible), "the strip is up for a route");

            SettingsWindow.type("settings.name", "MR renamed");

            assertEquals(SettingsWindow.asked(() -> Components.press(SettingsWindow.save())),
                Arrays.asList(I18n.f("loc.ui.errorLocomotiveDrivenByARunningRoute", NAME, "MR route")));
            assertEquals(loc.getName(), NAME);

            SettingsWindow.type("settings.name", NAME);

            Components.onTheEventThread(() ->
            {
                SettingsWindow.part("settings.speed", javax.swing.JSpinner.class).setValue(66);

                return null;
            });

            SettingsWindow.press(SettingsWindow.save());

            assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 66, WAIT), "the preset speed of a driven locomotive was refused");

            assertTrue(SettingsWindow.open(ui, third, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "another locomotive did not open");
        }
        finally
        {
            SettingsWindow.closeIfOpen();

            long giveUp = System.currentTimeMillis() + 30000;

            while (model.getRoute("MR route") != null && model.getRoute("MR route").isExecuting() && System.currentTimeMillis() < giveUp)
            {
                Thread.sleep(100);
            }

            model.deleteRoute("MR route");
        }
    }

    /**
     * The presets still save while autonomy runs, from the key menu and the hotkeys' own door: Save Current Speed and
     * Save Current Functions as Preset.
     *
     * MUTATION: ask the rule in `RightClickMenuListener` for the two Save items (gray them while autonomy runs), and
     * this fails.
     */
    @Test
    public void testThePresetsStillSaveDuringARun() throws Exception
    {
        autonomy(true);

        try
        {
            loc.setSpeed(35);
            loc.setF(2, true);

            assertTrue(Components.waitFor(() -> loc.getF(2), WAIT));

            JPopupMenu menu = SettingsWindow.rightClickKey(key);

            Mouse.choose(lit(menu, I18n.t("loc.ui.menuSaveSpeedAsPreset")));
            lit(menu, I18n.t("loc.ui.menuSaveFunctionsAsPreset"));

            assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 35, WAIT), "the speed preset was not saved during a run");

            menu = SettingsWindow.rightClickKey(key);

            Mouse.choose((JMenuItem) Components.withText(menu, I18n.t("loc.ui.menuSaveFunctionsAsPreset")));

            assertTrue(Components.waitFor(() -> loc.getPreferredFunctions()[2], WAIT), "the function preset was not saved during a run");

            // The hotkeys' door
            loc.setSpeed(44);

            ui.savePreferredSpeed(loc);

            assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 44, WAIT), "Alt+U did not save during a run");
        }
        finally
        {
            autonomy(false);
        }
    }

    /**
     * The rule's own comment says what it asks: its reasons are the enum's, and a route is not one.
     *
     * MUTATION: add a ROUTE reason to `LocomotiveSettings.Why`, and this fails.
     */
    @Test
    public void testTheRulesCommentSaysWhatItAsks() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/LocomotiveSettings.java")), java.nio.charset.StandardCharsets.UTF_8);

        String comment = source.substring(0, source.indexOf("public final class LocomotiveSettings"));

        java.util.Set<String> reasons = new java.util.TreeSet<>();

        for (Object o : LocomotiveSettings.Why.class.getEnumConstants()) reasons.add(o.toString());

        assertEquals(reasons, new java.util.TreeSet<>(Arrays.asList("MOVING", "AUTONOMY")), "the rule's reasons changed");
        assertTrue(comment.contains("moving") && comment.contains("autonomy runs"), "the comment does not name the reasons");
        assertFalse(comment.contains("route"), "the comment names a route, which is not a reason");
    }

    // Quick actions

    /**
     * The key menu's quick actions stay allowed for a moving train, the presets included: the speed preset applies
     * and saves.
     *
     * MUTATION: gray the speed presets under the moving rule in `RightClickMenuListener`, and this fails.
     */
    @Test
    public void testQuickActionsStayAllowedWhileMoving() throws Exception
    {
        loc.setPreferredSpeed(20);

        run(loc);

        JPopupMenu menu = SettingsWindow.rightClickKey(key);

        lit(menu, I18n.t("loc.ui.menuTurnOffFunctions"));

        Mouse.choose(lit(menu, I18n.f("loc.ui.menuApplySavedSpeedPreset", 20)));

        assertTrue(Components.waitFor(() -> loc.getSpeed() == 20, WAIT), "the speed preset did not drive the moving train");

        menu = SettingsWindow.rightClickKey(key);

        loc.setSpeed(35);

        Mouse.choose((JMenuItem) Components.withText(menu, I18n.t("loc.ui.menuSaveSpeedAsPreset")));

        assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 35, WAIT), "Save Current Speed did not keep the moving speed");
    }

    // Helpers

    /** The menu's item of this text, which is lit. */
    private static JMenuItem lit(JPopupMenu menu, String text)
    {
        JMenuItem item = (JMenuItem) Components.withText(menu, text);

        assertNotNull(item, "the key's menu has no \"" + text + "\"");
        assertTrue(Components.onTheEventThread(item::isEnabled), "\"" + text + "\" is gray");

        return item;
    }

    private static Object field(String name) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }


}
