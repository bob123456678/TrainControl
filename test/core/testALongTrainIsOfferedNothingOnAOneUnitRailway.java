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
 * A railway where every section really is one unit, and what the room rule says on it. The claim moved here from
 * `core.testTheLengthGuardsOnTheRealLayout`, where it was start-dependent (it asserted about ONE square) and measured
 * the wrong thing: that test set every TILE to one unit, but a section is not a tile (the run into `RampDown` is
 * eighteen tiles), so a nine-unit train fit and was correctly offered
 * (`testTheLengthGuardsOnTheRealLayout.testRampDownIsOfferedPastTheOneUnitItOnlyPasses`).
 *
 * `single-switch` fixes both: every edge is two to four tiles, five squares a train may stand on and three berths, and
 * the claim is asserted from EVERY start square. Three measurements, one train, opposite answers: nothing measured
 * admits everything (*"generally, allow it"*), one unit per section refuses the nine-unit train everywhere, and the
 * one-unit train is still welcome on the same railway. Without the third, the middle one would pass on a railway that
 * offers nothing to anybody.
 *
 * The train is this class's own (`newMM2Locomotive`), deleted in `tearDownClass`, not a borrowed real locomotive's
 * length.
 *
 * @author Adam
 */
public class testALongTrainIsOfferedNothingOnAOneUnitRailway
{
    private static support.Scenario scenario;

    private static MarklinLocomotive train;

    /** What the train is called, and it says what it is for. */
    private static final String TRAIN = "one-unit railway probe";

    /**
     * Its address, chosen so that a stray one left behind by a crash is identifiable: addresses are not unique in this
     * database, so this needs to be nobody else's rather than free.
     */
    private static final int ADDRESS = 62;

    /**
     * Every square a train may be placed on, which makes the claim not start-dependent. Named rather than discovered: a
     * list read off the layout would shrink silently if the fixture changed, and a claim over an empty list passes for
     * nothing. The scenario's README states these as invariants.
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
     * Takes the class's train off the database and puts the layout preference back: `init()` opens Adam's real
     * locomotive database, so a train left behind is a train on his railway.
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
     * The fixture is the shape the claim needs: sections of a few units, not of eighteen. Asserted first and
     * separately, because if an edge here ever became long enough to hold a nine-unit train the claim below would go
     * green for the wrong reason and nobody would be told.
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
     * With every section one unit, the nine-unit train is offered nothing at all, from ANY square: not just "no berth",
     * because on a railway measured this tightly there is nowhere the train fits.
     *
     * MUTATION: `>=` in place of `>` at `Layout.whyTooLongForThisRoute`'s comparison does not move this (nine is never
     * two); measuring the fixture at nine units a square does, and the control below asserts that from the other side.
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
     * And the control: the same railway, the same squares, a train that fits, is offered everything; without it the
     * claim above passes on a railway that offers nothing to anybody.
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
     * Unmeasured is unknown, not zero: with nothing measured the same long train is welcome again (*"generally, allow
     * it."*). The strongest form of the control: one railway, one train, two measurements, opposite answers.
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
     * Every square in the fixture, measured, including the squares trains stand on: the END of an edge counts towards
     * its length, so measuring only the track between them would leave the berths at zero.
     */
    private void measureEverySquareAs(int units)
    {
        // Collected first, then written: setTileLength rebuilds the reducer, so writing to its collections while
        // walking them re-derives the graph under the iterator.
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
     * Puts the train on a named square and asks the railway where it may go; the names of every destination offered,
     * empty when none is.
     */
    private Set<String> destinationsFrom(String start) throws Exception
    {
        Layout built = scenario.build();

        Point from = built.getPoint(start);

        assertNotNull(from, "there is no square called \"" + start + "\" on this railway, so the claim"
            + " that names it is asserting about nothing");

        // Off wherever it was first: moveLocomotive fills the target without emptying the old square when the two are
        // different Points, and a train recorded twice blocks twice the track.
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
