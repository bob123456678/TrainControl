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
 * The autonomy model deals in Points and Edges, the diagram in tiles; the bridge is the tile path every reduced edge
 * kept when it was built (the same data lock derivation uses), so nothing is recomputed from geometry while trains
 * move.
 *
 * Deliberately not a Swing class: it computes a map of tile to overlay and hands it to whatever paints it, so it can be
 * tested without a screen.
 *
 * Threading: the layout fires its callback from whichever thread moved a train, sometimes holding its own monitor, so
 * firing only sets a flag and a worker does the work; computing in the callback would let a slow repaint hold up the
 * railway.
 *
 * @author Adam
 */
public class DiagramMonitor
{
    /**
     * Receives a complete picture of what every tile should show.  Complete rather than incremental: a missed change
     * would leave a tile lit after its train has gone, which looks exactly like a train still there.
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

    // Swapped wholesale rather than mutated: setEdges runs on the event thread while compute iterates on the driver's
    // timer thread, and clearing a map under an iterator is a ConcurrentModification the tick would swallow silently.
    private volatile Map<String, ReducedEdge> edgesByName = new LinkedHashMap<>();
    private volatile Map<String, TileKey> pointTiles = new LinkedHashMap<>();

    // Which way a train standing on each Point faces, for the icon of a parked one.
    private volatile Map<String, org.traincontrol.automationui.TilePorts.Side> facings = new LinkedHashMap<>();

    // The trains the setup records where the railway stands them on no Point, and their facings.
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
     * Keyed by NAME rather than by tile, because that is the only thing the running Layout and the derived graph share:
     * Edge.getName() is "start -> end" over Point names, which the builder wrote into the generated file.
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
     * Which way a train standing on each Point faces: the icon of a parked train turns to it, as a running one's turns
     * to its path.
     *
     * @param facings Point name to the side of its square a train's front faces there
     */
    public void setFacings(Map<String, org.traincontrol.automationui.TilePorts.Side> facings)
    {
        this.facings = facings == null ? new LinkedHashMap<String, org.traincontrol.automationui.TilePorts.Side>()
            : new LinkedHashMap<>(facings);
    }

    /**
     * The trains the setup records on a square the railway stands them on no Point of, drawn parked there: the
     * error about each says it stands there, and a diagram that drew nothing read the square free.
     *
     * @param trains square to the way the train faces
     */
    public void setTrainsOnNoPoint(Map<TileKey, org.traincontrol.automationui.TilePorts.Side> trains,
        java.util.Set<TileKey> paused)
    {
        // The gray before the trains it is drawn on, and never an empty set between (OB-343): a frame on the monitor's
        // thread reads the trains and then asks of each whether it is paused, so the new trains must find the new set
        // already there.
        this.pausedOnNoPoint = paused == null ? Collections.<TileKey>emptySet() : new java.util.HashSet<>(paused);

        this.onNoPoint = trains == null ? new LinkedHashMap<TileKey, org.traincontrol.automationui.TilePorts.Side>()
            : new LinkedHashMap<>(trains);
    }

    /**
     * The same, none of them paused.
     *
     * @param trains square to the way the train faces
     */
    public void setTrainsOnNoPoint(Map<TileKey, org.traincontrol.automationui.TilePorts.Side> trains)
    {
        setTrainsOnNoPoint(trains, null);
    }

    /** The squares among the trains on no Point whose train is paused, drawn gray. */
    private volatile java.util.Set<TileKey> pausedOnNoPoint = Collections.emptySet();

    /**
     * Forgets what was last published, so the next refresh publishes even an identical picture: for after the screen
     * was cleared behind this class's back, when tiles that were wiped and show nothing need the same picture again.
     *
     * **SYNCHRONIZED, on the same monitor as `refresh()`**, whose compare-against-published is a check then a set.  The
     * driver's timer thread ticks `refreshIfDirty()` while `clear()` reaches here from the event thread, so a tick
     * between `compute()` and `published = overlays` would overwrite the invalidate, the identical picture would be
     * suppressed as unchanged, and the wiped tiles would stay blank until something moves.
     */
    public synchronized void invalidate()
    {
        published = Collections.emptyMap();
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
     * Recomputes and publishes unconditionally, for when the grid was rebuilt and the tiles lost what they showed,
     * which the layout would not fire about.
     *
     * Synchronized because the compare-against-published is a check then a set, called from both the driver's timer
     * thread and the event thread: overlapping calls could leave the older picture on screen and the newer one
     * recorded, and the newer is then suppressed as unchanged.  Short and holds no other lock, so it cannot hold up the
     * railway.
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
     * Works out what every tile should show, reading the layout's own view of what is running rather than tracking
     * movements: accumulated state would drift, leaving a tile lit after its train has gone.
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

            // The whole run as one sequence of squares: a line has to know what is on either side of a square to be
            // drawn through it, and taken one edge at a time it would stop dead in the middle of the square where two
            // edges meet.
            List<TileKey> run = new java.util.ArrayList<>();
            List<State> states = new java.util.ArrayList<>();

            // What the run has given back behind the train is drawn as nothing (FR-106): the driven line says the track
            // is still held, and under non-atomic routes the tail hands it back as it clears it, under atomic routes
            // only when the run ends.
            Set<Edge> givenBack = layout.releasedBehind(entry.getKey());

            for (Edge edge : path)
            {
                if (edge == null || givenBack.contains(edge)) continue;

                ReducedEdge reduced = edgesByName.get(edge.getName());

                if (reduced == null) continue;

                // An edge counts as reached once the train has passed the point it ends at.  That is the
                // same rule the graph window colors by, so the two views cannot disagree.
                boolean reached = edge.getEnd() != null && reachedPoints.contains(edge.getEnd().getName());

                State state = reached ? State.REACHED : State.ACTIVE;

                // The Points at the ends are colored by whether the train has passed THEM, not by the
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

            // And the locomotive itself, at the point it has most recently reached, through the one rule:
            // `getLocomotiveLocation` returns an ARBITRARY one of the several points a running train reserves, so the
            // marker could jump ahead.  `Layout.whereTheTrainIs` takes the last milestone, with a stationary train's
            // single point as the fallback.
            Point at = layout.whereTheTrainIs(entry.getKey());

            // Whether it is actually running, which decides the icon (FR-027): asked of the LOCOMOTIVE, not the path,
            // since a train with a path can stand waiting at a platform or held while another route clears.
            boolean running = entry.getKey() != null && entry.getKey().getSpeed() > 0;

            if (at != null)
            {
                markTrain(overlays, at, running, running ? null : willStandOn(layout, entry.getKey()), entry.getKey());
            }
        }

        // And every train parked with no path: its locomotive icon on top of the station, the same icon as when a run
        // is started.
        markTheParkedTrains(overlays, layout, active.keySet());

        // everything held clear so those paths can run - and NOT what an edge the run has given back held clear:
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
     * Adds one square to a run, unless it is already the square the run is standing on: consecutive edges share the
     * Point between them, and a square listed twice is a line drawn from itself to itself, a blob in the middle of the
     * track.
     */
    public static void append(List<TileKey> run, List<State> states, TileKey tile, State state)
    {
        if (tile == null) return;

        if (!run.isEmpty() && tile.equals(run.get(run.size() - 1))) return;

        run.add(tile);
        states.add(state);
    }

    /**
     * Turns a run of squares into a line through each of them.  Which way the line enters and leaves is read off the
     * squares either side, exactly as the editor reads it for a tested path, so the two views draw the same picture of
     * the same question.
     *
     * Null at the ends of the run, where the line stops in the middle of the square rather than running off into track
     * nobody claimed, and either side of a jump between pages, which has no side on this grid.  Public so the geometry
     * can be tested without a railway.
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
     * facing the way it stands.  A train in `active` is left to the run's own mark at the point it last reached; the
     * several points a running train reserves all answer it as occupant, and marking those would draw it more than
     * once.
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

            // NOR ONE SETTING ITS ROUTE UP: every Point it has reserved answers it, and each was drawn as a train
            // parked there - it is marked once, waiting, where it stands, as a run marks a train holding a path
            if (layout.holdsAPath(standing))
            {
                claiming.add(standing);

                continue;
            }

            TileKey tile = pointTiles.get(point.getName());

            if (tile == null) continue;

            // GRAY WHERE IT IS PAUSED (FR-117)
            TileOverlay mark = TileOverlay.parked(facings.get(point.getName())).withPaused(standing.isAutonomyPaused());

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

            markTrain(into, at, false, willStandOn(layout, train), train);
        }

        // AND THOSE ON NO POINT, where the setup says they stand
        for (Map.Entry<TileKey, org.traincontrol.automationui.TilePorts.Side> train : onNoPoint.entrySet())
        {
            // GRAY WHERE IT IS PAUSED, as every other train is
            TileOverlay mark = TileOverlay.parked(train.getValue()).withPaused(pausedOnNoPoint.contains(train.getKey()));

            TileOverlay existing = into.get(train.getKey());

            into.put(train.getKey(), existing == null ? mark : existing.merge(mark));
        }
    }

    /**
     * The copy a train holding a path will be stood on once its run is done (OB-314), or null - asked of the running
     * layout, which this timer thread may find being replaced.
     *
     * @param layout the running layout
     * @param loc the train
     * @return the Point, or null
     */
    private static Point willStandOn(Layout layout, org.traincontrol.base.Locomotive loc)
    {
        try
        {
            return layout.copyItWillStandOn(loc);
        }
        catch (RuntimeException replaced)
        {
            return null;
        }
    }

    /**
     * Marks the tile a locomotive is standing on.  The running Layout knows a Point only by name, so the tile comes
     * from the index the builder's names produced.
     *
     * @param into the picture
     * @param at the Point the train is on
     * @param moving whether it is running
     * @param will the copy it will be stood on when its run is done, or null
     * @param train the train, whose icon is gray while it is paused (FR-117), or null where it is not known
     */
    private void markTrain(Map<TileKey, TileOverlay> into, Point at, boolean moving, Point will,
        org.traincontrol.base.Locomotive train)
    {
        if (at == null) return;

        TileKey tile = pointTiles.get(at.getName());

        if (tile == null) return;

        // Standing still, the locomotive standing there, facing its way, not the dot (OB-317): the path line under it
        // says it holds a path; running, it is the run's own icon.
        //
        // Facing the way it will stand (MT-701): a train that kept its direction at a square it may turn at stops on
        // the square's turning copy and is stood on the plain copy only once its run is done, so it faced the turning
        // copy's way until then.  The copy it will stand on, as the caption's arrow asks
        // (`AutonomySession.facingOnTheRailway`, OB-314), when that is another copy of the same square.
        Point facingCopy = will != null && will != at && will.isSamePlaceAs(at) ? will : at;

        org.traincontrol.automationui.TilePorts.Side facing = facings.get(facingCopy.getName());

        if (facing == null) facing = facings.get(at.getName());

        TileOverlay mark = (moving ? new TileOverlay(State.IDLE, true, true, null) : TileOverlay.parked(facing))
            .withPaused(train != null && train.isAutonomyPaused());

        TileOverlay existing = into.get(tile);

        into.put(tile, existing == null ? mark : existing.merge(mark));
    }
}
