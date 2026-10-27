package ui;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Component;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.LocomotiveStats;
import org.traincontrol.gui.locomotive.SegmentedControl;
import org.traincontrol.gui.locomotive.UsageChart;
import org.traincontrol.gui.locomotive.UsageChartPanel;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Components;

/**
 * The locomotive usage chart (design A): opened by the statistics window's button, drawn from the run times the
 * locomotives hold.
 *
 * Two locomotives of the test's own get run times on days counted back from today, from one table (`SEEDS`).  Every
 * expectation is worked out from that table, never read back from the model the chart reads.  The window is the real
 * one, opened through the button; its bars are read off its panel and, for what is drawn, off a picture of the panel.
 *
 * @author Adam
 */
public class testTheUsageChartShowsWhatTheLocomotivesRan
{
    private static final long H = 3600000L;
    private static final long M = 60000L;

    /** Days back from today, then what locomotive A and B ran that day, in milliseconds. */
    private static final long[][] SEEDS = {
        {0, 2 * H, 0}, {1, H, 30 * M}, {3, 0, 45 * M}, {10, 3 * H + 30 * M, 0}, {29, H, 0}, {30, 0, 5 * H},
        {45, 2 * H, 0}, {100, 0, 4 * H}, {400, 6 * H, 0},
    };

    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static support.LayoutSandbox sandbox;
    private static MarklinLocomotive locA;
    private static MarklinLocomotive locB;
    private static MarklinLocomotive head;

    /** The fixture locomotives' own run times, set aside so the chart holds only the test's, and put back after. */
    private static final java.util.Map<Locomotive, java.util.Map<String, Long>> SET_ASIDE = new java.util.HashMap<>();

    private final LocalDate today = LocalDate.now();

    /** How many rows of the table the locomotives hold now. */
    private int rows = SEEDS.length;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the chart needs a display");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedInDebug();

        locA = model.newMM2Locomotive("Chart A", 71);
        locB = model.newMM2Locomotive("Chart B", 72);
        head = model.newMM2Locomotive("Chart Head", 73);

        for (Locomotive l : model.getLocomotives())
        {
            if (l != locA && l != locB && l != head)
            {
                SET_ASIDE.put(l, new java.util.HashMap<>(l.getHistoricalOperatingTime()));

                l.getHistoricalOperatingTime().clear();
            }
        }

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (ui != null)
        {
            final TrainControlUI closing = ui;

            javax.swing.SwingUtilities.invokeAndWait(closing::dispose);
        }

        for (java.util.Map.Entry<Locomotive, java.util.Map<String, Long>> e : SET_ASIDE.entrySet())
        {
            e.getKey().getHistoricalOperatingTime().clear();
            e.getKey().getHistoricalOperatingTime().putAll(e.getValue());
        }

        if (model != null)
        {
            try { model.deleteLoc("Chart Head"); } catch (Exception ignored) { }
            try { model.deleteLoc("Chart A"); } catch (Exception ignored) { }
            try { model.deleteLoc("Chart B"); } catch (Exception ignored) { }

            model.stop();
        }

        if (sandbox != null) sandbox.close();
    }

    @BeforeMethod
    public void seed()
    {
        seed(SEEDS.length);
    }

    /** Gives the two locomotives the first n rows of the table, and nothing else. */
    private void seed(int n)
    {
        rows = n;

        locA.getHistoricalOperatingTime().clear();
        locB.getHistoricalOperatingTime().clear();
        head.getHistoricalOperatingTime().clear();

        for (int i = 0; i < n; i++)
        {
            String day = today.minusDays(SEEDS[i][0]).toString();

            if (SEEDS[i][1] > 0) locA.getHistoricalOperatingTime().put(day, SEEDS[i][1]);
            if (SEEDS[i][2] > 0) locB.getHistoricalOperatingTime().put(day, SEEDS[i][2]);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheChart() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof UsageChart) w.dispose();
            }
        });
    }

    // ---- what the table says ----

    private long ran(LocalDate first, LocalDate last)
    {
        long total = 0;

        for (int i = 0; i < rows; i++)
        {
            long[] s = SEEDS[i];
            LocalDate day = today.minusDays(s[0]);

            if (!day.isBefore(first) && !day.isAfter(last)) total += s[1] + s[2];
        }

        return total;
    }

    private int locsRan(LocalDate first, LocalDate last)
    {
        boolean a = false;
        boolean b = false;

        for (int i = 0; i < rows; i++)
        {
            long[] s = SEEDS[i];
            LocalDate day = today.minusDays(s[0]);

            if (!day.isBefore(first) && !day.isAfter(last))
            {
                a |= s[1] > 0;
                b |= s[2] > 0;
            }
        }

        return (a ? 1 : 0) + (b ? 1 : 0);
    }

    private static String hm(long ms)
    {
        return String.format("%d:%02d", ms / H, (ms / M) % 60);
    }

    /** A bar's tooltip before its list: the dates, then how many locomotives ran and for how long, from the bundle. */
    private static String figures(String dates, long ms, int locs)
    {
        return dates + "<br>" + I18n.f("stats.chart.tipTotals", hm(ms), locs,
            I18n.t(locs == 1 ? "stats.ui.labelLocomotiveSingular" : "stats.ui.labelLocomotivePlural"));
    }

    /** What `periodText` reads for a period: its first day, the line above the chart, its last day. */
    private static String period(LocalDate first, LocalDate last, long ms, int locs)
    {
        return first + " | " + I18n.f("stats.chart.totals", hm(ms), locs,
            I18n.t(locs == 1 ? "stats.ui.labelLocomotiveSingular" : "stats.ui.labelLocomotivePlural")) + " | " + last;
    }

    private static String dates(LocalDate first, LocalDate last)
    {
        return first.equals(last) ? first.toString() : I18n.f("stats.chart.dates", first.toString(), last.toString());
    }

    // ---- the window ----

    private UsageChart shown()
    {
        return Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof UsageChart && w.isShowing()) return (UsageChart) w;
            }

            return null;
        });
    }

    /** Presses the statistics window's button, as the operator does. */
    private UsageChart open()
    {
        LocomotiveStats stats = Components.find(ui, LocomotiveStats.class, null);

        assertNotNull(stats, "the main window has no statistics panel");

        AbstractButton button = Components.withText(stats, I18n.t("stats.ui.usageGraph30Day"));

        assertNotNull(button, "the statistics panel has no usage graph button");

        Components.press(button);

        assertTrue(Components.waitFor(() -> shown() != null, 20000), "the button opened no usage chart");

        Components.settle();

        return shown();
    }

    private static UsageChartPanel panel(UsageChart chart)
    {
        return Components.named(chart, "usageChart.panel", UsageChartPanel.class);
    }

    private static JLabel summary(UsageChart chart)
    {
        return Components.named(chart, "usageChart.summary", JLabel.class);
    }

    private static AbstractButton button(UsageChart chart, String text)
    {
        AbstractButton b = Components.withText(chart, text);

        assertNotNull(b, "the chart has no button saying " + text);

        return b;
    }

    private static final String[] RANGES = {
        "stats.chart.range7", "stats.chart.range30", "stats.chart.range90", "stats.chart.rangeYear",
        "stats.chart.rangeAll",
    };

    /** Presses a range's button and waits for the chart to draw it. */
    private static void pick(UsageChart chart, String rangeKey)
    {
        Components.press(button(chart, I18n.t(rangeKey)));
        Components.settle();
    }

    private static void press(UsageChart chart, String key)
    {
        Components.press(button(chart, I18n.t(key)));
        Components.settle();
    }

    private static List<UsageChartPanel.Bar> bars(UsageChart chart)
    {
        return Components.onTheEventThread(() ->
        {
            UsageChartPanel p = panel(chart);

            List<UsageChartPanel.Bar> bars = new ArrayList<>();

            for (int i = 0; i < p.barCount(); i++) bars.add(p.bar(i));

            return bars;
        });
    }

    private static List<String> snapshot(UsageChart chart)
    {
        List<String> s = new ArrayList<>();

        for (UsageChartPanel.Bar b : bars(chart)) s.add(b.first + "|" + b.last + "|" + b.millis + "|" + b.locs);

        return s;
    }

    private static String summaryText(UsageChart chart)
    {
        return Components.onTheEventThread(() -> summary(chart).getText());
    }

    /** The first day under the chart, the line above it and the last day under it, as `period` builds them. */
    private static String periodText(UsageChart chart)
    {
        return Components.onTheEventThread(() -> Components.named(chart, "usageChart.firstDay", JLabel.class).getText()
            + " | " + summary(chart).getText() + " | " + Components.named(chart, "usageChart.lastDay", JLabel.class).getText());
    }

    // ---- the picture ----

    private static BufferedImage picture(UsageChart chart)
    {
        return Components.onTheEventThread(() ->
        {
            UsageChartPanel p = panel(chart);

            BufferedImage image = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = image.createGraphics();

            p.paint(g);
            g.dispose();

            return image;
        });
    }

    private static int distance(int argb, Color c)
    {
        return Math.abs(((argb >> 16) & 255) - c.getRed()) + Math.abs(((argb >> 8) & 255) - c.getGreen())
            + Math.abs((argb & 255) - c.getBlue());
    }

    /** How many pixels of the bar's color stand in a column, counted up from the lowest one. */
    private static int drawnHeight(BufferedImage image, int x, Color bar)
    {
        int y = image.getHeight() - 1;

        while (y >= 0 && distance(image.getRGB(x, y), bar) > 12) y--;

        int height = 0;

        while (y >= 0 && distance(image.getRGB(x, y), bar) <= 12)
        {
            height++;
            y--;
        }

        return height;
    }

    private static Rectangle barBounds(UsageChart chart, int i)
    {
        return Components.onTheEventThread(() -> panel(chart).barBounds(i));
    }

    private static Color barColor(UsageChart chart)
    {
        return Components.onTheEventThread(() -> panel(chart).barColor());
    }

    // ---- the claims ----

    /**
     * The default range is the 30 days ending today, a bar a day, and each bar is as tall as its day's run time: read
     * off the picture, where the tallest day is the scale and a day with no run has no bar.
     *
     * MUTATION: in `UsageChartPanel.barBounds`, take the square root of the run time before scaling, and the shorter
     * days stand too tall.
     */
    @Test(timeOut = 120000)
    public void testTheDefaultRangeIsThirtyDailyBarsAsTallAsTheirDays() throws Exception
    {
        UsageChart chart = open();

        List<UsageChartPanel.Bar> bars = bars(chart);

        assertEquals(bars.size(), 30, "the default range is not 30 bars");

        BufferedImage image = picture(chart);

        Color accent = barColor(chart);

        int tallest = drawnHeight(image, barBounds(chart, 29 - 10).x + barBounds(chart, 29 - 10).width / 2, accent);

        assertTrue(tallest > 100, "the day with 3:30 is only " + tallest + " pixels tall");

        for (int i = 0; i < 30; i++)
        {
            LocalDate day = today.minusDays(29 - i);

            assertEquals(bars.get(i).first, day, "bar " + i + " is not the day " + (29 - i) + " back");
            assertEquals(bars.get(i).last, day, "bar " + i + " covers more than a day");

            Rectangle r = barBounds(chart, i);

            int drawn = drawnHeight(image, r.x + r.width / 2, accent);

            long ms = ran(day, day);

            double expected = tallest * ms / (double) (3 * H + 30 * M);

            assertEquals(drawn, expected, 2.0, "the bar for " + day + " (" + hm(ms) + ") is " + drawn
                + " pixels tall where the day with 3:30 is " + tallest + ": it should be about " + (int) expected);

            if (ms == 0) assertEquals(drawn, 0, "the day " + day + " ran nothing and has a bar");
        }
    }

    /**
     * Each range has its number of bars, they follow one another with no day lost or counted twice, and a week or a
     * month holds the sum of its days and the locomotives that ran in them.
     *
     * MUTATION: in `UsageChart.spans`, begin each week of Year a day late (`start.plusWeeks(i).plusDays(1)`), and the
     * weeks are no longer Monday to Sunday and their sums move.
     */
    @Test(timeOut = 120000)
    public void testEachRangeHasItsBarsAndTheirSums() throws Exception
    {
        UsageChart chart = open();

        LocalDate earliest = today.minusDays(400);

        long monthsBack = ChronoUnit.MONTHS.between(YearMonth.from(earliest), YearMonth.from(today)) + 1;

        int[] counts = {7, 30, 90, 52, (int) monthsBack};

        for (int r = 0; r < RANGES.length; r++)
        {
            pick(chart, RANGES[r]);

            List<UsageChartPanel.Bar> bars = bars(chart);

            assertEquals(bars.size(), counts[r], I18n.t(RANGES[r]) + " has the wrong number of bars");

            assertEquals(bars.get(bars.size() - 1).last, today, I18n.t(RANGES[r]) + " does not end today");

            for (int i = 0; i < bars.size(); i++)
            {
                UsageChartPanel.Bar b = bars.get(i);

                assertEquals(b.millis, ran(b.first, b.last), I18n.t(RANGES[r]) + ": the bar for "
                    + dates(b.first, b.last) + " is not the sum of its days");
                assertEquals(b.locs, locsRan(b.first, b.last), I18n.t(RANGES[r]) + ": the locomotives in "
                    + dates(b.first, b.last));

                if (i > 0)
                {
                    assertEquals(b.first, bars.get(i - 1).last.plusDays(1), I18n.t(RANGES[r]) + ": a day is lost or "
                        + "counted twice before " + b.first);
                }

                if (r == 3)
                {
                    assertEquals(b.first.getDayOfWeek(), DayOfWeek.MONDAY, "a week of Year starts on " + b.first);
                    assertTrue(i == bars.size() - 1 || b.last.equals(b.first.plusDays(6)), "a week is not seven days");
                }

                if (r == 4)
                {
                    assertEquals(b.first.getDayOfMonth(), 1, "a month of All starts on " + b.first);
                    assertTrue(i == bars.size() - 1 || b.last.equals(YearMonth.from(b.first).atEndOfMonth()),
                        "a month of All ends on " + b.last);
                }
            }
        }
    }

    /**
     * All is weeks, not months, while the history is short; it still ends today.
     *
     * MUTATION: in `UsageChart.byMonth`, count a month's history as enough (`>= 1`), and a short history comes out in
     * months.
     */
    @Test(timeOut = 120000)
    public void testAllIsWeeksWhileTheHistoryIsShort() throws Exception
    {
        seed(4);

        UsageChart chart = open();

        pick(chart, "stats.chart.rangeAll");

        List<UsageChartPanel.Bar> bars = bars(chart);

        assertTrue(bars.size() >= 12, "a short history has only " + bars.size() + " bars");

        for (UsageChartPanel.Bar b : bars)
        {
            assertTrue(ChronoUnit.DAYS.between(b.first, b.last) <= 6, "a bar of a short history covers "
                + dates(b.first, b.last) + ", more than a week");
            assertEquals(b.millis, ran(b.first, b.last), "the week of " + b.first);
        }

        assertEquals(bars.get(bars.size() - 1).last, today, "All does not end today");
        assertEquals(periodText(chart), period(today.minusDays(10), today, ran(today.minusDays(10), today), 2),
            "All does not run from the earliest day any locomotive ran");
    }

    /**
     * Previous goes back one whole period of the range and Next returns to the very same bars; Next is gray at today,
     * and Reset returns there; All has no periods to scroll.
     *
     * MUTATION: in `UsageChart`, let Previous step a day (`scroll(1)` moving `page` by 1 but the period by one day), or
     * let `next.setEnabled(paged && page > 0)` ignore the page, and this fails.
     */
    @Test(timeOut = 120000)
    public void testPreviousAndNextScrollByAWholePeriod() throws Exception
    {
        UsageChart chart = open();

        int[] days = {7, 30, 90, 364};

        for (int r = 0; r < 4; r++)
        {
            pick(chart, RANGES[r]);

            List<String> now = snapshot(chart);
            String nowSummary = periodText(chart);
            LocalDate first = bars(chart).get(0).first;

            assertFalse(button(chart, I18n.t("stats.chart.next")).isEnabled(),
                I18n.t(RANGES[r]) + ": Next is enabled at today");

            press(chart, "stats.chart.previous");

            assertEquals(bars(chart).get(0).first, first.minusDays(days[r]), I18n.t(RANGES[r])
                + ": Previous did not go back one whole period");
            assertNotEquals(periodText(chart), nowSummary, "the summary did not follow Previous");
            assertTrue(button(chart, I18n.t("stats.chart.next")).isEnabled(), "Next is gray with a later period to go to");

            press(chart, "stats.chart.next");

            assertEquals(snapshot(chart), now, I18n.t(RANGES[r]) + ": Previous then Next is not the same period");
            assertEquals(periodText(chart), nowSummary, "the summary after Previous then Next");
            assertFalse(button(chart, I18n.t("stats.chart.next")).isEnabled(), "Next is enabled back at today");

            press(chart, "stats.chart.previous");
            press(chart, "stats.chart.previous");
            press(chart, "stats.ui.reset");

            assertEquals(snapshot(chart), now, I18n.t(RANGES[r]) + ": Reset did not come back to today");
        }

        pick(chart, "stats.chart.rangeAll");

        assertFalse(button(chart, I18n.t("stats.chart.previous")).isEnabled(), "All has an earlier period");
        assertFalse(button(chart, I18n.t("stats.chart.next")).isEnabled(), "All has a later period");

        // The 30 days before the default: the day 30 back is the first of the previous page's newest.
        pick(chart, "stats.chart.range30");
        press(chart, "stats.chart.previous");

        assertEquals(bars(chart).get(29).last, today.minusDays(30), "Previous on 30 days does not end the day before");
        assertEquals(bars(chart).get(29).millis, 5 * H, "the day 30 back ran 5:00");
    }

    /**
     * The line above the chart says the period's run time and how many locomotives ran, and its first and last days sit
     * under the chart's lower-left and lower-right corners, for each range and for an earlier period.
     *
     * MUTATION: in `UsageChart.refresh`, total the summary from the bars' locomotive counts (a locomotive that ran on
     * two days counted twice), give it the wrong last day, or put the dates back in the line, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheSummaryLineHoldsThePeriodsTotals() throws Exception
    {
        UsageChart chart = open();

        assertEquals(ran(today.minusDays(29), today), 8 * H + 45 * M, "the test's own table is wrong");
        assertEquals(periodText(chart), period(today.minusDays(29), today, 8 * H + 45 * M, 2),
            "the default summary");

        Components.onTheEventThread(() ->
        {
            Rectangle plot = new Rectangle(SwingUtilities.convertPoint(panel(chart).getParent(), panel(chart).getLocation(),
                chart.getContentPane()), panel(chart).getSize());

            JLabel firstDay = Components.named(chart, "usageChart.firstDay", JLabel.class);
            JLabel lastDay = Components.named(chart, "usageChart.lastDay", JLabel.class);

            Point left = SwingUtilities.convertPoint(firstDay.getParent(), firstDay.getLocation(), chart.getContentPane());
            Point right = SwingUtilities.convertPoint(lastDay.getParent(), lastDay.getLocation(), chart.getContentPane());

            assertTrue(firstDay.isShowing() && lastDay.isShowing(), "the dates under the chart are not shown");
            assertTrue(left.y >= plot.y + plot.height && right.y >= plot.y + plot.height,
                "the dates are not below the chart");
            assertEquals(left.x, plot.x, "the first day is not at the chart's left edge");
            assertEquals(right.x + lastDay.getWidth(), plot.x + plot.width, "the last day is not at the chart's right edge");

            return null;
        });

        int[] days = {7, 30, 90};

        for (int r = 0; r < 3; r++)
        {
            pick(chart, RANGES[r]);

            LocalDate first = today.minusDays(days[r] - 1);

            assertEquals(periodText(chart), period(first, today, ran(first, today), locsRan(first, today)),
                I18n.t(RANGES[r]));
        }

        pick(chart, "stats.chart.range30");
        press(chart, "stats.chart.previous");

        // Days 59 to 30 back: 5:00 and 2:00, each of one locomotive, two locomotives in all
        assertEquals(periodText(chart), period(today.minusDays(59), today.minusDays(30), 7 * H, 2),
            "the earlier 30 days");

        pick(chart, "stats.chart.rangeYear");

        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(51);

        assertEquals(periodText(chart), period(monday, today, ran(monday, today), locsRan(monday, today)),
            "Year");

        pick(chart, "stats.chart.rangeAll");

        assertEquals(periodText(chart), period(today.minusDays(400), today, 25 * H + 45 * M, 2), "All");
    }

    /**
     * Hovering a bar says its date (a week or a month by its two dates), its run time and how many locomotives ran; a
     * day with no run says so; beside the bars it says nothing; and the bar under the pointer is drawn differently.
     *
     * MUTATION: in `UsageChartPanel.getToolTipText`, ask for the bar next door (`barAt(...) + 1`), or leave the
     * highlight out of `drawBars`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testHoveringABarSaysWhatItHolds() throws Exception
    {
        UsageChart chart = open();

        UsageChartPanel p = panel(chart);

        int top = Components.onTheEventThread(() -> p.plot().y + p.plot().height / 2);

        // The day 10 back, one locomotive; the day 1 back, two; the day 2 back, none.
        int[][] cases = {{10, 1}, {1, 2}, {2, 0}};

        for (int[] c : cases)
        {
            Rectangle r = barBounds(chart, 29 - c[0]);

            LocalDate day = today.minusDays(c[0]);

            assertEquals(tip(p, r.x + r.width / 2, top),
                html(figures(day.toString(), ran(day, day), c[1]), tipLines(day, day)),
                "the tooltip over the day " + c[0] + " back");
        }

        assertNull(tip(p, 2, top), "there is a tooltip left of the first bar");

        // The highlight: the bar under the pointer changes color, the one beside it does not, and it leaves with the pointer
        Rectangle on = barBounds(chart, 29 - 10);
        Rectangle beside = barBounds(chart, 29 - 29);
        Color accent = barColor(chart);

        int x = on.x + on.width / 2;
        int y = on.y + on.height - 3;

        assertTrue(distance(picture(chart).getRGB(x, y), accent) <= 12, "the bar is not the accent before the pointer comes");

        move(p, MouseEvent.MOUSE_MOVED, x, top);

        BufferedImage hovered = picture(chart);

        assertTrue(distance(hovered.getRGB(x, y), accent) > 12, "the bar under the pointer is drawn as the others");
        assertTrue(distance(hovered.getRGB(beside.x + beside.width / 2, beside.y + beside.height - 3), accent) <= 12,
            "a bar beside the pointer is highlighted");

        move(p, MouseEvent.MOUSE_EXITED, x, top);

        assertTrue(distance(picture(chart).getRGB(x, y), accent) <= 12, "the bar stays highlighted after the pointer leaves");

        // A week of Year: the one holding the day 45 back, Monday to Sunday
        pick(chart, "stats.chart.rangeYear");

        LocalDate day = today.minusDays(45);
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int week = 51 - (int) ChronoUnit.WEEKS.between(monday, thisMonday);

        Rectangle w = barBounds(chart, week);

        assertEquals(tip(p, w.x + w.width / 2, top),
            html(figures(dates(monday, monday.plusDays(6)), ran(monday, monday.plusDays(6)), 1),
                tipLines(monday, monday.plusDays(6))), "the week of " + monday);

        // A month of All
        pick(chart, "stats.chart.rangeAll");

        LocalDate far = today.minusDays(400);
        YearMonth month = YearMonth.from(far);

        Rectangle m = barBounds(chart, 0);

        assertEquals(tip(p, m.x + m.width / 2, top),
            html(figures(dates(month.atDay(1), month.atEndOfMonth()), ran(month.atDay(1), month.atEndOfMonth()), 1),
                tipLines(month.atDay(1), month.atEndOfMonth())), "the month of " + month);
    }

    /**
     * A multi-unit records a run of its own while its members record theirs; the chart counts the time once, as the
     * Stats table does: the head and its member running together are one run and one locomotive.
     *
     * MUTATION: in `MarklinControlStation.locomotivesRunningOnTheirOwn`, keep every locomotive, and today's bar is the
     * time twice and the locomotives three.
     */
    @Test(timeOut = 120000)
    public void testAMultiUnitAndItsMembersRunningTogetherCountOnce() throws Exception
    {
        String day = today.toString();

        // Today the member ran 2:00 (the table's); the head, driving it, recorded the same 2:00 on its own.
        head.getHistoricalOperatingTime().put(day, 2 * H);

        assertEquals(head.setLinkedLocomotives(java.util.Collections.singletonMap("Chart A", 1.0)), 1,
            "the test's multi-unit could not be made");

        try
        {
            assertTrue(head.hasLinkedLocomotives(), "the head has no members");

            UsageChart chart = open();

            UsageChartPanel.Bar now = bars(chart).get(29);

            assertEquals(now.millis, 2 * H, "today's bar counts the head's time as well as its member's");
            assertEquals(now.locs, 1, "today's bar counts the head as a locomotive that ran");
            assertEquals(periodText(chart), period(today.minusDays(29), today, 8 * H + 45 * M, 2),
                "the summary counts the head's time");
        }
        finally
        {
            head.unlinkLocomotive(locA);
        }
    }

    // ---- the range picker, the arrows, the tooltips' locomotives, the half-hour gridlines ----

    private static final String[] RANGE_NAMES = {"DAYS_7", "DAYS_30", "DAYS_90", "YEAR", "ALL"};

    private static SegmentedControl ranges(UsageChart chart)
    {
        SegmentedControl s = Components.named(chart, "usageChart.ranges", SegmentedControl.class);

        assertNotNull(s, "the chart has no range picker");

        return s;
    }

    private static AbstractButton segment(UsageChart chart, int i)
    {
        AbstractButton b = Components.named(chart, "usageChart.range." + RANGE_NAMES[i], AbstractButton.class);

        assertNotNull(b, "the range picker has no segment " + RANGE_NAMES[i]);

        return b;
    }

    /** A picture of any component on the given background, as it is painted now. */
    private static BufferedImage pictureOf(java.awt.Component c, Color background)
    {
        return Components.onTheEventThread(() ->
        {
            BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = image.createGraphics();

            g.setColor(background);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            c.paint(g);
            g.dispose();

            return image;
        });
    }

    /**
     * The five ranges are one oval: the segments touch with no gap, the outline runs unbroken across them and round
     * at both ends, a thin divider stands between neighbors, and only the chosen segment is filled with the accent,
     * which follows a press.  No segment takes the focus, at open or on a press, so none wears a focus ring.
     *
     * MUTATION: in `SegmentedControl.paintChildren`, start the divider loop past the last segment (`i =
     * getComponentCount()`), and no divider is drawn; or leave the segments focusable, and the first takes the focus.
     */
    @Test(timeOut = 120000)
    public void testTheRangesAreOneOvalWithDividersAndTheChosenOneFilled() throws Exception
    {
        UsageChart chart = open();

        SegmentedControl control = ranges(chart);

        Color background = UIManager.getColor("Panel.background");
        Color line = Components.onTheEventThread(control::lineColor);
        Color accent = Components.onTheEventThread(control::chosenColor);

        int w = control.getWidth();
        int h = control.getHeight();

        assertTrue(h > 10 && w > 5 * 30, "the picker is " + w + " by " + h);

        // One row of touching segments, as tall as the picker
        int edge = 0;

        for (int i = 0; i < 5; i++)
        {
            AbstractButton s = segment(chart, i);

            assertEquals(s.getX(), edge, "segment " + i + " leaves a gap before it or overlaps its neighbor");
            assertEquals(s.getHeight(), h, "segment " + i + " is not as tall as the picker");

            edge += s.getWidth();
        }

        assertEquals(edge, w, "the segments do not fill the picker");

        BufferedImage image = pictureOf(control, background);

        // The outline: unbroken along the top and the bottom, across every divider, round at the ends
        for (int x = h; x < w - h; x++)
        {
            assertTrue(distance(image.getRGB(x, 0), line) <= 12, "the outline breaks along the top at x=" + x);
            assertTrue(distance(image.getRGB(x, h - 1), line) <= 12, "the outline breaks along the bottom at x=" + x);
        }

        assertEquals(distance(image.getRGB(0, 0), background), 0, "the top left corner is square");
        assertEquals(distance(image.getRGB(w - 1, h - 1), background), 0, "the bottom right corner is square");
        assertTrue(distance(image.getRGB(0, h / 2), line) <= 12, "the outline is missing at the left end");
        assertTrue(distance(image.getRGB(w - 1, h / 2), line) <= 12, "the outline is missing at the right end");

        // Dividers: a line-colored column between neighbors, near the top and the bottom, and not inside a segment
        for (int i = 1; i < 5; i++)
        {
            int x = segment(chart, i).getX();

            assertTrue(distance(image.getRGB(x, 4), line) <= 12 && distance(image.getRGB(x, h - 5), line) <= 12,
                "there is no divider between segments " + (i - 1) + " and " + i);
            assertTrue(distance(image.getRGB(x + 8, 4), line) > 12, "a divider stands inside segment " + i);
        }

        // The chosen one is filled and the others are not; the fill follows a press
        for (int chosen : new int[] {1, 3, 0})
        {
            if (chosen != 1) pick(chart, RANGES[chosen]);

            BufferedImage now = pictureOf(control, background);

            for (int i = 0; i < 5; i++)
            {
                int d = distance(now.getRGB(segment(chart, i).getX() + 8, h / 2), accent);

                if (i == chosen) assertTrue(d <= 12, "the chosen segment " + i + " is not filled");
                else assertTrue(d > 60, "segment " + i + " is filled but segment " + chosen + " is the chosen one");
            }
        }

        // Pressed above and open since the start, which is when the focus was handed out
        for (int i = 0; i < 5; i++)
        {
            final int at = i;

            assertFalse(Components.onTheEventThread(() -> segment(chart, at).isFocusable() || segment(chart, at).isFocusOwner()),
                "segment " + i + " takes the focus, and wears a ring for it");
        }
    }

    /** The ink in a picture of a button: columns, in runs separated by at least four empty ones, as {first, last}. */
    private static List<int[]> inkRuns(BufferedImage image, Color background)
    {
        List<int[]> runs = new ArrayList<>();

        int[] run = null;
        int empty = 0;

        for (int x = 6; x < image.getWidth() - 6; x++)
        {
            boolean ink = false;

            for (int y = 5; y < image.getHeight() - 5; y++)
            {
                if (distance(image.getRGB(x, y), background) > 40) ink = true;
            }

            if (ink)
            {
                if (run == null || empty >= 4) runs.add(run = new int[] {x, x});

                run[1] = x;
                empty = 0;
            }
            else
            {
                empty++;
            }
        }

        return runs;
    }

    /** The x range of the ink in one row of an icon's picture; null when the row is clear. */
    private static int[] inkInRow(BufferedImage image, int y)
    {
        int from = -1;
        int to = -1;

        for (int x = 0; x < image.getWidth(); x++)
        {
            if ((image.getRGB(x, y) >>> 24) > 100)
            {
                if (from < 0) from = x;

                to = x;
            }
        }

        return from < 0 ? null : new int[] {from, to};
    }

    /**
     * Previous wears a left arrow before its text and Next a right arrow after it, as icons: the buttons' text is
     * still the bundle's words, and Reset has none.
     *
     * MUTATION: in `UsageChart`'s constructor, give Previous the right-pointing icon (`new Chevron(false)`), or none,
     * and this fails.
     */
    @Test(timeOut = 120000)
    public void testPreviousAndNextWearArrows() throws Exception
    {
        UsageChart chart = open();

        // Both are enabled one page back
        press(chart, "stats.chart.previous");

        AbstractButton previous = Components.named(chart, "usageChart.previous", AbstractButton.class);
        AbstractButton next = Components.named(chart, "usageChart.next", AbstractButton.class);
        AbstractButton reset = Components.named(chart, "usageChart.reset", AbstractButton.class);

        assertEquals(previous.getText(), I18n.t("stats.chart.previous"), "Previous says something else");
        assertEquals(next.getText(), I18n.t("stats.chart.next"), "Next says something else");
        assertEquals(reset.getText(), I18n.t("stats.ui.reset"), "Reset says something else");
        assertNull(reset.getIcon(), "Reset has an arrow");
        assertNotNull(previous.getIcon(), "Previous has no arrow");
        assertNotNull(next.getIcon(), "Next has no arrow");

        Color buttonBackground = UIManager.getColor("Button.background");

        // Drawn alone: the point of a left arrow is at the left, and of a right arrow at the right
        for (AbstractButton b : new AbstractButton[] {previous, next})
        {
            boolean left = b == previous;

            BufferedImage icon = Components.onTheEventThread(() ->
            {
                javax.swing.Icon i = b.getIcon();

                BufferedImage image = new BufferedImage(i.getIconWidth(), i.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

                java.awt.Graphics2D g = image.createGraphics();

                i.paintIcon(b, g, 0, 0);
                g.dispose();

                return image;
            });

            int[] top = inkInRow(icon, 2);
            int[] middle = inkInRow(icon, icon.getHeight() / 2);

            assertNotNull(top, (left ? "Previous" : "Next") + " arrow is not drawn");
            assertNotNull(middle, (left ? "Previous" : "Next") + " arrow has no point");

            if (left) assertTrue(middle[0] < top[0], "Previous does not point left");
            else assertTrue(middle[1] > top[1], "Next does not point right");
        }

        // In the button: Previous's first ink is the arrow, before its text; Next's last ink is the arrow, after it
        List<int[]> before = inkRuns(pictureOf(previous, buttonBackground), buttonBackground);
        List<int[]> after = inkRuns(pictureOf(next, buttonBackground), buttonBackground);

        int iconWidth = Components.onTheEventThread(() -> previous.getIcon().getIconWidth());

        assertTrue(before.size() >= 2 && before.get(0)[1] - before.get(0)[0] + 1 <= iconWidth + 1,
            "the arrow does not stand before Previous's text");

        int[] last = after.isEmpty() ? new int[] {0, 999} : after.get(after.size() - 1);

        assertTrue(after.size() >= 2 && last[1] - last[0] + 1 <= iconWidth + 1,
            "the arrow does not stand after Next's text");
    }

    /** What the table says each of the two locomotives ran over a span, as the tooltip's lines, most run first. */
    private String tipLines(LocalDate first, LocalDate last)
    {
        long[] total = new long[2];

        for (int i = 0; i < rows; i++)
        {
            LocalDate day = today.minusDays(SEEDS[i][0]);

            if (!day.isBefore(first) && !day.isAfter(last))
            {
                total[0] += SEEDS[i][1];
                total[1] += SEEDS[i][2];
            }
        }

        StringBuilder lines = new StringBuilder();

        // "Chart A" before "Chart B" when they ran equally
        int[] order = total[1] > total[0] ? new int[] {1, 0} : new int[] {0, 1};

        for (int k : order)
        {
            if (total[k] > 0)
            {
                lines.append("<br>").append(I18n.f("stats.chart.topLoc", k == 0 ? "Chart A" : "Chart B", hm(total[k])));
            }
        }

        return lines.toString();
    }

    /** A tooltip: its head, then a blank line before the list when there is one. */
    private static String html(String first, String lines)
    {
        return "<html>" + first + (lines.isEmpty() ? "" : "<br>" + lines) + "</html>";
    }

    /** Extra locomotives that ran today, made for one test and gone after it; the last has markup in its name. */
    private static final String[][] EXTRA = {{"Chart C", "74"}, {"Chart D", "75"}, {"Chart <E> & Co", "76"}};

    private static void addExtras()
    {
        for (String[] e : EXTRA) model.newMM2Locomotive(e[0], Integer.parseInt(e[1]));
    }

    private static void dropExtras()
    {
        for (String[] e : EXTRA)
        {
            try { model.deleteLoc(e[0]); } catch (Exception ignored) { }
        }
    }

    /**
     * A bar's tooltip is its first line and then the three locomotives that ran most in it, the most run first, each
     * with its run time, and "and N more" for the rest; a bar with fewer lists them all and a day with no run lists
     * none.  Five locomotives ran today: 3:00, 2:00, 1:30, 0:45 and 0:10.
     *
     * MUTATION: in `UsageChartPanel`, list four (`TOP_LOCOMOTIVES = 4`), or in
     * `MarklinControlStation.getRuntimePerLocBetween`, sort the least run first, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheTooltipNamesTheTopThreeLocomotivesAndHowManyMore() throws Exception
    {
        try
        {
            addExtras();

            String day = today.toString();

            model.getLocByName("Chart C").getHistoricalOperatingTime().put(day, 90 * M);
            model.getLocByName("Chart D").getHistoricalOperatingTime().put(day, 45 * M);
            model.getLocByName("Chart <E> & Co").getHistoricalOperatingTime().put(day, 3 * H);
            locB.getHistoricalOperatingTime().put(day, 10 * M);

            UsageChart chart = open();
            UsageChartPanel p = panel(chart);

            int top = Components.onTheEventThread(() -> p.plot().y + p.plot().height / 2);

            Rectangle now = barBounds(chart, 29);

            long total = 2 * H + 90 * M + 45 * M + 3 * H + 10 * M;

            assertEquals(tip(p, now.x + now.width / 2, top),
                html(figures(day, total, 5), "<br>" + I18n.f("stats.chart.topLoc", "Chart &lt;E&gt; &amp; Co", "3:00")
                    + "<br>" + I18n.f("stats.chart.topLoc", "Chart A", "2:00")
                    + "<br>" + I18n.f("stats.chart.topLoc", "Chart C", "1:30") + "<br>" + I18n.f("stats.chart.more", 2)),
                "today's tooltip: the top three of five, and two more");

            // Three or fewer: all of them and no "more"; the day 1 back is A 1:00 and B 0:30
            Rectangle yesterday = barBounds(chart, 28);
            LocalDate before = today.minusDays(1);

            assertEquals(tip(p, yesterday.x + yesterday.width / 2, top),
                html(figures(before.toString(), 90 * M, 2), tipLines(before, before)),
                "yesterday's tooltip lists both locomotives and says no more");

            // A day with no run: its line and no list
            Rectangle quiet = barBounds(chart, 27);

            assertEquals(tip(p, quiet.x + quiet.width / 2, top), html(figures(today.minusDays(2).toString(), 0, 0), ""),
                "a day with no run lists locomotives");
        }
        finally
        {
            dropExtras();
        }
    }

    /**
     * The tooltip leaves out a multi-unit as the totals do: its member is listed once and the head not at all.
     *
     * MUTATION: in `MarklinControlStation.getRuntimePerLocBetween`, go through `getLocomotives()` instead of
     * `locomotivesRunningOnTheirOwn()`, and the head is listed beside its member.
     */
    @Test(timeOut = 120000)
    public void testTheTooltipLeavesOutAMultiUnit() throws Exception
    {
        String day = today.toString();

        head.getHistoricalOperatingTime().put(day, 5 * H);

        assertEquals(head.setLinkedLocomotives(java.util.Collections.singletonMap("Chart A", 1.0)), 1,
            "the test's multi-unit could not be made");

        try
        {
            UsageChart chart = open();
            UsageChartPanel p = panel(chart);

            int top = Components.onTheEventThread(() -> p.plot().y + p.plot().height / 2);

            Rectangle now = barBounds(chart, 29);

            assertEquals(tip(p, now.x + now.width / 2, top),
                html(figures(day, 2 * H, 1), "<br>" + I18n.f("stats.chart.topLoc", "Chart A", "2:00")),
                "the head, with the longer run, is listed beside its member");
        }
        finally
        {
            head.unlinkLocomotive(locA);
        }
    }

    /**
     * A locomotive's name is shown as text: its markup characters are escaped, so a name with a tag in it does not
     * change the tooltip's layout.
     *
     * MUTATION: in `UsageChartPanel.escaped`, return the text as it is, and the name goes into the tooltip raw.
     */
    @Test(timeOut = 120000)
    public void testTheTooltipEscapesALocomotivesName() throws Exception
    {
        try
        {
            addExtras();

            model.getLocByName("Chart <E> & Co").getHistoricalOperatingTime().put(today.toString(), 3 * H);

            UsageChart chart = open();
            UsageChartPanel p = panel(chart);

            Rectangle now = barBounds(chart, 29);

            String tip = tip(p, now.x + now.width / 2, Components.onTheEventThread(() -> p.plot().y + p.plot().height / 2));

            assertTrue(tip.contains("Chart &lt;E&gt; &amp; Co"), "the name is not escaped in " + tip);
            assertFalse(tip.contains("<E>"), "the name's tag is in the tooltip as markup: " + tip);
        }
        finally
        {
            dropExtras();
        }
    }

    /** The rows of a picture, at one column inside the plot, that are not the background: the gridlines. */
    private static List<Integer> gridRows(BufferedImage image, Rectangle plot, Color background)
    {
        List<Integer> rows = new ArrayList<>();

        for (int y = plot.y; y <= plot.y + plot.height; y++)
        {
            if (distance(image.getRGB(plot.x + 1, y), background) > 0) rows.add(y);
        }

        return rows;
    }

    /**
     * While the tallest bar is an hour or less the hours axis tops out at 1 with a tick and a gridline at 0.5, 0.5 in
     * the locale's decimal sign; past an hour the axis keeps whole-hour steps and draws nothing at half an hour.
     *
     * MUTATION: in `UsageChartPanel.tickStep`, return `axisStep` always, and the short scale has no half-hour line.
     */
    @Test(timeOut = 120000)
    public void testAShortScaleHasAHalfHourGridline() throws Exception
    {
        String sign = String.valueOf(java.text.DecimalFormatSymbols.getInstance(I18n.getLocale()).getDecimalSeparator());

        Color background = UIManager.getColor("Panel.background");

        // Forty minutes, then exactly an hour: both top out at 1
        for (long ran : new long[] {40 * M, H})
        {
            seed(0);

            locA.getHistoricalOperatingTime().put(today.toString(), ran);

            UsageChart chart = open();
            UsageChartPanel p = panel(chart);

            assertEquals((int) Components.onTheEventThread(p::axisMaxHours), 1,
                "the axis for " + hm(ran) + " does not top out at 1");
            assertEquals(Components.onTheEventThread(p::tickLabels), java.util.Arrays.asList("0", "0" + sign + "5", "1"),
                "the labels of the short scale");

            Rectangle plot = Components.onTheEventThread(p::plot);
            List<Integer> lines = gridRows(picture(chart), plot, background);

            assertEquals(lines.size(), 3, "the short scale has " + lines + " as gridlines");
            assertEquals((double) lines.get(1), plot.y + plot.height / 2.0, 1.0, "the middle line is not at half an hour");

            closeTheChart();
        }

        // A minute over the hour: whole hours, 0 1 2, and nothing at half an hour
        seed(0);

        locA.getHistoricalOperatingTime().put(today.toString(), H + M);

        UsageChart chart = open();
        UsageChartPanel p = panel(chart);

        assertEquals(Components.onTheEventThread(p::tickLabels), java.util.Arrays.asList("0", "1", "2"),
            "the labels of an hour and a minute");

        Rectangle plot = Components.onTheEventThread(p::plot);
        BufferedImage picture = picture(chart);

        assertEquals(gridRows(picture, plot, background).size(), 3, "the scale of an hour and a minute");

        int half = plot.y + plot.height - Math.round(plot.height * 0.25f);

        assertEquals(distance(picture.getRGB(plot.x + 1, half), background), 0, "there is a line at half an hour");

        // The seeded 30 days run to 3:30: a 4 hour scale, no half-hour line either
        seed(SEEDS.length);
        closeTheChart();

        UsageChart thirty = open();
        UsageChartPanel q = panel(thirty);

        assertEquals(Components.onTheEventThread(q::tickLabels), java.util.Arrays.asList("0", "1", "2", "3", "4"),
            "the labels of the 4 hour scale");

        Rectangle plot4 = Components.onTheEventThread(q::plot);
        int half4 = plot4.y + plot4.height - Math.round(plot4.height * 0.125f);

        assertEquals(distance(picture(thirty).getRGB(plot4.x + 1, half4), background), 0,
            "there is a line at half an hour on the 4 hour scale");
    }

    private static String tip(UsageChartPanel p, int x, int y)
    {
        return Components.onTheEventThread(() ->
            p.getToolTipText(new MouseEvent(p, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false)));
    }

    private static void move(UsageChartPanel p, int id, int x, int y)
    {
        Components.onTheEventThread(() ->
        {
            p.dispatchEvent(new MouseEvent(p, id, System.currentTimeMillis(), 0, x, y, 0, false));

            return null;
        });

        Components.settle();
    }

    /**
     * The look of design A: bars round at the top and square at the baseline, in the theme's accent; horizontal
     * gridlines only, drawn light against the background; the background and the lines from the look and feel.  The
     * theme is changed to a dark one by its colors, as a dark look and feel would, and put back.
     *
     * MUTATION: in `UsageChartPanel.drawBars`, fill the plain rectangle instead of the rounded one, or in `drawAxis`
     * draw the lines in the text color, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheLookIsRoundBarsAndLightGridlinesInTheThemesColors() throws Exception
    {
        UsageChart chart = open();

        Color accent = new Color(200, 60, 160);
        Color background = new Color(24, 26, 30);
        Color text = new Color(230, 230, 230);

        Color[] was = {UIManager.getColor("Component.accentColor"), UIManager.getColor("Panel.background"),
            UIManager.getColor("Label.foreground")};

        try
        {
            UIManager.put("Component.accentColor", accent);
            UIManager.put("Panel.background", background);
            UIManager.put("Label.foreground", text);

            BufferedImage image = picture(chart);

            assertEquals(distance(image.getRGB(1, 1), background), 0, "the chart's background is not the theme's");

            UsageChartPanel p = panel(chart);

            Rectangle plot = Components.onTheEventThread(p::plot);
            Rectangle bar = barBounds(chart, 29 - 10);

            assertTrue(distance(image.getRGB(bar.x + bar.width / 2, bar.y + bar.height / 2), accent) <= 12,
                "the bars are not the theme's accent");
            assertTrue(distance(image.getRGB(bar.x + bar.width / 2, bar.y + 1), accent) <= 12, "the top of a bar is not filled");
            assertTrue(distance(image.getRGB(bar.x, bar.y), background) < distance(image.getRGB(bar.x, bar.y), accent),
                "the top corner of a bar is square");
            assertTrue(distance(image.getRGB(bar.x, bar.y + bar.height - 1), accent) <= 12,
                "the foot of a bar is round: it should be square at the baseline");
            assertTrue(distance(image.getRGB(bar.x + bar.width - 1, bar.y + bar.height - 1), accent) <= 12,
                "the right foot of a bar is round");

            // Gridlines: rows in the plot that are not background at the plot's left edge
            List<Integer> lines = new ArrayList<>();

            for (int y = plot.y; y <= plot.y + plot.height; y++)
            {
                if (distance(image.getRGB(plot.x + 1, y), background) > 0) lines.add(y);
            }

            assertTrue(lines.size() >= 3, "the chart has " + lines.size() + " gridlines");

            for (int y : lines)
            {
                int line = image.getRGB(plot.x + 1, y);

                assertTrue(distance(line, background) < distance(line, text),
                    "the gridline at row " + y + " is nearer the text color than the background: it is not recessive");
            }

            // Between the bars, away from every gridline, nothing is drawn: no vertical lines
            double slot = plot.width / 30.0;

            for (int y = plot.y + 4; y < plot.y + plot.height - 4; y++)
            {
                if (lines.contains(y) || lines.contains(y - 1) || lines.contains(y + 1)) continue;

                for (int k = 1; k < 30; k++)
                {
                    int x = plot.x + (int) Math.round(k * slot);

                    assertEquals(distance(image.getRGB(x, y), background), 0, "something is drawn between the bars at "
                        + x + "," + y);
                }
            }
        }
        finally
        {
            UIManager.put("Component.accentColor", was[0]);
            UIManager.put("Panel.background", was[1]);
            UIManager.put("Label.foreground", was[2]);
        }

        // Back in the application's own colors, the bars are the look and feel's accent.
        Color accentNow = UIManager.getColor("Component.accentColor");

        if (accentNow != null) assertEquals(barColor(chart), accentNow, "the bars are not the theme's accent");
    }

    /**
     * The window cannot be made so narrow that the ranges and the paging buttons meet: at its smallest there is still
     * room between them.
     *
     * MUTATION: in the `UsageChart` constructor, keep the minimum at `MIN_WIDTH` (`Math.max(MIN_WIDTH, 0)`), and the
     * buttons run into the ranges.
     */
    @Test(timeOut = 120000)
    public void testTheSmallestWindowKeepsTheButtonsApart() throws Exception
    {
        UsageChart chart = open();

        Components.onTheEventThread(() ->
        {
            chart.setSize(1, 1);
            chart.validate();

            return null;
        });

        Components.settle();

        Components.onTheEventThread(() ->
        {
            Component ranges = ranges(chart);
            Component previous = button(chart, I18n.t("stats.chart.previous"));
            Component next = button(chart, I18n.t("stats.chart.next"));

            int rangesEnd = SwingUtilities.convertPoint(ranges.getParent(), ranges.getX() + ranges.getWidth(), 0,
                chart.getContentPane()).x;
            int previousStart = SwingUtilities.convertPoint(previous.getParent(), previous.getX(), 0, chart.getContentPane()).x;
            int nextEnd = SwingUtilities.convertPoint(next.getParent(), next.getX() + next.getWidth(), 0,
                chart.getContentPane()).x;

            assertTrue(previousStart - rangesEnd >= 12, "at the smallest window (" + chart.getWidth() + " wide) Previous"
                + " starts at " + previousStart + ", " + (previousStart - rangesEnd) + " px after the ranges end");
            assertTrue(nextEnd <= chart.getContentPane().getWidth(), "Next runs off the window");

            return null;
        });
    }

    /**
     * Date labels sit under the bars every few bars, and no two touch, whatever the range and however narrow the
     * window.
     *
     * MUTATION: in `UsageChartPanel.dateLabels`, label every bar (`every = 1`), and the 90-day range runs its dates
     * together.
     */
    @Test(timeOut = 120000)
    public void testTheDateLabelsNeverCollide() throws Exception
    {
        UsageChart chart = open();

        int[][] sizes = {{0, 0}, {520, 360}};

        for (int[] size : sizes)
        {
            if (size[0] > 0)
            {
                Components.onTheEventThread(() ->
                {
                    chart.setSize(size[0], size[1]);
                    chart.validate();

                    return null;
                });

                Components.settle();
            }

            for (String key : RANGES)
            {
                pick(chart, key);

                List<Rectangle> labels = Components.onTheEventThread(() -> panel(chart).dateLabelBounds());

                int width = Components.onTheEventThread(() -> panel(chart).getWidth());

                assertTrue(labels.size() >= 2, I18n.t(key) + " at " + width + " pixels has " + labels.size() + " date labels");

                for (int i = 0; i < labels.size(); i++)
                {
                    assertTrue(labels.get(i).x >= 0 && labels.get(i).x + labels.get(i).width <= width,
                        I18n.t(key) + ": a date label runs off the chart");

                    if (i > 0)
                    {
                        assertTrue(labels.get(i).x >= labels.get(i - 1).x + labels.get(i - 1).width + 4,
                            I18n.t(key) + " at " + width + " pixels: date labels " + (i - 1) + " and " + i + " collide");
                    }
                }
            }
        }
    }

    /**
     * The window opens in the application's look (FlatLaf's title bar and fonts, not Metal's) and wears its icon.
     *
     * MUTATION: take `TrainControlUI.applyWindowIcon(this)` out of the constructor, or the look and feel install out of
     * the static block, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheWindowWearsTheApplicationsLookAndIcon() throws Exception
    {
        UsageChart chart = open();

        assertTrue(Components.onTheEventThread(() -> chart.getRootPane().getUI())
            instanceof com.formdev.flatlaf.ui.FlatRootPaneUI, "the chart is not in the application's look and feel");
        assertNotNull(Components.onTheEventThread(chart::getIconImage), "the chart has no icon");
        assertEquals(Components.onTheEventThread(chart::getTitle), I18n.t("stats.chart.title"), "the chart's title");
        assertTrue(Components.onTheEventThread(chart::isResizable), "the chart cannot be resized");
    }

    /**
     * The chart fills the window as it is resized: wider, the bars widen and the last still ends inside the chart.
     *
     * MUTATION: in `UsageChartPanel.plot`, fix the width (`Math.max(1, 640 - left - right)`), and the bars stop
     * following the window.
     */
    @Test(timeOut = 120000)
    public void testTheChartFollowsTheWindowsSize() throws Exception
    {
        UsageChart chart = open();

        Rectangle narrow = barBounds(chart, 0);

        int before = Components.onTheEventThread(() -> panel(chart).getWidth());

        Components.onTheEventThread(() ->
        {
            chart.setSize(chart.getWidth() + 300, chart.getHeight() + 100);
            chart.validate();

            return null;
        });

        Components.settle();

        int after = Components.onTheEventThread(() -> panel(chart).getWidth());

        assertTrue(after >= before + 250, "the chart is " + after + " pixels wide after the window grew from " + before);

        Rectangle wide = barBounds(chart, 0);
        Rectangle lastBar = barBounds(chart, 29);

        assertTrue(wide.width > narrow.width, "the bars did not widen with the window (" + narrow.width + " then " + wide.width + ")");
        assertTrue(lastBar.x + lastBar.width <= after && lastBar.x + lastBar.width >= after - 40,
            "the last bar ends at " + (lastBar.x + lastBar.width) + " in a chart " + after + " wide");
    }
}
