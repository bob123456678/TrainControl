package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.ConditionRows;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.Route;
import org.traincontrol.util.I18n;

/**
 * Editing a route by picking from lists, instead of typing lines that have to be right.
 *
 * The existing editor is two text areas: one line per command, and a condition expression written out
 * by hand. It works, and it is the only place left in TrainControl where the interface asks somebody to
 * get syntax right - a mistyped address is a switch that never throws, and a mistyped condition is a
 * route that silently never fires.
 *
 * This shows the same route as two tables of dropdowns. Every command is the same three questions -
 * what kind, which one, what to do - and every condition is a term with an AND or OR joining it to what
 * follows. The conversions live in CommandRow and ConditionRows, are tested on their own, and are what
 * makes opening a route here safe: a route loaded and saved unchanged is unchanged.
 *
 * WHAT IT REFUSES TO TOUCH, which is the important part. A command of a kind with no controls yet, and
 * a condition with a real bracket in it, are kept exactly as found and shown read-only. The alternative
 * - rendering them as something that nearly means the same - is how an editor quietly rewrites
 * somebody's railway the first time they press Save.
 *
 * Hand-written rather than built in the GUI designer, so there is no .form to keep in step and nothing
 * generated to avoid editing.
 */
public class RouteEditorFrame extends JFrame
{
    private final TrainControlUI parent;

    /** The route being edited, or empty when this is a new one. */
    private final String originalName;

    private final JTextField nameField = new JTextField(24);
    private final JTextField s88Field = new JTextField(6);
    private final JComboBox<String> triggerBox = new JComboBox<>();
    private final JCheckBox enabledBox = new JCheckBox(I18n.t("route.ui.frameEnabled"));

    private final CommandTable commands = new CommandTable();
    private final ConditionTable conditions = new ConditionTable();

    /** The condition expression as loaded, kept when the rows cannot express it. */
    private NodeExpression conditionsAsFound;

    private boolean conditionsEditable = true;

    /**
     * While ticked, an accessory thrown on the layout adds itself to the command list.
     *
     * The old editor's most useful feature by some way: rather than looking up addresses, the user
     * throws the switches by hand in the order they want them and watches the route write itself.  It
     * would have been easy to leave out of a rebuild and hard to notice missing until somebody tried.
     */
    private final JCheckBox captureBox = new JCheckBox(I18n.t("route.ui.frameCapture"));

    /**
     * Where a captured accessory goes: the command list, or the conditions.
     *
     * Asked rather than guessed.  Capturing into conditions is genuinely useful - "run this route when
     * these points are already set the way I have just set them" is otherwise a lot of addresses typed
     * by hand - but it is not what capture has always meant, and a capture that quietly wrote to the
     * wrong table would be worse than one that did nothing.
     *
     * s88 is deliberately absent.  A feedback condition is the commonest kind there is, and it is also
     * the one that would fill the table with noise: a layout with trains on it reports sensors
     * constantly, and none of those reports is the user saying anything.
     */
    private final JComboBox<String> captureTarget = new JComboBox<>();

    /** Says which way the joins nest, because that is the one thing a row list cannot show. */
    private final JLabel readsAs = new JLabel(" ");

    /**
     * @param parent the main window, which owns the model and the route list
     * @param routeName the route to edit, or null for a new one
     */
    public RouteEditorFrame(TrainControlUI parent, String routeName)
    {
        this.parent = parent;
        this.originalName = routeName == null ? "" : routeName;

        setTitle(routeName == null ? I18n.t("route.ui.frameNewRoute")
            : I18n.f("route.ui.frameEditRoute", routeName));

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        for (Route.s88Triggers trigger : Route.s88Triggers.values())
        {
            triggerBox.addItem(trigger.toString());
        }

        setContentPane(build());

        load(routeName);

        pack();
        setLocationRelativeTo(parent);
    }

    private JPanel build()
    {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(header(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 8));

        JPanel commandSection = section(I18n.t("route.ui.frameCommands"), commands,
            () -> commands.addRow(), () -> commands.removeSelected(),
            () -> commands.move(-1), () -> commands.move(1));

        captureBox.setToolTipText(I18n.t("route.ui.tooltipCapture"));

        captureTarget.addItem(I18n.t("route.ui.frameCaptureIntoCommands"));
        captureTarget.addItem(I18n.t("route.ui.frameCaptureIntoConditions"));
        captureTarget.setToolTipText(I18n.t("route.ui.tooltipCaptureTarget"));

        ((JPanel) commandSection.getComponent(1)).add(captureBox);
        ((JPanel) commandSection.getComponent(1)).add(captureTarget);

        middle.add(commandSection);

        // Add and Remove refuse when the expression is one rows cannot express.  They used to stay
        // enabled: a user could open a bracketed route, add a condition, fill it in and save, and the
        // whole lot was dropped on the way out - onSave keeps the original expression - without a word.
        JPanel conditionSection = section(I18n.t("route.ui.frameConditions"), conditions,
            () -> { if (conditionsEditable) conditions.addRow(); },
            () -> { if (conditionsEditable) conditions.removeSelected(); }, null, null);

        readsAs.setFont(readsAs.getFont().deriveFont(java.awt.Font.PLAIN, 11f));

        ((JPanel) conditionSection.getComponent(1)).add(readsAs);

        middle.add(conditionSection);

        content.add(middle, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton save = new JButton(I18n.t("route.ui.frameSave"));
        save.addActionListener(e -> onSave());

        JButton cancel = new JButton(I18n.t("route.ui.frameCancel"));
        cancel.addActionListener(e -> dispose());

        buttons.add(cancel);
        buttons.add(save);

        content.add(buttons, BorderLayout.SOUTH);

        return content;
    }

    private JPanel header()
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        row.add(new JLabel(I18n.t("route.ui.frameName")));
        row.add(nameField);
        row.add(new JLabel(I18n.t("route.ui.frameS88")));
        row.add(s88Field);
        row.add(new JLabel(I18n.t("route.ui.frameTrigger")));
        row.add(triggerBox);
        row.add(enabledBox);

        return row;
    }

    /**
     * A titled table with the buttons that act on it.
     */
    private JPanel section(String title, JTable table, Runnable add, Runnable remove,
        Runnable up, Runnable down)
    {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        panel.setBorder(BorderFactory.createTitledBorder(title));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, 180));

        panel.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        buttons.add(button(I18n.t("route.ui.frameAdd"), add));
        buttons.add(button(I18n.t("route.ui.frameRemove"), remove));

        if (up != null) buttons.add(button("▲", up));
        if (down != null) buttons.add(button("▼", down));

        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JButton button(String text, Runnable action)
    {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    /**
     * Fills the frame from a route, or leaves it empty for a new one.
     */
    private void load(String routeName)
    {
        if (routeName == null)
        {
            enabledBox.setSelected(false);
            s88Field.setText("0");
            return;
        }

        Route route = parent.getModel().getRoute(routeName);

        if (route == null) return;

        nameField.setText(route.getName());
        s88Field.setText(String.valueOf(route.getS88()));
        triggerBox.setSelectedItem(route.getTriggerType().toString());
        enabledBox.setSelected(route.isEnabled());

        List<RouteCommand> stored = route.getRoute();

        for (RouteCommand command : stored)
        {
            // A kind with no controls becomes a read-only row rather than a remembered index.  It was
            // an index, held against the ORIGINAL position, and the editable rows moved under it: a
            // route of [Switch 1, run "Yard", Switch 2] whose first row is deleted put "Yard" AFTER
            // Switch 2, silently, having never shown it at all.  One list, one order, all of it on
            // screen.
            commands.rows.add(Entry.of(command));
        }

        conditionsAsFound = route.getConditions();

        List<ConditionRows.Row> rows = ConditionRows.of(conditionsAsFound);

        if (rows == null)
        {
            // A bracket: rows cannot say it, so the expression is written out beneath the table and
            // kept exactly as found when the route is saved
            conditionsEditable = false;
            conditions.setEnabled(false);

            // And capture cannot be pointed at it either.  Left enabled, the user could tick capture,
            // choose Conditions, throw switches, and have every one of them silently dropped - a
            // control that offers a destination nothing can reach.
            captureTarget.setSelectedIndex(0);
            captureTarget.setEnabled(false);
        }
        else
        {
            conditions.rows.addAll(rows);
        }

        commands.fireTableDataChanged();
        conditions.fireTableDataChanged();
        updateReadsAs();
    }

    /**
     * How many commands the list holds, so a test can see that a capture arrived.
     */
    public int commandCount()
    {
        return commands.rows.size();
    }

    /**
     * The commands this route would be saved as, in order.
     *
     * The order IS the route, so this is the one thing about the editor most worth being able to look
     * at directly: kept commands and edited rows come out interleaved exactly as the table shows them.
     *
     * @throws IllegalArgumentException when a row cannot be made into a command
     */
    public List<RouteCommand> commandsAsSaved()
    {
        List<RouteCommand> built = new LinkedList<>();

        for (Entry entry : commands.rows) built.add(entry.toCommand());

        return built;
    }

    /**
     * Removes one command, which is what the minus button does to the selected row.
     */
    public void removeCommandAt(int index)
    {
        commands.removeAt(index);
    }

    /**
     * Whether a thrown accessory should write itself into the command list.
     */
    public boolean isCapturing()
    {
        return captureBox.isSelected();
    }

    /**
     * Adds a command that was captured from the layout.
     *
     * Takes the same string the old editor is handed - the accessory's own setting line - and parses it
     * with the same parser, so a capture means exactly what it always meant.  A line this frame has no
     * controls for is kept rather than dropped, on the rule the rest of the editor follows.
     *
     * @param command the captured line
     */
    public void appendCommand(String command)
    {
        if (command == null || command.trim().isEmpty()) return;

        try
        {
            RouteCommand parsed = RouteCommand.fromLine(command, false);

            if (parsed == null) return;

            if (capturingIntoConditions())
            {
                // Only into a condition list rows can express.  A bracketed expression is kept exactly
                // as found and its table is read-only, so appending to it would be building something
                // that Save is going to throw away.
                if (!conditionsEditable) return;

                // The row before this one has to join to it, and AND is what a list of conditions
                // means when nobody has said otherwise
                if (!conditions.rows.isEmpty())
                {
                    int last = conditions.rows.size() - 1;

                    conditions.rows.set(last, new ConditionRows.Row(ConditionRows.Joiner.AND,
                        conditions.rows.get(last).getCommand()));
                }

                conditions.rows.add(new ConditionRows.Row(null, parsed));
                conditions.fireTableDataChanged();

                updateReadsAs();

                return;
            }

            commands.rows.add(Entry.of(parsed));
            commands.fireTableDataChanged();
        }
        catch (Exception e)
        {
            // A line that will not parse is not worth interrupting a capture for
        }
    }

    /**
     * Whether captures are going into the conditions rather than the commands.
     */
    public boolean capturingIntoConditions()
    {
        return captureTarget.getSelectedIndex() == 1;
    }

    /**
     * Sends captures to the conditions or to the commands, for a test that cannot click.
     */
    public void setCapturingIntoConditions(boolean intoConditions)
    {
        captureTarget.setSelectedIndex(intoConditions ? 1 : 0);
    }

    /**
     * How many conditions the list holds, so a test can see that a capture arrived.
     */
    public int conditionCount()
    {
        return conditions.rows.size();
    }

    /**
     * Writes out how the joins nest, in words.
     *
     * The one thing a flat list cannot show.  "a AND b OR c" is AND(a, OR(b, c)) here, not
     * OR(AND(a, b), c) - reading it left to right like arithmetic gives a different railway, and
     * nothing on screen would otherwise say which one is meant.
     */
    private void updateReadsAs()
    {
        List<ConditionRows.Row> rows = conditions.rows;

        if (!conditionsEditable)
        {
            // Actually show them.  The message said the conditions were "shown but not edited here"
            // beneath an EMPTY table, which reads as having lost them.  A bracket cannot be a row list,
            // but it can certainly be written out.
            String text = conditionsAsFound == null ? ""
                : NodeExpression.toTextRepresentation(conditionsAsFound,
                    parent == null ? null : parent.getModel());

            readsAs.setText(I18n.f("route.ui.frameConditionsNotShown", text));
            return;
        }

        if (rows.size() < 2)
        {
            readsAs.setText(" ");
            return;
        }

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < rows.size(); i++)
        {
            if (i > 0) out.append(rows.get(i - 1).getJoiner() == ConditionRows.Joiner.OR
                ? " or " : " and ");

            // Brackets from the right, which is how the list nests
            if (i > 0 && i < rows.size() - 1) out.append('(');

            out.append(shortly(rows.get(i).getCommand()));
        }

        for (int i = 0; i < rows.size() - 2; i++) out.append(')');

        readsAs.setText(I18n.f("route.ui.frameReadsAs", out.toString()));
    }

    /**
     * A condition in as few words as possible, for the reading above.
     */
    private static String shortly(RouteCommand command)
    {
        if (command == null) return "?";

        if (command.isFeedback())
        {
            return "s88 " + command.getAddress() + (command.getSetting() ? " on" : " off");
        }

        return String.valueOf(command);
    }

    /**
     * Builds the route back up and hands it to the same code the text editor uses.
     */
    private void onSave()
    {
        String name = nameField.getText().trim();

        if (name.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameNeedsAName"));
            return;
        }

        int s88;

        try
        {
            s88 = Math.abs(Integer.parseInt(s88Field.getText().trim()));
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameS88NotANumber"));
            return;
        }

        List<RouteCommand> built;

        try
        {
            built = commandsAsSaved();
        }
        catch (IllegalArgumentException e)
        {
            JOptionPane.showMessageDialog(this,
                I18n.f("route.ui.frameRowIsWrong", String.valueOf(e.getMessage())));
            return;
        }

        if (built.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameNeedsACommand"));
            return;
        }

        NodeExpression expression = conditionsEditable
            ? ConditionRows.toExpression(conditions.rows) : conditionsAsFound;

        Route.s88Triggers trigger =
            Route.s88Triggers.valueOf(String.valueOf(triggerBox.getSelectedItem()));

        try
        {
            if (originalName.isEmpty())
            {
                if (parent.getModel().getRouteList().contains(name))
                {
                    JOptionPane.showMessageDialog(this,
                        I18n.f("route.ui.errorRouteAlreadyExistsPickDifferentName", name));
                    return;
                }

                if (!parent.getModel().newRoute(name, built, s88, trigger, enabledBox.isSelected(),
                    expression))
                {
                    // Checked, the way the edit path below is.  It was discarded, so a refusal closed
                    // the window with no route made and nothing said.
                    JOptionPane.showMessageDialog(this, I18n.f("route.ui.errorEditRouteFailed", name));
                    return;
                }
            }
            else if (!parent.getModel().editRoute(originalName, name, built, s88, trigger,
                enabledBox.isSelected(), expression))
            {
                JOptionPane.showMessageDialog(this, I18n.f("route.ui.errorEditRouteFailed", name));
                return;
            }

            parent.refreshRouteList();
            parent.repaintLayout();

            // Every other route mutation in the interface syncs afterwards, on the same reasoning -
            // the Central Station holds routes too, and a route changed only here is a route the next
            // sync can undo.  This one did not, which was an undocumented divergence rather than a
            // decision.
            parent.syncWithCS2();

            dispose();
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }
    }

    /**
     * A decoder type from what the user chose, defaulting rather than throwing.
     */
    private static Accessory.accessoryDecoderType protocolOf(String text)
    {
        for (Accessory.accessoryDecoderType type : Accessory.accessoryDecoderType.values())
        {
            if (type.toString().equalsIgnoreCase(text)) return type;
        }

        return Accessory.DEFAULT_IMPLICIT_PROTOCOL;
    }

    /**
     * A delay from what the user typed.  Rubbish and blanks leave it as it was rather than silently
     * becoming zero, which would be a timing change nobody asked for.
     */
    private static int delayOf(String text, int wasBefore)
    {
        if (text == null || text.trim().isEmpty()) return 0;

        try
        {
            int parsed = Integer.parseInt(text.trim());

            return parsed < 0 ? wasBefore : parsed;
        }
        catch (NumberFormatException e)
        {
            return wasBefore;
        }
    }

    // ================================================================ the tables

    /**
     * One line of the command list: either a row the user can edit, or a command kept as found.
     *
     * Both live in the same list because the ORDER is the route, and an order kept in two places is an
     * order that comes apart.  It did: the kept commands used to be held in a map keyed by the position
     * they had when the route was loaded, while the editable rows moved around underneath them, so
     * deleting one editable row moved a sub-route call to the wrong side of a turnout.
     *
     * A kept command is shown, greyed and uneditable, rather than hidden.  A user who cannot see it
     * cannot understand why the route does something the table does not mention.
     */
    private static final class Entry
    {
        private final CommandRow row;
        private final RouteCommand kept;

        private Entry(CommandRow row, RouteCommand kept)
        {
            this.row = row;
            this.kept = kept;
        }

        /**
         * An entry for a stored command: editable when the editor has controls for its kind.
         */
        static Entry of(RouteCommand command)
        {
            CommandRow row = CommandRow.of(command);

            return row == null ? new Entry(null, command) : new Entry(row, null);
        }

        static Entry of(CommandRow row)
        {
            return new Entry(row, null);
        }

        boolean isEditable()
        {
            return row != null;
        }

        CommandRow getRow()
        {
            return row;
        }

        RouteCommand toCommand()
        {
            return row != null ? row.toCommand() : kept;
        }

        /**
         * How a kept command reads in the table: its own stored line, which is what the old text
         * editor showed and what the file holds.
         */
        String describe()
        {
            return kept == null ? "" : kept.toLine(null);
        }
    }

    /**
     * The columns every command shares: what kind, which one, what to do, and - for the kinds that
     * have them - which decoder speaks to it and how long to wait afterwards.
     *
     * Protocol and delay are columns rather than assumptions because both were assumed once and both
     * were lost on Save: every accessory was written as MM2, which is a DIFFERENT address space from
     * DCC, and every delay was dropped, which is what holds a slow point motor apart from the command
     * behind it.  A cell the kind cannot use is greyed rather than hidden, so the table stays one
     * shape.
     */
    private final class CommandTable extends JTable
    {
        private final List<Entry> rows = new ArrayList<>();

        private final AbstractTableModel model = new AbstractTableModel()
        {
            @Override
            public int getRowCount()
            {
                return rows.size();
            }

            @Override
            public int getColumnCount()
            {
                return 5;
            }

            @Override
            public String getColumnName(int column)
            {
                switch (column)
                {
                    case 0: return I18n.t("route.ui.frameColKind");
                    case 1: return I18n.t("route.ui.frameColTarget");
                    case 2: return I18n.t("route.ui.frameColSetting");
                    case 3: return I18n.t("route.ui.frameColProtocol");
                    default: return I18n.t("route.ui.frameColDelay");
                }
            }

            @Override
            public Object getValueAt(int row, int column)
            {
                Entry entry = rows.get(row);

                // A kept command has no columns to fill, so it reads as its own stored line in the
                // first one.  Better an unfamiliar line than a blank row doing something unexplained.
                if (!entry.isEditable())
                {
                    return column == 0 ? entry.describe() : "";
                }

                CommandRow at = entry.getRow();

                switch (column)
                {
                    case 0: return at.getKind().toString();
                    case 1: return at.getTarget();
                    case 2: return at.getSetting();

                    case 3:
                        if (!CommandRow.hasProtocol(at.getKind())) return "";
                        return (at.getProtocol() == null
                            ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : at.getProtocol()).toString();

                    default:
                        if (!CommandRow.hasDelay(at.getKind())) return "";
                        return at.getDelay() == 0 ? "" : String.valueOf(at.getDelay());
                }
            }

            @Override
            public boolean isCellEditable(int row, int column)
            {
                Entry entry = rows.get(row);

                if (!entry.isEditable()) return false;

                CommandRow at = entry.getRow();

                if (column == 1) return CommandRow.hasTarget(at.getKind());
                if (column == 2) return CommandRow.hasSetting(at.getKind());
                if (column == 3) return CommandRow.hasProtocol(at.getKind());
                if (column == 4) return CommandRow.hasDelay(at.getKind());

                return true;
            }

            @Override
            public void setValueAt(Object value, int row, int column)
            {
                Entry entry = rows.get(row);

                if (!entry.isEditable()) return;

                CommandRow at = entry.getRow();

                String text = value == null ? "" : value.toString().trim();

                CommandRow.Kind kind = column == 0 ? CommandRow.Kind.valueOf(text) : at.getKind();
                String target = column == 1 ? text : at.getTarget();
                String setting = column == 2 ? text : at.getSetting();

                // Every rebuild carries protocol and delay forward.  Editing the SETTING of a DCC
                // accessory used to move it to MM2, because the row was rebuilt from three columns
                // and the fourth thing it knew was simply not passed on.
                Accessory.accessoryDecoderType protocol = at.getProtocol();
                int delay = at.getDelay();

                if (column == 3) protocol = protocolOf(text);
                if (column == 4) delay = delayOf(text, at.getDelay());

                // A kind that cannot hold one does not keep it, so changing an accessory into a stop
                // and back does not smuggle a stale decoder type through
                if (!CommandRow.hasProtocol(kind)) protocol = null;
                if (!CommandRow.hasDelay(kind)) delay = 0;

                rows.set(row, Entry.of(new CommandRow(kind, target, setting, protocol, delay)));

                fireTableRowsUpdated(row, row);
            }
        };

        CommandTable()
        {
            setModel(model);
            setRowHeight(24);

            JComboBox<String> kinds = new JComboBox<>();

            for (CommandRow.Kind kind : CommandRow.Kind.values()) kinds.addItem(kind.toString());

            getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(kinds));

            JComboBox<String> protocols = new JComboBox<>();

            for (Accessory.accessoryDecoderType type : Accessory.accessoryDecoderType.values())
            {
                protocols.addItem(type.toString());
            }

            getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(protocols));

            TableColumn kindColumn = getColumnModel().getColumn(0);
            kindColumn.setPreferredWidth(170);

            getColumnModel().getColumn(3).setPreferredWidth(70);
            getColumnModel().getColumn(4).setPreferredWidth(70);

            // Kept commands are drawn greyed, so "you cannot edit this one" is something the table
            // says rather than something the user discovers by clicking
            setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
            {
                @Override
                public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column)
                {
                    java.awt.Component c = super.getTableCellRendererComponent(table, value, selected,
                        focused, row, column);

                    boolean editable = row < rows.size() && rows.get(row).isEditable();

                    c.setEnabled(editable);

                    if (!selected)
                    {
                        c.setForeground(editable
                            ? table.getForeground() : java.awt.Color.GRAY);
                    }

                    return c;
                }
            });
        }

        void addRow()
        {
            rows.add(Entry.of(new CommandRow(CommandRow.Kind.ACCESSORY, "", "straight")));
            model.fireTableDataChanged();
        }

        void removeSelected()
        {
            removeAt(getSelectedRow());
        }

        void removeAt(int at)
        {
            if (at < 0 || at >= rows.size()) return;

            rows.remove(at);
            model.fireTableDataChanged();
        }

        void move(int by)
        {
            int at = getSelectedRow();
            int to = at + by;

            if (at < 0 || to < 0 || to >= rows.size()) return;

            rows.add(to, rows.remove(at));
            model.fireTableDataChanged();
            setRowSelectionInterval(to, to);
        }

        void fireTableDataChanged()
        {
            model.fireTableDataChanged();
        }
    }

    /**
     * A term, and the operator joining it to what follows.
     */
    private final class ConditionTable extends JTable
    {
        private final List<ConditionRows.Row> rows = new ArrayList<>();

        private final AbstractTableModel model = new AbstractTableModel()
        {
            @Override
            public int getRowCount()
            {
                return rows.size();
            }

            @Override
            public int getColumnCount()
            {
                return 5;
            }

            @Override
            public String getColumnName(int column)
            {
                switch (column)
                {
                    case 0: return I18n.t("route.ui.frameColJoin");
                    case 1: return I18n.t("route.ui.frameColKind");
                    case 2: return I18n.t("route.ui.frameColTarget");
                    case 3: return I18n.t("route.ui.frameColSetting");
                    default: return I18n.t("route.ui.frameColProtocol");
                }
            }

            @Override
            public Object getValueAt(int row, int column)
            {
                ConditionRows.Row at = rows.get(row);

                if (column == 0)
                {
                    // The last row joins to nothing, and says so rather than showing a box that does
                    // nothing
                    return row == rows.size() - 1 ? ""
                        : at.getJoiner() == null ? "AND" : at.getJoiner().toString();
                }

                CommandRow term = CommandRow.of(at.getCommand());

                // A term with no controls is shown whole and left alone, as the commands are
                if (term == null) return column == 1 ? String.valueOf(at.getCommand()) : "";

                if (column == 1) return term.getKind().toString();
                if (column == 2) return term.getTarget();
                if (column == 3) return term.getSetting();

                // The decoder type, for the kinds that have one.  A condition is evaluated by address
                // AND protocol, and MM2 and DCC are separate address spaces - so without this column a
                // hand-added accessory condition always meant the MM2 one, which on a DCC layout is a
                // different physical accessory and a route that never fires.  Conditions LOADED from a
                // route were always safe: CommandRow.of carries their protocol.  It was only the ones
                // built here that could not say it.
                if (!CommandRow.hasProtocol(term.getKind())) return "";

                return (term.getProtocol() == null
                    ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : term.getProtocol()).toString();
            }

            @Override
            public boolean isCellEditable(int row, int column)
            {
                if (!conditionsEditable) return false;

                if (column == 0) return row < rows.size() - 1;

                CommandRow term = CommandRow.of(rows.get(row).getCommand());

                if (term == null) return false;

                if (column == 2) return CommandRow.hasTarget(term.getKind());
                if (column == 3) return CommandRow.hasSetting(term.getKind());
                if (column == 4) return CommandRow.hasProtocol(term.getKind());

                return true;
            }

            @Override
            public void setValueAt(Object value, int row, int column)
            {
                ConditionRows.Row at = rows.get(row);

                String text = value == null ? "" : value.toString();

                if (column == 0)
                {
                    rows.set(row, new ConditionRows.Row(
                        ConditionRows.Joiner.valueOf(text), at.getCommand()));

                    updateReadsAs();
                    fireTableRowsUpdated(row, row);
                    return;
                }

                CommandRow term = CommandRow.of(at.getCommand());

                if (term == null) return;

                CommandRow edited;

                if (column == 1)
                {
                    edited = new CommandRow(CommandRow.Kind.valueOf(text), term.getTarget(),
                        term.getSetting(), term.getProtocol(), term.getDelay());
                }
                else if (column == 2)
                {
                    edited = new CommandRow(term.getKind(), text, term.getSetting(),
                        term.getProtocol(), term.getDelay());
                }
                else if (column == 4)
                {
                    edited = new CommandRow(term.getKind(), term.getTarget(), term.getSetting(),
                        protocolOf(text), term.getDelay());
                }
                else
                {
                    edited = new CommandRow(term.getKind(), term.getTarget(), text,
                        term.getProtocol(), term.getDelay());
                }

                try
                {
                    rows.set(row, new ConditionRows.Row(at.getJoiner(),
                        edited.toCommand()));
                }
                catch (IllegalArgumentException e)
                {
                    // Half-typed is not wrong yet - the cell keeps what it had until it makes sense
                    return;
                }

                updateReadsAs();
                fireTableRowsUpdated(row, row);
            }
        };

        ConditionTable()
        {
            setModel(model);
            setRowHeight(24);

            JComboBox<String> joiners = new JComboBox<>();
            joiners.addItem("AND");
            joiners.addItem("OR");

            getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(joiners));
            getColumnModel().getColumn(0).setPreferredWidth(60);

            JComboBox<String> kinds = new JComboBox<>();

            // Only the kinds a condition can actually be.  The list offered all seven, but Route
            // evaluates accessory and feedback terms and nothing else - so picking a speed or a
            // function as a condition gave a term that is permanently false, saved without complaint,
            // and a route that silently stopped firing.
            for (CommandRow.Kind kind : CommandRow.Kind.values())
            {
                if (CommandRow.canBeACondition(kind)) kinds.addItem(kind.toString());
            }

            getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(kinds));
            getColumnModel().getColumn(1).setPreferredWidth(150);

            // The decoder type, chosen rather than typed - the same control the command table uses
            JComboBox<String> conditionProtocols = new JComboBox<>();

            for (Accessory.accessoryDecoderType type : Accessory.accessoryDecoderType.values())
            {
                conditionProtocols.addItem(type.toString());
            }

            getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(conditionProtocols));
            getColumnModel().getColumn(4).setPreferredWidth(70);
        }

        void addRow()
        {
            // A condition is a feedback term by default, which is what nearly every one is
            rows.add(new ConditionRows.Row(ConditionRows.Joiner.AND,
                RouteCommand.RouteCommandFeedback(1, true)));

            model.fireTableDataChanged();
            updateReadsAs();
        }

        void removeSelected()
        {
            int at = getSelectedRow();

            if (at < 0) return;

            rows.remove(at);
            model.fireTableDataChanged();
            updateReadsAs();
        }

        void fireTableDataChanged()
        {
            model.fireTableDataChanged();
        }
    }
}
