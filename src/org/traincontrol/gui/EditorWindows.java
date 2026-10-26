package org.traincontrol.gui;

import static org.traincontrol.gui.TrainControlUI.LAST_EDITOR_AUTONOMY_PREF;
import static org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_DEFAULT;
import static org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF;
import javax.swing.JOptionPane;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;

/** The window's editors: opening and closing the layout and autonomy editors, and whether the diagram may be edited. */
final class EditorWindows
{
    private final TrainControlUI ui;

    EditorWindows(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Whether every window holding unsaved work is willing to be closed: the exit's first question (`WindowClosed` ends
     * in `System.exit`).  The layout editor is asked first (OB-070).
     */
    boolean everyOpenWindowMaySettle()
    {
        if (ui.openEditor != null && ui.openEditor.isDisplayable() && !ui.openEditor.maySettleBeforeExit()) return false;

        // A non-modal frame with its own discard question; without it File > Exit would drop typing silently.
        return ui.routeEditor == null || !ui.routeEditor.isDisplayable() || ui.routeEditor.maySettleBeforeExit();
    }

    /** Whether the route editor holds work the exit would throw away: `maySettleBeforeExit` without the dialog. */
    boolean routeEditorHasUnsavedWork()
    {
        return ui.routeEditor != null && ui.routeEditor.isDisplayable() && ui.routeEditor.hasUnsavedWork();
    }

    /**
     * Opens the diagram editor in autonomy mode on a tile's page and reveals it; a null tile just opens the current
     * page.
     */
    void openAutonomyEditor(final org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        openAutonomyEditor(tile, null);
    }

    /** As above, also outlining {@code related}, the squares a finding names beside its own (MT-505); may be null. */
    void openAutonomyEditor(final org.traincontrol.automationui.TileGraph.TileKey tile,
        final java.util.List<org.traincontrol.automationui.TileGraph.TileKey> related)
    {
        openLayoutEditor(tile == null ? null : tile.getPage(), Boolean.TRUE, tile, false, related);
    }

    /** Selects the page being edited before a window is built, since the editor edits one page. */
    void selectLayoutPage(String page)
    {
        if (page != null) ui.layoutList().setSelectedItem(page);
    }

    /**
     * Whether the Edit button may be pressed; an editor switching page or mode does not close, so it restores the
     * button.
     */
    void setEditLayoutEnabled(boolean enabled)
    {
        // Remembered, not applied: "an editor is open" and "there is a layout to edit" (OB-126) are re-asked
        // separately.
        ui.noEditorOpen = enabled;

        applyLayoutEditingAvailability();
    }

    /** Lights or grays everything that edits the track diagram; call it on every transition that changes the answer. */
    void applyLayoutEditingAvailability()
    {
        // Also no editor window: a switch's hand-over says no editor is open while its window still is.
        boolean live = ui.noEditorOpen && !isLayoutEditorOpen() && layoutCanBeEdited();

        ui.editLayoutButton().setEnabled(live);

        // Manage Pages goes with it: it is the same permission, asked about the same folder.
        if (ui.modifyLocalLayoutMenu() != null) ui.modifyLocalLayoutMenu().setEnabled(live);

        // Lit with an editor open too, as it brings that editor forward
        if (ui.managePagesItem != null) ui.managePagesItem.setEnabled(layoutCanBeEdited());

        // The settings panel's button goes with it: both open the same editor and ask the same guard, so it must not
        // stay lit while an editor is open (OB-057).  Set here rather than at the four places that enable the layout
        // button.
        if (ui.editAutonomyFromSettings() != null)
        {
            ui.editAutonomyFromSettings().setEnabled(live);
        }
    }

    /** Whether there is a track diagram of the user's own to edit (OB-126): not with a CS layout or none loaded. */
    boolean layoutCanBeEdited()
    {
        return isLayoutLoaded() && ui.isLocalLayout();
    }

    /**
     * Whether a track diagram is loaded (OB-127): stored because `getLayoutList().isEmpty()` asked at many places gave
     * different answers.  Volatile: set on the event thread, read from the workers that rebuild the grid.
     */
    private volatile boolean layoutLoaded = false;

    /** Whether a track diagram is loaded; the stored answer. */
    boolean isLayoutLoaded()
    {
        return this.layoutLoaded;
    }

    /** Stores the model's answer; the only assignment to {@link #layoutLoaded}. */
    void updateLayoutLoaded()
    {
        this.layoutLoaded = ui.getModel() != null
            && ui.getModel().getLayoutList() != null
            && !ui.getModel().getLayoutList().isEmpty();
    }

    /** Why the Edit Layout button is gray, as a message key (OB-126); asks in `layoutCanBeEdited`'s order. */
    String reasonLayoutIsNotEditable()
    {
        String why = whyLayoutCannotBeEdited();

        // Never null in practice; a dialog saying "null" would be worse than a slightly wrong sentence.
        return why != null ? why : "autosetup.ui.errorEditorAlreadyOpen";
    }

    String whyLayoutCannotBeEdited()
    {
        if (!isLayoutLoaded())
        {
            return "layout.ui.errorNoLayoutLoaded";
        }

        if (!ui.isLocalLayout()) return "layout.ui.errorEditingOnlySupportedForLocalFiles";

        // Last: it is about a window, not the layout, and the caller may answer it by bringing that window forward.
        if (!ui.noEditorOpen) return "autosetup.ui.errorEditorAlreadyOpen";

        return null;
    }

    /** Remembers the editor the user last chose in the sidebar; not called for a mode forced back to the track. */
    void rememberEditorChoice(boolean autonomy)
    {
        TrainControlUI.getPrefs().putBoolean(LAST_EDITOR_AUTONOMY_PREF, autonomy);
    }

    /**
     * Why the autonomy editor would refuse to open, as a message key, or null (FR-026): openLayoutEditor's refusals in
     * its order, with the same keys (`testEditorSurfaceRules`).  An editor already open is no refusal when a window can
     * be brought forward.
     */
    String whyAutonomyEditorCannotOpen()
    {
        if (!ui.isLocalLayout()) return "layout.ui.errorEditingOnlySupportedForLocalFiles";

        // A setup that is there but would not read is not a layout with nowhere to keep one; both keys are answered
        // here.
        if (ui.getAutonomySession() == null)
        {
            return ui.whyTheSetupCannotBeRead() != null
                ? "autosetup.ui.errorSetupUnreadable" : "autosetup.ui.errorNoSetupToEdit";
        }

        // Not while a route runs either: the edit refusal's own sentence (`RunGates.refuseWhileARunHoldsTheSetup`)
        if (ui.aRunHoldsTheSetup()) return RunGates.NO_EDITS_WHILE_RUNNING;

        if (!ui.editLayoutButton().isEnabled() && !isLayoutEditorOpen())
        {
            return "autosetup.ui.errorEditorAlreadyOpen";
        }

        return null;
    }

    /** A refusal `whyAutonomyEditorCannotOpen` handed back, as the sentence to show; an unreadable setup says why. */
    String sayWhyAutonomyEditorCannotOpen(String why)
    {
        if ("autosetup.ui.errorSetupUnreadable".equals(why))
        {
            return I18n.f("autosetup.ui.errorSetupUnreadable", String.valueOf(ui.whyTheSetupCannotBeRead()));
        }

        return I18n.t(why);
    }

    void openLayoutEditor(String page, Boolean autonomy,
        final org.traincontrol.automationui.TileGraph.TileKey reveal)
    {
        openLayoutEditor(page, autonomy, reveal, false);
    }

    /**
     * Opens the layout editor, on a page, in a mode: the one way in, so the guards cannot drift.  `page` null is the
     * page the main window shows; `autonomy` TRUE is the setup, FALSE the track, null the last used; `reveal` is a
     * square to scroll to and flash.  `remember` is whether this counts as the user CHOOSING a mode to come back to:
     * most ways in do not, and remembering them would silently change what the Edit button does next time.
     */
    void openLayoutEditor(String page, Boolean autonomy,
        final org.traincontrol.automationui.TileGraph.TileKey reveal, boolean remember)
    {
        openLayoutEditor(page, autonomy, reveal, remember, null);
    }

    /**
     * As above, with {@code outline}, the squares a finding names beside the revealed one, outlined with it (or null).
     */
    private void openLayoutEditor(String page, Boolean autonomy,
        final org.traincontrol.automationui.TileGraph.TileKey reveal, boolean remember,
        final java.util.List<org.traincontrol.automationui.TileGraph.TileKey> outline)
    {
        if (!ui.isLocalLayout())
        {
            JOptionPane.showMessageDialog(ui,
                I18n.t("layout.ui.errorEditingOnlySupportedForLocalFiles"));
            return;
        }

        // What can be OPENED is any session at all (a setup with blocking errors will not load, and the editor is the
        // only place to fix them); what can be DEFAULTED to is a configuration actually chosen.
        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        // When last time is no longer possible (a setup since unloaded): the track, silently, since the user asked to
        // edit.
        boolean wantsAutonomy = autonomy == null
            ? TrainControlUI.getPrefs().getBoolean(LAST_EDITOR_AUTONOMY_PREF, false) && editableAutonomySession() != null
            : autonomy;

        if (wantsAutonomy && session == null)
        {
            // An explicit request says why it cannot be honored; a remembered one just falls back
            if (autonomy != null)
            {
                // With the reason when the setup is there and would not read (the two keys whyAutonomyEditorCannotOpen
                // answers).
                JOptionPane.showMessageDialog(ui, ui.whyTheSetupCannotBeRead() != null
                    ? I18n.f("autosetup.ui.errorSetupUnreadable", ui.whyTheSetupCannotBeRead())
                    : I18n.t("autosetup.ui.errorNoSetupToEdit"));
                return;
            }

            wantsAutonomy = false;
        }

        // Neither editor while trains run (OB-047): a tile could be moved or deleted under a train on its way to it.
        // No silent fallback, as falling back means opening something; a route counts too (`aRunHoldsTheSetup`).
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        // One editor at a time, gated on the same button: two editors share one LayoutDiagram and one edit flag, so
        // closing either sets edit false under the other and re-enables the button for a third.
        if (!ui.editLayoutButton().isEnabled())
        {
            // Bring the editor FORWARD when there is one (OB-058); the dialog is for a disabled button with no editor
            // to show.
            if (isLayoutEditorOpen())
            {
                showOpenEditor();
                return;
            }

            // Which of the three reasons (OB-126); the already-open case was handled above.
            JOptionPane.showMessageDialog(ui, I18n.t(reasonLayoutIsNotEditable()));
            return;
        }

        setEditLayoutEnabled(false);

        // the editor edits one page, so the page has to be selected before it is opened
        if (page != null) ui.layoutList().setSelectedItem(page);

        // Remembered only when it is a choice: not when the mode was forced back to the track, a temporary fact about
        // right now.
        if (remember && autonomy != null)
        {
            TrainControlUI.getPrefs().putBoolean(LAST_EDITOR_AUTONOMY_PREF, wantsAutonomy);
        }

        // Folds where the trains actually are into the setup before the editor snapshots it (OB-144): a run leaves a
        // locomotive's position in `Point.currentLoc` alone, so Save would write back where the train STARTED.  Before
        // the constructor, which takes the undo point; a no-op with no session, no active configuration, or a run in
        // progress.
        ui.captureRunningLayout();

        final org.traincontrol.automationui.AutonomySession opening = wantsAutonomy ? session : null;

        // Whether a square was clicked in the editor this one replaces, asked now since the build is posted
        final boolean carried = clickCarried;

        // Set until the posted build shows the editor; every door that sends a train asks this too
        ui.editorOnItsWay = true;

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try
            {
                // Trains may have started since the question above (Start's worker, a staging flow): refuse, say so,
                // give Edit back.
                if (ui.refuseWhileARunHoldsTheSetup(ui, () ->
                    {
                        ui.editorOnItsWay = false;

                        setEditLayoutEnabled(true);
                    }))
                {
                    return;
                }

                LayoutEditor editor = ui.openEditor = new LayoutEditor(
                    ui.getModel().getLayout(ui.layoutList().getSelectedItem().toString()),
                    ui.layoutSizes.get(ui.sizeList().getSelectedItem().toString()),
                    ui,
                    ui.layoutList().getSelectedIndex()
                );

                ui.setAlwaysOnTop(false);

                editor.render();

                if (opening != null) editor.setAutonomyMode(opening);

                if (carried) editor.carryTheClick();

                // Runs after render()'s own posted task shows the editor
                javax.swing.SwingUtilities.invokeLater(() -> ui.editorOnItsWay = false);

                // Posted twice: render() and setAutonomyMode only QUEUE the work that builds the grid, so reveal must
                // wait for it.
                if (reveal != null)
                {
                    javax.swing.SwingUtilities.invokeLater(() ->
                        javax.swing.SwingUtilities.invokeLater(() ->
                        {
                            editor.reveal(reveal);

                            // Outline what the finding names beside it, as the editor's own list does (OB-352); a door
                            // that is not a finding passes none
                            if (outline != null && editor.getAutonomyPanel() != null)
                            {
                                editor.getAutonomyPanel().outlineTheNotice(reveal, outline);
                            }
                        }));
                }
            }
            catch (Exception | Error e)
            {
                // Give the button back, or autonomy setup is unreachable until restart.
                setEditLayoutEnabled(true);

                ui.editorOnItsWay = false;

                // Rethrow an Error after giving the button back, or Edit would stay grayed for the session with no
                // editor anywhere
                if (e instanceof Error) throw (Error) e;

                if (ui.getModel().isDebug()) ui.getModel().log((Exception) e);
            }
        });
    }

    /**
     * The setup, if there is any point opening: a CHOSEN configuration even if it did not load, since the editor is the
     * only place to fix its errors.  Null when there is nothing to set up.
     */
    org.traincontrol.automationui.AutonomySession editableAutonomySession()
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        return session != null && session.getStore().getActiveConfiguration() != null ? session : null;
    }

    boolean isLayoutEditorOpen()
    {
        // The editor, not the button that opens it (OB-126): the button is also grayed for a Central Station layout.
        return anEditorWindow() != null;
    }

    /**
     * An editor window of this window's that is still there, or null (newest first).  Asked of the windows, not of
     * `openEditor` or the Edit button, which a switch between editors gives back early.
     */
    LayoutEditor anEditorWindow()
    {
        if (ui.openEditor != null && ui.openEditor.isDisplayable()) return ui.openEditor;

        for (java.awt.Frame frame : java.awt.Frame.getFrames())
        {
            if (frame instanceof LayoutEditor && frame.isDisplayable() && ((LayoutEditor) frame).isEditorOf(ui))
            {
                return (LayoutEditor) frame;
            }
        }

        return null;
    }

    /** An editor window has gone: Edit and the doors beside it ask again.  Called from `LayoutEditor.dispose`. */
    void editorWindowClosed()
    {
        if (javax.swing.SwingUtilities.isEventDispatchThread())
        {
            applyLayoutEditingAvailability();

            applyAlwaysOnTop();
        }
        else
        {
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                applyLayoutEditingAvailability();

                applyAlwaysOnTop();
            });
        }
    }

    /** The operator's always-on-top setting, except while an editor window is open; see `editorWindowClosed`. */
    void applyAlwaysOnTop()
    {
        boolean editing = ui.openEditor != null && ui.openEditor.isDisplayable();

        ui.setAlwaysOnTop(!editing && TrainControlUI.getPrefs().getBoolean(ONTOP_SETTING_PREF, ONTOP_SETTING_DEFAULT));
    }

    /**
     * The layout editor's window while one is open where it can be seen, or null: for a question that has to open in
     * front of it (MT-575).  NOT a minimized one: on Windows its owned windows hide with it, leaving a modal invisible.
     */
    java.awt.Window openLayoutEditorWindow()
    {
        return ui.openEditor != null && ui.openEditor.isDisplayable() && ui.openEditor.isShowing()
            && (ui.openEditor.getExtendedState() & java.awt.Frame.ICONIFIED) == 0 ? ui.openEditor : null;
    }

    /** Brings the open editor forward, for the menu item that says "it is over there". */
    void showOpenEditor()
    {
        LayoutEditor shown = anEditorWindow();

        if (shown == null) return;

        shown.setVisible(true);
        shown.toFront();
        shown.requestFocus();
    }

    /** Whether the editor about to open starts knowing a square was clicked; set only during `openCarryingTheClick`. */
    private boolean clickCarried = false;

    /**
     * Opens an editor in place of one that closed itself to change page, carrying whether a square had been clicked.
     * Cleared whatever the opening does, so a refused opening cannot hand the click to the next Edit.
     */
    void openCarryingTheClick(boolean clicked, Runnable open)
    {
        clickCarried = clicked;

        try
        {
            open.run();
        }
        finally
        {
            clickCarried = false;
        }
    }

    /**
     * An autonomy editor closing (Save, Cancel, a jump): the refresh is counted before its window goes and taken over
     * by the posted `autonomyEditorClosed`.
     *
     * @param then run after `autonomyEditorClosed`, on the event thread, or null
     */
    void autonomyEditorClosedFromTheEditor(final Runnable then)
    {
        ui.refreshesUnderWay.incrementAndGet();

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try
            {
                autonomyEditorClosed();

                if (then != null) then.run();
            }
            finally
            {
                ui.refreshesUnderWay.updateAndGet(n -> Math.max(0, n - 1));
            }
        });
    }

    void autonomyEditorClosed()
    {
        // reapplies the stored preference, which is what the diagram path does here too
        ui.windowAlwaysOnTopAsChosen();

        setEditLayoutEnabled(true);

        ui.repaintLayout();

        ui.refreshPopupsWhoseCaptionsChanged();

        // Refresh the banner too: an editor closing never called its one decider, so a fix left it stale.
        ui.refreshAutonomyPrompt();

        // The running graph is rebuilt from what the setup now says: an edit that changes how a square SPLITS changes
        // its copies and their names.  Not while trains are moving.
        ui.rebuildRunningLayoutFromSetup();

        // The file is written (Adam: "on exit or editor load or editor close, save to the setup file.").  AFTER the
        // rebuild: capturing first would fold the pre-edit running layout over the configuration.
        ui.captureRunningLayout();
    }

    /** Opens the setup editor on a named page, switching the diagram to it first (a menu names no page). */
    void openAutonomyEditorOnPage(String page)
    {
        // A menu item that names the setup editor is somebody saying which editor they want
        openLayoutEditor(page, Boolean.TRUE, null, true);
    }
}
