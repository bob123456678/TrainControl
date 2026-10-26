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
 * Instead of "EN57-203" in the caption in the track diagram's own style, the autonomy editor drew
 * {"name":"EN57-203"} in a box wide enough to cover three neighboring tiles.  A placement is stored as an OBJECT
 * ({"name": ..., "speed": ..., "arrivalFunc": ...}) because parseAuto resets whatever a placement omits, and the label
 * called String.valueOf on what came back.  The unwrapping already existed twice (AutonomyEditorPanel.locomotiveAt and
 * Layout.parseAuto); the label was a third copy that got it wrong, so the shape is now in one place and all three ask.
 *
 * What this pins is that asking the session for a name yields a NAME.
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

    /** The whole bug, in one assertion. */
    @Test
    public void testAPlacedLocomotiveReadsBackAsItsName() throws IOException
    {
        TileKey tile = placedAt("EN57-203");

        assertEquals(session.getLocomotiveNameAt(tile), "EN57-203",
            "the placement was rendered rather than read - this is the JSON the editor drew over "
            + "the diagram");
    }

    /**
     * And the settings that travel with the placement are not disturbed by reading it: storing the name as a bare
     * string would quietly reset a train's length and functions on the next build.
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
     * An empty square says so, rather than saying "null": String.valueOf(null) is four characters and a label given
     * those draws them.
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
     * A placement somebody hand-edited into a bare string still reads; drawing nothing would look exactly like an empty
     * platform.
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

    /**
     * A caption is bracketed and cut to length, whatever is standing on the platform: the editor set the NAME where the
     * running diagram sets a CAPTION, which is cut to LAYOUT_STATION_MAX_LENGTH so a platform is the same width
     * whatever is on it.
     */
    @Test
    public void testACaptionIsJustTheNameCutToLength()
    {
        // No brackets since FR-028: they were the caption's SHAPE, and the pill is its shape now.
        assertEquals(org.traincontrol.gui.LayoutGrid.stationCaption("EN57-203", ""), "EN57-203",
            "a caption is the name and nothing else - the brackets went with the pill that replaced "
            + "what they were doing");

        String longName = "065 001-0 DB Baureihe";

        String caption = org.traincontrol.gui.LayoutGrid.stationCaption(longName, "");

        assertTrue(caption.length() <= org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_MAX_LENGTH,
            "a caption must not grow with the name - that is what covered the neighbouring tiles, and "
            + "it covers more of them now that the name is drawn on an oval. Got " + caption);

        assertTrue(longName.startsWith(caption),
            "what survives the cut is the beginning of the name: " + caption);
    }

    /** The facing arrow lives INSIDE the brackets, and is paid for out of the same width. */
    @Test
    public void testTheFacingArrowIsPartOfTheCaption()
    {
        assertEquals(org.traincontrol.gui.LayoutGrid.stationCaption("EN57-203", " \u25BA"),
            "EN57-203 \u25BA",
            "the arrow belongs at the end of the caption, after the name");

        assertEquals(org.traincontrol.gui.LayoutGrid.stationCaption("EN57-203", null), "EN57-203",
            "no facing recorded is not the word null");
    }

    /**
     * An empty platform is the placeholder, spelled by the same method so the two cannot drift into different widths.
     */
    @Test
    public void testAnEmptyPlatformIsThePlaceholder()
    {
        assertEquals(org.traincontrol.gui.LayoutGrid.stationCaption(null, " >"),
            org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY,
            "nothing standing there is the placeholder, arrow or no arrow");
    }

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
