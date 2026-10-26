package org.traincontrol.automationui;

import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;

/**
 * Telling the window that a train has set off or arrived, so the panels that describe it redraw: the timetable (an
 * entry for each path dispatched with capture on) and the locomotive status panel have no other reason to redraw.
 *
 * Its own class rather than lines in TrainControlUI, because the callback once rode with the GraphStream graph window
 * and went when that window was deleted (`d8db4879`), after which two panels quietly stopped being true (OB-097).
 * Deleting a caller breaks nothing a behavioral test can see, so `testTheWindowAttachesItsRefreshCallback` fails the
 * build if TrainControlUI stops calling it, and `testTimetableCapturesThroughARealRun` attaches exactly this over a
 * real run.
 */
public final class AutonomyRefreshCallback
{
    /**
     * The name this registers under.  Layout keys callbacks by name, so this coexists with the diagram monitor's; two
     * subscribers under one name would silently unsubscribe the first.
     */
    public static final String CALLBACK_NAME = "UiRefresh";

    private AutonomyRefreshCallback()
    {
    }

    /**
     * Attaches to a layout, replacing any earlier attachment of this name on it.  Must be called on every layout the
     * model builds: `parseAuto` replaces the Layout wholesale, and callbacks live on the object.
     *
     * **The work is posted, never done here.** A layout fires these from the thread driving the trains, inside
     * `synchronized (this.activeLocomotives)`, so anything that blocks holds up the railway and anything that takes a
     * UI lock invites a deadlock with the event thread.
     *
     * @param layout the layout to watch, or null to do nothing
     * @param onPathEvent what to redraw; run on the event thread, and must be quick
     */
    public static void attach(Layout layout, Runnable onPathEvent)
    {
        if (layout == null || onPathEvent == null) return;

        layout.setCallback(CALLBACK_NAME,
            new Layout.TriFunction<List<Edge>, Locomotive, Boolean, Void>()
        {
            @Override
            public Void apply(List<Edge> edges, Locomotive locomotive, Boolean locked)
            {
                javax.swing.SwingUtilities.invokeLater(onPathEvent);

                return null;
            }
        });
    }
}
