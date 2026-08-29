package org.traincontrol.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The "still drawing" mark shown in place of a track diagram while its tiles are being decoded.
 *
 * A diagram used to appear in two stages: its text labels at once, because text needs no image, and
 * the track a second or so later as the decodes came back off the pool.  The order is an accident of
 * how the work is split, but it reads as the diagram being wrong and then correcting itself - the
 * labels look like they are floating on nothing.  Showing one thing that says "not yet" is honest
 * about the same wait.
 *
 * An hourglass rather than a turning arc (FR-024).  Adam: "change the very large spinner shown on top
 * of loading track diagrams to a large gray hourglass icon instead.  animate if possible."  An arc
 * says only "busy"; sand running through says how far along the wait is even though neither of them
 * actually knows, and at the size this is drawn over a full diagram the arc read as a target sitting
 * on the page rather than as a wait.
 *
 * Drawn rather than loaded from a GIF, because an animation file is one more asset to ship, scale for
 * a high-DPI screen and keep in step with the theme.  Four curves and a timer are fewer moving
 * parts.
 *
 * The turn at the end of each cycle is free: an emptied hourglass turned through half a circle is
 * pixel for pixel a full one, so there is no frame where the sand jumps back up the glass. (VAL-C3:
 * that used to describe the whole loop, drawn by rotating rather than by resetting; as of 2026-08-29
 * only the turn itself rotates and the drain is always drawn upright - see the halfTurns comment in
 * paintComponent - so "loops by rotating" is now true of the twelve turn frames only.)
 *
 * The timer is started and stopped with the component's visibility on screen, so one left in a window
 * nobody is looking at is not repainting a hidden pixel sixteen times a second.
 */
public class LoadingSpinner extends JPanel
{
    /**
     * How often the sand advances.  About sixteen steps a second is smooth enough to read as motion
     * and slow enough that it costs nothing; a wait indicator is not something anybody studies.
     */
    private static final int FRAME_MS = 60;

    /** Frames the sand takes to run through - about three seconds. */
    private static final int DRAIN_FRAMES = 50;

    /** Frames the turn takes - fast enough to read as a flick of the wrist rather than a spin. */
    private static final int TURN_FRAMES = 12;

    private static final int CYCLE_FRAMES = DRAIN_FRAMES + TURN_FRAMES;

    /** The glass itself. */
    private static final Color FRAME_GREY = new Color(120, 120, 120);

    /** The sand, a shade lighter so it reads as a fill inside the outline rather than as more line. */
    private static final Color SAND_GREY = new Color(158, 158, 158);

    /**
     * How wide the glass is as a fraction of its height.
     *
     * Half, and it wants to be no more.  Wider than that and the bulbs read as a bow tie however
     * they are drawn - which is what the first attempt at this looked like on the diagram.  The
     * curved profile does most of the work; this stops the proportions undoing it.
     */
    private static final double ASPECT = 0.5;

    /** How much of the space offered the glass actually takes, leaving it room to turn in. */
    private static final double FILL = 0.68;

    /**
     * The tallest the glass is ever drawn, whatever it is given room for (OB-129).
     *
     * Adam asked for it half the size. It used to be a fraction of a component capped at 400, so about
     * 272 tall; the component is now the size of the diagram it covers, which would make a fraction
     * LARGER rather than smaller. A ceiling says the size it should be regardless of the space.
     */
    private static final double MAX_GLASS_H = 136.0;

    private final Timer timer;

    /**
     * When the animation started, for working out which frame is due.
     *
     * The frame used to be a counter incremented once per tick, which is wrong for a Swing timer: it
     * fires on the event thread and coalesces when that thread is busy - and the whole purpose of this
     * component is to be on screen while a diagram build floods it. A second of ticks arrived as one
     * and the sand crawled, which is what OB-129 reports.
     */
    private long startedAt;

    /**
     * Frames since the animation started, modulo two whole cycles.
     *
     * VAL-C3: two whole cycles used to be load-bearing, because the drawing alternated between upright
     * and inverted and one cycle left it standing on its head. As of 2026-08-29 the drain is always
     * drawn upright and only the twelve turn frames rotate (see the halfTurns comment in
     * paintComponent), so every frame `n` now renders identically to `n + CYCLE_FRAMES` and the true
     * period is one cycle, not two. `frameAt` and `advanceOneFrame` still wrap at `CYCLE_FRAMES * 2`
     * regardless - harmless, since the second cycle is a silent repeat of the first, but the field is
     * twice the size it needs to be for what it now draws.
     */
    private int frame;

    public LoadingSpinner()
    {
        setOpaque(false);

        // Matches the diagram's own background so the swap to the finished grid is not also a flash of
        // a different colour
        setBackground(Color.WHITE);

        timer = new Timer(FRAME_MS, e ->
        {
            // FROM THE CLOCK, not from the number of times this has run (OB-129).
            frame = frameAt(System.currentTimeMillis() - startedAt);
            repaint();
        });

        timer.setRepeats(true);
    }

    /**
     * Which frame is due after a given time on screen.
     *
     * Static and taking the elapsed time so the clock can be checked without waiting for one: every
     * existing test of this class drives `advanceOneFrame` by hand, which is exactly why a broken
     * timer went unnoticed.
     *
     * @param elapsedMs milliseconds since the animation started
     * @return the frame to draw, within the two cycles the drawing alternates over
     */
    public static int frameAt(long elapsedMs)
    {
        if (elapsedMs < 0) return 0;

        return (int) ((elapsedMs / FRAME_MS) % (CYCLE_FRAMES * 2));
    }

    @Override
    public void addNotify()
    {
        super.addNotify();

        // Restarted from now, so a spinner shown a second time does not jump to wherever the clock
        // had got to while it was off screen.
        startedAt = System.currentTimeMillis();
        frame = 0;

        timer.start();
    }

    @Override
    public void removeNotify()
    {
        timer.stop();
        super.removeNotify();
    }

    /**
     * A default only, and only when nobody has said otherwise.
     *
     * This used to return 120x120 unconditionally, which quietly overrode all three callers (DOC-C24:
     * there are now three, not two - LayoutGrid.showWhenTilesAreReady, BusyDialog, and StartupSplash):
     * the grid asks for the space the diagram is about to take so that nothing jumps when the two are
     * swapped, the busy dialog asks for something small enough to sit above a line of text, and the
     * splash asks for its own fixed size. None of them got what they asked for, and the grid's comment
     * described a behaviour that could not happen.
     */
    @Override
    public Dimension getPreferredSize()
    {
        return isPreferredSizeSet() ? super.getPreferredSize() : new Dimension(120, 120);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int at = frame % CYCLE_FRAMES;
            // `turns` no longer reaches the drawing - see below.  int turns = frame / CYCLE_FRAMES;

            // How much of the sand has run through, and how far round the glass has been turned.  The
            // sand is all the way through before the turn starts, which is what makes the turn
            // invisible.
            double drained;

            // NOT `turns` - the drain is always drawn upright (Adam, 2026-08-29).
            //
            // The sand is anchored at the bottom of each region: the upper bulb fills from its level
            // DOWN to the waist, the lower from its level DOWN to the plate. Rotating that mid-fall
            // inverts the anchors, so the upper sand hangs from the top of the glass with a gap above
            // the waist and the pile in the lower bulb floats - which is what "weird in how it
            // empties" was.
            //
            // The rotation is for the TURN, and only there does it have to agree with an upright
            // glass. At the endpoints it does, exactly: a full lower bulb [0, bulbH] rotated is
            // [-bulbH, 0], which is a full upper bulb. So the seam is pixel for pixel and the drain
            // never leaves its feet.
            double halfTurns = 0;

            if (at < DRAIN_FRAMES)
            {
                drained = at / (double) DRAIN_FRAMES;
            }
            else
            {
                drained = 1.0;
                halfTurns += (at - DRAIN_FRAMES + 1) / (double) TURN_FRAMES;
            }

            // WK-C3: centred on the VISIBLE part of this component, not the whole of it. This
            // component is sized to the whole diagram page (LayoutGrid.showWhenTilesAreReady), which
            // on a page bigger than the LayoutArea viewport put the glass's true centre off whatever
            // screenful the operator happens to be scrolled to - the "blank page" symptom OB-129 was
            // filed for, arrived at by a different route. getVisibleRect() is the standard way to ask
            // a component what part of itself is actually on screen inside a JScrollPane; it degrades
            // to the full bounds outside one, and the empty-rect guard covers the moment before this
            // component has ever been laid out.
            java.awt.Rectangle visible = getVisibleRect();

            if (visible.isEmpty())
            {
                visible = new java.awt.Rectangle(0, 0, getWidth(), getHeight());
            }

            // Sized off whichever way round the visible space is tighter, so it never overhangs.
            double glassH = Math.max(18.0,
                Math.min(MAX_GLASS_H, Math.min(visible.width / ASPECT, visible.height) * FILL));
            double glassW = glassH * ASPECT;

            g2.translate(visible.getCenterX(), visible.getCenterY());
            g2.rotate(Math.PI * halfTurns);

            drawHourglass(g2, glassW, glassH, drained);
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * Draws the glass and its sand, centred on the origin of the transform it is given.
     *
     * Kept apart from the frame arithmetic above so the shape can be reasoned about on its own:
     * everything here is a function of the three numbers passed in, with no reference to what frame it
     * is or which way up the glass has been turned.
     *
     * @param g2 a graphics already translated to the centre and rotated to the current turn
     * @param glassW the width across the plates
     * @param glassH the height from plate to plate
     * @param drained how much of the sand has run through, 0 for none and 1 for all of it
     */
    private void drawHourglass(Graphics2D g2, double glassW, double glassH, double drained)
    {
        double halfW = glassW / 2.0;
        double halfH = glassH / 2.0;

        // The plates the glass is held between.
        double cap = Math.max(2.0, glassH * 0.05);

        double bulbH = halfH - cap;

        float line = (float) Math.max(1.5, glassW * 0.05);

        // --- the silhouette --------------------------------------------------------------------------
        //
        // Curved sides rather than two triangles.  A triangle pair meeting at a point is equally a bow
        // tie, and on a diagram that is how the first attempt at this read; the pinch just below each
        // plate is the whole of what makes it an hourglass instead.  Adding an outer frame was the
        // other way to settle it, and drew a rectangle with an X in it - worse.
        //
        // One closed path, anticlockwise from the top left, so the same shape both outlines the glass
        // and bounds the sand below.  Control points sit high and wide, which keeps the curve close to
        // the plate before it turns in hard toward the waist.
        Path2D.Double glass = new Path2D.Double();

        glass.moveTo(-halfW, -bulbH);
        glass.quadTo(-halfW * 0.78, -bulbH * 0.12, 0, 0);
        glass.quadTo(-halfW * 0.78, bulbH * 0.12, -halfW, bulbH);
        glass.lineTo(halfW, bulbH);
        glass.quadTo(halfW * 0.78, bulbH * 0.12, 0, 0);
        glass.quadTo(halfW * 0.78, -bulbH * 0.12, halfW, -bulbH);
        glass.closePath();

        // --- the sand, drawn first so the outline stays crisp over it -------------------------------
        //
        // Cut out of the silhouette rather than drawn as its own shape: a level is a horizontal line
        // across the glass, whatever the glass happens to be shaped like, and intersecting says that
        // once instead of restating the profile in a second place that then has to be kept in step.
        //
        // The level itself goes as the square root of what is left, because the bulb narrows toward
        // the waist - so the surface drops fast at first and creeps at the end, which is what real
        // sand does and what a straight interpolation gets visibly wrong.
        double remaining = Math.sqrt(Math.max(0.0, 1.0 - drained));

        Area inside = new Area(glass);

        g2.setColor(SAND_GREY);

        // Upper bulb: what has not fallen rests on the waist.
        if (remaining > 0.01)
        {
            Area upper = new Area(new Rectangle2D.Double(-halfW, -bulbH * remaining,
                glassW, bulbH * remaining));

            upper.intersect(inside);

            g2.fill(upper);
        }

        // Lower bulb: what has fallen piles up from the plate, so the EMPTY part is the top of it.
        if (drained > 0.01)
        {
            Area lower = new Area(new Rectangle2D.Double(-halfW, bulbH * remaining,
                glassW, bulbH * (1.0 - remaining)));

            lower.intersect(inside);

            g2.fill(lower);
        }

        // The falling stream, between the waist and whatever it is landing on.  Only while there is
        // something to fall: a thread of sand under an empty glass is the detail that gives it away.
        if (drained > 0.02 && drained < 0.99)
        {
            g2.setStroke(new BasicStroke((float) Math.max(1.2, halfW * 0.09), BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND));

            g2.draw(new Line2D.Double(0, 0, 0, bulbH * remaining));
        }

        // --- the glass ------------------------------------------------------------------------------
        g2.setColor(FRAME_GREY);
        g2.setStroke(new BasicStroke(line, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g2.draw(glass);

        // The plates, filled rather than stroked so they read as the solid ends of the thing.
        g2.fill(new Rectangle2D.Double(-halfW - line / 2, -halfH, glassW + line, cap));
        g2.fill(new Rectangle2D.Double(-halfW - line / 2, halfH - cap, glassW + line, cap));
    }

    /**
     * Whether the sand is running, for a test that wants to know the animation is live rather than a
     * still picture.
     *
     * @return true while the timer is advancing frames
     */
    public boolean isAnimating()
    {
        return timer.isRunning();
    }

    /**
     * Advances the animation by one frame without waiting for the timer.
     *
     * Public so that a test can step the cycle and photograph it. Waiting on the real timer would mean
     * a test that sleeps, and one that is a race on a slow machine; stepping is the same arithmetic
     * with the clock taken out of it.
     *
     * @return the frame counter afterwards, which advances and wraps rather than growing
     */
    public int advanceOneFrame()
    {
        frame = (frame + 1) % (CYCLE_FRAMES * 2);

        return frame;
    }

    /**
     * Which frame is showing.
     *
     * So a test can watch the TIMER move it, rather than moving it itself - which is what every test
     * of this class did, and why a clock that never ticked went unnoticed (OB-129).
     *
     * @return the current frame
     */
    public int currentFrame()
    {
        return this.frame;
    }
}
