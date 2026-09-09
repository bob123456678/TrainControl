package ui;

import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.AutonomyBanner;

/**
 * OB-191: "why not moving" painted the paths and then said nothing.
 *
 * Adam, 2026-09-08: *"when i click on DRG 06 001, 'why not moving' in the autonomy editor correctly
 * paints the paths, but it does not show the list of reasons in the top banner - the banner expands,
 * but I see no text."*
 *
 * **THREE THINGS IN THAT SENTENCE, AND THEY NARROW IT TO ONE PLACE.**  The paths are painted, so
 * `AutonomyEditorPanel.applyWhy` reached its loop and `Layout.explainDestinations` answered - the
 * traces and the reasons come out of ONE pass over ONE map there, so an answer that draws a path has
 * an entry for every station it refused.  The banner EXPANDS, so `AutonomyBanner.hold` ran, `saying`
 * was set, and `getPreferredSize` grew the strip to fit something it had been given.  And there is no
 * text.  A message composed, handed over, and made room for, and still not readable, is not a fault in
 * the composing - it is a fault in the LAYING OUT.
 *
 * **WHAT IT IS.**  The banner puts its scroll pane in a `GridBagLayout` with `fill = HORIZONTAL` and
 * `anchor = CENTER`, so that a short message sits in the middle of the strip rather than on top of it
 * (OB-151, Adam: *"center them vertically within their shaded backgrounds"*).  The comment there
 * claims "a long message still fills and still scrolls, because GridBag shrinks a child to the space
 * available rather than letting it overflow", and that is not what `GridBagLayout` does.  It imposes
 * the cell's height on a child only when the fill is `BOTH` or `VERTICAL`; with `HORIZONTAL` the child
 * keeps its PREFERRED height, and `CENTER` then places it at `(cellHeight - preferredHeight) / 2` -
 * negative for any message taller than the strip.  The strip is capped at `MAXIMUM_HEIGHT`, so every
 * answer longer than the cap is taller than it.
 *
 * So the scroll pane is laid out taller than the strip and hung above it, and the strip shows a slice
 * out of its middle.  Its viewport is then never smaller than its own contents, so it never gets a
 * scrollbar - and `hold`'s `setValue(0)` and `setCaretPosition(0)`, which exist to put a second answer
 * back at the top, have nothing to scroll and do nothing.  Where the slice lands depends on how much
 * taller the pane is than the strip.
 *
 * **MEASURED IN THE MOUNTING THE EDITOR USES**, not in a bare panel: `LayoutEditor` puts this banner
 * in a `JScrollPane`'s column header, and `ScrollPaneLayout` sizes a column header to the viewport's
 * width and the header's own preferred height.  That height is the number this defect turns on, so a
 * test that set it itself would be choosing the answer.
 *
 * **AND ASSERTED IN INK.**  The geometry says WHY; the pixels are what Adam is looking at.  A change
 * that put the scroll pane back inside the strip and still drew nothing would pass a geometry
 * assertion on its own - which is the argument `support.Rendered` makes about the diagram, one
 * component over.
 *
 * MUTATION: put `middle.fill = java.awt.GridBagConstraints.HORIZONTAL` back in `AutonomyBanner`'s
 * constructor and the first two go red - the first quoting the five pixels, the second reporting no ink at all.
 *
 * @author Adam
 */
public class testTheWhyAnswerIsOnScreen
{
    /**
     * How wide the strip is, which is the diagram viewport's width.  Wide enough that the answer wraps
     * to several lines rather than a hundred, which is the real case.
     */
    private static final int WIDTH = 900;

    /**
     * A real answer to "why is this train not moving", in the shape `applyWhy` composes.
     *
     * The same pieces: `autosetup.ui.whyReport` is `<b>{0}</b>: {1}{2}`, `{1}` is `whyCanGo` or
     * `whyNowhere`, and `{2}` is one `<br>station: reason` per refused destination.  A dozen is what
     * his railway answers with - `live-snapshot` carries thirty-six station squares - and a dozen is
     * what overflows the cap.
     */
    private static final String ANSWER = answer(12);

    /** One line, which is what the centring in OB-151 was added for. */
    private static final String NOTICE = "This tile is now one way.";

    @BeforeClass
    public static void setUpClass()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the banner has to be laid out and painted, so Swing has to be here");
        }
    }

    // ---------------------------------------------------------------- the claim

    /**
     * The message sits inside the strip that was made for it.
     *
     * The strip's height is `getPreferredSize`, capped; whatever holds the message has to fit inside
     * that, at a non-negative offset.  Anything else is a slice out of the middle of a taller thing.
     */
    @Test
    public void testALongAnswerIsGivenTheWholeBand() throws Exception
    {
        int[] where = messageWithin(mounted(ANSWER));

        assertTrue(where[3] > where[2],
            "this answer wants " + where[3] + "px and the strip has " + where[2] + "px to give it, so it"
            + " is NOT the overflowing case OB-191 is about and nothing below tests anything.  The strip"
            + " caps itself at MAXIMUM_HEIGHT; make the answer longer, or find out why the cap is not"
            + " being reached");

        assertEquals(where[1], where[2],
            "the message wants " + where[3] + "px, the strip's band is " + where[2] + "px, and the"
            + " message has been laid out " + where[1] + "px tall at y=" + where[0] + " - which is"
            + " OB-191.  A band that is one pixel short is not a reason to draw five pixels of a"
            + " document: an overflowing message must be given the WHOLE band, because that is the only"
            + " thing that makes the scroll pane's viewport smaller than its contents, and a viewport"
            + " smaller than its contents is the only thing that makes an AS_NEEDED scrollbar appear."
            + "  `GridBagLayout` with a HORIZONTAL fill shrinks toward the child's MINIMUM instead,"
            + " which for a JScrollPane is a few pixels, and CENTER then parks that sliver in the"
            + " middle of an otherwise empty strip");

        assertEquals(where[0], 0,
            "the message fills the band but starts at y=" + where[0] + ", so the top of it is above the"
            + " strip and the bottom is below it");
    }

    /**
     * And there is something to read in it.
     *
     * The geometry above says why; this is what Adam is looking at.  Painted rather than reasoned
     * about, because every other assertion available here answers identically with the defect present
     * and with it gone.
     */
    @Test
    public void testTheAnswerHasInkInIt() throws Exception
    {
        AutonomyBanner banner = mounted(ANSWER);

        assertTrue(banner.isSaying(),
            "the banner does not think it is saying anything, so this is not the OB-191 shape at all -"
            + " that one composed the answer and then failed to show it");

        BufferedImage strip = painted(banner);

        int ink = darkPixels(strip);

        assertTrue(ink > INK_FOR_A_LINE,
            "the strip is " + strip.getWidth() + "x" + strip.getHeight() + " and has " + ink
            + " pixels of text in it, against the " + INK_FOR_A_LINE + " a single line puts there."
            + "  It has been handed a message and has grown to fit it, and there is nothing to read -"
            + " which is OB-191 in Adam's own words: \"the banner expands, but I see no text\"."
            + "  testALongAnswerIsGivenTheWholeBand says where the text went");
    }

    /**
     * A one-line notice is still centred in the strip, and still readable.
     *
     * THE CONTROL, and it is a control in both directions.  Without it, giving the scroll pane the
     * whole band would pass the two above and bring OB-151 straight back - the short notice resting on
     * the top of a taller strip, which is what the centring was added to stop.  The ink half is what
     * says the two tests above are about long answers rather than about the banner drawing nothing at
     * all.
     */
    @Test
    public void testAShortNoticeIsStillCentredAndVisible() throws Exception
    {
        AutonomyBanner banner = mounted(NOTICE);

        int[] where = messageWithin(banner);

        assertTrue(where[1] < where[2],
            "a one-line notice is being given the whole band (" + where[1] + "px of " + where[2]
            + "px), so there is no slack left to centre it in.  That is OB-151 coming back: Adam asked"
            + " for these to be centred in their shaded backgrounds, and a message stretched to fill"
            + " rests on the top of the strip instead");

        int above = where[0];
        int below = where[2] - where[0] - where[1];

        assertTrue(Math.abs(above - below) <= 1,
            "the notice has " + above + "px above it and " + below + "px below, so it is not centred"
            + " (OB-151)");

        assertTrue(darkPixels(painted(banner)) > INK_FOR_A_LINE,
            "a one-line notice has nothing to read in it either, so the two tests above are not about"
            + " long answers - the banner is drawing nothing at all");
    }

    // ---------------------------------------------------------------- the fixture

    /**
     * How much ink one line of this text puts on the strip.
     *
     * A floor rather than an exact count: font rendering differs between machines, and the question
     * being asked is "is there anything there", which does not need a pixel-exact answer.  Measured at
     * roughly 1500 for the twelve-station answer and roughly 400 for the one-line notice.
     */
    private static final int INK_FOR_A_LINE = 150;

    /**
     * A banner in the mounting `LayoutEditor` gives it, saying something, laid out.
     *
     * `setColumnHeaderView` rather than a panel of our own: `ScrollPaneLayout` sizes a column header to
     * the viewport's width and the header's own `getPreferredSize().height`, and that height - capped
     * at `MAXIMUM_HEIGHT` - is the number this defect turns on.
     *
     * LAID OUT EMPTY FIRST, then given the message, then laid out again.  That is the order the editor
     * meets: the banner has been on screen since the window opened, so the message arrives at a strip
     * that already knows how wide it is - and how tall the message wraps to depends on that width.
     *
     * @param message what to say
     * @return the banner, sized and laid out
     */
    private static AutonomyBanner mounted(final String message) throws Exception
    {
        final AutonomyBanner[] built = new AutonomyBanner[1];
        final javax.swing.JFrame[] host = new javax.swing.JFrame[1];

        SwingUtilities.invokeAndWait(() ->
        {
            AutonomyBanner banner = new AutonomyBanner();

            javax.swing.JScrollPane around = new javax.swing.JScrollPane(new javax.swing.JPanel());

            around.setColumnHeaderView(banner);

            javax.swing.JFrame frame = new javax.swing.JFrame();

            frame.setContentPane(around);

            // PACKED, not merely sized.  `validate()` on a window that has never been made displayable
            // does nothing at all - the first run of this test measured a 1x1 banner inside a band of
            // -8px, which is not a defect, it is a component that was never laid out.  `pack()` gives
            // it a peer and a first layout; the size after it is this test's own.
            frame.pack();
            frame.setSize(WIDTH, 600);
            frame.validate();

            banner.show(message);

            built[0] = banner;
            host[0] = frame;

            OPENED.add(frame);
        });

        settle();

        // Again, because `hold` posts its own pass and the height the strip asks for depends on the
        // width the message has just been wrapped to.
        SwingUtilities.invokeAndWait(() ->
        {
            host[0].validate();
            host[0].getContentPane().doLayout();
        });

        settle();

        return built[0];
    }

    /**
     * Where the message sits inside the strip.
     *
     * Found by walking the banner rather than by naming a field, so a fix is free to rearrange what
     * holds the message as long as something still scrolls it.
     *
     * @param banner the banner
     * @return its top relative to the strip's inside, its height, the strip's inside height, and the
     *         height the message wanted
     */
    private static int[] messageWithin(final AutonomyBanner banner) throws Exception
    {
        final int[] where = new int[4];

        SwingUtilities.invokeAndWait(() ->
        {
            java.awt.Insets pad = banner.getInsets();

            where[2] = banner.getHeight() - pad.top - pad.bottom;

            int[] found = locate(banner);

            assertNotNull(found, "the banner has nothing in it that scrolls the message any more");

            where[0] = found[0] - pad.top;
            where[1] = found[1];
            where[3] = found[2];
        });

        return where;
    }

    /**
     * The scroll pane's top and height, in the banner's own coordinates.
     *
     * @param in what to look in
     * @return top, height and preferred height, or null when there is no scroll pane below here
     */
    private static int[] locate(java.awt.Container in)
    {
        for (java.awt.Component child : in.getComponents())
        {
            if (child instanceof javax.swing.JScrollPane)
            {
                return new int[] { child.getY(), child.getHeight(),
                    child.getPreferredSize().height };
            }

            if (child instanceof java.awt.Container)
            {
                int[] deeper = locate((java.awt.Container) child);

                // Offset by the parent that holds it, so every answer is in the banner's coordinates
                if (deeper != null)
                {
                    return new int[] { deeper[0] + child.getY(), deeper[1], deeper[2] };
                }
            }
        }

        return null;
    }

    /**
     * The strip as it appears on screen.
     *
     * @param banner the banner
     * @return its pixels
     */
    private static BufferedImage painted(final AutonomyBanner banner) throws Exception
    {
        final BufferedImage[] shot = new BufferedImage[1];

        SwingUtilities.invokeAndWait(() ->
        {
            BufferedImage image = new BufferedImage(Math.max(1, banner.getWidth()),
                Math.max(1, banner.getHeight()), BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D into = image.createGraphics();

            banner.paint(into);

            into.dispose();

            shot[0] = image;
        });

        return shot[0];
    }

    /**
     * How much ink there is.
     *
     * The strip is a light grey band and the message is dark blue; anything much darker than the band
     * is a letter.  Counted rather than sampled, because where the text is is exactly what is in doubt.
     *
     * @param image the strip
     * @return how many pixels are text
     */
    private static int darkPixels(BufferedImage image)
    {
        int dark = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                java.awt.Color at = new java.awt.Color(image.getRGB(x, y));

                if (at.getRed() + at.getGreen() + at.getBlue() < 400) dark++;
            }
        }

        return dark;
    }

    /**
     * An answer with this many refused destinations in it.
     *
     * @param stations how many
     * @return the HTML `applyWhy` would hand over
     */
    private static String answer(int stations)
    {
        StringBuilder out = new StringBuilder(
            "<b>DRG 06 001</b>: can go to 2 station(s) - BottomMainA, BottomMainC");

        for (int at = 1; at <= stations; at++)
        {
            out.append("<br>Station ").append(at)
                .append(": occupied by another locomotive, and there is no other way round to it");
        }

        return out.toString();
    }

    /**
     * The windows this class opened, so none is left behind.
     *
     * A frame with a peer keeps the AWT threads alive, and a class that leaves one poisons every class
     * after it in a runner that shares a JVM.
     */
    private static final java.util.List<javax.swing.JFrame> OPENED = new java.util.ArrayList<>();

    @org.testng.annotations.AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (javax.swing.JFrame frame : OPENED) frame.dispose();
        });
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 5; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
