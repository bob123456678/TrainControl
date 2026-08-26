package ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.LocIconCropDialog;

/**
 * The part of the locomotive icon crop that is arithmetic rather than a drag (FR-022).
 *
 * Adam: "add a crop / pan function to local locomotive icons at the time of image selection."
 *
 * The feature is a picture moving under a fixed window, and most of it is only meaningful with a mouse
 * on it. Two things underneath are not, and both would be silently wrong rather than visibly wrong:
 * the window must always be fully covered by the picture, and the file written must be the size the
 * icon is actually drawn at. A crop that let the window slide off the edge would produce an icon with
 * a band of nothing along one side, and nobody would look at the code to find out why.
 *
 * `CropPanel` is public and takes a `BufferedImage`, so all of this runs with no dialog, no file and
 * no display.
 *
 * @author Adam
 */
public class testLocIconCrop
{
    /** What the locomotive icon is drawn at - LOC_ICON_WIDTH and LOC_ICON_HEIGHT. */
    private static final int OUT_W = 296;
    private static final int OUT_H = 114;

    /**
     * A panel over a test picture, sized as though it had been laid out.
     *
     * @param sourceWidth the picture's width
     * @param sourceHeight the picture's height
     * @return a panel ready to be asked questions
     */
    private LocIconCropDialog.CropPanel panel(int sourceWidth, int sourceHeight)
    {
        BufferedImage source = new BufferedImage(sourceWidth, sourceHeight,
            BufferedImage.TYPE_INT_ARGB);

        LocIconCropDialog.CropPanel panel =
            new LocIconCropDialog.CropPanel(source, OUT_W, OUT_H);

        panel.setSize(800, 500);

        return panel;
    }

    /**
     * The crop written out is the size the icon is displayed at, whatever went in.
     *
     * The whole reason to crop rather than let the icon be squashed. A source of any shape - a tall
     * phone photograph is the case Adam would actually hit - has to come out at the one size the
     * window draws.
     */
    @Test
    public void testTheCropComesOutAtTheIconSize()
    {
        for (int[] size : new int[][] {{4000, 3000}, {600, 2000}, {90, 40}, {296, 114}})
        {
            BufferedImage out = panel(size[0], size[1]).getCroppedImage();

            assertEquals(out.getWidth(), OUT_W,
                "a " + size[0] + "x" + size[1] + " picture cropped to the wrong width");

            assertEquals(out.getHeight(), OUT_H,
                "a " + size[0] + "x" + size[1] + " picture cropped to the wrong height");
        }
    }

    /**
     * The picture always covers the crop window, however far it is dragged.
     *
     * The assertion this class exists for. A pan is a drag, so it can be as large as the user's arm,
     * and nothing about a drag says when to stop - which means the clamp is the only thing between a
     * hard flick and an icon with a strip of nothing down one side. The failure is invisible until the
     * file is written.
     *
     * Checked at both ends of the zoom range and in both axes, because the clamp has a different
     * amount of slack to work with at each.
     *
     * **This test used to say the opposite,** and the change is Adam's: "add some white (not black)
     * padding around the sides so that users can spill over onto it." The picture covering the frame
     * at all times was the old rule, and it is what stopped anybody framing a locomotive against
     * white. What is left of it is the part that is still true - some of the photograph has to stay
     * under the frame, or the dialog shows a blank rectangle and no way back.
     *
     * MUTATION: removing the `clampCenter()` call from `panBy` fails this test.
     */
    @Test
    public void testThePictureCannotBeDraggedOffTheWindowENTIRELY()
    {
        for (double zoom : new double[] {0.0, 0.5, 1.0})
        {
            for (int[] shove : new int[][] {{100000, 0}, {-100000, 0}, {0, 100000}, {0, -100000},
                {100000, 100000}, {-100000, -100000}})
            {
                LocIconCropDialog.CropPanel panel = panel(1200, 900);

                panel.setZoomFraction(zoom);
                panel.panBy(shove[0], shove[1]);

                assertTrue(stillTouchesItsWindow(panel),
                    "at zoom " + zoom + ", a drag of " + shove[0] + "," + shove[1]
                    + " pushed the picture completely out of the crop window. The dialog then shows "
                    + "a blank white rectangle with nothing on screen saying which way the "
                    + "photograph went, and Reset is the only way back");
            }
        }
    }

    /**
     * Zooming right out uncovers the frame on purpose, and what shows through is white.
     *
     * The inverse of what this test used to assert. Zooming out below the covering scale was refused
     * outright - the scale had a floor at "the picture exactly fills the frame" - and that floor is
     * what Adam removed: "add some white (not black) padding around the sides so that users can spill
     * over onto it."
     *
     * So the interesting property is no longer "this cannot happen". It is that when it does happen
     * the result is the white the dialog was showing under the frame, at the icon's exact size -
     * rather than a smaller rectangle stretched to fit, which is the failure `sourceRect`'s javadoc
     * describes and which clamping the cut back inside the picture would have produced silently.
     *
     * MUTATION: putting the clamp back into `sourceRect` - `x = max(0, ...)` and the rest - fails
     * this test, because the cut then comes back inside the picture and there is no white.
     */
    @Test
    public void testZoomingRightOutFillsTheRestWithWhite()
    {
        BufferedImage source = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D paint = source.createGraphics();
        paint.setColor(new java.awt.Color(20, 90, 200));
        paint.fillRect(0, 0, 1200, 900);
        paint.dispose();

        LocIconCropDialog.CropPanel panel = new LocIconCropDialog.CropPanel(source, OUT_W, OUT_H);
        panel.setSize(800, 500);

        // All the way out, so the whole picture is visible and the frame - which is as wide as the
        // panel allows - hangs off it left and right.
        panel.setZoomFraction(0.0);

        Rectangle cut = panel.sourceRect();

        assertTrue(cut.x < 0 || cut.x + cut.width > source.getWidth(),
            "the fixture did not take: at full zoom-out the frame has to reach past the picture, or "
            + "there is no white in the result and this test is checking nothing. Cut was " + cut);

        BufferedImage out = panel.getCroppedImage();

        assertEquals(out.getWidth(), OUT_W, "the icon is not the right width");
        assertEquals(out.getHeight(), OUT_H, "the icon is not the right height");

        java.awt.Color edge = new java.awt.Color(out.getRGB(1, OUT_H / 2), true);

        assertEquals(edge.getAlpha(), 255, "the spill is transparent rather than white");
        assertEquals(edge.getRed(), 255, "the spill is not white");
        assertEquals(edge.getGreen(), 255, "the spill is not white");
        assertEquals(edge.getBlue(), 255, "the spill is not white");

        java.awt.Color middle = new java.awt.Color(out.getRGB(OUT_W / 2, OUT_H / 2), true);

        assertEquals(middle.getBlue(), 200,
            "the middle is not the photograph, so the assertion above is describing a blank image");
    }

    /**
     * At full zoom-out the picture sits inside the frame with white all round it.
     *
     * Adam: "now we can't zoom out beyond the image width to add white space.  Add a 0.5x zoom
     * allowance."
     *
     * Zooming out until the whole picture fits was not far enough to be useful. The frame is as large
     * as the panel allows, so at that point the two are about the same size - the picture touches the
     * frame on its tight axis and there is nowhere to put white without dragging the photograph off to
     * one side, which is a different gesture and loses the middle.
     *
     * The floor is half of fitting now, so the photograph shrinks well inside the frame and every side
     * has white on it. That is the property here: not "there is some white somewhere", which panning
     * already gave, but white on ALL FOUR sides at once, which only zooming out can produce.
     *
     * MUTATION: putting MIN_ZOOM back to 1.0 - zooming out no further than the picture fitting - fails
     * this test.
     */
    @Test
    public void testZoomingOutLeavesWhiteOnEverySide()
    {
        BufferedImage source = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D paint = source.createGraphics();
        paint.setColor(new java.awt.Color(20, 90, 200));
        paint.fillRect(0, 0, 1200, 900);
        paint.dispose();

        LocIconCropDialog.CropPanel panel = new LocIconCropDialog.CropPanel(source, OUT_W, OUT_H);
        panel.setSize(800, 500);

        panel.setZoomFraction(0.0);

        Rectangle cut = panel.sourceRect();

        // The cut reaching past the picture on every side is the same statement as the picture
        // sitting inside the frame with a margin all round, said in source coordinates.
        assertTrue(cut.x < 0, "the frame does not reach past the LEFT of the picture: " + cut);
        assertTrue(cut.y < 0, "the frame does not reach past the TOP of the picture: " + cut);

        assertTrue(cut.x + cut.width > source.getWidth(),
            "the frame does not reach past the RIGHT of the picture: " + cut);

        assertTrue(cut.y + cut.height > source.getHeight(),
            "the frame does not reach past the BOTTOM of the picture: " + cut);

        BufferedImage out = panel.getCroppedImage();

        assertEquals(out.getWidth(), OUT_W, "the icon is not the right width");
        assertEquals(out.getHeight(), OUT_H, "the icon is not the right height");

        // All four corners, because a margin on one side only is what panning gives and is not what
        // was asked for.
        int[][] corners = {{1, 1}, {OUT_W - 2, 1}, {1, OUT_H - 2}, {OUT_W - 2, OUT_H - 2}};

        for (int[] at : corners)
        {
            java.awt.Color c = new java.awt.Color(out.getRGB(at[0], at[1]), true);

            assertEquals(c.getAlpha(), 255,
                "corner " + at[0] + "," + at[1] + " is transparent rather than white");

            assertTrue(c.getRed() == 255 && c.getGreen() == 255 && c.getBlue() == 255,
                "corner " + at[0] + "," + at[1] + " is not white: " + c);
        }

        // And the middle is still the photograph, or every assertion above describes a blank icon.
        java.awt.Color middle = new java.awt.Color(out.getRGB(OUT_W / 2, OUT_H / 2), true);

        assertEquals(middle.getBlue(), 200,
            "the middle of the icon is not the picture, so this test is checking a blank image");
    }

    /**
     * Reset returns the view to something legal, from wherever it had got to.
     */
    @Test
    public void testResetGoesBackToACentreCrop()
    {
        LocIconCropDialog.CropPanel panel = panel(1200, 900);

        panel.setZoomFraction(0.8);
        panel.panBy(9000, -9000);
        panel.resetView();

        assertTrue(coversItsWindow(panel),
            "reset did not go back to the view the dialog opens at - the picture covering the frame. "
            + "Zoom 0 is now 'the whole photograph visible', which would leave white down two sides "
            + "of an icon nobody asked to change");
    }

    /**
     * A reshaped frame still writes an icon-sized picture, padded with white.
     *
     * Adam: "lets make the aspect of the image editable. if the uses adjusts the aspect of the frame,
     * fill the rest of the displayed icon with a white background."
     *
     * Two things have to hold at once and only one of them is obvious. The file must still be exactly
     * the size the icon is drawn at, or every consumer of it is wrong - that is the property the test
     * above this one guards, and reshaping the frame is a new way to break it. And what the picture
     * does not cover has to be WHITE, not transparent: the icon is drawn onto a coloured button, so
     * transparent padding shows the button through and reads as the crop having failed.
     *
     * The corners are the honest place to look. A frame made taller than the icon leaves white down
     * the left and right, so the middle of the left edge is white while the centre is not - and
     * checking a corner alone would pass on a picture that was simply blank.
     *
     * MUTATION: filling the padded image with `new Color(0, 0, 0, 0)` instead of white - which is
     * what leaving it unfilled amounts to - fails this test.
     */
    @Test
    public void testAReshapedFrameIsPaddedWithWhite()
    {
        // A source with no transparency anywhere, so anything transparent or white in the result was
        // added by the padding rather than carried in from the picture.
        BufferedImage source = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D paint = source.createGraphics();
        paint.setColor(new java.awt.Color(20, 90, 200));
        paint.fillRect(0, 0, 1200, 900);
        paint.dispose();

        LocIconCropDialog.CropPanel panel = new LocIconCropDialog.CropPanel(source, OUT_W, OUT_H);
        panel.setSize(800, 500);

        assertTrue(panel.isIconShaped(), "the frame must start at the icon's own shape");

        // Much TALLER than the icon, so the padding lands on the left and right.
        panel.setFrameAspect(0.5);

        assertFalse(panel.isIconShaped(), "the fixture did not take: the frame is still icon-shaped");

        BufferedImage out = panel.getCroppedImage();

        assertEquals(out.getWidth(), OUT_W, "a reshaped frame changed the icon's width");
        assertEquals(out.getHeight(), OUT_H, "a reshaped frame changed the icon's height");

        // The middle of the left edge: outside the fitted picture, inside the icon.
        java.awt.Color leftEdge = new java.awt.Color(out.getRGB(1, OUT_H / 2), true);

        assertEquals(leftEdge.getAlpha(), 255,
            "the padding is transparent. The icon is drawn onto a coloured button, so this shows the "
            + "button through and reads as the crop having gone wrong");

        assertEquals(leftEdge.getRed(), 255, "the padding is not white");
        assertEquals(leftEdge.getGreen(), 255, "the padding is not white");
        assertEquals(leftEdge.getBlue(), 255, "the padding is not white");

        // And the middle still holds the picture, or the assertions above are describing a blank.
        java.awt.Color middle = new java.awt.Color(out.getRGB(OUT_W / 2, OUT_H / 2), true);

        assertEquals(middle.getBlue(), 200,
            "the middle of the icon is not the picture, so this test is checking a blank image");
    }

    /**
     * At the icon's own shape nothing is added at all.
     *
     * The control for the test above, and the one that matters for everybody who never touches the
     * frame: this dialog wrote a picture that filled the icon exactly and kept its transparency, and
     * it still has to. The Central Station's own locomotive pictures are transparent, and padding
     * them onto white would put a white box behind every one of them.
     *
     * MUTATION: removing the `isIconShaped()` early return from `getCroppedImage` - so everything
     * goes through the padding - fails this test.
     */
    @Test
    public void testAnUnchangedFrameAddsNothing()
    {
        LocIconCropDialog.CropPanel panel = panel(1200, 900);

        assertTrue(panel.isIconShaped(), "the frame must start at the icon's own shape");

        BufferedImage out = panel.getCroppedImage();

        assertEquals(out.getWidth(), OUT_W, "the icon changed width");
        assertEquals(out.getHeight(), OUT_H, "the icon changed height");

        // The source in `panel` is a blank ARGB image - every pixel transparent - so anything opaque
        // here was invented by the padding.
        java.awt.Color corner = new java.awt.Color(out.getRGB(0, 0), true);

        assertEquals(corner.getAlpha(), 0,
            "a frame nobody reshaped came back with an opaque corner, so the padding ran for a crop "
            + "that fills the icon exactly. Every transparent icon would gain a white box");
    }

    /**
     * Whether the picture, as currently placed, covers the whole crop window.
     *
     * Asked of `sourceRect`, which is the rectangle actually cut. That is the honest place to ask it,
     * because a view that has slid off the edge does not throw and does not leave a blank - the cut is
     * clamped into the picture and comes back SMALLER, and a smaller rectangle stretched to 296x114 is
     * a distorted locomotive. So the test for "the window is covered" is really the test for "the
     * rectangle cut still has the icon's shape", and that is a number rather than a look.
     *
     * @param panel the panel to check
     * @return true when the cut is inside the picture and still the icon's proportions
     */
    private boolean stillTouchesItsWindow(LocIconCropDialog.CropPanel panel)
    {
        Rectangle cut = panel.sourceRect();

        java.awt.image.BufferedImage source = panel.source();

        // The cut may hang off the picture in any direction now. What must not happen is that it
        // misses it entirely, which is what an intersection of zero area means.
        return cut.intersects(new Rectangle(0, 0, source.getWidth(), source.getHeight()));
    }

    private boolean coversItsWindow(LocIconCropDialog.CropPanel panel)
    {
        Rectangle cut = panel.sourceRect();

        java.awt.image.BufferedImage source = panel.source();

        if (cut.x < 0 || cut.y < 0) return false;

        if (cut.x + cut.width > source.getWidth()) return false;

        if (cut.y + cut.height > source.getHeight()) return false;

        // Within a percent.  The cut is four rounded integers, so it is never exactly 296:114, and a
        // clamp that has genuinely eaten into it is out by far more than rounding - a hard drag takes
        // one side to a fraction of its proper length.
        double wanted = OUT_W / (double) OUT_H;
        double got = cut.width / (double) cut.height;

        if (Math.abs(got - wanted) / wanted >= 0.01) return false;

        // And the clamp inside sourceRect never had to do anything.
        //
        // The ratio check above is not enough on its own, and finding that out is the reason this
        // paragraph exists: sourceRect clamps a bad view into the picture, and the clamp can eat the
        // same proportion off both sides, leaving the ratio intact and the crop somewhere the user
        // never put it.  Comparing against what the scale IMPLIES the cut should be catches that,
        // because a cut that had to be trimmed is smaller than its own scale says.
        int wantWidth = (int) Math.round(panel.cropWindow().width / panel.getScale());
        int wantHeight = (int) Math.round(panel.cropWindow().height / panel.getScale());

        return Math.abs(cut.width - wantWidth) <= 1 && Math.abs(cut.height - wantHeight) <= 1;
    }
}
