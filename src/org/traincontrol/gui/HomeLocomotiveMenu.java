package org.traincontrol.gui;

import javax.swing.JMenuItem;
import javax.swing.JComponent;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.util.I18n;

/**
 * The "Return Locomotives Home" menu item.
 *
 * UXR-C19: this class used to hold the whole home-locomotive editor - assigning and clearing a
 * single station's home, the "clear every home" bulk action, and the exclusion-conflict warning -
 * reached from three right-click menus. The editor moved to `AutonomyEditorPanel` (see
 * `homeChoices`, tested directly by `test/regression/testHomeAssignmentRules.java`), and every one
 * of `addStationItem`, `addClearAllItem`, `editHomeLocomotive`, `confirmExclusion`, `apply`,
 * `refuseWhileBusy` and `shortName` was left with no caller anywhere in `src/` or `test/` - verified
 * by grep, not assumed. Removed rather than left behind: a method with no caller is the half of a
 * removal that gets forgotten, and the next person to read this file would have had to work out
 * whether the by-name `setSelectedItem` fix and the `whyNotAHome` split in the deleted half were
 * still wanted. Only `addReturnHomeItem` survives, with its one caller in
 * `LayoutRightclickAutonomyMenu`.
 *
 * @author Adam
 */
final class HomeLocomotiveMenu
{
    private HomeLocomotiveMenu()
    {
    }

    /**
     * Adds the item that sends every locomotive back where it belongs.
     *
     * Shown always and greyed when there is nothing to do, so the feature stays discoverable and says
     * why it is unavailable.  Only the cheap half of the question is asked: whether a plan exists needs
     * a search, which would stall the popup, and the real answer comes when it is clicked.
     *
     * **AND IT IS THE BUTTON'S ANSWER, NOT THE RAILWAY'S (OB-192, second round).**  This used to ask
     * `Layout.triageReturnToHome` itself, and a popup menu is built on the event thread by definition:
     * that call builds a `HomeStaging.snapshot`, which calls `Layout.getHomeStations`, `synchronized`
     * on the `Layout`.  So right-clicking the diagram while anything held that monitor - a dispatch
     * inside `configureAndLockPath`, or `AutoLocomotiveStatus.findPaths` inside `getPossiblePaths`
     * with nothing running at all - froze the window instead of opening the menu.
     *
     * `refreshReturnHomeButton` is the one place that asks now, off the event thread, and this reads
     * the button it maintains.  That also settles by construction what this method's own guard was
     * arranged to approximate: the item and the button cannot describe one situation two ways, because
     * there is only one description.
     *
     * Autonomy being busy is still asked HERE and first, exactly as before.  It costs no monitor - two
     * flags and a ConcurrentHashMap - and it can turn true between the last refresh and this menu
     * opening, so asking it makes the item strictly fresher than the button beside it.
     *
     * @param menu
     * @param ui
     */
    static void addReturnHomeItem(JComponent menu, TrainControlUI ui)
    {
        boolean offered = !ui.isAutonomyBusy() && ui.isReturnHomeOffered();

        JMenuItem menuItem = new JMenuItem(I18n.t("autolayout.ui.menuReturnToHome"));

        menuItem.addActionListener(event -> ui.requestReturnToHome());
        menuItem.setEnabled(offered);

        if (!offered)
        {
            menuItem.setToolTipText(ui.isAutonomyBusy()
                ? ui.describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null)
                : ui.whyReturnHomeIsNotOffered());
        }

        menu.add(menuItem);
    }
}
