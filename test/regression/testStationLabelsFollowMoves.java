package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * A station's NAME survives being moved, wherever it is moved to.
 *
 * Adam: move sensor 1016 down one square and its name turns into the empty placeholder; down one and right one and the
 * name is fine; down one and right two and it is gone again.  A platform's name is written on a separate square beside
 * it (usually below), so nudging the platform down one lands it exactly on its own label.  Everything on a square being
 * built over is dropped, which is right, but a caption is a reference to another square: when what it refers to is what
 * built over it, it is the only copy of a name the same gesture was carrying to safety.
 *
 * So these tests are a matrix: the same platform and label moved to every square around it.  The other half is the
 * opposite direction, a label that really is stale must still be dropped, since "keep every caption" passes all the
 * rest.
 */
public class testStationLabelsFollowMoves
{
    private static final String PAGE = "main";

    // The matrix: a platform and its label, and every square the platform might be nudged to

    /** The label survives a move to any of the eight neighboring squares, including its own. */
    @Test
    public void testTheLabelSurvivesAMoveToEveryNeighbouringSquare()
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                if (dx == 0 && dy == 0) continue;

                check(at(4, 4), at(4, 5), at(4 + dx, 4 + dy));
            }
        }
    }

    /** And to squares further off, in case something ever special-cases the adjacent ones. */
    @Test
    public void testTheLabelSurvivesALongerMoveToo()
    {
        for (int dx = 0; dx <= 3; dx++)
        {
            for (int dy = 0; dy <= 3; dy++)
            {
                if (dx == 0 && dy == 0) continue;

                check(at(4, 4), at(4, 5), at(4 + dx, 4 + dy));
            }
        }
    }

    /**
     * The label works from any side of the platform, not only from below, where placeCaption puts one when there is
     * room.
     */
    @Test
    public void testItDoesNotMatterWhichSideTheLabelIsOn()
    {
        for (int[] label : new int[][] {{4, 5}, {4, 3}, {3, 4}, {5, 4}, {5, 5}, {3, 3}, {4, 4}})
        {
            for (int[] to : new int[][] {{4, 5}, {4, 3}, {3, 4}, {5, 4}, {5, 5}, {3, 3}, {9, 9}})
            {
                check(at(4, 4), at(label[0], label[1]), at(to[0], to[1]));
            }
        }
    }

    /**
     * A station moved onto its own label keeps the label, on the square it now occupies (Adam's bug).  A caption may
     * sit on its own station's square, which is how a name comes to be drawn over a platform.
     */
    @Test
    public void testAStationMovedOntoItsOwnLabelKeepsIt()
    {
        AutonomyCompanionStore store = named(at(4, 4), at(4, 5), "Platform3");

        store.moveTiles(one(at(4, 4), at(4, 5)));

        assertEquals(store.getCaptionTarget(at(4, 5)), at(4, 5),
            "the platform landed on its own name and the name was thrown away by the same move that "
            + "carried the platform - which is what Adam saw as [---] after nudging a sensor down one");

        assertEquals(store.getPointName(at(4, 5)), "Platform3", "and the name itself did not travel");
    }

    // The other direction: a label that IS stale still goes

    /**
     * A label naming a square that was genuinely built over is dropped: "never forget a caption" passes every other
     * test here and leaves names floating over track that has been replaced.
     */
    @Test
    public void testALabelNamingATileThatWasBuiltOverIsDropped()
    {
        AutonomyCompanionStore store = named(at(4, 4), at(4, 5), "Platform3");

        // Something else moves on top of the platform, from a square nobody has labeled
        store.moveTiles(one(at(9, 9), at(4, 4)));

        assertNull(store.getCaptionTarget(at(4, 5)),
            "the label is still naming a square that now holds somebody else's track");

        assertNull(store.getPointName(at(4, 4)),
            "and the station name is still on the square that was built over");
    }

    /** A label on a square being built over by an unrelated tile is dropped. */
    @Test
    public void testALabelBuiltOverByAnUnrelatedTileIsDropped()
    {
        AutonomyCompanionStore store = named(at(4, 4), at(4, 5), "Platform3");

        store.moveTiles(one(at(9, 9), at(4, 5)));

        assertNull(store.getCaptionTarget(at(4, 5)),
            "a label that has been built over by track from somewhere else is still there, naming a "
            + "platform from a square that is no longer its label");

        assertEquals(store.getPointName(at(4, 4)), "Platform3",
            "and the platform itself, which nothing touched, has lost its name");
    }

    // Group moves and bulk edits: the same rule, arrived at from the other paths

    /**
     * A group drag where a station lands on its OWN label keeps it: every square in a group drag is both a source and a
     * target, so one target can be the label of the station arriving there.
     */
    @Test
    public void testAGroupDragKeepsALabelTheArrivingStationLandsOn()
    {
        AutonomyCompanionStore store = named(at(4, 4), at(5, 4), "Platform3");

        store.setStation(at(9, 9), true);
        store.setPointName(at(9, 9), "Platform4");

        // The label's square is not itself moving, which makes this the landing case; a label on a moving square
        // travels with it (tested elsewhere).
        Map<TileKey, TileKey> moving = new LinkedHashMap<>();

        moving.put(at(4, 4), at(5, 4));
        moving.put(at(9, 9), at(9, 8));

        store.moveTiles(moving);

        assertEquals(store.getCaptionTarget(at(5, 4)), at(5, 4),
            "Platform3 landed on its own label and the label was thrown away by the same drag that "
            + "carried the platform");

        assertEquals(store.getPointName(at(5, 4)), "Platform3", "the first platform did not arrive");

        assertEquals(store.getPointName(at(9, 8)), "Platform4", "nor did the second");
    }

    /**
     * And a group drag where something ELSE lands on the label drops it: "its station is moving" is not the rule, "its
     * station is moving HERE" is, or one station's name is drawn across another's track.
     */
    @Test
    public void testAGroupDragDropsALabelSomethingElseLandsOn()
    {
        AutonomyCompanionStore store = named(at(4, 4), at(6, 4), "Platform3");

        store.setStation(at(5, 4), true);
        store.setPointName(at(5, 4), "Platform4");

        Map<TileKey, TileKey> moving = new LinkedHashMap<>();

        moving.put(at(4, 4), at(5, 4));
        moving.put(at(5, 4), at(6, 4));

        store.moveTiles(moving);

        assertNull(store.getCaptionTarget(at(6, 4)),
            "Platform4 was built over the top of Platform3's label, and the label stayed - so the "
            + "diagram now writes one platform's name across the other one's track");
    }

    /**
     * A column move where the destination column holds a label for the column being moved: the bulk path forgets the
     * destination first, then moves the source onto it.
     */
    @Test
    public void testAColumnMoveKeepsALabelBelongingToTheColumnBeingMoved()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.setStation(at(2, 3), true);
        store.setPointName(at(2, 3), "Platform3");
        store.setCaption(at(7, 3), at(2, 3));

        support.TileLine plan =
            support.TileLine.of(PAGE, true, 2, 7, 6, occupied(3), true);

        store.moveTiles(plan.moves, plan.builtOver);

        assertEquals(store.getCaptionTarget(at(7, 3)), at(7, 3),
            "the label was in the column being written over, so it was dropped - and the station it "
            + "names was in the column arriving, so it was dropped a moment too early");

        assertEquals(store.getPointName(at(7, 3)), "Platform3", "and the station name did not arrive");
    }

    /** And a column move over a label belonging to something that is NOT moving still drops it. */
    @Test
    public void testAColumnMoveStillDropsALabelThatIsNothingToDoWithIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.setStation(at(2, 3), true);
        store.setCaption(at(7, 3), at(9, 9));

        support.TileLine plan =
            support.TileLine.of(PAGE, true, 2, 7, 6, occupied(3), true);

        store.moveTiles(plan.moves, plan.builtOver);

        assertNull(store.getCaptionTarget(at(7, 3)),
            "a label naming a square nothing in this edit touched has been built over, and is still "
            + "sitting on the new track");
    }

    /**
     * A label is spared only by the station it NAMES landing on it, not by that station moving.  The difference appears
     * when more than one tile moves at once, which is every column move: sparing it because its station is "moving"
     * leaves the name on whichever OTHER tile actually landed there.
     */
    @Test
    public void testALabelIsSparedOnlyByTheStationThatLandsOnIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.setStation(at(2, 3), true);
        store.setPointName(at(2, 3), "Platform3");

        // The label is nowhere near the platform and nothing about it is arriving: the tile that lands on it comes from
        // (2, 8), not the station it names
        store.setCaption(at(7, 8), at(2, 3));

        support.TileLine plan =
            support.TileLine.of(PAGE, true, 2, 7, 10, occupied(3, 8), true);

        store.moveTiles(plan.moves, plan.builtOver);

        assertNull(store.getCaptionTarget(at(7, 8)),
            "a label was spared because the station it names happened to be moving somewhere - and "
            + "what landed on the label was a different tile, so the name is now drawn on track it "
            + "has nothing to do with");

        assertEquals(store.getPointName(at(7, 3)), "Platform3",
            "and the station itself should still have arrived");
    }

    // What the user actually sees: the name the diagram reads off the label

    /**
     * End to end, on real track: after the nudge the label still resolves to the station's name.  The tests above are
     * about the store; this is the answer the diagram asks for when it draws a caption, so it goes through a session
     * with track under it and moves the tile on the DIAGRAM as well, since a name is only resolved for a square the
     * graph made a Point of.
     */
    @Test
    public void testTheDiagramStillFindsTheNameAfterTheNudge() throws IOException
    {
        for (int[] to : new int[][] {{1, 2}, {2, 2}, {3, 2}})
        {
            File folder = Files.createTempDirectory("tc-labels").toFile();

            try
            {
                AutonomySession session = new AutonomySession(folder);
                LayoutDiagram page = twoRuns();

                session.open(Arrays.asList(page));

                TileKey station = at(1, 1);
                TileKey label = at(1, 2);

                // Through the SESSION, which rebuilds the graph: a name set straight into the store is not one the
                // diagram can read back yet.
                session.setStation(station, true);
                session.setPointName(station, "Platform3");
                session.setCaption(label, station);

                assertEquals(session.pointNameForTile(station), "Platform3",
                    "the fixture is wrong - the name does not resolve before the move either");

                // The diagram first, then the setup: the editor's order, and the graph is rebuilt from the pages.
                page.addComponent(null, 1, 1);
                page.addComponent(componentType.FEEDBACK, to[0], to[1], 0, 0, 5, 11,
                    accessoryDecoderType.MM2, null);

                session.moveTiles(one(station, at(to[0], to[1])));

                TileKey where = session.getStore().getCaptionTarget(at(1, 2)) == null
                    ? session.getStore().getCaptionTarget(at(to[0], to[1]))
                    : session.getStore().getCaptionTarget(at(1, 2));

                assertNotNull(where, "the label is gone after moving the sensor to "
                    + to[0] + "," + to[1] + " - which is the empty placeholder on the diagram");

                assertEquals(session.pointNameForTile(where), "Platform3",
                    "the label survived but no longer resolves to a name, so the diagram draws the "
                    + "empty placeholder - moved to " + to[0] + "," + to[1]);
            }
            finally
            {
                delete(folder);
            }
        }
    }

    /** One platform, one label, one move: the station and its name both come out the other side. */
    private static void check(TileKey station, TileKey label, TileKey to)
    {
        String where = station + " labelled at " + label + ", moved to " + to;

        AutonomyCompanionStore store = named(station, label, "Platform3");

        store.moveTiles(one(station, to));

        assertTrue(store.isStation(to), "the station did not arrive: " + where);

        assertEquals(store.getPointName(to), "Platform3", "the name did not arrive: " + where);

        TileKey labelNow = label.equals(station) ? to : label;

        assertEquals(store.getCaptionTarget(labelNow), to,
            "the label is gone, or is naming the wrong square: " + where);
    }

    private static AutonomyCompanionStore named(TileKey station, TileKey label, String name)
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.setStation(station, true);
        store.setPointName(station, name);
        store.setCaption(label, station);

        return store;
    }

    private static Map<TileKey, TileKey> one(TileKey from, TileKey to)
    {
        Map<TileKey, TileKey> move = new LinkedHashMap<>();

        move.put(from, to);

        return move;
    }

    private static Set<Integer> occupied(Integer... indices)
    {
        return new LinkedHashSet<>(Arrays.asList(indices));
    }

    private static TileKey at(int x, int y)
    {
        return new TileKey(PAGE, x, y);
    }

    /**
     * Two rows of connected track, so that a sensor nudged in any direction still lands on a square the graph will make
     * a Point of.
     */
    private static LayoutDiagram twoRuns() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram(PAGE, 8, 5, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 2, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    private static void delete(File file)
    {
        File[] kids = file.listFiles();

        if (kids != null) for (File kid : kids) delete(kid);

        file.delete();
    }
}
