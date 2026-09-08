package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * Why the mid-journey turn is not narrowed by geometry: the measurement that stopped it.
 *
 * Adam, 2026-09-08: *"unless you were already emitting reversal commands, then according to the graph,
 * the existing behavior was correct as trains passed through those points.  let's make sure we are not
 * regressing."* He was right, and this is the measurement that says so rather than an argument.
 *
 * **His rule.** *"terminus: always reverse at the end.  reversing: only reverse if intended at the end
 * or when backing in somewhere per the path, don't reverse if passing onwards."*
 *
 * **The code already implements it, through the SPLIT.** `AutonomyBuilder` gives a may-turn square two
 * copies: a plain one, which is what a path passing onwards is routed through, and a turning one
 * carrying `reversing`, which is what a path BACKING IN is routed through. So `current.isReversing()`
 * on a built graph already means "this journey is backing in here", and turning there is exactly
 * "reverse when backing in per the path".
 *
 * **What the narrowing would have done.** I proposed turning only where the next leg leaves by the side
 * the train arrived on. Measured over this railway it names `BottomMainB (westbound, reverse)` - in by
 * E, out by N, four such pairs - a turning copy that has always turned trains and would have stopped.
 * The facings recorded in the setup were written under the old behaviour, so they would have disagreed
 * with the railway from that moment. The narrowing was reverted unshipped.
 *
 * The fixture that made the case for it called `setReversing(true)` on a square a straight path runs
 * through, which the builder cannot emit - a through square gets the plain copy. So the journey it
 * showed does not exist on a built railway.
 *
 * @author Adam
 */
public class testTheTurnRuleDoesNotChangeTheRealRailway
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // A COPY of the real layout, opened before the model, because init reads the layout preference
        // and would otherwise open the railway itself (OB-111).
        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        model = init(null, true, false, false, false);
        model.stop();

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        model.parseAuto(session.buildConfiguration());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * A reversing copy can be left by a side other than the one it is entered by.
     *
     * Which is what makes a geometric test for "does the next leg need the turn" wrong: it would read
     * such a copy as a straight run and stop turning there. The copies are listed when this fails, so
     * whoever proposes the narrowing again sees the counter-examples rather than the idea.
     *
     * @throws Exception on a failure to read the built graph
     */
    @Test
    public void testAReversingCopyCanBeLeftByAnotherSide() throws Exception
    {
        Layout built = model.getAutoLayout();

        assertNotNull(built, "the sample layout did not build, so this measured nothing");

        List<Point> reversing = new ArrayList<>();

        for (Point p : built.getPoints())
        {
            if (p.isReversing()) reversing.add(p);
        }

        assertTrue(reversing.size() > 0,
            "the built graph has no reversing copy at all, so this test cannot say anything about the"
            + " rule it exists to check. Either the sample layout has lost its may-turn squares or the"
            + " builder has stopped splitting them");

        List<String> wouldChange = new ArrayList<>();

        for (Point copy : reversing)
        {
            List<Edge> in = new ArrayList<>();
            List<Edge> out = new ArrayList<>();

            for (Edge e : built.getEdges())
            {
                if (e.getEnd() == copy) in.add(e);

                if (e.getStart() == copy) out.add(e);
            }

            for (Edge arriving : in)
            {
                String cameInBy = built.entrySideOf(arriving, copy);

                for (Edge leaving : out)
                {
                    String leavesBy = built.entrySideOf(leaving, copy);

                    // The rule turns when it cannot tell, so an unknown is not a change.
                    if (cameInBy == null || leavesBy == null) continue;

                    if (!cameInBy.equals(leavesBy))
                    {
                        wouldChange.add(copy.getName() + ": in by " + cameInBy + ", out by " + leavesBy);
                    }
                }
            }
        }

        // MEASURED 2026-09-08, and it is why the narrowing was reverted rather than shipped:
        // `BottomMainB (westbound, reverse)` is entered from the E and left to the N, and there are
        // four such pairs on this railway. A geometric "only turn if the next leg leaves by the side it came in"
        // test therefore stops turning trains at a copy that has always turned them, which is a
        // regression and not a fix - and the facings recorded in the setup were written under the old
        // behaviour, so they would disagree with the railway from that moment.
        assertTrue(wouldChange.size() > 0,
            "every reversing copy on this railway is now left by the side it is entered by. That was"
            + " NOT true on 2026-09-08 - BottomMainB (westbound, reverse) went in by E and out by N -"
            + " so either the layout or the builder has changed. The narrowing this test blocked may"
            + " now be safe: re-measure before assuming either way");
    }
}
