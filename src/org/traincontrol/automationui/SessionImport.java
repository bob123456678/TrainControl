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

    /** The session whose setup the imports fill. */
    SessionImport(AutonomySession owner)
    {
        this.owner = owner;
    }

    /** The active configuration's run-wide settings object, created on demand; null when none is active. */
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
     * The active configuration's data for one square, created on demand; null when none is active.  A placement belongs
     * to a configuration, not the shared half, and is keyed by tile so a Point renamed later keeps what stands on it.
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
     * Gives each imported train the facing the file ran it, and a guess only where the file cannot say.
     *
     * The file's edges decide first (2.8.1 sent a train only along the edges starting at its point); a facing the file
     * states that no copy holds is not saved (OB-270) and is named in `facingsNotHeld`.  Otherwise the guess is the
     * first way trains may arrive in, since the copy IS the direction, else the first copy; guesses are counted for the
     * log.
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

            // Where the file cannot say, a facing the square records stays if the guess below could make it (OB-304):
            // kept unasked, a facing trains may not arrive in would stand the train where autonomy will not start it.
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
     * Sets the track between the old file's edges to run the way the file ran it, where it can.  A 2.8.1 graph is
     * one-way edges, so each edge is followed as its facing is (`sideTheEdgesLeaveBy`).  Track run both ways, and an
     * edge whose end is next by more than one side, none, or across a link, is skipped.  Only a diagram nobody has set
     * a direction on is filled: a default is stored as nothing, so tuned track cannot be told from untouched, and there
     * the pieces are only counted.
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

        // A tuned diagram is left as it is; what the file ran differently is counted.
        boolean tuned = owner.store.hasTileDirections();

        Map<TileKey, Map<RouteId, Direction>> bySquare = new LinkedHashMap<>();

        for (Map.Entry<TileGraph.DirectionKey, Direction> each : wanted.entrySet())
        {
            TileKey tile = each.getKey().square();
            RouteId id = each.getKey().getRouteId();

            // Only a route the store holds no direction for is a gap.
            if (owner.store.getTileDirection(tile, id) != null) continue;

            if (each.getValue() == owner.graph.getDirection(tile, id)) continue;

            if (tuned)
            {
                result.directionsNotCarried++;

                continue;
            }

            bySquare.computeIfAbsent(tile, k -> new LinkedHashMap<RouteId, Direction>()).put(id, each.getValue());
        }

        // A square at a time, by its arms, so a switch's roads run as its arrows will show them (OB-351).
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
     * The one side of a square that every edge from a legacy point leaves by; null when they leave by more than one, by
     * none, or when one cannot be followed on this diagram.  Asked of each side, not of the shortest walk (which round
     * a loop can reach the end the other way), over the track as drawn.  An edge ending on the same square (a station
     * and its approach guard share a sensor) says nothing.
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

    /** The body of `AutonomySession.importLegacy(JSONObject, Set, boolean)`. */
    LegacyImport importLegacy(org.json.JSONObject legacy, Set<String> knownLocomotives, boolean placeTrains)
    {
        LegacyImport result = new LegacyImport();

        // One locomotive stands in one place, whichever pages name it: two points naming one invalidate the layout.
        Set<String> placedAlready = new LinkedHashSet<>();

        // And has one HOME (OB-075).  `setHome` sweeps duplicates, but the import writes "home" straight into the
        // configuration: the first one named wins (the file's order) and the rest are counted.
        Set<String> homedAlready = new LinkedHashSet<>();

        // And across what the configuration already says: a second import must not put a train that has since moved on
        // two squares.
        Set<String> standingAlready = new LinkedHashSet<>();

        // The homes it already has, told apart from a home the file names twice.
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

        // Sensors on more than one square (a station and its approach guard) cannot be resolved: the old graph names
        // ONE point per sensor, so picking one could land a station on the wrong page silently.  Refused and reported.
        Set<Integer> ambiguous = new LinkedHashSet<>();

        for (GraphReducer.ReducedPoint point : owner.reducer.getPoints().values())
        {
            if (point.getS88() <= 0) continue;

            if (bySensor.containsKey(point.getS88())) ambiguous.add(point.getS88());

            bySensor.put(point.getS88(), point.getTile());
        }

        for (Integer sensor : ambiguous) bySensor.remove(sensor);

        // Where each point's edges lead: a 2.8.1 graph states direction as one-way edges, which say which way a train
        // standing there drives.  null for an end this diagram does not draw.
        Map<String, TileKey> squareOfPoint = new java.util.HashMap<>();

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null) continue;

            int sensor = point.optInt("s88", 0);

            squareOfPoint.put(point.optString("name", ""), sensor > 0 ? bySensor.get(sensor) : null);
        }

        Map<String, List<TileKey>> edgesLeadTo = new java.util.HashMap<>();

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

        // Where the walks stop: the squares the old graph had points on.
        Set<TileKey> pointSquares = new LinkedHashSet<>(squareOfPoint.values());

        pointSquares.remove(null);

        // Trains this import placed, whose facing is found after the loop over the finished setup.
        Map<TileKey, String> facingToFind = new LinkedHashMap<>();

        // Squares this import has met, so a further old point on one is said rather than counted again.
        Set<TileKey> metHere = new java.util.HashSet<>();

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null) continue;

            String name = point.optString("name", "");

            if (name.trim().isEmpty()) continue;

            int sensor = point.optInt("s88", 0);

            TileKey tile = sensor > 0 ? bySensor.get(sensor) : null;

            // Reported rather than dropped, so somebody can find out a page is missing or excluded.
            if (tile == null)
            {
                result.unmatched.add(name);
                continue;
            }

            // Before the name: a placement is about the SQUARE, so an already named square still gets its locomotive
            // back.
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

                        // Checked here: the model answers an unknown locomotive by invalidating the whole layout.
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

                            // Facing is found after the loop, from the file whatever the square records: a facing on an
                            // empty square is its last occupant's (`findTheImportedFacings`).
                            facingToFind.put(tile, name);

                            result.placed++;
                        }
                    }

                    // A square another train stands on keeps it and the file's train is named (OB-304); the same train
                    // again says nothing.
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
                        // Gap-filled, so re-running cannot undo a later edit.
                        if (!point.has(key) || extras.has(key)) continue;

                        Object value = point.get(key);

                        // A zero capacity is not a capacity: the loop is first-present-wins and a guard on a station's
                        // sensor often says `maxTrainLength: 0` (no limit), which would erase the station's real limit.
                        if ("maxTrainLength".equals(key) && value instanceof Number
                            && ((Number) value).intValue() <= 0)
                        {
                            continue;
                        }

                        // An allow-only square keeps its list: an old file's list names the locomotives kept OUT.
                        if ("excludedLocs".equals(key)
                            && org.traincontrol.automation.Point.ALLOW_ONLY_LISTED.equals(
                                extras.optString(org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE, null)))
                        {
                            continue;
                        }

                        // A switched-off station arrives on and is not one autonomy chooses: carried as `active: false`
                        // it would block the square entirely, which v2.8.1 did not mean.
                        if ("active".equals(key) && Boolean.FALSE.equals(value) && point.optBoolean("station", false))
                        {
                            if (!extras.has(AutonomyBuilder.AUTO_DESTINATION))
                            {
                                extras.put(AutonomyBuilder.AUTO_DESTINATION, Boolean.FALSE);

                                result.settings++;
                            }

                            continue;
                        }

                        // Copied, not shared: a handed-over array would be the caller's parsed object.
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

            // Terminus and reversing are derived, so neither can be written down: what the old graph recorded is that
            // every train arriving here turns round (mustReverse).  An old "reversing station" also meant autonomy
            // never chooses it, so it takes the parking flag as well, or a shunting neck would become a destination.
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
                // A square already met, from another old point on the same sensor or named before the import: the name
                // is said, not dropped, and the square is counted once however many old points stand on it.
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

            // Labeled on the station's own square when its page has no caption yet (OB-341): every station must show.
            if (point.optBoolean("station", false) && !owner.isCaptionedOnPage(tile, tile.getPage()))
            {
                owner.store.setCaption(tile, tile);
            }

            if (point.optBoolean("station", false)) owner.store.setStation(tile, true);

            // maxTrainLength goes through CARRIED_SETTINGS, not converted: a station's capacity is not a track's
            // length.

            result.matched++;
        }

        // The old directions first, so the facings below are read over the copies they make.
        carryTheOldDirections(oldEdges, pointSquares, result);

        findTheImportedFacings(facingToFind, edgesLeadTo, pointSquares, result);

        // And the settings above the points (pace, default speed, trains allowed, timetable): all that is not the setup
        // comes across gap-filled, as `captureFromLayout` does.  Not the edges' lengths: the file records length per
        // EDGE and the diagram per SQUARE, which cannot be resolved without guessing.
        org.json.JSONObject settings = configurationGlobals();

        if (settings != null)
        {
            for (String key : legacy.keySet())
            {
                if ("points".equals(key) || "edges".equals(key)) continue;

                // NOT THE ROUTE ACTIVATIONS: parseAuto ends in applyAutonomyRouteActivations, which walks the LIVE
                // Central Station route database, disabling every route not in activateRouteIDs and executing the rest,
                // and a legacy file can carry `activateRoutes: true` with an empty id list.  Reported instead
                // (`whatALegacyImportLeaves`).
                if ("activateRoutes".equals(key) || "activateRouteIDs".equals(key)) continue;

                // Nor the timetable: translating it is a decision.  TimetablePath.fromJSON drops an entry on the first
                // edge it cannot resolve, so a nearly empty one would arrive and be written back over these globals for
                // good.
                if ("timetable".equals(key)) continue;

                if (settings.has(key)) continue;

                Object value = legacy.get(key);

                // Copied, not shared, as above.
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

        // Derived again once the authored data has changed; after the loop because the tile-by-sensor map comes from
        // the reduction.
        owner.rebuild();

        return result;
    }

    /** The body of `AutonomySession.importBundle`. */
    int importBundle(String name, org.json.JSONObject file) throws java.io.IOException
    {
        int filled = owner.store.importBundle(name, file);

        owner.rebuild();

        return filled;
    }

    /** The body of `AutonomySession.detectImportFormat`. */
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

        // A configuration with all its points removed is still one; its globals are all that say so.
        if (file.optJSONObject("globals") != null) return ImportFormat.CONFIGURATION;

        // Deliberately NOT recognized: a configuration with nothing but a name, which is not evidence.

        return ImportFormat.UNKNOWN;
    }
}
