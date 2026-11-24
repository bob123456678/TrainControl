package org.traincontrol.gui;

import static org.traincontrol.gui.TrainControlUI.LAST_EDITOR_AUTONOMY_PREF;
import static org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_DEFAULT;
import static org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import java.awt.Frame;
import java.util.List;

/** The window's editors: opening and closing the layout and autonomy editors, and whether the diagram may be edited. */
public final class EditorWindows
{
    private final TrainControlUI ui;

    EditorWindows(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Whether every window holding unsaved work is willing to be closed: the exit's first question; layout editor first
     * (OB-070).
     */
    public boolean everyOpenWindowMaySettle()
    {
        if (ui.openEditor != null && ui.openEditor.isDisplayable() && !ui.openEditor.maySettleBeforeExit()) return false;

        if (ui.locomotiveSettings != null && ui.locomotiveSettings.isDisplayable()
            && !ui.locomotiveSettings.maySettleBeforeExit()) return false;

        // A non-modal frame with its own discard question; without it File > Exit would drop typing silently.
        return ui.routeEditor == null || !ui.routeEditor.isDisplayable() || ui.routeEditor.maySettleBeforeExit();
    }

    /** Whether the route editor holds work the exit would throw away: `maySettleBeforeExit` without the dialog. */
    public boolean routeEditorHasUnsavedWork()
    {
        return ui.routeEditor != null && ui.routeEditor.isDisplayable() && ui.routeEditor.hasUnsavedWork();
    }

    /** Opens the diagram editor in autonomy mode on a tile's page and reveals it; null opens the current page. */
    public void openAutonomyEditor(final TileGraph.TileKey tile)
    {
        openAutonomyEditor(tile, null);
    }

    /** As above, also outlining {@code related}, the squares a finding names beside its own (MT-505); may be null. */
    public void openAutonomyEditor(final TileGraph.TileKey tile,
        final List<TileGraph.TileKey> related)
    {
        openLayoutEditor(tile == null ? null : tile.getPage(), Boolean.TRUE, tile, false, related);
    }

    /** Selects the page being edited before a window is built, since the editor edits one page. */
    public void selectLayoutPage(String page)
    {
        if (page != null) ui.layoutList().setSelectedItem(page);
    }

    /** Whether the Edit button may be pressed; an editor switching page or mode does not close, so it restores it. */
    public void setEditLayoutEnabled(boolean enabled)
    {
        // Remembered, not applied: "an editor is open" and "a layout to edit" (OB-126) are re-asked separately
        ui.noEditorOpen = enabled;

        applyLayoutEditingAvailability();
    }

    /** Lights or grays everything that edits the track diagram; call it on every transition that changes the answer. */
    void applyLayoutEditingAvailability()
    {
        // Also no editor window: a switch's hand-over says none is open while its window still is
        boolean live = ui.noEditorOpen && !isLayoutEditorOpen() && layoutCanBeEdited();

        ui.editLayoutButton().setEnabled(live);

        // Manage Pages goes with it: same permission, same folder
        if (ui.modifyLocalLayoutMenu() != null) ui.modifyLocalLayoutMenu().setEnabled(live);

        // Lit with an editor open too, as it brings that editor forward
        if (ui.managePagesItem != null)
        {
            ui.managePagesItem.setEnabled(layoutCanBeEdited());

            LayoutMenus.sayWhy(ui.managePagesItem, "ui.main.toolbar.tooltip.modifyLayout", whyLayoutCannotBeEdited());
        }

        // The settings panel's button goes with it: same editor, same guard (OB-057)
        if (ui.editAutonomyFromSettings() != null) ui.editAutonomyFromSettings().setEnabled(live);
    }

    /** Whether there is a track diagram of the user's own to edit (OB-126): not with a CS layout or none loaded. */
    boolean layoutCanBeEdited()
    {
        return isLayoutLoaded() && ui.isLocalLayout();
    }

    /**
     * Whether a track diagram is loaded (OB-127): stored, as asking `getLayoutList().isEmpty()` gave different answers.
     * Volatile.
     */
    private volatile boolean layoutLoaded = false;

    /** Whether a track diagram is loaded; the stored answer. */
    public boolean isLayoutLoaded()
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

        return why != null ? why : "autosetup.ui.errorEditorAlreadyOpen";
    }

    public String whyLayoutCannotBeEdited()
    {
        if (!isLayoutLoaded()) return "layout.ui.errorNoLayoutLoaded";

        if (!ui.isLocalLayout()) return "layout.ui.errorEditingOnlySupportedForLocalFiles";

        // Last: it is about a window, not the layout; the caller may bring that window forward
        if (!ui.noEditorOpen) return "autosetup.ui.errorEditorAlreadyOpen";

        return null;
    }

    /** Remembers the editor the user last chose in the sidebar; not called for a mode forced back to the track. */
    public void rememberEditorChoice(boolean autonomy)
    {
        TrainControlUI.getPrefs().putBoolean(LAST_EDITOR_AUTONOMY_PREF, autonomy);
    }

    /**
     * Why the autonomy editor would refuse to open, as a message key, or null (FR-026): openLayoutEditor's refusals in
     * its order.
     */
    public String whyAutonomyEditorCannotOpen()
    {
        if (!ui.isLocalLayout()) return "layout.ui.errorEditingOnlySupportedForLocalFiles";

        // A setup that is there but would not read: both keys are answered here
        if (ui.getAutonomySession() == null)
        {
            return ui.whyTheSetupCannotBeRead() != null
                ? "autosetup.ui.errorSetupUnreadable" : "autosetup.ui.errorNoSetupToEdit";
        }

        // Not while a route runs either (`RunGates.refuseWhileARunHoldsTheSetup`)
        if (ui.aRunHoldsTheSetup()) return RunGates.NO_EDITS_WHILE_RUNNING;

        if (!ui.editLayoutButton().isEnabled() && !isLayoutEditorOpen()) return "autosetup.ui.errorEditorAlreadyOpen";

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
        final TileGraph.TileKey reveal)
    {
        openLayoutEditor(page, autonomy, reveal, false);
    }

    /**
     * Opens the layout editor, on a page, in a mode: the one way in, so the guards cannot drift.  `page` null is the
     * main window's page; `autonomy` TRUE is the setup, FALSE the track, null the last used; `reveal` is a square to
     * flash.  `remember` is whether this is the user CHOOSING a mode; others must not change what Edit does next.
     */
    void openLayoutEditor(String page, Boolean autonomy,
        final TileGraph.TileKey reveal, boolean remember)
    {
        openLayoutEditor(page, autonomy, reveal, remember, null);
    }

    /** As above, with {@code outline}: the squares a finding names beside the revealed one (or null). */
    private void openLayoutEditor(String page, Boolean autonomy,
        final TileGraph.TileKey reveal, boolean remember,
        final List<TileGraph.TileKey> outline)
    {
        if (!ui.isLocalLayout())
        {
            JOptionPane.showMessageDialog(ui,
                I18n.t("layout.ui.errorEditingOnlySupportedForLocalFiles"));
            return;
        }

        // OPENED: any session (the editor is the only place to fix blocking errors); DEFAULTED to: a chosen
        // configuration
        final AutonomySession session = ui.getAutonomySession();

        // When last time is no longer possible: the track, silently, as the user asked to edit
        boolean wantsAutonomy = autonomy == null
            ? TrainControlUI.getPrefs().getBoolean(LAST_EDITOR_AUTONOMY_PREF, false) && editableAutonomySession() != null
            : autonomy;

        if (wantsAutonomy && session == null)
        {
            // An explicit request says why it cannot be honored; a remembered one just falls back
            if (autonomy != null)
            {
                // With the reason when the setup is there and would not read
                JOptionPane.showMessageDialog(ui, ui.whyTheSetupCannotBeRead() != null
                    ? I18n.f("autosetup.ui.errorSetupUnreadable", ui.whyTheSetupCannotBeRead())
                    : I18n.t("autosetup.ui.errorNoSetupToEdit"));
                return;
            }

            wantsAutonomy = false;
        }

        // Neither editor while trains run (OB-047): no silent fallback, as that would open something
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        // One editor at a time, gated on the same button: two share one LayoutDiagram and one edit flag
        if (!ui.editLayoutButton().isEnabled())
        {
            // Bring the editor forward when there is one (OB-058); the dialog is for a disabled button with none
            if (isLayoutEditorOpen())
            {
                showOpenEditor();
                return;
            }

            JOptionPane.showMessageDialog(ui, I18n.t(reasonLayoutIsNotEditable()));
            return;
        }

        setEditLayoutEnabled(false);

        // the editor edits one page, so the page has to be selected before it is opened
        if (page != null) ui.layoutList().setSelectedItem(page);

        // Remembered only when it is a choice, not when the mode was forced back to the track
        if (remember && autonomy != null)
        {
            TrainControlUI.getPrefs().putBoolean(LAST_EDITOR_AUTONOMY_PREF, wantsAutonomy);
        }

        // Folds where the trains are into the setup before the editor snapshots it (OB-144), or Save would write back
        // where they STARTED.  Before the constructor takes the undo point; a no-op with no session or a run going.
        ui.captureRunningLayout();

        final AutonomySession opening = wantsAutonomy ? session : null;

        // Whether a square was clicked in the editor this one replaces, asked now since the build is posted
        final boolean carried = clickCarried;
        final String filterToCarry = filterCarried;

        // Set until the posted build shows the editor; every door that sends a train asks this too
        ui.editorOnItsWay = true;

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                // Trains may have started since the question above: refuse, say so, give Edit back.
                if (ui.refuseWhileARunHoldsTheSetup(ui, () ->
                    {
                        ui.editorOnItsWay = false;

                        setEditLayoutEnabled(true);

                        ui.editorGone();
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

                if (filterToCarry != null) editor.carryTheFindingFilter(filterToCarry);

                // Runs after render()'s own posted task shows the editor
                SwingUtilities.invokeLater(() -> ui.editorOnItsWay = false);

                // Posted twice: render() only queues the work that builds the grid, so reveal must wait
                if (reveal != null)
                {
                    SwingUtilities.invokeLater(() ->
                        SwingUtilities.invokeLater(() ->
                        {
                            editor.reveal(reveal);

                            // Outline what the finding names beside it (OB-352); a door that is not a finding passes
                            // none
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

                ui.editorGone();

                // Rethrow an Error after giving the button back, or Edit stays grayed with no editor anywhere
                if (e instanceof Error) throw (Error) e;

                if (ui.getModel().isDebug()) ui.getModel().log((Exception) e);
            }
        });
    }

    /**
     * The setup, if there is any point opening: a CHOSEN configuration even if it did not load; null if nothing to set
     * up.
     */
    public AutonomySession editableAutonomySession()
    {
        AutonomySession session = ui.getAutonomySession();

        return session != null && session.getStore().getActiveConfiguration() != null ? session : null;
    }

    public boolean isLayoutEditorOpen()
    {
        // The editor, not the button that opens it (OB-126): the button is also grayed for a Central Station layout.
        return anEditorWindow() != null;
    }

    /**
     * An editor window of this window's that is still there, or null (newest first); asked of the windows, not the
     * button.
     */
    LayoutEditor anEditorWindow()
    {
        if (ui.openEditor != null && ui.openEditor.isDisplayable()) return ui.openEditor;

        for (Frame frame : Frame.getFrames())
        {
            if (frame instanceof LayoutEditor && frame.isDisplayable() && ((LayoutEditor) frame).isEditorOf(ui))
            {
                return (LayoutEditor) frame;
            }
        }

        return null;
    }

    /**
     * An editor window has gone: Edit and the doors beside it ask again, and a sync owed meanwhile runs.  From
     * `LayoutEditor.dispose`.
     */
    public void editorWindowClosed()
    {
        ui.editorGone();

        if (SwingUtilities.isEventDispatchThread())
        {
            applyLayoutEditingAvailability();

            applyAlwaysOnTop();
        }
        else
        {
            SwingUtilities.invokeLater(() ->
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
     * The layout editor's window while one is open where it can be seen, or null: for a question that opens in front of
     * it (MT-575).  Not a minimized one: its owned windows hide with it on Windows, leaving a modal invisible.
     */
    java.awt.Window openLayoutEditorWindow()
    {
        return ui.openEditor != null && ui.openEditor.isDisplayable() && ui.openEditor.isShowing()
            && (ui.openEditor.getExtendedState() & Frame.ICONIFIED) == 0 ? ui.openEditor : null;
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

    /** The findings filter's text the editor about to open starts with, or null; set only during `openCarryingTheClick`. */
    private String filterCarried = null;

    /**
     * Opens an editor in place of one that closed itself to change page, carrying a square-clicked flag and the
     * findings filter's text (FR-073; null carries none); cleared whatever happens.
     */
    public void openCarryingTheClick(boolean clicked, String findingFilter, Runnable open)
    {
        clickCarried = clicked;
        filterCarried = findingFilter;

        try
        {
            open.run();
        }
        finally
        {
            clickCarried = false;
            filterCarried = null;
        }
    }

    /**
     * An autonomy editor closing: the refresh is counted before its window goes and taken over by the posted
     * `autonomyEditorClosed`.  @param then runs after it on the event thread, or null.
     */
    public void autonomyEditorClosedFromTheEditor(final Runnable then)
    {
        ui.refreshesUnderWay.incrementAndGet();

        SwingUtilities.invokeLater(() ->
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

    public void autonomyEditorClosed()
    {
        // reapplies the stored preference, which is what the diagram path does here too
        ui.windowAlwaysOnTopAsChosen();

        setEditLayoutEnabled(true);

        ui.repaintLayout();

        ui.refreshPopupsWhoseCaptionsChanged();

        ui.autonomyLifecycle().refreshAutonomyPrompt();

        // The running graph is rebuilt from the setup, as an edit can change how a square SPLITS; not while trains move
        ui.rebuildRunningLayoutFromSetup();

        // The setup file is written when an editor closes; AFTER the rebuild, or the pre-edit layout would overwrite it
        ui.captureRunningLayout();
    }

    /** Opens the setup editor on a named page, switching the diagram to it first (a menu names no page). */
    public void openAutonomyEditorOnPage(String page)
    {
        // A menu item that names the setup editor is somebody saying which editor they want
        openLayoutEditor(page, Boolean.TRUE, null, true);
    }
}
