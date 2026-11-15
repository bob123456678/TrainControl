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
import org.traincontrol.base.Feedback;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import support.Components;
import support.FrozenEditor;
import support.Reflect;
import support.Settle;

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

        // The decode's door; not marked @Override, so a red run can compile this file over code without it
        public Image getImage(String imageName, int size) throws IOException
        {
            reads.incrementAndGet();

            if (unreadable) throw new IOException("a picture that cannot be read, on purpose");

            return super.getImage(imageName, size);
        }
    }

    /** The tile loader with every decode held until `release` opens. */
    private static final class Held extends AbstractExecutorService
    {
        private final ExecutorService under;
        final CountDownLatch release = new CountDownLatch(1);

        Held(ExecutorService under)
        {
            this.under = under;
        }

        @Override
        public void execute(Runnable work)
        {
            under.execute(() ->
            {
                try
                {
                    release.await();
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
    }

    /** A sensor of no station, set and cleared by the claim. */
    private static final class HeldSensor extends Feedback
    {
        HeldSensor()
        {
            super("held");
        }

        @Override
        public int getUID()
        {
            return 0;
        }

        @Override
        public void setState(boolean val)
        {
            _setState(val);
        }

        @Override
        public void addTile(LayoutLabel l)
        {
        }
    }

    /** A combo box's model as it was, noting each read of its selection made on another thread than the two allowed. */
    private static final class Watched extends javax.swing.AbstractListModel<Object>
        implements javax.swing.ComboBoxModel<Object>
    {
        private final javax.swing.ComboBoxModel<Object> under;
        private final String which;
        private final Thread caller;
        private final List<String> offTheEventThread;

        Watched(javax.swing.ComboBoxModel<Object> under, String which, Thread caller, List<String> offTheEventThread)
        {
            this.under = under;
            this.which = which;
            this.caller = caller;
            this.offTheEventThread = offTheEventThread;
        }

        @Override
        public Object getSelectedItem()
        {
            // The claim's own thread asks repaintLayout, as a sync's worker does; the rebuild it starts is what is watched
            if (!SwingUtilities.isEventDispatchThread() && Thread.currentThread() != caller)
            {
                offTheEventThread.add(which + " on " + Thread.currentThread().getName());
            }

            return under.getSelectedItem();
        }

        @Override
        public void setSelectedItem(Object item)
        {
            under.setSelectedItem(item);
            fireContentsChanged(this, -1, -1);
        }

        @Override
        public int getSize()
        {
            return under.getSize();
        }

        @Override
        public Object getElementAt(int index)
        {
            return under.getElementAt(index);
        }
    }

    /**
     * A rebuild of the main window's diagram asked from a worker reads the page and size boxes on the event thread,
     * where Swing's models may be read, and not on the renderer's thread.
     *
     * MUTATION: in `TrainControlUI.awaitTheShownPagesPictures`, read `SizeList.getSelectedItem()` there again, and this
     * fails.
     */
    @Test(timeOut = 120000)
    @SuppressWarnings("unchecked")
    public void testARebuildReadsThePageAndSizeOnTheEventThread() throws Exception
    {
        final TrainControlUI ui = frozen.ui;
        final JComboBox<Object> pages = (JComboBox<Object>) Reflect.get(TrainControlUI.class, ui, "LayoutList");
        final JComboBox<Object> sizes = (JComboBox<Object>) Reflect.get(TrainControlUI.class, ui, "SizeList");
        final javax.swing.ComboBoxModel<Object> pagesWere = pages.getModel();
        final javax.swing.ComboBoxModel<Object> sizesWere = sizes.getModel();
        final List<String> off = Collections.synchronizedList(new java.util.ArrayList<>());
        final Thread caller = Thread.currentThread();

        SwingUtilities.invokeAndWait(() ->
        {
            pages.setModel(new Watched(pagesWere, "the page", caller, off));
            sizes.setModel(new Watched(sizesWere, "the size", caller, off));
        });

        try
        {
            Settle.theDiagram(ui);

            ui.repaintLayout();

            Settle.theDiagram(ui);

            assertEquals(off, Collections.emptyList(), "a rebuild read the combo boxes off the event thread, where a"
                + " change of page or size meanwhile can be read half made");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if (pages.getModel() instanceof Watched) pages.setModel(pagesWere);
                if (sizes.getModel() instanceof Watched) sizes.setModel(sizesWere);
            });
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
     * A sensor's picture asked for while it was occupied and decoded after it was cleared is the occupied picture: kept
     * under the occupied key, the cleared one stood for every occupied sensor of that turn and size until the program
     * closed.
     *
     * MUTATION: in `LayoutLabel.decodeOnce`, decode with `c.getImage(size, edit)`, the state at decode time, and this
     * fails.
     */
    @Test(timeOut = 60000)
    public void testAPictureIsOfTheStateItWasAskedIn() throws Exception
    {
        final TrainControlUI ui = frozen.ui;
        final HeldSensor sensor = new HeldSensor();
        final LayoutDiagramComponent square = new LayoutDiagramComponent(LayoutDiagramComponent.componentType.FEEDBACK,
            0, 0, 0, 0, 0, 0, Accessory.accessoryDecoderType.MM2);

        square.setFeedback(sensor);
        sensor.setState(true);

        // The icon set's own size, unturned: the picture is the file as read, the same object every time
        final int size = 30;
        final Image occupied = square.getImage(size, false);
        final String key = square.getImageKey(size, false);

        assertSame(square.getImage(size, false), occupied, "precondition: the occupied picture is drawn anew each time");

        final ExecutorService decoding = (ExecutorService) Reflect.get(TrainControlUI.class, ui, "TileImageLoader");
        final Held held = new Held(decoding);
        final Image cached;

        TrainControlUI.getImageCache().remove(key);
        Reflect.set(TrainControlUI.class, ui, "TileImageLoader", held);

        try
        {
            java.util.concurrent.Future<?> done = (java.util.concurrent.Future<?>) Reflect.method(LayoutLabel.class,
                "decodeOnce", LayoutDiagramComponent.class, int.class, boolean.class, TrainControlUI.class)
                .invoke(null, square, size, false, ui);

            sensor.setState(false);
            held.release.countDown();

            done.get(20, TimeUnit.SECONDS);
        }
        finally
        {
            held.release.countDown();

            Reflect.set(TrainControlUI.class, ui, "TileImageLoader", decoding);

            cached = TrainControlUI.getImageCache().get(key);

            // Not left for the claims after this one
            if (cached != occupied) TrainControlUI.getImageCache().remove(key);
        }

        assertSame(cached, occupied, "the occupied sensor's picture, asked for while it was occupied and decoded once it"
            + " was cleared, is " + (cached == null ? "missing" : "another picture, the cleared one") + ": every"
            + " occupied sensor drawn that way would look clear until the program is closed");
    }

    /**
     * A sensor that changes while its square is built, after the square put up its cached picture and before the build
     * registered it with the sensor, is shown changed once the build is done: neither the picture nor the sensor's news
     * carried it, and the square showed the old state until the sensor changed again.
     *
     * MUTATION: leave out the `readAgainOnceRegistered()` call in `LayoutGrid`'s build, and the square shows the sensor
     * clear.
     */
    @Test(timeOut = 60000)
    public void testASensorChangedWhileItsSquareIsBuiltIsShown() throws Exception
    {
        final HeldSensor sensor = new HeldSensor();
        final LayoutDiagramComponent square = new LayoutDiagramComponent(LayoutDiagramComponent.componentType.FEEDBACK,
            0, 0, 0, 0, 0, 0, Accessory.accessoryDecoderType.MM2)
        {
            // The build asks for the sensor to register the square with it, after the square's picture is up: the
            // sensor changes then
            @Override
            public Feedback getFeedback()
            {
                if (LayoutGrid.class.getName().equals(Thread.currentThread().getStackTrace()[2].getClassName()))
                {
                    sensor.setState(true);
                }

                return super.getFeedback();
            }
        };
        final LayoutDiagram page = new LayoutDiagram("changed while built", 2, 2, null, null);
        final Map<String, Image> cache = TrainControlUI.getImageCache();

        // A size no other claim draws; both pictures cached, so the square puts its picture up as it is made
        final int size = 37;

        square.setFeedback(sensor);
        page.addComponent(square, 0, 0);

        sensor.setState(true);

        final String occupiedKey = square.getImageKey(size, false);
        final Image occupied = square.getImage(size, false);

        sensor.setState(false);

        final String clearKey = square.getImageKey(size, false);

        cache.put(occupiedKey, occupied);
        cache.put(clearKey, square.getImage(size, false));

        try
        {
            LayoutLabel shown = ((LayoutLabel[][]) Reflect.get(LayoutGrid.class, buildAndSettle(page, size), "grid"))[0][0];

            assertTrue(sensor.isSet(), "precondition: the sensor was not set during the build");
            assertSame(((javax.swing.ImageIcon) shown.getIcon()).getImage(), occupied, "a sensor set while its square"
                + " was built, after its picture went up and before it was registered for the sensor's changes, is"
                + " shown clear: the square shows the wrong state until the sensor changes again");
        }
        finally
        {
            cache.remove(occupiedKey);
            cache.remove(clearKey);
        }
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

        final ExecutorService decoding = (ExecutorService) Reflect.get(TrainControlUI.class, ui, "TileImageLoader");

        // The new size's pictures are held, so a first paint cannot beat them by luck
        final Held held = new Held(decoding);

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                RepaintManager.setCurrentManager(watching);

                ui.setFocusableWindowState(false);
                ui.setVisible(true);
                ui.showLayoutTab();
            });

            Settle.theDiagram(ui);
            Components.settle();

            Reflect.set(TrainControlUI.class, ui, "TileImageLoader", held);

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

            // A diagram that does not wait for its pictures goes up meanwhile; one that waits is held, for a second
            // here, under the three the main window gives the pictures
            Components.waitFor(() -> firstPaint[0] != null, 1000);

            held.release.countDown();

            assertTrue(Components.waitFor(() -> firstPaint[0] != null, 20000),
                "precondition: the diagram at the new size was never painted");

            assertEquals(firstPaint[0].replaceFirst("^\\d+ squares, ", ""), "0 without their picture, 0 zero wide",
                "the first paint of the diagram at a new size showed " + firstPaint[0] + ".  It went on screen before "
                + "its pictures were decoded, so the operator sees squares missing and the squares after them closed "
                + "up, then everything jump into place one paint later");
        }
        finally
        {
            held.release.countDown();

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
