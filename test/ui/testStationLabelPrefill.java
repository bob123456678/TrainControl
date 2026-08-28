package ui;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.AutonomyEditorPanel;

/**
 * FR-034: "Show station label here" opens on the station the square is next to.
 *
 * Adam, 2026-08-27: "prefill it with the label of the nearest station by tile distance.  change to
 * current behavior: only prefill the last clicked station for one right click somewhere else - then
 * revert to the distance."
 *
 * Two rules. The distance one is arithmetic and is driven directly below. The memory one is a
 * sequence - a value used once and then gone - which needs the panel, a session and a railway to
 * exercise, so it is read out of the source instead; what can silently break there is the taking
 * turning back into a reading, and that is visible.
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

        // A case that BOTH terms have to be present to get right.
        //
        // Every case above happens to be decided by the horizontal distance alone, so dropping the
        // vertical term from the comparison passed all of them - which the mutation run found and I
        // had not. Here the nearer station is further away horizontally and much closer vertically,
        // so ignoring either term picks the wrong one.
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
     * A station on another page is not a candidate at all.
     *
     * A page is its own drawing with its own coordinates, so subtracting one page's numbers from
     * another's is arithmetic on unrelated things - and it would win whenever its coordinates happened
     * to be small, offering a station from a diagram nobody is looking at.
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
     * The last-clicked station is used ONCE and then forgotten.
     *
     * "Only prefill the last clicked station for one right click somewhere else - then revert to the
     * distance." Before this it was set whenever a station was clicked and won every label afterwards,
     * anywhere on the diagram, for the rest of the session.
     *
     * Read rather than run: the sequence needs the panel, a session and a graph. What can silently
     * break is the taking becoming a reading again, which is one line and is visible.
     *
     * MUTATION: removing `lastStationTouched = null;` fails the second assertion; putting the nearest
     * lookup before the memory fails the ordering one.
     */
    @Test
    public void testTheLastClickedStationIsSpentAfterOneUse() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/AutonomyEditorPanel.java")),
            java.nio.charset.StandardCharsets.UTF_8);

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
}
