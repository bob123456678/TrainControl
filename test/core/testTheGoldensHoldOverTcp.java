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
import support.StandInStation;
import support.TcpStation;

/**
 * The station goldens hold whichever way the frames go (FR-186): the CS2 requests, the CS2 sync and the CS3 sync are
 * run, unchanged, with every model connected over TCP to a `support.TcpStation`, and again with TCP refused so that
 * each falls back to UDP; each compares with its own golden file.  And over TCP the station receives exactly the frames
 * the program handed the network, byte for byte and in order: the bytes `testUdpMessagesReachTheWire` shows a datagram
 * carries.
 *
 * MUTATION: in `NetworkProxy.writeTcp`, write `message` twice, and the frames claim fails; have
 * `NetworkProxy.simulationTcpPort` return 0, and each golden over TCP fails on its connection count.
 *
 * @author Adam
 */
public class testTheGoldensHoldOverTcp
{
    private interface Golden
    {
        void run() throws Exception;
    }

    /** Each golden, by name, with TCP accepted (true) or refused. */
    @DataProvider(name = "goldens")
    public static Object[][] goldens()
    {
        String[] names = {"CS2 requests", "CS2 sync", "CS3 sync"};
        Object[][] runs = new Object[names.length * 2][];

        for (int i = 0; i < runs.length; i++) runs[i] = new Object[] {names[i / 2], i % 2 == 0};

        return runs;
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

    @Test(dataProvider = "goldens", timeOut = 300000)
    public void testTheGoldenIsAsBefore(String name, boolean tcpAccepted) throws Exception
    {
        try (TcpStation station = new TcpStation().answering(true))
        {
            TcpStation.forSimulations(tcpAccepted ? station.port() : TcpStation.closedPort(), () ->
            {
                golden(name).run();
                return null;
            });

            assertEquals(station.connections() > 0, tcpAccepted, tcpAccepted
                ? "no model connected over TCP, so the golden ran over UDP" : "a model connected to the TCP station");
        }
    }

    /**
     * A connected model's pings, power, locomotive and accessory commands reach the TCP station as the frames `exec`
     * was handed, byte for byte, in order, each once: the last frames the station received are those, ending with a
     * marker frame sent after them.
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
                    assertTrue(Components.waitFor(() -> station.received().size() >= 1, 5000),
                        "precondition: the start-up's ping did not reach the station, so the model is not connected");

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
