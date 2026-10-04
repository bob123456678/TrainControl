package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;

/**
 * The setup check is worked out once per event, and the configuration build works out its names once (speed, Adam,
 * 2026-10-04: *"Do 1 and 2"*).
 *
 * Measured on his railway before this: one click on a switch in the autonomy editor ran the setup check twenty times
 * over five events, and a track editor Save twenty-four times over seven - the findings list, then whether there are
 * errors, then the strip, the viewer and the Run buttons, each asking again of a setup nobody had touched.  Every check
 * builds the whole configuration to inspect it, and the build sorted every Point on the railway again for each copy it
 * named.  Together that was two thirds of the click and of the Save; on an N100-class processor the click took 1.1 s.
 *
 * **What may be kept, and for how long.**  The findings are kept for the rest of the event that asked, and only while
 * everything the check reads is as it was: a new event always checks afresh, and a change made inside the event - to
 * the setup, a train's length, a label on the diagram, a train on the railway - is answered for.  Each of those has a
 * claim here, because the check reads each of them from somewhere other than the setup, and a key missing one would
 * hand back findings about a railway that has just changed.
 *
 * On the frozen copy of his railway, whose findings move with each change asked of here (see each precondition).
 *
 * @author Adam
 */
public class testTheFindingsAreWorkedOutOncePerEvent
{
    private static support.Scenario scenario;

    private static AutonomySession session;

    /** The session's train-length and running-railway sources as the scenario left them, put back after each claim. */
    private static Object lengthsAsFound, railwayAsFound;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, before the model is built (OB-111): the snapshot is a checked-in
        // copy, and the copy is what is written.
        scenario = support.Scenario.open("live-snapshot");

        scenario.getModel().stop();

        session = scenario.getSession();

        lengthsAsFound = source("trainLengths").get(session);

        railwayAsFound = source("runningLayout").get(session);
    }

    private static java.lang.reflect.Field source(String name) throws NoSuchFieldException
    {
        java.lang.reflect.Field field = AutonomySession.class.getDeclaredField(name);

        field.setAccessible(true);

        return field;
    }

    /** Puts back the session's sources as the scenario left them. */
    private static void putBackTheSources() throws ReflectiveOperationException
    {
        source("trainLengths").set(session, lengthsAsFound);

        source("runningLayout").set(session, railwayAsFound);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    /** Runs something as one event on the event thread, and hands back what it returned. */
    private static <T> T inOneEvent(Callable<T> what) throws Exception
    {
        final List<T> got = new ArrayList<>();

        final Exception[] failed = new Exception[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                got.add(what.call());
            }
            catch (Exception e)
            {
                failed[0] = e;
            }
        });

        if (failed[0] != null) throw failed[0];

        return got.get(0);
    }

    /** What a list of findings says, finding by finding, for comparing two lists by content. */
    private static List<String> said(List<AutonomyChecks.Finding> findings)
    {
        List<String> out = new ArrayList<>();

        for (AutonomyChecks.Finding f : findings)
        {
            out.add(f.getSeverity() + " " + f.getMessageKey() + " " + f.getSubject() + " " + f.getTile() + " "
                + f.getCount() + " " + f.getDetail() + " " + f.getThird() + " " + f.getRelated());
        }

        return out;
    }

    /** How many findings carry a message. */
    private static int countOf(List<AutonomyChecks.Finding> findings, String messageKey)
    {
        int n = 0;

        for (AutonomyChecks.Finding f : findings) if (messageKey.equals(f.getMessageKey())) n++;

        return n;
    }

    /**
     * A build, and each map by name, works out the names of the railway's squares once - not once more for every copy
     * of a split square it names.
     *
     * Counted through the one input the name list reads once per working-out: the names of squares on pages out of
     * autonomy, handed to the builder as a map whose entries are walked each time.
     *
     * MUTATION: have nodeName work the names out again for itself, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testABuildWorksOutItsNamesOnce() throws Exception
    {
        final int[] walked = {0};

        AutonomyBuilder builder = session.builder(new AutonomyBuilder.Globals());

        java.lang.reflect.Field field = AutonomyBuilder.class.getDeclaredField("namesOutOfPlay");

        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<TileKey, String> outOfPlay = (Map<TileKey, String>) field.get(builder);

        builder.withNamesOutOfPlay(new java.util.LinkedHashMap<TileKey, String>(outOfPlay)
        {
            @Override
            public java.util.Set<Map.Entry<TileKey, String>> entrySet()
            {
                walked[0]++;

                return super.entrySet();
            }
        });

        String built = builder.build();

        assertTrue(built.contains("bound"), "precondition: the frozen railway built with no square split into copies, so no"
            + " copy was named and this asks nothing");

        assertEquals(walked[0], 1, "one build worked out the railway's names " + walked[0] + " times - again for each copy"
            + " it named");

        walked[0] = 0;

        builder.tilesByName();

        assertEquals(walked[0], 1, "tilesByName worked out the railway's names " + walked[0] + " times");

        walked[0] = 0;

        builder.edgesByName();

        assertEquals(walked[0], 1, "edgesByName worked out the railway's names " + walked[0] + " times");
    }

    /**
     * Two checks in one event are one check: the second hands back the first's findings, in a list of its own.
     *
     * MUTATION: work the findings out every time, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testOneEventChecksOnce() throws Exception
    {
        List<List<AutonomyChecks.Finding>> two = inOneEvent(() ->
        {
            List<List<AutonomyChecks.Finding>> out = new ArrayList<>();

            out.add(session.check());
            out.add(session.check());

            return out;
        });

        assertFalse(two.get(0).isEmpty(), "precondition: the frozen railway has no findings, so nothing could be kept");

        assertSame(two.get(1).get(0), two.get(0).get(0), "the second check in one event worked the findings out again");

        assertNotSame(two.get(1), two.get(0), "two checks handed out one list, so one caller's change is another's");

        two.get(1).clear();

        assertEquals(inOneEvent(() -> session.check()).size(), two.get(0).size(),
            "a caller emptying its list emptied the next check's");
    }

    /**
     * A new event checks afresh: nothing kept outlives the event that asked.
     *
     * MUTATION: keep the findings across events, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testEachEventChecksAfresh() throws Exception
    {
        List<AutonomyChecks.Finding> first = inOneEvent(() -> session.check());

        List<AutonomyChecks.Finding> second = inOneEvent(() -> session.check());

        assertFalse(first.isEmpty(), "precondition: the frozen railway has no findings");

        assertNotSame(second.get(0), first.get(0), "a second event was handed the first event's findings");

        assertEquals(said(second), said(first), "precondition: nothing changed between the events, yet the findings did");
    }

    /**
     * A change to the setup inside the event is checked: a train taken off a station in the setup itself, with nothing
     * re-derived, and the train's warning goes.
     *
     * MUTATION: leave the setup out of what the check is said to read, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testASetupChangeInTheEventIsChecked() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            session.setTrainLengthSource(name -> 0);

            List<List<AutonomyChecks.Finding>> got = inOneEvent(() ->
            {
                List<List<AutonomyChecks.Finding>> out = new ArrayList<>();

                out.add(session.check());

                // THE SETUP ITSELF, as a write that re-derives nothing does it
                org.json.JSONObject points = session.getStore().getConfiguration(
                    session.getStore().getActiveConfiguration()).getJSONObject("points");

                for (String id : points.keySet())
                {
                    if (points.getJSONObject(id).has("loc"))
                    {
                        points.getJSONObject(id).remove("loc");

                        break;
                    }
                }

                out.add(session.check());

                return out;
            });

            List<AutonomyChecks.Finding> fresh = inOneEvent(() -> session.check());

            assertNotEquals(countOf(fresh, "autosetup.ui.checkNoTrainLength"),
                countOf(got.get(0), "autosetup.ui.checkNoTrainLength"),
                "precondition: taking a placed train of no length off the setup changed none of its warnings");

            assertEquals(said(got.get(1)), said(fresh), "a train taken off the setup in the same event was not checked: the"
                + " findings are those from before");
        }
        finally
        {
            putBackTheSources();

            session.restoreSetup(asFound);
        }
    }

    /**
     * A train's length set inside the event is checked: the warning that it has none goes.
     *
     * MUTATION: leave the lengths out of what the check is said to read, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testALengthChangeInTheEventIsChecked() throws Exception
    {
        final int[] length = {0};

        try
        {
            session.setTrainLengthSource(name -> length[0]);

            List<List<AutonomyChecks.Finding>> got = inOneEvent(() ->
            {
                List<List<AutonomyChecks.Finding>> out = new ArrayList<>();

                out.add(session.check());

                length[0] = 100;

                out.add(session.check());

                return out;
            });

            assertTrue(countOf(got.get(0), "autosetup.ui.checkNoTrainLength") > 0,
                "precondition: no placed train is without a length, so setting one changes nothing");

            assertEquals(countOf(got.get(1), "autosetup.ui.checkNoTrainLength"), 0, "the trains given a length in the same"
                + " event are still said to have none");
        }
        finally
        {
            putBackTheSources();
        }
    }

    /**
     * A label written on the diagram inside the event is checked: a caption's own square given a label of its own is
     * covered, and said so.
     *
     * MUTATION: leave the diagram's squares out of what the check is said to read, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testALabelChangeInTheEventIsChecked() throws Exception
    {
        LayoutDiagramComponent square = null;

        for (Map.Entry<TileKey, TileKey> caption : session.getStore().getCaptions().entrySet())
        {
            for (LayoutDiagram page : scenario.getPages())
            {
                if (square != null || !page.getName().equals(caption.getKey().getPage())) continue;

                LayoutDiagramComponent c = page.getComponent(caption.getKey().getX(), caption.getKey().getY());

                if (c != null && (c.getLabel() == null || c.getLabel().trim().isEmpty())) square = c;
            }
        }

        assertNotNull(square, "precondition: no caption stands on a square of no label");

        final LayoutDiagramComponent labelled = square;

        final String was = labelled.getLabel();

        try
        {
            List<List<AutonomyChecks.Finding>> got = inOneEvent(() ->
            {
                List<List<AutonomyChecks.Finding>> out = new ArrayList<>();

                out.add(session.check());

                labelled.setLabel("Covered");

                out.add(session.check());

                return out;
            });

            assertEquals(countOf(got.get(1), AutonomyChecks.CAPTION_COVERED),
                countOf(got.get(0), AutonomyChecks.CAPTION_COVERED) + 1, "a caption's square labelled in the same event"
                + " is not said to be covered");
        }
        finally
        {
            labelled.setLabel(was);
        }
    }

    /**
     * A train moved on the running railway inside the event is checked: a train of no length moved from one station to
     * another is warned about where it now stands.
     *
     * MUTATION: leave the railway's trains out of what the check is said to read, and this fails.
     *
     * @throws Exception from the event thread or the build
     */
    @Test
    public void testATrainMovedInTheEventIsChecked() throws Exception
    {
        final Layout running = scenario.build();

        List<String> free = new ArrayList<>();

        for (org.traincontrol.automation.Point p : running.getPoints())
        {
            if (p.isDestination() && p.getCurrentLocomotive() == null) free.add(p.getName());
        }

        assertTrue(free.size() >= 2, "precondition: the frozen railway has fewer than two free stations: " + free);

        final String train = scenario.getModel().getLocList().get(0);

        try
        {
            session.setTrainLengthSource(name -> 0);

            session.setRunningLayoutSource(() -> running);

            assertTrue(running.moveLocomotive(train, free.get(0), false), "precondition: " + train + " could not be put on "
                + free.get(0));

            List<List<AutonomyChecks.Finding>> got = inOneEvent(() ->
            {
                List<List<AutonomyChecks.Finding>> out = new ArrayList<>();

                out.add(session.check());

                if (!running.moveLocomotive(train, free.get(1), false)) return null;

                out.add(session.check());

                return out;
            });

            assertNotNull(got, "precondition: " + train + " could not be moved to " + free.get(1));

            List<AutonomyChecks.Finding> fresh = inOneEvent(() -> session.check());

            assertNotEquals(said(fresh), said(got.get(0)), "precondition: moving a train of no length from " + free.get(0)
                + " to " + free.get(1) + " changed none of the findings");

            assertEquals(said(got.get(1)), said(fresh), "a train moved from " + free.get(0) + " to " + free.get(1) + " in"
                + " the same event was not checked: the findings are those from before");
        }
        finally
        {
            putBackTheSources();
        }
    }
}
