package support;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Writes out everything an old setup means to one build, so two builds can be compared file by file (FR-111):
 * `docs/tools/old-setups.sh` runs this on a base commit and on the working tree and compares.  Compiled into the older
 * build too, so it may ask only what every build since 3.0.8 has.
 *
 * One folder per fixture: `settings.txt` (what was loaded), `graph.txt` (what it built), `paths.txt` (every route
 * between stations for the locomotives sampled, whether it is clear and why not), `offers.txt` (each tier's answer),
 * `runs.txt` (simulated trips picked by a fixed seed),
 * `railway.json` (the running layout's own file), and `saved-unchanged/` and `saved-captured/` (the setup's files after
 * a save with no change, and after the exit's capture and save).  `input/` is the files as they came.
 *
 * The railway shuffles its route search and its choice of station with `Collections.shuffle`, which has no seed to give,
 * so `seed` plants one in the shared generator before each question: the same question then has the same answer in any
 * build that asks the generator as often.
 */
public final class SetupDump
{
    /** How much of each fixture to ask: more on the operator's railway only if the time can be spared. */
    public static final class Sample
    {
        public final int locomotives;
        public final int alternatives;
        public final int trips;

        public Sample(int locomotives, int alternatives, int trips)
        {
            this.locomotives = locomotives;
            this.alternatives = alternatives;
            this.trips = trips;
        }
    }

    private SetupDump()
    {
    }

    /**
     * Dumps a layout folder, read from a copy; the folder itself is never written.
     *
     * @param folder the layout, with its `config` folder
     * @param out where the dump goes; made if missing
     * @param sample how much to ask
     * @return how many answers were written, by what they are
     */
    public static Map<String, Integer> layout(File folder, File out, Sample sample) throws Exception
    {
        Map<String, Integer> counts = new TreeMap<>();

        out.mkdirs();

        Scenario sandbox = Scenario.openSimulated(folder);

        try
        {
            copyTree(new File(folder, "config/autonomy"), new File(out, "input"));

            MarklinControlStation model = sandbox.getModel();

            model.stop();

            AutonomySession session = sandbox.getSession();

            File files = new File(sandbox.getFolder(), "config/autonomy");

            writeSettings(session, new File(out, "settings.txt"), counts);

            String built = session.buildConfiguration();

            // Saved with no change, before anything is built from it
            session.save();

            copyTree(files, new File(out, "saved-unchanged"));

            Layout railway = read(model, built, out);

            if (railway == null) return counts;

            writeGraph(railway, built, new File(out, "graph.txt"), counts);

            writeText(new File(out, "railway.json"), railway.toJSON());

            session.captureFromLayout(railway.toJSON());
            session.save();

            copyTree(files, new File(out, "saved-captured"));

            dumpRailway(model, railway, out, sample, counts);

            return counts;
        }
        finally
        {
            sandbox.close();
        }
    }

    /**
     * Dumps a railway read straight from a JSON file, the old autonomy formats.
     *
     * @param over the layout folder the file is read over
     * @param json the file
     */
    public static Map<String, Integer> json(File over, File json, File out, Sample sample) throws Exception
    {
        Map<String, Integer> counts = new TreeMap<>();

        out.mkdirs();

        Scenario sandbox = Scenario.openSimulated(over);

        try
        {
            MarklinControlStation model = sandbox.getModel();

            model.stop();

            String text = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);

            Files.copy(json.toPath(), new File(out, "input.json").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Layout railway = read(model, text, out);

            if (railway == null) return counts;

            writeGraph(railway, text, new File(out, "graph.txt"), counts);

            writeText(new File(out, "railway.json"), railway.toJSON());

            dumpRailway(model, railway, out, sample, counts);

            return counts;
        }
        finally
        {
            sandbox.close();
        }
    }

    /** The railway this text builds, or null, having said why in the dump. */
    private static Layout read(MarklinControlStation model, String text, File out) throws IOException
    {
        String refused;

        try
        {
            model.parseAuto(text);

            refused = model.getAutoLayout() == null ? "no railway: " + Layout.getLastError() : null;
        }
        catch (Exception thrown)
        {
            refused = "refused: " + thrown.getMessage();
        }

        if (refused != null) writeLines(new File(out, "graph.txt"), Arrays.asList(refused));

        return refused != null ? null : model.getAutoLayout();
    }

    private static void dumpRailway(MarklinControlStation model, Layout railway, File out, Sample sample,
        Map<String, Integer> counts) throws Exception
    {
        railway.setSimulate(true);
        railway.setMinDelay(0);
        railway.setMaxDelay(0);

        List<Locomotive> sampled = sampleOf(model);

        if (sampled.size() > sample.locomotives) sampled = sampled.subList(0, sample.locomotives);

        counts.put("locomotives", sampled.size());
        counts.put("locomotives known", model.getLocomotives().size());

        writeAnswers(railway, sampled, sample, new File(out, "paths.txt"), new File(out, "offers.txt"), counts);

        writeRuns(railway, sampled, sample, new File(out, "runs.txt"), counts);

        // And the file once the trains have moved
        writeText(new File(out, "railway-after-runs.json"), railway.toJSON());
    }

    /** Seeds the generator `Collections.shuffle` shares, for this question.  Java 8: the field is reachable. */
    private static void seed(String question) throws Exception
    {
        java.lang.reflect.Field shared = Collections.class.getDeclaredField("r");

        shared.setAccessible(true);

        shared.set(null, new Random(question.hashCode()));
    }

    /** Every locomotive, by name; the same order whichever build asks. */
    private static List<Locomotive> sampleOf(MarklinControlStation model)
    {
        List<Locomotive> out = new ArrayList<>(model.getLocomotives());

        Collections.sort(out, Comparator.comparing(Locomotive::getName));

        return out;
    }

    private static void writeSettings(AutonomySession session, File to, Map<String, Integer> counts) throws IOException
    {
        List<String> lines = new ArrayList<>();

        lines.add("active: " + session.getStore().getActiveConfiguration());

        int settings = 0;

        for (String name : new TreeSet<>(session.getStore().getConfigurationNames()))
        {
            JSONObject configuration = session.getStore().getConfiguration(name);

            lines.add("configuration " + name + ": " + canonical(configuration));

            JSONObject points = configuration == null ? null : configuration.optJSONObject("points");

            if (points == null) continue;

            for (String tile : points.keySet())
            {
                settings += points.getJSONObject(tile).length();
            }
        }

        lines.add("companion: " + canonical(session.getStore().snapshotSetup()));

        List<String> findings = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check()) findings.add(said(finding));

        Collections.sort(findings);

        lines.addAll(findings);

        counts.put("settings", settings);
        counts.put("findings", findings.size());

        writeLines(to, lines);
    }

    /** A finding as what it says, which is all of it a later reader needs. */
    public static String said(AutonomyChecks.Finding finding)
    {
        return finding.getSeverity() + " " + finding.getMessageKey() + " " + finding.getSubject() + " " + finding.getTile();
    }

    private static void writeGraph(Layout railway, String built, File to, Map<String, Integer> counts) throws Exception
    {
        List<String> lines = new ArrayList<>();

        lines.add("valid: " + railway.isValid());

        lines.add("built: " + canonical(new JSONObject(built)));

        TreeSet<String> points = new TreeSet<>();

        for (Point p : railway.getPoints())
        {
            points.add(String.join(" ", "point", canonical(p.toJSON()), "terminus=" + p.isTerminus(),
                "reversing=" + p.isReversing(), "active=" + p.isActive(), "auto=" + p.isAutoDestination(),
                "block=" + p.getBlock(), "sendable=" + railway.isSendableDestination(p),
                "stationSquare=" + railway.isAStationSquare(p), "reach=" + railway.canReachAnyDestination(p)));
        }

        lines.addAll(points);

        List<String> edges = new ArrayList<>();

        for (Edge e : railway.getEdges())
        {
            List<String> locks = new ArrayList<>();

            for (Edge lock : e.getLockEdges()) locks.add(lock.getName());

            Collections.sort(locks);

            edges.add("edge " + e.getName() + " length=" + e.getLength() + " locks=" + locks);
        }

        Collections.sort(edges);

        lines.addAll(edges);

        counts.put("points", points.size());
        counts.put("edges", edges.size());

        writeLines(to, lines);
    }

    /** Every pair of stations, every route the search gives up to the sample's limit, for each locomotive sampled. */
    private static void writeAnswers(Layout railway, List<Locomotive> sampled, Sample sample, File paths, File offers,
        Map<String, Integer> counts) throws Exception
    {
        List<Point> stations = new ArrayList<>();

        for (Point p : railway.getPoints()) if (p.isDestination()) stations.add(p);

        Collections.sort(stations, Comparator.comparing(Point::getName));

        counts.put("stations", stations.size());

        int pairs = 0;
        int answers = 0;
        int offered = 0;

        try (PrintWriter pathLines = writer(paths); PrintWriter offerLines = writer(offers))
        {
            // The routes are the track's, whichever train asks
            Map<String, List<List<Edge>>> routes = new TreeMap<>();

            for (Point from : stations)
            {
                for (Point to : stations)
                {
                    if (from == to) continue;

                    pairs++;

                    seed(from.getName() + to.getName());

                    routes.put(from.getName() + "\u0000" + to.getName(), routesBetween(railway, from, to, sample));
                }
            }

            for (Locomotive loc : sampled)
            {
                Point wasAt = railway.getLocomotiveLocation(loc);

                for (Point from : stations)
                {
                    Locomotive standing = from.getBlockLocomotive();

                    if (standing != null && standing != loc) continue;

                    from.setLocomotive(loc);

                    seed(loc.getName() + from.getName());

                    offerLines.println(loc.getName() + " at " + from.getName() + " cannot start: "
                        + railway.explainCannotStart(loc) + " / by hand " + railway.explainCannotStart(loc, true));

                    offerLines.println("  autonomy " + railway.explainDestinations(loc, false));
                    offerLines.println("  by hand " + railway.explainDestinations(loc, true));
                    offerLines.println("  barred " + new TreeSet<>(railway.destinationsBarredFromAutonomy(loc)));

                    List<String> offeredHere = new ArrayList<>();

                    for (List<Edge> path : railway.getPossiblePaths(loc, false)) offeredHere.add(namesOf(path));

                    offered += offeredHere.size();

                    offerLines.println("  offered " + offeredHere);

                    for (Point to : stations)
                    {
                        if (from == to) continue;

                        int alternative = 0;

                        for (List<Edge> path : routes.get(from.getName() + "\u0000" + to.getName()))
                        {
                            boolean clear = railway.isPathClear(path, loc, false);

                            pathLines.println(loc.getName() + "|" + from.getName() + "|" + to.getName() + "|"
                                + (alternative++) + "|" + namesOf(path) + "|" + clear + "|"
                                + (clear ? "" : Layout.getLastError()));

                            answers++;
                        }

                        if (alternative == 0)
                        {
                            pathLines.println(loc.getName() + "|" + from.getName() + "|" + to.getName() + "|no route");

                            answers++;
                        }
                    }

                    from.setLocomotive(null);
                }

                // Back where the setup put it, so the next train meets the railway as loaded
                if (wasAt != null) wasAt.setLocomotive(loc);
            }
        }

        counts.put("station pairs", pairs);
        counts.put("path answers", answers);
        counts.put("routes offered", offered);
    }

    private static List<List<Edge>> routesBetween(Layout railway, Point from, Point to, Sample sample) throws Exception
    {
        List<List<Edge>> seen = new LinkedList<>();

        for (int i = 0; i < sample.alternatives; i++)
        {
            List<Edge> path = railway.bfs(from, to, seen);

            if (path == null) break;

            seen.add(path);
        }

        return seen;
    }

    /**
     * Simulated trips to their end, chosen by a fixed seed from what the railway offers, by name, so two builds offered
     * the same make the same choice and any change shows at the first trip it touches.
     */
    private static void writeRuns(Layout railway, List<Locomotive> sampled, Sample sample, File to,
        Map<String, Integer> counts) throws Exception
    {
        Random seeded = new Random(111);

        // The trains the setup placed, sampled or not, as they are the ones with somewhere to go
        Map<String, Locomotive> placed = new TreeMap<>();

        for (Point p : railway.getPoints())
        {
            if (p.getCurrentLocomotive() != null) placed.put(p.getCurrentLocomotive().getName(), p.getCurrentLocomotive());
        }

        List<Locomotive> standing = new ArrayList<>(placed.values());

        // A fixture nobody is placed on gets one train on its first free station
        if (standing.isEmpty() && !sampled.isEmpty())
        {
            List<Point> stations = new ArrayList<>();

            for (Point p : railway.getPoints())
            {
                if (p.isDestination() && p.getBlockLocomotive() == null) stations.add(p);
            }

            Collections.sort(stations, Comparator.comparing(Point::getName));

            if (!stations.isEmpty())
            {
                stations.get(0).setLocomotive(sampled.get(0));
                standing.add(sampled.get(0));
            }
        }

        int trips = 0;

        try (PrintWriter lines = writer(to))
        {
            if (standing.isEmpty()) return;

            for (int i = 0; i < sample.trips; i++)
            {
                Locomotive loc = standing.get(seeded.nextInt(standing.size()));

                List<String> names = new ArrayList<>();
                Map<String, List<Edge>> offers = new TreeMap<>();

                seed(loc.getName() + i);

                for (List<Edge> path : railway.getPossiblePaths(loc, false))
                {
                    offers.put(namesOf(path), path);
                }

                names.addAll(offers.keySet());

                if (names.isEmpty())
                {
                    lines.println(i + " " + loc.getName() + " at " + railway.getLocomotiveLocation(loc)
                        + " offered nothing");

                    continue;
                }

                String chosen = names.get(seeded.nextInt(names.size()));

                // Every other trip as autonomy would choose and send it, the rest as a person would
                seed(loc.getName() + "picks" + i);

                List<Edge> picked = i % 2 == 0 ? null : railway.pickPath(loc);

                boolean arrived = picked != null ? railway.executePath(picked, loc, 50, null)
                    : railway.executePathByHand(offers.get(chosen), loc, 50, Layout.ALWAYS_REVERSE, -1);

                if (picked != null) chosen = namesOf(picked);

                trips++;

                settle(railway);

                lines.println(i + " " + loc.getName() + " took " + chosen + " arrived=" + arrived + " now at "
                    + railway.getLocomotiveLocation(loc) + " direction=" + loc.getDirection());
            }
        }

        counts.put("simulated trips", trips);
    }

    /** Waits for the simulated sensors to fall behind a trip, or the next answer depends on how quickly they did. */
    private static void settle(Layout railway) throws InterruptedException
    {
        long until = System.currentTimeMillis() + 15000;

        while (railway.isRunning() && System.currentTimeMillis() < until) Thread.sleep(20);

        // Longer than the simulation holds a sensor
        Thread.sleep(1500);
    }

    /** Sorted keys all the way down, so two runs of one file read the same whatever order a map kept. */
    public static String canonical(Object value)
    {
        if (value instanceof JSONObject)
        {
            JSONObject object = (JSONObject) value;

            StringBuilder out = new StringBuilder("{");

            for (String key : new TreeSet<>(object.keySet()))
            {
                out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key))).append(',');
            }

            return out.append('}').toString();
        }

        if (value instanceof JSONArray)
        {
            StringBuilder out = new StringBuilder("[");

            for (Object one : (JSONArray) value) out.append(canonical(one)).append(',');

            return out.append(']').toString();
        }

        return String.valueOf(value);
    }

    private static String namesOf(List<Edge> path)
    {
        StringBuilder out = new StringBuilder();

        for (Edge e : path) out.append(e.getName()).append(' ');

        return out.toString();
    }

    private static PrintWriter writer(File to) throws IOException
    {
        return new PrintWriter(Files.newBufferedWriter(to.toPath(), StandardCharsets.UTF_8));
    }

    private static void writeLines(File to, List<String> lines) throws IOException
    {
        Files.write(to.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static void writeText(File to, String text) throws IOException
    {
        Files.write(to.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    /** Copies the files of a folder, byte for byte; nothing when it is not there. */
    private static void copyTree(File from, File to) throws IOException
    {
        File[] files = from.listFiles();

        to.mkdirs();

        if (files == null) return;

        for (File file : files)
        {
            if (file.isDirectory()) copyTree(file, new File(to, file.getName()));
            else Files.copy(file.toPath(), new File(to, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
