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
     * The refusing figures on his frozen railway are the railway's own refusals (TDA-C10, ADA-C4, ADD2-C8): MT-583's
     * five, each asked.
     *
     * - TopMainR1Inter gives 3, and LowerFront 4: on a route in from TopR1ParkShort, and from ParkingTrack12, measuring
     *   that figure, a train that long is admitted and one a unit longer is refused - `Layout.whyTooLongForThisRoute`.
     * - Tunnel, BottomMainA and BottomInnerOtherside have the notice and no figure: every way in measures at least the
     *   maximum.
     *
     * The notice reads the figure off the built railway; the refusal is the railway's.  Two rules stated apart, so asked
     * of each other.  Every route between the two squares is asked, not the one a shuffled search returns first.
     *
     * MUTATION: give the figure a unit high, or give it at a platform where it is not under the maximum, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheRefusingFigureIsTheRailwaysOwn() throws Exception
    {
        java.util.Map<String, Integer> notices = runInNotices();

        assertEquals(notices.get("TopMainR1Inter"), Integer.valueOf(3), "TopMainR1Inter's refusing figure: " + notices);
        assertEquals(notices.get("LowerFront"), Integer.valueOf(4), "LowerFront's refusing figure: " + notices);

        for (String none : new String[] {"Tunnel", "BottomMainA", "BottomInnerOtherside"})
        {
            assertTrue(notices.containsKey(none), "precondition: " + none + " has no run-in notice: " + notices);

            assertEquals(notices.get(none), null, none + " is given a refusing figure, where every way in measures at least"
                + " its maximum: " + notices);
        }

        assertRefusedAbove("TopR1ParkShort", "TopMainR1Inter", 3);
        assertRefusedAbove("ParkingTrack12", "LowerFront", 4);
    }

    /** The run-in notices on his railway, by square name, each to its refusing figure or null where it gives none. */
    private java.util.Map<String, Integer> runInNotices()
    {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            String key = finding.getMessageKey();

            if (!"autosetup.ui.checkRunInShorterThanThePlatform".equals(key)
                && !"autosetup.ui.checkRunInShorterThanThePlatformRefused".equals(key)) continue;

            if (finding.getTile() == null) continue;

            out.put(session.getStore().getPointName(finding.getTile()), key.endsWith("Refused") ? finding.getThird() : null);
        }

        return out;
    }

    /**
     * On some route the railway runs from one station into another whose route in measures the figure, a train that long
     * is admitted and one a unit longer refused.  Every route between each pair of copies, up to forty.
     */
    private void assertRefusedAbove(String from, String into, int figure) throws Exception
    {
        Layout layout = layoutNow();

        int measuring = 0;
        boolean held = false;

        Integer was = train.getTrainLength();

        try
        {
            for (org.traincontrol.automation.Point start : layout.getPoints())
            {
                if (!start.isDestination() || !isCopyOf(start, from)) continue;

                for (org.traincontrol.automation.Point end : layout.getPoints())
                {
                    if (!end.isDestination() || !isCopyOf(end, into)) continue;

                    java.util.List<java.util.List<Edge>> found = new java.util.ArrayList<>();

                    for (int i = 0; i < 40; i++)
                    {
                        java.util.List<Edge> way = layout.bfs(start, end, found);

                        if (way == null || found.contains(way)) break;

                        found.add(way);

                        if (Layout.measuredRouteIn(way) != figure) continue;

                        measuring++;

                        train.setTrainLength(figure);

                        boolean admitted = Layout.whyTooLongForThisRoute(way, train) == null;

                        train.setTrainLength(figure + 1);

                        if (admitted && Layout.whyTooLongForThisRoute(way, train) != null) held = true;
                    }
                }
            }
        }
        finally
        {
            train.setTrainLength(was);
        }

        assertTrue(measuring > 0, "no route the railway runs from " + from + " into " + into + " measures the notice's"
            + " figure, " + figure);

        assertTrue(held, "on no route from " + from + " into " + into + " measuring " + figure + " is a train of " + figure
            + " admitted and one of " + (figure + 1) + " refused, as the notice says");
    }

    /** Whether a point is the named square or one of its copies. */
    private static boolean isCopyOf(org.traincontrol.automation.Point point, String name)
    {
        return point.getName().equals(name) || point.getName().startsWith(name + " (");
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
