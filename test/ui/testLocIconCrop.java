package ui;

import java.io.File;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * FR-032: a crop remembers the picture it was cut from, so it can be cropped again.
 *
 * Adam: "once a custom icon is set, make it possible to crop/pan without first reselecting the
 * source."
 *
 * The wrinkle that makes this worth a test rather than a line of code: re-cropping the icon that is
 * SET means cropping a crop, and everything outside the last crop is already gone - so panning could
 * only ever go tighter, never back out. That is not what "crop/pan" means, and it would have been the
 * next ticket. A crop therefore keeps a note beside it naming the photograph it came from.
 *
 * Notes rot. The photograph is the user's, in the user's folder, and nothing here has any claim on it
 * - they may move it, rename it or delete it between one crop and the next. So the interesting cases
 * are the ones where the note is wrong, and they are what most of this checks.
 *
 * @author Adam
 */
public class testLocIconCrop
{
    /**
     * What is written beside a crop comes back, and what has gone does not.
     *
     * MUTATION: having `cropSourceOf` skip its `isFile` test - so a note naming a deleted photograph
     * is believed - fails the third case. Dropping the note in `deleteLocIconFile` fails the last.
     */
    @Test
    public void testACropRemembersWhereItCameFrom() throws Exception
    {
        // On the EDT, as every other test in this package builds one (C3).  A Swing window put
        // together off the event thread is a race that usually wins, which is the worst kind.
        final TrainControlUI[] built = new TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());

        TrainControlUI ui = built[0];

        File folder = java.nio.file.Files.createTempDirectory("fr032").toFile();

        folder.deleteOnExit();

        File crop = new File(folder, "crop.png");
        File source = new File(folder, "photograph.jpg");

        write(crop, "not really a png");
        write(source, "not really a jpeg");

        // A crop of OURS is the only kind that carries a note - `cropSourceOf` asks isLocIconFile
        // first, and a picture in the user's own folder is never ours to annotate.
        // The round trip, in the folder crops actually live in.
        // A name of its own, because tc_loc_icons is the REAL folder and already holds Adam's crops
        // (C3).  A fixed name races any other run - the battery runs this class while anything else
        // might be running it too - and two runs sharing one file fail each other for no reason.
        File ours = org.traincontrol.util.Util.getLocIconFile(
            "fr032_test_" + java.util.UUID.randomUUID() + ".png");

        if (ours == null)
        {
            throw new org.testng.SkipException("the icon folder could not be created here");
        }

        try
        {
            // Inside the try, so a failure here is tidied up like everything else (validator).
            assertNull(ui.cropSourceOf(crop.toPath().toUri().toString()),
                "a file outside the application's own icon folder came back with a source, which "
                + "would mean writing notes beside the user's own pictures");

            write(ours, "not really a png either");

            ui.rememberCropSource(ours, source);

            String url = ours.toPath().toUri().toString();

            assertEquals(ui.cropSourceOf(url), source,
                "what rememberCropSource wrote is not what cropSourceOf reads back, so re-cropping "
                + "would silently fall back to cropping the crop");

            // The photograph goes away, as the user's files may at any time.
            assertTrue(source.delete(), "could not delete the test photograph");

            assertNull(ui.cropSourceOf(url),
                "a note naming a photograph that is no longer there was believed. Re-cropping would "
                + "then try to read a file that does not exist rather than falling back to the crop");

            // And the note goes with the crop it belongs to.
            File note = new File(ours.getAbsolutePath() + ".source");

            write(source, "back again");
            ui.rememberCropSource(ours, source);

            assertTrue(note.exists(), "the note was not written at all");

            ui.deleteLocIcon(url);

            assertFalse(note.exists(),
                "the crop was deleted and its note was left behind - a file in the application's own "
                + "folder that nothing points at and nothing would ever remove");

            // AND THAT ANYBODY CALLS ANY OF IT (C4).
            //
            // Everything above tests the three helpers directly, and all of it passes with the whole
            // feature unwired: nothing fails if `cropLocIcon` stops writing the note, if `recropLocIcon`
            // stops reading it, or if re-crop stops deleting the crop it replaced. That is the same
            // fault this project has a name for - extracting a rule moves the defect to its call site -
            // and the OB-117 test had to be patched for it two days ago.
            //
            // Read rather than run: these are private, on a frame, driven by a modal dialog. What this
            // catches is the wiring being dropped, which is what a reader can break here.
            String wiring = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/org/traincontrol/gui/TrainControlUI.java")),
                java.nio.charset.StandardCharsets.UTF_8);

            assertTrue(bodyOf(wiring, "private String cropLocIcon(").contains("rememberCropSource("),
                "a crop is saved without noting where it came from, so re-cropping it can never pan "
                + "back out to anything the crop discarded");

            String recrop = bodyOf(wiring, "public void recropLocIcon(");

            assertTrue(recrop.contains("cropSourceOf("),
                "re-crop no longer looks for the original picture, so it always crops the crop");

            assertTrue(recrop.contains("cropSourceNoteOf("),
                "re-crop no longer READS the note when the picture is missing");

            // AND WRITES IT BACK, which is the half that carries the path.
            //
            // Checking only the read did not bind: a validator replaced the write-back with an empty
            // block and this test passed, with the data-loss defect fully restored - the old note is
            // still deleted, and the fresh one still names the crop. Reading a value and then not using
            // it is precisely the shape of that defect, so a test that only proves the read happened
            // proves nothing about it.
            assertTrue(recrop.contains("rememberCropSource(fresh, remembered)"),
                "re-crop reads the old note and never writes it over the fresh one, so the fresh note "
                + "names the crop this was cut from and the path to the photograph is gone the moment "
                + "the old crop is deleted - which is the whole of the defect this exists to stop");

            // In that ORDER: written after the new crop exists, before the old one is deleted.
            assertTrue(recrop.indexOf("rememberCropSource(fresh, remembered)")
                    < recrop.indexOf("deleteLocIconFile("),
                "the old crop is deleted before its note is carried forward, so the note being copied "
                + "is read from a file that has already gone");

            assertTrue(recrop.contains("deleteLocIconFile("),
                "re-crop leaves the crop it replaced behind, so the icon folder grows by one file "
                + "every time the user adjusts a crop");
        }
        finally
        {
            new File(ours.getAbsolutePath() + ".source").delete();

            ours.delete();
            crop.delete();
            source.delete();
            folder.delete();
        }
    }

    /**
     * A method's text, from its declaration to the next member.
     *
     * Bounded by the next member rather than by a closing brace: looking for a line separator plus a
     * brace assumes the file's line endings, and this one is written with LF while
     * System.lineSeparator() is CRLF here.
     *
     * @param source the whole file
     * @param declaration enough of the signature to find it
     * @return the text of that method
     */
    private String bodyOf(String source, String declaration)
    {
        int at = source.indexOf(declaration);

        assertTrue(at > 0, declaration + " is not in TrainControlUI - this test looks for nothing");

        int next = source.length();

        for (String start : new String[] {"    private ", "    public ", "    static "})
        {
            int found = source.indexOf(start, at + 10);

            if (found > 0 && found < next) next = found;
        }

        return source.substring(at, next);
    }

    /**
     * Writes a small file, since none of these are ever decoded as pictures.
     */
    private void write(File where, String what) throws Exception
    {
        java.nio.file.Files.write(where.toPath(),
            what.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
