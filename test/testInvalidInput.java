import org.json.JSONObject;
import org.traincontrol.util.Util;
import org.traincontrol.util.Conversion;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Graceful handling of input the user entered incorrectly.
 *
 * The suite already covers one such surface well - `testRoutes.testMalformedRouteLinesReportAReadableError`
 * feeds `RouteCommand.fromLine` eight malformed command lines - and covers address entry through
 * `validateNewAddress`.  Three surfaces were untested, and this class covers them:
 *
 *  - **The autonomy configuration file.**  `Layout.fromJSON` has around forty distinct rejection paths
 *    and not one of them was asserted; no test in the suite ever called `isValid()`.  Every existing
 *    autonomy test feeds the parser valid JSON.  This is also the input the user is most likely to get
 *    wrong, because it is the only one they hand-edit as text.
 *  - **The accessory command box in the graph edge editor**, `Edge.validateConfigCommand`.  It was
 *    called only with valid input, from `testAccessory`.
 *  - **Route file import**, which deletes every existing route before re-adding from the file.
 *
 * The contract being pinned in each case is not "it rejects bad input" alone - it is:
 *
 *  1. bad input is *detected* rather than half-applied,
 *  2. the failure arrives as a readable error or an invalidated layout, never as an unchecked
 *     exception escaping to the caller, and
 *  3. **valid input is still accepted** - each group carries a control case, because a validator that
 *     rejects everything would satisfy the first two points.
 *
 * The third point is the one that matters for regressions.  `Layout.fromJSON` never throws: it returns
 * an invalidated `Layout` object either way.  So a change that broke a check would not fail loudly -
 * it would quietly load a broken layout, which is exactly the failure these tests exist to catch.
 */
public class testInvalidInput
{
    private static MarklinControlStation model;

    /** A locomotive that exists, for the config cases that need to place a real one. */
    private static final String LOC_PLACED = "IN placed";
    private static final int LOC_PLACED_ADDRESS = 71;

    /** A name that must not resolve to anything in the database. */
    private static final String LOC_UNKNOWN = "IN no such locomotive";

    /**
     * Accessory addresses, kept apart per group so the accessories one group creates cannot satisfy
     * another group's assertions.
     *
     * Each is emptied before use rather than assumed unused.  `init` restores the real `LocDB.data`,
     * and as `testAccessory` puts it, a real database is not clean - the keyboard registers an
     * accessory at every address the operator has ever scrolled past. This test first picked addresses
     * believed to be free and asserted one was absent; it failed on the author's database, which had a
     * switch at 291.
     */
    private static final int ACC_REJECTED_ADDRESS = 291;
    private static final int ACC_ACCEPTED_ADDRESS = 292;
    private static final int ACC_IN_CONFIG_ADDRESS = 293;

    private static final String ACC_REJECTED = "Switch " + ACC_REJECTED_ADDRESS;
    private static final String ACC_ACCEPTED = "Switch " + ACC_ACCEPTED_ADDRESS;
    private static final String ACC_IN_CONFIG = "Switch " + ACC_IN_CONFIG_ADDRESS;

    private static final String ROUTE_NAME = "IN import survivor";
    private static final int ROUTE_ID = 9880;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();

        model.newMM2Locomotive(LOC_PLACED, LOC_PLACED_ADDRESS);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        model.deleteLoc(LOC_PLACED);
        model.deleteRoute(ROUTE_NAME);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Empties one logical accessory address, in memory only - nothing here calls saveState.
     *
     * Reaches accDB by reflection because the model has no accessory delete: the same approach
     * testAccessory uses, and for the same reason.  A switch and a signal at one address share a UID,
     * so both names have to go.
     */
    @SuppressWarnings("unchecked")
    private static void clearAccessoryAddress(int logicalAddress) throws Exception
    {
        Field accDbField = MarklinControlStation.class.getDeclaredField("accDB");
        accDbField.setAccessible(true);

        RemoteDeviceCollection<MarklinAccessory, Integer> accDb =
            (RemoteDeviceCollection<MarklinAccessory, Integer>) accDbField.get(model);

        accDb.delete("Switch " + logicalAddress);
        accDb.delete("Signal " + logicalAddress);
    }

    /**
     * Single quotes become double quotes, so the JSON below stays readable in Java source rather than
     * disappearing into backslashes.  No value in this class contains an apostrophe.
     */
    private static String json(String s)
    {
        return s.replace('\'', '"');
    }

    private static final String POINT_A = "{'name': 'IN A', 'station': true, 's88': 8880, 'x': 10, 'y': 10}";
    private static final String POINT_B = "{'name': 'IN B', 'station': true, 's88': 8881, 'x': 20, 'y': 20}";
    private static final String EDGE_AB = "{'start': 'IN A', 'end': 'IN B'}";

    /**
     * A configuration with the required top-level settings already present, so each test varies only
     * the part it is about.
     */
    private static String config(String points, String edges, String extraSettings)
    {
        return json("{'points': [" + points + "], 'edges': [" + edges + "]"
            + ", 'minDelay': 0, 'maxDelay': 0, 'defaultLocSpeed': 30"
            + extraSettings + "}");
    }

    /** The baseline every rejection case below is a single mutation away from. */
    private static String validConfig()
    {
        return config(POINT_A + ", " + POINT_B, EDGE_AB, "");
    }

    /**
     * Asserts that a configuration is rejected.  Failures name the case, because a config that parses
     * when it should not produces an otherwise silent pass.
     */
    private static void assertRejected(String description, String configJson)
    {
        Layout layout = Layout.fromJSON(configJson, model);

        assertNotNull(layout, description + ": fromJSON returned null rather than an invalid layout");
        assertFalse(layout.isValid(), description + ": was accepted, but should have been rejected");
    }

    // ---------------------------------------------------------------------------------------------
    // The autonomy configuration file
    // ---------------------------------------------------------------------------------------------

    /**
     * The control case.  Without it, every assertion below would be satisfied by a parser that rejects
     * its input unconditionally.
     */
    @Test
    public void testValidConfigurationIsAccepted()
    {
        Layout layout = Layout.fromJSON(validConfig(), model);

        assertNotNull(layout);
        assertTrue(layout.isValid(),
            "the baseline configuration was rejected, so every rejection test below is meaningless");
        assertNotNull(layout.getPoint("IN A"));
        assertNotNull(layout.getPoint("IN B"));
    }

    /**
     * Text that is not JSON at all - a truncated file, or the wrong file entirely.
     */
    @Test
    public void testMalformedJsonIsRejected()
    {
        assertRejected("empty", "");
        assertRejected("not json", "this is not a configuration file");
        assertRejected("truncated", json("{'points': [" + POINT_A));
        assertRejected("an array where an object belongs", json("['points', 'edges']"));
    }

    /**
     * Each required top-level key removed in turn.  All five are read in one try block, so this also
     * confirms none of them is quietly optional.
     */
    @Test
    public void testMissingRequiredKeysAreRejected()
    {
        assertRejected("no points",
            json("{'edges': [], 'minDelay': 0, 'maxDelay': 0, 'defaultLocSpeed': 30}"));
        assertRejected("no edges",
            json("{'points': [], 'minDelay': 0, 'maxDelay': 0, 'defaultLocSpeed': 30}"));
        assertRejected("no minDelay",
            json("{'points': [], 'edges': [], 'maxDelay': 0, 'defaultLocSpeed': 30}"));
        assertRejected("no maxDelay",
            json("{'points': [], 'edges': [], 'minDelay': 0, 'defaultLocSpeed': 30}"));
        assertRejected("no defaultLocSpeed",
            json("{'points': [], 'edges': [], 'minDelay': 0, 'maxDelay': 0}"));
    }

    /**
     * A number written as a quoted string.  This is the most likely hand-edit mistake in the file, and
     * the parser is deliberately strict about it for point fields: `"s88": "5"` is rejected even though
     * the digits are correct, because `point.get` returns a String and the check is an instanceof.
     */
    @Test
    public void testQuotedNumbersInPointsAreRejected()
    {
        assertRejected("s88 quoted",
            config("{'name': 'IN A', 'station': true, 's88': '8880'}", "", ""));
        assertRejected("x quoted",
            config("{'name': 'IN A', 'station': true, 's88': 8880, 'x': '10'}", "", ""));
        assertRejected("y quoted",
            config("{'name': 'IN A', 'station': true, 's88': 8880, 'y': '10'}", "", ""));
    }

    /**
     * The optional numeric settings.  These go through getInt/getDouble, which coerce a numeric string,
     * so only genuinely non-numeric text is rejected - the assertion is written to match that rather
     * than to assume the point-field strictness above applies here too.
     */
    @Test
    public void testNonNumericSettingsAreRejected()
    {
        assertRejected("maxLatency", config(POINT_A, "", ", 'maxLatency': 'soon'"));
        assertRejected("maxActiveTrains", config(POINT_A, "", ", 'maxActiveTrains': 'many'"));
        assertRejected("maxLocInactiveSeconds", config(POINT_A, "", ", 'maxLocInactiveSeconds': 'a while'"));
        assertRejected("preArrivalSpeedReduction", config(POINT_A, "", ", 'preArrivalSpeedReduction': 'half'"));
        assertRejected("atomicRoutes not a boolean", config(POINT_A, "", ", 'atomicRoutes': 'sometimes'"));
    }

    /**
     * Points that cannot be created: no name, and the same name twice - the latter being what a
     * copy-pasted block produces.
     */
    @Test
    public void testUnusablePointsAreRejected()
    {
        assertRejected("point with no name",
            config("{'station': true, 's88': 8880}", "", ""));
        assertRejected("duplicate point name",
            config(POINT_A + ", " + POINT_A, "", ""));
    }

    /**
     * A locomotive named in the config that is not in the database - what a rename or a deletion leaves
     * behind - and the same locomotive placed at two points at once.
     */
    @Test
    public void testUnusableLocomotivePlacementsAreRejected()
    {
        assertRejected("locomotive not in database",
            config("{'name': 'IN A', 'station': true, 's88': 8880, 'loc': {'name': '" + LOC_UNKNOWN + "'}}",
                "", ""));

        assertRejected("locomotive entry with no name",
            config("{'name': 'IN A', 'station': true, 's88': 8880, 'loc': {'speed': 30}}", "", ""));

        assertRejected("same locomotive at two points",
            config("{'name': 'IN A', 'station': true, 's88': 8880, 'loc': {'name': '" + LOC_PLACED + "'}},"
                 + "{'name': 'IN B', 'station': true, 's88': 8881, 'loc': {'name': '" + LOC_PLACED + "'}}",
                "", ""));
    }

    /**
     * Edges that cannot be built, and accessory commands on an edge that cannot be carried out.  An
     * edge naming a point that does not exist is what deleting a point from the file leaves behind.
     *
     * The last case is the one with history.  The load-time call to `validateConfigCommand` exists to
     * *add* the accessory, and its failure was discarded, so a config naming an accessory that could
     * not be created loaded as valid - and only failed later, when `configureEdge` refused to actuate
     * the edge, several steps from anything that named the cause.  It is now fatal at load.
     */
    @Test
    public void testUnusableEdgesAreRejected()
    {
        assertRejected("edge to a point that does not exist",
            config(POINT_A + ", " + POINT_B, "{'start': 'IN A', 'end': 'IN NOWHERE'}", ""));

        assertRejected("edge with no end",
            config(POINT_A + ", " + POINT_B, "{'start': 'IN A'}", ""));

        assertRejected("command with an action that is not a setting",
            config(POINT_A + ", " + POINT_B,
                "{'start': 'IN A', 'end': 'IN B', 'commands': ["
                + "{'acc': '" + ACC_IN_CONFIG + "', 'state': 'purple'}]}", ""));

        assertRejected("command with no state",
            config(POINT_A + ", " + POINT_B,
                "{'start': 'IN A', 'end': 'IN B', 'commands': [{'acc': '" + ACC_IN_CONFIG + "'}]}", ""));

        assertRejected("command with no accessory",
            config(POINT_A + ", " + POINT_B,
                "{'start': 'IN A', 'end': 'IN B', 'commands': [{'state': 'red'}]}", ""));

        assertRejected("command naming an accessory that cannot be added",
            config(POINT_A + ", " + POINT_B,
                "{'start': 'IN A', 'end': 'IN B', 'commands': [{'acc': 'Nonsense', 'state': 'red'}]}",
                ""));
    }

    // ---------------------------------------------------------------------------------------------
    // The accessory command box in the graph edge editor
    // ---------------------------------------------------------------------------------------------

    /**
     * What the user types into an edge's command box, one line at a time.  Each of these must produce a
     * readable error rather than an unchecked exception: the editor shows the message, and a
     * NumberFormatException reaching it would surface as a stack trace instead.
     */
    @Test
    public void testInvalidAccessoryCommandsAreRejected() throws Exception
    {
        clearAccessoryAddress(ACC_REJECTED_ADDRESS);

        assertNull(model.getAccessoryByName(ACC_REJECTED),
            "the address could not be emptied, so this test cannot tell whether a rejected command"
            + " created an accessory");

        String[][] invalid = {
            { null,          "red"    },   // no accessory
            { ACC_REJECTED,  null     },   // no setting
            { ACC_REJECTED,  "purple" },   // not one of green/red/straight/turn
            { ACC_REJECTED,  ""       },   // setting left blank
            { "Nonsense",    "red"    },   // accessory name with no address in it
            { "",            "red"    }    // accessory left blank
        };

        for (String[] command : invalid)
        {
            String description = command[0] + " / " + command[1];

            try
            {
                Edge.validateConfigCommand(command[0], command[1], model);

                fail("expected a readable error for: " + description);
            }
            catch (Exception e)
            {
                assertFalse(e instanceof RuntimeException,
                    description + " threw an unchecked " + e.getClass().getSimpleName()
                    + " instead of a readable error");

                assertNotNull(e.getMessage(), description + " produced no message");
            }
        }

        // A rejected command must not leave a half-created accessory behind.  validateConfigCommand
        // creates accessories as a side effect when the name is valid and the address is new, so
        // rejecting the setting has to happen before that point, not after.
        assertNull(model.getAccessoryByName(ACC_REJECTED),
            "a rejected command created " + ACC_REJECTED + " anyway");
    }

    /**
     * The control case for the group above: a well-formed command is still accepted, and still creates
     * the accessory it names.
     */
    @Test
    public void testValidAccessoryCommandIsAccepted() throws Exception
    {
        // Emptied first, so the assertion below shows the command created the accessory rather than
        // finding one the restored database already had
        clearAccessoryAddress(ACC_ACCEPTED_ADDRESS);
        assertNull(model.getAccessoryByName(ACC_ACCEPTED));

        assertNotNull(Edge.validateConfigCommand(ACC_ACCEPTED, "red", model));
        assertNotNull(model.getAccessoryByName(ACC_ACCEPTED),
            ACC_ACCEPTED + " was accepted but not created");

        // Case and surrounding whitespace are tolerated, as they must be for typed input
        assertNotNull(Edge.validateConfigCommand(ACC_ACCEPTED, " Green ", model));
    }

    // ---------------------------------------------------------------------------------------------
    // Route file import
    // ---------------------------------------------------------------------------------------------

    /**
     * Choosing the wrong file must not cost the user their routes.
     *
     * `importRoutes` deletes every existing route and then adds the parsed ones.  That is only safe
     * because parsing happens first, in a separate statement: a file that cannot be read throws before
     * the deletion loop is reached.  Nothing pinned that ordering, and the comment describing it
     * ("if all read successfully") sits *below* the deletion rather than above it, where a later reader
     * would take it as describing what follows.
     *
     * Three shapes of bad file, each failing at a different depth: not JSON at all, JSON without the
     * routes array, and a routes array whose contents are not routes.
     */
    @Test
    public void testFailedRouteImportLeavesExistingRoutesIntact() throws Exception
    {
        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.fromLine(ACC_IN_CONFIG + ",turn", false));

        MarklinRoute route = new MarklinRoute(model, ROUTE_NAME, ROUTE_ID, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        assertTrue(model.newRoute(route), "could not register the route this test needs");

        int routeCount = model.getRoutes().size();

        String[] badFiles = {
            "this is not a route file",
            "{}",
            json("{'notroutes': []}"),
            json("{'routes': 'not an array'}"),
            json("{'routes': [ {'name': 'incomplete'} ]}")
        };

        for (String badFile : badFiles)
        {
            try
            {
                model.importRoutes(badFile);

                fail("expected an error for: " + badFile);
            }
            catch (Exception e)
            {
                // Expected.  What matters is what did not happen, asserted below.
            }

            assertEquals(model.getRoutes().size(), routeCount,
                "importing an unreadable file changed the route database: " + badFile);
        }

        assertTrue(hasRoute(ROUTE_NAME),
            "the existing route was deleted by an import that never succeeded");
    }

    private static boolean hasRoute(String name)
    {
        for (MarklinRoute r : model.getRoutes())
        {
            if (name.equals(r.getName()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * A point cannot be both a terminus and a reversing station, whichever order they are set in.
     *
     * The rule itself is the layout's: a terminus is where a train stops and leaves the way it came, so
     * asking it to also reverse as part of shunting is a contradiction.  Point enforces it from both
     * setters, which is what makes the pair genuinely unreachable rather than merely unusual.
     *
     * It is pinned here because the graph drawing now reads it as a fact.  A reversing station renders
     * as a cross, and the home-locomotive outline thins its dots for exactly that shape by asking
     * isReversing() - which is only an exact test for "drawn as a cross" while a terminus can never be
     * reversing.  Drop either check below and the outline quietly starts thinning boxes too.
     */
    @Test
    public void testAPointCannotBeBothTerminusAndReversing() throws Exception
    {
        Point terminusFirst = new Point("Terminus first", true, "101");

        terminusFirst.setTerminus(true);

        try
        {
            terminusFirst.setReversing(true);
            fail("a terminus must not be allowed to become reversing");
        }
        catch (Exception expected)
        {
            assertTrue(terminusFirst.isTerminus(), "and the point it was called on is unchanged");
            assertFalse(terminusFirst.isReversing());
        }

        Point reversingFirst = new Point("Reversing first", true, "102");

        reversingFirst.setReversing(true);

        try
        {
            reversingFirst.setTerminus(true);
            fail("a reversing station must not be allowed to become a terminus");
        }
        catch (Exception expected)
        {
            assertTrue(reversingFirst.isReversing(), "and this one is unchanged too");
            assertFalse(reversingFirst.isTerminus());
        }

        // The control case: each alone is perfectly valid, or the rule above would be vacuous
        Point plainTerminus = new Point("Just a terminus", true, "103");
        Point plainReversing = new Point("Just reversing", true, "104");

        plainTerminus.setTerminus(true);
        plainReversing.setReversing(true);

        assertTrue(plainTerminus.isTerminus());
        assertFalse(plainTerminus.isReversing());

        assertTrue(plainReversing.isReversing());
        assertFalse(plainReversing.isTerminus());
    }

    /**
     * A point that omits "station" is a non-station, not a parse failure.
     *
     * Every other optional field on a point defaults; this one was read with getBoolean, which throws
     * when the key is absent.  The throw left the try block before createPoint ran, so the point was
     * dropped without a word - and the first sign of it was an edge reporting that one of its endpoints
     * did not exist.  In a file the operator edits by hand, that names the wrong line entirely.
     */
    @Test
    public void testAPointWithoutTheStationKeyIsANonStation()
    {
        String noKey = "{'name': 'IN C', 's88': 8882, 'x': 30, 'y': 30}";
        String edge = "{'start': 'IN A', 'end': 'IN C'}";

        Layout layout = Layout.fromJSON(config(POINT_A + ", " + noKey, edge, ""), model);

        assertNotNull(layout, "fromJSON returned null rather than a layout");

        assertTrue(layout.isValid(),
            "a point without the station key should load as a non-station - " + Layout.getLastError());

        assertNotNull(layout.getPoint("IN C"), "and the point itself has to exist");

        assertFalse(layout.getPoint("IN C").isDestination(),
            "defaulting to false means it is not a station");
    }

    /**
     * UC-C1: the update check must survive a release name with a non-numeric version component.
     *
     * parseReleaseVersion returns everything after the first "v"; compareVersions then parses every
     * dotted component as an integer.  The current beta is literally named
     * "Marklin Train Control v2.8.0 (Beta)" - the exact breaking shape - and one stable release
     * published like that makes every installed copy stop announcing updates, silently, with a log
     * line blaming the network.
     *
     * Asserted on the pair, so the fix may live in either half (strip the suffix in
     * parseReleaseVersion, or parse tolerantly in compareVersions).
     */
    @Test
    public void testUpdateCheckSurvivesASuffixedReleaseName()
    {
        String parsed = Util.parseReleaseVersion(
            new JSONObject().put("name", "Marklin Train Control v2.8.0 (Beta)"));

        assertEquals(Conversion.compareVersions("2.7.4", parsed), -1,
            "2.7.4 is older than the 2.8.0 beta, and saying so must not throw");

        assertEquals(Conversion.compareVersions(parsed, "2.8.0"), 0,
            "the suffix is not part of the version");

        assertEquals(Conversion.compareVersions(parsed, "2.8.1"), -1,
            "and ordering still works above it");
    }

    /**
     * UC-C2: a space after the comma in a feedback line must not silently flip the state.
     *
     * The feedback branch tests "1".equals(token) untrimmed, so "Feedback 3, 1" - one space - parses
     * as state CLEAR, and a condition waits for the opposite sensor edge.  The locfunc branch trims;
     * this one is the odd one out.
     */
    @Test
    public void testFeedbackStateTokenIsTrimmed() throws Exception
    {
        assertTrue(RouteCommand.fromLine("Feedback 3,1", false).getSetting(),
            "control: the unspaced form is state set");

        assertTrue(RouteCommand.fromLine("Feedback 3, 1", false).getSetting(),
            "one space after the comma must not turn state 1 into state 0");

        assertFalse(RouteCommand.fromLine("Feedback 3, 0", false).getSetting(),
            "control: a spaced 0 is still clear");
    }

    /**
     * UC-C3: a typo'd direction must be an error, not a silent reversal.
     *
     * The locdir branch maps every string that is not exactly "forward" to backward - "forwards",
     * "fwd", any typo - and executes it.  Every other malformed field in this parser produces the
     * friendly invalid-line error; the direction is the one mistake it drives instead.
     */
    @Test
    public void testATypodDirectionIsRefusedNotReversed() throws Exception
    {
        assertEquals(RouteCommand.fromLine("locdir,UC Loco,forward", false).getDirection(),
            Locomotive.locDirection.DIR_FORWARD, "control: forward parses");

        assertEquals(RouteCommand.fromLine("locdir,UC Loco,BACKWARD", false).getDirection(),
            Locomotive.locDirection.DIR_BACKWARD, "control: backward parses, case-insensitively");

        try
        {
            RouteCommand rc = RouteCommand.fromLine("locdir,UC Loco,forwards", false);

            fail("'forwards' must raise the invalid-line error, not quietly parse as "
                + rc.getDirection());
        }
        catch (Exception expected)
        {
            // the friendly checked error every other malformed field already gets
        }
    }

    /**
     * UC-C11: Point must fail where the mistake is made, not at save time.
     *
     * The public constructor accepts any string as an s88, and toJSON later does
     * Integer.valueOf(s88) - so new Point("x", true, "1a") explodes with an unchecked
     * NumberFormatException at save, far from the call that caused it.
     */
    @Test
    public void testAPointRejectsANonNumericS88AtConstruction()
    {
        try
        {
            new Point("UC bad s88", true, "1a");
            fail("a non-numeric s88 must be rejected at construction, not at save time");
        }
        catch (Exception expected)
        {
            // rejected where the mistake was made
        }
    }

    /**
     * UC-C11, the other trap: setMaxTrainLength(null) passes an inert assert, stores null, and
     * validateTrainLength later NPEs unboxing it.  Null must mean "no limit", like 0 does.
     */
    @Test
    public void testANullMaxTrainLengthMeansNoLimit() throws Exception
    {
        Point station = new Point("UC null length", true, "4711");
        MarklinLocomotive loc = model.newMM2Locomotive("UC C11 loco", 55);

        try
        {
            station.setMaxTrainLength(null);

            assertTrue(station.validateTrainLength(loc),
                "a station with no stated limit accepts any train - it must not NPE");
        }
        finally
        {
            model.deleteLoc("UC C11 loco");
        }
    }
}
