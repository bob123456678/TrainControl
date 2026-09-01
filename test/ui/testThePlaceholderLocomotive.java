package ui;

import java.awt.image.BufferedImage;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.LocomotivePlaceholder;

/**
 * The stand-in picture for a locomotive with no icon is the one Adam described (FR-054).
 *
 * "for locomotives without an icon, add a placeholder rather than nothing.  the placeholder should be
 * light gray and a simple electric locomotive consisting of: a main rectangle, trapezoid that 1/3 it's
 * height, 2 small closed pantographs, and 4 1/5 height circular wheels in sets of two on each side,
 * spaced evenly apart."
 *
 * **Measured out of the pixels rather than read off the code.** A drawing test that asserts the
 * arithmetic the drawing itself used proves only that the file has not changed; these count what is
 * actually on the picture. Four wheels means four separate runs of ink across the wheel line, and
 * "in sets of two on each side" means the gap in the middle is the biggest of the three.
 *
 * @author Adam
 */
public class testThePlaceholderLocomotive
{
    /**
     * Four wheels, in two pairs, with the widest gap between the pairs.
     */
    @Test
    public void testItHasFourWheelsInTwoPairs()
    {
        BufferedImage picture = LocomotivePlaceholder.image(200);

        // BELOW the body, not through it.
        //
        // The wheels occupy the bottom fifth and the body is drawn down to their centre line, so a row
        // through the middle of a wheel also crosses the body and reads as one continuous mark. Three
        // quarters of the way down the wheels is clear of it and still well inside them.
        int wheel = Math.max(3, Math.round(picture.getHeight() / 5f));

        // FOUND, not assumed.  The drawing is padded away from the edges and how far is its own
        // business - a row counted from the bottom of the PICTURE lands in the margin the moment that
        // padding changes, and reads as no wheels at all rather than as a test that has gone stale.
        int lowest = -1;

        for (int y = picture.getHeight() - 1; y >= 0 && lowest < 0; y--)
        {
            if (inkOnRow(picture, y) > 0) lowest = y;
        }

        assertTrue(lowest > 0, "nothing is drawn at all");

        int band = lowest - wheel / 4;

        int[] runs = runsOfInk(picture, Math.max(0, band));

        assertEquals(runs.length, 4,
            "the wheel line does not cross four separate marks, so this is not four wheels - found "
            + runs.length);

        // Two on each side: the gap between the pairs is wider than either gap within a pair.
        int firstGap = runs[1] - runs[0];
        int middleGap = runs[2] - runs[1];
        int lastGap = runs[3] - runs[2];

        assertTrue(middleGap > firstGap && middleGap > lastGap,
            "the four wheels are evenly spread rather than in two sets of two - gaps were "
            + firstGap + ", " + middleGap + ", " + lastGap);

        assertEquals(firstGap, lastGap, 2,
            "the two pairs are not spaced the same way as each other - " + firstGap + " against "
            + lastGap);
    }

    /**
     * The wheels are a fifth of the height, and the trapezoid a third of the body's.
     */
    @Test
    public void testTheProportionsAreTheOnesAskedFor()
    {
        int width = 200;

        BufferedImage picture = LocomotivePlaceholder.image(width);

        int height = picture.getHeight();

        int wheel = Math.round(height / 5f);

        // The body's own top: the first row where the picture is as wide as it ever gets. The
        // trapezoid above it is narrower by construction, so this is where the rectangle begins.
        int widest = 0;

        for (int y = 0; y < height; y++)
        {
            widest = Math.max(widest, inkOnRow(picture, y));
        }

        int bodyTop = -1;

        for (int y = 0; y < height && bodyTop < 0; y++)
        {
            if (inkOnRow(picture, y) >= widest - 2) bodyTop = y;
        }

        assertTrue(bodyTop > 0, "the body has no top edge, so nothing here is measurable");

        // The body runs from there to the wheel centres, which is where it is drawn to.
        int bodyBottom = height - wheel / 2;

        int body = bodyBottom - bodyTop;

        // The trapezoid sits between the pantographs and the body, and is a third of the body.
        int roofTop = -1;

        for (int y = 0; y < height && roofTop < 0; y++)
        {
            if (inkOnRow(picture, y) > width / 8) roofTop = y;
        }

        int roof = bodyTop - roofTop;

        assertTrue(roof > 0, "there is no trapezoid above the body");

        // A THIRD OF THE BODY PLUS 15%, which is what Adam asked for after seeing the first one.
        assertEquals(roof, Math.round(body / 3f * 1.15f), Math.max(2, body / 6),
            "the trapezoid is not a third of the body's height plus the 15% asked for - it is " + roof
            + " against a body of " + body);
    }

    /**
     * The trapezoid starts where the rectangle does, and its sides lean at 45 degrees.
     *
     * Adam: "make the end of the trapezoid match the end of the rectangles on the x axis, and increase
     * the angle of the slope to be 45 degrees."  The two settle each other - at 45 degrees the step in
     * equals the rise - so this measures the base against the body and then the top against the base.
     */
    @Test
    public void testTheTrapezoidIsFlushAndSlopedAtFortyFive()
    {
        BufferedImage picture = LocomotivePlaceholder.image(240);

        int height = picture.getHeight();

        int widest = 0;

        for (int y = 0; y < height; y++) widest = Math.max(widest, inkOnRow(picture, y));

        // The base of the trapezoid is the widest row it shares with the body, so the two are flush
        // when the roof reaches full width exactly where the body starts.
        int bodyTop = -1;

        for (int y = 0; y < height && bodyTop < 0; y++)
        {
            if (inkOnRow(picture, y) >= widest - 2) bodyTop = y;
        }

        assertTrue(bodyTop > 0, "nothing is as wide as the body, so this cannot be measured");

        int[] atBase = runsOfInk(picture, bodyTop);

        assertEquals(atBase.length, 1,
            "the trapezoid's base is not one continuous span across the body");

        // One row ABOVE the base the roof has stepped in by one on each side, and no more - which is
        // what a 45-degree slope is. Two rows up, two.
        int oneUp = spanOfInk(picture, bodyTop - 1);
        int twoUp = spanOfInk(picture, bodyTop - 2);

        assertEquals(oneUp, widest - 2, 3,
            "the slope does not step in one pixel per row, so it is not 45 degrees - the row above "
            + "the base spans " + oneUp + " against a base of " + widest);

        assertEquals(twoUp, widest - 4, 4,
            "the slope is not 45 degrees two rows up either - it spans " + twoUp);
    }

    /**
     * Nothing is cut off at the edges.
     *
     * Adam: "add some padding so the wheels don't get clipped ... make the pantograph visible."  Both
     * are the same fault at opposite ends, and both show as ink on the outermost row.
     */
    @Test
    public void testNothingTouchesTheEdges()
    {
        BufferedImage picture = LocomotivePlaceholder.image(200);

        assertEquals(inkOnRow(picture, picture.getHeight() - 1), 0,
            "the bottom row of the picture has ink on it, so the wheels are clipped");

        assertEquals(inkOnRow(picture, 0), 0,
            "the top row of the picture has ink on it, so the pantographs are clipped");
    }

    /**
     * How wide the ink on a row reaches, first to last.
     */
    private static int spanOfInk(BufferedImage picture, int y)
    {
        int first = -1;
        int last = -1;

        for (int x = 0; x < picture.getWidth(); x++)
        {
            if ((picture.getRGB(x, y) >>> 24) > 40)
            {
                if (first < 0) first = x;

                last = x;
            }
        }

        return first < 0 ? 0 : last - first + 1;
    }

    /**
     * It is light grey, which is the one thing about it Adam specified twice.
     */
    @Test
    public void testItIsLightGrey()
    {
        BufferedImage picture = LocomotivePlaceholder.image(120);

        int counted = 0;

        for (int x = 0; x < picture.getWidth(); x++)
        {
            for (int y = 0; y < picture.getHeight(); y++)
            {
                int argb = picture.getRGB(x, y);

                // Solid enough to be judging the colour of.
                //
                // It went down to 120 for the few hours the placeholder was translucent, and back up
                // with it: the floor has to follow the colour, or the assertions below run on
                // antialiased edge pixels whose grey is a blend with the background rather than the
                // paint.
                if ((argb >>> 24) < 200) continue;

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                assertTrue(Math.abs(r - g) < 12 && Math.abs(g - b) < 12,
                    "the placeholder is not grey at " + x + "," + y + " - it is " + r + "," + g + ","
                    + b);

                assertTrue(r > 120,
                    "the placeholder is not LIGHT grey at " + x + "," + y + " - it is " + r);

                counted++;
            }
        }

        assertTrue(counted > 100,
            "almost nothing was drawn, so the assertions above passed by having nothing to check - "
            + counted + " pixels");
    }

    /**
     * How many separate runs of ink a row crosses, and where each begins.
     */
    private static int[] runsOfInk(BufferedImage picture, int y)
    {
        java.util.List<Integer> starts = new java.util.ArrayList<>();

        boolean inRun = false;

        for (int x = 0; x < picture.getWidth(); x++)
        {
            boolean ink = (picture.getRGB(x, y) >>> 24) > 40;

            if (ink && !inRun) starts.add(x);

            inRun = ink;
        }

        int[] out = new int[starts.size()];

        for (int i = 0; i < out.length; i++) out[i] = starts.get(i);

        return out;
    }

    /**
     * How many pixels of ink a row carries.
     */
    private static int inkOnRow(BufferedImage picture, int y)
    {
        int ink = 0;

        for (int x = 0; x < picture.getWidth(); x++)
        {
            if ((picture.getRGB(x, y) >>> 24) > 40) ink++;
        }

        return ink;
    }
}
