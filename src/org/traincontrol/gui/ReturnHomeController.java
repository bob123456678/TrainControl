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
final class ReturnHomeController
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
            // Routed through describeStagingOutcome like every other surface.  This one said "please
            // wait for all active locomotives to stop" while the button tooltip and the plan-refusal
            // dialog described the same state differently - which is the exact thing that method
            // exists to prevent.
            JOptionPane.showMessageDialog(this.ui,
                describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null));
            return;
        }

        // The cheap triage is not asked here (OB-192): this is the event thread, and `triageReturnToHome` builds a
        // `HomeStaging.snapshot`, which calls `Layout.getHomeStations`, `synchronized` on the `Layout`, so pressing
        // this button while anything held that monitor froze the window (`refreshReturnHomeButton` says why that
        // monitor is not the event thread's to wait for).  Nothing is lost: the timetable is borrowed and handed back
        // rather than replaced, so no confirmation is needed, and the trivial outcome still arrives through `plan()` ->
        // `triage()` and is reported by the block below through `describeStagingPlan`, off this thread.  The button was
        // enabled by that very answer, so reaching here means the railway had work to do the last time anybody asked.

        // Borrowed, not replaced: the staging plan occupies the timetable only while it runs and the original goes back afterwards (on
        // success, failure and graceful stop alike), so there is nothing to warn about.  An abrupt exit is safe by construction: the timetable
        // only reaches disk when the autonomy file is saved, so a plan that was never saved cannot outlive the session.
        final List<TimetablePath> borrowedTimetable = new ArrayList<>(layout.getTimetable());

        // Cleared per run, so a stop from an earlier one cannot excuse this one ending short
        this.ui.gracefulStopRequested = false;

        // Disabled here rather than once execution begins, matching what pressing Execute Timetable
        // does: the run is committed from this point, and both buttons would start the same thing.
        // The flag is what makes that stick - a repaint or a menu opening during planning would
        // otherwise re-enable the button, because nothing is running yet.
        this.ui.stagingFlowActive = true;

        // The same state on the Layout, so the guards that ask the model rather than this class can see
        // it too - including the sync deferral, which no UI predicate can reach
        layout.setStagingInProgress(true);
        this.ui.returnHomeButton().setEnabled(false);

        // A TURNING MARK ON THE BUTTON WHILE THE PLAN IS WORKED OUT (Adam, FR-077: "there needs to be a
        // spinner on the return home button while it is calculating").  The search can take seconds on
        // a full railway, and a greyed button with nothing moving reads as a click that did nothing.
        this.ui.showReturnHomeWorking(true);
        this.ui.executeTimetableButton().setEnabled(false);

        // And the capture toggle (OB-101; Adam: "capture locomotive commands button can still be toggled while trains are returning home."):
        // its handler did refuse (isAutonomyBusy covers this flow), but refusing is not the same as not offering; a greyed button says the
        // railway is busy, which is true (the same distinction as OB-094 and OB-100: make the affordance agree with the guard).
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
                // THE AUTO TAB STOPS OFFERING PATHS: its double-click sends nothing while this flow owns the
                // railway, so its lists are hidden, as they are while autonomy runs - refreshed from this worker, as the
                // finally refreshes it
                this.ui.repaintAutoLocList(true);

                // Planned ONCE: asking planReturnToHome and then letting loadReturnToHomeTimetable plan again meant two answers that can differ
                // (getNeighbors shuffles, and the state can move underneath), the second deciding whether the timetable was replaced and the first
                // whether to run, which executed the operator's own timetable because "Return Home" had been pressed.
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

                // AND THE SAME QUESTION THE OTHER RUN DOORS ASK.  This is the run where it costs most: several trains
                // move at once, so one with no train length hands back the track under itself while the others are
                // being routed around it.  After every refusal, the plan's included: a refused press should change
                // nothing, and this writes the setting, which the planner does not read.  On the event thread, where
                // the setting's checkbox lives; a gate that fails stops the run, and the finally hands the timetable
                // back.
                // AND NO EDITOR COME SINCE THE PRESS, asked at the same event-thread moment as the gate
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

                // So the operator can see what is about to run rather than watching the old timetable while different trains move.  Called from this
                // thread, not the block above: repaintTimetable takes the Layout monitor before it marshals, and the first entry's
                // configureAndLockPath is about to hold that monitor for the length of its switch-throwing, so marshalled the wait landed on the EDT
                // and froze the UI.
                this.ui.repaintTimetable();

                // Blocks until every train has arrived, not merely until the last one set off
                final boolean completed = layout.executeTimetable(stopsAtPress);

                // Trusting the boolean means trusting a chain: every move completed or was abandoned,
                // and executePath completes only on arrival.  That holds today.  Asking outright costs
                // one occupancy read and does not decay when a link in the chain changes.
                // A paused train is left where it stands, so it does not make the run short (FR-117)
                HomeStaging.Outcome after = layout.triageReturnToHome();

                final boolean everyoneHome = after == HomeStaging.Outcome.ALREADY_HOME
                    || after == HomeStaging.Outcome.PAUSED_AWAY;

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    // A move that gave up stopped the whole run.  It is logged, but a log line is not
                    // something an operator watching the layout will see - and what they are looking at
                    // is trains that stopped halfway with no explanation.
                    // A graceful stop legitimately leaves trains short of home and returns true, so it
                    // is not reported - the operator asked for it and watched it happen.
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

                // Hands the timetable back.  In the finally so it survives every way out of the block:
                // the plan being impossible, the run finishing, a move giving up, a graceful stop, or
                // an exception.  setTimetable also clears the sequential flag, so normal execution
                // semantics return with it.
                layout.setTimetable(borrowedTimetable);

                // Re-enabled whether it ran, could not be planned, or threw.  In a finally because a
                // button that never comes back needs a restart to recover - and these two must not be
                // reset only on the normal path, or an exception out of executeTimetable leaves Start
                // Autonomy dead and Graceful Stop live, for a run that is no longer happening.
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

                // Off the EDT for the same reason as the pre-run repaint above: a path abandoned or
                // graceful-stopped can still be unwinding when this runs, so the monitor is not
                // reliably free here either
                this.ui.repaintTimetable();

                // The locomotive panel has to be repainted HERE, not by the end-of-path callback that normally maintains it, because that callback
                // runs one step too early: the last staging path fires it from executePathInternal and only afterwards does the entry thread call
                // stopLocomotives(), so the final repaint always sees isAutoRunning() still true and hides the available-paths list until autonomy
                // is started and gracefully stopped.  Full rather than Lite: the panels must be rebuilt, not just their route labels refreshed.
                this.ui.repaintAutoLocList(false);
            }
        }).start();
    }

    /**
     * Why a return home is not on offer.
     *
     * IMPOSSIBLE names the locomotives, because that is actionable.  NO_PLAN_FOUND must not claim
     * impossibility - the search only ran out of room, which is a weaker statement.
     */
    private String describeStagingPlan(HomeStaging.Plan plan)
    {
        return describeStagingOutcome(plan.getOutcome(), plan.getBlocked());
    }

    /**
     * Sets the return home button to match what the layout can actually do right now.  Called wherever the button would otherwise simply
     * be switched back on, and whenever the autonomy locomotive list is repainted (what runs when a locomotive is placed or arrives,
     * exactly when "is anything away from home" changes its answer).  Only the cheap half of the question is asked: whether a plan
     * exists needs a search.
     *
     * **AND THE CHEAP HALF IS STILL NOT ASKED HERE (OB-192).**  This runs on the event thread (`repaintAutoLocListLite` and
     * `repaintAutoLocListFull` call it from their `invokeLater`), and `Layout.triageReturnToHome` builds a `HomeStaging.snapshot`, which
     * calls `Layout.getHomeStations`, `synchronized` on the `Layout`: the same monitor, thread and freeze as the covered marks
     * (`refreshCoveredTrack` says why the event thread must never queue for it).  **`isAutonomyBusy` DOES NOT COVER IT**: it asks
     * `Layout.isRunning`, which counts `locomotiveThreads` and so is true for a hand dispatch as well as autonomy, but says nothing about
     * every OTHER holder of that monitor with no train moving at all, the nearest being this very refresh (`repaintAutoLocListLite`
     * submits `AutoLocomotiveStatus.findPaths` to `AutonomyRenderer`, which calls the `synchronized` `getPossiblePaths`, a search of the
     * whole graph, once per panel).  Measured: the reproduction holds the monitor with nothing running and the event thread parks at
     * `Layout.getHomeStations`.  So the question goes to a worker and the answer comes back to be painted; what the button says does not
     * change, only where it is worked out.
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
            // Same reasoning as the other disableReturnHome sites - a bare setEnabled(false)
            // here leaves the tooltip from whatever this button last had to say, which is nonsense once
            // there is no autonomy layout behind it at all.
            this.ui.disableReturnHome(I18n.t("autosetup.ui.whyNoLayout"));
            return;
        }

        if (this.ui.isAutonomyBusy())
        {
            // Said out loud rather than left to the previous reason.  Disabling without touching the
            // tooltip meant a button greyed because trains are moving went on claiming they were all
            // at home - the explanation from before the run started.
            this.ui.disableReturnHome(describeStagingOutcome(HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null));
            return;
        }

        // BOTH ANSWERED HERE, on the event thread, because neither costs a monitor: `isAutonomyBusy`
        // reads two flags and a ConcurrentHashMap.  Only the third question goes away.
        askForReturnHomeTriage(layout);
    }

    /**
     * Puts one worker on "is there anything to send home", unless one is already on it: COALESCED, AND THE LAST ASK
     * ALWAYS LANDS, for the reason `askForCoveredTrack` gives (the refresh runs on every arrival and departure, and
     * dropping the extra asks leaves a stale answer on screen).  `railway` is captured on the caller's thread.
     */
    private void askForReturnHomeTriage(final Layout railway)
    {
        this.ui.returnHomeTriageSubject = railway;

        this.ui.returnHomeTriageDirty.set(true);

        if (!this.ui.returnHomeTriageInFlight.compareAndSet(false, true)) return;

        try
        {
            this.ui.ReturnHomeTriageRenderer.submit(this::workOutReturnHomeTriage);
        }
        catch (Exception refused)
        {
            // Submitting can be refused - the pool is shut down when the window closes - and the flag
            // is set by then.  Clearing it here keeps the latch from coming back at one remove.
            settleReturnHomeTriage();
        }
    }

    /**
     * Asks the railway, off the event thread, and sends the answer back to be painted (see `refreshReturnHomeButton`).
     */
    private void workOutReturnHomeTriage()
    {
        try
        {
            // UNTIL THE ANSWER IS NOT OUT OF DATE.  Cleared BEFORE the pass that answers it, so an ask
            // that arrives while this one is computing sets it again and is served by the next turn of
            // this loop rather than being lost to it.
            while (this.ui.returnHomeTriageDirty.compareAndSet(true, false))
            {
                Layout asked = this.ui.returnHomeTriageSubject;

                if (asked == null) continue;

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
                    continue;
                }

                // DISCARDED IF THE RAILWAY MOVED ON.  `parseAuto` replaces the Layout wholesale, and an
                // answer about the graph that has just been thrown away must not paint the button for
                // the one that replaced it.
                if (asked != this.ui.returnHomeTriageSubject) continue;

                this.ui.returnHomeTriagePausedAway = pausedAway;
                this.ui.returnHomeTriage = answer;

                javax.swing.SwingUtilities.invokeLater(() -> paintReturnHomeFromTheAnswer(asked));
            }
        }
        finally
        {
            settleReturnHomeTriage();

            // AN ASK THAT ARRIVED IN THE GAP - between the loop reading the flag for the last time and
            // the line above giving the job up.  `workOutCoveredTrack` says why this matters: on the
            // last refresh of a run there is nothing else coming to pick it up.
            if (this.ui.returnHomeTriageDirty.get() && this.ui.returnHomeTriageSubject != null)
            {
                askForReturnHomeTriage(this.ui.returnHomeTriageSubject);
            }
        }
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
        // WRAPPED (Adam on MT-756: "The tooltip is much too wide"): a reason can name many trains
        this.ui.returnHomeButton().setToolTipText(AutonomyEditorPanel.wrapped(nothingToDo == null
            ? I18n.t("ui.main.tooltip.returnHome")
            : describeStagingOutcome(nothingToDo, this.ui.returnHomeTriagePausedAway)));
    }

    /**
     * Says the job is given up, and wakes anybody waiting for the answer, both under the one monitor
     * `awaitReturnHomeTriage` waits on, so a waiter cannot check the flags, find an ask outstanding, and sleep in the
     * instant between this clearing them and announcing it.
     */
    private void settleReturnHomeTriage()
    {
        synchronized (this.ui.returnHomeTriageSettled)
        {
            this.ui.returnHomeTriageInFlight.set(false);

            this.ui.returnHomeTriageSettled.notifyAll();
        }
    }

    /**
     * Waits until every return-home ask already made has landed; true when the answer is up to date, false when the
     * wait ran out after at most `millis`.  BOUNDED, ALWAYS: the only wait a caller on the event thread may make about
     * this question, because it is on THIS window's own object, held for no longer than it takes to set two fields,
     * never on the `Layout` monitor, which a dispatch holds across a `CONFIGURE_SLEEP` per accessory of a path
     * (OB-192).
     */
    boolean awaitReturnHomeTriage(long millis)
    {
        long deadline = System.currentTimeMillis() + millis;

        synchronized (this.ui.returnHomeTriageSettled)
        {
            while (this.ui.returnHomeTriageInFlight.get() || this.ui.returnHomeTriageDirty.get())
            {
                long left = deadline - System.currentTimeMillis();

                if (left <= 0) return false;

                try
                {
                    this.ui.returnHomeTriageSettled.wait(left);
                }
                catch (InterruptedException stopped)
                {
                    Thread.currentThread().interrupt();

                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Return Home's right-click menu, freshly built: Run Trains to Their Homes (the button itself, greyed with the
     * button's own reason), then Place All Trains at Their Homes, which puts each homed train on its home in the setup
     * and takes the others off the layout without sending anything to the railway (FR-129), greyed with its reason
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
                // The button is named by pulling its own label in, so the sentence cannot tell the user
                // to press something that is not on their screen - four translations had already
                // invented a different name for it on day one.
                return I18n.f("autolayout.ui.errorReturnHomeTrainsMoving",
                    I18n.t("ui.main.gracefulStop"));

            case IMPOSSIBLE:
                return I18n.f("autolayout.ui.errorCannotReachHome", namesWithHomes(blocked));

            // Its own sentence, not the default one: "no return plan found" would be wrong twice, as a plan almost
            // certainly exists and the obstacle is that nobody can say where the train is; errorCannotReachHome is
            // wrong too, sending the operator to look at track that is fine.
            case POSITION_AMBIGUOUS:
                return I18n.f("autolayout.ui.errorLocomotivePositionAmbiguous", namesOf(blocked));

            default:
                return I18n.t("autolayout.ui.errorNoReturnPlanFound");
        }
    }

    /**
     * The blocked locomotives with the station each of them could not reach (OB-206; Adam: "when you say 'these
     * locomotives cannot reach their home stations: <locomotive>', update the error message to provide the station
     * name."), falling back to the locomotive alone where the home cannot be named, as a sentence that drops a train
     * because its station could not be looked up would hide the very thing it is reporting.
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
