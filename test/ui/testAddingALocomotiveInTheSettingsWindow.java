package ui;

import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.SettingsWindow;

/**
 * Add Locomotive is the Locomotive Settings window on Basic Settings with blank fields and Add for Save (FR-179), with
 * the refusals the retired Add Locomotive window asked, in its order; Check Duplicates is on Basic Settings for every
 * locomotive and writes the report it wrote.
 */
public class testAddingALocomotiveInTheSettingsWindow extends support.SettingsClass
{
    private static final String MADE = "AD made";
    private static final String MFX_A = "AD mfx a";
    private static final String MFX_B = "AD mfx b";
    private static final String DCC_A = "AD dcc a";
    private static final String DCC_B = "AD dcc b";
    private static final String ALONE = "AD alone";

    private static final long WAIT = 10000;

    @Override
    protected String[] made()
    {
        return new String[] { MADE, MFX_A, MFX_B, DCC_A, DCC_B, ALONE };
    }

    private static void openAdd() throws Exception
    {
        fixture.quiet();

        assertTrue(Components.onTheEventThread(ui::openAddLocomotive), "Add Locomotive did not open");
        assertNotNull(SettingsWindow.awaited(), "Add Locomotive is not on screen");
    }

    /** Types into Add, picks the decoder, presses a button, and gives what it said once it has said something. */
    private static List<String> type(String name, String address, decoderType type, String press) throws Exception
    {
        SettingsWindow.type("settings.name", name);
        SettingsWindow.type("settings.address", address);
        Components.select(SettingsWindow.part("settings.decoder", JComboBox.class), type);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(SettingsWindow.part(press, JButton.class));

            assertTrue(answering.awaitAsked(1, WAIT), press + " said nothing");

            Components.settle();

            return answering.asked();
        }
    }

    /**
     * Add Locomotive opens the window on its first tab alone, titled Add Locomotive, with blank fields, DCC chosen as the
     * old window chose it, the three decoders it offered, and Add for Save.
     *
     * MUTATION: offer every decoder in Add, as Save does (`decoderType.values()` in `LocomotiveTab`), and this fails.
     */
    @Test
    public void testAddOpensBlankWithAddForSave() throws Exception
    {
        openAdd();

        LocomotiveSettingsFrame window = SettingsWindow.showing();

        assertEquals(Components.onTheEventThread(window::getTitle), I18n.t("loc.ui.addLocomotive"));
        assertEquals(Components.onTheEventThread(() -> window.tabsForTest().getTabCount()), Integer.valueOf(1),
            "Add shows more than Basic Settings");
        assertEquals(SettingsWindow.text("settings.name"), "", "the name is not blank");
        assertEquals(SettingsWindow.text("settings.address"), "", "the address is not blank");

        JComboBox<?> decoder = SettingsWindow.part("settings.decoder", JComboBox.class);

        assertEquals(Components.onTheEventThread(decoder::getSelectedItem), decoderType.DCC, "Add does not start on DCC");
        assertEquals(Components.onTheEventThread(decoder::getItemCount), Integer.valueOf(3),
            "Add offers other decoders than MFX, MM2 and DCC");
        assertEquals(SettingsWindow.save().getText(), I18n.t("ui.add"), "the button does not say Add");
        assertTrue(SettingsWindow.save().isEnabled(), "Add is gray with nothing typed, so a blank name is never refused");
        assertNull(window.getLocomotive(), "Add is showing a locomotive");
    }

    /**
     * Add asks what the old window asked, in its order: the name (missing, too long, taken, not usable in a route) before
     * the address (not a number, out of the decoder's range).  Each refusal makes nothing and leaves Add open.
     *
     * MUTATION: drop the missing-name refusal from `LocomotiveActions.addLocomotive`, and the blank name with a bad
     * address is refused for its address and this fails.
     */
    @Test
    public void testAddRefusesWhatAddLocomotiveRefused() throws Exception
    {
        fixture.forget(MADE, ALONE);

        openAdd();

        String tooLong = "AD" + new String(new char[TrainControlUI.MAX_LOC_NAME_DATABASE]).replace('\0', 'x');

        Object[][] cases = {
            { " ", "x", decoderType.DCC, I18n.t("loc.errorNameMissing") },
            { tooLong, "12", decoderType.DCC, I18n.f("loc.errorNameTooLong", TrainControlUI.MAX_LOC_NAME_DATABASE) },
            { "AD a,b", "12", decoderType.DCC, I18n.t("loc.ui.errorLocomotiveNameUnusable") },
            { MADE, "x12", decoderType.DCC, I18n.t("loc.errorAddressNotNumeric") },
            { MADE, "0x10", decoderType.DCC, I18n.t("loc.errorAddressNotNumeric") },
            { MADE, "0", decoderType.MM2, I18n.t("loc.errorMM2AddressOutOfRange") },
            { MADE, "81", decoderType.MM2, I18n.t("loc.errorMM2AddressOutOfRange") },
            { MADE, "2049", decoderType.DCC, I18n.t("loc.errorDCCAddressOutOfRange") },
            { MADE, "0x4000", decoderType.MFX, I18n.t("loc.errorMFXAddressOutOfRange") },
        };

        for (Object[] each : cases)
        {
            List<String> said = type((String) each[0], (String) each[1], (decoderType) each[2], "settings.save");

            assertEquals(said, java.util.Collections.singletonList(each[3]), "Add with \"" + each[0] + "\" at \""
                + each[1] + "\" said something else");
            assertNull(fixture.model.getLocByName((String) each[0]), "a refused Add made " + each[0]);
            assertNull(SettingsWindow.showing().getLocomotive(), "a refused Add left Add Locomotive");
        }

        // A name already taken
        fixture.model.newDCCLocomotive(ALONE, 1907);

        assertEquals(type(ALONE, "12", decoderType.DCC, "settings.save"),
            java.util.Collections.singletonList(I18n.t("loc.errorNameExists")), "a taken name was not refused");
        assertEquals(fixture.model.getLocByName(ALONE).getAddress(), 1907, "a refused Add changed the locomotive of that name");
    }

    /**
     * Add makes the locomotive, says so, and the window then shows it with all five tabs and Save.
     *
     * MUTATION: answer true without `aim` in `LocomotiveSettingsFrame.add`, and the window stays on Add and this fails.
     */
    @Test
    public void testAddMakesTheLocomotiveAndShowsIt() throws Exception
    {
        fixture.forget(MADE);

        openAdd();

        assertEquals(type(MADE, "0xCE5", decoderType.MFX, "settings.save"),
            java.util.Collections.singletonList(I18n.t("loc.infoAddedSuccessfully")), "Add did not say it added");

        Locomotive made = fixture.model.getLocByName(MADE);

        assertNotNull(made, "Add made no locomotive");
        assertEquals(made.getAddress(), 0xCE5, "Add gave the wrong address");
        assertEquals(made.getDecoderType(), decoderType.MFX, "Add gave the wrong decoder");

        LocomotiveSettingsFrame window = SettingsWindow.showing();

        assertNotNull(window, "Add closed the window");
        assertEquals(window.getLocomotive(), made, "the window does not show the locomotive Add made");
        assertEquals(Components.onTheEventThread(() -> window.tabsForTest().getTabCount()), Integer.valueOf(5),
            "the new locomotive is shown without all its tabs");
        assertEquals(Components.onTheEventThread(window::getTitle), I18n.f("loc.ui.settingsTitle", MADE));
        assertEquals(SettingsWindow.save().getText(), I18n.t("loc.ui.settingsSave"), "the button still says Add");
    }

    /**
     * Add is refused while autonomy runs whichever door opened the window, here the locomotive database's Add
     * Locomotive button, before the run began: autonomy works through the database, as the File menu's door says.
     *
     * MUTATION: drop the `refuseWhileAutonomyRunning` from `LocomotiveActions.addLocomotive`, and this fails.
     */
    @Test
    public void testAddIsRefusedWhileAutonomyRuns() throws Exception
    {
        fixture.forget(MADE);

        fixture.quiet();

        javax.swing.AbstractButton button = Components.withText(ui.getLocSelector(), I18n.t("loc.ui.addLocomotive"));

        assertNotNull(button, "the locomotive database has no Add Locomotive button");

        Components.press(button);

        assertNotNull(SettingsWindow.awaited(), "the database's Add Locomotive opened no window");

        fixture.model.getAutoLayout().setStagingInProgress(true);

        try
        {
            assertTrue(fixture.model.isAutonomyRunning(), "precondition: the model does not read autonomy as running");

            assertEquals(type(MADE, "12", decoderType.DCC, "settings.save"),
                java.util.Collections.singletonList(I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")),
                "Add was not refused while autonomy runs");

            assertNull(fixture.model.getLocByName(MADE), "Add made a locomotive while autonomy runs");
        }
        finally
        {
            fixture.model.getAutoLayout().setStagingInProgress(false);
        }
    }

    /**
     * The address says how it may be typed, as the retired Add Locomotive window's did: an (i) beside it on Basic
     * Settings.
     *
     * MUTATION: leave the address's (i) out of `LocomotiveTab`, and this fails.
     */
    @Test
    public void testTheAddressSaysHowToTypeIt() throws Exception
    {
        openAdd();

        javax.swing.JLabel hint = SettingsWindow.part("settings.addressHint", javax.swing.JLabel.class);

        assertEquals(Components.onTheEventThread(hint::getToolTipText),
            org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.wrapped(I18n.t("loc.ui.settingsAddressHint")),
            "the address's (i) does not say how an address is typed");
    }

    /**
     * Check Duplicates on Basic Settings writes the old window's report: under the MFX heading each shared MFX address's
     * locomotives and its separator, under the other heading the rest's, then the summary.  Asked of an Add and of a
     * locomotive's own settings, which counts only other locomotives at its address as using it.
     *
     * MUTATION: count the locomotive itself among those using its address in `LocomotiveActions.checkDuplicates`, and
     * this fails.
     */
    @Test
    public void testCheckDuplicatesWritesTheOldReport() throws Exception
    {
        fixture.forget(MFX_A, MFX_B, DCC_A, DCC_B, ALONE);

        MarklinControlStation model = fixture.model;

        Locomotive mfxA = model.newMFXLocomotive(MFX_A, 0xCE7);
        Locomotive mfxB = model.newMFXLocomotive(MFX_B, 0xCE7);
        Locomotive dccA = model.newDCCLocomotive(DCC_A, 1901);
        Locomotive dccB = model.newDCCLocomotive(DCC_B, 1901);
        Locomotive alone = model.newDCCLocomotive(ALONE, 1907);

        openAdd();

        List<String> logged = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.logging.Handler tap = support.LogTap.messagesInto(logged);
        java.util.logging.Logger log = java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());

        log.addHandler(tap);

        try
        {
            assertEquals(type("", "1901", decoderType.DCC, "settings.checkDuplicates"),
                java.util.Collections.singletonList(I18n.t("loc.errorAddressInUse")), "a shared address is not in use");

            List<String> report = new java.util.ArrayList<>(logged);

            int mfx = report.indexOf(I18n.t("loc.reportMfxHeader"));
            int rest = report.indexOf(I18n.t("loc.reportNonMfxHeader"));
            int end = report.indexOf(I18n.t("loc.reportDuplicateSummary"));

            assertTrue(mfx >= 0 && mfx < rest && rest < end, "the report's headings are missing or out of order: " + report);

            assertShared(report.subList(mfx, rest), I18n.f("loc.reportAddressSeparator", 0xCE7), mfxA, mfxB);
            assertShared(report.subList(rest, end), I18n.f("loc.reportAddressSeparator", 1901), dccA, dccB);

            // And from a locomotive's own settings: its own address is free when no other uses it
            fixture.quiet();
            fixture.show(alone, LocomotiveSettingsFrame.Tab.LOCOMOTIVE);

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                Components.press(SettingsWindow.part("settings.checkDuplicates", JButton.class));

                assertTrue(answering.awaitAsked(1, WAIT), "Check Duplicates said nothing");
                assertEquals(answering.asked(), java.util.Collections.singletonList(I18n.t("loc.infoAddressFree")),
                    "a locomotive's own address counted as used by another");
            }
        }
        finally
        {
            log.removeHandler(tap);
        }
    }

    /** The two locomotives' lines, in either order, then the separator, inside one half of the report. */
    private static void assertShared(List<String> half, String separator, Locomotive one, Locomotive other)
    {
        java.util.Set<String> pair = new java.util.HashSet<>(java.util.Arrays.asList(line(one), line(other)));

        for (int at = 2; at < half.size(); at++)
        {
            if (separator.equals(half.get(at)) && pair.equals(new java.util.HashSet<>(half.subList(at - 2, at)))) return;
        }

        throw new AssertionError("the report does not list " + one.getName() + " and " + other.getName()
            + " above \"" + separator + "\": " + half);
    }

    private static String line(Locomotive l)
    {
        return "\t" + l.getName() + " [" + l.getDecoderTypeLabel() + "]";
    }
}
