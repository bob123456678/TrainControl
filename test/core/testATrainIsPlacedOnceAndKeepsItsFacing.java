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
 * A locomotive stands in one place, a home and an arrival side keep the way its train faces, and a station
 * demoted keeps its settings.
 *
 * One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136 Phase 3): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder
 * every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testATrainIsPlacedOnceAndKeepsItsFacing extends AutonomySessionFixture
{

    /**
     * A copy of a square nothing arrives at is no arrival, so a side barred while trains did arrive does not shut it
     * (RSA3-C6, its first half).
     *
     * Round 18 gave such a square one copy per way out, each standing for a train that faces it - and so named after the
     * side that train would have come in by.  The build then asked the store's barred sides about it: a side barred while
     * trains arrived by it, the track then made one-way away, shut one copy as no station - a bar the editor no longer
     * shows, and cannot clear.
     *
     * MUTATION: let a bar reach such a copy, and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testAStaleBarDoesNotShutASquareNothingArrivesAt() throws Exception
    {
        final TileKey middle = new TileKey("main", 3, 1);

        threeStationsInARow("C6a");

        session.rebuild();

        assertTrue(session.arrivalSides(middle).contains(org.traincontrol.automationui.TilePorts.Side.E), "precondition:"
            + " trains do not arrive at the middle square from the east");

        // BARRED FROM THE EAST while trains arrived by it; then the track made one-way away
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
     * A square nothing arrives at offers no home facing: no train can be brought home to either copy (RSA3-C6, its second half).
     *
     * *"we shouldn't allow an impossible facing to be saved"* (OB-282).  Its facings were offered, and a train homed
     * facing one way and standing there the other was one Return Home called IMPOSSIBLE - where before round 18 the home
     * was the square, whichever way the train stood.
     *
     * MUTATION: offer a home facing on such a square, or hold a home there to one, and this fails.
     *
     * @throws Exception from the build
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

        // AND ONE ALREADY SET is not held to
        session.placeLocomotive(middle, "C6 train");
        session.setFacing(middle, org.traincontrol.automationui.TilePorts.Side.E);
        session.setHome(middle, "C6 train");

        // A FACING SET BEFORE THIS BUILD, which the menu offered then
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
     * The same on a feedback double curve whose two arcs each lead out one way (RSA2-C7): each copy stands for a train
     * facing its way out, and so came in by the OTHER end of that arc - which here is no way out at all.
     *
     * On the straight of `testASquareNothingArrivesAtFacesItsPlacedTrain` the far end of each way out is the other way
     * out, so a copy given the side it leaves by instead of the side behind it builds the same two copies.  Here it
     * builds two copies with no way out, and the placed train is stranded.
     *
     * MUTATION: give each copy the side it leaves by, and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testADoubleCurveNothingArrivesAtFacesItsPlacedTrain() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 6, 5, null, null);

        // North (2,0) - up the N-W arc; east (4,2) - along the E-S arc.  Nothing on the W or S sides.
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

        // ONE-WAY AWAY FROM THE CURVE on both, whichever end each straight's route calls A
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

    /** Makes a straight's one route run only towards the given side. */
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
     * faces (RSA2-C7; Adam, 2026-09-29: *"Go with a."*).
     *
     * A square nothing arrives at was built whole - one Point with no facing - so a train placed on it by hand was
     * offered both ways out, and sent the way it does not face it drives off the other way over track nothing locked:
     * the runtime never commands a direction, and the copy IS the facing.  Split by the ways out, one copy for each, the
     * facing the train is placed with chooses its copy, as everywhere else.
     *
     * Three stations in a row, the track either side of the middle one made one-way away from it, so nothing arrives at
     * the middle square and track leaves it both ways.
     *
     * MUTATION: emit the square whole again, and this fails.
     *
     * @throws Exception from the build
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

        // ONE-WAY AWAY FROM THE MIDDLE on both sides (a straight's route is E-W: A = E, B = W)
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

                // THE PLACEMENT IS AN OBJECT, named inside
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

            // WHERE IT MAY GO: only the station the way it faces
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
     * A locomotive is pointing the same way after a save and a reopen as it was before.
     *
     * Adam, 2026-09-06, asking the question that should have been asked in the morning: **"do you have
     * tests for locomotive direction staying put between saves?"**  The answer was no - not one test in
     * the suite both persisted a setup and read a facing back, which is measurable and was measured.
     *
     * **That is the gap the whole day came out of.**  The reported symptom was a train's direction
     * changing by itself, and the mechanism turned out to be that a facing was never RECORDED at one
     * door, so `placementCopy` fell through to copy 0 and the next `captureFromLayout` wrote that
     * arbitrary side back as though somebody had chosen it.  Every part of that is a round trip, and
     * nothing was checking round trips.  Five fixes were attempted against a defect whose simplest
     * possible test did not exist.
     *
     * Both hops are checked, because they fail for different reasons.  The FILE hop is the store
     * writing and reading `facing`; the BUILD hop is `buildConfiguration` emitting it and
     * `captureFromLayout` reading it back, which is the one that silently rewrote the value.
     *
     * @throws IOException on a failure to save
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

        // A side the square can actually hold, so this is a direction and not a stray value.
        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(sensor);

        assertFalse(choices.isEmpty(), "the fixture square offers no direction at all to record");

        org.traincontrol.automationui.TilePorts.Side chosen = choices.get(0);

        session.setFacing(sensor, chosen);

        assertEquals(session.getFacing(sensor), chosen, "control: the facing did not even go in");

        session.save();

        // ACROSS THE FILE.
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
     * A home remembers which way its train was facing when it was set, and the build puts it on that copy (OB-282).
     *
     * Adam, 2026-09-23: *"yes, it should accomplish the facing"*, and asked when the facing is taken: *"Direction it is
     * facing when home is set."*  A copy is a facing, so the home goes on the copy the train was facing in, marked so
     * that Return Home brings it back to that copy rather than to any copy of the square - and it survives a build
     * and a capture.
     *
     * MUTATION: `writeHome` not recording the facing, or the build not marking the copy, fails this.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAHomeRemembersTheWayItsTrainWasFacing() throws Exception
    {
        session.open(Arrays.asList(throughStationPage()));
        session.initialize("Default");

        // THE MIDDLE SENSOR, which trains reach from either end - two copies, facing opposite ways.
        TileKey sensor = new TileKey("main", 3, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.placeLocomotive(sensor, "Test Loc");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices = session.facingChoices(sensor);

        assertTrue(choices.size() > 1, "precondition: the square holds one facing, so which one the home keeps is no"
            + " choice: " + choices);

        // THE SECOND facing, so a build that put the home on copy 0 as before cannot pass by accident.
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

        // AND BACK THROUGH A CAPTURE, which is what the running railway does whenever it is asked to remember.
        session.captureFromLayout(built);

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            chosen.name(), "a build and a capture changed the facing the home was set with");

        // AND A HOME SET ON THE RUNNING DIAGRAM, on the OTHER copy: the running layout holds it to that copy, and the
        // capture is what tells the setup.  Made here by moving the home in the built configuration, which is what the
        // running layout's own `toJSON` would write after `setHomeLocomotive` on that copy.
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
     * A home given to a train standing elsewhere takes the facing asked for - and never one no train can stand in
     * (OB-282).
     *
     * Adam, 2026-09-23: *"prompt the user for the direction"*, and *"we shouldn't allow an impossible facing to be
     * saved."*
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAHomeForATrainElsewhereTakesTheFacingAskedAndNoImpossibleOne() throws Exception
    {
        session.open(Arrays.asList(throughStationPage()));
        session.initialize("Default");

        // THE MIDDLE SENSOR, which trains reach from either end - two copies, facing opposite ways.
        TileKey sensor = new TileKey("main", 3, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        java.util.Set<org.traincontrol.automationui.TilePorts.Side> canHold = session.homeFacingsFor(sensor);

        assertTrue(canHold.size() > 1, "precondition: the square offers one facing to be homed in: " + canHold);

        org.traincontrol.automationui.TilePorts.Side asked = new java.util.ArrayList<>(canHold).get(canHold.size() - 1);

        session.setHome(sensor, "Elsewhere Loc", asked);

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            asked.name(), "the facing asked for a home was not kept");

        // A FACING NO COPY TRAINS MAY ARRIVE AT HOLDS: on a straight east-west run, north.
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
     * And across a build and a capture, which is the hop that actually rewrote it.
     *
     * `captureFromLayout` takes the running layout back into the setup, and a train standing on a copy
     * is what tells it which way that train faces.  So a value that goes out through
     * `buildConfiguration` and comes back changed is a direction nobody chose - and until today,
     * nothing asked whether it came back the same.
     *
     * @throws Exception on a failure to build
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

        // Out through the build and back through the capture, which is the round trip the running
        // railway makes every time it is asked to remember where its trains are.
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
     * Taking a train off a square takes its arrival side with it (IND9-A1).
     *
     * `arrivedFrom` describes the TRAIN that was standing here, not the square - the same thing the
     * comment beside the facing in `placeLocomotive` says about the facing, three lines from where this
     * was missed. Both doors that empty a square cleared `loc` and the facing and left the arrival side
     * behind, and `parseAuto` reads it straight back onto whatever is placed there next.
     *
     * **What that does on the railway.** The stale side is where the PREVIOUS train’s tail lay, so the
     * walk that blocks track behind a standing train follows it for the new one: track behind train B
     * is greyed and refused on the strength of where train A came in. It is silent - nothing reports a
     * side it did not ask about - and it survives a save.
     *
     * It is also unreachable from the menu, which now offers only the four sides and no way to say "not
     * known" (Adam, 2026-09-07). Removing that option was right and it makes this defect permanent
     * until the root cause is fixed, which is here: the side leaves with the train.
     *
     * Both doors are asserted. `clearEveryPlacement` is a separate loop that mirrors this one, and the
     * two have drifted before - it is the reason `placeLocomotive` carries a comment telling the reader
     * they must agree.
     *
     * @throws IOException on a failure to save
     */
    @Test
    public void testAnArrivalSideDoesNotOutliveItsTrain() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        // TRAIN A ARRIVES FROM THE WEST.
        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "W");

        assertEquals(session.getArrivedFrom(sensor), "W", "control: the side did not go in");

        // AND IS TAKEN OFF AGAIN - a cut, or a right-click clear.
        session.placeLocomotive(sensor, null);

        assertNull(session.getArrivedFrom(sensor),
            "the square still says a train arrived from the west after the train was taken off it."
            + " The next locomotive placed here inherits that tail, and the track behind it is"
            + " blocked on the strength of where a different train came in (IND9-A1)");

        // AND THE OTHER DOOR, which is a separate loop over every square.
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
     * A NEW occupant does not inherit the old one's tail (VAL9-B5).
     *
     * The third branch of the same rule, and the one nothing exercised. Emptying a square is covered
     * above; handing it straight from one train to another is the case where the stale value is least
     * visible and most wrong, because the square is never empty in between and nothing looks wrong on
     * the diagram.
     *
     * **Both assignment doors now depend on this branch**, since neither asks the arrival side:
     * `GraphLocAssign.commitAndRecord` places through here and relies on the clear happening. So the
     * one path with no test was the one two doors were built on.
     *
     * `Point.setLocomotive` does exactly this on the running layout, for the same reason - the train
     * arriving did not arrive the way the train leaving did.
     *
     * The same locomotive re-placed is asserted first, because it is NOT a change of occupant and a
     * clear there would throw away a side that is still true - doors write a placement back unchanged
     * all the time.
     *
     * MUTATION: dropping the occupant-change clear in `placeLocomotive` fails the last assertion;
     * clearing unconditionally fails the middle one.
     *
     * @throws IOException on a failure to build
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
     * The arrival side reaches the running layout, and comes back from a save.
     *
     * `VAL8-A2` reported that `arrivedFrom` never crosses between the setup and the railway - that the
     * builder does not emit it, so an answer the operator gives blocks nothing.  It is not in the
     * builder's skip list and not in DERIVED, so by reading it should pass through as an extra; that
     * is exactly the kind of reading that has been wrong twice this week, so it is measured here
     * instead.
     *
     * Both hops, because they fail for different reasons: the FILE hop is the store, and the BUILD hop
     * is what `parseAuto` is handed.  A property that survives a save and never reaches the railway is
     * a setting that does nothing, which is worse than one that is not offered.
     *
     * @throws IOException on a failure to save
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

        // THE BUILD, which is what the railway is made from.
        String built = session.buildConfiguration();

        assertTrue(built.contains("arrivedFrom"),
            "the built configuration carries no arrival side, so an answer the operator gives never"
            + " reaches the railway and blocks nothing (VAL8-A2)");

        assertTrue(built.contains("\"arrivedFrom\": \"W\"") || built.contains("\"arrivedFrom\":\"W\""),
            "the arrival side is emitted but not as the side that was recorded: " 
            + built.substring(Math.max(0, built.indexOf("arrivedFrom") - 40),
                Math.min(built.length(), built.indexOf("arrivedFrom") + 40)));

        // AND THE FILE.
        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(runOfTrack()));

        assertEquals(reopened.getArrivedFrom(sensor), "W",
            "the arrival side did not survive a save and reload, so the track a train is lying across"
            + " is forgotten every time the setup is loaded");
    }

    /**
     * A direction change never flips the facing of a square the train has LEFT (REG7-A2).
     *
     * **The mechanism, which is sharper than "it might double-follow".**  `flipFacing` builds a list
     * of candidate squares: the ones the running layout says hold this train, then the ones the SETUP
     * says do.  The loop skips any square it cannot decide - a square with no recorded facing is a
     * `continue`, not a stop.
     *
     * After a run the arrival square frequently has no recorded facing yet, so the loop moved past it
     * and reached the next candidate: the square the train set off from, which the setup still names
     * until the next `captureFromLayout` and which does have a facing. That platform - now empty -
     * had its direction flipped for a train that is not standing on it, and the log said a direction
     * had been followed.
     *
     * Not an exotic interleaving: it is the ordinary state of things between a run ending and the
     * editor next being opened.
     *
     * The rule now is that the setup only gets a vote when the railway has no opinion. `DIR-C3` is
     * untouched - its case is a locomotive recorded on two SETUP squares, which is still walked in
     * full when the running layout does not know.
     *
     * @throws IOException on a failure to save
     */
    @Test
    public void testADirectionChangeDoesNotFlipTheSquareTheTrainLeft() throws IOException
    {
        // The same fixture testTheFlipMovesTheLocomotiveOnTheLayoutToo uses: flipFacing declines any
        // square that does not offer exactly two facings, and runOfTrack offers none.
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

        // A RAILWAY THAT KNOWS NOTHING still lets the setup answer - DIR-C3, and the half of the old
        // behaviour that must survive.  A train recorded in the setup and not yet running is the
        // ordinary state before anything has moved.
        org.traincontrol.automation.Layout knowsNothing =
            new org.traincontrol.automation.Layout(null);

        session.flipFacing("Test Loc", knowsNothing);

        assertEquals(session.getFacing(departed), choices.get(1),
            "a railway with no opinion about this train stopped the setup answering at all, which is"
            + " DIR-C3 undone: a locomotive recorded on a square it has not left still gets a follow");

        // AND THE FIX ITSELF, which needs a railway that DOES know the train - a state this fixture
        // cannot build, because the candidate has to come back from the station index.  Asserted as
        // the rule instead: setup squares are gathered ONLY when the running layout offered none.
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
     * A station whose track runs out on one side - at a buffer stop, or where the drawn track simply stops - before it
     * reaches another sensor is drawn as a terminus flat against that side (FR-118; Adam, 2026-10-09, asked *"a station
     * with only one way out becomes a terminus, correct?"* - confirmed).  The diagram's badge and the editor's carry the
     * side; a station with track going on both ways carries none, and a setting that stops trains leaving one way - a
     * one-way run - is no dead end: the track is what is asked.
     *
     * MUTATION: answer from the built edges, which a one-way run takes away, or drop the side from either badge, and this
     * fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAStationWhoseTrackRunsOutIsATerminus() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 12, 3, null, null);

        // a buffer stop, its track to the east; a station against it; a station in the middle; one where the track stops
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

        // A SETTING IS NOT A DEAD END: the track east of the middle station made one-way towards it, so no train leaves
        // it eastwards - its track still goes on
        session.setDirection(new TileKey("main", 6, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.setDirection(new TileKey("main", 7, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.rebuild();

        assertNull(session.deadEndSide(through), "a station trains cannot leave eastwards only because of a one-way run"
            + " is drawn as a terminus - Adam asked about a station with one way out of the track");

        assertEquals(session.deadEndSide(buffer), Side.W, "control: the buffer stop's station lost its dead end");
    }

    /**
     * A placement naming a locomotive this database does not have is refused, not written in.
     *
     * The running model used to invalidate the WHOLE layout for one such name, so an old graph naming a
     * locomotive since renamed or deleted imported cleanly and then produced a setup that refused to
     * open, reported as a locomotive problem with nothing to say the import put it there.  Since
     * AMR-C3 (Adam, 2026-09-16: *"drop the train and keep the rest."*) that loader drops the placement
     * with a log line too - `core.testHomeStaging
     * .testAPlacementForALocomotiveNotInTheDatabaseDropsOnlyThePlacement` - so the two doors now agree
     * about the same file, which is why this test is about the importer NAMING what it dropped.
     *
     * Refused here and named instead, which is a thing the user can act on.
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
     * A locomotive named at two points is placed once, and the second is reported.
     *
     * One locomotive cannot stand in two places, and the running model says so by invalidating the
     * layout rather than by ignoring the second.  An old graph that has drifted names the same
     * locomotive twice easily enough.
     */
    @Test
    public void testALocomotiveNamedTwiceIsPlacedOnce() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONArray points = new org.json.JSONArray();

        // pageOnDisk draws feedback 11 at 1,1 and feedback 12 at 4,1
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
     * Without a database to check against, placements are taken as given.
     *
     * The check is the caller's to supply - the session has no locomotive database of its own - and
     * passing nothing must not mean refusing everything.
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
     * A home authored on a split square is emitted onto exactly ONE copy.
     *
     * The running model hangs a home on a Point and refuses to let two Points claim one locomotive, so
     * emitting it on every copy meant rebuildHomeStations stripped all but the first at every load -
     * logging a "assigned twice, check your hand-edited file" warning per copy, for a file no hand had
     * edited - and the home came to rest on whichever copy parsed first: an arrival side chosen by enum
     * order, meaning nothing to whoever authored it.  The locomotive placement had already been given
     * this treatment; the home had not.
     *
     * Needs a THROUGH station: a square with one way in cannot be split, so the fault does not arise on
     * the straight line the other tests use.
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

        // "trains may turn round here" - what a berth or a stub platform gets, and what gives the
        // square a turning copy of every arrival on top of its plain ones
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

            // the builder writes "block" only where a square became more than one Point, so this is
            // also the precondition: without it the test would pass for want of a split
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
     * A locomotive is recorded standing in one place, never two.
     *
     * The running layout enforces that when a train is moved - it leaves where it was - but the
     * CONFIGURATION was never told, so a locomotive placed by hand on one square kept its old
     * placement on another.  Nothing looked wrong until the next build, which emitted the same
     * locomotive at two Points; fromJSON answers that by invalidating the whole layout, and from then
     * on every path was refused as "configuration is invalid" - from a placement made minutes before.
     *
     * Found in a real exported graph, with 065 001-0 DB standing at both BottomMainA and BottomMainC.
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

        // Its settings, which have to travel with it
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
     * Taking a locomotive off forgets it, and forgets which way it was pointing.
     *
     * The configuration is what the next build reads, so a placement left behind puts the train back.
     * The facing goes with it because it belonged to that train, not to the square - otherwise the
     * next locomotive placed there inherits the last one's direction without being asked.
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
     * A station takes trains from anywhere until somebody says otherwise.
     *
     * The default has to be free rather than shut, and it has to cost nothing to store: a setup nobody
     * has restricted should carry no restriction at all, so that track added to the diagram later
     * arrives open instead of arriving barred by a setting nobody ever opened.
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

        // AND A RESTRICTED ONE DRAWS THEM (FR-136), which is what the empty answer above is told apart from
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
     * Barring a side stops trains stopping there, and nothing else.
     *
     * The restriction lands on the copy for that arrival side: it stops being a station, so autonomy
     * cannot send a train to it.  The copy itself stays, and so does every edge through it, because
     * running THROUGH a square is a different question - the one the direction arrows answer.
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
     * Barring a side of a station where trains turn round still loads.
     *
     * The two flags are emitted from different places.  "station" is per copy, because that is where an
     * arrival restriction lands; "terminus" was still read off the SQUARE - so the reverse copy of a
     * barred side came out as a terminus that is not a destination.
     *
     * Point.setTerminus refuses exactly that pair, and parseAuto answers a refusal by invalidating the
     * WHOLE layout - naming a Point copy nothing on the diagram carries.  So restricting a terminus
     * platform, which is the most natural use this setting has, would have made the entire setup
     * unloadable and said nothing a user could act on.
     */
    @Test
    public void testBarringASideOfATurnAroundStationStillLoads() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        // A configuration has to exist for the per-point flags to be stored in
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

    /**
     * Lifting a restriction leaves nothing behind.
     */
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
     * A restriction naming a side the square no longer has is ignored, and then forgotten.
     *
     * The diagram moves under the setup: a tile replaced, an approach re-plumbed, and the square now
     * arrives from somewhere else.  The stale side is already dead in the build - there is no copy for
     * it - but it was still COUNTED, and the count is what decides whether the menu will let another
     * side be shut.  A station could end up with every box ticked, every box disabled, and nothing on
     * screen to say why.
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

        // The side facing the track that is about to be taken up, so the restriction is the one that
        // goes stale.  Barring the other would leave a live restriction, which is a different test.
        org.traincontrol.automationui.TilePorts.Side doomed =
            org.traincontrol.automationui.TileGraph.gridSideTowards(station, new TileKey("main", 2, 1));

        assertTrue(both.contains(doomed), "precondition: trains arrive from that side today");

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(Arrays.asList(doomed)));

        session.save();

        // The track on one side is taken up, so the station is now reached from one end only
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
     * Excluding a page does not destroy the arrival restrictions on it.
     *
     * The graph leaves excluded pages out by construction, so a square on one has no arrival sides at
     * all - and a save that pruned restrictions against the live sides read that as "every way in has
     * gone" and deleted the setting outright.  Re-including the page gave nothing back, and nothing
     * reported the loss, because it happened before the reconciliation that would have reported it.
     *
     * The same rule the whole reconciliation is built around: excluding a page must be reversible.
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
     * A station demoted and promoted again has every setting it had as a station (GSE-C4).
     *
     * Adam, 2026-10-10, asked: keep them, ignored while not a station - as its maximum train length (OB-291) and its
     * blockers (AMS-B2) already were.  Demoting cleared five, with no warning, from the editor and from the track
     * diagram's own menu: the caption, the sides it bars arrivals by, its exit guard, its entry guards, and the turn flag
     * older setups spell "terminus".  This claim replaces one that said it forgot its arrival restriction - the rule his
     * ruling reverses.
     *
     * Through the editor's own answer to the station menu, `setUsage`, because the turn flag was cleared there and the
     * other four in the session.  The caption sits two squares below the platform, where the placer never puts one, so a
     * caption placed afresh on promotion cannot pass for the one kept.
     *
     * MUTATION: clear any of the five on demotion again, and this fails naming it.
     *
     * @throws Exception from the panel
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
     * A double-curve station whose track runs out on the contact's curve is a terminus facing that way (RSA63-C2): round 124
     * put its badge on the contact's curve, and `deadEndSide` still asked the square's first road, the other curve - so a
     * double curve was never drawn as a terminus.  Every orientation, the side past the contact's curve's second end left
     * with no track: the badge's dead end is that side.
     *
     * MUTATION: ask the first road again, and this fails.
     *
     * @throws Exception from the build
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
     * A two-ended station with a signal on each side of it, for every setting a station has and a plain point has not
     * (GSE-C4): the station at 3,1, entered from the west and from the east, and signals at 2,1 (address 21) and 4,1
     * (address 22) on the track either side, wired as parsing a real layout wires them.
     *
     * @return the page, its file written
     * @throws IOException if the page file cannot be written
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
     * Opens that page, makes 3,1 a station and gives it every setting a plain point does not have (GSE-C4): a caption
     * two squares below it at 3,3, its first arrival side barred, the signal at 2,1 as its exit guard, the one at 4,1 as
     * its entry guard, and the turn flag older setups spell "terminus".  Each is asserted as set before anything is
     * demoted.
     *
     * @return an editor over the page, to answer the station menu through
     * @throws Exception from the session or the panel
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
        // NAMED, as a station made one in the editor is now (FR-120) - so promoting it again asks nothing
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
     * A square that stops being a Point keeps its locomotive.
     *
     * A sensor reduces to a Point only where track connects it to something, so nudging a station one
     * square - far enough that it no longer joins the run either side of it - leaves a square that is
     * perfectly present on the diagram and is not a Point.  The capture judged its prune on Points and
     * deleted everything about that square: the locomotive standing there, its facing, its markings.
     *
     * Adam found it by moving a tile and watching the train disappear, and asked for the opposite:
     * keep the placement, let the build refuse it, and have it come back when the track is joined up
     * again.  A disconnected station is a mistake somebody is in the middle of making, not an
     * instruction to forget the train.
     *
     * A square whose TILE is gone is a different thing and still keeps nothing - see the point named
     * "a point whose track was deleted" in the capture test above.
     */
    @Test
    public void testASquareThatStopsBeingAPointKeepsItsLocomotive() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));
        session.initialize("Default");

        // 1,1 and 4,1 are the sensors, and reduce to Points.  2,1 is a straight: a square that is
        // certainly ON the page and just as certainly not a Point, which is the state a sensor lands in
        // when somebody moves it out of the run it was part of.
        TileKey adrift = new TileKey("main", 2, 1);

        session.setStation(new TileKey("main", 1, 1), true);

        // Put a train on the square that is not a Point, the way a capture would after somebody moved
        // the tile out of the run
        org.json.JSONObject running = new org.json.JSONObject();

        running.put("minDelay", 1);
        running.put("maxDelay", 2);
        running.put("edges", new org.json.JSONArray());

        org.json.JSONArray points = new org.json.JSONArray();

        running.put("points", points);

        session.captureFromLayout(running.toString());

        org.json.JSONObject config = session.getStore().getConfiguration("Default");

        // Written straight into the configuration, which is what a placement on a square that has no
        // Point looks like: nothing in the running layout can speak for it
        org.json.JSONObject standing = new org.json.JSONObject();

        standing.put("loc", new org.json.JSONObject().put("name", "BR 218"));

        config.getJSONObject("points").put(adrift.toString(), standing);

        // Autonomy runs, and reports what it found
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
     * A demoted station's settings are kept and IGNORED while it is not a station: nothing builds, checks or draws them
     * (GSE-C4).
     *
     * Each was cleared on demotion for a reason that still holds of a plain point: a caption is drawn, and lights a
     * station's name up when a train reverses there; a guard is thrown red over a square nothing is sent to; the old turn
     * flag turns every train round.  They are kept now, at Adam's ruling of 2026-10-10 ("keep them, ignored while not a
     * station"), so the readers ask whether the square is a station: the caption the diagram draws by, the labelled
     * stations the checks count, the barred sides, the guards and the accessory names the build is given, whether trains
     * turn there - and the built railway itself.  This claim replaces one that said demoting took the caption away.
     *
     * MUTATION: hand the build every square's exit guards again, station or not, and this fails.
     *
     * @throws Exception from the panel or the build
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

        // KEPT, or there is nothing to ignore
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

        // AND IGNORED
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
     * A square that stops being a station keeps whatever train was standing on it.
     *
     * The designation and the placement are separate records and demotion only touches the first, so
     * this state is reachable by an ordinary gesture - switch a station to pass-through - as well as by
     * importing a setup that placed a train where this build draws no station.  It is why the editor
     * menu offers "remove" against the LOCOMOTIVE rather than against the designation: gated on being a
     * station, the only way to take this train off was to make the square a station again first.
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

        // And taking it off works here exactly as it does at a station - nothing about clearing a
        // placement ever needed the square to be one.
        session.placeLocomotive(tile, null);

        assertNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE),
            "a train could be left on a square with no way to take it off");
    }

    /**
     * A locomotive recorded in two places is reported before anything tries to run.
     *
     * The consequence is out of all proportion to the cause: fromJSON refuses the WHOLE layout for a
     * locomotive in two places, and every path afterwards is answered with "configuration is invalid
     * and must be reloaded" - which names neither the locomotive nor the square, and points at nothing
     * the reader did.  It happened on a real setup and took an exported graph to find.
     *
     * So it is a check, on the square that can be cleared to fix it.  There is no validate command to
     * run, and this is the list somebody reads before starting.
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

        // Written straight into the configuration, which is how it happens: one placement from an
        // import and one made by hand, neither aware of the other
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

            // The subject carries the locomotive even though the editor shows the square instead:
            // a finding with a tile is described by its tile, which is what gives it somewhere to
            // jump to.  Anything that wants the name can still have it.
            assertEquals(finding.getSubject(), "065 001-0 DB",
                "the finding no longer carries which locomotive is doubled up");

            assertNotNull(finding.getTile(),
                "without a square there is nothing for the reader to jump to and clear");
        }

        assertTrue(reported, "a locomotive standing in two places was not reported at all");
    }

    /**
     * And one locomotive in one place is not reported.
     *
     * The precondition that keeps the test above honest: a check that fired on every placement would
     * satisfy it and make the list useless.
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

    /**
     * And placing it somewhere new takes it off where it was, so the check never fires from a move.
     */
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
     * The bulk clear touches exactly the squares the findings call homes (WK3-C3).
     *
     * **Four walks of one map, and nothing made them agree.** `homeTiles()` fed the findings the
     * diagram shows; `tilesWithAHome()` decided what the Clear All Home Locomotives button touches;
     * `homesElsewhere()` added one filter; `shutTiles()` made a fourth. Each carried its own copy of
     * "is there an active configuration, does it have points, is this an object, parse the key".
     *
     * The cost of drift is specific: if either of the first two gains a qualification the other has
     * not - homes on excluded pages, squares the diagram no longer draws - the button acts on a set
     * the findings never mentioned, and the operator finds out afterwards.
     *
     * They are one walk now. This is what says so, because a shared helper that two callers stop
     * using is the same defect with an extra step.
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

        // A THIRD SQUARE WITH NO HOME, so "every square" would fail this rather than pass it.
        session.setPointProperty(new TileKey("main", 4, 1), "home", "");

        java.util.List<TileKey> bulk = session.tilesWithAHome();

        assertEquals(new java.util.LinkedHashSet<>(bulk), session.homesForFindings(),
            "the squares the bulk clear would touch and the squares the findings call homes are not "
            + "the same set.  They are the same question asked twice, and when they drift the button "
            + "acts on track the diagram never warned about (WK3-C3).  Bulk: " + bulk);

        assertEquals(bulk.size(), 2,
            "expected exactly the two squares carrying a home - a blank home is not a home, and a "
            + "walk that counts it would agree with itself while being wrong: " + bulk);

        // AND THE FILTERED ONE comes from the same walk: asked about `one`, it must name `two` and
        // not `one` itself.
        session.setPointProperty(two, "home", "Loc A");

        java.util.List<TileKey> elsewhere = session.homesElsewhereForTest(one, "Loc A");

        assertEquals(elsewhere, java.util.Arrays.asList(two),
            "the same locomotive is homed on two squares and the check that finds the OTHER one "
            + "answered: " + elsewhere);
    }
    /**
     * A facing that follows the track moves the locomotive on the running layout too (DIR-B3).
     *
     * Adam, 2026-09-06: **"flipFacing should also update the running layout."**
     *
     * **Two records of one fact, and they were kept by different hands.** A square is several Points
     * once it is split, and the copy a locomotive stands on is what decides where it can go next.
     * Writing only the setup flipped the diagram's arrow at once while `getPossiblePaths`, the
     * right-click destination list and `explainDestinations` all went on answering for the OLD facing.
     *
     * And it did not merely lag: `captureFromLayout` derives the facing from the copy the locomotive is
     * actually on and writes it back over the setup. Opening an editor was enough to undo the flip,
     * silently.
     *
     * **Asserted as both, in one call.** Either alone passes while the other is wrong, which is the
     * state the ruling exists to end.
     *
     * MUTATION: drop the `moveOntoFacingCopy` call and the layout half fails; drop the `setFacing`
     * and the setup half does.
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

        // The running layout, built from this very setup, so the copies it carries are the ones
        // facingsFor names.
        assertFalse(session.facingsFor(tile).isEmpty(),
            "precondition: the square has no copies in the station index, so there is nothing to "
            + "move a locomotive between");

        final TileKey moved = session.flipFacing("no such locomotive", null);

        assertNull(moved,
            "a locomotive that is not placed anywhere was followed, so the walk is matching on "
            + "something other than the name");

        // THE SETUP HALF, with no layout handed over - which must still work, because that is what a
        // session with no running railway does.
        session.setPointProperty(tile, "loc",
            new org.json.JSONObject().put("name", "Facing Test Loc"));

        final TileKey flipped = session.flipFacing("Facing Test Loc", null);

        assertEquals(flipped, tile,
            "the placed locomotive was not followed at all");

        assertEquals(session.getFacing(tile), choices.get(1),
            "the setup was not turned round: the recorded facing is still " + session.getFacing(tile)
            + " and the square offers " + choices);

        // AND THE CALL SITE HANDS THE LAYOUT OVER, which is the half a session-only test cannot see.
        //
        // Standing the whole window up to watch a Point change hands needs a model and a display; the
        // wiring is what was missing, and the wiring is what this checks.
        String source = SourceOf.declaration("private org.traincontrol.automationui.TileGraph.TileKey followTheTurn(")
            .replaceAll("\\s+", " ");

        // Asked without building one since RLV11-C5: the follow is posted, and can land after Unload
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
