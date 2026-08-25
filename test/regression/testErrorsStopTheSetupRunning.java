package regression;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * An error stops the setup running, and everything that offers to run it asks the same question.
 *
 * Adam, OB-090: "the setup refuses to run when there are errors.  it runs on warnings, but on errors
 * it should say fix it."
 *
 * He was right about the rule and I had written the opposite into the ticket. Starting autonomy IS
 * refused on any ERROR finding - that is OB-057, and refuseAutonomyStartWhileBroken has done it ever
 * since. What had never happened is that the things which OFFER to start it were told. The diagram
 * strip decided what to show from hasBlockingProblems(), which asks the GRAPH only - scissors
 * crossings, unaddressed switches, unpaired links - and knows nothing of the CHECKS. Four unnamed
 * stations are four errors and no blocking problem, so the strip went on showing a live Start button
 * while every press of it was refused with a dialog.
 *
 * That is the OB-057 shape a third time: "it says there are errors, but the start autonomy button is
 * still visible." The fix is not another guard at another press site - it is that there is now ONE
 * definition of broken, AutonomySession.errorCount(), which both the refusal and the strip ask.
 *
 * **What this fixture can and cannot show.** The sample layout is parsed with no Central Station on
 * the other end, so every switch on it is unaddressed and all 79 of its errors are blocking graph
 * problems. There is therefore no way to reach the state "graph builds cleanly, setup still must not
 * run" on this fixture, and a test that asserted hasBlockingProblems() were false would be asserting
 * something about the network being down. So the gap is shown as a DELTA instead, which is stronger
 * anyway: a mutation that adds an error WITHOUT adding a blocking problem proves there are errors the
 * narrow question cannot see, whatever the starting counts happen to be.
 *
 * Every assertion here is a delta for the same reason. What the sample layout contains today is not
 * the subject, and a test that pins it becomes a test about the fixture the first time it is edited.
 */
public class testErrorsStopTheSetupRunning
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * There are errors the blocking question cannot see, which is why the strip must not ask it.
     *
     * The mutation is a name, so nothing about the TRACK changes: the blocking count must come back
     * identical while the error count goes up by one. An affordance deciding from the blocking count
     * is blind to exactly this, and this is the state Adam was in with his four unnamed stations.
     */
    @Test
    public void testAnUnnamedStationIsAnErrorNoBlockingProblemAccountsFor() throws Exception
    {
        AutonomySession session = openOnACopy();

        int errorsBefore = session.errorCount();
        int blockingBefore = blockingProblems(session);

        TileKey station = aNamedStation(session);

        // THE MUTATION: take a station's name away.  An ERROR, and not a blocking problem.
        session.getStore().setPointName(station, "");
        session.rebuild();

        assertEquals(session.errorCount(), errorsBefore + 1,
            "unnaming a station did not add exactly one error, so either the check is not running or "
            + "the mutation disturbed something else - and both make the rest of this meaningless");

        assertEquals(blockingProblems(session), blockingBefore,
            "the blocking count moved when only a NAME changed.  If that is so this mutation is not "
            + "the clean demonstration it is meant to be");

        assertTrue(session.hasErrors(),
            "the setup has an error and will not admit it, so every affordance that asks this goes "
            + "on offering to start something the refusal will decline (OB-090)");

        // The heart of it: an error arrived that no blocking problem accounts for.
        assertTrue(session.errorCount() > blockingProblems(session),
            "there is now an error that is NOT a blocking problem, and the two counts say otherwise. "
            + "hasBlockingProblems() is what the diagram strip used to decide from, and this is the "
            + "gap that let it show a live Start button over a setup that would not start.  errors="
            + session.errorCount() + " blocking=" + blockingProblems(session));

        // And back again: the error is a reason, not a latch.
        session.getStore().setPointName(station, "Named Again");
        session.rebuild();

        assertEquals(session.errorCount(), errorsBefore,
            "naming the station again did not clear the error it caused");
    }

    /**
     * The count the strip shows and the count the refusal uses are the same count.
     *
     * They were not, which is the whole of OB-090, and they were not because each side counted for
     * itself. Nothing in the compiler stops that recurring - two loops over check() that filter
     * differently look identical at a glance - so the equality is asserted rather than assumed.
     */
    @Test
    public void testTheErrorCountIsExactlyTheErrorFindings() throws Exception
    {
        AutonomySession session = openOnACopy();

        // Something of our own to count, so this is not the trivially-equal case.
        session.getStore().setPointName(aNamedStation(session), "");
        session.rebuild();

        int counted = 0;

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) counted++;
        }

        assertTrue(counted > 0, "the fixture was supposed to have an error in it by now");

        assertEquals(session.errorCount(), counted,
            "errorCount() and the list the editor shows disagree, so the number on the diagram is "
            + "not the number of things the editor will make you fix (OB-090)");
    }

    /**
     * Warnings and notices are not errors, which is the other half of Adam's rule.
     *
     * "it runs on warnings." Worth its own test because the cheap way to fix OB-090 - have the strip
     * ask whether the checks found ANYTHING - passes every assertion in the two tests above and
     * quietly makes a railway with one unlabelled siding unstartable. The sample layout carries far
     * more soft findings than errors, so an errorCount() that had swallowed them would be obvious
     * here.
     */
    @Test
    public void testWarningsAndNoticesAreNotCountedAsErrors() throws Exception
    {
        AutonomySession session = openOnACopy();

        int errors = 0;
        int soft = 0;

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) errors++;
            else soft++;
        }

        assertTrue(soft > 0,
            "the sample layout is expected to carry warnings and notices; with none there is nothing "
            + "to prove here");

        assertEquals(session.errorCount(), errors,
            "errorCount() is counting things that are not errors.  " + soft + " of the findings on "
            + "this layout are warnings or notices, and a setup carrying only those has to start");

        assertTrue(session.errorCount() < errors + soft,
            "every finding is being counted as an error, so nothing could ever be merely worth "
            + "looking at");
    }

    /**
     * How many problems would stop the graph being built.
     *
     * Counted from the graph and the reducer directly rather than through check(), because the point
     * of these tests is that the two are different questions - taking this number from the same place
     * the other one comes from would beg it.
     */
    private static int blockingProblems(AutonomySession session)
    {
        int blocking = 0;

        for (TileGraph.Problem problem : session.getGraph().getProblems())
        {
            if (problem.isBlocking()) blocking++;
        }

        for (TileGraph.Problem problem : session.getReducer().getProblems())
        {
            if (problem.isBlocking()) blocking++;
        }

        return blocking;
    }

    /**
     * A station that currently has a name, so that taking it away is a change.
     */
    private static TileKey aNamedStation(AutonomySession session)
    {
        for (Map.Entry<TileKey, GraphReducer.ReducedPoint> entry
            : session.getReducer().getPoints().entrySet())
        {
            GraphReducer.ReducedPoint point = entry.getValue();

            if (point.isStation() && point.getName() != null && !point.getName().trim().isEmpty())
            {
                return entry.getKey();
            }
        }

        fail("the sample layout has no named station, so there is nothing to unname");

        return null;
    }

    /**
     * A session over a throwaway copy of the setup, because these tests write.
     */
    private static AutonomySession openOnACopy() throws Exception
    {
        File folder = new File(System.getProperty("user.dir"), "test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        File temp = File.createTempFile("tc-errors", "");

        assertTrue(temp.delete(), "making room for a directory of the same name");
        assertTrue(new File(temp, "config/autonomy").mkdirs(), "could not make the copy");

        File from = new File(folder, "config/autonomy");

        for (File one : from.listFiles())
        {
            if (one.isFile())
            {
                java.nio.file.Files.copy(one.toPath(),
                    new File(temp, "config/autonomy/" + one.getName()).toPath());
            }
        }

        temp.deleteOnExit();

        AutonomySession session = new AutonomySession(temp);

        assertTrue(session.isUsable(), "the sample layout ships a setup for this to open");

        session.open(pages);

        return session;
    }
}
