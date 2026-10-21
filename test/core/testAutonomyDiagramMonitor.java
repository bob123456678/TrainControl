package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.DiagramMonitor;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.automationui.TileOverlay.State;
import org.traincontrol.automationui.TilePorts.Side;
import support.ProgramConstants;

/**
 * Turning what the railway is doing into what each tile shows.
 *
 * The interesting behaviour is not the colours, it is the bookkeeping: a tile lit after its train has
 * gone reads as a train that is still there, and a monitor that computes on the firing thread holds up
 * the trains rather than the drawing.  So these tests are about staleness and about who does the work.
 *
 * No Swing, no hardware - the monitor deliberately deals in a map of tile to overlay so it can be tested
 * without a screen.
 *
 * @author Adam
 */
public class testAutonomyDiagramMonitor
{

    /**
     * The icons each claim draws: FR-118's for a claim marked `@DrawsFR118Icons`, the classic ones - what
     * stations are drawn with - for every other.
     *
     * @param claim the claim about to run
     */
    @org.testng.annotations.BeforeMethod(alwaysRun = true)
    public void theIconsItDraws(java.lang.reflect.Method claim)
    {
        org.traincontrol.automationui.TileAnnotation.setStationIcons(claim.isAnnotationPresent(support.DrawsFR118Icons.class)
            ? org.traincontrol.automationui.TileAnnotation.StationIcons.SHAPES
            : org.traincontrol.automationui.TileAnnotation.StationIcons.CLASSIC);
    }

    /** And the classic icons again after it, whatever it set. */
    @org.testng.annotations.AfterMethod(alwaysRun = true)
    public void theClassicIconsAgain()
    {
        org.traincontrol.automationui.TileAnnotation.setStationIcons(null);
    }

    /**
     * A tile that qualifies twice shows the more urgent claim.
     *
     * Reached beats active, because the train has demonstrably been there; active beats locked, because a
     * claimed path says more than the fact that something else is being held clear.
     */
    @Test
    public void testTheMoreUrgentClaimWins()
    {
        assertEquals(merge(State.ACTIVE, State.REACHED).getState(), State.REACHED);
        assertEquals(merge(State.LOCKED, State.ACTIVE).getState(), State.ACTIVE);
        assertEquals(merge(State.IDLE, State.LOCKED).getState(), State.LOCKED);

        // and merging is not order dependent, or two tiles of the same edge could disagree
        assertEquals(merge(State.REACHED, State.ACTIVE).getState(),
                     merge(State.ACTIVE, State.REACHED).getState());
    }

    /**
     * A train mark survives being merged with anything, because it answers a different question from the
     * wash: which track is claimed, versus which part of it the train is on.
     */
    @Test
    public void testATrainMarkIsNotLostWhenClaimsMerge()
    {
        TileOverlay train = new TileOverlay(State.IDLE, true);
        TileOverlay claimed = new TileOverlay(State.ACTIVE, false);

        assertTrue(train.merge(claimed).hasTrain());
        assertTrue(claimed.merge(train).hasTrain());
        assertEquals(train.merge(claimed).getState(), State.ACTIVE);
    }

    /**
     * An idle tile with no train paints nothing at all.
     *
     * The common case by far, and it has to cost nothing: a running layout should show what is moving,
     * not tint every tile it owns.
     */
    @Test
    public void testAnIdleTilePaintsNothing()
    {
        assertTrue(new TileOverlay(State.IDLE, false).isBlank());
        assertFalse(new TileOverlay(State.IDLE, true).isBlank());
        assertFalse(new TileOverlay(State.LOCKED, false).isBlank());
    }

    /**
     * A run of squares becomes a line laid along the track, one segment per square.
     *
     * Which way it enters and leaves is read off the squares either side, so the middle of a run is a
     * line right across the square and the two ends stop in the middle of theirs - the train is not
     * coming from anywhere before the start, and the path does not continue past its destination.
     */
    @Test
    public void testARunBecomesALineThroughEachSquare()
    {
        Map<TileKey, TileOverlay> overlays = new LinkedHashMap<>();

        DiagramMonitor.lay(overlays,
            Arrays.asList(tile(0, 0), tile(1, 0), tile(2, 0)),
            Arrays.asList(State.REACHED, State.ACTIVE, State.ACTIVE));

        assertEquals(segment(overlays, tile(1, 0)).getFrom(), Side.W,
            "the middle of a run has to know where the line came from");

        assertEquals(segment(overlays, tile(1, 0)).getTo(), Side.E,
            "and where it goes, or there is no line and no arrow");

        assertNull(segment(overlays, tile(0, 0)).getFrom(),
            "a line running off the first square claims track ahead of the train");

        assertEquals(segment(overlays, tile(0, 0)).getTo(), Side.E);

        assertEquals(segment(overlays, tile(2, 0)).getFrom(), Side.W);

        assertNull(segment(overlays, tile(2, 0)).getTo(),
            "the destination is where the path stops");
    }

    /**
     * The colour changes where the train is, not where the edge is.
     *
     * Green behind, red ahead: the whole point of drawing the path rather than outlining it is that the
     * two halves are told apart at a glance.
     */
    @Test
    public void testTheLineIsColouredSquareBySquare()
    {
        Map<TileKey, TileOverlay> overlays = new LinkedHashMap<>();

        DiagramMonitor.lay(overlays,
            Arrays.asList(tile(0, 0), tile(1, 0), tile(2, 0)),
            Arrays.asList(State.REACHED, State.REACHED, State.ACTIVE));

        assertEquals(segment(overlays, tile(1, 0)).getState(), State.REACHED,
            "track the train has covered");

        assertEquals(segment(overlays, tile(2, 0)).getState(), State.ACTIVE,
            "and track it has not - the same line, in two colours");
    }

    /**
     * The line follows the track round a corner.
     *
     * Read off the neighbours rather than from the tile art, so a curve is entered by one side and left
     * by the next one round without this having to know what a curve looks like.
     */
    @Test
    public void testTheLineTurnsWithTheTrack()
    {
        Map<TileKey, TileOverlay> overlays = new LinkedHashMap<>();

        // west to east, then south
        DiagramMonitor.lay(overlays,
            Arrays.asList(tile(0, 0), tile(1, 0), tile(1, 1)),
            Arrays.asList(State.ACTIVE, State.ACTIVE, State.ACTIVE));

        assertEquals(segment(overlays, tile(1, 0)).getFrom(), Side.W);

        assertEquals(segment(overlays, tile(1, 0)).getTo(), Side.S,
            "the corner leaves by the side the next square is on, not by the one it came in on");
    }

    /**
     * A square the path crosses twice keeps both passes.
     *
     * A switch taken on the way out and again on the way round is two lines through one square.  Keeping
     * only the winning claim would draw a route that stops in the middle of the switch.
     */
    @Test
    public void testASquareCrossedTwiceKeepsBothPasses()
    {
        Map<TileKey, TileOverlay> overlays = new LinkedHashMap<>();

        DiagramMonitor.lay(overlays, Arrays.asList(tile(0, 0), tile(1, 0), tile(2, 0)),
            Arrays.asList(State.REACHED, State.REACHED, State.REACHED));

        // and again, the other way round, over the same switch
        DiagramMonitor.lay(overlays, Arrays.asList(tile(1, 1), tile(1, 0), tile(0, 0)),
            Arrays.asList(State.ACTIVE, State.ACTIVE, State.ACTIVE));

        assertEquals(overlays.get(tile(1, 0)).getSegments().size(), 2,
            "one of the two passes was drawn and the other lost");

        // identical passes are not two passes - two claims over the same track through the same sides
        DiagramMonitor.lay(overlays, Arrays.asList(tile(0, 0), tile(1, 0), tile(2, 0)),
            Arrays.asList(State.REACHED, State.REACHED, State.REACHED));

        assertEquals(overlays.get(tile(1, 0)).getSegments().size(), 2,
            "the same pass drawn twice is one line, not two");
    }

    /**
     * Edges meeting at a Point do not name that square twice.
     *
     * A run is built by concatenating edges, and consecutive edges share the Point between them.  Listed
     * twice, that square gets a line drawn from itself to itself - a blob in the middle of the track
     * where the two edges join.
     */
    @Test
    public void testTheSquareWhereTwoEdgesMeetIsListedOnce()
    {
        java.util.List<TileKey> run = new java.util.ArrayList<>();
        java.util.List<State> states = new java.util.ArrayList<>();

        DiagramMonitor.append(run, states, tile(0, 0), State.ACTIVE);
        DiagramMonitor.append(run, states, tile(1, 0), State.ACTIVE);
        DiagramMonitor.append(run, states, tile(1, 0), State.ACTIVE);
        DiagramMonitor.append(run, states, tile(2, 0), State.ACTIVE);

        assertEquals(run.size(), 3, "the shared Point was counted once per edge that touches it");

        assertEquals(states.size(), run.size(), "a square with no state is a line with no colour");
    }

    /**
     * A jump between pages has no side to be drawn as, and says so.
     *
     * A link is a hole in one page that comes out on another, so the two squares are not neighbours on
     * any grid.  The honest answer is a line that stops, which is what the train visibly does.
     */
    @Test
    public void testAJumpBetweenPagesHasNoSide()
    {
        assertNull(org.traincontrol.automationui.TileGraph.gridSideTowards(
            tile(0, 0), new TileKey("other", 1, 0)),
            "two pages were treated as one grid");

        assertNull(org.traincontrol.automationui.TileGraph.gridSideTowards(tile(0, 0), tile(1, 1)),
            "a diagonal is not a side");

        assertNull(org.traincontrol.automationui.TileGraph.gridSideTowards(tile(0, 0), tile(0, 0)),
            "a square is not beside itself");
    }

    /**
     * A square carrying a line is not blank, however it was reached.
     *
     * isBlank is what stops the common case costing anything, and a claim it does not recognise is a
     * square that quietly refuses to paint.
     */
    @Test
    public void testASquareWithALinePaints()
    {
        Map<TileKey, TileOverlay> overlays = new LinkedHashMap<>();

        DiagramMonitor.lay(overlays, Arrays.asList(tile(0, 0), tile(1, 0)),
            Arrays.asList(State.ACTIVE, State.ACTIVE));

        assertFalse(overlays.get(tile(0, 0)).isBlank());

        // The same square with a line but no STATE.  On an ACTIVE overlay alone the check could not fail: a state
        // that is not IDLE already paints, with or without segments.
        TileOverlay quiet = new TileOverlay(State.IDLE, false,
            Arrays.asList(new TileOverlay.Segment(Side.W, Side.E, State.IDLE)));

        assertFalse(quiet.isBlank(),
            "a square carrying a line reported itself blank, so paint() returned before drawing it - "
            + "while equals() counted the segments and forced the repaint anyway");
    }

    private static TileKey tile(int x, int y)
    {
        return new TileKey("main", x, y);
    }

    private static TileOverlay.Segment segment(Map<TileKey, TileOverlay> overlays, TileKey tile)
    {
        TileOverlay overlay = overlays.get(tile);

        assertNotNull(overlay, "nothing was drawn on " + tile);

        assertEquals(overlay.getSegments().size(), 1, "expected one pass through " + tile);

        return overlay.getSegments().get(0);
    }

    /**
     * Firing does no work.
     *
     * The layout fires from whichever thread moved a train, sometimes holding its own monitor.  If the
     * callback computed anything, a slow repaint would hold up the railway - so firing only sets a flag,
     * and nothing is published until something asks.
     *
     * Built with a real claimed path, the way testATrainOnAClaimedPathIsPublishedAndFollowedAsItMoves is, so there
     * is an actual picture that markDirty() must NOT have published before refreshIfDirty() is asked.  A
     * null LayoutSource would make compute() return at its null-layout check, and "published stays empty" would
     * hold whatever markDirty() did.
     */
    @Test
    public void testFiringOnlyMarksDirtyAndPublishesNothing() throws Exception
    {
        LayoutDiagram page = page("main", 8, 5);
        feedback(page, 1, 1, 22);
        straight(page, 2, 1);
        straight(page, 3, 1);
        straight(page, 4, 1);
        feedback(page, 5, 1, 24);

        GraphReducer reducer = reduce(graph(page));

        ReducedEdge west = edgeBetween(reducer, key("main", 1, 1), key("main", 5, 1));

        assertNotNull(west, "the fixture did not reduce to an edge, so there is nothing to light");

        Map<String, ReducedEdge> edges = new LinkedHashMap<>();
        Map<String, TileKey> tiles = new LinkedHashMap<>();

        Point west88 = new Point("West", false, null);
        Point east88 = new Point("East", false, null);

        Edge run = new Edge(west88, east88);

        edges.put(run.getName(), west);
        tiles.put("West", key("main", 1, 1));
        tiles.put("East", key("main", 5, 1));

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        StubLayout layout = new StubLayout();

        layout.active.put(locomotive(), Arrays.asList(run));
        layout.standingAt = west88;

        DiagramMonitor monitor = new DiagramMonitor(source(layout), edges, tiles,
            new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    published.add(overlays);
                }
            });

        for (int i = 0; i < 50; i++)
        {
            monitor.markDirty();
        }

        // MUTATION this catches: `public void markDirty() { dirty.set(true); refresh(); }` - computing
        // on the firing thread, which may be holding the layout's own monitor.
        assertTrue(published.isEmpty(), "firing must not publish, however many times it fires");

        // and a burst collapses into ONE recompute rather than fifty: the flag says something moved,
        // not how often, so the first call does the work and the second finds nothing to do
        assertTrue(monitor.refreshIfDirty(), "fifty firings should leave exactly one recompute owed");
        assertFalse(monitor.refreshIfDirty(), "and nothing owed after it");

        assertEquals(published.size(), 1,
            "the deferred recompute should have published the claimed path exactly once");
    }

    /**
     * Publishing is skipped when nothing has changed.
     *
     * Every publish repaints tiles, so a monitor that published an identical picture on every tick would
     * make a still layout as expensive as a moving one.
     */
    @Test
    public void testAnUnchangedPictureIsNotRepublished()
    {
        final int[] publishes = {0};

        DiagramMonitor monitor = new DiagramMonitor(
            new DiagramMonitor.LayoutSource()
            {
                @Override
                public org.traincontrol.automation.Layout get()
                {
                    return null;
                }
            },
            new LinkedHashMap<String, org.traincontrol.automationui.GraphReducer.ReducedEdge>(),
            new LinkedHashMap<String, TileKey>(),
            new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    publishes[0]++;
                }
            });

        monitor.refresh();
        monitor.refresh();
        monitor.refresh();

        assertEquals(publishes[0], 0,
            "an empty picture matches the empty starting state, so nothing is published");
    }

    /**
     * A view that has just been rebuilt has lost whatever it was showing, and the layout will not fire
     * about that - so there has to be a way to ask for the picture again.
     */
    @Test
    public void testThePictureCanBeAskedForAgainAfterAViewIsRebuilt()
    {
        DiagramMonitor monitor = new DiagramMonitor(
            new DiagramMonitor.LayoutSource()
            {
                @Override
                public org.traincontrol.automation.Layout get()
                {
                    return null;
                }
            },
            new LinkedHashMap<String, org.traincontrol.automationui.GraphReducer.ReducedEdge>(),
            new LinkedHashMap<String, TileKey>(),
            null);

        assertNotNull(monitor.getPublished(), "there is always a picture, even if it is empty");
        assertTrue(monitor.getPublished().isEmpty());
    }

    /**
     * invalidate() forgets the last published picture, so the next refresh() republishes even an unchanged one.
     * That matters after a view has been rebuilt and lost the picture the monitor thinks is still current: an
     * identical picture is news to the new view.  (The test above never calls it: it reads a freshly constructed
     * monitor, where "there is always a picture" is already true.)
     *
     * MUTATION this catches: make invalidate() a no-op (DiagramMonitor.java:118). A rebuilt diagram
     * would then stay blank until the next train moves, which is the defect invalidate() exists for.
     */
    @Test
    public void testInvalidateForcesTheNextRefreshToRepublish() throws Exception
    {
        LayoutDiagram page = page("main", 8, 5);
        feedback(page, 1, 1, 22);
        straight(page, 2, 1);
        straight(page, 3, 1);
        straight(page, 4, 1);
        feedback(page, 5, 1, 24);

        GraphReducer reducer = reduce(graph(page));

        ReducedEdge west = edgeBetween(reducer, key("main", 1, 1), key("main", 5, 1));

        assertNotNull(west, "the fixture did not reduce to an edge, so there is nothing to light");

        Map<String, ReducedEdge> edges = new LinkedHashMap<>();
        Map<String, TileKey> tiles = new LinkedHashMap<>();

        Point west88 = new Point("West", false, null);
        Point east88 = new Point("East", false, null);

        Edge run = new Edge(west88, east88);

        edges.put(run.getName(), west);
        tiles.put("West", key("main", 1, 1));
        tiles.put("East", key("main", 5, 1));

        final int[] publishes = { 0 };

        StubLayout layout = new StubLayout();

        layout.active.put(locomotive(), Arrays.asList(run));
        layout.standingAt = west88;

        DiagramMonitor monitor = new DiagramMonitor(source(layout), edges, tiles,
            new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    publishes[0]++;
                }
            });

        monitor.refresh();

        assertEquals(publishes[0], 1, "precondition: the claimed path is published once");

        // The control: an unchanged picture is not republished by refresh() alone - proves the count
        // above is not simply incrementing on every call regardless of invalidate().
        monitor.refresh();

        assertEquals(publishes[0], 1, "precondition: an unchanged picture must not be republished");

        // The view was rebuilt behind this class's back - it has lost the picture the monitor still
        // holds as "already published".
        monitor.invalidate();

        monitor.refresh();

        assertEquals(publishes[0], 2,
            "invalidate() should force the next refresh to republish, even though nothing about the "
            + "railway changed - to a freshly rebuilt view the same picture is news");
    }

    // The monitor actually running: a train on real track, and a picture published about it

    /**
     * A train on a claimed path is published as a lit line of squares, and it moves as the train does.
     *
     * The tests above hand the monitor a `LayoutSource` whose `get()` returns null, and `compute()` returns at its
     * null-layout check, so the milestone rule, the run concatenation, the location fallback and the lock wash
     * (the 128 lines the operator is actually watching) need a test of their own.
     * The railway here is real rather than mocked: two sensors with three plain squares between them,
     * reduced by the real GraphReducer into one edge each way, which is what gives the edge a genuine
     * list of squares to light.  Only the running Layout is a stand-in, because a real one needs
     * hardware to move a train along it - and the three methods `compute` asks it are exactly the three
     * overridden here, so what is faked is the railway's ANSWERS, not the monitor's work.
     *
     * Three pictures, in the order the operator sees them:
     *
     *   1. Path claimed, train has reached nothing: five squares, all ACTIVE, train mark on the square
     *      it is standing on.
     *   2. Train has reached the first Point: that square turns REACHED and the rest stay ACTIVE.
     *   3. Train has reached the far Point: the whole line is REACHED and the mark has moved to the
     *      far end.  The mark following the LAST milestone rather than `getLocomotiveLocation` is the
     *      point of the fallback, and this is the only test that reaches it.
     *
     * Mutations this must fail:
     *
     *   - `DiagramMonitor.refresh`, publish deleted (`if (publisher != null) publisher.publish(...)`
     *     removed): fails here and in the lock test.
     *   - `compute`, milestones ignored (`boolean reached = false`): fails here, on "the far
     *     end of a completed run should show as reached".
     */
    @Test
    public void testATrainOnAClaimedPathIsPublishedAndFollowedAsItMoves() throws Exception
    {
        LayoutDiagram page = page("main", 8, 5);
        feedback(page, 1, 1, 22);
        straight(page, 2, 1);
        straight(page, 3, 1);
        straight(page, 4, 1);
        feedback(page, 5, 1, 24);

        GraphReducer reducer = reduce(graph(page));

        ReducedEdge west = edgeBetween(reducer, key("main", 1, 1), key("main", 5, 1));

        assertNotNull(west, "the fixture did not reduce to an edge, so there is nothing to light");

        assertEquals(west.getPath().size(), 3,
            "the three plain squares should be inside the edge - they are what the line is drawn "
            + "along, and an edge with no path lights only its endpoints");

        // The two indexes the driver hands the monitor, here built by hand so the names are ours
        Map<String, ReducedEdge> edges = new LinkedHashMap<>();
        Map<String, TileKey> tiles = new LinkedHashMap<>();

        Point west88 = new Point("West", false, null);
        Point east88 = new Point("East", false, null);

        Edge run = new Edge(west88, east88);

        edges.put(run.getName(), west);
        tiles.put("West", key("main", 1, 1));
        tiles.put("East", key("main", 5, 1));

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        StubLayout layout = new StubLayout();

        layout.active.put(locomotive(), Arrays.asList(run));
        layout.standingAt = west88;

        DiagramMonitor monitor = new DiagramMonitor(source(layout), edges, tiles,
            new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    published.add(new LinkedHashMap<>(overlays));
                }
            });

        // 1. the path is claimed and the train has reached nothing yet
        monitor.refresh();

        assertEquals(published.size(), 1,
            "the monitor published nothing about a train standing on a claimed path.  Nothing on the "
            + "diagram moves until it does");

        Map<TileKey, TileOverlay> claimed = published.get(published.size() - 1);

        assertEquals(claimed.keySet(),
            new LinkedHashSet<>(Arrays.asList(key("main", 1, 1), key("main", 2, 1),
                key("main", 3, 1), key("main", 4, 1), key("main", 5, 1))),
            "the whole run, endpoints included, should be lit - not the endpoints alone and not the "
            + "track in between alone");

        for (TileKey tile : claimed.keySet())
        {
            assertEquals(claimed.get(tile).getState(), State.ACTIVE,
                tile + " should be claimed-but-not-reached until the train gets there");
        }

        assertTrue(claimed.get(key("main", 1, 1)).hasTrain(),
            "the square the train is standing on carries no train mark");

        assertFalse(claimed.get(key("main", 5, 1)).hasTrain(),
            "a train mark appeared on a square the train has not got to");

        // 2. the same picture again publishes nothing - every publish repaints
        monitor.refresh();

        assertEquals(published.size(), 1,
            "an identical picture was published twice, so a still layout repaints as often as a "
            + "moving one");

        // 3. the train reaches the near Point
        layout.milestones.add(west88);

        monitor.refresh();

        assertEquals(published.size(), 2, "the train moved and the diagram was not told");

        Map<TileKey, TileOverlay> partway = published.get(published.size() - 1);

        assertEquals(partway.get(key("main", 1, 1)).getState(), State.REACHED,
            "the square the train has passed should show as reached");

        assertEquals(partway.get(key("main", 3, 1)).getState(), State.ACTIVE,
            "the track ahead of the train is claimed, not reached");

        // 4. and the far Point.  The mark follows the LAST milestone, which is the whole reason the
        // location fallback exists: getLocomotiveLocation answers an arbitrary one of the several
        // Points a running train reserves, and would leave the mark behind
        layout.milestones.add(east88);

        monitor.refresh();

        assertEquals(published.size(), 3, "the train reached its destination and nothing was drawn");

        Map<TileKey, TileOverlay> arrived = published.get(published.size() - 1);

        assertEquals(arrived.get(key("main", 5, 1)).getState(), State.REACHED,
            "the far end of a completed run should show as reached");

        assertTrue(arrived.get(key("main", 5, 1)).hasTrain(),
            "the train mark did not follow the train to the end of its run");

        assertFalse(arrived.get(key("main", 1, 1)).hasTrain(),
            "the train mark was left behind on the square the train started from");
    }

    /**
     * Track held clear for somebody else's path is washed, and it is a different wash from the path.
     *
     * The other half of `compute`.  Two separate lines of track: a train claims
     * the first, and the second is on its path's lock list - which on a real layout is the track a
     * conflicting move would use.  The operator has to be able to tell "my train is going here" from
     * "this is being held clear so it can".
     *
     * Mutations this must fail: deleting the publish in `refresh`, as above; and separately, returning from
     * `compute` before the lock-wash loop at its end, which fails this test alone - so the wash is covered here and
     * nowhere else.
     */
    @Test
    public void testTrackHeldClearForARunIsWashedRatherThanClaimed() throws Exception
    {
        LayoutDiagram page = page("main", 8, 6);
        feedback(page, 1, 1, 22);
        straight(page, 2, 1);
        straight(page, 3, 1);
        feedback(page, 4, 1, 24);

        // A second line, not touching the first
        feedback(page, 1, 3, 26);
        straight(page, 2, 3);
        straight(page, 3, 3);
        feedback(page, 4, 3, 28);

        GraphReducer reducer = reduce(graph(page));

        ReducedEdge taken = edgeBetween(reducer, key("main", 1, 1), key("main", 4, 1));
        ReducedEdge held = edgeBetween(reducer, key("main", 1, 3), key("main", 4, 3));

        assertNotNull(taken, "the claimed line did not reduce to an edge");
        assertNotNull(held, "the held line did not reduce to an edge, so there is nothing to wash");

        Point a = new Point("A", false, null);
        Point b = new Point("B", false, null);
        Point c = new Point("C", false, null);
        Point d = new Point("D", false, null);

        Edge claimed = new Edge(a, b);
        Edge conflicting = new Edge(c, d);

        claimed.addLockEdge(conflicting);

        Map<String, ReducedEdge> edges = new LinkedHashMap<>();
        edges.put(claimed.getName(), taken);
        edges.put(conflicting.getName(), held);

        Map<String, TileKey> tiles = new LinkedHashMap<>();
        tiles.put("A", key("main", 1, 1));
        tiles.put("B", key("main", 4, 1));
        tiles.put("C", key("main", 1, 3));
        tiles.put("D", key("main", 4, 3));

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        StubLayout layout = new StubLayout();

        layout.active.put(locomotive(), Arrays.asList(claimed));
        layout.standingAt = a;

        DiagramMonitor monitor = new DiagramMonitor(source(layout), edges, tiles,
            new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    published.add(new LinkedHashMap<>(overlays));
                }
            });

        monitor.refresh();

        assertEquals(published.size(), 1, "nothing was published for a train with a locked path");

        Map<TileKey, TileOverlay> picture = published.get(0);

        for (TileKey tile : Arrays.asList(key("main", 1, 1), key("main", 2, 1), key("main", 3, 1),
            key("main", 4, 1)))
        {
            assertEquals(picture.get(tile).getState(), State.ACTIVE,
                tile + " is on the train's own path and should be claimed, not merely held clear");
        }

        for (TileKey tile : Arrays.asList(key("main", 1, 3), key("main", 2, 3), key("main", 3, 3),
            key("main", 4, 3)))
        {
            assertNotNull(picture.get(tile), tile + " is being held clear for a running train and "
                + "nothing was drawn on it, so the operator cannot see why it is unavailable");

            assertEquals(picture.get(tile).getState(), State.LOCKED,
                tile + " is held clear for somebody else's path, which is a different thing from "
                + "being on it");
        }
    }

    /**
     * A run is drawn in the diagram's palette (FR-106: the stations' blue for the path ahead, dark grey
     * for what the train has driven, the tail in orange), from the one place it is kept: `DiagramColours`.  The arrowheads
     * are white, to read on both.
     *
     * MUTATION: draw the path ahead in its old red, driven track in its old green, the arrowheads black, or white with
     * no dark edge, and this fails.
     */
    @Test
    public void testTheRunIsDrawnInTheDiagramsColours()
    {
        assertEquals(org.traincontrol.automationui.DiagramColours.PATH_AHEAD,
            org.traincontrol.automationui.DiagramColours.STATION, "the path ahead is not the stations' blue (FR-106)");

        int size = 60;

        for (State state : new State[] {State.ACTIVE, State.REACHED})
        {
            TileOverlay overlay = new TileOverlay(state, false,
                Arrays.asList(new TileOverlay.Segment(Side.W, Side.E, state)));

            java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = image.createGraphics();

            overlay.paint(g, size, size, null);

            g.dispose();

            java.awt.Color expected = state == State.ACTIVE ? org.traincontrol.automationui.DiagramColours.PATH_AHEAD
                : org.traincontrol.automationui.DiagramColours.PATH_DRIVEN;

            // on the line near the tile's west edge, clear of the arrowhead
            java.awt.Color drawn = new java.awt.Color(image.getRGB(4, size / 2), true);

            assertTrue(drawn.getAlpha() > 200 && Math.abs(drawn.getRed() - expected.getRed()) < 12
                && Math.abs(drawn.getGreen() - expected.getGreen()) < 12
                && Math.abs(drawn.getBlue() - expected.getBlue()) < 12, state + " is drawn in " + drawn + ", not "
                + expected + " (FR-106)");

            int white = 0;

            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    java.awt.Color p = new java.awt.Color(image.getRGB(x, y), true);

                    if (p.getAlpha() > 200 && p.getRed() > 230 && p.getGreen() > 230 && p.getBlue() > 230) white++;
                }
            }

            assertTrue(white > 0, "the arrowhead on a " + state + " run is not white, so it does not read on its line"
                + " (FR-106)");

            int edged = 0;

            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    java.awt.Color p = new java.awt.Color(image.getRGB(x, y), true);

                    if (p.getAlpha() > 200 && p.getRed() < 70 && p.getGreen() < 70 && p.getBlue() < 70) edged++;
                }
            }

            assertTrue(edged > 0, "the white arrowhead on a " + state + " run has no dark edge, so on a sensor's white"
                + " contact it disappears (RSA25-C4)");
        }
    }

    /**
     * The railway answers what a run has given back behind it (FR-106): the edges its tail has released early under
     * non-atomic routes, as a copy the diagram may keep - which is what the monitor leaves undrawn.
     *
     * MUTATION: answer nothing, or the railway's own set, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testTheRailwaySaysWhatARunHasGivenBack() throws Exception
    {
        org.traincontrol.automation.Layout railway = new org.traincontrol.automation.Layout(null);

        org.traincontrol.base.Locomotive train = locomotive();

        Edge behind = new Edge(new Point("Behind", false, null), new Point("Here", false, null));

        assertTrue(railway.releasedBehind(train).isEmpty(), "precondition: a train with no run has given something back");

        java.lang.reflect.Field released = org.traincontrol.automation.Layout.class.getDeclaredField("releasedEarly");

        released.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<org.traincontrol.base.Locomotive, java.util.Set<Edge>> early =
            (Map<org.traincontrol.base.Locomotive, java.util.Set<Edge>>) released.get(railway);

        java.util.Set<Edge> given = java.util.concurrent.ConcurrentHashMap.newKeySet();

        given.add(behind);

        early.put(train, given);

        java.util.Set<Edge> asked = railway.releasedBehind(train);

        assertEquals(asked, given, "the railway does not say what the run has given back behind the train (FR-106)");

        asked.clear();

        assertEquals(given.size(), 1, "the answer is the railway's own set, which the diagram could then empty");
    }

    /**
     * Driven track is drawn only while the run holds it (FR-106): under non-atomic routes the track behind a train
     * is given back as its tail clears it, and the driven line goes with it.  Under atomic routes nothing is given
     * back until the run ends, and the line stays.
     *
     * And the pale wash of what a given-back edge held clear goes with it: the railway released those claims
     * with the edge.
     *
     * MUTATION: draw every driven edge, given back or not, or wash what every edge held clear, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testDrivenTrackIsDrawnOnlyWhileItIsHeld() throws Exception
    {
        LayoutDiagram page = page("main", 8, 5);
        feedback(page, 1, 1, 22);
        straight(page, 2, 1);
        feedback(page, 3, 1, 23);
        straight(page, 4, 1);
        feedback(page, 5, 1, 24);

        // AND A TRACK BESIDE IT THAT THE FIRST EDGE HOLDS CLEAR
        feedback(page, 1, 3, 25);
        straight(page, 2, 3);
        feedback(page, 3, 3, 26);

        GraphReducer reducer = reduce(graph(page));

        ReducedEdge first = edgeBetween(reducer, key("main", 1, 1), key("main", 3, 1));
        ReducedEdge second = edgeBetween(reducer, key("main", 3, 1), key("main", 5, 1));
        ReducedEdge beside = edgeBetween(reducer, key("main", 1, 3), key("main", 3, 3));

        assertNotNull(first, "precondition: no edge from the first sensor to the second");
        assertNotNull(second, "precondition: no edge from the second sensor to the third");
        assertNotNull(beside, "precondition: no edge along the track beside it");

        Map<String, ReducedEdge> edges = new LinkedHashMap<>();
        Map<String, TileKey> tiles = new LinkedHashMap<>();

        Point a = new Point("A", false, null);
        Point b = new Point("B", false, null);
        Point c = new Point("C", false, null);

        Edge ab = new Edge(a, b);
        Edge bc = new Edge(b, c);

        Edge xy = new Edge(new Point("X", false, null), new Point("Y", false, null));

        ab.addLockEdge(xy);

        edges.put(ab.getName(), first);
        edges.put(bc.getName(), second);
        edges.put(xy.getName(), beside);
        tiles.put("A", key("main", 1, 1));
        tiles.put("B", key("main", 3, 1));
        tiles.put("C", key("main", 5, 1));

        StubLayout layout = new StubLayout();

        // THE TRAIN AT B, half way: A to B driven, B to C ahead
        layout.active.put(locomotive(), Arrays.asList(ab, bc));
        layout.milestones.add(a);
        layout.milestones.add(b);
        layout.standingAt = b;

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        DiagramMonitor monitor = new DiagramMonitor(source(layout), edges, tiles, new DiagramMonitor.Publisher()
        {
            @Override
            public void publish(Map<TileKey, TileOverlay> overlays)
            {
                published.add(new LinkedHashMap<>(overlays));
            }
        });

        // ATOMIC: nothing given back
        monitor.refresh();

        Map<TileKey, TileOverlay> held = published.get(published.size() - 1);

        assertTrue(held.containsKey(key("main", 2, 1)) && held.get(key("main", 2, 1)).getState() == State.REACHED,
            "precondition: the driven edge is not drawn as driven while the run holds it: " + held.get(key("main", 2, 1)));

        assertTrue(held.containsKey(key("main", 2, 3)) && held.get(key("main", 2, 3)).getState() == State.LOCKED,
            "precondition: the track the driven edge holds clear is not washed as held: " + held.get(key("main", 2, 3)));

        // NON-ATOMIC: A to B given back as the tail cleared it
        layout.released.add(ab);

        monitor.refresh();

        Map<TileKey, TileOverlay> givenBack = published.get(published.size() - 1);

        TileOverlay behind = givenBack.get(key("main", 2, 1));

        assertTrue(behind == null || behind.isBlank(), "track a non-atomic run has given back behind the train is still"
            + " drawn as driven (FR-106): " + behind);

        TileOverlay clear = givenBack.get(key("main", 2, 3));

        assertTrue(clear == null || clear.isBlank(), "the track a given-back edge held clear is still washed as held, though"
            + " the railway released it with the edge (RSA25-C2): " + clear);

        assertEquals(givenBack.get(key("main", 4, 1)).getState(), State.ACTIVE, "the track ahead of the train is not drawn"
            + " as ahead once the track behind is given back");

        assertTrue(givenBack.get(key("main", 3, 1)).hasTrain(), "the train is not drawn where it stands once the track"
            + " behind it is given back");
    }

    /**
     * A running Layout, stubbed down to the questions the monitor asks it.
     *
     * Not a mock of the monitor's own work: `compute` reads `getActiveLocomotives`,
     * `getReachedMilestones` and `getLocomotiveLocation` and nothing else from the layout, so these
     * three answers are the whole of the railway as far as it is concerned.  Getting a real Layout into
     * these states needs a train physically moving over sensors.
     */
    private static final class StubLayout extends org.traincontrol.automation.Layout
    {
        final Map<org.traincontrol.base.Locomotive, List<Edge>> active = new LinkedHashMap<>();

        final List<Point> milestones = new ArrayList<>();

        Point standingAt;

        final List<Point> points = new ArrayList<>();

        final java.util.Set<org.traincontrol.base.Locomotive> claiming = new java.util.HashSet<>();

        StubLayout()
        {
            super(null);
        }

        @Override
        public boolean holdsAPath(org.traincontrol.base.Locomotive loc)
        {
            return claiming.contains(loc);
        }

        @Override
        public java.util.Collection<Point> getPoints()
        {
            return points;
        }

        @Override
        public Map<org.traincontrol.base.Locomotive, List<Edge>> getActiveLocomotives()
        {
            return active;
        }

        @Override
        public List<Point> getReachedMilestones(org.traincontrol.base.Locomotive loc)
        {
            return milestones;
        }

        @Override
        public Point getLocomotiveLocation(org.traincontrol.base.Locomotive loc)
        {
            return standingAt;
        }

        final java.util.Set<Edge> released = new java.util.HashSet<>();

        @Override
        public java.util.Set<Edge> releasedBehind(org.traincontrol.base.Locomotive loc)
        {
            return released;
        }
    }

    private static DiagramMonitor.LayoutSource source(final org.traincontrol.automation.Layout layout)
    {
        return new DiagramMonitor.LayoutSource()
        {
            @Override
            public org.traincontrol.automation.Layout get()
            {
                return layout;
            }
        };
    }

    /**
     * A locomotive with no control station behind it - the monitor only ever uses it as a map key and
     * hands it back to the layout.
     */
    private static org.traincontrol.base.Locomotive locomotive()
    {
        return new org.traincontrol.marklin.MarklinLocomotive(null, 3,
            org.traincontrol.marklin.MarklinLocomotive.decoderType.MFX, "BR 89");
    }

    // track, built the way testAutonomyDiagramReducer builds it

    private LayoutDiagram page(String name, int sx, int sy)
    {
        return new LayoutDiagram(name, sx, sy, null, null);
    }

    private void straight(LayoutDiagram page, int x, int y) throws java.io.IOException
    {
        page.addComponent(componentType.STRAIGHT, x, y, 0, 0, 0, 0,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);
    }

    /**
     * A feedback tile lying east-west, which is how FEEDBACK is drawn at orientation 0.  The address is
     * the RAW one as it appears in a CS2 file; CS2File halves it for the logical address.
     */
    private void feedback(LayoutDiagram page, int x, int y, int rawAddress) throws java.io.IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, 0, 0, rawAddress / 2, rawAddress,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);
    }

    private TileGraph graph(LayoutDiagram... pages)
    {
        return new TileGraph(new ArrayList<>(Arrays.asList(pages)),
            java.util.Collections.<String>emptySet());
    }

    private GraphReducer reduce(TileGraph graph)
    {
        GraphReducer reducer = new GraphReducer(graph, null);
        reducer.reduce();
        return reducer;
    }

    private TileKey key(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    private ReducedEdge edgeBetween(GraphReducer reducer, TileKey start, TileKey end)
    {
        for (ReducedEdge edge : reducer.getEdges())
        {
            if (edge.getStart().equals(start) && edge.getEnd().equals(end)) return edge;
        }

        return null;
    }

    private TileOverlay merge(State a, State b)
    {
        return new TileOverlay(a, false).merge(new TileOverlay(b, false));
    }
    /**
     * The line at the END of a run stops on the rail, not in the middle of the square.
     *
     * OB-026: arriving at a curved station, the trace follows the shape of the station and does not draw a
     * straight line across the tile.
     *
     * A segment is drawn from the midpoint of the side it came in by to the midpoint of the side it
     * leaves by. At the end of a run there is no side it leaves by, so the line stops on the rail, not at the
     * tile's geometric centre - which is ON the rail for a straight and nowhere near it for a curve, where the
     * track cuts the corner and never passes through the middle.
     *
     * The tile here is the shape that broke: track entering at the TOP and leaving at the EAST, which is
     * the curve at `1 - Main:0,11` the picture was taken of. Its rail runs from (30,0) to (60,30), so
     * the point half way along it is (45,15). The tile centre, (30,30), is well clear of the rail - far
     * enough that a line reaching it cannot be mistaken for one that stopped on the track.
     *
     * Painted rather than computed, because "where does the line stop" is a question about the picture.
     */
    @Test
    public void testTheStubAtTheEndOfARunStopsOnTheRail()
    {
        int size = 60;

        TileOverlay overlay = new TileOverlay(State.ACTIVE, false,
            Arrays.asList(new TileOverlay.Segment(Side.N, null, State.ACTIVE)));

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        // The rail's own midpoint, which is what the label knows and the overlay did not.
        // Both passes, for the reason given on `painted` (OB-159).
        overlay.paint(g, size, size, new int[] {45, 15});
        overlay.paintTrain(g, size, size, new int[] {45, 15});

        g.dispose();

        assertTrue(painted(image, 45, 15), "nothing was drawn where the rail actually runs");

        // The stroke is a seventh of the tile with a round cap, so it reaches a few pixels past where
        // the line ends.  Anything as far down as the tile centre is the old straight-down stub.
        int lowest = -1;

        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                if (painted(image, x, y)) lowest = y;
            }
        }

        assertTrue(lowest < 26,
            "the line runs down to y=" + lowest + ", past the rail and towards the tile centre at "
            + "(30,30) - which is the straight chord across a curve that OB-026 reported");
    }

    /**
     * The locomotive icon on every orientation of a curve and a double curve runs along the train's own road, centred on
     * it, with its front towards the side the train is heading for and its roof never pointing down - and at the size a
     * straight gives it, not cut off at its tile (MT-642).  A straight, both ways and both orientations, is drawn as
     * before.
     *
     * Painted through the tile itself - `LayoutLabel.paintTrainOverCaptions`, the call the diagram's train pass makes -
     * onto a canvas three tiles wide with the tile in the middle, so what reaches past the tile is seen.  Compared with
     * the icon drawn from first principles rather than by the product's arithmetic: its front along the vector from the
     * middle of the train's road to the heading side's midpoint, its roof the perpendicular that points up the screen,
     * centred on that middle.  Each case is every movement a train makes over a square - parked facing the heading side,
     * running through, arriving (a run that ends here, with only the side it came in by) and departing (a run that starts
     * here, with only the side it leaves by) - on the two tile sizes the diagram draws, 30 and 60 pixels.
     *
     * MUTATION: draw the icon square to the tile on a curve, clip it to its tile, centre it on the station's road of a
     * double curve, mirror it the wrong way, or point an arriving train at the side opposite its entry, and this fails.
     */
    @Test
    public void testTheIconRunsAlongEveryCurveAndDoubleCurveOnItsOwnRoad() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            iconsOnEveryRoad(size);
        }
    }

    /**
     * The claim above, on one tile size.
     *
     * @param size the tile size
     * @throws Exception reading the icon
     */
    private static void iconsOnEveryRoad(final int size) throws Exception
    {

        java.awt.image.BufferedImage icon = javax.imageio.ImageIO.read(
            TileOverlay.class.getResource("/org/traincontrol/gui/resources/running_train.png"));

        assertNotNull(icon, "precondition: the locomotive icon cannot be read");

        Side[] round = {Side.N, Side.E, Side.S, Side.W};

        List<String> wrong = new ArrayList<>();

        int cases = 0;

        for (int turn = 0; turn < 4; turn++)
        {
            Side a = round[turn];
            Side b = round[(turn + 1) % 4];

            // THE CURVE: one road, a to b
            List<Side[]> curve = new ArrayList<>();
            curve.add(new Side[] {a, b});

            // THE DOUBLE CURVE: that road and the one at the opposite corner, the station on the first
            Side c = round[(turn + 2) % 4];
            Side d = round[(turn + 3) % 4];

            List<Side[]> doubleCurve = new ArrayList<>();
            doubleCurve.add(new Side[] {a, b});
            doubleCurve.add(new Side[] {c, d});

            for (List<Side[]> roads : Arrays.asList(curve, doubleCurve))
            {
                for (Side[] road : roads)
                {
                    for (int way = 0; way < 2; way++)
                    {
                        Side from = road[way];
                        Side heading = road[1 - way];

                        String name = (roads.size() == 1 ? "curve " : "double curve ") + roads.get(0)[0]
                            + "-" + roads.get(0)[1] + (roads.size() == 1 ? "" : " / " + roads.get(1)[0] + "-"
                            + roads.get(1)[1]) + ", on " + road[0] + "-" + road[1] + " heading " + heading;

                        for (Map.Entry<String, TileOverlay> train : everyMovement(from, heading).entrySet())
                        {
                            for (String arrows : ARROWS)
                            {
                                cases++;

                                String why = compareTheIcon(size, icon, roads, train.getValue(), road, heading, arrows);

                                if (why != null) wrong.add(name + ", " + train.getKey() + ", " + arrows + ": " + why);
                            }
                        }
                    }
                }
            }
        }

        // AND A STRAIGHT, as before: centred on the tile, square to it
        for (Side[] road : new Side[][] {{Side.W, Side.E}, {Side.N, Side.S}})
        {
            for (int way = 0; way < 2; way++)
            {
                Side from = road[way];
                Side heading = road[1 - way];

                for (Map.Entry<String, TileOverlay> train : everyMovement(from, heading).entrySet())
                {
                    for (String arrows : ARROWS)
                    {
                        cases++;

                        String why = compareTheIcon(size, icon, java.util.Collections.singletonList(road),
                            train.getValue(), road, heading, arrows);

                        if (why != null)
                        {
                            wrong.add("straight " + road[0] + "-" + road[1] + " heading " + heading + ", "
                                + train.getKey() + ", " + arrows + ": " + why);
                        }
                    }
                }
            }
        }

        // 4 orientations x (curve: 1 road + double curve: 2 roads) x 2 ways x 4 movements, and 2 straights x 2 x 4 - each
        // with the restriction arrows on, off, and off on a station
        assertEquals(cases, (4 * 3 * 2 * 4 + 2 * 2 * 4) * ARROWS.length, "precondition: not every case was drawn");

        assertTrue(wrong.isEmpty(), "the locomotive icon is not where and how it should be on a " + size + "-pixel tile"
            + " (MT-642):\n  " + String.join("\n  ", wrong));
    }

    /**
     * What the square's annotation says: with the restriction arrows shown, every road and the station's
     * badge; with them off, nothing at all on a sensor that is no station, and a station's badge on its first road alone.
     * The road the icon sits on is the square's own either way.
     */
    private static final String[] ARROWS = {"arrows on", "arrows off", "arrows off, a station"};

    /**
     * A sensor square of the shape these roads make - a straight, a curve or a double curve - in the orientation that
     * gives exactly these roads.
     */
    private static org.traincontrol.base.LayoutDiagramComponent squareOf(List<Side[]> roads) throws Exception
    {
        java.util.Set<java.util.Set<Side>> wanted = new java.util.HashSet<>();

        for (Side[] road : roads) wanted.add(java.util.EnumSet.of(road[0], road[1]));

        boolean curve = Math.abs(roads.get(0)[0].ordinal() - roads.get(0)[1].ordinal()) != 2;

        componentType type = roads.size() == 2 ? componentType.FEEDBACK_DOUBLE_CURVE
            : curve ? componentType.FEEDBACK_CURVE : componentType.FEEDBACK;

        for (int orientation = 0; orientation < 4; orientation++)
        {
            java.util.Set<java.util.Set<Side>> made = new java.util.HashSet<>();

            for (org.traincontrol.automationui.TilePorts.Route route
                : org.traincontrol.automationui.TilePorts.ports(type, orientation, 0))
            {
                made.add(java.util.EnumSet.of(route.getA(), route.getB()));
            }

            if (!made.equals(wanted)) continue;

            LayoutDiagram page = new LayoutDiagram("arrows", 3, 3, null, null);

            page.addComponent(type, 1, 1, orientation, 0, 64, 64,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

            return page.getComponent(1, 1);
        }

        throw new IllegalStateException("no orientation of " + type + " has the roads " + wanted);
    }

    /**
     * Every movement of a train over a square, from one side towards the other: parked facing the way it goes, running
     * through, arriving - a run that ends here - and departing, a run that starts here.
     */
    private static Map<String, TileOverlay> everyMovement(Side from, Side heading)
    {
        Map<String, TileOverlay> trains = new LinkedHashMap<>();

        trains.put("parked", TileOverlay.parked(heading));

        trains.put("running through", new TileOverlay(State.ACTIVE, true, true,
            Arrays.asList(new TileOverlay.Segment(from, heading, State.ACTIVE))));

        trains.put("arriving", new TileOverlay(State.ACTIVE, true, true,
            Arrays.asList(new TileOverlay.Segment(from, null, State.ACTIVE))));

        trains.put("departing", new TileOverlay(State.ACTIVE, true, true,
            Arrays.asList(new TileOverlay.Segment(null, heading, State.ACTIVE))));

        return trains;
    }

    /**
     * Paints one train through the tile and compares it with the icon drawn from first principles.
     *
     * @return why they differ, or null where they agree
     */
    private static String compareTheIcon(int size, java.awt.image.BufferedImage icon, List<Side[]> roads,
        TileOverlay train, Side[] road, Side heading, String arrows) throws Exception
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        for (Side[] each : roads) marks.add(new TileAnnotation.Mark(each[0], each[1], null));

        // the station, and so the badge, on the first road
        TileAnnotation.Badge badge =
            new TileAnnotation.Badge(true, false, false, false, true, roads.get(0)[0], roads.get(0)[1]);

        TileAnnotation annotation = "arrows on".equals(arrows) ? new TileAnnotation(marks, 0, false, badge, false)
            : "arrows off".equals(arrows) ? null
            : new TileAnnotation(new ArrayList<TileAnnotation.Mark>(), 0, false, badge, false);

        org.traincontrol.gui.LayoutLabel tile = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        // WITH THE ARROWS OFF, A SQUARE OF ITS OWN: the road from its ports, as the diagram's tiles have.  Set after the
        // build, which would otherwise decode its picture through the window this test does not open
        if (!"arrows on".equals(arrows))
        {
            java.lang.reflect.Field component = org.traincontrol.gui.LayoutLabel.class.getDeclaredField("component");

            component.setAccessible(true);
            component.set(tile, squareOf(roads));
        }

        tile.setBounds(size, size, size, size);
        tile.setAutonomyAnnotation(annotation);
        tile.setAutonomyOverlay(train);

        java.awt.image.BufferedImage drawn =
            new java.awt.image.BufferedImage(3 * size, 3 * size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = drawn.createGraphics();

        tile.paintTrainOverCaptions(g);

        g.dispose();

        // THE EXPECTED ICON, from first principles
        int[] ma = TileAnnotation.midpoint(road[0], size, size);
        int[] mb = TileAnnotation.midpoint(road[1], size, size);
        int[] to = TileAnnotation.midpoint(heading, size, size);

        int cx = (ma[0] + mb[0]) / 2;
        int cy = (ma[1] + mb[1]) / 2;

        // the direction exactly, the middle unrounded: on a 30-pixel tile the drawn middle is (22,22), the line's (22.5,22.5)
        double fx = to[0] - (ma[0] + mb[0]) / 2.0;
        double fy = to[1] - (ma[1] + mb[1]) / 2.0;
        double length = Math.hypot(fx, fy);

        fx /= length;
        fy /= length;

        // the roof: the perpendicular pointing up the screen; on a straight running up or down the page, the quarter
        // turn the diagram has always used - its roof to the west going north, to the east going south
        double ux, uy;

        if (Math.abs(fx) < 1e-9)
        {
            ux = fy < 0 ? -1 : 1;
            uy = 0;
        }
        else
        {
            ux = fy;
            uy = -fx;

            if (uy > 0 || (uy == 0 && ux > 0))
            {
                ux = -ux;
                uy = -uy;
            }
        }

        // ON A STRAIGHT HEADING WEST, mirrored: the roof stays up
        if (Math.abs(fy) < 1e-9 && fx < 0)
        {
            ux = 0;
            uy = -1;
        }

        int side = iconSideOn(size);

        java.awt.image.BufferedImage expected =
            new java.awt.image.BufferedImage(3 * size, 3 * size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D e = expected.createGraphics();

        e.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        e.translate(size + cx, size + cy);
        e.transform(new java.awt.geom.AffineTransform(fx, fy, -ux, -uy, 0, 0));
        e.drawImage(icon, -side / 2, -side / 2, side, side, null);
        e.dispose();

        int both = 0, either = 0, outside = 0, expectedOutside = 0;

        for (int y = 0; y < 3 * size; y++)
        {
            for (int x = 0; x < 3 * size; x++)
            {
                // THE ICON'S BODY, not its white rim or the halo round it: the halo draws white past the icon by
                // design, and the body is where the placement is
                boolean p = body(drawn.getRGB(x, y));
                boolean q = body(expected.getRGB(x, y));

                if (p && q) both++;
                if (p || q) either++;

                boolean beyond = x < size || y < size || x >= 2 * size || y >= 2 * size;

                if (p && beyond) outside++;
                if (q && beyond) expectedOutside++;
            }
        }

        if (either == 0) return "nothing was drawn";

        double overlap = both / (double) either;

        // THE SAME DRAWING, but for the smoothing at its edges: one turned four degrees off the rail - the rounded middle of
        // a curve on a 30-pixel tile - is a different one, and a looser bar let it through
        if (overlap >= 0.98) return null;

        return String.format("%.0f%% the same as expected (centred on (%d,%d), front towards %s, %d px); %d pixels drawn"
            + " past the tile where %d belong", overlap * 100, cx, cy, heading, side, outside, expectedOutside);
    }

    /**
     * Whether a pixel is the icon's dark body: painted, and darker than half way.
     *
     * @param argb the pixel
     * @return whether it is the body
     */
    private static boolean body(int argb)
    {
        return (argb >>> 24) >= 128 && (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3 < 128;
    }

    /**
     * A train coming to or leaving a curve repaints the tiles around it, not only its own (MT-642): its icon is not
     * clipped to its tile and reaches onto the next ones along the rail, so a repaint of the tile alone left the front
     * of the old icon behind on its neighbours.
     *
     * MUTATION: repaint only the tile itself, and this fails.
     */
    @Test
    public void testATrainLeavingACurveRepaintsWhereItsIconReached()
    {
        final int size = 60;

        javax.swing.JPanel grid = new javax.swing.JPanel(null);

        grid.setSize(3 * size, 3 * size);

        org.traincontrol.gui.LayoutLabel tile = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        tile.setBounds(size, size, size, size);
        tile.setAutonomyAnnotation(new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(Side.N, Side.E, null)), 0,
            false, new TileAnnotation.Badge(true, false, false, false, true, Side.N, Side.E), false));

        grid.add(tile);

        final List<java.awt.Rectangle> dirty = new ArrayList<>();

        javax.swing.RepaintManager was = javax.swing.RepaintManager.currentManager(grid);

        javax.swing.RepaintManager.setCurrentManager(new javax.swing.RepaintManager()
        {
            @Override
            public void addDirtyRegion(javax.swing.JComponent c, int x, int y, int w, int h)
            {
                dirty.add(javax.swing.SwingUtilities.convertRectangle(c, new java.awt.Rectangle(x, y, w, h), grid));

                super.addDirtyRegion(c, x, y, w, h);
            }
        });

        try
        {
            for (TileOverlay comesOrGoes : new TileOverlay[] {TileOverlay.parked(Side.E), null})
            {
                dirty.clear();

                tile.setAutonomyOverlay(comesOrGoes);

                // the tile and the half of each neighbour an icon on a curve can reach
                java.awt.Rectangle reach = new java.awt.Rectangle(size / 2, size / 2, 2 * size, 2 * size);

                boolean covered = false;

                for (java.awt.Rectangle r : dirty) covered |= r.contains(reach);

                assertTrue(covered, "a train " + (comesOrGoes == null ? "leaving" : "coming to") + " a curve repainted"
                    + " only " + dirty + ", not the neighbours its icon reaches onto (MT-642)");
            }

            // AND THE ANNOTATION CHANGED UNDER A TRAIN STANDING THERE: the restriction arrows turned off, then on
            tile.setAutonomyOverlay(TileOverlay.parked(Side.E));

            for (TileAnnotation changed : new TileAnnotation[] {null, new TileAnnotation(Arrays.asList(
                new TileAnnotation.Mark(Side.N, Side.E, null)), 0, false, null, false)})
            {
                dirty.clear();

                tile.setAutonomyAnnotation(changed);

                java.awt.Rectangle reach = new java.awt.Rectangle(size / 2, size / 2, 2 * size, 2 * size);

                boolean covered = false;

                for (java.awt.Rectangle r : dirty) covered |= r.contains(reach);

                assertTrue(covered, "the annotation of a curve with a train on it changed and repainted only " + dirty
                    + ", not the neighbours the train's icon reaches onto (RSA23-C1)");
            }
        }
        finally
        {
            javax.swing.RepaintManager.setCurrentManager(was);
        }
    }

    /**
     * Whether the overlay drew anything at this pixel.
     */
    private boolean painted(java.awt.image.BufferedImage image, int x, int y)
    {
        return (image.getRGB(x, y) >>> 24) > 0;
    }


    /**
     * The dot marking where the train is sits on the rail too.
     *
     * The star is centred on `trackCentre` (MT-057), and the RUNNING overlay draws its own mark, the dot that says
     * which square of a claimed path actually holds the train.  That dot is centred on `trackCentre` too (MT-117):
     * on a straight the two agree, but on a curve the badge and star sit on the corner the rail cuts, so a dot in
     * the middle of the square would be a mark beside its own station.
     *
     * Same cause as OB-026 and the same answer, one method along.
     */
    @Test
    public void testTheTrainDotSitsOnTheRail()
    {
        int size = 60;

        // No segments: the dot and the claim outline, nothing else to confuse the pixels
        TileOverlay overlay = new TileOverlay(State.ACTIVE, true, new ArrayList<TileOverlay.Segment>());

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        // Both passes, for the reason given on `painted` (OB-159).
        overlay.paint(g, size, size, new int[] {45, 15});
        overlay.paintTrain(g, size, size, new int[] {45, 15});

        g.dispose();

        assertTrue(painted(image, 45, 15), "the train dot is not on the rail");

        assertFalse(painted(image, 30, 30),
            "the train dot is still in the middle of the square, which on a curve is off the track "
            + "and away from the badge it is meant to mark");
    }

    /**
     * The half of the OB-026 fix that computes where the rail is.
     *
     * The two tests above hand the answer in - `paint(g, size, size, new int[] {45, 15})` - so they pin the
     * drawing and say nothing about the call that works out those numbers, which is `LayoutLabel`'s
     * `annotation.trackCentre(getWidth(), getHeight())`.
     *
     * `trackCentre` is the join. Given the badge's own two sides it returns their midpoint - the corner
     * the rails bend around on a curve - and falls back to the middle of the square when it has nothing
     * to go on, which is right for a straight and is also what it answers for a square it knows nothing
     * about.
     *
     * A 60-pixel tile whose track joins N to E: the two side midpoints are (30,0) and (60,30), so the
     * rail's midpoint is (45,15). The tile centre, (30,30), is well clear of it.
     */
    @Test
    public void testTheAnnotationPutsTheTrackCentreOnTheRailOfACurve()
    {
        int size = 60;

        TileAnnotation curve = new TileAnnotation(
            Arrays.asList(new TileAnnotation.Mark(Side.N, Side.E, null)), 0, false,
            new TileAnnotation.Badge(true, false, false, false, true, Side.N, Side.E), false);

        int[] centre = curve.trackCentre(size, size);

        assertEquals(centre[0], 45,
            "the track centre of an N-E curve is not on the rail. This is the number LayoutLabel hands "
            + "the overlay, so a run ending here draws its stub across the tile instead of along it "
            + "(OB-026, NR-9)");

        assertEquals(centre[1], 15, "the track centre of an N-E curve is not on the rail");
    }

    /**
     * A square nothing can use is a cross, not a square (OB-167).
     *
     * The badge grid answers two questions - does this square turn trains, and is it a station - and being
     * switched OFF is in neither.  It has a flag of its own, which is a different fact from "autonomy does not
     * choose this one": a station autonomy will not pick is still somewhere trains pass through and stop at.  A
     * reversing point that is switched off must not keep the mark for "every train turns here".
     *
     * Switching a reversing point off changes its badge, and switching a station off does too (the editor's "out of
     * service" leaves the station flag set, so a rule that skipped stations would miss almost every square the mode is
     * used on).  Both draw the same cross, neither draws a grey block, and parking alone does not draw it.
     *
     * MUTATION: make `Badge.isImpassable` answer false, or answer `parking`, and the first assertion fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testASquareNothingCanUseIsDrawnAsACross() throws Exception
    {
        int size = 40;

        // Not a station, every train turns, and switched off - Adam's case exactly.
        int shut = inkOf(badgeAt(false, true, true, size));
        int open = inkOf(badgeAt(false, true, false, size));

        assertNotEquals(shut, open,
            "switching a reversing point off does not change its badge at all, so a square no train "
            + "can enter goes on wearing the mark for 'every train turns here'");


        // A STATION THAT IS SWITCHED OFF IS A CROSS TOO.  The editor's "out of service" is the third of three radio
        // buttons and its handler deliberately leaves the station flag alone, so a square in that mode is usually
        // still flagged a station: a rule that excluded stations would exclude almost every square the mode is used on.
        assertNotEquals(inkOf(badgeAt(true, true, true, size)), inkOf(badgeAt(true, true, false, size)),
            "a station that is switched off draws the same mark as one in use, so the one state where "
            + "nothing can stop or pass looks exactly like the ordinary one");

        // THE SAME CROSS for a station and a passing point: the mark is about the square being out of use.
        assertEquals(orangeOf(badgeAt(true, true, true, size)), orangeOf(badgeAt(false, true, true, size)),
            size / 5.0, "a switched-off station and a switched-off passing point draw different crosses, though "
            + "nothing can use either - the mark is about the square being out of use");

        // ONE MARK FOR BOTH, AND NO GREY BLOCK, whatever type of station or point the square otherwise is
        // (`testASquareOutOfServiceIsAnOrangeRingedCircle`); grey is counted, not ink, which the white over a
        // sensor's contact adds to a station's as well
        assertEquals(greyOf(badgeAt(true, true, true, size)) + greyOf(badgeAt(false, true, true, size)), 0,
            "a square out of service still draws a grey block");

        // AND PARKING IS NOT SHUT.  The two flags have to be told apart, or `isImpassable()` returning
        // `parking` would pass every assertion above.  They share a colour and mean opposite things - a parking berth
        // is somewhere autonomy leaves alone and the operator uses freely; a shut square is one nothing may enter at
        // all.
        assertNotEquals(inkOf(badgeAt(false, true, true, false, size)),
            inkOf(badgeAt(false, true, false, true, size)),
            "a parking berth and a square nothing may use draw the same mark, so the drawing cannot "
            + "be reading the flag it claims to read");
    }

    /**
     * A square nothing can pass - drawn with an X - is orange, and a parking berth grey.
     *
     * Both answer "autonomy will not send a train here" (FR-103), but the X is orange, so a square shut to every
     * train reads apart from a berth autonomy merely does not choose.
     * Asked as the editor and the diagram build a switched-off square - parking AND shut, since a square nothing can
     * pass is also one autonomy does not choose - and with shut alone.
     *
     * MUTATION: let parking decide the colour first, or draw the X grey, and this fails.
     *
     * @throws Exception from painting
     */
    @Test
    public void testASquareNothingCanPassIsOrange() throws Exception
    {
        final int size = 40;

        // THE CROSS'S OWN COLOUR (FR-118): a station's cross is drawn over a grey block now, which outweighs it
        java.awt.Color shut = crossColour(plainBadge(true, true), size);
        java.awt.Color shutAlone = crossColour(plainBadge(false, true), size);
        java.awt.Color parking = markColour(plainBadge(true, false), size);
        java.awt.Color working = markColour(plainBadge(false, false), size);

        assertTrue(shut.getRed() > 200 && shut.getGreen() > 60 && shut.getGreen() < 150 && shut.getBlue() < 60,
            "a square nothing can pass is not drawn orange - Adam: \"make the nothing can pass (X stations) be orange"
            + " again\": " + shut);

        assertEquals(shutAlone, shut, "a square nothing can pass takes another colour when it is not also parking");

        assertNotEquals(shut, parking, "a square nothing can pass is drawn in the parking berth's grey");

        assertNotEquals(shut, working, "a square nothing can pass is drawn in the colour of one in use");
    }

    /**
     * A square autonomy will not choose - a parking berth - is drawn in a medium dark grey, not orange (FR-103).  One
     * switched off is orange again, the X - `testASquareNothingCanPassIsOrange`.
     *
     * Grey as the grey station labels are, and slightly darker than they are (MT-492).
     *
     * MUTATION: put the orange back, and this fails.
     *
     * @throws Exception from painting
     */
    @Test
    public void testASquareAutonomyWillNotChooseIsGrey() throws Exception
    {
        java.awt.Color parking = markColour(plainBadge(true, false), 40);

        int spread = Math.max(parking.getRed(), Math.max(parking.getGreen(), parking.getBlue()))
            - Math.min(parking.getRed(), Math.min(parking.getGreen(), parking.getBlue()));

        assertTrue(spread <= 16, "a square autonomy will not choose is not drawn grey (FR-103): " + parking);

        int label = org.traincontrol.gui.StationCaption.PILL_GREY.getRed();

        assertTrue(parking.getRed() < label && parking.getRed() >= label - 90, "a square autonomy will not choose is not"
            + " a medium dark grey just darker than the grey station labels (" + label + ") (FR-103): " + parking);
    }

    /**
     * The cross gets heavier as the tile gets bigger, the way the old reversing-point cross did.
     *
     * The cross is stroked at a seventh of its own size, as the old reversing-point cross was, so its arms and its
     * thickness both grow with the tile and it keeps its weight relative to everything around it.  A flat stroke
     * would thin it out at the sizes the diagram is drawn at while the badges beside it stay solid.
     *
     * MEASURED BY HOW THE MARK GROWS, not by a pixel width, because the width is an implementation
     * detail and the property is the proportion.  Both arms and thickness scale with the
     * tile, so the ink goes up with the SQUARE of the size; with a fixed stroke only the arms scale and
     * it goes up in step with it.  Doubling the tile therefore roughly quadruples the ink under the
     * rule and roughly doubles it without, and three times over is comfortably between the two.
     *
     * MUTATION: putting back the flat 2f gives a ratio near two and fails.
     */
    @Test
    public void testTheCrossKeepsItsWeightAsTheTileGrows() throws Exception
    {
        // A PASSING POINT'S, the cross alone: a station's sits on a grey block (FR-118), whose ink grows with the
        // square of the tile whatever the cross does
        int small = inkOf(badgeAt(false, true, true, 40));
        int large = inkOf(badgeAt(false, true, true, 80));

        assertTrue(small > 0, "the cross drew nothing at all at the smaller size");

        assertTrue(large > small * 3,
            "doubling the tile did not thicken the cross with it - its arms grew and its stroke did "
            + "not, so it thins out as the diagram gets bigger.  Ink " + small + " then " + large);
    }

    // FR-118: the station icons

    /**
     * A station is a rounded block, longer along its track than across it and no taller than the sensor's contact under
     * it (FR-118).  The contact is 14 pixels across at 30 and 26 at 60.
     *
     * MUTATION: draw the circle again, or square the block's corners, and this fails.
     *
     * @throws Exception from reading the tile art
     */
    @Test
    @support.DrawsFR118Icons
    public void testAStationIsARoundedBlock() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            java.awt.image.BufferedImage image = onItsSensor(station(Side.W, Side.E, false, false, false, false, null), size);

            java.awt.Rectangle blue = extent(image, BLUE);

            assertNotNull(blue, "no station drawn at " + size + " pixels");

            assertTrue(blue.height <= contact(size), "a station at " + size + " pixels is " + blue.height + " across its"
                + " track, taller than the sensor's contact (" + contact(size) + ") it stands on (FR-118)");

            assertTrue(blue.width >= blue.height * 1.3, "a station at " + size + " pixels is " + blue.width + " along its"
                + " track and " + blue.height + " across - not the block half as long again as it is tall (FR-118)");

            // rounded: the corners of what it covers are not blue, the middles of its four edges are
            int right = blue.x + blue.width - 1, bottom = blue.y + blue.height - 1;

            for (int[] corner : new int[][] {{blue.x, blue.y}, {right, blue.y}, {blue.x, bottom}, {right, bottom}})
            {
                assertFalse(BLUE.test(image.getRGB(corner[0], corner[1])), "a station at " + size + " pixels has a"
                    + " square corner at " + corner[0] + "," + corner[1] + " - D's block has rounded ones (FR-118)");
            }

            assertTrue(BLUE.test(image.getRGB(blue.x + blue.width / 2, blue.y))
                && BLUE.test(image.getRGB(blue.x, blue.y + blue.height / 2)), "precondition: the block's edges are not"
                + " blue at " + size + " pixels");
        }
    }

    /**
     * Where trains may turn, the plain hexagon: pointed at both ends, nothing in it, the same shape at 30 pixels as at 60
     * (FR-118).  Its points are measured from tip to the first column it fills across, as a share of its height - at
     * 30 and at 60 the same within a tenth or so.
     *
     * MUTATION: the more pointed 30-pixel hexagon (points two thirds of its height), or a glyph inside, fails this.
     *
     * @throws Exception from reading the tile art
     */
    @Test
    @support.DrawsFR118Icons
    public void testWhereTrainsMayTurnIsTheHexagon() throws Exception
    {
        double[] points = new double[2];

        int at = 0;

        for (int size : new int[] {30, 60})
        {
            java.awt.image.BufferedImage image = onItsSensor(station(Side.W, Side.E, true, true, false, false, null), size);

            java.awt.Rectangle blue = extent(image, BLUE);

            assertNotNull(blue, "no may-turn station drawn at " + size + " pixels");

            assertTrue(blue.height <= contact(size), "a may-turn station at " + size + " pixels is taller than the"
                + " sensor's contact");

            assertTrue(blue.width >= blue.height * 1.6, "a may-turn station at " + size + " pixels is " + blue.width
                + " along and " + blue.height + " across - not the long hexagon (FR-118)");

            int right = blue.x + blue.width - 1;

            assertTrue(blueAlong(image, blue.x, true) <= blue.height / 3 && blueAlong(image, right, true) <= blue.height / 3,
                "a may-turn station at " + size + " pixels is not pointed at both ends (FR-118)");

            int full = blue.x;

            while (full < right && blueAlong(image, full, true) < blue.height - 1) full++;

            points[at++] = (full - blue.x) / (double) blue.height;

            // plain: blue right across its middle, no glyph
            for (int x = blue.x + 3; x <= right - 3; x++)
            {
                assertTrue(BLUE.test(image.getRGB(x, size / 2)), "a may-turn station at " + size + " pixels has"
                    + " something drawn in it at " + x + " - the plain hexagon has nothing (FR-118)");
            }
        }

        assertEquals(points[0], points[1], 0.12, "a may-turn station's points are " + points[0] + " of its height at 30"
            + " pixels and " + points[1] + " at 60 - Adam: \"matching the 60px hexagon's shape as closely as possible\"");
    }

    /**
     * Where trains must turn, two terminus shapes back to back, pointing away from each other, with
     * the track's black line showing in the gap between them, and each with a WHITE bar (FR-118).  A one-pixel bar
     * set on a half pixel spreads over two columns and shows grey.
     *
     * MUTATION: narrow the gap so the outlines meet, or draw the 30-pixel bar inside its half's own frame, and this
     * fails.
     *
     * @throws Exception from reading the tile art
     */
    @Test
    @support.DrawsFR118Icons
    public void testWhereTrainsMustTurnIsTwoTerminiBackToBack() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            java.awt.image.BufferedImage image = onItsSensor(station(Side.W, Side.E, true, false, false, false, null), size);

            java.awt.Rectangle blue = extent(image, BLUE);

            assertNotNull(blue, "no must-turn station drawn at " + size + " pixels");

            int mid = size / 2, right = blue.x + blue.width - 1;

            // its length, no longer than the may-turn hexagon: `testTheTurningIconsAreTheLengthsAdamAskedFor` (MT-710)

            assertTrue(blue.height <= contact(size), "a must-turn station at " + size + " pixels is taller than the"
                + " sensor's contact");

            assertTrue(BLACK.test(image.getRGB(mid - 1, mid)) && BLACK.test(image.getRGB(mid, mid)), "a must-turn"
                + " station at " + size + " pixels shows no black line between its halves - Adam: \"60px lost the"
                + " black line in between\"");

            // each half: a point at its outer end, flat against the gap, and a white bar
            for (boolean left : new boolean[] {true, false})
            {
                int outer = left ? blue.x : right;

                int inner = left ? mid - 1 : mid;

                while (!BLUE.test(image.getRGB(inner, mid))) inner += left ? -1 : 1;

                assertTrue(blueAlong(image, outer, true) <= blue.height / 3, "the " + (left ? "left" : "right") + " half"
                    + " of a must-turn station at " + size + " pixels does not point away from the other (FR-118)");

                assertTrue(blueAlong(image, inner, true) >= blue.height - 2, "the " + (left ? "left" : "right") + " half"
                    + " of a must-turn station at " + size + " pixels is not flat against the gap (FR-118)");

                boolean bar = false;

                for (int x = Math.min(outer, inner); x <= Math.max(outer, inner); x++)
                {
                    boolean white = true;

                    for (int y = mid - 3; y <= mid + 2; y++) white &= PURE_WHITE.test(image.getRGB(x, y));

                    bar |= white;
                }

                assertTrue(bar, "the " + (left ? "left" : "right") + " half of a must-turn station at " + size
                    + " pixels has no white bar - Adam: \"should be white, not gray\"");
            }
        }
    }

    /**
     * A station whose track runs out on one side is a terminus, flat against that side with its bar there and pointed
     * the other way, whatever its turning setting (FR-118).  All four ways round, at both sizes.
     *
     * MUTATION: ignore the dead end, or turn the shape the wrong way, and this fails.
     *
     * @throws Exception from reading the tile art
     */
    @Test
    @support.DrawsFR118Icons
    public void testAStationAtADeadEndIsATerminusFacingIt() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            for (Side end : new Side[] {Side.W, Side.E, Side.N, Side.S})
            {
                boolean along = end == Side.W || end == Side.E;

                for (boolean[] turning : new boolean[][] {{false, false}, {true, true}, {true, false}})
                {
                    String which = (turning[0] ? turning[1] ? "a may-turn" : "a must-turn" : "a") + " station whose"
                        + " track runs out to the " + end + ", at " + size + " pixels,";

                    java.awt.image.BufferedImage image = onItsSensor(station(along ? Side.W : Side.N,
                        along ? Side.E : Side.S, turning[0], turning[1], false, false, end), size);

                    assertTerminusFacing(image, BLUE, end, which);
                }
            }
        }
    }

    /**
     * A parking berth is grey in whatever shape it has: the block on through track, the terminus at a dead end
     * (FR-118).  Parking is a colour, not a shape.
     *
     * MUTATION: give parking a shape of its own, or draw it blue, and this fails.
     *
     * @throws Exception from reading the tile art
     */
    @Test
    @support.DrawsFR118Icons
    public void testParkingIsGreyInItsOwnShape() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            java.awt.image.BufferedImage through = onItsSensor(station(Side.W, Side.E, false, false, true, false, null), size);

            java.awt.Rectangle grey = extent(through, GREY);

            assertNotNull(grey, "a parking berth at " + size + " pixels is not drawn grey");

            assertNull(extent(through, BLUE), "a parking berth at " + size + " pixels has blue in it");

            assertTrue(grey.width >= grey.height * 1.3 && grey.height <= contact(size), "a parking berth on through track"
                + " at " + size + " pixels is not the station's block");

            assertTerminusFacing(onItsSensor(station(Side.W, Side.E, false, false, true, false, Side.W), size), GREY,
                Side.W, "a parking berth at a dead end, at " + size + " pixels,");
        }
    }

    /**
     * Every square out of service is one mark - a white circle, an orange ring, an orange X in it - whatever it otherwise
     * is, a station of any kind or a plain sensor, on the track diagram and in the editor.  It is small enough that the
     * sensor activation arrows still show, and big enough to be prominent.  It replaces the orange X over a grey block
     * (FR-118) and the bare X on a plain sensor.
     *
     * Painted on white, on straight track and a curve, at both sizes: every kind draws the same picture; the mark is round,
     * orange-ringed with white inside it, at least the contact's size and no wider than the space inside the activation
     * arrows (16 pixels at 30, 31 at 60); and over the sensor's art as it is drawn while the sensor reads occupied, the
     * contact - its ring and its yellow - is covered and every pixel of its red activation arrows still shows.
     *
     * MUTATION: draw a station out of service as its own kind again, or the circle over the activation arrows, and this
     * fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    @support.DrawsFR118Icons
    public void testASquareOutOfServiceIsAnOrangeRingedCircle() throws Exception
    {
        Side[][] roads = {{Side.W, Side.E}, {Side.E, Side.S}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : roads)
            {
                Side a = road[0], b = road[1];

                TileAnnotation.Badge[] kinds = {
                    station(a, b, false, false, true, true, null),
                    station(a, b, true, true, true, true, null),
                    station(a, b, true, false, true, true, null),
                    station(a, b, false, false, true, true, a),
                    new TileAnnotation.Badge(false, false, false, true, false, a, b, false, true, null),
                    new TileAnnotation.Badge(false, false, true, true, false, a, b, true, true, null)};

                for (boolean editor : new boolean[] {false, true})
                {
                    java.awt.image.BufferedImage first = null;

                    for (TileAnnotation.Badge badge : kinds)
                    {
                        java.awt.image.BufferedImage image = outOfServiceOn(null, badge, road, size, editor);

                        if (first == null)
                        {
                            first = image;

                            continue;
                        }

                        for (int x = 0; x < size; x++)
                        {
                            for (int y = 0; y < size; y++)
                            {
                                assertEquals(image.getRGB(x, y), first.getRGB(x, y), badge + " out of service on " + a
                                    + "-" + b + " at " + size + " pixels" + (editor ? " in the editor" : "") + " is not"
                                    + " drawn as " + kinds[0] + " is, at " + x + "," + y + " - Adam: \"regardless of the"
                                    + " type of station or point it otherwise is\"");
                            }
                        }
                    }

                    String what = "a square out of service on " + a + "-" + b + " at " + size + " pixels"
                        + (editor ? " in the editor" : "");

                    java.awt.Rectangle ring = extent(first, ORANGE);

                    assertNotNull(ring, what + " has no orange");

                    assertTrue(Math.abs(ring.width - ring.height) <= 1, what + " is not round: " + ring);

                    assertTrue(ring.width >= contact(size) && ring.width <= (size < 60 ? 16 : 31), what + " is "
                        + ring.width + " across - at least the contact (" + contact(size) + "), and clear of the"
                        + " activation arrows (Adam: \"small enough that you can still see the sensor activation arrows,"
                        + " but big enough to be prominent\")");

                    assertEquals(inkOf(first, GREY) + inkOf(first, BLUE), 0, what + " still draws the grey block or a"
                        + " station's blue");

                    // white inside the ring, between the arms of its X
                    double cx = ring.x + ring.width / 2.0, cy = ring.y + ring.height / 2.0;

                    assertTrue(PURE_WHITE.test(first.getRGB((int) cx, (int) (cy - ring.height * 0.3)))
                        || PURE_WHITE.test(first.getRGB((int) (cx + ring.width * 0.3), (int) cy)), what
                        + " is not white inside its ring");

                    // over the sensor's art while it reads occupied
                    java.awt.image.BufferedImage art = activeArt(size, road);
                    java.awt.image.BufferedImage over = outOfServiceOn(art, kinds[0], road, size, editor);

                    int red = 0, covered = 0, contactLeft = 0;

                    double ax = (midpointOf(a, size)[0] + midpointOf(b, size)[0]) / 2.0;
                    double ay = (midpointOf(a, size)[1] + midpointOf(b, size)[1]) / 2.0;

                    for (int x = 0; x < size; x++)
                    {
                        for (int y = 0; y < size; y++)
                        {
                            int before = art.getRGB(x, y), after = over.getRGB(x, y);

                            if (ACTIVATION_RED.test(before))
                            {
                                red++;

                                if (!ACTIVATION_RED.test(after)) covered++;
                            }

                            boolean nearContact = Math.hypot(x + 0.5 - ax, y + 0.5 - ay) <= contact(size) / 2.0 - 1;

                            if (nearContact && (YELLOW.test(after) || BLACK.test(after) && acrossTheChord(
                                new double[] {x + 0.5, y + 0.5}, road, size) > size * 4 / 30.0 + 1)) contactLeft++;
                        }
                    }

                    assertTrue(red > 0, "precondition: the sensor's art at " + size + " pixels reading occupied has no"
                        + " activation arrows");

                    assertEquals(covered, 0, what + " covers " + covered + " of the " + red + " pixels of the sensor's"
                        + " activation arrows - Adam: \"small enough that you can still see the sensor activation"
                        + " arrows\"");

                    assertEquals(contactLeft, 0, what + " leaves " + contactLeft + " pixels of the contact showing"
                        + " inside it");
                }
            }
        }
    }

    /** The sensor's art as it is drawn while the sensor reads occupied, turned to run along the road, on white. */
    private static java.awt.image.BufferedImage activeArt(int size, Side[] road) throws Exception
    {
        boolean curve = Math.abs(road[0].ordinal() - road[1].ordinal()) != 2;

        java.awt.image.BufferedImage art = sensorArt(curve ? "s88_curve_active.gif" : "s88_active.gif", size);

        for (int k = 0; k < 4; k++)
        {
            java.awt.image.BufferedImage image = turned(art, k);

            if (runs(image, road)) return image;
        }

        throw new AssertionError("precondition: no quarter turn of the occupied sensor's art runs " + road[0] + "-"
            + road[1]);
    }

    /** A square out of service painted on this art, or on white, with a road one-way through it. */
    private static java.awt.image.BufferedImage outOfServiceOn(java.awt.image.BufferedImage art, TileAnnotation.Badge badge,
        Side[] road, int size, boolean editor)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            if (art != null) g.drawImage(art, 0, 0, null);

            boolean curve = Math.abs(road[0].ordinal() - road[1].ordinal()) != 2;

            TileAnnotation annotation = new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0], road[1],
                org.traincontrol.automationui.TileGraph.Direction.TOWARD_B)), -1, false, badge, false, curve, false, null,
                false, null);

            if (editor) annotation = annotation.inTheEditor();

            annotation.paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    /** The red of the activation arrows a sensor's art draws while it reads occupied. */
    private static final java.util.function.IntPredicate ACTIVATION_RED =
        rgb -> channel(rgb, 16) > 180 && channel(rgb, 8) < 80 && channel(rgb, 0) < 80;

    /** The yellow of a contact that reads occupied. */
    private static final java.util.function.IntPredicate YELLOW =
        rgb -> channel(rgb, 16) > 200 && channel(rgb, 8) > 200 && channel(rgb, 0) < 80;

    /**
     * Every station icon covers the sensor's own contact - the circle of the s88 tile, 14 pixels at 30 and 26 at 60 -
     * with white, the track's black band drawn back across it (FR-118).  The icons are no taller than the contact, so
     * they alone would leave its edges showing.
     * Painted over the real tile art; above and below the band, nothing of the contact's grey ring may remain.
     *
     * MUTATION: drop the cover, and this fails.
     *
     * @throws Exception from reading the tile art
     */
    @Test
    public void testAStationCoversTheSensorsContact() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            for (TileAnnotation.Badge badge : new TileAnnotation.Badge[] {
                station(Side.W, Side.E, false, false, false, false, null), station(Side.W, Side.E, true, false, false, false, null),
                station(Side.W, Side.E, true, true, false, false, null)})
            {
                java.awt.image.BufferedImage image = onItsSensor(badge, size);

                int c = contact(size), from = (size - c) / 2, band = size * 4 / 30;

                for (int x = from; x < from + c; x++)
                {
                    for (int y = from; y < from + c; y++)
                    {
                        if (Math.abs(y + 0.5 - size / 2.0) < band) continue;

                        int rgb = image.getRGB(x, y), r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

                        assertFalse(Math.max(r, Math.max(g, b)) < 215 && Math.max(r, Math.max(g, b))
                            - Math.min(r, Math.min(g, b)) < 40, "the s88's contact shows at " + x + "," + y + " round "
                            + badge + " at " + size + " pixels: " + new java.awt.Color(rgb) + " - Adam: \"make sure the"
                            + " white would cover an s88 circle\"");
                    }
                }
            }
        }
    }

    /**
     * The turning icons' lengths (MT-710): a may-turn hexagon is 1px shorter than `round(h * 1.9)` (25 at 30, 49 at 60)
     * in the 30px view and 2px shorter in the 60px view, a terminus is 1px shorter, and a must-turn pair, both halves
     * and the gap between them, is no longer than the may-turn hexagon.
     *
     * Measured along the middle of the track, painted eight times over, so the half pixel an antialiased edge would round
     * away is seen: the outline's length is the shape's plus its stroke, which runs on further at a point.
     *
     * MUTATION: put any of the three lengths back and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testTheTurningIconsAreTheLengthsAdamAskedFor()
    {
        for (int size : new int[] {30, 60})
        {
            // The icon's own geometry is the program's: Adam's deltas below ("1px", "2px") are the claim
            boolean large = size >= ProgramConstants.integer(TileAnnotation.class, "LARGE_TILE");

            int h = Math.round(size * (float) ProgramConstants.real(TileAnnotation.class, "ICON_SHARE"));

            int was = Math.round(h * (float) ProgramConstants.real(TileAnnotation.class, "ICON_LENGTH_RATIO"));

            double stroke = ProgramConstants.real(TileAnnotation.class,
                large ? "ICON_STROKE_LARGE" : "ICON_STROKE_SMALL");

            double smallPoint = ProgramConstants.real(TileAnnotation.class, "TERMINUS_POINT_SMALL");

            double may = lengthAlong(station(Side.W, Side.E, true, true, false, false, null), size);
            double must = lengthAlong(station(Side.W, Side.E, true, false, false, false, null), size);
            double terminus = lengthAlong(station(Side.W, Side.E, false, false, false, false, Side.W), size);

            // the outline's own length past each end: half the stroke at a flat end, more at a point, where the stroke's
            // mitre runs on - a point of length p on a shape h tall is an angle whose half has tan (h / 2) / p
            double pointed = stroke / 2 / Math.sin(Math.atan(h / 2.0 / (h / 2.0)));
            double terminusPoint = stroke / 2 / Math.sin(Math.atan(h / 2.0 / (large ? h / 2.0 : h * smallPoint)));

            assertEquals(may, was - (large ? 2 : 1) + 2 * pointed, 0.35, "a may-turn station at " + size + " pixels is "
                + may + " long, outline and all - Adam: \"Shorten may reverse stations by 1px in 30px view, 2px in 60px"
                + " view\"");

            assertEquals(terminus, was - 1 + stroke / 2 + terminusPoint, 0.35, "a terminus at " + size + " pixels is "
                + terminus + " long -"
                + " Adam: \"Make terminuses 1px shorter\"");

            assertTrue(must <= may + 0.25, "a must-turn station at " + size + " pixels is " + must + " long, its two"
                + " halves and the gap, against a may-turn's " + may + " - Adam: \"Make sure must reverses are"
                + " cumulatively no longer\"");
        }
    }

    /**
     * A station's icon on a curve lies wholly inside its square, at both sizes, whichever way the curve turns, on the
     * diagram and in the editor - every kind, where trains may and must turn included (MT-710).  A square's paint is
     * cut off at its edges, so ink the icon puts outside the square is ink the diagram
     * loses: painted here with nothing to cut it, on a canvas three squares across, nothing may land outside the
     * middle one.  On straight track too.
     *
     * With the classic icons as with FR-118's: a classic station's mark a pixel from the edge would put the outer half
     * of its outline past it.
     *
     * MUTATION: let the square's own paint of a curve's icon run past its edges - not cut off there, where the spill
     * draws the rest - and this fails.  So does clamping a classic station's mark a pixel from the edge again.
     */
    @Test
    public void testAStationOnACurveIsWhollyInsideItsSquare()
    {
        Side[][] roads = {{Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}, {Side.W, Side.E},
            {Side.N, Side.S}};

        // with the classic icons and with FR-118's alike
        for (TileAnnotation.StationIcons icons : TileAnnotation.StationIcons.values())
        {
            TileAnnotation.setStationIcons(icons);

            for (int size : new int[] {30, 60})
            {
                for (Side[] road : roads)
                {
                    TileAnnotation.Badge[] kinds = {
                        station(road[0], road[1], false, false, false, false, null),
                        station(road[0], road[1], true, true, false, false, null),
                        station(road[0], road[1], true, false, false, false, null),
                        station(road[0], road[1], false, false, false, false, road[0]),
                        station(road[0], road[1], false, false, false, false, road[1]),
                        station(road[0], road[1], false, false, true, true, null)};

                    for (TileAnnotation.Badge badge : kinds)
                    {
                        for (boolean editor : new boolean[] {false, true})
                        {
                            java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(size * 3, size * 3,
                                java.awt.image.BufferedImage.TYPE_INT_ARGB);

                            java.awt.Graphics2D g = canvas.createGraphics();

                            try
                            {
                                g.translate(size, size);

                                TileAnnotation annotation = new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0],
                                    road[1], null)), 0, false, badge, false, false, false, null, true, null);

                                (editor ? annotation.inTheEditor() : annotation).paintBadgeOverRun(g, size, size);

                                // and with the classic icons nothing is left for a spill to draw
                                if (icons == TileAnnotation.StationIcons.CLASSIC)
                                {
                                    assertFalse(annotation.spillsOverItsSquare(), badge + " on a road " + road[0] + "-"
                                        + road[1] + " at " + size + " pixels spills with the classic icons");
                                }
                            }
                            finally
                            {
                                g.dispose();
                            }

                            int outside = 0;

                            for (int x = 0; x < canvas.getWidth(); x++)
                            {
                                for (int y = 0; y < canvas.getHeight(); y++)
                                {
                                    boolean in = x >= size && x < 2 * size && y >= size && y < 2 * size;

                                    if (!in && (canvas.getRGB(x, y) >>> 24) > 40) outside++;
                                }
                            }

                            assertEquals(outside, 0, badge + " on a road " + road[0] + "-" + road[1] + " at " + size
                                + " pixels" + (editor ? " in the editor" : "") + " with the " + icons + " icons puts " + outside
                            + " pixels outside its"
                                + " square, where the diagram cuts it off - Adam: \"On curves, regular stations are now cut"
                                + " off in the corners\"");
                        }
                    }
                }
            }
        }
    }

    /**
     * A station's icon on a curve is its full size on its rails, across the middle of the curve's chord, every kind, at both
     * sizes, whichever way the curve turns: the square draws what falls inside it and the rest spills onto the squares
     * beside it, rather than made smaller until it fits or moved in off its rails.
     *
     * Painted as the diagram paints it: the square's own paint cut off at its edges, over the curved sensor's art, then
     * the spill over the squares around it.  The middle of the icon's colour lies on the chord, within a pixel at 30 and
     * a pixel and a half at 60 - along the chord a terminus's weight may lean to its flat end; across it nothing may - and
     * there is as much of that colour as the same kind shows on straight track, give or take what drawing it on the
     * slant softens: at least 85%.
     *
     * MUTATION: make a curve's icon smaller to fit again, or move it in off its rails, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    @support.DrawsFR118Icons
    public void testAStationOnACurveIsFullSizeOnItsRails() throws Exception
    {
        Side[][] curves = {{Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : curves)
            {
                TileAnnotation.Badge[] kinds = everyKind(road[0], road[1]);
                TileAnnotation.Badge[] straight = everyKind(Side.W, Side.E);

                for (int k = 0; k < kinds.length; k++)
                {
                    java.util.function.IntPredicate colour = k == 5 ? GREY : k == 6 ? GREY.or(ORANGE) : BLUE;

                    double[] middle = middleOf(withItsSpill(kinds[k], road, size), colour);

                    assertTrue(middle[2] > 0, "precondition: " + kinds[k] + " on a curve " + road[0] + "-" + road[1]
                        + " at " + size + " pixels painted nothing of its colour");

                    double across = acrossTheChord(new double[] {middle[0] - size, middle[1] - size}, road, size);

                    assertTrue(across <= (size >= 60 ? 1.5 : 1.0), kinds[k] + " on a curve " + road[0] + "-" + road[1]
                        + " at " + size + " pixels sits " + across + " pixels off the middle of its rails - moved off them,"
                        + " where Adam asked for it to spill over onto the squares beside it");

                    double flat = middleOf(withItsSpill(straight[k], new Side[] {Side.W, Side.E}, size), colour)[2];

                    assertTrue(middle[2] >= 0.85 * flat, kinds[k] + " on a curve " + road[0] + "-" + road[1] + " at "
                        + size + " pixels shows " + (int) middle[2] + " pixels of its colour against " + (int) flat
                        + " on straight track - made smaller, where Adam asked: \"make the stations spill over onto"
                        + " adjacent tiles ... much better than trying to reduce the size\"");
                }
            }
        }
    }

    /**
     * The spill is only what falls outside the square: the square's own paint has drawn the rest, and drawn twice its
     * soft edges would darken.  Every kind on every curve at both sizes, on the diagram and in the editor - where the icon
     * is on its rails too, white so the arrows read over it: nothing inside the square, and something outside
     * it for the plain station, whose block reaches past two edges.  Nothing at all on straight track.
     *
     * MUTATION: let the spill draw inside the square too, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testTheSpillIsOnlyWhatFallsOutsideTheSquare()
    {
        Side[][] roads = {{Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}, {Side.W, Side.E}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : roads)
            {
                boolean curve = !(road[0] == Side.W && road[1] == Side.E);

                TileAnnotation.Badge[] kinds = everyKind(road[0], road[1]);

                for (int k = 0; k < kinds.length; k++)
                {
                    for (boolean editor : new boolean[] {false, true})
                    {
                        java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(size * 3, size * 3,
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);

                        java.awt.Graphics2D g = canvas.createGraphics();

                        try
                        {
                            g.translate(size, size);

                            TileAnnotation annotation = new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0],
                                road[1], null)), -1, false, kinds[k], false, false, false, null, true, null);

                            (editor ? annotation.inTheEditor() : annotation).paintSpill(g, size, size);
                        }
                        finally
                        {
                            g.dispose();
                        }

                        int inside = 0, outside = 0;

                        for (int x = 0; x < canvas.getWidth(); x++)
                        {
                            for (int y = 0; y < canvas.getHeight(); y++)
                            {
                                if ((canvas.getRGB(x, y) >>> 24) == 0) continue;

                                if (x >= size && x < 2 * size && y >= size && y < 2 * size) inside++;
                                else outside++;
                            }
                        }

                        String what = kinds[k] + " on a road " + road[0] + "-" + road[1] + " at " + size + " pixels"
                            + (editor ? " in the editor" : "");

                        assertEquals(inside, 0, what + ": its spill drew " + inside + " pixels inside its own square,"
                            + " which the square has drawn already");

                        if (!curve) assertEquals(outside, 0, what + " spilled " + outside + " pixels, where it"
                            + " is drawn inside its square");
                        else if (k == 0) assertTrue(outside > 0, what + " spilled nothing - its block reaches past two"
                            + " edges of the square, and the spill is what draws that part (Adam: \"make the stations"
                            + " spill over onto adjacent tiles\")");
                    }
                }
            }
        }
    }

    /**
     * A badge painted as the diagram paints it, on a canvas three squares across with the square in the middle: the
     * square's own paint over its sensor's art, cut off at the square's edges, then its spill over the squares around.
     */
    private static java.awt.image.BufferedImage withItsSpill(TileAnnotation.Badge badge, Side[] road, int size)
        throws Exception
    {
        java.awt.image.BufferedImage square = overTheArt(badge, road, size);

        java.awt.image.BufferedImage canvas =
            new java.awt.image.BufferedImage(size * 3, size * 3, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = canvas.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);
            g.drawImage(square, size, size, null);
            g.translate(size, size);

            new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0], road[1], null)), -1, false, badge, false,
                false, false, null, true, null).paintSpill(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        return canvas;
    }

    /**
     * And the sensor's contact under it is covered, as on straight track (FR-118) - an icon made smaller on a curve
     * would otherwise sit inside the contact's ring.  Painted over the
     * curved sensor's own art, every kind at both sizes on every curve: no pixel the art draws dark near the contact and
     * off the rails - the ring - is dark any more.
     *
     * MUTATION: cover the contact on straight track only again, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    public void testTheContactIsCoveredOnACurve() throws Exception
    {
        Side[][] curves = {{Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : curves)
            {
                java.awt.image.BufferedImage art = curvedSensorArt(size, road);

                double cx = (midpointOf(road[0], size)[0] + midpointOf(road[1], size)[0]) / 2.0;
                double cy = (midpointOf(road[0], size)[1] + midpointOf(road[1], size)[1]) / 2.0;

                List<int[]> ring = new ArrayList<>();

                for (int x = 0; x < size; x++)
                {
                    for (int y = 0; y < size; y++)
                    {
                        if (BLACK.test(art.getRGB(x, y)) && Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= size * 0.3
                            && acrossTheChord(new double[] {x + 0.5, y + 0.5}, road, size) > size * 4 / 30.0 + 1)
                        {
                            ring.add(new int[] {x, y});
                        }
                    }
                }

                assertFalse(ring.isEmpty(), "precondition: the curved sensor's art at " + size + " pixels has no ring off"
                    + " its rails to cover");

                for (TileAnnotation.Badge badge : everyKind(road[0], road[1]))
                {
                    java.awt.image.BufferedImage image = overTheArt(badge, road, size);

                    int showing = 0;

                    for (int[] at : ring)
                    {
                        if (BLACK.test(image.getRGB(at[0], at[1]))) showing++;
                    }

                    assertEquals(showing, 0, badge + " on a curve " + road[0] + "-" + road[1] + " at " + size
                        + " pixels leaves " + showing + " of the sensor's ring's " + ring.size() + " pixels showing round"
                        + " it - Adam: \"make sure the white would cover an s88 circle\"");
                }
            }
        }
    }

    /** Every kind of station on a road from a to b: plain, may turn, must turn, a terminus each way, parking, shut. */
    private static TileAnnotation.Badge[] everyKind(Side a, Side b)
    {
        return new TileAnnotation.Badge[] {
            station(a, b, false, false, false, false, null),
            station(a, b, true, true, false, false, null),
            station(a, b, true, false, false, false, null),
            station(a, b, false, false, false, false, a),
            station(a, b, false, false, false, false, b),
            station(a, b, false, false, true, false, null),
            station(a, b, false, false, true, true, null)};
    }

    /**
     * A plain sensor's turn mark - the diamond where trains may turn, the square where they must - covers the sensor's
     * contact (a little bigger than it was).  Painted over the sensor's own art, on straight track and every curve, at both sizes: no pixel of
     * the contact's ring off the rails is dark any more, and the mark is at least as wide as the contact.
     *
     * MUTATION: leave the contact uncovered under a plain turn mark, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    @support.DrawsFR118Icons
    public void testAPlainTurnMarkCoversTheContact() throws Exception
    {
        Side[][] roads = {{Side.W, Side.E}, {Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : roads)
            {
                java.awt.image.BufferedImage art = overTheArt(null, road, size);

                double cx = (midpointOf(road[0], size)[0] + midpointOf(road[1], size)[0]) / 2.0;
                double cy = (midpointOf(road[0], size)[1] + midpointOf(road[1], size)[1]) / 2.0;

                List<int[]> ring = new ArrayList<>();

                for (int x = 0; x < size; x++)
                {
                    for (int y = 0; y < size; y++)
                    {
                        if (BLACK.test(art.getRGB(x, y)) && Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= size * 0.3
                            && acrossTheChord(new double[] {x + 0.5, y + 0.5}, road, size) > size * 4 / 30.0 + 1)
                        {
                            ring.add(new int[] {x, y});
                        }
                    }
                }

                assertFalse(ring.isEmpty(), "precondition: the sensor's art " + road[0] + "-" + road[1] + " at " + size
                    + " pixels has no ring off its rails to cover");

                for (boolean may : new boolean[] {true, false})
                {
                    TileAnnotation.Badge mark = new TileAnnotation.Badge(false, false, true, false, false, road[0], road[1],
                        may, false, null);

                    java.awt.image.BufferedImage image = overTheArt(mark, road, size);

                    int showing = 0;

                    for (int[] at : ring)
                    {
                        if (BLACK.test(image.getRGB(at[0], at[1]))) showing++;
                    }

                    String what = (may ? "the diamond where trains may turn" : "the square where trains must turn") + " on a"
                        + " plain sensor " + road[0] + "-" + road[1] + " at " + size + " pixels";

                    assertEquals(showing, 0, what + " leaves " + showing + " of the contact's ring pixels showing round it"
                        + " - Adam: \"Make the plain turn icon slightly bigger so it covers the circle.\"");

                    if (may)
                    {
                        java.awt.Rectangle blue = extent(image, BLUE);

                        assertTrue(blue != null && Math.max(blue.width, blue.height) >= contact(size), what + " is "
                            + (blue == null ? "not drawn" : blue.width + " wide") + ", narrower than the contact ("
                            + contact(size) + ")");
                    }
                }
            }
        }
    }

    /**
     * In the autonomy editor every icon is white with its colour round it - blue, grey for a berth autonomy does not
     * choose - so the arrows over it can be seen clearly.  Every named kind, on
     * straight track and a curve, at both sizes: in the editor the icon's colour is its outline, a small part of what the
     * track diagram fills with it; on the diagram it is filled as before.
     *
     * MUTATION: fill the editor's icons in their colour again, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testTheEditorsIconsAreWhiteWithTheirColourRound()
    {
        Side[][] roads = {{Side.W, Side.E}, {Side.E, Side.S}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : roads)
            {
                TileAnnotation.Badge[] kinds = everyKind(road[0], road[1]);

                // the middle of the icon: the middle of the road, on a canvas three squares across
                int cx = size + (midpointOf(road[0], size)[0] + midpointOf(road[1], size)[0]) / 2;
                int cy = size + (midpointOf(road[0], size)[1] + midpointOf(road[1], size)[1]) / 2;

                for (TileAnnotation.Badge badge : java.util.Arrays.copyOf(kinds, kinds.length - 1))
                {
                    java.util.function.IntPredicate colour = badge.isParking() ? GREY : BLUE;

                    java.awt.image.BufferedImage diagram = wholeIcon(badge, road, size, false);
                    java.awt.image.BufferedImage editor = wholeIcon(badge, road, size, true);

                    assertTrue(inkOf(editor, colour) > 0, "precondition: " + badge + " on " + road[0] + "-" + road[1]
                        + " at " + size + " pixels shows none of its colour in the editor");

                    String what = badge + " on " + road[0] + "-" + road[1] + " at " + size + " pixels";

                    // not the must-turn pair, whose middle is the gap between its halves and each half barred in its
                    // colour: its fill is the same `fill` as every other kind's
                    if (badge.isTerminus() && !badge.isOptional()) continue;

                    assertTrue(colour.test(diagram.getRGB(cx, cy)), "precondition: " + what + " on the track diagram is"
                        + " not filled in its colour at its middle: " + new java.awt.Color(diagram.getRGB(cx, cy)));

                    assertTrue(PURE_WHITE.test(editor.getRGB(cx, cy)), what + " in the editor is "
                        + new java.awt.Color(editor.getRGB(cx, cy)) + " at its middle, not white - Adam: \"Let's make all"
                        + " of the icons white with a blue border in the editor\"");
                }
            }
        }
    }

    /**
     * And on a curve the editor's icon is where the track diagram's is - on the rails, its full size, spilling onto the
     * squares beside it - not in the square's corner: white, the arrows read over it, which is what the corner was for.
     * Every kind on every curve at both sizes: the middle of the icon's colour, square and spill together, is the middle of
     * the curve's chord.
     *
     * MUTATION: put the editor's icon on a bend back in the corner, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testTheEditorsIconOnACurveIsOnItsRails()
    {
        Side[][] curves = {{Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : curves)
            {
                double cx = size + (midpointOf(road[0], size)[0] + midpointOf(road[1], size)[0]) / 2.0;
                double cy = size + (midpointOf(road[0], size)[1] + midpointOf(road[1], size)[1]) / 2.0;

                TileAnnotation.Badge[] kinds = everyKind(road[0], road[1]);

                for (TileAnnotation.Badge badge : java.util.Arrays.copyOf(kinds, kinds.length - 1))
                {
                    double[] middle = middleOf(wholeIcon(badge, road, size, true), badge.isParking() ? GREY : BLUE);

                    assertTrue(middle[2] > 0, "precondition: " + badge + " drew none of its colour");

                    assertTrue(Math.hypot(middle[0] - cx, middle[1] - cy) <= size / 8.0, badge + " on a curve " + road[0]
                        + "-" + road[1] + " at " + size + " pixels in the editor is centred at " + Math.round(middle[0])
                        + "," + Math.round(middle[1]) + ", not on its rails at " + cx + "," + cy);
                }
            }
        }
    }

    /**
     * The classic icons are the grid: a circle where trains do not turn, a
     * diamond where they may, a square where they must - and where a station's track runs out, whatever its turning
     * setting - on a station and on a sensor that is no station alike.  Drawn at 150 pixels a square, so the shape is read
     * off the picture without a pixel's doubt: inside its colour's own extent a square is filled to its corners, a circle
     * short of them but past a diamond's sides.
     *
     * MUTATION: draw a classic station where trains may turn as a square, and this fails.
     */
    @Test
    public void testTheClassicIconsAreTheGridAgain()
    {
        Side[] road = {Side.W, Side.E};

        for (boolean isStation : new boolean[] {true, false})
        {
            String what = isStation ? "a station" : "a sensor that is no station";

            assertEquals(classicShape(new TileAnnotation.Badge(isStation, false, false, false, true, road[0], road[1], false,
                false, null)), "circle", what + " where trains do not turn - Adam: \"large circles for normal stations\"");

            assertEquals(classicShape(new TileAnnotation.Badge(isStation, true, false, false, true, road[0], road[1], true,
                false, null)), "diamond", what + " where trains may turn - Adam: \"diamond for reversing\"");

            assertEquals(classicShape(new TileAnnotation.Badge(isStation, true, false, false, true, road[0], road[1], false,
                false, null)), "square", what + " where trains must turn - Adam: \"square for terminus\"");
        }

        for (boolean may : new boolean[] {false, true})
        {
            assertEquals(classicShape(new TileAnnotation.Badge(true, may, false, false, true, road[0], road[1], may, false,
                Side.W)), "square", "a station whose track runs out" + (may ? ", where trains may turn," : "") + " is not"
                + " the terminus's square (FR-118: a station with one way out is a terminus)");
        }
    }

    /** The classic shape a named badge is drawn as, read off a square 150 pixels across. */
    private static String classicShape(TileAnnotation.Badge badge)
    {
        int size = 150;

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(badge.getA(), badge.getB(), null)), -1, false, badge,
                false, false, false, null, true, null).paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        java.awt.Rectangle fill = extent(image, BLUE);

        assertNotNull(fill, "precondition: " + badge + " drew none of its colour");

        double cx = fill.x + (fill.width - 1) / 2.0, cy = fill.y + (fill.height - 1) / 2.0, r = fill.width / 2.0;

        boolean inside = BLUE.test(image.getRGB((int) Math.round(cx - 0.6 * r), (int) Math.round(cy - 0.6 * r)));
        boolean corner = BLUE.test(image.getRGB((int) Math.round(cx - 0.9 * r), (int) Math.round(cy - 0.9 * r)));

        return corner ? "square" : inside ? "circle" : "diamond";
    }

    /**
     * A classic station's diamond on a curve sits on its rails, a little smaller there: the middle of its colour
     * within a pixel and a half of the middle of the curve's chord, every way a curve turns, at both sizes - clamped to
     * keep it inside its square it would sit 3 pixels in at 30 and 5 at 60, toward the inside of the curve.  And it is
     * still bigger than a sensor's diamond on the same curve.
     *
     * MUTATION: let a classic station's diamond on a curve be clamped like any other mark again, and this fails.
     */
    @Test
    public void testAClassicStationsDiamondOnACurveIsOnItsRails()
    {
        Side[][] curves = {{Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : curves)
            {
                TileAnnotation.Badge station = new TileAnnotation.Badge(true, true, false, false, true, road[0], road[1],
                    true, false, null);
                TileAnnotation.Badge sensor = new TileAnnotation.Badge(false, true, false, false, true, road[0], road[1],
                    true, false, null);

                java.awt.Rectangle fill = extent(paintedOn(station, size), BLUE);

                assertNotNull(fill, "precondition: a may-turn station on " + road[0] + "-" + road[1] + " at " + size
                    + " pixels drew none of its colour");

                double[] chord = {(midpointOf(road[0], size)[0] + midpointOf(road[1], size)[0]) / 2.0,
                    (midpointOf(road[0], size)[1] + midpointOf(road[1], size)[1]) / 2.0};

                double cx = fill.x + (fill.width - 1) / 2.0, cy = fill.y + (fill.height - 1) / 2.0;

                assertTrue(Math.abs(cx - chord[0]) <= 1.5 && Math.abs(cy - chord[1]) <= 1.5, "a may-turn station on "
                    + road[0] + "-" + road[1] + " at " + size + " pixels has its diamond's middle at " + cx + "," + cy
                    + ", off its rails, whose middle is " + chord[0] + "," + chord[1] + " - Adam: \"make sure that the"
                    + " diamonds are correctly aligned on curves\"");

                java.awt.Rectangle small = extent(paintedOn(sensor, size), BLUE);

                assertNotNull(small, "precondition: a may-turn sensor on " + road[0] + "-" + road[1] + " at " + size
                    + " pixels drew none of its colour");

                assertTrue(fill.width > small.width, "a may-turn station's diamond on " + road[0] + "-" + road[1] + " at "
                    + size + " pixels is " + fill.width + " across, no bigger than a sensor's, " + small.width + " - Adam:"
                    + " \"larger if station, smaller if not\"");
            }
        }
    }

    /**
     * The classic icon sizes: a station's mark 14 pixels across at 30 and 28 at 60, a pixel under the badges of FR-118;
     * a sensor's 10 and 20, the inside of its contact's ring - drawn, its outline's outer half and all, a pixel more:
     * 15 and 29, 11 and 21, the circle and the square alike.  A diamond is drawn bigger than the circle beside it, and
     * a station's bigger than a sensor's.
     *
     * MUTATION: draw a classic station's mark a pixel smaller again, and this fails.
     */
    @Test
    public void testTheClassicIconsAreTheSizesAdamAskedFor()
    {
        for (int size : new int[] {30, 60})
        {
            int big = size == 30 ? 15 : 29, small = size == 30 ? 11 : 21;

            for (boolean isStation : new boolean[] {true, false})
            {
                int expected = isStation ? big : small;

                String what = (isStation ? "a station's" : "a sensor's") + " classic mark at " + size + " pixels";

                assertEquals(ringAcross(isStation, false, false, size), expected, what + ", a circle, is drawn this wide -"
                    + " Adam: " + (isStation ? "\"same as before, just ever slightly smaller\""
                    : "\"match radius of s88, so 2px more diameter\""));

                assertEquals(ringAcross(isStation, true, false, size), expected, what + ", a square, is not the circle's"
                    + " width");

                assertTrue(ringAcross(isStation, true, true, size) > expected, what + ", a diamond, is no bigger than the"
                    + " circle beside it");
            }

            assertTrue(ringAcross(true, true, true, size) > ringAcross(false, true, true, size), "a station's diamond at "
                + size + " pixels is no bigger than a sensor's - Adam: \"larger if station, smaller if not\"");
        }
    }

    /** How wide an unnamed classic mark's ring is drawn on straight track, in solid blue. */
    private static int ringAcross(boolean isStation, boolean turns, boolean may, int size)
    {
        TileAnnotation.Badge badge = new TileAnnotation.Badge(isStation, turns, false, false, false, Side.W, Side.E,
            turns && may, false, null);

        java.awt.Rectangle ring = extent(paintedOn(badge, size), BLUE);

        assertNotNull(ring, "precondition: " + badge + " at " + size + " pixels drew none of its colour");

        assertEquals(ring.width, ring.height, "precondition: " + badge + " at " + size + " pixels is not as tall as it is"
            + " wide");

        return ring.width;
    }

    /** A badge painted alone on a square of white, on straight track. */
    private static java.awt.image.BufferedImage paintedOn(TileAnnotation.Badge badge, int size)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(badge.getA(), badge.getB(), null)), -1, false, badge,
                false, false, false, null, true, null).paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    /**
     * With the classic icons a square out of service is an orange sign at a station's size on a station and a smaller one
     * on a sensor that is no station: 14 pixels across at 30 and 28 at 60 on a station, its mark's size, and
     * 12 and 24 on a sensor.  A station's is solid orange with a white X; a sensor's keeps the white circle, ringed
     * in orange, with an orange X.
     *
     * MUTATION: draw every classic square out of service at FR-118's one size, or a station's white again, and this fails.
     */
    @Test
    public void testTheClassicOutOfServiceSignIsAStationsOrASensors()
    {
        for (int size : new int[] {30, 60})
        {
            for (boolean isStation : new boolean[] {true, false})
            {
                TileAnnotation.Badge badge = new TileAnnotation.Badge(isStation, false, false, true, false, Side.W, Side.E,
                    false, true, null);

                java.awt.Rectangle sign = extent(paintedOn(badge, size), ORANGE);

                assertNotNull(sign, "precondition: a " + (isStation ? "station" : "sensor") + " out of service at " + size
                    + " pixels drew no orange");

                int expected = isStation ? (size == 30 ? 14 : 28) : (size == 30 ? 12 : 24);

                String what = "a " + (isStation ? "station" : "sensor that is no station") + " out of service at " + size
                    + " pixels";

                assertEquals(sign.width, expected, what + " has its sign this wide - Adam: " + (isStation
                    ? "\"larger (same size a normal station) if a station\""
                    : "\"1px more radius on the non station out of service\""));

                // between the X's arms, on the level with its middle: orange on a station's, white inside a sensor's ring
                java.awt.image.BufferedImage image = paintedOn(badge, size);

                double cx = sign.x + (sign.width - 1) / 2.0, cy = sign.y + (sign.height - 1) / 2.0;

                int between = image.getRGB((int) Math.round(cx + 0.3 * sign.width), (int) Math.round(cy));
                int middle = image.getRGB((int) Math.round(cx), (int) Math.round(cy));

                java.util.function.IntPredicate whitish = rgb -> channel(rgb, 16) > 200 && channel(rgb, 8) > 200
                    && channel(rgb, 0) > 200;

                if (isStation)
                {
                    assertTrue(ORANGE.test(between), what + " is " + new java.awt.Color(between) + " between its X's"
                        + " arms, not solid orange - Adam: \"give the station out of service a solid orange fill\"");

                    assertTrue(whitish.test(middle), what + " has an X that is " + new java.awt.Color(middle) + ", not"
                        + " white - Adam: \"with a white X inside instead\"");
                }
                else
                {
                    assertTrue(PURE_WHITE.test(between), what + " is " + new java.awt.Color(between) + " between its X's"
                        + " arms, not the white inside its orange ring");

                    assertTrue(ORANGE.test(middle), what + " has an X that is " + new java.awt.Color(middle) + ", not"
                        + " orange");
                }
            }
        }
    }

    /**
     * With the classic icons the autonomy editor draws every mark exactly as the track diagram does: every kind, a
     * station's and a sensor's, named and not, on
     * straight track and on a curve, at both sizes, the same pixels.
     *
     * MUTATION: draw the editor's marks white again with the classic icons, and this fails.
     */
    @Test
    public void testTheClassicIconsAreTheSameInTheEditor()
    {
        Side[][] roads = {{Side.W, Side.E}, {Side.E, Side.S}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : roads)
            {
                java.util.List<TileAnnotation.Badge> kinds = new java.util.ArrayList<>(Arrays.asList(everyKind(road[0],
                    road[1])));

                for (boolean named : new boolean[] {true, false})
                {
                    kinds.add(new TileAnnotation.Badge(true, false, false, false, named, road[0], road[1], false, false,
                        null));
                    kinds.add(new TileAnnotation.Badge(false, false, false, false, named, road[0], road[1], false, false,
                        null));
                    kinds.add(new TileAnnotation.Badge(false, true, false, false, named, road[0], road[1], true, false,
                        null));
                    kinds.add(new TileAnnotation.Badge(false, true, false, false, named, road[0], road[1], false, false,
                        null));
                    kinds.add(new TileAnnotation.Badge(false, false, false, true, named, road[0], road[1], false, true,
                        null));
                }

                for (TileAnnotation.Badge badge : kinds)
                {
                    java.awt.image.BufferedImage diagram = wholeIcon(badge, road, size, false);
                    java.awt.image.BufferedImage editor = wholeIcon(badge, road, size, true);

                    int differ = 0;

                    for (int x = 0; x < diagram.getWidth(); x++)
                    {
                        for (int y = 0; y < diagram.getHeight(); y++)
                        {
                            if (diagram.getRGB(x, y) != editor.getRGB(x, y)) differ++;
                        }
                    }

                    assertEquals(differ, 0, badge + " on " + road[0] + "-" + road[1] + " at " + size + " pixels is drawn"
                        + " differently in the editor - Adam: \"same appearance in editor as viewer\"");
                }
            }
        }
    }

    /**
     * A square out of service carries no arrows and no arrival marks, on the diagram or in the editor: nothing can pass
     * it, so a way through says nothing, and the X says it all.  Its directions
     * stay as they are set, and are drawn again once it is back in service.  A station and a plain sensor, on straight
     * track and a curve, with a one-way road and an arrival barred and one open: none of the arrows' red or green, and
     * none of the arrival marks' yellow.
     *
     * MUTATION: draw the arrows on a square out of service again, and this fails.
     */
    @Test
    public void testASquareOutOfServiceHasNoArrows()
    {
        Side[][] roads = {{Side.W, Side.E}, {Side.E, Side.S}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : roads)
            {
                for (TileAnnotation.Badge shut : new TileAnnotation.Badge[] {
                    station(road[0], road[1], false, false, true, true, null),
                    new TileAnnotation.Badge(false, false, false, true, false, road[0], road[1], false, true, null)})
                {
                    for (boolean editor : new boolean[] {false, true})
                    {
                        int[] ink = arrowAndArrivalInk(shut, road, size, editor);

                        int arrows = ink[0], arrivals = ink[1];

                        // The control, which these zeroes are worthless without: the same square in service shows
                        // both, so the matchers can see what they are asserted to be missing.
                        int[] open = arrowAndArrivalInk(null, road, size, editor);

                        assertTrue(open[0] > 0 && open[1] > 0, "precondition: the same square in service draws "
                            + open[0] + " pixels of arrows and " + open[1] + " of an arrival mark at " + size
                            + " pixels, so a zero for the square out of service says nothing");

                        String what = shut + " on " + road[0] + "-" + road[1] + " at " + size + " pixels"
                            + (editor ? " in the editor" : "");

                        assertEquals(arrows, 0, what + " drew " + arrows + " pixels of arrows - nothing can pass it (Adam:"
                            + " \"For ones out of service, should arrows be drawn at all, since no trains can pass?\")");

                        assertEquals(arrivals, 0, what + " drew " + arrivals + " pixels of an arrival mark");
                    }
                }
            }
        }
    }

    /**
     * How many of a square's pixels are the arrows' red or green, and how many the arrival marks' yellow, painted as the
     * diagram or the editor paints it, on a road one way from its first side to its second with an arrival at each end.
     *
     * @param badge the square's badge, or null for none
     * @return the arrows' pixels, then the arrival marks'
     */
    private static int[] arrowAndArrivalInk(TileAnnotation.Badge badge, Side[] road, int size, boolean editor)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            TileAnnotation annotation = new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0],
                road[1], org.traincontrol.automationui.TileGraph.Direction.TOWARD_B)), -1, false, badge,
                false, road[0] != Side.W, false, null, false, Arrays.asList(
                    new TileAnnotation.Arrival(road[0], true), new TileAnnotation.Arrival(road[1], false)));

            if (editor) annotation = annotation.inTheEditor();

            annotation.paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        int arrows = 0, arrivals = 0;

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                int rgb = image.getRGB(x, y), r = channel(rgb, 16), gr = channel(rgb, 8), b = channel(rgb, 0);

                if (isArrowInk(r, gr, b)) arrows++;

                if (isArrivalInk(r, gr, b)) arrivals++;
            }
        }

        return new int[] {arrows, arrivals};
    }

    /** A badge painted on a canvas three squares across, the square in the middle - its own paint and its spill. */
    private static java.awt.image.BufferedImage wholeIcon(TileAnnotation.Badge badge, Side[] road, int size, boolean editor)
    {
        java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(size * 3, size * 3,
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = canvas.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);

            boolean curve = Math.abs(road[0].ordinal() - road[1].ordinal()) != 2;

            TileAnnotation annotation = new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0], road[1], null)),
                -1, false, badge, false, curve, false, null, true, null);

            if (editor) annotation = annotation.inTheEditor();

            java.awt.Graphics2D square = (java.awt.Graphics2D) g.create(size, size, size, size);

            annotation.paint(square, size, size);
            square.dispose();

            java.awt.Graphics2D spill = (java.awt.Graphics2D) g.create();

            spill.translate(size, size);
            annotation.paintSpill(spill, size, size);
            spill.dispose();
        }
        finally
        {
            g.dispose();
        }

        return canvas;
    }

    /** How many pixels of a colour an image has. */
    private static int inkOf(java.awt.image.BufferedImage image, java.util.function.IntPredicate colour)
    {
        int n = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if (colour.test(image.getRGB(x, y))) n++;
            }
        }

        return n;
    }

    /**
     * A square the editor shades spills nothing: while a protecting signal is picked every square but the
     * signals is shaded, and a shaded curve station draws no icon, so its two ends must not be drawn on the squares
     * beside it.
     *
     * MUTATION: let a shaded square spill again, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testAShadedStationSpillsNothing()
    {
        for (int size : new int[] {30, 60})
        {
            TileAnnotation shaded = new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(Side.E, Side.S, null)), -1,
                false, station(Side.E, Side.S, false, false, false, false, null), true, true, false, null, true, null)
                .inTheEditor();

            assertFalse(shaded.spillsOverItsSquare(), "a shaded curve station at " + size + " pixels says it spills"
                + " (RSA63-C4)");

            java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(size * 3, size * 3,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = canvas.createGraphics();

            try
            {
                g.translate(size, size);
                shaded.paintSpill(g, size, size);
            }
            finally
            {
                g.dispose();
            }

            assertEquals(inkOf(canvas), 0, "a shaded curve station at " + size + " pixels spills onto the squares beside it"
                + " (RSA63-C4)");
        }
    }

    /**
     * A badge painted as the diagram paints it, over its sensor's art turned to run along its road - the curved sensor's
     * for a curve, the straight one's for W-E - on white.
     */
    private static java.awt.image.BufferedImage overTheArt(TileAnnotation.Badge badge, Side[] road, int size)
        throws Exception
    {
        boolean curve = !(road[0] == Side.W && road[1] == Side.E);

        java.awt.image.BufferedImage image = curve ? curvedSensorArt(size, road) : turned(sensorArt("s88.gif", size), 0);

        if (!curve && !runs(image, road)) image = turned(image, 1);

        assertTrue(runs(image, road), "precondition: no quarter turn of the sensor's art runs " + road[0] + "-" + road[1]);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setClip(0, 0, size, size);

            new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0], road[1], null)), -1, false, badge, false,
                false, false, null, true, null).paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    /** The curved sensor's art, turned until its track runs from one side of the road to the other, on white. */
    private static java.awt.image.BufferedImage curvedSensorArt(int size, Side[] road) throws Exception
    {
        java.awt.image.BufferedImage art = sensorArt("s88_curve.gif", size);

        for (int k = 0; k < 4; k++)
        {
            java.awt.image.BufferedImage image = turned(art, k);

            if (runs(image, road)) return image;
        }

        throw new AssertionError("precondition: no quarter turn of the curved sensor's art runs " + road[0] + "-" + road[1]);
    }

    private static java.awt.image.BufferedImage sensorArt(String name, int size) throws Exception
    {
        java.awt.image.BufferedImage art = javax.imageio.ImageIO.read(org.traincontrol.gui.TrainControlUI.class
            .getResource("/org/traincontrol/gui/resources/icons" + size + "/" + name));

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.drawImage(art, 0, 0, null);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    /** The image turned k quarter turns clockwise. */
    private static java.awt.image.BufferedImage turned(java.awt.image.BufferedImage image, int k)
    {
        int size = image.getWidth();

        java.awt.image.BufferedImage out =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = out.createGraphics();

        try
        {
            g.rotate(k * Math.PI / 2, size / 2.0, size / 2.0);
            g.drawImage(image, 0, 0, null);
        }
        finally
        {
            g.dispose();
        }

        return out;
    }

    /** Whether the art's track meets the square's edges at the road's two sides and no other. */
    private static boolean runs(java.awt.image.BufferedImage image, Side[] road)
    {
        for (Side side : Side.values())
        {
            int[] at = midpointOf(side, image.getWidth());

            int x = Math.max(1, Math.min(image.getWidth() - 2, at[0])), y = Math.max(1, Math.min(image.getHeight() - 2, at[1]));

            if (BLACK.test(image.getRGB(x, y)) != (side == road[0] || side == road[1])) return false;
        }

        return true;
    }

    /** The middle of a side of a square. */
    private static int[] midpointOf(Side side, int size)
    {
        switch (side)
        {
            case N: return new int[] {size / 2, 0};
            case E: return new int[] {size, size / 2};
            case S: return new int[] {size / 2, size};
            default: return new int[] {0, size / 2};
        }
    }

    /** How far a point is across the chord a road's curve is drawn as - the line between its two sides' middles. */
    private static double acrossTheChord(double[] point, Side[] road, int size)
    {
        int[] a = midpointOf(road[0], size), b = midpointOf(road[1], size);

        double dx = b[0] - a[0], dy = b[1] - a[1], length = Math.hypot(dx, dy);

        return Math.abs((point[0] - a[0]) * dy - (point[1] - a[1]) * dx) / length;
    }

    /** The middle of the pixels of a colour, and how many there are. */
    private static double[] middleOf(java.awt.image.BufferedImage image, java.util.function.IntPredicate colour)
    {
        double x = 0, y = 0, n = 0;

        for (int i = 0; i < image.getWidth(); i++)
        {
            for (int j = 0; j < image.getHeight(); j++)
            {
                if (colour.test(image.getRGB(i, j)))
                {
                    x += i + 0.5;
                    y += j + 0.5;
                    n++;
                }
            }
        }

        return n == 0 ? new double[] {0, 0, 0} : new double[] {x / n, y / n, n};
    }

    /**
     * The red and green arrows on a station's square are drawn over its icon, on the diagram and in the editor, and over
     * the icon drawn again above a running train's line (MT-710).  FR-118's icons run along the track nearly the
     * width of the square, where the arrows are.
     *
     * Each kind painted with a one-way road - a red arrow at one end, a green at the other - and without its icon: the
     * arrows' pixels are all still there with it.
     *
     * MUTATION: paint the icon after the arrows again, or leave them out of the icon drawn over the run, and this fails.
     */
    @Test
    public void testTheArrowsAreDrawnOverAStationsIcon()
    {
        for (int size : new int[] {30, 60})
        {
            for (TileAnnotation.Badge badge : new TileAnnotation.Badge[] {
                station(Side.W, Side.E, false, false, false, false, null), station(Side.W, Side.E, true, true, false, false, null),
                station(Side.W, Side.E, true, false, false, false, null), station(Side.W, Side.E, false, false, false, false, Side.W)})
            {
                for (boolean editor : new boolean[] {false, true})
                {
                    // the square alone - the editor, a diagram at rest - and then with the icon drawn again over a run
                    for (boolean overRun : new boolean[] {false, true})
                    {
                        int bare = arrowInk(null, size, editor, overRun), over = arrowInk(badge, size, editor, overRun);

                        assertTrue(bare > 0, "precondition: no arrows drawn at " + size + " pixels");

                        assertTrue(over >= bare * 0.95, badge + " at " + size + " pixels" + (editor ? " in the editor" : "")
                            + (overRun ? " over a run" : "") + " leaves " + over + " of the arrows' " + bare + " pixels"
                            + " showing - Adam: \"render red arrows on top of stations\"");
                    }
                }
            }
        }
    }

    /**
     * And on a curve, where the icon is its full size across the chord the arrows sit on.  Every kind, every curve, both
     * sizes, on the diagram and in the editor, at rest and with the icon drawn
     * again over a run: the arrows' pixels are all still there with the icon.
     *
     * MUTATION: paint a station's icon over its square's arrows again, and this fails.
     */
    @Test
    public void testTheArrowsAreDrawnOverAStationsIconOnACurve()
    {
        Side[][] curves = {{Side.E, Side.S}, {Side.S, Side.W}, {Side.W, Side.N}, {Side.N, Side.E}};

        for (int size : new int[] {30, 60})
        {
            for (Side[] road : curves)
            {
                for (TileAnnotation.Badge badge : everyKind(road[0], road[1]))
                {
                    // not a square out of service, which carries no arrows (`testASquareOutOfServiceHasNoArrows`)
                    if (badge.isImpassable()) continue;

                    for (boolean editor : new boolean[] {false, true})
                    {
                        for (boolean overRun : new boolean[] {false, true})
                        {
                            int bare = arrowInk(null, road, size, editor, overRun);
                            int over = arrowInk(badge, road, size, editor, overRun);

                            assertTrue(bare > 0, "precondition: no arrows drawn on a curve " + road[0] + "-" + road[1]
                                + " at " + size + " pixels");

                            assertTrue(over >= bare * 0.95, badge + " on a curve " + road[0] + "-" + road[1] + " at " + size
                                + " pixels" + (editor ? " in the editor" : "") + (overRun ? " over a run" : "") + " leaves "
                                + over + " of the arrows' " + bare + " pixels showing - Adam: \"make sure that the optional"
                                + " ingress/egress arrows remain visible, especially on curves\"");
                        }
                    }
                }
            }
        }
    }

    /** `arrowInk` on a W-E road. */
    private static int arrowInk(TileAnnotation.Badge badge, int size, boolean editor, boolean overRun)
    {
        return arrowInk(badge, new Side[] {Side.W, Side.E}, size, editor, overRun);
    }

    /**
     * How many of a square's pixels are its arrows' red or green, painted as the diagram paints it - the square, and with
     * `overRun` the icon again over a running train's line - with a road one way from its first side to its second.
     *
     * @param badge the station, or null for none
     */
    private static int arrowInk(TileAnnotation.Badge badge, Side[] road, int size, boolean editor, boolean overRun)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            TileAnnotation annotation = new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(road[0], road[1],
                org.traincontrol.automationui.TileGraph.Direction.TOWARD_B)), -1, false, badge, false, false, false, null,
                false, null);

            if (editor) annotation = annotation.inTheEditor();

            annotation.paint(g, size, size);

            if (overRun) annotation.paintBadgeOverRun(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        int n = 0;

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                int rgb = image.getRGB(x, y), r = channel(rgb, 16), gr = channel(rgb, 8), b = channel(rgb, 0);

                // the arrows' own red and green, solid: an antialiased edge takes the colour of what it is drawn over
                if (isArrowInk(r, gr, b)) n++;
            }
        }

        return n;
    }

    /**
     * How long a station's icon is along a road from W to E, outline and all: painted eight times over on nothing, the
     * run of ink along the middle of the track, in the tile's own pixels.
     */
    private static double lengthAlong(TileAnnotation.Badge badge, int size)
    {
        int k = 8;

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size * k, size * k,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.scale(k, k);

            new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(Side.W, Side.E, null)), 0, false, badge, false, false,
                false, null, true, null).paintBadgeOverRun(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        int row = size * k / 2, first = -1, last = -1;

        for (int x = 0; x < image.getWidth(); x++)
        {
            if ((image.getRGB(x, row) >>> 24) >= 128)
            {
                if (first < 0) first = x;
                last = x;
            }
        }

        return first < 0 ? 0 : (last - first + 1) / (double) k;
    }

    /** The asserts of a terminus: flat on the side its track runs out, pointed the other way, a white bar near the flat. */
    private static void assertTerminusFacing(java.awt.image.BufferedImage image, java.util.function.IntPredicate colour,
        Side end, String which)
    {
        java.awt.Rectangle shape = extent(image, colour);

        assertNotNull(shape, which + " draws nothing in its colour");

        boolean along = end == Side.W || end == Side.E;

        int across = along ? shape.height : shape.width;

        int near = end == Side.W ? shape.x : end == Side.E ? shape.x + shape.width - 1
            : end == Side.N ? shape.y : shape.y + shape.height - 1;

        int far = end == Side.W ? shape.x + shape.width - 1 : end == Side.E ? shape.x
            : end == Side.N ? shape.y + shape.height - 1 : shape.y;

        assertTrue(count(image, near, along, colour) >= across - 2, which + " is not flat on that side (FR-118)");

        assertTrue(count(image, far, along, colour) <= across / 3, which + " does not point away from that side (FR-118)");

        // the bar: in the third of the shape nearest that side, a line across the track white through its middle
        int length = along ? shape.width : shape.height, step = near < far ? 1 : -1, middle = along
            ? shape.y + shape.height / 2 : shape.x + shape.width / 2;

        boolean bar = false;

        for (int i = 0; i < length / 3; i++)
        {
            int line = near + i * step;

            boolean white = true;

            for (int j = middle - 2; j <= middle + 1; j++)
            {
                white &= PURE_WHITE.test(along ? image.getRGB(line, j) : image.getRGB(j, line));
            }

            bar |= white;
        }

        assertTrue(bar, which + " has no white bar at its flat end (FR-118)");
    }

    /** How many pixels of a colour lie on one line across the track: a column (`along`), or a row. */
    private static int count(java.awt.image.BufferedImage image, int line, boolean along, java.util.function.IntPredicate colour)
    {
        int n = 0;

        for (int i = 0; i < (along ? image.getHeight() : image.getWidth()); i++)
        {
            if (colour.test(along ? image.getRGB(line, i) : image.getRGB(i, line))) n++;
        }

        return n;
    }

    /** How many blue pixels a column holds (`column`), or a row. */
    private static int blueAlong(java.awt.image.BufferedImage image, int line, boolean column)
    {
        return count(image, line, column, BLUE);
    }

    /** What the pixels of a colour cover, or null for none. */
    private static java.awt.Rectangle extent(java.awt.image.BufferedImage image, java.util.function.IntPredicate colour)
    {
        java.awt.Rectangle out = null;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if (!colour.test(image.getRGB(x, y))) continue;

                if (out == null) out = new java.awt.Rectangle(x, y, 1, 1);
                else out.add(new java.awt.Rectangle(x, y, 1, 1));
            }
        }

        return out;
    }

    /** The s88 tile's contact, across, in pixels: 14 at 30 and 26 at 60. */
    private static int contact(int size)
    {
        return size < 60 ? 14 : 26;
    }

    private static int channel(int rgb, int shift)
    {
        return (rgb >> shift) & 0xFF;
    }

    /** The stations' blue, solid - not where it blends into its white outline. */
    private static final java.util.function.IntPredicate BLUE =
        rgb -> channel(rgb, 0) > 150 && channel(rgb, 16) < 90 && channel(rgb, 8) < 90;

    /**
     * The grey of a berth autonomy does not choose, the program's own `POINT_INACTIVE` - bluer than it is red, which the
     * tile art's greys, all of them neutral, are not.  Asked rather than restated: a matcher for a colour the program no
     * longer paints finds nothing, and every "no grey" claim below passes on nothing.
     */
    private static final java.awt.Color INACTIVE = ProgramConstants.colour(TileAnnotation.class, "POINT_INACTIVE");

    private static final java.util.function.IntPredicate GREY =
        rgb -> Math.abs(channel(rgb, 16) - INACTIVE.getRed()) < 10
        && Math.abs(channel(rgb, 8) - INACTIVE.getGreen()) < 10 && Math.abs(channel(rgb, 0) - INACTIVE.getBlue()) < 10
        && channel(rgb, 0) - channel(rgb, 16) >= INACTIVE.getBlue() - INACTIVE.getRed() - 3;

    /** The one-way arrows' red (a road shut one way, `CLOSED`) and green (`ONE_WAY`), and the arrival marks' yellow. */
    private static final java.awt.Color ARROW_RED = ProgramConstants.colour(TileAnnotation.class, "CLOSED");

    private static final java.awt.Color ARROW_GREEN = ProgramConstants.colour(TileAnnotation.class, "ONE_WAY");

    private static final java.awt.Color ARRIVAL_YELLOW = ProgramConstants.colour(TileAnnotation.class, "ARRIVAL");

    /**
     * Whether a pixel is one of the arrows' own red or green, solid.
     *
     * The colours are the program's.  A matcher cut for a colour the program has stopped painting counts nothing, and the
     * claims that assert there is NONE of it on a square then pass on nothing.
     */
    private static boolean isArrowInk(int r, int gr, int b)
    {
        return (Math.abs(r - ARROW_RED.getRed()) < 30 && gr < ARROW_RED.getGreen() + 40 && b < ARROW_RED.getBlue() + 40)
            || (Math.abs(r - ARROW_GREEN.getRed()) < 30 && Math.abs(gr - ARROW_GREEN.getGreen()) < 30
                && Math.abs(b - ARROW_GREEN.getBlue()) < 30);
    }

    /** Whether a pixel is the arrival marks' yellow. */
    private static boolean isArrivalInk(int r, int gr, int b)
    {
        return r > ARRIVAL_YELLOW.getRed() - 25 && Math.abs(gr - ARRIVAL_YELLOW.getGreen()) < 25
            && b < ARRIVAL_YELLOW.getBlue() + 40;
    }

    /** How wide the program draws the train's icon on a square this size: `TileOverlay.iconSide`. */
    private static int iconSideOn(int size)
    {
        return sizeOfTrainMark("iconSide", size);
    }

    /** How wide the program draws the train's dot on a square this size: `TileOverlay.dotDiameter`. */
    private static int dotOn(int size)
    {
        return sizeOfTrainMark("dotDiameter", size);
    }

    private static int sizeOfTrainMark(String method, int size)
    {
        try
        {
            java.lang.reflect.Method ask = TileOverlay.class.getDeclaredMethod(method, int.class, int.class);

            ask.setAccessible(true);

            return (Integer) ask.invoke(null, size, size);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("TileOverlay." + method + " is not there to be asked: " + e, e);
        }
    }

    private static final java.util.function.IntPredicate ORANGE =
        rgb -> channel(rgb, 16) > 200 && channel(rgb, 8) > 60 && channel(rgb, 8) < 160 && channel(rgb, 0) < 70;

    private static final java.util.function.IntPredicate PURE_WHITE =
        rgb -> channel(rgb, 16) >= 245 && channel(rgb, 8) >= 245 && channel(rgb, 0) >= 245;

    private static final java.util.function.IntPredicate BLACK =
        rgb -> channel(rgb, 16) < 70 && channel(rgb, 8) < 70 && channel(rgb, 0) < 70;

    /**
     * A named station on a road from a to b, as the diagram and the editor build one.
     *
     * @param turns whether trains turn round there
     * @param may whether turning is a choice
     * @param parking whether autonomy leaves it alone
     * @param shut whether it is out of service
     * @param deadEnd the side its track runs out, or null
     */
    private static TileAnnotation.Badge station(Side a, Side b, boolean turns, boolean may, boolean parking, boolean shut,
        Side deadEnd)
    {
        return new TileAnnotation.Badge(true, turns, false, parking || shut, true, a, b, turns && may, shut, deadEnd);
    }

    /**
     * A badge painted as the diagram paints it on its sensor's square: over the s88 tile's own art (turned a quarter for
     * a road from N to S), on white.
     *
     * @param badge the badge
     * @param size the tile's edge: 30 or 60, the sizes the art is drawn at
     * @return the square
     * @throws Exception from reading the art
     */
    private static java.awt.image.BufferedImage onItsSensor(TileAnnotation.Badge badge, int size) throws Exception
    {
        java.awt.image.BufferedImage art = javax.imageio.ImageIO.read(org.traincontrol.gui.TrainControlUI.class
            .getResource("/org/traincontrol/gui/resources/icons" + size + "/s88.gif"));

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            java.awt.Graphics2D tile = (java.awt.Graphics2D) g.create();

            if (badge.getA() == Side.N || badge.getA() == Side.S) tile.rotate(Math.PI / 2, size / 2.0, size / 2.0);

            tile.drawImage(art, 0, 0, size, size, null);
            tile.dispose();

            new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(badge.getA(), badge.getB(), null)), 0, false, badge,
                false, false, false, null, true, null).paintBadgeOverRun(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    /**
     * The colour a cross is drawn in, read off the cross alone: the commonest solid colour where the badge differs from
     * a grey station's block, which is what a station's cross is drawn over (FR-118).
     */
    private static java.awt.Color crossColour(TileAnnotation.Badge b, int size) throws Exception
    {
        java.awt.image.BufferedImage with = painted(b, size), without = painted(plainBadge(true, false), size);

        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                int argb = with.getRGB(x, y);

                if (argb == without.getRGB(x, y) || (argb >>> 24) < 200) continue;

                // not the white inside the circle, which says nothing about the colour chosen
                if (PURE_WHITE.test(argb)) continue;

                counts.merge(argb & 0xFFFFFF, 1, Integer::sum);
            }
        }

        int best = -1, most = 0;

        for (java.util.Map.Entry<Integer, Integer> e : counts.entrySet())
        {
            if (e.getValue() > most)
            {
                most = e.getValue();
                best = e.getKey();
            }
        }

        assertTrue(best >= 0, "the cross drew nothing apart from the block");

        return new java.awt.Color(best);
    }

    /** How many of a painted badge's pixels are solid in the grey of a berth autonomy does not choose. */
    private static int greyOf(java.awt.image.BufferedImage image)
    {
        int n = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if ((image.getRGB(x, y) >>> 24) >= 200 && GREY.test(image.getRGB(x, y))) n++;
            }
        }

        return n;
    }

    /** How many of a painted badge's pixels are the cross's orange. */
    private static int orangeOf(java.awt.image.BufferedImage image)
    {
        int n = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if ((image.getRGB(x, y) >>> 24) >= 200 && ORANGE.test(image.getRGB(x, y))) n++;
            }
        }

        return n;
    }

    /** One badge on a road from W to E, painted over the run as the diagram does, on nothing. */
    private static java.awt.image.BufferedImage painted(TileAnnotation.Badge b, int size)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            new TileAnnotation(Arrays.asList(new TileAnnotation.Mark(Side.W, Side.E, null)), 0, false, b, false, false,
                false, null, true, null).paintBadgeOverRun(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    /**
     * An ordinary station badge, where the only things that vary are the two that decide its colour.
     */
    private static TileAnnotation.Badge plainBadge(boolean parking, boolean shut)
    {
        return new TileAnnotation.Badge(true, false, false, parking, true, Side.W, Side.E, false, shut);
    }

    /**
     * The colour the mark is actually drawn in - the commonest solid pixel that is not the white a
     * badge is lined and hollowed with, which says nothing about which colour was chosen.
     */
    private static java.awt.Color markColour(TileAnnotation.Badge b, int size) throws Exception
    {
        // as the running diagram builds one: its road for the badge, and only restricted roads' arrows (`blockedOnly`),
        // which an open road has none of - the arrows are drawn over the badge (MT-710)
        TileAnnotation annotation = new TileAnnotation(
            Arrays.asList(new TileAnnotation.Mark(Side.W, Side.E, null)), 0, false, b, false, false, false, null, true, null);

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            annotation.paintBadgeOverRun(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                int argb = image.getRGB(x, y);

                // Solid pixels only: the antialiased fringe of a stroke is every shade between the
                // colour and nothing, and counting it would make the answer depend on the shape.
                if ((argb >>> 24) < 200) continue;

                int rgb = argb & 0xFFFFFF;

                if (rgb == 0xFFFFFF) continue;

                counts.put(rgb, counts.containsKey(rgb) ? counts.get(rgb) + 1 : 1);
            }
        }

        int best = -1;
        int most = 0;

        for (java.util.Map.Entry<Integer, Integer> e : counts.entrySet())
        {
            if (e.getValue() > most)
            {
                most = e.getValue();
                best = e.getKey();
            }
        }

        assertTrue(best >= 0, "the badge drew no solid colour at all, so there is nothing to compare");

        return new java.awt.Color(best);
    }

    /**
     * One badge, painted on its own, for counting.
     *
     * @param station whether the square is somewhere trains stop
     * @param turns whether trains turn round here
     * @param shut whether nothing may use the square at all
     * @param size the tile's edge, in pixels
     * @return the painted badge
     */
    private static java.awt.image.BufferedImage badgeAt(boolean station, boolean turns, boolean shut,
        int size) throws Exception
    {
        return badgeAt(station, turns, false, shut, size);
    }

    /**
     * The same, with parking said separately from shut.
     *
     * They are separate arguments because the two tests that vary only `shut` would otherwise pass just as well
     * against `isImpassable() { return parking; }`: a fixture with the two equal cannot tell them apart.
     *
     * They share a colour, which is why separating them changes no ink here: parking was true only where shut
     * already was.
     *
     * @param station whether the square is a station
     * @param turns whether it turns trains round
     * @param parking whether autonomy leaves the square alone
     * @param shut whether it is out of service
     * @param size the tile size to draw at
     * @return the badge, drawn
     */
    private static java.awt.image.BufferedImage badgeAt(boolean station, boolean turns,
        boolean parking, boolean shut, int size) throws Exception
    {
        TileAnnotation annotation = new TileAnnotation(
            Arrays.asList(new TileAnnotation.Mark(Side.W, Side.E, null)), 0, false,
            new TileAnnotation.Badge(station, station && turns, !station && turns, parking, true,
                Side.W, Side.E, false, shut),
            false, false, false, null, true, null);

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            annotation.paintBadgeOverRun(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    /**
     * How much of the tile the mark actually covers.
     */
    private static int inkOf(java.awt.image.BufferedImage image)
    {
        int ink = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if ((image.getRGB(x, y) >>> 24) > 8) ink++;
            }
        }

        return ink;
    }

    /**
     * And a straight is still the middle of the square, which is the case that must not move.
     */
    @Test
    public void testAStraightKeepsTheMiddleOfTheSquare()
    {
        int size = 60;

        TileAnnotation straight = new TileAnnotation(
            Arrays.asList(new TileAnnotation.Mark(Side.W, Side.E, null)), 0, false,
            new TileAnnotation.Badge(true, false, false, false, true, Side.W, Side.E), false);

        int[] centre = straight.trackCentre(size, size);

        assertEquals(centre[0], 30, "a straight's track centre moved off the middle of the square");
        assertEquals(centre[1], 30, "a straight's track centre moved off the middle of the square");
    }

    /**
     * A square with nothing known about its track falls back to the middle, rather than to a corner.
     */
    @Test
    public void testASquareWithNoRouteFallsBackToTheCentre()
    {
        TileAnnotation blank = new TileAnnotation(
            java.util.Collections.<TileAnnotation.Mark>emptyList(), 0, false, null, false);

        int[] centre = blank.trackCentre(60, 60);

        assertEquals(centre[0], 30, "a square with no route known should answer the middle");
        assertEquals(centre[1], 30, "a square with no route known should answer the middle");
    }

    /**
     * The EDITOR's tested path stops on the rail too, not only the running overlay's run line.
     *
     * OB-026 stops the end of a run on the rail in `TileOverlay.paintRun`, with `trackCentre`, whose javadoc claims
     * "the run line and the badge now agree about where the track is."  `paintTraces`, in the same class, must start
     * from it too, not from `{width / 2, height / 2}`: the editor's yellow trace is drawn on the same squares to
     * answer the same question.  Null ends are not hypothetical: the editor builds them deliberately, "null
     * at the ends of the run, where the line stops in the middle of the square", which is exactly the first and last
     * square of a traced path.
     *
     * The pixel test written for OB-026 drives `TileOverlay` only, so it does not cover this.
     *
     * **The trace is isolated by DIFFERENCE.**  Painting one tile and asking whether anything was drawn near the rail
     * would pass against broken code, because the badge and the direction arrows are drawn on the rail too.
     * Subtracting a rendering without the trace from one with it leaves only the pixels the trace added, which is the
     * only thing this test is about.
     *
     * On a 60-pixel N-E bend the rail's midpoint is (45,15). The tile centre, (30,30), is 21 pixels
     * away - so a line that stops at the centre cannot be mistaken for one that reached the rail.
     */
    @Test
    public void testTheEditorsTracedPathStopsOnTheRailOfABend()
    {
        int size = 60;

        java.awt.image.BufferedImage without = painted(bend(null), size);
        java.awt.image.BufferedImage with =
            painted(bend(Arrays.asList(new TileAnnotation.Trace(Side.N, null, true))), size);

        assertTrue(addedNear(with, without, 45, 15),
            "the traced path does not reach the rail. The editor's own line for a tested run still "
            + "stops at the tile centre on a bend, which is the defect OB-026 fixed in the run "
            + "overlay and left in its sibling (TD-4)");
    }

    /**
     * A square whose track bends from N to E, with or without a traced run ending on it.
     */
    private TileAnnotation bend(java.util.List<TileAnnotation.Trace> traces)
    {
        return new TileAnnotation(
            Arrays.asList(new TileAnnotation.Mark(Side.N, Side.E, null)), 0, false,
            new TileAnnotation.Badge(true, false, false, false, true, Side.N, Side.E), false, true,
            false, traces, false, null);
    }

    /**
     * A train that is running is drawn as a locomotive; one that is standing still keeps the dot.
     *
     * FR-027.  The dot says WHERE a train is and nothing else; the icon says which of the trains on a layout are moving
     * and which are waiting at a platform.
     *
     * **What is asserted is ink in a ring the dot cannot reach.** Not "the two pictures differ", which
     * a one-pixel change would satisfy, and not the colour of a particular pixel, which is a bet on the
     * artwork - the icon is a FILE and is meant to be replaced. Whatever somebody draws, it is scaled
     * to ICON_SCALE of the tile and the dot is a third of it, so ink between those two radii is the
     * icon and nothing else.
     *
     * That also makes this the test that the resource is actually on the classpath: TileOverlay falls
     * back to the dot when the file is missing, deliberately and silently, so a build that stopped
     * copying the PNG would look exactly like a build with the feature turned off.
     *
     * MUTATION: setting ICON_ONLY_WHILE_MOVING false fails the standing half; renaming or deleting
     * running_train.png fails the moving half; painting the icon for both fails the standing half.
     */
    @Test
    public void testAMovingTrainIsDrawnAsALocomotive()
    {
        int size = 40;

        java.awt.image.BufferedImage moving =
            painted(new TileOverlay(State.IDLE, true, true, null), size);

        java.awt.image.BufferedImage standing =
            painted(new TileOverlay(State.IDLE, true, false, null), size);

        // The dot is the program's dot across, so nothing it draws reaches a little more than its radius. The
        // icon is the program's icon across, so it fills most of the way to its own.
        int near = dotOn(size) / 2 + 3;
        int far = iconSideOn(size) / 2 - 1;

        int onMoving = inkInRing(moving, near, far);
        int onStanding = inkInRing(standing, near, far);

        assertTrue(onMoving > 0,
            "nothing is drawn outside the dot for a train that is running, so either the locomotive "
            + "icon was not painted or running_train.png is not on the classpath - TileOverlay falls "
            + "back to the dot without saying so, which makes a missing resource look like a feature "
            + "that was never switched on");

        assertEquals(onStanding, 0,
            "a train standing still was drawn with something bigger than the dot. Adam asked for the "
            + "locomotive while autonomy is running and NOT while stationary, and a marker that looks "
            + "the same either way answers the question it was added to answer with 'both'");
    }

    /**
     * How many pixels carry ink between two radii of the tile's centre.
     *
     * A ring rather than a point, so this asks "is anything drawn out here" rather than betting on
     * where a particular piece of an icon lands - the icon is meant to be replaceable.
     */
    private int inkInRing(java.awt.image.BufferedImage image, int from, int to)
    {
        int centre = image.getWidth() / 2;
        int count = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                double away = Math.hypot(x - centre, y - centre);

                if (away < from || away > to) continue;

                // Anything at all, transparent included: the images start empty, so a non-zero alpha
                // is ink somebody put there.
                if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0) count++;
            }
        }

        return count;
    }

    /**
     * A square with a train running on it leaves the labels over it where they are - in front of it (OB-259).
     *
     * Since OB-159 the diagram's panel paints every train after all of its components, so the locomotive is not hidden
     * by the s88 number and the square needs no lift.  Lifting it would paint the address out, because a tile is
     * opaque.
     *
     * MUTATION: lift the square of a running train again, and this fails.
     */
    @Test
    public void testASquareWithARunningTrainLeavesItsLabelsInFront() throws Exception
    {
        javax.swing.JPanel grid = new javax.swing.JPanel();

        org.traincontrol.gui.LayoutLabel tile =
            new org.traincontrol.gui.LayoutLabel(null, null, 30, null, false);

        javax.swing.JLabel address = new javax.swing.JLabel("16");

        grid.add(tile);
        grid.add(address);

        // What LayoutGrid does with an address label: to index 0, which is painted LAST and therefore on top.
        grid.setComponentZOrder(address, 0);

        assertTrue(grid.getComponentZOrder(tile) > grid.getComponentZOrder(address),
            "precondition: the tile does not start behind the address label");

        tile.setAutonomyOverlay(new TileOverlay(State.IDLE, true, true, null));

        settle();

        assertTrue(grid.getComponentZOrder(tile) > grid.getComponentZOrder(address), "a square with a train running on"
            + " it came in front of its address label and, being opaque, painted the number out - the train itself is"
            + " drawn above every component by the diagram's panel (OB-159), so nothing has to move (OB-259)");

        tile.setAutonomyOverlay(new TileOverlay(State.IDLE, true, false, null));

        settle();

        assertTrue(grid.getComponentZOrder(tile) > grid.getComponentZOrder(address),
            "the square is in front of its address label after its train stopped");
    }

    /**
     * Waits for anything already queued on the event thread.
     *
     * The lift is posted rather than done where it is decided, because the monitor publishes from its
     * own worker and container order is not thread-safe. An empty task run to completion is the
     * shortest way to say "and now everything before me has happened".
     */
    private void settle() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * The locomotive is turned to face the way the train is going.
     *
     * **The icon cannot simply be compared between two headings**, and that is the whole difficulty of
     * this test: an overlay carrying a heading also draws the LINE for it, and the line for a train
     * going east looks nothing like the line for one going north. Two pictures that differ would prove
     * only that the lines differ.
     *
     * So each heading is rendered twice - once moving, once standing - and what is compared is the
     * DIFFERENCE between them. The line is identical in both, and the dot is round, so what is left is
     * the icon and the direction it faces.
     *
     * Two things are asserted, and the second is what makes the first mean something. East and west
     * must differ, or nothing is being turned. And all four must carry about the same amount of ink,
     * because a transform moves a picture rather than replacing it - a "fix" that drew a different
     * icon per heading would pass the first and fail this.
     *
     * MUTATION: setting ICON_FOLLOWS_TRAVEL false makes every heading identical and fails the first.
     */
    @Test
    public void testTheLocomotiveFacesTheWayTheTrainIsGoing()
    {
        int size = 40;

        boolean[] east = iconOnly(Side.E, size);
        boolean[] west = iconOnly(Side.W, size);
        boolean[] north = iconOnly(Side.N, size);
        boolean[] south = iconOnly(Side.S, size);

        assertFalse(java.util.Arrays.equals(east, west),
            "a train going east and one going west are drawn with the locomotive pointing the same "
            + "way, so it is not being turned at all - half the trains on the layout face backwards");

        assertFalse(java.util.Arrays.equals(east, north),
            "a train going north is drawn exactly as one going east");

        assertFalse(java.util.Arrays.equals(north, south),
            "a train going north and one going south are drawn the same way, which is the pair a "
            + "reader is most likely to be trying to tell apart on a vertical run");

        int e = count(east);

        assertTrue(e > 0, "nothing distinguishes a moving train from a standing one at all");

        for (boolean[] other : new boolean[][] { west, north, south })
        {
            int n = count(other);

            // A tenth, which is room for what antialiasing does to a rotated shape and not room for a
            // different picture.
            assertTrue(Math.abs(n - e) * 10 <= e,
                "one heading draws " + n + " pixels where east draws " + e + ". That is not the same "
                + "locomotive turned round, which is what rotating and flipping means - it is a "
                + "different picture per direction, and it will not survive somebody replacing the "
                + "icon file");
        }
    }

    /**
     * Which pixels a MOVING train adds to a square, for a train heading a given way.
     *
     * The same overlay twice, moving and standing, differenced. Both draw the same line - the heading
     * is the same - so what is left is the locomotive rather than the path it is on, which is the only
     * way to compare two headings without comparing their lines.
     */
    private boolean[] iconOnly(Side to, int size)
    {
        java.util.List<TileOverlay.Segment> along =
            Arrays.asList(new TileOverlay.Segment(null, to, State.ACTIVE));

        java.awt.image.BufferedImage moving =
            painted(new TileOverlay(State.ACTIVE, true, true, along), size);

        java.awt.image.BufferedImage standing =
            painted(new TileOverlay(State.ACTIVE, true, false, along), size);

        boolean[] differs = new boolean[size * size];

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                differs[y * size + x] = moving.getRGB(x, y) != standing.getRGB(x, y);
            }
        }

        return differs;
    }

    private int count(boolean[] mask)
    {
        int total = 0;

        for (boolean one : mask)
        {
            if (one) total++;
        }

        return total;
    }

    private java.awt.image.BufferedImage painted(TileAnnotation annotation, int size)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        annotation.paint(g, size, size);

        g.dispose();

        return image;
    }

    /**
     * The same, for an overlay.  TileAnnotation and TileOverlay both paint into a tile's graphics and
     * neither shares an interface with the other, so this is the second half of one idea rather than a
     * copy of it.
     */
    private java.awt.image.BufferedImage painted(TileOverlay overlay, int size)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        // BOTH passes, because the diagram draws both (OB-159).  The train came out of paint()
        // and into paintTrain() so it could be drawn above the station captions, which are
        // separate components in front of every tile; rendering only the first pass here is
        // asking about half a square.
        overlay.paint(g, size, size);
        overlay.paintTrain(g, size, size, null);

        g.dispose();

        return image;
    }

    /**
     * Whether the second rendering added ink near a point that the first did not have.
     *
     * A tolerance of three pixels, because a stroked line has width and where its exact pixels fall
     * depends on the join and the antialiasing. The question is where the line WENT.
     */
    private boolean addedNear(java.awt.image.BufferedImage with, java.awt.image.BufferedImage without,
        int x, int y)
    {
        for (int dx = -3; dx <= 3; dx++)
        {
            for (int dy = -3; dy <= 3; dy++)
            {
                int at = x + dx, down = y + dy;

                if (at < 0 || down < 0 || at >= with.getWidth() || down >= with.getHeight()) continue;

                if (with.getRGB(at, down) != without.getRGB(at, down)) return true;
            }
        }

        return false;
    }

    /**
     * A parked train is drawn as the locomotive a run draws, facing the way it stands, on top of its station in the
     * track diagram viewer.
     *
     * Ink in the ring the dot cannot reach, as `testAMovingTrainIsDrawnAsALocomotive` asks it, and two facings drawn
     * differently.
     *
     * MUTATION: leave a parked train out of the icon in `paintTrain`, and this fails; so does a heading that ignores
     * a parked train's facing.
     */
    @Test
    public void testAParkedTrainIsDrawnAsALocomotiveFacingTheWayItStands()
    {
        int size = 40;

        java.awt.image.BufferedImage east = painted(TileOverlay.parked(Side.E), size);
        java.awt.image.BufferedImage west = painted(TileOverlay.parked(Side.W), size);

        assertTrue(inkInRing(east, dotOn(size) / 2 + 3, iconSideOn(size) / 2 - 1) > 0, "a parked train is drawn with nothing bigger than the dot, not as the"
            + " locomotive a run draws (Adam, 2026-10-01)");

        assertFalse(java.util.Arrays.equals(pixels(east), pixels(west)), "a parked train facing east and one facing"
            + " west are drawn the same way, so the icon does not face the way the train stands");
    }

    /**
     * A paused train is drawn grey, where one that may run is black (FR-117).
     *
     * The icon's black body is counted on a train that runs; on a paused one none of it is left black, and as much again
     * is a mid grey.
     *
     * MUTATION: draw a paused train's icon as any other's, and this fails.
     */
    @Test
    public void testAPausedTrainIsDrawnGrey()
    {
        int size = 60;

        java.awt.image.BufferedImage running = painted(TileOverlay.parked(Side.E), size);
        java.awt.image.BufferedImage paused = painted(TileOverlay.parked(Side.E).withPaused(true), size);

        int black = 0, blackPaused = 0, greyPaused = 0;

        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                if (isBlack(running.getRGB(x, y))) black++;
                if (isBlack(paused.getRGB(x, y))) blackPaused++;
                if (isMidGrey(paused.getRGB(x, y))) greyPaused++;
            }
        }

        assertTrue(black > 50, "precondition: a train that runs is drawn with " + black + " black pixels, too few to tell"
            + " its icon by");

        assertEquals(blackPaused, 0, "a paused train is drawn with " + blackPaused + " of the " + black + " black pixels a"
            + " train that runs has - its icon is not grey");

        assertTrue(greyPaused >= black / 2, "a paused train's icon has " + greyPaused + " mid-grey pixels where one that"
            + " runs has " + black + " black ones");
    }

    private static boolean isBlack(int argb)
    {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;

        return a >= 200 && Math.max(r, Math.max(g, b)) < 70;
    }

    private static boolean isMidGrey(int argb)
    {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;

        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));

        int paused = ProgramConstants.integer(TileOverlay.class, "PAUSED_GREY");

        return a >= 200 && min >= paused - 40 && max <= paused + 40 && max - min <= 20;
    }

    /**
     * A train parked with no path is published on the square it stands on, as a parked train facing the way its Point
     * faces; a train holding a path is left to the run's own mark, the dot while it waits (FR-027).
     *
     * MUTATION: drop the parked trains from `compute`, and this fails.
     *
     * @throws Exception from building a Point
     */
    @Test
    public void testAParkedTrainIsPublishedWhereItStands() throws Exception
    {
        Map<String, TileKey> tiles = new LinkedHashMap<>();

        tiles.put("West", key("main", 1, 1));
        tiles.put("East", key("main", 5, 1));

        Point west88 = new Point("West", false, null);
        Point east88 = new Point("East", false, null);

        org.traincontrol.base.Locomotive train = locomotive();

        west88.setLocomotive(train);

        StubLayout layout = new StubLayout();

        layout.points.add(west88);
        layout.points.add(east88);

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        DiagramMonitor monitor = new DiagramMonitor(source(layout), new LinkedHashMap<String, ReducedEdge>(), tiles,
            new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    published.add(new LinkedHashMap<>(overlays));
                }
            });

        Map<String, Side> facings = new LinkedHashMap<>();

        facings.put("West", Side.W);

        monitor.setFacings(facings);

        // NOTHING RUNNING: the parked train is still marked
        monitor.refresh();

        assertEquals(published.size(), 1, "nothing was published about a train parked on the railway, so the diagram"
            + " shows no locomotive on its station (Adam, 2026-10-01)");

        TileOverlay parked = published.get(0).get(key("main", 1, 1));

        assertTrue(parked != null && parked.hasTrain() && parked.isParked(), "the square a train is parked on is not"
            + " marked as a parked train: " + published.get(0));

        assertEquals(parked.getFacing(), Side.W, "the parked train's mark does not face the way its Point faces");

        assertNull(published.get(0).get(key("main", 5, 1)), "an empty station was marked");

        // HOLDING A PATH, standing still: the locomotive where it stands, facing its way, not the dot (OB-317)
        layout.active.put(train, new ArrayList<Edge>());
        layout.standingAt = west88;

        monitor.refresh();

        TileOverlay held = published.get(published.size() - 1).get(key("main", 1, 1));

        assertTrue(held != null && held.hasTrain() && held.isParked() && !held.isMoving(), "a train holding a path,"
            + " standing still at its start, is not drawn as the locomotive standing there - the icon gave way to the dot"
            + " (OB-317): " + held);

        assertEquals(held.getFacing(), Side.W, "the train standing at its start does not face the way its Point faces");
    }

    /** Every pixel of an image, to compare two. */
    private static int[] pixels(java.awt.image.BufferedImage image)
    {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /**
     * A train setting its route up - every Point of it reserved, the train not yet under way - is not drawn parked on
     * each of them: during a run the diagram showed locomotives on sensors no train stood on, for the seconds
     * the accessories take.
     *
     * MUTATION: mark a train that holds a path as parked, and this fails.
     *
     * @throws Exception from building a Point
     */
    @Test
    public void testATrainSettingItsRouteUpIsNotDrawnParked() throws Exception
    {
        Map<String, TileKey> tiles = new LinkedHashMap<>();

        tiles.put("West", key("main", 1, 1));
        tiles.put("East", key("main", 5, 1));

        Point west88 = new Point("West", false, null);
        Point east88 = new Point("East", false, null);

        org.traincontrol.base.Locomotive train = locomotive();

        // RESERVED, both: the route being set up
        west88.setLocomotive(train);
        east88.setLocomotive(train);

        StubLayout layout = new StubLayout();

        layout.points.add(west88);
        layout.points.add(east88);
        layout.claiming.add(train);
        layout.standingAt = west88;

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        DiagramMonitor monitor = new DiagramMonitor(source(layout), new LinkedHashMap<String, ReducedEdge>(), tiles,
            new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    published.add(new LinkedHashMap<>(overlays));
                }
            });

        monitor.refresh();

        Map<TileKey, TileOverlay> picture = published.isEmpty() ? new LinkedHashMap<TileKey, TileOverlay>()
            : published.get(published.size() - 1);

        // NOT ON THE FAR POINT OF THE ROUTE IT HAS RESERVED - asserted below, with the far tile drawn nothing

        // AND DRAWN ONCE, WHERE IT STANDS: as the locomotive standing there, not the dot (OB-317)
        TileOverlay where = picture.get(key("main", 1, 1));

        assertTrue(where != null && where.hasTrain() && where.isParked() && !where.isMoving(), "a train setting its route"
            + " up is not drawn as the locomotive standing where it stands (RSA18-C2, OB-317): " + picture);

        TileOverlay far = picture.get(key("main", 5, 1));

        assertFalse(far != null && far.hasTrain(), "a train setting its route up is drawn at the far end of it too: "
            + picture);
    }

    /**
     * A train the setup records where the railway stands it on no Point is drawn parked there, facing its way:
     * the error about it says it stands there, and a diagram that drew nothing read the square free.
     *
     * MUTATION: leave the trains on no Point out of the picture, and this fails.
     */
    @Test
    public void testATrainOnNoPointIsDrawnWhereItStands()
    {
        StubLayout layout = new StubLayout();

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        DiagramMonitor monitor = new DiagramMonitor(source(layout), new LinkedHashMap<String, ReducedEdge>(),
            new LinkedHashMap<String, TileKey>(), new DiagramMonitor.Publisher()
            {
                @Override
                public void publish(Map<TileKey, TileOverlay> overlays)
                {
                    published.add(new LinkedHashMap<>(overlays));
                }
            });

        Map<TileKey, Side> nowhere = new LinkedHashMap<>();

        nowhere.put(key("main", 3, 1), Side.W);

        monitor.setTrainsOnNoPoint(nowhere);

        monitor.refresh();

        TileOverlay drawn = published.isEmpty() ? null : published.get(published.size() - 1).get(key("main", 3, 1));

        assertTrue(drawn != null && drawn.isParked() && drawn.getFacing() == Side.W, "a train on no Point is not drawn"
            + " parked, facing west, where the setup records it: " + drawn);
    }

    /**
     * A train's icon is drawn with a white halo, so it reads as on top of the caption and the badge it stands over.
     * Its size is unchanged.
     *
     * The icon carries a thin white rim of its own - about a pixel and a half at a 60-pixel square - so its outermost
     * pixels were white before the halo too.  What the halo adds is depth: read in from the edge of what was painted, the
     * third layer is still white, where without it that layer is the icon's dark body.
     *
     * MUTATION: drop the halo and this fails.
     */
    @Test
    public void testATrainsIconHasAHalo()
    {
        int size = 60;

        org.traincontrol.automationui.TileOverlay overlay =
            org.traincontrol.automationui.TileOverlay.parked(org.traincontrol.automationui.TilePorts.Side.E);

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        overlay.paintTrain(g, size, size, null);

        g.dispose();

        // How deep each painted pixel lies: 1 at the edge, counting in from the nearest unpainted one
        int[][] depth = new int[size][size];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) <= 128)
                {
                    depth[y][x] = 0;
                    queue.add(new int[] {x, y});
                }
                else
                {
                    depth[y][x] = Integer.MAX_VALUE;
                }
            }
        }

        while (!queue.isEmpty())
        {
            int[] at = queue.poll();

            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            {
                int nx = at[0] + d[0], ny = at[1] + d[1];

                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;

                if (depth[ny][nx] > depth[at[1]][at[0]] + 1)
                {
                    depth[ny][nx] = depth[at[1]][at[0]] + 1;
                    queue.add(new int[] {nx, ny});
                }
            }
        }

        int third = 0, white = 0;

        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                if (depth[y][x] != 3) continue;

                third++;

                java.awt.Color c = new java.awt.Color(image.getRGB(x, y), true);

                if (c.getRed() > 200 && c.getGreen() > 200 && c.getBlue() > 200) white++;
            }
        }

        assertTrue(third > 20, "precondition: no icon was drawn");

        assertTrue(white >= third * 0.8, "only " + white + " of the " + third + " pixels three in from the edge of a"
            + " train's icon are white - it has only the icon's own thin rim, no halo, and runs into the caption and the"
            + " badge under it");
    }

    /**
     * The halo round a train's icon is a little narrower at the large size than its share of the icon would make it, and
     * the same at the small (OB-329, MT-707).
     *
     * Read along the rows through the icon, from the edge of what was painted in to its dark body - the halo and the
     * icon's own thin rim together: no more than 4 pixels at 60 and still 3 at 30.
     *
     * MUTATION: let the halo grow with the icon at the large size as before, and this fails.
     */
    @Test
    public void testTheHaloIsNarrowerAtTheLargeSize()
    {
        int large = whiteBeforeTheBody(60);
        int small = whiteBeforeTheBody(30);

        assertTrue(large <= 4, "at 60 pixels the halo and rim reach " + large + " pixels past the icon's body, where Adam"
            + " asked for them a little narrower than the 5 they were (OB-329)");

        assertTrue(small >= 3, "at 30 pixels the halo and rim reach only " + small + " pixels past the icon's body - the"
            + " small size was to stay as it was (OB-329)");
    }

    /** From the left edge of a parked train's paint to its dark body, the median over the rows through its middle third. */
    private static int whiteBeforeTheBody(int size)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        org.traincontrol.automationui.TileOverlay.parked(org.traincontrol.automationui.TilePorts.Side.E)
            .paintTrain(g, size, size, null);

        g.dispose();

        java.util.List<Integer> gaps = new java.util.ArrayList<>();

        for (int y = size / 3; y < size * 2 / 3; y++)
        {
            int painted = -1, body = -1;

            for (int x = 0; x < size && body < 0; x++)
            {
                int argb = image.getRGB(x, y);

                if (((argb >>> 24) & 0xFF) <= 128) continue;

                if (painted < 0) painted = x;

                int r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;

                if (Math.max(r, Math.max(gr, b)) < 100) body = x;
            }

            if (painted >= 0 && body >= 0) gaps.add(body - painted);
        }

        assertFalse(gaps.isEmpty(), "precondition: no icon was drawn at " + size + " pixels");

        java.util.Collections.sort(gaps);

        return gaps.get(gaps.size() / 2);
    }

    /**
     * A paused train the railway stands on no Point is drawn grey there, as every other paused train is.
     *
     * MUTATION: draw the trains on no Point without asking whether they are paused, and this fails.
     */
    @Test
    public void testAPausedTrainOnNoPointIsDrawnGrey()
    {
        StubLayout layout = new StubLayout();

        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

        DiagramMonitor monitor = new DiagramMonitor(source(layout), new LinkedHashMap<String, ReducedEdge>(),
            new LinkedHashMap<String, TileKey>(), overlays -> published.add(new LinkedHashMap<>(overlays)));

        Map<TileKey, Side> nowhere = new LinkedHashMap<>();

        nowhere.put(key("main", 3, 1), Side.W);
        nowhere.put(key("main", 6, 1), Side.E);

        monitor.setTrainsOnNoPoint(nowhere, java.util.Collections.singleton(key("main", 3, 1)));

        monitor.refresh();

        Map<TileKey, TileOverlay> picture = published.get(published.size() - 1);

        assertTrue(picture.get(key("main", 3, 1)) != null && picture.get(key("main", 3, 1)).isPaused(), "a paused train on"
            + " no Point is drawn as one that runs: " + picture.get(key("main", 3, 1)));

        assertFalse(picture.get(key("main", 6, 1)).isPaused(), "a train on no Point that is not paused is drawn paused");
    }

    /**
     * No frame draws a paused train on no Point as one that runs while the monitor is told which trains are paused (OB-343;
     * FR-126): the trains and the set of the paused must be written together, or a frame on the monitor's thread
     * between the two writes would publish the picture with no grey.
     *
     * The monitor is told the same picture over and over while frames are taken on another thread, and not one of them may
     * show the train without its grey.  A race, so a failure is a matter of chance - the more it is told, the surer - and a
     * pass proves nothing by itself; the mutation below fails it every time it is run.
     *
     * MUTATION: write the trains of the two-argument form and then empty the paused set before filling it, and this fails.
     *
     * @throws Exception from the frames' thread
     */
    @Test
    public void testNoFrameDrawsAPausedTrainOnNoPointWithoutItsGrey() throws Exception
    {
        StubLayout layout = new StubLayout();

        final java.util.concurrent.atomic.AtomicInteger bare = new java.util.concurrent.atomic.AtomicInteger();

        final TileKey greyed = key("main", 3, 1);

        final DiagramMonitor monitor = new DiagramMonitor(source(layout), new LinkedHashMap<String, ReducedEdge>(),
            new LinkedHashMap<String, TileKey>(), overlays ->
            {
                TileOverlay mark = overlays.get(greyed);

                if (mark != null && !mark.isPaused()) bare.incrementAndGet();
            });

        final Map<TileKey, Side> nowhere = new LinkedHashMap<>();

        nowhere.put(greyed, Side.W);
        nowhere.put(key("main", 6, 1), Side.E);

        final java.util.Set<TileKey> paused = java.util.Collections.singleton(greyed);

        monitor.setTrainsOnNoPoint(nowhere, paused);

        monitor.refresh();

        assertEquals(bare.get(), 0, "precondition: the first picture draws the paused train without its grey");

        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        Thread frames = new Thread(() ->
        {
            while (going.get()) monitor.refresh();
        });

        frames.setDaemon(true);
        frames.start();

        try
        {
            for (long end = System.currentTimeMillis() + 10000, told = 0; bare.get() == 0 && told < 2000000
                && System.currentTimeMillis() < end; told++)
            {
                monitor.setTrainsOnNoPoint(nowhere, paused);
            }
        }
        finally
        {
            going.set(false);

            frames.join(5000);
        }

        assertEquals(bare.get(), 0, "a frame drew the paused train on no Point without its grey while the monitor was told"
            + " which trains are paused");
    }

    /**
     * A double-curve sensor's arrival chevrons keep off its direction arrows and off each other (MT-714).
     *
     * A chevron is pushed along its side, off the middle where the direction arrow is - to the corner away from the track,
     * which on a double curve was never asked: every side has track and so has its opposite, so each chevron went the same
     * fixed way round and half of them landed on their own curve, over its arrow.  Pushed away from its OWN curve, two
     * chevrons share each free corner, and are drawn in nearer the middles of their sides so they do not meet.
     *
     * MUTATION: push a double curve's chevrons the fixed way round again, and the overlap with the arrows fails; push them
     * as far along as on any other square, and the overlap of two in one corner fails.
     */
    @Test
    public void testADoubleCurvesArrivalArrowsKeepOffItsArrowsAndEachOther()
    {
        for (int size : new int[] {30, 60})
        {
            for (int orientation = 0; orientation < 4; orientation++)
            {
                List<org.traincontrol.automationui.TilePorts.Route> roads = org.traincontrol.automationui.TilePorts.ports(
                    componentType.FEEDBACK_DOUBLE_CURVE, orientation, 0);

                for (boolean allowed : new boolean[] {true, false})
                {
                    List<TileAnnotation.Arrival> every = new ArrayList<>();

                    for (Side side : Side.values()) every.add(new TileAnnotation.Arrival(side, allowed));

                    // the arrows: every side's, both ways shown; the chevrons: drawn over roads that say nothing
                    boolean[][] arrows = ink(new TileAnnotation(marksOf(roads, TileGraph.Direction.TOWARD_A), -1, false,
                        null, false, true, false, null, false, null), size);

                    boolean[][] chevrons = ink(chevronsOn(roads, every), size);

                    int over = 0, chevronInk = 0;

                    for (int x = 0; x < size; x++)
                    {
                        for (int y = 0; y < size; y++)
                        {
                            if (!chevrons[x][y]) continue;

                            chevronInk++;

                            if (arrows[x][y]) over++;
                        }
                    }

                    assertTrue(chevronInk > 0, "precondition: no chevrons drawn at " + size + " pixels");

                    assertTrue(over <= chevronInk / 50, "the double curve's arrival chevrons, turned " + orientation + ", "
                        + (allowed ? "open" : "barred") + ", at " + size + " pixels lie over its direction arrows: " + over
                        + " of their " + chevronInk + " pixels - Adam: \"when a double curve with a s88 has arrival"
                        + " directions set, the arrow icons may overlap\"");

                    int alone = 0;

                    for (TileAnnotation.Arrival one : every)
                    {
                        boolean[][] single = ink(chevronsOn(roads, Arrays.asList(one)), size);

                        for (boolean[] column : single) for (boolean on : column) if (on) alone++;
                    }

                    assertTrue(chevronInk >= alone * 0.97, "two of the double curve's arrival chevrons, turned "
                        + orientation + ", " + (allowed ? "open" : "barred") + ", at " + size + " pixels lie over each other: "
                        + chevronInk + " pixels drawn together against " + alone + " one at a time");
                }
            }
        }
    }

    /** These roads, each one-way or both ways. */
    private static List<TileAnnotation.Mark> marksOf(List<org.traincontrol.automationui.TilePorts.Route> roads,
        TileGraph.Direction direction)
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        for (org.traincontrol.automationui.TilePorts.Route road : roads)
        {
            marks.add(new TileAnnotation.Mark(road.getA(), road.getB(), direction));
        }

        return marks;
    }

    /** Only these arrivals' chevrons: the roads open both ways, which the track diagram draws no arrow for. */
    private static TileAnnotation chevronsOn(List<org.traincontrol.automationui.TilePorts.Route> roads,
        List<TileAnnotation.Arrival> arrivals)
    {
        return new TileAnnotation(marksOf(roads, TileGraph.Direction.BOTH), -1, false, null, false, true, false, null, true,
            arrivals);
    }

    /** Which pixels of a white square this annotation draws on. */
    private static boolean[][] ink(TileAnnotation annotation, int size)
    {
        java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            annotation.paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        boolean[][] on = new boolean[size][size];

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                on[x][y] = (shot.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF;
            }
        }

        return on;
    }
}
