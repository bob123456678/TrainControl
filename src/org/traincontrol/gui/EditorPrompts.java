package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's questions about a square that the tile menus and the keys ask: a number, which
 * locomotives, a length, a link's name, and which squares hold a station back (FR-136).
 *
 * The panel keeps every one of these as a one-line call to the method of the same name here, and every field: this
 * reads and writes them through `editor`.  Each dialog is still parented on the editor's window.
 */
final class EditorPrompts
{
    private final AutonomyEditorPanel editor;

    EditorPrompts(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /**
     * Asks for a number, storing nothing when it is left at the value that means "no setting".
     */
    void promptNumber(TileKey tile, String key, String promptKey, int unset)
    {
        Object current = editor.session.getPointProperty(tile, key);

        // I18n.f, not t: two of these prompts name the point they are about, and I18n.t does no substitution.
        String entered = JOptionPane.showInputDialog(editor.owner(),
            I18n.f(promptKey, editor.describeTile(tile)),
            current instanceof Number ? String.valueOf(current) : String.valueOf(unset));

        if (entered == null) return;

        try
        {
            int value = Integer.parseInt(entered.trim());

            // REFUSED BEFORE IT IS WRITTEN, rather than by the layer that loads it (SET-B1).  Nothing is stored,
            // which is what the NumberFormatException arm below does and for the same reason.
            String refused = AutonomyEditorPanel.whyNotThisNumber(key, value);

            if (refused != null)
            {
                JOptionPane.showMessageDialog(editor.owner(), AutonomyEditorPanel.wrapped(refused));

                return;
            }

            editor.session.setPointProperty(tile, key, value == unset ? null : value);
        }
        catch (NumberFormatException e)
        {
            // About numbers, not about lengths: this prompt is also used for a station's priority, where negatives are
            // valid.
            JOptionPane.showMessageDialog(editor.owner(), I18n.f("autosetup.ui.errorNotANumber", entered));
            // NOTHING WAS WRITTEN, so there is nothing to announce (VD10-C14): the call belongs after the last statement
            // of the success path, and after the failure path it would save the setup and rebuild the railway having
            // changed nothing.
            return;
        }

        // Persisted, and the running layout told (MT-246).
        editor.setupChanged();
    }

    /**
     * Asks which squares hold this station back (FR-001).
     *
     * A checklist of the other stations, rather than a picker of one: a station may be held back by more than one place.
     *
     * Only NAMED points are offered: the restriction is written into the built configuration as lock edges pointing at
     * the watched square, and a square with no name is one the operator cannot recognise in a list.
     */
    void promptBlockingPoints(TileKey station)
    {
        // Read FIRST, because what is already stored decides what has to be offered (FSR-C5).
        java.util.List<TileKey> already = editor.session.getStore().getBlockingPoints(station);

        java.util.List<TileKey> choices = new java.util.ArrayList<>();

        for (TileKey tile : editor.session.getStore().getNamedTiles())
        {
            // STATIONS ONLY (MT-376: *"works, but exclude non-station points from the list entirely."*): a name is not a station (a
            // caption square, an approach guard or a labelled piece of track all carry one), and this setting watches whether a place
            // trains stop is OCCUPIED.  ...unless it is ALREADY STORED (FSR-C5): this dialog is the only way to edit `blockedPoints`,
            // and `chosen` is built from what the list offers, so hiding a stored entry would delete it when somebody pressed OK
            // (FBR-A2).
            if (!editor.session.getStore().isStation(tile) && !already.contains(tile)) continue;

            // Not the station itself: standing there already decides whether it is free, so watching
            // itself would make it a station nothing can be sent to.
            if (tile.equals(station)) continue;

            // And not a square that IS this station by another name (OB-083): a caption points AT the station and carries a name, so
            // choosing it would hold the station back with itself.  THIS station's captions only: widening to every caption square was
            // tried and withdrawn (FBR-C4), because a caption square is often real track (`mayCarryACaption` allows one on a feedback
            // square, and importLegacy captions every imported station WITH ITSELF, FBR-B3).
            // ... unless it is ALREADY stored (FSR-C5, which subsumes FBR-A2): the filters are about what may be CHOSEN, and hiding an
            // entry chosen earlier makes it permanent.  It appears ticked, it can be unticked, and the filters still decide what may
            // be added.
            if (station.equals(editor.session.getCaptionTarget(tile)) && !already.contains(tile)) continue;

            choices.add(tile);
        }

        // And any stored entry the loop above never reached at all (a square that has since lost its name, or is the
        // station itself from before that was refused): visible, and therefore removable.
        for (TileKey held : already)
        {
            if (held != null && !choices.contains(held)) choices.add(held);
        }

        // ALPHABETICAL, BY WHAT IS ACTUALLY SHOWN (FR-074: *"sort unavailable while occupied list alphabetically."*), on
        // `describeTile`, the text on the check box: an entry that has lost its name renders as its sensor or its coordinates.
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
            // GREYED, because there is nothing a click could find (MT-376: *"if there are no stations on the diagram yet, the 'choose
            // on diagram' button should be greyed out to match the message"*): `whyNotABlocker` refuses a square that is not a
            // station.  Shown greyed rather than left out, so the operator learns the way in exists.
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

        // WHICH OF THESE AUTONOMY WOULD NEVER CHOOSE (FR-074: *"lightly grey out stations that can't be chosen in full
        // autonomy."*).  Read once, outside the loop, because the rule walks the reduction; `stationsAutonomyWillNotChoose` is the
        // runtime's own rule (manual-only, or switched out of service), the same the diagram's captions ask.
        java.util.Set<TileKey> notChosen = editor.session.stationsAutonomyWillNotChoose();

        for (TileKey tile : choices)
        {
            // describeTile, not getPointName: a carried entry that has lost its name would render with NO TEXT AT ALL (RA-C2).
            // describeTile falls back to the s88 where the square is a sensor, its coordinates otherwise; testEditorSurfaceRules pins
            // the caption rule here as there.
            javax.swing.JCheckBox box = new javax.swing.JCheckBox(
                editor.describeTile(tile), already.contains(tile));

            box.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            // LIGHTLY (FR-074), so a foreground colour rather than a disabled box: these squares are valid to block with (a
            // parking berth holding a platform back is the case FR-001 exists for), so the mark says what KIND of square it
            // is, not that it cannot be picked.
            if (notChosen.contains(tile))
            {
                box.setForeground(AutonomyEditorPanel.NOT_CHOSEN_BY_AUTONOMY);

                box.setToolTipText(I18n.t("autosetup.ui.tooltipBlockerNotAutoDestination"));
            }

            boxes.add(box);
            panel.add(box);
        }

        // White behind the list rather than the panel's default grey: this reads as a list of things
        // to pick from, and every other list in the application is white (OB-083).
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

        // Three answers, not two (FR-025: *"the 'unavailable when occupied' dialog should allow users to select a s88 tile by
        // clicking, similar to the signal guard popup."*): a checklist is the faster way to answer "which of these" and stays the
        // default button, and picking on the diagram is faster for "that one, there".  OK and Cancel take their words from the
        // look and feel.
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

        // Every stored entry is on the list above, so the boxes are the whole answer (FSR-C5).
        for (int at = 0; at < boxes.size(); at++)
        {
            if (boxes.get(at).isSelected()) chosen.add(choices.get(at));
        }

        editor.session.getStore().setBlockingPoints(station, chosen);

        // The restriction is built into the configuration as lock edges, so the running layout has to
        // be regenerated for it to mean anything - the same seam every other setup edit uses.
        editor.placementChanged(null);

        // Pick on the diagram: the ticks above are applied FIRST and then the next click is waited
        // for.  Arming without applying would throw away whatever had just been ticked, which is a
        // button that quietly undoes the work the operator did in the same dialog.
        if (chose == 1) editor.armBlockerPick(station);
    }

    /**
     * Asks which locomotives, as a multi-select list rather than typed names - a name that does not
     * match the roster exactly would silently do nothing.
     *
     * @param tile
     * @param key the point property to store the choice under
     * @param names the locomotives worth offering, which is not always the whole roster
     */
    void promptLocomotives(TileKey tile, String key, List<String> names)
    {
        if (names.isEmpty())
        {
            JOptionPane.showMessageDialog(editor.owner(), I18n.t("error.noLocs"));
            return;
        }

        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();

        for (String name : names) model.addElement(name);

        javax.swing.JList<String> list = new javax.swing.JList<>(model);

        // Tall enough to choose from without scrolling on an ordinary roster.
        list.setVisibleRowCount(Math.max(8, Math.min(18, names.size())));
        AutonomyEditorPanel.control(list);

        Set<String> chosen = editor.strings(tile, key);
        List<Integer> indexes = new java.util.ArrayList<>();

        for (int i = 0; i < names.size(); i++)
        {
            if (chosen.contains(names.get(i))) indexes.add(i);
        }

        int[] selection = new int[indexes.size()];

        for (int i = 0; i < indexes.size(); i++) selection[i] = indexes.get(i);

        list.setSelectedIndices(selection);

        // AND THE LIST'S MODE, above it (FR-121): keep the chosen locomotives out (the default, a blacklist), or let only
        // them in (a whitelist: every other one, a locomotive added later included, is kept out).  Adam: *"we don't want
        // to allow new locomotives that get added later when that mode is on."*
        boolean exclusions = "excludedLocs".equals(key);

        javax.swing.JRadioButton keepOut = new javax.swing.JRadioButton(I18n.t("autosetup.ui.optionExcludeChosenLocs"));
        javax.swing.JRadioButton letInOnly = new javax.swing.JRadioButton(I18n.t("autosetup.ui.optionAllowOnlyChosenLocs"));

        javax.swing.ButtonGroup modes = new javax.swing.ButtonGroup();

        modes.add(keepOut);
        modes.add(letInOnly);

        (editor.allowsOnlyListed(tile) ? letInOnly : keepOut).setSelected(true);

        javax.swing.JComponent asked = new JScrollPane(list);

        if (exclusions)
        {
            javax.swing.JPanel choices = new javax.swing.JPanel(new java.awt.GridLayout(0, 1));

            choices.add(keepOut);
            choices.add(letInOnly);

            javax.swing.JPanel both = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));

            both.add(choices, java.awt.BorderLayout.NORTH);
            both.add(asked, java.awt.BorderLayout.CENTER);

            asked = both;
        }

        // The "home" arm of this is not reached: the only caller passes "excludedLocs".  Kept because the shape is right
        // for any list-valued property, but anything that starts using it for a HOME has to go through session.setHome,
        // which sweeps that locomotive's home off every other square (TD-8).
        if (JOptionPane.showConfirmDialog(editor.owner(), asked, I18n.t(key.equals("home")
                ? "autosetup.ui.labelHomeFor" : "autosetup.ui.labelLocomotiveExclusions"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        List<String> picked = list.getSelectedValuesList();

        boolean allowOnly = exclusions && letInOnly.isSelected();

        // Excluding a locomotive from the station that is its HOME contradicts the home (OB-022; HomeStaging.homeBrokenByExcluding):
        // warned rather than refused, like the home warning, since an operator may mean it.
        if ("excludedLocs".equals(key))
        {
            String broken = editor.homeBrokenBy(tile, picked, allowOnly);

            if (broken != null && JOptionPane.showOptionDialog(editor.owner(),
                I18n.f("autolayout.ui.confirmExcludingHome", broken, editor.describeTile(tile)),
                I18n.t("autosetup.ui.labelExcludedLocs"),
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
            {
                return;
            }
        }

        editor.session.setPointProperty(tile, key,
            picked.isEmpty() ? null : new org.json.JSONArray(picked));

        if (exclusions)
        {
            editor.session.setPointProperty(tile, org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE,
                allowOnly ? org.traincontrol.automation.Point.ALLOW_ONLY_LISTED : null);
        }

        editor.refresh();

        // Persisted, and the running layout told (MT-246).
        editor.setupChanged();
    }

    /**
     * Sets the length of the square under the pointer, from outside this panel (FR-066).
     *
     * Control+E (Adam: *"a keyboard shortcut for setting a square's length"*, and *"let's do E"*, Control+D being taken
     * twice over).  Like Control+S for Rename, `applyLength` is private and the key handler lives in `LayoutEditor`; this
     * decides only who may ask.
     *
     * **It asks `offersALength`, the menu's own question, and writes where the menu writes**: the menu acts on
     * `leaderOf(tile)`, the square that speaks for a run of plain track, so a key acting on the hovered square would
     * measure a run through both doors and count it twice.  A key that acts where the menu offers nothing is the shape
     * of MT-313 (Control+S named plain track), and it says a sentence rather than doing nothing quietly.
     *
     * @param tile the square to measure, ignored when null
     */
    void promptLengthFor(TileKey tile)
    {
        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
        if (editor.heldByARun()) return;

        // A GESTURE IN PROGRESS IS PUT DOWN FIRST, as the right-click menu and Control+N do (MFV-C4).
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

        // The running layout carries link names too (VD10-B2).
        editor.setupChanged();
    }
}
