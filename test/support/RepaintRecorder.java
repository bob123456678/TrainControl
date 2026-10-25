package support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/**
 * The repaints production asks Swing for, written down, and a picture redrawn ONLY where they were asked (FR-136, issue 19).
 *
 * **Why this exists.**  A tile decides what it draws when it is painted, so a test that paints a tile and looks at it sees
 * the right answer whether or not anything asked for that tile to be redrawn - and a window on screen redraws only what it
 * is asked to.  OB-180 lived exactly there: `CoveredTrackView.workOutCoveredTrack` worked the covered set out correctly and
 * nothing told the tiles, so the old squares stayed orange.  Taking out its `repaintTheWashWhereItChanged` call left
 * `ui.testTheShadingIsRedrawnWhenATrainMoves` and `core.testTheShadingFollowsTheTrain` green, because each painted the whole
 * grid or the label itself and so did the repaint it was there to check.
 *
 * So this is installed as the `RepaintManager` and records every `repaint()` before passing it on - before, so what is
 * counted is what production asked for rather than what Swing then chose to act on (a component that is not on screen is
 * dropped by Swing, and every component a test builds is not on screen).  `redrawWhereAsked` then plays those requests onto
 * a picture taken earlier, the way the screen would: what was asked for is painted again, and everything else keeps the
 * pixels it had.
 *
 * Swing's own manager is put back by `close`.
 *
 * @author Adam
 */
public final class RepaintRecorder extends RepaintManager implements AutoCloseable
{
    private final RepaintManager was;

    /** Each component asked to repaint since the last `clear`, with the union of the areas asked for, in its own coordinates. */
    private final Map<JComponent, Rectangle> asked = new LinkedHashMap<>();

    private RepaintRecorder(RepaintManager was)
    {
        this.was = was;
    }

    /**
     * Installs a recorder as Swing's repaint manager, on the event thread.
     *
     * @return the recorder; close it to put Swing's own manager back
     * @throws Exception if the event thread cannot be reached
     */
    public static RepaintRecorder install() throws Exception
    {
        final RepaintRecorder[] made = new RepaintRecorder[1];

        SwingUtilities.invokeAndWait(() ->
        {
            made[0] = new RepaintRecorder(RepaintManager.currentManager((java.awt.Component) null));

            RepaintManager.setCurrentManager(made[0]);
        });

        return made[0];
    }

    @Override
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h)
    {
        if (c != null)
        {
            synchronized (asked)
            {
                Rectangle area = new Rectangle(x, y, Math.max(0, w), Math.max(0, h));

                Rectangle before = asked.get(c);

                asked.put(c, before == null ? area : before.union(area));
            }
        }

        super.addDirtyRegion(c, x, y, w, h);
    }

    /** Forgets what has been asked so far, for a picture taken now. */
    public void clear()
    {
        synchronized (asked)
        {
            asked.clear();
        }
    }

    /**
     * The components asked to repaint since the last `clear`.
     *
     * @return them, in the order first asked
     */
    public Set<JComponent> dirtied()
    {
        synchronized (asked)
        {
            return new LinkedHashSet<>(asked.keySet());
        }
    }

    /**
     * What the screen shows now, given that it showed `shown`: the areas of `host` and of its descendants that were asked
     * to repaint since the last `clear` are painted again, on white as `Rendered.snapshot` paints them, and every other
     * pixel is the one it was.  Clears what was asked, so the next call starts from this picture.
     *
     * @param shown the picture the screen showed when the recording was last cleared, the size of `host`; not changed
     * @param host the component the picture is of
     * @return the picture now
     * @throws Exception if the event thread cannot be reached
     */
    public BufferedImage redrawWhereAsked(final BufferedImage shown, final JComponent host) throws Exception
    {
        final Map<JComponent, Rectangle> now;

        synchronized (asked)
        {
            now = new LinkedHashMap<>(asked);

            asked.clear();
        }

        final BufferedImage out = new BufferedImage(shown.getWidth(), shown.getHeight(), BufferedImage.TYPE_INT_RGB);

        SwingUtilities.invokeAndWait(() ->
        {
            Graphics2D g = out.createGraphics();

            try
            {
                g.drawImage(shown, 0, 0, null);

                for (Map.Entry<JComponent, Rectangle> each : now.entrySet())
                {
                    JComponent c = each.getKey();

                    if (c != host && !SwingUtilities.isDescendingFrom(c, host)) continue;

                    Rectangle area = SwingUtilities.convertRectangle(c, each.getValue(), host);

                    if (area.isEmpty()) continue;

                    Graphics2D clipped = (Graphics2D) g.create();

                    try
                    {
                        clipped.clip(area);
                        clipped.setColor(Color.WHITE);
                        clipped.fill(area);

                        host.paint(clipped);
                    }
                    finally
                    {
                        clipped.dispose();
                    }
                }
            }
            finally
            {
                g.dispose();
            }
        });

        return out;
    }

    /**
     * Puts Swing's own repaint manager back.
     *
     * @throws Exception if the event thread cannot be reached
     */
    @Override
    public void close() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(was));
    }
}
