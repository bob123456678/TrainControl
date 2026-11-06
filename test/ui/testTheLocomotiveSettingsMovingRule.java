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

        JPopupMenu menu = SettingsWindow.rightClickKey(key);

        for (String text : new String[] { I18n.t("loc.ui.menuLocomotiveSettings"), I18n.t("loc.ui.menuSetAsMultiUnit") })
        {
            AbstractButton item = Components.withText(menu, text);

            assertNotNull(item, "the key's menu has no \"" + text + "\"");
            assertFalse(Components.onTheEventThread(item::isEnabled), "\"" + text + "\" is lit for a moving train");
            assertEquals(Components.onTheEventThread(item::getToolTipText), stop(NAME),
                "\"" + text + "\" does not say why it is gray");
        }

        Mouse.closeMenus();

        // The name
        Component name = (Component) field("ActiveLocLabel");

        assertEquals(SettingsWindow.asked(() -> Mouse.rightClick(name, 500)), Arrays.asList(stop(NAME)), "the name said the wrong thing");

        // A function button
        javax.swing.JToggleButton f3 = SettingsWindow.functionButton(ui, 3);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> f3.isEnabled() && f3.isShowing()), WAIT));

        assertEquals(SettingsWindow.asked(() -> Mouse.rightClick(f3, 500)), Arrays.asList(stop(NAME)), "a function button said the wrong thing");

        // The database tile
        final JPopupMenu[] tile = new JPopupMenu[1];

        SwingUtilities.invokeAndWait(() -> tile[0] = new RightClickSelectorMenu(ui, null, loc));

        AbstractButton settings = Components.withText(tile[0], I18n.t("loc.ui.menuLocomotiveSettings"));

        assertTrue(Components.onTheEventThread(settings::isEnabled), "the tile's entry is gray: only the key menu grays");

        assertEquals(SettingsWindow.asked(() -> Components.onTheEventThread(() ->
        {
            settings.doClick(0);

            return null;
        })), Arrays.asList(stop(NAME)), "the tile said the wrong thing");

        assertNull(SettingsWindow.showing(), "a moving train's window opened");
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

        AbstractButton apply = Components.withText(menu, I18n.f("loc.ui.menuApplySavedSpeedPreset", 20));
        AbstractButton off = Components.withText(menu, I18n.t("loc.ui.menuTurnOffFunctions"));

        assertTrue(Components.onTheEventThread(apply::isEnabled), "Apply Saved Speed Preset is gray for a moving train");
        assertTrue(Components.onTheEventThread(off::isEnabled), "Turn Off Functions is gray for a moving train");

        Mouse.choose((JMenuItem) apply);

        assertTrue(Components.waitFor(() -> loc.getSpeed() == 20, WAIT), "the speed preset did not drive the moving train");

        menu = SettingsWindow.rightClickKey(key);

        loc.setSpeed(35);

        Mouse.choose((JMenuItem) Components.withText(menu, I18n.t("loc.ui.menuSaveSpeedAsPreset")));

        assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 35, WAIT), "Save Current Speed did not keep the moving speed");
    }

    // Helpers

    private static Object field(String name) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }


}
