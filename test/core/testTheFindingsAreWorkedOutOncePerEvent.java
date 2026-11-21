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
import support.Reflect;

/**
 * The setup check is worked out once until something it reads changes (OB-459), and the configuration build works out
 * its names once.
 *
 * The findings are kept, across events, only while everything the check reads is as it was: a change in a later event
 * (the setup, a train's length, a label on the diagram, a train on the railway, stale pages, the language, what only a
 * rebuild reads, an index derived anew) is answered for.  Each has a claim here, because a key missing one would hand back findings about a railway that has changed.
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
        // The sandbox is opened inside Scenario.open, before the model is built (OB-111).
        scenario = support.Scenario.open("live-snapshot");

        scenario.getModel().stop();

        session = scenario.getSession();

        lengthsAsFound = source("trainLengths").get(session);

        railwayAsFound = source("runningLayout").get(session);
    }

    private static java.lang.reflect.Field source(String name) throws NoSuchFieldException
    {
        java.lang.reflect.Field field = Reflect.field(AutonomySession.class, name);

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

    /** The findings in one event, then in a later one, after this change made in an event of its own. */
    private static List<List<AutonomyChecks.Finding>> acrossEvents(Callable<?> change) throws Exception
    {
        List<List<AutonomyChecks.Finding>> out = new ArrayList<>();

        out.add(inOneEvent(() -> session.check()));

        inOneEvent(change);

        out.add(inOneEvent(() -> session.check()));

        return out;
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
     * A build, and each map by name, works out the names of the railway's squares once, not once more for every copy of
     * a split square it names.  Counted through the one input the name list reads once per working-out: the names of
     * squares on pages out of autonomy, handed to the builder as a map whose entries are walked each time.
     *
     * MUTATION: have nodeName work the names out again for itself, and this fails.
     */
    @Test
    public void testABuildWorksOutItsNamesOnce() throws Exception
    {
        final int[] walked = {0};

        AutonomyBuilder builder = session.builder(new AutonomyBuilder.Globals());

        java.lang.reflect.Field field = Reflect.field(AutonomyBuilder.class, "namesOutOfPlay");

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
     * A later event with nothing changed is handed the findings already worked out: one editor edit asks over many
     * events, from the editor's panel and the main window's strip.
     *
     * MUTATION: check afresh in every new event, and this fails.
     */
    @Test
    public void testALaterEventWithNothingChangedKeepsTheFindings() throws Exception
    {
        List<List<AutonomyChecks.Finding>> got = acrossEvents(() -> null);

        assertFalse(got.get(0).isEmpty(), "precondition: the frozen railway has no findings");

        assertSame(got.get(1).get(0), got.get(0).get(0), "a later event with nothing changed worked the findings out"
            + " again");
    }

    /**
     * A change to the setup in a later event is checked: a caption drawn on a square that has a label of its own, by
     * the setup's own write, which re-derives nothing, is said to be covered.  The setup alone, since a train taken off
     * it would change where the trains stand as well.
     *
     * MUTATION: leave the setup out of what the check is said to read, and this fails.
     */
    @Test
    public void testASetupChangeIsChecked() throws Exception
    {
        Map<TileKey, TileKey> captions = session.getStore().getCaptions();

        assertFalse(captions.isEmpty(), "precondition: the frozen railway has no captions, so no station to caption");

        final TileKey station = captions.values().iterator().next();

        TileKey labelled = null;

        for (LayoutDiagram page : scenario.getPages())
        {
            for (LayoutDiagramComponent c : page.getAll())
            {
                TileKey at = new TileKey(page.getName(), c.getX(), c.getY());

                if (labelled == null && c.getLabel() != null && !c.getLabel().trim().isEmpty() && !captions.containsKey(at))
                {
                    labelled = at;
                }
            }
        }

        assertNotNull(labelled, "precondition: no labelled square without a caption on the frozen railway");

        final TileKey on = labelled;

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            List<List<AutonomyChecks.Finding>> got = acrossEvents(() ->
            {
                session.getStore().setCaption(on, station);

                return null;
            });

            assertEquals(countOf(got.get(1), AutonomyChecks.CAPTION_COVERED),
                countOf(got.get(0), AutonomyChecks.CAPTION_COVERED) + 1, "a caption drawn on " + on + ", a labelled"
                + " square, is not said to be covered");
        }
        finally
        {
            session.restoreSetup(asFound);
        }
    }

    /**
     * A train's length set in a later event is checked: the warning that it has none goes.
     *
     * MUTATION: leave the lengths out of what the check is said to read, and this fails.
     */
    @Test
    public void testALengthChangeIsChecked() throws Exception
    {
        final int[] length = {0};

        try
        {
            session.setTrainLengthSource(name -> length[0]);

            List<List<AutonomyChecks.Finding>> got = acrossEvents(() -> length[0] = 100);

            assertTrue(countOf(got.get(0), "autosetup.ui.checkNoTrainLength") > 0,
                "precondition: no placed train is without a length, so setting one changes nothing");

            assertEquals(countOf(got.get(1), "autosetup.ui.checkNoTrainLength"), 0, "the trains given a length are still"
                + " said to have none");
        }
        finally
        {
            putBackTheSources();
        }
    }

    /**
     * A label written on the diagram in a later event is checked: a caption's own square given a label of its own is
     * covered, and said so.
     *
     * MUTATION: leave the diagram's squares out of what the check is said to read, and this fails.
     */
    @Test
    public void testALabelChangeIsChecked() throws Exception
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
            List<List<AutonomyChecks.Finding>> got = acrossEvents(() ->
            {
                labelled.setLabel("Covered");

                return null;
            });

            assertEquals(countOf(got.get(1), AutonomyChecks.CAPTION_COVERED),
                countOf(got.get(0), AutonomyChecks.CAPTION_COVERED) + 1, "a caption's square labelled is not said to be"
                + " covered");
        }
        finally
        {
            labelled.setLabel(was);
        }
    }

    /**
     * A train moved on the running railway in a later event is checked: a train of no length moved from one station to
     * another is warned about where it now stands.
     *
     * MUTATION: leave the railway's trains out of what the check is said to read, and this fails.
     */
    @Test
    public void testATrainMovedIsChecked() throws Exception
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

            final boolean[] moved = new boolean[1];

            List<List<AutonomyChecks.Finding>> got = acrossEvents(() -> moved[0] = running.moveLocomotive(train,
                free.get(1), false));

            assertTrue(moved[0], "precondition: " + train + " could not be moved to " + free.get(1));

            // Off the event thread nothing is kept, so this is worked out afresh
            List<AutonomyChecks.Finding> fresh = session.check();

            assertNotEquals(said(fresh), said(got.get(0)), "precondition: moving a train of no length from " + free.get(0)
                + " to " + free.get(1) + " changed none of the findings");

            assertEquals(said(got.get(1)), said(fresh), "a train moved from " + free.get(0) + " to " + free.get(1) + " was"
                + " not checked: the findings are those from before");
        }
        finally
        {
            putBackTheSources();
        }
    }

    /**
     * Pages marked stale (a page renamed or deleted) are checked again: until `open`, a caption on a page these objects
     * do not know counts as drawn.
     *
     * MUTATION: leave `pagesStale` out of what the check is said to read, and this fails.
     */
    @Test
    public void testPagesMarkedStaleAreCheckedAgain() throws Exception
    {
        try
        {
            List<List<AutonomyChecks.Finding>> got = acrossEvents(() ->
            {
                session.markPagesStale();

                return null;
            });

            assertFalse(got.get(0).isEmpty(), "precondition: the frozen railway has no findings");

            assertNotSame(got.get(1).get(0), got.get(0).get(0), "pages marked stale were not checked again");
        }
        finally
        {
            Reflect.set(AutonomySession.class, session, "pagesStale", false);
        }
    }

    /**
     * A change of language is checked: some findings are worked out in words, so they are said in the new language.
     *
     * MUTATION: leave the language out of what the check is said to read, and this fails.
     */
    @Test
    public void testALanguageChangeIsChecked() throws Exception
    {
        java.util.Locale was = org.traincontrol.util.I18n.getLocale();

        java.util.Locale other = java.util.Locale.GERMAN.equals(was) ? java.util.Locale.FRENCH : java.util.Locale.GERMAN;

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            // The pages left out repeat sensors of "1 - Main": brought in, each repeat is a finding whose subject is words
            for (String page : new ArrayList<>(session.getStore().getExcludedPages()))
            {
                session.getStore().setPageExcluded(page, false);
            }

            List<List<AutonomyChecks.Finding>> got = acrossEvents(() ->
            {
                org.traincontrol.util.I18n.setLocale(other);

                return null;
            });

            // Off the event thread nothing is kept, so this is worked out afresh
            List<AutonomyChecks.Finding> fresh = session.check();

            assertNotEquals(said(fresh), said(got.get(0)), "precondition: no finding is worked out in words, so the"
                + " language changes none");

            assertEquals(said(got.get(1)), said(fresh), "after a change of language the findings are said in the old one");
        }
        finally
        {
            org.traincontrol.util.I18n.setLocale(was);

            session.restoreSetup(asFound);
        }
    }

    /**
     * The length of a train the setup places where a railway train now stands is checked again: whether the railway
     * models any length asks every placed train's.
     *
     * MUTATION: key only the lengths of the trains where they stand, and this fails.
     */
    @Test
    public void testAHiddenPlacedTrainsLengthIsCheckedAgain() throws Exception
    {
        Map<TileKey, String> placed = session.trainsWhereTheyStand();

        assertFalse(placed.isEmpty(), "precondition: the frozen setup places no train");

        final String hidden = placed.values().iterator().next();

        String stranger = null;

        for (String loc : scenario.getModel().getLocList())
        {
            if (stranger == null && !placed.containsValue(loc)) stranger = loc;
        }

        assertNotNull(stranger, "precondition: every locomotive is placed, so none can stand in for " + hidden);

        final Layout running = scenario.build();

        org.traincontrol.automation.Point at = null;

        for (org.traincontrol.automation.Point p : running.getPoints())
        {
            if (p.getCurrentLocomotive() != null && hidden.equals(p.getCurrentLocomotive().getName())) at = p;
        }

        assertNotNull(at, "precondition: the railway built from the setup does not have " + hidden);

        at.setLocomotive(scenario.getModel().getLocByName(stranger));

        final int[] length = {0};

        try
        {
            session.setTrainLengthSource(name -> hidden.equals(name) ? length[0] : 0);

            session.setRunningLayoutSource(() -> running);

            assertFalse(session.trainsWhereTheyStand().containsValue(hidden), "precondition: " + hidden + " still stands"
                + " somewhere, so its length is keyed where it stands");

            List<List<AutonomyChecks.Finding>> got = acrossEvents(() -> length[0] = 100);

            assertFalse(got.get(0).isEmpty(), "precondition: the frozen railway has no findings");

            assertNotSame(got.get(1).get(0), got.get(0).get(0), "the length of " + hidden + ", placed under " + stranger
                + ", was not checked again");
        }
        finally
        {
            putBackTheSources();
        }
    }

    /**
     * A change only the rebuilt graph carries is checked: a three-way switch's second accessory, read at the rebuild and
     * in neither the setup nor the squares the key writes, taken off and rebuilt is an error that it has no address.
     *
     * MUTATION: leave the graph out of what the check is said to read, and this fails.
     */
    @Test
    public void testAChangeOnlyTheRebuildReadsIsChecked() throws Exception
    {
        LayoutDiagramComponent found = null;

        for (LayoutDiagram page : scenario.getPages())
        {
            for (LayoutDiagramComponent c : page.getAll())
            {
                if (found == null && c.isThreeWay() && c.getAccessory() != null && c.getAccessory2() != null) found = c;
            }
        }

        assertNotNull(found, "precondition: the frozen railway has no three-way switch with both accessories");

        final LayoutDiagramComponent threeWay = found;

        final org.traincontrol.base.Accessory second = threeWay.getAccessory2();

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            List<List<AutonomyChecks.Finding>> got = acrossEvents(() ->
            {
                threeWay.setAccessory2(null);

                session.rebuild();

                return null;
            });

            assertTrue(countOf(got.get(1), org.traincontrol.automationui.TileGraph.ERROR_NO_ADDRESS)
                > countOf(got.get(0), org.traincontrol.automationui.TileGraph.ERROR_NO_ADDRESS), "a three-way switch"
                + " left with one accessory and rebuilt is not said to have no address");
        }
        finally
        {
            threeWay.setAccessory2(second);

            session.restoreSetup(asFound);

            inOneEvent(() ->
            {
                session.rebuild();

                return null;
            });
        }
    }

    /**
     * An index derived anew in a later event is checked: a turning mark written straight into the setup, checked
     * against the old index, then a point-property door that changes nothing else derives the index, and the findings
     * follow it.
     *
     * MUTATION: leave the station index out of what the check is said to read, and this fails.
     */
    @Test
    public void testAnIndexDerivedAnewIsChecked() throws Exception
    {
        Map<TileKey, String> placed = session.trainsWhereTheyStand();

        assertFalse(placed.isEmpty(), "precondition: the frozen setup places no train");

        final TileKey square = placed.keySet().iterator().next();

        final Object priority = session.getPointProperty(square, "priority");

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            inOneEvent(() -> session.check());

            // Straight into the setup, as no door does: the index is not derived
            inOneEvent(() -> session.getStore().getConfiguration(session.getStore().getActiveConfiguration())
                .getJSONObject("points").getJSONObject(square.toString()).put(AutonomyBuilder.MUST_REVERSE, true));

            List<List<AutonomyChecks.Finding>> got = acrossEvents(() ->
            {
                session.setPointProperty(square, "priority", priority);

                return null;
            });

            // Off the event thread nothing is kept, so this is worked out afresh
            List<AutonomyChecks.Finding> fresh = session.check();

            assertEquals(said(got.get(1)), said(fresh), "the findings do not follow the index derived anew");

            assertFalse(got.get(0).isEmpty(), "precondition: the frozen railway has no findings");

            assertNotSame(got.get(1).get(0), got.get(0).get(0), "an index derived anew was not checked again");
        }
        finally
        {
            session.restoreSetup(asFound);

            inOneEvent(() ->
            {
                session.rebuild();

                return null;
            });
        }
    }
}
