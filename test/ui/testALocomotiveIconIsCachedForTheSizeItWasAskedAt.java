package ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * The locomotive icon cache holds a picture per URL, width and height limit.
 *
 * The plain request and the height-limited one used to cache under the same key, url + width, so whichever
 * came first decided what the other got back.
 */
public class testALocomotiveIconIsCachedForTheSizeItWasAskedAt
{
    private static final int WIDTH = 34;

    /** Half the 136 px the plain picture is tall, so the limited one is half as wide (17) and as tall (68). */
    private static final int LIMIT = 68;

    /**
     * The same URL and width, asked plain and height-limited, give two correct pictures, in either order.
     *
     * MUTATION: build the key in `TrainControlUI.getLocImageMaxHeight` as `url + size` for every limit; whichever
     * kind of request comes first then answers the other, and the second picture of each pair has the wrong size.
     *
     * @throws Exception from the window or the picture
     */
    @Test
    public void testPlainAndLimitedAreTwoPictures() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the cache is the window's");

        support.LayoutSandbox sandbox = null;
        File folder = null;
        TrainControlUI ui = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            final TrainControlUI[] built = new TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());

            ui = built[0];

            folder = java.nio.file.Files.createTempDirectory("iconcache").toFile();

            // The cache is static and keyed by URL: a picture per order, so neither order inherits the other's.
            String plainFirst = picture(folder, "plainfirst.png");
            String limitedFirst = picture(folder, "limitedfirst.png");

            Image plain = ui.getLocImage(plainFirst, WIDTH);
            Image limited = ui.getLocImageMaxHeight(plainFirst, WIDTH, LIMIT);

            assertSize(plain, WIDTH, 136, "the plain request");
            assertSize(limited, 17, 68, "the height-limited request after a plain one at the same width");

            Image limitedThenPlain = ui.getLocImageMaxHeight(limitedFirst, WIDTH, LIMIT);
            Image plainThenLimited = ui.getLocImage(limitedFirst, WIDTH);

            assertSize(limitedThenPlain, 17, 68, "the height-limited request");
            assertSize(plainThenLimited, WIDTH, 136, "the plain request after a limited one at the same width");

            // Each is cached for the next ask: the same picture comes back, not a second read and scale.
            assertSame(ui.getLocImage(plainFirst, WIDTH), plain, "the plain picture was not cached");
            assertSame(ui.getLocImageMaxHeight(plainFirst, WIDTH, LIMIT), limited, "the limited picture was not cached");

            // A plain request is a request with no limit at all.
            assertSame(ui.getLocImageMaxHeight(plainFirst, WIDTH, Integer.MAX_VALUE), plain,
                "a request with no limit is not the plain one");

            // A limit the picture is under changes nothing about its size.
            assertSize(ui.getLocImageMaxHeight(plainFirst, WIDTH, 1000), WIDTH, 136, "a limit above the picture");
        }
        finally
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                javax.swing.SwingUtilities.invokeAndWait(closing::dispose);
            }

            if (folder != null)
            {
                File[] files = folder.listFiles();

                if (files != null) for (File f : files) f.delete();

                folder.delete();
            }

            if (sandbox != null) sandbox.close();
        }
    }

    private static String picture(File folder, String name) throws Exception
    {
        File file = new File(folder, name);

        // 100 wide and 400 tall: the aspect is 4, so 34 px wide is 136 px tall.
        ImageIO.write(new BufferedImage(100, 400, BufferedImage.TYPE_INT_ARGB), "png", file);

        return file.toURI().toURL().toString();
    }

    private static void assertSize(Image image, int width, int height, String what)
    {
        assertNotNull(image, what + " gave no picture");
        assertEquals(image.getWidth(null), width, what + " is the wrong width");
        assertEquals(image.getHeight(null), height, what + " is the wrong height");
    }
}
