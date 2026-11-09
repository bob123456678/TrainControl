package core;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import support.Components;
import support.StandInStation;
import support.StationRig;
import support.TcpStation;

/**
 * Sync with Central Station asks a CS3 for its sensors' true states and puts TrainControl's right (OB-418).  Read-only
 * on the station: the only frame it adds is the s88 query (0x11, device and contact), sent only for a contact whose
 * device a real event showed, at that device.  An answer older than an event since its query never wins, and a station
 * that does not answer holds the sync up by the resync's bound only.  A CS2's sync asks nothing: its goldens hold.
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

    /** Runs a claim against a model connected over TCP to a CS3. */
    private static void connected(Claim claim) throws Exception
    {
        try (TcpStation station = new TcpStation())
        {
            TcpStation.forSimulations(station.port(), () ->
            {
                try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-s88").toFile(),
                    StandInStation.cs3("260", "{\"softwareVersion\":\"2.6.1 (Build 3)\"}"), false, true))
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
     * `NetworkProxy.askOnce`, `this.sensorDevice.get(contact)` to `this.sensorDevice.getOrDefault(contact, 2)`.
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

            TreeSet<String> queries = new TreeSet<>();

            for (byte[] f : during)
            {
                assertEquals(f[1] & 0xff, CS2Message.CMD_ACC_SENSOR << 1, "the sync sent a frame other than a query: "
                    + Arrays.toString(f));
                assertEquals(f[4], 4, "a query without four data bytes: " + Arrays.toString(f));
                queries.add(((f[5] & 0xff) << 8 | (f[6] & 0xff)) + "/" + ((f[7] & 0xff) << 8 | (f[8] & 0xff)));
            }

            assertEquals(during.size(), 2, "not one query per sensor with a known device");
            assertEquals(queries.toString(), "[2/2101, 3/2102]", "the queries are not each sensor at its device");
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
     * MUTATION: in `NetworkProxy.learn`, take out `this.overtaken.replace(contact, true);`.
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
}
