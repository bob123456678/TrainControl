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

        // And the same square with a line but no STATE, which is where this actually bit.  Asserted on
        // an ACTIVE overlay alone the check could not fail: a state that is not IDLE is already enough
        // to paint, with or without segments, so the test agreed with the rule it was not testing.
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
     * TA-B9 applies here too: a null LayoutSource makes compute() return at its null-layout check
     * before doing anything, so "published stays empty" held no matter when compute ran - a mutation
     * making markDirty() compute and publish immediately (`dirty.set(true); refresh();`) would pass this
     * exactly as it stood.  Built with a real claimed path instead, the way
     * testATrainOnAClaimedPathIsPublishedAndFollowedAsItMoves is, so there is an actual picture that
     * markDirty() must NOT have published before refreshIfDirty() is asked.
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
        // on the firing thread, which may be holding the layout's own monitor.  There is a genuine
        // claimed path to publish here, so this can actually fail now, unlike against a null layout.
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
     * invalidate() is what the class above is named for, and nothing above calls it: both read the
     * field initialiser of a monitor that has just been constructed, where "there is always a picture"
     * is already true before invalidate() does anything.  A no-op invalidate() would pass both.
     *
     * The mechanism, per its own javadoc: it forgets the last published picture, so the next refresh()
     * republishes even an unchanged one - which matters after a view has been rebuilt and lost the
     * picture the monitor thinks is still current, so an identical picture is news to the new view.
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

    // =============================================================================================
    // The monitor actually running: a train on real track, and a picture published about it
    // =============================================================================================

    /**
     * A train on a claimed path is published as a lit line of squares, and it moves as the train does.
     *
     * TA-B9 of the 2026-08-24 test suite audit: every test above that installs a Publisher hands the
     * monitor a `LayoutSource` whose `get()` returns null, and `compute()` returns at its null-layout
     * check before it does anything.  So the milestone rule, the run concatenation, the location
     * fallback and the lock wash - the 128 lines the operator is actually watching - were reached by no
     * test at all, and a monitor that published nothing for ever passed the class.
     *
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
     * Mutations this must fail, all run 2026-08-25 against a mutant compiled outside the repository:
     *
     *   - `DiagramMonitor.refresh`, publish deleted (`if (publisher != null) publisher.publish(...)`
     *     removed): passed the whole class before; now fails 2 of 21, this test and the lock one.
     *   - `compute`, milestones ignored (`boolean reached = false`): fails 1 of 21, here, on "the far
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
     * The other half of `compute` no test reached (TA-B9).  Two separate lines of track: a train claims
     * the first, and the second is on its path's lock list - which on a real layout is the track a
     * conflicting move would use.  The operator has to be able to tell "my train is going here" from
     * "this is being held clear so it can", and until now nothing checked that either was drawn.
     *
     * Mutations this must fail, run 2026-08-25: deleting the publish in `refresh`, as above (2 of 21);
     * and separately, returning from `compute` before the lock-wash loop at its end, which fails this
     * test alone (1 of 21) - so the wash is covered here and nowhere else.
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
     * A running Layout, stubbed down to the three questions the monitor asks it.
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

        StubLayout()
        {
            super(null);
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

    // --- track, built the way testAutonomyDiagramReducer builds it ---------------------------------

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
     * OB-026, reported by Adam and then confirmed in a rendered picture: "when arriving at a curved
     * station the red trace draws a straight line on the tile, rather than following the shape of the
     * station. Running through curves looks OK."
     *
     * A segment is drawn from the midpoint of the side it came in by to the midpoint of the side it
     * leaves by. At the end of a run there is no side it leaves by, so the line ran to the tile's
     * geometric centre - which is ON the rail for a straight and nowhere near it for a curve, where the
     * track cuts the corner and never passes through the middle.
     *
     * The tile here is the shape that broke: track entering at the TOP and leaving at the EAST, which is
     * the curve at `1 - Main:0,11` the picture was taken of. Its rail runs from (30,0) to (60,30), so
     * the point half way along it is (45,15). The tile centre, (30,30), is well clear of the rail - far
     * enough that a line reaching it cannot be mistaken for one that stopped on the track.
     *
     * Painted rather than computed, because "where does the line stop" is a question about the picture,
     * and the three drawing defects before this one were all missed by reasoning about the code.
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
     * Whether the overlay drew anything at this pixel.
     */
    private boolean painted(java.awt.image.BufferedImage image, int x, int y)
    {
        return (image.getRGB(x, y) >>> 24) > 0;
    }


    /**
     * The dot marking where the train is sits on the rail too.
     *
     * Adam, triaging MT-117: "037 - Stars work, but are offcenter on curve stations." The star itself
     * has been on `trackCentre` since MT-057 - but the RUNNING overlay draws its own mark, the dot that
     * says which square of a claimed path actually holds the train, and that one was still centred on
     * the tile. On a straight the two agree; on a curve the badge and star sit on the corner the rail
     * cuts and the dot sits in the middle of the square, and what you see is a mark beside its own
     * station.
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
     * NR-9, from the night review. The two tests above hand the answer in - `paint(g, size, size,
     * new int[] {45, 15})` - so they pin the drawing and say nothing about the call that works out
     * those numbers, which is `LayoutLabel`'s `annotation.trackCentre(getWidth(), getHeight())`. That
     * is the usual result of extracting a rule and testing the extract: the rule is covered and the
     * call becomes the only uncovered part.
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
     * TD-4, from the three-day history review. OB-026 - "the end of a run stops in the middle of the
     * square rather than on the rail" - was fixed in `TileOverlay.paintRun`, and `trackCentre` was made
     * public and threaded through `LayoutLabel` to do it. Its javadoc claims the outcome: "the run line
     * and the badge now agree about where the track is."
     *
     * `paintTraces`, in the same class as `trackCentre`, still started from `{width / 2, height / 2}` -
     * so the editor's yellow trace, drawn on the same squares to answer the same question, kept the
     * defect that had just been fixed one painter along. Null ends are not hypothetical: the editor
     * builds them deliberately, "null at the ends of the run, where the line stops in the middle of the
     * square", which is exactly the first and last square of a traced path.
     *
     * The pixel test written for OB-026 drives `TileOverlay` only, so nothing noticed.
     *
     * **The trace is isolated by DIFFERENCE**, and the first version of this test was wrong for want of
     * that. It painted one tile and asked whether anything had been drawn near the rail - which passed
     * against the unfixed code, because the badge and the direction arrows are drawn on the rail too.
     * Subtracting a rendering without the trace from one with it leaves only the pixels the trace
     * added, which is the only thing this test is about.
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
     * FR-027. Adam: "add little opaque locomotive icon at the s88 where a train is while autonomy is
     * running (not while stationary)."
     *
     * The dot said WHERE a train was and nothing else. On a layout with several paths out at once,
     * which of those trains are moving and which are waiting at a platform is the question a glance at
     * the diagram could not answer, and it is the one this adds.
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

        // The dot is max(6, size/3) across, so nothing it draws reaches radius 8 at this size. The
        // icon is round(size * 0.72) across, so it fills most of the way to 14.
        int near = 9;
        int far = 14;

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
     * A square with a train running on it is drawn in front of the labels that sit over it.
     *
     * Adam, looking at the icon: "make sure it renders on top of the S88's.  Right now, it's a coin
     * toss."  It was not a toss - it was fixed and wrong, and looked like chance because it depended on
     * where the address number happened to fall on the tile.
     *
     * The overlay is painted after `super.paintComponent`, so it is reliably over the tile's OWN icon.
     * What it can never reach is a SIBLING: LayoutGrid adds the address and station labels as separate
     * components and z-orders them to the front, and no painting order inside one component gets over
     * something drawn after it. So the fix is in the component order, and so is the test.
     *
     * Both halves matter. Coming to the front is the feature; going back afterwards is what stops a
     * railway that has been run for an hour ending up with every square that ever held a train
     * permanently over its own address label.
     *
     * MUTATION: removing the liftAboveLabels call from setAutonomyOverlay fails the first half;
     * lifting unconditionally, or never releasing, fails the second.
     */
    @Test
    public void testASquareWithARunningTrainComesToTheFront() throws Exception
    {
        javax.swing.JPanel grid = new javax.swing.JPanel();

        org.traincontrol.gui.LayoutLabel tile =
            new org.traincontrol.gui.LayoutLabel(null, null, 30, null, false);

        javax.swing.JLabel address = new javax.swing.JLabel("16");

        grid.add(tile);
        grid.add(address);

        // What LayoutGrid does with an address label: to index 0, which is painted LAST and therefore
        // on top.  Without this line the test would be about a panel nothing covers.
        grid.setComponentZOrder(address, 0);

        assertEquals(grid.getComponentZOrder(address), 0,
            "precondition: the address label is where LayoutGrid puts it");

        assertTrue(grid.getComponentZOrder(tile) > 0,
            "precondition: the tile starts behind that label, which is the situation being fixed");

        tile.setAutonomyOverlay(new TileOverlay(State.IDLE, true, true, null));

        settle();

        assertEquals(grid.getComponentZOrder(tile), 0,
            "a square with a train running on it is still behind the address label, so the locomotive "
            + "is drawn and then covered by a number - which is what it looked like a coin toss "
            + "between");

        // And back down when it stops.
        tile.setAutonomyOverlay(new TileOverlay(State.IDLE, true, false, null));

        settle();

        assertTrue(grid.getComponentZOrder(tile) > grid.getComponentZOrder(address),
            "the square stayed in front after its train stopped. Every square that ever held a moving "
            + "train would end up permanently over its own address label, which is a diagram that "
            + "degrades the longer it is used");
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
     * Adam: "Minor: make the locomotive face the right direction by rotating it or flipping it."
     *
     * **The icon cannot simply be compared between two headings**, and that is the whole difficulty of
     * this test: an overlay carrying a heading also draws the LINE for it, and the line for a train
     * going east looks nothing like the line for one going north. Two pictures that differ would prove
     * only that the lines differ, which they did before this feature existed.
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
}
