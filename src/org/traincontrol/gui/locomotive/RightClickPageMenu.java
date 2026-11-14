package org.traincontrol.gui.locomotive;

import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.PopupListener;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu with various utility functions for locomotive mapping pages
 * @author Adam
 */
public class RightClickPageMenu extends PopupListener
{    
    protected TrainControlUI ui;
    
    public RightClickPageMenu(TrainControlUI u)
    {
        this.ui = u;
    }
    
    @Override
    protected void showPopup(MouseEvent e)
    {
        RightClickMenu menu = new RightClickMenu(ui, e);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    final class RightClickMenu extends JPopupMenu
    {
        /**
         * Its tooltips wrapped where a line will not hold them, as it is shown (FR-112) - whatever set them.
         *
         * @param invoker where it is shown
         * @param x where it is shown
         * @param y where it is shown
         */
        @Override
        public void show(java.awt.Component invoker, int x, int y)
        {
            AutonomyEditorPanel.wrapTheTooltips(this);

            super.show(invoker, x, y);
        }

        JMenuItem menuItem;

        public RightClickMenu(TrainControlUI ui, MouseEvent e)
        {
            menuItem = new JMenuItem(
                I18n.f("page.ui.menuRenamePage", ui.getLocMappingNumber())
            );
            menuItem.addActionListener(event -> ui.renameCurrentPage());
            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("page.ui.menuCopyMappings")
            );
            menuItem.addActionListener(event -> ui.copyCurrentPage());
            add(menuItem);

            menuItem = new JMenuItem(
                I18n.t("page.ui.menuPasteMappings")
            );

            if (ui.pageCopied())
            {
                menuItem.addActionListener(event -> ui.pasteCopiedPage());
            }
            else
            {
                menuItem.setEnabled(false);
            }

            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("page.ui.menuMapUnassignedLocomotives")
            );
            menuItem.addActionListener(event -> ui.mapUnassignedLocomotives());
            menuItem.setToolTipText(I18n.t("page.ui.tooltip.fillHint"));
            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("page.ui.menuResetCurrentMappings")
            );
            menuItem.addActionListener(event -> ui.clearCurrentPage());
            add(menuItem);

            addSeparator();

            // No number on this one, though Delete below has one: "Add Page 11" beside "Delete Page 4" invites working
            // out how they relate, which is not at all.  Delete keeps its number because it names something that
            // exists.
            menuItem = new JMenuItem(
                I18n.t("page.ui.menuAddPage")
            );
            // Grayed at the ceiling, with the reason on it, as Delete does below.
            if (ui.canAddLocMappingPage())
            {
                menuItem.addActionListener(event -> ui.addLocMappingPage());
                menuItem.setToolTipText(I18n.t("page.ui.tooltipAddPage"));
            }
            else
            {
                menuItem.setEnabled(false);
                menuItem.setToolTipText(
                    I18n.f("page.ui.errorTooManyPages", TrainControlUI.MAX_LOC_MAPPINGS));
            }

            add(menuItem);

            // Offered and refused rather than hidden, with the reason on it: an item that comes and goes reads as an
            // interface that cannot make up its mind.
            menuItem = new JMenuItem(
                I18n.f("page.ui.menuDeletePage", ui.getLocMappingNumber())
            );

            if (ui.canDeleteCurrentPage())
            {
                menuItem.addActionListener(event -> ui.deleteCurrentLocMappingPage());
            }
            else
            {
                menuItem.setEnabled(false);
            }

            menuItem.setToolTipText(I18n.t("page.ui.tooltipDeletePage"));
            add(menuItem);
        }
    }
}
   