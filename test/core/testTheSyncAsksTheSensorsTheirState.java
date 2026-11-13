package core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.MarklinSimpleComponent;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import support.Components;
import support.RouteMonitor;
import support.StandInStation;
import support.StationRig;
import support.TcpStation;

/**
 * Sync with Central Station asks a CS3 for its sensors' true states and puts TrainControl's right (OB-418).  Read-only
 * on the station: the only frame it adds is the s88 query (0x11, device and contact), sent only for a contact whose
 * device is known, at that device: the one a real event showed, else the one the station's device list gives, else the
 * one saved before a restart.  The sync after start-up asks too.  An answer older
 * than an event since its query never wins, and a station that does not answer holds the sync up by the resync's bound
 * only.  A CS2's sync asks nothing: its goldens hold.
 *
 * Each claim starts a model whose start-up sync finds a CS3 at a `support.StandInStation` and connects over TCP to a
 * `support.TcpStation`, the Link S88 (device 2), which answers queries for its own device only.
 *
 * @author Adam
 */
public class testTheSyncAsksTheSensorsTheirState
{
    private static final long WAIT_MS = 5000;

    private interface Claim
    {
        void run(StationRig rig, TcpStation station) throws Exception;
    }

    private static final BiFunction<String, Integer, byte[]> CS3 =
        StandInStation.cs3("260", "{\"softwareVersion\":\"2.6.1 (Build 3)\"}");

    /** The same CS3, serving Adam's station's device list (`test/cs3_v261/app/api/devs.json`) at /app/api/devs. */
    private static final BiFunction<String, Integer, byte[]> CS3_LISTING = (path, n) -> "/app/api/devs".equals(path)
        ? StandInStation.file("test/cs3_v261/app/api/devs.json") : CS3.apply(path, n);

    /** Runs a claim against a model connected over TCP to a CS3. */
    private static void connected(Claim claim) throws Exception
    {
        connected(Files.createTempDirectory("tc-sync-s88").toFile(), CS3, claim);
    }

    /** The same, the model's data in `root`, the station's web server answering as `answer` does. */
    private static void connected(File root, BiFunction<String, Integer, byte[]> answer, Claim claim) throws Exception
    {
        try (TcpStation station = new TcpStation())
        {
            TcpStation.forSimulations(station.port(), () ->
            {
                try (StationRig rig = new StationRig(root, answer, false, true))
                {
                    assertTrue(rig.model.isCS3(), "precondition: the stand-in was not taken for a CS3");
                    assertTrue(Components.waitFor(() -> station.connections() == 1, WAIT_MS),
                        "precondition: the model is not connected over TCP");
                    claim.run(rig, station);
                }

                return null;
            });
        }
    }

    /** Waits until the model shows the contact in the state. */
    private static boolean shows(StationRig rig, int contact, boolean occupied)
    {
        String name = Integer.toString(contact);

        return Components.waitFor(() -> rig.model.isFeedbackSet(name) && rig.model.getFeedbackState(name) == occupied,
            WAIT_MS);
    }

    /** Syncs, and returns the frames the station received meanwhile. */
    private static List<byte[]> synced(StationRig rig, TcpStation station)
    {
        int before = station.received().size();
        assertTrue(rig.model.syncWithCS2() >= 0, "the sync failed: " + rig.model.getLastSyncError());

        List<byte[]> during = new ArrayList<>();
        List<TcpStation.Frame> all = station.received();

        for (TcpStation.Frame f : all.subList(before, all.size())) during.add(f.bytes);

        return during;
    }

    /** The queries among frames, as device/contact, sorted. */
    private static TreeSet<String> queries(List<byte[]> frames)
    {
        TreeSet<String> out = new TreeSet<>();

        for (byte[] f : frames)
        {
            if ((f[1] & 0xff) == CS2Message.CMD_ACC_SENSOR << 1 && f[4] == 4)
            {
                out.add(((f[5] & 0xff) << 8 | (f[6] & 0xff)) + "/" + ((f[7] & 0xff) << 8 | (f[8] & 0xff)));
            }
        }

        return out;
    }

    /** Every query the station received, as device/contact, sorted. */
    private static TreeSet<String> asked(TcpStation station)
    {
        List<byte[]> all = new ArrayList<>();

        for (TcpStation.Frame f : station.received()) all.add(f.bytes);

        return queries(all);
    }

    private static boolean logged(StationRig rig, int asked, int answered, int skipped)
    {
        return rig.takeLog().contains(I18n.f("network.sensorsSynced", asked, answered, skipped));
    }

    /**
     * The sync sends one query per sensor whose device an event showed, at that device (2 and 3 here), and no other
     * frame: not for a sensor known with no event, nor for one only a frame of no change named (the CS3's own false
     * "occupied" for device 1), so no device that never sent a change is asked.  Each query is 0x11 with four data
     * bytes and no response bit.
     *
     * MUTATION: in `MarklinControlStation.syncWithCS2`, take out `this.NetworkInterface.syncSensors();`.  And: in
     * `NetworkProxy.deviceOf`, `this.sensorDevice.get(contact)` to `this.sensorDevice.getOrDefault(contact, 2)`.
     */
    @Test(timeOut = 120000)
    public void testTheSyncAsksEachSensorAtTheDeviceItsEventsCameFrom() throws Exception
    {
        connected((rig, station) ->
        {
            station.sensor(2101, true);
            station.send(TcpStation.event(3, 2102, true));
            station.send(TcpStation.answer(1, 2103, true));
            rig.model.newFeedback(2104, null);
            assertTrue(shows(rig, 2103, true), "precondition: the station's frames did not arrive");
            int known = rig.model.knownSensors().size();
            rig.takeLog();

            List<byte[]> during = synced(rig, station);

            for (byte[] f : during)
            {
                assertEquals(f[1] & 0xff, CS2Message.CMD_ACC_SENSOR << 1, "the sync sent a frame other than a query: "
                    + Arrays.toString(f));
                assertEquals(f[4], 4, "a query without four data bytes: " + Arrays.toString(f));
            }

            assertEquals(during.size(), 2, "not one query per sensor with a known device");
            assertEquals(queries(during).toString(), "[2/2101, 3/2102]", "the queries are not each sensor at its device");
            assertTrue(logged(rig, 2, 1, known - 2), "the sync did not log 2 asked, 1 answered, " + (known - 2)
                + " not asked");
        });
    }

    /**
     * A sensor TrainControl shows wrong is right after the sync: one shown occupied that the station has clear, and
     * one the other way round, both changed while no event reached TrainControl.
     *
     * MUTATION: in `MarklinControlStation.syncWithCS2`, take out `this.NetworkInterface.syncSensors();`.
     */
    @Test(timeOut = 120000)
    public void testASensorShownWrongIsRightAfterTheSync() throws Exception
    {
        connected((rig, station) ->
        {
            station.sensor(2201, true);
            station.sensor(2202, true);
            station.sensor(2202, false);
            assertTrue(shows(rig, 2201, true) && shows(rig, 2202, false), "precondition: the events did not arrive");

            station.sensorUnseen(2201, false);
            station.sensorUnseen(2202, true);
            synced(rig, station);

            assertTrue(shows(rig, 2201, false), "a sensor shown occupied, clear on the station, was not put right");
            assertTrue(shows(rig, 2202, true), "a sensor shown clear, occupied on the station, was not put right");
        });
    }

    /**
     * A real event after the query wins over an answer that arrives after it: the station clears the contact once
     * asked, then answers "occupied", the state from before.  The contact stays clear; a later event of another
     * contact, carried after the answer, shows the answer was read.
     *
     * MUTATION: in `NetworkProxy.learn`, take out `this.overtaken.replace(contact, System.nanoTime());`.
     */
    @Test(timeOut = 120000)
    public void testAnEventAfterTheQueryBeatsTheAnswer() throws Exception
    {
        connected((rig, station) ->
        {
            station.sensor(2301, true);
            assertTrue(shows(rig, 2301, true), "precondition: the event did not arrive");

            station.changeBeforeAnswering(2301);
            synced(rig, station);

            assertEquals(station.queries().size(), 1, "the contact was not asked");
            station.sensor(2399, true);
            assertTrue(shows(rig, 2399, true), "precondition: the later event did not arrive");
            assertTrue(shows(rig, 2301, false), "the answer from before the event won over the event");
        });
    }

    /**
     * A sensor the sync puts right is no edge: an armed route watching for it to clear, then be made, does not fire on
     * the answer that says it is made, and waits from there; the next real clear, then made, fires it once.
     *
     * MUTATION: in `MarklinRoute.executeAutoRoute`, take out the `continue` after `isFeedbackCorrected`.
     */
    @Test(timeOut = 120000)
    public void testASensorTheSyncPutsRightFiresNoRoute() throws Exception
    {
        connected((rig, station) -> aCorrectionFiresNoRoute(rig, station, 2801, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            () -> synced(rig, station)));
    }

    /**
     * The same after a reconnect: a route watching for its sensor to be made, then clear, does not fire on the new
     * connection's answer that says it is clear; the next real made, then clear, fires it once.
     *
     * MUTATION: as testASensorTheSyncPutsRightFiresNoRoute.
     */
    @Test(timeOut = 120000)
    public void testASensorAReconnectPutsRightFiresNoRoute() throws Exception
    {
        connected((rig, station) -> aCorrectionFiresNoRoute(rig, station, 2802, MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
            station::drop));
    }

    private interface Correction
    {
        void run() throws Exception;
    }

    /**
     * A route of one turnout command armed on the contact; the station then changes the contact unseen to the state the
     * route waits for, and `correct` puts TrainControl right.  The route must not fire, then must fire on real events.
     */
    private static void aCorrectionFiresNoRoute(StationRig rig, TcpStation station, int contact,
        MarklinRoute.s88Triggers trigger, Correction correct) throws Exception
    {
        boolean first = trigger == MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR;
        String name = "Sync claim " + contact;
        List<String> log = new ArrayList<>();
        station.sensor(contact, first);
        assertTrue(shows(rig, contact, first), "precondition: the event did not arrive");
        int turnout = 10 + contact % 100;
        rig.model.newSwitch(turnout, Accessory.accessoryDecoderType.MM2, false);
        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandAccessory(turnout, Accessory.accessoryDecoderType.MM2, true));
        assertTrue(rig.model.newRoute(name, commands, contact, trigger, true, null), "precondition: no route");
        MarklinRoute route = rig.model.getRoute(name);
        assertTrue(Components.waitFor(() -> RouteMonitor.waitingFor(route, !first), WAIT_MS),
            "precondition: the route is not waiting for its trigger");

        station.sensorUnseen(contact, !first);
        correct.run();
        assertTrue(shows(rig, contact, !first), "precondition: the sensor was not put right");

        // Settled: the monitor waits again for the state it began from, whether or not it fired
        boolean settled = Components.waitFor(() -> RouteMonitor.waitingFor(route, first), WAIT_MS);
        assertEquals(fired(rig, log, name), 0, "the route fired on a state the station was asked for: " + log);
        assertTrue(settled, "the route's monitor did not go back to waiting: " + log);

        station.sensor(contact, first);
        assertTrue(Components.waitFor(() -> RouteMonitor.waitingFor(route, !first), WAIT_MS),
            "the route does not wait from the state the sync set: " + log);
        station.sensor(contact, !first);
        assertTrue(Components.waitFor(() -> fired(rig, log, name) == 1, WAIT_MS), "real events did not fire the route: "
            + log);
    }

    /** How many times the route has fired by the log, `log` keeping what was taken before. */
    private static int fired(StationRig rig, List<String> log, String route)
    {
        log.addAll(rig.takeLog());

        return java.util.Collections.frequency(log, I18n.f("route.s88Triggered", route));
    }

    /**
     * A sync while transmission is off sends no query, as `exec` sends nothing then; once on, the next sync asks.
     *
     * MUTATION: in `MarklinControlStation.syncWithCS2`, take out `if (this.on)` before `syncSensors()`.
     */
    @Test(timeOut = 120000)
    public void testASyncWhileTransmissionIsOffAsksNothing() throws Exception
    {
        connected((rig, station) ->
        {
            station.sensor(2701, true);
            assertTrue(shows(rig, 2701, true), "precondition: the event did not arrive");
            rig.model.setNetworkCommState(false);

            try
            {
                synced(rig, station);
            }
            finally
            {
                rig.model.setNetworkCommState(true);
            }

            // Answered, so in order over TCP every earlier query has arrived too
            synced(rig, station);
            long asked = station.queries().stream().filter(q -> q[1] == 2701).count();
            assertEquals(asked, 1L, "a sync asked the sensor while transmission was off");
        });
    }

    /**
     * A station that never answers holds the sync up by the resync's bound (1 s after the last query), not for ever,
     * and the log says how many were asked and that none answered.  The Link S88 here has no device 3.
     *
     * MUTATION: in `NetworkProxy.askOnce`, `System.nanoTime() + RESYNC_WAIT_NS` to `System.nanoTime() + 60 *
     * RESYNC_WAIT_NS`.
     */
    @Test(timeOut = 120000)
    public void testAStationThatNeverAnswersDoesNotHoldTheSyncUp() throws Exception
    {
        connected((rig, station) ->
        {
            station.send(TcpStation.event(3, 2401, true));
            assertTrue(shows(rig, 2401, true), "precondition: the event did not arrive");
            int known = rig.model.knownSensors().size();
            rig.takeLog();

            long began = System.nanoTime();
            synced(rig, station);
            long tookMs = (System.nanoTime() - began) / 1000000L;

            assertTrue(logged(rig, 1, 0, known - 1), "the sync did not log 1 asked, 0 answered");
            assertTrue(tookMs < 10000, "the sync took " + tookMs + " ms with a sensor unanswered");
            assertFalse(station.queries().isEmpty(), "the contact was not asked");
        });
    }

    /**
     * A device learned from an event is saved with its sensor, and after a restart the sensor is asked at it before any
     * event of its own (OB-418).  The sensor is the stand-in's diagram's, as a CS3 sync takes out sensors on no page.
     *
     * MUTATION: in `MarklinControlStation.saveState`, `s88DeviceToSave(f.getUID())` to `null`.  And: in
     * `MarklinControlStation.syncWithCS2`, pass `new java.util.HashMap<>()` for `this.savedS88Devices` to `knowDevices`.
     */
    @Test(timeOut = 180000)
    public void testADeviceLearnedBeforeARestartIsAskedAfterIt() throws Exception
    {
        File root = Files.createTempDirectory("tc-sync-s88-restart").toFile();
        int contact = savedAtDevice3(root);

        connected(root, CS3, (rig, station) ->
        {
            List<byte[]> during = synced(rig, station);
            assertTrue(queries(during).contains("3/" + contact), "the saved device was not asked: " + queries(during));
        });
    }

    /**
     * A first run in `root` that hears the diagram's one sensor (a CS3 sync takes out sensors on no page), 1002 and 50
     * from device 3 and saves; returns the diagram's sensor.
     */
    private static int savedAtDevice3(File root) throws Exception
    {
        int[] contact = new int[1];

        connected(root, CS3, (rig, station) ->
        {
            assertEquals(rig.model.knownSensors().size(), 1, "precondition: the diagram has not one sensor");
            contact[0] = rig.model.knownSensors().get(0);
            station.send(TcpStation.event(3, contact[0], true));
            station.send(TcpStation.event(3, 1002, true));
            station.send(TcpStation.event(3, 50, true));
            assertTrue(shows(rig, contact[0], true) && shows(rig, 1002, true) && shows(rig, 50, true),
                "precondition: the events did not arrive");
            rig.model.saveState(false);
        });

        return contact[0];
    }

    /**
     * The start-up sync asks the sensors once the proxy can hear the answers: a device saved before a restart is asked
     * as the model starts, before any sync of the operator's (OB-418).
     *
     * MUTATION: in `MarklinControlStation.init`, take out the `proxy.syncSensors();` after `proxy.setModel(model);`.
     */
    @Test(timeOut = 180000)
    public void testTheStartUpAsksTheSensors() throws Exception
    {
        File root = Files.createTempDirectory("tc-sync-s88-start").toFile();
        int contact = savedAtDevice3(root);

        connected(root, CS3, (rig, station) -> assertTrue(Components.waitFor(() -> asked(station).contains("3/" + contact),
            WAIT_MS), "the start-up did not ask the saved device: " + asked(station)));
    }

    /**
     * Adam's station's device list places his sensors at the Link S88 (device 2): 1-16, 1001-1032, 2001-2016 and
     * 3001-3016.  1-16 goes to the Link S88 although the station's own bus (device 1, five modules configured, nothing
     * on it) covers 1-80 too: the own bus never places a sensor, as Adam ruled ("L88 is much safer").  A sync asks a
     * sensor no event placed at the device the list gives (OB-418).
     *
     * MUTATION: in `CS2File.contactsOf`, `"64".equals(device.optString("typ"))` to `true`.  And: in
     * `MarklinControlStation.syncWithCS2`, `this.fileParser.parseS88DevicesCS3()` to `new java.util.HashMap<>()`.
     */
    @Test(timeOut = 120000)
    public void testAdamsDeviceListPlacesHisSensors() throws Exception
    {
        connected(Files.createTempDirectory("tc-sync-s88-list").toFile(), CS3_LISTING, (rig, station) ->
        {
            Map<Integer, Integer> want = new TreeMap<>();

            for (int c = 1; c <= 16; c++) want.put(c, 2);
            for (int c = 1001; c <= 1032; c++) want.put(c, 2);
            for (int c = 2001; c <= 2016; c++) want.put(c, 2);
            for (int c = 3001; c <= 3016; c++) want.put(c, 2);

            assertEquals(new TreeMap<>(new CS2File(rig.station.address(), rig.model).parseS88DevicesCS3()), want,
                "the list does not place the sensors as Adam's station has them");

            for (int c : new int[] {5, 17, 1032}) rig.model.newFeedback(c, null);

            TreeSet<String> asked = queries(synced(rig, station));
            assertTrue(asked.contains("2/1032") && asked.contains("2/5"), "the list's devices were not asked: " + asked);
            assertTrue(asked.stream().noneMatch(q -> q.endsWith("/17")), "the own bus placed a sensor: " + asked);
        });
    }

    /**
     * Only a device a real event showed is saved: a sensor the Link S88 list placed, with no event, is saved with none,
     * as the list is read again at each sync.
     *
     * MUTATION: in `MarklinControlStation.s88DeviceToSave`, `learnedDeviceOf(contact)` to `deviceOf(contact)`.
     */
    @Test(timeOut = 120000)
    public void testADeviceOnlyTheListGaveIsNotSaved() throws Exception
    {
        connected(Files.createTempDirectory("tc-sync-s88-nosave").toFile(), CS3_LISTING, (rig, station) ->
        {
            int contact = rig.model.knownSensors().get(0);
            synced(rig, station);
            assertTrue(asked(station).contains("2/" + contact), "precondition: the list did not place the sensor");
            rig.model.saveState(false);
            int found = 0;

            for (MarklinSimpleComponent c : rig.model.restoreState(
                org.traincontrol.util.Util.dataPath(org.traincontrol.marklin.MarklinControlStation.DATA_FILE_NAME)))
            {
                if (c.getType() == MarklinSimpleComponent.Type.FEEDBACK && c.getAddress() == contact)
                {
                    found++;
                    assertEquals(c.getS88Device(), null, "a device only the list gave was saved");
                }
            }

            assertEquals(found, 1, "precondition: the sensor was not saved");
        });
    }

    /**
     * A contact two Link S88s have is placed by neither, the station's own bus places nothing, and an entry with no
     * device number (0xffff) is skipped: here 1-16 is in both Link S88s, 1001-1016 only in device 3's, and 2001-2016
     * only in the 0xffff entry's.
     *
     * MUTATION: in `CS2File.parseS88DevicesCS3`, take out `owner.keySet().removeAll(shared);`.  And: there, take out
     * `|| device == 0xffff`.
     */
    @Test(timeOut = 120000)
    public void testTwoLinkS88sShareNothingAndTheOwnBusPlacesNothing() throws Exception
    {
        String list = "[{\"kennung\":\"0x2\",\"typ\":\"64\",\"kanal\":[{\"name\":\"Auswertung 1 - 16\"}]},"
            + "{\"kennung\":\"0x3\",\"typ\":\"64\",\"kanal\":[{\"name\":\"Auswertung 1 - 16\",\"wert\":\"0\"},"
            + "{\"name\":\"L\u00e4nge Bus 1 (RJ45-1)\",\"wert\":\"1\"}]},"
            + "{\"kennung\":\"0x1\",\"typ\":\"80\",\"kanal\":[{\"name\":\"L\u00e4nge S88Bus\",\"wert\":\"5\"}]},"
            + "{\"kennung\":\"0xffff\",\"typ\":\"64\","
            + "\"kanal\":[{\"name\":\"L\u00e4nge Bus 2 (RJ45-2)\",\"wert\":\"1\"}]}]";

        connected(Files.createTempDirectory("tc-sync-s88-two").toFile(), (path, n) -> "/app/api/devs".equals(path)
            ? list.getBytes(java.nio.charset.StandardCharsets.UTF_8) : CS3.apply(path, n), (rig, station) ->
        {
            Map<Integer, Integer> want = new TreeMap<>();

            for (int c = 1001; c <= 1016; c++) want.put(c, 3);

            assertEquals(new TreeMap<>(new CS2File(rig.station.address(), rig.model).parseS88DevicesCS3()), want,
                "the list placed a shared contact, the own bus's, or an entry with no device number");
        });
    }

    /**
     * Where they disagree a live event beats the station's list, and the list beats the saved device; the saved device
     * places what the list leaves out.  Saved at device 3: the diagram's sensor (in the Link S88's 1-16), 1002 and 50
     * (on no Link S88); after the restart an event for 1003 comes from device 3.
     *
     * MUTATION: in `NetworkProxy.deviceOf`, look in `savedDevice` before `listedDevice`.  And: look in `listedDevice`
     * before `sensorDevice`.
     */
    @Test(timeOut = 180000)
    public void testAnEventBeatsTheListAndTheListBeatsTheSavedDevice() throws Exception
    {
        File root = Files.createTempDirectory("tc-sync-s88-order").toFile();
        int contact = savedAtDevice3(root);
        assertTrue(contact >= 1 && contact <= 16, "precondition: the diagram's sensor is not in the Link S88's 1-16");

        connected(root, CS3_LISTING, (rig, station) ->
        {
            rig.model.newFeedback(1002, null);
            rig.model.newFeedback(50, null);
            station.send(TcpStation.event(3, 1003, true));
            assertTrue(shows(rig, 1003, true), "precondition: the event did not arrive");

            TreeSet<String> asked = queries(synced(rig, station));
            assertTrue(asked.contains("3/50"), "the saved device did not place what the list leaves: " + asked);
            assertTrue(asked.contains("2/" + contact) && !asked.contains("3/" + contact)
                && asked.contains("2/1002") && !asked.contains("3/1002"), "the saved device beat the list: " + asked);
            assertTrue(asked.contains("3/1003") && !asked.contains("2/1003"), "the list beat a live event: " + asked);
        });
    }

    /**
     * A station whose device list is missing (not found) or will not read is synced as before, and nothing is said of
     * it: devices come from events and the saved file.
     *
     * MUTATION: in `CS2File.parseS88DevicesCS3`, `return new HashMap<>();` to
     * `throw new IllegalStateException(unreadable);`.
     */
    @Test(timeOut = 180000)
    public void testAMissingOrUnreadableListIsQuiet() throws Exception
    {
        List<BiFunction<String, Integer, byte[]>> stations = Arrays.asList(CS3, (path, n) -> "/app/api/devs".equals(path)
            ? "<html>busy</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8) : CS3.apply(path, n));

        for (BiFunction<String, Integer, byte[]> answer : stations)
        {
            connected(Files.createTempDirectory("tc-sync-s88-nolist").toFile(), answer, (rig, station) ->
            {
                assertTrue(new CS2File(rig.station.address(), rig.model).parseS88DevicesCS3().isEmpty(),
                    "an unreadable list placed sensors");

                station.sensor(2601, true);
                assertTrue(shows(rig, 2601, true), "precondition: the event did not arrive");
                rig.takeLog();

                List<byte[]> during = synced(rig, station);
                assertTrue(queries(during).contains("2/2601"), "the sensor was not asked: " + queries(during));

                for (String line : rig.takeLog())
                {
                    assertFalse(line.contains("devs"), "the log spoke of the device list: " + line);
                }
            });
        }
    }

    /**
     * A file an older build saved, its sensors with no device, opens: its 58 sensors read back with none.
     * And the saved file keeps 3.0.8's shape: the device rides in a field 3.0.8 already had (a route's trigger), so a
     * 3.0.8 build reads a file this one saved as before, and takes only a sensor's address and state from it.
     *
     * MUTATION: in `MarklinSimpleComponent.getS88Device`, take out `&& this.s88 > 0`.  And: add a field to
     * `MarklinSimpleComponent`.
     */
    @Test(timeOut = 120000)
    public void testAnOlderFileOpensAndTheFileKeeps308sShape() throws Exception
    {
        connected((rig, station) ->
        {
            int sensors = 0;

            for (MarklinSimpleComponent c : rig.model.restoreState("test/LocDB3_0_0.data"))
            {
                if (c.getType() != MarklinSimpleComponent.Type.FEEDBACK) continue;

                sensors++;
                assertEquals(c.getS88Device(), null, "an older file's sensor " + c.getAddress() + " read with a device");
            }

            assertEquals(sensors, 58, "the older file's sensors did not read back");
        });

        java.io.ObjectStreamClass shape = java.io.ObjectStreamClass.lookup(MarklinSimpleComponent.class);
        StringBuilder fields = new StringBuilder(String.valueOf(shape.getSerialVersionUID()));

        for (java.io.ObjectStreamField f : shape.getFields()) fields.append(' ').append(f.getName()).append(f.getTypeCode());

        assertEquals(fields.toString(), SHAPE_308, "the saved file's shape is no longer 3.0.8's");
    }

    /** MarklinSimpleComponent's serialized shape in 3.0.8: its serialVersionUID, then each field's name and type code. */
    private static final String SHAPE_308 =
        "-9111893030704758839 addressI customFunctionsZ numActuationsI preferredSpeedI reversibleZ "
        + "routeEnabledZ s88I stateZ accessoryDecoderTypeL arrivalFunctionL centralStationLinkedLocomotivesL "
        + "conditionsL departureFunctionL functionTriggerTypes[ functionTypes[ functions[ "
        + "historicalOperatingTimeNewL linkedLocomotivesL localFunctionImageURLsL localImageURLL nameL notesL "
        + "preferredFunctions[ routeL s88TriggerTypeL trainLengthL typeL";
}
