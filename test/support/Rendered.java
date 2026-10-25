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
 * Adam: "let's make the suite able to see rendered output when needed."  Nothing in the suite did, and OB-180 is what
 * that cost: the covered-track wash was computed correctly, but nothing REPAINTED the tiles whose state had changed, so
 * every assertion on the covered set, the model or the source answered identically with the bug present and fixed.
 *
 * Held open is the whole design: `DiagramExport.render` renders a page offscreen but constructs a FRESH grid every
 * call, and a fresh label always computes its wash correctly.  The defect only exists for a label already on screen and
 * never told to redraw.  So this builds the grid ONCE; `snapshot()` paints that same grid again, and between two
 * snapshots a test can move a train and call the production refresh.
 *
 * Not an editor: the grid is built with a null master, so `inEditor` is false and the viewer's rules apply, which is
 * where the wash is drawn.
 *
 * Needs a display: callers skip when `GraphicsEnvironment.isHeadless()`, as the other rendering tests do.
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
     * @param ui the window, which the tiles ask about covered track and about images
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
     * The panel the grid was built into, for a test that looks at what the grid put there.
     */
    public JPanel host()
    {
        return host;
    }

    /**
     * Paints the grid as it stands now.  The tiles decode their images on a worker, so this waits for them to settle
     * first, or the first snapshot is a picture of a grid that has not finished arriving.
     *
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
     * The square at a grid position, as its own image: asking about one tile rather than the page makes a rendered
     * assertion readable ("the square behind the train got darker" rather than "4% of the page changed").
     *
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
     * How dark a tile is, averaged over its pixels.  The covered wash is a translucent gray, so a covered square is
     * uniformly DARKER than the same square uncovered; mean brightness says that in one number and does not depend on
     * which pixels the track happens to occupy.
     *
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
     * Whether two images are pixel-for-pixel the same: false when either is null or they differ.
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
     * Whether this tile is drawing the mark that says a train is lying across it (MT-309).  The mark is a line PAINTED
     * over the icon, which is not in the object at all, so the only honest way to ask is to paint the tile and look.
     *
     * By hue rather than against `LayoutLabel.TRAIN_MARK`: a check reading production's own constant would agree with
     * any value it was changed to, gray included.  `paint` and not `printAll`: both of the others begin with an
     * isShowing() check and do nothing for a component that is not on screen.
     *
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
     * Whether a pixel is the train's orange - by its HUE, an orange between 12 and 40 degrees, as saturated as a third
     * and as bright as 110 of 255.  The line is see-through (Adam: "can we change to the see through tail line"): over
     * black track it is a dark orange, rgb(140,56,0), and over white a pale one, rgb(255,171,115), which fixed
     * thresholds counted neither.  Red (0 degrees), the destination's yellow (47) and the grays fall outside it.  One
     * rule for every test that asks, so they cannot come to disagree about what orange is.
     */
    public static boolean isTrainOrange(int rgb)
    {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));

        if (max != r || max < 110 || max - min < max / 3.0) return false;

        double hue = 60.0 * (g - b) / (max - min);

        return hue >= 12 && hue <= 40;
    }

    /**
     * The same question asked of a picture that has already been taken (MT-309), so a test working from a whole-page
     * snapshot asks the same thing as one painting a single label.
     */
    public static boolean hasTheTrainMark(BufferedImage image)
    {
        if (image == null) return false;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if (isTrainOrange(image.getRGB(x, y))) return true;
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
