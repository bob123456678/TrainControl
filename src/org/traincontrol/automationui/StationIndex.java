package org.traincontrol.automationui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * The translation between the squares a user sees and the Points a railway runs.  One square becomes several Points,
 * one per arrival side, and nothing in the running model remembers they are the same platform, so every part of the
 * interface that shows a station has to translate.  Derived once from one configured builder, so the copies cannot
 * disagree.  Immutable; the session rebuilds it when the setup changes.
 *
 * @author Adam
 */
public final class StationIndex
{
    /** What to use before anything has been derived: "I have never heard of it" to everything, not a null. */
    public static final StationIndex EMPTY = new StationIndex(null);

    private final Map<TileKey, String> nameBySquare;
    private final Map<String, TileKey> squareByPoint;
    private final Map<String, String> baseByPoint;
    private final Map<TileKey, List<String>> pointsBySquare;
    private final Map<String, Side> facingByPoint;
    private final Map<TileKey, List<Side>> arrivalsBySquare;

    /**
     * @param builder configured exactly as the one that generated the running configuration, or null for the empty
     * index: whether a square splits depends on the reversible and mandatory-turn sets
     */
    StationIndex(AutonomyBuilder builder)
    {
        if (builder == null)
        {
            nameBySquare = Collections.emptyMap();
            squareByPoint = Collections.emptyMap();
            baseByPoint = Collections.emptyMap();
            pointsBySquare = Collections.emptyMap();
            facingByPoint = Collections.emptyMap();
            arrivalsBySquare = Collections.emptyMap();

            return;
        }

        nameBySquare = Collections.unmodifiableMap(new LinkedHashMap<>(builder.uniqueNames()));
        squareByPoint = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tilesByName()));
        baseByPoint = Collections.unmodifiableMap(new LinkedHashMap<>(builder.baseNames()));
        facingByPoint = Collections.unmodifiableMap(new LinkedHashMap<>(builder.facingByName()));

        Map<TileKey, List<String>> grouped = new LinkedHashMap<>();

        // In emission order, so "the first copy" means the same thing to everything that asks.
        for (Map.Entry<String, TileKey> entry : squareByPoint.entrySet())
        {
            List<String> here = grouped.get(entry.getValue());

            if (here == null)
            {
                here = new ArrayList<>();
                grouped.put(entry.getValue(), here);
            }

            here.add(entry.getKey());
        }

        for (Map.Entry<TileKey, List<String>> entry : grouped.entrySet())
        {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }

        pointsBySquare = Collections.unmodifiableMap(grouped);

        // From the same derivation as everything else: computed per call this was a builder pass per station per
        // repaint.
        Map<TileKey, List<Side>> arrivals = new LinkedHashMap<>();

        for (TileKey square : grouped.keySet())
        {
            List<Side> sides = new ArrayList<>();

            for (Side side : builder.arrivalSidesOf(square))
            {
                if (!sides.contains(side)) sides.add(side);
            }

            arrivals.put(square, Collections.unmodifiableList(sides));
        }

        arrivalsBySquare = Collections.unmodifiableMap(arrivals);
    }

    /**
     * The sides a train can arrive at a square by: the same answer the split itself uses, so nothing can offer a
     * restriction on a side the build has no copy for.
     *
     * @param square
     * @return the arrival sides, empty when the square never splits
     */
    public List<Side> arrivalSidesAt(TileKey square)
    {
        List<Side> sides = square == null ? null : arrivalsBySquare.get(square);

        return sides == null ? Collections.<Side>emptyList() : sides;
    }

    /**
     * What the square is called - the name a caption carries and a menu shows.
     *
     * @param square
     * @return the base name, or null when the square is not a Point of this setup
     */
    public String nameOf(TileKey square)
    {
        return square == null ? null : nameBySquare.get(square);
    }

    /**
     * The square a running Point stands on.
     *
     * @param pointName the name the running configuration knows, split copies included
     * @return the square, or null when this setup never emitted that name
     */
    public TileKey squareOf(String pointName)
    {
        return pointName == null ? null : squareByPoint.get(pointName);
    }

    /**
     * @param point a running Point
     * @return its square, or null
     */
    public TileKey squareOf(Point point)
    {
        return point == null ? null : squareOf(point.getName());
    }

    /**
     * What to call a running Point on screen: the base name, so a station reads as one place however many copies it
     * has; the Point's own name for one this index has never heard of.
     *
     * @param point
     * @return a name fit to show, never null
     */
    public String describe(Point point)
    {
        return point == null ? "" : baseNameOf(point.getName());
    }

    /**
     * Whether a name ends the way the builder names one direction of a square, and so cannot be told from one by
     * anything that reads it back: a lowercase `(northbound)`, `(southbound)`, `(eastbound)` or `(westbound)` at the
     * very end, with a comma and anything after it allowed inside the bracket (`, reverse` is the builder's own), as
     * `withoutArrivalSuffix` reads the heading up to the comma.  Loosen this and the two stop agreeing.  Adam:
     * *"refuse and close, but make sure the words are uncommon."*  So "Yard (old)" and "Main Line (Northbound)" are
     * still names a person may give; "Main (eastbound)" is not.
     *
     * @param name a name somebody typed
     * @return true when it ends in the builder's heading
     */
    public static boolean endsWithAnArrivalHeading(String name)
    {
        if (name == null) return false;

        String trimmed = name.trim();

        return !withoutArrivalSuffix(trimmed).equals(trimmed);
    }

    /**
     * A copy's name with the arrival side taken off, for when the index cannot answer: a Point built by a different
     * run of the builder is not in the map.  Only the suffixes the builder emits are removed, and only from the end:
     * a station called "Yard (upper)" keeps its name.
     *
     * @param name an emitted Point name
     * @return the square's name, as far as it can be recovered
     */
    public static String withoutArrivalSuffix(String name)
    {
        if (name == null) return "";

        return org.traincontrol.automation.Layout.withoutArrivalSuffix(name);
    }

    /**
     * What a Point is called, as a place rather than as a copy: `describe`'s implementation.  Both fall back to
     * `withoutArrivalSuffix` when the index has never heard of a Point.  A blank base counts as no answer.
     *
     * @param pointName an emitted Point name
     * @return the base name behind a copy, the name with its arrival side removed when the index
     *         cannot answer, and null only for null
     */
    public String baseNameOf(String pointName)
    {
        if (pointName == null) return null;

        String base = baseByPoint.get(pointName);

        if (base != null && !base.trim().isEmpty()) return base;

        return withoutArrivalSuffix(pointName);
    }

    /**
     * Every Point emitted for one square, in emission order.
     *
     * @param square
     * @return the names, empty when the square is not a Point
     */
    public List<String> pointNamesAt(TileKey square)
    {
        List<String> names = square == null ? null : pointsBySquare.get(square);

        return names == null ? Collections.<String>emptyList() : names;
    }

    /**
     * Every Point emitted for the square a base name belongs to.
     *
     * @param baseName
     * @return the names, empty when nothing carries that base name
     */
    public List<String> pointNamesFor(String baseName)
    {
        List<String> out = new ArrayList<>();

        if (baseName == null) return out;

        for (Map.Entry<String, String> entry : baseByPoint.entrySet())
        {
            if (baseName.equals(entry.getValue())) out.add(entry.getKey());
        }

        return out;
    }

    /**
     * Which way a train standing on each copy of a square would be pointing.
     *
     * @param square
     * @return copy name to the side its train faces, in emission order
     */
    public Map<String, Side> facingsAt(TileKey square)
    {
        Map<String, Side> out = new LinkedHashMap<>();

        for (String name : pointNamesAt(square))
        {
            Side facing = facingByPoint.get(name);

            if (facing != null) out.put(name, facing);
        }

        return out;
    }

    /**
     * Whether two Points are the same piece of track, by square rather than by name: the copies are named apart on
     * purpose.
     *
     * @param a a running Point name
     * @param b another
     * @return true when both stand on one square
     */
    public boolean sameSquare(String a, String b)
    {
        if (a == null || b == null) return false;

        // Identical names are the same place even when the index has never heard of either, or a path from P back to P
        // would be offered as a journey.
        if (a.equals(b)) return true;

        TileKey first = squareOf(a);
        TileKey second = squareOf(b);

        return first != null && first.equals(second);
    }

    /**
     * Every running Point standing on a square.
     *
     * @param layout the running layout, or null
     * @param square
     * @return the Points, in emission order, skipping any the layout does not have
     */
    public List<Point> pointsAt(Layout layout, TileKey square)
    {
        List<Point> out = new ArrayList<>();

        if (layout == null) return out;

        for (String name : pointNamesAt(square))
        {
            Point point = layout.getPoint(name);

            if (point != null) out.add(point);
        }

        // Or by the square each Point was built for, where the railway knows none of the setup's names (a station
        // renamed while the setup cannot build).  Names first, so a renamed page is still found by them.
        if (out.isEmpty() && square != null)
        {
            String built = square.toString();

            for (Point point : layout.getPoints())
            {
                if (built.equals(point.getSquare())) out.add(point);
            }
        }

        return out;
    }

    /**
    /**
     * The Points of a square that are actually holding a locomotive.  More than one is possible: two trains can be
     * sent to one platform from opposite ends, each on the copy that faces its own way.
     *
     * @param layout
     * @param square
     * @return the occupied Points, in emission order
     */
    public List<Point> occupantsAt(Layout layout, TileKey square)
    {
        List<Point> out = new ArrayList<>();

        for (Point point : pointsAt(layout, square))
        {
            if (point.getCurrentLocomotive() != null) out.add(point);
        }

        return oneEntryPerLocomotive(out);
    }

    /**
     * The same list with each locomotive appearing once.  Locking a path reserves every point along it for that train
     * without taking it off anywhere else, so a path through two copies of one square puts the train on both: right
     * for the model, wrong for a caption.  Two trains on one platform is real; one train shown as two is a mistake.
     * The first copy is kept.
     *
     * @param points occupied Points, in emission order
     * @return the same, with duplicates of one locomotive removed
     */
    public static List<Point> oneEntryPerLocomotive(List<Point> points)
    {
        List<Point> out = new ArrayList<>();

        java.util.Set<org.traincontrol.base.Locomotive> seen =
            java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<org.traincontrol.base.Locomotive, Boolean>());

        for (Point point : points)
        {
            if (point == null || point.getCurrentLocomotive() == null) continue;

            // By identity: two locomotives can share a name in this application.
            if (seen.add(point.getCurrentLocomotive())) out.add(point);
        }

        return out;
    }

    /**
    /**
     * The one Point that speaks for a square: an occupied copy in preference to an empty one, else a destination
     * copy, else the first.  The first is a choice, not an equivalence (copies differ in whether a train may stop on
     * them), so anything asking what may be done here must look at the copy it means.  With several trains it answers
     * for the first; callers that can show more should ask occupantsAt.
     *
     * @param layout
     * @param square
     * @return the Point, or null when the running layout has nothing on that square
     */
    public Point speakerAt(Layout layout, TileKey square)
    {
        List<Point> occupied = occupantsAt(layout, square);

        if (!occupied.isEmpty()) return occupied.get(0);

        List<Point> all = pointsAt(layout, square);

        // A copy trains may stop at, in preference to one they may not: with arrivals barred the copies are not
        // interchangeable.
        for (Point point : all)
        {
            if (point.isDestination()) return point;
        }

        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Drops the paths that take a train nowhere, both artifacts of one square being several Points: a path ending on
     * a copy of its own start square, and a station reachable facing either way, offered once per copy.
     *
     * @param paths as the layout offers them
     * @return the ones worth showing, in the order given
     */
    public List<List<Edge>> distinctDestinations(List<List<Edge>> paths)
    {
        if (paths == null) return null;

        List<List<Edge>> out = new ArrayList<>();

        Set<String> seen = new LinkedHashSet<>();

        for (List<Edge> path : paths)
        {
            if (path == null || path.isEmpty()) continue;

            String from = path.get(0).getStart() == null ? null : path.get(0).getStart().getName();
            String to = path.get(path.size() - 1).getEnd() == null
                ? null : path.get(path.size() - 1).getEnd().getName();

            if (sameSquare(from, to)) continue;

            // Keyed by square where one exists, else by name: a Point this index has never heard of is still a
            // destination, and keying them all under "null" would drop all but the first.
            TileKey square = squareOf(to);

            if (!seen.add(square == null ? "?" + to : square.toString())) continue;

            out.add(path);
        }

        return out;
    }

    /** @return every square this setup emits a Point for */
    public Set<TileKey> squares()
    {
        return Collections.unmodifiableSet(pointsBySquare.keySet());
    }

    @Override
    public String toString()
    {
        return "StationIndex(" + pointsBySquare.size() + " squares, "
            + squareByPoint.size() + " points)";
    }
}
