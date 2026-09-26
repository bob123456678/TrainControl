package core;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A route with an emergency stop has no other commands - Adam, 2026-09-25: *"if a route has emergency stop, it cannot
 * have any other types of commands.  Reject it from being created or imported as such.  This will keep a clean
 * separation."*  Asked what should become of his own route that has both, *"Auto Emergency Stop Bottom Secondary"*,
 * which throws switch 39 and then cuts the power: *"Split it automatically."*
 *
 * So a route that mixes them - loaded from the database, or read from a file - is split: it keeps its name, its sensor,
 * its conditions and whether it is armed, and fires a new stop-only route in its stop's place.  No door creates one:
 * the model refuses it, and the route editor will not save it (`ui.testCommandTableMarks`).  The route it becomes cuts
 * the power through the stop route, so it is still never asked about (`ui.testARouteOverATrainAtItsDoors`).
 *
 * **Runs only where the run has its own copy of the data**, as `testAnImportSaysItsRoutesAreOff` does: an import deletes
 * every route the model holds before it adds the file's.
 *
 * MUTATION: leave a mixed route as it came, from either door, or let the model make one, and this fails.
 *
 * @author Adam
 */
public class testAStopRouteStandsAlone
{
    private static final String HIS = "Auto Emergency Stop Bottom Secondary";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (System.getProperty(Util.DATA_DIR_PROPERTY) == null)
        {
            throw new SkipException("an import deletes every route in LocDB.data, so this runs only where the run has"
                + " its own copy of the data (one.sh, battery.sh)");
        }

        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * His own routes file, from the frozen copy of his railway, read back through Routes > Import's door: his mixed route
     * is split, armed as the file saved it, and the import names what it split.
     *
     * @throws Exception from the file or the model
     */
    @Test
    public void testAnImportSplitsARouteThatMixesAStop() throws Exception
    {
        JSONObject file = new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "test/layouts/live-snapshot/config/gleisbilder/routes.json")), java.nio.charset.StandardCharsets.UTF_8));

        JSONArray routes = file.getJSONArray("routes");

        JSONObject his = null;

        for (int i = 0; i < routes.length(); i++)
        {
            if (HIS.equals(routes.getJSONObject(i).optString("name"))) his = routes.getJSONObject(i);
        }

        assertNotNull(his, "precondition: his routes file has no " + HIS);

        // SAVED ARMED, so the split is seen to keep a route armed as the file saved it
        his.put("auto", true);

        int s88 = his.getInt("s88");

        model.importRoutes(file.toString(), true);

        String stop = I18n.f("route.stopRouteSplitName", HIS);

        assertTheSplit(HIS, stop, s88, true, 1);

        assertEquals(model.getRoutes().size(), routes.length() + 1, "the import did not add the one stop-only route it"
            + " split out of " + HIS);

        List<String[]> split = call("getRoutesSplitByLastImport");

        assertTrue(split.stream().anyMatch(pair -> HIS.equals(pair[0]) && stop.equals(pair[1])), "the import does not say"
            + " it split " + HIS);
    }

    /**
     * A route saved before the rule, loaded from the database - as the model's start-up restores each route - is split
     * by the step the start-up takes after its restore, and keeps the order of its commands.
     *
     * @throws Exception from the model or the source
     */
    @Test
    public void testALoadSplitsARouteThatMixesAStop() throws Exception
    {
        final String name = "SA loaded";

        if (!model.isFeedbackSet("48451")) model.newFeedback(48451, null);

        try
        {
            List<RouteCommand> mixed = new ArrayList<>();

            mixed.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));
            mixed.add(RouteCommand.RouteCommandStop());
            mixed.add(RouteCommand.RouteCommandAccessory(40, Accessory.accessoryDecoderType.MM2, false));

            // The door the start-up restores each saved route through
            assertTrue(model.newRoute(name, 84951, mixed, 48451, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null),
                "precondition: the route could not be restored");

            call("splitRoutesThatMixAStop");

            assertTheSplit(name, I18n.f("route.stopRouteSplitName", name), 48451, true, 1);

            assertEquals(model.getRoute(name).getRoute().size(), 3, "the split did not keep the route's other commands");

            assertEquals(model.getRoute(name).getRoute().get(2).getAddress(), 40, "the split did not keep the order of"
                + " the route's commands: the one after the stop is not last");

            // AND THE START-UP TAKES THAT STEP, after every saved route is restored
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/org/traincontrol/marklin/MarklinControlStation.java")), java.nio.charset.StandardCharsets.UTF_8);

            int restored = source.indexOf("this.restoreState(Util.dataPath(MarklinControlStation.DATA_FILE_NAME))");
            int splitAt = source.indexOf("this.splitRoutesThatMixAStop();", restored);
            int said = source.indexOf("this.logf(\"log.restored\");", restored);

            assertTrue(restored > 0 && splitAt > restored && splitAt < said, "the start-up does not split a route that"
                + " mixes an emergency stop with other commands, after it restores the saved routes");
        }
        finally
        {
            for (String each : new String[] {name, I18n.f("route.stopRouteSplitName", name)})
            {
                if (model.getRoute(each) != null)
                {
                    model.getRoute(each).disable();
                    model.deleteRoute(each);
                }
            }
        }
    }

    /**
     * Neither creating nor editing a route puts an emergency stop among other commands; a stop on its own is made.
     *
     * @throws Exception from the model
     */
    @Test
    public void testNoDoorCreatesARouteThatMixesAStop() throws Exception
    {
        List<RouteCommand> mixed = new ArrayList<>();

        mixed.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));
        mixed.add(RouteCommand.RouteCommandStop());

        List<RouteCommand> plain = new ArrayList<>();

        plain.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

        List<RouteCommand> alone = new ArrayList<>();

        alone.add(RouteCommand.RouteCommandStop());

        try
        {
            assertFalse(model.newRoute("SA made", mixed, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "a route with an emergency stop among other commands was created");

            assertNull(model.getRoute("SA made"), "a route refused is in the route list");

            assertTrue(model.newRoute("SA plain", plain, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "precondition: a route of one switch could not be created");

            assertFalse(model.editRoute("SA plain", "SA plain", mixed, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
                false, null), "a route was edited into an emergency stop among other commands");

            assertEquals(model.getRoute("SA plain").getRoute().size(), 1, "the refused edit changed the route");

            assertTrue(model.newRoute("SA alone", alone, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "CONTROL: a route of an emergency stop alone could not be created");
        }
        finally
        {
            for (String each : new String[] {"SA made", "SA plain", "SA alone"})
            {
                if (model.getRoute(each) != null) model.deleteRoute(each);
            }
        }
    }

    /**
     * The split: the stop alone in its own route, fired by nothing but the route it came from; that route without a stop,
     * firing the stop route in its place, armed as it was, on its own sensor, and still never asked about.
     */
    private static void assertTheSplit(String name, String stop, int s88, boolean armed, int firedAt)
    {
        MarklinRoute parent = model.getRoute(name);
        MarklinRoute alone = model.getRoute(stop);

        assertNotNull(parent, name + " is gone");

        assertNotNull(alone, name + ", which mixes an emergency stop with other commands, was not split: no route " + stop);

        assertEquals(alone.getRoute().size(), 1, "the stop-only route " + stop + " has other commands");
        assertTrue(alone.getRoute().get(0).isStop(), "the stop-only route " + stop + " has no emergency stop");
        assertFalse(alone.isEnabled(), "the stop-only route " + stop + " is armed on a sensor of its own");

        assertFalse(parent.getRoute().stream().anyMatch(command -> command.isStop()), name + " still has its emergency"
            + " stop among its other commands");

        RouteCommand fires = parent.getRoute().get(firedAt);

        assertTrue(fires.isRoute() && stop.equals(fires.getName()), name + " does not fire " + stop + " where its stop"
            + " stood: " + parent.getRoute());

        assertEquals(parent.getS88(), s88, "the split moved " + name + " off its sensor");
        assertEquals(parent.isEnabled(), armed, "the split changed whether " + name + " is armed");

        assertTrue(parent.hasEmergencyStop(), name + " cuts the power through " + stop + " and is not counted as a route"
            + " that does - so it would be asked about, where Adam's ruling of 2026-09-01 says a stop never is");
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(String method) throws Exception
    {
        try
        {
            return (T) MarklinControlStation.class.getMethod(method).invoke(model);
        }
        catch (NoSuchMethodException none)
        {
            throw new AssertionError("the model has no " + method + ": nothing splits a route that mixes an emergency"
                + " stop with other commands", none);
        }
    }
}
