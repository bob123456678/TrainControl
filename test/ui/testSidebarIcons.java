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
 * Then, having looked at them (2026-08-26): "Try using dark gray like 333 for the icons, rather than
 * the blue." So the colour asserted here is #333333, and the flat-colour rule is what it always was -
 * what changed is which single colour, not that there is one.
 *
 * Safe on this application because there is exactly one look and feel: `FlatLightLaf.setup()`, with no
 * dark variant offered anywhere. Dark grey on a dark tab strip would be close to invisible, and if a
 * dark theme is ever added this is one of the things that has to be revisited.
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
     * The one colour every icon is drawn in.
     *
     * Written down twice on purpose - once in `docs/tools/tab-icons.py`, which drew the files, and once here
     * - because the two are not connected by anything the compiler can see. If they ever disagree,
     * this is where it is noticed, and the message says which one to change.
     */
    private static final int ICON_INK = 0x333333;

    /**
     * Each icon is a single colour on transparency.
     *
     * A flat mark reads at 30 pixels; a picture does not, which was the whole of the complaint. Partial
     * alpha is allowed and expected - the edges are antialiased - but every pixel with any opacity at
     * all has to be the same colour underneath.
     *
     * The generator draws at four times the output size and reduces with LANCZOS, so there are a great
     * many more partial-alpha pixels than there used to be. That is the point: PIL does not antialias
     * shape edges, and a lamp punched out of a signal head came out with a stepped edge that survived
     * the scale down. Adam called it "poorly traced", which is what it was.
     */
    @Test
    public void testEverySidebarIconIsOneFlatColour() throws Exception
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

                    assertEquals(pixel & 0xFFFFFF, ICON_INK,
                        name + ".png has a pixel at " + x + "," + y + " that is #"
                        + Integer.toHexString(pixel & 0xFFFFFF) + " rather than #"
                        + Integer.toHexString(ICON_INK) + ". These are meant to be one flat colour: "
                        + "a mark that reads at thirty pixels, which is the size the sidebar draws "
                        + "them at");
                }
            }

            assertTrue(ink > 1000, name + ".png is nearly empty - " + ink + " opaque pixels - so "
                + "whatever is in the file, it is not an icon");
        }
    }

    /**
     * Each tab's icon is set on the tab that icon is for.
     *
     * The sidebar is built by the GUI designer, which fixes the order the panels are added in, and the
     * icons are then applied afterwards BY INDEX. Nothing connects the two: the panel at index 3 and
     * `setIconAt(3, ...)` agree only because somebody counted, and they stay agreeing only as long as
     * nobody moves a tab without recounting.
     *
     * Adam asked for exactly that move on 2026-08-27 - "swap the position of the route tab with the
     * keyboard tab" - which is done by lifting the routes panel out and putting it back one place
     * earlier. Get that right and forget the icons, and the routes tab wears the signal icon and the
     * signal tab wears the routes one, with correct tooltips on both. Nothing throws, nothing looks
     * broken, and every icon is wrong.
     *
     * **This reads the source, which is weaker than running it, and the reason is worth recording.**
     * The reorder happens in `setViewListener`, not in the constructor, so a window built the way the
     * other tests here build one has the DESIGNER's order and not this one. Reaching the real order
     * would mean standing up a model and a control station to hand it. What this checks instead is the
     * one thing that can silently drift: that the index a tab is moved to is the index its icon is set
     * on.
     *
     * MUTATION: swapping either the two `insertTab` positions or the two `setIconAt` indices - but not
     * both - fails this.
     */
    @Test
    public void testEachTabIconIsOnTheTabItNames() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")), java.nio.charset.StandardCharsets.UTF_8);

        // Where the routes panel is put back, and where the routes icon is put.
        java.util.regex.Matcher moved = java.util.regex.Pattern
            .compile("insertTab\\(\"Rout\"[^;]*?(\\d+)\\);").matcher(source);

        assertTrue(moved.find(), "the routes tab is no longer moved at all - if the designer's order "
            + "is wanted again, this test and the reorder should go together");

        int routesAt = Integer.parseInt(moved.group(1));

        assertEquals(iconIndex(source, "TAB_ICON_ROUTES"), routesAt,
            "the routes panel is moved to tab " + routesAt + " and the routes icon is put on a "
            + "different one, so the routes tab is wearing somebody else's picture");

        // And the keyboard tab, which is the one the routes tab displaced.
        int keyboardAt = iconIndex(source, "TAB_ICON_KEYBOARD");

        // WHERE it is, not merely that it differs.
        //
        // `iconIndex` answers -1 when the constant is never applied at all, and -1 differs from 3 -
        // so deleting the keyboard icon entirely satisfied the old assertNotEquals. The three
        // assertions around it use assertEquals; this one was the odd one out (reviewer, 2026-08-28).
        assertEquals(keyboardAt, 4,
            "the keyboard icon is on tab " + keyboardAt + " rather than tab 4, so either it moved or "
            + "- if this says -1 - it is never applied and that tab has no icon at all");

        assertNotEquals(keyboardAt, routesAt,
            "the routes icon and the keyboard icon are both set on tab " + routesAt + ", so one of "
            + "them is overwriting the other and a tab is left with no icon at all");

        // The two that were never asked to move, as a control: if this test only proved things about
        // the pair that changed, it would pass just as happily on a sidebar where everything else had
        // been renumbered.
        assertEquals(iconIndex(source, "TAB_ICON_LAYOUT"), 1, "the diagram tab has moved");
        assertEquals(iconIndex(source, "TAB_ICON_AUTONOMY"), 2, "the autonomy tab has moved");
    }

    /**
     * Which tab index an icon constant is applied to.
     *
     * @param source TrainControlUI's text
     * @param icon the constant's name
     * @return the index, or -1 when it is never applied
     */
    private int iconIndex(String source, String icon)
    {
        java.util.regex.Matcher at = java.util.regex.Pattern
            .compile("setIconAt\\((\\d+), " + icon + "\\)").matcher(source);

        return at.find() ? Integer.parseInt(at.group(1)) : -1;
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
