package ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JToggleButton;
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
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Components;
import support.Mouse;
import support.SettingsWindow;
import support.Answering;

/**
 * Right-clicking a function button on the locomotive panel: the menu it opens, the dialog behind "Edit Function", and
 * in that dialog choosing a picture of one's own for the button and Reset.  Each claim acts as the operator (a right
 * click, a menu item, a button, a file picked in the chooser, an answered question) and asserts what the button then
 * shows and what the locomotive holds.
 *
 * The railway is a sandbox with no Central Station; the model runs in debug and later syncs are pointed at nowhere.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 *
 * @author Adam
 */
public class testTheFunctionButtonMenu
{
    private static final String NAME = "Fmenu menu train";
    private static final int ADDRESS = 3301;

    /** The function the claims work on: low enough for every decoder, and not F0, which is the lights. */
    private static final int F = 3;

    private static final long WAIT = 8000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static MarklinLocomotive loc;
    private static File folder;

    /** What the locomotive held before any claim, put back before each. */
    private static Locomotive.Customizations pristine;

    private static String iconFolderWas;
    private static boolean stationWasUnset;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the function buttons are on a window");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedWithWindowInDebug();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        // The Reset question syncs with the station afterwards, as the application's own address: pointed at nowhere
        stationWasUnset = support.TestStationAddress.get() == null;

        if (stationWasUnset) support.TestStationAddress.set(support.TestStationAddress.NOWHERE);

        // The chooser remembers the folder it was last used in, in the operator's preferences: put back after
        iconFolderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_ICON_FOLDER, null);

        folder = java.nio.file.Files.createTempDirectory("fmenu").toFile();

        if (model.getLocByName(NAME) != null) model.deleteLoc(NAME);

        loc = model.newDCCLocomotive(NAME, ADDRESS);

        pristine = loc.captureCustomizations();

        Components.settle();

        showTheTrain();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() ->
                {
                    closing.stopDrivingTrainsFromTheWholeWindow();
                    closing.dispose();
                });
            }

            if (model != null && model.getLocByName(NAME) != null) model.deleteLoc(NAME);

            if (model != null) model.stop();
        }
        finally
        {
            if (stationWasUnset) support.TestStationAddress.set(null);

            if (iconFolderWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_ICON_FOLDER);
            else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_ICON_FOLDER, iconFolderWas);

            if (folder != null)
            {
                File[] files = folder.listFiles();

                if (files != null) for (File f : files) f.delete();

                folder.delete();
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The train as it came, nothing open, and the picture on the button the one it started with. */
    @BeforeMethod
    public void lay() throws Exception
    {
        closeEverythingOpen();

        loc.applyCustomizations(pristine);
        loc.setDepartureFunc(null);
        loc.setArrivalFunc(null);
        loc.setSpeed(0);
        loc.functionsOff();

        ui.repaintLoc(true, null);

        Components.settle();
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        closeEverythingOpen();
    }

    // The right-click

    /**
     * A right click on a function button opens the Locomotive Settings window on the Functions tab, on that function,
     * with no menu in between.  A function the train does not have opens nothing.
     *
     * MUTATION: in `TrainControlUI.EditFunction`, drop the `b.isEnabled()` test, or aim the window at `fNumber + 1`,
     * and this fails.
     */
    @Test
    public void testARightClickOpensTheFunctionsTabOnThatFunction() throws Exception
    {
        assertNull(rightClick(button(F)), "a right click on F" + F + " opened a menu: the old menu is meant to be gone");

        assertNotNull(SettingsWindow.awaited(), "a right click on F" + F + " opened no window");
        assertEquals(SettingsWindow.tabShowing(), 1, "the window did not open on the Functions tab");

        JTable table = SettingsWindow.part("functions.table", JTable.class);

        assertEquals((int) Components.onTheEventThread(table::getSelectedRow), F, "the Functions tab is not on F" + F);

        SettingsWindow.closeIfOpen();

        // F30 is past the 29 functions of a DCC decoder: grayed, and a right click on it opens nothing
        final JToggleButton beyond = button(30);

        assertFalse(Components.onTheEventThread(beyond::isEnabled), "precondition: F30 is enabled on a DCC train");

        rightClick(beyond, 1500);

        Components.settle();

        assertNull(SettingsWindow.showing(), "a right click on a grayed function button opened the window");
    }

    /** Whether the Functions table has this function's row selected and wholly inside what it shows. */
    private static boolean rowOnShow(JTable table, int row)
    {
        return Components.onTheEventThread(() -> table.getSelectedRow() == row
            && table.getVisibleRect().contains(table.getCellRect(row, 0, true)));
    }

    /**
     * A right click on a high function button opens the window with that function's row selected and scrolled into view;
     * with the window already open on another tab, or scrolled elsewhere, a right click on another function brings that
     * row into view too.
     *
     * MUTATION: drop the `scrollRectToVisible` from `LocomotiveFunctionsTab.select`, or ask for it before the tab is
     * laid out, and this fails.
     */
    @Test
    public void testARightClickScrollsToThatFunction() throws Exception
    {
        final int high = 28;

        rightClick(button(high));

        assertNotNull(SettingsWindow.awaited(), "a right click on F" + high + " opened no window");

        JTable table = SettingsWindow.part("functions.table", JTable.class);

        assertTrue(Components.waitFor(() -> rowOnShow(table, high), WAIT), "F" + high + " is not selected and on show");

        // Already open on another tab: switched to Functions, and the row brought in
        SettingsWindow.selectTab(0);

        Components.onTheEventThread(() ->
        {
            table.scrollRectToVisible(table.getCellRect(0, 0, true));

            return null;
        });

        rightClick(button(25));

        assertTrue(Components.waitFor(() -> SettingsWindow.tabShowing() == 1, WAIT), "the window stayed on its other tab");
        assertTrue(Components.waitFor(() -> rowOnShow(SettingsWindow.part("functions.table", JTable.class), 25), WAIT),
            "F25 is not selected and on show after the tab changed");

        // Scrolled to the bottom, then a function at the top
        Components.onTheEventThread(() ->
        {
            table.scrollRectToVisible(table.getCellRect(high, 0, true));

            return null;
        });

        rightClick(button(F));

        assertTrue(Components.waitFor(() -> rowOnShow(SettingsWindow.part("functions.table", JTable.class), F), WAIT),
            "F" + F + " is not on show after the table was scrolled to the bottom");
    }

    /**
     * The presets act on the train that is showing, from the key menu where they live now: Save Current Functions
     * keeps what is on, Turn Off Functions switches them off, Apply puts the saved ones back, and the speed pair does
     * the same for the speed.
     *
     * MUTATION: connect Turn Off Functions to `ui.applyPreferredFunctions` in `RightClickMenuListener`, and this fails.
     */
    @Test
    public void testThePresetsActOnTheTrain() throws Exception
    {
        loc.setF(1, true);
        loc.setF(F, true);

        assertTrue(Components.waitFor(() -> loc.getF(1) && loc.getF(F), WAIT), "precondition: f1 and f3 did not switch on");

        choose(I18n.t("loc.ui.menuSaveFunctionsAsPreset"));

        assertTrue(Components.waitFor(() -> saved(1) && saved(F), WAIT), "Save Current Functions as Preset did not save f1 and f3");

        choose(I18n.t("loc.ui.menuTurnOffFunctions"));

        assertTrue(Components.waitFor(() -> !loc.getF(1) && !loc.getF(F), WAIT), "Turn Off Functions left a function on");

        choose(I18n.t("loc.ui.menuApplySavedFunctionPreset"));

        assertTrue(Components.waitFor(() -> loc.getF(1) && loc.getF(F), WAIT * 4),
            "Apply Saved Function Preset did not put the saved functions back");

        loc.setSpeed(41);

        choose(I18n.t("loc.ui.menuSaveSpeedAsPreset"));

        assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 41, WAIT), "Save Current Speed as Preset did not keep 41");

        loc.setSpeed(0);

        choose(I18n.f("loc.ui.menuApplySavedSpeedPreset", 41));

        assertTrue(Components.waitFor(() -> loc.getSpeed() == 41, WAIT), "Apply Saved Speed Preset did not drive the train at 41");
    }

    /**
     * An error thrown while a sensor message is handled is written to TrainControl's log window, naming the thread, and
     * its stack trace is printed on the console, rather than being lost in a Future nobody reads.
     *
     * MUTATION: in `MarklinControlStation.receiveMessage`, `feedbackMessageProcessor.execute(` to `.submit(`; or drop
     * `thread.setUncaughtExceptionHandler(THREAD_ERRORS)` from `messageProcessor`, or the `station.log(said)` in the
     * handler.
     */
    @Test
    public void testAFailureInASensorTaskReachesTheLogWindow() throws Exception
    {
        javax.swing.JTextArea area = support.WindowField.of(ui, "debugArea");

        // A sensor frame with no payload: the task reads the sensor number from it and fails on the message thread.
        model.receiveMessage(new org.traincontrol.marklin.udp.CS2Message(
            org.traincontrol.marklin.udp.CS2Message.CMD_ACC_SENSOR, 0x7b34, true, new byte[0]));

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(area::getText).contains("cs2-feedback-messages"),
            30000), "an error thrown while a sensor message was handled never reached the log window: submit keeps it "
            + "in a Future nobody reads, and without the thread's handler nothing is written");
    }

    /**
     * A train with no saved speed preset has nothing to apply: the menu item is grayed and says why.
     *
     * MUTATION: drop the `getPreferredSpeed() < 1` test in `RightClickMenuListener`, and this fails.
     */
    @Test
    public void testApplyingNoSpeedPresetIsGrayedOut() throws Exception
    {
        loc.setPreferredSpeed(0);

        JPopupMenu menu = SettingsWindow.rightClickKey(SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_A));

        assertNotNull(menu, "a right click on the key opened no menu");

        JMenuItem item = (JMenuItem) Components.withText(menu, I18n.f("loc.ui.menuApplySavedSpeedPreset", 0));

        assertNotNull(item, "the key's menu has no Apply Saved Speed Preset");
        assertFalse(Components.onTheEventThread(item::isEnabled), "Apply Saved Speed Preset is enabled with no preset saved");
        assertEquals(Components.onTheEventThread(item::getToolTipText), I18n.t("loc.ui.tooltip.noSpeedPreset"),
            "the grayed item does not say why");
    }

    // Autonomy's departure and arrival functions were ticked on the old menu and in its editor; they move to the
    // Autonomy tab, which is built separately, and the two claims that pinned them wait for it.

    /**
     * The departure and arrival ticks of the old function menu: the menu is gone and the Autonomy tab that replaces
     * them is not built yet, so this waits to be re-pointed there.
     */
    @Test(enabled = false)
    public void testTheMenuTicksTheAutonomyFunctions() throws Exception
    {
        throw new SkipException("re-point to the Autonomy tab's departure and arrival dropdowns when it is built");
    }

    /**
     * Cancel put the departure and arrival ticks back in the old editor: the Autonomy tab will hold them, and Cancel
     * will leave them as they were.
     */
    @Test(enabled = false)
    public void testCancelPutsTheAutonomyTicksBack() throws Exception
    {
        throw new SkipException("re-point to the Autonomy tab's departure and arrival dropdowns when it is built");
    }

    // The Functions tab behind the right-click

    /**
     * Picking another icon and a momentary behavior in the row and pressing Save gives the train that icon and
     * behavior for that function and puts the icon on the button.
     *
     * MUTATION: in `LocomotiveSettingsFrame.write`, skip `functionsTab.apply`, and this fails.
     */
    @Test
    public void testSaveOnTheTabChangesTheFunctionAndItsButton() throws Exception
    {
        int was = loc.getFunctionType(F);
        int icon = (was + 7) % 100 + 1;

        openTheTab(F);

        SettingsWindow.pickIcon(F, icon);
        SettingsWindow.pickBehavior(F, 1);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getFunctionType(F) == icon, WAIT),
            "Save did not give F" + F + " the icon chosen: it has " + loc.getFunctionType(F));

        assertTrue(loc.isFunctionPulse(F), "Save did not make F" + F + " momentary, as chosen");
        assertTrue(loc.isCustomFunctions(), "a train with a changed function is not marked as customized");

        assertTrue(Components.waitFor(() -> shows(button(F), currentIcon(F)), WAIT),
            "the button for F" + F + " does not show the icon chosen on the tab");
    }

    /**
     * Cancel changes nothing, though an icon and a behavior were chosen.
     *
     * MUTATION: write the table from Cancel as well, and this fails.
     */
    @Test
    public void testCancelOnTheTabChangesNothing() throws Exception
    {
        int was = loc.getFunctionType(F);
        int[] types = loc.getFunctionTypes().clone();

        openTheTab(F);

        SettingsWindow.pickIcon(F, (was + 7) % 100 + 1);
        SettingsWindow.pickBehavior(F, 1);

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Cancel did not close the window");

        assertEquals(loc.getFunctionTypes(), types, "Cancel changed the function types");
        assertFalse(loc.isFunctionPulse(F), "Cancel made F" + F + " momentary");
        assertFalse(loc.isCustomFunctions(), "Cancel marked the train as customized");
    }

    // A picture of one's own for the button

    /**
     * The wrench in a row opens the chooser; the picture picked shows in the table, and Save gives the function that
     * picture, which the button then shows.  The train is then customized.
     *
     * MUTATION: leave the picked image out of the row in `useOwnImage`, and this fails.
     */
    @Test
    public void testACustomIconPickedOnTheTabReachesTheButton() throws Exception
    {
        File picture = SettingsWindow.picture(folder, "green.png", Color.GREEN);
        String url = picture.toPath().toUri().toString();

        openTheTab(F);

        SettingsWindow.pickAnImage(F, picture);

        assertNull(loc.getLocalFunctionImageURL(F), "the picture was given to the function before Save");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> url.equals(loc.getLocalFunctionImageURL(F)), WAIT),
            "Save did not give F" + F + " the picture chosen: it has " + loc.getLocalFunctionImageURL(F));

        assertTrue(loc.isCustomFunctions(), "a train with a custom icon is not marked as customized");

        assertTrue(Components.waitFor(() -> centerIs(button(F), Color.GREEN), WAIT),
            "the button for F" + F + " does not show the picture chosen");
    }

    /**
     * Cancel after choosing a picture leaves the function with the icon it had.
     *
     * MUTATION: call `loc.setLocalFunctionImageURL` as the picture is picked, and this fails.
     */
    @Test
    public void testACanceledCustomIconIsNotKept() throws Exception
    {
        openTheTab(F);

        SettingsWindow.pickAnImage(F, SettingsWindow.picture(folder, "red.png", Color.RED));

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Cancel did not close the window");

        assertNull(loc.getLocalFunctionImageURL(F), "Cancel kept the picture chosen on the tab");
        assertFalse(loc.isCustomFunctions(), "Cancel left the train marked as customized");
    }

    /**
     * Picking a standard icon in the row of a function that has a picture of one's own replaces the picture at Save, and
     * the button goes back to the icon its type gives; the picture itself is the user's file and stays.
     *
     * MUTATION: keep the picture when a standard icon is picked, in `useStandardIcon`, and this fails.
     */
    @Test
    public void testAStandardIconGivesTheButtonItsOwnBack() throws Exception
    {
        File picture = SettingsWindow.picture(folder, "blue.png", Color.BLUE);

        loc.setFunctionType(F, 5, Locomotive.FUNCTION_TOGGLE);
        loc.setLocalFunctionImageURL(F, picture.toURI().toString());

        ui.repaintLoc(true, null);

        assertTrue(Components.waitFor(() -> centerIs(button(F), Color.BLUE), WAIT), "precondition: the button does not show the picture");

        openTheTab(F);

        SettingsWindow.pickIcon(F, 5);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getLocalFunctionImageURL(F) == null, WAIT),
            "Save after picking a standard icon left the picture on the function");

        assertTrue(Components.waitFor(() -> shows(button(F), currentIcon(F)), WAIT),
            "the button does not go back to the icon the function's type gives");

        assertTrue(picture.isFile(), "replacing the picture deleted the user's file");
    }

    // Reset

    /**
     * Reset All Customizations asks first: No changes nothing; Yes marks the train for Save, which takes the custom
     * functions and pictures off it and the button goes back to the Central Station's icon.  Grayed on a train that is
     * not customized.
     *
     * MUTATION: in `resetEverything`, test for `JOptionPane.NO_OPTION` instead of `YES_OPTION`, and this fails.
     */
    @Test
    public void testResetAsksAndThenGivesTheStationsFunctionsBack() throws Exception
    {
        File picture = SettingsWindow.picture(folder, "yellow.png", Color.YELLOW);

        openTheTab(F);

        assertFalse(Components.onTheEventThread(SettingsWindow.part("functions.resetAll", JButton.class)::isEnabled),
            "Reset is offered on a train that is not customized");

        SettingsWindow.closeIfOpen();

        loc.setFunctionType(F, 9, Locomotive.FUNCTION_PULSE);
        loc.setLocalFunctionImageURL(F + 1, picture.toURI().toString());

        ui.repaintLoc(true, null);

        openTheTab(F);

        JButton reset = SettingsWindow.part("functions.resetAll", JButton.class);

        assertTrue(Components.onTheEventThread(reset::isEnabled), "Reset is grayed on a customized train");

        // NO: nothing changes
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            Components.press(reset);

            assertTrue(answering.awaitAsked(1, WAIT), "Reset asked no question");
            assertEquals(answering.asked(), java.util.Arrays.asList(I18n.t("loc.ui.confirmResetFunctionsToCentralStation")));
        }

        Components.settle();

        SettingsWindow.closeIfOpen();

        assertTrue(loc.isCustomFunctions(), "No on the Reset question took the customizations off");
        assertNotNull(loc.getLocalFunctionImageURL(F + 1), "No on the Reset question took a picture off");
        assertEquals(loc.getFunctionType(F), 9, "No on the Reset question changed a function's icon");

        // YES: the train is the station's again, at Save
        openTheTab(F);

        reset = SettingsWindow.part("functions.resetAll", JButton.class);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            Components.press(reset);

            assertTrue(answering.awaitAsked(1, WAIT), "Reset asked no question the second time");
        }

        Components.settle();

        assertTrue(loc.isCustomFunctions(), "Yes on the Reset question reset the train before Save");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> !loc.isCustomFunctions(), WAIT),
            "Save after Yes on the Reset question left the train customized");

        assertTrue(loc.getLocalFunctionImageURLs().isEmpty(), "Save after Yes on the Reset question left a picture on a function");

        assertTrue(Components.waitFor(() -> shows(button(F), currentIcon(F)), WAIT),
            "the button does not show the icon its function type gives after Reset");
    }

    // The window

    /** Makes the train the one on show, as choosing it on the keyboard does. */
    private static void showTheTrain() throws Exception
    {
        SettingsWindow.showOnKeyA(ui, loc);

        Components.onTheEventThread(() ->
        {
            if (ui.getActiveLoc() != loc) ui.mapLocToCurrentButton(NAME);

            return null;
        });

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == loc, WAIT), "precondition: the train is not the one on show");

        ui.repaintLoc(true, null);

        Components.settle();

        // Rendered once its function buttons are enabled and on screen
        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> button(F).isEnabled() && button(F).isShowing()), WAIT),
            "precondition: the function buttons did not come up");
    }

    /** The loaded state autonomy is in, as far as the menu and the editor ask: set by the window's own field. */
    private static void setAutonomyLoaded(boolean loaded) throws Exception
    {
        java.lang.reflect.Field name = TrainControlUI.class.getDeclaredField("activeDiagramConfiguration");

        name.setAccessible(true);

        name.set(ui, loaded ? "fmenu" : null);

        assertEquals(ui.isAutonomyLoaded(), loaded, "precondition: the window's autonomy state was not set");
    }

    private static JToggleButton button(int function)
    {
        try
        {
            java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("F" + function);

            f.setAccessible(true);

            return (JToggleButton) f.get(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static JPopupMenu rightClick(JToggleButton button)
    {
        return rightClick(button, WAIT);
    }

    private static JPopupMenu rightClick(JToggleButton button, long millis)
    {
        Components.select(button);

        return Mouse.rightClick(button, millis);
    }

    /** Opens the key's menu and chooses an item of it. */
    private static void choose(String item) throws Exception
    {
        JPopupMenu menu = SettingsWindow.rightClickKey(SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_A));

        assertNotNull(menu, "a right click on the key opened no menu");

        JMenuItem found = (JMenuItem) Components.withText(menu, item);

        assertNotNull(found, "the key's menu has no \"" + item + "\"");

        Mouse.choose(found);
    }

    /** The right-click on a function's button, and the window it opens. */
    private static void openTheTab(int function) throws Exception
    {
        rightClick(button(function));

        assertNotNull(SettingsWindow.awaited(), "a right click on F" + function + " opened no window");

        // Its icons load after it opens, and choosing one before they have replaced the list is lost
        Components.settle();
        Components.settle();
    }





    /** Whatever is open is closed, as a stray window would be by the operator. */
    private static void closeEverythingOpen()
    {
        Mouse.closeMenus();

        SettingsWindow.closeIfOpen();

        Components.onTheEventThread(() ->
        {
            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (w instanceof javax.swing.JDialog && w.isShowing()) w.dispose();
            }

            return null;
        });

        Components.settle();
    }

    private static boolean saved(int function)
    {
        boolean[] preset = loc.getPreferredFunctions();

        return preset != null && preset.length > function && preset[function];
    }

    // Pictures


    /** What the window itself loads for a function's current icon: the same picture the button shows. */
    private static java.awt.Image currentIcon(int function)
    {
        try
        {
            return ui.getLocImage(loc.getFunctionIconUrl(function, loc.getFunctionType(function),
                model.isCS3() || !model.getNetworkCommState(), true), TrainControlUI.BUTTON_ICON_WIDTH);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static boolean shows(JToggleButton button, java.awt.Image picture)
    {
        return Components.onTheEventThread(() ->
            button.getIcon() instanceof ImageIcon && ((ImageIcon) button.getIcon()).getImage() == picture);
    }

    /** Whether the middle of the button's icon is this color. */
    private static boolean centerIs(JToggleButton button, Color color)
    {
        return Components.onTheEventThread(() ->
        {
            if (!(button.getIcon() instanceof ImageIcon)) return false;

            java.awt.Image image = ((ImageIcon) button.getIcon()).getImage();

            BufferedImage read = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = read.createGraphics();

            g.drawImage(image, 0, 0, null);
            g.dispose();

            return read.getRGB(read.getWidth() / 2, read.getHeight() / 2) == color.getRGB();
        });
    }
}
