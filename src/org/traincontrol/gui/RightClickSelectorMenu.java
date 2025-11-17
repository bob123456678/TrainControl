package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu with various utility functions displayed when any locomotive DB tile is right-clicked
 * @author Adam
 */
public class RightClickSelectorMenu extends JPopupMenu
{
    JMenuItem menuItem;

    public RightClickSelectorMenu(TrainControlUI ui, MouseEvent e, Locomotive loc)
    {
        menuItem = new JMenuItem(loc.getName());
        menuItem.setEnabled(false);
        add(menuItem);

        addSeparator();

        menuItem = new JMenuItem(
            I18n.f("loc.ui.menuAssignToButton", String.valueOf((char) ui.getKeyForCurrentButton().intValue()))
        );
        menuItem.addActionListener(event -> {
            ui.mapLocToCurrentButton(loc.getName());
            ui.getLocSelector().refreshToolTips();
        });
        add(menuItem);

        menuItem = new JMenuItem(
            I18n.t("loc.ui.menuSetLocalLocomotiveIcon")
        );
        menuItem.addActionListener(event -> ui.setLocIcon(loc, e));
        add(menuItem);

        if (loc.getLocalImageURL() != null)
        {
            menuItem = new JMenuItem(
                I18n.t("loc.ui.menuClearLocalLocomotiveIcon")
            );
            menuItem.addActionListener(event -> ui.clearLocIcon(loc));
            add(menuItem);
        }

        menuItem = new JMenuItem(
            I18n.t("loc.ui.menuCustomizeFunctionIcons")
        );
        menuItem.addActionListener(event -> ui.setFunctionIcon(loc, null, e));
        add(menuItem);

        addSeparator();

        menuItem = new JMenuItem(
            I18n.t("loc.ui.menuEditNameAddressDecoder")
        );
        menuItem.addActionListener(event -> ui.changeLocAddress((MarklinLocomotive) loc, e));
        add(menuItem);

        menuItem = new JMenuItem(
            I18n.t("loc.ui.menuEditNotes")
        );
        menuItem.addActionListener(event -> ui.changeLocNotes(loc, e));
        add(menuItem);

        addSeparator();

        menuItem = new JMenuItem(
            I18n.t("loc.ui.menuFindSimilarLocomotives")
        );
        menuItem.addActionListener(event -> ui.findSimilarLocs(loc, e));
        menuItem.setToolTipText(I18n.t("loc.ui.tooltip.findSimilarHint"));
        add(menuItem);

        addSeparator();

        menuItem = new JMenuItem(
            I18n.t("loc.ui.menuDeleteFromDatabase")
        );
        menuItem.setForeground(Color.RED);
        menuItem.addActionListener(event -> {
            ui.deleteLoc(loc.getName(), e);
        });
        add(menuItem);
    }
}
   