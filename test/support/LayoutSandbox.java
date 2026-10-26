package support;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.prefs.Preferences;
import org.traincontrol.gui.TrainControlUI;

/**
 * A throwaway copy of the fixture layout, with the window pointed at it (OB-111).
 *
 * A full battery left `cs2_sample_layout`, Adam's real railway, modified in `git status` after every run, because three
 * classes construct the real window and the window opens whatever the saved layout preference names.  So the
 * preference is what this changes, to a COPY rather than to `test/test_layout`: the window writes as well as reads,
 * and the fixture is tracked.
 *
 * Usage is two lines, in @BeforeClass and @AfterClass:
 *
 *     sandbox = LayoutSandbox.open();      // and construct the window AFTER this
 *     sandbox.close();
 *
 * @author Adam
 */
public final class LayoutSandbox
{
    private final Preferences prefs = TrainControlUI.getPrefs();

    private final String was;

    private final Path folder;

    private LayoutSandbox(Path folder, String was)
    {
        this.folder = folder;
        this.was = was;
    }

    /**
     * Copies the fixture layout somewhere temporary and points the window's layout preference at it.
     *
     * @return the sandbox, to be closed when the class is done with it
     * @throws IOException if the fixture cannot be copied
     */
    public static LayoutSandbox open() throws IOException
    {
        return open(new File("test/test_layout"));
    }

    /**
     * The same, over a named folder, for a test that needs a particular railway, such as the operator's own: it is
     * COPIED, as the fixture is, so the original is only ever read.
     *
     * @param fixture the folder to copy; a missing one gives an empty sandbox, as with the fixture
     * @return the sandbox, to be closed when the class is done with it
     * @throws IOException if the folder cannot be copied
     */
    public static LayoutSandbox open(File fixture) throws IOException
    {

        Path to = Files.createTempDirectory("tc-sandbox-layout");

        if (fixture.isDirectory()) copy(fixture.toPath(), to);

        Preferences prefs = TrainControlUI.getPrefs();

        // ANYTHING A KILLED JVM LEFT BEHIND IS PUT BACK FIRST: `close()` runs only if the JVM lives, and a killed one
        // left the preference naming a sandbox, which the APPLICATION reads too, so Adam's own window would open that
        // folder.
        repairALeakedPreference(prefs);

        String was = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        // WRITTEN BEFORE the preference is changed, so a kill between the two lines leaves a true marker.
        rememberForRepair(was);

        prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, to.toFile().getAbsolutePath());

        // For an ordinary exit that skips close() the hook is enough; the marker above covers a forced kill.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> putBack(prefs, was)));

        // AND NOBODY IS WATCHING, which the window has no other way to know.  Adam: "in the tests you're running, at
        // startup there is a prompt about initializing a new track diagram.  I have been clicking on those windows."  A
        // modal dialog in an automated run stops it.  Set here because every test that stands a window up calls this.
        TrainControlUI.setUnattended(true);

        return new LayoutSandbox(to, was);
    }

    /**
     * Puts the preference back to what it WAS, including unset: a test that leaves a path behind changes which layout
     * the application opens next time.
     */
    public void close()
    {
        // Put back first, so an exception below cannot leave the application believing nobody is there.
        TrainControlUI.setUnattended(false);

        putBack(prefs, was);

        forgetTheRepairMarker();
    }

    /**
     * Puts the preference back to what it was, including back to unset: one place for `close`, the shutdown hook and
     * the repair after a killed JVM.
     *
     * @param was the value to restore, empty or null for "there was none"
     */
    private static void putBack(Preferences prefs, String was)
    {
        if (was == null || was.isEmpty())
        {
            prefs.remove(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF);
        }
        else
        {
            prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, was);
        }
    }

    /**
     * Where the previous value is written down, so a killed JVM can be cleaned up after: in the temp directory, since
     * it is machine state and a file in the tree would be committed by somebody.
     */
    private static java.io.File repairMarker()
    {
        return new java.io.File(System.getProperty("java.io.tmpdir"), "tc-sandbox-layout-pref.marker");
    }

    /**
     * Writes down what the preference was, for a JVM that does not get to run `close`.  A marker that already exists is
     * NOT overwritten: the first holds the operator's real value.
     *
     * @param was the value before this sandbox took the preference
     */
    private static void rememberForRepair(String was)
    {
        try
        {
            if (repairMarker().exists()) return;

            java.nio.file.Files.write(repairMarker().toPath(),
                (was == null ? "" : was).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (IOException cannotWrite)
        {
            // A sandbox that cannot write its marker still works; it just cannot be cleaned up after
            // a kill, which is where this started.
        }
    }

    /**
     * Removes the marker, once the preference is genuinely back.
     */
    private static void forgetTheRepairMarker()
    {
        try
        {
            java.nio.file.Files.deleteIfExists(repairMarker().toPath());
        }
        catch (IOException cannotDelete)
        {
        }
    }

    /**
     * Puts the preference back if a previous JVM was killed before it could: the marker's presence IS the diagnosis.
     * Acted on before this sandbox records its own value, or the repair would record the leaked path as the operator's.
     */
    private static void repairALeakedPreference(Preferences prefs)
    {
        try
        {
            if (!repairMarker().exists()) return;

            String was = new String(java.nio.file.Files.readAllBytes(repairMarker().toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            String now = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            // Only if the preference is still pointing at a sandbox.  If somebody has since set it
            // deliberately, theirs wins and the marker is simply stale.
            if (now.contains("tc-sandbox-layout"))
            {
                putBack(prefs, was);

                System.out.println("LayoutSandbox: put the layout preference back to \"" + was
                    + "\" - a previous run was killed before it could");
            }

            forgetTheRepairMarker();
        }
        catch (IOException cannotRead)
        {
        }
    }

    /**
     * Parses through this parser and WIRES what came back, which is the pair `syncLayouts` does.  A one-word change at
     * each call site: `parser.parseLayout(new LinkedList<>())` becomes `LayoutSandbox.wired(model, parser)`.
     *
     * Parsing alone leaves every switch and signal with a null accessory, so `TileGraph` refuses to trace through it
     * (see `wiredPages`).  `wireComponents` is the application's own loop, made reachable rather than copied here.
     *
     * @param model the control station whose accessory, feedback and route databases to wire against
     * @param parser the parser, already pointed at a layout folder
     * @return the pages, wired
     * @throws Exception if the folder cannot be read
     */
    public static java.util.List<org.traincontrol.base.LayoutDiagram> wired(
        org.traincontrol.marklin.MarklinControlStation model,
        org.traincontrol.marklin.file.CS2File parser) throws Exception
    {
        java.util.List<org.traincontrol.base.LayoutDiagram> pages = parser.parseLayout(
            new java.util.LinkedList<org.traincontrol.marklin.MarklinAccessory>());

        for (org.traincontrol.base.LayoutDiagram page : pages)
        {
            model.wireComponents(page, null);
        }

        return pages;
    }

    /**
     * The pages the MODEL parsed, wired to their accessories - not a second parse of the same files.
     *
     * Parsing does not attach a tile to its accessory; `MarklinControlStation.syncLayouts` does that in a loop AFTER
     * the parse.  A second `CS2File` parser bypasses it, so every switch and signal has a null accessory, `TileGraph`
     * reports `errorTileHasNoAddress` and refuses to trace through, and the railway arrives in pieces:
     *
     * | | re-parsed | the model's own |
     * |---|---|---|
     * | switches and signals with an accessory | 0 of 222 | 221 of 222 |
     * | reduced edges | 18 | 128 |
     * | built points / edges | 59 / 5 | 96 / 149 |
     * | isolated points | 51 | 3 |
     *
     * Tests standing on that asserted against a graph where nothing is connected to anything, and passed.
     *
     * @param model the control station, already built with this sandbox open
     * @return its pages, in its own order
     */
    public static java.util.List<org.traincontrol.base.LayoutDiagram> wiredPages(
        org.traincontrol.marklin.MarklinControlStation model)
    {
        java.util.List<org.traincontrol.base.LayoutDiagram> pages = new java.util.ArrayList<>();

        if (model == null) return pages;

        for (String name : model.getLayoutList())
        {
            org.traincontrol.base.LayoutDiagram page = model.getLayout(name);

            if (page != null) pages.add(page);
        }

        return pages;
    }

    /**
     * Where the copy lives, for a test that wants to look at what was written.
     */
    public File getFolder()
    {
        return folder.toFile();
    }

    /**
     * Turns simulation on in this copy's autonomy configurations, for a test that runs trains (FR-136).  `simulate` is
     * a setting in the operator's own file, so a refreshed snapshot carries whatever his last session left; the
     * checked-in fixture is never written, and a file that already has it is left byte for byte.
     *
     * @return this sandbox, so the call can sit on the line that opens it
     * @throws IOException if a configuration cannot be read or written
     */
    public LayoutSandbox simulated() throws IOException
    {
        for (Path file : configurations())
        {
            org.json.JSONObject configuration = readJson(file);

            org.json.JSONObject globals = configuration.optJSONObject("globals");

            if (globals != null && globals.optBoolean("simulate", false)) continue;

            if (globals == null) configuration.put("globals", globals = new org.json.JSONObject());

            globals.put("simulate", true);

            writeJson(file, configuration);
        }

        return this;
    }

    /**
     * Takes every train off this copy's autonomy configurations, every home with them, and empties the timetable
     * (FR-136).  Where his trains stand, which are homed where, and the last run's timetable are working state, not the
     * railway (`test/layouts/live-snapshot/README.md`, "Not invariants"); a test that needs a train standing calls this
     * and then `standing` for each one it names.
     *
     * @return this sandbox
     * @throws IOException if a configuration cannot be read or written
     */
    public LayoutSandbox withNoTrains() throws IOException
    {
        for (Path file : configurations())
        {
            org.json.JSONObject configuration = readJson(file);

            org.json.JSONObject points = configuration.optJSONObject("points");

            if (points != null)
            {
                for (String key : points.keySet())
                {
                    org.json.JSONObject point = points.optJSONObject(key);

                    if (point == null) continue;

                    for (String property : new String[] {"loc", "facing", "arrivedFrom", "arrivedAlong", "home",
                        "homeFacing"})
                    {
                        point.remove(property);
                    }
                }
            }

            org.json.JSONObject globals = configuration.optJSONObject("globals");

            if (globals != null && globals.has("timetable")) globals.put("timetable", new org.json.JSONArray());

            writeJson(file, configuration);
        }

        return this;
    }

    /**
     * Stands a named train on a square of this copy's autonomy configurations.
     *
     * @param square the square, as the configuration keys it
     * @param locomotive the train's name, which the database must hold or the load leaves it out (OB-349)
     * @param facing the way it faces, N, E, S or W
     * @param arrivedFrom the side it came in from, or null for not known
     * @return this sandbox
     * @throws IOException if a configuration cannot be read or written
     */
    public LayoutSandbox standing(org.traincontrol.automationui.TileGraph.TileKey square, String locomotive, String facing,
        String arrivedFrom) throws IOException
    {
        for (Path file : configurations())
        {
            org.json.JSONObject configuration = readJson(file);

            org.json.JSONObject points = configuration.optJSONObject("points");

            if (points == null) configuration.put("points", points = new org.json.JSONObject());

            org.json.JSONObject point = points.optJSONObject(square.toString());

            if (point == null) points.put(square.toString(), point = new org.json.JSONObject());

            point.put("loc", new org.json.JSONObject().put("name", locomotive));
            point.put("facing", facing);

            if (arrivedFrom == null) point.remove("arrivedFrom");
            else point.put("arrivedFrom", arrivedFrom);

            writeJson(file, configuration);
        }

        return this;
    }

    /**
     * Makes this copy's timetable one entry: a train's run over the named points, in order.
     *
     * @param locomotive the train's name
     * @param points the Points the run passes, as the running railway names them, first to last
     * @return this sandbox
     * @throws IOException if a configuration cannot be read or written
     */
    public LayoutSandbox withATimetableOf(String locomotive, String... points) throws IOException
    {
        org.json.JSONArray path = new org.json.JSONArray();

        for (int i = 1; i < points.length; i++)
        {
            path.put(new org.json.JSONObject().put("start", points[i - 1]).put("end", points[i]));
        }

        org.json.JSONObject entry = new org.json.JSONObject().put("executionTime", 0L).put("secondsToNext", 0)
            .put("loc", locomotive).put("path", path);

        for (Path file : configurations())
        {
            org.json.JSONObject configuration = readJson(file);

            org.json.JSONObject globals = configuration.optJSONObject("globals");

            if (globals == null) configuration.put("globals", globals = new org.json.JSONObject());

            globals.put("timetable", new org.json.JSONArray().put(entry));

            writeJson(file, configuration);
        }

        return this;
    }

    /**
     * The autonomy configurations in this copy, one file each; none when it has no setup.
     */
    private java.util.List<Path> configurations() throws IOException
    {
        java.util.List<Path> out = new java.util.ArrayList<>();

        Path autonomy = folder.resolve("config").resolve("autonomy");

        if (!Files.isDirectory(autonomy)) return out;

        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(autonomy, "configuration-*.json"))
        {
            for (Path file : files) out.add(file);
        }

        return out;
    }

    private static org.json.JSONObject readJson(Path file) throws IOException
    {
        return new org.json.JSONObject(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeJson(Path file, org.json.JSONObject json) throws IOException
    {
        Files.write(file, json.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void copy(final Path from, final Path to) throws IOException
    {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException
            {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                Files.copy(file, to.resolve(from.relativize(file).toString()),
                    StandardCopyOption.REPLACE_EXISTING);

                return FileVisitResult.CONTINUE;
            }
        });
    }
}
