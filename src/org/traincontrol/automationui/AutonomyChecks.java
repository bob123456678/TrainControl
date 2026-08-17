package org.traincontrol.automationui;

import org.traincontrol.base.LayoutDiagramComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.GraphReducer.ReducedPoint;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * Checks a user can run against a configuration before trusting trains to it.
 *
 * These are the questions that are obvious once asked and invisible until then: a terminus nothing can
 * leave, a station no train can reach, a run somebody has closed in both directions.  Every one of them
 * produces a configuration that loads, validates and runs - and then quietly never sends a train
 * somewhere, which is the kind of fault people spend an evening chasing on the baseboard.
 *
 * Reported as a list of findings rather than as a pass or a fail.  A layout mid-setup is expected to
 * have some of these, and a check that says only "no" is a check people stop running.
 *
 * @author Adam
 */
public class AutonomyChecks
{
    /**
     * How much a finding matters.
     */
    public static enum Severity
    {
        /**
         * Autonomy cannot be built at all until this is fixed.
         */
        ERROR,

        /**
         * It will build and run, but something will not work the way it looks like it should.
         */
        WARNING,

        /**
         * Worth knowing, not worth fixing.
         */
        INFO
    }

    /**
     * One thing worth telling the user, in terms they can act on.
     */
    public static class Finding
    {
        private final Severity severity;
        private final String messageKey;
        private final String subject;
        private final TileKey tile;

        Finding(Severity severity, String messageKey, String subject, TileKey tile)
        {
            this.severity = severity;
            this.messageKey = messageKey;
            this.subject = subject;
            this.tile = tile;
        }

        public Severity getSeverity()
        {
            return severity;
        }

        /**
         * A message bundle key, so presentation stays with whatever is presenting.
         * @return
         */
        public String getMessageKey()
        {
            return messageKey;
        }

        /**
         * What the finding is about - usually a Point name.
         * @return
         */
        public String getSubject()
        {
            return subject;
        }

        /**
         * Where to look on the diagram, or null if it is not about one place.
         * @return
         */
        public TileKey getTile()
        {
            return tile;
        }

        @Override
        public String toString()
        {
            return severity + " " + messageKey + " [" + subject + "]"
                + (tile == null ? "" : " at " + tile);
        }
    }

    public static final String STATION_REACHES_NOTHING = "autosetup.ui.checkStationReachesNothing";
    public static final String STATION_UNREACHABLE = "autosetup.ui.checkStationUnreachable";
    public static final String TERMINUS_STRANDED = "autosetup.ui.checkTerminusStranded";
    public static final String POINT_ISOLATED = "autosetup.ui.checkPointIsolated";
    public static final String RUN_CLOSED_BOTH_WAYS = "autosetup.ui.checkRunClosedBothWays";
    public static final String NO_STATIONS = "autosetup.ui.checkNoStations";
    public static final String ONE_STATION = "autosetup.ui.checkOneStation";

    private AutonomyChecks()
    {
    }

    /**
     * Runs every check.
     *
     * @param graph the tile graph, for the problems the diagram itself has
     * @param reducer the reduction, already run
     * @return everything found, most serious first
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer)
    {
        return run(graph, reducer, Collections.<TileKey>emptySet());
    }

    /**
     * @param termini the Points the user marked as termini
     *
     * A terminus used to be inferred here as "has no outgoing edge", which is a different thing from
     * what the user marked - so a marked terminus that reaches no station got the generic message, and
     * an ordinary dead end got the terminus one.  The flag lives in the configuration, so it is passed
     * in rather than guessed at.
     */
    public static List<Finding> run(TileGraph graph, GraphReducer reducer, Set<TileKey> termini)
    {
        List<Finding> findings = new ArrayList<>();

        // whatever the diagram itself is unhappy about - scissors, unaddressed switches, turntables
        for (TileGraph.Problem problem : graph.getProblems())
        {
            findings.add(new Finding(
                problem.isBlocking() ? Severity.ERROR : Severity.WARNING,
                problem.getMessageKey(), String.valueOf(problem.getTile()), problem.getTile()));
        }

        for (TileGraph.Problem problem : reducer.getProblems())
        {
            findings.add(new Finding(
                problem.isBlocking() ? Severity.ERROR : Severity.WARNING,
                problem.getMessageKey(), String.valueOf(problem.getTile()), problem.getTile()));
        }

        findings.addAll(checkStations(reducer, termini));
        findings.addAll(checkIsolatedPoints(reducer));
        findings.addAll(checkClosedRuns(graph, reducer));

        Collections.sort(findings, new java.util.Comparator<Finding>()
        {
            @Override
            public int compare(Finding a, Finding b)
            {
                return a.getSeverity().ordinal() - b.getSeverity().ordinal();
            }
        });

        return findings;
    }

    /**
     * Can trains actually get between the stations?
     *
     * A train may pass through a non-station but only ever stop at a station, so this is the question the
     * layout exists to answer - and a station that can reach nothing is not a station anybody can use,
     * however well connected its track is.
     */
    private static List<Finding> checkStations(GraphReducer reducer, Set<TileKey> termini)
    {
        List<Finding> findings = new ArrayList<>();

        Map<TileKey, Set<TileKey>> adjacency = adjacency(reducer);

        List<ReducedPoint> stations = new ArrayList<>();

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (point.isStation()) stations.add(point);
        }

        if (stations.isEmpty())
        {
            findings.add(new Finding(Severity.WARNING, NO_STATIONS, "", null));
            return findings;
        }

        if (stations.size() == 1)
        {
            // trains have nowhere to go, which is not an error but is certainly not what was meant
            findings.add(new Finding(Severity.WARNING, ONE_STATION,
                stations.get(0).getName(), stations.get(0).getTile()));
            return findings;
        }

        Set<TileKey> stationTiles = new LinkedHashSet<>();

        for (ReducedPoint station : stations)
        {
            stationTiles.add(station.getTile());
        }

        for (ReducedPoint station : stations)
        {
            Set<TileKey> reachable = reachableFrom(adjacency, station.getTile());

            boolean reachesAStation = false;

            for (TileKey other : stationTiles)
            {
                if (!other.equals(station.getTile()) && reachable.contains(other))
                {
                    reachesAStation = true;
                    break;
                }
            }

            if (!reachesAStation)
            {
                // A terminus that cannot be left is the specific case worth naming: a train sent there
                // is stuck, and the layout will look like it simply stopped using that station.
                findings.add(new Finding(Severity.WARNING,
                    isTerminus(reducer, station, termini)
                        ? TERMINUS_STRANDED : STATION_REACHES_NOTHING,
                    station.getName(), station.getTile()));
            }
        }

        // and the other direction: a station nothing can reach can never be a destination
        for (ReducedPoint station : stations)
        {
            boolean reachable = false;

            for (ReducedPoint other : stations)
            {
                if (other == station) continue;

                if (reachableFrom(adjacency, other.getTile()).contains(station.getTile()))
                {
                    reachable = true;
                    break;
                }
            }

            if (!reachable)
            {
                findings.add(new Finding(Severity.WARNING, STATION_UNREACHABLE,
                    station.getName(), station.getTile()));
            }
        }

        return findings;
    }

    /**
     * A Point with no edges at all.
     *
     * The reducer already leaves out sensors with nothing beside them, so anything here is a sensor whose
     * track exists but whose every connection has been closed - which is a decision somebody made and may
     * not have meant.
     */
    private static List<Finding> checkIsolatedPoints(GraphReducer reducer)
    {
        List<Finding> findings = new ArrayList<>();

        Set<TileKey> touched = new HashSet<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            touched.add(edge.getStart());
            touched.add(edge.getEnd());
        }

        for (ReducedPoint point : reducer.getPoints().values())
        {
            if (!touched.contains(point.getTile()))
            {
                findings.add(new Finding(Severity.WARNING, POINT_ISOLATED,
                    point.getName(), point.getTile()));
            }
        }

        return findings;
    }

    /**
     * Track that has been closed in both directions.
     *
     * Almost always a mis-click: somebody meant to make a run one way and cycled past it.  The tile is
     * still drawn, so nothing looks wrong, and the route through it simply stops existing.
     */
    private static List<Finding> checkClosedRuns(TileGraph graph, GraphReducer reducer)
    {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : graph.getTiles().entrySet())
        {
            Map<TileGraph.RouteId, TilePorts.Route> routes = graph.getRoutes(entry.getKey());

            if (routes.isEmpty()) continue;

            boolean anyOpen = false;

            for (TileGraph.RouteId routeId : routes.keySet())
            {
                if (graph.getDirection(entry.getKey(), routeId) != TileGraph.Direction.NONE)
                {
                    anyOpen = true;
                    break;
                }
            }

            if (!anyOpen)
            {
                findings.add(new Finding(Severity.INFO, RUN_CLOSED_BOTH_WAYS,
                    String.valueOf(entry.getKey()), entry.getKey()));
            }
        }

        return findings;
    }

    private static boolean isTerminus(GraphReducer reducer, ReducedPoint station, Set<TileKey> termini)
    {
        // What the user marked comes first; a dead end is the fallback for a Point nobody marked.
        if (termini.contains(station.getTile())) return true;

        for (ReducedEdge edge : reducer.getEdges())
        {
            if (edge.getStart().equals(station.getTile())) return false;
        }

        return true;
    }

    private static Map<TileKey, Set<TileKey>> adjacency(GraphReducer reducer)
    {
        Map<TileKey, Set<TileKey>> out = new LinkedHashMap<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            Set<TileKey> next = out.get(edge.getStart());

            if (next == null)
            {
                next = new LinkedHashSet<>();
                out.put(edge.getStart(), next);
            }

            next.add(edge.getEnd());
        }

        return out;
    }

    private static Set<TileKey> reachableFrom(Map<TileKey, Set<TileKey>> adjacency, TileKey start)
    {
        Set<TileKey> seen = new LinkedHashSet<>();
        LinkedList<TileKey> queue = new LinkedList<>();

        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty())
        {
            Set<TileKey> next = adjacency.get(queue.removeFirst());

            if (next == null) continue;

            for (TileKey neighbour : next)
            {
                if (seen.add(neighbour)) queue.add(neighbour);
            }
        }

        return seen;
    }
}
