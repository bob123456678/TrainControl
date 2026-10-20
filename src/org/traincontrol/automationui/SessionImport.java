package org.traincontrol.automationui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.AutonomySession.ImportFormat;
import org.traincontrol.automationui.AutonomySession.LegacyImport;
import org.traincontrol.automationui.AutonomySession.LocomotiveLeftOut;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.util.I18n;

/**
 * A session's imports: a legacy autonomy.json, an exported bundle, and telling the two apart (FR-136).
 *
 * The session keeps its fields, its public methods - each a one-line call to a method here - and the nested types
 * `LegacyImport`, `ImportFormat` and `LocomotiveLeftOut`.
 */
final class SessionImport
{
    private final AutonomySession owner;

    /**
     * @param owner the session whose setup the imports fill
     */
    SessionImport(AutonomySession owner)
    {
        this.owner = owner;
    }

    /**
     * The active configuration's run-wide settings object, created if it is not there yet.
     *
     * The sibling of `configurationExtras`, which does the same for one square. Both answer null when
     * no configuration is active, because there is then nowhere for the setting to live.
     *
     * @return the globals object, or null when nothing is active
     */
    private org.json.JSONObject configurationGlobals()
    {
        String active = owner.store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = owner.store.getConfiguration(active);

        if (configuration == null) return null;

        if (!configuration.has("globals")) configuration.put("globals", new org.json.JSONObject());

        return configuration.getJSONObject("globals");
    }

    /**
     * The active configuration's own data for one square, created if this is the first thing on it.
     *
     * A placement is not a decision about the track - it is where a train happens to be standing - so
     * it belongs to a configuration and not to the shared half.  Keyed by tile, the way the rest of
     * the configuration is, so a Point renamed later keeps whatever is standing on it.
     *
     * @param tile
     * @return null when no configuration is loaded to put anything in
     */
    private org.json.JSONObject configurationExtras(TileKey tile)
    {
        String active = owner.store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = owner.store.getConfiguration(active);

        if (configuration == null) return null;

        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        org.json.JSONObject points = configuration.getJSONObject("points");

        if (!points.has(tile.toString())) points.put(tile.toString(), new org.json.JSONObject());

        return points.getJSONObject(tile.toString());
    }

    /**
     * Which way each imported train faces: the way the file ran it, and a guess only where the file cannot say.
     *
     * READ FROM THE FILE FIRST: 2.8.1 sent a train only along the edges that start at its point, so where every
     * one leaves the square by one side, the train drives out that way.  AFTER THE LOOP: a legacy terminus or
     * reversing point is marked "must turn round" later in the same point's import, and a square trains must turn at has
     * only turning copies, so a facing is checked against the copies the build will make.
     *
     * THE GUESS, where the file cannot say - no edge, edges both ways, or an end this diagram does not draw: a way trains
     * may arrive in first, since the copy IS the direction; else the first copy.  Deterministic, and
     * counted so the log can say how many were guessed.  A facing the square already records stays, if the guess itself
     * could make it (OB-304).
     *
     * A facing the file states and no copy holds is not saved (Adam, OB-270: *"we shouldn't allow an impossible facing to
     * be saved"*): the train is stood the way the square allows, and named in `facingsNotHeld` rather than counted as a
     * guess, since it is known to be the other way round from how the train drives.
     *
     * WHERE THE FILE CANNOT SAY, A FACING THE SQUARE RECORDS STAYS: the last occupant's is a guess as good as
     * the first copy's.  Only one the guess itself could make, and counted with the guesses (OB-304).
     *
     * @param facingToFind the placed trains, by square and legacy point name
     * @param edgesLeadTo each legacy point's edges, by the square each ends on (null: not on this diagram)
     * @param pointSquares the squares the old graph had points on
     * @param result where the guesses are counted
     */
    private void findTheImportedFacings(Map<TileKey, String> facingToFind, Map<String, List<TileKey>> edgesLeadTo,
        Set<TileKey> pointSquares, LegacyImport result)
    {
        for (Map.Entry<TileKey, String> each : facingToFind.entrySet())
        {
            TileKey tile = each.getKey();

            java.util.List<Side> ways = new ArrayList<>(owner.facingsFor(tile).values());

            Side ran = sideTheEdgesLeaveBy(tile, edgesLeadTo.get(each.getValue()), pointSquares);

            if (ran != null && ways.contains(ran))
            {
                owner.setFacing(tile, ran);

                continue;
            }

            java.util.Set<Side> mayArrive = owner.homeFacingsFor(tile);

            // A FACING THE SQUARE RECORDS STAYS where the file cannot say - only one the guess below could make,
            // and counted with the guesses (OB-304).  Kept unasked, a facing trains may not arrive in would stand the
            // imported train where autonomy will not start it.  Where no copy is one trains may arrive at, the
            // guess takes the first copy, and the recorded one is as good.
            Side recorded = ran == null ? owner.getFacing(tile) : null;

            boolean keepIt = recorded != null && ways.contains(recorded)
                && (mayArrive.contains(recorded) || java.util.Collections.disjoint(ways, mayArrive));

            if (keepIt)
            {
                result.facingsInvented++;

                continue;
            }

            Side guess = null;

            for (Side way : ways)
            {
                if (mayArrive.contains(way))
                {
                    guess = way;

                    break;
                }
            }

            if (guess == null && !ways.isEmpty()) guess = ways.get(0);

            if (guess != null)
            {
                owner.setFacing(tile, guess);

                if (ran != null) result.facingsNotHeld.add(each.getValue());
                else result.facingsInvented++;
            }
        }
    }

    /**
     * Sets the track each of the old file's edges ran over running the way it ran (Adam, 2026-09-24: *"Carry the old
     * file's directions onto the diagram - yes, to the extent possible."*).
     *
     * A 2.8.1 graph is one-way edges: a train on a point went only along the edges starting there.  The diagram's
     * defaults - plain track both ways, a switch out of its toe only - can forbid the very way the old graph ran a
     * train.  So each edge is followed as its facing is (`sideTheEdgesLeaveBy`) and the track between is set as the
     * editor's one-way run sets it.
     *
     * To the extent possible:
     *  - track the file ran both ways is left both ways, and so is a square two edges run over opposite ways;
     *  - an edge whose end is next by more than one side, or none, or across a link, is skipped for what it cannot say;
     *  - and ONLY ONTO A DIAGRAM NOBODY HAS SET A DIRECTION ON.  A default is stored as nothing, so filling the gaps of a
     *    tuned diagram cannot tell track left running both ways on purpose from track nobody has looked at.  There the
     *    pieces are only counted, for the log.
     * The facings are found afterwards, over the copies these directions make.
     *
     * @param oldEdges each edge as the squares of its two points (null for one this diagram does not draw)
     * @param pointSquares the squares the old graph had points on
     * @param result where the directions set are recorded
     */
    private void carryTheOldDirections(List<TileKey[]> oldEdges, Set<TileKey> pointSquares, LegacyImport result)
    {
        if (owner.graph == null) return;

        Map<TileGraph.DirectionKey, Direction> wanted = new LinkedHashMap<>();

        for (TileKey[] edge : oldEdges)
        {
            TileKey from = edge[0];
            TileKey to = edge[1];

            if (from == null || to == null || from.equals(to)) continue;

            Side leaving = null;
            boolean both = false;

            for (Side side : Side.values())
            {
                if (!owner.graph.firstStopsLeaving(from, side, pointSquares).contains(to)) continue;

                if (leaving != null) both = true;

                leaving = side;
            }

            if (leaving == null || both) continue;

            List<TileKey> path = owner.graph.pathLeaving(from, leaving, to, pointSquares);

            if (path == null) continue;

            // As `applyOneWay` does, between the two ends only.
            for (int i = 1; i < path.size() - 1; i++)
            {
                TileKey tile = path.get(i);

                Side cameFrom = owner.graph.sideTowardNeighbour(tile, path.get(i - 1));
                Side goingTo = owner.graph.sideTowardNeighbour(tile, path.get(i + 1));

                if (cameFrom == null || goingTo == null) continue;

                for (Map.Entry<RouteId, Route> entry : owner.graph.getRoutes(tile).entrySet())
                {
                    Route route = entry.getValue();

                    if (!route.touches(cameFrom) || !route.touches(goingTo)) continue;

                    TileGraph.DirectionKey key = new TileGraph.DirectionKey(tile, entry.getKey());

                    Direction way = route.getA() == goingTo ? Direction.TOWARD_A : Direction.TOWARD_B;

                    Direction had = wanted.get(key);

                    wanted.put(key, had == null || had == way ? way : Direction.BOTH);
                }
            }
        }

        // A DIAGRAM WHOSE DIRECTIONS HAVE BEEN SET is left as it is, and what the file ran differently is counted.
        boolean tuned = owner.store.hasTileDirections();

        Map<TileKey, Map<RouteId, Direction>> bySquare = new LinkedHashMap<>();

        for (Map.Entry<TileGraph.DirectionKey, Direction> each : wanted.entrySet())
        {
            TileKey tile = each.getKey().square();
            RouteId id = each.getKey().getRouteId();

            // THE OPERATOR'S OWN IS KEPT: only a route the store holds no direction for is a gap.
            if (owner.store.getTileDirection(tile, id) != null) continue;

            if (each.getValue() == owner.graph.getDirection(tile, id)) continue;

            if (tuned)
            {
                result.directionsNotCarried++;

                continue;
            }

            bySquare.computeIfAbsent(tile, k -> new LinkedHashMap<RouteId, Direction>()).put(id, each.getValue());
        }

        // A SQUARE AT A TIME, BY ITS ARMS (OB-351): a switch's roads run as its arrows will show them
        for (Map.Entry<TileKey, Map<RouteId, Direction>> square : bySquare.entrySet())
        {
            TileKey tile = square.getKey();

            Map<RouteId, Route> routes = owner.graph.getRoutes(tile);

            for (Map.Entry<RouteId, Direction> answer : owner.graph.armAnswersFor(tile, square.getValue()).entrySet())
            {
                Route route = routes.get(answer.getKey());

                if (route == null || TileGraph.inForce(owner.graph.getDirection(tile, answer.getKey()), route) == answer.getValue())
                {
                    continue;
                }

                owner.record(tile, answer.getKey(), answer.getValue());

                result.directionsCarried.put(new TileGraph.DirectionKey(tile, answer.getKey()), answer.getValue());
            }
        }

        if (!result.directionsCarried.isEmpty()) owner.touched();
    }

    /**
     * The one side of a square that every edge from a legacy point leaves by, or null where they leave by more than
     * one, where there is none, or where one cannot be followed on this diagram.
     *
     * An old edge joins a point to the next ones along the track, so the side it leaves by is the side from which its
     * end is the next square the old graph had a point on - asked of each side, not of the shortest walk, which round
     * a loop can reach the end the other way.  Followed over the track as drawn, whichever way trains may run on it
     * now: the question is where the file sent the train, not whether the diagram still lets it.  An edge that ends on
     * the same square - a station and its approach guard share a sensor - says nothing about a side; one whose end is
     * next by both sides, or by neither, cannot say.
     *
     * @param tile the square the train stands on
     * @param ends the squares the point's edges end on, null for one this diagram does not draw
     * @param pointSquares the squares the old graph had points on
     * @return the side, or null
     */
    private Side sideTheEdgesLeaveBy(TileKey tile, List<TileKey> ends, Set<TileKey> pointSquares)
    {
        if (tile == null || ends == null || owner.graph == null) return null;

        Map<Side, Set<TileKey>> nextBy = new LinkedHashMap<>();

        for (Side side : Side.values()) nextBy.put(side, owner.graph.firstStopsLeaving(tile, side, pointSquares));

        Side only = null;

        for (TileKey end : ends)
        {
            if (end == null) return null;

            if (end.equals(tile)) continue;

            Side side = null;

            for (Map.Entry<Side, Set<TileKey>> by : nextBy.entrySet())
            {
                if (!by.getValue().contains(end)) continue;

                if (side != null) return null;

                side = by.getKey();
            }

            if (side == null || (only != null && only != side)) return null;

            only = side;
        }

        return only;
    }

    /**
     * The body of `AutonomySession.importLegacy(JSONObject, Set, boolean)`, whose javadoc says what it does.
     */
    LegacyImport importLegacy(org.json.JSONObject legacy, Set<String> knownLocomotives, boolean placeTrains)
    {
        LegacyImport result = new LegacyImport();

        // One locomotive stands in one place.  Tracked across the whole file rather than per point: two points naming the
        // same locomotive invalidate the layout, whichever pages they are on.
        Set<String> placedAlready = new LinkedHashSet<>();

        // And one locomotive has one HOME, for the same reason (OB-075).  `setHome` sweeps duplicates; this is the
        // second door, as the import writes "home" straight into the configuration.  A file written before the rule can
        // name two; the first one wins, which is the file's own order and so reproducible, and the rest are counted so
        // the choice can be reported rather than made silently.
        Set<String> homedAlready = new LinkedHashSet<>();

        // AND ACROSS WHAT THE CONFIGURATION ALREADY SAYS: where a train already stands, and which train already has
        // a home, are facts about the configuration, and gap-filling does not overrule them.  A second import could
        // otherwise put a train that has since moved on two squares, which refuses the whole configuration.
        Set<String> standingAlready = new LinkedHashSet<>();

        // And the homes it already has, told apart from a home the file names twice so the message can say which
        Set<String> homedInConfiguration = new LinkedHashSet<>();

        String into = owner.store.getActiveConfiguration();

        org.json.JSONObject intoConfiguration = into == null ? null : owner.store.getConfiguration(into);

        org.json.JSONObject intoPoints = intoConfiguration == null ? null : intoConfiguration.optJSONObject("points");

        if (intoPoints != null)
        {
            for (String square : intoPoints.keySet())
            {
                org.json.JSONObject extras = intoPoints.optJSONObject(square);

                if (extras == null) continue;

                org.json.JSONObject stands = extras.optJSONObject(AutonomyBuilder.LOCOMOTIVE);

                if (stands != null && !stands.optString("name", "").trim().isEmpty())
                {
                    standingAlready.add(stands.optString("name", "").trim());
                }

                if (!extras.optString("home", "").trim().isEmpty()) homedInConfiguration.add(extras.optString("home", "").trim());
            }
        }

        org.json.JSONArray points = legacy.optJSONArray("points");

        if (points == null || owner.reducer == null) return result;

        Map<Integer, TileKey> bySensor = new LinkedHashMap<>();

        // Sensors carried by more than one square, which no amount of reading the file can resolve: two squares on the same
        // s88 is ordinary (a station and its approach guard), and the old graph names ONE point per sensor, so putting it
        // on whichever came last would land a station on the wrong page silently.  Refused and reported; excluding the
        // duplicating pages first makes the rest of the import unambiguous.
        Set<Integer> ambiguous = new LinkedHashSet<>();

        for (GraphReducer.ReducedPoint point : owner.reducer.getPoints().values())
        {
            if (point.getS88() <= 0) continue;

            if (bySensor.containsKey(point.getS88())) ambiguous.add(point.getS88());

            bySensor.put(point.getS88(), point.getTile());
        }

        for (Integer sensor : ambiguous) bySensor.remove(sensor);

        // WHERE EACH POINT'S EDGES LEAD.  A 2.8.1 graph states direction as one-way edges - a train on a
        // point goes only along the edges that start there - so they say which way a train standing there drives.
        // The squares they end on, by the same sensor match as the points; null for an end this diagram does not draw.
        Map<String, TileKey> squareOfPoint = new java.util.HashMap<>();

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null) continue;

            int sensor = point.optInt("s88", 0);

            squareOfPoint.put(point.optString("name", ""), sensor > 0 ? bySensor.get(sensor) : null);
        }

        Map<String, List<TileKey>> edgesLeadTo = new java.util.HashMap<>();

        // And each edge as the two squares it joins, for the directions it ran (Adam, 2026-09-24).
        List<TileKey[]> oldEdges = new ArrayList<>();

        org.json.JSONArray edges = legacy.optJSONArray("edges");

        for (int i = 0; edges != null && i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.optJSONObject(i);

            if (edge == null) continue;

            edgesLeadTo.computeIfAbsent(edge.optString("start", ""), start -> new ArrayList<>())
                .add(squareOfPoint.get(edge.optString("end", "")));

            oldEdges.add(new TileKey[] {squareOfPoint.get(edge.optString("start", "")),
                squareOfPoint.get(edge.optString("end", ""))});
        }

        // Where the walks from a square stop: the squares the old graph had points on.
        Set<TileKey> pointSquares = new LinkedHashSet<>(squareOfPoint.values());

        pointSquares.remove(null);

        // The trains this import placed and whose facing it has still to find, by square and point name.  Decided
        // after the loop, over the finished setup.
        Map<TileKey, String> facingToFind = new LinkedHashMap<>();

        // The squares this import has met - named here, or found named and counted once in `skipped` - so a further old point
        // on one is said rather than counted again
        Set<TileKey> metHere = new java.util.HashSet<>();

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null) continue;

            String name = point.optString("name", "");

            if (name.trim().isEmpty()) continue;

            int sensor = point.optInt("s88", 0);

            TileKey tile = sensor > 0 ? bySensor.get(sensor) : null;

            // A point with no sensor, or one watching a sensor this diagram does not draw.  Reported
            // rather than dropped: it is how somebody finds out that a page is missing or excluded.
            if (tile == null)
            {
                result.unmatched.add(name);
                continue;
            }

            // Before the name, and regardless of it.  A placement is about the SQUARE, so a square
            // somebody has already named should still get its locomotive back.
            org.json.JSONObject standing = point.optJSONObject("loc");

            String home = point.optString("home", "");

            boolean anySetting = false;

            for (String key : AutonomySession.CARRIED_SETTINGS)
            {
                if (point.has(key)) anySetting = true;
            }

            if (standing != null || !home.trim().isEmpty() || anySetting)
            {
                org.json.JSONObject extras = configurationExtras(tile);

                if (extras != null)
                {
                    if (standing != null && !extras.has(AutonomyBuilder.LOCOMOTIVE))
                    {
                        String locName = standing.optString("name", "").trim();

                        // Checked here, not left to the load: the model answers an unknown locomotive by invalidating the
                        // whole layout, which would leave a setup that refuses to open with nothing saying the import
                        // put it there.
                        if (locName.isEmpty())
                        {
                            // a placement with no name: nothing to place, nothing worth reporting
                        }
                        else if (knownLocomotives != null && !knownLocomotives.contains(locName))
                        {
                            result.unknownLocomotives.add(locName);

                            result.unknownPlacements.add(
                                new LocomotiveLeftOut(locName, LocomotiveLeftOut.What.STANDING, tile));
                        }
                        else if (standingAlready.contains(locName))
                        {
                            if (!result.alreadyPlaced.contains(locName)) result.alreadyPlaced.add(locName);
                        }
                        else if (!placeTrains)
                        {
                            if (!result.notPlacedInUse.contains(locName)) result.notPlacedInUse.add(locName);
                        }
                        else if (!placedAlready.add(locName))
                        {
                            result.duplicateLocomotives.add(locName);
                        }
                        else
                        {
                            // Copied whole: the builder reads back exactly this shape (speed, functions, train length).
                            extras.put(AutonomyBuilder.LOCOMOTIVE,
                                new org.json.JSONObject(standing.toString()));

                            // And which way it is pointing - found after the loop, over the finished setup: see
                            // `findTheImportedFacings`.  ASKED OF THE FILE WHATEVER THE SQUARE RECORDS: a facing
                            // on an empty square is its last occupant's, no evidence about the train the file puts there.
                            facingToFind.put(tile, name);

                            result.placed++;
                        }
                    }

                    // A SQUARE ANOTHER TRAIN STANDS ON keeps it, and the file's train is named (OB-304): one square holds one
                    // train.  The same train already there - a file imported a second time - is nothing to say.
                    if (standing != null && extras.has(AutonomyBuilder.LOCOMOTIVE))
                    {
                        String locName = standing.optString("name", "").trim();

                        org.json.JSONObject there = extras.optJSONObject(AutonomyBuilder.LOCOMOTIVE);

                        String standsThere = there == null ? "" : there.optString("name", "").trim();

                        if (!locName.isEmpty() && !locName.equals(standsThere) && !result.squareTaken.contains(locName))
                        {
                            result.squareTaken.add(locName);
                        }
                    }

                    if (!home.trim().isEmpty() && !extras.has("home"))
                    {
                        if (homedInConfiguration.contains(home.trim()))
                        {
                            if (!result.homesKept.contains(home.trim())) result.homesKept.add(home.trim());
                        }
                        else if (homedAlready.add(home.trim()))
                        {
                            extras.put("home", home.trim());
                        }
                        else
                        {
                            result.duplicateHomes++;
                        }
                    }

                    // A SQUARE ALREADY HOME TO ANOTHER TRAIN keeps it, and the file's is named (OB-304).
                    if (!home.trim().isEmpty() && !extras.optString("home", "").trim().isEmpty()
                        && !home.trim().equals(extras.optString("home", "").trim())
                        && !result.homeSquareTaken.contains(home.trim()))
                    {
                        result.homeSquareTaken.add(home.trim());
                    }

                    for (String key : AutonomySession.CARRIED_SETTINGS)
                    {
                        // Gap-filled like everything else here, so re-running cannot undo an edit
                        // somebody made after the first import.
                        if (!point.has(key) || extras.has(key)) continue;

                        Object value = point.get(key);

                        // A ZERO CAPACITY IS NOT A CAPACITY.  This loop is first-present-wins and a legacy file puts several
                        // points on one sensor (a station and its approach guard), many with an explicit
                        // `maxTrainLength: 0`, so a guard's zero would erase the station's real limit.  Zero already means
                        // "no limit", so skipping it lets the real value through whatever the file's order.
                        if ("maxTrainLength".equals(key) && value instanceof Number
                            && ((Number) value).intValue() <= 0)
                        {
                            continue;
                        }

                        // A SQUARE THAT ALLOWS ONLY THE CHOSEN LOCOMOTIVES KEEPS ITS LIST: an old file's list names
                        // the locomotives kept OUT, so filling an allow-only square from it would let in exactly those.
                        if ("excludedLocs".equals(key)
                            && org.traincontrol.automation.Point.ALLOW_ONLY_LISTED.equals(
                                extras.optString(org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE, null)))
                        {
                            continue;
                        }

                        // A SWITCHED-OFF STATION ARRIVES ON, AND NOT ONE AUTONOMY CHOOSES: carried as `active: false`
                        // it would be a square nothing may pass through or end on, which v2.8.1 did not mean.  Gap-filled.
                        if ("active".equals(key) && Boolean.FALSE.equals(value) && point.optBoolean("station", false))
                        {
                            if (!extras.has(AutonomyBuilder.AUTO_DESTINATION))
                            {
                                extras.put(AutonomyBuilder.AUTO_DESTINATION, Boolean.FALSE);

                                result.settings++;
                            }

                            continue;
                        }

                        // Copied rather than shared: a JSONArray handed over would be the caller's parsed object, and a later
                        // edit here would edit their copy.
                        if (value instanceof org.json.JSONArray)
                        {
                            value = new org.json.JSONArray(value.toString());
                        }
                        else if (value instanceof org.json.JSONObject)
                        {
                            value = new org.json.JSONObject(value.toString());
                        }

                        extras.put(key, value);

                        result.settings++;
                    }
                }
            }

            // Terminus and reversing are DERIVED now, so neither can be written down.  What the old graph was recording is
            // that every train arriving here turns round, which is authored as mustReverse; whether the build then emits
            // terminus or reversing follows from the square being a station, imported above.
            //
            //   reversing, station  the old "reversing station" said two things at once: it reverses, AND autonomy never
            //                       chooses it.  Those are separate now - a terminus and a berth - so it takes the
            //                       parking flag as well, or a shunting neck would become a destination trains are sent to.
            boolean turnsTrains = point.optBoolean("terminus", false)
                || point.optBoolean("reversing", false);

            // Left alone if the square already says something about reversing: this fills gaps.
            boolean alreadyMarked = owner.getPointProperty(tile, AutonomyBuilder.MUST_REVERSE) != null
                || owner.getPointProperty(tile, AutonomyBuilder.CAN_REVERSE) != null;

            if (turnsTrains && !alreadyMarked)
            {
                owner.setPointFlag(tile, AutonomyBuilder.CAN_REVERSE, false);
                owner.setPointProperty(tile, AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);

                if (point.optBoolean("reversing", false) && point.optBoolean("station", false))
                {
                    owner.setPointFlag(tile, AutonomyBuilder.PARKING, true);
                }

                result.reversing++;
            }

            String existing = owner.store.getPointName(tile);

            if (existing != null && !existing.trim().isEmpty())
            {
                // A SQUARE ALREADY MET, from another old point on the same sensor: named by this import a point
                // ago, or found named before it and counted then - this point's name is said rather than dropped, and the
                // square is not counted again.  A square named before the import counts once in {4}, however many old
                // points stand on it: names are shared by every configuration, so a second import, or an old
                // file brought into a named railway, meets every square this way.
                if (!metHere.add(tile))
                {
                    result.namesNotKept.add(I18n.f("autosetup.ui.importSharedSquareLine", name, existing));
                }
                else
                {
                    result.skipped++;
                }

                continue;
            }

            owner.store.setPointName(tile, name);

            metHere.add(tile);

            // Labelled on the station square itself, where the station has no caption on its page already (OB-341): every
            // station has to be shown on the diagram, and its own square is the one place that always exists, is on the
            // page the reader is looking at, and cannot land on somebody else's track.
            if (point.optBoolean("station", false) && !owner.isCaptionedOnPage(tile, tile.getPage()))
            {
                owner.store.setCaption(tile, tile);
            }

            if (point.optBoolean("station", false)) owner.store.setStation(tile, true);

            // maxTrainLength is CARRIED, not converted: a station's capacity is not a track's length, and a
            // legacy file holds both (capacity on the points, length on the edges).  It goes through CARRIED_SETTINGS.

            result.matched++;
        }

        // THE OLD FILE'S DIRECTIONS FIRST, so the facings below are read over the copies they make.
        carryTheOldDirections(oldEdges, pointSquares, result);

        findTheImportedFacings(facingToFind, edgesLeadTo, pointSquares, result);

        // AND THE SETTINGS ABOVE THE POINTS: a 2.7.4c autonomy.json is one object, the points and edges and above
        // them the whole settings panel (pace, default speed, how many trains may run, whether routes are fired) and the
        // timetable.  Everything that is not the setup itself comes across, as `captureFromLayout` does, gap-filled so
        // running it twice cannot undo a setting somebody changed.
        //
        // NOT the edges' lengths: a legacy file records length per EDGE and the diagram per SQUARE, and which square a
        // length belongs to when an edge spans several cannot be answered without guessing.
        org.json.JSONObject settings = configurationGlobals();

        if (settings != null)
        {
            for (String key : legacy.keySet())
            {
                if ("points".equals(key) || "edges".equals(key)) continue;

                // NOT THE ROUTE ACTIVATIONS, and this is the important exclusion: parseAuto ends in
                // applyAutonomyRouteActivations, which walks the LIVE Central Station route database, disabling every route
                // whose id is not in activateRouteIDs and executing the rest - accessories thrown on the real railway.  A
                // legacy file can carry `activateRoutes: true` with an EMPTY id list, so carrying these across would
                // disable every route, again on every parseAuto.  They are REPORTED instead: see
                // `whatALegacyImportLeaves` and `autosetup.ui.leftRouteActivations`.
                if ("activateRoutes".equals(key) || "activateRouteIDs".equals(key)) continue;

                // NOT THE TIMETABLE EITHER, for the reason the edge lengths are left out: translating it is a decision.  Its
                // entries name points with no s88 or on sensors shared by several legacy points, and TimetablePath.fromJSON
                // drops an entry on the first edge it cannot resolve, so what would arrive is a heap of log lines and a
                // nearly empty timetable, which the next captureFromLayout writes back over these globals for good.  Left
                // in autonomy.json it loses less.
                if ("timetable".equals(key)) continue;

                if (settings.has(key)) continue;

                Object value = legacy.get(key);

                // Copied rather than shared, for the reason the point extras above are: a JSONArray
                // handed straight over stays the caller's, and the timetable is an array.
                if (value instanceof org.json.JSONArray)
                {
                    value = new org.json.JSONArray(value.toString());
                }
                else if (value instanceof org.json.JSONObject)
                {
                    value = new org.json.JSONObject(value.toString());
                }

                settings.put(key, value);

                result.settings++;
            }

            owner.dirty = true;
        }

        // Derived again, once, now that the authored data has changed: the reduction is what the diagram draws from, and
        // it is built from a snapshot of the authored data taken when the session opened.  After the loop rather than
        // inside it, because the tile-by-sensor map above comes from the reduction.
        owner.rebuild();

        return result;
    }

    /**
     * The body of `AutonomySession.importBundle`, whose javadoc says what it does.
     */
    int importBundle(String name, org.json.JSONObject file) throws java.io.IOException
    {
        int filled = owner.store.importBundle(name, file);

        owner.rebuild();

        return filled;
    }

    /**
     * The body of `AutonomySession.detectImportFormat`, whose javadoc says what it does.
     */
    static ImportFormat detectImportFormat(org.json.JSONObject file)
    {
        if (file == null) return ImportFormat.UNKNOWN;

        if (file.optJSONObject(AutonomyCompanionStore.EXPORT_CONFIGURATION) != null)
        {
            return ImportFormat.BUNDLE;
        }

        // A list of Points, which is the old graph and nothing else
        if (file.optJSONArray("points") != null) return ImportFormat.LEGACY_GRAPH;

        // Keyed by square, which is a configuration
        if (file.optJSONObject("points") != null) return ImportFormat.CONFIGURATION;

        // A configuration whose points have all been removed is still a configuration, and its globals
        // are the only thing left to say so.
        if (file.optJSONObject("globals") != null) return ImportFormat.CONFIGURATION;

        // Deliberately NOT recognised: a configuration created and never used, which carries nothing
        // but its own name.  A name is not evidence - half the JSON in the world has one - and there
        // would be nothing in such a file to import anyway.

        return ImportFormat.UNKNOWN;
    }
}
