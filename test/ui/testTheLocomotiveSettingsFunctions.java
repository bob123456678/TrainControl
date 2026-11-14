package ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
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
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Mouse;
import support.SettingsWindow;

/**
 * The Functions tab of the Locomotive Settings window (FR-048): one table of the locomotive's functions on a buffer,
 * written only by Save.  The icon is a dropdown of the standard icons with a wrench beside it for the user's own image;
 * a standard icon picked again replaces the image; the preset, the behavior, Copy Icons and Reset All Customizations
 * all wait for Save; and a right-click on a row offers the icon, the image, the preset and the behavior.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 */
public class testTheLocomotiveSettingsFunctions
{
    private static final String NAME = "LF train";
    private static final String FROM = "LF source";
    private static final int F = 3;
    private static final long WAIT = 8000;

    private static support.SettingsFixture fixture;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static MarklinLocomotive loc;
    private static MarklinLocomotive source;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        fixture = support.SettingsFixture.open();

        model = fixture.model;
        ui = fixture.ui;

        // Reset All syncs with the station afterwards, as the application's own address: pointed at nowhere
        fixture.needPictures("lfunctions");

        Components.settle();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (fixture != null) fixture.finish(NAME, FROM);
        }
        finally
        {
            if (fixture != null) fixture.closeSandbox();
        }
    }

    @BeforeMethod
    public void lay() throws Exception
    {
        fixture.quiet();

        SettingsWindow.power(ui, model, false);

        fixture.forget(NAME, FROM);

        loc = model.newDCCLocomotive(NAME, 3601);
        source = model.newDCCLocomotive(FROM, 3602);

        SettingsWindow.showOnKeyA(ui, loc);

        // Nothing is on the clipboard unless a claim puts it there
        Components.onTheEventThread(() ->
        {
            ui.clearCopyTarget();

            return null;
        });

        open();
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        fixture.quiet();
    }

    // The table and its buffer

    /**
     * A row's icon dropdown opens a list with six columns of icons across, as the old function dialog's did, and the
     * preset button says Active (MT-924).
     *
     * MUTATION: take the popup-widening listener out of `IconCell`, and the list is two columns wide.
     */
    @Test
    public void testTheIconDropdownShowsSixColumnsAndThePresetButtonSaysActive() throws Exception
    {
        JComboBox<?> combo = SettingsWindow.inEditor(0, 1, "functions.iconCombo", JComboBox.class);

        showList(combo);

        Components.settle();

        int columns = Components.onTheEventThread(() ->
        {
            javax.swing.JList<?> list = Components.find(((javax.swing.plaf.basic.BasicComboPopup)
                combo.getAccessibleContext().getAccessibleChild(0)), javax.swing.JList.class, null);

            int across = 0;

            while (across < list.getModel().getSize() && list.getCellBounds(across, across).y == list.getCellBounds(0, 0).y)
            {
                across++;
            }

            return across;
        });

        assertEquals(columns, 6, "the icon dropdown does not show six columns of icons");

        assertEquals(SettingsWindow.part("functions.useCurrent", JButton.class).getText(), "Save Active Functions as Preset");
    }


    /**
     * Every opening of the icon dropdown, on the first row, a middle one and the last, in a window moved to the
     * screen's right edge and again to its bottom, shows six columns wholly on the screen (OB-446).
     *
     * MUTATION: take the `popup.validate()` and `scroll.doLayout()` out of `LocomotiveFunctionsTab.widen`, and the
     * second opening shows one column.
     */
    @Test
    public void testTheIconDropdownIsSixColumnsAndOnScreenEverywhere() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("needs a display");

        java.awt.Window window = SettingsWindow.showing();

        java.awt.Rectangle screen = window.getGraphicsConfiguration().getBounds();

        int last = loc.getNumF() - 1;

        for (boolean bottom : new boolean[]{false, true})
        {
            for (int row : new int[]{0, last / 2, last})
            {
                JComboBox<?> combo = SettingsWindow.inEditor(row, 1, "functions.iconCombo", JComboBox.class);

                Components.onTheEventThread(() ->
                {
                    java.awt.Point at = combo.getLocationOnScreen();

                    window.setLocation(bottom ? window.getX() : window.getX() + screen.x + screen.width - 120 - at.x,
                        bottom ? window.getY() + screen.y + screen.height - 60 - at.y : window.getY());

                    return null;
                });

                Components.settle();

                for (int opening = 0; opening < 2; opening++)
                {
                    showList(combo);

                    Components.settle();

                    int[] result = columnsAcross(combo);

                    String where = "row " + row + (bottom ? " at the bottom" : " at the right") + ", opening " + opening
                        + ": " + java.util.Arrays.toString(result) + " on " + screen;

                    assertEquals(result[0], 6, "the icon dropdown does not show six columns, " + where);
                    assertEquals(result[3], 1, "the icon dropdown runs past the screen, " + where);
                }
            }
        }
    }

    /**
     * The images of the user's own on the locomotive's other functions are offered in a row's icon dropdown after the
     * standard icons, and picking one gives the row that image (OB-446).
     *
     * MUTATION: leave `offerCustomImages` out of the popup listener, and the dropdown has the standard icons only.
     */
    @Test
    public void testTheIconDropdownOffersTheLocomotivesOwnImages() throws Exception
    {
        File picture = SettingsWindow.picture(fixture.folder, "teal.png", Color.CYAN);
        String url = picture.toPath().toUri().toString();

        loc.setLocalFunctionImageURL(F + 1, url);

        open();

        JComboBox<?> combo = SettingsWindow.inEditor(F, 1, "functions.iconCombo", JComboBox.class);
        int standard = loc.getNumFnIcons() + 1;

        showList(combo);

        Components.settle();

        assertEquals((int) Components.onTheEventThread(() -> combo.getModel().getSize()), standard + 1,
            "the dropdown does not offer the image on another function");

        Components.onTheEventThread(() ->
        {
            combo.setSelectedIndex(standard);

            return null;
        });

        Components.settle();

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> url.equals(loc.getLocalFunctionImageURL(F)), WAIT),
            "picking the image in the dropdown did not give it to the row");
        assertEquals(loc.getLocalFunctionImageURL(F + 1), url, "the other function lost its image");
    }

    /**
     * The dropdown's tail lists the images in use on any locomotive, each once and by file name: another locomotive's
     * image appears, a second spelling of one file collapses into it, a missing file and a file that will not load are
     * left out, and the row's own image is the one marked selected.  Picking one gives it to the row at Save and the
     * source locomotive is left as it was; Cancel writes nothing (OB-446).
     *
     * MUTATION: in `LocomotiveFunctionsTab.customChoices`, leave out the loop over `getLocomotives()`, and the other
     * locomotive's image is missing; leave out `imageKey`'s normalizing, and the duplicate stays; leave out `readable`,
     * and the missing file is offered.
     */
    @Test
    public void testTheIconDropdownListsEveryLocomotivesImagesOnceAndOnlyThoseThatLoad() throws Exception
    {
        File folder = new File(fixture.folder, "tail");

        assertTrue(folder.isDirectory() || folder.mkdirs());

        File red = SettingsWindow.picture(folder, "b-red.png", Color.RED);
        File blue = SettingsWindow.picture(folder, "a-blue.png", Color.BLUE);
        File broken = new File(folder, "c-broken.png");

        java.nio.file.Files.write(broken.toPath(), new byte[]{1, 2, 3, 4});

        String missing = new File(folder, "d-missing.png").toPath().toUri().toString();
        String redAgain = folder.toPath().toUri().toString() + "sub/../b-red.png";
        String redUrl = red.toPath().toUri().toString();

        loc.setLocalFunctionImageURL(F + 1, blue.toPath().toUri().toString());

        source.setLocalFunctionImageURL(2, redUrl);
        source.setLocalFunctionImageURL(3, redAgain);
        source.setLocalFunctionImageURL(4, missing);
        source.setLocalFunctionImageURL(5, broken.toPath().toUri().toString());

        String sourceKept = source.getLocalFunctionImageURLs().toString();

        open();

        JComboBox<?> own = SettingsWindow.inEditor(F + 1, 1, "functions.iconCombo", JComboBox.class);
        int standard = loc.getNumFnIcons() + 1;

        openTheList(own);

        // The file that will not load goes when its picture fails, a moment after the list opens
        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> own.getModel().getSize()) - standard == 2, WAIT),
            "the tail is not the blue and the red image once each: " + Components.onTheEventThread(
                () -> own.getModel().getSize() - standard));
        assertEquals((int) Components.onTheEventThread(own::getSelectedIndex), standard,
            "the row's own image is not the one marked");

        Components.onTheEventThread(() ->
        {
            own.hidePopup();

            return null;
        });

        pickTheRedImageIn(F);

        assertNull(loc.getLocalFunctionImageURL(F), "the image was written before Save");

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT));
        assertNull(loc.getLocalFunctionImageURL(F), "Cancel wrote the image");

        open();

        pickTheRedImageIn(F);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> redUrl.equals(loc.getLocalFunctionImageURL(F)), WAIT),
            "Save did not write the other locomotive's image");
        assertEquals(source.getLocalFunctionImageURLs().toString(), sourceKept, "the source locomotive was changed");
    }

    /** Opens a row's icon dropdown. */
    private static void showList(JComboBox<?> combo)
    {
        Components.onTheEventThread(() ->
        {
            combo.showPopup();

            return null;
        });
    }

    /** Opens a row's icon dropdown and lets its pictures arrive. */
    private static void openTheList(JComboBox<?> combo)
    {
        showList(combo);

        Components.settle();
        Components.settle();
    }

    /** Opens the row's dropdown and picks the second image of the tail, the red one. */
    private static void pickTheRedImageIn(int function)
    {
        JComboBox<?> combo = SettingsWindow.inEditor(function, 1, "functions.iconCombo", JComboBox.class);
        int standard = loc.getNumFnIcons() + 1;

        openTheList(combo);

        Components.onTheEventThread(() ->
        {
            combo.setSelectedIndex(standard + 1);

            return null;
        });

        Components.settle();
    }

    /** The open list's columns across, its size, whether it scrolls, and whether it is on the screen; then closes it. */
    private static int[] columnsAcross(JComboBox<?> combo)
    {
        return Components.onTheEventThread(() ->
        {
            javax.swing.plaf.basic.BasicComboPopup popup = (javax.swing.plaf.basic.BasicComboPopup)
                combo.getAccessibleContext().getAccessibleChild(0);

            javax.swing.JList<?> list = Components.find(popup, javax.swing.JList.class, null);

            int across = 0;

            while (across < list.getModel().getSize()
                && list.getCellBounds(across, across).y == list.getCellBounds(0, 0).y) across++;

            javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(
                javax.swing.JScrollPane.class, list);

            java.awt.Rectangle on = new java.awt.Rectangle(popup.getLocationOnScreen(), popup.getSize());
            java.awt.Rectangle screen = combo.getGraphicsConfiguration().getBounds();

            combo.hidePopup();

            return new int[]{across, list.getModel().getSize(), scroll.getVerticalScrollBar().isVisible() ? 1 : 0,
                screen.contains(on) ? 1 : 0};
        });
    }

    /**
     * With many custom images the dropdown still opens six columns across at every opening and scrolls, and it opens
     * fast on a database of hundreds of locomotives (OB-446).  The times are written to the scratch folder.
     *
     * MUTATION: make `customChoices` read every image's file, and the time bound fails; leave out the six-column
     * widening, and the columns fail.
     */
    @Test
    public void testTheIconDropdownWithManyImagesIsSixColumnsAndQuick() throws Exception
    {
        File folder = new File(fixture.folder, "many");

        assertTrue(folder.isDirectory() || folder.mkdirs());

        int pictures = 60;

        for (int i = 0; i < pictures; i++)
        {
            SettingsWindow.picture(folder, String.format("p%03d.png", i), new Color(i * 4, 0, 0));
        }

        java.util.List<String> names = new java.util.ArrayList<>();

        try
        {
            for (int n = 0; n < 300; n++)
            {
                String name = "LF many " + n;

                names.add(name);

                MarklinLocomotive extra = model.newDCCLocomotive(name, 3700 + n);

                for (int f = 0; f < 8; f++)
                {
                    extra.setLocalFunctionImageURL(f, new File(folder, String.format("p%03d.png", (n * 8 + f) % pictures))
                        .toPath().toUri().toString());
                }
            }

            open();

            JComboBox<?> combo = SettingsWindow.inEditor(0, 1, "functions.iconCombo", JComboBox.class);
            int standard = loc.getNumFnIcons() + 1;

            for (int opening = 0; opening < 2; opening++)
            {
                long[] took = new long[1];

                Components.onTheEventThread(() ->
                {
                    long start = System.nanoTime();

                    combo.showPopup();

                    took[0] = (System.nanoTime() - start) / 1000000;

                    return null;
                });

                Components.settle();
                Components.settle();

                int[] shape = columnsAcross(combo);

                java.nio.file.Files.write(new File(System.getenv("TC_SCRATCH"), "opening-time.txt").toPath(),
                    ("opening " + opening + ": " + took[0] + " ms, " + shape[1] + " entries\n").getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

                assertEquals(shape[1], standard + pictures, "opening " + opening + " does not list the " + pictures
                    + " images once each");
                assertEquals(shape[0], 6, "opening " + opening + " is not six columns across");
                assertEquals(shape[2], 1, "the list does not scroll");
                assertTrue(took[0] < 1500, "opening " + opening + " took " + took[0] + " ms");
            }
        }
        finally
        {
            SettingsWindow.closeIfOpen();

            fixture.forget(names.toArray(new String[0]));
        }
    }

    /**
     * The table has a row for each function the locomotive has, with the icon, the behavior, whether it is on now and
     * whether it is in the preset, as the locomotive holds them; nothing is written by looking.
     *
     * MUTATION: leave a row out of the model, or show the behavior of another function, and this fails.
     */
    @Test
    public void testTheTableShowsWhatTheLocomotiveHolds() throws Exception
    {
        loc.setFunctionType(F, 9, 2);
        loc.setFunctionType(F + 1, 4, Locomotive.FUNCTION_PULSE);
        loc.setF(F + 2, true);

        boolean[] preset = loc.getPreferredFunctions().clone();
        preset[F + 3] = true;
        loc.setPreferredFunctions(preset);

        open();

        JTable table = table();

        assertEquals((int) Components.onTheEventThread(table::getRowCount), loc.getNumF(), "the table lacks a function");

        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F, 0)), "F" + F);
        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F, 2)), "2s");
        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F + 1, 2)), I18n.t("function.ui.momentary"));
        assertEquals(Components.onTheEventThread(() -> table.getValueAt(0, 2)), I18n.t("function.ui.toggle"));
        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F + 3, 3)), Boolean.TRUE);
        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F, 3)), Boolean.FALSE);
    }

    /**
     * Picking a standard icon in a row's dropdown and a behavior in its cell changes the table only: the locomotive is
     * written by Save, and Cancel leaves it as it was.
     *
     * MUTATION: write the icon to the locomotive from the dropdown's handler, or from Cancel, and this fails.
     */
    @Test
    public void testTheDropdownAndTheBehaviorWaitForSave() throws Exception
    {
        int was = loc.getFunctionType(F);
        int icon = (was + 7) % 100 + 1;

        SettingsWindow.pickIcon(F, icon);
        SettingsWindow.pickBehavior(F, 1);

        assertEquals(loc.getFunctionType(F), was, "the dropdown wrote the icon before Save");
        assertFalse(loc.isFunctionPulse(F), "the behavior was written before Save");
        assertFalse(loc.isCustomFunctions(), "the locomotive was marked customized before Save");

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, WAIT));
        assertEquals(loc.getFunctionType(F), was, "Cancel wrote the icon");
        assertFalse(loc.isFunctionPulse(F), "Cancel wrote the behavior");

        open();

        SettingsWindow.pickIcon(F, icon);
        SettingsWindow.pickBehavior(F, 1);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getFunctionType(F) == icon, WAIT), "Save did not write the icon");
        assertTrue(loc.isFunctionPulse(F), "Save did not make F" + F + " momentary");
        assertTrue(loc.isCustomFunctions(), "a changed function does not mark the locomotive customized");
        assertEquals(loc.getFunctionType(F + 1), loc.sanitizeFIconIndex(loc.getFunctionType(F + 1)), "another function changed");
    }

    /**
     * Save is gray while nothing differs from what the locomotive holds, lit once a function's icon or behavior is
     * changed, and gray again once it is put back: closing then asks nothing, and a change is never left unsavable
     * (FR-098).
     *
     * MUTATION: leave `LocomotiveFunctionsTab.isDirty` answering true, or false, whatever the table shows, and this fails.
     */
    @Test
    public void testSaveFollowsWhetherAFunctionChanged() throws Exception
    {
        int was = loc.getFunctionType(F);
        int icon = (was + 7) % 100 + 1;

        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled),
            "Save is lit on a function shown exactly as the locomotive holds it");

        SettingsWindow.pickIcon(F, icon);

        assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled),
            "Save is gray with another icon chosen: closing would drop a change the operator cannot save");

        SettingsWindow.pickIcon(F, was);

        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled),
            "Save stays lit with the icon put back as it was");

        SettingsWindow.pickBehavior(F, 1);

        assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled),
            "Save is gray with another behavior chosen");

        SettingsWindow.pickBehavior(F, 0);

        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled),
            "Save stays lit with the behavior put back as it was");
    }

    /**
     * The wrench asks for the user's own image: the row shows it, the locomotive does not hold it until Save, and
     * Save writes it for that function only.
     *
     * MUTATION: leave `useOwnImage` out of the wrench's handler, or write it for every row, and this fails.
     */
    @Test
    public void testTheWrenchPicksAnImageThatSaveWrites() throws Exception
    {
        File picture = SettingsWindow.picture(fixture.folder, "green.png", Color.GREEN);
        String url = picture.toPath().toUri().toString();

        SettingsWindow.pickAnImage(F, picture);

        assertNull(loc.getLocalFunctionImageURL(F), "the image was written before Save");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> url.equals(loc.getLocalFunctionImageURL(F)), WAIT), "Save did not write the image");
        assertNull(loc.getLocalFunctionImageURL(F + 1), "the image went to another function");
        assertTrue(loc.isCustomFunctions());
    }

    /**
     * The preset ticks, Use Current Functions as Preset and Clear Preset change the table; Save writes the preset and
     * Cancel does not.
     *
     * MUTATION: write the preset from the buttons, or tick the functions that are off, and this fails.
     */
    @Test
    public void testThePresetWaitsForSave() throws Exception
    {
        loc.setF(1, true);
        loc.setF(F, true);

        assertTrue(Components.waitFor(() -> loc.getF(1) && loc.getF(F), WAIT));

        press("functions.useCurrent");

        JTable table = table();

        assertEquals(Components.onTheEventThread(() -> table.getValueAt(1, 3)), Boolean.TRUE);
        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F, 3)), Boolean.TRUE);
        assertEquals(Components.onTheEventThread(() -> table.getValueAt(2, 3)), Boolean.FALSE, "a function that is off was ticked");
        assertFalse(saved(1), "Use Current Functions as Preset wrote before Save");

        tick(2);

        press("functions.clearPreset");

        assertEquals(Components.onTheEventThread(() -> table.getValueAt(1, 3)), Boolean.FALSE, "Clear Preset left a tick");

        tick(F);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> saved(F), WAIT), "Save did not write the tick");
        assertFalse(saved(1) || saved(2), "Save wrote ticks that Clear Preset had cleared");
    }

    /**
     * A sync that changes an icon while the window is open survives a Save of one preset tick, and the locomotive does
     * not become customized, so later syncs still take the station's functions.
     *
     * MUTATION: write a row's icon whenever it differs from the locomotive now, not from what the row loaded, and
     * this fails.
     */
    @Test(timeOut = 120000)
    public void testASaveKeepsAnIconSyncedWhileTheWindowWasOpen() throws Exception
    {
        assertFalse(loc.isCustomFunctions(), "precondition: not customized");

        int synced = loc.getFunctionType(F) == 5 ? 6 : 5;
        int[] types = Arrays.copyOf(loc.getFunctionTypes(), loc.getFunctionTypes().length);

        types[F] = synced;

        // As syncWithCS2 adopts the station's types for a locomotive that is not customized
        loc.setFunctionTypes(types, loc.getFunctionTriggerTypes());

        assertEquals(loc.getFunctionType(F), synced, "precondition: the sync took");

        tick(2);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> saved(2), WAIT), "Save did not write the tick");
        assertEquals(loc.getFunctionType(F), synced, "Save put back the icon F3 had before the sync");
        assertFalse(loc.isCustomFunctions(), "Save of a preset tick marked the locomotive customized");
    }

    /**
     * A preset saved from the key menu while the window is open survives a Save that ticks another function: Save
     * changes only the ticks the operator changed.
     *
     * MUTATION: write every row's tick at Save instead of only the changed ones, and this fails.
     */
    @Test(timeOut = 120000)
    public void testASaveKeepsAPresetSavedFromTheKeyMenuWhileOpen() throws Exception
    {
        presetFromTheKeyMenu(5);

        tick(2);

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> saved(2), WAIT), "Save did not write the tick");
        assertTrue(saved(5), "Save replaced the preset saved from the key menu while the window was open");
    }

    /**
     * Clear Preset says what every row holds, so its Save clears a tick saved from the key menu while the window was
     * open, though that row still shows what it loaded.
     *
     * MUTATION: drop `wholePreset = true` from Clear Preset in `LocomotiveFunctionsTab.buttons`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testClearPresetClearsATickSavedFromTheKeyMenuWhileOpen() throws Exception
    {
        presetFromTheKeyMenu(5);

        // A tick in the table, as Clear Preset is gray with none
        tick(2);

        press("functions.clearPreset");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> !saved(5), WAIT), "Clear Preset and Save left the tick saved from the key menu");
    }

    /**
     * Use Current Functions as Preset says what every row holds too: a function now off leaves the preset, though the
     * key menu saved it while the window was open.
     *
     * MUTATION: drop `wholePreset = true` from Use Current in `LocomotiveFunctionsTab.buttons`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testUseCurrentDropsATickSavedFromTheKeyMenuWhileOpen() throws Exception
    {
        presetFromTheKeyMenu(5);

        loc.setF(5, false);

        assertTrue(Components.waitFor(() -> !loc.getF(5), WAIT));

        press("functions.useCurrent");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> !saved(5), WAIT), "Use Current and Save kept a function that is off in the preset");
    }

    /**
     * Copy Icons from the clipboard locomotive takes its functions into the table and Save writes them; with nothing
     * copied the button is gray.
     *
     * MUTATION: write the copy to the locomotive when the button is pressed, and this fails.
     */
    @Test
    public void testCopyIconsWaitsForSave() throws Exception
    {
        assertFalse(Components.onTheEventThread(SettingsWindow.part("functions.copyIcons", JButton.class)::isEnabled),
            "Copy Icons is lit with nothing copied");

        source.setFunctionType(F, 21, Locomotive.FUNCTION_PULSE);
        source.setLocalFunctionImageURL(F + 1, SettingsWindow.picture(fixture.folder, "red.png", Color.RED).toPath().toUri().toString());

        SettingsWindow.closeIfOpen();

        JButton keyA = SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_A);

        // The copy target is the key's locomotive: make it the source
        SettingsWindow.showOnKeyA(ui, source);

        Components.onTheEventThread(() ->
        {
            ui.setCopyTarget(keyA, false);

            return null;
        });

        SettingsWindow.showOnKeyA(ui, loc);

        open();

        JButton copy = SettingsWindow.part("functions.copyIcons", JButton.class);

        assertTrue(Components.onTheEventThread(copy::isEnabled), "Copy Icons stayed gray with a locomotive copied");
        assertEquals(Components.onTheEventThread(copy::getText), I18n.f("loc.ui.copyIconsFromLocomotive", FROM));

        press("functions.copyIcons");

        assertFalse(loc.isCustomFunctions(), "Copy Icons wrote before Save");
        assertNull(loc.getLocalFunctionImageURL(F + 1));

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getFunctionType(F) == source.getFunctionType(F), WAIT),
            "Save did not give F" + F + " the copied icon");
        assertTrue(loc.isFunctionPulse(F), "Save did not give F" + F + " the copied behavior");
        assertEquals(loc.getLocalFunctionImageURL(F + 1), source.getLocalFunctionImageURL(F + 1), "the copied image did not arrive");
        assertTrue(loc.isCustomFunctions());
    }

    /**
     * Reset All Customizations asks; No leaves it all; Yes marks it for Save, which gives the Central Station's
     * functions back: no custom flag and no images of your own.
     *
     * MUTATION: reset when the button is pressed, or skip the question, and this fails.
     */
    @Test
    public void testResetAllAsksAndWaitsForSave() throws Exception
    {
        loc.setFunctionType(F, 9, Locomotive.FUNCTION_TOGGLE);
        loc.setLocalFunctionImageURL(F + 1, SettingsWindow.picture(fixture.folder, "gray.png", Color.GRAY).toPath().toUri().toString());

        open();

        JButton reset = SettingsWindow.part("functions.resetAll", JButton.class);

        assertTrue(Components.onTheEventThread(reset::isEnabled), "Reset is gray for a customized locomotive");

        assertEquals(SettingsWindow.resetAll(I18n.t("ui.no")), Arrays.asList(I18n.t("loc.ui.confirmResetFunctionsToCentralStation")));

        Components.settle();

        assertTrue(loc.isCustomFunctions(), "No on the question reset the locomotive");

        SettingsWindow.resetAll(I18n.t("ui.yes"));

        Components.settle();

        assertTrue(loc.isCustomFunctions(), "Yes reset the locomotive before Save");
        assertNotNull(loc.getLocalFunctionImageURL(F + 1), "Yes took the image off before Save");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> !loc.isCustomFunctions(), WAIT), "Save left the locomotive customized");
        assertTrue(loc.getLocalFunctionImageURLs().isEmpty(), "Save left an image on a function");
    }

    // The row's right-click

    /**
     * A right-click on a row offers the icon, your own image, whether it is in the preset, and its behavior, and
     * nothing to clear an image; its choices change the table, and Save writes them.
     *
     * MUTATION: add Clear Custom Icon to the row menu, or leave the preset item out, and this fails.
     */
    @Test
    public void testARowsRightClickOffersTheIconTheImageThePresetAndTheBehavior() throws Exception
    {
        JTable table = table();

        JPopupMenu menu = rightClickRow(F);

        assertNotNull(menu, "a right click on a row opened no menu");

        List<String> texts = new java.util.ArrayList<>();

        for (Component c : menu.getComponents())
        {
            if (c instanceof JMenuItem) texts.add(((JMenuItem) c).getText());
        }

        assertEquals(texts, Arrays.asList(I18n.t("loc.ui.settingsMenuIcon"), I18n.t("loc.ui.settingsMenuCustomIcon"),
            I18n.t("loc.ui.settingsMenuInPreset"), I18n.t("loc.ui.settingsColumnBehavior")),
            "the row menu does not offer exactly the icon, the image, the preset and the behavior");

        assertEquals((int) Components.onTheEventThread(table::getSelectedRow), F, "the right-click did not select its row");

        JCheckBoxMenuItem inPreset = (JCheckBoxMenuItem) Components.withText(menu, I18n.t("loc.ui.settingsMenuInPreset"));

        Mouse.choose(inPreset);

        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F, 3)), Boolean.TRUE, "In Preset did not tick the row");
        assertFalse(saved(F), "In Preset wrote before Save");

        menu = rightClickRow(F);

        Mouse.choose((JMenuItem) Components.onTheEventThread(() -> ((JMenu) Components.withText(menu(), I18n.t("loc.ui.settingsColumnBehavior")))
            .getItem(1)));

        assertEquals(Components.onTheEventThread(() -> table.getValueAt(F, 2)), I18n.t("function.ui.momentary"),
            "the row menu's behavior did not reach the table");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> saved(F) && loc.isFunctionPulse(F), WAIT), "Save did not write the row menu's choices");
    }

    /**
     * The row menu's Icon submenu is a grid of the standard icons: choosing one changes the table, and Save writes it.
     *
     * MUTATION: leave `useStandardIcon` out of the grid's click handler, and this fails.
     */
    @Test
    public void testTheIconSubmenuPicksAStandardIcon() throws Exception
    {
        int was = loc.getFunctionType(F);
        int icon = (was + 9) % 100 + 1;

        JMenu submenu = (JMenu) Components.withText(rightClickRow(F), I18n.t("loc.ui.settingsMenuIcon"));

        javax.swing.JList<?> grid = Components.find(submenu.getPopupMenu(), javax.swing.JList.class,
            l -> "functions.menuIconGrid".equals(l.getName()));

        assertNotNull(grid, "the Icon submenu holds no grid of icons");

        Components.onTheEventThread(() ->
        {
            grid.setSize(520, 3000);

            java.awt.Rectangle cell = grid.getCellBounds(icon, icon);

            grid.dispatchEvent(new java.awt.event.MouseEvent(grid, java.awt.event.MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, cell.x + 2, cell.y + 2, 1, false, java.awt.event.MouseEvent.BUTTON1));

            return null;
        });

        Components.settle();

        assertEquals(loc.getFunctionType(F), was, "the grid wrote the icon before Save");

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getFunctionType(F) == icon, WAIT), "Save did not write the icon chosen in the grid");
    }

    /**
     * The row menu's Customize Icon... asks for an image like the wrench.
     *
     * MUTATION: leave `chooseOwnImage` out of the menu item, and this fails.
     */
    @Test
    public void testARowsMenuCustomizeIconAsksForAnImage() throws Exception
    {
        File picture = SettingsWindow.picture(fixture.folder, "yellow.png", Color.YELLOW);

        JPopupMenu menu = rightClickRow(F);

        try (Answering answering = Answering.start(message -> null).choosing(picture))
        {
            Mouse.choose((JMenuItem) Components.withText(menu, I18n.t("loc.ui.settingsMenuCustomIcon")));

            Components.waitFor(() -> false, 800);
        }

        Components.settle();

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> picture.toPath().toUri().toString().equals(loc.getLocalFunctionImageURL(F)), WAIT),
            "Customize Icon... did not give the row the image");
    }

    /**
     * The Copy Icons button follows the clipboard locomotive while the window is open.
     *
     * MUTATION: leave `fitCopyIcons` out of `LocomotiveFunctionsTab.refresh`, and this fails.
     */
    @Test
    public void testCopyIconsFollowsTheClipboardWhileOpen() throws Exception
    {
        Components.onTheEventThread(() ->
        {
            ui.clearCopyTarget();

            return null;
        });

        open();

        JButton copy = SettingsWindow.part("functions.copyIcons", JButton.class);

        assertFalse(Components.onTheEventThread(copy::isEnabled), "Copy Icons is lit with nothing copied");

        JButton keyA = SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_A);

        SettingsWindow.showOnKeyA(ui, source);

        Components.onTheEventThread(() ->
        {
            ui.setCopyTarget(keyA, false);
            ui.repaintLoc(true, null);

            return null;
        });

        final JButton live = copy;

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(live::isEnabled), WAIT), "Copy Icons did not light when something was copied");
        assertEquals(Components.onTheEventThread(live::getText), I18n.f("loc.ui.copyIconsFromLocomotive", FROM));
    }

    /**
     * A custom image that is missing is tried and logged once, however often the table paints.
     *
     * MUTATION: load the image afresh in `pictureOf` every time, and this fails.
     */
    @Test
    public void testAMissingImageIsLoggedOnce() throws Exception
    {
        String missing = new File(fixture.folder, "nothing-here.png").toPath().toUri().toString();

        loc.setLocalFunctionImageURL(F, missing);

        final java.util.List<String> logged = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final java.util.logging.Handler tap = support.LogTap.messagesInto(logged);
        final java.util.logging.Logger log = java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());

        log.addHandler(tap);

        try
        {
            open();

            JTable table = table();

            Runnable paint = () ->
            {
                Components.onTheEventThread(() ->
                {
                    ui.repaintLoc(true, null);
                    table.paintImmediately(table.getBounds());

                    return null;
                });

                Components.settle();
            };

            for (int i = 0; i < 3; i++) paint.run();

            // The image is fetched off the event thread: wait for the one failure, then paint again
            assertTrue(Components.waitFor(() ->
            {
                synchronized (logged)
                {
                    return logged.stream().anyMatch(m -> m.contains(I18n.f("loc.ui.errorInvalidIconPath", missing)));
                }
            }, WAIT), "the missing image was never tried");

            for (int i = 0; i < 4; i++) paint.run();
        }
        finally
        {
            log.removeHandler(tap);
        }

        long count = 0;

        synchronized (logged)
        {
            for (String m : logged) if (m.contains(I18n.f("loc.ui.errorInvalidIconPath", missing))) count++;
        }

        assertEquals(count, 1L, "the missing image was logged " + count + " times");
    }

    /**
     * The icons and the picture are fetched off the event thread: with neither in the image cache, opening the window
     * reads them all, and no image is read on the event thread.
     *
     * MUTATION: load the standard icons in one `invokeLater` on `ICON_LOADER` (every `getLocImage` on the event
     * thread), and this fails.
     */
    @Test
    public void testTheImagesAreFetchedOffTheEventThread() throws Exception
    {
        File shown = SettingsWindow.picture(fixture.folder, "fetched.png", Color.ORANGE);

        loc.setImageURL(shown.toPath().toUri().toString());

        boolean active = model.isCS3() || !model.getNetworkCommState();

        List<String> icons = new java.util.ArrayList<>();

        for (int i = 0; i <= loc.getNumFnIcons(); i++) icons.add(loc.getFunctionIconUrl(i, active, true) + TrainControlUI.BUTTON_ICON_WIDTH);

        java.util.Set<String> cached = TrainControlUI.getImageCache().keySet();

        cached.removeIf(key -> key.contains("FktIcon") || key.contains(shown.getName()));

        ReadWatch watch = new ReadWatch();

        try
        {
            open();

            assertTrue(Components.waitFor(() -> cached.containsAll(icons) && cached.stream().anyMatch(key -> key.contains(shown.getName())), WAIT),
                "precondition: the icons and the picture did not load");
        }
        finally
        {
            watch.close();
        }

        assertFalse(watch.reads.isEmpty(), "precondition: the watch saw no image read");
        assertFalse(watch.reads.contains(true), "an image was read on the event thread");
    }

    /**
     * Notes, for each image `ImageIO` reads while it is open, whether the read is on the event thread: it is asked first
     * whether it can decode every image, and answers no.
     */
    private static final class ReadWatch extends javax.imageio.spi.ImageReaderSpi implements AutoCloseable
    {
        final List<Boolean> reads = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        private final javax.imageio.spi.IIORegistry registry = javax.imageio.spi.IIORegistry.getDefaultInstance();

        ReadWatch()
        {
            vendorName = "test";
            version = "1";
            names = new String[] {"read watch"};
            inputTypes = STANDARD_INPUT_TYPE;

            registry.registerServiceProvider(this, javax.imageio.spi.ImageReaderSpi.class);

            java.util.Iterator<javax.imageio.spi.ImageReaderSpi> others =
                registry.getServiceProviders(javax.imageio.spi.ImageReaderSpi.class, false);

            while (others.hasNext())
            {
                javax.imageio.spi.ImageReaderSpi other = others.next();

                if (other != this) registry.setOrdering(javax.imageio.spi.ImageReaderSpi.class, this, other);
            }
        }

        @Override
        public boolean canDecodeInput(Object source)
        {
            reads.add(SwingUtilities.isEventDispatchThread());

            return false;
        }

        @Override
        public javax.imageio.ImageReader createReaderInstance(Object extension)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDescription(java.util.Locale locale)
        {
            return "read watch";
        }

        @Override
        public void close()
        {
            registry.deregisterServiceProvider(this, javax.imageio.spi.ImageReaderSpi.class);
        }
    }

    // Helpers

    private static void open() throws Exception
    {
        SettingsWindow.closeIfOpen();

        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.FUNCTIONS, null), "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");

        // The standard icons load after it opens
        Components.settle();
        Components.settle();
    }

    private static JTable table()
    {
        return SettingsWindow.part("functions.table", JTable.class);
    }

    private static JPopupMenu menu()
    {
        return Components.popupShowing();
    }

    private static JPopupMenu rightClickRow(int row)
    {
        JTable table = table();

        java.awt.Rectangle cell = Components.onTheEventThread(() -> table.getCellRect(row, 1, true));

        return Mouse.rightClickAt(table, new java.awt.Point(cell.x + cell.width / 2, cell.y + cell.height / 2), WAIT);
    }




    private static void tick(int row)
    {
        JCheckBox box = SettingsWindow.inEditor(row, 3, null, JCheckBox.class);

        Components.onTheEventThread(() ->
        {
            box.doClick(0);

            return null;
        });

        Components.settle();
    }


    private static void press(String name)
    {
        AbstractButton b = SettingsWindow.part(name, JButton.class);

        assertNotNull(b, "the tab has no " + name);

        SettingsWindow.press(b);
    }

    /** F on and saved as the preset from the key menu, with the window open. */
    private static void presetFromTheKeyMenu(int f)
    {
        loc.setF(f, true);

        assertTrue(Components.waitFor(() -> loc.getF(f), WAIT));

        loc.savePreferredFunctions();

        assertTrue(saved(f), "precondition: the key menu's preset took");
    }

    private static boolean saved(int function)
    {
        boolean[] preset = loc.getPreferredFunctions();

        return preset != null && preset.length > function && preset[function];
    }

}
