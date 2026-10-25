package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.widgets.LoadingSpinner;
import support.SourceOf;

/**
 * What the "still drawing" mark draws, and where the signal window goes (FR-024, OB-107).
 *
 * Checked by looking: the drawing is rendered to an image and measured, the placement is a function of two
 * rectangles.  Not a source-reading guard, because a bow-tie silhouette mentions nothing wrong in code.
 *
 * The load-bearing assertion is the sand count: what leaves the upper bulb must arrive in the lower one.
 *
 * @author Adam
 */
public class testTheWaitMarkIsAnHourglass
{

    /** The application's look and feel; a bare Swing window would be drawn in Metal, not as the operator sees it. */
    @BeforeClass
    public static void theApplicationsLookAndFeel()
    {
        if (!java.awt.GraphicsEnvironment.isHeadless())
        {
            org.traincontrol.gui.TrainControlUI.installLookAndFeel();
        }
    }
    /** Frames in one drain-and-turn cycle - LoadingSpinner draws two of them per loop: asked of the spinner. */
    private static final int CYCLE = spinnerConstant("CYCLE_FRAMES");

    /** Milliseconds one frame lasts: asked of the spinner. */
    private static final int FRAME_MS = spinnerConstant("FRAME_MS");

    /** One of LoadingSpinner's private constants, read by reflection. */
    private static int spinnerConstant(String name)
    {
        try
        {
            java.lang.reflect.Field field = LoadingSpinner.class.getDeclaredField(name);

            field.setAccessible(true);

            return field.getInt(null);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("LoadingSpinner has no " + name + " to ask", e);
        }
    }

    /** Big enough that a few pixels of antialiasing either way cannot change a count materially. */
    private static final int SIZE = 240;

    /** Paints the mark `frames` frames into its cycle, offscreen, on white. */
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

    /** Counts the non-white pixels in the upper or lower half. */
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

                // Gray of any darkness; the threshold is clear of the antialiased edge.
                if (r < 230 && g < 230 && b < 230) count++;
            }
        }

        return count;
    }

    /** The mark draws something, and it is gray rather than the black the turning arc used. */
    @Test
    public void testItDrawsSomethingGrey() throws Exception
    {
        BufferedImage shot = shoot(0);

        int drawn = ink(shot, true) + ink(shot, false);

        assertTrue(drawn > 500, "the wait mark drew almost nothing: " + drawn + " pixels");

        // Not black: look at the DARKEST pixel, since black with alpha composites to gray on white.
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
     * Every other check here would still pass if the two bulbs ran on unrelated formulae.  The band is a twelfth,
     * from measurement: over a real cycle the count spreads 4.4% (outline and stream count as ink); driving the
     * lower bulb from its own level takes it to 20%.
     *
     * MUTATION: replacing the lower bulb's rectangle with one on a level of its own (`bulbH * (1.0 - drained * 0.5)`
     * for `bulbH * remaining`) fails this test.
     *
     * NOT COVERED: the square root in `remaining`.  Both bulbs read the same variable, so the plain fraction passes
     * all seven tests; how the sand LOOKS as it falls is seen by nothing here.
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

    /** It starts full at the top and ends full at the bottom; a backwards or both-bulbs animation fails here. */
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

    /** The picture changes from frame to frame: animation, not a still. */
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

    /** Nothing is drawn outside the component at any frame, including mid-turn when the rotated glass sweeps wider. */
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
     * `besideOwner` is right, but its caller asked getWindowAncestor(owner()), which walks UP and is null for a
     * top-level frame, so the centering fallback ran.  Driven with real components; no dialog is shown.
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

            // What the panel's own helper must answer for each.  CODE ONLY: the fix's javadoc describes the mistake.
            String panelSource = SourceOf.allCode();

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
     * The signal window does not open over the middle of the diagram it describes (OB-107).
     *
     * It points at the diagram behind it, so centering hides the thing it points at.
     *
     * MUTATION: making besideOwner return the centered point fails this test.
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

        // And still ON the window it belongs to, so "not centered" is not met by a corner of the desktop.
        assertTrue(window.contains(new Rectangle(at.x, at.y, dialog.width, dialog.height)),
            "the dialog landed outside the window it belongs to, at " + at);
    }

    /**
     * A dialog bigger than its window still starts on-screen: a negative offset from the left edge is off the display.
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
     * The timer coalesces when the event thread is busy; counting one frame per tick advanced the sand once for a
     * second of ticks.
     *
     * MUTATION: going back to a counter fails the third assertion; the first two pass either way.
     */
    @Test
    public void testTheFrameComesFromTheClock() throws Exception
    {
        assertEquals(LoadingSpinner.frameAt(0), 0, "the animation does not start at the beginning");

        assertEquals(LoadingSpinner.frameAt(FRAME_MS), 1,
            "one frame period does not advance exactly one frame");

        // Ten frames' worth of time passes with a single tick; it must land on frame 10, not frame 1.
        assertEquals(LoadingSpinner.frameAt(10L * FRAME_MS), 10,
            "600ms after the last frame the animation is still one frame along, so a busy event "
            + "thread - which is exactly when this component is shown - freezes the sand");

        // It wraps rather than running off the end.
        assertTrue(LoadingSpinner.frameAt(1000L * 60 * 60) >= 0,
            "an hour on screen puts the animation on a frame that does not exist");

        assertEquals(LoadingSpinner.frameAt(-5), 0, "a clock that went backwards is not handled");
    }

    /** The timer really does drive it: `shoot` advances frames by hand, so the tests above pass with a dead timer. */
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

    /** The glass has a ceiling, whatever room it is given (OB-129): the component is sized to the whole diagram. */
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

        // And it is still centered in that space.
        int middle = (top + bottom) / 2;

        assertTrue(Math.abs(middle - big / 2) <= 4,
            "the glass sits at " + middle + " in a " + big + "px panel rather than the middle");
    }

    /** The spinner covers the area the diagram will take; capped at 400 in a FlowLayout parent it sat at the top. */
    @Test
    public void testTheSpinnerCoversTheDiagramArea() throws Exception
    {
        assertTrue(SourceOf.allCode().contains("spinner.setPreferredSize(new Dimension(maxWidth, maxHeight))"),
            "the wait mark is no longer sized to the whole area the diagram will take, so on a page "
            + "bigger than it, the FlowLayout parent leaves it sitting at the top instead of over "
            + "the middle (OB-129)");
    }

    /**
     * A blocked event thread does not freeze the sand (OB-129).
     *
     * While a diagram build holds the thread the ticks coalesce, and a counter advances one frame for half a second;
     * the frame must move by roughly the time that passed.
     *
     * MUTATION: `frame = (frame + 1) % ...` in the timer passes every other test here and fails this one.
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

            int moved = Math.floorMod(after[0] - before[0], CYCLE * 2);

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
     * The drain is drawn UPRIGHT in every cycle, not only the first.
     *
     * A mid-drain frame in the second cycle must render exactly like the same point in the first.  Pixels are
     * compared because a 180-degree rotation flips top and bottom AND reverses the fall, so "sand ends at the
     * bottom" holds either way.
     *
     * MUTATION: reintroducing a `turns`-dependent rotation into the drain branch fails this: far more than
     * antialiasing's worth of pixels would differ.
     */
    @Test
    public void testItRunsDownwardsAfterTheFlipToo() throws Exception
    {
        // Mid-drain, past the antialiased edges of "just started" or "just finished".
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
     * A statement about the two ENDPOINTS only (mid-fall the cone and the funnel are not each other rotated); it
     * guards the TURN's own arithmetic reaching a full half-turn by its last frame.
     *
     * MUTATION: dropping the `+ 1` from `(at - DRAIN_FRAMES + 1) / TURN_FRAMES` in the turn branch stops the
     * rotation eleven twelfths of the way round, and the seam comparison fails by far more than antialiasing.
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
     * The start-up notice is not a window (OB-170).
     *
     * Showing a top-level window spends Windows' one chance to put a window in the foreground, and the splash cost
     * the main window its keyboard focus.  The notice goes on the main window; what must stay true is that nothing
     * here can open a window, a fact about the source and about what `panel` returns.
     *
     * MUTATION: giving `StartupSplash` a `show()` that opens a JWindow again.
     */
    @Test(timeOut = 60000)
    public void testTheStartupNoticeIsNotAWindow() throws Exception
    {
        // NOTHING IN THE CLASS OPENS ANYTHING.
        String source = SourceOf.fileDeclaring("public final class StartupSplash");

        // What a window costs to OPEN, written as the code that would do it: the class javadoc names the types.
        for (String opens : new String[]{"new JWindow(", "new JDialog(", "setVisible(true)"})
        {
            assertFalse(source.contains(opens),
                "StartupSplash can open a window again (" + opens + ") - and a second top-level window "
                + "during start-up is OB-170: it spends the one chance the process gets at the "
                + "foreground, and the main window then arrives into somebody else's");
        }

        // AND IT HAS NOWHERE TO KEEP ONE: a class with no state cannot hold a window between calls.
        assertEquals(org.traincontrol.gui.widgets.StartupSplash.class.getDeclaredFields().length, 0,
            "StartupSplash has state again, which is what a window it can close later needs");

        // AND WHAT IT HANDS OVER IS SOMETHING A WINDOW CAN WEAR.
        final javax.swing.JPanel[] built = new javax.swing.JPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            built[0] = org.traincontrol.gui.widgets.StartupSplash.panel("probe"));

        assertNotNull(built[0], "no notice was built");

        assertFalse(java.awt.Window.class.isAssignableFrom(
                org.traincontrol.gui.widgets.StartupSplash.class.getMethod("panel", String.class).getReturnType()),
            "the notice is handed over as a window again");

        assertTrue(saysAndSpins(built[0], "probe"),
            "the notice does not carry both the message and a spinner, so a slow connect shows either "
            + "a bare spinner - which says only \"wait\" - or words with nothing moving");
    }

    private boolean saysAndSpins(java.awt.Container where, String message)
    {
        boolean says = false;
        boolean spins = false;

        java.util.Deque<java.awt.Component> pending = new java.util.ArrayDeque<>();
        pending.push(where);

        while (!pending.isEmpty())
        {
            java.awt.Component c = pending.pop();

            if (c instanceof javax.swing.JLabel && message.equals(((javax.swing.JLabel) c).getText()))
            {
                says = true;
            }

            if (c instanceof org.traincontrol.gui.widgets.LoadingSpinner)
            {
                spins = true;
            }

            if (c instanceof java.awt.Container)
            {
                for (java.awt.Component child : ((java.awt.Container) c).getComponents())
                {
                    pending.push(child);
                }
            }
        }

        return says && spins;
    }
}
