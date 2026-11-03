package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import support.StandInStation;

/**
 * A sync with a CS3 takes each two-position accessory's position from `/app/api/mags/state`, without sending anything
 * and without counting an actuation; a station that will not say changes nothing and fails nothing (FR-171).
 *
 * A stand-in station serves Adam's CS3 plus (2.6.1) with its saved `mags.json` and `mags/state.json`
 * (`test/cs3_v261`); the layout is the same station's pages as a local sandbox.  In the saved state W 2L (MM2 2) and
 * W 11 (DCC 1) are "0", turned; W 4 (MM2 11), W 14 (DCC 25) and W 13 (DCC 2048) are "1", straight; S 1 (MM2 4, four
 * positions) is "2", which has no two-position meaning and is left alone.
 *
 * MUTATION: drop the adoptCS3AccessoryStates() call from syncWithCS2, and nothing moves.
 *
 * @author Adam
 */
public class testACS3SyncTakesTheStationsPositions
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static StandInStation station;

    /** What `/app/api/mags/state` answers, one entry per request (the last repeats); null is a 404. */
    private static volatile List<byte[]> stateBodies = new ArrayList<>();

    /** Run as `/app/api/mags/state` is asked for, before it answers. */
    private static volatile Runnable whileAsked = () -> { };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(new File("test/cs3_v261"));
        model = support.TestStation.simulated();

        station = new StandInStation((path, n) ->
        {
            switch (path)
            {
                case "/config/geraet.vrs": return StandInStation.CS3_DEVICE;
                case "/app/api/locos": return StandInStation.file("test/CS3_loks_v260.json");
                case "/app/api/automatics": return "[]".getBytes(StandardCharsets.UTF_8);
                case "/app/api/mags": return StandInStation.file("test/cs3_v261/app/api/mags.json");
                case "/app/api/mags/state":
                    whileAsked.run();
                    return stateBodies.get(Math.min(n, stateBodies.size()) - 1);
                default: return null;
            }
        });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        StandInStation.tearDown(station, model, sandbox);
    }

    @BeforeMethod
    public void forgetTheCounts()
    {
        station.forget();
        whileAsked = () -> { };
    }

    private static MarklinAccessory acc(int address, accessoryDecoderType type)
    {
        MarklinAccessory a = model.getAccessoryByAddressIfPresent(address, type);
        assertNotNull(a, type + " " + address + " was wired from the station's pages");
        return a;
    }

    /** Every accessory the claims look at, in the position opposite to the saved state's (S 1 straight). */
    private static void setOpposite()
    {
        acc(2, accessoryDecoderType.MM2).setSwitched(false);
        acc(11, accessoryDecoderType.MM2).setSwitched(true);
        acc(25, accessoryDecoderType.DCC).setSwitched(true);
        acc(1, accessoryDecoderType.DCC).setSwitched(false);
        acc(2048, accessoryDecoderType.DCC).setSwitched(true);
        acc(4, accessoryDecoderType.MM2).setSwitched(false);
    }

    private static void sync(byte[]... bodies) throws Exception
    {
        stateBodies = java.util.Arrays.asList(bodies);
        station.sync(model);

        assertTrue(model.isCS3(), "the stand-in was taken for a CS3");
    }

    private static void assertTheSavedPositions()
    {
        assertTrue(acc(2, accessoryDecoderType.MM2).isSwitched(), "W 2L, state 0: turned");
        assertFalse(acc(11, accessoryDecoderType.MM2).isSwitched(), "W 4, state 1: straight");
        assertFalse(acc(25, accessoryDecoderType.DCC).isSwitched(), "W 14, state 1: straight");
        assertTrue(acc(1, accessoryDecoderType.DCC).isSwitched(), "W 11, state 0: turned");
        assertFalse(acc(2048, accessoryDecoderType.DCC).isSwitched(), "W 13, state 1: straight");
        assertFalse(acc(4, accessoryDecoderType.MM2).isSwitched(), "S 1, four positions: left as it was");
    }

    private static void assertNothingMoved()
    {
        assertFalse(acc(2, accessoryDecoderType.MM2).isSwitched());
        assertTrue(acc(11, accessoryDecoderType.MM2).isSwitched());
        assertTrue(acc(25, accessoryDecoderType.DCC).isSwitched());
        assertFalse(acc(1, accessoryDecoderType.DCC).isSwitched());
        assertTrue(acc(2048, accessoryDecoderType.DCC).isSwitched());
    }

    /**
     * The saved state, answered at once: every two-position accessory takes it, and no actuation is counted.  The
     * accessory list is read once, by the routes, and not again for the positions.
     *
     * MUTATION: fetch `/app/api/mags` again in `parseAccessoryStatesCS3` (start from null, not `cs3Mags`), and it is
     * asked twice.
     */
    @Test
    public void testTheStationsPositionsAreTaken() throws Exception
    {
        setOpposite();
        int actuations = acc(2, accessoryDecoderType.MM2).getNumActuations();

        sync(StandInStation.file("test/cs3_v261/app/api/mags/state.json"));

        assertTheSavedPositions();
        assertEquals(acc(2, accessoryDecoderType.MM2).getNumActuations(), actuations, "a report is not an actuation");
        assertEquals(station.requests("/app/api/mags/state"), 1, "asked once: " + station.asked());
        assertEquals(station.requests("/app/api/mags"), 1, "the accessory list the routes read: " + station.asked());
    }

    /**
     * After a position is taken, the station's echoes count as they would have had TrainControl moved it there: an echo
     * of that position counts nothing, the move back counts one.
     *
     * MUTATION: leave `stateAtLastActuation` as it was in `adoptStationPosition`, and the first echo counts one.
     */
    @Test
    public void testATakenPositionCountsAsTheLastActuation() throws Exception
    {
        setOpposite();
        MarklinAccessory w2L = acc(2, accessoryDecoderType.MM2);
        w2L.parseMessage(support.Packets.accessoryEcho(w2L, false));

        sync(StandInStation.file("test/cs3_v261/app/api/mags/state.json"));
        w2L = acc(2, accessoryDecoderType.MM2);

        assertTrue(w2L.isSwitched(), "W 2L, state 0: turned");
        int actuations = w2L.getNumActuations();

        w2L.parseMessage(support.Packets.accessoryEcho(w2L, true));
        assertEquals(w2L.getNumActuations(), actuations, "the echo of the position taken");

        w2L.parseMessage(support.Packets.accessoryEcho(w2L, false));
        assertEquals(w2L.getNumActuations(), actuations + 1, "the move back");
    }

    /**
     * An echo that arrives while the positions are being read is newer than them, so it stands: W 2L, moved straight
     * by the echo, is not turned back by the station's older "0".
     *
     * MUTATION: drop the `lastEchoAt` check from `adoptStationPosition`, and W 2L is turned back.
     */
    @Test
    public void testAnEchoDuringTheReadStands() throws Exception
    {
        setOpposite();
        MarklinAccessory w2L = acc(2, accessoryDecoderType.MM2);
        w2L.parseMessage(support.Packets.accessoryEcho(w2L, true));
        whileAsked = () -> acc(2, accessoryDecoderType.MM2).parseMessage(support.Packets.accessoryEcho(w2L, false));

        sync(StandInStation.file("test/cs3_v261/app/api/mags/state.json"));

        assertFalse(acc(2, accessoryDecoderType.MM2).isSwitched(), "W 2L, as the echo during the read left it");
        assertTrue(acc(1, accessoryDecoderType.DCC).isSwitched(), "W 11, with no echo, takes the station's state 0");
    }

    /** The station's first answer empty, as Adam's CS3 answered the probe's first request: asked once more. */
    @Test
    public void testAnEmptyFirstAnswerIsAskedAgain() throws Exception
    {
        setOpposite();

        sync(new byte[0], StandInStation.file("test/cs3_v261/app/api/mags/state.json"));

        assertTheSavedPositions();
        assertEquals(station.requests("/app/api/mags/state"), 2, "asked twice: " + station.asked());
    }

    /**
     * The accessory file's `.stellung` means what the station's state means, 0 for turned: on Adam's saved files the
     * two agree for every article in the file at an address the station gives a two-position MM2 or DCC state, 22
     * articles at 21 addresses (D 2 shares W 13's).
     *
     * MUTATION: read `.stellung` 0 as straight again (CS2File.parseMags), and every one of them disagrees.
     */
    @Test
    public void testTheAccessoryFileReadsPositionsAsTheStationDoes() throws Exception
    {
        stateBodies = java.util.Arrays.asList(StandInStation.file("test/cs3_v261/app/api/mags/state.json"));
        CS2File parser = new CS2File(station.address(), model);
        java.util.Map<Integer, Boolean> states = parser.parseAccessoryStatesCS3();
        int compared = 0;

        for (MarklinAccessory a : parser.parseMags(CS2File.parseFile(CS2File.fetchURL(
            new File("test/cs3_v261/config/magnetartikel.cs2").toURI().toString()))))
        {
            // The file's .id is the address, which the station's UIDs count from 0
            Integer uid = MarklinAccessory.UIDfromAddress(a.getAddress() - 1, a.getDecoderType());

            if (states.containsKey(uid))
            {
                assertEquals(a.isSwitched(), (boolean) states.get(uid), a.getName() + " at " + a.getAddress());
                compared++;
            }
        }

        assertEquals(compared, 22, "the articles at an address with a two-position state");
    }

    /** No answer that can be read: the sync succeeds and nothing moves. */
    @Test
    public void testAStationThatWillNotSayChangesNothing() throws Exception
    {
        for (byte[] body : new byte[][] {new byte[0], null, "not json".getBytes(StandardCharsets.UTF_8),
            "[[\"48\",{}],[4144]]".getBytes(StandardCharsets.UTF_8)})
        {
            setOpposite();

            sync(body);

            assertNothingMoved();
        }
    }
}
