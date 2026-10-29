package org.traincontrol.gui.locomotive;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.I18n;

/**
 * The bars of the locomotive usage chart: drawing and hover only.  Where the figures come from, and how a period is cut
 * into bars, is {@link UsageChart}'s.  Every color is asked of the look and feel when painting, so a theme change shows
 * at the next repaint.
 */
public class UsageChartPanel extends JComponent
{
    /** One bar: a span of days with the run time, the number of locomotives that ran in it, and what each ran. */
    public static final class Bar
    {
        public final LocalDate first;
        public final LocalDate last;
        public final long millis;
        public final int locs;
        public final String label;

        /** Each locomotive's name and run time in the span, the most run first. */
        public final List<Map.Entry<String, Long>> ran;

        public Bar(LocalDate first, LocalDate last, long millis, int locs, String label,
            List<Map.Entry<String, Long>> ran)
        {
            this.first = first;
            this.last = last;
            this.millis = millis;
            this.locs = locs;
            this.label = label;
            this.ran = ran;
        }
    }

    private static final long HOUR_MS = 3600000L;

    /** Locomotives a bar's tooltip names. */
    private static final int TOP_LOCOMOTIVES = 3;

    private static final int BAR_RADIUS = 4;

    private static final int MAX_BAR_WIDTH = 56;

    /** Steps for the hours axis, so the ticks are whole hours and round ones. */
    private static final int[] STEPS = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};

    private List<Bar> bars = Collections.emptyList();

    private int axisStep = 1;

    private int axisMax = 1;

    private int hover = -1;

    public UsageChartPanel()
    {
        setOpaque(true);
        setPreferredSize(new Dimension(880, 300));
        setMinimumSize(new Dimension(360, 200));

        // Registered once; the text itself comes from getToolTipText as the pointer moves between bars.
        ToolTipManager.sharedInstance().registerComponent(this);

        MouseAdapter pointer = new MouseAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                hoverOver(barAt(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                hoverOver(-1);
            }
        };

        addMouseMotionListener(pointer);
        addMouseListener(pointer);
    }

    /** Shows these bars, with the axis scaled to the tallest. */
    public void setBars(List<Bar> shown)
    {
        this.bars = new ArrayList<>(shown);
        this.hover = -1;

        long tallest = 0;

        for (Bar b : bars) tallest = Math.max(tallest, b.millis);

        double hours = tallest / (double) HOUR_MS;
        double wanted = Math.max(1.0, hours / 4);

        axisStep = STEPS[STEPS.length - 1];

        for (int s : STEPS)
        {
            if (s >= wanted)
            {
                axisStep = s;

                break;
            }
        }

        axisMax = Math.max(axisStep, (int) Math.ceil(hours / axisStep) * axisStep);

        repaint();
    }

    /** The run time and how many locomotives ran, without the dates: the line above the chart, whose dates sit below it. */
    public static String totals(long millis, int locs)
    {
        return I18n.f("stats.chart.totals", Conversion.convertSecondsToHMm(millis), locs,
            locs == 1 ? I18n.t("stats.ui.labelLocomotiveSingular") : I18n.t("stats.ui.labelLocomotivePlural"));
    }

    public int barCount()
    {
        return bars.size();
    }

    public Bar bar(int i)
    {
        return bars.get(i);
    }

    /** The top of the axis, in whole hours. */
    public int axisMaxHours()
    {
        return axisMax;
    }

    /** Hours between ticks: half an hour while the axis tops out at one hour, else the whole-hour step. */
    private double tickStep()
    {
        return axisMax == 1 ? 0.5 : axisStep;
    }

    /** The labels of the hours axis, from 0 up, in the locale's number format. */
    public List<String> tickLabels()
    {
        NumberFormat format = NumberFormat.getNumberInstance(I18n.getLocale());

        format.setMaximumFractionDigits(1);

        List<String> labels = new ArrayList<>();

        for (double hours = 0; hours <= axisMax; hours += tickStep()) labels.add(format.format(hours));

        return labels;
    }

    /** The bar under the pointer, or -1; the whole column counts, so a bar too short to hit is still found. */
    public int barAt(Point p)
    {
        Rectangle plot = plot();

        if (bars.isEmpty() || !plot.contains(p)) return -1;

        return Math.min(bars.size() - 1, (int) ((p.x - plot.x) / slotWidth(plot)));
    }

    /** Where bar i is drawn; no height when it has no run time. */
    public Rectangle barBounds(int i)
    {
        Rectangle plot = plot();

        double slot = slotWidth(plot);

        int width = (int) Math.max(1, Math.min(MAX_BAR_WIDTH, Math.round(slot * 0.68)));

        int x = plot.x + (int) Math.round(i * slot + (slot - width) / 2);

        long millis = bars.get(i).millis;

        int height = millis <= 0 ? 0 : Math.max(1, (int) Math.round(plot.height * millis / (axisMax * (double) HOUR_MS)));

        return new Rectangle(x, plot.y + plot.height - height, width, height);
    }

    @Override
    public String getToolTipText(MouseEvent e)
    {
        int i = barAt(e.getPoint());

        if (i < 0) return null;

        Bar b = bars.get(i);

        String dates = b.first.equals(b.last)
            ? b.first.toString() : I18n.f("stats.chart.dates", b.first.toString(), b.last.toString());

        // The date, how many ran and for how long, then a blank line and the most-run locomotives (Adam's layout)
        StringBuilder html = new StringBuilder("<html>").append(escaped(dates)).append("<br>")
            .append(escaped(I18n.f("stats.chart.tipTotals", Conversion.convertSecondsToHMm(b.millis), b.locs,
                b.locs == 1 ? I18n.t("stats.ui.labelLocomotiveSingular") : I18n.t("stats.ui.labelLocomotivePlural"))));

        if (!b.ran.isEmpty()) html.append("<br>");

        for (int k = 0; k < Math.min(TOP_LOCOMOTIVES, b.ran.size()); k++)
        {
            html.append("<br>").append(escaped(I18n.f("stats.chart.topLoc", b.ran.get(k).getKey(),
                Conversion.convertSecondsToHMm(b.ran.get(k).getValue()))));
        }

        if (b.ran.size() > TOP_LOCOMOTIVES)
        {
            html.append("<br>").append(escaped(I18n.f("stats.chart.more", b.ran.size() - TOP_LOCOMOTIVES)));
        }

        return html.append("</html>").toString();
    }

    private static String escaped(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void hoverOver(int i)
    {
        if (i == hover) return;

        hover = i;

        repaint();
    }

    private double slotWidth(Rectangle plot)
    {
        return plot.width / (double) Math.max(1, bars.size());
    }

    private Font tickFont()
    {
        Font base = UIManager.getFont("Label.font");

        if (base == null) base = getFont();

        return base.deriveFont(Math.max(9f, base.getSize2D() - 1f));
    }

    /** The area the bars stand in: room left for the hour labels, above for their caption, below for the dates. */
    public Rectangle plot()
    {
        FontMetrics fm = getFontMetrics(tickFont());

        int widest = 0;

        for (String label : tickLabels()) widest = Math.max(widest, fm.stringWidth(label));

        int left = widest + 18;
        int top = fm.getHeight() + 10;
        int bottom = fm.getHeight() + 12;
        int right = 14;

        return new Rectangle(left, top, Math.max(1, getWidth() - left - right), Math.max(1, getHeight() - top - bottom));
    }

    private static Color blend(Color from, Color to, double toShare)
    {
        return new Color(
            (int) Math.round(from.getRed() * (1 - toShare) + to.getRed() * toShare),
            (int) Math.round(from.getGreen() * (1 - toShare) + to.getGreen() * toShare),
            (int) Math.round(from.getBlue() * (1 - toShare) + to.getBlue() * toShare));
    }

    private static Color themeColor(Color fallback, String... keys)
    {
        for (String key : keys)
        {
            Color c = UIManager.getColor(key);

            if (c != null) return c;
        }

        return fallback;
    }

    /** The bar color: the theme's accent. */
    public Color barColor()
    {
        return themeColor(new Color(0x26, 0x75, 0xBF), "Component.accentColor", "Button.default.background",
            "ProgressBar.foreground");
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Color background = themeColor(Color.WHITE, "Panel.background");
            Color foreground = themeColor(Color.BLACK, "Label.foreground");

            g2.setColor(background);
            g2.fillRect(0, 0, getWidth(), getHeight());

            Font font = tickFont();
            FontMetrics fm = g2.getFontMetrics(font);
            Rectangle plot = plot();

            g2.setFont(font);

            drawAxis(g2, fm, plot, blend(foreground, background, 0.88), blend(foreground, background, 0.35));
            drawBars(g2, plot, background, foreground);
            drawDates(g2, fm, blend(foreground, background, 0.35));
        }
        finally
        {
            g2.dispose();
        }
    }

    private void drawAxis(Graphics2D g2, FontMetrics fm, Rectangle plot, Color gridColor, Color textColor)
    {
        g2.setStroke(new BasicStroke(1f));

        List<String> labels = tickLabels();

        for (int k = 0; k < labels.size(); k++)
        {
            int y = plot.y + plot.height - Math.round(plot.height * (float) (k * tickStep() / axisMax));

            g2.setColor(gridColor);
            g2.draw(new java.awt.geom.Line2D.Float(plot.x, y + 0.5f, plot.x + plot.width, y + 0.5f));

            String tick = labels.get(k);

            g2.setColor(textColor);
            g2.drawString(tick, plot.x - 8 - fm.stringWidth(tick), y + fm.getAscent() / 2 - 1);
        }

        g2.drawString(I18n.t("stats.chart.axisHours"), 4, fm.getAscent() + 2);
    }

    private void drawBars(Graphics2D g2, Rectangle plot, Color background, Color foreground)
    {
        Color bar = barColor();

        if (hover >= 0 && hover < bars.size())
        {
            double slot = slotWidth(plot);

            int from = plot.x + (int) Math.round(hover * slot);
            int to = plot.x + (int) Math.round((hover + 1) * slot);

            g2.setColor(blend(background, foreground, 0.05));
            g2.fillRect(from, plot.y, to - from, plot.height);
        }

        for (int i = 0; i < bars.size(); i++)
        {
            Rectangle r = barBounds(i);

            if (r.height == 0) continue;

            int radius = Math.min(BAR_RADIUS, Math.min(r.width / 2, r.height));

            // A rounded rectangle taller than the bar, cut flat at the baseline: round at the top only.
            Area shape = new Area(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height + radius, radius * 2f, radius * 2f));

            shape.intersect(new Area(r));

            g2.setColor(i == hover ? blend(bar, foreground, 0.18) : bar);
            g2.fill(shape);
        }
    }

    /** Dates under the bars, every few bars so that no two labels meet. */
    private void drawDates(Graphics2D g2, FontMetrics fm, Color textColor)
    {
        g2.setColor(textColor);

        for (int[] at : dateLabels(fm))
        {
            g2.drawString(bars.get(at[0]).label, at[1], at[2] + fm.getAscent());
        }
    }

    /** Where the date labels are drawn, each as {bar, left, top, width}; from the newest bar back, so it is labeled. */
    private List<int[]> dateLabels(FontMetrics fm)
    {
        Rectangle plot = plot();

        int widest = 0;

        for (Bar b : bars) widest = Math.max(widest, fm.stringWidth(b.label));

        int every = Math.max(1, (int) Math.ceil((widest + 12) / slotWidth(plot)));

        List<int[]> labels = new ArrayList<>();

        for (int i = bars.size() - 1; i >= 0; i -= every)
        {
            Rectangle r = barBounds(i);

            int width = fm.stringWidth(bars.get(i).label);

            int x = r.x + r.width / 2 - width / 2;

            if (x < plot.x - 4 || x + width > getWidth()) continue;

            labels.add(new int[] {i, x, plot.y + plot.height + 8, width});
        }

        return labels;
    }

    /** The rectangles the date labels fill, left to right. */
    public List<Rectangle> dateLabelBounds()
    {
        FontMetrics fm = getFontMetrics(tickFont());

        List<Rectangle> bounds = new ArrayList<>();

        for (int[] at : dateLabels(fm)) bounds.add(0, new Rectangle(at[1], at[2], at[3], fm.getHeight()));

        return bounds;
    }
}
