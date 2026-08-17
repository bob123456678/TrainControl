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

        // Setting one up for the first time.  Offered even when configurations exist, because "another
        // one, built fresh from the diagram" is a different act from duplicating what is there.
        add(item(I18n.t("autosetup.ui.menuInitialize"), new Runnable()
        {
            @Override
            public void run()
            {
                actions.initialize();

                ui.autonomyMenuActed();
            }
        }));

        if (!names.isEmpty())
        {
            addSeparator();

            // Which one runs.  Radio, because exactly one does - and the one that is running is the
            // fact the user most often opens this menu to check.
            ButtonGroup group = new ButtonGroup();

            String running = ui.getActiveDiagramConfiguration();

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

                add(choice);
            }

            addSeparator();
            add(manageMenu(actions, names));
            add(pagesMenu(session));
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

        manage.setEnabled(!names.isEmpty());

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

        pages.setToolTipText(I18n.t("autosetup.ui.promptExcludePage"));

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
