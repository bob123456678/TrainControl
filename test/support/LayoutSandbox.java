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
 * A throwaway copy of the fixture layout, with the window pointed at it.
 *
 * OB-111. A full battery left `cs2_sample_layout` - Adam's real railway - showing as modified in
 * `git status` after every run. The content was unchanged, differing only in line endings, so
 * battery.sh's fingerprint correctly stayed quiet; what it cost is that on 2026-08-25 the churn masked
 * a change he had made himself, and telling the two apart meant reading the JSON semantically.
 *
 * **The cause is not a test that names his folder - none of them do.** Three classes construct the real
 * window, and the window opens whatever the saved layout preference names. On his machine that is his
 * railway. The fixture separation of b87c4f05 moved the suite onto `test/test_layout` and could not reach
 * this, because the path is not written in the suite at all: it is in his preferences.
 *
 * So the preference is what this changes, and it changes it to a COPY rather than to `test/test_layout`
 * itself - the window writes as well as reads, and the fixture is tracked, so pointing it at the real
 * fixture would move the same problem one folder over and put it in the repository.
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
     * The same, over a named folder, for a test that needs a particular railway.
     *
     * The operator's own layout is the case this exists for: a test that wants to reason about HIS
     * stations has to read them, and reading them in place is how that folder gets written to. It is
     * COPIED here, exactly as the fixture is, so the original is only ever read - and the copy is what
     * the preference points at, so nothing downstream can reach the original even by accident.
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

        // ANYTHING A KILLED JVM LEFT BEHIND IS PUT BACK FIRST.
        //
        // `close()` restores the preference and its javadoc says why.  It runs only if the JVM lives,
        // and test JVMs do get killed - two were on 2026-09-11, one parked on a modal dialog and one on
        // an untimed feedback wait, and both had to be stopped by hand.  The preference was left naming
        // a sandbox, so the next run built its configuration from a single-switch fixture and two
        // battery classes failed with no defect behind them.  Worse, the key is the same one the
        // APPLICATION reads, so Adam's own window would have opened that folder too.
        repairALeakedPreference(prefs);

        String was = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        // WRITTEN BEFORE the preference is changed, so a kill between the two lines leaves a marker
        // that says the truth rather than one that does not exist.
        rememberForRepair(was);

        prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, to.toFile().getAbsolutePath());

        // And for an ordinary exit that skips close() - an exception in a @BeforeClass, a System.exit
        // from a window - the hook is enough.  It cannot help against a forced kill, which is what the
        // marker above is for.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> putBack(prefs, was)));

        // AND NOBODY IS WATCHING, which the window has no other way to know.
        //
        // Adam, 2026-09-05: "in the tests you're running, at startup there is a prompt about
        // initializing a new track diagram.  I have been clicking on those windows."  A modal dialog
        // in an automated run does not fail it - it stops it, until somebody notices.
        //
        // Set here rather than in each test class because this is the one thing every test that
        // stands a window up already calls, and a flag that has to be remembered per class is a flag
        // that will be forgotten by the next class written.
        TrainControlUI.setUnattended(true);

        return new LayoutSandbox(to, was);
    }

    /**
     * Puts the preference back.
     *
     * Back to what it WAS, including back to unset - because a test that leaves a path behind has
     * changed which layout the application opens the next time the operator starts it, which is worse
     * than the churn this class exists to remove.
     */
    public void close()
    {
        // Put back first, so that an exception below cannot leave the application believing nobody is
        // there - which would silently answer a real operator's dialogs on the next launch of a
        // session that shares this JVM.
        TrainControlUI.setUnattended(false);

        putBack(prefs, was);

        forgetTheRepairMarker();
    }

    /**
     * Puts the preference back to what it was, including back to unset.
     *
     * One place, because three things do it now: `close`, the shutdown hook, and the repair that runs
     * when a previous JVM was killed before either could.
     *
     * @param prefs the preference node
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
     * Where the previous value is written down, so a killed JVM can be cleaned up after.
     *
     * In the temp directory rather than the repository: it is machine state, it is about a preference
     * that is machine state, and a file in the tree would be committed by somebody eventually.
     */
    private static java.io.File repairMarker()
    {
        return new java.io.File(System.getProperty("java.io.tmpdir"), "tc-sandbox-layout-pref.marker");
    }

    /**
     * Writes down what the preference was, for a JVM that does not get to run `close`.
     *
     * A marker that already exists is NOT overwritten: the first one is the operator's real value, and
     * a second sandbox opening inside a killed one's mess would otherwise record the sandbox path as
     * the thing to restore.
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
     * Puts the preference back if a previous JVM was killed before it could.
     *
     * The marker is only there when a sandbox took the preference and never gave it back, so its
     * presence IS the diagnosis.  Read and acted on before this sandbox records its own value, or the
     * repair would record the leaked sandbox path as the operator's.
     *
     * @param prefs the preference node
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
     * Parses through this parser and WIRES what came back, which is the pair `syncLayouts` does.
     *
     * Written to be a one-word change at each call site - `parser.parseLayout(new LinkedList<>())`
     * becomes `LayoutSandbox.wired(model, parser)` - so each class keeps its own folder logic and the
     * only thing that changes is that the pages come back attached to something.
     *
     * Parsing alone leaves every switch and signal with a null accessory. `TileGraph` reports
     * `errorTileHasNoAddress` for each and refuses to trace through it, so this railway reduced to 18
     * edges and built to 5, with 51 of 59 points isolated. See `wiredPages` for both columns.
     *
     * `wireComponents` is the application's own loop, made reachable rather than copied here.
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
     * **Measured 2026-09-08, and this is the difference between a railway and a skeleton.** Every
     * sandbox test built its pages by constructing a fresh `CS2File` and calling
     * `parseLayout(new LinkedList<MarklinAccessory>())`. That looks harmless - the same files, read the
     * same way - and it is not, because parsing is not what attaches a tile to its accessory.
     * `MarklinControlStation.syncLayouts` does that, in a loop AFTER the parse: it creates an accessory
     * from each tile's own address when the model has none, and calls `setAccessory` on the component.
     * A second parser bypasses that loop, so every switch and signal comes back with a null accessory.
     *
     * `TileGraph` then reports `errorTileHasNoAddress` for each of them and refuses to trace through
     * them, which breaks the chains and cuts the railway to pieces:
     *
     * | | re-parsed | the model's own |
     * |---|---|---|
     * | switches and signals with an accessory | 0 of 222 | 221 of 222 |
     * | reduced edges | 18 | 128 |
     * | built points / edges | 59 / 5 | 96 / 149 |
     * | isolated points | 51 | 3 |
     *
     * A railway with about ninety connections arrived as FIVE EDGES. Tests standing on that were
     * asserting against a graph where nothing is connected to anything - and passing, because an
     * assertion about a square that has no edges is usually an assertion about null.
     *
     * Nothing was missing from the fixture folder; the recipe was wrong.
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
     * @return where the copy lives, for a test that wants to look at what was written
     */
    public File getFolder()
    {
        return folder.toFile();
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
