package support;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.TrainControlUI;

/**
 * A page of the track diagram, held open, so a test can look at what is actually drawn.
 *
 * **Why this exists.** Adam, 2026-09-08: *"let's make the suite able to see rendered output when
 * needed."* Nothing in the suite did, and OB-180 is what that cost. The covered-track wash was
 * computed correctly the whole time; what was broken was that nothing REPAINTED the tiles whose state
 * had changed. Every assertion available to the suite - the covered set, the model, the source -
 * answered identically with the bug present and with it fixed.
 *
 * **Held open, and that is the whole design.** `DiagramExport.render` already renders a page offscreen,
 * and a test built on it would still not have caught OB-180: it constructs a FRESH grid every call, and
 * a fresh label always computes its wash correctly. The defect only exists for a label that is already
 * on screen and is never told to redraw.
 *
 * So this builds the grid ONCE and keeps it. `snapshot()` paints that same grid again. Between two
 * snapshots a test can move a train and call the production refresh, and the difference between the two
 * images is exactly what an operator would have seen - or not seen.
 *
 * **Not an editor.** The grid is built with a null master, so `inEditor` is false and the viewer's
 * rules apply - which is where the wash is drawn (Adam: "only the track diagram viewer").
 *
 * Needs a display: painting a component that has never been shown works offscreen, but Swing still has
 * to be there. Callers skip when `GraphicsEnvironment.isHeadless()`, the way the other rendering tests
 * in this suite do.
 *
 * @author Adam
 */
public final class Rendered
{
    /** The tile size to draw at - the native one, so nothing is scaled and no pixel is invented. */
    public static final int TILE = 30;

    private final JPanel host;

    private final LayoutGrid grid;

    private final TrainControlUI ui;

    private Rendered(JPanel host, LayoutGrid grid, TrainControlUI ui)
    {
        this.host = host;
        this.grid = grid;
        this.ui = ui;
    }

    /**
     * Builds one page's grid and keeps it.
     *
     * @param page the diagram page
     * @param ui the window, which the tiles ask about covered track and about images
     * @return the held render
     * @throws Exception if Swing cannot build it
     */
    public static Rendered open(final LayoutDiagram page, final TrainControlUI ui) throws Exception
    {
        final JPanel host = new JPanel();
        final LayoutGrid[] built = new LayoutGrid[1];

        SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
                host.setBackground(Color.WHITE);

                // popup = true lays the grid out to its own natural size rather than to a container
                // it has been given; master = null keeps it out of editor mode.
                built[0] = new LayoutGrid(page, TILE, host, null, true, ui);
            }
        });

        return new Rendered(host, built[0], ui);
    }

    /**
     * Paints the grid as it stands now.
     *
     * The tiles decode their images on a worker, so this waits for them to settle first - otherwise the
     * first snapshot of a page is a picture of a grid that has not finished arriving, and a test
     * comparing it with a later one reports a difference that is only timing.
     *
     * @return the image
     * @throws Exception if Swing cannot paint it
     */
    public BufferedImage snapshot() throws Exception
    {
        settle();

        final BufferedImage[] image = new BufferedImage[1];

        SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
                Dimension preferred = host.getPreferredSize();

                int width = Math.max(preferred.width, grid.maxWidth);
                int height = Math.max(preferred.height, grid.maxHeight);

                if (width < 1) width = 1;
                if (height < 1) height = 1;

                host.setSize(width, height);

                layoutEverything(host);

                image[0] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

                Graphics2D g = image[0].createGraphics();

                try
                {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, width, height);

                    // paint, not printAll or paintAll: both begin with an isShowing() check and do
                    // nothing for a component that is not on screen, which is every component here.
                    host.paint(g);
                }
                finally
                {
                    g.dispose();
                }
            }
        });

        return image[0];
    }

    /**
     * Waits for the tile images to finish decoding, and for the event queue to catch up.
     *
     * @throws Exception if the wait is interrupted
     */
    public void settle() throws Exception
    {
        if (ui != null)
        {
            final java.util.concurrent.CountDownLatch settled =
                new java.util.concurrent.CountDownLatch(1);

            ui.whenTilesSettled(new Runnable()
            {
                @Override
                public void run()
                {
                    settled.countDown();
                }
            });

            settled.await(30, java.util.concurrent.TimeUnit.SECONDS);
        }

        // One more turn, so label updates posted by the last decode have been applied.
        SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
            }
        });
    }

    /**
     * The square at a grid position, as its own image.
     *
     * Asking about one tile rather than about the page is what makes a rendered assertion readable: "the
     * square behind the train got darker" is a sentence, and "4% of the page changed" is not.
     *
     * @param page the whole-page image
     * @param x the column
     * @param y the row
     * @return the tile, or null when it falls outside the image
     */
    public static BufferedImage tile(BufferedImage page, int x, int y)
    {
        if (page == null) return null;

        int left = x * TILE;
        int top = y * TILE;

        if (left < 0 || top < 0 || left + TILE > page.getWidth() || top + TILE > page.getHeight())
        {
            return null;
        }

        return page.getSubimage(left, top, TILE, TILE);
    }

    /**
     * How dark a tile is, averaged over its pixels.
     *
     * The covered wash is a translucent grey laid over the tile art, so a covered square is uniformly
     * DARKER than the same square uncovered. Mean brightness says that in one number, and it does not
     * depend on which pixels the track happens to occupy - which a fixed sample point would.
     *
     * @param tile the image
     * @return 0 (black) to 255 (white), or -1 for a null image
     */
    public static double brightness(BufferedImage tile)
    {
        if (tile == null) return -1;

        double total = 0;

        for (int x = 0; x < tile.getWidth(); x++)
        {
            for (int y = 0; y < tile.getHeight(); y++)
            {
                int rgb = tile.getRGB(x, y);

                total += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
            }
        }

        return total / (tile.getWidth() * tile.getHeight() * 3.0);
    }

    /**
     * Whether two images are pixel-for-pixel the same.
     *
     * @param a one image
     * @param b the other
     * @return true when identical, false when either is null or they differ
     */
    public static boolean same(BufferedImage a, BufferedImage b)
    {
        if (a == null || b == null) return false;

        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;

        for (int x = 0; x < a.getWidth(); x++)
        {
            for (int y = 0; y < a.getHeight(); y++)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
            }
        }

        return true;
    }

    /**
     * Writes an image out, for looking at when an assertion fails.
     *
     * A rendered test that fails and shows you nothing is a test you cannot act on.
     *
     * @param image the image
     * @param to the file
     */
    public static void save(BufferedImage image, java.io.File to)
    {
        try
        {
            if (image == null || to == null) return;

            if (to.getParentFile() != null) to.getParentFile().mkdirs();

            javax.imageio.ImageIO.write(image, "png", to);
        }
        catch (java.io.IOException cannotSave)
        {
            // The assertion is the point; the picture is a courtesy.
        }
    }

    /**
     * Whether this tile is drawing the mark that says a train is lying across it (MT-309).
     *
     * The mark used to be the tile's ICON - a greyed copy - so "is it marked" could be asked of the
     * object with an identity comparison.  It is a line PAINTED over the icon now, which is not in
     * the object at all, so the only honest way to ask is to paint the tile and look.
     *
     * By hue rather than against `LayoutLabel.TRAIN_MARK`: the claim is that somebody looking at the
     * square sees orange on it, and a check reading production's own constant would agree with any
     * value that constant was changed to, grey included.
     *
     * `paint` and not `printAll`: both of the others begin with an isShowing() check and do nothing
     * for a component that is not on screen, which is every component a test builds.
     *
     * @param tile the label
     * @return true when the mark is on it
     * @throws Exception if Swing cannot paint it
     */
    public static boolean showsTheTrainMark(final java.awt.Component tile) throws Exception
    {
        if (tile == null) return false;

        final BufferedImage[] shot = new BufferedImage[1];

        SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
                int width = Math.max(1, tile.getWidth() > 0 ? tile.getWidth() : TILE);
                int height = Math.max(1, tile.getHeight() > 0 ? tile.getHeight() : TILE);

                tile.setSize(width, height);

                shot[0] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

                Graphics2D g = shot[0].createGraphics();

                try
                {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, width, height);

                    tile.paint(g);
                }
                finally
                {
                    g.dispose();
                }
            }
        });

        return hasTheTrainMark(shot[0]);
    }

    /**
     * The same question asked of a picture that has already been taken (MT-309).
     *
     * Separate so that a test working from a whole-page snapshot - `tile(page, x, y)` - asks the same
     * thing as one painting a single label, and there is one statement of what the mark looks like.
     *
     * @param image the tile
     * @return true when there is orange on it
     */
    public static boolean hasTheTrainMark(BufferedImage image)
    {
        if (image == null) return false;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                int rgb = image.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (r > 190 && g > 70 && g < 190 && b < 90 && r - g > 60) return true;
            }
        }

        return false;
    }

    private static void layoutEverything(Container container)
    {
        container.doLayout();

        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof Container) layoutEverything((Container) child);
        }
    }
}
