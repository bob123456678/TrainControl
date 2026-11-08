package core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
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
 * MUTATION: in `NetworkProxy.writeTcp`, write `message` twice, and the frames claim fails.
 *
 * @author Adam
 */
public class testTheGoldensHoldOverTcp
{
    private interface Golden
    {
        void run() throws Exception;
    }

    private static void overTcp(Golden golden) throws Exception
    {
        try (TcpStation station = new TcpStation().answering(true))
        {
            TcpStation.forSimulations(station.port(), () ->
            {
                golden.run();
                return null;
            });

            assertTrue(station.connections() > 0, "no model connected over TCP, so the golden ran over UDP");
        }
    }

    private static void overUdpAfterTcpFailed(Golden golden) throws Exception
    {
        TcpStation.forSimulations(TcpStation.closedPort(), () ->
        {
            golden.run();
            return null;
        });
    }

    @Test(timeOut = 300000)
    public void testTheCS2RequestsAreAsBeforeOverTcp() throws Exception
    {
        overTcp(() -> new testACS2RequestsAreAsBefore().testTheRequestsOfACS2AreAsBefore());
    }

    @Test(timeOut = 300000)
    public void testTheCS2RequestsAreAsBeforeOverUdpAfterTcpFailed() throws Exception
    {
        overUdpAfterTcpFailed(() -> new testACS2RequestsAreAsBefore().testTheRequestsOfACS2AreAsBefore());
    }

    @Test(timeOut = 300000)
    public void testTheCS2SyncIsAsBeforeOverTcp() throws Exception
    {
        overTcp(() -> new testACS2SyncIsAsBefore().testASyncWithACS2IsAsBefore());
    }

    @Test(timeOut = 300000)
    public void testTheCS2SyncIsAsBeforeOverUdpAfterTcpFailed() throws Exception
    {
        overUdpAfterTcpFailed(() -> new testACS2SyncIsAsBefore().testASyncWithACS2IsAsBefore());
    }

    @Test(timeOut = 300000)
    public void testTheCS3SyncIsAsBeforeOverTcp() throws Exception
    {
        overTcp(() -> new testACS3SyncIsAsBefore().testASyncWithACS3IsAsBefore());
    }

    @Test(timeOut = 300000)
    public void testTheCS3SyncIsAsBeforeOverUdpAfterTcpFailed() throws Exception
    {
        overUdpAfterTcpFailed(() -> new testACS3SyncIsAsBefore().testASyncWithACS3IsAsBefore());
    }

    /**
     * A connected model's pings, power, locomotive and accessory commands reach the TCP station as the frames `exec`
     * was handed, byte for byte, in order, each once.
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
                    Thread.sleep(500);
                    int mark = station.received().size();
                    rig.model.setSentMessageObserver(sent::add);

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

                        Thread.sleep(1000);

                        List<CS2Message> handed = new ArrayList<>(sent);
                        assertTrue(handed.size() >= 7, "only " + handed.size() + " commands were handed to the network");
                        assertTrue(Components.waitFor(() -> station.received().size() >= mark + handed.size(), 5000),
                            "the station received " + (station.received().size() - mark) + " frames for "
                            + handed.size());
                        Thread.sleep(300);

                        List<TcpStation.Frame> got = station.received().subList(mark, station.received().size());
                        assertEquals(got.size(), handed.size(), "the station received a different number of frames");

                        for (int i = 0; i < handed.size(); i++)
                        {
                            assertEquals(got.get(i).bytes, handed.get(i).getRawMessage(),
                                "frame " + (i + 1) + " over TCP is not the frame the program sent: " + handed.get(i));
                        }
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
