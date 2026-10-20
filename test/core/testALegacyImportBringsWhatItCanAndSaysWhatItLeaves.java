package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import support.SourceOf;

/**
 * An import of a legacy file or a bundle brings its names, trains, settings and stations across, never
 * overwrites what is set, and says what it left behind.
 *
 * One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136 Phase 3): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder
 * every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testALegacyImportBringsWhatItCanAndSaysWhatItLeaves extends AutonomySessionFixture
{

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

        // A configuration to import INTO, as every other import test has and as the menu path does.
        // The operational settings - priority, speed multiplier, exclusions, the station's own length
        // limit - are stored per configuration, so without one they are silently skipped and this
        // method would have asked nothing about them.
        session.getStore().createConfiguration("Legacy", null);
        session.getStore().setActiveConfiguration("Legacy");

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

        // A STATION'S CAPACITY, NOT A TRACK LENGTH.
        //
        // This asserted `getTileLength(tile) == 240` until 2026-08-31, which is the confusion the
        // import itself was making: `points[].maxTrainLength` is the longest train the STATION can
        // hold, and `tileLengths` is how long a piece of TRACK is - the quantity the shortest-track
        // and longest-track routing rules are computed from.  A legacy file carries both, capacity on
        // the points and length on the edges, and the import was reading one as the other.
        //
        // What it cost: every upgrading user's stations lost their limits, so validateTrainLength
        // passed a train of any length onto any platform; and the squares that had a capacity gained
        // a track length nobody measured, which changed which route those rules pick.
        assertEquals(session.getPointProperty(tile, "maxTrainLength"), 240,
            "the station's own length limit did not come across, so autonomy will send a train of "
            + "any length onto it");

        assertEquals(session.getStore().getTileLength(tile), 0,
            "the station's capacity was written into the square's TRACK LENGTH, which is a different "
            + "measurement and feeds the shortest-track and longest-track routing rules");

        assertEquals(result.matched, 1, "exactly one point should have matched");

        assertEquals(result.unmatched, Arrays.asList("NotOnThisDiagram"),
            "a point whose sensor is not on this diagram must be reported, not silently dropped");
    }

    /**
     * An imported legacy file brings its settings, not only its points.
     *
     * A 2.7.4c `autonomy.json` is one object: the points and edges, and above them the whole settings
     * panel - pace, default speed, how many trains may run, whether routes are fired, and the
     * timetable. `importLegacy` read the points array and nothing else, so an upgrading user kept
     * their station names and lost everything about how their railway RUNS.
     *
     * On Adam's own legacy file that is ten settings. The timetable and the two route-activation keys
     * are deliberately NOT carried - see the assertions below for why each one would do harm.
     *
     * Gap-filled, like the rest of this import: re-running it must not undo a setting somebody
     * changed after the first run.
     *
     * MUTATION: dropping the globals copy from importLegacy fails the first assertion; making it
     * overwrite rather than gap-fill fails the last.
     */
    @Test
    public void testALegacyImportBringsTheSettingsAboveThePoints() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Settings", null);
        session.getStore().setActiveConfiguration("Settings");

        org.json.JSONArray points = new org.json.JSONArray();

        org.json.JSONObject named = new org.json.JSONObject();
        named.put("name", "Hauptbahnhof");
        named.put("station", true);
        named.put("s88", 11);
        points.put(named);

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);
        legacy.put("edges", new org.json.JSONArray());
        legacy.put("minDelay", 3);
        legacy.put("maxDelay", 13);
        legacy.put("defaultLocSpeed", 41);
        legacy.put("maxActiveTrains", 4);
        legacy.put("activateRoutes", true);

        // BOTH keys, because the assertion below about the id list cannot fail without this one.
        // The fixture set only the flag, so removing the id list from the exclusion left the test
        // green - the more important half of the most important exclusion, untested.
        legacy.put("activateRouteIDs", new org.json.JSONArray());

        org.json.JSONArray timetable = new org.json.JSONArray();
        timetable.put(new org.json.JSONObject().put("loc", "Some Loc").put("secondsToNext", 90));
        legacy.put("timetable", timetable);

        session.importLegacy(legacy);

        org.json.JSONObject globals = session.getStore().getConfiguration("Settings")
            .optJSONObject("globals");

        assertNotNull(globals,
            "the imported file's settings did not arrive at all, so an upgrading user keeps their "
            + "station names and loses every rule about how the railway runs");

        assertEquals(globals.optInt("minDelay", -1), 3, "minDelay did not come across");
        assertEquals(globals.optInt("maxDelay", -1), 13, "maxDelay did not come across");
        assertEquals(globals.optInt("defaultLocSpeed", -1), 41,
            "the default locomotive speed did not come across");
        assertEquals(globals.optInt("maxActiveTrains", -1), 4,
            "the limit on how many trains may run did not come across");
        // AND THE THREE THAT MUST NOT COME ACROSS.
        //
        // activateRoutes and activateRouteIDs do not stay inside the configuration: parseAuto ends in
        // applyAutonomyRouteActivations, which disables every route in the live Central Station
        // database whose id is not listed, and fires the ones that are.  Adam's own legacy file says
        // `activateRoutes: true` with an empty list, so carrying them would switch off every route he
        // has - on import and again on every diagram edit.
        assertFalse(globals.has("activateRoutes"),
            "the import carried the route activation flag, so loading the setup will disable every "
            + "route in the Central Station that the file does not list - and the file lists none");

        assertFalse(globals.has("activateRouteIDs"),
            "the import carried the route id list, which decides which routes are switched off");

        // The timetable is left in autonomy.json rather than half-migrated.  Its legs name points by
        // NAME, and a legacy name only reaches the diagram when its sensor lands on exactly one
        // square; TimetablePath.fromJSON throws on the first edge it cannot resolve and drops that
        // leg.  Measured on Adam's file, two of thirty-six could survive - and the next capture writes
        // what is left back over these globals for good.
        assertFalse(globals.has("timetable"),
            "the timetable was carried across, where all but a couple of its legs will be dropped one "
            + "warning at a time and the remains written back permanently by the next capture");

        // Neither points nor edges belong up here: they are the setup, not the settings.
        assertFalse(globals.has("points"), "the points were copied into the settings as well");
        assertFalse(globals.has("edges"), "the edges were copied into the settings as well");

        // GAP-FILLED, not overwritten.  Somebody who imported, then changed a setting, then imported
        // again must keep their change - which is the rule every other part of this import follows.
        globals.put("minDelay", 9);

        session.importLegacy(legacy);

        assertEquals(session.getStore().getConfiguration("Settings").optJSONObject("globals")
            .optInt("minDelay", -1), 9,
            "a second import overwrote a setting that had been changed since the first one");
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

        // A SWITCHED-OFF STATION ARRIVES ON, AND NOT ONE AUTONOMY CHOOSES (Adam 2026-09-24: "translate as on but
        // not auto destination").  At v2.8.1 it kept autonomy out and still took a train sent by hand; carried as
        // `active: false` it became a square nothing may pass through or end on.
        assertEquals(session.getPointProperty(tile, "active"), null,
            "a station the 2.8.1 file switched off arrived switched off, so no train may be sent there by hand");

        assertFalse(session.isAutoDestination(tile), "a station the 2.8.1 file switched off arrived as one autonomy"
            + " chooses, which is what switching it off kept it from");

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
     * choice is the caller, and there are TWO of them (OB-130, settled 2026-08-29). Creating the
     * first configuration on a layout is the one moment there are no decisions to overrule. Importing
     * a legacy graph is not - it will overrule them - and Adam has ruled that it may: "yes, it may
     * override." A page repeating another page’s sensors is not a preference but a diagram the
     * reduction cannot read, and a setting made before the rule existed is not a decision the rule
     * has to honour.
     *
     * This comment used to end "if a second call site ever appears, this is the test whose comment
     * explains why it must not". It appeared, it was argued over, and it stays.
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
     * The invariant the comment above states - "runs only from the one call site that has no
     * decisions to overrule" - checked against src/, not just asserted in prose.
     *
     * The idempotence test above calls excludeRepeatedSensorPages() itself, twice, so it cannot tell a
     * safe call site from an unsafe one - both look identical from here. The comment names the actual
     * protection as "the caller", so this checks the caller.
     *
     * TWO CALL SITES, BOTH SANCTIONED (OB-130, settled 2026-08-29). Configuration creation (:885) and
     * legacy import into an existing setup (:1137). So this is no longer a count held while an argument
     * runs - it is the number of places the behaviour is wanted, and a third would still be one nobody
     * has argued for.
     *
     * The second was raised on the reading that it silently re-excludes a page the user had
     * switched back on. Adam ruled twice, and the two rulings are about different things: on
     * 2026-08-29 that the import "may override" pages he had chosen to keep - a page whose sensors
     * repeat another's is not a preference - and on 2026-09-11 that a page he had deliberately turned
     * BACK ON stays on, which is a decision rather than the absence of one. Both call sites are still
     * wanted; what changed is what the method itself skips.
     */
    @Test
    public void testExcludeRepeatedSensorPagesHasOnlyOneCallSite() throws IOException
    {
        List<File> sources = javaFilesUnder(new File("src"));

        assertFalse(sources.isEmpty(),
            "precondition: nothing was scanned - run from the project root, not a subdirectory");

        int calls = 0;

        for (File source : sources)
        {
            for (String line : Files.readAllLines(source.toPath(), StandardCharsets.UTF_8))
            {
                int comment = line.indexOf("//");
                String code = comment < 0 ? line : line.substring(0, comment);

                if (code.contains(".excludeRepeatedSensorPages(")) calls++;
            }
        }

        // TWO, AND THAT IS NOW A DECISION RATHER THAN A TRUCE (OB-130).
        //
        // This used to say the number was held while a question ran: the comment on
        // testRunningAgainOverASettledSetupChangesNothing called a second call site the bug, and the
        // second site carried its own later justification, and the two contradicted each other. The
        // question was whether importing a legacy graph may shut pages the operator had already
        // decided about.
        //
        // Adam, 2026-08-29: "yes, it may override." A page that repeats another page\u2019s sensors is
        // not a preference - it is a diagram the reduction cannot make sense of - and a setting made
        // before the rule existed is not a decision the rule has to honour.
        //
        // So the ratchet stands for a different reason than it did: not "nobody has settled this" but
        // "these two are wanted and a third has never been argued for".
        assertEquals(calls, 2,
            "excludeRepeatedSensorPages() is called from " + calls + " place(s) in src/, not the 2 "
            + "that are wanted - creating a configuration, and importing a legacy graph. Both are "
            + "settled (OB-130); a third site would be shutting pages somewhere nobody has argued "
            + "for, and a removed one means the behaviour was dropped without the ruling changing.");
    }

    /**
     * Every .java file under a directory, for the source scan above.
     */
    private static List<File> javaFilesUnder(File dir)
    {
        List<File> found = new ArrayList<>();

        File[] children = dir.listFiles();

        if (children == null) return found;

        for (File child : children)
        {
            if (child.isDirectory()) found.addAll(javaFilesUnder(child));
            else if (child.getName().endsWith(".java")) found.add(child);
        }

        return found;
    }

    /**
     * A page the user deliberately re-enabled is silently shut again by the next legacy import.
     *
     * The javadoc on {@link #testRunningAgainOverASettledSetupChangesNothing} promises this "must not"
     * happen, and names the caller as what makes that safe - but AutonomyViewerPanel.java now calls
     * excludeRepeatedSensorPages() from a second place, at line 1137, when importing a legacy graph into
     * an EXISTING setup.  That call site is not a different method: it is the same
     * excludeRepeatedSensorPages() exercised above, called again after the operator has had a chance to
     * change their mind.
     *
     * excludeRepeatedSensorPages() only skips pages ALREADY in the excluded set (:825) - it has no
     * record of a page having been deliberately turned back on, so re-running it after that happens
     * treats the page exactly like one that was never looked at, and re-excludes it.
     *
     * CLOSED, AND ENABLED (Adam 2026-09-11). Asked which way it should go, he took the first:
     * a page the operator turned back on stays on. `AutonomyCompanionStore` now remembers a deliberate
     * re-enable - `getPagesKeptDespiteRepeats`, written when a page that really was excluded is
     * switched off again - and `excludeRepeatedSensorPages` skips those pages.
     *
     * **The 2026-08-29 ruling still stands, and this does not contradict it.** Asked whether the import
     * may shut pages he had chosen to keep, Adam said *"yes, it may override"*, and that is about pages
     * nobody has had an opinion on: a page whose sensors repeat another's is not a preference, it is a
     * diagram the reduction cannot make sense of. An explicit re-enable IS an opinion, and the method's
     * own javadoc already drew that line - *"turning one back on is not undone by adding a
     * configuration"*. The legacy import was the one door that undid it.
     *
     * This test was off for two weeks because enabling it was the reproduction. It is the assertion
     * that closes the finding, so it goes back on with the fix rather than being rewritten to match it.
     *
     * MUTATION: take the `getPagesKeptDespiteRepeats` skip out of `excludeRepeatedSensorPages` and this
     * is the only test in the class that fails.
     */
    @Test
    public void testALegacyImportDoesNotReExcludeAPageTheOperatorTurnedBackOn() throws Exception
    {
        LayoutDiagram first = pageOnDisk();

        LayoutDiagram repeat = new LayoutDiagram("repeat", 6, 4, null, null);
        repeat.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        repeat.setPageId("2");
        repeat.checkBounds();

        session.open(Arrays.asList(first, repeat));

        // First call site: configuration creation auto-excludes the repeating page.
        assertEquals(session.excludeRepeatedSensorPages(), Arrays.asList("repeat"),
            "precondition: creating the setup shuts the repeating page, same as the test above");

        // The operator looks at the page checkboxes and deliberately turns "repeat" back on.
        session.getStore().setPageExcluded("repeat", false);

        assertFalse(session.getStore().getExcludedPages().contains("repeat"),
            "precondition: the operator's re-enable did not take");

        // Second call site: a legacy import into the now-existing setup calls the very same method
        // (AutonomyViewerPanel.java:1137), which is the "second call site" the javadoc says must not
        // exist.
        session.excludeRepeatedSensorPages();

        assertFalse(session.getStore().getExcludedPages().contains("repeat"),
            "a page the operator deliberately re-enabled was re-excluded by the next legacy import - "
            + "this is the invariant testRunningAgainOverASettledSetupChangesNothing's comment says "
            + "must not be violated");
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
     * A legacy file naming two homes for one locomotive imports one of them.
     *
     * OB-075. `setHome` sweeps duplicates, and its comment names the reason - "a rule enforced at one
     * door of two is the shape this defect came from". The import is the second door: it writes
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

    /**
     * An import says what it left on a square already taken: a placement onto a square another train stands on, and a
     * home onto a square that is already another train's home (OB-304).
     *
     * Both were skipped without a word - the square keeps its train and its home, which is right, and the operator was
     * told nothing about the train the file put there.  The same train already on its own square, which is what a file
     * imported a second time meets, is nothing to say.
     *
     * MUTATION: skip either silently again, or name the train already there as another, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAnImportSaysWhatItLeftOnATakenSquare() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey west = new TileKey("main", 1, 1);
        TileKey east = new TileKey("main", 4, 1);

        session.placeLocomotive(west, "BR 01");
        session.setHome(east, "BR 01");

        org.json.JSONArray points = new org.json.JSONArray();

        // ANOTHER TRAIN onto the square BR 01 stands on (sensor 11), and another home onto the square that is BR 01's
        points.put(new org.json.JSONObject().put("name", "Hauptbahnhof").put("s88", 11)
            .put("loc", new org.json.JSONObject().put("name", "BR 02")));
        points.put(new org.json.JSONObject().put("name", "Nebenbahnhof").put("s88", 12).put("home", "BR 03"));

        // AND THE SAME TRAIN onto its own square, from a second old point on that sensor: nothing to say
        points.put(new org.json.JSONObject().put("name", "Hauptbahnhof Einfahrt").put("s88", 11)
            .put("loc", new org.json.JSONObject().put("name", "BR 01")));

        AutonomySession.LegacyImport result = session.importLegacy(new org.json.JSONObject().put("points", points));

        assertEquals(result.squareTaken, Arrays.asList("BR 02"), "a placement onto a square another train stands on was"
            + " skipped without a word, or the train already standing there was named as another (OB-304)");

        assertEquals(result.homeSquareTaken, Arrays.asList("BR 03"), "a home onto a square already home to another"
            + " train was skipped without a word (OB-304)");

        // AND THE SQUARES KEPT WHAT THEY HAD
        Object standing = session.getPointProperty(west, "loc");

        assertTrue(standing instanceof org.json.JSONObject
            && "BR 01".equals(((org.json.JSONObject) standing).optString("name")), "the import put another train on a"
            + " square that had one: " + standing);

        assertEquals(session.getPointProperty(east, "home"), "BR 01", "the import took a square's home away for another"
            + " train");
    }

    /**
     * A facing the square records is kept by an import only where trains may arrive that way, and counted as the guess
     * it is (OB-304).
     *
     * Where the old file cannot say which way a placed train faces, the facing the square last recorded was kept as it
     * stood - unchecked against the ways trains may arrive, though the guess made in its place prefers one of
     * those, and uncounted, so the log said no facing was guessed.  Here the middle square of a line, with
     * arrivals from the east barred, records the barred way; the file places a train there with no edges to say.  Then,
     * the bar lifted, the same way is one trains may arrive in, and is kept - and counted.
     *
     * MUTATION: keep any recorded facing again, or keep one without counting it, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAnImportKeepsARecordedFacingOnlyWhereTrainsMayArrive() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        session.getStore().createConfiguration("Barred", null);
        session.getStore().setActiveConfiguration("Barred");

        TileKey middle = new TileKey("main", 3, 1);

        // A STATION, whose barred sides are read - a square that is not one has them ignored
        session.setStation(middle, true);
        session.setBarredArrivals(middle, java.util.EnumSet.of(Side.E));
        session.rebuild();

        Set<Side> mayArrive = session.homeFacingsFor(middle);

        Side barredWay = null;

        for (Side way : session.facingsFor(middle).values())
        {
            if (!mayArrive.contains(way)) barredWay = way;
        }

        assertFalse(mayArrive.isEmpty(), "precondition: no copy of the middle square is one trains may arrive at");
        assertNotNull(barredWay, "precondition: no copy of the middle square faces a way trains may not arrive in: "
            + session.facingsFor(middle) + ", " + mayArrive);

        // THE LAST OCCUPANT'S FACING: the barred way
        session.setFacing(middle, barredWay);

        org.json.JSONObject legacy = new org.json.JSONObject().put("points", new org.json.JSONArray().put(
            new org.json.JSONObject().put("name", "Mitte").put("s88", 12)
                .put("loc", new org.json.JSONObject().put("name", "BR 50"))));

        AutonomySession.LegacyImport barred = session.importLegacy(legacy);

        assertTrue(session.homeFacingsFor(middle).contains(session.getFacing(middle)), "the import kept the facing the"
            + " square recorded, " + session.getFacing(middle) + ", a way trains may not arrive in - the train stands"
            + " where autonomy will not start it, though the guess made in its place prefers a way trains may arrive"
            + " (REG3-C1, OB-304)");

        assertEquals(barred.facingsInvented, 1, "the facing the import chose for the train is not counted as a guess");

        // THE BAR LIFTED, in a configuration of its own: the same way is now one trains may arrive in, and stays
        session.setBarredArrivals(middle, java.util.EnumSet.noneOf(Side.class));

        session.getStore().createConfiguration("Open", null);
        session.getStore().setActiveConfiguration("Open");
        session.rebuild();

        assertTrue(session.homeFacingsFor(middle).contains(barredWay), "precondition: with the bar lifted, " + barredWay
            + " is still not a way trains may arrive at the middle square");

        session.setFacing(middle, barredWay);

        AutonomySession.LegacyImport lifted = session.importLegacy(legacy);

        assertEquals(session.getFacing(middle), barredWay, "the import did not keep a recorded facing trains may arrive"
            + " in (RLA2-B3)");

        assertEquals(lifted.facingsInvented, 1, "the import kept the facing the square recorded, which is no evidence"
            + " about the train the file puts there, and did not count it as a guess - the log says nothing was guessed"
            + " (OB-304)");
    }

    /**
     * The import's message names every skip it counts or lists (OB-304): the trains and homes left on a taken square,
     * and the homes the file named twice - each read by the viewer's import door into the message, through its own
     * sentence in the English bundle.
     *
     * MUTATION: leave any of the three out of the message, and this fails naming it.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheImportMessageNamesEverySkip() throws Exception
    {
        String door = SourceOf.declaration("private void importLegacyGraph(");

        java.util.Properties english = new java.util.Properties();

        try (java.io.InputStream in = AutonomySession.class.getResourceAsStream(
            "/org/traincontrol/resources/messages.properties"))
        {
            english.load(in);
        }

        for (String[] skip : new String[][] {
            {"result.squareTaken", "autosetup.ui.infoLegacySquareTaken"},
            {"result.homeSquareTaken", "autosetup.ui.infoLegacyHomeSquareTaken"},
            {"result.duplicateHomes", "autosetup.ui.infoLegacyDuplicateHomes"}})
        {
            int read = door.indexOf(skip[0]);

            assertTrue(read > 0, "the import door never reads " + skip[0] + ", so the operator is not told (OB-304)");

            int said = door.indexOf(skip[1], read);

            assertTrue(said > read && said - read < 300, "the import door reads " + skip[0] + " and does not say it"
                + " through " + skip[1] + " (OB-304)");

            assertNotNull(english.getProperty(skip[1]), "the English bundle has no " + skip[1]);
        }
    }
    /**
     * The import NAMES the signals it will no longer drive, rather than counting them.
     *
     * **An aggregate that cannot say the thing the operator needs.** A legacy file's edge commands
     * are mostly switch positions, which the derivation reproduces from the track diagram and which
     * nobody has to think about. A signal is the exception: the new model drives one only where the
     * operator pairs it with a station, by address, from that station's own menu.
     *
     * Measured on Adam's own file, which is where this came from. Twelve signals were driven red by
     * hand; he paired seven from memory; the other five stay green after every arrival. The report he
     * saw said "69 connections with commands" - true, useless, and it means three of the twelve. The
     * model does not obey signals, so no train is misrouted, but four stations show an aspect that
     * lies about occupancy until five specific pairings are made.
     *
     * **Named, not compared, and on purpose.** Deciding which of these is already paired means
     * resolving each address to a square through the reduction - and this report runs on a file that
     * has not been imported yet, so the reduction it would need is the one the import is about to
     * replace. Naming a signal the operator has already paired costs them a glance; omitting one
     * costs them a signal that lies for as long as the railway runs.
     *
     * MUTATION: removing the signal block fails the second assertion; the first is the control that
     * stops it passing by reporting nothing at all.
     */
    @Test
    public void testALegacyImportNamesTheSignalsItStopsDriving() throws Exception
    {
        org.json.JSONObject switchesOnly = new org.json.JSONObject("{'points':[],'edges':["
            + "{'start':'A','end':'B','commands':[{'acc':'Switch 68','state':'straight'}]}"
            + "]}".replace('\'', '\"'));

        java.util.List<String> without = session.whatALegacyImportLeaves(switchesOnly);

        // THE CONTROL, twice over: the report works at all, and a file whose commands are only switch
        // positions gets NO signal line - which is what stops the assertion below being satisfied by
        // a report that names something on every file.
        assertEquals(without.size(), 1,
            "expected exactly the edge-command line for a file whose only extra is a switch "
            + "position, and got: " + without);

        org.json.JSONObject withSignals = new org.json.JSONObject("{'points':[],'edges':["
            + "{'start':'A','end':'B','commands':[{'acc':'Switch 68','state':'straight'},"
            + "{'acc':'Signal 116','state':'red'}]},"
            + "{'start':'C','end':'D','commands':[{'acc':'Signal 37','state':'red'},"
            + "{'acc':'Signal 116','state':'green'}]}"
            + "]}".replace('\'', '\"'));

        java.util.List<String> named = session.whatALegacyImportLeaves(withSignals);

        assertEquals(named.size(), without.size() + 1,
            "the import said nothing about the signals it will stop driving.  The aggregate command "
            + "count cannot name them, and a signal nobody pairs shows an aspect that lies about "
            + "occupancy for as long as the railway runs (RG4-B1).  Report was: " + named);

        String line = null;

        for (String candidate : named)
        {
            if (candidate.contains("37") && candidate.contains("116")) line = candidate;
        }

        assertNotNull(line,
            "the signal line does not name both addresses, which is the whole of what it is for - "
            + "the operator works down it with Pair Signal open: " + named);

        // TWO, not three: Signal 116 is authored on both edges and is one signal to pair.
        //
        // Asserted as the RENDERED COUNT rather than `line.contains("2")`.  The addresses
        // are in the same sentence, so a substring check passes on a wrong count of 3 as soon as any
        // address in the list contains a digit 2 - which is the ordinary case for a real file.
        assertTrue(line.startsWith("2 "),
            "the line should begin with the number of distinct signals, and say two - 116 appears on "
            + "two edges and is still one signal to pair.  Line was: " + line);

        // AND IN ADDRESS ORDER, because it is a work list somebody reads down.
        assertTrue(line.indexOf("37") < line.indexOf("116"),
            "the addresses are not in order, and an arbitrary order in a list of twelve is a list "
            + "the operator loses their place in: " + line);
    }

    /**
     * The signal-name parser takes signals and leaves everything else alone.
     *
     * A legacy command names its accessory as a display string, so this is string work on data the
     * user could have typed. Each row below is a way that has gone wrong somewhere in this tree: a
     * switch that must not be read as a signal, a name that merely BEGINS with the word, a number
     * that does not parse, and the null that arrives from a command with no `acc` at all.
     */
    @Test
    public void testTheSignalNameParserIsNotFooled() throws Exception
    {
        assertEquals(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signal 116"),
            Integer.valueOf(116), "the ordinary case");

        assertEquals(org.traincontrol.automationui.AutonomySession.legacySignalAddress("signal 37"),
            Integer.valueOf(37), "the name is a display string and its case was never guaranteed");

        assertNull(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Switch 68"),
            "a switch position is reproduced from the track diagram and needs no attention - naming "
            + "one here would bury the signals in a list of sixty");

        assertNull(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signalbox 4"),
            "a user-named accessory that merely begins with the word is not a signal");

        // THE NAME THIS PROGRAM ACTUALLY GENERATES for anything that is not the implicit protocol.
        // `Accessory.getNameWithProtocol` appends the decoder, so a DCC railway writes
        // "Signal 116 DCC" - and parsing the whole remainder as a number returned null for every one
        // of them.  The address set stayed empty and the report written to stop a signal being
        // silently left out was silent about an entire protocol.
        //
        // Adam's own five signals are MM2, which is exactly why a fixture built from his file could
        // not see this.
        assertEquals(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signal 116 DCC"),
            Integer.valueOf(116), "a DCC signal is a signal");

        assertEquals(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signal 5 MFX"),
            Integer.valueOf(5), "and any other protocol suffix");

        assertNull(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signal east"),
            "a report that throws on a malformed old file is worse than one that leaves a line out");

        // NOT AN ADDRESS, though Integer.valueOf takes both.  Rendering "-5" into the
        // operator's work list sends them looking for a signal that cannot exist.
        assertNull(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signal -5"),
            "a negative number is not an accessory address");

        assertNull(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signal +116"),
            "nor a signed one");

        assertNull(org.traincontrol.automationui.AutonomySession.legacySignalAddress("Signal 116red"),
            "digits running straight into something else is not a name this program writes");

        assertNull(org.traincontrol.automationui.AutonomySession.legacySignalAddress(null),
            "a command with no acc at all");
    }
    /**
     * A legacy import says it is leaving hand-written locks behind.
     *
     * A 2.8.1 edge could carry `lockedges` - *"when this edge is taken, these others are locked
     * too"* - and the diagram model derives locks from geometry instead: `GraphReducer.deriveLocks`
     * locks any two edges that occupy a shared tile.
     *
     * That reproduces most of them and **cannot reproduce the conservative kind**: a lock between
     * edges that share no tile, written by hand for parallel adjacent tracks, an electrical section, a
     * clearance rule. Those vanish, and what makes it worth a line is that two trains are then
     * permitted to move at once where the file forbade it.
     *
     * The import already speaks about the four other things it drops - edge commands, edge lengths,
     * the timetable, route activations - and presents that list as the whole account. This was the
     * fifth thing and the only silent one. Adam's own legacy file carries 116 references across 50 of
     * its 90 edges.
     *
     * **Counted, not compared.** Deciding which of them the geometry already covers means building
     * the derived graph inside the report, and a report that names locks which turn out to be
     * reproduced is wrong in the safe direction; one that is silent about locks which are not is
     * wrong in the other.
     *
     * MUTATION: removing the `withLocks` block from `whatALegacyImportLeaves` fails the second
     * assertion, and the first is what stops that passing by reporting nothing at all.
     */
    @Test
    public void testALegacyImportSaysItIsLeavingHandWrittenLocksBehind() throws Exception
    {
        org.json.JSONObject plain = new org.json.JSONObject("{'points':[],'edges':["
            + "{'start':'A','end':'B','length':2}"
            + "]}".replace('\'', '\"'));

        java.util.List<String> without = session.whatALegacyImportLeaves(plain);

        // THE CONTROL: the report is working at all, and says the one thing this file does drop.
        assertEquals(without.size(), 1,
            "expected exactly the edge-length line for a file whose only extra is a length, and got: "
            + without);

        org.json.JSONObject locked = new org.json.JSONObject("{'points':[],'edges':["
            + "{'start':'A','end':'B','length':2,'lockedges':[{'start':'C','end':'D'}]},"
            + "{'start':'E','end':'F','lockedges':[{'start':'G','end':'H'}]}"
            + "]}".replace('\'', '\"'));

        java.util.List<String> with = session.whatALegacyImportLeaves(locked);

        assertEquals(with.size(), without.size() + 1,
            "the import dropped hand-written locks and said nothing about them.  Every other thing it "
            + "drops is spoken, and this was the only silent one - two trains can then move at once "
            + "where the file forbade it (ACC-B1).  Report was: " + with);

        boolean named = false;

        for (String line : with)
        {
            if (line.contains("2") && line.toLowerCase().contains("lock")) named = true;
        }

        assertTrue(named,
            "the line does not say how many connections carried locks, which is the whole of what it "
            + "is for: " + with);
    }
    /**
     * A modern export coming back through the legacy door is spotted by each of its three keys.
     *
     * `detectImportFormat` calls any file whose `points` is an ARRAY a legacy graph, and a
     * configuration serialised by `Layout.toJSON` is one of those - so a modern setup exported and
     * re-imported comes through a door that carries none of the fields the modern format added.
     *
     * **The first version of this check asked for `blocks` and the key is `block`**, singular, as
     * `Layout:7533` reads it and `AutonomyBuilder:844` writes it. The detector therefore missed every
     * file whose only modern key was that one, and nothing caught it because the fix shipped with no
     * test at all.
     *
     * Each key is asserted separately for that reason: a test that checked only `autoDestination`
     * would have passed against the broken one.
     *
     * MUTATION: renaming any of the three keys in `whatALegacyImportLeaves` fails its own assertion
     * here.
     */
    @Test
    public void testAModernExportIsSpottedByEachOfItsKeys() throws Exception
    {
        String[] keys = {"autoDestination", "protectingSignal", "block"};

        for (String key : keys)
        {
            String json = ("{'points':[{'name':'A','" + key + "':'x'}],'edges':[]}")
                .replace('\'', '"');

            java.util.List<String> said =
                session.whatALegacyImportLeaves(new org.json.JSONObject(json));

            assertEquals(said.size(), 1,
                "a file carrying `" + key + "` - a key the old format never wrote - was not "
                + "reported as looking like an export from this version.  Report was: " + said);
        }

        // AND THE CONTROL: a genuine legacy file must say nothing, or the check above is satisfied by
        // a report that fires on everything.
        String plain = "{'points':[{'name':'A','s88':1}],'edges':[]}".replace('\'', '"');

        assertEquals(session.whatALegacyImportLeaves(new org.json.JSONObject(plain)),
            new java.util.ArrayList<String>(),
            "a genuine 2.8.1 file was reported as a modern export, which would put a warning on every "
            + "supported migration");
    }
}
