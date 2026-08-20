import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for Layout.pickPath - the layer above bfs that decides WHERE a locomotive goes.
 *
 * bfs is handed a specific destination and only works out how to reach it.  pickPath is what chooses
 * the destination: it takes every point, shuffles them, stable-sorts by priority so the highest comes
 * first, and then walks that order looking for one that is a valid destination and that bfs can reach
 * by a path isPathClear accepts.  Station priority lives entirely here, and had no test coverage at all.
 *
 * Like bfs, pickPath is nondeterministic - Layout.getNeighbors shuffles, and pickPath shuffles the
 * candidate destinations before sorting them, so points of EQUAL priority are deliberately tried in a
 * random order.  Every assertion here is therefore either about a strict priority difference (which the
 * sort makes deterministic), or is repeated enough times to cover the possibilities.  Nothing asserts
 * which of two equally ranked destinations wins, because that is defined to be arbitrary.
 *
 * One thing to be aware of when extending this: when pickPath finds nothing it calls
 * loc.delay(minDelay, maxDelay), and those Layout fields are measured in SECONDS.  They default to zero,
 * which is why the negative tests below return immediately - a test that sets them on its layout and
 * then exercises a no-path case would sit there sleeping.
 */
public class testLayoutPickPath
{
    private static MarklinControlStation model;
    private static String destinationS88;

    private static int locCounter = 0;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();

        // Destination points require an s88; pickPath never reads it, so one shared feedback will do.
        // Kept clear so nothing reads as occupied.
        MarklinFeedback feedback = model.newFeedback(47200, null);
        model.setFeedbackState(feedback.getName(), false);

        destinationS88 = feedback.getName();
    }

    /**
     * A locomotive that exists only for this test - not registered in the locomotive database, matching
     * what testAutonomyPathValidation does.  Names are unique because Locomotive equality is by name and
     * pickPath compares the locomotive against the one occupying each point.
     */
    private MarklinLocomotive dummyLoc()
    {
        return new MarklinLocomotive(model, 1, MarklinLocomotive.decoderType.MM2, "PP Loc " + (++locCounter));
    }

    /**
     * START holds the locomotive.  LOW and HIGH are both destinations exactly one edge away, so the only
     * thing that can decide between them is priority - or one of the filters the tests below apply.
     */
    private Layout twoDestinations(Locomotive loc, int lowPriority, int highPriority) throws Exception
    {
        Layout layout = new Layout(model);

        // The start must itself be a destination - pickPath only looks for the locomotive on one
        layout.createPoint("START", true, destinationS88);
        layout.createPoint("LOW", true, destinationS88);
        layout.createPoint("HIGH", true, destinationS88);

        layout.createEdge("START", "LOW");
        layout.createEdge("START", "HIGH");

        layout.getPoint("LOW").setPriority(lowPriority);
        layout.getPoint("HIGH").setPriority(highPriority);

        layout.getPoint("START").setLocomotive(loc);

        return layout;
    }

    /**
     * The point a chosen path ends at, or null if no path was picked.
     */
    private static String destinationOf(List<Edge> path)
    {
        return path == null ? null : path.get(path.size() - 1).getEnd().getName();
    }

    /**
     * A strictly higher priority wins every time, however the candidates were shuffled beforehand.
     */
    @Test(timeOut = 60000)
    public void testHigherPriorityDestinationIsAlwaysChosen() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = twoDestinations(loc, 1, 5);

        for (int attempt = 0; attempt < 30; attempt++)
        {
            assertEquals(destinationOf(layout.pickPath(loc)), "HIGH",
                "attempt " + attempt + ": HIGH outranks LOW and is just as reachable");
        }
    }

    /**
     * Priority outranks distance.  The high priority destination is two edges away and the low priority
     * one is adjacent, and the further one must still be chosen - pickPath walks destinations in
     * priority order and returns the first that yields a clear path, rather than comparing lengths.
     */
    @Test(timeOut = 60000)
    public void testPriorityOutranksDistance() throws Exception
    {
        Locomotive loc = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("START", true, destinationS88);
        layout.createPoint("NEAR", true, destinationS88);
        layout.createPoint("MID", false, null);
        layout.createPoint("FAR", true, destinationS88);

        layout.createEdge("START", "NEAR");
        layout.createEdge("START", "MID");
        layout.createEdge("MID", "FAR");

        layout.getPoint("NEAR").setPriority(1);
        layout.getPoint("FAR").setPriority(9);

        layout.getPoint("START").setLocomotive(loc);

        for (int attempt = 0; attempt < 30; attempt++)
        {
            List<Edge> path = layout.pickPath(loc);

            assertEquals(destinationOf(path), "FAR",
                "attempt " + attempt + ": FAR outranks NEAR even though it is further away");
            assertEquals(path.size(), 2, "the route to FAR runs through MID");
        }
    }

    /**
     * Equal priority is explicitly arbitrary, so neither destination may be starved.  Sixty attempts
     * makes missing one of two vanishingly unlikely if the shuffle is working.
     */
    @Test(timeOut = 60000)
    public void testEquallyRankedDestinationsAreBothReachable() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = twoDestinations(loc, 3, 3);

        Set<String> chosen = new TreeSet<>();

        for (int attempt = 0; attempt < 60; attempt++)
        {
            chosen.add(destinationOf(layout.pickPath(loc)));
        }

        assertEquals(chosen, new TreeSet<>(Arrays.asList("HIGH", "LOW")),
            "with equal priority both destinations should be picked at least once across 60 attempts - "
            + "if only one appears, the shuffle before the priority sort has stopped working");
    }

    /**
     * A destination already holding another locomotive is skipped, even though it outranks the
     * alternative.
     */
    @Test(timeOut = 60000)
    public void testOccupiedDestinationIsSkippedDespitePriority() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = twoDestinations(loc, 1, 5);

        layout.getPoint("HIGH").setLocomotive(dummyLoc());

        for (int attempt = 0; attempt < 20; attempt++)
        {
            assertEquals(destinationOf(layout.pickPath(loc)), "LOW",
                "attempt " + attempt + ": HIGH is occupied, so the lower ranked LOW must be used");
        }
    }

    /**
     * An inactive destination is skipped, even though it outranks the alternative.
     */
    @Test(timeOut = 60000)
    public void testInactiveDestinationIsSkippedDespitePriority() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = twoDestinations(loc, 1, 5);

        layout.getPoint("HIGH").setActive(false);

        for (int attempt = 0; attempt < 20; attempt++)
        {
            assertEquals(destinationOf(layout.pickPath(loc)), "LOW",
                "attempt " + attempt + ": HIGH is inactive, so the lower ranked LOW must be used");
        }
    }

    /**
     * A destination that excludes this particular locomotive is skipped for it, even though it outranks
     * the alternative - and remains available to a locomotive it does not exclude.
     */
    @Test(timeOut = 60000)
    public void testExcludedLocomotiveIsSkippedDespitePriority() throws Exception
    {
        Locomotive excluded = dummyLoc();
        Layout layout = twoDestinations(excluded, 1, 5);

        layout.getPoint("HIGH").getExcludedLocs().add(excluded);

        for (int attempt = 0; attempt < 20; attempt++)
        {
            assertEquals(destinationOf(layout.pickPath(excluded)), "LOW",
                "attempt " + attempt + ": HIGH excludes this locomotive");
        }

        // The exclusion is per locomotive, so a different one may still be sent there
        layout.getPoint("START").setLocomotive(null);

        Locomotive allowed = dummyLoc();
        layout.getPoint("START").setLocomotive(allowed);

        assertEquals(destinationOf(layout.pickPath(allowed)), "HIGH",
            "the exclusion applies only to the locomotive it names");
    }

    /**
     * No route is offered when the locomotive is standing somewhere that is not a destination - pickPath
     * only looks for it on one.
     */
    @Test(timeOut = 30000)
    public void testLocomotiveNotOnADestinationGetsNoPath() throws Exception
    {
        Locomotive loc = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("SIDING", false, null);
        layout.createPoint("HIGH", true, destinationS88);
        layout.createEdge("SIDING", "HIGH");

        layout.getPoint("SIDING").setLocomotive(loc);

        assertNull(layout.pickPath(loc), "the locomotive is not standing on a destination");
    }

    /**
     * A paused locomotive is never given a route, whatever is available.
     */
    @Test(timeOut = 30000)
    public void testPausedLocomotiveGetsNoPath() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = twoDestinations(loc, 1, 5);

        loc.setAutonomyPaused(true);

        try
        {
            assertNull(layout.pickPath(loc), "autonomy is paused for this locomotive");
        }
        finally
        {
            loc.setAutonomyPaused(false);
        }

        assertEquals(destinationOf(layout.pickPath(loc)), "HIGH", "and resumes once unpaused");
    }

    /**
     * The locomotive is not sent to the point it is already standing on, even though that point is
     * itself a destination and there is a route back to it.
     */
    @Test(timeOut = 30000)
    public void testLocomotiveIsNotSentToWhereItAlreadyIs() throws Exception
    {
        Locomotive loc = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("START", true, destinationS88);
        layout.createPoint("LOOP", false, null);

        layout.createEdge("START", "LOOP");
        layout.createEdge("LOOP", "START");

        layout.getPoint("START").setLocomotive(loc);

        assertNull(layout.pickPath(loc),
            "START is the only destination and the locomotive is already on it");
    }

    /**
     * Nothing is offered when the only other destination cannot be reached.
     */
    @Test(timeOut = 30000)
    public void testUnreachableDestinationGivesNoPath() throws Exception
    {
        Locomotive loc = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("START", true, destinationS88);
        layout.createPoint("ISLAND", true, destinationS88);

        layout.getPoint("ISLAND").setPriority(9);
        layout.getPoint("START").setLocomotive(loc);

        assertNull(layout.pickPath(loc), "there is no edge leading to ISLAND");
    }

    /**
     * A reversing station is never chosen as a destination by full autonomy, whatever its priority.
     *
     * Reversing stations are how a layout marks track a train should not be sent to at random - on the
     * author's layout every one of the sixteen is a parking track.  Marking them inactive would keep
     * autonomy out, but it also puts them out of reach of "return home", which is precisely what is
     * meant to fill them at the end of a session.  Excluding them here instead separates the two: the
     * chooser skips them, and every other route to them stays open.
     *
     * The exclusion belongs in pickPath and nowhere else.  isPathClear cannot carry it, because
     * executeTimetable sets running, so isAutoRunning() is true during staging too - a rule fenced
     * that way would refuse the return-home run it exists to permit.
     */
    @Test(timeOut = 60000)
    public void testReversingStationIsNeverChosenAsAnAutonomyDestination() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = twoDestinations(loc, 1, 5);

        layout.getPoint("HIGH").setReversing(true);

        for (int attempt = 0; attempt < 20; attempt++)
        {
            assertEquals(destinationOf(layout.pickPath(loc)), "LOW",
                "attempt " + attempt + ": HIGH reverses, so autonomy must not park a train there "
                    + "even though it outranks LOW");
        }

        // With both reversing there is nowhere left to go, and saying so is the right answer - falling
        // back to one of them would defeat the rule exactly when it matters most.
        layout.getPoint("LOW").setReversing(true);

        assertNull(layout.pickPath(loc),
            "every destination reverses, so full autonomy has nowhere to send this locomotive");
    }

    /**
     * RV-C2: the yield probe must also refuse a locomotive whose only route out crosses a berth.
     *
     * The probe was aligned with pickPath so the two could not disagree, and then berths were barred
     * as intermediates in pickPath alone - so they disagreed again, one commit later, in exactly the
     * way the alignment existed to prevent.  Here the parked locomotive can reach BEYOND, an entirely
     * ordinary station, but only by driving across BERTH; pickPath will therefore never dispatch it,
     * and the probe has to reach the same answer or the false 30-second yields come back.
     */
    @Test(timeOut = 60000)
    public void testYieldingIgnoresALocomotiveWhoseOnlyRouteCrossesABerth() throws Exception
    {
        Locomotive running = dummyLoc();
        Locomotive parked = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("START", true, destinationS88);
        layout.createPoint("MAIN", true, destinationS88);
        layout.createEdge("START", "MAIN");

        layout.createPoint("SIDING", true, destinationS88);
        layout.createPoint("BERTH", true, destinationS88);
        layout.createPoint("BEYOND", true, destinationS88);
        layout.createEdge("SIDING", "BERTH");
        layout.createEdge("BERTH", "BEYOND");

        layout.getPoint("START").setLocomotive(running);
        layout.getPoint("SIDING").setLocomotive(parked);

        layout.setLocomotivesToRun(Arrays.asList(running, parked));

        Thread.sleep(30);
        running.incrementNumPaths();

        assertEquals(layout.checkForSlowerLoc(0, running), parked,
            "control: while BERTH is an ordinary station, BEYOND is genuinely reachable and the "
                + "parked locomotive is worth yielding to");

        layout.getPoint("BERTH").setReversing(true);

        assertNull(layout.checkForSlowerLoc(0, running),
            "BERTH is now a berth, so BEYOND is reachable only across one and BERTH itself is barred "
                + "as a destination - pickPath will never dispatch this locomotive, and the probe "
                + "that decides yields must agree with it");
    }

    /**
     * A reversing STATION is off the through-network too, not merely off the destination list.
     *
     * Barring berths as destinations left them usable as waypoints, so autonomy still routed through
     * the parking area - and because executePathInternal reverses the train at every reversing point
     * it reaches, a train bound for somewhere else would stop and change direction inside a berth on
     * the way.  Reported from the layout as "BottomMainA to TopMainR2 via TunnelLongPark".
     *
     * The control assertion runs first: while the middle point is an ordinary station, the far
     * destination must be reachable through it, so a fix that simply refused every multi-edge path
     * could not pass this by doing nothing.
     */
    @Test(timeOut = 60000)
    public void testAPathThroughAReversingStationIsNotChosen() throws Exception
    {
        Locomotive loc = dummyLoc();

        Layout layout = new Layout(model);

        // The only way to FAR runs through BERTH.  FAR outranks BERTH so the walk tries it first.
        layout.createPoint("START", true, destinationS88);
        layout.createPoint("BERTH", true, destinationS88);
        layout.createPoint("FAR", true, destinationS88);

        layout.createEdge("START", "BERTH");
        layout.createEdge("BERTH", "FAR");

        layout.getPoint("BERTH").setPriority(1);
        layout.getPoint("FAR").setPriority(5);
        layout.getPoint("START").setLocomotive(loc);

        assertEquals(destinationOf(layout.pickPath(loc)), "FAR",
            "control: an ordinary station may be driven through to reach what lies beyond it");

        layout.getPoint("BERTH").setReversing(true);

        // BERTH is now barred as a destination, and FAR is only reachable by driving through it.
        assertNull(layout.pickPath(loc),
            "a parking berth is not through-traffic: autonomy must not route a train across one, "
                + "which would stop and reverse it inside the parking area en route somewhere else");
    }

    /**
     * A reversing NON-station stays usable as an intermediate - that is what it is for.
     *
     * Reversing loops and headshunts are non-stations carrying the reversing flag, and the mid-path
     * direction change is their entire purpose.  Only berths - reversing points that are also
     * stations - are barred, so this guards the distinction the rule rests on.
     */
    @Test(timeOut = 60000)
    public void testAPathThroughAReversingPointIsStillChosen() throws Exception
    {
        Locomotive loc = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("START", true, destinationS88);
        layout.createPoint("LOOP", false, null);
        layout.createPoint("FAR", true, destinationS88);

        layout.createEdge("START", "LOOP");
        layout.createEdge("LOOP", "FAR");

        layout.getPoint("LOOP").setReversing(true);
        layout.getPoint("START").setLocomotive(loc);

        assertTrue(layout.getPoint("LOOP").isReversing() && !layout.getPoint("LOOP").isDestination(),
            "precondition: LOOP must be a reversing point rather than a reversing station");

        assertEquals(destinationOf(layout.pickPath(loc)), "FAR",
            "a reversing loop is meant to be driven through - only berths are barred");
    }

    /**
     * The fairness yield must not wait for a locomotive full autonomy will never dispatch.
     *
     * checkForSlowerLoc lets a finishing locomotive pause for YIELD_SECONDS so a longer-idle one can
     * claim a path first.  It decided that with getPossiblePaths - the MANUAL tier, which offers
     * reversing stations and destinations that exclude the locomotive.  A train parked on purpose
     * answers "yes, I could go somewhere" to that question and "no, autonomy will never send me" to
     * the one that matters, and because its idle time only grows it wins every later comparison too:
     * every running train stops 30 seconds for it, indefinitely.
     *
     * The control assertion comes first on purpose.  The obvious wrong fix - filtering so hard that
     * nothing is ever worth yielding to - would silently disable the fairness feature altogether, and
     * a test that only checked the null case would call that a pass.
     */
    @Test(timeOut = 60000)
    public void testYieldingIgnoresALocomotiveAutonomyWillNeverDispatch() throws Exception
    {
        Locomotive running = dummyLoc();
        Locomotive parked = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("START", true, destinationS88);
        layout.createPoint("MAIN", true, destinationS88);
        layout.createEdge("START", "MAIN");

        layout.createPoint("SIDING", true, destinationS88);
        layout.createPoint("PARK", true, destinationS88);
        layout.createEdge("SIDING", "PARK");

        layout.getPoint("START").setLocomotive(running);
        layout.getPoint("SIDING").setLocomotive(parked);

        layout.setLocomotivesToRun(Arrays.asList(running, parked));

        // The parked one has to be the longest idle, or it is not the locomotive under consideration
        // at all and every assertion below would pass without testing anything.
        Thread.sleep(30);
        running.incrementNumPaths();

        assertEquals(layout.checkForSlowerLoc(0, running), parked,
            "control: an idle locomotive with a real destination is still worth yielding to");

        // Now its only destination is one autonomy refuses to choose.
        layout.getPoint("PARK").setReversing(true);

        assertNull(layout.checkForSlowerLoc(0, running),
            "a locomotive whose only destination reverses is parked, not waiting - yielding to it "
                + "stops every running train for YIELD_SECONDS on a dispatch that never comes");
    }

    /**
     * The rule restricts arrivals only: a train standing on an active reversing station still departs.
     *
     * Autonomy must never SEND a train to a parking track, but a train already on one is free to leave
     * under its own power, exactly as it would from any other active station.  Holding a train in place
     * is what deactivating the point is for - the two settings do different jobs, and this asserts they
     * stay separate.
     *
     * Automation.md used to say paths starting from a reversing station were never chosen either.  That
     * sentence was wrong rather than unimplemented, and was corrected alongside this test.
     */
    @Test(timeOut = 60000)
    public void testALocomotiveStandingOnAReversingStationStillDeparts() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = twoDestinations(loc, 1, 5);

        layout.getPoint("START").setReversing(true);

        assertEquals(destinationOf(layout.pickPath(loc)), "HIGH",
            "a locomotive standing on an active reversing station is still dispatched from it");
    }

    /**
     * The manual route menu is a different tier from full autonomy, and keeps offering the station.
     *
     * getPossiblePaths is what the right-click menu and the locomotive status panel enumerate, and the
     * user may still send a train to a reversing station by hand.  This guard is what stops the
     * exclusion from being "simplified" down into isPathClear or bfs later, which would take the manual
     * route and the staging run with it.
     */
    /**
     * But a train that cannot reverse is refused it, by hand as well as by autonomy.
     *
     * The one deliberate narrowing of the tiering the test below protects. Adam watched two
     * non-reversible trains turned round on an ordinary platform and asked for a rule: such a train
     * may only be turned somewhere that is out of full autonomy - a parking road, a shed, a staging
     * siding - and never at a station where it would then run backwards in service.
     *
     * That is a fact about the train and the place, not about which menu the move came from, so it
     * lives in isPathClear where every tier passes through. The escape hatch is the place itself: a
     * point excluded from full autonomy is a parking spot, and turning a train there is allowed.
     */
    @Test(timeOut = 60000)
    public void testAReversingStationIsRefusedToATrainThatCannotReverse() throws Exception
    {
        Locomotive loc = dummyLoc();

        loc.setReversible(false);

        Layout layout = twoDestinations(loc, 1, 5);

        layout.getPoint("HIGH").setReversing(true);

        Set<String> offered = new TreeSet<>();

        for (List<Edge> path : layout.getPossiblePaths(loc, true))
        {
            offered.add(destinationOf(path));
        }

        assertFalse(offered.contains("HIGH"),
            "a train that cannot reverse was offered a station that would turn it round.  Doing that "
            + "at an ordinary platform is a train running backwards in service, which is the whole "
            + "of what the non-reversible mark is for - offered: " + offered);
    }

    @Test(timeOut = 60000)
    public void testAReversingStationRemainsManuallySelectable() throws Exception
    {
        Locomotive loc = dummyLoc();

        // Reversible, explicitly.  A train that CANNOT reverse is now refused a station that would
        // turn it round, at every tier and on purpose - see the test below.  This one is about the
        // tiering and has to say which kind of train it is asking about, or it is really asking two
        // questions and answering neither.
        loc.setReversible(true);

        Layout layout = twoDestinations(loc, 1, 5);

        layout.getPoint("HIGH").setReversing(true);

        Set<String> offered = new TreeSet<>();

        for (List<Edge> path : layout.getPossiblePaths(loc, true))
        {
            offered.add(destinationOf(path));
        }

        assertTrue(offered.contains("HIGH"),
            "a reversing station must stay reachable by hand - offered: " + offered);
    }

    /**
     * A straight run of stations, every one of them active and clear.  Returned as a path so the
     * isPathClear rules below can be exercised against it directly rather than through pickPath, whose
     * own destination filters would answer first and hide which layer actually refused.
     */
    private Layout threeInARow(Locomotive loc) throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("PC_A", true, destinationS88);
        layout.createPoint("PC_B", true, destinationS88);
        layout.createPoint("PC_C", true, destinationS88);

        layout.createEdge("PC_A", "PC_B");
        layout.createEdge("PC_B", "PC_C");

        layout.getPoint("PC_A").setLocomotive(loc);

        return layout;
    }

    private static List<Edge> wholeRun(Layout layout)
    {
        return Arrays.asList(layout.getEdge("PC_A -> PC_B"), layout.getEdge("PC_B -> PC_C"));
    }

    /**
     * An inactive point may never be driven through, by autonomy or by a route picked by hand.
     *
     * This is the absolute half of the inactive rule, and the only absolute one.  Switching a point off
     * says trains should not run over it; a manual route that crossed it anyway would be the one case
     * where nobody chose that point at all - the route was merely trying to get past it.  The two
     * ENDPOINT rules are deliberately softer and are pinned separately below: a manual route may still
     * start from a deactivated point and still finish on one.
     *
     * Note this exercises the gate itself.  testInactiveDestinationIsSkippedDespitePriority looks like
     * coverage of the same rule but is not: it goes through pickPath, whose own end.isActive() filter
     * answers first, with running false so this gate never runs.
     */
    @Test(timeOut = 60000)
    public void testAnInactiveIntermediateIsRefusedEvenByAManuallyPickedRoute() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = threeInARow(loc);

        assertTrue(layout.isPathClear(wholeRun(layout), loc),
            "control: the run is clear while every point is active");

        layout.getPoint("PC_B").setActive(false);

        assertFalse(layout.isAutoRunning(), "precondition: autonomy is stopped, so no fence applies");

        assertFalse(layout.isPathClear(wholeRun(layout), loc),
            "a switched-off point may not be driven through even by a route the user picks - passage "
                + "is the one part of the inactive rule that is not fenced behind autonomy");

        layout.setLocomotivesToRun(new LinkedList<>());
        layout.runLocomotives();

        try
        {
            assertFalse(layout.isPathClear(wholeRun(layout), loc),
                "and it is refused under autonomy too, where the fenced rule catches it as well");
        }
        finally
        {
            layout.stopLocomotives();
        }
    }

    /**
     * An inactive DESTINATION is refused to autonomy but allowed to a route picked by hand.
     *
     * This asymmetry against the intermediate rule above is the point of the pair, not an oversight.
     * Sending a train to a switched-off station is a deliberate act - it is how a train is put away on
     * a berth that autonomy is meant to leave alone - whereas crossing one is never chosen for its own
     * sake.  Deactivating a station therefore keeps autonomy out without putting it out of the
     * operator's reach.
     *
     * A note on which rule fires: isPathClear carries a later check reporting
     * errorInactiveStationInAutoRun, which names the offending station, but it cannot be reached.  The
     * fenced loop above it tests every edge END, and the last of those IS the destination, so the
     * generic errorInactivePointInAutoRun always wins.  Behaviour is asserted here - refused either
     * way - with the shadowing recorded so nobody assumes an operator has seen the friendlier message.
     */
    @Test(timeOut = 60000)
    public void testAnInactiveDestinationIsRefusedOnlyWhileAutonomyRuns() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = threeInARow(loc);

        layout.getPoint("PC_C").setActive(false);

        assertTrue(layout.isPathClear(wholeRun(layout), loc),
            "a route to a switched-off station may still be picked by hand - this is what makes a "
                + "deactivated berth reachable at all");

        layout.setLocomotivesToRun(new LinkedList<>());
        layout.runLocomotives();

        try
        {
            assertFalse(layout.isPathClear(wholeRun(layout), loc),
                "autonomy may not send a train to an inactive station");
        }
        finally
        {
            layout.stopLocomotives();
        }
    }

    /**
     * Autonomy may not set off from a point that is not a station, and that rule is fenced too.
     *
     * Reaching a non-station is ordinary - trains drive through them constantly - but starting a path
     * on one is not, because nothing put the train there under autonomy.  Manual routes may still do
     * it, which is what the fence preserves.
     */
    @Test(timeOut = 60000)
    public void testAPathStartingAtANonStationIsRefusedOnlyWhileAutonomyRuns() throws Exception
    {
        Locomotive loc = dummyLoc();

        Layout layout = new Layout(model);

        layout.createPoint("PC_A", false, null);
        layout.createPoint("PC_B", true, destinationS88);
        layout.createPoint("PC_C", true, destinationS88);

        layout.createEdge("PC_A", "PC_B");
        layout.createEdge("PC_B", "PC_C");

        layout.getPoint("PC_A").setLocomotive(loc);

        assertTrue(layout.isPathClear(wholeRun(layout), loc),
            "control: with autonomy stopped, a route may be picked by hand from a non-station");

        layout.setLocomotivesToRun(new LinkedList<>());
        layout.runLocomotives();

        try
        {
            assertFalse(layout.isPathClear(wholeRun(layout), loc),
                "autonomy must not set off from a point that is not a station");
        }
        finally
        {
            layout.stopLocomotives();
        }
    }

    /**
     * A terminus may only be the END of a path, never a point driven through.
     *
     * Unlike the three rules above this one is NOT fenced behind isAutoRunning: a terminus is a dead
     * end whatever asked for the route, so a manual route and a staging move are refused as well.  The
     * control asserts the permitted case in the same graph, so the test cannot pass by refusing
     * everything that mentions a terminus.
     */
    @Test(timeOut = 60000)
    public void testATerminusMayNotBeDrivenThrough() throws Exception
    {
        Locomotive loc = dummyLoc();
        Layout layout = threeInARow(loc);

        // isPathClear refuses a terminus DESTINATION to a non-reversible locomotive - a separate rule.
        // Made true so the control below tests the through-rule and not that one.
        loc.setReversible(true);

        layout.getPoint("PC_B").setTerminus(true);

        assertFalse(layout.isPathClear(wholeRun(layout), loc),
            "a terminus in the middle of a path is a dead end, not a through point");

        assertTrue(layout.isPathClear(Arrays.asList(layout.getEdge("PC_A -> PC_B")), loc),
            "control: the same terminus is a perfectly good place to finish");
    }
}
