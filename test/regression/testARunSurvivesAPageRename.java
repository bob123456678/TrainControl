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
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.LayoutPageEdit;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.SourceOf;

/**
 * What a run did is folded into the setup BEFORE a page is renamed, not after.
 *
 * Stopping autonomy captures nothing (`captureFromLayout` has three callers and none is the stop button), so between
 * the end of a run and the next configuration load, exit, or diagram edit, where the trains ended up lives only in the
 * running Layout. A rename triggers the diagram-edit capture, and the session refuses a capture while its pages are
 * stale: run, stop, rename a page, and every placement the run produced is discarded on every page. That is worse than
 * losing settings: occupancy is derived from placements (`Point.isOccupied` is `currentLoc != null`, and `isPathClear`
 * never consults the s88), so the rebuilt configuration puts the trains back where they were before the run and Start
 * can route one into a block that is physically occupied.
 *
 * The fix is an ORDER: capture while the naming and the store still agree, and let `renamePage` carry the captured
 * entries across. The stale flag stays as a backstop for the capture that follows. This test cannot press the menu
 * item, so it does what the window does in the same order and shows the ORDER decides the outcome (capture-then-rename
 * keeps the run, rename-then-capture loses it); `testTheWindowCallsThemInThatOrder` checks that the window follows it,
 * against `TrainControlUI.duplicateOrRenameCurrentLayout`, because a test that mirrors the sequence proves the sequence
 * works, not that anybody follows it.
 */
public class testARunSurvivesAPageRename
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();
    }

    /**
     * Capturing first keeps what the run did; capturing afterwards loses it. Both orders in one test on purpose:
     * together they say the difference is the order and nothing else.
     */
    @Test
    public void testTheOrderOfTheCaptureDecidesWhetherTheRunSurvives() throws Exception
    {
        assertEquals(runThenRename(true), "kept",
            "capturing BEFORE the rename lost what the run did anyway, so the fix for DW-A1 does not "
            + "work and a rename after a run still discards every placement it produced");

        assertEquals(runThenRename(false), "lost",
            "capturing AFTER the rename kept the run's placement, which would mean this test is not "
            + "reproducing DW-A1 at all - and then the assertion above proves nothing.  Either the "
            + "refusal has gone, or the fixture stopped modelling a run");
    }

    /**
     * The window itself calls `captureRunningLayout()` before it renames the page. The test above drives both orders
     * itself, so it says nothing about which order `TrainControlUI` asks for; this reads
     * `duplicateOrRenameCurrentLayout`, bounded to the method's own body, and checks the one line whose position is the
     * whole fix. Both terms are proved present before they are compared, since an absent term's index is -1, less than
     * every real one.
     *
     * MUTATION this catches: move `captureRunningLayout();` below the `renameOrDuplicate(...)` call. Both substrings
     * are still present, so only the ordering assertion goes red; delete either line instead and the presence asserts
     * catch it.
     */
    @Test
    public void testTheWindowCallsThemInThatOrder() throws Exception
    {
        String body = SourceOf.declaration("    void duplicateOrRenameCurrentLayout(");

        int captured = body.indexOf("captureRunningLayout();");
        int renamed = body.indexOf("renameOrDuplicate(");

        assertTrue(captured >= 0,
            "captureRunningLayout() is no longer called from duplicateOrRenameCurrentLayout - DW-A1 is "
            + "back, because nothing folds the run's placements into the configuration before the "
            + "rename rekeys it");

        assertTrue(renamed >= 0,
            "renameOrDuplicate( is no longer called from duplicateOrRenameCurrentLayout - the scan "
            + "needs updating for whatever replaced it");

        assertTrue(captured < renamed,
            "captureRunningLayout() no longer runs before renameOrDuplicate() in the window - which is "
            + "exactly DW-A1: the store is rekeyed by the rename before the run's placements are "
            + "folded in, and they are discarded on every page, not only the one being renamed");
    }

    /**
     * A run, a stop, and a rename, with the capture on whichever side of the rename is asked for; "kept" if the run's
     * placement survived into the reloaded setup, "lost" otherwise.
     */
    private String runThenRename(boolean captureFirst) throws Exception
    {
        File folder = aWorkingCopy();

        List<LayoutDiagram> pages = pagesIn(folder);

        AutonomySession session = new AutonomySession(folder);
        session.open(pages);

        String active = session.getStore().getActiveConfiguration();

        assertNotNull(active, "the sample setup has no active configuration");

        // The running layout, as loading a configuration builds it.
        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration did not build");

        // The run, in the only part of it that matters here: a train ends up somewhere it did not start. Moving the
        // locomotive between two Points is what a completed path leaves behind.
        org.traincontrol.automation.Point from = occupiedPoint(layout);

        assertNotNull(from, "no locomotive is placed in this configuration, so no run can be modelled");

        org.traincontrol.automation.Point to = emptyPoint(layout);

        assertNotNull(to, "every point is occupied, so there is nowhere for a train to have gone");

        org.traincontrol.base.Locomotive moved = from.getCurrentLocomotive();

        String movedName = moved.getName();
        String destination = to.getName();

        from.setLocomotive(null);
        to.setLocomotive(moved);

        assertFalse(layout.isRunning(), "the fixture must be stopped before a rename is allowed");

        String page = aPageWithSettings(session, pages);
        String renamed = page + " Renamed";

        // THE GESTURE, with the capture on one side of it or the other.
        if (captureFirst)
        {
            session.captureFromLayout(layout.toJSON(), active);
            session.saveWithoutReconciling();
        }

        LayoutPageEdit.renameOrDuplicate(namesOf(pages), diagram(pages, page),
            folder.getAbsolutePath(), page, renamed, true, false, false, session, model);

        if (!captureFirst)
        {
            session.captureFromLayout(layout.toJSON(), active);
        }

        session.saveWithoutReconciling();

        // THE LOAD, as a restart would give.
        AutonomySession after = new AutonomySession(folder);

        after.open(pagesIn(folder));

        // Did the train end up where the run left it?
        for (Map.Entry<String, String> placed : placements(after).entrySet())
        {
            if (!movedName.equals(placed.getValue())) continue;

            String at = after.pointNameForTile(parse(placed.getKey()));

            if (destination.equals(at)) return "kept";
        }

        return "lost";
    }

    // the running layout

    private static org.traincontrol.automation.Point occupiedPoint(
        org.traincontrol.automation.Layout layout)
    {
        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.isOccupied() && point.getCurrentLocomotive() != null) return point;
        }

        return null;
    }

    private static org.traincontrol.automation.Point emptyPoint(
        org.traincontrol.automation.Layout layout)
    {
        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.isDestination() && !point.isOccupied()) return point;
        }

        return null;
    }

    // the setup

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

            if (!standing.optString("name", "").trim().isEmpty())
            {
                out.put(key, standing.optString("name"));
            }
        }

        return out;
    }

    private static org.traincontrol.automationui.TileGraph.TileKey parse(String key)
    {
        int colon = key.lastIndexOf(':');

        String[] xy = key.substring(colon + 1).split(",");

        return new org.traincontrol.automationui.TileGraph.TileKey(key.substring(0, colon),
            Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
    }

    private static String aPageWithSettings(AutonomySession session, List<LayoutDiagram> pages)
    {
        String best = null;
        int most = 0;

        for (LayoutDiagram diagram : pages)
        {
            int count = 0;

            for (String key : placements(session).keySet())
            {
                if (key.startsWith(diagram.getName() + ":")) count++;
            }

            if (count > most)
            {
                most = count;
                best = diagram.getName();
            }
        }

        assertNotNull(best, "no page carries a placement");

        return best;
    }

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

        fail("no page called " + name);

        return null;
    }

    private static List<LayoutDiagram> pagesIn(File folder) throws Exception
    {
        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        return support.LayoutSandbox.wired(model, parser);
    }

    private static File aWorkingCopy() throws Exception
    {
        File from = new File(System.getProperty("user.dir"), "test/test_layout");

        assertTrue(from.isDirectory(), "sample layout not found");

        File temp = File.createTempFile("tc-run-rename", "");

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
