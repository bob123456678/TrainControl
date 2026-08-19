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
 * answer with its intermediate points, for a person to check.  A REPORT, not an assertion: it fails
 * only if a configuration will not build.  What the routes ought to be is Adam's to say.
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
        model = init(null, true, false, false, true);

        OUT.mkdirs();
    }

    @Test
    public void testDerivedRoutes() throws Exception
    {
        report("1-derived-active", build(null));
    }

    @Test
    public void testStuckBundleRoutes() throws Exception
    {
        File bundle = new File("tc_backup/Autonomy 1b.json");

        if (!bundle.isFile()) return;

        report("2-stuck-1b", build(bundle));
    }

    @Test
    public void testHandAuthoredRoutes() throws Exception
    {
        File hand = new File("cs2_sample_layout/config/autorun/autonomy.json");

        if (!hand.isFile()) return;

        report("3-hand-authored-2.8.1",
            new String(Files.readAllBytes(hand.toPath()), StandardCharsets.UTF_8));
    }

    /**
     * The configuration the diagram derives, optionally with a bundle imported over the setup first.
     */
    private String build(File bundle) throws Exception
    {
        AutonomySession session = new AutonomySession(new File("cs2_sample_layout"));

        List<org.traincontrol.base.LayoutDiagram> pages = new ArrayList<>();

        for (String name : model.getLayoutList())
        {
            pages.add(model.getLayout(name));
        }

        session.open(pages);

        if (bundle != null)
        {
            session.importBundle("probe", new JSONObject(
                new String(Files.readAllBytes(bundle.toPath()), StandardCharsets.UTF_8)));
        }

        return session.buildConfiguration();
    }

    /**
     * Places one locomotive at each destination in turn and writes down every route offered from there.
     *
     * One at a time on purpose: with several placed, every answer also depends on where the others are
     * standing, and the question here is what the TRACK allows.  Occupancy is a different test.
     */
    private void report(String label, String configuration) throws Exception
    {
        StringBuilder out = new StringBuilder("# ").append(label).append("\n\n");

        model.parseAuto(configuration);

        Layout layout = model.getAutoLayout();

        if (layout == null || !layout.isValid())
        {
            out.append("CONFIGURATION IS INVALID - nothing can run.\n");

            if (layout != null) out.append("reason: ").append(layout.getInvalidReason()).append("\n");

            write(label, out);

            return;
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
            return;
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
