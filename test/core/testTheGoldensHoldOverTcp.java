package core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.udp.CS2Message;
import support.Components;
import support.Reflect;
import support.StandInStation;
import support.TcpStation;

/**
 * TCP is for a CS3 the start-up sync found; everything else runs on UDP as before (FR-186).  The CS2 requests, the
 * CS2 sync and the CS3 sync goldens run unchanged with a `support.TcpStation` offered: their start-up sync finds no
 * station, so no model may connect, and each still matches its golden.  A CS2 found at start-up never connects, and
 * its proxy has no TCP listener; a CS3 connects.  And over TCP the station receives exactly the frames the program
 * handed the network, byte for byte and in order: the bytes `testUdpMessagesReachTheWire` shows a datagram carries.
 *
 * @author Adam
 */
public class testTheGoldensHoldOverTcp
{
    private interface Golden
    {
        void run() throws Exception;
    }

    private static final String CS3_INFO = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    @DataProvider(name = "goldens")
    public static Object[][] goldens()
    {
        return new Object[][] {{"CS2 requests"}, {"CS2 sync"}, {"CS3 sync"}};
    }

    private static Golden golden(String name)
    {
        switch (name)
        {
            case "CS2 requests": return () -> new testACS2RequestsAreAsBefore().testTheRequestsOfACS2AreAsBefore();
            case "CS2 sync": return () -> new testACS2SyncIsAsBefore().testASyncWithACS2IsAsBefore();
            default: return () -> new testACS3SyncIsAsBefore().testASyncWithACS3IsAsBefore();
        }
    }

    /**
     * A station whose type the start-up did not learn runs on UDP as before: no TCP connection, the golden unchanged.
     *
     * MUTATION: in `MarklinControlStation.init`, make the `connectOverTcp` condition `true`.
     */
    @Test(dataProvider = "goldens", timeOut = 300000)
    public void testTheGoldenIsAsBefore(String name) throws Exception
    {
        try (TcpStation station = new TcpStation())
        {
            TcpStation.forSimulations(station.port(), () ->
            {
                golden(name).run();
                return null;
            });

            assertEquals(station.connections(), 0, "a station of unknown type was connected to over TCP");
        }
    }

    /** Runs a rig whose start-up sync reaches a stand-in. */
    private static void startedAgainst(java.util.function.BiFunction<String, Integer, byte[]> answer,
        TcpStation station, java.util.function.Consumer<support.StationRig> check) throws Exception
    {
        TcpStation.forSimulations(station.port(), () ->
        {
            try (support.StationRig rig = new support.StationRig(Files.createTempDirectory("tc-tcp-start").toFile(),
                answer, false, true))
            {
                check.accept(rig);
            }

            return null;
        });
    }

    private static Object proxyField(support.StationRig rig, String name) throws Exception
    {
        Object proxy = Reflect.get(org.traincontrol.marklin.MarklinControlStation.class, rig.model, "NetworkInterface");
        return Reflect.get(proxy.getClass(), proxy, name);
    }

    /**
     * A CS2 found at start-up is never connected to over TCP, and its proxy runs only the UDP listener, as before.
     *
     * MUTATION: in `MarklinControlStation.init`, take out `model.isCS3() && `.
     */
    @Test(timeOut = 120000)
    public void testACS2NeverTriesTcp() throws Exception
    {
        File served = testACS2RequestsAreAsBefore.railway(new File(Files.createTempDirectory("tc-tcp-cs2").toFile(), "s"));

        try (TcpStation station = new TcpStation())
        {
            startedAgainst((path, n) -> path.equals("/config/geraet.vrs") ? testACS2RequestsAreAsBefore.CS2_DEVICE
                : StandInStation.file(served.getPath() + path), station, rig ->
            {
                try
                {
                    assertTrue(rig.model.getNetworkCommState(), "precondition: the start-up sync failed");
                    assertTrue(!rig.model.isCS3(), "precondition: the stand-in was taken for a CS3");
                    assertEquals(proxyField(rig, "tcpReader"), null, "a CS2's proxy started a TCP listener");
                }
                catch (Exception e)
                {
                    throw new AssertionError(e);
                }
            });

            assertEquals(station.connections(), 0, "a CS2 was connected to over TCP");
        }
    }

    /**
     * A CS3 whose start-up sync failed is not connected to over TCP: it runs as an unconnected station does.  The
     * stand-in says it is a CS3 and then answers nothing more.
     *
     * MUTATION: in `MarklinControlStation.init`, take out `model.getNetworkCommState()` (and its `&&`).
     */
    @Test(timeOut = 120000)
    public void testACS3WhoseSyncFailedTriesNoTcp() throws Exception
    {
        java.util.function.BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", CS3_INFO);

        try (TcpStation station = new TcpStation())
        {
            startedAgainst((path, n) -> path.equals("/config/geraet.vrs") || path.equals("/app/api/info")
                ? cs3.apply(path, n) : null, station, rig ->
                assertTrue(rig.model.isCS3() && !rig.model.getNetworkCommState(), "precondition: the sync did not fail"));

            assertEquals(station.connections(), 0, "a CS3 whose sync failed was connected to over TCP");
        }
    }

    /**
     * A CS3 found at start-up is connected to over TCP.
     *
     * MUTATION: in `MarklinControlStation.init`, take out `proxy.connectOverTcp(...)`.
     */
    @Test(timeOut = 120000)
    public void testACS3IsReachedOverTcp() throws Exception
    {
        try (TcpStation station = new TcpStation())
        {
            startedAgainst(StandInStation.cs3("260", CS3_INFO), station, rig ->
                assertTrue(Components.waitFor(() -> station.connections() == 1, 5000), "a CS3 was not connected to over TCP"));
        }
    }

    /**
     * A connected model's pings, power, locomotive and accessory commands reach the TCP station as the frames `exec`
     * was handed, byte for byte, in order, each once: the last frames the station received are those, ending with a
     * marker frame sent after them.
     *
     * MUTATION: in `NetworkProxy.writeTcp`, write `message` twice.
     */
    @Test(timeOut = 120000)
    public void testTheFramesOverTcpAreTheFramesOfUdp() throws Exception
    {
        File root = Files.createTempDirectory("tc-tcp-frames").toFile();

        try (TcpStation station = new TcpStation().answering(true))
        {
            TcpStation.forSimulations(station.port(), () ->
            {
                try (support.StationRig rig = new support.StationRig(root,
                    StandInStation.cs3("260", "{\"softwareVersion\":\"2.6.1 (Build 3)\"}"), false, true))
                {
                    // The start-up's own frames went over UDP, before the sync found a CS3
                    assertTrue(Components.waitFor(() -> station.connections() == 1, 5000),
                        "precondition: the model is not connected over TCP");

                    List<CS2Message> sent = Collections.synchronizedList(new ArrayList<>());
                    rig.model.setSentMessageObserver(sent::add);

                    // Sent last, so that once it is in every frame before it is: one connection keeps their order
                    CS2Message last = new CS2Message(CS2Message.CAN_CMD_PING, new byte[] {0x46, 0x52});

                    try
                    {
                        rig.model.sendPing(true);
                        rig.model.go();
                        Locomotive loco = rig.model.getLocomotives().get(0);
                        loco.setDirection(Locomotive.locDirection.DIR_BACKWARD);
                        loco.setSpeed(30);
                        loco.setF(1, true);
                        loco.setSpeed(0);
                        rig.model.getAccessoryByAddress(5, Accessory.accessoryDecoderType.MM2).turn();
                        rig.model.stop();
                        rig.model.exec(last);

                        List<CS2Message> handed = new ArrayList<>(sent);
                        assertTrue(handed.size() >= 8, "only " + handed.size() + " commands were handed to the network");
                        assertTrue(Components.waitFor(() -> station.received().stream()
                            .anyMatch(f -> Arrays.equals(f.bytes, last.getRawMessage())), 5000),
                            "the last frame never reached the station");

                        // The frames that end with the last one, as many as were handed over
                        List<TcpStation.Frame> all = station.received();
                        int end = all.size();

                        while (!Arrays.equals(all.get(end - 1).bytes, last.getRawMessage())) end--;

                        assertTrue(end >= handed.size(), "the station received fewer frames than were handed over");
                        List<TcpStation.Frame> got = all.subList(end - handed.size(), end);

                        for (int i = 0; i < handed.size(); i++)
                        {
                            assertEquals(got.get(i).bytes, handed.get(i).getRawMessage(),
                                "frame " + (i + 1) + " over TCP is not the frame the program sent: " + handed.get(i));
                        }

                        assertEquals(all.size(), end, "frames reached the station after the last one handed over");
                    }
                    finally
                    {
                        rig.model.setSentMessageObserver(null);
                    }
                }

                return null;
            });
        }
    }
}
