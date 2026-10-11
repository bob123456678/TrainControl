package core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A locomotive excluded from a point is not sent through it, or to it.
 *
 * Adam asked for these: "make sure we have test cases for locomotive exclusions - both on points that
 * are intermediate, and stations - that paths that try to send a locomotive down those are refused."
 * There were none. `testAutoLayout` touches `excludedLocs` only where it checks that a capture keeps
 * them; nothing exercised what they DO.
 *
 * THE TWO RULES ARE NOT THE SAME RULE, and a test that assumed they were would have been asserting a
 * behaviour this layout deliberately does not have:
 *
 *   - An excluded **intermediate** point cannot be TRAVERSED. `isPathClear` refuses any path whose
 *     interior includes it.
 *   - An excluded **station** cannot be STOPPED AT. It is checked against a path's destination, and
 *     the locomotive may still drive straight through it.
 *
 * That asymmetry is deliberate and expensive to get wrong. The comment at `Layout.isPathClear` records
 * that blocking passage through excluded stations too was tried and reverted, because on Adam's own
 * railway it removed 45% of the reachable station pairs for two locomotives - he uses station
 * exclusions on through routes. So the third test here is the one that matters most: it pins the
 * passage that is allowed, and it is what goes red if somebody "tidies" the two rules into one.
 */
public class testLocomotiveExclusions
{
    private static MarklinControlStation model;

    /**
     * The sandbox, opened BEFORE the model and held for the class.
     *
     * init() loads whatever layout the machine's preferences point at, which on Adam's machine is his
     * real railway - and the suite has a ratchet counting the classes that do this without a sandbox,
     * which is how this one was caught before it ever ran on his layout. Opened first, because a
     * sandbox after the model protects nothing.
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
    }

    @org.testng.annotations.AfterClass
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * A locomotive excluded from an intermediate point is not routed through it.
     */
    @Test
    public void testAnExcludedIntermediatePointIsNotTraversed() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive loc = placed(layout);

        // Both ways are open to begin with, which is the precondition that makes the exclusion below
        // mean something: without it, "no route through EX_Middle" could be true because there never
        // was one.
        assertTrue(usesPoint(layout.getPossiblePaths(loc, false), "EX_Middle"),
            "the fixture does not route through EX_Middle at all, so excluding it would prove nothing");

        layout.getPoint("EX_Middle").setExcludedLocs(new HashSet<>(Arrays.asList(loc)));

        assertFalse(usesPoint(layout.getPossiblePaths(loc, false), "EX_Middle"),
            "a locomotive excluded from an intermediate point was still routed through it - the "
            + "exclusion on a non-station means it may not pass, not merely may not stop");
    }

    /**
     * A locomotive excluded from a station is not sent there.
     *
     * Asked of pickPath, which is where autonomy CHOOSES, and not of getPossiblePaths. That method
     * filters only on "is a destination" and "is something standing there" - it does not apply
     * exclusions, and deliberately: it feeds the right-click menu, where the operator is allowed to
     * send a train somewhere autonomy would not pick. The tiers are different on purpose.
     *
     * EX_Far is the only place autonomy may choose here, so refusing it means refusing everything and
     * the assertion is a null rather than a guess about which of several routes came up.
     */
    @Test
    public void testAnExcludedStationIsNotChosenAsADestination() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive loc = placed(layout);

        assertNotNull(layout.pickPath(loc),
            "the fixture offers autonomy nothing at all, so excluding a station would prove nothing");

        layout.getPoint("EX_Far").setExcludedLocs(new HashSet<>(Arrays.asList(loc)));

        assertNull(layout.pickPath(loc),
            "a locomotive excluded from the only station it could be sent to was still sent there");
    }

    /**
     * ...and may still drive THROUGH an excluded station, which is the half that is easy to break.
     *
     * `Layout.isPathClear` refuses an excluded INTERMEDIATE point and deliberately does not refuse an
     * excluded station in the middle of a path. Its comment records what enforcing both cost when it
     * was tried: 45% of the reachable station pairs for two locomotives on Adam's own railway, because
     * he uses station exclusions on through routes.
     *
     * So this is not a test of a nicety. It is the guard against the two rules being merged, and it
     * goes red the moment somebody decides the asymmetry looks like an oversight.
     *
     * EX_Through is a station - so the station rule is the one that applies to it - but not one
     * autonomy may choose, so the only thing its exclusion can affect here is passage.
     */
    @Test
    public void testAnExcludedStationMayStillBeDrivenThrough() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive loc = placed(layout);

        layout.getPoint("EX_Through").setAutoDestination(false);

        assertNotNull(layout.pickPath(loc),
            "the fixture cannot reach EX_Far past EX_Through, so this proves nothing");

        layout.getPoint("EX_Through").setExcludedLocs(new HashSet<>(Arrays.asList(loc)));

        assertNotNull(layout.pickPath(loc),
            "excluding a STATION stopped the locomotive driving through it to somewhere else. That is "
            + "the change that was tried and reverted: on Adam's railway it removed 45% of the "
            + "reachable station pairs, because he uses station exclusions on through routes");
    }

    /**
     * Excluding ONE locomotive does not shut the point to the others (A113).
     *
     * Every test above asks about a single locomotive, which makes "excluded" and "anybody is
     * excluded" indistinguishable: replace `getExcludedLocs().contains(loc)` with
     * `!getExcludedLocs().isEmpty()` at either enforcement point and all three stay green, while one
     * operator's exclusion quietly shuts a siding to the whole fleet. That is a worse fault than the
     * one they were written for - it is silent, it grows with the number of exclusions, and on a
     * railway that uses station exclusions on through routes it removes reachability everywhere at
     * once.
     *
     * Both enforcement points, because they are different code in different methods: passage is
     * refused by `isPathClear`, and stopping by `pickPath`.
     *
     * The two halves place a DIFFERENT locomotive each time, which is the whole point - and placing
     * the second sweeps the first off the layout, since a locomotive is in one place. The exclusion
     * lives on the Point and names the Locomotive, so it survives that.
     *
     * MUTATION this catches: `!getExcludedLocs().isEmpty()` in place of `contains(loc)`, at either
     * `isPathClear` or `canEnter`'s station rule, fails the half that guards it.
     */
    @Test
    public void testExcludingOneLocomotiveDoesNotBarTheOthers() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive first = placed(layout);
        Locomotive second = other(first);

        // --- passage -------------------------------------------------------------------------
        layout.getPoint("EX_Middle").setExcludedLocs(new HashSet<>(Arrays.asList(first)));

        assertFalse(usesPoint(layout.getPossiblePaths(first, false), "EX_Middle"),
            "precondition: the exclusion has to bite the locomotive it names, or the assertion "
            + "below passes for a rule that is not being enforced at all");

        layout.moveLocomotive(second.getName(), "EX_Start", false);

        assertTrue(usesPoint(layout.getPossiblePaths(second, false), "EX_Middle"),
            "a locomotive that is NOT excluded was refused passage through the point, so one "
            + "operator's exclusion has shut it to the whole fleet");

        // --- stopping ------------------------------------------------------------------------
        Layout station = twoWaysRound();

        Locomotive firstAgain = placed(station);
        Locomotive secondAgain = other(firstAgain);

        station.getPoint("EX_Far").setExcludedLocs(new HashSet<>(Arrays.asList(firstAgain)));

        assertNull(station.pickPath(firstAgain),
            "precondition: EX_Far is the only place autonomy may choose, so the named locomotive "
            + "must be refused - otherwise the assertion below proves nothing");

        station.moveLocomotive(secondAgain.getName(), "EX_Start", false);

        assertNotNull(station.pickPath(secondAgain),
            "a locomotive that is NOT excluded was refused the only destination on the layout, so "
            + "excluding one train from a station has taken it out of service for everybody");
    }

    /**
     * A station whose list names the only locomotives allowed takes the named one and no other (FR-121; Adam, 2026-10-11:
     * *"For locomotive exclusions at a station, add a whitelist and a blacklist mode (default blacklist).  If whitelist, just flip it to anything other than what is selected by the user.  The point is we don't want to allow new locomotives that get added later when that mode is on."*).  Autonomy sends the named locomotive to EX_Far, the one place it may choose here, and refuses
     * the other.
     *
     * MUTATION: read an allow-only list as a list of exclusions - in `pickPath`, or in the yield probe that mirrors it -
     * and this fails.
     */
    @Test
    public void testAnAllowOnlyStationTakesOnlyTheLocomotivesItNames() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive first = placed(layout);
        Locomotive second = other(first);

        Point far = layout.getPoint("EX_Far");

        far.setExcludedLocs(new HashSet<>(Arrays.asList(first)));
        far.setAllowOnlyListed(true);

        // and the yield probe agrees with pickPath - the other trains wait for a train autonomy will send somewhere
        java.lang.reflect.Method probe = Layout.class.getDeclaredMethod("hasAutonomousDestination", Locomotive.class);

        probe.setAccessible(true);

        assertNotNull(layout.pickPath(first), "the one locomotive an allow-only station names was not sent to it");

        assertTrue((Boolean) probe.invoke(layout, first), "the yield probe says autonomy has nowhere to send the one"
            + " locomotive an allow-only station names");

        layout.moveLocomotive(second.getName(), "EX_Start", false);

        assertNull(layout.pickPath(second), "a locomotive an allow-only station does not name was sent to it - Adam:"
            + " \"just flip it to anything other than what is selected by the user\"");

        assertFalse((Boolean) probe.invoke(layout, second), "the yield probe says autonomy has somewhere to send a"
            + " locomotive an allow-only station does not name - the other trains would wait for a train never sent");
    }

    /**
     * And a locomotive added to the database after the list was made is kept out too, which is the point of the mode
     * (FR-121: *"we don't want to allow new locomotives that get added later when that mode is on"*): the list is kept as
     * chosen and read in its mode, not turned into a list of everybody else the day it was made.
     *
     * THE CONTROL: the same list keeping the chosen locomotives out lets the new one in, so the last assertion is about
     * the mode and not about a new locomotive being refused everywhere.
     *
     * MUTATION: make an allow-only list a list of exclusions of the locomotives there are when it is set, and this fails.
     */
    @Test
    public void testAnAllowOnlyListKeepsOutALocomotiveAddedLater() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive first = placed(layout);

        Point far = layout.getPoint("EX_Far");

        far.setExcludedLocs(new HashSet<>(Arrays.asList(first)));
        far.setAllowOnlyListed(true);

        String name = "FR-121 added later";

        Locomotive later = model.newMM2Locomotive(name, 79);

        try
        {
            assertNotNull(later, "precondition: no locomotive was added");

            assertTrue(far.excludes(later), "a locomotive added after an allow-only list was made is let in - Adam: \"we"
                + " don't want to allow new locomotives that get added later when that mode is on\"");

            assertFalse(far.excludes(first), "the locomotive an allow-only list names is kept out");

            far.setAllowOnlyListed(false);

            assertFalse(far.excludes(later), "control: a list keeping the chosen locomotives out kept a new one out too");
        }
        finally
        {
            model.deleteLoc(name);
        }
    }

    /**
     * On a point that is not a station an allow-only list lets only its locomotives pass, as a list of exclusions keeps
     * its own from passing (FR-121) - the same list, read in its mode.
     *
     * MUTATION: read an allow-only list as a list of exclusions where a route is checked for passage, and this fails.
     */
    @Test
    public void testAnAllowOnlyPointLetsOnlyItsLocomotivesPass() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive first = placed(layout);
        Locomotive second = other(first);

        Point middle = layout.getPoint("EX_Middle");

        middle.setExcludedLocs(new HashSet<>(Arrays.asList(second)));
        middle.setAllowOnlyListed(true);

        assertFalse(usesPoint(layout.getPossiblePaths(first, false), "EX_Middle"),
            "a locomotive an allow-only point does not name was routed through it");

        layout.moveLocomotive(second.getName(), "EX_Start", false);

        assertTrue(usesPoint(layout.getPossiblePaths(second, false), "EX_Middle"),
            "the locomotive an allow-only point names was refused passage through it");
    }

    /**
     * And the mode survives a save: written beside the list as `excludedLocsMode: whitelist` and read back, where a list
     * of exclusions writes no mode at all (FR-121).
     *
     * MUTATION: leave the mode out of the saved layout, or out of the reading, and this fails.
     */
    @Test
    public void testAnAllowOnlyListSurvivesASave() throws Exception
    {
        Layout layout = twoWaysRound();

        Locomotive first = placed(layout);
        Locomotive second = other(first);

        Point far = layout.getPoint("EX_Far");

        far.setExcludedLocs(new HashSet<>(Arrays.asList(first)));
        far.setAllowOnlyListed(true);

        layout.getPoint("EX_Middle").setExcludedLocs(new HashSet<>(Arrays.asList(second)));

        assertEquals(far.toJSON().optString(Point.EXCLUDED_LOCS_MODE, null), Point.ALLOW_ONLY_LISTED,
            "an allow-only list was saved without its mode, so it comes back as a list of exclusions");

        assertFalse(layout.getPoint("EX_Middle").toJSON().has(Point.EXCLUDED_LOCS_MODE),
            "a list of exclusions was saved with a mode");

        // as a saved layout has one, or it does not load
        layout.setDefaultLocSpeed(30);

        Layout back = Layout.fromJSON(layout.toJSON(), model);

        assertTrue(back.isValid(), "precondition: the saved layout does not load: " + back.getInvalidReason());

        Point farBack = back.getPoint("EX_Far");

        assertNotNull(farBack, "precondition: the saved layout came back without EX_Far");

        assertTrue(farBack.isAllowOnlyListed(), "an allow-only list came back from a save as a list of exclusions");
        assertTrue(farBack.excludes(second), "a locomotive an allow-only list does not name is let in after a save");
        assertFalse(farBack.excludes(first), "the locomotive an allow-only list names is kept out after a save");

        assertFalse(back.getPoint("EX_Middle").isAllowOnlyListed(), "a list of exclusions came back as an allow-only list");
    }

    // --- helpers ----------------------------------------------------------------------------------

    /**
     * Start, a plain intermediate point, a station in the middle, and a station beyond it - plus a
     * second way round that avoids the intermediate point but not the middle station.
     *
     * Shaped so that each test can turn exactly one thing off and see one thing change.
     */
    private static Layout twoWaysRound() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(301, null);
        MarklinFeedback middle = model.newFeedback(302, null);
        MarklinFeedback through = model.newFeedback(303, null);
        MarklinFeedback far = model.newFeedback(304, null);
        MarklinFeedback around = model.newFeedback(305, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{start, middle, through, far, around})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("EX_Start", true, start.getName());
        layout.createPoint("EX_Middle", false, middle.getName());
        layout.createPoint("EX_Through", true, through.getName());

        // A station, but not one autonomy may send a train TO.  Without this the fixture has two
        // destinations and "was it refused" becomes "which of the two came up this time".
        layout.getPoint("EX_Through").setAutoDestination(false);
        layout.createPoint("EX_Far", true, far.getName());
        layout.createPoint("EX_Around", false, around.getName());

        // The way through the plain intermediate point
        layout.createEdge("EX_Start", "EX_Middle");
        layout.createEdge("EX_Middle", "EX_Through");

        // and the way round it, so excluding EX_Middle removes a route without stranding the train
        layout.createEdge("EX_Start", "EX_Around");
        layout.createEdge("EX_Around", "EX_Through");

        // everything beyond runs through the middle station
        layout.createEdge("EX_Through", "EX_Far");

        return layout;
    }

    private static Locomotive placed(Layout layout) throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        for (Point point : layout.getPoints()) point.setLocomotive(null);

        layout.moveLocomotive(loc.getName(), "EX_Start", false);

        return loc;
    }

    /** Whether any offered route passes through this point, at either end of any of its edges. */
    private static boolean usesPoint(List<List<Edge>> paths, String name)
    {
        for (List<Edge> path : paths)
        {
            for (Edge edge : path)
            {
                if (name.equals(edge.getStart().getName())
                    || name.equals(edge.getEnd().getName())) return true;
            }
        }

        return false;
    }

    /** Whether any offered route finishes at this point. */
    private static boolean endsAt(List<List<Edge>> paths, String name)
    {
        for (List<Edge> path : paths)
        {
            if (!path.isEmpty() && name.equals(path.get(path.size() - 1).getEnd().getName()))
            {
                return true;
            }
        }

        return false;
    }
    /**
     * A second locomotive from the database, which is what A113 needs and nothing here had.
     *
     * Borrowed rather than created, like `placed`, so this class goes on owning no addresses - and
     * asserted to be a different object, because two names that resolve to one locomotive would make
     * the test above pass while asking one question twice.
     *
     * @param notThisOne the locomotive already in use
     * @return another one
     */
    private static Locomotive other(Locomotive notThisOne) throws Exception
    {
        for (String name : model.getLocList())
        {
            Locomotive candidate = model.getLocByName(name);

            if (candidate != null && !candidate.equals(notThisOne)) return candidate;
        }

        throw new org.testng.SkipException(
            "the locomotive database holds only one locomotive, so there is no second one to ask about");
    }

}
