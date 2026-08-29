package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Route;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu with various utility functions displayed when any route entry is right-clicked
 * @author Adam
 */
public class RightClickRouteMenu extends MouseAdapter
{    
    protected TrainControlUI ui;
    
    public RightClickRouteMenu(TrainControlUI u)
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
        if (ui.getRouteAtCursor(e) != null)
        {
            RightClickMenu menu = new RightClickMenu(ui, e);
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

                // UXR-B6, reconciled. `hasS88()` alone was right against the action as it read an
                // hour ago - the whole body was gated on it, disable included - and a second fix has
                // since corrected that, because turning autofire OFF never needed a sensor.
                //
                // So the affordance has to move with it, or it is stricter than the thing it offers:
                // a route whose s88 was removed while autofire was still on could be disabled from
                // Bulk Disable and not from its own menu. That is the original defect reflected.
                //
                // This is the predicate `BulkEnableOrDisable` has always used, and it is the action's
                // rule seen from here: offering DISABLE on an enabled route always works; offering
                // ENABLE needs a sensor.
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
   