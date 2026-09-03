package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LoadingSpinner;

/**
 * What the "still drawing" mark actually draws, and where the signal window actually goes.
 *
 * Two things Adam reported by looking at them (FR-024 and OB-107), so they are checked here by looking
 * at them too - the drawing is rendered to an image and the image is measured, and the placement is a
 * function of two rectangles that can simply be called.
 *
 * **Why this is not a source-reading guard.** The obvious way to hold FR-024 would be to assert that
 * the paint method mentions an hourglass, and the obvious way to hold OB-107 would be to assert that
 * the dialog code no longer says setLocationRelativeTo. Neither would have caught the two things that
 * actually went wrong while this was being built: a silhouette that came out as a bow tie, and a
 * second attempt that came out as a rectangle with an X in it. Both of those mention nothing and read
 * perfectly well. Only the pixels knew.
 *
 * The load-bearing assertion is the sand count: sand is conserved, so what leaves the upper bulb has
 * to arrive in the lower one, and that is a statement about the drawing which the drawing code does
 * not itself make anywhere.
 *
 * Its limit is written out on the method, because it is narrower than it first looks and a later
 * reader should not have to rediscover that.
 *
 * @author Adam
 */
public class testTheWaitMarkIsAnHourglass
{
    /** Frames in one drain-and-turn cycle - LoadingSpinner draws two of them per loop. */
    private static final int CYCLE = 62;

    /** Big enough that a few pixels of antialiasing either way cannot change a count materially. */
    private static final int SIZE = 240;

    /**
     * Paints the mark at a given frame of its cycle, offscreen.
     *
     * @param frames how many frames to advance before painting
     * @return the picture, on white
     */
    private BufferedImage shoot(int frames) throws Exception
    {
        LoadingSpinner mark = new LoadingSpinner();
        mark.setSize(SIZE, SIZE);

        for (int i = 0; i < frames; i++)
        {
            mark.advanceOneFrame();
        }

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, SIZE, SIZE);
        mark.paint(g);
        g.dispose();

        return out;
    }

    /**
     * Counts pixels that are neither white nor nearly white, above and below the middle.
     *
     * @param shot a picture from {@link #shoot}
     * @param top true to count the upper half, false for the lower half
     * @return how many pixels carry ink
     */
    private int ink(BufferedImage shot, boolean top)
    {
        int from = top ? 0 : SIZE / 2;
        int to = top ? SIZE / 2 : SIZE;

        int count = 0;

        for (int y = from; y < to; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                int rgb = shot.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Grey of any darkness, which is all this draws.  The threshold is well clear of the
                // antialiased edge between the sand and the white behind it.
                if (r < 230 && g < 230 && b < 230) count++;
            }
        }

        return count;
    }

    /**
     * The mark draws something, and it is grey rather than the black the turning arc used.
     *
     * The weakest assertion here, and it is only worth having because a drawing method that throws or
     * that draws nothing is a real failure mode for a component nobody looks at in a test.
     */
    @Test
    public void testItDrawsSomethingGrey() throws Exception
    {
        BufferedImage shot = shoot(0);

        int drawn = ink(shot, true) + ink(shot, false);

        assertTrue(drawn > 500, "the wait mark drew almost nothing: " + drawn + " pixels");

        // Not black.  Adam asked for grey specifically, and the arc this replaced drew in black with
        // alpha, which composites to grey against white - so a colour check has to look at the
        // DARKEST pixel rather than the average, or the two are indistinguishable.
        int darkest = 255;

        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                darkest = Math.min(darkest, shot.getRGB(x, y) & 0xFF);
            }
        }

        assertTrue(darkest > 80, "the wait mark is drawing too dark to be called grey: " + darkest);
    }

    /**
     * Sand is conserved: what drains out of the top arrives in the bottom.
     *
     * The assertion this class exists for. Every other check here would still pass if the two bulbs
     * were driven by unrelated formulae - the picture would still be an hourglass, still be grey,
     * still change frame to frame, and the sand would simply appear and vanish as it fell.
     *
     * The band is a twelfth, chosen from measurement rather than taste. Over a real cycle the count
     * runs 7176, 7504, 7479, 7455, 7417, 7382, 7399, 7338, 7213 - a spread of 4.4%, which is the
     * curved outline and the falling stream being counted as ink alongside the sand. Driving the lower
     * bulb from its own level instead of the shared one takes the spread to 20%.
     *
     * MUTATION: replacing the lower bulb's rectangle with one on a level of its own - `bulbH * (1.0 -
     * drained * 0.5)` for `bulbH * remaining` - fails this test. Measured: the count falls away to
     * 5984 by the end of the cycle instead of holding near 7200.
     *
     * WHAT THIS DOES NOT COVER, recorded so nobody assumes otherwise: the square root in `remaining`.
     * Both bulbs read the same variable, so whatever curve it follows the areas still sum to the same
     * constant - replacing the root with the plain fraction passes all seven tests here. The root is
     * how the sand LOOKS as it falls, and nothing in this battery can see that.
     */
    @Test
    public void testTheSandIsConserved() throws Exception
    {
        int[] frames = {0, 12, 25, 37, 49};

        int[] totals = new int[frames.length];

        for (int i = 0; i < frames.length; i++)
        {
            BufferedImage shot = shoot(frames[i]);

            totals[i] = ink(shot, true) + ink(shot, false);
        }

        int smallest = Integer.MAX_VALUE;
        int largest = 0;

        for (int total : totals)
        {
            smallest = Math.min(smallest, total);
            largest = Math.max(largest, total);
        }

        assertTrue(largest - smallest < largest / 12,
            "the amount of sand changes as it falls, from " + smallest + " to " + largest
            + " - the two bulbs are not agreeing about the level");
    }

    /**
     * It starts full at the top and ends full at the bottom, which is the direction sand falls in.
     *
     * A drawing that ran the animation backwards, or that filled both bulbs at once, would satisfy the
     * conservation check above and fail this one.
     */
    @Test
    public void testItRunsDownwards() throws Exception
    {
        BufferedImage full = shoot(0);
        BufferedImage empty = shoot(49);

        assertTrue(ink(full, true) > ink(full, false),
            "at the start of the cycle the sand should be in the upper bulb");

        assertTrue(ink(empty, false) > ink(empty, true),
            "by the end of the cycle the sand should have fallen to the lower bulb");
    }

    /**
     * The picture changes from frame to frame, so the animation is animation and not a still.
     *
     * Adam asked for it to move: "animate if possible."
     */
    @Test
    public void testItMoves() throws Exception
    {
        BufferedImage first = shoot(0);
        BufferedImage later = shoot(20);

        int different = 0;

        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                if (first.getRGB(x, y) != later.getRGB(x, y)) different++;
            }
        }

        assertTrue(different > 200, "the wait mark is not moving: " + different + " pixels differ");
    }

    /**
     * Nothing is drawn outside the component, at any point in the cycle including the turn.
     *
     * The turn is the frame that can break this: the glass is rotated about the centre, so a shape
     * sized to fill the space upright sweeps outside it halfway round. Checking a border of pixels
     * says the drawing left itself room to turn in.
     */
    @Test
    public void testItStaysInsideItsBounds() throws Exception
    {
        for (int frame = 0; frame < 62; frame += 3)
        {
            BufferedImage shot = shoot(frame);

            for (int at = 0; at < SIZE; at++)
            {
                assertEquals(shot.getRGB(at, 0), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the top edge at " + at);

                assertEquals(shot.getRGB(at, SIZE - 1), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the bottom edge at " + at);

                assertEquals(shot.getRGB(0, at), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the left edge at " + at);

                assertEquals(shot.getRGB(SIZE - 1, at), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the right edge at " + at);
            }
        }
    }

    /**
     * The window a dialog is placed against is the window itself, not what contains it (MT-182).
     *
     * **This is the test the two below could not be.** They check `besideOwner`, which is arithmetic on
     * two rectangles and has always been right. Its javadoc says it was made public and static so the
     * one thing worth checking could be checked without opening a modal dialog, and ends: "there is
     * nothing left in the caller to get wrong except which rectangles it passes."
     *
     * That is exactly what the caller got wrong, twice reported and twice not found. `owner()` returns
     * a WINDOW when the panel is in one; the caller asked `SwingUtilities.getWindowAncestor(owner())`,
     * which walks UP from what it is given, and a top-level frame has no window ancestor. So it got
     * null every time, took its fallback branch - `setLocationRidiculouslyRelativeTo(owner())`, which
     * centres - and produced the precise behaviour the ticket was raised about, while both tests of
     * the rule stayed green.
     *
     * Driven with real components, because the mistake is only visible in what `getWindowAncestor`
     * answers for a window as against a panel. No dialog is shown and nothing blocks.
     */
    @Test
    public void testAWindowIsNotAskedWhatContainsIt() throws Exception
    {
        // A frame is built but never shown, so this needs a graphics environment.
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("building a frame needs a display");
        }

        final javax.swing.JFrame frame = new javax.swing.JFrame("MT-182");
        final javax.swing.JPanel inside = new javax.swing.JPanel();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setSize(1200, 800);
                frame.add(inside);
            });

            // The mistake, stated as the platform states it.
            assertNull(javax.swing.SwingUtilities.getWindowAncestor(frame),
                "precondition: a top-level frame reports a window ancestor, so this whole class of "
                + "mistake would not arise and this test is about nothing");

            assertEquals(javax.swing.SwingUtilities.getWindowAncestor(inside), frame,
                "precondition: a panel inside the frame does not report it, so the fixture is wrong");

            // And what the panel's own helper must answer for each.
            String panelSource = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/org/traincontrol/gui/AutonomyEditorPanel.java")),
                java.nio.charset.StandardCharsets.UTF_8);

            // CODE ONLY. The first version of this read the whole file and matched the sentence in
            // the fix's own javadoc describing the mistake - so the test failed against the corrected
            // code, on the strength of prose explaining the correction.
            StringBuilder code = new StringBuilder();

            for (String line : panelSource.split("\n"))
            {
                String trimmed = line.trim();

                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*"))
                {
                    continue;
                }

                code.append(line).append('\n');
            }

            panelSource = code.toString();

            assertTrue(panelSource.contains("if (anchor instanceof java.awt.Window) return"),
                "the panel asks getWindowAncestor for something that may already BE the window, so "
                + "it gets null and the signal dialog falls back to centring - which is MT-182");

            assertFalse(panelSource.contains("getWindowAncestor(owner())"),
                "a caller still asks what contains owner(), and owner() is already the window - so "
                + "that caller gets null. One of the two centres the dialog; the other leaves it "
                + "with no owner at all");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    /**
     * The signal window does not open over the middle of the diagram it is describing (OB-107).
     *
     * Adam: "the signal protecting this station pops up over the middle of the diagram. see if you can
     * offset it." The window's own text tells the reader the signals are outlined on the diagram
     * BEHIND it, so centring hides the thing it is pointing at.
     *
     * MUTATION: making besideOwner return the centred point - the behaviour this replaced - fails
     * this test.
     */
    @Test
    public void testTheSignalWindowDoesNotSitOnTheDiagram()
    {
        Rectangle window = new Rectangle(100, 80, 1400, 900);
        Dimension dialog = new Dimension(420, 300);

        Point at = AutonomyEditorPanel.besideOwner(window, dialog);

        int centredX = window.x + (window.width - dialog.width) / 2;
        int centredY = window.y + (window.height - dialog.height) / 2;

        assertTrue(Math.abs(at.x - centredX) > dialog.width / 2
            || Math.abs(at.y - centredY) > dialog.height / 2,
            "the dialog is still effectively centred, at " + at);

        // And it is still ON the window it belongs to, which is what stops "not centred" being
        // answered by throwing it into a corner of the desktop.
        assertTrue(window.contains(new Rectangle(at.x, at.y, dialog.width, dialog.height)),
            "the dialog landed outside the window it belongs to, at " + at);
    }

    /**
     * A dialog bigger than the window it belongs to still starts on-screen.
     *
     * The clamp has two ends and only one of them is exercised by the ordinary case. Pushed by the
     * "keep it inside" rule alone, a dialog wider than its parent would be moved to a NEGATIVE offset
     * from the parent's left edge - which on a maximised window is off the side of the display, where
     * the buttons cannot be reached.
     */
    @Test
    public void testAnOversizedDialogStillStartsOnScreen()
    {
        Rectangle window = new Rectangle(60, 40, 300, 200);
        Dimension dialog = new Dimension(500, 400);

        Point at = AutonomyEditorPanel.besideOwner(window, dialog);

        assertTrue(at.x >= window.x, "the dialog starts left of its window, at x=" + at.x);
        assertTrue(at.y >= window.y, "the dialog starts above its window, at y=" + at.y);
    }

    /**
     * The frame is taken from the clock, so a coalesced tick lands where it should (OB-129).
     *
     * Adam: "track loading hourglass does not animate."
     *
     * `javax.swing.Timer` fires on the event thread and COALESCES when that thread is busy - and this
     * component exists to be on screen while a diagram build floods it. Counting one frame per tick
     * therefore advanced the sand once for a second's worth of ticks. Taking the frame from elapsed
     * time makes a late tick land on the frame it should have reached.
     *
     * MUTATION: going back to a counter fails the third assertion, which is the whole point - the
     * first two pass either way.
     */
    @Test
    public void testTheFrameComesFromTheClock() throws Exception
    {
        assertEquals(LoadingSpinner.frameAt(0), 0, "the animation does not start at the beginning");

        assertEquals(LoadingSpinner.frameAt(60), 1,
            "one frame period does not advance exactly one frame");

        // THE CASE THAT MATTERS. Ten frames' worth of time passes while the event thread is busy and
        // the timer manages a single tick; that tick must land on frame 10, not frame 1.
        assertEquals(LoadingSpinner.frameAt(600), 10,
            "600ms after the last frame the animation is still one frame along, so a busy event "
            + "thread - which is exactly when this component is shown - freezes the sand");

        // It wraps rather than running off the end.
        assertTrue(LoadingSpinner.frameAt(1000L * 60 * 60) >= 0,
            "an hour on screen puts the animation on a frame that does not exist");

        assertEquals(LoadingSpinner.frameAt(-5), 0, "a clock that went backwards is not handled");
    }

    /**
     * And the timer really does drive it, with nobody calling advanceOneFrame.
     *
     * The gap that let OB-129 ship: `shoot` advances the frames by hand, so every drawing test above
     * passes with a timer that never fires once.
     */
    @Test(timeOut = 30000)
    public void testTheTimerActuallyRuns() throws Exception
    {
        final javax.swing.JFrame window = new javax.swing.JFrame();
        final LoadingSpinner mark = new LoadingSpinner();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                window.getContentPane().add(mark);
                window.setSize(200, 200);
                window.setVisible(true);
            });

            int started = mark.currentFrame();

            // Long enough for several frames at 60ms, without making the suite wait.
            Thread.sleep(500);

            final int[] now = new int[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> now[0] = mark.currentFrame());

            assertTrue(now[0] != started,
                "the wait mark is on frame " + now[0] + " half a second after it was on frame "
                + started + " - nothing is driving it, so it is a still picture on screen");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * The glass has a ceiling, whatever room it is given (OB-129).
     *
     * Adam asked for it half the size. The component is now sized to the whole diagram it covers, so a
     * fraction of that would make the glass grow rather than shrink.
     */
    @Test
    public void testTheGlassDoesNotGrowWithTheSpaceForever() throws Exception
    {
        int big = 900;

        LoadingSpinner mark = new LoadingSpinner();
        mark.setSize(big, big);

        java.awt.image.BufferedImage out =
            new java.awt.image.BufferedImage(big, big, java.awt.image.BufferedImage.TYPE_INT_RGB);

        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, big, big);
        mark.paint(g);
        g.dispose();

        int top = -1;
        int bottom = -1;

        for (int y = 0; y < big; y++)
        {
            for (int x = 0; x < big; x++)
            {
                if ((out.getRGB(x, y) & 0xFFFFFF) < 0xF0F0F0)
                {
                    if (top < 0) top = y;

                    bottom = y;
                    break;
                }
            }
        }

        assertTrue(top >= 0, "nothing was drawn at all, so the measurement below means nothing");

        int drawn = bottom - top + 1;

        assertTrue(drawn <= 160,
            "the glass is " + drawn + " pixels tall in a 900px panel - it grows with the space it is "
            + "given, so covering a whole track diagram draws an enormous hourglass");

        // And it is still centred in that space, which is the other half of OB-129.
        int middle = (top + bottom) / 2;

        assertTrue(Math.abs(middle - big / 2) <= 4,
            "the glass sits at " + middle + " in a " + big + "px panel rather than the middle");
    }

    /**
     * The spinner covers the area the diagram will take, which is what puts it in the middle.
     *
     * It is centred inside its own component already; what was wrong is how much of the page that
     * component covered. Capped at 400 and dropped into a FlowLayout parent, it sat at the top.
     */
    @Test
    public void testTheSpinnerCoversTheDiagramArea() throws Exception
    {
        String grid = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/LayoutGrid.java")), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(grid.contains("spinner.setPreferredSize(new Dimension(maxWidth, maxHeight))"),
            "the wait mark is no longer sized to the whole area the diagram will take, so on a page "
            + "bigger than it, the FlowLayout parent leaves it sitting at the top instead of over "
            + "the middle (OB-129)");
    }

    /**
     * A blocked event thread does not freeze the sand (OB-129).
     *
     * This is the reported fault reproduced rather than described. `javax.swing.Timer` fires on the
     * event thread and coalesces, so while a diagram build holds that thread the ticks arrive as one -
     * and a counter advances one frame for half a second of waiting, which is what Adam saw.
     *
     * The frame must therefore come back having moved by roughly the time that passed, not by one.
     *
     * MUTATION: `frame = (frame + 1) % ...` in the timer passes every other test in this class and
     * fails this one - which is the gap that let OB-129 ship, since `frameAt` was correct all along
     * and nothing asserted the timer used it.
     */
    @Test(timeOut = 30000)
    public void testABlockedEventThreadDoesNotFreezeTheSand() throws Exception
    {
        final javax.swing.JFrame window = new javax.swing.JFrame();
        final LoadingSpinner mark = new LoadingSpinner();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                window.getContentPane().add(mark);
                window.setSize(200, 200);
                window.setVisible(true);
            });

            final int[] before = new int[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> before[0] = mark.currentFrame());

            // The event thread held, exactly as a diagram build holds it.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException stop)
                {
                    Thread.currentThread().interrupt();
                }
            });

            final int[] after = new int[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> after[0] = mark.currentFrame());

            int moved = Math.floorMod(after[0] - before[0], 124);

            // 500ms is a little over eight frames at 60ms. A counter would manage one or two.
            assertTrue(moved >= 5,
                "the wait mark advanced " + moved + " frames while the event thread was held for "
                + "500ms - it is counting ticks rather than reading the clock, so during the very "
                + "thing it exists to cover the sand barely moves (OB-129)");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * The drain is drawn UPRIGHT in every cycle, not only the first (VAL-B4, 2026-08-29).
     *
     * Adam, 2026-08-29: "the hourglass flows backwards after the flip." The fix in the tree today
     * never rotates the drain at all - only the turn does - so a mid-drain frame in the second cycle
     * has to render EXACTLY like the same point in the first, not merely fall in the same direction.
     *
     * This replaces what was here before this review (VAL-B4): a check that shot frames deep into the
     * second cycle and asked whether sand still fell downward. That is not the property that
     * distinguishes a correct drain from a rotated one - a 180-degree rotation flips top and bottom
     * AND reverses the fall, so the two inversions cancel and "sand ends up at the bottom" stays true
     * either way. Comparing the actual pixels does not have that blind spot: a mid-drain frame that is
     * rotated at all lands its ink on different pixels than the unrotated one, regardless of which way
     * the rotation goes.
     *
     * MUTATION: reintroducing a `turns`-dependent rotation into the drain branch - so the second
     * cycle's drain turns with the glass instead of staying upright - fails this: frame 25 of cycle two
     * would be a rotated copy of frame 25 of cycle one, not an identical one, and far more than
     * antialiasing's worth of pixels would differ.
     */
    @Test
    public void testItRunsDownwardsAfterTheFlipToo() throws Exception
    {
        // Mid-drain, past the antialiased edges of "just started" or "just finished" - the same frame
        // VAL-D4 measured by sand mass to confirm the two cycles agree.
        int midDrain = 25;

        BufferedImage firstCycle = shoot(midDrain);
        BufferedImage secondCycle = shoot(midDrain + CYCLE);

        int different = 0;

        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                if (firstCycle.getRGB(x, y) != secondCycle.getRGB(x, y)) different++;
            }
        }

        // A few pixels may differ from antialiasing; a rotated drain differs by far more than that.
        assertTrue(different < 60,
            "frame " + midDrain + " and frame " + (midDrain + CYCLE) + " are the same point in "
            + "successive cycles and should render identically - " + different + " pixels differ, "
            + "which is what a drain rotated with the glass looks like (the backwards-flow report)");
    }

    /**
     * The turn is seamless: the frame after it looks like the frame before it.
     *
     * This is what the class actually claims - "an emptied hourglass turned through half a circle is
     * pixel for pixel a full one" - and it is a statement about the two ENDPOINTS, not about every
     * frame. Mid-fall the sand is a cone in the lower bulb and a funnel in the upper one, which are
     * not each other rotated, so a whole-cycle comparison would be asking for something the drawing
     * has never promised.
     *
     * The seam is where the bug showed. Before the fix the drain after a turn began at drained = 0,
     * which under the rotation reads as an EMPTY top - so the glass snapped from full-at-top back to
     * empty-at-top the instant the turn finished, and then ran backwards.
     *
     * VAL-B4 (2026-08-29): the drain no longer rotates at all, so "putting the rotation back into the
     * drain" is not a live risk here any more - that mutation is caught by
     * `testItRunsDownwardsAfterTheFlipToo` instead. What this test still guards, on the code as it
     * stands, is the TURN's own arithmetic reaching a full half-turn by its last frame.
     *
     * MUTATION: dropping the `+ 1` from `(at - DRAIN_FRAMES + 1) / TURN_FRAMES` in the turn branch
     * stops the rotation eleven twelfths of the way around instead of all the way - the last turn
     * frame is then a full hourglass tilted just short of upside down, not a full hourglass rotated
     * exactly to look empty-at-top, and the seam comparison fails by far more than antialiasing.
     */
    @Test
    public void testTheTurnIsSeamless() throws Exception
    {
        // The last frame of each turn, and the first frame of the drain that follows it.
        for (int[] seam : new int[][] { { CYCLE - 1, CYCLE }, { CYCLE * 2 - 1, 0 } })
        {
            BufferedImage before = shoot(seam[0]);
            BufferedImage after = shoot(seam[1]);

            int different = 0;

            for (int y = 0; y < SIZE; y++)
            {
                for (int x = 0; x < SIZE; x++)
                {
                    if (before.getRGB(x, y) != after.getRGB(x, y)) different++;
                }
            }

            // A few pixels may differ from antialiasing on the rotated path.
            assertTrue(different < 60,
                "frame " + seam[0] + " and frame " + seam[1] + " are the two sides of a turn and "
                + "should look the same, or the loop visibly jumps - " + different + " pixels differ");
        }
    }
    /**
     * The start-up splash never takes the foreground (OB-170, sixth pass).
     *
     * Adam: **"2.8.1 works fine, the keyboard is focused on startup.  no message on 3.0.0."**  That
     * makes it a regression, and the start-up path has exactly one window in 3.0.0 that 2.8.1 does not
     * have.
     *
     * **Windows gives a process one chance to put a window in the foreground when the user starts it,
     * and showing a top-level window spends it.**  A splash that is always-on-top, up for the whole of
     * the connect and then destroyed spends that right and hands the foreground back to whatever was
     * there before - the application we were launched from.  Which is what he reported four times:
     * "the previous active application window retains focus".
     *
     * He asked about the splash on the very first report and was told it could not be the cause,
     * because it closes before the window is shown.  That answered a question about ORDERING, and the
     * cost is not in the order.
     *
     * Two properties, because they are two questions: `isFocusableWindow` is the platform's
     * no-activate window style, and `getAutoRequestFocus` is whether showing it asks for activation.
     * A splash wants neither - it cannot be typed into and it cannot be clicked.
     *
     * MUTATION this catches: removing either line from `StartupSplash.show`.
     */
    @Test(timeOut = 60000)
    public void testTheSplashNeverTakesTheForeground() throws Exception
    {
        org.traincontrol.gui.StartupSplash splash = org.traincontrol.gui.StartupSplash.show("probe");

        assertNotNull(splash, "no splash was built, so this proves nothing about the one that is");

        try
        {
            java.lang.reflect.Field held =
                org.traincontrol.gui.StartupSplash.class.getDeclaredField("window");
            held.setAccessible(true);

            javax.swing.JWindow window = (javax.swing.JWindow) held.get(splash);

            assertNotNull(window, "the splash has no window");

            assertFalse(window.isFocusableWindow(),
                "the splash can be focused, so the platform may activate it - and a window that takes "
                + "the foreground during start-up spends the one chance the main window needs");

            assertFalse(window.isAutoRequestFocus(),
                "the splash asks for activation when it is shown, which is the same right spent a "
                + "different way");
        }
        finally
        {
            org.traincontrol.gui.StartupSplash.closeIfShown(splash);
        }
    }

}
