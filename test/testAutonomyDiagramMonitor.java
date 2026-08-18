import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.DiagramMonitor;
import org.traincontrol.automationui.TileGraph.TileKey;
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
        assertNull(org.traincontrol.automationui.TileGraph.sideTowards(
            tile(0, 0), new TileKey("other", 1, 0)),
            "two pages were treated as one grid");

        assertNull(org.traincontrol.automationui.TileGraph.sideTowards(tile(0, 0), tile(1, 1)),
            "a diagonal is not a side");

        assertNull(org.traincontrol.automationui.TileGraph.sideTowards(tile(0, 0), tile(0, 0)),
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
     */
    @Test
    public void testFiringOnlyMarksDirtyAndPublishesNothing()
    {
        final List<Map<TileKey, TileOverlay>> published = new ArrayList<>();

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
                    published.add(overlays);
                }
            });

        for (int i = 0; i < 50; i++)
        {
            monitor.markDirty();
        }

        assertTrue(published.isEmpty(), "firing must not publish, however many times it fires");

        // and a burst collapses into ONE recompute rather than fifty: the flag says something moved,
        // not how often, so the first call does the work and the second finds nothing to do
        assertTrue(monitor.refreshIfDirty(), "fifty firings should leave exactly one recompute owed");
        assertFalse(monitor.refreshIfDirty(), "and nothing owed after it");
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

    private TileOverlay merge(State a, State b)
    {
        return new TileOverlay(a, false).merge(new TileOverlay(b, false));
    }
}
