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
import org.traincontrol.automationui.TileAnnotation;
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

    /**
     * A stand-in for a page that would not read is not a page autonomy may judge (FV3-A1, FV3-A3).
     *
     * **`CS2File` substitutes a blank page when a file will not parse**, so that a link tile after it
     * still resolves to the right page - Adam's ruling of 2026-09-10, and the reason is that a page
     * which simply vanished re-aimed every arrow after it (NSV-B3).
     *
     * The stand-in carries the missing page's NAME and ID, and both are how this session decides whether
     * it is looking at the whole railway. So it made things worse than the absence it replaced:
     * `pagesNotLoaded` compares names and came back empty, `pagesSafeToJudge` went true, the entries
     * held out of memory for a page nobody can see were released, and the next `save()` reconciled the
     * real page's whole setup against the one text tile the stand-in carries. That is MT-135 - *"all
     * stations are gone"* - reached by a third route.
     *
     * **Three loops in this class build that name set, and a repair reaching two of them is worse than
     * one reaching none** (FV3-A3): the prune is then declined while `Reconciliation.declined` with an
     * empty absent-list reports `wasDeclined()` as false, so the save refuses to judge the page and
     * tells every caller it did not. This asserts the ANSWER rather than the loops, so it holds however
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

        // Now the second page's file will not read, so CS2File hands over a blank stand-in for it -
        // same name, same id, one text tile.
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

        // THE CONTROL: with both pages really present, the session does judge - so the refusal above is
        // about the stand-in and not about this having stopped answering yes.
        assertAWholeLayoutIsJudged();
    }

    /**
     * The control for `testAPageThatWouldNotReadIsNotJudged`: a layout with every page loaded is safe
     * to judge.
     */
    private void assertAWholeLayoutIsJudged() throws IOException
    {
        AutonomySession whole = new AutonomySession(layout);

        whole.open(Arrays.asList(runOfTrack(), secondPage()));

        assertTrue(whole.pagesSafeToJudge(),
            "control: a layout with every page genuinely loaded is not safe to judge either, so the "
            + "assertion above would pass with the stand-in rule removed entirely");
    }

    /**
     * A capture does not judge a page whose file would not read either (AMS-A1).
     *
     * `captureFromLayout` folds the running layout back into the configuration and prunes every entry whose
     * square is not in the graph, on the pages "in play".  That set was every page not excluded - so a blank
     * stand-in for a page that would not read counted, the one text tile it carries was all that was "still
     * there", and every placement, home, priority and exclusion on the real page was removed and written.  It
     * is the same loss FV3-A1 closed for `save()`, by a door that fix did not reach: the capture runs when the
     * autonomy editor opens and closes, at the exit save, and before every configuration load.
     *
     * THE CONTROL is on the page that did read: a setting on a square that is not there is pruned, so the
     * first assertion is about the stand-in and not about a capture that has stopped pruning.
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
     * Moving a train in the setup takes its tail off the square it left (AMS-C1).
     *
     * `placeLocomotive(tile, null)` and `clearEveryPlacement` clear `loc`, the facing, `arrivedFrom` and
     * `arrivedAlong` together (IND9-A1, WK7-B1).  The third door - placing the same train somewhere else, which
     * sweeps it off every other square - cleared the first two only.
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
     *
     * The fixture is a single run of track, so closing EITHER of the two selected tiles already severs
     * the only path - checking connectivity alone cannot tell "both tiles were set" from "one was".
     * MUTATION this catches: replace the loop in AutonomySession.setDirection(Set&lt;TileKey&gt;,
     * Direction) (AutonomySession.java:3036-3050) with a single call touching only
     * tiles.iterator().next() - the run would still read severed, but the per-tile store check below
     * would find one of the two tiles never recorded a direction at all.
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

        // The connectivity check above is satisfied by closing just one of the two tiles, so ask the
        // store directly whether EVERY tile of the selection actually recorded the edit.
        for (TileKey tile : selection)
        {
            RouteId route = session.getRoutes(tile).keySet().iterator().next();

            assertEquals(session.getStore().getTileDirection(tile, route), Direction.NONE,
                "bulk edit skipped " + tile + " - a forty-tile selection would leave most of it untouched");
        }
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
     * The routing rule travels with the configuration (Adam).
     *
     * "Let's update it so that the preference is written into the autonomy config, not the UI
     * preferences.  that way it travels with the config, not the UI."
     *
     * It used to be a static on Layout, loaded from a UI preference by the window's menu builder - so
     * it belonged to the running application rather than to the railway, one value shared by every
     * configuration, and anything that ran autonomy without opening that window used the default no
     * matter what had been chosen.
     *
     * THE MIDDLE IS WHAT THIS TESTS. Writing it in toJSON and reading it in fromJSON are two ends;
     * what makes it travel is captureFromLayout copying every top-level key into "globals" and the
     * builder putting them back. That road already carried the timetable, which is a reason to expect
     * it to work and not a reason to assert that it does.
     *
     * Checked at both ends of that middle, since a break in either is invisible from the other.
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
     * Every clashing sensor is reported, and a page that clashes still poisons nothing (MT-223, LE2-B8).
     *
     * TWO RULES THAT PULL IN OPPOSITE DIRECTIONS, which is why they are tested together.
     *
     * Adam: "I only see 2015 reported as a conflict, but there are many other sensors that conflict
     * between page 1 and page 3, since page 3 is basically a zoomed view of page 1.  all conflicting
     * sensors should be listed." A page that duplicates forty sensors is forty things to fix.
     *
     * And LE2-B8: a page that repeats anything must contribute NOTHING to what counts as seen, or a
     * third page sharing one of its other sensors is reported too - blamed against the page that
     * should itself have been switched off.
     *
     * Satisfying one by breaking the other is easy in both directions, so the fixture does both at
     * once: "repeat" clashes on TWO sensors and must produce two findings, and "third" shares a sensor
     * that only "repeat" holds and must produce none.
     */
    @Test
    public void testEveryClashingSensorIsReportedAndOnlyTheRepeatingPageIs() throws Exception
    {
        LayoutDiagram first = new LayoutDiagram("main", 8, 4, null, null);

        first.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        first.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        first.setPageId("1");
        first.checkBounds();

        // a zoomed copy: it repeats BOTH of main's sensors, and carries one of its own
        LayoutDiagram repeat = new LayoutDiagram("repeat", 8, 4, null, null);

        repeat.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        repeat.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        repeat.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        repeat.setPageId("2");
        repeat.checkBounds();

        // and a page sharing only the sensor that "repeat" alone holds
        LayoutDiagram third = new LayoutDiagram("third", 8, 4, null, null);

        third.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        third.setPageId("3");
        third.checkBounds();

        session.open(Arrays.asList(first, repeat, third));
        session.initialize("Default");

        java.util.List<String> clashes = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.DUPLICATE_SENSOR_PAGE.equals(
                finding.getMessageKey()))
            {
                clashes.add(finding.getSubject() + " @ " + finding.getTile());
            }
        }

        assertEquals(clashes.size(), 2,
            "both of the repeated sensors have to be reported - a page that duplicates forty of them "
            + "is forty things to fix, and reporting one leaves the other thirty-nine invisible "
            + "(MT-223).  Found: " + clashes);

        for (String clash : clashes)
        {
            assertTrue(clash.contains("repeat"),
                "the finding must name and point at the REPEATING page, not the one that had the "
                + "sensor first: " + clash);
        }

        // and the page that only shares what "repeat" alone brought is innocent (LE2-B8)
        for (String clash : clashes)
        {
            assertFalse(clash.contains("third"),
                "a page sharing a sensor that only the REPEATING page holds was reported, so that "
                + "page is still feeding what it holds into the seen set (LE2-B8): " + clash);
        }
    }

    /**
     * A page that is switched off cannot refuse the railway (Adam).
     *
     * "the first few times it told me there was a conflict with page 2, and the changes didn't save,
     * even though page 2 was off."
     *
     * `placedLocomotives` read the configuration's points straight out of the JSON, so it handed the
     * checks placements on pages the user had excluded - the only reader in that class not going
     * through the reducer, which drops excluded pages for everybody else. The duplicate-locomotive
     * finding it feeds is an ERROR that says autonomy "refuses the whole setup", so a placement left
     * behind on a switched-off page could refuse the railway and send the user to that page to fix it.
     *
     * BOTH HALVES, because either alone is worthless. "An excluded page raises nothing" is satisfied
     * by a check that never fires - including by deleting the check - so the same placement on a page
     * still in play has to be reported, in the same test, from the same setup.
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

        // THE DUPLICATE SPANS THE EXCLUSION: one copy on a page in use, one on the page just
        // switched off.  Written straight into the configuration because that is where a stale
        // placement lives - excluding a page does not move the trains standing on it.
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

        // THE CONTROL: a duplicate wholly on pages in use must still refuse the setup.  Without it,
        // the assertions above are equally satisfied by deleting the check.
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
     * A locomotive is pointing the same way after a save and a reopen as it was before.
     *
     * Adam, 2026-09-06, asking the question that should have been asked in the morning: **"do you have
     * tests for locomotive direction staying put between saves?"**  The answer was no - not one test in
     * the suite both persisted a setup and read a facing back, which is measurable and was measured.
     *
     * **That is the gap the whole day came out of.**  The reported symptom was a train's direction
     * changing by itself, and the mechanism turned out to be that a facing was never RECORDED at one
     * door, so `placementCopy` fell through to copy 0 and the next `captureFromLayout` wrote that
     * arbitrary side back as though somebody had chosen it.  Every part of that is a round trip, and
     * nothing was checking round trips.  Five fixes were attempted against a defect whose simplest
     * possible test did not exist.
     *
     * Both hops are checked, because they fail for different reasons.  The FILE hop is the store
     * writing and reading `facing`; the BUILD hop is `buildConfiguration` emitting it and
     * `captureFromLayout` reading it back, which is the one that silently rewrote the value.
     *
     * @throws IOException on a failure to save
     */
    @Test
    public void testALocomotivesDirectionSurvivesASaveAndReopen() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        session.placeLocomotive(sensor, "Test Loc");

        // A side the square can actually hold, so this is a direction and not a stray value.
        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(sensor);

        assertFalse(choices.isEmpty(), "the fixture square offers no direction at all to record");

        org.traincontrol.automationui.TilePorts.Side chosen = choices.get(0);

        session.setFacing(sensor, chosen);

        assertEquals(session.getFacing(sensor), chosen, "control: the facing did not even go in");

        session.save();

        // ACROSS THE FILE.
        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(runOfTrack()));

        assertEquals(reopened.getFacing(sensor), chosen,
            "the locomotive is pointing a different way after a reload. A facing that does not survive"
            + " a save is worse than one that was never recorded: the diagram shows a direction the"
            + " operator chose until the next load, and something else afterwards");

        assertTrue(String.valueOf(reopened.getPointProperty(sensor, "loc")).contains("Test Loc"),
            "the locomotive itself did not survive the reload, so the facing above proved nothing");
    }

    /**
     * A home remembers which way its train was facing when it was set, and the build puts it on that copy (OB-282).
     *
     * Adam, 2026-09-23: *"yes, it should accomplish the facing"*, and asked when the facing is taken: *"Direction it is
     * facing when home is set."*  A copy is a facing, so the home goes on the copy the train was facing in, marked so
     * that Return Home brings it back to that copy rather than to any copy of the square - and it survives a build
     * and a capture.
     *
     * MUTATION: `writeHome` not recording the facing, or the build not marking the copy, fails this.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAHomeRemembersTheWayItsTrainWasFacing() throws Exception
    {
        session.open(Arrays.asList(throughStationPage()));
        session.initialize("Default");

        // THE MIDDLE SENSOR, which trains reach from either end - two copies, facing opposite ways.
        TileKey sensor = new TileKey("main", 3, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.placeLocomotive(sensor, "Test Loc");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices = session.facingChoices(sensor);

        assertTrue(choices.size() > 1, "precondition: the square holds one facing, so which one the home keeps is no"
            + " choice: " + choices);

        // THE SECOND facing, so a build that put the home on copy 0 as before cannot pass by accident.
        org.traincontrol.automationui.TilePorts.Side chosen = choices.get(choices.size() - 1);

        session.setFacing(sensor, chosen);
        session.setHome(sensor, "Test Loc");

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            chosen.name(), "the home was set with the train facing " + chosen + " and does not remember it");

        String built = session.buildConfiguration();

        String homedOn = null;

        org.json.JSONArray points = new org.json.JSONObject(built).getJSONArray("points");

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            if (!"Test Loc".equals(point.optString("home", null))) continue;

            assertTrue(point.optBoolean(org.traincontrol.automationui.AutonomyBuilder.HOME_FACING_FIXED, false),
                "the home is emitted on " + point.optString("name") + " without saying it is held to that copy");

            homedOn = point.getString("name");
        }

        assertNotNull(homedOn, "the build carries no home at all");

        assertEquals(session.facingsFor(sensor).get(homedOn), chosen,
            "the home was set facing " + chosen + " and the build put it on " + homedOn + ", which faces "
            + session.facingsFor(sensor).get(homedOn));

        // AND BACK THROUGH A CAPTURE, which is what the running railway does whenever it is asked to remember.
        session.captureFromLayout(built);

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            chosen.name(), "a build and a capture changed the facing the home was set with");

        // AND A HOME SET ON THE RUNNING DIAGRAM, on the OTHER copy: the running layout holds it to that copy, and the
        // capture is what tells the setup.  Made here by moving the home in the built configuration, which is what the
        // running layout's own `toJSON` would write after `setHomeLocomotive` on that copy.
        org.json.JSONObject running = new org.json.JSONObject(built);

        org.traincontrol.automationui.TilePorts.Side other = null;

        for (int at = 0; at < running.getJSONArray("points").length(); at++)
        {
            org.json.JSONObject point = running.getJSONArray("points").getJSONObject(at);

            org.traincontrol.automationui.TilePorts.Side faces = session.facingsFor(sensor).get(point.optString("name"));

            if (faces == null) continue;

            if (faces == chosen)
            {
                point.remove("home");
                point.remove(org.traincontrol.automationui.AutonomyBuilder.HOME_FACING_FIXED);
            }
            else
            {
                point.put("home", "Test Loc");
                point.put(org.traincontrol.automationui.AutonomyBuilder.HOME_FACING_FIXED, true);

                other = faces;
            }
        }

        assertNotNull(other, "precondition: the square has no copy facing another way to move the home to");

        session.captureFromLayout(running.toString());

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            other.name(), "a home set on the running diagram facing " + other + " was captured without its facing - the"
            + " next build puts it back on the copy facing " + chosen);
    }

    /**
     * A home given to a train standing elsewhere takes the facing asked for - and never one no train can stand in
     * (OB-282).
     *
     * Adam, 2026-09-23: *"prompt the user for the direction"*, and *"we shouldn't allow an impossible facing to be
     * saved."*
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAHomeForATrainElsewhereTakesTheFacingAskedAndNoImpossibleOne() throws Exception
    {
        session.open(Arrays.asList(throughStationPage()));
        session.initialize("Default");

        // THE MIDDLE SENSOR, which trains reach from either end - two copies, facing opposite ways.
        TileKey sensor = new TileKey("main", 3, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        java.util.Set<org.traincontrol.automationui.TilePorts.Side> canHold = session.homeFacingsFor(sensor);

        assertTrue(canHold.size() > 1, "precondition: the square offers one facing to be homed in: " + canHold);

        org.traincontrol.automationui.TilePorts.Side asked = new java.util.ArrayList<>(canHold).get(canHold.size() - 1);

        session.setHome(sensor, "Elsewhere Loc", asked);

        assertEquals(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            asked.name(), "the facing asked for a home was not kept");

        // A FACING NO COPY TRAINS MAY ARRIVE AT HOLDS: on a straight east-west run, north.
        org.traincontrol.automationui.TilePorts.Side impossible = null;

        for (org.traincontrol.automationui.TilePorts.Side side : org.traincontrol.automationui.TilePorts.Side.values())
        {
            if (!canHold.contains(side)) impossible = side;
        }

        assertNotNull(impossible, "precondition: every side is a facing here, so there is no impossible one to refuse");

        session.setHome(sensor, "Elsewhere Loc", impossible);

        assertNull(session.getPointProperty(sensor, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
            "a home was saved facing " + impossible + ", which no copy of this square a train may arrive at holds");
    }

    /**
     * And across a build and a capture, which is the hop that actually rewrote it.
     *
     * `captureFromLayout` takes the running layout back into the setup, and a train standing on a copy
     * is what tells it which way that train faces.  So a value that goes out through
     * `buildConfiguration` and comes back changed is a direction nobody chose - and until today,
     * nothing asked whether it came back the same.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testADirectionSurvivesABuildAndACapture() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.placeLocomotive(sensor, "Test Loc");

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(sensor);

        assertFalse(choices.isEmpty(), "the fixture square offers no direction to record");

        org.traincontrol.automationui.TilePorts.Side chosen = choices.get(0);

        session.setFacing(sensor, chosen);

        // Out through the build and back through the capture, which is the round trip the running
        // railway makes every time it is asked to remember where its trains are.
        String built = session.buildConfiguration();

        assertTrue(built.contains("Test Loc"),
            "the build does not carry the locomotive, so the capture below has nothing to say about"
            + " it and this test would pass on an empty railway");

        session.captureFromLayout(built);

        assertEquals(session.getFacing(sensor), chosen,
            "a build and a capture changed which way the train is pointing. That is the mechanism"
            + " behind the defect Adam reported four times: a direction goes out, comes back"
            + " different, and is presented as one somebody chose");
    }

    /**
     * Taking a train off a square takes its arrival side with it (IND9-A1).
     *
     * `arrivedFrom` describes the TRAIN that was standing here, not the square - the same thing the
     * comment beside the facing in `placeLocomotive` says about the facing, three lines from where this
     * was missed. Both doors that empty a square cleared `loc` and the facing and left the arrival side
     * behind, and `parseAuto` reads it straight back onto whatever is placed there next.
     *
     * **What that does on the railway.** The stale side is where the PREVIOUS train’s tail lay, so the
     * walk that blocks track behind a standing train follows it for the new one: track behind train B
     * is greyed and refused on the strength of where train A came in. It is silent - nothing reports a
     * side it did not ask about - and it survives a save.
     *
     * It is also unreachable from the menu, which now offers only the four sides and no way to say "not
     * known" (Adam, 2026-09-07). Removing that option was right and it makes this defect permanent
     * until the root cause is fixed, which is here: the side leaves with the train.
     *
     * Both doors are asserted. `clearEveryPlacement` is a separate loop that mirrors this one, and the
     * two have drifted before - it is the reason `placeLocomotive` carries a comment telling the reader
     * they must agree.
     *
     * @throws IOException on a failure to save
     */
    @Test
    public void testAnArrivalSideDoesNotOutliveItsTrain() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        // TRAIN A ARRIVES FROM THE WEST.
        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "W");

        assertEquals(session.getArrivedFrom(sensor), "W", "control: the side did not go in");

        // AND IS TAKEN OFF AGAIN - a cut, or a right-click clear.
        session.placeLocomotive(sensor, null);

        assertNull(session.getArrivedFrom(sensor),
            "the square still says a train arrived from the west after the train was taken off it."
            + " The next locomotive placed here inherits that tail, and the track behind it is"
            + " blocked on the strength of where a different train came in (IND9-A1)");

        // AND THE OTHER DOOR, which is a separate loop over every square.
        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "E");

        assertEquals(session.getArrivedFrom(sensor), "E", "control: the second side did not go in");

        assertTrue(session.clearEveryPlacement() > 0,
            "clearEveryPlacement cleared nothing, so the assertion below is about a square it never"
            + " visited");

        assertNull(session.getArrivedFrom(sensor),
            "clearEveryPlacement leaves the arrival side behind. It mirrors placeLocomotive and the"
            + " two must agree about what taking a train off a square means (IND9-A1)");
    }

    /**
     * A NEW occupant does not inherit the old one's tail (VAL9-B5).
     *
     * The third branch of the same rule, and the one nothing exercised. Emptying a square is covered
     * above; handing it straight from one train to another is the case where the stale value is least
     * visible and most wrong, because the square is never empty in between and nothing looks wrong on
     * the diagram.
     *
     * **Both assignment doors now depend on this branch**, since neither asks the arrival side:
     * `GraphLocAssign.commitAndRecord` places through here and relies on the clear happening. So the
     * one path with no test was the one two doors were built on.
     *
     * `Point.setLocomotive` does exactly this on the running layout, for the same reason - the train
     * arriving did not arrive the way the train leaving did.
     *
     * The same locomotive re-placed is asserted first, because it is NOT a change of occupant and a
     * clear there would throw away a side that is still true - doors write a placement back unchanged
     * all the time.
     *
     * MUTATION: dropping the occupant-change clear in `placeLocomotive` fails the last assertion;
     * clearing unconditionally fails the middle one.
     *
     * @throws IOException on a failure to build
     */
    @Test
    public void testANewOccupantDoesNotInheritTheOldOnesTail() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);

        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "W");

        assertEquals(session.getArrivedFrom(sensor), "W", "control: the side did not go in");

        session.placeLocomotive(sensor, "Test Loc");

        assertEquals(session.getArrivedFrom(sensor), "W",
            "re-placing the same locomotive threw away an arrival side that is still true. Every door"
            + " that writes a placement back unchanged would silently clear it");

        session.placeLocomotive(sensor, "Other Loc");

        assertNull(session.getArrivedFrom(sensor),
            "the new locomotive inherited where the old one came in from. Neither assignment door asks"
            + " the arrival side, so this clear is the only thing standing between a handover and a"
            + " tail that blocks the wrong rail (VAL9-B5)");
    }
    /**
     * The arrival side reaches the running layout, and comes back from a save.
     *
     * `VAL8-A2` reported that `arrivedFrom` never crosses between the setup and the railway - that the
     * builder does not emit it, so an answer the operator gives blocks nothing.  It is not in the
     * builder's skip list and not in DERIVED, so by reading it should pass through as an extra; that
     * is exactly the kind of reading that has been wrong twice this week, so it is measured here
     * instead.
     *
     * Both hops, because they fail for different reasons: the FILE hop is the store, and the BUILD hop
     * is what `parseAuto` is handed.  A property that survives a save and never reaches the railway is
     * a setting that does nothing, which is worse than one that is not offered.
     *
     * @throws IOException on a failure to save
     */
    @Test
    public void testTheArrivalSideReachesTheRailwayAndSurvivesASave() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Default");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Platform 1");
        session.setStation(sensor, true);
        session.placeLocomotive(sensor, "Test Loc");
        session.setArrivedFrom(sensor, "W");

        assertEquals(session.getArrivedFrom(sensor), "W", "control: it did not go in");

        // THE BUILD, which is what the railway is made from.
        String built = session.buildConfiguration();

        assertTrue(built.contains("arrivedFrom"),
            "the built configuration carries no arrival side, so an answer the operator gives never"
            + " reaches the railway and blocks nothing (VAL8-A2)");

        assertTrue(built.contains("\"arrivedFrom\": \"W\"") || built.contains("\"arrivedFrom\":\"W\""),
            "the arrival side is emitted but not as the side that was recorded: " 
            + built.substring(Math.max(0, built.indexOf("arrivedFrom") - 40),
                Math.min(built.length(), built.indexOf("arrivedFrom") + 40)));

        // AND THE FILE.
        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(runOfTrack()));

        assertEquals(reopened.getArrivedFrom(sensor), "W",
            "the arrival side did not survive a save and reload, so the track a train is lying across"
            + " is forgotten every time the setup is loaded");
    }

    /**
     * A direction change never flips the facing of a square the train has LEFT (REG7-A2).
     *
     * **The mechanism, which is sharper than "it might double-follow".**  `flipFacing` builds a list
     * of candidate squares: the ones the running layout says hold this train, then the ones the SETUP
     * says do.  The loop skips any square it cannot decide - a square with no recorded facing is a
     * `continue`, not a stop.
     *
     * After a run the arrival square frequently has no recorded facing yet, so the loop moved past it
     * and reached the next candidate: the square the train set off from, which the setup still names
     * until the next `captureFromLayout` and which does have a facing. That platform - now empty -
     * had its direction flipped for a train that is not standing on it, and the log said a direction
     * had been followed.
     *
     * Not an exotic interleaving: it is the ordinary state of things between a run ending and the
     * editor next being opened.
     *
     * The rule now is that the setup only gets a vote when the railway has no opinion. `DIR-C3` is
     * untouched - its case is a locomotive recorded on two SETUP squares, which is still walked in
     * full when the running layout does not know.
     *
     * @throws IOException on a failure to save
     */
    @Test
    public void testADirectionChangeDoesNotFlipTheSquareTheTrainLeft() throws IOException
    {
        // The same fixture testTheFlipMovesTheLocomotiveOnTheLayoutToo uses: flipFacing declines any
        // square that does not offer exactly two facings, and runOfTrack offers none.
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("Facing", null);
        session.getStore().setActiveConfiguration("Facing");

        final TileKey departed = new TileKey("main", 4, 1);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(departed);

        assertEquals(choices.size(), 2,
            "precondition: the square must offer two facings or flipFacing declines by design");

        session.setPointName(departed, "Platform 1");
        session.setStation(departed, true);
        session.placeLocomotive(departed, "Test Loc");

        session.setFacing(departed, choices.get(0));

        // A RAILWAY THAT KNOWS NOTHING still lets the setup answer - DIR-C3, and the half of the old
        // behaviour that must survive.  A train recorded in the setup and not yet running is the
        // ordinary state before anything has moved.
        org.traincontrol.automation.Layout knowsNothing =
            new org.traincontrol.automation.Layout(null);

        session.flipFacing("Test Loc", knowsNothing);

        assertEquals(session.getFacing(departed), choices.get(1),
            "a railway with no opinion about this train stopped the setup answering at all, which is"
            + " DIR-C3 undone: a locomotive recorded on a square it has not left still gets a follow");

        // AND THE FIX ITSELF, which needs a railway that DOES know the train - a state this fixture
        // cannot build, because the candidate has to come back from the station index.  Asserted as
        // the rule instead: setup squares are gathered ONLY when the running layout offered none.
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automationui/AutonomySession.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int flip = source.indexOf("public TileKey flipFacing");

        assertTrue(flip > 0, "flipFacing has been renamed and this check guards nothing");

        int gathers = source.indexOf("for (Map.Entry<TileKey, String> placed : placedLocomotives()",
            flip);

        int guarded = source.indexOf("if (candidates.isEmpty())", flip);

        assertTrue(guarded > 0 && guarded < gathers,
            "the setup's squares are appended to the running layout's answer again. The loop below"
            + " SKIPS a square it cannot decide rather than stopping, so an arrival square with no"
            + " recorded facing sends it on to the square the train LEFT - and that platform, now"
            + " empty, has its direction flipped for a train standing somewhere else (REG7-A2)");
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

        // Floor: without this, a rebuild that dropped s88 11 entirely (or renumbered it) would leave
        // the loop above running zero matching iterations and the test green regardless.
        assertTrue(sawS88Eleven, "the rebuilt configuration should still contain the captured point");

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
     * The editor warns about an arrival a train could never leave, and one nothing could reach.
     *
     * Adam, 2026-09-02: **"we need a warning for instances like the previous version of this."**  He
     * had just built a parking spur whose reverse copy came out with two ways in and none out -
     * somewhere autonomy may send a train that no train can ever depart from.  BottomMainB had the
     * same shape earlier the same day, and it cost an evening: Return Home was chased for a fault that
     * was in the diagram all along.
     *
     * **Every other check in that file asks about the SQUARE, and that is exactly why none of them
     * caught it.**  A square is emitted as one Point per side a train can arrive by, and the copies do
     * not share their edges - so a square can be perfectly reachable while one of its arrivals is a
     * trap.  A rule about copies cannot be enforced by looking at squares.
     *
     * So the fixture is a square with TWO arrivals, healthy from one side and stuck from the other.
     * Barring the far section of the run leaves the middle station's eastward arrival with nothing to
     * go on to, while its westward arrival is untouched - which is what keeps the square healthy and
     * the square-level checks quiet.
     *
     * **Which of TOWARD_A and TOWARD_B points which way is the tile's business, not this test's**, so
     * both are applied and the assertion is that exactly one of them traps the arrival.  True
     * whichever way round they are, and false if nothing is watching.
     *
     * The LAST assertion is the exception, and it is why the trapping direction is put back before it:
     * it asks `check()` about the current state rather than comparing the two answers (V33-C5).
     *
     * NOT COVERED HERE: the no-way-IN half of the same check.  It could not be provoked, and the
     * reason is worth writing down rather than rediscovering.  A copy is emitted per side a train can
     * ARRIVE by, so barring the approach does not leave a copy nothing can reach - it leaves no copy
     * at all, and there is nothing to report.  Before these checks were narrowed to healthy squares it
     * fired ten times on Adam's railway, and every one of those was a square nothing could reach from
     * any side - which `checkStationUnreachable` already warns about.  It is kept for symmetry with
     * its twin and because a split square could in principle have one unreachable arrival, but no
     * fixture here demonstrates it and it fires on no railway we have.
     *
     * MUTATION: asking `reducer.getPoints()` - the squares - rather than the built graph finds
     * neither, because the square is on a run that is perfectly connected.
     */
    @Test
    public void testTheEditorWarnsAboutACopyWithNoWayOutOrIn() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Copies", null);
        session.getStore().setActiveConfiguration("Copies");

        TileKey near = new TileKey("main", 1, 1);
        TileKey middle = new TileKey("main", 4, 1);
        TileKey stub = new TileKey("main", 7, 1);

        session.setStation(near, true);
        session.setPointName(near, "NearEnd");

        session.setStation(middle, true);
        session.setPointName(middle, "Middle");

        session.setStation(stub, true);
        session.setPointName(stub, "FarEnd");

        // Both ends may turn, which is what a railway does with a stub and what makes this a control:
        // an unmarked dead end traps the train that arrives at it, so without this the fixture reports
        // two trapped arrivals before anything has been done to it.
        session.setPointFlag(near, AutonomyBuilder.CAN_REVERSE, true);
        session.setPointFlag(stub, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        assertTrue(session.destinationCopiesWithNoWayOut().isEmpty(),
            "an ordinary two-way run reports a station a train cannot leave: "
            + session.destinationCopiesWithNoWayOut());

        assertTrue(session.destinationCopiesWithNoWayIn().isEmpty(),
            "an ordinary two-way run reports a station nothing can reach: "
            + session.destinationCopiesWithNoWayIn());

        // ONE WAY ALONG THE FAR SECTION ONLY, so the middle station keeps its healthy arrival.
        Set<TileKey> far = new LinkedHashSet<>();

        for (int x = 5; x <= 7; x++)
        {
            far.add(new TileKey("main", x, 1));
        }

        session.setDirection(far, Direction.TOWARD_A);
        session.rebuild();

        String outA = String.valueOf(session.destinationCopiesWithNoWayOut());
        String inA = String.valueOf(session.destinationCopiesWithNoWayIn());

        session.setDirection(far, Direction.TOWARD_B);
        session.rebuild();

        String outB = String.valueOf(session.destinationCopiesWithNoWayOut());
        String inB = String.valueOf(session.destinationCopiesWithNoWayIn());

        assertTrue(outA.contains("Middle") || outB.contains("Middle"),
            "barring the far section one way leaves the middle station an arrival with no track out "
            + "of it, and nothing said so.  A train sent there can never leave, and autonomy may send "
            + "one.  Out: " + outA + " then " + outB);

        // AND THE FAR END IS NOT REPORTED, which is the restriction that keeps these useful.
        //
        // Its only arrival is stuck once the section is barred, and a square whose every arrival is
        // stuck is a stuck SQUARE - which checkStationUnreachable and checkStationReachesNothing
        // already warn about.  Before this was narrowed, the three copy checks named twenty-two things
        // on Adam's railway and twenty-one of them were squares the editor already complained about.
        assertFalse(inA.contains("FarEnd") || inB.contains("FarEnd"),
            "a square whose every arrival is stuck was reported per-copy as well, which is the same "
            + "complaint the square-level checks already make.  In: " + inA + " then " + inB);

        assertTrue(outA.contains("Middle") != outB.contains("Middle"),
            "the same arrival was reported as trapped whichever way the far section was barred, so "
            + "the check is not reading the direction at all.  Out: " + outA + " then " + outB);

        // THE TRAPPING DIRECTION, PUT BACK FIRST (V33-C5, 2026-09-03).
        //
        // `subjectsOf` asks `check()` about the CURRENT state, and the assertions above leave whatever
        // the last `setDirection` set.  So this used to pass on the accident that the trapping
        // direction happened to be the one applied second: swapping the two calls above, or writing
        // `route(Side.W, Side.E)` in `TilePorts` - a rename with no behavioural meaning - made it fail
        // while accusing the editor of not reporting something that does not exist in that state.
        //
        // Which of the two traps the arrival is the tile's business, which is this test's whole
        // design, so it is asked rather than assumed.
        session.setDirection(far, outA.contains("Middle") ? Direction.TOWARD_A : Direction.TOWARD_B);
        session.rebuild();

        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.COPY_NO_WAY_OUT).isEmpty(),
            "the session knows about the trapped arrival but the editor never reports it");
    }

    /**
     * And the one that has a way out, and still gets the train nowhere.
     *
     * Adam, 2026-09-02: **"yes, extend the check to catch that shape too."**  BottomInner on his own
     * railway was a station a train could arrive at, and depart from, and never get anywhere from: one
     * way in, one way out, and the way out led only to track that came back.  It has an edge each way,
     * so the two checks above both pass it - which is exactly why he asked.
     *
     * **The square is healthy and one of its arrivals is not, and that is the whole point.**  Swept
     * over his frozen diagram, an unrestricted version of this named ten squares, and
     * `checkStationReachesNothing` already warned about nine of them - the same complaint twice.
     * Where every arrival is stuck the SQUARE is stuck and the square-level check has it; the only
     * thing no square-level check can see is a good square with one bad way in.
     *
     * The fixture is that, and it needs no one-way restriction at all: a run with a station at each
     * end and a plain sensor beyond, where the sensor is a dead end nothing may turn at.  Arriving at
     * the middle station from the west you can only carry on into that dead end; arriving from the
     * east you carry on to a real station.  One square, one trap, one good arrival.
     *
     * **The control is the same railway with the dead end marked "trains may turn here"**, which is
     * what a real railway does with a stub, and then nothing is reported at all.
     *
     * MUTATION: counting any Point the train can get to, rather than only a STATION, makes this pass
     * with the railway unchanged - the middle station reaches the dead-end sensor perfectly well, and
     * a train sent there still cannot get anywhere it is allowed to stop.
     *
     * NOT COVERED HERE: the walk also refuses to count a station standing on the SAME square, which is
     * what stops a trapped arrival calling its own turning twin an escape.  Reaching your own twin
     * needs a LOOP - track that comes back to the square from the other side - and this fixture is a
     * straight run, so that rule is defensive rather than demonstrated.
     */
    @Test
    public void testTheEditorWarnsAboutACopyThatReachesNoOtherStation() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Reaches", null);
        session.getStore().setActiveConfiguration("Reaches");

        TileKey near = new TileKey("main", 1, 1);
        TileKey middle = new TileKey("main", 4, 1);
        TileKey stub = new TileKey("main", 7, 1);

        session.setStation(near, true);
        session.setPointName(near, "NearEnd");

        session.setStation(middle, true);
        session.setPointName(middle, "Middle");

        // THE STUB IS DELIBERATELY NOT A STATION.  It is where the middle station's eastward arrival
        // can only carry on to, and it is the control for reporting on STATIONS rather than on Points:
        // the train gets there perfectly well and still has nowhere it may stop.
        session.setPointFlag(near, AutonomyBuilder.CAN_REVERSE, true);
        session.setPointFlag(stub, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        assertTrue(session.destinationCopiesReachingNoStation().isEmpty(),
            "with the stub marked as somewhere trains may turn, every arrival gets somewhere - and "
            + "this reported one that does not: " + session.destinationCopiesReachingNoStation());

        // NOW TAKE THE TURN AWAY, which is the whole change.
        session.setPointFlag(stub, AutonomyBuilder.CAN_REVERSE, false);

        session.rebuild();

        java.util.Map<TileKey, String> stranded = session.destinationCopiesReachingNoStation();

        assertFalse(stranded.isEmpty(),
            "a station whose eastward arrival can only carry on into a dead end nothing may turn at "
            + "reaches no other station, and nothing said so.  This is the shape Adam asked for: a "
            + "way in, a way out, and nowhere to go.  Copies: " + stranded
            + "  no way out: " + session.destinationCopiesWithNoWayOut());

        assertTrue(String.valueOf(stranded).contains("Middle"),
            "the station that reaches nothing is Middle, and this named something else: " + stranded);

        // AND NOT THE STUB, which is not a station and is nobody's destination.
        assertFalse(String.valueOf(stranded).contains("7,1"),
            "plain track was reported as a station that reaches nothing: " + stranded);

        // AND THE SQUARE-LEVEL CHECK IS SILENT, which is the reason this one exists.  If it fired
        // here, this check would be a second way of saying something the editor already says.
        assertFalse(subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING).contains("Middle"),
            "the square-level check already reports Middle, so a copy-level check adds nothing here "
            + "and the fixture is not the shape it was meant to be");

        assertFalse(subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.COPY_REACHES_NOTHING).isEmpty(),
            "the session knows the arrival gets nowhere but the editor never reports it");
    }

    /**
     * A straight run of track with a sensor at each end and one in the middle.
     *
     * Three sensors rather than two, because the shape that needs catching is a station with track on
     * both sides of it whose onward track goes nowhere - which needs somewhere for the onward track to
     * end.
     */
    private LayoutDiagram deadEndRun() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 10, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * A plain square marked out of service draws its cross while trains are running, not only while
     * the setup is being edited (SVN-B6, D24-C9).
     *
     * The running diagram badges a square only when it is worth badging, and the list of what that
     * means is written six lines above the expression: *"this is a station, trains turn round here,
     * autonomy is not using it."*  The third had no term in it.  So a plain sensor switched off got no
     * badge at all and drew nothing, while the editor - which badges every Point in the graph - drew
     * its cross.  The one drawing that says "nothing may pass here" appeared while setting the railway
     * up and vanished while running it, which is the half the operator is looking at when a train does
     * not arrive.
     *
     * MUTATION: dropping the `|| shut` term from `worthABadge` fails this and nothing else.
     */
    @Test
    public void testAShutPlainSquareDrawsItsCrossOnTheRunningDiagram() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Shut", null);
        session.getStore().setActiveConfiguration("Shut");

        // The MIDDLE sensor, which is neither a station nor anywhere trains turn round - so the only
        // thing that could earn it a badge is being switched off.
        TileKey plain = new TileKey("main", 4, 1);

        session.rebuild();

        org.traincontrol.automationui.TileAnnotation open = session.staticAnnotationFor(plain);

        assertTrue(open == null || open.getBadge() == null,
            "control: an ordinary plain sensor already carries a badge on the running diagram, so "
            + "this fixture cannot show what switching one off adds.  Got: " + open);

        session.setPointProperty(plain, "active", Boolean.FALSE);

        session.rebuild();

        org.traincontrol.automationui.TileAnnotation shut = session.staticAnnotationFor(plain);

        assertNotNull(shut, "a square switched off is described as nothing at all on the running "
            + "diagram, so it draws exactly like one in use");

        assertNotNull(shut.getBadge(),
            "a square switched off earns no badge on the running diagram, so the cross the editor "
            + "draws for it disappears the moment autonomy starts");

        assertTrue(shut.getBadge().isImpassable(),
            "the badge for a square nothing may use does not say so, so it draws as an ordinary "
            + "point rather than as a cross");
    }

    /**
     * And the running graph is told, so trains do not simply drive through it (D24-B5).
     *
     * The editor offers **Out of service** on every square, station or not, and its handler writes
     * `active` and touches nothing else - no direction, no arrow.  The builder used to drop that
     * property for anything that is not a station, on the reasoning that "the arrows say that through
     * the derivation".  They do not: nothing sets an arrow when the square is switched off.
     *
     * So the cross was a promise the runtime never kept.  `Layout.isPathClear` refuses a path whose
     * intermediate point is inactive, which is exactly what the cross means, and it can only do that
     * if the flag reaches the graph.
     *
     * MUTATION: putting back `if ("active".equals(key) && !point.isStation()) continue;` fails this.
     */
    @Test
    public void testAShutPlainSquareReachesTheRunningGraph() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("ShutBuild", null);
        session.getStore().setActiveConfiguration("ShutBuild");

        TileKey plain = new TileKey("main", 4, 1);

        session.rebuild();

        // THE CONTROL, ASKED OF THE COPIES (TS3-B1).
        //
        // It used to compare `pointNameForTile` - a BASE name - against the names in the built
        // configuration, which are the emitted COPY names.  This square splits into two copies, so the
        // builder suffixes both and the base name is never in that list: the control could not fail,
        // and what it exists to catch is a fixture that ships the square already inactive, which would
        // make the whole test vacuous.
        java.util.List<String> beforeAnything =
            session.pointNamesFor(session.pointNameForTile(plain));

        assertFalse(beforeAnything.isEmpty(), "the square emitted no Points to begin with");

        for (String copy : beforeAnything)
        {
            assertFalse(inactivePointNames(session).contains(copy),
                "control: " + copy + " is already inactive in the built graph before anything switched "
                + "it off, so this fixture cannot show the flag arriving");
        }

        session.setPointProperty(plain, "active", Boolean.FALSE);

        session.rebuild();

        String name = session.pointNameForTile(plain);

        assertNotNull(name, "the square stopped being a Point when it was switched off");

        // EVERY COPY OF IT, not just one.  A square with track on both sides is emitted once per
        // arrival side, and a train barred from one side and admitted from the other is a square that
        // is half switched off - which is not a state the menu can express or the cross can mean.
        java.util.List<String> copies = session.pointNamesFor(name);

        // TWO, not one (TS3-C1).  The comment above is about a square that became SEVERAL Points, so a
        // fixture that emits one satisfies the loop below while testing nothing it is written for -
        // and this is the sibling of the floor `TCX-B9` put on the same shape 2,800 lines down.
        assertTrue(copies.size() >= 2,
            "precondition: this needs a square that became several Points, and it got " + copies.size()
            + " - " + copies);

        for (String copy : copies)
        {
            assertTrue(inactivePointNames(session).contains(copy),
                "a plain square switched off is built as an ACTIVE point (" + copy + "), so the "
                + "runtime never learns about it and trains carry on running through the square the "
                + "editor draws a cross on.  Inactive points in the build: "
                + inactivePointNames(session));
        }
    }

    /**
     * The names of the points the built configuration marks inactive.
     *
     * @param session the setup to build
     * @return the names, empty when nothing is switched off
     */
    private java.util.List<String> inactivePointNames(AutonomySession session) throws Exception
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject p = points.getJSONObject(i);

            if (p.has("active") && !p.optBoolean("active", true)) out.add(p.getString("name"));
        }

        return out;
    }

    /**
     * Two sensors with two plain tiles between them.
     */
    /**
     * A ROAD THE OPERATOR HAS SHUT DRAWS NO ARROW, EVEN WHERE THE BLADES WOULD HAVE ONE (VD18-B1).
     *
     * `directionMarks` is what the diagram draws.  It used to override the authored answer whenever
     * it was not `NONE`, on the reasoning that a route the hardware restricts is one-way whatever the
     * user chose - true for the DEFAULT, which is `BOTH`, and false for the case that matters.
     *
     * On a permanent turnout every route is directed at the toe, so `TOWARD_A` - toward the fork -
     * permits only an entry the blades refuse: nothing passes, which is exactly what an operator who
     * shuts that arm means.  The override turned that into an arrow pointing at the toe, so the
     * drawing showed trains running along a road that had been closed.  Nothing covered
     * `directionMarks` at all before this claim, which is how it survived.
     *
     * MUTATION: drop the `isPassable` branch from `directionMarks` and the shut route draws an arrow
     * again; make `isPassable` answer true for everything and the same claim fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testAShutRouteDrawsNoArrow() throws IOException
    {
        session.open(Arrays.asList(permanentTurnout()));

        TileKey toe = new TileKey("main", 3, 1);

        java.util.Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes =
            session.getRoutes(toe);

        assertFalse(routes.isEmpty(), "the fixture gave the permanent turnout no routes at all");

        for (java.util.Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

            assertNotNull(route.getDirectedToward(),
                "precondition: this tile's routes should be directed at the toe, and nothing below is"
                + " about a permanent turnout if they are not");

            // THE ANSWER THAT PERMITS ONLY THE ENTRY THE BLADES REFUSE: a closure, not a direction.
            //
            // Travel TOWARD A means entering at B, which `isTraversableFrom` allows only when the
            // route is directed at A.  So the shut answer is the one naming the side the route is NOT
            // directed at - the first draft of this line named the allowed one and asserted the arrow
            // was absent, which failed against correct code.
            Direction shut = route.getDirectedToward() == route.getA() ? Direction.TOWARD_B
                : Direction.TOWARD_A;

            session.setDirection(toe, entry.getKey(), shut);
        }

        for (TileAnnotation.Mark mark : session.directionMarks(toe))
        {
            assertEquals(mark.getDirection(), Direction.NONE,
                "the diagram draws an arrow along a road the operator has shut: the answer stored"
                + " permits only an entry the blades refuse, so no train can pass either way, and the"
                + " arrow says trains run toward the toe");
        }

        // AND THE DEFAULT STILL DRAWS THE ONE ROAD THERE IS.  A rule that drew nothing for these tiles
        // would take away the arrow that says which way the turnout can be used at all.
        for (java.util.Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            session.setDirection(toe, entry.getKey(), Direction.BOTH);
        }

        for (TileAnnotation.Mark mark : session.directionMarks(toe))
        {
            assertNotEquals(mark.getDirection(), Direction.NONE,
                "the turnout is open in the direction the blades allow, and the diagram draws nothing");
        }
    }

    /**
     * A run of track with a permanently-set turnout at its far end: no address, trailing moves only.
     *
     * @return the page
     * @throws IOException from the diagram
     */
    private LayoutDiagram permanentTurnout() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CUSTOM_PERM_LEFT, 3, 1, 1, 0, 0, 0,
            accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * THE TWO ARCS OF A DOUBLE CURVE ARE TWO PIECES OF METAL (Adam, 2026-09-22, on OB-238 / IND9X-C9).
     *
     * *"It is two pieces of metal.  Imagine two parallel tracks simple appearing on one tile for visual
     * convenience.  Two distinct, not connected paths."*
     *
     * Occupancy is per Point, and a square is emitted as one Point per side a train can arrive by, so
     * the builder groups those copies under a `block` - two trains cannot stand on one square.  That
     * grouping was keyed by the TILE alone, so a train on one arc of a double curve made the other arc
     * read occupied and `isPathClear` refused track that is physically free.  The reduction has keyed
     * these tiles per ROAD since AUR-B1; this is the same rule at the occupancy door.
     *
     * MUTATION: key `blockFor` by the tile alone and this finds one name where it wants two.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testEachArcOfADoubleCurveIsItsOwnPieceOfMetal() throws IOException
    {
        session.open(Arrays.asList(doubleCurveCrossroads()));

        TileKey curve = new TileKey("main", 2, 2);

        session.rebuild();

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        java.util.Map<String, Integer> blocks = new java.util.LinkedHashMap<>();

        int copies = 0;

        for (Object o : built.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            // FOUND BY THE BLOCK ITSELF, which is the only thing in the emitted point that names the
            // square: the bare tile before this fix, and the tile plus its road after it.
            String block = point.optString("block", null);

            if (block == null || !block.startsWith(curve.toString())) continue;

            copies++;

            blocks.put(block, blocks.containsKey(block) ? blocks.get(block) + 1 : 1);
        }

        assertTrue(copies > 1,
            "precondition: the double curve must be emitted as several copies carrying a block, or"
            + " there is nothing here to group - found " + copies);

        assertEquals(blocks.size(), 2,
            "the two arcs of a double curve came back as " + blocks.size() + " piece(s) of metal: "
            + blocks + ".  They are two arcs in opposite corners that never touch, so a train on one"
            + " does not stand on the other - grouping them refuses a second train track that is"
            + " physically free");

        for (java.util.Map.Entry<String, Integer> entry : blocks.entrySet())
        {
            assertNotEquals(entry.getKey(), curve.toString(),
                "an arc is still grouped under the bare tile, which is the grouping this is about");
        }
    }

    /**
     * A double-curve sensor with track on both of its arcs: north, west, east and south all reachable.
     *
     * @return the page
     * @throws IOException from the diagram
     */
    private LayoutDiagram doubleCurveCrossroads() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 6, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 2, 1, 1, 0, 21, 21, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 22, 22, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK_DOUBLE_CURVE, 2, 2, 0, 0, 25, 25,
            accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 2, 0, 0, 23, 23, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 3, 1, 0, 24, 24, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

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
     * The session says which pages the migration rewrote, and how many names it took (RGN-B1).
     *
     * The migration edits files the user owns. A `Point:` name typed onto their own track diagram is
     * taken into the setup and emptied out of the `.cs2`, and nothing at this build writes those
     * labels back - so an upgrader who later opens 2.7.4c finds their station captions gone from the
     * diagram. `saveChanges` keeps a `.cs2.bak`, which makes them recoverable by hand.
     *
     * **Failures were reported and successes were not**, which is the wrong way round: a failure means
     * the migration simply runs again next time, and a success is the one-way half.
     * `TrainControlUI` writes these two into the log at start-up; what is asserted here is that the
     * session has them to write, because the log line itself is one string in a method that needs a
     * window and a Central Station.
     *
     * MUTATION: dropping either `migratedPages.add` or `migratedCaptions++` fails this.
     */
    @Test
    public void testTheSessionSaysWhichPagesTheMigrationRewrote() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.save();

        page.addComponent(componentType.TEXT, 1, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "Bahnhof");

        // And an orphan, which is rewritten out of nothing: it names no station this setup knows, so
        // it stays on the diagram and must not be counted among the names taken.
        page.addComponent(componentType.TEXT, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "GhostSiding");

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(page));

        assertTrue(reopened.getMigrationFailures().isEmpty(),
            "precondition: the page has to have been rewritten for there to be anything to report: "
            + reopened.getMigrationFailures());

        assertEquals(reopened.getMigratedPages(), Arrays.asList(page.getName()),
            "the session cannot say which of the user's page files it rewrote, so the start-up log "
            + "cannot say it either - and a user who upgrades, then goes back to 2.7.4c and finds "
            + "their station captions gone from the diagram, has nothing anywhere telling them what "
            + "happened or that a .cs2.bak is sitting beside each page (RGN-B1)");

        assertEquals(reopened.getMigratedCaptions(), 1,
            "the count of names taken over is wrong.  One label named a station this setup knows and "
            + "one named nothing; only the first is taken, and only the first is removed from the "
            + "user's file");

        // A SECOND OPEN HAS NOTHING TO REPORT, which is what makes the log line trustworthy.
        //
        // The counters are per-open, not cumulative: a session that reported the same pages every
        // time it was opened would put the notice in front of a user whose files nothing had touched.
        //
        // ON THE SAME SESSION, which is the only version of this that can fail (VD9-C19).  A brand-new
        // AutonomySession has empty counters before open() has run at all, so asking a fresh one
        // stayed green with the reset removed - it was asserting the constructor, not the reset.
        reopened.open(Arrays.asList(page));

        assertTrue(reopened.getMigratedPages().isEmpty(),
            "the second open on this session still reports the first one's work, so the notice "
            + "appears at every start-up instead of the once after the upgrade: "
            + reopened.getMigratedPages());

        assertEquals(reopened.getMigratedCaptions(), 0, "and so does the count");
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
     * A pocket whose only station is one autonomy may never choose still leads nowhere (V36-C4).
     *
     * **Adam, 2026-09-04: "Make it a notice."** - which is the severity half of the question, and it
     * is why this is safe to tighten. `20` of the `71` squares on his own railway carry
     * `autoDestination: false`, so matching the runtime could put a line against a good many of them;
     * a notice is the one severity that can afford that.
     *
     * **The finding.** The check counted any STATION as somewhere to go. The runtime does not:
     * `Layout.isSendableDestination` requires `!isReversing() && isAutoDestination()` before autonomy will pick a
     * destination at all. So a reversing point whose only reachable station is a parking berth read as
     * healthy in the editor and was useless in the run - a train sent there turns round, and there is
     * still nowhere for autonomy to send it next.
     *
     * **Only the two SQUARE-level clauses.** The runtime's other two - a terminus this train cannot
     * leave, and a station that excludes it - both take a locomotive, and this notice is drawn on a
     * square with no train in the question. Asking them here would mean picking a train or quantifying
     * over every train owned, and "no locomotive on the railway could go there" is a different and
     * much weaker claim than the one the notice makes. That distinction is the one that moved
     * `BottomMainB` on `FR-058` the day before, arriving from the other side.
     *
     * The fixture is the one above with one flag moved, so this is about the rule rather than about
     * the track.
     *
     * MUTATION: counting a station regardless of `isAutoDestination` - which is what the check did -
     * fails the second half; counting nothing at all fails the first.
     */
    @Test
    public void testAPocketWhoseOnlyStationIsAParkingBerthStillLeadsNowhere() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

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

        session.getStore().createConfiguration("Reversing", null);
        session.getStore().setActiveConfiguration("Reversing");

        session.setPointFlag(reversing, AutonomyBuilder.CAN_REVERSE, true);

        // The pocket gets its station, which is what silences the notice above.
        session.getStore().setStation(farEnd, true);

        session.rebuild();

        assertTrue(session.mayTurnTiles().contains(reversing)
            || session.mandatoryTurnTiles().contains(reversing),
            "precondition: the square is not actually one where trains change direction");

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE),
            "precondition: an ordinary station in the pocket must silence the notice, or the second "
            + "half below proves nothing");

        // AND NOW IT IS A BERTH AUTONOMY MAY NEVER CHOOSE.
        session.setAutoDestination(farEnd, false);

        session.rebuild();

        assertFalse(session.isAutoDestination(farEnd),
            "the fixture did not take: the station is still one autonomy may choose");

        assertTrue(hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE),
            "the only station reachable from that reversing point is one autonomy will never choose, "
            + "so a train sent there turns round and is still nowhere - which is exactly what this "
            + "notice is for, and the editor counted it as somewhere to go (V36-C4)");

        // AND THE SEVERITY IS THE ONE HE ASKED FOR.
        for (org.traincontrol.automationui.AutonomyChecks.Finding f : session.check())
        {
            if (!org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE
                .equals(f.getMessageKey())) continue;

            assertEquals(f.getSeverity(),
                org.traincontrol.automationui.AutonomyChecks.Severity.NOTICE,
                "Adam asked for a notice, and tightening the rule is only safe because it is one");
        }
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
     * A compulsory turn that reaches nothing is named as a terminus (AMG-C1).
     *
     * `AutonomyChecks.isTerminus` asked the authored "terminus" key, which setting a square's flags now clears, and
     * fell back to "no reduced edge leaves the tile" - false for every compulsory turn, whose turning copy departs.
     * So the specific message for a terminus a train is stranded at could not be chosen for the squares it describes.
     */
    @Test
    public void testAStrandedCompulsoryTurnIsNamedAsATerminus() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("AMG", null);
        session.getStore().setActiveConfiguration("AMG");

        TileKey west = new TileKey("main", 1, 1);
        TileKey east = new TileKey("main", 5, 1);

        session.setStation(west, true);
        session.setPointName(west, "WestEnd");

        session.setStation(east, true);
        session.setPointName(east, "EastEnd");

        session.setPointFlag(west, AutonomyBuilder.MUST_REVERSE, true);

        session.setBarredArrivals(east, new java.util.LinkedHashSet<>(session.arrivalSides(east)));

        session.rebuild();

        assertTrue(session.mandatoryTurnTiles().contains(west), "precondition: the west end is not a compulsory turn");

        java.util.List<String> stranded =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING);

        java.util.List<String> terminus =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED);

        assertTrue(stranded.contains("WestEnd") || terminus.contains("WestEnd"),
            "precondition: the west end reaches nothing and neither finding names it: " + stranded + " / " + terminus);

        assertTrue(terminus.contains("WestEnd"),
            "the west end is a compulsory turn that reaches no station, and the check named it as a station that reaches"
            + " nothing rather than as a terminus a train would be stranded at (AMG-C1)");
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

        // WHETHER IT WAS STORED AT ALL, not just what it reads as.
        //
        // This captured `getBoolean(key, false)` and put that back unconditionally - written when the
        // default WAS false. The default became true the next day, so on any machine where nobody had
        // ever set the key - which is every machine, including Adam's - the capture read false and the
        // finally wrote an explicit false into his live preferences. Running the suite once switched
        // off the feature he had just asked to be on by default, with nothing on screen connecting
        // the two.
        //
        // Found by a reviewer. A test may read the user's settings; it may not decide them.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        boolean had = prefs.get(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null) != null;

        boolean was = prefs.getBoolean(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

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
            // Put back as it was, INCLUDING having been unset.
            if (had)
            {
                prefs.putBoolean(
                    org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, was);
            }
            else
            {
                prefs.remove(org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            }
        }
    }

    /**
     * The station ingress arrows follow the same switch as the travel restrictions (Adam, 2026-08-28).
     *
     * "Couple the station ingress arrows to this setting in the track viewer, but be careful not to
     * disturb their behavior in the editor."
     *
     * A chevron saying which way into a platform is shut is the same kind of statement as a one-way
     * arrow - both say where a train may not go, both come from this setup - so a switch that turned
     * off one and left the other would be a setting that half works.
     *
     * The preference is set through the key the code reads and put back afterwards, because these are
     * the real preferences of whoever runs the suite.
     *
     * MUTATION: drawing the arrivals unconditionally - which is what the viewer did before - fails the
     * first half.
     */
    @Test
    public void testTheIngressArrowsFollowTheSettingInTheViewer() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Ingress", null);
        session.getStore().setActiveConfiguration("Ingress");

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Platform");

        session.rebuild();

        java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
            session.arrivalSides(station);

        assertTrue(ways.size() > 1,
            "precondition: the station has fewer than two ways in, so barring one leaves nothing to "
            + "draw a chevron about");

        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<>(Arrays.asList(ways.get(0))));

        session.rebuild();

        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        boolean had = prefs.get(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null) != null;
        boolean was = prefs.getBoolean(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

        try
        {
            prefs.putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, false);

            org.traincontrol.automationui.TileAnnotation off =
                session.staticAnnotationFor(station);

            assertTrue(off == null || off.getArrivals().isEmpty(),
                "the barred way into a platform is still drawn on the diagram with travel "
                + "restrictions switched OFF, so the setting turns off the arrows and leaves the "
                + "chevrons - which is the half-working state the coupling was asked for to stop");

            prefs.putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

            org.traincontrol.automationui.TileAnnotation on =
                session.staticAnnotationFor(station);

            assertNotNull(on, "a station with a barred way in is described as nothing at all");

            assertFalse(on.getArrivals().isEmpty(),
                "the barred way into a platform is not drawn with travel restrictions switched ON, "
                + "so the coupling took the chevrons away instead of following the switch");
        }
        finally
        {
            if (had)
            {
                prefs.putBoolean(
                    org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, was);
            }
            else
            {
                prefs.remove(org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            }
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

        // A configuration to import INTO, as every other import test has and as the menu path does.
        // The operational settings - priority, speed multiplier, exclusions, the station's own length
        // limit - are stored per configuration, so without one they are silently skipped and this
        // method would have asked nothing about them (IPR-A1).
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

        // A STATION'S CAPACITY, NOT A TRACK LENGTH (IPR-A1).
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
     * An imported legacy file brings its settings, not only its points (RGN-A1).
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
     * A stretch answered 0 on purpose is not reported as a reversal without a length (Adam, 2026-09-23: *"stop
     * listing answered zeros as missing"*).
     *
     * The same railway as the claim below, at its last step - measured elsewhere, nothing on this run-in - where the
     * notice fires.  Every square of the run-in answered 0 then silences it; the rules still read those squares as
     * unmeasured, so the guard stays exactly as blind as the notice used to say.
     */
    @Test
    public void testAReversalWhoseStretchIsAnsweredZeroIsNotAsked() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Lengths");

        TileKey turns = new TileKey("main", 1, 1);

        session.setStation(turns, true);
        session.setPointProperty(turns, "canReverse", Boolean.TRUE);
        session.setTileLength(new TileKey("main", 4, 1), 7);
        session.rebuild();

        assertTrue(session.reversalsWithoutLength().containsKey(turns),
            "CONTROL: nothing on the run-in is measured and the notice did not fire, so the claim below would pass by"
            + " the notice never firing");

        for (int x = 1; x <= 3; x++) session.getStore().answerTileLengthZero(new TileKey("main", x, 1));

        assertFalse(session.reversalsWithoutLength().containsKey(turns),
            "every square of the run-in was answered 0 on purpose and the editor still asks for its length: "
            + session.reversalsWithoutLength());
    }

    /**
     * The editor asks for lengths where trains reverse - but only on a layout that measures track.
     *
     * Adam: "Add notices to the autonomy editor to add track lengths between stations and switches
     * that accept reversal (if any other track length is set anywhere)."
     *
     * The guard that stops a train being backed over the switch behind its berth can only refuse what
     * it can measure, so these are the squares where it is blind: somewhere trains turn round, with
     * nothing recorded about how much room is there.
     *
     * **The second half is the half worth testing.** A railway that records no lengths at all has
     * decided not to model them, and listing every reversing square on it would be a page of notices
     * about something nobody is attempting. The same fixture is asked twice, and the only difference
     * between the two answers is one length recorded somewhere else entirely.
     *
     * MUTATION: dropping the measuresAnything test reports the square in both halves; dropping the
     * isTurnAround test reports squares nothing reverses at.
     */
    @Test
    public void testTheEditorAsksForLengthsWhereTrainsReverse() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Lengths");

        TileKey turns = new TileKey("main", 1, 1);

        session.setStation(turns, true);
        session.setPointProperty(turns, "canReverse", Boolean.TRUE);
        session.rebuild();

        assertTrue(session.isTurnAround(turns),
            "the fixture did not take: this square must be one trains turn round at");

        // NOTHING MEASURED ANYWHERE: the notice stays away.
        assertFalse(session.reversalsWithoutLength().containsKey(turns),
            "a layout that records no track lengths at all was asked to record one here, which is a "
            + "notice about something nobody on that railway is trying to do");

        // ONE LENGTH, ON A SQUARE OUTSIDE THIS STRETCH, so the layout is one that measures track
        // without that measurement being about the run-in under test.
        //
        // 4,1 is the sensor at the far end; the stretch behind the reversal at 1,1 is 3,1, 2,1 and
        // 1,1 itself.  This used to measure 2,1, which IS in the stretch - harmless while the notice
        // waited for every square, and the thing being measured once it waits for any (MT-364).
        session.setTileLength(new TileKey("main", 4, 1), 7);

        assertTrue(session.reversalsWithoutLength().containsKey(turns),
            "the layout measures track now, and the square where a train turns round - the one place "
            + "the length rule cannot do its job without a number - was not asked about");

        // ONE ENTRY, NOT ONE PER SQUARE (OB-171).
        //
        // Adam: "warnings ... fire on many tiles along a line.  Dedupe them, one per segment between a
        // switch and a station."  The map is the reversal square mapped to how many squares its guard
        // still needs measured, so a run of ten unmeasured squares is one notice saying ten - not ten
        // notices saying the same sentence.
        assertEquals(session.reversalsWithoutLength().size(), 1,
            "one square trains turn at, and the notice is not one entry: "
            + session.reversalsWithoutLength());

        // AND IT GOES ONCE THE STRETCH IS MEASURED AT ALL (Adam, MT-364, 2026-09-12).
        //
        // This used to assert the opposite - that the notice stays until EVERY square of the run-in
        // has a number - on the reasoning that "the guard needs every square before it can judge
        // anything".  That reasoning was true when it was written and stopped being true on
        // 2026-09-06, when Adam ruled that a stretch is only indeterminate if ALL of it is zero: a
        // half-measured run-in makes the guard PESSIMISTIC rather than blind, and it still binds.
        //
        // He met the consequence on his own railway: *"I set the track at 10,10 to length 2, so now we
        // know that the track to TunnelLongPark is 2.  But it is still asking for the tile at 10,9
        // (TunnelLongPark) to get a length."*  The reversal square was asked for on its own account,
        // before the walk was consulted, so measuring the track behind it could never have helped.
        session.setTileLength(turns, 5);

        assertFalse(session.reversalsWithoutLength().containsKey(turns),
            "the stretch behind this square carries a measurement and the editor is still asking for "
            + "the rest of it. Adam, MT-364: \"it is still asking for the tile at 10,9 to get a "
            + "length\" - the guard judges what it can see, and a notice about a stretch it can "
            + "already judge is a list you can never empty: " + session.reversalsWithoutLength());

        // AND THE CASE THE NOTICE EXISTS FOR IS UNTOUCHED: nothing measured in the stretch at all.
        //
        // Without this the claim above is satisfied by a notice that has stopped firing entirely,
        // which is the direction this change could most easily go wrong in.
        session.setTileLength(turns, 0);

        assertTrue(session.reversalsWithoutLength().containsKey(turns),
            "the layout measures track elsewhere and NOTHING on this run-in has a number, so the guard "
            + "is blind here rather than pessimistic - and the notice said nothing: "
            + session.reversalsWithoutLength());
    }

    /**
     * The editor says when a platform is told to hold more train than its track measures.
     *
     * Adam, 2026-09-11: *"Let's add an autonomy editor notice that alerts the user if a run-in is
     * shorter than the berth length, that way they can decide if it makes sense or not.  for example, a
     * station of length 4 may have two segments of tracks on either side of a switch, of length 2.  that
     * is acceptable and its limitations are understood."*
     *
     * **Two numbers set by two different hands.**  `maxTrainLength` is what somebody typed on the
     * platform; the measured room is what the track says, counted back from the platform to whichever of
     * the last switch and a reversal is met first.  Where the second is smaller the first never binds,
     * and a train inside the stated maximum is refused by a rule quoting a number nobody typed.
     *
     * **His own example is the first half of this test**, because it is the case that is NOT a fault: a
     * four-unit platform whose track is two stretches of two either side of a switch.  Nothing is wrong
     * with that railway - the geometry is finer than one number per station - and the notice exists so he
     * can say so and move on, which is why it is a NOTICE and not a warning.
     *
     * **The second half is the reversal**, which is the branch the switch case cannot reach: an edge
     * crossing no switch does not bound anything on its own, because the guard carries on walking back
     * through earlier edges - unless the train turns round where that edge starts, which is where the
     * walk stops under Adam's other ruling of the same day.
     *
     * **And three silences, each about a different missing half**, because a notice that fires when it
     * cannot know anything is the wall this list has twice been called.
     *
     * MUTATIONS: comparing against the edge's whole length rather than `getRoomAtTheEnd` loses the
     * switch case; taking the `isTurnAround` test out reports every station with a short arriving edge,
     * most of which the guard never judges on that edge; dropping the `room > 0` test reports every
     * unmeasured approach; and reporting the room as `{2}` and the maximum as `{3}` swaps the sentence
     * round, which the two assertions on the numbers catch.
     */
    @Test
    public void testTheEditorSaysWhenAPlatformHoldsLessTrackThanItClaims() throws Exception
    {
        session.open(Arrays.asList(platformBehindASwitch()));
        session.initialize("Short");

        TileKey platform = new TileKey("main", 5, 1);
        TileKey start = new TileKey("main", 1, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.rebuild();

        // NOTHING MEASURED: a railway that records no track lengths has decided not to model them, and
        // there is nothing to compare the maximum against.
        assertFalse(runInNotice() != null,
            "a railway that measures no track was told its platforms are shorter than their maxima, "
            + "which is a comparison with one side missing");

        // HIS EXAMPLE: a platform of four whose track is two either side of the switch.
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding notice =
            runInNotice();

        assertNotNull(notice, "a platform set to hold four units of train has two units of track "
            + "between it and the switch behind it, so a three-unit train is refused there whatever "
            + "the maximum says - and nothing told the operator (Adam, 2026-09-11)");

        assertEquals(notice.getSubject(), session.pointNameForTile(platform),
            "the notice is about some other square: " + notice);

        assertEquals(notice.getCount(), 4,
            "the first number in the sentence is the maximum somebody typed, which is 4 here");

        assertEquals(notice.getDetail(), 2,
            "the second number is the room the guard will actually measure, which is the two units "
            + "beyond the switch - not the four units of track on the edge");

        assertEquals(notice.getSeverity(),
            org.traincontrol.automationui.AutonomyChecks.Severity.NOTICE,
            "Adam's own example of this is a railway with nothing wrong with it - \"that is acceptable "
            + "and its limitations are understood\" - so it does not belong among the warnings");

        // AND IT GOES WHEN THE TRACK BEYOND THE SWITCH IS LONG ENOUGH.  The stretch before the switch
        // is left short on purpose: it is not the stretch the rule measures, so it must not matter.
        session.setTileLength(new TileKey("main", 4, 1), 4);
        session.rebuild();

        assertFalse(runInNotice() != null,
            "four units of track beyond the switch hold the four-unit train the platform claims, and "
            + "the notice is still asking about it");

        // A PLATFORM WITH NO STATED MAXIMUM IS NOT ASKED ABOUT: that is NO_MAX_TRAIN_LENGTH's sentence,
        // and a comparison needs two numbers.
        session.setPointProperty(platform, "maxTrainLength", 0);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertFalse(runInNotice() != null,
            "a platform with no stated maximum was compared against one");

        // AND AN UNMEASURED STRETCH IS UNKNOWN, NOT SHORT - the doctrine the guard itself follows.
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.setTileLength(new TileKey("main", 4, 1), 0);
        session.rebuild();

        assertFalse(runInNotice() != null,
            "the track beyond the switch has no length recorded and the platform was reported as "
            + "short. Unmeasured is unknown, not zero - and REVERSAL_NEEDS_LENGTH is the notice that "
            + "asks for a number where one is needed");

        // THE OTHER BRANCH: no switch between the two squares, and the train turns round at the far end.
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("ShortTurn");

        TileKey turns = new TileKey("main", 1, 1);
        TileKey berth = new TileKey("main", 4, 1);

        session.setStation(turns, true);
        session.setPointName(turns, "Turns");
        session.setPointProperty(turns, "canReverse", Boolean.TRUE);
        session.setStation(berth, true);
        session.setPointName(berth, "Berth");
        session.setPointProperty(berth, "maxTrainLength", 9);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 3, 1), 2);
        session.rebuild();

        assertTrue(session.isTurnAround(turns),
            "the fixture did not take: trains must turn round at the far end of this run");

        org.traincontrol.automationui.AutonomyChecks.Finding afterATurn =
            runInNotice();

        assertNotNull(afterATurn, "a platform set to hold nine units is four units of track from the "
            + "square trains turn round at, and the walk stops at a reversal just as it stops at a "
            + "switch (Adam, 2026-09-11) - so nine can never be reached there and nothing said so");

        assertEquals(afterATurn.getSubject(), session.pointNameForTile(berth),
            "the notice is about the wrong end of the run: " + afterATurn);

        assertEquals(afterATurn.getDetail(), 4,
            "the room is the four measured units between the turn and the platform");

        assertEquals(afterATurn.getThird(), 4, "a train setting off from the turn and longer than the four units to the"
            + " platform is refused there, and the notice does not give that figure (TDA-C10)");

        // AND THE SAME RUN WITH NOTHING TURNING AT ITS FAR END IS NOT ASKED ABOUT, because there the
        // guard walks on back through earlier edges and this edge bounds nothing.
        session.setPointProperty(turns, "canReverse", Boolean.FALSE);
        session.rebuild();

        assertFalse(session.isTurnAround(turns), "the fixture did not take: nothing should turn here now");

        assertFalse(runInNotice() != null,
            "an edge that crosses no switch and starts nowhere trains turn round bounds nothing - the "
            + "guard carries on back through the route - so the number this would quote is not the "
            + "number that would refuse the train");
    }

    /**
     * The first finding carrying a message key, or null.
     *
     * @param messageKey the check to look for
     * @return the finding, or null when nothing raised it
     */
    /** The run-in notice, whichever of its two sentences it has - the berth's, or a platform autonomy chooses (MT-555). */
    private org.traincontrol.automationui.AutonomyChecks.Finding runInNotice()
    {
        org.traincontrol.automationui.AutonomyChecks.Finding berth =
            findingFor(org.traincontrol.automationui.AutonomyChecks.RUN_IN_SHORTER_THAN_THE_BERTH);

        if (berth != null) return berth;

        // A platform's sentence, with the refusing figure where there is one (TDA-C10).
        org.traincontrol.automationui.AutonomyChecks.Finding platform = findingFor(RUN_IN_AT_A_PLATFORM);

        return platform != null ? platform : findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);
    }

    /** The platform's own sentence (MT-555), by its key so the claims compile before it exists. */
    private static final String RUN_IN_AT_A_PLATFORM = "autosetup.ui.checkRunInShorterThanThePlatform";

    /** The same, with the figure a train is refused above on the shortest way in (TDA-C10), by its key likewise. */
    private static final String RUN_IN_AT_A_PLATFORM_REFUSED = "autosetup.ui.checkRunInShorterThanThePlatformRefused";

    /**
     * The platform notice names the figure a train is refused above, where there is one (Adam, 2026-09-24, TDA-C10).
     *
     * *"Add the refusing figure where there is one."*  `Layout.whyTooLongForThisRoute` admits a train longer than the
     * room past the switch only as far as the measured route in holds (FR-087, `theApproachItselfHoldsIt`), so a train
     * that sets off from the station just behind is refused above that route's length - on his railway, a four-unit train
     * from TopR1ParkShort at TopMainR1Inter.  Here the way in from 1,1 measures 4 (2, the switch, 2) and the platform is
     * set to 6: a train of 5 stands across the switch coming from further back, and is refused coming from 1,1.
     *
     * MUTATION: leave the figure out of the finding, or leave a station copy behind out of it, and this fails.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    public void testThePlatformNoticeNamesTheFigureATrainIsRefusedAbove() throws Exception
    {
        session.open(Arrays.asList(platformBehindASwitch()));
        session.initialize("Refused");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(new TileKey("main", 1, 1), true);
        session.setPointName(new TileKey("main", 1, 1), "Behind");
        session.setStation(platform, true);
        session.setPointName(platform, "Platform");
        session.setPointProperty(platform, "maxTrainLength", 6);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding notice = findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);

        assertNotNull(notice, "the platform is set to 6 and the way in from the station behind it measures 4, so a train"
            + " of 5 from there is refused - and the notice does not say so: " + session.check());

        assertEquals(notice.getDetail(), 2, "the room past the switch is still the notice's {3}");

        java.lang.reflect.Method third = notice.getClass().getMethod("getThird");

        assertEquals(third.invoke(notice), Integer.valueOf(4), "the figure a train is refused above is the way in from"
            + " 1,1: 2, the switch's 0, and 2");

        // AND WHERE THERE IS NONE: the maximum within the shortest way in, the sentence is the one without a figure.
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.rebuild();

        assertNull(findingFor(RUN_IN_AT_A_PLATFORM_REFUSED), "a platform set to 4, whose every way in measures 4, was"
            + " given a figure no train within its maximum is refused above");

        assertNotNull(findingFor(RUN_IN_AT_A_PLATFORM), "precondition: the room past the switch is 2 and the maximum 4,"
            + " so the notice without a figure is still due");
    }

    /**
     * No refusing figure from a station no train is started at facing the platform's way (TDA-C10, found measuring the
     * frozen railway: Tunnel's notice said 3, from BottomInnerOtherside, where no route the railway runs into Tunnel
     * measures under 6).
     *
     * The square behind the platform is a station, but trains may not arrive at it from the west, so its copy heading
     * for the platform is no station and no train is started there - and nothing further back is a station either.  A
     * figure read off the square said a train setting off from it is refused above 4.
     *
     * MUTATION: take a square that is a station as one a train sets off from facing either way, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testNoRefusingFigureFromACopyNoTrainStartsAt() throws Exception
    {
        session.open(Arrays.asList(platformBehindAStationAndASwitch()));
        session.initialize("NoFigure");

        TileKey behind = new TileKey("main", 3, 1);
        TileKey platform = new TileKey("main", 7, 1);

        session.setStation(behind, true);
        session.setPointName(behind, "Behind");
        session.setBarredArrivals(behind, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.setStation(platform, true);
        session.setPointName(platform, "Platform");
        session.setPointProperty(platform, "maxTrainLength", 6);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.setTileLength(new TileKey("main", 6, 1), 2);
        session.rebuild();

        assertNotNull(runInNotice(), "precondition: the room past the switch is 2 and the maximum 6, so a run-in notice"
            + " is due: " + session.check());

        assertNull(findingFor(RUN_IN_AT_A_PLATFORM_REFUSED), "no train is started at the copy of the station behind"
            + " that heads for the platform - trains may not arrive at it from the west - and the notice gives a figure"
            + " for a train setting off from it: " + session.check());

        // THE CONTROL: the west side open, that copy is a station, and a train set off from it is refused above 4.
        session.setBarredArrivals(behind, java.util.EnumSet.noneOf(org.traincontrol.automationui.TilePorts.Side.class));
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding refused = findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);

        assertTrue(refused != null && refused.getThird() == 4, "with the west side open a train set off from the station"
            + " behind is refused above 4, the way in from it, and the notice does not say so: " + session.check());
    }

    /**
     * The refusing figure counts back over a sensor no train is started at, as the route in does (ADD-C6, ADA-C4): read
     * off one leg, it was given only where a station stood straight behind the platform - and on his railway a `...Pre`
     * sensor stands behind most of them.
     *
     * The station is two sensors back: 2 of track to the sensor, then 1, the switch and 2 to the platform.  A train set
     * off from it is refused above 5; the platform is set to 6.
     *
     * MUTATION: read the figure off the leg straight into the platform only, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheRefusingFigureCountsBackOverASensor() throws Exception
    {
        session.open(Arrays.asList(platformBehindAStationAndASwitch()));
        session.initialize("BackOverASensor");

        TileKey start = new TileKey("main", 1, 1);
        TileKey platform = new TileKey("main", 7, 1);

        session.setStation(start, true);
        session.setPointName(start, "Start");

        // A DEAD END TRAINS TURN AT: otherwise a train that came in there can never leave towards the platform, and the
        // build has no way in from it at all.
        session.setPointProperty(start, "canReverse", Boolean.TRUE);
        session.setStation(platform, true);
        session.setPointName(platform, "Platform");
        session.setPointProperty(platform, "maxTrainLength", 6);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 1);
        session.setTileLength(new TileKey("main", 6, 1), 2);
        session.rebuild();

        assertNotNull(runInNotice(), "precondition: the room past the switch is 2 and the maximum 6, so a run-in notice"
            + " is due: " + session.check());

        org.traincontrol.automationui.AutonomyChecks.Finding refused = findingFor(RUN_IN_AT_A_PLATFORM_REFUSED);

        assertTrue(refused != null && refused.getThird() == 5, "a train set off from the station two sensors back is"
            + " refused above 5 - 2, then 1, the switch and 2 - and the notice does not say so: " + session.check());
    }

    /**
     * The refusing figure over built railways written by hand, one shape each (ADA2-C1, ADA2-C2, ADA2-C3, ADD2-C5,
     * ADD2-C7): the walk that reads it off the built railway, asked directly.
     *
     * - **A loop walked whole**: a leg first reached inside a loop of sensors is not remembered as having no way in.
     * - **A turn stops it**: behind a copy trains turn at, nothing counts, as the route in stops there.
     * - **A switched-off point is no way in**: the railway runs no route through one.
     * - **No way in from the platform itself**, round a loop from another of its copies.
     * - **A way in with no length hides nothing**: the railway refuses no train on it, and the measured way's figure stands.
     * - **Never under the room**: a way in with nothing measured after its switch is not the notice's, and gives none.
     *
     * MUTATION: remember a leg a loop cut short, count on past a turn, walk through a switched-off point or from the
     * platform's own copy, take a 0 as the least, or read a way in with no room after its switch - and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testTheRefusingFigureWalksTheBuiltRailwayAsTheRouteInDoes() throws Exception
    {
        // A LOOP WALKED WHOLE: S -> A -> B -> C -> A, and B -> P1, C -> P2, every leg 1.
        java.util.Map<String, Integer> loop = figures(
            new String[][] {{"S", "station"}, {"A", ""}, {"B", ""}, {"C", ""}, {"P1", "station"}, {"P2", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "B", 1, null}, {"B", "C", 1, null}, {"C", "A", 1, null},
                {"B", "P1", 1, 1}, {"C", "P2", 1, 1}});

        assertEquals(loop.get("P1"), Integer.valueOf(3), "P1's way in from S, over A and B, is 3: " + loop);
        assertEquals(loop.get("P2"), Integer.valueOf(4), "P2's way in from S, over A, B and C, is 4 - the leg B -> C was"
            + " remembered as having no way in, because the loop cut it short while P1 was walked (ADA2-C3): " + loop);

        // A TURN STOPS IT: S -> T (5), T turns, T -> X -> P, 1 each.
        java.util.Map<String, Integer> turn = figures(
            new String[][] {{"S", "station"}, {"T", "reversing"}, {"X", ""}, {"P", "station"}},
            new Object[][] {{"S", "T", 5, null}, {"T", "X", 1, null}, {"X", "P", 1, 1}});

        assertEquals(turn.get("P"), Integer.valueOf(2), "a train that turned at T lies back no further than T, so the way in"
            + " is 2 - the walk counted on past the turn (ADD2-C5): " + turn);

        // A SWITCHED-OFF POINT IS NO WAY IN: S -> A (off) -> P, 1 each, and S2 -> P, 5.
        java.util.Map<String, Integer> off = figures(
            new String[][] {{"S", "station"}, {"A", "off"}, {"S2", "station"}, {"P", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "P", 1, 1}, {"S2", "P", 5, 1}});

        assertEquals(off.get("P"), Integer.valueOf(5), "no route runs through a switched-off point, and the figure came"
            + " over one (ADA2-C3): " + off);

        // NO WAY IN FROM THE PLATFORM ITSELF: Q is another copy of P's square; Q -> Y -> P, 1 each, and S -> P, 6.
        java.util.Map<String, Integer> self = figures(
            new String[][] {{"P", "station"}, {"Q", "station=P"}, {"Y", ""}, {"S", "station"}},
            new Object[][] {{"Q", "Y", 1, null}, {"Y", "P", 1, 1}, {"S", "P", 6, 1}});

        assertEquals(self.get("P"), Integer.valueOf(6), "a route from a platform round to itself is no journey, and the"
            + " figure came from the platform's other copy (ADA2-C3): " + self);

        // A WAY IN WITH NO LENGTH HIDES NOTHING: S -> P measured 4; T, a copy trains turn at, -> P with no length.
        java.util.Map<String, Integer> zero = figures(
            new String[][] {{"S", "station"}, {"T", "terminus"}, {"P", "station"}},
            new Object[][] {{"S", "P", 4, 2}, {"T", "P", 0, null}});

        assertEquals(zero.get("P"), Integer.valueOf(4), "the railway refuses no train on a way in with no length, and"
            + " it took the platform's figure with it (ADA2-C1): " + zero);

        // NEVER UNDER THE ROOM: S -> P measures 3 before its switch and nothing after; T turns, T -> P measures 4.
        java.util.Map<String, Integer> room = figures(
            new String[][] {{"S", "station"}, {"T", "terminus"}, {"P", "station"}},
            new Object[][] {{"S", "P", 3, -1}, {"T", "P", 4, null}});

        assertEquals(room.get("P"), Integer.valueOf(4), "a way in with nothing measured after its switch is none of the"
            + " notice's, and its 3 made the figure less than the room the notice quotes (ADA2-C2): " + room);

        // IN WHATEVER ORDER THE BUILD LISTS THE LEGS: here the leg into the platform first, the one behind it after.
        java.util.Map<String, Integer> order = figures(
            new String[][] {{"S", "station"}, {"X", ""}, {"P", "station"}},
            new Object[][] {{"X", "P", 1, 1}, {"S", "X", 1, null}});

        assertEquals(order.get("P"), Integer.valueOf(2), "the walk stopped before it had worked back to the station"
            + " behind, with the legs listed into the platform first: " + order);

        // AN ANSWERED 0 IS PASSED, adding nothing, as the railway's route in passes it (Adam, 2026-09-25); a 0 nobody
        // answered still ends the walk.  S -1-> A -0-> B -1-> P.
        java.util.Map<String, Integer> answered = figures(
            new String[][] {{"S", "station"}, {"A", ""}, {"B", ""}, {"P", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "B", 0, null, true}, {"B", "P", 1, 1}});

        assertEquals(answered.get("P"), Integer.valueOf(2), "the walk stops at a leg answered 0, where the railway's"
            + " route in counts on past it: " + answered);

        java.util.Map<String, Integer> unanswered = figures(
            new String[][] {{"S", "station"}, {"A", ""}, {"B", ""}, {"P", "station"}},
            new Object[][] {{"S", "A", 1, null}, {"A", "B", 0, null}, {"B", "P", 1, 1}});

        assertEquals(unanswered.get("P"), Integer.valueOf(1), "CONTROL: the walk counts on past a leg nobody answered: "
            + unanswered);
    }

    /**
     * The refusing figures of a built railway written by hand: points as {name, kind} - kind "station", "reversing",
     * "terminus" (a station trains turn at), "off" (switched off), "station=X" (a station that is another copy of X's
     * square), or "" - and legs as {start, end, length, room at the end or null}, with true after them for a leg whose one
     * place was answered 0.
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Integer> figures(String[][] points, Object[][] legs) throws Exception
    {
        org.json.JSONObject built = new org.json.JSONObject();
        org.json.JSONArray pointList = new org.json.JSONArray();
        org.json.JSONArray edgeList = new org.json.JSONArray();

        java.util.Map<String, TileKey> named = new java.util.LinkedHashMap<>();

        int x = 0;

        for (String[] point : points)
        {
            org.json.JSONObject json = new org.json.JSONObject();

            json.put("name", point[0]);
            json.put("station", point[1].startsWith("station") || point[1].equals("terminus"));

            if (point[1].equals("reversing")) json.put("reversing", true);
            if (point[1].equals("terminus")) json.put("terminus", true);
            if (point[1].equals("off")) json.put("active", false);

            pointList.put(json);

            named.put(point[0], point[1].startsWith("station=") ? named.get(point[1].substring(8))
                : new TileKey("main", ++x, 0));
        }

        for (Object[] leg : legs)
        {
            org.json.JSONObject json = new org.json.JSONObject();

            json.put("start", leg[0]);
            json.put("end", leg[1]);
            json.put("length", leg[2]);

            if (leg[3] != null) json.put("roomAtTheEnd", leg[3]);

            if (leg.length > 4 && Boolean.TRUE.equals(leg[4]))
            {
                org.json.JSONObject place = new org.json.JSONObject();

                place.put("at", "main:" + leg[0] + leg[1]);
                place.put("length", 0);
                place.put("answered", true);

                json.put("places", new org.json.JSONArray().put(place));
            }

            edgeList.put(json);
        }

        built.put("points", pointList);
        built.put("edges", edgeList);

        java.lang.reflect.Method read = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "refusingFigures", org.json.JSONObject.class, java.util.Map.class);

        read.setAccessible(true);

        java.util.Map<TileKey, Integer> bySquare = (java.util.Map<TileKey, Integer>) read.invoke(session, built, named);

        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, TileKey> point : named.entrySet())
        {
            if (bySquare.containsKey(point.getValue())) out.put(point.getKey(), bySquare.get(point.getValue()));
        }

        return out;
    }

    /** A sensor, a station square, a switch with its branch, and a platform, west to east. */
    private LayoutDiagram platformBehindAStationAndASwitch() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 11, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 5, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        // The branch off the switch, lying north-south so that it has a port facing the switch at all.
        page.addComponent(componentType.FEEDBACK, 5, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.getComponent(5, 1).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, 7, org.traincontrol.base.Accessory.accessoryType.SWITCH, accessoryDecoderType.MM2,
            "Switch 7", false, 0));

        page.setPageId("1");

        return page;
    }

    /**
     * The sentence with the refusing figure is in all eight languages and names it as {4} (TDA-C10).
     *
     * @throws Exception reading the bundles
     */
    @Test
    public void testTheRefusingFigureIsInEveryLanguage() throws Exception
    {
        java.io.File[] bundles = new java.io.File("src/org/traincontrol/resources").listFiles(
            (dir, name) -> name.startsWith("messages") && name.endsWith(".properties"));

        assertTrue(bundles != null && bundles.length == 8, "precondition: the eight message bundles are not where"
            + " this looks for them");

        for (java.io.File bundle : bundles)
        {
            java.util.Properties read = new java.util.Properties();

            try (java.io.InputStream in = new java.io.FileInputStream(bundle))
            {
                read.load(in);
            }

            String sentence = read.getProperty(RUN_IN_AT_A_PLATFORM_REFUSED);

            assertNotNull(sentence, bundle.getName() + " has no " + RUN_IN_AT_A_PLATFORM_REFUSED);

            assertTrue(sentence.contains("{2}") && sentence.contains("{3}") && sentence.contains("{4}"), bundle.getName()
                + ": the sentence names the maximum {2}, the room {3} and the refusing figure {4}: " + sentence);
        }
    }

    /** How many findings of this key the setup gives about this square. */
    private int findingsAbout(String messageKey, TileKey square)
    {
        int seen = 0;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey) && square.equals(finding.getTile())) seen++;
        }

        return seen;
    }

    /**
     * `platformBehindASwitch`, and track on past the platform to a second sensor - so the platform has an approach
     * from each side, one of which can be barred.
     */
    private LayoutDiagram platformWithTwoApproaches() throws IOException
    {
        LayoutDiagram page = platformBehindASwitch();

        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        return page;
    }

    private org.traincontrol.automationui.AutonomyChecks.Finding findingFor(String messageKey)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) return finding;
        }

        return null;
    }

    /**
     * A side trains may not arrive by is not an approach, and its run-in is not compared with the maximum (MT-552).
     *
     * Adam, 2026-09-24: *"RampDown and BottomMainPost should not have the ... warning as worded, since both sides are
     * measured (and moreover, they only accept arrivals from one side)."*  Every arriving edge was judged, the barred
     * one too - and no train comes in that way, so nothing about its track can refuse one.
     *
     * The control first: with both sides open the short side is reported, so the fixture can say it.
     *
     * MUTATION: judge every arriving edge in `runInsShorterThanTheBerth` again, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testABarredApproachIsNotJudgedForItsRunIn() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("Barred");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertNotNull(runInNotice(), "precondition: two units of track between the platform and the switch on its west"
            + " side, and a maximum of 4, is not reported - so this fixture cannot show the notice at all");

        session.setBarredArrivals(platform, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertNull(runInNotice(), "the platform takes no arrivals from the west, and the west side's run-in is still"
            + " compared with its maximum - Adam, MT-552: \"they only accept arrivals from one side\"");
    }

    /**
     * The half-measured warning says how many squares have no length, in every language (found with MT-552).
     *
     * A finding renders {0} as the square, {1} as its subject - here the station's name - {2} as its count and {3} as its
     * detail.  The sentence put {1} where it meant the count, so Adam's list read *"BottomMainPost can refuse trains ...
     * BottomMainPost squares leading to it still have no length"*.
     *
     * MUTATION: put {1} back in any one language, and this fails.
     *
     * @throws Exception reading the bundles
     */
    @Test
    public void testTheHalfMeasuredWarningCountsTheSquares() throws Exception
    {
        String key = org.traincontrol.automationui.AutonomyChecks.HALF_MEASURED_APPROACH;

        java.io.File[] bundles = new java.io.File("src/org/traincontrol/resources").listFiles(
            (dir, name) -> name.startsWith("messages") && name.endsWith(".properties"));

        assertTrue(bundles != null && bundles.length == 8, "precondition: the eight message bundles are not where"
            + " this looks for them");

        for (java.io.File bundle : bundles)
        {
            java.util.Properties read = new java.util.Properties();

            try (java.io.InputStream in = new java.io.FileInputStream(bundle))
            {
                read.load(in);
            }

            String sentence = read.getProperty(key);

            assertNotNull(sentence, bundle.getName() + " has no " + key);

            assertTrue(sentence.contains("{2}") && !sentence.contains("{1}"), bundle.getName() + ": the half-measured"
                + " warning names the station where it means the number of squares with no length - {1} is the"
                + " subject, {2} the count: " + sentence);
        }
    }

    /**
     * A side trains may not stop on is still asked to be measured for a turn there (Adam, 2026-09-24, TDA-C8).
     *
     * *"Arrivals THAT STOP THERE should only be allowed from the configured side(s).  Turning shouldn't need to factor
     * this in, since the former would govern the behavior."*  A barred side keeps its turning copy, so a train may come
     * in that way and turn - and the room rule judges that turn over the track after the last switch, which is what the
     * reversal notice asks to be measured.  `e09fe989` skipped the barred side here along with the two berth checks,
     * which are right to: nothing stops there.
     *
     * MUTATION: skip the barred side in `reversalsWithoutLength` again, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testABarredApproachIsStillAskedToBeMeasuredForATurn() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("BarredTurn");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "canReverse", Boolean.TRUE);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 6, 1), 1);
        session.rebuild();

        assertTrue(session.isTurnAround(platform), "precondition: trains may not turn at the platform");

        assertTrue(session.reversalsWithoutLength().containsKey(platform), "precondition: the west side's track after"
            + " the switch, measured not at all, is not asked for - so this fixture cannot show the notice at all");

        session.setBarredArrivals(platform, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertTrue(session.reversalsWithoutLength().containsKey(platform), "the platform takes no train that STOPS"
            + " from the west, and a train may still come in that way and turn - yet the west side's track is no longer"
            + " asked for (Adam, TDA-C8: \"Turning shouldn't need to factor this in\"): " + session.reversalsWithoutLength());
    }

    /**
     * A side trains may not arrive by is not an approach, and its unmeasured track is not a half-measured one (MT-552).
     *
     * The same note: RampDown and BottomMainPost were told they *"can refuse trains that would otherwise fit"* for the
     * side nothing arrives by.
     *
     * MUTATION: judge every arriving edge in `stationsWithAHalfMeasuredApproach` again, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testABarredApproachIsNotJudgedAsHalfMeasured() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("BarredHalf");

        TileKey berth = new TileKey("main", 5, 1);

        session.setStation(berth, true);
        session.setAutoDestination(berth, false);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "precondition: a berth whose west"
            + " approach is measured in part is not reported - so this fixture cannot show the warning at all");

        session.setBarredArrivals(berth, java.util.EnumSet.of(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "the berth takes no arrivals from"
            + " the west, and it is still warned that the west approach is half measured - Adam, MT-552: \"they only"
            + " accept arrivals from one side\"");
    }

    /**
     * At a station autonomy may choose, a train longer than the run-in stands across the switch and blocks the railway
     * behind it; at a parking berth it is refused - and the notice says which (MT-555).
     *
     * Adam, 2026-09-24: *"Notices work, but they are wrong.  'so a train longer than x is refused' - 'so a train
     * longer than x may block other parts of the layout...'"*  `Layout.whyTooLongForThisRoute` refuses it at a berth
     * and, at a station autonomy may choose, admits it as far as the measured route in holds (FR-087).
     *
     * MUTATION: word both kinds of station the same, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAPlatformIsToldALongTrainMayBlockAndABerthThatItIsRefused() throws Exception
    {
        session.open(Arrays.asList(platformBehindASwitch()));
        session.initialize("Wording");

        TileKey platform = new TileKey("main", 5, 1);

        session.setStation(platform, true);
        session.setPointProperty(platform, "maxTrainLength", 4);
        session.setTileLength(new TileKey("main", 2, 1), 2);
        session.setTileLength(new TileKey("main", 4, 1), 2);
        session.rebuild();

        assertTrue(session.isAutoDestination(platform), "precondition: a new station is not one autonomy may choose");

        assertEquals(findingsAbout(RUN_IN_AT_A_PLATFORM, platform), 1, "a station autonomy may choose is not told that a"
            + " train longer than its run-in may block the railway - Adam, MT-555: \"so a train longer than x may block"
            + " other parts of the layout\"");

        assertEquals(findingsAbout(org.traincontrol.automationui.AutonomyChecks.RUN_IN_SHORTER_THAN_THE_BERTH, platform),
            0, "a station autonomy may choose is told a train longer than its run-in is refused, and it is admitted");

        assertFalse(org.traincontrol.util.I18n.t(RUN_IN_AT_A_PLATFORM).contains("refused"), "the platform's sentence"
            + " still says the train is refused: " + org.traincontrol.util.I18n.t(RUN_IN_AT_A_PLATFORM));

        session.setAutoDestination(platform, false);
        session.rebuild();

        assertEquals(findingsAbout(org.traincontrol.automationui.AutonomyChecks.RUN_IN_SHORTER_THAN_THE_BERTH, platform),
            1, "a parking berth is not told that a train longer than its run-in is refused, which it is");
    }

    /**
     * A home on a parking berth every train turns at is not warned about (MT-552).
     *
     * Adam, 2026-09-24: *"the 'every train has to turn around' warning for TopMainR0Park is moot since it is a parking
     * berth.  Non-reversible trains can still be backed in there."*
     *
     * The control first: at a station autonomy may choose, the warning stands.
     *
     * MUTATION: warn about every home a train must turn at, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAHomeOnAParkingBerthIsNotWarnedAboutTurning() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Home");

        TileKey berth = new TileKey("main", 4, 1);

        session.setStation(berth, true);
        session.setPointProperty(berth, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);
        session.setHome(berth, "Test Loc");
        session.rebuild();

        String key = org.traincontrol.automationui.AutonomyChecks.HOME_NEEDS_REVERSIBLE;

        assertEquals(findingsAbout(key, berth), 1, "precondition: a home on a station every train turns at, which"
            + " autonomy may choose, is not warned about - so this fixture cannot show the warning at all");

        session.setAutoDestination(berth, false);
        session.rebuild();

        assertEquals(findingsAbout(key, berth), 0, "a home on a parking berth every train turns at is warned that a"
            + " locomotive will be driven in nose first - Adam, MT-552: \"moot since it is a parking berth\"");
    }

    /**
     * A station set to No - Nothing Can Pass is said to be, once - not reported as unreachable (FR-101).
     *
     * Adam, 2026-09-24: *"these show 'no train can reach &lt;point&gt; from any other station....check the direction'.
     * Update the error message to say that is marked for nothing to be able to pass, user to validate if
     * intentional."*
     *
     * MUTATION: let the reachability sentences report a closed station again, or leave out the new one, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAStationNothingCanPassIsSaidToBeClosed() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("Closed");

        TileKey platform = new TileKey("main", 5, 1);
        TileKey other = new TileKey("main", 1, 1);

        session.setStation(platform, true);
        session.setStation(other, true);
        session.setStation(new TileKey("main", 7, 1), true);
        session.rebuild();

        String unreachable = org.traincontrol.automationui.AutonomyChecks.STATION_UNREACHABLE;
        String closed = "autosetup.ui.checkStationClosed";

        assertEquals(findingsAbout(unreachable, platform), 0, "precondition: in service, the platform is already"
            + " reported as one no train can reach");

        session.setPointProperty(platform, "active", Boolean.FALSE);
        session.rebuild();

        assertEquals(findingsAbout(closed, platform), 1, "a station set to No - Nothing Can Pass is not said to be -"
            + " Adam, FR-101: \"say that is marked for nothing to be able to pass, user to validate if intentional\"");

        assertEquals(findingsAbout(unreachable, platform)
            + findingsAbout(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING, platform)
            + findingsAbout(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED, platform), 0,
            "a station set to No - Nothing Can Pass is still reported as one no train can reach or leave, with advice"
            + " about the directions");
    }

    /**
     * A platform with a switch in its approach, which is Adam's own example of a station whose track is
     * finer than one number: *"a station of length 4 may have two segments of tracks on either side of a
     * switch, of length 2."*
     *
     * The switch needs an address wired onto it or autonomy will not route over it, and its branch needs
     * somewhere to go or it is a dead end rather than a fork.
     */
    private LayoutDiagram platformBehindASwitch() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        // The branch off the switch, lying north-south so that it has a port facing the switch at all.
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.getComponent(3, 1).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, 7, org.traincontrol.base.Accessory.accessoryType.SWITCH, accessoryDecoderType.MM2,
            "Switch 7", false, 0));

        page.setPageId("1");

        return page;
    }

    /**
     * A station every train must turn round at may have only one way in (MT-361).
     *
     * Adam, 2026-09-12, on MT-361: *"terminuses (stations that must reverse) are currently allowed to have
     * ingress from two sides.  Make this be an autonomy ERROR that the user has to fix."*
     *
     * A terminus is the end of the line. A square marked "must turn round" is emitted as one Point per
     * arrival side, each leaving the way it came - so with two ways in it becomes two termini, a train
     * sent to either leaves back the way it arrived, and the track out of the other side is never used.
     *
     * **An ERROR is the strongest thing this file can say**: `errorCount() > 0` refuses to start autonomy
     * at all. The severity is asserted here rather than assumed, because that is the whole of what he
     * asked for - a warning would let the configuration run.
     *
     * **And the three remedies the message names all clear it**, which is what makes an error fair: bar
     * one way in, change the square to "may turn round", or stop calling it a station. The last is the
     * interesting one - a must-turn square that is not a station is emitted as a plain reversing point,
     * and a reversing point with two ways in is an ordinary mid-layout turn-round, the `BACK_mid` of
     * `core.testNonReversibleTrains`. A rule that reported those would fire on railways that are right.
     *
     * MUTATIONS: dropping the `isStation` test reports the reversing point; dropping the barred-arrival
     * test leaves the first remedy unable to clear it; using `isTurnAround` instead of
     * `mandatoryTurnTiles` reports the may-turn square, which is the ordinary through station.
     */
    @Test
    public void testATerminusWithTwoWaysInIsAnError() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));
        session.initialize("Termini");

        TileKey middle = new TileKey("main", 3, 1);

        session.setStation(middle, true);
        session.setPointProperty(middle, "mustReverse", Boolean.TRUE);
        session.rebuild();

        assertTrue(session.isMustTurnAround(middle),
            "the fixture did not take: " + middle + " has to be a square every train must turn at");

        assertTrue(session.getStore().isStation(middle),
            "the fixture did not take: " + middle + " has to be a station");

        assertEquals(session.terminiWithTwoWaysIn().get(middle), Integer.valueOf(2),
            "the middle sensor of this page has track on both sides, so two sides reach it - and the"
            + " rule counted " + session.terminiWithTwoWaysIn().get(middle) + ".  Without two this test"
            + " is about a fixture that cannot show the fault: " + session.terminiWithTwoWaysIn());

        assertTrue(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "a station where every train must turn round has track reaching it from both sides and"
            + " nothing objected. It is two termini, each sending its trains back the way they came,"
            + " and the track out of the far side is never used (Adam, MT-361)");

        // AN ERROR, NOT A WARNING, which is the whole of what was asked: `errorCount() > 0` is what
        // refuses to start autonomy, and a warning would let this configuration run.
        assertEquals(severityOf(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            org.traincontrol.automationui.AutonomyChecks.Severity.ERROR,
            "the finding is not an ERROR, so autonomy still starts on a railway carrying it - which is"
            + " exactly what Adam asked to stop: \"make this be an autonomy ERROR that the user has to"
            + " fix\"");

        assertTrue(session.errorCount() > 0,
            "the finding is an error and `errorCount()` does not count it, so every door that asks that"
            + " question - the Start button, the refusal - would still offer to run this setup");

        // REMEDY ONE: bar one way in with the arrows.
        session.setBarredArrivals(middle,
            java.util.Collections.singleton(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "one way in is barred, so this square has one way in - and the error the message tells the"
            + " operator to fix that way is still there, which would leave them nothing to do");

        // REMEDY TWO: let trains run through it instead.
        session.setBarredArrivals(middle, java.util.Collections.<org.traincontrol.automationui.TilePorts.Side>emptySet());
        session.setPointProperty(middle, "mustReverse", Boolean.FALSE);
        session.setPointProperty(middle, "canReverse", Boolean.TRUE);
        session.rebuild();

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "a station where trains MAY turn round is reported as a terminus with two ways in. That is"
            + " the ordinary station a train can either run through or reverse in, which is what \"may\""
            + " is for - and the rule has taken the operator's choice away");

        // REMEDY THREE, and the square this rule must leave alone: a must-turn square that is not a
        // station is a reversing point, and two ways into one of those is a mid-layout turn-round.
        session.setPointProperty(middle, "canReverse", Boolean.FALSE);
        session.setPointProperty(middle, "mustReverse", Boolean.TRUE);
        session.setStation(middle, false);
        session.rebuild();

        assertFalse(session.getStore().isStation(middle),
            "the fixture did not take: the square has to stop being a station for this claim");

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "a square that turns every train round and is NOT a station is reported as a terminus. It is"
            + " emitted as a plain reversing point, and a reversing point with track on both sides is an"
            + " ordinary mid-layout turn-round - `core.testNonReversibleTrains` is built on one");
    }

    /**
     * The severity of the first finding carrying a key, or null.
     *
     * @param messageKey the check
     * @return its severity, or null when nothing raised it
     */
    private org.traincontrol.automationui.AutonomyChecks.Severity severityOf(String messageKey)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) return finding.getSeverity();
        }

        return null;
    }

    /**
     * And the station-capacity notice is gated the same way (SVN-C3).
     *
     * The two halves of the length rule are `placedTrainsWithoutLength` and `stationsWithoutMaxLength`,
     * and only the reversal notice ever got Adam's condition: *"if any other track length is set
     * anywhere"*.  So a railway that models no lengths at all had every one of its stations listed -
     * thirty of them on his - beside the real problems, on the list this feature's own javadoc twice
     * says is made useless by exactly that.
     *
     * The same fixture, asked twice, with one length recorded somewhere else entirely between them.
     *
     * MUTATION this catches: removing the `measuresAnyTrack()` gate reports the station in both halves.
     */
    @Test
    public void testTheStationCapacityNoticeWaitsForALayoutThatMeasures() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Capacity");

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);
        session.rebuild();

        assertTrue(session.getStore().isStation(station),
            "the fixture did not take: this square must be a station");

        // NOTHING MEASURED ANYWHERE: no notice.
        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(station)),
            "a layout that records no lengths at all was asked for a station capacity, which is a "
            + "notice about something nobody on that railway is trying to do");

        // One length, on a different square, and the layout is one that measures.
        session.setTileLength(new TileKey("main", 2, 1), 7);

        assertTrue(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(station)),
            "the layout measures track now, and a station with no capacity was not asked about - so "
            + "the half of the length rule that stops a train being sent somewhere too short for it "
            + "has nothing to work with and nothing says so");
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

        // A SWITCHED-OFF STATION ARRIVES ON, AND NOT ONE AUTONOMY CHOOSES (REG-B1, Adam 2026-09-24: "translate as on but
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
     * explains why it must not". It appeared, it was argued over as TST-B15, and it stays.
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
     * The second was raised as TST-B15 on the reading that it silently re-excludes a page the user had
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
     * CLOSED, AND ENABLED (TST-B15, Adam 2026-09-11). Asked which way it should go, he took the first:
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
     * A placement naming a locomotive this database does not have is refused, not written in.
     *
     * The running model used to invalidate the WHOLE layout for one such name, so an old graph naming a
     * locomotive since renamed or deleted imported cleanly and then produced a setup that refused to
     * open, reported as a locomotive problem with nothing to say the import put it there.  Since
     * AMR-C3 (Adam, 2026-09-16: *"drop the train and keep the rest."*) that loader drops the placement
     * with a log line too - `core.testHomeStaging
     * .testAPlacementForALocomotiveNotInTheDatabaseDropsOnlyThePlacement` - so the two doors now agree
     * about the same file, which is why this test is about the importer NAMING what it dropped.
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
        // A SQUARE THAT GENUINELY BECOMES TWO POINTS (TCX-B9).
        //
        // This used to run on `pageOnDisk()`, whose sensors are at the two ENDS of a straight run. A
        // square with one arrival side emits one Node, so `copies` held one member, the nested loop
        // below only ever evaluated `sameSquare(x, x)`, and that short-circuits on identity - the half
        // of this test named in its own title could not fail whatever the station index did.
        //
        // The middle sensor here has track on both sides, so trains can arrive at it either way and it
        // is emitted twice.
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey first = new TileKey("main", 3, 1);
        TileKey second = new TileKey("main", 1, 1);

        session.getStore().setStation(first, true);
        session.setPointName(first, "BottomMainB");

        session.getStore().setStation(second, true);
        session.setPointName(second, "BottomMainC");

        session.rebuild();

        java.util.List<String> copies = session.pointNamesFor(session.pointNameForTile(first));

        assertTrue(copies.size() >= 2,
            "precondition: this needs a square that became SEVERAL Points, and it got " + copies.size()
            + " - " + copies + ".  With one copy the loop below compares a name with itself, which "
            + "sameSquare answers by identity, and the test proves nothing");

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

        assertTrue(points.length() > 0,
            "precondition: nothing was scanned, so the loop below could not have found the bad pair "
            + "even if it were still there");

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
     *
     * sameSquare/squareOf are the helper, not the rule: the rule the two menus actually call is
     * distinctDestinations (StationIndex.java:458), which has no test of its own anywhere in the suite.
     * This drives that method directly, not just the primitives underneath it.
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

        // MUTATION this catches: delete `if (sameSquare(from, to)) continue;` at
        // StationIndex.java:474 - both loopsBack and genuineJourney would then survive, offering the
        // train a journey back to the platform it is standing on.
        List<List<org.traincontrol.automation.Edge>> kept =
            index.distinctDestinations(Arrays.asList(loopsBack, genuineJourney));

        assertEquals(kept.size(), 1, "a path back to where it started must be dropped");
        assertEquals(kept.get(0), genuineJourney,
            "the path that survived should be the genuine journey, not the loop");
    }

    /**
     * A station reachable from two Points of the same square is offered once, not once per copy.
     *
     * Both copies answer to a different name, since the copies are what stop them being confused for
     * one another everywhere else - so the dedupe here has to go by SQUARE, and this is the only test
     * anywhere in the suite that drives distinctDestinations against a square that actually splits.
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

        // MUTATION this catches: key the "seen" set in distinctDestinations by `to` (the point name)
        // instead of by square - both copies would then be kept, and the menu would list one station
        // twice, which is what the javadoc there calls "the same destination listed twice".
        List<List<org.traincontrol.automation.Edge>> kept =
            index.distinctDestinations(Arrays.asList(viaFirstCopy, viaSecondCopy));

        assertEquals(kept.size(), 1, "two Points of one platform must be offered as one destination");
        assertEquals(kept.get(0), viaFirstCopy, "the first copy reached should be the one kept");

        // Control: a genuinely different destination is not swallowed by the same dedupe - proves the
        // check above can detect a SECOND distinct destination, not just fail to detect duplicates.
        org.traincontrol.automation.Point somewhereElse =
            new org.traincontrol.automation.Point("Somewhere else", false, null);
        List<org.traincontrol.automation.Edge> aThirdPath =
            Arrays.asList(new org.traincontrol.automation.Edge(origin, somewhereElse));

        assertEquals(index.distinctDestinations(Arrays.asList(viaFirstCopy, aThirdPath)).size(), 2,
            "two genuinely different destinations must both survive");
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
     *
     * Driven through session.snapshotPage/restorePage, which is what LayoutEditor.java:325-354 actually
     * calls.  captionsOnPage/restoreCaptionsOnPage looked like the same feature - same javadoc claim,
     * same shape - but were a second, parallel implementation that round-tripped against themselves and
     * would have stayed green even if AutonomyCompanionStore.kept() stopped carrying captions through
     * the real snapshot.  restoreCaptionsOnPage was deleted as uncalled (REL-C15); captionsOnPage has
     * one caller, LayoutEditor.forgetCaptionsOutsideThePage, which wants the keys and not the round
     * trip (VD9-C16).  MUTATION this catches: remove "captions" from kept()
     * (AutonomyCompanionStore.java:4354) - restorePage then puts the plaque's TILE back with no name on
     * it, which is the defect this class exists to prevent.
     */
    @Test
    public void testAPagesCaptionsRoundTripThroughASnapshot() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);
        TileKey plaque = new TileKey("main", 3, 2);

        session.setStation(station, true);
        session.setCaption(plaque, station);

        assertEquals(session.getCaptionTarget(plaque), station, "precondition: the plaque is up");

        java.util.Map<String, Object> before = session.snapshotPage("main");

        // what deleting the captioned square does
        session.forgetCaptionsAt(station);

        assertNull(session.getCaptionTarget(plaque), "precondition: and then it is not");

        session.restorePage("main", before);

        assertEquals(session.getCaptionTarget(plaque), station,
            "undo brought the platform back without its name");
    }

    /**
     * Restoring a snapshot removes captions added since it was taken.
     *
     * Putting the old ones back is only half of it: a caption placed after the snapshot has to go, or
     * undo leaves the page with both, which is a state the user was never in.
     *
     * Same fix as above: session.snapshotPage/restorePage, not the untested parallel pair.
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

        java.util.Map<String, Object> before = session.snapshotPage("main");

        session.setCaption(later, station);

        assertNull(session.getCaptionTarget(first),
            "precondition: one station, one caption - the second move took the first down");

        session.restorePage("main", before);

        assertEquals(session.getCaptionTarget(first), station);

        assertNull(session.getCaptionTarget(later),
            "the caption added after the snapshot survived the undo");
    }

    /**
     * A snapshot of one page leaves the other pages alone.
     *
     * The editor works on one page, so restoring every caption in the setup would undo work done
     * somewhere it was never looking.  Driven through snapshotPage/restorePage for the same reason as
     * the two tests above.  captionsOnPage's page filter has one caller in src/ -
     * LayoutEditor.forgetCaptionsOutsideThePage - and it is not an undo path (VD9-C16).
     */
    @Test
    public void testACaptionSnapshotIsPerPage() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));

        TileKey station = new TileKey("main", 3, 1);
        TileKey plaque = new TileKey("main", 3, 2);

        session.setStation(station, true);
        session.setCaption(plaque, station);

        assertEquals(session.getCaptionTarget(plaque), station, "precondition: the plaque is up");

        // MUTATION this catches: make snapshotPage/restorePage ignore the page argument (act on every
        // page's captions) - restoring an unrelated, empty page's snapshot would then wipe the caption
        // on "main", which is exactly the cross-page undo this test exists to rule out.
        java.util.Map<String, Object> elsewhereSnapshot = session.snapshotPage("elsewhere");

        session.restorePage("elsewhere", elsewhereSnapshot);

        assertEquals(session.getCaptionTarget(plaque), station,
            "restoring an unrelated page's snapshot reached into a page nobody was editing");
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
     * A signal on an excluded page is still reported gone (REL-B1).
     *
     * **The two sides of a pairing are not symmetric, and one filter treated them as if they were.**
     * `SVN-C6` added an excluded-page filter to `signalsThatAreGone` because a pairing whose STATION is
     * on a page that is switched off is not in play, and a warning about it is noise. The same filter
     * was applied to the SIGNAL, and that silenced a warning that was true: `protectingSignalNames`
     * resolves signals through the graph, which does not carry an excluded page, so a signal on one
     * really IS dropped from the built configuration - the platform really IS unprotected, and the
     * check exists to say so.
     *
     * MUTATION this catches: putting the signal-side `continue` back.
     */
    @Test
    public void testASignalOnAnExcludedPageIsStillReportedGone() throws Exception
    {
        LayoutDiagram elsewhere = new LayoutDiagram("second", 6, 4, null, null);

        // A REAL SIGNAL, because the check asks whether the tile resolves to an accessory - a feedback
        // tile on another page would report gone with both pages in play and prove nothing.
        elsewhere.addComponent(componentType.SIGNAL, 1, 1, 0, 0, 31, 0, accessoryDecoderType.MM2, null);
        elsewhere.addComponent(componentType.FEEDBACK, 2, 1, 0, 0, 10, 21, accessoryDecoderType.MM2, null);

        // Wired, as parsing a real layout does: without an accessory the signal has no address to
        // command and reports gone whatever page it is on, which would make the precondition below
        // pass for the wrong reason.
        wire(elsewhere, 1, 1, 31);

        elsewhere.setPageId("2");

        session.open(Arrays.asList(pageOnDisk(), elsewhere));

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("second", 1, 1);

        session.setStation(station, true);
        session.setProtectingSignal(station, signal);
        session.rebuild();

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.SIGNAL_GONE),
            "precondition: with both pages in play this pairing resolves, so a warning here would be "
            + "about something else");

        // The page the SIGNAL is on goes out of service.  The station is still in play.
        session.setPageExcluded("second", true);
        session.rebuild();

        assertTrue(hasFinding(org.traincontrol.automationui.AutonomyChecks.SIGNAL_GONE),
            "the signal protecting a station in play is on a page that is switched off, so it is not "
            + "in the built configuration and that platform has no protection - and nothing said so");
    }

    /**
     * The station-capacity notice does not need a measured tile (REL-C2).
     *
     * `SVN-C3` gated this check on `measuresAnyTrack()`, which is Adam's condition for the REVERSAL
     * notice - and there the gate and the notice are about the same data, tile lengths. Station
     * capacity is not that data: `Point.validateTrainLength` compares the station's typed maximum
     * against the locomotive's own authored length, and no tile length enters into it.
     *
     * So the gate was a precondition that lost its subject on the way over, and somebody who authors
     * train lengths and station maxima but has never measured track got no notices at all - about the
     * feature they are actively using.
     *
     * MUTATION this catches: putting `measuresAnyTrack()` back in place of `modelsAnyLength()`.
     */
    @Test
    public void testTheStationCapacityNoticeDoesNotNeedAMeasuredTile() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));
        session.initialize("Capacity");

        TileKey withMaximum = new TileKey("main", 1, 1);
        TileKey without = new TileKey("main", 4, 1);

        session.setStation(withMaximum, true);
        session.setStation(without, true);
        session.rebuild();

        // NOTHING MEASURED, AND NO MAXIMUM ANYWHERE: this railway does not model lengths.
        assertTrue(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH).isEmpty(),
            "a railway that models no lengths at all was asked for station capacities, which is a "
            + "notice about something nobody on that railway is trying to do: "
            + subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH));

        // One station is given a maximum.  No track is measured, and none needs to be.
        session.setPointProperty(withMaximum, "maxTrainLength", 8);
        session.rebuild();

        assertTrue(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(without)),
            "this railway models lengths - a station carries a maximum - and the station that does "
            + "not was never asked about.  The gate was asking whether any TRACK is measured, which "
            + "station capacity does not use: "
            + subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH));

        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH)
                .contains(session.pointNameForTile(withMaximum)),
            "the station that HAS a maximum is being asked for one");
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
     * A station's entry guard survives a save, is kept apart from its protecting signals, and goes when the station
     * does (FR-096).
     */
    @Test
    public void testAnEntryGuardIsKeptApartAndForgottenWithTheStation() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey north = new TileKey("main", 2, 1);
        TileKey south = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setEntrySignals(station, Arrays.asList(north, south));

        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(pageOnDisk()));

        assertEquals(reopened.getEntrySignals(station), Arrays.asList(north, south), "the entry guard did not survive the file");

        assertTrue(reopened.getProtectingSignals(station).isEmpty(),
            "the entry guard came back as protecting signals - two lists thrown at different moments");

        reopened.setStation(station, false);

        assertTrue(reopened.getEntrySignals(station).isEmpty(), "a plain point kept an entry guard");
    }

    /**
     * Every entry-guard signal reaches the built configuration, under its own key (FR-096).
     */
    @Test
    public void testEveryEntryGuardSignalReachesTheBuiltConfiguration() throws Exception
    {
        session.open(Arrays.asList(pageWithTwoSignals()));

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);
        session.setEntrySignals(station, Arrays.asList(new TileKey("main", 2, 1), new TileKey("main", 3, 1)));

        assertEquals(session.entrySignalNames().get(station).size(), 2,
            "one of the two entry-guard signals was lost on the way to the accessory names");

        org.json.JSONArray points = new org.json.JSONObject(session.buildConfiguration()).getJSONArray("points");

        boolean seen = false;

        for (int at = 0; at < points.length(); at++)
        {
            org.json.JSONObject point = points.getJSONObject(at);

            assertFalse(point.has("protectingSignal"), "the entry guard was emitted as a protecting signal");

            if (!point.has("entrySignal")) continue;

            seen = true;

            assertEquals(point.getJSONArray("entrySignal").length(), 2, "the built configuration lost an entry-guard signal");
        }

        assertTrue(seen, "nothing in the built configuration carries an entry guard");
    }

    /**
     * A run of track with a station at one end and two signals beside it.
     */
    /**
     * A station's entry guard is never its exit guard, whichever is set first (AUT-C2).
     *
     * Adam, 2026-09-24: *"make sure the entry guard can never be the same as the exit guard.  otherwise, it's up to the
     * user to set it up right."*  One signal on both lists is thrown red on arrival and then set by the platform's
     * occupancy, so neither guard means what it was paired for.
     *
     * MUTATION: let the setters take a signal the other list holds, and this fails.
     */
    @Test
    public void testAStationsEntryGuardIsNeverItsExitGuard() throws Exception
    {
        session.open(Arrays.asList(pageWithTwoSignals()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey first = new TileKey("main", 2, 1);
        TileKey second = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setProtectingSignals(station, Arrays.asList(first));
        session.setEntrySignals(station, Arrays.asList(first, second));

        assertEquals(session.getEntrySignals(station), Arrays.asList(second), "the station's exit guard was paired as"
            + " its entry guard too (AUT-C2)");

        session.setProtectingSignals(station, Arrays.asList(first, second));

        assertEquals(session.getProtectingSignals(station), Arrays.asList(first), "the station's entry guard was paired"
            + " as its exit guard too (AUT-C2)");
    }

    /**
     * A file that carries one signal on both lists anyway is warned about (AUT-C2).
     *
     * Loading a setup does not go through the setters, so a file written before the rule - or by hand - can still hold
     * it.  The editor says so rather than letting it run.
     *
     * MUTATION: drop the check, and this fails.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAGuardOnBothListsIsWarnedAbout() throws Exception
    {
        session.open(Arrays.asList(pageWithTwoSignals()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 1);

        session.setStation(station, true);
        session.setProtectingSignals(station, Arrays.asList(signal));

        // AS A LOADED FILE HAS IT, past the setter.
        java.lang.reflect.Field field = org.traincontrol.automationui.AutonomyCompanionStore.class.getDeclaredField("entrySignals");
        field.setAccessible(true);
        ((java.util.Map<TileKey, java.util.List<TileKey>>) field.get(session.getStore()))
            .put(station, new java.util.ArrayList<>(Arrays.asList(signal)));

        boolean warned = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if ("autosetup.ui.checkGuardIsBoth".equals(finding.getMessageKey()))
            {
                warned = true;

                assertEquals(finding.getSeverity(), org.traincontrol.automationui.AutonomyChecks.Severity.WARNING,
                    "a signal guarding both ways into and out of one station is not a warning");
            }
        }

        assertTrue(warned, "a setup with one signal as a station's entry guard and its exit guard is not warned about"
            + " (AUT-C2)");
    }

    /**
     * A guard no way into its station passes is noticed, and one on the approach is not (AUT-C2).
     *
     * Adam: *"if the guard signal is not on a path leading to the chosen station, we can add notice to the autonomy
     * editor."*  The approach is the track a train runs from the last station it could have stopped at.
     *
     * MUTATION: call every guard on the way in, and the first half fails; call none, and the second does.
     */
    @Test
    public void testAGuardOffTheWayInIsNoticed() throws Exception
    {
        session.open(Arrays.asList(pageWithAGuardOffTheLine()));

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);

        session.setEntrySignals(station, Arrays.asList(new TileKey("main", 2, 3)));

        assertTrue(noticesAGuardOffTheWayIn(session), "an entry guard on track no train reaching the station runs over"
            + " is not noticed (AUT-C2)");

        session.setEntrySignals(station, Arrays.asList(new TileKey("main", 2, 1)));

        assertFalse(noticesAGuardOffTheWayIn(session), "an entry guard on the station's own approach is noticed as"
            + " off the way in");

        session.setEntrySignals(station, null);
        session.setProtectingSignals(station, Arrays.asList(new TileKey("main", 2, 3)));

        assertTrue(noticesAGuardOffTheWayIn(session), "an exit guard on track no train reaching the station runs over"
            + " is not noticed (AUT-C2)");
    }

    private static boolean noticesAGuardOffTheWayIn(AutonomySession session)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if ("autosetup.ui.checkGuardOffTheWayIn".equals(finding.getMessageKey())) return true;
        }

        return false;
    }

    /**
     * The two-signal line, and a second line beside it carrying a signal of its own.
     */
    private LayoutDiagram pageWithAGuardOffTheLine() throws IOException
    {
        LayoutDiagram page = pageWithTwoSignals();

        page.addComponent(componentType.FEEDBACK, 1, 3, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SIGNAL, 2, 3, 0, 0, 23, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 3, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        wire(page, 2, 3, 23);

        return page;
    }

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
    /**
     * The bulk clear touches exactly the squares the findings call homes (WK3-C3).
     *
     * **Four walks of one map, and nothing made them agree.** `homeTiles()` fed the findings the
     * diagram shows; `tilesWithAHome()` decided what the Clear All Home Locomotives button touches;
     * `homesElsewhere()` added one filter; `shutTiles()` made a fourth. Each carried its own copy of
     * "is there an active configuration, does it have points, is this an object, parse the key".
     *
     * The cost of drift is specific: if either of the first two gains a qualification the other has
     * not - homes on excluded pages, squares the diagram no longer draws - the button acts on a set
     * the findings never mentioned, and the operator finds out afterwards.
     *
     * They are one walk now. This is what says so, because a shared helper that two callers stop
     * using is the same defect with an extra step.
     *
     * MUTATION: give either method a filter the other does not have and the first assertion fails.
     */
    @Test
    public void testTheHomeWalksAgree() throws Exception
    {
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("Homes", null);
        session.getStore().setActiveConfiguration("Homes");

        final TileKey one = new TileKey("main", 1, 1);
        final TileKey two = new TileKey("main", 7, 1);

        session.setPointProperty(one, "home", "Loc A");
        session.setPointProperty(two, "home", "Loc B");

        // A THIRD SQUARE WITH NO HOME, so "every square" would fail this rather than pass it.
        session.setPointProperty(new TileKey("main", 4, 1), "home", "");

        java.util.List<TileKey> bulk = session.tilesWithAHome();

        assertEquals(new java.util.LinkedHashSet<>(bulk), session.homesForFindings(),
            "the squares the bulk clear would touch and the squares the findings call homes are not "
            + "the same set.  They are the same question asked twice, and when they drift the button "
            + "acts on track the diagram never warned about (WK3-C3).  Bulk: " + bulk);

        assertEquals(bulk.size(), 2,
            "expected exactly the two squares carrying a home - a blank home is not a home, and a "
            + "walk that counts it would agree with itself while being wrong: " + bulk);

        // AND THE FILTERED ONE comes from the same walk: asked about `one`, it must name `two` and
        // not `one` itself.
        session.setPointProperty(two, "home", "Loc A");

        java.util.List<TileKey> elsewhere = session.homesElsewhereForTest(one, "Loc A");

        assertEquals(elsewhere, java.util.Arrays.asList(two),
            "the same locomotive is homed on two squares and the check that finds the OTHER one "
            + "answered: " + elsewhere);
    }
    /**
     * A facing that follows the track moves the locomotive on the running layout too (DIR-B3).
     *
     * Adam, 2026-09-06: **"flipFacing should also update the running layout."**
     *
     * **Two records of one fact, and they were kept by different hands.** A square is several Points
     * once it is split, and the copy a locomotive stands on is what decides where it can go next.
     * Writing only the setup flipped the diagram's arrow at once while `getPossiblePaths`, the
     * right-click destination list and `explainDestinations` all went on answering for the OLD facing.
     *
     * And it did not merely lag: `captureFromLayout` derives the facing from the copy the locomotive is
     * actually on and writes it back over the setup. Opening an editor was enough to undo the flip,
     * silently.
     *
     * **Asserted as both, in one call.** Either alone passes while the other is wrong, which is the
     * state the ruling exists to end.
     *
     * MUTATION: drop the `moveOntoFacingCopy` call and the layout half fails; drop the `setFacing`
     * and the setup half does.
     */
    @Test
    public void testTheFlipMovesTheLocomotiveOnTheLayoutToo() throws Exception
    {
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("Facing", null);
        session.getStore().setActiveConfiguration("Facing");

        final TileKey tile = new TileKey("main", 4, 1);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> choices =
            session.facingChoices(tile);

        assertEquals(choices.size(), 2,
            "precondition: the square has to offer exactly two facings, or \"the other one\" means "
            + "nothing and flipFacing declines by design.  Offered: " + choices);

        session.setFacing(tile, choices.get(0));

        // The running layout, built from this very setup, so the copies it carries are the ones
        // facingsFor names.
        assertFalse(session.facingsFor(tile).isEmpty(),
            "precondition: the square has no copies in the station index, so there is nothing to "
            + "move a locomotive between");

        final TileKey moved = session.flipFacing("no such locomotive", null);

        assertNull(moved,
            "a locomotive that is not placed anywhere was followed, so the walk is matching on "
            + "something other than the name");

        // THE SETUP HALF, with no layout handed over - which must still work, because that is what a
        // session with no running railway does.
        session.setPointProperty(tile, "loc",
            new org.json.JSONObject().put("name", "Facing Test Loc"));

        final TileKey flipped = session.flipFacing("Facing Test Loc", null);

        assertEquals(flipped, tile,
            "the placed locomotive was not followed at all");

        assertEquals(session.getFacing(tile), choices.get(1),
            "the setup was not turned round: the recorded facing is still " + session.getFacing(tile)
            + " and the square offers " + choices);

        // AND THE CALL SITE HANDS THE LAYOUT OVER, which is the half a session-only test cannot see.
        //
        // Standing the whole window up to watch a Point change hands needs a model and a display; the
        // wiring is what was missing, and the wiring is what this checks.
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ");

        assertTrue(source.contains("session.flipFacing(name, this.model.getAutoLayout())"),
            "the window no longer hands the running layout to flipFacing, so the setup moves and the "
            + "layout does not - and captureFromLayout will write the layout's answer back over it "
            + "at the next editor open (DIR-B3)");

        String session_ = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automationui/AutonomySession.java")),
            java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ");

        assertTrue(session_.contains("moveOntoFacingCopy(running, locomotive, tile, now);"),
            "flipFacing no longer moves the locomotive on the running layout, so the two records of "
            + "one fact are kept by different hands again (DIR-B3)");
    }
    /**
     * And the CHECKS are actually given it, which is a separate question (V31-C3).
     *
     * **The first attempt at this was a test of the rule, and it could not fail.** It pinned a helper
     * that barred every side of a closed square, and reverting the call site left it green -
     * extracting a rule moves the defect to the call. Worse, the rule itself was wrong: a barred
     * side is passed THROUGH and merely not stopped at (OB-120), so the walk went straight on and
     * the station beyond stayed reachable. Only asking the findings caught either.
     *
     * So this asks the question Adam actually asked - *"show how inactive points would affect the
     * routes"* - of the findings themselves: close the middle of a run and the station beyond it
     * becomes unreachable.
     *
     * MUTATION: hand `barredArrivals()` to `AutonomyChecks.run` again and the second assertion fails.
     */
    @Test
    public void testClosingASquareCutsOffWhatIsBeyondIt() throws Exception
    {
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("CutOff", null);
        session.getStore().setActiveConfiguration("CutOff");

        final TileKey near = new TileKey("main", 1, 1);
        final TileKey middle = new TileKey("main", 4, 1);
        final TileKey far = new TileKey("main", 7, 1);

        session.setStation(near, true);
        session.setStation(far, true);

        session.rebuild();

        // THE CONTROL: with everything in service the far station is reachable, so the assertion
        // below cannot be satisfied by a railway that was broken to begin with.
        assertFalse(unreachable(session.check()).contains(far),
            "control: the far station is already unreachable with every square in service, so this "
            + "fixture cannot show what closing one does.  Findings: " + session.check());

        session.setPointProperty(middle, "active", Boolean.FALSE);

        session.rebuild();

        assertTrue(unreachable(session.check()).contains(far),
            "the middle of the run was switched out of service and the station beyond it is still "
            + "reported as reachable.  The runtime blocks every path through a closed square - "
            + "manual routes included - and the cross on the diagram says so, but the checks walked "
            + "straight through it (V31-C3).  Findings: " + session.check());

        // AND THE CLOSED SQUARE IS NOT A DESTINATION EITHER (Adam, 2026-09-06).
        //
        // This asserted the opposite until his ruling: *"in manual mode, inactive endpoints and
        // intermediates should be refused as well.  Just not inactive start points.  Inactive really
        // means nothing can pass."*  `isPathClear`'s destination rule lost its `isAutoRunning` fence
        // in the same breath, so the walk and the runtime agree again.
        //
        // The START is the one exception and is not tested here: it is exempt by construction, since
        // the walk seeds `from` before the loop and never asks about it.
        session.setStation(middle, true);

        session.rebuild();

        // SAID IN ITS OWN WORDS SINCE FR-101 (Adam, 2026-09-24): *"Update the error message to say that is marked for
        // nothing to be able to pass"* - the closed station is named as closed rather than as unreachable, which it
        // also is.
        assertEquals(findingsAbout(org.traincontrol.automationui.AutonomyChecks.STATION_CLOSED, middle), 1,
            "a station switched out of service is still offered as somewhere to send a train.  "
            + "Inactive means nothing can pass - the runtime refuses it as a destination at every "
            + "door now, so offering it is a destination the railway will refuse.  Findings: "
            + session.check());
    }

    /**
     * The squares the findings say nothing can reach.
     *
     * @param findings what check() returned
     * @return the tiles carrying an unreachable-station finding
     */
    private static java.util.Set<TileKey> unreachable(
        java.util.List<org.traincontrol.automationui.AutonomyChecks.Finding> findings)
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding f : findings)
        {
            if (org.traincontrol.automationui.AutonomyChecks.STATION_UNREACHABLE.equals(f.getMessageKey()))
            {
                out.add(f.getTile());
            }
        }

        return out;
    }
    /**
     * The import NAMES the signals it will no longer drive, rather than counting them (RG4-B1).
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
        // Asserted as the RENDERED COUNT rather than `line.contains("2")` (VLD-C5).  The addresses
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
     * The signal-name parser takes signals and leaves everything else alone (RG4-B1).
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

        // THE NAME THIS PROGRAM ACTUALLY GENERATES for anything that is not the implicit protocol
        // (VLD-B1).  `Accessory.getNameWithProtocol` appends the decoder, so a DCC railway writes
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

        // NOT AN ADDRESS, though Integer.valueOf takes both (VLD-C5).  Rendering "-5" into the
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
     * A legacy import says it is leaving hand-written locks behind (ACC-B1).
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
     * A modern export coming back through the legacy door is spotted by each of its three keys
     * (ACC-C7, and the test OPV-C1 says should have existed).
     *
     * `detectImportFormat` calls any file whose `points` is an ARRAY a legacy graph, and a
     * configuration serialised by `Layout.toJSON` is one of those - so a modern setup exported and
     * re-imported comes through a door that carries none of the fields the modern format added.
     *
     * **The first version of this check asked for `blocks` and the key is `block`**, singular, as
     * `Layout:7533` reads it and `AutonomyBuilder:844` writes it. The detector therefore missed every
     * file whose only modern key was that one, and nothing caught it because the fix shipped with no
     * test at all. That is the whole of `OPV-B1` in one line.
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
