package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.marklin.MarklinAccessory;

/**
 * Turning round: one sensor, two things a train may do there.
 *
 * The shape under test is taken from the sample layout, where it was written by hand.  BottomMainC and
 * BottomMainCTerm are both stations and both s88 4; both are entered from BottomMainCPre; one leaves to
 * BottomMainPost carrying on, the other to TunnelReversePre having turned round.  A single Point per
 * sensor cannot say that - arriving from the west and going on east is the same edge set as arriving
 * from the west and backing out west again - so either the reversal is unreachable or every passing
 * train performs one.
 *
 * The track built here is that junction:
 *
 *     F11 --- x --- SW --- x --- F4 --- x --- F12        (the running line, west to east)
 *                    |
 *                   F18                                  (a siding, trailing off behind F4)
 *
 * The switch faces east, so a train running WEST out of F4 can take the siding, and a train running
 * east out of F11 cannot.  Reaching F18 from F11 therefore means stopping at F4 and turning round -
 * which is precisely what the old hand-written graph spelt out with a second Point.
 *
 * @author Adam
 */
public class testAutonomyDiagramReversal
{
    /**
     * Nothing marked, and the impossible move is already gone.
     *
     * This test used to state the defect: with one Point per sensor, F11 -> F4 and F4 -> F18 both
     * existed, so a path to the siding was offered even though the second leg leaves by the side the
     * first arrived at - and a locomotive sent to F18 would simply never get there.  Splitting every
     * square by arrival side is what makes that sayable, so the marking is not what fixes it and this
     * now pins the fix rather than the fault.
     *
     * Running west, the siding is straight ahead and needs no reversal at all; running east it is
     * behind the train.  Two copies, and only one of them can reach it.
     */
    @Test
    public void testWithoutTheMarkTheReversalIsSimplyNotOffered() throws IOException
    {
        JSONObject built = build(junction(), stations(), Collections.<TileKey>emptySet(), extras());

        List<JSONObject> atFour = pointsNamed(built, "Main4");

        assertEquals(atFour.size(), 2, "two lines reach it, so it is two Points");

        for (JSONObject copy : atFour)
        {
            assertFalse(copy.optBoolean("terminus"), "and nothing about it reverses");
            assertFalse(copy.optBoolean("reversing"));
        }

        assertTrue(hasEdge(built, "Main4 (westbound)", "Siding18"),
            "running west the siding is straight ahead");

        assertFalse(hasEdge(built, "Main4 (eastbound)", "Siding18"),
            "running east it is behind the train, and nothing here lets it turn round");
    }

    /**
     * The BottomMainC shape, rebuilt from the diagram: a station marked terminus becomes a plain copy
     * and a terminus copy, sharing one s88, exactly as the hand-written file had it.
     */
    @Test
    public void testAMarkedStationBecomesAPlainCopyAndATerminusCopyOnOneS88() throws IOException
    {
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), extras());

        List<JSONObject> copies = pointsNamed(built, "Main4");

        assertEquals(copies.size(), 4, "two arrival sides, each with a plain and a turning copy");

        int termini = 0;

        for (JSONObject copy : copies)
        {
            assertEquals(copy.getInt("s88"), 4, "every copy is the same physical sensor");
            assertTrue(copy.getBoolean("station"), "and every copy is still a station");

            if (copy.optBoolean("terminus")) termini++;
        }

        assertEquals(termini, 2, "one turning copy per arrival side");
    }

    /**
     * Both copies hang off the same incoming point, which is what lets the path finder choose between
     * carrying on and turning round.  BottomMainCPre reached both BottomMainC and BottomMainCTerm.
     */
    @Test
    public void testBothCopiesAreReachableFromTheSameIncomingPoint() throws IOException
    {
        // West11 is marked as well, and has to be: it is a buffer stop.  Track runs east from it and
        // nowhere else, so a train there arrived from the east and is pointing west, and until somebody
        // says trains turn round there it cannot set off towards the junction at all.  That is the
        // derivation being right about the fixture rather than wrong about the split - what is under
        // test here is Main4, and West11 only has to be able to reach it.
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2), key("main", 1, 2)), extras());

        assertTrue(hasEdge(built, "West11", "Main4 (eastbound)"),
            "a train from the west can pass through");
        assertTrue(hasEdge(built, "West11", "Main4 (eastbound, reverse)"),
            "or stop and turn round, from the very same place");
    }

    /**
     * The whole point of splitting by arrival side: the plain copy goes ON, and only the turning copy
     * goes BACK.  Get this wrong in either direction and the feature is worse than not having it - one
     * way the siding stays unreachable, the other way every passing train reverses.
     */
    @Test
    public void testThePlainCopyCarriesOnAndOnlyTheTurningCopyGoesBack() throws IOException
    {
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), extras());

        // arrived from the west, still pointing east
        assertTrue(hasEdge(built, "Main4 (eastbound)", "East12"),
            "carrying on east is what the plain copy is for");
        assertFalse(hasEdge(built, "Main4 (eastbound)", "Siding18"),
            "the siding is behind it - reaching it without turning round is the bug");
        assertFalse(hasEdge(built, "Main4 (eastbound)", "West11"));

        // arrived from the west and turned round
        assertTrue(hasEdge(built, "Main4 (eastbound, reverse)", "Siding18"),
            "having turned round, the siding is straight ahead");
        assertFalse(hasEdge(built, "Main4 (eastbound, reverse)", "East12"),
            "and carrying on east is exactly what it cannot do");
    }

    /**
     * The other direction is the mirror image, not a special case: a train that arrived from the east
     * carries on west - to the siding or beyond - and only turns round to go back east.
     */
    @Test
    public void testTheOppositeDirectionMirrorsIt() throws IOException
    {
        // East12 is a buffer stop too, and marked for the same reason West11 is above
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2), key("main", 7, 2)), extras());

        assertTrue(hasEdge(built, "East12", "Main4 (westbound)"));
        assertTrue(hasEdge(built, "Main4 (westbound)", "Siding18"),
            "running west, the siding needs no reversal at all");
        assertTrue(hasEdge(built, "Main4 (westbound)", "West11"));
        assertFalse(hasEdge(built, "Main4 (westbound)", "East12"));

        assertTrue(hasEdge(built, "Main4 (westbound, reverse)", "East12"));
        assertFalse(hasEdge(built, "Main4 (westbound, reverse)", "Siding18"));
    }

    /**
     * A sensor that is not a station says the same thing as "reversing" rather than "terminus".  Same
     * physical act; the model spells it two ways, and which one is right depends on whether trains are
     * allowed to stop there.
     */
    @Test
    public void testANonStationMarkedCanReverseGetsReversingCopies() throws IOException
    {
        JSONObject built = build(junction(), stations(), marked(key("main", 5, 2)), extras());

        List<JSONObject> copies = pointsNamed(built, "Main4");

        assertEquals(copies.size(), 4);

        int reversing = 0;

        for (JSONObject copy : copies)
        {
            assertFalse(copy.getBoolean("station"));
            assertFalse(copy.optBoolean("terminus"), "a non-station never becomes a terminus");

            if (copy.optBoolean("reversing")) reversing++;
        }

        assertEquals(reversing, 2);
    }

    /**
     * A dead-end platform is marked terminus like any other and must NOT be split.  Splitting it would
     * produce a plain copy with nowhere to go - a station a train could reach and never leave.
     */
    @Test
    public void testADeadEndTerminusIsLeftWhole() throws IOException
    {
        LayoutDiagram page = page("main", 10, 5);

        feedback(page, 1, 2, 11);
        straight(page, 2, 2);
        straight(page, 3, 2);
        straight(page, 4, 2);
        feedback(page, 5, 2, 4);

        // END is a stub on its north side at orientation 0, so orientation 1 - three turns clockwise -
        // puts the stub west, facing the sensor.  Pointing it any other way leaves it unconnected, and
        // the test would then pass for the wrong reason.
        add(page, componentType.END, 6, 2, 1);

        JSONObject built = build(page, stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), extras());

        List<JSONObject> copies = pointsNamed(built, "Main4");

        // One, not two.  The plain copy would be a train that arrived and is pointing at the buffers
        // with no track ahead of it - a station autonomy could pick as a destination and never get the
        // train out of again - so it is not emitted at all where a turning copy exists to carry the
        // arrival.  The turning copy is the whole truth about a dead end.
        assertEquals(copies.size(), 1, "a dead end is the turning copy and nothing else");

        // And it has to carry what the mark means, or the platform emits no flag at all and its trains
        // run into the buffers.
        assertTrue(copies.get(0).optBoolean("terminus"),
            "the only thing a train can do at a dead end is arrive and turn round");
    }

    /**
     * The mark is an instruction about how to SHAPE the graph, not a property parseAuto has ever heard
     * of.  Emitting it would put an unknown key on every point of every generated configuration.
     */
    @Test
    public void testTheMarkItselfIsNeverEmitted() throws IOException
    {
        JSONObject extras = extras();
        JSONObject four = new JSONObject();
        four.put(AutonomyBuilder.CAN_REVERSE, true);
        extras.put(key("main", 5, 2).toString(), four);

        JSONObject built = build(junction(), stations(), marked(key("main", 5, 2)), extras);

        for (Object o : built.getJSONArray("points"))
        {
            assertFalse(((JSONObject) o).has(AutonomyBuilder.CAN_REVERSE),
                "canReverse is for the builder, not for the model");
        }
    }

    /**
     * An authored terminus flag is not carried onto the copies either.  It described the tile; on a
     * split tile turning round is what the COPY means, and letting it through would make the plain copy
     * reverse as well - which is the behaviour the split exists to separate.
     */
    @Test
    public void testTheAuthoredFlagDoesNotLeakOntoThePlainCopy() throws IOException
    {
        JSONObject extras = extras();
        JSONObject four = new JSONObject();
        four.put("terminus", true);
        four.put("maxTrainLength", 2);
        extras.put(key("main", 5, 2).toString(), four);

        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), extras);

        for (JSONObject copy : pointsNamed(built, "Main4"))
        {
            assertEquals(copy.optInt("maxTrainLength"), 2,
                "ordinary settings still reach every copy, as they did on BottomMainC");

            if (copy.getString("name").contains("reverse")) continue;

            assertFalse(copy.optBoolean("terminus"),
                "the plain copy must not inherit the flag that made the tile split");
        }
    }

    /**
     * Splitting a tile multiplies its edges, and every copy is still the same piece of track.  A lock
     * that named one of them has to name all of them, or two trains get sent over one rail.
     */
    @Test
    public void testALockNamesEveryCopyOfTheEdgeItLocks() throws IOException
    {
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), extras());

        assertNotNull(edge(built, "Main4 (eastbound, reverse)", "Siding18"),
            "the turning move should exist");

        Set<String> emitted = edgeNames(built);
        int checked = 0;

        // Every lock in the whole document, not just one edge's: a lock naming a Point that the split
        // renamed is a lock parseAuto quietly drops, and a dropped lock is two trains on one rail.
        for (Object o : built.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            if (!edge.has("lockedges")) continue;

            for (Object l : edge.getJSONArray("lockedges"))
            {
                JSONObject lock = (JSONObject) l;
                String name = lock.getString("start") + " -> " + lock.getString("end");

                assertTrue(emitted.contains(name), "lock names an edge that was never emitted: " + name);
                checked++;
            }
        }

        assertTrue(checked > 0, "a junction with a diverging route has locks worth checking");
    }

    /**
     * Marking nothing changes nothing.  The split is opt-in per tile, so a configuration that uses none
     * of this has to come out byte for byte as it did before.
     */
    @Test
    public void testAnUnmarkedLayoutIsUntouched() throws IOException
    {
        LayoutDiagram page = junction();

        GraphReducer reducer = reduce(page, stations(key("main", 5, 2)));

        String withoutFeature = new AutonomyBuilder(reducer, null)
            .withPointExtras(map(extras())).build();

        String withEmptyMark = new AutonomyBuilder(reducer, null)
            .withPointExtras(map(extras()))
            .withReversibleTiles(Collections.<TileKey>emptySet()).build();

        assertEquals(withEmptyMark, withoutFeature);
    }

    /**
     * A station autonomy may not choose says exactly that, and nothing else.
     *
     * The point of the flag.  Before it, the only way to keep autonomy off a station was to make it a
     * reversing one, which also turned every arriving train round and refused any path through - three
     * statements welded into one switch, two of which nobody had asked for.
     */
    @Test
    public void testAStationAutonomyMayNotChooseSaysSoAndNothingElse() throws IOException
    {
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            noTiles(), manualOnly(key("main", 5, 2)), extras());

        List<JSONObject> copies = pointsNamed(built, "Main4");

        // Two, because two lines reach this square and each arrival is its own Point - which is true of
        // every square and has nothing to do with the flag under test.  What matters is that BOTH copies
        // say the one thing that was asked for and nothing more.
        assertEquals(copies.size(), 2, "one Point per arrival side, as everywhere else");

        for (JSONObject copy : copies)
        {
            assertTrue(copy.getBoolean("station"), "it is still a station");
            assertFalse(copy.optBoolean("autoDestination", true),
                "autonomy is told to leave it alone");
            assertFalse(copy.optBoolean("reversing"), "and nothing turns an arriving train round");
            assertFalse(copy.optBoolean("terminus"));
        }
    }

    /**
     * A berth that turns trains round AND is left alone by autonomy - which is what a berth usually is,
     * and which could not be authored at all until now.
     *
     * As a reversing station plus a terminus it was two flags on one Point, and Point refuses that
     * combination in either order: the configuration failed wholesale rather than at the square that
     * caused it.  Said separately, the two do not touch.
     */
    @Test
    public void testABerthCanTurnTrainsRoundAndStillBeLeftAloneByAutonomy() throws IOException
    {
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), manualOnly(key("main", 5, 2)), extras());

        List<JSONObject> copies = pointsNamed(built, "Main4");

        assertEquals(copies.size(), 4, "being left alone says nothing about the geometry, so it splits");

        int termini = 0;

        for (JSONObject copy : copies)
        {
            assertFalse(copy.optBoolean("autoDestination", true));

            assertFalse(copy.optBoolean("reversing"),
                "a terminus and a reversing flag on one Point is exactly what Point refuses");

            if (copy.optBoolean("terminus")) termini++;
        }

        assertEquals(termini, 2, "one turning copy per arrival side, as for any other split");
    }

    /**
     * The default is not written out, so a configuration gains no noise from a switch nobody touched -
     * and a file from before this existed reads back as it always did.
     */
    @Test
    public void testAnOrdinaryStationSaysNothingAboutAutonomy() throws IOException
    {
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            noTiles(), noTiles(), extras());

        assertFalse(pointsNamed(built, "Main4").get(0).has("autoDestination"),
            "the default belongs in the model, not in every point of every file");
    }

    // --- the track ---------------------------------------------------------------------------------

    /**
     * The running line with a siding trailing off behind the junction sensor.
     *
     * SWITCH_LEFT at orientation 0 has its toe south, straight ahead north and its branch west.  Ports
     * rotate by (4 - orientation) quarter turns clockwise - see TilePorts.ports, which follows what
     * getImage does to the artwork - so orientation 1 is three turns: toe EAST, straight west, branch
     * south.  A train running west out of F4 therefore meets the toe and may diverge into the siding,
     * while a train running east out of F11 meets the same switch trailing and can only carry straight
     * on.  That asymmetry is the whole point: it is what makes the siding reachable only by turning
     * round at F4.
     */
    private LayoutDiagram junction() throws IOException
    {
        LayoutDiagram page = page("main", 10, 5);

        feedback(page, 1, 2, 11);
        straight(page, 2, 2);

        add(page, componentType.SWITCH_LEFT, 3, 2, 1, 7);
        wire(page, 3, 2, 7, Accessory.accessoryType.SWITCH);

        straight(page, 4, 2);
        feedback(page, 5, 2, 4);
        straight(page, 6, 2);
        feedback(page, 7, 2, 12);

        feedbackNS(page, 3, 3, 18);

        return page;
    }

    // --- helpers -----------------------------------------------------------------------------------

    private JSONObject build(LayoutDiagram page, Set<TileKey> stations, Set<TileKey> reversible,
        JSONObject extras)
    {
        return build(page, stations, reversible, noTiles(), extras);
    }

    private JSONObject build(LayoutDiagram page, Set<TileKey> stations, Set<TileKey> reversible,
        Set<TileKey> manualOnly, JSONObject extras)
    {
        GraphReducer reducer = reduce(page, stations);

        String json = new AutonomyBuilder(reducer, null)
            .withPointExtras(map(extras))
            .withReversibleTiles(reversible)
            .withParkingTiles(manualOnly)
            .build();

        return new JSONObject(json);
    }

    private GraphReducer reduce(LayoutDiagram page, final Set<TileKey> stations)
    {
        TileGraph graph = new TileGraph(
            new ArrayList<>(Arrays.asList(page)), Collections.<String>emptySet());

        // Switches default to base-to-forks - out of the toe only - which is a sensible starting point
        // on a real layout and an unrelated variable here.  Left alone, the junction below admits
        // trains from the east and nowhere else, so the sensor has ONE arrival side and never splits:
        // the test would then be measuring the default rather than the reversal.  Everything is opened
        // both ways so that what is asserted is the split and nothing else.
        for (TileKey tile : graph.getTiles().keySet())
        {
            for (RouteId routeId : graph.getRoutes(tile).keySet())
            {
                graph.setDirection(tile, routeId, Direction.BOTH);
            }
        }

        GraphReducer reducer = new GraphReducer(graph, new GraphReducer.Authored()
        {
            @Override
            public String getPointName(TileKey tile)
            {
                return names(tile);
            }

            @Override
            public boolean isStation(TileKey tile)
            {
                return stations.contains(tile);
            }

            @Override
            public int getTileLength(TileKey tile)
            {
                return 0;
            }
        });

        reducer.reduce();

        return reducer;
    }

    /**
     * Readable names, so a failure names a place on the railway rather than a coordinate.
     */
    private String names(TileKey tile)
    {
        if (tile.getX() == 1 && tile.getY() == 2) return "West11";
        if (tile.getX() == 5 && tile.getY() == 2) return "Main4";
        if (tile.getX() == 7 && tile.getY() == 2) return "East12";
        if (tile.getX() == 3 && tile.getY() == 3) return "Siding18";

        return null;
    }

    private Map<String, JSONObject> map(JSONObject extras)
    {
        Map<String, JSONObject> out = new java.util.LinkedHashMap<>();

        for (String key : extras.keySet())
        {
            out.put(key, extras.getJSONObject(key));
        }

        return out;
    }

    private JSONObject extras()
    {
        return new JSONObject();
    }

    private Set<TileKey> stations(TileKey... tiles)
    {
        return new LinkedHashSet<>(Arrays.asList(tiles));
    }

    private Set<TileKey> marked(TileKey... tiles)
    {
        return new LinkedHashSet<>(Arrays.asList(tiles));
    }

    /**
     * Stations autonomy may not choose for itself.
     */
    private Set<TileKey> manualOnly(TileKey... tiles)
    {
        return new LinkedHashSet<>(Arrays.asList(tiles));
    }

    private Set<TileKey> noTiles()
    {
        return Collections.<TileKey>emptySet();
    }

    private List<JSONObject> pointsNamed(JSONObject built, String base)
    {
        List<JSONObject> out = new ArrayList<>();

        for (Object o : built.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;
            String name = point.getString("name");

            if (name.equals(base) || name.startsWith(base + " (")) out.add(point);
        }

        return out;
    }

    private JSONObject edge(JSONObject built, String start, String end)
    {
        for (Object o : built.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            if (edge.getString("start").equals(start) && edge.getString("end").equals(end))
            {
                return edge;
            }
        }

        return null;
    }

    private boolean hasEdge(JSONObject built, String start, String end)
    {
        return edge(built, start, end) != null;
    }

    private Set<String> edgeNames(JSONObject built)
    {
        Set<String> out = new LinkedHashSet<>();

        JSONArray edges = built.getJSONArray("edges");

        for (Object o : edges)
        {
            JSONObject edge = (JSONObject) o;
            out.add(edge.getString("start") + " -> " + edge.getString("end"));
        }

        return out;
    }

    private LayoutDiagram page(String name, int sx, int sy)
    {
        return new LayoutDiagram(name, sx, sy, null, null);
    }

    private void add(LayoutDiagram page, componentType type, int x, int y, int orientation)
        throws IOException
    {
        add(page, type, x, y, orientation, 0);
    }

    private void add(LayoutDiagram page, componentType type, int x, int y, int orientation, int address)
        throws IOException
    {
        page.addComponent(type, x, y, orientation, 0, address, address, accessoryDecoderType.MM2, null);
    }

    private void straight(LayoutDiagram page, int x, int y) throws IOException
    {
        add(page, componentType.STRAIGHT, x, y, 0);
    }

    private void feedback(LayoutDiagram page, int x, int y, int rawAddress) throws IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, 0, 0, rawAddress / 2, rawAddress,
            accessoryDecoderType.MM2, null);
    }

    private void feedbackNS(LayoutDiagram page, int x, int y, int rawAddress) throws IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, 1, 0, rawAddress / 2, rawAddress,
            accessoryDecoderType.MM2, null);
    }

    private void wire(LayoutDiagram page, int x, int y, int address, Accessory.accessoryType type)
    {
        page.getComponent(x, y).setAccessory(
            new MarklinAccessory(null, address, type, accessoryDecoderType.MM2,
                "Switch " + address, false, 0));
    }

    private TileKey key(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    /**
     * A split copy never takes a name some other square already has.
     *
     * The base names are made unique, and the " (eastbound)" suffixes are added afterwards - so a point
     * named "Main (eastbound)" by hand and a neighbouring "Main" that splits produced two Points with
     * one name.  Layout refuses that, and refuses the whole configuration with it, reporting a Point
     * name that appears nowhere on the diagram.
     */
    @Test
    public void testASplitCopyNeverCollidesWithAnAuthoredName() throws Exception
    {
        LayoutDiagram page = page("main", 8, 4);

        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);
        straight(page, 4, 1);
        feedback(page, 5, 1, 13);

        final Map<TileKey, String> authored = new java.util.LinkedHashMap<>();

        // the middle sensor splits, because track reaches it from both sides
        authored.put(key("main", 3, 1), "Main");

        // and somebody has already used the name its eastbound copy wants
        authored.put(key("main", 1, 1), "Main (eastbound)");
        authored.put(key("main", 5, 1), "Far");

        JSONObject built = new JSONObject(new AutonomyBuilder(
            reduceWithNames(page, authored), null).build());

        Set<String> seen = new LinkedHashSet<>();

        for (Object o : built.getJSONArray("points"))
        {
            String name = ((JSONObject) o).getString("name");

            assertTrue(seen.add(name), "two Points came out called \"" + name + "\"");
        }
    }

    /**
     * A locomotive is emitted on the copy that is pointing the way it is pointing.
     *
     * The copy was chosen by comparing the recorded facing against each copy's ARRIVAL side, and a
     * turning copy's facing is its arrival side rather than the opposite of it - so a facing learned
     * from a train that had just reversed matched no copy, fell through to the first one, and put the
     * locomotive on the copy pointing the other way.  Its first move was then the backwards edge the
     * whole split exists to forbid.
     */
    @Test
    public void testAPlacedLocomotiveLandsOnTheCopyItIsFacing() throws Exception
    {
        LayoutDiagram page = page("main", 8, 4);

        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);
        straight(page, 4, 1);
        feedback(page, 5, 1, 13);

        // A train at the middle sensor, facing WEST - so it came from the east, and the copy it
        // belongs on is the one that leaves westward.
        JSONObject extras = new JSONObject()
            .put("loc", new JSONObject().put("name", "BR 218"))
            .put("facing", "W");

        Map<String, JSONObject> byTile = new java.util.LinkedHashMap<>();
        byTile.put(key("main", 3, 1).toString(), extras);

        JSONObject built = new JSONObject(new AutonomyBuilder(reduce(page, noTiles()), null)
            .withPointExtras(byTile)
            .build());

        List<String> carrying = new ArrayList<>();

        for (Object o : built.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (point.has("loc")) carrying.add(point.getString("name"));
        }

        assertEquals(carrying.size(), 1,
            "a locomotive is one object and stands on one copy - found " + carrying);

        assertTrue(carrying.get(0).contains("westbound"),
            "a train facing west belongs on the westbound copy, not " + carrying.get(0));
    }

    /**
     * Like reduce above, but with the point names authored by the test rather than by names() - the
     * split-copy test has to author a colliding name, which the fixed fixture names cannot express.
     * Every route is opened both ways for the same reason reduce does it.
     */
    private GraphReducer reduceWithNames(LayoutDiagram page, final Map<TileKey, String> authored)
    {
        TileGraph graph = new TileGraph(
            new ArrayList<>(Arrays.asList(page)), Collections.<String>emptySet());

        for (TileKey tile : graph.getTiles().keySet())
        {
            for (RouteId routeId : graph.getRoutes(tile).keySet())
            {
                graph.setDirection(tile, routeId, Direction.BOTH);
            }
        }

        GraphReducer reducer = new GraphReducer(graph, new GraphReducer.Authored()
        {
            @Override
            public String getPointName(TileKey tile)
            {
                return authored.get(tile);
            }

            @Override
            public boolean isStation(TileKey tile)
            {
                return false;
            }

            @Override
            public int getTileLength(TileKey tile)
            {
                return 0;
            }
        });

        reducer.reduce();

        return reducer;
    }
}
