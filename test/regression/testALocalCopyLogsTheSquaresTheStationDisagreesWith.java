package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import support.StandInStation;

/**
 * A sync from a local copy logs one line for each accessory square whose protocol the station's accessory list does
 * not have at its address; on a CS3, a square that the station's plan of its page gives the address's other article
 * is logged as a hint.  No square is changed (FR-219).
 *
 * A stand-in serves a sandbox copy of `test/layouts/cs3-main-layout` as a CS3, or as a CS2 by its device file.  The
 * local copy is its download, with two squares of Main Layout set to DCC: (8,1), W 3 at address 10, which the station
 * has only as MM2, and (5,9), W 15 at address 25, which the station has as both, its plan putting W 15 (MM2) there.
 *
 * MUTATION: drop the `logSquaresTheStationDisagreesWith` call from `MarklinControlStation.syncThroughTheNewParser`,
 * and nothing is logged.
 *
 * @author Adam
 */
public class testALocalCopyLogsTheSquaresTheStationDisagreesWith
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static StandInStation station;
    private static final List<String> logged = Collections.synchronizedList(new ArrayList<>());
    private static final Handler tap = support.LogTap.messagesInto(logged);
    private static String addressWas;
    private static File copy;

    /** While set, the stand-in's device file says Central Station 2. */
    private static volatile boolean cs2;

    private static final String MISMATCH = "Layout: the square at 8,1 on page Main Layout is address 10 DCC, but the"
        + " Central Station has address 10 only as MM2.  If the station is right, set the square’s protocol in the"
        + " editor.";

    private static final String HINT = "Layout: hint: the square at 5,9 on page Main Layout is address 25 DCC, but the"
        + " Central Station’s plan of that page puts W 15 (MM2) there.  If the page still matches the station’s,"
        + " set the square’s protocol in the editor.";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("cs3-main-layout"));
        model = support.TestStation.simulated();

        String snapshot = sandbox.getFolder().getAbsolutePath();
        java.util.function.BiFunction<String, Integer, byte[]> rest = StandInStation.cs3("260",
            new String(StandInStation.file(snapshot + "/app/api/info.json"), StandardCharsets.UTF_8));

        station = new StandInStation((path, n) ->
        {
            if (cs2 && "/config/geraet.vrs".equals(path))
            {
                return "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8);
            }

            if (cs2 && "/config/lokomotive.cs2".equals(path)) return "[lokomotive]\n".getBytes(StandardCharsets.UTF_8);

            byte[] own = StandInStation.file(snapshot + (path.startsWith("/app/api/") ? path + ".json" : path));

            return own != null ? own : rest.apply(path, n);
        });

        addressWas = support.TestStationAddress.get();
        support.TestStationAddress.set(station.address());
        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);

        // The copy as Download Central Station Layout Files writes it, every square with its protocol
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        model.clearLayouts();
        assertTrue(model.syncWithCS2() >= 0, "the sync with the stand-in CS3: " + model.getLastSyncError());
        copy = new File(sandbox.getFolder(), "downloaded");
        model.downloadLayout(copy);

        setToDCC("0x40108");
        setToDCC("0x40905");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);
        support.TestStationAddress.set(addressWas);
        StandInStation.tearDown(station, model, sandbox);
    }

    @BeforeMethod
    public void reset()
    {
        cs2 = false;
        station.forget();

        // The model drops a message equal to the one before it, so a line repeated from the last claim would be lost
        model.log("next claim " + System.nanoTime());
        logged.clear();
    }

    /** Gives the copy's Main Layout square of this element id the protocol DCC. */
    private static void setToDCC(String id) throws Exception
    {
        File page = new File(copy, "config/gleisbilder/Main Layout.cs2");
        String text = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8);
        Matcher block = Pattern.compile("(?ms)(^ \\.id=" + id + "\\r?$(?:(?!^element).)*?^ \\.prot=)\\w+")
            .matcher(text);

        assertTrue(block.find(), "the downloaded page has a .prot on square " + id);
        Files.write(page.toPath(), (text.substring(0, block.start()) + block.group(1) + "DCC"
            + text.substring(block.end())).getBytes(StandardCharsets.UTF_8));
    }

    /** Syncs from the copy and returns the squares' lines it logged. */
    private static List<String> syncFromTheCopy() throws Exception
    {
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, copy.getAbsolutePath());

        try
        {
            assertTrue(model.syncWithCS2() >= 0, "the sync from the copy: " + model.getLastSyncError());
        }
        finally
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        }

        synchronized (logged)
        {
            return logged.stream().filter(l -> l.contains("the square at")).collect(Collectors.toList());
        }
    }

    /** The squares' protocols as the model holds them after the sync: as the copy saved them. */
    private static void assertNoSquareChanged()
    {
        assertEquals(String.valueOf(model.getLayout("Main Layout").getComponent(8, 1).getProtocol()), "DCC",
            "W 3's square, as the copy saved it");
        assertEquals(String.valueOf(model.getLayout("Main Layout").getComponent(5, 9).getProtocol()), "DCC",
            "W 15's square, as the copy saved it");
    }

    /** On a CS3: the square whose protocol the station does not have at its address, and the plan's hint. */
    @Test(timeOut = 120000)
    public void testACS3LogsTheMismatchAndThePlansHint() throws Exception
    {
        assertEquals(syncFromTheCopy(), Arrays.asList(MISMATCH, HINT), "the lines of a sync from the copy, on a CS3");
        assertNoSquareChanged();
    }

    /** On a CS2: the square whose protocol the station does not have at its address, and no plan asked for. */
    @Test(timeOut = 120000)
    public void testACS2LogsTheMismatchOnly() throws Exception
    {
        cs2 = true;

        assertEquals(syncFromTheCopy(), Collections.singletonList(MISMATCH), "the lines of a sync from the copy, on a CS2");
        assertEquals(station.requests("/app/api/gbs"), 0, "all requests: " + station.asked());
        assertNoSquareChanged();
    }
}
