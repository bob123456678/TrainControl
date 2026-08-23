package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * The name of a placed locomotive, as the diagram has to draw it.
 *
 * Reported 2026-08-22 with a screenshot: instead of "EN57-203" sitting in the caption in the track
 * diagram's own style, the autonomy editor drew {"name":"EN57-203"} in a box wide enough to cover
 * three neighbouring tiles.
 *
 * A placement is stored as an OBJECT - {"name": ..., "speed": ..., "arrivalFunc": ...} - because
 * parseAuto resets whatever a placement omits, so the extra settings have to travel with it. The
 * label asked for the property and called String.valueOf on what came back, which for a JSONObject
 * is its JSON. Correct for a string, and the property has never been one.
 *
 * The unwrapping already existed, twice: AutonomyEditorPanel.locomotiveAt did it right, and
 * Layout.parseAuto does it right with an extra guard. The label was a third copy that got it wrong,
 * so the fix puts the shape in one place - here - and has all three ask.
 *
 * What this pins is that asking the session for a name yields a NAME. Anything drawing a placement
 * goes through this method, so a regression shows up here rather than in a screenshot.
 *
 * @author Adam
 */
public class testAutonomyLabelShowsLocomotiveName
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-autonomy-label").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * The whole bug, in one assertion.
     */
    @Test
    public void testAPlacedLocomotiveReadsBackAsItsName() throws IOException
    {
        TileKey tile = placedAt("EN57-203");

        assertEquals(session.getLocomotiveNameAt(tile), "EN57-203",
            "the placement was rendered rather than read - this is the JSON the editor drew over "
            + "the diagram");
    }

    /**
     * And the settings that travel with the placement are not disturbed by reading it.
     *
     * The temptation when fixing this is to store the name as a bare string, which would make the
     * label right and quietly reset a train's length and functions on the next build. Nothing about
     * how it is READ may change how it is stored.
     */
    @Test
    public void testReadingTheNameLeavesTheRestOfThePlacementAlone() throws IOException
    {
        TileKey tile = placedAt("V 200 150");

        session.setPointProperty(tile, "loc",
            new org.json.JSONObject(session.getPointProperty(tile, "loc").toString())
                .put("arrivalFunc", 15).put("trainLength", 240));

        assertEquals(session.getLocomotiveNameAt(tile), "V 200 150", "the name");

        org.json.JSONObject stored =
            (org.json.JSONObject) session.getPointProperty(tile, "loc");

        assertEquals(stored.getInt("arrivalFunc"), 15, "the arrival function was lost");
        assertEquals(stored.getInt("trainLength"), 240, "the train length was lost");
    }

    /**
     * An empty square says so, rather than saying "null".
     *
     * String.valueOf(null) is the four characters n-u-l-l, and a label given those draws them. The
     * old code guarded that with an explicit null check; a rewrite that unwraps the object has to
     * keep the guard, and this is what notices if it does not.
     */
    @Test
    public void testAnEmptySquareHasNoName() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        assertNull(session.getLocomotiveNameAt(new TileKey("main", 1, 1)),
            "nothing is placed here, so there is no name to draw");

        assertNull(session.getLocomotiveNameAt(null), "no square at all");
    }

    /**
     * A placement somebody hand-edited into a bare string still reads.
     *
     * Not a shape this program writes, but setup files get edited by hand and an older autonomy.json
     * may carry one. Drawing nothing there would look exactly like an empty platform, which is the
     * one wrong answer a label can give about a placement.
     */
    @Test
    public void testAHandWrittenBareNameStillReads() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey tile = new TileKey("main", 1, 1);

        session.setPointProperty(tile, "loc", "BR 218");

        assertEquals(session.getLocomotiveNameAt(tile), "BR 218",
            "a bare name is still a name");
    }

    // ------------------------------------------------------------------------------------------

    private TileKey placedAt(String name) throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey tile = new TileKey("main", 1, 1);

        session.placeLocomotive(tile, name);

        // The bug is in the READING, so the test has to know the writing really did store an object -
        // otherwise a test that passes proves only that the shape changed underneath it.
        assertTrue(session.getPointProperty(tile, "loc") instanceof org.json.JSONObject,
            "a placement is stored as an object, and this test is about unwrapping one");

        return tile;
    }

    private LayoutDiagram runOfTrack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    private void delete(File f)
    {
        if (f.isDirectory())
        {
            File[] kids = f.listFiles();

            if (kids != null) for (File kid : kids) delete(kid);
        }

        f.delete();
    }
}
