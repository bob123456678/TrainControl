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
import javax.swing.Box;
import javax.swing.BoxLayout;
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
 * anything changes.  Every row starts on Ask Again, an ignored locomotive's on Ignore, and an mfx one differing only by
 * address on Apply where Set all would take it.  A by-hand row has no answers: nothing applies to it.
 */
public final class SyncDifferencesWindow
{
    /** A row's answer; KEEP is Keep As Is, RENAME and ADDRESS its two Apply choices. */
    public enum Answer { KEEP, RENAME, ADDRESS, BOTH, IGNORE, ASK }

    /** A grayed Apply's mark: darker than a disabled control's, so it stays readable. */
    static final Color GRAYED = new Color(110, 110, 110);
    /** "Deletes X" after a rename: a consequence to read before choosing it. */
    private static final Color DELETES = new Color(160, 0, 0);

    /** The window; `ask` shows it. */
    final JDialog dialog;
    private final RenameProposals proposals;
    private final ChoiceTable table;
    private final Map<Row, Map<Answer, JRadioButton>> choices = new LinkedHashMap<>();
    private final Map<Row, JLabel> triangles = new LinkedHashMap<>();
    private final Map<Row, JLabel> deleting = new LinkedHashMap<>();
    private boolean continued;
    private boolean settingAll;

    private SyncDifferencesWindow(Window owner, RenameProposals proposals, List<Row> rows)
    {
        this.dialog = new JDialog(owner, I18n.t("sync.ui.title"), JDialog.ModalityType.APPLICATION_MODAL);
        this.proposals = proposals;
        // A linked heading sets every row it can to its answer, as a row's own pick would
        this.table = new ChoiceTable(
            head(setAllTo("sync.ui.thisComputer", (r, can) -> Answer.KEEP), "sync.ui.tipKeep"),
            head(setAllTo("sync.ui.centralStation", (r, can) -> !can.contains(r) ? null
            : r.asksName() ? Answer.RENAME : Answer.ADDRESS), "sync.ui.tipApply"),
            head(setAllTo("sync.ui.keepBoth", (r, can) -> Answer.BOTH), "sync.ui.tipBoth"),
            head(setAllTo("sync.ui.ignore", (r, can) -> Answer.IGNORE), "sync.ui.tipIgnore"),
            head(setAllTo("sync.ui.askAgain", (r, can) -> Answer.ASK), "sync.ui.tipAsk"));
        for (Row r : rows) this.table.addRow(addRow(r));
        // An mfx locomotive the station has under its name at another address starts on Apply, where Set all would
        List<Row> mfx = new ArrayList<>();
        for (Row r : rows)
            if (r.stationName == null && r.loco.getDecoderType() == Locomotive.decoderType.MFX) mfx.add(r);
        this.settingAll = true;
        for (Row r : proposals.applicable(mfx, new ArrayList<>()))
            this.choices.get(r).get(Answer.ADDRESS).setSelected(true);
        this.settingAll = false;
        refresh();
        JPanel top = headed(rows.size() == 1 ? I18n.t("sync.ui.headingOne") : I18n.f("sync.ui.heading", rows.size()),
            I18n.t("sync.ui.info"));
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
        content.add(this.table, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        this.dialog.setContentPane(content);
        // Closing is Cancel; Enter continues, which changes nothing until a row is answered
        this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.dialog.getRootPane().setDefaultButton(go);
        this.dialog.getRootPane().registerKeyboardAction(e -> this.dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        // Sized with every Deletes note showing, so one appearing on a click has its room
        this.deleting.values().forEach(d -> d.setVisible(true));
        this.dialog.pack();
        refresh();
        this.dialog.setLocationRelativeTo(owner);
    }

    /**
     * Asks over owner about every row with something to answer, ignored ones included, or only's row unless null, and
     * returns once it is closed: the answers by row, empty with nothing to ask, or null when canceled or closed.  On
     * the event thread.
     */
    public static Map<Row, Answer> ask(Window owner, RenameProposals proposals, Locomotive only)
    {
        SyncDifferencesWindow w = over(owner, proposals, only);
        if (w == null) return new LinkedHashMap<>();
        w.dialog.setVisible(true);
        return w.answers();
    }

    /** The table `ask` shows, built and not shown; null with nothing to ask. */
    static SyncDifferencesWindow over(Window owner, RenameProposals proposals, Locomotive only)
    {
        List<Row> rows = new ArrayList<>();
        for (Row r : proposals.getRows())
            if ((only == null || r.loco == only) && (r.asksName() || r.asksAddress() || r.byHand)) rows.add(r);
        return rows.isEmpty() ? null : new SyncDifferencesWindow(owner, proposals, rows);
    }

    /** The answers by row once Continue is pressed; null before that, or once canceled or closed. */
    Map<Row, Answer> answers()
    {
        return this.continued ? picks() : null;
    }

    /** A row's cells, named for the tests: each side's radio beside its block; a by-hand row's text, no radios. */
    private JComponent[] addRow(Row r)
    {
        Locomotive l = r.loco;
        String name = l.getName(), kName = I18n.t("sync.ui.name"), kAddress = I18n.t("sync.ui.address");
        boolean byName = r.stationName != null, byAddress = r.stationAddress != null;
        JComponent[] here = {line(this.table, kName, name + (l.hasLinkedLocomotives() ? " +"
            + l.getLinkedLocomotives().size() : ""), byName, null, null), line(this.table, kAddress,
            l.getDecoderTypeLabel() + " " + l.getAddress(), byAddress, null, null)};
        if (r.byHand)
        {
            JLabel where = text(I18n.f("sync.ui.byHandWhere", r.stationName, there(l) + l.getAddress(), name, there(l)
                + r.stationAddress), GRAYED);
            where.setName(name + ":byHand");
            // A red triangle after the text, saying why
            where.setIcon(RowIcons.warning(16, true));
            where.setHorizontalTextPosition(JLabel.LEADING);
            where.setToolTipText(I18n.t("sync.ui.byHandTip"));
            // Where a radio sits in the other rows, a gap as wide
            int radio = new JRadioButton().getPreferredSize().width;
            return new JComponent[] {ChoiceTable.beside(Box.createHorizontalStrut(radio), here), ChoiceTable.beside(
                Box.createHorizontalStrut(radio), this.table.line(text(I18n.t("sync.ui.byHand"), GRAYED)),
                this.table.line(where))};
        }
        ButtonGroup group = new ButtonGroup();
        Map<Answer, JRadioButton> radios = new EnumMap<>(Answer.class);
        JRadioButton keep = choice(r, radios, group, Answer.KEEP, I18n.f("sync.ui.keepAsIs", name), null);
        JRadioButton rename = null, use = null;
        JLabel deletes = null, triangle = null;
        if (r.asksName())
        {
            rename = choice(r, radios, group, Answer.RENAME, I18n.f("sync.ui.rename", name, r.stationName),
                r.whyNot(true));
            // A grayed rename deletes nothing, so it has no Deletes note
            if (r.whyNot(true) == null)
            {
                deletes = mark(name + ":deletes", null);
                deletes.setForeground(DELETES);
                this.deleting.put(r, deletes);
            }
        }
        if (r.asksAddress())
        {
            use = choice(r, radios, group, Answer.ADDRESS, I18n.f("sync.ui.changeAddress", name, r.stationAddress),
                r.whyNot(false));
            triangle = mark(name + ":collision", RowIcons.warning(16));
            this.triangles.put(r, triangle);
        }
        // Keep Both adds the station's locomotive, so only where its name is free here; elsewhere it is grayed
        choice(r, radios, group, Answer.BOTH, I18n.f("sync.ui.keepBothSaid", name, r.stationName), !r.asksName()
            ? I18n.t("sync.ui.bothOnlyRenamed") : r.nameHolder != null ? I18n.f("sync.ui.bothNameTaken", r.stationName)
            : null);
        choice(r, radios, group, Answer.IGNORE, I18n.f("sync.ui.ignoreSaid", name), null);
        choice(r, radios, group, Answer.ASK, I18n.f("sync.ui.askAgainSaid", name), null);
        radios.get(l.isSyncIgnored() ? Answer.IGNORE : Answer.ASK).setSelected(true);
        // A heading's Set all judges the marks once, after every row it sets
        for (JRadioButton b : radios.values()) b.addItemListener(e -> { if (!this.settingAll) refresh(); });
        this.choices.put(r, radios);
        JComponent station = ChoiceTable.beside(rename != null ? rename : use, line(this.table, kName, byName
            ? r.stationName : name, byName, rename != null ? r.whyNot(true) : null, deletes), line(this.table, kAddress,
            there(l) + (byAddress ? r.stationAddress : l.getAddress()), byAddress, use != null ? r.whyNot(false) : null,
            triangle));
        return new JComponent[] {ChoiceTable.beside(keep, here), station, radios.get(Answer.BOTH),
            radios.get(Answer.IGNORE), radios.get(Answer.ASK)};
    }

    /** The station's decoder type and a space: plain, as a head's "MU" is this computer's. */
    private static String there(Locomotive l)
    {
        return (l.hasLinkedLocomotives() ? l.getDecoderType().name() : l.getDecoderTypeLabel()) + " ";
    }

    /** A row's radio for answer a, saying `said` on hover and to a screen reader; grayed, they say `whyNot`. */
    private JRadioButton choice(Row r, Map<Answer, JRadioButton> radios, ButtonGroup group, Answer a, String said,
        String whyNot)
    {
        JRadioButton b = choice(this.table, group, r.loco.getName() + ":" + a.name(), said, whyNot);
        radios.put(a, b);
        return b;
    }

    /** A table's radio named `name`, saying `said` on hover and to a screen reader; grayed, it says `whyNot`. */
    static JRadioButton choice(ChoiceTable table, ButtonGroup group, String name, String said, String whyNot)
    {
        JRadioButton b = table.choice(group);
        b.setName(name);
        b.getAccessibleContext().setAccessibleName(whyNot != null ? whyNot : said);
        b.setToolTipText(whyNot != null ? whyNot : said);
        b.setEnabled(whyNot == null);
        return b;
    }

    /** A blue heading with its (i) after it, as far in as a table's border. */
    static JPanel headed(String heading, String info)
    {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.add(UiStyle.heading(heading));
        top.add(Box.createHorizontalStrut(6));
        top.add(InfoMark.label(info));
        return top;
    }

    static JLabel text(String text, Color color)
    {
        JLabel t = new JLabel(text);
        t.setFont(UiStyle.TEXT_FONT);
        if (color != null) t.setForeground(color);
        return t;
    }

    private static JLabel mark(String name, javax.swing.Icon icon)
    {
        JLabel m = text(null, null);
        m.setIcon(icon);
        m.setName(name);
        m.setVisible(false);
        return m;
    }

    /** A line of `table`: a gray kind, the value (bold if it differs), "(can't apply)" saying `whyNot`, and the mark. */
    static JComponent line(ChoiceTable table, String kind, String value, boolean differs, String whyNot, JLabel mark)
    {
        JLabel k = UiStyle.blackLabel(kind), v = text(value, null), why = whyNot == null ? null : text(I18n.t(
            "sync.ui.cantApply"), GRAYED);
        k.setForeground(Color.GRAY);
        if (differs) v.setFont(UiStyle.TEXT_FONT.deriveFont(Font.BOLD));
        if (why != null) why.setToolTipText(whyNot);
        return table.line(k, v, why, mark);
    }

    /** A heading with its (i) after it, saying what a click does. */
    static JComponent head(JButton heading, String tip)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.add(heading);
        p.add(Box.createHorizontalStrut(4));
        p.add(InfoMark.label(I18n.t(tip)));
        return p;
    }

    /** A linked heading: each row not ignored gets what `to` gives it, from the rows Apply may take, if enabled. */
    private JButton setAllTo(String key, BiFunction<Row, Set<Row>, Answer> to)
    {
        return ChoiceTable.link(I18n.t(key), () ->
        {
            Set<Row> can = this.proposals.applicable(this.choices.keySet(), chosen(Answer.ADDRESS));
            this.settingAll = true;
            for (Map.Entry<Row, Map<Answer, JRadioButton>> each : this.choices.entrySet())
            {
                Row r = each.getKey();
                JRadioButton c = r.loco.isSyncIgnored() ? null : each.getValue().get(to.apply(r, can));
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
            // Said only while this row's Apply is picked
            d.getValue().setVisible(gone != null && renamed.contains(d.getKey()));
            if (gone != null) d.getValue().setText(I18n.f("sync.ui.deletes", gone.getName()
                + (gone.hasLinkedLocomotives() ? " +" + gone.getLinkedLocomotives().size() : ""),
                there(gone) + gone.getAddress()));
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
