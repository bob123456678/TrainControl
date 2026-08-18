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

        assertFalse(new String(Files.readAllBytes(pageFile.toPath()), StandardCharsets.UTF_8)
            .contains(AutonomySession.STATION_LABEL_PREFIX),
            "and the page written back out no longer carries it");
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
}
