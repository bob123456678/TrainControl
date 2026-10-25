package org.traincontrol.gui;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.locomotive.LocIconCropDialog;
import org.traincontrol.gui.widgets.QuickFileChooser;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * Locomotive icons of this computer's: choosing one, cropping it (FR-022), re-cropping it from the picture it was cut
 * from (FR-032, OB-125), cropping the Central Station's picture (FR-104), and deleting the crops nothing points at any
 * more; with the marks drawn over the big picture (FR-032, FR-104, MT-591).
 *
 * The hover tools over the picture, and every field, stay on the window.
 */
final class LocIconActions
{
    private final TrainControlUI ui;

    LocIconActions(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * The revert, drawn as the wrench is: a circular arrow, most of a ring with a solid head where it ends (MT-591; Adam: "make the arrow
     * at the end of the circle more prominent, right now it doesn't really look like an arrow"): two thin barbs off the end of the ring
     * read as a hook, so the head is a filled triangle, wider than the ring is thick, pointing the way the ring runs.
     */
    static final class RevertMark implements javax.swing.Icon
    {
        private static final int SIZE = 15;

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }

        @Override
        public void paintIcon(java.awt.Component host, java.awt.Graphics g, int x, int y)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL,
                java.awt.RenderingHints.VALUE_STROKE_PURE);

            double middle = SIZE / 2.0;
            double radius = 4.5;

            for (int pass = 0; pass < 2; pass++)
            {
                g2.setColor(pass == 0 ? new java.awt.Color(255, 255, 255, 200)
                    : new java.awt.Color(51, 51, 51));
                g2.setStroke(new java.awt.BasicStroke(pass == 0 ? 3.4f : 1.8f,
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

                // The ring, open at the top right, turning anticlockwise
                g2.draw(new java.awt.geom.Arc2D.Double(x + middle - radius, y + middle - radius, radius * 2, radius * 2,
                    60, 270, java.awt.geom.Arc2D.OPEN));

                // THE HEAD: its outline in the underlay's pass, so it reads on any picture, and filled in the ink's
                java.awt.Shape head = java.awt.geom.AffineTransform.getTranslateInstance(x, y).createTransformedShape(head());

                if (pass == 0) g2.draw(head);
                else g2.fill(head);
            }

            g2.dispose();
        }

        /**
         * The solid head at the end of the ring, in the icon's own coordinates: a triangle whose base straddles the
         * ring's end and whose tip points the way the ring runs there - up and to the right, the ring turning
         * anticlockwise to 330 degrees.
         *
         * @return the head
         */
        static java.awt.Shape head()
        {
            double middle = SIZE / 2.0;
            double radius = 4.5;

            double endX = middle + radius * Math.cos(Math.toRadians(330));
            double endY = middle - radius * Math.sin(Math.toRadians(330));

            // The way the ring runs at its end, and across it
            double runX = Math.sin(Math.toRadians(330)) * -1;
            double runY = Math.cos(Math.toRadians(330)) * -1;
            double acrossX = -runY;
            double acrossY = runX;

            double reach = 3.4;
            double half = 2.6;

            java.awt.geom.Path2D.Double head = new java.awt.geom.Path2D.Double();

            head.moveTo(endX + runX * reach, endY + runY * reach);
            head.lineTo(endX + acrossX * half, endY + acrossY * half);
            head.lineTo(endX - acrossX * half, endY - acrossY * half);
            head.closePath();

            return head;
        }
    }

    /**
     * The wrench itself, drawn rather than loaded, as the crop mark is and for its reasons: a handle corner to corner with an open jaw at
     * each end - dark over a white underlay, so it reads on a photograph of any color.  A JAW AT EACH END (MT-591; Adam: "make the
     * wrench icon look like a wrench on both sides. it looks more like a wine glass right now"): each jaw opens away from the handle, and
     * the drawing is the same turned half round, about the middle of the square, which is why it is laid out in half-pixel coordinates.
     */
    static final class WrenchMark implements javax.swing.Icon
    {
        private static final int SIZE = 15;

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }

        @Override
        public void paintIcon(java.awt.Component host, java.awt.Graphics g, int x, int y)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // Drawn where the coordinates say, so the two ends come out alike
            g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL,
                java.awt.RenderingHints.VALUE_STROKE_PURE);

            for (int pass = 0; pass < 2; pass++)
            {
                g2.setColor(pass == 0 ? new java.awt.Color(255, 255, 255, 200)
                    : new java.awt.Color(51, 51, 51));
                g2.setStroke(new java.awt.BasicStroke(pass == 0 ? 3.4f : 1.8f,
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

                double middle = SIZE / 2.0;
                double reach = 4.0;
                double jaw = 2.6;

                // The handle, between the two jaws
                g2.draw(new java.awt.geom.Line2D.Double(x + middle - reach + jaw * 0.7, y + middle + reach - jaw * 0.7,
                    x + middle + reach - jaw * 0.7, y + middle - reach + jaw * 0.7));

                // The jaws: a ring at each end, open away from the handle - top right, and bottom left
                g2.draw(new java.awt.geom.Arc2D.Double(x + middle + reach - jaw, y + middle - reach - jaw, jaw * 2,
                    jaw * 2, 95, 260, java.awt.geom.Arc2D.OPEN));

                g2.draw(new java.awt.geom.Arc2D.Double(x + middle - reach - jaw, y + middle + reach - jaw, jaw * 2,
                    jaw * 2, 275, 260, java.awt.geom.Arc2D.OPEN));
            }

            g2.dispose();
        }
    }

    /**
     * The crop mark itself, drawn rather than loaded: two overlapping right angles, which is what every photo tool uses for this, in
     * eleven pixels of two lines (a PNG would want a size, a folder, a name, and a test that it is still there).  A white line is laid
     * under the dark one so it reads on a photograph of any color, as the running-train marker on the diagram does.
     */
    static final class CropMark implements javax.swing.Icon
    {
        private static final int SIZE = 15;

        private static final int INSET = 4;

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }

        @Override
        public void paintIcon(java.awt.Component host, java.awt.Graphics g, int x, int y)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            for (int pass = 0; pass < 2; pass++)
            {
                g2.setColor(pass == 0 ? new java.awt.Color(255, 255, 255, 200)
                    : new java.awt.Color(51, 51, 51));
                g2.setStroke(new java.awt.BasicStroke(pass == 0 ? 3.4f : 1.8f,
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

                int near = INSET;
                int far = SIZE - INSET;

                // The top-left angle, and the bottom-right one, each overshooting the other's corner -
                // which is what makes the pair read as crop marks rather than as a box.
                g2.drawLine(x + near, y + 1, x + near, y + far);
                g2.drawLine(x + 1, y + near, x + far, y + near);

                g2.drawLine(x + far, y + near, x + far, y + SIZE - 1);
                g2.drawLine(x + near, y + far, x + SIZE - 1, y + far);
            }

            g2.dispose();
        }
    }

    /**
     * `TrainControlUI.setLocIcon`'s body.
     */
    void setLocIcon(Locomotive l, MouseEvent evt)
    {
        Component source = evt != null ? (Component) evt.getSource() : this.ui;
        
        javax.swing.SwingUtilities.invokeLater(() -> 
        {
            String currentPath = null;

            if (l.getLocalImageURL() != null)
            {
                // Through a URI, because that is what the value IS (UI review, C4): `new File(String)` on a "file:/..." URI produces a path that never
                // exists, so the chooser never opened where the current icon lives.  And it must NOT open in tc_loc_icons, where a cropped icon lives:
                // that is the application's own folder, not somewhere the operator keeps photographs, so the chooser starts where the current icon is
                // only when that is somewhere else.
                File currentIcon = TrainControlUI.fileOf(l.getLocalImageURL());

                if (currentIcon != null && currentIcon.exists()
                    && !org.traincontrol.util.Util.isLocIconFile(l.getLocalImageURL()))
                {
                    currentPath = currentIcon.getParent();
                }

                this.ui.getModel().logf(
                    "loc.ui.infoCurrentIconForLocomotiveIsUrl",
                    l.getName(),
                    l.getLocalImageURL()
                );
            }
            
            JFileChooser fc = new QuickFileChooser(
                currentPath != null ? currentPath : TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_ICON_FOLDER, new File(".").getAbsolutePath())
            );

            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setFileFilter(QuickFileChooser.imageFilter());

            fc.setAcceptAllFileFilterUsed(false);

            // FR-022.  The offer lives in the chooser, beside the files, because that is where the
            // user is when the question "will the whole of this picture fit?" occurs to them.  A
            // menu item alongside "Set Local Locomotive Icon" would have been a second way to start
            // the same job, and two entrances to one thing is how a menu stops being readable.
            final JCheckBox crop = new JCheckBox(I18n.t("loc.ui.cropCheckbox"),
                TrainControlUI.getPrefs().getBoolean(TrainControlUI.CROP_LOC_ICON_PREF, false));

            crop.setToolTipText(I18n.t("loc.ui.tooltip.cropCheckbox"));

            // UNDER the file name, not in the accessory panel (Adam: "is there a way to put the checkbox in a more organic place?"): setAccessory
            // hangs a component off the right-hand side of the chooser, where a preview pane belongs, and reads as bolted on when it holds one tick
            // box; along the bottom is where a file dialog's options live everywhere else.  **SOUTH, and the constraint is the whole of it**: a
            // JFileChooser is laid out with a BorderLayout whose CENTER holds the entire browser, so `fc.add(row)` with no constraint REPLACED the
            // browser (found by rendering the chooser to a PNG).  NORTH, SOUTH, EAST and WEST are all free, so this displaces nothing.
            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
            row.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));
            row.add(crop);

            // Clear Icon (FR-142; Adam: "In the locomotive icon file chooser, we should have a button to also clear the current icon, so it's
            // accessible via the wrench."): the menu's own clear, then the chooser closes with nothing chosen.  Disabled, not hidden, when
            // there is no icon of this computer's to clear, so the row does not change shape from one locomotive to the next; the menu item
            // is absent in that case for the same reason it has nothing to do.
            final javax.swing.JButton clear = new javax.swing.JButton(I18n.t("loc.ui.menuClearLocalLocomotiveIcon"));

            clear.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            clear.setEnabled(l.getLocalImageURL() != null);
            clear.addActionListener(event ->
            {
                this.ui.clearLocIcon(l);
                fc.cancelSelection();
            });

            row.add(javax.swing.Box.createHorizontalStrut(16));
            row.add(clear);

            fc.add(row, java.awt.BorderLayout.SOUTH);

            int i = fc.showOpenDialog(source);

            if (i == JFileChooser.APPROVE_OPTION)
            {
                File f = fc.getSelectedFile();

                TrainControlUI.getPrefs().putBoolean(TrainControlUI.CROP_LOC_ICON_PREF, crop.isSelected());

                // The file the user picked, unless cropping produced a file of our own.  Assigned
                // before the crop is attempted so that every way the crop can fail - unreadable
                // picture, canceled dialog, unwritable folder - lands on exactly the behavior this
                // method had before FR-022 rather than on no icon at all.
                String chosenURL = Paths.get(f.getAbsolutePath()).toUri().toString();

                // Readable at all, whether or not it is going to be cropped: the guard must not live inside the branch below, or it only runs when Crop
                // was ticked; untick Crop, pick a CMYK JPEG or a `.png` that is really something else (both get past the extension filter), and the
                // locomotive is pointed at a file that renders as nothing and the crop it had is deleted.  Costs a decode of a picture about to be
                // decoded again, a one-off on a file the user just picked by hand.
                if (!pictureCanBeRead(f, source)) return;

                if (crop.isSelected())
                {
                    // Cancel is not a failure, and treating it as one was destructive: cropLocIcon returned null for both "the user pressed Cancel"
                    // and "the picture could not be read", and the fallback to the UNCROPPED file set the full photograph as the icon and deleted the
                    // crop that was there.  The dialog's own javadoc says null means "leave everything exactly as it was, not a failure".  Two answers,
                    // not one: was it canceled, and was the picture unreadable.
                    boolean[] cancelled = {false, false};

                    String cropped = cropLocIcon(source, l, f, cancelled, null);

                    // Nothing is assigned and nothing is deleted: the locomotive keeps the icon it
                    // had, which is what canceling a dialog means.
                    if (cancelled[0]) return;

                    // Nor when the picture could not be read at all: the fallback below (use the file the user picked, uncropped) is right when the
                    // picture is good and only the crop failed; here it would point the locomotive at a file that renders as nothing AND delete the crop
                    // it had.  The sibling `recropLocIcon` has always refused this case.
                    if (cancelled[1]) return;

                    if (cropped != null) chosenURL = cropped;
                }

                // The crop this icon is replacing, if the icon it is replacing was one of ours.  Read
                // BEFORE the assignment and deleted after it, so a failure in between cannot leave
                // the locomotive pointing at a file that has been removed.
                String superseded = l.getLocalImageURL();

                l.setLocalImageURL(chosenURL);
                TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_ICON_FOLDER, f.getParent());

                if (!chosenURL.equals(superseded) && Util.isLocIconFile(superseded))
                {
                    deleteLocIconFile(superseded);
                }

                this.ui.repaintLoc(true, null);
                this.ui.repaintMappings(Collections.singletonList(l), true);
                this.ui.getLocSelector().refreshLocSelectorList();

            }
        });
    }

    /**
     * Whether a picture the user picked can actually be displayed, complaining if it cannot.  The file chooser filters on the NAME: a
     * `.png` that is really something else, a CMYK JPEG, a HEIC renamed, a cloud-backed file not on this machine all get past it, and each
     * ends as a locomotive pointing at a file that renders as nothing.  Asked before anything is assigned or deleted, because the deletion
     * cannot be undone: the crop being replaced is a file this application made.
     *
     * @param chosen the picture the user picked, opened for reading and nothing else
     * @param parent the component to complain on
     * @return whether it read as an image
     */
    private boolean pictureCanBeRead(File chosen, Component parent)
    {
        try
        {
            // ImageIO answers null rather than throwing for a file no reader claims, so both.
            if (ImageIO.read(chosen) != null) return true;
        }
        catch (IOException | RuntimeException failed)
        {
            this.ui.getModel().log(failed);
        }

        this.ui.getModel().logf("ui.errorFailedToLoadImage", chosen.getAbsolutePath());

        JOptionPane.showMessageDialog(parent,
            I18n.f("loc.ui.errorPictureCouldNotBeRead", chosen.getName()));

        return false;
    }

    /**
     * The array the crop dialog opens on and writes what the user settled on back into: SIX NUMBERS, the last the crop's
     * width in the photograph's own pixels (OB-257), whatever the note it opens on held - an older note's five are copied
     * in, and the sixth is written after the crop either way.
     *
     * @param opening the view the note held, or null
     * @return the array, NaN where nothing was held
     */
    private static double[] viewToOpenOn(double[] opening)
    {
        double[] view = { Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN };

        if (opening != null) System.arraycopy(opening, 0, view, 0, Math.min(opening.length, view.length));

        return view;
    }

    /**
     * FR-022 - runs the crop dialog over a picture the user has just picked, and stores what comes back as a locomotive icon of our own.
     *
     * The user's file is opened for READING and nothing else: the user picks these out of their own photo folders, and a program that
     * writes into them has done something they did not ask for and cannot undo.  The crop is a new picture and goes in the application's
     * own folder, as PNG regardless of the source, since JPEG would flatten the transparency of a crop of a Central Station picture to
     * black.
     *
     * @param parent the component the dialog should center on
     * @param l the locomotive the icon is for; its name is used to make the file recognizable
     * @param chosen the picture the user picked, never written to
     * @param cancelled set to true when the user dismissed the crop dialog, which is NOT a failure: the caller must then change nothing at
     *                  all (collapsing the two set the uncropped photograph as the icon, deleted the crop it replaced)
     * @return the URL of the cropped icon, or null if there is nothing to use - the user canceled, the picture could not be read, or the
     *         folder could not be written - in which case the caller must fall back to the uncropped original
     */
    private String cropLocIcon(Component parent, Locomotive l, File chosen, boolean[] cancelled,
        double[] opening)
    {
        // One array, in and out: what to open on, and afterwards what the user settled on (OB-125).
        // Allocated here when the caller has nothing to open on, because the note still wants
        // recording either way.
        double[] view = viewToOpenOn(opening);

        BufferedImage picture;

        try
        {
            picture = ImageIO.read(chosen);
        }
        catch (IOException | RuntimeException e)
        {
            picture = null;
        }

        if (picture == null)
        {
            // ImageIO returns null rather than throwing for a file no reader claims - a .png that is
            // really something else, which is common enough among pictures collected off the web.
            this.ui.getModel().logf("ui.errorFailedToLoadImage", chosen.getAbsolutePath());

            // And said out loud, because the caller must not fall back to this file: falling back is right when the picture is good and only the
            // crop failed, and the worst of both when it cannot be read (the locomotive is pointed at a file that renders as nothing and its crop
            // is deleted).
            if (cancelled != null) cancelled[1] = true;

            JOptionPane.showMessageDialog(parent,
                I18n.f("loc.ui.errorPictureCouldNotBeRead", chosen.getName()));

            return null;
        }

        BufferedImage cropped = LocIconCropDialog.crop(parent, picture, TrainControlUI.LOC_ICON_WIDTH,
            TrainControlUI.LOC_ICON_HEIGHT, view);

        // The dialog returns null only when the user said no - Cancel, Escape, or the close box. Its
        // own javadoc calls that "leave everything exactly as it was, not a failure", and the caller
        // needs to be able to tell it apart from the failures above, which DO fall back to the file
        // the user picked.
        if (cropped == null)
        {
            if (cancelled != null) cancelled[0] = true;

            return null;
        }

        // A fresh name every time, never the same file overwritten.  getLocImage caches by URL, and
        // the cache has no way to know a file behind a URL it has already read has changed - reusing
        // the name would show the OLD crop until the application was restarted, which reads as the
        // crop dialog having done nothing.  The superseded file is deleted by the caller, so this
        // accumulates one file per locomotive rather than one per attempt.
        String name = Util.sanitizeFilename(l.getName()) + "_" + System.currentTimeMillis() + ".png";

        File target = Util.getLocIconFile(name);

        if (target == null)
        {
            this.ui.getModel().logf("loc.ui.errorCropCouldNotBeSaved", Util.LOC_ICON_FOLDER);

            return null;
        }

        try
        {
            // The RETURN VALUE, which ImageIO answers false with rather than throwing when no writer
            // claims the format. Thrown away here, a zero-byte file was moved into place, reported as
            // a success, and the crop it replaced deleted on the strength of it. DiagramExport checks
            // the same call.
            Util.writeAtomically(target, out ->
            {
                if (!ImageIO.write(cropped, "png", out))
                {
                    throw new IOException("no writer claimed PNG for " + target.getAbsolutePath());
                }
            });
        }
        catch (IOException | RuntimeException e)
        {
            this.ui.getModel().logf("loc.ui.errorCropCouldNotBeSaved", target.getAbsolutePath());
            this.ui.getModel().log(e);

            return null;
        }

        // Not when the picture cropped was itself one of our crops (validator): a crop with no note (anything made before FR-032) re-crops
        // from the crop itself, and this line would write a note naming that crop, which the caller deletes three lines later; a wrong note is
        // worse than none, since no note degrades to "crop the crop", which is correct.
        if (!Util.isLocIconFile(chosen.toPath().toUri().toString()))
        {
            rememberCropSource(target, chosen, view);
        }

        return target.toPath().toUri().toString();
    }

    /**
     * `TrainControlUI.rememberCropSource`'s body.
     */
    void rememberCropSource(File crop, File source)
    {
        rememberCropSource(crop, source, null);
    }

    /**
     * `TrainControlUI.rememberCropSource`'s body.
     */
    void rememberCropSource(File crop, File source, double[] view)
    {
        final String body = source.getAbsolutePath()
            + (LocIconCropDialog.CropPanel.viewIsUsable(view)
                ? "\n" + TrainControlUI.CROP_VIEW_PREFIX + view[0] + "," + view[1] + "," + view[2] + ","
                    + view[3] + "," + view[4]
                    + (view.length >= 6 && view[5] > 0 && !Double.isInfinite(view[5]) ? "," + view[5] : "")
                : "");

        try
        {
            Util.writeAtomically(new File(crop.getAbsolutePath() + TrainControlUI.CROP_SOURCE_SUFFIX),
                out -> out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (IOException | RuntimeException e)
        {
            // Untidy, not broken.  Re-cropping will use the crop itself.
        }
    }

    /**
     * `TrainControlUI.cropViewOf`'s body.
     */
    double[] cropViewOf(String url)
    {
        String line = cropNoteLine(url, TrainControlUI.CROP_VIEW_PREFIX);

        if (line == null) return null;

        String[] parts = line.split(",");

        // FIVE, as notes were written before OB-257, or SIX with the crop's width in the photograph
        if (parts.length != 5 && parts.length != 6) return null;

        double[] view = new double[parts.length];

        try
        {
            for (int i = 0; i < parts.length; i++) view[i] = Double.parseDouble(parts[i].trim());
        }
        catch (NumberFormatException notANumber)
        {
            return null;
        }

        return LocIconCropDialog.CropPanel.viewIsUsable(view) ? view : null;
    }

    /**
     * A line of a crop's note that begins with a given prefix, with the prefix taken off.
     *
     * @param url the icon currently set on the locomotive
     * @param prefix what the line starts with
     * @return the rest of that line, or null when there is no note or no such line
     */
    private String cropNoteLine(String url, String prefix)
    {
        if (!Util.isLocIconFile(url)) return null;

        File crop = TrainControlUI.fileOf(url);

        if (crop == null) return null;

        File note = new File(crop.getAbsolutePath() + TrainControlUI.CROP_SOURCE_SUFFIX);

        if (!note.exists()) return null;

        try
        {
            for (String line : new String(java.nio.file.Files.readAllBytes(note.toPath()),
                java.nio.charset.StandardCharsets.UTF_8).split("\n"))
            {
                if (line.trim().startsWith(prefix)) return line.trim().substring(prefix.length());
            }
        }
        catch (IOException | RuntimeException unreadable)
        {
            return null;
        }

        return null;
    }

    /**
     * `TrainControlUI.cropSourceOf`'s body.
     */
    File cropSourceOf(String url)
    {
        File note = cropSourceNoteOf(url);

        // The photograph may have been moved, renamed or deleted since - it is the user's file in the
        // user's folder, and nothing here has any claim on it.
        return note != null && note.isFile() ? note : null;
    }

    /**
     * What a crop's note SAYS, whether or not the file it names is there right now.  The distinction is a data-loss defect a reviewer
     * found (C5): `cropSourceOf` answered null for both "there is no note" and "the note names a photograph I cannot see at the moment",
     * and re-crop treated the two the same, wrote a NEW note naming the crop and deleted the old crop and note, so one re-crop while an
     * external drive was unplugged or a OneDrive folder had not come online threw away the only record of where the photograph was.
     * Carrying the old note forward loses nothing: at worst it names a file that never comes back, which is the state the crop was in.
     *
     * @param url the icon currently set on the locomotive
     * @return the file the note names, existing or not, or null when there is no note to read
     */
    private File cropSourceNoteOf(String url)
    {
        if (!Util.isLocIconFile(url)) return null;

        File crop = TrainControlUI.fileOf(url);

        if (crop == null) return null;

        File note = new File(crop.getAbsolutePath() + TrainControlUI.CROP_SOURCE_SUFFIX);

        if (!note.exists()) return null;

        try
        {
            // THE FIRST LINE. A note may now carry a view on a second one (OB-125), and trimming the
            // whole file and calling it a path would hand back both lines as a filename.
            String body = new String(java.nio.file.Files.readAllBytes(note.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            String path = body.split("\n")[0].trim();

            return path.isEmpty() ? null : new File(path);
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    /**
     * `TrainControlUI.recropLocIcon`'s body.
     */
    void recropLocIcon(Locomotive l, java.awt.Component parent)
    {
        // THE CENTRAL STATION'S PICTURE, where there is none of this computer's (FR-104)
        if (l != null && l.getLocalImageURL() == null && TrainControlUI.offersACrop(l))
        {
            cropTheCentralStationPicture(l, parent);

            return;
        }

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            if (l == null || l.getLocalImageURL() == null) return;

            File source = cropSourceOf(l.getLocalImageURL());

            boolean fromOriginal = source != null;

            // What the note SAID, kept even when the file it names is not there today (C5).  Cropping
            // the crop must not be allowed to forget where the photograph was: the drive comes back,
            // and the icon should still be pannable when it does.
            File remembered = fromOriginal ? null : cropSourceNoteOf(l.getLocalImageURL());

            if (source == null) source = TrainControlUI.fileOf(l.getLocalImageURL());

            if (source == null || !source.isFile())
            {
                this.ui.getModel().logf("ui.errorFailedToLoadImage", String.valueOf(l.getLocalImageURL()));

                return;
            }

            // Two slots, the same as the other call site: this passed one to a method that writes slot 1, saved only by its `cancelled.length > 1`
            // guard, which silences the mismatch and leaves the next reader of `cancelled[1]` here out of bounds.
            boolean[] cancelled = {false, false};

            // ONLY when working from the photograph the view was measured over (OB-125).
            //
            // The fallback crops the crop itself, where these coordinates describe somewhere else
            // entirely - the picture underneath them is a different picture.
            String cropped = cropLocIcon(parent, l, source, cancelled,
                fromOriginal ? cropViewOf(l.getLocalImageURL()) : null);

            if (cancelled[0] || cropped == null) return;

            String superseded = l.getLocalImageURL();

            l.setLocalImageURL(cropped);

            // The old note wins over the one just written, which names the crop this was cut from rather than the photograph behind it (C5).  No
            // view goes with it: this branch cropped the CROP, so the view describes a point in that crop while the note names the photograph, and
            // storing one against the other would open the next re-crop of the original at coordinates measured on something else (OB-125).
            if (remembered != null)
            {
                File fresh = TrainControlUI.fileOf(cropped);

                if (fresh != null) rememberCropSource(fresh, remembered);
            }

            if (!cropped.equals(superseded) && Util.isLocIconFile(superseded))
            {
                deleteLocIconFile(superseded);
            }

            // Told, because the difference matters and is invisible: re-cropping a crop cannot pan back out to anything the previous crop threw
            // away.  The fallback message does not claim to know WHY there was no original: the note named a file that is not there today, or
            // there was never a note (every crop made before FR-032).
            this.ui.getModel().logf(fromOriginal ? "loc.ui.infoRecroppedFromOriginal"
                : "loc.ui.infoRecroppedFromCrop", l.getName());

            this.ui.repaintLoc(true, null);
            this.ui.repaintMappings(Collections.singletonList(l), true);
            this.ui.getLocSelector().refreshLocSelectorList();
        });
    }

    /**
     * Crops a locomotive's Central Station picture into an icon of this computer's (FR-104; Adam: "also, make central station supplied
     * icons croppable locally.").  The picture is fetched off the event thread (it is on the station, across the network) and kept, named
     * for the locomotive, in a folder of its own inside the icon folder; the crop's note names it, so a second crop pans over the whole
     * picture again rather than cropping the crop.  Outside the crops proper, which `Util.isLocIconFile` vouches for and replacing a crop
     * deletes: the kept picture outlives every crop cut from it, one per locomotive, replaced when a crop next starts from the station's
     * picture.
     *
     * @param l the locomotive
     * @param parent where the crop window centers
     */
    private void cropTheCentralStationPicture(Locomotive l, java.awt.Component parent)
    {
        final String url = l.getImageURL();

        Thread fetching = new Thread(() ->
        {
            final File kept = keepTheCentralStationPicture(l, url);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                if (kept == null)
                {
                    this.ui.getModel().logf("ui.errorFailedToLoadImage", String.valueOf(url));

                    return;
                }

                // STILL THE STATION'S: an icon of this computer's set while the picture was fetched is not replaced
                if (l.getLocalImageURL() != null) return;

                // Two slots, as the other call sites
                boolean[] cancelled = {false, false};

                String cropped = cropLocIcon(parent, l, kept, cancelled, null);

                if (cancelled[0] || cropped == null) return;

                l.setLocalImageURL(cropped);

                this.ui.getModel().logf("loc.ui.infoCroppedFromCentralStation", l.getName());

                this.ui.repaintLoc(true, null);
                this.ui.repaintMappings(Collections.singletonList(l), true);
                this.ui.getLocSelector().refreshLocSelectorList();
            });
        }, "Central Station picture of " + l.getName());

        fetching.setDaemon(true);

        fetching.start();
    }

    /**
     * The Central Station's picture of a locomotive, written into the icon folder's own folder for them (FR-104) - or
     * null where it could not be read or written, which is logged.
     *
     * @param l the locomotive, whose name the file is given
     * @param url where the station serves the picture
     * @return the file
     */
    private File keepTheCentralStationPicture(Locomotive l, String url)
    {
        try
        {
            BufferedImage picture = ImageIO.read(new URL(url));

            if (picture == null) return null;

            File folder = new File(Util.LOC_ICON_FOLDER, TrainControlUI.CENTRAL_STATION_PICTURES);

            if (!folder.isDirectory() && !folder.mkdirs()) return null;

            File target = new File(folder, Util.sanitizeFilename(l.getName()) + ".png");

            // PNG, and the return value asked, for the reasons `cropLocIcon` gives
            Util.writeAtomically(target, out ->
            {
                if (!ImageIO.write(picture, "png", out))
                {
                    throw new IOException("no writer claimed PNG for " + target.getAbsolutePath());
                }
            });

            return target;
        }
        catch (IOException | RuntimeException e)
        {
            this.ui.getModel().log(e);

            return null;
        }
    }

    /**
     * Removes a cropped icon file that nothing points at any more.
     *
     * Only ever called for a URL {@link Util#isLocIconFile} has already vouched for, so this cannot
     * reach the user's own pictures.  Failure is ignored on purpose: a stale file in our own folder
     * is untidy, and telling the user about it in the middle of setting an icon that worked would be
     * worse than the untidiness.
     *
     * @param url the URL of the file to remove
     */
    void deleteLocIconFile(String url)
    {
        try
        {
            File file = new File(new java.net.URI(url));

            // The note about where it came from goes with it (FR-032).  Left behind it would be a file
            // in our own folder that nothing points at and nothing would ever remove.
            new File(file.getAbsolutePath() + TrainControlUI.CROP_SOURCE_SUFFIX).delete();

            file.delete();
        }
        catch (URISyntaxException | RuntimeException e)
        {
            // Nothing to do and nothing worth saying.  RuntimeException covers the
            // IllegalArgumentException a URL that is not a file URL produces.
        }
    }
}
