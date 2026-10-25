package org.traincontrol.gui;

import javax.swing.JMenuItem;
import javax.swing.JComponent;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.util.I18n;

/**
 * The "Return Locomotives Home" menu item.
 *
 * The editor for assigning and clearing homes lives in `AutonomyEditorPanel` (see `homeChoices`, tested by
 * `test/regression/testHomeAssignmentRules.java`); only `addReturnHomeItem` is here, with its one caller in
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
     * Shown always and grayed when there is nothing to do, so the feature stays discoverable and says why it is
     * unavailable.  Only the cheap half of the question is asked here: whether a plan exists needs a search, which
     * would stall the popup, and the real answer comes when it is clicked.  The setup's question is asked by the
     * caller, once, from what Start's item already knows.
     *
     * **IT IS THE BUTTON'S ANSWER, NOT THE RAILWAY'S (OB-192).**  A popup menu is built on the event thread, and
     * `Layout.triageReturnToHome` builds a `HomeStaging.snapshot` that calls `Layout.getHomeStations`, `synchronized`
     * on the `Layout`, so right-clicking the diagram while anything held that monitor froze the window.
     * `refreshReturnHomeButton` is the one place that asks, off the event thread, and this reads the button it
     * maintains so the two describe the railway one way.  Over a setup with errors they part on purpose, below: the
     * item is grayed with the setup's sentence and the button stays live and explains on a click, as Start's does.
     *
     * Autonomy being busy is asked HERE, after the setup: it costs no monitor (two flags and a ConcurrentHashMap) and
     * can turn true between the last refresh and this menu opening.
     *
     * **A setup that cannot be used grays it, with the setup's own sentence**, as the right-click Start beside it does:
     * Return Home refuses to run over it, and this item says why.
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
            // WRAPPED, as Start's item beside it is: the setup's sentence runs to two hundred characters.
            menuItem.setToolTipText(broken != null ? AutonomyEditorPanel.wrapped(broken) : ui.isAutonomyBusy()
                ? ui.describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null)
                : ui.whyReturnHomeIsNotOffered());
        }

        menu.add(menuItem);
    }
}
