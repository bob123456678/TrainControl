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
 * Edits the user threw away do not take their stations with them.
 *
 * Found the only way it could be: Adam experimented with the new multi-select in the diagram editor -
 * selecting, dragging, deleting - saved nothing, and afterwards a whole page of his railway had lost
 * every station, locomotive placement, facing and reversing flag it had.
 *
 * The mechanism is written down in this codebase already, in the javadoc of the method that exists to
 * avoid it. The editor works on the LIVE diagram objects the autonomy session is holding, so while an
 * edit is in progress the session's idea of the layout is the half-finished one. Reconciling compares
 * the setup against those pages and deletes the settings of every square that is no longer there - and
 * a square the user emptied and then thought better of is, at that moment, no longer there. Cancel
 * makes it worse rather than better: it reverts by re-reading the pages from disk into NEW objects,
 * so the session is left holding the discarded version for as long as it lives.
 *
 * What was missing is that one path still reconciled anyway: the save on the way out. Closing the
 * application ran a full reconciliation against whatever the editor had last left in memory, which is
 * the one moment nobody is watching and the one moment there is no way back.
 *
 * Both halves are tested here. The first says the mechanism is real, so nobody has to take the
 * paragraph above on faith; the second says the save that runs on the way out does not do it.
 */
public class testDiscardedEditsDoNotDeleteSetup
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * Reconciling against a half-edited diagram deletes the settings of everything the edit removed.
     *
     * This is the hazard itself, pinned so that it stays understood. It is not a bug in reconciling -
     * tidying away the settings of squares that are genuinely gone is exactly what it is for. It is a
     * bug in choosing that moment.
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
     * And the save that runs on the way out does not reconcile, so it cannot do that.
     *
     * The same setup as above, saved the way closing the application saves it. Nothing is lost by
     * waiting: reconciliation tidies a setup whose diagram has really changed, and the next explicit
     * Save does it against pages that are current - which is also the only moment its report can be
     * shown to anybody.
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
     * A station with no signal paired to it is listed, and one that has a signal is not.
     *
     * Adam accepted that signal pairings can only be audited one station at a time, and asked for this
     * as the half of the audit a check can do on its own: it cannot know which platforms are SUPPOSED
     * to be protected, but it can list the ones that are not.
     *
     * A notice rather than a warning. Running a station without a signal is an ordinary way to build a
     * railway; what is not ordinary is a pairing that was set and then lost, and that already has its
     * own warning. From the outside those two looked identical - both silent.
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

    /**
     * Takes every component off a page, in memory only, the way a select-all and delete would.
     */
    private static void emptyInMemory(LayoutDiagram page) throws Exception
    {
        for (LayoutDiagramComponent component : new LinkedList<>(page.getAll()))
        {
            page.addComponent((LayoutDiagramComponent) null, component.getX(), component.getY());
        }

        assertTrue(page.getAll().isEmpty(), "the page should now look empty to anything reading it");
    }

    /**
     * How many squares of one page the setup calls stations, counted against a given set of pages.
     */
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

    /**
     * The sample layout's pages, parsed again so each test starts with unedited ones.
     */
    private static List<LayoutDiagram> freshPages() throws Exception
    {
        File folder = findLayoutFolder();

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        return parser.parseLayout(new LinkedList<MarklinAccessory>());
    }

    /**
     * A session over a throwaway copy of the setup.
     *
     * A copy because both tests write, and the sample layout in the repository is a fixture every
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
        File here = new File("test_layout");

        if (here.isDirectory()) return here;

        return new File(System.getProperty("user.dir"), "test_layout");
    }
}
