package core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;

/**
 * Mass Assign Lengths and the Unmeasured Track display: which squares a length rule reads, and how a stretch's whole
 * length is shared out over them.
 *
 * Adam, 2026-09-16: *"per stretch, every relevant square a rule reads. build it.  let's have: the 'mass assign lengths'
 * feature in the right click menu that cycles through each relevant square.  and a display option to statically
 * highlight all relevant unmeasured squares."*
 *
 * **Which squares a rule reads**, from the rules rather than from a summary of them.  The room rule
 * (`Layout.measuredRoomAtTheEndOf`) and the FR-087 allowance (`Layout.measuredRouteIn`) both walk back from where a train
 * comes to rest, leg by leg, and stop at the last switch; the tail walk stops at a fork, which is the same place.  A leg
 * that crosses no switch does not stop them - they go on through the sensor behind it onto the leg before (AMR-B3).  So
 * the relevant squares are, on every approach to every square a train can come to rest on - a station, a parking berth,
 * a turn-round square - the track from that square back to the nearest switch.  The switch tile itself is not room and
 * is not asked for.
 *
 * **Per stretch, as Adam chose.**  A stretch is one leg's share of that: the squares between the square it arrives at and
 * the switch, or between two sensors.  He types its whole length once and it is shared evenly over its unmeasured
 * squares.  The trade-off was put to him first: the room rule needs only the total, but the tail and berth rules read
 * where each unit lies, so an even share is an approximation - and any remainder goes to the squares FARTHEST from where
 * the train rests, which makes a tail reach further back rather than less far, the refusing direction.
 *
 * Every railway here is its own, built in memory; none is shared with another test.
 *
 * @author Adam
 */
public class testMassAssignLengths
{
    private File folder;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        folder = Files.createTempDirectory("tc-mass-assign-lengths").toFile();
        session = new AutonomySession(folder);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(folder);
    }

    // ------------------------------------------------------------------------------------------------ which squares

    /**
     * A parking berth nobody turns round at still has its stretch asked for - and nothing a rule does not read is.
     *
     * The gap this feature closes.  The existing notice asks only where trains turn round, because it was written when
     * the room rule ran only at reversals; since MT-262 it runs at every destination, so a parking berth's approach was
     * read by the rule and asked for by nothing.
     *
     * The second half is the one that stops "highlight every square" passing: the switch, the track on the far side of
     * it, and the branch are not read by any length rule for this berth, and must not be asked for.
     */
    @Test
    public void testAParkingBerthNobodyTurnsAtHasItsStretchAskedFor() throws IOException
    {
        TileKey berth = key(5, 1);

        openBerthBehindASwitch(berth);

        assertFalse(session.isTurnAround(berth),
            "precondition: trains turn round at the berth, so the old notice could be what finds it");

        List<AutonomySession.Stretch> needing = session.stretchesNeedingALength();

        assertEquals(needing.size(), 1, "one stretch leads into the berth from the switch; found " + describe(needing));

        assertEquals(new HashSet<>(needing.get(0).getTiles()), set(key(5, 1), key(4, 1)),
            "the stretch into the berth is the berth and the track between it and the switch");

        assertEquals(needing.get(0).getRestsAt(), berth, "the stretch should say which square it leads to");

        Set<TileKey> highlighted = session.squaresNeedingALength();

        assertEquals(highlighted, set(key(5, 1), key(4, 1)),
            "the squares to highlight are exactly the unmeasured squares a length rule reads for this berth");

        for (TileKey unread : Arrays.asList(key(3, 1), key(2, 1), key(1, 1), key(3, 0)))
        {
            assertFalse(highlighted.contains(unread), unread + " was asked for, but no length rule reads it: the switch"
                + " is not room, and the far side and the branch lie behind the switch the room rule stops at");
        }
    }

    /**
     * A leg with no switch in it does not end the stretch: the rules walk on through the sensor behind (AMR-B3).
     *
     * A - B - C in a line with no switch, and C the only station.  `measuredRoomAtTheEndOf` sums the leg into C and then
     * carries on over the leg into B, because nothing divides them - so both legs are read, and both are asked for.  A
     * itself is not: it is where the second leg starts, and a leg's length is its track and the square it arrives at.
     */
    @Test
    public void testAStretchWithNoSwitchRunsBackThroughTheSensorBehindIt() throws IOException
    {
        session.open(Arrays.asList(threeSensorsInALine()));
        session.initialize("Lengths");
        session.setStation(key(5, 1), true);
        session.rebuild();

        assertEquals(session.squaresNeedingALength(), set(key(5, 1), key(4, 1), key(3, 1), key(2, 1)),
            "the room at C is read back over both legs, through B, because no switch divides them");

        assertEquals(session.stretchesNeedingALength().size(), 2,
            "each leg is its own stretch - one between B and C, one between A and B - so each is measured as one run: "
            + describe(session.stretchesNeedingALength()));
    }

    /**
     * One run of track that is the approach to a station at each end is ONE stretch, not two.
     *
     * Asked twice it would be typed twice, and the second prompt would be about squares the first had just filled.
     */
    @Test
    public void testBothWaysAlongOneRunAreOneStretch() throws IOException
    {
        session.open(Arrays.asList(runBetweenTwoStations()));
        session.initialize("Lengths");
        session.setStation(key(1, 1), true);
        session.setStation(key(4, 1), true);
        session.rebuild();

        List<AutonomySession.Stretch> needing = session.stretchesNeedingALength();

        assertEquals(needing.size(), 1, "the one run between the two stations was asked for more than once: "
            + describe(needing));

        assertEquals(new HashSet<>(needing.get(0).getTiles()), set(key(1, 1), key(2, 1), key(3, 1), key(4, 1)));
    }

    /**
     * A measured square stops being asked for, and a fully measured stretch stops being a stretch to measure.
     */
    @Test
    public void testOnlyTheUnmeasuredSquaresAreStillAskedFor() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setTileLength(key(4, 1), 3);

        assertEquals(session.squaresNeedingALength(), set(key(5, 1)),
            "a square with a length is still highlighted, or the one without is not");

        assertEquals(session.stretchesNeedingALength().size(), 1,
            "a stretch with one square still unmeasured must still be asked for");

        session.setTileLength(key(5, 1), 2);

        assertTrue(session.squaresNeedingALength().isEmpty(), "a fully measured railway still has squares highlighted");
        assertTrue(session.stretchesNeedingALength().isEmpty(), "a fully measured stretch is still asked for");

        assertEquals(session.stretchesALengthRuleReads().size(), 1,
            "control: the stretch has not stopped being READ just because it is measured");
    }

    /**
     * The walk is per page: a stretch on another page is not asked for from this one.
     */
    @Test
    public void testTheWalkAsksOnlyAboutThisPage() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        assertEquals(session.stretchesNeedingALengthOn("main").size(), 1, "the stretch on this page was not offered");
        assertTrue(session.stretchesNeedingALengthOn("elsewhere").isEmpty(),
            "a page with no track on it was offered a stretch from another page");
    }

    // ------------------------------------------------------------------------------------------------ sharing it out

    /**
     * The whole length is shared evenly over the unmeasured squares, and the remainder goes FARTHEST from the berth.
     *
     * Seven units over the berth and the square behind it: three each and one over.  The one over goes to 4,1, the
     * square farther from where the train rests - so a tail walked back from the berth spends less on the first square
     * and reaches further, which is the refusing direction for the berth rule rather than the admitting one.
     */
    @Test
    public void testTheWholeLengthIsSharedOverTheUnmeasuredSquares() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        AutonomySession.Stretch stretch = session.stretchesNeedingALength().get(0);

        assertTrue(session.assignStretchLength(stretch, 7), "a whole length the stretch can hold was refused");

        assertEquals(length(5, 1) + length(4, 1), 7, "the shares do not add up to the whole length that was typed");

        assertEquals(length(4, 1), 4, "the unit over should go to the square farther from the berth");
        assertEquals(length(5, 1), 3, "the berth itself should get the even share");

        assertTrue(session.stretchesNeedingALength().isEmpty(), "a stretch that has just been measured is still asked for");
    }

    /**
     * A square already measured keeps its length, and a whole length too short to give every other square one unit is
     * refused with nothing written.
     */
    @Test
    public void testAMeasuredSquareIsKeptAndATooShortLengthIsRefused() throws IOException
    {
        session.open(Arrays.asList(threeSensorsInALine()));
        session.initialize("Lengths");
        session.setStation(key(5, 1), true);
        session.rebuild();

        session.setTileLength(key(4, 1), 5);

        AutonomySession.Stretch intoC = null;

        for (AutonomySession.Stretch stretch : session.stretchesNeedingALength())
        {
            if (stretch.getTiles().contains(key(5, 1))) intoC = stretch;
        }

        assertNotNull(intoC, "the stretch into C is no longer asked for, with C itself still unmeasured");

        assertEquals(session.leastWholeLengthOf(intoC), 6,
            "4,1 already holds 5, and C needs at least one: the least whole length is 6");

        assertFalse(session.assignStretchLength(intoC, 5),
            "a whole length that leaves an unmeasured square at nothing was accepted");

        assertEquals(length(5, 1), 0, "a refused length still wrote something");
        assertEquals(length(4, 1), 5, "a refused length changed a square that was already measured");

        assertTrue(session.assignStretchLength(intoC, 9), "a whole length the stretch can hold was refused");

        assertEquals(length(4, 1), 5, "the square that was already measured was overwritten");
        assertEquals(length(5, 1), 4, "the rest of the whole length should go to the square that had none");
    }

    // ------------------------------------------------------------------------------------------------ the display

    /**
     * The highlight is a display choice, and it marks exactly the squares a rule reads that have no length.
     *
     * Asked of the editor's own `annotationFor`, which is what each square paints from - so this is the highlight a
     * person sees, not a set somebody could forget to draw.  The toggle is set directly rather than clicked, so the test
     * does not write Adam's remembered view settings.
     */
    @Test
    public void testTheHighlightIsADisplayChoiceAndMarksOnlyWhatARuleReads() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        panel.getShowUnmeasured().setSelected(false);

        assertFalse(marked(panel, key(4, 1)), "the highlight showed with the display choice off");

        panel.getShowUnmeasured().setSelected(true);

        assertTrue(marked(panel, key(4, 1)), "the unmeasured square behind the berth is not highlighted");
        assertTrue(marked(panel, key(5, 1)), "the unmeasured berth is not highlighted");
        assertFalse(marked(panel, key(2, 1)), "a square no length rule reads is highlighted");
        assertFalse(marked(panel, key(3, 1)), "the switch is highlighted, and it is not room");
    }

    /**
     * A square whose only mark is "needs a length" still paints (OB-007).
     *
     * `isBlank` decides whether a square is drawn at all, and a mark missing from it is invisible on exactly the squares
     * with nothing else to say - which, for plain track, is nearly all of them.
     */
    @Test
    public void testASquareMarkedOnlyAsUnmeasuredIsNotBlank()
    {
        TileAnnotation plain = new TileAnnotation(null, -1, false);
        TileAnnotation marked = new TileAnnotation(null, -1, false).needsALength();

        assertTrue(plain.isBlank(), "control: an annotation with nothing on it is blank");
        assertFalse(marked.isBlank(), "a square whose only mark is 'needs a length' would never be painted");
        assertNotEquals(marked, plain, "the mark is left out of equals, so a repaint could be skipped as unchanged");
    }

    // ------------------------------------------------------------------------------------------------ the railways

    /**
     * 1,1 sensor - 2,1 - SWITCH 3,1 - 4,1 - 5,1 sensor, with the switch's branch going to a sensor at 3,0 so that it is a
     * real fork.  The given square becomes a parking berth.
     */
    private void openBerthBehindASwitch(TileKey berth) throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.getComponent(3, 1).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, 7, org.traincontrol.base.Accessory.accessoryType.SWITCH, accessoryDecoderType.MM2,
            "Switch 7", false, 0));

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(berth, true);
        session.setAutoDestination(berth, false);
        session.rebuild();
    }

    /** A 1,1 - 2,1 - B 3,1 - 4,1 - C 5,1, sensors at 1, 3 and 5 and plain track between. */
    private LayoutDiagram threeSensorsInALine() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /** A 1,1 - 2,1 - 3,1 - B 4,1: one run of plain track between two sensors. */
    private LayoutDiagram runBetweenTwoStations() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static TileKey key(int x, int y)
    {
        return new TileKey("main", x, y);
    }

    private static Set<TileKey> set(TileKey... keys)
    {
        return new HashSet<>(Arrays.asList(keys));
    }

    private int length(int x, int y)
    {
        return session.getStore().getTileLength(key(x, y));
    }

    private static boolean marked(AutonomyEditorPanel panel, TileKey tile)
    {
        TileAnnotation annotation = panel.annotationFor(tile);

        return annotation != null && annotation.isUnmeasured();
    }

    private static String describe(List<AutonomySession.Stretch> stretches)
    {
        StringBuilder out = new StringBuilder("[");

        for (AutonomySession.Stretch stretch : stretches)
        {
            out.append(stretch.getTiles()).append(" -> ").append(stretch.getRestsAt()).append("; ");
        }

        return out.append("]").toString();
    }

    private static void delete(File file)
    {
        if (file == null) return;

        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children) delete(child);
        }

        file.delete();
    }
}
