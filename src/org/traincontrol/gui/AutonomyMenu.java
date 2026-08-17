package org.traincontrol.gui;

import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
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

    private AutonomyViewerPanel actions()
    {
        return ui.getAutonomyViewerPanel();
    }

    private void rebuild()
    {
        removeAll();

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

            // Both act on the configuration that is RUNNING, so neither means anything until one is.
            // Choosing which to load is the step before these, and it is the item above.
            boolean loaded = running != null;

            // Fenced off by itself, because it is the only item here that changes the RAILWAY.
            // Everything above chooses which setup is in force and everything below is housekeeping on
            // the file that holds it; this one opens the editor and starts naming stations and setting
            // which way trains may run.
            addSeparator();

            JMenu edit = editMenu(session);
            edit.setEnabled(loaded && edit.getItemCount() > 0);
            edit.setToolTipText(loaded ? I18n.t("autosetup.ui.tooltipEditAutonomy")
                : I18n.t("autosetup.ui.tooltipNeedsLoaded"));
            add(edit);

            addSeparator();

            JMenu manage = manageMenu(actions, names);
            manage.setEnabled(loaded);
            manage.setToolTipText(loaded ? null : I18n.t("autosetup.ui.tooltipNeedsLoaded"));
            add(manage);

            JMenu pages = pagesMenu(session);
            pages.setEnabled(loaded);
            pages.setToolTipText(loaded ? I18n.t("autosetup.ui.promptExcludePage")
                : I18n.t("autosetup.ui.tooltipNeedsLoaded"));
            add(pages);

            addSeparator();
            add(addConfigurationItem(actions));
        }

        if (ui.getModel() != null && ui.getModel().isDebug())
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
    private JMenu manageMenu(final AutonomyViewerPanel actions, List<String> names)
    {
        JMenu manage = new JMenu(I18n.t("autosetup.ui.btnManage"));

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

        manage.add(item(I18n.t("autosetup.ui.btnImportConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.importConfiguration();

                ui.autonomyMenuActed();
            }
        }));

        manage.add(item(I18n.t("autosetup.ui.btnExportConfiguration"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.exportConfiguration();
            }
        }));

        return manage;
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
