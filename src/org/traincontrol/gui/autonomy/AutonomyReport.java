package org.traincontrol.gui.autonomy;

import java.awt.Component;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.util.I18n;

/**
 * Shows what a save of the autonomy setup did, or says why it declined to do anything.  One display for six doors: five
 * of the six callers of `AutonomySession.save` threw the `Reconciliation` away, against its own javadoc: *"Nothing here
 * is acted on silently: the whole point is that a diagram changing under a setup should be visible."*  The refusal
 * matters more than the pruning: a save declines to reconcile while a page the setup knows about is not loaded (which
 * stops a OneDrive placeholder costing a page's settings), and putting the file back is the moment that fixes it.
 *
 * @author Adam
 */
public class AutonomyReport
{
    /**
     * The sets of missing pages already warned about in this session (MT-380).
     *
     * Keyed by the sorted page names, so the same refusal is recognized however the report lists them,
     * and a DIFFERENT set - another page gone, or one back - is a new thing to say.
     */
    private static final java.util.Set<String> ALREADY_SAID =
        java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    /**
     * Whether a declined save is worth a dialog: the first time a set of missing pages is reported in this session, not
     * again until the set changes (it *"should stop repeating"*).  Separated from the dialog so the rule can be tested.
     *
     * @return true when this refusal has not been shown before, and records that it now has been
     */
    public static boolean worthSaying(AutonomyCompanionStore.Reconciliation report)
    {
        if (report == null || !report.wasDeclined()) return false;

        List<String> pages = new java.util.ArrayList<>(report.getDeclinedBecauseAbsent());

        java.util.Collections.sort(pages);

        return ALREADY_SAID.add(String.join("\n", pages));
    }
    /**
     * Says what the save did, if anything worth saying.  Silent when the reconciliation was clean and not declined,
     * which is almost every save.
     *
     * @param report what {@code AutonomySession.save} returned; null is tolerated and says nothing
     */
    public static void show(Component owner, AutonomyCompanionStore.Reconciliation report)
    {
        // (The declined branch below asks `worthSaying` before it shows anything - MT-380.)

        if (report == null) return;

        // DECLINED comes first and is not the same message as "nothing changed": one is a layout that needed no
        // tidying, the other one that was refused permission to tidy, and only the second is worth interrupting
        // somebody for.
        if (report.wasDeclined())
        {
            // ONCE PER SET OF MISSING PAGES, NOT ON EVERY SAVE.  The refusal is still made every time (that protects a
            // page that has not downloaded); only the dialog is spared when it would say what it said last time.
            if (!worthSaying(report)) return;

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

        String text = describe(report);

        if (!text.isEmpty())
        {
            JOptionPane.showMessageDialog(owner, text,
                I18n.t("autosetup.ui.titleSetupTidied"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * What the dialog would say about a reconciliation, or an empty string for nothing worth saying.  Separated from
     * the dialog so it can be tested: `isClean()` counts three lists, and a text built from two showed nothing for a
     * save whose only casualty was track lengths and directions.
     *
     * @return the text, empty when there is nothing to say
     */
    public static String describe(AutonomyCompanionStore.Reconciliation report)
    {
        if (report == null) return "";

        // Say WHAT happened and WHY, not just a list of names (OB-052).  Adam: "I got a popup message with no context
        // and just a list of stations. unclear why."  The two lists mean opposite things (stations FORGOTTEN, stations
        // KEPT because something still refers to them), so they need headings.
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

        // AND THE THIRD LIST: `isClean()` counts three, the names forgotten, the names kept, and the LENGTHS AND
        // DIRECTIONS dropped because their tile is gone.  An edit whose only casualty was measurements must not build
        // an empty text and show nothing.
        if (!report.getDroppedTileProperties().isEmpty())
        {
            if (text.length() > 0) text.append("\n");

            text.append(I18n.t("autosetup.ui.infoTilePropertiesDropped")).append("\n\n");

            for (String dropped : report.getDroppedTileProperties())
            {
                text.append("    ").append(dropped).append("\n");
            }
        }

        return text.toString();
    }
}
