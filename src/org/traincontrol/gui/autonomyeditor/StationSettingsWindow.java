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
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
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
 * asks the railway off the event thread (OB-192).  About several squares picked together (FR-052) it has no matrix,
 * picture or per-station rows; a field they disagree on says "(mixed)" and is left alone, and a change goes to each
 * square through the door it takes for one.
 */
public final class StationSettingsWindow extends StationWindowBase
{
    private static final Color GRAY_ICON = new Color(170, 170, 170);
    private static final Font REGULAR = new Font("Segoe UI", Font.PLAIN, 14);
    private static final String MIXED = "autosetup.ui.stationMixed";

    private static final int NEVER = 0;
    private static final int MAY = 1;
    private static final int MUST = 2;

    /** The squares picked together, or empty for one; `tile` is the first of them. */
    private List<TileKey> several = Collections.emptyList();

    private final JPanel form = new JPanel(new GridBagLayout());
    private int row;

    /** The form's width once locked, for a line across it. */
    private int formWidth;

    private final JLabel severalLine = new RoomyLabel();

    private final JLabel stopsMixed = mixedLabel("station.stopsMixed");
    private final JLabel throughMixed = mixedLabel("station.runsThroughMixed");
    private final JLabel autoMixed = mixedLabel("station.fullAutonomyMixed");
    private final JLabel turnMixed = mixedLabel("station.turningMixed");
    private final JLabel whichMixed = mixedLabel("station.whichMixed");

    /** The rows that are about one station alone, left out for several: its ways, signals, home and blockers. */
    private final List<JComponent> perStation = new ArrayList<>();

    /** The matrix and picture's column. */
    private final JPanel sideColumn = new JPanel(new BorderLayout());

    /** Where the reason at the top wraps, with the side column and without. */
    private int stripWide;
    private int stripNarrow;

    /** Whether the last pack was for several squares; the width is kept only while that stays so. */
    private Boolean packedSeveral;

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

        sideColumn.add(right, BorderLayout.NORTH);

        columns.add(sideColumn, BorderLayout.EAST);

        assemble(columns);

        stripNarrow = wrapWithin(form.getPreferredSize().width + 2 * PAD - 24);
        stripWide = wrapWithin(form.getPreferredSize().width + 14 + matrix.getPreferredSize().width + 2 * PAD - 24);
        stripWrapsAt = stripWide;

        showAdvanced(false);
    }

    /**
     * Points the window at these squares: one is that station's window, several are set together (FR-052).  What was
     * being typed applies to the squares it was typed for, never to the next ones.
     */
    void aimAt(List<TileKey> squares)
    {
        commitFields();

        List<TileKey> before = tile == null ? Collections.<TileKey>emptyList() : new ArrayList<>(targets());

        several = squares.size() > 1 ? new ArrayList<>(squares) : Collections.<TileKey>emptyList();

        stripWrapsAt = isSeveral() ? stripNarrow : stripWide;

        // A refusal, and the text it kept, belong to the squares it was typed for (OB-415)
        if (!before.equals(squares))
        {
            for (JTextField part : problems.keySet()) showProblem(part, null);

            sayOnTheStrip(null);
        }

        boolean was = reading;

        reading = true;

        try
        {
            aim(squares.get(0));
        }
        finally
        {
            reading = was;
        }
    }

    /** @return whether the window is about several squares */
    boolean isSeveral()
    {
        return !several.isEmpty();
    }

    /** The squares a change goes to. */
    private List<TileKey> targets()
    {
        return isSeveral() ? several : Collections.singletonList(tile);
    }

    /** The value every square has, or null where they differ. */
    private <T> T agreed(Function<TileKey, T> read)
    {
        T first = read.apply(tile);

        for (TileKey each : targets())
        {
            if (!Objects.equals(first, read.apply(each))) return null;
        }

        return first;
    }

    /** `actOnEach` for a change every square takes. */
    private void actOnEach(Predicate<TileKey> door)
    {
        actOnEach(each -> true, door);
    }

    /**
     * A press for every square, in one gesture: each through the door one square's press takes, those `needs` passes
     * over left as they are.  A door answering false was refused or canceled, which stops the rest; where some had
     * changed, the strip says how many.
     */
    private void actOnEach(Predicate<TileKey> needs, Predicate<TileKey> door)
    {
        final List<TileKey> squares = new ArrayList<>(targets());
        final int[] changed = {0};
        final boolean[] stopped = {false};

        sayOnTheStrip(null);

        act(() ->
        {
            editor.oneOfSeveral = squares.size() > 1;

            try
            {
                for (TileKey each : squares)
                {
                    if (!needs.test(each)) continue;

                    if (!door.test(each))
                    {
                        stopped[0] = true;

                        break;
                    }

                    changed[0]++;
                }
            }
            finally
            {
                editor.oneOfSeveral = false;
            }

            return changed[0] > 0;
        });

        if (stopped[0] && changed[0] > 0 && squares.size() > 1)
        {
            sayOnTheStrip(I18n.f("autosetup.ui.stationPartlyChanged", changed[0], squares.size()));
        }
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

        formWidth = whole;

        closedLine.setText(AutonomyEditorPanel.wrappedAt(I18n.t("autosetup.ui.stationNothingCanPass"), wrapWithin(whole - 8)));
    }

    /**
     * Keeps the width the window has while it is open, whatever it is asked to fit; only the height follows.  Going from
     * one square to several or back fits the width again, as the side column comes or goes.
     */
    @Override
    public void pack()
    {
        int kept = isVisible() && Boolean.valueOf(isSeveral()).equals(packedSeveral) ? getWidth() : 0;

        super.pack();

        packedSeveral = isSeveral();

        if (kept > 0) setSize(kept, getHeight());
    }

    /** A picked square that has gone (its page left out, say) is dropped from the picks; the window goes with the last. */
    @Override
    boolean stillAbout()
    {
        List<TileKey> left = new ArrayList<>();

        for (TileKey each : targets())
        {
            if (editor.stationWindows.stillAbout(each)) left.add(each);
        }

        if (left.size() == targets().size()) return true;

        if (!isSeveral() || left.isEmpty())
        {
            editor.keepOnlyTheseStationPicks(left);

            return false;
        }

        several = left.size() > 1 ? left : Collections.<TileKey>emptyList();
        tile = left.get(0);

        editor.keepOnlyTheseStationPicks(left);

        return true;
    }

    @Override
    void watched()
    {
        int before = targets().size();

        super.watched();

        if (tile != null && targets().size() != before) reload();
    }

    // ------------------------------------------------------------------------------------------- building the form

    private void buildForm()
    {
        severalLine.setName("station.several");
        severalLine.setFont(REGULAR);
        severalLine.setForeground(Color.BLACK);
        severalLine.setVisible(false);
        wide(severalLine);

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
        closedLine.setFont(REGULAR);
        closedLine.setForeground(new Color(170, 0, 0));
        closedLine.setVisible(false);
        wide(closedLine);

        section(stops, mark("stops"), stopsMixed);
        stops.addActionListener(e -> pressBoxes(stops));

        JPanel autonomyRow = leftAligned(fullAutonomy);

        autonomyRow.add(autoMixed);

        rowOf("autosetup.ui.menuAutoDestination", "fullAutonomy", autonomyRow, null, stopSection);
        stopSection.add(fullAutonomy);
        fullAutonomy.addActionListener(e ->
        {
            // Through `act` itself, which the editor's surface rules read as announcing the write
            if (!reading) act(() ->
            {
                for (TileKey each : targets()) editor.session.setAutoDestination(each, fullAutonomy.isSelected());

                return true;
            });
        });

        typedRow("autosetup.ui.stationMaxLength", "maxLength", maxLength, leftAligned(maxLength), stopSection,
            this::maxLengthInStore, (each, typed) -> editor.applyNumber(each, "maxTrainLength", blankIsZero(typed), 0));

        rowOf("autosetup.ui.menuArrivalsGroup", "arrivals", arrivals, null, stopSection);
        perStation.addAll(rowsFrom(row - 1));

        JButton exitGuardChange = flat("station.exitGuardChange", "autosetup.ui.stationChange");

        rowOf("autosetup.ui.stationExitGuard", "exitGuard", exitGuard, exitGuardChange, stopSection);
        perStation.addAll(rowsFrom(row - 1));
        exitGuardChange.addActionListener(e -> pressGuard(AutonomyEditorPanel.Guard.EXIT));

        JButton entryGuardChange = flat("station.entryGuardChange", "autosetup.ui.stationChange");

        rowOf("autosetup.ui.stationEntryGuard", "entryGuard", entryGuard, entryGuardChange, stopSection);
        perStation.addAll(rowsFrom(row - 1));
        entryGuardChange.addActionListener(e -> pressGuard(AutonomyEditorPanel.Guard.ENTRY));

        home.setName("station.home");
        home.addActionListener(e -> pressHome());
        rowOf("autosetup.ui.stationHome", null, home, null, stopSection);
        perStation.addAll(rowsFrom(row - 1));

        section(runsThrough, mark("runsThrough"), throughMixed);
        runsThrough.addActionListener(e -> pressBoxes(runsThrough));

        rowOf("autosetup.ui.stationDepart", "depart", depart, null, throughSection);
        perStation.addAll(rowsFrom(row - 1));

        ActionListener turned = e -> pressTurning();

        turnNever.addActionListener(turned);
        turnMay.addActionListener(turned);
        turnMust.addActionListener(turned);

        JPanel turning = oneOf(throughSection, turnNever, turnMay, turnMust);

        turning.add(turnMixed);

        rowOf("autosetup.ui.stationTurning", "turning", turning, null, throughSection);

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
            this::priorityInStore, (each, typed) -> editor.applyNumber(each, "priority", blankIsZero(typed), 0));

        JPanel speedRow = leftAligned(speed);

        speedRow.add(javax.swing.Box.createHorizontalStrut(4));
        speedRow.add(new JLabel("%"));

        typedRow("autosetup.ui.stationSpeed", "speed", speed, speedRow, otherControls,
            each -> String.valueOf(editor.percent(each)), (each, typed) -> editor.applyPercent(each, typed));

        whichAll.addActionListener(e -> pressWhich(WhichTrains.ALL));
        whichBlack.addActionListener(e -> pressWhich(WhichTrains.BLACKLIST));
        whichWhite.addActionListener(e -> pressWhich(WhichTrains.WHITELIST));
        whichChoose.addActionListener(e -> pressChoose());

        JPanel which = oneOf(otherControls, whichAll, whichBlack, whichWhite);

        which.add(whichMixed);

        rowOf("autosetup.ui.stationWhich", "which", which, whichChoose, otherControls);

        // Each station's own list: several are given theirs by the radios, which open an empty one
        perStation.add(whichChoose);

        JButton unavailableChoose = flat("station.unavailableChoose", "autosetup.ui.stationChoose");

        unavailableChoose.addActionListener(e -> actAlone(() -> editor.promptBlockingPoints(tile)));

        rowOf("autosetup.ui.stationUnavailable", "unavailable", unavailable, unavailableChoose, otherControls);
        perStation.addAll(rowsFrom(row - 1));

        // Everything added since the heading folds with it
        for (JComponent part : rowsFrom(first))
        {
            if (part != advanced) advancedRows.add(part);
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

    /** The form's parts from this row down. */
    private List<JComponent> rowsFrom(int first)
    {
        List<JComponent> out = new ArrayList<>();

        for (java.awt.Component part : form.getComponents())
        {
            if (((GridBagLayout) form.getLayout()).getConstraints(part).gridy >= first) out.add((JComponent) part);
        }

        return out;
    }

    /**
     * A row whose field applies on Enter or leaving, with a line under it for a refusal.  `stored` reads a square's
     * value as the field shows it; `door` writes what was typed to a square, answering why not or null.
     */
    private void typedRow(String labelKey, String markName, JTextField part, JComponent shown, List<JComponent> section,
        Function<TileKey, String> stored, BiFunction<TileKey, String, String> door)
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

    private void section(JCheckBox box, JLabel sectionMark, JLabel mixed)
    {
        GridBagConstraints c = at(0, row);

        c.insets = new Insets(10, 0, 3, 8);

        form.add(box, c);

        GridBagConstraints m = at(1, row);

        m.insets = new Insets(10, 0, 3, 8);

        form.add(sectionMark, m);

        GridBagConstraints x = at(2, row);

        x.insets = m.insets;

        form.add(mixed, x);

        row++;
    }

    /** "(mixed)", shown beside a box or radios the squares picked disagree on. */
    private static JLabel mixedLabel(String partName)
    {
        JLabel out = styled(new JLabel(I18n.t(MIXED)), partName, REGULAR);

        out.setForeground(Color.BLACK);
        out.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 6, 0, 0));
        out.setVisible(false);

        return out;
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

    private final Map<JTextField, BiFunction<TileKey, String, String>> typedDoors = new LinkedHashMap<>();
    private final Map<JTextField, Function<TileKey, String>> storedValues = new LinkedHashMap<>();
    private final Map<JTextField, JLabel> problems = new LinkedHashMap<>();

    /** Enter or leaving applies; Escape puts back what it held.  Never per keystroke, as each change rebuilds the railway. */
    private void commitsOnLeaving(JTextField part, Function<TileKey, String> stored)
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

            // "(mixed)" is replaced by what is typed
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (part.getText().equals(I18n.t(MIXED))) part.selectAll();
            }
        });

        part.registerKeyboardAction(e ->
        {
            // Several squares' names, shown, are not a value to put back
            if (!part.isEditable()) return;

            part.setText(shown(part));

            showProblem(part, null);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    }

    /** What a field shows: the squares' value, or "(mixed)" where they differ. */
    private String shown(JTextField part)
    {
        String common = agreed(storedValues.get(part));

        return common == null ? I18n.t(MIXED) : common;
    }

    private void commit(JTextField part)
    {
        if (reading || tile == null || !part.isEditable() || !part.isEnabled()) return;

        String typed = part.getText();

        // Unchanged writes nothing: an untouched station opened and closed changes no setup, nor "(mixed)" left alone
        if (typed.trim().equals(shown(part).trim())) { showProblem(part, null); return; }

        final String[] refused = new String[1];

        actOnEach(each ->
        {
            refused[0] = typedDoors.get(part).apply(each, typed);

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

    private String applyTypedName(TileKey square, String typed)
    {
        // No quotes, as `promptName` takes them out
        return editor.applyName(square, typed.replace("\"", ""));
    }

    private String nameInStore(TileKey square)
    {
        String stored = editor.session.getStore().getPointName(square);

        return stored == null ? "" : stored;
    }

    private String maxLengthInStore(TileKey square)
    {
        int stored = editor.number(square, "maxTrainLength", 0);

        return stored == 0 ? "" : String.valueOf(stored);
    }

    private String priorityInStore(TileKey square)
    {
        return String.valueOf(editor.number(square, "priority", 0));
    }

    private int turningOf(TileKey square)
    {
        return editor.session.isMustTurnAround(square) ? MUST : editor.session.isTurnAround(square) ? MAY : NEVER;
    }

    // ------------------------------------------------------------------------------------------------- reading

    @Override
    void fill(boolean readOnly)
    {
        AutonomySession session = editor.session;

        boolean many = isSeveral();

        setTitle(I18n.f("autosetup.ui.titleStationSettings", many
            ? I18n.f("autosetup.ui.stationsPicked", several.size()) : editor.describeTile(tile)));

        severalLine.setText(many ? AutonomyEditorPanel.wrappedAt(I18n.f("autosetup.ui.stationSeveral", several.size()),
            wrapWithin(formWidth - 8)) : "");

        StationBoxes boxes = StationBoxes.of(session, tile);

        // Each null where the squares differ, which only several can
        Boolean stopping = agreed(each -> StationBoxes.of(session, each).stops);
        Boolean through = agreed(each -> StationBoxes.of(session, each).runsThrough);
        Integer turning = agreed(this::turningOf);

        boolean must = Integer.valueOf(MUST).equals(turning);
        boolean may = Integer.valueOf(MAY).equals(turning);

        if (many)
        {
            name.setText(namesOf(several));
            name.setCaretPosition(0);
        }
        else keepTyping(name, nameInStore(tile));

        StationPreset lit = agreed(each -> StationPreset.matching(session, each));

        for (Map.Entry<StationPreset, JToggleButton> each : presets.entrySet())
        {
            each.getValue().setSelected(each.getKey() == lit);
            each.getValue().setEnabled(!readOnly);
        }

        closedLine.setVisible(Boolean.FALSE.equals(stopping) && Boolean.FALSE.equals(through));

        stops.setSelected(Boolean.TRUE.equals(stopping));
        stops.setEnabled(!readOnly);
        stopsMixed.setVisible(stopping == null);
        fly("stops", stopping == null ? mixedTip() : StationFlyovers.stops(stopping));

        runsThrough.setSelected(Boolean.TRUE.equals(through));
        runsThrough.setEnabled(!readOnly);
        throughMixed.setVisible(through == null);
        fly("runsThrough", stopping == null || through == null || turning == null ? mixedTip()
            : StationFlyovers.through(boxes, must));

        Boolean autonomy = agreed(session::isAutoDestination);

        fullAutonomy.setSelected(Boolean.TRUE.equals(autonomy));
        autoMixed.setVisible(autonomy == null);
        fly("fullAutonomy", autonomy == null ? mixedTip() : StationFlyovers.fullAutonomy(autonomy));

        keepTyping(maxLength, shown(maxLength));

        Integer length = agreed(each -> editor.number(each, "maxTrainLength", 0));

        fly("maxLength", length == null ? mixedTip() : StationFlyovers.maxLength(length));

        if (!many)
        {
            fillArrivals(boxes.stops && !readOnly);

            fillGuard(exitGuard, "exitGuard", session.getProtectingSignals(tile), true);
            fillGuard(entryGuard, "entryGuard", session.getEntrySignals(tile), false);

            fillHome();

            fillDepart(!readOnly && (boxes.stops || boxes.runsThrough));
        }

        if (turning == null) clearOneOf(turnNever);

        turnNever.setSelected(Integer.valueOf(NEVER).equals(turning));
        turnMay.setSelected(may);
        turnMust.setSelected(must);
        turnMixed.setVisible(turning == null);
        fly("turning", turning == null ? mixedTip() : StationFlyovers.turning(may, must));

        keepTyping(priority, shown(priority));

        Integer rank = agreed(each -> editor.number(each, "priority", 0));

        fly("priority", rank == null ? mixedTip() : StationFlyovers.priority(rank));

        keepTyping(speed, shown(speed));

        Integer pace = agreed(editor::percent);

        fly("speed", pace == null ? mixedTip() : StationFlyovers.speed(pace));

        WhichTrains which = agreed(session::getWhichTrains);
        Integer listed = agreed(each -> session.getTrainList(each).size());

        if (which == null) clearOneOf(whichAll);

        whichAll.setSelected(which == WhichTrains.ALL);
        whichBlack.setSelected(which == WhichTrains.BLACKLIST);
        whichWhite.setSelected(which == WhichTrains.WHITELIST);
        whichMixed.setVisible(which == null);
        fly("which", which == null || listed == null ? mixedTip() : StationFlyovers.whichTrains(which, listed));

        if (!many)
        {
            String blockers = namesOf(session.getStore().getBlockingPoints(tile));

            unavailable.setText(blockers == null ? I18n.t("autosetup.ui.labelNone") : blockers);
            fly("unavailable", StationFlyovers.unavailable(blockers));
        }

        enable(stopSection, Boolean.TRUE.equals(stopping) && !readOnly);
        enable(throughSection, !readOnly && Boolean.TRUE.equals(agreed(each ->
            StationBoxes.of(session, each).stops || StationBoxes.of(session, each).runsThrough)));
        enable(otherControls, !readOnly);

        // Several have no one name to change
        if (many) name.setEditable(false);

        // A field grayed by a section still shows its value
        whichChoose.setEnabled(!readOnly && which != WhichTrains.ALL);

        showForHowMany(many);

        if (many)
        {
            // No matrix to wait for, and an answer still coming for one square is dropped
            matrixPainted = matrixAsked.incrementAndGet();

            return;
        }

        picture.show(session, tile, editor.parentWindow(), pictureDoors);

        askTheRailway();
    }

    /** Shows the rows one station has, or the line that says several are set. */
    private void showForHowMany(boolean many)
    {
        severalLine.setVisible(many);
        sideColumn.setVisible(!many);

        for (JComponent part : perStation) part.setVisible(!many && (advancedOpen || !advancedRows.contains(part)));
    }

    private static String mixedTip()
    {
        return I18n.t("autosetup.ui.stationMixedTip");
    }

    /** Leaves none of a row's radios chosen, which a group allows only by being told. */
    private static void clearOneOf(JRadioButton any)
    {
        ((javax.swing.DefaultButtonModel) any.getModel()).getGroup().clearSelection();
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
        arriveBoxes.clear();

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
            arriveBoxes.put(side, allow);
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
        departChoices.clear();
        departAnswers.clear();

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

            departChoices.put(entry.getKey(), choice);
            departAnswers.put(entry.getKey(), answers);
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

        actOnEach(each -> editor.applyPreset(each, preset));
    }

    /** The box pressed is set on every square; the other box stays as each square has it. */
    private void pressBoxes(JCheckBox pressed)
    {
        if (reading) return;

        final boolean wanted = pressed.isSelected();

        actOnEach(each ->
        {
            StationBoxes now = StationBoxes.of(editor.session, each);

            return editor.setUsage(each, pressed == stops ? wanted : now.stops, pressed == runsThrough ? wanted : now.runsThrough);
        });
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

        actOnEach(each -> { editor.setTurning(each, may, must); return true; });
    }

    private void pressWhich(WhichTrains which)
    {
        if (reading || which == agreed(editor.session::getWhichTrains)) return;

        // Either list picked with nothing on it opens the list (FR-111), each square's own
        actOnEach(each -> which != editor.session.getWhichTrains(each),
            each -> which != WhichTrains.ALL && editor.session.getTrainList(each).isEmpty()
                ? editor.promptTrainList(each, which) : editor.setWhichTrains(each, which));
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

        for (JComponent part : advancedRows) part.setVisible(open && !(isSeveral() && perStation.contains(part)));

        // A refusal line stays hidden until there is one
        if (open)
        {
            problems.get(priority).setVisible(false);
            problems.get(speed).setVisible(false);
        }

        if (isVisible()) pack();
    }

    // ------------------------------------------------------------------------------------------- the picture's clicks

    /** The form's controls by what they set, so a click on the picture presses the same control. */
    private final Map<RouteId, JComboBox<String>> departChoices = new LinkedHashMap<>();
    private final Map<RouteId, List<Direction>> departAnswers = new LinkedHashMap<>();
    private final Map<TilePorts.Side, JCheckBox> arriveBoxes = new EnumMap<>(TilePorts.Side.class);

    private transient StandingRules shownRules;

    /** A station's mark presses the form's control for it; a next square's arrow, its right-click menu's door. */
    private final StationPicture.Doors pictureDoors = new StationPicture.Doors()
    {
        @Override
        public String tip(StationPicture.Mark mark)
        {
            return pictureClick(mark, false);
        }

        @Override
        public void press(StationPicture.Mark mark)
        {
            pictureClick(mark, true);
        }
    };

    /** What a click on a mark does, and does it when pressed: one method, so the tooltip says what the click does. */
    private String pictureClick(StationPicture.Mark mark, boolean press)
    {
        if (whyHeld() != null) return whyHeld();

        switch (mark.kind)
        {
            case OUT: return wayOut(mark, press);
            case IN: return wayIn(mark, press);
            default: return nextSquare(mark, press);
        }
    }

    /** A way out toggles that side among the open arms, as May Depart's choice for its track. */
    private String wayOut(StationPicture.Mark mark, boolean press)
    {
        Map<RouteId, TilePorts.Route> routes = editor.session.getRoutes(tile);
        List<TilePorts.Side> arms = AutonomyEditorPanel.armsOf(routes);

        int at = arms.indexOf(mark.side);

        if (at < 0) return I18n.t("autosetup.ui.stationPicCannot");

        int mask = editor.armMask(tile, routes, arms);
        boolean set = (mask & 1 << at) != 0;

        // Open here, but the railway takes no train out this way: the track around is what to change
        if (set && !mark.open) return I18n.t("autosetup.ui.stationPicOutBeyond");

        for (Map.Entry<RouteId, TilePorts.Route> each : routes.entrySet())
        {
            TilePorts.Route road = each.getValue();

            if (road.getA() != mark.side && road.getB() != mark.side) continue;

            JComboBox<String> choice = departChoices.get(each.getKey());

            int answer = choice == null ? -1
                : departAnswers.get(each.getKey()).indexOf(TileGraph.answerFor(road, arms, mask ^ 1 << at));

            if (answer < 0 || !choice.isEnabled()) return I18n.t("autosetup.ui.stationPicCannot");

            if (press) choice.setSelectedIndex(answer);

            return I18n.t(set ? "autosetup.ui.stationPicOutOpen" : "autosetup.ui.stationPicOutShut");
        }

        return I18n.t("autosetup.ui.stationPicCannot");
    }

    /** A way in ticks or unticks that side's May Arrive From box. */
    private String wayIn(StationPicture.Mark mark, boolean press)
    {
        boolean barred = editor.session.getBarredArrivals(tile).contains(mark.side);

        // Open, and still no train may stop coming this way: the table's reason
        if (!barred && !mark.open)
        {
            StandingRules.Cell why = shownRules == null ? null : shownRules.cells(StandingRules.Row.SENT_HERE, true)
                .get(mark.side.name());

            return why == null ? I18n.t("autosetup.ui.stationPicCannot") : why.getWhy();
        }

        JCheckBox box = arriveBoxes.get(mark.side);

        if (box == null || !box.isEnabled())
        {
            return editor.session.arrivalSides(tile).size() == 1
                ? I18n.f("autosetup.ui.hintOneWayIn", I18n.t("autosetup.ui.side" + mark.side.name()))
                : I18n.t(box == null ? "autosetup.ui.stationPicCannot" : "autosetup.ui.stationPicInKept");
        }

        if (press) box.doClick();

        return I18n.t(barred ? "autosetup.ui.stationPicInShut" : "autosetup.ui.stationPicInOpen");
    }

    /** A next square's arrow, set as its right-click menu's Trains May Depart sets it: its one track, or that arm. */
    private String nextSquare(StationPicture.Mark mark, boolean press)
    {
        final TileKey square = mark.square;

        String name = editor.describeTile(square);

        Map<RouteId, TilePorts.Route> routes = editor.session.getRoutes(square);
        List<TilePorts.Side> arms = AutonomyEditorPanel.armsOf(routes);

        int at = arms.indexOf(mark.side);

        if (!editor.session.canCarryDirection(square) || at < 0) return I18n.f("autosetup.ui.stationPicNextCannot", name);

        int mask = editor.armMask(square, routes, arms);
        final boolean open = (mask & 1 << at) != 0;

        if (routes.size() == 1)
        {
            final Map.Entry<RouteId, TilePorts.Route> only = routes.entrySet().iterator().next();
            final Direction wanted = TileGraph.answerFor(only.getValue(), arms, mask ^ 1 << at);

            if (!TileGraph.directionIsPossible(wanted, only.getValue())) return I18n.f("autosetup.ui.stationPicNextCannot", name);

            if (press) actAlone(() -> editor.setRunDirection(square, only.getKey(), wanted));
        }
        else if (press)
        {
            actAlone(() -> editor.setArm(square, mark.side, !open));
        }

        return I18n.f(open ? "autosetup.ui.stationPicNextOpen" : "autosetup.ui.stationPicNextShut", name);
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

                    shownRules = error == null ? rules : null;

                    picture.showWays(shownRules);

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

    /**
     * @return in the picture's block's pixels: "OUT.N", "IN.N" (a station's marks), "AXIS.N" (that side's axis),
     *     "NEXT.N.W" (the next square to the north's arrow at its west side), "SQUARE" and "SQUARE.N" (the squares)
     */
    public Map<String, java.awt.Shape> pictureMarksForTest()
    {
        Map<String, java.awt.Shape> out = new LinkedHashMap<>();

        for (Map.Entry<String, java.awt.Rectangle> each : picture.squaresForTest().entrySet())
        {
            out.put(each.getKey().isEmpty() ? "SQUARE" : "SQUARE." + each.getKey(), each.getValue());

            if (!each.getKey().isEmpty())
            {
                java.awt.geom.Line2D axis = picture.axisForTest(TilePorts.Side.valueOf(each.getKey()));

                if (axis != null) out.put("AXIS." + each.getKey(), axis);
            }
        }

        for (StationPicture.Mark mark : picture.marks()) out.put(keyOf(mark), mark.shape);

        return out;
    }

    /** @return every mark of the picture with its open state, to tell a press that changed something */
    public java.util.Set<String> pictureStateForTest()
    {
        return picture.stateForTest();
    }

    /** @return the mark, named as `pictureMarksForTest` names it, that a click at this point of the picture presses */
    public String pictureMarkAtForTest(java.awt.Point at)
    {
        StationPicture.Mark mark = picture.markAtForTest(at);

        return mark == null ? null : keyOf(mark);
    }

    private String keyOf(StationPicture.Mark mark)
    {
        boolean next = mark.kind == StationPicture.Mark.Kind.NEIGHBOR;

        return (next ? "NEXT." + sideOf(mark.square) : mark.kind.name()) + "." + mark.side.name();
    }

    private String sideOf(TileKey square)
    {
        for (TilePorts.Side side : TilePorts.Side.values())
        {
            if (square.equals(tile.beside(side))) return side.name();
        }

        return "?";
    }

    /** @return the square the window is about */
    public TileKey squareForTest()
    {
        return tile;
    }

    /** @return every square a change goes to, in the order picked */
    public List<TileKey> squaresForTest()
    {
        return tile == null ? Collections.<TileKey>emptyList() : new ArrayList<>(targets());
    }

    /** What the matrix draws for a row and tier ("auto" or "hand"): side to answer; empty where every cell says No. */
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
