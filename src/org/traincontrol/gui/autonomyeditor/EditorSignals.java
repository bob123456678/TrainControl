package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's pairing of a station with its signals: the exit guard that is red while the platform is
 * claimed and the entry guard that is red when a train arrives to stay (FR-096), chosen in a window beside the
 * editor, by clicking one on the diagram, or by address (FR-136).
 */
final class EditorSignals
{
    private final AutonomyEditorPanel editor;

    EditorSignals(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /**
     * @param guard which list
     * @param station the station's square
     * @return the signals on it
     */
    private java.util.List<TileKey> signalsOf(AutonomyEditorPanel.Guard guard, TileKey station)
    {
        return guard == AutonomyEditorPanel.Guard.ENTRY ? editor.session.getEntrySignals(station) : editor.session.getProtectingSignals(station);
    }

    /**
     * Asks which signals protect a station, and how the user would like to say: by pointing at it on the diagram, or by
     * its address.  Both ways ADD to a list, because a platform reachable from two directions needs a signal on each
     * approach.  What is paired so far is on screen the whole time.
     *
     * @param station the station's square
     */
    void pairProtectingSignal(TileKey station)
    {
        pairGuardSignals(AutonomyEditorPanel.Guard.EXIT, station);
    }

    /**
     * The pairing window, for either of a station's two signal lists (FR-096) - the protecting signals, or the
     * signals guarding the way in.
     *
     * @param guard which list
     * @param station the station's square
     */
    void pairGuardSignals(AutonomyEditorPanel.Guard guard, TileKey station)
    {
        editor.signalWindowOpen = true;

        try
        {
            askAboutSignals(guard, station);
        }
        finally
        {
            // Cleared HERE, in the finally: the window has four ways out (Done, Escape, the close box, "click it on the
            // diagram"), and a flag that quietens the diagram strands the editor gray and arrowless if any one forgets.
            editor.signalWindowOpen = false;

            editor.highlightedSignals.clear();
            editor.refresh();
        }
    }

    /**
     * The list itself, with the highlight held around it by the caller.  One window that stays open, because the answer
     * is a LIST: adding, removing and looking are the same gesture repeated, and closing between each hid the list as
     * it changed.  Only "click it on the diagram" gives the window back, because the next click belongs to the diagram.
     *
     * @param station the station's square
     */
    void askAboutSignals(final AutonomyEditorPanel.Guard guard, TileKey station)
    {
        // A dialog of its own, held open while the list is worked on: an option pane re-shown in a loop blinks.
        final javax.swing.JDialog dialog = new javax.swing.JDialog(
            editor.ownerWindow(),
            I18n.t(guard.title),
            java.awt.Dialog.ModalityType.APPLICATION_MODAL);

        TrainControlUI.applyWindowIcon(dialog);

        final javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        final RemovableList list = new RemovableList(model);
        final javax.swing.JLabel heading = new javax.swing.JLabel();

        final javax.swing.JButton byClick = org.traincontrol.gui.widgets.UiStyle.button(
            new javax.swing.JButton(I18n.t("autosetup.ui.optionClickSignal")));
        final javax.swing.JButton byAddress = org.traincontrol.gui.widgets.UiStyle.button(
            new javax.swing.JButton(I18n.t("autosetup.ui.optionSignalAddress")));
        final javax.swing.JButton done = org.traincontrol.gui.widgets.UiStyle.button(
            new javax.swing.JButton(I18n.t("autosetup.ui.optionSignalsDone")));

        // How wide the heading wraps - set once the buttons are laid out, below (MT-479).
        final int[] headingWidth = {0};

        // What is paired now, on screen and on the diagram behind.  Called again after every change
        // rather than rebuilding the window.
        final Runnable show = () ->
        {
            java.util.List<TileKey> paired = signalsOf(guard, station);

            editor.highlightedSignals.clear();
            editor.highlightedSignals.addAll(paired);
            editor.refresh();

            model.clear();

            for (TileKey one : paired)
            {
                model.addElement(I18n.f("autosetup.ui.signalListEntry",
                    editor.addressOf(one), editor.describeTile(one)));
            }

            if (paired.isEmpty()) model.addElement(I18n.t("autosetup.ui.signalListEmpty"));

            heading.setText(AutonomyEditorPanel.wrappedAt(paired.isEmpty()
                ? I18n.f(guard.how, editor.describeTile(station))
                : I18n.f(guard.paired, editor.describeTile(station)), headingWidth[0]));

            list.setEnabled(!paired.isEmpty());

            if (!paired.isEmpty()) list.setSelectedIndex(0);
        };

        list.setVisibleRowCount(6);
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        list.onRemove(at ->
        {
            java.util.List<TileKey> paired = signalsOf(guard, station);

            if (at < paired.size()) removeGuardSignal(guard, station, paired.get(at));

            show.run();
        });

        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));

        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(heading, java.awt.BorderLayout.NORTH);
        panel.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);

        javax.swing.JPanel buttons = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));

        buttons.add(byClick);
        buttons.add(byAddress);
        buttons.add(done);

        panel.add(buttons, java.awt.BorderLayout.SOUTH);

        // As wide as the row of buttons, up to `GUARD_WIDTH`, the sentence wrapping (MT-948); the heading keeps the
        // taller of its two sentences, so pairing the first signal changes the words and not the window (MT-479).
        String[] sentences = { I18n.f(guard.how, editor.describeTile(station)), I18n.f(guard.paired, editor.describeTile(station)) };

        headingWidth[0] = StationWindowBase.wrapWithin(Math.min(GUARD_WIDTH, buttons.getPreferredSize().width));

        int tallest = 0;

        for (String sentence : sentences)
        {
            heading.setText(AutonomyEditorPanel.wrappedAt(sentence, headingWidth[0]));

            tallest = Math.max(tallest, heading.getPreferredSize().height);
        }

        heading.setPreferredSize(new java.awt.Dimension(heading.getPreferredSize().width, tallest));

        byAddress.addActionListener(e ->
        {
            pairSignalsByAddress(guard, station);

            show.run();
        });

        // The one answer that has to give the window back: the next click belongs to the diagram.  The list returns by
        // itself once the click lands (see the click handler).
        byClick.addActionListener(e ->
        {
            if (editor.needsTheGrid(station))
            {
                dialog.dispose();
                return;
            }

            editor.signalFor = station;
            editor.signalForGuard = guard;

            dialog.dispose();

            editor.waitFor(I18n.f(guard.click, editor.describeTile(station)));

            editor.refresh();
        });

        done.addActionListener(e -> dialog.dispose());

        // Escape closes it, which means the same as Done: every change was applied as it was made, so
        // there is nothing half-finished to discard.
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.getRootPane().setDefaultButton(done);

        show.run();

        dialog.setContentPane(panel);
        dialog.pack();

        // Not smaller than it packed to (OB-107): dragging the edge up would hide the buttons that finish the pairing.
        dialog.setMinimumSize(dialog.getSize());

        place(dialog, station);

        dialog.setVisible(true);
    }

    /** At most this wide, its sentence wrapping, unless its buttons need more (MT-948). */
    static final int GUARD_WIDTH = 420;

    /**
     * Under Station Settings on this station, hidden meanwhile (MT-956), where it opened before (MT-948); without one,
     * beside the diagram rather than over the middle of it, as its text says the signals are outlined behind it (OB-107).
     */
    private void place(java.awt.Window dialog, TileKey station)
    {
        StationSettingsWindow settings = editor.stationWindows.stationWindow();
        java.awt.Window parent = editor.ownerWindow();

        if (settings != null && settings.isDisplayable() && station.equals(settings.tile))
        {
            dialog.setLocation(AutonomyEditorPanel.under(settings.getBounds(), dialog.getSize(), AutonomyEditorPanel.screenOf(settings)));
        }
        else if (parent == null) dialog.setLocationRelativeTo(editor.owner());
        else dialog.setLocation(AutonomyEditorPanel.besideOwner(parent.getBounds(), dialog.getSize()));
    }

    /**
     * The paired signals, each row with a red X at its right end that removes it, Remove as the X's tooltip (MT-948).
     * Grayed, as it is while it only says none is paired, it shows no X.
     */
    static final class RemovableList extends JList<String>
    {
        /** The X's room at a row's right end. */
        private static final int MARK = 26;

        private transient java.util.function.IntConsumer remove = at -> { };

        RemovableList(DefaultListModel<String> model)
        {
            super(model);

            final JLabel mark = new JLabel(org.traincontrol.gui.widgets.RowIcons.cross(14));
            final javax.swing.ListCellRenderer<? super String> plain = getCellRenderer();

            mark.setPreferredSize(new Dimension(MARK, 14));

            setCellRenderer((shown, value, index, selected, focused) ->
            {
                java.awt.Component text = plain.getListCellRendererComponent(shown, value, index, selected, focused);
                JPanel row = new JPanel(new BorderLayout());

                row.setBackground(text.getBackground());
                row.add(text, BorderLayout.CENTER);

                if (shown.isEnabled()) row.add(mark, BorderLayout.EAST);

                return row;
            });

            // A left press and release on the same row's X, the first click only: the list rebuilds at once, and a
            // double click's second click would land on the next row's X
            addMouseListener(new java.awt.event.MouseAdapter()
            {
                private int pressed = -1;

                @Override
                public void mousePressed(java.awt.event.MouseEvent e)
                {
                    pressed = javax.swing.SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1 ? crossAt(e.getPoint()) : -1;
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e)
                {
                    int at = pressed;

                    pressed = -1;

                    if (at >= 0 && javax.swing.SwingUtilities.isLeftMouseButton(e) && crossAt(e.getPoint()) == at) remove.accept(at);
                }
            });

            // And from the keyboard, the row chosen
            javax.swing.Action removeChosen = new javax.swing.AbstractAction()
            {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e)
                {
                    if (isEnabled() && getSelectedIndex() >= 0) remove.accept(getSelectedIndex());
                }
            };

            getActionMap().put("removeChosen", removeChosen);
            getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "removeChosen");
            getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, 0), "removeChosen");

            setToolTipText("");
        }

        void onRemove(java.util.function.IntConsumer remove)
        {
            this.remove = remove;
        }

        /** @return the row whose X is at this point, or -1 */
        int crossAt(java.awt.Point p)
        {
            int at = isEnabled() ? locationToIndex(p) : -1;
            java.awt.Rectangle cell = at < 0 ? null : getCellBounds(at, at);

            return cell != null && cell.contains(p) && p.x >= cell.x + cell.width - MARK ? at : -1;
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent e)
        {
            return crossAt(e.getPoint()) >= 0 ? I18n.t("autosetup.ui.optionRemoveSignal") : null;
        }
    }

    /**
     * Finds signals by the addresses written on them and adds them.  The LOGICAL address, the number the user sees
     * everywhere, taken off the diagram component (the control station subtracts one to reach the accessory).  SEVERAL
     * at once, separated by commas or spaces; every address that resolves is added and the rest are reported together
     * at the end, since stopping at the first would throw away the good ones typed after it.
     *
     * @param station the station's square
     */
    private void pairSignalsByAddress(AutonomyEditorPanel.Guard guard, TileKey station)
    {
        String typed = JOptionPane.showInputDialog(editor.owner(),
            I18n.t("autosetup.ui.promptSignalAddress"), I18n.t(guard.title),
            JOptionPane.PLAIN_MESSAGE);

        if (typed == null) return;

        java.util.List<String> notNumbers = new java.util.ArrayList<>();
        java.util.List<String> notFound = new java.util.ArrayList<>();
        java.util.List<TileKey> found = new java.util.ArrayList<>();

        for (String piece : typed.split("[,;\\s]+"))
        {
            if (piece.trim().isEmpty()) continue;

            int wanted;

            try
            {
                wanted = Integer.parseInt(piece.trim());
            }
            catch (NumberFormatException e)
            {
                notNumbers.add(piece.trim());
                continue;
            }

            TileKey at = signalAtAddress(wanted);

            if (at == null) notFound.add(String.valueOf(wanted));
            else if (!found.contains(at)) found.add(at);
        }

        // A SIGNAL FOUND AND REFUSED is said with the addresses that found nothing (MT-505).
        java.util.List<String> refused = new java.util.ArrayList<>();

        for (TileKey one : found)
        {
            String why = addGuardSignal(guard, station, one, false);

            if (why != null) refused.add(why);
        }

        if (!found.isEmpty()) editor.refresh();

        // Said once, however many went wrong, and after the good ones have been taken: a dialog per bad address would be
        // a row of them to dismiss before seeing whether anything worked.
        if (!notNumbers.isEmpty() || !notFound.isEmpty() || !refused.isEmpty())
        {
            StringBuilder trouble = new StringBuilder();

            if (!notNumbers.isEmpty())
            {
                trouble.append(I18n.f("autosetup.ui.errorSignalAddressNotANumber",
                    joined(notNumbers)));
            }

            if (!notFound.isEmpty())
            {
                if (trouble.length() > 0) trouble.append("\n");

                trouble.append(I18n.f("autosetup.ui.errorNoSignalAtAddress", joined(notFound)));
            }

            for (String why : refused)
            {
                if (trouble.length() > 0) trouble.append("\n");

                trouble.append(why);
            }

            JOptionPane.showMessageDialog(editor.owner(), trouble.toString());
        }
    }

    /**
     * @param wanted a logical address
     * @return the square of a signal carrying it, or null
     */
    private TileKey signalAtAddress(int wanted)
    {
        if (editor.session.getGraph() == null) return null;

        for (java.util.Map.Entry<TileKey, LayoutDiagramComponent> entry
            : editor.session.getGraph().getTiles().entrySet())
        {
            if (!editor.isPairableSignal(entry.getValue())) continue;

            if (entry.getValue().getAddress() == wanted) return entry.getKey();
        }

        return null;
    }

    /**
     * Adds one signal to a station's protection, and says so in words.  A refusal is RETURNED rather than said, so the
     * caller can say it where it will be seen (MT-505).
     *
     * @param station the station's square
     * @param signal the signal's square
     * @param redraw whether to repaint now - false while several are being added at once
     * @return why the signal was refused, or null when it was added
     */
    String addGuardSignal(AutonomyEditorPanel.Guard guard, TileKey station, TileKey signal, boolean redraw)
    {
        java.util.List<TileKey> paired
            = new java.util.ArrayList<>(signalsOf(guard, station));

        if (paired.contains(signal)) return I18n.f("autosetup.ui.signalAlreadyPaired", editor.addressOf(signal));

        // NOT THE STATION'S OTHER GUARD (*"make sure the entry guard can never be the same as the exit guard"*).
        // The store refuses it as well; this is where the operator is told why.
        if (signalsOf(guard == AutonomyEditorPanel.Guard.ENTRY ? AutonomyEditorPanel.Guard.EXIT : AutonomyEditorPanel.Guard.ENTRY, station).contains(signal))
        {
            return I18n.f(guard == AutonomyEditorPanel.Guard.ENTRY ? "autosetup.ui.signalIsTheExitGuard"
                : "autosetup.ui.signalIsTheEntryGuard", editor.addressOf(signal), editor.describeTile(station));
        }

        paired.add(signal);

        if (guard == AutonomyEditorPanel.Guard.ENTRY) editor.session.setEntrySignals(station, paired);
        else editor.session.setProtectingSignals(station, paired);

        editor.say(editor.hint, I18n.f(paired.size() == 1 ? guard.set : guard.added,
            editor.describeTile(station), editor.describeTile(signal)));

        if (redraw) editor.refresh();

        // The running layout is what throws a protecting signal.
        editor.setupChanged();

        return null;
    }

    /**
     * Takes one signal out of a station's protection, and says so in words.
     *
     * @param station the station's square
     * @param signal the signal's square
     */
    private void removeGuardSignal(AutonomyEditorPanel.Guard guard, TileKey station, TileKey signal)
    {
        java.util.List<TileKey> paired
            = new java.util.ArrayList<>(signalsOf(guard, station));

        if (!paired.remove(signal)) return;

        if (guard == AutonomyEditorPanel.Guard.ENTRY) editor.session.setEntrySignals(station, paired);
        else editor.session.setProtectingSignals(station, paired);

        editor.say(editor.hint, paired.isEmpty()
            ? I18n.f(guard.cleared, editor.describeTile(station))
            : I18n.f(guard.removed, editor.describeTile(signal), editor.describeTile(station)));

        editor.refresh();

        // The running layout is what throws a protecting signal.
        editor.setupChanged();
    }

    /**
     * @param signals squares carrying signals
     * @return their addresses, for a menu label
     */
    String signalAddresses(java.util.List<TileKey> signals)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (TileKey one : signals) out.add(editor.addressOf(one));

        return joined(out);
    }

    private static String joined(java.util.List<String> pieces)
    {
        StringBuilder out = new StringBuilder();

        for (String piece : pieces)
        {
            if (out.length() > 0) out.append(", ");

            out.append(piece);
        }

        return out.toString();
    }
}
