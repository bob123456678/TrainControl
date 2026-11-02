package org.traincontrol.gui;

import javax.swing.JMenuItem;
import javax.swing.JComponent;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
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
     * Adds the item that sends every locomotive back where it belongs (OB-192).
     *
     * Shown always, grayed when there is nothing to do or the setup cannot be used, with the setup's own sentence.
     * It reads the button `refreshReturnHomeButton` maintains and never asks `Layout.triageReturnToHome`: a popup is
     * built on the event thread, and that call takes the Layout monitor.
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
