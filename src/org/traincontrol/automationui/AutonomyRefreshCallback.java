package org.traincontrol.automationui;

import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;

/**
 * Telling the window that a train has set off or arrived, so the panels that describe it redraw.
 *
 * A `Layout` announces both ends of every path to whatever has registered a callback on it. Two things
 * in the window are descriptions of exactly that and of nothing else - the timetable, which grows an
 * entry each time a path is dispatched with capture on, and the locomotive status panel, which says
 * what each train is doing. Neither has any other reason to redraw, so neither redraws unless this is
 * attached.
 *
 * **Why this is its own class rather than four lines in TrainControlUI.** It used to be four lines in
 * TrainControlUI - inside the method that built the GraphStream graph window, because that window
 * wanted the same notification for its own purposes and registered the callback for all three. When
 * the graph window was deleted in `d8db4879` the callback went with it, and the timetable and the
 * status panel quietly stopped redrawing. Nothing failed; two panels simply stopped being true.
 *
 * Adam found both, three days apart, as two separate bugs: "capture locomotive commands is capturing
 * neither manual locomotive commands nor full autonomy commands into the timetable" - it was capturing
 * them perfectly, and the table was never repainted to show it - and OB-097, "a route finished, but the
 * loc status panel under Locomotive Commands still indicated an active route".
 *
 * Here, it is a thing with a name that can be tested. `testTimetableCapturesThroughARealRun` attaches
 * exactly this and runs real autonomy over it, and `testTheWindowAttachesItsRefreshCallback` fails the
 * build if TrainControlUI stops calling it - which is the only kind of test that would have caught the
 * deletion, because deleting a caller breaks nothing that any behavioural test can see.
 */
public final class AutonomyRefreshCallback
{
    /**
     * The name this registers under.
     *
     * Layout keys callbacks by name, so this coexists with the diagram monitor's rather than replacing
     * it - which is the trap in a shared registry: two subscribers under one name means the second
     * silently unsubscribes the first.
     */
    public static final String CALLBACK_NAME = "UiRefresh";

    private AutonomyRefreshCallback()
    {
    }

    /**
     * Attaches to a layout, replacing any earlier attachment of this name on it.
     *
     * Must be called on every layout the model builds, not once at start-up: `parseAuto` replaces the
     * Layout object wholesale, and callbacks live on the object.
     *
     * **The work is posted, never done here.** A layout fires these from the thread that is driving the
     * trains, inside `synchronized (this.activeLocomotives)` - so anything that blocks in a callback
     * holds up the railway, and anything that takes a UI lock invites the deadlock DR-B7 produced: the
     * event thread holding the window's monitor and waiting on the layout's while a layout thread does
     * the reverse. Posting means the firing thread returns immediately and takes no second lock.
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
