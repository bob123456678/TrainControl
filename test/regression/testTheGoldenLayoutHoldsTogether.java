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
 * Adam's own railway, read and checked, and never written to.
 *
 * Every other test in this suite runs against `test/test_layout`, a frozen copy. That is right: a fixture
 * has to hold still or the tests are measuring the fixture. But it means the only thing the suite
 * knows about the layout Adam actually drives is what was true on the day the copy was taken - and he
 * goes on drawing track, naming stations and setting up autonomy, which is exactly where the next
 * defect will be found.
 *
 * Adam: "Using his new golden layout files, expand test coverage and find/fix new bugs."
 *
 * **What this asserts is deliberately narrow: things that must be true of ANY layout.** It is not a
 * ratchet on his data. He is allowed to draw a page with no stations on it, exclude whatever he likes
 * and leave a configuration half finished, and none of that is a defect - so a test that counted
 * things would go red every time he worked on the railway, which is the fastest way to make a suite
 * worth ignoring. What it checks is internal consistency: that the setup names track the diagram has,
 * that the numbering agrees with itself, and that nothing was written.
 *
 * **The `@AfterClass` check is the important one and it is about this suite, not about the railway.**
 * Two test classes start the real window, which loads whatever the saved UI state names - his layout.
 * On 25 August the battery was leaving it modified on every run, and it went unnoticed for two days
 * because nothing was watching. Something is now - and it runs LAST, after every `@Test` below, rather
 * than as the first of them: a fingerprint taken as the first `@Test` compares against itself before any
 * of its four siblings have run, so a write made by the third or fourth was never in scope. Review
 * caught this as TST-B2.
 *
 * Skipped rather than failed when the folder is not there, so this travels with the repository
 * without demanding that everybody have Adam's railway.
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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (!GOLDEN.isDirectory())
        {
            throw new SkipException("no golden layout at " + GOLDEN.getAbsolutePath());
        }

        before = fingerprint(GOLDEN);

        assertFalse(before.isEmpty(), "the golden layout folder is empty, so nothing below is a check");

        model = init(null, true, false, false, true);

        String path = "file:///" + GOLDEN.getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        store = new AutonomyCompanionStore(GOLDEN);

        store.setPageIds(pageIds());
        store.load();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (model != null) model.stop();
    }

    /**
     * Reading Adam's railway does not write to it.
     *
     * The assertion this class exists for, and its SUBJECT is what nothing caught for two days: two
     * test classes start the real window, which opens whatever the saved UI state names, and a save on
     * that path rewrote his configuration file on every battery.
     *
     * **`@AfterClass`, not `@Test` - and that is itself the fix for TST-B2.** This used to be the
     * FIRST `@Test` in the class, comparing `after` against `before` while the four tests below it had
     * not run yet - so a write made by any of THEM, in this same JVM, was never in scope; the class
     * could tell you it had not written to the golden layout before it was done reading it. Declared
     * here instead, it runs once, after every `@Test` in the class has finished, so `after` sees
     * whatever any of them did.
     *
     * **Read the scope before relying on it further.** Both captures are still in this class's own
     * JVM, and the suite runs one JVM per class - so a write by ANOTHER class already happened before
     * `before` was taken, and this cannot see it. What it catches now is every write this class's own
     * reading could cause, which is the property the class exists for and no longer only a slice of it.
     *
     * The protection against another class writing to that folder lives in `docs/tools/battery.sh`, which
     * fingerprints it around the whole run and says so explicitly - and which `ant test` does not
     * use.
     *
     * That particular cause is fixed - the file was being written in a different ORDER each run,
     * because a set of locomotives was iterated in identity-hash order - but the class of problem is
     * not: any test that can reach the live layout is one bug away from writing to it, and his setup
     * is the thing he cannot get back.
     *
     * Every file, by content, not by timestamp: a file rewritten with identical bytes is not a
     * problem, and flagging it would make this test noise.
     *
     * MUTATION this catches: have any `@Test` above - `testEverySquareTheSetupNamesIsOnTheDiagram` is
     * as good as any - call `store.save()` once. As a `@Test` running first this passed regardless,
     * because `after` was taken before that write happened; run last, it sees the rewritten file and
     * fails.
     */
    @AfterClass
    public void testNothingWroteToTheGoldenLayout() throws Exception
    {
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
     * Every square the setup names is a square the diagram still draws.
     *
     * The one direction of difference that is always a defect - a setting for track that does not
     * exist means either the diagram failed to read something real, or the setup is keyed to a page
     * that has moved underneath it. The other direction is nothing: track with no settings on it is
     * just track nobody has set up.
     *
     * Squares on pages the setup was told to leave out of autonomy are still checked, because they are
     * still drawn - excluding a page hides it from autonomy, not from the diagram.
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

        assertTrue(missing.isEmpty(),
            "the setup names squares the diagram does not draw.  Either a page failed to read, or "
            + "settings are keyed to a page that has moved under them: " + missing);
    }

    /**
     * The setup's record of the page numbering agrees with the index on disk.
     *
     * The setup is keyed by page ID in the file and by page NAME in memory, and `"pages"` is the
     * translation between them. When that record disagrees with `gleisbild.cs2`, every key resolves to
     * the wrong page - which is the MT-135 loss, and it is silent.
     *
     * A page the record knows and the index does not is normal and is not checked here: that is a page
     * deleted or not yet loaded, and FR-018 is the mechanism for it. What is checked is the pairs they
     * both have, which must say the same thing.
     */
    @Test
    public void testTheSetupAndTheIndexAgreeAboutTheNumbering()
    {
        Map<String, Integer> index = LayoutDiagram.readLayoutIndexIds(GOLDEN.getAbsolutePath());

        assertFalse(index.isEmpty(),
            "the layout index could not be read, so this test is comparing against nothing");

        // Asked of the store's own detection rather than recomputed here. `pageIdConflicts` is
        // populated at load: it holds every id whose recorded NAME is not the name that id carries
        // now, which is precisely "the numbering moved under the setup". Recomputing it would be a
        // second opinion about the same question, and this file has been bitten by second opinions.
        Map<String, String> conflicts = store.getPageIdConflicts();

        assertTrue(conflicts.isEmpty(),
            "the autonomy setup and the layout index disagree about which page is which.  Every "
            + "setting is keyed by that number, so this is a page's settings attached to another "
            + "page's track, and it is silent - recorded name to name that id carries now: "
            + conflicts);
    }

    /**
     * No two pages claim one id.
     *
     * Silent everywhere until DR-B4: the index would write a duplicate straight back out, the setup's
     * inversion would let the second page win, and half of one page's settings would resolve to the
     * other. Asked of the real file because that is where a duplicate would have come from.
     */
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

        assertTrue(clashes.isEmpty(), "two pages answer to one id: " + clashes);
    }

    /**
     * Every square a configuration says something about is a square the diagram draws.
     *
     * The configurations key by page NAME on disk and are never translated, which is the opposite
     * keying from the shared setup in the same folder - so they survive a rename only because the
     * application rekeys them in memory first. That dependency is stated nowhere in the file format,
     * and this is the check that would notice it failing on the real railway.
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

        assertTrue(missing.isEmpty(),
            "a configuration holds settings for squares the diagram does not draw.  Placements, "
            + "homes, facings and lengths live there, and they are keyed by page NAME rather than by "
            + "id, so a rename the application did not perform orphans them: " + missing);
    }

    /**
     * Every page in the layout index is a page that actually loaded.
     *
     * `CS2File` skips a page whose file will not parse or is not there and carries on, which is the
     * right behaviour and is why FR-018 exists - but it is also how a page can quietly stop being part
     * of the railway. On a layout that lives in OneDrive this is a real state, so it is worth saying
     * out loud when it happens rather than only when something else goes wrong because of it.
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

        assertTrue(absent.isEmpty(),
            "the layout index lists pages that did not load.  Their settings are held rather than "
            + "lost, but autonomy cannot see that track and any page operation will ask about them: "
            + absent);
    }

    /**
     * The page ids the setup was written under, from the index as it stands.
     *
     * @return page name to page id, in the string form the store wants
     */
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
     * Every file under a folder, by content.
     *
     * By CONTENT rather than by timestamp, because a file rewritten with identical bytes has not lost
     * anything and flagging it would make this test noise. The hash is SHA-256 because collisions here
     * would be a silent pass, and this is the one assertion in the class that guards something
     * irreplaceable.
     *
     * @param folder where to look
     * @return relative path to hash
     * @throws Exception if a file cannot be read
     */
    private static Map<String, String> fingerprint(File folder) throws Exception
    {
        Map<String, String> out = new TreeMap<>();

        collect(folder, folder.getAbsolutePath().length() + 1, out);

        return out;
    }

    /**
     * The same bytes without carriage returns.
     *
     * Git checks these files out with CRLF and the application writes them with LF, so a file
     * rewritten with identical content still differs byte for byte. Reporting that would make this
     * test fail on every run, and a test that always fails is one somebody switches off - which is the
     * failure this check exists to prevent, arriving from the other side.
     *
     * @param raw the file
     * @return the same, with every 0x0D removed
     */
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
