package org.traincontrol.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;
import org.traincontrol.gui.autonomy.DiagramExport;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.widgets.BusyDialog;
import org.traincontrol.gui.widgets.QuickFileChooser;
import org.traincontrol.util.I18n;

/** The window's Layouts menu (guard, headings, pages, Recent Layouts, picture export), drawing settings, Debug menu. */
final class LayoutMenus
{
    private final TrainControlUI ui;

    LayoutMenus(TrainControlUI ui)
    {
        this.ui = ui;
    }

    private javax.swing.JCheckBoxMenuItem allowedDirectionsMenuItem;

    /**
     * Grays the Layout menu while an editor has the diagram and offers the way back (OB-033): saving or rebuilding then
     * commits edits the editor has not saved.  Applied when the menu opens, when the answer is certainly current.
     */
    void guardLayoutMenu()
    {
        if (ui.layoutMenu() == null) return;

        boolean busy = ui.isLayoutEditorOpen();

        for (int i = 0; i < ui.layoutMenu().getMenuComponentCount(); i++)
        {
            java.awt.Component child = ui.layoutMenu().getMenuComponent(i);

            if (child == goToEditorItem) continue;

            // A heading is a disabled label by design; do not switch it back on.
            if (child == localHeading || child == centralStationHeading) continue;

            // Taken away, never handed back: `repaintPathLabel` owns the Central Station items and grays them when none
            // is reachable (OB-098), which IS their guard (the switch action checks no connection).
            // initializeLocalLayoutMenuItem likewise: its listener disables it before createAndApplyEmptyLayout runs
            // off the EDT, and only the completion callback re-enables it.
            if (child == ui.switchCSLayoutMenuItem() || child == ui.downloadCSLayoutMenuItem()
                || child == ui.initializeLocalLayoutMenuItem())
            {
                if (busy) child.setEnabled(false);

                continue;
            }

            child.setEnabled(!busy);
        }

        ui.applyLayoutEditingAvailability();

        refreshDataSourceLabel();

        // The items that need a local layout to act on (OB-128); left alone when an editor is open.
        if (!busy)
        {
            boolean workable = ui.layoutCanBeEdited();

            // Manage Pages is not set here: this menu, opening on its own schedule, disagreed with the owner,
            // `applyLayoutEditingAvailability`, during a layout database rebuild.
            if (ui.editPageMenu != null) ui.editPageMenu.setEnabled(workable && ui.isLayoutLoaded());

            refreshRecentLayouts();

            // Pop-outs and the picture export need loaded pages, not a local folder (a Central Station diagram works),
            // and the export would be handed the null page that activeLayoutPage returns when the selector is empty.
            boolean loaded = ui.isLayoutLoaded();

            if (ui.popUpAllMenuItem() != null) ui.popUpAllMenuItem().setEnabled(loaded);

            if (ui.exportDiagramItem != null) ui.exportDiagramItem.setEnabled(loaded);

            // Its own question: excluded from the loop above, and its only other writer never re-enables it, so opening
            // this menu once with an editor open grayed it for the session on a CS3.
            if (ui.openCS3AppMenuItem() != null) ui.openCS3AppMenuItem().setEnabled(
                ui.getModel() != null && ui.getModel().isCS3());
        }

        // While autonomy runs or a route holds the setup (`aRunHoldsTheSetup`), what changes the diagram or the folder
        // is grayed with the reason (each also refuses on the click, MT-141); the folder, pop-outs, picture and CS3 app
        // stay.
        boolean running = ui.isAutonomyBusy() || (ui.getModel() != null && ui.getModel().isAutonomyRunning())
            || ui.aRunHoldsTheSetup();

        String why = running ? I18n.t(RunGates.NO_EDITS_WHILE_RUNNING) : null;

        greyForTheRun(ui.chooseLocalDataFolderMenuItem(), running, false, why);
        greyForTheRun(recentLayoutsMenu, running, false, why);
        greyForTheRun(ui.modifyLocalLayoutMenu(), running, false, why);
        greyForTheRun(ui.managePagesItem, running, false, why);
        greyForTheRun(ui.editPageMenu, running, false, why);
        greyForTheRun(ui.initializeLocalLayoutMenuItem(), running, true, why);

        // The two Central Station items' owner works them out again once they are given back
        if (greyForTheRun(ui.switchCSLayoutMenuItem(), running, true, why)
            | greyForTheRun(ui.downloadCSLayoutMenuItem(), running, true, why))
        {
            ui.repaintPathLabel();
        }

        if (goToEditorItem == null)
        {
            goToEditorItem = new javax.swing.JMenuItem(I18n.t("autosetup.ui.menuEditorOpen"));

            goToEditorItem.addActionListener(e -> ui.showOpenEditor());

            ui.layoutMenu().add(goToEditorItem, 0);
        }

        goToEditorItem.setVisible(busy);
    }

    /** The "an editor has this diagram" item on the Layout menu. */
    private javax.swing.JMenuItem goToEditorItem;

    /** What a Layout menu item had before a run grayed it: whether it was live, and its tooltip */
    private static final String GREYED_FOR_THE_RUN = "greyedForTheRun";

    /**
     * Grays the item with `why` as its tooltip while a run goes and gives the tooltip back after it (the enabled state
     * too, when `owned` by something else); returns true when it was given back just now.
     */
    private static boolean greyForTheRun(javax.swing.JMenuItem item, boolean running, boolean owned, String why)
    {
        if (item == null) return false;

        Object[] kept = (Object[]) item.getClientProperty(GREYED_FOR_THE_RUN);

        if (running)
        {
            if (kept == null)
            {
                item.putClientProperty(GREYED_FOR_THE_RUN, new Object[] {item.isEnabled(), item.getToolTipText()});
            }

            item.setEnabled(false);

            item.setToolTipText(AutonomyEditorPanel.wrapped(why));

            return false;
        }

        if (kept == null) return false;

        item.putClientProperty(GREYED_FOR_THE_RUN, null);

        if (owned) item.setEnabled((Boolean) kept[0]);

        item.setToolTipText((String) kept[1]);

        return true;
    }

    /** The two section labels on the Layout menu. */
    private javax.swing.JMenuItem localHeading;
    private javax.swing.JMenuItem centralStationHeading;

    /**
     * Preferences > Debug, in debug and simulation only.  Echo Sent Commands is `DEBUG_SIMULATE_PACKETS`: a simulation
     * then answers every command as a Central Station would, so the window can follow a throttle reversal (MT-488).
     * An unattended test sets the flag in code, so there the stored choice is neither read into it nor written from it.
     */
    void mountDebugMenu()
    {
        if (ui.getModel() == null || !ui.getModel().isDebug() || !ui.getModel().isSimulation()) return;

        if (ui.interfaceMenu() == null) return;

        // On in every simulation: otherwise it answers nothing it sends (power, switch, route) and autonomy cannot run.
        // Turned on at each start and said where the stored choice was off; the menu item still turns it off.
        if (!TrainControlUI.isUnattended())
        {
            boolean wasOn = TrainControlUI.getPrefs().getBoolean(TrainControlUI.ECHO_COMMANDS_PREF, false);

            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

            TrainControlUI.getPrefs().putBoolean(TrainControlUI.ECHO_COMMANDS_PREF, true);

            if (!wasOn)
            {
                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(ui, I18n.t("ui.main.infoEchoTurnedOn")));
            }
        }

        javax.swing.JMenu debug = new javax.swing.JMenu(I18n.t("ui.main.toolbar.debug"));

        final javax.swing.JCheckBoxMenuItem echo =
            new javax.swing.JCheckBoxMenuItem(I18n.t("ui.main.toolbar.echoCommands"));

        echo.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("ui.main.toolbar.tooltip.echoCommands")));

        echo.setSelected(org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS);

        echo.addActionListener(e ->
        {
            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = echo.isSelected();

            if (!TrainControlUI.isUnattended())
            {
                TrainControlUI.getPrefs().putBoolean(TrainControlUI.ECHO_COMMANDS_PREF, echo.isSelected());
            }
        });

        debug.add(echo);

        ui.interfaceMenu().add(debug);
    }

    /** A disabled bold item as a label (as LayoutRightclickAutonomyMenu): a plain grayed item looks unavailable. */
    private javax.swing.JMenuItem menuHeading(String text)
    {
        javax.swing.JMenuItem heading = new javax.swing.JMenuItem(text);

        heading.setEnabled(false);
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD));

        return heading;
    }

    /**
     * Layouts > Manage Pages...: opens the layout editor as Edit does, or brings the open one forward, with its Manage
     * Pages menu open (FR-146).  It stands in for the Manage Pages menu, hidden on Adam's word: to bring that back,
     * drop its `setVisible(false)` here and this item.
     */
    void mountManagePages()
    {
        if (ui.layoutMenu() == null || ui.modifyLocalLayoutMenu() == null || ui.managePagesItem != null) return;

        ui.managePagesItem = new javax.swing.JMenuItem(I18n.t("ui.main.toolbar.managePages"));

        ui.managePagesItem.setToolTipText(I18n.t("ui.main.toolbar.tooltip.modifyLayout"));

        ui.managePagesItem.addActionListener(e -> openManagePages());

        int at = indexOnLayoutMenu(ui.modifyLocalLayoutMenu());

        if (at >= 0) ui.layoutMenu().add(ui.managePagesItem, at);
        else ui.layoutMenu().add(ui.managePagesItem);

        ui.modifyLocalLayoutMenu().setVisible(false);
    }

    /** Edit's way in, then the editor's page menu, posted twice to follow the editor's posted build and show. */
    private void openManagePages()
    {
        ui.openLayoutEditor(null, null, null);

        javax.swing.SwingUtilities.invokeLater(() -> javax.swing.SwingUtilities.invokeLater(() ->
        {
            LayoutEditor shown = ui.anEditorWindow();

            if (shown != null) shown.openPageMenu();
        }));
    }

    /** Items are looked up, not counted, as everything above the headings moves. */
    void mountLayoutHeadings()
    {
        if (ui.layoutMenu() == null || localHeading != null) return;

        localHeading = menuHeading(I18n.t("ui.main.toolbar.headingLocalLayout"));
        centralStationHeading = menuHeading(I18n.t("ui.main.toolbar.headingCentralStationLayout"));

        int local = indexOnLayoutMenu(ui.chooseLocalDataFolderMenuItem());

        if (local >= 0) ui.layoutMenu().add(localHeading, local);

        int station = indexOnLayoutMenu(ui.switchCSLayoutMenuItem());

        if (station >= 0) ui.layoutMenu().add(centralStationHeading, station);

        // Below both sections: both take whatever is loaded and put it in a window or a file, so they sit under no
        // heading, moved together and in order so the export stays beside the pop-out.
        if (ui.popUpAllMenuItem() != null)
        {
            ui.layoutMenu().remove(ui.popUpAllMenuItem());

            if (ui.exportDiagramItem != null) ui.layoutMenu().remove(ui.exportDiagramItem);

            ui.layoutMenu().addSeparator();
            ui.layoutMenu().add(ui.popUpAllMenuItem());

            if (ui.exportDiagramItem != null) ui.layoutMenu().add(ui.exportDiagramItem);
        }

        tidyLayoutMenuSeparators();
    }

    /** Walks the children, not the generated `jSeparator8` and `jSeparator24`, which the designer renumbers. */
    private void tidyLayoutMenuSeparators()
    {
        if (ui.layoutMenu() == null) return;

        boolean previousWasSeparator = true;

        for (int i = 0; i < ui.layoutMenu().getMenuComponentCount(); i++)
        {
            boolean separator = ui.layoutMenu().getMenuComponent(i) instanceof javax.swing.JSeparator;

            if (separator && previousWasSeparator)
            {
                ui.layoutMenu().remove(i);

                i--;

                continue;
            }

            previousWasSeparator = separator;
        }

        // And a trailing one, which the loop above cannot see because nothing follows it.
        int last = ui.layoutMenu().getMenuComponentCount() - 1;

        if (last >= 0 && ui.layoutMenu().getMenuComponent(last) instanceof javax.swing.JSeparator)
        {
            ui.layoutMenu().remove(last);
        }
    }

    private int indexOnLayoutMenu(java.awt.Component item)
    {
        if (ui.layoutMenu() == null || item == null) return -1;

        for (int i = 0; i < ui.layoutMenu().getMenuComponentCount(); i++)
        {
            if (ui.layoutMenu().getMenuComponent(i) == item) return i;
        }

        return -1;
    }

    /**
     * Empties the track diagram panel when there is no longer a diagram (OB-128), as a grayed tab holding the last
     * railway looks live.  The page cache stays: its grids belong to pages that may come back with their captions.
     */
    void clearLayoutPanel()
    {
        if (ui.innerLayoutPanel() == null) return;

        ui.innerLayoutPanel().removeAll();
        ui.innerLayoutPanel().revalidate();
        ui.innerLayoutPanel().repaint();
    }

    /**
     * Names the current data source on the menu item that opens it (FR-040), asked from `guardLayoutMenu` when somebody
     * looks.  A local layout whose pages are not loaded is None, not Local: the folder a preference points at would
     * describe a railway not on screen.
     */
    private void refreshDataSourceLabel()
    {
        if (ui.showCurrentLayoutFolderMenuItem() == null) return;

        String source;

        // isLayoutLoaded(), not the raw getLayoutList().isEmpty() (see the layoutLoaded field), so this item cannot
        // disagree with guardLayoutMenu's during a refreshLayouts() rebuild.
        if (ui.getModel() == null || !ui.isLayoutLoaded())
        {
            source = I18n.t("ui.main.toolbar.dataSourceNone");
        }
        else if (ui.isLocalLayout())
        {
            source = I18n.t("ui.main.toolbar.dataSourceLocal");
        }
        else
        {
            source = I18n.t("ui.main.toolbar.dataSourceCentralStation");
        }

        ui.showCurrentLayoutFolderMenuItem().setText(I18n.f("ui.main.toolbar.dataSource", source));
    }

    /** "Edit Layout Page..." on the Layouts menu, one entry per page; rebuilt on every mount as the pages change. */
    void mountEditPageMenu()
    {
        if (ui.layoutMenu() == null) return;

        if (ui.editPageMenu != null) ui.layoutMenu().remove(ui.editPageMenu);

        ui.editPageMenu = new javax.swing.JMenu(I18n.t("layout.ui.menuEditLayoutPage"));

        ui.editPageMenu.setToolTipText(
            AutonomyEditorPanel.wrapped(I18n.t("layout.ui.tooltipEditLayoutPage")));

        List<String> pages = ui.getModel() == null ? new ArrayList<>() : ui.getModel().getLayoutList();

        for (final String page : pages)
        {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(page);

            item.addActionListener(event -> ui.openLayoutEditor(page, null, null));

            ui.editPageMenu.add(item);
        }

        ui.editPageMenu.setEnabled(!pages.isEmpty() && ui.isLocalLayout());

        // Directly under Manage Pages, its other half (OB-021); add() would put it at the bottom.
        int under = -1;

        for (int i = 0; i < ui.layoutMenu().getMenuComponentCount(); i++)
        {
            if (ui.layoutMenu().getMenuComponent(i) == ui.modifyLocalLayoutMenu())
            {
                under = i;
                break;
            }
        }

        if (under >= 0) ui.layoutMenu().add(ui.editPageMenu, under + 1);
        else ui.layoutMenu().add(ui.editPageMenu);
    }

    /** Drops separators that separate nothing (OB-021), leading and trailing ones too; swept, not removed by index. */
    private void tidySeparators(javax.swing.JMenu menu)
    {
        if (menu == null) return;

        boolean previousWasSeparator = true;

        for (int i = 0; i < menu.getMenuComponentCount(); )
        {
            boolean separator = menu.getMenuComponent(i) instanceof javax.swing.JSeparator;

            if (separator && previousWasSeparator)
            {
                menu.remove(i);
                continue;
            }

            previousWasSeparator = separator;

            i++;
        }

        while (menu.getMenuComponentCount() > 0
            && menu.getMenuComponent(menu.getMenuComponentCount() - 1)
                instanceof javax.swing.JSeparator)
        {
            menu.remove(menu.getMenuComponentCount() - 1);
        }
    }

    /**
     * The three settings that decide what an autonomy setup DRAWS on the ordinary track diagram.  None needs a setup
     * loaded: `showStaticAutonomyLayer` returns when there is no session, graph or configuration.
     */
    void buildDiagramDrawingMenu()
    {
        final javax.swing.JMenu autonomyToolbarMenu = ui.autonomyToolbarMenu();

        final java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        if (autonomyToolbarMenu == null) return;

        autonomyToolbarMenu.addSeparator();

        // Built here (generated blocks drop hand-written items).  On by default: a vanishing caption surprises more.
        final javax.swing.JCheckBoxMenuItem show = new javax.swing.JCheckBoxMenuItem(
            I18n.t("autolayout.ui.menuShowInactiveLabels"),
            prefs.getBoolean(TrainControlUI.SHOW_INACTIVE_LABELS_PREF,
                TrainControlUI.SHOW_INACTIVE_LABELS_DEFAULT));

        show.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("autolayout.ui.tooltip.menuShowInactiveLabels")));

        show.addActionListener(event ->
        {
            prefs.putBoolean(TrainControlUI.SHOW_INACTIVE_LABELS_PREF, show.isSelected());

            // Applied to what is already drawn; the grid rebuilds only for other reasons.
            ui.refreshCaptionVisibility();
        });

        autonomyToolbarMenu.add(show);

        ui.greyStationLabelsMenuItem =
            new javax.swing.JCheckBoxMenuItem(I18n.t("ui.main.toolbar.greyStationLabels"));

        ui.greyStationLabelsMenuItem.setToolTipText(
            AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.greyStationLabels")));

        ui.greyStationLabelsMenuItem.setSelected(TrainControlUI.stationLabelsAreGrey());

        ui.greyStationLabelsMenuItem.addActionListener(event ->
        {
            prefs.putBoolean(TrainControlUI.STATION_LABELS_GREY,
                ui.greyStationLabelsMenuItem.isSelected());

            // The grid, not a repaint: a caption's color is chosen when it is built.
            ui.repaintLayout();
        });

        autonomyToolbarMenu.add(ui.greyStationLabelsMenuItem);

        ui.restrictionArrowsMenuItem =
            new javax.swing.JCheckBoxMenuItem(I18n.t("ui.main.toolbar.restrictionArrows"));

        ui.restrictionArrowsMenuItem.setToolTipText(
            AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.restrictionArrows")));

        ui.restrictionArrowsMenuItem.setSelected(
            prefs.getBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true));

        ui.restrictionArrowsMenuItem.addActionListener(event ->
        {
            prefs.putBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS,
                ui.restrictionArrowsMenuItem.isSelected());

            // The annotation layer, not the grid; handed the overlay's own state, which is safe with no setup loaded.
            ui.showStaticAutonomyLayer(
                ui.autonomyOverlayToggle != null && ui.autonomyOverlayToggle.isOverlayShown());
        });

        autonomyToolbarMenu.add(ui.restrictionArrowsMenuItem);

        // And the green (FR-110), chosen apart from the red: either, or both.
        this.allowedDirectionsMenuItem =
            new javax.swing.JCheckBoxMenuItem(I18n.t("ui.main.toolbar.allowedDirections"));

        this.allowedDirectionsMenuItem.setToolTipText(
            AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.allowedDirections")));

        this.allowedDirectionsMenuItem.setSelected(TrainControlUI.diagramShowsAllowedDirections());

        this.allowedDirectionsMenuItem.addActionListener(event ->
        {
            prefs.putBoolean(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS,
                this.allowedDirectionsMenuItem.isSelected());

            ui.showStaticAutonomyLayer(
                ui.autonomyOverlayToggle != null && ui.autonomyOverlayToggle.isOverlayShown());
        });

        autonomyToolbarMenu.add(this.allowedDirectionsMenuItem);
    }

    /**
     * "Save diagram as a picture", on the Layout menu; built by hand so the feature is one method and one class.
     */
    void buildDiagramExportMenu()
    {
        if (ui.layoutMenu() == null) return;

        // One item, for the page on screen: a second one asking which page was rarely worth a menu line.
        javax.swing.JMenuItem active =
            new javax.swing.JMenuItem(I18n.t("layout.ui.menuExportActiveDiagram"));

        active.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("layout.ui.tooltipExportActiveDiagram")));

        active.addActionListener(event -> exportDiagram(activeLayoutPage()));

        // Beside the pop-out, both making a picture in a window of their own; looked up rather than counted.
        int beside = indexOnLayoutMenu(ui.popUpAllMenuItem());

        if (beside >= 0) ui.layoutMenu().add(active, beside + 1);
        else ui.layoutMenu().add(active);

        // Kept so guardLayoutMenu can gray it by name (OB-128).
        ui.exportDiagramItem = active;
    }

    /**
     * The diagram page currently on screen, or null when there is none.
     *
     * The selector's own answer rather than a remembered one, so it cannot disagree with what the
     * user is looking at.
     */
    private String activeLayoutPage()
    {
        Object showing = ui.layoutList() == null ? null : ui.layoutList().getSelectedItem();

        return showing == null ? null : showing.toString();
    }

    /** Pixels per square for a size picked in the export dialog, the pop-out's own; null for no pick. */
    private Integer diagramTileSizeFor(Object picked)
    {
        return picked == null ? null : ui.layoutSizes.get(picked.toString());
    }

    /**
     * Writes a diagram page to a picture file: asks which page, at what size, and where.  Drawn OFF the event thread
     * behind a spinner: it WAITS for tile images applied on the event thread, which holding would deadlock.
     *
     * @param page the page to draw, or null to ask which one
     */
    private void exportDiagram(String page)
    {
        java.util.List<String> pages = ui.getModel().getLayoutList();

        if (pages == null || pages.isEmpty())
        {
            JOptionPane.showMessageDialog(ui, I18n.t("layout.ui.errorNoDiagramToExport"));

            return;
        }

        // A page that is no longer there - a rename or a reload between opening the menu and choosing
        // from it - falls back to asking rather than exporting the wrong thing or nothing
        if (page != null && !pages.contains(page)) page = null;

        if (page == null)
        {
            page = (String) JOptionPane.showInputDialog(ui,
                I18n.t("layout.ui.promptWhichDiagram"), I18n.t("layout.ui.menuExportDiagram"),
                JOptionPane.PLAIN_MESSAGE, null, pages.toArray(), pages.get(0));
        }

        if (page == null) return;

        // The pop-out window's sizes, by its names, not a free pixel size, which is not a question about their railway.
        Object[] sizes = ui.layoutSizes.keySet().toArray();

        Object size = JOptionPane.showInputDialog(ui, I18n.t("layout.ui.promptDiagramSizeChoice"),
            I18n.t("layout.ui.menuExportActiveDiagram"), JOptionPane.PLAIN_MESSAGE, null,
            sizes, sizes[sizes.length - 1]);

        Integer tileSize = diagramTileSizeFor(size);

        if (tileSize == null) return;

        javax.swing.JFileChooser chooser = new QuickFileChooser();

        chooser.setDialogTitle(I18n.t("layout.ui.menuExportDiagram"));

        // A sensible name that is also a legal one, since a page may be called anything at all
        chooser.setSelectedFile(new java.io.File(page.replaceAll("[^a-zA-Z0-9 _-]", "_") + ".png"));

        if (chooser.showSaveDialog(ui) != javax.swing.JFileChooser.APPROVE_OPTION) return;

        java.io.File destination = chooser.getSelectedFile();

        if (!destination.getName().toLowerCase().endsWith(".png"))
        {
            destination = new java.io.File(destination.getParentFile(), destination.getName() + ".png");
        }

        final java.io.File writeTo = destination;
        final int pixels = tileSize;
        final String chosen = page;
        final Exception[] failed = new Exception[1];

        // Off the event thread, behind the spinner: the render WAITS for tile images decoded on the event thread.
        BusyDialog.run(ui, I18n.t("layout.ui.busyExportingDiagram"),
            () ->
            {
                try
                {
                    DiagramExport.writePng(ui.getModel().getLayout(chosen), pixels, ui, writeTo);
                }
                catch (Exception e)
                {
                    failed[0] = e;
                }
            },
            () ->
            {
                if (failed[0] != null)
                {
                    ui.getModel().log(failed[0]);

                    JOptionPane.showMessageDialog(ui,
                        I18n.f("layout.ui.errorDiagramExportFailed",
                            String.valueOf(failed[0].getMessage())));

                    return;
                }

                ui.log(I18n.f("layout.ui.infoDiagramExported", writeTo.getAbsolutePath()));

                ui.showFileExplorer(writeTo.getParentFile());
            });
    }

    /** The recorded layout folders, most recent first, without `open` and without repeats (by `File.equals`). */
    static List<String> recentLayoutsWithout(String open)
    {
        List<String> kept = new ArrayList<>();

        Set<File> seen = new HashSet<>();

        if (open != null && !open.isEmpty()) seen.add(new File(open).getAbsoluteFile());

        for (String path : TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, "").split("\n"))
        {
            if (!path.isEmpty() && seen.add(new File(path).getAbsoluteFile())) kept.add(path);
        }

        return kept;
    }

    /** Takes a folder off Recent Layouts (FR-145). */
    private static void forgetRecentLayout(String path)
    {
        List<String> kept = new ArrayList<>();

        for (String recorded : TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, "").split("\n"))
        {
            if (!recorded.isEmpty() && !new File(recorded).getAbsoluteFile().equals(new File(path).getAbsoluteFile()))
            {
                kept.add(recorded);
            }
        }

        TrainControlUI.getPrefs().put(TrainControlUI.RECENT_LAYOUTS_PREF, String.join("\n", kept));
    }

    /** Layouts &gt; Recent Layouts (FR-145), rebuilt each time the Layouts menu opens. */
    private javax.swing.JMenu recentLayoutsMenu;

    void mountRecentLayouts()
    {
        if (ui.layoutMenu() == null || recentLayoutsMenu != null) return;

        recentLayoutsMenu = new javax.swing.JMenu(I18n.t("ui.main.toolbar.recentLayouts"));

        recentLayoutsMenu.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.recentLayouts")));

        int open = indexOnLayoutMenu(ui.chooseLocalDataFolderMenuItem());

        if (open >= 0) ui.layoutMenu().add(recentLayoutsMenu, open + 1);
        else ui.layoutMenu().add(recentLayoutsMenu);

        refreshRecentLayouts();
    }

    /** The open layout is not listed; a name two folders share is shown as the whole path. */
    private void refreshRecentLayouts()
    {
        if (recentLayoutsMenu == null) return;

        recentLayoutsMenu.removeAll();

        List<String> shown = recentLayoutsWithout(ui.localLayoutPath());

        shown = shown.subList(0, Math.min(shown.size(), TrainControlUI.RECENT_LAYOUTS_SHOWN));

        Map<String, Integer> named = new HashMap<>();

        for (String path : shown) named.merge(new File(path).getName(), 1, Integer::sum);

        for (final String path : shown)
        {
            String name = new File(path).getName();

            javax.swing.JMenuItem item = new javax.swing.JMenuItem(named.get(name) > 1 || name.isEmpty() ? path : name);

            item.setToolTipText(AutonomyEditorPanel.wrappedIfLong(path));

            item.addActionListener(e -> openRecentLayout(path));

            recentLayoutsMenu.add(item);
        }

        recentLayoutsMenu.setEnabled(!shown.isEmpty());
    }

    /** Open Layout's rules, without its chooser; a folder that has gone is said once and taken off the list. */
    private void openRecentLayout(String path)
    {
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        if (ui.refuseWhileEditorOpen()) return;

        File folder = new File(path);

        if (!folder.isDirectory())
        {
            forgetRecentLayout(path);

            refreshRecentLayouts();

            JOptionPane.showMessageDialog(ui, I18n.f("ui.main.toolbar.errorRecentLayoutGone", path));

            return;
        }

        ui.openLayoutFolder(folder);
    }
}
