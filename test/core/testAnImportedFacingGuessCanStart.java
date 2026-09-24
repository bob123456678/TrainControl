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
import static org.testng.Assert.assertEquals;
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
 * The facing an import of a 2.8.1 file gives a train is the way the file ran it, and where the file cannot say, a
 * guess trains may arrive in over the finished setup (REG3-C1, TDY3-B1, REG4-A1, REG4-C1).
 *
 * A 2.8.1 graph states direction as one-way edges: a train on a point goes only along the edges that start there, so
 * where they all leave the square by one side, that is the way the train drives (REG4-A1).  The import read none of
 * them and guessed, and a guess the wrong way round sends a train along a path locked one way while its decoder drives
 * it the other.
 *
 * The old format cannot say which way a train points, so the import chooses - and it chose the first copy the build
 * emits, barred or not.  Copies are emitted N, E, S, W, so at BottomMainA, with arrivals from the east barred, that is
 * the westbound copy: no station.  Since the build honours a recorded facing even where only such a copy holds it -
 * the copy IS the direction, and a train that really faces that way must stand there - the guess put an imported train
 * where autonomy will not start it, under a log line saying "just run them".  A guess is not a fact, and the one guess
 * autonomy can start is a way trains may arrive.
 *
 * On a fresh configuration over the frozen railway, with only BottomMainA carrying a train in the file - and its edges
 * taken out, so the file cannot say and the import has to guess.
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

        // THE FILE CANNOT SAY: BottomMainA's own edges taken out, so the import has to guess (REG4-A1).
        withoutTheEdgesOf(legacy, "BottomMainA");

        AutonomySession.LegacyImport imported = session.importLegacy(legacy);

        Side guessed = session.getFacing(mainA);

        assertNotNull(guessed, "precondition: the import did not place the train at BottomMainA, or invented no facing");
        assertTrue(imported.facingsInvented == 1, "precondition: the import did not count its guess");

        // Asked of the finished setup: a way trains may arrive is a question about where the train will stand.
        mayArrive = session.homeFacingsFor(mainA);

        assertTrue(mayArrive.contains(guessed), "the import guessed the train at BottomMainA faces " + guessed + ", a way"
            + " trains may not arrive there - the build honours it, so the train stands where autonomy will not start"
            + " it, and the import's log says to just run them (REG3-C1).  Ways trains may arrive: " + mayArrive);
    }

    /**
     * The guess is made over the setup the import leaves, after it has marked the squares trains turn round at
     * (REG4-C1).
     *
     * A legacy terminus or reversing point is written as "must turn round" further down the same point's import, and a
     * square trains must turn at has only turning copies, which face the other way from its plain ones.  Guessed
     * before the mark, the way trains may arrive was asked of the plain copies, and at a terminus with one side barred
     * the guess was the one facing only the barred turning copy holds: the train stood where autonomy will not start
     * it, whichever side was barred.
     *
     * BottomMainA, arrivals from the east barred, its legacy point marked a terminus, its edges taken out.
     *
     * MUTATION: guess inside the points loop again, before the mark, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheGuessIsMadeOverTheFinishedSetup() throws Exception
    {
        JSONObject legacy = legacyFile();

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        session.getStore().createConfiguration("Imported onto a terminus", null);
        session.getStore().setActiveConfiguration("Imported onto a terminus");
        session.rebuild();

        TileKey mainA = tileNamed(session, "BottomMainA");

        assertNotNull(mainA, "precondition: the frozen railway has no BottomMainA");
        assertFalse(session.isMustTurnAround(mainA), "precondition: the new configuration already turns trains at"
            + " BottomMainA");

        onlyThisPointPlaced(legacy, "BottomMainA").put("terminus", true);

        withoutTheEdgesOf(legacy, "BottomMainA");

        session.importLegacy(legacy);

        assertTrue(session.isMustTurnAround(mainA), "precondition: the import did not mark BottomMainA a square trains"
            + " turn round at");

        Side guessed = session.getFacing(mainA);

        assertNotNull(guessed, "precondition: the import did not place the train at BottomMainA, or invented no facing");

        java.util.Set<Side> mayArrive = session.homeFacingsFor(mainA);

        assertTrue(mayArrive.contains(guessed), "at BottomMainA, a terminus now with arrivals from the east barred, the"
            + " import guessed the train faces " + guessed + " - asked before the same import marked the square, of"
            + " copies the build no longer makes; now only a copy trains may not arrive at faces that way, and autonomy"
            + " will not start it (REG4-C1).  Ways trains may arrive: " + mayArrive);
    }

    /**
     * A 2.8.1 file's edges say which way each train it places runs, and the import stands it that way round (REG4-A1).
     *
     * A fresh upgrade: the frozen railway with the diagram's directions and the barred arrivals taken out, which is how a
     * 2.8.1 user meets the diagram - plain track both ways, a switch by default out of its toe only.  TopMainR1's one
     * legacy edge leads north, to TopMainPost, and 2.8.1 drove the 2-8-4 standing there no other way; but the square is
     * arrived at from both sides by default, so the first copy the build makes faces south, and the import stood the
     * train facing south.
     *
     * Where the diagram cannot hold the file's facing - TopMainR1Inter's edge leads east, and by default the switch
     * above it lets no train arrive from the north, so its one copy faces north - no impossible facing is saved (OB-270),
     * and the import names the square rather than calling it a guess.  Tunnel is not split on a fresh upgrade, so it
     * has no facing to give.  (On Adam's own railway all three agree with the sides he bars, and hold the file's.)
     *
     * MUTATION: ignore the file's edges and guess, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheFileSaysWhichWayItRan() throws Exception
    {
        JSONObject legacy = legacyFile();

        File fresh = freshUpgrade();

        try
        {
            AutonomySession session = new AutonomySession(fresh);

            session.open(support.LayoutSandbox.wiredPages(model));

            session.getStore().createConfiguration("A fresh upgrade", null);
            session.getStore().setActiveConfiguration("A fresh upgrade");
            session.rebuild();

            TileKey r1 = tileNamed(session, "TopMainR1");

            assertNotNull(r1, "precondition: the frozen railway has no TopMainR1");
            assertTrue(session.facingsFor(r1).containsValue(Side.N) && session.facingsFor(r1).containsValue(Side.S),
                "precondition: on a fresh upgrade TopMainR1 is not arrived at from both sides: "
                + session.facingsFor(r1));

            AutonomySession.LegacyImport imported = session.importLegacy(legacy);

            java.util.Map<String, Side> expected = new java.util.LinkedHashMap<>();

            expected.put("TopMainR1", Side.N);
            expected.put("TopMainR1Inter", Side.E);
            expected.put("TopMainR2Inter", Side.E);
            expected.put("Tunnel", Side.S);

            for (java.util.Map.Entry<String, Side> each : expected.entrySet())
            {
                TileKey tile = tileNamed(session, each.getKey());

                assertNotNull(tile, "precondition: the frozen railway has no " + each.getKey());

                // NOT SPLIT: one Point, with no facing to give (Tunnel, on a fresh upgrade).
                if (session.facingsFor(tile).isEmpty())
                {
                    assertTrue(session.getFacing(tile) == null && !imported.facingsNotHeld.contains(each.getKey()),
                        "the import gave " + each.getKey() + ", a square not split into copies, a facing or a report: "
                        + session.getFacing(tile) + ", " + imported.facingsNotHeld);

                    continue;
                }

                if (!session.facingsFor(tile).containsValue(each.getValue()))
                {
                    assertTrue(imported.facingsNotHeld.contains(each.getKey()), "the 2.8.1 file ran the train on "
                        + each.getKey() + " " + each.getValue() + ", which no copy of the square holds on a fresh upgrade,"
                        + " and the import does not say so: " + imported.facingsNotHeld + ".  Its copies: "
                        + session.facingsFor(tile) + "; the next named squares by each side: "
                        + nextByEachSide(session, tile) + "; guessed: " + imported.facingsInvented);

                    continue;
                }

                assertTrue(each.getValue() == session.getFacing(tile), "the train the 2.8.1 file stands on "
                    + each.getKey() + " was imported facing " + session.getFacing(tile) + ", and the file's edges from"
                    + " there all lead " + each.getValue() + " - the only way 2.8.1 drove it (REG4-A1).  Its copies: "
                    + session.facingsFor(tile) + "; the next named squares by each side: " + nextByEachSide(session, tile)
                    + "; guessed: " + imported.facingsInvented);
            }

            assertTrue(imported.facingsInvented == 0, "every facing the file states was read, and the import still says"
                + " it guessed " + imported.facingsInvented);

            assertTrue(session.getFacing(r1) == Side.N, "precondition of the claim's point: TopMainR1 holds the file's"
                + " facing on a fresh upgrade, and was not checked above");
        }
        finally
        {
            deleteQuietly(fresh);
        }
    }

    /**
     * The old file's one-way running is carried onto the diagram, so on a fresh upgrade every train can stand the way
     * the file ran it (Adam, 2026-09-24: *"Carry the old file's directions onto the diagram - yes, to the extent
     * possible."*).
     *
     * Without it the diagram's defaults decide: a switch lets trains out of its toe only, so TopMainR1Inter, reached
     * from the north in the old graph through such a switch, could be arrived at from the east only - one copy, facing
     * north - and the train the file ran east from there was stood facing north.
     *
     * MUTATION: carry nothing, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheOldDirectionsAreCarried() throws Exception
    {
        JSONObject legacy = legacyFile();

        File fresh = freshUpgrade();

        try
        {
            AutonomySession session = new AutonomySession(fresh);

            session.open(support.LayoutSandbox.wiredPages(model));

            session.getStore().createConfiguration("Directions carried", null);
            session.getStore().setActiveConfiguration("Directions carried");
            session.rebuild();

            AutonomySession.LegacyImport imported = session.importLegacy(legacy);

            assertFalse(imported.directionsCarried.isEmpty(), "the import set no track running the way the old file's"
                + " one-way edges ran it");

            TileKey inter = tileNamed(session, "TopMainR1Inter");

            assertTrue(session.facingsFor(inter).containsValue(Side.E) && session.getFacing(inter) == Side.E,
                "the old file ran the train on TopMainR1Inter east, and after the import it stands facing "
                + session.getFacing(inter) + " among " + session.facingsFor(inter) + " - its directions were not"
                + " carried onto the diagram");

            assertTrue(imported.facingsNotHeld.isEmpty(), "with the old file's directions carried, a facing it gives"
                + " still cannot be held: " + imported.facingsNotHeld);
        }
        finally
        {
            deleteQuietly(fresh);
        }
    }

    /**
     * A direction the operator set is kept, and where it keeps a train from standing the way the file ran it, the
     * import says so (Adam: *"to the extent possible"*).
     *
     * The import fills gaps, like everything else it writes.  The square north of TopMainR1Inter, which the old file's
     * edges run south, is set to run north beforehand: it stays so, and TopMainR1Inter - no longer arrived at from the
     * north - cannot hold the east the file ran its train, which the import names.
     *
     * MUTATION: overwrite a direction the operator set, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheOperatorsOwnDirectionIsKept() throws Exception
    {
        JSONObject legacy = legacyFile();

        File first = freshUpgrade();
        File second = freshUpgrade();

        try
        {
            AutonomySession probe = new AutonomySession(first);

            probe.open(support.LayoutSandbox.wiredPages(model));
            probe.getStore().createConfiguration("Directions carried", null);
            probe.getStore().setActiveConfiguration("Directions carried");
            probe.rebuild();

            TileKey inter = tileNamed(probe, "TopMainR1Inter");
            TileKey north = new TileKey(inter.getPage(), inter.getX(), inter.getY() - 1);

            java.util.Map.Entry<org.traincontrol.automationui.TileGraph.DirectionKey,
                org.traincontrol.automationui.TileGraph.Direction> carried = null;

            for (java.util.Map.Entry<org.traincontrol.automationui.TileGraph.DirectionKey,
                org.traincontrol.automationui.TileGraph.Direction> each
                : new java.util.ArrayList<>(probe.importLegacy(legacy).directionsCarried.entrySet()))
            {
                if (north.equals(each.getKey().square())) carried = each;
            }

            assertNotNull(carried, "precondition: the old file's edges set no direction on " + north + ", north of"
                + " TopMainR1Inter");

            org.traincontrol.automationui.TileGraph.Direction theirs =
                carried.getValue() == org.traincontrol.automationui.TileGraph.Direction.TOWARD_A
                ? org.traincontrol.automationui.TileGraph.Direction.TOWARD_B
                : org.traincontrol.automationui.TileGraph.Direction.TOWARD_A;

            AutonomySession session = new AutonomySession(second);

            session.open(support.LayoutSandbox.wiredPages(model));
            session.getStore().createConfiguration("Directions carried", null);
            session.getStore().setActiveConfiguration("Directions carried");
            session.rebuild();

            // THE OPERATOR'S OWN, set before the import.
            session.setDirection(north, carried.getKey().getRouteId(), theirs);

            AutonomySession.LegacyImport imported = session.importLegacy(legacy);

            assertEquals(session.getStore().getTileDirection(north, carried.getKey().getRouteId()), theirs, "the import"
                + " overwrote the direction the operator had set on " + north);

            assertTrue(imported.facingsNotHeld.contains("TopMainR1Inter"), "with the way in from the north closed by the"
                + " operator, TopMainR1Inter cannot hold the east the old file ran its train, and the import does not"
                + " say so: " + imported.facingsNotHeld);
        }
        finally
        {
            deleteQuietly(first);
            deleteQuietly(second);
        }
    }

    /**
     * For a failure message: the named squares a walk leaving each side of a square meets first.
     */
    private static String nextByEachSide(AutonomySession session, TileKey tile)
    {
        java.util.Set<TileKey> named = new java.util.LinkedHashSet<>(session.getStore().getNamedTiles());

        StringBuilder out = new StringBuilder();

        for (Side side : Side.values())
        {
            List<String> names = new ArrayList<>();

            for (TileKey next : session.getGraph().firstStopsLeaving(tile, side, named))
            {
                names.add(session.getStore().getPointName(next));
            }

            out.append(side).append('=').append(names).append(' ');
        }

        return out.toString().trim();
    }

    private static JSONObject legacyFile() throws Exception
    {
        File file = new File(new File(sandbox.getFolder(), "config" + File.separator + "autonomy_legacy"), "autonomy.json");

        if (!file.exists()) throw new SkipException("this fixture carries no legacy autonomy.json at " + file);

        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    private static TileKey tileNamed(AutonomySession session, String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        return null;
    }

    /**
     * The file with one train, one the file names elsewhere, standing only on the named point.
     */
    private static JSONObject onlyThisPointPlaced(JSONObject legacy, String name)
    {
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

        for (int i = 0; i < points.length(); i++)
        {
            JSONObject point = points.getJSONObject(i);

            if (name.equals(point.optString("name")))
            {
                point.put("loc", new JSONObject().put("name", train));

                return point;
            }
        }

        throw new AssertionError("precondition: the legacy file has no " + name + " point");
    }

    private static void withoutTheEdgesOf(JSONObject legacy, String name)
    {
        JSONArray edges = legacy.getJSONArray("edges");

        JSONArray kept = new JSONArray();

        for (int i = 0; i < edges.length(); i++)
        {
            if (!name.equals(edges.getJSONObject(i).optString("start"))) kept.put(edges.getJSONObject(i));
        }

        assertTrue(kept.length() < edges.length(), "precondition: the legacy file has no edge from " + name);

        legacy.put("edges", kept);
    }

    /**
     * A copy of the sandbox with the diagram's directions and barred arrivals taken out of the setup.
     */
    private static File freshUpgrade() throws Exception
    {
        java.nio.file.Path from = sandbox.getFolder().toPath();
        java.nio.file.Path to = Files.createTempDirectory("tc-fresh-upgrade");

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(from))
        {
            for (java.nio.file.Path each : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                java.nio.file.Path target = to.resolve(from.relativize(each).toString());

                if (Files.isDirectory(each)) Files.createDirectories(target);
                else Files.copy(each, target);
            }
        }

        File setup = new File(to.toFile(), "config" + File.separator + "autonomy" + File.separator + "setup.json");

        JSONObject json = new JSONObject(new String(Files.readAllBytes(setup.toPath()), StandardCharsets.UTF_8));

        assertTrue(json.has("tileDirections") && json.has("barredArrivals"), "precondition: the frozen setup has no"
            + " directions or barred arrivals to take out");

        json.remove("tileDirections");
        json.remove("barredArrivals");

        Files.write(setup.toPath(), json.toString(1).getBytes(StandardCharsets.UTF_8));

        return to.toFile();
    }

    private static void deleteQuietly(File folder)
    {
        if (folder == null) return;

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(folder.toPath()))
        {
            java.util.List<java.nio.file.Path> all = new ArrayList<>();

            walk.forEach(all::add);

            java.util.Collections.reverse(all);

            for (java.nio.file.Path each : all) Files.deleteIfExists(each);
        }
        catch (Exception ignored)
        {
            // a temporary folder left behind is not a failure of the claim
        }
    }
}
