package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.GraphReducer.ReducedPoint;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Problem;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * The ground truth gate: reduce a real track diagram and compare what comes out against the autonomy
 * configuration somebody wrote for that same layout by hand.
 *
 * This is the whole point of R0.  Everything before it is unit tests agreeing with themselves; this is
 * the first time the port map, the tile graph and the reducer meet a diagram drawn by a person, with a
 * hand-built graph beside it saying what the answer should look like.
 *
 * It is deliberately NOT a pass/fail equality check.  The two are not supposed to match exactly:
 *
 *   - the generated graph should be a SUPERSET of Points, because every s88 becomes one whereas
 *     hand-authoring includes only the sensors somebody bothered with;
 *   - trailing moves are absent by default, so hand-written edges that trail through a switch will be
 *     missing until those branches are opened;
 *   - lock references are derived from shared tiles, so hand-written conservative locks may have no
 *     derived counterpart, which is fine, while a derived lock the author never wrote is worth reading.
 *
 * What matters is the report it prints.  Read that, not the assertion count.  The assertions here only
 * catch the reduction collapsing entirely - producing nothing, or refusing the diagram.
 *
 * @author Adam
 */
public class testAutonomyDiagramSampleLayout
{
    private static final String LAYOUT = "cs2_sample_layout";

    private MarklinControlStation model;
    private List<LayoutDiagram> pages;
    private TileGraph graph;
    private GraphReducer reducer;
    private JSONObject legacy;
    private Set<String> excludedPages = new LinkedHashSet<>();

    @BeforeClass
    public void setUp() throws Exception
    {
        try
        {
            build();
        }
        catch (Exception e)
        {
            // A configuration failure otherwise reports as five skipped tests and nothing else, which
            // says only that something went wrong somewhere
            System.out.println("\nGROUND TRUTH SETUP FAILED: " + e);
            e.printStackTrace(System.out);
            throw e;
        }
    }

    private void build() throws Exception
    {
        model = init(null, true, false, false, false);

        File folder = findLayoutFolder();

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        // The same URL shape syncLayoutsFromConfiguredSource builds for a local layout
        String path = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        // This layout has no magnetartikel.cs2 - it ships only the diagram and its autonomy file - and
        // the accessory list is used solely to pick a decoder protocol, so an empty one is correct here.
        pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        wireAccessories();

        // Three pages are outside autonomy, excluded the way a user would exclude them (author,
        // 2026-08-16: only Main and Bottom are used for autonomy; the rest are for convenience):
        //
        //   "4 - Combined" redraws tiles from the other pages.  Left in, it would mint a second Point
        //   for every sensor it shows and a parallel set of edges - exactly what the exclusion flag
        //   exists for, and this layout is the reason the plan predicted the case.
        //
        //   "5 - Test" is a scratch page, and it carries a scissors tile - a drawing convention for a
        //   double slip across two tiles, which cannot be expressed per tile and so disqualifies any
        //   page it appears on.  Finding one here is the disqualification working, not failing.
        //
        //   "3 - Top Parking" is a convenience view.  Every sensor the hand-built configuration uses is
        //   drawn on Main or Bottom, so excluding it costs nothing - and it removes the one genuine
        //   pairing ambiguity in this layout.
        Set<String> excluded = excludedPages;

        for (LayoutDiagram page : pages)
        {
            String name = page.getName().toLowerCase();

            if (name.contains("combined") || name.contains("test") || name.contains("parking"))
            {
                excluded.add(page.getName());
            }
        }

        graph = new TileGraph(pages, excluded);

        // With only Main and Bottom participating, the link pairing is forced: Main has one arrow
        // pointing at Bottom and Bottom has one pointing back, and nothing else is a candidate.  The two
        // arrows on Main that point at Top Parking are left unpaired, which is exactly what an unnamed
        // link should do - lead nowhere - now that their destination is outside autonomy.
        //
        // The CS2 file records only a destination PAGE, never a destination tile, which is why pairing
        // has to be authored at all: had Top Parking stayed in, its two arrows back to Main would have
        // made the pairing genuinely ambiguous and no amount of reading the file would settle it.
        TileKey mainLink = findLink("1 - Main", 15, 5);
        TileKey bottomLink = findLink("2 - Bottom", 10, 9);

        // Fail rather than carry on unpaired.  Tolerating a missing link tile let a wrong coordinate
        // masquerade as a diagram with no cross-page routes at all, which is indistinguishable in the
        // output from the pairing working and the two pages genuinely not connecting.
        assertNotNull(mainLink, "no link tile on Main at 15,5 - the pairing coordinates are stale");
        assertNotNull(bottomLink, "no link tile on Bottom at 10,9 - the pairing coordinates are stale");

        graph.pairPortals(mainLink, bottomLink);

        graph.validatePortals();

        File legacyFile = new File(findLayoutFolder(), "config/autorun/autonomy.json");

        assertTrue(legacyFile.isFile(), "hand-built autonomy config not found at "
            + legacyFile.getAbsolutePath());

        legacy = new JSONObject(new String(
            Files.readAllBytes(legacyFile.toPath()), StandardCharsets.UTF_8));

        // Nothing is authored on a freshly read diagram, which would leave every derived Point a
        // non-station - and a graph with no stations is one no train can be sent anywhere in, since a
        // train may pass through a non-station but never stop at one.  Take the station designations
        // from the hand-built file, which is the authored data this comparison is standing in for.
        reducer = new GraphReducer(graph, stationsFromLegacy());
        reducer.reduce();

        System.out.println(report(excluded));
    }

    /**
     * The link tile at these coordinates, if it is still there.  Returns null rather than failing, so a
     * layout edited later does not break the harness in a way that hides everything else it reports.
     */
    private TileKey findLink(String page, int x, int y)
    {
        TileKey key = new TileKey(page, x, y);

        org.traincontrol.base.LayoutDiagramComponent c = graph.getTiles().get(key);

        return c != null && c.isLink() ? key : null;
    }

    /**
     * Every sensor that exists on a page autonomy is actually looking at.
     *
     * A legacy point whose sensor lives only on an excluded page is out of scope by choice, not missing
     * by defect, so it is exempted from the completeness checks and reported separately.
     */
    private Set<Integer> derivableSensors()
    {
        Set<Integer> out = new LinkedHashSet<>();

        for (ReducedPoint p : reducer.getPoints().values())
        {
            out.add(p.getS88());
        }

        return out;
    }

    /**
     * Finds the sample layout whether the tests run from the project root or from a build directory.
     */
    private File findLayoutFolder()
    {
        File candidate = new File(LAYOUT);

        for (int up = 0; up < 4 && !candidate.isDirectory(); up++)
        {
            candidate = new File("../" + candidate.getPath());
        }

        return candidate;
    }

    /**
     * Attaches an accessory to every switch and signal, the way syncLayouts does when the application
     * loads a layout.
     *
     * parseLayout deliberately does not do this - it uses the accessory database only to pick a decoder
     * protocol - so a diagram parsed in isolation has addresses on its tiles but no Accessory objects
     * behind them.  Names are built exactly as the application builds them, since the name is what both
     * the model and the hand-written configuration key on.
     */
    private void wireAccessories()
    {
        for (LayoutDiagram page : pages)
        {
            for (org.traincontrol.base.LayoutDiagramComponent c : page.getAll())
            {
                if (!c.isSwitch() && !c.isSignal()) continue;

                // the application skips tiles drawn without a digital address, and so does this
                if (c.getAddress() <= 0) continue;

                org.traincontrol.base.Accessory.accessoryType type = c.isSignal()
                    ? org.traincontrol.base.Accessory.accessoryType.SIGNAL
                    : org.traincontrol.base.Accessory.accessoryType.SWITCH;

                c.setAccessory(accessory(c.getAddress(), type, c.getProtocol()));

                if (c.isThreeWay())
                {
                    c.setAccessory2(accessory(c.getAddress() + 1,
                        org.traincontrol.base.Accessory.accessoryType.SWITCH, c.getProtocol()));
                }
            }
        }
    }

    private MarklinAccessory accessory(int logicalAddress,
        org.traincontrol.base.Accessory.accessoryType type,
        org.traincontrol.base.Accessory.accessoryDecoderType protocol)
    {
        return new MarklinAccessory(null, logicalAddress - 1, type, protocol,
            MarklinAccessory.getNameWithProtocol(logicalAddress, type, protocol), false, 0);
    }

    /**
     * Prints the reduction, so a route somebody disputes can be checked against what was derived.
     *
     * Not an assertion - it asserts only that something was derived at all.  Its job is to put the
     * edges in front of a person: which Points exist, what joins them, and which squares each join
     * runs over.  A drawn line can be followed wrongly by eye; this cannot be argued with.
     *
     * Ordered by name so two runs can be diffed against each other, and so a particular sensor can be
     * found by searching the output rather than by reading all of it.
     */
    @Test
    public void printTheDerivedEdgesForInspection() throws Exception
    {
        assertFalse(reducer.getPoints().isEmpty(), "nothing was derived");

        Map<TileKey, String> names = new AutonomyBuilder(reducer, null).uniqueNames();

        System.out.println();
        System.out.println("=== POINTS (" + reducer.getPoints().size() + ") ===");

        List<String> lines = new ArrayList<>();

        for (ReducedPoint point : reducer.getPoints().values())
        {
            lines.add(String.format("  %-28s s88 %-6s %s%s",
                names.get(point.getTile()), point.getS88(), point.getTile(),
                point.isStation() ? "  [station]" : ""));
        }

        Collections.sort(lines);

        for (String line : lines) System.out.println(line);

        System.out.println();
        System.out.println("=== EDGES (" + reducer.getEdges().size() + ") ===");

        lines.clear();

        for (ReducedEdge edge : reducer.getEdges())
        {
            StringBuilder over = new StringBuilder();

            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (over.length() > 0) over.append(' ');

                over.append(step.getTile().getX()).append(',').append(step.getTile().getY());
            }

            lines.add(String.format("  %-26s -> %-26s  leaves %s, arrives %s, over [%s]",
                names.get(edge.getStart()), names.get(edge.getEnd()),
                edge.getExitSide(), edge.getEntrySide(),
                over.length() == 0 ? "adjacent" : over.toString()));
        }

        Collections.sort(lines);

        for (String line : lines) System.out.println(line);

        System.out.println();
        System.out.println("=== PROBLEMS ===");

        for (Problem problem : graph.getProblems())
        {
            System.out.println("  " + (problem.isBlocking() ? "ERROR   " : "warning ")
                + problem.getTile() + "  " + problem.getMessageKey());
        }

        for (Problem problem : reducer.getProblems())
        {
            System.out.println("  " + (problem.isBlocking() ? "ERROR   " : "warning ")
                + problem.getTile() + "  " + problem.getMessageKey());
        }
    }

    /**
     * A train may not leave a Point by the side it arrived at.
     *
     * This is the one rule a Point-and-edge graph cannot state.  The reduction is honest about the
     * TRACK - if two sensors are joined, trains run between them both ways, and both edges are real -
     * but a journey is not free to use them consecutively: leaving by the side you came in on means
     * reversing, and a train only reverses where the railway has somewhere for it to do so.  Nothing in
     * the running model prevents it, because the model has no idea which way the train is pointing; it
     * knows only which Point it is standing on.
     *
     * The hand-built configuration solved this by hand, which is visible in the file: TunnelPre -> Tunnel
     * exists and Tunnel -> TunnelPre does not, and the way back out of that sensor is a SECOND Point on
     * the same s88 (TunnelReverse) with its own edges.  One-way edges and doubled Points are not the
     * author being cautious - they are how the direction a train is facing was written down.
     *
     * Checked on the BUILT graph rather than on the reduction, which is where the answer lives.  The
     * reduction is meant to keep both directions - the track does run both ways - and the builder is
     * what turns each square into one Point per arrival side, so that leaving by the side you came in at
     * is not a move any single Point has.  Asserting on the reduction would be asserting that the track
     * is one-way, which it is not.
     *
     * A Point emitted as a terminus or a reversing point is exempt: that IS the copy that turns trains
     * round, and it exists precisely to have the move everything else is denied.
     *
     * Both reductions are built.  As authored, most branches are closed and a reversal has no edge to be
     * made of, so that reading understates the problem badly; with every branch open is the shape a
     * finished setup has, and is where the real count lives.
     */
    @Test
    public void testATrainCannotLeaveBySideItArrivedAt()
    {
        List<String> turns = new ArrayList<>();

        turns.addAll(turnsOffered(reducer, "as authored"));
        turns.addAll(turnsOffered(reduceWithEveryBranchOpen(), "with every branch open"));

        assertTrue(turns.isEmpty(), turns.size()
            + " emitted Points let a train leave by the side it arrived at, which it cannot do without a"
            + " reversing point:\n" + String.join("\n", turns));
    }

    /**
     * The emitted Points of one build that offer a train the way it came, printed and returned.
     */
    private List<String> turnsOffered(GraphReducer from, String label)
    {
        AutonomyBuilder builder = new AutonomyBuilder(from, null);

        Map<String, ReducedEdge> byName = builder.edgesByName();

        // Emitted Point name -> the sides trains reach it by and leave it by.  Taken from the emitted
        // EDGE names, since those are what the running model would actually offer.
        Map<String, Set<Side>> arrivals = new LinkedHashMap<>();
        Map<String, Set<Side>> departures = new LinkedHashMap<>();

        for (Map.Entry<String, ReducedEdge> entry : byName.entrySet())
        {
            String[] ends = entry.getKey().split(" -> ", 2);

            if (ends.length < 2) continue;

            ReducedEdge edge = entry.getValue();

            // A move through a link has no side on the grid, so it cannot be told apart from any other
            // arrival and is left out rather than guessed at
            if (edge.getExitSide() != null) named(departures, ends[0]).add(edge.getExitSide());
            if (edge.getEntrySide() != null) named(arrivals, ends[1]).add(edge.getEntrySide());
        }

        Set<String> turnsHere = new LinkedHashSet<>();

        for (Object o : new JSONObject(builder.build()).getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (point.optBoolean("terminus") || point.optBoolean("reversing"))
            {
                turnsHere.add(point.getString("name"));
            }
        }

        List<String> turns = new ArrayList<>();

        for (String name : arrivals.keySet())
        {
            if (turnsHere.contains(name)) continue;

            for (Side side : named(arrivals, name))
            {
                if (!named(departures, name).contains(side)) continue;

                turns.add("   [" + label + "] " + name + ": arrives " + side
                    + ", and may leave " + side + " again");
            }
        }

        Collections.sort(turns);

        System.out.println();
        System.out.println("=== WHERE A TRAIN COULD TURN ROUND, " + label + " ===");
        System.out.println("emitted Points: " + arrivals.size()
            + ", of which trains may turn round at " + turnsHere.size());
        System.out.println("Points offering a turn a train cannot make: " + turns.size());

        for (String line : turns) System.out.println(line);

        return turns;
    }

    private Set<Side> named(Map<String, Set<Side>> map, String name)
    {
        Set<Side> sides = map.get(name);

        if (sides == null)
        {
            sides = new LinkedHashSet<>();
            map.put(name, sides);
        }

        return sides;
    }

    /**
     * The case the author raised: a train that reached BottomMainA from the Tunnel end must not be able
     * to go straight back to the Tunnel.  It cannot turn round anywhere between the two - the reversing
     * point is further up the line - so the move is impossible on the railway however the graph reads.
     *
     * Named rather than left to the sweep above because it is the case a person can check by eye against
     * the diagram, and because a sweep that is failing everywhere proves nothing about any one square.
     *
     * Checked against the wide-open reduction as well as the authored one.  As authored the branch back
     * out of BottomMainA is closed by default, so there is no edge for the reversal to be made of and
     * this passes without having established anything - a green light for the wrong reason is worse than
     * no test, because it is read as evidence.
     */
    @Test
    public void testATrainReachingBottomMainAFromTheTunnelCannotGoBack()
    {
        Integer arrivingAt = sensorOf("BottomMainA");
        Integer comingFrom = sensorOf("BottomMainAPre");

        assertNotNull(arrivingAt, "BottomMainA is not in the hand-built file any more");
        assertNotNull(comingFrom, "BottomMainAPre is not in the hand-built file any more");

        List<String> offending = new ArrayList<>();

        offending.addAll(reversalsAt(reducer, "as authored", comingFrom, arrivingAt));
        offending.addAll(reversalsAt(reduceWithEveryBranchOpen(), "with every branch open",
            comingFrom, arrivingAt));

        assertTrue(offending.isEmpty(), "a train that reached BottomMainA from the tunnel end can"
            + " reverse out of it without a reversing point:\n   " + String.join("\n   ", offending));
    }

    /**
     * Every way a train that arrived at one sensor from another could immediately go back, judged on the
     * emitted Points rather than on the squares - which is the whole difference the split makes.  The
     * train is standing on ONE copy of the square, so only that copy's edges are its choices.
     */
    private List<String> reversalsAt(GraphReducer from, String label, Integer comingFrom,
        Integer arrivingAt)
    {
        AutonomyBuilder builder = new AutonomyBuilder(from, null);

        Map<String, TileKey> tiles = builder.tilesByName();
        Set<String> edgeNames = builder.edgesByName().keySet();

        List<String> offending = new ArrayList<>();

        for (String inbound : edgeNames)
        {
            String[] ends = inbound.split(" -> ", 2);

            if (ends.length < 2) continue;

            if (!carries(from, tiles.get(ends[0]), comingFrom)) continue;
            if (!carries(from, tiles.get(ends[1]), arrivingAt)) continue;

            for (String outbound : edgeNames)
            {
                String[] back = outbound.split(" -> ", 2);

                if (back.length < 2 || !back[0].equals(ends[1])) continue;

                if (!carries(from, tiles.get(back[1]), comingFrom)) continue;

                offending.add("[" + label + "] " + inbound + ", then straight back: " + outbound);
            }
        }

        return offending;
    }

    /**
     * Whether the Point on this tile watches the given sensor.
     */
    private boolean carries(GraphReducer from, TileKey tile, Integer s88)
    {
        if (tile == null) return false;

        ReducedPoint point = from.getPoints().get(tile);

        return point != null && s88 != null && point.getS88() == s88;
    }

    /**
     * The reduction must produce a graph at all.  If this fails, nothing below it is worth reading.
     */
    @Test
    public void testTheDiagramReducesToSomething()
    {
        assertFalse(reducer.getPoints().isEmpty(), "no Points were derived from a real layout");
        assertFalse(reducer.getEdges().isEmpty(), "no edges were derived from a real layout");
    }

    /**
     * A real diagram should not be refused.  A blocking problem here is a genuine finding - either the
     * layout has an unmapped switch, or the port map is misreading something.
     */
    @Test
    public void testTheDiagramIsNotRefused()
    {
        List<String> blocking = new ArrayList<>();

        for (Problem p : graph.getProblems())
        {
            if (p.isBlocking()) blocking.add(p.toString());
        }

        assertTrue(blocking.isEmpty(), "the sample layout was refused: " + blocking);
    }

    /**
     * Every sensor the hand-built configuration relies on must exist in the derived graph.  A Point only
     * the legacy file knows about means the diagram failed to derive something real, which is the one
     * direction of difference that is always a defect.
     */
    @Test
    public void testEverySensorTheLegacyConfigUsesIsDerived()
    {
        Set<Integer> derived = new HashSet<>();

        for (ReducedPoint p : reducer.getPoints().values())
        {
            derived.add(p.getS88());
        }

        List<String> missing = new ArrayList<>();

        for (Object o : legacy.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (!point.has("s88") || point.isNull("s88")) continue;

            int s88 = point.getInt("s88");

            if (!derived.contains(s88))
            {
                missing.add(point.getString("name") + " (s88 " + s88 + ")");
            }
        }

        // Every sensor the hand-built configuration uses is now drawn on a participating page, so this
        // is a plain requirement again: one it relies on that the diagram does not derive means the
        // diagram failed to read something real.  (An allowance lived here while two parking sensors
        // appeared only on the excluded page; the layout was corrected instead.)
        assertTrue(missing.isEmpty(),
            "sensors the hand-built graph uses but the diagram did not derive: " + missing);
    }

    /**
     * The completeness question, asked properly.
     *
     * Comparing edges one for one understates the match badly, because the two graphs cut the railway at
     * different places: every s88 is a Point here, whereas the hand-built graph skips the sensors nobody
     * bothered with, so one legacy edge routinely spans several derived ones.  What matters is not
     * whether a single derived edge has the same endpoints, but whether a train could still get from one
     * end to the other.
     *
     * So: for every legacy edge, is its destination reachable from its origin in the derived graph?
     * Anything unreachable is track the diagram failed to derive - or, until links are paired, a route
     * that crosses pages.
     */
    @Test
    public void testEveryLegacyConnectionIsStillReachable()
    {
        Map<Integer, Set<Integer>> adjacency = derivedAdjacency();
        Set<Integer> derivable = derivableSensors();

        List<String> unreachable = new ArrayList<>();

        for (Object o : legacy.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            Integer from = sensorOf(edge.getString("start"));
            Integer to = sensorOf(edge.getString("end"));

            if (from == null || to == null || from.equals(to)) continue;

            // a connection to a sensor autonomy is not looking at is out of scope, not unreachable
            if (!derivable.contains(from) || !derivable.contains(to)) continue;

            if (!reachable(adjacency, from, to))
            {
                unreachable.add(edge.getString("start") + " -> " + edge.getString("end"));
            }
        }

        // Now the same question with every switch branch opened both ways.
        //
        // Switches default to base-to-forks: a train may fan out of a toe but never merge back into one.
        // That is deliberate, and on a real layout it severs most of the network, because nearly every
        // route needs a trailing move somewhere.  So the interesting number is not how much is
        // unreachable under the default - it is how much is STILL unreachable once direction is out of
        // the way.  Anything left is geometry the engine genuinely cannot express, and that is a defect;
        // the difference between the two is authoring work, and that is not.
        List<String> unreachableWideOpen = unreachableWithEveryBranchOpen();

        System.out.println("\n--- reachability of the hand-built connections ---");
        System.out.println("unreachable as authored (switches default base-to-forks): "
            + unreachable.size());
        System.out.println("unreachable with every branch opened both ways:           "
            + unreachableWideOpen.size() + "   <- this is the one that matters");

        if (!unreachableWideOpen.isEmpty())
        {
            System.out.println(unreachableWideOpen);
        }

        System.out.println();

        assertTrue(unreachableWideOpen.isEmpty(),
            "even with every switch branch open, the derived graph cannot get from one end to the "
            + "other of " + unreachableWideOpen.size() + " hand-built connections: "
            + unreachableWideOpen);
    }

    /**
     * Can two trains collide?
     *
     * Comparing lock counts tells us nothing - the two graphs cut the railway in different places, so 118
     * hand-written references and 96 derived ones are not comparable quantities.  What IS comparable is
     * the track: two routes can collide if and only if they occupy the same tile, and that truth belongs
     * to neither graph, so it can referee both.
     *
     * Each hand-built edge is given a physical extent by walking the derived tile graph between its two
     * sensors.  Then, for every pair of hand-built edges that turn out to share tiles, the hand-built
     * configuration is asked whether it knew: does it lock them against each other, or are they the same
     * edge?  Anything it missed is a pair of routes it would run at once over shared track.
     *
     * The derived side is checked the other way round - by construction it locks every shared tile, so
     * this asserts that construction actually holds, since a lock derivation that quietly dropped pairs
     * would be the most dangerous defect in the project.
     */
    @Test
    public void testNoTwoRoutesCanOccupyTheSameTrackUnlocked()
    {
        GraphReducer wideOpen = reduceWithEveryBranchOpen();

        // --- the derived graph: every pair of edges sharing a tile must be locked -----------------
        List<String> derivedGaps = new ArrayList<>();

        List<ReducedEdge> edges = new ArrayList<>(wideOpen.getEdges());

        for (int i = 0; i < edges.size(); i++)
        {
            for (int j = i + 1; j < edges.size(); j++)
            {
                ReducedEdge a = edges.get(i);
                ReducedEdge b = edges.get(j);

                // an edge and its reverse are one piece of track, not two claims on it
                if (a.getStart().equals(b.getEnd()) && a.getEnd().equals(b.getStart())) continue;

                if (!sharesATile(a, b)) continue;

                Set<ReducedEdge> locked = wideOpen.getLocks().get(a);

                if (locked == null || !locked.contains(b))
                {
                    if (derivedGaps.size() < 10) derivedGaps.add(a + "  ||  " + b);
                }
            }
        }

        // --- the hand-built graph, judged by the same tiles ---------------------------------------
        Map<String, Set<TileKey>> legacyExtent = new LinkedHashMap<>();
        Map<String, JSONObject> legacyEdges = new LinkedHashMap<>();
        int ambiguous = 0;

        for (Object o : legacy.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            Integer from = sensorOf(edge.getString("start"));
            Integer to = sensorOf(edge.getString("end"));

            if (from == null || to == null) continue;

            // Only judge edges whose physical extent is EXACT.
            //
            // A hand-built edge has no tiles of its own, so its extent has to be inferred - and where it
            // spans several derived edges the inference is a guess, because with every branch open the
            // graph offers many ways between two sensors and the shortest is not necessarily the one the
            // author drew.  That guess produced nonsense: five separate hand-built edges came out sharing
            // the same seventeen tiles, all of them routed down one corridor the walk happened to like.
            //
            // Where a single derived edge joins the two sensors there is no guess: that IS the track
            // between them.  Judging only those covers less but says something true, which is worth more
            // than a large number nobody can act on.
            Set<TileKey> tiles = exactExtent(wideOpen, from, to);

            if (tiles == null)
            {
                ambiguous++;
                continue;
            }

            String id = edge.getString("start") + " -> " + edge.getString("end");
            legacyExtent.put(id, tiles);
            legacyEdges.put(id, edge);
        }

        List<String> legacyGaps = new ArrayList<>();
        int legacyCovered = 0;
        int sharedPoint = 0;

        List<String> ids = new ArrayList<>(legacyExtent.keySet());

        for (int i = 0; i < ids.size(); i++)
        {
            for (int j = i + 1; j < ids.size(); j++)
            {
                String one = ids.get(i);
                String two = ids.get(j);

                Set<TileKey> shared = new LinkedHashSet<>(legacyExtent.get(one));
                shared.retainAll(legacyExtent.get(two));

                if (shared.isEmpty()) continue;

                // Sharing a point already keeps these apart - one train per point, and a path to an
                // occupied destination is refused - so no lock reference is needed or expected
                if (sharesAnEndpoint(legacyEdges.get(one), legacyEdges.get(two)))
                {
                    sharedPoint++;
                    continue;
                }

                if (legacyLocksTogether(legacyEdges.get(one), legacyEdges.get(two)))
                {
                    legacyCovered++;
                }
                else
                {
                    legacyGaps.add(one + "  ||  " + two + "   sharing " + shared.size() + " tile(s)");
                }
            }
        }

        System.out.println("\n--- mutual exclusion, judged by shared track ---");
        System.out.println("derived: pairs sharing a tile but NOT locked:  " + derivedGaps.size()
            + "   <- must be zero");

        for (String g : derivedGaps)
        {
            System.out.println("   " + g);
        }

        System.out.println("hand-built edges with an exact extent:      " + legacyExtent.size()
            + " of " + (legacyExtent.size() + ambiguous) + "   (the rest span several derived edges, so "
            + "their track cannot be pinned down)");
        System.out.println("hand-built: overlapping pairs sharing a point: " + sharedPoint
            + "   (kept apart by occupancy, no lock needed)");
        System.out.println("hand-built: overlapping pairs it does lock:    " + legacyCovered);
        System.out.println("hand-built: overlapping pairs it does NOT:     " + legacyGaps.size()
            + "   <- shared track, no shared point, no lock");

        int shown = 0;

        for (String g : legacyGaps)
        {
            if (shown++ >= 15)
            {
                System.out.println("   ... and " + (legacyGaps.size() - 15) + " more");
                break;
            }

            System.out.println("   " + g);
        }

        System.out.println();

        assertTrue(derivedGaps.isEmpty(),
            "the derived graph would let these run at once over shared track: " + derivedGaps);
    }

    /**
     * Whether two derived edges occupy any tile in common.
     *
     * An overpass is the exception the whole design turns on: its two routes are at different heights, so
     * sharing that tile by different routes is not sharing track.
     */
    private boolean sharesATile(ReducedEdge a, ReducedEdge b)
    {
        for (GraphReducer.TileStep stepA : a.getPath())
        {
            for (GraphReducer.TileStep stepB : b.getPath())
            {
                if (!stepA.getTile().equals(stepB.getTile())) continue;

                org.traincontrol.base.LayoutDiagramComponent c = graph.getTiles().get(stepA.getTile());

                boolean overpass = c != null
                    && c.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.OVERPASS;

                if (overpass && !stepA.getRouteId().equals(stepB.getRouteId())) continue;

                return true;
            }
        }

        return false;
    }

    /**
     * The tiles a hand-built edge physically occupies, found by walking the derived graph between its two
     * sensors.
     *
     * The hand-built file has no notion of tiles, so this is the only way to give its edges an extent.
     * Where several routes join the same pair it takes the shortest, which is an approximation - a
     * hand-built edge meant to describe a longer way round would be judged on the wrong track.
     */
    /**
     * The tiles between two sensors, but only when a single derived edge joins them.
     *
     * @return the tiles, or null when several routes or several edges could be meant - in which case any
     *         answer would be a guess, and a guess here quietly accuses the layout of collision risks it
     *         does not have
     */
    private Set<TileKey> exactExtent(GraphReducer from, int startS88, int endS88)
    {
        Set<TileKey> tiles = null;

        for (ReducedEdge edge : from.getEdges())
        {
            ReducedPoint start = from.getPoints().get(edge.getStart());
            ReducedPoint end = from.getPoints().get(edge.getEnd());

            if (start == null || end == null) continue;
            if (start.getS88() != startS88 || end.getS88() != endS88) continue;

            // more than one derived edge joins these sensors, so which track is meant is not decidable
            if (tiles != null) return null;

            tiles = new LinkedHashSet<>();

            for (GraphReducer.TileStep step : edge.getPath())
            {
                tiles.add(step.getTile());
            }
        }

        return tiles == null || tiles.isEmpty() ? null : tiles;
    }

    private Set<TileKey> tilesAlong(GraphReducer from, int startS88, int endS88)
    {
        Map<Integer, ReducedEdge> arrivedBy = new LinkedHashMap<>();
        Map<Integer, Integer> cameFrom = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        LinkedList<Integer> queue = new LinkedList<>();

        queue.add(startS88);
        seen.add(startS88);

        while (!queue.isEmpty())
        {
            Integer current = queue.removeFirst();

            if (current == endS88) break;

            for (ReducedEdge edge : from.getEdges())
            {
                ReducedPoint start = from.getPoints().get(edge.getStart());
                ReducedPoint end = from.getPoints().get(edge.getEnd());

                if (start == null || end == null || start.getS88() != current) continue;

                if (seen.add(end.getS88()))
                {
                    arrivedBy.put(end.getS88(), edge);
                    cameFrom.put(end.getS88(), current);
                    queue.add(end.getS88());
                }
            }
        }

        Set<TileKey> tiles = new LinkedHashSet<>();

        Integer at = endS88;

        while (arrivedBy.containsKey(at))
        {
            for (GraphReducer.TileStep step : arrivedBy.get(at).getPath())
            {
                tiles.add(step.getTile());
            }

            at = cameFrom.get(at);

            if (at == null || at == startS88) break;
        }

        return tiles;
    }

    /**
     * Whether the hand-built configuration treats these two edges as mutually exclusive - either by
     * naming one in the other's lock list, or by their being the same edge.
     */
    private boolean legacyLocksTogether(JSONObject one, JSONObject two)
    {
        return legacyNames(one, two) || legacyNames(two, one);
    }

    /**
     * Whether two hand-built edges are kept apart by something other than a lock reference.
     *
     * A lock list is not the only thing that stops two trains meeting.  Sharing a point does it too, and
     * without anybody writing it down:
     *
     *   - two edges ending at the same point can never both run, because a point holds one train and a
     *     path to an occupied destination is refused;
     *   - two edges leaving the same point can never both run either, since only one train is standing
     *     there;
     *   - an edge and its own reverse are one piece of track rather than two claims on it.
     *
     * Only a pair that shares track WITHOUT sharing a point needs a lock reference, and that is the pair
     * worth reporting.
     */
    private boolean sharesAnEndpoint(JSONObject one, JSONObject two)
    {
        Set<String> ends = new LinkedHashSet<>();
        ends.add(one.getString("start"));
        ends.add(one.getString("end"));

        return ends.contains(two.getString("start")) || ends.contains(two.getString("end"));
    }

    private boolean legacyNames(JSONObject holder, JSONObject target)
    {
        if (holder.getString("start").equals(target.getString("start"))
            && holder.getString("end").equals(target.getString("end")))
        {
            return true;
        }

        if (!holder.has("lockedges")) return false;

        for (Object o : holder.getJSONArray("lockedges"))
        {
            JSONObject lock = (JSONObject) o;

            if (lock.getString("start").equals(target.getString("start"))
                && lock.getString("end").equals(target.getString("end")))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The generated file must be something parseAuto accepts - otherwise the compile step has produced
     * something only this test can read.
     */
    @Test
    public void testTheGeneratedConfigurationIsWellFormed()
    {
        String json = new AutonomyBuilder(reducer, null).build();

        JSONObject parsed = new JSONObject(json);

        assertTrue(parsed.has("points"));
        assertTrue(parsed.has("edges"));
        assertTrue(parsed.has("minDelay"));
        assertTrue(parsed.getJSONArray("points").length() > 0);

        // names are what the model keys on, so they must be unique
        Set<String> names = new HashSet<>();

        for (Object o : parsed.getJSONArray("points"))
        {
            String name = ((JSONObject) o).getString("name");
            assertTrue(names.add(name), "duplicate Point name in generated output: " + name);
        }

        // and every edge must reference Points that exist
        for (Object o : parsed.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;
            assertTrue(names.contains(edge.getString("start")), "edge from unknown Point");
            assertTrue(names.contains(edge.getString("end")), "edge to unknown Point");
        }
    }

    /**
     * Writes the derived graph out as an ordinary autonomy file, so it can be loaded and looked at.
     *
     * Nothing new is needed to do this - the builder already emits exactly the format a hand-written file
     * uses, because that is the whole point of the compile step.  What the export adds is graph
     * coordinates taken from the tiles, so the rendered graph is laid out like the track it came from and
     * can be checked against the diagram beside it.
     *
     * Two files, because they answer different questions:
     *   -derived        what the diagram gives with switches at their default, base to forks.  This is
     *                   what a user would get on day one, and it is deliberately sparse.
     *   -derived-open   the same with every branch opened, which is what the layout can express once the
     *                   trailing moves are enabled.  This is the one to compare against the hand-built
     *                   file.
     */
    @Test
    public void testTheDerivedGraphCanBeExportedForInspection() throws Exception
    {
        List<String> pageOrder = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (!excludedPages.contains(page.getName())) pageOrder.add(page.getName());
        }

        write("autonomy-derived.json",
            new AutonomyBuilder(reducer, null).withCoordinatesFromTiles(pageOrder).build());

        write("autonomy-derived-open.json",
            new AutonomyBuilder(reduceWithEveryBranchOpen(), null)
                .withCoordinatesFromTiles(pageOrder).build());
    }

    private void write(String name, String contents) throws Exception
    {
        File out = new File(name);

        Files.write(out.toPath(), contents.getBytes(StandardCharsets.UTF_8));

        System.out.println("wrote " + out.getAbsolutePath() + "  (" + contents.length() + " bytes)");
    }

    /**
     * Two builds of the same diagram must be identical, or the diff against the hand-built file is
     * comparing noise.
     */
    @Test
    public void testTheBuildIsDeterministic()
    {
        assertEquals(new AutonomyBuilder(reducer, null).build(),
                     new AutonomyBuilder(reducer, null).build());
    }

    /**
     * The wide-open graph as sensor-to-sensor adjacency.
     */
    private Map<Integer, Set<Integer>> wideOpenAdjacency()
    {
        GraphReducer wideOpen = reduceWithEveryBranchOpen();

        Map<Integer, Set<Integer>> adjacency = new LinkedHashMap<>();

        for (ReducedEdge edge : wideOpen.getEdges())
        {
            ReducedPoint start = wideOpen.getPoints().get(edge.getStart());
            ReducedPoint end = wideOpen.getPoints().get(edge.getEnd());

            if (start == null || end == null) continue;

            Set<Integer> next = adjacency.get(start.getS88());

            if (next == null)
            {
                next = new LinkedHashSet<>();
                adjacency.put(start.getS88(), next);
            }

            next.add(end.getS88());
        }

        return adjacency;
    }

    /**
     * The same diagram reduced again with every switch branch traversable both ways.
     */
    private GraphReducer reduceWithEveryBranchOpen()
    {
        TileGraph open = new TileGraph(pages, excludedPages);

        TileKey mainLink = linkAt(open, "1 - Main", 15, 5);
        TileKey bottomLink = linkAt(open, "2 - Bottom", 10, 9);

        if (mainLink != null && bottomLink != null) open.pairPortals(mainLink, bottomLink);

        for (TileKey tile : open.getTiles().keySet())
        {
            for (TileGraph.RouteId routeId : open.getRoutes(tile).keySet())
            {
                open.setDirection(tile, routeId, TileGraph.Direction.BOTH);
            }
        }

        GraphReducer wideOpen = new GraphReducer(open, stationsFromLegacy());
        wideOpen.reduce();

        return wideOpen;
    }

    /**
     * Reduces the same diagram again with every switch branch traversable both ways, and reports which
     * hand-built connections are still unreachable.
     *
     * This separates the two kinds of gap.  A connection that appears here is one the geometry cannot
     * express at all - a real defect in the port map, the walk, or the pairing.  A connection that is
     * unreachable under the default but reachable here is simply a trailing move nobody has opened yet,
     * which is authoring work rather than a fault.
     */
    private List<String> unreachableWithEveryBranchOpen()
    {
        GraphReducer wideOpen = reduceWithEveryBranchOpen();

        Map<Integer, Set<Integer>> adjacency = wideOpenAdjacency();

        Set<Integer> derivable = new LinkedHashSet<>();

        for (ReducedPoint p : wideOpen.getPoints().values())
        {
            derivable.add(p.getS88());
        }

        List<String> out = new ArrayList<>();

        for (Object o : legacy.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            Integer from = sensorOf(edge.getString("start"));
            Integer to = sensorOf(edge.getString("end"));

            if (from == null || to == null || from.equals(to)) continue;
            if (!derivable.contains(from) || !derivable.contains(to)) continue;

            if (!reachable(adjacency, from, to))
            {
                out.add(edge.getString("start") + " -> " + edge.getString("end"));
            }
        }

        System.out.println("(with every branch open: " + wideOpen.getPoints().size() + " points, "
            + wideOpen.getEdges().size() + " edges)");

        // For each remaining failure, say enough to find it on the diagram.  "Unreachable" on its own
        // cannot distinguish a sensor the walk never leaves from one that simply does not connect to
        // that particular destination, and those need different fixes.
        int explained = 0;

        for (String failure : out)
        {
            if (explained++ >= 8) break;

            String[] ends = failure.split(" -> ");

            System.out.println("\n   " + failure);
            describeSensor(wideOpen, "from", sensorOf(ends[0]));
            describeSensor(wideOpen, "to  ", sensorOf(ends[1]));
        }

        return out;
    }

    /**
     * Station designations lifted from the hand-built configuration, by sensor.
     *
     * Which Points are stations is authored data, not something geometry knows, so for this comparison
     * it comes from the file being compared against.  It matters because a train may only stop at a
     * station: a graph where nothing is a station is connected but useless.
     */
    private GraphReducer.Authored stationsFromLegacy()
    {
        final Set<Integer> stationSensors = new LinkedHashSet<>();

        for (Object o : legacy.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (point.optBoolean("station", false) && point.has("s88") && !point.isNull("s88"))
            {
                stationSensors.add(point.getInt("s88"));
            }
        }

        return new GraphReducer.Authored()
        {
            @Override
            public String getPointName(TileKey tile)
            {
                return null;
            }

            @Override
            public boolean isStation(TileKey tile)
            {
                org.traincontrol.base.LayoutDiagramComponent c = graph.getTiles().get(tile);

                return c != null && stationSensors.contains(c.getRawAddress());
            }

            @Override
            public int getTileLength(TileKey tile)
            {
                return 0;
            }
        };
    }

    /**
     * Can a train actually be sent from each hand-built station to the others?
     *
     * This is the question the layout exists to answer, and it is stricter than track connectivity:
     * a train may pass through a non-station but never stop at one, so a station that nothing can reach
     * or that can reach nothing is unusable however well connected its track is.
     */
    @Test
    public void testStationsCanStillReachOneAnother()
    {
        Map<Integer, Set<Integer>> adjacency = derivedAdjacency();

        Set<Integer> stations = new LinkedHashSet<>();

        for (ReducedPoint p : reducer.getPoints().values())
        {
            if (p.isStation()) stations.add(p.getS88());
        }

        List<String> stranded = new ArrayList<>();

        for (Integer station : stations)
        {
            int reachable = 0;

            for (Integer other : stations)
            {
                if (!other.equals(station) && reachable(adjacency, station, other)) reachable++;
            }

            if (reachable == 0) stranded.add(String.valueOf(station));
        }

        // The same count with every branch open.  Measured as authored it says only that trailing moves
        // are closed, which is the default doing its job rather than anything about the layout - so both
        // numbers are printed and the gap between them is the authoring still to do.
        Map<Integer, Set<Integer>> wideOpen = wideOpenAdjacency();

        List<String> strandedWideOpen = new ArrayList<>();

        for (Integer station : stations)
        {
            int reachable = 0;

            for (Integer other : stations)
            {
                if (!other.equals(station) && reachable(wideOpen, station, other)) reachable++;
            }

            if (reachable == 0) strandedWideOpen.add(String.valueOf(station));
        }

        System.out.println("\nstations derived: " + stations.size());
        System.out.println("   reaching no other station, as authored:      " + stranded.size()
            + "   (trailing moves closed by default)");
        System.out.println("   reaching no other station, all branches open: " + strandedWideOpen.size()
            + "   <- this is the one that matters");

        if (!strandedWideOpen.isEmpty()) System.out.println("   " + strandedWideOpen);

        System.out.println();

        assertFalse(stations.isEmpty(), "no stations were derived at all");
        assertTrue(strandedWideOpen.isEmpty(),
            "with every branch open these stations can still reach nowhere: " + strandedWideOpen);
    }

    /**
     * Where a sensor lives and what the derived graph joins it to.
     *
     * A sensor can carry more than one tile - the same s88 legitimately appears at a station and at its
     * approach guards - so this lists every tile bearing it, with the sensors each one reaches.
     */
    private void describeSensor(GraphReducer from, String label, Integer s88)
    {
        if (s88 == null)
        {
            System.out.println("      " + label + ": (no sensor)");
            return;
        }

        for (Map.Entry<TileKey, ReducedPoint> entry : from.getPoints().entrySet())
        {
            if (entry.getValue().getS88() != s88) continue;

            List<String> reaches = new ArrayList<>();

            for (ReducedEdge edge : from.getEdges())
            {
                if (!edge.getStart().equals(entry.getKey())) continue;

                ReducedPoint end = from.getPoints().get(edge.getEnd());
                reaches.add((end == null ? "?" : String.valueOf(end.getS88()))
                    + " (" + edge.getEnd() + ")");
            }

            System.out.println("      " + label + " s88 " + s88 + " at " + entry.getKey()
                + " -> " + (reaches.isEmpty() ? "NOTHING" : reaches.toString()));
        }
    }

    private TileKey linkAt(TileGraph g, String page, int x, int y)
    {
        TileKey key = new TileKey(page, x, y);

        org.traincontrol.base.LayoutDiagramComponent c = g.getTiles().get(key);

        return c != null && c.isLink() ? key : null;
    }

    /**
     * The derived graph as sensor-to-sensor adjacency, which is the level the hand-built file speaks at.
     */
    private Map<Integer, Set<Integer>> derivedAdjacency()
    {
        Map<Integer, Set<Integer>> out = new LinkedHashMap<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            ReducedPoint start = reducer.getPoints().get(edge.getStart());
            ReducedPoint end = reducer.getPoints().get(edge.getEnd());

            if (start == null || end == null) continue;

            Set<Integer> next = out.get(start.getS88());

            if (next == null)
            {
                next = new LinkedHashSet<>();
                out.put(start.getS88(), next);
            }

            next.add(end.getS88());
        }

        return out;
    }

    private Integer sensorOf(String legacyPointName)
    {
        for (Object o : legacy.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (legacyPointName.equals(point.getString("name")))
            {
                return point.has("s88") && !point.isNull("s88") ? point.getInt("s88") : null;
            }
        }

        return null;
    }

    private boolean reachable(Map<Integer, Set<Integer>> adjacency, int from, int to)
    {
        Set<Integer> seen = new HashSet<>();
        LinkedList<Integer> queue = new LinkedList<>();

        queue.add(from);
        seen.add(from);

        while (!queue.isEmpty())
        {
            Integer current = queue.removeFirst();

            if (current == to) return true;

            Set<Integer> next = adjacency.get(current);

            if (next == null) continue;

            for (Integer neighbour : next)
            {
                if (seen.add(neighbour)) queue.add(neighbour);
            }
        }

        return false;
    }

    // --- the report -------------------------------------------------------------------------------

    /**
     * Everything worth reading, printed once.  Categories follow the plan: what is only in the legacy
     * file is a defect, what is only generated is expected in bulk, and disagreements about accessory
     * commands on a matched edge point at the port map.
     */
    private String report(Set<String> excluded)
    {
        StringBuilder out = new StringBuilder();

        out.append("\n================ GROUND TRUTH COMPARISON ================\n");
        out.append("layout:   ").append(LAYOUT).append("\n");
        out.append("pages:    ").append(pages.size())
           .append(" (excluded: ").append(excluded).append(")\n");

        out.append("\n--- diagram problems ---\n");

        if (graph.getProblems().isEmpty())
        {
            out.append("none\n");
        }
        else
        {
            Map<String, Integer> counts = new LinkedHashMap<>();

            for (Problem p : graph.getProblems())
            {
                String label = (p.isBlocking() ? "ERROR " : "warn  ") + p.getMessageKey();
                Integer n = counts.get(label);
                counts.put(label, n == null ? 1 : n + 1);
            }

            for (Map.Entry<String, Integer> entry : counts.entrySet())
            {
                out.append(String.format("%-60s %d%n", entry.getKey(), entry.getValue()));
            }

            // name the first few, so a blocking problem can actually be found on the diagram
            int shown = 0;

            for (Problem p : graph.getProblems())
            {
                if (p.isBlocking() && shown++ < 10) out.append("   ").append(p).append("\n");
            }
        }

        out.append("\n--- counts ---\n");
        out.append(String.format("%-28s legacy %5d   derived %5d%n", "points",
            legacy.getJSONArray("points").length(), reducer.getPoints().size()));
        out.append(String.format("%-28s legacy %5d   derived %5d%n", "edges",
            legacy.getJSONArray("edges").length(), reducer.getEdges().size()));
        out.append(String.format("%-28s %5d%n", "isolated sensors skipped",
            reducer.getIsolatedFeedbackTiles()));

        // --- sensors ---
        Set<Integer> legacySensors = new LinkedHashSet<>();
        Map<Integer, String> legacyNames = new LinkedHashMap<>();

        for (Object o : legacy.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (point.has("s88") && !point.isNull("s88"))
            {
                legacySensors.add(point.getInt("s88"));
                legacyNames.put(point.getInt("s88"), point.getString("name"));
            }
        }

        Set<Integer> derivedSensors = new LinkedHashSet<>();

        for (ReducedPoint p : reducer.getPoints().values())
        {
            derivedSensors.add(p.getS88());
        }

        Set<Integer> onlyLegacy = new LinkedHashSet<>(legacySensors);
        onlyLegacy.removeAll(derivedSensors);

        Set<Integer> onlyDerived = new LinkedHashSet<>(derivedSensors);
        onlyDerived.removeAll(legacySensors);

        out.append("\n--- sensors ---\n");
        out.append("in both:            ").append(legacySensors.size() - onlyLegacy.size()).append("\n");
        out.append("ONLY IN LEGACY:     ").append(onlyLegacy.size())
           .append("   <- each one is a defect\n");

        for (Integer s88 : onlyLegacy)
        {
            out.append("   s88 ").append(s88).append(" \"").append(legacyNames.get(s88)).append("\"\n");
        }

        out.append("only derived:       ").append(onlyDerived.size())
           .append("   <- expected: every sensor becomes a Point\n");

        // --- edges, compared by the sensor pair they join ---
        Set<String> legacyPairs = new LinkedHashSet<>();
        Map<String, JSONObject> legacyByPair = new LinkedHashMap<>();
        Map<String, Integer> nameToS88 = new LinkedHashMap<>();

        for (Object o : legacy.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (point.has("s88") && !point.isNull("s88"))
            {
                nameToS88.put(point.getString("name"), point.getInt("s88"));
            }
        }

        for (Object o : legacy.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            Integer a = nameToS88.get(edge.getString("start"));
            Integer b = nameToS88.get(edge.getString("end"));

            if (a == null || b == null) continue;

            String pair = a + "->" + b;
            legacyPairs.add(pair);
            legacyByPair.put(pair, edge);
        }

        Set<String> derivedPairs = new LinkedHashSet<>();
        Map<String, ReducedEdge> derivedByPair = new LinkedHashMap<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            ReducedPoint start = reducer.getPoints().get(edge.getStart());
            ReducedPoint end = reducer.getPoints().get(edge.getEnd());

            if (start == null || end == null) continue;

            String pair = start.getS88() + "->" + end.getS88();
            derivedPairs.add(pair);

            if (!derivedByPair.containsKey(pair)) derivedByPair.put(pair, edge);
        }

        Set<String> edgesOnlyLegacy = new LinkedHashSet<>(legacyPairs);
        edgesOnlyLegacy.removeAll(derivedPairs);

        Set<String> edgesOnlyDerived = new LinkedHashSet<>(derivedPairs);
        edgesOnlyDerived.removeAll(legacyPairs);

        out.append("\n--- edges (matched by the sensor pair they join) ---\n");
        out.append("in both:            ").append(legacyPairs.size() - edgesOnlyLegacy.size()).append("\n");
        out.append("ONLY IN LEGACY:     ").append(edgesOnlyLegacy.size())
           .append("   <- a defect, OR a trailing move not yet enabled\n");

        int shown = 0;

        for (String pair : edgesOnlyLegacy)
        {
            if (shown++ >= 25)
            {
                out.append("   ... and ").append(edgesOnlyLegacy.size() - 25).append(" more\n");
                break;
            }

            JSONObject edge = legacyByPair.get(pair);
            out.append("   ").append(pair).append("  ")
               .append(edge.getString("start")).append(" -> ").append(edge.getString("end")).append("\n");
        }

        out.append("only derived:       ").append(edgesOnlyDerived.size())
           .append("   <- expected in bulk\n");

        // Trailing moves are closed by default, so a legacy edge whose reverse IS derived is almost
        // certainly that rather than a reduction defect.  Separating the two makes the remainder - the
        // edges with no derived counterpart in either direction - the list actually worth reading.
        int reverseExists = 0;
        List<String> neitherDirection = new ArrayList<>();

        for (String pair : edgesOnlyLegacy)
        {
            String[] ends = pair.split("->");
            String reversed = ends[1] + "->" + ends[0];

            if (derivedPairs.contains(reversed))
            {
                reverseExists++;
            }
            else
            {
                neitherDirection.add(pair + "  " + legacyByPair.get(pair).getString("start")
                    + " -> " + legacyByPair.get(pair).getString("end"));
            }
        }

        out.append("   of those, reverse IS derived: ").append(reverseExists)
           .append("   <- a trailing move, not a defect\n");
        out.append("   NEITHER direction derived:    ").append(neitherDirection.size())
           .append("   <- the real list to work through\n");

        int neither = 0;

        for (String line : neitherDirection)
        {
            if (neither++ >= 25)
            {
                out.append("      ... and ").append(neitherDirection.size() - 25).append(" more\n");
                break;
            }

            out.append("      ").append(line).append("\n");
        }

        // --- accessory commands on matched edges: the highest value signal ---
        out.append("\n--- config commands on matched edges ---\n");

        int agree = 0;
        List<String> disagree = new ArrayList<>();

        for (String pair : legacyPairs)
        {
            if (!derivedByPair.containsKey(pair)) continue;

            JSONObject legacyEdge = legacyByPair.get(pair);
            ReducedEdge derivedEdge = derivedByPair.get(pair);

            Map<String, String> legacyCommands = new LinkedHashMap<>();

            if (legacyEdge.has("commands"))
            {
                for (Object c : legacyEdge.getJSONArray("commands"))
                {
                    JSONObject command = (JSONObject) c;
                    legacyCommands.put(command.getString("acc"), command.getString("state"));
                }
            }

            Map<String, String> derivedCommands = new LinkedHashMap<>();

            for (Map.Entry<String, org.traincontrol.base.Accessory.accessorySetting> e
                : derivedEdge.getCommands().entrySet())
            {
                derivedCommands.put(e.getKey(), e.getValue().toString().toLowerCase());
            }

            if (legacyCommands.equals(derivedCommands))
            {
                agree++;
            }
            else if (disagree.size() < 15)
            {
                disagree.add(pair + "\n      legacy:  " + legacyCommands
                                  + "\n      derived: " + derivedCommands);
            }
        }

        out.append("agree:              ").append(agree).append("\n");
        out.append("DISAGREE:           ").append(disagree.size())
           .append("   <- port map defects show up here first\n");

        for (String d : disagree)
        {
            out.append("   ").append(d).append("\n");
        }

        // --- locks ---
        int derivedLockRefs = 0;

        for (Set<ReducedEdge> set : reducer.getLocks().values())
        {
            derivedLockRefs += set.size();
        }

        int legacyLockRefs = 0;

        for (Object o : legacy.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            if (edge.has("lockedges")) legacyLockRefs += edge.getJSONArray("lockedges").length();
        }

        // How much of each graph runs both ways.
        //
        // The two defaults pull in opposite directions: switches default to base-to-forks, which is
        // restrictive, but plain track defaults to both ways, which is permissive.  A hand-built file
        // contains only the directions somebody chose to create, while the diagram offers every direction
        // the track physically allows - so a derived graph is MORE permissive about direction of travel
        // even while being less permissive about switches.
        //
        // The gap is the one-way running still to be marked, and it is the safety-relevant half of the
        // authoring work: it is what stops autonomy sending a train the wrong way up a line the author
        // never meant to be bidirectional.
        int legacyBoth = 0;

        for (String pair : legacyPairs)
        {
            String[] ends = pair.split("->");

            if (legacyPairs.contains(ends[1] + "->" + ends[0])) legacyBoth++;
        }

        int derivedBoth = 0;

        for (String pair : derivedPairs)
        {
            String[] ends = pair.split("->");

            if (derivedPairs.contains(ends[1] + "->" + ends[0])) derivedBoth++;
        }

        out.append("\n--- how much runs both ways ---\n");
        out.append(String.format("%-36s %4d of %4d%n", "hand-built, reversible connections",
            legacyBoth, legacyPairs.size()));
        out.append(String.format("%-36s %4d of %4d%n", "derived, reversible connections",
            derivedBoth, derivedPairs.size()));
        out.append("(the difference is one-way running still to be marked, not a defect)\n");

        out.append("\n--- lock references ---\n");
        out.append("legacy:             ").append(legacyLockRefs).append("\n");
        out.append("derived:            ").append(derivedLockRefs).append("\n");
        out.append("(legacy refs with no derived counterpart are the evidence on whether\n");
        out.append(" conservative hand-written locks were load bearing)\n");

        out.append("=========================================================\n");

        return out.toString();
    }
    /**
     * A doorway switched off at one end is shut at both.
     *
     * TD-2, from the three-day history review. OB-041 made the WRITER mutual on 2026-08-23 - switching
     * a paired link off switches its partner off - but the readers went on asking about the near end
     * alone, and there is no migration. So every setup saved before that date holds one-ended disables,
     * and autonomy went on routing through a doorway the operator had excluded, in one direction.
     *
     * This is what those files look like: a pairing with only one end in the disabled set. The graph
     * has to treat it as shut, which repairs them without anybody running anything.
     */
    @Test
    public void testALinkSwitchedOffAtOneEndIsShutAtBoth()
    {
        TileKey here = findLink("1 - Main", 15, 5);
        TileKey there = findLink("2 - Bottom", 10, 9);

        assertNotNull(here, "no link tile on Main at 15,5");
        assertNotNull(there, "no link tile on Bottom at 10,9");

        graph.pairPortals(here, there);

        // Only ONE end, which is what a file written before 2026-08-23 contains
        graph.disablePortal(here);

        assertTrue(graph.isPortalDisabled(here), "the end that was switched off is not off");

        assertTrue(graph.isPortalDisabled(there),
            "the far end of a switched-off pairing is still open. A pair of links is one doorway with "
            + "an end in two places and autonomy walks through it both ways, so this is a route that "
            + "exists going one way and not the other - and it is the state every setup saved before "
            + "2026-08-23 is in (TD-2)");
    }


}
