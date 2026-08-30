import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.prefs.Preferences;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Records what one autonomy engine will do with four trains, so two engines can be compared.
 *
 * Adam: "we need to do a proper parity test between the old autonomy setup and the new one.  I suspect
 * that (a) our pathing algorithm setting isn't being respected and (b) graph locking is too eager,
 * excluding valid paths that don't actually collide."
 *
 * COMPILED TWICE, against 2.8.1 and against the current tree, and it uses nothing that is new. Every
 * call here - fromJSON, moveLocomotive, getPossiblePaths, getLockEdges - exists in both, so a
 * difference in the output is a difference in the engine rather than in the questions asked of it. The
 * one thing 3.0.0 can do and 2.8.1 cannot, deriving a graph from the track diagram, happens before this
 * runs and reaches it as a JSON file like any other.
 *
 * THE TWO GRAPHS DO NOT SHARE A NAMESPACE, and finding that out is what this driver had to be rebuilt
 * for. 2.8.1 has one Point called "BottomMainA". 3.0.0 splits every station by the direction a train
 * faces - "BottomMainB (westbound, reverse)", "BottomMainC (eastbound)" - because facing is carried by
 * one-way edges rather than by any field on the train. So "BottomMainA" simply does not exist in the
 * new graph, and the first run of this driver said exactly that: `Point BottomMainA does not exist`.
 *
 * That means "standing at BottomMainA" is not one state in 3.0.0 but several, one per facing, and they
 * do NOT offer the same routes - which is the whole reason for splitting them. The honest comparison is
 * therefore: everything reachable from 2.8.1's BottomMainA should be reachable from SOME facing of
 * 3.0.0's BottomMainA. Anything stricter fails the superset test for trains that were merely pointed
 * the wrong way; anything looser lets 3.0.0 claim a route no real train could take. So each place is
 * enumerated once per facing, tagged with the facing, and the report unions them.
 *
 * ENUMERATION IS THE EVIDENCE, NOT THE TIMED RUN. "The routes offered in 3.0.0 should be a superset of
 * those in 2.8.1" is a question about which paths the engine will CONSIDER, and getPossiblePaths
 * answers it exactly, for every train and every facing, in one pass. Watching an autonomy run answers
 * it only for the paths that happened to come up, and a path missing from a five-minute run may be
 * missing because it is excluded or because it was unlucky - which is the distinction the whole
 * exercise turns on.
 *
 * It is also the ONLY evidence available here, because simulate mode says so itself: "Auto layout
 * development / simulation mode enabled. Trains will not run." The timed run is kept, because Adam
 * asked for the timing and because it would be the check on the enumeration - a path that runs but was
 * never enumerated means the enumeration is lying - but on a simulated station it records nothing, and
 * saying that plainly beats handing back an empty table that looks like a result.
 *
 * CONCURRENCY IS COMPUTED, NOT OBSERVED. Two paths can run at once exactly when the edges they lock do
 * not intersect, which is a property of the graph rather than of what two trains happened to do.
 * Observing it needs both trains ready at the same moment; computing it needs the lock sets, which is
 * what the LOCK lines carry. That is concern (b) measured directly: if 3.0.0 locks edges 2.8.1 did not,
 * the pairs that stop being able to run together say so by name.
 *
 * The output is tab-separated lines rather than JSON, on purpose - it has to be written identically by
 * two builds a year apart, and the fewer of their libraries it depends on the better.
 *
 * Usage: ParityDriver &lt;layoutFolder&gt; &lt;autonomyJson&gt; &lt;outFile&gt; &lt;label&gt; [runSeconds]
 */
public class ParityDriver
{
    /** Where the four test trains stand.  Adam named these; both graphs have them, under two spellings. */
    private static final String[] PLACES =
        {"BottomMainA", "BottomMainB", "BottomMainC", "BottomInner"};

    private static final int[] ADDRESSES = {901, 902, 903, 904};

    private static final String PREFIX = "PARITY-";

    public static void main(String[] args) throws Exception
    {
        if (args.length < 4)
        {
            System.err.println("Usage: ParityDriver <layoutFolder> <autonomyJson> <outFile> <label>"
                + " [runSeconds]");

            System.exit(2);
        }

        File layoutFolder = new File(args[0]).getAbsoluteFile();
        File autonomyJson = new File(args[1]).getAbsoluteFile();
        File out = new File(args[2]).getAbsoluteFile();
        String label = args[3];

        int runSeconds = args.length >= 5 ? Integer.parseInt(args[4]) : 0;

        if (!layoutFolder.isDirectory()) throw new IllegalStateException("no layout at " + layoutFolder);
        if (!autonomyJson.isFile()) throw new IllegalStateException("no autonomy at " + autonomyJson);

        // POINTED AT THE COPY, THE WAY THE APPLICATION IS POINTED AT ONE.
        //
        // This preference is what the Layout Override menu item writes, and its key is namespaced by a
        // hash of the working directory - so each environment folder already has preferences of its
        // own and the two runs cannot read each other's. That namespacing is also why the application
        // behaves differently when launched from different folders.
        Preferences prefs = Preferences.userNodeForPackage(TrainControlUI.class);

        prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, layoutFolder.getAbsolutePath());

        // Simulate and debug - what the command line spells as three arguments - minus the window,
        // which has nothing to show a script.
        //
        // autoPowerOn is FALSE, and that is not a preference: with it true this blocks at "Restoring
        // state...", because powering on asks a Central Station that is not there and there is no
        // window to put the question to. The suite's own fixtures call init(null, true, false, false,
        // ...) for the same reason.
        MarklinControlStation control = MarklinControlStation.init(null, true, false, false, true);

        Layout layout = Layout.fromJSON(
            new String(Files.readAllBytes(autonomyJson.toPath()), Charset.forName("UTF-8")), control);

        if (layout == null) throw new IllegalStateException("the engine refused " + autonomyJson);

        List<String> lines = new ArrayList<>();

        lines.add(join("META", label, autonomyJson.getName(), layoutFolder.getName()));

        // ==================================================================== the board, as found
        //
        // Every point, with the flags the comparison needs. isReversing is here because Adam said to
        // ignore reversing stations as destinations on the 2.8.1 side - they are parking - and a list
        // that decided that for itself would be one more thing to get wrong.
        for (Point point : sorted(layout.getPoints()))
        {
            lines.add(join("POINT", point.getName(), baseName(point.getName()),
                bool(point.isDestination()), bool(point.isReversing()),
                bool(point.isTerminus()), bool(point.isActive())));
        }

        // ==================================================================== the trains
        for (int i = 0; i < PLACES.length; i++)
        {
            String name = PREFIX + ADDRESSES[i];

            // Deleted and remade rather than reused, so a leftover from an earlier run cannot bring a
            // train length, a function mapping or a placement with it.
            control.deleteLoc(name);

            if (control.newDCCLocomotive(name, ADDRESSES[i]) == null)
            {
                throw new IllegalStateException("could not create " + name);
            }
        }

        // Which actual Points each requested place is.  One in 2.8.1, several in 3.0.0.
        List<List<String>> variants = new ArrayList<>();

        for (String place : PLACES)
        {
            List<String> found = variantsOf(layout, place);

            if (found.isEmpty())
            {
                throw new IllegalStateException("neither \"" + place + "\" nor any facing of it exists"
                    + " - the comparison is meaningless if the trains cannot start in the same places");
            }

            lines.add(join("PLACE", place, joinWith('|', found)));

            variants.add(found);
        }

        // ==================================================================== what each train may do
        //
        // One scenario per facing of the train under test, with the other three standing at their first
        // facing - because a train on a point occupies it, and an occupied point is refused as a
        // destination. Everything is cleared before each scenario so that the layout's own locomotives
        // cannot remove destinations from one engine and not the other, quietly.
        for (int i = 0; i < PLACES.length; i++)
        {
            String name = PREFIX + ADDRESSES[i];

            for (String facing : variants.get(i))
            {
                clearEveryPoint(layout);

                for (int j = 0; j < PLACES.length; j++)
                {
                    if (j == i) continue;

                    place(layout, PREFIX + ADDRESSES[j], variants.get(j).get(0));
                }

                place(layout, name, facing);

                Locomotive loc = control.getLocByName(name);

                List<List<Edge>> paths = layout.getPossiblePaths(loc, false);

                lines.add(join("PATHCOUNT", name, facing, String.valueOf(paths.size())));

                for (List<Edge> path : paths)
                {
                    if (path == null || path.isEmpty()) continue;

                    String start = path.get(0).getStart().getName();
                    String end = path.get(path.size() - 1).getEnd().getName();

                    lines.add(join("PATH", name, facing, start, end, edgeNames(path)));

                    // The lock set, which is what decides whether two of these can run at once.
                    List<String> locked = new ArrayList<>();

                    for (Edge edge : path)
                    {
                        locked.add(edge.getName());

                        List<Edge> also = edge.getLockEdges();

                        if (also != null)
                        {
                            for (Edge other : also) locked.add(other.getName());
                        }
                    }

                    java.util.Collections.sort(locked);

                    lines.add(join("LOCK", name, facing, start, end, joinWith('|', locked)));
                }
            }
        }

        // ==================================================================== and what it actually did
        if (runSeconds > 0)
        {
            clearEveryPoint(layout);

            List<Locomotive> locs = new ArrayList<>();

            for (int i = 0; i < PLACES.length; i++)
            {
                place(layout, PREFIX + ADDRESSES[i], variants.get(i).get(0));

                locs.add(control.getLocByName(PREFIX + ADDRESSES[i]));
            }

            lines.add(join("RUNSTART", String.valueOf(System.currentTimeMillis())));

            layout.setLocomotivesToRun(locs);
            layout.runLocomotives();

            long until = System.currentTimeMillis() + (runSeconds * 1000L);

            java.util.Map<String, String> lastSeen = new java.util.HashMap<>();

            while (System.currentTimeMillis() < until)
            {
                for (Locomotive loc : locs)
                {
                    String now = "(moving)";

                    for (Point point : layout.getPoints())
                    {
                        if (loc.equals(point.getCurrentLocomotive()))
                        {
                            now = point.getName();
                            break;
                        }
                    }

                    if (!now.equals(lastSeen.get(loc.getName())))
                    {
                        lines.add(join("AT", String.valueOf(System.currentTimeMillis()),
                            loc.getName(), now));

                        lastSeen.put(loc.getName(), now);
                    }
                }

                Thread.sleep(250);
            }

            lines.add(join("RUNEND", String.valueOf(System.currentTimeMillis())));
        }

        PrintWriter writer = new PrintWriter(out, "UTF-8");

        try
        {
            for (String line : lines) writer.println(line);
        }
        finally
        {
            writer.close();
        }

        System.out.println("wrote " + lines.size() + " lines to " + out);

        // The simulated station keeps threads alive; nothing here needs them once the file is written.
        System.exit(0);
    }

    /**
     * Every Point that is this station, under whatever name the engine gives it.
     *
     * 2.8.1 returns one, the name itself. 3.0.0 returns one per facing, spelled "Name (eastbound)" and
     * so on. Matched on the exact name or on the name followed by " (", so that BottomInner does not
     * also collect BottomInnerOtherside - a different place on the railway, and one this layout has.
     */
    private static List<String> variantsOf(Layout layout, String place)
    {
        List<String> out = new ArrayList<>();

        for (Point point : sorted(layout.getPoints()))
        {
            String name = point.getName();

            if (!name.equals(place) && !name.startsWith(place + " (")) continue;

            // ONLY THE FACINGS A TRAIN CAN STAND ON.
            //
            // Splitting a station by facing does not make every facing a platform: 3.0.0 has
            // "BottomInner (eastbound)", and moveLocomotive refuses it - "BottomInner (eastbound) is
            // not a station." A facing no train can occupy offers no routes from it either, so
            // including it would add an empty scenario and a crash, in that order.
            if (!point.isDestination()) continue;

            out.add(name);
        }

        return out;
    }

    /** "BottomMainB (westbound, reverse)" is BottomMainB, for comparison across the two namespaces. */
    private static String baseName(String name)
    {
        int at = name.indexOf(" (");

        return at < 0 ? name : name.substring(0, at);
    }

    private static void clearEveryPoint(Layout layout)
    {
        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) point.setLocomotive(null);
        }
    }

    private static void place(Layout layout, String loc, String point)
    {
        if (!layout.moveLocomotive(loc, point, true))
        {
            throw new IllegalStateException(loc + " would not go on " + point);
        }
    }

    private static List<Point> sorted(Collection<Point> points)
    {
        List<Point> out = new ArrayList<>(points);

        java.util.Collections.sort(out, new java.util.Comparator<Point>()
        {
            @Override
            public int compare(Point a, Point b)
            {
                return a.getName().compareTo(b.getName());
            }
        });

        return out;
    }

    private static String edgeNames(List<Edge> path)
    {
        List<String> names = new ArrayList<>();

        for (Edge edge : path) names.add(edge.getName());

        return joinWith('|', names);
    }

    private static String bool(boolean value)
    {
        return value ? "1" : "0";
    }

    private static String join(String... parts)
    {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0) out.append('\t');

            out.append(parts[i] == null ? "" : parts[i].replace('\t', ' '));
        }

        return out.toString();
    }

    private static String joinWith(char separator, List<String> parts)
    {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < parts.size(); i++)
        {
            if (i > 0) out.append(separator);

            out.append(parts.get(i));
        }

        return out.toString();
    }
}
