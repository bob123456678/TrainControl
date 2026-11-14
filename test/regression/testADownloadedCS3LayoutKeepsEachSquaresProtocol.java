package regression;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import support.StandInStation;

/**
 * A layout downloaded from a CS3 keeps each square's protocol: read from the station, an address with an MM2 and a DCC
 * article is told apart by the CS3's own plan (OB-402), and the copy that Download Central Station Layout Files writes,
 * opened as the window opens it, gives every switch, signal and uncoupler square the protocol it had (OB-465).
 *
 * A stand-in CS3 serves a sandbox copy of `test/layouts/cs3-main-layout`, the owner's station: W 14 (DCC) at (4,7) and
 * W 15 (MM2) at (5,9) of the grid page Main Layout are both address 25; W 11 (DCC, a slip at (16,6) and (17,6) of Main
 * Layout) and W 1R (MM2, (10,4) of Startpackung) both address 1.  The page files name only the address.  What the
 * snapshot does not hold (locomotives, routes) comes from the 2.6.1 fixtures.
 *
 * MUTATION: drop the `withSavedProtocols` call from `CS2File.downloadCS2Layout`, and W 15 and W 1R open as DCC.
 *
 * @author Adam
 */
public class testADownloadedCS3LayoutKeepsEachSquaresProtocol
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static StandInStation station;

    /** While set, the station's plan has W 14 and W 15 swapped, as when the user exchanges them at the CS3. */
    private static volatile boolean swapped;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // A copy, so nothing reads or writes the snapshot itself
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("cs3-main-layout"));
        model = support.TestStation.simulated();

        String snapshot = sandbox.getFolder().getAbsolutePath();
        java.util.function.BiFunction<String, Integer, byte[]> rest = StandInStation.cs3("260",
            new String(StandInStation.file(snapshot + "/app/api/info.json"), java.nio.charset.StandardCharsets.UTF_8));

        station = new StandInStation((path, n) ->
        {
            byte[] own = StandInStation.file(snapshot + (path.startsWith("/app/api/") ? path + ".json" : path));

            if (swapped && "/app/api/gbs".equals(path)) own = withW14AndW15Swapped(own);

            return own != null ? own : rest.apply(path, n);
        });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        StandInStation.tearDown(station, model, sandbox);
    }

    /** Every addressed switch, signal and uncoupler square of the model's pages, by page and square. */
    private static Map<String, String> protocols()
    {
        Map<String, String> out = new TreeMap<>();

        for (String name : model.getLayoutList())
        {
            for (LayoutDiagramComponent c : model.getLayout(name).getAll())
            {
                if (c.isAccessory() && c.getAddress() > 0)
                {
                    out.put(name + " " + c.getX() + "," + c.getY(), String.valueOf(c.getProtocol()));
                }
            }
        }

        return out;
    }

    private static void assertTheTwoProtocolSquares(Map<String, String> squares, String when)
    {
        assertEquals(squares.get("Main Layout 4,7"), "DCC", "W 14 at 25, " + when);
        assertEquals(squares.get("Main Layout 5,9"), "MM2", "W 15 at 25, " + when);
        assertEquals(squares.get("Main Layout 16,6"), "DCC", "W 11 at 1, its first square, " + when);
        assertEquals(squares.get("Main Layout 17,6"), "DCC", "W 11 at 1, its second square, " + when);
        assertEquals(squares.get("Startpackung 10,4"), "MM2", "W 1R at 1, " + when);
    }

    /**
     * Read from the station, downloaded, and opened as the window opens a downloaded layout (the layout folder
     * preference set to it, the diagram read again): every square has the protocol it had on the station.
     */
    @Test(timeOut = 120000)
    public void testEverySquareOpensWithTheProtocolItHadOnTheStation() throws Exception
    {
        String was = support.TestStationAddress.get();
        File copy = new File(sandbox.getFolder(), "downloaded");

        try
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            model.clearLayouts();
            support.TestStationAddress.set(station.address());
            assertTrue(model.syncWithCS2() >= 0, "the sync with the stand-in CS3: " + model.getLastSyncError());

            Map<String, String> read = protocols();
            assertTheTwoProtocolSquares(read, "read from the station");

            model.downloadLayout(copy);
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, copy.getAbsolutePath());
            model.refreshLayouts();

            Map<String, String> opened = protocols();
            assertTheTwoProtocolSquares(opened, "in the downloaded copy");
            assertEquals(opened, read, "every addressed square, downloaded and opened, against the station's");
        }
        finally
        {
            support.TestStationAddress.set(was);
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        }
    }

    /** The plan with the two articles at address 25 in each other's places. */
    private static byte[] withW14AndW15Swapped(byte[] gbs)
    {
        String plan = new String(gbs, java.nio.charset.StandardCharsets.UTF_8);
        String w14 = "\"internname\":\"W#2014\",\"x\":35,\"y\":525";
        String w15 = "\"internname\":\"W#2015\",\"x\":105,\"y\":665";

        assertTrue(plan.contains(w14) && plan.contains(w15), "precondition: the snapshot's plan moved W 14 or W 15");

        return plan.replace(w14, "W14HERE").replace(w15, w14.replace("2014", "2015")).replace("W14HERE",
            w15.replace("2015", "2014")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * A station changed after it was read: W 14 and W 15 exchanged in the CS3's plan, so (4,7) is now the MM2 article
     * and (5,9) the DCC one.  The copy downloaded then has every square as a fresh read of the station has it today,
     * not as the diagram read at the start.
     *
     * MUTATION: in `CS2File.downloadCS2Layout`, take each square's protocol from `this.control.getLayout(layoutName)`
     * (the diagram read before the change) instead of the page parsed again, and (4,7) and (5,9) keep their old
     * protocols.
     */
    @Test(timeOut = 120000)
    public void testACopyDownloadedAfterTheStationChangedMatchesTheStationToday() throws Exception
    {
        String was = support.TestStationAddress.get();
        File copy = new File(sandbox.getFolder(), "downloaded-after-a-change");

        try
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            model.clearLayouts();
            support.TestStationAddress.set(station.address());
            assertTrue(model.syncWithCS2() >= 0, "the sync with the stand-in CS3: " + model.getLastSyncError());
            assertTheTwoProtocolSquares(protocols(), "read from the station before the change");

            swapped = true;
            model.downloadLayout(copy);

            // The station as it is now, read afresh
            model.clearLayouts();
            assertTrue(model.syncWithCS2() >= 0, "the sync after the change: " + model.getLastSyncError());
            Map<String, String> today = protocols();

            assertEquals(today.get("Main Layout 4,7"), "MM2", "precondition: the station read afresh has W 15 at (4,7)");
            assertEquals(today.get("Main Layout 5,9"), "DCC", "precondition: the station read afresh has W 14 at (5,9)");

            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, copy.getAbsolutePath());
            model.refreshLayouts();

            assertEquals(protocols(), today, "every addressed square of a copy downloaded after the station changed,"
                + " against the station read afresh");
        }
        finally
        {
            swapped = false;
            support.TestStationAddress.set(was);
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        }
    }
}
