package org.traincontrol.base;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.GraphReducer.ReducedEdge;
import org.traincontrol.base.GraphReducer.TileStep;
import org.traincontrol.base.TileGraph.TileKey;
import org.traincontrol.base.TileOverlay.State;

/**
 * Turns what the running layout is doing into what each tile should show.
 *
 * The autonomy model deals in Points and Edges; the diagram deals in tiles.  The bridge is the tile path
 * every reduced edge kept when it was built - the same data lock derivation uses - so nothing has to be
 * recomputed from geometry while trains are moving.
 *
 * Deliberately not a Swing class.  It computes a map of tile to overlay and hands it to whatever wants
 * to paint it, which is what lets it be tested without a screen.
 *
 * Threading: the layout fires its callback from whichever thread moved a train, sometimes while holding
 * its own monitor.  So firing does nothing but set a flag, and a worker does the work - if the callback
 * did the computing, a slow repaint would hold up the railway.
 *
 * @author Adam
 */
public class DiagramMonitor
{
    /**
     * Receives a complete picture of what every tile should show.
     *
     * Complete rather than incremental: the alternative is tracking what changed since last time, and a
     * missed change leaves a tile lit after its train has gone - which looks exactly like a train that
     * is still there.
     */
    public interface Publisher
    {
        void publish(Map<TileKey, TileOverlay> overlays);
    }

    /**
     * Supplies the layout being watched.  A supplier rather than the layout itself because the layout is
     * replaced wholesale whenever a configuration is loaded, and a monitor holding the old one would
     * quietly report on a railway nobody is running.
     */
    public interface LayoutSource
    {
        Layout get();
    }

    public static final String CALLBACK_NAME = "DiagramCallback";

    private final LayoutSource layoutSource;
    private final Publisher publisher;

    // Swapped wholesale rather than mutated: setEdges runs on the event thread while compute iterates
    // on the driver's timer thread, and clearing a map under an iterator is a ConcurrentModification
    // that the tick would swallow silently - the overlay would just quietly stop being right.
    private volatile Map<String, ReducedEdge> edgesByName = new LinkedHashMap<>();
    private volatile Map<String, TileKey> pointTiles = new LinkedHashMap<>();

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    private volatile Map<TileKey, TileOverlay> published = Collections.emptyMap();

    /**
     * @param layoutSource where the running layout comes from
     * @param edgesByName the reduced edges, keyed by the name the running Layout knows them by
     * @param pointTiles which tile each Point name sits on
     * @param publisher what to do with the result
     */
    public DiagramMonitor(LayoutSource layoutSource, Map<String, ReducedEdge> edgesByName,
        Map<String, TileKey> pointTiles, Publisher publisher)
    {
        this.layoutSource = layoutSource;
        this.publisher = publisher;

        setEdges(edgesByName, pointTiles);
    }

    /**
     * Replaces what is being watched, after a rebuild.
     *
     * Keyed by NAME rather than by tile, because that is the only thing the running Layout and the
     * derived graph share: Edge.getName() is "start -> end" over Point names, and those names are what
     * the builder wrote into the generated file.  Anything else would be this class guessing at a join
     * the builder already made.
     *
     * @param edgesByName
     * @param pointTiles
     */
    public final void setEdges(Map<String, ReducedEdge> edgesByName, Map<String, TileKey> pointTiles)
    {
        Map<String, ReducedEdge> newEdges = new LinkedHashMap<>();
        Map<String, TileKey> newPoints = new LinkedHashMap<>();

        if (edgesByName != null) newEdges.putAll(edgesByName);
        if (pointTiles != null) newPoints.putAll(pointTiles);

        this.edgesByName = newEdges;
        this.pointTiles = newPoints;
    }

    /**
     * Forgets what was last published, so the next refresh publishes even an identical picture.
     *
     * For after the screen has been cleared behind this class's back: refresh() suppresses a publish
     * whose picture has not changed, which is right for a burst of movement and wrong for tiles that
     * were wiped and now show nothing - to them, the same picture is news.
     */
    public void invalidate()
    {
        published = Collections.emptyMap();
    }

    /**
     * Builds the two indexes this needs from a reduction and the names the builder gave its Points.
     *
     * @param reducer the reduction the running configuration was built from
     * @param names tile -> the name that Point carries in the generated file (AutonomyBuilder.uniqueNames)
     * @return edge name -> reduced edge
     */
    public static Map<String, ReducedEdge> indexEdges(GraphReducer reducer, Map<TileKey, String> names)
    {
        Map<String, ReducedEdge> out = new LinkedHashMap<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            String start = names.get(edge.getStart());
            String end = names.get(edge.getEnd());

            if (start == null || end == null) continue;

            out.put(start + " -> " + end, edge);
        }

        return out;
    }

    /**
     * @param names tile -> Point name
     * @return the reverse: Point name -> tile
     */
    public static Map<String, TileKey> indexPoints(Map<TileKey, String> names)
    {
        Map<String, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> entry : names.entrySet())
        {
            out.put(entry.getValue(), entry.getKey());
        }

        return out;
    }

    /**
     * Registers with a layout.  Additive: other callbacks on the same layout are untouched.
     * @param layout
     */
    public void attach(Layout layout)
    {
        if (layout == null) return;

        layout.setCallback(CALLBACK_NAME, new Layout.TriFunction<List<Edge>, org.traincontrol.base.Locomotive, Boolean, Void>()
        {
            @Override
            public Void apply(List<Edge> edges, org.traincontrol.base.Locomotive locomotive, Boolean locked)
            {
                markDirty();
                return null;
            }
        });
    }

    /**
     * Notes that something moved.  Does no work: the firing thread may be holding the layout's monitor,
     * and anything slow here would hold up the trains rather than the drawing.
     */
    public void markDirty()
    {
        dirty.set(true);
    }

    /**
     * Recomputes and publishes if anything has moved since the last time.
     *
     * Called by whatever is driving the monitor - a timer, a worker thread, or a test.  Idempotent and
     * complete, so a run that coincides with a burst of movement simply reports the end state.
     *
     * @return true if anything was published
     */
    public boolean refreshIfDirty()
    {
        if (!dirty.getAndSet(false)) return false;

        refresh();

        return true;
    }

    /**
     * Recomputes and publishes unconditionally - used when the grid has been rebuilt and the tiles have
     * lost whatever they were showing, which is not something the layout would fire about.
     */
    public void refresh()
    {
        Map<TileKey, TileOverlay> overlays = compute();

        if (overlays.equals(published)) return;

        published = overlays;

        if (publisher != null) publisher.publish(Collections.unmodifiableMap(overlays));
    }

    /**
     * @return what was last published, for a view being rebuilt to catch up with
     */
    public Map<TileKey, TileOverlay> getPublished()
    {
        return Collections.unmodifiableMap(published);
    }

    /**
     * Works out what every tile should show.
     *
     * Reads the layout's own view of what is running rather than tracking movements itself: a monitor
     * that accumulated state would drift, and drift here means a tile still lit after its train has
     * gone, which reads as a train that is still there.
     */
    Map<TileKey, TileOverlay> compute()
    {
        Map<TileKey, TileOverlay> overlays = new LinkedHashMap<>();

        Layout layout = layoutSource == null ? null : layoutSource.get();

        if (layout == null) return overlays;

        Map<org.traincontrol.base.Locomotive, List<Edge>> active;

        try
        {
            active = layout.getActiveLocomotives();
        }
        catch (RuntimeException e)
        {
            // the layout is being replaced underneath us; the next refresh will catch up
            return overlays;
        }

        if (active == null) return overlays;

        for (Map.Entry<org.traincontrol.base.Locomotive, List<Edge>> entry : active.entrySet())
        {
            List<Edge> path = entry.getValue();

            if (path == null) continue;

            Set<String> reachedPoints = new LinkedHashSet<>();

            List<Point> milestones = layout.getReachedMilestones(entry.getKey());

            if (milestones != null)
            {
                for (Point point : milestones)
                {
                    if (point != null) reachedPoints.add(point.getName());
                }
            }

            for (Edge edge : path)
            {
                if (edge == null) continue;

                ReducedEdge reduced = edgesByName.get(edge.getName());

                if (reduced == null) continue;

                // An edge counts as reached once the train has passed the point it ends at.  That is the
                // same rule the graph window colours by, so the two views cannot disagree.
                boolean reached = edge.getEnd() != null && reachedPoints.contains(edge.getEnd().getName());

                paint(overlays, reduced, reached ? State.REACHED : State.ACTIVE, false);
            }

            // and the locomotive itself, at whichever point it has most recently reached
            Point at = layout.getLocomotiveLocation(entry.getKey());

            if (at != null) markTrain(overlays, at);
        }

        // everything held clear so those paths can run
        for (List<Edge> path : active.values())
        {
            if (path == null) continue;

            for (Edge edge : path)
            {
                if (edge == null || edge.getLockEdges() == null) continue;

                for (Edge locked : edge.getLockEdges())
                {
                    ReducedEdge reduced = edgesByName.get(locked.getName());

                    if (reduced != null) paint(overlays, reduced, State.LOCKED, false);
                }
            }
        }

        return overlays;
    }

    private void paint(Map<TileKey, TileOverlay> into, ReducedEdge edge, State state, boolean train)
    {
        TileOverlay overlay = new TileOverlay(state, train);

        for (TileStep step : edge.getPath())
        {
            TileOverlay existing = into.get(step.getTile());

            into.put(step.getTile(), existing == null ? overlay : existing.merge(overlay));
        }
    }

    /**
     * Marks the tile a locomotive is standing on.
     *
     * The running Layout knows a Point only by name, so the tile comes from the index the builder's
     * names produced rather than from the Point itself, which has never heard of tiles.
     */
    private void markTrain(Map<TileKey, TileOverlay> into, Point at)
    {
        if (at == null) return;

        TileKey tile = pointTiles.get(at.getName());

        if (tile == null) return;

        TileOverlay existing = into.get(tile);

        into.put(tile, existing == null ? new TileOverlay(State.IDLE, true)
                                        : existing.merge(new TileOverlay(State.IDLE, true)));
    }
}
