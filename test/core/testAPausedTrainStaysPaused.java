package core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.DiagramMonitor;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A paused train stays paused, and is kept out of what Adam said it is kept out of (FR-117; Adam, 2026-10-09: "in
 * autonomy configs, track the paused/unpaused status of locomotives, as designated on the autonomy locomotive controls
 * tab", and asked what a paused train is kept out of, "Autonomy and Return Home").
 *
 * On a ring of four stations of this class's own, with two locomotives of its own: the railway's file says which trains
 * are paused and a file read back says it again; Return Home leaves a paused train where it stands and moves it out of
 * nobody's way; the diagram's mark for a paused train says so.  And on Adam's frozen railway (`test/operator_layout`), the
 * setup records the pause, builds it, saves it, and takes in one made on the railway.
 */
public class testAPausedTrainStaysPaused
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    /** Clear of the other suites' feedback ranges. */
    private static final int S88_BASE = 8760;

    private static final String ALPHA = "FR-117 alpha";
    private static final String BRAVO = "FR-117 bravo";
    private static final String SETUP_TRAIN = "FR-117 in the setup";

    /** The key the railway's file lists its paused trains under - the file's contract, so spelled out here. */
    private static final String PAUSED = "pausedLocomotives";

    /** What the model logged, as the log window shows it. */
    private static final List<String> logged = Collections.synchronizedList(new ArrayList<String>());

    private static final Handler listening = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            if (record != null && record.getMessage() != null) logged.add(record.getMessage());
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory()) throw new SkipException("test/operator_layout is not here");

        // HIS FROZEN RAILWAY, for the setup's claim; the ring's claims load their own configuration over it
        sandbox = support.LayoutSandbox.open(frozen);

        model = init(null, true, false, false, true);
        model.stop();

        model.newMM2Locomotive(ALPHA, 2404);
        model.newMM2Locomotive(BRAVO, 2405);
        model.newMM2Locomotive(SETUP_TRAIN, 2406);

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(listening);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(listening);

        try
        {
            if (model != null)
            {
                for (String name : new String[] {ALPHA, BRAVO, SETUP_TRAIN})
                {
                    try
                    {
                        model.deleteLoc(name);
                    }
                    catch (Exception alreadyGone)
                    {
                    }
                }

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** Every train set going again, so no claim inherits another's pause. */
    @AfterMethod(alwaysRun = true)
    public void unpause()
    {
        for (String name : new String[] {ALPHA, BRAVO, SETUP_TRAIN})
        {
            MarklinLocomotive train = model.getLocByName(name);

            if (train != null) train.setAutonomyPaused(false);
        }
    }

    /**
     * The railway's file lists the paused trains, and a file read back pauses those and sets every other train going.
     *
     * MUTATION: leave the list out of the file, or leave a train the file does not list as it was, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testThePauseIsWrittenWithTheRailwayAndReadBack() throws Exception
    {
        Layout layout = load(ring(ALPHA, BRAVO));

        MarklinLocomotive alpha = model.getLocByName(ALPHA);
        MarklinLocomotive bravo = model.getLocByName(BRAVO);

        alpha.setAutonomyPaused(true);

        JSONObject written = new JSONObject(layout.toJSON());

        assertEquals(names(written.optJSONArray(PAUSED)), Collections.singletonList(ALPHA), "with " + ALPHA + " paused,"
            + " the railway's file lists as paused " + written.opt(PAUSED) + ", so the configuration forgets the pause");

        // READ BACK, with each train told the opposite first
        alpha.setAutonomyPaused(false);
        bravo.setAutonomyPaused(true);

        load(written.toString());

        assertTrue(alpha.isAutonomyPaused(), "a file listing " + ALPHA + " as paused loaded it running");

        assertFalse(bravo.isAutonomyPaused(), "a file that does not list " + BRAVO + " left it paused from before the load");

        // A FILE THAT LISTS NOBODY sets everybody going, and a railway with nobody paused writes no list
        load(ring(ALPHA, BRAVO));

        assertFalse(alpha.isAutonomyPaused(), "a file listing nobody as paused left " + ALPHA + " paused");

        assertFalse(new JSONObject(model.getAutoLayout().toJSON()).has(PAUSED), "a railway with nobody paused still"
            + " writes the list, so every configuration file grows a key that means nothing to it");
    }

    /**
     * A configuration that lists paused trains names them in the log as it loads - an import's load among them - and one
     * that lists nobody says nothing about pauses (Adam, 2026-10-09: "Make sure the log shows what locomotive are paused
     * when the autonomy import happens").
     *
     * MUTATION: drop the line, or write it for a configuration that pauses nobody, and this fails.
     */
    @Test
    public void testTheLogNamesThePausedTrainsAsAConfigurationLoads()
    {
        JSONObject both = new JSONObject(ring(ALPHA, BRAVO));

        both.put(PAUSED, new JSONArray(java.util.Arrays.asList(BRAVO, ALPHA)));

        logged.clear();

        load(both.toString());

        String expected = I18n.f("autolayout.infoPausedLocomotives", ALPHA + ", " + BRAVO);

        assertTrue(anyLogged(expected), "a configuration pausing " + ALPHA + " and " + BRAVO + " loaded without the log"
            + " saying \"" + expected + "\": " + logged);

        logged.clear();

        load(ring(ALPHA, BRAVO));

        String said = I18n.f("autolayout.infoPausedLocomotives", "#").split("#", -1)[0];

        for (String line : new ArrayList<>(logged))
        {
            assertFalse(line.trim().startsWith(said.trim()), "a configuration pausing nobody logged \"" + line + "\"");
        }
    }

    /**
     * Return Home leaves a paused train where it stands: no move for it, the others brought home, and its check after the
     * run says everyone is home - the one the window reports a stopped run by.
     *
     * MUTATION: plan a paused train home like any other, and this fails.
     */
    @Test
    public void testReturnHomeLeavesAPausedTrainWhereItStands()
    {
        Layout layout = load(ring(ALPHA, BRAVO));

        assertTrue(layout.moveLocomotive(ALPHA, "PT C", false), "precondition: " + ALPHA + " could not be moved to PT C");
        assertTrue(layout.moveLocomotive(BRAVO, "PT D", false), "precondition: " + BRAVO + " could not be moved to PT D");

        MarklinLocomotive alpha = model.getLocByName(ALPHA);

        alpha.setAutonomyPaused(true);

        logged.clear();

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "with " + BRAVO + " away from home, Return Home found nothing to do: "
            + plan.getOutcome());

        assertTrue(anyLogged(I18n.f("autolayout.infoReturnToHomePaused", ALPHA)), "Return Home left the paused " + ALPHA
            + " where it stands and the log does not say so: " + logged);

        for (HomeStaging.Move move : plan.getMoves())
        {
            assertFalse(move.getLocomotive().equals(alpha), "Return Home plans the paused " + ALPHA + ": " + move);
        }

        for (HomeStaging.Move move : plan.getMoves())
        {
            assertTrue(layout.moveLocomotive(move.getLocomotive().getName(), move.getEnd().getName(), false),
                "the move " + move + " was refused");
        }

        assertEquals(layout.getLocomotiveLocation(alpha).getName(), "PT C", "the paused train did not stay where it stood");

        assertEquals(layout.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME, "with every train but the paused"
            + " one home, Return Home's check after a run says not everyone is, and the window reports the run stopped");
    }

    /**
     * A paused train standing on another's home is not moved out of its way - one with no home of its own, which the
     * search would otherwise move as it moves any train with nowhere to be.
     *
     * Control: set going, the same train is moved out of the way, or this claim could not tell.
     *
     * MUTATION: let the search move a paused train as it moves a train with nowhere to be, and this fails.
     */
    @Test
    public void testReturnHomeDoesNotMoveAPausedTrainOutOfTheWay()
    {
        Layout layout = load(ring(null, BRAVO));

        MarklinLocomotive alpha = model.getLocByName(ALPHA);

        assertTrue(layout.moveLocomotive(BRAVO, "PT D", false), "precondition: " + BRAVO + " could not be moved to PT D");

        // ON BRAVO'S HOME, which is claimed, so ALPHA has no home of its own
        assertTrue(layout.moveLocomotive(ALPHA, "PT B", false), "precondition: " + ALPHA + " could not be put on PT B");

        assertNull(layout.getHomeStation(alpha), "precondition: " + ALPHA + " was given a home, so it is not the train"
            + " with nowhere to be that the search moves out of the way");

        alpha.setAutonomyPaused(true);

        HomeStaging.Plan plan = layout.planReturnToHome();

        for (HomeStaging.Move move : plan.getMoves())
        {
            assertFalse(move.getLocomotive().equals(alpha), "Return Home moves the paused " + ALPHA + " off "
                + BRAVO + "'s home: " + move + " (" + plan.getOutcome() + ")");
        }

        // CONTROL
        alpha.setAutonomyPaused(false);

        boolean moved = false;

        for (HomeStaging.Move move : layout.planReturnToHome().getMoves())
        {
            if (move.getLocomotive().equals(alpha)) moved = true;
        }

        assertTrue(moved, "precondition: set going, " + ALPHA + " is not moved out of " + BRAVO + "'s way either, so"
            + " this claim cannot tell a paused train from any other");
    }

    /**
     * The diagram's mark for a train standing still says whether it is paused, and pausing one is a new picture.
     *
     * MUTATION: mark every standing train the same, or leave the pause out of what makes a picture new, and this fails.
     */
    @Test
    public void testTheDiagramMarksAPausedTrain()
    {
        final Layout layout = load(ring(ALPHA, BRAVO));

        Map<String, TileKey> tiles = new LinkedHashMap<>();

        tiles.put("PT A", new TileKey("FR-117", 0, 0));
        tiles.put("PT B", new TileKey("FR-117", 1, 0));
        tiles.put("PT C", new TileKey("FR-117", 2, 0));
        tiles.put("PT D", new TileKey("FR-117", 3, 0));

        final List<Map<TileKey, TileOverlay>> pictures = new ArrayList<>();

        DiagramMonitor monitor = new DiagramMonitor(() -> layout, new LinkedHashMap<String, ReducedEdge>(), tiles,
            overlays -> pictures.add(new LinkedHashMap<>(overlays)));

        monitor.refresh();

        TileOverlay atA = monitor.getPublished().get(tiles.get("PT A"));

        assertTrue(atA != null && atA.isParked(), "precondition: " + ALPHA + " is not drawn standing on PT A: " + atA);

        assertFalse(atA.isPaused(), ALPHA + " is drawn paused before anybody paused it");

        model.getLocByName(ALPHA).setAutonomyPaused(true);

        int before = pictures.size();

        monitor.refresh();

        assertEquals(pictures.size(), before + 1, "pausing " + ALPHA + " drew nothing new: the picture was taken to be"
            + " the same one");

        assertTrue(monitor.getPublished().get(tiles.get("PT A")).isPaused(), "the paused " + ALPHA + " is not drawn"
            + " paused: " + monitor.getPublished().get(tiles.get("PT A")));

        assertFalse(monitor.getPublished().get(tiles.get("PT B")).isPaused(), BRAVO + ", not paused, is drawn paused");
    }

    /**
     * The setup records a pause: the door pauses the train and lists it, the configuration built from the setup carries
     * it to the railway, it is saved, and setting the train going takes it out again.  And a pause made on the railway -
     * the Auto tab's button - is taken into the setup by the fold, and taken out by it too.
     *
     * MUTATION: leave the setup's list out of what is built, or out of what the fold takes, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheSetupKeepsThePauseAndTheRailwayReadsIt() throws Exception
    {
        MarklinLocomotive train = model.getLocByName(SETUP_TRAIN);

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(pages);

        String configuration = session.getStore().getActiveConfiguration();

        assertNotNull(configuration, "precondition: his railway has no active configuration");

        // THE DOOR
        session.setActive(train, false);

        assertTrue(train.isAutonomyPaused(), "the setup's door did not pause the train");

        assertEquals(new ArrayList<>(session.getPausedLocomotives()), Collections.singletonList(SETUP_TRAIN), "the"
            + " setup does not list the train it paused");

        // BUILT: the railway reads it, with the train told otherwise first
        String built = session.buildConfiguration();

        assertEquals(names(new JSONObject(built).optJSONArray(PAUSED)), Collections.singletonList(SETUP_TRAIN), "the"
            + " configuration built from the setup does not list the paused train");

        train.setAutonomyPaused(false);

        model.parseAuto(built);

        assertTrue(train.isAutonomyPaused(), "the railway built from the setup has the paused train running");

        // SAVED
        AutonomySession again = new AutonomySession(sandbox.getFolder());

        again.open(pages);

        assertTrue(again.getPausedLocomotives().contains(SETUP_TRAIN), "the pause was not saved: the setup read back"
            + " from its folder lists " + again.getPausedLocomotives());

        // SET GOING AGAIN
        session.setActive(train, true);

        assertFalse(train.isAutonomyPaused(), "the setup's door did not set the train going again");

        assertTrue(session.getPausedLocomotives().isEmpty(), "the setup still lists " + session.getPausedLocomotives());

        assertFalse(new JSONObject(session.buildConfiguration()).has(PAUSED), "a setup with nobody paused still builds"
            + " the list");

        // THE FOLD: paused on the railway, as the Auto tab's button does, and taken into the setup
        model.parseAuto(session.buildConfiguration());

        Layout running = model.getAutoLayout();

        JSONObject asBuilt = AutonomySession.globalsOf(new JSONObject(running.toJSON()));

        train.setAutonomyPaused(true);

        session.captureTheRailwaysSettings(running.toJSON(), configuration, asBuilt);

        assertTrue(session.getPausedLocomotives().contains(SETUP_TRAIN), "a train paused on the railway is not in the"
            + " setup after the fold: " + session.getPausedLocomotives());

        // AND SET GOING ON THE RAILWAY, taken out by the fold
        asBuilt = AutonomySession.globalsOf(new JSONObject(running.toJSON()));

        train.setAutonomyPaused(false);

        session.captureTheRailwaysSettings(running.toJSON(), configuration, asBuilt);

        assertFalse(session.getPausedLocomotives().contains(SETUP_TRAIN), "a train set going on the railway is still"
            + " paused in the setup after the fold");
    }

    /**
     * A fold brings the record of how the railway was built up to date with what it takes (OB-343): the record is what the
     * railway and the setup last agreed on, and the settings a fold writes into the setup are the railway's and the setup's
     * both.  Left behind, a pause the fold took, then undone on the railway, read to the next fold as the railway unchanged
     * from how it was built - the same as the stale record - and the setup's older answer stood.
     *
     * The settings fold, the whole fold, and a setting the railway stopped writing, which the record loses too.
     *
     * MUTATION: leave the record as it was after a fold, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAFoldBringsTheRecordOfHowTheRailwayWasBuiltUpToDate() throws Exception
    {
        MarklinLocomotive train = model.getLocByName(SETUP_TRAIN);

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(pages);

        String configuration = session.getStore().getActiveConfiguration();

        assertNotNull(configuration, "precondition: his railway has no active configuration");

        train.setAutonomyPaused(false);

        session.setLocomotivesPaused(Collections.<String>emptyList(), false);

        model.parseAuto(session.buildConfiguration());

        Layout running = model.getAutoLayout();

        JSONObject asBuilt = AutonomySession.globalsOf(new JSONObject(running.toJSON()));

        assertFalse(asBuilt.has(PAUSED), "precondition: the railway was built with somebody paused");

        // THE SETTINGS FOLD: a pause made on the railway is taken into the setup, and into the record
        train.setAutonomyPaused(true);

        session.captureTheRailwaysSettings(running.toJSON(), configuration, asBuilt);

        assertTrue(session.getPausedLocomotives().contains(SETUP_TRAIN), "precondition: the fold did not take the pause into"
            + " the setup");

        assertEquals(names(asBuilt.optJSONArray(PAUSED)), Collections.singletonList(SETUP_TRAIN), "the fold took the pause"
            + " into the setup and left the record of how the railway was built without it");

        // SET GOING: the railway's list is empty again, as when it was built, and the fold must take that into the setup
        train.setAutonomyPaused(false);

        session.captureTheRailwaysSettings(running.toJSON(), configuration, asBuilt);

        assertFalse(session.getPausedLocomotives().contains(SETUP_TRAIN), "a train paused and then set going on the railway"
            + " is paused in the setup after the second fold");

        assertFalse(asBuilt.has(PAUSED), "the record still lists a pause the railway has stopped writing");

        // THE WHOLE FOLD: the record is the railway's settings as they stand
        train.setAutonomyPaused(true);

        session.captureFromLayout(running.toJSON(), configuration, asBuilt);

        assertTrue(session.getPausedLocomotives().contains(SETUP_TRAIN), "precondition: the whole fold did not take the"
            + " pause into the setup");

        assertTrue(asBuilt.similar(AutonomySession.globalsOf(new JSONObject(running.toJSON()))), "the whole fold wrote the"
            + " railway's settings into the setup and left the record of how it was built as it was: " + asBuilt);

        // AND THE RECORD SHARES NOTHING WITH THE SETUP'S SETTINGS: changing one leaves the other
        asBuilt.getJSONArray(PAUSED).put("Not A Train");

        assertFalse(session.getPausedLocomotives().contains("Not A Train"), "the record and the setup's list are one array");

        train.setAutonomyPaused(false);

        session.captureFromLayout(running.toJSON(), configuration, asBuilt);

        assertFalse(session.getPausedLocomotives().contains(SETUP_TRAIN), "precondition: the whole fold did not take the"
            + " train's going into the setup");
    }

    /**
     * An edit session's Cancel puts the setup back and keeps what the railway changed alone while it was open (OB-343):
     * the settings a fold took from the railway are the railway's, not the edit's, to undo.
     *
     * The setup is snapshotted and an edit session begun; a pause made on the railway is folded in; an edit of the setup's
     * own is made; and the snapshot is put back.  The pause stays and the edit goes.  Outside an edit session, putting a
     * snapshot back is exactly that.
     *
     * MUTATION: leave what the fold took out of the restore, or keep the edit, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAnEditSessionsRestoreKeepsWhatTheRailwayChangedAlone() throws Exception
    {
        MarklinLocomotive train = model.getLocByName(SETUP_TRAIN);

        MarklinLocomotive other = model.getLocByName(ALPHA);

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(pages);

        String configuration = session.getStore().getActiveConfiguration();

        assertNotNull(configuration, "precondition: his railway has no active configuration");

        train.setAutonomyPaused(false);

        session.setLocomotivesPaused(Collections.<String>emptyList(), false);

        model.parseAuto(session.buildConfiguration());

        Layout running = model.getAutoLayout();

        JSONObject asBuilt = AutonomySession.globalsOf(new JSONObject(running.toJSON()));

        JSONObject opened = session.snapshotSetup();

        try
        {
            session.beginEditSession();

            // THE RAILWAY ALONE: paused, and folded into the setup
            train.setAutonomyPaused(true);

            session.captureTheRailwaysSettings(running.toJSON(), configuration, asBuilt);

            assertTrue(session.getPausedLocomotives().contains(SETUP_TRAIN), "precondition: the fold did not take the pause");

            // AN EDIT OF THE SETUP'S OWN
            session.setLocomotivesPaused(Collections.singletonList(ALPHA), true);

            assertTrue(session.getPausedLocomotives().contains(ALPHA), "precondition: the edit was not made");

            // CANCEL
            session.restoreSetup(opened);

            assertTrue(session.getPausedLocomotives().contains(SETUP_TRAIN), "Cancel took back a pause the railway made alone"
                + " while the edit session was open");

            assertFalse(session.getPausedLocomotives().contains(ALPHA), "Cancel kept an edit of the setup's own: "
                + session.getPausedLocomotives());
        }
        finally
        {
            session.endEditSession();

            train.setAutonomyPaused(false);

            other.setAutonomyPaused(false);
        }

        // OUTSIDE AN EDIT SESSION a restore is the snapshot, whatever was folded before it
        session.setLocomotivesPaused(Collections.singletonList(SETUP_TRAIN), true);

        session.restoreSetup(opened);

        assertFalse(session.getPausedLocomotives().contains(SETUP_TRAIN), "a restore outside an edit session kept what the"
            + " snapshot did not have: " + session.getPausedLocomotives());
    }

    /** The setup, its railway built with nobody paused, and the record of how it was built - the fixture of the folds below. */
    private static final class Folded
    {
        AutonomySession session;
        String configuration;
        Layout running;
        JSONObject asBuilt;
    }

    private static Folded builtWithNobodyPaused() throws Exception
    {
        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        Folded f = new Folded();

        f.session = new AutonomySession(sandbox.getFolder());

        f.session.open(pages);

        f.configuration = f.session.getStore().getActiveConfiguration();

        assertNotNull(f.configuration, "precondition: his railway has no active configuration");

        for (org.traincontrol.base.Locomotive each : model.getLocomotives()) each.setAutonomyPaused(false);

        f.session.setLocomotivesPaused(new ArrayList<>(f.session.getPausedLocomotives()), false);

        f.session.setGlobal("simulate", null);

        model.parseAuto(f.session.buildConfiguration());

        f.running = model.getAutoLayout();

        f.asBuilt = AutonomySession.globalsOf(new JSONObject(f.running.toJSON()));

        return f;
    }

    /**
     * A fold takes the paused trains BY TRAIN and never replaces the setup's list (OB-343): the trains the railway gained or
     * lost against the record are applied to the setup's own, so what the setup lists that the railway and the record do not
     * have stays - a name no train answers to, or a train the setup paused and the railway has not been told of - and a
     * record with no list at all is every train gained.
     *
     * MUTATION: put the railway's whole list in the setup in a fold, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAFoldTakesThePausedListByTrain() throws Exception
    {
        Folded f = builtWithNobodyPaused();

        MarklinLocomotive train = model.getLocByName(SETUP_TRAIN);

        // THE SETUP LISTS A NAME the railway and the record do not have
        f.session.setLocomotivesPaused(Collections.singletonList("Zz Ghost Train"), true);

        assertFalse(f.asBuilt.has(PAUSED), "precondition: the record has a list of the paused");

        train.setAutonomyPaused(true);

        try
        {
            f.session.captureTheRailwaysSettings(f.running.toJSON(), f.configuration, f.asBuilt);

            assertEquals(new ArrayList<>(f.session.getPausedLocomotives()), java.util.Arrays.asList(SETUP_TRAIN, "Zz Ghost Train"),
                "the fold replaced the setup's list of the paused with the railway's");

            // A TRAIN THE RAILWAY LOSES is taken off, and the rest stays
            train.setAutonomyPaused(false);

            f.session.captureTheRailwaysSettings(f.running.toJSON(), f.configuration, f.asBuilt);

            assertEquals(new ArrayList<>(f.session.getPausedLocomotives()), Collections.singletonList("Zz Ghost Train"),
                "the fold did not take off the train the railway lost, or took off one it did not");

            assertFalse(f.asBuilt.has(PAUSED), "the record still lists a train the railway has set going");
        }
        finally
        {
            train.setAutonomyPaused(false);

            f.session.setLocomotivesPaused(Collections.singletonList("Zz Ghost Train"), false);
        }
    }

    /**
     * An edit session's Cancel keeps a card's un-pause (OB-343): the fold's note of a train the railway LOST against the record
     * is taken off the list Cancel puts back, and a setting the railway stopped writing - Simulate turned off - is taken out
     * of it.
     *
     * The railway is built with a train paused and Simulate on, the snapshot taken and an edit session begun; the train is set
     * going and the fold taken; Simulate is turned off and the fold taken again; and the snapshot, which has both, is put back.
     *
     * MUTATION: leave a loss out of what Cancel puts back, or a setting the railway stopped writing, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAnEditSessionsRestoreKeepsACardsUnpauseAndAStoppedSimulation() throws Exception
    {
        Folded f = builtWithNobodyPaused();

        MarklinLocomotive train = model.getLocByName(SETUP_TRAIN);

        // BUILT PAUSED, WITH SIMULATE ON
        f.session.setActive(train, false);

        f.session.setGlobal("simulate", true);

        model.parseAuto(f.session.buildConfiguration());

        f.running = model.getAutoLayout();

        f.asBuilt = AutonomySession.globalsOf(new JSONObject(f.running.toJSON()));

        assertTrue(f.asBuilt.has(PAUSED) && f.asBuilt.optBoolean("simulate"), "precondition: the railway was not built paused,"
            + " simulating: " + f.asBuilt);

        JSONObject opened = f.session.snapshotSetup();

        try
        {
            f.session.beginEditSession();

            // A CARD SETS THE TRAIN GOING, folded
            train.setAutonomyPaused(false);

            f.session.captureTheRailwaysSettings(f.running.toJSON(), f.configuration, f.asBuilt);

            assertFalse(f.session.getPausedLocomotives().contains(SETUP_TRAIN), "precondition: the fold did not take the train"
                + " off the setup's list");

            // SIMULATE TURNED OFF, folded: the railway stops writing it
            f.running.setSimulate(false);

            f.session.captureTheRailwaysSettings(f.running.toJSON(), f.configuration, f.asBuilt);

            assertNull(f.session.getGlobal("simulate"), "precondition: the fold did not take Simulate out of the setup");

            assertFalse(f.asBuilt.has("simulate"), "the record still has Simulate, which the railway has stopped writing");

            // CANCEL
            f.session.restoreSetup(opened);

            assertFalse(f.session.getPausedLocomotives().contains(SETUP_TRAIN), "Cancel paused again a train a card set going"
                + " while the edit session was open");

            assertNull(f.session.getGlobal("simulate"), "Cancel put Simulate back, which the railway turned off while the edit"
                + " session was open");
        }
        finally
        {
            f.session.endEditSession();

            f.running.setSimulate(false);

            train.setAutonomyPaused(false);

            f.session.setLocomotivesPaused(Collections.singletonList(SETUP_TRAIN), false);

            f.session.setGlobal("simulate", null);
        }
    }

    /**
     * A fold with no record of how the railway was built takes every setting of the railway's (OB-343): the paused list is the
     * railway's own - there is nothing to tell what it gained - and an edit session's Cancel keeps all of it.
     *
     * MUTATION: take only the settings that differ from an empty record, or leave the pauses out of what Cancel keeps, and
     * this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAFoldWithNoRecordTakesEverySettingAndCancelKeepsIt() throws Exception
    {
        Folded f = builtWithNobodyPaused();

        MarklinLocomotive train = model.getLocByName(SETUP_TRAIN);

        f.session.setLocomotivesPaused(Collections.singletonList("Zz Ghost Train"), true);

        JSONObject opened = f.session.snapshotSetup();

        int delay = f.running.getMinDelay();

        int moved = delay + 1 <= f.running.getMaxDelay() ? delay + 1 : Math.max(0, delay - 1);

        try
        {
            f.session.beginEditSession();

            train.setAutonomyPaused(true);

            f.running.setMinDelay(moved);

            f.session.captureTheRailwaysSettings(f.running.toJSON(), f.configuration, null);

            assertEquals(new ArrayList<>(f.session.getPausedLocomotives()), Collections.singletonList(SETUP_TRAIN), "a fold with"
                + " no record did not make the setup's list the railway's");

            assertEquals(String.valueOf(f.session.getGlobal("minDelay")), String.valueOf(moved), "a fold with no record did not"
                + " take the setting the railway moved");

            f.session.restoreSetup(opened);

            assertTrue(f.session.getPausedLocomotives().contains(SETUP_TRAIN), "Cancel took back the pause a fold with no"
                + " record took from the railway");

            assertEquals(String.valueOf(f.session.getGlobal("minDelay")), String.valueOf(moved), "Cancel took back the setting"
                + " a fold with no record took from the railway");
        }
        finally
        {
            f.session.endEditSession();

            train.setAutonomyPaused(false);

            f.session.setLocomotivesPaused(java.util.Arrays.asList(SETUP_TRAIN, "Zz Ghost Train"), false);

            f.running.setMinDelay(delay);

            f.session.setGlobal("minDelay", delay);
        }
    }

    // ------------------------------------------------------------------------------------------- fixtures

    /** Loads a configuration and returns the railway, asserting it built. */
    private static Layout load(String config)
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertTrue(layout != null && layout.isValid(), "precondition: the ring must build - " + Layout.getLastError());

        return layout;
    }

    /** Four stations in a ring, every edge both ways, with the two trains on PT A and PT B - their homes. */
    private static String ring(String atA, String atB)
    {
        return ("{'points': [" + station("PT A", 0, atA) + "," + station("PT B", 1, atB) + "," + station("PT C", 2, null)
            + "," + station("PT D", 3, null) + "],'edges': ["
            + edge("PT A", "PT B") + "," + edge("PT B", "PT A") + ","
            + edge("PT B", "PT C") + "," + edge("PT C", "PT B") + ","
            + edge("PT C", "PT D") + "," + edge("PT D", "PT C") + ","
            + edge("PT D", "PT A") + "," + edge("PT A", "PT D")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}").replace('\'', '"');
    }

    private static String station(String name, int s88Offset, String loc)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (S88_BASE + s88Offset)
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    private static String edge(String from, String to)
    {
        return "{'start': '" + from + "', 'end': '" + to + "'}";
    }

    /** Whether the model logged this line, its leading spaces aside. */
    private static boolean anyLogged(String line)
    {
        for (String each : new ArrayList<>(logged))
        {
            if (each.trim().equals(line.trim())) return true;
        }

        return false;
    }

    /** The names in a list from a file, or an empty list where there is none. */
    private static List<String> names(JSONArray list)
    {
        List<String> out = new ArrayList<>();

        for (int i = 0; list != null && i < list.length(); i++) out.add(String.valueOf(list.get(i)));

        return out;
    }
}
