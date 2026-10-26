package regression;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Edits the user threw away do not take their stations with them: experimenting with multi-select in the diagram editor
 * (selecting, dragging, deleting) and saving nothing once cost a whole page every station, locomotive placement, facing
 * and reversing flag.
 *
 * The editor works on the LIVE diagram objects the autonomy session holds, so mid-edit the session's idea of the layout
 * is the half-finished one, and reconciling deletes the settings of every square no longer there. Cancel reverts by
 * re-reading the pages into NEW objects, so the session keeps the discarded version. One path still reconciled anyway:
 * the save on the way out, the one moment nobody is watching and there is no way back.
 *
 * Both halves are tested: the first says the mechanism is real, the second that the save that runs on the way out does
 * not do it.
 */
public class testDiscardedEditsDoNotDeleteSetup
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();
    }

    /**
     * Reconciling against a half-edited diagram deletes the settings of everything the edit removed. The hazard itself,
     * pinned so it stays understood: not a bug in reconciling, but in choosing that moment.
     */
    @Test
    public void testReconcilingAgainstAnEditedPageDropsItsStations() throws Exception
    {
        List<LayoutDiagram> pages = freshPages();

        AutonomySession session = openOn(pages);

        String page = pages.get(0).getName();

        int before = stationsOn(session, pages, page);

        assertTrue(before > 0, "the first page must have stations, or this proves nothing");

        emptyInMemory(pages.get(0));

        session.save();

        assertEquals(stationsOn(session, freshPages(), page), 0,
            "reconciling against a page the editor had emptied should drop that page's settings - if "
            + "this ever stops being true, the test below is no longer testing anything");
    }

    /**
     * And the save that runs on the way out does not reconcile, so it cannot do that. Nothing is lost by waiting: the
     * next explicit Save reconciles against pages that are current, the only moment its report can be shown to anybody.
     */
    @Test
    public void testTheSaveOnTheWayOutKeepsThem() throws Exception
    {
        List<LayoutDiagram> pages = freshPages();

        AutonomySession session = openOn(pages);

        String page = pages.get(0).getName();

        int before = stationsOn(session, pages, page);

        assertTrue(before > 0, "the first page must have stations, or this proves nothing");

        emptyInMemory(pages.get(0));

        session.saveWithoutReconciling();

        assertEquals(stationsOn(session, freshPages(), page), before,
            "closing the application after an edit that was never saved - or was cancelled - deleted "
            + "every station on that page.  There is no undo for it: the setup is rewritten without "
            + "them, and the track coming back on the next start makes it look like the setup was "
            + "never there");
    }

    /**
     * A station with no signal paired to it is listed, and one that has a signal is not: a check cannot know which
     * platforms are SUPPOSED to be protected, but it can list the ones that are not. A notice rather than a warning:
     * running a station without a signal is ordinary, while a pairing that was set and then lost already has its own
     * warning.
     */
    @Test
    public void testAStationWithNoSignalIsNoticed() throws Exception
    {
        List<LayoutDiagram> pages = freshPages();

        AutonomySession session = openOn(pages);

        int noticed = 0;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.NO_SIGNAL_PAIRED
                .equals(finding.getMessageKey()))
            {
                noticed++;

                assertNull(session.getStore().getProtectingSignal(finding.getTile()),
                    "a station WITH a signal was listed as having none, which would make the list "
                    + "worse than not having it: every entry has to be worth looking at");
            }
        }

        assertTrue(noticed > 0,
            "the sample layout has stations without signals and none of them was listed - the check "
            + "is not running, or is not reaching the stations");
    }

    /** Takes every component off a page, in memory only, the way a select-all and delete would. */
    private static void emptyInMemory(LayoutDiagram page) throws Exception
    {
        for (LayoutDiagramComponent component : new LinkedList<>(page.getAll()))
        {
            page.addComponent((LayoutDiagramComponent) null, component.getX(), component.getY());
        }

        assertTrue(page.getAll().isEmpty(), "the page should now look empty to anything reading it");
    }

    /** How many squares of one page the setup calls stations, counted against a given set of pages. */
    private static int stationsOn(AutonomySession session, List<LayoutDiagram> against, String page)
    {
        int count = 0;

        for (LayoutDiagram diagram : against)
        {
            if (!page.equals(diagram.getName())) continue;

            for (LayoutDiagramComponent component : diagram.getAll())
            {
                if (component == null) continue;

                if (session.getStore().isStation(
                    new TileGraph.TileKey(page, component.getX(), component.getY())))
                {
                    count++;
                }
            }
        }

        return count;
    }

    /** The sample layout's pages, parsed again so each test starts with unedited ones. */
    private static List<LayoutDiagram> freshPages() throws Exception
    {
        File folder = findLayoutFolder();

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        return support.LayoutSandbox.wired(model, parser);
    }

    /**
     * A session over a throwaway copy of the setup, because both tests write and the sample layout is a fixture every
     * other test reads.
     */
    private static AutonomySession openOn(List<LayoutDiagram> pages) throws Exception
    {
        File temp = File.createTempFile("tc-setup", "");

        assertTrue(temp.delete(), "making room for a directory of the same name");
        assertTrue(new File(temp, "config/autonomy").mkdirs(), "could not make the copy");

        File from = new File(findLayoutFolder(), "config/autonomy");

        for (File one : from.listFiles())
        {
            if (one.isFile())
            {
                java.nio.file.Files.copy(one.toPath(),
                    new File(temp, "config/autonomy/" + one.getName()).toPath());
            }
        }

        temp.deleteOnExit();

        AutonomySession session = new AutonomySession(temp);

        assertTrue(session.isUsable(), "the sample layout ships a setup for this to open");

        session.open(pages);

        return session;
    }

    private static File findLayoutFolder()
    {
        File here = new File("test/test_layout");

        if (here.isDirectory()) return here;

        return new File(System.getProperty("user.dir"), "test/test_layout");
    }
}
