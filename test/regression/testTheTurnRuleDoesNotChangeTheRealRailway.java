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
 * Adam: "unless you were already emitting reversal commands, then according to the graph, the existing behavior was
 * correct as trains passed through those points.  let's make sure we are not regressing."  His rule: "terminus: always
 * reverse at the end.  reversing: only reverse if intended at the end or when backing in somewhere per the path, don't
 * reverse if passing onwards."
 *
 * **The code already implements it, through the SPLIT.**  `AutonomyBuilder` gives a may-turn square two copies: a plain
 * one, which a path passing onwards is routed through, and a turning one carrying `reversing`, which a path BACKING IN
 * is routed through.  So `current.isReversing()` on a built graph already means "this journey is backing in here".
 *
 * **What the narrowing would have done:** turning only where the next leg leaves by the side the train arrived on names
 * `BottomMainB (westbound, reverse)` (in by E, out by N, four such pairs), a turning copy that has always turned trains
 * and would have stopped, so the facings recorded in the setup would have disagreed with the railway.  The narrowing
 * was reverted unshipped.  The fixture that made the case for it called `setReversing(true)` on a square a straight
 * path runs through, which the builder cannot emit.
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
        // THE FROZEN SNAPSHOT of the real layout, opened before the model, because init reads the layout preference
        // (OB-111).  It once read a copy of the LIVE folder, and the battery went red when Adam turned a square from
        // may-turn to must-turn: a claim about what the builder does to his railway cannot change color because he
        // edited the railway.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();
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
     * A reversing copy can be left by a side other than the one it is entered by - MEASURED BY THE COMPASS: the side a
     * copy is left by is read as `entrySideOf(leaving, copy)`, which for an edge that STARTS at the copy falls to
     * `sideTowards`, the neighbor's compass position (OB-268).  So the compass does not say which side a reversing copy
     * is left by, and a geometric test for "does the next leg need the turn" would read such a copy as a straight run
     * and stop turning there.  It does not refute that a turning copy's only outgoing edges leave by the side the train
     * arrived from, which the builder guarantees in the metal and this ruler cannot see.  The copies are listed when
     * this fails, so whoever proposes the narrowing again sees the counter-examples.
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

        // MEASURED, and why the narrowing was reverted: `BottomMainB (westbound, reverse)` is entered from the E and
        // left to the N, four such pairs on this railway, so a geometric "only turn if the next leg leaves by the side
        // it came in" test stops turning trains at a copy that has always turned them, a regression whose facings in
        // the setup were written under the old behavior.
        assertTrue(wouldChange.size() > 0,
            "every reversing copy on this railway is now left by the side it is entered by. That was"
            + " NOT true on 2026-09-08 - BottomMainB (westbound, reverse) went in by E and out by N -"
            + " so either the layout or the builder has changed.  Do not measure the side a copy is left by with"
            + " entrySideOf - it falls to the compass for an edge that starts at the copy (OB-268)");
    }
}
