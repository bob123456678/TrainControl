import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.DiagramMonitor;
import org.traincontrol.base.TileGraph.TileKey;
import org.traincontrol.base.TileOverlay;
import org.traincontrol.base.TileOverlay.State;

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
public class testDiagramMonitor
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
            new LinkedHashMap<String, org.traincontrol.base.GraphReducer.ReducedEdge>(),
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

        // and a burst collapses into one recompute rather than fifty
        assertFalse(monitor.refreshIfDirty(),
            "with no layout there is nothing to publish, but the flag should still have cleared");
        assertFalse(monitor.refreshIfDirty(), "a second call with nothing new does nothing");
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
            new LinkedHashMap<String, org.traincontrol.base.GraphReducer.ReducedEdge>(),
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
            new LinkedHashMap<String, org.traincontrol.base.GraphReducer.ReducedEdge>(),
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
