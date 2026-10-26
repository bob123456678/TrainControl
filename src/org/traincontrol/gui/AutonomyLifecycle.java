package org.traincontrol.gui;

import org.traincontrol.gui.autonomy.AutonomyBanner;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.widgets.RestoreAction;
import org.traincontrol.util.I18n;

/** The autonomy menu and the controls mounted around it: the bar's Autonomy menu, the diagram strip, the Auto tabs. */
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
}
