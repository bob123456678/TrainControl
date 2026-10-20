package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * A station's signals and entry guards are kept, built and checked, and clicking a notice or a finding shows
 * where it is.
 *
 * One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136 Phase 3): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder
 * every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testASignalGuardsItsStationAndTheNoticeNamesIt extends AutonomySessionFixture
{

    /**
     * A station's protecting signal survives a save, and is not read while the square is not a station - it is kept for
     * when it is one again (`testADemotedStationHasItsSettingsBackWhenItIsAStationAgain`).
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
     * A signal on an excluded page is still reported gone.
     *
     * **The two sides of a pairing are not symmetric, and one filter treated them as if they were.**
     * An excluded-page filter was added to `signalsThatAreGone` because a pairing whose STATION is
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
     * A station's entry guard survives a save, is kept apart from its protecting signals, and is not read while the
     * square is not a station (FR-096, kept for when it is one again).
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
     * A station's entry guard is never its exit guard, whichever is set first.
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
     * A file that carries one signal on both lists anyway is warned about.
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
     * A guard no way into its station passes is noticed, and one on the approach is not.
     *
     * Adam: *"if the guard signal is not on a path leading to the chosen station, we can add notice to the autonomy
     * editor."*  The approach is the track a train runs from the last station it could have stopped at.
     *
     * MUTATION: call every guard on the way in, and the first half fails; call none, and the second does.
     */
    @Test
    public void testAGuardOffTheWayInIsNoticed() throws Exception
    {
        openTheGuardedLine(pageWithAGuardOffTheLine());

        TileKey station = new TileKey("main", 1, 1);

        int errorsBefore = session.errorCount();
        boolean brokenBefore = session.hasErrors();

        session.setEntrySignals(station, Arrays.asList(new TileKey("main", 2, 3)));

        assertTrue(noticesAGuardOffTheWayIn(session), "an entry guard on track no train reaching the station runs over"
            + " is not noticed (AUT-C2)");

        // AND THE SIGNAL'S SQUARE, which a click on the notice outlines (Adam, on MT-505: "highlight the affected signals
        // when the notice is clicked")
        assertEquals(squaresTheGuardNoticeNames(session), Arrays.asList(new TileKey("main", 2, 3)), "the notice does not"
            + " carry the square of the signal it is about, so a click on it can show only the station (MT-505)");

        // A NOTICE, NOT AN ERROR (MT-505): the setup still saves and runs.
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if ("autosetup.ui.checkGuardOffTheWayIn".equals(finding.getMessageKey()))
            {
                assertEquals(finding.getSeverity(), org.traincontrol.automationui.AutonomyChecks.Severity.NOTICE, "a guard"
                    + " off the way in is graded " + finding.getSeverity() + ", not a notice (MT-505)");
            }
        }

        assertEquals(session.errorCount(), errorsBefore, "an entry guard off the way in added an error, so the setup no"
            + " longer runs (MT-505)");

        assertEquals(session.hasErrors(), brokenBefore, "an entry guard off the way in stops the setup running (MT-505)");

        session.setEntrySignals(station, Arrays.asList(new TileKey("main", 2, 1)));

        assertFalse(noticesAGuardOffTheWayIn(session), "an entry guard on the station's own approach is noticed as"
            + " off the way in");

        session.setEntrySignals(station, null);
        session.setProtectingSignals(station, Arrays.asList(new TileKey("main", 2, 3)));

        assertTrue(noticesAnExitGuardOffTheWay(session), "an exit guard on track no train reaching or leaving the station"
            + " runs over is not noticed (AUT-C2, RSA41-C2)");
    }

    /**
     * An exit guard on its station's way in - a home signal - is named for the entry guard.
     *
     * The arriving route turns it green, and the arrival's claim of the station can turn it red in front of that train -
     * or, on the last track in, it stays green over the train standing there.  Adam, 2026-08-23: *"The protecting signal
     * is at the destination"*, a different signal from one a path crosses; the entry guard is the signal on the way in.
     * As an entry guard the same signal is right, and gets nothing.
     *
     * MUTATION: let a route into the station count as the way out, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAnExitGuardOnTheWayInIsNamedForTheEntryGuard() throws Exception
    {
        session.open(Arrays.asList(pageWithAGuardOffTheLine()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey far = new TileKey("main", 4, 1);
        TileKey signal = new TileKey("main", 2, 1);

        session.setStation(station, true);
        session.setStation(far, true);

        // ONE WAY, from the far station to this one: the signal is on its way in and on no way out
        assertTrue(session.setOneWayRun(far, station) > 0, "precondition: the line could not be made one way");

        session.rebuild();

        session.setProtectingSignals(station, Arrays.asList(signal));

        assertTrue(notices(session, "autosetup.ui.checkExitGuardOnTheWayIn"), "an exit guard on the station's way in is"
            + " not named for the entry guard, where the arriving route turns it green (RSA42-C1)");

        session.setProtectingSignals(station, null);
        session.setEntrySignals(station, Arrays.asList(signal));

        assertFalse(noticesAGuardOffTheWayIn(session), "an entry guard on the way in - where it belongs - is noticed");
    }

    /**
     * An exit guard on its station's way out is not noticed; an entry guard there still is.
     *
     * An exit signal - the one a train leaving the station passes, which its own route sets green as it goes - lies on
     * the station's way out, and the notice asked only of the ways in: on Adam's railway it named his exit signals 38, 63
     * and 64 as signals no arriving train meets, *"Check it is the signal you meant"*.  An exit guard is noticed only where
     * no way into or out of its station passes it; an entry guard, which turns red behind a train arriving, still where no
     * way in does.
     *
     * MUTATION: ask an exit guard of the ways in only, and the first half fails; count a way out for an entry guard too,
     * and the second does.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAnExitGuardOnTheWayOutIsNotNoticed() throws Exception
    {
        session.open(Arrays.asList(pageWithAGuardOffTheLine()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey far = new TileKey("main", 4, 1);
        TileKey signal = new TileKey("main", 2, 1);

        session.setStation(station, true);
        session.setStation(far, true);

        // ONE WAY, from the station to the far one: the signal is on its way out and on no way in
        assertTrue(session.setOneWayRun(station, far) > 0, "precondition: the line could not be made one way");

        session.rebuild();

        session.setProtectingSignals(station, Arrays.asList(signal));

        assertFalse(noticesAGuardOffTheWayIn(session) || noticesAnExitGuardOffTheWay(session), "an exit guard on the"
            + " station's way out - its exit signal - is noticed as misplaced (RSA41-C3)");

        session.setProtectingSignals(station, null);
        session.setEntrySignals(station, Arrays.asList(signal));

        assertTrue(noticesAGuardOffTheWayIn(session), "an entry guard on the way out alone, which no train arriving"
            + " passes, is no longer noticed");
    }

    /**
     * Clicking a guard notice outlines the signal as well as the station (MT-505).
     *
     * Adam, 2026-09-26, on MT-505: *"Works, but also highlight the affected signals when the notice is clicked."*  The
     * notice named the signal in words and took the reader to the station, which is the one square it says nothing is
     * wrong with.
     *
     * MUTATION: outline only the finding's own square, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testClickingAGuardNoticeOutlinesItsSignal() throws Exception
    {
        openTheGuardedLine(pageWithAGuardOffTheLine());

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 3);

        session.setEntrySignals(station, Arrays.asList(signal));

        final org.traincontrol.gui.AutonomyEditorPanel panel =
            new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { });

        java.lang.reflect.Field field = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("findings");

        field.setAccessible(true);

        final javax.swing.JList<?> list = (javax.swing.JList<?>) field.get(panel);

        final int[] row = {-1};

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (int i = 0; i < list.getModel().getSize(); i++)
            {
                if (String.valueOf(list.getModel().getElementAt(i)).contains("but no way into")) row[0] = i;
            }

            if (row[0] >= 0) list.setSelectedIndex(row[0]);
        });

        assertTrue(row[0] >= 0, "precondition: the editor's list has no guard notice");

        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        // OUTLINED, not selected - read by reflection, as the claim came first
        java.lang.reflect.Method outlined = org.traincontrol.gui.AutonomyEditorPanel.class.getMethod("isOutlined",
            TileKey.class);

        assertTrue((Boolean) outlined.invoke(panel, signal), "a click on the guard notice did not outline the signal it"
            + " is about (MT-505)");

        assertTrue((Boolean) outlined.invoke(panel, station), "a click on the guard notice no longer outlines the"
            + " station");
    }

    /**
     * A click on a guard notice outlines its squares without making them the bulk selection, so a length set
     * afterwards on another square goes to that square.  The outline was the selection, and the next Segment Length - on
     * any square - was written to the station and its signal instead.
     *
     * MUTATION: outline the notice's squares by selecting them, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAGuardNoticeClickedSelectsNothing() throws Exception
    {
        openTheGuardedLine(pageWithAGuardOffTheLine());

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 3);
        TileKey elsewhere = new TileKey("main", 4, 1);

        session.setEntrySignals(station, Arrays.asList(signal));

        final org.traincontrol.gui.AutonomyEditorPanel panel =
            new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { });

        clickTheGuardNotice(panel);

        assertTrue(panel.getSelection().isEmpty(), "a click on the guard notice made its squares the bulk selection,"
            + " which the next length is written to (RLV9-C3): " + panel.getSelection());

        int stationWas = session.getStore().getTileLength(station);
        int signalWas = session.getStore().getTileLength(signal);

        javax.swing.SwingUtilities.invokeAndWait(() -> panel.applyLengthAnswer(elsewhere, 9));

        assertEquals(session.getStore().getTileLength(elsewhere), 9, "a length set on " + elsewhere + " after a click on"
            + " the guard notice was not written there (RLV9-C3)");

        assertEquals(session.getStore().getTileLength(station), stationWas, "a length set on " + elsewhere + " after a"
            + " click on the guard notice was written to its station (RLV9-C3)");

        assertEquals(session.getStore().getTileLength(signal), signalWas, "a length set on " + elsewhere + " after a"
            + " click on the guard notice was written to its signal (RLV9-C3)");
    }

    /**
     * A clicked notice's outline goes with the next click on the diagram: it is drawn as the bulk selection
     * is, and left standing it reads as a selection nothing is in.
     *
     * MUTATION: leave the outline after a click on the diagram, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAClickOnTheDiagramTakesANoticesOutlineAway() throws Exception
    {
        openTheGuardedLine(pageWithAGuardOffTheLine());

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 3);

        session.setEntrySignals(station, Arrays.asList(signal));

        final org.traincontrol.gui.AutonomyEditorPanel panel =
            new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { });

        clickTheGuardNotice(panel);

        assertTrue(panel.isOutlined(signal), "precondition: the notice's signal is not outlined");

        // An empty square, which a click changes nothing on
        javax.swing.SwingUtilities.invokeAndWait(() -> panel.tileClicked(new TileKey("main", 6, 2), null, false));

        assertFalse(panel.isOutlined(signal) || panel.isOutlined(station), "a click on the diagram left the guard"
            + " notice's squares outlined (RLV10-C5)");
    }

    /**
     * A guard notice about a station on another page takes its signal with it to that page: the jump handed on
     * only the station, and the editor opened there outlined the station alone.  Read by reflection, as the claim came
     * first.
     *
     * MUTATION: jump with the station only, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAGuardNoticeOnAnotherPageTakesItsSignalWithIt() throws Exception
    {
        openTheGuardedLine(pageWithAGuardOffTheLine(), aSecondPage());

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 3);

        session.setEntrySignals(station, Arrays.asList(signal));

        final org.traincontrol.gui.AutonomyEditorPanel panel =
            new org.traincontrol.gui.AutonomyEditorPanel(session, "far", () -> { });

        final List<Object> handed = new ArrayList<>();

        java.lang.reflect.Method jump = org.traincontrol.gui.AutonomyEditorPanel.class.getMethod("setOnJumpToNotice",
            java.util.function.BiConsumer.class);

        jump.invoke(panel, (java.util.function.BiConsumer<TileKey, List<TileKey>>) (at, related) ->
        {
            handed.add(at);
            handed.addAll(related);
        });

        clickTheGuardNotice(panel);

        assertTrue(handed.contains(station), "a click on a guard notice about another page did not go to its station: "
            + handed);

        assertTrue(handed.contains(signal), "a click on a guard notice about another page did not take its signal with"
            + " it (RLV9-C5): " + handed);
    }

    /**
     * Every finding with a square is shown the strong way when clicked - flashed AND outlined - not only one that names
     * squares beside its own (OB-352).  Adam: *"autonomy must be fixed: "is at the end of the line", when clicked, does a
     * regular highlight, whereas "is shown more than ones" gives the yellow highlight plus a strong outline.  make them
     * consistent, using the latter, which is easier to use."*
     *
     * MUTATION: outline only a finding that names other squares, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAFindingAtTheEndOfALineIsOutlinedWhenClicked() throws Exception
    {
        threeStationsInARow("OB-352");

        TileKey alpha = new TileKey("main", 1, 1);

        session.rebuild();

        final org.traincontrol.gui.AutonomyEditorPanel panel =
            new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { });

        final List<TileKey> revealed = new ArrayList<>();

        panel.setOnReveal(revealed::add);

        clickTheFinding(panel, "is at the end of a line", alpha);

        assertTrue(panel.isOutlined(alpha), "a click on \"is at the end of a line\" did not outline its square, as a click"
            + " on \"is shown more than once\" outlines its squares (OB-352)");

        assertEquals(revealed, Arrays.asList(alpha), "a click on \"is at the end of a line\" no longer flashes its square");

        assertTrue(panel.getSelection().isEmpty(), "a click on a finding made its square the bulk selection (RLV9-C3): "
            + panel.getSelection());
    }

    /**
     * A finding about another page that names nothing beside its own square goes there by the same door as one that does,
     * so the editor opened there outlines it too (OB-352).
     *
     * MUTATION: send it through the plain page jump, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAFindingOnAnotherPageIsOutlinedThere() throws Exception
    {
        threeStationsInARow("OB-352 far");

        TileKey alpha = new TileKey("main", 1, 1);

        session.rebuild();

        final org.traincontrol.gui.AutonomyEditorPanel panel =
            new org.traincontrol.gui.AutonomyEditorPanel(session, "far", () -> { });

        final List<Object> outlined = new ArrayList<>();
        final List<Object> plain = new ArrayList<>();

        panel.setOnJumpToNotice((at, related) -> outlined.add(at));
        panel.setOnJumpToPage(plain::add);

        clickTheFinding(panel, "is at the end of a line", alpha);

        assertEquals(outlined, Arrays.asList(alpha), "a click on \"is at the end of a line\" about another page did not go"
            + " there to be outlined (OB-352); the plain page jump had " + plain);
    }

    /**
     * Clicking a notice copies its text to the clipboard as plain text, for sharing (MT-767).
     *
     * Read back from the system clipboard after a sentinel is put there first, so a copy that never happens fails.  Windows
     * opens the clipboard to one program at a time, and a clipboard listener (the history, a remote desktop) opens it after
     * every change, so each use waits for it, and a click whose copy the program dropped on a busy clipboard is made again.
     * Skipped only when another program holds the clipboard for the whole wait.
     *
     * MUTATION: take the copy out of the list's selection listener, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testClickingANoticeCopiesItsTextToTheClipboard() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("there is no clipboard on a headless machine");
        }

        openTheGuardedLine(pageWithAGuardOffTheLine());

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 3);

        session.setEntrySignals(station, Arrays.asList(signal));

        final org.traincontrol.gui.AutonomyEditorPanel panel =
            new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { });

        java.lang.reflect.Field field = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("findings");

        field.setAccessible(true);

        final javax.swing.JList<?> list = (javax.swing.JList<?>) field.get(panel);

        String expected = null;

        for (int i = 0; i < list.getModel().getSize(); i++)
        {
            String shown = String.valueOf(list.getModel().getElementAt(i));

            if (shown.contains("but no way into")) expected = shown.trim();
        }

        assertNotNull(expected, "precondition: the editor's list has no guard notice");

        final java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();

        final java.awt.datatransfer.StringSelection sentinel =
            new java.awt.datatransfer.StringSelection("nothing copied yet");

        String copied = "nothing copied yet";

        // A CLICK THE PROGRAM COULD NOT COPY is made again: it leaves a busy clipboard alone by design, and a copy that
        // never happens still reads the sentinel every time
        for (int click = 0; click < CLIPBOARD_CLICKS && "nothing copied yet".equals(copied); click++)
        {
            onTheClipboard(() -> { clipboard.setContents(sentinel, sentinel); return null; });

            javax.swing.SwingUtilities.invokeAndWait(list::clearSelection);

            clickTheGuardNotice(panel);

            copied = onTheClipboard(() -> (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor));
        }

        assertEquals(copied, expected, "a click on a notice did not copy its text to the clipboard (MT-767)");

        assertFalse(copied.contains("<"), "the copied notice carries markup, which is not plain text: " + copied);

        // AND A HEADING IS NOT A NOTICE: clicking "Warnings" or a page's name leaves the clipboard alone
        java.lang.reflect.Field severities = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("findingSeverity");

        severities.setAccessible(true);

        final java.util.List<?> severity = (java.util.List<?>) severities.get(panel);

        int heading = -1;

        for (int i = 0; i < severity.size() && heading < 0; i++) if (severity.get(i) == null) heading = i;

        assertTrue(heading >= 0, "precondition: the editor's list has no heading row");

        final java.awt.datatransfer.StringSelection untouched = new java.awt.datatransfer.StringSelection("left alone");

        onTheClipboard(() -> { clipboard.setContents(untouched, untouched); return null; });

        final int row = heading;

        javax.swing.SwingUtilities.invokeAndWait(() -> list.setSelectedIndex(row));

        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertEquals(onTheClipboard(() -> clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor)), "left alone",
            "a click on the heading " + list.getModel().getElementAt(row) + " was copied over the clipboard");
    }

    /** How long a use of the clipboard waits for another program to let go of it. */
    private static final long CLIPBOARD_PATIENCE_MS = 10_000;

    /** How many times a notice is clicked while its copy keeps finding the clipboard busy. */
    private static final int CLIPBOARD_CLICKS = 5;

    /**
     * One use of the system clipboard, made again while another program has it open (Windows: "cannot open system
     * clipboard"), and skipped when that lasts the whole wait - the desktop, not the program, decides that.
     *
     * @param use the use
     * @return what it answers
     * @throws Exception from the use
     */
    private static <T> T onTheClipboard(java.util.concurrent.Callable<T> use) throws Exception
    {
        long until = System.currentTimeMillis() + CLIPBOARD_PATIENCE_MS;

        while (true)
        {
            try
            {
                return use.call();
            }
            catch (IllegalStateException busy)
            {
                if (System.currentTimeMillis() > until)
                {
                    throw new org.testng.SkipException("another program held the system clipboard open for "
                        + CLIPBOARD_PATIENCE_MS / 1000 + " s: " + busy);
                }

                Thread.sleep(20);
            }
        }
    }

    /** Selects the first row of the editor's list that says this and is about this square, as a click on it does. */
    private static void clickTheFinding(org.traincontrol.gui.AutonomyEditorPanel panel, String says, TileKey at)
        throws Exception
    {
        final javax.swing.JList<?> list = (javax.swing.JList<?>) panelPart(panel, "findings");

        java.lang.reflect.Field field = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("findingTiles");

        field.setAccessible(true);

        final List<?> tiles = (List<?>) field.get(panel);

        final int[] row = {-1};

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (int i = 0; i < list.getModel().getSize() && row[0] < 0; i++)
            {
                if (String.valueOf(list.getModel().getElementAt(i)).contains(says) && at.equals(tiles.get(i))) row[0] = i;
            }

            if (row[0] >= 0) list.setSelectedIndex(row[0]);
        });

        assertTrue(row[0] >= 0, "precondition: the editor's list has no \"" + says + "\" about " + at);

        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /** Selects the guard notice in the editor's list, as a click on it does. */
    private static void clickTheGuardNotice(org.traincontrol.gui.AutonomyEditorPanel panel) throws Exception
    {
        java.lang.reflect.Field field = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("findings");

        field.setAccessible(true);

        final javax.swing.JList<?> list = (javax.swing.JList<?>) field.get(panel);

        final int[] row = {-1};

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (int i = 0; i < list.getModel().getSize(); i++)
            {
                if (String.valueOf(list.getModel().getElementAt(i)).contains("but no way into")) row[0] = i;
            }

            if (row[0] >= 0) list.setSelectedIndex(row[0]);
        });

        assertTrue(row[0] >= 0, "precondition: the editor's list has no guard notice");

        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * The guard line's pages opened as a railway: a station at each end of the two-signal line, and trains free to turn at
     * both, so it has routes.  The guard notices read the routes the built railway has, and a station whose
     * only approach is a sensor nothing reaches has none.
     *
     * @param pages the pages, the guard line's first
     */
    private void openTheGuardedLine(LayoutDiagram... pages) throws IOException
    {
        session.open(Arrays.asList(pages));

        TileKey station = new TileKey("main", 1, 1);
        TileKey far = new TileKey("main", 4, 1);

        session.setStation(station, true);
        session.setStation(far, true);
        session.initialize("Guards");
        session.setPointFlag(station, AutonomyBuilder.CAN_REVERSE, true);
        session.setPointFlag(far, AutonomyBuilder.CAN_REVERSE, true);
        session.rebuild();

        assertFalse(session.check().stream().anyMatch(f -> "autosetup.ui.checkStationAtTheEndOfALine".equals(f.getMessageKey())),
            "precondition: trains still may not turn at the line's ends, so the built railway has no route to read");
    }

    /** A second page, "far", with one sensor on it. */
    private LayoutDiagram aSecondPage() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        File far = new File(pages, "far.cs2");

        Files.write(far.toPath(), "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + far.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("far", 4, 2, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 9, 15, accessoryDecoderType.MM2, null);

        page.setPageId("2");

        return page;
    }

    /** The squares, beyond its own, that the guard notice names - read by reflection, as the claim came first. */
    private static List<?> squaresTheGuardNoticeNames(AutonomySession session) throws Exception
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if ("autosetup.ui.checkGuardOffTheWayIn".equals(finding.getMessageKey()))
            {
                return new ArrayList<>((java.util.Collection<?>) finding.getClass().getMethod("getRelated")
                    .invoke(finding));
            }
        }

        return null;
    }

    /** Whether the check gives any of the exit guard's three notices. */
    private static boolean noticesAnExitGuardOffTheWay(AutonomySession session)
    {
        return notices(session, "autosetup.ui.checkExitGuardOffTheWay") || notices(session, "autosetup.ui.checkExitGuardCrossed")
            || notices(session, "autosetup.ui.checkExitGuardOnTheWayIn");
    }

    /** Whether the check gives this notice. */
    private static boolean notices(AutonomySession session, String key)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (key.equals(finding.getMessageKey())) return true;
        }

        return false;
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
}
