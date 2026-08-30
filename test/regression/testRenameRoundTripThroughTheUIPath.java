package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.LayoutPageEdit;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A whole setup, renamed and renamed back through the same call the menu makes.
 *
 * Adam, 2026-08-24: "Do you have tests that try to load an entire config, and then trigger a rename via
 * the same function that the UI calls, and then rename it back and test along the way?  This is the only
 * way to catch bugs across these complex types of features."
 *
 * We did not, and he is right that it is the only way. The existing rename tests call
 * `AutonomyCompanionStore.renamePage` and `LayoutDiagram.writeLayoutIndex` themselves, one layer below
 * the sequence, in an order the test author chose. That reads as coverage and is not: a test that
 * supplies its own order agrees with itself no matter what the application does. Every rename defect
 * this project has had - MT-135, OB-049, OB-092 - lived in the ORDER of those steps or in a step nobody
 * called, and none of them could have been caught that way.
 *
 * So this calls `LayoutPageEdit.renameOrDuplicate`, which is what the menu item now calls, with the
 * arguments the menu item passes. What is NOT covered is the four refusals above that call - open
 * editor, running trains, remote layout, name taken - which stay in the window because they raise
 * dialogs. Those are decisions about whether to ask; everything about what then happens is here.
 *
 * The shape is the one Adam asked for on 2026-08-23: a mutation, a check, a save, a load, and
 * verification that the mutation is still there while the rest stayed the same. The round TRIP is the
 * addition, and it earns its place - MT-135 was reported as "renaming the page back did not restore the
 * stations", so the way back is where the loss became visible.
 */
public class testRenameRoundTripThroughTheUIPath
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * Everything the setup holds for a page survives a rename and the rename back.
     *
     * Deliberately not a spot check. The failures this covers did not damage one collection - they
     * pruned the whole page, because the settings looked like squares that had been deleted. So every
     * collection is loaded before, compared after the rename, and compared again after the way back,
     * and the comparison is of the WHOLE map rather than of a key somebody remembered to look at.
     */
    @Test
    public void testAPageSurvivesBeingRenamedAndRenamedBack() throws Exception
    {
        File folder = aWorkingCopy();

        List<LayoutDiagram> pages = pagesIn(folder);

        AutonomySession session = new AutonomySession(folder);
        session.open(pages);

        String page = aPageWithSettings(session, pages);

        // WHAT THE SETUP HOLDS FOR THAT PAGE, before anything happens to it.
        Map<String, String> before = settingsOn(session, page, pages, page);

        // The SQUARE settings, not the whole map (LD-5).
        //
        // This asked `before.isEmpty()`, which can never be true: `settingsOn` ends with an
        // unconditional `#excluded` entry plus one `#config/<name>` per configuration, and has a
        // single return. So the guard against "this page has nothing on it, and a rename that lost
        // everything would pass" could only fire on a layout with no pages at all.
        //
        // The same file diagnoses this exactly, forty lines from here, and fixed the assertion it was
        // written for without carrying it back to this one: "the map it returns is never empty ...
        // which is the more dangerous kind of wrong".
        assertFalse(squareSettings(before).isEmpty(),
            "the page chosen has no SQUARE settings on it, so a rename that lost them all would "
            + "pass. Only the per-square entries count - the exclusion flag and the per-configuration "
            + "entries are always present, whatever the page holds");

        int idBefore = idOf(folder, page);

        assertTrue(idBefore > 0, "the page has no id in the index, so nothing here can be checked");

        // What the OTHER pages hold, which a rename must not touch either.
        Map<String, Map<String, String>> othersBefore =
            settingsOnEveryOtherPage(session, page, pages);

        // THE MUTATION, through the same call the menu makes.
        String renamed = page + " Renamed";

        LayoutPageEdit.renameOrDuplicate(namesOf(pages), diagram(pages, page),
            folder.getAbsolutePath(), page, renamed, true, false, false, session, model);

        // THE LOAD. A fresh session over the folder as it now stands on disk, exactly as restarting
        // the application would give.
        AutonomySession after = reopened(folder);

        assertEquals(settingsOn(after, renamed, pagesIn(folder), renamed), before,
            "the renamed page did not bring its settings with it.  This is the MT-135 loss: the "
            + "settings are keyed by square, the squares are keyed by page, and a page that changed "
            + "its name without telling the setup reads as a page that was deleted");

        assertEquals(squareSettings(settingsOn(after, page, pages, renamed)), noSettings(),
            "settings are still filed under the OLD page name, so the rename copied rather than "
            + "moved and the next reconcile will prune one of the two");

        assertEquals(idOf(folder, renamed), idBefore,
            "the page changed id when it was renamed.  Ids are what setup.json is keyed by, so a "
            + "page that takes a new number leaves its whole setup attached to a number no page "
            + "holds any more - orphaned, and pruned by the next save (MT-135)");

        assertEquals(settingsOnEveryOtherPage(after, renamed, pagesIn(folder)), othersBefore,
            "renaming one page changed what another page holds.  That is OB-092, where renaming a "
            + "page to \"5\" excluded the page whose ID was 5 and took its train placements with it");

        // AND BACK AGAIN, the same way.
        AutonomySession back = reopened(folder);

        List<LayoutDiagram> nowPages = pagesIn(folder);

        LayoutPageEdit.renameOrDuplicate(namesOf(nowPages), diagram(nowPages, renamed),
            folder.getAbsolutePath(), renamed, page, true, false, false, back, model);

        AutonomySession finished = reopened(folder);

        assertEquals(settingsOn(finished, page, pagesIn(folder), page), before,
            "the page did not come back.  Adam, MT-135: \"Renaming the page back did not restore "
            + "the stations\" - which was the symptom of the settings having already been pruned "
            + "and written on the way out, so there was nothing left to restore");

        assertEquals(idOf(folder, page), idBefore,
            "the page has a different id than it started with after a round trip");

        assertEquals(settingsOnEveryOtherPage(finished, page, pagesIn(folder)), othersBefore,
            "another page was left changed by the round trip");
    }

    /**
     * A rename carries the page's own file with it, and leaves no second copy behind.
     *
     * Separate from the settings, because they fail independently: the setup can be carried perfectly
     * across a rename that left two page files on disk, and the next parse would then show the user
     * both.
     */
    @Test
    public void testTheRenameLeavesOnePageFileNotTwo() throws Exception
    {
        File folder = aWorkingCopy();

        List<LayoutDiagram> pages = pagesIn(folder);

        AutonomySession session = new AutonomySession(folder);
        session.open(pages);

        String page = aPageWithSettings(session, pages);

        int filesBefore = pagesIn(folder).size();

        LayoutPageEdit.renameOrDuplicate(namesOf(pages), diagram(pages, page),
            folder.getAbsolutePath(), page, page + " Moved", true, false, false, session, model);

        List<LayoutDiagram> now = pagesIn(folder);

        assertEquals(now.size(), filesBefore,
            "the page count changed over a RENAME, so a copy was left behind or one went missing");

        List<String> names = new ArrayList<>();

        for (LayoutDiagram one : now) names.add(one.getName());

        assertTrue(names.contains(page + " Moved"), "the renamed page is not there.  Got: " + names);
        assertFalse(names.contains(page), "the old page is still there too.  Got: " + names);
    }

    // --- what the setup holds -------------------------------------------------------------------

    /**
     * The whole gesture, including what the window does AFTER the rename.
     *
     * Adam, MT-171: "renaming a page MOVES locomotives to other stations, not just deleted them.  make
     * a COMPREHENSIVE AND REALISTIC test case to reproduce these bugs, then fix them." And MT-135, with
     * the errors it produced on his railway: "TopMainR2Inter holds a locomotive that is also recorded
     * as standing somewhere else."
     *
     * The test above this one renames a page and reads the setup back, and it passes, and it passed
     * while his railway was being wrecked. Two things were missing from it, and each on its own is
     * enough to miss this.
     *
     * **It stopped at the rename.** The window does not. `layoutEditingComplete` re-reads the diagrams
     * and then `resetAutonomySession` captures the running Layout's state back into the configuration
     * before letting the session go. That capture is the step that does the damage, and no test had
     * ever run it.
     *
     * **It compared stored settings, and never ran the CHECKS.** The damage does not look like a
     * setting going missing. Every placement is still there - there are simply twice as many of them,
     * the second copy keyed to the page's old name. Comparing what the store holds sees more data, not
     * less, and "more" does not fail an equality unless you happen to look at the right key. What the
     * OPERATOR sees is the check refusing to build the setup.
     *
     * So this asserts on the checks, which is the thing he actually meets, and it drives the sequence
     * the window drives. It reproduces on the sample layout exactly as reported: four locomotives, each
     * in two places, on the same squares as his - 6,4 and 14,3 and 13,11.
     */
    @Test
    public void testARenameDoesNotLeaveLocomotivesInTwoPlaces() throws Exception
    {
        File folder = aWorkingCopy();

        List<LayoutDiagram> pages = pagesIn(folder);

        AutonomySession session = new AutonomySession(folder);
        session.open(pages);

        String page = aPageWithSettings(session, pages);
        String renamed = page + "2";

        String active = session.getStore().getActiveConfiguration();

        assertNotNull(active, "the sample setup has no active configuration to capture into");

        int errorsBefore = errorCount(session);
        int placedBefore = placements(session).size();

        assertTrue(placedBefore > 0,
            "no locomotives are placed in this configuration, so a test about placements being "
            + "duplicated cannot fail");

        // The running layout, as loading a configuration builds it.
        model.parseAuto(session.buildConfiguration());

        assertNotNull(model.getAutoLayout(), "the configuration did not build into a layout");

        // THE MUTATION, through the menu's own call.
        LayoutPageEdit.renameOrDuplicate(namesOf(pages), diagram(pages, page),
            folder.getAbsolutePath(), page, renamed, true, false, false, session, model);

        // AND WHAT THE WINDOW DOES NEXT, on the same session: resetAutonomySession captures the
        // running layout's state before letting the session go.  This is the step that was never
        // tested and the step that did the damage.
        session.captureFromLayout(model.getAutoLayout().toJSON(), active);
        session.saveWithoutReconciling();

        // THE LOAD.
        AutonomySession after = reopened(folder);

        Map<String, String> placedAfter = placements(after);

        assertEquals(placedAfter.size(), placedBefore,
            "the rename changed how many locomotives are placed, from " + placedBefore + " to "
            + placedAfter.size() + ".  Each one is recorded twice - once under the new page name and "
            + "once under the old, because the capture that runs after a rename works its tile keys "
            + "out from page objects the rename has just made stale: " + duplicatesIn(placedAfter));

        assertEquals(duplicatesIn(placedAfter), "",
            "a locomotive is recorded in two places at once, which makes autonomy refuse the whole "
            + "setup - \"a locomotive can only be in one place\" (MT-135)");

        assertEquals(errorCount(after), errorsBefore,
            "renaming a page added " + (errorCount(after) - errorsBefore) + " errors to a setup that "
            + "had " + errorsBefore + ".  This is what Adam sees: the page is renamed and the setup "
            + "will no longer run");
    }

    /**
     * tile -> locomotive, out of the active configuration, which is where placements live.
     */
    private static Map<String, String> placements(AutonomySession session)
    {
        Map<String, String> out = new LinkedHashMap<>();

        String active = session.getStore().getActiveConfiguration();

        if (active == null) return out;

        org.json.JSONObject configuration = session.getStore().getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return out;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String key : points.keySet())
        {
            org.json.JSONObject extras = points.optJSONObject(key);

            if (extras == null) continue;

            org.json.JSONObject standing = extras.optJSONObject("loc");

            if (standing == null) continue;

            String name = standing.optString("name", "");

            if (!name.trim().isEmpty()) out.put(key, name);
        }

        return out;
    }

    /**
     * Any locomotive standing on more than one square, named with its squares, or "" if none is.
     *
     * Returned as text rather than as a boolean so that a failure says WHICH train is in two places -
     * which is the first thing anybody reading the failure will want and the first thing the operator
     * is told by the check this mirrors.
     */
    private static String duplicatesIn(Map<String, String> placed)
    {
        Map<String, List<String>> byLocomotive = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : placed.entrySet())
        {
            List<String> where = byLocomotive.get(entry.getValue());

            if (where == null)
            {
                where = new ArrayList<>();
                byLocomotive.put(entry.getValue(), where);
            }

            where.add(entry.getKey());
        }

        StringBuilder out = new StringBuilder();

        for (Map.Entry<String, List<String>> entry : byLocomotive.entrySet())
        {
            if (entry.getValue().size() > 1)
            {
                out.append(out.length() > 0 ? "; " : "")
                   .append(entry.getKey()).append(" at ").append(entry.getValue());
            }
        }

        return out.toString();
    }

    private static int errorCount(AutonomySession session)
    {
        return session.errorCount();
    }

    /**
     * Everything the setup records about one page, flattened so that two of them can be compared whole.
     *
     * Flattened rather than compared collection by collection on purpose. A comparison written by hand
     * covers the collections its author thought of, and the store has eleven - the same counting
     * problem DD-A1 is about, and the one that let the eleventh collection be forgotten in four
     * separate places.
     *
     * The squares come from the PAGE rather than from the store, which is what makes this whole rather
     * than a spot check: every square the page has is asked about, so a setting that moved to the wrong
     * square shows up as a difference at both ends. It also needs no new production API, which a test
     * should not be inventing.
     *
     * The configuration JSON is folded in as text with the page name blanked, because that is where the
     * per-configuration settings live - terminus, reversing, home, placed locomotives - and they are
     * keyed by strings that contain the page name. Blanking it is what makes "the same setup under a
     * different name" comparable at all.
     */
    private static Map<String, String> settingsOn(AutonomySession session, String page,
        List<LayoutDiagram> pages, String subject)
    {
        Map<String, String> out = new LinkedHashMap<>();

        AutonomyCompanionStore store = session.getStore();

        for (LayoutDiagram diagram : pages)
        {
            if (!page.equals(diagram.getName())) continue;

            for (int x = 0; x <= diagram.getSx(); x++)
            {
                for (int y = 0; y <= diagram.getSy(); y++)
                {
                    TileKey tile = new TileKey(page, x, y);

                    String at = x + "," + y;

                    record(out, at, "name", store.getPointName(tile));
                    record(out, at, "station", store.isStation(tile));
                    record(out, at, "length", store.getTileLength(tile));
                    record(out, at, "link", store.getLinkName(tile));
                    record(out, at, "portalOff", store.isPortalDisabled(tile));
                    record(out, at, "partner", named(store.getPortalPartner(tile), page, subject));
                    record(out, at, "barred", store.getBarredArrivals(tile));
                    record(out, at, "signals", named(store.getProtectingSignals(tile), page, subject));
                    record(out, at, "blocking", named(store.getBlockingPoints(tile), page, subject));
                    record(out, at, "caption", named(store.getCaptions().get(tile), page, subject));
                }
            }
        }

        out.put("#excluded", String.valueOf(store.getExcludedPages().contains(page)));

        for (String name : store.getConfigurationNames())
        {
            Object configuration = store.getConfiguration(name);

            out.put("#config/" + name, named(canonical(configuration), page, subject));
        }

        return out;
    }

    private static Map<String, Map<String, String>> settingsOnEveryOtherPage(AutonomySession session,
        String subject, List<LayoutDiagram> pages)
    {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();

        for (LayoutDiagram diagram : pages)
        {
            if (subject.equals(diagram.getName())) continue;

            out.put(diagram.getName(), settingsOn(session, diagram.getName(), pages, subject));
        }

        return out;
    }

    /**
     * Only the per-square entries, dropping the whole-page ones.
     *
     * settingsOn always reports #excluded and a line per configuration, so the map it returns is never
     * empty and "has this page been emptied" cannot be asked of it directly. That is not a flaw in
     * settingsOn - those entries are part of what a page holds - but it did make the first version of
     * the assertion below unfalsifiable, which is the more dangerous kind of wrong.
     */
    private static Map<String, String> squareSettings(Map<String, String> all)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : all.entrySet())
        {
            if (entry.getKey().matches("^[0-9]+,[0-9]+/.*")) out.put(entry.getKey(), entry.getValue());
        }

        return out;
    }

    private static Map<String, String> noSettings()
    {
        return new LinkedHashMap<>();
    }

    /**
     * JSON as text with its object keys in a fixed order.
     *
     * org.json.JSONObject is a HashMap, so toString() emits its keys in whatever order the hash gave
     * them - and that order is not stable across two objects holding identical data. Comparing the
     * strings therefore failed on the first run of this test with two configurations that were the
     * same in every entry. A comparison that reports a difference where there is none is worse than no
     * comparison: it gets muted, and then it is not checking anything.
     */
    private static String canonical(Object value)
    {
        if (value instanceof org.json.JSONObject)
        {
            org.json.JSONObject object = (org.json.JSONObject) value;

            List<String> keys = new ArrayList<>(object.keySet());

            java.util.Collections.sort(keys);

            StringBuilder out = new StringBuilder("{");

            for (String key : keys)
            {
                out.append(key).append(":").append(canonical(object.opt(key))).append(",");
            }

            return out.append("}").toString();
        }

        if (value instanceof org.json.JSONArray)
        {
            org.json.JSONArray array = (org.json.JSONArray) value;

            StringBuilder out = new StringBuilder("[");

            // In order: an array's order is data, unlike an object's key order.
            for (int at = 0; at < array.length(); at++)
            {
                out.append(canonical(array.opt(at))).append(",");
            }

            return out.append("]").toString();
        }

        return String.valueOf(value);
    }

    /**
     * A value as text, with both the page it belongs to and the page being renamed blanked.
     *
     * Blanking the SUBJECT everywhere is the part the first version missed. Other pages point at the
     * renamed page - a protecting signal, a blocker, the far end of a portal, a caption, and the
     * configuration JSON, which every page's map carries - and after a successful rename those
     * pointers say the new name. That is the rename WORKING. Blanking only each page's own name made
     * every one of those read as a change, and the test called correct behaviour a bug.
     *
     * The subject goes first because the new name contains the old one - "1 - Main Renamed" contains
     * "1 - Main" - so replacing the shorter first would leave "&lt;page&gt; Renamed" behind and the two
     * sides would still differ, for a new reason that is even harder to see.
     */
    private static String named(Object value, String page, String subject)
    {
        if (value == null) return null;

        String text = String.valueOf(value);

        if (subject != null) text = text.replace(subject, "<subject>");

        return text.replace(page, "<page>");
    }

    private static void record(Map<String, String> out, String at, String what, Object value)
    {
        if (value == null) return;

        String text = String.valueOf(value);

        // Absent and "nothing set" are the same thing to the user, and must not read as a change: a
        // store returning "" where it returned null has lost nothing.
        if (text.trim().isEmpty() || "false".equals(text) || "0".equals(text) || "[]".equals(text))
        {
            return;
        }

        out.put(at + "/" + what, text);
    }

    /**
     * The page the setup records most about, so that losing it would show.
     */
    private static String aPageWithSettings(AutonomySession session, List<LayoutDiagram> pages)
    {
        String best = null;
        int most = 0;

        for (LayoutDiagram diagram : pages)
        {
            // Counted by SQUARE settings, for the same reason the caller's guard is: every page
            // reports at least the exclusion flag and one entry per configuration, so counting the
            // whole map picks whichever page has the most CONFIGURATIONS rather than the most
            // settings - and on a setup with one configuration, whichever comes first.
            int size = squareSettings(
                settingsOn(session, diagram.getName(), pages, diagram.getName())).size();

            if (size > most)
            {
                most = size;
                best = diagram.getName();
            }
        }

        assertNotNull(best, "the sample setup records nothing about any square on any page, so there "
            + "is no page here whose rename could lose anything");

        return best;
    }

    // --- the index ----------------------------------------------------------------------------

    /**
     * The id the layout index currently gives a page, or 0.
     *
     * Read from the FILE rather than from the session, because the id surviving in memory is not the
     * property under test - what matters is what a restart would see.
     */
    private static int idOf(File folder, String page) throws Exception
    {
        Integer id = LayoutDiagram.readLayoutIndexIds(folder.getAbsolutePath()).get(page);

        return id == null ? 0 : id;
    }

    /**
     * The page names in the order the index has them, which is what the operation mutates.
     */
    private static List<String> namesOf(List<LayoutDiagram> pages)
    {
        List<String> names = new ArrayList<>();

        for (LayoutDiagram one : pages) names.add(one.getName());

        return names;
    }

    private static LayoutDiagram diagram(List<LayoutDiagram> pages, String name)
    {
        for (LayoutDiagram one : pages)
        {
            if (name.equals(one.getName())) return one;
        }

        fail("no page called " + name + " among " + namesOf(pages));

        return null;
    }

    // --- fixtures -----------------------------------------------------------------------------

    private static List<LayoutDiagram> pagesIn(File folder) throws Exception
    {
        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        return parser.parseLayout(new LinkedList<MarklinAccessory>());
    }

    private static AutonomySession reopened(File folder) throws Exception
    {
        AutonomySession session = new AutonomySession(folder);

        session.open(pagesIn(folder));

        return session;
    }

    /**
     * A whole copy of the sample layout - diagram files, index and setup - because these tests write.
     */
    private static File aWorkingCopy() throws Exception
    {
        File from = new File(System.getProperty("user.dir"), "test/test_layout");

        assertTrue(from.isDirectory(), "sample layout not found at " + from.getAbsolutePath());

        File temp = File.createTempFile("tc-rename", "");

        assertTrue(temp.delete(), "making room for a directory of the same name");

        copyTree(from, temp);

        temp.deleteOnExit();

        return temp;
    }

    private static void copyTree(File from, File to) throws Exception
    {
        assertTrue(to.mkdirs() || to.isDirectory(), "could not make " + to);

        File[] children = from.listFiles();

        if (children == null) return;

        for (File one : children)
        {
            File target = new File(to, one.getName());

            if (one.isDirectory()) copyTree(one, target);
            else Files.copy(one.toPath(), target.toPath());

            target.deleteOnExit();
        }
    }
}
