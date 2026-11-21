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
import org.testng.annotations.AfterMethod;
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

    /** What the stand-in serves at a path in place of the snapshot's file; a null body is a 404. */
    private static final java.util.Map<String, byte[]> instead =
        Collections.synchronizedMap(new java.util.HashMap<>());

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

        station = StandInStation.ofSnapshot(sandbox.getFolder(), (path, answer) ->
        {
            if (cs2 && "/config/geraet.vrs".equals(path))
            {
                return "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8);
            }

            if (cs2 && "/config/lokomotive.cs2".equals(path)) return "[lokomotive]\n".getBytes(StandardCharsets.UTF_8);

            return instead.containsKey(path) ? instead.get(path) : answer;
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

        // A sensor at (3,10) whose artikel gives address 10, DCC in the copy's own list, MM2 on the station; and a
        // switch at (3,11), address 777, which the station has no article for
        addElement(".id=0x40a03\n .typ=s88kontakt\n .artikel=20");
        addElement(".id=0x40b03\n .typ=rechtsweiche\n .prot=DCC\n .artikel=1554");

        File mags = new File(copy, "config/magnetartikel.cs2");
        String list = new String(Files.readAllBytes(mags.toPath()), StandardCharsets.UTF_8);
        Matcher w3 = Pattern.compile("(?ms)(^ \\.id=10\\r?$(?:(?!^artikel).)*?^ \\.dectyp=)mm2").matcher(list);

        assertTrue(w3.find(), "the copy's accessory list has address 10 as MM2");
        Files.write(mags.toPath(), (list.substring(0, w3.start()) + w3.group(1) + "dcc" + list.substring(w3.end()))
            .getBytes(StandardCharsets.UTF_8));
    }

    @AfterMethod(alwaysRun = true)
    public void servedAsItWas()
    {
        instead.clear();
    }

    /** Adds an element, given from its first key on, to the end of the copy's Main Layout page. */
    private static void addElement(String keys) throws Exception
    {
        File page = new File(copy, "config/gleisbilder/Main Layout.cs2");
        String text = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8);
        String nl = text.contains("\r\n") ? "\r\n" : "\n";

        Files.write(page.toPath(), (text + (text.endsWith("\n") ? "" : nl) + ("element\n " + keys + "\n").replace("\n", nl))
            .getBytes(StandardCharsets.UTF_8));
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
        return syncLines().stream().filter(l -> l.contains("the square at")).collect(Collectors.toList());
    }

    /** Syncs from the copy, which must succeed, and returns every line it logged. */
    private static List<String> syncLines() throws Exception
    {
        model.log("next sync " + System.nanoTime());
        logged.clear();
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
            return new ArrayList<>(logged);
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

    /**
     * A sensor square is not an accessory, so its protocol is not checked, though its artikel gives an address the
     * station has with another protocol.
     *
     * MUTATION: drop `!c.isAccessory()` from the filter in `CS2File.logSquaresTheStationDisagreesWith`, and (3,10) is
     * logged.
     */
    @Test(timeOut = 120000)
    public void testASensorSquareIsNotChecked() throws Exception
    {
        List<String> lines = syncFromTheCopy();
        org.traincontrol.base.LayoutDiagramComponent sensor = model.getLayout("Main Layout").getComponent(3, 10);

        assertTrue(sensor.isFeedback() && sensor.getAddress() == 10 && "DCC".equals(String.valueOf(sensor.getProtocol())),
            "precondition: (3,10) is a sensor at address 10, DCC by the copy's list");
        assertFalse(lines.stream().anyMatch(l -> l.contains("square at 3,10 ")), "a sensor square was logged: " + lines);
    }

    /**
     * A square at an address the station has no article for is not logged: there is no protocol there to disagree with.
     *
     * MUTATION: drop `!has.isEmpty()` from `CS2File.logSquaresTheStationDisagreesWith`, and (3,11) is logged.
     */
    @Test(timeOut = 120000)
    public void testASquareAtAnAddressTheStationLacksIsNotLogged() throws Exception
    {
        List<String> lines = syncFromTheCopy();
        org.traincontrol.base.LayoutDiagramComponent square = model.getLayout("Main Layout").getComponent(3, 11);

        assertTrue(square.isAccessory() && square.getAddress() == 777, "precondition: (3,11) is a switch at address 777");
        assertFalse(lines.stream().anyMatch(l -> l.contains("square at 3,11 ")),
            "a square at an address the station has no article for was logged: " + lines);
    }

    /**
     * A check that cannot run, its plan not found or unreadable or the station's accessory list not found, logs nothing
     * outside debug, and the sync succeeds.
     *
     * MUTATION: log the check's failure outside debug too (`MarklinControlStation.syncThroughTheNewParser`), and the
     * missing accessory list adds a line.
     */
    @Test(timeOut = 240000)
    public void testACheckThatCannotRunIsSilentAndTheSyncSucceeds() throws Exception
    {
        // Compared with a second healthy sync, as the first from the copy also logs the accessories it adds
        syncLines();
        List<String> healthy = withoutTheSquaresLines(syncLines());
        String[][] failures = { { "/app/api/gbs", null }, { "/app/api/gbs", "not a plan" },
            { "/config/magnetartikel.cs2", null } };

        for (String[] failure : failures)
        {
            instead.clear();
            instead.put(failure[0], failure[1] == null ? null : failure[1].getBytes(StandardCharsets.UTF_8));

            assertEquals(withoutTheSquaresLines(syncLines()), healthy,
                "with " + failure[0] + " " + (failure[1] == null ? "not found" : "unreadable") + ", what the sync logged, against " + healthy);
        }
    }

    private static List<String> withoutTheSquaresLines(List<String> lines)
    {
        return lines.stream().filter(l -> !l.contains("the square at")).collect(Collectors.toList());
    }
}
