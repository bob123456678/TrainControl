package org.traincontrol.gui;

import java.awt.Component;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.util.I18n;

/**
 * Shows what a save of the autonomy setup did, or says why it declined to do anything.
 *
 * **One display, for six doors (DR-B10).** `AutonomySession.save` returns a {@code Reconciliation}
 * describing what it pruned, and five of the six places that call it threw the answer away - the
 * autonomy menu, the viewer panel, the diagram's right-click menu, and two places in the main window.
 * Only the editor showed it. The `Reconciliation` class's own javadoc states the principle that was
 * being broken: *"Nothing here is acted on silently: the whole point is that a diagram changing under
 * a setup should be visible."*
 *
 * The refusal matters more than the pruning, and it had no display at all. A save declines to
 * reconcile while a page the setup knows about is not loaded - which is the right thing to do, and is
 * what stops a OneDrive placeholder costing somebody a page's settings - but nothing said so. Meanwhile
 * the next page operation retires that page's id in the background. The one moment when putting the
 * file back would fix everything passed in silence.
 *
 * @author Adam
 */
public class AutonomyReport
{
    /**
     * Says what the save did, if anything worth saying.
     *
     * Silent when the reconciliation was clean and not declined, which is almost every save - this
     * must not become a dialog that appears every time somebody presses Save.
     *
     * @param owner the window to hang the dialog from
     * @param report what {@code AutonomySession.save} returned; null is tolerated and says nothing
     */
    public static void show(Component owner, AutonomyCompanionStore.Reconciliation report)
    {
        if (report == null) return;

        // DECLINED comes first, and is not the same message as "nothing changed".
        //
        // A caller holding an empty Reconciliation could not previously tell a layout that needed no
        // tidying from one it was refused permission to tidy. Those are opposite situations, and only
        // one of them is worth interrupting somebody for.
        if (report.wasDeclined())
        {
            StringBuilder names = new StringBuilder();

            for (String page : report.getDeclinedBecauseAbsent())
            {
                names.append("\n    ").append(page);
            }

            JOptionPane.showMessageDialog(owner,
                I18n.f("autosetup.ui.infoSetupNotTidied", names.toString()),
                I18n.t("autosetup.ui.titleSetupNotTidied"), JOptionPane.WARNING_MESSAGE);

            return;
        }

        if (report.isClean()) return;

        // Say WHAT happened and WHY, not just a list of names (OB-052).
        //
        // This showed the names alone, with no title and no sentence - Adam: "I got a popup message
        // with no context and just a list of stations. unclear why." Worse, the two lists mean
        // opposite things: one names stations that have been FORGOTTEN, the other names stations that
        // have been KEPT because something still refers to them. Run together with no headings, the
        // reader cannot tell which of their stations they have just lost.
        StringBuilder text = new StringBuilder();

        if (!report.getForgottenNames().isEmpty())
        {
            text.append(I18n.t("autosetup.ui.infoNamesForgotten")).append("\n\n");

            for (String forgotten : report.getForgottenNames())
            {
                text.append("    ").append(forgotten).append("\n");
            }
        }

        if (!report.getNamesStillReferenced().isEmpty())
        {
            if (text.length() > 0) text.append("\n");

            text.append(I18n.t("autosetup.ui.infoNamesStillReferenced")).append("\n\n");

            for (Map.Entry<String, List<String>> entry : report.getNamesStillReferenced().entrySet())
            {
                text.append("    ").append(entry.getKey())
                    .append(" - ").append(entry.getValue()).append("\n");
            }
        }

        if (text.length() > 0)
        {
            JOptionPane.showMessageDialog(owner, text.toString(),
                I18n.t("autosetup.ui.titleSetupTidied"), JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
