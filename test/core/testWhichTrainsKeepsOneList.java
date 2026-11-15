package core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.AutonomySession.WhichTrains;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Which Trains (FR-111): All, Blacklist or Whitelist sharing one list.  All keeps the list and the railway never sees
 * it, so a list held back keeps nobody out, here or in an older build.  On `single-switch`, at `Approach`.
 *
 * @author Adam
 */
public class testWhichTrainsKeepsOneList
{
    private static support.Scenario scenario;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static List<LayoutDiagram> pages;
    private static Locomotive listed;
    private static Locomotive unlisted;
    private static TileKey square;
    private static JSONObject pristine;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        scenario = support.Scenario.open("single-switch");

        model = scenario.getModel();

        pages = scenario.getPages();

        session = scenario.getSession();

        square = new TileKey(pages.get(0).getName(), 3, 3);

        listed = scenario.newTrain("Which trains listed", 2211);
        unlisted = scenario.newTrain("Which trains unlisted", 2212);

        pristine = session.snapshotSetup();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    @BeforeMethod
    public void aFreshSetup() throws Exception
    {
        session.restoreSetup(new JSONObject(pristine.toString()));
    }

    private static Point built() throws Exception
    {
        Layout railway = scenario.build();

        assertTrue(railway.isValid(), "the setup did not build: " + Layout.getLastError());

        for (Point p : railway.getPoints())
        {
            if (p.getName().startsWith("Approach")) return p;
        }

        throw new AssertionError("no Approach on the railway");
    }

    private static List<String> theList()
    {
        return Collections.singletonList(listed.getName());
    }

    /**
     * All remembers the list and keeps nobody out, and the build carries none of it.
     *
     * MUTATION: stop leaving `REMEMBERED_LOCS` out in `AutonomyBuilder`, or keep All's list under `excludedLocs` in
     * `AutonomySession.storeTrains`, and this fails.
     */
    @Test
    public void testAllKeepsTheListAndTheBuildLeavesItOut() throws Exception
    {
        session.setTrainList(square, theList());
        session.setWhichTrains(square, WhichTrains.BLACKLIST);

        assertEquals(session.getWhichTrains(square), WhichTrains.BLACKLIST, "a list in force does not read Blacklist");

        session.setWhichTrains(square, WhichTrains.ALL);

        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "All did not read back");

        assertEquals(session.getTrainList(square), theList(), "All forgot the list");

        String build = session.buildConfiguration();

        assertFalse(build.contains(AutonomyBuilder.REMEMBERED_LOCS), "the remembered list went into the railway");

        assertFalse(build.contains(listed.getName()), "the name on the remembered list went into the railway");

        Point point = built();

        assertFalse(point.excludes(listed), "All kept a train out");
        assertFalse(point.excludes(unlisted), "All kept a train out");
        assertTrue(point.getExcludedLocs().isEmpty(), "the railway holds the remembered list");
    }

    /**
     * One list, through all three: each reads it its own way, and coming back to All it is the same list.
     *
     * MUTATION: have `AutonomySession.setWhichTrains` pass an empty list on and this fails.
     */
    @Test
    public void testTheThreeShareOneList() throws Exception
    {
        session.setWhichTrains(square, WhichTrains.WHITELIST);
        session.setTrainList(square, theList());

        Point whitelist = built();

        assertFalse(whitelist.excludes(listed), "a Whitelist kept out the train it lists");
        assertTrue(whitelist.excludes(unlisted), "a Whitelist let in a train it does not list");

        session.setWhichTrains(square, WhichTrains.BLACKLIST);

        assertEquals(session.getTrainList(square), theList(), "Blacklist has another list");

        Point blacklist = built();

        assertTrue(blacklist.excludes(listed), "a Blacklist let in the train it lists");
        assertFalse(blacklist.excludes(unlisted), "a Blacklist kept out a train it does not list");

        session.setWhichTrains(square, WhichTrains.ALL);

        session.setWhichTrains(square, WhichTrains.WHITELIST);

        assertEquals(session.getTrainList(square), theList(), "the list did not come back from All");

        assertFalse(built().excludes(listed), "the list came back and is not in force");
    }

    /**
     * An empty Whitelist keeps every train out; an empty All or Blacklist is nothing at all, with no key to show for it.
     *
     * MUTATION: write the mode only with a list in `AutonomySession.storeTrains` and this fails.
     */
    @Test
    public void testAnEmptyListMeansWhatItSays() throws Exception
    {
        session.setWhichTrains(square, WhichTrains.WHITELIST);

        assertEquals(session.getWhichTrains(square), WhichTrains.WHITELIST, "an empty Whitelist did not read back");

        assertTrue(built().excludes(listed), "an empty Whitelist let a train in");

        session.setWhichTrains(square, WhichTrains.ALL);

        for (String key : Arrays.asList("excludedLocs", Point.EXCLUDED_LOCS_MODE, AutonomyBuilder.REMEMBERED_LOCS))
        {
            assertNull(session.getPointProperty(square, key), "All with no list left " + key + " behind");
        }

        session.setWhichTrains(square, WhichTrains.BLACKLIST);

        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "a Blacklist of nobody is not All");
    }

    /**
     * A file written before this reads as it did: a list with no mode is a Blacklist, the whitelist key a Whitelist,
     * neither key All.
     *
     * MUTATION: read an absent mode as ALL in `AutonomySession.getWhichTrains` and this fails.
     */
    @Test
    public void testAnOldListReadsAsItDid() throws Exception
    {
        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "a square with nothing set is not All");

        session.setPointProperty(square, "excludedLocs", new JSONArray(theList()));

        assertEquals(session.getWhichTrains(square), WhichTrains.BLACKLIST, "an old list did not read as a Blacklist");

        assertEquals(session.getTrainList(square), theList(), "an old list was not found");

        session.setPointProperty(square, Point.EXCLUDED_LOCS_MODE, Point.ALLOW_ONLY_LISTED);

        assertEquals(session.getWhichTrains(square), WhichTrains.WHITELIST, "an old whitelist did not read back");
    }

    /**
     * A remembered list stays through a save and reopen and a capture from the running railway, which reads the list off
     * the railway and has none to read.
     *
     * MUTATION: add `REMEMBERED_LOCS` to `AutonomySession.POINT_OPERATIONAL_KEYS` and this fails.
     */
    @Test
    public void testARememberedListSurvivesASaveAndACapture() throws Exception
    {
        session.setTrainList(square, theList());

        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "setting a list changed the choice");

        session.save();

        AutonomySession reopened = new AutonomySession(scenario.getFolder());

        reopened.open(pages);

        assertEquals(reopened.getTrainList(square), theList(), "a reopen lost the list");

        model.parseAuto(reopened.buildConfiguration());

        reopened.captureFromLayout(model.getAutoLayout().toJSON());

        assertEquals(reopened.getWhichTrains(square), WhichTrains.ALL, "a capture changed the choice");

        assertEquals(reopened.getTrainList(square), theList(), "a capture from the railway lost the list");
    }

    /**
     * A locomotive renamed or deleted is followed on a list held back too, or it would come back naming a train that is
     * gone.
     *
     * MUTATION: take the `REMEMBERED_LOCS` line out of the store's rename and this fails.
     */
    @Test
    public void testARenameFollowsARememberedList() throws Exception
    {
        session.setTrainList(square, theList());

        session.getStore().locomotiveRenamed(listed.getName(), "Which trains renamed");

        assertEquals(session.getTrainList(square), Collections.singletonList("Which trains renamed"),
            "a rename missed the remembered list");

        session.getStore().locomotiveDeleted("Which trains renamed");

        assertEquals(session.getTrainList(square), Collections.emptyList(), "a deletion left the name on the list");
    }
}
