package ui;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
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
 * The Locomotive Settings window as the operator uses it (FR-048): the doors that open it, what Save and Cancel do, the
 * refusals Save asks and their order, delete behind its question, Find Similar on the Notes tab, the multi-unit tab,
 * and the window that is reused.  Each claim acts through a real menu, key, button or field and asserts what the
 * locomotive then holds or what was said.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 */
public class testTheLocomotiveSettingsWindow
{
    private static final String NAME = "LS train";
    private static final String OTHER = "LS other";
    private static final String THIRD = "LS third";
    private static final long WAIT = 8000;

    private static TrainControlUI ui;
    private static MarklinControlStation model;
    private static MarklinLocomotive loc;
    private static MarklinLocomotive other;
    private static MarklinLocomotive third;
    private static JButton key;
    private static support.SettingsFixture fixture;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        fixture = support.SettingsFixture.open();

        model = fixture.model;
        ui = fixture.ui;
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (fixture != null) fixture.finish(NAME, OTHER, THIRD, "LS renamed");
        }
        finally
        {
            if (fixture != null) fixture.closeSandbox();
        }
    }

    /** Three trains as they were made, the first on key A, and no window open. */
    @BeforeMethod
    public void lay() throws Exception
    {
        fixture.quiet();

        LocomotiveSettingsFrame.unsavedAnswerForTest = null;

        fixture.forget(NAME, OTHER, THIRD, "LS renamed");

        SettingsWindow.power(ui, model, false);

        loc = model.newMFXLocomotive(NAME, 3401);
        other = model.newMFXLocomotive(OTHER, 3402);
        third = model.newMFXLocomotive(THIRD, 3403);

        loc.setStructuredNotes(1950, 1970, "LS Railway", "first notes");
        loc.setPreferredSpeed(30);

        key = SettingsWindow.showOnKeyA(ui, loc);
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        LocomotiveSettingsFrame.unsavedAnswerForTest = null;

        fixture.quiet();
    }

    // The doors

    /**
     * The key's menu opens the window on its first tab with the locomotive's name, decoder, address and preset speed,
     * and has neither the Manage Locomotive submenu nor any of the entries that moved into the window.
     *
     * MUTATION: put the Manage Locomotive submenu back in `RightClickMenuListener`, or drop `settings(...)` from it,
     * and this fails.
     */
    @Test
    public void testTheKeyMenuOpensTheWindowAndHoldsOnlyWhatStays() throws Exception
    {
        JPopupMenu menu = SettingsWindow.rightClickKey(key);

        assertNotNull(menu, "the key opened no menu");

        for (String gone : new String[] { "loc.ui.submenuManageLocomotive", "loc.ui.menuEditNameAddressDecoder",
            "loc.ui.menuEditNotes", "loc.ui.menuFindSimilarLocomotives", "loc.ui.menuDeleteFromDatabase",
            "loc.ui.menuSetLocalLocomotiveIcon", "loc.ui.menuCustomizeFunctionIcons" })
        {
            assertNull(Components.withText(menu, I18n.t(gone)), "the key's menu still offers \"" + I18n.t(gone) + "\"");
        }

        for (String stays : new String[] { "loc.ui.menuAssignLocomotive", "loc.ui.menuApplySavedFunctionPreset",
            "loc.ui.menuSaveFunctionsAsPreset", "loc.ui.menuSaveSpeedAsPreset", "loc.ui.menuTurnOffFunctions",
            "loc.ui.menuSyncCentralStation", "loc.ui.menuSetAsMultiUnit", "loc.ui.menuClearButtonCut",
            "loc.ui.menuClearButton" })
        {
            assertNotNull(Components.withText(menu, I18n.t(stays)), "the key's menu lost \"" + I18n.t(stays) + "\"");
        }

        assertNotNull(Components.withText(menu, I18n.f("loc.ui.menuApplySavedSpeedPreset", 30)),
            "the key's menu lost the speed preset");

        AbstractButton settings = Components.withText(menu, I18n.t("loc.ui.menuLocomotiveSettings"));

        assertNotNull(settings, "the key's menu has no Locomotive Settings...");
        assertTrue(Components.onTheEventThread(settings::isEnabled), "Locomotive Settings... is grayed for a train at rest");

        Mouse.choose((JMenuItem) settings);

        LocomotiveSettingsFrame window = SettingsWindow.awaited();

        assertNotNull(window, "Locomotive Settings... opened no window");
        assertEquals(Components.onTheEventThread(window::getTitle), SettingsWindow.titleFor(NAME));
        assertEquals(SettingsWindow.tabShowing(), 0, "the window did not open on the Locomotive tab");

        assertEquals(SettingsWindow.text("settings.name"), NAME);
        assertEquals(SettingsWindow.text("settings.address"), "3401");
        assertEquals(SettingsWindow.part("settings.decoder", JComboBox.class).getSelectedItem(), loc.getDecoderType());
        assertEquals(((Number) SettingsWindow.part("settings.speed", JSpinner.class).getValue()).intValue(), 30);
    }

    /**
     * A right-click on the active locomotive's name opens the window; a left-click still opens the locomotive database.
     *
     * MUTATION: open the database for both buttons in `ActiveLocLabelMouseReleased`, or open the window for both, and
     * this fails.
     */
    @Test
    public void testARightClickOnTheNameOpensTheWindowAndALeftClickTheDatabase() throws Exception
    {
        Component name = (Component) field("ActiveLocLabel");

        Mouse.click(name);
        Components.settle();

        assertNull(SettingsWindow.showing(), "a left click on the name opened the settings window");

        Mouse.rightClick(name, 1000);

        assertNotNull(SettingsWindow.awaited(), "a right click on the name opened no window");
        assertEquals(Components.onTheEventThread(SettingsWindow.showing()::getLocomotive), loc);
    }

    /**
     * The database tile's menu is its header, Assign, Locomotive Settings... and Delete, and the settings entry opens
     * the window for that tile's locomotive.
     *
     * MUTATION: keep Edit Notes in `RightClickSelectorMenu`, or aim Locomotive Settings... at the active locomotive,
     * and this fails.
     */
    @Test
    public void testTheDatabaseTilesMenuOpensTheWindowForItsLocomotive() throws Exception
    {
        final JPopupMenu[] menu = new JPopupMenu[1];

        SwingUtilities.invokeAndWait(() -> menu[0] = new RightClickSelectorMenu(ui, null, other));

        for (String gone : new String[] { "loc.ui.menuEditNotes", "loc.ui.menuFindSimilarLocomotives",
            "loc.ui.menuEditNameAddressDecoder", "loc.ui.menuSetAsMultiUnit", "loc.ui.menuCustomizeFunctionIcons" })
        {
            assertNull(Components.withText(menu[0], I18n.t(gone)), "the tile's menu still offers \"" + I18n.t(gone) + "\"");
        }

        assertNotNull(Components.withText(menu[0], I18n.t("loc.ui.menuDeleteFromDatabase")), "the tile's menu lost Delete");

        AbstractButton settings = Components.withText(menu[0], I18n.t("loc.ui.menuLocomotiveSettings"));

        assertNotNull(settings, "the tile's menu has no Locomotive Settings...");

        Components.onTheEventThread(() ->
        {
            settings.doClick(0);

            return null;
        });

        LocomotiveSettingsFrame window = SettingsWindow.awaited();

        assertNotNull(window, "the tile's Locomotive Settings... opened no window");
        assertEquals(Components.onTheEventThread(window::getLocomotive), other, "the window is for another locomotive");
    }

    /**
     * Set as Multi-Unit... opens the window on the Multi-Unit tab.
     *
     * MUTATION: open the Locomotive tab from `multiUnit(...)` in `LocomotiveMenuItems`, and this fails.
     */
    @Test
    public void testSetAsMultiUnitOpensTheMultiUnitTab() throws Exception
    {
        JPopupMenu menu = SettingsWindow.rightClickKey(key);

        Mouse.choose((JMenuItem) Components.withText(menu, I18n.t("loc.ui.menuSetAsMultiUnit")));

        assertNotNull(SettingsWindow.awaited(), "Set as Multi-Unit... opened no window");
        assertEquals(SettingsWindow.tabShowing(), 3, "Set as Multi-Unit... did not open the Multi-Unit tab");
    }

    /**
     * A right-click on a function button opens the window on the Functions tab with that function selected, with no
     * menu in between; a second one on another function goes to that function in the same window and keeps what was
     * typed.
     *
     * MUTATION: show the old menu in `EditFunction`, or open a new window each time, and this fails.
     */
    @Test
    public void testAFunctionButtonOpensTheFunctionsTabOnThatFunction() throws Exception
    {
        javax.swing.JToggleButton f3 = SettingsWindow.functionButton(ui, 3);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> f3.isEnabled() && f3.isShowing()), WAIT),
            "precondition: the function buttons did not come up");

        assertNull(Mouse.rightClick(f3, 1500), "a right click on a function button opened a menu");

        LocomotiveSettingsFrame first = SettingsWindow.awaited();

        assertNotNull(first, "a right click on F3 opened no window");
        assertEquals(SettingsWindow.tabShowing(), 1, "a right click on a function button did not open the Functions tab");
        assertEquals(selectedFunction(), 3, "the Functions tab is not on F3");

        SettingsWindow.type("settings.name", "typed and kept");

        Mouse.rightClick(SettingsWindow.functionButton(ui, 5), 1500);
        Components.settle();

        assertSame(SettingsWindow.showing(), first, "a second right click opened another window");
        assertEquals(selectedFunction(), 5, "the Functions tab did not go to F5");
        assertEquals(SettingsWindow.text("settings.name"), "typed and kept", "re-aiming the window lost what was typed");
    }

    // Save and Cancel

    /**
     * Save writes the name, address, decoder, preset speed and notes together and closes the window; Cancel writes
     * nothing; and Save is gray until something has changed.
     *
     * MUTATION: write nothing for the notes, or leave Save enabled, and this fails.
     */
    @Test
    public void testSaveWritesAndCancelDoesNot() throws Exception
    {
        open(0);

        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save is lit with nothing changed");

        SettingsWindow.type("settings.name", "LS renamed");
        SettingsWindow.type("settings.address", "3411");
        speed(55);

        assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save stayed gray after typing");

        SettingsWindow.selectTab(2);
        SettingsWindow.type("notes.startYear", "1980");
        SettingsWindow.type("notes.railway", "LS New Railway");

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Cancel did not close the window");
        assertEquals(loc.getName(), NAME, "Cancel renamed the locomotive");
        assertEquals(loc.getAddress(), 3401, "Cancel re-addressed the locomotive");
        assertEquals(loc.getPreferredSpeed(), 30, "Cancel changed the preset speed");
        assertEquals(loc.getStructuredNotes().getRailway(), "LS Railway", "Cancel changed the notes");

        open(0);

        SettingsWindow.type("settings.name", "LS renamed");
        SettingsWindow.type("settings.address", "3411");
        speed(55);
        SettingsWindow.selectTab(2);
        SettingsWindow.type("notes.startYear", "1980");
        SettingsWindow.type("notes.railway", "LS New Railway");
        typeArea("notes.text", "second notes");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Save did not close the window");

        assertNotNull(model.getLocByName("LS renamed"), "Save did not rename the locomotive");
        assertEquals(loc.getAddress(), 3411, "Save did not re-address the locomotive");
        assertEquals(loc.getPreferredSpeed(), 55, "Save did not keep the preset speed");
        assertEquals(loc.getStructuredNotes().getStartYear(), 1980);
        assertEquals(loc.getStructuredNotes().getRailway(), "LS New Railway");
        assertEquals(loc.getStructuredNotes().getNotes(), "second notes");
    }

    // The refusals, and their order

    /**
     * The name is asked before the address is written (OB-301): a name that is taken, with a new address beside it, is
     * refused and leaves the address as it was, and the window stays open with what was typed.
     *
     * MUTATION: write the address before the name is checked in `LocomotiveActions.writeNameAndAddress`, and this
     * fails.
     */
    @Test
    public void testATakenNameIsRefusedBeforeTheAddressIsWritten() throws Exception
    {
        open(0);

        SettingsWindow.type("settings.name", OTHER);
        SettingsWindow.type("settings.address", "3499");

        List<String> said = saveAndAnswer();

        assertEquals(said, Arrays.asList(I18n.f("loc.ui.errorLocomotiveAlreadyExists", OTHER)),
            "Save did not refuse a taken name, once");
        assertEquals(loc.getAddress(), 3401, "the address was written though the name was refused (OB-301)");
        assertEquals(loc.getName(), NAME);
        assertNotNull(SettingsWindow.showing(), "a refused Save closed the window");
        assertEquals(SettingsWindow.text("settings.address"), "3499", "a refused Save lost what was typed");
    }

    /**
     * An empty name, a name with a comma and a name that is too long are each refused with today's sentence.
     *
     * MUTATION: drop the comma test from `writeNameAndAddress`, and this fails.
     */
    @Test
    public void testAnEmptyAndAnUnusableAndALongNameAreRefused() throws Exception
    {
        open(0);

        String[][] cases = {
            { "  ", I18n.t("loc.ui.errorEmptyLocomotiveName") },
            { "a, b", I18n.t("loc.ui.errorLocomotiveNameUnusable") },
            { "x" + new String(new char[TrainControlUI.MAX_LOC_NAME_DATABASE]).replace('\0', 'y'),
                I18n.f("loc.ui.errorLocomotiveNameTooLong", TrainControlUI.MAX_LOC_NAME_DATABASE) } };

        for (String[] c : cases)
        {
            SettingsWindow.type("settings.name", c[0]);

            assertEquals(saveAndAnswer(), Arrays.asList(c[1]), "the wrong refusal for the name \"" + c[0] + "\"");
            assertEquals(loc.getName(), NAME, "a refused name was written");
        }
    }

    /**
     * An address that is not a number is refused, and nothing else typed (the preset speed, the notes) is written.
     *
     * MUTATION: write the preset speed before the address in `save`, and this fails.
     */
    @Test
    public void testABadAddressRefusesTheWholeSave() throws Exception
    {
        open(0);

        SettingsWindow.type("settings.address", "not a number");
        speed(77);
        SettingsWindow.selectTab(2);
        SettingsWindow.type("notes.railway", "never written");

        List<String> said = saveAndAnswer();

        assertEquals(said.size(), 1, "a bad address was refused " + said.size() + " times: " + said);
        assertEquals(loc.getAddress(), 3401);
        assertEquals(loc.getPreferredSpeed(), 30, "the speed was written though the address was refused");
        assertEquals(loc.getStructuredNotes().getRailway(), "LS Railway", "the notes were written though the address was refused");
        assertNotNull(SettingsWindow.showing(), "a refused Save closed the window");
    }

    /**
     * A year that is not a number is refused on the Notes tab, which comes forward, and nothing is written.
     *
     * MUTATION: skip `checkYears` in `save`, and this fails.
     */
    @Test
    public void testAYearThatIsNotANumberIsRefusedOnTheNotesTab() throws Exception
    {
        open(0);

        speed(66);
        SettingsWindow.selectTab(2);
        SettingsWindow.type("notes.startYear", "19x");
        SettingsWindow.selectTab(0);

        assertEquals(saveAndAnswer(), Arrays.asList(I18n.t("loc.ui.errorStartEndYearMustBeIntegers")));
        assertEquals(SettingsWindow.tabShowing(), 2, "the Notes tab did not come forward");
        assertEquals(loc.getPreferredSpeed(), 30, "the speed was written though a year was refused");
    }

    // Delete

    /**
     * The trash can asks today's question: No leaves the locomotive and the window; Yes deletes it and closes the
     * window.
     *
     * MUTATION: delete without asking in the trash can's handler, and this fails.
     */
    @Test
    public void testTheTrashCanAsksAndYesDeletes() throws Exception
    {
        open(0);

        JButton trash = SettingsWindow.part("settings.trash", JButton.class);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            Components.press(trash);

            assertTrue(answering.awaitAsked(1, WAIT), "the trash can asked nothing");
            assertEquals(answering.asked(), Arrays.asList(I18n.f("ui.confirmDeleteFromDatabase", NAME)));
        }

        Components.settle();

        assertNotNull(model.getLocByName(NAME), "No on the question deleted the locomotive");
        assertNotNull(SettingsWindow.showing(), "No on the question closed the window");

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            Components.press(trash);

            assertTrue(answering.awaitAsked(1, WAIT), "the trash can asked nothing the second time");
        }

        assertTrue(Components.waitFor(() -> model.getLocByName(NAME) == null, WAIT), "Yes did not delete the locomotive");
        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "the window stayed open on a deleted locomotive");
    }

    // The Notes tab

    /**
     * Find Similar Locomotives is on the Notes tab, and finds the locomotives of the same railway and years.
     *
     * MUTATION: leave the button off the Notes tab, or search from another locomotive, and this fails.
     */
    @Test
    public void testFindSimilarIsOnTheNotesTab() throws Exception
    {
        other.setStructuredNotes(1960, 1980, "LS Railway", "");

        open(2);

        AbstractButton find = SettingsWindow.part("settings.findSimilar", JButton.class);

        assertNotNull(find, "the Notes tab has no Find Similar Locomotives");

        String similar = I18n.t("search.ui.labelSimilarLocomotives");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(find);

            assertTrue(answering.awaitAsked(2, WAIT), "Find Similar did not ask and answer: " + answering.asked());
            assertTrue(answering.asked().get(1).contains(OTHER), "the results do not name the train of the same railway: "
                + answering.asked().get(1));
            assertTrue(answering.asked().get(1).contains(similar));
        }
    }

    // The Multi-Unit tab

    /**
     * Ticking a locomotive on the Multi-Unit tab and pressing Save makes it a member; the member's own tab says it is
     * part of the unit instead of offering a list.
     *
     * MUTATION: leave out `applyLinkedLocomotives` in `save`, and this fails.
     */
    @Test
    public void testTheMultiUnitTabSavesItsMembers() throws Exception
    {
        open(3);

        SettingsWindow.tickMember(OTHER);

        assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save stayed gray after ticking a member");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getLinkedLocomotiveNames().containsKey(OTHER), WAIT),
            "Save did not make " + OTHER + " a member");

        SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null);

        assertTrue(Components.all(SettingsWindow.showing().getContentPane(), JLabel.class,
            l -> l.getText() != null && l.getText().equals(I18n.f("loc.ui.settingsPartOfMultiUnit", OTHER, NAME))).size() == 1,
            "a member's Multi-Unit tab does not say it is part of " + NAME);
    }

    // The window that is reused

    /**
     * Opening the window for another locomotive while typing is unsaved asks: Cancel stays where it was, Discard goes
     * on and loses the typing, Save writes it first.
     *
     * MUTATION: skip `settleUnsaved` in `aim`, and this fails.
     */
    @Test
    public void testAnotherLocomotiveAsksAboutTypingNotSaved() throws Exception
    {
        open(0);

        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsFrame.unsavedAnswerForTest = 2;

        assertFalse(SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "Cancel went on to open it");
        assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel lost the typing");

        LocomotiveSettingsFrame.unsavedAnswerForTest = 1;

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null));
        assertEquals(SettingsWindow.text("settings.name"), OTHER, "Discard did not go on to the other locomotive");
        assertEquals(loc.getName(), NAME, "Discard wrote the typing");

        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsFrame.unsavedAnswerForTest = 0;

        assertTrue(SettingsWindow.open(ui, third, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null));
        assertNotNull(model.getLocByName("LS renamed"), "Save did not write the typing before moving on");
        assertEquals(SettingsWindow.text("settings.name"), THIRD);
    }

    /**
     * The application's exit asks about unsaved typing in the window.
     *
     * MUTATION: drop the window from `EditorWindows.everyOpenWindowMaySettle`, and this fails.
     */
    @Test
    public void testTheExitAsksAboutUnsavedTyping() throws Exception
    {
        open(0);

        java.lang.reflect.Method asks = Class.forName("org.traincontrol.gui.EditorWindows")
            .getDeclaredMethod("everyOpenWindowMaySettle");

        asks.setAccessible(true);

        java.lang.reflect.Field editors = TrainControlUI.class.getDeclaredField("editorWindows");

        editors.setAccessible(true);

        Object windows = editors.get(ui);

        assertTrue((Boolean) Components.onTheEventThread(() -> invoke(asks, windows)), "the exit asked about a window with nothing typed");

        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsFrame.unsavedAnswerForTest = 2;

        assertFalse((Boolean) Components.onTheEventThread(() -> invoke(asks, windows)),
            "the exit went on though the window holds unsaved typing and the operator canceled");
    }

    // Helpers

    private static Object invoke(java.lang.reflect.Method m, Object on)
    {
        try
        {
            return m.invoke(on);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static Object field(String name) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }

    /** Opens the window on a tab of the first train. */
    private static void open(int tab) throws Exception
    {
        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.values()[tab], null), "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
    }



    private static void typeArea(String name, String value)
    {
        Components.onTheEventThread(() ->
        {
            SettingsWindow.part(name, JTextArea.class).setText(value);

            return null;
        });
    }

    private static void speed(int value)
    {
        Components.onTheEventThread(() ->
        {
            SettingsWindow.part("settings.speed", JSpinner.class).setValue(value);

            return null;
        });
    }

    private static int selectedFunction()
    {
        return Components.onTheEventThread(() -> SettingsWindow.part("functions.table", javax.swing.JTable.class).getSelectedRow());
    }

    /** Presses Save, answers whatever it asks with its first button, and gives what was asked. */
    private static List<String> saveAndAnswer() throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(SettingsWindow.save());

            assertTrue(answering.awaitAsked(1, WAIT), "Save said nothing");

            Components.settle();

            return answering.asked();
        }
    }
}
