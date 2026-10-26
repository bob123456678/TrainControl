package ui;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import support.SourceOf;

/**
 * FR-034: "Show station label here" opens on the station the square is next to.
 *
 * Two rules.  The distance one is arithmetic and is driven directly.  The memory one is a sequence - a last-clicked
 * station used once and then gone - which needs a panel, a session and a railway, so it is read out of the source
 * instead.
 *
 * @author Adam
 */
public class testStationLabelPrefill
{
    /** A page with two stations on it, and one on a different page. */
    private static final TileKey NEAR = new TileKey("main", 4, 4);
    private static final TileKey FAR = new TileKey("main", 20, 20);
    private static final TileKey ELSEWHERE = new TileKey("other", 1, 1);

    /**
     * The nearest station wins, and distance is measured the way the eye measures it.
     *
     * MUTATION: comparing only x, or dropping the square on either term, picks FAR for the diagonal
     * case below.
     */
    @Test
    public void testTheNearestStationIsTheOneOffered()
    {
        TileKey square = new TileKey("main", 5, 5);

        assertEquals(AutonomyEditorPanel.nearestOf(square, Arrays.asList(FAR, NEAR)), NEAR,
            "the label chooser opened on the station across the page rather than the one beside it");

        // Order of the list must not decide it.
        assertEquals(AutonomyEditorPanel.nearestOf(square, Arrays.asList(NEAR, FAR)), NEAR,
            "which station is nearest depends on what order the graph happened to list them in");

        // A station straight up is nearer than one diagonally away, and both are nearer than FAR.
        TileKey up = new TileKey("main", 5, 2);
        TileKey diagonal = new TileKey("main", 8, 8);

        assertEquals(AutonomyEditorPanel.nearestOf(square, Arrays.asList(diagonal, up)), up,
            "three squares up beat three across and three down, so the distance is not being "
            + "measured in two dimensions");

        // A case that both terms have to be present to get right: the cases above are decided by the horizontal
        // distance alone, so here the nearer station is further away horizontally and much closer vertically.
        TileKey acrossABit = new TileKey("main", 7, 5);
        TileKey farBelow = new TileKey("main", 5, 20);

        assertEquals(AutonomyEditorPanel.nearestOf(square, Arrays.asList(farBelow, acrossABit)),
            acrossABit,
            "a station two squares across lost to one fifteen squares down, so only one dimension is "
            + "being compared");

        assertEquals(AutonomyEditorPanel.nearestOf(new TileKey("main", 5, 19),
            Arrays.asList(farBelow, acrossABit)), farBelow,
            "and the same pair the other way round picks the same station, so the comparison is not "
            + "reading the square it was asked about");
    }

    /**
     * A station on another page is not a candidate at all: a page has its own coordinates, and a station there would
     * win whenever its coordinates happened to be small.
     *
     * MUTATION: dropping the page test makes ELSEWHERE, at (1,1), beat NEAR for the square below.
     */
    @Test
    public void testAStationOnAnotherPageIsNotNear()
    {
        TileKey square = new TileKey("main", 3, 3);

        assertEquals(AutonomyEditorPanel.nearestOf(square, Arrays.asList(ELSEWHERE, NEAR)), NEAR,
            "a station on a different page was offered - its coordinates are not distances from "
            + "anything on this one, they just happened to be smaller");

        assertNull(AutonomyEditorPanel.nearestOf(square, Arrays.asList(ELSEWHERE)),
            "a page with no stations on it still produced a default, taken from another drawing");

        assertNull(AutonomyEditorPanel.nearestOf(square, java.util.Collections.emptyList()),
            "a railway with no stations produced one anyway");

        assertNull(AutonomyEditorPanel.nearestOf(null, Arrays.asList(NEAR)),
            "asked about no square at all and answered");
    }

    /**
     * The last-clicked station is used once and then forgotten, so one right click elsewhere gets it and the next goes
     * back to the distance.  Read rather than run: what can silently break is the taking becoming a reading again.
     *
     * MUTATION: removing `lastStationTouched = null;` fails the second assertion; putting the nearest
     * lookup before the memory fails the ordering one.
     */
    @Test
    public void testTheLastClickedStationIsSpentAfterOneUse() throws Exception
    {
        String source = SourceOf.declaration("    void promptStationLabel(");

        int used = source.indexOf("showing = lastStationTouched;");

        assertTrue(used > 0, "nothing defaults to the station last clicked any more");

        int spent = source.indexOf("lastStationTouched = null;", used);
        int nearest = source.indexOf("showing = nearestStation(tile);", used);

        assertTrue(spent > 0 && (nearest < 0 || spent < nearest),
            "the last-clicked station is read and never cleared, so one click on a platform keeps "
            + "winning every label for the rest of the session - which is the behaviour FR-034 asked "
            + "to change");

        assertTrue(nearest > used,
            "the nearest station is not consulted after the memory, so either it never runs or it "
            + "overrides the one thing the operator just did");
    }

    /**
     * `nearestStation` turns the graph into the list `nearestOf` chooses from, keeping only the reduced points the user
     * marked as stations.  It is private, so it is reached by reflection over the smallest session, three-feedback
     * diagram and `AutonomyEditorPanel` that will do.
     *
     * The middle point is the closest tile to the square asked about but an ordinary point of track, so only a filter
     * that reads `isStation()` can tell the right answer from the geometrically nearest tile.
     *
     * MUTATION: drop `point.isStation()` from the filter in `AutonomyEditorPanel.nearestStation`, and the excluded
     * middle point, nearer than either real station, wins.
     */
    @Test
    public void testNearestStationOnlyOffersRealStations() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs "
                + "a display");
        }

        java.io.File layout = java.nio.file.Files.createTempDirectory("tc-station-prefill").toFile();

        try
        {
            LayoutDiagram page = new LayoutDiagram("main", 12, 3, null, null);

            page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2,
                null);

            for (int x = 2; x <= 4; x++)
            {
                page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0,
                    accessoryDecoderType.MM2, null);
            }

            // The excluded point: an ordinary feedback tile, never marked as a station.
            page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2,
                null);

            for (int x = 6; x <= 9; x++)
            {
                page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0,
                    accessoryDecoderType.MM2, null);
            }

            page.addComponent(componentType.FEEDBACK, 10, 1, 0, 0, 7, 13, accessoryDecoderType.MM2,
                null);

            page.setPageId("1");

            AutonomySession session = new AutonomySession(layout);

            session.open(Arrays.asList(page));

            TileKey nearStation = new TileKey("main", 1, 1);
            TileKey excluded = new TileKey("main", 5, 1);
            TileKey farStation = new TileKey("main", 10, 1);

            assertTrue(session.getReducer().getPoints().containsKey(excluded),
                "the middle point was not reduced at all, so this proves nothing about filtering it");

            session.setStation(nearStation, true);
            session.setPointName(nearStation, "Near");

            session.setStation(farStation, true);
            session.setPointName(farStation, "Far");

            // "excluded" is deliberately left as it was built - an ordinary point of track.

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            java.lang.reflect.Method nearestStation =
                AutonomyEditorPanel.class.getDeclaredMethod("nearestStation", TileKey.class);
            nearestStation.setAccessible(true);

            // Right beside the excluded point and further from both real stations, so a filter that let it through
            // would win on distance alone.
            TileKey asking = new TileKey("main", 5, 2);

            Object offered = nearestStation.invoke(panel, asking);

            assertEquals(offered, nearStation,
                "nearestStation offered " + offered + " instead of the nearer REAL station - the "
                + "excluded point is closer to the square asked about, so this only comes out "
                + "right if isStation() actually filters it out");
        }
        finally
        {
            deleteRecursively(layout);
        }
    }

    /** A temporary directory and everything under it. */
    private static void deleteRecursively(java.io.File file)
    {
        java.io.File[] children = file.listFiles();

        if (children != null)
        {
            for (java.io.File child : children) deleteRecursively(child);
        }

        file.delete();
    }
}
