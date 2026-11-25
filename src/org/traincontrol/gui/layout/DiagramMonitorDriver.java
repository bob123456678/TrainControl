package org.traincontrol.gui.layout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.DiagramMonitor;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.StationIndex;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.gui.TrainControlUI;

/**
 * Keeps the diagram showing what the trains are doing: the piece between DiagramMonitor (what each tile should show)
 * and DiagramTileRegistry (the screen) that repeats it on the right threads.
 *
 * A poll rather than painting from the layout's callback, which fires from whichever thread moved a train, sometimes
 * holding the layout's own monitor.  The callback only sets a flag and this ticks over, so a burst of movement
 * collapses into one repaint.  Computing reads the layout, so it happens on the timer thread; publishing touches Swing,
 * so it happens on the event thread; neither ever happens on the other.
 *
 * @author Adam
 */
public class DiagramMonitorDriver
{
    /** How often to look: cheap when nothing has moved, since a tick with a clean flag reads one boolean. */
    public static final int TICK_MS = 200;

    private final TrainControlUI ui;
    private final DiagramTileRegistry registry;

    // Volatile: written on the event thread (bind, setEnabled, stop), read on the timer thread (tick).
    private volatile DiagramMonitor monitor;

    // What the last bind's builder says each Point faces, the Points it says nothing about, with their squares, the setup
    // they were bound with, and the facings last handed to the monitor (OB-348).  Written and read on the event thread.
    private Map<String, TilePorts.Side> built;
    private Map<String, TileKey> guessed = new LinkedHashMap<>();
    private AutonomySession boundTo;
    private Map<String, TilePorts.Side> facingsShown;
    private volatile Timer timer;

    // Whether monitoring is wanted at all; separate from the timer running, so turning the layer off keeps the wiring.
    private volatile boolean enabled = true;

    /**
     * Which wipe the overlays on screen belong to: a tick computes on the timer thread and paints on the event thread,
     * so comparing this on both sides lets a wipe asked for in between win over a picture computed before it.
     */
    private final AtomicLong generation = new AtomicLong();

    public DiagramMonitorDriver(TrainControlUI ui, DiagramTileRegistry registry)
    {
        this.ui = ui;
        this.registry = registry;
    }

    /**
     * Points the monitor at a reduction, and at the layout currently built from it.  Called after every rebuild and
     * configuration load; rebinding rather than making a new driver keeps the timer running with no window where
     * movement goes unnoticed.
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

        // The names the builder wrote into the generated file, recomputed because naming depends only on the reduction
        // and the split, which must be passed or every extra Point a split tile produced is unknown to the monitor.
        AutonomyBuilder builder = session.builder(null);

        Map<String, TileKey> names = builder.tilesByName();

        if (monitor == null)
        {
            monitor = new DiagramMonitor(
                () -> currentLayout(),
                builder.edgesByName(),
                names,
                overlays -> onEventThread(overlays));
        }
        else
        {
            monitor.setEdges(builder.edgesByName(), names);
        }

        built = facingsOf(builder, session.getStationIndex(), names);

        // THE SQUARES WHOSE FACING IS THE SETUP'S, or a guess along their track (OB-348): asked again whenever the captions
        // are, so the icon follows a facing a door writes (`recordedFacingsChanged`)
        Map<String, TileKey> guesses = new LinkedHashMap<>();

        for (Map.Entry<String, TileKey> entry : names.entrySet())
        {
            if (!built.containsKey(entry.getKey())) guesses.put(entry.getKey(), entry.getValue());
        }

        guessed = guesses;
        boundTo = session;
        facingsShown = null;

        recordedFacingsChanged();

        // WITH WHICH OF THEM ARE PAUSED (FR-126).  Binding follows every rebuild, and the one-argument form would take
        // the gray away.
        monitor.setTrainsOnNoPoint(session.trainsOnNoPoint(), session.pausedTrainsOnNoPoint());

        attach();
    }

    /** Tells the diagram again which trains the setup records on no Point (one written after the load drew nowhere). */
    public void trainsOnNoPointChanged(AutonomySession session)
    {
        DiagramMonitor current = monitor;

        if (current == null || session == null) return;

        current.setTrainsOnNoPoint(session.trainsOnNoPoint(), session.pausedTrainsOnNoPoint());

        current.markDirty();
    }

    /**
     * Draws the trains again: pausing or resuming a train changes its icon (FR-117) and nothing the railway reports.
     */
    public void trainsPausedChanged()
    {
        DiagramMonitor current = monitor;

        if (current != null) current.markDirty();
    }

    /**
     * Which way a train standing on each Point faces, from the builder that named them: a split copy's own facing, and
     * a single copy's only way out where it has one, as the caption reads it (`StationIndex.onlyWayOutAt`).  Answers
     * Point name to the side a train's front faces there.
     */
    private static Map<String, TilePorts.Side> facingsOf(AutonomyBuilder builder, StationIndex index,
        Map<String, TileKey> names)
    {
        Map<String, TilePorts.Side> out = new LinkedHashMap<>(builder.facingByName());

        for (Map.Entry<String, TileKey> entry : names.entrySet())
        {
            if (out.containsKey(entry.getKey())) continue;

            TilePorts.Side only = index.onlyWayOutAt(entry.getValue());

            if (only != null) out.put(entry.getKey(), only);
        }

        return out;
    }

    /**
     * The facings of the Points whose copy says nothing, asked of the setup again, and the diagram redrawn where one
     * changed (OB-348).  Asked whenever the captions are, not only at a bind: every door that writes a facing redraws
     * the captions and none rebinds.  Event thread only.
     */
    public void recordedFacingsChanged()
    {
        DiagramMonitor current = monitor;
        AutonomySession session = boundTo;

        if (current == null || session == null || built == null) return;

        Map<String, TilePorts.Side> facings = new LinkedHashMap<>(built);

        for (Map.Entry<String, TileKey> entry : guessed.entrySet())
        {
            TilePorts.Side guess = session.facingWhereNoCopySays(entry.getValue());

            if (guess != null) facings.put(entry.getKey(), guess);
        }

        if (facings.equals(facingsShown)) return;

        facingsShown = facings;

        current.setFacings(facings);

        current.markDirty();
    }

    /**
     * Registers the callback on whatever layout is running now.  Separate from bind() because loading a configuration
     * replaces the Layout object wholesale.
     */
    public void attach()
    {
        if (monitor == null) return;

        monitor.attach(currentLayout());

        // The new layout has never fired, so nothing would be shown until the first train moved.
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

        timer.scheduleAtFixedRate(aTick(), TICK_MS, TICK_MS);
    }

    private TimerTask aTick()
    {
        return new TimerTask()
        {
            @Override
            public void run()
            {
                tick();
            }
        };
    }

    /**
     * Stops looking and clears what is on screen: tiles left lit after monitoring ends read as trains still there.
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

    /** Turns the monitoring layer on or off without unwiring anything. */
    public void setEnabled(boolean enabled)
    {
        if (this.enabled == enabled) return;

        this.enabled = enabled;
        if (enabled)
        {
            // Catch up with whatever happened while it was off, on the TIMER thread: refresh() walks the live layout.
            // Now, not at the next tick, or the trains come back after the rest (MT-1063); it runs between ticks.  What
            // was published is forgotten first: the wipe that turned this off may still be queued behind the click.
            DiagramMonitor current = monitor;
            Timer running = timer;
            if (current != null) current.invalidate();
            try
            {
                if (running != null) running.schedule(aTick(), 0);
            }
            catch (IllegalStateException dead)
            {
                // A timer an Error has ended refuses tasks; the rest of the click must still happen.
            }
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

    // Nothing here redraws a rebuilt grid on purpose: DiagramTileRegistry.register() hands a new label whatever its
    // square was last told to show, so a page switched mid-run catches up tile by tile as it is built.

    // the loop

    private void tick()
    {
        // An exception here would kill the timer thread and stop monitoring silently, which looks like trains that have
        // stopped moving, so none is allowed out.  An Error still is.
        try
        {
            DiagramMonitor current = monitor;

            if (current == null || !enabled) return;

            current.refreshIfDirty();
        }
        catch (Exception e)
        {
            if (ui != null && ui.getModel() != null && ui.getModel().isDebug()) ui.getModel().log(e);
        }
    }

    private void onEventThread(final Map<TileKey, TileOverlay> overlays)
    {
        if (registry == null) return;

        // Read on the thread that computed this picture, so it records the world this picture is of
        final long computedAt = generation.get();

        SwingUtilities.invokeLater(() ->
        {
            // Re-checked HERE, on the event thread: timer.cancel does not wait for a tick already running, so its
            // publish can arrive after stop()'s clear.
            if (timer == null || !enabled) return;

            // And the same for a wipe that is not a stop, or a wipe landing first leaves stale trains over an
            // emptied diagram and landing second leaves it blank while the monitor believes it published.
            if (generation.get() != computedAt) return;

            registry.publish(overlays);

            // The station labels show which locomotive is where it is heading; nothing else drives them on this
            // path.
            if (ui != null) ui.updateVisiblePoints();
        });
    }

    private void clear()
    {
        if (registry == null) return;

        // Claimed before anything is queued, so every picture already computed is dropped rather than painted after the
        // wipe.
        generation.incrementAndGet();

        SwingUtilities.invokeLater(() ->
        {
            registry.clearOverlays();

            // AFTER the wipe, on the thread that does it: the monitor suppresses an unchanged picture, which is
            // wrong for a screen just emptied.  Forgetting what was published makes the next refresh arrive, and
            // doing it here stops a tick already in flight from writing its picture into that memory afterwards.
            DiagramMonitor current = monitor;

            if (current != null) current.invalidate();
        });
    }

    private Layout currentLayout()
    {
        // ASKED, NOT BUILT: `getAutoLayout` makes a Layout where there is none, and `hasAutoLayout` would answer yes
        // about nothing.
        return ui == null || ui.getModel() == null ? null : ui.getModel().getAutoLayoutIfLoaded();
    }
}
