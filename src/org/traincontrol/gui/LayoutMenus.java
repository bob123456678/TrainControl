package org.traincontrol.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import org.traincontrol.gui.autonomy.DiagramExport;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
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

        takeBackTheEditorsReason();

        for (int i = 0; i < ui.layoutMenu().getMenuComponentCount(); i++)
        {
            java.awt.Component child = ui.layoutMenu().getMenuComponent(i);

            if (child == goToEditorItem) continue;

            // A heading is a disabled label by design; do not switch it back on.
            if (child == localHeading || child == centralStationHeading) continue;

            // Taken away, never handed back: `repaintPathLabel` owns the Central Station items and grays them when none
            // is reachable (OB-098), which IS their guard.  initializeLocalLayoutMenuItem likewise: only its completion
            // callback re-enables it.
            if (child == ui.switchCSLayoutMenuItem() || child == ui.downloadCSLayoutMenuItem()
                || child == ui.initializeLocalLayoutMenuItem())
            {
                if (busy) child.setEnabled(false);

                continue;
            }

            child.setEnabled(!busy);
        }

        ui.applyLayoutEditingAvailability();

        refreshHeadings();

        // The items that need loaded pages or a Central Station 3 (OB-128); left alone while an editor is open.
        if (!busy)
        {
            refreshRecentLayouts();

            // The picture export needs loaded pages, not a local folder (a Central Station diagram works), and would be
            // handed the null page that activeLayoutPage returns when the selector is empty.
            if (ui.exportDiagramItem != null) ui.exportDiagramItem.setEnabled(ui.isLayoutLoaded());

            // Its own question: excluded from the loop above, and its only other writer never re-enables it.
            if (ui.openCS3AppMenuItem() != null)
            {
                ui.openCS3AppMenuItem().setEnabled(ui.getModel() != null && ui.getModel().isCS3());

                sayWhy(ui.openCS3AppMenuItem(), null, "ui.main.toolbar.tooltip.needsCS3");
            }
        }

        // While autonomy runs or a route holds the setup (`aRunHoldsTheSetup`), what changes the diagram or the folder
        // is grayed with the reason (each also refuses on the click, MT-141).
        boolean running = ui.isAutonomyBusy() || (ui.getModel() != null && ui.getModel().isAutonomyRunning())
            || ui.aRunHoldsTheSetup();

        String why = running ? I18n.t(RunGates.NO_EDITS_WHILE_RUNNING) : null;

        greyForTheRun(ui.chooseLocalDataFolderMenuItem(), running, false, why);
        // The layouts Recent Layouts lists wait; the folder under them stays live, as opening it changes nothing.
        for (javax.swing.JMenuItem each : recentItems) greyForTheRun(each, running, true, why);
        greyForTheRun(ui.modifyLocalLayoutMenu(), running, false, why);
        greyForTheRun(ui.managePagesItem, running, false, why);
        greyForTheRun(ui.initializeLocalLayoutMenuItem(), running, true, why);

        // The two Central Station items' owner works them out again once they are given back
        if (greyForTheRun(ui.switchCSLayoutMenuItem(), running, true, why)
            | greyForTheRun(ui.downloadCSLayoutMenuItem(), running, true, why))
        {
            ui.repaintPathLabel();
        }

        if (goToEditorItem == null)
        {
            goToEditorItem = new javax.swing.JMenuItem(I18n.t("autosetup.ui.menuGoToOpenEditor"));

            goToEditorItem.addActionListener(e -> ui.showOpenEditor());

            ui.layoutMenu().add(goToEditorItem, 0);

            goToEditorDivider = new javax.swing.JPopupMenu.Separator();

            ui.layoutMenu().add(goToEditorDivider, 1);
        }

        goToEditorItem.setVisible(busy);
        goToEditorDivider.setVisible(busy);

        if (busy) sayCloseTheEditor();
    }

    /** The "an editor has this diagram" item on the Layout menu, and the divider under it. */
    private javax.swing.JMenuItem goToEditorItem;
    private javax.swing.JPopupMenu.Separator goToEditorDivider;

    /** What a Layout menu item said before an open editor's reason replaced it, in a one-element array. */
    private static final String SAID_BEFORE_THE_EDITOR = "saidBeforeTheEditor";

    /**
     * Puts the editor's reason on every item an open editor leaves grayed, over a run's reason too: closing the editor
     * is the first step either way.  Applied last and taken back first, so `greyForTheRun` never keeps it.
     */
    private void sayCloseTheEditor()
    {
        String why = AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipCloseTheEditor"));

        for (java.awt.Component child : ui.layoutMenu().getMenuComponents())
        {
            if (!(child instanceof javax.swing.JMenuItem) || child.isEnabled() || child == goToEditorItem
                || child == localHeading || child == centralStationHeading) continue;

            javax.swing.JMenuItem item = (javax.swing.JMenuItem) child;

            item.putClientProperty(SAID_BEFORE_THE_EDITOR, new String[] {item.getToolTipText()});

            item.setToolTipText(why);
        }
    }

    /** Gives each item back what it said before the editor's reason, unless something has set its tooltip since. */
    private void takeBackTheEditorsReason()
    {
        String why = AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipCloseTheEditor"));

        for (java.awt.Component child : ui.layoutMenu().getMenuComponents())
        {
            if (!(child instanceof javax.swing.JMenuItem)) continue;

            javax.swing.JMenuItem item = (javax.swing.JMenuItem) child;

            Object kept = item.getClientProperty(SAID_BEFORE_THE_EDITOR);

            if (!(kept instanceof String[])) continue;

            item.putClientProperty(SAID_BEFORE_THE_EDITOR, null);

            if (java.util.Objects.equals(item.getToolTipText(), why)) item.setToolTipText(((String[]) kept)[0]);
        }
    }

    /** What a Layout menu item had before a run grayed it: whether it was live, and its tooltip */
    private static final String GREYED_FOR_THE_RUN = "greyedForTheRun";

    /**
     * Grays the item with `why` as its tooltip while a run goes and gives the tooltip back after it (the enabled state
     * too, when `owned` by something else); returns true when it was given back just now.
     */
    static boolean greyForTheRun(javax.swing.JMenuItem item, boolean running, boolean owned, String why)
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
     * Preferences > Debug, in debug and simulation only.  Echo Sent Commands is `DEBUG_SIMULATE_PACKETS`, so a
     * simulation answers every command as a Central Station would (MT-488).  An unattended test sets the flag in code,
     * so there the stored choice is neither read into it nor written from it.
     */
    void mountDebugMenu()
    {
        if (ui.getModel() == null || !ui.getModel().isDebug() || !ui.getModel().isSimulation()) return;

        if (ui.interfaceMenu() == null) return;

        // On in every simulation: otherwise it answers nothing it sends and autonomy cannot run; the menu item turns it
        // off.
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
    javax.swing.JMenuItem menuHeading(String text)
    {
        javax.swing.JMenuItem heading = new javax.swing.JMenuItem(text);

        heading.setEnabled(false);
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD));

        return heading;
    }

    /**
     * Layouts > Edit/Add Pages...: opens the layout editor as Edit does, or brings the open one forward (FR-146); the
     * page actions are the icons down its left side.  It stands in for the hidden Manage Pages menu.
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

    /** Edit's way in. */
    private void openManagePages()
    {
        ui.openLayoutEditor(null, null, null);
    }

    /**
     * The tooltip a menu item shows for its state (D46): its description when lit (a null key for none), and why when
     * grayed.  Asked after whatever decides the item's state.
     */
    static void sayWhy(javax.swing.JMenuItem item, String descriptionKey, String whyKey)
    {
        String key = item.isEnabled() ? descriptionKey : whyKey;

        item.setToolTipText(key == null ? null : AutonomyEditorPanel.wrappedIfLong(I18n.t(key)));
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

        // Pop Up All is the Layout tab's All button now (D52), and the folder is under Recent Layouts (D43).
        if (ui.popUpAllMenuItem() != null) ui.layoutMenu().remove(ui.popUpAllMenuItem());

        if (ui.showCurrentLayoutFolderMenuItem() != null)
        {
            ui.layoutMenu().remove(ui.showCurrentLayoutFolderMenuItem());

            ui.showCurrentLayoutFolderMenuItem().setText(I18n.t("ui.main.toolbar.showCurrentLayoutFolder"));
            ui.showCurrentLayoutFolderMenuItem().setToolTipText(null);
        }

        // Below both sections, under no heading: it takes whatever is loaded and puts it in a file.
        if (ui.exportDiagramItem != null)
        {
            ui.layoutMenu().remove(ui.exportDiagramItem);
            ui.layoutMenu().addSeparator();
            ui.layoutMenu().add(ui.exportDiagramItem);
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
     * railway looks live.  The page cache stays: its pages may come back with their captions.
     */
    void clearLayoutPanel()
    {
        if (ui.innerLayoutPanel() == null) return;

        ui.innerLayoutPanel().removeAll();
        ui.innerLayoutPanel().revalidate();
        ui.innerLayoutPanel().repaint();
    }

    /** Marks the heading of the layout in use "(in use)" (D43); neither, with no layout loaded. */
    private void refreshHeadings()
    {
        if (localHeading == null) return;

        boolean loaded = ui.getModel() != null && ui.isLayoutLoaded();

        localHeading.setText(heading("ui.main.toolbar.headingLocalLayout", loaded && ui.isLocalLayout()));
        centralStationHeading.setText(heading("ui.main.toolbar.headingCentralStationLayout",
            loaded && !ui.isLocalLayout()));
    }

    private static String heading(String key, boolean inUse)
    {
        return inUse ? I18n.f("ui.main.toolbar.headingInUse", I18n.t(key)) : I18n.t(key);
    }

    /**
     * The three settings that decide what an autonomy setup DRAWS on the ordinary track diagram; none needs a setup.
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

    /** Preferences > Hide Hover Tooltips (FR-168); read when a menu opens, so it needs no refresh. */
    void mountRightClickTipsMenu()
    {
        RightClickTips.install();

        if (ui.interfaceMenu() == null) return;

        final javax.swing.JCheckBoxMenuItem hide = new javax.swing.JCheckBoxMenuItem(
            I18n.t("ui.main.toolbar.hideRightClickTips"),
            RightClickTips.hidden());

        hide.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.hideRightClickTips")));

        hide.addActionListener(event ->
            TrainControlUI.getPrefs().putBoolean(RightClickTips.PREF, hide.isSelected()));

        // Under Remember Window Locations, above the divider (MT-910).
        int under = ui.interfaceMenu().getPopupMenu().getComponentIndex(ui.rememberLocationMenuItem());

        if (under >= 0) ui.interfaceMenu().add(hide, under + 1);
        else ui.interfaceMenu().add(hide);
    }

    /** "Save diagram as a picture", on the Layout menu; built by hand so the feature is one method and one class. */
    void buildDiagramExportMenu()
    {
        if (ui.layoutMenu() == null) return;

        // One item, for the page on screen: a second one asking which page was rarely worth a menu line.
        javax.swing.JMenuItem active =
            new javax.swing.JMenuItem(I18n.t("layout.ui.menuExportActiveDiagram"));

        active.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("layout.ui.tooltipExportActiveDiagram")));

        active.addActionListener(event -> exportDiagram(activeLayoutPage()));

        // At the foot; mountLayoutHeadings puts it below the Central Station section.
        ui.layoutMenu().add(active);

        // Kept so guardLayoutMenu can gray it by name (OB-128).
        ui.exportDiagramItem = active;
    }

    /**
     * The diagram page currently on screen, or null; the selector's own answer, so it cannot disagree with the user's
     * view.
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
     * Writes a diagram page to a picture file: asks which page (unless `page` names one), at what size, and where.
     * Drawn OFF the event thread behind a spinner: it WAITS for tile images applied on the event thread, which holding
     * would deadlock.
     */
    private void exportDiagram(String page)
    {
        java.util.List<String> pages = ui.getModel().getLayoutList();

        if (pages == null || pages.isEmpty())
        {
            JOptionPane.showMessageDialog(ui, I18n.t("layout.ui.errorNoDiagramToExport"));

            return;
        }

        // A page renamed or reloaded between opening the menu and choosing falls back to asking.
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

    /** The layouts it lists now, each opening one. */
    private final List<javax.swing.JMenuItem> recentItems = new ArrayList<>();

    void mountRecentLayouts()
    {
        if (ui.layoutMenu() == null || recentLayoutsMenu != null) return;

        recentLayoutsMenu = new javax.swing.JMenu(I18n.t("ui.main.toolbar.recentLayouts"));

        int open = indexOnLayoutMenu(ui.chooseLocalDataFolderMenuItem());

        if (open >= 0) ui.layoutMenu().add(recentLayoutsMenu, open + 1);
        else ui.layoutMenu().add(recentLayoutsMenu);

        refreshRecentLayouts();
    }

    /**
     * The open layout is not listed; each item reads `recentLabel`.  With none, one gray line says so and the submenu
     * stays live (D46); Show Current Layout Folder ends it for a layout on this computer, under a divider (D43).
     */
    private void refreshRecentLayouts()
    {
        if (recentLayoutsMenu == null) return;

        recentLayoutsMenu.removeAll();

        recentItems.clear();

        List<String> shown = recentLayoutsWithout(ui.localLayoutPath());

        shown = shown.subList(0, Math.min(shown.size(), TrainControlUI.RECENT_LAYOUTS_SHOWN));

        int number = 0;

        for (final String path : shown)
        {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(++number + ". " + recentLabel(path, shown));

            item.setToolTipText(AutonomyEditorPanel.wrappedIfLong(path));

            item.addActionListener(e -> openRecentLayout(path));

            recentLayoutsMenu.add(item);

            recentItems.add(item);
        }

        if (shown.isEmpty())
        {
            javax.swing.JMenuItem none = new javax.swing.JMenuItem(I18n.t("ui.main.toolbar.noOtherLayouts"));

            none.setEnabled(false);

            recentLayoutsMenu.add(none);
        }

        javax.swing.JMenuItem folder = ui.showCurrentLayoutFolderMenuItem();

        if (folder != null && ui.isLocalLayout() && ui.isLayoutLoaded())
        {
            recentLayoutsMenu.addSeparator();

            recentLayoutsMenu.add(folder);
        }
    }

    /** The most characters an item's label takes, unless a cut would read like another item's (OB-390). */
    private static final int RECENT_LABEL_MOST = 50;

    /**
     * What a Recent Layouts item reads (OB-390): the folder's name, or where another listed folder shares it, as many
     * of its last folders as tell the two apart after an ellipsis, the whole path only where nothing shorter does.
     */
    static String recentLabel(String path, List<String> shown)
    {
        String label = uncutLabel(path, shown);

        List<String> others = new ArrayList<>();

        for (String other : shown)
        {
            if (!other.equals(path)) others.add(uncutLabel(other, shown));
        }

        String cut = cut(label, others);

        for (String other : others)
        {
            List<String> theirOthers = new ArrayList<>(others);

            theirOthers.remove(other);
            theirOthers.add(label);

            if (cut(other, theirOthers).equals(cut)) return label;
        }

        return cut;
    }

    /** A label cut in the middle past `RECENT_LABEL_MOST`, its head past where it first differs from any of these. */
    private static String cut(String label, List<String> others)
    {
        if (label.length() <= RECENT_LABEL_MOST) return label;

        int head = RECENT_LABEL_MOST / 3;

        for (String other : others)
        {
            int differs = 0;

            while (differs < Math.min(label.length(), other.length())
                && label.charAt(differs) == other.charAt(differs))
            {
                differs++;
            }

            head = Math.max(head, Math.min(differs + 1, RECENT_LABEL_MOST - 1 - RECENT_LABEL_MOST / 5));
        }

        return label.substring(0, head) + "\u2026"
            + label.substring(label.length() - (RECENT_LABEL_MOST - head - 1));
    }

    /** The folder's name, or as many of its last folders as tell it from another listed folder of that name. */
    private static String uncutLabel(String path, List<String> shown)
    {
        List<String> mine = folders(path);

        int needed = 1;

        for (String other : shown)
        {
            List<String> theirs = folders(other);

            if (other.equals(path) || !theirs.get(theirs.size() - 1).equals(mine.get(mine.size() - 1))) continue;

            // One more folder than the two have in common at the end
            int same = 0;

            while (same < Math.min(mine.size(), theirs.size())
                && mine.get(mine.size() - 1 - same).equals(theirs.get(theirs.size() - 1 - same)))
            {
                same++;
            }

            needed = Math.max(needed, same + 1);
        }

        return needed >= mine.size() ? path
            : (needed > 1 ? "\u2026" + File.separator : "")
                + String.join(File.separator, mine.subList(mine.size() - needed, mine.size()));
    }

    /** A path's folders from its root down, the root as written (C:\ or /), so none is empty. */
    private static List<String> folders(String path)
    {
        List<String> out = new ArrayList<>();

        for (File at = new File(path).getAbsoluteFile(); at != null; at = at.getParentFile())
        {
            out.add(0, at.getName().isEmpty() ? at.getPath() : at.getName());
        }

        return out;
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
