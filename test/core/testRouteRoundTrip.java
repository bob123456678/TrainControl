package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.traincontrol.base.RouteCommand;
import java.util.Arrays;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A route must survive the trip through the editor: rendered to text by toCSV, parsed back by
 * fromLine, and still be the same list of commands.
 *
 * That round trip is not a theoretical concern - it is what the route editor does every time it is
 * opened and saved (TrainControlUI populates the editor from toCSV, and RouteEditorFrame's save path parses
 * each line with fromLine).  It was broken for feedback commands: toLine ended every branch with a
 * newline except that one, and toCSV concatenates without a separator, so a feedback command was glued
 * to whatever followed it:
 *
 *     Feedback 5,1locspeed,MyLoc,50
 *
 * Which then parsed without error - the address came from the first field, and the state was evaluated
 * as "1".equals("1locspeed") - so no exception was raised, the feedback setting silently flipped to 0,
 * and the following command was deleted from the route.
 *
 * The second test runs the same invariant over the sample layout shipped with the repository: 83 real
 * routes, a good third of them conditional.  Hand-built cases only cover the shapes someone thought of.
 */
public class testRouteRoundTrip
{
    private static MarklinControlStation model;

    /** Relative to the project root, which is where the tests run (build-impl sets work.dir=basedir). */
    private static final String SAMPLE_ROUTES = "test/test_layout/config/gleisbilder/routes.json";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStationAddress.withNoStation(
            () -> MarklinControlStation.init(null, true, false, false, false));
        model.stop();
    }

    /**
     * Renders a route the way the editor does, then parses it back the way the editor's save does.
     */
    private static List<RouteCommand> roundTrip(MarklinRoute route) throws Exception
    {
        List<RouteCommand> parsed = new ArrayList<>();

        for (String line : route.toCSV().split("\n"))
        {
            if (line.trim().isEmpty()) continue;

            RouteCommand rc = RouteCommand.fromLine(line, false);

            if (rc != null) parsed.add(rc);
        }

        return parsed;
    }

    /**
     * The defect, at its smallest: a feedback command followed by anything.
     */
    @Test
    public void testFeedbackCommandDoesNotSwallowTheNextCommand() throws Exception
    {
        MarklinRoute route = new MarklinRoute(model, "RT feedback", 90001);

        route.addItem(RouteCommand.RouteCommandFeedback(5, true));
        route.addItem(RouteCommand.RouteCommandLocomotiveSpeed("RT loc", 50));

        String csv = route.toCSV();

        assertTrue(csv.contains("\n"),
            "the two commands must be on separate lines - concatenating them produced "
            + "'Feedback 5,1locspeed,...', which still parsed and silently dropped the second command");

        List<RouteCommand> parsed = roundTrip(route);

        assertEquals(parsed.size(), 2, "both commands must survive the editor round trip");
        assertTrue(parsed.get(0).isFeedback(), "the feedback command, still feedback");
        assertTrue(parsed.get(0).getSetting(), "and still set to 1, not flipped to 0 by the merge");
        assertTrue(parsed.get(1).isLocomotiveSpeed(), "and the command that used to be swallowed");
    }

    /**
     * A feedback command in the middle, so the failure cannot be masked by it being last.
     */
    @Test
    public void testFeedbackCommandBetweenTwoOthersKeepsBoth() throws Exception
    {
        MarklinRoute route = new MarklinRoute(model, "RT middle", 90002);

        route.addItem(RouteCommand.RouteCommandLocomotiveSpeed("RT loc", 10));
        route.addItem(RouteCommand.RouteCommandFeedback(7, false));
        route.addItem(RouteCommand.RouteCommandFunction("RT loc", 3, true));

        List<RouteCommand> parsed = roundTrip(route);

        assertEquals(parsed.size(), 3, "all three commands must survive");
        assertTrue(parsed.get(1).isFeedback());
        assertTrue(parsed.get(2).isFunction(), "the command after the feedback line must still be there");
    }

    /**
     * The same invariant over real data: every route in the repository's own sample layout renders and
     * parses back to the same number of commands.
     *
     * The file is not currently read by anything else in the suite, which is why a defect in the
     * round trip could go unnoticed - it ships with the project and is what the application falls back
     * to when no Central Station is present.
     */
    @Test
    public void testSampleLayoutRoutesSurviveTheRoundTrip() throws Exception
    {
        File file = new File(SAMPLE_ROUTES);

        assertTrue(file.exists(),
            "sample route data not found at " + file.getAbsolutePath()
            + " - if it moved, update this test rather than deleting it");

        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        List<MarklinRoute> routes = model.parseRoutesFromJson(json);

        assertNotNull(routes, "the sample route file must parse");
        assertTrue(routes.size() > 50,
            "expected the full sample set, got " + (routes == null ? 0 : routes.size()));

        int withCommands = 0;

        for (MarklinRoute route : routes)
        {
            if (route.getRoute().isEmpty()) continue;

            withCommands++;

            assertEquals(roundTrip(route).size(), route.getRoute().size(),
                "route '" + route.getName() + "' lost or gained a command in the editor round trip");
        }

        assertTrue(withCommands > 50,
            "only " + withCommands + " routes had commands - the fixture is not exercising much");
    }

    /**
     * A "train not at a sensor" condition comes back as "not at" from every form a route is kept in (FR-141).
     *
     * The text line (the condition CSV and the editor's paste), the JSON a route is saved, exported and imported as, the
     * locomotive database (Java serialization of the condition), and a duplicate of the route.  Any one of them dropping
     * the state turns the condition into its opposite, silently: "not at" read back as "at" fires the route exactly when
     * it was written not to.
     *
     * MUTATION: drop the SETTING from `RouteCommandAutoLocomotive` and every form below reads back as "at".
     */
    @Test
    public void testATrainNotAtASensorSurvivesEveryFormARouteIsKeptIn() throws Exception
    {
        RouteCommand notAt = RouteCommand.RouteCommandAutoLocomotive("RT loc", 21, false);

        assertFalse(notAt.isAtSensor(), "built as not at, and it says at");

        RouteCommand line = RouteCommand.fromLine(notAt.toLine(null), false);

        assertFalse(line.isAtSensor(), "the text line lost the not: " + notAt.toLine(null).trim());
        assertEquals(line, notAt, "the text line came back as a different condition");

        RouteCommand json = RouteCommand.fromJSON(notAt.toJSON());

        assertFalse(json.isAtSensor(), "the JSON lost the not: " + notAt.toJSON());
        assertEquals(json, notAt, "the JSON came back as a different condition");

        MarklinRoute route = new MarklinRoute(model, "RT not at", 90010,
            Arrays.asList(RouteCommand.RouteCommandAccessory(5, Accessory.accessoryDecoderType.MM2, true)), 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, NodeExpression.fromList(Arrays.asList(notAt)));

        // The route's JSON is what Save writes to routes.json and what Export and Import read and write
        List<MarklinRoute> reread = model.parseRoutesFromJson(
            "{\"routes\": [" + route.toJSON().toString() + "]}");

        assertEquals(reread.size(), 1, "the route did not read back from its own JSON");
        assertEquals(NodeExpression.toList(reread.get(0).getConditions()), Arrays.asList(notAt),
            "the route's saved JSON lost the not");

        // The condition CSV, read back the way a condition's text is parsed
        NodeExpression text = NodeExpression.fromTextRepresentation(route.getConditionCSV(), model);

        assertEquals(NodeExpression.toList(text), Arrays.asList(notAt),
            "the condition CSV lost the not: " + route.getConditionCSV());

        // The locomotive database keeps a route's conditions by Java serialization
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();

        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes))
        {
            out.writeObject(route.getConditions());
        }

        NodeExpression kept;

        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(
            new java.io.ByteArrayInputStream(bytes.toByteArray())))
        {
            kept = (NodeExpression) in.readObject();
        }

        assertEquals(NodeExpression.toList(kept), Arrays.asList(notAt), "the locomotive database lost the not");

        // A duplicate is made from the route's own commands and conditions, as TrainControlUI.duplicateRoute does
        String copy = "RT not at (copy FR-141)";

        assertTrue(model.newRoute(copy, route.getRoute(), route.getS88(), route.getTriggerType(), false,
            route.getConditions()), "precondition: the copy was not made");

        try
        {
            assertEquals(NodeExpression.toList(model.getRoute(copy).getConditions()), Arrays.asList(notAt),
                "the duplicate lost the not");
        }
        finally
        {
            model.deleteRoute(copy);
        }
    }

    /**
     * A train-at-a-sensor condition saved before FR-141 reads as "at", and is written back as it was.
     *
     * Every route saved until now holds NAME and ADDRESS and nothing else, and its text line three fields.  Those have to
     * mean what they always meant, and a route opened and saved again must not grow a field it did not have: the same
     * command, equal to one built today as "at".
     *
     * MUTATION: read a missing SETTING as "not at" in `isAtSensor` and this fails on the sample layout's routes.
     */
    @Test
    public void testATrainAtASensorSavedBeforeFR141StillReadsAsAt() throws Exception
    {
        String json = new String(Files.readAllBytes(new File(SAMPLE_ROUTES).toPath()), StandardCharsets.UTF_8);

        int found = 0;

        for (MarklinRoute route : model.parseRoutesFromJson(json))
        {
            if (route.getConditions() == null) continue;

            for (RouteCommand condition : NodeExpression.toList(route.getConditions()))
            {
                if (!condition.isAutoLocomotive()) continue;

                found++;

                assertTrue(condition.isAtSensor(), "route '" + route.getName() + "' was saved as at, and reads as not at");

                assertEquals(condition, RouteCommand.RouteCommandAutoLocomotive(condition.getName(),
                    condition.getAddress()), "route '" + route.getName() + "' does not equal the same condition built today");

                assertFalse(condition.toJSON().getJSONObject("state").has(RouteCommand.KEY_SETTING),
                    "route '" + route.getName() + "' would be saved back with a field it was not saved with");
            }
        }

        // Assert the variable: a sample with none would prove nothing
        assertTrue(found >= 2, "the sample layout's train-at-a-sensor conditions were not found, so this proved nothing");

        RouteCommand old = RouteCommand.fromLine("autoloc,EN57-203,10", false);

        assertTrue(old.isAtSensor(), "an old three-field line reads as not at");
        assertEquals(old.toLine(null).trim(), "autoloc,EN57-203,10", "an old line is written back differently");
    }

    /** One accessory line, exactly as capture and the editor write it. */
    private static String line(int address, boolean thrown)
    {
        return Accessory.toAccessorySettingString(Accessory.accessoryType.SWITCH, address, "MM2", thrown);
    }

    /**
     * Capturing a three-way keeps the released drive ahead of the thrown one.
     *
     * The diagram cycles straight - left - right, so capturing "right" takes two clicks and records
     * four lines, each click releasing one drive and throwing the other.  filterConfigCommands then
     * reduces them to one line per drive.  It used to keep the latest value at the earliest position,
     * and those two rules disagree: the pair came back as throw-before-release, which puts both blade
     * sets over at once - the one combination a three-way must never be given.
     */
    @Test
    public void testCapturingAThreeWayKeepsReleaseBeforeThrow()
    {
        String captured = line(6, false) + "\n" + line(5, true) + "\n"
                        + line(5, false) + "\n" + line(6, true);

        List<String> out = Arrays.asList(org.traincontrol.base.RouteCapture.filterConfigCommands(captured).split("\n"));

        assertEquals(out.size(), 2, "one line per drive: " + out);

        assertEquals(out.get(0), line(5, false), "the released drive has to come first");
        assertEquals(out.get(1), line(6, true), "and the thrown drive second");
    }

    /**
     * A three-way written as a condition can actually be saved.
     *
     * A condition is an expression, not a sequence.  The old editor's wizard emitted the pair's two
     * lines joined by a bare newline - the AND it inserts goes between whole entries, never inside
     * one - and NodeExpression rejects two adjacent operands, so the first conditional three-way a
     * user added made the entire condition unsaveable.
     *
     * The wizard is gone with the editor it belonged to, and ThreeWaySwitch owns the shape now.  The
     * test stays, rewritten against it, because what it is really about is the FORMAT: two accessory
     * lines are a route when a newline separates them and are nothing at all unless an AND does.
     * That is a property of the two writers agreeing, and it outlived the wizard.
     */
    @Test
    public void testAConditionalThreeWayCanBeParsed() throws Exception
    {
        List<RouteCommand> pair = org.traincontrol.base.ThreeWaySwitch.expand(5,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2,
            org.traincontrol.base.ThreeWaySwitch.Position.LEFT,
            org.traincontrol.base.ThreeWaySwitch.SETTLE);

        assertEquals(pair.size(), 2, "a three-way is two commands");

        // As a route: one line each, and the order is the whole point
        String asARoute = pair.get(0).toLine(null) + pair.get(1).toLine(null);

        try
        {
            NodeExpression.fromTextRepresentation(asARoute, null);
            fail("two operands with no operator between them must not parse: " + asARoute);
        }
        catch (Exception expected)
        {
            // the reason the separator has to differ between a route and a condition
        }

        // As a condition: the same two lines with an AND between them
        String asACondition = pair.get(0).toLine(null).trim() + "\nAND "
            + pair.get(1).toLine(null).trim();

        NodeExpression parsed = NodeExpression.fromTextRepresentation(asACondition, null);

        assertNotNull(parsed, "the condition must parse");
        assertEquals(NodeExpression.toList(parsed).size(), 2, "both drives belong to the condition");
    }

    /**
     * And the route form of the same pair still must NOT carry an operator - it is a sequence.
     */
    @Test
    public void testTheRouteFormOfAPairHasNoOperator()
    {
        List<RouteCommand> pair = org.traincontrol.base.ThreeWaySwitch.expand(5,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2,
            org.traincontrol.base.ThreeWaySwitch.Position.LEFT, 300);

        String route = pair.get(0).toLine(null) + pair.get(1).toLine(null);

        assertFalse(route.contains("AND"), "a route is executed in order, not evaluated: " + route);

        assertEquals(route.trim().split("\n").length, 2, "still two lines: " + route);

        assertTrue(route.trim().split("\n")[0].endsWith(",300"),
            "the pair's delay sits on its first line, which is what holds the second motor off "
            + "until the first has finished: " + route);
    }
}
