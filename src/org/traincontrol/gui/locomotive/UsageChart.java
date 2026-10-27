package org.traincontrol.gui.locomotive;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

/**
 * The locomotive usage chart: run time per day, week or month, over a range the operator picks, with Previous and Next
 * to scroll by one whole period.  The figures come from the model; the bars are {@link UsageChartPanel}'s.
 */
public class UsageChart extends JFrame
{
    // The application's look and feel, before this class can be instantiated.  A static block, not the constructor's
    // first line: `JFrame`'s constructor creates the root pane with whatever look and feel is installed then, so a
    // cold-built window (a test's) got `MetalRootPaneUI` and an operating-system title bar.  Headless-guarded because a
    // static initializer that throws leaves the class unusable for the process.
    static
    {
        if (!java.awt.GraphicsEnvironment.isHeadless()) TrainControlUI.installLookAndFeel();
    }

    /** What one period is, and what one bar is in it. */
    private enum Range
    {
        DAYS_7("stats.chart.range7"), DAYS_30("stats.chart.range30"), DAYS_90("stats.chart.range90"),
        YEAR("stats.chart.rangeYear"), ALL("stats.chart.rangeAll");

        final String key;

        Range(String key)
        {
            this.key = key;
        }

        /** Days in a period of day bars; 0 for the ranges of weeks and months. */
        int days()
        {
            return this == DAYS_7 ? 7 : this == DAYS_30 ? 30 : this == DAYS_90 ? 90 : 0;
        }
    }

    /** Weeks in a Year period. */
    private static final int YEAR_WEEKS = 52;

    /** All shows weeks, not months, while the history spans fewer months than this. */
    private static final int SHORT_HISTORY_MONTHS = 6;

    /** All shows at least this many weeks, so a first week of history is not one lonely bar. */
    private static final int MIN_WEEKS = 12;

    private final ViewListener model;

    private final UsageChartPanel chart = new UsageChartPanel();

    private final JLabel summary = new JLabel();

    private final JButton previous;

    private final JButton next;

    private final JButton reset;

    private Range range = Range.DAYS_30;

    /** Whole periods back from the current one. */
    private long page = 0;

    /**
     * Builds the chart on the 30 days ending today; the caller shows it.
     *
     * @param tcui the main window, whose model has the figures
     */
    public UsageChart(TrainControlUI tcui)
    {
        super(I18n.t("stats.chart.title"));

        this.model = tcui.getModel();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        TrainControlUI.applyWindowIcon(this);

        chart.setName("usageChart.panel");
        summary.setName("usageChart.summary");
        summary.setFont(summary.getFont().deriveFont(Font.BOLD));

        JPanel ranges = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ButtonGroup group = new ButtonGroup();
        Range[] all = Range.values();

        for (int i = 0; i < all.length; i++)
        {
            final Range r = all[i];

            JToggleButton pick = new JToggleButton(I18n.t(r.key), r == range);

            pick.setName("usageChart.range." + r.name());
            pick.setFocusable(false);
            pick.putClientProperty("JButton.segmentPosition",
                i == 0 ? "first" : i == all.length - 1 ? "last" : "middle");
            pick.addActionListener(e -> showRange(r));
            group.add(pick);
            ranges.add(pick);
        }

        previous = pagingButton("stats.chart.previous", "usageChart.previous");
        next = pagingButton("stats.chart.next", "usageChart.next");
        reset = pagingButton("stats.ui.reset", "usageChart.reset");

        previous.addActionListener(e -> scroll(1));
        next.addActionListener(e -> scroll(-1));
        reset.addActionListener(e -> scroll(-page));

        JPanel paging = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        paging.add(previous);
        paging.add(reset);
        paging.add(next);

        JPanel controls = new JPanel(new BorderLayout());

        controls.add(ranges, BorderLayout.WEST);
        controls.add(paging, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 10));

        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        top.add(controls, BorderLayout.NORTH);
        top.add(summary, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());

        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        content.add(top, BorderLayout.NORTH);
        content.add(chart, BorderLayout.CENTER);

        setContentPane(content);
        refresh();
        pack();
        setMinimumSize(new java.awt.Dimension(520, 360));
        setAlwaysOnTop(tcui.isAlwaysOnTop());
        setLocationRelativeTo(tcui);
    }

    private static JButton pagingButton(String textKey, String name)
    {
        JButton b = new JButton(I18n.t(textKey));

        b.setName(name);
        b.setFocusable(false);

        return b;
    }

    private void showRange(Range picked)
    {
        range = picked;
        page = 0;

        refresh();
    }

    private void scroll(long periods)
    {
        page = Math.max(0, page + periods);

        refresh();
    }

    /** Asks the model once for every bar and for the period's totals; painting asks nothing. */
    private void refresh()
    {
        LocalDate today = LocalDate.now();
        LocalDate earliest = model.getEarliestRunDay();

        List<LocalDate[]> spans = spans(range, page, today, earliest);

        DateTimeFormatter labels = DateTimeFormatter.ofPattern(byMonth(range, today, earliest) ? "MMM yyyy" : "MMM d",
            I18n.getLocale());

        List<UsageChartPanel.Bar> bars = new ArrayList<>();

        for (LocalDate[] span : spans)
        {
            bars.add(new UsageChartPanel.Bar(span[0], span[1], model.getRuntimeBetween(span[0], span[1]),
                model.getLocsRunBetween(span[0], span[1]), labels.format(span[0])));
        }

        // All starts at the earliest day anything ran, though its first bar is the whole week or month
        LocalDate first = range == Range.ALL && earliest != null ? earliest : spans.get(0)[0];
        LocalDate last = spans.get(spans.size() - 1)[1];

        summary.setText(UsageChartPanel.figures(first, last, model.getRuntimeBetween(first, last),
            model.getLocsRunBetween(first, last)));

        boolean paged = range != Range.ALL;

        previous.setEnabled(paged);
        next.setEnabled(paged && page > 0);
        reset.setEnabled(paged && page > 0);

        chart.setBars(bars);
    }

    /** Whether All is drawn in months: not while the history is short, which is drawn in weeks. */
    private static boolean byMonth(Range range, LocalDate today, LocalDate earliest)
    {
        return range == Range.ALL && earliest != null
            && ChronoUnit.MONTHS.between(YearMonth.from(earliest), YearMonth.from(today)) + 1 >= SHORT_HISTORY_MONTHS;
    }

    private static LocalDate weekStart(LocalDate day)
    {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * The bars of a period as first and last day each.  A week runs Monday to Sunday and a month from its 1st, each cut
     * at today.
     */
    private static List<LocalDate[]> spans(Range range, long page, LocalDate today, LocalDate earliest)
    {
        List<LocalDate[]> spans = new ArrayList<>();

        if (range.days() > 0)
        {
            LocalDate end = today.minusDays(page * range.days());

            for (int i = range.days() - 1; i >= 0; i--)
            {
                spans.add(new LocalDate[] {end.minusDays(i), end.minusDays(i)});
            }
        }
        else if (range == Range.YEAR)
        {
            LocalDate start = weekStart(today).minusWeeks(page * YEAR_WEEKS + YEAR_WEEKS - 1);

            for (int i = 0; i < YEAR_WEEKS; i++)
            {
                LocalDate from = start.plusWeeks(i);

                spans.add(new LocalDate[] {from, min(from.plusDays(6), today)});
            }
        }
        else
        {
            LocalDate begin = earliest == null ? today : earliest;
            YearMonth thisMonth = YearMonth.from(today);

            if (!byMonth(range, today, earliest))
            {
                LocalDate start = min(weekStart(begin), weekStart(today).minusWeeks(MIN_WEEKS - 1));

                for (LocalDate from = start; !from.isAfter(today); from = from.plusWeeks(1))
                {
                    spans.add(new LocalDate[] {from, min(from.plusDays(6), today)});
                }
            }
            else
            {
                for (YearMonth m = YearMonth.from(begin); !m.isAfter(thisMonth); m = m.plusMonths(1))
                {
                    spans.add(new LocalDate[] {m.atDay(1), min(m.atEndOfMonth(), today)});
                }
            }
        }

        return spans;
    }

    private static LocalDate min(LocalDate a, LocalDate b)
    {
        return a.isBefore(b) ? a : b;
    }
}
