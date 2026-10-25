package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.widgets.StationCaption;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * MT-337, second half: a station named twice draws its home locomotive twice.
 *
 * Adam: "if a station label is offset from the station, the home label is duplicated on the station's square, and on
 * the one with the label".  The editor keeps one station to one caption, but the store can hold more (a setup edited by
 * hand or written by an older version).  A station named on its own square and beside it is drawn once and is no error
 * for the setup check; only two captions beside a station on one page are an ERROR (OB-341); a caption on another page
 * is allowed (Adam: "It is OK to show on another page what is going on on another.").
 *
 * The rule: the home appears once, on the caption square when there is one, and otherwise on the station's own square,
 * so the self-caption gives way only when something else already names that station.  Asserted by DRAWING it: the
 * editor is built, its captions are read off the container, and the home name is counted.
 *
 * MUTATION: drop the `captionsFor` clause from `TrainControlUI.autonomyCaptionAt` and
 * `testAStationNamedTwiceDrawsItsHomeOnce` fails with "drawn 2 times". Suppress the self-caption
 * unconditionally and `testAStationNamedOnlyOnItsOwnSquareKeepsIt` fails; suppress the offset one
 * instead and `testAStationNamedOnlyBesideItselfKeepsIt` fails.
 *
 * @author Adam
 */
public class testTheHomeLabelIsDrawnOnce
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static support.LayoutSandbox sandbox;
    private static AutonomySession session;
    private static LayoutEditor editor;

    /** The page everything below is arranged on */
    private static final String PAGE = "1 - Main";

    /**
     * The station OB-193 is about, which the frozen railway carries already captioned twice (`5:6,4` on `5:6,4` AND on
     * `5:6,5`; `1 - Main:6,4` is TopMainR2).  Held out of the arrangement below, which would caption it a THIRD time
     * and fail the doubled case's own precondition of two.
     */
    private static final TileKey OB193 = new TileKey(PAGE, 6, 4);

    /** Captioned on its own square AND on a neighbor - the defect */
    private static TileKey doubled;

    /** The neighbor that names `doubled` */
    private static TileKey doubledOffset;

    /** Captioned on its own square only - the control against suppressing every self-caption */
    private static TileKey selfOnly;

    /** Captioned on a neighbor only - the control against suppressing the offset one */
    private static TileKey offsetOnly;

    private static TileKey offsetOnlyCaption;

    /**
     * Captioned on two neighbors and not on its own square - drawn TWICE, which is the one shape here the setup check
     * calls an error (OB-341).  Null where the railway has no fourth station with two free squares beside it.
     */
    private static TileKey twoBeside;

    /** The two squares that name `twoBeside` */
    private static TileKey twoBesideFirst;
    private static TileKey twoBesideSecond;

    /** The locomotive homed at `twoBeside` */
    private static String twoBesideHome;

    /** The locomotive homed at each of the three, so the three captions cannot be confused */
    private static String doubledHome;
    private static String selfHome;
    private static String offsetHome;

    /** Every caption the editor actually drew, as text */
    private static List<String> drawn;

    /** Why the set-up found no fixture, or null; each claim asks it, so it fails rather than skips. */
    private static String unbuilt;

    /** Fails the claim when the set-up found no fixture: a skip or an assert in `setUpClass` reads as skipped tests. */
    private static void assertBuilt()
    {
        if (unbuilt != null) throw new AssertionError(unbuilt);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("reading what the editor drew needs a display");
        }

        // The operator's own railway, COPIED: the frozen `live-snapshot` (OB-111), never written in place.  It carries
        // a doubly captioned station, and reading the live folder made a sibling class red when his trains moved.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            unbuilt = "no autonomy configuration to draw captions from";
            return;
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null)
        {
            unbuilt = "no page " + PAGE;
            return;
        }

        // THREE STATIONS, ARRANGED HERE rather than found in the file: a test that depends on which square the doubled
        // caption is on breaks the next time Adam moves a label.
        List<TileKey> selfCaptioned = new ArrayList<>();

        for (Map.Entry<TileKey, TileKey> caption : session.getCaptions().entrySet())
        {
            if (!PAGE.equals(caption.getKey().getPage())) continue;

            // NOT the square OB-193 is about: it is captioned twice already, so arranging on it would add a third.
            if (OB193.equals(caption.getKey())) continue;

            if (caption.getKey().equals(caption.getValue())
                && freeNeighbour(page, caption.getKey()) != null)
            {
                selfCaptioned.add(caption.getKey());
            }
        }

        if (selfCaptioned.size() < 3)
        {
            unbuilt = "this layout has fewer than three self-captioned stations with "
                + "room beside them - found " + selfCaptioned.size();
            return;
        }

        List<String> locomotives = model.getLocList();

        if (locomotives.size() < 3)
        {
            unbuilt = "fewer than three locomotives";
            return;
        }

        doubled = selfCaptioned.get(0);
        selfOnly = selfCaptioned.get(1);
        offsetOnly = selfCaptioned.get(2);

        doubledOffset = freeNeighbour(page, doubled);
        offsetOnlyCaption = freeNeighbour(page, offsetOnly);

        // A SECOND caption through the raw store door, which is how the state arises: nothing on the session's own
        // setCaption can produce it.
        session.getStore().setCaption(doubledOffset, doubled);

        // And the third station's caption MOVED, which does sweep, so it is captioned only beside itself.
        session.moveCaption(offsetOnly, offsetOnlyCaption);

        doubledHome = locomotives.get(0);
        selfHome = locomotives.get(1);
        offsetHome = locomotives.get(2);

        session.setHome(doubled, doubledHome);
        session.setHome(selfOnly, selfHome);
        session.setHome(offsetOnly, offsetHome);

        // A FOURTH, if the railway has one, captioned on two neighbors: moved off its own square (which sweeps), and a
        // second put beside it through the raw store door.  Everything above is arranged first, so the squares it takes
        // are free ones.
        for (int at = 3; at < selfCaptioned.size() && twoBeside == null && locomotives.size() > 3; at++)
        {
            TileKey candidate = selfCaptioned.get(at);

            // One beside it, and one on any other blank square of the page: a caption may sit on blank space anywhere
            TileKey first = freeNeighbour(page, candidate);
            TileKey second = first == null ? null : aBlankSquare(page, first);

            if (second == null) continue;

            session.moveCaption(candidate, first);
            session.getStore().setCaption(second, candidate);

            twoBeside = candidate;
            twoBesideFirst = first;
            twoBesideSecond = second;
            twoBesideHome = locomotives.get(3);

            session.setHome(twoBeside, twoBesideHome);
        }

        final LayoutEditor[] built = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
            built[0].getAutonomyPanel().getShowHomeLocomotives().setSelected(true);
        });

        editor = built[0];

        settle();

        drawn = new ArrayList<>();

        javax.swing.SwingUtilities.invokeAndWait(() -> collect(editor.getContentPane(), drawn));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (editor != null)
        {
            final LayoutEditor closing = editor;

            javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        if (ui != null)
        {
            final TrainControlUI closing = ui;

            javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /** The defect: one station, two captions, one home written on the diagram twice. */
    @Test
    public void testAStationNamedTwiceDrawsItsHomeOnce()
    {
        assertBuilt();

        assertEquals(session.captionsFor(doubled).size(), 2,
            "precondition: " + doubled + " is meant to be captioned on its own square and beside it, "
            + "and it is captioned on " + session.captionsFor(doubled));

        assertEquals(times(caption(doubledHome)), 1,
            "the home locomotive of " + doubled + " is drawn " + times(caption(doubledHome))
            + " times on the diagram - once on the station's own square and once on the offset "
            + "label, which is what Adam reported");
    }

    /** And the one that survives is the offset label, not the square underneath. */
    @Test
    public void testTheCaptionThatSurvivesIsTheOffsetOne()
    {
        assertBuilt();

        assertEquals(ui.autonomyCaptionAt(doubledOffset), doubled,
            "the offset label stopped naming its station, so the caption moved onto the track "
            + "instead of staying where it was put");

        assertNull(ui.autonomyCaptionAt(doubled),
            "the station's own square is still drawing a caption of its own while another square is "
            + "already naming it");
    }

    /**
     * CONTROL: a station named only on its own square keeps that name; "never caption itself" would take every unmoved
     * label.
     */
    @Test
    public void testAStationNamedOnlyOnItsOwnSquareKeepsIt()
    {
        assertBuilt();

        assertEquals(session.captionsFor(selfOnly).size(), 1,
            "precondition: " + selfOnly + " is captioned on " + session.captionsFor(selfOnly));

        assertEquals(ui.autonomyCaptionAt(selfOnly), selfOnly,
            "a station captioned only on its own square lost its caption");

        assertEquals(times(caption(selfHome)), 1,
            "the home locomotive of " + selfOnly + " is drawn " + times(caption(selfHome))
            + " times, and it should be drawn once - on the station's own square, which is the only "
            + "square naming it");
    }

    /**
     * CONTROL: a station named only beside itself keeps that name; suppressing the offset caption would drag labels
     * back onto the track.
     */
    @Test
    public void testAStationNamedOnlyBesideItselfKeepsIt()
    {
        assertBuilt();

        assertEquals(session.captionsFor(offsetOnly).size(), 1,
            "precondition: " + offsetOnly + " is captioned on " + session.captionsFor(offsetOnly));

        assertEquals(ui.autonomyCaptionAt(offsetOnlyCaption), offsetOnly,
            "the offset label stopped naming its station");

        assertNull(ui.autonomyCaptionAt(offsetOnly),
            "the station's own square started drawing a caption nobody asked it for");

        assertEquals(times(caption(offsetHome)), 1,
            "the home locomotive of " + offsetOnly + " is drawn " + times(caption(offsetHome))
            + " times, and it should be drawn once - on the square beside it");
    }

    /**
     * OB-193 (MT-293): TopMainR2, which really is captioned twice on this railway, draws ONE label.  The other claims arrange
     * their own state; this one pins the FIXTURE as well as the rule, which is safe because `live-snapshot` is frozen.
     * If it fails, either the fix at `TrainControlUI.autonomyCaptionAt` was lost or the snapshot was re-taken from a
     * railway whose labels moved; the precondition tells the two apart.
     */
    @Test
    public void testTopMainR2DrawsOneLabelOnTheFrozenRailway()
    {
        assertBuilt();

        assertEquals(session.getStore().getPointName(OB193), "TopMainR2",
            "1 - Main:6,4 is called " + session.getStore().getPointName(OB193) + " on this snapshot"
            + " rather than TopMainR2, so the square OB-193 was reported about is somewhere else and"
            + " this claim is about the wrong one");

        assertEquals(session.captionsFor(OB193).size(), 2,
            "precondition: TopMainR2 is meant to be captioned on its own square AND beside it - that"
            + " doubling is what Adam was looking at - and this snapshot captions it on "
            + session.captionsFor(OB193) + ". Nothing below is being tested");

        assertNull(ui.autonomyCaptionAt(OB193),
            "TopMainR2 is still drawing a caption on its own square while 1 - Main:6,5 is already"
            + " naming it, so the two labels Adam reported are both there (OB-193)");

        assertEquals(ui.autonomyCaptionAt(new TileKey(PAGE, 6, 5)), OB193,
            "the label beside TopMainR2 stopped naming it, so the ONE label that survives is not the"
            + " one somebody placed - and with the self-caption suppressed as well the station would"
            + " have no name at all");
    }

    /**
     * The setup check calls a station an error exactly when the diagram draws its name twice (OB-341).  Measured on the
     * drawn editor, not on the rule: each home locomotive is written once for every place its name is drawn, so
     * counting it off the diagram says how many places there are.  The check reports the station drawn twice and none
     * of the others.
     *
     * MUTATION: count the station's own square as a place, or let it be drawn beside a caption it should give way to,
     * and this fails.
     */
    @Test
    public void testTheSetupCheckCountsWhatTheDiagramDraws()
    {
        assertBuilt();

        if (twoBeside == null)
        {
            throw new AssertionError("this railway has no fourth self-captioned station with two free squares beside it");
        }

        // WHAT THE GRID SHOWS
        assertEquals(times(caption(doubledHome)), 1, "the station on its own square and beside it is not drawn once");
        assertEquals(times(caption(selfHome)), 1, "the station on its own square only is not drawn once");
        assertEquals(times(caption(offsetHome)), 1, "the station beside itself only is not drawn once");
        assertEquals(times(caption(twoBesideHome)), 2, "the station beside itself in two places is not drawn twice: "
            + session.captionsFor(twoBeside));

        // WHAT THE CHECK SAYS: the station drawn twice, once, and nothing else
        List<org.traincontrol.automationui.AutonomyChecks.Finding> reported = new ArrayList<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.STATION_NAMED_TWICE.equals(finding.getMessageKey()))
            {
                reported.add(finding);
            }
        }

        assertEquals(reported.size(), 1, "the railway has one station drawn twice, and the check reports: " + reported);

        assertEquals(reported.get(0).getAbout(), twoBeside, "the check reports the wrong station");
        assertEquals(reported.get(0).getCount(), 2, "the check does not count two places");

        assertEquals(new java.util.LinkedHashSet<>(reported.get(0).getRelated()),
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(twoBesideFirst, twoBesideSecond)),
            "the check does not name the two squares the name is drawn on");

        // and what the setup says is drawn is what the check counted, for the stations drawn once
        for (TileKey station : new TileKey[] {doubled, selfOnly, offsetOnly, OB193})
        {
            assertEquals(session.drawnCaptionsFor(station).size(), 1,
                station + " is drawn " + session.drawnCaptionsFor(station) + ", and it should be drawn once");
        }
    }

    /**
     * The caption text the diagram writes for a home locomotive, through the production helper since a caption is cut
     * to length.
     */
    private static String caption(String locomotive)
    {
        return LayoutGrid.stationCaption(locomotive, "");
    }

    /** How many captions on the drawn diagram say exactly this. */
    private static int times(String text)
    {
        int found = 0;

        for (String was : drawn)
        {
            if (text.equals(was)) found++;
        }

        return found;
    }

    /** A square beside this one that is inside the page, carries nothing, and has no caption on it, or null. */
    private static TileKey freeNeighbour(LayoutDiagram page, TileKey tile)
    {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] step : around)
        {
            int x = tile.getX() + step[0];
            int y = tile.getY() + step[1];

            if (x < page.getMinx() || x > page.getMaxx()) continue;
            if (y < page.getMiny() || y > page.getMaxy()) continue;

            if (page.getComponent(x, y) != null) continue;

            TileKey at = new TileKey(tile.getPage(), x, y);

            if (session.getCaptionTarget(at) != null) continue;

            return at;
        }

        return null;
    }

    /** The first square of the page, in reading order, that carries nothing and has no caption on it, or null. */
    private static TileKey aBlankSquare(LayoutDiagram page, TileKey not)
    {
        for (int y = page.getMiny(); y <= page.getMaxy(); y++)
        {
            for (int x = page.getMinx(); x <= page.getMaxx(); x++)
            {
                if (page.getComponent(x, y) != null) continue;

                TileKey at = new TileKey(not.getPage(), x, y);

                if (at.equals(not) || session.getCaptionTarget(at) != null) continue;

                return at;
            }
        }

        return null;
    }

    /** Every caption the container holds, in the order they were built. */
    private static void collect(java.awt.Container container, List<String> into)
    {
        for (java.awt.Component child : container.getComponents())
        {
            // StationCaption only: the window's other labels are ordinary JLabels, and counting those would test the
            // sidebar.
            if (child instanceof StationCaption)
            {
                String text = ((StationCaption) child).getText();

                if (text != null && !text.trim().isEmpty()) into.add(text);
            }

            if (child instanceof java.awt.Container) collect((java.awt.Container) child, into);
        }
    }

    private static void settle() throws Exception
    {
        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }
}
