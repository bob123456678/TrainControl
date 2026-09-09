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
     * Lets the event thread finish what it has been given.
     *
     * @throws Exception on an event-thread failure
     */
    private static void pump() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }
}
