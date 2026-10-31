package org.traincontrol.gui.autonomy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * Saves a track diagram to a picture file, at whatever size it is asked for.
 *
 * Drawn OFFSCREEN from a fresh grid at the requested tile size, not by photographing the on-screen component, which
 * would inherit its scroll position, window size and selection highlights.
 *
 * The tiles come from the same LayoutGrid the application draws with, so an export cannot drift from what is on screen.
 */
public final class DiagramExport
{
    /**
     * How big a tile is, in pixels, when no size is given.
     *
     * Larger than anything the interface uses. The point of an export is a picture bigger than the
     * screen could show, and a diagram at the on-screen size is one somebody could have taken a
     * photograph of.
     */
    public static final int DEFAULT_TILE_SIZE = 60;

    /** The largest tile size offered, so that a big layout cannot ask for an image nothing can open. */
    public static final int MAX_TILE_SIZE = 200;

    /** How long to wait for tile images before drawing anyway. */
    private static final int TILE_WAIT_SECONDS = 30;

    /**
     * The size the diagram is DRAWN at: tile icons exist only as icons30 and icons60, so a grid at any other size loads
     * no icons and renders blank.  The grid is always built at 60 and the finished picture is scaled, which is also
     * sharper and far cheaper than scaling the tiles.
     */
    private static final int NATIVE_TILE_SIZE = 60;

    private DiagramExport()
    {
    }

    /** Set by a test to make a render fail after its grid is built; null everywhere else. */
    public static Runnable stumbleForTest;

    /**
     * Draws a diagram into an image.
     *
     * Must be called OFF the event thread, and throws rather than produce a blank picture: tile images are decoded on a
     * worker and applied on the event thread afterwards, so a render that holds the event thread paints every tile
     * before it has an image.  Hence: build on the event thread, WAIT for the decodes off it, paint on it again.
     *
     * @param layout the page to draw
     * @param tileSize how many pixels each square should be
     * @param ui the application, which the grid needs for icons and colors
     * @return the picture
     */
    public static BufferedImage render(final LayoutDiagram layout, int tileSize,
        final TrainControlUI ui) throws Exception
    {
        if (javax.swing.SwingUtilities.isEventDispatchThread())
        {
            throw new IllegalStateException(
                "a diagram export holds the event thread while it waits for tile images, so it cannot "
                + "run on it - call this from a worker");
        }

        if (tileSize < 1) tileSize = DEFAULT_TILE_SIZE;
        if (tileSize > MAX_TILE_SIZE) tileSize = MAX_TILE_SIZE;

        final int wanted = tileSize;
        final int size = NATIVE_TILE_SIZE;
        final JPanel host = new JPanel();
        final LayoutGrid[] grid = new LayoutGrid[1];

        final BufferedImage[] image = new BufferedImage[1];

        // Every path out retires the grid, and the build is inside the bracket too: LayoutGrid's constructor registers
        // the captions, so one that threw part-way would otherwise leak.  Retired by the PANEL (the key the constructor
        // registers under first), not grid[0], which a throwing constructor never assigns.
        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                host.setBackground(Color.WHITE);

                // popup = true, because that is the mode that lays a grid out to its own natural size
                // rather than to fit a container it has been given
                grid[0] = new LayoutGrid(layout, size, host, null, true, ui);
            });

            // A way to make this fail on purpose, null in the application: nothing here throws on input a test can hand
            // in, so this seam is the only way to say "the render threw" (as the route editor's discardAnswerForTest).
            if (stumbleForTest != null) stumbleForTest.run();

            awaitTiles(ui);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                // A component never shown has no size and paints nothing: give it its preferred size and lay it out by
                // hand.
                Dimension preferred = host.getPreferredSize();

                int width = Math.max(preferred.width, grid[0].maxWidth);
                int height = Math.max(preferred.height, grid[0].maxHeight);

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

                    // paint, not printAll or paintAll: both begin with an isShowing() check and do nothing offscreen.
                    host.paint(g);
                }
                finally
                {
                    g.dispose();
                }
            });

        }
        finally
        {
            // Finished with, and said so: the grid is built for one render into a panel nobody shows, and an unretired
            // one would keep its whole grid reachable through the window's label table, one page per export.
            // And the retiring does not replace what went wrong: invokeAndWait throws InterruptedException when the
            // interrupt flag is set, which would come out in place of the paint's own exception.  The discard still
            // happens: the event is posted before the wait.
            try
            {
                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    // By the panel, not the reference: grid[0] is assigned only if the constructor returned, and
                    // LayoutGrid.retire asks the registry the constructor writes first.
                    LayoutGrid.retire(host);
                });
            }
            catch (InterruptedException marshalling)
            {
                Thread.currentThread().interrupt();
            }
            catch (java.lang.reflect.InvocationTargetException fromTheRetirement)
            {
                // The retirement's own failure must not replace the render's: invokeAndWait wraps what the task throws,
                // which would discard the cause the caller needs.  Logged rather than thrown: a stale registration is a
                // leak, while losing the reason an export failed leaves nobody anywhere to start.
                if (ui != null) ui.log(I18n.f("layout.errorExportGridNotRetired",
                    String.valueOf(fromTheRetirement.getCause())));
            }
        }

        return wanted == NATIVE_TILE_SIZE ? image[0] : scaled(image[0], wanted);
    }

    /**
     * The picture at a different tile size, scaled as a whole.
     */
    private static BufferedImage scaled(BufferedImage source, int wantedTileSize)
    {
        if (source == null) return null;

        double factor = (double) wantedTileSize / (double) NATIVE_TILE_SIZE;

        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = out.createGraphics();

        try
        {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);

            g.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            g.dispose();
        }

        return out;
    }

    /**
     * Blocks until every tile image has been decoded and applied.
     *
     * Bounded, because a decode that never finishes must not hang the export forever - a picture
     * missing a tile is a better outcome than a program that stops responding.
     */
    private static void awaitTiles(TrainControlUI ui) throws InterruptedException
    {
        if (ui == null) return;

        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(TILE_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

        // One more turn of the event queue, so the label updates posted by the last decode have been
        // applied before anything is painted
        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
        catch (Exception e)
        {
            // Nothing to do about it here; the paint below simply happens a moment early
        }
    }

    /**
     * Lays out a container and everything inside it.
     *
     * doLayout only arranges one level.  Without walking down, the tiles inside the grid keep a size
     * of zero and the picture comes out empty.
     */
    private static void layoutEverything(java.awt.Container container)
    {
        container.doLayout();

        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof java.awt.Container)
            {
                layoutEverything((java.awt.Container) child);
            }
        }
    }

    /**
     * Draws a diagram and writes it to a PNG.
     *
     * @param layout the page
     * @param tileSize pixels per square
     * @param ui the application
     * @param destination the file to write
     * @throws java.io.IOException if the file cannot be written, so the caller can say so
     */
    public static void writePng(LayoutDiagram layout, int tileSize, TrainControlUI ui, File destination)
        throws Exception
    {
        BufferedImage image = render(layout, tileSize, ui);

        if (!ImageIO.write(image, "png", destination))
        {
            throw new java.io.IOException(I18n.t("layout.ui.errorExportNoWriter"));
        }
    }
}
