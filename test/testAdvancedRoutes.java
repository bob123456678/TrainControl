import java.util.LinkedList;
import org.traincontrol.base.NodeRouteCommand;
import org.traincontrol.base.NodeOr;
import org.traincontrol.base.NodeAnd;
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

    /**
     * UC-C4: a JSON-origin condition tree must mean the same thing after the editor round trip.
     *
     * The text parser applies stacked operators LIFO, so text-origin trees are right-nested - a bare
     * binary node can never be a LEFT child.  Hand-written JSON can build exactly that shape,
     * Or(And(a,b),c), and it used to render as "a AND b OR c", which reparses as And(a,Or(b,c)):
     * opening and saving such a route silently changed when it fires.  The fix normalizes at the JSON
     * door - a bare cross-operator left child is wrapped in a group on load, so the serializer emits
     * the preserving parentheses - and deliberately NOT in the serializer, which must keep rendering
     * text-origin trees byte-identically (testRoutes.testExpressions pins that identity).
     */
    @Test
    public void testAnUngroupedConditionTreeSurvivesTheEditorRoundTrip() throws Exception
    {
        int[] sensors = {46801, 46802, 46803};

        for (int s : sensors)
        {
            model.newFeedback(s, null);
        }

        // Or(And(a,b),c), built the only way it can exist: hand-written structural JSON
        org.json.JSONObject a = new org.json.JSONObject()
            .put("type", "NodeRouteCommand")
            .put("command", RouteCommand.RouteCommandFeedback(sensors[0], true).toJSON());
        org.json.JSONObject b = new org.json.JSONObject()
            .put("type", "NodeRouteCommand")
            .put("command", RouteCommand.RouteCommandFeedback(sensors[1], true).toJSON());
        org.json.JSONObject c = new org.json.JSONObject()
            .put("type", "NodeRouteCommand")
            .put("command", RouteCommand.RouteCommandFeedback(sensors[2], true).toJSON());

        org.json.JSONObject tree = new org.json.JSONObject()
            .put("type", "NodeOr")
            .put("left", new org.json.JSONObject()
                .put("type", "NodeAnd").put("left", a).put("right", b))
            .put("right", c);

        NodeExpression loaded = NodeExpression.fromJSON(tree);

        String text = NodeExpression.toTextRepresentation(loaded, model);
        NodeExpression reparsed = NodeExpression.fromTextRepresentation(text, model);

        for (int mask = 0; mask < 8; mask++)
        {
            model.setFeedbackState(String.valueOf(sensors[0]), (mask & 1) != 0);
            model.setFeedbackState(String.valueOf(sensors[1]), (mask & 2) != 0);
            model.setFeedbackState(String.valueOf(sensors[2]), (mask & 4) != 0);

            assertEquals(reparsed.evaluate(model), loaded.evaluate(model),
                "a=" + ((mask & 1) != 0) + " b=" + ((mask & 2) != 0) + " c=" + ((mask & 4) != 0)
                + ": the reparse of \"" + text.replace("\n", " ") + "\" changed the meaning");
        }
    }

    /**
     * UC-C20: a condition tree restored from the locomotive database is normalized too.
     *
     * The locomotive database Java-serializes condition trees and restores them without running any
     * parser - so a bare cross-operator tree imported from hand-written JSON before normalization
     * existed came back through that door unrepaired, and the editor round trip kept silently
     * rewriting its meaning.  The route constructor is the choke point every door shares; building
     * the route directly with a constructor-built bare tree models exactly what deserialization
     * hands it.
     */
    @Test
    public void testALegacyDatabaseConditionTreeIsNormalizedOnRestore() throws Exception
    {
        int[] sensors = {46811, 46812, 46813};

        for (int s : sensors)
        {
            model.newFeedback(s, null);
        }

        NodeExpression bare = new NodeOr(
            new NodeAnd(
                new NodeRouteCommand(RouteCommand.RouteCommandFeedback(sensors[0], true)),
                new NodeRouteCommand(RouteCommand.RouteCommandFeedback(sensors[1], true))),
            new NodeRouteCommand(RouteCommand.RouteCommandFeedback(sensors[2], true)));

        MarklinRoute restored = new MarklinRoute(model, "UC C20 legacy", 46810,
            new LinkedList<>(), 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, bare);

        String text = NodeExpression.toTextRepresentation(restored.getConditions(), model);
        NodeExpression reparsed = NodeExpression.fromTextRepresentation(text, model);

        for (int mask = 0; mask < 8; mask++)
        {
            model.setFeedbackState(String.valueOf(sensors[0]), (mask & 1) != 0);
            model.setFeedbackState(String.valueOf(sensors[1]), (mask & 2) != 0);
            model.setFeedbackState(String.valueOf(sensors[2]), (mask & 4) != 0);

            assertEquals(reparsed.evaluate(model), bare.evaluate(model),
                "a=" + ((mask & 1) != 0) + " b=" + ((mask & 2) != 0) + " c=" + ((mask & 4) != 0)
                + ": the restored tree changed meaning through \"" + text.replace("\n", " ") + "\"");
        }
    }

    /**
     * Renaming a locomotive repairs the routes whose CONDITION names it, not only their commands.
     *
     * Route.locomotiveRenamed sweeps the command list, and renameLoc runs it over every route - its own
     * comment names routes as one of the two stores that hold locomotives by name and therefore need
     * repairing.  Conditions are held separately, on MarklinRoute, and nothing swept them.
     *
     * The consequence is silent, which is what makes it worth a test: evaluate asks getLocByName for a
     * name that no longer exists, gets null, and returns false forever.  The monitor thread keeps
     * running and keeps logging "condition failed", so the route looks alive and never fires again.
     */
    @Test
    public void testARenameReachesConditionsAndNotOnlyCommands() throws Exception
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandLocomotiveSpeed("BR 218", 40));

        NodeExpression condition =
            new NodeRouteCommand(RouteCommand.RouteCommandAutoLocomotive("BR 218", 50));

        // Not enabled: the constructor starts a monitor thread for an enabled route with an s88, and
        // this test is about the rename, not the trigger.
        MarklinRoute route = new MarklinRoute(null, "Ahead", 1, commands, 50,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, condition);

        route.locomotiveRenamed("BR 218", "BR 218 neu");

        for (RouteCommand rc : NodeExpression.toList(route.getConditions()))
        {
            assertEquals(rc.getName(), "BR 218 neu",
                "the condition still names the old locomotive, so this route can never fire again");
        }

        for (RouteCommand rc : route.getRoute())
        {
            assertEquals(rc.getName(), "BR 218 neu", "the command list was not repaired either");
        }
    }

    /**
     * Capturing a command keeps one entry per locomotive, not one per kind of command.
     *
     * The filter keyed on everything before the first comma.  For an accessory line - "name,setting" -
     * that is the accessory, which is right.  For a locomotive line - "prefix,name,value" - it is just
     * "locspeed", so every locomotive in the route collapsed onto one key and only the last survived.
     *
     * The user sees this as a line vanishing from the middle of the text area while they click a
     * turnout somewhere else, and saving persists the shortened route.
     */
    @Test
    public void testCapturingKeepsEveryLocomotivesCommands() throws Exception
    {
        String filtered = org.traincontrol.gui.RouteEditor.filterConfigCommands(
            "locspeed,Loc A,50\nlocspeed,Loc B,40\nlocfunc,Loc A,3,1\nlocfunc,Loc A,4,1");

        assertTrue(filtered.contains("locspeed,Loc A,50"), "Loc A's speed was dropped:\n" + filtered);
        assertTrue(filtered.contains("locspeed,Loc B,40"), "Loc B's speed was dropped:\n" + filtered);

        assertTrue(filtered.contains("locfunc,Loc A,3,1") && filtered.contains("locfunc,Loc A,4,1"),
            "two functions of one locomotive are two settings, not one:\n" + filtered);
    }

    /**
     * The same filter still collapses repeated writes to one accessory, keeping the last.
     *
     * The precondition that makes the test above meaningful: a key wide enough to separate locomotives
     * must not have become so wide that nothing dedupes any more.  Captured three-way pairs depend on
     * this - rewriting an accessory moves it to the end, and that ordering is load-bearing.
     */
    @Test
    public void testCapturingStillCollapsesRepeatedAccessoryWrites() throws Exception
    {
        String filtered = org.traincontrol.gui.RouteEditor.filterConfigCommands(
            "Switch 3,straight\nSwitch 4,turn\nSwitch 3,turn");

        assertFalse(filtered.contains("Switch 3,straight"),
            "the earlier setting of Switch 3 should have been replaced:\n" + filtered);

        assertTrue(filtered.contains("Switch 3,turn"), "the later one should survive:\n" + filtered);
        assertTrue(filtered.contains("Switch 4,turn"), "and the other accessory too:\n" + filtered);
    }

    /**
     * A name routes can carry survives the text format; one they cannot does not.
     *
     * The guard exists because the format cannot express these names, so the test proves that rather
     * than restating the predicate - a test that only asserted isNameUsable("a,b") is false would pass
     * against a rule invented for no reason.
     */
    @Test
    public void testANameRoutesCanCarrySurvivesTheTextFormat() throws Exception
    {
        assertTrue(RouteCommand.isNameUsable("BR 218"), "precondition: an ordinary name is allowed");

        RouteCommand allowed = RouteCommand.RouteCommandLocomotiveSpeed("BR 218", 40);

        assertEquals(RouteCommand.fromLine(allowed.toLine(null).trim(), false).getName(), "BR 218",
            "an allowed name must survive being written and read back");

        assertFalse(RouteCommand.isNameUsable("BR 103, 001"), "a comma is refused");

        RouteCommand refused = RouteCommand.RouteCommandLocomotiveSpeed("BR 103, 001", 40);

        boolean carried;

        try
        {
            carried = "BR 103, 001".equals(
                RouteCommand.fromLine(refused.toLine(null).trim(), false).getName());
        }
        catch (Exception e)
        {
            // The other way it fails: the tail does not parse as a number and the whole line is
            // rejected, which is what blocks the route from being saved at all
            carried = false;
        }

        assertFalse(carried,
            "the format carried a comma after all, which would make the guard unnecessary");
    }

    /**
     * A name with a bracket cannot survive the CONDITION parser, which is the other half of the rule.
     *
     * preprocessText rewrites every bracket into a line break to find the grouping - the same
     * unanchored-replacement mistake that was fixed for AND and OR, and not for brackets.  A real
     * locomotive in the sample file is called "SBB 460 (2)".
     */
    @Test
    public void testANameWithABracketCannotSurviveTheConditionParser() throws Exception
    {
        assertFalse(RouteCommand.isNameUsable("SBB 460 (2)"), "a bracket is refused");

        NodeExpression condition =
            new NodeRouteCommand(RouteCommand.RouteCommandAutoLocomotive("SBB 460 (2)", 50));

        String asText = NodeExpression.toTextRepresentation(condition, null);

        boolean survived;

        try
        {
            NodeExpression back = NodeExpression.fromTextRepresentation(asText, null);

            survived = !NodeExpression.toList(back).isEmpty()
                && "SBB 460 (2)".equals(NodeExpression.toList(back).get(0).getName());
        }
        catch (Exception e)
        {
            survived = false;
        }

        assertFalse(survived,
            "the condition parser carried a bracket after all, which would make the guard "
                + "unnecessary - and would mean this name could simply be allowed");
    }

    /**
     * A route refused for having a duplicate name does not leave its monitor running.
     *
     * The complete constructor arms the s88 monitor as soon as the route is enabled and has a sensor -
     * before the object has been offered to any database - so an import carrying two routes of the
     * same name left the rejected one watching its sensor forever, firing turnouts for a route the UI
     * has no handle on and only a restart could stop.
     */
    @Test
    public void testARefusedRouteDoesNotKeepItsMonitorRunning() throws Exception
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandLocomotiveSpeed("BR 218", 40));

        // A name and ids this database will not already have
        MarklinRoute first = new MarklinRoute(model, "Duplicate Import Probe", 8801, commands, 61,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

        try
        {
            assertTrue(model.newRoute(first), "precondition: the first route is accepted");

            MarklinRoute second = new MarklinRoute(model, "Duplicate Import Probe", 8802, commands, 61,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

            assertTrue(second.isEnabled(), "precondition: the constructor armed the second route");

            assertFalse(model.newRoute(second), "precondition: the duplicate name is refused");

            assertFalse(second.isEnabled(),
                "the refused route is still armed, so its monitor keeps firing a route nothing can "
                    + "reach or disable");
        }
        finally
        {
            model.deleteRoute("Duplicate Import Probe");
        }
    }
}
