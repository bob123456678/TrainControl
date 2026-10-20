package org.traincontrol.gui;

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
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's pairing of a station with its signals: the exit guard that is red while the platform is
 * claimed and the entry guard that is red when a train arrives to stay (FR-096), chosen in a window beside the
 * editor, by clicking one on the diagram, or by address (FR-136).
 *
 * The panel keeps every method something outside the pairing calls, as a one-line call to the method of the same
 * name here, and every field: this reads and writes them through `editor`.  Each dialog is still parented on the
 * editor's window.
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
     * Asks which signals protect a station, and how the user would like to say.
     *
     * Two ways, because two quite different people ask this question: somebody looking at the diagram knows the signal
     * by where it is and can point at it; somebody who set the layout up knows it by its address.
     *
     * Both ways ADD to a list rather than replace one answer, because a platform reachable from two directions needs a
     * signal on each approach.  What is paired so far is on screen the whole time.
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
            // Cleared HERE, in the finally, and not in the button handlers: the window has four ways out (Done, Escape,
            // the close box, and "click it on the diagram"), and a flag that quietens the whole diagram strands the editor
            // grey and arrowless if any one of them forgets.  This is the only place all four pass through.
            editor.signalWindowOpen = false;

            editor.highlightedSignals.clear();
            editor.refresh();
        }
    }

    /**
     * The list itself, with the highlight held around it by the caller.
     *
     * One window that stays open, because the answer is a LIST: adding a second signal, removing one paired by mistake
     * and looking at what is there are the same gesture repeated, and a window that closed and reopened between each
     * hid the list at the moment it changed.  The one answer that gives the window back is "click it on the diagram",
     * because the next click belongs to the diagram.
     *
     * @param station the station's square
     */
    void askAboutSignals(final AutonomyEditorPanel.Guard guard, TileKey station)
    {
        // A dialog of its own, held open while the list is worked on: an option pane re-shown in a loop makes the whole
        // window blink on every change and jumps the list out from under the reader.
        final javax.swing.JDialog dialog = new javax.swing.JDialog(
            editor.ownerWindow(),
            I18n.t(guard.title),
            java.awt.Dialog.ModalityType.APPLICATION_MODAL);

        // OB-124: built here rather than being a class of its own, and just as much a window.
        TrainControlUI.applyWindowIcon(dialog);

        final javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        final javax.swing.JList<String> list = new javax.swing.JList<>(model);
        final javax.swing.JLabel heading = new javax.swing.JLabel();

        final javax.swing.JButton byClick =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionClickSignal"));
        final javax.swing.JButton byAddress =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionSignalAddress"));
        final javax.swing.JButton remove =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionRemoveSignal"));
        final javax.swing.JButton done =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionSignalsDone"));

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
            remove.setEnabled(!paired.isEmpty());

            if (!paired.isEmpty()) list.setSelectedIndex(0);
        };

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);

        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));

        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(heading, java.awt.BorderLayout.NORTH);
        panel.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);

        javax.swing.JPanel buttons = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));

        buttons.add(byClick);
        buttons.add(byAddress);
        buttons.add(remove);
        buttons.add(done);

        panel.add(buttons, java.awt.BorderLayout.SOUTH);

        // THE SENTENCE ON TWO LINES, NOT ONE (MT-479: *"make the sentence split over 2 lines so the window isn't too wide."*): the
        // heading wraps at half the first sentence, never narrower than the row of buttons; the longer sentence once signals are
        // paired takes more lines at that width, and the heading keeps the taller of the two heights, so pairing the first signal
        // changes the words and not the window.
        String[] sentences = { I18n.f(guard.how, editor.describeTile(station)), I18n.f(guard.paired, editor.describeTile(station)) };

        headingWidth[0] = AutonomyEditorPanel.twoLineWidth(heading.getFontMetrics(heading.getFont()), buttons.getPreferredSize().width,
            sentences[0]);

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

        remove.addActionListener(e ->
        {
            java.util.List<TileKey> paired = signalsOf(guard, station);

            int at = list.getSelectedIndex();

            if (at >= 0 && at < paired.size()) removeGuardSignal(guard, station, paired.get(at));

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

        // Not smaller than it packed to (OB-107): dragging the edge up would hide the buttons at the bottom, which are how
        // the pairing is finished.
        dialog.setMinimumSize(dialog.getSize());

        // Beside the diagram rather than on top of it (OB-107): this window's own text says the signals "are outlined on the
        // diagram behind this window", so centring would cover them.  Offset from centre, not pinned to a corner, and clamped to
        // the owner so it cannot land off-screen.
        placeBesideOwner(dialog);

        dialog.setVisible(true);
    }

    /**
     * Puts a dialog beside the diagram rather than over the middle of it.
     *
     * For the windows that describe something drawn on the diagram behind them (the paired signals are outlined while
     * this is open), where a window centred on them hides what it is describing (OB-107).
     *
     * Offset from the owner's centre rather than pinned to a corner: a dialog in the corner of the screen reads as
     * belonging to nothing.  Clamped so it stays within the owner, which keeps it on the same monitor.
     *
     * @param dialog a packed dialog, ready to show
     */
    private void placeBesideOwner(java.awt.Window dialog)
    {
        java.awt.Window parent = editor.ownerWindow();

        if (parent == null)
        {
            dialog.setLocationRelativeTo(editor.owner());

            return;
        }

        dialog.setLocation(AutonomyEditorPanel.besideOwner(parent.getBounds(), dialog.getSize()));
    }

    /**
     * Finds signals by the addresses written on them and adds them.
     *
     * The LOGICAL address, the number the user sees everywhere (on the tile, in the switch panel, in the accessory
     * list), taken off the diagram component.  The control station subtracts one to reach the accessory, because the
     * protocol counts from zero, and asking the accessory would mean asking the user to subtract that one themselves.
     *
     * SEVERAL at once, separated by commas or spaces, because somebody who knows the addresses knows all of them.  Every
     * address that resolves is added, and the ones that do not are reported together at the end: stopping at the first
     * would throw away the good ones typed after it.
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

        // A SIGNAL FOUND AND REFUSED is said with the addresses that found nothing (MT-505): on the grey line alone
        // it was behind this window.
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
     * Adds one signal to a station's protection, and says so in words.
     *
     * A refusal is RETURNED rather than said, so the caller can say it where it will be seen (MT-505): a click in a
     * popup of its own, a list of addresses in the one popup that reports them all.
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

        if (paired.contains(signal))
        {
            return I18n.f("autosetup.ui.signalAlreadyPaired", editor.addressOf(signal));
        }

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

        for (TileKey one : signals)
        {
            out.add(editor.addressOf(one));
        }

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
