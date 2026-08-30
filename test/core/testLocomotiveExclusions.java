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
}
