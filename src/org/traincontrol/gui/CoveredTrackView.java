package org.traincontrol.gui;

/**
 * The marks a standing train puts on the track diagram - the road it covers and the track it blocks - worked out on
 * `CoveredTrackRenderer`, never on the event thread (OB-192), coalesced so the last ask always lands, and a bounded
 * wait for them to have landed.
 *
 * The marks, the flags, the ask and the worker stay on `TrainControlUI`; this holds the machine that drives them.
 */
final class CoveredTrackView
{
    private final TrainControlUI ui;

    CoveredTrackView(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Asks for both of the marks a standing train puts on the diagram to be worked out again.
     *
     * **NOT ON THE EVENT THREAD, EVER (OB-192)** (Adam: "starting autonomous operation from the current track state... makes the UI
     * unresponsive.  Trains still run, but nothing is repainted, and controls are stuck.").  The answer comes from
     * `Layout.edgesCoveredByStandingTrains`, which is `synchronized` on the `Layout`, and a dispatch holds that monitor for the
     * whole of `configureAndLockPath` (a `CONFIGURE_SLEEP` per accessory of the path).  Worse than a stall, as `Layout.getEdges`'s
     * comment spells out: a driving thread inside that monitor commands an accessory, and `MarklinAccessory.setSwitched` called
     * `repaintSwitch`, which was `synchronized` on this window until RSA67-C1, so an event thread taking the railway's monitor while
     * holding its own was AB-BA and unrecoverable.
     *
     * So the answer is worked out on a worker (as `repaintAutoLocListLite` and `repaintAutoLocListFull` also are).  The two sets are
     * computed together and diffed together, and `repaintTheWashWhereItChanged` redraws only the squares that changed.
     *
     * SAFE off the event thread, a claim about three things: `coveredTrack` and `blockedTrack` are volatile and replaced wholesale;
     * `DiagramTileRegistry` is built on `ConcurrentHashMap` and `labelsFor` hands back a copy; and `refreshCoveredMark` is a
     * `repaint()`, which Swing documents as callable from any thread.  And in the railway's own lock order: the worker takes the
     * `Layout` monitor and gives it back before it touches anything of this window's, so it never holds one and wants the other.
     *
     * @see #awaitCoveredTrack(long) for waiting until the answer has landed
     */
    void refreshCoveredTrack()
    {
        // Captured here, on the caller's thread: both getters BUILD when there is nothing yet (`getAutoLayout()` constructs a `Layout`,
        // `getAutonomySession()` opens the setup off disk), and neither belongs on a worker; `hasAutoLayout` rather than `getAutoLayout`,
        // so a question about what is covered does not bring a railway into being.  And only with a setup loaded (OB-307; Adam: "orange
        // should only be painted with autonomy loaded"): a railway object outlives the setup it was loaded from.
        org.traincontrol.automation.Layout railway = this.ui.getModel() != null && this.ui.getModel().hasAutoLayout()
            && this.ui.isAutonomyLoaded() ? this.ui.getModel().getAutoLayout() : null;

        this.ui.coveredTrackSubject = new TrainControlUI.CoveredTrackAsk(railway, railway == null ? null : this.ui.getAutonomySession());

        this.ui.coveredTrackDirty.set(true);

        askForCoveredTrack();
    }

    /**
     * Puts one worker on the job, unless one is already on it.  COALESCED, AND THE LAST ASK ALWAYS LANDS: the refresh runs on every
     * monitor tick of a run and at both ends of every path, and the answer can take as long as a dispatch holds the railway's
     * monitor, so asks arrive faster than they can be served.  Dropping the extra ones leaves a stale mark (OB-180); instead the flag
     * says "the answer is out of date" and the worker keeps going until it is not.  A flag rather than a queue, cleared in a
     * `finally` (`repaintAutoLocList` says why).
     */
    void askForCoveredTrack()
    {
        if (!this.ui.coveredTrackInFlight.compareAndSet(false, true)) return;

        try
        {
            this.ui.CoveredTrackRenderer.submit(this::workOutCoveredTrack);
        }
        catch (Exception refused)
        {
            // Submitting can be refused - the pool is shut down when the window closes - and the flag
            // is set by then.  Clearing it here keeps the latch from coming back at one remove.
            settleCoveredTrack();
        }
    }

    /**
     * The old body of `refreshCoveredTrack`, on a worker thread.
     *
     * @see #refreshCoveredTrack()
     */
    void workOutCoveredTrack()
    {
        try
        {
            // UNTIL THE ANSWER IS NOT OUT OF DATE.  Cleared BEFORE the pass that answers it, so an ask
            // that arrives while this one is computing sets it again and is served by the next turn of
            // this loop rather than being lost to it.
            while (this.ui.coveredTrackDirty.compareAndSet(true, false))
            {
                TrainControlUI.CoveredTrackAsk ask = this.ui.coveredTrackSubject;

                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    java.util.Set<org.traincontrol.automationui.TileGraph.RouteId>> found =
                    java.util.Collections.emptyMap();

                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    java.util.Set<org.traincontrol.automationui.TileGraph.RouteId>> greyed = java.util.Collections.emptyMap();

                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    java.util.Set<org.traincontrol.automationui.TileGraph.RouteId>> was = this.ui.coveredTrack;

                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    java.util.Set<org.traincontrol.automationui.TileGraph.RouteId>> wasBlocked = this.ui.blockedTrack;

                try
                {
                    if (ask != null && ask.railway != null && ask.setup != null)
                    {
                        found = ask.setup.routesCoveredByStandingTrains(ask.railway);

                        // The grey is not bounded to a running railway, and this is the one place that decides it (W7B-B1; Adam: "yes, this greyout
                        // should appear at idle and be regenerated if a placement or train/track length is changed."): `Layout.isPathClear` sweeps the
                        // covered edges in every tier at every time, because a tail lying across the rail is a physical fact, so at idle a manual send
                        // across a parked train's tail was refused over track this drew as free.  The guard and the affordance ask one question
                        // (OB-057/OB-090).  The extent is the squares the railway claims (OB-280), and `repaintTheWashWhereItChanged` still redraws only
                        // the squares that changed.
                        greyed = ask.setup.routesBlockedByStandingTrains(ask.railway);
                    }
                }
                catch (Exception cannotWorkItOut)
                {
                    // NOTHING MARKED rather than a broken diagram.  A picture nobody can read is worse
                    // than a protection nobody can see.
                    found = java.util.Collections.emptyMap();
                    greyed = java.util.Collections.emptyMap();
                }

                this.ui.coveredTrack = found;
                this.ui.blockedTrack = greyed;

                // And the tiles that changed are redrawn (OB-180): the wash is decided when a tile is DRAWN, and a tile is only drawn when its
                // own accessory, feedback or route changes, so moving a train left the old squares grey (Adam: "when a train is manually moved to
                // a new station in the track diagram viewer using control+X and V, its former shaded icons are not reset.").  Reached on every
                // path out of the computation above, including the failed one, since ungreying is where the old tiles most need repainting.
                // Only what CHANGED, because the refresh runs whenever anything about a train changes.
                this.ui.repaintTheWashWhereItChanged(was, this.ui.coveredTrack, wasBlocked, this.ui.blockedTrack);
            }
        }
        finally
        {
            settleCoveredTrack();

            // AN ASK THAT ARRIVED IN THE GAP.  Between the loop reading the flag for the last time and
            // the line above giving the job up, an ask can set the flag and find a worker still in
            // flight - so it neither runs now nor is picked up, and the marks stay as they were until
            // something else asks.  On the last refresh of a run there is nothing else.
            if (this.ui.coveredTrackDirty.get()) askForCoveredTrack();
        }
    }

    /**
     * Says the job is given up, and wakes anybody waiting for the marks to have caught up.
     *
     * Both under the one monitor `awaitCoveredTrack` waits on, so a waiter cannot check the flags,
     * find a refresh outstanding, and go to sleep in the instant between this clearing them and
     * announcing it.
     */
    void settleCoveredTrack()
    {
        synchronized (this.ui.coveredTrackSettled)
        {
            this.ui.coveredTrackInFlight.set(false);

            this.ui.coveredTrackSettled.notifyAll();
        }
    }

    /**
     * Waits until every covered-track refresh already asked for has landed: the marks are worked out on a worker (OB-192), so
     * asking for them and looking at them are two moments, which anything that READS `coveredTrack` or `blockedTrack` right after
     * asking (in practice a test) has to bridge.  BOUNDED, and it says which it did: a wait with no deadline would turn a dead
     * worker into a hung window rather than a stale mark.
     *
     * @param millis how long to wait at most
     * @return true when the marks are up to date, false when the wait ran out
     */
    boolean awaitCoveredTrack(long millis)
    {
        long deadline = System.currentTimeMillis() + millis;

        synchronized (this.ui.coveredTrackSettled)
        {
            while (this.ui.coveredTrackInFlight.get() || this.ui.coveredTrackDirty.get())
            {
                long left = deadline - System.currentTimeMillis();

                if (left <= 0) return false;

                try
                {
                    this.ui.coveredTrackSettled.wait(left);
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
}
