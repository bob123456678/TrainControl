package org.traincontrol.gui;

import java.awt.Component;
import javax.swing.JOptionPane;
import org.traincontrol.util.I18n;

/**
 * The refusal gates: whether a door may send a train, edit a locomotive or the setup, or act while an editor holds the
 * diagram, and the sentence each refusal shows.  Static, over the window, whose doors keep a delegator each; every
 * field stays on `TrainControlUI`.
 */
final class RunGates
{
    private RunGates()
    {
    }

    /**
     * Refuses to edit or delete a locomotive while a route that drives it is running, and says which route (autonomy's
     * refusal does not cover it, as a route runs by hand with autonomy idle).  Package-private for `LocomotiveActions`'
     * edit and delete doors; event thread only, as it shows a dialog.  True when the caller must not proceed.
     */
    static boolean refuseWhileARouteDrivesIt(TrainControlUI ui, Component source, String name)
    {
        if (ui.getModel() == null) return false;

        org.traincontrol.base.Route running = ui.getModel().runningRouteDriving(name);

        if (running == null) return false;

        JOptionPane.showMessageDialog(source == null ? ui : source,
            I18n.f("loc.ui.errorLocomotiveDrivenByARunningRoute", name, running.getName()));

        return true;
    }
    /**
     * `refuseWhileARouteDrivesIt` of each of these locomotives in turn: the first a running route drives is refused
     * (OB-287).
     */
    static boolean refuseWhileARouteDrivesAny(TrainControlUI ui, Component source, java.util.Collection<String> names)
    {
        for (String name : names)
        {
            if (refuseWhileARouteDrivesIt(ui, source, name)) return true;
        }

        return false;
    }

    /**
     * Refuses to change the locomotive database while autonomy is running (Adam, MT-141: "Never allow any modifications
     * to a running layout.  This includes locomotive database, the track diagram, the autonomy config, or the
     * locomotive placements."), collected here so that a fifth door is one call.  True when the caller must not
     * proceed.
     */

    static boolean refuseWhileAutonomyRunning(TrainControlUI ui, Component source)
    {
        return refuseWhile(ui, source, false);
    }

    /**
     * The same for a door that edits the setup or the diagram, which a route running also holds (`aRunHoldsTheSetup`):
     * the same sentence while autonomy runs, one naming a route otherwise (OB-287).
     */
    static boolean refuseWhileARunHoldsTheSetup(TrainControlUI ui, Component source)
    {
        return refuseWhile(ui, source, true);
    }

    /** `refuseWhileAutonomyRunning`, and with `aRoute` `refuseWhileARunHoldsTheSetup`. */
    private static boolean refuseWhile(TrainControlUI ui, Component source, boolean aRoute)
    {
        if (aRoute && (ui.getModel() == null || !ui.getModel().isAutonomyRunning()) && ui.aRunHoldsTheSetup())
        {
            JOptionPane.showMessageDialog(source == null || !source.isShowing() ? ui : source,
                I18n.t("ui.main.tooltip.placeAtHomesWhileRunning"));

            return true;
        }

        if (ui.getModel() == null || !ui.getModel().isAutonomyRunning()) return false;

        // OVER THE MAIN WINDOW where the source is not showing (the popups sweep, on Adam's MT-703 note): a menu's item
        // has left its window by the time it runs, and the message went to Swing's hidden frame - under an
        // always-on-top main window
        JOptionPane.showMessageDialog(source == null || !source.isShowing() ? ui : source,
            I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning"));

        return true;
    }

    /**
     * Refuses an autonomy action while an editor holds the diagram, and says why: the banner is one button drawn before
     * the state it guards against, and loading would save the setup against a diagram somebody is editing (discarding
     * tiles they meant to Cancel) and rebuild the window under the open editor.  True when the caller must not proceed.
     */
    static boolean refuseWhileEditorOpen(TrainControlUI ui)
    {
        if (!ui.isLayoutEditorOpen()) return false;

        // The dialog goes to the event thread; the ANSWER does not wait for it (OB-078): two callers ask this from a raw worker thread
        // (editRoute and the route-duplicate loop), and a modal JOptionPane built off the EDT mispaints and can deadlock.
        // invokeLater rather than invokeAndWait: the caller has already decided to refuse, and blocking until a dialog is dismissed
        // would hold a worker and, from the EDT, deadlock outright.
        if (javax.swing.SwingUtilities.isEventDispatchThread())
        {
            JOptionPane.showMessageDialog(ui, I18n.t("autosetup.ui.menuEditorOpen"));
        }
        else
        {
            javax.swing.SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(ui, I18n.t("autosetup.ui.menuEditorOpen")));
        }

        return true;
    }

    /**
     * What stops a train being sent now, or null: the one question every door that sends a train asks (the Auto tab,
     * the diagram's destinations, Start, Execute Timetable and Return Home; Adam: "can all the checks go through a
     * single door?").  In order: an open editor holds the diagram, and no setup edit is made during a run (OB-047,
     * MT-135); no route running; a setup with errors sends nothing (MT-263); the power.  What a door asks besides is
     * its own (`whyThisPathCannotBeTaken` for a hand send's path).  `startingAutonomy`: the door is Start, which words
     * a broken setup its own way.
     */
    static String whyNoTrainMayBeSent(TrainControlUI ui, boolean startingAutonomy)
    {
        // OR ON ITS WAY: the editor is built in a posted task, and a send that ran in between passed this
        if (ui.anEditorIsOpenOrOnItsWay()) return I18n.t("autosetup.ui.menuEditorOpen");

        // NOR WHILE THE DIAGRAM RELOADS AFTER A PAGE DOOR: the reset at its end forgot a run started meanwhile
        if (ui.refreshesUnderWay.get() > 0) return I18n.t("autosetup.ui.errorDiagramRefreshing");

        // NOR WHILE A ROUTE RUNS: a run must not begin under one, or a turn held for the route (`turnsHeldByARoute`) would
        // meet a run that began before it ended.  Adam: *"prevent starting autonomy etc if a route is still running"*
        if (TrainControlUI.aRouteIsExecuting(ui.getModel())) return TrainControlUI.routeRunningRefusal(
            TrainControlUI.executingRouteNames(ui.getModel()));

        String broken = startingAutonomy ? ui.whyAutonomyStartIsRefused() : ui.whyAHandSendIsRefused();

        if (broken != null) return broken;

        if (ui.getModel() == null || !ui.getModel().getPowerState()) return I18n.t("autolayout.ui.powerOnToStart");

        return null;
    }

    /**
     * Why a train may not be sent by hand right now, or null when it may (MT-263; Adam: "Start autonomy doesn't run autonomy, as
     * expected - I get an error message saying errors must first be fixed.  Good.  But trains can still be moved manually via both the
     * track diagram viewer and the autonomy tab, which should throw an error instead.").  A hand send runs over the railway the same
     * setup built, so it is refused on the question Start is refused on - `autonomyHasErrors`.  Both hand doors ask it before the
     * reversal question, so nothing is asked about a journey that is going to be refused; Execute Timetable and Return Home ask it too
     * - named for the hand doors, where it was first asked.
     *
     * @return the sentence to show, or null
     */
    static String whyAHandSendIsRefused(TrainControlUI ui)
    {
        if (!ui.autonomyHasErrors()) return null;

        org.traincontrol.automationui.AutonomySession asked = ui.getAutonomySession();

        return whyAHandSendIsRefused(ui.autonomyErrorCount(), asked == null ? 0 : asked.blockingProblemCount());
    }

    /**
     * The words for a refused hand send: the setup's own - *"This setup cannot be used yet"*, with however many things
     * there are to deal with - and never Start's, which say AUTONOMY cannot start.  Through the Start rule rather than
     * beside it, so each sentence is still chosen in one place: the error findings where there are any, since those are
     * what the editor lists, and otherwise the problems that stop the setup being built (`errors` and `blocking` are
     * the counts).  Returns the sentence, already translated.
     */
    static String whyAHandSendIsRefused(int errors, int blocking)
    {
        return TrainControlUI.whyAutonomyWillNotStart(0, errors > 0 ? errors : blocking, true);
    }

    /**
     * What a door refusing a setup edit says: its own sentence (`autonomyKey`) where autonomy is busy, and one naming a
     * route too where something else holds the setup (`aRunHoldsTheSetup`).
     */
    static String whyTheSetupIsHeld(TrainControlUI ui, String autonomyKey)
    {
        return I18n.t(ui.isAutonomyBusy() ? autonomyKey : "ui.main.tooltip.placeAtHomesWhileRunning");
    }
}
