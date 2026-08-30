package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Locomotive;
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

        // No current button means no key to render - (char) -1 painted as U+FFFF garbage.
        // Without a target the item is meaningless, so it is omitted rather than disabled.
        Integer currentButtonKey = ui.getKeyForCurrentButton();

        if (currentButtonKey != null && currentButtonKey != -1)
        {
            menuItem = new JMenuItem(
                I18n.f("loc.ui.menuAssignToButton", String.valueOf((char) currentButtonKey.intValue()))
            );
            menuItem.addActionListener(event -> {
                ui.mapLocToCurrentButton(loc.getName());
                ui.getLocSelector().refreshToolTips();
            });
            add(menuItem);
        }

        add(LocomotiveMenuItems.setLocalIcon(ui, loc, e));

        if (loc.getLocalImageURL() != null)
        {
            add(LocomotiveMenuItems.clearLocalIcon(ui, loc));
        }

        add(LocomotiveMenuItems.customiseFunctionIcons(ui, loc, null, e));

        addSeparator();

        add(LocomotiveMenuItems.editNameAddressDecoder(ui, loc, e));

        add(LocomotiveMenuItems.editNotes(ui, loc, e));

        // HOW LONG THIS TRAIN IS, when there is autonomy to care (FR-047).
        //
        // Beside the other facts about the locomotive itself rather than with the icons: it is a
        // number about the train, like its name and address, and the items above it are about how it
        // is drawn.
        JMenuItem length = LocomotiveMenuItems.trainLength(ui, loc, e);

        if (length != null) add(length);

        addSeparator();

        add(LocomotiveMenuItems.findSimilar(ui, loc, e));

        addSeparator();

        add(LocomotiveMenuItems.deleteFromDatabase(ui, loc, e));
    }
}
   