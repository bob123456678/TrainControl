package org.traincontrol.gui;

import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * The menu items that belong to a locomotive rather than to the place it was right-clicked (FR-047).
 *
 * Adam: "SAFELY consolidate the code between these two right click menus to minimize duplication."
 *
 * Eight items were written out twice, once in {@link RightClickMenuListener} for a key-mapping button
 * and once in {@link RightClickSelectorMenu} for a tile in the locomotive database: set and clear the
 * local icon, customise function icons, edit the name and address, edit the notes, find similar
 * locomotives, and delete from the database. Two copies of a menu item is two places to fix a label
 * and one place to forget - the customise-icons item already differed between them, and nothing said
 * which was intended.
 *
 * **Why this is provably safe rather than merely tidy.** The two copies called the same methods with
 * different arities: `setLocIcon(loc)` in one and `setLocIcon(loc, evt)` in the other. Every one of
 * those one-argument forms is a delegate that passes null - `setLocIcon(l)` is `setLocIcon(l, null)`,
 * and so are changeLocAddress, changeLocNotes and deleteLoc. So a single item taking a nullable event
 * makes exactly the same call as whichever copy it replaces, and passing null is what the key-mapping
 * menu was already doing.
 *
 * **What is deliberately NOT consolidated: the order.** Each menu adds these where it wants them,
 * among its own items and its own separators. Merging the order would have changed both menus'
 * appearance to save nothing - the duplication was in the item bodies, not in the arrangement.
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
     * @param source the button the icons are being customised from, or null when there is none
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
     * How long this train is, or null when there is no reason to ask (FR-047).
     *
     * Adam: "visible only when autonomy is loaded."  The number decides one thing only - whether a
     * train is refused a platform too short for it - so on a layout with no autonomy setup it is a box
     * to fill in for no reason, and on one WITH a setup it is the other half of the warning FR-046
     * raises about a train that has no length.
     *
     * Null rather than a disabled item, because a greyed entry invites the question "why not", and the
     * answer - there is no autonomy here - is not something this menu can say in the space it has.
     *
     * @return the item, or null when autonomy is not loaded
     */
    static JMenuItem trainLength(TrainControlUI ui, Locomotive loc, MouseEvent evt)
    {
        if (ui == null || loc == null) return null;

        // LOADED, not merely available (Adam).  hasAutoLayout() is true whenever a graph exists, which
        // includes a layout whose setup nobody has loaded - so this appeared where there was nothing to
        // apply it to.
        if (!ui.isAutonomyLoaded()) return null;

        Integer length = loc.getTrainLength();

        JMenuItem item = new JMenuItem(I18n.f("loc.ui.menuAutonomyTrainLength",
            length == null || length <= 0
                ? I18n.t("loc.ui.trainLengthNotSet") : String.valueOf(length)));

        item.addActionListener(event -> ui.promptTrainLength(loc, evt));

        return item;
    }
}
