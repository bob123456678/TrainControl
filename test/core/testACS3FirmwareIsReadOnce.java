package core;

import java.io.File;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.marklin.file.FirmwareVersion;
import support.StandInStation;
import support.StationRig;

/**
 * A CS3 sync reads the firmware once, from /app/api/info, and branches on it (FR-170).  Check for Renamed Locomotives
 * and the layout download reuse what the sync found: no second info request, no probe.  When info cannot be read the
 * old probe of the 2.6 locomotive endpoint finds the kind, once.  A CS2 never asks (testACS2RequestsAreAsBefore).
 *
 * MUTATION: have cs3Firmware in CS2File forget its answer (never store it in `this.cs3Firmware`), and the rename check
 * and the download ask /app/api/info again.  Make openCS3Locos rethrow when the chosen endpoint is not found, and the two
 * stations whose info names the wrong endpoint fail their sync.
 *
 * @author Adam
 */
public class testACS3FirmwareIsReadOnce
{
    private static final String INFO_261 = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    private static File folder() throws Exception
    {
        return Files.createTempDirectory("tc-cs3-firmware").toFile();
    }

    /** Syncs, then asks for renamed locomotives and downloads; the counts after each of the three. */
    private static String afterEach(String firmware, String info) throws Exception
    {
        File root = folder();

        try (StationRig rig = new StationRig(root, StandInStation.cs3(firmware, info)))
        {
            StandInStation s = rig.station;
            String out = "";

            assertTrue(rig.model.syncWithCS2() >= 0, "the sync succeeds: " + rig.model.getLastSyncError());
            out += count(s) + ";";
            rig.model.getRenameProposals(true);
            out += count(s) + ";";
            rig.model.downloadLayout(new File(root, "download"));
            return out + count(s);
        }
    }

    /** info, locos (the 2.6 list or its probe) and loks (the older list) requests so far. */
    private static String count(StandInStation s)
    {
        return s.requests("/app/api/info") + "/" + s.requests("/app/api/locos") + "/" + s.requests("/app/api/loks");
    }

    @Test
    public void testTheVersionIsReadFromTheInfoText()
    {
        assertTrue(FirmwareVersion.parse("2.6.1 (Build 3)").atLeast(2, 6), "2.6.1");
        assertTrue(FirmwareVersion.parse("2.10.0").atLeast(2, 6), "2.10 is later than 2.6");
        assertTrue(FirmwareVersion.parse("3.0").atLeast(2, 6), "3.0");
        assertFalse(FirmwareVersion.parse("2.5.9 (Build 1)").atLeast(2, 6), "2.5.9");
        assertNull(FirmwareVersion.parse("unknown"), "text with no version");
        assertNull(FirmwareVersion.parse(null), "no text");
    }

    /**
     * A device file (geraet.vrs) states the serial number on its `.sernum` line, a CS2's and a real CS3 plus's alike,
     * CRLF lines too; only the CS3's names a CS3; a serial number missing, blank or 0 is none (FR-242).
     *
     * MUTATION: take a serial number of 0 in `CS2File.known`, and this fails.
     */
    @Test
    public void testTheDeviceFileSaysTheSerialNumber()
    {
        String cs2 = "[geraet]\nversion\n .minor=1\ngeraet\n .sernum=4321\n .gfpuid=1a2b3c4d\n .guiuid=1a2b3c4e\n"
            + " .hardvers=HW:2.0\n";
        String cs3 = new String(StandInStation.CS3_DEVICE, java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(CS2File.deviceSerial(cs2), "4321", "a CS2's serial number");
        assertEquals(CS2File.deviceSerial(cs2.replace("\n", "\r\n")), "4321", "CRLF lines");
        assertEquals(CS2File.deviceSerial(cs3), "1234", "a CS3's serial number");
        assertTrue(CS2File.isCS3Device(cs3), "a CS3's device file names a CS3");
        assertFalse(CS2File.isCS3Device(cs2), "a CS2's does not");
        assertFalse(CS2File.isCS3Device(null), "no text is no CS3");
        assertNull(CS2File.deviceSerial("geraet\n .sernum=0\n"), "a serial number of 0");
        assertNull(CS2File.deviceSerial("geraet\n .sernum=\n"), "a blank serial number");
        assertNull(CS2File.deviceSerial(""), "an empty file");
    }

    /**
     * `isCS3(String)` answers for the device file at an address, read here from local file URLs.
     *
     * MUTATION: make `CS2File.isCS3(String)` return `isCS3Device(null)`, and this fails.
     */
    @Test
    public void testAnAddressIsAskedWhetherItIsACS3() throws Exception
    {
        java.nio.file.Path cs3 = java.nio.file.Files.createTempFile("geraet", ".vrs");
        java.nio.file.Path cs2 = java.nio.file.Files.createTempFile("geraet", ".vrs");

        try
        {
            java.nio.file.Files.write(cs3, StandInStation.CS3_DEVICE);
            java.nio.file.Files.write(cs2, "[geraet]\ngeraet\n .sernum=4321\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(CS2File.isCS3(cs3.toUri().toString()), "a CS3's device file");
            assertFalse(CS2File.isCS3(cs2.toUri().toString()), "a CS2's");
        }
        finally
        {
            java.nio.file.Files.delete(cs3);
            java.nio.file.Files.delete(cs2);
        }
    }

    /** The firmware and serial number a CS3 sync keeps, its device file `device` and its info `info`. */
    private static String afterSync(byte[] device, String info) throws Exception
    {
        java.util.function.BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", info);

        try (StationRig rig = new StationRig(folder(),
            (path, n) -> path.endsWith("geraet.vrs") ? device : cs3.apply(path, n)))
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync fails: " + rig.model.getLastSyncError());
            return rig.model.getStationFirmware() + " " + rig.model.getStationSerial();
        }
    }

    /**
     * A CS3 sync keeps the firmware its info gives, and the serial number its device file gives, else its info's; an
     * info serial number of 0 is none (FR-242).
     *
     * MUTATION: drop the serial number from `logTheFirmware`'s call in cs3Firmware, and this fails.
     */
    @Test
    public void testACS3SyncKeepsItsInfosFirmwareAndSerialNumber() throws Exception
    {
        String info = "{\"softwareVersion\":\"2.6.1 (Build 3)\",\"serialNumber\":\"60226123\"}";
        byte[] noSerial =
            "[geraet]\ngeraet\n .produkt=Central Station 3 plus\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(afterSync(StandInStation.CS3_DEVICE, info), "2.6.1 (Build 3) 1234",
            "the device file's serial first");
        assertEquals(afterSync(noSerial, info), "2.6.1 (Build 3) 60226123", "the info's with none in the file");
        assertEquals(afterSync(noSerial, info.replace("60226123", "0")), "2.6.1 (Build 3) null", "an info serial of 0");
    }

    /** 2.6.1: the sync asks info once and reads the 2.6 list once (FR-207); the two actions add one read of it each. */
    @Test
    public void testA261SyncReadsTheFirmwareOnceAndTheActionsReuseIt() throws Exception
    {
        assertEquals(afterEach("260", INFO_261), "1/1/0;1/2/0;1/3/0");
    }

    /** 2.5: no probe at all; the one read of the list is of the older one. */
    @Test
    public void testA25SyncNeedsNoProbe() throws Exception
    {
        assertEquals(afterEach("250", "{\"softwareVersion\":\"2.5.1 (Build 1)\"}"), "1/0/1;1/0/2;1/0/3");
    }

    /** A station that does not answer info is probed once, as before, and the sync reads the probe's list; the actions reuse its answer. */
    @Test
    public void testAStationWithNoInfoIsProbedOnce() throws Exception
    {
        assertEquals(afterEach("260", null), "1/1/0;1/2/0;1/3/0");
        assertEquals(afterEach("250", null), "1/1/1;1/1/2;1/1/3");
    }

    /** An info answer with no readable version is treated as no answer. */
    @Test
    public void testAnUnreadableVersionFallsBackToTheProbe() throws Exception
    {
        assertEquals(afterEach("260", "{\"softwareVersion\":\"unknown\"}"), "1/1/0;1/2/0;1/3/0");
        assertEquals(afterEach("260", "{\"name\":\"CS3\"}"), "1/1/0;1/2/0;1/3/0");
        assertEquals(afterEach("260", "not json"), "1/1/0;1/2/0;1/3/0");
    }

    /** Info says 2.5 and only the 2.6 list is there: the old probe decides, once, and the sync, the check and the download follow it. */
    @Test
    public void testAnInfoThatNamesAnEndpointTheStationLacksIsOverruledByTheProbe() throws Exception
    {
        assertEquals(afterEach("260", "{\"softwareVersion\":\"2.5.1 (Build 1)\"}"), "1/1/1;1/2/1;1/3/1");
    }

    /** Info says 2.6 and only the older list is there: the list not found is asked for again by the probe. */
    @Test
    public void testAnInfoThatNamesTheNewEndpointOnAnOlderStationIsOverruledToo() throws Exception
    {
        assertEquals(afterEach("250", INFO_261), "1/2/1;1/2/2;1/2/3");
    }

    /**
     * A CS3 sync logs the firmware in the station's own words, once: a second sync reads it again but does not repeat
     * it (MT-895).
     *
     * MUTATION: take the `logTheFirmware` call out of cs3Firmware in CS2File, and this fails.
     */
    @Test
    public void testTheFirmwareIsLoggedOnceInTheStationsOwnWords() throws Exception
    {
        try (StationRig rig = new StationRig(folder(), StandInStation.cs3("260", INFO_261)))
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the first sync fails: " + rig.model.getLastSyncError());
            assertTrue(rig.model.syncWithCS2() >= 0, "the second sync fails: " + rig.model.getLastSyncError());

            java.util.List<String> log = rig.takeLog();
            String line = org.traincontrol.util.I18n.f("log.cs3Firmware", "2.6.1 (Build 3)");

            assertEquals(log.stream().filter(line::equals).count(), 1L, "the firmware is not logged once: " + log);
        }
    }

    /** With no sync before, the action finds the kind itself with one request, and asks no more after. */
    @Test
    public void testAnActionBeforeAnySyncFindsTheKindWithOneRequest() throws Exception
    {
        try (StandInStation s = new StandInStation(StandInStation.cs3("260", INFO_261)))
        {
            CS2File parser = new CS2File(s.address(), null);

            parser.parseLocomotivesCS3();
            parser.parseLocomotivesCS3();
            assertEquals(count(s), "1/2/0");
        }
    }
}
