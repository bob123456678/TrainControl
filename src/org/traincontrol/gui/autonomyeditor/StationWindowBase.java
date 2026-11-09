package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.RunGates;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * What Station Settings and the train window share (FR-111): non-modal, hung off the window that asked, about one square.
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

    /** The yellow banner naming the stations picked together; hidden for one. */
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

        // Applied before whatever is clicked next: the editor's Save or Cancel, or a question of this window's
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

        strip.setName("station.strip");
        picked.setName("station.picked");

        banners.add(picked, BorderLayout.NORTH);
        banners.add(strip, BorderLayout.CENTER);

        // Escape closes; a field's own Escape is tried first, and puts back what it held
        getRootPane().registerKeyboardAction(e -> closeWindow(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
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
        close.addActionListener(e -> closeWindow());

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

    /** Points the window at a square and shows it, beside the window that asked. */
    final void aim(TileKey square)
    {
        commitFields();

        boolean first = !isVisible();

        this.tile = square;

        reload();

        pack();

        if (first)
        {
            Window parent = getOwner();

            if (parent != null) setLocation(AutonomyEditorPanel.besideOwner(parent.getBounds(), getSize()));

            watch.start();
            setVisible(true);
        }

        toFront();
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

    /** Commits a field being typed in, as leaving it does. */
    void commitFields()
    {
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

    /** Names the stations picked together in the yellow banner at the top; null takes it down. */
    final void sayWhichArePicked(String said)
    {
        picked.setText(said == null ? "" : AutonomyEditorPanel.wrappedAt(said, stripWrapsAt));

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
     * A control's press: asked again at the click (a run may have begun), then the door, whose questions hang off this
     * window, then saved, drawn and rebuilt as the menus' items do.  A door answering false changed nothing, so nothing
     * is written or rebuilt.
     */
    final void act(BooleanSupplier door)
    {
        editor.pressedFrom(this, () ->
        {
            if (editor.refusedWhileRunning())
            {
                reload();

                return;
            }

            boolean changed = false;

            try
            {
                changed = door.getAsBoolean();
            }
            catch (RuntimeException ex)
            {
                editor.reportFailedDoor(ex);
            }

            if (changed) editor.placementChanged(null);
        });

        reload();
    }

    /** A control's press whose door saves and draws for itself. */
    final void actAlone(Runnable door)
    {
        editor.pressedFrom(this, () ->
        {
            if (!editor.refusedWhileRunning()) door.run();
        });

        reload();
    }

    /** Closed by the user: what is being typed applies, as leaving the field does. */
    final void closeWindow()
    {
        commitFields();

        goAway();
    }

    /** Closed because its surface or square has gone. */
    final void goAway()
    {
        tile = null;

        watch.stop();

        dispose();

        editor.stationWindows.closed(this);
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
    static final class RoomyPanel extends JPanel
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

    /** Names a control and gives it its font. */
    static <T extends JComponent> T styled(T part, String name, Font font)
    {
        part.setName(name);
        part.setFont(font);

        return part;
    }
}
