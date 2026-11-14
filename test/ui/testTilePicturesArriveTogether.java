package ui;

import java.awt.Component;
import java.awt.Image;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import support.Components;
import support.FrozenEditor;
import support.Reflect;

/**
 * A diagram's squares and their pictures: a page drawn at a size nobody has drawn yet appears whole on its first paint,
 * each picture is decoded once however many squares show it, and a picture that cannot be read never holds a diagram.
 */
public class testTilePicturesArriveTogether
{
    private static FrozenEditor frozen;

    @BeforeClass
    public static void open() throws Exception
    {
        frozen = FrozenEditor.open();
    }

    @AfterClass(alwaysRun = true)
    public static void close() throws Exception
    {
        if (frozen != null) frozen.close();
    }

    /** A square that counts how often its picture is decoded, and can be made unreadable. */
    private static final class Counted extends LayoutDiagramComponent
    {
        private final AtomicInteger reads;
        private final boolean unreadable;

        Counted(componentType type, int x, int y, AtomicInteger reads, boolean unreadable) throws IOException
        {
            super(type, x, y, 0, 0, 0, 0, Accessory.accessoryDecoderType.MM2);

            this.reads = reads;
            this.unreadable = unreadable;
        }

        @Override
        public Image getImage(int size, boolean ignoreState) throws IOException
        {
            reads.incrementAndGet();

            if (unreadable) throw new IOException("a picture that cannot be read, on purpose");

            return super.getImage(size, ignoreState);
        }
    }

    /** Builds `page` at `size` on a panel of its own and waits for its pictures; the grid built. */
    private static LayoutGrid buildAndSettle(LayoutDiagram page, int size) throws Exception
    {
        final LayoutGrid[] grid = new LayoutGrid[1];

        SwingUtilities.invokeAndWait(() -> grid[0] = new LayoutGrid(page, size, new JPanel(), null, true, frozen.ui));

        final CountDownLatch settled = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> frozen.ui.whenTilesSettled(settled::countDown));

        assertTrue(settled.await(20, TimeUnit.SECONDS),
            "the diagram was still waiting for its pictures 20 seconds after it was built: the count of pictures being "
            + "decoded never came back to zero, and a diagram waiting on it is held behind the spinner");

        Components.settle();

        return grid[0];
    }

    /**
     * Two hundred straight squares and twenty curves are two pictures, so two decodes, not one per square.
     *
     * MUTATION: in `LayoutLabel.decodeOnce`, return a fresh `CompletableFuture.runAsync(...)` instead of going through
     * `DECODING.computeIfAbsent` and the count goes to dozens.
     */
    @Test
    public void testAPictureIsDecodedOnceForAllItsSquares() throws Exception
    {
        final AtomicInteger reads = new AtomicInteger();
        final LayoutDiagram page = new LayoutDiagram("decoded once", 20, 11, null, null);

        for (int x = 0; x < 20; x++)
        {
            for (int y = 0; y < 10; y++)
            {
                page.addComponent(new Counted(LayoutDiagramComponent.componentType.STRAIGHT, x, y, reads, false), x, y);
            }

            page.addComponent(new Counted(LayoutDiagramComponent.componentType.CURVE, x, 10, reads, false), x, 10);
        }

        // A size no other claim draws, so nothing is in the cache yet.
        buildAndSettle(page, 41);

        assertEquals(reads.get(), 2,
            "a page of 200 straight squares and 20 curves decoded its pictures " + reads.get() + " times.  Each square "
            + "missing its picture read and scaled the same file again, and only one copy was kept: the first page "
            + "shown at a size took far longer than any after it");
    }

    /**
     * A picture that cannot be read still stops being waited for, and its squares keep their room: blank squares, not
     * a row pulled together.
     *
     * MUTATION: in `LayoutLabel.setImage`, call `tileDecodeFinished` only when the picture reached the image cache and
     * the wait never ends (deleting `LayoutLabel.getPreferredSize` fails the width).
     */
    @Test
    public void testAPictureThatCannotBeReadDoesNotHoldTheDiagram() throws Exception
    {
        final AtomicInteger reads = new AtomicInteger();
        final LayoutDiagram page = new LayoutDiagram("unreadable", 6, 2, null, null);

        for (int x = 0; x < 6; x++)
        {
            page.addComponent(new Counted(LayoutDiagramComponent.componentType.STRAIGHT, x, 0, reads, false), x, 0);
            // A switch: a plain track square had a blank picture to stand in from the start.
            page.addComponent(new Counted(LayoutDiagramComponent.componentType.SWITCH_LEFT, x, 1, reads, true), x, 1);
        }

        final LayoutGrid grid = buildAndSettle(page, 43);

        assertTrue(frozen.ui.tilesAreSettled(), "a picture is still counted as being decoded after the build settled");

        final int[] width = {-1};

        SwingUtilities.invokeAndWait(() ->
        {
            for (Component k : grid.getContainer().getComponents())
            {
                if (k instanceof LayoutLabel && ((LayoutLabel) k).getComponent() instanceof Counted
                    && ((LayoutLabel) k).getComponent().getY() == 1)
                {
                    width[0] = k.getPreferredSize().width;
                }
            }
        });

        assertTrue(width[0] >= 43,
            "a square whose picture could not be read asks for " + width[0] + " pixels of width, not 43.  The squares "
            + "after it close up and the row no longer lines up with the rows around it");
    }

    /**
     * Changing the main window's diagram size to one not drawn yet: the first paint of the new diagram has every square
     * with its picture and none zero wide.  The old one stays up while the pictures are decoded.
     *
     * MUTATION: delete the `awaitTheShownPagesPictures()` call in `TrainControlUI.repaintLayout` and the first paint
     * has squares without their pictures.
     */
    @Test(timeOut = 120000)
    public void testANewSizeIsWholeOnItsFirstPaint() throws Exception
    {
        final TrainControlUI ui = frozen.ui;
        final JPanel inner = (JPanel) Reflect.get(TrainControlUI.class, ui, "InnerLayoutPanel");
        final JComboBox<?> sizes = (JComboBox<?>) Reflect.get(TrainControlUI.class, ui, "SizeList");

        @SuppressWarnings("unchecked")
        final Map<String, Integer> pixels = (Map<String, Integer>) Reflect.get(TrainControlUI.class, ui, "layoutSizes");

        final int[] chosen = {-1, -1};
        final String[] firstPaint = {null};
        final Component[] before = {null};
        final boolean[] switched = {false};

        // What the new diagram holds as each paint begins: squares without a picture, squares with no width.
        final RepaintManager watching = new RepaintManager()
        {
            @Override
            public void paintDirtyRegions()
            {
                Component now = inner.getComponentCount() == 0 ? null : inner.getComponent(0);

                if (switched[0] && firstPaint[0] == null && now != before[0] && now instanceof JPanel)
                {
                    int squares = 0, missing = 0, zero = 0;

                    for (Component k : ((JPanel) now).getComponents())
                    {
                        if (!(k instanceof LayoutLabel) || ((LayoutLabel) k).getComponent() == null
                            || ((LayoutLabel) k).getComponent().isText()) continue;

                        squares++;

                        if (((LayoutLabel) k).getIcon() == null) missing++;
                        if (k.getWidth() == 0) zero++;
                    }

                    firstPaint[0] = squares + " squares, " + missing + " without their picture, " + zero + " zero wide";
                }

                super.paintDirtyRegions();
            }
        };

        final RepaintManager was = Components.onTheEventThread(() -> RepaintManager.currentManager((JComponent) inner));

        // Each decode slowed to what a busy machine takes, so a first paint cannot beat the pictures by luck.
        final ExecutorService decoding = (ExecutorService) Reflect.get(TrainControlUI.class, ui, "TileImageLoader");

        final ExecutorService slowed = new AbstractExecutorService()
        {
            @Override
            public void execute(Runnable work)
            {
                decoding.execute(() ->
                {
                    try
                    {
                        Thread.sleep(150);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }

                    work.run();
                });
            }

            @Override
            public void shutdown()
            {
            }

            @Override
            public List<Runnable> shutdownNow()
            {
                return Collections.emptyList();
            }

            @Override
            public boolean isShutdown()
            {
                return false;
            }

            @Override
            public boolean isTerminated()
            {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit)
            {
                return false;
            }
        };

        Reflect.set(TrainControlUI.class, ui, "TileImageLoader", slowed);

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                RepaintManager.setCurrentManager(watching);

                ui.setFocusableWindowState(false);
                ui.setVisible(true);
                ui.showLayoutTab();
            });

            Thread.sleep(1500);
            Components.settle();

            SwingUtilities.invokeAndWait(() ->
            {
                chosen[0] = sizes.getSelectedIndex();

                int shown = pixels.get(String.valueOf(sizes.getSelectedItem()));

                // The middle size unless it is shown already, then the largest: drawn by nothing yet either way.
                for (int i = 0; i < sizes.getItemCount(); i++)
                {
                    int each = pixels.get(String.valueOf(sizes.getItemAt(i)));

                    if (each != shown && (chosen[1] < 0 || each == TrainControlUI.MEDIUM_TILE_SIZE)) chosen[1] = i;
                }

                before[0] = inner.getComponentCount() == 0 ? null : inner.getComponent(0);
                switched[0] = true;

                sizes.setSelectedIndex(chosen[1]);
            });

            assertTrue(Components.waitFor(() -> firstPaint[0] != null, 20000),
                "precondition: the diagram at the new size was never painted");

            assertEquals(firstPaint[0].replaceFirst("^\\d+ squares, ", ""), "0 without their picture, 0 zero wide",
                "the first paint of the diagram at a new size showed " + firstPaint[0] + ".  It went on screen before "
                + "its pictures were decoded, so the operator sees squares missing and the squares after them closed "
                + "up, then everything jump into place one paint later");
        }
        finally
        {
            Reflect.set(TrainControlUI.class, ui, "TileImageLoader", decoding);

            SwingUtilities.invokeAndWait(() ->
            {
                RepaintManager.setCurrentManager(was);

                if (chosen[0] >= 0) sizes.setSelectedIndex(chosen[0]);

                ui.setVisible(false);
            });
        }
    }
}
