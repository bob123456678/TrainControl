package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import support.SourceOf;

/**
 * A setup is built into the railway, saved, reopened and captured back without losing what was decided, and a
 * page that would not read is never judged.  One of the topic classes split from `testAutonomyDiagramSession`
 * (FR-136 Phase 3); `AutonomySessionFixture` builds the session and layout folder every claim starts from.
 *
 * @author Adam
 */
public class testASetupIsBuiltSavedAndReopenedWhole extends AutonomySessionFixture
{

    /**
     * A stand-in for a page that would not read is not a page autonomy may judge.  `CS2File` substitutes a blank page
     * (same NAME and ID) so that link tiles after it still resolve; `pagesNotLoaded` compares names, so the stand-in
     * made the session think it saw the whole railway, and the next `save()` reconciled the real page's setup against
     * one text tile (MT-135, "all stations are gone", by a third route).
     *
     * Three loops in this class build that name set, and a repair reaching two of them is worse than one reaching
     * none (the prune is declined while `wasDeclined()` reads false).  This asserts the ANSWER, so it holds however
     * many copies of them there are.
     *
     * MUTATION: take `if (page.isUnreadable()) continue;` out of `pagesSafeToJudge` or out of `open`
     * and this fails.
     */
    @Test
    public void testAPageThatWouldNotReadIsNotJudged() throws IOException
    {
        // A setup written while both pages are genuinely there.
        session.open(Arrays.asList(runOfTrack(), secondPage()));
        session.initialize("Evening");

        TileKey onTheSecondPage = new TileKey("second", 1, 1);

        session.setPointName(onTheSecondPage, "Second Platform");
        session.save();

        // Now the second page's file will not read, so CS2File hands over a blank stand-in: same name, same id.
        LayoutDiagram standIn = new LayoutDiagram("second", 6, 4, null, null);

        standIn.setPageId("2");

        standIn.markUnreadable();

        AutonomySession withAStandIn = new AutonomySession(layout);

        withAStandIn.open(Arrays.asList(runOfTrack(), standIn));

        assertFalse(withAStandIn.pagesSafeToJudge(),
            "a blank stand-in for a page whose file would not read was accepted as a loaded page.  "
            + "Everything the setup holds about the real page is then judged against the one text tile "
            + "the stand-in carries, and written - which is MT-135 (FV3-A1)");

        // AND THE SETTING IS STILL THERE afterwards, which is the half that matters.
        withAStandIn.save();

        AutonomySession reopened = new AutonomySession(layout);

        reopened.open(Arrays.asList(runOfTrack(), secondPage()));

        assertEquals(reopened.getStore().getPointName(onTheSecondPage), "Second Platform",
            "the name on a page that could not be read was reconciled away by a save that should have "
            + "declined to judge it");

        // THE CONTROL: with both pages really present the session does judge, so the refusal above is about the
        // stand-in.
        assertAWholeLayoutIsJudged();
    }

    /** The control for `testAPageThatWouldNotReadIsNotJudged`: a layout with every page loaded is safe to judge. */
    private void assertAWholeLayoutIsJudged() throws IOException
    {
        AutonomySession whole = new AutonomySession(layout);

        whole.open(Arrays.asList(runOfTrack(), secondPage()));

        assertTrue(whole.pagesSafeToJudge(),
            "control: a layout with every page genuinely loaded is not safe to judge either, so the "
            + "assertion above would pass with the stand-in rule removed entirely");
    }

    /**
     * A capture does not judge a page whose file would not read either.  `captureFromLayout` prunes every entry whose
     * square is not in the graph on the pages "in play"; a blank stand-in counted, so every placement, home, priority
     * and exclusion on the real page was removed and written.  The capture runs when the autonomy editor opens and
     * closes, at the exit save, and before every configuration load.
     *
     * THE CONTROL is on the page that did read: a setting on a square that is not there is pruned.
     */
    @Test
    public void testACaptureDoesNotJudgeAPageThatWouldNotRead() throws IOException
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));
        session.initialize("Evening");

        TileKey onTheSecondPage = new TileKey("second", 1, 1);
        TileKey nowhereOnTheFirst = new TileKey("main", 7, 3);

        session.setPointProperty(onTheSecondPage, "priority", 7);
        session.setPointProperty(nowhereOnTheFirst, "priority", 3);
        session.save();

        LayoutDiagram standIn = new LayoutDiagram("second", 6, 4, null, null);

        standIn.setPageId("2");

        standIn.markUnreadable();

        AutonomySession withAStandIn = new AutonomySession(layout);

        withAStandIn.open(Arrays.asList(runOfTrack(), standIn));

        assertEquals(String.valueOf(withAStandIn.getPointProperty(onTheSecondPage, "priority")), "7",
            "precondition: the priority on the second page did not reach the reopened setup");
        assertEquals(String.valueOf(withAStandIn.getPointProperty(nowhereOnTheFirst, "priority")), "3",
            "precondition: the setting on a square the first page does not have did not reach the reopened setup,"
            + " so the control below cannot show the capture prunes");

        withAStandIn.captureFromLayout("{\"points\": [], \"edges\": []}", "Evening");

        assertEquals(String.valueOf(withAStandIn.getPointProperty(onTheSecondPage, "priority")), "7",
            "a capture removed the priority on a page whose file would not read - it judged the page by the one"
            + " text tile its stand-in carries, and the editor's close or the exit save writes that (AMS-A1)");

        assertNull(withAStandIn.getPointProperty(nowhereOnTheFirst, "priority"),
            "control: a setting on a square the loaded page does not have survived the capture, so the capture is"
            + " not pruning at all and the assertion above proves nothing");
    }

    /**
     * A station's allow-only mode goes into the railway with its list and comes back from it (FR-121): the build writes
     * `excludedLocsMode` on the station's point, and a capture of a railway whose point no longer carries it takes it
     * off the setup.
     *
     * MUTATION: leave the mode out of the settings a capture carries back, and this fails.
     */
    @Test
    public void testAnAllowOnlyModeGoesIntoTheRailwayAndComesBack() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Evening");

        TileKey station = new TileKey("main", 1, 1);

        session.setPointName(station, "FR121 Platform");
        session.setStation(station, true);
        session.setPointProperty(station, "excludedLocs", new org.json.JSONArray().put("FR121 Loco"));
        session.setPointProperty(station, org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE,
            org.traincontrol.automation.Point.ALLOW_ONLY_LISTED);

        org.json.JSONObject running = new org.json.JSONObject(session.buildConfiguration());

        org.json.JSONObject built = null;

        for (Object o : running.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            if (point.getString("name").startsWith("FR121 Platform")) built = point;
        }

        assertNotNull(built, "precondition: the station is not in the built railway: " + running);

        assertEquals(built.optString(org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE, null),
            org.traincontrol.automation.Point.ALLOW_ONLY_LISTED, "the station's allow-only mode did not reach the railway,"
            + " so its list keeps out the one locomotive it names: " + built);

        built.remove(org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE);

        session.captureFromLayout(running.toString());

        assertNull(session.getPointProperty(station, org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE), "a capture of"
            + " a railway whose station no longer lets in only its list left the setup reading it that way");
    }

    /**
     * Moving a train in the setup takes its tail off the square it left: placing the same train elsewhere must clear
     * `loc`, the facing, `arrivedFrom` and `arrivedAlong` together, as `placeLocomotive(tile, null)` does.
     */
    @Test
    public void testMovingATrainInTheSetupTakesItsTailOffTheSquareItLeft() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Evening");

        TileKey from = new TileKey("main", 1, 1);
        TileKey to = new TileKey("main", 4, 1);

        String name = "AMS C1 train";

        session.placeLocomotive(from, name);
        session.setPointProperty(from, "arrivedFrom", "WEST");
        session.setPointProperty(from, "arrivedAlong", new org.json.JSONArray().put("a rail"));

        assertNotNull(session.getPointProperty(from, "arrivedFrom"), "precondition: the arrival side did not take");
        assertNotNull(session.getPointProperty(from, "arrivedAlong"), "precondition: the road did not take");

        session.placeLocomotive(to, name);

        assertNull(session.getLocomotiveNameAt(from), "precondition: placing the train elsewhere did not move it");

        assertNull(session.getPointProperty(from, "arrivedFrom"),
            "the square the train was moved off still records which side it came in by - the next train placed there"
            + " inherits the last one's tail (AMS-C1, the sibling of IND9-A1)");

        assertNull(session.getPointProperty(from, "arrivedAlong"),
            "the square the train was moved off still records the road it came in along (AMS-C1, WK7-B1)");
    }

    /**
     * An entry of a page in play whose file does not load at a start is kept until the page is back: while the pages
     * are not all there, the build sets such an entry aside rather than hand it to the railway.
     *
     * MUTATION: leave an entry the build cannot build in the timetable while a page is not loaded, and this fails.
     */
    @Test
    public void testAnEntryOfAPageThatDidNotLoadIsKept() throws Exception
    {
        twoPagesWithATimetable("B6 in play");

        session.rebuild();
        session.save();

        // THE NEXT START, second's file not read; its load, and the fold of that railway
        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(twoPagesMade[0], aStandInFor("second", "2")));

        assertFalse(next.pagesSafeToJudge(), "precondition: a page not read is taken as read");

        org.json.JSONObject without = loadedFrom(next);

        next.captureFromLayout(without.toString());
        next.save();

        assertEquals(entriesKept(next), 2, "an entry of a page whose file did not load was lost to the load and the fold"
            + " (RSA8-B6): timetable " + legsOfTheTimetable(next));

        // THE START AFTER, both read
        AutonomySession after = new AutonomySession(layout);

        after.open(Arrays.asList(twoPagesMade));
        after.rebuild();

        assertEquals(timetableAfterALoad(after).length(), 2, "the entry did not come back once its page loaded again"
            + " (RSA8-B6)");
    }

    /** A blank page standing in for one whose file would not read, as `CS2File` makes it. */
    private static LayoutDiagram aStandInFor(String name, String id) throws Exception
    {
        LayoutDiagram standIn = new LayoutDiagram(name, 7, 3, null, null);

        standIn.markUnreadable();
        standIn.setPageId(id);

        return standIn;
    }

    /** An edit changes the graph immediately, not at some later save: a lagging derivation shows a wrong answer. */
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
     * A default is not written out as though it were a decision: plain track runs both ways and a switch runs base to
     * forks, so a stored "default" would freeze whichever applied that day.
     */
    @Test
    public void testSettingATileBackToItsDefaultStoresNothing() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        TileKey middle = new TileKey("main", 2, 1);
        RouteId route = session.getRoutes(middle).keySet().iterator().next();

        // THE SQUARE'S RUN, as a click sets it: a square of a run takes its run's direction (OB-361)
        TileKey leader = session.runLeaders().get(middle);

        session.setRunDirection(leader, route, Direction.TOWARD_A);
        assertNotNull(session.getStore().getTileDirection(middle, route));

        session.setRunDirection(leader, route, Direction.BOTH);
        assertNull(session.getStore().getTileDirection(middle, route),
            "back at the default, so nothing should be stored");
    }

    /**
     * Bulk editing sets what clicking one tile would have set.  The fixture is a single run of track, so closing EITHER
     * selected tile severs the only path and connectivity alone cannot tell "both tiles were set" from "one was".
     *
     * MUTATION: make the loop in AutonomySession.setDirection(Set, Direction) touch only
     * tiles.iterator().next(); the per-tile store check below finds a tile with no direction recorded.
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

        // Connectivity is satisfied by closing just one tile, so ask the store whether EVERY tile recorded the edit.
        for (TileKey tile : selection)
        {
            RouteId route = session.getRoutes(tile).keySet().iterator().next();

            assertEquals(session.getStore().getTileDirection(tile, route), Direction.NONE,
                "bulk edit skipped " + tile + " - a forty-tile selection would leave most of it untouched");
        }
    }

    /** Initializing twice makes a second configuration rather than quietly doing nothing ("add a configuration"). */
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

    /** And a name already taken is refused rather than silently replacing what is there. */
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
     * The routing rule travels with the configuration, not the UI preferences (Adam's ruling).  What makes it travel
     * is `captureFromLayout` copying every top-level key into "globals" and the builder putting them back, so this
     * checks both ends of that middle, since a break in either is invisible from the other.
     */
    @Test
    public void testTheRoutingRuleTravelsWithTheConfiguration() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        org.json.JSONObject running = new org.json.JSONObject();

        running.put("points", new org.json.JSONArray());
        running.put("edges", new org.json.JSONArray());
        running.put("pathPreference", "SHORTEST_LENGTH");

        session.captureFromLayout(running.toString());

        org.json.JSONObject globals =
            session.getStore().getConfiguration("Default").getJSONObject("globals");

        assertEquals(globals.optString("pathPreference"), "SHORTEST_LENGTH",
            "the rule has to be kept in the configuration, or it is a UI preference by another name");

        // and out the other side, which is the half the running railway actually reads
        String built = session.buildConfiguration();

        assertEquals(new org.json.JSONObject(built).optString("pathPreference"), "SHORTEST_LENGTH",
            "a configuration built from the setup must still name the rule - kept but not emitted "
            + "would load as the default and look like the setting being ignored");
    }

    /**
     * A page that is switched off cannot refuse the railway.  `placedLocomotives` read the configuration's points
     * straight out of the JSON, so a placement left behind on an excluded page raised the duplicate-locomotive ERROR.
     *
     * BOTH HALVES, because either alone is worthless: "an excluded page raises nothing" is satisfied by deleting the
     * check, so the same placement on a page still in play has to be reported from the same setup.
     */
    @Test
    public void testAPageThatIsOffCannotRefuseTheRailway() throws Exception
    {
        LayoutDiagram first = runOfTrack();

        LayoutDiagram other = new LayoutDiagram("other", 6, 4, null, null);

        // 11 is the first page's own sensor, so this page repeats it and is the one shut out
        other.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        other.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        other.setPageId("2");
        other.checkBounds();

        session.open(Arrays.asList(first, other));
        session.initialize("Default");

        assertEquals(session.excludeRepeatedSensorPages(), Arrays.asList("other"),
            "the fixture depends on the repeating page being the one excluded");

        // THE DUPLICATE SPANS THE EXCLUSION: one copy on a page in use, one on the page just switched off, written
        // straight into the configuration because excluding a page does not move the trains standing on it.
        org.json.JSONObject configuration = session.getStore().getConfiguration("Default");

        // A fresh configuration has no points object until something writes one.
        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        org.json.JSONObject points = configuration.getJSONObject("points");

        points.put(new TileKey("other", 1, 1).toString(),
            new org.json.JSONObject().put("loc", new org.json.JSONObject().put("name", "BR 218")));

        points.put(new TileKey(first.getName(), 1, 1).toString(),
            new org.json.JSONObject().put("loc", new org.json.JSONObject().put("name", "BR 218")));

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            assertFalse("other".equals(finding.getTile() == null ? null : finding.getTile().getPage()),
                "a page the user switched off raised " + finding.getSeverity() + " "
                + finding.getMessageKey() + " - excluding a page has to stop it having an opinion, "
                + "or the only way to fix the railway is on a page that is not in use");

            assertFalse(
                org.traincontrol.automationui.AutonomyChecks.DUPLICATE_LOCOMOTIVE.equals(
                    finding.getMessageKey()),
                "the only other copy of this locomotive is on a page that is switched off, so there "
                + "is no duplicate among the pages in play - and this finding refuses the whole setup");
        }

        // THE CONTROL: a duplicate wholly on pages in use must still refuse the setup.
        points.put(new TileKey(first.getName(), 3, 1).toString(),
            new org.json.JSONObject().put("loc", new org.json.JSONObject().put("name", "BR 218")));

        boolean refused = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.DUPLICATE_LOCOMOTIVE.equals(
                finding.getMessageKey()))
            {
                refused = true;
            }
        }

        assertTrue(refused,
            "a locomotive standing on two squares of a page that IS in play was not reported, so the "
            + "assertions above prove nothing - scoping the check to live pages must not turn it off");
    }

    /** What was decided comes back after a restart, and the graph derived from it matches. */
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

    /** The generated configuration is the ordinary format, so nothing downstream has to learn a new one. */
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
     * A configuration's per-point data (placements, termini, homes) rides into the generated file without touching what
     * the reduction decided.
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

        // Derived, not operational: a terminus sitting in a configuration is a leftover and must not reach the file.
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

            // A placement rides on exactly ONE copy of a split square; taking whichever was emitted last found a copy
            // with no "loc" on it.
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
     * What the running layout knew is lifted back into the active configuration, keyed by tile; vanished points are
     * dropped.
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

        // Terminus is the builder's answer, not the user's: read back, it would turn round every train that passed.
        assertFalse(extras.has("terminus"), "derived flags are not lifted off the running layout");

        // pace settings land in globals, and points/edges do not
        org.json.JSONObject globals = config.getJSONObject("globals");
        assertEquals(globals.getInt("minDelay"), 3);
        assertFalse(globals.has("points"));

        // and the round trip: what was captured is what the next build emits
        org.json.JSONObject rebuilt = new org.json.JSONObject(session.buildConfiguration());
        assertEquals(rebuilt.getInt("minDelay"), 3, "captured globals should feed the next build");

        boolean sawS88Eleven = false;

        for (Object o : rebuilt.getJSONArray("points"))
        {
            org.json.JSONObject p = (org.json.JSONObject) o;

            if (p.getInt("s88") == 11)
            {
                sawS88Eleven = true;

                assertEquals(p.getInt("maxTrainLength"), 7);
                assertEquals(p.getJSONObject("loc").getString("name"), "BR 218");
            }
        }

        // Floor: a rebuild that dropped s88 11 would leave the loop above with zero iterations and the test green.
        assertTrue(sawS88Eleven, "the rebuilt configuration should still contain the captured point");

        // capture by NAME lands in that configuration even when another is active, so a refused load cannot have
        // another configuration's state written over it at exit
        session.getStore().createConfiguration("Other", null);

        assertEquals(session.getStore().getActiveConfiguration(), "Default");

        session.captureFromLayout(running.toString(), "Other");

        assertTrue(session.getStore().getConfiguration("Other").has("points"),
            "the named configuration should receive the capture");
        assertEquals(session.getStore().getConfiguration("Default")
            .getJSONObject("globals").getInt("minDelay"), 3,
            "and the active one should keep what it already had");
    }

    /** Editing marks the setup unsaved, and saving clears it: that decides whether closing the editor has to ask. */
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

    /** Excluding a page takes its sensors out of the graph, which is what the flag is for. */
    @Test
    public void testExcludingAPageTakesItOutOfTheGraph() throws IOException
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        assertEquals(session.getReducer().getPoints().size(), 3);

        session.setPageExcluded("second", true);

        assertEquals(session.getReducer().getPoints().size(), 2,
            "an excluded page should contribute nothing");
    }


    /** The name the builder generated for the Point with this s88, as a running Layout would call it. */
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

    /**
     * A path that ends where it started is not offered, even from a configuration this index predates: the index
     * answers "different place" about two Points it has never heard of.  Drives `distinctDestinations` (what the two
     * menus call) directly, not just the primitives under it.
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

        org.traincontrol.automation.Point ghost = new org.traincontrol.automation.Point("Ghost", false, null);
        org.traincontrol.automation.Point elsewhere = new org.traincontrol.automation.Point("Elsewhere", false, null);

        List<org.traincontrol.automation.Edge> loopsBack =
            Arrays.asList(new org.traincontrol.automation.Edge(ghost, ghost));
        List<org.traincontrol.automation.Edge> genuineJourney =
            Arrays.asList(new org.traincontrol.automation.Edge(ghost, elsewhere));

        // MUTATION this catches: delete `if (sameSquare(from, to)) continue;` in StationIndex.distinctDestinations;
        // both loopsBack and genuineJourney would then survive.
        List<List<org.traincontrol.automation.Edge>> kept =
            index.distinctDestinations(Arrays.asList(loopsBack, genuineJourney));

        assertEquals(kept.size(), 1, "a path back to where it started must be dropped");
        assertEquals(kept.get(0), genuineJourney,
            "the path that survived should be the genuine journey, not the loop");
    }

    /**
     * A station reachable from two Points of the same square is offered once, not once per copy: the dedupe has to go
     * by SQUARE, because the copies answer to different names.
     */
    @Test
    public void testDistinctDestinationsKeepsOnlyOneCopyOfAStation() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);

        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        List<String> copies = index.pointNamesAt(station);

        assertTrue(copies.size() > 1,
            "precondition: the fixture must actually split, or the dedupe below proves nothing");

        org.traincontrol.automation.Point origin = new org.traincontrol.automation.Point("Origin", false, null);
        org.traincontrol.automation.Point firstCopy =
            new org.traincontrol.automation.Point(copies.get(0), false, null);
        org.traincontrol.automation.Point secondCopy =
            new org.traincontrol.automation.Point(copies.get(copies.size() - 1), false, null);

        List<org.traincontrol.automation.Edge> viaFirstCopy =
            Arrays.asList(new org.traincontrol.automation.Edge(origin, firstCopy));
        List<org.traincontrol.automation.Edge> viaSecondCopy =
            Arrays.asList(new org.traincontrol.automation.Edge(origin, secondCopy));

        // MUTATION this catches: key the "seen" set in distinctDestinations by `to` (the point name) instead of by
        // square, and the menu lists one station twice.
        List<List<org.traincontrol.automation.Edge>> kept =
            index.distinctDestinations(Arrays.asList(viaFirstCopy, viaSecondCopy));

        assertEquals(kept.size(), 1, "two Points of one platform must be offered as one destination");
        assertEquals(kept.get(0), viaFirstCopy, "the first copy reached should be the one kept");

        // Control: a genuinely different destination is not swallowed by the same dedupe.
        org.traincontrol.automation.Point somewhereElse =
            new org.traincontrol.automation.Point("Somewhere else", false, null);
        List<org.traincontrol.automation.Edge> aThirdPath =
            Arrays.asList(new org.traincontrol.automation.Edge(origin, somewhereElse));

        assertEquals(index.distinctDestinations(Arrays.asList(viaFirstCopy, aThirdPath)).size(), 2,
            "two genuinely different destinations must both survive");
    }

    /** The restriction survives being written out and read back. */
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
     * The translation between squares and Points is derived once and agrees with itself: copies built by separate
     * AutonomyBuilders were configured differently, so one split a square and another did not.
     */
    @Test
    public void testTheIndexRoundTripsSquaresAndPoints() throws Exception
    {
        // A square that actually SPLITS: on a one-Point station the closing check reduces to sameSquare(x, x).
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
     * Renaming a square is visible to the index immediately: a cache that outlives what it describes stops stations
     * filling in.
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
     * A save that declines to tidy up SAYS SO, and names the pages.  An EMPTY reconciliation and a REFUSED one used to
     * be the same object, and five of the six doors that call `save()` threw the result away, so nobody was told.
     *
     * MUTATION: having `save()` return a plain `new Reconciliation()` for the incomplete case fails this test.
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

        // and now with the second page missing
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
     * A page that did not load keeps its setup (OB-068).  `CS2File.parseLayout` skips a page whose file will not parse
     * or is not there, quietly and on purpose (an unhydrated OneDrive placeholder is enough), and `save()` used to
     * reconcile against the pages that DID load, pruning everything on the missing one.
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

        // the second page fails to load this time
        AutonomySession partial = new AutonomySession(layout);

        partial.open(Arrays.asList(runOfTrack()));

        // Its entries are in memory under RAW ID keys, since pageOf has no name to resolve id 2 to; that is why they
        // look like squares that do not exist, and exactly the state in which they must not be deleted.
        assertFalse(partial.getStore().pagesNotLoaded(
                java.util.Collections.singletonList("main")).isEmpty(),
            "the store cannot tell that a page it knows about is missing, so the guard below has "
            + "nothing to act on and this test proves nothing");

        partial.save();

        // and the page that never loaded still has everything
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
     * The model, and the doors that ask about the railway off the event thread, ask for it once.  `hasAutoLayout()`
     * then `getAutoLayout()` is two takes of the model's lock, and Unload can clear the model between them;
     * `getAutoLayoutIfLoaded()` asks once.  Read from the source, because the moment between the two calls cannot be
     * held open from here; it knows the model's file as a whole and the three methods it names.
     *
     * MUTATION: put back any one of the pairs, and this fails.
     */
    @Test
    public void testTheModelAndTheDoorsOffTheEventThreadAskForTheRailwayOnce() throws Exception
    {
        // The model's own file, found by the constructor it is built with
        String model = SourceOf.fileDeclaring("public MarklinControlStation(NetworkProxy network, View view,"
            + " boolean autoPowerOn, boolean debug, boolean simulate)");

        java.util.List<String> pairs = new java.util.ArrayList<>();

        for (String line : model.split("\n"))
        {
            String code = line.trim();

            if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) continue;

            if (code.contains("hasAutoLayout()") && !code.contains("boolean hasAutoLayout()")) pairs.add(code);
        }

        assertTrue(pairs.isEmpty(), "the model asks whether there is a railway and then asks for it - Unload between the"
            + " two leaves it building an empty one (RLV12-C5): " + pairs);

        String[][] doors = {
            {"TrainControlUI", "boolean isAutonomyBusy()"},
            {"LayoutRightclickAutonomyMenu", "private static PathOptions gatherPathOptions("},
            {"LayoutLabel", "private static boolean aboutToClearProtection("},
            // Graceful Stop's worker waits out the coast-down, while Unload is allowed
            {"TrainControlUI", "private void gracefulStopActionPerformed("}};

        for (String[] door : doors)
        {
            String body = SourceOf.declaration(door[1]);

            assertFalse(body.contains("hasAutoLayout()") && body.contains("getAutoLayout()"), door[1] + " asks whether"
                + " there is a railway and then asks for it, off the event thread (RLV12-C5)");
        }
    }

    /**
     * The diagram's path gather asks for the railway once, and asks every question of that one: asking the model again
     * per path let Unload build an empty railway in between, or a load put the new railway's questions to the old
     * Points.
     *
     * MUTATION: let the gather ask the model for the railway again, and this fails.
     */
    @Test
    public void testTheDiagramsGatherAsksForTheRailwayOnce() throws Exception
    {
        String body = SourceOf.declaration("private static PathOptions gatherPathOptions(");

        assertFalse(body.contains("getAutoLayout()"), "the diagram's path gather asks the model for the railway again on"
            + " its worker, where Unload can leave it building an empty one (RSA2-C5)");
    }
}
