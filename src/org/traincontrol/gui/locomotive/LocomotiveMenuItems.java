package org.traincontrol.gui.locomotive;

import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The menu items that belong to a locomotive rather than to the place it was right-clicked (FR-047).  Adam: "SAFELY
 * consolidate the code between these two right click menus to minimize duplication."
 *
 * Eight items were written out twice, in {@link RightClickMenuListener} and {@link RightClickSelectorMenu}.  Safe
 * because each one-argument form (`setLocIcon(l)`, changeLocAddress, changeLocNotes, deleteLoc) is a delegate that
 * passes null, so a single item taking a nullable event makes the same call as whichever copy it replaces.  The order
 * is deliberately NOT consolidated: each menu adds these among its own items and separators.
 *
 * @author Adam
 */
final class LocomotiveMenuItems
{
    private LocomotiveMenuItems()
    {
    }

    /**
     * @param ui the main window
     * @param loc the locomotive
     * @param evt the click, so a dialog opens near it, or null to let the window place it
     * @return the item
     */
    static JMenuItem setLocalIcon(TrainControlUI ui, Locomotive loc, MouseEvent evt)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuSetLocalLocomotiveIcon"));

        item.addActionListener(event -> ui.setLocIcon(loc, evt));

        return item;
    }

    static JMenuItem clearLocalIcon(TrainControlUI ui, Locomotive loc)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuClearLocalLocomotiveIcon"));

        item.addActionListener(event -> ui.clearLocIcon(loc));

        return item;
    }

    /**
     * @param source the button the icons are being customized from, or null when there is none
     */
    static JMenuItem customiseFunctionIcons(TrainControlUI ui, Locomotive loc, JButton source,
        MouseEvent evt)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuCustomizeFunctionIcons"));

        item.addActionListener(event -> ui.setFunctionIcon(loc, source, evt));

        return item;
    }

    static JMenuItem editNameAddressDecoder(TrainControlUI ui, Locomotive loc, MouseEvent evt)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuEditNameAddressDecoder"));

        item.addActionListener(event -> ui.changeLocAddress(loc, evt));

        return item;
    }

    static JMenuItem editNotes(TrainControlUI ui, Locomotive loc, MouseEvent evt)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuEditNotes"));

        item.addActionListener(event -> ui.changeLocNotes(loc, evt));

        return item;
    }

    static JMenuItem findSimilar(TrainControlUI ui, Locomotive loc, MouseEvent evt)
    {
        JMenuItem item = new JMenuItem(I18n.t("loc.ui.menuFindSimilarLocomotives"));

        item.setToolTipText(I18n.t("loc.ui.tooltip.findSimilarHint"));
        item.addActionListener(event -> ui.findSimilarLocs(loc, evt));

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

        item.addActionListener(event -> ui.changeLinkedLocomotives(loc));

        return item;
    }

    /**
     * How long this train is, or null when there is no reason to ask (FR-047).  Adam: "visible only when autonomy is
     * loaded." The number decides only whether a train is refused a platform too short for it (the other half of the
     * FR-046 warning).  Null rather than a disabled item, because a grayed entry invites "why not" and this menu cannot
     * say there is no autonomy.
     *
     * @return the item, or null when autonomy is not loaded
     */
    static JMenuItem trainLength(TrainControlUI ui, Locomotive loc, MouseEvent evt)
    {
        if (ui == null || loc == null) return null;

        // Loaded, not merely available: hasAutoLayout() is true whenever a graph exists, including one whose setup
        // nobody has loaded.
        if (!ui.isAutonomyLoaded()) return null;

        Integer length = loc.getTrainLength();

        JMenuItem item = new JMenuItem(I18n.f("loc.ui.menuAutonomyTrainLength",
            length == null || length <= 0
                ? I18n.t("loc.ui.trainLengthNotSet") : String.valueOf(length)));

        item.addActionListener(event -> ui.promptTrainLength(loc, evt));

        return item;
    }
}
