package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A route refusal names the square, not the direction copy the builder made of it.
 *
 * Adam's ruling on the translations: the whole "copy" business *"needs to be masked from the user"*.  On
 * his own railway, on 2026-09-13, EN57-947 was refused BottomMainB with *"Disallowed because 75 407 DB is
 * standing across Tunnel (southbound) -> BottomMainAPre (eastbound)"* - the headings are how the builder
 * tells the directions of one square apart, and nothing on the diagram carries them.
 *
 * **Named the way the builder names copies**, so the claims are about the words a person would actually
 * read: a station switched out of service at the end of a route, and one part-way along it.  And a control
 * that a name which only LOOKS like a copy - a parenthesis that is not a heading - keeps its parenthesis.
 *
 * @author Adam
 */
public class testARefusalNamesTheSquare
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Locomotive loc;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        loc = model.getLocByName(model.getLocList().get(0));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * A destination out of service is named by its square.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAnInactiveStationIsNamedWithoutItsHeading() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RN Start (westbound)", true, model.newFeedback(2310, null).getName());
        layout.createPoint("RN Platform (eastbound, reverse)", true, model.newFeedback(2311, null).getName());
        layout.createEdge("RN Start (westbound)", "RN Platform (eastbound, reverse)");

        layout.getPoint("RN Platform (eastbound, reverse)").setActive(false);

        String said = refusalOf(layout, "RN Start (westbound)", "RN Platform (eastbound, reverse)");

        assertTrue(said.contains("RN Platform"),
            "precondition: the refusal does not name the station at all, so nothing below is about how it"
            + " names it: " + said);

        assertFalse(said.contains("eastbound") || said.contains("reverse"),
            "the refusal names the builder's copy of the station, heading and all, rather than the"
            + " square: \"" + said + "\". Adam: the whole copy business \"needs to be masked from the"
            + " user\"");
    }

    /**
     * A square out of service part-way along the route is named by its square too.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAnInactivePointOnTheWayIsNamedWithoutItsHeading() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RN From (northbound)", true, model.newFeedback(2320, null).getName());
        layout.createPoint("RN Junction (southbound)", false, model.newFeedback(2321, null).getName());
        layout.createPoint("RN To (eastbound)", true, model.newFeedback(2322, null).getName());
        layout.createEdge("RN From (northbound)", "RN Junction (southbound)");
        layout.createEdge("RN Junction (southbound)", "RN To (eastbound)");

        layout.getPoint("RN Junction (southbound)").setActive(false);

        String said = refusalOf(layout, "RN From (northbound)", "RN Junction (southbound)", "RN To (eastbound)");

        assertTrue(said.contains("RN Junction"),
            "precondition: the refusal does not name the square out of service: " + said);

        assertFalse(said.contains("southbound"),
            "the refusal names the builder's copy of the square it cannot pass, heading and all: \""
            + said + "\"");
    }

    /**
     * A station too short for the train is named by its square too (TDR-C5, the length refusals).
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAStationTooShortIsNamedWithoutItsHeading() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RN Begin (westbound)", true, model.newFeedback(2340, null).getName());
        layout.createPoint("RN Short (eastbound, reverse)", true, model.newFeedback(2341, null).getName());
        layout.createEdge("RN Begin (westbound)", "RN Short (eastbound, reverse)");

        layout.getPoint("RN Short (eastbound, reverse)").setMaxTrainLength(2);

        Integer was = loc.getTrainLength();

        try
        {
            loc.setTrainLength(5);

            String said = refusalOf(layout, "RN Begin (westbound)", "RN Short (eastbound, reverse)");

            assertTrue(said.contains("RN Short"),
                "precondition: the refusal does not name the station: " + said);

            assertFalse(said.contains("eastbound") || said.contains("reverse"),
                "the length refusal names the builder's copy of the station, heading and all: \"" + said + "\"");
        }
        finally
        {
            loc.setTrainLength(was);
        }
    }

    /**
     * The control: a name with a parenthesis that is not a heading keeps it.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAParenthesisThatIsNotAHeadingIsKept() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RN Origin", true, model.newFeedback(2330, null).getName());
        layout.createPoint("RN Yard (old)", true, model.newFeedback(2331, null).getName());
        layout.createEdge("RN Origin", "RN Yard (old)");

        layout.getPoint("RN Yard (old)").setActive(false);

        String said = refusalOf(layout, "RN Origin", "RN Yard (old)");

        assertTrue(said.contains("RN Yard (old)"),
            "a station somebody really did call \"RN Yard (old)\" lost its name in the refusal: \"" + said
            + "\" - only the builder's four headings are machinery");
    }

    /** Asks the railway for the route through these points and returns what it said when it refused. */
    private static String refusalOf(Layout layout, String... names)
    {
        List<Edge> path = new ArrayList<>();

        for (int i = 1; i < names.length; i++)
        {
            Edge edge = layout.getEdge(names[i - 1], names[i]);

            assertTrue(edge != null, "precondition: the fixture has no edge " + names[i - 1] + " -> " + names[i]);

            path.add(edge);
        }

        assertFalse(layout.isPathClear(path, loc, false),
            "precondition: the route was not refused, so there is no refusal to read");

        return String.valueOf(Layout.getLastError());
    }
}
