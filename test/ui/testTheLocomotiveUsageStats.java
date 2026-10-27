package ui;

import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.LocomotiveStats;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * The locomotive statistics tab as the operator uses it: the table and its totals, the filter, Refresh, the usage
 * graph button, and Export Raw Data through the file chooser.
 *
 * The test database's other locomotives have their run times set aside while the class runs, so every figure here is
 * the one the class laid down: runtimes are milliseconds, and the dates are counted back from today.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 *
 * @author Adam
 */
public class testTheLocomotiveUsageStats
{
    private static final String ALPHA = "Stats alpha";
    private static final String BRAVO = "Stats bravo";
    private static final String CHARLIE = "Stats charlie";
    private static final String DELTA = "Stats delta";
    private static final String UNIT = "Stats unit";

    private static final String[] NAMES = {ALPHA, BRAVO, CHARLIE, DELTA, UNIT};

    private static final long WAIT = 8000;

    /** An hour, a minute, in milliseconds, which is what a locomotive's runtime is kept in. */
    private static final long HOUR = 3600000L;
    private static final long MINUTE = 60000L;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static JPanel stats;
    private static String folderWas;
    private static Set<Window> windowsAtStart;

    private static final Map<Locomotive, Map<String, Long>> setAside = new HashMap<>();

    private static MarklinLocomotive alpha;
    private static MarklinLocomotive bravo;
    private static MarklinLocomotive charlie;
    private static MarklinLocomotive delta;
    private static MarklinLocomotive unit;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the statistics are on a window");

        sandbox = support.LayoutSandbox.open();

        folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        model = support.TestStation.simulatedWithWindowInDebug();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        stats = (JPanel) field(ui, "stats");

        windowsAtStart = showingWindows();

        Components.settle();

        for (String name : NAMES)
        {
            if (model.getLocByName(name) != null) model.deleteLoc(name);
        }

        for (Locomotive other : model.getLocomotives())
        {
            setAside.put(other, new HashMap<>(other.getHistoricalOperatingTime()));

            other.getHistoricalOperatingTime().clear();
        }

        alpha = model.newMFXLocomotive(ALPHA, 3301);
        bravo = model.newMFXLocomotive(BRAVO, 3302);
        charlie = model.newMFXLocomotive(CHARLIE, 3303);
        delta = model.newMFXLocomotive(DELTA, 3304);
        unit = model.newDCCLocomotive(UNIT, 2);

        model.changeLocAddress(UNIT, 101, MarklinLocomotive.decoderType.MULTI_UNIT);

        unit = (MarklinLocomotive) model.getLocByName(UNIT);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (folderWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
            else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, folderWas);

            if (model != null)
            {
                for (String name : NAMES)
                {
                    if (model.getLocByName(name) != null) model.deleteLoc(name);
                }

                for (Map.Entry<Locomotive, Map<String, Long>> back : setAside.entrySet())
                {
                    back.getKey().getHistoricalOperatingTime().clear();
                    back.getKey().getHistoricalOperatingTime().putAll(back.getValue());
                }
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() ->
                {
                    closing.stopDrivingTrainsFromTheWholeWindow();
                    closing.dispose();
                });
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Alpha ran 1 hour today, half an hour yesterday and 10 minutes three days ago; Bravo 2 minutes today and 90 minutes
     * forty days ago; Delta has only a record that it was started today; Charlie and the multi-unit never ran.
     */
    @BeforeMethod
    public void lay() throws Exception
    {
        closeStrayWindows();

        for (MarklinLocomotive loc : new MarklinLocomotive[] {alpha, bravo, charlie, delta, unit})
        {
            loc.getHistoricalOperatingTime().clear();
        }

        alpha.getHistoricalOperatingTime().put(day(0), HOUR);
        alpha.getHistoricalOperatingTime().put(day(1), 30 * MINUTE);
        alpha.getHistoricalOperatingTime().put(day(3), 10 * MINUTE);

        bravo.getHistoricalOperatingTime().put(day(0), 2 * MINUTE);
        bravo.getHistoricalOperatingTime().put(day(40), 90 * MINUTE);

        delta.getHistoricalOperatingTime().put(day(0), 0L);

        // The panel's own refresh, so the Refresh button's claim is the only one that needs the button
        ((LocomotiveStats) stats).refresh();

        Components.settle();
    }

    /**
     * The table has a row for every locomotive, the one that ran most first, and each row says its overall and today's
     * runtime, the last and first dates it ran, and on how many days; a locomotive that never ran says so.
     *
     * MUTATION: swap `l1` and `l2` in the sort of `sortedLocs` in `LocomotiveStats.refresh`, and the order fails.
     */
    @Test
    public void testTheTableListsEveryLocomotiveMostRunFirst() throws Exception
    {
        JTable table = table();

        assertEquals(rowOf(table, CHARLIE) >= 0, true, "the table has no row for a locomotive that never ran");

        assertEquals(cell(table, ALPHA, 1), "1:40:00", "alpha's overall runtime is not 60 + 30 + 10 minutes");
        assertEquals(cell(table, ALPHA, 2), "1:00:00", "alpha's runtime today is not the hour it ran today");
        assertEquals(cell(table, ALPHA, 3), day(0), "alpha's last run is not today");
        assertEquals(cell(table, ALPHA, 4), day(3), "alpha's first run is not three days ago");
        assertEquals(cell(table, ALPHA, 5), "3", "alpha ran on three days");

        assertEquals(cell(table, BRAVO, 1), "1:32:00", "bravo's overall runtime is not 2 + 90 minutes");
        assertEquals(cell(table, BRAVO, 4), day(40), "bravo's first run is not forty days ago");
        assertEquals(cell(table, BRAVO, 5), "2", "bravo ran on two days");

        assertEquals(cell(table, DELTA, 1), "0:00:00", "a locomotive that was only started has run time");
        assertEquals(cell(table, DELTA, 5), "1", "a started locomotive was not counted for the day it started");

        assertEquals(cell(table, CHARLIE, 3), I18n.f("loc.neverOperated"), "a locomotive that never ran has a last run");
        assertEquals(cell(table, CHARLIE, 4), I18n.f("loc.neverOperated"), "a locomotive that never ran has a first run");
        assertEquals(cell(table, CHARLIE, 5), "0", "a locomotive that never ran has days run");

        assertTrue(rowOf(table, ALPHA) < rowOf(table, BRAVO), "bravo, who ran less, is listed above alpha");
        assertTrue(rowOf(table, BRAVO) < rowOf(table, CHARLIE), "charlie, who never ran, is listed above bravo");
        assertTrue(rowOf(table, BRAVO) < rowOf(table, DELTA), "delta, who ran no time, is listed above bravo");
    }

    /**
     * The totals under the table add up the single locomotives only: a multi-unit's time is its members' already, so
     * the multi-unit is listed, marked, and left out of both totals and both counts.  A locomotive that was only
     * started adds nothing and is not counted.
     *
     * MUTATION: delete `if (!isMultiUnit)` around the cumulative totals in `LocomotiveStats.refresh` (keeping the
     * block), and the cumulative runtime and count fail.
     */
    @Test
    public void testTheTotalsLeaveOutMultiUnits() throws Exception
    {
        unit.getHistoricalOperatingTime().put(day(0), 5 * HOUR);

        pressRefresh();

        JTable table = table();

        assertTrue(rowOf(table, UNIT) >= 0, "the multi-unit has no row");
        assertTrue(String.valueOf(table.getValueAt(rowOf(table, UNIT), 0)).contains(I18n.t("loc.multiUnit")),
            "the multi-unit's row does not say it is one");

        assertEquals(text("todaysRuntimeVal"), "1:02:00", "today's total is not alpha's hour and bravo's two minutes");
        assertEquals(text("locCountVal"), "2", "today's count is not alpha and bravo");

        assertEquals(text("cumulativeRuntimeVal"), "3:12:00",
            "the overall total is not alpha's 1:40 and bravo's 1:32, or it counts the multi-unit");
        assertEquals(text("locCountCumulativeVal"), "2", "the overall count is not alpha and bravo");
    }

    /**
     * One locomotive run today reads "1 locomotive" and two read "2 locomotives", under each total.
     *
     * MUTATION: compare `todaysLocsRun == 0` in the label of the today total in `LocomotiveStats.refresh`, and this
     * fails.
     */
    @Test
    public void testOneLocomotiveIsSingular() throws Exception
    {
        assertEquals(text("locCountVal"), "2", "precondition: two ran today");
        assertEquals(text("locomotivesLabel"), I18n.t("stats.ui.labelLocomotivePlural"),
            "two locomotives are not called locomotives");

        bravo.getHistoricalOperatingTime().remove(day(0));

        pressRefresh();

        assertEquals(text("locCountVal"), "1", "precondition: only alpha ran today");
        assertEquals(text("locomotivesLabel"), I18n.t("stats.ui.labelLocomotiveSingular"),
            "one locomotive is not called a locomotive");
        assertEquals(text("locomotivesCumulativeLabel"), I18n.t("stats.ui.labelLocomotivePlural"),
            "the overall count of two is no longer plural");
    }

    /**
     * Refresh shows what the locomotives have run since the table was built.
     *
     * MUTATION: delete `this.refresh();` from `refreshActionPerformed` in `LocomotiveStats`, and this fails.
     */
    @Test
    public void testRefreshShowsNewRuntime() throws Exception
    {
        JTable table = table();

        charlie.getHistoricalOperatingTime().put(day(0), 25 * MINUTE);

        Components.settle();

        assertEquals(cell(table, CHARLIE, 1), "0:00:00", "precondition: the table changed before Refresh was pressed");

        pressRefresh();

        table = table();

        assertEquals(cell(table, CHARLIE, 1), "0:25:00", "Refresh did not show the 25 minutes charlie ran");
        assertEquals(cell(table, CHARLIE, 5), "1", "Refresh did not show the day charlie ran");
        assertEquals(text("locCountVal"), "3", "Refresh did not count charlie among today's locomotives");
    }

    /**
     * Turning to the Stats tab shows the latest figures with no Refresh pressed.
     *
     * MUTATION: compare `KeyboardTab.getTabCount() - 1` in `KeyboardTabStateChanged`, and this fails.
     */
    @Test
    public void testTheStatsTabIsCurrentWhenItOpens() throws Exception
    {
        JTabbedPane tabs = (JTabbedPane) field(ui, "KeyboardTab");

        Components.onTheEventThread(() ->
        {
            tabs.setSelectedIndex(0);

            return null;
        });

        charlie.getHistoricalOperatingTime().put(day(0), 25 * MINUTE);

        Components.select(stats);

        assertTrue(Components.waitFor(() -> "0:25:00".equals(cell(table(), CHARLIE, 1)), WAIT),
            "opening the Stats tab did not show the 25 minutes charlie has run");
    }

    /**
     * The filter keeps the rows containing what is typed, whatever its case, and typing nothing shows them all.
     *
     * MUTATION: drop the `(?i)` from `filterTable` in `LocomotiveStats`, and the capital letters match nothing.
     */
    @Test
    public void testTheFilterNarrowsTheRows() throws Exception
    {
        JTable table = table();

        int all = Components.onTheEventThread(table::getRowCount);

        assertTrue(all >= NAMES.length, "precondition: the table is missing locomotives");

        JTextField filter = (JTextField) field(stats, "filterField");

        type(filter, "STATS AL");

        assertEquals((int) Components.onTheEventThread(table::getRowCount), 1, "\"STATS AL\" did not narrow to alpha");
        assertEquals(String.valueOf(Components.onTheEventThread(() -> table.getValueAt(0, 0))), ALPHA,
            "the one row left is not alpha");

        type(filter, "stats b");

        assertEquals((int) Components.onTheEventThread(table::getRowCount), 1, "\"stats b\" did not narrow to bravo");

        type(filter, "  ");

        assertEquals((int) Components.onTheEventThread(table::getRowCount), all, "clearing the filter did not show every row");
    }

    /**
     * The usage graph button opens a chart window of its own, which closes without touching the statistics tab.
     *
     * MUTATION: empty `viewUsageGraphActionPerformed` in `LocomotiveStats`, and no window opens.
     */
    @Test
    public void testTheGraphButtonOpensAChartWindow() throws Exception
    {
        Set<Window> before = showingWindows();

        Components.select(stats);

        JButton open = (JButton) Components.withText(stats, I18n.t("stats.ui.usageGraph30Day"));

        assertNotNull(open, "the Stats tab has no 30-day usage graph button");

        Components.onTheEventThread(() ->
        {
            open.doClick();

            return null;
        });

        assertTrue(Components.waitFor(() -> !newWindows(before).isEmpty(), WAIT), "the button opened no window");

        closeStrayWindows();

        assertTrue(newWindows(before).isEmpty(), "the chart window did not close");
        assertTrue(Components.onTheEventThread(stats::isShowing), "closing the chart hid the statistics tab");
    }

    /**
     * Export Raw Data writes the file the chooser names: a line for each day each locomotive ran, its name in quotes,
     * the date, and the milliseconds; and for every locomotive the table lists, those lines add up to its overall
     * runtime and number its days run.
     *
     * MUTATION: write the value before the date in the line `exportDataActionPerformed` builds in `LocomotiveStats`,
     * and this fails.
     */
    @Test
    public void testExportWritesTheRowsTheWindowShows() throws Exception
    {
        File out = new File(sandbox.getFolder(), "stats-export.csv");

        export(out);

        assertTrue(out.isFile(), "Export Raw Data wrote no file where the chooser said");

        Set<String> lines = new HashSet<>(Arrays.asList(read(out).split("\n")));

        Set<String> expected = new HashSet<>();

        expected.add("\"" + ALPHA + "\"," + day(0) + "," + HOUR);
        expected.add("\"" + ALPHA + "\"," + day(1) + "," + 30 * MINUTE);
        expected.add("\"" + ALPHA + "\"," + day(3) + "," + 10 * MINUTE);
        expected.add("\"" + BRAVO + "\"," + day(0) + "," + 2 * MINUTE);
        expected.add("\"" + BRAVO + "\"," + day(40) + "," + 90 * MINUTE);
        expected.add("\"" + DELTA + "\"," + day(0) + ",0");

        assertEquals(lines, expected, "the file does not hold a line for each day each locomotive ran");

        JTable table = table();

        for (MarklinLocomotive loc : new MarklinLocomotive[] {alpha, bravo, charlie, delta})
        {
            long total = 0;
            int days = 0;

            for (String line : lines)
            {
                if (!line.startsWith("\"" + loc.getName() + "\",")) continue;

                total += Long.parseLong(line.substring(line.lastIndexOf(',') + 1));
                days++;
            }

            assertEquals(Conversion.convertSecondsToHMmSs(total), cell(table, loc.getName(), 1),
                "the file's lines for " + loc.getName() + " do not add up to the runtime the table shows");
            assertEquals(String.valueOf(days), cell(table, loc.getName(), 5),
                "the file's lines for " + loc.getName() + " are not the days the table shows");
        }
    }

    /**
     * The folder the file went to is the one the next chooser opens in.
     *
     * MUTATION: delete the `TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, ...)` line from
     * `exportDataActionPerformed`, and this fails.
     */
    @Test
    public void testExportRemembersTheFolder() throws Exception
    {
        File folder = Files.createTempDirectory(sandbox.getFolder().toPath(), "export-folder").toFile();

        TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, sandbox.getFolder().getParent());

        export(new File(folder, "remembered.csv"));

        assertEquals(TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, ""), folder.getPath(),
            "the next export would not open in the folder this one went to");
    }

    /**
     * The chooser proposes a dated file name; the button is grayed while the chooser is open, so a second export cannot
     * start; canceling the chooser writes nothing and gives the button back.
     *
     * MUTATION: `continue` in place of `break` where `exportDataActionPerformed` finds the chooser canceled, and the
     * chooser comes back for ever and the button never returns.
     */
    @Test
    public void testCancelingTheExportWritesNothing() throws Exception
    {
        JButton button = exportButton();

        File folder = Files.createTempDirectory(sandbox.getFolder().toPath(), "canceled").toFile();

        TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, folder.getPath());

        Components.onTheEventThread(() ->
        {
            button.doClick();

            return null;
        });

        assertFalse(Components.onTheEventThread(button::isEnabled), "the button stays live while its chooser is open");

        JFileChooser chooser = awaitAChooser();

        assertNotNull(chooser, "Export Raw Data showed no file chooser");

        String proposed = Components.onTheEventThread(() -> chooser.getSelectedFile().getName());

        assertTrue(Pattern.matches("TC_locstats_\\d{8}_\\d{6}\\.csv", proposed),
            "the chooser's proposed name is not a dated TC_locstats file: " + proposed);

        Components.onTheEventThread(() ->
        {
            chooser.cancelSelection();

            return null;
        });

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(button::isEnabled), WAIT),
            "canceling the chooser did not give the button back");

        assertNull(awaitAChooser(), "the chooser came back after it was canceled");
        assertEquals(folder.list().length, 0, "a canceled export wrote a file");
        assertEquals(TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, ""), folder.getPath(),
            "a canceled export changed the folder the chooser opens in");
    }

    /**
     * A file that cannot be written says so and shows the chooser again; the second place is written, and is the one
     * remembered.
     *
     * MUTATION: `break` after the error dialog in the `catch` of `exportDataActionPerformed`, and the chooser is not
     * shown again.
     */
    @Test
    public void testAFailedWriteAsksForAnotherPlace() throws Exception
    {
        File nowhere = new File(sandbox.getFolder(), "no-such-folder/stats.csv");
        File good = new File(sandbox.getFolder(), "second-try.csv");

        Answering[] holder = new Answering[1];

        try (Answering answering = Answering.start(message ->
        {
            if (I18n.t("error.writingToFile").equals(message)) holder[0].choosing(good);

            return Answering.press(I18n.t("ui.ok"));
        }).choosing(nowhere))
        {
            holder[0] = answering;

            Components.onTheEventThread(() ->
            {
                exportButton().doClick();

                return null;
            });

            assertTrue(Components.waitFor(good::isFile, WAIT), "the second place was never written");
            assertTrue(answering.awaitAsked(1, WAIT), "nothing said the first place could not be written");
            assertEquals(answering.asked().get(0), I18n.t("error.writingToFile"), "the error does not say so");
        }

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(exportButton()::isEnabled), WAIT),
            "the button did not come back after the second place was written");
        assertFalse(nowhere.exists(), "the first place was written");
        assertTrue(read(good).contains("\"" + ALPHA + "\"," + day(0) + "," + HOUR), "the second file lacks alpha's hour");
        assertEquals(TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, ""), sandbox.getFolder().getPath(),
            "the folder remembered is not the one that was written");
    }

    // The table

    private static JTable table()
    {
        try
        {
            return (JTable) field(stats, "statsTable");
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** The row, as the table shows it, of the locomotive with this name; -1 when it has none. */
    private static int rowOf(JTable table, String name)
    {
        return Components.onTheEventThread(() ->
        {
            for (int row = 0; row < table.getRowCount(); row++)
            {
                if (String.valueOf(table.getValueAt(row, 0)).startsWith(name)) return row;
            }

            return -1;
        });
    }

    private static String cell(JTable table, String name, int column)
    {
        return Components.onTheEventThread(() ->
        {
            int row = rowOf(table, name);

            return row < 0 ? null : String.valueOf(table.getValueAt(row, column));
        });
    }

    private static String text(String label) throws Exception
    {
        return Components.onTheEventThread(() -> ((JLabel) field(stats, label)).getText());
    }

    /** Types into the filter: the text, then the key's release that follows each letter, to the field's own listeners. */
    private static void type(JTextField filter, String text)
    {
        Components.onTheEventThread(() ->
        {
            filter.setText(text);

            KeyEvent released = new KeyEvent(filter, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                KeyEvent.VK_E, 'e');

            for (java.awt.event.KeyListener listener : filter.getKeyListeners()) listener.keyReleased(released);

            return null;
        });
    }

    private static void pressRefresh() throws Exception
    {
        JButton refresh = (JButton) Components.withText(stats, I18n.t("ui.refresh"));

        Components.onTheEventThread(() ->
        {
            refresh.doClick();

            return null;
        });

        Components.settle();
    }

    // The export

    private static JButton exportButton()
    {
        return (JButton) Components.withText(stats, I18n.t("stats.ui.exportRawData"));
    }

    /** Presses Export Raw Data, picks this file in the chooser, and waits for the button to come back. */
    private static void export(File to) throws Exception
    {
        try (Answering answering = Answering.start(message -> null).choosing(to))
        {
            Components.onTheEventThread(() ->
            {
                exportButton().doClick();

                return null;
            });

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(exportButton()::isEnabled), WAIT),
                "the export did not finish");
        }
    }

    private static String read(File file) throws Exception
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static JFileChooser awaitAChooser()
    {
        JFileChooser[] found = new JFileChooser[1];

        Components.waitFor(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (!(w instanceof JDialog) || !w.isShowing()) continue;

                found[0] = Components.find(w, JFileChooser.class, null);

                if (found[0] != null) return true;
            }

            return false;
        }, 2000);

        return found[0];
    }

    private static Set<Window> showingWindows()
    {
        return Components.onTheEventThread(() ->
        {
            Set<Window> showing = new HashSet<>();

            for (Window w : Window.getWindows())
            {
                if (w.isShowing()) showing.add(w);
            }

            return showing;
        });
    }

    private static Set<Window> newWindows(Set<Window> before)
    {
        Set<Window> now = showingWindows();

        now.removeAll(before);

        return now;
    }

    /** Closes every window and dialog that was not showing when the class began, except the main window. */
    private static void closeStrayWindows()
    {
        Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w.isShowing() && w != ui && !windowsAtStart.contains(w)) w.dispose();
            }

            return null;
        });
    }

    /** The date this many days ago, as the locomotive keeps it. */
    private static String day(int ago)
    {
        return LocalDate.now().minusDays(ago).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static Object field(Object owner, String name) throws Exception
    {
        Class<?> type = owner.getClass();

        while (type != null)
        {
            try
            {
                Field f = type.getDeclaredField(name);

                f.setAccessible(true);

                return f.get(owner);
            }
            catch (NoSuchFieldException next)
            {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }
}
