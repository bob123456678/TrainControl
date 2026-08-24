package regression;

import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Capturing what the operator drives, into the timetable.
 *
 * There was no test for this at all, which Adam noticed when it broke: "capture locomotive commands is
 * capturing neither manual locomotive commands nor full autonomy commands into the timetable.
 * Regression."
 *
 * The engine was never at fault. `addTimetableEntry` works, and did throughout - probing it directly
 * captured a dispatch first time. What was broken is that the operator's CHOICE did not survive: the
 * capture flag lives on the Layout object, `parseAuto` replaces that object wholesale, and a fresh
 * Layout starts with capture off. So every rebuild - applying a diagram edit, placing a locomotive,
 * loading a configuration - quietly turned capture off underneath a toggle button that stayed lit.
 *
 * That is why the tests below are in two halves. The first two are the behaviour anybody would have
 * written; the third is the one that would have caught this, and it is about a piece of state
 * surviving something that looks unrelated to it.
 *
 * @author Adam
 */
public class testTimetableCapture
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpModel() throws Exception
    {
        model = MarklinControlStation.init(null, true, false, false, true);

        for (int address : new int[]{47441, 47442})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }
    }

    @AfterClass
    public static void tearDownModel()
    {
        if (model != null) model.stop();
    }

    /**
     * With capture on, a dispatch lands in the timetable.
     */
    @Test
    public void testADispatchIsCapturedWhenCaptureIsOn() throws Exception
    {
        Layout layout = twoStations();

        Locomotive loc = firstLocomotive();

        layout.getPoint("A").setLocomotive(loc);
        layout.setTimetableCapture(true);

        assertTrue(layout.getTimetable().isEmpty(), "the fixture started with a timetable already");

        dispatch(layout, loc);

        List<TimetablePath> captured = waitForCapture(layout, 1);

        assertEquals(captured.size(), 1,
            "a dispatch was not captured, with capture switched on - which is the whole of the "
            + "feature");

        assertEquals(captured.get(0).getLoc().getName(), loc.getName(),
            "the captured entry names a different locomotive");

        assertFalse(captured.get(0).getPath().isEmpty(),
            "the captured entry has no path, so replaying it would move nothing");
    }

    /**
     * And with capture off, nothing is.
     *
     * The mutation check for the test above: an entry appearing here would mean the flag is not
     * consulted at all, and the first test would pass whatever the code did.
     */
    @Test
    public void testNothingIsCapturedWhenCaptureIsOff() throws Exception
    {
        Layout layout = twoStations();

        Locomotive loc = firstLocomotive();

        layout.getPoint("A").setLocomotive(loc);

        assertFalse(layout.isTimetableCapture(), "capture should start off");

        dispatch(layout, loc);

        // long enough that a capture would have happened - the entry is added immediately after the
        // path is locked, well before the train finishes
        waitForCapture(layout, 1);

        assertTrue(layout.getTimetable().isEmpty(),
            "a dispatch was captured with capture switched OFF: " + layout.getTimetable());
    }

    /**
     * The operator's choice survives the layout being rebuilt.
     *
     * This is the one that matters, and the one nothing covered. `parseAuto` replaces the Layout
     * object, and the capture flag lives on it - so a rebuild started a fresh Layout with capture off
     * while the toggle button, which is not repainted at that moment, stayed lit. The operator pressed
     * a button, did something ordinary that rebuilds the setup, and captured nothing for the rest of
     * the session with no indication why.
     *
     * A rebuild happens far more often than it reads: applying a diagram edit, placing a locomotive
     * and loading a configuration all come through parseAuto.
     */
    @Test
    public void testCaptureSurvivesTheLayoutBeingRebuilt() throws Exception
    {
        model.parseAuto(twoStationJSON());

        assertNotNull(model.getAutoLayout(), "the fixture did not build");

        model.getAutoLayout().setTimetableCapture(true);

        assertTrue(model.getAutoLayout().isTimetableCapture(), "the fixture did not switch capture on");

        // Anything that rebuilds the setup - a diagram edit applied, a locomotive placed, a
        // configuration loaded - arrives here.
        model.parseAuto(twoStationJSON());

        assertTrue(model.getAutoLayout().isTimetableCapture(),
            "rebuilding the layout switched capture off. The toggle button is not repainted at that "
            + "moment, so it stays lit over a layout that is no longer capturing - and the operator "
            + "drives a whole session that records nothing");
    }

    // ---------------------------------------------------------------------------------------------

    private static String twoStationJSON()
    {
        return "{"
            + "\"points\": ["
            + "  {\"name\": \"A\", \"station\": true, \"s88\": 47441},"
            + "  {\"name\": \"B\", \"station\": true, \"s88\": 47442}"
            + "],"
            + "\"edges\": [{\"start\": \"A\", \"end\": \"B\", \"length\": 1}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";
    }

    private Layout twoStations() throws Exception
    {
        Layout layout = Layout.fromJSON(twoStationJSON(), model);

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        return layout;
    }

    private Locomotive firstLocomotive()
    {
        return model.getLocByName(model.getLocList().get(0));
    }

    /**
     * Dispatches A to B on a thread of its own, because executePath drives the train and does not
     * return until it has arrived - and the capture happens immediately after the path is locked.
     */
    private void dispatch(Layout layout, Locomotive loc)
    {
        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("A", "B"));

        Thread run = new Thread(() -> layout.executePath(path, loc, 35, null));

        run.setDaemon(true);
        run.start();
    }

    private List<TimetablePath> waitForCapture(Layout layout, int wanted) throws InterruptedException
    {
        for (int waited = 0; waited < 40 && layout.getTimetable().size() < wanted; waited++)
        {
            Thread.sleep(250);
        }

        return layout.getTimetable();
    }
}
