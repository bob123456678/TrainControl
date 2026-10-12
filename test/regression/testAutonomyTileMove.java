package regression;

import java.util.Arrays;
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

    /**
     * A page can be put back as it was, which is what the editor's undo rests on.
     *
     * The snapshot used to cover the CAPTIONS of a page and nothing else - enough while a caption was
     * the only thing the editor moved, and not enough the moment a tile started carrying its whole
     * setup with it.  Undo then put the track back and left the station wherever the move had taken
     * it, which is a worse state than either: the diagram says one thing and the setup another.
     */
    @Test
    public void testAPageGoesBackAsItWas()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = new TileKey("1 - Main", 14, 3);
        TileKey now = new TileKey("1 - Main", 15, 3);

        store.setStation(was, true);
        store.setPointName(was, "BottomInnerOtherside");
        store.setTileLength(was, 42);

        Map<String, Object> before = store.snapshotPage("1 - Main");

        store.moveTiles(moving(was, now));

        assertTrue(store.isStation(now), "the move did not happen, so the undo proves nothing");

        store.restorePage("1 - Main", before);

        assertTrue(store.isStation(was), "the station did not come back to the square it left");

        assertFalse(store.isStation(now),
            "and it is still ALSO at the square it was moved to - undo that only adds is how one "
            + "station becomes two");

        assertEquals(store.getPointName(was), "BottomInnerOtherside");

        assertEquals(store.getTileLength(was), 42);
    }

    /**
     * Undoing an edit on one page puts back the far end of a pairing it broke.
     *
     * B5. A portal\u2019s two halves are stored symmetrically and are normally on DIFFERENT pages;
     * protecting signals and blocked points cross pages the same way. `move` and `forget` have always
     * reached both halves - the base class says so in as many words - but the snapshot filtered on the
     * key\u2019s square alone, so an edit that broke the far page\u2019s half had no record of it to
     * put back.
     *
     * What that costs: delete one end of a page link, press undo, and the track comes back while the
     * other page still points at the square the undo just emptied. `reconcile` cannot see it - both
     * squares exist - so it survives every save, the far page\u2019s menu shows the link as unpaired,
     * and unpairing from that side is a no-op.
     *
     * The sibling test above - testRestoringOnePageDoesNotTouchAnother - is the boundary this must not
     * cross, and it does not: point NAMES have no square on the value side, so nothing about them is
     * captured by the other page\u2019s snapshot. What is captured is exactly the entries that MENTION
     * the page being restored.
     *
     * MUTATION: filtering the snapshot on the key alone again fails this.
     */
    @Test
    public void testUndoPutsBackBothEndsOfACrossPagePairing()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey here = new TileKey("1 - Main", 4, 4);
        TileKey far = new TileKey("2 - Bottom", 7, 7);

        store.pairPortals(here, far);

        assertEquals(store.getPortalPartner(far), here, "precondition: the pairing is symmetric");

        Map<String, Object> before = store.snapshotPage("1 - Main");

        // The edit, made from THIS page: unpairing one end removes the far page's half too, which is
        // the whole point - a pairing is symmetric, so any edit to one end is an edit to both.
        store.unpairPortal(here);

        assertNull(store.getPortalPartner(far),
            "precondition: unpairing one end takes the other end's pointer with it - if it does not, "
            + "there is nothing here for the restore to put back and this test proves nothing");

        store.restorePage("1 - Main", before);

        assertEquals(store.getPortalPartner(here), far, "this page's own end did not come back");

        assertEquals(store.getPortalPartner(far), here,
            "the FAR page still points nowhere. The undo brought this page's half of the link back "
            + "and left the other page pointing at a square it had just emptied - which reconcile "
            + "cannot see, because both squares exist, so it survives every save from then on");
    }

    /**
     * And putting one page back leaves the others alone.
     */
    @Test
    public void testRestoringOnePageDoesNotTouchAnother()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey here = new TileKey("1 - Main", 1, 1);
        TileKey elsewhere = new TileKey("2 - Bottom", 1, 1);

        store.setPointName(here, "OnPageOne");

        Map<String, Object> before = store.snapshotPage("1 - Main");

        store.setPointName(elsewhere, "OnPageTwo");
        store.setPointName(here, "Renamed");

        store.restorePage("1 - Main", before);

        assertEquals(store.getPointName(here), "OnPageOne", "the page did not go back");

        assertEquals(store.getPointName(elsewhere), "OnPageTwo",
            "restoring one page reached into another, which would undo edits made somewhere the "
            + "editor was never looking");
    }

    /**
     * A station guarded by two signals keeps both when it moves, and both are repointed when THEY move.
     *
     * The pairing is square to square at both ends, so a move has to be applied to the keys and to the
     * values - and now to every entry of a list of values rather than to one.  A signal left pointing
     * at coordinates that hold no track is dropped by the next reconcile, which is the same silent
     * loss that moving a station used to cause.
     */
    @Test
    public void testEverySignalGuardingAStationTravelsWithIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = new TileKey("1 - Main", 14, 3);
        TileKey now = new TileKey("1 - Main", 15, 3);

        TileKey north = new TileKey("1 - Main", 12, 3);
        TileKey south = new TileKey("1 - Main", 16, 3);
        TileKey southMoved = new TileKey("1 - Main", 16, 4);

        store.setStation(was, true);
        store.setProtectingSignals(was, Arrays.asList(north, south));

        assertEquals(store.getProtectingSignals(was), Arrays.asList(north, south),
            "a station could not be given two signals at all");

        Map<TileKey, TileKey> moves = new LinkedHashMap<>();

        moves.put(was, now);
        moves.put(south, southMoved);

        store.moveTiles(moves);

        assertTrue(store.getProtectingSignals(was).isEmpty(),
            "the old square still claims to be guarded, with no track on it");

        assertEquals(store.getProtectingSignals(now), Arrays.asList(north, southMoved),
            "the station arrived without both of its signals, or with one still pointing at the "
            + "square the signal left");
    }

    /**
     * A page put back brings back every signal on it, not the first one.
     */
    @Test
    public void testRestoringAPageBringsBackEverySignal()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey station = new TileKey("1 - Main", 1, 1);
        TileKey near = new TileKey("1 - Main", 2, 1);
        TileKey far = new TileKey("1 - Main", 3, 1);

        store.setStation(station, true);
        store.setProtectingSignals(station, Arrays.asList(near, far));

        Map<String, Object> before = store.snapshotPage("1 - Main");

        store.setProtectingSignal(station, near);

        store.restorePage("1 - Main", before);

        assertEquals(store.getProtectingSignals(station), Arrays.asList(near, far),
            "the discarded edit kept one of the two signals, which is the half-restored state a "
            + "snapshot exists to prevent");
    }

    /**
     * The one-signal calls still mean what they always did.
     *
     * Everything outside this feature - promoting a square, demoting it, the tests written when a
     * station had one signal - goes through the singular pair, and it has to keep replacing rather
     * than appending.
     */
    @Test
    public void testTheSingularCallStillReplacesAndClears()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey station = new TileKey("1 - Main", 1, 1);
        TileKey near = new TileKey("1 - Main", 2, 1);
        TileKey far = new TileKey("1 - Main", 3, 1);

        store.setProtectingSignals(station, Arrays.asList(near, far));
        store.setProtectingSignal(station, far);

        assertEquals(store.getProtectingSignals(station), Arrays.asList(far),
            "setting one signal added to the list instead of replacing it");

        assertEquals(store.getProtectingSignal(station), far);

        store.setProtectingSignal(station, null);

        assertTrue(store.getProtectingSignals(station).isEmpty(), "null did not unpair");

        assertNull(store.getProtectingSignal(station));
    }

    /**
     * One signal cannot be paired to the same station twice.
     */
    @Test
    public void testASignalIsNotPairedTwice()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey station = new TileKey("1 - Main", 1, 1);
        TileKey signal = new TileKey("1 - Main", 2, 1);

        store.setProtectingSignals(station, Arrays.asList(signal, signal));

        assertEquals(store.getProtectingSignals(station), Arrays.asList(signal),
            "the same signal is on the list twice, which would show it twice and command it twice");
    }

    /**
     * A facing travels with the tile it is about.
     *
     * A direction is keyed by the square AND the route across it - "page:x,y#state,index" - and the
     * mover matched whole keys, so it never matched one of these.  Every facing stayed on the square
     * the track had walked away from and was dropped by the next reconcile, which is exactly the loss
     * moving the setup was written to prevent.
     */
    @Test
    public void testAFacingTravelsWithTheTile()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = new TileKey("1 - Main", 14, 3);
        TileKey now = new TileKey("1 - Main", 15, 3);

        org.traincontrol.automationui.TileGraph.RouteId route =
            new org.traincontrol.automationui.TileGraph.RouteId(0, 0);

        store.setTileDirection(was, route, org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);

        store.moveTiles(moving(was, now));

        assertEquals(store.getTileDirection(now, route),
            org.traincontrol.automationui.TileGraph.Direction.TOWARD_A,
            "the facing did not travel, so the next reconcile drops it and the square goes back to "
            + "carrying trains both ways");

        assertNull(store.getTileDirection(was, route), "and it was left behind as well");
    }

    /**
     * A tile dragged onto a square takes that square's setup away, as the diagram takes its track.
     *
     * The mover only overwrote a landing square when the source had something to overwrite it with.
     * So plain track dragged over a station left the station - its name, its signals, its length -
     * attached to a square that now holds plain track, and reconcile never tidied it up because the
     * square still had a tile on it.  Worse than a loss: nothing anywhere looks wrong.
     */
    @Test
    public void testASquareLandedOnLetsGoOfWhatItKnew()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey plain = new TileKey("1 - Main", 9, 5);
        TileKey station = new TileKey("1 - Main", 10, 5);

        store.setStation(station, true);
        store.setPointName(station, "Platform 3");
        store.setTileLength(station, 42);
        store.setProtectingSignal(station, new TileKey("1 - Main", 11, 5));

        // the plain square carries nothing at all, which is the case that used to leave the station
        store.moveTiles(moving(plain, station));

        assertFalse(store.isStation(station),
            "the square still calls itself a station, with plain track on it");

        assertNull(store.getPointName(station), "and still carries the station's name");

        assertTrue(store.getProtectingSignals(station).isEmpty(),
            "and would still hold trains out of it with a signal");
    }

    /**
     * A square that is landed on AND moving away keeps what it is taking with it.
     *
     * The case a group drag is made of: every source square lands on another source square.  Letting
     * go of a landing square must not throw away a setup that is on its way somewhere.
     */
    @Test
    public void testASquareThatIsBothLandedOnAndMovingIsNotForgotten()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey first = new TileKey("1 - Main", 1, 1);
        TileKey second = new TileKey("1 - Main", 2, 1);
        TileKey third = new TileKey("1 - Main", 3, 1);

        store.setPointName(first, "First");
        store.setPointName(second, "Second");

        Map<TileKey, TileKey> moves = new LinkedHashMap<>();

        moves.put(first, second);
        moves.put(second, third);

        store.moveTiles(moves);

        assertEquals(store.getPointName(second), "First", "the first square did not arrive");

        assertEquals(store.getPointName(third), "Second",
            "the middle square was forgotten as a landing square, even though it was moving too - "
            + "which is every square of a group dragged one place along");
    }

    private static Map<TileKey, TileKey> moving(TileKey from, TileKey to)
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        out.put(from, to);

        return out;
    }

    /**
     * A snapshot is a COPY, so an edit that changes something in place cannot change the snapshot too.
     *
     * UR-5, from the uninformed review. `snapshotPage` copies each map, entry by entry, and shares the
     * VALUES. For eight of the ten collections that is exactly right - the values are strings and
     * numbers, which cannot change underneath anybody. Two of them hold something mutable:
     *
     *  - `stationSignals` maps a station to a LIST of signals, and `forgetTiles` calls `removeAll` on
     *    that list in place, so deleting the signal's square empties the snapshot's list as well.
     *  - `configurations` maps a name to a JSONObject holding the points, and `setPointProperty` does
     *    `points.getJSONObject(id).put(key, value)` - straight into the object the snapshot is holding.
     *
     * Undo then restores what it captured, which by then is the edit. The facings, placements, homes
     * and maximum lengths are all point properties, so this is most of what a station carries - and the
     * snapshot's own comment says why they are in it at all: "A snapshot without them restores half a
     * station."
     *
     * The failure is quiet in the worst way. Undo appears to work, the diagram goes back, and the
     * setup that was supposed to come back with it silently does not.
     *
     * Restore copies too, for the same reason in the other direction: an undo that hands the store the
     * snapshot's own lists leaves the two sharing again, and the next edit corrupts a snapshot that is
     * still being held for a second undo.
     */
    @Test
    public void testASnapshotDoesNotShareWhatTheStoreCanStillChange()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey station = new TileKey("1 - Main", 4, 4);
        TileKey signal = new TileKey("1 - Main", 5, 4);

        store.setStation(station, true);
        store.setProtectingSignal(station, signal);

        Map<String, Object> before = store.snapshotPage("1 - Main");

        // Deleting the signal's square: removeAll, straight into the list the snapshot is holding
        store.forgetTiles(Arrays.asList(signal));

        assertTrue(store.getProtectingSignals(station).isEmpty(),
            "the pairing did not go away, so the undo below proves nothing");

        store.restorePage("1 - Main", before);

        assertEquals(store.getProtectingSignals(station), Arrays.asList(signal),
            "undo did not bring the protecting signal back. The snapshot shared the store's own list "
            + "and forgetTiles emptied it in place, so what undo restored was the deletion (UR-5)");
    }

    /**
     * The same for the half of a station's setup that lives in the configuration.
     */
    @Test
    public void testASnapshotDoesNotSharePointProperties() throws Exception
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.createConfiguration("Undo test", null);
        store.setActiveConfiguration("Undo test");

        TileKey tile = new TileKey("1 - Main", 6, 2);

        org.json.JSONObject configuration = store.getConfiguration("Undo test");

        configuration.put("points", new org.json.JSONObject());
        configuration.getJSONObject("points").put(tile.toString(),
            new org.json.JSONObject().put("facing", "N"));

        Map<String, Object> before = store.snapshotPage("1 - Main");

        // What setPointProperty does: points.getJSONObject(id).put(key, value), in place
        configuration.getJSONObject("points").getJSONObject(tile.toString()).put("facing", "S");

        store.restorePage("1 - Main", before);

        assertEquals(store.getConfiguration("Undo test").getJSONObject("points")
            .getJSONObject(tile.toString()).getString("facing"), "N",
            "undo did not put the facing back. The snapshot held the very JSONObject that "
            + "setPointProperty writes into, so the facing, the placement, the home and the maximum "
            + "length are all captured by reference and undo restores the edit (UR-5)");
    }
}
