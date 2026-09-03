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

    /**
     * Every rule this class chooses a winner for, and WHICH method does it (TSX-C8).
     *
     * This was a bare set, which is a comment: deleting `testTheSensorRulesAreMirrors` left
     * `FEWEST_POINTS` and `MOST_POINTS` tested by nothing anywhere in the suite, and
     * `testEveryRuleIsCoveredSomewhere` went on reporting both as covered because they were still in
     * the set.  The far half of this index was given a reflective check on 2026-09-01 for exactly that
     * reason, in words that are every bit as true of the near half: "naming a rule used to be nothing
     * more than a comment - true today, but nothing would notice if the method were renamed."
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
     *
     * LEAST_RECENTLY_VISITED ranks DESTINATIONS by when a train last arrived at them, so it cannot be
     * told apart on a fixture whose routes all end in the same place. It has its own test in
     * testAutoLayout, on a fixture with two destinations and an arrival recorded at one of them.
     *
     * RANDOM_ANY_STATION cannot be told apart by picking one winner at all: it differs from RANDOM
     * only in WHICH stations are eligible, and this fixture's routes share their destination. It is
     * measured instead - a distribution over 400 decisions per case, on the frozen snapshot of the
     * operator's railway, where a train leaving Tunnel can reach three platforms with priorities the
     * test varies (MT-231, OB-156).
     */
    private static final Set<Layout.PathPreference> COVERED_ELSEWHERE = EnumSet.of(
        Layout.PathPreference.LEAST_RECENTLY_VISITED,
        Layout.PathPreference.RANDOM_ANY_STATION);

    /**
     * Where each COVERED_ELSEWHERE rule is actually tested: fully-qualified class name and method
     * name, checked by reflection in testEveryRuleIsCoveredSomewhere.
     *
     * Naming a rule in COVERED_ELSEWHERE used to be nothing more than a comment - true today, but
     * nothing would notice if the file it points at were deleted or the method renamed. This map is
     * what turns that into a check: the covering method has to still exist, and still be a @Test,
     * or testEveryRuleIsCoveredSomewhere fails instead of continuing to claim the coverage is there.
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
        model = init(null, true, false, false, false);
    }

    // NO TEARDOWN ANY MORE, and the comment that was here is the reason it is gone: "Static and
    // process-wide: left set, it would change how every later class in this JVM routes."  The rule now
    // belongs to the Layout that was asked, so each test builds its own and nothing escapes it.

    /**
     * Fewest sensors takes the two-sensor way; most sensors takes the three-sensor way.
     */
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

    /**
     * Fewest stations takes the way past none; most stations takes the way past one.
     */
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

    /**
     * Shortest track takes the twelve; longest takes the hundred.
     */
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

        layout.setPathPreference(Layout.PathPreference.RANDOM);

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

            // AND THE SAME FOR THE NEAR HALF (TSX-C8).  A rule named here is covered by a method in
            // THIS class, and that method has to exist and still be a @Test - otherwise deleting it
            // leaves the claim standing on nothing, which is what happened to `TST-C4` before.
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

            // A name in COVERED_ELSEWHERE is a claim, not proof.  Reflectively confirm the covering
            // test still exists and is still a @Test, so deleting or weakening it away fails THIS
            // test instead of leaving the claim of coverage standing on nothing.
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
     * With no lengths recorded, an edge counts as one s88 of track (Adam).
     *
     * "a min length option that tries to minimize total track length, where we count each s88 as
     * length 1 by default."
     *
     * THE FIXTURE ABOVE CANNOT TEST THIS, because every edge in it has a length. On a railway where
     * none do - which is most of Adam's derived graph, 114 edges of 132 - lengthOf summed to zero for
     * every route, every route tied, and the tie went to whichever the search happened to reach first.
     * So SHORTEST_LENGTH and LONGEST_LENGTH returned the SAME route, which is what "the setting does
     * nothing" looks like from outside, and is exactly what the parity probe found on the real layout.
     *
     * BOTH DIRECTIONS, deliberately. Asserting only that shortest takes the two-edge way is satisfied
     * by a rule that always takes it - including by the tie-break that was already doing so. The pair
     * only passes if the two rules can be told apart.
     */
    @Test
    public void testUnmeasuredTrackCountsOneEachAndStillRanks() throws Exception
    {
        Layout layout = twoWaysAcrossUnmeasured();
        Locomotive loc = placedLocomotive(layout);

        // REPEATED, because the regression it names is settled by a coin toss (LE2-C14).
        //
        // Without the floor every route scores 0, every route ties, and `cost < bestCost` keeps
        // whichever the search reached first - which getNeighbors and pickPath both shuffle. A single
        // pair of assertions therefore passed about one run in four with the bug present, which reads
        // as protection and is not. Twenty rounds is the number this folder's README settled on after
        // a guard was measured at 247 catches in 500.
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
     * The same two ways across, with no lengths set on anything.
     *
     * Sensor addresses of its own rather than the ones twoWaysAcross uses: these fixtures share a
     * MarklinControlStation, and reusing an address would hand back the feedback the other fixture
     * already built. The POINT names are deliberately the same - they live on the Layout rather than
     * the model, so there is no clash, and placedLocomotive puts the train on "RP_Start".
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
     * Start with nothing startable leaves the layout NOT running (RC-B5).
     *
     * runLocomotives sets `running` before it looks at a single locomotive, and both of its skips -
     * start point inactive, preferred speed outside 1 to 100 - return out of the forEach without
     * starting a thread.  Skip every locomotive and the flag is set with nothing that will ever clear
     * it: announceRunFinished is only reached from a thread decrement that never happens.
     *
     * What that costs while it lasts: moveLocomotive, renamePoint and setSimulate all refuse, every
     * protecting signal on the layout has been commanded, and isPathClear starts applying the
     * autonomy-only rules to hand dispatches.  Recoverable only by pressing Stop, which is why this is
     * a B and not an A - and why nothing tells the user to.
     *
     * executeTimetableInternal guards exactly this and says so; runLocomotives did not inherit it.
     *
     * A preferred speed of 0 is not exotic: it is the state of any locomotive that has been placed on
     * the graph without the speed dialog ever being opened.
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

            // isAutoRunning(), not isRunning().  isRunning() is `running || anything active ||
            // any thread alive`, so it answers about three things and RC-B5 is about one of them -
            // and the extra two are set from the started thread, which is a race in the control
            // below.  The flag is set and cleared on this thread, so there is nothing to wait for.
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
     * And a locomotive that CAN start still starts it - the control (RC-B5).
     *
     * Without this, "never set running" passes the test above and turns Start into a no-op.
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
     * A locomotive placed on the graph is given the default speed, as a loaded one already is (MT-233).
     *
     * Adam: "added MT-233 Test Loc.  After initial placement, error: Invalid speed specified.  Instead
     * of default speed.  It was added via control+V on the track diagram viewer."
     *
     * parseAuto applies defaultLocSpeed as it LOADS, so anything out of a file has a speed - and a
     * locomotive placed by hand has not been through that path.  Start then refused it, which is
     * runLocomotive's own guard doing its job on a locomotive nothing had given a speed to.
     *
     * moveLocomotive is the single door onto the graph: the diagram paste, the right-click menu and the
     * autonomy editor all arrive here.
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

    /**
     * And a speed already chosen is not overwritten by the default (MT-233).
     *
     * The control.  Without it, "always set the default on placement" passes the test above and quietly
     * throws away every speed the user has ever picked.
     */
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
     * Puts a locomotive at the start and hands it back.
     */
    private static Locomotive placedLocomotive(Layout layout) throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "RP_Start", false);

        return loc;
    }


    /**
     * Where a chosen route ENDS, as against wayTaken, which names where it goes first.
     */
    private static String destinationTaken(Layout layout, Locomotive loc)
    {
        List<Edge> path = layout.pickPath(loc);

        assertNotNull(path, "no route was chosen at all");

        return path.get(path.size() - 1).getEnd().getName();
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
    /**
     * Station priority weighed against distance can send a train past the higher-priority station.
     *
     * Adam: "one that balances priority vs distance as a ratio."
     *
     * THE POINT OF THE RULE IS THE BAND IT CROSSES. Every other preference ranks candidates within one
     * priority band, because pickPath settles the highest band before looking at the next - which is
     * why "the highest priority available station" needed no rule of its own, it is the behaviour. So
     * the fixture here is built to make the two answers differ: a high-priority station a long way
     * off, and an ordinary one close by.
     *
     * BOTH DIRECTIONS, because either alone is satisfied by a constant. Under FEWEST_STATIONS - any
     * banded rule - the important station must still win outright; under the ratio the near one must.
     * A rule that always took the near one would pass the second assertion and fail the first.
     */
    @Test
    public void testStationPriorityIsWeighedAgainstDistance() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = placedLocomotive(layout);

        layout.setPathPreference(Layout.PathPreference.FEWEST_STATIONS);

        // The DESTINATION, not the first hop.  wayTaken names the second point of the route, which is
        // the same thing only when the route is one edge long - and the whole fixture here is that one
        // of them is not.
        assertEquals(destinationTaken(layout, loc), "RP_Plain1",
            "a banded rule must still take the important station whatever it costs to get there - if "
            + "this fails the fixture is not testing what the ratio is for");

        layout.setPathPreference(Layout.PathPreference.BALANCED_PRIORITY);

        assertEquals(destinationTaken(layout, loc), "RP_ViaStation",
            "the ratio has to be able to prefer a near ordinary station to a distant important one; "
            + "taking the important one anyway means the priority band was never opened");
    }

    /**
     * Priority high enough, and the ratio takes the LONG way - which distance alone never would.
     *
     * The sibling above cannot tell the ratio from plain 1/distance: at priority 5 the far station
     * scores (5+1)*1000/18 = 333 against the near one's 500, so both rules choose the near station and
     * a numerator replaced by a constant passes.  That was the hole the suite review found, and it is
     * the hole the negative-priority inversion (RC-B2) sat in.
     *
     * At 9 the far station scores (9+1)*1000/18 = 555 and wins.  Nothing that ignores priority can
     * produce that answer, because the near station is closer under every measure.
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
     * A de-prioritised station is not reached by the longest route available (RC-B2).
     *
     * ratioOf was ((priority + 1) * 1000) / length, reasoned about only from a default of zero.  Point.
     * setPriority takes negatives and the editor calls them perfectly valid, so at -1 the numerator was
     * 0 - every route to that station scored the same and distance stopped mattering - and at -2 and
     * below the numerator was negative, where dividing by a LARGER length gives a LARGER value.  Among
     * de-prioritised stations the rule therefore chose the most distant one it could find.
     *
     * Two assertions, because the two halves fail apart: the de-prioritised station must lose to an
     * ordinary one at all, and - the inversion itself - it must not beat it BY BEING FURTHER.
     */
    @Test
    public void testADePrioritisedStationIsNotReachedByTheLongestRoute() throws Exception
    {
        Layout layout = importantButFarAway();
        Locomotive loc = placedLocomotive(layout);

        // The FAR station is the one told to go away.  Under the inversion that made it the winner:
        // -2 gives a numerator of -1000, and -1000/18 is greater than -1000/2, so the longest route to
        // the least wanted station scored highest of anything on the layout.
        layout.getPoint("RP_Plain1").setPriority(-2);

        layout.setPathPreference(Layout.PathPreference.BALANCED_PRIORITY);

        assertEquals(destinationTaken(layout, loc), "RP_ViaStation",
            "a station the user pushed DOWN to -2 was chosen over an ordinary one, and chosen because "
            + "it was eighteen units away rather than two - a negative numerator makes a longer route "
            + "score higher, so \"go here less\" was read as \"go here the long way round\" (RC-B2)");

        // And the same station, de-prioritised the same amount, must not become MORE attractive as the
        // route to it grows.  This is the inversion stated on its own, without a rival station.
        int near = ratioFor(layout, 2, -2);
        int away = ratioFor(layout, 18, -2);

        assertTrue(near > away,
            "a shorter route to a de-prioritised station must still be worth more than a longer one: "
            + "scored " + near + " at length 2 against " + away + " at length 18 (RC-B2)");

        // AND ORDERED AMONG THEMSELVES, at one distance (RC-B2, RC-B8).
        //
        // The fix says a station pushed further down is worth less.  Without this, replacing the whole
        // weight with a constant passes everything above - the inversion is gone and so is the reason
        // for having a weight at all, and "-20 is visited as often as -1" is invisible.
        //
        // It is also what RC-B8 was about: at the original scale the two tied at zero on any route
        // longer than a few hundred, because the division is integer.
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
     * What the ratio makes of a de-prioritised destination at a given distance.
     *
     * Asked of the real method rather than recomputed here, which is the point: a copy of the
     * arithmetic in the test would agree with whatever the arithmetic says, including a sign error.
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
     * A long way to an important station, and a short way to an ordinary one.
     *
     * Priorities: the far station is 5, the near one the default of 0. The near one therefore scores
     * (0+1)*1000/2 = 500 and the far one (5+1)*1000/18 = 333, so priority-per-unit favours the near
     * station while raw priority favours the far one - which is the disagreement the test needs.
     *
     * The baseline in that arithmetic is not decoration: without it the near station scores 0/2 = 0
     * and could never win, whatever the distance.
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
