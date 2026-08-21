import java.util.LinkedHashMap;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;

/**
 * Moving a tile on the diagram takes its whole setup with it.
 *
 * Adam nudged an S88 tile and lost the station on it - the designation, the name, the facing, the
 * arrival restrictions, the length, all of it.  Everything the setup holds is keyed by SQUARE, so a
 * tile that moves leaves the lot behind on coordinates that now hold no track; the next reconcile
 * then finds a station on a square with no sensor and drops it for good.
 *
 * The caption alone used to follow, which was worse than nothing following: the NAME moved and the
 * station under it did not, so the diagram looked right and the setup was in pieces.
 *
 * These tests are on the store rather than on the editor, because that is where a key is a key.  The
 * awkward case is the group drag, where every source square lands on another source square - the
 * reason the whole set moves in one call rather than one tile at a time.
 */
public class testAutonomyTileMove
{
    /**
     * A station, with everything hung off it, arrives at the new square intact.
     */
    @Test
    public void testEverythingAboutASquareTravelsWithIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = new TileKey("1 - Main", 14, 3);
        TileKey now = new TileKey("1 - Main", 15, 3);

        store.setStation(was, true);
        store.setPointName(was, "BottomInnerOtherside");
        store.setTileLength(was, 42);

        store.moveTiles(moving(was, now));

        assertFalse(store.isStation(was), "the old square is still a station, with no track on it");

        assertTrue(store.isStation(now), "the station did not travel, which is the whole bug - a "
            + "setup destroyed by nudging a tile one square");

        assertEquals(store.getPointName(now), "BottomInnerOtherside", "it arrived unnamed");

        assertNull(store.getPointName(was), "and left its name behind");

        assertEquals(store.getTileLength(now), 42, "the length stayed on the old square");
    }

    /**
     * A caption on a square that did NOT move still names the station that did.
     *
     * The difference between a square that moves and a square that is merely named by something else.
     * Move the second sort and the caption ends up on track nobody labelled; leave the reference
     * pointing at the old square and it names a station that is not there any more.
     */
    @Test
    public void testAReferenceToAMovedSquareIsRepointed()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey station = new TileKey("1 - Main", 14, 3);
        TileKey moved = new TileKey("1 - Main", 15, 3);
        TileKey caption = new TileKey("1 - Main", 14, 4);

        store.setStation(station, true);
        store.setCaption(caption, station);

        store.moveTiles(moving(station, moved));

        assertEquals(store.getCaptionTarget(caption), moved,
            "the label stayed where it was and went on naming the square the station used to be on");
    }

    /**
     * A group dragged one square right does not eat itself.
     *
     * Every source square lands on another source square, so moving them one at a time reads a store
     * that the previous move has already written.  Dragging LEFT happened to work, which is what made
     * the same bug in the captions look intermittent rather than wrong.
     */
    @Test
    public void testAGroupThatOverlapsItselfArrivesWhole()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey a = new TileKey("1 - Main", 5, 5);
        TileKey b = new TileKey("1 - Main", 6, 5);

        store.setStation(a, true);
        store.setPointName(a, "First");

        store.setStation(b, true);
        store.setPointName(b, "Second");

        Map<TileKey, TileKey> moves = new LinkedHashMap<>();

        moves.put(a, new TileKey("1 - Main", 6, 5));
        moves.put(b, new TileKey("1 - Main", 7, 5));

        store.moveTiles(moves);

        assertEquals(store.getPointName(new TileKey("1 - Main", 6, 5)), "First",
            "the first station did not land where it was dragged to");

        assertEquals(store.getPointName(new TileKey("1 - Main", 7, 5)), "Second",
            "the second was destroyed by the first arriving on top of it, which is what moving them "
            + "one at a time does");

        assertNull(store.getPointName(new TileKey("1 - Main", 5, 5)),
            "and the square they came from keeps nothing");
    }

    /**
     * A tile moved onto a square that is standing still replaces what was there.
     *
     * Which is what the diagram does: the track is overwritten, so the setup describing it has to go
     * too, or the new tile inherits a station it never was.
     */
    @Test
    public void testArrivingOnASettledSquareReplacesIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey from = new TileKey("1 - Main", 2, 2);
        TileKey onto = new TileKey("1 - Main", 3, 3);

        store.setPointName(from, "Arriving");
        store.setPointName(onto, "WasHere");

        store.moveTiles(moving(from, onto));

        assertEquals(store.getPointName(onto), "Arriving",
            "the square that was overwritten kept its old name, so the new tile inherited a station "
            + "it never was");
    }

    /**
     * Barred arrivals travel too, being one of the things that made the loss expensive.
     */
    @Test
    public void testArrivalRestrictionsTravel()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = new TileKey("1 - Main", 8, 1);
        TileKey now = new TileKey("1 - Main", 8, 2);

        java.util.Set<TilePorts.Side> barred = new java.util.LinkedHashSet<>();

        barred.add(TilePorts.Side.N);

        store.setBarredArrivals(was, barred);

        store.moveTiles(moving(was, now));

        assertTrue(store.getBarredArrivals(now).contains(TilePorts.Side.N),
            "an arrival restriction stayed on the old square, so the station arrives open on a side "
            + "somebody deliberately shut");

        assertTrue(store.getBarredArrivals(was).isEmpty(), "and the old square keeps none");
    }

    private static Map<TileKey, TileKey> moving(TileKey from, TileKey to)
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        out.put(from, to);

        return out;
    }
}
