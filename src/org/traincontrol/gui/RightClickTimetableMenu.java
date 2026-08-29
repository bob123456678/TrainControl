package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu with various utility functions displayed when any timetable entry is right-clicked
 * @author Adam
 */
public class RightClickTimetableMenu extends MouseAdapter
{    
    protected TrainControlUI ui;
    
    public RightClickTimetableMenu(TrainControlUI u)
    {
        this.ui = u;
    }
    
    @Override
    public void mousePressed(MouseEvent e)
    {
        if (e.isPopupTrigger()) showPopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (e.isPopupTrigger()) showPopup(e);
    }

    private void showPopup(MouseEvent e)
    {
        if (ui.getTimetableEntryAtCursor(e) != null)
        {
            RightClickMenu menu = new RightClickMenu(ui, e);
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }
    
    final class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public RightClickMenu(TrainControlUI ui, MouseEvent e)
        {
            // UXR-B12: all four handlers below open with the identical
            // `if (ui.isAutonomyBusy()) { showMessageDialog(...); return; }` guard, so right-clicking
            // the timetable during a run - exactly when an operator is likely to be looking at it -
            // offered four items and refused all four with the same dialog. Greyed here on the same
            // question the handler asks, with the same sentence as the tooltip, rather than found out
            // by pressing.
            boolean busy = ui.isAutonomyBusy();
            String busyTooltip = busy
                ? I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop") : null;

            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuChangeDelay")
            );
            menuItem.addActionListener(event -> ui.updateTimetableDelay(e));
            menuItem.setToolTipText(busy ? busyTooltip : I18n.t("timetable.ui.tooltip.changeDelay"));
            menuItem.setEnabled(!busy);
            add(menuItem);

            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuDeleteEntry")
            );
            menuItem.addActionListener(event -> ui.deleteTimetableEntry(e));
            menuItem.setToolTipText(busyTooltip);
            menuItem.setEnabled(!busy);
            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuRestartTimetable")
            );
            menuItem.addActionListener(event -> ui.restartTimetable());
            menuItem.setToolTipText(busy ? busyTooltip : I18n.t("timetable.ui.tooltip.restartTimetable"));
            menuItem.setEnabled(!busy);
            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuClearTimetable")
            );
            menuItem.setForeground(Color.RED);
            menuItem.addActionListener(event -> ui.clearTimetable());
            menuItem.setToolTipText(busy ? busyTooltip : I18n.t("timetable.ui.tooltip.clearTimetable"));
            menuItem.setEnabled(!busy);
            add(menuItem);
        }
    }
}
   