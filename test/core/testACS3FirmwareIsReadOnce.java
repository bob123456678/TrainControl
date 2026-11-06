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
 * and the download ask /app/api/info again.
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

            rig.model.syncWithCS2();
            out += count(s) + ";";
            rig.model.getRenameProposals();
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

    /** 2.6.1: the sync asks info once and reads the 2.6 list twice; the two actions add one read of it each. */
    @Test
    public void testA261SyncReadsTheFirmwareOnceAndTheActionsReuseIt() throws Exception
    {
        assertEquals(afterEach("260", INFO_261), "1/2/0;1/3/0;1/4/0");
    }

    /** 2.5: no probe at all; both reads of the list are of the older one. */
    @Test
    public void testA25SyncNeedsNoProbe() throws Exception
    {
        assertEquals(afterEach("250", "{\"softwareVersion\":\"2.5.1 (Build 1)\"}"), "1/0/2;1/0/3;1/0/4");
    }

    /** A station that does not answer info is probed once, as before; the actions reuse the probe's answer. */
    @Test
    public void testAStationWithNoInfoIsProbedOnce() throws Exception
    {
        assertEquals(afterEach("260", null), "1/3/0;1/4/0;1/5/0");
        assertEquals(afterEach("250", null), "1/1/2;1/1/3;1/1/4");
    }

    /** An info answer with no readable version is treated as no answer. */
    @Test
    public void testAnUnreadableVersionFallsBackToTheProbe() throws Exception
    {
        assertEquals(afterEach("260", "{\"softwareVersion\":\"unknown\"}"), "1/3/0;1/4/0;1/5/0");
        assertEquals(afterEach("260", "{\"name\":\"CS3\"}"), "1/3/0;1/4/0;1/5/0");
        assertEquals(afterEach("260", "not json"), "1/3/0;1/4/0;1/5/0");
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
