package core;

import java.util.LinkedHashSet;
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

/**
 * A turnout, and the two routes that share its metal.
 *
 * **The suite has never had one.**  `test/README.md`'s survey of the fixtures (MON-C17 to C21) found
 * that every hand-built fixture is a straight chain of two or three ordinary points: no switch, no
 * curve, no split square.  So the whole of the lock-edge machinery - the rule that two routes over one
 * piece of metal are mutually exclusive, which is what stops a route being cleared under a train
 * standing on the other arm - was exercised only against the operator's live railway, whose ground
 * truth moves as he operates it.
 *
 * `test/layouts/single-switch` is a checked-in railway of exactly that shape, and this is what it is
 * for.  Four sensors: a west end, an approach, and the two platforms the switch divides.  The approach
 * splits into two Points - trains arrive at it from both sides - which is the other thing no hand-built
 * fixture in this suite has ever produced.
 *
 * **The lengths are not here.**  Nothing in the fixture folder gives a tile a length, on purpose: Adam
 * asked for topology in the files and lengths in code, so a test that needs measured track measures it
 * itself and no two tests inherit one author's numbers.
 *
 * @author Adam
 */
public class testTwoRoutesShareOneSwitch
{
    private static support.Scenario scenario;

    private static final String MAIN = "Approach (eastbound) -> MainPlatform";

    private static final String BRANCH = "Approach (eastbound) -> BranchPlatform";

    private static final String MAIN_BACK = "MainPlatform -> Approach (westbound)";

    private static final String BRANCH_BACK = "BranchPlatform -> Approach (westbound)";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("single-switch");

        scenario.getModel().stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    /**
     * The railway the fixture says it is: four stations, five Points, six edges.
     *
     * Pinned exactly rather than loosely, which is the whole argument for a checked-in scenario - a
     * fixture whose shape can drift is a fixture whose assertions mean less every week.  If any of these
     * numbers moves, either the fixture was edited or the reduction changed, and both are things a
     * reader of this file wants to be told about rather than to have absorbed.
     */
    @Test
    public void testTheJunctionBuildsToTheShapeItIsDrawnAs() throws Exception
    {
        Layout built = scenario.build();

        assertEquals(scenario.getSession().getReducer().getPoints().size(), 4,
            "the fixture is drawn with four feedback sensors and reduced to "
            + scenario.getSession().getReducer().getPoints().size());

        assertEquals(built.getPoints().size(), 5,
            "four sensors, one of which - the approach - is arrived at from both sides and so becomes"
            + " two Points. Built: " + names(built));

        assertEquals(built.getEdges().size(), 6,
            "three pieces of track, each running both ways. Built: " + edgeNames(built));

        // AND THE SPLIT IS A REAL SPLIT: two Points that are one piece of metal.
        Point east = built.getPoint("Approach (eastbound)");
        Point west = built.getPoint("Approach (westbound)");

        assertNotNull(east, "the approach did not split - " + names(built));
        assertNotNull(west, "the approach did not split - " + names(built));

        assertEquals(east.getBlock(), west.getBlock(),
            "the two copies of the approach do not share a block, so the model would let two trains"
            + " stand on one square");

        assertNotNull(east.getBlock(), "the copies share no block at all");
    }

    /**
     * The two routes through the switch lock each other, in both directions of travel.
     *
     * This is the fact no fixture in the suite could state before: four edges, one switch, and every
     * one of them names the two that use the other position of the blades.
     */
    @Test
    public void testTheTwoRoutesThroughTheSwitchLockEachOther() throws Exception
    {
        Layout built = scenario.build();

        assertEquals(locks(built, MAIN), asSet(BRANCH, BRANCH_BACK),
            "the main route over the switch should hold both directions of the branch route, and holds "
            + locks(built, MAIN));

        assertEquals(locks(built, BRANCH), asSet(MAIN, MAIN_BACK),
            "the branch route over the switch should hold both directions of the main route, and holds "
            + locks(built, BRANCH));

        // AND THE TRAILING DIRECTION TOO. A tail lies across a rail whichever way traffic runs on it,
        // and the reason both directions exist here at all is that the fixture opens the switch's
        // trailing direction - a switch defaults to base-to-forks, so an unopened one gives a one-way
        // railway and half of these edges would simply not be there.
        assertEquals(locks(built, MAIN_BACK), asSet(BRANCH, BRANCH_BACK),
            "the main route read the other way should hold the branch route, and holds "
            + locks(built, MAIN_BACK));

        assertEquals(locks(built, BRANCH_BACK), asSet(MAIN, MAIN_BACK),
            "the branch route read the other way should hold the main route, and holds "
            + locks(built, BRANCH_BACK));
    }

    /**
     * And the lock does something: claiming one arm makes the other read as occupied.
     *
     * The lock LIST is a statement of intent; this is the behaviour it exists for, which is what
     * `isPathClear` asks before letting a train onto a switch somebody else's route is standing on.
     * `review-by-running-not-reading`: a fixture that can only be read proves nothing about the machine.
     */
    @Test
    public void testClaimingOneArmHoldsTheOther() throws Exception
    {
        Layout built = scenario.build();

        Edge main = edge(built, MAIN);
        Edge branch = edge(built, BRANCH);

        assertFalse(branch.isOccupied(null),
            "the branch arm reads as occupied before anything has claimed anything");

        main.setOccupied();

        assertTrue(branch.isOccupied(null),
            "a route was granted over the main arm of the switch and the branch arm still reads clear,"
            + " so a second train could be cleared onto the same blades");

        main.setUnoccupied();

        assertFalse(branch.isOccupied(null),
            "the main arm was released and the branch arm is still held, so the switch would stay"
            + " blocked for good");
    }

    /**
     * The switch really is switchable, and its two positions are the two routes.
     *
     * Said out loud because the parser turns a `linksweiche` with no usable address into a
     * CUSTOM_PERM_LEFT - a defective turnout that can only be trailed through - and the difference is
     * invisible in the file. A fixture that had quietly become one would still produce edges, and this
     * class would be testing a permanent crossing while claiming to test a turnout.
     */
    @Test
    public void testTheSwitchIsCommandedByEachRoute() throws Exception
    {
        Layout built = scenario.build();

        assertFalse(edge(built, MAIN).getConfigCommands().isEmpty(),
            "the main route over the switch commands nothing, so the blades are never thrown for it -"
            + " which is what a defective turnout with no address looks like, not a switch");

        assertFalse(edge(built, BRANCH).getConfigCommands().isEmpty(),
            "the branch route over the switch commands nothing");

        assertFalse(edge(built, MAIN).getConfigCommands().toString()
            .equals(edge(built, BRANCH).getConfigCommands().toString()),
            "the two routes over the switch command the SAME thing, so one of them is not throwing the"
            + " blades and a train sent that way would take whichever road the switch was already"
            + " lying for: " + edge(built, MAIN).getConfigCommands());
    }

    /**
     * @return the named edge, with a message naming what is there instead
     */
    private Edge edge(Layout built, String name)
    {
        for (Edge edge : built.getEdges())
        {
            if (name.equals(edge.getName())) return edge;
        }

        throw new IllegalStateException("the fixture has no edge called \"" + name + "\" - it has "
            + edgeNames(built));
    }

    /**
     * @return the names of the edges the given edge holds when it is claimed
     */
    private Set<String> locks(Layout built, String name)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge locked : edge(built, name).getLockEdges()) out.add(locked.getName());

        return out;
    }

    /**
     * @return the given names as a set
     */
    private static Set<String> asSet(String... names)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String name : names) out.add(name);

        return out;
    }

    /**
     * @return every built Point's name
     */
    private Set<String> names(Layout built)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Point point : built.getPoints()) out.add(point.getName());

        return out;
    }

    /**
     * @return every built edge's name
     */
    private Set<String> edgeNames(Layout built)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge edge : built.getEdges()) out.add(edge.getName());

        return out;
    }
}
