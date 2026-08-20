package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.util.I18n;

/**
 * Saves a track diagram to a picture file, at whatever size it is asked for.
 *
 * There was no way to get a diagram out of TrainControl except by photographing the screen, which
 * caps it at the size of the window and at whatever part of it happened to be scrolled into view. A
 * diagram is the one thing about a layout worth showing somebody else - in a manual, on a forum, or
 * beside the layout itself - so it should be possible to hand over the whole of one, sharp.
 *
 * Drawn OFFSCREEN rather than by photographing the on-screen grid. Painting the visible component
 * would inherit its scroll position, its window size, and its selection highlights; building a fresh
 * grid at the requested tile size and painting that gives the whole diagram, at any size, with none
 * of the interface in it.
 *
 * The tiles come from the same LayoutGrid the application draws with, so an export cannot drift away
 * from what is on screen: if a tile renders differently here than there, it is because the grid
 * renders it differently, and both change together.
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

    /**
     * About what the diagram looks like on screen.
     *
     * The small of the two sizes offered.  Sixty reads well in a document and is the default; this is
     * for a picture meant to look like the thing the user is looking at.
     */
    public static final int SCREEN_TILE_SIZE = 30;

    /** How long to wait for tile images before drawing anyway. */
    private static final int TILE_WAIT_SECONDS = 30;

    /**
     * The tile size the diagram is actually DRAWN at, whatever size was asked for.
     *
     * Tile images are files on disk, in folders named for their size - there is an icons30 and an
     * icons60 and nothing else. Ask a grid for 40-pixel tiles and every icon fails to load: the
     * failure is logged per tile and the picture comes out blank, which is exactly what the first
     * version of this class produced and what its test caught.
     *
     * So the grid is always built at 60, the larger of the two, and the finished picture is scaled to
     * whatever was wanted. That makes "any size" true rather than "any size for which somebody
     * remembered to draw an icon set", and scaling one finished image is both sharper and far cheaper
     * than scaling several hundred tiles.
     */
    private static final int NATIVE_TILE_SIZE = 60;

    private DiagramExport()
    {
    }

    /**
     * Draws a diagram into an image.
     *
     * Must be called OFF the event thread, and says so by throwing rather than by producing a blank
     * picture.  A tile does not draw itself when it is built: the image is decoded on a worker and
     * applied to the label afterwards, on the event thread.  So a render that holds the event thread
     * from start to finish paints every tile before any of them has an image - a file that opens
     * perfectly well and holds an empty white rectangle, with nothing anywhere saying so.
     *
     * The three steps therefore go where they belong: build on the event thread, WAIT for the decodes
     * off it, paint on it again.
     *
     * @param layout the page to draw
     * @param tileSize how many pixels each square should be
     * @param ui the application, which the grid needs for icons and colours
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

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            host.setBackground(Color.WHITE);

            // popup = true, because that is the mode that lays a grid out to its own natural size
            // rather than to fit a container it has been given
            grid[0] = new LayoutGrid(layout, size, host, null, true, ui);
        });

        awaitTiles(ui);

        final BufferedImage[] image = new BufferedImage[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            // A component that has never been shown has no size, and painting one paints nothing.
            // Giving it its preferred size and laying it out by hand is what makes an offscreen render
            // work at all.
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

                // paint, NOT printAll or paintAll.  Both of those begin with an isShowing() check and
                // do nothing at all for a component that is not on screen - which is every component
                // in an offscreen render, and the second reason the first version came out blank.
                host.paint(g);
            }
            finally
            {
                g.dispose();
            }
        });

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
