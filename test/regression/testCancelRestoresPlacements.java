package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * Cancel in the track diagram editor puts back everything the edit took, not most of it.
 *
 * MT-072, answered 2026-08-22: "stations stay, locomotives are removed and no longer shown in the
 * labels" - and the 18 August answer to the same test said the same thing about labels.
 *
 * Cancel is two undos that have to agree. The DIAGRAM is undone by re-reading the pages from disk;
 * the SETUP is undone by restoring a snapshot taken when the window opened, because every gesture
 * that moves track writes the setup as it goes and there is nothing else to undo it with.
 *
 * The two halves are stored differently, and that is what makes this worth a test rather than a read:
 * a station lives in the shared half, in setup.json, and a locomotive PLACEMENT lives inside a
 * configuration file, under that Point. A snapshot that covers one and not the other looks completely
 * correct until somebody deletes a square that has both on it.
 *
 * This drives the session directly - the same forgetTiles the editor calls when a square is deleted -
 * so it needs no display and no railway.
 *
 * @author Adam
 */
public class testCancelRestoresPlacements
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-cancel-restore").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * The whole of MT-072: delete a square that carries settings, cancel, and get all of it back.
     */
    @Test
    public void testCancelPutsBackTheStationAndTheLocomotive() throws IOException
    {
        TileKey sensor = furnished();

        // What the window remembers when it opens, and what Cancel restores
        org.json.JSONObject asOpened = session.snapshotSetup();

        // The editor deleting that square
        session.forgetTiles(Collections.singletonList(sensor));

        assertNull(session.getLocomotiveNameAt(sensor),
            "the delete did not take the placement, so this test is not exercising the case");

        // Cancel
        assertTrue(session.restoreSetup(asOpened), "the restore did not reach the file");

        assertTrue(session.getStore().isStation(sensor),
            "the station did not come back - this half has always worked, so a failure here means "
            + "the restore did not run at all");

        assertEquals(session.getLocomotiveNameAt(sensor), "BR 218",
            "the STATION came back and the LOCOMOTIVE did not, which is exactly what MT-072 "
            + "reports: the two halves of a square are stored in different files and only one of "
            + "them is being put back");

        assertEquals(session.getStore().getPointName(sensor), "Bahnhof",
            "the name did not come back");
    }

    /**
     * And what Cancel restores survives a reload, which is the half the 18 August answer checked.
     *
     * "Confirmed the labels stay gone after reload" - so an in-memory restore that never reaches the
     * configuration file would pass the test above and fail the railway.
     */
    @Test
    public void testTheRestoreReachesTheFiles() throws IOException
    {
        TileKey sensor = furnished();

        org.json.JSONObject asOpened = session.snapshotSetup();

        session.forgetTiles(Collections.singletonList(sensor));

        session.restoreSetup(asOpened);

        // A second session over the same folder reads what is actually on disk
        AutonomySession reloaded = new AutonomySession(layout);

        reloaded.open(Arrays.asList(pageOnDisk()));

        assertTrue(reloaded.getStore().isStation(sensor), "the station is not in the files");

        assertEquals(reloaded.getLocomotiveNameAt(sensor), "BR 218",
            "the placement is not in the files, so it comes back until the next reload and then "
            + "goes - which is what was reported on 18 August");
    }

    /**
     * The order Cancel actually happens in, which is not the order the tests above use.
     *
     * The editor works on the LIVE LayoutDiagram objects the session is holding, so while an edit is in
     * progress the session's idea of the page is the half-finished one - and Cancel reverts by re-
     * reading the pages from disk into NEW objects, leaving the session holding the discarded version.
     * That is written down in testDiscardedEditsDoNotDeleteSetup's javadoc; this asks whether a
     * PLACEMENT survives it, which that test does not cover.
     *
     * So: place a train, take the snapshot the window takes, empty the page the way the editor's delete
     * does, save the way an edit saves as it goes, then restore the snapshot - and read the answer off
     * disk, because that is where the next session reads it from.
     */
    @Test
    public void testCancelSurvivesTheSessionHoldingTheEmptiedPage() throws IOException
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Bahnhof");
        session.getStore().setStation(sensor, true);
        session.placeLocomotive(sensor, "BR 218");

        org.json.JSONObject asOpened = session.snapshotSetup();

        // The editor deleting the squares: the setup is told, and the PAGE THE SESSION HOLDS is emptied
        session.forgetTiles(Collections.singletonList(sensor));

        // The same way testDiscardedEditsDoNotDeleteSetup empties one: a null component per square,
        // which is what the editor's delete leaves behind in the object the session is holding.
        for (org.traincontrol.base.LayoutDiagramComponent component
            : new java.util.LinkedList<>(page.getAll()))
        {
            page.addComponent((org.traincontrol.base.LayoutDiagramComponent) null,
                component.getX(), component.getY());
        }

        // Every gesture writes the setup as it goes - rememberAutonomy
        session.saveWithoutReconciling();

        // Cancel
        session.restoreSetup(asOpened);

        AutonomySession reloaded = new AutonomySession(layout);

        reloaded.open(Arrays.asList(pageOnDisk()));

        assertTrue(reloaded.getStore().isStation(sensor),
            "the station did not survive Cancel with the session holding the emptied page");

        assertEquals(reloaded.getLocomotiveNameAt(sensor), "BR 218",
            "the placement did not survive Cancel - MT-072 reports exactly this, stations staying "
            + "and locomotives going");
    }

    /**
     * An editing session that never ended is put back to how it was before it started (OB-108).
     *
     * Adam, 2026-08-25, choosing between three remedies: "ob-108 - revert to pre save state."
     *
     * The class above this test is the in-memory Cancel, and it covers every ordinary ending. What it
     * cannot cover is the one thing it is a snapshot against: **the process dying while the editor is
     * open.** The setup is written after every gesture, deliberately, because the session is rebuilt
     * from disk after each one; the diagram is only written at Save. So a crash leaves disk holding a
     * setup keyed to squares the user dragged and page files with the track still where it was, and the
     * next reconciling save prunes the difference as track that does not exist.
     *
     * A snapshot that lives in memory is lost by exactly the event it exists to survive.
     *
     * The crash is played by throwing the session away and opening a new one on the same folder, which
     * is all a restart is from the setup's point of view.
     *
     * MUTATION: making `rememberBeforeEdit` return false without writing the sidecar - so the note is
     * never taken - fails this test.  So does dropping the `forgetBeforeEdit` from `endEditSession`,
     * on the control below.
     */
    @Test
    public void testAnEditThatNeverFinishedIsPutBack() throws IOException
    {
        TileKey sensor = furnished();

        // The editor opening, which is where beginEditSession is called from
        assertTrue(session.beginEditSession(), "the note was not written, so nothing below tests it");

        // The edit, written as it goes the way every gesture writes it
        session.forgetTiles(Collections.singletonList(sensor));
        session.saveWithoutReconciling();

        assertNull(session.getLocomotiveNameAt(sensor),
            "the edit did not take, so this test is not exercising the case");

        // The crash: no Save, no Cancel, nothing.  A restart is a new session on the same folder.
        AutonomySession afterTheCrash = new AutonomySession(layout);

        afterTheCrash.open(Arrays.asList(pageOnDisk()));

        assertTrue(afterTheCrash.revertUnfinishedEdit(),
            "the unfinished edit was not noticed, so the setup would be left describing squares the "
            + "diagram never moved and the next save would prune them");

        assertEquals(afterTheCrash.getLocomotiveNameAt(sensor), "BR 218",
            "the placement did not come back after a crash mid-edit");

        assertTrue(afterTheCrash.getStore().isStation(sensor),
            "the station did not come back after a crash mid-edit");

        // And it is spent: a second start finds nothing to do, or every later start would keep
        // dragging the setup back to a state that is now several edits old.
        AutonomySession startedAgain = new AutonomySession(layout);

        startedAgain.open(Arrays.asList(pageOnDisk()));

        assertFalse(startedAgain.revertUnfinishedEdit(),
            "the note outlived the revert, so every subsequent start would undo the user again");
    }

    /**
     * A locomotive renamed while the note exists is renamed in the note too (SVN-B9).
     *
     * `LayoutEditor.autonomyLocomotiveRenamed` sweeps three holders - the snapshot Cancel restores and
     * both undo stacks - under a comment saying *"Cancel and Ctrl+Z are two ways of saying the same
     * thing, and only one of them was covered"*. There is a fourth, and it is the one that survives
     * the event the whole mechanism exists for: the note on disk.
     *
     * Rename a locomotive with the editor open, then let the process die. `revertUnfinishedEdit`
     * restores the note **and saves it**, so the pre-rename name goes back to disk - and a
     * configuration naming a locomotive that is not in the database is refused by `parseAuto`, which
     * invalidates the whole layout. The rename armed that, to go off at the next start.
     *
     * The repair sits on the store's own `repairLocomotive` funnel, which is where every rename and
     * every deletion already arrives, rather than at the editor: the note outlives the window that
     * wrote it.
     *
     * MUTATION: dropping the `repairTheUnfinishedEditNote` call from `repairLocomotive` fails this.
     */
    @Test
    public void testARenameReachesTheNoteOnDisk() throws IOException
    {
        TileKey sensor = furnished();

        assertTrue(session.beginEditSession(), "the note was not written, so nothing below tests it");

        assertEquals(session.getLocomotiveNameAt(sensor), "BR 218",
            "precondition: the note has to name a locomotive for a rename to reach");

        // The rename, through the door TrainControlUI uses.
        session.getStore().locomotiveRenamed("BR 218", "BR 218 II");
        session.getStore().save();

        // The crash: no Save, no Cancel.  A restart is a new session on the same folder.
        AutonomySession afterTheCrash = new AutonomySession(layout);

        afterTheCrash.open(Arrays.asList(pageOnDisk()));

        assertTrue(afterTheCrash.revertUnfinishedEdit(),
            "the unfinished edit was not noticed, so this test is not exercising the case");

        assertEquals(afterTheCrash.getLocomotiveNameAt(sensor), "BR 218 II",
            "the revert put the PRE-RENAME name back on the square, and saved it.  A configuration "
            + "naming a locomotive that is not in the database is refused by parseAuto, which "
            + "invalidates the whole layout - so renaming a locomotive with the editor open armed "
            + "that, to go off whenever the process next died (SVN-B9)");
    }

    /**
     * An editing session that DID end leaves nothing behind to revert (OB-108).
     *
     * The control for the test above, and the half that decides whether the mechanism is safe: the
     * whole meaning of the note is "if this is still here, the last session did not finish".  If a
     * clean Save left it behind, the next start would throw away the edit the user just saved - which
     * is a far worse failure than the one being fixed.
     *
     * Both endings are checked, because the editor has both and they are wired separately.
     */
    @Test
    public void testAnEditThatFinishedLeavesNothingBehind() throws IOException
    {
        TileKey sensor = furnished();

        // Ending one: Save.
        session.beginEditSession();
        session.forgetTiles(Collections.singletonList(sensor));
        session.saveWithoutReconciling();
        session.endEditSession();

        AutonomySession afterSave = new AutonomySession(layout);

        afterSave.open(Arrays.asList(pageOnDisk()));

        assertFalse(afterSave.revertUnfinishedEdit(),
            "a saved edit was treated as unfinished, so the next start would undo what the user just "
            + "saved");

        assertNull(afterSave.getLocomotiveNameAt(sensor),
            "the saved edit was reverted anyway");

        // Ending two: Cancel, which puts the setup back itself and then closes the session.
        org.json.JSONObject asOpened = afterSave.snapshotSetup();

        afterSave.beginEditSession();
        afterSave.setPointName(new TileKey("main", 3, 1), "Somewhere");
        afterSave.saveWithoutReconciling();
        afterSave.restoreSetup(asOpened);
        afterSave.endEditSession();

        AutonomySession afterCancel = new AutonomySession(layout);

        afterCancel.open(Arrays.asList(pageOnDisk()));

        assertFalse(afterCancel.revertUnfinishedEdit(),
            "a cancelled edit was treated as unfinished.  Cancel has already put the setup back, so a "
            + "second revert on top of it is undoing an undo");
    }

    /**
     * A pre-edit note that is not the right shape is refused, not applied (LD-4).
     *
     * `restoreSetup` was written for what `snapshotSetup` produces: it clears the store and then reads.
     * That is safe when the object is known to be the right shape, and unsafe the moment the contract
     * moves from memory to a FILE - written by another run, possibly by another build. Only "not JSON
     * at all" was handled.
     *
     * Review fed it four notes. `{}` threw out of `getJSONObject("shared")` after the clear, which
     * leaves autonomy dead for the rest of the session AND leaves the note in place to do it again at
     * every start. A version 99 one was accepted, where `load()` refuses exactly that with "silently
     * dropping fields it does not recognise would lose the user's work on the next save".
     *
     * Three of the four are handled. The fourth - well-formed but empty - turned out not to be
     * refusable at all, and the list below says why.
     *
     * So `unfinishedEdit` now makes the same checks `load()` makes, and throws away a note it cannot
     * use - because a note that cannot be applied protects nothing, and kept it would be re-read and
     * re-refused for ever with nothing said.
     *
     * MUTATION: removing the shape and version checks from `unfinishedEdit` fails this test on the
     * first note.
     */
    @Test
    public void testANoteThatCannotBeTrustedIsNotApplied() throws IOException
    {
        TileKey sensor = furnished();

        // On DISK, because everything below opens a fresh session that can only see what is there.
        session.saveWithoutReconciling();

        // "config/autonomy", not "autonomy" - the store's own FOLDER.  The first version of this test
        // wrote one directory up, so `unfinishedEdit` never saw any of these notes and three of the
        // four assertions below were satisfied by a file nothing was reading.  The fourth caught it.
        java.io.File note = new java.io.File(new java.io.File(layout, "config/autonomy"),
            "setup-before-edit.json");

        // NOT in this list: a well-formed but EMPTY note.
        //
        // Review offered it as a fourth bad case and it is not one. A note is a snapshot taken
        // when the editor opened, so an empty one is exactly what a setup that was empty at that
        // moment produces - and reverting to empty is then correct, which is the whole point
        // when the user built the setup during the edit and the process died. Nothing in the
        // file tells that apart from a corrupt note, and refusing it would break the case the
        // mechanism exists for.
        String[] rubbish =
        {
            "{}",
            "{\"shared\":{\"version\":99},\"configurations\":{}}",
            "not json at all",
        };

        for (String bad : rubbish)
        {
            note.getParentFile().mkdirs();

            java.nio.file.Files.write(note.toPath(),
                bad.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            AutonomySession restarted = new AutonomySession(layout);

            restarted.open(Arrays.asList(pageOnDisk()));

            assertFalse(restarted.revertUnfinishedEdit(),
                "a note this build cannot use was applied anyway: " + bad);

            assertEquals(restarted.getLocomotiveNameAt(sensor), "BR 218",
                "the setup was damaged by a note that could not be used: " + bad);

            assertTrue(restarted.getStore().isStation(sensor),
                "the station went with it: " + bad);

            // KEPT, not deleted - and this is the assertion that changed its mind.  A note this
            // build cannot read is one a different build wrote and can: a version 99 note belongs to
            // the newer TrainControl that will be run next, and deleting it on the way past would
            // throw away that build's safety net.  What must not happen is silence, so the session
            // can tell "there was no note" from "there was one and it was refused", and the interface
            // says the second out loud.
            assertTrue(note.isFile(),
                "a note this build cannot read was deleted rather than left for the build that can: "
                + bad);

            assertTrue(restarted.unusableEditNote(),
                "a refused note was indistinguishable from no note at all, so nothing can report it "
                + "and it is refused again at every start in silence: " + bad);
        }
    }

    /**
     * Deleting the whole setup deletes the note too, or the setup comes back (LD-4).
     *
     * `deleteEverything` removed every configuration and the setup itself and left the note. Two
     * consequences, and the second is the serious one: the folder is never empty, so the way back out
     * of having set autonomy up at all leaves it behind - and the next session build finds the note,
     * restores the whole deleted setup and saves it. The operator confirms a two-step deletion,
     * watches it happen, and gets all of it back on restart.
     *
     * MUTATION: dropping the note's delete from `deleteEverything` fails this test.
     */
    @Test
    public void testDeletingEverythingDeletesTheNoteToo() throws IOException
    {
        TileKey sensor = furnished();

        session.saveWithoutReconciling();

        // The precondition, stated rather than assumed: without it every assertion below - all of
        // which say something is GONE - would be satisfied by a setup that was never there.
        AutonomySession beforehand = new AutonomySession(layout);

        beforehand.open(Arrays.asList(pageOnDisk()));

        assertEquals(beforehand.getLocomotiveNameAt(sensor), "BR 218",
            "the fixture did not reach the disk, so this test would pass without deleting anything");

        assertTrue(session.beginEditSession(), "the note was not written, so nothing below tests it");

        session.getStore().deleteEverything();

        AutonomySession restarted = new AutonomySession(layout);

        restarted.open(Arrays.asList(pageOnDisk()));

        assertFalse(restarted.revertUnfinishedEdit(),
            "a deleted setup was put back by the note the editor left behind");

        assertNull(restarted.getLocomotiveNameAt(sensor),
            "the deleted setup came back after a restart");

        assertFalse(restarted.getStore().isStation(sensor),
            "the deleted station came back after a restart");
    }

    // ------------------------------------------------------------------------------------------

    /**
     * A sensor square with everything on it a square can carry: a name, a station, a locomotive.
     */
    private TileKey furnished() throws IOException
    {
        session.open(Arrays.asList(pageOnDisk()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Bahnhof");
        session.getStore().setStation(sensor, true);
        session.placeLocomotive(sensor, "BR 218");

        assertEquals(session.getLocomotiveNameAt(sensor), "BR 218", "the fixture did not take");

        return sensor;
    }

    private LayoutDiagram pageOnDisk() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    private void delete(File f)
    {
        if (f.isDirectory())
        {
            File[] kids = f.listFiles();

            if (kids != null) for (File kid : kids) delete(kid);
        }

        f.delete();
    }
}
