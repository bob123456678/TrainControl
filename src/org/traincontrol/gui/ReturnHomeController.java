package org.traincontrol.gui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.base.Locomotive;
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
     * Sends every locomotive back to the station it started on.  Called from the button and from both right-click menus.  The
     * confirmation comes first and the planning second, deliberately: the question is about the timetable, which the user already knows
     * the state of.  Everything after the confirmation is off the EDT (planning enumerates routes and may search, and executeTimetable
     * blocks until the run finishes).
     */
    void requestReturnToHome()
    {
        // As Start Autonomy and Execute Timetable: this drives every train on the layout, and an open editor means the diagram it is driving
        // over is being changed underneath it.  Through the one gate: the editor, the setup (MT-263's rule) and the power, as every
        // door that moves trains.
        if (this.ui.refusedToSendATrain(false)) return;

        final Layout layout = this.ui.getModel().getAutoLayout();

        if (this.ui.isAutonomyBusy())
        {
            // Routed through describeStagingOutcome like every other surface, so one state is described one way.
            JOptionPane.showMessageDialog(this.ui,
                describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null));
            return;
        }

        // The cheap triage is not asked here (OB-192): this is the event thread, and `triageReturnToHome` takes the
        // `Layout` monitor (`HomeStaging.snapshot` calls the `synchronized` `Layout.getHomeStations`), so the press
        // froze the window while anything held it.  Nothing is lost: the timetable is borrowed and handed back, so no
        // confirmation is needed, and a trivial outcome still arrives through `plan()` and is reported below through
        // `describeStagingPlan`, off this thread.

        // Borrowed, not replaced: the staging plan occupies the timetable only while it runs and the original goes back afterwards (on
        // success, failure and graceful stop alike), so there is nothing to warn about.  An abrupt exit is safe by construction: the timetable
        // only reaches disk when the autonomy file is saved, so a plan that was never saved cannot outlive the session.
        final List<TimetablePath> borrowedTimetable = new ArrayList<>(layout.getTimetable());

        // Cleared per run, so a stop from an earlier one cannot excuse this one ending short
        this.ui.gracefulStopRequested = false;

        // Disabled here rather than once execution begins, as Execute Timetable does: the run is committed from this
        // point.  The flag makes that stick, since a repaint during planning would re-enable the button.
        this.ui.stagingFlowActive = true;

        // The same state on the Layout, so the guards that ask the model rather than this class can see
        // it too - including the sync deferral, which no UI predicate can reach
        layout.setStagingInProgress(true);
        this.ui.returnHomeButton().setEnabled(false);

        // A TURNING MARK ON THE BUTTON WHILE THE PLAN IS WORKED OUT (FR-077): the search can take seconds on a full
        // railway, and a grayed button with nothing moving reads as a click that did nothing.
        this.ui.showReturnHomeWorking(true);
        this.ui.executeTimetableButton().setEnabled(false);

        // And the capture toggle (OB-101): its handler refused, but refusing is not the same as not offering; a grayed
        // button says the railway is busy (as OB-094 and OB-100).
        this.ui.timetableCapture().setEnabled(false);

        // and the settings, which wait for the trains as these do
        this.ui.refreshWhatWaitsForTheTrains();

        // THE STOPS COUNTED AT THE PRESS: the plan runs with them, so the reload's Yes while it is worked out
        // - nothing yet moving - is answered by starting nothing, whether the load that asked it succeeds or not
        final int stopsAtPress = layout.stopsOrdered();

        new Thread(() ->
        {
            // Whether this flow was the one that disabled those two buttons, so the finally can put
            // back exactly what was taken and nothing else.  The impossible path never touches them.
            boolean tookTheButtons = false;

            try
            {
                // THE AUTO TAB STOPS OFFERING PATHS: its double-click sends nothing while this flow owns the railway,
                // so its lists are hidden, as while autonomy runs.
                this.ui.repaintAutoLocList(true);

                // Planned ONCE: planning twice gave two answers that can differ (getNeighbors shuffles, the state
                // moves), the second deciding whether the timetable was replaced and the first whether to run.
                HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

                // The plan is known, whatever it says - the run that follows has its own marks.
                javax.swing.SwingUtilities.invokeLater(() -> this.ui.showReturnHomeWorking(false));

                if (!plan.isPossible())
                {
                    final String message = describeStagingPlan(plan);

                    javax.swing.SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this.ui, message));

                    return;
                }

                // AND THE SAME QUESTION THE OTHER RUN DOORS ASK: several trains move at once here, so one with no train
                // length hands back the track under itself.  After every refusal, the plan's included: a refused press
                // should change nothing, and this writes the setting.  On the event thread, where the setting's
                // checkbox lives; a gate that fails stops the run, and the finally hands the timetable back.  AND NO
                // EDITOR COME SINCE THE PRESS, asked at the same event-thread moment as the gate
                final boolean[] editorCame = {false};

                try
                {
                    javax.swing.SwingUtilities.invokeAndWait(() ->
                    {
                        editorCame[0] = this.ui.refusedForAnEditorOnItsWay();

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

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    this.ui.timetableCapture().setSelected(layout.isTimetableCapture());
                    this.ui.startAutonomyButton().setEnabled(false);
                    this.ui.gracefulStopButton().setEnabled(true);
                });

                // So the operator sees what is about to run.  Called from this thread, not the block above:
                // repaintTimetable takes the Layout monitor before it marshals, and the first entry's
                // configureAndLockPath holds it for the length of its switch-throwing, so marshalled the wait froze the
                // UI.
                this.ui.repaintTimetable();

                // Blocks until every train has arrived, not merely until the last one set off
                final boolean completed = layout.executeTimetable(stopsAtPress);

                // Asked outright rather than trusting the boolean (a chain: every move completed or was abandoned, and
                // executePath completes only on arrival), which costs one occupancy read and does not decay.  A paused
                // train is left where it stands, so it does not make the run short (FR-117)
                HomeStaging.Outcome after = layout.triageReturnToHome();

                final boolean everyoneHome = after == HomeStaging.Outcome.ALREADY_HOME
                    || after == HomeStaging.Outcome.PAUSED_AWAY;

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    // A move that gave up stopped the whole run, and a log line is not something an operator watching
                    // the layout sees.  A graceful stop legitimately leaves trains short of home and returns true, so
                    // it is not reported.
                    if (!completed || (!everyoneHome && !this.ui.gracefulStopRequested))
                    {
                        JOptionPane.showMessageDialog(this.ui,
                            I18n.t("autolayout.ui.errorReturnToHomeStopped"));
                    }
                });
            }
            finally
            {
                // And here as well, for a plan that threw: the mark must never outlive the flow.
                javax.swing.SwingUtilities.invokeLater(() -> this.ui.showReturnHomeWorking(false));

                // Released before the buttons come back, so no surface can offer the action while this
                // worker is still unwinding
                this.ui.stagingFlowActive = false;

                layout.setStagingInProgress(false);

                // Hands the timetable back, in the finally so it survives every way out of the block.  setTimetable
                // also clears the sequential flag.
                layout.setTimetable(borrowedTimetable);

                // Re-enabled whether it ran, could not be planned, or threw: in a finally, because an exception out of
                // executeTimetable would leave Start Autonomy dead and Graceful Stop live.
                final boolean restoreButtons = tookTheButtons;

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    if (restoreButtons)
                    {
                        this.ui.startAutonomyButton().setEnabled(true);
                        this.ui.gracefulStopButton().setEnabled(false);
                    }

                    this.refreshReturnHomeButton();
                    this.ui.executeTimetableButton().setEnabled(true);

                    // In the finally with its siblings: a toggle that never comes back needs a
                    // restart to recover, which is worse than the state it was guarding against.
                    this.ui.timetableCapture().setEnabled(true);

                    this.ui.refreshWhatWaitsForTheTrains();
                });

                // Off the EDT as the pre-run repaint above: a path abandoned or graceful-stopped can still be
                // unwinding, so the monitor is not reliably free.
                this.ui.repaintTimetable();

                // The locomotive panel is repainted HERE, not by the end-of-path callback, which runs one step too
                // early (the last staging path fires it before stopLocomotives(), so isAutoRunning() is still true and
                // the paths list stays hidden).  Full rather than Lite: the panels must be rebuilt.
                this.ui.repaintAutoLocList(false);
            }
        }).start();
    }

    /**
     * Why a return home is not on offer.  IMPOSSIBLE names the locomotives, because that is actionable; NO_PLAN_FOUND
     * must not claim impossibility, as the search only ran out of room.
     */
    private String describeStagingPlan(HomeStaging.Plan plan)
    {
        return describeStagingOutcome(plan.getOutcome(), plan.getBlocked());
    }

    /**
     * Sets the return home button to match what the layout can actually do right now.  Called wherever the button would
     * otherwise simply be switched back on, and whenever the autonomy locomotive list is repainted.  Only the cheap
     * half of the question is asked: whether a plan exists needs a search.
     *
     * **AND THE CHEAP HALF IS STILL NOT ASKED HERE (OB-192).**  This runs on the event thread, and
     * `Layout.triageReturnToHome` builds a `HomeStaging.snapshot`, which calls the `synchronized`
     * `Layout.getHomeStations`; `isAutonomyBusy` does not cover it, as it says nothing about every OTHER holder of that
     * monitor, the nearest being `AutoLocomotiveStatus.findPaths` from the refresh itself (`refreshCoveredTrack` says
     * why the event thread must never queue for it).  So the question goes to a worker and the answer comes back to be
     * painted.
     *
     * @see #askForReturnHomeTriage(org.traincontrol.automation.Layout)
     */
    void refreshReturnHomeButton()
    {
        // repaintAutoLocList can run while the window is still being built, before the buttons exist
        if (this.ui.returnHomeButton() == null) return;

        // Asked, not built: the refresh a railway's last thread fires as it ends runs this, and after Unload `getAutoLayout` built an
        // empty railway which hasAutoLayout then answered yes about; the null branch below was written for exactly that state.
        Layout layout = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

        if (layout == null)
        {
            // Same as the other disableReturnHome sites: a bare setEnabled(false) leaves the tooltip from whatever this
            // button last said.
            this.ui.disableReturnHome(I18n.t("autosetup.ui.whyNoLayout"));
            return;
        }

        if (this.ui.isAutonomyBusy())
        {
            // Said out loud rather than left to the previous reason: a button grayed because trains are moving went on
            // claiming they were all at home.
            this.ui.disableReturnHome(describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null));
            return;
        }

        // BOTH ANSWERED HERE, on the event thread, because neither costs a monitor: `isAutonomyBusy`
        // reads two flags and a ConcurrentHashMap.  Only the third question goes away.
        askForReturnHomeTriage(layout);
    }

    /**
     * Puts one worker on "is there anything to send home", unless one is already on it, COALESCED (`CoalescedAsk`): the
     * refresh runs on every arrival and departure.  `railway` is captured on the caller's thread; the gap's re-ask
     * sends the subject again.
     */
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

    /**
     * One pass: asks the railway, off the event thread, and sends the answer back to be painted (see
     * `refreshReturnHomeButton`).
     */
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
            // The layout is being rebuilt underneath this pass; the next ask catches up.  Not
            // logged: this runs on every arrival, and a burst would fill the log with one
            // moment.
            return;
        }

        // DISCARDED IF THE RAILWAY MOVED ON: `parseAuto` replaces the Layout wholesale, and an answer about the
        // discarded graph must not paint the button.
        if (asked != this.ui.returnHomeTriageSubject) return;

        this.ui.returnHomeTriagePausedAway = pausedAway;
        this.ui.returnHomeTriage = answer;

        javax.swing.SwingUtilities.invokeLater(() -> paintReturnHomeFromTheAnswer(asked));
    }

    /**
     * Puts the worker's answer on the button, on the event thread.  THE GUARDS ARE ASKED AGAIN, because this runs a
     * beat after they were: a run can have started between the ask and the answer, and an answer computed against a
     * standing railway must not re-enable the button for one that is now moving.
     */
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

        // Says why it is unavailable, rather than leaving a dead button with no explanation
        // WRAPPED (MT-756): a reason can name many trains
        this.ui.returnHomeButton().setToolTipText(AutonomyEditorPanel.wrapped(nothingToDo == null
            ? I18n.t("ui.main.tooltip.returnHome")
            : describeStagingOutcome(nothingToDo, this.ui.returnHomeTriagePausedAway)));
    }

    /**
     * The triage's monitor: THIS window's own object, held only to set two fields, never the `Layout` monitor a dispatch
     * holds across a `CONFIGURE_SLEEP` per accessory of a path (OB-192), so the event thread may wait on it, bounded.
     */
    @Override
    Object settledLock()
    {
        return this.ui.returnHomeTriageSettled;
    }

    /** The triage is outstanding while a worker is on it or an ask has not been picked up. */
    @Override
    boolean outstanding()
    {
        return this.ui.returnHomeTriageInFlight.get() || this.ui.returnHomeTriageDirty.get();
    }

    @Override
    void giveUp()
    {
        this.ui.returnHomeTriageInFlight.set(false);
    }

    /**
     * Return Home's right-click menu, freshly built: Run Trains to Their Homes (the button itself, grayed with the
     * button's own reason), then Place All Trains at Their Homes, which puts each homed train on its home in the setup
     * and takes the others off the layout without sending anything to the railway (FR-129), grayed with its reason
     * while `whyTheTrainsCannotBePlacedAtTheirHomes` says no.
     */
    javax.swing.JPopupMenu buildReturnHomeMenu()
    {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        // The button's own state and tooltip, so the item can never offer what the button refuses (FR-129)
        javax.swing.JMenuItem run = new javax.swing.JMenuItem(I18n.t("autolayout.ui.menuRunTrainsToTheirHomes"));

        run.setEnabled(this.ui.isReturnHomeOffered());
        run.setToolTipText(this.ui.whyReturnHomeIsNotOffered());
        run.addActionListener(e -> requestReturnToHome());

        menu.add(run);

        String why = this.ui.whyTheTrainsCannotBePlacedAtTheirHomes();

        org.traincontrol.automationui.AutonomySession session = this.ui.canUseAutonomy() ? this.ui.getAutonomySession() : null;

        int homes = session == null ? 0 : session.homesToPlace().size();

        javax.swing.JMenuItem place = new javax.swing.JMenuItem(I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes));

        place.setEnabled(why == null);
        place.setToolTipText(AutonomyEditorPanel.wrapped(why == null
            ? I18n.f("autosetup.ui.tipPlaceEveryTrainAtHome", homes) : why));
        place.addActionListener(e -> this.ui.placeEveryTrainAtItsHome());

        menu.add(place);

        return menu;
    }

    /**
     * Shared by the cheap triage and the full plan, so the same situation cannot be described two
     * different ways depending on which one noticed it.
     *
     * @param outcome
     * @param blocked - may be null; only IMPOSSIBLE and POSITION_AMBIGUOUS carry any
     * @return
     */
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
                // The button is named by pulling its own label in, so the sentence cannot tell the user to press
                // something not on their screen.
                return I18n.f("autolayout.ui.errorReturnHomeTrainsMoving",
                    I18n.t("ui.main.gracefulStop"));

            case IMPOSSIBLE:
                return I18n.f("autolayout.ui.errorCannotReachHome", namesWithHomes(blocked));

            // Its own sentence, not the default one: a plan almost certainly exists and the obstacle is that nobody can
            // say where the train is.
            case POSITION_AMBIGUOUS:
                return I18n.f("autolayout.ui.errorLocomotivePositionAmbiguous", namesOf(blocked));

            default:
                return I18n.t("autolayout.ui.errorNoReturnPlanFound");
        }
    }

    /**
     * The blocked locomotives with the station each could not reach (OB-206), falling back to the locomotive alone
     * where the home cannot be named, as dropping the train would hide the very thing reported.
     */
    private String namesWithHomes(List<Locomotive> blocked)
    {
        java.util.Map<Locomotive, org.traincontrol.automation.Point> homes =
            this.ui.getModel() != null && this.ui.getModel().hasAutoLayout()
                ? this.ui.getModel().getAutoLayout().getHomeStations() : null;

        List<String> named = new ArrayList<>();

        for (Locomotive l : (blocked == null ? new ArrayList<Locomotive>() : blocked))
        {
            org.traincontrol.automation.Point home = homes == null ? null : homes.get(l);

            named.add(home == null ? l.getName()
                : I18n.f("autolayout.ui.locomotiveAndHome", l.getName(), stationName(home)));
        }

        return String.join(", ", named);
    }

    /**
     * What to call a home station in a sentence: the base name, not the copy's, as a station is several Points and
     * their names carry the arrival side ("BottomMainB (westbound, reverse)").
     */
    private String stationName(org.traincontrol.automation.Point home)
    {
        org.traincontrol.automationui.AutonomySession session = this.ui.getAutonomySession();

        return session == null || session.getStationIndex() == null
            ? home.getName() : session.getStationIndex().describe(home);
    }

    private String namesOf(List<Locomotive> blocked)
    {
        List<String> names = new ArrayList<>();

        for (Locomotive l : (blocked == null ? new ArrayList<Locomotive>() : blocked))
        {
            names.add(l.getName());
        }

        return String.join(", ", names);
    }
}
