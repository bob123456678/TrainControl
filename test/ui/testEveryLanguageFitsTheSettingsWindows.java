package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JPopupMenu;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.LocomotiveSettingsWindow;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;
import support.AskingWindows;
import support.Components;
import support.FrozenEditor;
import support.LanguageFit;
import support.Reflect;
import support.SettingsWindow;
import support.StationDoors;

/**
 * `testEveryLanguageFits` for the two settings windows, filled from the frozen railway: Locomotive Settings on every tab
 * for a locomotive with functions and a multi-unit (and Add Locomotive), and Station Settings on both tabs with a train
 * on the station.  Each is built in each language at the size it opens at and measured by `support.LanguageFit`.
 *
 * MUTATION: give Done Editing in `StationWindowBase` a fixed preferred width of 20 pixels, and the Station Settings claim
 * fails in every language.
 */
public class testEveryLanguageFitsTheSettingsWindows
{
    private static final String PAGE = "1 - Main";
    private static final String TRAIN = "Fit train";
    private static final String MEMBER = "Fit member";

    private static final LanguageFit.Ledger LEDGER = new LanguageFit.Ledger();

    private static LanguageFit.Railway railway;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        railway = LanguageFit.Railway.open("live-snapshot");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            SettingsWindow.closeIfOpen();

            for (String name : new String[] {TRAIN, MEMBER}) if (railway.model != null && railway.model.getLocByName(name) != null) railway.model.deleteLoc(name);
        }
        finally
        {
            if (railway != null) railway.close(LEDGER, "settings");
        }
    }

    /**
     * Locomotive Settings, every tab, and Add Locomotive fit in every language; the Functions table's headings and a
     * row's right-click menu are measured too, with the Danish Preset heading and In Preset item named.
     *
     * MUTATION: give the Functions table's Preset column a fixed width of 30 pixels, and this fails in every language.
     */
    @Test(timeOut = 240000)
    public void testLocomotiveSettingsFitsInEveryLanguage() throws Exception
    {
        MarklinLocomotive train = aMultiUnitWithFunctions();
        List<String> unseen = new ArrayList<>();

        Map<String, List<String>> clipped = LanguageFit.everyLanguage(language ->
        {
            assertTrue(SettingsWindow.open(railway.ui, train, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null), "the window did not open");

            LocomotiveSettingsWindow window = SettingsWindow.awaited();

            assertNotNull(window, "Locomotive Settings is not on screen");

            List<String> found = LanguageFit.measureNow(LEDGER, "loc-settings", language, window, 30);
            List<String> menu = Components.onTheEventThread(() -> LanguageFit.popupMenu(rowMenu(window),
                "of a function row", LanguageFit.menuCeiling(window), found));

            if ("da".equals(language))
            {
                if (!LEDGER.saw("loc-settings", language, I18n.t("loc.ui.settingsColumnPreset"))) unseen.add("da Preset heading");
                if (!menu.contains(I18n.t("loc.ui.settingsMenuInPreset"))) unseen.add("da In Preset item");
            }

            SettingsWindow.closeIfOpen();

            Components.press((javax.swing.AbstractButton) Reflect.get(TrainControlUI.class, railway.ui, "addLocomotiveMenuItem"));

            LocomotiveSettingsWindow adding = SettingsWindow.awaited();

            assertNotNull(adding, "Add Locomotive opened no window");

            found.addAll(LanguageFit.measureNow(LEDGER, "add-locomotive", language, adding, 5));

            SettingsWindow.closeIfOpen();

            return found;
        });

        assertTrue(unseen.isEmpty(), "named labels were never measured: " + unseen);

        LanguageFit.assertNothingClipped("loc-settings", clipped);
    }

    /**
     * Station Settings, its settings tab with the station row table and its train tab with a train on the station, fits
     * in every language; the German row headings, the German and Polish Arrived and the Polish Presets are named.
     *
     * MUTATION: see the class.
     */
    @Test(timeOut = 240000)
    public void testStationSettingsFitsInEveryLanguage() throws Exception
    {
        List<String> unseen = new ArrayList<>();

        Map<String, List<String>> clipped = LanguageFit.everyLanguage(language ->
        {
            FrozenEditor.putBack(railway.ui, railway.session, railway.asOpened);

            LayoutEditor editor = FrozenEditor.editorOn(railway.model, railway.ui, railway.session, PAGE);

            try
            {
                TileKey station = aStationWithATrain();

                // A priority, so the Advanced heading wears its * (FR-226)
                Components.onTheEventThread(() -> { railway.session.setPointProperty(station, "priority", 5); return null; });

                StationSettingsWindow window = StationDoors.open(Components.onTheEventThread(editor::getAutonomyPanel),
                    station);

                List<String> found = LanguageFit.measureNow(LEDGER, "station-settings", language, window, 20);

                List<String> named = new ArrayList<>();

                if (!LEDGER.saw("station-settings", language, I18n.t("autosetup.ui.stationSectionAdvanced") + " *"))
                {
                    unseen.add(language + " the starred Advanced heading");
                }

                if ("de".equals(language))
                {
                    for (String key : new String[] {"autosetup.ui.stationRowSentHere", "autosetup.ui.stationRowSentThrough",
                        "autosetup.ui.stationRowSentFrom"}) named.add(key);
                }

                if ("de".equals(language) || "pl".equals(language)) named.add("autosetup.ui.trainArrived");
                if ("pl".equals(language)) named.add("autosetup.ui.stationPresets");

                for (String key : named)
                {
                    if (!LEDGER.saw("station-settings", language, I18n.t(key))) unseen.add(language + " " + key);
                }

                StationDoors.close(window);

                return found;
            }
            finally
            {
                FrozenEditor.dispose(editor);
            }
        });

        assertTrue(unseen.isEmpty(), "named labels were never measured: " + unseen);

        LanguageFit.assertNothingClipped("station-settings", clipped);
    }

    /** A locomotive with named functions, made the head of a multi-unit through its own Multi-Unit tab. */
    private static MarklinLocomotive aMultiUnitWithFunctions() throws Exception
    {
        for (String name : new String[] {TRAIN, MEMBER}) if (railway.model.getLocByName(name) != null) railway.model.deleteLoc(name);

        MarklinLocomotive train = railway.model.newMFXLocomotive(TRAIN, 3611);

        railway.model.newMFXLocomotive(MEMBER, 3612);

        for (int f = 0; f < 6; f++) train.setFunctionType(f, f + 1, 0);

        SettingsWindow.power(railway.ui, railway.model, false);

        assertTrue(SettingsWindow.open(railway.ui, train, LocomotiveSettingsWindow.Tab.MULTI_UNIT, null), "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");

        SettingsWindow.tickMember(MEMBER);
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> train.getLinkedLocomotiveNames().containsKey(MEMBER), 8000),
            "precondition: Save did not make " + MEMBER + " a member");

        SettingsWindow.closeIfOpen();

        return train;
    }

    /** The Functions table's right-click menu for its first row, as a right-click builds it. */
    private static JPopupMenu rowMenu(LocomotiveSettingsWindow window) throws Exception
    {
        Class<?> tab = Class.forName("org.traincontrol.gui.LocomotiveFunctionsTab");
        Object functions = Components.find(window.getContentPane(), java.awt.Component.class, tab::isInstance);

        assertNotNull(functions, "Locomotive Settings has no Functions tab");

        List<?> rows = (List<?>) Reflect.method(tab, "everyRow").invoke(functions);

        assertFalse(rows.isEmpty(), "precondition: the Functions table has no rows");

        Class<?> row = Class.forName("org.traincontrol.gui.LocomotiveFunctionsTab$Row");

        return (JPopupMenu) Reflect.method(tab, "rowMenu", row).invoke(functions, rows.get(0));
    }

    /** A station on the page with the setup's own train standing on it. */
    private static TileKey aStationWithATrain()
    {
        return railway.session.trainsWhereTheyStand().keySet().stream()
            .filter(square -> PAGE.equals(square.getPage()) && railway.session.getStore().isStation(square)
                && railway.session.trainsWhereTheyStand().get(square).equals(railway.session.getLocomotiveNameAt(square)))
            .findFirst().orElseThrow(() -> new AssertionError("precondition: no train stands on a station of " + PAGE));
    }
}
