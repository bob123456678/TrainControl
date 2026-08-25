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
     * amount of slack to work with at each: zoomed right out there is none at all in one direction,
     * which is the case an off-by-one in the clamp survives.
     *
     * MUTATION: removing the `clampCenter()` call from `panBy` fails this test.
     */
    @Test
    public void testThePictureCannotBeDraggedOffTheWindow()
    {
        for (double zoom : new double[] {0.0, 0.5, 1.0})
        {
            for (int[] shove : new int[][] {{100000, 0}, {-100000, 0}, {0, 100000}, {0, -100000},
                {100000, 100000}, {-100000, -100000}})
            {
                LocIconCropDialog.CropPanel panel = panel(1200, 900);

                panel.setZoomFraction(zoom);
                panel.panBy(shove[0], shove[1]);

                assertTrue(coversItsWindow(panel),
                    "at zoom " + zoom + ", a drag of " + shove[0] + "," + shove[1]
                    + " moved the picture off the crop window - the icon would be written with a "
                    + "band of nothing along one side");
            }
        }
    }

    /**
     * Zooming cannot push the window off the picture either.
     *
     * The other way to reach the same broken state, and it is reached differently: a pan moves the
     * view, a zoom changes how much of the picture the window covers, so a view that was legal at 4x
     * can be illegal at 1x. Clamping after a pan and not after a zoom would pass the test above.
     *
     * MUTATION: removing the `clampCenter()` call from `setZoomFraction` fails this test.
     */
    @Test
    public void testZoomingBackOutCannotUncoverTheWindow()
    {
        // Both corners.  Which one it is matters: sourceRect clamps a left or top overhang by moving
        // the cut back to zero and keeping its size, and a right or bottom overhang by keeping the
        // position and shortening it - so only one of the two can be caught at all.
        for (int corner : new int[] {-100000, 100000})
        {
            LocIconCropDialog.CropPanel panel = panel(1200, 900);

            // Right in, then as far into the corner as it will go, then all the way back out.
            panel.setZoomFraction(1.0);
            panel.panBy(corner, corner);
            panel.setZoomFraction(0.0);

            assertTrue(coversItsWindow(panel),
                "zooming back out from the " + (corner < 0 ? "top left" : "bottom right")
                + " corner left the crop window off the edge of the picture");
        }
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

        assertEquals(panel.getZoomFraction(), 0.0, 1e-9, "reset did not zoom back out");

        assertTrue(coversItsWindow(panel), "reset left the picture off the crop window");
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
