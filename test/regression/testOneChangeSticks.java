package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Change ONE thing on a normally-running railway, and it is still there afterwards.
 *
 * Adam, 2026-09-08: **"we need more tests that just run the layout normally, change one thing, and
 * confirm that sticks. this is how I caught all the recent bugs..."**
 *
 * He is describing his own method, and it has a much better record than the suite's. The defects he
 * has found this way - the arrival side wiped when the train was placed on top of it (`REG8-A1`), the
 * facing that came back as a different copy's, the caption mode remembered as a word and not as an
 * effect (`RGD-C3`), the tail left on a square whose occupant had changed (`RGD-B1`) - are all the same
 * shape. Nothing REFUSED the change. It was accepted, it looked right, and it was gone by the time
 * anybody looked again, because something downstream rebuilt over it.
 *
 * A test that sets a value and reads it straight back cannot see any of that, and the suite has plenty
 * of those. What catches it is the round trip an operator actually makes: **write it, then do the
 * ordinary things - save, build, run, capture, save again, reload from disk - and ask whether it is
 * still what you said.**
 *
 * The four steps are all load-bearing and each has eaten a value at least once:
 *
 * | | |
 * |---|---|
 * | **save** | writes the configuration out |
 * | **build** | `buildConfiguration` + `parseAuto` - where placement used to clear the arrival side |
 * | **capture** | folds the running layout back over the setup - where a departed train left its tail |
 * | **reload** | a fresh `AutonomySession` off the disk - where nothing that was never written survives |
 *
 * **Every property is checked and they are all reported together.** A loop that fails on the first one
 * hides the rest, and which of them survive is the whole diagnosis: one lost value is a bug in one
 * setter, and five lost values is a bug in the round trip.
 *
 * MUTATION: making any of the setters write only to the running layout, or making the capture drop a
 * key it does not itself carry, fails this and names the property.
 *
 * @author Adam
 */
public class testOneChangeSticks
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // OB-111: the sandbox is opened BEFORE the model is built, so nothing reaches Adam's railway.
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // The SHAPE is what this class is about, and the shape is the same in both.  Where his trains
        // are standing, how long they are and which side they came in by are not, and reading those
        // off the live folder is how `testTheLengthGuardsOnTheRealLayout` came to assert that the
        // 2-8-4 stood at BottomMainB - it is at BottomMainA now, and that class was red for a reason
        // that had nothing to do with any guard.  A fixture that moves while nobody is looking makes
        // every class over it say something different every week.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * One property at a time, through the whole round trip.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testEveryAuthoredValueSurvivesTheRoundTrip() throws Exception
    {
        TileKey station = aNamedStation();

        assertNotNull(station, "no named station on this railway, so nothing below was exercised");

        List<String> lost = new ArrayList<>();

        // Each entry: what it is called, how to write it, how to read it back, and what to expect.
        check(lost, station, "maxTrainLength", 7);
        check(lost, station, "priority", 3);
        check(lost, station, "canReverse", Boolean.TRUE);
        check(lost, station, "autoDestination", Boolean.FALSE);

        assertTrue(lost.isEmpty(),
            "these values were written to a station, and after saving, building, capturing and"
            + " reloading the railway no longer has them. Nothing refused the change: it was accepted,"
            + " it looked right, and something downstream rebuilt over it - which is the shape of every"
            + " defect Adam has found by hand this month. " + lost);
    }

    /**
     * The arrival side, which is the one that has been lost twice.
     *
     * Kept apart from the table above because it is not a plain point property: it is written by the
     * arrival, by three operator doors and by nothing else, and `REG8-A1` was the build applying it
     * BEFORE placing the locomotive, so placing the train wiped the side that had just been set.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheArrivalSideSurvivesTheRoundTrip() throws Exception
    {
        TileKey station = aNamedStation();

        assertNotNull(station, "no named station on this railway");

        List<String> sides = new ArrayList<>();

        for (Side side : Side.values()) sides.add(side.name());

        String recorded = null;

        // A side the square can actually be entered by, where the build knows one - and any side at all
        // where it does not, because what is being tested is whether the VALUE survives, not whether it
        // is a sensible one.
        for (org.traincontrol.automationui.TilePorts.Side side : session.arrivalSides(station))
        {
            recorded = side.name();

            break;
        }

        if (recorded == null) recorded = sides.get(0);

        session.setArrivedFrom(station, recorded);

        roundTrip();

        assertTrue(recorded.equals(reopened().getArrivedFrom(station)),
            "the arrival side was set to " + recorded + " and after a save, a build, a capture and a"
            + " reload the setup says " + reopened().getArrivedFrom(station) + ". That is REG8-A1's"
            + " shape: the value is accepted, and something in the rebuild writes over it - and the"
            + " cost is that the track behind a standing train stops being blocked");
    }

    /**
     * A home assignment, which is authored and has no other source.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAHomeSurvivesTheRoundTrip() throws Exception
    {
        TileKey station = aNamedStation();

        assertNotNull(station, "no named station on this railway");

        String train = model.getLocList().get(0);

        session.setHome(station, train);

        roundTrip();

        assertTrue(train.equals(String.valueOf(reopened().getPointProperty(station, "home"))),
            "a home locomotive was assigned to " + station + " and after the round trip the setup says "
            + reopened().getPointProperty(station, "home") + ". A home has no source but the operator, so anything that"
            + " loses one has invented the loss");
    }

    // ---------------------------------------------------------------- the round trip itself

    /**
     * Writes one property and reports it if the round trip loses it.
     *
     * @param lost where to record a loss
     * @param station the square
     * @param key the property
     * @param value what to set it to
     * @throws Exception on a failure to build
     */
    private void check(List<String> lost, TileKey station, String key, Object value) throws Exception
    {
        session.setPointProperty(station, key, value);

        roundTrip();

        Object back = reopened().getPointProperty(station, key);

        if (!String.valueOf(value).equals(String.valueOf(back)))
        {
            lost.add(key + ": set " + value + ", got " + back);
        }
    }

    /**
     * Everything the application does between one edit and the next look.
     *
     * @throws Exception on a failure to build
     */
    private void roundTrip() throws Exception
    {
        session.save();

        model.parseAuto(session.buildConfiguration());

        // THE CAPTURE, which is where a value that the running layout does not itself carry can be
        // dropped - the running layout is the authority for what it knows, and the file for the rest.
        session.captureFromLayout(model.getAutoLayout().toJSON());

        session.save();
    }

    /**
     * The setup as it is on disk, read fresh.
     *
     * A getter on the live session would answer from memory, and memory is exactly what a reload is
     * meant to be independent of.
     *
     * @return a session opened on the same folder
     * @throws Exception on a failure to open
     */
    private AutonomySession reopened() throws Exception
    {
        AutonomySession fresh = new AutonomySession(sandbox.getFolder());

        fresh.open(support.LayoutSandbox.wiredPages(model));

        return fresh;
    }

    /**
     * A station this railway actually has.
     *
     * @return its square, or null
     */
    private TileKey aNamedStation()
    {
        for (TileKey square : session.getReducer().getPoints().keySet())
        {
            // A NAMED station, because the properties below are stored against the setup's own
            // squares and an unnamed point has nowhere to keep them.
            if (session.getStationIndex().nameOf(square) != null) return square;
        }

        return null;
    }
}
