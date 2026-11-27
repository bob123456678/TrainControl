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
import static org.testng.Assert.assertNotSame;
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
import org.traincontrol.gui.LocomotiveSettingsWindow;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.CurrentKeyRow;
import org.traincontrol.gui.locomotive.LocomotiveSelectorContextMenu;
import org.traincontrol.gui.widgets.HandPointer;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Mouse;
import support.Reflect;
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

        LocomotiveSettingsWindow.unsavedAnswerForTest = null;

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
        LocomotiveSettingsWindow.unsavedAnswerForTest = null;

        fixture.quiet();
    }

    // The doors

    /**
     * The key's menu opens the window on its first tab with the locomotive's name, decoder, address and preset speed,
     * and has neither the Manage Locomotive submenu nor any of the entries that moved into the window.
     *
     * MUTATION: put the Manage Locomotive submenu back in `LocomotiveButtonContextMenu`, or drop `settings(...)` from it,
     * or add the Sync w/ Central Station item back to it, and this fails.
     */
    @Test
    public void testTheKeyMenuOpensTheWindowAndHoldsOnlyWhatStays() throws Exception
    {
        JPopupMenu menu = SettingsWindow.rightClickKey(key);

        assertNotNull(menu, "the key opened no menu");

        for (String gone : new String[] { "loc.ui.menuFindSimilarLocomotives", "loc.ui.menuDeleteFromDatabase",
            "loc.ui.menuSetLocalLocomotiveIcon", "loc.ui.menuSyncCentralStation" })
        {
            assertNull(Components.withText(menu, I18n.t(gone)), "the key's menu still offers \"" + I18n.t(gone) + "\"");
        }

        for (String stays : new String[] { "loc.ui.menuAssignLocomotive", "loc.ui.menuApplySavedFunctionPreset",
            "loc.ui.menuSaveFunctionsAsPreset", "loc.ui.menuSaveSpeedAsPreset", "loc.ui.menuTurnOffFunctions",
            "loc.ui.menuSetAsMultiUnit", "loc.ui.menuClearButtonCut",
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

        LocomotiveSettingsWindow window = SettingsWindow.awaited();

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
     * MUTATION: keep Edit Notes in `LocomotiveSelectorContextMenu`, or aim Locomotive Settings... at the active locomotive,
     * and this fails.
     */
    @Test
    public void testTheDatabaseTilesMenuOpensTheWindowForItsLocomotive() throws Exception
    {
        final JPopupMenu[] menu = new JPopupMenu[1];

        SwingUtilities.invokeAndWait(() -> menu[0] = new LocomotiveSelectorContextMenu(ui, null, other));

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

        LocomotiveSettingsWindow window = SettingsWindow.awaited();

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

        LocomotiveSettingsWindow first = SettingsWindow.awaited();

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

        SettingsWindow.cancelDiscarding();

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
     * refused and leaves the address as it was, and the window stays open with what was typed.  With the other train's
     * own address beside it, OB-301's shape, neither train moves.
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

        SettingsWindow.type("settings.address", "3402");

        assertEquals(saveAndAnswer(), Arrays.asList(I18n.f("loc.ui.errorLocomotiveAlreadyExists", OTHER)),
            "Save did not refuse a taken name beside the other train's address");
        assertEquals(loc.getAddress(), 3401, "the other train's address was written though the name was refused, leaving"
            + " two trains on one address (OB-301)");
        assertEquals(other.getAddress(), 3402, "a refused Save moved the other train");
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
     * Start and End Year each take four digits and nothing else: typed a key at a time, a letter, a minus and a fifth
     * digit are refused; a paste of five digits or of a letter is refused whole, and one of four digits is taken.
     *
     * MUTATION: build the year fields in `LocomotiveNotesPanel` as plain `JTextField`s, and this fails.
     */
    @Test
    public void testTheYearsRefuseAFifthDigitAndNonDigits() throws Exception
    {
        open(2);

        for (String name : new String[] { "notes.startYear", "notes.endYear" })
        {
            List<String> shown = Components.onTheEventThread(() ->
            {
                JTextField year = SettingsWindow.part(name, JTextField.class);
                List<String> out = new java.util.ArrayList<>();

                year.selectAll();
                year.replaceSelection("");

                // A typed key goes in as a replaceSelection at the caret, as the field's own key binding does
                for (char c : "19x5-78".toCharArray()) year.replaceSelection(String.valueOf(c));

                out.add(year.getText());

                for (String paste : new String[] { "123456", "20a1", "2001" })
                {
                    year.selectAll();
                    year.replaceSelection(paste);
                    out.add(year.getText());
                }

                return out;
            });

            assertEquals(shown, Arrays.asList("1957", "1957", "1957", "2001"), name + " took what it should refuse");
        }
    }

    /**
     * A year typed in another script's digits is saved as its value: full-width
     * 1950 in Start Year and Arabic-Indic 1950 in End Year are both saved as 1950.
     *
     * MUTATION: read the years in `LocomotiveNotesPanel.year` with `[^0-9]` stripped, and this fails.
     */
    @Test
    public void testAYearInAnotherScriptsDigitsIsSavedAsItsValue() throws Exception
    {
        open(2);

        Components.onTheEventThread(() ->
        {
            typeDigits(SettingsWindow.part("notes.startYear", JTextField.class), "1950", 0xFF10);
            typeDigits(SettingsWindow.part("notes.endYear", JTextField.class), "1950", 0x0660);

            return null;
        });

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Save did not close the window");
        assertEquals(loc.getStructuredNotes().getStartYear(), 1950, "the full-width year was not saved as 1950");
        assertEquals(loc.getStructuredNotes().getEndYear(), 1950, "the Arabic-Indic year was not saved as 1950");
    }

    /** Clears a year field and types these digits a key at a time, in the script whose zero is at `zero`. */
    private static void typeDigits(JTextField year, String digits, int zero)
    {
        year.selectAll();
        year.replaceSelection("");

        for (char c : digits.toCharArray()) year.replaceSelection(String.valueOf((char) (zero + c - '0')));
    }

    /**
     * A year pasted with whitespace around it, a line break after it or a space on either side, is taken without it.
     *
     * MUTATION: build the year fields in `LocomotiveNotesPanel` as plain `digitsOnlyField`s, without the trimming
     * filter, and this fails.
     */
    @Test
    public void testAPastedYearLosesItsSurroundingWhitespace() throws Exception
    {
        open(2);

        String crlf = String.valueOf((char) 13) + (char) 10;

        for (String paste : new String[] { "1950" + crlf, "1950" + (char) 10, " 1950", "1950 " })
        {
            String shown = Components.onTheEventThread(() ->
            {
                JTextField year = SettingsWindow.part("notes.startYear", JTextField.class);

                year.selectAll();
                year.replaceSelection("");
                year.replaceSelection(paste);

                return year.getText();
            });

            assertEquals(shown, "1950", "a paste of \"" + paste.trim() + "\" with whitespace around it was not taken");
        }
    }

    /**
     * The lowest stored year an import can write, Integer.MIN_VALUE, saves without an error beside an edit of the
     * notes and is kept as it was.
     *
     * MUTATION: parse the year as an int in `LocomotiveNotesPanel.year`, and this fails.
     */
    @Test
    public void testTheLowestStoredYearSavesWithoutAnError() throws Exception
    {
        loc.setStructuredNotes(Integer.MIN_VALUE, 1970, "LS Railway", "first notes");

        open(2);

        typeArea("notes.text", "second notes");
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Save did not go through");
        assertEquals(loc.getStructuredNotes().getStartYear(), Integer.MIN_VALUE, "saving the notes changed the stored year");
        assertEquals(loc.getStructuredNotes().getNotes(), "second notes");
    }

    /**
     * A stored year longer than four digits, as an import may write, is shown whole and does not light Save, and a
     * save of the notes beside it keeps it.
     *
     * MUTATION: show the loaded years through the field's filter (`setText`) in `LocomotiveNotesPanel.reload`, and this
     * fails.
     */
    @Test
    public void testALongStoredYearIsShownWholeAndKept() throws Exception
    {
        loc.setStructuredNotes(12345, 1970, "LS Railway", "first notes");

        open(2);

        assertEquals(SettingsWindow.text("notes.startYear"), "12345", "the stored year is not shown whole");
        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "showing the stored year lit Save");

        typeArea("notes.text", "second notes");
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "Save did not close the window");
        assertEquals(loc.getStructuredNotes().getStartYear(), 12345, "saving the notes changed the stored year");
        assertEquals(loc.getStructuredNotes().getNotes(), "second notes");
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

        SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.MULTI_UNIT, null);

        assertEquals(noteText(), I18n.f("loc.ui.settingsPartOfMultiUnit", OTHER, NAME, NAME),
            "a member's Multi-Unit tab does not say it is part of " + NAME);
    }

    /**
     * Members ticked in the same Save as a decoder change to Multi-Unit are never dropped without a word: Save refuses
     * with the reason a locomotive that already has members gets, writes nothing, and the window stays open.
     *
     * MUTATION: leave out the members check before the writes in `write`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testMembersAndAMultiUnitDecoderInOneSaveAreRefused() throws Exception
    {
        open(3);

        SettingsWindow.tickMember(OTHER);

        decoder(Locomotive.decoderType.MULTI_UNIT);

        List<String> asked = saveAndAnswer();

        assertEquals(asked, Arrays.asList(I18n.t("loc.changeToMultiUnitNotAllowed")),
            "Save did not say why the members and a Multi-Unit decoder cannot go together");
        assertNotNull(SettingsWindow.showing(), "the window closed as if saved");
        assertEquals(loc.getDecoderType(), Locomotive.decoderType.MFX, "the refused Save changed the decoder");
        assertTrue(loc.getLinkedLocomotiveNames().isEmpty(), "the refused Save wrote members");
    }

    /**
     * A member ticked in the same Save as a new address equal to its own is never dropped without a word: Save refuses
     * with the reason a link at the same address gets, writes nothing, and the window stays open.
     *
     * MUTATION: leave out the same-address check before the writes in `write`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAMemberAtTheNewAddressInOneSaveIsRefused() throws Exception
    {
        open(3);

        SettingsWindow.tickMember(OTHER);

        SettingsWindow.type("settings.address", String.valueOf(other.getAddress()));

        List<String> asked = saveAndAnswer();

        assertEquals(asked, Arrays.asList(I18n.f("loc.errorLinkSameAddress", NAME, OTHER)),
            "Save did not say why a member cannot share the new address");
        assertNotNull(SettingsWindow.showing(), "the window closed as if saved");
        assertEquals(loc.getAddress(), 3401, "the refused Save changed the address");
        assertTrue(loc.getLinkedLocomotiveNames().isEmpty(), "the refused Save wrote members");
    }

    /**
     * With the Multi-Unit tab untouched, a new address equal to an existing member's is refused the same way: a
     * consist never ends up with two locomotives on one address.
     *
     * MUTATION: check only the ticked members, not the existing ones when the tab is untouched, and this fails.
     */
    @Test(timeOut = 120000)
    public void testANewAddressEqualToAnExistingMembersIsRefused() throws Exception
    {
        java.util.Map<String, Double> link = new java.util.HashMap<>();

        link.put(OTHER, 1.0);

        assertEquals(loc.setLinkedLocomotives(link), 1, "precondition: the consist was not made");

        open(0);

        SettingsWindow.type("settings.address", String.valueOf(other.getAddress()));

        refusedOverTheConsist("Save did not say why the new address cannot be a member's");
        assertEquals(loc.getAddress(), 3401, "the refused Save changed the address");
        assertTrue(loc.getLinkedLocomotiveNames().containsKey(OTHER), "the refused Save dropped the member");
    }

    /**
     * The mirror: a member given its head's address in its own window is refused the same way, before anything is
     * written, where the Save would drop it from the consist with only a line in the log.
     *
     * MUTATION: drop the head's check after the members' in `LocomotiveSettingsWindow.write`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAMembersNewAddressEqualToItsHeadsIsRefused() throws Exception
    {
        assertEquals(loc.setLinkedLocomotives(java.util.Collections.singletonMap(OTHER, 1.0)), 1,
            "precondition: the consist was not made");

        open(other, 0);

        SettingsWindow.type("settings.address", String.valueOf(loc.getAddress()));

        refusedOverTheConsist("Save did not say why the member cannot take its head's address");
        assertEquals(other.getAddress(), 3402, "the refused Save changed the member's address");
        assertTrue(loc.getLinkedLocomotiveNames().containsKey(OTHER), "the member left the consist");
    }

    /** The Multi-Unit tab's note for a member, as it reads. */
    private static String noteText()
    {
        return Components.onTheEventThread(() -> SettingsWindow.part("settings.multiUnitNote", javax.swing.JTextPane.class).getText());
    }

    /** Makes OTHER a member of NAME and opens OTHER's Multi-Unit tab, which carries the note. */
    private static void openTheMembersNote() throws Exception
    {
        SettingsWindow.open(ui, loc, LocomotiveSettingsWindow.Tab.MULTI_UNIT, null);
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
        SettingsWindow.tickMember(OTHER);
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getLinkedLocomotiveNames().containsKey(OTHER), WAIT),
            "precondition: Save did not make " + OTHER + " a member");

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.MULTI_UNIT, null), "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
    }

    /** A mouse event on the note at the first letter of the multi-unit's name (the link), or off it. */
    private static void mouseOnTheNote(int id, boolean onTheLink)
    {
        boolean moved = id == java.awt.event.MouseEvent.MOUSE_MOVED;

        Components.onTheEventThread(() ->
        {
            javax.swing.JTextPane note = SettingsWindow.part("settings.multiUnitNote", javax.swing.JTextPane.class);

            try
            {
                java.awt.Rectangle r = note.modelToView(onTheLink ? note.getText().lastIndexOf(NAME) + 1 : 0);

                note.dispatchEvent(new java.awt.event.MouseEvent(note, id, System.currentTimeMillis(),
                    moved ? 0 : java.awt.event.InputEvent.BUTTON1_MASK, r.x + 1, r.y + r.height / 2, 1, false,
                    moved ? java.awt.event.MouseEvent.NOBUTTON : java.awt.event.MouseEvent.BUTTON1));
            }
            catch (javax.swing.text.BadLocationException e)
            {
                throw new IllegalStateException(e);
            }

            return null;
        });

        Components.settle();
    }

    /**
     * In every language the multi-unit's name in the member's note is the underlined run, and only it, with a hand over
     * it and a keyboard way in (FR-196).
     *
     * MUTATION: leave the underline off the run in `partOfNote`, or the hand off the link, and this fails.
     */
    @Test
    public void testTheMultiUnitNameInTheNoteIsALinkInEveryLanguage() throws Exception
    {
        try
        {
            for (String lang : new String[] {"en", "de", "da", "es", "fr", "it", "nl", "pl"})
            {
                I18n.setLocale(new java.util.Locale(lang));
                lay();
                openTheMembersNote();

                javax.swing.JTextPane note = SettingsWindow.part("settings.multiUnitNote", javax.swing.JTextPane.class);
                StringBuilder underlined = new StringBuilder();

                Components.onTheEventThread(() ->
                {
                    javax.swing.text.StyledDocument doc = note.getStyledDocument();

                    for (int i = 0; i < doc.getLength(); i++)
                    {
                        if (javax.swing.text.StyleConstants.isUnderline(doc.getCharacterElement(i).getAttributes()))
                        {
                            underlined.append(note.getText().charAt(i));
                        }
                    }

                    return null;
                });

                assertEquals(underlined.toString(), NAME, lang + ": the link is not exactly the multi-unit's name");
                assertTrue(Components.onTheEventThread(note::isFocusable), lang + ": the link cannot be reached by keyboard");

                mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_MOVED, true);
                assertTrue(Components.onTheEventThread(() -> HandPointer.isHand(note.getCursor())),
                    lang + ": no hand over the link");

                mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_MOVED, false);
                assertEquals((int) Components.onTheEventThread(() -> note.getCursor().getType()), java.awt.Cursor.TEXT_CURSOR,
                    lang + ": the hand stays off the link");
            }
        }
        finally
        {
            I18n.setLocale(java.util.Locale.ENGLISH);
        }
    }

    /**
     * Clicking the multi-unit's name in a member's note closes this window and opens the multi-unit's settings on its
     * Multi-Unit tab (FR-196); Enter on the link does the same.
     *
     * MUTATION: leave the open out of `partOfNote`'s action, and this fails.
     */
    @Test
    public void testTheLinkOpensTheMultiUnitsSettings() throws Exception
    {
        openTheMembersNote();

        LocomotiveSettingsWindow member = SettingsWindow.showing();

        assertEquals(SettingsWindow.text("settings.name"), OTHER);

        mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_RELEASED, true);

        assertTrue(Components.waitFor(() -> NAME.equals(SettingsWindow.text("settings.name")), WAIT),
            "the click did not open the multi-unit's settings");
        assertFalse(Components.onTheEventThread(member::isDisplayable), "the member's window was not closed");
        assertEquals(SettingsWindow.tabShowing(), 3, "the multi-unit's settings did not open on the Multi-Unit tab");

        // The keyboard
        SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.MULTI_UNIT, null);
        assertNotNull(SettingsWindow.awaited());
        assertEquals(SettingsWindow.text("settings.name"), OTHER);

        support.Keys.press(SettingsWindow.part("settings.multiUnitNote", javax.swing.JTextPane.class),
            java.awt.event.KeyEvent.VK_ENTER);

        assertTrue(Components.waitFor(() -> NAME.equals(SettingsWindow.text("settings.name")), WAIT),
            "Enter on the link did not open the multi-unit");
    }

    /**
     * Tab and Shift+Tab leave the link for the next and the previous control, as they do everywhere else in the window
     * (an editable text pane keeps Tab for itself).
     *
     * MUTATION: take `setEditable(false)` out of `partOfNote`, and this fails.
     */
    @Test
    public void testTabLeavesTheLink() throws Exception
    {
        openTheMembersNote();

        javax.swing.JTextPane note = SettingsWindow.part("settings.multiUnitNote", javax.swing.JTextPane.class);

        java.util.Set<java.awt.AWTKeyStroke> forward = Components.onTheEventThread(
            () -> note.getFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        java.util.Set<java.awt.AWTKeyStroke> backward = Components.onTheEventThread(
            () -> note.getFocusTraversalKeys(java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));

        assertTrue(forward.contains(java.awt.AWTKeyStroke.getAWTKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0)),
            "Tab does not leave the link");
        assertTrue(backward.contains(java.awt.AWTKeyStroke.getAWTKeyStroke(java.awt.event.KeyEvent.VK_TAB,
            java.awt.event.InputEvent.SHIFT_DOWN_MASK)), "Shift+Tab does not leave the link");
    }

    /**
     * The link is the whole of the name and no more: the right half of its last letter and the left half of its first
     * are on it, the space on either side is not.
     *
     * MUTATION: let the hit test in `partOfNote` take one more letter (`pos <= from + name.length()`), and this fails.
     */
    @Test
    public void testTheLinkIsExactlyTheName() throws Exception
    {
        openTheMembersNote();

        javax.swing.JTextPane note = SettingsWindow.part("settings.multiUnitNote", javax.swing.JTextPane.class);
        int from = Components.onTheEventThread(() -> note.getText().lastIndexOf(NAME));
        java.awt.Rectangle first = Components.onTheEventThread(() -> viewOf(note, from));
        java.awt.Rectangle end = Components.onTheEventThread(() -> viewOf(note, from + NAME.length()));
        int y = first.y + first.height / 2;

        assertTrue(handAt(note, first.x - 2, y) == false, "the space before the name is on the link");
        assertTrue(handAt(note, first.x + 1, y), "the first letter's left edge is not on the link");
        assertTrue(handAt(note, end.x - 1, y), "the last letter's right half is not on the link");
        assertTrue(handAt(note, end.x + 1, y) == false, "the space after the name is on the link");
    }

    private static java.awt.Rectangle viewOf(javax.swing.JTextPane note, int at)
    {
        try
        {
            return note.modelToView(at);
        }
        catch (javax.swing.text.BadLocationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the pointer moved to this point of the note shows the hand. */
    private static boolean handAt(javax.swing.JTextPane note, int x, int y)
    {
        Components.onTheEventThread(() ->
        {
            note.dispatchEvent(new java.awt.event.MouseEvent(note, java.awt.event.MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, x, y, 0, false));

            return null;
        });

        return Components.onTheEventThread(() -> HandPointer.isHand(note.getCursor()));
    }

    /**
     * When the unit has changed since the tab was built, the link builds the tab again instead of opening a unit this
     * locomotive is no longer in.
     *
     * MUTATION: leave the check out of `partOfNote`'s action, and this fails.
     */
    @Test
    public void testAStaleLinkRefreshesTheTabInsteadOfOpening() throws Exception
    {
        openTheMembersNote();

        LocomotiveSettingsWindow member = SettingsWindow.showing();

        loc.setLinkedLocomotives(new java.util.HashMap<String, Double>());

        mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_RELEASED, true);

        assertSame(SettingsWindow.showing(), member, "the stale link opened another window");
        assertEquals(SettingsWindow.text("settings.name"), OTHER);
        assertTrue(Components.onTheEventThread(() -> SettingsWindow.part("settings.multiUnitNote",
            javax.swing.JTextPane.class) == null), "the tab still shows the note of a unit this locomotive left");
    }

    /**
     * With changes unsaved, the link asks Save, Discard or Cancel as the X does: Cancel leaves the window where it was,
     * with what was typed; Discard goes on to the multi-unit (FR-196).
     *
     * MUTATION: have the link dispose the window without asking, and this fails.
     */
    @Test
    public void testTheLinkAsksAboutUnsavedChangesFirst() throws Exception
    {
        openTheMembersNote();
        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 2;
        mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_RELEASED, true);

        assertTrue(SettingsWindow.showing() != null, "Cancel closed the window");
        assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel did not leave the window as it was");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 1;
        mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_RELEASED, true);

        assertTrue(Components.waitFor(() -> NAME.equals(SettingsWindow.text("settings.name")), WAIT),
            "Discard did not go on to the multi-unit");
        assertEquals(other.getName(), OTHER, "Discard saved the typed name");
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

        LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

        assertFalse(SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null), "Cancel went on to open it");
        assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel lost the typing");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 1;

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null));
        assertEquals(SettingsWindow.text("settings.name"), OTHER, "Discard did not go on to the other locomotive");
        assertEquals(loc.getName(), NAME, "Discard wrote the typing");

        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 0;

        assertTrue(SettingsWindow.open(ui, third, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null));
        assertNotNull(model.getLocByName("LS renamed"), "Save did not write the typing before moving on");
        assertEquals(SettingsWindow.text("settings.name"), THIRD);
    }

    /**
     * A minimized window opened by another door comes back out of the taskbar.  With typing not saved, its question
     * asks over the window brought back, and blocks that window alone, so the main window's Stop stays live.
     *
     * MUTATION: drop `bringBack` from `LocomotiveSettingsWindow.settleUnsaved`, or ask with an application-modal
     * `showOptionDialog`, and this fails.
     */
    @Test
    public void testAMinimizedWindowComesBackAndAsksOverItself() throws Exception
    {
        open(0);

        LocomotiveSettingsWindow window = SettingsWindow.showing();

        support.AskingWindows.minimize(window, true);

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null), "precondition: it did not open");
        assertTrue(Components.waitFor(() -> (Components.onTheEventThread(window::getExtendedState) & java.awt.Frame.ICONIFIED) == 0,
            5000), "opened from another door, the window stayed in the taskbar");

        SettingsWindow.type("settings.name", "LS renamed");

        support.AskingWindows.minimize(window, true);

        try (support.Answering answering = support.Answering.start(message -> support.Answering.press(I18n.t("ui.cancel"))))
        {
            assertFalse(SettingsWindow.open(ui, third, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null), "Cancel went on to open it");

            assertEquals(answering.questions().size(), 1, "the typing was not asked about");

            support.Answering.Question asked = answering.questions().get(0);

            assertSame(asked.owner, window, "the question does not hang off the window");
            assertFalse(asked.ownerMinimized, "the question asked over a minimized window");
            assertEquals(asked.modality, java.awt.Dialog.ModalityType.DOCUMENT_MODAL,
                "the question blocks more than its own window");
        }

        assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel lost the typing");
    }

    /**
     * While the unsaved-changes question is up, a second door brings it back out of the taskbar rather than being
     * canceled unseen, and a door of the main window speaks over the main window, though the question was asked from a
     * door of another window.
     *
     * MUTATION: drop `bringBack` from `settleUnsaved`'s early return, or ask the question inside the door's
     * `pressFromWindow`, and this fails.
     */
    @Test
    public void testWhileTheQuestionIsUpASecondDoorBringsItBack() throws Exception
    {
        open(0);

        LocomotiveSettingsWindow window = SettingsWindow.showing();

        SettingsWindow.type("settings.name", "LS renamed");

        javax.swing.JDialog elsewhere = Components.onTheEventThread(() ->
        {
            TrainControlUI.installLookAndFeel();

            javax.swing.JDialog out = new javax.swing.JDialog(ui, "another window");

            out.setSize(200, 100);
            out.setVisible(true);

            return out;
        });

        java.awt.Window[] parent = new java.awt.Window[1];
        boolean[] back = new boolean[1];

        try (Answering answering = Answering.start(message ->
        {
            try
            {
                parent[0] = Components.onTheEventThread(ui::dialogParent);

                support.AskingWindows.minimize(window, true);

                Components.onTheEventThread(() -> ui.openLocomotiveSettings(third, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null));

                back[0] = Components.waitFor(() -> (Components.onTheEventThread(window::getExtendedState)
                    & java.awt.Frame.ICONIFIED) == 0, 5000);
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }

            return Answering.press(I18n.t("ui.cancel"));
        }))
        {
            SwingUtilities.invokeLater(() -> ui.pressFromWindow(elsewhere,
                () -> ui.openLocomotiveSettings(other, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null)));

            assertTrue(answering.awaitAsked(1, 20000), "the typing was not asked about");
        }
        finally
        {
            support.AskingWindows.minimize(window, false);
            SwingUtilities.invokeAndWait(elsewhere::dispose);
        }

        assertTrue(back[0], "a second door while the question was up left it in the taskbar");
        assertSame(parent[0], ui, "with the question up, the main window's doors speak over " + parent[0]);
        assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel lost the typing");
    }

    /** How many Locomotive Settings windows are open. */
    private static long windowsOpen()
    {
        return Components.onTheEventThread(() -> java.util.Arrays.stream(java.awt.Window.getWindows())
            .filter(w -> w instanceof LocomotiveSettingsWindow && w.isDisplayable()).count());
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

        LocomotiveSettingsWindow first = SettingsWindow.showing();

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.NOTES, null), "the second door did not open");
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

        Object windows = Reflect.get(TrainControlUI.class, ui, "editorWindows");

        assertTrue((Boolean) Components.onTheEventThread(() -> invoke(asks, windows)), "the exit asked about a window with nothing typed");

        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

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
     * MUTATION: pass the typed name and address whenever either changed, in `LocomotiveSettingsWindow.write`, and this
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
     * An address-only Save keeps the decoder type another door changed meanwhile; changing both writes both.
     *
     * MUTATION: give `writeNameAndAddress` the chosen decoder whenever the identity changed, in
     * `LocomotiveTab.decoderToWrite`, and this fails.
     */
    @Test
    public void testAnAddressOnlySaveKeepsADecoderASyncChanged() throws Exception
    {
        open(0);

        model.changeLocAddress(NAME, 40, Locomotive.decoderType.DCC);

        saveTyped(null, "50");

        assertEquals(loc.getAddress(), 50, "the typed address was not written");
        assertEquals(loc.getDecoderType(), Locomotive.decoderType.DCC, "the address-only Save put the old decoder type back");

        open(0);

        saveTyped(Locomotive.decoderType.MFX, "3421");

        assertEquals(loc.getAddress(), 3421);
        assertEquals(loc.getDecoderType(), Locomotive.decoderType.MFX, "a changed decoder type was not written");
    }

    /**
     * The same-address check follows the decoder type the Save will write: a member that shares the typed address only
     * under the type a sync gave the train meanwhile still refuses an address-only Save.
     *
     * MUTATION: use `chosenDecoder()` in `LocomotiveTab.warnAboutTheSameAddress`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheSameAddressCheckUsesTheDecoderASyncChanged() throws Exception
    {
        loc.setLinkedLocomotives(java.util.Collections.singletonMap(OTHER, 1.0));
        open(0);

        model.changeLocAddress(NAME, 40, Locomotive.decoderType.DCC);
        model.changeLocAddress(OTHER, 50, Locomotive.decoderType.DCC);

        SettingsWindow.type("settings.address", "50");

        refusedOverTheConsist("an address shared with a member under the new type was not refused");
        assertEquals(loc.getAddress(), 40, "the refused Save changed the address");
    }

    /**
     * A year an import filled while the window was open survives a Save of the notes text beside it.
     *
     * MUTATION: write all four fields in `LocomotiveNotesPanel.apply` whenever one is dirty, and this fails.
     */
    @Test
    public void testANotesSaveKeepsAYearAnImportFilledMeanwhile() throws Exception
    {
        loc.setStructuredNotes(0, 1970, "LS Railway", "first notes");

        open(0);

        loc.setStructuredNotes(1955, 1970, "LS Railway", "first notes");

        typeArea("notes.text", "second notes");
        saveTyped(null, "3401");

        assertEquals(loc.getStructuredNotes().getStartYear(), 1955, "the import's start year was written back empty");
        assertEquals(loc.getStructuredNotes().getNotes(), "second notes");
    }

    /** Picks the decoder type when given one, types the address, and Saves. */
    private static void saveTyped(Locomotive.decoderType type, String address) throws Exception
    {
        if (type != null) decoder(type);

        SettingsWindow.type("settings.address", address);
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "the Save did not go through");
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

        SettingsWindow.open(ui, loc, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null);
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
        Object actions = Reflect.get(TrainControlUI.class, ui, "locomotiveActions");

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

        LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

        assertTrue(closeBy(true), "a window with nothing typed did not just close");

        open(0);

        SettingsWindow.type("settings.name", "LS renamed");

        for (boolean byEscape : new boolean[] { true, false })
        {
            LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

            assertFalse(closeBy(byEscape), "Cancel on the question closed the window");
            assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel lost the typing");
        }

        LocomotiveSettingsWindow.unsavedAnswerForTest = 1;

        assertTrue(closeBy(false), "Discard did not close the window");
        assertEquals(loc.getName(), NAME, "Discard wrote the typing");

        open(0);

        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 0;

        assertTrue(closeBy(true), "Save on the question did not close the window");
        assertNotNull(model.getLocByName("LS renamed"), "Save on the question did not write the typing");
    }

    /**
     * Cancel asks what to do with typing that is not saved, as the X does: Cancel on the question keeps the window and
     * the typing, Discard closes and writes nothing, Save writes and closes; with nothing typed it just closes (MT-939).
     *
     * MUTATION: wire the Cancel button to `close()` rather than `closeAsking()`, and this fails.
     */
    @Test
    public void testCancelAsksAboutTypingNotSaved() throws Exception
    {
        open(0);

        assertTrue(cancelAnswering(2), "a window with nothing typed did not just close");

        for (int answer = 2; answer >= 0; answer--)
        {
            if (SettingsWindow.showing() == null)
            {
                open(2);
                typeArea("notes.text", "typed notes");
            }

            assertEquals(cancelAnswering(answer), answer < 2,
                "answer " + answer + " to the question: the window stayed, or Cancel closed it without asking");

            if (answer == 2)
            {
                assertEquals(Components.onTheEventThread(() -> SettingsWindow.part("notes.text", JTextArea.class).getText()),
                    "typed notes", "Cancel on the question lost the typing");
            }

            if (answer == 1) assertEquals(loc.getStructuredNotes().getNotes(), "first notes", "Discard wrote the notes");
        }

        assertEquals(loc.getStructuredNotes().getNotes(), "typed notes", "Save on the question did not write the notes");
    }

    /**
     * A window locked while its train moves, or while autonomy runs, closes at the X, Escape and Cancel without asking
     * when nothing is typed: what the run does to the train is not typing (MT-939).
     *
     * MUTATION: have `closeAsking` ask whether or not the window is dirty, and this fails.
     */
    @Test
    public void testALockedWindowWithNothingTypedClosesWithoutAsking() throws Exception
    {
        LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

        try
        {
            for (int door = 0; door < 3; door++)
            {
                open(0);

                SettingsWindow.power(ui, model, true);
                loc.setSpeed(40);

                assertTrue(Components.waitFor(() -> Components.onTheEventThread(
                    SettingsWindow.part("settings.strip", JLabel.class)::isVisible), WAIT), "precondition: the window did not lock");

                if (door < 2)
                {
                    assertTrue(closeBy(door == 0), "a locked window with nothing typed did not close at door " + door);
                }
                else
                {
                    assertTrue(cancelAnswering(2), "a locked window with nothing typed did not close at Cancel");
                }

                loc.setSpeed(0);
                SettingsWindow.power(ui, model, false);
            }

            open(0);

            model.getAutoLayout().setStagingInProgress(true);

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(
                SettingsWindow.part("settings.strip", JLabel.class)::isVisible), WAIT), "precondition: autonomy did not lock the window");

            assertTrue(cancelAnswering(2), "a window locked by autonomy with nothing typed did not close at Cancel");
        }
        finally
        {
            model.getAutoLayout().setStagingInProgress(false);
            loc.setSpeed(0);
            SettingsWindow.power(ui, model, false);
        }
    }

    /**
     * Typing made before the window locked is still asked about at Cancel: Save on the question is refused with the
     * moving rule's sentence and keeps the window and the typing, writing nothing; Discard then closes (MT-939).
     *
     * MUTATION: have the Cancel button close without asking while the window is locked, and this fails.
     */
    @Test
    public void testTypingFromBeforeTheLockIsAskedAboutAtCancel() throws Exception
    {
        open(0);
        SettingsWindow.type("settings.name", "LS renamed");

        try
        {
            SettingsWindow.power(ui, model, true);
            loc.setSpeed(40);

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(
                SettingsWindow.part("settings.strip", JLabel.class)::isVisible), WAIT), "precondition: the window did not lock");

            assertEquals(SettingsWindow.asked(() -> cancelAnswering(0)), Arrays.asList(I18n.f("loc.ui.stopToEdit", NAME)),
                "Save on the question was not refused for a moving train");
            assertNotNull(SettingsWindow.showing(), "the refused Save closed the window");
            assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "the refused Save lost the typing");
            assertEquals(loc.getName(), NAME, "the refused Save wrote the typing");

            assertTrue(cancelAnswering(1), "Discard did not close the locked window");
            assertEquals(loc.getName(), NAME, "Discard wrote the typing");
        }
        finally
        {
            loc.setSpeed(0);
            SettingsWindow.power(ui, model, false);
        }
    }

    /**
     * Add Locomotive into a window holding typing asks first, and so does opening a locomotive over Add's typing; Cancel
     * keeps the typing where it was, Discard goes on and writes nothing (MT-939).
     *
     * MUTATION: skip `settleUnsaved` in `aimAtANewLocomotive`, and this fails.
     */
    @Test
    public void testAddLocomotiveAndTheWindowItLeavesAskAboutTypingNotSaved() throws Exception
    {
        open(0);
        SettingsWindow.type("settings.name", "LS renamed");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

        assertFalse(Components.onTheEventThread(ui::openAddLocomotive), "Cancel went on to Add Locomotive");
        assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel lost the typing");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 1;

        assertTrue(Components.onTheEventThread(ui::openAddLocomotive), "Discard did not go on to Add Locomotive");
        assertEquals(SettingsWindow.text("settings.name"), "", "Add Locomotive did not open blank");
        assertEquals(loc.getName(), NAME, "Discard wrote the typing");

        SettingsWindow.type("settings.name", "LS elsewhere");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

        assertFalse(SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null), "Cancel went on to the other locomotive");
        assertEquals(SettingsWindow.text("settings.name"), "LS elsewhere", "Cancel lost Add's typing");

        LocomotiveSettingsWindow.unsavedAnswerForTest = 1;

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null), "Discard did not go on");
        assertEquals(SettingsWindow.text("settings.name"), OTHER);
        assertNull(model.getLocByName("LS elsewhere"), "Discard made the locomotive typed in Add");
    }

    /**
     * A blank address is not a quiet close: Save keeps the window open with a message saying what is wrong and writes
     * nothing, and the X and Escape ask Save, Discard or Cancel; Discard closes with the address as it was (OB-439).
     *
     * MUTATION: let `LocomotiveTab.addressText` hand a blank address on as "no change" (return null for it), and Save
     * closes the window without a word.
     */
    @Test
    public void testABlankAddressIsRefusedAndNeverClosesQuietly() throws Exception
    {
        open(0);

        SettingsWindow.type("settings.address", "");

        assertEquals(saveAndAnswer(), Arrays.asList(I18n.t("loc.errorAddressNotNumeric")), "a blank address was not refused");
        assertNotNull(SettingsWindow.showing(), "Save closed the window over a blank address");
        assertEquals(loc.getAddress(), 3401, "a blank address was written");

        for (boolean byEscape : new boolean[] { true, false })
        {
            LocomotiveSettingsWindow.unsavedAnswerForTest = 2;

            assertFalse(closeBy(byEscape), "the window closed over a blank address without asking");
        }

        // Save on the question is the same refusal, and the window stays
        LocomotiveSettingsWindow.unsavedAnswerForTest = 0;

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            assertFalse(closeBy(true), "Save on the question closed the window over a blank address");
            assertEquals(answering.asked(), Arrays.asList(I18n.t("loc.errorAddressNotNumeric")));
        }

        LocomotiveSettingsWindow.unsavedAnswerForTest = 1;

        assertTrue(closeBy(true), "Discard did not close the window");
        assertEquals(loc.getAddress(), 3401, "Discard wrote the blank address");
    }

    /** The arrow button of the address field, "next" or "previous". */
    private static JButton addressArrow(boolean next)
    {
        return Components.onTheEventThread(() -> Components.find(SettingsWindow.part("settings.addressSpinner", JSpinner.class),
            JButton.class, b -> next == "Spinner.nextButton".equals(b.getName())));
    }

    /**
     * The address field has up and down arrows like the length field: one step each, written only at Save, and held to
     * the chosen decoder type's range (MM2 stops at 80), from a blank as from zero (OB-439).
     *
     * MUTATION: let `AddressModel.ifValid` answer any value, and the MM2 stop at 80 is passed.
     */
    @Test
    public void testTheAddressArrowsStepWithinTheDecodersRange() throws Exception
    {
        open(0);

        assertNotNull(addressArrow(true), "the address field has no up arrow");

        Components.press(addressArrow(true));
        assertEquals(SettingsWindow.text("settings.address"), "3402", "the up arrow did not step the address");

        Components.press(addressArrow(false));
        Components.press(addressArrow(false));
        assertEquals(SettingsWindow.text("settings.address"), "3400", "the down arrow did not step the address");
        assertEquals(loc.getAddress(), 3401, "the arrows wrote before Save");
        assertTrue(SettingsWindow.save().isEnabled(), "stepping the address left Save grayed");

        decoder(Locomotive.decoderType.MM2);
        SettingsWindow.type("settings.address", "80");
        Components.press(addressArrow(true));
        assertEquals(SettingsWindow.text("settings.address"), "80", "the up arrow went past MM2's last address");

        SettingsWindow.type("settings.address", "1");
        Components.press(addressArrow(false));
        assertEquals(SettingsWindow.text("settings.address"), "1", "the down arrow went below address 1");

        SettingsWindow.type("settings.address", "");
        Components.press(addressArrow(true));
        assertEquals(SettingsWindow.text("settings.address"), "1", "the up arrow did not start a blank at 1");

        // An address outside the chosen type's range steps to the nearest one inside it
        for (boolean up : new boolean[] { true, false })
        {
            SettingsWindow.type("settings.address", "3401");
            Components.press(addressArrow(up));
            assertEquals(SettingsWindow.text("settings.address"), "80", "an address above MM2's range did not step to its last");
        }
    }

    /**
     * Changing the decoder type changes the Functions tab's rows at once, to the count that type gets on reopening;
     * nothing is written until Save.
     *
     * MUTATION: leave `functionsTab.showCount` out of the decoder box's listener in `LocomotiveTab`, and this fails.
     */
    @Test
    public void testTheDecoderChoiceSetsTheFunctionCountAtOnce() throws Exception
    {
        open(1);

        assertEquals(functionRows(), MarklinLocomotive.getMaxNumF(Locomotive.decoderType.MFX), "the MFX locomotive has the wrong rows");

        for (Locomotive.decoderType type : new Locomotive.decoderType[] { Locomotive.decoderType.DCC,
            Locomotive.decoderType.MM2, Locomotive.decoderType.MFX })
        {
            decoder(type);

            assertEquals(functionRows(), MarklinLocomotive.getMaxNumF(type), "the Functions tab did not follow " + type);
            assertEquals(loc.getNumF(), MarklinLocomotive.MFX_NUM_FN, "choosing " + type + " wrote before Save");
        }

        decoder(Locomotive.decoderType.MM2);
        SettingsWindow.type("settings.address", "20");
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "the Save did not go through");

        open(1);

        assertEquals(functionRows(), loc.getNumF(), "what the tab showed is not what reopening shows");
        assertEquals(loc.getNumF(), MarklinLocomotive.getMaxNumF(Locomotive.decoderType.MM2));
    }

    /**
     * Stored function images and the Autonomy tab's function lists follow the decoder type chosen in the window: an
     * image above a smaller type's count is back when the bigger type is chosen again, Reset All clears it wherever it
     * sits, and a departure function the new type does not have is not offered, kept or recorded.
     *
     * MUTATION: seed a new row with a null image (the `getLocalFunctionImageURLs` lookup in `showCount`), skip the
     * set-aside rows in Reset All, or drop `autonomyTab.showCount` from the decoder listener, and this fails.
     */
    @Test
    public void testTheTabsFollowTheDecoderChosenInTheWindow() throws Exception
    {
        fixture.needPictures("lwindow");

        loc.setLocalFunctionImageURL(30, SettingsWindow.picture(fixture.folder, "f30.png", java.awt.Color.RED).toPath().toUri().toString());
        loc.setDepartureFunc(20);

        open(1);

        decoder(Locomotive.decoderType.DCC);
        decoder(Locomotive.decoderType.MFX);

        assertEquals(iconComboIndex(30), -1, "the image stored for F30 did not come back with MFX's 32 functions");

        decoder(Locomotive.decoderType.DCC);
        SettingsWindow.resetAll("Yes");
        decoder(Locomotive.decoderType.MFX);

        assertTrue(iconComboIndex(30) >= 0, "Reset All left the image on a function the decoder had no room for");

        SettingsWindow.selectTab(4);
        decoder(Locomotive.decoderType.MM2);

        javax.swing.JComboBox<?> departure = SettingsWindow.part("autonomy.departure", JComboBox.class);

        assertEquals((int) Components.onTheEventThread(departure::getItemCount), 1 + MarklinLocomotive.MM2_NUM_FN,
            "the departure list is not MM2's");
        assertEquals((int) Components.onTheEventThread(departure::getSelectedIndex), 0, "F20 stayed chosen under MM2");

        SettingsWindow.type("settings.address", "20");
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT), "the Save did not go through");
        assertNull(loc.getDepartureFunc(), "a departure function MM2 does not have was kept");
    }

    /**
     * Clear Preset reaches the rows a smaller decoder type has set aside, so they do not come back with an old tick.
     *
     * MUTATION: loop over `rows` instead of `everyRow()` in the Clear Preset listener, and this fails.
     */
    @Test
    public void testClearPresetReachesTheRowsADecoderSetAside() throws Exception
    {
        boolean[] preset = new boolean[loc.getNumF()];

        preset[30] = true;
        loc.setPreferredFunctions(preset);

        open(1);

        decoder(Locomotive.decoderType.DCC);
        Components.press(SettingsWindow.part("functions.clearPreset", JButton.class));
        decoder(Locomotive.decoderType.MFX);

        assertEquals(Components.onTheEventThread(() -> SettingsWindow.part("functions.table", javax.swing.JTable.class).getValueAt(30, 3)),
            (Object) Boolean.FALSE, "F30's preset tick came back after Clear Preset");
    }

    /** The locomotive as a DCC one with an image of its own stored for F30, as a Save under DCC leaves it. */
    private void withAnImageBeyondDcc() throws Exception
    {
        fixture.needPictures("lwindow");

        loc.setLocalFunctionImageURL(30, SettingsWindow.picture(fixture.folder, "f30.png", java.awt.Color.RED).toPath().toUri().toString());
        model.changeLocAddress(NAME, 20, Locomotive.decoderType.DCC);
    }

    /**
     * Rows a bigger decoder type adds take Reset All's word, not the image stored for them: Reset All under DCC, then
     * MFX, shows F30 without its own image.
     *
     * MUTATION: take the stored image whatever `reset` says in `LocomotiveFunctionsTab.showCount`, and this fails.
     */
    @Test
    public void testRowsABiggerDecoderAddsFollowResetAll() throws Exception
    {
        withAnImageBeyondDcc();

        open(1);

        SettingsWindow.resetAll("Yes");
        decoder(Locomotive.decoderType.MFX);

        assertTrue(iconComboIndex(30) >= 0, "F30 came back with its own image after Reset All");
    }

    /**
     * Rows a bigger decoder type adds take what Copy Icons copied: the copied icon and behavior for F30 under MFX.
     *
     * MUTATION: leave `takeTheCopy` out of the new-row branch of `LocomotiveFunctionsTab.showCount`, and this fails.
     */
    @Test
    public void testRowsABiggerDecoderAddsFollowCopyIcons() throws Exception
    {
        withAnImageBeyondDcc();

        other.setFunctionType(30, 21, Locomotive.FUNCTION_PULSE);

        key = SettingsWindow.showOnKeyA(ui, other);

        JButton keyA = SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_A);

        Components.onTheEventThread(() ->
        {
            ui.setCopyTarget(keyA, false);

            return null;
        });

        key = SettingsWindow.showOnKeyA(ui, loc);

        open(1);

        Components.press(SettingsWindow.part("functions.copyIcons", JButton.class));
        decoder(Locomotive.decoderType.MFX);

        assertEquals(iconComboIndex(30), 21, "F30 did not get the copied icon");
    }

    private static int iconComboIndex(int row)
    {
        javax.swing.JComboBox<?> combo = SettingsWindow.inEditor(row, 1, "functions.iconCombo", JComboBox.class);

        // A row with an image of its own has that image's entry marked, after the standard icons
        int picked = Components.onTheEventThread(combo::getSelectedIndex);

        return picked > loc.getNumFnIcons() ? -1 : picked;
    }

    private static int functionRows()
    {
        return Components.onTheEventThread(() -> SettingsWindow.part("functions.table", javax.swing.JTable.class).getRowCount());
    }

    /** Closes by Escape or by the X, and says whether the window went. */
    private static boolean closeBy(boolean escape) throws Exception
    {
        LocomotiveSettingsWindow window = SettingsWindow.showing();

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
     * The picture in Locomotive Settings carries the revert, and it clears this computer's icon so the Central Station's
     * picture shows again; the main window's picture no longer offers it (OB-443).
     *
     * MUTATION: wire the revert to nothing in `LocomotiveTab`, and this fails.
     */
    @Test
    public void testTheSettingsPictureRevertsToTheStationsPicture() throws Exception
    {
        loc.setImageURL("http://cs/OB-443.png");
        loc.setLocalImageURL(new java.io.File("OB-443-own.png").toURI().toString());

        open(0);

        Component revert = SettingsWindow.part("settings.revert", Component.class);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(revert::isVisible), WAIT),
            "no revert over a picture replaced on this computer");

        Mouse.click(revert);

        assertTrue(Components.waitFor(() -> loc.getLocalImageURL() == null, WAIT), "the revert did not clear the icon");
        assertEquals(loc.getImageURL(), "http://cs/OB-443.png", "the Central Station's picture is not back");
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

        LocomotiveSettingsWindow window = SettingsWindow.showing();

        Mouse.click(SettingsWindow.part("settings.wrench", Component.class));

        final java.awt.Window[] owner = new java.awt.Window[1];
        final javax.swing.JFileChooser chooser = chooserShowing(owner);

        Components.onTheEventThread(() ->
        {
            chooser.cancelSelection();

            return null;
        });

        assertSame(owner[0], window, "the chooser hangs off " + owner[0] + ", not the settings window");
    }

    /**
     * The pill after the picture's heading names where the picture shown comes from, and changes at once when the
     * wrench sets a picture or the revert clears it, without Save; with no picture at all it is hidden (MT-924).  The
     * window's once-a-second watch is stopped, so only the action itself can change the pill.
     *
     * MUTATION: drop the `source.setText` calls from `LocomotiveTab.refreshPicture`, or stop `tellLocomotiveSettings`
     * telling the window, and this fails.
     */
    @Test
    public void testThePicturesPillNamesItsSourceAtOnce() throws Exception
    {
        java.io.File own = pngFile();

        String station = I18n.t("loc.ui.settingsPictureFromStation");
        String local = I18n.t("loc.ui.settingsPictureLocal");

        loc.setImageURL(null);
        loc.setLocalImageURL(null);
        openWithoutTheWatch();
        assertEquals(pillShows(null), null, "a pill shows over no picture at all");

        pickPicture(own);
        assertEquals(pillShows(local), local, "setting a picture did not name it a local file at once");

        Mouse.click(SettingsWindow.part("settings.revert", Component.class));
        assertEquals(pillShows(null), null, "clearing the only picture left the pill up");

        // The Central Station's picture, then one of this computer's over it and cleared again
        assertTrue(closeBy(true), "Escape did not close the window");
        loc.setImageURL(pngFile().toURI().toString());
        openWithoutTheWatch();
        assertEquals(pillShows(station), station, "the Central Station's picture is not named");
        pictureLayout("en");

        pickPicture(own);
        assertEquals(pillShows(local), local, "a local picture over the station's is not named at once");

        // Nothing waits for Save: closed without it, the window opens on the picture as it now is
        assertTrue(closeBy(true), "Escape did not close the window");
        openWithoutTheWatch();
        assertEquals(pillShows(local), local, "the window opened again on a pill that is not the picture's");

        Mouse.click(SettingsWindow.part("settings.revert", Component.class));
        assertEquals(pillShows(station), station, "clearing the local picture did not name the station's at once");
    }

    /**
     * The pill names the picture on screen: while the station's picture is still on its way, the revert leaves the
     * local picture and its pill up together, and both change when the station's picture arrives (MT-924).
     *
     * MUTATION: set the pill in `refreshPicture` when the fetch starts rather than when its picture lands, and this
     * fails.
     */
    @Test
    public void testThePillWaitsForThePictureItNames() throws Exception
    {
        String station = I18n.t("loc.ui.settingsPictureFromStation");
        String local = I18n.t("loc.ui.settingsPictureLocal");
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();

        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB),
            "png", png);

        // A station that holds its picture back until told
        try (support.StandInStation slow = new support.StandInStation((path, n) ->
        {
            try
            {
                release.await(2 * WAIT, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            return png.toByteArray();
        }))
        {
            loc.setImageURL("http://" + slow.address() + "/MT-924-slow.png");
            loc.setLocalImageURL(pngFile().toURI().toString());
            open(0);
            assertEquals(pillShows(local), local, "the local picture is not named");

            JLabel picture = SettingsWindow.part("settings.picture", JLabel.class);
            CurrentKeyRow.Pill pill = SettingsWindow.part("settings.pictureSource", CurrentKeyRow.Pill.class);
            javax.swing.Icon own = Components.onTheEventThread(picture::getIcon);

            Mouse.click(SettingsWindow.part("settings.revert", Component.class));
            assertTrue(Components.waitFor(() -> loc.getLocalImageURL() == null, WAIT), "the revert did not clear the icon");

            // The window's own look at the locomotive, and what is on screen straight after it
            LocomotiveSettingsWindow window = SettingsWindow.showing();
            Object[] shown = Components.onTheEventThread(() ->
            {
                watch(window).getActionListeners()[0].actionPerformed(null);

                return new Object[] {picture.getIcon(), pill.isShowing() ? pill.getText() : null};
            });

            assertSame(shown[0], own, "the local picture left before the station's arrived");
            assertEquals(shown[1], local, "the pill named the station's picture over the local one");

            release.countDown();
            assertEquals(pillShows(station), station, "the station's picture arrived without its pill");
            assertNotSame(Components.onTheEventThread(picture::getIcon), own, "the station's picture never replaced it");
        }
        finally
        {
            release.countDown();
        }
    }

    /**
     * A picture that fails to load leaves no pill: there is no picture on screen for it to name.
     *
     * MUTATION: have `refreshPicture` set the pill whether or not the picture loaded, and this fails.
     */
    @Test
    public void testAPictureThatFailsToLoadHasNoPill() throws Exception
    {
        String station = I18n.t("loc.ui.settingsPictureFromStation");
        java.io.File broken = java.io.File.createTempFile("broken-", ".png");

        broken.deleteOnExit();
        java.nio.file.Files.write(broken.toPath(), "not a picture".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        loc.setLocalImageURL(null);
        loc.setImageURL(pngFile().toURI().toString());
        openWithoutTheWatch();
        assertEquals(pillShows(station), station, "precondition: the picture is not named");

        JLabel picture = SettingsWindow.part("settings.picture", JLabel.class);
        LocomotiveSettingsWindow window = SettingsWindow.showing();

        loc.setImageURL(broken.toURI().toString());
        Components.onTheEventThread(() -> { watch(window).getActionListeners()[0].actionPerformed(null); return null; });

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(picture::getIcon) == null, WAIT),
            "the picture that fails to load never took the old one's place");
        Components.settle();

        assertNull(pillShows(null), "a picture that failed to load is named");
    }

    /**
     * The revert and crop marks belong to the picture on screen, as the pill does, at every door that swaps it: a
     * window opening on a slow picture shows no marks over the empty frame, a new local picture brings its revert when
     * it lands, and after a revert the local picture keeps its marks until the station's picture arrives (OB-482).
     *
     * MUTATION: set the marks' visibility in `refreshPicture` when the fetch starts rather than in `arrived`, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testTheMarksWaitForThePictureTheySitOn() throws Exception
    {
        String station = I18n.t("loc.ui.settingsPictureFromStation");
        String local = I18n.t("loc.ui.settingsPictureLocal");
        java.util.Map<String, java.util.concurrent.CountDownLatch> held = heldPictures("a", "b", "c");

        try (support.StandInStation slow = slowStation(held))
        {
            loc.setLocalImageURL(null);
            loc.setImageURL(slowPicture(slow, "a"));
            open(0);

            JLabel picture = SettingsWindow.part("settings.picture", JLabel.class);
            Component crop = SettingsWindow.part("settings.crop", Component.class);
            Component revert = SettingsWindow.part("settings.revert", Component.class);
            LocomotiveSettingsWindow window = SettingsWindow.showing();

            Components.settle();
            assertEquals(marks(crop, revert), "", "marks showed over a picture still on its way");

            arrives(held, "a", station, crop, revert, "crop");

            // A local picture slow to load, as the wrench sets one
            javax.swing.Icon stations = Components.onTheEventThread(picture::getIcon);

            loc.setLocalImageURL(slowPicture(slow, "b"));
            look(window);

            stillUp(picture, stations, marks(crop, revert), "crop", "the revert came before the local picture it clears");

            arrives(held, "b", local, crop, revert, "crop revert");

            // The revert, with the station's picture now slow: the local one keeps its marks until it is replaced
            javax.swing.Icon own = Components.onTheEventThread(picture::getIcon);

            loc.setImageURL(slowPicture(slow, "c"));
            Mouse.click(revert);
            assertTrue(Components.waitFor(() -> loc.getLocalImageURL() == null, WAIT), "the revert did not clear the icon");
            look(window);

            stillUp(picture, own, marks(crop, revert), "crop revert", "the marks changed before the station's picture arrived");

            arrives(held, "c", station, crop, revert, "crop");
        }
        finally
        {
            held.values().forEach(java.util.concurrent.CountDownLatch::countDown);
        }
    }

    /**
     * The crop acts on the picture on screen: clicked after a revert while the station's picture is still on its way,
     * it opens nothing, rather than a crop of a picture the window does not show yet (OB-482).
     *
     * MUTATION: drop the `waiting` check from `LocomotiveTab.mark`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheCropDoesNothingWhileItsPictureIsOnItsWay() throws Exception
    {
        String station = I18n.t("loc.ui.settingsPictureFromStation");
        String local = I18n.t("loc.ui.settingsPictureLocal");
        String cropTitle = I18n.t("loc.ui.cropTitle");
        java.util.Map<String, java.util.concurrent.CountDownLatch> held = heldPictures("d");

        try (support.StandInStation slow = slowStation(held))
        {
            loc.setImageURL(slowPicture(slow, "d"));
            loc.setLocalImageURL(pngFile().toURI().toString());
            open(0);
            assertEquals(pillShows(local), local, "the local picture is not named");

            Component crop = SettingsWindow.part("settings.crop", Component.class);

            Mouse.click(SettingsWindow.part("settings.revert", Component.class));
            assertTrue(Components.waitFor(() -> loc.getLocalImageURL() == null, WAIT), "the revert did not clear the icon");
            look(SettingsWindow.showing());
            assertTrue(Components.onTheEventThread(crop::isVisible), "precondition: no crop over the local picture");

            Mouse.click(crop);

            held.get("d").countDown();
            assertEquals(pillShows(station), station, "the station's picture arrived without its pill");
            assertFalse(Components.waitFor(() -> Components.onTheEventThread(() -> java.util.Arrays.stream(
                java.awt.Window.getWindows()).anyMatch(w -> w instanceof java.awt.Dialog && w.isShowing()
                && cropTitle.equals(((java.awt.Dialog) w).getTitle()))), 3000),
                "the crop clicked before the station's picture arrived opened a crop of it");
        }
        finally
        {
            held.values().forEach(java.util.concurrent.CountDownLatch::countDown);

            Components.onTheEventThread(() ->
            {
                for (java.awt.Window w : java.awt.Window.getWindows())
                {
                    if (w instanceof java.awt.Dialog && cropTitle.equals(((java.awt.Dialog) w).getTitle())) w.dispose();
                }

                return null;
            });
        }
    }

    /** One latch for each picture name, which holds that picture back until it is counted down. */
    private static java.util.Map<String, java.util.concurrent.CountDownLatch> heldPictures(String... names)
    {
        java.util.Map<String, java.util.concurrent.CountDownLatch> held = new java.util.concurrent.ConcurrentHashMap<>();

        for (String name : names) held.put(name, new java.util.concurrent.CountDownLatch(1));

        return held;
    }

    /** A station whose pictures each wait for their latch in `held`, by the name `slowPicture` gave them. */
    private static support.StandInStation slowStation(java.util.Map<String, java.util.concurrent.CountDownLatch> held)
        throws Exception
    {
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();

        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB),
            "png", png);

        return new support.StandInStation((path, n) ->
        {
            String name = path.substring(path.lastIndexOf('-') + 1).replace(".png", "");
            java.util.concurrent.CountDownLatch release = held.get(name);

            try
            {
                if (release != null) release.await(2 * WAIT, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            return png.toByteArray();
        });
    }

    /** A picture on the slow station, new to this run so no cache holds it. */
    private static String slowPicture(support.StandInStation slow, String name)
    {
        return "http://" + slow.address() + "/OB-482-" + Long.toString(System.nanoTime(), 36) + "-" + name + ".png";
    }

    /** The window's own look at the locomotive, as its once-a-second watch takes it. */
    private static void look(LocomotiveSettingsWindow window) throws Exception
    {
        javax.swing.Timer watch = watch(window);

        Components.onTheEventThread(() ->
        {
            watch.getActionListeners()[0].actionPerformed(null);

            return null;
        });
    }

    /** Lets the named picture through, and asserts that its pill and these marks came with it. */
    private static void arrives(java.util.Map<String, java.util.concurrent.CountDownLatch> held, String name, String pill,
        Component crop, Component revert, String expected) throws Exception
    {
        held.get(name).countDown();

        assertEquals(pillShows(pill), pill, "picture " + name + " arrived without its pill");
        assertEquals(marks(crop, revert), expected, "picture " + name + " arrived without its marks");
    }

    /** While the next picture is on its way, the one before is still on screen with its marks. */
    private static void stillUp(JLabel picture, javax.swing.Icon before, String marks, String expected, String message)
    {
        assertSame(Components.onTheEventThread(picture::getIcon), before, "precondition: the picture left before the next one came");
        assertEquals(marks, expected, message);
    }

    /** Presses Cancel with this answer ready for the question, if it asks; true when the window closed. */
    private static boolean cancelAnswering(int answer)
    {
        LocomotiveSettingsWindow.unsavedAnswerForTest = answer;
        SettingsWindow.press(SettingsWindow.cancel());

        return SettingsWindow.showing() == null;
    }

    /** Which of the picture's marks show: "crop", "revert", both or neither. */
    private static String marks(Component crop, Component revert)
    {
        return Components.onTheEventThread(() -> ((crop.isVisible() ? "crop " : "") + (revert.isVisible() ? "revert" : "")).trim());
    }

    /** Opens the window on Basic Settings, its watch stopped: what changes then, the action itself changed. */
    private static void openWithoutTheWatch() throws Exception
    {
        open(0);

        javax.swing.Timer watch = watch(SettingsWindow.showing());

        Components.onTheEventThread(() ->
        {
            watch.stop();

            return null;
        });
    }

    /** A small picture in a file of its own, gone when the run ends. */
    private static java.io.File pngFile() throws Exception
    {
        java.io.File file = java.io.File.createTempFile("MT-924-", ".png");

        file.deleteOnExit();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB),
            "png", file);

        return file;
    }

    /** The window's once-a-second look at the locomotive. */
    private static javax.swing.Timer watch(LocomotiveSettingsWindow window) throws Exception
    {
        return (javax.swing.Timer) Reflect.get(LocomotiveSettingsWindow.class, window, "watch");
    }

    /** The pill's words once they are these (null: once it is hidden), or what it shows when they never come. */
    private static String pillShows(String expected) throws Exception
    {
        CurrentKeyRow.Pill pill = SettingsWindow.part("settings.pictureSource", CurrentKeyRow.Pill.class);
        java.util.function.Supplier<String> shown = () -> Components.onTheEventThread(() ->
            pill.isShowing() ? pill.getText() : null);

        Components.waitFor(() -> java.util.Objects.equals(shown.get(), expected), WAIT);

        return shown.get();
    }

    /**
     * In every language the picture's column, its heading and the fields keep their places whether the pill names the
     * station, a local file or nothing; the pill ends where the picture does and never touches the heading (MT-924).
     *
     * MUTATION: drop `gbc.fill = GridBagConstraints.HORIZONTAL` before the heading row, and this fails.
     */
    @Test
    public void testThePillMovesNothingInAnyLanguage() throws Exception
    {
        String station = pngFile().toURI().toString();
        String own = pngFile().toURI().toString();

        try
        {
            for (String lang : new String[] {"en", "de", "da", "es", "fr", "it", "nl", "pl"})
            {
                I18n.setLocale(new java.util.Locale(lang));

                String[] words = {I18n.t("loc.ui.settingsPictureFromStation"), I18n.t("loc.ui.settingsPictureLocal"), null};
                String first = null;

                for (int state = 0; state < words.length; state++)
                {
                    loc.setImageURL(state == 2 ? null : station);
                    loc.setLocalImageURL(state == 1 ? own : null);
                    SettingsWindow.closeIfOpen();
                    open(0);
                    assertEquals(pillShows(words[state]), words[state], lang + ": the pill does not name the picture");

                    String layout = pictureLayout(lang);

                    if (first == null) first = layout;

                    assertEquals(layout, first, lang + ": the picture's column or the rows moved with the pill");
                }
            }
        }
        finally
        {
            I18n.setLocale(java.util.Locale.ENGLISH);
        }
    }

    /**
     * Where the picture's column, its heading and the name field sit in the tab; on the way, the pill must end where
     * the picture does, clear of the heading, and the heading must be level with Name and Address.
     */
    private static String pictureLayout(String lang) throws Exception
    {
        LocomotiveSettingsWindow window = SettingsWindow.showing();
        java.awt.Rectangle[] at = Components.onTheEventThread(() ->
        {
            java.awt.Container pane = window.getContentPane();
            Component column = Components.named(pane, "settings.pictureColumn", Component.class);
            java.awt.Container tab = column.getParent();
            Component[] parts = {column, Components.named(pane, "settings.picture", Component.class),
                Components.inside(tab, JLabel.class, l -> I18n.t("loc.ui.settingsLocomotiveIcon").equals(l.getText())),
                Components.inside(tab, JLabel.class, l -> I18n.t("loc.ui.settingsNameAndAddress").equals(l.getText())),
                Components.named(pane, "settings.name", Component.class),
                Components.named(pane, "settings.pictureSource", Component.class)};
            java.awt.Rectangle[] r = new java.awt.Rectangle[parts.length];

            for (int i = 0; i < parts.length; i++)
            {
                if (parts[i].isShowing()) r[i] = SwingUtilities.convertRectangle(parts[i].getParent(), parts[i].getBounds(), tab);
            }

            return r;
        });

        java.awt.Rectangle column = at[0], picture = at[1], heading = at[2], level = at[3], name = at[4], pill = at[5];

        assertEquals(heading.y, level.y, lang + ": the picture's heading is not level with Name and Address");

        if (pill != null)
        {
            assertTrue(Math.abs(pill.x + pill.width - picture.x - picture.width) <= 1,
                lang + ": the pill ends at " + (pill.x + pill.width) + ", the picture at " + (picture.x + picture.width));
            assertTrue(heading.x + heading.width < pill.x, lang + ": the heading " + heading + " runs into the pill " + pill);
        }

        return "column " + column.x + "+" + column.width + ", picture " + picture.x + "+" + picture.width
            + ", heading y " + heading.y + ", name y " + name.y;
    }

    /** Picks this picture with the wrench's chooser, uncropped. */
    private static void pickPicture(java.io.File file) throws Exception
    {
        Mouse.click(SettingsWindow.part("settings.wrench", Component.class));

        javax.swing.JFileChooser chooser = chooserShowing(new java.awt.Window[1]);

        Components.onTheEventThread(() ->
        {
            Components.inside(chooser, javax.swing.JCheckBox.class).setSelected(false);
            chooser.setSelectedFile(file);
            chooser.approveSelection();

            return null;
        });
    }

    /**
     * The pill lays the tab out again only when its words change, not every time it is told them (MT-924).
     *
     * MUTATION: drop the early return from `CurrentKeyRow.Pill.setText`, and this fails.
     */
    @Test
    public void testThePillLaysOutOnlyForNewWords() throws Exception
    {
        String station = I18n.t("loc.ui.settingsPictureFromStation");

        loc.setImageURL(pngFile().toURI().toString());
        loc.setLocalImageURL(null);
        open(0);
        assertEquals(pillShows(station), station, "the station's picture is not named");

        LocomotiveSettingsWindow window = SettingsWindow.showing();
        CurrentKeyRow.Pill pill = SettingsWindow.part("settings.pictureSource", CurrentKeyRow.Pill.class);

        assertTrue(Components.onTheEventThread(() ->
        {
            window.validate();
            pill.setText(station);

            return pill.isValid();
        }), "the same words laid the pill out again");
    }

    /** The file chooser on screen, once one is, with the window it hangs off in `owner[0]`. */
    private static javax.swing.JFileChooser chooserShowing(java.awt.Window[] owner) throws Exception
    {
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

        return chooser[0];
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
        return Reflect.get(TrainControlUI.class, ui, name);
    }

    /** Opens the window on a tab of the first train. */
    private static void open(int tab) throws Exception
    {
        open(loc, tab);
    }

    /** Opens the window on a tab of this train. */
    private static void open(Locomotive which, int tab) throws Exception
    {
        assertTrue(SettingsWindow.open(ui, which, LocomotiveSettingsWindow.Tab.values()[tab], null), "the window did not open");
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

    /** Save refuses: the consist of the first two trains cannot share an address, and the window stays open. */
    private static void refusedOverTheConsist(String notSaid) throws Exception
    {
        assertEquals(saveAndAnswer(), Arrays.asList(I18n.f("loc.errorLinkSameAddress", NAME, OTHER)), notSaid);
        assertNotNull(SettingsWindow.showing(), "the window closed as if saved");
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

    /**
     * A member's multiplier opens as the percent it was saved at, for figures whose double times 100 falls just short
     * (0.57 is 56.99...), and a Save of another change on the tab writes it back unchanged.
     *
     * MUTATION: in `MultiUnitPanel.buildRow`, cast `Math.abs(multiplierValue) * 100` to int again instead of rounding,
     * and 0.57 opens as 56 and is saved as 0.56.
     */
    @Test(timeOut = 120000)
    public void testAMultiplierOpensAndSavesAsItWasSet() throws Exception
    {
        for (double set : new double[] { 0.29, 0.57, 1.13 })
        {
            fixture.quiet();

            assertEquals(loc.setLinkedLocomotives(java.util.Collections.singletonMap(OTHER, set)), 1,
                "precondition: the consist was not made");

            assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsWindow.Tab.MULTI_UNIT, null), "the window did not open");
            assertNotNull(SettingsWindow.awaited(), "the window is not on screen");

            int shown = Components.onTheEventThread(() ->
            {
                javax.swing.JCheckBox member = Components.find(SettingsWindow.showing().getContentPane(),
                    javax.swing.JCheckBox.class, b -> OTHER.equals(b.getToolTipText()));

                return Components.find(member.getParent(), javax.swing.JSlider.class, s -> true).getValue();
            });

            assertEquals(shown, (int) Math.round(set * 100), "a member saved at " + set + " opened as " + shown + "%");

            // Another change on the tab, so that Save writes every member from the sliders
            SettingsWindow.tickMember(THIRD);
            SettingsWindow.press(SettingsWindow.save());

            assertTrue(Components.waitFor(() -> loc.getLinkedLocomotiveNames().containsKey(THIRD), WAIT),
                "precondition: Save did not make " + THIRD + " a member");
            assertEquals(loc.getLinkedLocomotiveNames().get(OTHER), set, 1e-9, "Save changed the multiplier set at " + set);
        }
    }

    /**
     * A station multi-unit's read-only list shows a member whose rename was answered No as ticked, at the station's
     * percent and reversed as the station has it.
     *
     * MUTATION: in `MultiUnitPanel.buildRow`, look the member up in the station's names by `loco.getName()` again.
     */
    @Test(timeOut = 120000)
    public void testAStationMultiUnitShowsAMemberWhoseRenameWasAnsweredNo() throws Exception
    {
        final String head = "LS station head";
        final String station = "LS station name";

        model.newMFXLocomotive(head, 3410);
        model.changeLocAddress(head, 3410, MarklinLocomotive.decoderType.MULTI_UNIT);

        try
        {
            ((MarklinLocomotive) model.getLocByName(head)).setModelMultiUnitLocomotives(
                java.util.Collections.singletonMap(station, -0.5));
            other.setDeclinedStationName(station);

            assertTrue(SettingsWindow.open(ui, model.getLocByName(head), LocomotiveSettingsWindow.Tab.MULTI_UNIT, null),
                "the window did not open");

            List<Boolean> shown = Components.onTheEventThread(() ->
            {
                javax.swing.JCheckBox member = Components.find(SettingsWindow.showing().getContentPane(),
                    javax.swing.JCheckBox.class, b -> OTHER.equals(b.getToolTipText()));

                List<javax.swing.JCheckBox> boxes = Components.all(member.getParent(), javax.swing.JCheckBox.class,
                    b -> true);

                return Arrays.asList(member.isSelected(), boxes.get(1).isSelected(),
                    Components.find(member.getParent(), javax.swing.JSlider.class, s -> true).getValue() == 50);
            });

            assertEquals(shown, Arrays.asList(true, true, true), "the member opened ticked, reversed and at 50%: "
                + shown);
        }
        finally
        {
            other.setDeclinedStationName(null);

            fixture.quiet();

            model.deleteLoc(head);
        }
    }

    /**
     * A head's own links are keyed by names here: a locomotive that declined a member's name at the station opens
     * unticked, and a Save of another change on the tab does not link it.
     *
     * MUTATION: in `MultiUnitPanel`'s `keyOf`, let a head's own link match a name the row's locomotive kept its own
     * over (`keepsItsNameOver`) as well as its name.
     */
    @Test(timeOut = 120000)
    public void testAnEditableListDoesNotTickALocomotiveThatDeclinedAMembersName() throws Exception
    {
        assertEquals(loc.setLinkedLocomotives(java.util.Collections.singletonMap(OTHER, 0.5)), 1,
            "precondition: the consist was not made");
        model.newMFXLocomotive("LS elsewhere", 3404);

        try
        {
            third.setDeclinedStationName(OTHER);
            open(3);

            boolean ticked = Components.onTheEventThread(() -> Components.find(SettingsWindow.showing().getContentPane(),
                javax.swing.JCheckBox.class, b -> THIRD.equals(b.getToolTipText())).isSelected());

            SettingsWindow.tickMember("LS elsewhere");
            SettingsWindow.press(SettingsWindow.save());

            assertTrue(Components.waitFor(() -> loc.getLinkedLocomotiveNames().containsKey("LS elsewhere"), WAIT),
                "precondition: Save did not make LS elsewhere a member");
            assertEquals(Arrays.asList(ticked, loc.getLinkedLocomotiveNames().containsKey(THIRD)), Arrays.asList(false, false),
                "a locomotive that declined a member's name [opened ticked, was linked by Save]");
        }
        finally
        {
            third.setDeclinedStationName(null);
        }
    }

    /**
     * A station multi-unit's read-only list shows the locomotive that holds a member's name, not one that declined it,
     * in either database order: the two trains swap roles.
     *
     * MUTATION: in `Locomotive.stationMember`, take the first locomotive in the list that holds or declined the name.
     */
    @Test(timeOut = 120000)
    public void testAStationMultiUnitShowsTheHolderOfANameOverItsDecliner() throws Exception
    {
        final String head = "LS station head";

        model.newMFXLocomotive(head, 3410);
        model.changeLocAddress(head, 3410, MarklinLocomotive.decoderType.MULTI_UNIT);

        try
        {
            for (MarklinLocomotive[] roles : new MarklinLocomotive[][] { { other, third }, { third, other } })
            {
                ((MarklinLocomotive) model.getLocByName(head)).setModelMultiUnitLocomotives(
                    java.util.Collections.singletonMap(roles[0].getName(), 0.5));
                roles[1].setDeclinedStationName(roles[0].getName());

                assertTrue(SettingsWindow.open(ui, model.getLocByName(head), LocomotiveSettingsWindow.Tab.MULTI_UNIT, null),
                    "the window did not open");

                List<String> rows = Components.onTheEventThread(() -> Components.all(
                    SettingsWindow.showing().getContentPane(), javax.swing.JCheckBox.class,
                    b -> OTHER.equals(b.getToolTipText()) || THIRD.equals(b.getToolTipText())).stream()
                    .map(b -> b.getToolTipText() + (b.isSelected() ? " ticked" : "")).collect(java.util.stream.Collectors.toList()));

                assertEquals(rows, Arrays.asList(roles[0].getName() + " ticked"), roles[1].getName() + " declined "
                    + roles[0].getName() + "'s name");

                roles[1].setDeclinedStationName(null);
                fixture.quiet();
            }
        }
        finally
        {
            other.setDeclinedStationName(null);
            third.setDeclinedStationName(null);
            fixture.quiet();
            model.deleteLoc(head);
        }
    }
}
