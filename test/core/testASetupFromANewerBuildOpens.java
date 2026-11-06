package core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A setup saved by 3.1.0 with a new feature stays safe in 3.0.8 (FR-111).  `saved-by-3-1-0` is `single-switch` as this
 * build saves it: `Approach` stop-only, and `MainPlatform` on All remembering a list.  This class compiles and runs in
 * both builds: `docs/tools/old-setups.sh --opens-in dev-3.0.9` runs it in the older one, then reads what that build
 * saved here.  So it may ask only what 3.0.8 has; an older build is told apart by `Point` having no `isStopOnly`.
 *
 * @author Adam
 */
public class testASetupFromANewerBuildOpens
{
    private static final String STOP_ONLY_KEY = "runThrough";
    private static final String REMEMBERED_KEY = "rememberedLocs";
    private static final String LISTED = "Saved list train";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static List<LayoutDiagram> pages;
    private static Locomotive listed;
    private static Locomotive other;
    private static TileKey approach;
    private static TileKey main;

    private static boolean thisBuildKnowsTheRule()
    {
        try
        {
            Point.class.getMethod("isStopOnly");

            return true;
        }
        catch (NoSuchMethodException older)
        {
            return false;
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        String savedByTheOlderBuild = System.getenv("TC_OLDER_SAVED");

        // Before the model (OB-111)
        sandbox = support.LayoutSandbox.open(savedByTheOlderBuild != null ? new File(savedByTheOlderBuild)
            : support.Scenario.folderFor("saved-by-3-1-0"));

        model = support.TestStation.simulatedInDebug();

        model.stop();

        pages = support.LayoutSandbox.wiredPages(model);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        approach = new TileKey(pages.get(0).getName(), 3, 3);
        main = new TileKey(pages.get(0).getName(), 7, 3);

        listed = model.newMM2Locomotive(LISTED, 2221);
        other = model.newMM2Locomotive("Saved other train", 2222);

        for (Locomotive each : Arrays.asList(listed, other))
        {
            each.setReversible(true);
            each.setPreferredSpeed(50);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null)
            {
                model.deleteLoc(listed.getName());
                model.deleteLoc(other.getName());
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    private static Layout build(AutonomySession from) throws Exception
    {
        model.parseAuto(from.buildConfiguration());

        Layout railway = model.getAutoLayout();

        assertTrue(railway != null && railway.isValid(), "the setup does not build here: " + Layout.getLastError());

        railway.setSimulate(true);
        railway.setMinDelay(0);
        railway.setMaxDelay(0);

        return railway;
    }

    private static List<String> offered(Layout railway, Locomotive loc)
    {
        List<String> out = new ArrayList<>();

        for (List<Edge> path : railway.getPossiblePaths(loc, true))
        {
            out.add(path.get(path.size() - 1).getEnd().getName());
        }

        Collections.sort(out);

        return out;
    }

    /**
     * No throw, the checks raise nothing they would not raise without the new settings, and the railway builds whole.
     *
     * MUTATION: have `Layout.parseAuto` invalidate the railway on the stop-only key and this fails.
     */
    @Test
    public void testItOpensAndBuilds() throws Exception
    {
        Layout railway = build(session);

        assertEquals(railway.getPoints().size(), 5, "the railway came out a different shape");
        assertEquals(railway.getEdges().size(), 6, "the railway came out a different shape");

        List<String> withThem = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check()) withThem.add(said(finding));

        org.json.JSONObject held = session.snapshotSetup();

        session.setPointProperty(approach, STOP_ONLY_KEY, null);
        session.setPointProperty(main, REMEMBERED_KEY, null);

        List<String> without = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check()) without.add(said(finding));

        session.getStore().restoreSetup(held);

        assertEquals(withThem, without, "the new settings raised a finding of their own");
    }

    /**
     * Nothing is lost: not on a load, a save, a reopen, nor the exit's capture from the running railway, so a setup
     * carried through this build and back keeps its choices.
     *
     * MUTATION: add the remembered list's key to `AutonomySession.POINT_OPERATIONAL_KEYS` and this fails.
     */
    @Test
    public void testNothingIsLostOnLoadAndSave() throws Exception
    {
        assertKept(session, "on load");

        session.save();

        AutonomySession reopened = new AutonomySession(sandbox.getFolder());

        reopened.open(pages);

        assertKept(reopened, "after a save and a reopen");

        reopened.captureFromLayout(build(reopened).toJSON());

        assertKept(reopened, "after a capture from the running railway");

        reopened.save();

        AutonomySession again = new AutonomySession(sandbox.getFolder());

        again.open(pages);

        assertKept(again, "after a capture, a save and a reopen");

        String saveTo = System.getenv("TC_OLDER_SAVES_TO");

        if (saveTo != null) copy(sandbox.getFolder().toPath(), new File(saveTo).toPath());
    }

    private static String said(AutonomyChecks.Finding finding)
    {
        return finding.getSeverity() + " " + finding.getMessageKey() + " " + finding.getSubject() + " " + finding.getTile();
    }

    private static void assertKept(AutonomySession from, String when)
    {
        assertEquals(from.getPointProperty(approach, STOP_ONLY_KEY), Boolean.FALSE, "the stop-only choice was lost " + when);

        assertEquals(String.valueOf(from.getPointProperty(main, REMEMBERED_KEY)), "[\"" + LISTED + "\"]",
            "the remembered list was lost " + when);

        assertNull(from.getPointProperty(main, "excludedLocs"), "the remembered list came to be in force " + when);
    }

    /**
     * A list All remembers keeps nobody out, in any build: it must not read as a Blacklist or a Whitelist.
     *
     * MUTATION: store it in the fixture as `excludedLocs` with `excludedLocsMode: all` and run this in 3.0.8: it fails.
     */
    @Test
    public void testTheRememberedListKeepsNobodyOut() throws Exception
    {
        Layout railway = build(session);

        Point platform = railway.getPoint("MainPlatform");

        assertFalse(platform.excludes(listed), "the train on the remembered list was kept out");
        assertFalse(platform.excludes(other), "a train not on the remembered list was kept out");
        assertFalse(platform.isAllowOnlyListed(), "the remembered list reads as a whitelist");
        assertTrue(platform.getExcludedLocs().isEmpty(), "the remembered list reached the railway");
    }

    /**
     * Here the rule holds and a train stops.  In an older build, which knows no such rule, the same station is an
     * ordinary station: a train offered what it is offered with the setting gone, and it runs through to the end.  No
     * worse than with the feature off, and that is what the owner accepted.
     *
     * MUTATION: take the `isStopOnly` line out of `Layout.bfsBySettings` and this fails here.
     */
    @Test
    public void testAStopOnlyStationIsSafeWhereItIsNotKnown() throws Exception
    {
        Layout railway = build(session);

        railway.getPoint("WestEnd").setLocomotive(other);

        Point onward = railway.getPoint("MainPlatform");

        List<Edge> route = railway.bfs(railway.getPoint("WestEnd"), onward, null);

        if (thisBuildKnowsTheRule())
        {
            assertNull(route, "this build ran a route through the stop-only station");

            return;
        }

        List<String> withTheSetting = offered(railway, other);

        org.json.JSONObject held = session.snapshotSetup();

        session.setPointProperty(approach, STOP_ONLY_KEY, null);
        session.setPointProperty(main, REMEMBERED_KEY, null);

        Layout control = build(session);

        control.getPoint("WestEnd").setLocomotive(other);

        assertEquals(withTheSetting, offered(control, other), "the older build offers something it would not otherwise");

        session.getStore().restoreSetup(held);

        railway = build(session);

        railway.getPoint("WestEnd").setLocomotive(other);

        route = railway.bfs(railway.getPoint("WestEnd"), railway.getPoint("MainPlatform"), null);

        assertNotNull(route, "the older build has no route through an ordinary station");

        assertTrue(railway.executePathByHand(route, other, 50, Layout.ALWAYS_REVERSE, -1), "the train did not arrive");

        assertEquals(railway.getLocomotiveLocation(other).getName(), "MainPlatform", "the train is not where it was sent");
    }

    private static void copy(Path from, Path to) throws Exception
    {
        try (java.util.stream.Stream<Path> files = Files.walk(from))
        {
            for (Path file : (Iterable<Path>) files::iterator)
            {
                Path target = to.resolve(from.relativize(file).toString());

                if (Files.isDirectory(file)) Files.createDirectories(target);
                else Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
