package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * Station Settings' Train tab (FR-111, FR-195): which train stands on the window's square, and which way round.
 * Read-only during a run except Active, which pauses the train as its Auto tab card does.  It asks for no width, so a
 * long name in a row never widens the window.
 */
final class TrainTab extends StationWindowBase.RoomyPanel
{
    private static final Font REGULAR = new Font("Segoe UI", Font.PLAIN, 14);

    private final StationWindowBase window;
    private final AutonomyEditorPanel editor;

    private final StationWindowBase.Choice train;

    /** On the footer's left, on Done Editing's row, while this tab shows. */
    final JButton settings = new JButton(I18n.t("autosetup.ui.trainLocomotiveSettings"),
        RowIcons.wrench(16, new java.awt.Color(110, 110, 110)));

    private final JCheckBox active = new JCheckBox(I18n.t("autosetup.ui.trainMayBeMoved"));
    private final JLabel activeMark = InfoMark.label("");

    private final JPanel facing = rowOfChoices("train.facing");
    private final JLabel facingMark = InfoMark.label("");

    private final JPanel arrived = rowOfChoices("train.arrived");
    private final JLabel arrivedMark = InfoMark.label("");

    /** One answer a line, as a station's name can make each long. */
    private final JPanel tail = rowOfChoices("train.tail");
    private final JLabel tailMark = InfoMark.label("");
    private final JLabel tailLabel = UiStyle.blackLabel(I18n.t("autosetup.ui.trainTail"));

    /** What the tab last showed, so the watch redraws only when a train moved. */
    private String shown;

    TrainTab(StationWindowBase window)
    {
        super(new BorderLayout());

        this.window = window;
        this.editor = window.editor;

        train = window.new Choice(at -> pressTrain());

        JPanel form = new JPanel(new GridBagLayout());

        form.setName("train.form");

        train.setName("train.train");

        settings.setName("train.settings");
        settings.putClientProperty("JButton.buttonType", "toolBarButton");
        settings.setFont(org.traincontrol.gui.widgets.UiStyle.BUTTON_FONT);
        settings.setFocusable(false);
        settings.addActionListener(e -> pressSettings());

        active.setName("train.active");
        active.setFont(REGULAR);
        active.addActionListener(e -> pressActive());

        tail.setLayout(new BoxLayout(tail, BoxLayout.Y_AXIS));

        int y = 0;

        StationWindowBase.formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainTrain")), null, train, 1);
        StationWindowBase.formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainActive")), activeMark, active, 1);
        StationWindowBase.formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainFacing")), facingMark, facing, 1);
        StationWindowBase.formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainArrived")), arrivedMark, arrived, 1);
        StationWindowBase.formRow(form, y, tailLabel, tailMark, tail, 1);

        // Tail lies and its (i) beside the first of its lines
        GridBagLayout layout = (GridBagLayout) form.getLayout();

        for (JComponent top : new JComponent[] {tailLabel, tailMark})
        {
            GridBagConstraints c = layout.getConstraints(top);

            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets.top += 4;

            layout.setConstraints(top, c);
        }

        setBorder(BorderFactory.createEmptyBorder(StationWindowBase.PAD, 0, 0, 0));

        add(form, BorderLayout.NORTH);
    }

    private static JPanel rowOfChoices(String name)
    {
        JPanel out = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        out.setName(name);

        return out;
    }

    /** Gives the keyboard to the train list, as a window of its own did on opening. */
    void takeTheKeyboard()
    {
        train.requestFocusInWindow();
    }

    void fill(boolean readOnly)
    {
        TileKey tile = window.tile;

        String standing = editor.tileMenus.trainStanding(tile);

        // A train is put only on a station; one left standing elsewhere can be taken off
        boolean station = editor.session.getStore().isStation(tile);

        List<String> names = standing == null || station ? editor.trainChoices(tile, I18n.t("autosetup.ui.labelNone"))
            : new java.util.ArrayList<>(java.util.Arrays.asList(I18n.t("autosetup.ui.labelNone"), standing));

        if (standing != null && !names.contains(standing)) names.add(1, standing);

        train.setModel(new DefaultComboBoxModel<>(names.toArray(new String[0])));
        train.setSelectedItem(standing == null ? names.get(0) : standing);
        train.setEnabled(!readOnly && (station || standing != null));

        settings.setEnabled(standing != null);
        settings.setToolTipText(I18n.t("autosetup.ui.trainLocomotiveSettingsTip"));

        Locomotive model = train(standing);

        boolean going = standing != null && !editor.isPaused(standing, editor.session.getPausedLocomotives());

        active.setSelected(going);

        // Not while nothing stands here; on the track diagram, the one control a run leaves live (FR-187)
        active.setEnabled(standing != null && (!readOnly || pausesAlone()));

        // Every (i) explains its row in every state (OB-429)
        activeMark.setToolTipText(standing == null ? StationFlyovers.noTrain("autosetup.ui.flyActiveMeans")
            : StationFlyovers.active(standing, going));

        fillFacing(standing, readOnly);

        fillRow(arrived, arrivedMark, standing == null ? null : editor.tileMenus.arrivedFacts(tile), "autosetup.ui.labelNone",
            standing == null ? StationFlyovers.noTrain("autosetup.ui.flyArrivedMeans") : StationFlyovers.arrivedFrom(),
            (facts, group) -> addArrivals(facts, group, readOnly));

        String[] why = new String[1];

        EditorTileMenus.TailFacts tailFacts = standing == null ? null : editor.tileMenus.tailFacts(tile, why);

        String tailSays = standing == null ? StationFlyovers.noTrain("autosetup.ui.flyTailNow") : tailFacts != null
            ? StationFlyovers.tail() : StationFlyovers.unanswered(I18n.t("autosetup.ui.flyTailNow"), why[0]);

        fillRow(tail, tailMark, tailFacts, "autosetup.ui.tailCrossedNotKnown", tailSays,
            (facts, group) -> addTailChoices(facts, group, readOnly));

        shown = signature(standing, model);
    }

    /**
     * Whether Active pauses the train alone, as the diagram's Is Active does: a run holds the setup and no editor does,
     * whose draft would undo a pause it does not hold.
     */
    private boolean pausesAlone()
    {
        TrainControlUI main = editor.parentWindow();

        return editor.menuOnly && main != null && !main.isLayoutEditorOpen() && main.aRunHoldsTheSetup();
    }

    private Locomotive train(String name)
    {
        TrainControlUI main = editor.parentWindow();

        return name == null || main == null || main.getModel() == null ? null : main.getModel().getLocByName(name);
    }

    /** What may change on the railway with no edit: which train stands here, and whether it is paused. */
    private String signature(String standing, Locomotive model)
    {
        return standing + "|" + (model != null && model.isAutonomyPaused()) + "|" + editor.session.getArrivedFrom(window.tile);
    }

    /** Whether the railway moved under what the tab shows, which edits alone do not say. */
    boolean stale()
    {
        String standing = editor.tileMenus.trainStanding(window.tile);

        return !signature(standing, train(standing)).equals(shown);
    }

    private void fillFacing(String standing, boolean readOnly)
    {
        ButtonGroup group = emptied(facing);

        EditorTileMenus.FacingFacts facts = standing == null ? null : editor.tileMenus.facingFacts(window.tile);

        if (facts != null && !facts.facings.isEmpty())
        {
            for (final TilePorts.Side side : facts.facings)
            {
                facing.add(choice("train.facing." + side.name(), I18n.t("autosetup.ui.facing" + side.name()), group,
                    facts.isTicked(side), !readOnly && facts.facings.size() > 1,
                    () -> editor.tileMenus.chooseFacing(window.tile, side)));
            }
        }
        else
        {
            facing.add(plain(standing == null ? I18n.t("autosetup.ui.labelNone") : I18n.t("autosetup.ui.hintFacingNoWayOut")));
        }

        facingMark.setToolTipText(standing == null ? StationFlyovers.noTrain("autosetup.ui.flyFacingMeans")
            : StationFlyovers.facing(standing));

        facing.revalidate();
    }

    /** Fills a row again: with its answers, or with the words saying it has none. */
    private <F> void fillRow(JPanel row, JComponent mark, F facts, String noneKey, String flyover,
        BiConsumer<F, ButtonGroup> answers)
    {
        ButtonGroup group = emptied(row);

        if (facts == null) row.add(plain(I18n.t(noneKey)));
        else answers.accept(facts, group);

        mark.setToolTipText(flyover);

        row.revalidate();
    }

    private void addArrivals(EditorTileMenus.ArrivedFacts facts, ButtonGroup group, boolean readOnly)
    {
        for (final String side : facts.sides)
        {
            arrived.add(choice("train.arrived." + side, org.traincontrol.gui.autonomy.ArrivalSidePrompt.labelFor(side),
                group, side.equals(facts.recorded), !readOnly, () -> editor.tileMenus.setArrivedFrom(window.tile, side)));
        }

        if (facts.recordedButGone == null) return;

        JRadioButton gone = choice("train.arrived.gone", I18n.f("autosetup.ui.arrivedFromNotOnTrack",
            org.traincontrol.gui.autonomy.ArrivalSidePrompt.labelFor(facts.recordedButGone)), new ButtonGroup(), true,
            false, null);

        gone.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintArrivedFromNotOnTrack")));

        arrived.add(gone);
    }

    private void addTailChoices(EditorTileMenus.TailFacts facts, ButtonGroup group, boolean readOnly)
    {
        for (final TailCrossedPrompt.Choice each : facts.choices)
        {
            tail.add(choice("train.tail." + each.getLabel(), each.getLabel(), group, each == facts.ticked, !readOnly,
                () -> editor.recordTailRoad(window.tile, each.getRoad())));
        }

        tail.add(choice("train.tail.unknown", I18n.t("autosetup.ui.tailCrossedNotKnown"), group, facts.ticked == null,
            !readOnly, () -> editor.recordTailRoad(window.tile, null)));

        // Clicking is the editor's: the track diagram's panel has no grid to be clicked on
        if (editor.menuOnly) return;

        // Pressed in while the pick waits; pressing it again ends the pick (MT-959)
        JToggleButton pick = new JToggleButton(I18n.t("autosetup.ui.menuPickTailCrossed"), RowIcons.pencil(16),
            window.tile.equals(editor.tailPickFor()));

        pick.setName("train.tailPick");
        pick.putClientProperty("JButton.buttonType", "toolBarButton");
        pick.setFont(org.traincontrol.gui.widgets.UiStyle.BUTTON_FONT);
        pick.setFocusable(false);
        pick.setEnabled(!readOnly);
        pick.addActionListener(e ->
        {
            if (!editor.endTailPick(window.tile)) window.actAlone(() -> editor.armTailPick(window.tile));
        });

        tail.add(pick);
    }

    /** A row emptied for filling again, and a group for its answers. */
    private static ButtonGroup emptied(JPanel row)
    {
        row.removeAll();

        return new ButtonGroup();
    }

    /** One answer of a row; pressing it runs the door, as every control of the window does. */
    private JRadioButton choice(String name, String text, ButtonGroup group, boolean on, boolean live, Runnable door)
    {
        JRadioButton out = StationWindowBase.styled(new JRadioButton(text, on), name, REGULAR);

        out.setEnabled(live);

        group.add(out);

        if (door != null)
        {
            out.addActionListener(e ->
            {
                if (!window.reading) window.act(() -> { door.run(); return true; });
            });
        }

        return out;
    }

    private static JLabel plain(String text)
    {
        JLabel out = new JLabel(text);

        out.setFont(REGULAR);
        out.setEnabled(false);

        return out;
    }

    private void pressTrain()
    {
        final int at = train.getSelectedIndex();
        final String chosen = (String) train.getSelectedItem();

        final String standing = editor.tileMenus.trainStanding(window.tile);

        if (at <= 0)
        {
            if (standing != null) window.actAlone(() -> editor.removeLocomotive(window.tile));

            return;
        }

        final String name = editor.withoutMark(chosen);

        if (!name.equals(standing)) window.actAlone(() -> editor.placeLocomotive(window.tile, name));
    }

    private void pressSettings()
    {
        TrainControlUI main = editor.parentWindow();

        Locomotive model = train(editor.tileMenus.trainStanding(window.tile));

        if (main == null || model == null) return;

        main.pressFromWindow(window, () -> main.openLocomotiveSettings(model, LocomotiveSettingsFrame.Tab.AUTONOMY, null));
    }

    private void pressActive()
    {
        if (window.reading) return;

        final String standing = editor.tileMenus.trainStanding(window.tile);
        final Locomotive model = train(standing);
        final boolean on = active.isSelected();

        if (standing == null) return;

        if (model != null && pausesAlone())
        {
            // Decided at the click, as the diagram's Is Active is: during a run, the train alone (FR-059)
            editor.parentWindow().pauseTrainWhileRunning(model, !on);
        }
        else
        {
            window.act(() -> { editor.setActive(standing, on); return true; });
        }

        window.reload();
    }
}
