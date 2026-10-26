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
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.Route;
import org.traincontrol.base.ThreeWaySwitch;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.DetailsArea;
import org.traincontrol.util.I18n;

/**
 * Edits a route as two tables of dropdowns (commands; conditions) instead of text lines that have to be right.
 * The conversions live in CommandRow and ConditionOutline.  A command of a kind with no controls yet, and a
 * condition with a real bracket in it, are kept exactly as found and shown read-only: rendering them as something
 * that nearly means the same would rewrite somebody's railway on Save.
 */
public class RouteEditorFrame extends JFrame
{
    // A static block, not the constructor: JFrame's constructor creates the root pane before any constructor body
    // here, so a look and feel installed later leaves a cold-built window (a test's) with Metal's title bar instead
    // of FlatLaf's.  Headless-guarded: a static initializer that throws leaves the class unusable.
    static
    {
        if (!java.awt.GraphicsEnvironment.isHeadless()) TrainControlUI.installLookAndFeel();
    }

    private final TrainControlUI parent;

    /** The route being edited, or empty when this is a new one. */
    private final String originalName;

    /**
     * What the route looked like in the database when this window opened, as one string.  Save compares it with the
     * route as it stands now, since Enable/Disable, a delete, an import or another editor can change it underneath.
     * Null for a new route.
     */
    private final String loadedFromDatabase;

    /**
     * The id of the route this window opened on, or null for a new one: the id is what the route is, a name only what
     * it is called.
     */
    private final Integer loadedId;

    private final JTextField nameField = new JTextField(24);
    private final JTextField s88Field = AutonomyEditorPanel.digitsOnlyField(null, 6, 0);
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

    /** Whether this route came from the Central Station, and so may be read but not changed. */
    private boolean locked = false;

    /**
     * @return whether this route belongs to the Central Station and is shown read-only
     */
    public boolean isLocked()
    {
        return this.locked;
    }


    /**
     * Whether this window will let the route be changed.  Asked before a mark is drawn and again before it acts:
     * the marks are painted values, and their click listener ignores a disabled table.
     *
     * @return true when the route is this window's to change
     */
    public boolean isEditable()
    {
        return !locked;
    }

    /** Held so a Central Station route can gray it. */
    private JButton saveButton;

    /** Held so a Central Station route can keep it: a locked route can still be tested. */
    private JButton testButton;

    /** While ticked, an accessory thrown on the layout adds itself to the command list. */
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
        this(parent, routeName,
            parent == null || routeName == null ? null : parent.getModel().getRoute(routeName));
    }

    /**
     * The same window, handed the route rather than sent to look it up, so its read-only behavior can be exercised
     * without a running control station.
     *
     * @param parent the main window, or null
     * @param routeName what to call it in the title
     * @param route the route to show, or null for a new one
     */
    public RouteEditorFrame(TrainControlUI parent, String routeName, Route route)
    {
        this.parent = parent;
        this.originalName = routeName == null ? "" : routeName;

        // WHAT THE DATABASE SAID WHEN THIS WINDOW OPENED, so Save can tell whether it still says it.
        this.loadedFromDatabase = signatureOf(route);

        // AND WHICH ROUTE IT WAS, which the name alone cannot say once somebody renames it.
        this.loadedId = route == null ? null : route.getId();

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

        TrainControlUI.applyWindowIcon(this);

        // In words, not constants: CLEAR_THEN_OCCUPIED means nothing to somebody who has not read the code.
        for (Route.s88Triggers trigger : Route.s88Triggers.values())
        {
            triggerBox.addItem(triggerLabel(trigger));
        }

        setContentPane(build());

        load(route);

        // After load(), which is what discovers whether the route is the station's
        if (locked) becomeReadOnly();

        // What closeIfThrowingNothingAway compares against.
        loadedSignature = stateSignature();

        // Above the main window if it is, as every child window is: capture is ticked here and the switches are thrown
        // there.  Null-checked: test classes build this window with a null parent.
        if (parent != null) setAlwaysOnTop(parent.isAlwaysOnTop());

        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Fills in a name for a route that does not exist yet.  Not the constructor's routeName: a non-empty originalName
     * makes Save an edit of an existing route, not an add.  The signature is re-taken so that closing straight away
     * still throws nothing away.
     *
     * @param name the name to offer
     */
    public void proposeName(String name)
    {
        nameField.setText(name);

        loadedSignature = stateSignature();
    }

    private JPanel build()
    {
        JPanel content = new JPanel(new BorderLayout(8, 8));

        // White to the edges, so the panels read as one window.
        content.setBackground(java.awt.Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(header(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 8));

        middle.setBackground(java.awt.Color.WHITE);

        JPanel commandSection = section(I18n.t("route.ui.frameCommands"), commands);

        captureBox.setToolTipText(I18n.t("route.ui.tooltipCapture"));

        // Not in the tab order (OB-271, from MT-446): clicking one of these mid-typing would take the keyboard, so the next Enter
        // would press it.  Save stays focusable: it is the button Enter should reach.
        captureBox.setFocusable(false);
        captureTarget.setFocusable(false);

        captureTarget.addItem(I18n.t("route.ui.frameCaptureIntoCommands"));
        captureTarget.addItem(I18n.t("route.ui.frameCaptureIntoConditions"));
        captureTarget.setToolTipText(I18n.t("route.ui.tooltipCaptureTarget"));

        buttonsOf(commandSection).add(captureBox);
        buttonsOf(commandSection).add(captureTarget);

        middle.add(commandSection);

        JPanel conditionSection = section(I18n.t("route.ui.frameConditions"), conditions);

        readsAs.setFont(new java.awt.Font("Segoe UI", 0, 14));
        readsAs.setForeground(new java.awt.Color(90, 90, 90));

        readsAs.setVerticalAlignment(JLabel.TOP);

        // The selected condition is bold in the reading, so an edit shows where it now sits in the sentence (FR-147).
        conditions.getSelectionModel().addListSelectionListener(e ->
        {
            if (!locked && !e.getValueIsAdjusting()) updateReadsAs();
        });

        // Answers whether the route would fire right now, which a condition list cannot show.
        testButton = button(I18n.t("route.ui.testCondition"), this::testAgainstTheRailway);

        testButton.setToolTipText(I18n.t("route.ui.tooltipTestConditions"));

        // Out of the tab order with the capture controls above - see the note there (OB-271).
        testButton.setFocusable(false);

        buttonsOf(conditionSection).add(testButton);

        // Where the route is, drawn on the railway: yellow for what it commands, orange for what it checks first.
        highlightButton = button(I18n.t("route.ui.highlightOnDiagram"), this::highlightOnDiagram);

        // As the test button above (OB-271).
        highlightButton.setFocusable(false);

        highlightButton.setToolTipText(
            AutonomyEditorPanel.wrapped(I18n.t("route.ui.tooltipHighlightOnDiagram")));

        buttonsOf(conditionSection).add(highlightButton);

        // Help beside Test: most of it is about the conditions.
        buttonsOf(conditionSection).add(button(I18n.t("ui.help"), this::showHelp));

        // Stacked in the one slot at the bottom: a second component at SOUTH replaces the first, not sits under it.
        JPanel below = new JPanel(new BorderLayout(4, 0));

        below.setBackground(java.awt.Color.WHITE);

        // The reading goes above the table, as a subtitle restating the whole condition (MT-108).
        conditionSection.add(readsAs, BorderLayout.NORTH);

        below.add(buttonsOf(conditionSection), BorderLayout.SOUTH);

        conditionSection.add(below, BorderLayout.SOUTH);

        middle.add(conditionSection);

        content.add(middle, BorderLayout.CENTER);

        // Save in the bottom right corner, Cancel just left of it (OB-018).  Cancel is added first: a right-aligned
        // FlowLayout lays out left to right, so the last one added sits in the corner.
        JPanel buttons = new JPanel(new BorderLayout());

        buttons.setBackground(java.awt.Color.WHITE);

        // No rule above them (MT-108): they sit at the opposite end from the buttons that act on a row.
        buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        right.setBackground(java.awt.Color.WHITE);

        // Through the question Escape asks, so Cancel does not skip the discard prompt.
        right.add(button(I18n.t("route.ui.frameCancel"), this::closeIfThrowingNothingAway));

        saveButton = button(I18n.t("route.ui.frameSave"), this::onSave);

        right.add(saveButton);

        buttons.add(right, BorderLayout.EAST);

        content.add(buttons, BorderLayout.SOUTH);

        // Escape is Cancel; a cell being edited gets its Escape first, as WHEN_IN_FOCUSED_WINDOW is checked last.
        getRootPane().registerKeyboardAction(e -> closeIfThrowingNothingAway(),
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        return content;
    }

    /**
     * Everything a route says about itself, as one string, tolerant like `stateSignature`.
     *
     * @param route the route, or null
     * @return its signature, or null when there is no such route
     */
    private static String signatureOf(org.traincontrol.base.Route route)
    {
        if (route == null) return null;

        StringBuilder out = new StringBuilder();

        out.append(route.getName()).append('\u0001')
           .append(route.getS88()).append('\u0001')
           .append(String.valueOf(route.getTriggerType())).append('\u0001')
           .append(route.isEnabled()).append('\u0002');

        for (org.traincontrol.base.RouteCommand command : route.getRoute())
        {
            out.append(String.valueOf(command)).append('\u0003');
        }

        // Conditions by content: NodeExpression has no toString, and Route.locomotiveRenamed rewrites them in place.
        for (org.traincontrol.base.RouteCommand condition
            : org.traincontrol.base.NodeExpression.toList(route.getConditions()))
        {
            out.append(String.valueOf(condition)).append('\u0004');
        }

        return out.toString();
    }

    /**
     * Whether the route this window is editing has changed in the database since it opened.  Renamed is not gone:
     * looking up by name alone would offer save as new, leaving a second route with a fresh id that no route tile or
     * autonomy selection follows.
     *
     * @return "gone", "renamed", "changed", or null
     */
    public String howTheRouteMoved()
    {
        if (originalName == null || originalName.isEmpty() || parent == null || parent.getModel() == null)
        {
            return null;
        }

        org.traincontrol.base.Route now = parent.getModel().getRoute(originalName);

        if (now == null) return nameNowHeldById() != null ? "renamed" : "gone";

        // The name may be held by another route now: saving onto it would overwrite a stranger.
        if (loadedId != null && now.getId() != loadedId)
        {
            return nameNowHeldById() != null ? "renamed" : "gone";
        }

        return signatureOf(now).equals(loadedFromDatabase) ? null : "changed";
    }

    /**
     * Which name a save onto a renamed route should use.  The name field still holds the opening name unless the
     * operator typed in it; passing that would rename the route back over somebody else's rename.
     *
     * @param typed what the name field holds
     * @param nowCalled what the route is called now, by whoever renamed it
     * @return the name to save it under
     */
    public String nameToSaveAs(String typed, String nowCalled)
    {
        if (nowCalled == null) return typed;

        return typed != null && typed.equals(originalName) ? nowCalled : typed;
    }

    /**
     * What the route this window opened on is called now, or null if no route carries that id (found by walking the
     * list; the view interface has no lookup by id).
     *
     * @return the current name, or null
     */
    public String nameNowHeldById()
    {
        if (loadedId == null || parent == null || parent.getModel() == null) return null;

        for (String name : parent.getModel().getRouteList())
        {
            org.traincontrol.base.Route candidate = parent.getModel().getRoute(name);

            if (candidate != null && candidate.getId() == loadedId) return candidate.getName();
        }

        return null;
    }

    /**
     * Whether closing this window now would throw away work: the question `closeIfThrowingNothingAway` asks, without
     * a dialog.  Compared against what was loaded, not a flag, since six paths change things here.
     *
     * @return true when something typed here is not saved
     */
    public boolean hasUnsavedWork()
    {
        return !locked && !stateSignature().equals(loadedSignature);
    }

    /** What a test answers the discard question with, or null to ask the operator (the dialog is modal). */
    public static Boolean discardAnswerForTest;

    /**
     * Asked by the application's exit, which is a door out of this window too (as the layout editor's, OB-070):
     * disposing with the process would take unsaved typing silently.  Answering No leaves the application open.
     *
     * @return true when the exit may carry on
     */
    public boolean maySettleBeforeExit()
    {
        if (!hasUnsavedWork()) return true;

        // Out of the taskbar before it asks (MT-703): toFront does not restore a minimized window.
        if ((getExtendedState() & java.awt.Frame.ICONIFIED) != 0)
        {
            setExtendedState(getExtendedState() & ~java.awt.Frame.ICONIFIED);
        }

        toFront();

        if (discardAnswerForTest != null) return discardAnswerForTest;

        return JOptionPane.showOptionDialog(this,
            I18n.t("route.ui.confirmDiscardChanges"),
            I18n.t("route.ui.titleDiscardChanges"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) == 0;
    }
    /**
     * Closes, asking first only if there is something to lose, compared against what was loaded rather than a flag:
     * a flag must be set by every path that changes anything, and a prompt that does not appear teaches that closing
     * is safe.
     */
    private void closeIfThrowingNothingAway()
    {
        if (!hasUnsavedWork())
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
     * Everything the window is currently saying, as one string.  Tolerant: it is compared, never parsed, so a
     * half-typed row still contributes its text.
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
     * Whether any route in the database was imported from the Central Station.  Without one, a new route's id cannot
     * collide with the station's, which is all the sync after a save checks.
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

        // Grayed, not hidden: hiding made the window rearrange itself as the tick was pressed.
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
     * The trigger behind a line of words, matched against the labels so the two directions cannot disagree; falls
     * back to the arrival trigger, which every older route had.
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
        // No leading gap: FlowLayout puts its hgap before the first component too, so struts separate the fields.
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));

        row.setBackground(java.awt.Color.WHITE);

        row.add(label(I18n.t("route.ui.frameName")));
        row.add(javax.swing.Box.createHorizontalStrut(8));
        row.add(nameField);
        row.add(javax.swing.Box.createHorizontalStrut(8));

        // The tick first, and the sensor only when ticked: a sensor box beside an unticked box asks a question with
        // no bearing yet.
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

        // AND THE CONDITIONS SAY WHETHER THEY ARE ASKED AT ALL
        enabledBox.addActionListener(e -> updateReadsAs());

        showSensorIfAutomatic();

        return row;
    }

    /**
     * A titled table with the buttons that act on it.
     */
    private JPanel section(String title, JTable table)
    {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        // A panel with a line round it, like the rest of the application (docs/UI-standards.md).
        panel.setBackground(java.awt.Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204), 1),
            BorderFactory.createEmptyBorder(6, 8, 8, 8)));

        // A heading, not a nested frame: styled as GraphEdgeEdit's, indented like the panel's contents.
        JLabel heading = new JLabel(title);

        heading.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13));
        heading.setForeground(HEADING_BLUE);
        heading.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        panel.add(heading, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, 180));

        // White, with a single line round it (docs/UI-standards.md).
        table.setBackground(java.awt.Color.WHITE);
        scroll.setBorder(BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204), 1));

        panel.add(scroll, BorderLayout.CENTER);

        // No button row: add, remove and reorder are marks in the rows, so nothing depends on which row is selected.
        JPanel below = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        below.setOpaque(false);
        below.setBackground(java.awt.Color.WHITE);

        panel.add(below, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * A cell editor that will only accept digits, refused as typed rather than at Save.
     *
     * @return the editor
     */
    private static javax.swing.table.TableCellEditor digitsOnly()
    {
        return new DefaultCellEditor(AutonomyEditorPanel.digitsOnlyField(null, 0, 0));
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
     * A cell editor offering setting words: each is shown in the user's language and committed as the stored word.
     *
     * @param words the stored words
     * @return the editor
     */
    private static javax.swing.table.TableCellEditor chooseSetting(String[] words)
    {
        JComboBox<String> box = new JComboBox<>();

        for (String word : words) box.addItem(word);

        box.setRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                int index, boolean selected, boolean focused)
            {
                return super.getListCellRendererComponent(list,
                    value instanceof String ? settingLabel((String) value) : value, index, selected, focused);
            }
        });

        return new DefaultCellEditor(box);
    }

    /** The setting words a route stores, each with a `route.ui.setting.<word>` text in every bundle. */
    private static final java.util.Set<String> SETTING_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "straight", "turn", "green", "red", "on", "off", "forward", "backward", "left", "right"));

    /**
     * A stored setting word as the user reads it.
     *
     * @param word the stored word
     * @return its text in the user's language, or the word itself when it is not a setting word (a number, a name)
     */
    static String settingLabel(String word)
    {
        return word != null && SETTING_WORDS.contains(word) ? I18n.t("route.ui.setting." + word) : word;
    }

    /**
     * The names to offer in a locomotive or route cell: the database's, and the name already in the cell.  A combo
     * box handed a value it lacks keeps whatever was selected, so a route naming a since-deleted locomotive would be
     * rewritten to the first name by a click, and the Save gate would be satisfied.
     *
     * @param names the database's names, or null
     * @param has what the cell holds, or null
     * @return the names to offer
     */
    private static String[] withTheCellsOwn(java.util.List<String> names, String has)
    {
        java.util.List<String> out = names == null ? new ArrayList<>() : new ArrayList<>(names);

        // As it stands, spaces and all: a trimmed offer is not the value the combo box was handed.
        if (has != null && !has.trim().isEmpty() && !out.contains(has)) out.add(has);

        return out.toArray(new String[0]);
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
     * The on-or-off half.  Defaults to off: a function command with no state is not a command.
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
            // The kind says which pair: a signal is not offered "turn" and "straight".
            case ACCESSORY: return new String[]{"straight", "turn"};
            case SIGNAL: return new String[]{"green", "red"};

            // Left, straight, right, as on the diagram; the commands each becomes are ThreeWaySwitch's business alone.
            case THREE_WAY: return ThreeWaySwitch.words();

            case FEEDBACK: return new String[]{"off", "on"};
            case LOCOMOTIVE_DIRECTION: return new String[]{"forward", "backward"};

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
     * A stored command as a row, with an accessory named as whatever is at that address: a route file records one
     * accessory command for switch and signal, so of() can only answer ACCESSORY.
     */
    private CommandRow asShown(CommandRow row)
    {
        // THREE_WAY as well as ACCESSORY (MT-005): a three-way row given a signal's address would stay a pair of
        // commands aimed at something with no pair.  The reverse is impossible: the model has no isThreeWay, so an
        // ACCESSORY row becomes THREE_WAY only by being chosen.
        if (row == null
            || (row.getKind() != CommandRow.Kind.ACCESSORY
                && row.getKind() != CommandRow.Kind.THREE_WAY)) return row;

        if (kindAtAddress(row.getTarget(), row.getProtocol(), row.getKind())
            != CommandRow.Kind.SIGNAL)
        {
            return row;
        }

        // And the setting with it: a signal row still carrying "turn" would hold a value its dropdown lacks, which one
        // click in and out commits as the first entry.
        String said = row.getSetting();

        if ("turn".equalsIgnoreCase(said)) said = "red";
        else if ("straight".equalsIgnoreCase(said)) said = "green";
        else if (!"red".equalsIgnoreCase(said) && !"green".equalsIgnoreCase(said))
        {
        // Any word a THREE_WAY row can carry becomes red (MT-089: a combo lacking its value commits its first entry);
        // red because a wrong guess that stops a train cannot cause a collision.
            said = "red";
        }

        return new CommandRow(CommandRow.Kind.SIGNAL, row.getTarget(), said,
            row.getProtocol(), row.getDelay());
    }

    /**
     * The row of buttons under a section's table, asked of the layout rather than counted by index, which breaks when
     * a heading is added above the table.
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

        // The small labels beside a field are plain 14 in the heading blue, so they do not compete with headings.
        out.setFont(new java.awt.Font("Segoe UI", 0, 14));
        out.setForeground(HEADING_BLUE);

        return out;
    }

    /** The blue this application uses for headings and for the labels beside fields. */
    private static final java.awt.Color HEADING_BLUE = new java.awt.Color(0, 0, 155);

    /** For a line that says something the rest of its level does not agree with. */
    private static final java.awt.Color WRONG = new java.awt.Color(190, 30, 30);

    /** Says how the two halves of this window fit together. */
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

    /** Fills the frame from a route, or leaves it empty for a new one. */
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

        // setSelected fires no ActionEvent, so an existing automatic route would otherwise open with its sensor dead.
        showSensorIfAutomatic();

        List<RouteCommand> stored = route.getRoute();

        for (int at = 0; at < stored.size(); at++)
        {
            // Two lines that are one point come in as one row: read, and only when the pair matches a shape exactly,
            // since a guess would write a different pair back.
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

            // A kind with no controls becomes a read-only row, not a remembered index: deleting an earlier row would
            // move the editable rows under an index.  One list, one order.
            Entry loaded = Entry.of(stored.get(at));

            commands.rows.add(loaded.isEditable()
                ? Entry.of(asShown(loaded.getRow())) : loaded);
        }

        locked = route.isLocked();

        conditionsAsFound = route.getConditions();

        // The condition as an outline: one row per term, indented where it was bracketed.
        {
            conditions.rows.addAll(ConditionOutline.of(conditionsAsFound));
        }

        commands.fireTableDataChanged();
        conditions.fireTableDataChanged();
        updateReadsAs();
    }

    /** How many commands the list holds, so a test can see that a capture arrived. */
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
                // Which row, not just what is wrong with it: a long route has to be hunted through.
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

    /** Whether a thrown accessory should write itself into the command list. */
    public boolean isCapturing()
    {
        return captureBox.isSelected();
    }

    /**
     * Adds a command that was captured from the layout: the accessory's own setting line, parsed as the old editor
     * parsed it.  A line this frame has no controls for is kept rather than dropped.
     *
     * @param command the captured line
     */
    public void appendCommand(String command)
    {
        // Capture is grayed on a locked route, but this is a public way into the list, and the rule belongs with it.
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

                // Required as well as whatever is there, as capturing several things in a row means; the word goes in
                // as its own line.
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

            // The same accessory thrown twice leaves ONE row, with the value it ended on (a turnout thrown the wrong
            // way and back).  Through the lines, which RouteCapture's filter reads.  Caught on its own: a row this
            // could not express must not stop the table being told about the row just added.
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
     * Collapses repeated captures of the same thing down to the last one.  Kept commands are left where they are:
     * rewriting them through a text round trip is the one way to lose them.
     */
    private void settleCapturedRows() throws Exception
    {
        StringBuilder text = new StringBuilder();

        for (Entry entry : commands.rows)
        {
            if (!entry.isEditable()) return;

            // A three-way row stands for a pair of commands and refuses to answer as one; the rows on screen are
            // better.
            if (entry.getRow() != null && entry.getRow().getKind() == CommandRow.Kind.THREE_WAY) return;

            text.append(entry.toCommand().toLine(null));
        }

        String settled = org.traincontrol.base.RouteCapture.filterConfigCommands(text.toString());

        java.util.List<Entry> rebuilt = new ArrayList<>();

        for (String line : settled.split("\n"))
        {
            if (line.trim().isEmpty()) continue;

            RouteCommand one = RouteCommand.fromLine(line, false);

            // A line the parser will not take back: the rows already on screen are the better answer.
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
     * Puts text in the sensor field, for a test about what the editor accepts (OB-178).
     *
     * @param text what the user typed, empty for a cleared field
     */
    public void setS88TextForTest(String text)
    {
        s88Field.setText(text);
    }

    /**
     * Whether auto-fire is ticked, for the same test - ticked as a click ticks it, so what listens to the box hears it.
     *
     * @param on whether the route fires itself
     */
    public void setAutoFireForTest(boolean on)
    {
        if (enabledBox.isSelected() != on) enabledBox.doClick();
    }

    /**
     * Everything this editor would refuse to save, as message keys resolved to text (OB-178).
     *
     * @return the problems, empty when the route would save
     */
    public java.util.List<String> problemsForTest()
    {
        return everythingWrong();
    }
    /**
     * Sets a cell of the command table, as typing in it would.  These setters go through the table model, where what
     * a row keeps when its kind changes is decided.
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
     * The function NUMBER of a row, its own column (MT-464): `setCommandSettingForTest` reaches only the on-or-off
     * half.
     *
     * @param row which row
     * @param number the function number, as typed
     */
    public void setCommandFunctionNumberForTest(int row, String number)
    {
        commands.getModel().setValueAt(number, row, 5);
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
     * @return how many rows the command table holds, the plus not counted
     */
    public int commandRowCountForTest()
    {
        return commands.rows.size();
    }

    /**
     * The three marks at the end of a command row, driven the way a click drives them (MT-029).  The marks are
     * painted values, so there is no component to click; this calls the methods the cell editor calls.
     *
     * @param row which row
     * @param mark MOVE_UP, MOVE_DOWN, DELETE_ROW or COPY_ROW
     */
    public void clickCommandMarkForTest(int row, String mark)
    {
        if (MOVE_UP.equals(mark)) moveRow(commands, row, -1);
        else if (MOVE_DOWN.equals(mark)) moveRow(commands, row, 1);
        else if (DELETE_ROW.equals(mark)) deleteRow(commands, row);
        else if (COPY_ROW.equals(mark)) duplicateRow(commands, row);
        else throw new IllegalArgumentException("no such mark: " + mark);
    }

    /**
     * For tests: the condition outline as the table holds it.
     *
     * @return the live list, so a caller can read what an edit left behind
     */
    public java.util.List<ConditionOutline.Row> conditionRowsForTest()
    {
        return conditions.rows;
    }

    /**
     * For tests: put an outline into the condition table, as loading a route does.
     *
     * @param rows the outline
     */
    public void setConditionRowsForTest(java.util.List<ConditionOutline.Row> rows)
    {
        conditions.rows.clear();
        conditions.rows.addAll(rows);
        conditions.fireTableDataChanged();
    }

    /**
     * For tests: delete one condition row, through the same door the trash can uses.
     *
     * @param row which row
     */
    public void deleteConditionForTest(int row)
    {
        conditions.removeAt(row);
    }

    /**
     * For tests (FR-068): move one condition row in or out, through the method the indent marks call.  The rule that
     * a row may be at most one level deeper than the row above lives in `ConditionTable.indent` alone.
     *
     * @param row which line - conditions and joining words alike, since both carry a depth
     * @param by 1 to indent, -1 to outdent
     */
    public void indentConditionForTest(int row, int by)
    {
        conditions.indent(row, by);
    }

    /**
     * The "Reads as" line exactly as the label holds it, markup included - for tests.
     *
     * @return the label's text
     */
    public String readsAsForTest()
    {
        return readsAs.getText();
    }

    /**
     * For tests: choose a kind in a condition line's Kind cell, as closing its drop-down does (through the model).
     *
     * @param line which line
     * @param kind what was chosen
     */
    public void setConditionKindForTest(int line, CommandRow.Kind kind)
    {
        conditions.getModel().setValueAt(CommandRow.labelFor(kind), line, 4);
    }

    /** For tests: adds an empty command row, the way the plus at the bottom of the table does. */
    public void addCommandRowForTest()
    {
        commands.addRow();
    }

    /**
     * For tests: leaves no row selected, since a selected row wears the look-and-feel's colors, not the table's ink.
     */
    public void clearCommandSelectionForTest()
    {
        commands.clearSelection();
    }

    /**
     * For tests: a command row's cell as the table paints it, color and tooltip included (OB-246).  Through the
     * renderer, since the claim is what the user sees.
     *
     * @param row which row
     * @param column which column
     * @return the rendered component
     */
    public java.awt.Component commandCellForTest(int row, int column)
    {
        return commands.prepareRenderer(commands.getCellRenderer(row, column), row, column);
    }

    /** @return the ink a row Save would refuse is drawn in */
    public static java.awt.Color refusedInkForTest()
    {
        return WRONG;
    }

    /**
     * For tests: a condition line's word-or-kind cell as the table paints it, icon and tooltip included.
     *
     * @param line which line
     * @return the rendered label
     */
    public JLabel conditionCellForTest(int line)
    {
        return conditionCellForTest(line, 4);
    }

    /**
     * For tests: any cell of a condition line as the table paints it, tooltip included (FR-141).
     *
     * @param line which line
     * @param column which column
     * @return the rendered label
     */
    public JLabel conditionCellForTest(int line, int column)
    {
        return (JLabel) conditions.prepareRenderer(conditions.getCellRenderer(line, column), line, column);
    }

    /** The color "Reads as" gives on, straight and green - the settings that let a train go. */
    public static final String READS_GO_COLOUR = "#1a7f37";

    /** The color "Reads as" gives off, turn and red. */
    public static final String READS_STOP_COLOUR = "#c62828";

    /** The marks, so a test names them the way the table does */
    public static String markMoveUp() { return MOVE_UP; }

    /** @return the move-down mark */
    public static String markMoveDown() { return MOVE_DOWN; }

    /** @return the delete mark */
    public static String markDelete() { return DELETE_ROW; }

    /** @return the duplicate mark */
    public static String markCopy() { return COPY_ROW; }

    /**
     * Whether the command list is showing its plus: a painted value, not a button, so there is no component to ask.
     *
     * @return true when there is a way to add a command
     */
    public boolean offersToAddCommands()
    {
        return ADD_HERE.equals(commands.getValueAt(commands.getRowCount() - 1, UP));
    }

    /** The same question of the condition list. */
    public boolean offersToAddConditions()
    {
        return ADD_HERE.equals(conditions.getValueAt(conditions.getRowCount() - 1, UP));
    }

    /** How many conditions the list holds, so a test can see that a capture arrived. */
    public int conditionCount()
    {
        // Conditions, not lines: the outline holds the joining words as lines of their own.
        int out = 0;

        for (ConditionOutline.Row row : conditions.rows)
        {
            if (!row.isJoiner()) out++;
        }

        return out;
    }

    /**
     * Writes out how the joins nest, in words, which a flat list cannot show: "a AND b OR c" is AND(a, OR(b, c)).
     */
    private void updateReadsAs()
    {
        String reading = readingOfTheConditions();

        // Checked only when the route fires itself; a route run by hand ignores its conditions.  No warning triangle,
        // which the font does not draw.
        boolean unasked = !enabledBox.isSelected()
            && (conditionsEditable ? conditionCount() > 0 : conditionsAsFound != null);

        if (!unasked)
        {
            readsAs.setText(reading);
            return;
        }

        String below = reading.startsWith("<html>") && reading.endsWith("</html>")
            ? reading.substring("<html>".length(), reading.length() - "</html>".length()) : marked(reading.trim());

        readsAs.setText("<html><span style='color:#C62828'>" + marked(I18n.t("route.ui.frameConditionsNeedAutomaticFire"))
            + "</span>" + (below.isEmpty() ? "" : "<br>" + below) + "</html>");
    }

    /**
     * Whether the conditions' warning is showing: there are conditions and Automatically Fire is off.
     *
     * @return true while it is shown
     */
    public boolean warnsTheConditionsAreUnaskedForTest()
    {
        return readsAs.getText().contains(I18n.t("route.ui.frameConditionsNeedAutomaticFire"));
    }

    /**
     * How the conditions read, in words, as `updateReadsAs` shows them under the warning when there is one.
     *
     * @return the text, markup or plain, or a blank
     */
    private String readingOfTheConditions()
    {
        if (!conditionsEditable)
        {
            String text = conditionsAsFound == null ? ""
                : NodeExpression.toTextRepresentation(conditionsAsFound,
                    parent == null ? null : parent.getModel());

            return I18n.f("route.ui.frameConditionsNotShown", text);
        }

        if (conditions.rows.size() < 2) return " ";

        // A run of rows joined by the same word is a group, and a change of word starts a new one.  Bold operators and
        // colored settings are markup, so everything the conditions carry is escaped on the way in.
        return "<html>" + I18n.f("route.ui.frameReadsAs",
            describe(ConditionOutline.toExpression(conditions.rows), conditions.chosenConditions())) + "</html>";
    }

    /**
     * An expression in words, for the reading under the conditions.
     *
     * @param node the expression
     * @param chosen the conditions selected in the table, drawn in bold (FR-147)
     * @return what it says
     */
    private String describe(NodeExpression node, java.util.Set<RouteCommand> chosen)
    {
        if (node == null) return "";

        if (node instanceof org.traincontrol.base.NodeRouteCommand)
        {
            RouteCommand command = ((org.traincontrol.base.NodeRouteCommand) node).getRouteCommand();

            return chosen.contains(command) ? "<b>" + shortly(command) + "</b>" : shortly(command);
        }

        if (node instanceof org.traincontrol.base.NodeAnd)
        {
            return describe(((org.traincontrol.base.NodeAnd) node).getLeft(), chosen)
                + " <b>" + marked(I18n.t("route.ui.joinAnd")) + "</b> "
                + describe(((org.traincontrol.base.NodeAnd) node).getRight(), chosen);
        }

        if (node instanceof org.traincontrol.base.NodeOr)
        {
            return describe(((org.traincontrol.base.NodeOr) node).getLeft(), chosen)
                + " <b>" + marked(I18n.t("route.ui.joinOr")) + "</b> "
                + describe(((org.traincontrol.base.NodeOr) node).getRight(), chosen);
        }

        if (node instanceof org.traincontrol.base.NodeGroup)
        {
            StringBuilder out = new StringBuilder("(");

            for (NodeExpression inside
                : ((org.traincontrol.base.NodeGroup) node).getExpressions())
            {
                out.append(describe(inside, chosen));
            }

            return out.append(")").toString();
        }

        return "";
    }

    // Which column is which, for the three that are pressed rather than typed in.
    private static final int UP = 0;
    private static final int DOWN = 1;
    /** The row's number, which is read rather than filled in. */
    private static final int POSITION = 2;

    /** The shade a cell takes when its kind has no use for it. */
    private static final java.awt.Color UNUSABLE = new java.awt.Color(242, 242, 242);

    private static final int DELETE = 9;

    /**
     * Copies a row, directly under it, beside the trash because the two are a pair: most of a route is near-repetition.
     */
    private static final int DUPLICATE = 10;

    /** The conditions table is narrower, so its trash sits in a different column. */
    private static final int CONDITION_DELETE = 8;

    /** Indenting a condition nests it; outdenting brings it back out. */
    private static final int INDENT = 2;
    private static final int OUTDENT = 3;

    private static final String INDENT_ROW = "indent";
    private static final String OUTDENT_ROW = "outdent";

    // The same two marks, drawn pale and doing nothing, when a line is as deep as the line above allows: a vanished
    // control reads as a feature that stops, while a pale mark says "not from here".
    private static final String INDENT_LIMIT = "indent-limit";
    private static final String OUTDENT_LIMIT = "outdent-limit";

    // What a cell holds when it is one of those: values, so the renderer decides what a mark looks like.
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
     * Draws the marks and makes them do something when pressed.  Drawn by the table's renderer, not as buttons in the
     * cells, because this table reorders and deletes rows constantly and would have to keep live components in step.
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

                // Only a MARK is drawn as one: this renderer also covers the position column, and blanking it would
                // take the row numbers with it.
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
                // In the selection's own ink when the row is picked: a mid-green plus is nearly invisible on the
                // selection blue.
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

        // Further mark columns get the renderer and the pointer but NOT a second copy of the listener below: it
        // dispatches on the value under the pointer, so two copies would both act on every click.
        for (int column : alsoMarked)
        {
            if (column >= 0) table.getColumnModel().getColumn(column).setCellRenderer(marks);
        }

        table.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e)
            {
                // A right-click on the conditions opens their menu rather than working the mark under it.
                if (table instanceof ConditionTable && !javax.swing.SwingUtilities.isLeftMouseButton(e)) return;

                pressRowMark(table, table.rowAtPoint(e.getPoint()), table.columnAtPoint(e.getPoint()));
            }
        });

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
     * A cell with no wash on it (OB-121).  A renderer hands back one recycled component, which may already have been
     * painted gray for the cell before; a selected row is left as super made it.
     *
     * @param out the recycled renderer component
     * @param which the table it is being drawn for
     * @param selected whether this cell is in the selection
     * @return the same component, with any wash taken off it
     */
    private static java.awt.Component unshaded(java.awt.Component out, JTable which, boolean selected)
    {
        // And no tooltip: the recycled component would otherwise wear the refusal of the row drawn before it (OB-246).
        if (out instanceof javax.swing.JComponent) ((javax.swing.JComponent) out).setToolTipText(null);

        if (!selected && out instanceof javax.swing.JComponent)
        {
            ((javax.swing.JComponent) out).setOpaque(true);
            out.setBackground(which.getBackground());
        }

        return out;
    }

    /**
     * Files an accessory command under its address and the protocol it is sent in.
     *
     * @param into the addresses so far
     * @param command an accessory command
     */
    private static void byDecoder(
        java.util.Map<Integer, java.util.Set<org.traincontrol.base.Accessory.accessoryDecoderType>> into,
        org.traincontrol.base.RouteCommand command)
    {
        into.computeIfAbsent(command.getAddress(), address -> new java.util.LinkedHashSet<>())
            .add(command.getProtocol());
    }

    /**
     * Grays every cell somebody cannot type into, since a blank cell that will accept a value and one that will not
     * look identical until clicked.
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
                // A TRAIN'S SENSOR SAYS IT IS ONE (FR-141): shown, not stored, so the cell still holds and edits the number
                Object shown = which instanceof ConditionTable
                    ? ((ConditionTable) which).shownAs(row, column, value) : value;

                // OB-367: the setting is stored as the English word and shown in the user's language
                if (column == 6 && shown instanceof String) shown = settingLabel((String) shown);

                java.awt.Component out = was.getTableCellRendererComponent(which, shown, selected,
                    focused, row, column);

                boolean editable = which.getModel().isCellEditable(row, column);

                // A joining word's row is left alone: only its own column is editable, so shading would read as off.
                if (which instanceof ConditionTable && ((ConditionTable) which).isJoinerRow(row))
                {
                    return unshaded(out, which, selected);
                }

                // So is the + row: it is the button that makes a row, and shading it would read as a row switched off.
                if (row >= which.getRowCount() - 1) return unshaded(out, which, selected);

                // Why Save would refuse this row, said as it is typed (OB-246): the target column offers locomotive
                // names that Save refuses, so the row is marked and the choice stays.  Set on every cell, never
                // skipped: the recycled component would otherwise wear the tooltip of the marked row drawn before it.
                String refused = which instanceof CommandTable
                    ? ((CommandTable) which).whyWrong(row) : null;

                // What a cell means where its column's name does not say (FR-141); not a refusal, so the ink stays.
                String explained = which instanceof ConditionTable
                    ? ((ConditionTable) which).explains(row, column) : null;

                if (out instanceof javax.swing.JComponent)
                {
                    ((javax.swing.JComponent) out).setToolTipText(refused != null ? refused : explained);
                }

                if (!selected)
                {
                    out.setForeground(refused != null && editable ? WRONG
                        : editable ? which.getForeground() : java.awt.Color.GRAY);

                    // A background, not only gray text: most of these cells are empty, and gray text in an empty cell
                    // shows nothing.  Not the position column, which is the row's number.
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
     * Draws the two indent marks and makes them move a row in or out; only conditions have depth.
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
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;

                pressIndentMark(table, table.rowAtPoint(e.getPoint()), table.columnAtPoint(e.getPoint()));
            }
        });
    }

    /** What a press on a row's mark does: the mark's own value says whether it may. */
    private void pressRowMark(JTable table, int row, int column)
    {
        if (row < 0 || column < 0) return;

        // Only where there is a mark: an empty cell (the top row has no way up) stays empty.
        Object value = table.getValueAt(row, column);

        if (MOVE_UP.equals(value)) moveRow(table, row, -1);
        else if (MOVE_DOWN.equals(value)) moveRow(table, row, 1);
        else if (DELETE_ROW.equals(value)) deleteRow(table, row);
        else if (COPY_ROW.equals(value)) duplicateRow(table, row);
        else if (ADD_HERE.equals(value)) addTo(table);
    }

    /**
     * The right-click menu on the conditions (FR-147): inserting beside a line, and the line's marks in words, each
     * enabled exactly when its mark is drawn and doing what a press on it does.  Below the lines, Add Condition.
     */
    private void offerConditionMenu(java.awt.event.MouseEvent e)
    {
        final ConditionTable table = conditions;

        if (locked || !conditionsEditable || !table.isShowing()) return;

        if (table.isEditing()) table.getCellEditor().stopCellEditing();

        final int line = table.rowAtPoint(e.getPoint());

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        if (line < 0 || line >= table.rows.size())
        {
            menu.add(menuItem("route.ui.menuAddCondition", true, () -> addTo(table)));
        }
        else
        {
            if (!table.isRowSelected(line)) table.setRowSelectionInterval(line, line);

            boolean condition = !table.rows.get(line).isJoiner();

            menu.add(menuItem("route.ui.menuInsertConditionAbove", condition, () -> table.insertBeside(line, -1)));
            menu.add(menuItem("route.ui.menuInsertConditionBelow", condition, () -> table.insertBeside(line, 1)));
            menu.addSeparator();
            menu.add(markItem(table, line, UP, MOVE_UP, "route.ui.menuMoveUp"));
            menu.add(markItem(table, line, DOWN, MOVE_DOWN, "route.ui.menuMoveDown"));
            menu.add(markItem(table, line, INDENT, INDENT_ROW, "route.ui.menuIndent"));
            menu.add(markItem(table, line, OUTDENT, OUTDENT_ROW, "route.ui.menuOutdent"));
            menu.addSeparator();
            menu.add(markItem(table, line, CONDITION_DELETE, DELETE_ROW, "ui.delete"));
        }

        menu.show(table, e.getX(), e.getY());
    }

    /** A menu item standing for the mark in one cell. */
    private javax.swing.JMenuItem markItem(final ConditionTable table, final int line, final int column, String mark,
        String key)
    {
        return menuItem(key, mark.equals(table.getValueAt(line, column)), () ->
        {
            if (column == INDENT || column == OUTDENT) pressIndentMark(table, line, column);
            else pressRowMark(table, line, column);
        });
    }

    private static javax.swing.JMenuItem menuItem(String key, boolean enabled, Runnable action)
    {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(I18n.t(key));

        item.setEnabled(enabled);
        item.addActionListener(e -> action.run());

        return item;
    }

    /** What a press on an indent mark does: the mark's own value says whether it may. */
    private void pressIndentMark(ConditionTable table, int row, int column)
    {
        if (row < 0 || column < 0 || locked) return;

        Object value = table.getValueAt(row, column);

        if (INDENT_ROW.equals(value)) table.indent(row, 1);
        else if (OUTDENT_ROW.equals(value)) table.indent(row, -1);
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
     * Nothing in either table moves, goes or arrives on a route that is not ours to change.  Checked here as well as
     * where the marks are drawn, since the keyboard and later additions reach these too.
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
     * Puts a copy of a row directly under it: a route's order is its meaning, and the copy is nearly always wanted
     * next.
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
     * Turns the whole window into something to read: everything that could change the route goes quiet except Cancel
     * and Help.  The title says why, since a window full of grayed controls otherwise reads as broken software.
     */
    private void becomeReadOnly()
    {
        setTitle(I18n.f("route.ui.frameLockedTitle", originalName));

        // Not focusable either: an uneditable field still takes the caret and the I-beam, which reads as broken
        // rather than locked.
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

        // The marks in the rows go with it: a trash can that declines reads as a fault rather than a rule.
        conditionsEditable = false;

        if (saveButton != null) saveButton.setEnabled(false);

        readsAs.setText(I18n.t("route.ui.frameLockedExplains"));
    }

    /**
     * Text from a route, made safe for the "Reads as" markup.
     */
    private static String marked(String text)
    {
        if (text == null) return "";

        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * A setting word in the color of what it does, by the word: the same true is "on" for a sensor, "turn" for a
     * switch.
     */
    private static String setting(String word)
    {
        String safe = marked(settingLabel(word));

        if ("on".equals(word) || "straight".equals(word) || "green".equals(word))
        {
            return "<font color=\"" + READS_GO_COLOUR + "\">" + safe + "</font>";
        }

        if ("off".equals(word) || "turn".equals(word) || "red".equals(word))
        {
            return "<font color=\"" + READS_STOP_COLOUR + "\">" + safe + "</font>";
        }

        return safe;
    }

    /**
     * A condition in as few words as possible, for the reading above.
     */
    private String shortly(RouteCommand command)
    {
        if (command == null) return "?";

        CommandRow row = CommandRow.of(command);

        // A kind with no controls: its own toString is the only description there is.
        if (row == null) return marked(String.valueOf(command));

        switch (row.getKind())
        {
            // The words the row's own dropdown offers, so the reading and the table agree; only the layout knows a
            // signal from a switch.
            case ACCESSORY:
            case SIGNAL:
                return I18n.f(row.getKind() == CommandRow.Kind.SIGNAL
                        ? "route.reads.signal" : "route.reads.switch",
                    marked(row.getTarget()), setting(settingWords(row)[command.getSetting() ? 1 : 0]));

            case FEEDBACK: return I18n.f("route.reads.sensor",
                marked(row.getTarget()), setting(settingWords(row)[command.getSetting() ? 1 : 0]));

            case FUNCTION: return I18n.f("route.reads.function", marked(row.getTarget()),
                command.getFunction(), setting(command.getSetting() ? "on" : "off"));

            case LOCOMOTIVE_SPEED: return I18n.f("route.reads.speed",
                marked(row.getTarget()), marked(row.getSetting()));

            case LOCOMOTIVE_DIRECTION: return I18n.f("route.reads.direction",
                marked(row.getTarget()), marked(settingLabel(row.getSetting())));

            case ROUTE: return I18n.f("route.reads.route", marked(row.getTarget()));

            // "Train X is standing at sensor 21": a fact, not an order, so only ever a condition.
            case AUTO_LOCOMOTIVE: return I18n.f("route.reads.trainAt",
                marked(row.getTarget()), marked(row.getSetting()));

            // And the same fact denied (FR-141)
            case AUTO_LOCOMOTIVE_NOT_AT: return I18n.f("route.reads.trainNotAt",
                marked(row.getTarget()), marked(row.getSetting()));

            // Stop, all functions off, lights on: the kind's own label says it all.
            default: return marked(CommandRow.labelFor(row.getKind()));
        }
    }

    /** The problems numbered, for the Details list: a route pasted in can have a fault in every row. */
    private static List<String> listed(List<String> wrong)
    {
        List<String> out = new java.util.ArrayList<>();

        for (int at = 0; at < wrong.size(); at++) out.add((at + 1) + ".  " + wrong.get(at));

        return out;
    }

    /**
     * The s88 the user typed, with an empty field meaning none (OB-178: "just treat this as 0").  One reader for the
     * check and the save, so relaxing one cannot turn a refusal into an exception.  Still -1 for rubbish such as
     * letters or a stray sign.
     *
     * @return the sensor address, 0 for none, or -1 when what is there is not a number
     */
    private int enteredS88()
    {
        String typed = s88Field.getText() == null ? "" : s88Field.getText().trim();

        if (typed.isEmpty()) return 0;

        try
        {
            int value = Integer.parseInt(typed);

            // Not abs(): a typed minus sign would become a positive address the user never named.
            return value < 0 ? -1 : value;
        }
        catch (NumberFormatException notANumber)
        {
            return -1;
        }
    }
    /**
     * Everything wrong with the window, in the order a reader would work through it.  Most of these catch a row that
     * looks right and builds but does nothing when run (a locomotive called "3", say): no error leads back to it.
     *
     * @return the problems, empty when there are none
     */
    private List<String> everythingWrong()
    {
        List<String> wrong = new ArrayList<>();

        if (nameField.getText().trim().isEmpty()) wrong.add(I18n.t("route.ui.frameNeedsAName"));

        int s88 = enteredS88();

        if (s88 < 0) wrong.add(I18n.t("route.ui.frameS88NotANumber"));

        // Automatic with no sensor can never fire: the sensor is what fires it.
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

        // An emergency stop stands alone: "if a route has emergency stop, it cannot have any other types of commands."
        // Chain a stop-only route with a Route command instead, as the message says.
        boolean aStop = false;
        boolean anythingElse = false;

        for (Entry entry : commands.rows)
        {
            if (entry.getRow() == null) continue;

            if (entry.getRow().getKind() == CommandRow.Kind.STOP) aStop = true;
            else anythingElse = true;
        }

        // In the editor's own words for the two rows
        if (aStop && anythingElse)
        {
            wrong.add(I18n.f("route.ui.frameStopStandsAlone", I18n.t("route.kind.STOP"), I18n.t("route.kind.ROUTE")));
        }

        // A level that disagrees with itself means two things at once; saving would pick one quietly.
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
     * The function numbers a row's locomotive has, or null where nothing can be said (MT-464).  A number already in
     * the cell is always offered, even out of range: a combo box handed a value it lacks keeps whatever was selected,
     * so a click would quietly rewrite F32 to F0, the lights.
     *
     * @param row the row, or null
     * @return the numbers to offer, or null for a free typed number
     */
    private String[] functionsOffered(CommandRow row)
    {
        if (row == null || !CommandRow.isFunction(row.getKind())) return null;

        if (parent == null || parent.getModel() == null) return null;

        String target = row.getTarget() == null ? "" : row.getTarget().trim();

        org.traincontrol.base.Locomotive loco = parent.getModel().getLocByName(target);

        if (loco == null || loco.getNumF() <= 0) return null;

        java.util.List<String> out = new ArrayList<>();

        for (int f = 0; f < loco.getNumF(); f++) out.add(String.valueOf(f));

        String has = functionNumberOf(row.getSetting());

        if (has != null && !has.trim().isEmpty() && !out.contains(has.trim())) out.add(has.trim());

        return out.toArray(new String[0]);
    }

    /**
     * What the function column would offer for a row, for a test (MT-464).
     *
     * @param row which row
     * @return the numbers offered, or null where the cell takes a typed number
     */
    public java.util.List<String> functionChoicesForTest(int row)
    {
        // Through the cell editor the table would use, not functionsOffered: the editor decides what can be typed.
        return offeredBy(commands, row, 5);
    }

    /**
     * What a command's locomotive or route cell would offer, for a test.
     *
     * @param row which row
     * @return the names offered, or null where the cell is not a list
     */
    public java.util.List<String> targetChoicesForTest(int row)
    {
        return offeredBy(commands, row, 4);
    }

    /**
     * What a condition's locomotive cell would offer, for a test.
     *
     * @param line which line of the conditions
     * @return the names offered, or null where the cell is not a list
     */
    public java.util.List<String> conditionTargetChoicesForTest(int line)
    {
        return offeredBy(conditions, line, 5);
    }

    /**
     * What one cell's editor offers, read through the editor the table would use.
     *
     * @param table the table
     * @param row its row
     * @param column its column
     * @return the values offered, or null where the cell is not a list
     */
    private static java.util.List<String> offeredBy(JTable table, int row, int column)
    {
        javax.swing.table.TableCellEditor editor = table.getCellEditor(row, column);

        if (!(editor instanceof DefaultCellEditor)) return null;

        java.awt.Component control = ((DefaultCellEditor) editor).getComponent();

        if (!(control instanceof JComboBox)) return null;

        JComboBox<?> box = (JComboBox<?>) control;

        java.util.List<String> out = new ArrayList<>();

        for (int at = 0; at < box.getItemCount(); at++) out.add(String.valueOf(box.getItemAt(at)));

        return out;
    }

    /**
     * What is wrong with one row, if anything: a row that cannot be built (the row refuses itself), or one that builds
     * and names something that does not exist (a locomotive nobody owns, an address no decoder can carry).
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
            // The name of a locomotive on this layout, not any text at all: an accessory turned into a
            // locomotive command kept its address as the name.
            case LOCOMOTIVE_SPEED:
            case LOCOMOTIVE_DIRECTION:
            case FUNCTION:
            case AUTO_LOCOMOTIVE:
            case AUTO_LOCOMOTIVE_NOT_AT:
                if (parent.getModel().getLocByName(target) == null)
                {
                    wrong.add(I18n.f("route.ui.frameNoSuchLocomotive", target));
                }
                else if (!RouteCommand.isNameUsable(target))
                {
                    // A comma in the name breaks the comma-separated command line.  Brackets are allowed: "bracketed
                    // loc names should just be allowed", as in "SBB 460 (2)".
                    wrong.add(I18n.f("route.ui.frameNameNotUsable", target));
                }

                // And the sensor an "is at" condition watches: saved as 0 or nothing, it can never be true.
                if (CommandRow.asksWhereATrainIs(row.getKind()) && numberOr(row.getSetting(), 0) <= 0)
                {
                    wrong.add(I18n.f("route.ui.frameNotASensor", row.getSetting() == null ? "" : row.getSetting()));
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

            // Both motors: the second is the one the user never typed.
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

        // A function number past the end of what that locomotive has saves cleanly and does nothing on the rails.
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

        // A speed the decoder cannot be given is clipped quietly further down.  A negative speed is not a mistake:
        // RouteCommand.parseLine documents it as an instant stop (MarklinRoute calls instantStop()).
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

    /** Adds a complaint when an address is not one the decoder can carry (MM2 and DCC have different ranges). */
    private void addressProblem(List<String> wrong, String target,
        Accessory.accessoryDecoderType protocol)
    {
        Accessory.accessoryDecoderType speaks =
            protocol == null ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : protocol;

        int address = numberOr(target, 0);

        // The logical address, asked as one: Accessory's maxima are raw, so unconverted values would be refused here
        // and accepted by the diagram editor.
        if (address <= 0 || !Accessory.isValidLogicalAddress(address, speaks))
        {
            wrong.add(I18n.f("route.ui.frameNotAnAddress", target + " (" + speaks + ")"));
        }
    }

    /** The speed that means "stop now" rather than "coast to zero" (RouteCommand.parseLine, MarklinRoute). */
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

    /**
     * A number, or a fallback where the text is not one.
     */
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
     * Lights this route on the track diagram: what it commands in yellow, what it checks in orange.  Read off the
     * window, not the saved route, so rows typed a moment ago count.  Held five seconds, so nothing has to be cleared.
     */
    private void highlightOnDiagram()
    {
        if (parent == null) return;

        // By kind, not number (MT-462): a tile of any kind carries a number, so the kind travels with the address, and
        // the protocol too, as an accessory address is one per protocol.
        java.util.Map<Integer, java.util.Set<org.traincontrol.base.Accessory.accessoryDecoderType>> commanded =
            new java.util.LinkedHashMap<>();
        java.util.Set<Integer> commandedRoutes = new java.util.LinkedHashSet<>();
        java.util.Map<Integer, java.util.Set<org.traincontrol.base.Accessory.accessoryDecoderType>> checked =
            new java.util.LinkedHashMap<>();
        java.util.Set<Integer> checkedSensors = new java.util.LinkedHashSet<>();

        // A sensor a COMMAND names, which only a route saved by an older version can hold.
        java.util.Set<Integer> commandedSensors = new java.util.LinkedHashSet<>();

        for (Entry entry : commands.rows)
        {
            org.traincontrol.base.RouteCommand command = entry.toCommand();

            // Asked whether it has an address first: getAddress() throws for command types without one.
            if (command == null || !command.hasAddress() || command.getAddress() <= 0) continue;

            if (command.isAccessory()) byDecoder(commanded, command);

            if (command.isRoute()) commandedRoutes.add(command.getAddress());

            // And a sensor this route commands: canBeACommand blocks new FEEDBACK rows, but an older route can hold
            // one, and CommandRow says such a row still opens.
            if (command.isFeedback()) commandedSensors.add(command.getAddress());
        }

        for (ConditionOutline.Row row : conditions.rows)
        {
            if (row.isJoiner() || row.getCommand() == null) continue;

            // The same question on the condition side: a condition can name a locomotive.
            if (!row.getCommand().hasAddress() || row.getCommand().getAddress() <= 0) continue;

            if (row.getCommand().isAccessory()) byDecoder(checked, row.getCommand());

            if (row.getCommand().isFeedback()) checkedSensors.add(row.getCommand().getAddress());
        }

        // And the sensor that fires it: "the S88 that triggers the route or is involved in conditions".
        int trigger = numberOr(s88Field.getText(), 0);

        if (trigger > 0) checkedSensors.add(trigger);

        // A square both commanded and checked is drawn as commanded, the stronger statement.  Asked within a kind and
        // a decoder: an accessory and a sensor numbered alike, or MM2 5 and DCC 5, are different squares.
        for (java.util.Map.Entry<Integer, java.util.Set<org.traincontrol.base.Accessory.accessoryDecoderType>>
            both : commanded.entrySet())
        {
            java.util.Set<org.traincontrol.base.Accessory.accessoryDecoderType> alsoChecked = checked.get(both.getKey());

            if (alsoChecked == null) continue;

            alsoChecked.removeAll(both.getValue());

            if (alsoChecked.isEmpty()) checked.remove(both.getKey());
        }

        // And the same for the sensors, before either is painted: canBeACommand blocks a new FEEDBACK row, but an older
        // route can hold one, and a sensor the route writes to must not be washed as checked.  The case this is for is
        // the route's own trigger, always in checkedSensors.  The removal comes before the paint calls.
        checkedSensors.removeAll(commandedSensors);

        int lit = parent.highlightAccessories(commanded, org.traincontrol.util.ImageUtil.HIGHLIGHT,
            HIGHLIGHT_HOLD_MS);

        lit += parent.highlightAddresses(commandedRoutes, TrainControlUI.AddressedAs.ROUTE,
            org.traincontrol.util.ImageUtil.HIGHLIGHT, HIGHLIGHT_HOLD_MS);

        // And not a square already lit as commanded (OB-286): a three-way is commanded under one address and checked
        // under the other.
        lit += parent.highlightAccessories(checked, org.traincontrol.util.ImageUtil.HIGHLIGHT_CONDITION,
            HIGHLIGHT_HOLD_MS, commanded);

        lit += parent.highlightAddresses(checkedSensors, TrainControlUI.AddressedAs.FEEDBACK,
            org.traincontrol.util.ImageUtil.HIGHLIGHT_CONDITION, HIGHLIGHT_HOLD_MS);

        lit += parent.highlightAddresses(commandedSensors, TrainControlUI.AddressedAs.FEEDBACK,
            org.traincontrol.util.ImageUtil.HIGHLIGHT, HIGHLIGHT_HOLD_MS);

        // Nothing lit is an answer too: a route can name accessories that are not drawn anywhere on the diagram.
        if (lit == 0)
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.infoNothingToHighlight"));
        }
    }

    /** Five seconds: long enough to look from this window to the diagram and back */
    private static final int HIGHLIGHT_HOLD_MS = 5000;

    private JButton highlightButton;

    /**
     * Answers "would this route fire right now", off the event thread: an autoloc condition is evaluated by the
     * static `Route.evaluate`, which can wait three seconds (`Layout.waitForS88Reached`) and would freeze the window.
     * The trigger sensor and the conditions are asked separately so a single "no" does not leave the user guessing.
     * The button is disabled meanwhile, since a second press would start a second wait.
     */
    private void testAgainstTheRailway()
    {
        if (parent == null || parent.getModel() == null) return;

        final NodeExpression conditions;

        final String s88;

        // Read on the event thread (the rows are its to read) and still inside a try: what is known to throw is read
        // inside the worker's try, and what is left is a belt, so the operator gets the sentence the single-threaded
        // version gave instead of a stack trace.
        try
        {
            conditions = conditionsEditable
                ? ConditionOutline.toExpression(this.conditions.rows) : conditionsAsFound;

            s88 = s88Field.getText().trim();
        }
        catch (RuntimeException e)
        {
            JOptionPane.showMessageDialog(this, I18n.t("route.ui.errorConditionExpressionInvalid"));

            return;
        }

        if (testButton != null) testButton.setEnabled(false);

        new Thread(() ->
        {
            String answer;

            try
            {
                boolean sensor = parent.getModel().getFeedbackState(s88);

                // No conditions is a route held up by nothing but its sensor: true, not unknown.
                boolean held = conditions == null || conditions.evaluate(parent.getModel());

                answer = I18n.f("route.ui.messageTriggeringConditionSummary",
                    I18n.t(sensor ? "route.ui.valueTrue" : "route.ui.valueFalse"),
                    I18n.t(held ? "route.ui.valueTrue" : "route.ui.valueFalse"),
                    I18n.t(sensor && held ? "route.ui.valueWould" : "route.ui.valueWouldNot"));
            }
            catch (Exception e)
            {
                // A sensor that names nothing, or a non-number address: the old editor's message, same answer.
                answer = I18n.t("route.ui.errorConditionExpressionInvalid");
            }

            final String said = answer;

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                if (testButton != null) testButton.setEnabled(true);

                JOptionPane.showMessageDialog(this, said);
            });
        }, "route-condition-test").start();
    }

    /** Builds the route back up and hands it to the same code the text editor uses. */
    private void onSave()
    {
        List<String> wrong = everythingWrong();

        if (!wrong.isEmpty())
        {
            // The window stays open on Fix.  Discard is offered beside it: a row somebody was experimenting with, in a
            // route they no longer want changed, should not have to be corrected just to close the window.
            String[] answers = { I18n.t("route.ui.frameGoAndFixIt"), I18n.t("route.ui.frameDiscard") };

            int answer = JOptionPane.showOptionDialog(this,
                DetailsArea.message(I18n.t("route.ui.frameCannotSaveYet"), listed(wrong)),
                I18n.t("route.ui.frameCannotSaveYetTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, answers, answers[0]);

            if (answer == 1) dispose();

            return;
        }

        String name = nameField.getText().trim();

        // Through the reader the validation used, so the two cannot disagree about a blank field (OB-178).
        int s88 = enteredS88();

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
                    // Checked, as the edit path below is: a refusal says so rather than closing with nothing said.
                    JOptionPane.showMessageDialog(this, I18n.f("route.ui.errorEditRouteFailed", name));
                    return;
                }
            }
            else
            {
                // The route may have moved under this window: it is not modal, and Save is a delete-and-re-add of
                // everything it holds, so it would write over Enable/Disable, a delete or an import silently.  Asked
                // rather than refused, because what to do is the operator's to decide.
                String moved = howTheRouteMoved();

                if ("renamed".equals(moved))
                {
                    // Renamed is not deleted: saving onto the route under its new name keeps its id, and with it every
                    // route tile and autonomy selection bound to it.
                    String nowCalled = nameNowHeldById();

                    // Asked twice, and it can answer differently: if the route has gone since, the message would say
                    // "renamed to null".
                    if (nowCalled == null)
                    {
                        JOptionPane.showMessageDialog(this,
                            I18n.f("route.ui.errorEditRouteFailed", originalName));

                        return;
                    }

                    // And it keeps the name the other party gave it, unless the operator typed in the name field (a
                    // rename they asked for).
                    String saveAs = nameToSaveAs(name, nowCalled);

                    if (JOptionPane.showOptionDialog(this,
                        I18n.f("route.ui.confirmRouteRenamed", originalName, nowCalled),
                        I18n.t("route.ui.titleRouteMoved"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != 0)
                    {
                        return;
                    }

                    if (!parent.getModel().editRoute(nowCalled, saveAs, built, s88, trigger,
                        enabledBox.isSelected(), expression))
                    {
                        JOptionPane.showMessageDialog(this, I18n.f("route.ui.errorEditRouteFailed", saveAs));
                        return;
                    }
                }
                else if ("gone".equals(moved))
                {
                    // Added back rather than lost: originalName is final, so editRoute would return false.
                    if (JOptionPane.showOptionDialog(this,
                        I18n.f("route.ui.confirmRouteGoneSaveAsNew", originalName),
                        I18n.t("route.ui.titleRouteMoved"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[0]) != 0)
                    {
                        return;
                    }

                    if (!parent.getModel().newRoute(name, built, s88, trigger, enabledBox.isSelected(), expression))
                    {
                        JOptionPane.showMessageDialog(this, I18n.f("route.ui.errorEditRouteFailed", name));
                        return;
                    }
                }
                else
                {
                    if ("changed".equals(moved) && JOptionPane.showOptionDialog(this,
                        I18n.f("route.ui.confirmRouteChangedUnderneath", originalName),
                        I18n.t("route.ui.titleRouteMoved"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != 0)
                    {
                        return;
                    }

                    if (!parent.getModel().editRoute(originalName, name, built, s88, trigger,
                        enabledBox.isSelected(), expression))
                    {
                        JOptionPane.showMessageDialog(this, I18n.f("route.ui.errorEditRouteFailed", name));
                        return;
                    }
                }
            }

            // A finished edit puts every play button back (FR-043): a spinner still turning is a claim about a route
            // that has changed.
            parent.resetRouteSpinners();

            parent.refreshRouteList();
            parent.repaintLayout();

            // No diagram edit to complete: a tile follows its route by number (rebindRouteTiles), and
            // layoutEditingComplete would reset autonomy and re-read every page.  A route is no page of the diagram.

            // A sync only when the route is NEW and its id could collide with one the station issued (OB-155).  Routes
            // travel one way, so a sync after an edit would re-fetch everything to learn nothing.  A new route's id is
            // the next free one in the local database, which does not know of routes added on the station since the
            // last sync; but newRoute allocates from ROUTE_STARTING_ID up, which the station never issued ("the route
            // page should not have to sync with the cs2 after edits/deletions for routes >= ID 1000").  Read back from
            // the database, because newRoute picks the id.
            org.traincontrol.base.Route saved = parent.getModel().getRoute(name);

            boolean couldCollide = saved == null
                || !org.traincontrol.marklin.MarklinControlStation.isLocalRouteId(saved.getId());

            if (originalName.isEmpty() && couldCollide && anyRouteCameFromTheStation())
            {
                parent.syncWithCS2();
            }

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
     * The delay the railway will actually wait for, given the one somebody asked for.  MarklinRoute sleeps
     * `max(delay, DEFAULT_SLEEP_MS)` between commands, so a delay of 1 to 150 would be stored, shown and never used.
     * Zero is left alone: it means no delay asked for.  Applied on the way out as well as in, since a route from an
     * earlier build, a station or a JSON file must show a number the railway will use.
     *
     * @param asked what the row holds
     * @return what the railway will wait
     */
    private static int delayTheRailwayWillUse(int asked)
    {
        if (asked > 0 && asked < org.traincontrol.marklin.MarklinRoute.DEFAULT_SLEEP_MS)
        {
            return org.traincontrol.marklin.MarklinRoute.DEFAULT_SLEEP_MS;
        }

        return asked;
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

            if (parsed < 0) return wasBefore;

            return delayTheRailwayWillUse(parsed);
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
     * order that comes apart.
     *
     * A kept command is shown, grayed and uneditable, rather than hidden.  A user who cannot see it
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
     * Protocol and delay are columns rather than assumptions: an assumed MM2 is a DIFFERENT address space
     * from DCC, and a dropped delay is what holds a slow point motor apart from the command behind it.  A
     * cell the kind cannot use is grayed rather than hidden, so the table stays one shape.
     */
    private final class CommandTable extends JTable
    {
        private final List<Entry> rows = new ArrayList<>();

        /**
         * Why each row cannot be saved, for the mark beside it (OB-246).  Held, not asked per paint: `problemsWith`
         * asks the layout about every name, and a renderer runs once per cell.
         */
        private final java.util.Map<Integer, String> wrongRows = new java.util.LinkedHashMap<>();

        private final AbstractTableModel model = new AbstractTableModel()
        {
            @Override
            public int getRowCount()
            {
                // One more than there are rows: the last is where a command is added, part of the table so adding
                // happens where the row will land.
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
                    // Move, position and delete live in the row: a button under the table acts on whichever row is
                    // selected.
                    case UP: return "";
                    case DOWN: return "";

                    // The position: order is the whole meaning of a route.
                    case 2: return "#";
                    case 3: return I18n.t("route.ui.frameColKind");
                    case 4: return I18n.t("route.ui.frameColTarget");

                    // The function NUMBER, which only one kind has; kept out of the "3:on" cell so on/off can be a
                    // dropdown.
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

                // No marks on a route that is not ours to change: a mark that declines reads as a fault.
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

                    // Blank where the kind has no such thing: a grayed cell with a stale address reads as a value in
                    // use.
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

                        // The number the railway will wait: routes saved before the floor was visible, and imports, can
                        // hold less.
                        int shown = delayTheRailwayWillUse(at.getDelay());

                        return shown == 0 ? "" : String.valueOf(shown);
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

                // A kind the dropdown does not offer cannot be edited: it is built from canBeACommand (OB-141 excludes
                // FEEDBACK), and a non-editable JComboBox refuses an item not in its model, so one click would write
                // another kind back (an s88 command becoming an emergency stop).  Read-only, so the guard and the
                // affordance ask one question.
                if (column == 3) return CommandRow.canBeACommand(at.getKind());

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

                // A different kind starts empty: the setting vocabularies do not overlap, and neither does the target's
                // meaning, so carrying it over would turn "Accessory 3" into a command for a locomotive called "3".
                boolean became = column == 3 && kind != at.getKind();

                String target = column == 4 ? text : at.getTarget();

                // Changing the kind replaces the setting with one the new kind accepts, or Save would refuse a cell the
                // user never touched.  A function's two halves are stored as one value, which is the model's business.
                String setting = at.getSetting();

                if (column == 3 && kind != at.getKind()) setting = CommandRow.defaultSettingFor(kind);
                else if (column == 5) setting = text + ":" + functionStateOf(at.getSetting());
                else if (column == 6) setting = CommandRow.isFunction(kind)
                    ? functionNumberOf(at.getSetting()) + ":" + text : text;

                // Every rebuild carries protocol and delay forward, or editing the SETTING of a DCC accessory would
                // move it to MM2: the row is rebuilt from the columns, and anything not passed on is lost.
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

                // The address decides which of the two it is, since only the layout knows which stands there.  Not done
                // when the user has just chosen the kind by hand, nor for an address the layout has never heard of.
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

                // Judged as it is typed (OB-246), and every row: a locomotive command's target can make a later row
                // wrong, because problemsWith asks the layout.
                settle();

                fireTableRowsUpdated(row, row);

                // The whole table repaints: settle may have marked another row, and fireTableRowsUpdated repaints one.
                CommandTable.this.repaint();
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

            // Only what a route can DO; "train at a sensor" is a fact, so only a condition.
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

            // The duplicate column is named here: a second call would register a second mouse listener, and both would
            // act on every click.
            actOnRowMarks(this, DELETE, UP, DOWN, UP, DUPLICATE);

            // Kept commands are drawn grayed, so "you cannot edit this one" is something the table
            // says rather than something the user discovers by clicking
            setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
            {
                @Override
                public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column)
                {
                    java.awt.Component c = super.getTableCellRendererComponent(table, value, selected,
                        focused, row, column);

                    // The row at the bottom is left alone (MT-011): it is the button that makes a row, and
                    // greyWhatCannotBeEdited exempts it from the wash, so this renderer, running inside that one, must
                    // too.
                    if (row >= rows.size())
                    {
                        // Set, not skipped: the renderer is one shared component carrying whatever the cell above left,
                        // and super latches setForeground/setBackground into the next row.
                        c.setEnabled(true);

                        if (!selected)
                        {
                            c.setForeground(table.getForeground());
                            c.setBackground(table.getBackground());
                        }

                        return c;
                    }

                    // Both kinds of "you cannot type here": a kept command row, and a cell whose kind has no such thing
                    // (a stop has no address).
                    boolean editable = row < rows.size() && rows.get(row).isEditable()
                        && model.isCellEditable(row, column);

                    c.setEnabled(editable);

                    // The refusal mark is not set here (OB-246): greyWhatCannotBeEdited wraps this renderer and would
                    // overwrite the ink, so the mark lives there.
                    if (!selected)
                    {
                        c.setForeground(editable
                            ? table.getForeground() : java.awt.Color.GRAY);
                    }

                    return c;
                }
            });

            // Wrapped around the renderer above, after it, because setDefaultRenderer replaces what was set: gray text
            // in an empty cell shows nothing, so it adds a wash and exempts the + row.
            greyWhatCannotBeEdited(this);
        }

        /**
         * The words this row's setting may take (turn or straight, on or off, forward or backward), or null for a
         * number or a name.
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

                if (words != null) return chooseSetting(words);
            }

            // A number where a number is meant: refused as typed, not at Save.
            if (column == 8) return digitsOnly();

            // And the functions that locomotive actually has (MT-464: "there is no validation of the function count on
            // mm2 locomotives in the route view, F32 was accepted").  The control that offers a value asks the guard's
            // question (OB-057, OB-090); a locomotive the database lacks takes a typed number.
            if (column == 5)
            {
                String[] functions = functionsOffered(rows.get(row).getRow());

                return functions == null ? digitsOnly() : chooseFrom(functions);
            }

            // And the target, wherever the answer comes from a list this application already has: typing invites a typo
            // found only when the route misbehaves.
            if (column == 4)
            {
                CommandRow.Kind kind = rows.get(row).getRow().getKind();

                if (kind == CommandRow.Kind.ACCESSORY || kind == CommandRow.Kind.FEEDBACK)
                {
                    return digitsOnly();
                }

                if (kind == CommandRow.Kind.ROUTE)
                {
                    // Not this route: Save refuses a route that calls itself (frameRouteCallsItself).  A cell already
                    // holding it keeps it (withTheCellsOwn), so the gate still names it.
                    java.util.List<String> others = new ArrayList<>(parent.getModel().getRouteList());

                    others.remove(originalName);

                    return chooseFrom(withTheCellsOwn(others, rows.get(row).getRow().getTarget()));
                }

                if (kind == CommandRow.Kind.LOCOMOTIVE_SPEED
                    || kind == CommandRow.Kind.LOCOMOTIVE_DIRECTION
                    || kind == CommandRow.Kind.FUNCTION
                    || CommandRow.asksWhereATrainIs(kind))
                {
                    return chooseFrom(withTheCellsOwn(parent.getModel().getLocList(),
                        rows.get(row).getRow().getTarget()));
                }
            }

            return super.getCellEditor(row, column);
        }

        /**
         * Copies a row and puts the copy under it; a kept command is copied as it stands, not turned into something
         * shown.
         */
        void duplicateAt(int at)
        {
            if (at < 0 || at >= rows.size()) return;

            Entry was = rows.get(at);

            rows.add(at + 1, was.isEditable()
                ? Entry.of(new CommandRow(was.getRow().getKind(), was.getRow().getTarget(),
                    was.getRow().getSetting(), was.getRow().getProtocol(), was.getRow().getDelay()))
                : Entry.of(was.toCommand()));

            fireTableDataChanged();

            setRowSelectionInterval(at + 1, at + 1);
        }

        void addRow()
        {
            rows.add(Entry.of(new CommandRow(CommandRow.Kind.ACCESSORY, "", "straight")));
            fireTableDataChanged();
        }

        void removeSelected()
        {
            removeAt(getSelectedRow());
        }

        void removeAt(int at)
        {
            if (at < 0 || at >= rows.size()) return;

            rows.remove(at);
            fireTableDataChanged();
        }

        void move(int by)
        {
            shift(getSelectedRow(), by);
        }

        /**
         * Not "move": Component has had a move(int, int) since AWT 1.0, and overriding it by accident repositions the
         * window.
         */
        void shift(int at, int by)
        {
            int to = at + by;

            if (at < 0 || at >= rows.size() || to < 0 || to >= rows.size()) return;

            rows.add(to, rows.remove(at));
            fireTableDataChanged();
            setRowSelectionInterval(to, to);
        }

        /**
         * Works out which rows Save would refuse, and why (OB-246).  A row nobody has filled in yet is not marked: a
         * new row is added empty, and problemsWith refuses that at once, so it would be red before a character was
         * typed.  Save still refuses the blank row, naming its number.
         */
        private void settle()
        {
            wrongRows.clear();

            for (int at = 0; at < rows.size(); at++)
            {
                Entry entry = rows.get(at);

                if (!entry.isEditable()) continue;

                CommandRow row = entry.getRow();

                boolean named = !CommandRow.hasTarget(row.getKind())
                    || (row.getTarget() != null && !row.getTarget().trim().isEmpty());

                if (!named) continue;

                List<String> problems = problemsWith(row);

                if (!problems.isEmpty()) wrongRows.put(at, problems.get(0));
            }
        }

        /**
         * @param row which row
         * @return why Save would refuse it, or null
         */
        String whyWrong(int row)
        {
            return wrongRows.get(row);
        }

        void fireTableDataChanged()
        {
            settle();
            model.fireTableDataChanged();
        }
    }

    /**
     * The conditions, as an indented list with the joining words on lines of their own; ConditionOutline holds the
     * rule.
     */
    private final class ConditionTable extends JTable
    {
        /**
         * Whether the row at this line is a joining word rather than a condition; asked by the renderer.
         *
         * @param line the row
         * @return whether it is a joiner
         */
        boolean isJoinerRow(int line)
        {
            return line >= 0 && line < conditions.rows.size() && conditions.rows.get(line).isJoiner();
        }

        /** A press on an indent mark inside a selection of several lines keeps it, so the mark acts on them all. */
        @Override
        public void changeSelection(int row, int column, boolean toggle, boolean extend)
        {
            if ((column == INDENT || column == OUTDENT) && !toggle && !extend && getSelectedRowCount() > 1
                && isRowSelected(row)) return;

            super.changeSelection(row, column, toggle, extend);
        }

        /** The marks of a selection depend on all of it, so every change of selection redraws them. */
        @Override
        public void valueChanged(javax.swing.event.ListSelectionEvent e)
        {
            super.valueChanged(e);

            repaint();
        }

        /**
         * What a cell shows, where that is more than what it holds (FR-141): a train-at-a-sensor line's setting is the
         * sensor's address, which a bare number under "Setting" would not say.  Only the painting changes.
         *
         * @param line the row
         * @param column the column
         * @param value what the cell holds
         * @return what to paint
         */
        Object shownAs(int line, int column, Object value)
        {
            return column == 6 && asksWhereATrainIs(line)
                ? I18n.f("route.ui.frameSensorAddress", String.valueOf(value)) : value;
        }

        /**
         * The tooltip a cell carries to say what it means, or null for none (FR-141).
         *
         * @param line the row
         * @param column the column
         * @return the explanation
         */
        String explains(int line, int column)
        {
            return column == 6 && asksWhereATrainIs(line) ? I18n.t("route.ui.frameSensorAddressTip") : null;
        }

        private boolean asksWhereATrainIs(int line)
        {
            if (line < 0 || line >= rows.size() || rows.get(line).isJoiner()) return false;

            CommandRow term = CommandRow.of(rows.get(line).getCommand());

            return term != null && CommandRow.asksWhereATrainIs(term.getKind());
        }

        private final List<ConditionOutline.Row> rows = new ArrayList<>();

        /** How many pixels one level of indentation is worth. */
        private static final int STEP = 16;

        /** The lines whose word disagrees with its level, refreshed whenever anything changes. */
        private java.util.Set<Integer> flagged = new java.util.LinkedHashSet<>();

        /** Why each flagged line is flagged, for the warning beside it. */
        private java.util.Map<Integer, ConditionOutline.Problem> why = new java.util.LinkedHashMap<>();

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

                // Conditions only, where there is another condition that way to swap with; a word does not move by
                // hand.
                if (column == UP) return ConditionOutline.canMove(rows, line, -1) ? MOVE_UP : "";
                if (column == DOWN) return ConditionOutline.canMove(rows, line, 1) ? MOVE_DOWN : "";

                int[] chosen = column == INDENT || column == OUTDENT ? chosenLines() : null;

                if (chosen != null && line >= chosen[0] && line <= chosen[1])
                {
                    boolean in = column == INDENT;

                    return block(chosen[0], chosen[1], in ? 1 : -1) != null
                        ? (in ? INDENT_ROW : OUTDENT_ROW) : (in ? INDENT_LIMIT : OUTDENT_LIMIT);
                }

                if (column == INDENT)
                {
                    // The rules `indent` applies, so the mark never offers a press that does nothing.
                    if (line == 0) return rows.size() < 2 ? "" : row.getDepth() < 1 ? INDENT_ROW : INDENT_LIMIT;

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

                // As shown, not stored (asShown): a cell whose editability is decided from another kind than the one on
                // screen argues with itself.
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

                // As shown: picking "red" from a signal row must build a signal row, not an accessory row carrying
                // "red"; what is stored is the same either way.
                CommandRow term = asShown(CommandRow.of(row.getCommand()));

                if (term == null) return;

                CommandRow edited;

                if (column == 4)
                {
                    CommandRow.Kind became = CommandRow.kindFor(text);

                    if (became == null) return;

                    // Choosing the kind a line already is changes nothing: a drop-down commits on close, and the reset
                    // below would put the line back to its starting address.
                    if (became == term.getKind()) return;

                    // A different kind does not keep the old target (a sensor number left in an accessory row is a
                    // valid address of something else).  Reset to something that builds, not blank: this table stores
                    // built commands, so an empty address would throw in the rebuild below.  "Train X is at sensor N"
                    // has a name target and an address setting, so it starts with the first locomotive on the roster.
                    String starting = CommandRow.asksWhereATrainIs(became)
                        ? firstLocomotive() : (CommandRow.hasTarget(became) ? "1" : "");

                    String settingFor = CommandRow.asksWhereATrainIs(became)
                        ? "1" : CommandRow.defaultSettingFor(became);

                    edited = new CommandRow(became, starting, settingFor,
                        null, CommandRow.defaultDelayFor(became));

                    // Between at and not at (FR-141), one condition asked either way round: only the question turns.
                    if (CommandRow.asksWhereATrainIs(became) && CommandRow.asksWhereATrainIs(term.getKind()))
                    {
                        edited = new CommandRow(became, term.getTarget(), term.getSetting());
                    }

                    // Except between switch and signal, one command in two vocabularies: the line keeps its address and
                    // says the same setting in the other words (turn is red, straight is green).  What it shows is the
                    // layout's to say (asShown).
                    boolean switchOrSignal = (became == CommandRow.Kind.ACCESSORY || became == CommandRow.Kind.SIGNAL)
                        && (term.getKind() == CommandRow.Kind.ACCESSORY || term.getKind() == CommandRow.Kind.SIGNAL);

                    if (switchOrSignal)
                    {
                        String said = term.getSetting() == null
                            ? "" : term.getSetting().trim().toLowerCase(java.util.Locale.ROOT);

                        boolean stop = "turn".equals(said) || "red".equals(said);
                        boolean go = "straight".equals(said) || "green".equals(said);
                        boolean signal = became == CommandRow.Kind.SIGNAL;

                        String inOtherWords = stop ? (signal ? "red" : "turn")
                            : go ? (signal ? "green" : "straight") : CommandRow.defaultSettingFor(became);

                        edited = new CommandRow(became, term.getTarget(), inOtherWords,
                            term.getProtocol(), term.getDelay());
                    }
                }
                else
                {
                    edited = new CommandRow(term.getKind(),
                        column == 5 ? text : term.getTarget(),
                        column == 6 ? text : term.getSetting(),
                        column == 7 ? protocolOf(text) : term.getProtocol(),
                        term.getDelay());

                    // The address decides which of the two it is, as in the commands table.
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

            // No vertical rules: the marks down either side are controls, not data.
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

                    // The guides run down the joining words too, so a word and the conditions it joins share a level.
                    out.setBorder(RowIcons.guides(depth, STEP));

                    out.setFont(joiner
                        ? new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 13)
                        : new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));

                    // A warning triangle after a red word, with the reason on its tooltip.  This label is reused down
                    // the column, so an unflagged line clears both.
                    ConditionOutline.Problem problem = why.get(line);

                    out.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
                    out.setIconTextGap(6);
                    out.setIcon(problem == null ? null : RowIcons.warning(14));
                    out.setToolTipText(problem == null ? null : "<html><p style='width:300px'>"
                        + marked(I18n.t(problem == ConditionOutline.Problem.DISAGREES
                            ? "route.ui.conditionWordDisagrees" : "route.ui.conditionWordJoinsNothing"))
                        + "</p></html>");

                    if (!selected)
                    {
                        // Red where a word disagrees with the rest of its level: two words at one level are two
                        // meanings.
                        out.setForeground(flagged.contains(line) ? WRONG
                            : joiner ? HEADING_BLUE : which.getForeground());
                    }

                    return out;
                }
            });

            greyWhatCannotBeEdited(this);

            actOnRowMarks(this, CONDITION_DELETE, UP, DOWN, UP);
            actOnIndentMarks(this);

            // To the bottom of its scroll pane, so a right-click below the last line reaches the menu (FR-147).
            setFillsViewportHeight(true);

            addMouseListener(new org.traincontrol.gui.widgets.PopupListener()
            {
                @Override
                protected void showPopup(java.awt.event.MouseEvent e)
                {
                    offerConditionMenu(e);
                }
            });
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

            // As shown, not stored: the cell displays "red", and a dropdown built from the stored kind would offer
            // "straight" and "turn", so a click in and out would commit its first entry (the mechanism asShown
            // describes).
            CommandRow term = asShown(CommandRow.of(row.getCommand()));

            if (term == null) return super.getCellEditor(line, column);

            if (column == 6)
            {
                String[] words = settingWords(term);

                if (words != null) return chooseSetting(words);

                if (CommandRow.asksWhereATrainIs(term.getKind())) return digitsOnly();
            }

            if (column == 5)
            {
                if (CommandRow.asksWhereATrainIs(term.getKind()))
                {
                    return chooseFrom(withTheCellsOwn(parent.getModel().getLocList(), term.getTarget()));
                }

                return digitsOnly();
            }

            return super.getCellEditor(line, column);
        }

        /**
         * Adds a condition with the word that joins it to what is already there, both lines at once; the word is
         * written, so one click makes it "or".
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

            RouteCommand fresh = freshCondition();

            rows.add(ConditionOutline.Row.condition(depth, fresh));

            settle();
            model.fireTableDataChanged();
            select(fresh);
        }

        /** What a new condition asks until it is filled in. */
        private RouteCommand freshCondition()
        {
            return RouteCommand.RouteCommandFeedback(1, true);
        }

        /**
         * A new condition above or below a line (FR-147), at its level and joined by its level's word.
         *
         * @param by -1 for above, 1 for below
         */
        void insertBeside(int line, int by)
        {
            if (line < 0 || line >= rows.size() || rows.get(line).isJoiner()) return;

            RouteCommand fresh = freshCondition();
            List<ConditionOutline.Row> after = ConditionOutline.inserted(rows, line, by, fresh);

            rows.clear();
            rows.addAll(after);

            settle();
            model.fireTableDataChanged();
            select(fresh);
        }

        /** Selects the line holding this very condition, so an edit leaves the changed one picked. */
        private void select(RouteCommand command)
        {
            for (int line = 0; line < rows.size(); line++)
            {
                if (rows.get(line).getCommand() == command) setRowSelectionInterval(line, line);
            }
        }

        /** The conditions on the selected lines, by identity: two lines can ask the same thing. */
        java.util.Set<RouteCommand> chosenConditions()
        {
            java.util.Set<RouteCommand> out = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<RouteCommand, Boolean>());

            for (int line : getSelectedRows())
            {
                if (line < rows.size() && !rows.get(line).isJoiner()) out.add(rows.get(line).getCommand());
            }

            return out;
        }

        void removeSelected()
        {
            removeAt(getSelectedRow());
        }

        /**
         * Removes a line, and whatever cannot stay behind without it.  A word taken out takes the term it joins on
         * (OB-240, MT-469: "Any linked entries should also be deleted"): a wordless run is legal to `tidy()` and
         * `ConditionOutline.toExpression` folds it with AND, so deleting an `or` would silently turn it into an `and`,
         * a change to when the route fires that `whatIsWrong` does not flag.
         */
        void removeAt(int line)
        {
            if (line < 0 || line >= rows.size()) return;

            boolean joiner = rows.get(line).isJoiner();

            // Before the removal, because the term a word joins on is found by comparing depths with it.
            int depth = rows.get(line).getDepth();

            rows.remove(line);

            if (!joiner)
            {
                // The word at this row's own depth, not always the one before it: the first row of a group has the
                // outer word before it, one level shallower.  Deleting the 2 of `1 or (2 and 3)` would take the `or`
                // and leave `[1(0), and(1), 3(1)]`.  A word at another depth is left for `tidy()`.
                boolean before = line - 1 >= 0 && rows.get(line - 1).isJoiner()
                    && rows.get(line - 1).getDepth() == depth;

                boolean after = line < rows.size() && rows.get(line).isJoiner()
                    && rows.get(line).getDepth() == depth;

                if (before) rows.remove(line - 1);
                else if (after) rows.remove(line);
            }
            else if (line < rows.size())
            {
                // The whole term, which may be a group: ConditionOutline.write puts a group's rows one level in, and
                // removing only the first line would leave the rest behind with its own leading word.
                if (rows.get(line).getDepth() > depth)
                {
                    while (line < rows.size() && rows.get(line).getDepth() > depth) rows.remove(line);
                }
                else
                {
                    rows.remove(line);
                }
            }

            tidy();

            settle();
            model.fireTableDataChanged();
        }

        /**
         * Moves a condition one place up or down by the rule `ConditionOutline.moved` holds, and keeps it selected.
         *
         * @param line the row that was pressed
         * @param by -1 for up, 1 for down
         */
        void shift(int line, int by)
        {
            if (!ConditionOutline.canMove(rows, line, by)) return;

            int landed = ConditionOutline.landing(rows, line, by);

            List<ConditionOutline.Row> after = ConditionOutline.moved(rows, line, by);

            rows.clear();
            rows.addAll(after);

            // No tidy(): from an outline that reads, moved() gives one that reads.
            settle();
            model.fireTableDataChanged();

            setRowSelectionInterval(landed, landed);
        }

        /**
         * The selected lines when there are several, so the indent marks act on all of them (FR-144).
         *
         * @return {first, last}, or null for one line or none
         */
        int[] chosenLines()
        {
            int[] picked = getSelectedRows();

            if (picked.length < 2) return null;

            return new int[]{Math.min(picked[0], picked[picked.length - 1]),
                Math.max(picked[0], picked[picked.length - 1])};
        }

        /**
         * The lines a block indent would move, or null where it would break the outline.  A word at either edge stays,
         * as it joins the block to what is outside; the lines nested under the last one come along.
         *
         * @param first the first chosen line
         * @param last the last chosen line
         * @param by 1 to indent, -1 to outdent
         * @return {first, last} of the lines to move
         */
        int[] block(int first, int last, int by)
        {
            last = Math.min(last, rows.size() - 1);

            while (first <= last && rows.get(first).isJoiner()) first++;
            while (last >= first && rows.get(last).isJoiner()) last--;

            if (first < 0 || first > last) return null;

            int top = Integer.MAX_VALUE;

            for (int at = first; at <= last; at++) top = Math.min(top, rows.get(at).getDepth());

            while (last + 1 < rows.size() && rows.get(last + 1).getDepth() > top) last++;

            if (by < 0 && top == 0) return null;

            if (by > 0)
            {
                int most = first == 0 ? 1 : rows.get(first - 1).getDepth() + 1;

                if (rows.get(first).getDepth() + 1 > most) return null;
            }

            return new int[]{first, last};
        }

        void indent(int line, int by)
        {
            if (line < 0 || line >= rows.size()) return;

            int[] chosen = chosenLines();

            if (chosen != null && line >= chosen[0] && line <= chosen[1])
            {
                int[] moving = block(chosen[0], chosen[1], by);

                if (moving == null) return;

                for (int at = moving[0]; at <= moving[1]; at++)
                {
                    rows.set(at, rows.get(at).atDepth(rows.get(at).getDepth() + by));
                }

                tidy();

                settle();
                model.fireTableDataChanged();

                if (!rows.isEmpty())
                {
                    setRowSelectionInterval(Math.min(moving[0], rows.size() - 1), Math.min(moving[1], rows.size() - 1));
                }

                return;
            }

            int was = rows.get(line).getDepth();
            int depth = was + by;

            if (depth < 0) return;

            // One level deeper than the line above, at most: a nesting with a hole can be neither drawn nor meant.
            if (line > 0 && depth > rows.get(line - 1).getDepth() + 1) return;

            // The first line may go one level in: a condition that starts with a group, "(2 or 3) and (1 or 4)", needs
            // it.
            if (line == 0 && depth > 1) return;

            // A word may be indented past the condition after it (building a group one row at a time does that); left
            // there, ConditionOutline.problems shows it red and Save refuses it.

            // Everything nested under this line comes with it: moving only the top would take a group apart.
            // Outdenting stops at the outermost level.
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

        /** Pulls the outline back into a shape it can have, afterwards, rather than refusing a move. */
        private void tidy()
        {
            for (int at = 0; at < rows.size(); at++)
            {
                // Row 0 keeps its own depth: a condition that begins with a bracketed group legitimately starts one
                // level in (`(A or B) and C` is `[A(1), or(1), B(1), and(0), C(0)]`).  Flattening it after deleting the
                // unrelated `C` would read back as `A and B`, flag nothing, and change when the route fires.  Row 0 has
                // no row above to be measured against.
                int most = at == 0 ? rows.get(0).getDepth() : rows.get(at - 1).getDepth() + 1;

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

        /** Works out which lines disagree with their level, and says what the outline means. */
        private void settle()
        {
            why = ConditionOutline.whatIsWrong(rows);
            flagged = new java.util.LinkedHashSet<>(why.keySet());

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
