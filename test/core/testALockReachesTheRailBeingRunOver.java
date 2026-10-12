package core;

import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A running path blocks the rail it runs over and the same rail the other way - and nothing beyond
 * that (OB-269).
 *
 * **Adam, 2026-09-22:** *"when running auto layout, more edges (tracks) may get locked than
 * necessary.  For example, a path to tunnel from bottomsecondary should not block the path from
 * bottominner to bottominnerotherside (and vice-versa), but I see that they do.  significant bug."*
 * Asked whether the long row-3 road is real he said it is, and gave the rule: *"nothing should disturb
 * the ability to go from bottominner to bottominnerotherside unless trains are crossing down, or
 * passing through either of those stations."*
 *
 * **The write and the read each reach one hop, and together they reached two.**  `Edge.setOccupied`
 * marks every edge in each locked edge's `lockEdges`; `isPathClear` read the flag off every edge in
 * each CANDIDATE's `lockEdges`.  Both are deliberate - the same list walked both ways is what makes a
 * one-directional relation refuse either ordering - and the cost was that a third edge sharing rail
 * with each of two others made those two conflict.  Measured here: the run and the hop share **not one
 * tile**, and the refusal named `12,7 -> Tunnel`, which shares eight with the run and three with the
 * hop.
 *
 * **The fix is a narrower question, not a narrower relation.**  `Edge.runOver` counts only the edges a
 * path actually runs over; `isRunOver` asks it; `occupancy` and `isLockHeld` are untouched, so a held
 * throat's accessories stay protected either way (`getActiveAccs`).  Both orderings of an asymmetric
 * relation still refuse - one by the write into `occupancy`, one by this read - and what is given up
 * is exactly the proxy-locked middle case.
 *
 * **And the reverse direction is refused on its own account, which is not optional.**
 * `GraphReducer.deriveLocks` deliberately does not pair the two directions of one rail, so the reverse
 * edge is in nobody's `lockEdges` and the second hop was what refused it - by accident.  The third
 * claim below is that control, and it is the one that caught the first attempt at this fix: with the
 * reach narrowed and nothing put in its place, a path over `Tunnel -> TunnelPre` was allowed while a
 * train ran `TunnelPre -> Tunnel`.
 *
 * **Every train is taken off the railway first**, and that is not tidiness: the snapshot parks
 * `75 407 DB` on `BottomInnerOtherside`, and a train standing there refuses the hop whatever the
 * locking does.  The first version of this measurement had no such step and was reading the parked
 * train.
 *
 * MUTATIONS, measured rather than asserted, and each reddens exactly the claims it should:
 *
 * - ask `isLockHeld` in the candidate scan again, and **the first claim and Adam's own list** fail -
 *   his by six pairs of seven, naming `12,7 -> Tunnel` and `TunnelLongPark -> BottomCrossover`.  The
 *   three refusal controls stay green, which is what says the narrowing is the only thing being
 *   measured.
 * - drop the reverse-rail check, and **both refusal controls** fail: the same rail the other way, and
 *   the shared-rail control, which picks that pair up too because eleven of its tiles are the run's.
 *   The first claim and the list stay green.
 * - drop the `runOver` decrement in `setUnoccupied`, and **the release claim** fails, naming the edge
 *   still reading as run over after three locks and three releases.
 *
 * @author Adam
 */
public class testALockReachesTheRailBeingRunOver
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static Layout layout;

    private static Locomotive asking;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        if (model.getLocList().isEmpty()) throw new SkipException("this fixture has no locomotives");

        asking = model.getLocByName(model.getLocList().get(0));

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

    /**
     * The hop Adam named stays clear while a path that shares no rail with it runs.
     *
     * @throws Exception from the search
     */
    @Test
    public void testAPathIsNotBlockedByRailNothingIsRunningOver() throws Exception
    {
        List<Edge> hop = theHop();

        List<Edge> run = theRun();

        // PRECONDITION, and it is what makes the claim discriminate: these two must share no rail.
        // If they ever come to share one, the refusal becomes correct and this would be pinning the
        // wrong thing without saying so.
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

    /**
     * A path that DOES share rail with a running one is still refused.
     *
     * @throws Exception from the search
     */
    @Test
    public void testAPathThatSharesRailIsStillRefused() throws Exception
    {
        List<Edge> run = theRun();

        Edge shares = null;

        for (Edge candidate : layout.getEdges())
        {
            if (run.contains(candidate)) continue;

            // Not the reverse of a run edge - that is the next claim's business, and letting it be
            // picked here would make the two claims test one thing.
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
            // THE MESSAGE NAMES THE RAIL AND THE RELATION, because "it shares rail" is not enough to
            // act on: what matters is whether the lock relation knows they share it.
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
     * And the same rail the OTHER WAY is refused - the control that caught the first attempt.
     *
     * `deriveLocks` does not pair the two directions of one rail, so nothing in `lockEdges` says they
     * conflict; until this fix what refused the reverse was the second hop, by accident.  A narrowing
     * that does not replace that protection trades an over-refusal for a collision.
     *
     * @throws Exception from the search
     */
    @Test
    public void testTheSameRailTheOtherWayIsRefused() throws Exception
    {
        Edge forward = null;
        Edge back = null;

        // FOUND BY PLACE, the way the rule finds it - and the first version of this looked it up by
        // NAME, which is why it stayed green when the rule was disabled.  The two directions of one
        // rail run between different COPIES of the two squares, so `getEdge(end, start)` finds either
        // nothing or a different rail; the pair this is about is the one `deriveLocks` declines to
        // lock, and that comparison is `isSamePlaceAs`.  His own concurrency list caught the mistake.
        for (Edge e : layout.getEdges())
        {
            if (forward != null) break;

            if (e.getStart() == null || e.getEnd() == null) continue;

            for (Edge reverse : layout.getEdges())
            {
                if (reverse == e || reverse.getStart() == null || reverse.getEnd() == null) continue;

                if (!reverse.getStart().isSamePlaceAs(e.getEnd())) continue;

                if (!reverse.getEnd().isSamePlaceAs(e.getStart())) continue;

                // NOT one the lock relation already pairs, or the claim would pass through the
                // ordinary relation rather than through the rule being added.
                if (e.getLockEdges().contains(reverse)) continue;

                if (reverse.getLockEdges().contains(e)) continue;

                // AND THEY MUST REALLY BE ONE RAIL, which is the whole premise: two edges between the
                // same pair of squares by different roads would make this claim about something else.
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
            throw new SkipException("this railway writes no rail in both directions outside the lock"
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
     * And a run gives every claim back, so the next train finds the railway as it was.
     *
     * The counter arithmetic is where this could go wrong quietly: a claim never released leaves rail
     * blocked for ever, and one released twice makes the NEXT train find occupied rail free.
     *
     * @throws Exception from the search
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

        // AND THE OTHER DIRECTION of the release, which is the worse one: a claim given back twice
        // would let the next train onto rail a running one is on.
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
     * Adam's own list of pairs that must be able to run at once (OB-269).
     *
     * **His words, 2026-09-22**, given unprompted when he offered *"a list of allowed concurrent paths
     * if it helps"*:
     *
     * while `BottomInner -> BottomInnerOtherside` (or the reverse) runs, any of
     * `TopMainR1 -> BottomSecondary`, `TopMainR2 -> BottomSecondary`,
     * `BottomSecondary -> BottomMainA`, `BottomSecondary -> BottomMainC`;
     *
     * and while `BottomMainA -> TopMainR1` runs, any of `TopMainR2 -> BottomSecondary`,
     * `BottomInner -> BottomMainB`, `TopMainR2Pre -> TopMainR2`.
     *
     * He wrote the first of those as `BottomMainR2` and corrected it on 2026-09-23: *"There is no
     * BottomMainR2, only TopMainR2."*  It is the same journey group one already names, asked against a
     * different run - which is worth having, because the two runs share no rail with it for different
     * reasons.
     *
     * **This is an ORACLE, not a fixture I chose**, which is the point of it: a narrowing that is right
     * about the one hop I measured and wrong in general fails here.  Both his lists are marked *non
     * exhaustive*, so nothing below asserts that anything ELSE is refused - the refusal side is the
     * three controls beside this claim.
     *
     * **Every pair is checked and they are reported together.**  Stopping at the first would hide how
     * much of his list a change gets wrong, and that number is the useful one.
     *
     * **A pair whose name this railway does not have is reported rather than skipped silently**, so a
     * typo in the list - or a square renamed since - reads as a question and not as a pass.
     * Nothing in either list fails to resolve now that the name above is corrected; the check stays,
     * because a square renamed later must read as a question rather than as a pass.
     *
     * @throws Exception from the search
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

        for (String[] running : whileRunning)
        {
            check(running, alsoOk, wrong, unresolved);
        }

        check(new String[] {"BottomMainA", "TopMainR1"},
            new String[][] {
                {"TopMainR2", "BottomSecondary"},
                {"BottomInner", "BottomMainB"},
                {"TopMainR2Pre", "TopMainR2"},
            }, wrong, unresolved);

        assertTrue(wrong.isEmpty(),
            "these pairs Adam says may run at once are refused: " + wrong
            + ".  His rule: \"nothing should disturb the ability to go from bottominner to"
            + " bottominnerotherside unless trains are crossing down, or passing through either of"
            + " those stations\"");

        // NOT an assertion: a name his railway does not carry is a question for him, and failing on
        // it would stop the claim above being about the locking.
        if (!unresolved.isEmpty())
        {
            System.out.println("OB-269 oracle: these names did not resolve on this railway and were"
                + " not checked: " + unresolved);
        }
    }

    /**
     * Locks one journey and asks whether each of the others can still be made.
     *
     * Every combination of copies is tried for both journeys, shortest route first, because a station
     * pair has one route per pair of copies and the copies are what encode facing - so which routes
     * exist is the layout's own answer about which directions it allows.  The claim is satisfied by
     * ONE combination being permitted, which is the question his list poses.
     *
     * @param running the journey to lock, as two station names
     * @param others the journeys that must still be possible
     * @param wrong filled with the ones no combination allows
     * @param unresolved filled with the ones this railway cannot express
     * @throws Exception from the search
     */
    private static void check(String[] running, String[][] others, List<String> wrong,
        List<String> unresolved) throws Exception
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

            for (List<Edge> run : runs)
            {
                for (List<Edge> other : candidates)
                {
                    String overlap = sharedRail(run, other);

                    if (overlap != null)
                    {
                        // The two journeys would use the same rail on THIS combination of copies, so
                        // refusing is right and says nothing about the locking.  Another combination
                        // may not share any.
                        if (anOverlap == null) anOverlap = overlap;

                        continue;
                    }

                    // Clear on its own account, or a refusal is about something else entirely - an
                    // unmeasured berth, a barred arrival.
                    if (!layout.isPathClear(other, asking, false)) continue;

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

            if (allowed) continue;

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
     * Every route between two squares, over any combination of the copies they build to.
     *
     * Shortest first, because that is the road an operator means - and because a long way round is
     * exactly what the first version of this claim tested by accident.
     *
     * The copies ARE the layout's statement about direction: a train on a copy may only leave by the
     * one-way edges that copy has, so a combination with no route is a direction his layout does not
     * allow, and is simply absent here rather than worked around.
     *
     * @param from the starting square's name, as he writes it
     * @param to the destination square's name
     * @return the routes, shortest first
     * @throws Exception from the search
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

    /**
     * A Point's name without the copy suffix, so "BottomMainA (eastbound)" answers to "BottomMainA".
     *
     * @param name the Point's name
     * @return the square's name
     */
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
     * What rail two routes have in common, or null when they have none.
     *
     * @param one a route
     * @param two another
     * @return a description of every shared stretch, or null
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
