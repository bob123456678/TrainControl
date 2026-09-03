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
        // The window reads the layout preference in its constructor, so without this it opens the
        // operator's own railway (OB-111).
        support.LayoutSandbox sandbox = null;

        try
        {
            // INSIDE the try, because everything below it throws (TSX-B8).
            //
            // The window constructor, the temporary folder, the writes - and the
            // SkipException below, which is not a failure at all but an ordinary
            // outcome on a machine where the icon folder cannot be made.  Opened
            // outside, every one of those left the machine-global layout preference
            // pointing at a folder under %TEMP%, which is the railway TrainControl
            // opens next time (OB-111).
            sandbox = support.LayoutSandbox.open();

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
        finally
        {
            if (sandbox != null) sandbox.close();
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

    /**
     * An unreadable picture is refused whether or not Crop is ticked.
     *
     * The guard for this shipped inside the `if (crop.isSelected())` branch, so untick Crop and pick a
     * CMYK JPEG or a `.png` that is really something else - both get past the chooser's extension
     * filter - and nothing read the file at all: it was assigned, and the crop it superseded was
     * deleted. The note recording this bug was sitting on that same unguarded branch (validator,
     * 2026-08-28).
     *
     * MUTATION: moving the call back inside the crop branch fails this, and so does removing it.
     */
    @Test
    public void testAnUnreadablePictureIsRefusedWithoutCropping() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int asks = source.indexOf("pictureCanBeRead(f, source)");
        int ticked = source.indexOf("if (crop.isSelected())");

        // Both present before either is ordered - indexOf answers -1 for something absent, and -1 is
        // less than every real index, so the ordering alone passes when the guard is deleted outright.
        assertTrue(asks >= 0,
            "nothing asks whether the picked picture can be read, so a file that renders as nothing "
            + "is assigned to the locomotive and the crop it replaces is deleted");

        assertTrue(ticked >= 0,
            "the crop branch has moved or been renamed, so this check can no longer tell which side "
            + "of it the guard is on - look at setLocIcon before trusting anything here");

        assertTrue(asks < ticked,
            "the readability guard is inside the crop branch again, so it only runs when Crop is "
            + "ticked - untick it and the locomotive is pointed at an unreadable file and the crop it "
            + "had is deleted, which is the whole defect one checkbox away from where it was fixed");

        // And the note that recorded the bug is not left standing over the fixed code.
        assertFalse(source.contains("clear icon setting if load failed"),
            "the note describing this bug is still in setLocIcon - either it was never closed, or it "
            + "has been quoted back into a comment where the next reader will take it for an open one");
    }

    /**
     * The note carries the view, and an older note still reads as a path (OB-125).
     *
     * Adam: "if an image is cropped, upon re edit, the crop editor initially shows the default
     * zoom/crop instead of the active crop."
     *
     * The compatibility half is the part worth writing down. Every note FR-032 wrote is a bare path
     * with no second line, and every crop Adam already has carries one - so a reader that needs two
     * lines would have quietly broken re-cropping for the whole existing set.
     *
     * MUTATION: reading the note with `trim()` over the whole file rather than taking the first line
     * fails the path assertion, because the view line comes back as part of the filename.
     */
    @Test
    public void testACropRemembersTheViewItWasTakenAt() throws Exception
    {
        // The window reads the layout preference in its constructor, so without this it opens the
        // operator's own railway (OB-111).
        support.LayoutSandbox sandbox = null;

        try
        {
            // INSIDE the try, because everything below it throws (TSX-B8).
            //
            // The window constructor, the temporary folder, the writes - and the
            // SkipException below, which is not a failure at all but an ordinary
            // outcome on a machine where the icon folder cannot be made.  Opened
            // outside, every one of those left the machine-global layout preference
            // pointing at a folder under %TEMP%, which is the railway TrainControl
            // opens next time (OB-111).
            sandbox = support.LayoutSandbox.open();

            final TrainControlUI[] built = new TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());

            TrainControlUI ui = built[0];

            File folder = java.nio.file.Files.createTempDirectory("ob125").toFile();

            folder.deleteOnExit();

            File source = new File(folder, "photograph.jpg");

            write(source, "not really a jpeg");

            File ours = org.traincontrol.util.Util.getLocIconFile(
                "ob125_test_" + java.util.UUID.randomUUID() + ".png");

            if (ours == null)
            {
                throw new org.testng.SkipException("the icon folder could not be created here");
            }

            try
            {
                write(ours, "not really a png");

                String url = ours.toPath().toUri().toString();

                // AN OLDER NOTE - a bare path, which is every note written before this.
                ui.rememberCropSource(ours, source);

                assertEquals(ui.cropSourceOf(url), source,
                    "a note without a view no longer reads back as a path, so every crop made before "
                    + "this change has lost the photograph behind it");

                assertNull(ui.cropViewOf(url),
                    "a note with no view line answered with one anyway, so the panel would be opened on "
                    + "numbers nobody wrote");

                // AND ONE WITH A VIEW.
                double[] view = { 250.5, 200.25, 0.4, 2.5, 0.7 };

                ui.rememberCropSource(ours, source, view);

                assertEquals(ui.cropSourceOf(url), source,
                    "the path no longer reads back once a view is stored beside it - the whole file is "
                    + "being taken for a filename, so re-crop would look for a photograph whose name has "
                    + "the view appended to it");

                double[] read = ui.cropViewOf(url);

                assertNotNull(read, "the view was written and did not come back");

                for (int i = 0; i < 5; i++)
                {
                    assertEquals(read[i], view[i], 1e-9,
                        "the view came back changed at position " + i + " - it was written as "
                        + view[i] + " and read as " + read[i]);
                }

                // A note that says something else on its second line is not a view.
                ui.rememberCropSource(ours, source);

                assertNull(ui.cropViewOf(url),
                    "re-recording the source without a view left the OLD view in place, so the panel "
                    + "would open on where a crop that no longer exists was taken");
            }
            finally
            {
                ui.deleteLocIcon(ours.toPath().toUri().toString());

            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The panel opens on the view it is given, and on the covering crop when it is given none.
     *
     * Driven with no display, which the panel is built for - "it can be constructed, given a size and
     * painted into a BufferedImage with no display attached". `getScale` is what settles the opening
     * view, so asking for it is what makes this happen.
     *
     * The control matters more than the assertion here: without it, a `setView` that did nothing at
     * all would pass if the default happened to be close.
     *
     * MUTATION: applying the view in `setView` rather than deferring it to `startAtCover` fails this,
     * because the opening view then lands on top of it - which is the defect `setZoomFraction`
     * already carries a comment about.
     */
    @Test
    public void testTheCropPanelOpensOnARememberedView() throws Exception
    {
        java.awt.image.BufferedImage picture =
            new java.awt.image.BufferedImage(800, 600, java.awt.image.BufferedImage.TYPE_INT_RGB);

        double[] wanted = { 250.5, 200.25, 0.4, 2.5, 0.7 };

        org.traincontrol.gui.LocIconCropDialog.CropPanel restored =
            new org.traincontrol.gui.LocIconCropDialog.CropPanel(picture, 100, 50);

        restored.setSize(600, 420);
        restored.setView(wanted);
        restored.getScale();

        double[] got = new double[5];

        restored.copyViewInto(got);

        // THE CONTROL: what the same panel does with no view to open on.
        org.traincontrol.gui.LocIconCropDialog.CropPanel plain =
            new org.traincontrol.gui.LocIconCropDialog.CropPanel(picture, 100, 50);

        plain.setSize(600, 420);
        plain.getScale();

        double[] byDefault = new double[5];

        plain.copyViewInto(byDefault);

        boolean differs = false;

        for (int i = 0; i < 5; i++)
        {
            if (Math.abs(byDefault[i] - wanted[i]) > 1e-6) differs = true;
        }

        assertTrue(differs,
            "the view being asked for is the one the panel opens on anyway, so this test would pass "
            + "with setView doing nothing at all - pick a view that is not the default");

        for (int i = 0; i < 5; i++)
        {
            assertEquals(got[i], wanted[i], 1e-6,
                "the panel did not open on the remembered view at position " + i + ": asked for "
                + wanted[i] + ", opened on " + got[i] + " (the default there is " + byDefault[i]
                + "). Re-editing a crop therefore starts from the default framing again, which is "
                + "OB-125");
        }

        // An unusable view is ignored rather than believed.
        org.traincontrol.gui.LocIconCropDialog.CropPanel nonsense =
            new org.traincontrol.gui.LocIconCropDialog.CropPanel(picture, 100, 50);

        nonsense.setSize(600, 420);
        nonsense.setView(new double[] { 1, 2, Double.NaN, 4, 5 });
        nonsense.getScale();

        double[] fallback = new double[5];

        nonsense.copyViewInto(fallback);

        for (int i = 0; i < 5; i++)
        {
            assertEquals(fallback[i], byDefault[i], 1e-6,
                "a view with a NaN in it was applied rather than ignored, so a half-written note "
                + "opens the panel on nothing at position " + i);
        }
    }

    /**
     * The view is handed in only when the re-crop works from the photograph it was measured over.
     *
     * The fallback crops the crop itself. The same numbers point somewhere else in that picture, so
     * opening on them would be worse than opening on the default - and the note rewritten in that
     * branch names the photograph, not the crop, so no view may be stored against it either.
     */
    @Test
    public void testTheViewOnlyTravelsWithItsOwnPicture() throws Exception
    {
        String wiring = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(wiring.contains("fromOriginal ? cropViewOf(l.getLocalImageURL()) : null)"),
            "re-crop no longer asks for the stored view, or asks for it whatever it is cropping - "
            + "either the editor opens on the default again, or it opens the crop at coordinates "
            + "measured on the photograph behind it");

        assertTrue(wiring.contains("rememberCropSource(fresh, remembered)"),
            "the fallback branch now stores a view against the note it rewrites - that note names "
            + "the photograph, and the view was measured over the crop, so the next re-crop of the "
            + "original would open somewhere arbitrary");

        assertTrue(wiring.contains("rememberCropSource(target, chosen, view)"),
            "the crop no longer records the view it was taken at, so nothing is ever stored and "
            + "re-editing always opens on the default");
    }
}
