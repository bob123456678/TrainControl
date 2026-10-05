package core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * Two permanent Y turnouts drawn toe to toe are one crossing (OB-320; Adam, 2026-10-04: *"Lets support two permanent Ys
 * as a crossing, if possible, same as the current crossing by effectively rotated 45 degrees, just connecting differently
 * and spread across 2 tiles."*).
 *
 * Each claim builds a small railway round the Ys and reads what the reduction makes of it: the edges a train may run
 * between the sensors, which is what autonomy plans with.  The vertical pair is the shape on Adam's TC3Sandbox layout at
 * 12,9 and 12,10 (*"this should connect 11,8 with 13,10, and 13,9 with 11,10"*), the horizontal pair the shape at 14,12
 * and 15,12.  A sensor stands at each end, so an edge is a track and a missing one a track no train can run.
 *
 * And a permanent Y that is not half of such a pair is the turnout with no address it always was (Adam: *"there won't
 * always be two adjacent Y to form a logical crossing.  sometimes it could just be one perma Y, so account for this.  Be
 * careful with edge cases."*).
 *
 * Two permanent lefts or two permanent rights toe to toe, turned half round from each other, are a crossing too - the
 * straight legs a straight track through both toes, the diverging legs the diagonal (Adam, of the two rights at 15,13
 * and 16,13 on TC3Sandbox_layout: *"Treat those two adjacent perma switches at 15,13 and 16,13 as one logical crossing.
 * Keep the existing rules for perma switches, i.e. those without another adjacent to form a crossing are treated as
 * switches only crossable in one direction by autonomy."*).
 *
 * @author Adam
 */
public class testTwoYsMakeACrossing
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-two-ys").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * The vertical pair carries the crossing's two tracks, each corner to corner and both ways, and nothing else: no
     * reverse curve from one track onto the other.
     *
     * MUTATION: leave the Ys trailable only, or let a train that came along one track leave along the other, and this
     * fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheVerticalPairCarriesBothTracksBothWays() throws Exception
    {
        open(verticalPair(), "OB-320 vertical");

        assertEquals(session.getGraph().crossingPartner(key(5, 5)), key(5, 6), "the Y at 5,5 is not half of a crossing");
        assertEquals(session.getGraph().crossingPartner(key(5, 6)), key(5, 5), "the Y at 5,6 is not half of a crossing");

        assertEquals(edges(), setOf("4,5>6,6", "6,6>4,5", "6,5>4,6", "4,6>6,5"), "two Ys drawn toe to toe do not carry"
            + " the crossing's two tracks, corner to corner both ways, and only them (OB-320)");
    }

    /**
     * The horizontal pair does the same, on its own sides.
     *
     * MUTATION: as above.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheHorizontalPairCarriesBothTracksBothWays() throws Exception
    {
        LayoutDiagram page = page();

        y(page, 5, 5, 1);
        y(page, 6, 5, 3);

        sensor(page, 5, 4, 1, 1);
        sensor(page, 5, 6, 1, 2);
        sensor(page, 6, 4, 1, 3);
        sensor(page, 6, 6, 1, 4);

        open(page, "OB-320 horizontal");

        assertEquals(session.getGraph().crossingPartner(key(5, 5)), key(6, 5), "the Y at 5,5 is not half of a crossing");

        assertEquals(edges(), setOf("5,4>6,6", "6,6>5,4", "5,6>6,4", "6,4>5,6"), "two Ys drawn toe to toe across the"
            + " page do not carry the crossing's two tracks, corner to corner both ways, and only them (OB-320)");
    }

    /**
     * A permanent Y on its own is still trailed only, into its toe, keeps its warning, and is not a crossing.
     *
     * MUTATION: open every permanent Y both ways, or drop the warning for all of them, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAPermanentYAloneIsStillOnlyTrailed() throws Exception
    {
        LayoutDiagram page = page();

        y(page, 5, 5, 0);

        sensor(page, 4, 5, 0, 1);
        sensor(page, 6, 5, 0, 2);
        sensor(page, 5, 6, 1, 3);

        open(page, "OB-320 alone");

        assertNull(session.getGraph().crossingPartner(key(5, 5)), "a Y on its own is half of a crossing");

        assertEquals(edges(), setOf("4,5>5,6", "6,5>5,6"), "a permanent Y on its own is not trailed into its toe, and"
            + " only that");

        for (Route route : session.getGraph().getRoutes(key(5, 5)).values())
        {
            assertNotNull(route.getDirectedToward(), "a permanent Y on its own has a road the blades do not restrict: "
                + route);
        }

        assertTrue(warnedAbout(key(5, 5)), "a permanent Y on its own is not reported as one");
    }

    /**
     * A pair's squares are not reported as turnouts with no address, which may only be trailed: they are a crossing.
     *
     * MUTATION: keep the warning for a pair, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAPairIsNotWarnedAboutAsTurnouts() throws Exception
    {
        open(verticalPair(), "OB-320 warning");

        assertFalse(warnedAbout(key(5, 5)) || warnedAbout(key(5, 6)), "a crossing's square is reported as a turnout"
            + " trains may only trail: " + session.getGraph().getProblems());
    }

    /**
     * A Y whose toe meets another Y's leg is no crossing: both are turnouts with no address, and a train trails through
     * one into the other's leg and on into its toe, as before.
     *
     * MUTATION: pair a toe with a leg, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAYWhoseToeMeetsALegIsNoCrossing() throws Exception
    {
        LayoutDiagram page = page();

        y(page, 5, 5, 0);
        y(page, 5, 6, 1);

        sensor(page, 4, 5, 0, 1);
        sensor(page, 6, 5, 0, 2);
        sensor(page, 6, 6, 0, 3);
        sensor(page, 5, 7, 1, 4);

        open(page, "OB-320 toe to leg");

        assertNull(session.getGraph().crossingPartner(key(5, 5)), "a Y whose toe meets a leg is half of a crossing");
        assertNull(session.getGraph().crossingPartner(key(5, 6)), "a Y whose leg meets a toe is half of a crossing");

        assertEquals(edges(), setOf("4,5>6,6", "6,5>6,6", "5,7>6,6"), "two Ys toe to leg are not trailed through as the"
            + " turnouts they are");
    }

    /**
     * Ys side by side, back to back, or toe to toe with an addressed Y switch, are no crossing.
     *
     * MUTATION: pair any two Ys that are neighbours, or a permanent Y with a switch, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testOnlyTwoPermanentYsToeToToeAreACrossing() throws Exception
    {
        LayoutDiagram page = page();

        // side by side, both toes south
        y(page, 2, 2, 0);
        y(page, 3, 2, 0);

        // back to back: toes north and south, away from each other
        y(page, 8, 2, 2);
        y(page, 8, 3, 0);

        // toe to toe with a Y switch that has an address
        y(page, 5, 7, 0);
        page.addComponent(componentType.SWITCH_Y, 5, 8, 2, 0, 5, 5, accessoryDecoderType.MM2, null);

        open(page, "OB-320 not pairs");

        for (TileKey square : Arrays.asList(key(2, 2), key(3, 2), key(8, 2), key(8, 3), key(5, 7), key(5, 8)))
        {
            assertNull(session.getGraph().crossingPartner(square), square + " is half of a crossing");
        }
    }

    /**
     * A run that crosses on one track and comes back over the other is not going in circles (OB-320).  The two tracks
     * share the side the squares meet at, so a walk remembered by the square and side alone stopped there the second time
     * - or, told nothing of the track, took the short reverse curve instead.
     *
     * MUTATION: remember the walk without the track, or let it change track at the toes, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testACrossingCrossedTwiceInOneRunIsNotACircle() throws Exception
    {
        LayoutDiagram page = page();

        y(page, 5, 5, 0);
        y(page, 5, 6, 2);

        // a loop from the lower Y's east leg up to the upper Y's east leg
        page.addComponent(componentType.CURVE, 6, 6, 2, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CURVE, 6, 5, 3, 0, 0, 0, accessoryDecoderType.MM2, null);

        sensor(page, 4, 5, 0, 1);
        sensor(page, 4, 6, 0, 2);

        open(page, "OB-320 figure of eight");

        assertEquals(edges(), setOf("4,5>4,6", "4,6>4,5"), "a run over both tracks of one crossing is not an edge both"
            + " ways (OB-320)");

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            if (!at(edge.getStart()).equals("4,5")) continue;

            List<String> squares = new ArrayList<>();

            for (GraphReducer.TileStep step : edge.getPath()) squares.add(at(step.getTile()));

            assertEquals(squares, Arrays.asList("5,5", "5,6", "6,6", "6,5", "5,5", "5,6"), "the run from 4,5 does not"
                + " cross on one track, go round the loop and cross back on the other");
        }
    }

    /**
     * A direction set on one square's half of a track is set on the other's, the same way along the track; the other
     * track is left as it was.
     *
     * MUTATION: set only the square it was set on, or the other square the other way, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testADirectionIsOneTrackOnBothSquares() throws Exception
    {
        open(verticalPair(), "OB-320 direction");

        TileKey upper = key(5, 5);
        TileKey lower = key(5, 6);

        RouteId fromTheWest = roadTouching(upper, Side.W);
        Route road = session.getGraph().getRoutes(upper).get(fromTheWest);

        // ONTO THE OTHER SQUARE: the north-west to south-east track, that way only
        session.setDirection(upper, fromTheWest, road.getA() == Side.S ? Direction.TOWARD_A : Direction.TOWARD_B);

        RouteId toTheEast = roadTouching(lower, Side.E);
        Route lowerRoad = session.getGraph().getRoutes(lower).get(toTheEast);

        assertEquals(session.getGraph().getDirection(lower, toTheEast), lowerRoad.getA() == Side.E ? Direction.TOWARD_A
            : Direction.TOWARD_B, "the other square's half of the track does not run the same way (OB-320)");

        assertEquals(edges(), setOf("4,5>6,6", "6,5>4,6", "4,6>6,5"), "one track made one-way does not run one way over"
            + " the crossing, with the other track both ways");

        // AND THE OTHER TRACK CLOSED FROM THE OTHER SQUARE
        session.setDirection(lower, roadTouching(lower, Side.W), Direction.NONE);

        assertEquals(session.getGraph().getDirection(upper, roadTouching(upper, Side.E)), Direction.NONE, "a track"
            + " closed on one square is open on the other");

        assertEquals(edges(), setOf("4,5>6,6"), "a track closed on one square of the crossing is still run");
    }

    /**
     * Directions set on the squares of a crossing while they were permanent turnouts are read as a turnout's, the same on
     * both halves of the track (RSA37-C1): towards the toe, the way the blades ran, restricted nothing, so the track runs
     * both ways; towards a leg was how a permanent turnout was closed, so the track is closed.  Read in the crossing's
     * terms on the one square that held them, the blades' way ran the track one way, and the closure one way the other.
     * A track set one way through a door is set on both squares already, and stays one way.
     *
     * MUTATION: leave the halves as applied, read towards the toe as a closure, or settle halves that agree, and this
     * fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testATurnoutsDirectionIsReadAsATurnoutsOnBothHalves() throws Exception
    {
        open(verticalPair(), "RSA37-C1");

        TileKey upper = key(5, 5);
        TileKey lower = key(5, 6);

        RouteId fromTheWest = roadTouching(upper, Side.W);
        Route road = session.getGraph().getRoutes(upper).get(fromTheWest);

        Direction toTheToe = road.getA() == Side.S ? Direction.TOWARD_A : Direction.TOWARD_B;
        Direction toTheLeg = toTheToe == Direction.TOWARD_A ? Direction.TOWARD_B : Direction.TOWARD_A;

        RouteId toTheEast = roadTouching(lower, Side.E);

        Set<String> bothTracksBothWays = setOf("4,5>6,6", "6,6>4,5", "6,5>4,6", "4,6>6,5");

        // THROUGH A DOOR, ONE WAY: both squares, and it stays so over a rebuild
        session.setDirection(upper, fromTheWest, toTheToe);
        session.rebuild();

        assertEquals(edges(), setOf("4,5>6,6", "6,5>4,6", "4,6>6,5"), "a track set one way through a door does not run"
            + " one way after a rebuild");

        session.setDirection(upper, fromTheWest, Direction.BOTH);
        session.rebuild();

        assertEquals(edges(), bothTracksBothWays, "precondition: the crossing does not carry both tracks both ways");

        // TOWARDS THE TOE, on one square only, as a permanent turnout's setting was stored
        session.getStore().setTileDirection(upper, fromTheWest, toTheToe);
        session.rebuild();

        assertEquals(session.getGraph().getDirection(upper, fromTheWest), Direction.BOTH, "the way a permanent turnout's"
            + " blades ran is read as a one-way track on its own square (RSA37-C1)");

        assertEquals(session.getGraph().getDirection(lower, toTheEast), Direction.BOTH, "the other square's half of the"
            + " track does not run as the first does (RSA37-C1)");

        assertEquals(edges(), bothTracksBothWays, "a permanent turnout's own way runs its track one way over the crossing"
            + " (RSA37-C1)");

        // TOWARDS A LEG: how a permanent turnout was closed
        session.getStore().setTileDirection(upper, fromTheWest, toTheLeg);
        session.rebuild();

        assertEquals(session.getGraph().getDirection(upper, fromTheWest), Direction.NONE, "a permanent turnout's closure"
            + " is read as a one-way track on its own square (RSA37-C1)");

        assertEquals(session.getGraph().getDirection(lower, toTheEast), Direction.NONE, "a permanent turnout's closure is"
            + " closed on its own square only (RSA37-C1)");

        assertEquals(edges(), setOf("6,5>4,6", "4,6>6,5"), "a track closed on a permanent turnout runs over the crossing"
            + " (RSA37-C1)");

        // AND THE SETUP KEEPS WHAT WAS SET
        assertEquals(session.getStore().getTileDirection(upper, fromTheWest), toTheLeg, "the setup lost the turnout's"
            + " setting");
    }

    /**
     * A click on either square steps both tracks through both ways, the four ways they can each run one way, and closed,
     * then round again, carrying on from the same place whichever square is clicked (OB-320; Adam, 2026-10-04: *"Pretend
     * these are two curved tracks on one tile, so we need to cycle through both possible directions on both (4 combos)"*,
     * and both ways first).
     *
     * MUTATION: count from the clicked square, step the arms, or leave out a combination, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testAClickStepsThroughEveryWayTheTwoTracksCanRun() throws Exception
    {
        open(verticalPair(), "OB-320 click");

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { }));

        final java.lang.reflect.Method cycle =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("cycle", TileKey.class);

        cycle.setAccessible(true);

        List<Set<String>> expected = Arrays.asList(
            setOf("4,5>6,6", "6,5>4,6"),
            setOf("4,5>6,6", "4,6>6,5"),
            setOf("6,6>4,5", "6,5>4,6"),
            setOf("6,6>4,5", "4,6>6,5"),
            setOf(),
            setOf("4,5>6,6", "6,6>4,5", "6,5>4,6", "4,6>6,5"));

        for (int click = 0; click < expected.size(); click++)
        {
            // THE UPPER SQUARE AND THE LOWER IN TURN
            final TileKey square = click % 2 == 0 ? key(5, 5) : key(5, 6);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    cycle.invoke(panel[0], square);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(edges(), expected.get(click), "click " + (click + 1) + ", on " + square + ", does not leave the"
                + " crossing's tracks running the next of both ways, the four one-way combinations and closed (OB-320)");
        }
    }

    /**
     * Allow Every Path opens a closed crossing both ways - its tracks are a crossing's, not a turnout's that only the
     * blades can run (FR-108 with OB-320).
     *
     * MUTATION: leave a pair's roads directed into the toe, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAllowEveryPathOpensAClosedCrossing() throws Exception
    {
        open(verticalPair(), "OB-320 allow");

        assertTrue(session.routesNotOpenBothWays().isEmpty(), "a crossing nobody has touched counts as shut: "
            + session.routesNotOpenBothWays());

        for (RouteId road : session.getGraph().getRoutes(key(5, 5)).keySet())
        {
            session.setDirection(key(5, 5), road, Direction.NONE);
        }

        assertEquals(edges(), setOf(), "precondition: the closed crossing is still run");

        assertEquals(session.allowEveryPath(), 2, "Allow Every Path does not open both squares of the crossing");

        assertEquals(edges(), setOf("4,5>6,6", "6,6>4,5", "6,5>4,6", "4,6>6,5"), "Allow Every Path does not open the"
            + " crossing's two tracks both ways");
    }

    /**
     * A two-square crossing does not end a berth's room, as a crossing does not (OB-320, behaviour.md 5a and 5e): the room
     * before a station past it runs back over it to the turnout behind.
     *
     * MUTATION: let the room walk stop at the crossing's squares as at a permanent turnout, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheRoomRunsOverTheCrossing() throws Exception
    {
        LayoutDiagram page = page();

        sensor(page, 1, 5, 0, 1);

        // a turnout with no address, toe east, which ends the room as a switch does (5e)
        page.addComponent(componentType.CUSTOM_PERM_LEFT, 2, 5, 1, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 5, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        y(page, 4, 5, 0);
        y(page, 4, 6, 2);

        page.addComponent(componentType.STRAIGHT, 5, 6, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        sensor(page, 6, 6, 0, 2);

        open(page, "OB-320 room");

        java.util.Map<TileKey, Integer> lengths = new java.util.LinkedHashMap<>();

        lengths.put(key(3, 5), 5);
        lengths.put(key(4, 5), 2);
        lengths.put(key(4, 6), 2);
        lengths.put(key(5, 6), 4);
        lengths.put(key(6, 6), 1);

        session.setTileLengths(lengths);

        GraphReducer.ReducedEdge over = null;

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            if (at(edge.getStart()).equals("1,5") && at(edge.getEnd()).equals("6,6")) over = edge;
        }

        assertNotNull(over, "precondition: nothing runs from 1,5 over the crossing to 6,6: " + edges());

        assertEquals(over.getRoomAtTheEnd(), 14, "the room before 6,6 stops at the crossing (4 + 1) rather than running"
            + " back over it to the turnout (5 + 2 + 2 + 4 + 1) - a crossing taken for the two turnouts it is drawn with"
            + " (OB-320)");
    }

    /**
     * The One-Way tool follows a track across the crossing, corner to corner, and finds no way round the reverse curve
     * from one leg to the leg beside it (OB-320): its walk, like the reduction's, is told which track it crossed on.
     *
     * MUTATION: let the walk change track at the toes, or carry the wrong leg across, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheOneWayToolFollowsATrackAcross() throws Exception
    {
        open(verticalPair(), "OB-320 one-way tool");

        assertEquals(session.setOneWayRun(key(4, 5), key(4, 6)), -1, "the One-Way tool found track from 4,5 to 4,6 - the"
            + " reverse curve between two legs of the crossing, which no train can run (OB-320)");

        assertTrue(session.setOneWayRun(key(4, 5), key(6, 6)) > 0, "the One-Way tool found no track from 4,5 across the"
            + " crossing to 6,6 (OB-320)");

        assertEquals(edges(), setOf("4,5>6,6", "6,5>4,6", "4,6>6,5"), "the One-Way tool did not make the crossing's"
            + " north-west to south-east track one-way, and only that");
    }

    /**
     * Two permanent rights toe to toe, each turned half round from the other, are one crossing: the straight legs a
     * straight track through both toes, the diverging legs the diagonal - the shape at 15,13 and 16,13 on Adam's
     * TC3Sandbox_layout.
     *
     * MUTATION: pair only Ys, or compare the legs without turning one square's round, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTwoRightTurnoutsToeToToeAreACrossing() throws Exception
    {
        LayoutDiagram page = page();

        // toe east, straight leg west, diverging north; and toe west, straight leg east, diverging south
        turnout(page, componentType.CUSTOM_PERM_RIGHT, 5, 5, 1);
        turnout(page, componentType.CUSTOM_PERM_RIGHT, 6, 5, 3);

        sensor(page, 4, 5, 0, 1);
        sensor(page, 5, 4, 1, 2);
        sensor(page, 7, 5, 0, 3);
        sensor(page, 6, 6, 1, 4);

        open(page, "OB-320 two rights");

        assertEquals(session.getGraph().crossingPartner(key(5, 5)), key(6, 5), "two permanent rights toe to toe are not a"
            + " crossing (OB-320)");

        assertEquals(edges(), setOf("4,5>7,5", "7,5>4,5", "5,4>6,6", "6,6>5,4"), "two permanent rights toe to toe do not"
            + " carry a straight track and a diagonal, both ways, and only them (OB-320)");
    }

    /**
     * Two permanent lefts toe to toe, each turned half round from the other, are one crossing the same way.
     *
     * MUTATION: pair only Ys, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTwoLeftTurnoutsToeToToeAreACrossing() throws Exception
    {
        LayoutDiagram page = page();

        // toe south, straight leg north, diverging west; and toe north, straight leg south, diverging east
        turnout(page, componentType.CUSTOM_PERM_LEFT, 5, 5, 0);
        turnout(page, componentType.CUSTOM_PERM_LEFT, 5, 6, 2);

        sensor(page, 5, 4, 1, 1);
        sensor(page, 4, 5, 0, 2);
        sensor(page, 5, 7, 1, 3);
        sensor(page, 6, 6, 0, 4);

        open(page, "OB-320 two lefts");

        assertEquals(session.getGraph().crossingPartner(key(5, 5)), key(5, 6), "two permanent lefts toe to toe are not a"
            + " crossing (OB-320)");

        assertEquals(edges(), setOf("5,4>5,7", "5,7>5,4", "4,5>6,6", "6,6>4,5"), "two permanent lefts toe to toe do not"
            + " carry a straight track and a diagonal, both ways, and only them (OB-320)");
    }

    /**
     * A permanent left and a permanent right toe to toe have both diverging legs on one side, so no track could go on from
     * one of them: they stay two turnouts, trailed only - and with both toes facing, nothing passes them.
     *
     * MUTATION: pair any two permanent turnouts toe to toe, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testALeftAndARightToeToToeStayTurnouts() throws Exception
    {
        LayoutDiagram page = page();

        // toe east, legs west and south; and toe west, legs east and south
        turnout(page, componentType.CUSTOM_PERM_LEFT, 5, 5, 1);
        turnout(page, componentType.CUSTOM_PERM_RIGHT, 6, 5, 3);

        sensor(page, 4, 5, 0, 1);
        sensor(page, 5, 6, 1, 2);
        sensor(page, 7, 5, 0, 3);
        sensor(page, 6, 6, 1, 4);

        open(page, "OB-320 left and right");

        assertNull(session.getGraph().crossingPartner(key(5, 5)), "a left and a right with both diverging legs on one side"
            + " are a crossing");

        for (TileKey square : Arrays.asList(key(5, 5), key(6, 5)))
        {
            for (Route route : session.getGraph().getRoutes(square).values())
            {
                assertNotNull(route.getDirectedToward(), square + " has a road its blades do not restrict: " + route);
            }
        }

        assertEquals(edges(), setOf(), "a train passes a left and a right toe to toe, which as turnouts with no address"
            + " can only be trailed");
    }

    /**
     * A permanent Y toe to toe with a permanent right, and two permanent three-ways toe to toe, stay turnouts: the Y's legs
     * do not face the right's, and a three-way is not half of a crossing.
     *
     * MUTATION: pair any two permanent turnouts whose legs face, three-ways too, or any toe to toe, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAYWithARightAndTwoThreeWaysStayTurnouts() throws Exception
    {
        LayoutDiagram page = page();

        y(page, 2, 2, 0);
        turnout(page, componentType.CUSTOM_PERM_RIGHT, 2, 3, 2);

        turnout(page, componentType.CUSTOM_PERM_THREEWAY, 8, 2, 0);
        turnout(page, componentType.CUSTOM_PERM_THREEWAY, 8, 3, 2);

        open(page, "OB-320 still turnouts");

        for (TileKey square : Arrays.asList(key(2, 2), key(2, 3), key(8, 2), key(8, 3)))
        {
            assertNull(session.getGraph().crossingPartner(square), square + " is half of a crossing");
        }
    }

    /**
     * The click names a straight track through the crossing by its two sides, and a diagonal by its two corners: on two
     * rights toe to toe, "W-E" and "NW-SE" (OB-320).
     *
     * MUTATION: name every end by a corner, and this fails with "WW".
     *
     * @throws Exception from the panel
     */
    @Test
    public void testTheClickNamesAStraightTrackByItsSides() throws Exception
    {
        LayoutDiagram page = page();

        turnout(page, componentType.CUSTOM_PERM_RIGHT, 5, 5, 1);
        turnout(page, componentType.CUSTOM_PERM_RIGHT, 6, 5, 3);

        open(page, "OB-320 naming");

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        final String[] said = new String[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { });

                java.lang.reflect.Method cycle =
                    org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("cycle", TileKey.class);

                cycle.setAccessible(true);
                cycle.invoke(panel[0], key(5, 5));

                java.lang.reflect.Field hint = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("hint");

                hint.setAccessible(true);

                said[0] = ((javax.swing.JLabel) hint.get(panel[0])).getText();
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        // WHOLE NAMES: "WW-EE" holds "W-E"
        assertTrue(said[0] != null && java.util.regex.Pattern.compile("(?<![A-Z])W-E(?![A-Z])").matcher(said[0]).find()
            && java.util.regex.Pattern.compile("(?<![A-Z])NW-SE(?![A-Z])").matcher(said[0]).find(), "the click does not"
            + " name the straight track W-E and the diagonal NW-SE: " + said[0]);
    }

    // ---------------------------------------------------------------- the railway

    /** The vertical pair: Ys at 5,5 (toe south) and 5,6 (toe north), and a sensor at each of the four ends. */
    private static LayoutDiagram verticalPair() throws IOException
    {
        LayoutDiagram page = page();

        y(page, 5, 5, 0);
        y(page, 5, 6, 2);

        sensor(page, 4, 5, 0, 1);
        sensor(page, 6, 5, 0, 2);
        sensor(page, 4, 6, 0, 3);
        sensor(page, 6, 6, 0, 4);

        return page;
    }

    private static LayoutDiagram page()
    {
        LayoutDiagram page = new LayoutDiagram("main", 12, 12, null, null);

        page.setPageId("1");

        return page;
    }

    /** A permanent Y: toe south at orientation 0, east at 1, north at 2, west at 3. */
    private static void y(LayoutDiagram page, int x, int y, int orientation) throws IOException
    {
        page.addComponent(componentType.CUSTOM_PERM_Y, x, y, orientation, 0, 0, 0, accessoryDecoderType.MM2, null);
    }

    /** A turnout with no address, of the type given, at the orientation given. */
    private static void turnout(LayoutDiagram page, componentType type, int x, int y, int orientation) throws IOException
    {
        page.addComponent(type, x, y, orientation, 0, 0, 0, accessoryDecoderType.MM2, null);
    }

    /** A sensor: east-west at orientation 0, north-south at 1. */
    private static void sensor(LayoutDiagram page, int x, int y, int orientation, int n) throws IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, orientation, 0, 63 + n, 63 + n, accessoryDecoderType.MM2, null);
    }

    private void open(LayoutDiagram page, String configuration) throws Exception
    {
        session.open(Arrays.asList(page));
        session.initialize(configuration);
    }

    /** Every edge of the reduction, as "x,y>x,y" from its start to its end. */
    private Set<String> edges()
    {
        Set<String> out = new TreeSet<>();

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            out.add(at(edge.getStart()) + ">" + at(edge.getEnd()));
        }

        return out;
    }

    /** The road of a square that touches this side. */
    private RouteId roadTouching(TileKey square, Side side)
    {
        for (java.util.Map.Entry<RouteId, Route> road : session.getGraph().getRoutes(square).entrySet())
        {
            if (road.getValue().touches(side)) return road.getKey();
        }

        throw new AssertionError("precondition: " + square + " has no road touching " + side);
    }

    /** Whether the graph reports this square as a turnout with no address. */
    private boolean warnedAbout(TileKey square)
    {
        for (TileGraph.Problem problem : session.getGraph().getProblems())
        {
            if (square.equals(problem.getTile()) && TileGraph.WARN_PERMANENT_TURNOUT.equals(problem.getMessageKey()))
            {
                return true;
            }
        }

        return false;
    }

    private static TileKey key(int x, int y)
    {
        return new TileKey("main", x, y);
    }

    private static String at(TileKey square)
    {
        return square.getX() + "," + square.getY();
    }

    private static Set<String> setOf(String... edges)
    {
        return new TreeSet<>(Arrays.asList(edges));
    }

    private static void delete(File file)
    {
        File[] inside = file.listFiles();

        if (inside != null) for (File f : inside) delete(f);

        if (!file.delete()) file.deleteOnExit();
    }
}
