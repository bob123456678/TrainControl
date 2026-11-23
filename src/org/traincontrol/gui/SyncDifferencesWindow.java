package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
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
public final class SyncDifferencesWindow
{
    /** A row's answer; RENAME and ADDRESS are its two Apply choices. */
    public enum Answer { KEEP, RENAME, ADDRESS, BOTH, IGNORE, ASK }

    /** A grayed Apply's text: darker than a disabled control's, so it stays readable. */
    private static final Color GRAYED = new Color(110, 110, 110);
    /** "Deletes X" after a rename: a consequence to read before choosing it. */
    private static final Color DELETES = new Color(160, 0, 0);

    private final JDialog dialog;
    private final RenameProposals proposals;
    private final Map<Row, Map<Answer, JRadioButton>> choices = new LinkedHashMap<>();
    private final Map<Row, JLabel> triangles = new LinkedHashMap<>();
    private final Map<Row, JLabel> deleting = new LinkedHashMap<>();
    private boolean continued;
    private boolean settingAll;

    private SyncDifferencesWindow(Window owner, RenameProposals proposals, List<Row> rows)
    {
        this.dialog = new JDialog(owner, I18n.t("sync.ui.title"), JDialog.ModalityType.APPLICATION_MODAL);
        this.proposals = proposals;
        // Each answer's heading sets every row it can to it, as a row's own pick would; Ignore is never set so
        ChoiceTable table = new ChoiceTable(UiStyle.blackLabel(I18n.t("sync.ui.thisComputer")),
            UiStyle.blackLabel(I18n.t("sync.ui.centralStation")), setAllTo("sync.ui.apply", (r, can) ->
            !can.contains(r) ? null : r.asksName() ? Answer.RENAME : Answer.ADDRESS),
            setAllTo("sync.ui.keep", (r, can) -> Answer.KEEP), setAllTo("sync.ui.keepBoth", (r, can) -> Answer.BOTH),
            UiStyle.blackLabel(I18n.t("sync.ui.ignore")), setAllTo("sync.ui.askAgain", (r, can) -> Answer.ASK));
        for (Row r : rows) table.addRow(addRow(r));
        refresh();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.setOpaque(false);
        top.add(UiStyle.heading(rows.size() == 1 ? I18n.t("sync.ui.headingOne")
            : I18n.f("sync.ui.heading", rows.size())));
        top.add(InfoMark.label(I18n.t("sync.ui.info")));
        JButton cancel = UiStyle.button(new JButton(I18n.t("ui.cancel")));
        JButton go = UiStyle.button(new JButton(I18n.t("sync.ui.continue")));
        cancel.addActionListener(e -> this.dialog.dispose());
        go.addActionListener(e ->
        {
            this.continued = true;
            this.dialog.dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(go);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(table, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        this.dialog.setContentPane(content);
        // Closing is Cancel; Enter continues, which changes nothing until a row is answered
        this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.dialog.getRootPane().setDefaultButton(go);
        this.dialog.getRootPane().registerKeyboardAction(e -> this.dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        this.dialog.pack();
        this.dialog.setLocationRelativeTo(owner);
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
        w.dialog.setVisible(true);
        return w.continued ? w.picks() : null;
    }

    /** One row's cells: this computer's locomotive, the station's, and the choices, named for the tests. */
    private JComponent[] addRow(Row r)
    {
        Locomotive l = r.loco;
        String name = l.getName();
        ButtonGroup group = new ButtonGroup();
        Map<Answer, JRadioButton> radios = new EnumMap<>(Answer.class);
        List<JComponent> apply = new ArrayList<>();
        if (r.asksName())
        {
            JLabel deletes = mark(name + ":deletes", null);
            deletes.setForeground(DELETES);
            deletes.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
            this.deleting.put(r, deletes);
            apply.add(applyChoice(r, radios, group, Answer.RENAME, I18n.f("sync.ui.rename", name, r.stationName),
                r.whyNot(true), deletes));
        }
        if (r.asksAddress())
        {
            JLabel triangle = mark(name + ":collision", RowIcons.warning(16));
            this.triangles.put(r, triangle);
            apply.add(applyChoice(r, radios, group, Answer.ADDRESS, I18n.f("sync.ui.changeAddress", name,
                r.stationAddress), r.whyNot(false), triangle));
        }
        for (Answer a : new Answer[] {Answer.KEEP, Answer.BOTH, Answer.IGNORE, Answer.ASK})
        {
            // Keep Both adds the station's locomotive, so only where its name is free here
            if (a != Answer.BOTH || r.asksName() && r.nameHolder == null && r.station != null)
                radios.put(a, named(ChoiceTable.choice(group, ""), name, a));
        }
        radios.get(l.isSyncIgnored() ? Answer.IGNORE : Answer.ASK).setSelected(true);
        // A heading's Set all judges the marks once, after every row it sets
        for (JRadioButton b : radios.values()) b.addItemListener(e -> { if (!this.settingAll) refresh(); });
        this.choices.put(r, radios);
        String at = l.getDecoderTypeLabel() + " ";
        return new JComponent[] {side(r, name + (l.hasLinkedLocomotives() ? " +" + l.getLinkedLocomotives().size()
            : ""), at + l.getAddress()), side(r, r.stationName != null ? r.stationName : name, at
            + (r.stationAddress != null ? r.stationAddress : l.getAddress())),
            ChoiceTable.lines(apply.toArray(new JComponent[0])), radios.get(Answer.KEEP), radios.get(Answer.BOTH),
            radios.get(Answer.IGNORE), radios.get(Answer.ASK)};
    }

    /** One side's name and address, a line each, the value that differs between the sides in bold. */
    private static JComponent side(Row r, String name, String address)
    {
        return ChoiceTable.lines(kindAndValue("sync.ui.name", name, r.stationName != null),
            kindAndValue("sync.ui.address", address, r.stationAddress != null));
    }

    /** One Apply choice on its line, its mark after it; grayed, its text stays readable and says why on hover. */
    private static JComponent applyChoice(Row r, Map<Answer, JRadioButton> radios, ButtonGroup group, Answer a,
        String text, String whyNot, JLabel mark)
    {
        JRadioButton b = named(ChoiceTable.choice(group, whyNot == null ? text : ""), r.loco.getName(), a);
        radios.put(a, b);
        if (whyNot == null) return ChoiceTable.line(b, mark);
        // Asked again next Sync
        b.setEnabled(false);
        b.setToolTipText(whyNot);
        JLabel said = text(text);
        said.setForeground(GRAYED);
        said.setToolTipText(whyNot);
        return ChoiceTable.line(b, said, mark);
    }

    private static JRadioButton named(JRadioButton b, String name, Answer a)
    {
        b.setName(name + ":" + a.name());
        return b;
    }

    private static JLabel text(String text)
    {
        JLabel t = new JLabel(text);
        t.setFont(UiStyle.TEXT_FONT);
        return t;
    }

    private static JLabel mark(String name, javax.swing.Icon icon)
    {
        JLabel m = text(null);
        m.setIcon(icon);
        m.setName(name);
        m.setVisible(false);
        return m;
    }

    /** "Name:" or "Address:" as a gray label, and the value as plain text, bold where it differs, on one line. */
    private static JComponent kindAndValue(String kind, String value, boolean differs)
    {
        JLabel k = UiStyle.blackLabel(I18n.t(kind)), v = text(value);
        k.setForeground(Color.GRAY);
        if (differs) v.setFont(UiStyle.TEXT_FONT.deriveFont(Font.BOLD));
        return ChoiceTable.line(k, v);
    }

    /** An answer's heading: each row not ignored takes the answer `to` gives it, and the rows Apply may take, if enabled. */
    private JButton setAllTo(String key, BiFunction<Row, Set<Row>, Answer> to)
    {
        return ChoiceTable.link(I18n.t(key), I18n.f("sync.ui.setAllTip", I18n.t(key)), () ->
        {
            Set<Row> can = this.proposals.applicable(this.choices.keySet(), chosen(Answer.ADDRESS));
            this.settingAll = true;
            for (Row r : this.choices.keySet())
            {
                JRadioButton c = r.loco.isSyncIgnored() ? null : this.choices.get(r).get(to.apply(r, can));
                if (c != null && c.isEnabled()) c.setSelected(true);
            }
            this.settingAll = false;
            refresh();
        });
    }

    /** The marks, judged with every Apply chosen now: the triangle, naming who else has that address, and Deletes. */
    private void refresh()
    {
        List<Row> moved = chosen(Answer.ADDRESS), renamed = chosen(Answer.RENAME);
        for (Map.Entry<Row, JLabel> t : this.triangles.entrySet())
        {
            List<String> others = new ArrayList<>();
            for (Locomotive o : this.proposals.sharing(t.getKey(), with(moved, t.getKey()))) others.add(o.getName());
            t.getValue().setVisible(!others.isEmpty());
            t.getValue().setToolTipText(I18n.f("sync.ui.collision", t.getKey().stationAddress,
                String.join(", ", others)));
        }
        for (Map.Entry<Row, JLabel> d : this.deleting.entrySet())
        {
            Locomotive gone = this.proposals.deletes(d.getKey(), with(renamed, d.getKey()));
            d.getValue().setVisible(gone != null);
            if (gone != null) d.getValue().setText(I18n.f("sync.ui.deletes", gone.getName()
                + (gone.hasLinkedLocomotives() ? " +" + gone.getLinkedLocomotives().size() : "")));
        }
    }

    /** Each row's selected answer, top to bottom. */
    private Map<Row, Answer> picks()
    {
        Map<Row, Answer> out = new LinkedHashMap<>();
        this.choices.forEach((r, radios) -> radios.forEach((a, b) -> { if (b.isSelected()) out.put(r, a); }));
        return out;
    }

    private List<Row> chosen(Answer a)
    {
        List<Row> out = new ArrayList<>();
        picks().forEach((r, p) -> { if (p == a) out.add(r); });
        return out;
    }

    private static List<Row> with(Collection<Row> rows, Row r)
    {
        List<Row> out = new ArrayList<>(rows);
        if (!out.contains(r)) out.add(r);
        return out;
    }
}
