package core;

import java.io.File;
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
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * What TrainControl treats as a parking berth, now that it is two facts rather than a flag (FR-060; Adam: "For now, we
 * consider anything with autodestination=false and only one way in/out as a parking square.  We can revisit dedicated
 * marking if this doesn't work out with clean logic, or if it gets too confusing to the user to manage.").
 *
 * The designation is derivable, so there is nothing to mark or migrate.  Each half lets something through alone, so
 * both directions are asserted rather than a count: `autoDestination` off alone would call a platform he keeps for
 * himself a berth, and one way in and out alone would call a headshunt one.  It is not the berth rule's gate:
 * `whyABerthCannotHoldIt` applies wherever autonomy will not choose the square, which is deliberately wider.
 *
 * @author Adam
 */
public class testWhatCountsAsAParkingSquare
{
    /** Twelve squares he would call parking, by name, on the frozen copy of his railway. */
    private static final String[] PARKING = {
        "ParkingTrack4", "ParkingTrack5", "ParkingTrack6", "ParkingTrack7", "ParkingTrack8",
        "ParkingTrack9", "ParkingTrack10", "ParkingTrack12", "TopR1ParkLong", "TopR1ParkShort",
        "ButtomLongestPark", "TopMainR0Park"
    };

    /** Squares autonomy will not choose that have track running past or through them. */
    private static final String[] NOT_PARKING = {
        "TunnelLongPark", "TunnelLeftPark", "TunnelCenterPark", "TunnelRightPark",
        "ParkingTrack11", "LowerParkingOuter", "RampDown", "BottomMainPost"
    };

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    /** Opens the frozen railway as it stands, measuring nothing: this is about shape, not length. */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new AssertionError("the snapshot did not build");
    }

    /** Closes the sandbox, and with it the layout preference it redirected. */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /** The twelve dead-end berths on his railway answer yes. */
    @Test
    public void testADeadEndHeKeepsForHimselfIsParking()
    {
        for (String name : PARKING)
        {
            Point square = named(name);

            assertFalse(square.isAutoDestination(),
                name + " is choosable by autonomy on this railway, so it is not the square this claim"
                + " is about and the fixture has moved");

            assertTrue(layout.isParkingSquare(square),
                name + " has one way in and out and autonomy will not choose it, and TrainControl does"
                + " not call it a parking berth. Adam, 2026-09-13: \"anything with autodestination"
                + "=false and only one way in/out\" is one - that is the whole definition, and it is"
                + " what FR-060 asked for a fourth flag to say.");
        }
    }

    /**
     * And the eight with track running past them answer no: counting a square's COPIES rather than the places they lead
     * to makes a split berth look like a junction.
     */
    @Test
    public void testSomewhereWithAnotherWayOutIsNot()
    {
        for (String name : NOT_PARKING)
        {
            Point square = named(name);

            assertFalse(square.isAutoDestination(),
                name + " is choosable by autonomy now, so it no longer shows the case this claim is"
                + " about: a square he keeps for himself that trains can still run past");

            assertFalse(layout.isParkingSquare(square),
                name + " has more than one way in and out and TrainControl calls it a parking berth"
                + " anyway. A berth is a dead end; somewhere with another road through it is a place"
                + " he is keeping for himself, which is a different thing and is what the"
                + " autoDestination flag already says.");
        }
    }

    /**
     * Both halves are load-bearing, and the railway can tell them apart; without this both claims above could pass
     * against `!isAutoDestination()` alone.
     */
    @Test
    public void testNeitherHalfDecidesItAlone()
    {
        int notChosen = 0;
        int parking = 0;

        Set<String> counted = new LinkedHashSet<>();

        for (Point square : layout.getPoints())
        {
            TileKey tile = session.getStationIndex().squareOf(square.getName());

            if (tile == null || !counted.add(tile.toString())) continue;

            if (!square.isDestination()) continue;

            if (!square.isAutoDestination()) notChosen++;

            if (layout.isParkingSquare(square)) parking++;
        }

        assertTrue(notChosen > parking,
            "every square autonomy will not choose is also a parking berth (" + notChosen + " of"
            + " each), so the \"only one way in/out\" half of Adam's ruling decides nothing on this"
            + " railway and the claims above would pass without it");

        assertTrue(parking > 0,
            "no square on this railway is a parking berth at all, so the claims above are asserting"
            + " that a rule never fires");

        // AND THE OTHER HALF, WHICH THIS RAILWAY CANNOT SHOW BY ITSELF: every dead end on it already carries
        // `autoDestination: false`, so the edit an operator would make shows it: turn autonomy's choosing back ON at a
        // berth.
        Point berth = named("ParkingTrack4");

        assertTrue(layout.isParkingSquare(berth),
            "the square this half uses is not a parking berth to begin with, so turning its flag"
            + " cannot show the flag deciding anything");

        try
        {
            berth.setAutoDestination(true);

            assertFalse(layout.isParkingSquare(berth),
                "a dead-end siding that autonomy is allowed to choose is still called a parking"
                + " berth, so \"autodestination=false\" is doing nothing in the definition and every"
                + " dead end on the railway - headshunts included - is one");
        }
        finally
        {
            berth.setAutoDestination(false);
        }

        assertTrue(layout.isParkingSquare(berth),
            "the flag was not put back, so every claim after this one is running against a railway"
            + " this class edited");
    }

    /**
     * And the berth rule is deliberately wider than this: Adam's rule "parking berths cant block any other edges" was
     * validated at TunnelLongPark, which has three ways in and is not a parking square.  Gating it on this predicate
     * would drop the case it was written for.
     */
    @Test
    public void testTheBerthRuleIsNotGatedOnThis()
    {
        Point wider = named("TunnelLongPark");

        assertFalse(layout.isParkingSquare(wider),
            "TunnelLongPark is now a parking square, so this claim is about a railway that has moved");

        assertFalse(wider.isAutoDestination(),
            "TunnelLongPark is choosable by autonomy now, so the berth rule no longer applies there"
            + " for a different reason than the one this is about");
    }

    /** One Point standing on the named station, or a skip when the snapshot has moved. */
    private static Point named(String station)
    {
        for (Point square : layout.getPoints())
        {
            TileKey tile = session.getStationIndex().squareOf(square.getName());

            if (tile == null) continue;

            if (station.equals(session.getStationIndex().nameOf(tile))) return square;
        }

        throw new AssertionError("this railway no longer has a station called " + station);
    }
}
