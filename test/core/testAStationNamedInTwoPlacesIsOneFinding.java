package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * A station whose name is drawn in two places is one finding, and an import or a move never captions it twice
 * (OB-341).
 *
 * One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136 Phase 3): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder
 * every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testAStationNamedInTwoPlacesIsOneFinding extends AutonomySessionFixture
{

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
     * A station whose name is drawn in two places is an error that names the station and both squares (OB-341).
     *
     * Adam, 2026-10-12, on a sandbox layout whose setup held two captions for St01: *"add code to make this be an error
     * in the autonomy validator (i.e. if someone edits the file by hand externally)"*.  Every door of the editor keeps
     * one station to one caption (OB-256), so the state arrives from outside them, and the file is the door used here: a
     * setup saved, a second caption written into its `captions`, and the setup opened again.
     *
     * One finding for the station, on its own square, carrying both caption squares and their number.  THE CONTROLS are
     * the same setup before the edit - one station captioned once beside itself, another on its own square - and the
     * station once it is no station, whose captions are kept but not drawn.
     *
     * MUTATION: leave the error out, report a station with one caption, or count a square that is no station, and this
     * fails.  A station named on its own square and beside it is NOT an error (it is drawn once): the next claims.
     *
     * @throws Exception from the session or the setup file
     */
    @Test
    public void testAStationShownTwiceIsAnErrorNamingTheStationAndBothSquares() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey other = new TileKey("main", 4, 1);
        TileKey beside = new TileKey("main", 1, 2);
        TileKey further = new TileKey("main", 1, 3);

        session.getStore().setStation(station, true);
        session.getStore().setStation(other, true);
        session.setPointName(station, "Hauptbahnhof");
        session.setPointName(other, "Nebenbahnhof");
        session.setCaption(beside, station);
        session.setCaption(other, other);
        session.save();

        // THE CONTROL: every station shown once
        AutonomySession once = new AutonomySession(layout);

        once.open(Arrays.asList(page));
        once.rebuild();

        assertTrue(shownTwice(once).isEmpty(), "a station shown once is reported as shown twice: " + once.check());

        // THE EDIT: a second caption for the station, written into the file by hand
        File setup = new File(layout, "config/autonomy/setup.json");

        org.json.JSONObject file = new org.json.JSONObject(new String(Files.readAllBytes(setup.toPath()),
            StandardCharsets.UTF_8));

        assertTrue(file.getJSONObject("captions").has("1:1,2"), "precondition: the caption is not in the file under the"
            + " page's id: " + file.getJSONObject("captions"));

        file.getJSONObject("captions").put("1:1,3", "1:1,1");

        Files.write(setup.toPath(), file.toString().getBytes(StandardCharsets.UTF_8));

        AutonomySession edited = new AutonomySession(layout);

        edited.open(Arrays.asList(page));
        edited.rebuild();

        assertEquals(edited.captionsFor(station), new LinkedHashSet<>(Arrays.asList(beside, further)),
            "precondition: the setup did not read the second caption in");

        List<org.traincontrol.automationui.AutonomyChecks.Finding> found = shownTwice(edited);

        assertEquals(found.size(), 1, "a station shown in two places is not reported exactly once: " + edited.check());

        org.traincontrol.automationui.AutonomyChecks.Finding finding = found.get(0);

        assertEquals(finding.getSeverity(), org.traincontrol.automationui.AutonomyChecks.Severity.ERROR,
            "a station named twice with nothing to say which is current must stop the setup, not sit among the warnings");

        assertEquals(finding.getAbout(), station, "the finding is not about the station");
        assertEquals(finding.getTile(), beside, "the finding is not filed on the first square the name is drawn on");
        assertEquals(finding.getCount(), 2, "the finding does not say how many places the station is shown in");

        assertEquals(new LinkedHashSet<>(finding.getRelated()), new LinkedHashSet<>(Arrays.asList(beside, further)),
            "the finding does not point at both caption squares");

        // WHAT THE READER IS TOLD, as the editor renders it: {0} the station, {1} the finding's subject, {2} the count
        String said = org.traincontrol.util.I18n.f(finding.getMessageKey(), edited.describeTile(finding.getAbout()),
            finding.getSubject(), finding.getCount(), finding.getDetail(), finding.getThird());

        assertTrue(said.contains("Hauptbahnhof"), "the sentence does not name the station: " + said);
        assertTrue(said.contains("(1,2)") && said.contains("(1,3)"), "the sentence does not name both squares: " + said);
        assertFalse(said.contains("Nebenbahnhof"), "the sentence names a station that is shown once: " + said);

        // and it is one more thing to fix before the setup runs, not just a line in a list
        assertEquals(edited.errorCount(), once.errorCount() + 1, "the setup is not refused for it");

        // A square that is no station now is not drawn, so it is not shown twice
        edited.getStore().setStation(station, false);

        assertTrue(shownTwice(edited).isEmpty(), "captions kept for a square that is no station are reported as shown"
            + " twice, though nothing is drawn: " + edited.check());
    }

    /**
     * One finding per station however many places its name is drawn in, and the editor's own doors clear it (OB-341).
     *
     * Three captions beside one station and two beside another are two findings, the first counting three; the first
     * station is also captioned on its own square, which gives way to them and is not counted.  The sentence tells the
     * reader to stop showing the station on all but one square, and to show it on the one to keep: both go through
     * `setCaption`, the door the editor's menu uses, so doing as it says is what ends the finding.
     *
     * MUTATION: one finding per caption square, counting the station's own square, or not clearing when the extra ones
     * go, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAStationShownInThreePlacesIsOneErrorAndTheEditorClearsIt() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey other = new TileKey("main", 4, 1);
        TileKey first = new TileKey("main", 1, 2);
        TileKey second = new TileKey("main", 1, 3);
        TileKey third = new TileKey("main", 2, 2);

        session.getStore().setStation(station, true);
        session.getStore().setStation(other, true);
        session.setPointName(station, "Hauptbahnhof");
        session.setPointName(other, "Nebenbahnhof");

        // Through the store's own door, which does not sweep - as a file edited by hand does not
        for (TileKey where : new TileKey[] {station, first, second, third})
        {
            session.getStore().setCaption(where, station);
        }

        for (TileKey where : new TileKey[] {new TileKey("main", 4, 2), new TileKey("main", 4, 3)})
        {
            session.getStore().setCaption(where, other);
        }

        session.rebuild();

        List<org.traincontrol.automationui.AutonomyChecks.Finding> found = shownTwice(session);

        assertEquals(found.size(), 2, "not one finding for each station named twice: " + session.check());

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : found)
        {
            assertEquals(finding.getCount(), station.equals(finding.getAbout()) ? 3 : 2,
                "the count is not the number of places the name is drawn in: " + finding);
        }

        assertFalse(findingFor(found, station).getRelated().contains(station),
            "the station's own square is counted, though its caption gives way to the ones beside it");

        // "Stop Showing" on all but one of them: what is left is the one beside it, and the station's own, which gives way
        session.setCaption(first, null);
        session.setCaption(second, null);

        assertEquals(shownTwice(session).size(), 1, "stopping the extra captions did not clear the station: "
            + session.check());

        assertEquals(shownTwice(session).get(0).getAbout(), other, "it cleared the wrong station's finding");

        // "Show a Station Name Here" on the one to keep: the others go
        session.setCaption(new TileKey("main", 4, 3), other);

        assertTrue(shownTwice(session).isEmpty(), "showing the station on one square did not take it off the others: "
            + session.check());
    }

    /**
     * A station named on its own square and beside it is drawn once, so it is no error (OB-341).
     *
     * That is the shape of Adam's own railway - TopMainR2 is captioned on `5:6,4` and on `5:6,5` in `cs2_sample_layout`
     * and every fixture copied from it - and `TrainControlUI.autonomyCaptionAt` draws it ONCE, the beside one (MT-337).
     * The check counts what is drawn, by the session's `isCaptionDrawn` that the drawing asks as well, so the shape is no
     * finding; a second caption beside the station makes it two names and is one.  St59 and St00AA in the sandbox that
     * raised this are the same shape.
     *
     * MUTATION: count the station's own square as a place, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAStationNamedOnItsOwnSquareAndBesideItIsDrawnOnceAndIsNoError() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey beside = new TileKey("main", 1, 2);
        TileKey further = new TileKey("main", 1, 3);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Hauptbahnhof");

        // On its own square only: drawn there
        session.getStore().setCaption(station, station);
        session.rebuild();

        assertTrue(session.isCaptionDrawn(station), "a station's only caption, on its own square, is not drawn");
        assertTrue(shownTwice(session).isEmpty(), "precondition: " + session.check());

        // AND BESIDE IT: drawn once, beside, and no error
        session.getStore().setCaption(beside, station);
        session.rebuild();

        assertEquals(session.captionsFor(station).size(), 2, "precondition: the station is not named in two places");
        assertFalse(session.isCaptionDrawn(station), "the station's own caption is drawn, beside the one it gives way to");
        assertTrue(session.isCaptionDrawn(beside), "the caption beside the station is not drawn");
        assertEquals(session.drawnCaptionsFor(station), Arrays.asList(beside), "the station is not drawn exactly once");

        assertTrue(shownTwice(session).isEmpty(), "a station named on its own square and beside it is drawn once, and is"
            + " reported as drawn twice: " + session.check());

        // A SECOND ONE BESIDE IT: two names, drawn twice, and the station's own square is not one of the places
        session.getStore().setCaption(further, station);
        session.rebuild();

        assertEquals(shownTwice(session).size(), 1, "two captions beside the station are not reported: " + session.check());

        assertEquals(new LinkedHashSet<>(shownTwice(session).get(0).getRelated()),
            new LinkedHashSet<>(Arrays.asList(beside, further)), "the places are not the two beside the station");
    }

    /**
     * What is stale is not counted (OB-341): a caption on a page that is gone.
     *
     * Two captions beside a station are an error, but one of them on a page the session no longer has is not drawn
     * anywhere.  The control is the station with a second caption that is on a page, which is reported.
     *
     * MUTATION: leave out the page test in `isCaptionDrawn`, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testStaleCaptionsAreNotCounted() throws Exception
    {
        session.open(Arrays.asList(runOfTrack()));

        TileKey station = new TileKey("main", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Hauptbahnhof");

        // One real caption beside it, and one on a page that is gone
        session.getStore().setCaption(new TileKey("main", 1, 2), station);
        session.getStore().setCaption(new TileKey("gone", 1, 1), station);
        session.rebuild();

        assertTrue(shownTwice(session).isEmpty(), "a caption on a page that is gone is reported: " + session.check());

        // THE CONTROL: the same station with a second caption that is on a page, is reported
        session.getStore().setCaption(new TileKey("main", 1, 3), station);
        session.rebuild();

        assertEquals(shownTwice(session).size(), 1, "precondition: a second real caption is not reported: " + session.check());
        assertEquals(shownTwice(session).get(0).getAbout(), station, "it was reported for the wrong station");
        assertEquals(shownTwice(session).get(0).getCount(), 2, "the caption on the page that is gone was counted");
    }

    /**
     * A station that is no Point is reported when its name is drawn twice on a page (OB-341).
     *
     * The rule's promise is an ERROR exactly where the diagram draws a name twice on a page, and the diagram draws a
     * caption for any station - `getCaptionTarget` asks only that the square be one - whether or not the railway built a
     * Point there.  Two such stations: a sensor with no track to it, and a station on a page left out of autonomy whose
     * captions sit on a page that is drawn.  No door of the editor makes either; a setup edited by hand does.
     *
     * MUTATION: ask whether the station is a Point in `stationsShownMoreThanOnce`, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAStationThatIsNoPointIsReportedWhenItsNameIsDrawnTwice() throws Exception
    {
        LayoutDiagram page = runOfTrack();

        // A sensor on its own, with no track to it: marked a station, it is not a Point
        page.addComponent(componentType.FEEDBACK, 6, 3, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        session.open(Arrays.asList(page, secondPage()));

        TileKey alone = new TileKey("main", 6, 3);
        TileKey left = new TileKey("second", 1, 1);

        session.getStore().setStation(alone, true);
        session.getStore().setStation(left, true);
        session.setPointName(alone, "Einzelgleis");
        session.setPointName(left, "Nebengleis");

        session.getStore().setCaption(new TileKey("main", 6, 4), alone);
        session.getStore().setCaption(new TileKey("main", 7, 4), alone);

        // the station on the page left out, its captions on the page that is drawn
        session.setPageExcluded("second", true);
        session.getStore().setCaption(new TileKey("main", 1, 2), left);
        session.getStore().setCaption(new TileKey("main", 1, 3), left);
        session.rebuild();

        assertFalse(session.getReducer().getPoints().containsKey(alone), "precondition: the lone sensor should be no Point,"
            + " and it is one");

        assertFalse(session.getReducer().getPoints().containsKey(left), "precondition: a station on a page left out of"
            + " autonomy should be no Point, and it is one");

        List<org.traincontrol.automationui.AutonomyChecks.Finding> found = shownTwice(session);

        assertNotNull(findingFor(found, alone), "a sensor with no track, marked a station and drawn twice, is not reported: "
            + session.check());

        assertNotNull(findingFor(found, left), "a station on a page left out of autonomy, drawn twice on a page that is drawn,"
            + " is not reported: " + session.check());
    }

    /**
     * A station unnamed and out of service is not reported for what is not drawn (OB-341).
     *
     * The diagram draws no caption for a station that is both unnamed and switched off (Adam, 2026-08-31: *"hide the label
     * if empty and nothing can pass."*), so two captions beside it are nothing drawn twice - and its automatic name already
     * has an error of its own, which `checkStationLabels` avoids doubling.  Named, it keeps its caption however it is
     * switched; unnamed and in service it draws its dash.  Both of those are reported.
     *
     * MUTATION: leave the rule out of `isCaptionDrawn`, and the first assertion fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAnUnnamedStationOutOfServiceIsNotReportedForWhatIsNotDrawn() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));
        session.initialize("Evening");

        TileKey shut = new TileKey("main", 1, 1);
        TileKey open = new TileKey("main", 4, 1);

        session.getStore().setStation(shut, true);
        session.getStore().setStation(open, true);

        // UNNAMED, and out of service
        session.setPointProperty(shut, "active", Boolean.FALSE);
        session.getStore().setCaption(new TileKey("main", 1, 2), shut);
        session.getStore().setCaption(new TileKey("main", 1, 3), shut);

        // UNNAMED, in service: it draws its dash, twice
        session.getStore().setCaption(new TileKey("main", 4, 2), open);
        session.getStore().setCaption(new TileKey("main", 4, 3), open);
        session.rebuild();

        assertEquals(session.getPointProperty(shut, "active"), Boolean.FALSE, "precondition: the station is not out of service");

        assertFalse(session.isCaptionDrawn(new TileKey("main", 1, 2)), "a caption of a station unnamed and out of service is"
            + " drawn, though the diagram draws none");

        List<org.traincontrol.automationui.AutonomyChecks.Finding> found = shownTwice(session);

        assertNull(findingFor(found, shut), "a station unnamed and out of service is reported for a name nothing draws: "
            + session.check());

        assertNotNull(findingFor(found, open), "an unnamed station in service, drawing its dash twice, is not reported: "
            + session.check());

        // NAMED, it keeps its caption however it is switched
        session.setPointName(shut, "Gesperrt");
        session.rebuild();

        assertNotNull(findingFor(shownTwice(session), shut), "a named station out of service is not reported: "
            + session.check());
    }

    /**
     * The finding is filed on the first square the name is drawn on, and counted and clicked to there (OB-341).
     *
     * Two captions of a station on page B, the station on page A: the editor files a finding, counts it for the page and
     * jumps to it by the square it is filed on, so one filed on the station's own square put the count on A and the click on
     * A, where the squares to look at are not.  It is filed on B's first square and is ABOUT the station, which is what the
     * sentence names.
     *
     * MUTATION: file it on the station's own square, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheFindingIsFiledOnThePageThatDrawsTheNameTwice() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey thereOne = new TileKey("second", 3, 2);
        TileKey thereTwo = new TileKey("second", 3, 3);

        session.getStore().setStation(station, true);
        session.getStore().setStation(new TileKey("main", 4, 1), true);
        session.setPointName(station, "Hauptbahnhof");

        session.getStore().setCaption(thereOne, station);
        session.getStore().setCaption(thereTwo, station);
        session.rebuild();

        List<org.traincontrol.automationui.AutonomyChecks.Finding> found = shownTwice(session);

        assertEquals(found.size(), 1, "precondition: " + session.check());

        org.traincontrol.automationui.AutonomyChecks.Finding finding = found.get(0);

        assertEquals(finding.getTile().getPage(), "second", "the finding is filed on the station's page, where the squares to"
            + " look at are not: " + finding.getTile());

        assertEquals(finding.getTile(), thereOne, "the finding is not filed on the first square the name is drawn on");
        assertEquals(finding.getAbout(), station, "the finding is not about the station");

        String said = org.traincontrol.util.I18n.f(finding.getMessageKey(), session.describeTile(finding.getAbout()),
            finding.getSubject(), finding.getCount(), finding.getDetail(), finding.getThird());

        assertTrue(said.contains("Hauptbahnhof"), "the sentence does not name the station: " + said);
        assertTrue(said.contains("second 3,2") && said.contains("second 3,3"), "the sentence does not name the squares: " + said);
    }

    /**
     * A station on a page left out of autonomy is not reported (OB-341), as it is not built.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAStationOnAPageLeftOutOfAutonomyIsNotReported() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        TileKey station = new TileKey("second", 1, 1);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Nebengleis");
        session.getStore().setCaption(new TileKey("second", 1, 2), station);
        session.getStore().setCaption(new TileKey("second", 1, 3), station);
        session.rebuild();

        assertEquals(shownTwice(session).size(), 1, "precondition: the station named twice is not reported: " + session.check());

        session.setPageExcluded("second", true);
        session.rebuild();

        assertTrue(shownTwice(session).isEmpty(), "a station on a page left out of autonomy is reported: " + session.check());
    }

    /**
     * A station named on its own square and on another page is drawn on both, and is no error (OB-341).
     *
     * Adam, 2026-10-12: *"The duplicate caption check should be per page.  It is OK to show on another page what is going
     * on on another."*  MT-337's trouble was the two landing next to each other, which two pages never do, so the station's
     * own caption gives way only to a caption on its OWN page.  A caption on a page that is not drawn - one left out of
     * autonomy, which no window draws (`LayoutGrid.hidesStationCaptions`), or one that is gone - is drawn nowhere.
     *
     * MUTATION: let the own caption give way to a caption on another page, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAStationNamedOnItsOwnSquareAndOnAnotherPageIsDrawnOnBoth() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey elsewhere = new TileKey("second", 3, 2);
        TileKey gone = new TileKey("gone", 1, 1);

        session.getStore().setStation(station, true);
        session.getStore().setStation(new TileKey("main", 4, 1), true);
        session.setPointName(station, "Hauptbahnhof");

        session.getStore().setCaption(station, station);
        session.getStore().setCaption(elsewhere, station);
        session.rebuild();

        assertTrue(session.isCaptionDrawn(station), "the station's own caption gives way to one on another page, though the"
            + " two never land next to each other");

        assertTrue(session.isCaptionDrawn(elsewhere), "the caption on the other page is not drawn");

        assertEquals(session.drawnCaptionsFor(station).size(), 2, "the station is not drawn on both pages: "
            + session.drawnCaptionsFor(station));

        assertTrue(shownTwice(session).isEmpty(), "a station drawn once on each of two pages is reported: " + session.check());

        // THE OTHER PAGE LEFT OUT OF AUTONOMY: nothing there is drawn, and the own caption is
        session.setPageExcluded("second", true);
        session.rebuild();

        assertFalse(session.isCaptionDrawn(elsewhere), "a caption on a page left out of autonomy is drawn");
        assertTrue(session.isCaptionDrawn(station), "the station is drawn nowhere");

        // THE OTHER PAGE GONE
        session.setPageExcluded("second", false);
        session.getStore().setCaption(elsewhere, null);
        session.getStore().setCaption(gone, station);
        session.rebuild();

        assertFalse(session.isCaptionDrawn(gone), "a caption on a page that is gone is drawn");
        assertTrue(session.isCaptionDrawn(station), "the station is drawn nowhere");
    }

    /**
     * Only a page that draws a station's name twice is an error, and the finding names that page's squares (OB-341).
     *
     * Two captions beside the station on its own page and one on another page: one finding, with the two.  Two on the
     * OTHER page and one beside the station: one finding, with the other page's two, named with their page, and not the
     * lone one.  A caption beside the station on each of two pages is nothing to report, and one page's two that are not
     * drawn - the page left out of autonomy, or gone - are not two names.
     *
     * Adam, 2026-10-12: *"The duplicate caption check should be per page."*
     *
     * MUTATION: count the captions of all pages together, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testOnlyAPageThatDrawsTheNameTwiceIsAnErrorAndTheFindingNamesItsSquares() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey hereOne = new TileKey("main", 1, 2);
        TileKey hereTwo = new TileKey("main", 1, 3);
        TileKey thereOne = new TileKey("second", 3, 2);
        TileKey thereTwo = new TileKey("second", 3, 3);

        session.getStore().setStation(station, true);
        session.getStore().setStation(new TileKey("main", 4, 1), true);
        session.setPointName(station, "Hauptbahnhof");

        // ONE BESIDE IT ON EACH PAGE: nothing to report
        session.getStore().setCaption(hereOne, station);
        session.getStore().setCaption(thereOne, station);
        session.rebuild();

        assertEquals(session.drawnCaptionsFor(station).size(), 2, "precondition: the station is not drawn on both pages");
        assertTrue(shownTwice(session).isEmpty(), "a caption beside the station on each of two pages is reported: "
            + session.check());

        // TWO ON ITS OWN PAGE, ONE ON THE OTHER
        session.getStore().setCaption(hereTwo, station);
        session.rebuild();

        List<org.traincontrol.automationui.AutonomyChecks.Finding> found = shownTwice(session);

        assertEquals(found.size(), 1, "two on the station's page and one on another are not one finding: " + session.check());
        assertEquals(found.get(0).getCount(), 2, "the finding counts the lone caption on the other page");

        assertEquals(found.get(0).getRelated(), Arrays.asList(hereOne, hereTwo),
            "the finding does not name exactly the station's page's two");

        // TWO ON THE OTHER PAGE, ONE ON THE STATION'S
        session.getStore().setCaption(hereTwo, null);
        session.getStore().setCaption(thereTwo, station);
        session.rebuild();

        found = shownTwice(session);

        assertEquals(found.size(), 1, "two on the other page and one on the station's are not one finding: " + session.check());

        assertEquals(found.get(0).getRelated(), Arrays.asList(thereOne, thereTwo),
            "the finding does not name exactly the other page's two");

        String said = org.traincontrol.util.I18n.f(found.get(0).getMessageKey(), session.describeTile(station),
            found.get(0).getSubject(), found.get(0).getCount(), found.get(0).getDetail(), found.get(0).getThird());

        assertTrue(said.contains("second 3,2") && said.contains("second 3,3"),
            "the squares on the other page are not named with their page: " + said);

        assertFalse(said.contains("(1,2)"), "the sentence names the lone caption on the station's page: " + said);

        // THE PAGE'S TWO NOT DRAWN: left out of autonomy, then gone
        session.setPageExcluded("second", true);
        session.rebuild();

        assertTrue(shownTwice(session).isEmpty(), "two captions on a page left out of autonomy are reported: " + session.check());

        session.setPageExcluded("second", false);
        session.getStore().setCaption(thereOne, null);
        session.getStore().setCaption(thereTwo, null);
        session.getStore().setCaption(new TileKey("gone", 1, 1), station);
        session.getStore().setCaption(new TileKey("gone", 1, 2), station);
        session.rebuild();

        assertTrue(shownTwice(session).isEmpty(), "two captions on a page that is gone are reported: " + session.check());
    }

    /**
     * The squares are named in reading order, and the stations are listed in it, whatever order the setup holds them in
     * (OB-341).
     *
     * A file's captions load in the order the JSON gives them, which is no order at all, so the sentence and the list
     * would change from one open to the next.  Put in through the store in the opposite order, so that only sorting gives
     * the answer.
     *
     * MUTATION: leave the sorts out of `stationsShownMoreThanOnce`, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheSquaresAreNamedInReadingOrderWhateverOrderTheSetupHoldsThem() throws Exception
    {
        session.open(Arrays.asList(pageOnDisk()));

        TileKey early = new TileKey("main", 1, 1);
        TileKey late = new TileKey("main", 4, 1);

        session.getStore().setStation(early, true);
        session.getStore().setStation(late, true);
        session.setPointName(early, "Hauptbahnhof");
        session.setPointName(late, "Nebenbahnhof");

        // The later station's, and the later square's, first
        session.getStore().setCaption(new TileKey("main", 4, 3), late);
        session.getStore().setCaption(new TileKey("main", 1, 3), early);
        session.getStore().setCaption(new TileKey("main", 4, 2), late);
        session.getStore().setCaption(new TileKey("main", 1, 2), early);
        session.rebuild();

        assertEquals(new ArrayList<>(session.captionsFor(early)).get(0), new TileKey("main", 1, 3),
            "precondition: the setup does not hold the squares in the opposite order");

        List<org.traincontrol.automationui.AutonomyChecks.Finding> found = shownTwice(session);

        assertEquals(found.size(), 2, "precondition: " + session.check());

        assertEquals(found.get(0).getAbout(), early, "the stations are not listed in reading order: " + found);

        assertEquals(found.get(0).getRelated(), Arrays.asList(new TileKey("main", 1, 2), new TileKey("main", 1, 3)),
            "the squares are not in reading order");

        assertTrue(found.get(0).getSubject().indexOf("(1,2)") < found.get(0).getSubject().indexOf("(1,3)"),
            "the sentence does not name the squares in reading order: " + found.get(0).getSubject());
    }

    /**
     * An import does not caption a station the setup already shows somewhere else (OB-341).
     *
     * `importBundle` merges the file's `captions` square by square, keeping what is already there and filling the gaps -
     * and a square the setup had no caption on is a gap whether or not the STATION it names is shown elsewhere.  Importing
     * onto a layout whose stations an old-file import had captioned on their own squares gave each station named in the
     * file a second caption beside the first: the state the setup check now calls an error.  A station the setup shows
     * nowhere still gets the file's caption, or an import onto a fresh setup would lose them all.
     *
     * MUTATION: take the guard out of the `captions` merge in `importBundle`, and this fails.
     *
     * @throws Exception from the sessions
     */
    @Test
    public void testAnImportDoesNotCaptionAStationTheSetupAlreadyShows() throws Exception
    {
        TileKey shown = new TileKey("main", 1, 1);
        TileKey notShown = new TileKey("main", 4, 1);

        File exporting = Files.createTempDirectory("tc-autonomy-export").toFile();

        try
        {
            // The file's setup: both stations captioned beside themselves
            AutonomySession from = new AutonomySession(exporting);

            from.open(Arrays.asList(runOfTrack()));
            from.initialize("Evening");
            from.setStation(shown, true);
            from.setStation(notShown, true);
            from.setPointName(shown, "Hauptbahnhof");
            from.setPointName(notShown, "Nebenbahnhof");
            from.setCaption(new TileKey("main", 1, 2), shown);
            from.setCaption(new TileKey("main", 4, 2), notShown);

            org.json.JSONObject bundle = from.getStore().exportBundle("Evening");

            // This setup: one of them captioned on its own square, as the old-file import leaves it, the other not at all
            session.open(Arrays.asList(runOfTrack()));
            session.initialize("Morning");
            session.setStation(shown, true);
            session.setStation(notShown, true);
            session.setPointName(shown, "Hauptbahnhof");
            session.setPointName(notShown, "Nebenbahnhof");
            session.setCaption(shown, shown);

            session.importBundle("Imported", new org.json.JSONObject(bundle.toString()));

            assertEquals(session.captionsFor(shown), new LinkedHashSet<>(Arrays.asList(shown)),
                "the import gave a station already shown a second caption");

            assertEquals(session.captionsFor(notShown), new LinkedHashSet<>(Arrays.asList(new TileKey("main", 4, 2))),
                "the import did not give a station shown nowhere the file's caption");

            assertTrue(shownTwice(session).isEmpty(), "the import left the setup with an error: " + session.check());
        }
        finally
        {
            delete(exporting);
        }
    }

    /**
     * A file that names one station in several places gives the setup one of them, and always the same one (OB-341).
     *
     * The import takes only the stations the setup does not caption yet, and a file's own repeats are not exempt.  org.json
     * holds the file's keys in no order, so which survived would be an accident of their hashes; the squares are taken in
     * reading order - page, row, column - and the first is kept.  The keys below hash in an order that is NOT reading order,
     * so only the sort puts the right one first.
     *
     * MUTATION: take the keys in the order the file gives them, and this fails.
     *
     * @throws Exception from the sessions
     */
    @Test
    public void testAnImportThatNamesAStationSeveralTimesKeepsTheFirstSquare() throws Exception
    {
        TileKey shown = new TileKey("main", 1, 1);

        File exporting = Files.createTempDirectory("tc-autonomy-export").toFile();

        try
        {
            AutonomySession from = new AutonomySession(exporting);

            from.open(Arrays.asList(runOfTrack()));
            from.initialize("Evening");
            from.setStation(shown, true);
            from.setPointName(shown, "Hauptbahnhof");
            from.setCaption(new TileKey("main", 1, 2), shown);

            org.json.JSONObject bundle = from.getStore().exportBundle("Evening");

            // the file names the station in five more places, by hand
            org.json.JSONObject captions = bundle.getJSONObject("shared").getJSONObject("captions");

            for (String more : new String[] {"1:5,2", "1:1,3", "1:7,0", "1:2,3", "1:6,1"})
            {
                captions.put(more, "1:1,1");
            }

            session.open(Arrays.asList(runOfTrack()));
            session.initialize("Morning");
            session.setStation(shown, true);
            session.setPointName(shown, "Hauptbahnhof");

            session.importBundle("Imported", new org.json.JSONObject(bundle.toString()));

            assertEquals(session.captionsFor(shown), new LinkedHashSet<>(Arrays.asList(new TileKey("main", 7, 0))),
                "the import did not keep exactly the first square, in reading order, of the ones the file names");
        }
        finally
        {
            delete(exporting);
        }
    }

    /**
     * An import keeps a caption on another page than the station's own, and still skips a second on one page (OB-341).
     *
     * Adam, 2026-10-12: *"The duplicate caption check should be per page."*  The merge takes a caption of the file's only
     * when the station has none yet ON THAT CAPTION'S PAGE, the file's own repeats included, in the order of the squares.
     * So a station the setup captions on its own square gets the file's caption on the other page, and the file's second
     * caption beside it on the station's page is the one that is not taken.
     *
     * MUTATION: compare the stations alone, without their page, and this fails.
     *
     * @throws Exception from the sessions
     */
    @Test
    public void testAnImportKeepsACaptionOnAnotherPageAndSkipsASecondOnThePage() throws Exception
    {
        TileKey shown = new TileKey("main", 1, 1);

        File exporting = Files.createTempDirectory("tc-autonomy-export").toFile();

        try
        {
            // The file: the station beside itself on its own page, and on the other page
            AutonomySession from = new AutonomySession(exporting);

            from.open(Arrays.asList(runOfTrack(), secondPage()));
            from.initialize("Evening");
            from.setStation(shown, true);
            from.setPointName(shown, "Hauptbahnhof");
            from.getStore().setCaption(new TileKey("main", 1, 2), shown);
            from.getStore().setCaption(new TileKey("second", 3, 2), shown);

            org.json.JSONObject bundle = from.getStore().exportBundle("Evening");

            // This setup: the station beside itself on its own page already
            session.open(Arrays.asList(runOfTrack(), secondPage()));
            session.initialize("Morning");
            session.setStation(shown, true);
            session.setPointName(shown, "Hauptbahnhof");
            session.setCaption(new TileKey("main", 1, 3), shown);

            session.importBundle("Imported", new org.json.JSONObject(bundle.toString()));

            assertEquals(session.captionsFor(shown),
                new LinkedHashSet<>(Arrays.asList(new TileKey("main", 1, 3), new TileKey("second", 3, 2))),
                "the import did not keep the other page's caption and skip the second on the station's page");

            assertTrue(shownTwice(session).isEmpty(), "the import left the setup with an error: " + session.check());
        }
        finally
        {
            delete(exporting);
        }
    }

    /**
     * Showing a station's name on another page ADDS a caption there; on the same page it MOVES the one that was there, and
     * Stop Showing takes off only the one clicked (OB-341).
     *
     * Adam, 2026-10-12, ruled that Show Station Here goes per page like the check: on a page where the station has no
     * caption it adds one and keeps the captions on other pages, and on the same page it still moves it, so each page
     * shows the name at most once.  `clearCaptions` takes off only the captions on the
     * new one's page, and `setCaption`, `placeCaption` and `moveCaption` go through it.
     *
     * MUTATION: clear the station's captions on every page, as before, and this fails at the first step.
     *
     * @throws Exception from the session
     */
    @Test
    public void testShowingAStationNameOnAnotherPageAddsAndOnTheSamePageMoves() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), secondPage()));

        TileKey station = new TileKey("main", 1, 1);
        TileKey beside = new TileKey("main", 1, 2);
        TileKey there = new TileKey("second", 3, 2);
        TileKey thereToo = new TileKey("second", 3, 3);

        session.getStore().setStation(station, true);
        session.getStore().setStation(new TileKey("main", 4, 1), true);
        session.setPointName(station, "Bahnhof");

        session.setCaption(station, station);

        // ANOTHER PAGE ADDS: the station's own caption stays where it was
        session.setCaption(there, station);

        assertEquals(session.captionsFor(station), new LinkedHashSet<>(Arrays.asList(station, there)),
            "showing the station on another page took its caption off its own page");

        // THE SAME PAGE MOVES: the one on that page goes, the other page's stays
        session.setCaption(beside, station);

        assertEquals(session.captionsFor(station), new LinkedHashSet<>(Arrays.asList(beside, there)),
            "showing the station on its own page again did not move the caption there, or took the other page's with it");

        session.setCaption(thereToo, station);

        assertEquals(session.captionsFor(station), new LinkedHashSet<>(Arrays.asList(beside, thereToo)),
            "showing the station on the other page again did not move that page's caption");

        // PLACING IT, on its own page: moves that page's, and leaves the other's
        assertNull(session.placeCaption(station), "there was room beside the platform");

        assertEquals(session.captionsFor(station).size(), 2, "placing the name beside the station left it shown "
            + session.captionsFor(station));

        assertTrue(session.captionsFor(station).contains(thereToo), "placing the name on the station's page took the"
            + " other page's caption off");

        // AND THE OWN PAGE'S MOVED (FR-136): a placement that did nothing leaves the caption on the square it was put on
        TileKey shown = null;

        for (TileKey where : session.captionsFor(station))
        {
            if (where.getPage().equals("main")) shown = where;
        }

        assertNotNull(shown, "placing the name left the station without a caption on its own page");

        assertNotEquals(shown, beside, "placing the name on the station's page did not move the caption off " + beside);

        // MOVING ONE (the drag's door): only on its own page
        session.moveCaption(thereToo, there);

        assertTrue(session.captionsFor(station).contains(there) && !session.captionsFor(station).contains(thereToo),
            "the caption did not move to the square on its own page: " + session.captionsFor(station));

        // STOP SHOWING takes off only the one clicked
        session.setCaption(there, null);

        assertEquals(session.captionsFor(station).size(), 1, "stopping the caption on the second page took the one on the"
            + " first as well: " + session.captionsFor(station));

        assertFalse(session.captionsFor(station).contains(there), "the caption that was stopped is still shown");
    }

    /**
     * A legacy label on another page still captions a station that is captioned on its own page, and one on a page that has
     * a caption of it does not (OB-256, OB-341).
     *
     * The migration captions a station only where it has none ON THAT PAGE.  The same-page meaning is
     * `testALegacyLabelDoesNotCaptionAStationTwice`; this is the counterpart on the next page, whose label becomes a
     * caption there and leaves the one on the first page.
     *
     * MUTATION: caption the label's square only when the station has no caption anywhere, as before, and this fails.
     *
     * @throws Exception from the session or the page files
     */
    @Test
    public void testALegacyLabelOnAnotherPageStillCaptionsAStationShownOnItsOwnPage() throws Exception
    {
        LayoutDiagram main = pageOnDisk();

        File secondFile = new File(new File(layout, "config/gleisbilder"), "second.cs2");

        Files.write(secondFile.toPath(), "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        LayoutDiagram second = new LayoutDiagram("second", 8, 4,
            "file:///" + secondFile.getAbsolutePath().replace('\\', '/'), null);

        second.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        second.setPageId("2");

        session.open(Arrays.asList(main, second));

        TileKey station = new TileKey("main", 1, 1);
        TileKey kept = new TileKey("main", 3, 2);
        TileKey labelled = new TileKey("second", 1, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(kept, station);
        session.save();

        // and the diagram of the OTHER page still carries an old-style label naming it
        second.addComponent(componentType.TEXT, 1, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "Bahnhof");

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(main, second));

        assertTrue(reopened.getMigrationFailures().isEmpty(),
            "precondition: the page should have been written: " + reopened.getMigrationFailures());

        assertEquals(reopened.captionsFor(station), new LinkedHashSet<>(Arrays.asList(kept, labelled)),
            "the label on the other page did not become a caption there, or took the first page's off");

        assertEquals(second.getComponent(1, 2).getLabel(), "", "the old label was left on the page");
    }

    /**
     * The old-file import captions a station on its own square only where it has no caption on its page (OB-256, OB-341).
     *
     * One station has a caption of a setup edited by hand on ANOTHER page, and is still captioned on its own square; the
     * other has one beside it on its own page, and is not.
     *
     * MUTATION: caption only a station with no caption anywhere, as before, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheOldFileImportCaptionsAStationOnlyWhereItHasNoneOnItsPage() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page, secondPage()));

        TileKey other = new TileKey("main", 1, 1);
        TileKey same = new TileKey("main", 4, 1);

        session.getStore().setCaption(new TileKey("second", 3, 2), other);
        session.getStore().setCaption(new TileKey("main", 4, 2), same);

        org.json.JSONArray points = new org.json.JSONArray();

        for (int s88 : new int[] {11, 12})
        {
            org.json.JSONObject point = new org.json.JSONObject();

            point.put("name", s88 == 11 ? "Hauptbahnhof" : "Nebenbahnhof");
            point.put("station", true);
            point.put("s88", s88);

            points.put(point);
        }

        org.json.JSONObject legacy = new org.json.JSONObject();
        legacy.put("points", points);

        session.importLegacy(legacy);

        assertEquals(session.captionsFor(other), new LinkedHashSet<>(Arrays.asList(new TileKey("second", 3, 2), other)),
            "a caption on another page kept the import from captioning the station on its own square");

        assertEquals(session.captionsFor(same), new LinkedHashSet<>(Arrays.asList(new TileKey("main", 4, 2))),
            "the import captioned a station that is captioned on its own page");
    }

    /** The setup check's findings that a station's name is drawn in more than one place. */
    private static List<org.traincontrol.automationui.AutonomyChecks.Finding> shownTwice(AutonomySession from)
    {
        List<org.traincontrol.automationui.AutonomyChecks.Finding> out = new ArrayList<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : from.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.STATION_NAMED_TWICE.equals(finding.getMessageKey()))
            {
                out.add(finding);
            }
        }

        return out;
    }

    /**
     * Asking to show a station's name moves the caption rather than adding a second one on the same page (OB-341: on
     * another page it adds one, which is `testShowingAStationNameOnAnotherPageAddsAndOnTheSamePageMoves`).
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
}
