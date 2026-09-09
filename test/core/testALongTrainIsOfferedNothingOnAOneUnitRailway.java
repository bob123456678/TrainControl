package core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A railway where every section really is one unit, and what the room rule says on it.
 *
 * **This claim used to live on the operator's own railway and could not be true there.**
 * `core.testTheLengthGuardsOnTheRealLayout` asserted that, with "every section measured at one unit", a
 * nine-unit train is offered no berth it would have to come to rest inside.  Adam, 2026-09-09, on being
 * shown that it holds from one start square and not from another:
 *
 * > *"this is just a poor test, and an even worse layout config to test with, since we only gave 3
 * > tracks artificially low lengths."*
 *
 * Two things were wrong with it and only one of them was the fixture.
 *
 * 1. **A SECTION IS NOT A TILE.**  That test set every TILE to one unit, and an edge on his railway is
 *    made of many tiles - the run into `RampDown` is eighteen of them - so "every section is one unit"
 *    produced sections of eighteen units.  A nine-unit train fits in eighteen, and was correctly
 *    offered.  The measurement is at `testTheLengthGuardsOnTheRealLayout.testWhyRampDownIsOffered`.
 * 2. **It was start-dependent.**  It asked whatever train happened to be standing somewhere for a
 *    destination, so it asserted about ONE square and read as an assertion about the whole railway.
 *
 * `single-switch` fixes both.  Every edge on it is two to four tiles, there are five squares a train
 * may stand on and three berths, and the claim is asserted from EVERY start square rather than from
 * whichever one the fixture enumerates first.  Nothing here is start-dependent, so nothing here can go
 * green for the reason the old claim did.
 *
 * **Three measurements, one train, opposite answers**, which is the shape a length test has to have:
 * nothing measured admits everything (Adam: *"generally, allow it"*), one unit per section refuses the
 * nine-unit train everywhere, and the one-unit train is still welcome on the same one-unit railway.
 * Without the third, the middle one would pass on a railway that offers nothing to anybody - which is
 * the state `testTheLengthGuardsOnTheRealLayout` was in before its fixture wired the accessories.
 *
 * The train is this class's own (`newMM2Locomotive`), deleted in `tearDownClass`.  Adam: *"generate
 * trains programmatically in the tests, and give them semantic names."*  Two classes broke on
 * 2026-09-09 for borrowing a real locomotive's length.
 *
 * @author Adam
 */
public class testALongTrainIsOfferedNothingOnAOneUnitRailway
{
    private static support.Scenario scenario;

    private static MarklinLocomotive train;

    /**
     * What the train is called, and it says what it is for.
     */
    private static final String TRAIN = "one-unit railway probe";

    /**
     * Its address, chosen only so that a stray one left behind by a crash is identifiable.
     *
     * Addresses are not unique in this database and several test classes already share one, so this
     * needs to be nobody else's rather than free.
     */
    private static final int ADDRESS = 62;

    /**
     * Every square a train may be placed on here, which is what makes the claim below not
     * start-dependent.
     *
     * Named rather than discovered: a list read off the layout would shrink silently if the fixture
     * changed, and a claim asserted over an empty list is a claim that passes for nothing.  The
     * scenario's README states these as invariants.
     */
    private static final String[] EVERY_START =
    {
        "WestEnd", "Approach (eastbound)", "Approach (westbound)", "MainPlatform", "BranchPlatform"
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("single-switch");

        scenario.getModel().stop();

        train = scenario.getModel().newMM2Locomotive(TRAIN, ADDRESS);

        assertNotNull(train, "the class could not create its own train, so every claim below would be"
            + " about whatever the fixture happened to contain");
    }

    /**
     * Takes the class's train off the database and puts the layout preference back.
     *
     * `init()` opens Adam's real locomotive database rather than an empty one, so a train left behind
     * here is a train on his railway.
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null)
        {
            try
            {
                scenario.getModel().deleteLoc(TRAIN);
            }
            catch (Exception alreadyGone)
            {
            }

            scenario.close();
        }
    }

    /**
     * The fixture is the shape the claim needs: sections of a few units, not of eighteen.
     *
     * Asserted first and separately, because this is the property the old claim silently did not have.
     * If an edge here ever became long enough to hold a nine-unit train, the claim below would go green
     * for the same reason the one on the real railway did, and nobody would be told.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testEverySectionReallyIsOneUnit() throws Exception
    {
        measureEverySquareAs(1);

        Layout built = scenario.build();

        int longest = 0;

        StringBuilder what = new StringBuilder();

        for (Edge edge : built.getEdges())
        {
            longest = Math.max(longest, edge.getLength());

            what.append(" ").append(edge.getName()).append("=").append(edge.getLength());
        }

        assertTrue(longest > 0,
            "no edge on this railway has a length at all after every square was measured at one unit,"
            + " so the claims below would be about an unmeasured railway - which the guard declines to"
            + " judge and therefore admits:" + what);

        assertTrue(longest < 9,
            "the longest section here is " + longest + " units, which holds the nine-unit train - so"
            + " \"one unit per section\" no longer means what this class asserts and the refusal below"
            + " would be testing nothing:" + what);
    }

    /**
     * With every section one unit, the nine-unit train is offered nothing at all - from ANY square.
     *
     * Adam's original claim, on a railway that can carry it.  Not "no berth": nothing, including the
     * through squares, because on a railway measured this tightly there is nowhere the train fits.
     *
     * MUTATION: `>=` in place of `>` at `Layout.whyTooLongForTheBerth`'s comparison does not move this
     * (nine is never two); measuring the fixture at nine units a square does, and that is what the
     * control below asserts from the other side.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheNineUnitTrainIsOfferedNothingFromAnySquare() throws Exception
    {
        measureEverySquareAs(1);

        train.setTrainLength(9);

        StringBuilder offered = new StringBuilder();

        for (String start : EVERY_START)
        {
            Set<String> here = destinationsFrom(start);

            if (!here.isEmpty()) offered.append(" ").append(start).append("->").append(here);
        }

        assertEquals(offered.toString(), "",
            "a nine-unit train is still offered somewhere on a railway whose longest section is four"
            + " units:" + offered + ". Every one of those is a refusal the room rule did not make");
    }

    /**
     * And the control: the same railway, the same squares, a train that fits, is offered everything.
     *
     * Without this the claim above passes on a railway that offers nothing to anybody, which is the
     * state its predecessor was in until its fixture wired the accessories - and no assertion in that
     * file could tell the difference.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheOneUnitTrainIsStillOfferedSomewhereFromEverySquare() throws Exception
    {
        measureEverySquareAs(1);

        train.setTrainLength(1);

        StringBuilder empty = new StringBuilder();

        for (String start : EVERY_START)
        {
            if (destinationsFrom(start).isEmpty()) empty.append(" ").append(start);
        }

        assertEquals(empty.toString(), "",
            "a one-unit train is offered nothing from" + empty + " on a railway measured at one unit a"
            + " square, so the refusal above says nothing about lengths - it says this railway offers"
            + " nothing to anybody");
    }

    /**
     * Unmeasured is unknown, not zero: with nothing measured the same long train is welcome again.
     *
     * Adam, on an unmeasured run in: **"generally, allow it."**  This is the half of the rule that
     * keeps an unmeasured railway usable, and it is the strongest form of the control - one railway,
     * one train, two measurements, opposite answers, and nothing but the lengths changed between them.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheSameTrainIsWelcomeWhenNothingIsMeasured() throws Exception
    {
        train.setTrainLength(9);

        measureEverySquareAs(1);

        Set<String> whenTight = destinationsFrom("Approach (eastbound)");

        measureEverySquareAs(0);

        Set<String> whenUnmeasured = destinationsFrom("Approach (eastbound)");

        assertTrue(whenTight.isEmpty(),
            "the nine-unit train was offered " + whenTight + " on one-unit sections");

        assertFalse(whenUnmeasured.isEmpty(),
            "the same train is offered nothing on a railway where NOTHING is measured, so the guard is"
            + " refusing on the absence of a measurement rather than on a measurement - which makes"
            + " every unmeasured layout unusable and is the failure its own comment says was removed"
            + " once already");
    }

    /**
     * Every square in the fixture, measured, including the squares trains stand on.
     *
     * The END of an edge counts towards its length, so a length on a platform is part of the room a
     * train coming to rest there has.  Measuring only the track between them would leave the berths
     * themselves at zero.
     *
     * @param units the length to give every square
     */
    private void measureEverySquareAs(int units)
    {
        // COLLECTED FIRST, THEN WRITTEN. setTileLength rebuilds the reducer, so walking its own
        // collections while writing to them re-derives the graph under the iterator.
        Set<TileKey> everyTile = new LinkedHashSet<>();

        everyTile.addAll(scenario.getSession().getReducer().getPoints().keySet());

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge
            : scenario.getSession().getReducer().getEdges())
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) everyTile.add(step.getTile());
            }
        }

        for (TileKey tile : everyTile) scenario.getSession().setTileLength(tile, units);
    }

    /**
     * Puts the train on a named square and asks the railway where it may go.
     *
     * @param start the square to stand it on
     * @return the names of every destination offered, empty when none is
     * @throws Exception on a failure to build
     */
    private Set<String> destinationsFrom(String start) throws Exception
    {
        Layout built = scenario.build();

        Point from = built.getPoint(start);

        assertNotNull(from, "there is no square called \"" + start + "\" on this railway, so the claim"
            + " that names it is asserting about nothing");

        // OFF WHEREVER IT WAS FIRST: moveLocomotive fills the target without emptying the old square
        // when the two are different Points, and a train recorded twice blocks twice the track.
        for (Point point : built.getPoints())
        {
            if (train.equals(point.getCurrentLocomotive()))
            {
                built.moveLocomotive(null, point.getName(), true);
            }
        }

        built.moveLocomotive(train.getName(), from.getName(), false);

        Set<String> out = new LinkedHashSet<>();

        List<List<Edge>> paths = built.getPossiblePaths(train, true);

        if (paths == null) return out;

        for (List<Edge> path : paths)
        {
            if (!path.isEmpty()) out.add(path.get(path.size() - 1).getEnd().getName());
        }

        return out;
    }
}
