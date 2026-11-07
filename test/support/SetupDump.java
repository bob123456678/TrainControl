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
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
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
 * `runs.txt` (simulated trips picked by a fixed seed), `homes.txt` (Return Home, as set and for homes chosen by the seed:
 * the outcome, the parity audit, and where each train ended with its direction), `home-plans.txt` (those plans' moves,
 * apart because a planner from before OB-413, 3.0.8's included, picks among plans by its wall clock; line one says
 * which kind of planner wrote it),
 * `timetables.txt` (the stored timetable and one recorded from the railway's offers, run to the end),
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

            dumpRailway(model, railway, built, out, sample, counts);

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

            dumpRailway(model, railway, text, out, sample, counts);

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

    private static void dumpRailway(MarklinControlStation model, Layout railway, String text, File out, Sample sample,
        Map<String, Integer> counts) throws Exception
    {
        unpaced(railway);

        List<Locomotive> sampled = sampleOf(model);

        if (sampled.size() > sample.locomotives) sampled = sampled.subList(0, sample.locomotives);

        counts.put("locomotives", sampled.size());
        counts.put("locomotives known", model.getLocomotives().size());

        writeAnswers(railway, sampled, sample, new File(out, "paths.txt"), new File(out, "offers.txt"), counts);

        writeRuns(railway, sampled, sample, new File(out, "runs.txt"), counts);

        // And the file once the trains have moved
        writeText(new File(out, "railway-after-runs.json"), railway.toJSON());

        writeTimetables(model, text, sampled, new File(out, "timetables.txt"), counts);

        writeHomes(model, text, sampled, new File(out, "homes.txt"), counts);
    }

    private static void unpaced(Layout railway) throws Exception
    {
        railway.setSimulate(true);
        railway.setMinDelay(0);
        railway.setMaxDelay(0);
    }

    /** The railway as the text builds it, with nothing moved. */
    private static Layout fresh(MarklinControlStation model, String text) throws Exception
    {
        model.parseAuto(text);

        unpaced(model.getAutoLayout());

        return model.getAutoLayout();
    }

    /** The trains the setup placed, by name, with sampled ones put on free stations until there are this many. */
    private static List<Locomotive> trainsOn(Layout railway, List<Locomotive> sampled, int atLeast)
    {
        Map<String, Locomotive> placed = new TreeMap<>();

        for (Point p : railway.getPoints())
        {
            if (p.getCurrentLocomotive() != null) placed.put(p.getCurrentLocomotive().getName(), p.getCurrentLocomotive());
        }

        Map<String, Point> free = new TreeMap<>();

        for (Point p : railway.getPoints())
        {
            if (p.isDestination() && p.isActive() && p.getBlockLocomotive() == null) free.put(p.getName(), p);
        }

        for (Locomotive loc : sampled)
        {
            if (placed.size() >= atLeast) break;

            if (placed.containsKey(loc.getName())) continue;

            for (Point p : free.values())
            {
                if (p.getBlockLocomotive() != null) continue;

                p.setLocomotive(loc);

                placed.put(loc.getName(), loc);

                break;
            }
        }

        return new ArrayList<>(placed.values());
    }

    /** What the railway offers this train now, by name, the same whichever build asks. */
    private static Map<String, List<Edge>> offersTo(Layout railway, Locomotive loc, String question) throws Exception
    {
        seed(question);

        Map<String, List<Edge>> offers = new TreeMap<>();

        for (List<Edge> path : railway.getPossiblePaths(loc, false)) offers.put(namesOf(path), path);

        return offers;
    }

    /** Runs what the timetable holds to its end, with a limit so a run that never ends is an answer and no hang. */
    private static String played(Layout railway) throws Exception
    {
        boolean[] returned = new boolean[1];

        Thread run = new Thread(() -> returned[0] = railway.executeTimetable(), "dump playing a timetable");

        run.setDaemon(true);
        run.start();
        run.join(PLAY_LIMIT_MS);

        if (run.isAlive())
        {
            railway.stopLocomotives();

            run.join(30000);

            return "did not finish in " + PLAY_LIMIT_MS + " ms";
        }

        settle(railway);

        return "returned " + returned[0] + ", stopped because " + railway.whyTheTimetableStopped();
    }

    private static final long PLAY_LIMIT_MS = 180000;

    private static String whereTheyAre(Layout railway, List<Locomotive> trains)
    {
        List<String> out = new ArrayList<>();

        for (Locomotive loc : trains)
        {
            Point at = railway.getLocomotiveLocation(loc);

            out.add(loc.getName() + " at " + (at == null ? "nowhere" : at.getName()) + " " + loc.getDirection());
        }

        return out.toString();
    }

    /** Stands the train on this point alone, unless another train is there. */
    private static void standOn(Layout railway, Locomotive loc, Point point) throws Exception
    {
        if (point.getBlockLocomotive() != null && !loc.equals(point.getBlockLocomotive())) return;

        Point at = railway.getLocomotiveLocation(loc);

        if (at != null && at != point) at.setLocomotive(null);

        point.setLocomotive(loc);
    }

    /** The first entries of the stored timetable, each train stood where it starts; then one recorded from the offers. */
    private static void writeTimetables(MarklinControlStation model, String text, List<Locomotive> sampled, File to,
        Map<String, Integer> counts) throws Exception
    {
        int entries = 0;
        int finished = 0;

        try (PrintWriter lines = writer(to))
        {
            Layout stored = fresh(model, text);

            List<TimetablePath> kept = new ArrayList<>(stored.getTimetable());

            kept = kept.subList(0, Math.min(6, kept.size()));

            List<Locomotive> theirs = new ArrayList<>();

            for (TimetablePath entry : kept)
            {
                if (!entry.isRunnable() || theirs.contains(entry.getLoc())) continue;

                theirs.add(entry.getLoc());

                standOn(stored, entry.getLoc(), entry.getPath().get(0).getStart());
            }

            lines.println("stored: " + stored.getTimetable().size() + " entries, playing " + kept.size());

            if (!kept.isEmpty())
            {
                stored.setTimetable(new ArrayList<>(kept));
                stored.setTimetableSequential(true);

                lines.println(played(stored) + " " + whereTheyAre(stored, theirs));

                entries += kept.size();
            }

            Layout railway = fresh(model, text);

            List<Locomotive> trains = trainsOn(railway, sampled, 3);

            Random seeded = new Random(211);

            List<TimetablePath> recorded = new ArrayList<>();

            // Each train stays where its legs left it for the next train's offers, as it will on the run
            Map<Locomotive, Point> starts = new java.util.LinkedHashMap<>();

            for (Locomotive loc : trains.subList(0, Math.min(3, trains.size())))
            {
                starts.put(loc, railway.getLocomotiveLocation(loc));

                for (int leg = 0; leg < 2; leg++)
                {
                    Map<String, List<Edge>> offers = offersTo(railway, loc, loc.getName() + "table" + leg);

                    if (offers.isEmpty()) break;

                    List<Edge> path = new ArrayList<>(offers.values()).get(seeded.nextInt(offers.size()));

                    recorded.add(new TimetablePath(loc, path, 0));

                    lines.println("recorded " + loc.getName() + " " + namesOf(path));

                    standOn(railway, loc, path.get(path.size() - 1).getEnd());
                }
            }

            // All back where they started, for the run
            for (Locomotive loc : starts.keySet())
            {
                Point at = railway.getLocomotiveLocation(loc);

                if (at != null) at.setLocomotive(null);
            }

            for (Map.Entry<Locomotive, Point> start : starts.entrySet())
            {
                if (start.getValue() != null) start.getValue().setLocomotive(start.getKey());
            }

            if (!recorded.isEmpty())
            {
                railway.setTimetable(recorded);
                railway.setTimetableSequential(true);

                String outcome = played(railway);

                lines.println(outcome + " " + whereTheyAre(railway, trains));

                entries += recorded.size();

                if (outcome.startsWith("returned true")) finished++;
            }
        }

        counts.put("timetable entries played", entries);
        counts.put("recorded timetables that ran to the end", finished);
    }

    /** The first line of `home-plans.txt`, read by `docs/tools/old-setups.py` to know whether moves can be compared. */
    public static final String PLANNER = "planner budget: ";

    /** Whether this build's Return Home planner has a wall clock budget, as every build before OB-413 does. */
    private static boolean plannerReadsAClock()
    {
        for (java.lang.reflect.Field field : HomeStaging.class.getDeclaredFields())
        {
            if (field.getName().equals("clock")) return true;
        }

        return false;
    }

    /** Return Home as the setup has it, then for homes the seed chooses among where each train can be sent. */
    private static void writeHomes(MarklinControlStation model, String text, List<Locomotive> sampled, File to,
        Map<String, Integer> counts) throws Exception
    {
        int ready = 0;
        int home = 0;

        try (PrintWriter lines = writer(to); PrintWriter plans = writer(new File(to.getParentFile(), "home-plans.txt")))
        {
            plans.println(PLANNER + (plannerReadsAClock() ? "wall clock" : "route work"));

            for (int round = 0; round < 4; round++)
            {
                Layout railway = fresh(model, text);

                List<Locomotive> trains = trainsOn(railway, sampled, 3);

                // The first round is the setup's own homes; the rest are chosen
                if (round > 0) chooseHomes(railway, trains, new Random(300 + round), lines);

                seed("plan" + round);

                // What the plan reads besides the squares
                lines.println("sensors on " + sensorsOn(railway));
                lines.println("tails " + tailsOf(railway));

                HomeStaging.Plan plan = railway.loadReturnToHomeTimetable();

                lines.println("round " + round + " " + plan.getOutcome() + " audit "
                    + HomeStaging.snapshot(railway).auditAgainstRuntime());

                plans.println("round " + round + " " + plan.getMoves().size() + " moves");

                for (HomeStaging.Move move : plan.getMoves())
                {
                    plans.println("  " + move.getLocomotive().getName() + " " + namesOf(move.getPath()));
                }

                if (!plan.isPossible() || plan.getMoves().isEmpty()) continue;

                ready++;

                lines.println("  " + played(railway) + " " + whereTheyAre(railway, trains));

                if (railway.triageReturnToHome() == HomeStaging.Outcome.ALREADY_HOME) home++;
            }
        }

        counts.put("plans ready", ready);
        counts.put("plans that brought every train home", home);
    }

    private static List<String> sensorsOn(Layout railway)
    {
        TreeSet<String> on = new TreeSet<>();

        for (Point p : railway.getPoints())
        {
            if (p.getS88() != null && railway.isFeedbackOccupied(p.getS88())) on.add(p.getS88());
        }

        return new ArrayList<>(on);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TreeSet<String>> tailsOf(Layout railway)
    {
        Map<String, TreeSet<String>> tails = new TreeMap<>();

        for (Map.Entry<Locomotive, Object[]> tail : railway.tailsOfEachStandingTrain().entrySet())
        {
            TreeSet<String> edges = new TreeSet<>();

            for (Edge edge : ((Map<Edge, Locomotive>) tail.getValue()[0]).keySet()) edges.add(edge.getName());

            tails.put(tail.getKey().getName(), edges);
        }

        return tails;
    }

    private static void chooseHomes(Layout railway, List<Locomotive> trains, Random seeded, PrintWriter lines)
        throws Exception
    {
        railway.clearHomeLocomotives();

        for (Locomotive loc : trains.subList(0, Math.min(4, trains.size())))
        {
            Map<String, Point> ends = new TreeMap<>();

            for (List<Edge> path : offersTo(railway, loc, loc.getName() + "homes").values())
            {
                Point end = path.get(path.size() - 1).getEnd();

                if (end.getHomeLoc() == null) ends.put(end.getName(), end);
            }

            if (ends.isEmpty()) continue;

            Point chosen = new ArrayList<>(ends.values()).get(seeded.nextInt(ends.size()));

            railway.setHomeLocomotive(chosen.getName(), loc.getName());

            lines.println("home of " + loc.getName() + " is " + chosen.getName());
        }
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

        // The trains the setup placed, sampled or not, as they are the ones with somewhere to go; one on a bare fixture
        List<Locomotive> standing = trainsOn(railway, sampled, 1);

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
