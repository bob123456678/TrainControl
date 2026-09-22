package ui;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Atomic Routes cannot be switched off while autonomy's own track is unmeasured (Adam, 2026-09-21).
 *
 * Adam, shown that the only thing standing between an unmeasured railway and track handed back under a
 * moving train was a sentence in a tooltip: *"can we simply refuse it now if there are unmeasured
 * segments in what's walkable via the autonomy editor?  Limit it to logical edges that it prompts for,
 * and on active pages only."*
 *
 * **WHY IT IS UNSAFE.**  `Layout.tailHasProvablyPassed` returns true as soon as the path it is asked
 * about has no measured leg at all - the honest answer for a railway with lengths on its platforms and
 * nowhere else - and non-atomic mode releases an edge the moment that is true.  So with nothing
 * measured every edge is handed back as the head passes it, train still lying over it, and the next
 * dispatch is routed onto occupied track.  Atomic mode releases nothing until the run ends, so it does
 * not care.
 *
 * **THE QUESTION IS THE EDITOR'S OWN.**  `squaresNeedingALength` is what the Unmeasured Track display
 * highlights and what Mass Assign Lengths offers to fill in: the pieces a length rule reads, the
 * switches it reads, and the squares two roads share.  Track nothing walks over is not in it, and the
 * pages autonomy takes no notice of are not in the graph those legs are built from - which is the
 * "active pages only" half, and it needs no code of its own.
 *
 * **THE CONTROL IS THE FIRST CLAIM, and it is the one that could go wrong.**  A refusal that fires
 * whatever the railway looks like would pass a test that only measures the refusal, and it would take
 * a feature away from an operator who has measured everything - which is the failure Adam's standing
 * rule is about ("he would rather have no check than one that refuses something legal").  So this
 * measures every square first and requires the door to be OPEN, and only then takes one length away.
 *
 * MUTATION: make `whyNonAtomicRoutesAreRefused` return the message unconditionally and the first claim
 * goes red; make it return null unconditionally and the second does.
 *
 * @author Adam
 */
public class testNonAtomicRoutesNeedTheirLengths
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static TrainControlUI ui;

    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the door is a checkbox on a window, and a window needs a display");
        }

        // THE FROZEN COPY of the operator's railway, not the railway he is operating (OB-111): this is
        // about which squares have lengths, and his own layout's lengths move as he measures them.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);

        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so the door this is about does not exist");

        settle();
        settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "the sandbox copy holds no autonomy setup");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Measured everywhere, the door is open; one length short, it is refused and says how many.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheDoorIsOpenWhenEverythingIsMeasuredAndShutWhenItIsNot() throws Exception
    {
        Set<TileKey> everySquare = everySquareOfTheGraph();

        assertFalse(everySquare.isEmpty(), "precondition: the graph has no squares to measure");

        SwingUtilities.invokeAndWait(() ->
        {
            for (TileKey tile : everySquare) session.setTileLength(tile, 1);
        });

        assertTrue(session.squaresNeedingALength().isEmpty(),
            "precondition: measuring every square left " + session.squaresNeedingALength().size()
            + " still wanting a length, so the control below cannot show an open door: "
            + session.squaresNeedingALength());

        // THE CONTROL.  Everything measured, so nothing is in the way of switching atomic routes off.
        assertNull(ui.whyNonAtomicRoutesAreRefused(),
            "every square autonomy runs over has a length and the door still refuses, which takes the"
            + " setting away from an operator who has done the work: " + ui.whyNonAtomicRoutesAreRefused());

        // AND NOW ONE SQUARE SHORT.  A switch the length rule reads, because that is a square the
        // editor prompts for on its own - taking a length off one tile of a longer piece leaves the
        // piece measured, and the editor rightly asks nothing.
        TileKey switchSquare = null;

        for (TileKey tile : session.switchesALengthRuleReads())
        {
            switchSquare = tile;

            break;
        }

        if (switchSquare == null)
        {
            throw new SkipException("this configuration has no switch that a length rule reads, so there"
                + " is no square the editor prompts for on its own and the claim below would be about"
                + " the fixture rather than the door");
        }

        final TileKey short0 = switchSquare;

        try
        {
            SwingUtilities.invokeAndWait(() -> session.setTileLength(short0, 0));

            assertTrue(session.squaresNeedingALength().contains(short0),
                "precondition: taking the length off " + short0 + " did not put it back among the"
                + " squares the editor asks for, so the door below is being asked about nothing");

            String why = ui.whyNonAtomicRoutesAreRefused();

            assertNotNull(why,
                "one square autonomy runs over has no length and Atomic Routes can still be switched"
                + " off - so an edge will be handed back as the head passes it with the train still"
                + " standing on it, which is what the tooltip used to warn about and nothing enforced");

            assertTrue(why.contains("1"),
                "the refusal does not say how many squares are unmeasured, so the operator cannot tell"
                + " whether it is one square or the whole railway: " + why);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> session.setTileLength(short0, 1));
        }
    }

    /** Every square of the autonomy graph - its points and every square of every reduced edge. */
    private static Set<TileKey> everySquareOfTheGraph()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (session.getReducer() == null) return out;

        out.addAll(session.getReducer().getPoints().keySet());

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) out.add(step.getTile());
            }
        }

        return out;
    }

    /** Lets the event thread catch up, twice being what the window needs after init (OB-192). */
    private static void settle() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
