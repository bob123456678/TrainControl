package core;

import java.util.Arrays;
import java.util.LinkedHashSet;
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
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;

/**
 * A square answered 0 on purpose is not called unmeasured when a berth is refused (Adam, 2026-09-23).
 *
 * *"stop listing answered zeros as missing."*  A berth refused because its approach is partly unmeasured says how
 * many squares of it *"still have no length ... Measuring them may clear this"* - and a square the operator answered
 * 0 on purpose (OB-274) was counted among them, pointing him at a square he has already answered.  The rule itself is
 * unchanged: an answered 0 is still read as nothing, the walk still claims it, and the berth is still refused.
 *
 * The berth rule runs on the running layout, which knew only each place's length, so the build now marks a place
 * answered and the runtime keeps the mark.
 *
 * On Adam's measured TunnelLongPark: its approach from BottomMainA crosses squares he left at 0, and a four-unit train
 * lies back over them onto the road to BottomMainAPre.
 *
 * @author Adam
 */
public class testAnAnsweredZeroIsNotMissing
{
    private static final String OUR_TRAIN = "answered zero probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    private static MarklinLocomotive train;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        train = model.newMM2Locomotive(OUR_TRAIN, 2397);

        assertNotNull(train, "could not create this class's train");

        train.setTrainLength(4);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null) model.deleteLoc(OUR_TRAIN);
        }
        catch (Exception alreadyGone)
        {
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Answered, the squares stop being counted as having no length; the berth is still refused.
     *
     * MUTATION: the build not marking an answered place, the runtime not reading the mark, or the berth rule not
     * asking it - each leaves the count where it was.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheRefusalNoLongerCountsAnAnsweredSquare() throws Exception
    {
        String before = whyTheBerthRefuses();

        assertNotNull(before, "a four-unit train was accepted at TunnelLongPark, so there is no refusal to read");

        assertTrue(saysSquaresHaveNoLength(before),
            "CONTROL: nothing on the approach is answered and the refusal does not say any square has no length, so"
            + " the claim below would pass by the note never being there: " + before);

        // EVERY SQUARE OF THE APPROACH HE LEFT AT 0, answered 0 on purpose.
        Set<TileKey> unmeasured = new LinkedHashSet<>();

        Edge approach = approach(layoutNow());

        for (int at = 0; at < approach.getPlaceIds().size(); at++)
        {
            if (approach.getPlaceLengths().get(at) > 0) continue;

            String id = approach.getPlaceIds().get(at);

            String square = id.indexOf('/') >= 0 ? id.substring(0, id.indexOf('/')) : id;

            int colon = square.lastIndexOf(':');
            int comma = square.lastIndexOf(',');

            unmeasured.add(new TileKey(square.substring(0, colon), Integer.parseInt(square.substring(colon + 1, comma)),
                Integer.parseInt(square.substring(comma + 1))));
        }

        assertFalse(unmeasured.isEmpty(), "precondition: every square of the approach is measured");

        for (TileKey square : unmeasured) session.getStore().answerTileLengthZero(square);

        session.rebuild();

        String after = whyTheBerthRefuses();

        assertNotNull(after, "answering the squares 0 made the berth accept a four-unit train - an answered 0 is still"
            + " read as nothing, and the train still lies back onto the road");

        assertFalse(saysSquaresHaveNoLength(after),
            "every square of the approach was answered 0 on purpose (" + unmeasured + ") and the refusal still says some"
            + " of them have no length: " + after);
    }

    /**
     * On his railway the build marks exactly the places Mass Assign Lengths would ask a length for (OB-297, ADA-C1).
     *
     * MUTATION: mark nothing, mark every place, or mark from another list, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheBuildMarksWhatMassAssignAsksFor() throws Exception
    {
        // What Mass Assign Lengths asks for: its pieces, its switches and its shared squares still with no length.
        Set<TileKey> asked = new LinkedHashSet<>(session.squaresNeedingALength());

        java.lang.reflect.Method marked = Edge.class.getMethod("pieceToMeasure", String.class);

        int seen = 0;

        for (Edge edge : layoutNow().getEdges())
        {
            for (String id : edge.getPlaceIds())
            {
                boolean expected = asked.contains(squareOf(id));

                if (expected) seen++;

                assertEquals(marked.invoke(edge, id) != null, expected, id + " on " + edge.getName() + " is "
                    + (expected ? "" : "not ") + "something Mass Assign asks a length for, and the railway is told"
                    + " otherwise");
            }
        }

        assertTrue(seen > 0, "precondition: nothing on his frozen railway is left to measure, so nothing here is marked");
    }

    /**
     * TopMainR1Inter's refusing figure is the railway's own refusal (TDA-C10, ADA-C4): on a route in from TopR1ParkShort
     * measuring that figure, a train that long is admitted and one a unit longer is refused.
     *
     * The notice reads the figure off the built railway; the refusal is `Layout.whyTooLongForThisRoute`.  Two rules stated
     * apart, so asked of each other.
     *
     * MUTATION: give the figure a unit high, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheRefusingFigureIsTheRailwaysOwn() throws Exception
    {
        TileKey inter = null;

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if ("TopMainR1Inter".equals(session.getStore().getPointName(key))) inter = key;
        }

        assertNotNull(inter, "precondition: his railway has no TopMainR1Inter");

        Integer figure = null;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if ("autosetup.ui.checkRunInShorterThanThePlatformRefused".equals(finding.getMessageKey())
                && inter.equals(finding.getTile())) figure = finding.getThird();
        }

        assertNotNull(figure, "precondition: TopMainR1Inter's notice gives no refusing figure");

        Layout layout = layoutNow();

        java.util.List<Edge> path = null;

        for (org.traincontrol.automation.Point start : layout.getPoints())
        {
            if (!start.isDestination() || !start.getName().startsWith("TopR1ParkShort")) continue;

            for (org.traincontrol.automation.Point end : layout.getPoints())
            {
                if (!end.isDestination() || !(end.getName().equals("TopMainR1Inter")
                    || end.getName().startsWith("TopMainR1Inter ("))) continue;

                java.util.List<Edge> way = layout.bfs(start, end, new java.util.ArrayList<java.util.List<Edge>>());

                if (way != null && Layout.measuredRouteIn(way) == figure) path = way;
            }
        }

        assertNotNull(path, "no route the railway runs from TopR1ParkShort into TopMainR1Inter measures the notice's"
            + " figure, " + figure);

        Integer was = train.getTrainLength();

        try
        {
            train.setTrainLength(figure);

            org.testng.Assert.assertNull(Layout.whyTooLongForThisRoute(path, train), "a train of " + figure + ", the"
                + " notice's figure, is refused from TopR1ParkShort");

            train.setTrainLength(figure + 1);

            assertNotNull(Layout.whyTooLongForThisRoute(path, train), "a train of " + (figure + 1) + " is admitted from"
                + " TopR1ParkShort, where the notice says it is refused");
        }
        finally
        {
            train.setTrainLength(was);
        }
    }

    /** The square a place identifier names - the tile, before any `/route` of an overpass. */
    private static TileKey squareOf(String id)
    {
        String square = id.indexOf('/') >= 0 ? id.substring(0, id.indexOf('/')) : id;

        int colon = square.lastIndexOf(':');
        int comma = square.lastIndexOf(',');

        return new TileKey(square.substring(0, colon), Integer.parseInt(square.substring(colon + 1, comma)),
            Integer.parseInt(square.substring(comma + 1)));
    }

    /** The berth rule's answer for this class's train at TunnelLongPark, on a freshly built railway. */
    private static String whyTheBerthRefuses() throws Exception
    {
        return Layout.whyABerthCannotHoldIt(Arrays.asList(approach(layoutNow())), train);
    }

    private static Layout layoutNow() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        return model.getAutoLayout();
    }

    private static Edge approach(Layout layout)
    {
        Edge edge = layout.getEdge("BottomMainA (westbound)", "TunnelLongPark");

        assertNotNull(edge, "the frozen railway no longer runs BottomMainA (westbound) -> TunnelLongPark");

        return edge;
    }

    /** Whether a refusal carries the "N squares still have no length" note, for any N. */
    private static boolean saysSquaresHaveNoLength(String refusal)
    {
        for (int n = 1; n <= 40; n++)
        {
            if (refusal.contains(I18n.f("autolayout.errorBerthApproachPartlyUnmeasured", n))) return true;
        }

        return false;
    }
}
