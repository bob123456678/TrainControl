import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Why a train is not moving, said in words.
 *
 * "I pressed start and nothing happened" is the commonest thing a new user asks, and until now nothing
 * in TrainControl could answer it. All of the information existed and was thrown away on every
 * attempt: pickPath rejects a destination with one conjunction of seven terms and records nothing,
 * and isPathClear names its reasons properly but is called with logging turned off, so its message
 * went into lastError and was overwritten by the next candidate.
 *
 * What matters here is that each reason DISCRIMINATES. A "why not" that says the same thing whatever
 * is wrong is worse than none: it looks like an answer and sends the user to check the wrong thing.
 * So each test sets up exactly one obstacle and asserts that the reason names THAT one.
 */
public class testWhyStuck
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
        model.stop();
    }

    /**
     * A station with a train standing on it says so, and names the train.
     */
    @Test
    public void testAnOccupiedStationNamesWhoIsThere() throws Exception
    {
        Layout layout = twoStations("WS1");

        MarklinLocomotive first = model.getLocByName(model.getLocList().get(0));
        MarklinLocomotive second = model.getLocByName(model.getLocList().get(1));

        layout.moveLocomotive(first.getName(), "WS1_A", false);
        layout.moveLocomotive(second.getName(), "WS1_B", false);

        Map<String, String> why = layout.explainDestinations(first);

        assertNotNull(why.get("WS1_B"), "B holds a train, so it cannot be available");

        assertTrue(why.get("WS1_B").contains(second.getName()),
            "the reason must NAME the train standing in the way - a bare refusal sends the user "
            + "looking at the track instead of at the other locomotive.  Got: " + why.get("WS1_B"));
    }

    /**
     * A station switched off says that, and not something else.
     */
    @Test
    public void testAnInactiveStationSaysItIsSwitchedOff() throws Exception
    {
        Layout layout = twoStations("WS2");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "WS2_A", false);

        layout.getPoint("WS2_B").setActive(false);

        String reason = layout.explainDestinations(loc).get("WS2_B");

        assertNotNull(reason, "a switched-off station is not available");

        assertEquals(reason, org.traincontrol.util.I18n.t("autolayout.why.inactive"),
            "the reason for a switched-off station must be that it is switched off, not a generic "
            + "refusal - got: " + reason);
    }

    /**
     * A station that excludes this locomotive says so, and names it.
     */
    @Test
    public void testAnExcludedLocomotiveIsToldWhy() throws Exception
    {
        Layout layout = twoStations("WS3");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "WS3_A", false);

        java.util.Set<org.traincontrol.base.Locomotive> excluded = new java.util.HashSet<>();
        excluded.add(loc);
        layout.getPoint("WS3_B").setExcludedLocs(excluded);

        String reason = layout.explainDestinations(loc).get("WS3_B");

        assertNotNull(reason, "a station that excludes this locomotive is not available to it");

        assertTrue(reason.contains(loc.getName()),
            "the reason must name the locomotive that is excluded, since a station may exclude several "
            + "and only one of them is standing here.  Got: " + reason);
    }

    /**
     * A station with no track to it says THAT, rather than that it is busy.
     *
     * The distinction is the difference between "build some track" and "wait a minute", and a user
     * told the wrong one of those will go and look at the wrong thing.
     */
    @Test
    public void testNoTrackAtAllIsADifferentAnswerFromBusy() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback a = model.newFeedback(61, null);
        MarklinFeedback b = model.newFeedback(62, null);
        MarklinFeedback island = model.newFeedback(63, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{a, b, island})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("WS4_A", true, a.getName());
        layout.createPoint("WS4_B", true, b.getName());
        layout.createPoint("WS4_Island", true, island.getName());

        // A and B are joined; the island is joined to nothing
        layout.createEdge("WS4_A", "WS4_B");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "WS4_A", false);

        Map<String, String> why = layout.explainDestinations(loc);

        assertNull(why.get("WS4_B"), "B is joined to A and free, so it must come back available");

        assertEquals(why.get("WS4_Island"), org.traincontrol.util.I18n.t("autolayout.why.noRoute"),
            "a station with no track leading to it must say so - reporting it as blocked would send "
            + "the user looking for a train that is not there.  Got: " + why.get("WS4_Island"));
    }

    /**
     * A train that can go somewhere gets a null reason, which is what "available" means here.
     */
    @Test
    public void testAnAvailableStationHasNoReason() throws Exception
    {
        Layout layout = twoStations("WS5");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "WS5_A", false);

        assertNull(layout.explainDestinations(loc).get("WS5_B"),
            "nothing is in the way, so B must be available - a reason here would mean the explanation "
            + "disagrees with the layout it is explaining");

        assertNull(layout.explainCannotStart(loc),
            "and the locomotive itself is free to start");
    }

    /**
     * The four reasons that are about the train rather than any destination.
     */
    @Test
    public void testALocomotiveOffTheGraphIsToldSo() throws Exception
    {
        Layout layout = twoStations("WS6");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        // Deliberately not placed
        assertEquals(layout.explainCannotStart(loc),
            org.traincontrol.util.I18n.t("autolayout.why.notOnGraph"),
            "a locomotive that is not on the railway must be told that, rather than being given a "
            + "list of stations it cannot reach");

        assertTrue(layout.explainDestinations(loc).isEmpty(),
            "and there is nothing useful to say about destinations for a train that is nowhere");
    }

    /**
     * Non-stations are left out entirely.
     *
     * On a real layout they are most of the graph, and a list of two hundred "not a station" lines
     * answers nothing while burying the entries that do.
     */
    @Test
    public void testPlainTrackIsNotListed() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback a = model.newFeedback(64, null);
        MarklinFeedback middle = model.newFeedback(65, null);
        MarklinFeedback b = model.newFeedback(66, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{a, middle, b})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("WS7_A", true, a.getName());
        layout.createPoint("WS7_Middle", false, middle.getName());
        layout.createPoint("WS7_B", true, b.getName());

        layout.createEdge("WS7_A", "WS7_Middle");
        layout.createEdge("WS7_Middle", "WS7_B");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "WS7_A", false);

        Map<String, String> why = layout.explainDestinations(loc);

        assertFalse(why.containsKey("WS7_Middle"),
            "plain track must not be listed - it is most of a real graph, and listing it buries the "
            + "stations that are the actual answer");

        assertTrue(why.containsKey("WS7_B"), "but the station beyond it must be");
    }

    /**
     * Two stations joined both ways, named with a prefix so the tests cannot collide.
     */
    private static Layout twoStations(String prefix) throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback a = model.newFeedback(51, null);
        MarklinFeedback b = model.newFeedback(52, null);

        model.setFeedbackState(a.getName(), false);
        model.setFeedbackState(b.getName(), false);

        layout.createPoint(prefix + "_A", true, a.getName());
        layout.createPoint(prefix + "_B", true, b.getName());

        layout.createEdge(prefix + "_A", prefix + "_B");
        layout.createEdge(prefix + "_B", prefix + "_A");

        return layout;
    }
}
