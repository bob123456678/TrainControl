package org.traincontrol.gui;

import org.traincontrol.gui.autonomy.AutonomyBanner;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.widgets.RestoreAction;
import javax.swing.JOptionPane;
import org.traincontrol.util.I18n;

/** The autonomy session's life, and the menu, strip and Auto tabs mounted around it. */
final class AutonomyLifecycle
{
    private final TrainControlUI ui;

    AutonomyLifecycle(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Puts the autonomy menu in the bar, once, in the slot of the placeholder in the form (OB-202), so the bar has an
     * Autonomy menu from the first frame; the fallback keeps it beside Layouts if the placeholder is taken out.
     */
    void mountAutonomyMenu()
    {
        if (ui.autonomyMenu == null)
        {
            ui.autonomyMenu = new AutonomyMenu(ui);

            javax.swing.JMenuBar mainMenuBar = ui.mainMenuBar();

            int at = -1;

            for (int i = 0; i < mainMenuBar.getMenuCount(); i++)
            {
                if (mainMenuBar.getMenu(i) == ui.autonomyTopMenu())
                {
                    at = i;
                    break;
                }
            }

            if (at >= 0)
            {
                mainMenuBar.remove(ui.autonomyTopMenu());
            }
            else
            {
                // No placeholder: put it after Layouts, which is where one would have been.
                at = mainMenuBar.getMenuCount();

                for (int i = 0; i < mainMenuBar.getMenuCount(); i++)
                {
                    if (mainMenuBar.getMenu(i) == ui.layoutMenu())
                    {
                        at = i + 1;
                        break;
                    }
                }
            }

            mainMenuBar.add(ui.autonomyMenu, at);
            mainMenuBar.revalidate();
            mainMenuBar.repaint();
        }

        ui.autonomyMenu.refreshEnabled();
    }

    /** Wires the Layout menu's additions and the Autonomy menu for every layout, and the page menus for a local one. */
    void mountAutonomyControls()
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        // Above the no-session return: these concern the Layout menu, not a session, and a Central Station layout
        // always has a null `session`, so below it the guard, the data-source label and the headings would never exist
        // for it.
        ui.layoutMenus.mountLayoutHeadings();
        ui.layoutMenus.mountManagePages();
        ui.layoutMenus.mountRecentLayouts();
        mountRestoreFromBackup();
        mountFindRoute();

        if (ui.layoutMenu() != null && ui.layoutMenu().getMenuListeners().length == 0)
        {
            ui.layoutMenu().addMenuListener(new javax.swing.event.MenuListener()
            {
                @Override
                public void menuSelected(javax.swing.event.MenuEvent e)
                {
                    ui.layoutMenus.guardLayoutMenu();
                }

                @Override
                public void menuDeselected(javax.swing.event.MenuEvent e) { }

                @Override
                public void menuCanceled(javax.swing.event.MenuEvent e) { }
            });
        }

        // For every layout, and above the return: built after it, the menu never existed for a window that started on a
        // Central Station layout, where it opens onto the download offer and Documentation.
        mountAutonomyMenu();

        // No local copy, no setup (OB-254): the menu above is what such a layout gets.
        if (session == null) return;

        if (ui.autonomyViewerPanel == null)
        {
            ui.autonomyViewerPanel = new AutonomyViewerPanel(session, ui);
        }

        // Built but not shown: it stays as the home of the actions the Autonomy menu runs (a few hundred lines of
        // dialogs and file handling).

        ui.layoutMenus.mountEditPageMenu();
    }

    /** Shows or hides the tabs that only mean something with a valid autonomy layout loaded. */
    void setAutonomyDependentTabs(boolean valid)
    {
        if (!valid)
        {
            ui.locCommandPanels().remove(ui.locCommandTab());
            ui.locCommandPanels().remove(ui.timetablePanel());
            ui.locCommandPanels().remove(ui.autoSettingsPanel());

            // An empty Auto tab says nothing about why, so gray it, like the track diagram tab with no layout.
            ui.setAutoTabEnabled(false);

            ui.startAutonomyButton().setEnabled(false);

            // Not a bare setEnabled(false): that would keep a previous session's tooltip on the button.
            ui.disableReturnHome(I18n.t("autosetup.ui.whyNoLayout"));

            return;
        }

        ui.setAutoTabEnabled(true);

        // remove-then-insert so a second load does not stack duplicates
        ui.locCommandPanels().remove(ui.locCommandTab());
        ui.locCommandPanels().remove(ui.timetablePanel());
        ui.locCommandPanels().remove(ui.autoSettingsPanel());

        ui.locCommandPanels().insertTab(I18n.t("autolayout.ui.tabLocomotiveCommands"),
            null, ui.locCommandTab(), null, 0);
        ui.locCommandPanels().insertTab(I18n.t("autolayout.ui.tabTimetable"),
            null, ui.timetablePanel(), null, 1);
        ui.locCommandPanels().insertTab(I18n.t("autolayout.ui.tabAutonomySettings"),
            null, ui.autoSettingsPanel(), null, 2);

        ui.autonomySettingsTab.loadAutoLayoutSettings();

        ui.startAutonomyButton().setEnabled(true);
        ui.refreshReturnHomeButton();
        ui.executeTimetableButton().setEnabled(true);
        ui.gracefulStopButton().setEnabled(false);

        // grayed again if the trains still run
        ui.refreshWhatWaitsForTheTrains();
    }

    /**
     * The panel for editing the live setup outside the editor (FR-129); null with no local setup or while an editor
     * holds it.
     */
    AutonomyEditorPanel liveSetupPanel()
    {
        if (!ui.isLocalLayout()) return null;

        // Not while an editor holds the setup (OB-076): the editor's Cancel restores autonomyAsOpened and saves it,
        // silently reverting an edit made here.  No menu rather than a grayed one, because this menu IS the autonomy
        // surface here.
        if (ui.isLayoutEditorOpen()) return null;

        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null) return null;

        if (ui.autonomyTileMenus == null || ui.autonomyTileMenusFor != session)
        {
            ui.autonomyTileMenusFor = session;

            // Switched on after the panel exists: its constructor's refresh reports a change and would save the setup.
            final boolean[] listening = { false };

            ui.autonomyTileMenus = new AutonomyEditorPanel(session, null, () ->
            {
                if (!listening[0]) return;

                try
                {
                    // Logged rather than shown (this fires on every edit), but the refusal has to reach somebody.
                    ui.noteIfTheSetupWasNotTidied(session.save());
                }
                catch (java.io.IOException e)
                {
                    // The edit stands in the session either way; only the record of it is at risk
                    ui.getModel().log(e);
                }

                ui.refreshStaticAutonomyLayer();
                ui.updateVisiblePoints();
            });

            ui.autonomyTileMenus.setMenuOnly(true);

            ui.autonomyTileMenus.setDialogOwner(ui);

            // This panel is never added to anything, so its walk up the window tree finds no main window (see
            // parentWindow).
            ui.autonomyTileMenus.setMainWindow(ui);

            // The two items that want a second click hand themselves to the editor instead.
            ui.autonomyTileMenus.setOnJumpToPage(where -> ui.openAutonomyEditor(where));

            // A link's other end: no window is being left here, so nothing to ask; opens the editor on that page.
            ui.autonomyTileMenus.setOnJumpToLink(where -> ui.openAutonomyEditor(where));

            // Asked for on every use, not held: loading a configuration replaces the Layout wholesale.
            ui.autonomyTileMenus.setLayoutSource(() -> ui.getModel().getAutoLayoutIfLoaded());

            ui.autonomyTileMenus.setLocomotiveNames(() ->
                ui.getModel() == null ? new java.util.ArrayList<String>() : ui.getModel().getLocList());

            // Writing a station name changes the tile art, so rebuild the diagram rather than repaint.
            ui.autonomyTileMenus.setOnDiagramChanged(() ->
                javax.swing.SwingUtilities.invokeLater(() -> ui.repaintLayout()));

            listening[0] = true;
        }

        return ui.autonomyTileMenus;
    }

    /**
     * The strip above the track diagram, made once and kept: a banner for what the user needs to know about autonomy,
     * and the controls for a running setup.  Built even when nothing is loaded, or an unloaded setup would say nothing.
     */
    void ensureDiagramStrip()
    {
        if (ui.autonomyOverlayToggle != null) return;

        ui.autonomyOverlayToggle = new AutonomyOverlayToggle(ui);

        ui.autonomyDiagramBanner = new AutonomyBanner();

        // Starts hidden; it appears when it has something to offer.  No X: this offer is a state with its button.
        ui.autonomyDiagramBanner.setVisible(false);

        ui.autonomyDiagramStrip = new javax.swing.JPanel(new java.awt.BorderLayout());

        javax.swing.JPanel strip = ui.autonomyDiagramStrip;
        strip.setOpaque(true);
        strip.setBackground(java.awt.Color.WHITE);

        strip.add(ui.autonomyDiagramBanner, java.awt.BorderLayout.NORTH);
        strip.add(ui.autonomyOverlayToggle, java.awt.BorderLayout.CENTER);

        // Nothing in the main window may hold the keyboard: bare key presses drive locomotives
        AutonomyViewerPanel.unfocusable(strip);

        // Above the scroll pane, not inside it (OB-148): a column header scrolls with the view and flickered on a wide
        // diagram.  Swapped in at runtime because the layout comes from the generated form.
        java.awt.Container home = ui.layoutArea().getParent();

        if (home != null && home.getLayout() instanceof javax.swing.GroupLayout)
        {
            javax.swing.JPanel stacked = new javax.swing.JPanel(new java.awt.BorderLayout());

            stacked.setOpaque(false);

            ((javax.swing.GroupLayout) home.getLayout()).replace(ui.layoutArea(), stacked);

            stacked.add(strip, java.awt.BorderLayout.NORTH);
            stacked.add(ui.layoutArea(), java.awt.BorderLayout.CENTER);

            home.revalidate();
            home.repaint();
        }
        else
        {
            // Whatever the form does next, the strip must appear somewhere; the column header will do.
            ui.layoutArea().setColumnHeaderView(strip);
            ui.layoutArea().revalidate();
        }
    }

    /** The setup for the loaded layout, created on demand; null when the layout cannot hold one (no local folder). */
    org.traincontrol.automationui.AutonomySession getAutonomySession()
    {
        if (ui.autonomySession != null) return ui.autonomySession;

        ui.autonomySetupUnreadable = null;

        String path = ui.localLayoutPath();

        if (path == null || path.isEmpty()) return null;

        org.traincontrol.automationui.AutonomySession session =
            new org.traincontrol.automationui.AutonomySession(new java.io.File(path));

        if (!session.isUsable()) return null;

        try
        {
            java.util.List<org.traincontrol.base.LayoutDiagram> pages = new java.util.ArrayList<>();

            for (String name : ui.getModel().getLayoutList())
            {
                pages.add(ui.getModel().getLayout(name));
            }

            session.open(pages);

            // A setup left mid-edit is put back to how it was before the edit (OB-108), after open (pages known) and
            // before anything can save.  Not while the layout editor is open: `resetAutonomySession` throws the session
            // away, and the note that editor wrote on the way in would be acted on mid-edit.
            boolean editorOpen = ui.openEditor != null && ui.openEditor.isDisplayable();

            if (!editorOpen && session.revertUnfinishedEdit())
            {
                ui.getModel().logf("autosetup.log.unfinishedEditReverted");
            }
            else if (!editorOpen && session.unusableEditNote())
            {
                // Logged: an unreadable note is kept for a newer build and refused at every start.
                ui.getModel().logf("autosetup.log.unfinishedEditNoteUnusable");
            }

            // The pages it rewrote are logged: captions leave the `.cs2` one-way, so an older version finds them gone
            // (`saveChanges` keeps a `.cs2.bak` the first time).
            if (!session.getMigratedPages().isEmpty())
            {
                // The setup is saved before any page is written, so a page whose write throws still has its labels on
                // disk.
                ui.getModel().logf("autosetup.log.captionsMigrated",
                    String.join(", ", session.getMigratedPages()),
                    session.getMigratedCaptions());
            }

            // Reported once: the setup was saved that way as it opened (OB-351).
            for (String line : session.takeSquaresBroughtToTheirArrows())
            {
                ui.getModel().log(line);
            }

            // A page that could not be rewritten keeps its old labels and migrates again next time; the user is told,
            // since that page carries text the editor no longer manages.
            if (!session.getMigrationFailures().isEmpty())
            {
                final String pagesLeft = String.join(", ", session.getMigrationFailures());

                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(ui,
                        I18n.f("autosetup.ui.errorLabelWriteFailed", pagesLeft)));
            }
        }
        catch (Exception e)
        {
            ui.getModel().log(e);

            // Kept for the doors, which must not take an unreadable setup for a layout with no folder.
            ui.autonomySetupUnreadable = describeSetupFailure(e);

            return null;
        }

        // How the session learns a train's length (FR-046): a function and not a copied map, so the model stays the
        // only record of the number.
        session.setTrainLengthSource(name ->
        {
            org.traincontrol.base.Locomotive loc =
                name == null ? null : ui.getModel().getLocByName(name);

            return loc == null ? null : loc.getTrainLength();
        });

        // The running railway from the moment the session is made: refusing a direction or a link asks where its trains
        // stand, which a session told only by the editor would judge against the setup alone.
        session.setRunningLayoutSource(() -> ui.getModel() == null ? null : ui.getModel().getAutoLayoutIfLoaded());

        ui.autonomySession = session;

        return ui.autonomySession;
    }

    /** Drops the setup so the next request reads it again; called when the layout changes underneath it. */
    void resetAutonomySession()
    {
        // Save BEFORE forgetting: with activeDiagramConfiguration nulled the exit-path capture (gated on it) would skip
        // everything, and a re-download would drop every placement, home and setting made since the last save.  While
        // an edit waits this saves what the railway owns, which Open Layout on the folder in use and Combine Linked
        // Pages leave unfolded.
        ui.captureRunningLayout();

        ui.autonomySession = null;

        // The driver holds indexes derived from the old session and would light tiles from a stale graph.
        if (ui.diagramMonitorDriver != null) ui.diagramMonitorDriver.stop();

        // A replaced diagram reuses the registry's page-and-coordinate keys for different track, and register() would
        // replay the old badges and arrows onto the new labels.
        if (ui.diagramTileRegistry != null) ui.diagramTileRegistry.reset();

        setAutonomyDependentTabs(false);

        ui.autonomyViewerPanel = null;

        // Remembered, for a load of it to carry its trains across
        if (ui.activeDiagramConfiguration != null) ui.forgottenByTheReset = ui.activeDiagramConfiguration;

        ui.activeDiagramConfiguration = null;

        // Takes the trains' lines off: nothing is loaded now (OB-307).
        ui.refreshCoveredTrack();

        ui.autonomyTileMenus = null;
        ui.autonomyTileMenusFor = null;

        mountAutonomyControls();

        // After the session is built again, which an autonomy editor's title asks (FR-127)
        ui.refreshConfigurationTitles();
    }

    /** The confirm-and-stop gate for loading a configuration from the diagram (OB-254); true to proceed. */
    boolean prepareAutonomyReload()
    {
        if (ui.getModel() == null) return false;

        if (ui.getModel().hasAutoLayout() && ui.isAutonomyBusy())
        {
            int result = JOptionPane.showOptionDialog(
                ui,
                I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives"),
                I18n.t("loc.ui.dialogConfirmReset"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[1] // default to leaving the running layout alone
            );

            if (result != JOptionPane.YES_OPTION) return false;

            // The autonomy stop below does not stop a route (an s88 route can fire from an autonomy train).
            if (TrainControlUI.aRouteIsExecuting(ui.getModel()))
            {
                JOptionPane.showMessageDialog(ui, TrainControlUI.routeRunningRefusal(TrainControlUI.executingRouteNames(ui.getModel())));

                return false;
            }

            ui.gracefulStopRequested = true;

            // Counts a stop first, which every dispatched journey obeys; then autonomy stops choosing; then every train
            // stops.
            ui.getModel().getAutoLayout().stopEveryTrainWhereItIs();
        }

        ui.resetLayoutStationLabels();

        return true;
    }

    void repairAutonomyLocomotive(String from, String to)
    {
        // The session already built, not the lazy getter, which would create a setup out of nothing and then save it.
        org.traincontrol.automationui.AutonomySession session = ui.autonomySession;

        // The paused-trains record of how the railway was built and the edit session's folds also name locomotives
        // (OB-343); the fold applies them by name to the setup's list, so a stale name pauses the wrong train or
        // restores a deleted one.
        if (ui.settingsAsBuilt != null) org.traincontrol.automationui.AutonomyCompanionStore.repairLocomotiveInPausedList(
            ui.settingsAsBuilt, from, to);

        if (session != null) session.followALocomotiveName(from, to);

        // No session: repair the FILE without building one (OB-062), which would open pages, migrate and raise dialogs.
        if (session == null)
        {
            String path = ui.localLayoutPath();

            if (path == null || path.isEmpty()) return;

            try
            {
                org.traincontrol.automationui.AutonomyCompanionStore.repairLocomotiveOnDisk(
                    new java.io.File(path), from, to);
            }
            catch (Exception e)
            {
                ui.getModel().log(e);
            }

            return;
        }

        // Nothing is written for a setup never saved: save() would create the folder and the file.
        if (!session.exists()) return;

        try
        {
            if (to == null) session.getStore().locomotiveDeleted(from);
            else session.getStore().locomotiveRenamed(from, to);

            // The note repair records its failure in a field; read it, or the next start's revert restores a deleted
            // locomotive.
            if (session.getStore().didTheNoteRepairFail())
            {
                ui.getModel().logf("autosetup.log.noteRepairFailed");
            }

            session.getStore().save();

            // The editor's Cancel snapshot still names the old locomotive; canceling would write it back over the
            // repair.
            if (ui.openEditor != null && ui.openEditor.isDisplayable())
            {
                ui.openEditor.autonomyLocomotiveRenamed(from, to);
            }

            // The window still shows the old name (MT-153): station labels and the locomotive panel repaint only when
            // called.  Posted, not called: this runs on the event thread inside a rename dialog, and
            // updateVisiblePoints is synchronized on this window and reaches into the layout, so taking both locks in
            // that order here deadlocks.
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                ui.updateVisiblePoints();
                ui.repaintAutoLocListLite();

                // Every timetable row names a locomotive (MT-149).
                ui.repaintTimetable();
            });
        }
        catch (Exception e)
        {
            // Quiet: the rename has already happened and a failed sidecar write must not turn it into an error.
            ui.getModel().log(e);
        }
    }

    /** Re-runs the checks and shows the result beside the overlay checkbox; cheap enough for every autonomy action. */
    void refreshAutonomyFindings()
    {
        if (ui.autonomyOverlayToggle == null) return;

        org.traincontrol.automationui.AutonomySession session = getAutonomySession();

        Object showing = ui.layoutList().getSelectedItem();
        String page = showing == null ? null : showing.toString();

        boolean excluded = page != null && ui.isPageExcludedFromAutonomy(page);

        ui.autonomyOverlayToggle.setPageExcluded(excluded);

        // The whole band, not only the half holding the controls, or the header reads as a drawing fault.
        if (ui.autonomyDiagramStrip != null)
        {
            ui.autonomyDiagramStrip.setBackground(excluded
                ? AutonomyOverlayToggle.EXCLUDED_BACKGROUND : java.awt.Color.WHITE);

            ui.autonomyDiagramStrip.repaint();
        }

        // No setup (getAutonomySession() builds a session for any local layout), or none loaded (OB-029): a finding is
        // about the configuration being run, and the banner's offer to load one is all worth saying then.
        if (session == null || session.getReducer() == null || !session.exists()
            || ui.activeDiagramConfiguration == null)
        {
            ui.autonomyOverlayToggle.setFindings(0, 0, 0, 0, null);
            return;
        }

        int errors = 0;
        int warnings = 0;
        int pageErrors = 0;
        int pageWarnings = 0;

        org.traincontrol.automationui.TileGraph.TileKey first = null;
        org.traincontrol.automationui.TileGraph.TileKey firstHere = null;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            org.traincontrol.automationui.AutonomyChecks.Severity severity = finding.getSeverity();

            boolean isError =
                severity == org.traincontrol.automationui.AutonomyChecks.Severity.ERROR;

            // Notices and information are not warnings; the editor lists them under a heading of their own.
            if (!isError && severity != org.traincontrol.automationui.AutonomyChecks.Severity.WARNING)
            {
                continue;
            }

            // Counted twice on purpose: on the page in front of the user, and anywhere.
            boolean onThisPage = finding.getTile() != null && page != null
                && page.equals(finding.getTile().getPage());

            if (isError)
            {
                errors++;

                if (onThisPage) pageErrors++;
            }
            else
            {
                warnings++;

                if (onThisPage) pageWarnings++;
            }

            // Most serious first, so the first one with a square is the one worth landing on.
            if (first == null && finding.getTile() != null) first = finding.getTile();

            if (firstHere == null && onThisPage) firstHere = finding.getTile();
        }

        // Somewhere on this page when there is something here, and the worst one anywhere otherwise
        ui.autonomyOverlayToggle.setFindings(pageErrors, pageWarnings, errors, warnings,
            firstHere != null ? firstHere : first);
    }

    /** Offers to load this layout's setup when one is waiting; the strip below takes over once one is loaded. */
    void refreshAutonomyPrompt()
    {
        // Which stations autonomy can choose has just changed, and the captions follow it (FR-023).
        ui.refreshCaptionVisibility();

        org.traincontrol.automationui.AutonomySession session = getAutonomySession();

        boolean waiting = session != null && session.exists()
            && ui.activeDiagramConfiguration == null
            && !session.getStore().getConfigurationNames().isEmpty();

        if (!waiting)
        {
            if (ui.autonomyDiagramBanner != null)
            {
                ui.autonomyDiagramBanner.offer(null, null, null);

                ui.layoutArea().revalidate();
                ui.layoutArea().repaint();
            }

            if (ui.autonomyOverlayToggle != null)
            {
                ui.autonomyOverlayToggle.setLoaded(ui.activeDiagramConfiguration != null);
                ui.autonomyOverlayToggle.setBannerShowing(false);
            }

            return;
        }

        ensureDiagramStrip();

        ui.autonomyOverlayToggle.setLoaded(false);

        // The banner says this in words, so the count below it would be the same news twice.
        ui.autonomyOverlayToggle.setBannerShowing(true);

        // The chosen configuration (the one the menu would run), else the first, since one is the common case.
        final String chosen = session.getStore().getActiveConfiguration() != null
            ? session.getStore().getActiveConfiguration()
            : session.getStore().getConfigurationNames().get(0);

        // A setup that cannot be built is not offered for loading, which would refuse; the way on is the editor.
        if (session.hasBlockingProblems())
        {
            // Opening is not refused with an editor open, unlike loading below: `openAutonomyEditor` brings it forward.
            ui.autonomyDiagramBanner.offer(I18n.t("autosetup.ui.bannerSetupCannotRun"),
                I18n.t("autosetup.ui.btnFixSetup"),
                () -> ui.openAutonomyEditor(null));
        }
        else
        {
            // The configuration is deliberately not named: the banner is one line, and the button's own menu names it.
            ui.autonomyDiagramBanner.offer(I18n.t("autosetup.ui.bannerSetupNotLoaded"),
                I18n.t("autosetup.ui.btnLoadConfiguration"),
                () ->
                {
                    if (ui.refuseWhileEditorOpen()) return;

                    AutonomyViewerPanel actions = ui.getAutonomyViewerPanel();

                    if (actions == null || actions.refusedWhileRunning()) return;

                    actions.setSelectedConfiguration(chosen);
                    actions.load(chosen, true);

                    ui.autonomyMenuActed();
                });
        }

        // Sized like the Start button that takes its place; after the offer, because the width follows the label.
        ui.autonomyOverlayToggle.styleAsRunButton(ui.autonomyDiagramBanner.getActionButton());

        // The header changes height as the banner comes and goes, so the scroll pane has to be told.
        ui.layoutArea().revalidate();
        ui.layoutArea().repaint();
    }

    /**
     * Stops using autonomy without deleting anything: the way back out of having loaded a configuration.
     * Returns false when the user declined to stop a run, which leaves everything alone.
     */
    boolean unloadAutonomy()
    {
        if (ui.getModel() == null) return false;

        // Not while a route or another run holds the setup; autonomy's own run is asked about below.
        if (ui.refusedWhileSomethingElseRuns()) return false;

        if (!prepareAutonomyReload()) return false;

        ui.writeTheTurnsOwed();

        // Folded before it is cleared: the reset below asks the model for its railway, which Unload empties first, so
        // nothing would be folded.  A train under way is written on the one point it is kept at.
        ui.captureRunningLayout(true);

        ui.getModel().clearAutoLayout();

        resetAutonomySession();

        ui.forgetTheRailway();

        ui.autonomyMenuActed();

        return true;
    }

    private void mountRestoreFromBackup()
    {
        if (ui.fileMenu() == null || ui.restoreDataMenuItem != null) return;

        ui.restoreDataMenuItem = new javax.swing.JMenuItem(I18n.t("ui.main.toolbar.restoreData"));

        ui.restoreDataMenuItem.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.restoreData")));

        ui.restoreDataMenuItem.addActionListener(e ->
        {
            // It replaces the layout folder, so it waits for a run and for the layout editor, as Open Layout does
            if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

            if (ui.refuseWhileEditorOpen()) return;

            new RestoreAction(ui).restoreFromBackup();
        });

        int backup = -1;

        for (int i = 0; i < ui.fileMenu().getMenuComponentCount(); i++)
        {
            if (ui.fileMenu().getMenuComponent(i) == ui.backupDataMenuItem()) backup = i;
        }

        ui.fileMenu().add(ui.restoreDataMenuItem, backup + 1);
    }

    /**
     * Adds Find Route to the routes menu.  Hand-mounted: anything declared inside a GEN-BEGIN block is deleted when the
     * form is regenerated.  Safe to call twice.
     */
    private void mountFindRoute()
    {
        if (ui.routesMenu() == null || ui.findRouteMenuItem != null) return;

        ui.findRouteMenuItem = new javax.swing.JMenuItem(I18n.t("route.ui.menuFindRoute"));

        ui.findRouteMenuItem.addActionListener(e -> ui.findRoute());

        // At the top, above Export and Import, which are about the route list as a file.
        ui.routesMenu().add(ui.findRouteMenuItem, 0);
        ui.routesMenu().add(new javax.swing.JPopupMenu.Separator(), 1);
    }

    /** A setup refused as written by a newer TrainControl arrives as a message KEY plus versions; translated here. */
    private static String describeSetupFailure(Exception failure)
    {
        String message = failure.getMessage();

        if (message == null || message.trim().isEmpty()) return failure.getClass().getSimpleName();

        String version = org.traincontrol.automationui.AutonomyCompanionStore.ERROR_VERSION;

        return message.startsWith(version) ? I18n.t(version) + message.substring(version.length()) : message;
    }
}
