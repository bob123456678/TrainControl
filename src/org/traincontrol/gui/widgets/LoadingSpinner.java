package org.traincontrol.gui.widgets;

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
 * The "still drawing" mark shown in place of a track diagram while its tiles are being decoded.  Text labels appear at
 * once and the track a second later, which reads as the diagram being wrong and then correcting itself; one thing that
 * says "not yet" is honest about the same wait.
 *
 * An hourglass rather than a turning arc (FR-024).  Adam: "change the very large spinner shown on top of loading track
 * diagrams to a large gray hourglass icon instead.  animate if possible."
 *
 * Drawn rather than loaded from a GIF, which is one more asset to ship, scale for high-DPI and keep in step with the
 * theme.  The turn at the end of each cycle is free: an emptied hourglass turned through half a circle is pixel for
 * pixel a full one, so the sand never jumps back up the glass.  Only the turn rotates; the drain is drawn upright (see
 * the halfTurns comment in paintComponent).
 *
 * The timer runs only while the component is visible on screen.
 */
public class LoadingSpinner extends JPanel
{
    /** How often the sand advances: about sixteen steps a second reads as motion and costs nothing. */
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
     * How wide the glass is as a fraction of its height.  Wider and the bulbs read as a bow tie however they are drawn;
     * the curved profile does most of the work and this stops the proportions undoing it.
     */
    private static final double ASPECT = 0.5;

    /** How much of the space offered the glass actually takes, leaving it room to turn in. */
    private static final double FILL = 0.68;

    /**
     * The tallest the glass is ever drawn, whatever it is given room for (OB-129).  The component is now the size of
     * the diagram it covers, so a fraction would grow it; a ceiling keeps the size asked for.
     */
    private static final double MAX_GLASS_H = 136.0;

    private final Timer timer;

    /**
     * When the animation started, for working out which frame is due.  A Swing timer coalesces ticks when the event
     * thread is busy, which a diagram build floods, so a per-tick counter made the sand crawl (OB-129).
     */
    private long startedAt;

    /**
     * Frames since the animation started, modulo two whole cycles.  The drain is drawn upright and only the twelve turn
     * frames rotate, so frame `n` renders like `n + CYCLE_FRAMES` and the true period is one cycle; `frameAt` and
     * `advanceOneFrame` still wrap at `CYCLE_FRAMES * 2`, harmlessly.
     */
    private int frame;

    public LoadingSpinner()
    {
        setOpaque(false);

        // Matches the diagram's own background, so the swap to the finished grid is not a flash of another color.
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
     * Which frame is due after a given time on screen.  Static and taking the elapsed time so the clock can be checked
     * without waiting for one.
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
     * A default only, and only when nobody has said otherwise: the callers (LayoutGrid.showWhenTilesAreReady,
     * BusyDialog, StartupSplash) each ask for their own size.
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

            // How much of the sand has run through, and how far round the glass has been turned.  The
            // sand is all the way through before the turn starts, which is what makes the turn
            // invisible.
            double drained;

            // The drain is always drawn upright, not rotated by `turns`: the sand is anchored at the bottom of each
            // region, so rotating mid-fall inverts the anchors and the upper sand would hang from the top of the glass
            // with a gap above the waist.  The rotation is for the TURN only, where it agrees with an upright glass at
            // the endpoints (a full lower bulb rotated is a full upper bulb), so the seam is pixel for pixel.
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

            // Centered on the VISIBLE part of this component: it is sized to the whole diagram page, and on a page
            // bigger than the viewport the glass's true center is off whatever screenful the operator is scrolled to
            // (OB-129).  getVisibleRect() degrades to the full bounds outside a JScrollPane; the empty-rect guard
            // covers the moment before the first layout.
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
     * Draws the glass and its sand, centered on the origin of the transform it is given.  Kept apart from the frame
     * arithmetic so the shape is a function of the three numbers passed in.
     *
     * @param g2 a graphics already translated to the center and rotated to the current turn
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

        // the silhouette: curved sides rather than two triangles, which read as a bow tie, and an outer frame drew a
        // rectangle with an X in it.  One closed path, anticlockwise from the top left, so the same shape outlines the
        // glass and bounds the sand.  Control points sit high and wide to stay close to the plate before turning in
        // hard.
        Path2D.Double glass = new Path2D.Double();

        glass.moveTo(-halfW, -bulbH);
        glass.quadTo(-halfW * 0.78, -bulbH * 0.12, 0, 0);
        glass.quadTo(-halfW * 0.78, bulbH * 0.12, -halfW, bulbH);
        glass.lineTo(halfW, bulbH);
        glass.quadTo(halfW * 0.78, bulbH * 0.12, 0, 0);
        glass.quadTo(halfW * 0.78, -bulbH * 0.12, halfW, -bulbH);
        glass.closePath();

        // the sand, drawn first so the outline stays crisp over it.  Cut out of the silhouette rather than drawn as its
        // own shape, so the profile is not restated in a second place.  The level goes as the square root of what is
        // left because the bulb narrows toward the waist: the surface drops fast, then creeps, as real sand does.
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

        // the glass
        g2.setColor(FRAME_GREY);
        g2.setStroke(new BasicStroke(line, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g2.draw(glass);

        // The plates, filled rather than stroked so they read as the solid ends of the thing.
        g2.fill(new Rectangle2D.Double(-halfW - line / 2, -halfH, glassW + line, cap));
        g2.fill(new Rectangle2D.Double(-halfW - line / 2, halfH - cap, glassW + line, cap));
    }

    /**
     * Whether the sand is running, for a test that wants to know the animation is live.
     *
     * @return true while the timer is advancing frames
     */
    public boolean isAnimating()
    {
        return timer.isRunning();
    }

    /**
     * Advances the animation by one frame without waiting for the timer.  Public so a test can step the cycle without
     * sleeping, which would race on a slow machine.
     *
     * @return the frame counter afterwards, which advances and wraps rather than growing
     */
    public int advanceOneFrame()
    {
        frame = (frame + 1) % (CYCLE_FRAMES * 2);

        return frame;
    }

    /**
     * Which frame is showing, so a test can watch the TIMER move it rather than moving it itself (OB-129).
     *
     * @return the current frame
     */
    public int currentFrame()
    {
        return this.frame;
    }
}
