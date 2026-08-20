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
     * Held so that a route belonging to the Central Station can grey it.
     */
    private JButton saveButton;

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
     */
    private String formula = "";

    /**
     * The bubbles, one per piece of the formula.
     */
    private final JPanel formulaView = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));

    /**
     * Which pieces are picked out for grouping, by their position in the formula.
     *
     * Shift-click, the same gesture the track diagram uses to pick several squares - and it has to be
     * a different gesture from plain click, because plain click is how a piece is removed.
     */
    private final java.util.Set<Integer> picked = new java.util.LinkedHashSet<>();

    /**
     * The terms as things to click, so the letters need not be remembered or typed.
     */
    private final JPanel termPills = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

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

        setIconImage(java.awt.Toolkit.getDefaultToolkit().getImage(
            TrainControlUI.class.getResource("resources/locicon.png")));

        // In words rather than in constants.  CLEAR_THEN_OCCUPIED is precise and says nothing to
        // somebody who has not read the code: what it means on a railway is that a train arrived.
        for (Route.s88Triggers trigger : Route.s88Triggers.values())
        {
            triggerBox.addItem(triggerLabel(trigger));
        }

        setContentPane(build());

        load(routeName);

        // The blocks, once the formula is known.  Everything else that changes it rebuilds them; this
        // is the first draw.
        refreshFormula();

        // After load(), which is what discovers whether the route is the station's
        if (locked) becomeReadOnly();

        pack();
        setLocationRelativeTo(parent);
    }

    private JPanel build()
    {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(header(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 8));

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

        // Under the table rather than beside it.  The terms are read down the table and the formula
        // is written over them, so putting it alongside asked the reader to look sideways in the
        // middle of one thought.
        conditionSection.add(buildFormulaRow(), BorderLayout.SOUTH);

        middle.add(conditionSection);

        content.add(middle, BorderLayout.CENTER);

        // Save on the left, Cancel on the right, and a line above the pair.
        //
        // They were together at the bottom left, which put them directly under the Add and Remove
        // buttons of the table above and made them read as two more of those.  The buttons that act
        // on a row and the buttons that finish with the window are different kinds of thing, and the
        // old route editor keeps them apart the same way: its Save Changes is bottom left and its
        // Cancel is bottom right, with the width of the window between them.
        JPanel buttons = new JPanel(new BorderLayout());

        buttons.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)),
            BorderFactory.createEmptyBorder(6, 0, 0, 0)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        saveButton = button(I18n.t("route.ui.frameSave"), this::onSave);

        left.add(saveButton);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        // Help beside Cancel rather than at the top: it is the same offer the old editor makes, and
        // this window needs it more - the formula under the conditions is a small language, and a
        // small language with nothing explaining it is a box people leave empty.
        right.add(button(I18n.t("ui.help"), this::showHelp));
        right.add(button(I18n.t("route.ui.frameCancel"), this::dispose));

        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        content.add(buttons, BorderLayout.SOUTH);

        return content;
    }

    /**
     * Shows the sensor and the trigger only when the route fires by itself.
     */
    private void showSensorIfAutomatic()
    {
        boolean automatic = enabledBox.isSelected();

        sensorLabel.setVisible(automatic);
        s88Field.setVisible(automatic);
        triggerLabelText.setVisible(automatic);
        triggerBox.setVisible(automatic);
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
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        row.add(label(I18n.t("route.ui.frameName")));
        row.add(nameField);

        // The tick first, and the sensor only when it is ticked.
        //
        // A route fires automatically FROM a sensor - that is the whole of what automatic means here -
        // so a sensor box standing next to an unticked box is asking a question that has no bearing on
        // anything yet.  Ticking is what raises it.
        row.add(enabledBox);

        row.add(sensorLabel);
        row.add(s88Field);
        row.add(triggerLabelText);
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

        panel.add(below, BorderLayout.SOUTH);

        return panel;
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
            // Whichever pair belongs to the thing at that address.  A signal and a switch are the same
            // device and the same two states; offering a signal "turn" and "straight" is asking
            // somebody to translate their own layout.  An address that names nothing yet - a row just
            // added - gets the switch words, which is what most accessories are.
            case ACCESSORY: return isSignalAt(row)
                ? new String[]{"green", "red"} : new String[]{"straight", "turn"};

            case FEEDBACK: return new String[]{"off", "on"};
            case LOCOMOTIVE_DIRECTION: return new String[]{"forward", "backward"};

            // Now that the number has a column of its own, what is left of a function's setting is
            // one of two words - which is a choice, and a choice is a dropdown
            case FUNCTION: return new String[]{"off", "on"};
            default: return null;
        }
    }

    /**
     * Whether the accessory this row names is a signal.
     *
     * @param row the row
     * @return true only when there is an accessory at that address and it is a signal
     */
    private boolean isSignalAt(CommandRow row)
    {
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

    /**
     * The formula, with the terms above it as things to click.
     *
     * Clickable because the letters are POSITIONAL - A is whatever is in the first row - so asking
     * anybody to remember which is which would be asking them to hold the table in their head while
     * they look away from it. Each pill says its letter and what it means, and pressing one writes it
     * where the cursor is.
     *
     * @return the panel
     */
    private JPanel buildFormulaRow()
    {
        JPanel row = new JPanel(new BorderLayout(4, 4));

        row.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 8));

        // Heading and the one-line box together at the top.
        //
        // The box used to be the whole middle of this panel, stretched by the layout to the height of
        // the table beside it - so a place to type one line of algebra looked like a large empty area
        // with some buttons under it, and Adam could not tell it was a box at all.  A field that is
        // one line tall reads as a field.
        JPanel top = new JPanel(new BorderLayout(0, 2));

        JLabel heading = new JLabel(I18n.t("route.ui.frameFormula"));

        heading.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13));
        heading.setForeground(HEADING_BLUE);

        top.add(heading, BorderLayout.NORTH);

        // Blocks, not text.
        //
        // A formula is a small language, and a box somebody can type into is a box that will hold
        // things the language does not accept - at which point the window's job becomes explaining a
        // syntax error about a route somebody only wanted to change one term of.  Shown as its pieces,
        // there is no state it can be in that the reader cannot read: each piece is a thing, clicking
        // one takes it out, and what is left is tidied so it still means something.
        formulaView.setBackground(java.awt.Color.WHITE);
        formulaView.setToolTipText(I18n.t("route.ui.tooltipFormula"));
        formulaView.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204), 1),
            BorderFactory.createEmptyBorder(2, 4, 2, 4)));

        top.add(formulaView, BorderLayout.SOUTH);

        row.add(top, BorderLayout.NORTH);

        termPills.setOpaque(false);

        row.add(termPills, BorderLayout.CENTER);

        // What the formula means, in words, directly under the thing it is about.  It used to sit in
        // the row of Add and Remove buttons on the far side of the window, where it read as a caption
        // for those.
        readsAs.setVerticalAlignment(JLabel.TOP);

        row.add(readsAs, BorderLayout.SOUTH);

        return row;
    }

    /**
     * Rebuilds the row of clickable terms from the table.
     *
     * Called whenever the rows change, because a letter names a POSITION - delete the first row and
     * every letter after it means something else.
     */
    private void refreshTermPills()
    {
        termPills.removeAll();

        for (int at = 0; at < conditions.rows.size(); at++)
        {
            final String letter = org.traincontrol.base.ConditionFormula.letterFor(at);

            // The letter AND what it stands for.  A row of bare letters is a row of things nobody
            // can choose between: the whole point of a handle is that it is short, and the whole
            // problem with a short handle is that it says nothing.
            JButton pill = new JButton(letter + " - "
                + shortly(conditions.rows.get(at).getCommand()));

            pill.setFont(new java.awt.Font("Segoe UI", 1, 12));
            pill.setMargin(new java.awt.Insets(0, 6, 0, 6));
            pill.setFocusable(false);
            pill.setToolTipText(I18n.f("route.ui.tooltipTermPill", letter));

            pill.addActionListener(e -> insertIntoFormula(letter));

            termPills.add(pill);
        }

        // The joining words, for the same reason the letters are here: they are part of the language
        // and typing them is not the point of the exercise
        for (final String word : new String[]{"and", "or"})
        {
            JButton pill = new JButton(word);

            pill.setFont(new java.awt.Font("Segoe UI", 0, 12));
            pill.setMargin(new java.awt.Insets(0, 6, 0, 6));
            pill.setFocusable(false);

            pill.addActionListener(e -> insertIntoFormula(word));

            termPills.add(pill);
        }

        // Brackets go ROUND something rather than in a place.
        //
        // One button, and it wraps whatever is selected - which is what a bracket is for and how
        // somebody thinks about it: "these two go together".  Two buttons that each insert half a
        // pair leave it possible to write an opening one and never the closing one, and then the
        // window has to explain an unbalanced formula to somebody who was only grouping two terms.
        JButton bracket = new JButton(I18n.t("route.ui.frameGroup"));

        bracket.setFont(new java.awt.Font("Segoe UI", 0, 12));
        bracket.setMargin(new java.awt.Insets(0, 6, 0, 6));
        bracket.setFocusable(false);
        bracket.setToolTipText(I18n.t("route.ui.tooltipGroup"));

        bracket.addActionListener(e -> bracketSelection());

        termPills.add(bracket);

        // No "clear".  Every piece can be taken out by clicking it, so a button that removes all of
        // them at once is a second way of doing the same thing with more to regret.

        termPills.revalidate();
        termPills.repaint();
    }

    /**
     * Puts brackets round whatever is selected in the formula.
     *
     * With nothing selected there is nothing to group, and saying so beats inserting an empty pair
     * that the reader then has to delete.
     */
    private void bracketSelection()
    {
        if (picked.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameSelectToGroup"));
            return;
        }

        java.util.List<String> pieces =
            org.traincontrol.base.ConditionFormula.tokens(formula);

        int from = java.util.Collections.min(picked);
        int to = java.util.Collections.max(picked);

        if (from < 0 || to >= pieces.size()) return;

        // A run, not a scattering.  Brackets go round something continuous, and picking the first and
        // third pieces of a formula does not describe anything you could write down.
        pieces.add(to + 1, ")");
        pieces.add(from, "(");

        StringBuilder out = new StringBuilder();

        for (String piece : pieces)
        {
            boolean space = out.length() > 0 && !")".equals(piece)
                && out.charAt(out.length() - 1) != '(';

            if (space) out.append(' ');

            out.append(piece);
        }

        formula = out.toString().trim();

        picked.clear();

        refreshFormula();
    }

    /**
     * Draws the formula as blocks, and keeps everything that depends on it in step.
     *
     * Rebuilt whole rather than patched, because the pieces are positional: taking one out renumbers
     * every one after it, and a half-updated row of blocks would answer clicks about pieces that have
     * moved.
     */
    private void refreshFormula()
    {
        formulaView.removeAll();

        java.util.List<String> pieces = org.traincontrol.base.ConditionFormula.tokens(formula);

        if (pieces.isEmpty())
        {
            JLabel empty = new JLabel(I18n.t("route.ui.frameFormulaEmpty"));

            empty.setFont(new java.awt.Font("Segoe UI", 2, 13));
            empty.setForeground(new java.awt.Color(130, 130, 130));

            formulaView.add(empty);
        }

        for (int at = 0; at < pieces.size(); at++)
        {
            final int which = at;
            final String piece = pieces.get(at);

            JButton block = new JButton(piece);

            block.setFont(new java.awt.Font("Segoe UI", 1, 12));
            block.setMargin(new java.awt.Insets(1, 7, 1, 7));
            block.setFocusable(false);
            block.setToolTipText(I18n.t("route.ui.tooltipBlock"));

            // Picked blocks show it, because grouping needs to say what it is about to group
            if (picked.contains(at))
            {
                block.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0, 0, 155), 2));
            }

            block.addActionListener(e ->
            {
                boolean picking = (e.getModifiers() & java.awt.event.ActionEvent.SHIFT_MASK) != 0;

                if (picking)
                {
                    // Shift to pick, the same gesture the track diagram uses to pick several squares -
                    // and it has to differ from a plain click, because that is how a block is removed
                    if (!picked.remove(which)) picked.add(which);
                }
                else
                {
                    formula = org.traincontrol.base.ConditionFormula.without(formula, which);

                    picked.clear();
                }

                refreshFormula();
            });

            formulaView.add(block);
        }

        formulaView.revalidate();
        formulaView.repaint();

        updateReadsAs();
    }

    /**
     * Adds a term to the end of the formula, joined by AND.
     *
     * One call, so that everything that has to move when the terms change moves together: the formula
     * itself, the buttons that name the terms, and the reading underneath. They came apart once
     * already - the letters are positional, so a term added or removed renames every one after it.
     *
     * @param letter the handle to add
     */
    private void appendToFormula(String letter)
    {
        formula = formula.trim().isEmpty() ? letter : formula.trim() + " and " + letter;

        refreshTermPills();
        refreshFormula();
    }

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

    /**
     * Writes a letter or a word into the formula where the cursor is.
     *
     * With a space either side where one is wanted, because "AandB" is not a formula and making the
     * user tidy up after a button they pressed is worse than not having the button.
     *
     * @param what the text to insert
     */
    private void insertIntoFormula(String what)
    {
        // Always at the end.  There is no cursor in a row of blocks, and inventing one - a gap that
        // moves as you click - would be a text box wearing a costume.
        formula = (formula.trim() + " " + what).trim();

        refreshFormula();
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
        triggerBox.setSelectedItem(triggerLabel(route.getTriggerType()));
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

        locked = route.isLocked();

        conditionsAsFound = route.getConditions();

        // Every term the condition is built from, in the order somebody reading it would meet them -
        // and then the formula that combines them, written over those same terms.
        //
        // A bracketed condition used to arrive here as "rows cannot say this": the table was disabled,
        // the expression was printed underneath, capture was refused, and the whole thing was written
        // back untouched on save.  A formula can say it, so it is editable for the first time.
        java.util.List<RouteCommand> terms =
            org.traincontrol.base.ConditionFormula.termsOf(conditionsAsFound);

        formula = org.traincontrol.base.ConditionFormula.formulaFor(conditionsAsFound, terms);

        List<ConditionRows.Row> rows = new ArrayList<>();

        for (RouteCommand term : terms) rows.add(new ConditionRows.Row(null, term));

        {
            conditions.rows.addAll(rows);

            refreshTermPills();
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
                built.add(commands.rows.get(at).toCommand());
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

                conditions.rows.add(new ConditionRows.Row(null, parsed));
                conditions.fireTableDataChanged();

                // Into the formula as well as into the table.  A term nothing refers to is a fact
                // nobody asked about: it would sit in the list looking captured and take no part in
                // whether the route fires, which is the quietest possible way to waste somebody's
                // afternoon.  ANDed on, because that is what capturing several things in a row means.
                String letter = org.traincontrol.base.ConditionFormula.letterFor(
                    conditions.rows.size() - 1);

                formula = formula.trim().isEmpty() ? letter : formula.trim() + " and " + letter;

                refreshTermPills();
                refreshFormula();

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

        // The formula, with each letter replaced by what it stands for.
        //
        // Worth showing for two reasons.  The obvious one is that a letter says nothing about the
        // railway.  The other is precedence: "A or B and C" is "A or (B and C)", as it is in every
        // language that has both words, and that is the one rule here somebody might expect to work
        // the other way round.  Reading it back in words settles the question without anybody having
        // to know the rule.
        if (conditionsEditable)
        {
            if (formula.trim().isEmpty())
            {
                readsAs.setText(" ");
                return;
            }

            String problem = org.traincontrol.base.ConditionFormula.problemWith(
                formula, rows.size());

            if (problem != null)
            {
                readsAs.setText(I18n.f("route.ui.frameFormulaIsWrong", problem));
                return;
            }

            readsAs.setText(I18n.f("route.ui.frameReadsAs", inWords(formula, rows)));
            return;
        }

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

    // Which column is which, for the three that are pressed rather than typed in.  Named because
    // "column 8" in a click handler is a number nobody can check against the model that produced it.
    private static final int UP = 0;
    private static final int DOWN = 1;
    private static final int DELETE = 9;

    /** The conditions table is narrower, so its trash sits in a different column. */
    private static final int CONDITION_DELETE = 5;

    // What a cell holds when it is one of those.  Values rather than icons, so the model stays a model
    // and the renderer decides what a mark looks like.
    private static final String MOVE_UP = "up";
    private static final String MOVE_DOWN = "down";
    private static final String DELETE_ROW = "delete";
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
        final int addOn)
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
                    || DELETE_ROW.equals(value) || ADD_HERE.equals(value);

                // Only a MARK is drawn as one.  This renderer also covers the column the plus sits
                // under - which is the position column - and blanking every cell in it took the row
                // numbers with it: a table numbered one to four showed four empty cells, and the
                // numbers are the thing that makes "row 4 cannot be saved" mean anything.
                JLabel out = (JLabel) super.getTableCellRendererComponent(which,
                    isMark ? "" : value, selected, false, row, column);

                out.setHorizontalAlignment(JLabel.CENTER);

                if (MOVE_UP.equals(value)) out.setIcon(RowIcons.arrow(mark, true));
                else if (MOVE_DOWN.equals(value)) out.setIcon(RowIcons.arrow(mark, false));
                else if (DELETE_ROW.equals(value)) out.setIcon(RowIcons.trash(mark));
                else if (ADD_HERE.equals(value)) out.setIcon(RowIcons.plus(mark));
                else out.setIcon(null);

                return out;
            }
        };

        for (int column : new int[]{up, down, delete, addOn})
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

                boolean live = row >= 0 && column >= 0
                    && !"".equals(String.valueOf(table.getValueAt(row, column)))
                    && (column == up || column == down || column == delete
                        || (row >= rowsOf(table) && column == addOn));

                table.setCursor(java.awt.Cursor.getPredefinedCursor(
                    live ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
            }
        });
    }

    private int rowsOf(JTable table)
    {
        return table == commands ? commands.rows.size() : conditions.rows.size();
    }

    private void moveRow(JTable table, int row, int by)
    {
        if (table == commands) commands.shift(row, by);
    }

    private void deleteRow(JTable table, int row)
    {
        if (table == commands) commands.removeAt(row);
        else conditions.removeAt(row);
    }

    private void addTo(JTable table)
    {
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

        nameField.setEditable(false);
        s88Field.setEditable(false);
        triggerBox.setEnabled(false);
        enabledBox.setEnabled(false);

        captureBox.setEnabled(false);
        captureTarget.setEnabled(false);

        commands.setEnabled(false);
        conditions.setEnabled(false);

        formulaView.setEnabled(false);

        for (java.awt.Component block : formulaView.getComponents()) block.setEnabled(false);

        // The marks in the rows go with it.  A trash can that does nothing is worse than no trash can:
        // it says the row can be deleted and then declines, which reads as a fault rather than a rule.
        conditionsEditable = false;

        for (java.awt.Component pill : termPills.getComponents()) pill.setEnabled(false);

        if (saveButton != null) saveButton.setEnabled(false);

        readsAs.setText(I18n.t("route.ui.frameLockedExplains"));
    }

    /**
     * The terms the formula is written over, in the order their letters follow.
     *
     * @return the commands, by row
     */
    private java.util.List<RouteCommand> termsFromRows()
    {
        java.util.List<RouteCommand> out = new ArrayList<>();

        for (ConditionRows.Row row : conditions.rows) out.add(row.getCommand());

        return out;
    }

    /**
     * A formula with each letter swapped for what it stands for.
     *
     * The letters are taken whole - a run of them is one handle - so this cannot turn the "a" of a
     * term's description into a term of its own.
     *
     * @param formula what was typed
     * @param rows the terms
     * @return the same shape, in words
     */
    private String inWords(String formula, List<ConditionRows.Row> rows)
    {
        StringBuilder out = new StringBuilder();

        int at = 0;

        while (at < formula.length())
        {
            char one = formula.charAt(at);

            if (!Character.isLetter(one))
            {
                out.append(one);
                at++;

                continue;
            }

            int start = at;

            while (at < formula.length() && Character.isLetter(formula.charAt(at))) at++;

            String word = formula.substring(start, at);

            if ("and".equalsIgnoreCase(word) || "or".equalsIgnoreCase(word))
            {
                out.append(word.toLowerCase());

                continue;
            }

            int index = indexOfLetter(word);

            out.append(index >= 0 && index < rows.size()
                ? shortly(rows.get(index).getCommand()) : word);
        }

        return out.toString().trim();
    }

    /**
     * The position a handle names, or -1 when it is not one.
     */
    private static int indexOfLetter(String letter)
    {
        int out = 0;

        for (int c = 0; c < letter.length(); c++)
        {
            char one = Character.toUpperCase(letter.charAt(c));

            if (one < 'A' || one > 'Z') return -1;

            out = out * 26 + (one - 'A' + 1);
        }

        return out - 1;
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
            // Not abs().  A typed minus sign was silently turned into the positive address, so a
            // route triggered off a sensor the user never named - and there is no way to tell from
            // the saved route that it happened.  Refusing says which cell is wrong; coercing does not.
            s88 = Integer.parseInt(s88Field.getText().trim());

            if (s88 < 0) throw new NumberFormatException("negative");
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameS88NotANumber"));
            return;
        }

        // Automatic with no sensor is a route that can never fire by itself: the sensor IS the thing
        // that fires it.  Saved, it would sit in the list marked automatic and do nothing, which is
        // the quietest way for a route to be wrong.
        if (enabledBox.isSelected() && s88 == 0)
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameAutomaticNeedsSensor"));
            return;
        }

        List<RouteCommand> built;

        try
        {
            built = commandsAsSaved();
        }
        catch (IllegalArgumentException e)
        {
            // Already carries its row number from commandsAsSaved
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
            return;
        }

        if (built.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameNeedsACommand"));
            return;
        }

        NodeExpression expression;

        try
        {
            expression = org.traincontrol.base.ConditionFormula.parse(
                formula, termsFromRows());
        }
        catch (IllegalArgumentException e)
        {
            // Refused rather than saved as something else.  A condition that does not read is a route
            // that exists, looks right in the list, and never fires - which is the failure this whole
            // editor was built to stop, and the one nobody ever debugs because nothing is wrong on
            // screen.
            JOptionPane.showMessageDialog(this,
                I18n.f("route.ui.frameFormulaIsWrong", String.valueOf(e.getMessage())));
            return;
        }

        // Terms with nothing to combine them are no condition at all, and saying so beats saving a
        // route whose conditions are listed and never consulted
        if (expression == null && !conditions.rows.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.frameFormulaNeeded"));
            return;
        }

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
                return 10;
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
                    return column == 2 ? ADD_HERE : "";
                }

                Entry entry = rows.get(row);

                if (column == UP) return row > 0 ? MOVE_UP : "";
                if (column == DOWN) return row < rows.size() - 1 ? MOVE_DOWN : "";
                if (column == DELETE) return DELETE_ROW;

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
                // The adding row, and the three columns that are pressed rather than typed in
                if (row >= rows.size()) return false;

                if (column == UP || column == DOWN || column == DELETE || column == 2) return false;

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

            TableColumn positionColumn = getColumnModel().getColumn(2);
            positionColumn.setPreferredWidth(30);
            positionColumn.setMaxWidth(40);

            TableColumn kindColumn = getColumnModel().getColumn(3);
            kindColumn.setPreferredWidth(170);

            getColumnModel().getColumn(5).setPreferredWidth(60);
            getColumnModel().getColumn(7).setPreferredWidth(70);
            getColumnModel().getColumn(8).setPreferredWidth(70);

            actOnRowMarks(this, DELETE, UP, DOWN, 2);

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
            if (column == 4)
            {
                CommandRow.Kind kind = rows.get(row).getRow().getKind();

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
                // The extra row is where a term is added, in the table rather than beside it
                return rows.size() + 1;
            }

            @Override
            public int getColumnCount()
            {
                return 6;
            }

            @Override
            public String getColumnName(int column)
            {
                switch (column)
                {
                    case 0: return I18n.t("route.ui.frameColTerm");
                    case 1: return I18n.t("route.ui.frameColKind");
                    case 2: return I18n.t("route.ui.frameColTarget");
                    case 3: return I18n.t("route.ui.frameColSetting");
                    case 4: return I18n.t("route.ui.frameColProtocol");
                    default: return "";
                }
            }

            @Override
            public Object getValueAt(int row, int column)
            {
                if (row >= rows.size())
                {
                    return column == 0 ? ADD_HERE : "";
                }

                if (column == CONDITION_DELETE) return DELETE_ROW;

                ConditionRows.Row at = rows.get(row);

                // The handle this term is known by in the formula underneath.  It replaced a column
                // holding the operator that joined this row to the next, which is how conditions used
                // to combine: left to right, one chain, no brackets possible.  The operators live in
                // the formula now, where brackets can be written.
                if (column == 0) return org.traincontrol.base.ConditionFormula.letterFor(row);

                CommandRow term = CommandRow.of(at.getCommand());

                // A term with no controls is shown whole and left alone, as the commands are
                if (term == null) return column == 1 ? String.valueOf(at.getCommand()) : "";

                if (column == 1) return CommandRow.labelFor(term.getKind());
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
                if (row >= rows.size()) return false;

                if (!conditionsEditable) return false;

                // The letter is what the row IS, and the trash is pressed rather than typed in
                if (column == 0 || column == CONDITION_DELETE) return false;

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
                if (row >= rows.size()) return;

                ConditionRows.Row at = rows.get(row);

                String text = value == null ? "" : value.toString();

                if (column == 0) return;

                CommandRow term = CommandRow.of(at.getCommand());

                if (term == null) return;

                CommandRow edited;

                if (column == 1)
                {
                    CommandRow.Kind became = CommandRow.kindFor(text);

                    // A setting that means nothing to the new kind is REPLACED, not carried over.
                    //
                    // The vocabularies are disjoint - a feedback is on/off, an accessory is
                    // turn/straight - so carrying the old word made the rebuild below throw, and the
                    // rebuild failing silently reverted the edit.  The kind dropdown snapped back with
                    // no message, in both directions, and since a new row is always a feedback term an
                    // accessory condition could not be built by hand at all.  Which made the protocol
                    // column added for exactly that case unreachable.
                    edited = new CommandRow(became, term.getTarget(),
                        CommandRow.defaultSettingFor(became), term.getProtocol(), term.getDelay());
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

            getColumnModel().getColumn(0).setPreferredWidth(60);

            JComboBox<String> kinds = new JComboBox<>();

            // Only the kinds a condition can actually be.  The list offered all seven, but Route
            // evaluates accessory and feedback terms and nothing else - so picking a speed or a
            // function as a condition gave a term that is permanently false, saved without complaint,
            // and a route that silently stopped firing.
            for (CommandRow.Kind kind : CommandRow.Kind.values())
            {
                if (CommandRow.canBeACondition(kind)) kinds.addItem(CommandRow.labelFor(kind));
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

            narrow(this, CONDITION_DELETE);

            TableColumn termColumn = getColumnModel().getColumn(0);
            termColumn.setPreferredWidth(50);
            termColumn.setMaxWidth(70);

            actOnRowMarks(this, CONDITION_DELETE, -1, -1, 0);
            getColumnModel().getColumn(4).setPreferredWidth(70);
        }

        void addRow()
        {
            // A condition is a feedback term by default, which is what nearly every one is
            rows.add(new ConditionRows.Row(ConditionRows.Joiner.AND,
                RouteCommand.RouteCommandFeedback(1, true)));

            model.fireTableDataChanged();
            updateReadsAs();

            // Into the formula too.  A term nothing refers to takes no part in whether the route
            // fires, so a table of terms with an empty formula is a condition that looks written and
            // does nothing - which is exactly what Adam met: an empty box and no way to tell what it
            // wanted.  ANDed on, because adding a second requirement is what adding a row means.
            appendToFormula(org.traincontrol.base.ConditionFormula.letterFor(rows.size() - 1));
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

            // The letters are positional, so removing a row renames every term after it.  The pills
            // and the reading have to be rebuilt or they name rows that have moved.
            refreshTermPills();
            updateReadsAs();
            updateReadsAs();
        }

        void fireTableDataChanged()
        {
            model.fireTableDataChanged();
        }
    }
}
