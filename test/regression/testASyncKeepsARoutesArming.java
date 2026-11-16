package regression;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Components;
import support.Reflect;
import support.StandInStation;
import support.StationRig;
import support.TcpStation;

/**
 * A route the user armed stays armed when a Central Station sync replaces it because its definition changed, and one
 * left unarmed stays unarmed (OB-433).  A firmware that drops its items' `typ` brings it in with no sensor: it keeps the
 * flag, cannot fire, and the next good sync arms it again.  A carried route fires on neither the sync's own sensor pass
 * nor a reconnect's confirmation (D133), only on real changes after.
 *
 * Each claim starts a model whose start-up sync finds a CS3 at a `support.StandInStation`, serving the fixture's routes
 * and one of its own, and connects over TCP to a `support.TcpStation`, the Link S88 the route's sensor is on.  The route
 * is armed through the door Enable Auto Execution uses, `editRoute`.
 *
 * @author Adam
 */
public class testASyncKeepsARoutesArming
{
    private static final long WAIT_MS = 5000;

    private static final String ROUTES = "/app/api/automatics";

    private static final BiFunction<String, Integer, byte[]> CS3 =
        StandInStation.cs3("260", "{\"softwareVersion\":\"2.6.1 (Build 3)\"}");

    /** The route's one switch, an article of the stand-in's accessory list. */
    private static final int ARTICLE = 14;

    private interface Claim
    {
        void run(StationRig rig, TcpStation station, AtomicReference<JSONObject> served) throws Exception;
    }

    /** The route, numbered and named after its sensor: the sensor then its switch at `stellung`, or both untyped. */
    private static JSONObject route(int contact, String stellung, boolean typed)
    {
        JSONObject s88 = new JSONObject().put("deviceId", "2").put("id", String.valueOf(contact)).put("mode", "1");
        JSONObject mag = new JSONObject().put("magnetartikel", String.valueOf(ARTICLE)).put("stellung", stellung);

        if (typed)
        {
            s88.put("typ", "s88");
            mag.put("typ", "mag");
        }

        return new JSONObject().put("id", String.valueOf(contact)).put("name", name(contact))
            .put("items", new JSONArray().put(s88).put(mag));
    }

    private static String name(int contact)
    {
        return "Arming claim " + contact;
    }

    /** Runs a claim with the route served in its first form, its sensor clear, and the start-up's confirmation over. */
    private static void connected(int contact, Claim claim) throws Exception
    {
        AtomicReference<JSONObject> served = new AtomicReference<>(route(contact, "1", true));

        BiFunction<String, Integer, byte[]> answer = (path, n) ->
        {
            if (!ROUTES.equals(path)) return CS3.apply(path, n);

            JSONArray list = new JSONArray(new String(CS3.apply(path, n), StandardCharsets.UTF_8));

            return list.put(served.get()).toString().getBytes(StandardCharsets.UTF_8);
        };

        try (TcpStation station = new TcpStation())
        {
            TcpStation.forSimulations(station.port(), () ->
            {
                try (StationRig rig = new StationRig(Files.createTempDirectory("tc-ob433").toFile(), answer, false, true))
                {
                    assertTrue(rig.model.isCS3(), "precondition: the stand-in was not taken for a CS3");
                    assertTrue(Components.waitFor(() -> station.connections() == 1, WAIT_MS),
                        "precondition: the model is not connected over TCP");
                    assertTrue(rig.model.getRoute(name(contact)) != null && rig.model.getRoute(name(contact)).hasS88(),
                        "precondition: the start-up sync did not import the route with its sensor");

                    station.sensor(contact, false);
                    assertTrue(shows(rig, contact, false), "precondition: the event did not arrive");
                    assertTrue(Components.waitFor(() -> !rig.model.isConfirming(), WAIT_MS),
                        "precondition: the connect's confirmation did not end");

                    try
                    {
                        claim.run(rig, station, served);
                    }
                    finally
                    {
                        for (MarklinRoute r : rig.model.getRoutes()) r.disable();
                    }
                }

                return null;
            });
        }
    }

    /** Enable Auto Execution's write: the route edited with its flag on. */
    private static MarklinRoute armed(StationRig rig, int contact)
    {
        MarklinRoute r = rig.model.getRoute(name(contact));

        assertTrue(rig.model.editRoute(r.getName(), r.getName(), r.getRoute(), r.getS88(), r.getTriggerType(), true,
            r.getConditions()), "precondition: the route was not armed");

        MarklinRoute armed = rig.model.getRoute(name(contact));

        assertTrue(Components.waitFor(() -> waitingFor(armed, true), WAIT_MS),
            "precondition: the armed route is not watching its sensor");

        return armed;
    }

    /** Serves the route as `route` is, syncs, and returns the route the model then holds, checked to be a new one. */
    private static MarklinRoute syncedAs(StationRig rig, AtomicReference<JSONObject> served, JSONObject route,
        MarklinRoute before)
    {
        served.set(route);
        assertTrue(rig.model.syncWithCS2() >= 0, "the sync failed: " + rig.model.getLastSyncError());

        MarklinRoute after = rig.model.getRoute(route.getString("name"));

        assertNotSame(after, before, "precondition: the sync did not replace the route");

        return after;
    }

    /**
     * Whether the route's own monitor is parked waiting for its sensor to be made (`occupied`) or to clear, asked of
     * its own thread: the route it replaced may still have one under the same name, parked until the sensor changes.
     */
    private static boolean waitingFor(MarklinRoute route, boolean occupied)
    {
        Thread own = ownWatch(route);
        String waiting = occupied ? "waitForOccupiedFeedback" : "waitForClearFeedback";

        if (own == null || own.getState() != Thread.State.WAITING) return false;

        for (StackTraceElement frame : own.getStackTrace())
        {
            if (waiting.equals(frame.getMethodName())) return true;
        }

        return false;
    }

    private static boolean watching(MarklinRoute route)
    {
        return waitingFor(route, true);
    }

    /** Whether the route has a monitor of its own at all. */
    private static boolean hasAWatch(MarklinRoute route)
    {
        return ownWatch(route) != null;
    }

    private static Thread ownWatch(MarklinRoute route)
    {
        try
        {
            return (Thread) Reflect.get(MarklinRoute.class, route, "monitorThread");
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("MarklinRoute has no monitorThread field to ask", e);
        }
    }

    private static boolean shows(StationRig rig, int contact, boolean occupied)
    {
        String name = Integer.toString(contact);

        return Components.waitFor(() -> rig.model.isFeedbackSet(name) && rig.model.getFeedbackState(name) == occupied,
            WAIT_MS);
    }

    /** How many times the route has fired by the log, `log` keeping what was taken before. */
    private static int fired(StationRig rig, List<String> log, int contact)
    {
        log.addAll(rig.takeLog());

        return Collections.frequency(log, I18n.f("route.s88Triggered", name(contact)));
    }

    /** Arms the route, then serves it as `as` is and syncs; the route the sync put in its place. */
    private static MarklinRoute armedThenSynced(StationRig rig, AtomicReference<JSONObject> served, int contact,
        JSONObject as)
    {
        MarklinRoute before = armed(rig, contact);

        rig.takeLog();

        return syncedAs(rig, served, as, before);
    }

    /** The route a sync put in an armed one's place is armed, watching its sensor, and the log says it stays armed. */
    private static void keptArmed(StationRig rig, MarklinRoute route, int contact, String what)
    {
        assertTrue(route.hasS88() && route.isEnabled(), "the sync that replaced an armed route disarmed it: " + what);
        assertTrue(Components.waitFor(() -> watching(route), WAIT_MS),
            "the route the sync kept armed is not watching its sensor: " + what);
        assertTrue(rig.takeLog().contains(I18n.f("route.syncKeptArmed", name(contact))),
            "the log does not say the replaced route stays armed: " + what);
    }

    /** The route goes back to waiting for its sensor to clear, having not fired. */
    private static void settlesUnfired(StationRig rig, MarklinRoute route, int contact, List<String> log, String what)
    {
        boolean settled = Components.waitFor(() -> waitingFor(route, false), WAIT_MS);

        assertEquals(fired(rig, log, contact), 0, "the carried route fired " + what + ": " + log);
        assertTrue(settled, "the carried route is not watching its sensor " + what + ": " + log);
    }

    /** The station clears the contact for real, and the route then waits for it to be made. */
    private static void realClear(TcpStation station, MarklinRoute route, int contact)
    {
        station.sensor(contact, false);

        assertTrue(Components.waitFor(() -> waitingFor(route, true), WAIT_MS),
            "the route does not wait for its sensor to be made after a real clear");
    }

    /**
     * An armed route whose switch the Central Station sets the other way is replaced by the sync, and the new route is
     * armed and watching its sensor; the log says so.
     *
     * MUTATION: in `MarklinControlStation.adoptCentralStationRoute`, take out `wasArmed = ...isEnabled();`.
     */
    @Test(timeOut = 120000)
    public void testAnArmedRouteASyncReplacesStaysArmed() throws Exception
    {
        int contact = 4331;

        connected(contact, (rig, station, served) ->
        {
            MarklinRoute after = armedThenSynced(rig, served, contact, route(contact, "0", true));

            keptArmed(rig, after, contact, "its switch set the other way");
            assertTrue(after.isLocked(), "the replaced route is not locked as a Central Station route");
        });
    }

    /**
     * A firmware that drops the items' `typ` brings an armed route in with no items and no sensor: it keeps its flag,
     * has nothing to watch, and the log says why it cannot fire.  The next good sync brings the sensor back armed.
     *
     * MUTATION: as testAnArmedRouteASyncReplacesStaysArmed.
     */
    @Test(timeOut = 120000)
    public void testAnArmedRouteSurvivesAFirmwareThatDropsItsTrigger() throws Exception
    {
        int contact = 4332;

        connected(contact, (rig, station, served) ->
        {
            MarklinRoute bare = armedThenSynced(rig, served, contact, route(contact, "1", false));

            assertTrue(bare.getRoute().isEmpty() && !bare.hasS88(), "precondition: the untyped items were read");
            assertTrue(bare.isEnabled(), "the sync that took the route's sensor away disarmed it");
            assertFalse(hasAWatch(bare), "a route with no sensor is watching one");
            assertTrue(rig.takeLog().contains(I18n.f("route.syncKeptArmedNoSensor", name(contact))),
                "the log does not say the route has no sensor to fire on");

            keptArmed(rig, syncedAs(rig, served, route(contact, "1", true), bare), contact, "its sensor brought back");
        });
    }

    /**
     * A route the user left unarmed stays unarmed through the same replaces, sensor gone and back, with no watch on its
     * sensor and nothing said of arming.
     *
     * MUTATION: in `MarklinControlStation.adoptCentralStationRoute`, `wasArmed = ...isEnabled();` to `wasArmed = true;`.
     */
    @Test(timeOut = 120000)
    public void testAnUnarmedRouteASyncReplacesStaysUnarmed() throws Exception
    {
        int contact = 4333;

        connected(contact, (rig, station, served) ->
        {
            MarklinRoute route = rig.model.getRoute(name(contact));
            assertFalse(route.isEnabled(), "precondition: a route from the station arrived armed");
            rig.takeLog();

            for (JSONObject as : new JSONObject[] {route(contact, "0", true), route(contact, "0", false),
                route(contact, "1", true)})
            {
                route = syncedAs(rig, served, as, route);

                assertFalse(route.isEnabled(), "a sync armed a route the user had left unarmed: " + as);
                assertFalse(hasAWatch(route), "an unarmed route is watching its sensor: " + as);
            }

            for (String line : rig.takeLog())
            {
                assertFalse(line.equals(I18n.f("route.syncKeptArmed", name(contact)))
                    || line.equals(I18n.f("route.syncKeptArmedNoSensor", name(contact))),
                    "the log says an unarmed route stays armed: " + line);
            }
        });
    }

    /**
     * The route a sync keeps armed does not fire on the sync itself: the station made its sensor unseen, and the sync's
     * pass of queries, which runs after the routes are in, puts it right.  Nor on a real event during a reconnect's
     * confirmation (D133).  The next real clear then made fires it once.
     *
     * MUTATION: in `MarklinRoute.executeAutoRoute`, take out the `continue` after `isFeedbackCorrected`: red on the
     * sync's own answer.
     */
    @Test(timeOut = 120000)
    public void testACarriedRouteFiresOnNeitherTheSyncNorTheConfirmation() throws Exception
    {
        int contact = 4334;

        connected(contact, (rig, station, served) ->
        {
            List<String> log = new ArrayList<>();
            int pad = contact + 1;

            station.sensor(pad, false);
            assertTrue(shows(rig, pad, false), "precondition: the second sensor's event did not arrive");
            MarklinRoute before = armed(rig, contact);
            rig.takeLog();

            // The answer waits until the new route watches for the made state, past the clear state's debounce that
            // would otherwise swallow it; the second sensor's answer waits until it is in, so it is not the pass's
            // last (OB-490)
            station.sensorUnseen(contact, true);
            station.onQuery(contact, () -> Components.waitFor(() -> rig.model.getRoute(name(contact)) != before
                && watching(rig.model.getRoute(name(contact))), WAIT_MS));
            station.onQuery(pad, () -> shows(rig, contact, true));
            int asked = station.queries().size();
            MarklinRoute after = syncedAs(rig, served, route(contact, "0", true), before);
            List<int[]> pass = station.queries().subList(asked, station.queries().size());
            assertTrue(pass.size() == 2 && pass.get(0)[1] == contact && pass.get(1)[1] == pad,
                "precondition: the sync did not ask the route's sensor first, then the second");
            assertTrue(shows(rig, contact, true), "precondition: the sync did not put the sensor right");
            settlesUnfired(rig, after, contact, log, "on the sync's own answer");

            realClear(station, after, contact);
            station.onQuery(contact, () -> station.sensor(contact, true));
            station.drop();
            assertTrue(shows(rig, contact, true), "precondition: the event during the confirmation did not arrive");
            assertTrue(Components.waitFor(() -> !rig.model.isConfirming(), WAIT_MS),
                "precondition: the reconnect's confirmation did not end");
            settlesUnfired(rig, after, contact, log, "during the confirmation");

            realClear(station, after, contact);
            station.sensor(contact, true);
            assertTrue(Components.waitFor(() -> fired(rig, log, contact) == 1, WAIT_MS),
                "real events after the confirmation did not fire the carried route: " + log);
        });
    }
}
