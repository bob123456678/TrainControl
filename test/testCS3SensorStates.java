import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.model.View;
import org.traincontrol.util.I18n;

/**
 * A CS3's sensors' true states are asked for at start-up and at each Sync with Central Station, and no S88 route fires
 * on them until the CS3 has confirmed them (OB-418, D133, OB-490).  A CS2 is never asked.
 *
 * Each claim starts a station whose start-up sync reads a stand-in web server on this machine, which answers as a CS3
 * (or a CS2) whose device list has one Link S88 at device 9, placing contacts 3001 to 3496.  Nothing goes on the wire:
 * the proxy's sends are taken by the stand-in Link S88, which answers each query for its own device with the state it
 * holds, as a datagram to the proxy's own port, the way the station's frames arrive.  The contacts are above the owner's
 * real ones, whose live frames may reach the port too.
 */
public class testCS3SensorStates
{
    private static final int DEVICE = 9;
    private static final long WAIT_MS = 5000;

    /** Longer than a route's wait for a steady state (Locomotive.FEEDBACK_DURATION_THRESHOLD). */
    private static final long SETTLE_MS = 600;

    private static File layoutFolder;
    private static DatagramSocket sender;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // It writes the real preference node, which only docs/tools/one.sh replaces with a node of its own
        if (System.getProperty(org.traincontrol.util.Util.PREFERENCES_PROPERTY, "").trim().isEmpty())
            throw new org.testng.SkipException("needs docs/tools/one.sh");

        // The diagram is read from a folder, through the setting a user picks it with
        layoutFolder = Files.createTempDirectory("tc-cs3-sensors").toFile();
        File pages = new File(layoutFolder, "config/gleisbilder");
        assertTrue(pages.mkdirs(), "the fixture could not make " + pages);
        write(new File(layoutFolder, "config/gleisbild.cs2"), "[gleisbild]", "version", " .major=1", "seite", " .id=1",
            " .name=Main");
        write(new File(pages, "Main.cs2"), "[gleisbildseite]", "version", " .major=1", "element", " .id=0x10001",
            " .typ=linksweiche", " .artikel=90");

        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF,
            layoutFolder.getAbsolutePath().replace(File.separatorChar, '/'));

        sender = new DatagramSocket(0, InetAddress.getLoopbackAddress());
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        TrainControlUI.getPrefs().remove(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF);
        sender.close();

        for (String name : new String[]{ "config/gleisbilder/Main.cs2", "config/gleisbilder", "config/gleisbild.cs2",
            "config", "" })
        {
            Files.deleteIfExists(new File(layoutFolder, name).toPath());
        }
    }

    private static void write(File file, String... lines) throws Exception
    {
        Files.write(file.toPath(), String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8));
    }

    /** The station's address points at the web server; its sends go to the stand-in Link S88. */
    private static class Station extends NetworkProxy
    {
        final String ip;

        /** The queries sent, as device/contact. */
        final List<String> queries = new CopyOnWriteArrayList<>();

        /** The Link S88's contacts and their states: a contact not here is not answered. */
        final Map<Integer, Boolean> link = new ConcurrentHashMap<>();

        /** How late each contact's answer comes, in ms. */
        final Map<Integer, Long> late = new ConcurrentHashMap<>();

        Station(String ip) throws IOException
        {
            super(InetAddress.getLoopbackAddress());
            this.ip = ip;
        }

        @Override
        public String getIP()
        {
            return this.ip;
        }

        @Override
        public boolean sendMessage(CANMessage m)
        {
            byte[] f = m.getRawMessage();

            if ((f[1] & 0xff) == CS2Message.CMD_ACC_SENSOR << 1 && f[4] == 4)
            {
                int device = (f[5] & 0xff) << 8 | (f[6] & 0xff);
                int contact = (f[7] & 0xff) << 8 | (f[8] & 0xff);
                Boolean state = this.link.get(contact);

                this.queries.add(device + "/" + contact);

                if (device == DEVICE && state != null) frame(contact, state, state, this.late.getOrDefault(contact, 0L));
            }

            return true;
        }
    }

    /** A sensor frame from the Link S88, sent to the station's port `ms` from now: an event, or an answer when old == new. */
    private static void frame(int contact, boolean old, boolean now, long ms)
    {
        frame(DEVICE, contact, old, now, ms);
    }

    /** The same from another device. */
    private static void frame(int device, int contact, boolean old, boolean now, long ms)
    {
        byte[] f = new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x7b34, true, new byte[]
            {(byte) (device >> 8), (byte) device, (byte) (contact >> 8), (byte) contact, (byte) (old ? 1 : 0),
            (byte) (now ? 1 : 0), 0, 0}).getRawMessage();

        Runnable send = () ->
        {
            try
            {
                sender.send(new DatagramPacket(f, f.length, InetAddress.getLoopbackAddress(), NetworkProxy.RX_PORT));
            }
            catch (IOException e)
            {
                throw new AssertionError("the stand-in could not send", e);
            }
        };

        if (ms == 0)
        {
            send.run();
            return;
        }

        Thread later = new Thread(() ->
        {
            try
            {
                Thread.sleep(ms);
            }
            catch (InterruptedException e)
            {
                return;
            }

            send.run();
        }, "stand-in late answer");

        later.setDaemon(true);
        later.start();
    }

    /** A station started against a stand-in CS3 or CS2, its log recorded. */
    private static final class Rig implements AutoCloseable
    {
        final HttpServer web;
        final Station network;
        final MarklinControlStation model;
        final List<String> log = new CopyOnWriteArrayList<>();
        final Map<String, String> files = new ConcurrentHashMap<>();
        final Handler handler = new Handler()
        {
            @Override public void publish(LogRecord r) { log.add(r.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        };

        Rig(boolean cs3) throws Exception
        {
            this.files.put("/config/geraet.vrs", "[geraet]\nversion\n .minor=1\ngeraet\n .sernum=1\n .hardvers="
                + (cs3 ? "Central Station 3" : "60214") + "\n");
            this.files.put("/config/fahrstrassen.cs2", "[fahrstrassen]\nversion\n .minor=4\n");
            this.files.put("/config/magnetartikel.cs2", "[magnetartikel]\nversion\n .minor=1\n");
            this.files.put("/config/lokomotive.cs2", "[lokomotive]\nversion\n .minor=3\n");
            this.files.put("/app/api/locos", "[]");
            this.files.put("/app/api/loks", "[]");
            this.files.put("/app/api/mags", "[]");
            this.files.put("/app/api/automatics", "[]");

            // One Link S88 at device 9: keyboard matrix on 1-16, 31 modules on its third bus
            this.files.put("/app/api/devs", "[{\"kennung\":\"0x9\",\"typ\":\"64\",\"name\":\"Link S88\",\"kanal\":["
                + "{\"name\":\"Auswertung 1 - 16\",\"wert\":1},{\"name\":\"L\u00e4nge Bus 3\",\"wert\":31}]}]");

            this.web = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.web.createContext("/", exchange ->
            {
                String body = this.files.get(exchange.getRequestURI().getPath());

                if (body == null)
                {
                    exchange.sendResponseHeaders(404, -1);
                }
                else
                {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);

                    try (OutputStream out = exchange.getResponseBody())
                    {
                        out.write(bytes);
                    }
                }

                exchange.close();
            });
            this.web.start();

            Logger.getLogger(MarklinControlStation.class.getName()).addHandler(this.handler);

            View view = (View) Proxy.newProxyInstance(View.class.getClassLoader(), new Class<?>[]{ View.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "recording window";
                        case "getAllLocButtonMappings": return new ArrayList<String>();
                        default: return null;
                    }
                });

            this.network = new Station("127.0.0.1:" + this.web.getAddress().getPort());
            this.model = new MarklinControlStation(this.network, view, false, false);

            assertEquals(this.model.isCS3(), cs3, "precondition: the stand-in was not taken for the station it is");
            assertTrue(this.model.getNetworkCommState(), "precondition: the start-up sync failed: " + this.log);
        }

        /** What MarklinControlStation.init does once the window is up: the proxy gets its model and listens. */
        void start()
        {
            this.network.setModel(this.model);
        }

        boolean shows(int contact, boolean occupied)
        {
            String name = Integer.toString(contact);

            return waitFor(() -> this.model.isFeedbackSet(name) && this.model.getFeedbackState(name) == occupied);
        }

        /** How many times the route has fired. */
        long fired(String route)
        {
            String line = I18n.f("route.s88Triggered", route);

            return this.log.stream().filter(line::equals).count();
        }

        /** A route armed on the contact, set to wait for the trigger. */
        MarklinRoute armed(String name, int contact, MarklinRoute.s88Triggers trigger)
        {
            List<RouteCommand> commands = new ArrayList<>(Arrays.asList(
                RouteCommand.RouteCommandAccessory(90, Accessory.accessoryDecoderType.MM2, true)));

            assertTrue(this.model.newRoute(name, commands, contact, trigger, true, null), "precondition: no route " + name);

            return this.model.getRoute(name);
        }

        @Override
        public void close() throws Exception
        {
            for (MarklinRoute r : this.model.getRoutes()) r.disable();

            Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(this.handler);

            Field socket = NetworkProxy.class.getDeclaredField("socket");
            socket.setAccessible(true);
            ((DatagramSocket) socket.get(this.network)).close();

            this.web.stop(0);
        }
    }

    private static boolean waitFor(BooleanSupplier condition)
    {
        long until = System.currentTimeMillis() + WAIT_MS;

        while (!condition.getAsBoolean())
        {
            if (System.currentTimeMillis() > until) return false;

            try
            {
                Thread.sleep(20);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return true;
    }

    /**
     * The start-up asks a CS3 each known sensor's state at the device its Link S88 is listed at, and so does a Sync; the
     * answers put each sensor right, one shown clear that is occupied and one the other way round.
     *
     * MUTATION: in `NetworkProxy.setModel`, take out `if (holding != null) ask(holding);`.  And: in
     * `MarklinControlStation.syncWithCS2`, take out the `syncSensors` call under `if (this.on)`.
     */
    @Test(timeOut = 120000)
    public void testTheStartUpAndASyncAskACS3ItsSensorsTrueStates() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            rig.network.link.put(3401, true);
            rig.network.link.put(3402, false);
            rig.model.newFeedback(3401, null);
            rig.model.newFeedback(3402, null).setState(true);

            rig.start();

            assertTrue(rig.network.queries.containsAll(Arrays.asList("9/3401", "9/3402")),
                "the start-up did not ask each sensor at its Link S88's device: " + rig.network.queries);
            assertTrue(rig.shows(3401, true) && rig.shows(3402, false), "the start-up's answers did not set the sensors");

            // Both change while no event reaches TrainControl
            rig.network.link.put(3401, false);
            rig.network.link.put(3402, true);
            rig.network.queries.clear();

            assertTrue(rig.model.syncWithCS2() >= 0, "the sync failed: " + rig.log);
            assertTrue(rig.network.queries.containsAll(Arrays.asList("9/3401", "9/3402")),
                "the sync did not ask each sensor at its Link S88's device: " + rig.network.queries);
            assertTrue(rig.shows(3401, false) && rig.shows(3402, true), "the sync's answers did not set the sensors");
        }
    }

    /**
     * A CS2 is never asked, at start-up or at a Sync, even for a sensor whose events and device list name a device.
     *
     * MUTATION: in `MarklinControlStation.syncWithCS2`, `if (this.isCS3)` before `knowDevices` to `if (true)`.
     */
    @Test(timeOut = 120000)
    public void testACS2IsNeverAsked() throws Exception
    {
        try (Rig rig = new Rig(false))
        {
            rig.network.link.put(3403, true);
            rig.model.newFeedback(3403, null);

            rig.start();
            frame(3403, false, true, 0);
            assertTrue(rig.shows(3403, true), "precondition: the event did not arrive");

            assertTrue(rig.model.syncWithCS2() >= 0, "the sync failed: " + rig.log);
            assertEquals(rig.network.queries, new ArrayList<String>(), "a CS2 was asked for sensor states");
        }
    }

    /**
     * No S88 route fires until the CS3 has confirmed the sensors after the connect (D133): a sensor that changes during
     * the start-up's confirmation is put right but fires nothing, and after it the next real change does.  The change
     * comes once the first query is out, or after the wait when none comes, and once the route has settled on the clear
     * state: a change inside its wait for a steady clear would be no edge at all.
     *
     * MUTATION: in `MarklinRoute.executeAutoRoute`, take out the `continue` after `isFeedbackCorrected`.  And: in
     * `NetworkProxy.beginAsk`, `boolean holding = !this.connectAsked;` to `boolean holding = false;`.
     */
    @Test(timeOut = 120000)
    public void testARouteFiresOnlyOnceTheStartUpHasConfirmedTheSensors() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            int contact = 3405;
            String name = "Sensor claim " + contact;

            rig.model.newFeedback(contact, null);
            rig.armed(name, contact, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED);

            // Unanswered, so the confirmation lasts its whole wait
            rig.model.newFeedback(3406, null);
            Thread.sleep(SETTLE_MS);

            Thread starting= new Thread(rig::start, "claim start-up");
            starting.start();

            waitFor(() -> !rig.network.queries.isEmpty());
            frame(contact, false, true, 0);

            starting.join(WAIT_MS * 2);
            assertTrue(rig.shows(contact, true), "precondition: the change did not arrive");
            Thread.sleep(SETTLE_MS);
            assertEquals(rig.fired(name), 0, "a change during the start-up's confirmation fired the route");

            frame(contact, true, false, 0);
            assertTrue(rig.shows(contact, false), "precondition: the clear did not arrive");
            Thread.sleep(SETTLE_MS);
            frame(contact, false, true, 0);

            assertTrue(waitFor(() -> rig.fired(name) == 1), "a real change after the confirmation did not fire the route");
        }
    }

    /**
     * A sync's answer that comes late, its parse held up until the sync is over, is still the sync's own (OB-490): it puts
     * the sensor right and fires no armed route; the next real changes do.
     *
     * MUTATION: in `NetworkProxy.learn`, take out the `heardDuring` call.
     */
    @Test(timeOut = 120000)
    public void testALateAnswerToASyncFiresNoRoute() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            int contact = 3410;
            String name = "Sensor claim " + contact;

            rig.start();

            MarklinFeedback feedback = rig.model.newFeedback(contact, null);
            rig.armed(name, contact, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED);
            rig.network.link.put(contact, true);
            rig.network.late.put(contact, 300L);

            parsedAfter(feedback, () -> assertTrue(rig.model.syncWithCS2() >= 0, "the sync failed: " + rig.log));

            assertTrue(rig.shows(contact, true), "the sync's late answer did not put the sensor right");
            Thread.sleep(SETTLE_MS);
            assertEquals(rig.fired(name), 0, "a late answer to the sync fired the route");

            frame(contact, true, false, 0);
            assertTrue(rig.shows(contact, false), "precondition: the clear did not arrive");
            Thread.sleep(SETTLE_MS);
            frame(contact, false, true, 0);

            assertTrue(waitFor(() -> rig.fired(name) == 1), "real changes after the sync did not fire the route");
        }
    }

    /**
     * A route a sync replaces, its definition changed at the station, keeps its arming (OB-433), and fires on the next
     * real changes.
     *
     * MUTATION: in `MarklinControlStation.syncWithCS2`, take out `r.enable();` under `if (wasArmed)`.
     */
    @Test(timeOut = 120000)
    public void testARouteASyncReplacesStaysArmed() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            int contact = 3420;
            String name = "Sensor claim " + contact;
            String route = "[{\"id\":77,\"name\":\"" + name + "\",\"items\":[{\"typ\":\"s88\",\"id\":" + contact
                + ",\"mode\":0%s}]}]";

            rig.start();
            rig.files.put("/app/api/automatics", String.format(route, ""));
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync failed: " + rig.log);

            MarklinRoute first = rig.model.getRoute(name);
            assertTrue(first != null && first.hasS88(), "precondition: the station's route was not imported");
            first.enable();
            first.executeAutoRoute();

            // The station's route now waits for occupied, then clear
            rig.files.put("/app/api/automatics", String.format(route, ",\"value\":1"));
            assertTrue(rig.model.syncWithCS2() >= 0, "the second sync failed: " + rig.log);

            MarklinRoute replaced = rig.model.getRoute(name);
            assertTrue(replaced != first, "precondition: the sync did not replace the route");
            assertEquals(replaced.getTriggerType(), MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
                "precondition: the route did not change");
            assertTrue(replaced.isEnabled(), "a route the sync replaced lost its arming");

            frame(contact, false, true, 0);
            assertTrue(rig.shows(contact, true), "precondition: the change did not arrive");
            Thread.sleep(SETTLE_MS);
            frame(contact, true, false, 0);

            assertTrue(waitFor(() -> rig.fired(name) == 1), "the replaced route did not fire on real changes");
        }
    }

    /**
     * A sensor change that queued on the station's port before the proxy had its model is read during the start-up's
     * confirmation, and fires no route (D133).
     *
     * MUTATION: in `NetworkProxy.beginAsk`, `boolean holding = !this.connectAsked;` to `boolean holding = false;`.
     */
    @Test(timeOut = 120000)
    public void testAChangeHeardBeforeTheStartUpFiresNoRoute() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            int contact = 3407;
            String name = "Sensor claim " + contact;

            rig.model.newFeedback(contact, null);
            rig.armed(name, contact, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED);
            Thread.sleep(SETTLE_MS);

            frame(contact, false, true, 0);
            Thread.sleep(50);
            rig.start();

            assertTrue(rig.shows(contact, true), "precondition: the change did not arrive");
            Thread.sleep(SETTLE_MS);
            assertEquals(rig.fired(name), 0, "a change heard before the start-up's confirmation fired the route");
        }
    }

    /**
     * A later Sync's pass holds no route (D139): a real change that arrives while it waits for its answers fires the
     * route, as it would outside the pass.
     *
     * MUTATION: in `NetworkProxy.beginAsk`, `boolean holding = !this.connectAsked;` to `boolean holding = true;`.  And:
     * in `MarklinFeedback.parseMessage`, `confirming || (asking && answer)` to `confirming || asking`.
     */
    @Test(timeOut = 120000)
    public void testARealChangeDuringALaterSyncFiresTheRoute() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            int contact = 3440;
            String name = "Sensor claim " + contact;

            rig.model.newFeedback(contact, null);

            // Unanswered, so the pass lasts its whole wait
            rig.model.newFeedback(3441, null);
            rig.start();
            rig.armed(name, contact, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED);
            Thread.sleep(SETTLE_MS);
            rig.network.queries.clear();

            Thread sync = new Thread(() -> rig.model.syncWithCS2(), "claim sync");
            sync.start();

            assertTrue(waitFor(() -> rig.network.queries.contains("9/3441")), "precondition: the pass did not start");
            frame(contact, false, true, 0);
            Thread.sleep(SETTLE_MS);
            assertTrue(rig.network.isAsking(), "precondition: the change did not come during the pass");

            sync.join(WAIT_MS * 2);
            assertTrue(waitFor(() -> rig.fired(name) == 1), "a real change during a later sync's pass did not fire the route");
        }
    }

    /**
     * An answer older than a real change since its query is dropped (OB-418): the station clears the contact once asked,
     * and its answer from before, "occupied", comes after the clear.  The contact stays clear.
     *
     * MUTATION: in `NetworkProxy.learn`, the last `return event != null && ...` to `return false;`.
     */
    @Test(timeOut = 120000)
    public void testAnAnswerOlderThanAChangeIsDropped() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            int contact = 3445;

            rig.model.newFeedback(contact, null);
            rig.start();
            frame(contact, false, true, 0);
            assertTrue(rig.shows(contact, true), "precondition: the change did not arrive");

            rig.network.link.put(contact, true);
            rig.network.late.put(contact, 300L);
            rig.network.queries.clear();

            Thread sync = new Thread(() -> rig.model.syncWithCS2(), "claim sync");
            sync.start();
            assertTrue(waitFor(() -> rig.network.queries.contains("9/" + contact)), "precondition: the contact was not asked");

            // The real clear, after the query and before its answer; the second pass is not answered
            rig.network.link.remove(contact);
            frame(contact, true, false, 0);

            sync.join(WAIT_MS * 2);
            Thread.sleep(SETTLE_MS);
            assertFalse(rig.model.getFeedbackState(Integer.toString(contact)),
                "an answer older than a real change put the sensor back to occupied");
        }
    }

    /**
     * The device a real change showed for a sensor is saved, and the start-up after a restart asks the sensor there: here
     * a contact the Link S88 list does not place.
     *
     * MUTATION: in `MarklinControlStation.s88DeviceToSave`, return null.
     */
    @Test(timeOut = 120000)
    public void testADeviceIsSavedAndAskedAfterARestart() throws Exception
    {
        // It saves LocDB.data, which only one.sh's data folder keeps out of the working folder
        if (System.getProperty(org.traincontrol.util.Util.DATA_DIR_PROPERTY) == null)
            throw new org.testng.SkipException("needs traincontrol.dataDir (docs/tools/one.sh)");

        int contact = 3500;

        try (Rig rig = new Rig(true))
        {
            rig.model.newFeedback(contact, null);
            rig.start();
            frame(12, contact, false, true, 0);
            assertTrue(rig.shows(contact, true), "precondition: the change did not arrive");
            rig.model.saveState(false);
        }

        try (Rig rig = new Rig(true))
        {
            assertTrue(rig.model.isFeedbackSet(Integer.toString(contact)), "precondition: the sensor did not come back");
            rig.start();
            assertTrue(rig.network.queries.contains("12/" + contact), "the saved device was not asked: "
                + rig.network.queries);
        }
    }

    /**
     * A Sync on the window's thread does not hold the window while the CS3 answers: once its pass is out, the window
     * runs the next thing it is given at once, while the pass still waits.
     *
     * MUTATION: in `MarklinControlStation.syncWithCS2`, `syncSensors(!javax.swing.SwingUtilities.isEventDispatchThread())`
     * to `syncSensors(true)`.
     */
    @Test(timeOut = 120000)
    public void testASyncOnTheWindowsThreadLeavesTheWindowFree() throws Exception
    {
        try (Rig rig = new Rig(true))
        {
            rig.start();

            // Unanswered, so the pass lasts its whole wait, about 1.5 s
            rig.model.newFeedback(3470, null);
            rig.network.queries.clear();

            SwingUtilities.invokeLater(() -> rig.model.syncWithCS2());
            assertTrue(waitFor(() -> rig.network.queries.contains("9/3470")), "precondition: the pass did not start");

            long start = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> { });
            long waited = (System.nanoTime() - start) / 1000000L;
            boolean asking = rig.network.isAsking();

            assertTrue(waitFor(() -> !rig.network.isAsking()), "precondition: the pass did not end");
            assertTrue(asking && waited < 500, "the window waited " + waited + " ms for the sync's sensor pass");
        }
    }

    private interface Step
    {
        void run() throws Exception;
    }

    /**
     * Runs `step` while another thread holds the sensor's monitor, so the feedback thread parses what arrives meanwhile
     * only once `step` returns, as a busy feedback thread may.
     */
    private static void parsedAfter(MarklinFeedback feedback, Step step) throws Exception
    {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch over = new CountDownLatch(1);

        Thread holder = new Thread(() ->
        {
            synchronized (feedback)
            {
                held.countDown();

                try
                {
                    over.await();
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }, "claim holder");

        holder.setDaemon(true);
        holder.start();
        assertTrue(held.await(WAIT_MS, TimeUnit.MILLISECONDS), "precondition: the monitor was not held");

        try
        {
            step.run();
        }
        finally
        {
            over.countDown();
        }
    }
}
