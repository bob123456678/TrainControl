package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.traincontrol.automationui.TileAnnotation;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import support.SourceOf;

/**
 * A locomotive stands in one place, a home and an arrival side keep the way its train faces, and a station demoted
 * keeps its settings.  One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder every claim
 * starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testATrainIsPlacedOnceAndKeepsItsFacing extends AutonomySessionFixture
{

    /**
     * A copy of a square nothing arrives at is no arrival, so a side barred while trains did arrive does not shut it
     * (its first half).  Such a square has one copy per way out, each named after the side its train would have come in
     * by; a bar reaching one shut it as no station, a bar the editor no longer shows and cannot clear.
     *
     * MUTATION: let a bar reach such a copy, and this fails.
     */
    @Test
    public void testAStaleBarDoesNotShutASquareNothingArrivesAt() throws Exception
    {
        final TileKey middle = new TileKey("main", 3, 1);

        threeStationsInARow("C6a");

        session.rebuild();

        assertTrue(session.arrivalSides(middle).contains(org.traincontrol.automationui.TilePorts.Side.E), "precondition:"
            + " trains do not arrive at the middle square from the east");

        session.setBarredArrivals(middle, new java.util.HashSet<>(Arrays.asList(org.traincontrol.automationui.TilePorts.Side.E)));

        oneWayAwayFromTheMiddle();

        assertTrue(session.arrivalSides(middle).isEmpty(), "precondition: something still arrives at the middle square");

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        int copies = 0;

        for (Object o : built.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            if (!point.getString("name").startsWith("main 3,1")) continue;

            copies++;

            assertTrue(point.optBoolean("station"), point.getString("name") + " is built as no station, shut by a bar on a"
                + " side nothing arrives by - one the editor does not show (RSA3-C6)");
        }

        assertEquals(copies, 2, "precondition: the middle square is not built as a copy per way out");
    }

    /**
     * A square nothing arrives at offers no home facing: no train can be brought home to either copy (its second half;
     * *"we shouldn't allow an impossible facing to be saved"*, OB-282).  Otherwise a train homed facing one way and
     * standing there the other was one Return Home called IMPOSSIBLE.
     *
     * MUTATION: offer a home facing on such a square, or hold a home there to one, and this fails.
     */
    @Test
    public void testASquareNothingArrivesAtOffersNoHomeFacing() throws Exception
    {
        final TileKey middle = new TileKey("main", 3, 1);

        threeStationsInARow("C6b");

        oneWayAwayFromTheMiddle();

        assertTrue(session.arrivalSides(middle).isEmpty(), "precondition: something still arrives at the middle square");

        assertTrue(session.homeFacingsFor(middle).isEmpty(), "a home facing is offered on a square no train can arrive at"
            + " (RSA3-C6): " + session.homeFacingsFor(middle));

        session.placeLocomotive(middle, "C6 train");
        session.setFacing(middle, org.traincontrol.automationui.TilePorts.Side.E);
        session.setHome(middle, "C6 train");

        session.setPointProperty(middle, AutonomyBuilder.HOME_FACING, "W");
        session.rebuild();

        for (Object o : new org.json.JSONObject(session.buildConfiguration()).getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            if (!point.getString("name").startsWith("main 3,1")) continue;

            assertFalse(point.optBoolean(AutonomyBuilder.HOME_FACING_FIXED, false), point.getString("name") + " holds its"
                + " home to a facing no train can be brought home in (RSA3-C6)");
        }
    }

    /**
     * The same on a feedback double curve whose two arcs each lead out one way: each copy stands for a train facing its
     * way out, and so came in by the OTHER end of that arc, which here is no way out at all.  A copy given the side it
     * leaves by instead of the side behind it builds two copies with no way out, and the placed train is stranded.
     *
     * MUTATION: give each copy the side it leaves by, and this fails.
     */
    @Test
    public void testADoubleCurveNothingArrivesAtFacesItsPlacedTrain() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 6, 5, null, null);

        page.addComponent(componentType.FEEDBACK, 2, 0, 1, 0, 70, 70, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 1, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK_DOUBLE_CURVE, 2, 2, 0, 0, 71, 71, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 2, 0, 0, 72, 72, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("C7 curve");

        final TileKey curve = new TileKey("main", 2, 2);

        for (TileKey station : Arrays.asList(new TileKey("main", 2, 0), curve, new TileKey("main", 4, 2)))
        {
            session.setStation(station, true);
        }

        awayOnly(new TileKey("main", 2, 1), org.traincontrol.automationui.TilePorts.Side.N);
        awayOnly(new TileKey("main", 3, 2), org.traincontrol.automationui.TilePorts.Side.E);

        session.placeLocomotive(curve, "C7 curve train");
        session.rebuild();

        assertTrue(session.facingChoices(curve).containsAll(Arrays.asList(org.traincontrol.automationui.TilePorts.Side.N,
            org.traincontrol.automationui.TilePorts.Side.E)), "a double curve nothing arrives at, with one way out on each"
            + " arc, offers no choice of which way its train faces (RSA2-C7): " + session.facingChoices(curve));

        for (org.traincontrol.automationui.TilePorts.Side facing : new org.traincontrol.automationui.TilePorts.Side[] {
            org.traincontrol.automationui.TilePorts.Side.N, org.traincontrol.automationui.TilePorts.Side.E})
        {
            session.setFacing(curve, facing);

            org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

            java.util.Map<String, Integer> sensorOf = new java.util.HashMap<>();

            String standing = null;

            for (Object o : built.getJSONArray("points"))
            {
                org.json.JSONObject point = (org.json.JSONObject) o;

                sensorOf.put(point.getString("name"), point.optInt("s88"));

                org.json.JSONObject held = point.optJSONObject(AutonomyBuilder.LOCOMOTIVE);

                if (held != null && "C7 curve train".equals(held.optString("name", null))) standing = point.getString("name");
            }

            assertNotNull(standing, "precondition: the train placed on the curve is on no Point");

            int ahead = facing == org.traincontrol.automationui.TilePorts.Side.N ? 70 : 72;

            List<Integer> reached = new ArrayList<>();

            for (Object o : built.getJSONArray("edges"))
            {
                org.json.JSONObject edge = (org.json.JSONObject) o;

                if (standing.equals(edge.getString("start"))) reached.add(sensorOf.get(edge.getString("end")));
            }

            assertEquals(reached, Arrays.asList(ahead), "the train placed on the curve facing " + facing + " is not"
                + " offered exactly the station that way (RSA2-C7): " + reached);
        }
    }

    private void awayOnly(TileKey straight, org.traincontrol.automationui.TilePorts.Side towards)
    {
        for (java.util.Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> route
            : session.getGraph().getRoutes(straight).entrySet())
        {
            session.setDirection(straight, route.getKey(), route.getValue().getA() == towards ? Direction.TOWARD_A
                : Direction.TOWARD_B);
        }
    }

    /**
     * A station no track arrives at records which way the train placed on it faces, and offers it only the way out it
     * faces (*"Go with a."*).  Built whole, a train placed on it by hand was offered both ways out and could drive off
     * the way it does not face, since the runtime never commands a direction and the copy IS the facing.  Three
     * stations in a row, the track either side of the middle one made one-way away from it.
     *
     * MUTATION: emit the square whole again, and this fails.
     */
    @Test
    public void testASquareNothingArrivesAtFacesItsPlacedTrain() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 7, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 64, 64, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 65, 65, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 66, 66, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("C7");

        for (int x : new int[] {1, 3, 5}) session.setStation(new TileKey("main", x, 1), true);

        session.setDirection(new TileKey("main", 2, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.setDirection(new TileKey("main", 4, 1), new RouteId(0, 0), Direction.TOWARD_A);

        final TileKey middle = new TileKey("main", 3, 1);

        session.placeLocomotive(middle, "C7 train");
        session.rebuild();

        assertTrue(session.facingChoices(middle).containsAll(Arrays.asList(
            org.traincontrol.automationui.TilePorts.Side.E, org.traincontrol.automationui.TilePorts.Side.W)),
            "a square nothing arrives at, with track leaving it both ways, offers no choice of which way its train faces"
            + " (RSA2-C7): " + session.facingChoices(middle));

        for (org.traincontrol.automationui.TilePorts.Side facing : new org.traincontrol.automationui.TilePorts.Side[] {
            org.traincontrol.automationui.TilePorts.Side.E, org.traincontrol.automationui.TilePorts.Side.W})
        {
            session.setFacing(middle, facing);

            org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

            java.util.Map<String, Integer> sensorOf = new java.util.HashMap<>();

            String standing = null;
            String itsFacing = null;

            for (Object o : built.getJSONArray("points"))
            {
                org.json.JSONObject point = (org.json.JSONObject) o;

                sensorOf.put(point.getString("name"), point.optInt("s88"));

                org.json.JSONObject held = point.optJSONObject(AutonomyBuilder.LOCOMOTIVE);

                if (held != null && "C7 train".equals(held.optString("name", null)))
                {
                    standing = point.getString("name");
                    itsFacing = point.optString(AutonomyBuilder.COPY_FACING, null);
                }
            }

            assertNotNull(standing, "precondition: the train placed on the middle square is on no Point");

            assertEquals(itsFacing, facing.name(), "the train placed facing " + facing + " stands on a copy that records"
                + " no facing, or another (RSA2-C7)");

            int ahead = facing == org.traincontrol.automationui.TilePorts.Side.E ? 66 : 64;

            List<Integer> reached = new ArrayList<>();

            for (Object o : built.getJSONArray("edges"))
            {
                org.json.JSONObject edge = (org.json.JSONObject) o;

                if (standing.equals(edge.getString("start"))) reached.add(sensorOf.get(edge.getString("end")));
            }

            assertFalse(reached.isEmpty(), "precondition: the train facing " + facing + " has no way out at all");

            for (Integer sensor : reached)
            {
                assertEquals(sensor.intValue(), ahead, "the train placed facing " + facing + " is offered the way out"
                    + " behind it, where it would drive off the other way over track nothing locked (RSA2-C7): "
                    + reached);
            }
        }
    }

    /**
     * A locomotive is pointing the same way after a save and a reopen as it was before.  Both hops are checked, because
     * they fail for different reasons: the FILE hop is the store writing and reading `facing`; the BUILD hop is
     * `buildConfiguration` emitting it and `captureFromLayout` reading it back, which once silently rewrote the value
     * as the arbitrary side `placementCopy` fell through to.
     */
    @Test
    public void testALocomotivesDirectionSurvivesASaveAndReopen() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        session.placeLocomotive(sensor, "Test Loc");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(sensor);

        assertFalse(choices.isEmpty(), "the fixture square offers no direction at all to record");

        org.traincontrol.automationui.TilePorts.Side chosen = choices.get(0);

        session.setFacing(sensor, chosen);

        assertEquals(session.getFacing(sensor), chosen, "control: the facing did not even go in");

        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(runOfTrack()));

        assertEquals(reopened.getFacing(sensor), chosen,
            "the locomotive is pointing a different way after a reload. A facing that does not survive"
            + " a save is worse than one that was never recorded: the diagram shows a direction the"
            + " operator chose until the next load, and something else afterwards");

        assertTrue(String.valueOf(reopened.getPointProperty(sensor, "loc")).contains("Test Loc"),
            "the locomotive itself did not survive the reload, so the facing above proved nothing");
    }

    /**
     * A home remembers which way its train was facing when it was set, and the build puts it on that copy (OB-282):
     * *"yes, it should accomplish the facing"*, taken as *"Direction it is facing when home is set."*  The home is
     * marked so that Return Home brings it back to that copy, and it survives a build and a capture.
     *
     * MUTATION: `writeHome` not recording the facing, or the build not marking the copy, fails this.
     */
    @Test
    public void testAHomeRemembersTheWayItsTrainWasFacing() throws Exception
    {
        session.open(Arrays.asList(throughStationPage()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 3, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.placeLocomotive(sensor, "Test Loc");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices = session.facingChoices(sensor);

        assertTrue(choices.size() > 1, "precondition: the square holds one facing, so which one the home keeps is no"
            + " choice: " + choices);

        org.traincontrol.automationui.TilePorts.Side chosen = choices.get(choices.size() - 1);

        session.setFacing(sensor, chosen);
        session.setHome(sensor, "Test Loc");

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            chosen.name(), "the home was set with the train facing " + chosen + " and does not remember it");

        String built = session.buildConfiguration();

        String homedOn = null;

        org.json.JSONArray points = new org.json.JSONObject(built).getJSONArray("points");

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            if (!"Test Loc".equals(point.optString("home", null))) continue;

            assertTrue(point.optBoolean(org.traincontrol.automationui.AutonomyBuilder.HOME_FACING_FIXED, false),
                "the home is emitted on " + point.optString("name") + " without saying it is held to that copy");

            homedOn = point.getString("name");
        }

        assertNotNull(homedOn, "the build carries no home at all");

        assertEquals(session.facingsFor(sensor).get(homedOn), chosen,
            "the home was set facing " + chosen + " and the build put it on " + homedOn + ", which faces "
            + session.facingsFor(sensor).get(homedOn));

        session.captureFromLayout(built);

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            chosen.name(), "a build and a capture changed the facing the home was set with");

        // Made by moving the home in the built configuration, as the running layout's `toJSON` writes it after
        // `setHomeLocomotive` on that copy.
        org.json.JSONObject running = new org.json.JSONObject(built);

        org.traincontrol.automationui.TilePorts.Side other = null;

        for (int at = 0; at < running.getJSONArray("points").length(); at++)
        {
            org.json.JSONObject point = running.getJSONArray("points").getJSONObject(at);

            org.traincontrol.automationui.TilePorts.Side faces = session.facingsFor(sensor).get(point.optString("name"));

            if (faces == null) continue;

            if (faces == chosen)
            {
                point.remove("home");
                point.remove(org.traincontrol.automationui.AutonomyBuilder.HOME_FACING_FIXED);
            }
            else
            {
                point.put("home", "Test Loc");
                point.put(org.traincontrol.automationui.AutonomyBuilder.HOME_FACING_FIXED, true);

                other = faces;
            }
        }

        assertNotNull(other, "precondition: the square has no copy facing another way to move the home to");

        session.captureFromLayout(running.toString());

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            other.name(), "a home set on the running diagram facing " + other + " was captured without its facing - the"
            + " next build puts it back on the copy facing " + chosen);
    }

    /**
     * A home given to a train standing elsewhere takes the facing asked for, and never one no train can stand in
     * (OB-282): *"prompt the user for the direction"*, *"we shouldn't allow an impossible facing to be saved."*
     */
    @Test
    public void testAHomeForATrainElsewhereTakesTheFacingAskedAndNoImpossibleOne() throws Exception
    {
        session.open(Arrays.asList(throughStationPage()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 3, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        java.util.Set<org.traincontrol.automationui.TilePorts.Side> canHold = session.homeFacingsFor(sensor);

        assertTrue(canHold.size() > 1, "precondition: the square offers one facing to be homed in: " + canHold);

        org.traincontrol.automationui.TilePorts.Side asked = new java.util.ArrayList<>(canHold).get(canHold.size() - 1);

        session.setHome(sensor, "Elsewhere Loc", asked);

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            asked.name(), "the facing asked for a home was not kept");

        org.traincontrol.automationui.TilePorts.Side impossible = null;

        for (org.traincontrol.automationui.TilePorts.Side side : org.traincontrol.automationui.TilePorts.Side.values())
        {
            if (!canHold.contains(side)) impossible = side;
        }

        assertNotNull(impossible, "precondition: every side is a facing here, so there is no impossible one to refuse");

        session.setHome(sensor, "Elsewhere Loc", impossible);

        assertNull(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            "a home was saved facing " + impossible + ", which no copy of this square a train may arrive at holds");
    }

    /**
     * And across a build and a capture, which is the hop that actually rewrote it: `captureFromLayout` takes the
     * running layout back into the setup, so a value that goes out through `buildConfiguration` and comes back changed
     * is a direction nobody chose.
     */
    @Test
    public void testADirectionSurvivesABuildAndACapture() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.placeLocomotive(sensor, "Test Loc");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(sensor);

        assertFalse(choices.isEmpty(), "the fixture square offers no direction to record");

        org.traincontrol.automationui.TilePorts.Side chosen = choices.get(0);

        session.setFacing(sensor, chosen);

        String built = session.buildConfiguration();

        assertTrue(built.contains("Test Loc"),
            "the build does not carry the locomotive, so the capture below has nothing to say about"
            + " it and this test would pass on an empty railway");

        session.captureFromLayout(built);

        assertEquals(session.getFacing(sensor), chosen,
            "a build and a capture changed which way the train is pointing. That is the mechanism"
            + " behind the defect Adam reported four times: a direction goes out, comes back"
            + " different, and is presented as one somebody chose");
    }

    /**
     * Taking a train off a square takes its arrival side with it: `arrivedFrom` describes the TRAIN that was standing
     * here, and `parseAuto` reads it straight back onto whatever is placed there next, so the walk that blocks track
     * behind a standing train would follow the previous train's tail, silently.  The menu offers no way to say "not
     * known", so the side must leave with the train.  Both doors that empty a square are asserted:
     * `clearEveryPlacement` is a separate loop that mirrors `placeLocomotive`, and the two have drifted before.
     */
    @Test
    public void testAnArrivalSideDoesNotOutliveItsTrain() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "W");

        assertEquals(session.getArrivedFrom(sensor), "W", "control: the side did not go in");

        session.placeLocomotive(sensor, null);

        assertNull(session.getArrivedFrom(sensor),
            "the square still says a train arrived from the west after the train was taken off it."
            + " The next locomotive placed here inherits that tail, and the track behind it is"
            + " blocked on the strength of where a different train came in (IND9-A1)");

        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "E");

        assertEquals(session.getArrivedFrom(sensor), "E", "control: the second side did not go in");

        assertTrue(session.clearEveryPlacement() > 0,
            "clearEveryPlacement cleared nothing, so the assertion below is about a square it never"
            + " visited");

        assertNull(session.getArrivedFrom(sensor),
            "clearEveryPlacement leaves the arrival side behind. It mirrors placeLocomotive and the"
            + " two must agree about what taking a train off a square means (IND9-A1)");
    }

    /**
     * A NEW occupant does not inherit the old one's tail.  Handing a square straight from one train to another is where
     * the stale value is least visible, and `GraphLocAssign.commitAndRecord` relies on the clear happening here.  The
     * same locomotive re-placed is asserted first: it is NOT a change of occupant, and a clear would throw away a side
     * that is still true.
     *
     * MUTATION: dropping the occupant-change clear in `placeLocomotive` fails the last assertion; clearing
     * unconditionally fails the middle one.
     */
    @Test
    public void testANewOccupantDoesNotInheritTheOldOnesTail() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "W");

        assertEquals(session.getArrivedFrom(sensor), "W", "control: the side did not go in");

        session.placeLocomotive(sensor, "Test Loc");

        assertEquals(session.getArrivedFrom(sensor), "W",
            "re-placing the same locomotive threw away an arrival side that is still true. Every door"
            + " that writes a placement back unchanged would silently clear it");

        session.placeLocomotive(sensor, "Other Loc");

        assertNull(session.getArrivedFrom(sensor),
            "the new locomotive inherited where the old one came in from. Neither assignment door asks"
            + " the arrival side, so this clear is the only thing standing between a handover and a"
            + " tail that blocks the wrong rail (VAL9-B5)");
    }
    /**
     * The arrival side reaches the running layout, and comes back from a save.  Both hops, because they fail for
     * different reasons: the FILE hop is the store, and the BUILD hop is what `parseAuto` is handed.  A property that
     * survives a save and never reaches the railway is a setting that does nothing.
     */
    @Test
    public void testTheArrivalSideReachesTheRailwayAndSurvivesASave() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "W");

        assertEquals(session.getArrivedFrom(sensor), "W", "control: it did not go in");

        String built = session.buildConfiguration();

        assertTrue(built.contains("arrivedFrom"),
            "the built configuration carries no arrival side, so an answer the operator gives never"
            + " reaches the railway and blocks nothing (VAL8-A2)");

        assertTrue(built.contains("\"arrivedFrom\": \"W\"") || built.contains("\"arrivedFrom\":\"W\""),
            "the arrival side is emitted but not as the side that was recorded: " 
            + built.substring(Math.max(0, built.indexOf("arrivedFrom") - 40),
                Math.min(built.length(), built.indexOf("arrivedFrom") + 40)));

        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(runOfTrack()));

        assertEquals(reopened.getArrivedFrom(sensor), "W",
            "the arrival side did not survive a save and reload, so the track a train is lying across"
            + " is forgotten every time the setup is loaded");
    }

    /**
     * A direction change never flips the facing of a square the train has LEFT.  `flipFacing` gathers the squares the
     * running layout says hold this train, then the setup's, and skips any it cannot decide.  After a run the arrival
     * square often has no recorded facing yet, so the loop moved on to the square the train set off from and flipped a
     * platform the train is not standing on.  The setup now gets a vote only when the railway has no opinion; a
     * locomotive recorded on two SETUP squares is still walked in full when the running layout does not know.
     */
    @Test
    public void testADirectionChangeDoesNotFlipTheSquareTheTrainLeft() throws IOException
    {
        // The fixture of testTheFlipMovesTheLocomotiveOnTheLayoutToo: flipFacing declines any square that does not
        // offer exactly two facings, and runOfTrack offers none.
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("Facing", null);
        session.getStore().setActiveConfiguration("Facing");

        final TileKey departed = new TileKey("main", 4, 1);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(departed);

        assertEquals(choices.size(), 2,
            "precondition: the square must offer two facings or flipFacing declines by design");

        session.setPointName(departed, "Platform 1");
        session.setStation(departed, true);
        session.placeLocomotive(departed, "Test Loc");

        session.setFacing(departed, choices.get(0));

        // A railway that knows nothing still lets the setup answer: the ordinary state before anything has moved.
        org.traincontrol.automation.Layout knowsNothing =
            new org.traincontrol.automation.Layout(null);

        session.flipFacing("Test Loc", knowsNothing);

        assertEquals(session.getFacing(departed), choices.get(1),
            "a railway with no opinion about this train stopped the setup answering at all, which is"
            + " DIR-C3 undone: a locomotive recorded on a square it has not left still gets a follow");

        // The fix needs a railway that DOES know the train, which this fixture cannot build, so it is asserted as the
        // rule: setup squares are gathered ONLY when the running layout offered none.
        String flip = SourceOf.declaration("public TileKey flipFacing");

        int gathers = flip.indexOf("for (Map.Entry<TileKey, String> placed : placedLocomotives()");

        int guarded = flip.indexOf("if (candidates.isEmpty())");

        assertTrue(guarded > 0 && guarded < gathers,
            "the setup's squares are appended to the running layout's answer again. The loop below"
            + " SKIPS a square it cannot decide rather than stopping, so an arrival square with no"
            + " recorded facing sends it on to the square the train LEFT - and that platform, now"
            + " empty, has its direction flipped for a train standing somewhere else (REG7-A2)");
    }

    /**
     * A station whose track runs out on one side, at a buffer stop or where the drawn track simply stops, before it
     * reaches another sensor is drawn as a terminus flat against that side (FR-118; *"a station with only one way out
     * becomes a terminus, correct?"*, confirmed).  The diagram's badge and the editor's carry the side; a station with
     * track going on both ways carries none, and a one-way run is no dead end: the track is what is asked.
     *
     * MUTATION: answer from the built edges, which a one-way run takes away, or drop the side from either badge, and
     * this fails.
     */
    @Test
    public void testAStationWhoseTrackRunsOutIsATerminus() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 12, 3, null, null);

        page.addComponent(componentType.END, 1, 1, 3, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 1, 0, 0, 81, 81, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 82, 82, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 7, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 8, 1, 0, 0, 83, 83, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 9, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("FR-118");

        TileKey buffer = new TileKey("main", 2, 1), through = new TileKey("main", 5, 1), open = new TileKey("main", 8, 1);

        for (TileKey station : new TileKey[] {buffer, through, open}) session.setStation(station, true);

        session.rebuild();

        assertEquals(session.deadEndSide(buffer), Side.W, "the station against the buffer stop is not a terminus facing"
            + " it (FR-118)");

        assertEquals(session.deadEndSide(open), Side.E, "the station whose track stops two squares on is not a terminus"
            + " facing that way (FR-118)");

        assertNull(session.deadEndSide(through), "a station with track going on both ways is drawn as a terminus");

        assertEquals(session.staticAnnotationFor(buffer).getBadge().getDeadEnd(), Side.W, "the diagram's badge for the"
            + " station against the buffer stop does not say which way its track runs out");

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session,
            "main", () -> { }));

        java.lang.reflect.Method badgeFor =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("badgeFor", TileKey.class);

        badgeFor.setAccessible(true);

        assertEquals(((TileAnnotation.Badge) badgeFor.invoke(panel[0], open)).getDeadEnd(), Side.E, "the editor's badge"
            + " for the station whose track stops does not say which way it runs out");

        session.setDirection(new TileKey("main", 6, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.setDirection(new TileKey("main", 7, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.rebuild();

        assertNull(session.deadEndSide(through), "a station trains cannot leave eastwards only because of a one-way run"
            + " is drawn as a terminus - Adam asked about a station with one way out of the track");

        assertEquals(session.deadEndSide(buffer), Side.W, "control: the buffer stop's station lost its dead end");
    }

    /**
     * A placement naming a locomotive this database does not have is refused, not written in.  The running model drops
     * such a placement with a log line (`core.testHomeStaging
     * .testAPlacementForALocomotiveNotInTheDatabaseDropsOnlyThePlacement`: *"drop the train and keep the rest."*), so
     * this test is about the importer NAMING what it dropped, which the user can act on.
     */
    @Test
    public void testAPlacementForAnUnknownLocomotiveIsRefused() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONObject standing = new org.json.JSONObject();
        standing.put("name", "Sold Years Ago");

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St21");
        point.put("station", true);
        point.put("s88", 11);
        point.put("loc", standing);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        Set<String> known = new LinkedHashSet<>(Arrays.asList("Q 343", "MY 1106"));

        AutonomySession.LegacyImport result = session.importLegacy(legacy, known);

        assertEquals(result.placed, 0, "a locomotive the database does not have must not be placed");

        assertEquals(result.unknownLocomotives, Arrays.asList("Sold Years Ago"),
            "the unknown locomotive must be named, since it is the reason a placement is missing");

        assertEquals(session.getStore().getPointName(new TileKey("main", 1, 1)), "St21",
            "the name should still have been imported - only the placement was refused");
    }

    /**
     * A locomotive named at two points is placed once, and the second is reported: the running model invalidates the
     * layout rather than ignoring the second.
     */
    @Test
    public void testALocomotiveNamedTwiceIsPlacedOnce() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONArray points = new org.json.JSONArray();

        for (int sensor : new int[] {11, 12})
        {
            org.json.JSONObject standing = new org.json.JSONObject();
            standing.put("name", "Q 343");

            org.json.JSONObject point = new org.json.JSONObject();
            point.put("name", "St" + sensor);
            point.put("station", true);
            point.put("s88", sensor);
            point.put("loc", standing);

            points.put(point);
        }

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy,
            new LinkedHashSet<>(Arrays.asList("Q 343")));

        assertEquals(result.placed, 1, "the locomotive should have been placed exactly once");

        assertEquals(result.duplicateLocomotives, Arrays.asList("Q 343"),
            "the second placement must be reported rather than silently dropped");
    }

    /**
     * Without a database to check against, placements are taken as given: passing nothing must not mean refusing
     * everything.
     */
    @Test
    public void testPlacementsAreTakenAsGivenWhenThereIsNothingToCheckAgainst() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONObject standing = new org.json.JSONObject();
        standing.put("name", "Anything At All");

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St21");
        point.put("s88", 11);
        point.put("loc", standing);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy, null);

        assertEquals(result.placed, 1, "with no database given, the placement should be taken as-is");

        assertTrue(result.unknownLocomotives.isEmpty(), "nothing can be unknown with nothing to check");
    }

    /**
     * A home authored on a split square is emitted onto exactly ONE copy: the running model refuses two Points claiming
     * one locomotive, so emitting it on every copy had `rebuildHomeStations` strip all but the first at every load with
     * a warning per copy.  Needs a THROUGH station: a square with one way in cannot be split.
     */
    @Test
    public void testAHomeOnASplitSquareIsEmittedOnce() throws Exception
    {
        LayoutDiagram page = throughStationPage();

        session.open(Arrays.asList(page));
        session.getStore().createConfiguration("Homes", null);
        session.getStore().setActiveConfiguration("Homes");

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        // "Trains may turn round here" (a berth or a stub platform) gives the square a turning copy of every arrival.
        session.setPointProperty(station, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE,
            Boolean.TRUE);

        session.setPointProperty(station, "home", "BR 218");

        session.rebuild();

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        int copies = 0;
        int carrying = 0;

        for (Object o : built.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            if (station.toString().equals(point.optString("block", null))) copies++;

            if ("BR 218".equals(point.optString("home", null))) carrying++;
        }

        assertTrue(copies > 1,
            "precondition: this square must be emitted as several copies, or there is nothing to test");

        assertEquals(carrying, 1,
            "a home emitted onto every copy of a split square is stripped back to one at load, with a "
                + "warning per copy blaming a file nobody edited");
    }

    /**
     * A locomotive is recorded standing in one place, never two.  The running layout enforces that when a train is
     * moved, but the CONFIGURATION must be told too: a locomotive at two Points makes `fromJSON` invalidate the whole
     * layout, and every path is then refused as "configuration is invalid".
     */
    @Test
    public void testALocomotiveIsRecordedInOnePlaceOnly() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey was = new TileKey("main", 1, 1);
        TileKey now = new TileKey("main", 4, 1);

        session.placeLocomotive(was, "065 001-0 DB");

        ((org.json.JSONObject) session.getPointProperty(was, AutonomyBuilder.LOCOMOTIVE))
            .put("speed", 42);

        session.placeLocomotive(now, "065 001-0 DB");

        assertNull(session.getPointProperty(was, AutonomyBuilder.LOCOMOTIVE),
            "the locomotive is still recorded where it was, so the next build emits it twice and "
                + "invalidates the whole layout");

        org.json.JSONObject standing =
            (org.json.JSONObject) session.getPointProperty(now, AutonomyBuilder.LOCOMOTIVE);

        assertNotNull(standing, "the locomotive is not recorded where it was put");

        assertEquals(standing.getInt("speed"), 42,
            "the placement was rebuilt rather than moved, so its settings were lost");
    }

    /**
     * Taking a locomotive off forgets it, and forgets which way it was pointing: the next build reads the
     * configuration, and the facing belonged to that train, not to the square.
     */
    @Test
    public void testTakingALocomotiveOffForgetsItAndItsFacing() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey tile = new TileKey("main", 1, 1);

        session.placeLocomotive(tile, "SM31-108");
        session.setFacing(tile, org.traincontrol.automationui.TilePorts.Side.E);

        session.placeLocomotive(tile, null);

        assertNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE),
            "the next build would put the train straight back");

        assertNull(session.getFacing(tile), "the square kept a direction belonging to a train that has gone");
    }

    /**
     * A station takes trains from anywhere until somebody says otherwise.  The default is free and costs nothing to
     * store, so track added to the diagram later arrives open instead of barred by a setting nobody ever opened.
     */
    @Test
    public void testAStationTakesTrainsFromAnywhereByDefault() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);

        assertTrue(session.getBarredArrivals(station).isEmpty(),
            "a station nobody has restricted is carrying a restriction");

        assertTrue(session.arrivalMarks(station, false).isEmpty(),
            "an unrestricted station draws marks on the running diagram, which is the clutter this "
            + "setting exists to avoid");

        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey restricted = new TileKey("main", 3, 1);

        session.setStation(restricted, true);
        session.setPointName(restricted, "Bahnhof");

        assertTrue(session.arrivalMarks(restricted, false).isEmpty(), "control: the second station is unrestricted too");

        org.traincontrol.automationui.TilePorts.Side shut = session.arrivalSides(restricted).get(0);

        session.setBarredArrivals(restricted, new java.util.LinkedHashSet<>(Arrays.asList(shut)));

        boolean marked = false;

        for (TileAnnotation.Arrival mark : session.arrivalMarks(restricted, false))
        {
            if (mark.getSide() == shut && !mark.isAllowed()) marked = true;
        }

        assertTrue(marked, "a station with a side barred draws no mark for it on the running diagram");
    }

    /**
     * Barring a side stops trains stopping there, and nothing else: the restriction lands on the copy for that arrival
     * side, which stops being a station, while the copy and every edge through it stay (running THROUGH a square is the
     * direction arrows' question).
     */
    @Test
    public void testBarringAnArrivalSideOnlyStopsTrainsStoppingThere() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Bahnhof");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
            session.arrivalSides(station);

        assertTrue(ways.size() > 1, "precondition: this fixture has a square with two ways in");

        org.traincontrol.automationui.TilePorts.Side shut = ways.get(0);

        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<>(Arrays.asList(shut)));

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());
        org.json.JSONArray points = built.getJSONArray("points");

        int stations = 0;
        int copies = 0;

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            if (!point.getString("name").startsWith("Bahnhof")) continue;

            copies++;

            if (point.getBoolean("station")) stations++;
        }

        assertTrue(copies > 1, "the square has to still be emitted as every copy it was before");

        assertTrue(stations > 0, "barring one way in shut the whole station");

        assertTrue(stations < copies,
            "the barred side is still a place autonomy can send a train to");
    }

    /**
     * Barring a side of a station where trains turn round still loads.  "station" is per copy, but "terminus" was read
     * off the SQUARE, so the reverse copy of a barred side was a terminus that is not a destination, which
     * `Point.setTerminus` refuses and `parseAuto` answers by invalidating the WHOLE layout.
     */
    @Test
    public void testBarringASideOfATurnAroundStationStillLoads() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Terminus", null);
        session.getStore().setActiveConfiguration("Terminus");
        session.rebuild();

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Kopfbahnhof");
        session.setPointFlag(station, AutonomyBuilder.CAN_REVERSE, true);

        assertTrue(session.isTurnAround(station), "precondition: trains may turn round here");

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(
            Arrays.asList(session.arrivalSides(station).get(0))));

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());
        org.json.JSONArray points = built.getJSONArray("points");

        assertTrue(points.length() > 0,
            "precondition: nothing was scanned, so the loop below could not have found the bad pair "
            + "even if it were still there");

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            assertFalse(point.optBoolean("terminus", false) && !point.optBoolean("station", false),
                "a terminus that is not a destination is refused by Point.setTerminus, which "
                + "invalidates the whole configuration: " + point);
        }
    }

    /** Lifting a restriction leaves nothing behind. */
    @Test
    public void testLiftingAnArrivalRestrictionStoresNothing() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        org.traincontrol.automationui.TilePorts.Side shut = session.arrivalSides(station).get(0);

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(Arrays.asList(shut)));
        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<org.traincontrol.automationui.TilePorts.Side>());

        assertFalse(session.barredArrivals().containsKey(station),
            "the square kept an empty restriction, which is a setting that says nothing");
    }

    /**
     * A restriction naming a side the square no longer has is ignored, and then forgotten: the stale side is dead in
     * the build but was still COUNTED, which decides whether the menu will let another side be shut.
     */
    @Test
    public void testARestrictionOnASideTheSquareNoLongerHasIsIgnored() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> both =
            session.arrivalSides(station);

        assertEquals(both.size(), 2, "precondition: two ways in");

        // The side facing the track about to be taken up, so the restriction is the one that goes stale.
        org.traincontrol.automationui.TilePorts.Side doomed =
            org.traincontrol.automationui.TileGraph.gridSideTowards(station, new TileKey("main", 2, 1));

        assertTrue(both.contains(doomed), "precondition: trains arrive from that side today");

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(Arrays.asList(doomed)));

        session.save();

        LayoutDiagram shortened = pageWithATwoEndedStation();

        shortened.addComponent(null, 2, 1);
        shortened.addComponent(null, 1, 1);

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(shortened));

        assertEquals(reopened.arrivalSides(station).size(), 1,
            "precondition: the square lost a way in");

        assertTrue(reopened.getBarredArrivals(station).isEmpty(),
            "a side that no longer exists is still being counted against the ways in");

        reopened.save();

        AutonomySession again = new AutonomySession(layout);
        again.open(Arrays.asList(shortened));

        assertFalse(again.barredArrivals().containsKey(station),
            "the dead side is still in the file, ready to come back the day the diagram does");
    }

    /**
     * Excluding a page does not destroy the arrival restrictions on it: the graph leaves excluded pages out by
     * construction, so a save that pruned against the live sides read "every way in has gone".  Excluding must be
     * reversible.
     */
    @Test
    public void testExcludingAPageKeepsItsArrivalRestrictions() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        java.util.Set<org.traincontrol.automationui.TilePorts.Side> barred =
            new java.util.LinkedHashSet<>(Arrays.asList(session.arrivalSides(station).get(0)));

        session.setBarredArrivals(station, barred);

        session.getStore().setPageExcluded("main", true);
        session.rebuild();

        assertTrue(session.arrivalSides(station).isEmpty(),
            "precondition: an excluded page is not in the graph, so the square has no sides");

        session.save();

        session.getStore().setPageExcluded("main", false);
        session.rebuild();

        assertEquals(session.getBarredArrivals(station), barred,
            "the restriction was destroyed by excluding the page, and re-including gave nothing back");
    }

    /**
     * A station demoted and promoted again has every setting it had as a station (*"keep them, ignored while not a
     * station"*, as its maximum train length, OB-291, and its blockers already were): the caption, the sides it bars
     * arrivals by, its exit guard, its entry guards, and the turn flag older setups spell "terminus".  Through the
     * editor's own answer to the station menu, `setUsage`, because the turn flag was cleared there and the other four
     * in the session.  The caption sits two squares below the platform, where the placer never puts one, so a caption
     * placed afresh on promotion cannot pass for the one kept.
     *
     * MUTATION: clear any of the five on demotion again, and this fails naming it.
     */
    @Test
    public void testADemotedStationHasItsSettingsBackWhenItIsAStationAgain() throws Exception
    {
        org.traincontrol.gui.AutonomyEditorPanel panel = aStationWithEverySetting();

        TileKey station = new TileKey("main", 3, 1);

        Set<Side> barred = session.getBarredArrivals(station);

        answerTheStationMenu(panel, station, false);
        answerTheStationMenu(panel, station, true);

        assertTrue(session.getStore().isStation(station), "precondition: the square is not a station again");

        assertEquals(session.getCaptionTarget(new TileKey("main", 3, 3)), station, "the station's caption did not come"
            + " back with it (GSE-C4)");

        assertEquals(session.getBarredArrivals(station), barred, "the sides the station barred arrivals by did not come"
            + " back with it (GSE-C4)");

        assertEquals(session.getProtectingSignals(station), Arrays.asList(new TileKey("main", 2, 1)), "the station's"
            + " exit guard did not come back with it (GSE-C4)");

        assertEquals(session.getEntrySignals(station), Arrays.asList(new TileKey("main", 4, 1)), "the station's entry"
            + " guard did not come back with it (GSE-C4)");

        assertEquals(session.getPointProperty(station, "terminus"), Boolean.TRUE, "the station's turn flag did not come"
            + " back with it (GSE-C4)");
    }

    /**
     * A double-curve station whose track runs out on the contact's curve is a terminus facing that way: `deadEndSide`
     * must ask the contact's curve, not the square's first road.  Every orientation, the side past the contact's
     * curve's second end left with no track is the badge's dead end.
     *
     * MUTATION: ask the first road again, and this fails.
     */
    @Test
    public void testADoubleCurveStationWhoseTrackRunsOutIsATerminus() throws Exception
    {
        for (int orientation = 0; orientation < 4; orientation++)
        {
            session = new AutonomySession(layout);

            Route contact = org.traincontrol.automationui.TilePorts.contactRoad(componentType.FEEDBACK_DOUBLE_CURVE,
                orientation);

            Side gone = contact.getB();

            LayoutDiagram page = new LayoutDiagram("main", 6, 6, null, null);

            if (gone != Side.N) page.addComponent(componentType.FEEDBACK, 2, 1, 1, 0, 21, 21, accessoryDecoderType.MM2, null);
            if (gone != Side.W) page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 22, 22, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.FEEDBACK_DOUBLE_CURVE, 2, 2, orientation, 0, 25, 25, accessoryDecoderType.MM2,
                null);
            if (gone != Side.E) page.addComponent(componentType.FEEDBACK, 3, 2, 0, 0, 23, 23, accessoryDecoderType.MM2, null);
            if (gone != Side.S) page.addComponent(componentType.FEEDBACK, 2, 3, 1, 0, 24, 24, accessoryDecoderType.MM2, null);
            page.setPageId("1");

            session.open(Arrays.asList(page));
            session.initialize("dead end " + orientation);

            TileKey curve = new TileKey("main", 2, 2);

            session.setStation(curve, true);

            TileAnnotation annotation = session.staticAnnotationFor(curve, false, false);

            assertNotNull(annotation, "precondition: no annotation at orientation " + orientation);
            assertNotNull(annotation.getBadge(), "precondition: no badge at orientation " + orientation);

            assertEquals(annotation.getBadge().getDeadEnd(), gone, "a double-curve station turned " + orientation + " whose"
                + " track runs out at " + gone + " on the contact's curve is not drawn as a terminus facing it (RSA63-C2)");
        }
    }

    /**
     * A two-ended station with a signal on each side of it, for every setting a station has and a plain point has not:
     * the station at 3,1, entered from the west and from the east, and signals at 2,1 (address 21) and 4,1 (address
     * 22), wired as parsing a real layout wires them.
     */
    private LayoutDiagram pageWithAGuardedTwoEndedStation() throws IOException
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        page.addComponent(componentType.SIGNAL, 2, 1, 0, 0, 21, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SIGNAL, 4, 1, 0, 0, 22, 0, accessoryDecoderType.MM2, null);

        wire(page, 2, 1, 21);
        wire(page, 4, 1, 22);

        return page;
    }

    /**
     * Opens that page, makes 3,1 a station and gives it every setting a plain point does not have: a caption at 3,3,
     * its first arrival side barred, the signal at 2,1 as its exit guard, the one at 4,1 as its entry guard, and the
     * turn flag.  Each is asserted as set before anything is demoted.  Returns an editor over the page.
     */
    private org.traincontrol.gui.AutonomyEditorPanel aStationWithEverySetting() throws Exception
    {
        session.open(Arrays.asList(pageWithAGuardedTwoEndedStation()));
        session.initialize("Default");

        TileKey station = new TileKey("main", 3, 1);
        TileKey caption = new TileKey("main", 3, 3);
        TileKey exit = new TileKey("main", 2, 1);
        TileKey entry = new TileKey("main", 4, 1);

        session.setStation(station, true);
        session.setPointName(station, "Platform");
        session.setCaption(caption, station);
        session.setBarredArrivals(station, new LinkedHashSet<>(Arrays.asList(session.arrivalSides(station).get(0))));
        session.setProtectingSignal(station, exit);
        session.setEntrySignals(station, Arrays.asList(entry));
        session.setPointProperty(station, "terminus", Boolean.TRUE);

        assertEquals(session.getCaptionTarget(caption), station, "precondition: the caption was not set");

        assertFalse(session.getBarredArrivals(station).isEmpty(), "precondition: no arrival side was barred");

        assertEquals(session.getProtectingSignals(station), Arrays.asList(exit), "precondition: the exit guard was not"
            + " paired");

        assertEquals(session.getEntrySignals(station), Arrays.asList(entry), "precondition: the entry guard was not"
            + " paired");

        assertEquals(session.getPointProperty(station, "terminus"), Boolean.TRUE, "precondition: the turn flag was not"
            + " set");

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { }));

        return panel[0];
    }

    /**
     * A square that stops being a Point keeps its locomotive.  A sensor reduces to a Point only where track connects
     * it, so a station nudged out of its run is present on the diagram and is not a Point.  The placement is kept, the
     * build refuses it, and it comes back when the track is joined up again: a disconnected station is a mistake
     * somebody is in the middle of making.  A square whose TILE is gone still keeps nothing.
     */
    @Test
    public void testASquareThatStopsBeingAPointKeepsItsLocomotive() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));
        session.initialize("Default");

        // 2,1 is certainly ON the page and not a Point, the state a sensor lands in when moved out of its run.
        TileKey adrift = new TileKey("main", 2, 1);

        session.setStation(new TileKey("main", 1, 1), true);

        org.json.JSONObject running = new org.json.JSONObject();

        running.put("minDelay", 1);
        running.put("maxDelay", 2);
        running.put("edges", new org.json.JSONArray());

        org.json.JSONArray points = new org.json.JSONArray();

        running.put("points", points);

        session.captureFromLayout(running.toString());

        org.json.JSONObject config = session.getStore().getConfiguration("Default");

        // Written straight into the configuration: nothing in the running layout can speak for a square with no Point.
        org.json.JSONObject standing = new org.json.JSONObject();

        standing.put("loc", new org.json.JSONObject().put("name", "BR 218"));

        config.getJSONObject("points").put(adrift.toString(), standing);

        session.captureFromLayout(running.toString());

        org.json.JSONObject after = session.getStore()
            .getConfiguration("Default").getJSONObject("points");

        assertTrue(after.has(adrift.toString()),
            "the square is still on the diagram and its locomotive was deleted anyway.  Moving a tile "
            + "out of a run is a mistake somebody is in the middle of making, not an instruction to "
            + "forget the train standing on it");

        assertEquals(after.getJSONObject(adrift.toString())
            .getJSONObject("loc").getString("name"), "BR 218");
    }

    /**
     * A demoted station's settings are kept and IGNORED while it is not a station: nothing builds, checks or draws
     * them.  Each was once cleared on demotion for a reason that still holds of a plain point (a caption lights a
     * station's name up when a train reverses there, a guard is thrown red over a square nothing is sent to, the old
     * turn flag turns every train round), so the readers ask whether the square is a station.
     *
     * MUTATION: hand the build every square's exit guards again, station or not, and this fails.
     */
    @Test
    public void testADemotedStationsSettingsAreIgnoredWhileItIsNotAStation() throws Exception
    {
        org.traincontrol.gui.AutonomyEditorPanel panel = aStationWithEverySetting();

        TileKey station = new TileKey("main", 3, 1);
        TileKey caption = new TileKey("main", 3, 3);

        answerTheStationMenu(panel, station, false);

        session.rebuild();

        assertFalse(session.getStore().isStation(station), "precondition: the square is still a station");

        assertEquals(session.getStore().getCaptionTarget(caption), station, "precondition: demoting the station took its"
            + " caption, so nothing below asks whether a kept one is ignored (GSE-C4)");

        assertFalse(session.getStore().getBarredArrivals(station).isEmpty(), "precondition: demoting the station took"
            + " its arrival restriction (GSE-C4)");

        assertFalse(session.getStore().getProtectingSignals(station).isEmpty(), "precondition: demoting the station took"
            + " its exit guard (GSE-C4)");

        assertFalse(session.getStore().getEntrySignals(station).isEmpty(), "precondition: demoting the station took its"
            + " entry guard (GSE-C4)");

        assertEquals(session.getPointProperty(station, "terminus"), Boolean.TRUE, "precondition: demoting the station"
            + " took its turn flag (GSE-C4)");

        assertNull(session.getCaptionTarget(caption), "a plain point's kept caption is drawn - the diagram asks this -"
            + " and lights a station name up when a train reverses there");

        assertFalse(session.getLabelledStationTiles().contains(station), "a plain point counts as a labelled station");

        assertTrue(session.getBarredArrivals(station).isEmpty(), "a plain point bars arrivals by its kept restriction");

        assertFalse(session.barredArrivals().containsKey(station), "the build and the checks are given a plain point's"
            + " kept arrival restriction");

        assertNull(session.getProtectingSignal(station), "a plain point has an exit guard");

        assertTrue(session.getEntrySignals(station).isEmpty(), "a plain point has an entry guard");

        assertFalse(session.protectingSignalNames().containsKey(station), "the build is given a plain point's kept exit"
            + " guard, and throws it red");

        assertFalse(session.entrySignalNames().containsKey(station), "the build is given a plain point's kept entry"
            + " guard, and throws it red");

        assertFalse(session.isTurnAround(station), "a plain point's kept turn flag turns trains round there");

        org.json.JSONArray points = new org.json.JSONObject(session.buildConfiguration()).getJSONArray("points");

        int copies = 0;

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            if (!station.toString().equals(point.optString("square"))) continue;

            copies++;

            assertFalse(point.has("protectingSignal") || point.has("entrySignal"), "the built railway throws a kept guard"
                + " for a square that is not a station: " + point);

            assertFalse(point.optBoolean("terminus", false) || point.optBoolean("reversing", false), "the built railway"
                + " turns trains round on a plain point by its kept turn flag: " + point);
        }

        assertTrue(copies > 0, "precondition: the built railway has no point for " + station + ", so nothing above read"
            + " it");
    }

    /**
     * A square that stops being a station keeps whatever train was standing on it: the designation and the placement
     * are separate records.  It is why the editor menu offers "remove" against the LOCOMOTIVE rather than the
     * designation.
     */
    @Test
    public void testDemotingAStationLeavesItsLocomotiveToBeRemoved() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Demoted", null);
        session.getStore().setActiveConfiguration("Demoted");

        TileKey tile = new TileKey("main", 1, 1);

        session.setStation(tile, true);
        session.placeLocomotive(tile, "SM31-108");

        session.setStation(tile, false);

        assertFalse(session.getStore().isStation(tile), "the square was demoted");

        assertNotNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE),
            "demotion dropped the placement, so there would be nothing left to offer to remove");

        session.placeLocomotive(tile, null);

        assertNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE),
            "a train could be left on a square with no way to take it off");
    }

    /**
     * A locomotive recorded in two places is reported before anything tries to run: `fromJSON` refuses the WHOLE
     * layout, and every path is then answered with "configuration is invalid and must be reloaded", naming neither
     * locomotive nor square.  So it is a check, on the square that can be cleared to fix it.
     */
    @Test
    public void testALocomotiveInTwoPlacesIsReportedAsAnError() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey first = new TileKey("main", 1, 1);
        TileKey second = new TileKey("main", 4, 1);

        session.getStore().setStation(first, true);
        session.setPointName(first, "BottomMainA");

        session.getStore().setStation(second, true);
        session.setPointName(second, "BottomMainC");

        // Written straight into the configuration, as it happens: one placement from an import and one made by hand.
        session.setPointProperty(first, "loc", new org.json.JSONObject().put("name", "065 001-0 DB"));
        session.setPointProperty(second, "loc", new org.json.JSONObject().put("name", "065 001-0 DB"));

        session.rebuild();

        boolean reported = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (!org.traincontrol.automationui.AutonomyChecks.DUPLICATE_LOCOMOTIVE
                .equals(finding.getMessageKey())) continue;

            reported = true;

            assertEquals(finding.getSeverity(),
                org.traincontrol.automationui.AutonomyChecks.Severity.ERROR,
                "a setup that will refuse every path is not a warning");

            // A finding with a tile is described by its tile, which gives it somewhere to jump to.
            assertEquals(finding.getSubject(), "065 001-0 DB",
                "the finding no longer carries which locomotive is doubled up");

            assertNotNull(finding.getTile(),
                "without a square there is nothing for the reader to jump to and clear");
        }

        assertTrue(reported, "a locomotive standing in two places was not reported at all");
    }

    /**
     * And one locomotive in one place is not reported: a check that fired on every placement would satisfy the test
     * above.
     */
    @Test
    public void testALocomotiveInOnePlaceIsNotReported() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey tile = new TileKey("main", 1, 1);

        session.getStore().setStation(tile, true);
        session.setPointName(tile, "BottomMainA");
        session.placeLocomotive(tile, "065 001-0 DB");

        session.rebuild();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            assertFalse(org.traincontrol.automationui.AutonomyChecks.DUPLICATE_LOCOMOTIVE
                .equals(finding.getMessageKey()),
                "a locomotive standing in one place was reported as being in two");
        }
    }

    /** And placing it somewhere new takes it off where it was, so the check never fires from a move. */
    @Test
    public void testMovingALocomotiveDoesNotLeaveItInTwoPlaces() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey was = new TileKey("main", 1, 1);
        TileKey now = new TileKey("main", 4, 1);

        session.placeLocomotive(was, "065 001-0 DB");
        session.placeLocomotive(now, "065 001-0 DB");

        assertNull(session.getPointProperty(was, "loc"),
            "the locomotive is still recorded where it was, which is what invalidates the layout");

        assertNotNull(session.getPointProperty(now, "loc"),
            "the locomotive is not recorded where it was put");
    }

    /**
     * The bulk clear touches exactly the squares the findings call homes.  `homeTiles()` feeds the findings,
     * `tilesWithAHome()` decides what Clear All Home Locomotives touches, and `homesElsewhere()` and `shutTiles()` walk
     * the same map; they are one walk now, and a qualification added to one alone would have the button act on a set
     * the findings never mentioned.
     *
     * MUTATION: give either method a filter the other does not have and the first assertion fails.
     */
    @Test
    public void testTheHomeWalksAgree() throws Exception
    {
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("Homes", null);
        session.getStore().setActiveConfiguration("Homes");

        final TileKey one = new TileKey("main", 1, 1);
        final TileKey two = new TileKey("main", 7, 1);

        session.setPointProperty(one, "home", "Loc A");
        session.setPointProperty(two, "home", "Loc B");

        session.setPointProperty(new TileKey("main", 4, 1), "home", "");

        java.util.List<TileKey> bulk = session.tilesWithAHome();

        assertEquals(new java.util.LinkedHashSet<>(bulk), session.homesForFindings(),
            "the squares the bulk clear would touch and the squares the findings call homes are not "
            + "the same set.  They are the same question asked twice, and when they drift the button "
            + "acts on track the diagram never warned about (WK3-C3).  Bulk: " + bulk);

        assertEquals(bulk.size(), 2,
            "expected exactly the two squares carrying a home - a blank home is not a home, and a "
            + "walk that counts it would agree with itself while being wrong: " + bulk);

        session.setPointProperty(two, "home", "Loc A");

        java.util.List<TileKey> elsewhere = session.homesElsewhereForTest(one, "Loc A");

        assertEquals(elsewhere, java.util.Arrays.asList(two),
            "the same locomotive is homed on two squares and the check that finds the OTHER one "
            + "answered: " + elsewhere);
    }
    /**
     * A facing that follows the track moves the locomotive on the running layout too (*"flipFacing should also update
     * the running layout."*).  A square is several Points once split, and the copy a locomotive stands on decides where
     * it can go next; `captureFromLayout` derives the facing from that copy and writes it back over the setup.
     * Asserted as both in one call, since either alone passes while the other is wrong.
     *
     * MUTATION: drop the `moveOntoFacingCopy` call and the layout half fails; drop the `setFacing` and the setup half
     * does.
     */
    @Test
    public void testTheFlipMovesTheLocomotiveOnTheLayoutToo() throws Exception
    {
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("Facing", null);
        session.getStore().setActiveConfiguration("Facing");

        final TileKey tile = new TileKey("main", 4, 1);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(tile);

        assertEquals(choices.size(), 2,
            "precondition: the square has to offer exactly two facings, or \"the other one\" means "
            + "nothing and flipFacing declines by design.  Offered: " + choices);

        session.setFacing(tile, choices.get(0));

        assertFalse(session.facingsFor(tile).isEmpty(),
            "precondition: the square has no copies in the station index, so there is nothing to "
            + "move a locomotive between");

        final TileKey moved = session.flipFacing("no such locomotive", null);

        assertNull(moved,
            "a locomotive that is not placed anywhere was followed, so the walk is matching on "
            + "something other than the name");

        // With no layout handed over, as a session with no running railway does.
        session.setPointProperty(tile, "loc",
            new org.json.JSONObject().put("name", "Facing Test Loc"));

        final TileKey flipped = session.flipFacing("Facing Test Loc", null);

        assertEquals(flipped, tile,
            "the placed locomotive was not followed at all");

        assertEquals(session.getFacing(tile), choices.get(1),
            "the setup was not turned round: the recorded facing is still " + session.getFacing(tile)
            + " and the square offers " + choices);

        // The call site hands the layout over, the half a session-only test cannot see: standing the whole window up
        // would need a model and a display, so the wiring is checked in the source.
        String source = SourceOf.declaration("private org.traincontrol.automationui.TileGraph.TileKey followTheTurn(")
            .replaceAll("\\s+", " ");

        assertTrue(source.contains("session.flipFacing(name, this.model.getAutoLayoutIfLoaded())"),
            "the window no longer hands the running layout to flipFacing, so the setup moves and the "
            + "layout does not - and captureFromLayout will write the layout's answer back over it "
            + "at the next editor open (DIR-B3)");

        String session_ = SourceOf.declaration("public TileKey flipFacing(").replaceAll("\\s+", " ");

        assertTrue(session_.contains("moveOntoFacingCopy(running, locomotive, tile, now);"),
            "flipFacing no longer moves the locomotive on the running layout, so the two records of "
            + "one fact are kept by different hands again (DIR-B3)");
    }
}
