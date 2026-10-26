package regression;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * Adam's own railway, read and checked, and never written to.  Every other test runs against `test/test_layout`, a
 * frozen copy, so the suite otherwise knows only what was true on the day the copy was taken.
 *
 * What this asserts is deliberately narrow: things that must be true of ANY layout (internal consistency: the setup
 * names track the diagram has, the numbering agrees with itself, nothing was written).  It is not a ratchet on his
 * data, because a test that counted things would go red every time he worked on the railway.
 *
 * The `@AfterClass` check is about this suite, not the railway: two test classes start the real window, which loads
 * whatever the saved UI state names, and the battery once left it modified on every run unnoticed.  It runs LAST,
 * after every `@Test`, because a fingerprint taken as the first `@Test` could not see a write made by a later one.
 *
 * Failed, not skipped, when the folder is not there: `cs2_sample_layout` is tracked, so the battery always has it
 * (FR-136).
 *
 * @author Adam
 */
public class testTheGoldenLayoutHoldsTogether
{
    /** Adam's live layout, as distinct from the frozen fixture every other test uses. */
    private static final File GOLDEN = new File("cs2_sample_layout");

    private static MarklinControlStation model;

    private static List<LayoutDiagram> pages;

    private static AutonomyCompanionStore store;

    /** What every file under the layout folder hashed to before anything read it. */
    private static Map<String, String> before;

    /**
     * A COPY of the golden layout, which is what everything below actually reads: the real folder is the one in this
     * repository that is not recoverable, and a fingerprint is an alarm rather than a guard.  `init` reads the layout
     * preference too, so the sandbox must be open BEFORE it and not merely before the window (OB-111).  The copy is
     * byte-for-byte his railway, so the questions are answered by the same data.
     */
    private static support.LayoutSandbox sandbox;

    /** The copy's folder - GOLDEN is only ever read to make it. */
    private static File working;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (!GOLDEN.isDirectory())
        {
            throw new AssertionError("no golden layout at " + GOLDEN.getAbsolutePath());
        }

        before = fingerprint(GOLDEN);

        assertFalse(before.isEmpty(), "the golden layout folder is empty, so nothing below is a check");

        // COPIED FIRST, AND BEFORE THE MODEL (OB-111): see the sandbox field.
        sandbox = support.LayoutSandbox.open(GOLDEN);

        working = sandbox.getFolder();

        assertFalse(fingerprint(working).isEmpty(),
            "the copy is empty, so every test below would read nothing and pass");

        model = support.TestStation.simulatedInDebug();

        String path = "file:///" + working.getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        pages = support.LayoutSandbox.wired(model, parser);

        store = new AutonomyCompanionStore(working);

        store.setPageIds(pageIds());
        store.load();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null) model.stop();

        // alwaysRun: the sandbox goes back whatever happened above, because a preference left pointing at a temporary
        // folder changes which layout the application opens next time.
        if (sandbox != null) sandbox.close();
    }

    /**
     * Reading Adam's railway does not write to it.  A `@Test` that fingerprinted first compared against itself before
     * its siblings ran; declared `@AfterClass` it runs once, after every `@Test`, so `after` sees whatever any of them
     * did.
     *
     * The scope: the suite runs one JVM per class, so a write by ANOTHER class happened before `before` was taken and
     * this cannot see it.  That protection lives in `docs/tools/battery.sh`, which fingerprints the folder around the
     * whole run (and `ant test` does not use it).  Every file by content, not timestamp: a file rewritten with
     * identical bytes is not a problem.
     *
     * MUTATION this catches: have any `@Test` above call `store.save()` once.
     */
    @AfterClass(alwaysRun = true)
    public void testNothingWroteToTheGoldenLayout() throws Exception
    {
        // NOTHING TO COMPARE AGAINST IS NOT A FAILURE: `alwaysRun` means this runs after the `SkipException` the set-up
        // throws when there is no golden layout, and `before` is null there.
        if (before == null) return;

        Map<String, String> after = fingerprint(GOLDEN);

        List<String> changed = new ArrayList<>();

        for (Map.Entry<String, String> was : before.entrySet())
        {
            String now = after.get(was.getKey());

            if (now == null) changed.add(was.getKey() + " (gone)");
            else if (!now.equals(was.getValue())) changed.add(was.getKey() + " (rewritten)");
        }

        for (String name : after.keySet())
        {
            if (!before.containsKey(name)) changed.add(name + " (new)");
        }

        assertTrue(changed.isEmpty(),
            "something wrote to Adam's own railway while the tests were running.  This is his "
            + "accumulated setup and it is not recoverable: " + changed);
    }

    /**
     * What the reversal-length guard can actually judge on the real railway.  `reversalsWithoutLength` is armed by
     * `measuresAnyTrack()`: ONE measured square anywhere turns the notice on for every square trains turn round at.
     * His railway had six measured squares, all on one test page, against 71 points, so the notice was armed with
     * nothing to say about almost every reversal (FR-136 1b).
     *
     * This measures rather than demands: how much he has measured changes as he works.  What it refuses is armed and
     * unable to answer anything.  The count is put in the message either way.
     */
    @Test
    public void testWhatTheReversalGuardCanJudgeHere()
    {
        // THE ARMING ANSWER ON A STORE MADE HERE, because the railway's own data cannot show the state this is about:
        // once any square of it is measured, "armed" is the right answer whatever the question does.  Nothing measured
        // must not arm the notice, and one measured square must, and taking it off must disarm it.
        AutonomyCompanionStore fresh = new AutonomyCompanionStore(null);

        TileKey somewhere = new TileKey("ZZ", 1, 1);

        assertFalse(fresh.measuresAnyTrack(),
            "a store with no length on any square arms the reversal-length notice - a warning that cannot answer (TCX-B3)");

        assertTrue(fresh.tilesWithALength().isEmpty(), "precondition: a new store lists a measured square");

        fresh.setTileLength(somewhere, 3);

        assertTrue(fresh.measuresAnyTrack(), "one measured square does not arm the reversal-length notice");

        assertEquals(fresh.tilesWithALength(), Arrays.asList(somewhere), "the measured square is not listed");

        fresh.setTileLength(somewhere, 0);

        assertFalse(fresh.measuresAnyTrack(), "taking the only length off does not disarm the notice");

        int measured = 0;

        for (TileKey tile : store.getNamedTiles())
        {
            if (store.getTileLength(tile) > 0) measured++;
        }

        // A length belongs to any square, and the plain track between two sensors is what somebody measures, so the
        // store is asked directly rather than inferred from the count above.
        final boolean armed = store.measuresAnyTrack();

        if (!armed)
        {
            assertEquals(measured, 0,
                "the store says nothing is measured and " + measured + " named squares carry a "
                + "length, so the arming question and the data disagree");
        }

        // THE ARMING ANSWER AGAINST THE DATA, not against itself: the squares that carry a length are listed by
        // `tilesWithALength`, which reads the lengths and not the arming question.  Armed with none listed is the state
        // worth checking, and so is a listed length with the notice off.
        assertEquals(armed, !store.tilesWithALength().isEmpty(),
            "the reversal-length notice is armed by one measured square anywhere and then covers "
            + "every square trains turn round at.  Armed with nothing measured is the state TCX-B3 "
            + "names: a warning that cannot answer the question it raises.  The store says "
            + (armed ? "a square is measured" : "none is") + "; it lists " + store.tilesWithALength().size()
            + " squares with a length, and " + measured + " of those named carry one");
    }
    /**
     * Every square the setup names is a square the diagram still draws.  The one direction of difference that is always
     * a defect: a setting for track that does not exist means the diagram failed to read something real, or the setup
     * is keyed to a page that has moved.  Squares on excluded pages are still checked, because they are still drawn.
     */
    @Test
    public void testEverySquareTheSetupNamesIsOnTheDiagram()
    {
        Map<String, LayoutDiagram> byName = new LinkedHashMap<>();

        for (LayoutDiagram page : pages) byName.put(page.getName(), page);

        List<String> missing = new ArrayList<>();

        for (TileKey named : store.getNamedTiles())
        {
            LayoutDiagram page = byName.get(named.getPage());

            if (page == null)
            {
                missing.add(named + " - no page called that");

                continue;
            }

            if (named.getX() >= page.getSx() || named.getY() >= page.getSy()
                || page.getComponent(named.getX(), named.getY()) == null)
            {
                missing.add(named + " (\"" + store.getPointName(named) + "\") - nothing drawn there");
            }
        }

        // A FLOOR, so an empty walk is not a pass: an empty loop satisfies "the collection of wrong things is empty".
        assertFalse(store.getNamedTiles().isEmpty(),
            "the setup names no squares at all, so this walked nothing");

        assertTrue(missing.isEmpty(),
            "the setup names squares the diagram does not draw.  Either a page failed to read, or "
            + "settings are keyed to a page that has moved under them: " + missing);
    }

    /**
     * The setup's record of the page numbering agrees with the index on disk.  The setup is keyed by page ID in the
     * file and by page NAME in memory, and `"pages"` translates; when it disagrees with `gleisbild.cs2` every key
     * resolves to the wrong page, silently (MT-135).  A page the record knows and the index does not is normal
     * (FR-018); the pairs they both have must say the same thing.
     */
    @Test
    public void testTheSetupAndTheIndexAgreeAboutTheNumbering()
    {
        Map<String, Integer> index = LayoutDiagram.readLayoutIndexIds(GOLDEN.getAbsolutePath());

        assertFalse(index.isEmpty(),
            "the layout index could not be read, so this test is comparing against nothing");

        // Asked of the store's own detection (`pageIdConflicts`, populated at load) rather than recomputed, which would
        // be a second opinion about the same question.
        Map<String, String> conflicts = store.getPageIdConflicts();

        assertTrue(conflicts.isEmpty(),
            "the autonomy setup and the layout index disagree about which page is which.  Every "
            + "setting is keyed by that number, so this is a page's settings attached to another "
            + "page's track, and it is silent - recorded name to name that id carries now: "
            + conflicts);
    }

    /** No two pages claim one id; silent everywhere else, so asked of the real file. */
    @Test
    public void testNoTwoPagesShareAnId()
    {
        Map<Integer, String> byId = new TreeMap<>();

        List<String> clashes = new ArrayList<>();

        for (Map.Entry<String, Integer> page
            : LayoutDiagram.readLayoutIndexIds(GOLDEN.getAbsolutePath()).entrySet())
        {
            String already = byId.put(page.getValue(), page.getKey());

            if (already != null) clashes.add(page.getValue() + " is both " + already + " and "
                + page.getKey());
        }

        // A FLOOR, so an empty walk is not a pass.
        assertFalse(LayoutDiagram.readLayoutIndexIds(GOLDEN.getAbsolutePath()).isEmpty(),
            "the layout index names no pages, so this walked nothing");

        assertTrue(clashes.isEmpty(), "two pages answer to one id: " + clashes);
    }

    /**
     * Every square a configuration says something about is a square the diagram draws.  Configurations key by page NAME
     * on disk and are never translated, so they survive a rename only because the application rekeys them in memory
     * first; nothing in the file format states that dependency.
     */
    @Test
    public void testEverySquareAConfigurationNamesIsOnTheDiagram()
    {
        Map<String, LayoutDiagram> byName = new LinkedHashMap<>();

        for (LayoutDiagram page : pages) byName.put(page.getName(), page);

        List<String> missing = new ArrayList<>();

        for (String name : store.getConfigurationNames())
        {
            JSONObject configuration = store.getConfiguration(name);

            if (configuration == null || !configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");

            for (String key : points.keySet())
            {
                int colon = key.lastIndexOf(':');

                if (colon < 0) continue;

                String page = key.substring(0, colon);

                String[] xy = key.substring(colon + 1).split(",");

                if (xy.length != 2) continue;

                LayoutDiagram drawn = byName.get(page);

                if (drawn == null)
                {
                    missing.add(name + " / " + key + " - no page called that");

                    continue;
                }

                try
                {
                    int x = Integer.parseInt(xy[0].trim());
                    int y = Integer.parseInt(xy[1].trim());

                    if (x >= drawn.getSx() || y >= drawn.getSy() || drawn.getComponent(x, y) == null)
                    {
                        missing.add(name + " / " + key + " - nothing drawn there");
                    }
                }
                catch (NumberFormatException notASquare)
                {
                    // Not a square key at all - a configuration may hold other things.
                }
            }
        }

        // A FLOOR, so an empty walk is not a pass.
        assertFalse(store.getConfigurationNames().isEmpty(),
            "the setup holds no configurations at all, so this walked nothing and would pass on a "
            + "file it could not read");

        assertTrue(missing.isEmpty(),
            "a configuration holds settings for squares the diagram does not draw.  Placements, "
            + "homes, facings and lengths live there, and they are keyed by page NAME rather than by "
            + "id, so a rename the application did not perform orphans them: " + missing);
    }

    /**
     * Every page in the layout index is a page that actually loaded.  `CS2File` skips a page whose file will not parse
     * or is not there (FR-018), which is how a page can quietly stop being part of the railway, a real state on
     * OneDrive.
     */
    @Test
    public void testEveryPageInTheIndexLoaded()
    {
        List<String> loaded = new ArrayList<>();

        for (LayoutDiagram page : pages) loaded.add(page.getName());

        List<String> absent = new ArrayList<>();

        for (String named : LayoutDiagram.readLayoutIndexIds(GOLDEN.getAbsolutePath()).keySet())
        {
            if (!loaded.contains(named)) absent.add(named);
        }

        // A FLOOR, so an empty walk is not a pass.
        assertFalse(pages.isEmpty(), "no page loaded at all, so this walked nothing");

        assertTrue(absent.isEmpty(),
            "the layout index lists pages that did not load.  Their settings are held rather than "
            + "lost, but autonomy cannot see that track and any page operation will ask about them: "
            + absent);
    }

    /** The page ids the setup was written under, from the index as it stands, as page name to id. */
    private static Map<String, String> pageIds()
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> page
            : LayoutDiagram.readLayoutIndexIds(GOLDEN.getAbsolutePath()).entrySet())
        {
            out.put(page.getKey(), String.valueOf(page.getValue()));
        }

        return out;
    }

    /**
     * Every file under a folder, by content rather than timestamp.  SHA-256, because a collision here would be a silent
     * pass on the one assertion that guards something irreplaceable.
     */
    private static Map<String, String> fingerprint(File folder) throws Exception
    {
        Map<String, String> out = new TreeMap<>();

        collect(folder, folder.getAbsolutePath().length() + 1, out);

        return out;
    }

    /** The same bytes without carriage returns: git checks these files out with CRLF and the application writes LF. */
    private static byte[] withoutCarriageReturns(byte[] raw)
    {
        byte[] out = new byte[raw.length];

        int at = 0;

        for (byte b : raw)
        {
            if (b != 13) out[at++] = b;
        }

        return Arrays.copyOf(out, at);
    }

    private static void collect(File at, int strip, Map<String, String> into) throws Exception
    {
        File[] children = at.listFiles();

        if (children == null) return;

        for (File child : children)
        {
            if (child.isDirectory())
            {
                collect(child, strip, into);

                continue;
            }

            MessageDigest sha = MessageDigest.getInstance("SHA-256");

            byte[] hash = sha.digest(withoutCarriageReturns(Files.readAllBytes(child.toPath())));

            StringBuilder hex = new StringBuilder();

            for (byte b : hash) hex.append(String.format("%02x", b));

            into.put(child.getAbsolutePath().substring(strip).replace('\\', '/'), hex.toString());
        }
    }
}
