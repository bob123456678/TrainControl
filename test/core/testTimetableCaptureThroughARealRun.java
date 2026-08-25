package core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomyRefreshCallback;
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Capture, tested the way the operator meets it: a real configuration, real autonomy, real trains.
 *
 * Adam, 2026-08-24: "Make sure you validate via REALISTIC tests for the timetable issue that still
 * persists." He had already reported it twice - "capture locomotive commands is capturing neither
 * manual locomotive commands nor full autonomy commands into the timetable", and then "still an issue
 * after testing - nothing gets captured" - against a build whose `testTimetableCapture` was green.
 *
 * It was green because of what it did. It built its own two-point Layout with `Layout.fromJSON`, set
 * the flag on that object, handed `executePath` a path it had assembled itself, and asked whether an
 * entry appeared. Every one of those is a step the application does differently, and the one that
 * mattered is that a test can only see the model. The capture was working the whole time. What had
 * stopped was the REDRAW: the timetable is a description of path starts and ends, it repaints when the
 * layout says one has happened, and the registration that said so was deleted with the GraphStream
 * window in `d8db4879`. Entries were landing in a table nobody was told to redraw.
 *
 * So this test does the whole thing:
 *
 * - a real configuration, through `model.parseAuto`, which is what loading one does
 * - capture switched on where the window switches it, on `model.getAutoLayout()`
 * - `AutonomyRefreshCallback.attach`, which is the same call the window makes
 * - `runLocomotives()`, so autonomy picks its own paths rather than being handed one
 *
 * and then asks the two questions that were separately false: did anything get captured, and was
 * anybody told.
 *
 * The old test is kept. It is a good unit test of the capture flag and it is faster than this; what it
 * cannot do is stand in for this one.
 */
public class testTimetableCaptureThroughARealRun
{
    private static final Accessory.accessoryDecoderType MM2 = Accessory.accessoryDecoderType.MM2;

    /** The names `autonomy_sanity.json` places; parseAuto places only locomotives that already exist. */
    private static final String[] LOCO_NAMES =
    {
        "Auto Test Loc 1", "Auto Test Loc 2", "Auto Test Loc 3"
    };

    private static final long RUN_MS = 45000;

    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);

        // Not connected, and simulating packets, so accessory actuations are confirmed and the trains
        // actually move - the same arrangement testAutonomySimulationSanity uses.
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        for (int at = 0; at < LOCO_NAMES.length; at++)
        {
            model.newMM2Locomotive(LOCO_NAMES[at], 61 + at);
        }

        model.newSwitch(1, MM2, false);
        model.newSwitch(2, MM2, false);
        model.newSwitch(3, MM2, false);
        model.newSwitch(4, MM2, false);
        model.newSignal(5, MM2, false);
        model.newSignal(6, MM2, false);
        model.newSignal(7, MM2, false);
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (model != null && model.hasAutoLayout()) model.getAutoLayout().stopLocomotives();
    }

    /**
     * With capture on, a real autonomy run fills the timetable AND says so.
     *
     * Two assertions because two different things were broken and only one of them was ever visible.
     * The capture half has worked throughout; asserting it here is what makes the redraw half
     * meaningful, because "the table is empty" has two possible causes and this tells them apart.
     */
    @Test
    public void testARealRunCapturesAndAnnouncesIt() throws Exception
    {
        Layout layout = loadedConfiguration();

        // Set rather than assumed, in BOTH directions, because parseAuto preserves the flag across a
        // rebuild - so whichever of these two tests TestNG happens to run first decides what the other
        // one starts with, and within-class order is arbitrary reflection order.  A test that comes
        // and goes between runs of unchanged code is worse than no test.
        layout.setTimetableCapture(false);

        assertFalse(layout.isTimetableCapture(), "capture did not switch off");

        // As the window does: on the model's own layout, before starting.
        layout.setTimetableCapture(true);

        // And the notification the window attaches, by the same call.
        final AtomicInteger announcements = new AtomicInteger();

        AutonomyRefreshCallback.attach(layout, () -> announcements.incrementAndGet());

        assertTrue(layout.getTimetable().isEmpty(), "the fixture started with a timetable already");

        model.go();
        layout.runLocomotives();

        boolean moved = false;

        long deadline = System.currentTimeMillis() + RUN_MS;

        while (System.currentTimeMillis() < deadline)
        {
            if (!layout.getActiveLocomotives().isEmpty()) moved = true;

            // Enough captured to be sure it is not a single lucky dispatch.
            if (layout.getTimetable().size() >= 3 && announcements.get() > 0) break;

            Thread.sleep(200);
        }

        layout.stopLocomotives();

        assertTrue(moved,
            "no locomotive ever moved, so this test proved nothing about capture.  The fixture is "
            + "broken rather than the feature - check that the three locomotives exist and that "
            + "parseAuto placed them");

        assertTrue(layout.getTimetable().size() >= 3,
            "a real autonomy run with capture ON put " + layout.getTimetable().size() + " entries in "
            + "the timetable.  This is the half Adam reported twice, and the half that was never "
            + "actually broken - if it fails now, capture itself has gone");

        // The half that WAS broken, and that no model-level test could see.
        assertTrue(announcements.get() > 0,
            "nothing was told that a path had started or finished.  The entries are in the timetable "
            + "and the panel showing them is never repainted, which is exactly what the operator "
            + "reports as \"nothing gets captured\" - the table stays empty on screen while the data "
            + "behind it is perfectly correct.  Deleted with the GraphStream window in d8db4879");
    }

    /**
     * And with capture off, a run of the same length captures nothing.
     *
     * The mutation check for the test above. Without it, a capture that ignored the flag entirely -
     * or a test fixture that somehow arrived with entries already in it - would pass.
     */
    @Test
    public void testARealRunCapturesNothingWithCaptureOff() throws Exception
    {
        Layout layout = loadedConfiguration();

        // Set explicitly rather than assumed off.  parseAuto PRESERVES the flag across a rebuild -
        // deliberately, because the operator toggling it and then doing something ordinary that
        // rebuilds the layout must not silently switch it back off - so after the test above this
        // fixture arrives with capture ON.  A test that assumed the default here would fail for a
        // reason that is correct behaviour, and the obvious repair is to weaken it.
        layout.setTimetableCapture(false);

        assertFalse(layout.isTimetableCapture(), "capture did not switch off");

        model.go();
        layout.runLocomotives();

        boolean moved = false;

        long deadline = System.currentTimeMillis() + (RUN_MS / 2);

        while (System.currentTimeMillis() < deadline)
        {
            if (!layout.getActiveLocomotives().isEmpty()) moved = true;

            Thread.sleep(200);
        }

        layout.stopLocomotives();

        assertTrue(moved, "no locomotive moved, so nothing was declined and nothing is proved");

        assertTrue(layout.getTimetable().isEmpty(),
            "trains ran with capture switched OFF and the timetable filled anyway, so the flag is "
            + "not being consulted: " + layout.getTimetable());
    }

    /**
     * A fresh configuration in the model, as loading one does.
     *
     * Reloaded per test rather than shared. Layout's version counter is static and every construction
     * retires the earlier instances - a retired Layout refuses to dispatch, silently - so a test that
     * ran second against a shared fixture would see no trains move and blame the feature.
     */
    private static Layout loadedConfiguration() throws Exception
    {
        String json = new BufferedReader(new InputStreamReader(
            testTimetableCaptureThroughARealRun.class
                .getResource("/autonomy_sanity.json").openStream()))
            .lines().collect(Collectors.joining("\n"));

        model.parseAuto(json);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        return layout;
    }
}
