package ui;

import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.autonomy.AutonomyBanner;

/**
 * OB-191: "why not moving" painted the paths and then said nothing.  Adam: *"when i click on DRG 06 001, 'why not
 * moving' in the autonomy editor correctly paints the paths, but it does not show the list of reasons in the top
 * banner - the banner expands, but I see no text."*
 *
 * The paths are painted and the banner EXPANDS, so the message was composed, handed over and made room for: the fault
 * is in the LAYING OUT.  The banner puts its scroll pane in a `GridBagLayout` with `fill = HORIZONTAL` and
 * `anchor = CENTER`, so a short message sits in the middle of the strip (OB-151, Adam: *"center them vertically within
 * their shaded backgrounds"*).  `GridBagLayout` imposes the cell's height on a child only for `BOTH` or `VERTICAL`;
 * with `HORIZONTAL` the child keeps its PREFERRED height and `CENTER` places it at
 * `(cellHeight - preferredHeight) / 2`, negative for any message taller than the strip (capped at three lines,
 * `MOST_LINES`, OB-366).  So the scroll pane hangs above the strip, shows a slice of its middle, never gets a
 * scrollbar, and `hold`'s `setValue(0)` does nothing.
 *
 * Measured in the mounting the editor uses: `LayoutEditor` floats this banner over the bottom of the diagram's
 * viewport (`AutonomyBanner.floatOver`), which gives it the viewport's width and its own preferred height; a test that
 * set that height itself would be choosing the answer.  And asserted in ink: the geometry says WHY, the pixels are
 * what Adam is looking at.
 *
 * MUTATION: put `middle.fill = java.awt.GridBagConstraints.HORIZONTAL` back in `AutonomyBanner`'s constructor and the
 * first two go red - the first quoting the five pixels, the second reporting no ink at all.
 *
 * @author Adam
 */
public class testTheWhyAnswerIsOnScreen
{

    /**
     * The application's look and feel, before this class stands anything up.  Adam: *"I still see tests popping up with
     * the small font in the menu bar."*  This class builds a bare Swing window, which is nobody's to fix but this
     * one's; without it the window is drawn in Metal, not the window the operator sees.
     * `regression.testEveryWindowWearsTheApplicationsLook` holds this in place.
     */
    @BeforeClass
    public static void theApplicationsLookAndFeel()
    {
        if (!java.awt.GraphicsEnvironment.isHeadless())
        {
            org.traincontrol.gui.TrainControlUI.installLookAndFeel();
        }
    }
    /** How wide the strip is (the diagram viewport's width): wide enough that the answer wraps to several lines. */
    private static final int WIDTH = 900;

    /**
     * A real answer to "why is this train not moving", in the shape `applyWhy` composes (`autosetup.ui.whyReport`, then
     * one `<br>station: reason` per refused destination); a dozen is what his railway answers with and what overflows
     * the cap.
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
     * The message sits inside the strip that was made for it: within the capped `getPreferredSize`, at a non-negative
     * offset.
     */
    @Test
    public void testALongAnswerIsGivenTheWholeBand() throws Exception
    {
        int[] where = messageWithin(mounted(ANSWER));

        assertTrue(where[3] > where[2],
            "this answer wants " + where[3] + "px and the strip has " + where[2] + "px to give it, so it"
            + " is NOT the overflowing case OB-191 is about and nothing below tests anything.  The strip"
            + " caps itself at MOST_LINES; make the answer longer, or find out why the cap is not"
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
     * And there is something to read in it: painted rather than reasoned about, as every other assertion here answers
     * identically with the defect present and gone.
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
     * A one-line notice is still centered in the strip, and still readable: the control in both directions, without
     * which giving the scroll pane the whole band would bring OB-151 back.
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

    /** A floor on the ink one line of this text puts on the strip: font rendering differs between machines. */
    private static final int INK_FOR_A_LINE = 150;

    /**
     * A banner in the mounting `LayoutEditor` gives it, saying something, laid out: floated over a scroll pane by
     * `floatOver` (OB-366).  Laid out EMPTY first, then given the message, then laid out again, as the editor meets
     * it: the message arrives at a strip that already knows how wide it is.
     */
    private static AutonomyBanner mounted(final String message) throws Exception
    {
        final AutonomyBanner[] built = new AutonomyBanner[1];
        final javax.swing.JFrame[] host = new javax.swing.JFrame[1];

        SwingUtilities.invokeAndWait(() ->
        {
            AutonomyBanner banner = new AutonomyBanner();

            javax.swing.JPanel around = new javax.swing.JPanel(new java.awt.BorderLayout());

            around.add(banner.floatOver(new javax.swing.JScrollPane(new javax.swing.JPanel())),
                java.awt.BorderLayout.CENTER);

            javax.swing.JFrame frame = new javax.swing.JFrame();

            frame.setContentPane(around);

            // PACKED, not merely sized: `validate()` on a window never made displayable does nothing (a 1x1 banner in
            // a band of -8px); `pack()` gives it a peer and a first layout.
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
     * Where the message sits inside the strip, found by walking the banner: its top relative to the strip's inside, its
     * height, the strip's inside height, and the height the message wanted.
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
     * The scroll pane's top and height, in the banner's own coordinates, or null when there is no scroll pane below
     * here.
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

    /** The strip as it appears on screen. */
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
     * How many pixels are text: the strip is a light gray band and the message dark blue; counted, because where the
     * text is is what is in doubt.
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

    /** An answer with this many refused destinations in it, as the HTML `applyWhy` would hand over. */
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

    /** The windows this class opened, so none is left behind: a frame with a peer keeps the AWT threads alive. */
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
