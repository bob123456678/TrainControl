package core;

import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A running path blocks the rail it runs over and the same rail the other way, and nothing beyond that (OB-269). Adam:
 * *"nothing should disturb the ability to go from bottominner to bottominnerotherside unless trains are crossing down,
 * or passing through either of those stations."*
 *
 * `Edge.setOccupied` marks every edge in each locked edge's `lockEdges` and `isPathClear` read the flag off every edge
 * in each CANDIDATE's `lockEdges`: each is deliberate (one list walked both ways makes a one-directional relation
 * refuse either ordering), but together they reached two hops, so a third edge sharing rail with each of two others
 * made those two conflict. The fix is a narrower question, not a narrower relation: `Edge.runOver` counts only the
 * edges a path actually runs over and `isRunOver` asks it, while `occupancy` and `isLockHeld` are untouched, so a held
 * throat's accessories stay protected (`getActiveAccs`).
 *
 * The reverse direction is refused on its own account, which is not optional: `GraphReducer.deriveLocks` does not pair
 * the two directions of one rail, so the second hop was refusing it by accident. The third claim below is that control.
 * Every train is taken off the railway first: a train parked on `BottomInnerOtherside` refuses the hop whatever the
 * locking does.
 *
 * MUTATIONS, each reddening exactly the claims it should: ask `isLockHeld` in the candidate scan again and the first
 * claim and Adam's own list fail (the three refusal controls stay green); drop the reverse-rail check and both refusal
 * controls fail; drop the `runOver` decrement in `setUnoccupied` and the release claim fails; make `TRAIN_LENGTH` 3 and
 * the floor under Adam's list fails (too long to stop at BottomMainC or TopMainR2).
 *
 * @author Adam
 */
public class testALockReachesTheRailBeingRunOver
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static Layout layout;

    private static Locomotive asking;

    /** The train every path is asked for, in units: the shortest station maximum on this railway is two. */
    private static final int TRAIN_LENGTH = 2;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new AssertionError("the snapshot did not build");

        // Its own train, two units long, the snapshot's shortest station maximum: the database's first locomotive
        // (three units) cannot stop at BottomMainC or TopMainR2, so pairs would be refused before any lock was asked
        // about them.
        asking = new MarklinLocomotive(model, 1, MarklinLocomotive.decoderType.MM2, "OB-269 two-unit train");

        asking.setTrainLength(TRAIN_LENGTH);

        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null) p.setLocomotive(null);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /** The hop Adam named stays clear while a path that shares no rail with it runs. */
    @Test
    public void testAPathIsNotBlockedByRailNothingIsRunningOver() throws Exception
    {
        List<Edge> hop = theHop();

        List<Edge> run = theRun();

        // PRECONDITION, what makes the claim discriminate: these two must share no rail, or the refusal becomes correct
        // and this would pin the wrong thing.
        for (Edge r : run)
        {
            for (Edge h : hop)
            {
                java.util.Set<String> shared = new java.util.LinkedHashSet<>(r.getPlaceIds());

                shared.retainAll(h.getPlaceIds());

                assertTrue(shared.isEmpty(),
                    "precondition: " + name(r) + " and " + name(h) + " share " + shared
                    + ", so refusing between them is correct and this claim is about nothing");
            }
        }

        assertTrue(layout.isPathClear(hop, asking, false),
            "precondition: the hop is not clear on an empty railway, so nothing below is about the"
            + " lock.  Reason: " + Layout.getLastError());

        for (Edge e : run) e.setOccupied();

        try
        {
            assertTrue(layout.isPathClear(hop, asking, false),
                "a train running " + names(run) + " refuses " + names(hop) + ", which shares no rail"
                + " with it.  Reason: " + Layout.getLastError() + ".  That is OB-269: the lock"
                + " reaching a second hop through a third edge that shares rail with each of them"
                + " separately");
        }
        finally
        {
            for (Edge e : run) e.setUnoccupied();
        }
    }

    /** A path that DOES share rail with a running one is still refused. */
    @Test
    public void testAPathThatSharesRailIsStillRefused() throws Exception
    {
        List<Edge> run = theRun();

        Edge shares = null;

        for (Edge candidate : layout.getEdges())
        {
            if (run.contains(candidate)) continue;

            // Not the reverse of a run edge: that is the next claim's business, and picking it here would make the two
            // claims test one thing.
            boolean reverseOfTheRun = false;

            for (Edge r : run)
            {
                if (candidate.getStart() == r.getEnd() && candidate.getEnd() == r.getStart())
                {
                    reverseOfTheRun = true;
                }
            }

            if (reverseOfTheRun) continue;

            for (Edge r : run)
            {
                java.util.Set<String> common = new java.util.LinkedHashSet<>(r.getPlaceIds());

                common.retainAll(candidate.getPlaceIds());

                if (!common.isEmpty() && shares == null) shares = candidate;
            }
        }

        assertNotNull(shares,
            "no edge on this railway shares rail with " + names(run) + " without being its reverse,"
            + " so there is nothing here to check the refusal still works on");

        for (Edge e : run) e.setOccupied();

        try
        {
            // The message names the rail and the relation: what matters is whether the lock relation knows they share
            // it.
            StringBuilder why = new StringBuilder();

            for (Edge r : run)
            {
                java.util.Set<String> common = new java.util.LinkedHashSet<>(r.getPlaceIds());

                common.retainAll(shares.getPlaceIds());

                if (common.isEmpty()) continue;

                why.append(nl()).append("      shares ").append(common).append(" with ").append(name(r))
                   .append("; candidate names it: ").append(shares.getLockEdges().contains(r))
                   .append("; it names the candidate: ").append(r.getLockEdges().contains(shares))
                   .append("; reverseByName: ")
                   .append(layout.getEdge(shares.getEnd().getName(), shares.getStart().getName()) == r);
            }

            assertFalse(layout.isPathClear(java.util.Collections.singletonList(shares), asking, false),
                "a path over " + name(shares) + " is allowed while a train runs over rail it shares -"
                + " two trains on one piece of track.  The OB-269 fix must narrow the lock's REACH and"
                + " not its grip." + why);
        }
        finally
        {
            for (Edge e : run) e.setUnoccupied();
        }
    }

    /**
     * And the same rail the OTHER WAY is refused. `deriveLocks` does not pair the two directions of one rail, so
     * nothing in `lockEdges` says they conflict; a narrowing that does not replace that protection trades an
     * over-refusal for a collision.
     */
    @Test
    public void testTheSameRailTheOtherWayIsRefused() throws Exception
    {
        Edge forward = null;
        Edge back = null;

        // Found by place, the way the rule finds it, not by name: the two directions of one rail run between different
        // COPIES of the two squares, so `getEdge(end, start)` finds nothing or a different rail. The pair is the one
        // `deriveLocks` declines to lock, compared by `isSamePlaceAs`.
        for (Edge e : layout.getEdges())
        {
            if (forward != null) break;

            if (e.getStart() == null || e.getEnd() == null) continue;

            for (Edge reverse : layout.getEdges())
            {
                if (reverse == e || reverse.getStart() == null || reverse.getEnd() == null) continue;

                if (!reverse.getStart().isSamePlaceAs(e.getEnd())) continue;

                if (!reverse.getEnd().isSamePlaceAs(e.getStart())) continue;

                // Not one the lock relation already pairs, or the claim would pass through the ordinary relation rather
                // than the rule being added.
                if (e.getLockEdges().contains(reverse)) continue;

                if (reverse.getLockEdges().contains(e)) continue;

                // And they must really be one rail, the whole premise: two edges between the same squares by different
                // roads would make this claim about something else.
                java.util.Set<String> common = new java.util.LinkedHashSet<>(e.getPlaceIds());

                common.retainAll(reverse.getPlaceIds());

                if (common.isEmpty()) continue;

                forward = e;
                back = reverse;

                break;
            }
        }

        if (forward == null)
        {
            throw new AssertionError("this railway writes no rail in both directions outside the lock"
                + " relation, so there is nothing for this rule to be about");
        }

        forward.setOccupied();

        try
        {
            assertFalse(layout.isPathClear(java.util.Collections.singletonList(back), asking, false),
                "a train is allowed onto " + name(back) + " while another runs over " + name(forward)
                + " - the same piece of rail in the other direction, which `deriveLocks` deliberately"
                + " does not lock against itself.  Two trains on one rail");
        }
        finally
        {
            forward.setUnoccupied();
        }
    }

    /**
     * And a run gives every claim back, so the next train finds the railway as it was: a claim never released leaves
     * rail blocked for ever, and one released twice makes the NEXT train find occupied rail free.
     */
    @Test
    public void testARunGivesEveryClaimBack() throws Exception
    {
        List<Edge> run = theRun();

        List<Edge> hop = theHop();

        assertTrue(layout.isPathClear(hop, asking, false), "the hop is not clear to begin with");

        for (int round = 0; round < 3; round++)
        {
            for (Edge e : run) e.setOccupied();

            for (Edge e : run) e.setUnoccupied();
        }

        for (Edge e : run)
        {
            assertFalse(e.isRunOver(asking),
                name(e) + " still reads as run over after three locks and three releases, so the"
                + " railway is permanently blocked by a train that has finished");

            assertFalse(e.isLockHeld(asking),
                name(e) + " still holds a claim after three locks and three releases");
        }

        assertTrue(layout.isPathClear(hop, asking, false),
            "the hop is refused after three complete runs that were all released.  Reason: "
            + Layout.getLastError());

        // And the other direction of the release, the worse one: a claim given back twice would let the next train onto
        // rail a running one is on.
        for (Edge e : run) e.setOccupied();

        try
        {
            assertTrue(run.get(0).isRunOver(asking),
                "after three release cycles a fresh lock does not register at all, so a claim has"
                + " been given back more often than it was made and the next train finds occupied"
                + " rail free");
        }
        finally
        {
            for (Edge e : run) e.setUnoccupied();
        }
    }

    /**
     * Adam's own list of pairs that must be able to run at once (OB-269), given when he offered *"a list of allowed
     * concurrent paths if it helps"*: while `BottomInner -> BottomInnerOtherside` (or the reverse) runs, any of
     * `TopMainR1 -> BottomSecondary`, `TopMainR2 -> BottomSecondary`, `BottomSecondary -> BottomMainA`,
     * `BottomSecondary -> BottomMainC`; and while `BottomMainA -> TopMainR1` runs, any of `TopMainR2 ->
     * BottomSecondary`, `BottomInner -> BottomMainB`, `TopMainR2Pre -> TopMainR2`.
     *
     * This is an ORACLE, not a fixture chosen here: a narrowing right about one hop and wrong in general fails. Both
     * his lists are marked *non exhaustive*, so nothing asserts that anything ELSE is refused; the refusal side is the
     * three controls beside this claim. Every pair is checked and they are reported together, since how much of his
     * list a change gets wrong is the useful number. A pair whose name this railway does not have is reported rather
     * than skipped silently, so a renamed square reads as a question and not as a pass.
     */
    @Test
    public void testHisAllowedConcurrentPathsAreAllowed() throws Exception
    {
        String[][] whileRunning = {
            {"BottomInner", "BottomInnerOtherside"},
            {"BottomInnerOtherside", "BottomInner"},
        };

        String[][] alsoOk = {
            {"TopMainR1", "BottomSecondary"},
            {"TopMainR2", "BottomSecondary"},
            {"BottomSecondary", "BottomMainA"},
            {"BottomSecondary", "BottomMainC"},
        };

        List<String> wrong = new java.util.ArrayList<>();
        List<String> unresolved = new java.util.ArrayList<>();
        List<String> allowedPairs = new java.util.ArrayList<>();

        for (String[] running : whileRunning)
        {
            check(running, alsoOk, wrong, unresolved, allowedPairs);
        }

        check(new String[] {"BottomMainA", "TopMainR1"},
            new String[][] {
                {"TopMainR2", "BottomSecondary"},
                {"BottomInner", "BottomMainB"},
                {"TopMainR2Pre", "TopMainR2"},
            }, wrong, unresolved, allowedPairs);

        assertTrue(wrong.isEmpty(),
            "these pairs Adam says may run at once are refused: " + wrong
            + ".  His rule: \"nothing should disturb the ability to go from bottominner to"
            + " bottominnerotherside unless trains are crossing down, or passing through either of"
            + " those stations\"");

        // A floor under the pairs compared (FR-136): `wrong` is empty for a pair never compared, so a search that found
        // no route for any of them left the claim above green over nothing. All eleven of his pairs run alongside on
        // this railway for a train short enough to stop at both ends.
        assertTrue(allowedPairs.size() >= 11,
            "only " + allowedPairs.size() + " of the 11 pairs Adam says may run at once were found to, so the locking was"
            + " asked about fewer of them than it was: " + allowedPairs + ", not compared: " + unresolved);

        // Not an assertion: a name his railway does not carry is a question for him, and failing on it would stop the
        // claim above being about the locking.
        if (!unresolved.isEmpty())
        {
            System.out.println("OB-269 oracle: these names did not resolve on this railway and were"
                + " not checked: " + unresolved);
        }
    }

    /**
     * Locks one journey and asks whether each of the others can still be made. Every combination of copies is tried for
     * both journeys, shortest route first, because a station pair has one route per pair of copies and the copies
     * encode facing; the claim is satisfied by ONE combination being permitted, the question his list poses. `wrong`
     * and `unresolved` are filled with the pairs no combination allows and the ones this railway cannot express,
     * `allowedPairs` with the ones some combination allows.
     */
    private static void check(String[] running, String[][] others, List<String> wrong,
        List<String> unresolved, List<String> allowedPairs) throws Exception
    {
        List<List<Edge>> runs = routesBetween(running[0], running[1]);

        if (runs.isEmpty())
        {
            unresolved.add("while " + running[0] + " -> " + running[1] + " (no route at all)");

            return;
        }

        for (String[] pair : others)
        {
            List<List<Edge>> candidates = routesBetween(pair[0], pair[1]);

            if (candidates.isEmpty())
            {
                unresolved.add(pair[0] + " -> " + pair[1] + " (no route at all)");

                continue;
            }

            boolean allowed = false;
            boolean everTried = false;

            String firstRefusal = null;
            String anOverlap = null;
            String refusedOnItsOwn = null;

            for (List<Edge> run : runs)
            {
                for (List<Edge> other : candidates)
                {
                    String overlap = sharedRail(run, other);

                    if (overlap != null)
                    {
                        // The two journeys would use the same rail on THIS combination of copies, so refusing is right
                        // and says nothing about the locking; another combination may not share any.
                        if (anOverlap == null) anOverlap = overlap;

                        continue;
                    }

                    // Clear on its own account, or a refusal is about something else entirely (an unmeasured berth, a
                    // barred arrival).
                    if (!layout.isPathClear(other, asking, false))
                    {
                        if (refusedOnItsOwn == null)
                        {
                            refusedOnItsOwn = Layout.getLastError() + nl() + "      refused:   " + names(other);
                        }

                        continue;
                    }

                    everTried = true;

                    for (Edge e : run) e.setOccupied();

                    try
                    {
                        if (layout.isPathClear(other, asking, false))
                        {
                            allowed = true;

                            System.out.println("OB-269 oracle: " + pair[0] + " -> " + pair[1]
                                + " runs alongside " + running[0] + " -> " + running[1]);
                            System.out.println("      running:   " + names(run));
                            System.out.println("      alongside: " + names(other));
                        }
                        else if (firstRefusal == null)
                        {
                            firstRefusal = Layout.getLastError()
                                + nl() + "      running:   " + names(run)
                                + nl() + "      refused:   " + names(other);
                        }
                    }
                    finally
                    {
                        for (Edge e : run) e.setUnoccupied();
                    }

                    if (allowed) break;
                }

                if (allowed) break;
            }

            if (allowed)
            {
                allowedPairs.add(pair[0] + " -> " + pair[1] + " while " + running[0] + " -> " + running[1]);

                continue;
            }

            // Never compared: say why. A combination clear of the run's rail that is refused on an empty railway is
            // about the train or the station, not the lock, and naming shared rail for it sends the reader to the wrong
            // question.
            if (!everTried && refusedOnItsOwn != null)
            {
                unresolved.add(pair[0] + " -> " + pair[1] + " while " + running[0] + " -> " + running[1]
                    + " (never compared: every combination clear of the run's rail is refused on an empty railway,"
                    + " the first because: " + refusedOnItsOwn + ")");

                continue;
            }

            if (!everTried)
            {
                unresolved.add(pair[0] + " -> " + pair[1] + " while " + running[0] + " -> "
                    + running[1] + " (every combination of copies shares rail, so these two journeys"
                    + " cannot be made at once on this layout:" + anOverlap + ")");

                continue;
            }

            wrong.add(pair[0] + " -> " + pair[1] + " while " + running[0] + " -> " + running[1]
                + " (" + firstRefusal + ")");
        }
    }

    /**
     * Every route between two squares, over any combination of the copies they build to, shortest first (the road an
     * operator means). The copies ARE the layout's statement about direction: a combination with no route is a
     * direction his layout does not allow, and is simply absent here rather than worked around.
     */
    private static List<List<Edge>> routesBetween(String from, String to) throws Exception
    {
        List<List<Edge>> found = new java.util.ArrayList<>();

        for (Point start : layout.getPoints())
        {
            if (!base(start.getName()).equalsIgnoreCase(from)) continue;

            for (Point end : layout.getPoints())
            {
                if (!base(end.getName()).equalsIgnoreCase(to)) continue;

                if (start == end) continue;

                List<Edge> path = layout.bfs(start, end, null);

                if (path != null && !path.isEmpty()) found.add(path);
            }
        }

        java.util.Collections.sort(found, new java.util.Comparator<List<Edge>>()
        {
            @Override
            public int compare(List<Edge> a, List<Edge> b)
            {
                return a.size() - b.size();
            }
        });

        return found;
    }

    /** A Point's name without the copy suffix, so "BottomMainA (eastbound)" answers to "BottomMainA". */
    private static String base(String name)
    {
        int bracket = name.indexOf(" (");

        return bracket < 0 ? name : name.substring(0, bracket);
    }

    private static List<Edge> theHop() throws Exception
    {
        Point inner = startingWith("BottomInner (");
        Point other = startingWith("BottomInnerOtherside");

        assertNotNull(inner, "no BottomInner copy");
        assertNotNull(other, "no BottomInnerOtherside");

        List<Edge> hop = layout.bfs(inner, other, null);

        assertNotNull(hop, "no route from " + inner.getName() + " to " + other.getName());

        return hop;
    }

    private static List<Edge> theRun() throws Exception
    {
        Point secondary = startingWith("BottomSecondary");
        Point tunnel = startingWith("Tunnel (");

        assertNotNull(secondary, "no BottomSecondary");
        assertNotNull(tunnel, "no Tunnel copy");

        List<Edge> road = layout.bfs(secondary, tunnel, null);

        assertNotNull(road, "no route from " + secondary.getName() + " to " + tunnel.getName());

        return road;
    }

    private static Point startingWith(String prefix)
    {
        for (Point p : layout.getPoints())
        {
            if (p.getName().startsWith(prefix)) return p;
        }

        return null;
    }

    /**
     * What rail two routes have in common, as a description of every shared stretch, or null when they have none.
     */
    private static String sharedRail(List<Edge> one, List<Edge> two)
    {
        StringBuilder out = new StringBuilder();

        for (Edge a : one)
        {
            for (Edge b : two)
            {
                java.util.Set<String> common = new java.util.LinkedHashSet<>(a.getPlaceIds());

                common.retainAll(b.getPlaceIds());

                if (!common.isEmpty())
                {
                    out.append(" [").append(name(b)).append(" and ").append(name(a))
                       .append(" share ").append(common).append("]");
                }
            }
        }

        return out.length() == 0 ? null : out.toString();
    }

    private static String nl()
    {
        return System.getProperty("line.separator");
    }

    private static String name(Edge e)
    {
        return e.getStart().getName() + " -> " + e.getEnd().getName();
    }

    private static String names(List<Edge> path)
    {
        StringBuilder out = new StringBuilder();

        for (Edge e : path)
        {
            if (out.length() > 0) out.append(", ");

            out.append(name(e));
        }

        return out.toString();
    }
}
