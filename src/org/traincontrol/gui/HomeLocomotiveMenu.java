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
     * Autonomy being busy counts as a reason of its own, and has to be asked before the triage rather
     * than instead of it - the triage knows nothing about it, so asking only the triage offered an
     * action the flow would then refuse, computed against positions that were changing underneath.
     *
     * @param menu
     * @param ui
     */
    static void addReturnHomeItem(JComponent menu, TrainControlUI ui)
    {
        HomeStaging.Outcome nothingToDo = ui.isAutonomyBusy()
            ? HomeStaging.Outcome.LOCOMOTIVES_RUNNING
            : ui.getModel().getAutoLayout().triageReturnToHome();

        JMenuItem menuItem = new JMenuItem(I18n.t("autolayout.ui.menuReturnToHome"));

        menuItem.addActionListener(event -> ui.requestReturnToHome());
        menuItem.setEnabled(nothingToDo == null);

        if (nothingToDo != null)
        {
            menuItem.setToolTipText(ui.describeStagingOutcome(nothingToDo, null));
        }

        menu.add(menuItem);
    }
}
