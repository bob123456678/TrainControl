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
 * The translation between the squares a user sees and the Points a railway runs.
 *
 * One square of the track diagram becomes several Points - one per side a train can arrive by - and
 * nothing in the running model remembers that they are the same platform.  Every part of the interface
 * that shows a station therefore has to translate: a caption has a square and needs the Points standing
 * on it, a path has a Point and needs the name to print, a menu has a square and needs to know which
 * copies a train could leave from.
 *
 * That translation used to be done wherever it was needed, by building a fresh AutonomyBuilder and
 * recomputing the whole naming - twice per call in places, once per Point on every feedback event.  It
 * was slow, and worse, the copies disagreed: the same question asked in two places built the builder
 * with different settings, so one of them split the squares and the other did not, and the two answers
 * were both confidently wrong in different ways.
 *
 * So the naming is derived ONCE, from one configured builder, and every map here comes out of that same
 * derivation.  They cannot disagree, because there is nothing left for them to disagree about.
 *
 * Immutable, and cheap to hold.  The session rebuilds it when the setup changes, which is the only
 * moment any of it can become untrue.
 *
 * @author Adam
 */
public final class StationIndex
{
    /**
     * What to use before anything has been derived.  Answers "I have never heard of it" to everything,
     * rather than being a null every caller has to test for.
     */
    public static final StationIndex EMPTY = new StationIndex(null);

    private final Map<TileKey, String> nameBySquare;
    private final Map<String, TileKey> squareByPoint;
    private final Map<String, String> baseByPoint;
    private final Map<TileKey, List<String>> pointsBySquare;
    private final Map<String, Side> facingByPoint;
    private final Map<TileKey, List<Side>> arrivalsBySquare;

    /**
     * @param builder a builder configured exactly as the one that generated the running configuration,
     *        or null for the empty index.  The configuration matters: whether a square splits at all
     *        depends on the reversible and mandatory-turn sets, so a builder without them names a
     *        railway that is not the one running.
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

        // In emission order, which is the order the copies were named in - so "the first copy" means
        // the same thing to everything that asks, rather than depending on which map it asked.
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

        // The sides a train can arrive at each square by, from the same derivation as everything else.
        // Worked out per call it was a whole builder pass - the very thing this class exists to stop -
        // and it is asked once per station per repaint.
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
     * The sides a train can arrive at a square by.
     *
     * The same answer the split itself uses, so nothing can offer a restriction on a side the build has
     * no copy for - which would be a setting that silently did nothing.
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
     * @return the square it stands on, or null
     */
    public TileKey squareOf(Point point)
    {
        return point == null ? null : squareOf(point.getName());
    }

    /**
     * What to call a running Point on screen.
     *
     * The base name, so a station reads as one place however many copies of it the railway is made of.
     * Falls back to the Point's own name rather than to nothing: a Point this index has never heard of
     * is a configuration built before the last change, and its name is still better than a blank.
     *
     * @param point
     * @return a name fit to show, never null
     */
    public String describe(Point point)
    {
        if (point == null) return "";

        String base = baseByPoint.get(point.getName());

        if (base != null && !base.trim().isEmpty()) return base;

        return withoutArrivalSuffix(point.getName());
    }

    /**
     * A copy's name with the arrival side taken off, for when the index cannot answer.
     *
     * The index maps every emitted Point back to the name of its square, and that is the answer
     * whenever it has one. It does not always: a Point whose configuration was built by a different
     * run of the builder - a setup reloaded, a layout edited underneath a running graph - is not in
     * the map, and the fallback was to show the internal name. "BottomMainA (northbound)" is not a
     * place on anybody's railway. It is how the model spells one platform's several arrival sides,
     * and a user who did not create an eastbound one and a westbound one should not meet them.
     *
     * Only the suffixes the builder itself emits are removed, and only from the end. A station somebody
     * has genuinely called "Yard (upper)" keeps its name, because "upper" is not a heading.
     *
     * @param name an emitted Point name
     * @return the square's name, as far as it can be recovered
     */
    private static String withoutArrivalSuffix(String name)
    {
        if (name == null) return "";

        if (!name.endsWith(")")) return name;

        int open = name.lastIndexOf(" (");

        if (open <= 0) return name;

        String inside = name.substring(open + 2, name.length() - 1);

        // "northbound", or "northbound, reverse" - see AutonomyBuilder.nodeName
        int comma = inside.indexOf(',');

        String heading = comma < 0 ? inside : inside.substring(0, comma);

        for (String known : new String[]{"northbound", "southbound", "eastbound", "westbound"})
        {
            if (known.equals(heading)) return name.substring(0, open);
        }

        return name;
    }

    /**
     * @param pointName
     * @return the base name behind a copy, or the name itself when it is not a copy
     */
    public String baseNameOf(String pointName)
    {
        String base = pointName == null ? null : baseByPoint.get(pointName);

        return base == null ? pointName : base;
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
     * Whether two Points are the same piece of track.
     *
     * By square rather than by name, which is the whole reason this class exists: the copies are named
     * apart on purpose, so string comparison says "different place" about one platform.
     *
     * @param a a running Point name
     * @param b another
     * @return true when both stand on one square
     */
    public boolean sameSquare(String a, String b)
    {
        if (a == null || b == null) return false;

        // Identical names are the same place even when this index has never heard of either.  A
        // configuration built before the last diagram change still has Points, and they are still
        // somewhere; without this, a path from P back to P is offered as a journey to where the train
        // is already standing, which is the thing the caller is trying to drop.
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

        return out;
    }

    /**
     * The Points of a square that are actually holding a locomotive.
     *
     * More than one is entirely possible and was the bug that prompted this: two trains can be sent to
     * one platform from opposite ends, each arriving on the copy that faces its own way, and a caption
     * that asked for "the" occupant showed whichever it happened to find first.
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
     * The same list with each locomotive appearing once.
     *
     * A square is several Points, and a locomotive can legitimately be on more than one of them at
     * once: locking a path RESERVES every point along it for that train, deliberately without taking
     * it off anywhere else, because that is how a junction is held against a second train. Where a
     * path runs through two copies of one square, the train is on both.
     *
     * That is right for the model and wrong for a caption. It put one train on the platform twice -
     * "[BR &lt; |BR &gt;]" - each entry carrying the arrow of its own copy, so the same locomotive appeared
     * to be facing both ways at once. Two trains on one platform is a real thing this has to show; one
     * train shown as two is never anything but a mistake.
     *
     * The first copy is the one kept. For a train standing still there is only one; for a train part
     * way along a locked path the rest are the ones it is about to be on, and any of them says the
     * same thing about whether that platform is spoken for.
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

            // By identity, because that is how a locomotive is compared everywhere else here - two
            // locomotives can share a name in this application, and they are not the same train
            if (seen.add(point.getCurrentLocomotive())) out.add(point);
        }

        return out;
    }

    /**
     * The one Point that speaks for a square when only one answer is wanted.
     *
     * An occupied copy in preference to an empty one, because a question asked of a platform - what is
     * standing here, where can it go - is nearly always about the train.  With none occupied the first
     * is used, which is a choice and not an equivalence: the copies differ in whether a train may stop
     * on them once arrivals are restricted.  For "what is standing here" that does not matter; anything
     * asking what may be DONE here has to look at the copy it means.
     *
     * With more than one train standing on a square this answers for the first of them.  Callers that
     * can show more than one - the caption does - should ask occupantsAt instead.
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

        // A copy trains may stop at, in preference to one they may not.
        //
        // The copies stopped being interchangeable when arrivals could be barred, and the order they
        // come in is the order the sides happen to sort in - so with one side barred, whether the
        // answer was the open copy or the shut one depended on WHICH side had been barred.  Callers
        // that ask what may be done at a platform got the shut copy half the time, and answered
        // "nothing" about a station that works perfectly from the other end.
        for (Point point : all)
        {
            if (point.isDestination()) return point;
        }

        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Drops the paths that do not actually take a train anywhere.
     *
     * Two ways that happens, and both are artefacts of one square being several Points.  A path that
     * ends on a copy of the square it started from is a train being offered a journey to where it is
     * already standing.  And a station reachable facing either way is offered once per copy, which
     * since the copies stopped being named apart on screen reads as the same destination listed twice.
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

            // Keyed by the SQUARE where one exists, and by the name where it does not - a Point this
            // index has never heard of is still a destination, and keying every one of them under
            // "null" would show the first and silently drop the rest.
            TileKey square = squareOf(to);

            if (!seen.add(square == null ? "?" + to : square.toString())) continue;

            out.add(path);
        }

        return out;
    }

    /**
     * @return every square this setup emits a Point for
     */
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
