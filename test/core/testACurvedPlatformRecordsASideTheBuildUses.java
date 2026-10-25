package core;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomy.ArrivalSidePrompt;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A train put down by hand on a CURVED station has its tail recorded on a side no track enters by.
 *
 * Found by reading, so this was written RED first: against the unrepaired door it failed in its own words on exactly
 * the two methods about the curve, while the two CONTROL methods over a straight platform passed, which is what makes
 * it a statement about the curve rather than about this test.
 *
 * MUTATION: put `forPlacement`'s ordinary-station branch back to `opposite(facing)` and
 * `testTheCurvedPlatformIsAnsweredWithASideTheBuildUses` and `testTheTailIsBlockedBehindTheCurvedPlatform` fail, in
 * these words - "recorded as arriving from \"W\", and the build enters that square only by [N, E]", and "blocks no rail
 * touching the square it is standing on ... what is blocked is []".  The other three stay green.
 *
 * `ArrivalSidePrompt.forPlacement` answered an ordinary (non-may-reverse) station with the compass OPPOSITE of the
 * train's facing, which is true on a straight and false on a curve.  OB-182 moved the offered sides, the written
 * arrival sides and the tail walk's comparison onto the BUILD's entry sides (`Layout.entrySideOf`) because compass and
 * metal differ on a curve; this branch was not swept with them.  On `curve-into-platform`'s CurvedPlatform the build
 * enters the square by N and by E, and `CurvedPlatform (southbound)` faces E, so the door recorded `arrivedFrom = "W"`,
 * a side the build enters it by nowhere.  `Layout.edgesCoveredByStandingTrains` then matched no candidate on its first
 * hop and took the `segment == null -> break` exit, so the track behind a standing train was silently left open (a
 * 4-length train blocked `[]`).
 *
 * It needed a new fixture: every other tail test ran on a straight chain of ordinary points, where the two vocabularies
 * agree.  `test/layouts/curve-into-platform` is the shape.
 *
 * The rule: the sides are given and the FACING chooses between them, rather than the compass answering on its own.
 * `ArrivalSidePrompt.arrivedFrom` takes the one side of the square the train is not pointing at; where several are
 * left, the documented compass assumption picks between them, but only where the build really enters by that side, and
 * otherwise nothing is recorded.  A null arrival side narrows the tail walk; a wrong one sends it down track the train
 * is not on (`behaviour/facing.md` 4).
 *
 * @author Adam
 */
public class testACurvedPlatformRecordsASideTheBuildUses
{
    private static support.Scenario scenario;

    /** The station approached round a curve: the build enters it by N and by E. */
    private static final int[] CURVED = {5, 5};

    /** The control: an ordinary station on a straight row, where compass and metal agree. */
    private static final int[] STRAIGHT = {5, 8};

    /**
     * Every square the scenario draws, so lengths can be given to all of them in one place.  Lengths are NOT in the
     * fixture, which Adam wants to vary; one unit each, so the assertions' arithmetic is readable.
     */
    private static final int[][] SQUARES =
    {
        {5, 2}, {5, 3}, {5, 4}, {5, 5}, {6, 5}, {7, 5}, {8, 5}, {9, 5}, {10, 5},
        {2, 8}, {3, 8}, {4, 8}, {5, 8}, {6, 8}, {7, 8}, {8, 8}
    };

    private static final String LOC = "SM curve tail";

    private static final int ADDRESS = 71;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("curve-into-platform");

        scenario.getModel().stop();

        for (int[] square : SQUARES)
        {
            scenario.getSession().setTileLength(scenario.tile(square[0], square[1]), 1);
        }

        scenario.getModel().newMM2Locomotive(LOC, ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null)
        {
            try { scenario.getModel().deleteLoc(LOC); } catch (Exception ignored) { }

            scenario.close();
        }
    }

    /**
     * Asserted first because everything below is a statement about a difference between two squares, and a fixture
     * where the difference has quietly gone passes both halves for the wrong reason.
     */
    @Test
    public void testTheFixtureHasACurvedStationAndAStraightOne() throws Exception
    {
        scenario.build();

        assertEquals(sidesText(CURVED), "[N, E]",
            "the curved platform is supposed to be entered by N and by E - the whole point of this"
            + " fixture is that its two sides are at right angles, so that the compass opposite of a"
            + " facing is not one of them. It is now entered by " + sidesText(CURVED));

        assertEquals(sidesText(STRAIGHT), "[E, W]",
            "the control platform is supposed to be an ordinary straight, entered by E and by W, so"
            + " that the compass opposite of a facing IS one of its sides. It is now entered by "
            + sidesText(STRAIGHT));

        // AND THE DOOR IS ON ITS ORDINARY-STATION BRANCH FOR BOTH: a square marked "trains may turn here" would be
        // asked about instead, and this test would be measuring a dialog.
        assertFalse(scenario.getSession().mayTurnTiles().contains(scenario.tile(CURVED[0], CURVED[1])),
            "the curved platform has become a may-reverse square, so the placement door would put a"
            + " question to the operator instead of assuming - which is not the branch REV9-B3 is in");

        assertFalse(scenario.getSession().mayTurnTiles()
            .contains(scenario.tile(STRAIGHT[0], STRAIGHT[1])),
            "the control platform has become a may-reverse square, so it is no longer a control for"
            + " the same branch");
    }

    /** THE CONTROL. On a straight platform the door answers with a side the build actually uses. */
    @Test
    public void testTheStraightPlatformIsAnsweredWithASideTheBuildUses() throws Exception
    {
        answersWithABuildSide(STRAIGHT, "the straight platform");
    }

    /** THE SUBJECT. On the curved platform it does not. */
    @Test
    public void testTheCurvedPlatformIsAnsweredWithASideTheBuildUses() throws Exception
    {
        answersWithABuildSide(CURVED, "the curved platform");
    }

    /** THE CONTROL, in behavior rather than in labels: the rail behind a straight platform is blocked. */
    @Test
    public void testTheTailIsBlockedBehindAStraightPlatform() throws Exception
    {
        blocksTheRailBehind(STRAIGHT, "the straight platform");
    }

    /** THE SUBJECT, and the failure: nothing at all is blocked behind the curved one. */
    @Test
    public void testTheTailIsBlockedBehindTheCurvedPlatform() throws Exception
    {
        blocksTheRailBehind(CURVED, "the curved platform");
    }

    /**
     * Puts a train down on every copy of a square through the placement door and checks the answer is a side the build
     * enters that square by.  Every copy, not one: a split square has one per arrival side, and a rule that holds for
     * one and not the other is the defect.
     */
    private void answersWithABuildSide(int[] square, String what) throws Exception
    {
        Layout built = scenario.build();

        TileKey tile = scenario.tile(square[0], square[1]);

        Set<Side> buildSides = new LinkedHashSet<>(scenario.getSession().arrivalSides(tile));

        Map<String, Side> facings = scenario.getSession().facingsFor(tile);

        assertFalse(facings.isEmpty(), what + " built to no copies at all, so nothing was asked");

        for (Map.Entry<String, Side> copy : facings.entrySet())
        {
            Point point = built.getPoint(copy.getKey());

            assertNotNull(point, what + " has a copy called " + copy.getKey()
                + " that the built layout does not hold");

            // EXACTLY THE APPLICATION'S OWN CALL, argument for argument: a test that assembled the arguments
            // differently would be measuring its own arithmetic.
            String recorded = ArrivalSidePrompt.forPlacement(built, point, copy.getValue().name(),
                false, null, scenario.getSession().arrivalSides(tile));

            assertNotNull(recorded, what + " copy " + copy.getKey()
                + " was answered with nothing, so no tail is recorded at all");

            assertTrue(buildSides.contains(Side.valueOf(recorded)),
                "REV9-B3: a train placed by hand on " + what + " (" + copy.getKey() + ", facing "
                + copy.getValue() + ") has its tail recorded as arriving from \"" + recorded
                + "\", and the build enters that square only by " + buildSides + ". The door takes the"
                + " COMPASS opposite of the facing, which is right on a straight and wrong on a curve -"
                + " the side it names has no track on it, so the tail walk in"
                + " Layout.edgesCoveredByStandingTrains matches nothing and blocks nothing. The fix is"
                + " the one already made at the sibling sites: the other of the square's build arrival"
                + " sides, not the compass opposite.");
        }
    }

    /**
     * Stands a long train on a platform, with the tail recorded exactly as the placement door would record it, and
     * checks the rail it lies across is blocked.  This is what the wrong label COSTS: a protection that reports itself
     * as present and does nothing.
     */
    private void blocksTheRailBehind(int[] square, String what) throws Exception
    {
        Layout built = scenario.build();

        TileKey tile = scenario.tile(square[0], square[1]);

        MarklinLocomotive loc = scenario.getModel().getLocByName(LOC);

        assertNotNull(loc, "the test's own locomotive is gone");

        // FOUR, against segments of two: long enough to reach past the neighboring station, which is
        // the shape of Adam's own example ("bottommainc should currently be blocked since a train of
        // length 4 is standing at bottommainb").
        loc.setTrainLength(4);

        Map<String, Side> facings = scenario.getSession().facingsFor(tile);

        int tried = 0;

        for (Map.Entry<String, Side> copy : facings.entrySet())
        {
            Point point = built.getPoint(copy.getKey());

            if (point == null) continue;

            String recorded = ArrivalSidePrompt.forPlacement(built, point, copy.getValue().name(),
                false, null, scenario.getSession().arrivalSides(tile));

            clearEveryPlatform(built);

            point.setLocomotive(loc);
            point.setArrivedFrom(recorded);

            tried++;

            Set<String> covered = coveredNames(built);

            assertTrue(coversAnEdgeAt(built, point),
                "REV9-B3: a train of length 4 standing on " + what + " (" + copy.getKey()
                + ", facing " + copy.getValue() + "), with its tail recorded the way the placement"
                + " door records it - \"" + recorded + "\" - blocks no rail touching the square it is"
                + " standing on. The build enters that square by " + sidesText(square)
                + "; what is blocked is " + covered + ". The recorded side names no side the build"
                + " enters that square by, so the walk's first hop in"
                + " Layout.edgesCoveredByStandingTrains matches no candidate and breaks at once. The"
                + " track behind a standing train is left open, and the picture says it is"
                + " protected.");
        }

        assertTrue(tried > 0, what + " built to no copies, so this test placed no train and asserted"
            + " nothing");
    }

    /**
     * Whether the tail walk blocked any rail that actually touches the square the train is on: stronger than "something
     * was blocked", since a covered set naming some other part of the railway would answer a different question.
     */
    private boolean coversAnEdgeAt(Layout built, Point point)
    {
        Set<String> covered = coveredNames(built);

        for (Edge edge : built.getNeighborsAndIncoming(point))
        {
            if (covered.contains(edge.getName())) return true;
        }

        return false;
    }

    /**
     * The sides the build enters a square by, printed, e.g. "[N, E]": as text rather than a set, because TestNG's
     * collection overload compares iteration order anyway.
     */
    private String sidesText(int[] square)
    {
        return scenario.getSession().arrivalSides(scenario.tile(square[0], square[1])).toString();
    }

    /** Takes every train off the railway, so one method's placement cannot answer the next one's. */
    private void clearEveryPlatform(Layout built)
    {
        for (Point point : built.getPoints())
        {
            point.setLocomotive(null);
            point.setArrivedFrom(null);
        }
    }

    /** The names of every edge covered by a standing train's tail. */
    private Set<String> coveredNames(Layout built)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Map.Entry<Edge, Locomotive> entry : built.edgesCoveredByStandingTrains().entrySet())
        {
            out.add(entry.getKey().getName());
        }

        return out;
    }

}
