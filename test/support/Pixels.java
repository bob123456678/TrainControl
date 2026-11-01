package support;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import javax.swing.SwingUtilities;

/**
 * Counts in a picture a test has taken, and the one way a single tile is painted to take it.  What counts as a color
 * stays with the test that asks (`support.Rendered.isTrainOrange` is the shared orange); these only count.
 */
public final class Pixels
{
    private Pixels()
    {
    }

    /** How many pixels pass the test. */
    public static int count(BufferedImage image, IntPredicate which)
    {
        int found = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if (which.test(image.getRGB(x, y))) found++;
            }
        }

        return found;
    }

    /** How many pixels of `a` differ from the same pixel of `b`, over `a`'s size. */
    public static int differing(BufferedImage a, BufferedImage b)
    {
        int found = 0;

        for (int x = 0; x < a.getWidth(); x++)
        {
            for (int y = 0; y < a.getHeight(); y++)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y)) found++;
            }
        }

        return found;
    }

    /**
     * How many pixels carry anything but the picture's background, its commonest color.  A wash paints every pixel and
     * a fade moves only those that were not background, so "the whole tile is marked" is counted against these.
     */
    public static int drawn(BufferedImage image)
    {
        Map<Integer, Integer> seen = new HashMap<>();

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                int rgb = image.getRGB(x, y);

                seen.put(rgb, seen.containsKey(rgb) ? seen.get(rgb) + 1 : 1);
            }
        }

        int background = 0;

        for (Map.Entry<Integer, Integer> each : seen.entrySet())
        {
            if (each.getValue() > background) background = each.getValue();
        }

        return image.getWidth() * image.getHeight() - background;
    }

    /** Brightest minus darkest pixel, by the mean of its three channels: its own contrast, whatever the theme. */
    public static int contrast(BufferedImage image)
    {
        int darkest = 255;
        int lightest = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                int rgb = image.getRGB(x, y);

                int shade = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;

                darkest = Math.min(darkest, shade);
                lightest = Math.max(lightest, shade);
            }
        }

        return lightest - darkest;
    }

    /**
     * Paints one tile on its own, on white, sized `size` square, as the diagram paints it: `paint`, because `printAll`
     * does nothing for a component that is not on screen.  Runs on the event thread and waits.
     */
    public static BufferedImage paint(final Component tile, final int size) throws Exception
    {
        final BufferedImage[] shot = new BufferedImage[1];

        SwingUtilities.invokeAndWait(() ->
        {
            tile.setSize(size, size);

            shot[0] = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = shot[0].createGraphics();

            try
            {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, size, size);

                tile.paint(g);
            }
            finally
            {
                g.dispose();
            }
        });

        return shot[0];
    }
}
