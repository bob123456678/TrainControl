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

    /**
     * Pressing OK at full zoom-out does not allocate an image squared in the source (IPR-B4).
     *
     * `contentOf` built the whole overhanging rectangle at source resolution and then threw it away:
     * the output is 296 x 114. At `zoomFraction = 0` - the left end of the slider, and what
     * `setZoomFraction` clamps to - the scale is half the fit, so the rectangle is about twice the
     * source each way and its **area is quadratic in the source and independent of the dialog's**
     * **size**, because the window and the fit scale together. The finding's table: a 4032 x 3024
     * phone photograph asks for ~134 MB, an 8000 x 6000 one ~528 MB, 12000 x 9000 ~1.2 GB.
     *
     * The `OutOfMemoryError` lands in the OK listener on the EDT, so what the user sees is not a
     * crash: it is OK doing nothing and the dialog staying open.
     *
     * **Asserted on the allocation, not on the symptom.** Waiting for an OOM would need a JVM sized to
     * fail, which is a test that passes on a bigger machine for the wrong reason. `contentOf` is asked
     * directly, by reflection, what it built - and it must not be bigger than what the caller can use,
     * which is the icon.
     *
     * MUTATION: allocating `region.width x region.height` again fails the size assertion below.
     */
    @Test
    public void testTheCropAtFullZoomOutDoesNotAllocateTheSquareOfTheSource() throws Exception
    {
        final int OUT_WIDTH = 296;
        final int OUT_HEIGHT = 114;

        java.awt.image.BufferedImage source =
            new java.awt.image.BufferedImage(8000, 6000, java.awt.image.BufferedImage.TYPE_INT_RGB);

        // PAINTED, so the composition can be asserted and not only its size (VD9-C5).
        //
        // A blank source lets every assertion below pass against a bound that puts the photograph in
        // the wrong place - swapping the scale and the offset, which is the one mistake the code’s
        // own comment names, produces an image of identical dimensions.  Two blocks of different
        // colours make "where did it land" answerable.
        java.awt.Graphics2D paint = source.createGraphics();

        try
        {
            paint.setColor(java.awt.Color.RED);
            paint.fillRect(0, 0, 8000, 3000);
            paint.setColor(java.awt.Color.BLUE);
            paint.fillRect(0, 3000, 8000, 3000);
        }
        finally
        {
            paint.dispose();
        }

        org.traincontrol.gui.LocIconCropDialog.CropPanel panel =
            new org.traincontrol.gui.LocIconCropDialog.CropPanel(source, OUT_WIDTH, OUT_HEIGHT);

        panel.setSize(600, 420);
        panel.setZoomFraction(0.0);

        java.awt.Rectangle region = panel.sourceRect();

        // PRECONDITION, and it is the finding: at the left end of the slider the frame hangs off the
        // photograph in both directions, so the cheap getSubimage branch is never the one taken and
        // the rectangle asked for is bigger than the picture it is cut from.
        assertTrue(region.width > source.getWidth() && region.height > source.getHeight(),
            "the rectangle at full zoom-out no longer overhangs the source, so this test is not "
            + "exercising the branch it is about: " + region);

        java.lang.reflect.Method contentOf = org.traincontrol.gui.LocIconCropDialog.CropPanel.class
            .getDeclaredMethod("contentOf", java.awt.Rectangle.class);

        contentOf.setAccessible(true);

        java.awt.image.BufferedImage cut =
            (java.awt.image.BufferedImage) contentOf.invoke(panel, region);

        long asked = (long) cut.getWidth() * cut.getHeight() * 4L;
        long region_bytes = (long) region.width * region.height * 4L;

        assertTrue(cut.getWidth() <= OUT_WIDTH && cut.getHeight() <= OUT_HEIGHT,
            "the crop allocated " + cut.getWidth() + " x " + cut.getHeight() + " ("
            + (asked / (1024 * 1024)) + " MB) to produce a " + OUT_WIDTH + " x " + OUT_HEIGHT
            + " icon, from a rectangle of " + region.width + " x " + region.height + " ("
            + (region_bytes / (1024 * 1024)) + " MB).  That area is quadratic in the source and "
            + "independent of the dialog's size, so a large photograph runs the JVM out of memory "
            + "inside the OK listener on the EDT - and what the user sees is OK doing nothing "
            + "(IPR-B4)");

        // AND THE SHAPE IS KEPT, because the caller fits the cut into the icon and centres it: a
        // bound that squashed the rectangle would put the photograph in the frame stretched.
        double wanted = (double) region.width / region.height;
        double got = (double) cut.getWidth() / cut.getHeight();

        assertTrue(Math.abs(wanted - got) / wanted < 0.02,
            "the bounded crop is a different shape from the rectangle it stands for (" + wanted
            + " against " + got + "), so the picture would be stretched into the icon");

        // AND THE WHOLE WAY THROUGH still gives the icon that was asked for.
        java.awt.image.BufferedImage icon = panel.getCroppedImage();

        // Both of these are structural: every return path of getCroppedImage is CONSTRUCTED at the
        // icon size, so no value of `cut` can make them fail.  Kept as a shape check and not counted
        // as evidence (VD9-C5).
        assertEquals(icon.getWidth(), OUT_WIDTH, "the icon is the wrong width");
        assertEquals(icon.getHeight(), OUT_HEIGHT, "the icon is the wrong height");

        // AND THE PICTURE LANDED WHERE IT SHOULD, which is the assertion that can actually fail.
        //
        // The frame is centred on the source, so the middle of the cut is the middle of the
        // photograph - red above, blue below.  A bound that scaled without moving the offset with it,
        // or that translated before it scaled, puts the join somewhere else entirely while leaving
        // every size assertion above green.
        int middle = cut.getWidth() / 2;

        java.awt.Color above = new java.awt.Color(cut.getRGB(middle, cut.getHeight() / 2 - 1), true);
        java.awt.Color below = new java.awt.Color(cut.getRGB(middle, cut.getHeight() / 2 + 1), true);

        assertTrue(above.getRed() > above.getBlue(),
            "the top half of the crop is not the red half of the photograph, so the picture was not "
            + "composed where the frame is standing over it - the scale and the offset have come "
            + "apart (VD9-C5).  Found " + above);

        assertTrue(below.getBlue() > below.getRed(),
            "the bottom half of the crop is not the blue half of the photograph.  Found " + below);
    }

    /**
     * The picture's own tool is a wrench in its upper left that chooses the icon, and the pointer over it is the plain one
     * (FR-104).
     *
     * Adam, 2026-09-29: *"instead of "right click to change icon", add a wrench icon to the upper-left of the locomotive
     * icon."*  And on the pointer: *"change the cover icon for the loc icon from the hand to regular"*.  The right-click
     * still opens the same chooser; the sentence telling you to right-click is gone with the need for it.
     *
     * MUTATION: leave the hand on the picture, or wire the wrench to nothing, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheWrenchChoosesTheIconAndThePointerIsPlain() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            // OPENED INSIDE THE TRY (TSX-B8, OB-111)
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            ui = windowOver(model);

            final javax.swing.JLabel picture = field(ui, "locIcon", javax.swing.JLabel.class);

            assertEquals(picture.getCursor().getType(), java.awt.Cursor.DEFAULT_CURSOR, "the pointer over the locomotive"
                + " picture is still a hand - Adam, FR-104: \"change the cover icon for the loc icon from the hand to"
                + " regular\"");

            assertNotEquals(picture.getToolTipText(), org.traincontrol.util.I18n.t("ui.main.tooltip.locIcon"),
                "the picture still says to right-click it, where the wrench now does it (FR-104)");

            final javax.swing.JLabel wrench = field(ui, "iconWrench", javax.swing.JLabel.class);
            final javax.swing.JLabel crop = field(ui, "cropOverlay", javax.swing.JLabel.class);

            assertTrue(wrench.getToolTipText() != null && !wrench.getToolTipText().trim().isEmpty(), "the wrench says"
                + " nothing about what it does");

            // THE UPPER LEFT, with the crop mark in the upper right: both shown, as a hover shows them, and laid out
            final java.awt.Point[] at = new java.awt.Point[2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                wrench.setVisible(true);
                crop.setVisible(true);
                picture.setSize(296, 116);
                picture.invalidate();
                picture.validate();

                at[0] = javax.swing.SwingUtilities.convertPoint(wrench, 0, 0, picture);
                at[1] = javax.swing.SwingUtilities.convertPoint(crop, 0, 0, picture);
            });

            assertTrue(javax.swing.SwingUtilities.isDescendingFrom(wrench, picture), "the wrench is not on the picture");

            assertTrue(at[0].x < picture.getWidth() / 4 && at[0].y < picture.getHeight() / 4, "the wrench is not in the"
                + " picture's upper left - it is at " + at[0] + " (FR-104)");

            assertTrue(at[0].x < at[1].x, "the wrench is not left of the crop mark: " + at[0] + " against " + at[1]);

            // A LEFT CLICK ON IT OPENS THE ICON CHOOSER, for the locomotive on show
            final org.traincontrol.marklin.MarklinLocomotive loc = new org.traincontrol.marklin.MarklinLocomotive(model,
                84, org.traincontrol.marklin.MarklinLocomotive.decoderType.MM2, "FR-104 wrench");

            java.lang.reflect.Field active = TrainControlUI.class.getDeclaredField("activeLoc");

            active.setAccessible(true);
            active.set(ui, loc);

            javax.swing.SwingUtilities.invokeAndWait(() -> wrench.dispatchEvent(new java.awt.event.MouseEvent(wrench,
                java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_MASK,
                5, 5, 1, false, java.awt.event.MouseEvent.BUTTON1)));

            final javax.swing.JFileChooser chooser = awaitAChooser(10000);

            assertNotNull(chooser, "a left click on the wrench opened no icon chooser (FR-104)");

            javax.swing.SwingUtilities.invokeAndWait(chooser::cancelSelection);
        }
        finally
        {
            closeTheWindow(ui);

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A picture from the Central Station can be cropped here (FR-104).
     *
     * Adam, 2026-09-29: *"also, make central station supplied icons croppable locally."*  The crop mark was offered only on
     * a picture of this computer's, and re-cropping refused anything else, so a locomotive showing the Central Station's
     * own picture had nothing to crop with.  The crop is kept here, as every crop is; the Central Station's picture is
     * kept beside it to crop again from, and never as one of the crops that replacing a crop deletes.
     *
     * MUTATION: crop nothing for a Central Station picture, or offer the mark only on a picture of this computer's, and
     * this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testACentralStationPictureCanBeCropped() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        TrainControlUI ui = null;

        String crop = null;
        File kept = null;

        try
        {
            // OPENED INSIDE THE TRY (TSX-B8, OB-111)
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            ui = windowOver(model);

            // THE CENTRAL STATION'S PICTURE, as a URL the way the station's are
            File folder = java.nio.file.Files.createTempDirectory("fr104").toFile();

            folder.deleteOnExit();

            File station = new File(folder, "BR 101.png");

            java.awt.image.BufferedImage drawn = new java.awt.image.BufferedImage(400, 160,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = drawn.createGraphics();

            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, 400, 80);
            g.setColor(java.awt.Color.BLUE);
            g.fillRect(0, 80, 400, 80);
            g.dispose();

            assertTrue(javax.imageio.ImageIO.write(drawn, "png", station), "precondition: the picture was not written");

            station.deleteOnExit();

            final org.traincontrol.marklin.MarklinLocomotive loc = new org.traincontrol.marklin.MarklinLocomotive(model,
                85, org.traincontrol.marklin.MarklinLocomotive.decoderType.MM2, "FR-104 station picture");

            loc.setImageURL(station.toURI().toString());

            assertNull(loc.getLocalImageURL(), "precondition: the locomotive has a picture of this computer's already");

            // THE CROP MARK'S DOOR
            ui.recropLocIcon(loc, ui);

            final javax.swing.JDialog window = awaitADialogTitled(org.traincontrol.util.I18n.t("loc.ui.cropTitle"), 15000);

            assertNotNull(window, "no crop window opened for a Central Station picture (FR-104)");

            final javax.swing.AbstractButton ok = buttonIn(window, org.traincontrol.util.I18n.t("ui.ok"));

            assertNotNull(ok, "the crop window has no OK");

            javax.swing.SwingUtilities.invokeAndWait(ok::doClick);

            long giveUp = System.currentTimeMillis() + 10000;

            while (loc.getLocalImageURL() == null && System.currentTimeMillis() < giveUp) Thread.sleep(50);

            crop = loc.getLocalImageURL();

            assertNotNull(crop, "OK on the crop of a Central Station picture set no icon (FR-104)");

            assertTrue(org.traincontrol.util.Util.isLocIconFile(crop), "the crop is not kept among this computer's icons: "
                + crop);

            // AND THE STATION'S PICTURE KEPT TO CROP AGAIN FROM, outside the crops that are tidied away
            kept = ui.cropSourceOf(crop);

            assertNotNull(kept, "the crop remembers no picture to crop again from, so a second crop could only go"
                + " tighter");

            assertFalse(org.traincontrol.util.Util.isLocIconFile(kept.toURI().toString()), "the Central Station's picture"
                + " is kept among the crops, where replacing the crop would delete it: " + kept);

            assertTrue(kept.getCanonicalPath().startsWith(new File(org.traincontrol.util.Util.LOC_ICON_FOLDER)
                .getCanonicalPath()), "the Central Station's picture is kept outside the application's icon folder: "
                + kept);

            java.awt.image.BufferedImage whole = javax.imageio.ImageIO.read(kept);

            assertTrue(whole != null && whole.getWidth() == 400 && whole.getHeight() == 160, "what is kept to crop again"
                + " from is not the whole of the Central Station's picture");

            // AND THE MARK IS OFFERED OVER SUCH A PICTURE - and over no picture at all, nothing
            java.lang.reflect.Method offers = TrainControlUI.class.getDeclaredMethod("offersACrop",
                org.traincontrol.base.Locomotive.class);

            offers.setAccessible(true);

            loc.setLocalImageURL(null);

            assertTrue((Boolean) offers.invoke(null, loc), "the crop mark is not offered over a Central Station picture"
                + " (FR-104)");

            assertFalse((Boolean) offers.invoke(null, new org.traincontrol.marklin.MarklinLocomotive(model, 86,
                org.traincontrol.marklin.MarklinLocomotive.decoderType.MM2, "FR-104 no picture")), "the crop mark is"
                + " offered over a locomotive with no picture at all");
        }
        finally
        {
            if (crop != null && ui != null) ui.deleteLocIcon(crop);

            if (kept != null) kept.delete();

            closeTheWindow(ui);

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /** The main window over a model, as the application builds it. */
    private static TrainControlUI windowOver(final org.traincontrol.marklin.MarklinControlStation model) throws Exception
    {
        final TrainControlUI[] made = new TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                made[0] = new TrainControlUI();
                made[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        return made[0];
    }

    private static void closeTheWindow(final TrainControlUI ui) throws Exception
    {
        if (ui != null) javax.swing.SwingUtilities.invokeAndWait(ui::dispose);
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception
    {
        java.lang.reflect.Field f = owner.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return type.cast(f.get(owner));
    }

    /** A file chooser showing in a window, within the time, or null. */
    private static javax.swing.JFileChooser awaitAChooser(long millis) throws Exception
    {
        long giveUp = System.currentTimeMillis() + millis;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                javax.swing.JFileChooser found = chooserIn(((javax.swing.JDialog) window).getContentPane());

                if (found != null) return found;
            }

            Thread.sleep(50);
        }

        return null;
    }

    private static javax.swing.JFileChooser chooserIn(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JFileChooser) return (javax.swing.JFileChooser) child;

            if (child instanceof java.awt.Container)
            {
                javax.swing.JFileChooser found = chooserIn((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    /** A showing dialog titled so, within the time, or null. */
    private static javax.swing.JDialog awaitADialogTitled(String title, long millis) throws Exception
    {
        long giveUp = System.currentTimeMillis() + millis;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) return (javax.swing.JDialog) window;
            }

            Thread.sleep(50);
        }

        return null;
    }

    private static javax.swing.AbstractButton buttonIn(java.awt.Container container, String text)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.AbstractButton && text.equals(((javax.swing.AbstractButton) child).getText()))
            {
                return (javax.swing.AbstractButton) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.AbstractButton found = buttonIn((java.awt.Container) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }
}
