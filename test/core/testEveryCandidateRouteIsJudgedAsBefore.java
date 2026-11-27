package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * `Layout.pickPath` and `isPathClear` judge every candidate route as they did before FR-203 took what a pass asks of
 * the railway once: on every scenario folder and three autonomy files, with standing trains (short and long tails), lit
 * destination sensors, a route holding track, locked edges, a train that fits nowhere and one with its own tail, the
 * choice pickPath makes and, for routes found by a fixed search, each verdict with its reason.  The situations follow
 * one another on one railway, so an answer kept from an earlier pass shows.  The golden,
 * `test/baseline/pickpath_golden.txt`, was written by `main` below run on dev at 68d9bc812.
 *
 * MUTATION: keep the first walk's covered track for a train for every later pass, and the tails placed afterwards stop
 * refusing; or take Layout.OnePass.railsBack the same way round rather than reversed, and the held routes' other
 * directions stop refusing.
 */
public class testEveryCandidateRouteIsJudgedAsBefore
{
    private static final String GOLDEN = "test/baseline/pickpath_golden.txt";

    private static final String[] FILES =
    {
        "test/autonomy.json", "test/autonomy_sanity_forks.json", "test/autonomy_formats/autonomy-2.7.4c.json"
    };

    private static int address = 0;

    /** A train not in the locomotive database, so nothing here depends on Adam's. */
    private static Locomotive train(MarklinControlStation model, String name, int length)
    {
        Locomotive t = new MarklinLocomotive(model, 70 + (address++ % 100), MarklinLocomotive.decoderType.MM2, name);

        t.setTrainLength(length);

        return t;
    }

    private static List<Edge> sortedByName(java.util.Collection<Edge> edges)
    {
        List<Edge> out = new ArrayList<>(edges);

        out.sort((a, b) -> a.getName().compareTo(b.getName()));

        return out;
    }

    private static void stand(Layout layout, Point at, Locomotive t)
    {
        at.setLocomotive(t);

        for (Edge in : sortedByName(layout.getIncomingEdges(at)))
        {
            if (in.getStart() == null) continue;

            at.setArrivedFrom(layout.entrySideOf(in, at));
            at.setArrivedAlong(Arrays.asList(in));

            return;
        }
    }

    private static void lift(Point at)
    {
        at.setLocomotive(null);
        at.setArrivedFrom(null);
        at.setArrivedAlong(null);
    }

    /** Up to `most` simple routes from start to end, depth first by edge name, by bfs's rules on termini and copies. */
    private static List<List<Edge>> routes(Layout layout, Point start, Point end, int most)
    {
        List<List<Edge>> out = new ArrayList<>();

        search(layout, start, end, start, new ArrayList<>(), new HashSet<>(), out, most, new int[] {4000});

        return out;
    }

    private static void search(Layout layout, Point start, Point end, Point at, List<Edge> path, Set<Point> seen,
        List<List<Edge>> out, int most, int[] budget)
    {
        if (out.size() >= most || budget[0]-- <= 0 || path.size() >= 14) return;

        seen.add(at);

        for (Edge next : sortedByName(layout.getNeighbors(at)))
        {
            Point to = next.getEnd();

            path.add(next);

            if (to.equals(end)) out.add(new ArrayList<>(path));
            else if (!seen.contains(to) && !to.isTerminus() && !to.isSamePlaceAs(start) && !to.isSamePlaceAs(end))
            {
                search(layout, start, end, to, path, seen, out, most, budget);
            }

            path.remove(path.size() - 1);

            if (out.size() >= most) break;
        }

        seen.remove(at);
    }

    private static String names(List<Edge> path)
    {
        StringBuilder s = new StringBuilder();

        for (Edge e : path) s.append(s.length() == 0 ? e.getStart().getName() : "").append(" > ")
            .append(e.getEnd().getName());

        return s.toString();
    }

    /** Every answer for one railway, in a fixed order. */
    private static void judge(String label, MarklinControlStation model, Layout layout, List<String> out)
        throws Exception
    {
        out.add("== " + label);

        if (layout == null)
        {
            out.add("no layout");
            return;
        }

        List<Point> all = new ArrayList<>(layout.getPoints());

        all.sort((a, b) -> a.getName().compareTo(b.getName()));

        for (Point p : all) if (p.getCurrentLocomotive() != null) lift(p);

        // Distinct priorities, so pickPath's order of ends is fixed.
        for (int i = 0; i < all.size(); i++) all.get(i).setPriority(i % 97);

        List<Point> stations = new ArrayList<>();

        for (Point p : all)
        {
            boolean copy = false;

            for (Point q : stations) copy |= q.isSamePlaceAs(p);

            if (p.isDestination() && p.isActive() && !copy) stations.add(p);
        }

        out.add("stations " + stations.size());

        if (stations.size() < 2) return;

        int n = stations.size();
        List<Point> standing = new ArrayList<>(), movers = new ArrayList<>();

        for (int i = 1; i < n && standing.size() < 6; i += Math.max(2, n / 6)) standing.add(stations.get(i));

        for (int i = 0; i < n && movers.size() < 3; i += Math.max(1, n / 3))
        {
            if (!standing.contains(stations.get(i))) movers.add(stations.get(i));
        }

        Locomotive mover = train(model, "PJ mover", 1);
        List<Locomotive> others = new ArrayList<>();

        for (int i = 0; i < standing.size(); i++) others.add(train(model, "PJ standing " + i, 3));

        String[] situations = {"alone", "standing", "long tails", "lit", "route held", "fits nowhere", "own tail",
            "locked edges"};
        List<Edge> edges = sortedByName(layout.getEdges());

        for (String situation : situations)
        {
            List<Runnable> undo = new ArrayList<>();

            if (!situation.equals("alone"))
            {
                for (int i = 0; i < standing.size(); i++)
                {
                    others.get(i).setTrainLength(situation.equals("long tails") ? 8 : 3);
                    stand(layout, standing.get(i), others.get(i));
                }
            }

            if (situation.equals("lit"))
            {
                for (int i = 0; i < n; i += 3)
                {
                    String s88 = stations.get(i).getS88();

                    if (s88 == null || model.getFeedbackState(s88)) continue;

                    model.setFeedbackState(s88, true);
                    undo.add(() -> model.setFeedbackState(s88, false));
                }
            }
            else if (situation.equals("route held"))
            {
                for (Point to : stations)
                {
                    List<List<Edge>> held = standing.contains(to) ? null : routes(layout, standing.get(0), to, 1);

                    if (held == null || held.isEmpty()) continue;

                    out.add("held " + names(held.get(0)));

                    for (Edge e : held.get(0))
                    {
                        e.setOccupied();
                        undo.add(e::setUnoccupied);
                    }

                    break;
                }
            }
            else if (situation.equals("fits nowhere"))
            {
                for (Point p : all)
                {
                    if (!p.isDestination()) continue;

                    Integer was = p.getMaxTrainLength();

                    p.setMaxTrainLength(1);
                    undo.add(() -> p.setMaxTrainLength(was));
                }
            }
            else if (situation.equals("locked edges"))
            {
                for (int i = 0; i < edges.size(); i += 5)
                {
                    Edge e = edges.get(i);

                    e.setOccupied();
                    undo.add(e::setUnoccupied);
                }
            }

            mover.setTrainLength(situation.equals("fits nowhere") ? 5 : situation.equals("own tail") ? 6 : 1);

            for (Point at : movers)
            {
                out.add("-- " + situation + ", from " + at.getName());

                stand(layout, at, mover);

                for (Layout.PathPreference preference : new Layout.PathPreference[] {Layout.PathPreference.RANDOM,
                    Layout.PathPreference.SHORTEST_LENGTH})
                {
                    layout.setPathPreference(preference);

                    List<String> said = new ArrayList<>();
                    Handler tap = support.LogTap.messagesInto(said);
                    Logger logger = Logger.getLogger(MarklinControlStation.class.getName());

                    // The station drops a line equal to the one before it, so a fresh line goes first.
                    model.log("PJ asking " + (address++));
                    logger.addHandler(tap);

                    List<Edge> picked;

                    try
                    {
                        picked = layout.pickPath(mover);
                    }
                    finally
                    {
                        logger.removeHandler(tap);
                    }

                    // The route a random rule takes among equals is arbitrary; its end and a ranked rule's length are
                    // not.  Only what was said about this train: the station logs on other threads.
                    said.removeIf(line -> !line.contains(mover.getName()));

                    out.add("pick " + preference + ": " + (picked == null ? "none"
                        : picked.get(picked.size() - 1).getEnd().getName() + (preference == Layout.PathPreference.RANDOM
                            ? "" : " length " + layout.getPathLength(picked)))
                        + (said.isEmpty() ? "" : " said " + String.join(" | ", said)));
                }

                for (Point end : stations)
                {
                    if (end.isSamePlaceAs(at)) continue;

                    for (List<Edge> route : routes(layout, at, end, 2))
                    {
                        boolean clear = layout.isPathClear(route, mover, false);

                        out.add(names(route) + ": " + (clear ? "clear" : Layout.getLastError()));
                    }
                }

                lift(at);
            }

            Collections.reverse(undo);

            for (Runnable r : undo) r.run();

            for (Point p : standing) lift(p);
        }
    }

    private static String dump() throws Exception
    {
        List<String> out = new ArrayList<>();
        Map<String, String> configs = new LinkedHashMap<>();

        for (String file : FILES)
        {
            configs.put(file, new String(Files.readAllBytes(new File(file).toPath()), StandardCharsets.UTF_8));
        }

        for (String name : support.Scenario.names())
        {
            support.Scenario scenario = null;

            try
            {
                scenario = support.Scenario.openSimulated(name);

                Layout layout;

                try
                {
                    layout = scenario.build();
                }
                catch (IllegalStateException e)
                {
                    layout = null;
                }

                judge(name, scenario.getModel(), layout, out);
            }
            finally
            {
                if (scenario != null) scenario.close();
            }
        }

        support.LayoutSandbox sandbox = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            MarklinControlStation model = support.TestStation.simulatedInDebug();

            model.stop();

            for (Map.Entry<String, String> config : configs.entrySet())
            {
                model.parseAuto(config.getValue());
                judge(config.getKey(), model, model.getAutoLayout(), out);
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }

        return String.join("\n", out) + "\n";
    }

    @Test
    public void testEveryCandidateIsJudgedAsBefore() throws Exception
    {
        String got = dump();

        support.GoldenCheck.check(got, GOLDEN, GOLDEN + ".changes", "pickpath_golden.txt");
        org.testng.Assert.assertTrue(got.split("\n").length > 3000, "the golden covers the railways: "
            + got.split("\n").length + " lines");
    }

    /** Writes the golden to the file named: run on the code before the change. */
    public static void main(String[] args) throws Exception
    {
        Files.write(new File(args[0]).toPath(), dump().getBytes(StandardCharsets.UTF_8));
        System.exit(0);
    }
}
