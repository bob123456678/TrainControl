package regression;

import java.util.Collections;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A destination turn nobody has managed to write down yet survives a setup rebuild.
 *
 * **D3-C5, and it is the `new-field-needs-the-copy-constructor` shape.**  `Layout.reversedOnArrival`
 * holds the reversals the railway performed at a destination and has not yet drained into the setup.
 * It lives on the `Layout` object, and `rebuildRunningLayoutFromSetup` replaces that object wholesale.
 * Placements were taught to survive that (OB-183), and `arrivedFrom` was added to the carry with them
 * - `reversedOnArrival` was not, so every pending turn died on any setup gesture at all: a home set
 * from the diagram's right-click menu, a direction, a caption.
 *
 * **Why that matters is the RETRY, not the happy path.**  RGD-C7 went to some trouble to make a turn
 * that could not be written survive - *"NOT WRITTEN, SO NOT FORGOTTEN"* - because a turn destroyed
 * mid-drain never self-heals: there is nothing left to try again with.  A rebuild between the turn and
 * a successful write destroyed exactly those retries, and the next dispatch is then offered paths for
 * the wrong heading, which is the symptom of OB-189 arriving through a third door.
 * `Layout.restoreReversalsOnArrival`'s own javadoc named the hole rather than closing it: *"not a cure
 * for a configuration RELOAD between the turn and the write - that swaps this object entirely and the
 * pending records go with it.  That one needs the record to outlive the layout."*
 *
 * **THE REBUILD IS RUN, NOT MODELLED.**  Its siblings assert about `putTheTrainsBack` over a
 * hand-built `Layout`, and one of them reproduces the rebuild by calling `parseAuto` directly.  Both
 * are right about the rule and neither can see this defect, because the defect is that the CALL SITE
 * does not carry something - `extracted-rule-moves-the-bug-to-the-call`.  So this opens a real window
 * on the frozen railway and calls `rebuildRunningLayoutFromSetup` itself.
 *
 * **AND IT CANNOT PASS VACUOUSLY.**  The rebuild is guarded by three conditions, any of which makes it
 * a no-op - no active configuration, autonomy busy, no viewer panel - and a no-op rebuild replaces no
 * Layout, so nothing would have had to survive anything.  The `assertNotSame` below is what says the
 * rebuild really happened; without it this class would go green the day the guard changed.
 *
 * MUTATION: take the `takeThePendingTurns()` / `putThePendingTurnsBack(...)` pair out of
 * `TrainControlUI.rebuildRunningLayoutFromSetup` and this fails, with the record gone.
 *
 * @author Adam
 */
public class testAPendingTurnSurvivesTheRebuild
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    /**
     * A record the drain can never write, which is the state this defect is about.
     *
     * `AutonomySession.faceTheWayItCameIn` answers null for a Point that is not there, so this is put
     * back at every idle refresh and never consumed - which is precisely the retry RGD-C7 preserved
     * the map for, and precisely what a rebuild used to destroy.  A record that COULD be written would
     * make the test a race between the rebuild and the next refresh.
     */
    private static final String PENDING_LOC = "D3C5 nobody";

    private static final String PENDING_POINT = "D3C5 no such point";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the rebuild is a method on the window");
        }

        // THE FROZEN COPY, never the operator's own railway.  `Scenario.folderFor` is the only naming
        // of a fixture folder that cannot spell its way out to `cs2_sample_layout`, and the sandbox is
        // opened BEFORE `init`, which reads the layout preference (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so the rebuild this class is about cannot be reached");

        // The window has to have finished starting: `init` posts `display()` to the event thread and
        // returns without waiting for it.
        pump();
        pump();

        session = ui.getAutonomySession();

        assertNotNull(session, "the snapshot holds no autonomy setup, so there is nothing to rebuild");

        model.parseAuto(session.buildConfiguration());

        assertNotNull(model.getAutoLayout(), "the snapshot did not build to a layout");

        // THE CONFIGURATION THE REBUILD LOADS, which its first guard reads.  Set here where it is
        // missing rather than left to chance: a null makes the whole method a no-op, and a test whose
        // subject silently does nothing is the failure mode this repository keeps finding.
        if (ui.getActiveDiagramConfiguration() == null)
        {
            String active = session.getStore().getActiveConfiguration();

            assertNotNull(active, "the snapshot names no active configuration");

            java.lang.reflect.Field field =
                TrainControlUI.class.getDeclaredField("activeDiagramConfiguration");

            field.setAccessible(true);

            field.set(ui, active);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null && model.hasAutoLayout()) model.getAutoLayout().stopLocomotives();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The turn the railway owes the setup is still owed after the setup is rebuilt.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testATurnNobodyHasWrittenYetSurvivesTheRebuild() throws Exception
    {
        Layout before = model.getAutoLayout();

        assertNotNull(before, "there is no running layout to record a turn on");

        // Whatever the snapshot's own start-up may owe, put back at the end so this class leaves the
        // railway as it found it.
        Map<String, String> owed = before.takeReversalsOnArrival();

        try
        {
            before.restoreReversalsOnArrival(
                Collections.singletonMap(PENDING_LOC, PENDING_POINT));

            // THE PRECONDITION, MEASURED: the drain declines this one and puts it back.  If it could
            // be written, the assertion at the end would be a race between the rebuild and whichever
            // refresh got there first, and a green result would mean nothing.
            ui.reconcileFacingWhenIdle();

            pump();

            Map<String, String> stillOwed = before.takeReversalsOnArrival();

            assertEquals(stillOwed.get(PENDING_LOC), PENDING_POINT,
                "the drain consumed a turn it cannot possibly have written, so the record this test"
                + " is about is not the one being carried and nothing below means anything");

            before.restoreReversalsOnArrival(stillOwed);

            // THE REBUILD: the same call every setup gesture on the diagram's right-click menu makes.
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            pump();

            Layout after = model.getAutoLayout();

            assertNotNull(after, "the rebuild left no running layout at all");

            assertNotSame(after, before,
                "the rebuild did not replace the running Layout, so nothing had to survive it and this"
                + " test measured nothing.  `rebuildRunningLayoutFromSetup` is a no-op without an"
                + " active configuration, with autonomy busy, or with no viewer panel - one of those"
                + " three is true, and the fixture needs fixing rather than the code");

            Map<String, String> carried = after.takeReversalsOnArrival();

            try
            {
                assertEquals(carried.get(PENDING_LOC), PENDING_POINT,
                    "a destination turn the railway had not yet managed to write down was discarded by"
                    + " a setup rebuild.  The map lives on the Layout and the rebuild replaces the"
                    + " Layout, so any right-click gesture on the diagram - a home, a direction, a"
                    + " caption - destroys it, and a turn that declined to write once has its retries"
                    + " destroyed with it (D3-C5).  The next dispatch is then offered paths for the"
                    + " wrong heading, which is OB-189.  It owes: " + carried);
            }
            finally
            {
                after.restoreReversalsOnArrival(carried);
            }
        }
        finally
        {
            if (model.hasAutoLayout())
            {
                Layout now = model.getAutoLayout();

                // The seeded record goes, whatever happened above; the railway's own goes back.
                now.takeReversalsOnArrival();

                now.restoreReversalsOnArrival(owed);
            }
        }
    }

    /**
     * The window's rebuild really is the method this class calls, and it really does replace a layout.
     *
     * Separate from the test above so that a fixture that cannot rebuild fails HERE, saying so, rather
     * than being read as the carry being broken.  `a-red-test-is-not-proof-of-your-hypothesis`.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testTheRebuildReplacesTheRunningLayout() throws Exception
    {
        Layout before = model.getAutoLayout();

        assertNotNull(before, "there is no running layout to rebuild");

        assertTrue(!model.getAutoLayout().isRunning(),
            "the railway is running, and the rebuild refuses while autonomy is busy - so nothing in"
            + " this class is reaching the code it is about");

        javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        pump();

        assertNotSame(model.getAutoLayout(), before,
            "the window's rebuild did not produce a new Layout, so the carry this class tests has"
            + " nothing to carry anything across");
    }

    /**
     * A setup edit that leaves every caption as it was keeps the track diagram on screen, and one that renames a station
     * builds it again (MT-670; Adam, 2026-10-04: *"every click I make in the autonomy editor makes the viewer diagram
     * flicker.  Necessary?"*).  Every edit rebuilt the whole grid - every square of the page taken down and drawn again -
     * for the one thing a grid cannot be told once built: which squares carry a caption, and the name each gives.
     *
     * MUTATION: rebuild the grid at every setup change again, or never, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testAnEditThatLeavesTheCaptionsKeepsTheDiagram() throws Exception
    {
        settle();

        java.awt.Component before = shownDiagram();

        assertNotNull(before, "precondition: the window shows no track diagram");

        String page = String.valueOf(((javax.swing.JComboBox<?>) windowField("LayoutList")).getSelectedItem());

        // A DIRECTION ON A PIECE OF TRACK ON THE PAGE SHOWN, as an arrow click sets one, and put back
        org.traincontrol.automationui.TileGraph.TileKey track = null;
        org.traincontrol.automationui.TileGraph.RouteId road = null;

        for (org.traincontrol.automationui.TileGraph.TileKey square : session.getGraph().getTiles().keySet())
        {
            if (!page.equals(square.getPage()) || ui.autonomyCaptionAt(square) != null) continue;

            java.util.Map<org.traincontrol.automationui.TileGraph.RouteId, ?> roads = session.getGraph().getRoutes(square);

            if (roads.size() == 1)
            {
                track = square;
                road = roads.keySet().iterator().next();
                break;
            }
        }

        assertNotNull(track, "precondition: page " + page + " has no plain track to set a direction on");

        org.traincontrol.automationui.TileGraph.Direction was = session.getGraph().getDirection(track, road);

        try
        {
            session.setDirection(track, road, was == org.traincontrol.automationui.TileGraph.Direction.NONE
                ? org.traincontrol.automationui.TileGraph.Direction.BOTH : org.traincontrol.automationui.TileGraph.Direction.NONE);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();

            org.testng.Assert.assertSame(shownDiagram(), before, "a direction set on " + track + " rebuilt the whole track diagram, though"
                + " no caption changed (MT-670)");
        }
        finally
        {
            session.setDirection(track, road, was);
        }

        // A STATION RENAMED: its caption's name changes, which a built grid cannot be told
        org.traincontrol.automationui.TileGraph.TileKey station = null;

        for (org.traincontrol.automationui.TileGraph.TileKey square : session.getGraph().getTiles().keySet())
        {
            org.traincontrol.automationui.TileGraph.TileKey captioned = page.equals(square.getPage()) ? ui.autonomyCaptionAt(square) : null;

            if (captioned != null && session.getStore().getPointName(captioned) != null)
            {
                station = captioned;
                break;
            }
        }

        assertNotNull(station, "precondition: page " + page + " has no named station");

        String name = session.getStore().getPointName(station);

        try
        {
            session.setPointName(station, name + " MT670");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();

            assertNotSame(shownDiagram(), before, "a station renamed did not rebuild the track diagram, so its caption"
                + " cannot carry the new name (MT-670)");
        }
        finally
        {
            session.setPointName(station, name);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();
        }
    }

    /**
     * While anything runs - the spinner's gate - the track diagram draws none of the directions trains may or may not take,
     * and they come back as the spinner stops: through a run, a Graceful Stop's coast-down and a hand send (Adam,
     * 2026-10-04: *"when trains are running in autonomy, we hide the allowances/restrictions on the track diagram."*; and on
     * MT-686: *"Works until graceful stop is requested, at which point they get shown prematurely.  Should be the same gate
     * as the spinner."*).
     *
     * MUTATION: draw the arrows whatever runs, gate them on the run alone, or leave the spinner's refresh not asking after
     * them, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testTheArrowsAreHiddenWhileAutonomyRuns() throws Exception
    {
        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        String restrictionsWere = prefs.get(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null);
        String allowedWere = prefs.get(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, null);

        java.lang.reflect.Field flag = Layout.class.getDeclaredField("running");

        flag.setAccessible(true);

        final javax.swing.JButton graceful = (javax.swing.JButton) windowField("gracefulStop");

        final java.util.concurrent.atomic.AtomicInteger hands =
            (java.util.concurrent.atomic.AtomicInteger) windowField("handSendsUnderWay");

        final boolean gracefulWas = graceful.isEnabled();

        boolean handSent = false;

        Layout railway = model.getAutoLayout();

        try
        {
            // WHAT THE DIAGRAM DRAWS WITH BOTH KINDS OF ARROW TURNED OFF, which is what hidden means
            prefs.putBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, false);
            prefs.putBoolean(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, false);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.refreshStaticAutonomyLayer());

            settle();

            int none = arrowPixels();

            // BOTH ASKED FOR, so every square in use draws one
            prefs.putBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);
            prefs.putBoolean(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, true);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.refreshStaticAutonomyLayer());

            theSpinnersRefresh();

            assertFalse(ui.isShowingSomethingRuns(), "precondition: the spinner turns with nothing running");

            assertTrue(arrowPixels() > none, "precondition: the track diagram draws no arrows with the railway at rest");

            // THE RUN STARTS
            flag.setBoolean(railway, true);

            theSpinnersRefresh();

            assertTrue(ui.isShowingSomethingRuns(), "precondition: the spinner does not turn with autonomy running");

            assertEquals(arrowPixels(), none, "the track diagram still draws arrows while autonomy runs");

            // GRACEFUL STOP: the run is over, and the trains coast down, the spinner turning
            flag.setBoolean(railway, false);

            javax.swing.SwingUtilities.invokeAndWait(() -> graceful.setEnabled(true));

            theSpinnersRefresh();

            assertTrue(ui.isShowingSomethingRuns(), "precondition: the spinner stops for a Graceful Stop's coast-down");

            assertEquals(arrowPixels(), none, "the arrows came back as Graceful Stop was pressed, while the trains still"
                + " coasted down and the spinner turned (MT-686)");

            // AND THE LAST TRAIN STOPS
            javax.swing.SwingUtilities.invokeAndWait(() -> graceful.setEnabled(false));

            theSpinnersRefresh();

            assertFalse(ui.isShowingSomethingRuns(), "precondition: the spinner turns on after the run");

            assertTrue(arrowPixels() > none, "the arrows did not come back as the spinner stopped");

            // A HAND SEND, which the spinner shows too
            hands.incrementAndGet();
            handSent = true;

            theSpinnersRefresh();

            assertTrue(ui.isShowingSomethingRuns(), "precondition: the spinner does not turn for a hand send");

            assertEquals(arrowPixels(), none, "the track diagram draws arrows while a train sent by hand runs, the"
                + " spinner turning");

            hands.decrementAndGet();
            handSent = false;

            theSpinnersRefresh();

            assertTrue(arrowPixels() > none, "the arrows did not come back after the hand send");
        }
        finally
        {
            flag.setBoolean(railway, false);

            if (handSent) hands.decrementAndGet();

            javax.swing.SwingUtilities.invokeAndWait(() -> graceful.setEnabled(gracefulWas));

            if (restrictionsWere == null) prefs.remove(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            else prefs.put(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, restrictionsWere);

            if (allowedWere == null) prefs.remove(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS);
            else prefs.put(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, allowedWere);

            theSpinnersRefresh();

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.refreshStaticAutonomyLayer());

            settle();
        }
    }

    /** The refresh that turns the spinner on Start or stops it, as the window makes it where what runs changes. */
    private static void theSpinnersRefresh() throws Exception
    {
        final java.lang.reflect.Method refresh = TrainControlUI.class.getDeclaredMethod("refreshWhatWaitsForTheTrains");

        refresh.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                refresh.invoke(ui);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        settle();
    }

    /**
     * How many red or green pixels the track diagram's marks paint, over every square it marks: each square's mark
     * painted on black, as the registry last set it.
     */
    private static int arrowPixels() throws Exception
    {
        java.lang.reflect.Field last = org.traincontrol.gui.DiagramTileRegistry.class.getDeclaredField("lastAnnotated");

        last.setAccessible(true);

        final java.util.Map<?, ?> marks = new java.util.HashMap<>((java.util.Map<?, ?>) last.get(ui.getDiagramTileRegistry()));

        int count = 0;

        for (Object mark : marks.values())
        {
            if (!(mark instanceof org.traincontrol.automationui.TileAnnotation)) continue;

            java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(30, 30,
                java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = shot.createGraphics();

            try
            {
                g.setColor(java.awt.Color.BLACK);
                g.fillRect(0, 0, 30, 30);

                ((org.traincontrol.automationui.TileAnnotation) mark).paint(g, 30, 30, false);
            }
            finally
            {
                g.dispose();
            }

            for (int x = 0; x < 30; x++)
            {
                for (int y = 0; y < 30; y++)
                {
                    java.awt.Color c = new java.awt.Color(shot.getRGB(x, y));

                    boolean green = c.getGreen() > 150 && c.getRed() < 120 && c.getBlue() < 140;
                    boolean red = c.getRed() > 150 && c.getGreen() < 80 && c.getBlue() < 80;

                    if (green || red) count++;
                }
            }
        }

        return count;
    }

    /**
     * A setup edit that keeps the track diagram on screen forgets the other pages the page cache keeps, as the rebuild it
     * skips did (RSA38-B1): a page drawn before the edit came back from the page list as it was drawn - a page put back
     * into autonomy with none of its captions, a station added with none, a renamed one with its old name.
     *
     * MUTATION: keep the other cached pages through an edit that keeps the diagram, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testAnEditThatKeepsTheDiagramForgetsTheOtherPages() throws Exception
    {
        settle();

        @SuppressWarnings("unchecked")
        final javax.swing.JComboBox<Object> pages = (javax.swing.JComboBox<Object>) windowField("LayoutList");

        final Object first = pages.getSelectedItem();

        Object other = null;

        for (int i = 0; i < pages.getItemCount(); i++)
        {
            if (!pages.getItemAt(i).equals(first)) other = pages.getItemAt(i);
        }

        if (other == null) throw new SkipException("the frozen railway has one page, so no other page can be cached");

        final Object second = other;

        try
        {
            // THE OTHER PAGE DRAWN, AND THIS ONE BROUGHT BACK, so both are in the page cache
            javax.swing.SwingUtilities.invokeAndWait(() -> pages.setSelectedItem(second));
            settle();
            javax.swing.SwingUtilities.invokeAndWait(() -> pages.setSelectedItem(first));
            settle();

            @SuppressWarnings("unchecked")
            java.util.Map<String, javax.swing.JPanel> cache = (java.util.Map<String, javax.swing.JPanel>) windowField(
                "layoutCache");

            assertTrue(cache.size() >= 2, "precondition: the page cache does not hold both pages: " + cache.keySet());

            java.awt.Component before = shownDiagram();

            final int captionsBefore = captionsOf(before);

            assertTrue(captionsBefore > 0, "precondition: the page shown has no captions in the station map");

            // AN EDIT THAT KEEPS THE DIAGRAM ON SCREEN
            setADirectionAndPutItBack(() ->
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                    new java.util.LinkedHashSet<String>()));

                settle();

                org.testng.Assert.assertSame(shownDiagram(), before, "precondition: the edit did not keep the diagram on screen");

                @SuppressWarnings("unchecked")
                java.util.Map<String, javax.swing.JPanel> after = (java.util.Map<String, javax.swing.JPanel>)
                    windowField("layoutCache");

                for (javax.swing.JPanel kept : after.values())
                {
                    org.testng.Assert.assertSame(kept, before, "an edit that kept the diagram on screen kept another page in the page"
                        + " cache, which comes back as it was drawn before the edit (RSA38-B1): " + after.keySet());
                }

                // AND THE PAGE SHOWN KEEPS ITS CAPTIONS: forgotten with the rest, no update would reach them (RSA39-C2)
                assertEquals(captionsOf(before), captionsBefore, "an edit that kept the diagram on screen let go of its"
                    + " captions, which nothing then writes (RSA39-C2)");
            });
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> pages.setSelectedItem(first));

            settle();
        }
    }

    /**
     * A grid the window has replaced is let go (RSA38-C3): the map that found a panel's grid held every grid the window
     * ever built - each grid holds its panel - about half a megabyte for every rebuild of 1 - Main.
     *
     * MUTATION: keep the window's grids in a map again, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testAReplacedGridIsLetGo() throws Exception
    {
        settle();

        java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid> old =
            new java.lang.ref.WeakReference<>(org.traincontrol.gui.LayoutGrid.of(shownDiagram()));

        assertNotNull(old.get(), "precondition: the window's diagram is no grid's");

        // A STATION RENAMED AND PUT BACK: two rebuilds, the grid replaced
        org.traincontrol.automationui.TileGraph.TileKey station = null;

        for (org.traincontrol.automationui.TileGraph.TileKey square : session.getGraph().getTiles().keySet())
        {
            org.traincontrol.automationui.TileGraph.TileKey captioned = ui.autonomyCaptionAt(square);

            if (captioned != null && session.getStore().getPointName(captioned) != null)
            {
                station = captioned;
                break;
            }
        }

        assertNotNull(station, "precondition: the frozen railway has no named station");

        String name = session.getStore().getPointName(station);

        try
        {
            session.setPointName(station, name + " C3");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();
        }
        finally
        {
            session.setPointName(station, name);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();
        }

        assertNotSame(org.traincontrol.gui.LayoutGrid.of(shownDiagram()), old.get(), "precondition: the rename did not"
            + " replace the grid");

        for (int i = 0; i < 10 && old.get() != null; i++)
        {
            System.gc();

            Thread.sleep(100);
        }

        org.testng.Assert.assertNull(old.get(), "a grid the window replaced is still held, as every one it builds would be (RSA38-C3)");
    }

    /**
     * A page the page cache brings back is wired - every tile and caption of it registered - after the same page was drawn
     * at the other size while it was put away (RSA39, outside its round): the registrations are kept by square, the same at
     * both sizes, and the panel came back with no switch, sensor, train's line, arrow or caption on it told anything.
     *
     * MUTATION: put a cached page back without asking whether it is still wired, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testACachedPageComesBackWired() throws Exception
    {
        // AN EMPTY PAGE CACHE FIRST: a page already kept at the other size would come back from it below, and take nothing
        // (RSA40's note)
        javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLayout());

        settle();

        @SuppressWarnings("unchecked")
        final javax.swing.JComboBox<Object> pages = (javax.swing.JComboBox<Object>) windowField("LayoutList");

        @SuppressWarnings("unchecked")
        final javax.swing.JComboBox<Object> sizes = (javax.swing.JComboBox<Object>) windowField("SizeList");

        final Object page = pages.getSelectedItem();
        final Object size = sizes.getSelectedItem();

        Object otherPage = null;
        Object otherSize = null;

        for (int i = 0; i < pages.getItemCount(); i++) if (!pages.getItemAt(i).equals(page)) otherPage = pages.getItemAt(i);
        for (int i = 0; i < sizes.getItemCount(); i++) if (!sizes.getItemAt(i).equals(size)) otherSize = sizes.getItemAt(i);

        if (otherPage == null || otherSize == null) throw new SkipException("the window has one page or one size");

        final Object away = otherPage;
        final Object bigger = otherSize;

        try
        {
            // ANOTHER PAGE, THE OTHER SIZE, THIS PAGE AT IT, AND THIS SIZE AGAIN - this page's panel from the cache
            for (Runnable step : java.util.Arrays.<Runnable>asList(() -> pages.setSelectedItem(away),
                () -> sizes.setSelectedItem(bigger), () -> pages.setSelectedItem(page), () -> sizes.setSelectedItem(size)))
            {
                javax.swing.SwingUtilities.invokeAndWait(step);

                settle();
            }

            java.awt.Component shown = shownDiagram();

            org.traincontrol.gui.LayoutGrid grid = org.traincontrol.gui.LayoutGrid.of(shown);

            assertNotNull(grid, "precondition: the page shown is no grid's");

            assertTrue(grid.tilesAreStillRegistered(), "a page brought back from the page cache has tiles nobody tells"
                + " anything - its switches, sensors, trains' lines and arrows frozen (RSA39)");

            assertTrue(captionsOf(shown) > 0, "a page brought back from the page cache has no captions in the station map -"
                + " frozen (RSA39)");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                sizes.setSelectedItem(size);
                pages.setSelectedItem(page);
            });

            settle();
        }
    }

    /**
     * A setup edit the build refuses still tells the track diagram, as the Autonomy menu's door does (RSA39-C3): through
     * the editor's door the railway was left as it was and nothing else followed, so a page left out came back from the
     * page list with the captions it had before.
     *
     * MUTATION: leave the diagram untold where the build refuses, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testARefusedRebuildStillTellsTheDiagram() throws Exception
    {
        settle();

        @SuppressWarnings("unchecked")
        final javax.swing.JComboBox<Object> pages = (javax.swing.JComboBox<Object>) windowField("LayoutList");

        final Object page = pages.getSelectedItem();

        Object out = null;

        for (int i = 0; i < pages.getItemCount(); i++)
        {
            if (String.valueOf(pages.getItemAt(i)).startsWith("2 - ")) out = pages.getItemAt(i);
        }

        if (out == null || out.equals(page)) throw new SkipException("the frozen railway has no page 2 to leave out");

        final Object leftOut = out;

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> pages.setSelectedItem(leftOut));
            settle();
            javax.swing.SwingUtilities.invokeAndWait(() -> pages.setSelectedItem(page));
            settle();

            @SuppressWarnings("unchecked")
            java.util.Map<String, javax.swing.JPanel> cache = (java.util.Map<String, javax.swing.JPanel>) windowField(
                "layoutCache");

            assertTrue(cache.keySet().stream().anyMatch(k -> k.startsWith(leftOut + " ")), "precondition: page "
                + leftOut + " is not in the page cache: " + cache.keySet());

            Layout railway = model.getAutoLayout();

            session.setPageExcluded(String.valueOf(leftOut), true);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();

            if (model.getAutoLayout() != railway)
            {
                throw new SkipException("leaving " + leftOut + " out did not stop the build here, so the door the build"
                    + " refuses is not reached");
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, javax.swing.JPanel> after = (java.util.Map<String, javax.swing.JPanel>) windowField(
                "layoutCache");

            assertFalse(after.keySet().stream().anyMatch(k -> k.startsWith(leftOut + " ")), "a page left out in an edit the"
                + " build refused is still in the page cache, and comes back with the captions it had (RSA39-C3): "
                + after.keySet());
        }
        finally
        {
            session.setPageExcluded(String.valueOf(leftOut), false);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            javax.swing.SwingUtilities.invokeAndWait(() -> pages.setSelectedItem(page));

            settle();
        }
    }

    /**
     * A build keeps both of its captions of a station captioned on two squares - a long platform labelled at both ends -
     * and a later build's caption still takes an earlier build's place (RSA40-C1).  The station map keeps captions by
     * station, and registering one let go of every other of the same window not on screen, its own build's included: the
     * first of the two was never written, and its page was never served from the page cache again.
     *
     * MUTATION: let a build evict its own caption again, or no caption evict another, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testABuildKeepsBothCaptionsOfAStation() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey station =
            new org.traincontrol.automationui.TileGraph.TileKey("RSA40-C1", 1, 1);

        final javax.swing.JPanel owner = new javax.swing.JPanel();

        final javax.swing.JLabel first = new javax.swing.JLabel();
        final javax.swing.JLabel second = new javax.swing.JLabel();
        final javax.swing.JLabel later = new javax.swing.JLabel();

        Object build = new Object();

        first.putClientProperty(TrainControlUI.LAYOUT_STATION_BUILD, build);
        second.putClientProperty(TrainControlUI.LAYOUT_STATION_BUILD, build);
        later.putClientProperty(TrainControlUI.LAYOUT_STATION_BUILD, new Object());

        try
        {
            // ONE BUILD'S TWO CAPTIONS OF THE STATION, neither on screen yet
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                ui.addLayoutStation(station, first, owner);
                ui.addLayoutStation(station, second, owner);
            });

            assertEquals(new java.util.HashSet<>(ui.getLayoutStations(station)), new java.util.HashSet<>(
                java.util.Arrays.asList(first, second)), "a build let go of its own caption of a station captioned twice,"
                + " which is then never written (RSA40-C1)");

            // AND THE NEXT BUILD'S, which takes their place
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.addLayoutStation(station, later, owner));

            assertEquals(new java.util.HashSet<>(ui.getLayoutStations(station)), new java.util.HashSet<>(
                java.util.Arrays.asList(later)), "a later build's caption did not take the earlier build's place");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.forgetLayoutStations(
                java.util.Arrays.asList(first, second, later), null));
        }
    }

    /**
     * A station renamed while the setup cannot build keeps the train standing at it on its caption (RSA40-C2; Adam,
     * 2026-10-05: *"Yes, do it."*): the railway, left as it was, still carries the names it was built with, and the
     * caption, asked by the new name, found no Point there and went blank until the setup built.
     *
     * MUTATION: find the railway's Points by the setup's names alone, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testARenameTheBuildRefusesKeepsTheTrainOnItsCaption() throws Exception
    {
        settle();

        @SuppressWarnings("unchecked")
        final javax.swing.JComboBox<Object> pages = (javax.swing.JComboBox<Object>) windowField("LayoutList");

        String shownPage = String.valueOf(pages.getSelectedItem());

        Object out = null;

        for (int i = 0; i < pages.getItemCount(); i++)
        {
            if (String.valueOf(pages.getItemAt(i)).startsWith("2 - ")) out = pages.getItemAt(i);
        }

        if (out == null || String.valueOf(out).equals(shownPage)) throw new SkipException("no page 2 to leave out");

        // A STATION ON THE PAGE SHOWN WITH A TRAIN STANDING AT IT
        org.traincontrol.automationui.TileGraph.TileKey station = null;
        String train = null;

        for (org.traincontrol.automationui.TileGraph.TileKey square : session.getGraph().getTiles().keySet())
        {
            if (!shownPage.equals(square.getPage()) || ui.getLayoutStations(square).isEmpty()) continue;

            java.util.List<org.traincontrol.automation.Point> standing = ui.getAutonomyOccupantsForTile(square);

            if (!standing.isEmpty() && session.getStore().getPointName(square) != null)
            {
                station = square;
                train = standing.get(0).getCurrentLocomotive().getName();
                break;
            }
        }

        if (station == null) throw new SkipException("no train stands at a captioned station on page " + shownPage);

        final org.traincontrol.automationui.TileGraph.TileKey at = station;

        // INACTIVE CAPTIONS HIDDEN, so the half of this that hides a station autonomy cannot choose is asked too - the
        // setting is the operator's, copied into the run, and on it hides nothing; put back below
        final java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        final String inactiveWere = prefs.get(TrainControlUI.SHOW_INACTIVE_LABELS_PREF, null);

        prefs.putBoolean(TrainControlUI.SHOW_INACTIVE_LABELS_PREF, false);

        captionsSettled();

        // WHAT IT SAYS WITH THE TRAIN STANDING THERE - the train's name, shortened as a caption shortens it, and its arrow
        final String said = captionText(at);

        assertFalse(said.trim().isEmpty(), "precondition: the caption at " + at + " says nothing of " + train);

        String name = session.getStore().getPointName(at);

        Layout railway = model.getAutoLayout();

        try
        {
            // THE SETUP UNABLE TO BUILD, and the station renamed through the editor's door
            session.setPageExcluded(String.valueOf(out), true);
            session.setPointName(at, name + " C2");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();

            if (model.getAutoLayout() != railway) throw new SkipException("leaving " + out + " out did not stop the build");

            captionsSettled();

            assertEquals(captionText(at), said, "the caption at " + at + " no longer shows " + train + " standing there"
                + " after the station was renamed while the setup could not build (RSA40-C2)");
        }
        finally
        {
            if (inactiveWere == null) prefs.remove(TrainControlUI.SHOW_INACTIVE_LABELS_PREF);
            else prefs.put(TrainControlUI.SHOW_INACTIVE_LABELS_PREF, inactiveWere);

            session.setPointName(at, name);
            session.setPageExcluded(String.valueOf(out), false);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true,
                new java.util.LinkedHashSet<String>()));

            settle();
        }
    }

    /** Asks the window which captions to hide, as a setup change does, and waits for its worker's answer to be painted. */
    private static void captionsSettled() throws Exception
    {
        java.lang.reflect.Method ask = TrainControlUI.class.getDeclaredMethod("refreshCaptionVisibility");

        ask.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ask.invoke(ui);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        java.util.concurrent.atomic.AtomicBoolean busy =
            (java.util.concurrent.atomic.AtomicBoolean) windowField("captionVisibilityInFlight");
        java.util.concurrent.atomic.AtomicBoolean dirty =
            (java.util.concurrent.atomic.AtomicBoolean) windowField("captionVisibilityDirty");

        long until = System.currentTimeMillis() + 30000;

        while ((busy.get() || dirty.get()) && System.currentTimeMillis() < until) Thread.sleep(20);

        assertFalse(busy.get() || dirty.get(), "precondition: the window never finished working out which captions to"
            + " hide");

        pump();
        pump();
    }

    /** What the visible captions of a station say, together. */
    private static String captionText(org.traincontrol.automationui.TileGraph.TileKey station) throws Exception
    {
        final StringBuilder said = new StringBuilder();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (javax.swing.JLabel label : ui.getLayoutStations(station))
            {
                if (label.isVisible()) said.append(label.getText()).append(' ');
            }
        });

        return said.toString();
    }

    /** How many caption labels in the station map are on this panel. */
    @SuppressWarnings("unchecked")
    private static int captionsOf(java.awt.Component panel) throws Exception
    {
        java.util.Map<Object, java.util.Set<javax.swing.JLabel>> stations =
            (java.util.Map<Object, java.util.Set<javax.swing.JLabel>>) windowField("layoutStations");

        final int[] count = new int[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (java.util.Set<javax.swing.JLabel> labels : stations.values())
            {
                for (javax.swing.JLabel label : labels)
                {
                    if (panel instanceof java.awt.Container
                        && javax.swing.SwingUtilities.isDescendingFrom(label, (java.awt.Container) panel)) count[0]++;
                }
            }
        });

        return count[0];
    }

    /** Something to run with a direction set on plain track of the page shown, put back however it ends. */
    private interface WithADirection
    {
        void run() throws Exception;
    }

    /**
     * Sets a direction on a piece of plain track on the page shown, as an arrow click does, runs this, and puts it back.
     *
     * @param then what to run with it set
     * @throws Exception from it
     */
    private static void setADirectionAndPutItBack(WithADirection then) throws Exception
    {
        String page = String.valueOf(((javax.swing.JComboBox<?>) windowField("LayoutList")).getSelectedItem());

        org.traincontrol.automationui.TileGraph.TileKey track = null;
        org.traincontrol.automationui.TileGraph.RouteId road = null;

        for (org.traincontrol.automationui.TileGraph.TileKey square : session.getGraph().getTiles().keySet())
        {
            if (!page.equals(square.getPage()) || ui.autonomyCaptionAt(square) != null) continue;

            java.util.Map<org.traincontrol.automationui.TileGraph.RouteId, ?> roads = session.getGraph().getRoutes(square);

            if (roads.size() == 1)
            {
                track = square;
                road = roads.keySet().iterator().next();
                break;
            }
        }

        assertNotNull(track, "precondition: page " + page + " has no plain track to set a direction on");

        org.traincontrol.automationui.TileGraph.Direction was = session.getGraph().getDirection(track, road);

        try
        {
            session.setDirection(track, road, was == org.traincontrol.automationui.TileGraph.Direction.NONE
                ? org.traincontrol.automationui.TileGraph.Direction.BOTH : org.traincontrol.automationui.TileGraph.Direction.NONE);

            then.run();
        }
        finally
        {
            session.setDirection(track, road, was);
        }
    }

    /** The panel the track diagram shows, or null. */
    private static java.awt.Component shownDiagram() throws Exception
    {
        javax.swing.JPanel inner = (javax.swing.JPanel) windowField("InnerLayoutPanel");

        final java.awt.Component[] shown = new java.awt.Component[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> shown[0] = inner.getComponentCount() == 1 ? inner.getComponent(0)
            : null);

        return shown[0];
    }

    /** One of the window's own fields. */
    private static Object windowField(String name) throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField(name);

        field.setAccessible(true);

        return field.get(ui);
    }

    /**
     * Lets a setup change finish on the window: the posted refresh, the grid renderer's job behind it, and the grid it
     * posts in turn.
     *
     * @throws Exception on an event-thread failure
     */
    private static void settle() throws Exception
    {
        java.util.concurrent.ExecutorService renderer =
            (java.util.concurrent.ExecutorService) windowField("LayoutGridRenderer");

        for (int round = 0; round < 3; round++)
        {
            pump();
            pump();

            renderer.submit(() -> { }).get(30, java.util.concurrent.TimeUnit.SECONDS);

            pump();
            pump();
        }
    }

    /**
     * Lets the event thread finish what it has been given.
     *
     * @throws Exception on an event-thread failure
     */
    private static void pump() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * A turn the railway owes at a station renamed since is carried to the station's new name (RSA5-C2): the turns owed
     * are carried across a rebuild by the name of the Point the train turned at, and after a rename no Point had it -
     * the turn was never written and never forgotten, and the train stayed on the copy facing the way it came in.
     *
     * MUTATION: carry the turns owed by the names recorded alone, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testATurnOwedAtARenamedStationFollowsTheRename() throws Exception
    {
        Layout before = model.getAutoLayout();

        assertNotNull(before, "there is no running layout to record a turn on");

        // A COPY OF A STATION WITH A NAME OF ITS OWN, nobody on it
        org.traincontrol.automation.Point at = null;
        org.traincontrol.automationui.TileGraph.TileKey square = null;

        for (org.traincontrol.automation.Point p : before.getPoints())
        {
            if (at != null || !p.isDestination() || p.getCurrentLocomotive() != null) continue;

            org.traincontrol.automationui.TileGraph.TileKey sq = session.getStationIndex().squareOf(p.getName());

            if (sq != null && session.getStore().getPointName(sq) != null)
            {
                at = p;
                square = sq;
            }
        }

        assertNotNull(at, "precondition: his railway has no named station free");

        final org.traincontrol.automationui.TileGraph.TileKey renamedSquare = square;
        final String oldName = session.getStore().getPointName(square);
        final String renamed = "RSA5 turn renamed";

        Map<String, String> owed = before.takeReversalsOnArrival();

        try
        {
            before.restoreReversalsOnArrival(Collections.singletonMap(PENDING_LOC, at.getName()));

            // THE PRECONDITION, MEASURED: nobody can write it, so it is still owed at the rename
            ui.reconcileFacingWhenIdle();

            pump();

            Map<String, String> stillOwed = before.takeReversalsOnArrival();

            assertEquals(stillOwed.get(PENDING_LOC), at.getName(), "precondition: the drain consumed the turn");

            before.restoreReversalsOnArrival(stillOwed);

            // THE RENAME, from the diagram's setup menu, and the rebuild it makes
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                session.setPointName(renamedSquare, renamed);

                ui.rebuildRunningLayoutFromSetup(true, null);
            });

            pump();

            Layout after = model.getAutoLayout();

            assertNotSame(after, before, "precondition: the rename rebuilt nothing");

            Map<String, String> carried = after.takeReversalsOnArrival();

            try
            {
                String now = carried.get(PENDING_LOC);

                assertTrue(now != null && now.startsWith(renamed)
                    && renamedSquare.equals(session.getStationIndex().squareOf(now)), "a turn owed at " + at.getName()
                    + " was carried by that name, which the rename gave no Point, so it is never written (RSA5-C2): "
                    + carried);
            }
            finally
            {
                after.restoreReversalsOnArrival(carried);
            }
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                session.setPointName(renamedSquare, oldName);

                ui.rebuildRunningLayoutFromSetup(true, null);
            });

            pump();

            if (model.hasAutoLayout())
            {
                Layout now = model.getAutoLayout();

                now.takeReversalsOnArrival();

                now.restoreReversalsOnArrival(owed);
            }
        }
    }

    /**
     * The session sees the running railway from the moment the window makes it (RSA6-A1): the direction refusal asks
     * where the railway's trains stand, and the session was told only when a station's Facing menu was built or the
     * setup editor entered autonomy - so after start-up, or any reset, a direction set from a plain track square's menu
     * was judged against the setup alone, which is where the run began.
     *
     * MUTATION: tell the session only from the menus, and this fails.
     *
     * @throws Exception on an event-thread failure
     */
    @Test
    public void testTheSessionSeesTheRailwayFromTheStart() throws Exception
    {
        AutonomySession made = ui.getAutonomySession();

        assertNotNull(made, "precondition: the window made no session");

        java.lang.reflect.Field source = AutonomySession.class.getDeclaredField("runningLayout");

        source.setAccessible(true);

        Object supplier = source.get(made);

        assertTrue(supplier != null && ((java.util.function.Supplier<?>) supplier).get() == model.getAutoLayoutIfLoaded(),
            "the session the window made cannot see the running railway, so a direction refused over a train the run"
            + " left is judged against where the setup last had it (RSA6-A1): " + supplier);
    }
}
