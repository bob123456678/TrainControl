package org.traincontrol.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.GraphReducer.ReducedEdge;
import org.traincontrol.base.GraphReducer.ReducedPoint;
import org.traincontrol.base.TileGraph.TileKey;

/**
 * The compile step: turns a reduced diagram into the autonomy JSON the existing model already reads.
 *
 * Nothing new is taught to the autonomy model.  The output goes through the same parseAuto that a
 * hand-written file went through, so validation, path finding, locking and running are all untouched -
 * what changes is only where the file comes from.
 *
 * Output is deterministic: the same diagram and the same authored data produce byte-identical JSON, so
 * two builds can be diffed against each other and against a hand-written configuration.
 *
 * @author Adam
 */
public class AutonomyBuilder
{
    /**
     * The settings that belong to a configuration rather than to the track: pace, speeds, and the rest of
     * what used to sit at the top of a hand-written autonomy file.
     */
    public static class Globals
    {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Globals()
        {
            // the three parseAuto insists on, at the model's own defaults
            values.put("minDelay", 1);
            values.put("maxDelay", 5);
            values.put("defaultLocSpeed", 35);
        }

        public Globals set(String key, Object value)
        {
            values.put(key, value);
            return this;
        }

        Map<String, Object> getValues()
        {
            return values;
        }
    }

    private final GraphReducer reducer;
    private final Globals globals;

    private List<String> coordinatePages = null;

    // Per-point operational data from the active configuration - placements, homes, termini and the
    // rest - keyed by TileKey.toString().  See withPointExtras.
    private Map<String, JSONObject> pointExtras = null;

    public AutonomyBuilder(GraphReducer reducer, Globals globals)
    {
        this.reducer = reducer;
        this.globals = globals == null ? new Globals() : globals;
    }

    /**
     * Emits graph coordinates for each Point, taken from where its tile sits on the diagram.
     *
     * Off by default: the diagram is the layout now, so nothing needs a second set of positions, and a
     * stale one would only drift.  It is worth having for **inspection** - a graph laid out like the track
     * it came from can be read at a glance and checked against the diagram beside it, where the same graph
     * sprayed at random cannot be checked against anything.
     *
     * Pages are stacked vertically in the order given, so two pages do not land on top of each other.
     *
     * @param pagesInOrder the participating page names, or null to stop emitting coordinates
     * @return this
     */
    public AutonomyBuilder withCoordinatesFromTiles(List<String> pagesInOrder)
    {
        this.coordinatePages = pagesInOrder;
        return this;
    }

    /**
     * Merges per-point operational data into the generated Points.
     *
     * This is how a configuration differs from the track: where the locomotives start, which Points are
     * termini or reversing, homes, exclusions, speed multipliers.  The keys are TileKey.toString(), so
     * the data survives a Point being renamed; the values are whatever parseAuto accepts on a point.
     *
     * What the reduction itself decides - name, station, s88 - cannot be overridden from here, because
     * a configuration that quietly changed the track would be the JSON window all over again.
     *
     * @param extras tile key string to the point's extra properties, or null for none
     * @return this
     */
    public AutonomyBuilder withPointExtras(Map<String, JSONObject> extras)
    {
        this.pointExtras = extras;
        return this;
    }

    /**
     * Builds the autonomy JSON.
     *
     * @return a string suitable for parseAuto
     */
    public String build()
    {
        JSONObject root = new JSONObject();

        for (Map.Entry<String, Object> entry : globals.getValues().entrySet())
        {
            root.put(entry.getKey(), entry.getValue());
        }

        // Names are what the model keys on, so they have to be unique.  Two sensors legitimately share an
        // s88 - a station and its approach guards - so uniqueness is enforced here rather than assumed.
        Map<TileKey, String> names = uniqueNames();

        List<ReducedPoint> points = new ArrayList<>(reducer.getPoints().values());
        Collections.sort(points, new Comparator<ReducedPoint>()
        {
            @Override
            public int compare(ReducedPoint a, ReducedPoint b)
            {
                return a.getTile().toString().compareTo(b.getTile().toString());
            }
        });

        JSONArray pointList = new JSONArray();

        for (ReducedPoint point : points)
        {
            JSONObject json = new JSONObject();

            json.put("name", names.get(point.getTile()));
            json.put("station", point.isStation());
            json.put("s88", point.getS88());

            if (coordinatePages != null)
            {
                // Roughly one tile per 60 units, which is the spacing the hand-written files use, with
                // each page stacked below the last so they do not overlap
                int page = Math.max(0, coordinatePages.indexOf(point.getTile().getPage()));

                json.put("x", point.getTile().getX() * 60);
                json.put("y", point.getTile().getY() * 60 + page * 1800);
            }

            JSONObject extras = pointExtras == null
                ? null : pointExtras.get(point.getTile().toString());

            if (extras != null)
            {
                for (String key : extras.keySet())
                {
                    // never the structural fields: those are the reduction's to decide
                    if (json.has(key)) continue;

                    json.put(key, extras.get(key));
                }
            }

            pointList.put(json);
        }

        root.put("points", pointList);

        List<ReducedEdge> edges = new ArrayList<>(reducer.getEdges());
        Collections.sort(edges, new Comparator<ReducedEdge>()
        {
            @Override
            public int compare(ReducedEdge a, ReducedEdge b)
            {
                int byStart = a.getStart().toString().compareTo(b.getStart().toString());

                if (byStart != 0) return byStart;

                return a.getEnd().toString().compareTo(b.getEnd().toString());
            }
        });

        JSONArray edgeList = new JSONArray();

        for (ReducedEdge edge : edges)
        {
            JSONObject json = new JSONObject();

            json.put("start", names.get(edge.getStart()));
            json.put("end", names.get(edge.getEnd()));
            json.put("length", edge.getLength());

            JSONArray commands = new JSONArray();

            List<String> accessoryNames = new ArrayList<>(edge.getCommands().keySet());
            Collections.sort(accessoryNames);

            for (String accessory : accessoryNames)
            {
                accessorySetting setting = edge.getCommands().get(accessory);

                JSONObject command = new JSONObject();
                command.put("acc", accessory);
                command.put("state", setting.toString().toLowerCase());
                commands.put(command);
            }

            if (commands.length() > 0)
            {
                json.put("commands", commands);
            }

            JSONArray lockEdges = new JSONArray();

            Set<ReducedEdge> locked = reducer.getLocks().get(edge);

            if (locked != null)
            {
                List<ReducedEdge> sorted = new ArrayList<>(locked);
                Collections.sort(sorted, new Comparator<ReducedEdge>()
                {
                    @Override
                    public int compare(ReducedEdge a, ReducedEdge b)
                    {
                        int byStart = a.getStart().toString().compareTo(b.getStart().toString());

                        if (byStart != 0) return byStart;

                        return a.getEnd().toString().compareTo(b.getEnd().toString());
                    }
                });

                for (ReducedEdge other : sorted)
                {
                    JSONObject lock = new JSONObject();
                    lock.put("start", names.get(other.getStart()));
                    lock.put("end", names.get(other.getEnd()));
                    lockEdges.put(lock);
                }
            }

            if (lockEdges.length() > 0)
            {
                json.put("lockedges", lockEdges);
            }

            edgeList.put(json);
        }

        root.put("edges", edgeList);

        return root.toString(2);
    }

    /**
     * The name each Point will carry in the generated file, disambiguated where two collide.
     *
     * A user who names two Points the same thing is told at authoring time; this is the backstop for
     * generated names, and for the case where a rename has not yet been applied everywhere.
     */
    public Map<TileKey, String> uniqueNames()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();

        List<ReducedPoint> points = new ArrayList<>(reducer.getPoints().values());
        Collections.sort(points, new Comparator<ReducedPoint>()
        {
            @Override
            public int compare(ReducedPoint a, ReducedPoint b)
            {
                return a.getTile().toString().compareTo(b.getTile().toString());
            }
        });

        for (ReducedPoint point : points)
        {
            String base = point.getName();
            Integer count = seen.get(base);

            if (count == null)
            {
                seen.put(base, 1);
                out.put(point.getTile(), base);
            }
            else
            {
                seen.put(base, count + 1);
                out.put(point.getTile(), base + " (" + (count + 1) + ")");
            }
        }

        return out;
    }
}
