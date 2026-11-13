package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.RunGates;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * Station Settings' window apart from its tabs' content (FR-111): non-modal, hung off the window that asked, about one square.
 * Changes apply at once through the panel's doors; into the editor's draft there, saved at once on the track diagram.
 * Reading never writes.  Read-only while a run holds the setup, or while an editor holds it from the track diagram.
 */
abstract class StationWindowBase extends JDialog
{
    // A static block, not the constructor: JDialog's constructor creates the root pane before any constructor body here.
    static
    {
        if (!java.awt.GraphicsEnvironment.isHeadless()) TrainControlUI.installLookAndFeel();
    }

    /** The margin round the content: the route editor's. */
    static final int PAD = 10;

    static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 12);

    final transient AutonomyEditorPanel editor;
    transient TileKey tile;

    /** True while controls are being set from the setup, when their listeners must not act. */
    boolean reading;

    /** What holds the setup now, in words, or null. */
    private String held;

    /** The width the reason at the top wraps at, for a window that keeps its width; 0 leaves it on one line. */
    int stripWrapsAt;

    private final JLabel strip = new Banner();

    /** The green banner naming the stations picked together; hidden for one. */
    private final JLabel picked = new Banner();

    private final JPanel banners = new JPanel(new BorderLayout());

    /** A yellow banner, as the app's other notices, wrapped at `stripWrapsAt`. */
    private final class Banner extends JLabel
    {
        @Override
        public Dimension getPreferredSize()
        {
            Dimension asked = super.getPreferredSize();

            if (stripWrapsAt > 0) asked.width = 1;

            return asked;
        }
    }

    /** Asks again every second: a run starting or stopping raises no single event. */
    private final javax.swing.Timer watch = new javax.swing.Timer(1000, e -> watched());

    StationWindowBase(AutonomyEditorPanel editor, Window owner)
    {
        super(owner, ModalityType.MODELESS);

        this.editor = editor;

        TrainControlUI.applyWindowIcon(this);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e)
            {
                closeWindow();
            }

            // Its owner went: nothing is committed over whatever the owner's Cancel put back
            @Override
            public void windowClosed(java.awt.event.WindowEvent e)
            {
                if (tile != null) goAway();
            }
        });

        // Typed text applies before whatever is clicked next (the editor's Save); a list's choice its own loss put back
        addWindowFocusListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e)
            {
                commitFields();
            }
        });

        for (JLabel banner : new JLabel[] {strip, picked})
        {
            banner.setOpaque(true);
            banner.setBackground(new Color(255, 243, 205));
            banner.setForeground(new Color(102, 77, 3));
            banner.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            banner.setVisible(false);
        }

        // A list, not a warning: the editor's green for a setup that is ready
        picked.setBackground(AutonomyEditorPanel.READY_GREEN);
        picked.setForeground(Color.BLACK);

        strip.setName("station.strip");
        picked.setName("station.picked");

        banners.add(picked, BorderLayout.NORTH);
        banners.add(strip, BorderLayout.CENTER);

        // Escape ends a tail pick waiting first, else closes; a field's own Escape is tried before either
        getRootPane().registerKeyboardAction(e -> { if (!editor.endTailPick(tile)) closeWindow(); },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Lays the window out: the strip, the content, then the footer's (i) and Done Editing (changes apply as they are
     * made) at the bottom right.  Hands back the footer, whose bottom left (`BorderLayout.WEST`) is free.
     */
    final JPanel assemble(JComponent content)
    {
        JButton close = new JButton(I18n.t("autosetup.ui.stationDone"));

        close.setName("station.close");
        close.setFont(BUTTON_FONT);
        close.addActionListener(e -> done());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        buttons.add(InfoMark.label(I18n.t(editor.menuOnly
            ? "autosetup.ui.stationSavedAtOnce" : "autosetup.ui.stationAppliesToDraft")));
        buttons.add(close);

        JPanel footer = new JPanel(new BorderLayout());

        footer.setBorder(BorderFactory.createEmptyBorder(6, PAD, PAD, PAD));
        footer.add(buttons, BorderLayout.EAST);

        content.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, 0, PAD));

        JPanel root = new JPanel(new BorderLayout());

        root.add(banners, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);

        return footer;
    }

    /** Points the window at a square and shows it, first beside the window that asked (MT-948). */
    final void aim(TileKey square)
    {
        commitFields();

        boolean first = !isVisible() && !steppedAside;

        this.tile = square;

        reload();

        pack();

        if (first)
        {
            Window parent = getOwner();

            if (parent != null) setLocation(editor.whereStationSettingsOpens(parent, getSize()));

            watch.start();
            setVisible(true);
        }

        toFront();
    }

    /** Hidden while guard signals are picked, as it cannot be minimized behind their window (MT-956). */
    private boolean steppedAside;

    /** Hides the window, state kept, or brings it back where it was. */
    final void stepAside(boolean aside)
    {
        if (aside == steppedAside || tile == null) return;

        steppedAside = aside;

        setVisible(!aside);
    }

    /** Reads everything again; writes nothing. */
    final void reload()
    {
        if (tile == null) return;

        if (!stillAbout())
        {
            goAway();

            return;
        }

        readHeld();

        reading = true;

        try
        {
            fill(held != null);
        }
        finally
        {
            reading = false;
        }

        if (getHeight() > 0 && getPreferredSize().height > getHeight()) pack();
    }

    /** Whether what the window is about may keep it open (`StationWindows.stillAbout`). */
    boolean stillAbout()
    {
        return editor.stationWindows.stillAbout(tile);
    }

    /** Sets every control from the setup, and grays what may not be touched. */
    abstract void fill(boolean readOnly);

    /** Commits what is being typed or chosen, as leaving it does: the base, every list's choice. */
    void commitFields()
    {
        settleChoicesIn(getContentPane());
    }

    private static void settleChoicesIn(Container in)
    {
        for (Component each : in.getComponents())
        {
            if (each instanceof Choice) ((Choice) each).settle();
            else if (each instanceof Container) settleChoicesIn((Container) each);
        }
    }

    /** What the watch sees change without the panel saying so; the base reads who holds the setup. */
    void watched()
    {
        if (tile == null) return;

        if (!stillAbout())
        {
            goAway();

            return;
        }

        // Back if a way out of the guard pick forgot to say so
        editor.stationWindows.followTheGuardPick();

        String was = held;

        readHeld();

        if (was == null ? held != null : !was.equals(held)) reload();
    }

    /** @return why nothing in the window may be changed now, or null */
    final String whyHeld()
    {
        return held;
    }

    private void readHeld()
    {
        TrainControlUI main = editor.parentWindow();

        if (main == null) held = null;
        else if (editor.menuOnly && main.isLayoutEditorOpen()) held = I18n.t("autosetup.ui.menuEditorOpen");
        else held = main.aRunHoldsTheSetup() ? I18n.t(RunGates.NO_EDITS_WHILE_RUNNING) : null;

        showTheStrip();
    }

    /** What the last change came to, shown on the strip under nothing more pressing; null for nothing. */
    private String note;

    /** Says what a change came to on the strip, until the next one; null takes it down. */
    final void sayOnTheStrip(String said)
    {
        note = said;

        showTheStrip();

        if (getHeight() > 0 && getPreferredSize().height > getHeight()) pack();
    }

    /** Names the stations picked together in the green banner at the top, `tip` on hover; null takes it down. */
    final void sayWhichArePicked(String said, String tip)
    {
        picked.setText(said == null ? "" : AutonomyEditorPanel.wrappedAt(said, stripWrapsAt));
        picked.setToolTipText(tip);

        if (picked.isVisible() != (said != null))
        {
            picked.setVisible(said != null);

            if (getContentPane() != null) getContentPane().revalidate();
        }
    }

    private void showTheStrip()
    {
        String shown = held != null ? held : note;

        strip.setText(shown == null ? "" : AutonomyEditorPanel.wrappedAt(shown, stripWrapsAt));

        if (strip.isVisible() != (shown != null))
        {
            strip.setVisible(shown != null);

            if (getContentPane() != null) getContentPane().revalidate();
        }
    }

    /** @return what holds the setup now, in words, or null */
    final String heldBy()
    {
        return held;
    }

    /** Whether the window is read-only now. */
    public final boolean isReadOnly()
    {
        return held != null;
    }

    /**
     * A control's press: asked again at the click (a run may have begun, or an editor opened), then the door, whose
     * questions hang off this window, then saved, drawn and rebuilt as the menus' items do.  A door answering false
     * changed nothing, so nothing is written or rebuilt.
     */
    final void act(BooleanSupplier door)
    {
        act(door, true);
    }

    /**
     * `act`, where `putBack` false leaves the window as it is when the door changed nothing: a refused typed field keeps
     * its text, and a rebuild under a press away from it would lose that press.
     */
    final void act(BooleanSupplier door, boolean putBack)
    {
        final boolean[] changed = {false};

        editor.pressedFrom(this, () ->
        {
            if (refused())
            {
                reload();

                return;
            }

            try
            {
                changed[0] = door.getAsBoolean();
            }
            catch (RuntimeException ex)
            {
                editor.reportFailedDoor(ex);
            }

            if (changed[0]) editor.placementChanged(null);
        });

        if (putBack || changed[0]) reload();
    }

    /** A control's press whose door saves and draws for itself. */
    final void actAlone(Runnable door)
    {
        editor.pressedFrom(this, () ->
        {
            if (!refused()) door.run();
        });

        reload();
    }

    /**
     * Asked again at the click, as the watch asks: the setup still the one the window opened on (else it goes), then in
     * `readHeld`'s order an editor open over the track diagram's setup, and a run.
     */
    private boolean refused()
    {
        if (!stillAbout())
        {
            goAway();

            return true;
        }

        TrainControlUI main = editor.parentWindow();

        return editor.menuOnly && main != null && main.refuseWhileEditorOpen(this) || editor.refusedWhileRunning();
    }

    /** Closed by the user: what is being typed applies, as leaving the field does. */
    final void closeWindow()
    {
        commitFields();

        goAway();
    }

    /** Done Editing: closes as the user does, but stays open while a typed value is refused, saying why. */
    private void done()
    {
        commitFields();

        if (!refusesAValue()) goAway();
        else showTheRefusal();
    }

    /** Whether a typed value is refused, its reason showing; the base types nothing. */
    boolean refusesAValue()
    {
        return false;
    }

    /** Brings the refused value's reason into view; the base has none. */
    void showTheRefusal()
    {
    }

    /** Closed because its surface or square has gone. */
    final void goAway()
    {
        TileKey was = tile;

        tile = null;

        watch.stop();

        dispose();

        editor.stationWindows.closed(this, was);
    }

    /** Grid constraints for a form row's cell. */
    static GridBagConstraints at(int x, int y)
    {
        GridBagConstraints c = new GridBagConstraints();

        c.gridx = x;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 8);

        return c;
    }

    /** A label, its (i) if it has one, and a control taking the room left, in one row of a form. */
    static void formRow(JPanel form, int y, JLabel label, JLabel mark, JComponent control, int columnsUsed)
    {
        form.add(label, at(0, y));

        if (mark != null) form.add(mark, at(1, y));

        GridBagConstraints c = at(2, y);

        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = columnsUsed;

        form.add(control, c);
    }

    /** A label that takes the room its row has and never asks for more: its text is cut off or already wrapped. */
    static final class RoomyLabel extends JLabel
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
    }

    /** The same for a panel of answers that vary. */
    static class RoomyPanel extends JPanel
    {
        RoomyPanel(java.awt.LayoutManager layout)
        {
            super(layout);
        }

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
    }

    /** The width to give `wrappedAt` to fill this room: Swing's HTML counts its pixel as 1.3 of ours. */
    static int wrapWithin(int room)
    {
        return room * 3 / 4;
    }

    static Dimension noWidth(Dimension asked)
    {
        asked.width = 1;

        return asked;
    }

    /**
     * A list that acts on a choice made, as a typed field does: a pick from the open list, Enter, or leaving the list
     * within the window (`commitFields`).  A typed letter or an arrow alone only shows the choice; Escape, or another
     * window taking the keyboard, puts back what it held.
     */
    class Choice extends JComboBox<String>
    {
        private final transient IntConsumer door;
        private Object held;

        /** True while a key or `putBack` moves the list, which then only shows. */
        private boolean quiet;
        private boolean canceled;

        /** @param door given the index chosen */
        Choice(IntConsumer door, String... items)
        {
            super(items);

            this.door = door;
            this.held = getSelectedItem();

            addPopupMenuListener(new PopupMenuListener()
            {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e)
                {
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e)
                {
                    canceled = true;
                }

                // Once closed: the door may ask
                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
                {
                    final boolean undo = canceled;

                    canceled = false;
                    SwingUtilities.invokeLater(undo ? Choice.this::putBack : Choice.this::settle);
                }
            });

            addFocusListener(new FocusAdapter()
            {
                // Another window took the keyboard (a loss for now): put back, so no question swallows its click
                @Override
                public void focusLost(FocusEvent e)
                {
                    if (e.isTemporary()) putBack();
                    else if (!isPopupVisible()) settle();
                }
            });

            onKey(KeyEvent.VK_ENTER, this::settle);
            onKey(KeyEvent.VK_ESCAPE, this::putBack);
        }

        /** Whether a choice is shown and not made. */
        private boolean pending()
        {
            return !Objects.equals(getSelectedItem(), held);
        }

        /** A key of the closed list with a choice pending; otherwise the key goes where it went. */
        private void onKey(int key, Runnable action)
        {
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), action);
            getActionMap().put(action, new AbstractAction()
            {
                @Override
                public boolean isEnabled()
                {
                    return !isPopupVisible() && pending();
                }

                @Override
                public void actionPerformed(ActionEvent e)
                {
                    action.run();
                }
            });
        }

        @Override
        public void processKeyEvent(KeyEvent e)
        {
            quiet = e.getKeyCode() != KeyEvent.VK_ENTER;

            try
            {
                super.processKeyEvent(e);
            }
            finally
            {
                quiet = false;
            }
        }

        @Override
        protected void fireActionEvent()
        {
            if (reading) held = getSelectedItem();
            else if (!quiet && !isPopupVisible()) settle();
        }

        /** Applies the choice shown, while the window is still about its square and the list on it (a reload rebuilds some). */
        private void settle()
        {
            if (tile == null || !isDisplayable() || !pending()) return;

            held = getSelectedItem();
            door.accept(getSelectedIndex());
        }

        private void putBack()
        {
            quiet = true;
            setSelectedItem(held);
            quiet = false;
        }
    }

    /** Names a control and gives it its font. */
    static <T extends JComponent> T styled(T part, String name, Font font)
    {
        part.setName(name);
        part.setFont(font);

        return part;
    }
}
