package regression;

import java.util.Collections;
import java.util.Map;
import static org.testng.Assert.assertEquals;
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
     * While autonomy runs, the track diagram draws none of the directions trains may or may not take, and they come back
     * when the run ends (Adam, 2026-10-04: *"when trains are running in autonomy, we hide the allowances/restrictions on
     * the track diagram."*) - redrawn by the refresh a run's trains make as they move, and its end.
     *
     * MUTATION: draw the arrows whatever the run, or leave the refresh unasked, and this fails.
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

            settle();

            assertTrue(arrowPixels() > none, "precondition: the track diagram draws no arrows with the railway at rest");

            // THE RUN STARTS, and its trains' refresh comes
            flag.setBoolean(railway, true);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

            settle();

            assertEquals(arrowPixels(), none, "the track diagram still draws arrows while autonomy runs");

            // AND ENDS
            flag.setBoolean(railway, false);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

            settle();

            assertTrue(arrowPixels() > none, "the arrows did not come back when the run ended");
        }
        finally
        {
            flag.setBoolean(railway, false);

            if (restrictionsWere == null) prefs.remove(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            else prefs.put(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, restrictionsWere);

            if (allowedWere == null) prefs.remove(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS);
            else prefs.put(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, allowedWere);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.refreshStaticAutonomyLayer());

            settle();
        }
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
