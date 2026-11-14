package org.traincontrol.gui.locomotive;

import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The menu items that belong to a locomotive rather than to the place it was right-clicked, shared by
 * {@link RightClickMenuListener} and {@link RightClickSelectorMenu}.  The order is deliberately NOT consolidated: each
 * menu adds these among its own items and separators.
 *
 * @author Adam
 */
final class LocomotiveMenuItems
{
    private LocomotiveMenuItems()
    {
    }

    /** Locomotive Settings..., which opens the window on its first tab; a locomotive that cannot be edited opens locked. */
    static JMenuItem settings(TrainControlUI ui, Locomotive loc)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuLocomotiveSettings"));

        item.setName("menu.locomotiveSettings");
        item.addActionListener(event -> ui.openLocomotiveSettings(loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null));

        return item;
    }

    static JMenuItem deleteFromDatabase(TrainControlUI ui, Locomotive loc, MouseEvent evt)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuDeleteFromDatabase"));

        item.setForeground(java.awt.Color.RED);
        item.addActionListener(event -> ui.deleteLoc(loc.getName(), evt));

        return item;
    }

    /**
     * Set as, edit or view a multi-unit - the locomotive's own, so the keyboard's menu and the locomotive database's
     * offer the same item.  A multi-unit the Central Station defines is shown, not edited.
     *
     * @param ui the main window
     * @param loc the locomotive
     * @return the item
     */
    static JMenuItem multiUnit(TrainControlUI ui, Locomotive loc)
    {
        String text = loc.getDecoderType() == Locomotive.decoderType.MULTI_UNIT
            ? I18n.t("loc.ui.menuViewMultiUnitLocomotives")
            : loc.hasLinkedLocomotives() ? I18n.t("loc.ui.menuEditMultiUnitLocomotives") : I18n.t("loc.ui.menuSetAsMultiUnit");

        JMenuItem item = new JMenuItem(text);

        item.setName("menu.multiUnit");
        item.addActionListener(event -> ui.openLocomotiveSettings(loc, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null));

        return item;
    }
}
