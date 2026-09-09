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
import org.traincontrol.gui.ArrivalSidePrompt;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A train put down by hand on a CURVED station has its tail recorded on a side no track enters by
 * (REV9-B3).
 *
 * **FIXED 2026-09-08, and this class was written RED first.**
 * `docs/reviews-2026-09-09/REV-reversal-mechanics-review.md` B3 was found by reading, and a defect
 * found by reading is a claim until something executes it - so this ran against the unrepaired door
 * and failed in its own words, on exactly the two methods about the curve, while the two CONTROL
 * methods over a straight platform passed.  That split is what makes it a statement about the curve
 * rather than about this test.  `tests-red-before-green`.
 *
 * MUTATION, 2026-09-08: put `forPlacement`'s ordinary-station branch back to `opposite(facing)` and
 * `testTheCurvedPlatformIsAnsweredWithASideTheBuildUses` and `testTheTailIsBlockedBehindTheCurvedPlatform`
 * fail again, in these words - "recorded as arriving from \"W\", and the build enters that square only
 * by [N, E]", and "blocks no rail touching the square it is standing on ... what is blocked is []".
 * The other three stay green.
 *
 * **The defect that was.**  `ArrivalSidePrompt.forPlacement` answered an ordinary (non-may-reverse)
 * station with the compass OPPOSITE of the train's facing, on the reasoning that a train faces the way
 * it will leave and arrived from behind.  That is true on a straight and false on a curve.  OB-182 moved the offered
 * sides, the written arrival sides and the tail walk's comparison onto the BUILD's entry sides -
 * `Layout.entrySideOf` - precisely because compass and metal differ on a curve; the non-may-reverse
 * branch of `forPlacement` was not swept with them.
 *
 * On `curve-into-platform`'s CurvedPlatform the build enters the square by N and by E.  The copy
 * `CurvedPlatform (southbound)` faces E, so the door recorded `arrivedFrom = "W"` - a side the build
 * enters that square by nowhere.  `Layout.edgesCoveredByStandingTrains` then matched no candidate on
 * its first hop and took the `segment == null -> break` exit, whose comment attributes that state to
 * "a stale value after an edit".  The placement door was manufacturing it every time, and the track
 * behind a standing train was silently left open.  Measured, not argued: with the door unrepaired a
 * 4-length train on that square blocked `[]`.
 *
 * **Why it needed a new fixture.**  Every tail test in this suite ran on a straight chain of ordinary
 * points - `testATrainCoversTheTrackBehindIt` is all "E" and "W" on one row - where the two vocabularies
 * agree and the bug cannot appear.  `test/README.md` recorded that blind spot as MON-C17 before this
 * defect walked through it.  `test/layouts/curve-into-platform` is the shape, and this is its first use.
 *
 * **The fix**, which is the one already made twice at the sibling sites: the sides are given and the
 * FACING chooses between them, rather than the compass answering on its own.  `ArrivalSidePrompt`
 * gained `arrivedFrom`, which takes the one side of the square the train is not pointing at; where
 * several are left behind it - three ways in - the documented compass assumption picks between them,
 * but only where the build really does enter by that side, and otherwise nothing is recorded at all.
 * A null arrival side narrows the tail walk; a wrong one sends it down track the train is not on.
 * `behaviour.md` §4 states the rule in those terms.
 *
 * Nothing here changed when it landed - these assertions are what correct behaviour looks like, and
 * they were written before it did.
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
     * Every square the scenario draws, so lengths can be given to all of them in one place.
     *
     * Lengths are NOT in the fixture - that is the half Adam asked to be able to vary - so a test that
     * needs measured track says so itself.  One unit each: the tail walk only needs the segments to be
     * determinate, and equal lengths make the arithmetic in the assertions readable.
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
     * The fixture really is curved, and the straight one really is straight.
     *
     * Asserted first because everything below is a statement about a difference between two squares, and
     * a fixture where the difference has quietly gone is a fixture where both halves pass for the wrong
     * reason.  `assert-the-variable-not-the-control`.
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

        // AND THE DOOR IS ON ITS ORDINARY-STATION BRANCH FOR BOTH, which is the branch B3 is about.
        // A square marked "trains may turn here" would be asked about instead, and this test would be
        // measuring a dialog rather than the assumption.
        assertFalse(scenario.getSession().mayTurnTiles().contains(scenario.tile(CURVED[0], CURVED[1])),
            "the curved platform has become a may-reverse square, so the placement door would put a"
            + " question to the operator instead of assuming - which is not the branch REV9-B3 is in");

        assertFalse(scenario.getSession().mayTurnTiles()
            .contains(scenario.tile(STRAIGHT[0], STRAIGHT[1])),
            "the control platform has become a may-reverse square, so it is no longer a control for"
            + " the same branch");
    }

    /**
     * THE CONTROL. On a straight platform the door answers with a side the build actually uses.
     */
    @Test
    public void testTheStraightPlatformIsAnsweredWithASideTheBuildUses() throws Exception
    {
        answersWithABuildSide(STRAIGHT, "the straight platform");
    }

    /**
     * THE SUBJECT. On the curved platform it does not - REV9-B3.
     */
    @Test
    public void testTheCurvedPlatformIsAnsweredWithASideTheBuildUses() throws Exception
    {
        answersWithABuildSide(CURVED, "the curved platform");
    }

    /**
     * THE CONTROL, in behaviour rather than in labels: the rail behind a straight platform is blocked.
     */
    @Test
    public void testTheTailIsBlockedBehindAStraightPlatform() throws Exception
    {
        blocksTheRailBehind(STRAIGHT, "the straight platform");
    }

    /**
     * THE SUBJECT, and the failure REV9-B3 names: nothing at all is blocked behind the curved one.
     */
    @Test
    public void testTheTailIsBlockedBehindTheCurvedPlatform() throws Exception
    {
        blocksTheRailBehind(CURVED, "the curved platform");
    }

    /**
     * Puts a train down on every copy of a square through the placement door and checks the answer is a
     * side the build enters that square by.
     *
     * Every copy, not one: a split square has one per arrival side, the door is asked about whichever
     * the train is standing on, and a rule that holds for one of them and not the other is the defect.
     *
     * @param square the platform, as x and y
     * @param what what to call it in a failure message
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

            // EXACTLY THE APPLICATION'S OWN CALL, argument for argument - TrainControlUI.java:6515.
            // A test that assembled the arguments differently would be measuring its own arithmetic.
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
     * Stands a long train on a platform, with the tail recorded exactly as the placement door would
     * record it, and checks the rail it lies across is blocked.
     *
     * The behavioural half of the same rule.  A label that names a side nothing enters by is a tidy
     * defect; this is what it COSTS - a protection that reports itself as present and does nothing.
     *
     * @param square the platform
     * @param what what to call the platform in a failure message
     */
    private void blocksTheRailBehind(int[] square, String what) throws Exception
    {
        Layout built = scenario.build();

        TileKey tile = scenario.tile(square[0], square[1]);

        MarklinLocomotive loc = scenario.getModel().getLocByName(LOC);

        assertNotNull(loc, "the test's own locomotive is gone");

        // FOUR, against segments of two: long enough to reach past the neighbouring station, which is
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
     * Whether the tail walk blocked any rail that actually touches the square the train is on.
     *
     * Stronger than "something was blocked": a covered set that names some other part of the railway
     * would be an answer to a different question, and the point of the walk is the metal this train is
     * lying across.
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
     * The sides the build enters a square by, printed.
     *
     * As text rather than as a set, because `arrivalSides` comes back in a fixed order and comparing
     * two sets through TestNG's collection overload compares iteration order anyway - so the string is
     * the honest form of the comparison being made.
     *
     * @param square a square as x and y
     * @return the sides, e.g. "[N, E]"
     */
    private String sidesText(int[] square)
    {
        return scenario.getSession().arrivalSides(scenario.tile(square[0], square[1])).toString();
    }

    /**
     * Takes every train off the railway, so one method's placement cannot answer the next one's.
     */
    private void clearEveryPlatform(Layout built)
    {
        for (Point point : built.getPoints())
        {
            point.setLocomotive(null);
            point.setArrivedFrom(null);
        }
    }

    /**
     * @return the names of every edge covered by a standing train's tail
     */
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
