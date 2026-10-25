package org.traincontrol.automationui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * Where trains can get to, read off the built graph: the Points and Edges the railway routes over.  The one source
 * for every finding about reachability.
 *
 * The turn sets and the barred arrivals are already in which copies and edges the build emitted, and a square out of
 * service is a copy built inactive, so nothing here re-applies an authored set.  The answer is per COPY: one copy of
 * a square can reach nothing while another reaches plenty, so a square's answer is combined from the copies a train
 * stops at there (`bySquare`).
 */
public final class BuiltReach
{
    private final Map<String, List<String>> onward = new HashMap<>();
    private final Set<String> stations = new HashSet<>();
    private final Set<String> inactive = new HashSet<>();
    private final Map<String, TileKey> squareOf;
    private final Map<TileKey, List<String>> copiesOf = new LinkedHashMap<>();

    private BuiltReach(JSONObject built, Map<String, TileKey> squareOf)
    {
        this.squareOf = squareOf;

        JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            JSONObject point = points.getJSONObject(i);
            String name = point.getString("name");

            if (point.optBoolean("station", false)) stations.add(name);

            // As parseAuto reads it: only a boolean false switches a Point off
            if (Boolean.FALSE.equals(point.opt("active"))) inactive.add(name);

            TileKey square = squareOf.get(name);

            if (square != null) copiesOf.computeIfAbsent(square, k -> new ArrayList<String>()).add(name);
        }

        JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            JSONObject edge = edges.getJSONObject(i);

            onward.computeIfAbsent(edge.getString("start"), k -> new ArrayList<String>()).add(edge.getString("end"));
        }
    }

    /**
     * @param built the configuration as the builder emitted it, or null
     * @param squareOf the builder's own name-to-square map (`AutonomyBuilder.tilesByName`)
     * @return the reach of that build, or null where there is no build to read
     */
    public static BuiltReach of(JSONObject built, Map<String, TileKey> squareOf)
    {
        if (built == null || squareOf == null || !built.has("points") || !built.has("edges")) return null;

        return new BuiltReach(built, squareOf);
    }

    /**
     * The squares, other than its own, where a train starting on this copy can be sent to a station copy.
     *
     * With `closures`, a copy out of service is neither a way through nor somewhere to stop, as `isPathClear` refuses
     * it; the start is exempt, since a train standing on a square switched off is driven off it by hand.
     *
     * @param copy the Point a train stands on
     * @param closures whether squares out of service stop the walk
     * @return the squares reached
     */
    public Set<TileKey> stationsReachedFrom(String copy, boolean closures)
    {
        Set<TileKey> reached = new LinkedHashSet<>();
        TileKey home = squareOf.get(copy);

        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        seen.add(copy);
        queue.add(copy);

        while (!queue.isEmpty())
        {
            for (String next : onward.getOrDefault(queue.poll(), Collections.<String>emptyList()))
            {
                if (closures && inactive.contains(next)) continue;

                if (!seen.add(next)) continue;

                TileKey square = squareOf.get(next);

                if (stations.contains(next) && square != null && !square.equals(home)) reached.add(square);

                queue.add(next);
            }
        }

        return reached;
    }

    /**
     * Each square to the squares reached from the copies a train stops at there (`startsOf`), out-of-service squares
     * obeyed.  A barred copy that reaches plenty therefore does not hide a station copy that reaches nothing.
     *
     * @return square to the squares reached
     */
    public Map<TileKey, Set<TileKey>> bySquare()
    {
        Map<TileKey, Set<TileKey>> out = new LinkedHashMap<>();

        for (TileKey square : copiesOf.keySet())
        {
            Set<TileKey> reached = new LinkedHashSet<>();

            for (String copy : startsOf(square)) reached.addAll(stationsReachedFrom(copy, true));

            out.put(square, reached);
        }

        return out;
    }

    /**
     * The copies a train at this square starts from: its station copies, as a train sent there stands on one of them;
     * where it has none (track, or a station barred every way in), every copy.
     *
     * @param square the square
     * @return the copies, empty for a square the build has none of
     */
    public List<String> startsOf(TileKey square)
    {
        List<String> copies = copiesOf.getOrDefault(square, Collections.<String>emptyList());

        List<String> starts = new ArrayList<>();

        for (String copy : copies)
        {
            if (stations.contains(copy)) starts.add(copy);
        }

        return starts.isEmpty() ? copies : starts;
    }

    /** @return whether an edge leaves this copy */
    public boolean leaves(String copy)
    {
        return onward.containsKey(copy);
    }

    /** @return whether this copy is somewhere a train may be sent to stop */
    public boolean isStation(String copy)
    {
        return stations.contains(copy);
    }
}
