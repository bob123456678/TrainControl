package core;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Every rule for choosing between routes, measured against a railway where they disagree.
 *
 * Adam asked for this. Three of the seven rules were already tested one at a time in testAutoLayout -
 * fewest sensors, fewest stations, shortest track - and the three that are their mirrors were not
 * tested at all. A mirror is exactly the kind of thing that looks too simple to get wrong: it is the
 * same number negated, which means one missing minus sign turns "take the scenic way round" into
 * "take the direct one" and nothing anywhere complains.
 *
 * The fixture is one junction with two ways across it, arranged so that every rule has a different
 * favourite:
 *
 * <pre>
 *   the short way:  Start -> ViaStation -> End      2 sensors, 1 station passed, 100 long
 *   the long way:   Start -> Plain1 -> Plain2 -> End  3 sensors, 0 stations passed, 12 long
 * </pre>
 *
 * So fewest-sensors and longest-track both want the short way; fewest-stations and shortest-track
 * both want the long one. Each rule and its mirror must therefore choose differently from each other,
 * which is the whole of what a mirror has to do and the thing that cannot be checked one rule at a
 * time.
 *
 * The last test is the one that will outlive the rest: it fails when a new rule is added to the enum
 * and not tested here.
 */
public class testRoutePicking
{
    private static MarklinControlStation model;
    private static Layout.PathPreference was;

    /** Every rule this class chooses a winner for. */
    private static final Set<Layout.PathPreference> COVERED_HERE = EnumSet.of(
        Layout.PathPreference.FEWEST_POINTS,
        Layout.PathPreference.MOST_POINTS,
        Layout.PathPreference.FEWEST_STATIONS,
        Layout.PathPreference.MOST_STATIONS,
        Layout.PathPreference.SHORTEST_LENGTH,
        Layout.PathPreference.LONGEST_LENGTH,
        Layout.PathPreference.RANDOM);

    /**
     * And the ones whose ranking needs a different railway, with where they are tested instead.
     *
     * LEAST_RECENTLY_VISITED ranks DESTINATIONS by when a train last arrived at them, so it cannot be
     * told apart on a fixture whose routes all end in the same place. It has its own test in
     * testAutoLayout, on a fixture with two destinations and an arrival recorded at one of them.
     */
    private static final Set<Layout.PathPreference> COVERED_ELSEWHERE = EnumSet.of(
        Layout.PathPreference.LEAST_RECENTLY_VISITED);

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        was = Layout.getPathPreference();
    }

    @AfterClass
    public static void tearDownClass()
    {
        // Static and process-wide: left set, it would change how every later class in this JVM routes
        if (was != null) Layout.setPathPreference(was);
    }

    /**
     * Fewest sensors takes the two-sensor way; most sensors takes the three-sensor way.
     */
    @Test
    public void testTheSensorRulesAreMirrors() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        Layout.setPathPreference(Layout.PathPreference.FEWEST_POINTS);

        assertEquals(wayTaken(layout, loc), "RP_ViaStation",
            "fewest sensors must take the way across two of them rather than three");

        Layout.setPathPreference(Layout.PathPreference.MOST_POINTS);

        assertEquals(wayTaken(layout, loc), "RP_Plain1",
            "most sensors must take the way across three.  A mirror that picks the same route as the "
            + "rule it mirrors is a missing minus sign, and nothing else would ever say so");
    }

    /**
     * Fewest stations takes the way past none; most stations takes the way past one.
     */
    @Test
    public void testTheStationRulesAreMirrors() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        Layout.setPathPreference(Layout.PathPreference.FEWEST_STATIONS);

        assertEquals(wayTaken(layout, loc), "RP_Plain1",
            "fewest stations must take the way that passes none");

        Layout.setPathPreference(Layout.PathPreference.MOST_STATIONS);

        assertEquals(wayTaken(layout, loc), "RP_ViaStation",
            "most stations must take the way that calls past one - this is the rule for a layout "
            + "that should look busy, and taking the direct route is the opposite of it");
    }

    /**
     * Shortest track takes the twelve; longest takes the hundred.
     */
    @Test
    public void testTheLengthRulesAreMirrors() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        Layout.setPathPreference(Layout.PathPreference.SHORTEST_LENGTH);

        assertEquals(wayTaken(layout, loc), "RP_Plain1",
            "shortest track must take the 12-long way over the 100-long one");

        Layout.setPathPreference(Layout.PathPreference.LONGEST_LENGTH);

        assertEquals(wayTaken(layout, loc), "RP_ViaStation",
            "longest track must take the 100-long way");
    }

    /**
     * Random takes one of the two, and it is a real route either way.
     *
     * There is nothing to assert about which one - that is the point of it - so what is worth pinning
     * is that it still produces a route at all. Random is the DEFAULT, so a fault here is a fault
     * every existing railway meets on upgrade, and "picks nothing" would read as a layout with
     * nowhere to go.
     */
    @Test
    public void testRandomStillChoosesARealRoute() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        Layout.setPathPreference(Layout.PathPreference.RANDOM);

        String taken = wayTaken(layout, loc);

        assertTrue("RP_ViaStation".equals(taken) || "RP_Plain1".equals(taken),
            "random must still choose one of the two ways across - it chose: " + taken);
    }

    /**
     * Every rule the enum offers is tested somewhere.
     *
     * This is the test that keeps the rest honest as the application grows. Adding a rule to
     * PathPreference is a two-line change - a case in the enum and a case in costOf - and the cost of
     * getting it wrong is a preference that silently does nothing, which is invisible precisely
     * because the user cannot tell "this rule chose that route" from "this rule did not run".
     *
     * A new rule fails this until it is either given a winner above or named as covered elsewhere,
     * with the reason.
     */
    @Test
    public void testEveryRuleIsCoveredSomewhere()
    {
        for (Layout.PathPreference rule : Layout.PathPreference.values())
        {
            assertTrue(COVERED_HERE.contains(rule) || COVERED_ELSEWHERE.contains(rule),
                rule + " is a way of choosing routes that nothing tests.  Give it a winner on the "
                + "fixture in this class, or add it to COVERED_ELSEWHERE saying which railway it "
                + "needs and where that test is");
        }
    }

    /**
     * One junction with two ways across it, built fresh so no test can disturb another.
     */
    private static Layout twoWaysAcross() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(201, null);
        MarklinFeedback viaStation = model.newFeedback(202, null);
        MarklinFeedback plainOne = model.newFeedback(203, null);
        MarklinFeedback plainTwo = model.newFeedback(204, null);
        MarklinFeedback end = model.newFeedback(205, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{start, viaStation, plainOne, plainTwo, end})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("RP_Start", true, start.getName());
        layout.createPoint("RP_ViaStation", true, viaStation.getName());
        layout.createPoint("RP_End", true, end.getName());

        layout.createPoint("RP_Plain1", false, plainOne.getName());
        layout.createPoint("RP_Plain2", false, plainTwo.getName());

        // The short way: two sensors, one station passed, 100 long
        layout.createEdge("RP_Start", "RP_ViaStation").setLength(50);
        layout.createEdge("RP_ViaStation", "RP_End").setLength(50);

        // The long way: three sensors, no stations passed, 12 long
        layout.createEdge("RP_Start", "RP_Plain1").setLength(4);
        layout.createEdge("RP_Plain1", "RP_Plain2").setLength(4);
        layout.createEdge("RP_Plain2", "RP_End").setLength(4);

        // A station, but not one autonomy may send a train TO.
        //
        // Without this the fixture cannot tell the rules apart: "go to RP_ViaStation" is itself a
        // route past no stations, so fewest-stations would pick it and the assertion could not say
        // whether the rule had measured the route or simply chosen a nearer destination.
        layout.getPoint("RP_ViaStation").setAutoDestination(false);

        return layout;
    }

    /**
     * Puts a locomotive at the start and hands it back.
     */
    private static Locomotive placedLocomotive(Layout layout) throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "RP_Start", false);

        return loc;
    }

    /**
     * Which way the chosen route went, named by the square it takes first.
     *
     * The routes share their destination, so the first square after the start is the only thing that
     * distinguishes them - which is also why this fixture can compare rules that a two-destination
     * one could not.
     */
    private static String wayTaken(Layout layout, Locomotive loc)
    {
        List<Edge> path = layout.pickPath(loc);

        assertNotNull(path, "no route was chosen at all, and there are two");

        return path.get(0).getEnd().getName();
    }
}
