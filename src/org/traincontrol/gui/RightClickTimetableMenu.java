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
            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuChangeDelay")
            );
            menuItem.addActionListener(event -> ui.updateTimetableDelay(e));
            menuItem.setToolTipText(I18n.t("timetable.ui.tooltip.changeDelay"));
            add(menuItem);

            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuDeleteEntry")
            );
            menuItem.addActionListener(event -> ui.deleteTimetableEntry(e));
            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuRestartTimetable")
            );
            menuItem.addActionListener(event -> ui.restartTimetable());
            menuItem.setToolTipText(I18n.t("timetable.ui.tooltip.restartTimetable"));
            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("timetable.ui.menuClearTimetable")
            );
            menuItem.setForeground(Color.RED);
            menuItem.addActionListener(event -> ui.clearTimetable());
            menuItem.setToolTipText(I18n.t("timetable.ui.tooltip.clearTimetable"));
            add(menuItem);
        }
    }
}
   