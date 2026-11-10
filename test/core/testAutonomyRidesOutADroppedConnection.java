package core;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.util.I18n;
import support.FastSimulation;
import support.Packets;
import support.Reflect;
import support.StandInStation;
import support.StationRig;
import support.TcpStation;
import static support.Settle.until;

/**
 * A CS3's TCP connection dropped in the middle of an autonomy run, end to end (FR-186): the model syncs with a stand-in
 * CS3 and connects to its TCP port, autonomy runs test/autonomy_sanity.json with its sensors simulated, and the station
 * drops the connection and refuses the first two reconnect tries, about 10 s on Windows.  During the gap the program
 * gives a train a speed, and a handset drives another train and throws a switch, broadcast over UDP as a CS3 does.
 * A simulation's station is always 127.0.0.1, so once the model has connected its UDP and its reconnects are aimed at
 * a loopback address of this class's own, where the test reads the UDP as the station would.
 *
 * @author Adam
 */
public class testAutonomyRidesOutADroppedConnection
{
    private static final String STATION = "127." + (16 + java.util.concurrent.ThreadLocalRandom.current().nextInt(239))
        + "." + java.util.concurrent.ThreadLocalRandom.current().nextInt(256) + "."
        + (1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(254));

    private static final String CS3_INFO = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    /** Reconnect tries refused before the station takes connections again: 2 makes the gap about 10 s on Windows. */
    private static final int TRIES_REFUSED = 2;

    /**
     * A route that passed the gate and was set going just before the loss was found logs its start just after it;
     * one whose route was still being set is given back.
     */
    private static final long LOCK_GRACE_MS = 1500;

    /** Contacts of the station's Link S88 outside the railway: one shown occupied, one clear, both changed unseen. */
    private static final int SEEN = 901;
    private static final int UNSEEN = 902;

    /** Something logged or sent, and when (System.currentTimeMillis). */
    private static final class Stamped<T>
    {
        final long at;
        final T what;

        Stamped(long at, T what)
        {
            this.at = at;
            this.what = what;
        }
    }

    private final List<Stamped<String>> log = Collections.synchronizedList(new ArrayList<>());

    // What the model sent over UDP to the station's address
    private final List<Stamped<byte[]>> udp = Collections.synchronizedList(new ArrayList<>());

    private final Handler tap = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            if (record != null && record.getMessage() != null)
            {
                log.add(new Stamped<>(record.getMillis(), record.getMessage()));
            }
        }

        @Override
        public void flush()
        {
        }

        @Override
        public void close()
        {
        }
    };

    /**
     * The drop, ridden out: the log says lost, then reconnected; no new route starts in the gap and routes resume
     * after; a speed given in the gap goes over UDP at once and no command given before or during the gap reaches the
     * station again; the handset's train and switch are left as the handset left them; the log counts the commands
     * that may not have arrived; the sensors changed unseen in the gap match the station once reconnected.
     *
     * MUTATION: take the `isReplacingConnection()` gate out of `waitWhileThePowerIsOff` and `executePathInternal`.
     */
    @Test(timeOut = 240000)
    public void testAutonomyRidesOutADroppedConnection() throws Exception
    {
        ride(true);
    }

    /**
     * The same drop, and the station never takes a connection again: UDP takes over for the session, and routes resume
     * over it.
     *
     * MUTATION: as above.
     */
    @Test(timeOut = 240000)
    public void testRoutesResumeOnUdpWhenReconnectingFails() throws Exception
    {
        ride(false);
    }

    /**
     * A run with a CS2, which is never connected to over TCP, is never held.
     *
     * MUTATION: make `NetworkProxy.isReplacing` true whenever TCP is not the transport.
     */
    @Test(timeOut = 240000)
    public void testACS2RunIsNotHeld() throws Exception
    {
        File served = testACS2RequestsAreAsBefore.railway(
            new File(Files.createTempDirectory("tc-drop-cs2").toFile(), "s"));

        try (TcpStation station = new TcpStation())
        {
            TcpStation.forSimulations(station.port(), () -> onRig((path, n) -> path.equals("/config/geraet.vrs")
                ? testACS2RequestsAreAsBefore.CS2_DEVICE : StandInStation.file(served.getPath() + path), true, rig ->
            {
                assertTrue(rig.model.getNetworkCommState() && !rig.model.isCS3(), "precondition: no CS2 was found");
                runsUnheld(rig.model);
            }));

            assertEquals(station.connections(), 0, "a CS2 was connected to over TCP");
        }
    }

    /**
     * A simulation with no station is never held.
     *
     * MUTATION: as for the CS2.
     */
    @Test(timeOut = 240000)
    public void testASimulationIsNotHeld() throws Exception
    {
        onRig((path, n) -> null, false, rig ->
        {
            assertFalse(rig.model.getNetworkCommState(), "precondition: a station was found");
            runsUnheld(rig.model);
        });
    }

    private interface Check
    {
        void on(StationRig rig) throws Exception;
    }

    /** Runs a check on a rig whose model sends UDP to STATION, with the log tapped and the UDP read. */
    private Object onRig(BiFunction<String, Integer, byte[]> answer, boolean atStartUp, Check check) throws Exception
    {
        this.log.clear();
        this.udp.clear();
        Logger logger = Logger.getLogger(MarklinControlStation.class.getName());
        logger.addHandler(this.tap);

        try (DatagramSocket wire = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(STATION),
            NetworkProxy.TX_PORT)))
        {
            Thread reading = new Thread(() -> read(wire), "drop-udp-wire");
            reading.setDaemon(true);
            reading.start();

            File root = Files.createTempDirectory("tc-drop").toFile();

            try (StationRig rig = new StationRig(root, answer, false, atStartUp))
            {
                Object proxy = Reflect.get(MarklinControlStation.class, rig.model, "NetworkInterface");
                Reflect.set(NetworkProxy.class, proxy, "transmitIP", InetAddress.getByName(STATION));
                check.on(rig);
            }
        }
        finally
        {
            logger.removeHandler(this.tap);
        }

        return null;
    }

    private void read(DatagramSocket wire)
    {
        DatagramPacket packet = new DatagramPacket(new byte[64], 64);

        try
        {
            while (true)
            {
                wire.receive(packet);
                byte[] frame = Arrays.copyOf(packet.getData(), packet.getLength());
                this.udp.add(new Stamped<>(System.currentTimeMillis(), frame));
            }
        }
        catch (java.io.IOException closed)
        {
            // The wire is closed with its rig
        }
    }

    /** Runs the railway on a model and asserts its routes start and nothing is held. */
    private void runsUnheld(MarklinControlStation model) throws Exception
    {
        Layout layout = railway(model);

        try (FastSimulation fast = FastSimulation.unpace(layout))
        {
            layout.runLocomotives();

            assertTrue(until(() -> starts(0, Long.MAX_VALUE) >= 3, 30000), "routes did not start: " + texts());
            assertFalse(said("autolayout.infoRoutesHeld"), "new routes were held with no TCP connection lost: "
                + texts());
        }
        finally
        {
            stop(layout);
        }
    }

    /** The drop, with or without the station taking a connection again. */
    private void ride(boolean reconnects) throws Exception
    {
        try (TcpStation station = new TcpStation().answering(true))
        {
            TcpStation.forSimulations(station.port(), () -> onRig(StandInStation.cs3("260", CS3_INFO), true, rig ->
                ride(rig.model, station, reconnects)));
        }
    }

    private void ride(MarklinControlStation model, TcpStation station, boolean reconnects) throws Exception
    {
        assertTrue(until(() -> station.connections() == 1, 5000), "precondition: not connected over TCP: " + texts());

        NetworkProxy proxy = (NetworkProxy) Reflect.get(MarklinControlStation.class, model, "NetworkInterface");

        // The station's own contacts, each shown by an event, so the model learns their device
        station.sensor(SEEN, true);
        station.sensor(UNSEEN, false);
        assertTrue(until(() -> matchesTheStation(model, station), 5000), "precondition: the station's contacts unseen");

        List<MarklinLocomotive> others = new ArrayList<>();

        for (Locomotive l : model.getLocomotives())
        {
            if (!l.getName().startsWith("Auto Test")) others.add((MarklinLocomotive) l);
        }

        MarklinLocomotive given = others.get(0);
        MarklinLocomotive driven = others.get(1);
        MarklinAccessory thrown = model.newSwitch(300, Accessory.accessoryDecoderType.MM2, false);
        Layout layout = railway(model);

        try (FastSimulation fast = FastSimulation.unpace(layout))
        {
            layout.runLocomotives();
            assertTrue(until(() -> starts(0, Long.MAX_VALUE) >= 2, 30000), "precondition: no route started: "
                + texts());

            // A speed the station answers, then one it has not answered when the connection drops
            given.setSpeed(20);
            assertTrue(until(() -> aimedAt(station.receivedOn(1), given.getIntUID()) > 0, 5000),
                "precondition: unsent");
            station.answering(false);
            int answered = aimedAt(station.receivedOn(1), given.getIntUID());
            given.setSpeed(25);
            assertTrue(until(() -> aimedAt(station.receivedOn(1), given.getIntUID()) > answered, 5000),
                "precondition: unsent");

            station.drop();
            station.refuse();

            Stamped<String> lost = awaitLine("network.tcpLostReconnecting", 5000);
            java.util.concurrent.atomic.AtomicInteger triesFailed =
                (java.util.concurrent.atomic.AtomicInteger) Reflect.get(NetworkProxy.class, proxy, "triesFailed");

            assertTrue(said("network.framesMayNotHaveArrived"), "the loss did not count the commands that may not have"
                + " arrived: " + texts());

            // The handset's, broadcast by the station while the connection is replaced
            broadcast(proxy, Packets.speedEcho(driven, 600).getRawMessage());
            broadcast(proxy, Packets.accessoryEcho(thrown, true).getRawMessage());
            assertTrue(until(() -> driven.getSpeed() > 0 && thrown.isSwitched(), 900), "the handset's broadcasts did not"
                + " reach the model: " + texts());
            int drivenSpeed = driven.getSpeed();

            // The program's command in the gap: over UDP at once
            long givenAt = System.currentTimeMillis();
            given.setSpeed(40);
            assertTrue(until(() -> aimedAt(udpSince(givenAt), given.getIntUID()) > 0, 900), "a speed given while the"
                + " connection was replaced did not go over UDP at once: " + texts());
            int givenInTheGap = aimedAt(udpSince(givenAt), given.getIntUID());

            // Two contacts change with no event seen
            station.sensorUnseen(SEEN, false);
            station.sensorUnseen(UNSEEN, true);

            Stamped<String> back;

            if (reconnects)
            {
                assertTrue(until(() -> triesFailed.get() >= TRIES_REFUSED, 30000), "the reconnect tries never"
                    + " failed: " + texts());
                station.answering(true);
                station.reopen(STATION);
                back = awaitLine("network.tcpReconnected", 15000);
            }
            else
            {
                back = awaitLine("network.tcpReconnectFailed", 30000);
            }

            assertEquals(starts(lost.at + LOCK_GRACE_MS, back.at), 0, "a new route started while the connection was"
                + " being replaced (FR-186): " + texts());
            assertTrue(lineAfter("autolayout.infoRoutesHeld", lost.at) != null, "new routes were not said to be held: "
                + texts());
            assertTrue(until(() -> starts(back.at, Long.MAX_VALUE) >= 2, 30000), "routes did not resume once a transport"
                + " was live: " + texts());
            assertTrue(lineAfter("autolayout.infoRoutesResumed", back.at) != null, "the resumption was not said: "
                + texts());

            if (reconnects)
            {
                assertTrue(until(() -> matchesTheStation(model, station), 10000), "the sensors changed in the gap do not"
                    + " match the station after the reconnect: " + texts());
                assertEquals(aimedAt(station.receivedOn(2), given.getIntUID()), 0, "a command given before or during the"
                    + " gap was sent again on the new connection");
            }
            else
            {
                assertEquals(station.connections(), 1, "precondition: the station took a connection");
                assertTrue(until(() -> aimedAtTheRailway(model, udpSince(back.at)) > 0, 5000), "the resumed routes'"
                    + " commands did not go over UDP: " + texts());
            }

            assertEquals(aimedAt(udpSince(givenAt), given.getIntUID()), givenInTheGap, "a command given before or"
                + " during the gap was sent again over UDP");

            for (TcpStation.Frame f : station.received())
            {
                assertFalse(aims(f.bytes, driven.getIntUID()) || aims(f.bytes, thrown.getUID()), "the program commanded"
                    + " the handset's train or switch over TCP");
            }

            assertEquals(aimedAt(udpSince(0), driven.getIntUID()) + aimedAt(udpSince(0), thrown.getUID()), 0, "the"
                + " program commanded the handset's train or switch over UDP");
            assertTrue(driven.getSpeed() == drivenSpeed && thrown.isSwitched(), "the handset's train or switch is not as"
                + " the handset left it");
        }
        finally
        {
            stop(layout);
        }
    }

    /** The sanity railway on a model, its sensors clear and simulated, ready to run. */
    private static Layout railway(MarklinControlStation model) throws Exception
    {
        for (int i = 1; i <= 3; i++)
        {
            if (model.getLocByName("Auto Test Loc " + i) == null) model.newMM2Locomotive("Auto Test Loc " + i, 60 + i);
        }

        for (int a = 1; a <= 7; a++)
        {
            if (a <= 4) model.newSwitch(a, Accessory.accessoryDecoderType.MM2, false);
            else model.newSignal(a, Accessory.accessoryDecoderType.MM2, false);
        }

        model.parseAuto(new String(Files.readAllBytes(Paths.get("test/autonomy_sanity.json")), StandardCharsets.UTF_8));

        Layout layout = model.getAutoLayout();

        assertTrue(layout != null && layout.isValid(), "the railway did not parse: " + Layout.getLastError());
        assertEquals(layout.getLocomotivesToRun().size(), 3, "the railway's trains were not placed");

        // A connected station refuses Simulate; here the station is a stand-in, and the railway's sensors are simulated
        Reflect.set(Layout.class, layout, "simulate", true);

        for (Point p : layout.getPoints()) if (p.getS88() != null) model.setFeedbackState(p.getS88(), false);

        return layout;
    }

    private static void stop(Layout layout)
    {
        layout.stopLocomotives();
        until(() -> !layout.isRunning(), 20000);
    }

    /** Sends a datagram to the proxy's UDP socket, as the station's broadcast arrives. */
    private static void broadcast(NetworkProxy proxy, byte[] frame) throws Exception
    {
        int port = ((DatagramSocket) Reflect.get(NetworkProxy.class, proxy, "socket")).getLocalPort();

        try (DatagramSocket out = new DatagramSocket())
        {
            out.send(new DatagramPacket(frame, frame.length, InetAddress.getByName("127.0.0.1"), port));
        }
    }

    private static boolean matchesTheStation(MarklinControlStation model, TcpStation station)
    {
        for (int contact : new int[] {SEEN, UNSEEN})
        {
            String name = Integer.toString(contact);

            if (!model.isFeedbackSet(name) || model.getFeedbackState(name) != station.occupied(contact)) return false;
        }

        return true;
    }

    /** Whether a frame is a locomotive or accessory command for `uid`. */
    private static boolean aims(byte[] frame, int uid)
    {
        int command = ((frame[0] & 0x01) << 7) | ((frame[1] & 0xFF) >> 1);
        int to = ((frame[5] & 0xFF) << 24) | ((frame[6] & 0xFF) << 16) | ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);

        return to == uid && frame[4] >= 5 && (command == CS2Message.CMD_LOCO_VELOCITY
            || command == CS2Message.CMD_LOCO_DIRECTION || command == CS2Message.CMD_LOCO_FUNCTION
            || command == CS2Message.CMD_ACC_SWITCH);
    }

    private static int aimedAt(List<byte[]> frames, int uid)
    {
        int n = 0;

        for (byte[] f : frames) if (aims(f, uid)) n++;

        return n;
    }

    /** How many frames command the railway's trains. */
    private static int aimedAtTheRailway(MarklinControlStation model, List<byte[]> frames)
    {
        int n = 0;

        for (int i = 1; i <= 3; i++)
        {
            n += aimedAt(frames, ((MarklinLocomotive) model.getLocByName("Auto Test Loc " + i)).getIntUID());
        }

        return n;
    }

    private List<byte[]> udpSince(long at)
    {
        List<byte[]> out = new ArrayList<>();

        synchronized (this.udp)
        {
            for (Stamped<byte[]> s : this.udp) if (s.at >= at) out.add(s.what);
        }

        return out;
    }

    /** How many routes logged their start in [from, to). */
    private int starts(long from, long to)
    {
        int n = 0;

        synchronized (this.log)
        {
            for (Stamped<String> line : this.log)
            {
                boolean starting = says(line.what, "autolayout.infoExecutingPathForLocomotive");

                if (starting && line.at >= from && line.at < to) n++;
            }
        }

        return n;
    }

    private boolean said(String key)
    {
        return lineAfter(key, 0) != null;
    }

    private Stamped<String> lineAfter(String key, long at)
    {
        synchronized (this.log)
        {
            for (Stamped<String> line : this.log) if (line.at >= at && says(line.what, key)) return line;
        }

        return null;
    }

    private Stamped<String> awaitLine(String key, long ms)
    {
        assertTrue(until(() -> said(key), ms), "never logged: " + I18n.t(key) + ": " + texts());

        return lineAfter(key, 0);
    }

    /** Whether a line is the message of `key`, whatever its arguments. */
    private static boolean says(String text, String key)
    {
        String[] parts = I18n.f(key, "\u0001", "\u0001", "\u0001").split("\u0001", -1);
        StringBuilder regex = new StringBuilder("(?s)");

        for (int i = 0; i < parts.length; i++) regex.append(i > 0 ? ".*" : "").append(Pattern.quote(parts[i]));

        return text.matches(regex.toString());
    }

    private String texts()
    {
        List<String> out = new ArrayList<>();

        synchronized (this.log)
        {
            for (Stamped<String> line : this.log) out.add(line.what);
        }

        return out.toString();
    }
}
