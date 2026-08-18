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
}
