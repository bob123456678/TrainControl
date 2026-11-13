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
     * A year typed in another script's digits is saved as its value, as before the fields took digits only: full-width
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

        SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null);

        assertEquals(noteText(), I18n.f("loc.ui.settingsPartOfMultiUnit", OTHER, NAME, NAME),
            "a member's Multi-Unit tab does not say it is part of " + NAME);
    }

    /** The Multi-Unit tab's note for a member, as it reads. */
    private static String noteText()
    {
        return Components.onTheEventThread(() -> SettingsWindow.part("settings.multiUnitNote", javax.swing.JTextPane.class).getText());
    }

    /** Makes OTHER a member of NAME and opens OTHER's Multi-Unit tab, which carries the note. */
    private static void openTheMembersNote() throws Exception
    {
        SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null);
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
        SettingsWindow.tickMember(OTHER);
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getLinkedLocomotiveNames().containsKey(OTHER), WAIT),
            "precondition: Save did not make " + OTHER + " a member");

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null), "the window did not open");
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
                assertEquals((int) Components.onTheEventThread(() -> note.getCursor().getType()), java.awt.Cursor.HAND_CURSOR,
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

        LocomotiveSettingsFrame member = SettingsWindow.showing();

        assertEquals(SettingsWindow.text("settings.name"), OTHER);

        mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_RELEASED, true);

        assertTrue(Components.waitFor(() -> NAME.equals(SettingsWindow.text("settings.name")), WAIT),
            "the click did not open the multi-unit's settings");
        assertFalse(Components.onTheEventThread(member::isDisplayable), "the member's window was not closed");
        assertEquals(SettingsWindow.tabShowing(), 3, "the multi-unit's settings did not open on the Multi-Unit tab");

        // The keyboard
        SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null);
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

        return Components.onTheEventThread(() -> note.getCursor().getType()) == java.awt.Cursor.HAND_CURSOR;
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

        LocomotiveSettingsFrame member = SettingsWindow.showing();

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

        LocomotiveSettingsFrame.unsavedAnswerForTest = 2;
        mouseOnTheNote(java.awt.event.MouseEvent.MOUSE_RELEASED, true);

        assertTrue(SettingsWindow.showing() != null, "Cancel closed the window");
        assertEquals(SettingsWindow.text("settings.name"), "LS renamed", "Cancel did not leave the window as it was");

        LocomotiveSettingsFrame.unsavedAnswerForTest = 1;
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
     * A minimized window opened by another door comes back out of the taskbar.  With typing not saved, its question
     * asks over the window brought back, and blocks that window alone, so the main window's Stop stays live.
     *
     * MUTATION: drop `bringBack` from `LocomotiveSettingsFrame.settleUnsaved`, or ask with an application-modal
     * `showOptionDialog`, and this fails.
     */
    @Test
    public void testAMinimizedWindowComesBackAndAsksOverItself() throws Exception
    {
        open(0);

        LocomotiveSettingsFrame window = SettingsWindow.showing();

        support.AskingWindows.minimize(window, true);

        assertTrue(SettingsWindow.open(ui, other, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "precondition: it did not open");
        assertTrue(Components.waitFor(() -> (Components.onTheEventThread(window::getExtendedState) & java.awt.Frame.ICONIFIED) == 0,
            5000), "opened from another door, the window stayed in the taskbar");

        SettingsWindow.type("settings.name", "LS renamed");

        support.AskingWindows.minimize(window, true);

        try (support.Answering answering = support.Answering.start(message -> support.Answering.press(I18n.t("ui.cancel"))))
        {
            assertFalse(SettingsWindow.open(ui, third, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "Cancel went on to open it");

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

        LocomotiveSettingsFrame window = SettingsWindow.showing();

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

                Components.onTheEventThread(() -> ui.openLocomotiveSettings(third, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null));

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
                () -> ui.openLocomotiveSettings(other, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null)));

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

        Object windows = Reflect.get(TrainControlUI.class, ui, "editorWindows");

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
            LocomotiveSettingsFrame.unsavedAnswerForTest = 2;

            assertFalse(closeBy(byEscape), "the window closed over a blank address without asking");
        }

        // Save on the question is the same refusal, and the window stays
        LocomotiveSettingsFrame.unsavedAnswerForTest = 0;

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            assertFalse(closeBy(true), "Save on the question closed the window over a blank address");
            assertEquals(answering.asked(), Arrays.asList(I18n.t("loc.errorAddressNotNumeric")));
        }

        LocomotiveSettingsFrame.unsavedAnswerForTest = 1;

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
        return Reflect.get(TrainControlUI.class, ui, name);
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
