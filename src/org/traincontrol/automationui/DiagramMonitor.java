package org.traincontrol.automationui;

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
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.GraphReducer.TileStep;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay.State;

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

    // Which way a train standing on each Point faces, for the icon of a parked one (Adam, 2026-10-01).
    private volatile Map<String, org.traincontrol.automationui.TilePorts.Side> facings = new LinkedHashMap<>();

    // The trains the setup records where the railway stands them on no Point, and their facings (RSA17-C2).
    private volatile Map<TileKey, org.traincontrol.automationui.TilePorts.Side> onNoPoint = new LinkedHashMap<>();

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
     * Which way a train standing on each Point faces: the icon of a parked train turns to it (Adam, 2026-10-01), as a
     * running one's turns to its path.
     *
     * @param facings Point name to the side of its square a train's front faces there
     */
    public void setFacings(Map<String, org.traincontrol.automationui.TilePorts.Side> facings)
    {
        this.facings = facings == null ? new LinkedHashMap<String, org.traincontrol.automationui.TilePorts.Side>()
            : new LinkedHashMap<>(facings);
    }

    /**
     * The trains the setup records on a square the railway stands them on no Point of, drawn parked there (RSA17-C2): the
     * error about each says it stands there, and a diagram that drew nothing read the square free.
     *
     * @param trains square to the way the train faces
     */
    public void setTrainsOnNoPoint(Map<TileKey, org.traincontrol.automationui.TilePorts.Side> trains)
    {
        this.onNoPoint = trains == null ? new LinkedHashMap<TileKey, org.traincontrol.automationui.TilePorts.Side>()
            : new LinkedHashMap<>(trains);
    }

    /**
     * Forgets what was last published, so the next refresh publishes even an identical picture.
     *
     * For after the screen has been cleared behind this class's back: refresh() suppresses a publish
     * whose picture has not changed, which is right for a burst of movement and wrong for tiles that
     * were wiped and now show nothing - to them, the same picture is news.
     *
     * **SYNCHRONIZED, on the same monitor as `refresh()`** (W21-C4). `refresh()` holds it because the
     * compare-against-published is a check then a set; this is the set half of that same field, and it
     * was taking no lock at all. The threads really are different - the driver's timer thread ticks
     * `refreshIfDirty()` while `clear()` reaches here from the event thread - so a tick already inside
     * `refresh()`, between `compute()` and `published = overlays`, would overwrite the invalidate. The
     * identical picture is then suppressed as unchanged and the wiped tiles stay blank until something
     * moves, which is the outcome this method exists to prevent.
     *
     * `published` is volatile, so visibility was never the problem; only the check-then-set was.
     */
    public synchronized void invalidate()
    {
        published = Collections.emptyMap();
    }

    // indexEdges and indexPoints stood here: two public helpers that built these maps from
    // uniqueNames.  Nothing called them - the driver takes both from the builder, which knows about the
    // split - and they indexed BASE names only, so anything that had started calling them would have
    // got a monitor that silently missed every split copy.  Deleted rather than fixed: the builder
    // already answers this correctly and a second answer is what this whole area keeps going wrong on.

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
     *
     * Synchronized because the compare-against-published is a check then a set, and this is called from
     * both the driver's timer thread and the event thread.  Two overlapping calls could publish out of
     * order, leaving the older picture on screen and the newer one recorded - after which the newer one
     * is suppressed as unchanged and the diagram stays wrong until something else moves.  It is short
     * and holds no other lock, so it cannot hold up the railway.
     */
    public synchronized void refresh()
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

        // Nothing running is no reason to show nothing: the trains parked on the railway are still marked.
        if (active == null) active = Collections.emptyMap();

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

            // The whole run as one sequence of squares, rather than each edge on its own.
            //
            // A line has to know what is on EITHER side of a square to be drawn through it, and an edge
            // alone does not: the square where two edges meet is the Point between them, and taken one
            // edge at a time its line would stop dead in the middle of that square and start again.
            List<TileKey> run = new java.util.ArrayList<>();
            List<State> states = new java.util.ArrayList<>();

            // WHAT THE RUN HAS GIVEN BACK BEHIND THE TRAIN is drawn as nothing (Adam, FR-106: "In non automic, would the
            // dark gray fade go away where unlocked?"): the driven line says the track is still held, and under
            // non-atomic routes the tail hands it back as it clears it - under atomic routes, nothing until the run ends
            Set<Edge> givenBack = layout.releasedBehind(entry.getKey());

            for (Edge edge : path)
            {
                if (edge == null || givenBack.contains(edge)) continue;

                ReducedEdge reduced = edgesByName.get(edge.getName());

                if (reduced == null) continue;

                // An edge counts as reached once the train has passed the point it ends at.  That is the
                // same rule the graph window colours by, so the two views cannot disagree.
                boolean reached = edge.getEnd() != null && reachedPoints.contains(edge.getEnd().getName());

                State state = reached ? State.REACHED : State.ACTIVE;

                // The Points at the ends are coloured by whether the train has passed THEM, not by the
                // edge they belong to: the square a train is standing on has been reached even though
                // the track ahead of it has not.
                append(run, states, reduced.getStart(),
                    edge.getStart() != null && reachedPoints.contains(edge.getStart().getName())
                        ? State.REACHED : State.ACTIVE);

                for (TileStep step : reduced.getPath())
                {
                    append(run, states, step.getTile(), state);
                }

                append(run, states, reduced.getEnd(),
                    reached ? State.REACHED : State.ACTIVE);
            }

            lay(overlays, run, states);

            // and the locomotive itself, at the point it has most recently reached.
            //
            // THROUGH THE ONE RULE (VD12-B1).  `getLocomotiveLocation` returns an ARBITRARY one of
            // the several points a running train reserves at once - the reservation legitimately
            // holds them all - so the marker could jump to a point the train had not reached yet.
            // The milestones are in the order they were reached, so the last is where the train
            // actually is, and a stationary train reserves only one point, which is the fallback.
            //
            // That paragraph was written here, then again in `AutoLocomotiveStatus`, and a third
            // time in the covered-track walk; the fourth reader - the diagram's tail overlay - did
            // not write it and drew the orange in the wrong place.  `Layout.whereTheTrainIs` is
            // those two lines with the reasoning attached.
            Point at = layout.whereTheTrainIs(entry.getKey());

            // Whether it is actually running, which is what decides the icon (FR-027).
            //
            // Asked of the LOCOMOTIVE rather than of the path: a train with a path can be standing
            // still, waiting at a platform or held while another route clears, and those are exactly
            // the ones Adam did not want marked as running.
            boolean running = entry.getKey() != null && entry.getKey().getSpeed() > 0;

            if (at != null) markTrain(overlays, at, running);
        }

        // AND EVERY TRAIN PARKED WITH NO PATH (Adam, 2026-10-01): "when a train is standing somewhere, can we show its
        // locomotive icon on top of the station in the track diagram viewer ... same icon as when a run is started."
        markTheParkedTrains(overlays, layout, active.keySet());

        // everything held clear so those paths can run - and NOT what an edge the run has given back held clear (RSA25-C2):
        // the railway released those claims with it, and the wash said the track was still spoken for
        for (Map.Entry<org.traincontrol.base.Locomotive, List<Edge>> run : active.entrySet())
        {
            List<Edge> path = run.getValue();

            if (path == null) continue;

            Set<Edge> givenBack = layout.releasedBehind(run.getKey());

            for (Edge edge : path)
            {
                if (edge == null || edge.getLockEdges() == null || givenBack.contains(edge)) continue;

                for (Edge locked : edge.getLockEdges())
                {
                    ReducedEdge reduced = edgesByName.get(locked.getName());

                    if (reduced == null) continue;

                    List<TileKey> held = new java.util.ArrayList<>();
                    List<State> states = new java.util.ArrayList<>();

                    append(held, states, reduced.getStart(), State.LOCKED);

                    for (TileStep step : reduced.getPath())
                    {
                        append(held, states, step.getTile(), State.LOCKED);
                    }

                    append(held, states, reduced.getEnd(), State.LOCKED);

                    lay(overlays, held, states);
                }
            }
        }

        return overlays;
    }

    /**
     * Adds one square to a run, unless it is already the square the run is standing on.
     *
     * Consecutive edges share the Point between them, so a run built by concatenating them names that
     * square twice - and a square listed twice is a line drawn from itself to itself, which is a blob
     * in the middle of the track.
     */
    public static void append(List<TileKey> run, List<State> states, TileKey tile, State state)
    {
        if (tile == null) return;

        if (!run.isEmpty() && tile.equals(run.get(run.size() - 1))) return;

        run.add(tile);
        states.add(state);
    }

    /**
     * Turns a run of squares into a line through each of them.
     *
     * Which way the line enters and leaves is read off the squares either side, exactly as the editor
     * reads it for a tested path - the two views draw the same picture of the same question, one before
     * the train runs and one while it does.
     *
     * Null at the ends of the run, where the line stops in the middle of the square rather than running
     * off into track nobody claimed, and null again either side of a jump between pages: a link has no
     * side on this grid to be drawn as, and the answer to that is a line that stops.
     *
     * Public so the geometry can be tested without a railway.  Everything above it needs a running
     * Layout with trains on it and cannot be reached from a test at all; this needs a list of squares.
     */
    public static void lay(Map<TileKey, TileOverlay> into, List<TileKey> run, List<State> states)
    {
        for (int i = 0; i < run.size(); i++)
        {
            TileKey at = run.get(i);

            TileOverlay.Segment segment = new TileOverlay.Segment(
                i == 0 ? null : TileGraph.gridSideTowards(at, run.get(i - 1)),
                i == run.size() - 1 ? null : TileGraph.gridSideTowards(at, run.get(i + 1)),
                states.get(i));

            TileOverlay overlay = new TileOverlay(states.get(i), false,
                java.util.Arrays.asList(segment));

            TileOverlay existing = into.get(at);

            into.put(at, existing == null ? overlay : existing.merge(overlay));
        }
    }

    /**
     * Marks every train the railway holds that has no path: the locomotive a run draws, on the square it stands on,
     * facing the way it stands (Adam, 2026-10-01).
     *
     * A train in `active` is left to the run's own mark, at the point it last reached: the several points a running
     * train reserves all answer it as their occupant, and marking those would draw it more than once.
     *
     * @param into the picture
     * @param layout the running layout
     * @param pathed the trains that hold a path
     */
    private void markTheParkedTrains(Map<TileKey, TileOverlay> into, Layout layout,
        Set<org.traincontrol.base.Locomotive> pathed)
    {
        List<Point> points;

        try
        {
            // A copy: the layout's own collection is live, and this is the timer thread
            points = new java.util.ArrayList<>(layout.getPoints());
        }
        catch (RuntimeException e)
        {
            // the layout is being replaced underneath us; the next refresh will catch up
            return;
        }

        Set<org.traincontrol.base.Locomotive> claiming = new LinkedHashSet<>();

        for (Point point : points)
        {
            org.traincontrol.base.Locomotive standing = point == null ? null : point.getCurrentLocomotive();

            if (standing == null || pathed.contains(standing)) continue;

            // NOR ONE SETTING ITS ROUTE UP (RSA17-B2): every Point it has reserved answers it, and each was drawn as a train
            // parked there - it is marked once, waiting, where it stands (RSA18-C2), as a run marks a train holding a path
            if (layout.holdsAPath(standing))
            {
                claiming.add(standing);

                continue;
            }

            TileKey tile = pointTiles.get(point.getName());

            if (tile == null) continue;

            TileOverlay mark = TileOverlay.parked(facings.get(point.getName()));

            TileOverlay existing = into.get(tile);

            into.put(tile, existing == null ? mark : existing.merge(mark));
        }

        for (org.traincontrol.base.Locomotive train : claiming)
        {
            Point at;

            try
            {
                at = layout.whereTheTrainIs(train);
            }
            catch (RuntimeException replaced)
            {
                at = null;
            }

            markTrain(into, at, false);
        }

        // AND THOSE ON NO POINT, where the setup says they stand (RSA17-C2)
        for (Map.Entry<TileKey, org.traincontrol.automationui.TilePorts.Side> train : onNoPoint.entrySet())
        {
            TileOverlay mark = TileOverlay.parked(train.getValue());

            TileOverlay existing = into.get(train.getKey());

            into.put(train.getKey(), existing == null ? mark : existing.merge(mark));
        }
    }

    /**
     * Marks the tile a locomotive is standing on.
     *
     * The running Layout knows a Point only by name, so the tile comes from the index the builder's
     * names produced rather than from the Point itself, which has never heard of tiles.
     */
    private void markTrain(Map<TileKey, TileOverlay> into, Point at, boolean moving)
    {
        if (at == null) return;

        TileKey tile = pointTiles.get(at.getName());

        if (tile == null) return;

        // STANDING STILL, THE LOCOMOTIVE STANDING THERE, facing its way - not the dot (OB-317; Adam, 2026-10-03: "when
        // autonomy is started and the blue path painted for a train, the locomotive icon disappears on the starting
        // station ... it needs to be added to the starting path / kept where it is standing").  The path line under it
        // says it holds a path; running, it is the run's own icon.
        TileOverlay mark = moving ? new TileOverlay(State.IDLE, true, true, null) : TileOverlay.parked(facings.get(at.getName()));

        TileOverlay existing = into.get(tile);

        into.put(tile, existing == null ? mark : existing.merge(mark));
    }
}
