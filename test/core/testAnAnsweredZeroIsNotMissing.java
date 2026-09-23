package core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
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
