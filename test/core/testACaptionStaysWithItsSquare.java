package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * A station's caption is written on a page, kept inside the drawn area, carried by its square and taken away
 * with it.
 *
 * One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136 Phase 3): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder
 * every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testACaptionStaysWithItsSquare extends AutonomySessionFixture
{


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
     * A legacy label naming a station the setup already shows on its page does not show it twice (OB-256; on another page
     * it becomes a caption there, which is `testALegacyLabelOnAnotherPageStillCaptionsAStationShownOnItsOwnPage`).
     *
     * One caption per station per page: `setCaption` takes a station's caption off wherever it was on that page, and the
     * old-file import captions a station only on a page where it has none.  The migration of `Point:` labels wrote through the store's own door,
     * which does neither, so a station the setup already captioned gained a second caption on the label's square.  The
     * setup's caption is the one somebody chose, so it is kept; the label still leaves the page, or the migration would
     * find it again at every open.
     *
     * MUTATION: caption the label's square whether or not the station has a caption, as before, and this fails.
     *
     * @throws Exception from the session or the page file
     */
    @Test
    public void testALegacyLabelDoesNotCaptionAStationTwice() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey kept = new TileKey("main", 3, 2);
        TileKey labelled = new TileKey("main", 1, 2);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setCaption(kept, station);
        session.save();

        // and the diagram still carries an old-style label naming it, on another square
        page.addComponent(componentType.TEXT, 1, 2, 0, 0, 0, 0, accessoryDecoderType.MM2,
            AutonomySession.STATION_LABEL_PREFIX + "Bahnhof");

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(page));

        assertTrue(reopened.getMigrationFailures().isEmpty(),
            "precondition: the page should have been written: " + reopened.getMigrationFailures());

        assertEquals(reopened.captionsFor(station).size(), 1, "the station is captioned on "
            + reopened.captionsFor(station) + ": the migration added a caption beside the one the setup had (OB-256)");

        assertEquals(reopened.getCaptionTarget(kept), station, "the setup's own caption was not the one kept");

        assertNull(reopened.getCaptionTarget(labelled), "the label's square was captioned as well");

        assertEquals(page.getComponent(1, 2).getLabel(), "",
            "the old label was left on the page, so the migration finds it again at every open");
    }

    /**
     * The session says which pages the migration rewrote, and how many names it took.
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
        // ON THE SAME SESSION, which is the only version of this that can fail.  A brand-new
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

        // A station hard against the top-left of the drawn area, with its only neighbor to the east a curve (FR-136).
        // Beside a straight the name went on that straight and the search for blank space was never reached, which is
        // where the square outside the drawn area was found; with no plain track to put it on, the blank squares are
        // looked at, and the ones outside have to be refused.
        page.addComponent(componentType.FEEDBACK, 0, 0, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CURVE, 1, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.setPageId("1");
        page.checkBounds();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 0, 0);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Kopfbahnhof");

        assertNull(session.placeCaption(station), "the name found no square at all, even the station's own");

        assertFalse(session.getCaptions().isEmpty(), "placing the name left no caption, so none can be outside anything");

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
     * The migration leaves an unrecognized "Point:" label exactly where it is - deleting it would
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
     * A caption is never placed outside the area the RUNNING diagram draws, with the page in edit mode too.
     *
     * `testACaptionIsNeverPlacedOutsideTheDrawnArea` builds its page with edit mode off.  The autonomy editor puts the page
     * object the session shares into edit mode, where its bounds are the whole page - and a caption is not a component, so
     * the guard read the whole page and filed the caption on a blank square the running diagram never draws.  The area is
     * worked out from the components alone, as the running grid does.
     *
     * MUTATION: read the page's own bounds in `placeCaption`, as before, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testACaptionIsNeverPlacedOutsideTheAreaTheRunningDiagramDrawsWithThePageInEditMode() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        // A station hard against the top-left corner, its only neighbor a curve (so no plain track to put the name on) and
        // nothing below or beside it that carries a component
        page.addComponent(componentType.FEEDBACK, 0, 0, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CURVE, 1, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.setPageId("1");
        page.setEdit(true);
        page.checkBounds();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 0, 0);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Kopfbahnhof");

        assertNull(session.placeCaption(station), "the name found no square at all, even the station's own");

        assertFalse(session.getCaptions().isEmpty(), "placing the name left no caption, so none can be outside anything");

        for (TileKey where : session.getCaptions().keySet())
        {
            assertTrue(where.getX() >= 0 && where.getX() <= 1 && where.getY() == 0,
                "a caption at " + where + " is outside the area the running diagram draws, the part its components cover,"
                + " so nothing shows it there");
        }
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
        // A SQUARE THAT GENUINELY BECOMES TWO POINTS.
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
     * the real snapshot.  restoreCaptionsOnPage was deleted as uncalled; captionsOnPage has
     * one caller, LayoutEditor.forgetCaptionsOutsideThePage, which wants the keys and not the round
     * trip.  MUTATION this catches: remove "captions" from kept()
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
     * LayoutEditor.forgetCaptionsOutsideThePage - and it is not an undo path.
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
     * A caption kept for a square that is not a station now survives the setup being opened, undrawn, and is drawn again
     * once the square is a station.
     *
     * Opening used to forget every caption naming a square that is not a station - a clean-up for setups written before
     * demoting took the caption with it.  Adam, 2026-10-10, asked for the opposite rule: keep them, ignored while not a
     * station - so that clean-up would throw away, at the next start, what demoting now keeps.  This claim replaces the
     * one that said opening forgot them.
     *
     * The square is demoted behind the session's back, as a setup written by another build would arrive.
     *
     * MUTATION: forget non-stations' captions in `open` again, and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testOpeningKeepsTheCaptionOfASquareThatIsNotAStationNow() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey caption = new TileKey("main", 1, 2);

        session.setStation(station, true);
        session.setCaption(caption, station);

        session.getStore().setStation(station, false);
        session.save();

        AutonomySession reopened = new AutonomySession(layout);
        reopened.open(Arrays.asList(pageOnDisk()));

        assertNull(reopened.getCaptionTarget(caption), "a caption kept for a square that is not a station is drawn");

        assertEquals(reopened.getStore().getCaptionTarget(caption), station, "opening the setup forgot the caption of a"
            + " square that is not a station now, which is kept for when it is one again (GSE-C4)");

        reopened.setStation(station, true);

        assertEquals(reopened.getCaptionTarget(caption), station, "the square is a station again and its caption is not"
            + " drawn (GSE-C4)");
    }
}
