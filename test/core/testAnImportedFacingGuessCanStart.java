package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The facing an import of a 2.8.1 file has to guess is one trains may arrive in, where the square has one (REG3-C1,
 * TDY3-B1).
 *
 * The old format cannot say which way a train points, so the import chooses - and it chose the first copy the build
 * emits, barred or not.  Copies are emitted N, E, S, W, so at BottomMainA, with arrivals from the east barred, that is
 * the westbound copy: no station.  Since the build honours a recorded facing even where only such a copy holds it -
 * the copy IS the direction, and a train that really faces that way must stand there - the guess put an imported train
 * where autonomy will not start it, under a log line saying "just run them".  A guess is not a fact, and the one guess
 * autonomy can start is a way trains may arrive.
 *
 * On a fresh configuration over the frozen railway, with only BottomMainA carrying a train in the file.
 *
 * MUTATION: have the import take the first copy's facing again and this fails.
 *
 * @author Adam
 */
public class testAnImportedFacingGuessCanStart
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The guess at BottomMainA is the way trains may arrive there.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheGuessIsAWayTrainsMayArrive() throws Exception
    {
        File file = new File(new File(sandbox.getFolder(), "config" + File.separator + "autonomy_legacy"), "autonomy.json");

        if (!file.exists()) throw new SkipException("this fixture carries no legacy autonomy.json at " + file);

        JSONObject legacy = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        session.getStore().createConfiguration("Imported onto a bar", null);
        session.getStore().setActiveConfiguration("Imported onto a bar");
        session.rebuild();

        TileKey mainA = null;

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if ("BottomMainA".equals(session.getStore().getPointName(key))) mainA = key;
        }

        assertNotNull(mainA, "precondition: the frozen railway has no BottomMainA");

        Set<Side> mayArrive = session.homeFacingsFor(mainA);

        List<Side> inBuildOrder = new ArrayList<>(session.facingsFor(mainA).values());

        assertFalse(mayArrive.isEmpty(), "precondition: no copy of BottomMainA is one trains may arrive at");
        assertFalse(inBuildOrder.isEmpty() || mayArrive.contains(inBuildOrder.get(0)), "precondition: the first copy the"
            + " build emits at BottomMainA is one trains may arrive at, so the guess cannot go wrong here");

        // ONLY BOTTOMMAINA CARRIES A TRAIN, one the file names elsewhere.
        JSONArray points = legacy.getJSONArray("points");

        String train = null;

        for (int i = 0; i < points.length(); i++)
        {
            JSONObject point = points.getJSONObject(i);

            if (!point.has("loc")) continue;

            if (train == null) train = point.getJSONObject("loc").getString("name");

            point.remove("loc");
        }

        assertNotNull(train, "precondition: the legacy file places no train");

        boolean placed = false;

        for (int i = 0; i < points.length(); i++)
        {
            JSONObject point = points.getJSONObject(i);

            if ("BottomMainA".equals(point.optString("name")))
            {
                point.put("loc", new JSONObject().put("name", train));
                placed = true;
            }
        }

        assertTrue(placed, "precondition: the legacy file has no BottomMainA point");

        session.importLegacy(legacy);

        Side guessed = session.getFacing(mainA);

        assertNotNull(guessed, "precondition: the import did not place the train at BottomMainA, or invented no facing");

        assertTrue(mayArrive.contains(guessed), "the import guessed the train at BottomMainA faces " + guessed + ", a way"
            + " trains may not arrive there - the build honours it, so the train stands where autonomy will not start"
            + " it, and the import's log says to just run them (REG3-C1).  Ways trains may arrive: " + mayArrive);
    }
}
