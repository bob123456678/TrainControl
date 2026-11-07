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
import org.traincontrol.base.Locomotive;
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
            if (fixture != null) fixture.finish(NAME, OTHER, THIRD, "LS renamed", "LS elsewhere");
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

        fixture.forget(NAME, OTHER, THIRD, "LS renamed", "LS elsewhere");

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

        for (String gone : new String[] { "loc.ui.menuFindSimilarLocomotives", "loc.ui.menuDeleteFromDatabase",
            "loc.ui.menuSetLocalLocomotiveIcon" })
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

        for (String gone : new String[] { "loc.ui.menuFindSimilarLocomotives", "loc.ui.menuSetAsMultiUnit" })
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
     * A typed address that is not a number, or too long for one, says what Add Locomotive says, and the locomotive is
     * left as it was.
     *
     * MUTATION: drop the NumberFormatException catch around `Conversion.parseTypedAddress` in
     * `LocomotiveActions.writeNameAndAddress`, and this fails with `Error: For input string`.
     */
    @Test
    public void testAnAddressThatIsNotANumberSaysSo() throws Exception
    {
        for (String typed : new String[] { "abc", "99999999999999" })
        {
            open(0);

            SettingsWindow.type("settings.address", typed);

            assertEquals(saveAndAnswer(), Arrays.asList(I18n.t("loc.errorAddressNotNumeric")),
                "the wrong message for the address \"" + typed + "\"");
            assertEquals(loc.getAddress(), 3401, "a refused address was written");
            assertEquals(loc.getName(), NAME);
            assertNotNull(SettingsWindow.showing(), "a refused Save closed the window");

            SettingsWindow.closeIfOpen();
        }
    }

    /**
     * The address box takes digits, A to F and x only, and at most 6 characters, as Add Locomotive's does.
     *
     * MUTATION: drop the key listener on `LocomotiveTab.address`, and this fails.
     */
    @Test
    public void testTheAddressBoxFiltersAndCapsWhatIsTyped() throws Exception
    {
        open(0);

        JTextField box = SettingsWindow.part("settings.address", JTextField.class);

        SettingsWindow.type("settings.address", "12g-ab!");
        support.Keys.press(box, java.awt.event.KeyEvent.VK_B);

        assertEquals(SettingsWindow.text("settings.address"), "12ab", "the box kept characters no address has");

        SettingsWindow.type("settings.address", "1234567890");
        support.Keys.press(box, java.awt.event.KeyEvent.VK_0);

        assertEquals(SettingsWindow.text("settings.address"), "123456", "the box took more than 6 characters");
    }

    /**
     * The decoder box shows each type's user label: no raw enum name such as MULTI_UNIT.
     *
     * MUTATION: remove the renderer from `LocomotiveTab.decoder`, and this fails.
     */
    @Test
    public void testTheDecoderBoxShowsNoRawEnumNames() throws Exception
    {
        open(0);

        @SuppressWarnings("unchecked")
        JComboBox<Object> box = SettingsWindow.part("settings.decoder", JComboBox.class);

        List<String> shown = Components.onTheEventThread(() ->
        {
            List<String> out = new java.util.ArrayList<>();

            for (int i = 0; i < box.getItemCount(); i++)
            {
                java.awt.Component c = box.getRenderer().getListCellRendererComponent(new javax.swing.JList<>(), box.getItemAt(i), i,
                    false, false);

                out.add(((JLabel) c).getText());
            }

            return out;
        });

        assertEquals(shown, Arrays.asList("MFX", "MM2", "DCC", I18n.t("loc.multiUnit")),
            "the decoder box shows " + shown);
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

    /** How many Locomotive Settings windows are open. */
    private static long windowsOpen()
    {
        return Components.onTheEventThread(() -> java.util.Arrays.stream(java.awt.Window.getWindows())
            .filter(w -> w instanceof LocomotiveSettingsFrame && w.isDisplayable()).count());
    }

    /**
     * There is only ever one Locomotive Settings window: a second locomotive, opened from another door, and a third,
     * show in the same window; and when it was closed, the next door makes one new window.
     *
     * MUTATION: always construct a new frame in `TrainControlUI.openLocomotiveSettings`, and this fails.
     */
    @Test
    public void testOnlyOneLocomotiveSettingsWindowIsEverOpen() throws Exception
    {
        open(0);

        assertEquals(windowsOpen(), 1L, "the first door made " + windowsOpen() + " windows");

        LocomotiveSettingsFrame first = SettingsWindow.showing();

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.NOTES, null), "the second door did not open");
        assertEquals(windowsOpen(), 1L, "a second locomotive made another window");
        assertSame(SettingsWindow.showing(), first, "a second locomotive opened a different window");
        assertSame(Components.onTheEventThread(first::getLocomotive), other, "the window does not show the second locomotive");

        SettingsWindow.openFromTheTile(ui, third);

        assertEquals(windowsOpen(), 1L, "the database's door made another window");
        assertSame(Components.onTheEventThread(first::getLocomotive), third, "the window does not show the third locomotive");

        SettingsWindow.press(SettingsWindow.cancel());

        assertEquals(windowsOpen(), 0L, "Cancel left a window open");

        open(0);

        assertEquals(windowsOpen(), 1L, "opening after a close made " + windowsOpen() + " windows");
        assertSame(Components.onTheEventThread(SettingsWindow.showing()::getLocomotive), loc, "the new window shows another locomotive");
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

    // The validators' findings

    /**
     * A decoder change with a function edited in the same Save goes through: the new decoder has fewer functions than
     * the table, and the rows past them are skipped.
     *
     * MUTATION: drop the `f >= preset.length` skip in `LocomotiveFunctionsTab.apply`, and this fails.
     */
    @Test
    public void testADecoderChangeWithAFunctionEditSavesEverything() throws Exception
    {
        assertTrue(loc.getNumF() > 20, "precondition: the MFX locomotive has few functions");

        open(1);

        SettingsWindow.pickIcon(3, 12);
        SettingsWindow.pickIcon(25, 12);

        SettingsWindow.selectTab(0);

        decoder(Locomotive.decoderType.MM2);

        SettingsWindow.type("settings.address", "40");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Save did not go through and close the window");
        assertEquals(loc.getDecoderType(), Locomotive.decoderType.MM2);
        assertEquals(loc.getAddress(), 40);
        assertEquals(loc.getFunctionType(3), 12, "the function edit was not written");
    }

    /**
     * A name-only Save does not put back the address it was opened with, and an address-only Save does not put back
     * the name, when another door changed them while the window was open.
     *
     * MUTATION: pass the typed name and address whenever either changed, in `LocomotiveSettingsFrame.write`, and this
     * fails.
     */
    @Test
    public void testASaveWritesOnlyWhatWasChanged() throws Exception
    {
        open(0);

        model.changeLocAddress(NAME, 3450, Locomotive.decoderType.MFX);

        SettingsWindow.type("settings.name", "LS renamed");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT));
        assertNotNull(model.getLocByName("LS renamed"), "the name was not written");
        assertEquals(loc.getAddress(), 3450, "a name-only Save put the old address back");

        open(0);

        model.renameLoc("LS renamed", "LS elsewhere");

        SettingsWindow.type("settings.address", "3460");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT));
        assertEquals(loc.getAddress(), 3460, "the address was not written");
        assertNotNull(model.getLocByName("LS elsewhere"), "an address-only Save put the old name back");
    }

    /**
     * A decoder-only Save keeps the address another door changed meanwhile: the write is given the locomotive's own
     * address, not the typed one.
     *
     * MUTATION: give `writeNameAndAddress` the typed address for a decoder change, in `LocomotiveTab.addressText`, and
     * this fails.
     */
    @Test
    public void testADecoderOnlySaveKeepsAnAddressASyncChanged() throws Exception
    {
        open(0);

        model.changeLocAddress(NAME, 1500, Locomotive.decoderType.MFX);

        decoder(Locomotive.decoderType.DCC);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "the decoder-only Save did not go through");
        assertEquals(loc.getDecoderType(), Locomotive.decoderType.DCC);
        assertEquals(loc.getAddress(), 1500, "the decoder-only Save put the old typed address back");
    }

    /**
     * With the power off and a stored speed, a change of address or decoder is refused at Save, as the old door would
     * refuse it, while a name saves; at speed zero the address saves.
     *
     * MUTATION: leave the stored-speed question out of `LocomotiveActions.writeNameAndAddress`, and this fails.
     */
    @Test
    public void testAStoredSpeedRefusesTheAddressAndTheDecoderButNotTheName() throws Exception
    {
        String stop = I18n.f("loc.ui.stopToEdit", NAME);

        loc.setSpeed(40);

        open(0);

        SettingsWindow.type("settings.address", "3411");

        assertEquals(saveAndAnswer(), Arrays.asList(stop), "an address change was not refused under a stored speed");
        assertEquals(loc.getAddress(), 3401);

        SettingsWindow.type("settings.address", "3401");

        decoder(Locomotive.decoderType.DCC);

        assertEquals(saveAndAnswer(), Arrays.asList(stop), "a decoder change was not refused under a stored speed");
        assertEquals(loc.getDecoderType(), Locomotive.decoderType.MFX);

        decoder(Locomotive.decoderType.MFX);

        SettingsWindow.type("settings.name", "LS renamed");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "a name change was refused under a stored speed");
        assertNotNull(model.getLocByName("LS renamed"));

        model.getLocByName("LS renamed").setSpeed(0);

        SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null);
        SettingsWindow.awaited();
        SettingsWindow.type("settings.address", "3411");
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getAddress() == 3411, WAIT), "an address change was refused at speed zero");
    }

    /**
     * The name and address write asks again whether autonomy runs, for the old dialogs that reach it without the
     * window's rule.
     *
     * MUTATION: leave the autonomy question out of `LocomotiveActions.writeNameAndAddress`, and this fails.
     */
    @Test
    public void testTheNameAndAddressWriteAsksAboutAutonomy() throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("locomotiveActions");

        field.setAccessible(true);

        Object actions = field.get(ui);

        java.lang.reflect.Method write = actions.getClass().getDeclaredMethod("writeNameAndAddress", Component.class,
            Locomotive.class, String.class, String.class, Locomotive.decoderType.class);

        write.setAccessible(true);

        model.getAutoLayout().setStagingInProgress(true);

        try
        {
            assertEquals(SettingsWindow.asked(() -> Components.onTheEventThread(() ->
            {
                try
                {
                    return write.invoke(actions, ui, loc, "LS renamed", "", Locomotive.decoderType.MFX);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            })), Arrays.asList(I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")));

            assertEquals(loc.getName(), NAME, "the write renamed a locomotive while autonomy runs");
        }
        finally
        {
            model.getAutoLayout().setStagingInProgress(false);
        }
    }

    /**
     * The X and Escape ask what to do with typing that is not saved; Cancel stays, Discard closes and writes nothing,
     * Save writes; with nothing typed they close without asking.
     *
     * MUTATION: call `close()` instead of `closeAsking()` from the X or Escape, and this fails.
     */
    @Test
    public void testTheXAndEscapeAskAboutTypingNotSaved() throws Exception
    {
        open(0);

        LocomotiveSettingsFrame.unsavedAnswerForTest = 2;

        assertTrue(closeBy(true), "a window with nothing typed did not just close");

        open(0);

        SettingsWindow.type("settings.name", "LS renamed");

        for (boolean byEscape : new boolean[] { true, false })
        {
            LocomotiveSettingsFrame.unsavedAnswerForTest = 2;

            assertFalse(closeBy(byEscape), "Cancel on the question closed the window");
            assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel lost the typing");
        }

        LocomotiveSettingsFrame.unsavedAnswerForTest = 1;

        assertTrue(closeBy(false), "Discard did not close the window");
        assertEquals(loc.getName(), NAME, "Discard wrote the typing");

        open(0);

        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsFrame.unsavedAnswerForTest = 0;

        assertTrue(closeBy(true), "Save on the question did not close the window");
        assertNotNull(model.getLocByName("LS renamed"), "Save on the question did not write the typing");
    }

    /** Closes by Escape or by the X, and says whether the window went. */
    private static boolean closeBy(boolean escape) throws Exception
    {
        LocomotiveSettingsFrame window = SettingsWindow.showing();

        Components.onTheEventThread(() ->
        {
            if (escape)
            {
                javax.swing.JRootPane root = window.getRootPane();

                root.getActionForKeyStroke(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0))
                    .actionPerformed(new java.awt.event.ActionEvent(root, java.awt.event.ActionEvent.ACTION_PERFORMED, "escape"));
            }
            else
            {
                window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING));
            }

            return null;
        });

        Components.settle();

        return SettingsWindow.showing() == null;
    }

    /**
     * The picture's marks open their chooser over this window, not over the main window.
     *
     * MUTATION: call `main.setLocIcon(loc)` with no mouse event in the wrench's handler, and this fails.
     */
    @Test
    public void testThePicturesChooserHangsOffTheSettingsWindow() throws Exception
    {
        open(0);

        LocomotiveSettingsFrame window = SettingsWindow.showing();

        Mouse.click(SettingsWindow.part("settings.wrench", Component.class));

        final java.awt.Window[] owner = new java.awt.Window[1];
        final javax.swing.JFileChooser[] chooser = new javax.swing.JFileChooser[1];

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() ->
        {
            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (!(w instanceof javax.swing.JDialog) || !w.isShowing()) continue;

                javax.swing.JFileChooser found = Components.inside(((javax.swing.JDialog) w).getContentPane(),
                    javax.swing.JFileChooser.class);

                if (found != null)
                {
                    chooser[0] = found;
                    owner[0] = w.getOwner();

                    return true;
                }
            }

            return false;
        }), WAIT), "the wrench opened no chooser");

        Components.onTheEventThread(() ->
        {
            chooser[0].cancelSelection();

            return null;
        });

        assertSame(owner[0], window, "the chooser hangs off " + owner[0] + ", not the settings window");
    }

    // Helpers

    private static void decoder(Locomotive.decoderType type)
    {
        Components.onTheEventThread(() ->
        {
            SettingsWindow.part("settings.decoder", JComboBox.class).setSelectedItem(type);

            return null;
        });
    }

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
