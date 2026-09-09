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
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.StationCaption;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * MT-337, second half - a station named twice draws its home locomotive twice.
 *
 * Adam, 2026-09-08: *"if a station label is offset from the station, the home label is duplicated on
 * the station's square, and on the one with the label"*, and again: *"if a station has an offset
 * label, the current home loc is shown twice (once on the tile itself, once on the offset label)"*.
 *
 * **A station can carry more than one caption, and that is deliberate.** `AutonomyCompanionStore.
 * setCaption` says so - "several squares may name the same station, which is deliberate: a long
 * platform is legitimately labelled at both ends" - and the state is on Adam's own railway today:
 * `1 - Main:6,4` is captioned on `6,5` AND on `6,4`. `AutonomySession.migrateStationLabels` writes
 * captions through the raw store door, so a station already captioned on its own square gains a
 * second one the moment a `Point:` label is migrated off the page beside it.
 *
 * `LayoutGrid` draws a caption per CAPTION SQUARE and asks `autonomyHomeAt` about the station each one
 * names, so a station named twice states its home twice - side by side, on the tile and beside it.
 *
 * **The rule Adam asked for**: the home appears once, on the caption square when there is one, and
 * otherwise on the station's own square. So the self-caption is the one that gives way, and only when
 * something else is already naming that station. Two captions at opposite ends of a long platform are
 * untouched, which is the case the store's own comment exists for.
 *
 * Asserted by DRAWING it: the editor is built, its captions are read off the container, and the home
 * name is counted. The two rules that could be got wrong instead of right - suppressing every
 * self-caption, or suppressing the offset one - are what the other two tests here are.
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
     * The station OB-193 is about, which the frozen railway carries already captioned twice.
     *
     * Adam, on MT-293: *"At the time of testing, TopMainR2 still shows two labels."*
     * `live-snapshot`'s setup captions `5:6,4` on `5:6,4` AND on `5:6,5` - the state
     * `migrateStationLabels` can produce without anybody asking for it - and `1 - Main:6,4` is
     * TopMainR2.  It is the very square `TrainControlUI.autonomyCaptionAt`'s own comment names.
     *
     * **Held out of the arrangement below**, so the three stations this class builds its cases on can
     * never be this one.  Two reasons, and the second is the one that matters: a square already
     * doubly captioned would be captioned a THIRD time by the arrangement, and the doubled case's own
     * precondition asserts a count of two - so the class would fail for a reason that has nothing to
     * do with the rule.
     */
    private static final TileKey OB193 = new TileKey(PAGE, 6, 4);

    /** Captioned on its own square AND on a neighbour - the defect */
    private static TileKey doubled;

    /** The neighbour that names `doubled` */
    private static TileKey doubledOffset;

    /** Captioned on its own square only - the control against suppressing every self-caption */
    private static TileKey selfOnly;

    /** Captioned on a neighbour only - the control against suppressing the offset one */
    private static TileKey offsetOnly;

    private static TileKey offsetOnlyCaption;

    /** The locomotive homed at each of the three, so the three captions cannot be confused */
    private static String doubledHome;
    private static String selfHome;
    private static String offsetHome;

    /** Every caption the editor actually drew, as text */
    private static List<String> drawn;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("reading what the editor drew needs a display");
        }

        // The operator's own railway, COPIED - it is the one that carries a doubly captioned station,
        // and it is never written to in place.
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // The SHAPE is what this class is about, and the shape is the same in both.  Where his trains
        // are standing, how long they are and which side they came in by are not, and reading those
        // off the live folder is how `testTheLengthGuardsOnTheRealLayout` came to assert that the
        // 2-8-4 stood at BottomMainB - it is at BottomMainA now, and that class was red for a reason
        // that had nothing to do with any guard.  A fixture that moves while nobody is looking makes
        // every class over it say something different every week.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration to draw captions from");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        // THREE STATIONS, ARRANGED HERE rather than found in the file.
        //
        // The doubled caption exists on Adam's railway, but a test that depends on which square it is
        // on is a test that breaks the next time he moves a label. Every state below is built from
        // whatever self-captioned stations the page happens to have.
        List<TileKey> selfCaptioned = new ArrayList<>();

        for (Map.Entry<TileKey, TileKey> caption : session.getCaptions().entrySet())
        {
            if (!PAGE.equals(caption.getKey().getPage())) continue;

            // NOT the square OB-193 is about - see the field.  It is captioned twice already, so
            // arranging on it would produce a third caption and fail this class's own precondition.
            if (OB193.equals(caption.getKey())) continue;

            if (caption.getKey().equals(caption.getValue())
                && freeNeighbour(page, caption.getKey()) != null)
            {
                selfCaptioned.add(caption.getKey());
            }
        }

        if (selfCaptioned.size() < 3)
        {
            throw new SkipException("this layout has fewer than three self-captioned stations with "
                + "room beside them - found " + selfCaptioned.size());
        }

        List<String> locomotives = model.getLocList();

        if (locomotives.size() < 3) throw new SkipException("fewer than three locomotives");

        doubled = selfCaptioned.get(0);
        selfOnly = selfCaptioned.get(1);
        offsetOnly = selfCaptioned.get(2);

        doubledOffset = freeNeighbour(page, doubled);
        offsetOnlyCaption = freeNeighbour(page, offsetOnly);

        // A SECOND caption through the raw store door, which is how the state arises: nothing on the
        // session's own setCaption can produce it, and migrateStationLabels does not go through that.
        session.getStore().setCaption(doubledOffset, doubled);

        // And the third station's caption MOVED, which does sweep - so it is captioned only beside
        // itself.
        session.moveCaption(offsetOnly, offsetOnlyCaption);

        doubledHome = locomotives.get(0);
        selfHome = locomotives.get(1);
        offsetHome = locomotives.get(2);

        session.setHome(doubled, doubledHome);
        session.setHome(selfOnly, selfHome);
        session.setHome(offsetOnly, offsetHome);

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

    /**
     * The defect: one station, two captions, one home written on the diagram twice.
     */
    @Test
    public void testAStationNamedTwiceDrawsItsHomeOnce()
    {
        assertEquals(session.captionsFor(doubled).size(), 2,
            "precondition: " + doubled + " is meant to be captioned on its own square and beside it, "
            + "and it is captioned on " + session.captionsFor(doubled));

        assertEquals(times(caption(doubledHome)), 1,
            "the home locomotive of " + doubled + " is drawn " + times(caption(doubledHome))
            + " times on the diagram - once on the station's own square and once on the offset "
            + "label, which is what Adam reported");
    }

    /**
     * And the one that survives is the offset label, not the square underneath.
     */
    @Test
    public void testTheCaptionThatSurvivesIsTheOffsetOne()
    {
        assertEquals(ui.autonomyCaptionAt(doubledOffset), doubled,
            "the offset label stopped naming its station, so the caption moved onto the track "
            + "instead of staying where it was put");

        assertNull(ui.autonomyCaptionAt(doubled),
            "the station's own square is still drawing a caption of its own while another square is "
            + "already naming it");
    }

    /**
     * CONTROL - a station named only on its own square keeps that name.
     *
     * The obvious wrong fix is "a station never captions itself", which would take the caption off
     * every station on a railway whose labels have never been moved.
     */
    @Test
    public void testAStationNamedOnlyOnItsOwnSquareKeepsIt()
    {
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
     * CONTROL - a station named only beside itself keeps that name.
     *
     * The other wrong fix is to suppress the OFFSET caption and keep the square's own, which would
     * drag every label somebody has placed back onto the track it was moved off.
     */
    @Test
    public void testAStationNamedOnlyBesideItselfKeepsIt()
    {
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
     * OB-193: TopMainR2, which really is captioned twice on this railway, draws ONE label.
     *
     * Adam, on MT-293 (2026-09-08): *"This works. At the time of testing, TopMainR2 still shows two
     * labels, but this is still pending being worked."*  He ruled it fixed on 2026-09-09, and this is
     * the check rather than the belief.
     *
     * **Every other claim in this class arranges its own state**, deliberately - a test that depends
     * on which square Adam's doubled caption is on breaks the next time he moves a label.  This one is
     * the exception and can be, because `live-snapshot` is frozen: it is taken from `git show HEAD:`
     * rather than from the working tree, so the caption pair below cannot move under it.  What it
     * costs is that it pins the FIXTURE as well as the rule, which is why it says so here.
     *
     * The state is not contrived: `5:6,4` is captioned on `5:6,4` and on `5:6,5` in the snapshot's
     * `setup.json`, and `AutonomySession.migrateStationLabels` is how it got there - it writes through
     * the raw store door, which does not sweep, so a station already captioned on its own square gains
     * a second caption the moment a legacy `Point:` label is migrated off the page beside it.
     *
     * If this ever fails, either the fix at `TrainControlUI.autonomyCaptionAt` has been lost or the
     * snapshot has been re-taken from a railway whose labels have moved.  The precondition tells the
     * two apart before the claim runs.
     */
    @Test
    public void testTopMainR2DrawsOneLabelOnTheFrozenRailway()
    {
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
     * The caption text the diagram writes for a home locomotive.
     *
     * Through the production helper, because a caption is cut to length: a name asserted raw would
     * pass for the short names and fail for the long ones.
     *
     * @param locomotive the home locomotive's name
     * @return what the label says
     */
    private static String caption(String locomotive)
    {
        return LayoutGrid.stationCaption(locomotive, "");
    }

    /**
     * How many captions on the drawn diagram say exactly this.
     *
     * @param text the caption
     * @return the count
     */
    private static int times(String text)
    {
        int found = 0;

        for (String was : drawn)
        {
            if (text.equals(was)) found++;
        }

        return found;
    }

    /**
     * A square beside this one that is inside the page, carries nothing, and has no caption on it.
     *
     * @param page the diagram
     * @param tile the station
     * @return somewhere its name could go, or null
     */
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

    /**
     * Every caption the container holds, in the order they were built.
     */
    private static void collect(java.awt.Container container, List<String> into)
    {
        for (java.awt.Component child : container.getComponents())
        {
            // StationCaption only: the window's own labels - Page, Text Labels, the findings line -
            // are ordinary JLabels, and counting those would make this a test about the sidebar.
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
