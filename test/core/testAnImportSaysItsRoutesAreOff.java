package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * An import of routes says that they arrive with their automatic firing off, and how to turn it on (REG-B3).
 *
 * Every route read from a file is disarmed, whatever the file says - Adam, 2026-09-10: *"they should not be armed.
 * The user can choose to do this when they are ready."*  That rule is pinned in `testRoutes`.  What was missing was
 * any word of it: at 2.8.1 an import restored each route as it was saved, and a user restoring a backup onto a new
 * computer found out when a train ran through a sensor that used to set a road or cut the power.
 *
 * **Runs only where the run has its own copy of the data.**  An import deletes every route the model holds before it
 * adds the file's, and in a run that shares `LocDB.data` with the application that would be the operator's routes.
 * `one.sh` and `battery.sh` give every run its own copy (`Util.DATA_DIR_PROPERTY`); anywhere else this skips.
 *
 * MUTATION: take the notice out of `importRoutes` and this fails.
 *
 * @author Adam
 */
public class testAnImportSaysItsRoutesAreOff
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    private static final List<String> logged = Collections.synchronizedList(new ArrayList<>());
    private static Handler tap;

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

        tap = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (tap != null) Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Two routes, one saved armed: both come in, and the log says they are off and where to turn them on.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAnImportSaysTheRoutesArriveOff() throws Exception
    {
        JSONArray routes = new JSONArray();

        for (int i = 0; i < 2; i++)
        {
            List<RouteCommand> commands = new ArrayList<>();
            commands.add(RouteCommand.RouteCommandAccessory(293 + i, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true));

            MarklinRoute fixture = new MarklinRoute(model, "REG-B3 probe " + i, 9830 + i, commands, 8870 + i,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            JSONObject saved = fixture.toJSON();

            // THE FIRST ONE WAS SAVED ARMED, which is what a 2.8.1 backup of a working railway holds.
            saved.put("auto", i == 0);

            routes.put(saved);
        }

        logged.clear();

        int added = model.importRoutes(new JSONObject().put("routes", routes).toString());

        assertEquals(added, 2, "precondition: the file's two routes were not both imported");

        String notice = I18n.f("route.infoImportedArriveDisarmed", 2, I18n.t("ui.main.bulkEnable"),
            I18n.t("route.ui.menuEnableAutoExecution"));

        assertTrue(logged.contains(notice), "an import of routes switched their automatic firing off - one of them"
            + " was saved armed - and said nothing about it: a train runs through the sensor that used to set a road"
            + " or cut the power before anybody knows (REG-B3).  Logged: " + logged);

        // AND NOTHING WAS ARMED ON THE WAY IN (REG2-C6).  A route built from a file that says "auto" started its
        // sensor monitor, which logs that the route is running, and was disarmed on the next line - so the log said
        // a route was running just before saying every route had arrived off.
        assertFalse(logged.contains(I18n.f("route.running", "REG-B3 probe 0")), "the route saved armed logged that"
            + " it was running while it was imported, just before the notice that the routes arrive off: it was"
            + " built armed and disarmed afterwards (REG2-C6).  Logged: " + logged);
    }

    /**
     * Asked, the import turns automatic firing back on for the routes the file saved with it on - and only those
     * (REG2-C7).
     *
     * Adam, 2026-09-24: *"save the state in the file on export, and ask the user on import.  if they want them armed,
     * arm them.  otherwise, don't."*  The export has always written each route's `auto`; the import threw it away, so a
     * restored backup came back with every route off and nothing said which had been on.
     *
     * MUTATION: ignore the answer, or arm every route, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test(dependsOnMethods = "testAnImportSaysTheRoutesArriveOff")
    public void testAnImportArmsWhatWasSavedArmedWhenAsked() throws Exception
    {
        JSONArray routes = new JSONArray();

        for (int i = 0; i < 2; i++)
        {
            List<RouteCommand> commands = new ArrayList<>();
            commands.add(RouteCommand.RouteCommandAccessory(303 + i, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true));

            MarklinRoute fixture = new MarklinRoute(model, "REG2-C7 probe " + i, 9840 + i, commands, 8880 + i,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            JSONObject saved = fixture.toJSON();

            saved.put("auto", i == 0);

            routes.put(saved);
        }

        String file = new JSONObject().put("routes", routes).toString();

        assertEquals(model.routesSavedArmed(file), Collections.singletonList("REG2-C7 probe 0"), "the routes the file"
            + " saved armed are not the one it saved armed");

        logged.clear();

        int added = model.importRoutes(file, true);

        assertEquals(added, 2, "precondition: the file's two routes were not both imported");

        assertTrue(model.getRoute("REG2-C7 probe 0").isEnabled(), "asked to, the import did not turn automatic firing"
            + " back on for the route the file saved with it on (REG2-C7)");

        assertFalse(model.getRoute("REG2-C7 probe 1").isEnabled(), "the import turned automatic firing on for a route"
            + " the file saved with it off");

        // AND IT IS WATCHING ITS SENSOR, as a route armed by hand is - the monitor says so when it starts.
        long until = System.currentTimeMillis() + 3000;

        while (!logged.contains(I18n.f("route.running", "REG2-C7 probe 0")) && System.currentTimeMillis() < until)
        {
            Thread.sleep(50);
        }

        assertTrue(logged.contains(I18n.f("route.running", "REG2-C7 probe 0")), "the route turned back on is not"
            + " watching its sensor.  Logged: " + logged);

        // NOT ASKED, NOTHING IS ARMED - the rule of 2026-09-10 stands for a No.
        model.importRoutes(file, false);

        assertFalse(model.getRoute("REG2-C7 probe 0").isEnabled(), "answered No, the import still turned automatic"
            + " firing on");
    }
}
