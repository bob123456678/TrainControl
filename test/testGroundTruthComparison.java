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
    private static final String LEGACY = "cs2_sample_layout/config/autorun/autonomy.json";

    private MarklinControlStation model;
    private List<LayoutDiagram> pages;
    private TileGraph graph;
    private GraphReducer reducer;
    private JSONObject legacy;

    @BeforeClass
    public void setUp() throws Exception
    {
        model = init(null, true, false, false, false);

        String path = new File(LAYOUT).toURI().toString();

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<MarklinAccessory> accessories = parser.getMagList(true);
        pages = parser.parseLayout(accessories);

        // "4 - Combined" redraws tiles from the other pages.  Left in, it would mint a second Point for
        // every sensor it shows and a parallel set of edges - which is exactly what the exclusion flag
        // exists for, and this layout is the reason the plan predicted it.
        Set<String> excluded = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            if (page.getName().toLowerCase().contains("combined")) excluded.add(page.getName());
        }

        graph = new TileGraph(pages, excluded);
        graph.validatePortals();

        reducer = new GraphReducer(graph, null);
        reducer.reduce();

        legacy = new JSONObject(new String(
            Files.readAllBytes(Paths.get(LEGACY)), StandardCharsets.UTF_8));

        System.out.println(report(excluded));
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

        assertTrue(missing.isEmpty(),
            "sensors the hand-built graph uses but the diagram did not derive: " + missing);
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
