package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.base.RenameProposals.Row;
import org.traincontrol.gui.widgets.ChoiceTable;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The manual Sync's table (FR-249): one row per locomotive the Central Station differs on, each answered before
 * anything changes.  Every row starts on Ask Again, an ignored locomotive's on Ignore.
 */
public final class SyncDifferencesWindow extends JDialog
{
    /** A row's answer; RENAME and ADDRESS are its two Apply choices. */
    public enum Answer { KEEP, RENAME, ADDRESS, BOTH, IGNORE, ASK }

    private final RenameProposals proposals;
    private final Map<Row, Map<Answer, JRadioButton>> choices = new LinkedHashMap<>();
    private final Map<Row, JLabel> triangles = new LinkedHashMap<>();
    private final Map<Row, JLabel> deleting = new LinkedHashMap<>();
    private boolean continued;

    private SyncDifferencesWindow(Window owner, RenameProposals proposals, List<Row> rows)
    {
        super(owner, I18n.t("sync.ui.title"), ModalityType.APPLICATION_MODAL);
        this.proposals = proposals;
        ChoiceTable table = new ChoiceTable(I18n.t("sync.ui.locomotive"), I18n.t("sync.ui.stationHas"),
            I18n.t("sync.ui.keep"), I18n.t("sync.ui.apply"), I18n.t("sync.ui.keepBoth"), I18n.t("sync.ui.ignore"),
            I18n.t("sync.ui.askAgain"));
        for (Row r : rows) table.addRow(addRow(r));
        refresh();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.setOpaque(false);
        top.add(UiStyle.heading(rows.size() == 1 ? I18n.t("sync.ui.headingOne")
            : I18n.f("sync.ui.heading", rows.size())));
        top.add(InfoMark.label(I18n.t("sync.ui.info")));
        JPanel setAll = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        setAll.setOpaque(false);
        setAll.add(UiStyle.blackLabel(I18n.t("sync.ui.setAll")));
        setAll.add(setAllTo("sync.ui.keep", r -> Answer.KEEP));
        setAll.add(setAllTo("sync.ui.apply", r -> !proposals.applicable(this.choices.keySet()).contains(r) ? null
            : r.asksName() ? Answer.RENAME : Answer.ADDRESS));
        setAll.add(setAllTo("sync.ui.keepBoth", r -> Answer.BOTH));
        setAll.add(setAllTo("sync.ui.askAgain", r -> Answer.ASK));
        JButton cancel = UiStyle.button(new JButton(I18n.t("ui.cancel")));
        JButton go = UiStyle.button(new JButton(I18n.t("sync.ui.continue")));
        cancel.addActionListener(e -> dispose());
        go.addActionListener(e ->
        {
            this.continued = true;
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(go);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(setAll, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(table, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        // Closing is Cancel; Enter continues, which changes nothing until a row is answered
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(go);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Asks over owner about every row with something to answer, ignored ones included, and returns once it is closed:
     * the answers by row, empty with nothing to ask, or null when canceled or closed.  On the event thread.
     */
    public static Map<Row, Answer> ask(Window owner, RenameProposals proposals)
    {
        List<Row> rows = new ArrayList<>();
        for (Row r : proposals.getRows()) if (r.asksName() || r.asksAddress()) rows.add(r);
        if (rows.isEmpty()) return new LinkedHashMap<>();
        SyncDifferencesWindow w = new SyncDifferencesWindow(owner, proposals, rows);
        w.setVisible(true);
        if (!w.continued) return null;
        Map<Row, Answer> answers = new LinkedHashMap<>();
        for (Map.Entry<Row, Map<Answer, JRadioButton>> each : w.choices.entrySet())
        {
            for (Map.Entry<Answer, JRadioButton> c : each.getValue().entrySet())
                if (c.getValue().isSelected()) answers.put(each.getKey(), c.getKey());
        }
        return answers;
    }

    /** One row's cells: the locomotive, what the station has, and its choices, named for the tests. */
    private JComponent[] addRow(Row r)
    {
        Locomotive l = r.loco;
        String name = l.getName();
        ButtonGroup group = new ButtonGroup();
        Map<Answer, JRadioButton> radios = new EnumMap<>(Answer.class);
        JPanel has = column(), apply = column();
        if (r.asksName())
        {
            has.add(kindAndValue("sync.ui.name", r.stationName));
            apply.add(applyChoice(r, radios, group, Answer.RENAME, I18n.f("sync.ui.rename", name, r.stationName),
                r.whyNot(true)));
            JLabel deletes = mark(name + ":deletes", null);
            this.deleting.put(r, deletes);
            apply.add(deletes);
        }
        if (r.asksAddress())
        {
            has.add(kindAndValue("sync.ui.address", r.stationAddress.toString()));
            apply.add(applyChoice(r, radios, group, Answer.ADDRESS, I18n.f("sync.ui.changeAddress", name,
                r.stationAddress), r.whyNot(false)));
            JLabel triangle = mark(name + ":collision", RowIcons.warning(16));
            triangle.setToolTipText(I18n.f("sync.ui.collision", r.stationAddress));
            this.triangles.put(r, triangle);
            apply.add(triangle);
        }
        for (Answer a : new Answer[] {Answer.KEEP, Answer.BOTH, Answer.IGNORE, Answer.ASK})
        {
            // Keep Both adds the station's locomotive, so only where its name is free here
            if (a != Answer.BOTH || r.asksName() && r.nameHolder == null && r.station != null)
                radios.put(a, named(ChoiceTable.choice(group, ""), name, a));
        }
        radios.get(l.isSyncIgnored() ? Answer.IGNORE : Answer.ASK).setSelected(true);
        for (JRadioButton b : radios.values()) b.addItemListener(e -> refresh());
        this.choices.put(r, radios);
        JLabel loco = new JLabel(name + (l.hasLinkedLocomotives() ? " +" + l.getLinkedLocomotives().size() : ""));
        loco.setFont(UiStyle.TEXT_FONT);
        JLabel at = new JLabel(l.getDecoderTypeLabel() + " " + l.getAddress());
        at.setFont(UiStyle.TEXT_FONT);
        at.setForeground(Color.GRAY);
        JPanel who = column();
        who.add(loco);
        who.add(at);
        return new JComponent[] {who, has, radios.get(Answer.KEEP), apply, radios.get(Answer.BOTH),
            radios.get(Answer.IGNORE), radios.get(Answer.ASK)};
    }

    private static JRadioButton applyChoice(Row r, Map<Answer, JRadioButton> radios, ButtonGroup group, Answer a,
        String text, String whyNot)
    {
        JRadioButton b = named(ChoiceTable.choice(group, text), r.loco.getName(), a);
        // Grayed with its reason; asked again next Sync
        b.setEnabled(whyNot == null);
        b.setToolTipText(whyNot);
        radios.put(a, b);
        return b;
    }

    private static JRadioButton named(JRadioButton b, String name, Answer a)
    {
        b.setName(name + ":" + a.name());
        return b;
    }

    private static JLabel mark(String name, javax.swing.Icon icon)
    {
        JLabel m = new JLabel(icon);
        m.setName(name);
        m.setFont(UiStyle.TEXT_FONT);
        m.setVisible(false);
        return m;
    }

    private static JPanel column()
    {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setOpaque(false);
        return p;
    }

    /** "Name:" or "Address:" as a gray label, and the station's value as plain text. */
    private static JPanel kindAndValue(String kind, String value)
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel k = UiStyle.blackLabel(I18n.t(kind));
        k.setForeground(Color.GRAY);
        JLabel v = new JLabel(value);
        v.setFont(UiStyle.TEXT_FONT);
        p.add(k);
        p.add(v);
        return p;
    }

    /** A Set all button: each row not ignored takes the answer `to` gives it, where it has that choice enabled. */
    private JButton setAllTo(String key, java.util.function.Function<Row, Answer> to)
    {
        JButton b = UiStyle.button(new JButton(I18n.t(key)));
        b.addActionListener(e ->
        {
            Map<Row, Answer> picks = new LinkedHashMap<>();
            for (Row r : this.choices.keySet()) if (!r.loco.isSyncIgnored()) picks.put(r, to.apply(r));
            for (Map.Entry<Row, Answer> p : picks.entrySet())
            {
                JRadioButton c = p.getValue() == null ? null : this.choices.get(p.getKey()).get(p.getValue());
                if (c != null && c.isEnabled()) c.setSelected(true);
            }
        });
        return b;
    }

    /** The marks, judged on every Apply chosen now with the row's own: the triangle and what a rename deletes. */
    private void refresh()
    {
        List<Row> moved = chosen(Answer.ADDRESS::equals), renamed = chosen(Answer.RENAME::equals);
        for (Map.Entry<Row, JLabel> t : this.triangles.entrySet())
            t.getValue().setVisible(this.proposals.collisions(with(moved, t.getKey())).contains(t.getKey()));
        for (Map.Entry<Row, JLabel> d : this.deleting.entrySet())
        {
            Locomotive gone = this.proposals.deletes(d.getKey(), with(renamed, d.getKey()));
            d.getValue().setVisible(gone != null);
            if (gone != null) d.getValue().setText(I18n.f("sync.ui.deletes", gone.getName()
                + (gone.hasLinkedLocomotives() ? " +" + gone.getLinkedLocomotives().size() : "")));
        }
    }

    private List<Row> chosen(Predicate<Answer> which)
    {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<Row, Map<Answer, JRadioButton>> each : this.choices.entrySet())
        {
            for (Map.Entry<Answer, JRadioButton> c : each.getValue().entrySet())
                if (c.getValue().isSelected() && which.test(c.getKey())) out.add(each.getKey());
        }
        return out;
    }

    private static List<Row> with(Collection<Row> rows, Row r)
    {
        List<Row> out = new ArrayList<>(rows);
        if (!out.contains(r)) out.add(r);
        return out;
    }
}
