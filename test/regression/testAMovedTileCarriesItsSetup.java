package regression;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A station, and the train standing on it, travel with the tile when it is dragged.
 *
 * Adam, MT-171: "stations do not travel with a moved tile. they vanish until the tile is moved back, at which point the
 * loc is vanished."
 *
 * **This test does not reproduce that.**  It drives the editor's own gesture: move the component on the diagram exactly
 * as `LayoutEditor` does (clear the source square, set the component's coordinates, add it at the destination), then
 * `session.moveTiles`, the capture that runs when the editor closes, the save, and a re-read from disk.  Four variants
 * all carry the station, the name and the locomotive correctly in both directions, adding no error to the setup: to an
 * isolated empty square, one square along onto occupied track, each move in its own session, and both moves in one
 * session.
 *
 * So the data is right; what is left, and what this cannot see, is the DISPLAY: nothing is told to redraw the autonomy
 * overlay when `moveTiles` rebuilds the graph (`touched()` notifies nobody).  Unconfirmed, a lead rather than a
 * finding.  The test is kept regardless: nothing else covered a moved tile carrying its setup through a save and a
 * reload.
 */
public class testAMovedTileCarriesItsSetup
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();
    }

    /**
     * Moved to an isolated square and back, each move its own sitting.  A station that no longer joins the run either
     * side stops being a Point, deliberately; its SETUP must survive that, and reconnecting the track brings it all
     * back.
     */
    @Test
    public void testAStationCarriesItsTrainToAnIsolatedSquareAndBack() throws Exception
    {
        roundTrip(true);
    }

    /**
     * And the ordinary nudge, one square along onto existing track: a different path through `moveTiles`, where the
     * landing square is forgotten before the arriving square's setup is written over it.
     */
    @Test
    public void testAStationCarriesItsTrainOneSquareOntoOccupiedTrack() throws Exception
    {
        roundTrip(false);
    }

    /** Isolated: move to an empty corner; otherwise nudge one square onto occupied track. */
    private void roundTrip(boolean isolated) throws Exception
    {
        File folder = aWorkingCopy();

        List<LayoutDiagram> pages = pagesIn(folder);

        AutonomySession session = open(folder, pages);

        Map<String, String> placed = placements(session);

        assertFalse(placed.isEmpty(),
            "no locomotive is placed in the sample setup, so this test cannot show one travelling");

        String key = placed.keySet().iterator().next();
        String locomotive = placed.get(key);

        TileKey from = parse(key);

        LayoutDiagram page = diagram(pages, from.getPage());

        assertTrue(session.getStore().isStation(from), "the square chosen is not a station");

        String name = session.getStore().getPointName(from);

        TileKey to = isolated ? new TileKey(page.getName(), 1, 0)
            : new TileKey(page.getName(), from.getX() + 1, from.getY());

        assertNotEquals(from, to, "the move has to go somewhere else");

        int errorsBefore = session.errorCount();

        Set<String> reportedBefore = errors(session);

        // The running layout, as loading a configuration builds it.
        String active = session.getStore().getActiveConfiguration();

        model.parseAuto(session.buildConfiguration());

        // THE MUTATION, as the editor makes it.
        move(page, from, to);

        Map<TileKey, TileKey> away = new LinkedHashMap<>();
        away.put(from, to);

        session.moveTiles(away);

        // THE CHECK, before anything is written.
        assertTrue(session.getStore().isStation(to),
            "the station did not travel with the tile, in memory.  Adam, MT-171: \"stations do not "
            + "travel with a moved tile\"");

        assertEquals(session.getStore().getPointName(to), name, "the station's name did not travel");

        assertEquals(placements(session).get(to.toString()), locomotive,
            "the locomotive did not travel with the tile it was standing on.  Got: "
            + placements(session));

        // WHAT THE EDITOR DOES ON THE WAY OUT, then THE SAVE.
        if (model.getAutoLayout() != null)
        {
            session.captureFromLayout(model.getAutoLayout().toJSON(), active);
        }

        session.save();
        page.saveChanges(null, false);

        // THE LOAD.
        AutonomySession after = open(folder, pagesIn(folder));

        assertTrue(after.getStore().isStation(to),
            "the station did not survive the save and reload at its new square");

        assertEquals(after.getStore().getPointName(to), name,
            "the station lost its name across the save");

        assertEquals(placements(after).get(to.toString()), locomotive,
            "the locomotive is not on the moved tile after a save and reload.  Got: "
            + placements(after));

        assertFalse(after.getStore().isStation(from),
            "the station is recorded at BOTH squares, so the move copied rather than moved - which is "
            + "how a train ends up recorded in two places and the whole setup is refused");

        Set<String> reportedAfter = errors(after);

        Set<String> appeared = new LinkedHashSet<>(reportedAfter);
        appeared.removeAll(reportedBefore);

        // THE TRACK THE NUDGE BROKE: the station lands on the next square and leaves a gap behind it, so it stands at
        // the end of a line with its train facing the gap - errors about the new geometry, not a setup the move
        // damaged.  The way back still demands the count come back exactly.
        if (!isolated)
        {
            appeared.remove(AutonomyChecks.STATION_AT_THE_END_OF_A_LINE + " | " + name);
            appeared.remove(AutonomyChecks.FACING_IMPOSSIBLE + " | " + name);
        }

        assertTrue(appeared.isEmpty(), "moving a tile added errors to the setup: " + appeared);

        // AND BACK AGAIN, which is where Adam reports losing the locomotive.
        List<LayoutDiagram> now = pagesIn(folder);

        LayoutDiagram samePage = diagram(now, from.getPage());

        AutonomySession back = open(folder, now);

        String activeAgain = back.getStore().getActiveConfiguration();

        model.parseAuto(back.buildConfiguration());

        move(samePage, to, from);

        Map<TileKey, TileKey> home = new LinkedHashMap<>();
        home.put(to, from);

        back.moveTiles(home);

        if (model.getAutoLayout() != null)
        {
            back.captureFromLayout(model.getAutoLayout().toJSON(), activeAgain);
        }

        back.save();
        samePage.saveChanges(null, false);

        AutonomySession finished = open(folder, pagesIn(folder));

        assertTrue(finished.getStore().isStation(from), "the station did not come back");

        assertEquals(finished.getStore().getPointName(from), name,
            "the station came back without its name");

        assertEquals(placements(finished).get(from.toString()), locomotive,
            "the locomotive did not come back with the tile.  Adam, MT-171: \"they vanish until the "
            + "tile is moved back, at which point the loc is vanished\".  Got: "
            + placements(finished));

        if (isolated)
        {
            assertEquals(finished.errorCount(), errorsBefore,
                "the round trip left the setup with errors it did not start with");
        }
        else
        {
            // The nudge wrote the station over the track it landed on, so the way back leaves a gap there: the same
            // two errors about the broken track are allowed, and every other one must come back exactly.
            Set<String> left = errors(finished);

            for (String brokenTrack : new String[] {AutonomyChecks.STATION_AT_THE_END_OF_A_LINE + " | " + name,
                AutonomyChecks.FACING_IMPOSSIBLE + " | " + name})
            {
                if (!reportedBefore.contains(brokenTrack)) left.remove(brokenTrack);
            }

            assertEquals(left, reportedBefore, "the round trip left the setup with errors it did not start with, or lost"
                + " one it did");
        }
    }

    /**
     * WHICH errors are reported, not how many.  The isolated half moves a station where no track joins, so its
     * copy-level finding has nothing left to attach to and goes away, and a count assertion written to catch a move
     * DAMAGING the setup fails because the report got shorter.  No error may APPEAR; the round trip still demands the
     * count come back exactly, where a genuinely lost finding shows.
     */
    private static Set<String> errors(AutonomySession session)
    {
        Set<String> out = new LinkedHashSet<>();

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR)
            {
                // NOT the square, which this test changes on purpose; the subject is the station's name, which travels
                // with the tile.
                out.add(finding.getMessageKey() + " | " + finding.getSubject());
            }
        }

        return out;
    }

    /** The editor's own gesture: clear the source, move the component, add it at the destination. */
    private static void move(LayoutDiagram page, TileKey from, TileKey to) throws Exception
    {
        LayoutDiagramComponent carrying = page.getComponent(from.getX(), from.getY());

        assertNotNull(carrying, "nothing on " + from + " to move");

        page.addComponent(null, from.getX(), from.getY());

        carrying.setX(to.getX());
        carrying.setY(to.getY());

        page.addComponent(carrying, to.getX(), to.getY());
    }

    private static TileKey parse(String key)
    {
        int colon = key.lastIndexOf(':');

        String[] xy = key.substring(colon + 1).split(",");

        return new TileKey(key.substring(0, colon),
            Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
    }

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

    private static AutonomySession open(File folder, List<LayoutDiagram> pages) throws Exception
    {
        AutonomySession session = new AutonomySession(folder);

        session.open(pages);

        return session;
    }

    private static File aWorkingCopy() throws Exception
    {
        File from = new File(System.getProperty("user.dir"), "test/test_layout");

        assertTrue(from.isDirectory(), "sample layout not found at " + from.getAbsolutePath());

        File temp = File.createTempFile("tc-move", "");

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
