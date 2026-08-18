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
import org.traincontrol.util.I18n;

/**
 * Setting autonomy up, from the menu bar beside Layouts.
 *
 * This was a tab, and a tab was the wrong shape for it.  Everything here is something a user does
 * ONCE - choose a configuration, rename one, leave a page out - and then never looks at again, while
 * the tab it occupied sat in the strip beside the ones they use constantly.  Worse, it was a page of
 * controls describing the diagram on a DIFFERENT page, so acting on it meant looking away from the
 * thing being set up.
 *
 * Next to Layouts on purpose: a setup belongs to a layout, cannot exist without one, and the two are
 * chosen the same way.  With no layout the menu is disabled and says why rather than opening onto a
 * list of things none of which can be done.
 *
 * Rebuilt each time it opens.  A menu is cheap to build and the alternative - keeping items in step
 * with configurations being added, renamed and deleted from inside the menu itself - is a second copy
 * of the truth that can disagree with the first.
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
     * Switches the menu on or off according to whether there is a layout to set autonomy up for.
     *
     * Disabled rather than hidden, with the reason on the tooltip: a menu that vanishes teaches
     * nothing, and "why is there no autonomy menu" is a harder question than "what do I do first".
     */
    public final void refreshEnabled()
    {
        boolean hasLayout = ui.getModel() != null && !ui.getModel().getLayoutList().isEmpty();

        setEnabled(hasLayout);

        setToolTipText(hasLayout ? null : I18n.t("autosetup.ui.tooltipNoLayout"));
    }

    /**
     * The pages submenu as it was last built, so that something else can send the user straight to it.
     *
     * The diagram says when the page being looked at is left out of autonomy, and that statement is only
     * half an answer without a way to change it - the setting lives three levels into a menu somebody
     * would have to know to open.
     */
    private JMenu lastPagesMenu;

    /**
     * Opens this menu with the pages submenu already showing.
     *
     * Driven through the MenuSelectionManager rather than by clicking: a click would toggle the menu
     * shut again if it happened to be open, and there is no path from a click to a SUBmenu at all.
     */
    public void showPages()
    {
        if (!(getParent() instanceof javax.swing.JMenuBar)) return;

        final javax.swing.JMenuBar bar = (javax.swing.JMenuBar) getParent();

        // Open this menu first, and only then walk into the submenu.
        //
        // Not one setSelectedPath with the whole path in it, which is what this did and why nothing
        // appeared: selecting the menu fires menuSelected, which rebuilds every item - so the submenu
        // named in the path was removed from the menu a moment after being pointed at, and the
        // selection came apart.  Opening in two steps lets the rebuild happen in between, and the
        // second step then points at the submenu that rebuild actually produced.
        javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
            new javax.swing.MenuElement[] { bar, this, getPopupMenu() });

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            // Unavailable - no configuration chosen - so this menu is as far as it can honestly take
            // them, and its items say why.
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

    private void rebuild()
    {
        removeAll();

        // Dropped, not kept.  It is only reassigned on the branch that builds the submenu, so after a
        // rebuild that takes another branch it pointed at a JMenu no longer in this popup - and
        // showPages then asked Swing to open a detached component.
        lastPagesMenu = null;

        // Nothing here while an editor has the diagram.
        //
        // Every item saves the setup or rebuilds the main window, and an open editor makes both unsafe:
        // saving commits the edits that editor has not saved - so its Cancel then has nothing to take
        // back - and the rebuild redraws the main diagram with the editor's edit flag still set on the
        // shared page, after which every tile there tries to talk to a window that is not its parent.
        if (ui.isLayoutEditorOpen())
        {
            JMenuItem busy = new JMenuItem(I18n.t("autosetup.ui.menuEditorOpen"));
            busy.setEnabled(false);
            add(busy);

            return;
        }

        final AutonomyViewerPanel actions = actions();
        AutonomySession session = ui.getAutonomySession();

        if (actions == null || session == null)
        {
            JMenuItem none = new JMenuItem(I18n.t("autosetup.ui.menuNoSetupPossible"));
            none.setEnabled(false);
            add(none);

            return;
        }

        List<String> names = session.getStore().getConfigurationNames();

        String running = ui.getActiveDiagramConfiguration();

        // Nothing set up yet: one thing to do, and no submenus of things that would all be empty.
        if (names.isEmpty())
        {
            add(addConfigurationItem(actions));
        }
        else
        {
            // The configurations get a submenu of their own rather than sitting loose on the menu,
            // where a name like "Adam 1" reads as an action next to the items around it.  The heading
            // carries the running one, which is the fact this menu is most often opened to check.
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
                    actions.setSelectedConfiguration(name);
                    actions.load(name, true);

                    ui.autonomyMenuActed();
                });

                choose.add(choice);
            }

            add(choose);

            // Manage acts on the configuration that is RUNNING, so it means nothing until one is.
            boolean loaded = running != null;

            // Editing and page exclusions ask only that a configuration be CHOSEN.  Both are how a
            // setup that will not load gets FIXED - it refuses on blocking errors, and the editor is
            // the only place those can be dealt with - so gating them on a running setup locked the
            // door on the room the user was being sent to: "4 things must be dealt with", and no way
            // to deal with them.
            boolean chosen = session.getStore().getActiveConfiguration() != null;

            // The settings for the whole setup - pace, how many trains at once, what a train may do on
            // arrival.  They live on a tab inside a tab, which is somewhere nobody finds by looking,
            // and this menu is where every other decision about the setup is made.
            //
            // Loaded, not merely chosen: that tab is built when a configuration loads and is not there
            // before, so offering it earlier would be an item that goes nowhere.
            JMenuItem settings = item(I18n.t("autosetup.ui.menuGlobalSettings"), new Runnable()
            {
                @Override
                public void run()
                {
                    ui.showAutonomySettingsTab();
                }
            });

            settings.setEnabled(loaded);

            settings.setToolTipText(loaded
                ? I18n.t("autosetup.ui.tooltipGlobalSettings")
                : I18n.t("autosetup.ui.tooltipNeedsLoaded"));

            add(settings);

            // Fenced off by itself, because it is the only item here that changes the RAILWAY.
            // Everything above chooses which setup is in force and everything below is housekeeping on
            // the file that holds it; this one opens the editor and starts naming stations and setting
            // which way trains may run.
            addSeparator();

            JMenu edit = editMenu(session);
            edit.setEnabled(chosen && edit.getItemCount() > 0);
            edit.setToolTipText(chosen ? I18n.t("autosetup.ui.tooltipEditAutonomy")
                : I18n.t("autosetup.ui.tooltipNeedsLoaded"));
            add(edit);

            addSeparator();

            JMenu manage = manageMenu(actions, session, loaded);
            add(manage);

            JMenu pages = pagesMenu(session);
            lastPagesMenu = pages;
            pages.setEnabled(chosen);
            pages.setToolTipText(chosen ? I18n.t("autosetup.ui.promptExcludePage")
                : I18n.t("autosetup.ui.tooltipNeedsLoaded"));
            add(pages);

        }

        // Exporting the derived graph, for a debug session and only when there is a graph to export.
        //
        // It used to appear whenever debug was on, including on a layout with no setup at all and on one
        // whose setup will not build - so the one item here that promises a file offered it in the two
        // states where pressing it produces either nothing or a graph the user has been told is invalid.
        // Debug or not, a menu item that cannot do what it says is worse than an absent one.
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
    }

    /**
     * Adding a configuration - the same offer whether or not any exist yet.
     *
     * Worded the same in both cases on purpose.  It said "set autonomy up for this layout" when there
     * was nothing, and "new configuration from this layout" when there was, which read as two different
     * features; every configuration is built from this layout, so that phrase distinguished nothing.
     * What it actually differs from is Duplicate, and the tooltip is where that belongs.
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

        add.setToolTipText(I18n.t("autosetup.ui.tooltipAddConfiguration"));

        return add;
    }

    /**
     * Opening the setup editor, one item per page.
     *
     * A submenu rather than a dialog asking which page: the question is a list of pages to pick from,
     * which is what a submenu already is, and it answers in one click instead of three.
     *
     * Pages autonomy has been told to ignore are left out.  There is nothing on one to configure -
     * every square is greyed and refuses - so offering it would open an editor that can do nothing.
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
     * The things done to a configuration rather than with one.
     *
     * All of them act on whichever configuration is chosen above, which is why they are one level down:
     * on the top level they read as things that might act on the layout as a whole.
     */
    private JMenu manageMenu(final AutonomyViewerPanel actions, final AutonomySession session,
        boolean loaded)
    {
        JMenu manage = new JMenu(I18n.t("autosetup.ui.btnManage"));

        // Adding one lives here now rather than loose at the bottom of the menu.  On the top level it
        // sat beside "which configuration is running", where it read as a third choice of the same kind;
        // in here it sits beside duplicate, rename and delete, which is what it actually is.
        manage.add(addConfigurationItem(actions));

        manage.addSeparator();

        manage.add(item(I18n.t("autosetup.ui.menuNewConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.duplicate();

                ui.autonomyMenuActed();
            }
        }));

        manage.add(item(I18n.t("autosetup.ui.menuRenameConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.rename();

                ui.autonomyMenuActed();
            }
        }));

        manage.add(item(I18n.t("autosetup.ui.menuDeleteConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.delete();

                ui.autonomyMenuActed();
            }
        }));

        manage.addSeparator();

        // Held, so the greying below can skip it.  Import does NOT act on the configuration that
        // is running - it brings one in from a file - so gating it on something being loaded locked
        // the door in exactly the situation it exists for: a setup that will not load, which is
        // repaired by importing one that will.  The same argument the comment below makes for adding.
        JMenuItem importItem = item(I18n.t("autosetup.ui.btnImportConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.importConfiguration();

                ui.autonomyMenuActed();
            }
        });

        manage.add(importItem);

        manage.add(item(I18n.t("autosetup.ui.btnExportConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.exportConfiguration();
            }
        }));

        // Everything above except adding acts on the configuration that is RUNNING, so it means nothing
        // until one is.  Greyed one at a time rather than the whole submenu, so that adding - and
        // deleting the lot, below - stay reachable when nothing is loaded, which is exactly when
        // somebody wants them.
        for (int i = 2; i < manage.getItemCount(); i++)
        {
            if (manage.getItem(i) == null || manage.getItem(i) == importItem) continue;

            manage.getItem(i).setEnabled(loaded);

            if (!loaded) manage.getItem(i).setToolTipText(I18n.t("autosetup.ui.tooltipNeedsLoaded"));
        }

        manage.addSeparator();

        // The way back out of having set autonomy up at all.  Configurations could only be deleted one
        // at a time and the last one was refused, so a layout somebody had experimented on kept a setup
        // they could not be rid of without deleting a folder by hand.
        JMenuItem forget = item(I18n.t("autosetup.ui.menuDeleteSetup"), new Runnable()
        {
            @Override
            public void run()
            {
                deleteEverything(session);
            }
        });

        forget.setToolTipText(I18n.t("autosetup.ui.hintDeleteSetup"));

        manage.add(forget);

        // Stopping without deleting, which is the smaller of the two things somebody wants here and the
        // one there was no way to do at all: every other path replaces one configuration with another.
        JMenuItem unload = item(I18n.t("autosetup.ui.menuUnloadAutonomy"), new Runnable()
        {
            @Override
            public void run()
            {
                ui.unloadAutonomy();
            }
        });

        unload.setEnabled(loaded);
        unload.setToolTipText(I18n.t("autosetup.ui.hintUnloadAutonomy"));

        manage.add(unload, manage.getItemCount() - 1);

        return manage;
    }

    /**
     * Removes the whole setup, having asked twice over: the message names the layout and says what
     * survives, and the default answer is no.
     */
    private void deleteEverything(AutonomySession session)
    {
        if (ui.isAutonomyBusy())
        {
            JOptionPane.showMessageDialog(ui, I18n.t("autolayout.errorCannotEditWhileRunning"));
            return;
        }

        int names = session.getStore().getConfigurationNames().size();

        int answer = JOptionPane.showConfirmDialog(ui,
            I18n.f("autosetup.ui.confirmDeleteSetup", names),
            I18n.t("autosetup.ui.menuDeleteSetup"),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (answer != JOptionPane.YES_OPTION) return;

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
     * Which pages autonomy uses, one checkbox each.
     *
     * A submenu of checkboxes rather than the dialog this used to be: the question is a list of pages
     * with a tick against each, which is exactly what a menu of checkboxes IS - and a dialog for it
     * meant opening a window to answer something that fits in the menu already open.
     *
     * The menu does not close between ticks, so several pages can be changed in one visit.
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
                session.setPageExcluded(name, !box.isSelected());

                try
                {
                    session.save();
                }
                catch (java.io.IOException io)
                {
                    javax.swing.JOptionPane.showMessageDialog(ui, String.valueOf(io.getMessage()));
                }

                ui.autonomyMenuActed();
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
