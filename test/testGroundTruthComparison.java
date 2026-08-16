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
import org.traincontrol.base.AutonomyBuilder;
import org.traincontrol.base.GraphReducer;
import org.traincontrol.base.GraphReducer.ReducedEdge;
import org.traincontrol.base.GraphReducer.ReducedPoint;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.TileGraph;
import org.traincontrol.base.TileGraph.Problem;
import org.traincontrol.base.TileGraph.TileKey;
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
public class testGroundTruthComparison
{
    private static final String LAYOUT = "cs2_sample_layout";

    private MarklinControlStation model;
    private List<LayoutDiagram> pages;
    private TileGraph graph;
    private GraphReducer reducer;
    private JSONObject legacy;

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
        //   "3 - Top Parking" is a convenience view.  Only two of the hand-built configuration's 56
        //   sensors live there and nowhere else (TopR1ParkLong, TopR1ParkShort), so excluding it costs
        //   almost nothing - and it removes the one genuine pairing ambiguity in this layout.
        Set<String> excluded = new LinkedHashSet<>();

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
        TileKey mainLink = findLink("1 - Main", 14, 5);
        TileKey bottomLink = findLink("2 - Bottom", 10, 9);

        if (mainLink != null && bottomLink != null) graph.pairPortals(mainLink, bottomLink);

        graph.validatePortals();

        reducer = new GraphReducer(graph, null);
        reducer.reduce();

        File legacyFile = new File(findLayoutFolder(), "config/autorun/autonomy.json");

        assertTrue(legacyFile.isFile(), "hand-built autonomy config not found at "
            + legacyFile.getAbsolutePath());

        legacy = new JSONObject(new String(
            Files.readAllBytes(legacyFile.toPath()), StandardCharsets.UTF_8));

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

        // Two of this layout's sensors live only on Top Parking, which is excluded from autonomy by
        // choice.  Being out of scope is not the same as being underived, so they are named rather than
        // failed on - but anything BEYOND them is a real gap.
        System.out.println("\nlegacy sensors not derived (expected: only those on excluded pages): "
            + missing + "\n");

        assertTrue(missing.size() <= 2,
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

        System.out.println("\nlegacy connections unreachable in the derived graph: "
            + unreachable.size() + "\n" + unreachable + "\n");

        assertTrue(unreachable.isEmpty(),
            "the derived graph cannot get from one end to the other of " + unreachable.size()
            + " hand-built connections: " + unreachable);
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

        out.append("\n--- lock references ---\n");
        out.append("legacy:             ").append(legacyLockRefs).append("\n");
        out.append("derived:            ").append(derivedLockRefs).append("\n");
        out.append("(legacy refs with no derived counterpart are the evidence on whether\n");
        out.append(" conservative hand-written locks were load bearing)\n");

        out.append("=========================================================\n");

        return out.toString();
    }
}
