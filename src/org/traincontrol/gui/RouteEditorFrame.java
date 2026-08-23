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
import org.traincontrol.base.ConditionOutline;
import org.traincontrol.base.ConditionRows;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.Route;
import org.traincontrol.base.ThreeWaySwitch;
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
    private final JTextField s88Field = digitsOnlyField(6);
    private final JComboBox<String> triggerBox = new JComboBox<>();
    private final JCheckBox enabledBox = new JCheckBox(I18n.t("route.ui.frameEnabled"));

    // Held so they can be raised and lowered with the tick beside them
    private final JLabel sensorLabel = label(I18n.t("route.ui.frameS88"));
    private final JLabel triggerLabelText = label(I18n.t("route.ui.frameTrigger"));

    private final CommandTable commands = new CommandTable();
    private final ConditionTable conditions = new ConditionTable();

    /** The condition expression as loaded, kept when the rows cannot express it. */
    private NodeExpression conditionsAsFound;

    private boolean conditionsEditable = true;

    /**
     * Whether this route came from the Central Station, and so may be read but not changed.
     *
     * The station owns those: TrainControl imports them, marks them locked, and shows them with a
     * star in the route list. Saving one would be writing over something the station will simply send
     * again on the next sync, so the older editor greys its controls and this one has to as well - the
     * new editor was reachable from the same menu and knew nothing about it, which made every one of
     * them look editable.
     */
    private boolean locked = false;

    /**
     * @return whether this route belongs to the Central Station and is shown read-only
     */
    public boolean isLocked()
    {
        return this.locked;
    }


    /**
     * Whether this window will let the route be changed.
     *
     * Asked before a mark is drawn and again before it acts.  Greying the tables was not enough:
     * the marks in the rows are painted values rather than buttons, and the click that works them is
     * a click on a cell picked up by a listener of this window's own - which a disabled table does
     * nothing about.  So a route belonging to the Central Station opened with its name greyed, its
     * Save switched off, and a working plus at the bottom of the command list.
     *
     * @return true when the route is this window's to change
     */
    public boolean isEditable()
    {
        return !locked;
    }

    /**
     * Held so that a route belonging to the Central Station can grey it.
     */
    private JButton saveButton;

    /**
     * Held so a route belonging to the Central Station can keep it: a locked route can still be
     * TESTED, which is the one useful thing to do with a route somebody else owns.
     */
    private JButton testButton;

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
     * How the terms combine: "(A or B) and (C or D)".
     *
     * The second of the two steps. The table above says what the facts are; this says what has to be
     * true of them. They are separate because a bracket cannot be drawn in a list of rows without the
     * list pretending to be a tree - which is what the operator-per-row column was, and why it could
     * only ever express one chain read left to right.
     */
    /**
     * The formula itself. Shown as bubbles, never as text somebody can type into.





    /**
     * @param parent the main window, which owns the model and the route list
     * @param routeName the route to edit, or null for a new one
     */
    public RouteEditorFrame(TrainControlUI parent, String routeName)
    {
        this(parent, routeName,
            parent == null || routeName == null ? null : parent.getModel().getRoute(routeName));
    }

    /**
     * The same window, handed the route rather than sent to look it up.
     *
     * The name alone meant this window could only ever be opened against the running control
     * station, which is also the only way its read-only behaviour could be exercised - and that
     * behaviour is the half nobody notices is broken, because a route that cannot be changed looks
     * exactly like one nobody has tried to change.
     *
     * @param parent the main window, or null
     * @param routeName what to call it in the title
     * @param route the route to show, or null for a new one
     */
    public RouteEditorFrame(TrainControlUI parent, String routeName, Route route)
    {
        this.parent = parent;
        this.originalName = routeName == null ? "" : routeName;

        setTitle(routeName == null ? I18n.t("route.ui.frameNewRoute")
            : I18n.f("route.ui.frameEditRoute", routeName));

        // DO_NOTHING, because the close has a question to ask first - see closeIfThrowingNothingAway
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e)
            {
                closeIfThrowingNothingAway();
            }
        });

        setIconImage(java.awt.Toolkit.getDefaultToolkit().getImage(
            TrainControlUI.class.getResource("resources/locicon.png")));

        // In words rather than in constants.  CLEAR_THEN_OCCUPIED is precise and says nothing to
        // somebody who has not read the code: what it means on a railway is that a train arrived.
        for (Route.s88Triggers trigger : Route.s88Triggers.values())
        {
            triggerBox.addItem(triggerLabel(trigger));
        }

        setContentPane(build());

        load(route);

        // After load(), which is what discovers whether the route is the station's
        if (locked) becomeReadOnly();

        // What the window says before anybody has touched it.  Everything closeIfThrowingNothingAway
        // asks about is the difference between this and the same question asked later.
        loadedSignature = stateSignature();

        pack();
        setLocationRelativeTo(parent);
    }

    private JPanel build()
    {
        JPanel content = new JPanel(new BorderLayout(8, 8));

        // White, all the way out to the edges.  The panels inside are white with a line round them,
        // and a grey window behind them made each one look like a card floating on something rather
        // than like part of one window.
        content.setBackground(java.awt.Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(header(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 8));

        middle.setBackground(java.awt.Color.WHITE);

        JPanel commandSection = section(I18n.t("route.ui.frameCommands"), commands);

        captureBox.setToolTipText(I18n.t("route.ui.tooltipCapture"));

        captureTarget.addItem(I18n.t("route.ui.frameCaptureIntoCommands"));
        captureTarget.addItem(I18n.t("route.ui.frameCaptureIntoConditions"));
        captureTarget.setToolTipText(I18n.t("route.ui.tooltipCaptureTarget"));

        buttonsOf(commandSection).add(captureBox);
        buttonsOf(commandSection).add(captureTarget);

        middle.add(commandSection);

        JPanel conditionSection = section(I18n.t("route.ui.frameConditions"), conditions);

        readsAs.setFont(new java.awt.Font("Segoe UI", 0, 14));
        readsAs.setForeground(new java.awt.Color(90, 90, 90));

        // The reading goes under the table, saying in words what the outline says in shape.  It is
        // the one place the run-of-the-same-word rule is spelled out, so it earns its line.
        readsAs.setVerticalAlignment(JLabel.TOP);

        // The old editor's Test button, which the outline editor was missing.
        //
        // It answers the one question a condition list cannot answer by being looked at: whether the
        // route would fire RIGHT NOW.  The conditions are about the state of the railway, and the
        // railway is in a state while the editor is open - so the sensors can be read and the
        // expression evaluated against them, which is a great deal quicker than shunting a train
        // over a sensor to find out.
        testButton = button(I18n.t("route.ui.testCondition"), this::testAgainstTheRailway);

        testButton.setToolTipText(I18n.t("route.ui.tooltipTestConditions"));

        buttonsOf(conditionSection).add(testButton);

        // Where the route IS, drawn on the railway rather than listed in a window.
        //
        // A route is a list of addresses, and an address is not a place - so reading one and knowing
        // where on the layout it happens means looking each number up on the diagram by hand.  Two
        // colours because a route has two kinds of square and they answer different questions: yellow
        // for what it COMMANDS, orange for what it CHECKS before commanding anything.
        highlightButton = button(I18n.t("route.ui.highlightOnDiagram"), this::highlightOnDiagram);

        highlightButton.setToolTipText(
            AutonomyEditorPanel.wrapped(I18n.t("route.ui.tooltipHighlightOnDiagram")));

        buttonsOf(conditionSection).add(highlightButton);

        // Help beside Test rather than down beside Cancel.
        //
        // Both are about the conditions, and most of what the help has to say is about them - how the
        // indenting means what it means, and which word joins what.  Beside Cancel it sat in the row
        // a user reads on the way OUT of the window, which is the one moment they have stopped
        // needing it.
        buttonsOf(conditionSection).add(button(I18n.t("ui.help"), this::showHelp));

        // The reading and the buttons, stacked, in the one slot BorderLayout has at the bottom.
        //
        // The reading used to be added straight to SOUTH, where section() had already put the button
        // row - and a second component at SOUTH does not sit under the first, it REPLACES it.  The
        // row was still a child of the panel with nothing laying it out, so it had no size and never
        // appeared.  Nobody noticed while the conditions section had no buttons of its own; adding
        // Test to it is what made an invisible row visible as a missing feature.
        JPanel below = new JPanel(new BorderLayout(4, 4));

        below.setBackground(java.awt.Color.WHITE);

        below.add(readsAs, BorderLayout.NORTH);
        below.add(buttonsOf(conditionSection), BorderLayout.SOUTH);

        conditionSection.add(below, BorderLayout.SOUTH);

        middle.add(conditionSection);

        content.add(middle, BorderLayout.CENTER);

        // Save in the bottom RIGHT corner, Cancel just left of it, and a line above the pair (OB-018).
        //
        // They were at opposite ends of the window - Save bottom left, mirroring the old route editor,
        // Cancel bottom right - which kept them clear of the Add and Remove buttons above but put the
        // two answers to one question a window's width apart. Together, with the one you usually want
        // in the corner, is the arrangement every other dialog in this application uses.
        //
        // Cancel FIRST, because a right-aligned FlowLayout still lays its children out left to right:
        // the last one added is the one in the corner.
        JPanel buttons = new JPanel(new BorderLayout());

        buttons.setBackground(java.awt.Color.WHITE);

        buttons.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)),
            BorderFactory.createEmptyBorder(6, 0, 0, 0)));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        right.setBackground(java.awt.Color.WHITE);

        // Through the same question Escape asks, not straight to dispose.
        //
        // Escape has always run closeIfThrowingNothingAway - "closing one by accident threw away
        // everything typed since it opened with no warning at all" - and the BUTTON went straight to
        // dispose. So the obvious way out was the one that did not ask, and the keyboard shortcut was
        // the safe one, which is exactly backwards.
        right.add(button(I18n.t("route.ui.frameCancel"), this::closeIfThrowingNothingAway));

        saveButton = button(I18n.t("route.ui.frameSave"), this::onSave);

        right.add(saveButton);

        buttons.add(right, BorderLayout.EAST);

        content.add(buttons, BorderLayout.SOUTH);

        // Escape is Cancel.  A modal-feeling window that can only be dismissed by finding a button is
        // one the keyboard cannot get out of, and this one is opened from a list somebody is working
        // down.  WHEN_IN_FOCUSED_WINDOW is checked after the focused component's own bindings, so a
        // cell being edited still gets its Escape first and cancels the edit rather than the window.
        getRootPane().registerKeyboardAction(e -> closeIfThrowingNothingAway(),
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        return content;
    }

    /**
     * Closes, asking first if anything would be lost.
     *
     * A route editor is a form with two tables in it, and closing one by accident - Escape, or the
     * window's own X - threw away everything typed since it opened with no warning at all.  Save is
     * the only way out that keeps anything, and nothing said so.
     *
     * Only asks when there IS something to lose, compared against what was loaded rather than against
     * a flag: a flag has to be set by every path that changes anything, and the paths here are two
     * table models, four fields and a capture that writes from another window.  One of them would have
     * been missed, and a prompt that does not appear is worse than none - it teaches the user that
     * closing is safe.
     */
    private void closeIfThrowingNothingAway()
    {
        if (locked || stateSignature().equals(loadedSignature))
        {
            dispose();
            return;
        }

        int answer = JOptionPane.showOptionDialog(this,
            I18n.t("route.ui.confirmDiscardChanges"),
            I18n.t("route.ui.titleDiscardChanges"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]);

        if (answer == 0) dispose();
    }

    /**
     * Everything the window is currently saying, as one string.
     *
     * Tolerant on purpose: it is compared with itself, never parsed, so a half-typed row that cannot
     * be built still contributes its text rather than throwing.  toCommand is only asked of the kept
     * commands, which are RouteCommands already.
     *
     * @return a signature to compare against loadedSignature
     */
    private String stateSignature()
    {
        StringBuilder out = new StringBuilder();

        out.append(nameField.getText()).append('\u0001')
           .append(s88Field.getText()).append('\u0001')
           .append(String.valueOf(triggerBox.getSelectedItem())).append('\u0001')
           .append(enabledBox.isSelected()).append('\u0002');

        for (Entry entry : commands.rows)
        {
            if (entry.isEditable())
            {
                CommandRow row = entry.getRow();

                out.append(row.getKind()).append(',')
                   .append(row.getTarget()).append(',')
                   .append(row.getSetting()).append(',')
                   .append(row.getProtocol()).append(',')
                   .append(row.getDelay());
            }
            else
            {
                out.append(entry.toCommand().toLine(null));
            }

            out.append('\u0002');
        }

        out.append('\u0003');

        for (ConditionOutline.Row row : conditions.rows)
        {
            out.append(row.getDepth()).append(',');

            if (row.isJoiner()) out.append(row.getJoiner());
            else out.append(row.getCommand() == null ? "" : row.getCommand().toLine(null));

            out.append('\u0002');
        }

        return out.toString();
    }

    /** What the window said when it finished loading, for closeIfThrowingNothingAway to compare with. */
    private String loadedSignature = "";

    /**
     * Whether any route in the database was imported from the Central Station.
     *
     * A locked route is one of the station's - they travel one way, are marked on import, and are never
     * written back.  With none of them, a new route's id cannot collide with anything the station knows
     * about, which is the only thing the sync after a save was there to find out.
     *
     * @return whether a sync could tell us anything
     */
    private boolean anyRouteCameFromTheStation()
    {
        for (String name : parent.getModel().getRouteList())
        {
            org.traincontrol.base.Route route = parent.getModel().getRoute(name);

            if (route instanceof org.traincontrol.marklin.MarklinRoute
                && ((org.traincontrol.marklin.MarklinRoute) route).isLocked())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Shows the sensor and the trigger only when the route fires by itself.
     */
    private void showSensorIfAutomatic()
    {
        boolean automatic = enabledBox.isSelected();

        // Greyed rather than hidden.  Hiding them made the window rearrange itself as the tick was
        // pressed, and worse, it took the trigger dropdown off the screen entirely for every route
        // that does not fire by itself - so a control somebody was looking for simply was not there,
        // with nothing to say it existed.  Greyed says "this belongs to the tick" and stays put.
        sensorLabel.setEnabled(automatic);
        s88Field.setEnabled(automatic);
        triggerLabelText.setEnabled(automatic);
        triggerBox.setEnabled(automatic);

        sensorLabel.setForeground(automatic ? HEADING_BLUE : java.awt.Color.GRAY);
        triggerLabelText.setForeground(automatic ? HEADING_BLUE : java.awt.Color.GRAY);
    }

    /**
     * What a trigger means, in words.
     *
     * @param trigger the stored value
     * @return the line to show for it
     */
    private static String triggerLabel(Route.s88Triggers trigger)
    {
        return trigger == Route.s88Triggers.OCCUPIED_THEN_CLEAR
            ? I18n.t("route.ui.triggerLeaves") : I18n.t("route.ui.triggerArrives");
    }

    /**
     * The trigger behind a line of words.
     *
     * Matched against the labels rather than parsed, so the two directions cannot disagree - and it
     * falls back to the arrival trigger, which is the one every route made before this had.
     *
     * @param label what the box is showing
     * @return the value to store
     */
    private static Route.s88Triggers triggerFor(String label)
    {
        return I18n.t("route.ui.triggerLeaves").equals(label)
            ? Route.s88Triggers.OCCUPIED_THEN_CLEAR : Route.s88Triggers.CLEAR_THEN_OCCUPIED;
    }

    private JPanel header()
    {
        // No leading gap.  FlowLayout puts its hgap before the FIRST component as well as between
        // them, so Name sat eight pixels in from the left edge while the framed panels below it
        // started at the edge - near enough to look like a mistake rather than an indent.  The gaps
        // between the fields are struts instead, which puts them where they were and leaves the
        // first label flush with everything else down that side.
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));

        row.setBackground(java.awt.Color.WHITE);

        row.add(label(I18n.t("route.ui.frameName")));
        row.add(javax.swing.Box.createHorizontalStrut(8));
        row.add(nameField);
        row.add(javax.swing.Box.createHorizontalStrut(8));

        // The tick first, and the sensor only when it is ticked.
        //
        // A route fires automatically FROM a sensor - that is the whole of what automatic means here -
        // so a sensor box standing next to an unticked box is asking a question that has no bearing on
        // anything yet.  Ticking is what raises it.
        row.add(enabledBox);
        row.add(javax.swing.Box.createHorizontalStrut(8));

        row.add(sensorLabel);
        row.add(javax.swing.Box.createHorizontalStrut(8));
        row.add(s88Field);
        row.add(javax.swing.Box.createHorizontalStrut(8));
        row.add(triggerLabelText);
        row.add(javax.swing.Box.createHorizontalStrut(8));
        row.add(triggerBox);

        enabledBox.addActionListener(e -> showSensorIfAutomatic());

        showSensorIfAutomatic();

        return row;
    }

    /**
     * A titled table with the buttons that act on it.
     */
    private JPanel section(String title, JTable table)
    {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        // A panel with a line round it, like the rest of this application - see docs/UI-standards.md.
        // The heading sits inside the line at the same indentation as the contents, so the two read as
        // one thing rather than as a label and a box that happen to be near each other.
        panel.setBackground(java.awt.Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204), 1),
            BorderFactory.createEmptyBorder(6, 8, 8, 8)));

        // A heading rather than a box round everything.  Two framed panels stacked inside a third
        // frame is three borders deep before any content, and the rest of this application says
        // "here is a section" with a line of blue text instead.
        // Styled the way GraphEdgeEdit is - see docs/UI-standards.md.  Section headings are Segoe UI
        // Semibold 13 in 0,0,155, and take the same indentation as the panel they name so the heading
        // and its contents line up down the left edge.
        JLabel heading = new JLabel(title);

        heading.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13));
        heading.setForeground(HEADING_BLUE);
        heading.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        panel.add(heading, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, 180));

        // White, with a single line round it, like every other panel here - the standard in
        // docs/UI-standards.md, and what the old route editor's boxes look like
        table.setBackground(java.awt.Color.WHITE);
        scroll.setBorder(BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204), 1));

        panel.add(scroll, BorderLayout.CENTER);

        // No button row.  Add, remove and reorder are marks in the rows themselves now - a button
        // under a table acts on whichever row happens to be selected, which is one more thing to get
        // right before anything happens: select the row, find the button, press it, check it did what
        // you meant.  The row you are pointing at is not ambiguous.
        JPanel below = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        below.setOpaque(false);
        below.setBackground(java.awt.Color.WHITE);

        panel.add(below, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * A text field that will only accept digits.
     *
     * @param columns how wide
     * @return the field
     */
    private static JTextField digitsOnlyField(int columns)
    {
        JTextField field = new JTextField(columns);

        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(
            new javax.swing.text.DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass bypass, int offset, String text,
                javax.swing.text.AttributeSet attributes) throws javax.swing.text.BadLocationException
            {
                if (digits(text)) super.insertString(bypass, offset, text, attributes);
            }

            @Override
            public void replace(FilterBypass bypass, int offset, int length, String text,
                javax.swing.text.AttributeSet attributes) throws javax.swing.text.BadLocationException
            {
                if (digits(text)) super.replace(bypass, offset, length, text, attributes);
            }

            private boolean digits(String text)
            {
                if (text == null) return true;

                for (int at = 0; at < text.length(); at++)
                {
                    if (!Character.isDigit(text.charAt(at))) return false;
                }

                return true;
            }
        });

        return field;
    }

    /**
     * A cell editor that will only accept digits.
     *
     * Refused as it is typed rather than at Save. A cell that takes "twelve" and complains later is a
     * cell that produces a message about something the user did several minutes and several rows ago,
     * and by then they are being asked to remember rather than to look.
     *
     * @return the editor
     */
    private static javax.swing.table.TableCellEditor digitsOnly()
    {
        JTextField box = new JTextField();

        ((javax.swing.text.AbstractDocument) box.getDocument()).setDocumentFilter(
            new javax.swing.text.DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass bypass, int offset, String text,
                javax.swing.text.AttributeSet attributes) throws javax.swing.text.BadLocationException
            {
                if (isDigits(text)) super.insertString(bypass, offset, text, attributes);
            }

            @Override
            public void replace(FilterBypass bypass, int offset, int length, String text,
                javax.swing.text.AttributeSet attributes) throws javax.swing.text.BadLocationException
            {
                if (isDigits(text)) super.replace(bypass, offset, length, text, attributes);
            }

            private boolean isDigits(String text)
            {
                if (text == null) return true;

                for (int at = 0; at < text.length(); at++)
                {
                    if (!Character.isDigit(text.charAt(at))) return false;
                }

                return true;
            }
        });

        return new DefaultCellEditor(box);
    }

    /**
     * A cell editor offering exactly these values.
     *
     * @param values what to offer
     * @return the editor
     */
    private static javax.swing.table.TableCellEditor chooseFrom(String[] values)
    {
        JComboBox<String> box = new JComboBox<>();

        for (String value : values) box.addItem(value);

        return new DefaultCellEditor(box);
    }

    /**
     * A list of names as an array, empty rather than null when there are none.
     */
    private static String[] namesOf(java.util.List<String> names)
    {
        return names == null ? new String[0] : names.toArray(new String[0]);
    }

    /**
     * The number half of a function's setting, which is stored as "3:on".
     *
     * @param setting the stored value
     * @return the function number, or empty
     */
    private static String functionNumberOf(String setting)
    {
        if (setting == null) return "";

        int colon = setting.indexOf(':');

        return colon < 0 ? setting : setting.substring(0, colon);
    }

    /**
     * The on-or-off half.
     *
     * Defaults to off rather than to nothing, because a function command with no state is not a
     * command - and a row that cannot be saved until a cell nobody pointed at is filled in is a row
     * that looks finished and is not.
     *
     * @param setting the stored value
     * @return "on" or "off"
     */
    private static String functionStateOf(String setting)
    {
        if (setting == null) return "off";

        int colon = setting.indexOf(':');

        if (colon < 0 || colon + 1 >= setting.length()) return "off";

        return setting.substring(colon + 1).trim();
    }

    /**
     * The closed set of words a kind's setting may take, or null when it takes a number or a name.
     *
     * @param kind the row's kind
     * @return the words, in the order they should be offered
     */
    private String[] settingWords(CommandRow row)
    {
        switch (row.getKind())
        {
            // The kind says which pair, now that there is a kind for each.  A signal and a switch
            // are the same device at the same address with the same two states; offering a signal
            // "turn" and "straight" is asking somebody to translate their own layout.
            case ACCESSORY: return new String[]{"straight", "turn"};
            case SIGNAL: return new String[]{"green", "red"};

            // Left, straight, right - as they read on the diagram.  The pair of commands each one
            // becomes is ThreeWaySwitch's business; nothing about the order or the pause is decided
            // here, or in two places, which is how the two would drift apart.
            case THREE_WAY: return ThreeWaySwitch.words();

            case FEEDBACK: return new String[]{"off", "on"};
            case LOCOMOTIVE_DIRECTION: return new String[]{"forward", "backward"};

            // Now that the number has a column of its own, what is left of a function's setting is
            // one of two words - which is a choice, and a choice is a dropdown
            case FUNCTION: return new String[]{"off", "on"};
            default: return null;
        }
    }

    /**
     * Which of Switch and Signal the layout says is at an address.
     *
     * @param target the address as typed
     * @param protocol the row's decoder type, or null for the default
     * @param unchanged what to answer when the layout has never heard of that address
     * @return the kind
     */
    private CommandRow.Kind kindAtAddress(String target, Accessory.accessoryDecoderType protocol,
        CommandRow.Kind unchanged)
    {
        if (parent == null || parent.getModel() == null) return unchanged;

        try
        {
            Accessory accessory = parent.getModel().getAccessoryByAddressIfPresent(
                Integer.parseInt(target.trim()),
                protocol == null ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : protocol);

            if (accessory == null) return unchanged;

            return accessory.isSignal() ? CommandRow.Kind.SIGNAL : CommandRow.Kind.ACCESSORY;
        }
        catch (NumberFormatException e)
        {
            // A row with nothing in its address column yet, which is every row the moment it is added
            return unchanged;
        }
    }

    /**
     * A stored command as a row, with an accessory named as whatever is actually at that address.
     *
     * A route file records one accessory command for both - they ARE one command - so of() can only
     * answer ACCESSORY.  Asking the layout here is what makes an existing route open showing "Signal
     * 100 red" rather than "Switch 100 turn", which is the difference between a route somebody can
     * read and one they have to decode.
     */
    private CommandRow asShown(CommandRow row)
    {
        if (row == null || row.getKind() != CommandRow.Kind.ACCESSORY) return row;

        if (kindAtAddress(row.getTarget(), row.getProtocol(), CommandRow.Kind.ACCESSORY)
            != CommandRow.Kind.SIGNAL)
        {
            return row;
        }

        // And the SETTING with it.  The kind decides which pair of words the dropdown offers, so a
        // signal row still carrying "turn" had a setting its own dropdown does not contain: the combo
        // fell back to the first entry, green, and one click into that cell and out again committed
        // it.  A route that put a signal to danger quietly became one that cleared it.
        String said = row.getSetting();

        if ("turn".equalsIgnoreCase(said)) said = "red";
        else if ("straight".equalsIgnoreCase(said)) said = "green";

        return new CommandRow(CommandRow.Kind.SIGNAL, row.getTarget(), said,
            row.getProtocol(), row.getDelay());
    }

    /**
     * Whether the accessory this row names is a signal.
     *
     * @param row the row
     * @return true only when there is an accessory at that address and it is a signal
     */
    private boolean isSignalAt(CommandRow row)
    {
        // The layout is what knows.  Without one - the editor opened from a test, or before a control
        // station has answered - every accessory reads as a switch, which is what most of them are.
        if (parent == null || parent.getModel() == null) return false;

        try
        {
            Accessory accessory =
                parent.getModel().getAccessoryByAddressIfPresent(
                    // As typed.  getAccessoryByAddressIfPresent takes the address a user would say
                    // and does the conversion to a UID itself, so subtracting here would look one
                    // switch to the left - which for a signal beside a switch is a real address
                    // holding the wrong kind of thing
                    Integer.parseInt(row.getTarget().trim()),
                    row.getProtocol() == null
                        ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : row.getProtocol());

            return accessory != null && accessory.isSignal();
        }
        catch (NumberFormatException e)
        {
            // A row with nothing in its address column yet, which is every row the moment it is added
            return false;
        }
    }

    /**
     * The row of buttons under a section's table.
     *
     * Asked of the layout rather than counted off by index.  It WAS an index, and adding a heading
     * above the table moved everything down one - so the capture controls were added to the scroll
     * pane, which is not a panel, and the editor stopped opening at all.  A layout knows where it put
     * things; a number written down elsewhere does not.
     *
     * @param section a panel built by section()
     * @return its button row
     */
    private static JPanel buttonsOf(JPanel section)
    {
        return (JPanel) ((BorderLayout) section.getLayout())
            .getLayoutComponent(BorderLayout.SOUTH);
    }

    /**
     * A label naming something the reader has to fill in: Segoe UI Semibold 13, black.
     *
     * @param text the label
     * @return the label
     */
    private static JLabel label(String text)
    {
        JLabel out = new JLabel(text);

        // The small labels beside a field - Name, S88, Trigger - are set the way the old route editor
        // sets its own: plain 14 in the heading blue.  They name the box next to them rather than
        // announcing a section, and semibold black made every one of them compete with the section
        // headings above.
        out.setFont(new java.awt.Font("Segoe UI", 0, 14));
        out.setForeground(HEADING_BLUE);

        return out;
    }

    /**
     * The blue this application uses for headings and for the labels beside fields.
     */
    private static final java.awt.Color HEADING_BLUE = new java.awt.Color(0, 0, 155);

    /** For a line that says something the rest of its level does not agree with. */
    private static final java.awt.Color WRONG = new java.awt.Color(190, 30, 30);

    /**
     * Says how the two halves of this window fit together.
     *
     * Worth a dialog rather than a tooltip: Adam built a route, looked at the conditions, and said he
     * did not understand how the logic worked and could see no objects in it. That is not a wording
     * problem with one control - it is somebody meeting a small language with nothing to say what it
     * is for.
     */
    private void showHelp()
    {
        JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameHelp"),
            I18n.t("ui.help"), JOptionPane.INFORMATION_MESSAGE);
    }

    private JButton button(String text, Runnable action)
    {
        JButton button = new JButton(text);

        // Segoe UI Bold 12, as every other button in this application is
        button.setFont(new java.awt.Font("Segoe UI", 1, 12));
        button.addActionListener(e -> action.run());

        return button;
    }

    /**
     * Fills the frame from a route, or leaves it empty for a new one.
     */
    private void load(Route route)
    {
        if (route == null)
        {
            enabledBox.setSelected(false);
            s88Field.setText("0");
            return;
        }

        nameField.setText(route.getName());
        s88Field.setText(String.valueOf(route.getS88()));
        triggerBox.setSelectedItem(triggerLabel(route.getTriggerType()));
        enabledBox.setSelected(route.isEnabled());

        // setSelected fires no ActionEvent, so the listener that greys these two never ran on the way
        // in: an existing automatic route opened with Automatic ticked and its sensor and trigger
        // boxes dead, and the only way to reach them was to untick Automatic and tick it again.
        showSensorIfAutomatic();

        List<RouteCommand> stored = route.getRoute();

        for (int at = 0; at < stored.size(); at++)
        {
            // Two lines that are one point come in as one row.
            //
            // Nothing in the file says so, so this is read rather than looked up: the pair has to
            // match one of the three shapes exactly, down to the pause and the order, or it stays
            // two ordinary rows.  A guess here would show a point that is not there and write a
            // different pair back out on the next save.
            ThreeWaySwitch point = ThreeWaySwitch.read(stored, at);

            if (point != null)
            {
                commands.rows.add(Entry.of(new CommandRow(CommandRow.Kind.THREE_WAY,
                    String.valueOf(point.getAddress()),
                    ThreeWaySwitch.wordFor(point.getPosition()),
                    point.getProtocol(), point.getSettle())));

                at++;

                continue;
            }

            // A kind with no controls becomes a read-only row rather than a remembered index.  It was
            // an index, held against the ORIGINAL position, and the editable rows moved under it: a
            // route of [Switch 1, run "Yard", Switch 2] whose first row is deleted put "Yard" AFTER
            // Switch 2, silently, having never shown it at all.  One list, one order, all of it on
            // screen.
            Entry loaded = Entry.of(stored.get(at));

            commands.rows.add(loaded.isEditable()
                ? Entry.of(asShown(loaded.getRow())) : loaded);
        }

        locked = route.isLocked();

        conditionsAsFound = route.getConditions();

        // The condition as an outline: one row per term, indented where it was bracketed.
        //
        // A bracketed condition used to arrive here as "rows cannot say this" - the table was
        // disabled, the expression printed underneath, capture refused, and the whole thing written
        // back untouched on save.  An outline can say it, so it is editable for the first time.
        {
            conditions.rows.addAll(ConditionOutline.of(conditionsAsFound));
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

        for (int at = 0; at < commands.rows.size(); at++)
        {
            try
            {
                built.addAll(commands.rows.get(at).toCommands(
                    Accessory.DEFAULT_IMPLICIT_PROTOCOL));
            }
            catch (IllegalArgumentException e)
            {
                // Which row, not just what is wrong with it.  "'' is not an address" is true of an
                // accessory added and never filled in, and says nothing about where to look - and a
                // route long enough to be worth building is long enough to have to hunt through.
                throw new IllegalArgumentException(
                    I18n.f("route.ui.frameRowNumberIsWrong", String.valueOf(at + 1),
                        String.valueOf(e.getMessage())));
            }
        }

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
        // The tick box that drives capture is greyed on a locked route, so nothing should arrive here
        // - but this is a public way into the command list, and the rule belongs with the list.
        if (locked) return;

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

                // Required as well as whatever is already there, which is what capturing several
                // things in a row means - and the word goes in as its own line, so one click makes
                // it "or".
                int depth = 0;

                for (int at = conditions.rows.size() - 1; at >= 0; at--)
                {
                    if (!conditions.rows.get(at).isJoiner())
                    {
                        depth = conditions.rows.get(at).getDepth();
                        break;
                    }
                }

                if (!conditions.rows.isEmpty())
                {
                    conditions.rows.add(ConditionOutline.Row.joining(depth,
                        ConditionOutline.Joiner.AND));
                }

                conditions.rows.add(ConditionOutline.Row.condition(depth, parsed));

                conditions.fireTableDataChanged();

                updateReadsAs();

                return;
            }

            commands.rows.add(Entry.of(parsed));

            // The same accessory thrown twice leaves ONE row, with the value it ended on.
            //
            // Somebody capturing a route changes their mind - a turnout goes the wrong way, is thrown
            // back, and without this both throws are in the route.  The old text editor filtered its
            // captured text this way and the rebuilt editor did not, so capture here recorded every
            // throw; the filter moved into RouteCapture when that editor was deleted, and this is
            // where it was always wanted.
            //
            // Through the LINES, because that is what the filter reads and what it has been tested
            // against - and a round trip through them is cheap next to the click that caused it.
            //
            // Caught on its own, so that a settle which cannot be done does not take the capture with
            // it.  It used to share the catch below: a row this could not express threw, the table was
            // never told about the row just added, and the capture went on filling a table that had
            // stopped changing on screen.  Everything it captured was still saved.
            try
            {
                settleCapturedRows();
            }
            catch (Exception cannot)
            {
                // The rows as they stand are the better answer
            }

            commands.fireTableDataChanged();
        }
        catch (Exception e)
        {
            // A line that will not parse is not worth interrupting a capture for
        }
    }

    /**
     * Collapses repeated captures of the same thing down to the last one.
     *
     * Only the rows this editor can express are offered to the filter; a kept command - one of a kind
     * there are no controls for - is left exactly where it is, because it did not come from a capture
     * and rewriting it through a text round trip is the one way to lose it.
     */
    private void settleCapturedRows() throws Exception
    {
        StringBuilder text = new StringBuilder();

        for (Entry entry : commands.rows)
        {
            if (!entry.isEditable()) return;

            // A three-way row stands for a PAIR of commands and refuses to answer as one, by design.
            // Settling is a tidy-up; where it cannot be done the rows on screen are the better answer.
            if (entry.getRow() != null && entry.getRow().getKind() == CommandRow.Kind.THREE_WAY) return;

            text.append(entry.toCommand().toLine(null));
        }

        String settled = org.traincontrol.base.RouteCapture.filterConfigCommands(text.toString());

        java.util.List<Entry> rebuilt = new ArrayList<>();

        for (String line : settled.split("\n"))
        {
            if (line.trim().isEmpty()) continue;

            RouteCommand one = RouteCommand.fromLine(line, false);

            // A line the filter produced and the parser will not take back is a disagreement between
            // the two, and the rows already on screen are the better answer
            if (one == null) return;

            rebuilt.add(Entry.of(one));
        }

        if (rebuilt.isEmpty()) return;

        commands.rows.clear();
        commands.rows.addAll(rebuilt);
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
    /**
     * Sets a cell of the command table, as typing in it would.
     *
     * For tests.  These four go through the table MODEL rather than round it, because the model is
     * where the interesting behaviour lives: what a row keeps and what it throws away when its kind
     * changes is decided there, and a test that built a CommandRow directly would be testing its own
     * arithmetic rather than the editor's.
     *
     * @param row which row
     * @param kind what to make it
     */
    public void setCommandKindForTest(int row, CommandRow.Kind kind)
    {
        commands.getModel().setValueAt(CommandRow.labelFor(kind), row, 3);
    }

    public void setCommandTargetForTest(int row, String target)
    {
        commands.getModel().setValueAt(target, row, 4);
    }

    public void setCommandSettingForTest(int row, String setting)
    {
        commands.getModel().setValueAt(setting, row, 6);
    }

    /**
     * @param row which row
     * @return what that row of the command table holds, or null where it is a kept command
     */
    public CommandRow commandRowForTest(int row)
    {
        return commands.rows.get(row).getRow();
    }

    /**
     * Whether the command list is showing its plus.
     *
     * For tests.  The plus is a value the table paints rather than a button in a cell, so there is
     * no component to ask - and it is exactly what went wrong: greying the table left the plus drawn
     * and working on a route belonging to the Central Station.
     *
     * @return true when there is a way to add a command
     */
    public boolean offersToAddCommands()
    {
        return ADD_HERE.equals(commands.getValueAt(commands.getRowCount() - 1, UP));
    }

    /**
     * The same question of the condition list.
     */
    public boolean offersToAddConditions()
    {
        return ADD_HERE.equals(conditions.getValueAt(conditions.getRowCount() - 1, UP));
    }

    public int conditionCount()
    {
        // Conditions, not lines.  The outline holds the joining words as lines of their own, so the
        // list is longer than the number of things it is asking about - and "how many conditions does
        // this route have" is a question about the conditions.
        int out = 0;

        for (ConditionOutline.Row row : conditions.rows)
        {
            if (!row.isJoiner()) out++;
        }

        return out;
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
        if (!conditionsEditable)
        {
            String text = conditionsAsFound == null ? ""
                : NodeExpression.toTextRepresentation(conditionsAsFound,
                    parent == null ? null : parent.getModel());

            readsAs.setText(I18n.f("route.ui.frameConditionsNotShown", text));
            return;
        }

        if (conditions.rows.size() < 2)
        {
            readsAs.setText(" ");
            return;
        }

        // What the outline means, in words and brackets.
        //
        // The shape is on screen, so this is not there to explain the nesting - it is there for the
        // one rule the shape alone does not state: a run of rows joined by the same word is a group,
        // and a change of word starts a new one.  Reading it back settles that without anybody having
        // to be told.
        readsAs.setText(I18n.f("route.ui.frameReadsAs",
            describe(ConditionOutline.toExpression(conditions.rows))));
    }

    /**
     * An expression in words, for the reading under the conditions.
     *
     * @param node the expression
     * @return what it says
     */
    private String describe(NodeExpression node)
    {
        if (node == null) return "";

        if (node instanceof org.traincontrol.base.NodeRouteCommand)
        {
            return shortly(((org.traincontrol.base.NodeRouteCommand) node).getRouteCommand());
        }

        if (node instanceof org.traincontrol.base.NodeAnd)
        {
            return describe(((org.traincontrol.base.NodeAnd) node).getLeft())
                + " " + I18n.t("route.ui.joinAnd") + " "
                + describe(((org.traincontrol.base.NodeAnd) node).getRight());
        }

        if (node instanceof org.traincontrol.base.NodeOr)
        {
            return describe(((org.traincontrol.base.NodeOr) node).getLeft())
                + " " + I18n.t("route.ui.joinOr") + " "
                + describe(((org.traincontrol.base.NodeOr) node).getRight());
        }

        if (node instanceof org.traincontrol.base.NodeGroup)
        {
            StringBuilder out = new StringBuilder("(");

            for (NodeExpression inside
                : ((org.traincontrol.base.NodeGroup) node).getExpressions())
            {
                out.append(describe(inside));
            }

            return out.append(")").toString();
        }

        return "";
    }

    // Which column is which, for the three that are pressed rather than typed in.  Named because
    // "column 8" in a click handler is a number nobody can check against the model that produced it.
    private static final int UP = 0;
    private static final int DOWN = 1;
    /** The row's number, which is read rather than filled in. */
    private static final int POSITION = 2;

    /** The shade a cell takes when its kind has no use for it. */
    private static final java.awt.Color UNUSABLE = new java.awt.Color(242, 242, 242);

    private static final int DELETE = 9;

    /**
     * Copies a row, and puts the copy directly under it.
     *
     * Beside the trash because the two are a pair: one takes a row away and the other makes another
     * of it, and both act on the row they sit in.  Most of a route is near-repetition - the same
     * switch at the next address, the same delay, the same protocol - and building each of those
     * from a blank row means choosing the kind, the protocol and the delay again every time to get
     * back to where the row above already was.
     */
    private static final int DUPLICATE = 10;

    /** The conditions table is narrower, so its trash sits in a different column. */
    private static final int CONDITION_DELETE = 8;

    /** Indenting a condition nests it; outdenting brings it back out. */
    private static final int INDENT = 2;
    private static final int OUTDENT = 3;

    private static final String INDENT_ROW = "indent";
    private static final String OUTDENT_ROW = "outdent";

    // The same two marks, drawn pale and doing nothing.
    //
    // They used to disappear when a line was as deep as the line above allowed, and a control that
    // vanishes reads as a feature that stops - which is exactly what Adam concluded, that only two
    // levels were possible.  Nesting goes as deep as anybody wants; a line simply cannot jump two
    // levels past its neighbour, and a pale mark says "not from here" where an absent one says
    // nothing at all.
    private static final String INDENT_LIMIT = "indent-limit";
    private static final String OUTDENT_LIMIT = "outdent-limit";

    // What a cell holds when it is one of those.  Values rather than icons, so the model stays a model
    // and the renderer decides what a mark looks like.
    private static final String MOVE_UP = "up";
    private static final String MOVE_DOWN = "down";
    private static final String DELETE_ROW = "delete";
    private static final String COPY_ROW = "copy";
    private static final String ADD_HERE = "add";

    /**
     * Squeezes a column down to the width of the mark in it.
     *
     * @param table the table
     * @param column which column
     */
    private static void narrow(JTable table, int column)
    {
        TableColumn narrow = table.getColumnModel().getColumn(column);

        narrow.setPreferredWidth(26);
        narrow.setMaxWidth(30);
        narrow.setMinWidth(22);
    }

    /**
     * Draws the marks, and makes them do something when pressed.
     *
     * The marks are drawn by the table's own renderer rather than by putting buttons in the cells,
     * because a table full of live components is a table that has to keep them in step with its rows -
     * and this one reorders and deletes rows constantly. A mark is a value the renderer knows how to
     * paint, and a click is a click on a cell.
     *
     * @param table the table
     * @param delete the delete column, or -1
     * @param up the move-up column, or -1
     * @param down the move-down column, or -1
     * @param addOn the column the plus sits under on the last row
     */
    private void actOnRowMarks(final JTable table, final int delete, final int up, final int down,
        final int addOn, final int... alsoMarked)
    {
        final int mark = Math.max(12, table.getRowHeight() - 10);

        final javax.swing.table.DefaultTableCellRenderer marks =
            new javax.swing.table.DefaultTableCellRenderer()
        {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable which, Object value,
                boolean selected, boolean focused, int row, int column)
            {
                boolean isMark = MOVE_UP.equals(value) || MOVE_DOWN.equals(value)
                    || DELETE_ROW.equals(value) || ADD_HERE.equals(value)
                    || COPY_ROW.equals(value);

                // Only a MARK is drawn as one.  This renderer also covers the column the plus sits
                // under - which is the position column - and blanking every cell in it took the row
                // numbers with it: a table numbered one to four showed four empty cells, and the
                // numbers are the thing that makes "row 4 cannot be saved" mean anything.
                JLabel out = (JLabel) super.getTableCellRendererComponent(which,
                    isMark ? "" : value, selected, false, row, column);

                out.setHorizontalAlignment(JLabel.CENTER);

                java.awt.Color ink = selected ? which.getSelectionForeground() : null;

                if (MOVE_UP.equals(value))
                {
                    out.setIcon(ink == null ? RowIcons.arrow(mark, true)
                        : RowIcons.arrow(mark, true, ink));
                }
                else if (MOVE_DOWN.equals(value))
                {
                    out.setIcon(ink == null ? RowIcons.arrow(mark, false)
                        : RowIcons.arrow(mark, false, ink));
                }
                // In the selection's own ink when the row is picked.
                //
                // These three are drawn in their own colours - green to add, red to delete, grey to
                // copy - and a selected row is painted in the look-and-feel's selection blue, against
                // which a mid-green plus is very nearly invisible.  It is the row somebody has just
                // clicked, so it is the mark they are most likely to be reaching for.  The arrows
                // above already did this; these three were written before that was noticed.
                else if (DELETE_ROW.equals(value))
                {
                    out.setIcon(ink == null ? RowIcons.trash(mark) : RowIcons.trash(mark, ink));
                }
                else if (COPY_ROW.equals(value))
                {
                    out.setIcon(ink == null ? RowIcons.copy(mark) : RowIcons.copy(mark, ink));
                }
                else if (ADD_HERE.equals(value))
                {
                    out.setIcon(ink == null ? RowIcons.plus(mark) : RowIcons.plus(mark, ink));
                }
                else out.setIcon(null);

                return out;
            }
        };

        for (int column : new int[]{up, down, delete, addOn})
        {
            if (column >= 0) table.getColumnModel().getColumn(column).setCellRenderer(marks);
        }

        // Any further column that carries a mark gets the renderer and the pointer, and NOT a second
        // copy of the listener below.
        //
        // This was a second whole call to this method, which added a second mouse listener to the same
        // table - and the listener dispatches on the VALUE under the pointer rather than on the column,
        // so both copies acted on every click.  One press of the trash deleted two commands: the first
        // listener removed the row, the second read the same cell again, found the row that had just
        // shifted up into it, and deleted that one too.  The arrows were worse than wrong - the second
        // listener moved the row back, so they did nothing at all.
        for (int column : alsoMarked)
        {
            if (column >= 0) table.getColumnModel().getColumn(column).setCellRenderer(marks);
        }

        table.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e)
            {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());

                if (row < 0 || column < 0) return;

                // Only where there is actually a mark.  An empty cell in one of these columns - the
                // top row has no way up - has to stay empty rather than being a hidden button.
                Object value = table.getValueAt(row, column);

                if (MOVE_UP.equals(value)) moveRow(table, row, -1);
                else if (MOVE_DOWN.equals(value)) moveRow(table, row, 1);
                else if (DELETE_ROW.equals(value)) deleteRow(table, row);
                else if (COPY_ROW.equals(value)) duplicateRow(table, row);
                else if (ADD_HERE.equals(value)) addTo(table);
            }
        });

        // The hand says "this does something" before anything is pressed, which is the whole
        // difference between a mark and a decoration
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e)
            {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());

                boolean marked = column == up || column == down || column == delete
                    || (row >= rowsOf(table) && column == addOn);

                for (int also : alsoMarked)
                {
                    if (column == also) marked = true;
                }

                boolean live = row >= 0 && column >= 0
                    && !"".equals(String.valueOf(table.getValueAt(row, column)))
                    && marked;

                table.setCursor(java.awt.Cursor.getPredefinedCursor(
                    live ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
            }
        });
    }

    /**
     * Greys every cell somebody cannot type into.
     *
     * A blank cell that will accept a value and a blank cell that will not look identical until one
     * is clicked, and finding out by clicking is a poor way to learn the rules of a table. The
     * commands table has done this since the columns were split; the conditions table had not.
     *
     * @param table the table
     */
    private void greyWhatCannotBeEdited(final JTable table)
    {
        final javax.swing.table.TableCellRenderer was = table.getDefaultRenderer(Object.class);

        table.setDefaultRenderer(Object.class, new javax.swing.table.TableCellRenderer()
        {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable which, Object value,
                boolean selected, boolean focused, int row, int column)
            {
                java.awt.Component out = was.getTableCellRendererComponent(which, value, selected,
                    focused, row, column);

                boolean editable = which.getModel().isCellEditable(row, column);

                // A joining word's row is left alone.
                //
                // Only its own column can be typed into, so every other cell in it was shaded - and a
                // row that is four-fifths grey reads as a row that is switched off, when it is one of
                // the two things the outline is MADE of.  The shading exists to tell an empty cell that
                // will take a value from one that will not, and a joiner has no such cells: there is
                // one word and nothing else, which the row already shows.
                if (which instanceof ConditionTable && ((ConditionTable) which).isJoinerRow(row))
                {
                    return out;
                }

                // And so is the row at the bottom with the + on it.
                //
                // Nothing in it can be edited, because it is not a row yet - it is the button that
                // makes one.  Shading it therefore shaded the whole line, which reads as a row that has
                // been switched off rather than as the way to add another, and it is the one line in
                // the table a new user is looking for.
                if (row >= which.getRowCount() - 1) return out;

                if (!selected)
                {
                    out.setForeground(editable ? which.getForeground() : java.awt.Color.GRAY);

                    // And a background, not only grey text.
                    //
                    // Most of these cells are EMPTY - a function number on a row that is not a
                    // function, a protocol on a locomotive command - and grey text in an empty cell
                    // is exactly as visible as black text in an empty cell.  So a column that could
                    // not be used looked no different from one that was simply not filled in yet,
                    // and the way to find out was to click it and watch nothing happen.
                    //
                    // Not the position column, which is not a field somebody might try to fill in -
                    // it is the row's number, and shading it would say it was disabled rather than
                    // that it is not for typing in.
                    if (!editable && column != POSITION && out instanceof javax.swing.JComponent)
                    {
                        ((javax.swing.JComponent) out).setOpaque(true);
                        out.setBackground(UNUSABLE);
                    }
                    else if (out instanceof javax.swing.JComponent)
                    {
                        out.setBackground(which.getBackground());
                    }
                }

                return out;
            }
        });
    }

    /**
     * Draws the two indent marks and makes them move a row in or out.
     *
     * Separate from actOnRowMarks because only the conditions have depth - a command list is a
     * sequence rather than a shape.
     *
     * @param table the conditions
     */
    private void actOnIndentMarks(final ConditionTable table)
    {
        final int mark = Math.max(12, table.getRowHeight() - 12);

        javax.swing.table.DefaultTableCellRenderer marks =
            new javax.swing.table.DefaultTableCellRenderer()
        {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable which, Object value,
                boolean selected, boolean focused, int row, int column)
            {
                JLabel out = (JLabel) super.getTableCellRendererComponent(which, "", selected,
                    false, row, column);

                out.setHorizontalAlignment(JLabel.CENTER);

                java.awt.Color ink = selected ? which.getSelectionForeground() : null;

                java.awt.Color pale = new java.awt.Color(215, 215, 215);

                if (INDENT_ROW.equals(value))
                {
                    out.setIcon(ink == null ? RowIcons.indent(mark, true)
                        : RowIcons.indent(mark, true, ink));
                }
                else if (OUTDENT_ROW.equals(value))
                {
                    out.setIcon(ink == null ? RowIcons.indent(mark, false)
                        : RowIcons.indent(mark, false, ink));
                }
                else if (INDENT_LIMIT.equals(value)) out.setIcon(RowIcons.indent(mark, true, pale));
                else if (OUTDENT_LIMIT.equals(value)) out.setIcon(RowIcons.indent(mark, false, pale));
                else out.setIcon(null);

                return out;
            }
        };

        table.getColumnModel().getColumn(INDENT).setCellRenderer(marks);
        table.getColumnModel().getColumn(OUTDENT).setCellRenderer(marks);

        table.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e)
            {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());

                if (row < 0 || column < 0) return;

                Object value = table.getValueAt(row, column);

                if (locked) return;

                if (INDENT_ROW.equals(value)) table.indent(row, 1);
                else if (OUTDENT_ROW.equals(value)) table.indent(row, -1);
            }
        });
    }

    /**
     * A joining word as it is shown.
     */
    private static String joinerLabel(ConditionOutline.Joiner joiner)
    {
        return I18n.t(joiner == ConditionOutline.Joiner.OR ? "route.ui.joinOr" : "route.ui.joinAnd");
    }

    /**
     * The joining word a label names, defaulting to AND - which is what a condition list has always
     * meant when nobody said otherwise.
     */
    private static ConditionOutline.Joiner joinerFor(String label)
    {
        return I18n.t("route.ui.joinOr").equals(label)
            ? ConditionOutline.Joiner.OR : ConditionOutline.Joiner.AND;
    }

    private int rowsOf(JTable table)
    {
        return table == commands ? commands.rows.size() : conditions.rows.size();
    }

    /**
     * Nothing in either table moves, goes or arrives on a route that is not ours to change.
     *
     * Checked here as well as where the marks are drawn, because these are also reached from the
     * keyboard and from anything added later that forgets to ask.  One rule, two guards: the drawing
     * one is what the user sees, this one is what makes it true.
     */
    private void moveRow(JTable table, int row, int by)
    {
        if (locked) return;

        if (table == commands) commands.shift(row, by);
        else conditions.shift(row, by);
    }

    private void deleteRow(JTable table, int row)
    {
        if (locked) return;

        if (table == commands) commands.removeAt(row);
        else conditions.removeAt(row);
    }

    /**
     * Puts a copy of a row directly under it.
     *
     * Under rather than at the end, because the reason to copy a row is almost always that the next
     * command is nearly the same one - and a route's order is its meaning, so a copy appearing eight
     * rows away would have to be walked back up by hand.
     */
    private void duplicateRow(JTable table, int row)
    {
        if (locked || table != commands) return;

        if (row < 0 || row >= commands.rows.size()) return;

        commands.duplicateAt(row);
    }

    private void addTo(JTable table)
    {
        if (locked) return;

        if (table == commands) commands.addRow();
        else if (conditionsEditable) conditions.addRow();
    }

    /**
     * Turns the whole window into something to read.
     *
     * Everything that could change the route goes quiet: the name, the trigger, both tables, the
     * formula, capture, and Save. Cancel stays, because closing a window you cannot change is the one
     * thing you certainly want to do, and so does Help.
     *
     * The title says why. A window full of greyed controls with no explanation reads as broken
     * software rather than as a rule, and "this one belongs to the Central Station" is a fact a user
     * can act on - they can change it there.
     */
    private void becomeReadOnly()
    {
        setTitle(I18n.f("route.ui.frameLockedTitle", originalName));

        // Not focusable either, not merely uneditable.
        //
        // An uneditable text field still takes the caret: it can be tabbed into, clicked into, and
        // its text selected, and it keeps the white background and the I-beam pointer of a box that
        // is waiting to be typed in.  Somebody who does that and finds nothing happens has been told
        // the window is broken rather than that the route belongs to the station.
        nameField.setEditable(false);
        nameField.setFocusable(false);

        s88Field.setEditable(false);
        s88Field.setFocusable(false);
        triggerBox.setEnabled(false);
        enabledBox.setEnabled(false);

        captureBox.setEnabled(false);
        captureTarget.setEnabled(false);

        commands.setEnabled(false);
        conditions.setEnabled(false);

        // The marks in the rows go with it.  A trash can that does nothing is worse than no trash can:
        // it says the row can be deleted and then declines, which reads as a fault rather than a rule.
        conditionsEditable = false;

        if (saveButton != null) saveButton.setEnabled(false);

        readsAs.setText(I18n.t("route.ui.frameLockedExplains"));
    }

    /**
     * A condition in as few words as possible, for the reading above.
     */
    private String shortly(RouteCommand command)
    {
        if (command == null) return "?";

        CommandRow row = CommandRow.of(command);

        // A kind this editor has no controls for.  Its own toString is the only description there is,
        // and printing it is better than printing nothing.
        if (row == null) return String.valueOf(command);

        switch (row.getKind())
        {
            // The words the row's own dropdown offers, so the reading and the table agree.  A signal
            // and a switch are the same device at the same address and the same two states, and only
            // the layout knows which is standing there - so the reading asks, exactly as the Setting
            // column does.
            case ACCESSORY:
            case SIGNAL:
                return I18n.f(row.getKind() == CommandRow.Kind.SIGNAL
                        ? "route.reads.signal" : "route.reads.switch",
                    row.getTarget(), settingWords(row)[command.getSetting() ? 1 : 0]);

            case FEEDBACK: return I18n.f("route.reads.sensor",
                row.getTarget(), settingWords(row)[command.getSetting() ? 1 : 0]);

            case FUNCTION: return I18n.f("route.reads.function", row.getTarget(),
                command.getFunction(), command.getSetting() ? "on" : "off");

            case LOCOMOTIVE_SPEED: return I18n.f("route.reads.speed",
                row.getTarget(), row.getSetting());

            case LOCOMOTIVE_DIRECTION: return I18n.f("route.reads.direction",
                row.getTarget(), row.getSetting());

            case ROUTE: return I18n.f("route.reads.route", row.getTarget());

            // "Train X is standing at sensor 21" - the one kind that is a fact rather than an order,
            // which is why it is only ever a condition.
            case AUTO_LOCOMOTIVE: return I18n.f("route.reads.trainAt",
                row.getTarget(), row.getSetting());

            // Stop, all functions off, lights on: nothing to name and nothing to set, so the kind's
            // own label already says the whole thing.
            default: return CommandRow.labelFor(row.getKind());
        }
    }

    /**
     * The problems as a list somebody can read down.
     *
     * Numbered, because "there are four things wrong" and "here are four sentences" are different
     * amounts of help - and capped, because a route pasted in from somewhere else can have a fault
     * in every row and a dialog taller than the screen has no buttons on it.
     */
    private static String listed(List<String> wrong)
    {
        StringBuilder out = new StringBuilder();

        int shown = Math.min(wrong.size(), 12);

        for (int at = 0; at < shown; at++)
        {
            out.append("\n    ").append(at + 1).append(".  ").append(wrong.get(at));
        }

        if (wrong.size() > shown)
        {
            out.append("\n\n").append(I18n.f("route.ui.frameAndMoreProblems",
                String.valueOf(wrong.size() - shown)));
        }

        return out.toString();
    }

    /**
     * Everything wrong with the window, in the order a reader would work through it.
     *
     * Gathered rather than reported one at a time.  Save used to stop at the first problem, so a
     * route with three things wrong took three attempts to find out - and each attempt named one
     * cell, which reads as the editor changing its mind about what it wants.
     *
     * The point of most of these is that the row LOOKS right.  Changing an accessory into a
     * locomotive command used to leave the address behind in the name column, giving a command for a
     * locomotive called "3" - a name no locomotive has.  Nothing refused it: the row built, the
     * route saved, and it did nothing whatever when it ran.  A route that quietly does nothing is
     * the worst thing this editor can produce, because there is no error anywhere to lead anybody
     * back to it.
     *
     * @return the problems, empty when there are none
     */
    private List<String> everythingWrong()
    {
        List<String> wrong = new ArrayList<>();

        if (nameField.getText().trim().isEmpty()) wrong.add(I18n.t("route.ui.frameNeedsAName"));

        int s88;

        try
        {
            s88 = Integer.parseInt(s88Field.getText().trim());

            // Not abs().  A typed minus sign was silently turned into the positive address, so a
            // route triggered off a sensor the user never named - and there is no way to tell from
            // the saved route that it happened.
            if (s88 < 0) s88 = -1;
        }
        catch (NumberFormatException e)
        {
            s88 = -1;
        }

        if (s88 < 0) wrong.add(I18n.t("route.ui.frameS88NotANumber"));

        // Automatic with no sensor is a route that can never fire by itself: the sensor IS the thing
        // that fires it.  Saved, it would sit in the list marked automatic and do nothing, which is
        // the quietest way for a route to be wrong.
        if (enabledBox.isSelected() && s88 == 0)
        {
            wrong.add(I18n.t("route.ui.frameAutomaticNeedsSensor"));
        }

        if (commands.rows.isEmpty()) wrong.add(I18n.t("route.ui.frameNeedsACommand"));

        for (int at = 0; at < commands.rows.size(); at++)
        {
            Entry entry = commands.rows.get(at);

            if (!entry.isEditable()) continue;

            for (String problem : problemsWith(entry.getRow()))
            {
                wrong.add(I18n.f("route.ui.frameRowNumberIsWrong", String.valueOf(at + 1), problem));
            }
        }

        // A level that disagrees with itself means two things at once, and the editor has been
        // showing which line is the problem in red.  Saving it would be picking one of the two
        // meanings quietly, and the route would then fire at times nobody asked for.
        if (conditions.hasProblems()) wrong.add(I18n.t("route.ui.frameLogicDisagrees"));

        if (conditionsEditable)
        {
            int line = 0;

            for (ConditionOutline.Row row : conditions.rows)
            {
                if (row.isJoiner()) continue;

                line++;

                for (String problem : problemsWith(CommandRow.of(row.getCommand())))
                {
                    wrong.add(I18n.f("route.ui.frameConditionNumberIsWrong",
                        String.valueOf(line), problem));
                }
            }
        }

        return wrong;
    }

    /**
     * What is wrong with one row, if anything.
     *
     * Two sorts of thing.  The first is a row that cannot be built at all - a blank address, a speed
     * that is not a number - which the row itself refuses, and this asks it.  The second is a row
     * that builds perfectly well and names something that does not exist: a locomotive nobody owns,
     * a route nobody wrote, an address no decoder can carry.  Only the layout knows about those, and
     * nothing was asking it.
     *
     * @param row the row, or null for a kind the editor has no controls for
     * @return the problems with it
     */
    private List<String> problemsWith(CommandRow row)
    {
        List<String> wrong = new ArrayList<>();

        if (row == null) return wrong;

        try
        {
            row.toCommands(Accessory.DEFAULT_IMPLICIT_PROTOCOL);
        }
        catch (IllegalArgumentException e)
        {
            wrong.add(String.valueOf(e.getMessage()));

            // No point asking whether a locomotive called "" exists
            return wrong;
        }

        if (parent == null || parent.getModel() == null) return wrong;

        String target = row.getTarget() == null ? "" : row.getTarget().trim();

        switch (row.getKind())
        {
            // The name of a locomotive on this layout, not any text at all.  This is the one Adam
            // found: an accessory turned into a locomotive command kept its address as the name.
            case LOCOMOTIVE_SPEED:
            case LOCOMOTIVE_DIRECTION:
            case FUNCTION:
            case AUTO_LOCOMOTIVE:
                if (parent.getModel().getLocByName(target) == null)
                {
                    wrong.add(I18n.f("route.ui.frameNoSuchLocomotive", target));
                }
                else if (!RouteCommand.isNameUsable(target))
                {
                    // A comma or a bracket in the name breaks the formats a route is written in: the
                    // command line is comma-separated, and the condition parser rewrites brackets
                    // into line breaks.  The old editor refused such a name at both doors; this one
                    // offered it in a dropdown and saved it, which produces a route file that reads
                    // back as something else.  Real names look like this - "SBB 460 (2)".
                    wrong.add(I18n.f("route.ui.frameNameNotUsable", target));
                }

                break;

            // A route that calls a route that is not there does nothing, and says nothing either
            case ROUTE:
                if (!parent.getModel().getRouteList().contains(target))
                {
                    wrong.add(I18n.f("route.ui.frameNoSuchRoute", target));
                }
                else if (target.equals(originalName))
                {
                    wrong.add(I18n.t("route.ui.frameRouteCallsItself"));
                }

                break;

            case ACCESSORY:
            case SIGNAL:
                addressProblem(wrong, target, row.getProtocol());
                break;

            // Both motors, because the second is the one the user never typed and so the one they
            // would have no way of knowing was out of range
            case THREE_WAY:
                addressProblem(wrong, target, row.getProtocol());
                addressProblem(wrong, String.valueOf(numberOr(target, 0) + 1), row.getProtocol());
                break;

            case FEEDBACK:
                if (numberOr(target, 0) <= 0)
                {
                    wrong.add(I18n.f("route.ui.frameNotASensor", target));
                }

                break;

            default:
                break;
        }

        // A function number past the end of what that locomotive has.  The old editor built its list
        // from the locomotive and so could not offer one; this one takes a typed number, and F30 on a
        // five-function loco saves cleanly and does nothing on the rails.
        if (row.getKind() == CommandRow.Kind.FUNCTION)
        {
            org.traincontrol.base.Locomotive loco = parent.getModel().getLocByName(target);

            int number = numberOr(functionNumberOf(row.getSetting()), -1);

            if (loco != null && (number < 0 || number >= loco.getNumF()))
            {
                wrong.add(I18n.f("route.ui.frameNoSuchFunction",
                    String.valueOf(number) + " / " + target));
            }
        }

        // A speed the decoder cannot be given.  RouteCommand takes the number as typed, so a
        // hundred and fifty is saved, sent, and quietly clipped by something further down.
        //
        // A NEGATIVE speed is not a mistake: RouteCommand.parseLine documents it as an instant stop,
        // and MarklinRoute calls instantStop() for it.  Refusing everything below zero blocked a
        // route that already contained one from being saved AT ALL - the row loaded, the editor
        // reported it as wrong, and the only ways out were to discard the edit or to change what the
        // command meant.  Locking somebody out of their own route is worse than the typo this check
        // was written to catch.
        if (row.getKind() == CommandRow.Kind.LOCOMOTIVE_SPEED)
        {
            int speed = numberOr(row.getSetting(), Integer.MIN_VALUE);

            if (speed == Integer.MIN_VALUE || speed < INSTANT_STOP || speed > 100)
            {
                wrong.add(I18n.f("route.ui.frameNotASpeed", String.valueOf(row.getSetting())));
            }
        }

        return wrong;
    }

    /**
     * Adds a complaint when an address is not one the decoder can carry.
     *
     * MM2 and DCC have different ranges, and an address past the end of either is not a switch that
     * fails to move - it is a command sent to something that is not there.
     */
    private void addressProblem(List<String> wrong, String target,
        Accessory.accessoryDecoderType protocol)
    {
        Accessory.accessoryDecoderType speaks =
            protocol == null ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : protocol;

        int address = numberOr(target, 0);

        if (address <= 0 || !Accessory.isValidAddress(address, speaks))
        {
            wrong.add(I18n.f("route.ui.frameNotAnAddress", target + " (" + speaks + ")"));
        }
    }

    /**
     * A number, or a fallback where the text is not one.
     */
    /**
     * The speed that means "stop now" rather than "coast to zero".
     *
     * Documented in RouteCommand.parseLine and acted on in MarklinRoute: any negative speed is an
     * instant stop.  Named here so the validation below reads as a rule rather than as a magic -1.
     */
    private static final int INSTANT_STOP = -1;

    /**
     * A locomotive to start an autonomy condition off with, or blank where there are none.
     */
    private String firstLocomotive()
    {
        if (parent == null || parent.getModel() == null) return "";

        java.util.List<String> names = parent.getModel().getLocList();

        return names == null || names.isEmpty() ? "" : names.get(0);
    }

    private static int numberOr(String text, int fallback)
    {
        try
        {
            return Integer.parseInt(text == null ? "" : text.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    /**
     * Says whether this route would fire as the railway stands.
     *
     * The two halves are asked separately, because they fail for different reasons and a single
     * "no" would leave the user to guess which: the trigger sensor is either occupied or it is not,
     * and the conditions either hold or they do not.  Both have to be true for the route to run.
     *
     * Ported from the old editor, which read the expression out of a text box; this one asks the
     * outline, so what is tested is what the table shows rather than what was last typed.
     */
    /**
     * Lights this route on the track diagram: what it commands in yellow, what it checks in orange.
     *
     * Read off the WINDOW rather than off the saved route, so it answers about what is on screen -
     * including rows typed a moment ago and not yet saved, which is when somebody most wants to know
     * where they are.
     *
     * Held for five seconds.  Long enough to look from here to the diagram and back, short enough that
     * nothing has to be cleared afterwards - a highlight that stays until it is dismissed is a highlight
     * somebody leaves on.
     */
    private void highlightOnDiagram()
    {
        if (parent == null) return;

        java.util.Set<Integer> commanded = new java.util.LinkedHashSet<>();
        java.util.Set<Integer> checked = new java.util.LinkedHashSet<>();

        for (Entry entry : commands.rows)
        {
            org.traincontrol.base.RouteCommand command = entry.toCommand();

            if (command != null && command.getAddress() > 0) commanded.add(command.getAddress());
        }

        for (ConditionOutline.Row row : conditions.rows)
        {
            if (row.isJoiner() || row.getCommand() == null) continue;

            if (row.getCommand().getAddress() > 0) checked.add(row.getCommand().getAddress());
        }

        // A square that is BOTH commanded and checked is drawn as commanded.  It is the stronger of the
        // two statements - the route does something to it - and two washes on one tile is a colour
        // neither of them chose.
        checked.removeAll(commanded);

        int lit = parent.highlightAddresses(commanded, org.traincontrol.util.ImageUtil.HIGHLIGHT,
            HIGHLIGHT_HOLD_MS);

        lit += parent.highlightAddresses(checked,
            org.traincontrol.util.ImageUtil.HIGHLIGHT_CONDITION, HIGHLIGHT_HOLD_MS);

        // Nothing lit is an answer too, and a silent button is not.  It happens for a real reason: a
        // route can name accessories that are not drawn anywhere on the diagram.
        if (lit == 0)
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.infoNothingToHighlight"));
        }
    }

    /** Five seconds: long enough to look from this window to the diagram and back */
    private static final int HIGHLIGHT_HOLD_MS = 5000;

    private JButton highlightButton;

    private void testAgainstTheRailway()
    {
        if (parent == null || parent.getModel() == null) return;

        try
        {
            boolean sensor = parent.getModel().getFeedbackState(s88Field.getText().trim());

            NodeExpression conditions = conditionsEditable
                ? ConditionOutline.toExpression(this.conditions.rows) : conditionsAsFound;

            // No conditions is a route held up by nothing but its sensor, which is true rather than
            // unknown - a route with an empty condition list fires whenever it is triggered.
            boolean held = conditions == null || conditions.evaluate(parent.getModel());

            JOptionPane.showMessageDialog(this,
                I18n.f("route.ui.messageTriggeringConditionSummary",
                    I18n.t(sensor ? "route.ui.valueTrue" : "route.ui.valueFalse"),
                    I18n.t(held ? "route.ui.valueTrue" : "route.ui.valueFalse"),
                    I18n.t(sensor && held ? "route.ui.valueWould" : "route.ui.valueWouldNot")));
        }
        catch (Exception e)
        {
            // A sensor that names nothing, or an address that is not a number: the same message the
            // old editor gave, because the answer is the same - there is something in here that
            // cannot be evaluated, and the route would not fire on it either.
            JOptionPane.showMessageDialog(this,
                I18n.t("route.ui.errorConditionExpressionInvalid"));
        }
    }

    /**
     * Builds the route back up and hands it to the same code the text editor uses.
     */
    private void onSave()
    {
        List<String> wrong = everythingWrong();

        if (!wrong.isEmpty())
        {
            // The window stays open on Fix, so the user is looking at the cells the list names.
            //
            // Discard is offered beside it because there is a state this editor can get into that no
            // amount of fixing is worth: a row somebody was experimenting with, in a route they no
            // longer want changed.  Without it, the only way out of a route with a problem in it was
            // to correct the problem in order to be allowed to close the window.
            String[] answers = { I18n.t("route.ui.frameGoAndFixIt"), I18n.t("route.ui.frameDiscard") };

            int answer = JOptionPane.showOptionDialog(this,
                I18n.f("route.ui.frameCannotSaveYet", listed(wrong)),
                I18n.t("route.ui.frameCannotSaveYetTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, answers, answers[0]);

            if (answer == 1) dispose();

            return;
        }

        String name = nameField.getText().trim();

        int s88 = Integer.parseInt(s88Field.getText().trim());

        List<RouteCommand> built = commandsAsSaved();

        NodeExpression expression = ConditionOutline.toExpression(conditions.rows);

        Route.s88Triggers trigger = triggerFor(String.valueOf(triggerBox.getSelectedItem()));

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

            // A route that a diagram tile triggers has to tell the main window, the way the old
            // editor does: the tile carries the route's name, and renaming one here left the tile
            // pointing at a route that no longer answers to it.
            parent.layoutEditingComplete();

            // A sync only when the route is NEW, which is what the old editor settled on years ago
            // and what this one copied without reading the comment beside it.
            //
            // Routes travel one way: the Central Station's are imported and marked locked, and
            // nothing is ever written back - editRoute is a delete-then-re-add in the local database
            // and the station is never told.  So a sync after an EDIT re-fetches the layouts, the
            // locomotives, the accessories and the routes over the network to learn nothing, and the
            // route import it runs is keyed by id: a local route sharing an id with one on the
            // station is deleted and replaced by the station's version.  It cannot help and it can
            // hurt.  Restarting the route's monitoring thread, which is the thing that really has to
            // happen, editRoute already does - it disables the old route before re-adding it.
            //
            // A NEW route is different in one way worth the round trip: its id is the next free one
            // in the LOCAL database, which knows nothing about routes added on the station since the
            // last sync.  Syncing now surfaces a collision while the user is still looking at the
            // route they just made, rather than at the next startup.
            // And only when the station HAS routes of its own.
            //
            // The paragraph above is about an id collision with a route added on the station since the
            // last sync.  A layout whose routes are all local cannot have one: every id in the database
            // came from the database.  So the round trip - layouts, locomotives, accessories and routes,
            // over the network - buys nothing there, and Adam asked why it was happening at all.
            if (originalName.isEmpty() && anyRouteCameFromTheStation()) parent.syncWithCS2();

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
     * A delay from what the user typed.
     *
     * A BLANK clears it - emptying the cell is how a delay is removed, and there is no other way to
     * say it.  Rubbish leaves it as it was, because a mistyped number is not a request to change
     * anything and silently becoming zero would be a timing change nobody asked for.
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
         * What this entry writes into the route - two commands where it stands for a point.
         */
        List<RouteCommand> toCommands(Accessory.accessoryDecoderType protocol)
        {
            if (row != null) return row.toCommands(protocol);

            List<RouteCommand> out = new ArrayList<>();

            out.add(kept);

            return out;
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
                // One more than there are rows.  The last is where a new command is added, and it is
                // part of the table rather than a button beside it so that adding happens where the
                // adding will appear - a button in another panel is a different place from the one
                // the row lands in.
                return rows.size() + 1;
            }

            @Override
            public int getColumnCount()
            {
                return 11;
            }

            @Override
            public String getColumnName(int column)
            {
                switch (column)
                {
                    // Move, position, and delete all live IN the row now.  They were buttons under
                    // the table acting on whichever row happened to be selected, which is one more
                    // thing to get right before anything happens: select the row, find the button,
                    // press it, check it did what you meant.
                    case UP: return "";
                    case DOWN: return "";

                    // The position, which a route needs and a list of rows does not show.  Order is
                    // the whole meaning of a route - a turnout thrown after the train has passed is a
                    // different railway from one thrown before.
                    case 2: return "#";
                    case 3: return I18n.t("route.ui.frameColKind");
                    case 4: return I18n.t("route.ui.frameColTarget");

                    // The function NUMBER, which only one kind has.  A function command names three
                    // things - which locomotive, which function, and whether it goes on or off - and
                    // the middle one used to be packed into the same cell as the last, written
                    // "3:on".  That is a format to be learned rather than a thing to be chosen, and
                    // it is why the on/off could not be a dropdown.
                    case 5: return I18n.t("route.ui.frameColNumber");

                    case 6: return I18n.t("route.ui.frameColSetting");
                    case 7: return I18n.t("route.ui.frameColProtocol");
                    case 8: return I18n.t("route.ui.frameColDelay");
                    default: return "";
                }
            }

            @Override
            public Object getValueAt(int row, int column)
            {
                // The adding row: a plus under the position column and nothing else
                if (row >= rows.size())
                {
                    return column == UP && !locked ? ADD_HERE : "";
                }

                Entry entry = rows.get(row);

                // No marks on a route that is not ours to change.  A mark that declines when it is
                // pressed reads as a fault; one that was never drawn reads as the rule it is.
                if (column == UP) return row > 0 && !locked ? MOVE_UP : "";
                if (column == DOWN) return row < rows.size() - 1 && !locked ? MOVE_DOWN : "";
                if (column == DELETE) return locked ? "" : DELETE_ROW;
                if (column == DUPLICATE) return locked ? "" : COPY_ROW;

                if (column == 2) return String.valueOf(row + 1);

                // A kept command has no columns to fill, so it reads as its own stored line in the
                // first one.  Better an unfamiliar line than a blank row doing something unexplained.
                if (!entry.isEditable())
                {
                    return column == 3 ? entry.describe() : "";
                }

                CommandRow at = entry.getRow();

                switch (column)
                {
                    case 3: return CommandRow.labelFor(at.getKind());

                    // Blank where the kind has no such thing, rather than whatever the row was
                    // carrying before it became a stop.  A greyed cell with a stale address in it
                    // reads as a value that is being used and cannot be changed, which is the
                    // opposite of what it means.
                    case 4: return CommandRow.hasTarget(at.getKind()) ? at.getTarget() : "";

                    case 5: return CommandRow.isFunction(at.getKind())
                        ? functionNumberOf(at.getSetting()) : "";

                    case 6:
                        if (!CommandRow.hasSetting(at.getKind())) return "";

                        return CommandRow.isFunction(at.getKind())
                            ? functionStateOf(at.getSetting()) : at.getSetting();

                    case 7:
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
                if (locked) return false;

                // The adding row, and the three columns that are pressed rather than typed in
                if (row >= rows.size()) return false;

                if (column == UP || column == DOWN || column == DELETE || column == DUPLICATE
                    || column == 2)
                {
                    return false;
                }

                Entry entry = rows.get(row);

                if (!entry.isEditable()) return false;

                CommandRow at = entry.getRow();

                if (column == 4) return CommandRow.hasTarget(at.getKind());
                if (column == 5) return CommandRow.isFunction(at.getKind());
                if (column == 6) return CommandRow.hasSetting(at.getKind());
                if (column == 7) return CommandRow.hasProtocol(at.getKind());
                if (column == 8) return CommandRow.hasDelay(at.getKind());

                return true;
            }

            @Override
            public void setValueAt(Object value, int row, int column)
            {
                if (row >= rows.size()) return;

                Entry entry = rows.get(row);

                if (!entry.isEditable()) return;

                CommandRow at = entry.getRow();

                String text = value == null ? "" : value.toString().trim();

                CommandRow.Kind kind = column == 3 ? CommandRow.kindFor(text) : at.getKind();

                // A different kind is a different command, and it starts empty.
                //
                // The setting was already replaced, because the vocabularies do not overlap and the
                // old word would be refused at Save.  The TARGET was carried over, and its
                // vocabularies do not overlap either - they merely both accept text.  Turning
                // "Accessory 3" into a locomotive command therefore produced a command for a
                // locomotive called "3", which is a name no locomotive has: it saved without
                // complaint and did nothing at all when the route ran.
                boolean became = column == 3 && kind != at.getKind();

                String target = column == 4 ? text : at.getTarget();

                // Changing the KIND replaces the setting with one the new kind accepts.  The
                // vocabularies do not overlap, so carrying the old word over left a row that looks
                // fine and is refused at Save with a message about a cell the user never touched.
                // A function's two halves are edited in two columns and stored as one value, which
                // is the model's business rather than the user's
                String setting = at.getSetting();

                if (column == 3 && kind != at.getKind()) setting = CommandRow.defaultSettingFor(kind);
                else if (column == 5) setting = text + ":" + functionStateOf(at.getSetting());
                else if (column == 6) setting = CommandRow.isFunction(kind)
                    ? functionNumberOf(at.getSetting()) + ":" + text : text;

                // Every rebuild carries protocol and delay forward.  Editing the SETTING of a DCC
                // accessory used to move it to MM2, because the row was rebuilt from three columns
                // and the fourth thing it knew was simply not passed on.
                Accessory.accessoryDecoderType protocol = at.getProtocol();
                int delay = at.getDelay();

                if (column == 7) protocol = protocolOf(text);
                if (column == 8) delay = delayOf(text, at.getDelay());

                if (became)
                {
                    target = "";
                    protocol = null;
                    delay = CommandRow.defaultDelayFor(kind);
                }

                // The address decides which of the two it really is.
                //
                // Switch and Signal are one command in two vocabularies, and only the layout knows
                // which is standing at an address.  So typing one follows the layout: put in the
                // address of a signal and the row becomes a Signal, with red and green in its
                // setting box, without the user having to know that the kind box was the thing to
                // fix.  Deliberately NOT done when the user has just chosen the kind by hand - that
                // would make the box refuse to be set - and not for an address the layout has never
                // heard of, which is every row the moment it is added.
                if (column == 4 && (kind == CommandRow.Kind.ACCESSORY
                    || kind == CommandRow.Kind.SIGNAL))
                {
                    CommandRow.Kind resolved = kindAtAddress(target, protocol, kind);

                    if (resolved != kind)
                    {
                        kind = resolved;
                        setting = CommandRow.defaultSettingFor(kind);
                    }
                }

                // A kind that takes no target or setting does not keep the ones it had, so the blank
                // the table shows and the row underneath it say the same thing
                if (!CommandRow.hasTarget(kind)) target = "";
                if (!CommandRow.hasSetting(kind)) setting = "";

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
            setBackground(java.awt.Color.WHITE);

            // Same as the conditions: the marks down either side are controls, not data
            setShowVerticalLines(false);
            setGridColor(new java.awt.Color(228, 228, 228));

            JComboBox<String> kinds = new JComboBox<>();

            // Only what a route can DO.  "Train at a sensor" is a fact rather than an instruction,
            // so it belongs in the conditions and nowhere else - it was offered here because this
            // list offered every kind there is.
            for (CommandRow.Kind kind : CommandRow.Kind.values())
            {
                if (CommandRow.canBeACommand(kind)) kinds.addItem(CommandRow.labelFor(kind));
            }

            getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(kinds));

            JComboBox<String> protocols = new JComboBox<>();

            for (Accessory.accessoryDecoderType type : Accessory.accessoryDecoderType.values())
            {
                protocols.addItem(type.toString());
            }

            getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(protocols));

            narrow(this, UP);
            narrow(this, DOWN);
            narrow(this, DELETE);
            narrow(this, DUPLICATE);

            TableColumn positionColumn = getColumnModel().getColumn(2);
            positionColumn.setPreferredWidth(30);
            positionColumn.setMaxWidth(40);

            TableColumn kindColumn = getColumnModel().getColumn(3);
            kindColumn.setPreferredWidth(170);

            getColumnModel().getColumn(5).setPreferredWidth(60);
            getColumnModel().getColumn(7).setPreferredWidth(70);
            getColumnModel().getColumn(8).setPreferredWidth(70);

            // The duplicate column is named here rather than in a second call: a second call would
            // register a second mouse listener on this table, and both would act on every click
            actOnRowMarks(this, DELETE, UP, DOWN, UP, DUPLICATE);

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

                    // Both kinds of "you cannot type here": a whole row that is a kept command, and a
                    // single cell whose kind has no such thing - a stop has no address.  The second was
                    // drawn in ordinary black, so an empty cell that could not be filled looked exactly
                    // like an empty cell waiting to be.
                    boolean editable = row < rows.size() && rows.get(row).isEditable()
                        && model.isCellEditable(row, column);

                    c.setEnabled(editable);

                    if (!selected)
                    {
                        c.setForeground(editable
                            ? table.getForeground() : java.awt.Color.GRAY);
                    }

                    return c;
                }
            });

            // Wrapped AROUND the renderer above, and it has to be after it.
            //
            // It was called BEFORE, in the fix for AR-19, and setDefaultRenderer here replaced it six
            // lines later - so the change I reported as landed had no effect at all.  That fix was also
            // wrong about what was missing: this table has greyed by KIND all along, through
            // model.isCellEditable in the renderer above.
            //
            // What it does not do is the background.  Most unusable cells are EMPTY - a function number
            // on a signal command, a protocol on a stop - and grey text in an empty cell looks exactly
            // like black text in an empty cell.  That is what Adam was asking for, and it is what
            // greyWhatCannotBeEdited adds: a wash, plus the exemption that keeps the + row unshaded.
            greyWhatCannotBeEdited(this);
        }

        /**
         * The words this row's setting may take, or null where it is a number or a name.
         *
         * Turn or straight, on or off, forward or backward: three closed pairs that were typed by
         * hand, spelled wrongly, and refused at Save with a message about a word the user had no way
         * of knowing. A speed and a function number stay as text, because they are not a choice.
         */
        @Override
        public javax.swing.table.TableCellEditor getCellEditor(int row, int column)
        {
            if (row < 0 || row >= rows.size() || !rows.get(row).isEditable())
            {
                return super.getCellEditor(row, column);
            }

            if (column == 6)
            {
                String[] words = settingWords(rows.get(row).getRow());

                if (words != null) return chooseFrom(words);
            }

            // And the target, wherever the answer comes from a list this application already has.
            //
            // A locomotive is named exactly, or the command names nothing; a route likewise.  Typing
            // either by hand is an invitation to a typo that is only discovered when the route does
            // not do what it says - so where the set of right answers is known, it is offered.
            // A number where a number is meant.  An address is a number and a delay is a number, and
            // a cell that will take "twelve" or "12a" is a cell that produces a route refused at Save
            // for something typed several minutes earlier.
            if (column == 5 || column == 8) return digitsOnly();

            if (column == 4)
            {
                CommandRow.Kind kind = rows.get(row).getRow().getKind();

                if (kind == CommandRow.Kind.ACCESSORY || kind == CommandRow.Kind.FEEDBACK)
                {
                    return digitsOnly();
                }

                if (kind == CommandRow.Kind.ROUTE)
                {
                    return chooseFrom(namesOf(parent.getModel().getRouteList()));
                }

                if (kind == CommandRow.Kind.LOCOMOTIVE_SPEED
                    || kind == CommandRow.Kind.LOCOMOTIVE_DIRECTION
                    || kind == CommandRow.Kind.FUNCTION
                    || kind == CommandRow.Kind.AUTO_LOCOMOTIVE)
                {
                    return chooseFrom(namesOf(parent.getModel().getLocList()));
                }
            }

            return super.getCellEditor(row, column);
        }

        /**
         * Copies a row and puts the copy under it.
         *
         * A kept command - one of a kind this editor has no controls for - is copied as it stands,
         * so duplicating one does not quietly turn it into something the editor CAN show.
         */
        void duplicateAt(int at)
        {
            if (at < 0 || at >= rows.size()) return;

            Entry was = rows.get(at);

            rows.add(at + 1, was.isEditable()
                ? Entry.of(new CommandRow(was.getRow().getKind(), was.getRow().getTarget(),
                    was.getRow().getSetting(), was.getRow().getProtocol(), was.getRow().getDelay()))
                : Entry.of(was.toCommand()));

            model.fireTableDataChanged();

            setRowSelectionInterval(at + 1, at + 1);
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
            shift(getSelectedRow(), by);
        }

        /**
         * Not "move": Component has had a move(int, int) since AWT 1.0, and overriding it by accident
         * is how a row reorder becomes a window reposition.
         */
        void shift(int at, int by)
        {
            int to = at + by;

            if (at < 0 || at >= rows.size() || to < 0 || to >= rows.size()) return;

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
     * The conditions, as an indented list with the joining words on lines of their own.
     *
     * One line of the table is one line of the outline - a condition, or a word - so what is on screen
     * and what is stored are the same shape. An earlier version kept the word on the condition and
     * interleaved the two when drawing, which worked and meant that indenting a word was impossible:
     * it had no depth of its own to change.
     *
     * ConditionOutline holds the rule and the tests for it; this is what it looks like.
     */
    private final class ConditionTable extends JTable
    {
        /**
         * Whether the row at this line is a joining word rather than a condition.
         *
         * Asked by the renderer, which is handed a row number and nothing else.
         *
         * @param line the row
         * @return whether it is a joiner
         */
        boolean isJoinerRow(int line)
        {
            return line >= 0 && line < conditions.rows.size() && conditions.rows.get(line).isJoiner();
        }

        private final List<ConditionOutline.Row> rows = new ArrayList<>();

        /** How many pixels one level of indentation is worth. */
        private static final int STEP = 16;

        /** The lines whose word disagrees with its level, refreshed whenever anything changes. */
        private java.util.Set<Integer> flagged = new java.util.LinkedHashSet<>();

        private final AbstractTableModel model = new AbstractTableModel()
        {
            @Override
            public int getRowCount()
            {
                return rows.size() + 1;
            }

            @Override
            public int getColumnCount()
            {
                return 9;
            }

            @Override
            public String getColumnName(int column)
            {
                switch (column)
                {
                    case 4: return I18n.t("route.ui.frameColKind");
                    case 5: return I18n.t("route.ui.frameColTarget");
                    case 6: return I18n.t("route.ui.frameColSetting");
                    case 7: return I18n.t("route.ui.frameColProtocol");
                    default: return "";
                }
            }

            @Override
            public Object getValueAt(int line, int column)
            {
                if (line >= rows.size())
                {
                    // On the left, where a new line begins rather than where its kind will land
                    return column == UP && conditionsEditable ? ADD_HERE : "";
                }

                ConditionOutline.Row row = rows.get(line);

                if (!conditionsEditable && (column <= OUTDENT || column == CONDITION_DELETE))
                {
                    return "";
                }

                // Conditions only, and only where there is another condition that way to swap with.
                // A word does not move by hand, and an arrow on one would have nothing to do.
                if (column == UP) return ConditionOutline.canMove(rows, line, -1) ? MOVE_UP : "";
                if (column == DOWN) return ConditionOutline.canMove(rows, line, 1) ? MOVE_DOWN : "";

                if (column == INDENT)
                {
                    if (line == 0) return "";

                    return row.getDepth() <= rows.get(line - 1).getDepth()
                        ? INDENT_ROW : INDENT_LIMIT;
                }

                if (column == OUTDENT) return row.getDepth() > 0 ? OUTDENT_ROW : OUTDENT_LIMIT;

                if (column == CONDITION_DELETE) return DELETE_ROW;

                if (row.isJoiner())
                {
                    return column == 4 ? joinerLabel(row.getJoiner()) : "";
                }

                CommandRow term = asShown(CommandRow.of(row.getCommand()));

                if (term == null) return column == 4 ? String.valueOf(row.getCommand()) : "";

                if (column == 4) return CommandRow.labelFor(term.getKind());
                if (column == 5) return CommandRow.hasTarget(term.getKind()) ? term.getTarget() : "";
                if (column == 6) return CommandRow.hasSetting(term.getKind()) ? term.getSetting() : "";

                if (column == 7)
                {
                    if (!CommandRow.hasProtocol(term.getKind())) return "";

                    return (term.getProtocol() == null
                        ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : term.getProtocol()).toString();
                }

                return "";
            }

            @Override
            public boolean isCellEditable(int line, int column)
            {
                if (!conditionsEditable || line >= rows.size()) return false;

                if (column <= OUTDENT || column == CONDITION_DELETE) return false;

                ConditionOutline.Row row = rows.get(line);

                if (row.isJoiner()) return column == 4;

                // As SHOWN, not as stored - see asShown.  The display path already converts, and a
                // cell whose editability is decided from a different kind than the one on screen is a
                // cell that argues with itself.
                CommandRow term = asShown(CommandRow.of(row.getCommand()));

                if (term == null) return false;

                if (column == 5) return CommandRow.hasTarget(term.getKind());
                if (column == 6) return CommandRow.hasSetting(term.getKind());
                if (column == 7) return CommandRow.hasProtocol(term.getKind());

                return true;
            }

            @Override
            public void setValueAt(Object value, int line, int column)
            {
                if (line >= rows.size()) return;

                ConditionOutline.Row row = rows.get(line);

                String text = value == null ? "" : value.toString();

                if (row.isJoiner())
                {
                    if (column == 4) rows.set(line, row.joinedBy(joinerFor(text)));

                    settle();
                    fireTableDataChanged();

                    return;
                }

                // As SHOWN.  The user answered a question the display asked, so the answer has to be
                // interpreted in the display's vocabulary: picking "red" from a signal row must build a
                // signal row, not an accessory row carrying the word "red" by accident.  toCommand
                // treats the two kinds identically and accepts all four words, so what is STORED is
                // unchanged either way.
                CommandRow term = asShown(CommandRow.of(row.getCommand()));

                if (term == null) return;

                CommandRow edited;

                if (column == 4)
                {
                    CommandRow.Kind became = CommandRow.kindFor(text);

                    if (became == null) return;

                    // A different kind is a different condition, and it does not keep the old
                    // target.  A sensor number left behind in an accessory row is an address, and it
                    // would be a perfectly good one belonging to something else entirely.
                    //
                    // Reset to something that BUILDS rather than to blank: this table stores each
                    // line as a built command, so a line with nothing in its address cannot be held
                    // at all - the rebuild below would throw and the keystroke would vanish.  The
                    // commands table above holds rows rather than commands and so can be left
                    // properly empty.
                    //
                    // "Train X is at sensor N" is the awkward one: its target is a NAME and its
                    // setting is an address, and the blank default meant choosing it did nothing
                    // whatever - the dropdown offered the kind, the row threw on rebuild, and the
                    // cell snapped back with no message.  The first locomotive on the roster is a
                    // starting point somebody can then change, which is what every other kind gets.
                    String starting = became == CommandRow.Kind.AUTO_LOCOMOTIVE
                        ? firstLocomotive() : (CommandRow.hasTarget(became) ? "1" : "");

                    String settingFor = became == CommandRow.Kind.AUTO_LOCOMOTIVE
                        ? "1" : CommandRow.defaultSettingFor(became);

                    edited = new CommandRow(became, starting, settingFor,
                        null, CommandRow.defaultDelayFor(became));
                }
                else
                {
                    edited = new CommandRow(term.getKind(),
                        column == 5 ? text : term.getTarget(),
                        column == 6 ? text : term.getSetting(),
                        column == 7 ? protocolOf(text) : term.getProtocol(),
                        term.getDelay());

                    // The address decides which of the two it really is - the same rule the commands
                    // table follows, and for the same reason.  Switch and Signal are one command in
                    // two vocabularies and only the layout knows which is standing at an address, so
                    // typing a signal's address here turns the row into a Signal with red and green in
                    // its setting box.  It worked when building a route and not when building the
                    // condition that fires it, which is the same question asked in the same words.
                    if (column == 5 && (edited.getKind() == CommandRow.Kind.ACCESSORY
                        || edited.getKind() == CommandRow.Kind.SIGNAL))
                    {
                        CommandRow.Kind resolved = kindAtAddress(edited.getTarget(),
                            edited.getProtocol(), edited.getKind());

                        if (resolved != edited.getKind())
                        {
                            edited = new CommandRow(resolved, edited.getTarget(),
                                CommandRow.defaultSettingFor(resolved),
                                edited.getProtocol(), edited.getDelay());
                        }
                    }
                }

                try
                {
                    rows.set(line, row.about(edited.toCommand()));
                }
                catch (IllegalArgumentException e)
                {
                    // A half-typed address is not a reason to refuse the keystroke; Save reports a
                    // line that cannot be built, and names it
                    return;
                }

                settle();
                fireTableRowsUpdated(line, line);
            }
        };

        ConditionTable()
        {
            setModel(model);
            setRowHeight(24);
            setBackground(java.awt.Color.WHITE);

            // No vertical rules.  The marks down either side are controls rather than data, and a
            // grid line beside them makes them read as columns of a table somebody must fill in.
            setShowVerticalLines(false);
            setGridColor(new java.awt.Color(228, 228, 228));

            JComboBox<String> joiners = new JComboBox<>();

            joiners.addItem(joinerLabel(ConditionOutline.Joiner.AND));
            joiners.addItem(joinerLabel(ConditionOutline.Joiner.OR));

            JComboBox<String> kinds = new JComboBox<>();

            for (CommandRow.Kind kind : CommandRow.Kind.values())
            {
                if (CommandRow.canBeACondition(kind)) kinds.addItem(CommandRow.labelFor(kind));
            }

            // One column, two meanings, so the editor depends on the line
            this.kindEditor = new DefaultCellEditor(kinds);
            this.joinerEditor = new DefaultCellEditor(joiners);

            JComboBox<String> protocols = new JComboBox<>();

            for (Accessory.accessoryDecoderType type : Accessory.accessoryDecoderType.values())
            {
                protocols.addItem(type.toString());
            }

            getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(protocols));

            narrow(this, UP);
            narrow(this, DOWN);
            narrow(this, INDENT);
            narrow(this, OUTDENT);
            narrow(this, CONDITION_DELETE);

            getColumnModel().getColumn(4).setPreferredWidth(190);

            getColumnModel().getColumn(4).setCellRenderer(
                new javax.swing.table.DefaultTableCellRenderer()
            {
                @Override
                public java.awt.Component getTableCellRendererComponent(JTable which, Object value,
                    boolean selected, boolean focused, int line, int column)
                {
                    JLabel out = (JLabel) super.getTableCellRendererComponent(which, value, selected,
                        focused, line, column);

                    boolean joiner = line < rows.size() && rows.get(line).isJoiner();
                    int depth = line < rows.size() ? rows.get(line).getDepth() : 0;

                    // The guides run down the joining words too, so a word and the conditions it
                    // joins share a level visibly rather than by inference
                    out.setBorder(RowIcons.guides(depth, STEP));

                    out.setFont(joiner
                        ? new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 13)
                        : new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));

                    if (!selected)
                    {
                        // Red where a word disagrees with the rest of its level.  Two different words
                        // at one level is a sentence with two meanings, and this is where it is said.
                        out.setForeground(flagged.contains(line) ? WRONG
                            : joiner ? HEADING_BLUE : which.getForeground());
                    }

                    return out;
                }
            });

            greyWhatCannotBeEdited(this);

            actOnRowMarks(this, CONDITION_DELETE, UP, DOWN, UP);
            actOnIndentMarks(this);
        }

        private final javax.swing.table.TableCellEditor kindEditor;
        private final javax.swing.table.TableCellEditor joinerEditor;

        @Override
        public javax.swing.table.TableCellEditor getCellEditor(int line, int column)
        {
            if (line < 0 || line >= rows.size()) return super.getCellEditor(line, column);

            ConditionOutline.Row row = rows.get(line);

            if (column == 4) return row.isJoiner() ? joinerEditor : kindEditor;

            if (row.isJoiner()) return super.getCellEditor(line, column);

            // As SHOWN, and this is the one that mattered.
            //
            // The cell DISPLAYS "red" - getValueAt has converted since this table was written - and the
            // dropdown was built from the stored kind, so it offered "straight" and "turn".  A combo
            // whose model does not contain the current value falls back to its first entry, and one
            // click into that cell and out again committed it: a condition that tested a signal at
            // danger quietly became one that tested it clear.  That is the exact mechanism asShown's
            // own javadoc describes for the commands table, left in place here.
            CommandRow term = asShown(CommandRow.of(row.getCommand()));

            if (term == null) return super.getCellEditor(line, column);

            if (column == 6)
            {
                String[] words = settingWords(term);

                if (words != null) return chooseFrom(words);

                if (term.getKind() == CommandRow.Kind.AUTO_LOCOMOTIVE) return digitsOnly();
            }

            if (column == 5)
            {
                if (term.getKind() == CommandRow.Kind.AUTO_LOCOMOTIVE)
                {
                    return chooseFrom(namesOf(parent.getModel().getLocList()));
                }

                return digitsOnly();
            }

            return super.getCellEditor(line, column);
        }

        /**
         * Adds a condition, with the word that joins it to what is already there.
         *
         * Both lines at once, because a condition with no word before it is not a thing anybody wants
         * and would have to be typed in separately - and the word is written rather than implied, so
         * one click makes it "or".
         */
        void addRow()
        {
            int depth = 0;

            for (int at = rows.size() - 1; at >= 0; at--)
            {
                if (!rows.get(at).isJoiner())
                {
                    depth = rows.get(at).getDepth();
                    break;
                }
            }

            if (!rows.isEmpty()) rows.add(ConditionOutline.Row.joining(depth,
                ConditionOutline.Joiner.AND));

            rows.add(ConditionOutline.Row.condition(depth,
                RouteCommand.RouteCommandFeedback(1, true)));

            settle();
            model.fireTableDataChanged();
        }

        void removeSelected()
        {
            removeAt(getSelectedRow());
        }

        /**
         * Removes a line, and the word that went with it.
         *
         * A condition taken out on its own leaves the word that joined it with nothing on one side,
         * which is not what removing a requirement means.
         */
        void removeAt(int line)
        {
            if (line < 0 || line >= rows.size()) return;

            boolean joiner = rows.get(line).isJoiner();

            rows.remove(line);

            if (!joiner)
            {
                // The word before it by preference, since "1 and 2" without 2 is "1"
                if (line - 1 >= 0 && rows.get(line - 1).isJoiner()) rows.remove(line - 1);
                else if (line < rows.size() && rows.get(line).isJoiner()) rows.remove(line);
            }

            tidy();

            settle();
            model.fireTableDataChanged();
        }

        /**
         * Moves a condition past the one above or below it, leaving the outline's shape alone.
         *
         * Not by moving the LINE.  The joining words are lines of their own here, so lifting a
         * condition one line put it next to the word above instead of past it - and a word with a
         * word on one side and nothing on the other is not a sentence, so tidy() swept it away.
         * Three conditions joined by two ANDs came back as three conditions joined by one, which is
         * a change to when the route fires made by a button that says nothing about firing.
         *
         * So the two conditions trade places and every word stays where it is.  The shape on screen
         * IS the logic - which condition is nested inside which group, joined by which word - and
         * reordering the conditions within it is what the arrows are for.  "A and (B or C)" with B
         * moved up is "B and (A or C)": the same sentence about different sensors, which is what
         * somebody pressing the arrow beside B means.
         *
         * @param line the row that was pressed
         * @param by -1 for up, 1 for down
         */
        void shift(int line, int by)
        {
            if (!ConditionOutline.canMove(rows, line, by)) return;

            List<ConditionOutline.Row> after = ConditionOutline.moved(rows, line, by);

            rows.clear();
            rows.addAll(after);

            // No tidy(): nothing about the shape changed, only which condition sits where in it.
            settle();
            model.fireTableDataChanged();

            // Where the condition ended up, which is past the words rather than one line along
            int to = line + by;

            while (to >= 0 && to < rows.size() && rows.get(to).isJoiner()) to += by;

            setRowSelectionInterval(to, to);
        }

        void indent(int line, int by)
        {
            if (line < 0 || line >= rows.size()) return;

            int was = rows.get(line).getDepth();
            int depth = was + by;

            if (depth < 0) return;

            // One level deeper than the line above, at most.  Two at once would be a nesting with a
            // hole in the middle, which the outline has no way to draw and no way to mean.
            if (line > 0 && depth > rows.get(line - 1).getDepth() + 1) return;

            if (line == 0 && depth > 0) return;

            // Everything nested UNDER this line comes with it.
            //
            // A line and the lines indented beneath it are one thing - that is what indenting them
            // said - so moving the top of it and leaving the rest behind would take a group apart
            // rather than move it.  Outdenting is the same in reverse, and stops at the outermost
            // level rather than pulling anything past it.
            int last = line;

            while (last + 1 < rows.size() && rows.get(last + 1).getDepth() > was) last++;

            for (int at = line; at <= last; at++)
            {
                int moved = rows.get(at).getDepth() + by;

                rows.set(at, rows.get(at).atDepth(Math.max(0, moved)));
            }

            tidy();

            settle();
            model.fireTableDataChanged();
            setRowSelectionInterval(line, line);
        }

        /**
         * Pulls the outline back into a shape it can have.
         *
         * Moving or deleting can leave a line indented two levels past its new neighbour, or a word
         * at either end with nothing to join. Rather than refuse the move - which would make
         * reordering feel arbitrary - the outline is straightened afterwards.
         */
        private void tidy()
        {
            for (int at = 0; at < rows.size(); at++)
            {
                int most = at == 0 ? 0 : rows.get(at - 1).getDepth() + 1;

                if (rows.get(at).getDepth() > most)
                {
                    rows.set(at, rows.get(at).atDepth(most));
                }
            }

            // A word with nothing on one side of it, or two in a row
            boolean again = true;

            while (again)
            {
                again = false;

                for (int at = 0; at < rows.size(); at++)
                {
                    if (!rows.get(at).isJoiner()) continue;

                    boolean nothingBefore = at == 0;
                    boolean nothingAfter = at == rows.size() - 1;
                    boolean doubled = (at > 0 && rows.get(at - 1).isJoiner())
                        || (at < rows.size() - 1 && rows.get(at + 1).isJoiner());

                    if (nothingBefore || nothingAfter || doubled)
                    {
                        rows.remove(at);
                        again = true;
                        break;
                    }
                }
            }
        }

        /**
         * Works out which lines disagree with their level, and says what the outline means.
         */
        private void settle()
        {
            flagged = ConditionOutline.problems(rows);

            updateReadsAs();
        }

        /**
         * @return whether anything is flagged, so Save can refuse
         */
        boolean hasProblems()
        {
            return !flagged.isEmpty();
        }

        void fireTableDataChanged()
        {
            settle();
            model.fireTableDataChanged();
        }
    }
}
