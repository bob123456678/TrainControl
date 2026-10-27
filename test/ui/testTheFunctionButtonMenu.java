package ui;

import java.awt.Color;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
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

    // The menu

    /**
     * A right click on a function button opens the menu for that function: Edit Function F-number first, then the
     * function and speed presets, the last carrying the speed the train has saved.  A function the train does not have
     * opens none.
     *
     * MUTATION: in `TrainControlUI.EditFunction`, drop the `b.isEnabled()` test, or in `FunctionPopupMenu` give the
     * Edit item `fNumber + 1`, and this fails.
     */
    @Test
    public void testARightClickOpensTheMenuForThatFunction() throws Exception
    {
        loc.setPreferredSpeed(37);

        JPopupMenu menu = rightClick(button(F));

        assertNotNull(menu, "a right click on F" + F + " opened no menu");

        assertTrue(Components.withText(menu, I18n.f("loc.ui.editFunction", F)) != null,
            "the menu has no \"" + I18n.f("loc.ui.editFunction", F) + "\"");

        for (String key : new String[] {"loc.ui.menuApplySavedFunctionPreset", "loc.ui.menuSaveFunctionsAsPreset",
            "loc.ui.menuTurnOffFunctions", "loc.ui.menuSaveSpeedAsPreset"})
        {
            assertNotNull(Components.withText(menu, I18n.t(key)), "the menu has no \"" + I18n.t(key) + "\"");
        }

        assertNotNull(Components.withText(menu, I18n.f("loc.ui.menuApplySavedSpeedPreset", 37)),
            "the speed preset item does not name the speed the train has saved");

        assertEquals(itemsOf(menu).get(0).getText(), I18n.f("loc.ui.editFunction", F),
            "Edit Function is not the first thing the menu offers");

        Mouse.closeMenus();

        // F30 is past the 29 functions of a DCC decoder: grayed, and a right click on it opens nothing
        final JToggleButton beyond = button(30);

        assertFalse(Components.onTheEventThread(beyond::isEnabled), "precondition: F30 is enabled on a DCC train");

        assertNull(rightClick(beyond, 1500), "a right click on a grayed function button opened a menu");
    }

    /**
     * The presets in the menu act on the train that is showing: Save Current Functions keeps what is on, Turn Off
     * Functions switches them off, Apply puts the saved ones back, and the speed pair does the same for the speed.
     *
     * MUTATION: connect Turn Off Functions to `tcui.applyPreferredFunctions` in `FunctionPopupMenu`, and this fails.
     */
    @Test
    public void testTheMenuPresetsActOnTheTrain() throws Exception
    {
        loc.setF(1, true);
        loc.setF(F, true);

        assertTrue(Components.waitFor(() -> loc.getF(1) && loc.getF(F), WAIT), "precondition: f1 and f3 did not switch on");

        choose(F, I18n.t("loc.ui.menuSaveFunctionsAsPreset"));

        assertTrue(Components.waitFor(() -> saved(1) && saved(F), WAIT), "Save Current Functions as Preset did not save f1 and f3");

        choose(F, I18n.t("loc.ui.menuTurnOffFunctions"));

        assertTrue(Components.waitFor(() -> !loc.getF(1) && !loc.getF(F), WAIT), "Turn Off Functions left a function on");

        choose(F, I18n.t("loc.ui.menuApplySavedFunctionPreset"));

        assertTrue(Components.waitFor(() -> loc.getF(1) && loc.getF(F), WAIT * 4),
            "Apply Saved Function Preset did not put the saved functions back");

        loc.setSpeed(41);

        choose(F, I18n.t("loc.ui.menuSaveSpeedAsPreset"));

        assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 41, WAIT), "Save Current Speed as Preset did not keep 41");

        loc.setSpeed(0);

        choose(F, I18n.f("loc.ui.menuApplySavedSpeedPreset", 41));

        assertTrue(Components.waitFor(() -> loc.getSpeed() == 41, WAIT), "Apply Saved Speed Preset did not drive the train at 41");
    }

    /**
     * With autonomy loaded the menu also holds the two ticks for the function autonomy uses on departure and on
     * arrival: ticking one makes this function the train's, a function that holds the slot names itself on the other,
     * and unticking clears it.  Without autonomy the menu holds neither.
     *
     * MUTATION: in `autonomySlot`, write the arrival slot from the departure item (`activeLoc.setDepartureFunc(now)` in
     * both arms), and this fails.
     */
    @Test
    public void testTheMenuTicksTheAutonomyFunctions() throws Exception
    {
        String departure = I18n.t("loc.ui.menuAutonomyDepartureFunction");
        String arrival = I18n.t("loc.ui.menuAutonomyArrivalFunction");

        assertTrue(ui.isAutonomyLoaded(), "precondition: the fixture window has no autonomy loaded");

        // With none loaded, neither is offered
        setAutonomyLoaded(false);

        try
        {
            JPopupMenu without = rightClick(button(F));

            assertNull(Components.withText(without, departure), "the menu offers the departure tick with no autonomy loaded");
            assertNull(Components.withText(without, arrival), "the menu offers the arrival tick with no autonomy loaded");

            Mouse.closeMenus();
        }
        finally
        {
            setAutonomyLoaded(true);
        }

        JPopupMenu menu = rightClick(button(F));

        JCheckBoxMenuItem dep = (JCheckBoxMenuItem) Components.withText(menu, departure);
        JCheckBoxMenuItem arr = (JCheckBoxMenuItem) Components.withText(menu, arrival);

        assertNotNull(dep, "the menu has no departure tick with autonomy loaded");
        assertNotNull(arr, "the menu has no arrival tick with autonomy loaded");

        assertFalse(dep.isSelected() || arr.isSelected(), "a tick is set before anything was ticked");

        Mouse.choose(dep);

        assertEquals(loc.getDepartureFunc(), Integer.valueOf(F), "ticking departure did not make F" + F + " the departure function");
        assertNull(loc.getArrivalFunc(), "ticking departure set the arrival function as well");

        // Another function's menu shows who holds the slot, unticked
        menu = rightClick(button(F + 1));

        dep = (JCheckBoxMenuItem) Components.withText(menu, departure + "  (F" + F + ")");

        assertNotNull(dep, "another function's menu does not name F" + F + " as the departure function");
        assertFalse(dep.isSelected(), "another function's menu shows the departure tick as its own");

        Mouse.closeMenus();

        // And F3's own menu shows it ticked, and unticking clears it
        menu = rightClick(button(F));

        dep = (JCheckBoxMenuItem) Components.withText(menu, departure);

        assertTrue(dep != null && dep.isSelected(), "the function that holds the departure slot does not show it ticked");

        Mouse.choose(dep);

        assertNull(loc.getDepartureFunc(), "unticking the departure function did not clear it");

        // The arrival slot is its own
        menu = rightClick(button(F));

        Mouse.choose((JMenuItem) Components.withText(menu, arrival));

        assertEquals(loc.getArrivalFunc(), Integer.valueOf(F), "ticking arrival did not make F" + F + " the arrival function");
        assertNull(loc.getDepartureFunc(), "ticking arrival set the departure function");
    }

    // The dialog behind Edit Function

    /**
     * Edit Function opens the function editor for that function, titled with the train; picking another icon and a
     * momentary trigger and pressing OK gives the train that icon and trigger for that function and puts the icon on
     * the button.
     *
     * MUTATION: in `openEditDialog`, test for `JOptionPane.CANCEL_OPTION` where it tests for `OK_OPTION`, and this
     * fails.
     */
    @Test
    public void testOkOnTheEditorChangesTheFunctionAndItsButton() throws Exception
    {
        int was = loc.getFunctionType(F);
        int icon = (was + 7) % 100 + 1;

        JDialog dialog = openTheEditor(F);

        JComboBox<?> icons = iconsIn(dialog);
        JComboBox<?> trigger = triggerIn(dialog);

        Components.onTheEventThread(() ->
        {
            icons.setSelectedIndex(icon);
            trigger.setSelectedIndex(1);

            return null;
        });

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> loc.getFunctionType(F) == icon, WAIT),
            "OK on the editor did not give F" + F + " the icon chosen: it has " + loc.getFunctionType(F));

        assertTrue(loc.isFunctionPulse(F), "OK on the editor did not make F" + F + " momentary, as chosen");
        assertTrue(loc.isCustomFunctions(), "a train with a changed function is not marked as customized");

        assertTrue(Components.waitFor(() -> shows(button(F), currentIcon(F)), WAIT),
            "the button for F" + F + " does not show the icon chosen in the editor");
    }

    /**
     * Cancel on the editor changes nothing, though an icon and a trigger were chosen in it.
     *
     * MUTATION: in `openEditDialog`, call `edit.doApply()` in the Cancel branch as well, and this fails.
     */
    @Test
    public void testCancelOnTheEditorChangesNothing() throws Exception
    {
        int was = loc.getFunctionType(F);
        int[] types = loc.getFunctionTypes().clone();

        JDialog dialog = openTheEditor(F);

        JComboBox<?> icons = iconsIn(dialog);
        JComboBox<?> trigger = triggerIn(dialog);

        Components.onTheEventThread(() ->
        {
            icons.setSelectedIndex((was + 7) % 100 + 1);
            trigger.setSelectedIndex(1);

            return null;
        });

        press(dialog, I18n.t("ui.cancel"));

        assertClosed(dialog, "Cancel did not close the editor");

        Components.settle();

        assertEquals(loc.getFunctionTypes(), types, "Cancel on the editor changed the function types");
        assertFalse(loc.isFunctionPulse(F), "Cancel on the editor made F" + F + " momentary");
        assertFalse(loc.isCustomFunctions(), "Cancel on the editor marked the train as customized");
    }

    /**
     * With autonomy loaded the editor carries the two ticks as well: they follow the function the editor shows, write
     * to the train at once, and Cancel puts them back as they were.
     *
     * MUTATION: delete the two `activeLoc.setDepartureFunc(departureWas)` / `setArrivalFunc(arrivalWas)` lines of the
     * Cancel branch, and this fails.
     */
    @Test
    public void testCancelPutsTheAutonomyTicksBack() throws Exception
    {
        assertTrue(ui.isAutonomyLoaded(), "precondition: the fixture window has no autonomy loaded");

        loc.setArrivalFunc(2);

        JDialog dialog = openTheEditor(F);

        String departure = I18n.t("loc.ui.menuAutonomyDepartureFunction");
        String arrival = I18n.t("loc.ui.menuAutonomyArrivalFunction");

        AbstractButton dep = Components.withText(dialog, departure);
        AbstractButton arr = Components.withText(dialog, arrival + "  (F2)");

        assertNotNull(dep, "the editor has no departure tick with autonomy loaded");
        assertNotNull(arr, "the editor's arrival tick does not name F2, which holds the slot");

        Components.onTheEventThread(() ->
        {
            dep.doClick(0);
            arr.doClick(0);

            return null;
        });

        assertEquals(loc.getDepartureFunc(), Integer.valueOf(F), "ticking departure in the editor did not reach the train");
        assertEquals(loc.getArrivalFunc(), Integer.valueOf(F), "ticking arrival in the editor did not move the slot here");

        press(dialog, I18n.t("ui.cancel"));

        assertClosed(dialog, "Cancel did not close the editor");

        Components.settle();

        assertNull(loc.getDepartureFunc(), "Cancel kept the departure tick");
        assertEquals(loc.getArrivalFunc(), Integer.valueOf(2), "Cancel did not give the arrival slot back to F2");
    }

    // A picture of one's own for the button

    /**
     * Use Custom Icon in the editor opens the chooser; the picture picked shows in the editor, and OK gives the
     * function that picture, which the button then shows.  The train is then customized, and the chooser starts in the
     * picture's folder next time.
     *
     * MUTATION: in `useCustomFunctionIconActionPerformed`, leave `customIconPath` unassigned when the chooser is
     * approved, and this fails.
     */
    @Test
    public void testACustomIconPickedInTheEditorReachesTheButton() throws Exception
    {
        File picture = picture("green.png", Color.GREEN);
        String url = picture.toPath().toUri().toString();

        JDialog dialog = openTheEditor(F);

        pickACustomIcon(dialog, picture);

        JLabel shown = customIconIn(dialog);

        assertTrue(Components.waitFor(() -> shown.getIcon() != null, WAIT), "the editor does not show the picture chosen");
        assertTrue(Components.onTheEventThread(() -> Components.withText(dialog, I18n.t("loc.ui.clearCustomIcon")).isVisible()),
            "with a picture chosen the editor offers no Clear Custom Icon");

        assertNull(loc.getLocalFunctionImageURL(F), "the picture was given to the function before OK");

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> url.equals(loc.getLocalFunctionImageURL(F)), WAIT),
            "OK did not give F" + F + " the picture chosen: it has " + loc.getLocalFunctionImageURL(F));

        assertTrue(loc.isCustomFunctions(), "a train with a custom icon is not marked as customized");

        assertTrue(Components.waitFor(() -> centerIs(button(F), Color.GREEN), WAIT),
            "the button for F" + F + " does not show the picture chosen");
    }

    /**
     * Cancel after choosing a picture leaves the function with the icon it had.
     *
     * MUTATION: in `useCustomFunctionIconActionPerformed`, call `loc.setLocalFunctionImageURL` as the picture is picked,
     * and this fails.
     */
    @Test
    public void testACanceledCustomIconIsNotKept() throws Exception
    {
        File picture = picture("red.png", Color.RED);

        JDialog dialog = openTheEditor(F);

        pickACustomIcon(dialog, picture);

        press(dialog, I18n.t("ui.cancel"));

        assertClosed(dialog, "Cancel did not close the editor");

        Components.settle();

        assertNull(loc.getLocalFunctionImageURL(F), "Cancel kept the picture chosen in the editor");
        assertFalse(loc.isCustomFunctions(), "Cancel left the train marked as customized");
    }

    /**
     * Clear Custom Icon, then OK, takes the picture off the function and the button goes back to the icon its type
     * gives; the picture itself is the user's file and stays.
     *
     * MUTATION: have `applyCustomFunctionIcon` skip `unsetLocalFunctionImageURL` for "reset", and this fails.
     */
    @Test
    public void testClearingACustomIconGivesTheButtonItsOwnBack() throws Exception
    {
        File picture = picture("blue.png", Color.BLUE);

        loc.setFunctionType(F, 5, Locomotive.FUNCTION_TOGGLE);
        loc.setLocalFunctionImageURL(F, picture.toURI().toString());

        ui.repaintLoc(true, null);

        assertTrue(Components.waitFor(() -> centerIs(button(F), Color.BLUE), WAIT), "precondition: the button does not show the picture");

        JDialog dialog = openTheEditor(F);

        AbstractButton clear = Components.withText(dialog, I18n.t("loc.ui.clearCustomIcon"));

        assertTrue(Components.onTheEventThread(clear::isVisible), "precondition: no Clear Custom Icon beside a function with a picture");

        Components.press(clear);
        Components.settle();

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> loc.getLocalFunctionImageURL(F) == null, WAIT),
            "OK after Clear Custom Icon left the picture on the function");

        assertTrue(Components.waitFor(() -> shows(button(F), currentIcon(F)), WAIT),
            "the button does not go back to the icon the function's type gives");

        assertTrue(picture.isFile(), "clearing the icon deleted the user's picture");
    }

    // Reset

    /**
     * Reset All Customizations asks first: No changes nothing; Yes takes the custom functions and pictures off the
     * train and the button goes back to the Central Station's icon.  Grayed on a train that is not customized.
     *
     * MUTATION: in `resetButtonActionPerformed`, test for `JOptionPane.NO_OPTION` instead of `YES_OPTION`, and this
     * fails.
     */
    @Test
    public void testResetAsksAndThenGivesTheStationsFunctionsBack() throws Exception
    {
        File picture = picture("yellow.png", Color.YELLOW);

        JDialog dialog = openTheEditor(F);

        AbstractButton reset = Components.withText(dialog, I18n.t("loc.ui.resetAllCustomizations"));

        assertFalse(Components.onTheEventThread(reset::isEnabled), "Reset is offered on a train that is not customized");

        press(dialog, I18n.t("ui.cancel"));

        assertClosed(dialog, "Cancel did not close the editor");

        loc.setFunctionType(F, 9, Locomotive.FUNCTION_PULSE);
        loc.setLocalFunctionImageURL(F + 1, picture.toURI().toString());

        ui.repaintLoc(true, null);

        dialog = openTheEditor(F);

        reset = Components.withText(dialog, I18n.t("loc.ui.resetAllCustomizations"));

        final AbstractButton resetting = reset;

        assertTrue(Components.onTheEventThread(resetting::isEnabled), "Reset is grayed on a customized train");

        // NO: nothing changes
        Components.press(reset);

        JDialog question = questionShowing(I18n.t("loc.ui.confirmResetFunctionsToCentralStation"));

        assertNotNull(question, "Reset asked no question");

        press(question, I18n.t("ui.no"));

        assertClosed(question, "No did not close the question");

        Components.settle();

        assertTrue(loc.isCustomFunctions(), "No on the Reset question took the customizations off");
        assertNotNull(loc.getLocalFunctionImageURL(F + 1), "No on the Reset question took a picture off");
        assertEquals(loc.getFunctionType(F), 9, "No on the Reset question changed a function's icon");

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(resetting::isEnabled), WAIT),
            "Reset stayed grayed after No");

        // YES: the train is the station's again
        Components.press(reset);

        question = questionShowing(I18n.t("loc.ui.confirmResetFunctionsToCentralStation"));

        assertNotNull(question, "Reset asked no question the second time");

        press(question, I18n.t("ui.yes"));

        assertTrue(Components.waitFor(() -> !loc.isCustomFunctions(), WAIT),
            "Yes on the Reset question left the train customized");

        assertTrue(loc.getLocalFunctionImageURLs().isEmpty(), "Yes on the Reset question left a picture on a function");

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> !resetting.isEnabled()), WAIT),
            "Reset is still offered after it reset everything");

        press(dialog, I18n.t("ui.cancel"));

        assertClosed(dialog, "Cancel did not close the editor");

        assertTrue(Components.waitFor(() -> shows(button(F), currentIcon(F)), WAIT),
            "the button does not show the icon its function type gives after Reset");
    }

    // The window

    /** Makes the train the one on show, as choosing it on the keyboard does. */
    private static void showTheTrain() throws Exception
    {
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

    /** Right-clicks a function button and chooses an item of the menu that opens. */
    private static void choose(int function, String item)
    {
        JPopupMenu menu = rightClick(button(function));

        assertNotNull(menu, "a right click on F" + function + " opened no menu");

        JMenuItem found = (JMenuItem) Components.withText(menu, item);

        assertNotNull(found, "the menu has no \"" + item + "\"");

        Mouse.choose(found);
    }

    private static List<JMenuItem> itemsOf(JPopupMenu menu)
    {
        return Components.all(menu, JMenuItem.class, i -> true);
    }

    /** Edit Function from the menu of a function's button, and the editor that opens. */
    private static JDialog openTheEditor(int function) throws Exception
    {
        choose(function, I18n.f("loc.ui.editFunction", function));

        JDialog dialog = dialogShowing(d -> I18n.f("loc.ui.dialogEditLocomotiveFunctions", NAME).equals(d.getTitle()));

        assertNotNull(dialog, "Edit Function F" + function + " opened no editor");

        // Its icons load after it opens, and choosing one before they have replaced the list is lost
        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> loaded(dialog)), WAIT),
            "the editor never finished loading its icons");

        return dialog;
    }

    private static boolean loaded(JDialog dialog)
    {
        for (JLabel label : Components.all(dialog, JLabel.class, l -> true))
        {
            if (label.getText() != null && label.getText().contains(I18n.t("loc.ui.loadingMessage"))) return false;
        }

        return true;
    }

    /** The icon list of the editor: the combo box whose entries are pictures. */
    private static JComboBox<?> iconsIn(JDialog dialog)
    {
        return Components.find(dialog, JComboBox.class, c -> c.getItemCount() > 50);
    }

    /** The trigger list: the combo box that starts with Toggle. */
    private static JComboBox<?> triggerIn(JDialog dialog)
    {
        return Components.find(dialog, JComboBox.class, c -> c.getItemCount() > 0
            && I18n.t("function.ui.toggle").equals(c.getItemAt(0)));
    }

    /** The label that shows the chosen picture, beside the buttons Use Custom Icon and Clear Custom Icon. */
    private static JLabel customIconIn(JDialog dialog)
    {
        return Components.find(dialog, JLabel.class, l -> l.getText() == null || l.getText().isEmpty());
    }

    /** Presses Use Custom Icon and picks this file in the chooser it opens. */
    private static void pickACustomIcon(JDialog dialog, File picture) throws Exception
    {
        AbstractButton use = Components.withText(dialog, I18n.t("loc.ui.useCustomIcon"));

        assertNotNull(use, "the editor has no Use Custom Icon");

        Components.press(use);

        JFileChooser[] chooser = new JFileChooser[1];

        assertTrue(Components.waitFor(() -> (chooser[0] = chooserShowing()) != null, WAIT), "Use Custom Icon opened no chooser");

        SwingUtilities.invokeLater(() ->
        {
            chooser[0].setSelectedFile(picture);
            chooser[0].approveSelection();
        });

        assertTrue(Components.waitFor(() -> chooserShowing() == null, WAIT), "the chooser did not close once a picture was picked");

        Components.settle();
    }

    private static JFileChooser chooserShowing()
    {
        return Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (!(w instanceof JDialog) || !w.isShowing()) continue;

                JFileChooser found = Components.find(w, JFileChooser.class, null);

                if (found != null) return found;
            }

            return null;
        });
    }

    private static JDialog dialogShowing(Predicate<JDialog> which)
    {
        JDialog[] found = new JDialog[1];

        Components.waitFor(() -> (found[0] = Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing() && which.test((JDialog) w)) return (JDialog) w;
            }

            return null;
        })) != null, WAIT);

        return found[0];
    }

    /** The question whose message is exactly this. */
    private static JDialog questionShowing(String message)
    {
        return dialogShowing(d ->
        {
            JOptionPane pane = Components.find(d, JOptionPane.class, null);

            return pane != null && message.equals(pane.getMessage());
        });
    }

    private static void assertClosed(JDialog dialog, String message)
    {
        assertTrue(Components.waitFor(() -> !dialog.isShowing(), WAIT), message);
    }

    /** Presses the button of a dialog that says this. */
    private static void press(JDialog dialog, String text)
    {
        AbstractButton b = Components.find(dialog, JButton.class, x -> text.equals(x.getText()));

        assertNotNull(b, "the dialog has no \"" + text + "\" button");

        Components.press(b);

        Components.settle();
    }

    /** Whatever is open is closed, as a stray window would be by the operator. */
    private static void closeEverythingOpen()
    {
        Mouse.closeMenus();

        Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing()) w.dispose();
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

    private static File picture(String name, Color color) throws Exception
    {
        BufferedImage drawn = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = drawn.createGraphics();

        g.setColor(color);
        g.fillRect(0, 0, 40, 40);
        g.dispose();

        File file = new File(folder, name);

        assertTrue(javax.imageio.ImageIO.write(drawn, "png", file), "precondition: the picture was not written");

        return file;
    }

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
