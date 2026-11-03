package core;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import support.StandInStation;

/**
 * A sync with a CS3 asks its firmware once: each probe reads the whole locomotive list (444 KB on Adam's CS3 plus), and
 * the route import and the locomotive import used to probe once each.  A CS2 sync is untouched.
 *
 * A stand-in station counts the requests; the layout is a local sandbox, so the layout sync never reaches it.
 *
 * MUTATION: have syncWithCS2 call the no-argument parseLocomotivesCS3() again, and a 2.6 sync reads the list four times
 * and a 2.5 sync probes twice.
 *
 * @author Adam
 */
public class testACS3SyncReadsTheLocomotiveListOnceEach
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static StandInStation station;

    /** "260", "250" (a CS3 before 2.6.0, `/app/api/loks`) or "cs2". */
    private static volatile String kind = "260";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();
        model = support.TestStation.simulated();
        station = new StandInStation((path, n) -> answer(path));
    }

    /** What the stand-in answers, as a 2.6 CS3, a 2.5 CS3 or a CS2; null for not found. */
    private static byte[] answer(String path)
    {
        boolean cs3 = !"cs2".equals(kind);

        switch (path)
        {
            case "/config/geraet.vrs": return cs3 ? StandInStation.CS3_DEVICE
                : "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case "/app/api/locos": return "260".equals(kind) ? StandInStation.file("test/CS3_loks_v260.json") : null;
            case "/app/api/loks": return "250".equals(kind) ? StandInStation.file("test/CS3_loks.json") : null;
            case "/app/api/mags": return cs3 ? StandInStation.file("test/CS3_mags.json") : null;
            case "/app/api/automatics": return StandInStation.file("260".equals(kind) ? "test/CS3_automatics_v260.json"
                : "250".equals(kind) ? "test/CS3_automatics.json" : "no such file");
            default: return cs3 || !path.startsWith("/config/") ? null : StandInStation.file("test" + path.substring(7));
        }
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
    }

    private static void sync(String which) throws Exception
    {
        kind = which;
        station.sync(model);

        assertEquals(model.isCS3(), !"cs2".equals(which), "the stand-in was taken for the right station");
    }

    /** 2.6 firmware: one probe, the route import's read and the locomotive import's read. */
    @Test
    public void testA260SyncReadsTheLocomotiveListThreeTimes() throws Exception
    {
        sync("260");

        assertNotNull(model.getLocByName("02 0314-1 DDR"), "the 2.6 locomotive list was imported");
        assertEquals(station.requests("/app/api/locos"), 3, "all requests: " + station.asked());
        assertEquals(station.requests("/app/api/loks"), 0, "all requests: " + station.asked());
    }

    /** Before 2.6.0: the probe finds no `/app/api/locos` once, and both imports read `/app/api/loks`. */
    @Test
    public void testA250SyncProbesOnce() throws Exception
    {
        sync("250");

        assertEquals(station.requests("/app/api/locos"), 1, "one probe; all requests: " + station.asked());
        assertEquals(station.requests("/app/api/loks"), 2, "both imports; all requests: " + station.asked());
    }

    /** A CS2 never reaches the CS3 interface (green before and after: the CS2 path is not changed). */
    @Test
    public void testACS2SyncAsksNoCS3Interface() throws Exception
    {
        sync("cs2");

        assertNotNull(model.getLocByName("BR 85 006 Gl."), "the CS2 locomotive list was imported");
        assertFalse(station.asked().contains("/app/"), "a CS2 sync asked the CS3 interface: " + station.asked());
        assertEquals(station.requests("/config/lokomotive.cs2"), 1, "all requests: " + station.asked());
    }
}
