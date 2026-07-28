import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Conditional routes that act on locomotives, and multi-units inside them.
 *
 * Two combinations that the suite did not cover, and that the author's manual testing did not exercise
 * either:
 *
 *  - A route whose CONDITION is an autoloc term ("locomotive X is standing at sensor N") and whose
 *    COMMAND acts on that same locomotive - the "fire a function when the train reaches this sensor"
 *    arrangement.  `testRoutes` covers autoloc conditions in isolation, through `Route.evaluate`, and
 *    covers function commands nowhere: the condition and the command had never been exercised together
 *    through a real trigger.
 *  - Multi-units reached through a route.  `MarklinLocomotive.setF` and `setSpeed` fan out to
 *    `linkedLocomotives`, so a route command naming a consist head silently commands every member.
 *    Nothing asserted that, and the fan-out map was rebuilt three separate times during the July 2026
 *    cycle (INT-A1, INT-A2, RR-C1).
 *
 * These run against the real trigger path - a monitor thread woken by an s88 transition - rather than
 * calling execRoute directly, because the point is the combination, not the pieces.  That makes them
 * timing-dependent: `pulseFeedback` holds each sensor state well past
 * `Locomotive.FEEDBACK_DURATION_THRESHOLD` and then allows the route body time to run, matching the
 * approach `testRoutes` already uses.
 *
 * Every route is disabled in a finally block.  A route left enabled keeps a monitor thread parked on
 * its sensor for the rest of the JVM, and would fire during later tests.
 */
public class testAdvancedRoutes
{
    private static MarklinControlStation model;

    /** Feedback and route id ranges picked clear of testRoutes (88xx / 98xx) and the layout suites. */
    private static final int FEEDBACK_BASE = 8850;
    private static final int ROUTE_ID_BASE = 9850;

    private static final int FUNCTION = 3;

    private static final String[] TEST_LOCS = {
        "AR solo", "AR head", "AR member", "AR other" };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        for (String name : TEST_LOCS)
        {
            model.deleteLoc(name);
        }
    }

    /**
     * A single station, so autoloc conditions have a graph to resolve against.  Each test uses its own
     * point name and sensor: parseAuto replaces the whole graph, so tests must not share one.
     */
    private static String stationAutonomy(String pointName, int s88)
    {
        return "{"
            + "\"points\": [ {\"name\": \"" + pointName + "\", \"station\": true, \"s88\": " + s88 + "} ],"
            + "\"edges\": [],"
            + "\"minDelay\": 0,"
            + "\"maxDelay\": 0,"
            + "\"defaultLocSpeed\": 30"
            + "}";
    }

    /**
     * Two stations, so a locomotive can be placed at one while a condition names the other.
     */
    private static String twoStationAutonomy(String a, int s88a, String b, int s88b)
    {
        return "{"
            + "\"points\": ["
            + " {\"name\": \"" + a + "\", \"station\": true, \"s88\": " + s88a + "},"
            + " {\"name\": \"" + b + "\", \"station\": true, \"s88\": " + s88b + "}"
            + "],"
            + "\"edges\": [],"
            + "\"minDelay\": 0,"
            + "\"maxDelay\": 0,"
            + "\"defaultLocSpeed\": 30"
            + "}";
    }

    /**
     * Drives a sensor clear then occupied, holding each state past FEEDBACK_DURATION_THRESHOLD, then
     * allows the route body to run.  Same shape as testRoutes.pulseFeedback.
     */
    private static void pulseFeedback(int address) throws InterruptedException
    {
        model.setFeedbackState(Integer.toString(address), false);
        Thread.sleep(400);
        model.setFeedbackState(Integer.toString(address), true);
        Thread.sleep(1500);
    }

    /** Registers a trigger sensor and leaves it clear. */
    private static void prepareFeedback(int address)
    {
        model.newFeedback(address, null);
        model.setFeedbackState(Integer.toString(address), false);
    }

    /** A locomotive with its test function off, created fresh if it does not exist. */
    private static MarklinLocomotive loco(String name, int address) throws Exception
    {
        MarklinLocomotive l = model.getLocByName(name);

        if (l == null)
        {
            l = model.newMM2Locomotive(name, address);
        }

        l.setF(FUNCTION, false);
        l.setSpeed(0);

        return l;
    }

    /**
     * Links member to head as a multi-unit, and returns the head.
     */
    private static MarklinLocomotive consist(MarklinLocomotive head, MarklinLocomotive member)
    {
        Map<String, Double> links = new HashMap<>();
        links.put(member.getName(), 1.0);

        head.preSetLinkedLocomotives(links);
        head.setLinkedLocomotives();

        return head;
    }

    /**
     * Builds an enabled route: fire FUNCTION on the named locomotive when triggerS88 goes clear then
     * occupied, provided the condition holds.
     */
    private static MarklinRoute functionRoute(String routeName, int routeId, String locName,
        int triggerS88, List<RouteCommand> conditions)
    {
        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandFunction(locName, FUNCTION, true));

        return new MarklinRoute(model, routeName, routeId, commands, triggerS88,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true,
            conditions == null ? null : NodeExpression.fromList(conditions));
    }

    // ---------------------------------------------------------------------------------------------
    // The autoloc condition driving a function command
    // ---------------------------------------------------------------------------------------------

    /**
     * The arrangement this file exists for: fire a function when a particular locomotive reaches a
     * particular sensor.
     *
     * The condition and the command name the same locomotive, and the route is triggered by a third
     * sensor, so the two halves are independent: the trigger says *when* to look, the condition says
     * *whether* to act.
     */
    @Test
    public void testFunctionFiresOnlyOnceTheLocomotiveIsAtTheSensor() throws Exception
    {
        int stationS88 = FEEDBACK_BASE;
        int triggerS88 = FEEDBACK_BASE + 1;

        model.parseAuto(stationAutonomy("AR_AtSensor", stationS88));
        prepareFeedback(triggerS88);

        MarklinLocomotive loc = loco("AR solo", 61);

        List<RouteCommand> conditions = new ArrayList<>();
        conditions.add(RouteCommand.RouteCommandAutoLocomotive(loc.getName(), stationS88));

        MarklinRoute route = functionRoute("AR function on arrival", ROUTE_ID_BASE,
            loc.getName(), triggerS88, conditions);

        try
        {
            // Let the monitor thread reach its blocking wait
            Thread.sleep(600);

            assertNull(model.getAutoLayout().getLocomotiveLocation(loc),
                "precondition: the locomotive is not on the graph, so the condition is unsatisfied");

            pulseFeedback(triggerS88);

            assertFalse(loc.getF(FUNCTION),
                "the function must not fire while the locomotive is not at the named sensor");

            // Put the locomotive where the condition expects it
            assertTrue(model.getAutoLayout().moveLocomotive(loc.getName(), "AR_AtSensor", false),
                "precondition: the locomotive is placed at the station");

            assertTrue(Route.evaluate(
                RouteCommand.RouteCommandAutoLocomotive(loc.getName(), stationS88), model),
                "precondition: the condition is now satisfiable");

            pulseFeedback(triggerS88);

            assertTrue(loc.getF(FUNCTION),
                "with the locomotive at the sensor the route must fire and set the function");
        }
        finally
        {
            route.disable();
        }
    }

    /**
     * The same route, with the locomotive standing somewhere else.
     *
     * Distinguishes "the condition is satisfied" from "the locomotive is on the graph at all" - a route
     * that fired merely because the locomotive existed would pass the test above and fail this one.
     */
    @Test
    public void testFunctionDoesNotFireWhenTheLocomotiveIsAtAnotherSensor() throws Exception
    {
        int expectedS88 = FEEDBACK_BASE + 2;
        int elsewhereS88 = FEEDBACK_BASE + 3;
        int triggerS88 = FEEDBACK_BASE + 4;

        model.parseAuto(twoStationAutonomy("AR_Expected", expectedS88, "AR_Elsewhere", elsewhereS88));
        prepareFeedback(triggerS88);

        MarklinLocomotive loc = loco("AR solo", 61);

        List<RouteCommand> conditions = new ArrayList<>();
        conditions.add(RouteCommand.RouteCommandAutoLocomotive(loc.getName(), expectedS88));

        MarklinRoute route = functionRoute("AR function wrong sensor", ROUTE_ID_BASE + 1,
            loc.getName(), triggerS88, conditions);

        try
        {
            Thread.sleep(600);

            assertTrue(model.getAutoLayout().moveLocomotive(loc.getName(), "AR_Elsewhere", false),
                "precondition: the locomotive is on the graph, but at the other station");

            pulseFeedback(triggerS88);

            assertFalse(loc.getF(FUNCTION),
                "the locomotive is on the graph but not at the sensor the condition names, so the "
                + "function must not fire");
        }
        finally
        {
            route.disable();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Multi-units reached through a route
    // ---------------------------------------------------------------------------------------------

    /**
     * A route function command naming a consist head reaches every member.
     *
     * MarklinLocomotive.setF fans out over linkedLocomotives before setting its own function, so this
     * is a property of the consist rather than of the route - but a route is how a user would actually
     * trigger it, and the fan-out map was rebuilt three times during the July 2026 cycle.
     */
    @Test
    public void testRouteFunctionOnConsistHeadReachesMembers() throws Exception
    {
        int triggerS88 = FEEDBACK_BASE + 5;

        prepareFeedback(triggerS88);

        MarklinLocomotive head = loco("AR head", 62);
        MarklinLocomotive member = loco("AR member", 63);

        consist(head, member);

        assertTrue(head.hasLinkedLocomotives(), "precondition: the consist is linked");
        assertFalse(head.getF(FUNCTION), "precondition: the head's function is off");
        assertFalse(member.getF(FUNCTION), "precondition: the member's function is off");

        MarklinRoute route = functionRoute("AR consist function", ROUTE_ID_BASE + 2,
            head.getName(), triggerS88, null);

        try
        {
            Thread.sleep(600);

            pulseFeedback(triggerS88);

            assertTrue(head.getF(FUNCTION), "the route must set the function on the head");
            assertTrue(member.getF(FUNCTION),
                "and the head must pass it to its member - a route naming the head commands the whole "
                + "consist");
        }
        finally
        {
            route.disable();
        }
    }

    /**
     * An autoloc condition resolves against a consist head like any other locomotive.
     *
     * The head is what autonomy drives and what stands at a point; the member is not separately placed.
     * A condition naming the member must therefore be unsatisfied even while the consist is at the
     * sensor - which is correct, and worth pinning because it is easy to assume membership implies
     * presence.
     */
    @Test
    public void testAutoLocConditionResolvesAgainstTheConsistHeadOnly() throws Exception
    {
        int stationS88 = FEEDBACK_BASE + 6;

        model.parseAuto(stationAutonomy("AR_Consist", stationS88));

        MarklinLocomotive head = loco("AR head", 62);
        MarklinLocomotive member = loco("AR member", 63);

        consist(head, member);

        assertTrue(model.getAutoLayout().moveLocomotive(head.getName(), "AR_Consist", false),
            "precondition: the consist head is placed at the station");

        assertTrue(Route.evaluate(
            RouteCommand.RouteCommandAutoLocomotive(head.getName(), stationS88), model),
            "the head is at the sensor, so a condition naming it is satisfied");

        assertFalse(Route.evaluate(
            RouteCommand.RouteCommandAutoLocomotive(member.getName(), stationS88), model),
            "the member is not separately on the graph, so a condition naming it is not satisfied - "
            + "being part of a consist that is present is not the same as being present");
    }

    /**
     * A consist head and its own member may never be run as separate trains.
     *
     * isSimultaneousMultiUnitCompatible is what stops autonomy from driving both: they share a decoder
     * through the fan-out, so commanding them independently would have them fight each other.
     */
    @Test
    public void testConsistHeadAndMemberAreNotSimultaneouslyCompatible() throws Exception
    {
        MarklinLocomotive head = loco("AR head", 62);
        MarklinLocomotive member = loco("AR member", 63);
        MarklinLocomotive unrelated = loco("AR other", 64);

        consist(head, member);

        assertFalse(head.isSimultaneousMultiUnitCompatible(member),
            "the head drives the member, so autonomy must never run them as two trains");

        assertTrue(head.isSimultaneousMultiUnitCompatible(unrelated),
            "an unrelated locomotive on a different address is unaffected");
    }

    /**
     * A route speed command naming the head reaches the members too.
     *
     * The multiplier is 1.0 here, so the member should match the head exactly.  Speed is the fan-out
     * that IND-M1 found silently desynchronising above a threshold, so it is worth an explicit check
     * that a route - not just a throttle - drives the whole consist.
     */
    @Test
    public void testRouteSpeedOnConsistHeadReachesMembers() throws Exception
    {
        int triggerS88 = FEEDBACK_BASE + 7;

        prepareFeedback(triggerS88);

        MarklinLocomotive head = loco("AR head", 62);
        MarklinLocomotive member = loco("AR member", 63);

        consist(head, member);

        assertEquals(head.getSpeed(), 0, "precondition: stopped");
        assertEquals(member.getSpeed(), 0, "precondition: stopped");

        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandLocomotiveSpeed(head.getName(), 40));

        MarklinRoute route = new MarklinRoute(model, "AR consist speed", ROUTE_ID_BASE + 3, commands,
            triggerS88, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

        try
        {
            Thread.sleep(600);

            pulseFeedback(triggerS88);

            assertEquals(head.getSpeed(), 40, "the route must set the head's speed");
            assertEquals(member.getSpeed(), 40,
                "and the member must follow at the same speed, the multiplier being 1.0");
        }
        finally
        {
            route.disable();

            head.setSpeed(0);
        }
    }
}
