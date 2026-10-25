package org.traincontrol.gui;

/**
 * Which station captions autonomy can never choose, worked out on `CoveredTrackRenderer` rather than on the event
 * thread, coalesced as the covered marks are, and painted on the event thread when the answer lands.
 *
 * The answer, the flags and the ask stay on `TrainControlUI`; this holds the machine that drives them.
 */
final class CaptionVisibility
{
    private final TrainControlUI ui;

    CaptionVisibility(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Re-applies the caption rule to every label the diagram currently holds, for the moments when the ANSWER changes without the
     * labels being rebuilt (the setting toggled, a configuration loaded or unloaded); visibility is otherwise decided once, as each
     * label is registered.
     *
     * **THE ANSWER IS WORKED OUT ON A WORKER AND PAINTED WHEN IT LANDS**: the rule reaches `Layout.isChoosableByAutonomy`,
     * `synchronized` on the `Layout`, once per captioned square, and an `invokeLater` loop over every registered station parked the
     * event thread on the railway's monitor for the length of a dispatch.  `refreshCoveredTrack` is the same fix for the same reason.
     */
    void refreshCaptionVisibility()
    {
        // Captured here, on the caller's thread, for the reason `refreshCoveredTrack` gives: both getters BUILD when there is nothing yet.
        // The session comes off the FIELD rather than the lazy getter, which parses every page and can raise a dialog.
        org.traincontrol.automation.Layout railway =
            this.ui.getModel() != null && this.ui.getModel().hasAutoLayout() ? this.ui.getModel().getAutoLayout() : null;

        this.ui.captionVisibilitySubject = new TrainControlUI.CoveredTrackAsk(railway, this.ui.autonomySession());

        this.ui.captionVisibilityDirty.set(true);

        askForCaptionVisibility();
    }

    /**
     * Puts one worker on "which squares can autonomy choose", unless one is already on it, COALESCED (`CoalescedAsk`).
     * On `CoveredTrackRenderer` rather than a pool of its own, deliberately: this answer changes when the SETUP changes (a
     * handful of times a session, not once a tick), and a fourth pool would be a fourth thing to shut down.  It has no
     * await, so it settles with no lock.
     */
    void askForCaptionVisibility()
    {
        CoalescedAsk.ask(this.ui.captionVisibilityInFlight, this.ui.captionVisibilityDirty, this.ui.CoveredTrackRenderer,
            this::workOutCaptionVisibility, () -> this.ui.captionVisibilityInFlight.set(false), this::askForCaptionVisibility);
    }

    /**
     * Asks the railway which squares autonomy can choose, off the event thread, and paints the answer.  Any copy of a square
     * autonomy could choose is enough: a square split into several Points is one place to the person looking at it, and hiding its
     * name because one of its directions is barred would be a lie about the other.
     *
     * `isChoosableByAutonomy`, NOT `isDestination` (a different flag, which left an inactive terminus's name showing): "will
     * autonomy ever pick this" is answered by the standing bars in `barredFromAutonomy` (inactive, reversing, not an auto destination).
     *
     * @see #refreshCaptionVisibility()
     */
    void workOutCaptionVisibility()
    {
        TrainControlUI.CoveredTrackAsk ask = this.ui.captionVisibilitySubject;

        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> cannot = null;

        if (ask != null && ask.railway != null && ask.setup != null)
        {
            java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> found =
                new java.util.HashSet<>();

            try
            {
                for (org.traincontrol.automationui.TileGraph.TileKey square
                    : ask.setup.getStationIndex().squares())
                {
                    boolean choosable = false;

                    for (org.traincontrol.automation.Point point
                        : TrainControlUI.autonomyPointsForTile(ask.railway, ask.setup, square))
                    {
                        if (ask.railway.isChoosableByAutonomy(point))
                        {
                            choosable = true;
                            break;
                        }
                    }

                    if (!choosable) found.add(square);
                }
            }
            catch (RuntimeException replaced)
            {
                // The layout or the setup is being rebuilt underneath this pass; the next ask
                // catches up.  Not logged: a burst would fill the log with one moment.
                return;
            }

            cannot = java.util.Collections.unmodifiableSet(found);
        }

        // DISCARDED IF THE SETUP MOVED ON.  `parseAuto` replaces the Layout wholesale, and an
        // answer about the graph that has just been thrown away must not hide captions on the
        // one that replaced it.
        if (ask != this.ui.captionVisibilitySubject) return;

        this.ui.captionsAutonomyCannotChoose = cannot;

        javax.swing.SwingUtilities.invokeLater(this.ui::paintCaptionsFromTheAnswer);
    }
}
