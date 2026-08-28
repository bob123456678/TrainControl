package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * The whole chain, from a decision somebody made to the graph a train could run on.
 *
 * The unit tests below this cover each link separately; this covers the thing that goes wrong when they
 * are joined - an edit that changes the files but not the graph, or a graph that reflects an edit that
 * was never saved.  Both leave a user checking their work against the wrong answer.
 *
 * @author Adam
 */
public class testAutonomyDiagramSession
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-autonomy-session").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * An edit changes the graph immediately, not at some later save.
     *
     * A derivation lagging behind an edit shows a graph that was true a moment ago, which is worse than
     * showing none: the user is checking their work against the wrong answer and has no way to tell.
     */
    @Test
    public void testAnEditChangesTheGraphAtOnce() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        assertEquals(edgesBetween(11, 12), 1, "the run should connect");
        assertEquals(edgesBetween(12, 11), 1, "and back");

        // close the middle tile in one direction
        TileKey middle = new TileKey("main", 2, 1);
        RouteId route = session.getRoutes(middle).keySet().iterator().next();

        session.setDirection(middle, route, Direction.TOWARD_A);

        int forward = edgesBetween(11, 12);
        int backward = edgesBetween(12, 11);

        assertEquals(forward + backward, 1, "exactly one direction should survive, without a rebuild call");
    }

    /**
     * A default is not written out as though it were a decision.
     *
     * It matters here more than it looks: the defaults are not all the same - plain track runs both ways
     * while a switch runs base to forks - so a stored "default" would freeze whichever default happened
     * to apply on the day, and stop tracking the rule.
     */
    @Test
    public void testSettingATileBackToItsDefaultStoresNothing() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        TileKey middle = new TileKey("main", 2, 1);
        RouteId route = session.getRoutes(middle).keySet().iterator().next();

        session.setDirection(middle, route, Direction.TOWARD_A);
        assertNotNull(session.getStore().getTileDirection(middle, route));

        session.setDirection(middle, route, Direction.BOTH);
        assertNull(session.getStore().getTileDirection(middle, route),
            "back at the default, so nothing should be stored");
    }

    /**
     * Bulk editing sets what clicking one tile would have set.
     *
     * Not a convenience: switches default to base-to-forks, so most of setting a real layout up is
     * opening trailing moves, and one tile at a time would be the bulk of the work.
     */
    @Test
    public void testBulkEditingAppliesToEveryTileSelected() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        Set<TileKey> selection = new LinkedHashSet<>(Arrays.asList(
            new TileKey("main", 2, 1), new TileKey("main", 3, 1)));

        session.setDirection(selection, Direction.NONE);

        assertEquals(edgesBetween(11, 12), 0, "a closed run should carry nothing");
        assertEquals(edgesBetween(12, 11), 0);
    }

    /**
     * Initialising twice makes a second configuration rather than quietly doing nothing.
     *
     * It used to create one only when the store was empty, which was right while it was the "set
     * autonomy up for the first time" button and wrong the moment the menu offered "add a
     * configuration": the second one did nothing at all, and said so nowhere.
     */
    @Test
    public void testInitialisingAgainAddsAnotherConfiguration() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        session.initialize("Morning");
        session.initialize("Evening");

        assertEquals(session.getStore().getConfigurationNames().size(), 2);
        assertTrue(session.getStore().getConfigurationNames().contains("Evening"));

        // and the first one is still the one running, because adding is not loading
        assertEquals(session.getStore().getActiveConfiguration(), "Morning");
    }

    /**
     * And a name already taken is refused rather than silently replacing what is there.
     */
    @Test
    public void testInitialisingOntoAnExistingNameIsRefused() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        session.initialize("Morning");

        try
        {
            session.initialize("Morning");

            fail("a second configuration called Morning should not be creatable");
        }
        catch (IOException expected)
        {
            assertEquals(session.getStore().getConfigurationNames().size(), 1);
        }
    }

    /**
     * What was decided comes back after a restart, and the graph derived from it matches.
     */
    @Test
    public void testASetupSurvivesBeingReopened() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Evening");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.setTileLength(new TileKey("main", 2, 1), 7);
        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(runOfTrack()));

        assertEquals(reopened.getStore().getPointName(sensor), "Platform 1");
        assertTrue(reopened.getReducer().getPoints().get(sensor).isStation());
        assertEquals(reopened.getStore().getActiveConfiguration(), "Evening");

        // and the length reaches the edge, which is the point of storing it
        assertEquals(reopened.getReducer().getEdges().get(0).getLength(), 7);
    }

    /**
     * The generated configuration is the ordinary format, so nothing downstream has to learn a new one.
     */
    @Test
    public void testTheGeneratedConfigurationIsTheOrdinaryFormat() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        assertTrue(built.has("points"));
        assertTrue(built.has("edges"));
        assertTrue(built.has("minDelay"), "the keys parseAuto insists on must be there");
        assertEquals(built.getJSONArray("points").length(), 2);

        // and the inspection copy adds coordinates so it can be read against the diagram
        org.json.JSONObject inspect = new org.json.JSONObject(session.buildConfigurationForInspection());

        assertTrue(inspect.getJSONArray("points").getJSONObject(0).has("x"),
            "the inspection copy should be laid out like the track");
    }

    /**
     * A configuration's per-point data - placements, termini, homes - rides into the generated file,
     * without being able to touch what the reduction decided.
     */
    @Test
    public void testConfigurationPointDataRidesIntoTheGeneratedFile() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey first = new TileKey("main", 1, 1);

        org.json.JSONObject config = session.getStore().getConfiguration("Default");

        org.json.JSONObject extras = new org.json.JSONObject();
        extras.put("maxTrainLength", 7);
        extras.put("loc", new org.json.JSONObject().put("name", "BR 218"));

        // Derived, not operational: terminus is what the "trains can turn round here" switch compiles
        // to, so one sitting in a configuration is a leftover and must not reach the generated file.
        // Carried through, it would put a terminus on the plain copy of a split square as well.
        extras.put("terminus", true);

        // an attempt to override a structural field, which must lose
        extras.put("s88", 999);

        config.put("points",
            new org.json.JSONObject().put(first.toString(), extras));

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        org.json.JSONObject builtPoint = null;

        for (Object o : built.getJSONArray("points"))
        {
            org.json.JSONObject p = (org.json.JSONObject) o;

            if (p.getInt("s88") != 11) continue;

            // The copy carrying the locomotive, where the square became several.  A placement is a
            // physical object and rides on exactly ONE copy; taking whichever was emitted last found a
            // copy with every other authored property and no "loc" on it.
            if (builtPoint == null || p.has("loc")) builtPoint = p;
        }

        assertNotNull(builtPoint, "the sensor should still be a Point, keyed by its real s88");
        assertEquals(builtPoint.getInt("maxTrainLength"), 7,
            "the configuration's operational data should ride in");
        assertFalse(builtPoint.has("terminus"),
            "but not a flag the builder decides for itself");
        assertEquals(builtPoint.getJSONObject("loc").getString("name"), "BR 218");
        assertEquals(builtPoint.getInt("s88"), 11, "a configuration cannot override the reduction");
    }

    /**
     * What the running layout knew is lifted back into the active configuration, keyed by tile, with
     * points that no longer exist dropped rather than carried forward onto nothing.
     */
    @Test
    public void testCaptureLiftsTheRunningLayoutIntoTheConfiguration() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        // what a running Layout would serialize: the generated names, plus operational state
        String generatedName = pointName(11);

        org.json.JSONObject running = new org.json.JSONObject();
        running.put("minDelay", 3);
        running.put("maxDelay", 9);

        org.json.JSONArray points = new org.json.JSONArray();

        org.json.JSONObject known = new org.json.JSONObject();
        known.put("name", generatedName);
        known.put("maxTrainLength", 7);
        known.put("loc", new org.json.JSONObject().put("name", "BR 218"));
        known.put("station", true); // structural - must not be captured
        known.put("terminus", true); // derived - must not be captured either
        points.put(known);

        org.json.JSONObject vanished = new org.json.JSONObject();
        vanished.put("name", "a point whose track was deleted");
        vanished.put("terminus", true);
        points.put(vanished);

        running.put("points", points);
        running.put("edges", new org.json.JSONArray());

        session.captureFromLayout(running.toString());

        org.json.JSONObject config = session.getStore().getConfiguration("Default");
        org.json.JSONObject captured = config.getJSONObject("points");

        assertEquals(captured.length(), 1, "only the point that still exists should be captured");

        org.json.JSONObject extras = captured.getJSONObject(
            new TileKey("main", 1, 1).toString());

        assertEquals(extras.getInt("maxTrainLength"), 7);
        assertEquals(extras.getJSONObject("loc").getString("name"), "BR 218");
        assertFalse(extras.has("station"), "structural fields are the reduction's, not captured");

        // Terminus is the builder's answer, not the user's.  Read back it would land on the square
        // somebody marked "trains can turn round here" and the next build would turn round every train
        // that passed - the setting asserting itself long after the switch that made it was turned off.
        assertFalse(extras.has("terminus"), "derived flags are not lifted off the running layout");

        // pace settings land in globals, and points/edges do not
        org.json.JSONObject globals = config.getJSONObject("globals");
        assertEquals(globals.getInt("minDelay"), 3);
        assertFalse(globals.has("points"));

        // and the round trip: what was captured is what the next build emits
        org.json.JSONObject rebuilt = new org.json.JSONObject(session.buildConfiguration());
        assertEquals(rebuilt.getInt("minDelay"), 3, "captured globals should feed the next build");

        for (Object o : rebuilt.getJSONArray("points"))
        {
            org.json.JSONObject p = (org.json.JSONObject) o;

            if (p.getInt("s88") == 11)
            {
                assertEquals(p.getInt("maxTrainLength"), 7);
                assertEquals(p.getJSONObject("loc").getString("name"), "BR 218");
            }
        }

        // capture by NAME lands in that configuration even when another one is active - which is what
        // keeps a refused load from having another configuration's state written over it at exit
        session.getStore().createConfiguration("Other", null);

        assertEquals(session.getStore().getActiveConfiguration(), "Default");

        session.captureFromLayout(running.toString(), "Other");

        assertTrue(session.getStore().getConfiguration("Other").has("points"),
            "the named configuration should receive the capture");
        assertEquals(session.getStore().getConfiguration("Default")
            .getJSONObject("globals").getInt("minDelay"), 3,
            "and the active one should keep what it already had");
    }

    /**
     * Editing marks the setup unsaved, and saving clears it - which is what decides whether closing the
     * editor has to ask.
     */
    @Test
    public void testEditingMarksTheSetupUnsavedUntilItIsSaved() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        assertFalse(session.isDirty(), "freshly written, so nothing is owed");

        session.setPointName(new TileKey("main", 1, 1), "Platform 1");

        assertTrue(session.isDirty());

        session.save();

        assertFalse(session.isDirty());
    }

    /**
     * Excluding a page takes its sensors out of the graph, which is what the flag is for.
     */
    @Test
    public void testExcludingAPageTakesItOutOfTheGraph() throws IOException
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        assertEquals(session.getReducer().getPoints().size(), 3);

        session.setPageExcluded("second", true);

        assertEquals(session.getReducer().getPoints().size(), 2,
            "an excluded page should contribute nothing");
    }

    // --- helpers ----------------------------------------------------------------------------------

    /**
     * The name the builder generated for the Point with this s88 - what a running Layout would call it.
     */
    private String pointName(int s88)
    {
        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        for (Object o : built.getJSONArray("points"))
        {
            org.json.JSONObject p = (org.json.JSONObject) o;

            if (p.getInt("s88") == s88) return p.getString("name");
        }

        throw new IllegalStateException("no Point with s88 " + s88);
    }

    /**
     * Two sensors with two plain tiles between them.
     */
    private LayoutDiagram runOfTrack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    private LayoutDiagram secondPage() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("second", 6, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 10, 21, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.setPageId("2");

        return page;
    }

    private int edgesBetween(int fromS88, int toS88)
    {
        int count = 0;

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            org.traincontrol.automationui.GraphReducer.ReducedPoint start =
                session.getReducer().getPoints().get(edge.getStart());
            org.traincontrol.automationui.GraphReducer.ReducedPoint end =
                session.getReducer().getPoints().get(edge.getEnd());

            if (start != null && end != null && start.getS88() == fromS88 && end.getS88() == toS88)
            {
                count++;
            }
        }

        return count;
    }

    private void delete(File file)
    {
        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children)
            {
                delete(child);
            }
        }

        file.delete();
    }

    // --- captions ----------------------------------------------------------------------------------

    /**
     * Renaming a station does not touch the track diagram.
     *
     * The whole reason captions moved out of the layout file, stated as a test.  A caption used to be
     * the text "Point:<name>" written into a text square, so renaming a station meant rewriting every
     * page showing the old name - which could fail halfway, and which regenerated those pages from a
     * model that silently dropped anything the parser could not understand.  A caption points at the
     * station\u2019s SQUARE now, so a rename is a change to the setup and to nothing else.
     */
    @Test
    public void testRenamingAStationTouchesNoPage() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        // Named first, so the migration below has something to attach the old label to
        session.open(Arrays.asList(page));
        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.save();

        // A caption of the old kind, written into the diagram.  It is here so that the snapshot taken
        // below is of a file this code demonstrably WRITES - the migration rewrites the page to strip
        // the label - rather than of one nothing ever touches, where "unchanged" would be true whatever
        // the rename did.
        page.addComponent(componentType.TEXT, 1, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "Bahnhof");

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(page));

        assertEquals(reopened.getCaptionTarget(caption), station,
            "the fixture did not migrate, so the rest of this test means nothing");

        byte[] before = Files.readAllBytes(pageFile.toPath());

        reopened.setPointName(station, "Hauptbahnhof");

        assertEquals(Files.readAllBytes(pageFile.toPath()), before,
            "renaming a station rewrote the track diagram, which is the thing this design removes");

        assertEquals(reopened.getCaptionTarget(caption), station,
            "and the caption still points at the same station, without having been told its new name");
    }

    /**
     * A caption written into the diagram by an older version is brought across, and an orphan is not.
     *
     * "Point:Bahnhof" naming a station this setup knows becomes a caption keyed to that station\u2019s
     * square.  One naming nothing - left behind by a configuration that no longer exists, which is what
     * four of the captions on the author\u2019s own layout were - is dropped rather than drawn as a
     * caption that looks live and does nothing.
     */
    @Test
    public void testALegacyLabelBecomesACaptionAndAnOrphanIsDropped() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        // authored before the migration runs, so the names are there to be matched against
        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.save();

        // and now the diagram carries two old-style labels: one live, one naming nothing
        page.addComponent(componentType.TEXT, 1, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "Bahnhof");

        page.addComponent(componentType.TEXT, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "GhostSiding");

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(page));

        assertTrue(reopened.getMigrationFailures().isEmpty(),
            "the pages should have been written: " + reopened.getMigrationFailures());

        assertEquals(reopened.getCaptionTarget(new TileKey("main", 1, 2)), station,
            "a label naming a station this setup knows becomes that station\u2019s caption");

        assertNull(reopened.getCaptionTarget(new TileKey("main", 3, 2)),
            "a label naming nothing is dropped rather than carried forward");

        // and the labels themselves are gone, so the migration does not run again on the next open
        assertEquals(page.getComponent(1, 2).getLabel(), "",
            "the old label is cleared once its caption exists");

        String written = new String(Files.readAllBytes(pageFile.toPath()), StandardCharsets.UTF_8);

        assertFalse(written.contains(AutonomySession.STATION_LABEL_PREFIX + "Bahnhof"),
            "the label that became a caption is still in the file, so the migration will run again");

        // The orphan stays, and this assertion is the point of the pair.  This test once required the
        // file to carry no "Point:" label at all, which was true while the migration stripped every one
        // it found - including the ones it could not match.  That was changed deliberately: not DRAWING
        // an orphan and deleting it from somebody's diagram are different acts, and a label naming a
        // station on a page this setup has been told to leave alone is not the program's to remove.
        // The assertion was left behind and contradicted its own sibling,
        // testAnUnrecognisedLabelIsLeftOnTheDiagram, until one of them was finally run.
        assertTrue(written.contains(AutonomySession.STATION_LABEL_PREFIX + "GhostSiding"),
            "the orphan was deleted from the user's diagram rather than merely left undrawn");
    }

    /**
     * A caption the user\u2019s own writing sits on top of is reported.
     *
     * The square belongs to the diagram, so the text is what gets drawn and the caption is what goes
     * quiet.  Nothing is deleted, and a station that looks captioned and shows nothing is exactly the
     * puzzle worth a warning.
     */
    @Test
    public void testACaptionCoveredByTextIsReported() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(caption, station);

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.CAPTION_COVERED),
            "nothing is on that square yet");

        // the user writes something of their own on the square the caption is drawn on
        page.addComponent(componentType.TEXT, 1, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, "Yard");

        session.rebuild();

        assertTrue(hasFinding(org.traincontrol.automationui.AutonomyChecks.CAPTION_COVERED),
            "a caption nobody can see has to say so");
    }

    /**
     * A square trains turn round at, from which no station can be reached, is reported.
     *
     * Adam, closing OB-113 - a route he expected and did not get, whose cause was the reversing point:
     * "We need to add a warning if a reversing point leads to nothing else."
     *
     * **What the check does NOT say, which is most of the value.** A reversing spur is a perfectly good
     * thing: a train runs in, turns, and comes back out to take a different branch. From the square
     * itself the whole railway is still reachable, so a healthy switchback never appears here - and
     * `MAY_TURN_ON_DEAD_END` above already covers the one thing that is worth saying about a stub. What
     * is left, and what this test builds, is a reversing square in a pocket of track with no station in
     * it at all: a train sent there turns round and is still nowhere.
     *
     * `TERMINUS_STRANDED` and `STATION_REACHES_NOTHING` say this for STATIONS and only for stations, so
     * a reversing point that is not one had nothing watching it.
     *
     * The second half is the half that keeps the list readable. Giving the pocket a station stops the
     * notice - a check that also fires on finished layouts is one that gets scrolled past, which this
     * list has been in before.
     *
     * MUTATION: dropping the `!reachesAStation` condition - so every reversing point is reported -
     * fails the second half; removing the call to `checkReversingGoesSomewhere` fails the first.
     */
    @Test
    public void testAReversingPointThatLeadsNowhereIsReported() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        // A second run of track, not joined to the first.  The station is over on the original one.
        page.addComponent(componentType.FEEDBACK, 1, 3, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 3, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey farEnd = new TileKey("main", 1, 3);
        TileKey reversing = new TileKey("main", 4, 3);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setPointName(farEnd, "Siding End");
        session.setPointName(reversing, "Turnback");

        // Reversing is a property of the ACTIVE configuration, and setPointProperty returns quietly
        // when there is none - so without these two lines the flag below does nothing at all.
        session.getStore().createConfiguration("Reversing", null);
        session.getStore().setActiveConfiguration("Reversing");

        session.setPointFlag(reversing, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        // Precondition, because the interesting failure and a fixture that never set the flag look
        // exactly alike from the finding list.
        assertTrue(session.mayTurnTiles().contains(reversing)
            || session.mandatoryTurnTiles().contains(reversing),
            "precondition: the square is not actually one where trains change direction");

        if (!hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE))
        {
            // Said with the whole list, because a check that did not fire and a check that fired about
            // some other square look identical from a boolean.
            StringBuilder saw = new StringBuilder();

            for (org.traincontrol.automationui.AutonomyChecks.Finding f : session.check())
            {
                saw.append("\n  ").append(f.getMessageKey()).append(" ").append(f.getSubject());
            }

            fail("a square trains turn round at, with no station reachable from it, was not reported. "
                + "Turning round somewhere a train can never get to a station from is the shape of "
                + "OB-113 - a route that does not exist and nothing on the diagram saying why. "
                + "Findings were:" + saw);
        }

        // The control: give that pocket of track a station, and the notice has to go.  This is the half
        // that separates the check from one that simply lists every reversing point on the railway.
        session.getStore().setStation(farEnd, true);

        session.rebuild();

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE),
            "the notice stayed after a station appeared within reach of the reversing square, so it is "
            + "reporting reversing points rather than reversing points that lead nowhere - which is how "
            + "a findings list stops being read");
    }

    /**
     * What a square is called, in the three cases there are.
     *
     * OB-112 put a name at the top of the diagram\u2019s right-click menu, and the menu in the editor
     * has had one for months - so the rule moved to the session, where both can ask it. This is that
     * rule: what somebody named it, then the sensor address printed on the diagram beside it, then
     * where it is. Each fallback is a real choice - a square with no name still has an address the
     * user can see on their own diagram, and one with neither has only its position - and each is a
     * separate assertion, because a rule that only ever gets its first case tested is a rule with two
     * untested branches under it.
     *
     * MUTATION: dropping the feedback branch - so an unnamed sensor square falls straight through to
     * its coordinates - fails the second assertion; returning the coordinates always fails all three.
     */
    @Test
    public void testASquareIsNamedByWhatIsKnownAboutIt() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey named = new TileKey("main", 1, 1);
        TileKey sensor = new TileKey("main", 4, 1);
        TileKey plain = new TileKey("main", 2, 1);

        session.setPointName(named, "  Bahnhof  ");

        assertEquals(session.describeTile(named), "Bahnhof",
            "a square somebody named is called that - trimmed, because the name is going into a menu "
            + "heading and the padding a text field leaves behind is not part of it");

        String sensorName = session.describeTile(sensor);

        assertTrue(sensorName.startsWith("s88 "),
            "an unnamed sensor square is known by the address printed on the diagram next to it, "
            + "which is the only thing about it the user can already see. Got: " + sensorName);

        assertEquals(session.describeTile(plain), "2,1",
            "a square with neither a name nor a sensor has only where it is");
    }

    /**
     * "May change direction here" is judged by where a train can GO, not by what arrives (OB-123).
     *
     * Adam, 2026-08-27: "this is wrong: the train can continue or reverse.  It should test for two
     * outgoing paths, not two incoming."
     *
     * The check counted ARRIVAL sides and called the setting pointless below two. Carrying on is a
     * DEPARTURE, so a square a train can only enter from one side but leave by either offers exactly
     * the choice the setting describes, and was being told the choice did not exist.
     *
     * That shape needs a one-way run to build - a plain feedback tile has two symmetric sides - which
     * is also how it arises on a real railway: directions are set along a stretch of track, and the
     * square at the end of that stretch can then be entered from one side while still being left by
     * either.
     *
     * BOTH halves in one test, because `hasFinding` asks by message key and not by square: a check
     * that fired about some other square would look exactly like the one under test passing. So the
     * far end of the line is set reversing too, as a control - it is a genuine dead end and must still
     * be reported - and the subjects are read to say which is which.
     *
     * MUTATION: putting `arrivalSides(tile).size() < 2` back reports the middle square and fails.
     */
    @Test
    public void testMayTurnIsJudgedByWhereATrainCanGo() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("OB123", null);
        session.getStore().setActiveConfiguration("OB123");

        TileKey middle = new TileKey("main", 3, 1);
        TileKey east = new TileKey("main", 5, 1);
        TileKey west = new TileKey("main", 1, 1);

        session.setPointName(middle, "LowerBack");
        session.setPointName(west, "Stub");

        session.setPointFlag(middle, AutonomyBuilder.CAN_REVERSE, true);
        session.setPointFlag(west, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        // Precondition: with track running both ways the middle is not the case under test at all.
        assertEquals(session.arrivalSides(middle).size(), 2,
            "precondition: the middle square does not have two arrivals to begin with, so making it "
            + "one-way below proves nothing");

        // Trains may run only eastward over the second half.
        assertTrue(session.setOneWayRun(middle, east) > 0,
            "precondition: the run east could not be made one-way, so the shape this test is about "
            + "was never built");

        session.rebuild();

        assertEquals(session.arrivalSides(middle).size(), 1,
            "precondition: the middle square still has two arrival sides after the run was made "
            + "one-way, so this is not the one-in-two-out case Adam described");

        java.util.List<String> reported = subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.MAY_TURN_ON_DEAD_END);

        // THE CASE. One way in, and still somewhere to carry on to.
        assertFalse(reported.contains("LowerBack"),
            "the square a train can enter from one side and leave by either was reported as a place "
            + "where \"every train turns round anyway\" - it can carry on east, which is the whole "
            + "of OB-123. Reported: " + reported);

        // THE CONTROL, which is what stops this passing by the check having been switched off.
        assertTrue(reported.contains("Stub"),
            "the end of the line - one way in, and the only way out is back the way you came - is no "
            + "longer reported, so the check has stopped saying anything rather than saying the right "
            + "thing. Reported: " + reported);
    }

    /**
     * Two ways in and one way out is still a real choice, for the train that came the other way.
     *
     * The case that separates the rule from the shortcut. Adam put it as "test for two outgoing
     * paths", and counting departures gets almost everything right - but a square with two arrivals
     * and ONE departure forces a turn on the train that arrived by the departure side and offers a
     * genuine choice to the one that did not. So "may change direction here" means what it says there,
     * and a departure count would call it pointless.
     *
     * Written because the mutation run found it: with only the one-in-two-out fixture, replacing the
     * per-arrival rule with `departures.size() < 2` passed. The refinement I had argued for in the
     * comments was not actually being tested by anything.
     *
     * MUTATION: `return departures.size() < 2` in place of the per-arrival answer fails this.
     */
    @Test
    public void testTwoWaysInAndOneOutIsStillAChoice() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("OB123b", null);
        session.getStore().setActiveConfiguration("OB123b");

        TileKey middle = new TileKey("main", 3, 1);
        TileKey west = new TileKey("main", 1, 1);

        session.setPointName(middle, "LowerBack");

        session.setPointFlag(middle, AutonomyBuilder.CAN_REVERSE, true);

        // Trains may run only EASTWARD over the western half, so the middle square can be arrived at
        // from the west but not departed to it.
        assertTrue(session.setOneWayRun(west, middle) > 0,
            "precondition: the western run could not be made one-way");

        session.rebuild();

        assertEquals(session.arrivalSides(middle).size(), 2,
            "precondition: the middle square no longer has two arrivals, so this is not the "
            + "two-in-one-out case this test is about");

        java.util.List<String> reported = subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.MAY_TURN_ON_DEAD_END);

        assertFalse(reported.contains("LowerBack"),
            "a square with two ways in and one way out was called a place where every train turns "
            + "round anyway. The western half runs one way EASTWARD, so the train that arrived from "
            + "the west can carry on east; only the one that came from the east is forced to turn - "
            + "it cannot go back west. Reported: " + reported);
    }

    /**
     * The findings walk the railway a TRAIN can use, red arrows included (OB-120).
     *
     * Found by a reviewer, and it is this project's most familiar defect wearing my own handwriting.
     * When OB-120 taught the graph about barred arrivals I gave `reachableTiles` an overload that takes
     * them, wrote a comment on it saying it existed "so the two walks cannot disagree", tested the
     * reducer - and changed neither caller. Both went on asking the barred-less form. The test I wrote
     * even says in its javadoc that teaching findPath and not reachableTiles "would have broken that
     * quietly", which is exactly what shipped.
     *
     * What it cost: bars only ever REMOVE runs, so the findings saw more railway than a train can use.
     * A station reachable only by a side that refuses arrivals counted as reachable, and the warning
     * that would have said so stayed silent - while the path test on the same screen refused that very
     * run. OB-122 is the ticket where Adam confirmed these warnings tell the truth and went and fixed
     * his diagram on the strength of one.
     *
     * The two halves are asserted together on purpose: without the bar the station is fine, with it
     * the station is stranded. Either alone could pass for the wrong reason.
     *
     * MUTATION: dropping `barred` at either reachableTiles call site fails this.
     */
    @Test
    public void testTheFindingsObeyTheRedArrows() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("OB120", null);
        session.getStore().setActiveConfiguration("OB120");

        TileKey west = new TileKey("main", 1, 1);
        TileKey east = new TileKey("main", 5, 1);

        session.setStation(west, true);
        session.setPointName(west, "WestEnd");

        session.setStation(east, true);
        session.setPointName(east, "EastEnd");

        session.rebuild();

        // A railway where the two stations can reach each other is the control: without it, a finding
        // that fires for some unrelated reason would look like the bar working.
        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING)
            .contains("WestEnd"),
            "precondition: the west end already reaches nothing before anything was barred, so this "
            + "fixture cannot show what barring does");

        // Now shut the only way into the east end.
        java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
            session.arrivalSides(east);

        assertFalse(ways.isEmpty(), "precondition: the east end has no arrival sides to bar");

        session.setBarredArrivals(east, new java.util.LinkedHashSet<>(ways));

        session.rebuild();

        java.util.List<String> stranded =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING);

        java.util.List<String> terminus =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED);

        assertTrue(stranded.contains("WestEnd") || terminus.contains("WestEnd"),
            "the west end can now reach no station a train may actually enter - every way into the "
            + "east end is barred - and nothing said so. The findings are walking runs the railway "
            + "refuses, which is the half of OB-120 that never reached its call sites. Reported: "
            + stranded + " / " + terminus);
    }

    /**
     * BOTH walks in the findings honour the red arrows, not just the one a fixture happens to drive.
     *
     * The test above proves it for the station walk, by running a railway. It does not reach
     * `checkReversingGoesSomewhere`, which walks the same graph for a different question - and the
     * mutation run showed that: dropping `barred` from that second call site left the suite green.
     *
     * Which is the very shape this whole ticket is about. OB-120 added an overload, tested the
     * overload, and left both callers asking the old one. Fixing one caller and testing only that one
     * would have left the other exactly where it was.
     *
     * Read rather than run, and that is a real limit: building a reversing point whose reachable
     * stations depend on a bar needs a fixture this class does not have. What this catches is a call
     * site quietly losing its bars, which is how the defect arrived both times.
     *
     * MUTATION: dropping `barred` from either reachableTiles call fails this.
     */
    @Test
    public void testBothFindingWalksAreGivenTheRedArrows() throws Exception
    {
        String checks = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automationui/AutonomyChecks.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int asked = 0;
        int withBars = 0;

        for (int at = checks.indexOf("reachableTiles("); at >= 0;
            at = checks.indexOf("reachableTiles(", at + 1))
        {
            asked++;

            // To the end of the STATEMENT, not to the first closing bracket.
            //
            // `reachableTiles(station.getTile(), ...)` closes an inner bracket first, so stopping
            // there read a perfectly correct call as missing its bars - this check failed on code that
            // was right, which is the other way for a test to be wrong and just as expensive.
            int end = checks.indexOf(';', at);

            if (end > at && checks.substring(at, end).contains("barred")) withBars++;
        }

        assertTrue(asked > 0, "nothing in the findings walks the graph any more");

        assertEquals(withBars, asked,
            "only " + withBars + " of the " + asked + " reachability walks in the findings are given "
            + "the barred arrivals. Bars only ever REMOVE runs, so a walk without them sees more "
            + "railway than a train can use - and the warning that a station reaches nothing stays "
            + "silent about a station reachable only through a side that refuses arrivals");
    }

    /**
     * A restricted piece of TRACK gets its arrow on the ordinary diagram, not only a sensor (FR-037).
     *
     * Adam: "the arrows are not showing up for me when I toggle the option."  They were - on the few
     * squares that happen to be Points, which on a real railway is not where a restriction lives. A
     * one-way run is a property of track: straights, curves, the squares either side of a switch, and
     * almost none of those carry a sensor.
     *
     * **This is the test that was missing rather than the one that failed.** The four I wrote for
     * FR-037 checked the preference, the guards, the menu grouping and the refresh call. Every one was
     * true, and not one of them could notice that the arrows never reached any track. Asking "does a
     * restricted straight get a mark" would have failed the moment it was written.
     *
     * The option is set through the preference the code reads, and put back afterwards, so this cannot
     * leave the running application drawing arrows nobody asked for.
     *
     * MUTATION: computing the marks after the Point test - which is what shipped - fails this.
     */
    @Test
    public void testARestrictedStraightGetsAnArrowOnTheDiagram() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("FR037", null);
        session.getStore().setActiveConfiguration("FR037");

        // A STRAIGHT, deliberately: the feedbacks are at 1, 3 and 5, so this square is track and
        // nothing else - exactly the kind the option appeared not to work on.
        TileKey straight = new TileKey("main", 2, 1);
        TileKey west = new TileKey("main", 1, 1);
        TileKey east = new TileKey("main", 5, 1);

        assertFalse(session.getReducer().getPoints().containsKey(straight),
            "precondition: the square being tested is a Point, so it would have drawn an arrow even "
            + "with the defect this test is about");

        assertTrue(session.setOneWayRun(west, east) > 0,
            "precondition: the run could not be made one-way, so there is no restriction to draw");

        session.rebuild();

        boolean was = org.traincontrol.gui.TrainControlUI.getPrefs().getBoolean(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, false);

        try
        {
            org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, false);

            org.traincontrol.automationui.TileAnnotation off = session.staticAnnotationFor(straight);

            assertTrue(off == null || off.isBlank(),
                "a plain piece of track is described on the diagram with the option OFF, so the "
                + "default has stopped being a bare diagram");

            org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

            org.traincontrol.automationui.TileAnnotation on = session.staticAnnotationFor(straight);

            assertNotNull(on,
                "a one-way straight is described as nothing at all with the option ON - which is what "
                + "Adam saw: the arrows were added after the test that returns null for anything that "
                + "is not a sensor, and a restriction is a fact about track");

            assertFalse(on.isBlank(),
                "the annotation for a one-way straight carries nothing to draw, so the square stays "
                + "as bare as it was with the option off");
        }
        finally
        {
            org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, was);
        }
    }

    /**
     * The squares a given check named, so a test can tell which one it fired about.
     */
    private java.util.List<String> subjectsOf(String messageKey)
    {
        java.util.List<String> subjects = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) subjects.add(finding.getSubject());
        }

        return subjects;
    }

    private boolean hasFinding(String messageKey)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) return true;
        }

        return false;
    }

    /**
     * A page whose file really exists, so that "this was not written" is a claim that can be checked.
     */
    private File pageFile;

    /**
     * A page whose middle sensor has track on BOTH sides, so a train can arrive at it either way.
     *
     * pageOnDisk puts its two sensors at the ends of a line, where there is only one way in - which is
     * fine for naming and captions and useless for anything about arrival sides, since a square with
     * one way in has no choice to restrict.
     */
    private LayoutDiagram pageWithATwoEndedStation() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(),
            "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + pageFile.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    private LayoutDiagram pageOnDisk() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(),
            "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + pageFile.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * A caption on a blank square is still there after a save.
     *
     * placeCaption prefers blank space beside a platform - it is the most readable place there is - and
     * the migration captions a square whose text it then empties, which removes that square from the
     * layout file altogether.  Reconciling on "does this square hold a component" therefore deleted, on
     * the very next save, both the captions the user had just placed and every caption the migration had
     * just created.  A caption is about a station; the square it is drawn on need hold nothing.
     */
    @Test
    public void testACaptionOnABlankSquareSurvivesASave() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        // 1,2 is blank: pageOnDisk draws track along row 1 only
        TileKey caption = new TileKey("main", 1, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(caption, station);

        session.save();

        assertEquals(session.getCaptionTarget(caption), station,
            "saving deleted a caption because its square carries no track");

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(page));

        assertEquals(reopened.getCaptionTarget(caption), station, "and it is gone from disk too");
    }

    /**
     * A caption is never filed outside the part of the page the diagram draws.
     *
     * getComponent answers null both for a blank square and for one off the edge, and the search for
     * somewhere to put a caption read null as "free" - so a station against an edge had its caption
     * filed one square outside the drawn area.  It showed in the editor, which pads its grid, and never
     * on the running diagram; and the "this station is not shown anywhere" warning went quiet, because
     * a caption did exist.
     */
    @Test
    public void testACaptionIsNeverPlacedOutsideTheDrawnArea() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        // A station hard against the top-left of the drawn area, with its only neighbour to the east
        page.addComponent(componentType.FEEDBACK, 0, 0, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 1, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.setPageId("1");
        page.checkBounds();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 0, 0);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Kopfbahnhof");

        session.placeCaption(station);

        for (TileKey where : session.getCaptions().keySet())
        {
            assertTrue(where.getX() >= page.getMinx() && where.getX() <= page.getMaxx()
                    && where.getY() >= page.getMiny() && where.getY() <= page.getMaxy(),
                "a caption at " + where + " is outside the area the diagram draws, so nothing shows it");
        }
    }

    /**
     * A label naming a station this setup has never heard of is left exactly where it is.
     *
     * Orphans are not drawn - that was the instruction - but not drawing one and deleting it from
     * somebody\u2019s diagram are different acts.  The migration used to strip every "Point:" label it
     * found, including the ones it could not match, so a label naming a station on a page left out of
     * autonomy was destroyed on the strength of this program not currently looking at that page.
     */
    @Test
    public void testAnUnrecognisedLabelIsLeftOnTheDiagram() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        page.addComponent(componentType.TEXT, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "GhostSiding");

        AutonomySession opened = new AutonomySession(layout);
        opened.open(Arrays.asList(page));

        assertNull(opened.getCaptionTarget(new TileKey("main", 3, 2)),
            "a label naming nothing is not drawn as a caption");

        assertEquals(page.getComponent(3, 2).getLabel(),
            AutonomySession.STATION_LABEL_PREFIX + "GhostSiding",
            "but it is still the user\u2019s text, and this program does not get to delete it");
    }

    /**
     * A page whose labels all name stations this setup never heard of is not written at all.
     *
     * The migration leaves an unrecognised "Point:" label exactly where it is - deleting it would
     * destroy the only record that it existed.  But it saved every page it had LOOKED at, including
     * those, and because the labels stay they are found again on the next open.  So a user who has
     * never used autonomy, and whose diagram carries labels from the hand-written configuration this
     * feature replaced, had a setup file created and their layout files rewritten on every single
     * launch.  Under a sync lock that is an error dialog at every start that nothing in the UI can
     * clear, and the sample layout's four orphan labels make it the shipped default.
     *
     * Reading the bytes is the point: "did this rewrite the file" is the actual question, and a test
     * that asked whether the labels survived would have passed throughout.
     */
    @Test
    public void testAPageThatCannotBeMigratedIsNeverWritten() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        page.addComponent(componentType.TEXT, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "GhostSiding");

        byte[] before = Files.readAllBytes(pageFile.toPath());

        AutonomySession opened = new AutonomySession(layout);
        opened.open(Arrays.asList(page));

        assertEquals(Files.readAllBytes(pageFile.toPath()), before,
            "the page was rewritten even though nothing on it could become a caption");

        assertFalse(new File(layout, "config/autonomy/setup.json").exists(),
            "a setup file was created for a layout that has no autonomy and gained no captions");
    }

    /**
     * Names out of a legacy autonomy.json land on the squares carrying the same sensors.
     *
     * The graph this replaces held points by name and recorded the s88 each watched; the diagram
     * derives its points from the feedback squares themselves.  The sensor is the one thing both
     * models agree on, so it is what the two are matched by.
     *
     * The names were never derivable from a diagram - the track's shape is, but what any of it is
     * CALLED is a decision - so without this every upgrading user would enter them all again.
     */
    @Test
    public void testLegacyNamesLandOnTheSquaresCarryingTheirSensors() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        org.json.JSONArray points = new org.json.JSONArray();

        // pageOnDisk puts a feedback with raw address 11 at 1,1 - and the raw address is what an
        // autonomy Point's s88 has always meant
        org.json.JSONObject named = new org.json.JSONObject();
        named.put("name", "Hauptbahnhof");
        named.put("station", true);
        named.put("s88", 11);
        named.put("maxTrainLength", 240);
        points.put(named);

        org.json.JSONObject elsewhere = new org.json.JSONObject();
        elsewhere.put("name", "NotOnThisDiagram");
        elsewhere.put("s88", 9999);
        points.put(elsewhere);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        TileKey tile = new TileKey("main", 1, 1);

        assertEquals(session.getStore().getPointName(tile), "Hauptbahnhof",
            "the name did not reach the square carrying its sensor");

        assertTrue(session.getStore().isStation(tile), "the station flag did not come across");

        assertEquals(session.getStore().getTileLength(tile), 240, "the length did not come across");

        assertEquals(result.matched, 1, "exactly one point should have matched");

        assertEquals(result.unmatched, Arrays.asList("NotOnThisDiagram"),
            "a point whose sensor is not on this diagram must be reported, not silently dropped");
    }

    /**
     * A square that already has a name keeps it.
     *
     * Same rule as importing a configuration: this fills gaps, it does not overwrite somebody's work
     * with a file's.  Without the assertion a fix that adopted the file wholesale would look correct
     * against the test above.
     */
    @Test
    public void testLegacyNamesDoNotOverwriteNamesAlreadyEntered() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey tile = new TileKey("main", 1, 1);

        session.setPointName(tile, "The name I typed");

        org.json.JSONArray points = new org.json.JSONArray();

        org.json.JSONObject named = new org.json.JSONObject();
        named.put("name", "The name in the file");
        named.put("s88", 11);
        points.put(named);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(session.getStore().getPointName(tile), "The name I typed",
            "importing overwrote a name that was already there");

        assertEquals(result.skipped, 1, "the skip should be counted and reported");
    }

    /**
     * The locomotive that was standing on a Point is put back on the square carrying its sensor.
     *
     * The old graph recorded the locomotive with the speed, arrival and departure functions and train
     * length it was placed with; the builder reads that same shape back out, so the object is carried
     * over whole rather than picked apart and rebuilt.
     *
     * A placement belongs to a configuration and not to the shared half - it is where a train happens
     * to be standing, not a decision about the track - so it is written there, keyed by tile.
     */
    @Test
    public void testALegacyImportPutsTheLocomotivesBack() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONObject standing = new org.json.JSONObject();
        standing.put("name", "Q 343");
        standing.put("speed", 35);
        standing.put("arrivalFunc", 15);

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St21");
        point.put("station", true);
        point.put("s88", 11);
        point.put("loc", standing);
        point.put("home", "Q 343");

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(result.placed, 1, "the locomotive was not placed");

        org.json.JSONObject extras = session.getStore().getConfiguration("Restored")
            .getJSONObject("points").getJSONObject(new TileKey("main", 1, 1).toString());

        assertEquals(extras.getJSONObject("loc").getString("name"), "Q 343",
            "the locomotive did not land on the square carrying its sensor");

        assertEquals(extras.getJSONObject("loc").getInt("arrivalFunc"), 15,
            "the placement was rebuilt rather than carried over, so its settings were lost");

        assertEquals(extras.getString("home"), "Q 343", "the home did not come across");
    }

    /**
     * A square somebody has already named still gets its locomotive back.
     *
     * The placement is about the SQUARE, so it must not be skipped along with the name - which it was
     * when both were decided by one branch.
     */
    @Test
    public void testALegacyImportPlacesEvenWhereTheNameIsKept() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey tile = new TileKey("main", 1, 1);

        session.setPointName(tile, "The name I typed");

        org.json.JSONObject standing = new org.json.JSONObject();
        standing.put("name", "MY 1106");

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St23");
        point.put("s88", 11);
        point.put("loc", standing);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(result.skipped, 1, "the name should have been left alone");

        assertEquals(result.placed, 1, "the locomotive was skipped along with the name");

        assertEquals(session.getStore().getPointName(tile), "The name I typed",
            "importing overwrote a name that was already there");
    }

    /**
     * A legacy terminus and a legacy reversing point both come back as squares that turn trains round.
     *
     * Neither word can be written down any more - the build derives both, and which one it emits
     * follows from whether the square is a station.  What the old graph was recording in both cases is
     * that every arriving train reverses, and that is authored as mustReverse, so one flag restores
     * both readings.
     *
     * The station case of "reversing" is the old reversing station, which said two things at once: it
     * turns trains round AND autonomy never chooses it.  Those are separate ideas now, so it has to
     * take the parking flag as well - without it, importing would quietly turn a shunting neck into a
     * destination trains get sent to.
     */
    @Test
    public void testALegacyImportRestoresTerminiAndReversingPoints() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONObject terminus = new org.json.JSONObject();
        terminus.put("name", "BottomMainCTerm");
        terminus.put("station", true);
        terminus.put("terminus", true);
        terminus.put("s88", 11);

        // The other feedback pageOnDisk draws, raw address 12 at 4,1
        org.json.JSONObject berth = new org.json.JSONObject();
        berth.put("name", "ParkingTrack10");
        berth.put("station", true);
        berth.put("reversing", true);
        berth.put("s88", 12);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(terminus);
        points.put(berth);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(result.reversing, 2, "both squares should have been marked");

        TileKey terminusTile = new TileKey("main", 1, 1);
        TileKey berthTile = new TileKey("main", 4, 1);

        assertEquals(session.getPointProperty(terminusTile, AutonomyBuilder.MUST_REVERSE),
            Boolean.TRUE, "the terminus does not turn trains round");

        assertEquals(session.getPointProperty(berthTile, AutonomyBuilder.MUST_REVERSE),
            Boolean.TRUE, "the reversing station does not turn trains round");

        assertEquals(session.getPointProperty(berthTile, AutonomyBuilder.PARKING), Boolean.TRUE,
            "an old reversing STATION is a berth, and without the parking flag autonomy would start "
                + "choosing it as a destination");

        assertNull(session.getPointProperty(terminusTile, AutonomyBuilder.PARKING),
            "a terminus is an ordinary destination, and must not have been shut to autonomy");
    }

    /**
     * A square that already says something about reversing keeps what it says.
     *
     * The same gap-filling rule the names and placements follow, asserted because the marking is set
     * through two properties at once and a fix that wrote them unconditionally would look correct
     * against the test above.
     */
    @Test
    public void testALegacyImportDoesNotOverrideReversingAlreadySet() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey tile = new TileKey("main", 1, 1);

        // Somebody has already said this square MAY turn trains, which is not the same as must
        session.setPointFlag(tile, AutonomyBuilder.CAN_REVERSE, true);

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St01rev");
        point.put("station", true);
        point.put("terminus", true);
        point.put("s88", 11);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(result.reversing, 0, "nothing should have been marked");

        assertNull(session.getPointProperty(tile, AutonomyBuilder.MUST_REVERSE),
            "importing promoted a may-turn square to must-turn, which is a different instruction");

        assertEquals(session.getPointProperty(tile, AutonomyBuilder.CAN_REVERSE), Boolean.TRUE,
            "the may-turn marking somebody made was lost");
    }

    /**
     * Priorities, speed multipliers, exclusions and a station's switch come across too.
     *
     * The builder passes unknown extras straight through to the built graph, so these need no
     * translation - only carrying.  They are per-point operational settings rather than decisions
     * about the track, so they go to the configuration, beside the placement.
     *
     * The exclusions are asserted by content rather than by identity because they must be a COPY: a
     * JSONArray handed straight over would still be the one the caller's parsed file holds, and
     * editing the exclusions here later would reach back into that.
     */
    @Test
    public void testALegacyImportCarriesThePerPointSettings() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONArray excluded = new org.json.JSONArray();
        excluded.put("ER 2035 DSB");
        excluded.put("MY 1150 DSB");

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St21");
        point.put("station", true);
        point.put("s88", 11);
        point.put("priority", -3);
        point.put("speedMultiplier", 0.75);
        point.put("excludedLocs", excluded);
        point.put("active", false);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(result.settings, 4, "all four settings should have been carried");

        TileKey tile = new TileKey("main", 1, 1);

        assertEquals(session.getPointProperty(tile, "priority"), -3, "the priority did not come across");

        assertEquals(session.getPointProperty(tile, "speedMultiplier"), 0.75,
            "the speed multiplier did not come across");

        assertEquals(session.getPointProperty(tile, "active"), Boolean.FALSE,
            "the station's switch did not come across");

        org.json.JSONArray carried = (org.json.JSONArray) session.getPointProperty(tile, "excludedLocs");

        assertNotNull(carried, "the exclusions did not come across");

        assertEquals(carried.length(), 2, "not every excluded locomotive came across");

        assertEquals(carried.getString(0), "ER 2035 DSB", "the exclusions came across wrong");

        assertNotSame(carried, excluded,
            "the exclusions are the file's own array, so editing them here would edit the file's copy");
    }

    /**
     * A setting already present is kept, so a second import cannot undo an edit made after the first.
     */
    @Test
    public void testALegacyImportDoesNotOverrideSettingsAlreadySet() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey tile = new TileKey("main", 1, 1);

        session.setPointProperty(tile, "priority", 5);

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St21");
        point.put("station", true);
        point.put("s88", 11);
        point.put("priority", -3);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(result.settings, 0, "nothing should have been carried");

        assertEquals(session.getPointProperty(tile, "priority"), 5,
            "importing overwrote a priority that had already been set");
    }

    /**
     * The diagram shows the imported names without being reloaded.
     *
     * The squares, their names, their station markers and their captions are all drawn from the
     * REDUCTION, which is derived from a snapshot of the authored data taken when the session opened.
     * Writing to the store therefore changes nothing anybody can see until that derivation is redone -
     * so the import wrote every name correctly and the diagram went on showing what it knew before,
     * which from the outside is indistinguishable from the import having done nothing.
     *
     * Asserted against the reduction rather than the store for exactly that reason: the store was
     * always right.
     */
    @Test
    public void testTheDiagramSeesTheImportWithoutBeingReloaded() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey tile = new TileKey("main", 1, 1);

        // Not null: a square nobody has named still gets a name, generated from its page and
        // coordinates, so that every Point in the built graph has one.  The precondition worth
        // asserting is therefore that it is not ALREADY called what the import is about to call it.
        assertNotEquals(session.getReducer().getPoints().get(tile).getName(), "Hauptbahnhof",
            "precondition: this square is not already named what the import will name it");

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "Hauptbahnhof");
        point.put("station", true);
        point.put("s88", 11);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        session.importLegacy(legacy);

        assertEquals(session.getReducer().getPoints().get(tile).getName(), "Hauptbahnhof",
            "the derivation the diagram draws from still holds the pre-import name");

        assertTrue(session.getReducer().getPoints().get(tile).isStation(),
            "the derivation does not show the square as a station yet");
    }

    /**
     * A terminus and a station's switch survive being exported and imported again.
     *
     * Both are DERIVED at build time - terminus from mustReverse plus the station flag, active from a
     * property the build copies through - so neither can be looked for in the store, and neither shows
     * up until the derivation is redone.  Importing a bundle wrote the configuration holding both and
     * never re-derived, so they arrived and stayed invisible, which is indistinguishable from their
     * not having arrived.
     *
     * Asserted against the BUILT graph rather than the store or the reduction: the store was always
     * right, and the reduction does not carry these at all.  What matters is what the running model
     * would be handed.
     */
    @Test
    public void testATerminusAndAStationSwitchSurviveAnExportAndImport() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey tile = new TileKey("main", 1, 1);

        session.getStore().createConfiguration("Adam 1", null);
        session.getStore().setActiveConfiguration("Adam 1");

        session.getStore().setStation(tile, true);
        session.setPointName(tile, "Hauptbahnhof");

        // What a terminus is authored as, and a station's own switch
        session.setPointProperty(tile, AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);
        session.setPointProperty(tile, "active", Boolean.FALSE);

        session.rebuild();

        assertTrue(session.buildConfigurationForInspection().contains("\"terminus\""),
            "precondition: the source setup builds a terminus");

        org.json.JSONObject bundle = session.getStore().exportBundle("Adam 1");

        assertNotNull(bundle, "there was nothing to export");

        // A different setup entirely - the same track, nothing set up on it
        File second = Files.createTempDirectory("tc-autonomy-roundtrip").toFile();

        try
        {
            AutonomySession fresh = new AutonomySession(second);
            fresh.open(Arrays.asList(page));

            fresh.importBundle("Adam 1", new org.json.JSONObject(bundle.toString()));
            fresh.getStore().setActiveConfiguration("Adam 1");

            fresh.rebuild();

            String built = fresh.buildConfigurationForInspection();

            assertTrue(built.contains("Hauptbahnhof"), "the name did not survive:\n" + built);

            assertTrue(built.contains("\"terminus\""),
                "the terminus did not survive the round trip, so every square that turned trains "
                    + "round came back an ordinary one:\n" + built);

            assertTrue(built.contains("\"active\""),
                "the station's switch did not survive the round trip:\n" + built);
        }
        finally
        {
            delete(second);
        }
    }

    /**
     * A sensor carried by more than one square is reported rather than guessed at.
     *
     * Two squares on one s88 is ordinary - a station and its approach guard - and on a layout whose
     * pages repeat a section it happens across pages too.  A legacy file names ONE point per sensor,
     * so nothing in it says which square was meant, and taking whichever came last would land a
     * station on the wrong page without a word.  Excluding the duplicating pages first is what makes
     * the rest of an import unambiguous, and this is what tells somebody they need to.
     */
    @Test
    public void testALegacyImportRefusesASensorOnTwoSquares() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        // The same s88 twice, which is what a duplicated page looks like to the reduction
        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);

        page.setPageId("1");
        page.checkBounds();

        session.open(Arrays.asList(page));

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "Ambiguous");
        point.put("station", true);
        point.put("s88", 11);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        assertEquals(result.matched, 0, "a sensor on two squares must not be matched to either");

        assertEquals(result.unmatched, Arrays.asList("Ambiguous"),
            "the ambiguous point must be reported, so somebody knows to exclude the repeated page");

        assertNull(session.getStore().getPointName(new TileKey("main", 1, 1)),
            "a name was written to one of the two squares anyway");

        assertNull(session.getStore().getPointName(new TileKey("main", 3, 1)),
            "a name was written to the other square anyway");
    }

    /**
     * A page repeating an earlier page's sensor is left out; the earlier page stays in.
     *
     * A layout that draws the same track twice - an overview and a detail view of one yard - gives two
     * squares the same s88, and nothing downstream can tell which one a train is on.  The reduction
     * makes a Point of each, so one sensor becomes two destinations, and a legacy import cannot decide
     * which square a name belongs to.
     *
     * Earliest page wins, in the order the layout lists them: it is the one a reader thinks of as the
     * real one, and it is the only rule that does not depend on which page happens to be open.
     */
    @Test
    public void testAPageRepeatingASensorIsLeftOut() throws Exception
    {
        LayoutDiagram first = pageOnDisk();

        LayoutDiagram repeat = new LayoutDiagram("repeat", 6, 4, null, null);

        // 11 is pageOnDisk's own sensor; 21 is this page's alone
        repeat.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        repeat.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        repeat.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 10, 21, accessoryDecoderType.MM2, null);

        repeat.setPageId("2");
        repeat.checkBounds();

        session.open(Arrays.asList(first, repeat));

        List<String> shut = session.excludeRepeatedSensorPages();

        assertEquals(shut, Arrays.asList("repeat"),
            "the page repeating an earlier sensor should have been the one left out");

        assertTrue(session.getStore().getExcludedPages().contains("repeat"),
            "the repeating page is not actually excluded");

        assertFalse(session.getStore().getExcludedPages().contains("main"),
            "the earlier page was excluded, which is the wrong one of the two");
    }

    /**
     * Pages that share nothing are all left in.
     *
     * The precondition that keeps the test above honest: a rule that excluded every page after the
     * first would satisfy it and be useless.
     */
    @Test
    public void testPagesWithDistinctSensorsAreAllKept() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk(), secondPage()));

        assertTrue(session.excludeRepeatedSensorPages().isEmpty(),
            "nothing repeats between these pages, so nothing should have been shut");

        assertTrue(session.getStore().getExcludedPages().isEmpty(),
            "a page was excluded even though it shares no sensor with any other");
    }

    /**
     * Running again over a settled setup changes nothing.
     *
     * Worth pinning because the excluded pages are SHARED, not per-configuration: anything here that
     * re-asserted itself would fight the user, and the page checkboxes are the whole point.
     *
     * Note what this does NOT promise.  A page the user deliberately switches back on WOULD be shut
     * again by another run - the method has no record of having been overruled, and inventing one to
     * carry that would be a second source of truth beside the checkbox itself.  What protects that
     * choice is the caller: this runs only when the first configuration on a layout is created, which
     * is the one moment there are no decisions to overrule.  If a second call site ever appears, this
     * is the test whose comment explains why it must not.
     */
    @Test
    public void testRunningAgainOverASettledSetupChangesNothing() throws Exception
    {
        LayoutDiagram first = pageOnDisk();

        LayoutDiagram repeat = new LayoutDiagram("repeat", 6, 4, null, null);
        repeat.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        repeat.setPageId("2");
        repeat.checkBounds();

        session.open(Arrays.asList(first, repeat));

        assertEquals(session.excludeRepeatedSensorPages(), Arrays.asList("repeat"),
            "precondition: the first run shuts the repeating page");

        assertTrue(session.excludeRepeatedSensorPages().isEmpty(),
            "a second run reported shutting something that was already shut");

        assertEquals(session.getStore().getExcludedPages().size(), 1,
            "a second run changed which pages are excluded");
    }

    /**
     * A placement naming a locomotive this database does not have is refused, not written in.
     *
     * The running model does not skip an unknown locomotive - it invalidates the WHOLE layout, by
     * name, with errorLocomotiveNotInDatabase.  So an old graph naming one that has since been renamed
     * or deleted would have imported cleanly and then produced a setup that refuses to open, reported
     * as a locomotive problem with nothing to say the import put it there.
     *
     * Refused here and named instead, which is a thing the user can act on.
     */
    @Test
    public void testAPlacementForAnUnknownLocomotiveIsRefused() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONObject standing = new org.json.JSONObject();
        standing.put("name", "Sold Years Ago");

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St21");
        point.put("station", true);
        point.put("s88", 11);
        point.put("loc", standing);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        Set<String> known = new LinkedHashSet<>(Arrays.asList("Q 343", "MY 1106"));

        AutonomySession.LegacyImport result = session.importLegacy(legacy, known);

        assertEquals(result.placed, 0, "a locomotive the database does not have must not be placed");

        assertEquals(result.unknownLocomotives, Arrays.asList("Sold Years Ago"),
            "the unknown locomotive must be named, since it is the reason a placement is missing");

        assertEquals(session.getStore().getPointName(new TileKey("main", 1, 1)), "St21",
            "the name should still have been imported - only the placement was refused");
    }

    /**
     * A locomotive named at two points is placed once, and the second is reported.
     *
     * One locomotive cannot stand in two places, and the running model says so by invalidating the
     * layout rather than by ignoring the second.  An old graph that has drifted names the same
     * locomotive twice easily enough.
     */
    @Test
    public void testALocomotiveNamedTwiceIsPlacedOnce() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONArray points = new org.json.JSONArray();

        // pageOnDisk draws feedback 11 at 1,1 and feedback 12 at 4,1
        for (int sensor : new int[] {11, 12})
        {
            org.json.JSONObject standing = new org.json.JSONObject();
            standing.put("name", "Q 343");

            org.json.JSONObject point = new org.json.JSONObject();
            point.put("name", "St" + sensor);
            point.put("station", true);
            point.put("s88", sensor);
            point.put("loc", standing);

            points.put(point);
        }

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy,
            new LinkedHashSet<>(Arrays.asList("Q 343")));

        assertEquals(result.placed, 1, "the locomotive should have been placed exactly once");

        assertEquals(result.duplicateLocomotives, Arrays.asList("Q 343"),
            "the second placement must be reported rather than silently dropped");
    }

    /**
     * Without a database to check against, placements are taken as given.
     *
     * The check is the caller's to supply - the session has no locomotive database of its own - and
     * passing nothing must not mean refusing everything.
     */
    @Test
    public void testPlacementsAreTakenAsGivenWhenThereIsNothingToCheckAgainst() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        org.json.JSONObject standing = new org.json.JSONObject();
        standing.put("name", "Anything At All");

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "St21");
        point.put("s88", 11);
        point.put("loc", standing);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy, null);

        assertEquals(result.placed, 1, "with no database given, the placement should be taken as-is");

        assertTrue(result.unknownLocomotives.isEmpty(), "nothing can be unknown with nothing to check");
    }

    /**
     * An imported station is labelled on its own square, and so raises no unlabelled-station error.
     *
     * Every station has to be shown on the diagram - it is an error not to be - so an import that
     * named fifty stations and captioned none would have handed back fifty errors to clear by hand,
     * which is not a migration anybody would finish.
     *
     * The station's own square is the one place that is always right: it exists, it is on the page the
     * reader is looking at, and unlike a search for nearby blank space it cannot land the label on
     * somebody else's track.
     */
    @Test
    public void testAnImportedStationIsLabelledOnItsOwnSquare() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        org.json.JSONObject point = new org.json.JSONObject();
        point.put("name", "Hauptbahnhof");
        point.put("station", true);
        point.put("s88", 11);

        org.json.JSONArray points = new org.json.JSONArray();
        points.put(point);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        session.importLegacy(legacy);

        TileKey tile = new TileKey("main", 1, 1);

        assertEquals(session.getCaptionTarget(tile), tile,
            "the imported station is not labelled on its own square");

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            assertFalse(org.traincontrol.automationui.AutonomyChecks.UNLABELLED_STATION
                .equals(finding.getMessageKey()) && tile.equals(finding.getTile()),
                "the station this import labelled is still reported as not shown on the diagram");
        }
    }

    /**
     * A station nobody has labelled is an error, not a warning.
     *
     * The railway runs perfectly well unlabelled, which was the old argument for a warning - true of
     * the trains and beside the point for the person watching them.  A setup whose stations cannot be
     * found on the diagram is not one anybody can supervise.
     */
    @Test
    public void testAnUnlabelledStationIsAnError() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey tile = new TileKey("main", 1, 1);

        session.getStore().setStation(tile, true);
        session.setPointName(tile, "Hauptbahnhof");

        session.rebuild();

        boolean found = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (!org.traincontrol.automationui.AutonomyChecks.UNLABELLED_STATION
                .equals(finding.getMessageKey())) continue;

            if (!tile.equals(finding.getTile())) continue;

            found = true;

            assertEquals(finding.getSeverity(),
                org.traincontrol.automationui.AutonomyChecks.Severity.ERROR,
                "a station that cannot be found on the diagram should block the setup, not merely "
                    + "sit among the things worth checking");
        }

        assertTrue(found, "precondition: an unlabelled station is reported at all");
    }

    /**
     * Asking to show a station's name moves the caption rather than adding a second one.
     *
     * This used to refuse when the station was already shown somewhere, which left the user to find
     * and delete the old caption first - and once importing began captioning every station on its own
     * square, made the action appear to do nothing at all on a freshly imported setup.
     *
     * The count is the assertion: two captions for one station is the thing being prevented, and it is
     * exactly what a refusal-turned-into-a-placement would produce if the old one were not cleared.
     */
    @Test
    public void testShowingAStationNameMovesItRatherThanAddingASecond() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");

        // Captioned on its own square, which is where an import leaves it
        session.getStore().setCaption(station, station);

        assertEquals(session.captionsFor(station).size(), 1, "precondition: shown exactly once");

        String why = session.placeCaption(station);

        assertNull(why, "there was room beside the platform, so placing should have succeeded: " + why);

        assertEquals(session.captionsFor(station).size(), 1,
            "the station is captioned in two places at once");

        assertFalse(station.equals(session.getCaptions().keySet().iterator().next()),
            "the caption should have moved off the sensor onto the track beside it");
    }

    /**
     * A station with nowhere new to go keeps the caption it has.
     *
     * The old caption is cleared only once somewhere new has been found.  Clearing first and then
     * failing to place would answer "show this name" by removing the name that was there.
     */
    @Test
    public void testAFailedMoveLeavesTheCaptionWhereItWas() throws Exception
    {
        // A lone sensor with nothing beside it and text written on it, so every candidate square fails
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2,
            "written on");

        page.setPageId("1");
        page.checkBounds();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.getStore().setCaption(station, station);

        String why = session.placeCaption(station);

        assertNotNull(why, "precondition: there is nowhere for this caption to go");

        assertEquals(session.captionsFor(station).size(), 1,
            "a move that could not find anywhere new deleted the caption that was already there");
    }

    /**
     * Importing a bundle re-derives, so the stations it brought are Points immediately.
     *
     * The diagram draws a caption for every station the setup knows, and then asks the RUNNING
     * derivation what is standing at each one.  A station that exists in the setup and not in the
     * derivation therefore gets a label with nothing behind it, and stays blank - which is
     * indistinguishable, on screen, from the import not having worked.
     *
     * The companion to testTheDiagramSeesTheImportWithoutBeingReloaded, which pins the same property
     * for the legacy path.  Both doors need it and only one had it.
     */
    @Test
    public void testImportingABundleReDerivesImmediately() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        // A setup that already has a configuration, which is the case that was broken: an import onto
        // an empty setup happened to work because it was loaded afterwards anyway.
        session.open(Arrays.asList(page));
        session.getStore().createConfiguration("Existing", null);
        session.getStore().setActiveConfiguration("Existing");
        session.rebuild();

        TileKey station = new TileKey("main", 4, 1);

        assertFalse(session.getReducer().getPoints().get(station).isStation(),
            "precondition: this square is not a station before the import");

        // Built elsewhere, exported, and brought here
        File other = Files.createTempDirectory("tc-bundle-source").toFile();

        try
        {
            AutonomySession source = new AutonomySession(other);
            source.open(Arrays.asList(page));

            source.getStore().setStation(station, true);
            source.setPointName(station, "Hauptbahnhof");
            source.getStore().createConfiguration("Adam 1", null);

            org.json.JSONObject bundle = source.getStore().exportBundle("Adam 1");

            session.importBundle("Adam 1", new org.json.JSONObject(bundle.toString()));

            assertTrue(session.getReducer().getPoints().get(station).isStation(),
                "the imported station is not a Point in the derivation, so the caption drawn for it "
                    + "has nothing behind it and stays blank");

            assertEquals(session.getReducer().getPoints().get(station).getName(), "Hauptbahnhof",
                "the derivation does not carry the imported name");
        }
        finally
        {
            delete(other);
        }
    }

    /**
     * A link is never offered a direction, and ordinary track always is.
     *
     * The guard this pins is the only thing standing between a user and a setting that silently does
     * nothing.  A link's route is a stub - the same side twice - so "toward A" and "toward B" name the
     * same place, and the traversal would allow both whichever was chosen.  See the note on
     * TileGraph.PORTAL_ROUTE.
     */
    @Test
    public void testALinkIsNotOfferedADirection() throws Exception
    {
        LayoutDiagram page = throughStationPage();

        page.addComponent(componentType.LINK, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        session.open(Arrays.asList(page));
        session.rebuild();

        assertFalse(session.canCarryDirection(new TileKey("main", 6, 1)),
            "a link was offered the four direction answers, none of which can mean anything on it");

        assertTrue(session.canCarryDirection(new TileKey("main", 2, 1)),
            "ordinary track must still be able to carry a direction, or the rule has gone too far");
    }

    /**
     * A home authored on a split square is emitted onto exactly ONE copy.
     *
     * The running model hangs a home on a Point and refuses to let two Points claim one locomotive, so
     * emitting it on every copy meant rebuildHomeStations stripped all but the first at every load -
     * logging a "assigned twice, check your hand-edited file" warning per copy, for a file no hand had
     * edited - and the home came to rest on whichever copy parsed first: an arrival side chosen by enum
     * order, meaning nothing to whoever authored it.  The locomotive placement had already been given
     * this treatment; the home had not.
     *
     * Needs a THROUGH station: a square with one way in cannot be split, so the fault does not arise on
     * the straight line the other tests use.
     */
    @Test
    public void testAHomeOnASplitSquareIsEmittedOnce() throws Exception
    {
        LayoutDiagram page = throughStationPage();

        session.open(Arrays.asList(page));
        session.getStore().createConfiguration("Homes", null);
        session.getStore().setActiveConfiguration("Homes");

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        // "trains may turn round here" - what a berth or a stub platform gets, and what gives the
        // square a turning copy of every arrival on top of its plain ones
        session.setPointProperty(station, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE,
            Boolean.TRUE);

        session.setPointProperty(station, "home", "BR 218");

        session.rebuild();

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        int copies = 0;
        int carrying = 0;

        for (Object o : built.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            // the builder writes "block" only where a square became more than one Point, so this is
            // also the precondition: without it the test would pass for want of a split
            if (station.toString().equals(point.optString("block", null))) copies++;

            if ("BR 218".equals(point.optString("home", null))) carrying++;
        }

        assertTrue(copies > 1,
            "precondition: this square must be emitted as several copies, or there is nothing to test");

        assertEquals(carrying, 1,
            "a home emitted onto every copy of a split square is stripped back to one at load, with a "
                + "warning per copy blaming a file nobody edited");
    }

    /**
     * A line with a feedback in the MIDDLE, so that square has two ways in and can be split.
     */
    private LayoutDiagram throughStationPage() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(),
            "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + pageFile.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * A bundle from a BIGGER railway lands what it can and keeps the rest.
     *
     * The realistic import: somebody's setup, exported, brought to a layout that is not the same shape.
     * Squares the diagram does have take their settings; squares it does not have are kept in the store
     * rather than dropped, and neither refuses the import nor throws.
     *
     * Kept rather than dropped, deliberately.  A square that is not in the derivation is not the same
     * thing as a square that does not exist - a page switched off is absent from the graph too, and an
     * earlier version of this pruned against the graph and destroyed the arrival restrictions of every
     * excluded page.  So the rule is that the store remembers what it was told, and the derivation uses
     * what it can find.
     *
     * That does mean an import onto a genuinely different layout is quiet about the half that did not
     * apply.  Asserted here so the behaviour is at least written down, and so that anyone adding a
     * report of it later has to come through this test.
     */
    @Test
    public void testAnImportFromADifferentLayoutKeepsWhatItCannotPlace() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));
        session.getStore().createConfiguration("Existing", null);
        session.getStore().setActiveConfiguration("Existing");
        session.rebuild();

        TileKey here = new TileKey("main", 4, 1);

        // A square on a page this layout has never heard of
        TileKey elsewhere = new TileKey("goods yard", 30, 30);

        File other = Files.createTempDirectory("tc-bundle-bigger").toFile();

        try
        {
            AutonomySession source = new AutonomySession(other);
            source.open(Arrays.asList(page));

            source.getStore().setStation(here, true);
            source.setPointName(here, "Hauptbahnhof");

            // authored against track this layout does not have
            source.getStore().setPointName(elsewhere, "Goods Arrival");
            source.getStore().setTileLength(elsewhere, 9);

            source.getStore().createConfiguration("Adam 1", null);

            org.json.JSONObject bundle = source.getStore().exportBundle("Adam 1");

            session.importBundle("Adam 1", new org.json.JSONObject(bundle.toString()));

            // what this layout does have, applied
            assertTrue(session.getReducer().getPoints().get(here).isStation(),
                "a square the diagram has did not take its imported setting");

            assertEquals(session.getReducer().getPoints().get(here).getName(), "Hauptbahnhof");

            // what it does not have, kept rather than lost
            assertEquals(session.getStore().getPointName(elsewhere), "Goods Arrival",
                "a setting for a square this diagram lacks was dropped, so re-importing onto the "
                    + "layout it came from would not bring it back");

            assertEquals(session.getStore().getTileLength(elsewhere), 9);

            // and it is not in the derivation, because there is no such track to derive
            assertFalse(session.getReducer().getPoints().containsKey(elsewhere),
                "a square that is not on any page must not appear in the graph");
        }
        finally
        {
            delete(other);
        }
    }

    /**
     * An import reloads whatever was already running, rather than leaving it alone.
     *
     * This is the rule the screen depended on and no test could see, because it lived as an early
     * return inside a button.  An import onto an empty setup worked - it was loaded afterwards anyway
     * - while an import onto a setup with a configuration running returned immediately, and that is
     * every backup import onto a working railway.  The running layout is derived from the setup, so
     * leaving it left the diagram describing the setup as it was: a caption for every imported station
     * with no Point behind it, blank until an editor round trip reloaded.
     */
    @Test
    public void testAnImportReloadsWhateverWasAlreadyRunning() throws Exception
    {
        assertEquals(AutonomySession.configurationToLoadAfterImport("Yard", "Adam 1"), "Yard",
            "a configuration already running must be re-derived, not left describing the old setup");

        assertEquals(AutonomySession.configurationToLoadAfterImport(null, "Adam 1"), "Adam 1",
            "with nothing running, the imported configuration is the one to bring up");

        assertEquals(AutonomySession.configurationToLoadAfterImport("  ", "Adam 1"), "Adam 1",
            "a blank name is nothing running");

        assertNull(AutonomySession.configurationToLoadAfterImport(null, null),
            "nothing running and nothing imported is nothing to do");

        assertEquals(AutonomySession.configurationToLoadAfterImport(null, "  Adam 1  "), "Adam 1",
            "the imported name is trimmed, since it comes from a text box");
    }

    /**
     * The one Import action tells the shapes apart by reading them, not by their names.
     *
     * A user has one Import.  Being asked which menu item their own file belongs to is a question they
     * should never have to answer, so the file is identified by something only its own shape has.
     *
     * The two real files are pinned as fixtures deliberately.  A synthetic sample proves the rule
     * against itself; these are what the application actually wrote, and they are the reason the
     * array-versus-object distinction below is safe rather than merely plausible.
     */
    @Test
    public void testEveryImportableShapeIsRecognised() throws Exception
    {
        // The old graph, from the author's own layout
        assertEquals(formatOf("test/autonomy_formats/legacy-graph.json"),
            AutonomySession.ImportFormat.LEGACY_GRAPH,
            "a real autonomy.json is not recognised as one");

        // And from the sample layout, which is a different railway written by the same version
        assertEquals(formatOf("test/autonomy_formats/legacy-graph-sample-layout.json"),
            AutonomySession.ImportFormat.LEGACY_GRAPH,
            "a second real autonomy.json is not recognised as one");

        // A bundle, built the way exportBundle builds one rather than copied from a file, so this
        // cannot drift away from what the exporter actually writes
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));
        session.getStore().createConfiguration("Adam 1", null);

        // Given the globals every real configuration carries.  A configuration created and never
        // touched holds nothing but its own name, and a file that says only "name" is not something
        // this should claim to recognise - see the note on detectImportFormat.
        session.getStore().getConfiguration("Adam 1").put("globals", new org.json.JSONObject());

        org.json.JSONObject bundle = session.getStore().exportBundle("Adam 1");

        assertEquals(AutonomySession.detectImportFormat(bundle),
            AutonomySession.ImportFormat.BUNDLE, "a bundle from exportBundle is not recognised");

        // A bare configuration, which is what exporting wrote before bundles existed
        assertEquals(AutonomySession.detectImportFormat(bundle.getJSONObject("configuration")),
            AutonomySession.ImportFormat.CONFIGURATION,
            "a configuration on its own is not recognised");
    }

    /**
     * Something that is not an autonomy file at all is refused rather than half-imported.
     *
     * A routes file is the realistic mistake - it sits in the same backup folder, under a similar
     * name - and importing one as a configuration would write a configuration full of nothing and
     * report success.
     */
    @Test
    public void testAFileThatIsNotAnAutonomySetupIsRefused() throws Exception
    {
        org.json.JSONObject routes = new org.json.JSONObject();
        routes.put("routes", new org.json.JSONArray());

        assertEquals(AutonomySession.detectImportFormat(routes),
            AutonomySession.ImportFormat.UNKNOWN, "a routes file is not an autonomy setup");

        assertEquals(AutonomySession.detectImportFormat(new org.json.JSONObject()),
            AutonomySession.ImportFormat.UNKNOWN, "an empty object says nothing about what it is");

        // A configuration that has never been used carries only its name, and a name is not evidence:
        // half the JSON in the world has one.  Refusing it costs a user nothing - there is nothing in
        // it to import - and claiming it would mean claiming any file with a name field.
        org.json.JSONObject nameOnly = new org.json.JSONObject();
        nameOnly.put("name", "Adam 1");

        assertEquals(AutonomySession.detectImportFormat(nameOnly),
            AutonomySession.ImportFormat.UNKNOWN, "a name alone is not enough to go on");

        assertEquals(AutonomySession.detectImportFormat(null),
            AutonomySession.ImportFormat.UNKNOWN, "and nothing at all is not a setup either");
    }

    /**
     * The two formats that share the key "points" disagree about its type, which is what keeps them
     * apart.
     *
     * The old graph is a LIST of Points; a configuration is keyed BY SQUARE.  Asserting this directly
     * says why the detection is safe rather than lucky - a file merely containing the word cannot be
     * mistaken for either.
     */
    @Test
    public void testTheTwoPointsShapesCannotBeConfused() throws Exception
    {
        org.json.JSONObject asList = new org.json.JSONObject();
        asList.put("points", new org.json.JSONArray());

        org.json.JSONObject asMap = new org.json.JSONObject();
        asMap.put("points", new org.json.JSONObject());

        assertEquals(AutonomySession.detectImportFormat(asList),
            AutonomySession.ImportFormat.LEGACY_GRAPH, "a list of points is the old graph");

        assertEquals(AutonomySession.detectImportFormat(asMap),
            AutonomySession.ImportFormat.CONFIGURATION, "points keyed by square is a configuration");
    }

    /**
     * Reads a pinned fixture and says what it is.
     */
    private AutonomySession.ImportFormat formatOf(String path) throws Exception
    {
        String text = new String(Files.readAllBytes(new File(path).toPath()),
            StandardCharsets.UTF_8);

        return AutonomySession.detectImportFormat(new org.json.JSONObject(text));
    }

    /**
     * Captioning a station somewhere new takes the caption off wherever it was.
     *
     * There are three ways to caption a station - place it automatically, choose the square in the
     * autonomy editor, or drag the square it sits on in the track diagram editor - and only the first
     * knew to remove the old one.  So choosing a new square left the station named twice on the
     * diagram, with nothing saying which was current.  The rule belongs to setCaption, which all three
     * go through.
     */
    @Test
    public void testAStationIsOnlyEverCaptionedInOnePlace() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");

        session.setCaption(new TileKey("main", 1, 2), station);

        assertEquals(session.captionsFor(station).size(), 1, "precondition: shown exactly once");

        // and now somewhere else entirely
        session.setCaption(new TileKey("main", 3, 2), station);

        assertEquals(session.captionsFor(station).size(), 1,
            "the station is captioned in two places at once");

        assertEquals(session.getCaptionTarget(new TileKey("main", 3, 2)), station,
            "the caption is not where it was just put");

        assertNull(session.getCaptionTarget(new TileKey("main", 1, 2)),
            "the caption was left behind on the square it came from");
    }

    /**
     * Dragging a square in the track diagram editor carries its caption with it.
     *
     * A caption belongs to the setup, keyed by the square it sits on, so moving the tile underneath
     * one used to leave it behind pointing at track that is no longer there.  On a layout being
     * rearranged that is every label, replaced by hand.
     */
    @Test
    public void testACaptionFollowsTheSquareItSitsOn() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey was = new TileKey("main", 1, 2);
        TileKey now = new TileKey("main", 3, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(was, station);

        assertTrue(session.moveCaption(was, now), "the move reported doing nothing");

        assertEquals(session.getCaptionTarget(now), station, "the caption did not arrive");

        assertNull(session.getCaptionTarget(was), "the caption did not leave");

        assertEquals(session.captionsFor(station).size(), 1, "the station is now captioned twice");
    }

    /**
     * Moving a square that has no caption on it does nothing, and says so.
     *
     * Most dragged tiles are plain track.  The caller asks on every move, so the common answer has to
     * be cheap and has to be distinguishable from having moved something.
     */
    @Test
    public void testMovingASquareWithNoCaptionDoesNothing() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        assertFalse(session.moveCaption(new TileKey("main", 2, 1), new TileKey("main", 3, 1)),
            "a square with nothing written on it reported moving a caption");

        assertTrue(session.getCaptions().isEmpty(), "a caption was invented by moving a bare tile");

        // and a move onto itself is not a move
        TileKey station = new TileKey("main", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(station, station);

        assertFalse(session.moveCaption(station, station), "a square moved onto itself is not a move");

        assertEquals(session.getCaptionTarget(station), station,
            "moving a caption onto its own square deleted it");
    }

    /**
     * Two copies of one square are the same place; two squares sharing a sensor are not.
     *
     * This is the distinction the fix rests on, and getting it the other way round is the mistake that
     * was nearly shipped.  A train standing at a station was offered a path to that same station -
     * the copy facing the other way is a different Point - and the obvious fix, comparing sensors, is
     * WRONG: a station and its approach guard legitimately share one and are genuinely two places, so
     * that filter would have refused real journeys.
     *
     * What makes two Points one place is the square they were built from, which only the setup knows.
     */
    @Test
    public void testCopiesOfOneSquareAreTheSamePlaceAndNeighboursAreNot() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey first = new TileKey("main", 1, 1);
        TileKey second = new TileKey("main", 4, 1);

        session.getStore().setStation(first, true);
        session.setPointName(first, "BottomMainB");

        session.getStore().setStation(second, true);
        session.setPointName(second, "BottomMainC");

        session.rebuild();

        java.util.List<String> copies = session.pointNamesFor(session.pointNameForTile(first));

        assertFalse(copies.isEmpty(), "precondition: the station has at least one Point");

        // Every copy of one square is that square
        for (String one : copies)
        {
            for (String other : copies)
            {
                assertTrue(session.sameSquare(one, other),
                    one + " and " + other + " are copies of one square and should count as one place");
            }
        }

        // And a different square is not, however its Points are named
        String elsewhere = session.pointNameForTile(second);

        assertNotNull(elsewhere, "precondition: the second station is a Point");

        for (String one : copies)
        {
            assertFalse(session.sameSquare(one, elsewhere),
                "two different squares were treated as one place, which would refuse real journeys");
        }

        assertFalse(session.sameSquare(null, copies.get(0)), "nothing is not somewhere");
    }

    /**
     * Deleting the square a caption sits on takes the caption with it.
     *
     * A caption may legitimately sit on blank space - that is the most readable place for one - so an
     * EMPTY square keeps its caption.  A square somebody has just deleted is a different thing: they
     * said to remove it, and the label that was on it stayed, naming nothing, with no way to get rid
     * of it.  Putting any tile back on that square then made the orphan look like the new tile's own
     * label, because a caption is drawn wherever its square is.
     */
    @Test
    public void testDeletingTheSquareUnderACaptionTakesItAway() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(caption, station);

        assertTrue(session.forgetCaptionsAt(caption), "nothing was forgotten");

        assertNull(session.getCaptionTarget(caption), "the caption outlived the square it sat on");
    }

    /**
     * And deleting the station takes every caption that names it, wherever they are.
     *
     * The other end of the same relationship: text pointing at track that no longer exists is the
     * orphan this design removed, and it can be sitting anywhere on the page.
     */
    @Test
    public void testDeletingAStationTakesTheCaptionsThatNameIt() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(caption, station);

        assertTrue(session.forgetCaptionsAt(station), "nothing was forgotten");

        assertNull(session.getCaptionTarget(caption),
            "a caption naming a station that has been deleted is a label about nothing");

        assertTrue(session.captionsFor(station).isEmpty(), "the station still claims a caption");
    }

    /**
     * A square with nothing written about it is not changed by being deleted.
     *
     * Most deleted tiles are plain track.  The editor asks on every delete, so the common answer has
     * to be cheap and has to be distinguishable from having removed something.
     */
    @Test
    public void testDeletingAnOrdinarySquareForgetsNothing() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(caption, station);

        assertFalse(session.forgetCaptionsAt(new TileKey("main", 3, 1)),
            "a square with nothing written about it reported forgetting something");

        assertEquals(session.getCaptionTarget(caption), station,
            "deleting an unrelated square disturbed a caption");
    }

    /**
     * A locomotive is recorded standing in one place, never two.
     *
     * The running layout enforces that when a train is moved - it leaves where it was - but the
     * CONFIGURATION was never told, so a locomotive placed by hand on one square kept its old
     * placement on another.  Nothing looked wrong until the next build, which emitted the same
     * locomotive at two Points; fromJSON answers that by invalidating the whole layout, and from then
     * on every path was refused as "configuration is invalid" - from a placement made minutes before.
     *
     * Found in a real exported graph, with 065 001-0 DB standing at both BottomMainA and BottomMainC.
     */
    @Test
    public void testALocomotiveIsRecordedInOnePlaceOnly() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey was = new TileKey("main", 1, 1);
        TileKey now = new TileKey("main", 4, 1);

        session.placeLocomotive(was, "065 001-0 DB");

        // Its settings, which have to travel with it
        ((org.json.JSONObject) session.getPointProperty(was, AutonomyBuilder.LOCOMOTIVE))
            .put("speed", 42);

        session.placeLocomotive(now, "065 001-0 DB");

        assertNull(session.getPointProperty(was, AutonomyBuilder.LOCOMOTIVE),
            "the locomotive is still recorded where it was, so the next build emits it twice and "
                + "invalidates the whole layout");

        org.json.JSONObject standing =
            (org.json.JSONObject) session.getPointProperty(now, AutonomyBuilder.LOCOMOTIVE);

        assertNotNull(standing, "the locomotive is not recorded where it was put");

        assertEquals(standing.getInt("speed"), 42,
            "the placement was rebuilt rather than moved, so its settings were lost");
    }

    /**
     * Taking a locomotive off forgets it, and forgets which way it was pointing.
     *
     * The configuration is what the next build reads, so a placement left behind puts the train back.
     * The facing goes with it because it belonged to that train, not to the square - otherwise the
     * next locomotive placed there inherits the last one's direction without being asked.
     */
    @Test
    public void testTakingALocomotiveOffForgetsItAndItsFacing() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey tile = new TileKey("main", 1, 1);

        session.placeLocomotive(tile, "SM31-108");
        session.setFacing(tile, org.traincontrol.automationui.TilePorts.Side.E);

        session.placeLocomotive(tile, null);

        assertNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE),
            "the next build would put the train straight back");

        assertNull(session.getFacing(tile), "the square kept a direction belonging to a train that has gone");
    }

    /**
     * A station takes trains from anywhere until somebody says otherwise.
     *
     * The default has to be free rather than shut, and it has to cost nothing to store: a setup nobody
     * has restricted should carry no restriction at all, so that track added to the diagram later
     * arrives open instead of arriving barred by a setting nobody ever opened.
     */
    @Test
    public void testAStationTakesTrainsFromAnywhereByDefault() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);

        assertTrue(session.getBarredArrivals(station).isEmpty(),
            "a station nobody has restricted is carrying a restriction");

        assertTrue(session.arrivalMarks(station, false).isEmpty(),
            "an unrestricted station draws marks on the running diagram, which is the clutter this "
            + "setting exists to avoid");
    }

    /**
     * Barring a side stops trains stopping there, and nothing else.
     *
     * The restriction lands on the copy for that arrival side: it stops being a station, so autonomy
     * cannot send a train to it.  The copy itself stays, and so does every edge through it, because
     * running THROUGH a square is a different question - the one the direction arrows answer.
     */
    @Test
    public void testBarringAnArrivalSideOnlyStopsTrainsStoppingThere() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Bahnhof");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
            session.arrivalSides(station);

        assertTrue(ways.size() > 1, "precondition: this fixture has a square with two ways in");

        org.traincontrol.automationui.TilePorts.Side shut = ways.get(0);

        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<>(Arrays.asList(shut)));

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());
        org.json.JSONArray points = built.getJSONArray("points");

        int stations = 0;
        int copies = 0;

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            if (!point.getString("name").startsWith("Bahnhof")) continue;

            copies++;

            if (point.getBoolean("station")) stations++;
        }

        assertTrue(copies > 1, "the square has to still be emitted as every copy it was before");

        assertTrue(stations > 0, "barring one way in shut the whole station");

        assertTrue(stations < copies,
            "the barred side is still a place autonomy can send a train to");
    }

    /**
     * Barring a side of a station where trains turn round still loads.
     *
     * The two flags are emitted from different places.  "station" is per copy, because that is where an
     * arrival restriction lands; "terminus" was still read off the SQUARE - so the reverse copy of a
     * barred side came out as a terminus that is not a destination.
     *
     * Point.setTerminus refuses exactly that pair, and parseAuto answers a refusal by invalidating the
     * WHOLE layout - naming a Point copy nothing on the diagram carries.  So restricting a terminus
     * platform, which is the most natural use this setting has, would have made the entire setup
     * unloadable and said nothing a user could act on.
     */
    @Test
    public void testBarringASideOfATurnAroundStationStillLoads() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        // A configuration has to exist for the per-point flags to be stored in
        session.getStore().createConfiguration("Terminus", null);
        session.getStore().setActiveConfiguration("Terminus");
        session.rebuild();

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Kopfbahnhof");
        session.setPointFlag(station, AutonomyBuilder.CAN_REVERSE, true);

        assertTrue(session.isTurnAround(station), "precondition: trains may turn round here");

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(
            Arrays.asList(session.arrivalSides(station).get(0))));

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());
        org.json.JSONArray points = built.getJSONArray("points");

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            assertFalse(point.optBoolean("terminus", false) && !point.optBoolean("station", false),
                "a terminus that is not a destination is refused by Point.setTerminus, which "
                + "invalidates the whole configuration: " + point);
        }
    }

    /**
     * Lifting a restriction leaves nothing behind.
     */
    @Test
    public void testLiftingAnArrivalRestrictionStoresNothing() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        org.traincontrol.automationui.TilePorts.Side shut = session.arrivalSides(station).get(0);

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(Arrays.asList(shut)));
        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<org.traincontrol.automationui.TilePorts.Side>());

        assertFalse(session.barredArrivals().containsKey(station),
            "the square kept an empty restriction, which is a setting that says nothing");
    }

    /**
     * A restriction naming a side the square no longer has is ignored, and then forgotten.
     *
     * The diagram moves under the setup: a tile replaced, an approach re-plumbed, and the square now
     * arrives from somewhere else.  The stale side is already dead in the build - there is no copy for
     * it - but it was still COUNTED, and the count is what decides whether the menu will let another
     * side be shut.  A station could end up with every box ticked, every box disabled, and nothing on
     * screen to say why.
     */
    @Test
    public void testARestrictionOnASideTheSquareNoLongerHasIsIgnored() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> both =
            session.arrivalSides(station);

        assertEquals(both.size(), 2, "precondition: two ways in");

        // The side facing the track that is about to be taken up, so the restriction is the one that
        // goes stale.  Barring the other would leave a live restriction, which is a different test.
        org.traincontrol.automationui.TilePorts.Side doomed =
            org.traincontrol.automationui.TileGraph.gridSideTowards(station, new TileKey("main", 2, 1));

        assertTrue(both.contains(doomed), "precondition: trains arrive from that side today");

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(Arrays.asList(doomed)));

        session.save();

        // The track on one side is taken up, so the station is now reached from one end only
        LayoutDiagram shortened = pageWithATwoEndedStation();

        shortened.addComponent(null, 2, 1);
        shortened.addComponent(null, 1, 1);

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(shortened));

        assertEquals(reopened.arrivalSides(station).size(), 1,
            "precondition: the square lost a way in");

        assertTrue(reopened.getBarredArrivals(station).isEmpty(),
            "a side that no longer exists is still being counted against the ways in");

        reopened.save();

        AutonomySession again = new AutonomySession(layout);
        again.open(Arrays.asList(shortened));

        assertFalse(again.barredArrivals().containsKey(station),
            "the dead side is still in the file, ready to come back the day the diagram does");
    }

    /**
     * Excluding a page does not destroy the arrival restrictions on it.
     *
     * The graph leaves excluded pages out by construction, so a square on one has no arrival sides at
     * all - and a save that pruned restrictions against the live sides read that as "every way in has
     * gone" and deleted the setting outright.  Re-including the page gave nothing back, and nothing
     * reported the loss, because it happened before the reconciliation that would have reported it.
     *
     * The same rule the whole reconciliation is built around: excluding a page must be reversible.
     */
    @Test
    public void testExcludingAPageKeepsItsArrivalRestrictions() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        java.util.Set<org.traincontrol.automationui.TilePorts.Side> barred =
            new java.util.LinkedHashSet<>(Arrays.asList(session.arrivalSides(station).get(0)));

        session.setBarredArrivals(station, barred);

        session.getStore().setPageExcluded("main", true);
        session.rebuild();

        assertTrue(session.arrivalSides(station).isEmpty(),
            "precondition: an excluded page is not in the graph, so the square has no sides");

        session.save();

        session.getStore().setPageExcluded("main", false);
        session.rebuild();

        assertEquals(session.getBarredArrivals(station), barred,
            "the restriction was destroyed by excluding the page, and re-including gave nothing back");
    }

    /**
     * A path that ends where it started is not offered, even from a configuration this index predates.
     *
     * The dedupe used to short-circuit on the names being equal before ever consulting the index.
     * Moved into the index it lost that, and the index answers "different place" about two Points it
     * has never heard of - which is exactly a configuration built before the last diagram edit.  The
     * train is then offered a journey to the platform it is standing on.
     */
    @Test
    public void testAPathBackToWhereItStartedIsDroppedEvenForUnknownPoints() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        assertNull(index.squareOf("Ghost"),
            "precondition: a Point from a configuration this index does not describe");

        assertTrue(index.sameSquare("Ghost", "Ghost"),
            "a Point is in the same place as itself, whether or not this index has heard of it");
    }

    /**
     * Demoting a station forgets how trains were allowed to arrive at it.
     *
     * Inert while it is not a station, so leaving it costs nothing today - and everything the day
     * somebody makes the square a station again and finds it refusing trains for a reason recorded
     * months earlier.  Symmetrical with the caption rule.
     */
    @Test
    public void testDemotingAStationForgetsItsArrivalRestriction() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<>(Arrays.asList(session.arrivalSides(station).get(0))));

        session.setStation(station, false);
        session.setStation(station, true);

        assertTrue(session.getBarredArrivals(station).isEmpty(),
            "a restriction nobody remembers setting came back with the station");
    }

    /**
     * The restriction survives being written out and read back.
     */
    @Test
    public void testArrivalRestrictionsSurviveASaveAndLoad() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        org.traincontrol.automationui.TilePorts.Side shut = session.arrivalSides(station).get(0);

        session.setBarredArrivals(station, new java.util.LinkedHashSet<>(Arrays.asList(shut)));
        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(pageWithATwoEndedStation()));

        assertEquals(reopened.getBarredArrivals(station),
            new java.util.LinkedHashSet<>(Arrays.asList(shut)),
            "the restriction did not survive the file");
    }

    /**
     * A station shut from every direction is reported - as INFORMATION.
     *
     * The editor will not let anybody tick the last way in, so this is for the ways round it - a
     * diagram edited after the fact, or a file written by hand.  It has to be said out loud, because
     * the consequence is quiet: the station stops being somewhere autonomy will send a train, with
     * nothing on screen to say why.
     *
     * **This test used to require an ERROR, and Adam overruled that on 2026-08-23** (MT-078): "We
     * should let the user know a train can't come in in any way (warning). If manual only, it's info."
     *
     * A bar is advisory. Autonomy will not route into a barred side; a person driving by hand may - so
     * the platform is still reachable, and an ERROR blocks the whole setup from starting over a station
     * the operator can still use. The case that IS a warning - nothing can arrive by any means - is a
     * square no track reaches, which is POINT_ISOLATED and is a warning already.
     *
     * The old assertion said "a station autonomy can never use is not a suggestion". That was a fair
     * reading and it was the wrong half of the question: it is not a suggestion, and it is not a
     * reason to stop the railway either.
     */
    @Test
    public void testAStationWithEveryWayInBarredIsReported() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Bahnhof");

        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<>(session.arrivalSides(station)));

        assertTrue(session.shutStations().containsKey(station),
            "a station no train can reach is not being noticed");

        boolean reported = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.NO_ARRIVALS_LEFT
                .equals(finding.getMessageKey()))
            {
                reported = true;

                assertEquals(finding.getSeverity(),
                    org.traincontrol.automationui.AutonomyChecks.Severity.INFO,
                    "a station with every way in barred is still reachable by hand, so reporting it "
                    + "as an ERROR stops the whole setup running over something the operator can "
                    + "still use (MT-078)");
            }
        }

        assertTrue(reported,
            "nothing told the user autonomy will no longer send a train to their station");
    }

    /**
     * A page's captions can be taken away and put back exactly.
     *
     * This is what the track diagram editor's undo holds on to.  A caption belongs to the setup rather
     * than to the tile - which is what stops a rename rewriting every page - so it cannot ride in the
     * editor's snapshot of components beside it, and without a snapshot of its own Ctrl+Z brought a
     * deleted platform back with no name on it.
     */
    @Test
    public void testAPagesCaptionsRoundTripThroughASnapshot() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);
        TileKey plaque = new TileKey("main", 3, 2);

        session.setStation(station, true);
        session.setCaption(plaque, station);

        java.util.Map<TileKey, TileKey> before = session.captionsOnPage("main");

        assertEquals(before.get(plaque), station, "precondition: the plaque is up");

        // what deleting the captioned square does
        session.forgetCaptionsAt(station);

        assertNull(session.getCaptionTarget(plaque), "precondition: and then it is not");

        session.restoreCaptionsOnPage("main", before);

        assertEquals(session.getCaptionTarget(plaque), station,
            "undo brought the platform back without its name");
    }

    /**
     * Restoring a snapshot removes captions added since it was taken.
     *
     * Putting the old ones back is only half of it: a caption placed after the snapshot has to go, or
     * undo leaves the page with both, which is a state the user was never in.
     */
    @Test
    public void testRestoringASnapshotRemovesWhatWasAddedAfterIt() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);
        TileKey first = new TileKey("main", 3, 2);
        TileKey later = new TileKey("main", 3, 0);

        session.setStation(station, true);
        session.setCaption(first, station);

        java.util.Map<TileKey, TileKey> before = session.captionsOnPage("main");

        session.setCaption(later, station);

        assertNull(session.getCaptionTarget(first),
            "precondition: one station, one caption - the second move took the first down");

        session.restoreCaptionsOnPage("main", before);

        assertEquals(session.getCaptionTarget(first), station);

        assertNull(session.getCaptionTarget(later),
            "the caption added after the snapshot survived the undo");
    }

    /**
     * A snapshot of one page leaves the other pages alone.
     *
     * The editor works on one page, so restoring every caption in the setup would undo work done
     * somewhere it was never looking.
     */
    @Test
    public void testACaptionSnapshotIsPerPage() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setCaption(new TileKey("main", 3, 2), station);

        assertTrue(session.captionsOnPage("elsewhere").isEmpty(),
            "a page with no captions answered with somebody else's");

        assertEquals(session.captionsOnPage("main").size(), 1);
    }

    /**
     * A station's protecting signal survives a save, and goes when the station does.
     *
     * Kept with the captions and the arrival restrictions rather than beside the running state: it is a
     * fact about the railway, not about today's traffic.
     */
    @Test
    public void testAProtectingSignalIsKeptAndForgottenWithTheStation() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 1);

        session.setStation(station, true);
        session.setProtectingSignal(station, signal);

        assertEquals(session.getProtectingSignal(station), signal);

        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(pageOnDisk()));

        assertEquals(reopened.getProtectingSignal(station), signal,
            "the pairing did not survive the file");

        // and a square that stops being a station is not somewhere trains are held out of
        reopened.setStation(station, false);

        assertNull(reopened.getProtectingSignal(station),
            "a plain point kept a signal protecting it");
    }

    /**
     * A square that stops being a Point keeps its locomotive.
     *
     * A sensor reduces to a Point only where track connects it to something, so nudging a station one
     * square - far enough that it no longer joins the run either side of it - leaves a square that is
     * perfectly present on the diagram and is not a Point.  The capture judged its prune on Points and
     * deleted everything about that square: the locomotive standing there, its facing, its markings.
     *
     * Adam found it by moving a tile and watching the train disappear, and asked for the opposite:
     * keep the placement, let the build refuse it, and have it come back when the track is joined up
     * again.  A disconnected station is a mistake somebody is in the middle of making, not an
     * instruction to forget the train.
     *
     * A square whose TILE is gone is a different thing and still keeps nothing - see the point named
     * "a point whose track was deleted" in the capture test above.
     */
    @Test
    public void testASquareThatStopsBeingAPointKeepsItsLocomotive() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));
        session.initialize("Default");

        // 1,1 and 4,1 are the sensors, and reduce to Points.  2,1 is a straight: a square that is
        // certainly ON the page and just as certainly not a Point, which is the state a sensor lands in
        // when somebody moves it out of the run it was part of.
        TileKey adrift = new TileKey("main", 2, 1);

        session.setStation(new TileKey("main", 1, 1), true);

        // Put a train on the square that is not a Point, the way a capture would after somebody moved
        // the tile out of the run
        org.json.JSONObject running = new org.json.JSONObject();

        running.put("minDelay", 1);
        running.put("maxDelay", 2);
        running.put("edges", new org.json.JSONArray());

        org.json.JSONArray points = new org.json.JSONArray();

        running.put("points", points);

        session.captureFromLayout(running.toString());

        org.json.JSONObject config = session.getStore().getConfiguration("Default");

        // Written straight into the configuration, which is what a placement on a square that has no
        // Point looks like: nothing in the running layout can speak for it
        org.json.JSONObject standing = new org.json.JSONObject();

        standing.put("loc", new org.json.JSONObject().put("name", "BR 218"));

        config.getJSONObject("points").put(adrift.toString(), standing);

        // Autonomy runs, and reports what it found
        session.captureFromLayout(running.toString());

        org.json.JSONObject after = session.getStore()
            .getConfiguration("Default").getJSONObject("points");

        assertTrue(after.has(adrift.toString()),
            "the square is still on the diagram and its locomotive was deleted anyway.  Moving a tile "
            + "out of a run is a mistake somebody is in the middle of making, not an instruction to "
            + "forget the train standing on it");

        assertEquals(after.getJSONObject(adrift.toString())
            .getJSONObject("loc").getString("name"), "BR 218");
    }

    /**
     * A station may be guarded by more than one signal, and every one of them survives the file.
     *
     * A platform reachable from two directions needs a signal on each approach, and the setup held one
     * per station until 3.0.0 - so this is as much about the file as about the pairing: the second
     * signal has to come back, and the first has to still be first.
     */
    @Test
    public void testAStationKeepsEverySignalGuardingIt() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey north = new TileKey("main", 2, 1);
        TileKey south = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setProtectingSignals(station, Arrays.asList(north, south));

        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(pageOnDisk()));

        assertEquals(reopened.getProtectingSignals(station), Arrays.asList(north, south),
            "the second signal did not survive the file");

        // and the singular call, which everything written before this feature uses, still answers
        assertEquals(reopened.getProtectingSignal(station), north);
    }

    /**
     * A setup written before 3.0.0 holds one signal as a bare string, and still reads.
     *
     * Nothing migrates it.  The string is read as a list of one, and the file gains an array only when
     * somebody pairs a second signal and saves - so a setup opened by this version and never edited is
     * still openable by the last one.
     */
    @Test
    public void testASetupFromBeforeThisFeatureStillReads() throws Exception
    {
        File folder = new File(layout, "config/autonomy");

        assertTrue(folder.mkdirs() || folder.isDirectory(), "could not create " + folder);

        // Keyed by page ID rather than by page name, which is how the file has always stored a square
        Files.write(new File(folder, "setup.json").toPath(),
            ("{\"stations\": [\"1:1,1\"], \"stationSignals\": {\"1:1,1\": \"1:2,1\"}}")
                .getBytes(StandardCharsets.UTF_8));

        session.open(Arrays.asList(pageOnDisk()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 1);

        assertEquals(session.getProtectingSignals(station), Arrays.asList(signal),
            "a pairing written as a bare string was not read at all, which would silently unprotect "
            + "every platform on an existing railway");
    }

    /**
     * One signal is still written as a bare string.
     *
     * The compatibility half of the change: a station with a single signal - most of them - is written
     * exactly as it was, so an older TrainControl reading the same layout finds what it expects.
     */
    @Test
    public void testOneSignalIsStillWrittenAsAString() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);
        session.setProtectingSignal(station, new TileKey("main", 2, 1));

        session.save();

        org.json.JSONObject written = new org.json.JSONObject(new String(
            Files.readAllBytes(new File(layout, "config/autonomy/setup.json").toPath()),
            StandardCharsets.UTF_8));

        Object one = written.getJSONObject("stationSignals").get("1:1,1");

        assertTrue(one instanceof String,
            "one signal was written as " + one.getClass().getSimpleName()
            + ", which the version before this one cannot read");

        // and two are written as an array
        session.setProtectingSignals(station,
            Arrays.asList(new TileKey("main", 2, 1), new TileKey("main", 3, 1)));

        session.save();

        org.json.JSONObject again = new org.json.JSONObject(new String(
            Files.readAllBytes(new File(layout, "config/autonomy/setup.json").toPath()),
            StandardCharsets.UTF_8));

        assertTrue(again.getJSONObject("stationSignals").get("1:1,1") instanceof org.json.JSONArray,
            "two signals were not written as a list");
    }

    /**
     * Every signal guarding a station reaches the built configuration.
     *
     * The pairing is between squares and the running layout commands accessories by name, so this is
     * the join that would quietly drop the second signal: a platform that looks guarded on both
     * approaches in the editor and is guarded on one of them on the railway.
     */
    @Test
    public void testEverySignalReachesTheBuiltConfiguration() throws Exception
    {
        session.open(Arrays.asList(pageWithTwoSignals()));

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);
        session.setProtectingSignals(station,
            Arrays.asList(new TileKey("main", 2, 1), new TileKey("main", 3, 1)));

        assertEquals(session.protectingSignalNames().get(station).size(), 2,
            "one of the two signals was lost on the way to the accessory names");

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        org.json.JSONArray points = built.getJSONArray("points");

        boolean seen = false;

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            if (!point.has("protectingSignal")) continue;

            seen = true;

            assertTrue(point.get("protectingSignal") instanceof org.json.JSONArray,
                "the built configuration carries one signal where two were paired, so the platform "
                + "runs unprotected on one of its approaches");

            assertEquals(point.getJSONArray("protectingSignal").length(), 2);
        }

        assertTrue(seen, "nothing in the built configuration mentions a protecting signal at all");
    }

    /**
     * A run of track with a station at one end and two signals beside it.
     */
    private LayoutDiagram pageWithTwoSignals() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(),
            "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + pageFile.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SIGNAL, 2, 1, 0, 0, 21, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SIGNAL, 3, 1, 0, 0, 22, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        // Wired as parsing a real layout does.  Without an accessory a signal has no address to
        // command, and the pairing is dropped on the way to the built configuration rather than emitted
        // as something the running layout would fail to find.
        wire(page, 2, 1, 21);
        wire(page, 3, 1, 22);

        page.setPageId("1");

        return page;
    }

    private void wire(LayoutDiagram page, int x, int y, int address)
    {
        page.getComponent(x, y).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, address, org.traincontrol.base.Accessory.accessoryType.SIGNAL,
            accessoryDecoderType.MM2, "Signal " + address, false, 0));
    }

    /**
     * Demoting a station takes its name plaque with it.
     *
     * The two used to be independent, so a demoted square kept a caption pointing at it - and a caption
     * is not inert: it is a registered label that fills in the moment anything stands on the square.  A
     * reversing point that was once a station therefore announced itself as one the first time a train
     * touched it, on a square drawn as a plain point.  The diagram contradicted itself and neither half
     * was wrong on its own.
     */
    @Test
    public void testDemotingAStationTakesItsCaptionWithIt() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        session.setStation(station, true);
        session.setCaption(caption, station);

        assertEquals(session.getCaptionTarget(caption), station, "precondition: the plaque is up");

        session.setStation(station, false);

        assertNull(session.getCaptionTarget(caption),
            "the name plaque outlived the station it names");

        assertTrue(session.captionsFor(station).isEmpty(),
            "and the station still believes it is captioned somewhere");
    }

    /**
     * A setup written before that rule is cleaned up when it is opened.
     *
     * The rule stops new ones appearing; it cannot touch the ones already on disk, and the setup that
     * showed this fault has one.  Nothing here is a user's to fix - the plaque comes back the moment
     * the square is made a station again - so it is cleared silently.
     */
    @Test
    public void testOpeningForgetsPlaquesForSquaresThatAreNoLongerStations() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        session.setStation(station, true);
        session.setCaption(caption, station);

        // behind the session's back, exactly as a setup written by an older build would look
        session.getStore().setStation(station, false);
        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(pageOnDisk()));

        assertNull(reopened.getCaptionTarget(caption),
            "the stale plaque survived being opened and will light up again");
    }

    /**
     * The translation between squares and Points is derived once and agrees with itself.
     *
     * It used to be worked out wherever it was needed, by building a fresh AutonomyBuilder - and the
     * copies were configured differently, so one of them split a square and another did not.  Both
     * answered confidently and they were not answers to the same question.
     */
    @Test
    public void testTheIndexRoundTripsSquaresAndPoints() throws Exception
    {
        // A square that actually SPLITS.  Run on a station at the end of a line - one way in, one
        // Point - the closing check below reduced to sameSquare(x, x), and the whole test would have
        // passed against an index that could not split at all.
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Bahnhof");

        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        assertEquals(index.nameOf(station), "Bahnhof");

        assertTrue(index.pointNamesAt(station).size() > 1,
            "the point of this class is one square being several Points, so the fixture has to be one");

        for (String name : index.pointNamesAt(station))
        {
            assertEquals(index.squareOf(name), station,
                "a copy that does not lead back to its own square is what broke every caption");

            assertEquals(index.baseNameOf(name), "Bahnhof",
                "every copy of a station is that station when a person reads it");
        }

        assertTrue(index.sameSquare(index.pointNamesAt(station).get(0),
            index.pointNamesAt(station).get(index.pointNamesAt(station).size() - 1)),
            "copies of one platform have to compare as one place");
    }

    /**
     * Renaming a square is visible to the index immediately.
     *
     * The index is cached, and a cache that outlives the thing it describes is the failure this class
     * has now had three times: the labels look up names the running graph has never heard of, and that
     * station quietly stops filling in.
     */
    @Test
    public void testTheIndexIsDroppedWhenTheSetupChanges() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);
        session.setPointName(station, "Bahnhof");

        assertEquals(session.getStationIndex().nameOf(station), "Bahnhof");

        session.setPointName(station, "Hauptbahnhof");

        assertEquals(session.getStationIndex().nameOf(station), "Hauptbahnhof",
            "the index answered with a name the setup no longer uses");
    }

    /**
     * A square that stops being a station keeps whatever train was standing on it.
     *
     * The designation and the placement are separate records and demotion only touches the first, so
     * this state is reachable by an ordinary gesture - switch a station to pass-through - as well as by
     * importing a setup that placed a train where this build draws no station.  It is why the editor
     * menu offers "remove" against the LOCOMOTIVE rather than against the designation: gated on being a
     * station, the only way to take this train off was to make the square a station again first.
     */
    @Test
    public void testDemotingAStationLeavesItsLocomotiveToBeRemoved() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Demoted", null);
        session.getStore().setActiveConfiguration("Demoted");

        TileKey tile = new TileKey("main", 1, 1);

        session.setStation(tile, true);
        session.placeLocomotive(tile, "SM31-108");

        session.setStation(tile, false);

        assertFalse(session.getStore().isStation(tile), "the square was demoted");

        assertNotNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE),
            "demotion dropped the placement, so there would be nothing left to offer to remove");

        // And taking it off works here exactly as it does at a station - nothing about clearing a
        // placement ever needed the square to be one.
        session.placeLocomotive(tile, null);

        assertNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE),
            "a train could be left on a square with no way to take it off");
    }

    /**
     * A locomotive recorded in two places is reported before anything tries to run.
     *
     * The consequence is out of all proportion to the cause: fromJSON refuses the WHOLE layout for a
     * locomotive in two places, and every path afterwards is answered with "configuration is invalid
     * and must be reloaded" - which names neither the locomotive nor the square, and points at nothing
     * the reader did.  It happened on a real setup and took an exported graph to find.
     *
     * So it is a check, on the square that can be cleared to fix it.  There is no validate command to
     * run, and this is the list somebody reads before starting.
     */
    @Test
    public void testALocomotiveInTwoPlacesIsReportedAsAnError() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey first = new TileKey("main", 1, 1);
        TileKey second = new TileKey("main", 4, 1);

        session.getStore().setStation(first, true);
        session.setPointName(first, "BottomMainA");

        session.getStore().setStation(second, true);
        session.setPointName(second, "BottomMainC");

        // Written straight into the configuration, which is how it happens: one placement from an
        // import and one made by hand, neither aware of the other
        session.setPointProperty(first, "loc", new org.json.JSONObject().put("name", "065 001-0 DB"));
        session.setPointProperty(second, "loc", new org.json.JSONObject().put("name", "065 001-0 DB"));

        session.rebuild();

        boolean reported = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (!org.traincontrol.automationui.AutonomyChecks.DUPLICATE_LOCOMOTIVE
                .equals(finding.getMessageKey())) continue;

            reported = true;

            assertEquals(finding.getSeverity(),
                org.traincontrol.automationui.AutonomyChecks.Severity.ERROR,
                "a setup that will refuse every path is not a warning");

            // The subject carries the locomotive even though the editor shows the square instead:
            // a finding with a tile is described by its tile, which is what gives it somewhere to
            // jump to.  Anything that wants the name can still have it.
            assertEquals(finding.getSubject(), "065 001-0 DB",
                "the finding no longer carries which locomotive is doubled up");

            assertNotNull(finding.getTile(),
                "without a square there is nothing for the reader to jump to and clear");
        }

        assertTrue(reported, "a locomotive standing in two places was not reported at all");
    }

    /**
     * And one locomotive in one place is not reported.
     *
     * The precondition that keeps the test above honest: a check that fired on every placement would
     * satisfy it and make the list useless.
     */
    @Test
    public void testALocomotiveInOnePlaceIsNotReported() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey tile = new TileKey("main", 1, 1);

        session.getStore().setStation(tile, true);
        session.setPointName(tile, "BottomMainA");
        session.placeLocomotive(tile, "065 001-0 DB");

        session.rebuild();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            assertFalse(org.traincontrol.automationui.AutonomyChecks.DUPLICATE_LOCOMOTIVE
                .equals(finding.getMessageKey()),
                "a locomotive standing in one place was reported as being in two");
        }
    }

    /**
     * And placing it somewhere new takes it off where it was, so the check never fires from a move.
     */
    @Test
    public void testMovingALocomotiveDoesNotLeaveItInTwoPlaces() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Restored", null);
        session.getStore().setActiveConfiguration("Restored");

        TileKey was = new TileKey("main", 1, 1);
        TileKey now = new TileKey("main", 4, 1);

        session.placeLocomotive(was, "065 001-0 DB");
        session.placeLocomotive(now, "065 001-0 DB");

        assertNull(session.getPointProperty(was, "loc"),
            "the locomotive is still recorded where it was, which is what invalidates the layout");

        assertNotNull(session.getPointProperty(now, "loc"),
            "the locomotive is not recorded where it was put");
    }

    /**
     * A save that declines to tidy up SAYS SO, and names the pages (DR-B10).
     *
     * The test next door proves the setup survives a page that did not load. This one is about the
     * other half of that finding, which had no coverage at all: nobody was ever told.
     *
     * `absent` was computed inside `save()` and used only as a boolean. The comment beside it said a
     * caller "can ask store.pagesNotLoaded the same question" - and no caller did; the method's only
     * two references were inside `save()` itself. Meanwhile five of the six doors that call `save()`
     * threw the returned Reconciliation away, so the one moment when putting the missing file back
     * would have fixed everything passed in silence, while the next page operation quietly retired
     * that page's id.
     *
     * The distinction the report could not previously make is the point: an EMPTY reconciliation and a
     * REFUSED one were the same object. "Nothing needed tidying" and "I was not allowed to tidy" are
     * opposite situations and only one of them is worth interrupting somebody for.
     *
     * MUTATION: having `save()` return a plain `new Reconciliation()` for the incomplete case - which
     * is what it did - fails this test.
     */
    @Test
    public void testASaveThatDeclinesToTidySaysWhichPagesStoppedIt() throws IOException
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        session.setPointName(new TileKey("second", 1, 1), "Second Platform");
        session.save();

        // Everything is here, so nothing stops it and nothing is said.
        AutonomySession whole = new AutonomySession(layout);

        whole.open(Arrays.asList(runOfTrack(), secondPage()));

        assertTrue(whole.pagesSafeToJudge(),
            "with every page loaded the setup should be safe to judge, or the assertion below is "
            + "testing the wrong thing");

        assertFalse(whole.save().wasDeclined(),
            "a save with nothing missing reported itself as refused, which would put a dialog in "
            + "front of somebody on an ordinary save");

        // --- and now with the second page missing ------------------------------------------------
        AutonomySession partial = new AutonomySession(layout);

        partial.open(Arrays.asList(runOfTrack()));

        assertFalse(partial.pagesSafeToJudge(),
            "the session cannot tell that a page it knows about is missing, so nothing below is "
            + "being tested");

        org.traincontrol.automationui.AutonomyCompanionStore.Reconciliation report = partial.save();

        assertTrue(report.wasDeclined(),
            "a save that left the whole setup alone reported itself as an ordinary clean save, so "
            + "every door showing this to somebody would say nothing");

        assertTrue(report.getDeclinedBecauseAbsent().contains("second"),
            "the refusal did not name the page that caused it, which is the one thing that makes it "
            + "actionable - the reader has to know which file to put back.  Got: "
            + report.getDeclinedBecauseAbsent());
    }

    /**
     * A page that did not load keeps its setup.
     *
     * OB-068. `CS2File.parseLayout` skips a page whose file will not parse or is not there, quietly and
     * on purpose - on a layout that lives in OneDrive an unhydrated placeholder or a file held by the
     * sync client is enough, and neither is anything the user did.
     *
     * The session then opens without it, and `save()` reconciled against the pages that DID load: every
     * name, station, direction, length, signal pairing and caption on the missing page read as track
     * that had been deleted, and was pruned and written. Three of the four doors that reach that save
     * discard the report, so it happened in silence - and the next page operation dropped the page from
     * gleisbild.cs2 as well, orphaning its file.
     *
     * `readShared` is relaxed about the same absence, and says why: "Absent is fine - the page may
     * simply not be loaded." Both halves agreed a missing page was survivable and then one of them
     * deleted its contents.
     *
     * The mutation here is the absence itself: set up two pages, then reopen holding only one.
     */
    @Test
    public void testAPageThatDidNotLoadKeepsItsSetup() throws IOException
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey onSecond = new TileKey("second", 1, 1);
        TileKey onFirst = new TileKey("main", 2, 1);

        session.setPointName(onSecond, "Second Platform");
        session.getStore().setStation(onSecond, true);

        session.setPointName(onFirst, "First Platform");
        session.getStore().setStation(onFirst, true);

        session.save();

        // --- the second page fails to load this time -------------------------------------------
        AutonomySession partial = new AutonomySession(layout);

        partial.open(Arrays.asList(runOfTrack()));

        // Its entries are still in memory, but under RAW ID keys - with no page called "second"
        // loaded, pageOf has no name to resolve id 2 to, so it hands back the id. That is why they
        // look like squares that do not exist to anything working in page names, and it is exactly
        // the state in which they must not be deleted.
        assertFalse(partial.getStore().pagesNotLoaded(
                java.util.Collections.singletonList("main")).isEmpty(),
            "the store cannot tell that a page it knows about is missing, so the guard below has "
            + "nothing to act on and this test proves nothing");

        partial.save();

        // --- and the page that never loaded still has everything --------------------------------
        AutonomySession reopened = new AutonomySession(layout);

        reopened.open(Arrays.asList(runOfTrack(), secondPage()));

        assertEquals(reopened.getStore().getPointName(onSecond), "Second Platform",
            "a page that merely failed to load had its station name pruned as deleted track. Nothing "
            + "the user did caused the page to be missing, and nothing told them it had gone");

        assertTrue(reopened.getStore().isStation(onSecond),
            "a page that merely failed to load had its station pruned");

        assertEquals(reopened.getStore().getPointName(onFirst), "First Platform",
            "the page that DID load lost its setup instead");
    }

    /**
     * A legacy file naming two homes for one locomotive imports one of them.
     *
     * OB-075. `setHome` sweeps duplicates, and its comment names the reason - "a rule enforced at one
     * door of two is the shape this defect came from" (TD-8). The import is the second door: it writes
     * "home" straight into the configuration, so it went round setHome and its sweep entirely.
     *
     * A pre-rule autonomy.json can legitimately hold two, because the rule did not exist when it was
     * written. Both were imported; `Layout.rebuildHomeStations` then dropped one by iteration order
     * with a log line, and the next capture wrote that arbitrary choice back permanently. The user
     * ended up with a home they never chose and nothing to say which had been theirs.
     */
    @Test
    public void testAnImportLeavesOneHomePerLocomotive() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        // Homes live in a configuration, so there has to be one for the import to write into.
        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        org.json.JSONArray points = new org.json.JSONArray();

        // Both squares carry a sensor the diagram has - 11 at 1,1 and 12 at 4,1 - and both name the
        // SAME locomotive as home, which is what a file written before the one-home rule looks like.
        // which is what a file written before the one-home rule looks like.
        org.json.JSONObject first = new org.json.JSONObject();
        first.put("name", "Hauptbahnhof");
        first.put("station", true);
        first.put("s88", 11);
        first.put("home", "BR 232");
        points.put(first);

        org.json.JSONObject second = new org.json.JSONObject();
        second.put("name", "Nebenbahnhof");
        second.put("station", true);
        second.put("s88", 12);
        second.put("home", "BR 232");
        points.put(second);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        int homes = 0;

        for (TileKey square : new TileKey[] {new TileKey("main", 1, 1), new TileKey("main", 4, 1)})
        {
            if ("BR 232".equals(session.getPointProperty(square, "home"))) homes++;
        }

        assertEquals(homes, 1,
            "the import gave one locomotive " + homes + " homes. Only one can survive: "
            + "rebuildHomeStations drops the rest by iteration order, and the next capture writes "
            + "that arbitrary choice back permanently - so the user keeps a home they never chose");

        assertEquals(result.duplicateHomes, 1,
            "the import cleared a home without counting it, so nothing can tell the user that a "
            + "choice was made on their behalf");
    }
}
