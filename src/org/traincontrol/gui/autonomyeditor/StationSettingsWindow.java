package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.StandingRules;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.AutonomySession.WhichTrains;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The Station Settings window (FR-111, FR-176): every setting of one sensor square, beside what they allow.  Its matrix
 * asks the railway off the event thread (OB-192), and a future several-stations form (FR-052) can reuse the sections, as
 * each control reads and writes through one door.
 */
public final class StationSettingsWindow extends StationWindowBase
{
    private static final Color GRAY_ICON = new Color(170, 170, 170);
    private static final Font REGULAR = new Font("Segoe UI", Font.PLAIN, 14);

    private final JPanel form = new JPanel(new GridBagLayout());
    private int row;

    /** Each row's (i), by the row's name, whose flyover follows the value. */
    private final Map<String, JLabel> marks = new LinkedHashMap<>();

    private final JTextField name = field("station.name", 16);

    private final Map<StationPreset, JToggleButton> presets = new EnumMap<>(StationPreset.class);

    private final JLabel closedLine = new RoomyLabel();

    private final JCheckBox stops = sectionBox("station.stops", "autosetup.ui.stationSectionStop");
    private final JCheckBox fullAutonomy = box("station.fullAutonomy", "autosetup.ui.stationMayChoose");
    private final JTextField maxLength = field("station.maxLength", 5);
    private final JPanel arrivals = new RoomyPanel(new GridLayout(0, 2));
    private final JLabel exitGuard = value("station.exitGuard");
    private final JLabel entryGuard = value("station.entryGuard");

    private final JComboBox<String> home = new JComboBox<String>()
    {
        @Override
        public Dimension getPreferredSize()
        {
            return noWidth(super.getPreferredSize());
        }

        @Override
        public Dimension getMinimumSize()
        {
            return noWidth(super.getMinimumSize());
        }
    };

    private final JCheckBox runsThrough = sectionBox("station.runsThrough", "autosetup.ui.stationSectionThrough");
    private final JPanel depart = new RoomyPanel(new GridBagLayout());
    private final JRadioButton turnNever = radio("station.turnNever", "autosetup.ui.stationTurnNever");
    private final JRadioButton turnMay = radio("station.turnMay", "autosetup.ui.stationTurnMay");
    private final JRadioButton turnMust = radio("station.turnMust", "autosetup.ui.stationTurnMust");

    private final JButton advanced = new JButton(I18n.t("autosetup.ui.stationSectionAdvanced"));
    private boolean advancedOpen;
    private final List<JComponent> advancedRows = new ArrayList<>();

    private final JTextField priority = field("station.priority", 5);
    private final JTextField speed = field("station.speed", 5);
    private final JRadioButton whichAll = radio("station.whichAll", "autosetup.ui.stationWhichAll");
    private final JRadioButton whichBlack = radio("station.whichBlack", "autosetup.ui.stationWhichBlack");
    private final JRadioButton whichWhite = radio("station.whichWhite", "autosetup.ui.stationWhichWhite");
    private final JButton whichChoose = flat("station.whichChoose", "autosetup.ui.stationChoose");
    private final JLabel unavailable = value("station.unavailable");

    /** Every label and control of the stop section, and those May Depart and Changing Direction gray with. */
    private final List<JComponent> stopSection = new ArrayList<>();
    private final List<JComponent> throughSection = new ArrayList<>();
    private final List<JComponent> otherControls = new ArrayList<>();

    private final StationMatrixPanel matrix = new StationMatrixPanel();

    private final StationPicture picture = new StationPicture();

    /** How wide what is under a control, and a line across the whole form, may be. */
    private int controlWidth;

    /** Numbers each matrix question, so only the latest paints; a change of mind on a worker is dropped. */
    private final AtomicLong matrixAsked = new AtomicLong();
    private volatile long matrixPainted;

    StationSettingsWindow(AutonomyEditorPanel editor, Window owner)
    {
        super(editor, owner);

        form.setName("station.form");

        buildForm();

        lockTheWidth();

        JPanel columns = new JPanel(new BorderLayout(14, 0));

        JPanel left = new JPanel(new BorderLayout());

        left.add(form, BorderLayout.NORTH);

        columns.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 12));

        right.add(matrix, BorderLayout.NORTH);
        right.add(picture, BorderLayout.CENTER);

        JPanel rightTop = new JPanel(new BorderLayout());

        rightTop.add(right, BorderLayout.NORTH);

        columns.add(rightTop, BorderLayout.EAST);

        assemble(columns);

        stripWrapsAt = wrapWithin(form.getPreferredSize().width + 14 + matrix.getPreferredSize().width + 2 * PAD - 24);

        showAdvanced(false);
    }

    /**
     * Sizes the form once, for its widest state in this language and scale: Advanced open.  Whatever varies after this is
     * built to take the room it is given (`RoomyLabel`), so no edit moves the window's width.
     */
    private void lockTheWidth()
    {
        for (JComponent part : advancedRows) part.setVisible(true);

        form.setSize(form.getPreferredSize());
        form.doLayout();

        GridBagLayout layout = (GridBagLayout) form.getLayout();

        int[] columns = layout.getLayoutDimensions()[0];

        layout.columnWidths = columns;

        controlWidth = columns[2] + columns[3];

        int whole = 0;

        for (int each : columns) whole += each;

        closedLine.setText(AutonomyEditorPanel.wrappedAt(I18n.t("autosetup.ui.stationNothingCanPass"), wrapWithin(whole - 8)));
    }

    /** Keeps the width the window has while it is open, whatever it is asked to fit; only the height follows. */
    @Override
    public void pack()
    {
        int kept = isVisible() ? getWidth() : 0;

        super.pack();

        if (kept > 0) setSize(kept, getHeight());
    }

    // ------------------------------------------------------------------------------------------- building the form

    private void buildForm()
    {
        typedRow("autosetup.ui.stationName", null, name, name, otherControls, this::nameInStore, this::applyTypedName);

        JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        for (StationPreset preset : StationPreset.values())
        {
            JToggleButton button = new JToggleButton(I18n.t(preset.labelKey));

            button.setName("station.preset." + preset.name());
            button.setFont(BUTTON_FONT);
            button.setFocusable(false);
            button.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t(preset.tipKey)));
            button.addActionListener(e -> pressPreset(preset));

            if (!presets.isEmpty()) strip.add(javax.swing.Box.createHorizontalStrut(4));

            presets.put(preset, button);
            strip.add(button);
        }

        wide(strip);

        closedLine.setName("station.closedLine");
        closedLine.setForeground(new Color(170, 0, 0));
        closedLine.setVisible(false);
        wide(closedLine);

        section(stops, mark("stops"));
        stops.addActionListener(e -> pressBoxes());

        rowOf("autosetup.ui.menuAutoDestination", "fullAutonomy", fullAutonomy, null, stopSection);
        fullAutonomy.addActionListener(e ->
        {
            if (!reading) act(() -> { editor.session.setAutoDestination(tile, fullAutonomy.isSelected()); return true; });
        });

        typedRow("autosetup.ui.stationMaxLength", "maxLength", maxLength, leftAligned(maxLength), stopSection,
            this::maxLengthInStore, typed -> editor.applyNumber(tile, "maxTrainLength", blankIsZero(typed), 0));

        rowOf("autosetup.ui.menuArrivalsGroup", "arrivals", arrivals, null, stopSection);

        JButton exitGuardChange = flat("station.exitGuardChange", "autosetup.ui.stationChange");

        rowOf("autosetup.ui.stationExitGuard", "exitGuard", exitGuard, exitGuardChange, stopSection);
        exitGuardChange.addActionListener(e -> pressGuard(AutonomyEditorPanel.Guard.EXIT));

        JButton entryGuardChange = flat("station.entryGuardChange", "autosetup.ui.stationChange");

        rowOf("autosetup.ui.stationEntryGuard", "entryGuard", entryGuard, entryGuardChange, stopSection);
        entryGuardChange.addActionListener(e -> pressGuard(AutonomyEditorPanel.Guard.ENTRY));

        home.setName("station.home");
        home.addActionListener(e -> pressHome());
        rowOf("autosetup.ui.stationHome", null, home, null, stopSection);

        section(runsThrough, mark("runsThrough"));
        runsThrough.addActionListener(e -> pressBoxes());

        rowOf("autosetup.ui.stationDepart", "depart", depart, null, throughSection);

        ActionListener turned = e -> pressTurning();

        turnNever.addActionListener(turned);
        turnMay.addActionListener(turned);
        turnMust.addActionListener(turned);

        rowOf("autosetup.ui.stationTurning", "turning", oneOf(throughSection, turnNever, turnMay, turnMust), null, throughSection);

        advanced.setName("station.advanced");
        advanced.setIcon(RowIcons.arrow(14, false));
        advanced.setFont(UiStyle.LABEL_FONT);
        advanced.setForeground(UiStyle.BLUE);
        advanced.setFocusable(false);

        // A heading that folds, not a button: no band behind it, hovered, pressed or open, and on the labels' left edge
        advanced.setContentAreaFilled(false);
        advanced.setBorderPainted(false);
        advanced.setRolloverEnabled(false);
        advanced.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        advanced.setHorizontalAlignment(JButton.LEFT);
        advanced.addActionListener(e -> showAdvanced(!advancedOpen));

        GridBagConstraints heading = at(0, row++);

        heading.gridwidth = 4;
        heading.insets = new Insets(10, 0, 3, 0);

        form.add(advanced, heading);

        int first = row;

        typedRow("autosetup.ui.stationPriority", "priority", priority, leftAligned(priority), otherControls,
            this::priorityInStore, typed -> editor.applyNumber(tile, "priority", blankIsZero(typed), 0));

        JPanel speedRow = leftAligned(speed);

        speedRow.add(javax.swing.Box.createHorizontalStrut(4));
        speedRow.add(new JLabel("%"));

        typedRow("autosetup.ui.stationSpeed", "speed", speed, speedRow, otherControls,
            () -> String.valueOf(editor.percent(tile)), typed -> editor.applyPercent(tile, typed));

        whichAll.addActionListener(e -> pressWhich(WhichTrains.ALL));
        whichBlack.addActionListener(e -> pressWhich(WhichTrains.BLACKLIST));
        whichWhite.addActionListener(e -> pressWhich(WhichTrains.WHITELIST));
        whichChoose.addActionListener(e -> pressChoose());

        rowOf("autosetup.ui.stationWhich", "which", oneOf(otherControls, whichAll, whichBlack, whichWhite), whichChoose, otherControls);

        JButton unavailableChoose = flat("station.unavailableChoose", "autosetup.ui.stationChoose");

        unavailableChoose.addActionListener(e -> actAlone(() -> editor.promptBlockingPoints(tile)));

        rowOf("autosetup.ui.stationUnavailable", "unavailable", unavailable, unavailableChoose, otherControls);

        // Everything added since the heading folds with it
        for (java.awt.Component part : form.getComponents())
        {
            GridBagConstraints c = ((GridBagLayout) form.getLayout()).getConstraints(part);

            if (c.gridy >= first && part != advanced) advancedRows.add((JComponent) part);
        }

        arrivals.setName("station.arrivals");
        depart.setName("station.depart");

        // The keys that do these without the window; the track diagram has none
        if (!editor.menuOnly)
        {
            name.setToolTipText(AutonomyEditorPanel.SHORTCUT_NAME);
            home.setToolTipText(AutonomyEditorPanel.SHORTCUT_HOME);
            maxLength.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipMaxTrainLengthKey")));
        }
    }

    /** A row's (i), made the first time it is asked for. */
    private JLabel mark(String rowName)
    {
        return marks.computeIfAbsent(rowName, unused -> InfoMark.label(""));
    }

    private void fly(String rowName, String sentence)
    {
        mark(rowName).setToolTipText(sentence);
    }

    /** A label, its (i), a control and a flat button, in one row; the label and control gray with the section. */
    private void rowOf(String labelKey, String markName, JComponent control, JComponent button, List<JComponent> section)
    {
        JLabel label = UiStyle.blackLabel(I18n.t(labelKey));

        formRow(form, row, label, markName == null ? null : mark(markName), control, button == null ? 2 : 1);

        if (button != null) form.add(button, at(3, row));

        section.add(label);
        section.add(control);

        if (button != null) section.add(button);

        row++;
    }

    /** A row whose field applies on Enter or leaving, with a line under it for a refusal. */
    private void typedRow(String labelKey, String markName, JTextField part, JComponent shown, List<JComponent> section,
        Supplier<String> stored, Function<String, String> door)
    {
        rowOf(labelKey, markName, shown, null, section);

        if (shown != part) section.add(part);

        JLabel problem = problemLine(part.getName() + "Problem");

        GridBagConstraints c = at(2, row++);

        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 3, 0);

        form.add(problem, c);

        problems.put(part, problem);
        typedDoors.put(part, door);

        commitsOnLeaving(part, stored);
    }

    /** Radio buttons sharing a row, and graying with this section. */
    private static JPanel oneOf(List<JComponent> section, JRadioButton... answers)
    {
        ButtonGroup group = new ButtonGroup();

        JPanel out = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        for (JRadioButton each : answers)
        {
            group.add(each);
            out.add(each);
            section.add(each);
        }

        return out;
    }

    private void section(JCheckBox box, JLabel sectionMark)
    {
        GridBagConstraints c = at(0, row);

        c.insets = new Insets(10, 0, 3, 8);

        form.add(box, c);

        GridBagConstraints m = at(1, row);

        m.insets = new Insets(10, 0, 3, 8);

        form.add(sectionMark, m);

        row++;
    }

    private static JPanel leftAligned(JComponent part)
    {
        JPanel out = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        out.add(part);

        return out;
    }

    private void wide(JComponent part)
    {
        GridBagConstraints c = at(0, row++);

        c.gridwidth = 4;
        c.fill = GridBagConstraints.HORIZONTAL;

        form.add(part, c);
    }

    private static JLabel problemLine(String partName)
    {
        JLabel out = new RoomyLabel();

        out.setName(partName);
        out.setForeground(new Color(170, 0, 0));
        out.setVisible(false);

        return out;
    }

    private static JTextField field(String partName, int columns)
    {
        return styled(new JTextField(columns), partName, REGULAR);
    }

    private static JLabel value(String partName)
    {
        return styled(new RoomyLabel(), partName, REGULAR);
    }

    private static JCheckBox box(String partName, String key)
    {
        return styled(new JCheckBox(I18n.t(key)), partName, REGULAR);
    }

    private static JCheckBox sectionBox(String partName, String key)
    {
        JCheckBox out = styled(new JCheckBox(I18n.t(key)), partName, UiStyle.LABEL_FONT);

        out.setForeground(UiStyle.BLUE);

        return out;
    }

    private static JRadioButton radio(String partName, String key)
    {
        return styled(new JRadioButton(I18n.t(key)), partName, REGULAR);
    }

    /** A pencil with no border until hovered, as the route editor's strip; what it does is in its tooltip. */
    private static JButton flat(String partName, String tipKey)
    {
        JButton out = new JButton(RowIcons.pencil(16));

        out.setDisabledIcon(RowIcons.pencil(16, GRAY_ICON));
        out.setName(partName);
        out.putClientProperty("JButton.buttonType", "toolBarButton");
        out.setFocusable(false);
        out.setToolTipText(I18n.t(tipKey));

        return out;
    }

    private static String blankIsZero(String typed)
    {
        return typed.trim().isEmpty() ? "0" : typed;
    }

    // ----------------------------------------------------------------------------------------- fields apply on leaving

    private final Map<JTextField, Function<String, String>> typedDoors = new LinkedHashMap<>();
    private final Map<JTextField, Supplier<String>> storedValues = new LinkedHashMap<>();
    private final Map<JTextField, JLabel> problems = new LinkedHashMap<>();

    /** Enter or leaving applies; Escape puts back what it held.  Never per keystroke, as each change rebuilds the railway. */
    private void commitsOnLeaving(JTextField part, Supplier<String> stored)
    {
        storedValues.put(part, stored);

        part.addActionListener(e -> commit(part));

        part.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusLost(java.awt.event.FocusEvent e)
            {
                if (!e.isTemporary()) commit(part);
            }
        });

        part.registerKeyboardAction(e ->
        {
            part.setText(stored.get());

            showProblem(part, null);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    }

    private void commit(JTextField part)
    {
        if (reading || tile == null || !part.isEditable() || !part.isEnabled()) return;

        String typed = part.getText();

        // Unchanged writes nothing: an untouched station opened and closed changes no setup
        if (typed.trim().equals(storedValues.get(part).get().trim())) { showProblem(part, null); return; }

        final String[] refused = new String[1];

        act(() ->
        {
            refused[0] = typedDoors.get(part).apply(typed);

            return refused[0] == null;
        });

        showProblem(part, refused[0]);

        // Refused, the field keeps what was typed, so it can be fixed
        if (refused[0] != null) part.setText(typed);
    }

    private void showProblem(JTextField part, String why)
    {
        JLabel line = problems.get(part);

        if (line != null)
        {
            line.setText(why == null ? "" : AutonomyEditorPanel.wrappedAt(why, wrapWithin(controlWidth - 8)));
            line.setVisible(why != null);
        }

        part.putClientProperty("JComponent.outline", why == null ? null : "error");

        if (isVisible() && getPreferredSize().height > getHeight()) pack();
    }

    @Override
    void commitFields()
    {
        if (tile == null) return;

        for (JTextField part : new ArrayList<>(storedValues.keySet())) commit(part);
    }

    private String applyTypedName(String typed)
    {
        // No quotes, as `promptName` takes them out
        return editor.applyName(tile, typed.replace("\"", ""));
    }

    private String nameInStore()
    {
        String stored = editor.session.getStore().getPointName(tile);

        return stored == null ? "" : stored;
    }

    private String maxLengthInStore()
    {
        int stored = editor.number(tile, "maxTrainLength", 0);

        return stored == 0 ? "" : String.valueOf(stored);
    }

    private String priorityInStore()
    {
        return String.valueOf(editor.number(tile, "priority", 0));
    }

    // ------------------------------------------------------------------------------------------------- reading

    @Override
    void fill(boolean readOnly)
    {
        AutonomySession session = editor.session;

        setTitle(I18n.f("autosetup.ui.titleStationSettings", editor.describeTile(tile)));

        StationBoxes boxes = StationBoxes.of(session, tile);

        boolean must = session.isMustTurnAround(tile);
        boolean may = session.isTurnAround(tile) && !must;

        keepTyping(name, nameInStore());

        StationPreset lit = StationPreset.matching(session, tile);

        for (Map.Entry<StationPreset, JToggleButton> each : presets.entrySet())
        {
            each.getValue().setSelected(each.getKey() == lit);
            each.getValue().setEnabled(!readOnly);
        }

        closedLine.setVisible(!boxes.stops && !boxes.runsThrough);

        stops.setSelected(boxes.stops);
        stops.setEnabled(!readOnly);
        fly("stops", StationFlyovers.stops(boxes.stops));

        runsThrough.setSelected(boxes.runsThrough);
        runsThrough.setEnabled(!readOnly);
        fly("runsThrough", StationFlyovers.through(boxes, must));

        boolean autonomy = session.isAutoDestination(tile);

        fullAutonomy.setSelected(autonomy);
        fly("fullAutonomy", StationFlyovers.fullAutonomy(autonomy));

        keepTyping(maxLength, maxLengthInStore());
        fly("maxLength", StationFlyovers.maxLength(editor.number(tile, "maxTrainLength", 0)));

        fillArrivals(boxes.stops && !readOnly);

        fillGuard(exitGuard, "exitGuard", session.getProtectingSignals(tile), true);
        fillGuard(entryGuard, "entryGuard", session.getEntrySignals(tile), false);

        fillHome();

        fillDepart(!readOnly && (boxes.stops || boxes.runsThrough));

        turnNever.setSelected(!may && !must);
        turnMay.setSelected(may);
        turnMust.setSelected(must);
        fly("turning", StationFlyovers.turning(may, must));

        keepTyping(priority, priorityInStore());
        fly("priority", StationFlyovers.priority(editor.number(tile, "priority", 0)));

        keepTyping(speed, String.valueOf(editor.percent(tile)));
        fly("speed", StationFlyovers.speed(editor.percent(tile)));

        WhichTrains which = session.getWhichTrains(tile);

        whichAll.setSelected(which == WhichTrains.ALL);
        whichBlack.setSelected(which == WhichTrains.BLACKLIST);
        whichWhite.setSelected(which == WhichTrains.WHITELIST);
        fly("which", StationFlyovers.whichTrains(which, session.getTrainList(tile).size()));

        String blockers = namesOf(session.getStore().getBlockingPoints(tile));

        unavailable.setText(blockers == null ? I18n.t("autosetup.ui.labelNone") : blockers);
        fly("unavailable", StationFlyovers.unavailable(blockers));

        enable(stopSection, boxes.stops && !readOnly);
        enable(throughSection, !readOnly && (boxes.stops || boxes.runsThrough));
        enable(otherControls, !readOnly);

        // A field grayed by a section still shows its value
        whichChoose.setEnabled(!readOnly && which != WhichTrains.ALL);

        picture.show(session, tile, editor.parentWindow());

        askTheRailway();
    }

    /** A value read from the setup, except over a field showing a refusal, which keeps what was typed. */
    private void keepTyping(JTextField part, String stored)
    {
        if (!problems.get(part).isVisible()) part.setText(stored);
    }

    private static void enable(List<JComponent> parts, boolean on)
    {
        for (JComponent part : parts)
        {
            part.setEnabled(on);

            if (part instanceof JTextField) ((JTextField) part).setEditable(on);
        }
    }

    private void fillArrivals(boolean live)
    {
        AutonomySession session = editor.session;

        List<TilePorts.Side> ways = session.arrivalSides(tile);
        java.util.Set<TilePorts.Side> barred = session.getBarredArrivals(tile);

        arrivals.removeAll();

        List<String> open = new ArrayList<>();
        List<String> all = new ArrayList<>();

        for (final TilePorts.Side side : ways)
        {
            String sideName = I18n.t("autosetup.ui.side" + side.name());

            all.add(sideName);

            if (!barred.contains(side)) open.add(sideName);

            final JCheckBox allow = new JCheckBox(I18n.f("autosetup.ui.stationArriveFrom", sideName), !barred.contains(side));

            allow.setName("station.arrive." + side.name());
            allow.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            // The last way in cannot be shut: unticking one at a time is the same as barring a single-sided station
            allow.setEnabled(live && ways.size() > 1 && (barred.contains(side)
                || session.arrivalsCanBeClosed(tile, java.util.Collections.singleton(side))));

            if (ways.size() == 1)
            {
                allow.setToolTipText(AutonomyEditorPanel.wrapped(I18n.f("autosetup.ui.hintOneWayIn", sideName)));
            }

            allow.addActionListener(e ->
            {
                if (!reading) act(() -> { editor.setArrivalAllowed(tile, side, allow.isSelected()); return true; });
            });

            arrivals.add(allow);
        }

        fly("arrivals", StationFlyovers.arrivals(open, all));

        arrivals.revalidate();
    }

    private void fillGuard(JLabel shown, String markName, List<TileKey> signals, boolean exit)
    {
        String addresses = signals.isEmpty() ? null : editor.signalAddresses(signals);

        shown.setText(addresses == null ? I18n.t("autosetup.ui.labelNone") : addresses);
        fly(markName, exit ? StationFlyovers.exitGuard(addresses) : StationFlyovers.entryGuard(addresses));
    }

    private void fillHome()
    {
        String current = editor.homeOf(tile);

        List<String> names = AutonomyEditorPanel.homeChoices(I18n.t("autosetup.ui.labelNone"), editor.placedLocomotives(), current);

        home.setModel(new DefaultComboBoxModel<>(names.toArray(new String[0])));
        home.setSelectedItem(current == null ? names.get(0) : current);
    }

    private void fillDepart(boolean live)
    {
        AutonomySession session = editor.session;

        depart.removeAll();

        Map<RouteId, TilePorts.Route> routes = session.getRoutes(tile);

        boolean carries = session.canCarryDirection(tile);

        int y = 0;

        String said = null;

        for (final Map.Entry<RouteId, TilePorts.Route> entry : routes.entrySet())
        {
            final TilePorts.Route route = entry.getValue();

            final List<Direction> answers = new ArrayList<>();
            final List<String> labels = new ArrayList<>();

            if (TileGraph.directionIsPossible(Direction.BOTH, route))
            {
                answers.add(Direction.BOTH);
                labels.add(I18n.t("autosetup.ui.menuRouteBoth"));
            }

            if (TileGraph.directionIsPossible(Direction.TOWARD_A, route))
            {
                answers.add(Direction.TOWARD_A);
                labels.add(I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getA())));
            }

            if (TileGraph.directionIsPossible(Direction.TOWARD_B, route))
            {
                answers.add(Direction.TOWARD_B);
                labels.add(I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getB())));
            }

            answers.add(Direction.NONE);
            labels.add(I18n.t("autosetup.ui.menuRouteNone"));

            Direction current = TileGraph.inForce(session.directionAsDrawn(tile, entry.getKey()), route);

            final JComboBox<String> choice = new JComboBox<>(labels.toArray(new String[0]));

            choice.setName("station.depart." + y);
            choice.setEnabled(live && carries);

            if (answers.contains(current)) choice.setSelectedIndex(answers.indexOf(current));

            if (said == null) said = String.valueOf(choice.getSelectedItem());

            choice.addActionListener(e ->
            {
                if (reading) return;

                final Direction picked = answers.get(choice.getSelectedIndex());

                actAlone(() -> editor.setRunDirection(tile, entry.getKey(), picked));
            });

            GridBagConstraints c = at(0, y++);

            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 0, 0);

            depart.add(choice, c);
        }

        fly("depart", StationFlyovers.depart(said == null ? I18n.t("autosetup.ui.labelNone") : said));

        depart.revalidate();
    }

    private String namesOf(List<TileKey> squares)
    {
        if (squares.isEmpty()) return null;

        List<String> names = new ArrayList<>();

        for (TileKey each : squares) names.add(editor.describeTile(each));

        return String.join(", ", names);
    }

    // ------------------------------------------------------------------------------------------------- pressing

    private void pressPreset(StationPreset preset)
    {
        if (reading) return;

        act(() -> editor.applyPreset(tile, preset));
    }

    private void pressBoxes()
    {
        if (reading) return;

        final boolean wantsStops = stops.isSelected();
        final boolean wantsThrough = runsThrough.isSelected();

        act(() -> editor.setUsage(tile, wantsStops, wantsThrough));
    }

    private void pressGuard(AutonomyEditorPanel.Guard guard)
    {
        // The track diagram has no grid to click a signal on: the editor opens at this square
        actAlone(() ->
        {
            if (!editor.needsTheGrid(tile)) editor.pairGuardSignals(guard, tile);
        });
    }

    private void pressHome()
    {
        if (reading) return;

        final String chosen = (String) home.getSelectedItem();
        final String picked = home.getSelectedIndex() <= 0 ? null : chosen;

        if (java.util.Objects.equals(picked, editor.homeOf(tile))) return;

        act(() -> { editor.applyHome(tile, picked); return true; });
    }

    private void pressTurning()
    {
        if (reading) return;

        final boolean may = turnMay.isSelected();
        final boolean must = turnMust.isSelected();

        act(() -> { editor.setTurning(tile, may, must); return true; });
    }

    private void pressWhich(WhichTrains which)
    {
        if (reading || which == editor.session.getWhichTrains(tile)) return;

        // Either list picked with nothing on it opens the list (FR-111)
        act(() -> which != WhichTrains.ALL && editor.session.getTrainList(tile).isEmpty()
            ? editor.promptTrainList(tile, which) : editor.setWhichTrains(tile, which));
    }

    private void pressChoose()
    {
        final WhichTrains which = editor.session.getWhichTrains(tile);

        act(() -> editor.promptTrainList(tile, which));
    }

    private void showAdvanced(boolean open)
    {
        advancedOpen = open;

        advanced.setIcon(RowIcons.arrow(14, open));

        for (JComponent part : advancedRows) part.setVisible(open);

        // A refusal line stays hidden until there is one
        if (open)
        {
            problems.get(priority).setVisible(false);
            problems.get(speed).setVisible(false);
        }

        if (isVisible()) pack();
    }

    // ------------------------------------------------------------------------------------------------- the matrix

    /** Asks the railway what this square's settings allow, on a worker (OB-192), and paints the latest answer. */
    private void askTheRailway()
    {
        final long asked = matrixAsked.incrementAndGet();

        final Layout railway = editor.builtRailway();

        final List<String> copies = new ArrayList<>(editor.session.getStationIndex().pointNamesAt(tile));

        final String error = editor.whyTheSetupStopsThisSquare(tile);

        final String line = alsoLine();

        editor.offTheEventThread(() ->
        {
            StandingRules answer = null;

            try
            {
                answer = error == null && railway != null ? railway.standingRulesAt(copies) : null;
            }
            finally
            {
                final StandingRules rules = answer;

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    if (asked != matrixAsked.get()) return;

                    String shown = error != null || rules != null ? error : I18n.t("autosetup.ui.stationMatrixNotBuilt");

                    matrix.show(rules, shown, line);

                    picture.showWays(error == null ? rules : null);

                    matrixPainted = asked;

                    if (getPreferredSize().height > getHeight()) pack();
                });
            }
        });
    }

    /** One line for what a cell cannot say: the maximum length, the list and Unavailable While. */
    private String alsoLine()
    {
        AutonomySession session = editor.session;

        if (!StationBoxes.of(session, tile).stops) return null;

        List<String> parts = new ArrayList<>();

        int length = editor.number(tile, "maxTrainLength", 0);

        if (length > 0) parts.add(I18n.f("autosetup.ui.stationAlsoLength", length));

        int listed = session.getTrainList(tile).size();

        WhichTrains which = session.getWhichTrains(tile);

        if (which == WhichTrains.BLACKLIST) parts.add(I18n.f("autosetup.ui.stationAlsoBlack", listed));

        if (which == WhichTrains.WHITELIST) parts.add(I18n.f("autosetup.ui.stationAlsoWhite", listed));

        String blockers = namesOf(session.getStore().getBlockingPoints(tile));

        if (blockers != null) parts.add(I18n.f("autosetup.ui.stationAlsoBlocked", blockers));

        return parts.isEmpty() ? null : String.join("  ", parts);
    }

    // ------------------------------------------------------------------------------------------------- for tests

    /** @return whether the matrix shows the answer to the latest question */
    public boolean matrixIsCurrent()
    {
        return matrixPainted == matrixAsked.get();
    }

    /** @return what the picture of the station wears */
    public org.traincontrol.automationui.TileAnnotation pictureForTest()
    {
        return picture.drawnForTest();
    }

    /** @return the sides the picture draws a way out of the station at, or a way in */
    public java.util.Set<String> pictureWaysForTest(boolean out)
    {
        return picture.waysForTest(out);
    }

    /** @return the square the window is about */
    public TileKey squareForTest()
    {
        return tile;
    }

    /** What the matrix draws for a row and tier ("auto", "hand", or "both"): side to answer; empty where every cell says No. */
    public Map<String, StandingRules.Answer> drawnForTest(StandingRules.Row row, String tier)
    {
        return matrix.drawnFor(row, tier);
    }

    /** @return the preset lit now, or null */
    public StationPreset litPresetForTest()
    {
        for (Map.Entry<StationPreset, JToggleButton> each : presets.entrySet())
        {
            if (each.getValue().isSelected()) return each.getKey();
        }

        return null;
    }

    /** Opens Advanced, as its heading does. */
    public void openAdvancedForTest()
    {
        advanced.doClick();
    }

    /** @return the sentence the mark in this row says now, by the row's field */
    public String flyoverForTest(String markName)
    {
        return mark(markName).getToolTipText();
    }
}
