package ui;

import java.awt.image.BufferedImage;
import java.net.URL;
import javax.imageio.ImageIO;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * The sidebar icons are one flat colour, and that colour is the theme's.
 *
 * FR-029. Adam: "the sidebar icons (locomotive, track, autonomy, signal, route, stats, log), while
 * nice, date the application.  use modernized, simple icons with a plain blue color matching the
 * flatlaf theme."
 *
 * **These are assets, not code, which is exactly why they are worth a test.** Nothing compiles them,
 * nothing refers to them by anything but a string, and a file that goes missing or comes back as a
 * photograph fails silently at the top left of the window. The three things asserted here are the
 * three ways that goes wrong: the file is not there, it is not one colour, or it is not the colour the
 * rest of the window is painted in.
 *
 * The size they are drawn at is deliberately NOT asserted. `getTabIcon` scales whatever it finds to 30
 * pixels tall, so the source size is free, and pinning it would mean anybody redrawing them had to
 * match a number that does not matter.
 *
 * @author Adam
 */
public class testSidebarIcons
{
    /**
     * Every tab in the sidebar, by the file it is drawn from.
     */
    private static final String[] ICONS =
    {
        "loc", "track", "autonomy", "signal", "route", "stats", "log"
    };

    /**
     * FlatLightLaf's accent, which is what "matching the flatlaf theme" means here.
     *
     * Written down twice on purpose - once in the generator that drew the files and once here - because
     * the two are not connected by anything the compiler can see. If they ever disagree, this is where
     * it is noticed, and the message says which one to change.
     */
    private static final int THEME_BLUE = 0x2675BF;

    /**
     * Each icon is a single colour on transparency.
     *
     * A flat mark reads at 30 pixels; a picture does not, which was the whole of the complaint. Partial
     * alpha is allowed and expected - the edges are antialiased - but every pixel with any opacity at
     * all has to be the same colour underneath.
     */
    @Test
    public void testEverySidebarIconIsTheThemeBlue() throws Exception
    {
        for (String name : ICONS)
        {
            URL url = TrainControlUI.class.getResource("resources/tabs/" + name + ".png");

            assertNotNull(url, "resources/tabs/" + name + ".png is not on the classpath. The sidebar "
                + "draws it by name and would come up blank, which is a thing nobody looks at twice");

            BufferedImage image = ImageIO.read(url);

            assertNotNull(image, name + ".png could not be decoded as an image");

            int ink = 0;

            for (int x = 0; x < image.getWidth(); x++)
            {
                for (int y = 0; y < image.getHeight(); y++)
                {
                    int pixel = image.getRGB(x, y);

                    int alpha = (pixel >>> 24) & 0xFF;

                    // Nearly transparent is the antialiased edge, where the colour is unreliable and
                    // nobody can see it anyway.
                    if (alpha < 128) continue;

                    ink++;

                    assertEquals(pixel & 0xFFFFFF, THEME_BLUE,
                        name + ".png has a pixel at " + x + "," + y + " that is #"
                        + Integer.toHexString(pixel & 0xFFFFFF) + " rather than the theme blue #"
                        + Integer.toHexString(THEME_BLUE) + ". These are meant to be one flat colour: "
                        + "a mark that reads at thirty pixels, which is the size the sidebar draws "
                        + "them at");
                }
            }

            assertTrue(ink > 1000, name + ".png is nearly empty - " + ink + " opaque pixels - so "
                + "whatever is in the file, it is not an icon");
        }
    }

    /**
     * And they still say something at the size they are actually drawn.
     *
     * An icon that is legible at 512 pixels and a grey smudge at 30 is the commonest way this kind of
     * change goes wrong, and it is invisible to anybody looking at the source files. So the scaling
     * the application does is done here too, and what is asserted is that a useful part of the mark
     * survives it: enough ink to be a shape, and not so much that it is a filled square.
     */
    @Test
    public void testEverySidebarIconSurvivesBeingScaledDown() throws Exception
    {
        for (String name : ICONS)
        {
            BufferedImage source =
                ImageIO.read(TrainControlUI.class.getResource("resources/tabs/" + name + ".png"));

            BufferedImage small = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = small.createGraphics();

            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g.drawImage(source, 0, 0, 30, 30, null);
            g.dispose();

            int ink = 0;

            for (int x = 0; x < 30; x++)
            {
                for (int y = 0; y < 30; y++)
                {
                    if (((small.getRGB(x, y) >>> 24) & 0xFF) > 100) ink++;
                }
            }

            assertTrue(ink > 40,
                name + " leaves only " + ink + " visible pixels at 30x30, which is a smudge rather "
                + "than an icon. Whatever it is, it is drawn too finely for the size it is seen at");

            assertTrue(ink < 700,
                name + " covers " + ink + " of 900 pixels at 30x30, which is close enough to a filled "
                + "square that its shape says nothing");
        }
    }
}
