package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * What routes does a configuration actually OFFER?
 *
 * Adam's framing: what matters is the routing, not the graph.  Reachability says two stations are
 * connected; it cannot say whether the journey between them is one a train could make - and on this
 * layout every station pair is reachable, so it distinguishes nothing at all.
 *
 * So this asks the running model the question a train asks - getPossiblePaths - and writes down every
 * answer with its intermediate points, for a person to check.  Mostly a REPORT, not an assertion: it
 * fails if a configuration will not build, and each method also asserts the one floor it can state
 * without pre-judging Adam's layout - that the configuration it just built is valid, and (for the live
 * derived configuration specifically, where zero routes would mean the router or the config broke
 * rather than that the railway genuinely has none) that it offers at least one route.  What the routes
 * actually ARE beyond that floor is still Adam's to read out of the written report.
 *
 * Uses the real control station in simulation, so the accessories are the layout's own and the router
 * is the one that actually runs.  An earlier attempt built the graph by hand and produced three edges,
 * because a switch with no accessory is one autonomy refuses to route over - the harness, not the
 * railway.  Nothing here is worth reading unless the edge count is plausible.
 *
 * @author Adam
 */
public class testRouteInventory
{
    private static MarklinControlStation model;

    private static final File OUT = new File(System.getProperty("java.io.tmpdir"), "route-inventory");

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        try
        {
            model = init(null, true, false, false, true);
        }
        catch (Throwable t)
        {
            System.out.println("SETUP FAILED: " + t);
            t.printStackTrace(System.out);
            throw t;
        }

        OUT.mkdirs();
    }

    @Test
    public void testDerivedRoutes() throws Exception
    {
        RouteInventoryResult result = report("1-derived-active", build(null));

        assertTrue(result.valid,
            "the live, currently-active derived configuration must build into a valid layout");

        // The one floor this report-generator can assert without pre-judging what Adam's routing
        // ought to be: the live setup is not a degenerate one, so SOME route must be offered.  A
        // getPossiblePaths (or config-building) regression that returns nothing for every station
        // would otherwise leave all eight methods in this class green while offering nothing at all.
        assertTrue(result.hasLocomotive,
            "precondition: no locomotive found in the database to probe with");
        assertTrue(result.offered > 0,
            "the live active configuration offered zero routes from any of its "
                + result.destinations + " destinations - either the router or the derived "
                + "configuration is broken, since this layout has always offered routes");
    }

    @Test
    public void testStuckBundleRoutes() throws Exception
    {
        File bundle = new File("tc_backup/Autonomy 1b.json");

        if (!bundle.isFile()) return;

        RouteInventoryResult result = report("2-stuck-1b", build(bundle));

        assertTrue(result.valid, "tc_backup/Autonomy 1b.json must build into a valid layout");
    }

    @Test
    public void testLaterBundleRoutes() throws Exception
    {
        File bundle = new File("tc_backup/Autonomy 1d.json");

        if (!bundle.isFile()) return;

        RouteInventoryResult result = report("4-bundle-1d", build(bundle));

        assertTrue(result.valid, "tc_backup/Autonomy 1d.json must build into a valid layout");
    }

    @Test
    public void testLatestBundleRoutes() throws Exception
    {
        File bundle = new File("tc_backup/Autonomy 1e.json");

        if (!bundle.isFile()) return;

        RouteInventoryResult result = report("5-bundle-1e", build(bundle));

        assertTrue(result.valid, "tc_backup/Autonomy 1e.json must build into a valid layout");
    }

    @Test
    public void testHandAuthoredRoutes() throws Exception
    {
        File hand = new File("test/test_layout/config/autorun/autonomy.json");

        if (!hand.isFile()) return;

        RouteInventoryResult result = report("3-hand-authored-2.8.1",
            new String(Files.readAllBytes(hand.toPath()), StandardCharsets.UTF_8));

        assertTrue(result.valid,
            "test/test_layout/config/autorun/autonomy.json must build into a valid layout");
    }

    /**
     * The configuration the diagram derives, optionally with a bundle imported over the setup first.
     */
    private String build(File bundle) throws Exception
    {
        File folder = new File("test/test_layout");

        // A bundle is imported into an EMPTY store, in a scratch copy of the layout folder.
        //
        // importBundle merges rather than adopts - it fills in what the local setup does not already
        // have, and keeps whatever it does.  Imported over the live setup, a bundle therefore changes
        // nothing at all, and three different bundles produced three identical reports before I noticed.
        // With nothing local to keep, the bundle is the setup.
        if (bundle != null)
        {
            folder = scratchLayout();
        }

        AutonomySession session = new AutonomySession(folder);

        List<org.traincontrol.base.LayoutDiagram> pages = new ArrayList<>();

        for (String name : model.getLayoutList())
        {
            pages.add(model.getLayout(name));
        }

        session.open(pages);

        if (bundle != null)
        {
            int filled = session.importBundle("probe", new JSONObject(
                new String(Files.readAllBytes(bundle.toPath()), StandardCharsets.UTF_8)));

            System.out.println("  imported " + filled + " values from " + bundle.getName());
        }

        return session.buildConfiguration();
    }

    /**
     * A copy of the layout folder with no autonomy setup in it, so an imported bundle has nothing to
     * merge against.  The diagram pages themselves come from the model and are not copied.
     */
    private File scratchLayout() throws Exception
    {
        File scratch = new File(System.getProperty("java.io.tmpdir"),
            "tc-scratch-" + System.nanoTime());

        assertTrue(scratch.mkdirs(), "could not make " + scratch);

        scratch.deleteOnExit();

        return scratch;
    }

    /**
     * What one call to {@link #report} actually found, so a caller can assert on it instead of only
     * writing it to a file for a person to read later.
     *
     * TST-B5: this class used to compute all of this and throw every bit of it away except the file on
     * disk, which is how seven of its eight {@code @Test} methods could pass without checking anything.
     */
    private static final class RouteInventoryResult
    {
        final boolean valid;
        final boolean hasLocomotive;
        final int destinations;
        final int offered;
        final int barren;

        RouteInventoryResult(boolean valid, boolean hasLocomotive, int destinations, int offered,
            int barren)
        {
            this.valid = valid;
            this.hasLocomotive = hasLocomotive;
            this.destinations = destinations;
            this.offered = offered;
            this.barren = barren;
        }
    }

    /**
     * Places one locomotive at each destination in turn and writes down every route offered from there.
     *
     * One at a time on purpose: with several placed, every answer also depends on where the others are
     * standing, and the question here is what the TRACK allows.  Occupancy is a different test.
     */
    private RouteInventoryResult report(String label, String configuration) throws Exception
    {
        StringBuilder out = new StringBuilder("# ").append(label).append("\n\n");

        // the configuration itself, so it can be read alongside the routes.  Written from the
        // real-model path deliberately: a config dumped from a hand-built graph is not the one that
        // runs, and reading one while believing it is the other has wasted a whole afternoon.
        Files.write(new File(OUT, label + ".json").toPath(),
            configuration.getBytes(StandardCharsets.UTF_8));

        model.parseAuto(configuration);

        Layout layout = model.getAutoLayout();

        if (layout == null || !layout.isValid())
        {
            out.append("CONFIGURATION IS INVALID - nothing can run.\n");

            if (layout != null) out.append("reason: ").append(layout.getInvalidReason()).append("\n");

            write(label, out);

            return new RouteInventoryResult(false, false, 0, 0, 0);
        }

        List<Point> destinations = new ArrayList<>();

        for (Point p : layout.getPoints())
        {
            if (p.isDestination() && p.isAutoDestination() && !p.isReversing()) destinations.add(p);
        }

        out.append("points ").append(layout.getPoints().size())
           .append("   edges ").append(layout.getEdges().size())
           .append("   destinations ").append(destinations.size()).append("\n\n");

        Locomotive loc = null;

        for (String name : model.getLocList())
        {
            if (model.getLocByName(name) != null)
            {
                loc = model.getLocByName(name);
                break;
            }
        }

        if (loc == null)
        {
            write(label, out.append("no locomotive in the database\n"));
            return new RouteInventoryResult(true, false, destinations.size(), 0, 0);
        }

        out.append("probe locomotive: ").append(loc.getName()).append("\n\n");

        int offered = 0;
        int barren = 0;

        for (Point from : destinations)
        {
            layout.moveLocomotive(loc.getName(), from.getName(), false);

            List<List<Edge>> paths = layout.getPossiblePaths(loc, true);

            if (paths == null || paths.isEmpty())
            {
                barren++;
                out.append("FROM ").append(from.getName()).append("   -> nothing offered\n");
                continue;
            }

            out.append("FROM ").append(from.getName()).append("\n");

            for (List<Edge> path : paths)
            {
                offered++;
                out.append("    -> ").append(path.get(path.size() - 1).getEnd().getName())
                   .append("   via ").append(via(path)).append("\n");
            }
        }

        out.append("\ntotals: ").append(offered).append(" routes offered from ")
           .append(destinations.size() - barren).append(" stations; ")
           .append(barren).append(" stations offer nothing\n");

        write(label, out);

        return new RouteInventoryResult(true, true, destinations.size(), offered, barren);
    }

    /**
     * Why does one station offer nothing when the station beside it, with the same successor, offers
     * three?  Asks the model step by step instead of guessing.
     */
    @Test
    public void testWhyBottomMainAOffersNothing() throws Exception
    {
        model.parseAuto(build(null));

        Layout layout = model.getAutoLayout();

        assertTrue(layout.isValid(), "the live setup must load for this probe to mean anything");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // Nothing else standing anywhere.  The question is what the TRACK allows, and with the setup's
        // own three locomotives still placed every answer is also a statement about where they are.
        for (Locomotive other : layout.getLocomotivesToRun())
        {
            Point where = layout.getLocomotiveLocation(other);

            if (where != null && !other.equals(loc)) layout.moveLocomotive(null, where.getName(), false);
        }

        StringBuilder out = new StringBuilder("# why BottomSecondary offers nothing\n\n");

        for (String start : new String[]{"BottomSecondary", "LowerBack", "LowerFront", "ParkingTrack6", "BottomMainC (eastbound)"})
        {
            Point from = layout.getPoint(start);

            if (from == null)
            {
                out.append(start).append(": NOT A POINT\n");
                continue;
            }

            boolean moved = layout.moveLocomotive(loc.getName(), start, false);

            Point actually = layout.getLocomotiveLocation(loc);

            out.append("\n== ").append(start)
               .append("\n   moveLocomotive returned ").append(moved)
               .append(", locomotive is at ")
               .append(actually == null ? "NOWHERE" : actually.getName()).append("\n");

            int reachable = 0;
            int blocked = 0;

            for (Point to : layout.getPoints())
            {
                if (to == from || !to.isDestination() || !to.isAutoDestination()) continue;

                if (to.isReversing() || !to.isActive()) continue;

                List<Edge> path = layout.bfs(from, to, null);

                if (path == null) continue;

                reachable++;

                if (layout.isPathClear(path, loc, true))
                {
                    out.append("   CLEAR   -> ").append(to.getName()).append("\n");
                }
                else
                {
                    blocked++;
                    out.append("   BLOCKED -> ").append(to.getName()).append("\n");

                    for (java.util.Map.Entry<List<Edge>, String> tried
                        : layout.debugPath(loc, from, to).entrySet())
                    {
                        out.append("        via ").append(via(tried.getKey())).append("\n")
                           .append("            ")
                           .append(tried.getValue() == null ? "CLEAR" : tried.getValue())
                           .append("\n");
                    }
                }
            }

            out.append("   reachable by bfs: ").append(reachable)
               .append(", blocked by isPathClear: ").append(blocked).append("\n");
        }

        write("6-probe-bottommaina", out);
    }

    /**
     * Exactly what the application shows: the locomotives the configuration places, where it places
     * them, asked for their own paths with nothing moved.
     *
     * Every other probe here moves a locomotive of its own choosing, which changes the occupancy and
     * therefore the answer.  This one touches nothing, so "the UI offers no paths" either reproduces
     * or it does not.
     */
    @Test
    public void testWhatTheUiWouldShow() throws Exception
    {
        // The LIVE setup, not a bundle.  Every time this harness has been pointed at an export it
        // has answered a question about a snapshot somebody took hours ago.
        String configuration = build(null);

        model.parseAuto(configuration);

        Layout layout = model.getAutoLayout();

        StringBuilder out = new StringBuilder("# what the UI would show, live setup\n\n");

        assertTrue(layout != null && layout.isValid(),
            "the live derived configuration must build into a valid layout: "
                + (layout == null ? "no layout" : layout.getInvalidReason()));

        // how many edges carry a length at all - the tile lengths are supposed to feed these
        int withLength = 0;

        for (Edge e : layout.getEdges())
        {
            if (e.getLength() > 0) withLength++;
        }

        out.append("edges ").append(layout.getEdges().size())
           .append(", of which carry a length: ").append(withLength).append("\n\n");

        for (Locomotive loc : layout.getLocomotivesToRun())
        {
            Point at = layout.getLocomotiveLocation(loc);

            out.append("== ").append(loc.getName())
               .append("  at ").append(at == null ? "NOWHERE" : at.getName())
               .append("  (train length ").append(loc.getTrainLength()).append(")\n");

            List<List<Edge>> paths = layout.getPossiblePaths(loc, true);

            if (paths == null || paths.isEmpty())
            {
                out.append("   NO PATHS OFFERED\n");

                // and why: every destination bfs can reach, with the refusal logged
                if (at != null)
                {
                    for (Point to : layout.getPoints())
                    {
                        if (to == at || !to.isDestination() || !to.isAutoDestination()) continue;

                        if (to.isReversing() || !to.isActive()) continue;

                        List<Edge> path = layout.bfs(at, to, null);

                        if (path == null) continue;

                        out.append("     bfs reaches ").append(to.getName()).append("\n");

                        // Every candidate route to that destination WITH the reason it was refused.
                        //
                        // "isPathClear=false" says a train cannot get there and nothing about why, and
                        // the why is the whole question: an occupied platform is traffic and clears
                        // itself, a refused lock edge is a fault in this program.  debugPath is the
                        // layout's own answer, and it is what the UI reports to the user.
                        for (java.util.Map.Entry<List<Edge>, String> tried
                            : layout.debugPath(loc, at, to).entrySet())
                        {
                            out.append("        via ").append(via(tried.getKey())).append("\n")
                               .append("            ")
                               .append(tried.getValue() == null ? "CLEAR" : tried.getValue())
                               .append("\n");
                        }
                    }
                }
            }
            else
            {
                for (List<Edge> path : paths)
                {
                    out.append("   -> ").append(path.get(path.size() - 1).getEnd().getName()).append("\n");
                }
            }
        }

        write("7-as-the-ui-sees-it", out);
    }

    /**
     * Does a train standing at BottomInnerOtherside actually stand on track BR 628 needs?
     *
     * The refusal says "lock edge ... -> BottomInnerOtherside occupied".  A lock edge is meant to hold
     * a shared THROAT clear, and the sensor at the far end of one is not the throat - but that is the
     * intent written in a comment, and the question here is what the tiles say.
     */
    @Test
    public void testWhatTheLockEdgeActuallyShares() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = liveSession();

        org.traincontrol.automationui.GraphReducer reducer = session.getReducer();

        // TST-B5: reducer.getEdges() is the whole reason this test exists - if the reducer stops
        // producing lock edges for the live layout, there is nothing left below to write down or to
        // debug, and that is itself the regression this floor catches.
        assertFalse(reducer.getEdges().isEmpty(),
            "the live layout's reducer produced no edges at all - nothing to report on");

        StringBuilder out = new StringBuilder("# what the lock edge shares\n");

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            out.append(edge.getStart()).append("  ->  ").append(edge.getEnd()).append("\n");

            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                out.append("        ").append(step.getTile()).append("\n");
            }
        }

        write("8-edge-tiles", out);
    }

    /**
     * The live setup, opened against the model's own pages.
     */
    private org.traincontrol.automationui.AutonomySession liveSession() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session =
            new org.traincontrol.automationui.AutonomySession(new File("test/test_layout"));

        List<org.traincontrol.base.LayoutDiagram> pages = new ArrayList<>();

        for (String name : model.getLayoutList())
        {
            pages.add(model.getLayout(name));
        }

        session.open(pages);

        return session;
    }

    private String via(List<Edge> path)
    {
        StringBuilder out = new StringBuilder(path.get(0).getStart().getName());

        for (Edge e : path)
        {
            out.append(" > ").append(e.getEnd().getName());
        }

        return out.toString();
    }

    private void write(String label, StringBuilder body) throws Exception
    {
        File file = new File(OUT, label + ".txt");

        Files.write(file.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("WROTE " + file.getAbsolutePath());

        int head = body.indexOf("\n\ntotals");

        System.out.println(body.substring(0, Math.min(body.length(), 700)));

        if (head > 0) System.out.println("..." + body.substring(head));
    }
}
