package org.traincontrol.gui;

import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.autonomy.AutonomyReport;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * Setting autonomy up, from the menu bar beside Layouts: what a user does once (choose a configuration, rename one,
 * leave a page out), kept out of the tab strip.  Next to Layouts because a setup belongs to a layout; with no layout
 * the menu is disabled and says why.  Rebuilt each time it opens, so no second copy of the truth can disagree with the
 * first.
 *
 * @author Adam
 */
public class AutonomyMenu extends JMenu
{
    private final TrainControlUI ui;

    public AutonomyMenu(TrainControlUI ui)
    {
        super(I18n.t("autosetup.ui.menuAutonomy"));

        this.ui = ui;

        // Nothing in the menu bar takes focus, for the same reason nothing in the autonomy panels
        // does: the main window drives locomotives from bare key presses.
        setFocusable(false);

        addMenuListener(new MenuListener()
        {
            @Override
            public void menuSelected(MenuEvent e)
            {
                rebuild();
            }

            @Override
            public void menuDeselected(MenuEvent e)
            {
            }

            @Override
            public void menuCanceled(MenuEvent e)
            {
            }
        });

        refreshEnabled();
    }

    /**
     * Switches the menu on or off according to whether there is a layout to set autonomy up for.  Disabled rather than
     * hidden, with the reason on the tooltip.
     */
    public final void refreshEnabled()
    {
        // The question lives on the window (OB-202): `autonomyTopMenu` holds this slot until start-up builds this menu,
        // and it has to be grayed for the same reasons.  The parts are at `TrainControlUI.autonomyMenuIsUsable`: a
        // layout, and not while the connecting notice holds the bar (OB-187).  Not a local one: on a Central Station
        // layout the menu opens onto the download and the guide.
        setEnabled(ui.autonomyMenuIsUsable());

        setToolTipText(AutonomyEditorPanel.wrapped(ui.whyAutonomyIsUnavailable()));
    }

    /**
     * The pages submenu as it was last built, so that something else can send the user straight to it: the diagram says
     * when its page is left out of autonomy, which is half an answer without a way to change it.
     */
    private JMenu lastPagesMenu;

    /** Autonomy Settings as last built, or null: the one item a run leaves live with the guide. */
    private JMenuItem settingsItem;

    /**
     * Opens this menu with the pages submenu already showing.  Driven through the MenuSelectionManager, not a click,
     * which would toggle an open menu shut and cannot reach a submenu.
     */
    public void showPages()
    {
        if (!(getParent() instanceof javax.swing.JMenuBar)) return;

        final javax.swing.JMenuBar bar = (javax.swing.JMenuBar) getParent();

        // Open this menu first, and only then walk into the submenu: selecting the menu fires menuSelected, which
        // rebuilds every item, so a submenu named in a single path was removed a moment after being pointed at.
        javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
            new javax.swing.MenuElement[] { bar, this, getPopupMenu() });

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            // Unavailable (no configuration chosen): this menu is as far as it goes, and its items say why.
            if (lastPagesMenu == null || !lastPagesMenu.isEnabled()) return;

            javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
                new javax.swing.MenuElement[]
                {
                    bar, this, getPopupMenu(), lastPagesMenu, lastPagesMenu.getPopupMenu()
                });
        });
    }

    private AutonomyViewerPanel actions()
    {
        return ui.getAutonomyViewerPanel();
    }

    /**
     * Grays every item while an editor has the diagram, and offers the way back to it.  The same shape as
     * TrainControlUI.guardLayoutMenu: the two menus are guarded for the same reason.
     */
    private void guardWhileEditing()
    {
        if (!ui.isLayoutEditorOpen()) return;

        for (int i = 0; i < getMenuComponentCount(); i++)
        {
            getMenuComponent(i).setEnabled(false);
        }

        // Clickable, and it brings the editor forward (OB-033): disabled, it stated the problem and offered nothing,
        // and the window may be behind this one.  First in the list, as the only thing that can be done.  It reads
        // menuOpenFullEditor, the label already used for the same action (LayoutRightclickAutonomyMenu.addSetupMenu),
        // not the refusal sentence.
        JMenuItem busy = new JMenuItem(I18n.t("autosetup.ui.menuOpenFullEditor"));

        busy.addActionListener(e -> ui.showOpenEditor());

        insert(busy, 0);

        // Under the item it belongs to: addSeparator appends, which drew the rule after the last item.
        insertSeparator(1);
    }

    /**
     * Builds the menu, and ends it with Documentation whatever else it holds (Adam: *"Add a 'documentation' link to the
     * autonomy JMenu that points to the same URL as the current jlabel."*).  The link was on the old Load Autonomy
     * Configuration tab, which went (OB-254).  Left enabled while an editor grays the rest: reading the guide changes
     * nothing.
     */
    private void rebuild()
    {
        buildItems();

        if (getMenuComponentCount() > 0) addSeparator();

        JMenuItem documentation = new JMenuItem(I18n.t("ui.main.documentation"));

        // On a thread of its own (Adam, on MT-546: "can it get its own thread?"): opening a browser is a call into
        // Windows.
        documentation.addActionListener(e -> new Thread(() -> Util.openUrl(TrainControlUI.README_URL),
            "open the automation guide").start());

        add(documentation);

        guardWhileRunning(documentation);

        guardWhileEditing();

        documentation.setEnabled(true);
    }

    /**
     * Grays what changes the setup while autonomy runs, with the reason on each (Adam, MT-613 and MT-626: *"why not
     * grey out the whole menu while running?"*).  Each door still refuses on its own; this says so before the click.
     * Autonomy Settings and Documentation stay live (the settings are shown grayed while autonomy is busy, each saying
     * why).  The debug export is grayed too (Adam, MT-633: "export raw graph as json should also be disabled while
     * running"), grayed only: it writes nothing of the setup.
     *
     * @param documentation the guide's item
     */
    private void guardWhileRunning(JMenuItem documentation)
    {
        // A route too (`TrainControlUI.aRunHoldsTheSetup`); the sentence every item's door refuses with
        if (!ui.aRunHoldsTheSetup()) return;

        String why = AutonomyEditorPanel.wrapped(I18n.t(RunGates.NO_EDITS_WHILE_RUNNING));

        for (int i = 0; i < getMenuComponentCount(); i++)
        {
            java.awt.Component part = getMenuComponent(i);

            if (!(part instanceof JMenuItem) || part == documentation || part == settingsItem)
            {
                continue;
            }

            part.setEnabled(false);

            ((JMenuItem) part).setToolTipText(why);
        }
    }

    private void buildItems()
    {
        removeAll();

        settingsItem = null;

        // Dropped, not kept: it is only reassigned on the branch that builds the submenu, so after another branch it
        // pointed at a JMenu no longer in this popup and showPages asked Swing to open a detached component.
        lastPagesMenu = null;

        final AutonomyViewerPanel actions = actions();
        AutonomySession session = ui.getAutonomySession();

        if (actions == null || session == null)
        {
            // A dead sentence, unless a download would answer it (OB-093; Adam: "the 'autonomy needs a layout stored on
            // this computer' notice should be clickable and allow the user to download one."): grayed, it leaves the
            // reader knowing what is wrong and not what to do.  Only when a download is the answer: this branch is also
            // taken for reasons it would not touch (no viewer panel yet, a session that failed to open), and an offer
            // that cannot help teaches the reader the notice lies.  The handler's own question, both halves:
            // downloadCSLayoutMenuItemActionPerformed also requires a non-empty page list, so asking only whether the
            // layout is remote showed a live item that did nothing (the OB-057 shape).
            //
            // A choice, download or create (Adam, on MT-548: *"what is the cs3 has no layout?  give the user the choice
            // to either download or create a new one, which redirects to the corresponding option under layouts."*):
            // both of the Layouts menu's ways to a layout on this computer, under their own names, the download grayed
            // with its reason where it would do nothing, and Create New Layout grayed exactly when its Layouts item is.
            if (ui.isRemoteLayout())
            {
                javax.swing.JMenu offer = new javax.swing.JMenu(I18n.t("autosetup.ui.menuNoSetupPossible"));

                JMenuItem download = new JMenuItem(I18n.t("ui.main.toolbar.downloadCSLayout"));

                boolean canDownload = ui.hasPagesToDownload() && ui.isCentralStationConnected();

                download.setEnabled(canDownload);

                if (!canDownload)
                {
                    download.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("autosetup.ui.tooltipNoLayoutToDownload")));
                }

                download.addActionListener(event -> ui.downloadCentralStationLayout());

                JMenuItem create = new JMenuItem(I18n.t("ui.main.toolbar.createLayout"));

                create.setEnabled(ui.canCreateNewLayout());

                create.addActionListener(event -> ui.createNewLayout());

                offer.add(download);
                offer.add(create);

                add(offer);

                return;
            }

            // A setup that is there and would not read says so, with the reason on the grayed line: "needs a layout
            // stored on this computer" sent the reader looking for a folder they had when setup.json had a typing slip
            // or came from a newer TrainControl.  The window keeps why; the line only offers to say it.
            String unreadable = ui.whyTheSetupCannotBeRead();

            JMenuItem none = new JMenuItem(I18n.t(unreadable != null
                ? "autosetup.ui.menuSetupUnreadable" : "autosetup.ui.menuNoSetupPossible"));
            none.setEnabled(false);

            if (unreadable != null)
            {
                none.setToolTipText(AutonomyEditorPanel.wrapped(I18n.f("autosetup.ui.errorSetupUnreadable", unreadable)));
            }

            add(none);

            return;
        }

        List<String> names = session.getStore().getConfigurationNames();

        String running = ui.getActiveDiagramConfiguration();

        // Nothing set up yet: no submenus of empty things, but two ways to get started (FR-007): "it should be possible
        // to initially load autonomy from an import, not just forcing the creation of a new one."
        if (names.isEmpty())
        {
            add(addConfigurationItem(actions));

            add(item(I18n.t("autosetup.ui.btnImportConfiguration"), new Runnable()
            {
                @Override
                public void run()
                {
                    actions.importConfiguration();

                    ui.autonomyMenuActed();
                }
            }));
        }
        else
        {
            // The configurations get a submenu of their own, where a name like "Adam 1" would read as an action; the
            // heading carries the running one, which is what this menu is most often opened to check.
            JMenu choose = new JMenu(I18n.f("autosetup.ui.menuConfigurations",
                running == null ? I18n.t("autosetup.ui.menuNoneRunning") : running));

            // Radio, because exactly one runs
            ButtonGroup group = new ButtonGroup();

            for (final String name : names)
            {
                JRadioButtonMenuItem choice =
                    new JRadioButtonMenuItem(name, name.equals(running));

                group.add(choice);

                choice.addActionListener(e ->
                {
                    if (actions.refusedWhileRunning()) return;

                    actions.setSelectedConfiguration(name);
                    actions.load(name, true);

                    ui.autonomyMenuActed();
                });

                choose.add(choice);
            }

            // Import and Export, under a divider, with the configurations they act on (OB-051): they were buried a
            // level down, where Adam could not find them ("the option is just buried and I didn't see it.").
            choose.addSeparator();

            // Import is grayed only with the rest of the menu while autonomy runs (`guardWhileRunning`), and refuses
            // then too, saying so; into the running configuration it refuses a file that would replace it and takes an
            // old file's gaps without its trains.  It is most needed when the current setup will not load, which
            // importing one that will repairs.
            choose.add(item(I18n.t("autosetup.ui.btnImportConfiguration"), new Runnable()
            {
                @Override
                public void run()
                {
                    actions.importConfiguration();

                    ui.autonomyMenuActed();
                }
            }));

            // Export keeps its graying: it writes out the configuration that is running, so with none there is nothing
            // to write.
            JMenuItem exportItem = item(I18n.t("autosetup.ui.btnExportConfiguration"), new Runnable()
            {
                @Override
                public void run()
                {
                    actions.exportConfiguration();
                }
            });

            exportItem.setEnabled(running != null);

            if (running == null)
            {
                exportItem.setToolTipText(
                    AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipNeedsLoaded")));
            }

            choose.add(exportItem);

            add(choose);

            // Manage acts on the configuration that is RUNNING, so it means nothing until one is.
            boolean loaded = running != null;

            // Directly under the configuration it manages: choosing one and managing the set of them are one subject.
            add(manageMenu(actions, session, loaded));

            // Editing and page exclusions ask only that a configuration be chosen: they are how a setup that will not
            // load gets fixed (it refuses on blocking errors and the editor is where those are dealt with), so gating
            // them on a running setup locked the door on the room the user was being sent to.
            boolean chosen = session.getStore().getActiveConfiguration() != null;

            // The settings for the whole setup (pace, how many trains at once, what a train may do on arrival) live on
            // a tab inside a tab that nobody finds, and this menu is where every other decision about the setup is
            // made.  Loaded, not merely chosen: that tab is built when a configuration loads, so offering it earlier
            // would go nowhere.
            JMenuItem settings = settingsItem = item(I18n.t("autosetup.ui.menuGlobalSettings"), new Runnable()
            {
                @Override
                public void run()
                {
                    ui.showAutonomySettingsTab();
                }
            });

            settings.setEnabled(loaded);

            settings.setToolTipText(AutonomyEditorPanel.wrapped(loaded
                ? I18n.t("autosetup.ui.tooltipGlobalSettings")
                : I18n.t("autosetup.ui.tooltipNeedsLoaded")));

            add(settings);

            // Fenced off by itself, as the only item here that changes the railway: everything above picks a setup or
            // keeps the file that holds it; below the line the editor starts naming stations and setting which way
            // trains may run, and the pages submenu decides which pages it sees.
            addSeparator();

            JMenu edit = editMenu(session);

            // And not while trains are running (OB-045): a grayed item says before the click what a refusal says after.
            // While autonomy runs `guardWhileRunning` grays the rest of the menu with it, bar Autonomy Settings and
            // Documentation (Adam, MT-613 and MT-626; the debug export too, MT-633); this item asks the editor's own
            // refusals as well, at rest.  It asks `whyAutonomyEditorCannotOpen`, as
            // LayoutRightclickAutonomyMenu.addSetupMenu does, rather than re-deriving three of openLayoutEditor's four
            // refusals by hand, which left it live while "an editor is already open".
            String editRefusal = ui.whyAutonomyEditorCannotOpen();
            boolean pagesAvailable = edit.getItemCount() > 0;

            edit.setEnabled(editRefusal == null && pagesAvailable);
            edit.setToolTipText(AutonomyEditorPanel.wrapped(
                editRefusal != null ? I18n.t(editRefusal)
                : !pagesAvailable ? I18n.t("autosetup.ui.tooltipNeedsLoaded")
                : I18n.t("autosetup.ui.tooltipEditAutonomy")));
            add(edit);

            // Directly under the editor, because it answers the question the editor list raises: a page left out does
            // not appear in the list above.
            JMenu pages = pagesMenu(session);
            lastPagesMenu = pages;
            pages.setEnabled(chosen);
            pages.setToolTipText(AutonomyEditorPanel.wrapped(chosen ? I18n.t("autosetup.ui.promptExcludePage")
                : I18n.t("autosetup.ui.tooltipNeedsLoaded")));
            add(pages);
        }

        // Exporting the derived graph, for a debug session and only when there is a graph to export: shown with no
        // setup, or one that will not build, it promised a file in the two states where pressing it produces nothing or
        // an invalid graph.
        boolean inspectable = ui.getModel() != null && ui.getModel().isDebug()
            && !session.getStore().getConfigurationNames().isEmpty()
            && session.getStore().getActiveConfiguration() != null
            && session.getReducer() != null
            && !session.hasBlockingProblems();

        if (inspectable)
        {
            addSeparator();

            add(item(I18n.t("autosetup.ui.menuExportRawGraph"), new Runnable()
            {
                @Override
                public void run()
                {
                    actions.inspect();
                }
            }));
        }
    
        // Everything grayed while an editor has the diagram, rather than nothing shown (MT-111, Adam: "Layout greys out
        // all options while Autonomy hides them. Grey them out in Autonomy instead."): a grayed item says "this exists
        // and not now", an empty menu "this feature is gone".  Every item here saves the setup or rebuilds the main
        // window, and an open editor makes both unsafe: saving commits edits that editor has not saved, and the rebuild
        // redraws the main diagram with the editor's edit flag still set on the shared page.
    }

    /**
     * Adding a configuration, offered for the very first setup alone (Adam: "One door: do it").  Once one exists,
     * Manage's New Configuration copies it, so a station's rules, the settings and the speeds are never entered twice.
     */
    private JMenuItem addConfigurationItem(final AutonomyViewerPanel actions)
    {
        JMenuItem add = item(I18n.t("autosetup.ui.menuInitialize"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.initialize();

                ui.autonomyMenuActed();
            }
        });

        add.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipAddConfiguration")));

        return add;
    }

    /**
     * Opening the setup editor, one item per page: a submenu, since the question is a list to pick from.  Pages
     * autonomy has been told to ignore are left out: every square on one is grayed and refuses.
     */
    private JMenu editMenu(final AutonomySession session)
    {
        JMenu edit = new JMenu(I18n.t("autosetup.ui.menuEditAutonomy"));

        for (LayoutDiagram page : session.getPages())
        {
            final String name = page.getName();

            if (session.getStore().getExcludedPages().contains(name)) continue;

            edit.add(item(name, new Runnable()
            {
                @Override
                public void run()
                {
                    ui.openAutonomyEditorOnPage(name);
                }
            }));
        }

        return edit;
    }

    /**
     * The things done to a configuration rather than with one.  All act on whichever configuration is chosen above,
     * which is why they are one level down: on the top level they read as acting on the layout as a whole.
     */
    private JMenu manageMenu(final AutonomyViewerPanel actions, final AutonomySession session,
        boolean loaded)
    {
        JMenu manage = new JMenu(I18n.t("autosetup.ui.btnManage"));

        // One door to a new configuration (Adam: "One door: do it"): a copy of the one chosen, asking whether where the
        // trains stand and the timetable come too.  Add a Configuration is offered only before there is one.
        JMenuItem copy = item(I18n.t("autosetup.ui.menuNewConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.duplicate();

                ui.autonomyMenuActed();
            }
        });

        copy.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipNewConfiguration")));

        manage.add(copy);

        manage.add(item(I18n.t("autosetup.ui.menuRenameConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.rename();

                ui.autonomyMenuActed();
            }
        }));

        JMenuItem deleteOne = item(I18n.t("autosetup.ui.menuDeleteConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.delete();

                ui.autonomyMenuActed();
            }
        });

        manage.add(deleteOne);

        // Import and Export are not here (OB-051): they were correct and unfindable, and sit in the Configuration
        // submenu under a divider, beside the configurations they are about.

        // Everything above acts on the configuration that is running, so it means nothing until one is.  Grayed one at
        // a time rather than the whole submenu, so deleting the lot, below, stays reachable when nothing is loaded.
        for (int i = 0; i < manage.getItemCount(); i++)
        {
            if (manage.getItem(i) == null) continue;

            manage.getItem(i).setEnabled(loaded);

            if (!loaded) manage.getItem(i).setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipNeedsLoaded")));
        }

        // But Delete asks which (OB-319), so it needs only that there is one to delete: a setup that cannot be used is
        // never loaded, and graying Delete until one was left it out of reach.
        boolean anyToDelete = !session.getStore().getConfigurationNames().isEmpty();

        deleteOne.setEnabled(anyToDelete);

        if (anyToDelete) deleteOne.setToolTipText(null);

        manage.addSeparator();

        // The way back out of having set autonomy up at all: configurations could only be deleted one at a time and the
        // last was refused, so an experiment left a setup that could not be removed without deleting a folder by hand.
        JMenuItem forget = item(I18n.t("autosetup.ui.menuDeleteSetup"), new Runnable()
        {
            @Override
            public void run()
            {
                deleteEverything(session);
            }
        });

        forget.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintDeleteSetup")));

        manage.add(forget);

        // Stopping without deleting, the smaller of the two things wanted here: every other path replaces one
        // configuration with another.
        JMenuItem unload = item(I18n.t("autosetup.ui.menuUnloadAutonomy"), new Runnable()
        {
            @Override
            public void run()
            {
                ui.unloadAutonomy();
            }
        });

        unload.setEnabled(loaded);
        unload.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintUnloadAutonomy")));

        manage.add(unload, manage.getItemCount() - 1);

        return manage;
    }

    /**
     * Removes the whole setup, having asked twice over: the message names the layout and says what
     * survives, and the default answer is no.
     */
    private void deleteEverything(AutonomySession session)
    {
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        int names = session.getStore().getConfigurationNames().size();

        // TrainControl's own button text - see the note in AutonomyEditorPanel.  An INDEX comes back,
        // and defaulting to No matters more here than anywhere: this deletes a whole setup.
        int answer = JOptionPane.showOptionDialog(ui,
            I18n.f("autosetup.ui.confirmDeleteSetup", names),
            I18n.t("autosetup.ui.menuDeleteSetup"),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]);

        if (answer != 0) return;

        try
        {
            session.getStore().deleteEverything();

            session.rebuild();
        }
        catch (java.io.IOException e)
        {
            JOptionPane.showMessageDialog(ui,
                I18n.f("autosetup.ui.errorDeleteSetupFailed", String.valueOf(e.getMessage())));
        }

        // Whatever happened, nothing is running against it any more
        ui.autonomySetupDeleted();
    }

    /**
     * Which pages autonomy uses, one checkbox each: a list with a tick against each is what a menu of checkboxes is,
     * and the menu does not close between ticks, so several pages can be changed in one visit.
     */
    private JMenu pagesMenu(final AutonomySession session)
    {
        JMenu pages = new JMenu(I18n.t("autosetup.ui.btnExcludePage"));

        for (LayoutDiagram page : session.getPages())
        {
            final String name = page.getName();

            boolean used = !session.getStore().getExcludedPages().contains(name);

            final JCheckBoxMenuItem box = new JCheckBoxMenuItem(name, used);

            box.addActionListener(e ->
            {
                // Refused while trains are running: the running layout is built from the configuration as loaded and
                // nothing here rebuilds it, so ticking a page off mid-run turned the strip red and took the captions
                // away while the layout kept routing trains to those stations.
                if (ui.refuseWhileARunHoldsTheSetup(ui, () -> box.setSelected(!box.isSelected()))) return;

                // Applying this rebuilds the layout, and every configuration load stops everything, including a train
                // somebody is driving by hand.  isAutonomyBusy above covers a hand dispatch; what is left is a train
                // driven on the throttle alone, which nothing counts and this still sees.  Stopping a moving train is
                // not a side effect of a checkbox to do without saying so.
                if (ui.getActiveDiagramConfiguration() != null)
                {
                    java.util.List<String> moving = ui.locomotivesMoving();

                    if (!moving.isEmpty()
                        && JOptionPane.showOptionDialog(ui,
                            I18n.f("autosetup.ui.confirmExcludeStopsTrains",
                                String.valueOf(moving.size())),
                            I18n.t("ui.dialogConfirm"), JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE, null,
                            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != 0)
                    {
                        box.setSelected(!box.isSelected());
                        return;
                    }
                }

                session.setPageExcluded(name, !box.isSelected());

                try
                {
                    // The reconciliation the save returns is shown, not dropped: it reports what the save had to let go
                    // (a setting on a page that is gone, a pairing whose partner has gone), and silently the operator
                    // finds out later from a railway that behaves differently.
                    AutonomyReport.show(ui, session.save());
                }
                catch (java.io.IOException io)
                {
                    javax.swing.JOptionPane.showMessageDialog(ui, String.valueOf(io.getMessage()));
                }

                ui.autonomyMenuActed();

                // And the layout itself, so what is drawn and what is driven are built from the same list of pages.
                // Does nothing when none is loaded.
                ui.reloadActiveDiagramConfiguration();
            });

            pages.add(box);
        }

        return pages;
    }

    private JMenuItem item(String text, final Runnable action)
    {
        JMenuItem menuItem = new JMenuItem(text);

        menuItem.addActionListener(e -> action.run());

        return menuItem;
    }
}
