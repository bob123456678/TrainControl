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
     * why it is unavailable.  Only the cheap half of the question is asked here: whether a plan exists needs
     * a search, which would stall the popup, and the real answer comes when it is clicked.  The setup's
     * question is asked by the caller, once, from what Start's item already knows (ADU-C3).
     *
     * **AND IT IS THE BUTTON'S ANSWER, NOT THE RAILWAY'S (OB-192, second round).**  This used to ask
     * `Layout.triageReturnToHome` itself, and a popup menu is built on the event thread by definition:
     * that call builds a `HomeStaging.snapshot`, which calls `Layout.getHomeStations`, `synchronized`
     * on the `Layout`.  So right-clicking the diagram while anything held that monitor - a dispatch
     * inside `configureAndLockPath`, or `AutoLocomotiveStatus.findPaths` inside `getPossiblePaths`
     * with nothing running at all - froze the window instead of opening the menu.
     *
     * `refreshReturnHomeButton` is the one place that asks now, off the event thread, and this reads
     * the button it maintains, so the two describe the railway one way.  Over a setup with errors they part
     * on purpose, below: the item is greyed with the setup's sentence and the button stays live and explains
     * on a click, as Start's item and button do.
     *
     * Autonomy being busy is asked HERE, after the setup.  It costs no monitor - two flags and a
     * ConcurrentHashMap - and it can turn true between the last refresh and this menu opening, so asking it
     * makes the item strictly fresher than the button beside it.
     *
     * **AND A SETUP THAT CANNOT BE USED GREYS IT, with the setup's own sentence** (Adam, 2026-09-24,
     * TDU2-C3: *"Yes, go with your recommendation"*).  Return Home refuses to run over it (TDU-B1), and
     * this item is shown always and greyed where there is nothing it can do - so it says why here,
     * as the right-click Start beside it does.  The buttons stay live and explain on a click, as
     * Start's does.
     *
     * @param menu
     * @param ui
     * @param broken why the setup refuses a hand send - `whyAHandSendIsRefused` - or null where it does not
     */
    static void addReturnHomeItem(JComponent menu, TrainControlUI ui, String broken)
    {
        boolean offered = broken == null && !ui.isAutonomyBusy() && ui.isReturnHomeOffered();

        JMenuItem menuItem = new JMenuItem(I18n.t("autolayout.ui.menuReturnToHome"));

        menuItem.addActionListener(event -> ui.requestReturnToHome());
        menuItem.setEnabled(offered);

        if (!offered)
        {
            // WRAPPED, as Start's item beside it is (ADU-C4): the setup's sentence runs to two hundred characters.
            menuItem.setToolTipText(broken != null ? AutonomyEditorPanel.wrapped(broken) : ui.isAutonomyBusy()
                ? ui.describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null)
                : ui.whyReturnHomeIsNotOffered());
        }

        menu.add(menuItem);
    }
}
