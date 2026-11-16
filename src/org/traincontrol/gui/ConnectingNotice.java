package org.traincontrol.gui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * The window's arrival on screen: the menus the connecting notice holds and gives back, the work that waits for the
 * notice to go, and the one raise to the foreground with the keyboard after it (OB-170).  The notice's state stays on
 * the window, which puts the notice up.
 */
final class ConnectingNotice
{
    private final TrainControlUI ui;

    ConnectingNotice(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /** How long the window manager gets to act on a raise before the topmost flag is handed back. */
    private static final int FOREGROUND_SETTLE_MS = 400;

    /** Gives the window its own content back; safe when no notice was shown. */
    void connectingFinished()
    {
        if (this.ui.contentBehindTheNotice == null) return;

        this.ui.setContentPane(this.ui.contentBehindTheNotice);

        final List<Runnable> waiting;

        synchronized (this.ui.afterTheNotice)
        {
            this.ui.contentBehindTheNotice = null;

            waiting = new ArrayList<>(this.ui.afterTheNotice);

            this.ui.afterTheNotice.clear();
        }

        ungreyTheMenus();

        this.ui.revalidate();
        this.ui.repaint();

        // Posted, so the window has painted its own content before the first of them speaks
        for (Runnable one : waiting) SwingUtilities.invokeLater(one);
    }

    /** Runs this on the event thread once the connecting notice is gone (OB-349); at once where there is none. */
    void whenTheConnectingNoticeIsGone(Runnable action)
    {
        synchronized (this.ui.afterTheNotice)
        {
            if (this.ui.contentBehindTheNotice != null)
            {
                this.ui.afterTheNotice.add(action);

                return;
            }
        }

        SwingUtilities.invokeLater(action);
    }

    /** Whether the connecting notice still holds the menu bar; menus decided while connecting ask this (OB-187). */
    boolean menusAreHeldByTheNotice()
    {
        return this.ui.theNoticeHoldsTheMenus;
    }

    /**
     * Grays out each menu, not the bar (setEnabled(false) leaves menus opening); only what this turned off comes back.
     */
    void greyTheMenus()
    {
        // Set before walking the bar: a menu mounted during the connect asks it afterwards.
        this.ui.theNoticeHoldsTheMenus = true;

        JMenuBar bar = this.ui.getJMenuBar();

        if (bar == null) return;

        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            JMenu menu = bar.getMenu(i);

            // getMenu returns null for anything in the bar that is not a JMenu - a glue or a strut.
            if (menu == null || !menu.isEnabled()) continue;

            menu.setEnabled(false);

            this.ui.menusGreyedByTheNotice.add(menu);
        }
    }

    /** Gives back exactly the menus `greyTheMenus` took. */
    void ungreyTheMenus()
    {
        // First, because the menus asked below read it.
        this.ui.theNoticeHoldsTheMenus = false;

        for (JMenu menu : this.ui.menusGreyedByTheNotice) menu.setEnabled(true);

        this.ui.menusGreyedByTheNotice.clear();

        // The menus not on the bar when it was grayed.  Through `refreshAutonomyAnchor`, not the menu: enabling the
        // PLACEHOLDER would switch on a menu that opens onto nothing (OB-202).
        this.ui.refreshAutonomyAnchor();
    }

    /** Takes the window off the screen when start-up failed, so no notice hides the failure. */
    void connectingFailed()
    {
        // On the event thread whoever calls: the callers are failure paths, and hiding a frame off it races on paint.
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> connectingFailed());

            return;
        }

        connectingFinished();

        this.ui.setVisible(false);
    }

    /**
     * Puts the keyboard on the tabbed pane and locomotive panel, which read the letter keys (the frame is
     * non-focusable), once the window is up (OB-168); posted, as focus asked for during the showing event is dropped on
     * Windows.
     */
    void takeTheKeyboard()
    {
        // The window first, then what is in it (OB-170): restoring the topmost flag later resets the focus owner.
        SwingUtilities.invokeLater(() -> comeToTheForeground());

        // And again when the window gains focus with nothing focused: requestFocusInWindow does nothing until then.
        this.ui.addWindowFocusListener(new WindowAdapter()
        {
            @Override
            public void windowGainedFocus(WindowEvent e)
            {
                // Posted, measured: AWT restores the last focus owner while handling this event.
                SwingUtilities.invokeLater(() -> focusTheKeyboard());
            }
        });
    }

    /**
     * Makes this the active window, which on Windows `toFront()` does not (OB-170): only the always-on-top toggle gets
     * past the foreground rule.  Called once from start-up; every activation would pull the application in front of
     * whatever the operator switched to.
     */
    void comeToTheForeground()
    {
        // Unminimized first, clearing only the ICONIFIED bit so a maximized window stays maximized.
        if ((this.ui.getExtendedState() & Frame.ICONIFIED) != 0)
        {
            this.ui.setExtendedState(this.ui.getExtendedState() & ~Frame.ICONIFIED);
        }

        // The operator's setting, not `isAlwaysOnTop()`: the window arrives topmost on purpose.
        raiseOnce(TrainControlUI.getPrefs().getBoolean(TrainControlUI.ONTOP_SETTING_PREF, TrainControlUI.ONTOP_SETTING_DEFAULT), 1);
    }

    /**
     * One attempt at the front; tried twice, not looped, as an application that keeps pulling itself forward is worse.
     */
    void raiseOnce(final boolean wasOnTop, final int attempt)
    {
        this.ui.setAlwaysOnTop(true);

        this.ui.toFront();

        this.ui.requestFocus();

        javax.swing.Timer settle = new javax.swing.Timer(FOREGROUND_SETTLE_MS, event ->
        {
            // The operator's setting back, always: stuck above everything would disagree with the menu item.
            this.ui.setAlwaysOnTop(wasOnTop);

            if (!this.ui.isActive() && attempt < 2)
            {
                raiseOnce(wasOnTop, attempt + 1);

                return;
            }

            // And only now the keyboard (OB-170): the restore above resets the focus owner.
            SwingUtilities.invokeLater(this::focusTheKeyboard);
        });

        settle.setRepeats(false);
        settle.start();
    }

    /**
     * Puts the keyboard on the tabbed pane unless somebody is typing here, so it is safe on every activation.  Typing,
     * not focus (OB-170): focus on a label or button consumes no letters.
     */
    void focusTheKeyboard()
    {
        if (this.ui.keyboardTab() == null) return;

        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        if (owner instanceof JTextComponent && SwingUtilities.isDescendingFrom(owner, this.ui)) return;

        this.ui.keyboardTab().requestFocusInWindow();
    }
}
