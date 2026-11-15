package regression;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
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

    /** While set, the station's plan does not arrive: the connection is dropped, as a failed read, not a missing file. */
    private static volatile boolean planFails;

    private static String addressWas;

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

            if (planFails && "/app/api/gbs".equals(path)) throw new IllegalStateException("the plan does not arrive");

            return own != null ? own : rest.apply(path, n);
        });

        addressWas = support.TestStationAddress.get();
        support.TestStationAddress.set(station.address());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        support.TestStationAddress.set(addressWas);
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

    @AfterMethod(alwaysRun = true)
    public void backOnTheStation()
    {
        swapped = false;
        planFails = false;
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
    }

    /** The station's pages read afresh, as switching to its layout reads them. */
    private static Map<String, String> readTheStation() throws Exception
    {
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        model.clearLayouts();
        assertTrue(model.syncWithCS2() >= 0, "the sync with the stand-in CS3: " + model.getLastSyncError());

        return protocols();
    }

    /** A downloaded copy opened as the window opens one: the folder preference set to it, the diagram read again. */
    private static Map<String, String> openTheCopy(File copy) throws Exception
    {
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, copy.getAbsolutePath());
        model.refreshLayouts();

        return protocols();
    }

    /**
     * Read from the station, downloaded, and opened as the window opens a downloaded layout: every square has the
     * protocol it had on the station.
     */
    @Test(timeOut = 120000)
    public void testEverySquareOpensWithTheProtocolItHadOnTheStation() throws Exception
    {
        Map<String, String> read = readTheStation();
        assertTheTwoProtocolSquares(read, "read from the station");

        File copy = new File(sandbox.getFolder(), "downloaded");
        model.downloadLayout(copy);

        Map<String, String> opened = openTheCopy(copy);
        assertTheTwoProtocolSquares(opened, "in the downloaded copy");
        assertEquals(opened, read, "every addressed square, downloaded and opened, against the station's");
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
        assertTheTwoProtocolSquares(readTheStation(), "read from the station before the change");

        swapped = true;
        File copy = new File(sandbox.getFolder(), "downloaded-after-a-change");
        model.downloadLayout(copy);

        Map<String, String> today = readTheStation();
        assertEquals(today.get("Main Layout 4,7"), "MM2", "precondition: the station read afresh has W 15 at (4,7)");
        assertEquals(today.get("Main Layout 5,9"), "DCC", "precondition: the station read afresh has W 14 at (5,9)");

        assertEquals(openTheCopy(copy), today, "every addressed square of a copy downloaded after the station changed,"
            + " against the station read afresh");
    }

    /**
     * A download whose CS3 plan fails to read writes nothing into the folder: the user agreed to replace the layout
     * there, but not with pages whose two-protocol squares carry the accessory list's guess, which a later Open Layout
     * would read as written.
     *
     * MUTATION: in `CS2File.downloadCS2Layout`, write each page as it is parsed again, before the plan's failure is
     * asked, and the folder holds the guessed pages.
     */
    @Test(timeOut = 120000)
    public void testADownloadWhosePlanFailsWritesNothing() throws Exception
    {
        assertTheTwoProtocolSquares(readTheStation(), "read from the station");

        File copy = new File(sandbox.getFolder(), "downloaded-without-a-plan");
        File index = new File(copy, "config/gleisbild.cs2");

        assertTrue(index.getParentFile().mkdirs(), "could not build the fixture");
        byte[] before = "the layout there before".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(index.toPath(), before);

        planFails = true;

        try
        {
            model.downloadLayout(copy);

            fail("a download whose plan did not arrive was reported as done");
        }
        catch (java.io.IOException expected)
        {
        }

        java.util.List<String> files = new java.util.ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(copy.toPath()))
        {
            walk.filter(java.nio.file.Files::isRegularFile)
                .forEach(f -> files.add(copy.toPath().relativize(f).toString()));
        }

        assertEquals(files, java.util.Collections.singletonList(new File("config/gleisbild.cs2").getPath()),
            "a download whose plan did not arrive wrote into the folder");
        assertEquals(java.nio.file.Files.readAllBytes(index.toPath()), before,
            "a download whose plan did not arrive replaced the layout's index");
    }
}
