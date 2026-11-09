package org.traincontrol.gui.autonomyeditor;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
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
 * "Train at Bramley" (FR-111): which train stands on a square, and which way round.  Read-only during a run except
 * Active, which pauses the train as its Auto tab card does.  Saved as Station Settings is.
 */
public final class TrainWindow extends StationWindowBase
{
    private static final Font REGULAR = new Font("Segoe UI", Font.PLAIN, 14);

    private final Choice train = new Choice(at -> pressTrain());
    private final JButton settings = new JButton(I18n.t("autosetup.ui.trainLocomotiveSettings"),
        RowIcons.wrench(16, new java.awt.Color(110, 110, 110)));

    private final JCheckBox active = new JCheckBox(I18n.t("autosetup.ui.trainMayBeMoved"));
    private final JLabel activeMark = InfoMark.label("");

    private final JPanel facing = rowOfChoices("train.facing");
    private final JLabel facingMark = InfoMark.label("");

    private final JPanel arrived = rowOfChoices("train.arrived");
    private final JLabel arrivedMark = InfoMark.label("");

    private final JPanel tail = rowOfChoices("train.tail");
    private final JLabel tailMark = InfoMark.label("");
    private final JLabel tailLabel = UiStyle.blackLabel(I18n.t("autosetup.ui.trainTail"));

    /** What the window last showed, so the watch redraws only when a train moved. */
    private String shown;

    TrainWindow(AutonomyEditorPanel editor, Window owner)
    {
        super(editor, owner);

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

        int y = 0;

        formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainTrain")), null, train, 1);
        formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainActive")), activeMark, active, 1);
        formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainFacing")), facingMark, facing, 1);
        formRow(form, y++, UiStyle.blackLabel(I18n.t("autosetup.ui.trainArrived")), arrivedMark, arrived, 1);
        formRow(form, y, tailLabel, tailMark, tail, 1);

        // Locomotive settings sit at the bottom left, on Done Editing's row
        assemble(form).add(settings, java.awt.BorderLayout.WEST);
    }

    private static JPanel rowOfChoices(String name)
    {
        JPanel out = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        out.setName(name);

        return out;
    }

    @Override
    void fill(boolean readOnly)
    {
        setTitle(I18n.f("autosetup.ui.titleTrainAt", editor.describeTile(tile)));

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

        // Not while nothing stands here; the one control a run leaves live, as the diagram's Is Active (FR-187)
        active.setEnabled(standing != null && (!readOnly || runHolds()));

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

    private boolean runHolds()
    {
        TrainControlUI main = editor.parentWindow();

        return main != null && main.aRunHoldsTheSetup();
    }

    private Locomotive train(String name)
    {
        TrainControlUI main = editor.parentWindow();

        return name == null || main == null || main.getModel() == null ? null : main.getModel().getLocByName(name);
    }

    /** What may change on the railway with no edit: which train stands here, and whether it is paused. */
    private String signature(String standing, Locomotive model)
    {
        return standing + "|" + (model != null && model.isAutonomyPaused()) + "|" + editor.session.getArrivedFrom(tile);
    }

    @Override
    void watched()
    {
        super.watched();

        if (tile == null) return;

        String standing = editor.tileMenus.trainStanding(tile);

        if (!signature(standing, train(standing)).equals(shown)) reload();
    }

    private void fillFacing(String standing, boolean readOnly)
    {
        ButtonGroup group = emptied(facing);

        EditorTileMenus.FacingFacts facts = standing == null ? null : editor.tileMenus.facingFacts(tile);

        if (facts != null && !facts.facings.isEmpty())
        {
            for (final TilePorts.Side side : facts.facings)
            {
                facing.add(choice("train.facing." + side.name(), I18n.t("autosetup.ui.facing" + side.name()), group,
                    facts.isTicked(side), !readOnly && facts.facings.size() > 1, () -> editor.tileMenus.chooseFacing(tile, side)));
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
                group, side.equals(facts.recorded), !readOnly, () -> editor.tileMenus.setArrivedFrom(tile, side)));
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
                () -> editor.recordTailRoad(tile, each.getRoad())));
        }

        tail.add(choice("train.tail.unknown", I18n.t("autosetup.ui.tailCrossedNotKnown"), group, facts.ticked == null,
            !readOnly, () -> editor.recordTailRoad(tile, null)));

        // Clicking is the editor's: the track diagram's panel has no grid to be clicked on
        if (editor.menuOnly) return;

        JButton pick = new JButton(I18n.t("autosetup.ui.menuPickTailCrossed"), RowIcons.pencil(16));

        pick.setName("train.tailPick");
        pick.putClientProperty("JButton.buttonType", "toolBarButton");
        pick.setFont(org.traincontrol.gui.widgets.UiStyle.BUTTON_FONT);
        pick.setFocusable(false);
        pick.setEnabled(!readOnly);
        pick.addActionListener(e -> actAlone(() -> editor.armTailPick(tile)));

        tail.add(pick);
    }

    /** A row emptied for filling again, and a group for its answers. */
    private static ButtonGroup emptied(JPanel row)
    {
        row.removeAll();

        return new ButtonGroup();
    }

    /** One answer of a row; pressing it runs the door, as every control of this window does. */
    private JRadioButton choice(String name, String text, ButtonGroup group, boolean on, boolean live, Runnable door)
    {
        JRadioButton out = styled(new JRadioButton(text, on), name, REGULAR);

        out.setEnabled(live);

        group.add(out);

        if (door != null)
        {
            out.addActionListener(e ->
            {
                if (!reading) act(() -> { door.run(); return true; });
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

        final String standing = editor.tileMenus.trainStanding(tile);

        if (at <= 0)
        {
            if (standing != null) actAlone(() -> editor.removeLocomotive(tile));

            return;
        }

        final String name = editor.withoutMark(chosen);

        if (!name.equals(standing)) actAlone(() -> editor.placeLocomotive(tile, name));
    }

    private void pressSettings()
    {
        TrainControlUI main = editor.parentWindow();

        Locomotive model = train(editor.tileMenus.trainStanding(tile));

        if (main == null || model == null) return;

        main.pressFromWindow(this, () -> main.openLocomotiveSettings(model, LocomotiveSettingsFrame.Tab.AUTONOMY, null));
    }

    private void pressActive()
    {
        if (reading) return;

        final String standing = editor.tileMenus.trainStanding(tile);
        final Locomotive model = train(standing);
        final boolean on = active.isSelected();

        if (standing == null) return;

        if (model != null && runHolds())
        {
            // Decided at the click, as the diagram's Is Active is: during a run, the train alone (FR-059)
            editor.parentWindow().pauseTrainWhileRunning(model, !on);
        }
        else
        {
            act(() -> { editor.setActive(standing, on); return true; });
        }

        reload();
    }

    /** @return the square the window is about */
    public TileKey squareForTest()
    {
        return tile;
    }
}
