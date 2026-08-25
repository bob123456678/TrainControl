package regression;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
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
 * Adam, MT-171: "stations do not travel with a moved tile. they vanish until the tile is moved back,
 * at which point the loc is vanished."
 *
 * **This test does not reproduce that, and that is worth saying plainly rather than leaving it to be
 * inferred from a green run.** It was written to reproduce it, the way the rename half of the same
 * entry was reproduced, and it drives the editor's own gesture: move the component on the diagram
 * exactly as `LayoutEditor` does - clear the source square, set the component's coordinates, add it at
 * the destination - then `session.moveTiles`, then the capture that runs when the editor closes, then
 * the save, then re-read everything from disk.
 *
 * Four variants were tried, and all four carry the station, the name and the locomotive correctly, in
 * both directions, with the check count unchanged:
 *
 * - to an isolated empty square, which disconnects the station from the run
 * - one square along, onto occupied track, which is the ordinary nudge
 * - each move in its own session, as a restart between them would give
 * - both moves in one session with a single capture at the end, as one editing sitting gives
 *
 * So the data is right. What is left, and what this cannot see, is the DISPLAY: nothing in the editor
 * is told to redraw the autonomy overlay when `moveTiles` rebuilds the graph - `touched()` rebuilds and
 * notifies nobody. A station whose marker is not repainted looks exactly like a station that did not
 * travel, and that is the same shape of fault as the timetable one, where the data was perfect
 * throughout and the panel was never repainted. Unconfirmed, and this comment is a lead rather than a
 * finding.
 *
 * The test is kept regardless. There was no coverage at all of a moved tile carrying its setup through
 * a save and a reload, and the rename work showed what a gap like that costs.
 */
public class testAMovedTileCarriesItsSetup
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * Moved to an isolated square and back, each move its own sitting.
     *
     * The isolated square matters: a station that no longer joins the run either side of it stops
     * being a Point, which is deliberate and documented. Its SETUP must survive that - the station,
     * the name and the train are still recorded, and reconnecting the track brings it all back with
     * nothing to re-enter.
     */
    @Test
    public void testAStationCarriesItsTrainToAnIsolatedSquareAndBack() throws Exception
    {
        roundTrip(true);
    }

    /**
     * And the ordinary nudge: one square along, onto track that is already there.
     *
     * A different path through `moveTiles` - the destination is a landing square that gets forgotten
     * before the arriving square's setup is written over it - so it is worth its own run rather than
     * being assumed to behave like the one above.
     */
    @Test
    public void testAStationCarriesItsTrainOneSquareOntoOccupiedTrack() throws Exception
    {
        roundTrip(false);
    }

    /**
     * @param isolated true to move to an empty corner, false to nudge one square onto occupied track
     */
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

        assertEquals(after.errorCount(), errorsBefore,
            "moving a tile added errors to the setup");

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

        assertEquals(finished.errorCount(), errorsBefore,
            "the round trip left the setup with errors it did not start with");
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

        return parser.parseLayout(new LinkedList<MarklinAccessory>());
    }

    private static AutonomySession open(File folder, List<LayoutDiagram> pages) throws Exception
    {
        AutonomySession session = new AutonomySession(folder);

        session.open(pages);

        return session;
    }

    private static File aWorkingCopy() throws Exception
    {
        File from = new File(System.getProperty("user.dir"), "cs2_sample_layout");

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
