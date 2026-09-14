package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Whether a square has a way through it: the question MT-367 turned on, asked directly at last.
 *
 * **Why this class exists, which is a story about coverage rather than about railways.**  `hasAWayThrough`
 * used to skip the copy it was being asked about.  That is wrong in principle - ask it about a plain
 * copy and it answered "there is no way to stand here without being turned round" while BEING the way
 * through - and SEV-C1 repaired it on 2026-09-13.
 *
 * The validation round after that (SVV-C4) found the repair had nothing that could fail.  Both callers
 * inside `Layout` hand it a copy they have already established is a terminus or a reversing point, and
 * such a copy is never the answer, so skipping it or counting it gives the identical answer at every
 * call the program can make.  The method was private, so no test could ask it either.  A rule that no
 * test can tell apart from its own defect has no coverage, however green the suite is.
 *
 * Adam, 2026-09-13: **"make it public and add the tests."**
 *
 * **The three shapes, which are the three answers the rule has:**
 *
 *   - a PLAIN square answers true about its own copy - the case the old skip got wrong;
 *   - a COMPULSORY turn answers false from every copy - a terminus, or a reversing point with no plain
 *     sibling;
 *   - a MAY-TURN square answers true from either copy, because one of its copies is plain. This is the
 *     case MT-367 was reported against: *"EN57-947 is not allowed to go to BottomMainB, even though it
 *     'may' reverse (is not a terminus)"*, and it is why the flag on a single copy cannot be read for
 *     this - `AutonomyBuilder` emits the turning copy of a may-turn square with `terminus: true`.
 *
 * Built by hand rather than from the frozen railway, because the three shapes have to sit side by side
 * and the middle one - a square whose copies ALL turn - is what a fixture has to state rather than
 * find.
 *
 * @author Adam
 */
public class testWhatHasAWayThrough
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    /** Sensor numbers of this class's own, so it can run beside the other graph suites. */
    private static final int S88_BASE = 8840;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * A plain square is its own way through, which is what the skip got wrong.
     *
     * MUTATION, run 2026-09-13: restoring `if (copy == square) continue;` fails this claim, and the
     * plain-copy half of `testAMayTurnSquareAnswersFromEitherCopy` below. SVV-C4 measured every claim
     * in `testAMayTurnStationIsNotATerminus` and `testTheArrivalHonoursTheAnswer` passing under the same
     * mutation, which is the whole reason this class was written.
     */
    @Test
    public void testAPlainSquareIsItsOwnWayThrough()
    {
        Layout layout = threeShapes();

        Point plain = layout.getPoint("WT Plain");

        assertNotNull(plain, "the fixture did not take: there is no WT Plain");

        assertFalse(plain.isTerminus() || plain.isReversing(),
            "precondition: WT Plain turns trains, so it is not the shape this claim is about");

        assertTrue(layout.hasAWayThrough(plain),
            "a plain square - one that turns nobody - was reported as having no way through it. It is"
            + " itself the way through: a train stands there and leaves the way it was going. Skipping"
            + " the copy being asked about is what produced this answer, and it is the defect SEV-C1"
            + " repaired.");
    }

    /**
     * A square whose every copy turns trains has none.
     *
     * The other end of the rule, and the control for the claim above: without it, a method that always
     * answered true would pass.
     */
    @Test
    public void testACompulsoryTurnHasNoWayThrough()
    {
        Layout layout = threeShapes();

        Point terminus = layout.getPoint("WT Terminus");

        assertNotNull(terminus, "the fixture did not take: there is no WT Terminus");

        assertTrue(terminus.isTerminus(),
            "precondition: WT Terminus is not built as a terminus, so this claim is about some other"
            + " square");

        assertFalse(layout.hasAWayThrough(terminus),
            "a terminus was reported as having a way through it. Every copy of it turns the train"
            + " round, which is what a terminus IS - and a train that cannot reverse is stranded"
            + " there, which is the whole reason this question is asked (MT-367).");
    }

    /**
     * And a may-turn square answers true from the copy that turns, because its sibling does not.
     *
     * This is the case Adam reported MT-367 against, and the reason the flag on one copy cannot answer
     * it: `AutonomyBuilder` emits the turning copy of a square trains MAY turn at with `terminus:
     * true`, exactly like a real terminus. What tells them apart is the other copy.
     */
    @Test
    public void testAMayTurnSquareAnswersFromEitherCopy()
    {
        Layout layout = threeShapes();

        Point turning = layout.getPoint("WT MayTurn (turning)");
        Point plainCopy = layout.getPoint("WT MayTurn");

        assertNotNull(turning, "the fixture did not take: there is no turning copy");
        assertNotNull(plainCopy, "the fixture did not take: there is no plain copy");

        assertTrue(turning.isSamePlaceAs(plainCopy),
            "precondition: the two copies are not recorded as one piece of track, so this railway has"
            + " no may-turn square on it and the claim below is about two unrelated points");

        assertTrue(turning.isTerminus() || turning.isReversing(),
            "precondition: the turning copy does not turn trains, so there is nothing here to tell"
            + " from a compulsory turn");

        assertTrue(layout.hasAWayThrough(turning),
            "asked about the TURNING copy of a square trains may turn at, the railway said there is no"
            + " way through - and there is: the other copy of that square turns nobody. The builder"
            + " emits this copy with terminus:true exactly as it emits a real terminus, so reading the"
            + " flag on one copy is what MT-367 was reported against.");

        assertTrue(layout.hasAWayThrough(plainCopy),
            "and asked about the plain copy of the same square it said no as well, which is the first"
            + " claim in this class arriving by a different road");
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * The three shapes side by side: a plain square, a compulsory turn, and one trains may turn at.
     *
     * The may-turn square is two Points sharing a `block`, which is how the builder says two copies
     * are one piece of track - one of them a turning copy, the other plain.
     *
     * @return the graph
     */
    private static Layout threeShapes()
    {
        String config = ("{'points': ["
            + "{'name': 'WT Start', 'station': true, 's88': " + (S88_BASE) + "},"
            + "{'name': 'WT Plain', 'station': true, 's88': " + (S88_BASE + 1) + "},"
            + "{'name': 'WT Terminus', 'station': true, 's88': " + (S88_BASE + 2)
            + ", 'terminus': true},"
            + "{'name': 'WT MayTurn', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'block': 'wt-may'},"
            + "{'name': 'WT MayTurn (turning)', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'terminus': true, 'block': 'wt-may'}"
            + "],'edges': ["
            + "{'start': 'WT Start', 'end': 'WT Plain'},"
            + "{'start': 'WT Plain', 'end': 'WT Terminus'},"
            + "{'start': 'WT Start', 'end': 'WT MayTurn'},"
            + "{'start': 'WT Start', 'end': 'WT MayTurn (turning)'}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');

        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration produced no graph");

        assertTrue(layout.isValid(),
            "precondition: the test graph must parse - " + Layout.getLastError());

        return layout;
    }
}
