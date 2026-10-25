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
 * Capturing what the operator drives, into the timetable.  There was no test for this at all, which Adam noticed when
 * it broke: "capture locomotive commands is capturing neither manual locomotive commands nor full autonomy commands
 * into the timetable. Regression."
 *
 * The engine was never at fault; what was broken is that the operator's CHOICE did not survive.  The capture flag lives
 * on the Layout object, `parseAuto` replaces that object wholesale, and a fresh Layout starts with capture off, so
 * every rebuild (a diagram edit applied, a locomotive placed, a configuration loaded) turned capture off underneath a
 * toggle button that stayed lit.  So the tests are in two halves: the first two are the behavior anybody would have
 * written; the third is about a piece of state surviving something that looks unrelated to it.
 *
 * @author Adam
 */
public class testTimetableCapture
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpModel() throws Exception
    {
        model = support.TestStation.simulatedInDebug();

        for (int address : new int[]{47441, 47442})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownModel()
    {
        if (model != null) model.stop();
    }

    /** With capture on, a dispatch lands in the timetable. */
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
     * And with capture off, nothing is: an entry here would mean the flag is not consulted, and the first test would
     * pass whatever the code did.
     */
    @Test
    public void testNothingIsCapturedWhenCaptureIsOff() throws Exception
    {
        Layout layout = twoStations();

        Locomotive loc = firstLocomotive();

        layout.getPoint("A").setLocomotive(loc);

        assertFalse(layout.isTimetableCapture(), "capture should start off");

        dispatch(layout, loc);

        // The entry is added immediately after the path is locked, well before the train finishes.
        waitForCapture(layout, 1);

        assertTrue(layout.getTimetable().isEmpty(),
            "a dispatch was captured with capture switched OFF: " + layout.getTimetable());
    }

    /**
     * The operator's choice survives the layout being rebuilt.  `parseAuto` replaces the Layout object, and the capture
     * flag lives on it, so a rebuild started a fresh Layout with capture off while the toggle button stayed lit, and
     * nothing was captured for the rest of the session with no indication why.  A rebuild happens far more often than
     * it reads.
     */
    @Test
    public void testCaptureSurvivesTheLayoutBeingRebuilt() throws Exception
    {
        model.parseAuto(twoStationJSON());

        assertNotNull(model.getAutoLayout(), "the fixture did not build");

        model.getAutoLayout().setTimetableCapture(true);

        assertTrue(model.getAutoLayout().isTimetableCapture(), "the fixture did not switch capture on");

        // Anything that rebuilds the setup arrives here.
        model.parseAuto(twoStationJSON());

        assertTrue(model.getAutoLayout().isTimetableCapture(),
            "rebuilding the layout switched capture off. The toggle button is not repainted at that "
            + "moment, so it stays lit over a layout that is no longer capturing - and the operator "
            + "drives a whole session that records nothing");
    }

    /**
     * How long the run from A to B is measured, and it is LONGER THAN THE TRAIN on purpose (MT-262): the berth-room
     * rule in `Layout.isPathClear` judges B, a plain through station, on the track leading into it, and
     * `firstLocomotive()` comes from the operator's real database, three units long.  A dispatch refused for length
     * never reaches the capture, which also makes the capture-off test pass for the wrong reason.
     */
    private static final int APPROACH = 20;

    /**
     * That the run really is longer than the train this class dispatches: the locomotive is the operator's own, so its
     * length is data this file does not control, and a longer one would read as a broken capture.
     */
    private static void theRunHoldsTheTrainUsedHere(Locomotive loc)
    {
        Integer length = loc.getTrainLength();

        assertTrue(length == null || length <= APPROACH,
            loc.getName() + " is " + length + " units long and this fixture measures A to B at "
            + APPROACH + ", so the berth-room rule refuses the dispatch before anything can be"
            + " captured.  Raise APPROACH");
    }

    private static String twoStationJSON()
    {
        return "{"
            + "\"points\": ["
            + "  {\"name\": \"A\", \"station\": true, \"s88\": 47441},"
            + "  {\"name\": \"B\", \"station\": true, \"s88\": 47442}"
            + "],"
            + "\"edges\": [{\"start\": \"A\", \"end\": \"B\", \"length\": " + APPROACH + "}],"
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
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        theRunHoldsTheTrainUsedHere(loc);

        return loc;
    }

    /**
     * Dispatches A to B on a thread of its own, because executePath does not return until the train has arrived; the
     * capture happens right after the path is locked.
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
