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
 * The ground truth gate: reduce a real track diagram and compare what comes out against the autonomy configuration
 * somebody wrote for that same layout by hand.
 *
 * It is deliberately NOT a pass/fail equality check.  The two are not supposed to match exactly:
 *
 *   - the generated graph should be a SUPERSET of Points, because every s88 becomes one whereas hand-authoring includes
 *     only the sensors somebody bothered with;
 *   - trailing moves are absent by default, so hand-written edges that trail through a switch will be missing until
 *     those branches are opened;
 *   - lock references are derived from shared tiles, so hand-written conservative locks may have no derived
 * counterpart,
 *     which is fine, while a derived lock the author never wrote is worth reading.
 *
 * What matters is the report it prints.  Read that, not the assertion count: the assertions only catch the reduction
 * collapsing entirely.
 *
 * @author Adam
 */
public class testAutonomyDiagramSampleLayout
{
    private static final String LAYOUT = "test/test_layout";

    /**
     * The two ends of the one doorway between the pages autonomy is looking at.
     *
     * Found once in the setup and kept, rather than each test writing the coordinates down again: three did, all wrong,
     * and every test in this class was skipped from the @BeforeClass while the battery reported it green, because a
     * skip is not a failure.
     */
    private static TileKey mainLink;
    private static TileKey bottomLink;

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
        model = support.TestStation.simulated();

        File folder = findLayoutFolder();

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        // The same URL shape syncLayoutsFromConfiguredSource builds for a local layout
        String path = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        // This layout has no magnetartikel.cs2 - it ships only the diagram and its autonomy file - and
        // the accessory list is used solely to pick a decoder protocol, so an empty one is correct here.
        pages = support.LayoutSandbox.wired(model, parser);

        wireAccessories();

        // Three pages are outside autonomy, excluded the way a user would exclude them (only Main and Bottom are used
        // for autonomy; the rest are for convenience):
        //
        //   "4 - Combined" redraws tiles from the other pages.  Left in, it would mint a second Point for every sensor
        // it
        //   shows and a parallel set of edges.
        //
        //   "5 - Test" is a scratch page carrying a scissors tile, a drawing convention for a double slip across two
        // tiles
        //   that cannot be expressed per tile and so disqualifies any page it appears on: the disqualification working.
        //
        //   "3 - Top Parking" is a convenience view.  Every sensor the hand-built configuration uses is drawn on Main
        // or
        //   Bottom, so excluding it costs nothing and removes the one genuine pairing ambiguity in this layout.
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

        // FOUND rather than written down, because a written-down coordinate went stale (it said 15,5; the arrow sits at
        // 14,5), skipping every test in this class from its @BeforeClass while the battery called it green.
        //
        // A link tile records its destination PAGE and nothing more, which is why the pairing has to be authored at
        // all.  With Top Parking excluded there is exactly ONE arrow on Main pointing at Bottom and one on Bottom
        // pointing back, so the pair is forced and can be looked up; the two arrows on Main pointing at Top Parking are
        // left unpaired, which is what an unnamed link should do now that its destination is outside autonomy.
        mainLink = onlyLinkFrom("1 - Main", "2 - Bottom", pages);
        bottomLink = onlyLinkFrom("2 - Bottom", "1 - Main", pages);

        // Fail rather than carry on unpaired: tolerating a missing link tile let a wrong coordinate masquerade as a
        // diagram with no cross-page routes at all.
        assertNotNull(mainLink, "no link tile on Main pointing at Bottom - the fixture has changed "
            + "shape, and this class cannot pair the two pages without one");
        assertNotNull(bottomLink, "no link tile on Bottom pointing at Main - the fixture has changed "
            + "shape, and this class cannot pair the two pages without one");

        graph.pairPortals(mainLink, bottomLink);

        graph.validatePortals();

        File legacyFile = new File(findLayoutFolder(), "config/autorun/autonomy.json");

        assertTrue(legacyFile.isFile(), "hand-built autonomy config not found at "
            + legacyFile.getAbsolutePath());

        legacy = new JSONObject(new String(
            Files.readAllBytes(legacyFile.toPath()), StandardCharsets.UTF_8));

        // Nothing is authored on a freshly read diagram, which would leave every derived Point a non-station, and a
        // train can never stop at one.  The station designations come from the hand-built file, which this comparison
        // stands in for.
        reducer = new GraphReducer(graph, stationsFromLegacy());
        reducer.reduce();

        System.out.println(report(excluded));
    }

    /**
     * The one link tile on a page that points at another named page.
     *
     * A link records its destination page as a raw address counting from zero, so the page it means is
     * `pages.get(getRawAddress())`.  Returns null unless there is EXACTLY one: the file names a destination page and
     * never a destination tile, so two arrows to one page would be ambiguous, and silently taking the first would hide
     * that.
     *
     * @param from the page to search
     * @param to the page the arrow should point at
     * @param pages every page, in the order the index gives them
     * @return the square, or null when there is not exactly one
     */
    private static TileKey onlyLinkFrom(String from, String to, List<LayoutDiagram> pages)
    {
        TileKey found = null;

        for (LayoutDiagram page : pages)
        {
            if (!from.equals(page.getName())) continue;

            for (int x = 0; x < page.getSx(); x++)
            {
                for (int y = 0; y < page.getSy(); y++)
                {
                    org.traincontrol.base.LayoutDiagramComponent c = page.getComponent(x, y);

                    if (c == null || !c.isLink()) continue;

                    int at = c.getRawAddress();

                    if (at < 0 || at >= pages.size()) continue;

                    if (!to.equals(pages.get(at).getName())) continue;

                    // A second one means the pairing is ambiguous, which is a different fixture from
                    // the one this class is written for.
                    if (found != null) return null;

                    found = new TileKey(from, x, y);
                }
            }
        }

        return found;
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

        // Read straight off the tile graph (`graph.getFeedbackTiles()`, every sensor drawn on a page autonomy looks at)
        // and never from reducer.getPoints(): the same loop as `derived` in testEverySensorTheLegacyConfigUsesIsDerived
        // would make `missing` empty by construction, and a page that fails to produce Points would take its own
        // sensors out of scope along with it.
        for (TileKey tile : graph.getFeedbackTiles())
        {
            org.traincontrol.base.LayoutDiagramComponent c = graph.getTiles().get(tile);

            // Same convention GraphReducer.buildPoints() uses: feedback is keyed on the raw address.
            if (c != null) out.add(c.getRawAddress());
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
     * Prints the reduction, so a route somebody disputes can be checked against what was derived: which Points exist,
     * what joins them, and which squares each join runs over.  Not an assertion beyond "something was derived".
     * Ordered by name so two runs can be diffed and a sensor found by searching the output.
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
     * The one rule a Point-and-edge graph cannot state: the reduction is honest about the TRACK (joined sensors run
     * both ways), but leaving by the side you came in on means reversing, which a train does only where the railway has
     * somewhere for it.  The model has no idea which way a train is pointing, so the hand-built configuration encoded
     * direction as one-way edges and doubled Points (TunnelPre -> Tunnel but not back; the way out of that sensor is a
     * SECOND Point on the same s88, TunnelReverse).
     *
     * Checked on the BUILT graph, where the answer lives: the builder turns each square into one Point per arrival
     * side, so leaving by the side you came in at is not a move any single Point has.  Asserting on the reduction would
     * assert that the track is one-way, which it is not.  A Point emitted as a terminus or reversing point is exempt:
     * that IS the copy that turns trains round.
     *
     * Both reductions are built: as authored most branches are closed and a reversal has no edge to be made of, which
     * understates the problem badly; with every branch open is the shape a finished setup has, and where the real count
     * lives.
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

        // Without this, AutonomyBuilder.edgesByName() returning an empty map would leave
        // `turns` empty by construction - zero Points examined, not zero offenders - and the caller's
        // assertTrue(turns.isEmpty()) would pass having checked nothing.  testTheDiagramReducesToSomething
        // pins the raw reducer's own points/edges, but not what this builder derives from them.
        assertFalse(byName.isEmpty(),
            "precondition [" + label + "]: edgesByName() built no edges, so nothing was checked");

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
     * The case the author raised: a train that reached BottomMainA from the Tunnel end must not be able to go straight
     * back to the Tunnel, since it cannot turn round anywhere between the two (the reversing point is further up the
     * line).
     *
     * Named rather than left to the sweep above because a person can check it by eye against the diagram, and a sweep
     * that is failing everywhere proves nothing about one square.  Checked against the wide-open reduction as well as
     * the authored one: as authored the branch back out of BottomMainA is closed by default, so there is no edge for
     * the reversal to be made of and this would pass without establishing anything.
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

        // Same floor as turnsOffered - an empty edgesByName() would leave `offending` empty
        // because nothing was walked, not because nothing offends.
        assertFalse(edgeNames.isEmpty(),
            "precondition [" + label + "]: edgesByName() built no edges, so nothing was checked");

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
     * A real diagram should not be refused.  A blocking problem here is a genuine finding: an unmapped switch, or the
     * port map misreading something.  The arrows to the excluded page (the two on Main pointing at "3 - Top Parking")
     * cannot be paired, and OB-150's unpaired-link error must not fire for them; the assertion stays that NOTHING
     * blocks rather than excusing those two, because a test that lists what it tolerates stops being able to tell.
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

        // Sensors that are DRAWN on a page autonomy is looking at.  A legacy point whose sensor is
        // not among them is out of scope by choice rather than missing by defect - which is the rule
        // this class already states on derivableSensors, applied here rather than restated.
        Set<Integer> inScope = derivableSensors();

        List<String> missing = new ArrayList<>();
        List<String> outOfScope = new ArrayList<>();

        for (Object o : legacy.getJSONArray("points"))
        {
            JSONObject point = (JSONObject) o;

            if (!point.has("s88") || point.isNull("s88")) continue;

            int s88 = point.getInt("s88");

            if (derived.contains(s88)) continue;

            if (inScope.contains(s88)) missing.add(point.getString("name") + " (s88 " + s88 + ")");
            else outOfScope.add(point.getString("name") + " (s88 " + s88 + ")");
        }

        // COMPUTED, not listed: on the frozen fixture s88 1017 and 1024 are drawn only on 3 - Top Parking and 4 -
        // Combined, both excluded here (the two squares on Main that look like them carry 1016 and 1023, different
        // sensors).  Asking whether the sensor is drawn on a participating page cannot go stale, keeps the assertion's
        // teeth for a sensor that IS in scope and was not derived, and reports the rest rather than hiding them.
        assertTrue(missing.isEmpty(),
            "sensors the hand-built graph uses, drawn on a page autonomy is looking at, that the "
            + "diagram did not derive: " + missing
            + " (out of scope, drawn only on excluded pages: " + outOfScope + ")");
    }

    /**
     * The completeness question, asked properly.
     *
     * Comparing edges one for one understates the match, because the two graphs cut the railway at different places
     * (every s88 is a Point here; the hand-built graph skips the sensors nobody bothered with), so one legacy edge
     * routinely spans several derived ones.  What matters is whether a train could still get from one end to the other:
     * for every legacy edge, is its destination reachable from its origin in the derived graph?  Anything unreachable
     * is track the diagram failed to derive or, until links are paired, a route that crosses pages.
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
     * Comparing lock counts tells us nothing: the graphs cut the railway at different places, so 118 hand-written
     * references and 96 derived ones are not comparable.  What IS comparable is the track: two routes can collide if
     * and only if they occupy the same tile, and that truth belongs to neither graph, so it can referee both.
     *
     * Each hand-built edge is given a physical extent by walking the derived tile graph between its two sensors.  For
     * every pair of hand-built edges that share tiles, the hand-built configuration is asked whether it knew (does it
     * lock them against each other, or are they the same edge?); anything it missed is a pair of routes it would run at
     * once over shared track.  The derived side is checked the other way round: by construction it locks every shared
     * tile, and a lock derivation that quietly dropped pairs would be the most dangerous defect in the project.
     */
    @Test
    public void testNoTwoRoutesCanOccupyTheSameTrackUnlocked()
    {
        GraphReducer wideOpen = reduceWithEveryBranchOpen();

        // the derived graph: every pair of edges sharing a tile must be locked
        List<String> derivedGaps = new ArrayList<>();

        List<ReducedEdge> edges = new ArrayList<>(wideOpen.getEdges());

        assertFalse(edges.isEmpty(), "precondition: the wide-open reduction produced no edges");

        // The risk ("the most dangerous defect in the project"): if ReducedEdge.getPath() ever returns empty,
        // sharesATile is false for every pair and derivedGaps stays empty because nothing was compared.  This counts
        // every pair actually found sharing a tile, so the population is checked, not just the gap count.
        int sharedTilePairs = 0;

        for (int i = 0; i < edges.size(); i++)
        {
            for (int j = i + 1; j < edges.size(); j++)
            {
                ReducedEdge a = edges.get(i);
                ReducedEdge b = edges.get(j);

                // an edge and its reverse are one piece of track, not two claims on it
                if (a.getStart().equals(b.getEnd()) && a.getEnd().equals(b.getStart())) continue;

                if (!sharesATile(a, b)) continue;

                sharedTilePairs++;

                Set<ReducedEdge> locked = wideOpen.getLocks().get(a);

                if (locked == null || !locked.contains(b))
                {
                    if (derivedGaps.size() < 10) derivedGaps.add(a + "  ||  " + b);
                }
            }
        }

        assertTrue(sharedTilePairs > 0,
            "precondition: no two derived edges were found sharing a tile at all, on a real layout that "
            + "certainly has junctions - sharesATile could not have caught an overlap even if one "
            + "existed, which is exactly what an emptied ReducedEdge.getPath() looks like");

        // the hand-built graph, judged by the same tiles
        Map<String, Set<TileKey>> legacyExtent = new LinkedHashMap<>();
        Map<String, JSONObject> legacyEdges = new LinkedHashMap<>();
        int ambiguous = 0;

        for (Object o : legacy.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;

            Integer from = sensorOf(edge.getString("start"));
            Integer to = sensorOf(edge.getString("end"));

            if (from == null || to == null) continue;

            // Only judge edges whose physical extent is EXACT.  A hand-built edge has no tiles of its own, so where it
            // spans several derived edges the inference is a guess (with every branch open the shortest way between two
            // sensors is not necessarily the one the author drew): it once put five edges on the same seventeen tiles.
            // Where a single derived edge joins the two sensors that IS the track between them; judging only those
            // covers less but says something true.
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

        // Same defect, the other side: if exactExtent silently returned null for every legacy edge (the same
        // emptied-getPath() mutation would do it), legacyExtent would be empty and the hand-built half would be skipped
        // without a pair compared.
        assertTrue(legacyExtent.size() > 0,
            "precondition: every hand-built edge came back ambiguous - the hand-built half checked "
            + "nothing, which is silent, not clean");

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

                // TWO ROADS THAT NEVER MEET ARE NOT SHARED TRACK, whichever square carries them: the overpass, and a
                // double curve, which `TilePorts` gives `route(N, W)` and `route(E, S)`, two curves in opposite corners
                // with no rail between them.  The reduction keys both per route, so a pair crossing one on different
                // roads is not locked.
                boolean twoRoads = c != null
                    && (c.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.OVERPASS
                        || c.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.DOUBLE_CURVE
                        || c.getType()
                            == org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK_DOUBLE_CURVE);

                if (twoRoads && !stepA.getRouteId().equals(stepB.getRouteId())) continue;

                return true;
            }
        }

        return false;
    }

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
     * Sharing a point does it without anybody writing it down: two edges ending at the same point can never both run (a
     * point holds one train and a path to an occupied destination is refused), nor two leaving the same point, and an
     * edge and its own reverse are one piece of track.  Only a pair that shares track WITHOUT sharing a point needs a
     * lock reference, and that is the pair worth reporting.
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

        // Without this, a build that emitted points but no edges at all (a defect in AutonomyBuilder itself, which
        // testTheDiagramReducesToSomething would not catch) would run the loop below zero times and pass having checked
        // nothing.
        assertTrue(parsed.getJSONArray("edges").length() > 0, "no edges in generated output to check");

        // and every edge must reference Points that exist
        for (Object o : parsed.getJSONArray("edges"))
        {
            JSONObject edge = (JSONObject) o;
            assertTrue(names.contains(edge.getString("start")), "edge from unknown Point");
            assertTrue(names.contains(edge.getString("end")), "edge to unknown Point");
        }
    }

    /**
     * Writes the derived graph out as an ordinary autonomy file, so it can be loaded and looked at: the builder already
     * emits the format a hand-written file uses; the export adds graph coordinates taken from the tiles, so the
     * rendered graph is laid out like the track it came from.
     *
     * Two files, because they answer different questions:
     *   -derived        what the diagram gives with switches at their default, base to forks: what a user gets on day
     *                   one, deliberately sparse.
     *   -derived-open   the same with every branch opened, which is what the layout can express once the trailing moves
     *                   are enabled: the one to compare against the hand-built file.
     */
    @Test
    public void testTheDerivedGraphCanBeExportedForInspection() throws Exception
    {
        List<String> pageOrder = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (!excludedPages.contains(page.getName())) pageOrder.add(page.getName());
        }

        String closed = new AutonomyBuilder(reducer, null).withCoordinatesFromTiles(pageOrder).build();
        String open = new AutonomyBuilder(reduceWithEveryBranchOpen(), null)
            .withCoordinatesFromTiles(pageOrder).build();

        write("autonomy-derived.json", closed);
        write("autonomy-derived-open.json", open);

        // The writes above are a dump for a person to read, not a check, so assert the one property the export exists
        // to have: a mutation making withCoordinatesFromTiles(pageOrder) return (0,0) for every tile would still
        // produce two well-formed, non-empty files and stay green while the rendered graph collapsed onto one point.
        assertPlacedByTile(closed);
        assertPlacedByTile(open);
    }

    /**
     * Asserts that withCoordinatesFromTiles actually spread the exported Points across distinct,
     * non-degenerate coordinates rather than collapsing them all onto the origin.
     */
    private void assertPlacedByTile(String json)
    {
        JSONArray points = new JSONObject(json).getJSONArray("points");

        assertTrue(points.length() > 1,
            "too few exported points to prove coordinates vary: " + points.length());

        Set<String> distinctCoordinates = new LinkedHashSet<>();
        boolean sawNonZero = false;

        for (Object o : points)
        {
            JSONObject point = (JSONObject) o;
            int x = point.getInt("x");
            int y = point.getInt("y");

            distinctCoordinates.add(x + "," + y);

            if (x != 0 || y != 0) sawNonZero = true;
        }

        assertTrue(distinctCoordinates.size() > 1,
            "every exported Point landed on the same coordinate - withCoordinatesFromTiles is not "
            + "placing Points by tile");
        assertTrue(sawNonZero, "every exported Point sits at (0,0)");
    }

    /**
     * Writes a diagnostic dump to a temp directory, never the working directory - this is a file for a
     * person to open and read, not part of the build.
     */
    private void write(String name, String contents) throws Exception
    {
        File dir = Files.createTempDirectory("autonomy-derived").toFile();
        File out = new File(dir, name);

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
        String a = new AutonomyBuilder(reducer, null).build();
        String b = new AutonomyBuilder(reducer, null).build();

        // Without this, two builds that both degenerated to "" would satisfy assertEquals below.
        // testTheGeneratedConfigurationIsWellFormed pins that A build has points; this pins that the two builds
        // compared here are non-trivial.
        assertTrue(new JSONObject(a).getJSONArray("points").length() > 0,
            "precondition: the build being checked for determinism has no points to be deterministic about");

        assertEquals(a, b);
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

        // The same two ends the setup found, checked against THIS graph rather than assumed onto it.
        TileKey here = linkAt(open, mainLink);
        TileKey there = linkAt(open, bottomLink);

        if (here != null && there != null) open.pairPortals(here, there);

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
     * Reduces the same diagram again with every switch branch traversable both ways, and reports which hand-built
     * connections are still unreachable.
     *
     * This separates the two kinds of gap: a connection that appears here is one the geometry cannot express at all (a
     * real defect in the port map, the walk, or the pairing); one unreachable only under the default is a trailing move
     * nobody has opened yet, which is authoring work rather than a fault.
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
     * Station designations lifted from the hand-built configuration, by sensor: which Points are stations is authored
     * data, not geometry, and a train may only stop at a station, so a graph where nothing is a station is connected
     * but useless.
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
     * Can a train actually be sent from each hand-built station to the others?  Stricter than track connectivity: a
     * station that nothing can reach, or that can reach nothing, is unusable however well connected its track is.
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

        // The same count with every branch open: as authored it says only that trailing moves are closed (the default
        // doing its job), so both numbers are printed and the gap between them is the authoring still to do.
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

    /**
     * The same square in another graph, if that graph has a link there too.
     *
     * @param g the graph to look in
     * @param key the square, from the paired-up graph the setup built
     * @return the key, or null when this graph has no link there
     */
    private TileKey linkAt(TileGraph g, TileKey key)
    {
        if (key == null) return null;

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

    // the report

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

        // How much of each graph runs both ways.  Switches default to base-to-forks (restrictive) but plain track
        // defaults to both ways (permissive); a hand-built file holds only the directions somebody chose, so a derived
        // graph is MORE permissive about direction even while less permissive about switches.  The gap is the one-way
        // running still to be marked, the safety-relevant half of the authoring work: it stops autonomy sending a train
        // the wrong way up a line never meant to be bidirectional.
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
     * OB-041 made the WRITER mutual (switching a paired link off switches its partner off) but the readers went on
     * asking about the near end alone, and there is no migration: every setup saved before holds one-ended disables,
     * and autonomy went on routing through a doorway the operator had excluded, in one direction.  This is what those
     * files look like (a pairing with only one end in the disabled set); the graph has to treat it as shut, which
     * repairs them without anybody running anything.
     */
    @Test
    public void testALinkSwitchedOffAtOneEndIsShutAtBoth() throws Exception
    {
        TileKey here = mainLink;
        TileKey there = bottomLink;

        assertNotNull(here, "the setup found no link on Main pointing at Bottom");
        assertNotNull(there, "the setup found no link on Bottom pointing at Main");

        // pairPortals/disablePortal mutate the @BeforeClass-built `graph`, shared by every test in this class, and
        // TileGraph has no public way to undo either; TestNG's arbitrary method order could otherwise run another test
        // against a graph this one has re-paired.  Both fields are private and final, so they are snapshotted and
        // restored by reflection.
        java.lang.reflect.Field portalsField = TileGraph.class.getDeclaredField("portals");
        portalsField.setAccessible(true);

        java.lang.reflect.Field disabledField = TileGraph.class.getDeclaredField("disabledPortals");
        disabledField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<TileKey, TileKey> portals = (Map<TileKey, TileKey>) portalsField.get(graph);
        @SuppressWarnings("unchecked")
        Set<TileKey> disabled = (Set<TileKey>) disabledField.get(graph);

        Map<TileKey, TileKey> hadPortals = new LinkedHashMap<>(portals);
        Set<TileKey> hadDisabled = new LinkedHashSet<>(disabled);

        try
        {
            graph.pairPortals(here, there);

            // Only ONE end, which is what an older file contains
            graph.disablePortal(here);

            assertTrue(graph.isPortalDisabled(here), "the end that was switched off is not off");

            assertTrue(graph.isPortalDisabled(there),
                "the far end of a switched-off pairing is still open. A pair of links is one doorway with "
                + "an end in two places and autonomy walks through it both ways, so this is a route that "
                + "exists going one way and not the other - and it is the state every setup saved before "
                + "2026-08-23 is in (TD-2)");
        }
        finally
        {
            portals.clear();
            portals.putAll(hadPortals);

            disabled.clear();
            disabled.addAll(hadDisabled);
        }
    }


}
