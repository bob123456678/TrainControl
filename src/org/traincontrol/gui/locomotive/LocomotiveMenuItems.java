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

    /**
     * Locomotive Settings..., which opens the window on its first tab.  On the key menu it is grayed, with the reason as
     * its tooltip, while the locomotive is moving (FR-048); other doors say so when pressed.
     *
     * @param ui the main window
     * @param loc the locomotive
     * @param grayWhenMoving whether to gray it while the locomotive is moving
     * @return the item
     */
    static JMenuItem settings(TrainControlUI ui, Locomotive loc, boolean grayWhenMoving)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuLocomotiveSettings"));

        item.setName("menu.locomotiveSettings");
        item.addActionListener(event -> ui.openLocomotiveSettings(loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null));
        if (grayWhenMoving) grayWhileMoving(ui, loc, item);

        return item;
    }

    /** The moving rule for a menu entry: grayed, and the tooltip says why. */
    private static void grayWhileMoving(TrainControlUI ui, Locomotive loc, JMenuItem item)
    {
        String why = ui.whyNotEditable(loc);

        if (why != null)
        {
            item.setEnabled(false);
            item.setToolTipText(why);
        }
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
    static JMenuItem multiUnit(TrainControlUI ui, Locomotive loc, boolean grayWhenMoving)
    {
        String text = loc.getDecoderType() == Locomotive.decoderType.MULTI_UNIT
            ? I18n.t("loc.ui.menuViewMultiUnitLocomotives")
            : loc.hasLinkedLocomotives() ? I18n.t("loc.ui.menuEditMultiUnitLocomotives") : I18n.t("loc.ui.menuSetAsMultiUnit");

        JMenuItem item = new JMenuItem(text);

        item.setName("menu.multiUnit");
        item.addActionListener(event -> ui.openLocomotiveSettings(loc, LocomotiveSettingsFrame.Tab.MULTI_UNIT, null));
        if (grayWhenMoving) grayWhileMoving(ui, loc, item);

        return item;
    }
}
