package org.traincontrol.gui.autonomyeditor;

import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's questions about a square that the tile menus and the keys ask: a number, which locomotives, a
 * length, a link's name, and which squares hold a station back (FR-136).  The panel keeps every one of these as a
 * one-line call to the method of the same name here, and every field: this reads and writes them through `editor`.
 */
final class EditorPrompts
{
    private final AutonomyEditorPanel editor;

    EditorPrompts(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /** Asks for a number, storing nothing when it is left at the value that means "no setting". */
    void promptNumber(TileKey tile, String key, String promptKey, int unset)
    {
        Object current = editor.session.getPointProperty(tile, key);

        // I18n.f, not t: two of these prompts name the point they are about, and I18n.t does no substitution.
        String entered = JOptionPane.showInputDialog(editor.owner(),
            I18n.f(promptKey, editor.describeTile(tile)),
            current instanceof Number ? String.valueOf(current) : String.valueOf(unset));

        if (entered == null) return;

        String refused = applyNumber(tile, key, entered, unset);

        if (refused != null) JOptionPane.showMessageDialog(editor.owner(), AutonomyEditorPanel.wrapped(refused));
    }

    /**
     * Stores a typed number, nothing when it is the value that means "no setting".
     * @return why it was refused, with nothing written, or null
     */
    String applyNumber(TileKey tile, String key, String entered, int unset)
    {
        try
        {
            int value = Integer.parseInt(entered.trim());

            // Refused before it is written, rather than by the layer that loads it.
            String refused = AutonomyEditorPanel.whyNotThisNumber(key, value);

            if (refused != null) return refused;

            editor.session.setPointProperty(tile, key, value == unset ? null : value);
        }
        catch (NumberFormatException e)
        {
            // About numbers, not about lengths: this is also used for a station's priority, where negatives are valid.
            // Nothing was written, so nothing to announce.
            return I18n.f("autosetup.ui.errorNotANumber", entered);
        }

        // Persisted, and the running layout told (MT-246).
        editor.setupChanged();

        return null;
    }

    /**
     * Asks which squares hold this station back (FR-001), as a checklist of the other stations since more than one
     * place may hold it back.  Only NAMED points are offered: the restriction is written as lock edges pointing at the
     * watched square, and an unnamed square cannot be recognized in a list.
     */
    void promptBlockingPoints(TileKey station)
    {
        // Read FIRST, because what is already stored decides what has to be offered.
        java.util.List<TileKey> already = editor.session.getStore().getBlockingPoints(station);

        java.util.List<TileKey> choices = new java.util.ArrayList<>();

        for (TileKey tile : editor.session.getStore().getNamedTiles())
        {
            // Stations only (MT-376: "works, but exclude non-station points from the list entirely."): a caption
            // square, an approach guard or labeled track all carry a name, and this setting watches whether a place
            // trains stop is OCCUPIED.  Unless already stored: this dialog is the only way to edit `blockedPoints`, so
            // hiding a stored entry would delete it when somebody pressed OK.
            if (!editor.session.getStore().isStation(tile) && !already.contains(tile)) continue;

            // Not the station itself: standing there already decides whether it is free, so watching
            // itself would make it a station nothing can be sent to.
            if (tile.equals(station)) continue;

            // And not a square that IS this station by another name (OB-083): a caption points AT the station and
            // carries a name, so choosing it would hold the station back with itself.  This station's captions only: a
            // caption square is often real track (`mayCarryACaption` allows one on a feedback square, and importLegacy
            // captions every imported station WITH ITSELF).  Unless already stored: the filters are about what may be
            // CHOSEN, and hiding an earlier choice makes it permanent.
            if (station.equals(editor.session.getCaptionTarget(tile)) && !already.contains(tile)) continue;

            choices.add(tile);
        }

        // And any stored entry the loop above never reached at all (a square that has since lost its name, or is the
        // station itself from before that was refused): visible, and therefore removable.
        for (TileKey held : already)
        {
            if (held != null && !choices.contains(held)) choices.add(held);
        }

        // Alphabetical, by what is actually shown (FR-074: "sort unavailable while occupied list alphabetically."), on
        // `describeTile`: an entry that has lost its name renders as its sensor or its coordinates.
        java.util.Collections.sort(choices, new java.util.Comparator<TileKey>()
        {
            @Override
            public int compare(TileKey a, TileKey b)
            {
                return String.CASE_INSENSITIVE_ORDER.compare(editor.describeTile(a), editor.describeTile(b));
            }
        });

        // Nothing to offer, which now also means nothing is stored - the loop above puts every stored
        // entry on the list. So returning here cannot hide anything.
        if (choices.isEmpty())
        {
            // Grayed, because there is nothing a click could find (MT-376: "if there are no stations on the diagram
            // yet, the 'choose on diagram' button should be greyed out to match the message"); shown rather than left
            // out, so the way in is learnt.
            javax.swing.JButton pickNow = AutonomyEditorPanel.nothingToPickButton();

            final javax.swing.JButton close = new javax.swing.JButton(
                javax.swing.UIManager.getString("OptionPane.cancelButtonText"));

            final JOptionPane pane = new JOptionPane(
                AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.infoNoOtherPointsToBlockWith")),
                JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] { pickNow, close }, close);

            close.addActionListener(e -> pane.setValue(close));

            javax.swing.JDialog dialog =
                pane.createDialog(editor.owner(), I18n.t("autosetup.ui.menuBlockedByPointsTitle"));

            dialog.setVisible(true);
            dialog.dispose();

            return;
        }

        JPanel panel = new JPanel();

        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        // Wrapped, because the message names a station and ran off the side of a dialog sized for
        // check boxes (OB-083).
        panel.add(new javax.swing.JLabel(AutonomyEditorPanel.wrapped(
            I18n.f("autosetup.ui.promptBlockedByPoints", editor.describeTile(station)))));

        panel.add(javax.swing.Box.createVerticalStrut(LayoutEditor.HEADING_GAP));

        java.util.List<javax.swing.JCheckBox> boxes = new java.util.ArrayList<>();

        // Which of these autonomy would never choose (FR-074: "lightly grey out stations that can't be chosen in full
        // autonomy.").  Read once, outside the loop, because the rule walks the reduction;
        // `stationsAutonomyWillNotChoose` is the runtime's own rule, the same the diagram's captions ask.
        java.util.Set<TileKey> notChosen = editor.session.stationsAutonomyWillNotChoose();

        for (TileKey tile : choices)
        {
            // describeTile, not getPointName: an entry that lost its name would render with no text at all;
            // testEditorSurfaceRules pins the caption rule here as there.
            javax.swing.JCheckBox box = new javax.swing.JCheckBox(
                editor.describeTile(tile), already.contains(tile));

            box.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            // Lightly (FR-074), so a foreground color rather than a disabled box: these squares are valid to block with
            // (a parking berth holding a platform back is what FR-001 exists for); the mark says what kind of square it
            // is.
            if (notChosen.contains(tile))
            {
                box.setForeground(AutonomyEditorPanel.NOT_CHOSEN_BY_AUTONOMY);

                box.setToolTipText(I18n.t("autosetup.ui.tooltipBlockerNotAutoDestination"));
            }

            boxes.add(box);
            panel.add(box);
        }

        // White behind the list, as every other list in the application is (OB-083).
        panel.setBackground(java.awt.Color.WHITE);
        panel.setOpaque(true);

        for (javax.swing.JCheckBox box : boxes)
        {
            box.setBackground(java.awt.Color.WHITE);
            box.setOpaque(true);
        }

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(panel);

        scroll.getViewport().setBackground(java.awt.Color.WHITE);
        scroll.setBackground(java.awt.Color.WHITE);

        // A wheel notch moves a row and a bit rather than one pixel.
        scroll.getVerticalScrollBar().setUnitIncrement(AutonomyEditorPanel.BLOCKED_ROW_HEIGHT);
        scroll.getVerticalScrollBar().setBlockIncrement(AutonomyEditorPanel.BLOCKED_ROW_HEIGHT * 5);

        // 460 wide: the prompt names a station, and at 320 the name wrapped into three lines.
        scroll.setPreferredSize(new java.awt.Dimension(460,
            Math.min(360, 80 + choices.size() * AutonomyEditorPanel.BLOCKED_ROW_HEIGHT)));

        // Three answers, not two (FR-025: "the 'unavailable when occupied' dialog should allow users to select a s88
        // tile by clicking, similar to the signal guard popup."): the checklist stays the default button, and picking
        // on the diagram is faster for "that one, there".  OK and Cancel take their words from the look and feel.
        final String ok = javax.swing.UIManager.getString("OptionPane.okButtonText");
        final String cancel = javax.swing.UIManager.getString("OptionPane.cancelButtonText");
        final String pick = I18n.t("autosetup.ui.optionPickBlockerOnDiagram");

        Object[] answers = { ok, pick, cancel };

        int chose = JOptionPane.showOptionDialog(editor.owner(), scroll,
            I18n.t("autosetup.ui.menuBlockedByPointsTitle"),
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, answers, ok);

        // Anything that is not one of the two doing answers - Cancel, Escape, the close box - changes
        // nothing, which is what Cancel has always meant here.
        if (chose != 0 && chose != 1) return;

        java.util.List<TileKey> chosen = new java.util.ArrayList<>();

        // Every stored entry is on the list above, so the boxes are the whole answer.
        for (int at = 0; at < boxes.size(); at++)
        {
            if (boxes.get(at).isSelected()) chosen.add(choices.get(at));
        }

        editor.session.getStore().setBlockingPoints(station, chosen);

        // The restriction is built into the configuration as lock edges, so the running layout has to
        // be regenerated for it to mean anything - the same seam every other setup edit uses.
        editor.placementChanged(null);

        // Pick on the diagram: the ticks above are applied FIRST, then the next click is waited for; arming without
        // applying would quietly undo the operator's work in this dialog.
        if (chose == 1) editor.armBlockerPick(station);
    }

    /**
     * Asks which locomotives are on a square's one list (FR-111), as a multi-select list rather than typed names: a name
     * that does not match the roster exactly would silently do nothing.  OK puts the list in force under `which`.
     *
     * @param tile the square
     * @param which Blacklist or Whitelist
     * @return false where nothing changed: no locomotives, Cancel, or a home the operator would not break
     */
    boolean promptTrainList(TileKey tile, org.traincontrol.automationui.AutonomySession.WhichTrains which)
    {
        List<String> names = editor.allLocomotives();

        if (names.isEmpty())
        {
            JOptionPane.showMessageDialog(editor.owner(), I18n.t("error.noLocs"));
            return false;
        }

        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();

        for (String name : names) model.addElement(name);

        javax.swing.JList<String> list = new javax.swing.JList<>(model);

        // Tall enough to choose from without scrolling on an ordinary roster.
        list.setVisibleRowCount(Math.max(8, Math.min(18, names.size())));
        AutonomyEditorPanel.control(list);

        List<String> chosen = editor.session.getTrainList(tile);
        List<Integer> indexes = new java.util.ArrayList<>();

        for (int i = 0; i < names.size(); i++)
        {
            if (chosen.contains(names.get(i))) indexes.add(i);
        }

        int[] selection = new int[indexes.size()];

        for (int i = 0; i < indexes.size(); i++) selection[i] = indexes.get(i);

        list.setSelectedIndices(selection);

        if (JOptionPane.showConfirmDialog(editor.owner(), new JScrollPane(list),
            I18n.t("autosetup.ui.labelLocomotiveExclusions"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return false;
        }

        List<String> picked = list.getSelectedValuesList();

        if (!homeMayBeBroken(tile, picked, which)) return false;

        editor.session.setWhichTrains(tile, which, picked);

        // Persisted, and the running layout told (MT-246).
        editor.setupChanged();

        return true;
    }

    /**
     * Excluding a locomotive from the station that is its home contradicts the home (OB-022;
     * HomeStaging.homeBrokenByExcluding): warned rather than refused, like the home warning, since an operator may mean
     * it.
     *
     * @return false where the operator would rather not
     */
    boolean homeMayBeBroken(TileKey tile, List<String> chosen,
        org.traincontrol.automationui.AutonomySession.WhichTrains which)
    {
        String broken = AutonomyEditorPanel.homeBrokenBy(editor.homeOf(tile), chosen,
            which == org.traincontrol.automationui.AutonomySession.WhichTrains.WHITELIST);

        return broken == null || JOptionPane.showOptionDialog(editor.owner(),
            I18n.f("autolayout.ui.confirmExcludingHome", broken, editor.describeTile(tile)),
            I18n.t("autosetup.ui.labelExcludedLocs"),
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) == JOptionPane.YES_OPTION;
    }

    /**
     * Sets the length of the square under the pointer, from outside this panel (FR-066): Control+E, the key handler
     * living in `LayoutEditor`.  It asks `offersALength`, the menu's own question, and writes where the menu writes
     * (`leaderOf(tile)`), so a key cannot measure a run through both doors and count it twice; where the menu offers
     * nothing it says a sentence (MT-313).
     *
     * @param tile the square to measure, ignored when null
     */
    void promptLengthFor(TileKey tile)
    {
        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
        if (editor.heldByARun()) return;

        // A GESTURE IN PROGRESS IS PUT DOWN FIRST, as the right-click menu and Control+N do.
        editor.cancelPendingGesture();

        if (!editor.offersALength(tile))
        {
            if (tile != null && editor.session != null && editor.session.getGraph() != null)
            {
                editor.say(editor.hint, editor.isIgnored(tile) ? I18n.t("autosetup.ui.infoTileIgnored")
                    : I18n.t("autosetup.ui.infoNothingToMeasureHere"));
            }

            return;
        }

        editor.applyLength(editor.squareTheLengthWouldGoOn(tile));
    }

    void promptLinkName(TileKey tile)
    {
        String current = editor.session.getStore().getLinkName(tile);

        String name = JOptionPane.showInputDialog(editor.owner(),
            I18n.t("autosetup.ui.promptLinkName"), current == null ? "" : current);

        if (name == null) return;

        editor.session.setLinkName(tile, name);

        // The running layout carries link names too.
        editor.setupChanged();
    }
}
