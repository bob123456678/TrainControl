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

    /**
     * A SETUP THAT LOADS NON-ATOMIC OVER UNMEASURED TRACK COMES UP ATOMIC (Adam, 2026-09-21).
     *
     * *"Yes, shut the file door too - just enable the setting and show a warning in the log."*
     *
     * The checkbox refuses the gesture, because somebody is there to read the refusal and is one
     * gesture from fixing it.  A file has nobody at it, and refusing the load would make a
     * configuration he already has unopenable - so the safe setting is written instead and the log
     * says why.  Turning atomic routes ON can never be the unsafe answer: atomic mode releases nothing
     * until a run ends.
     *
     * **THE CONTROL IS THE SECOND HALF.**  A rule that forced the setting on whatever the railway looks
     * like would take non-atomic mode away from an operator who has measured everything, and a test
     * that only checked the forcing would pass.  So this asks twice: once with a square short, where it
     * must be turned back on, and once with everything measured, where a deliberate OFF must be left
     * exactly as it is.
     *
     * MUTATION: drop the `layout.isAtomicRoutes()` early return and the second claim goes red (a setting
     * already on is "changed" every load, harmlessly, but the count then reads as a reason to log);
     * drop the `unmeasured <= 0` return and the second claim goes red properly - a measured railway has
     * its setting forced.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testTheDoorIsOpenWhenEverythingIsMeasuredAndShutWhenItIsNot")
    public void testALoadedSetupCannotRunNonAtomicOverUnmeasuredTrack() throws Exception
    {
        org.traincontrol.automation.Layout layout = model.getAutoLayout();

        assertNotNull(layout, "precondition: there is no layout, so there is nothing to load a setting into");

        boolean atomicWas = layout.isAtomicRoutes();

        TileKey switchSquare = null;

        for (TileKey tile : session.switchesALengthRuleReads())
        {
            switchSquare = tile;

            break;
        }

        if (switchSquare == null)
        {
            throw new SkipException("this configuration has no switch a length rule reads, so no square"
                + " can be left unmeasured for the claim below");
        }

        final TileKey short0 = switchSquare;

        try
        {
            // ONE SQUARE SHORT, and the setting as a loaded file would have left it.
            SwingUtilities.invokeAndWait(() -> session.setTileLength(short0, 0));

            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTrackIsUnmeasured());

            assertTrue(layout.isAtomicRoutes(),
                "a setup that turns atomic routes off has been loaded with " + short0 + " unmeasured,"
                + " and the railway is running non-atomic: every edge will be handed back as the head"
                + " passes it, with the train still standing on it, and nobody was at the door to be"
                + " told");

            // THE CONTROL: measured everywhere, a deliberate OFF is left alone.
            SwingUtilities.invokeAndWait(() -> session.setTileLength(short0, 1));

            assertTrue(session.squaresNeedingALength().isEmpty(),
                "precondition: putting the length back left " + session.squaresNeedingALength().size()
                + " squares still wanting one, so the control below is not about a measured railway");

            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTrackIsUnmeasured());

            assertFalse(layout.isAtomicRoutes(),
                "every square autonomy runs over has a length and the load turned atomic routes back on"
                + " anyway, which takes the setting away from an operator who has done the work - the"
                + " same failure the refusal at the checkbox is guarded against");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> session.setTileLength(short0, 1));

            layout.setAtomicRoutes(atomicWas);
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
