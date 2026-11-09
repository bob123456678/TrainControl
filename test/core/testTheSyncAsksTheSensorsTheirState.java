package core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiFunction;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinSimpleComponent;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import support.Components;
import support.StandInStation;
import support.StationRig;
import support.TcpStation;

/**
 * Sync with Central Station asks a CS3 for its sensors' true states and puts TrainControl's right (OB-418).  Read-only
 * on the station: the only frame it adds is the s88 query (0x11, device and contact), sent only for a contact whose
 * device is known, at that device: the one a real event showed, else the one saved before a restart.  An answer older
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

    /**
     * A device learned from an event is saved with its sensor, and after a restart the sensor is asked at it before any
     * event of its own (OB-418).  The sensor is the stand-in's diagram's, as a CS3 sync takes out sensors on no page.
     *
     * MUTATION: in `MarklinControlStation.saveState`, `s88DeviceToSave(f.getUID())` to `null`.  And: in
     * `MarklinControlStation.syncWithCS2`, take out `this.NetworkInterface.rememberDevices(this.savedS88Devices);`.
     */
    @Test(timeOut = 180000)
    public void testADeviceLearnedBeforeARestartIsAskedAfterIt() throws Exception
    {
        File root = Files.createTempDirectory("tc-sync-s88-restart").toFile();
        int[] contact = new int[1];

        connected(root, CS3, (rig, station) ->
        {
            assertEquals(rig.model.knownSensors().size(), 1, "precondition: the diagram has not one sensor");
            contact[0] = rig.model.knownSensors().get(0);
            station.send(TcpStation.event(3, contact[0], true));
            assertTrue(shows(rig, contact[0], true), "precondition: the event did not arrive");
            rig.model.saveState(false);
        });

        connected(root, CS3, (rig, station) ->
        {
            synced(rig, station);
            assertTrue(asked(station).contains("3/" + contact[0]), "the saved device was not asked: " + asked(station));
        });
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
