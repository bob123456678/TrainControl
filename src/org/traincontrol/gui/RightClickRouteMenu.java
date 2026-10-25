package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Route;
import org.traincontrol.gui.widgets.PopupListener;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu with various utility functions displayed when any route entry is right-clicked
 * @author Adam
 */
public class RightClickRouteMenu extends PopupListener
{    
    protected TrainControlUI ui;
    
    public RightClickRouteMenu(TrainControlUI u)
    {
        this.ui = u;
    }
    
    @Override
    protected void showPopup(MouseEvent e)
    {
        openFor(ui, e);
    }

    /**
     * Opens this menu wherever the pointer is, for a click that was not a popup trigger (FR-043).  Adam: "make all
     * other left or clicks on the table cells open the right-click menu."  The play button on the cell executes a route
     * without asking, so every other click opens the menu rather than doing nothing.
     *
     * Here rather than copied into the window, because the menu knows how to refuse: a click not over a route opens
     * nothing.
     *
     * @param ui the main window
     * @param e the click
     */
    public static void openFor(TrainControlUI ui, MouseEvent e)
    {
        if (ui.getRouteAtCursor(e) != null)
        {
            RightClickRouteMenu owner = new RightClickRouteMenu(ui);

            RightClickMenu menu = owner.new RightClickMenu(ui, e);

            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }
    
    final class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public String getRouteTooltip(TrainControlUI ui, String route)
        {
            Route currentRoute = ui.getModel().getRoute(route);
            return currentRoute.getName() + " (" + I18n.t("route.ui.id") + ": " + ui.getModel().getRouteId(route) + ")";
        }
        
        public RightClickMenu(TrainControlUI ui, MouseEvent e)
        {       
            Route route = ui.getRouteAtCursor(e);

            if (route != null)
            {
                String routeName = route.getName();

                menuItem = new JMenuItem(
                    I18n.f("route.ui.menuExecuteRoute", getRouteTooltip(ui, routeName))
                );
                menuItem.addActionListener(event -> ui.executeRoute(routeName));
                add(menuItem);
                addSeparator();

                menuItem = new JMenuItem(
                    route.isLocked()
                        ? I18n.t("route.ui.menuViewRouteDetails")
                        : I18n.t("route.ui.menuEditRoute")
                );
                menuItem.addActionListener(event -> ui.editRoute(routeName));
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("route.ui.menuDuplicateRoute")
                );
                menuItem.addActionListener(event -> ui.duplicateRoute(routeName));
                add(menuItem);

                // `hasS88()` alone was the rule while the action was gated on it wholesale, but turning autofire OFF
                // never needed a sensor, so the affordance has to move with the action or it is stricter than the thing
                // it offers: a route whose s88 was removed while autofire was on could be disabled from Bulk Disable
                // and not from its own menu.  This is the predicate `BulkEnableOrDisable` uses: offering DISABLE on an
                // enabled route always works, ENABLE needs a sensor.
                if (route.hasS88() || route.isEnabled())
                {
                    addSeparator();

                    if (!route.isEnabled())
                    {
                        menuItem = new JMenuItem(
                            I18n.t("route.ui.menuEnableAutoExecution")
                        );
                        menuItem.addActionListener(event -> ui.enableOrDisableRoute(routeName, true));
                        add(menuItem);
                    }
                    else
                    {
                        menuItem = new JMenuItem(
                            I18n.t("route.ui.menuDisableAutoExecution")
                        );
                        menuItem.addActionListener(event -> ui.enableOrDisableRoute(routeName, false));
                        add(menuItem);
                    }
                }

                if (!route.isLocked())
                {
                    addSeparator();

                    menuItem = new JMenuItem(
                        I18n.t("route.ui.menuChangeRouteId")
                    );
                    menuItem.addActionListener(event -> ui.changeRouteId(routeName));
                    add(menuItem);

                    menuItem = new JMenuItem(
                        I18n.t("route.ui.menuDeleteRoute")
                    );
                    menuItem.setForeground(Color.RED);
                    menuItem.addActionListener(event -> ui.deleteRoute(routeName));
                    add(menuItem);
                }
            }
        }
    }
}
   