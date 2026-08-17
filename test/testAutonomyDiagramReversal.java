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
     * Nothing marked: one Point at the junction, and nothing anywhere telling a train to turn round.
     *
     * This is the defect stated as a test.  The edges F11 -> F4 and F4 -> F18 both exist, so a path to
     * the siding is offered - but the second leaves by the side the first arrived at, and with one Point
     * there is no way to say so.  The locomotive would be sent to F18 and simply never get there.
     */
    @Test
    public void testWithoutTheMarkOneSensorIsOnePointAndNothingReverses() throws IOException
    {
        JSONObject built = build(junction(), stations(), Collections.<TileKey>emptySet(), extras());

        List<JSONObject> atFour = pointsNamed(built, "Main4");

        assertEquals(atFour.size(), 1, "an unmarked sensor is still exactly one Point");
        assertFalse(atFour.get(0).optBoolean("terminus"), "and nothing about it reverses");
        assertFalse(atFour.get(0).optBoolean("reversing"));

        // the path that needs a reversal is offered all the same, which is the problem
        assertTrue(hasEdge(built, "Main4", "Siding18"),
            "the siding is reachable on paper even though no train could actually get there");
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
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), extras());

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
        JSONObject built = build(junction(), stations(key("main", 5, 2)),
            marked(key("main", 5, 2)), extras());

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
        LayoutDiagram page = page("main", 8, 5);

        feedback(page, 1, 2, 11);
        straight(page, 2, 2);
        feedback(page, 3, 2, 4);
        add(page, componentType.END, 4, 2, 3);

        JSONObject built = build(page, stations(key("main", 3, 2)),
            marked(key("main", 3, 2)), extras());

        List<JSONObject> copies = pointsNamed(built, "Main4");

        assertEquals(copies.size(), 1, "one arrival side means there is nothing to split");
        assertTrue(copies.get(0).optBoolean("terminus"), "and it is still a terminus");
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

    // --- the track ---------------------------------------------------------------------------------

    /**
     * The running line with a siding trailing off behind the junction sensor.
     *
     * SWITCH_LEFT at orientation 0 has its toe south, straight ahead north and its branch west; three
     * quarter turns put the toe EAST, the straight west and the branch south.  So a train running west
     * out of F4 meets the toe and may diverge into the siding, while a train running east out of F11
     * meets the same switch trailing and can only carry straight on.  That asymmetry is the whole point:
     * it is what makes the siding reachable only by turning round at F4.
     */
    private LayoutDiagram junction() throws IOException
    {
        LayoutDiagram page = page("main", 10, 5);

        feedback(page, 1, 2, 11);
        straight(page, 2, 2);

        add(page, componentType.SWITCH_LEFT, 3, 2, 3, 7);
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
        GraphReducer reducer = reduce(page, stations);

        String json = new AutonomyBuilder(reducer, null)
            .withPointExtras(map(extras))
            .withReversibleTiles(reversible)
            .build();

        return new JSONObject(json);
    }

    private GraphReducer reduce(LayoutDiagram page, final Set<TileKey> stations)
    {
        TileGraph graph = new TileGraph(
            new ArrayList<>(Arrays.asList(page)), Collections.<String>emptySet());

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
}
