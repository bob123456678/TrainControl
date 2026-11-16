package org.traincontrol.automationui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.automationui.GraphReducer.Reached;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessorySetting;

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
    /** Each copy to the edges leaving it: the copy each ends at and the switch settings it needs. */
    private final Map<String, List<Way>> onward = new HashMap<>();
    private final Set<String> stations = new HashSet<>();
    /** Each copy's place, which the railway never passes twice on a way to or from it: its block, else itself. */
    private final Map<String, String> placeOf = new HashMap<>();
    private final Set<String> stationPlaces = new HashSet<>();
    private final Map<String, TileKey> squareOf;
    private final Set<String> inactive = new HashSet<>();
    private final Set<String> stopOnly = new HashSet<>();
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

            if (Boolean.FALSE.equals(point.opt(org.traincontrol.automation.Point.RUN_THROUGH))) stopOnly.add(name);

            TileKey square = squareOf.get(name);

            placeOf.put(name, point.optString("block", name));

            if (point.optBoolean("station", false)) stationPlaces.add(placeOf.get(name));

            if (square != null) copiesOf.computeIfAbsent(square, k -> new ArrayList<String>()).add(name);
        }

        JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            JSONObject edge = edges.getJSONObject(i);
            JSONArray commands = edge.optJSONArray("commands");
            Map<String, accessorySetting> settings = new HashMap<>();

            for (int j = 0; commands != null && j < commands.length(); j++)
            {
                settings.put(commands.getJSONObject(j).getString("acc"),
                    accessorySetting.valueOf(commands.getJSONObject(j).getString("state").toUpperCase(Locale.ROOT)));
            }

            onward.computeIfAbsent(edge.getString("start"), k -> new ArrayList<Way>())
                .add(new Way(edge.getString("end"), settings));
        }
    }

    /**
     * The reach of the build the builder emitted, or null where there is no build to read; `squareOf` is
     * `AutonomyBuilder.tilesByName`.
     */
    public static BuiltReach of(JSONObject built, Map<String, TileKey> squareOf)
    {
        if (built == null || squareOf == null || !built.has("points") || !built.has("edges")) return null;

        return new BuiltReach(built, squareOf);
    }

    /**
     * The squares, other than its own, where a train starting on this copy can be sent to a station copy, searched as
     * the railway routes (`GraphReducer.searchBySettings`): never past another copy of either end, nor throwing a
     * switch two ways (FR-238).  With `closures`, a copy out of service is neither a way through nor somewhere to stop,
     * as `isPathClear` refuses it, and a stop-only station is somewhere to stop and no way through (FR-111); the start
     * is exempt, since a train on a square switched off is driven off it by hand.
     */
    public Set<TileKey> stationsReachedFrom(String copy, boolean closures)
    {
        return reach(copy, closures, false);
    }

    /** Whether a train starting on this copy can be sent to any station copy, closures ignored: the first will do. */
    public boolean reachesAStation(String copy)
    {
        return !reach(copy, false, true).isEmpty();
    }

    private Set<TileKey> reach(String copy, boolean closures, boolean firstOnly)
    {
        Set<TileKey> reached = new LinkedHashSet<>();
        TileKey home = squareOf.get(copy);
        String start = placeOf.get(copy);

        GraphReducer.<Place, Way>searchBySettings(Collections.singleton(new Place(copy, Collections.<String>emptySet())),
            here -> onward.getOrDefault(here.copy, Collections.<Way>emptyList()), (here, way) -> here.then(way.end),
            way -> way.settings, here -> Arrays.asList(here.copy, here.passed), here ->
            {
                TileKey square = squareOf.get(here.copy);

                if (closures && inactive.contains(here.copy) || placeOf.get(here.copy).equals(start)) return Reached.REFUSE;

                if (stations.contains(here.copy) && square != null && !square.equals(home)
                    && !here.passed.contains(placeOf.get(here.copy)))
                {
                    reached.add(square);

                    if (firstOnly) return Reached.ARRIVE;
                }

                return closures && stopOnly.contains(here.copy) ? Reached.STOP : Reached.PASS;
            });

        return reached;
    }

    /** A copy reached, and the station places whose other copies the way there has passed, no longer to stop at. */
    private final class Place
    {
        final String copy;
        final Set<String> passed;

        Place(String copy, Set<String> passed)
        {
            this.copy = copy;
            this.passed = passed;
        }

        Place then(String next)
        {
            if (stations.contains(next) || !stationPlaces.contains(placeOf.get(next))) return new Place(next, passed);

            Set<String> more = new HashSet<>(passed);

            more.add(placeOf.get(next));

            return new Place(next, more);
        }
    }

    /** An edge of the build as the search walks it. */
    private static final class Way
    {
        final String end;
        final Map<String, accessorySetting> settings;

        Way(String end, Map<String, accessorySetting> settings)
        {
            this.end = end;
            this.settings = settings;
        }
    }

    /**
     * Each square to the squares reached from the copies a train stops at there (`startsOf`), out-of-service squares
     * obeyed, so a barred copy that reaches plenty does not hide a station copy that reaches nothing.
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
