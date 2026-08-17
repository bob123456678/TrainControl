package org.traincontrol.gui;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.DiagramMonitor;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay;

/**
 * Keeps the diagram showing what the trains are doing.
 *
 * DiagramMonitor works out what each tile should show and DiagramTileRegistry gets it onto the screen;
 * this is the piece between them that makes it happen repeatedly, on the right threads.
 *
 * Why a poll rather than painting straight from the layout's callback: the callback fires from whichever
 * thread moved a train, sometimes while holding the layout's own monitor.  Repainting there would put the
 * drawing in front of the railway - a slow paint would delay the next movement.  So the callback only
 * sets a flag, and this ticks over, notices, and does the work somewhere harmless.  A burst of movement
 * collapses into one repaint, which is what makes a busy layout cheap rather than expensive.
 *
 * Threading: computing reads the layout, so it happens on the timer thread.  Publishing touches Swing, so
 * it happens on the event thread.  Neither ever happens on the other.
 *
 * @author Adam
 */
public class DiagramMonitorDriver
{
    /**
     * How often to look.  Fast enough that a train arriving somewhere lights up without a wait anybody
     * would notice, and cheap when nothing has moved: a tick with a clean flag reads one boolean.
     */
    public static final int TICK_MS = 200;

    private final TrainControlUI ui;
    private final DiagramTileRegistry registry;

    // Volatile because both are written on the event thread (bind, setEnabled, stop) and read on the
    // timer thread (tick); without the fence the timer could legally see a stale monitor forever.
    private volatile DiagramMonitor monitor;
    private volatile Timer timer;

    // Whether monitoring is wanted at all.  Kept separate from whether the timer is running, so turning
    // the layer off does not tear down the wiring that a train arriving would need.
    private volatile boolean enabled = true;

    public DiagramMonitorDriver(TrainControlUI ui, DiagramTileRegistry registry)
    {
        this.ui = ui;
        this.registry = registry;
    }

    /**
     * Points the monitor at a reduction, and at the layout currently built from it.
     *
     * Called after every rebuild and after every configuration load, because both replace the thing being
     * watched.  Rebinding rather than making a new driver so that the timer keeps running across the
     * change and there is no window where movement goes unnoticed.
     *
     * @param session the setup the running layout was generated from
     */
    public void bind(AutonomySession session)
    {
        GraphReducer reducer = session == null ? null : session.getReducer();

        if (reducer == null)
        {
            monitor = null;
            clear();
            return;
        }

        // The same names the builder wrote into the generated file - recomputed rather than remembered,
        // because the naming depends only on the reduction and the split, so a fresh builder given both
        // cannot disagree with the one that produced the configuration.
        //
        // The split has to be passed: without it every extra Point a split tile produced is a name the
        // monitor has never heard of, and the overlay stops drawing at exactly the squares that matter.
        AutonomyBuilder builder = new AutonomyBuilder(reducer, null)
            .withReversibleTiles(session.reversibleTiles())
            .withMandatoryTurns(session.mandatoryTurnTiles())
            .withParkingTiles(session.parkingTiles());

        Map<String, TileKey> names = builder.tilesByName();

        if (monitor == null)
        {
            monitor = new DiagramMonitor(
                new DiagramMonitor.LayoutSource()
                {
                    @Override
                    public Layout get()
                    {
                        return currentLayout();
                    }
                },
                builder.edgesByName(),
                names,
                new DiagramMonitor.Publisher()
                {
                    @Override
                    public void publish(final Map<TileKey, TileOverlay> overlays)
                    {
                        onEventThread(overlays);
                    }
                });
        }
        else
        {
            monitor.setEdges(builder.edgesByName(), names);
        }

        attach();
    }

    /**
     * Registers the callback on whatever layout is running now.
     *
     * Separate from bind() because loading a configuration replaces the Layout object wholesale, and a
     * callback registered on the old one would be watching a railway nobody is running.
     */
    public void attach()
    {
        if (monitor == null) return;

        monitor.attach(currentLayout());

        // The new layout has never fired, so nothing would be shown until the first train moved - which
        // on a layout that is already running could be a long time.
        monitor.markDirty();
    }

    /**
     * Starts looking.  Idempotent.
     */
    public void start()
    {
        if (timer != null) return;

        // daemon, so a window closed with monitoring on does not keep the process alive
        timer = new Timer("DiagramMonitor", true);

        timer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                tick();
            }
        }, TICK_MS, TICK_MS);
    }

    /**
     * Stops looking and clears what is on screen.
     *
     * Clearing matters as much as stopping: tiles left lit after monitoring ends read as trains that are
     * still there.
     */
    public void stop()
    {
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }

        clear();
    }

    public boolean isRunning()
    {
        return timer != null;
    }

    /**
     * Turns the monitoring layer on or off without unwiring anything.
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled)
    {
        if (this.enabled == enabled) return;

        this.enabled = enabled;

        if (enabled)
        {
            // Catch up with whatever happened while it was off - but on the TIMER thread.  This used to
            // call refresh() directly, which walks every edge and point of the live layout, on the event
            // thread, in flat contradiction of this class's own rule.
            if (monitor != null) monitor.markDirty();
        }
        else
        {
            clear();
        }
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    // Nothing here redraws a rebuilt grid on purpose: DiagramTileRegistry.register() hands a new label
    // whatever its square was last told to show, so a page switched mid-run catches up tile by tile as it
    // is built.  A whole-layout republish would be the same picture arriving later.

    // --- the loop ---------------------------------------------------------------------------------

    private void tick()
    {
        // An exception here would kill the timer thread and stop monitoring silently, which looks exactly
        // like trains that have stopped moving.  So none is allowed out.  An Error still is: a timer that
        // kept ticking through an OutOfMemoryError would only make it harder to see what went wrong.
        try
        {
            DiagramMonitor current = monitor;

            if (current == null || !enabled) return;

            current.refreshIfDirty();
        }
        catch (Exception e)
        {
            if (ui != null && ui.getModel() != null && ui.getModel().isDebug())
            {
                ui.getModel().log(e);
            }
        }
    }

    private void onEventThread(final Map<TileKey, TileOverlay> overlays)
    {
        if (registry == null) return;

        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                // Re-checked HERE, on the event thread: timer.cancel does not wait for a tick already
                // running, so that tick's publish can arrive after stop()'s clear - and painting it
                // would put stale trains back on a diagram that was just wiped.
                if (timer == null || !enabled) return;

                registry.publish(overlays);

                // The station labels are the other half of showing where a train is - the tile overlay
                // says which track is claimed, the label says which locomotive and where it is heading.
                // Nothing else drives them on this path; they used to be written by the graph window.
                if (ui != null) ui.updateVisiblePoints();
            }
        });
    }

    private void clear()
    {
        if (registry == null) return;

        // The monitor suppresses publishing an unchanged picture, which is right for movement and wrong
        // for a screen that was just wiped - to it, the same picture is news.  Forgetting what was
        // published is what makes the next refresh actually arrive.
        DiagramMonitor current = monitor;

        if (current != null) current.invalidate();

        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                registry.clearOverlays();
            }
        });
    }

    private Layout currentLayout()
    {
        return ui == null || ui.getModel() == null ? null : ui.getModel().getAutoLayout();
    }
}
