package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The road a standing train arrived along is kept, so its tail is the same after a save, a load or a rebuild.
 *
 * Adam, 2026-09-14, on WK7-B1: *"why not ask the user to specify the last sensor it crossed from a list of
 * possible sensors?  Then state will always be fully consistent."*  And: *"Go - build it."*
 *
 * **WK7-B1, which this closes.**  `Point.arrivedAlong` lived in memory only.  Every rebuild of the running
 * layout - every setup gesture, closing the autonomy editor - put a driven train back with its side and not
 * its road, so its tail stopped at the first fork again and roads it was lying across were offered.  A
 * restart did the same.  The road is now written with the point (`Point.toJSON`), read back by
 * `Layout.fromJSON`, and carried across a rebuild with the train (`TrainControlUI.whereTheTrainsAre` /
 * `putTheTrainsBack`).
 *
 * **The fixture is `testATailFollowsTheRouteItCameIn`'s shape on sensors of its own:** A -> J -> S with
 * C -> J making J a junction, the platform's approach one unit and the train three, so two units lie back
 * past J along the road it came in on.
 *
 * @author Adam
 */
public class testATailRouteIsKept
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Locomotive loc;
    private static Integer lengthWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();
        model = MarklinControlStation.init(null, true, false, false, false);
        loc = model.getLocByName(model.getLocList().get(0));
        lengthWas = loc.getTrainLength();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (loc != null) loc.setTrainLength(lengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Written with the point and read back: after a save and a load the tail still follows the road past J.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheRouteSurvivesASaveAndALoad() throws Exception
    {
        Layout layout = aTrainThatDroveAToS(2270);

        Layout loaded = Layout.fromJSON(layout.toJSON(), model);

        assertTrue(loaded.isValid(), "precondition: the saved layout does not load: " + loaded.getInvalidReason());
        assertTrue(loaded.getPoint("TK_S").getCurrentLocomotive() == loc,
            "precondition: the train is not standing at TK_S after the load");

        Map<Edge, Locomotive> covered = loaded.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(loaded.getEdge("TK_J", "TK_S")),
            "precondition: the platform's own approach is not covered after the load, so the walk did not start");

        assertTrue(covered.containsKey(loaded.getEdge("TK_A", "TK_J")),
            "after a save and a load the tail stops at the junction J again: the road the train came in on,"
            + " A -> J, is not claimed, so the file did not keep it (WK7-B1). Covered: " + covered.keySet());

        assertFalse(covered.containsKey(loaded.getEdge("TK_C", "TK_J")),
            "the tail was claimed along C -> J after the load, a road the train never used");
    }

    /**
     * Carried with the train across a rebuild that regenerates the layout from a setup which does not have it.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheRouteSurvivesARebuild() throws Exception
    {
        Layout layout = aTrainThatDroveAToS(2280);

        Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(layout);

        // THE REBUILD: the same railway regenerated with the train where the setup last had it and nothing said
        // about how it got there - which is what a setup gesture's rebuild hands `putTheTrainsBack`.
        org.json.JSONObject regenerated = new org.json.JSONObject(layout.toJSON());

        for (Object each : regenerated.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) each;
            point.remove("arrivedAlong");
            point.remove("arrivedFrom");
        }

        Layout built = Layout.fromJSON(regenerated.toString(), model);

        assertTrue(built.isValid(), "precondition: the regenerated layout does not load: " + built.getInvalidReason());

        TrainControlUI.putTheTrainsBack(built, standing, null);

        Map<Edge, Locomotive> covered = built.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(built.getEdge("TK_J", "TK_S")),
            "precondition: the platform's own approach is not covered after the rebuild, so the side did not come back");

        assertTrue(covered.containsKey(built.getEdge("TK_A", "TK_J")),
            "after a rebuild the tail stops at the junction J again - the train was put back with its side and"
            + " not its road, which is WK7-B1. Covered: " + covered.keySet());
    }

    /**
     * A layout with a standing train nobody has given a length still saves.
     *
     * Found 2026-09-14 while writing the capture claim in `testAPassingTrainMayStandAcrossThePoints`: `Point.toJSON`
     * unboxed the train's length, a length nobody has set is null, and `Layout.toJSON` threw - so every capture of
     * the running layout (closing the editor, the exit, a re-download) logged an exception and wrote nothing while
     * such a train stood anywhere.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testALayoutWithATrainOfNoLengthStillSaves() throws Exception
    {
        Layout layout = aTrainThatDroveAToS(2290);

        loc.setTrainLength(null);

        String saved;

        try
        {
            saved = layout.toJSON();
        }
        catch (NullPointerException thrown)
        {
            fail("the layout does not save while a train with no length stands on it - Point.toJSON unboxes the null"
                + " length, and every capture of the running layout fails with it", thrown);
            return;
        }

        assertTrue(saved.contains("TK_S") && saved.contains(loc.getName()),
            "the saved layout does not name the standing train: " + saved);
    }

    /**
     * A -> J -> S with C -> J, every edge measured, the platform's approach at one; a three-unit train standing at
     * S having driven A -> J -> S.
     *
     * The arrival is written as the arrival writes it (`Layout.executePath`: the side of the last edge and the
     * whole path) - `testATailFollowsTheRouteItCameIn` is the claim that a real run writes exactly that.
     *
     * @param s88 the first of four sensor numbers this fixture uses
     * @return the graph
     */
    private static Layout aTrainThatDroveAToS(int s88) throws Exception
    {
        Layout built = new Layout(model);

        // A speed the file accepts: a bare Layout's default is refused by `fromJSON`, and every claim here reads one back.
        built.setDefaultLocSpeed(30);

        built.createPoint("TK_A", true, model.newFeedback(s88, null).getName());
        built.createPoint("TK_C", true, model.newFeedback(s88 + 1, null).getName());
        built.createPoint("TK_J", false, model.newFeedback(s88 + 2, null).getName());
        built.createPoint("TK_S", true, model.newFeedback(s88 + 3, null).getName());

        built.createEdge("TK_A", "TK_J");
        built.createEdge("TK_C", "TK_J");
        built.createEdge("TK_J", "TK_S");

        built.getEdge("TK_A", "TK_J").setLength(3);
        built.getEdge("TK_C", "TK_J").setLength(3);
        built.getEdge("TK_J", "TK_S").setLength(1);
        built.getEdge("TK_J", "TK_S").setEntrySide("W");

        assertTrue(built.moveLocomotive(loc.getName(), "TK_S", false), "could not stand the train at TK_S");

        Point s = built.getPoint("TK_S");

        List<Edge> path = new ArrayList<>();
        path.add(built.getEdge("TK_A", "TK_J"));
        path.add(built.getEdge("TK_J", "TK_S"));

        s.setArrivedFrom(built.entrySideOf(built.getEdge("TK_J", "TK_S"), s));
        s.setArrivedAlong(path);

        loc.setTrainLength(3);

        assertTrue(built.edgesCoveredByStandingTrains().containsKey(built.getEdge("TK_A", "TK_J")),
            "precondition: before any save or rebuild the tail does not follow the road past J, so there is nothing"
            + " for either to lose");

        return built;
    }
}
