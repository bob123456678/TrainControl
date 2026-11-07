package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.automation.StandingRules;
import org.traincontrol.automation.StandingRules.Answer;
import org.traincontrol.automation.StandingRules.Cell;
import org.traincontrol.automation.StandingRules.Row;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * "What's allowed here" (FR-111): draws what `Layout.standingRulesAt` answered, and holds no rule itself.  A cell names a
 * side only where the sides differ; Sent Through Here is one cell, as automatic and manual trips ask it alike.
 */
final class StationMatrixPanel extends JPanel
{
    private static final Color GRAY_NO = new Color(120, 120, 120);

    private static final Font CELL_FONT = new Font("Segoe UI", Font.PLAIN, 13);

    private final JPanel table = new JPanel(new GridBagLayout());
    private final JLabel problem = new JLabel();
    private final JLabel also = new JLabel();

    /** A cell as drawn, for a test to compare with the real doors: row, tier and answer per side. */
    private final Map<String, Map<String, Answer>> drawn = new java.util.LinkedHashMap<>();

    /** The width the two lines under the table wrap at, and the panel's. */
    private final int lineWidth;

    StationMatrixPanel()
    {
        super(new BorderLayout(0, 6));

        JPanel heading = new JPanel();

        heading.setLayout(new BoxLayout(heading, BoxLayout.X_AXIS));
        heading.setOpaque(false);
        heading.add(UiStyle.heading(I18n.t("autosetup.ui.stationMatrixHeading")));
        heading.add(javax.swing.Box.createHorizontalStrut(4));
        heading.add(mark(I18n.t("autosetup.ui.stationMatrixHelp")));

        add(heading, BorderLayout.NORTH);

        table.setName("station.matrix");
        table.setBackground(Color.WHITE);
        table.setBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)));

        problem.setName("station.matrixProblem");
        problem.setForeground(new Color(170, 0, 0));
        problem.setFont(CELL_FONT);
        problem.setVisible(false);

        also.setName("station.matrixAlso");
        also.setFont(CELL_FONT);
        also.setVisible(false);

        JPanel lines = new JPanel();

        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        lines.setOpaque(false);
        problem.setAlignmentX(LEFT_ALIGNMENT);
        also.setAlignmentX(LEFT_ALIGNMENT);
        lines.add(problem);
        lines.add(also);

        JPanel below = new JPanel(new BorderLayout(0, 6));

        below.setOpaque(false);
        below.add(table, BorderLayout.NORTH);
        below.add(lines, BorderLayout.CENTER);

        add(below, BorderLayout.CENTER);

        lineWidth = lockTheColumns();
    }

    /**
     * Sizes the table once, for the longest thing each column can say in this language and scale, so an answer changing
     * moves nothing.
     *
     * @return the table's width
     */
    private int lockTheColumns()
    {
        Insets gap = cellGap();

        int first = UiStyle.blackLabel(I18n.t("autosetup.ui.stationMatrixTrainsCanBe")).getPreferredSize().width;

        for (String key : new String[] {"autosetup.ui.stationRowSentHere", "autosetup.ui.stationRowSentThrough",
            "autosetup.ui.stationRowSentFrom", "autosetup.ui.stationRowTurned"})
        {
            first = Math.max(first, UiStyle.blackLabel(I18n.t(key)).getPreferredSize().width);
        }

        int answers = 0;

        for (Answer each : Answer.values()) answers = Math.max(answers, line(each, verdict(each)).getPreferredSize().width);

        for (TilePorts.Side side : TilePorts.Side.values())
        {
            for (String key : new String[] {"autosetup.ui.stationSideFrom", "autosetup.ui.stationSideTo"})
            {
                answers = Math.max(answers, line(Answer.YES, I18n.f(key, sideName(side.name()))).getPreferredSize().width);
            }
        }

        int[] widths =
        {
            first + gap.left + gap.right,
            Math.max(answers, autoHeading().getPreferredSize().width) + gap.left + gap.right,
            Math.max(answers, handHeading().getPreferredSize().width) + gap.left + gap.right
        };

        ((GridBagLayout) table.getLayout()).columnWidths = widths;

        return table.getInsets().left + table.getInsets().right + widths[0] + widths[1] + widths[2];
    }

    private static Insets cellGap()
    {
        return new Insets(5, 8, 5, 8);
    }

    private static JComponent autoHeading()
    {
        return withMark(I18n.t("autosetup.ui.stationMatrixAutomatically"), I18n.t("autosetup.ui.stationMatrixAutoHelp"));
    }

    private static JComponent handHeading()
    {
        return withMark(I18n.t("autosetup.ui.stationMatrixManually"), I18n.t("autosetup.ui.stationMatrixManualHelp")
            + "  " + I18n.t("autosetup.ui.stationMatrixHomeHelp"));
    }

    /** As wide as the table, whatever the lines under it hold. */
    @Override
    public java.awt.Dimension getPreferredSize()
    {
        java.awt.Dimension asked = super.getPreferredSize();

        asked.width = lineWidth;

        return asked;
    }

    /**
     * @param rules what the railway answered, or null with `error` to say
     * @param error why every cell reads No, or null
     * @param alsoLine what a cell cannot say: the length, the list, what must be free; or null
     */
    void show(StandingRules rules, String error, String alsoLine)
    {
        table.removeAll();
        drawn.clear();

        GridBagConstraints c = new GridBagConstraints();

        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.insets = cellGap();

        heading(c, 0, UiStyle.blackLabel(I18n.t("autosetup.ui.stationMatrixTrainsCanBe")));
        heading(c, 1, autoHeading());
        heading(c, 2, handHeading());

        row(c, Row.SENT_HERE, "autosetup.ui.stationRowSentHere", "autosetup.ui.stationSideFrom", rules, error);
        row(c, Row.SENT_THROUGH, "autosetup.ui.stationRowSentThrough", "autosetup.ui.stationSideFrom", rules, error);
        row(c, Row.SENT_FROM, "autosetup.ui.stationRowSentFrom", "autosetup.ui.stationSideTo", rules, error);
        row(c, Row.TURNED_ROUND, "autosetup.ui.stationRowTurned", "autosetup.ui.stationSideFrom", rules, error);

        problem.setText(error == null ? "" : wrapAt(error));
        problem.setVisible(error != null);

        also.setText(alsoLine == null ? "" : wrapAt(alsoLine));
        also.setVisible(alsoLine != null);

        revalidate();
        repaint();
    }

    private String wrapAt(String text)
    {
        return AutonomyEditorPanel.wrappedAt(text, StationWindowBase.wrapWithin(lineWidth));
    }

    private void heading(GridBagConstraints c, int x, JComponent part)
    {
        c.gridx = x;
        c.weightx = x == 0 ? 0 : 1;

        table.add(part, c);
    }

    private void row(GridBagConstraints c, Row row, String labelKey, String sideKey, StandingRules rules, String error)
    {
        c.gridy++;
        c.gridx = 0;
        c.weightx = 0;

        JLabel label = UiStyle.blackLabel(I18n.t(labelKey));

        table.add(label, c);

        Map<String, Cell> auto = rules == null ? null : rules.cells(row, false);
        Map<String, Cell> hand = rules == null ? null : rules.cells(row, true);

        String name = I18n.t(labelKey);

        if (row == Row.SENT_THROUGH)
        {
            c.gridx = 1;
            c.gridwidth = 2;
            c.weightx = 1;

            table.add(cell(row, "both", name, auto, sideKey, error), c);

            c.gridwidth = 1;

            return;
        }

        c.gridx = 1;
        c.weightx = 1;

        table.add(cell(row, "auto", I18n.f("autosetup.ui.stationCellTitle", name, I18n.t("autosetup.ui.stationMatrixAutomatically")),
            auto, sideKey, error), c);

        c.gridx = 2;

        table.add(cell(row, "hand", I18n.f("autosetup.ui.stationCellTitle", name, I18n.t("autosetup.ui.stationMatrixManually")),
            hand, sideKey, error), c);
    }

    private JComponent cell(Row row, String tier, String title, Map<String, Cell> cells, String sideKey, String error)
    {
        JPanel out = new JPanel();

        out.setLayout(new BoxLayout(out, BoxLayout.Y_AXIS));
        out.setOpaque(false);
        out.setName("station.cell." + row + "." + tier);

        Map<String, Answer> answers = new java.util.LinkedHashMap<>();

        drawn.put(row + "." + tier, answers);

        if (cells == null || cells.isEmpty())
        {
            out.add(line(Answer.NO, I18n.t("autosetup.ui.stationAnswerNo")));

            out.setToolTipText(AutonomyEditorPanel.wrapped(error == null ? title : title + ": " + error));

            tipOnChildren(out);

            return out;
        }

        Set<Answer> different = new LinkedHashSet<>();

        List<String> why = new ArrayList<>();

        for (Map.Entry<String, Cell> each : cells.entrySet())
        {
            different.add(each.getValue().getAnswer());
            answers.put(each.getKey(), each.getValue().getAnswer());
        }

        if (different.size() == 1)
        {
            Answer answer = different.iterator().next();

            out.add(line(answer, verdict(answer)));

            Set<String> sentences = new LinkedHashSet<>();

            for (Cell each : cells.values()) sentences.add(each.getWhy());

            if (sentences.size() == 1) why.addAll(sentences);
            else for (Map.Entry<String, Cell> each : cells.entrySet()) why.add(sideSentence(sideKey, each));
        }
        else
        {
            // Only the sides something may happen on: the tooltip says why the rest may not
            for (Map.Entry<String, Cell> each : cells.entrySet())
            {
                if (each.getValue().getAnswer() != Answer.NO)
                {
                    out.add(line(each.getValue().getAnswer(), I18n.f(sideKey, sideName(each.getKey()))));
                }

                why.add(sideSentence(sideKey, each));
            }
        }

        out.setToolTipText(AutonomyEditorPanel.wrapped(title + ": " + String.join("  ", why)));

        tipOnChildren(out);

        return out;
    }

    private static String sideSentence(String sideKey, Map.Entry<String, Cell> each)
    {
        return each.getKey().isEmpty() ? each.getValue().getWhy()
            : I18n.f("autosetup.ui.stationCellSide", I18n.f(sideKey, sideName(each.getKey())), each.getValue().getWhy());
    }

    private static String sideName(String side)
    {
        return side.isEmpty() ? "" : I18n.t("autosetup.ui.side" + side);
    }

    private static String verdict(Answer answer)
    {
        switch (answer)
        {
            case YES: return I18n.t("autosetup.ui.stationAnswerYes");
            case SOME: return I18n.t("autosetup.ui.stationAnswerSome");
            default: return I18n.t("autosetup.ui.stationAnswerNo");
        }
    }

    /** A child's mouse would otherwise hide the cell's tooltip. */
    private static void tipOnChildren(JComponent out)
    {
        for (Component part : out.getComponents())
        {
            if (part instanceof JComponent) ((JComponent) part).setToolTipText(out.getToolTipText());
        }
    }

    private static JLabel line(Answer answer, String text)
    {
        JLabel label = new JLabel(text, iconOf(answer), JLabel.LEFT);

        label.setFont(CELL_FONT);
        label.setForeground(answer == Answer.NO ? GRAY_NO : Color.BLACK);

        return label;
    }

    private static JComponent withMark(String text, String help)
    {
        JPanel out = new JPanel();

        out.setLayout(new BoxLayout(out, BoxLayout.X_AXIS));
        out.setOpaque(false);
        out.add(UiStyle.blackLabel(text));
        out.add(javax.swing.Box.createHorizontalStrut(4));
        out.add(mark(help));

        return out;
    }

    private static JLabel mark(String help)
    {
        JLabel mark = InfoMark.label(help);

        mark.setFocusable(true);

        return mark;
    }

    /** @return what is drawn for this row and tier ("auto", "hand", or "both"): side to answer */
    Map<String, Answer> drawnFor(Row row, String tier)
    {
        return drawn.get(row + "." + tier);
    }

    /** A tick, half a disc or a dash, drawn: these glyphs are in no font this application can count on. */
    private static Icon iconOf(Answer answer)
    {
        if (answer == Answer.YES) return RowIcons.answer(14, RowIcons.Answer.YES, new Color(31, 122, 58));

        return answer == Answer.SOME ? RowIcons.answer(14, RowIcons.Answer.SOME, new Color(154, 91, 11))
            : RowIcons.answer(14, RowIcons.Answer.NO, GRAY_NO);
    }
}
