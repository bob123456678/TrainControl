package org.traincontrol.gui;

/**
 * The marks a standing train puts on the track diagram - the road it covers and the track it blocks - worked out on
 * `CoveredTrackRenderer`, never on the event thread (OB-192), coalesced so the last ask always lands, and a bounded
 * wait for them to have landed.
 *
 * The marks, the flags and the pool stay on `TrainControlUI`; the ask and the loop are `CoalescedAsk`'s.
 */
final class CoveredTrackView extends SettledQuestion
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
     * `repaintSwitch`, which was `synchronized` on this window, so an event thread taking the railway's monitor while
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
     * @see #awaitSettled(long) for waiting until the answer has landed
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
     * Puts one worker on the job, unless one is already on it, COALESCED (`CoalescedAsk`): the refresh runs on every
     * monitor tick of a run and at both ends of every path, and the answer can take as long as a dispatch holds the
     * railway's monitor.
     */
    void askForCoveredTrack()
    {
        CoalescedAsk.ask(this.ui.coveredTrackInFlight, this.ui.coveredTrackDirty, this.ui.CoveredTrackRenderer,
            this::workOutCoveredTrack, this::settle, this::askForCoveredTrack);
    }

    /**
     * One pass of the old body of `refreshCoveredTrack`, on a worker thread.
     *
     * @see #refreshCoveredTrack()
     */
    void workOutCoveredTrack()
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

                // The grey is not bounded to a running railway, and this is the one place that decides it (Adam: "yes, this greyout
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

    @Override
    Object settledLock()
    {
        return this.ui.coveredTrackSettled;
    }

    /**
     * A refresh is outstanding while a worker is on it or one is asked for: asking for the marks and looking at them are
     * two moments, which anything that READS `coveredTrack` or `blockedTrack` right after asking (in practice a test) bridges.
     */
    @Override
    boolean outstanding()
    {
        return this.ui.coveredTrackInFlight.get() || this.ui.coveredTrackDirty.get();
    }

    @Override
    void giveUp()
    {
        this.ui.coveredTrackInFlight.set(false);
    }
}
