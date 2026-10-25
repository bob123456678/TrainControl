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
 * Every rule for choosing between routes, measured against a railway where they disagree.  A mirror rule (the same
 * number negated) looks too simple to get wrong, and one missing minus sign turns "take the scenic way round" into
 * "take the direct one" with nothing complaining.
 *
 * The fixture is one junction with two ways across it, arranged so that every rule has a different favorite:
 *
 * <pre>
 *   the short way:  Start -> ViaStation -> End      2 sensors, 1 station passed, 100 long
 *   the long way:   Start -> Plain1 -> Plain2 -> End  3 sensors, 0 stations passed, 12 long
 * </pre>
 *
 * Fewest-sensors and longest-track both want the short way; fewest-stations and shortest-track both want the long
 * one, so each rule and its mirror must choose differently.  The last test fails when a new rule is added to the enum
 * and not tested here.
 */
public class testRoutePicking
{
    private static MarklinControlStation model;

    /**
     * Every rule this class chooses a winner for, and WHICH method does it, checked by reflection: a bare set would
     * go on reporting a rule as covered after its test was deleted.
     */
    private static final java.util.Map<Layout.PathPreference, String> COVERED_HERE_BY;

    static
    {
        java.util.Map<Layout.PathPreference, String> here = new java.util.LinkedHashMap<>();

        here.put(Layout.PathPreference.FEWEST_POINTS, "testTheSensorRulesAreMirrors");
        here.put(Layout.PathPreference.MOST_POINTS, "testTheSensorRulesAreMirrors");
        here.put(Layout.PathPreference.FEWEST_STATIONS, "testTheStationRulesAreMirrors");
        here.put(Layout.PathPreference.MOST_STATIONS, "testTheStationRulesAreMirrors");
        here.put(Layout.PathPreference.SHORTEST_LENGTH, "testTheLengthRulesAreMirrors");
        here.put(Layout.PathPreference.LONGEST_LENGTH, "testTheLengthRulesAreMirrors");
        here.put(Layout.PathPreference.RANDOM, "testRandomStillChoosesARealRoute");
        here.put(Layout.PathPreference.BALANCED_PRIORITY,
            "testStationPriorityIsWeighedAgainstDistance");

        COVERED_HERE_BY = java.util.Collections.unmodifiableMap(here);
    }

    private static final Set<Layout.PathPreference> COVERED_HERE =
        EnumSet.copyOf(COVERED_HERE_BY.keySet());

    /**
     * And the ones whose ranking needs a different railway, with where they are tested instead.
     * LEAST_RECENTLY_VISITED ranks DESTINATIONS, so it needs two destinations (testAutoLayout).  RANDOM_ANY_STATION
     * differs from RANDOM only in WHICH stations are eligible, so it is measured as a distribution over 400 decisions
     * per case on the frozen snapshot of the operator's railway (MT-231, OB-156).
     */
    private static final Set<Layout.PathPreference> COVERED_ELSEWHERE = EnumSet.of(
        Layout.PathPreference.LEAST_RECENTLY_VISITED,
        Layout.PathPreference.RANDOM_ANY_STATION);

    /**
     * Where each COVERED_ELSEWHERE rule is actually tested: fully-qualified class name and method name, checked by
     * reflection in testEveryRuleIsCoveredSomewhere, so the covering method has to still exist and still be a @Test.
     */
    private static final java.util.Map<Layout.PathPreference, String[]> COVERED_ELSEWHERE_LOCATION =
        new java.util.EnumMap<>(Layout.PathPreference.class);

    static
    {
        COVERED_ELSEWHERE_LOCATION.put(Layout.PathPreference.LEAST_RECENTLY_VISITED,
            new String[]{"core.testAutoLayout", "testLeastRecentlyVisitedGoesWhereTrainsHaveNotBeen"});

        COVERED_ELSEWHERE_LOCATION.put(Layout.PathPreference.RANDOM_ANY_STATION,
            new String[]{"core.testStationPriorityDistribution",
                "testTheCompletelyRandomRuleIgnoresPriority"});
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();
    }

    // NO TEARDOWN: the rule belongs to the Layout that was asked, so each test builds its own and nothing escapes it.

    /** Fewest sensors takes the two-sensor way; most sensors takes the three-sensor way. */
    @Test
    public void testTheSensorRulesAreMirrors() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        layout.setPathPreference(Layout.PathPreference.FEWEST_POINTS);

        assertEquals(wayTaken(layout, loc), "RP_ViaStation",
            "fewest sensors must take the way across two of them rather than three");

        layout.setPathPreference(Layout.PathPreference.MOST_POINTS);

        assertEquals(wayTaken(layout, loc), "RP_Plain1",
            "most sensors must take the way across three.  A mirror that picks the same route as the "
            + "rule it mirrors is a missing minus sign, and nothing else would ever say so");
    }

    /** Fewest stations takes the way past none; most stations takes the way past one. */
    @Test
    public void testTheStationRulesAreMirrors() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        layout.setPathPreference(Layout.PathPreference.FEWEST_STATIONS);

        assertEquals(wayTaken(layout, loc), "RP_Plain1",
            "fewest stations must take the way that passes none");

        layout.setPathPreference(Layout.PathPreference.MOST_STATIONS);

        assertEquals(wayTaken(layout, loc), "RP_ViaStation",
            "most stations must take the way that calls past one - this is the rule for a layout "
            + "that should look busy, and taking the direct route is the opposite of it");
    }

    /** Shortest track takes the twelve; longest takes the hundred. */
    @Test
    public void testTheLengthRulesAreMirrors() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        layout.setPathPreference(Layout.PathPreference.SHORTEST_LENGTH);

        assertEquals(wayTaken(layout, loc), "RP_Plain1",
            "shortest track must take the 12-long way over the 100-long one");

        layout.setPathPreference(Layout.PathPreference.LONGEST_LENGTH);

        assertEquals(wayTaken(layout, loc), "RP_ViaStation",
            "longest track must take the 100-long way");
    }

    /**
     * A stretch answered 0 counts 0 in the length a route is chosen by, and one nobody measured still counts 1 (Adam:
     * "Yes, count answered 0 as 0").  The floor of 1 keeps an unmeasured railway from tying every route at 0.
     * Two ways to one end: 4 + 0 + 0 + 4 over two hops answered 0, and 9.  Shortest takes the first and longest the
     * second; with the hops unanswered, each counts 1 and the first is 10.
     *
     * MUTATION: count an answered 0 as 1, and this fails.
     */
    @Test
    public void testAnAnsweredZeroCountsNothingInTheLength() throws Exception
    {
        Layout layout = twoWaysAcrossWithAnsweredHops(true);
        Locomotive loc = placedLocomotive(layout);

        layout.setPathPreference(Layout.PathPreference.SHORTEST_LENGTH);

        assertEquals(wayTaken(layout, loc), "RZ_Plain1", "shortest track takes the 9-long way over the one measured 8 -"
            + " its two hops answered 0 are counted as 1 each, as if nobody had measured them");

        layout.setPathPreference(Layout.PathPreference.LONGEST_LENGTH);

        assertEquals(wayTaken(layout, loc), "RZ_Via", "longest track takes the way measured 8 over the 9-long one");

        // CONTROL: the same hops unanswered count 1 each, so that way is 10.
        Layout unanswered = twoWaysAcrossWithAnsweredHops(false);
        Locomotive other = placedLocomotive(unanswered);

        unanswered.setPathPreference(Layout.PathPreference.SHORTEST_LENGTH);

        assertEquals(wayTaken(unanswered, other), "RZ_Via", "CONTROL: two hops nobody measured no longer count 1 each");
    }

    /**
     * Two ways from RP_Start to RZ_End: over RZ_Plain1, RZ_Plain2 and RZ_Plain3, 4 + 0 + 0 + 4 with the two middle hops
     * answered 0 or not; and over RZ_Via, a station no train is sent to, 5 + 4.
     */
    private static Layout twoWaysAcrossWithAnsweredHops(boolean answered) throws Exception
    {
        Layout layout = new Layout(model);

        int base = answered ? 221 : 231;

        String[] names = {"RP_Start", "RZ_Via", "RZ_Plain1", "RZ_Plain2", "RZ_Plain3", "RZ_End"};

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback fb = model.newFeedback(base + i, null);

            model.setFeedbackState(fb.getName(), false);

            boolean station = names[i].equals("RP_Start") || names[i].equals("RZ_Via") || names[i].equals("RZ_End");

            layout.createPoint(names[i], station, fb.getName());
        }

        layout.createEdge("RP_Start", "RZ_Via").setLength(5);
        layout.createEdge("RZ_Via", "RZ_End").setLength(4);

        layout.createEdge("RP_Start", "RZ_Plain1").setLength(4);
        layout.createEdge("RZ_Plain3", "RZ_End").setLength(4);

        for (String[] hop : new String[][] {{"RZ_Plain1", "RZ_Plain2"}, {"RZ_Plain2", "RZ_Plain3"}})
        {
            Edge edge = layout.createEdge(hop[0], hop[1]);

            java.util.List<String> ids = java.util.Arrays.asList("RZ:" + hop[0], "RZ:" + hop[1]);

            edge.setPlaces(ids, java.util.Arrays.asList(0, 0));

            if (answered) edge.setAnsweredPlaces(ids);
        }

        layout.getPoint("RZ_Via").setAutoDestination(false);

        return layout;
    }

    /**
     * Random takes one of the two, and it is a real route either way.  There is nothing to assert about which one, but
     * Random is the DEFAULT, so "picks nothing" would hit every existing railway on upgrade.
     */
    @Test
    public void testRandomStillChoosesARealRoute() throws Exception
    {
        Layout layout = twoWaysAcross();
        Locomotive loc = placedLocomotive(layout);

        layout.setPathPreference(Layout.PathPreference.RANDOM);

        String taken = wayTaken(layout, loc);

        assertTrue("RP_ViaStation".equals(taken) || "RP_Plain1".equals(taken),
            "random must still choose one of the two ways across - it chose: " + taken);
    }

    /**
     * Every rule the enum offers is tested somewhere.  A rule that silently does nothing is invisible, because the user
     * cannot tell "this rule chose that route" from "this rule did not run"; a new rule fails this until it is given a
     * winner above or named as covered elsewhere, with the reason.
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

            // A rule named here is covered by a method in THIS class, which has to exist and still be a @Test.
            if (COVERED_HERE.contains(rule))
            {
                String covering = COVERED_HERE_BY.get(rule);

                assertNotNull(covering, rule + " is claimed as covered here but names no method");

                try
                {
                    java.lang.reflect.Method mine =
                        testRoutePicking.class.getDeclaredMethod(covering);

                    assertNotNull(mine.getAnnotation(Test.class),
                        covering + "() is claimed to cover " + rule + " and is no longer a @Test - "
                        + "so this class reports a coverage it does not have");
                }
                catch (NoSuchMethodException gone)
                {
                    fail(covering + "() is claimed to cover " + rule + " and no longer exists.  "
                        + "Nothing else in the suite tests that rule");
                }
            }

            // A name in COVERED_ELSEWHERE is a claim, not proof: confirm the covering test still exists and is a @Test.
            if (COVERED_ELSEWHERE.contains(rule))
            {
                String[] location = COVERED_ELSEWHERE_LOCATION.get(rule);

                assertNotNull(location,
                    rule + " is claimed as COVERED_ELSEWHERE but COVERED_ELSEWHERE_LOCATION does not "
                    + "say which class and method actually covers it");

                try
                {
                    Class<?> coveringClass = Class.forName(location[0]);
                    java.lang.reflect.Method coveringMethod =
                        coveringClass.getDeclaredMethod(location[1]);

                    assertNotNull(coveringMethod.getAnnotation(Test.class),
                        location[0] + "." + location[1] + "() is claimed to cover " + rule
                        + " but is no longer annotated @Test - the coverage this class claims does "
                        + "not exist any more");
                }
                catch (ClassNotFoundException | NoSuchMethodException e)
                {
                    fail(location[0] + "." + location[1] + "() is claimed to cover " + rule
                        + " but " + e.getClass().getSimpleName() + " says it no longer exists - the "
                        + "coverage this class claims does not exist any more");
                }
            }
        }
    }

    /**
     * With no lengths recorded, an edge counts as one s88 of track (Adam: "where we count each s88 as length 1 by
     * default").  The fixture above cannot test this because every edge in it has a length; with none recorded every
     * route scored zero, tied, and SHORTEST_LENGTH and LONGEST_LENGTH returned the SAME route.
     *
     * BOTH DIRECTIONS, because asserting only that shortest takes the two-edge way is satisfied by a rule that always
     * takes it, including the tie-break that was already doing so.
     */
    @Test
    public void testUnmeasuredTrackCountsOneEachAndStillRanks() throws Exception
    {
        Layout layout = twoWaysAcrossUnmeasured();
        Locomotive loc = placedLocomotive(layout);

        // REPEATED, because the regression it names is settled by a coin toss: without the floor every route ties and
        // the search keeps whichever it reached first (getNeighbors and pickPath shuffle), so one pair of assertions
        // passed about one run in four with the bug present.  Twenty rounds is the number this folder's README settled
        // on.
        for (int round = 0; round < 20; round++)
        {
            layout.setPathPreference(Layout.PathPreference.SHORTEST_LENGTH);

            assertEquals(wayTaken(layout, loc), "RP_ViaStation",
                "round " + round + ": with no lengths anywhere, the two-edge way is the shorter track "
                + "and has to be chosen");

            layout.setPathPreference(Layout.PathPreference.LONGEST_LENGTH);

            assertEquals(wayTaken(layout, loc), "RP_Plain1",
                "round " + round + ": the three-edge way is the longer - if this matches the line "
                + "above, both rules are seeing a tie at zero and neither is ranking anything");
        }
    }

    /** One junction with two ways across it, built fresh so no test can disturb another. */
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

        // A station, but not one autonomy may send a train TO: otherwise "go to RP_ViaStation" is itself a route past
        // no stations, and fewest-stations could pick a nearer destination rather than measure the route.
        layout.getPoint("RP_ViaStation").setAutoDestination(false);

        return layout;
    }

    /**
     * The same two ways across, with no lengths set on anything.  Sensor addresses of its own, because these fixtures
     * share a MarklinControlStation and a reused address would hand back the other fixture's feedback; the POINT names
     * are the same because they live on the Layout.
     */
    private static Layout twoWaysAcrossUnmeasured() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(211, null);
        MarklinFeedback viaStation = model.newFeedback(212, null);
        MarklinFeedback plainOne = model.newFeedback(213, null);
        MarklinFeedback plainTwo = model.newFeedback(214, null);
        MarklinFeedback end = model.newFeedback(215, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{start, viaStation, plainOne, plainTwo, end})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("RP_Start", true, start.getName());
        layout.createPoint("RP_ViaStation", true, viaStation.getName());
        layout.createPoint("RP_End", true, end.getName());

        layout.createPoint("RP_Plain1", false, plainOne.getName());
        layout.createPoint("RP_Plain2", false, plainTwo.getName());

        // Two edges, no lengths.  Two s88 to cross, so two.
        layout.createEdge("RP_Start", "RP_ViaStation");
        layout.createEdge("RP_ViaStation", "RP_End");

        // Three edges, no lengths.  Three.
        layout.createEdge("RP_Start", "RP_Plain1");
        layout.createEdge("RP_Plain1", "RP_Plain2");
        layout.createEdge("RP_Plain2", "RP_End");

        layout.getPoint("RP_ViaStation").setAutoDestination(false);

        return layout;
    }

    /**
     * Start with nothing startable leaves the layout NOT running.  runLocomotives sets `running` before it looks at a
     * locomotive, and both skips (start point inactive, preferred speed outside 1 to 100) return without starting a
     * thread, so nothing ever clears the flag; moveLocomotive, renamePoint and setSimulate then all refuse until Stop
     * is pressed.  A preferred speed of 0 is the state of any locomotive placed without the speed dialog.
     */
    @Test
    public void testStartWithNothingStartableDoesNotLeaveTheLayoutRunning() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = placedLocomotive(layout);

        int was = loc.getPreferredSpeed();

        try
        {
            // The one thing runLocomotives will skip it for.
            loc.setPreferredSpeed(0);

            layout.setLocomotivesToRun(java.util.Arrays.asList(loc));

            layout.runLocomotives();

            // isAutoRunning(), not isRunning(): the latter also answers about threads, which are set from the started
            // thread and race in the control below.  The flag is set and cleared on this thread.
            assertFalse(layout.isAutoRunning(),
                "every locomotive was skipped and the layout is still \"running\", with no thread that "
                + "can ever clear the flag.  Until Stop is pressed, moving a locomotive by hand, "
                + "renaming a point and switching simulation all refuse, and hand dispatches are "
                + "judged by the autonomy-only rules (RC-B5)");
        }
        finally
        {
            loc.setPreferredSpeed(was);

            layout.stopLocomotives();
        }
    }

    /**
     * And a locomotive that CAN start still starts it - the control: "never set running" would turn Start into a no-op.
     */
    @Test
    public void testStartWithSomethingStartableDoesRun() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = placedLocomotive(layout);

        int was = loc.getPreferredSpeed();

        try
        {
            loc.setPreferredSpeed(40);

            layout.setLocomotivesToRun(java.util.Arrays.asList(loc));

            layout.runLocomotives();

            assertTrue(layout.isAutoRunning(),
                "a locomotive that can be started did not leave the layout running, so Start has "
                + "become a no-op - which is the wrong way to satisfy RC-B5");
        }
        finally
        {
            loc.setPreferredSpeed(was);

            layout.stopLocomotives();
        }
    }

    /**
     * A locomotive placed on the graph is given the default speed, as a loaded one already is (MT-233): `parseAuto`
     * applies defaultLocSpeed as it LOADS, a locomotive placed by hand has not been through that path, and Start then
     * refused it.  moveLocomotive is the single door onto the graph (diagram paste, right-click menu, autonomy editor).
     */
    @Test
    public void testAPlacedLocomotiveIsGivenTheDefaultSpeed() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        int was = loc.getPreferredSpeed();

        try
        {
            layout.setDefaultLocSpeed(35);

            // What a locomotive that has never had its speed dialog opened looks like.
            loc.setPreferredSpeed(0);

            assertTrue(layout.moveLocomotive(loc.getName(), "RP_Start", false),
                "precondition: the locomotive could not be placed at all");

            assertEquals(loc.getPreferredSpeed(), 35,
                "a locomotive placed on the graph still has no speed, so Start answers \"Invalid speed "
                + "specified\" instead of running it at the default - which is what the load path has "
                + "always done for a locomotive read from a file (MT-233)");
        }
        finally
        {
            loc.setPreferredSpeed(was);
        }
    }

    /** And a speed already chosen is not overwritten by the default (MT-233): the control for the test above. */
    @Test
    public void testPlacingDoesNotOverwriteASpeedAlreadyChosen() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        int was = loc.getPreferredSpeed();

        try
        {
            layout.setDefaultLocSpeed(35);

            loc.setPreferredSpeed(72);

            assertTrue(layout.moveLocomotive(loc.getName(), "RP_Start", false),
                "precondition: the locomotive could not be placed at all");

            assertEquals(loc.getPreferredSpeed(), 72,
                "placing the locomotive replaced the speed its owner had chosen with the default");
        }
        finally
        {
            loc.setPreferredSpeed(was);
        }
    }

    /**
     * Puts a locomotive at the start and hands it back, WITH NO RECORDED LENGTH on purpose (MT-262): the locomotive
     * comes from the real database, and a length longer than a fixture's edges refuses that fixture's berth, which
     * turns a preference test into a reachability test.  Zero is how the model spells "not recorded".
     */
    private static Locomotive placedLocomotive(Layout layout) throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        loc.setTrainLength(0);

        layout.moveLocomotive(loc.getName(), "RP_Start", false);

        return loc;
    }


    /** Where a chosen route ENDS, as against wayTaken, which names where it goes first. */
    private static String destinationTaken(Layout layout, Locomotive loc)
    {
        List<Edge> path = layout.pickPath(loc);

        assertNotNull(path, "no route was chosen at all");

        return path.get(path.size() - 1).getEnd().getName();
    }
    /** Which way the chosen route went, named by the square it takes first; the routes share their destination. */
    private static String wayTaken(Layout layout, Locomotive loc)
    {
        List<Edge> path = layout.pickPath(loc);

        assertNotNull(path, "no route was chosen at all, and there are two");

        return path.get(0).getEnd().getName();
    }
    /**
     * Station priority weighed against distance can send a train past the higher-priority station (Adam: "one that
     * balances priority vs distance as a ratio").  Every other preference ranks candidates within one priority band, so
     * the fixture has a high-priority station a long way off and an ordinary one close by.
     *
     * BOTH DIRECTIONS, because either alone is satisfied by a constant: under FEWEST_STATIONS (any banded rule) the
     * important station must still win outright; under the ratio the near one must.
     */
    @Test
    public void testStationPriorityIsWeighedAgainstDistance() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = placedLocomotive(layout);

        layout.setPathPreference(Layout.PathPreference.FEWEST_STATIONS);

        // The DESTINATION, not the first hop: wayTaken names the second point, which is the same only for a one-edge
        // route.
        assertEquals(destinationTaken(layout, loc), "RP_Plain1",
            "a banded rule must still take the important station whatever it costs to get there - if "
            + "this fails the fixture is not testing what the ratio is for");

        layout.setPathPreference(Layout.PathPreference.BALANCED_PRIORITY);

        assertEquals(destinationTaken(layout, loc), "RP_ViaStation",
            "the ratio has to be able to prefer a near ordinary station to a distant important one; "
            + "taking the important one anyway means the priority band was never opened");
    }

    /**
     * Priority high enough, and the ratio takes the LONG way, which distance alone never would.  At priority 5 the far
     * station scores (5+1)*1000/18 = 333 against the near one's 500, so a numerator replaced by a constant passes; at 9
     * it scores 555 and wins, and nothing that ignores priority can produce that.
     */
    @Test
    public void testEnoughPriorityOutweighsTheDistance() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = placedLocomotive(layout);

        layout.getPoint("RP_Plain1").setPriority(9);

        layout.setPathPreference(Layout.PathPreference.BALANCED_PRIORITY);

        assertEquals(destinationTaken(layout, loc), "RP_Plain1",
            "priority stopped counting: the far station is worth (9+1)*1000/18 = 555 against the near "
            + "one's (0+1)*1000/2 = 500, so the ratio must take it.  A rule that always takes the "
            + "nearest station gives the same answer as this test's sibling and a different one here, "
            + "which is the whole reason this test exists");
    }

    /**
     * A de-prioritized station is not reached by the longest route available.  `ratioOf` was
     * ((priority + 1) * 1000) / length, and Point.setPriority takes negatives: at -1 the numerator was 0, and at -2 and
     * below dividing by a LARGER length gave a LARGER value, so the most distant station won.
     *
     * Two assertions, because the two halves fail apart: the de-prioritized station must lose to an ordinary one at
     * all, and it must not beat it BY BEING FURTHER.
     */
    @Test
    public void testADePrioritisedStationIsNotReachedByTheLongestRoute() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = placedLocomotive(layout);

        // The FAR station is the one told to go away; under the inversion -1000/18 beat -1000/2, so it won.
        layout.getPoint("RP_Plain1").setPriority(-2);

        layout.setPathPreference(Layout.PathPreference.BALANCED_PRIORITY);

        assertEquals(destinationTaken(layout, loc), "RP_ViaStation",
            "a station the user pushed DOWN to -2 was chosen over an ordinary one, and chosen because "
            + "it was eighteen units away rather than two - a negative numerator makes a longer route "
            + "score higher, so \"go here less\" was read as \"go here the long way round\" (RC-B2)");

        // And the same station, de-prioritized the same amount, must not become MORE attractive as the route grows.
        int near = ratioFor(layout, 2, -2);
        int away = ratioFor(layout, 18, -2);

        assertTrue(near > away,
            "a shorter route to a de-prioritised station must still be worth more than a longer one: "
            + "scored " + near + " at length 2 against " + away + " at length 18 (RC-B2)");

        // AND ORDERED AMONG THEMSELVES, at one distance: without this, replacing the whole weight with a constant
        // passes everything above.  The division is integer, so at the original scale the two tied at zero on long
        // routes.
        int gently = ratioFor(layout, 100, -1);
        int firmly = ratioFor(layout, 100, -20);

        assertTrue(gently > firmly,
            "a station pushed down to -20 is worth the same as one pushed to -1 over the same track: "
            + gently + " against " + firmly + ".  Either the weight is a constant, or the ratio has "
            + "collapsed to zero for both - which is the integer division RC-B8 rescaled for");

        assertTrue(firmly > 0,
            "the ratio for a de-prioritised station has collapsed to zero over an ordinary route, so "
            + "distance has stopped mattering for it - which is the symptom RC-B2 was raised for, at "
            + "priority -1, reappearing at -20 (RC-B8)");
    }

    /**
     * What the ratio makes of a de-prioritized destination at a given distance, asked of the real method (a copy would
     * repeat a sign error).
     */
    private static int ratioFor(Layout layout, int length, int priority) throws Exception
    {
        java.lang.reflect.Method ratioOf =
            Layout.class.getDeclaredMethod("ratioOf", java.util.List.class);
        ratioOf.setAccessible(true);

        org.traincontrol.automation.Edge edge = layout.getEdge("RP_Start", "RP_ViaStation");

        int was = edge.getLength();
        int wasPriority = layout.getPoint("RP_ViaStation").getPriority();

        try
        {
            edge.setLength(length);
            layout.getPoint("RP_ViaStation").setPriority(priority);

            return (int) ratioOf.invoke(layout, java.util.Arrays.asList(edge));
        }
        finally
        {
            edge.setLength(was);
            layout.getPoint("RP_ViaStation").setPriority(wasPriority);
        }
    }

    /**
     * A long way to an important station, and a short way to an ordinary one.  The far station is priority 5 and the
     * near one the default 0: (0+1)*1000/2 = 500 against (5+1)*1000/18 = 333, so priority-per-unit favors the near
     * station while raw priority favors the far one.  The +1 baseline matters: without it the near station scores 0.
     */
    private static Layout importantButFarAway() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(221, null);
        MarklinFeedback nearer = model.newFeedback(222, null);
        MarklinFeedback hopOne = model.newFeedback(223, null);
        MarklinFeedback hopTwo = model.newFeedback(224, null);
        MarklinFeedback far = model.newFeedback(225, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{start, nearer, hopOne, hopTwo, far})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("RP_Start", true, start.getName());

        // the near, ordinary one - reached in one short edge
        layout.createPoint("RP_ViaStation", true, nearer.getName());
        layout.createEdge("RP_Start", "RP_ViaStation").setLength(2);

        // and the important one, four times the track away
        layout.createPoint("RP_Plain2", false, hopOne.getName());
        layout.createPoint("RP_End", false, hopTwo.getName());
        layout.createPoint("RP_Plain1", true, far.getName());

        layout.createEdge("RP_Start", "RP_Plain2").setLength(6);
        layout.createEdge("RP_Plain2", "RP_End").setLength(6);
        layout.createEdge("RP_End", "RP_Plain1").setLength(6);

        layout.getPoint("RP_Plain1").setPriority(5);

        return layout;
    }

}
