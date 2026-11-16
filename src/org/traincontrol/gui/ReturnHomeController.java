package org.traincontrol.gui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * Return Home: the button's state, worked out off the event thread by the triage's ask, settle and await machine; its
 * right-click menu; the press, which plans and runs the trains home; and the sentence for each outcome.
 *
 * Every field, `stagingFlowActive` and the triage's among them, stays on `TrainControlUI`.
 */
final class ReturnHomeController extends SettledQuestion
{
    private final TrainControlUI ui;

    ReturnHomeController(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Sends every locomotive back to the station it started on, from the button and both right-click menus.  Planning
     * and the run are off the EDT.
     */
    void requestReturnToHome()
    {
        // As Start Autonomy: through the one gate (editor, setup per MT-263, power) every train-moving door uses
        if (this.ui.refusedToSendATrain(false)) return;

        // The window that pressed it, for what the worker below has to say
        final java.awt.Window asked = this.ui.dialogParent();

        final Layout layout = this.ui.getModel().getAutoLayout();

        if (this.ui.isAutonomyBusy())
        {
            JOptionPane.showMessageDialog(asked,
                describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null));
            return;
        }

        // The triage is not asked here (OB-192): the event thread must not take the `Layout` monitor.  A trivial
        // outcome arrives through `plan()` and is reported below, off this thread.

        // Borrowed, not replaced: the plan occupies the timetable only while it runs, and it goes back afterwards.
        final List<TimetablePath> borrowedTimetable = new ArrayList<>(layout.getTimetable());

        // Cleared per run, so a stop from an earlier one cannot excuse this one ending short
        this.ui.gracefulStopRequested = false;

        // Disabled now, not at execution start; the flag makes it stick against repaints during planning.
        this.ui.stagingFlowActive = true;

        // The same state on the Layout, for guards that ask the model (including the sync deferral)
        layout.setStagingInProgress(true);
        this.ui.returnHomeButton().setEnabled(false);

        // A turning mark on the button while the plan is worked out (FR-077): the search can take seconds
        this.ui.showReturnHomeWorking(true);
        this.ui.executeTimetableButton().setEnabled(false);

        // And the capture toggle (OB-101): grayed says the railway is busy
        this.ui.timetableCapture().setEnabled(false);

        // and the settings, which wait for the trains as these do
        this.ui.refreshWhatWaitsForTheTrains();

        // The stops counted at the press: the plan runs with them, so a reload's Yes during planning starts nothing
        final int stopsAtPress = layout.stopsOrdered();

        new Thread(() ->
        {
            // Whether this flow disabled those two buttons, so the finally restores exactly that
            boolean tookTheButtons = false;

            try
            {
                // The Auto tab stops offering paths while this flow owns the railway, as while autonomy runs
                this.ui.repaintAutoLocList(true);

                // Planned once: planning twice can give different answers (getNeighbors shuffles)
                HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

                // The plan is known, whatever it says - the run that follows has its own marks.
                SwingUtilities.invokeLater(() -> this.ui.showReturnHomeWorking(false));

                if (!plan.isPossible())
                {
                    final String message = describeStagingPlan(plan);

                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this.ui.stillShowing(asked), message));

                    return;
                }

                // The question the other run doors ask: a train with no length hands back the track under itself.  On
                // the event thread, where the setting's checkbox lives; a failing gate stops the run and the finally
                // hands the timetable back.  And no editor since the press, asked at the same moment.
                final boolean[] editorCame = {false};

                try
                {
                    SwingUtilities.invokeAndWait(() ->
                    {
                        editorCame[0] = this.ui.refusedForAnEditorOnItsWay(asked);

                        if (!editorCame[0]) this.ui.keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack();
                    });
                }
                catch (InterruptedException | java.lang.reflect.InvocationTargetException gate)
                {
                    this.ui.getModel().log(gate);

                    return;
                }

                if (editorCame[0]) return;

                tookTheButtons = true;

                SwingUtilities.invokeLater(() ->
                {
                    this.ui.timetableCapture().setSelected(layout.isTimetableCapture());
                    this.ui.startAutonomyButton().setEnabled(false);
                    this.ui.gracefulStopButton().setEnabled(true);
                });

                // So the operator sees what is about to run; from this thread, as repaintTimetable waits on the Layout
                // monitor
                this.ui.repaintTimetable();

                // Blocks until every train has arrived, not merely until the last one set off
                final boolean completed = layout.executeTimetable(stopsAtPress);

                // Asked outright rather than trusting the boolean: one occupancy read; a paused train is not short
                // (FR-117)
                HomeStaging.Outcome after = layout.triageReturnToHome();

                final boolean everyoneHome = after == HomeStaging.Outcome.ALREADY_HOME
                    || after == HomeStaging.Outcome.PAUSED_AWAY;

                SwingUtilities.invokeLater(() ->
                {
                    // A give-up stopped the whole run and a log line goes unseen; a graceful stop is not reported.
                    if (!completed || (!everyoneHome && !this.ui.gracefulStopRequested))
                    {
                        JOptionPane.showMessageDialog(this.ui.stillShowing(asked),
                            I18n.t("autolayout.ui.errorReturnToHomeStopped"));
                    }
                });
            }
            finally
            {
                // And here as well, for a plan that threw: the mark must never outlive the flow.
                SwingUtilities.invokeLater(() -> this.ui.showReturnHomeWorking(false));

                // Released before the buttons come back, so no surface offers the action meanwhile
                this.ui.stagingFlowActive = false;

                layout.setStagingInProgress(false);

                // Hands the timetable back, in the finally so it survives every way out; setTimetable clears the
                // sequential flag
                layout.setTimetable(borrowedTimetable);

                // Re-enabled in a finally, or an exception would leave Start Autonomy dead
                final boolean restoreButtons = tookTheButtons;

                SwingUtilities.invokeLater(() ->
                {
                    if (restoreButtons)
                    {
                        this.ui.startAutonomyButton().setEnabled(true);
                        this.ui.gracefulStopButton().setEnabled(false);
                    }

                    this.refreshReturnHomeButton();
                    this.ui.executeTimetableButton().setEnabled(true);

                    this.ui.timetableCapture().setEnabled(true);

                    this.ui.refreshWhatWaitsForTheTrains();
                });

                // Off the EDT as the pre-run repaint; a path still unwinding may hold the monitor
                this.ui.repaintTimetable();

                // Repainted here, not by the end-of-path callback, which runs before stopLocomotives(); full, to
                // rebuild panels
                this.ui.repaintAutoLocList(false);
            }
        }).start();
    }

    /**
     * Why a return home is not on offer.  IMPOSSIBLE names the locomotives; NO_PLAN_FOUND must not claim impossibility.
     */
    private String describeStagingPlan(HomeStaging.Plan plan)
    {
        return describeStagingOutcome(plan.getOutcome(), plan.getBlocked());
    }

    /**
     * Sets the return home button to match what the layout can do now.  Only the cheap half of the question is asked,
     * on a worker, not the event thread (OB-192).
     */
    void refreshReturnHomeButton()
    {
        // repaintAutoLocList can run while the window is still being built, before the buttons exist
        if (this.ui.returnHomeButton() == null) return;

        // Asked, not built: after Unload getAutoLayout built an empty railway that hasAutoLayout answered yes about
        Layout layout = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

        if (layout == null)
        {
            this.ui.disableReturnHome(I18n.t("autosetup.ui.whyNoLayout"));
            return;
        }

        if (this.ui.isAutonomyBusy())
        {
            // Said outright, not left to the previous reason
            this.ui.disableReturnHome(describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null));
            return;
        }

        // Both answered here, on the event thread, as neither costs a monitor; only the third question goes to a worker
        askForReturnHomeTriage(layout);
    }

    /** Puts one worker on "is there anything to send home" unless one is on it, coalesced (`CoalescedAsk`). */
    private void askForReturnHomeTriage(final Layout railway)
    {
        this.ui.returnHomeTriageSubject = railway;

        this.ui.returnHomeTriageDirty.set(true);

        CoalescedAsk.ask(this.ui.returnHomeTriageInFlight, this.ui.returnHomeTriageDirty, this.ui.ReturnHomeTriageRenderer,
            this::workOutReturnHomeTriage, this::settle, () ->
            {
                if (this.ui.returnHomeTriageSubject != null) askForReturnHomeTriage(this.ui.returnHomeTriageSubject);
            });
    }

    /** One pass: asks the railway off the event thread and sends the answer back to be painted. */
    private void workOutReturnHomeTriage()
    {
        Layout asked = this.ui.returnHomeTriageSubject;

        if (asked == null) return;

        HomeStaging.Outcome answer;
        List<Locomotive> pausedAway = new ArrayList<>();

        try
        {
            answer = asked.triageReturnToHome(pausedAway);
        }
        catch (RuntimeException replaced)
        {
            // The layout is being rebuilt; the next ask catches up.  Not logged: this runs on every arrival.
            return;
        }

        // Discarded if the railway moved on: parseAuto replaces the Layout wholesale
        if (asked != this.ui.returnHomeTriageSubject) return;

        this.ui.returnHomeTriagePausedAway = pausedAway;
        this.ui.returnHomeTriage = answer;

        SwingUtilities.invokeLater(() -> paintReturnHomeFromTheAnswer(asked));
    }

    /** Puts the worker's answer on the button, on the event thread, asking the guards again: a run may have started. */
    private void paintReturnHomeFromTheAnswer(Layout asked)
    {
        if (this.ui.returnHomeButton() == null) return;

        // Asked, not built
        if (this.ui.getModel() == null || this.ui.getModel().getAutoLayoutIfLoaded() != asked) return;

        if (this.ui.isAutonomyBusy())
        {
            this.ui.disableReturnHome(describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null));
            return;
        }

        HomeStaging.Outcome nothingToDo = this.ui.returnHomeTriage;

        this.ui.returnHomeButton().setEnabled(nothingToDo == null);

        // Says why it is unavailable; wrapped (MT-756), as a reason can name many trains
        this.ui.tipReturnHome(nothingToDo == null ? null
            : describeStagingOutcome(nothingToDo, this.ui.returnHomeTriagePausedAway));
    }

    /** The triage's monitor: this window's own object, never the `Layout` monitor a dispatch holds (OB-192). */
    @Override
    protected Object settledLock()
    {
        return this.ui.returnHomeTriageSettled;
    }

    /** The triage is outstanding while a worker is on it or an ask has not been picked up. */
    @Override
    protected boolean outstanding()
    {
        return this.ui.returnHomeTriageInFlight.get() || this.ui.returnHomeTriageDirty.get();
    }

    @Override
    protected void giveUp()
    {
        this.ui.returnHomeTriageInFlight.set(false);
    }

    /** Return Home's right-click menu: Run Trains to Their Homes, then Place All Trains at Their Homes (FR-129). */
    JPopupMenu buildReturnHomeMenu()
    {
        JPopupMenu menu = new JPopupMenu();

        // The button's own state and tooltip, so the item can never offer what the button refuses (FR-129)
        JMenuItem run = new JMenuItem(I18n.t("autolayout.ui.menuRunTrainsToTheirHomes"));

        TrainControlUI.showShortcut(run, "return locomotives home");
        run.setEnabled(this.ui.isReturnHomeOffered());
        run.setToolTipText(this.ui.whyReturnHomeIsNotOffered());
        run.addActionListener(e -> requestReturnToHome());

        menu.add(run);

        String why = this.ui.whyTheTrainsCannotBePlacedAtTheirHomes();

        AutonomySession session = this.ui.canUseAutonomy() ? this.ui.getAutonomySession() : null;

        int homes = session == null ? 0 : session.homesToPlace().size();

        JMenuItem place = new JMenuItem(I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes));

        place.setEnabled(why == null);
        place.setToolTipText(AutonomyEditorPanel.wrapped(why == null
            ? I18n.f("autosetup.ui.tipPlaceEveryTrainAtHome", homes) : why));
        place.addActionListener(e -> this.ui.placeEveryTrainAtItsHome());

        menu.add(place);

        return menu;
    }

    /** Shared by the triage and the full plan, so one situation is described one way.  `blocked` may be null. */
    String describeStagingOutcome(HomeStaging.Outcome outcome, List<Locomotive> blocked)
    {
        switch (outcome)
        {
            case ALREADY_HOME:
                return I18n.t("autolayout.ui.infoAllLocomotivesAlreadyHome");

            // The trains that are away and paused, named, and how to set them going (OB-355): not "already home"
            case PAUSED_AWAY:
                return I18n.f("autolayout.ui.infoAwayLocomotivesPaused", namesOf(blocked));

            case NO_LOCOMOTIVES:
                return I18n.t("autolayout.ui.errorNoLocomotivesOnGraph");

            case NO_HOMES:
                return I18n.t("autolayout.ui.errorNoHomeStations");

            case LOCOMOTIVES_RUNNING:
                // The button is named by pulling its own label in
                return I18n.f("autolayout.ui.errorReturnHomeTrainsMoving",
                    I18n.t("ui.main.gracefulStop"));

            case IMPOSSIBLE:
                return I18n.f("autolayout.ui.errorCannotReachHome", namesWithHomes(blocked));

            // Its own sentence: a plan almost certainly exists but nobody can say where the train is
            case POSITION_AMBIGUOUS:
                return I18n.f("autolayout.ui.errorLocomotivePositionAmbiguous", namesOf(blocked));

            default:
                return I18n.t("autolayout.ui.errorNoReturnPlanFound");
        }
    }

    /**
     * The blocked locomotives with the station each could not reach (OB-206), or the locomotive alone when no home is
     * named.
     */
    private String namesWithHomes(List<Locomotive> blocked)
    {
        java.util.Map<Locomotive, Point> homes =
            this.ui.getModel() != null && this.ui.getModel().hasAutoLayout()
                ? this.ui.getModel().getAutoLayout().getHomeStations() : null;

        List<String> named = new ArrayList<>();

        for (Locomotive l : (blocked == null ? new ArrayList<Locomotive>() : blocked))
        {
            Point home = homes == null ? null : homes.get(l);

            named.add(home == null ? l.getName()
                : I18n.f("autolayout.ui.locomotiveAndHome", l.getName(), stationName(home)));
        }

        return String.join(", ", named);
    }

    /** What to call a home station in a sentence: the base name, as the copy's name carries the arrival side. */
    private String stationName(Point home)
    {
        AutonomySession session = this.ui.getAutonomySession();

        return session == null || session.getStationIndex() == null
            ? home.getName() : session.getStationIndex().describe(home);
    }

    private String namesOf(List<Locomotive> blocked)
    {
        List<String> names = new ArrayList<>();

        for (Locomotive l : (blocked == null ? new ArrayList<Locomotive>() : blocked)) names.add(l.getName());

        return String.join(", ", names);
    }
}
