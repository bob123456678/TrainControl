import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinFeedback;

/**
 * Track diagram tile refreshing, across the two threads that drive it.
 *
 * Tiles are registered from the EDT as diagram windows open and pages are switched, and refreshed from
 * a Central Station message thread whenever a device changes state.  Two defects came out of that:
 *
 *   - the tile collection was a plain HashSet, and MarklinFeedback additionally spawned a thread per
 *     refresh, so iteration and registration raced.  The ConcurrentModificationException surfaced inside
 *     a thread nobody joined, so those tiles just silently stopped refreshing
 *   - LayoutLabel's temporary change-highlight applied its icon from a raw thread, mutating a Swing
 *     component off the EDT - the one place in that class which did not marshal its work
 *
 * Requires a display (showUI = true), like testAutonomyPathValidation.
 */
public class testLayoutTiles
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    private static Thread.UncaughtExceptionHandler previousHandler;

    private static final Accessory.accessoryDecoderType MM2 = Accessory.accessoryDecoderType.MM2;

    // Tiles pinned to a visible parent (never pruned) - these make each iteration long enough to race
    private static final int FILLER_TILES = 8000;

    // Tiles on a hidden parent - updateTiles prunes these, and the adder thread puts them back, which
    // is what produces the sustained structural modification during iteration
    private static final int CHURN_TILES = 4000;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();
        assertNotNull(ui, "the UI must be available for these tests");

        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @AfterClass
    public static void tearDownClass()
    {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
    }

    // ------------------------------------------------------------------------------------------------
    // B9 - MarklinFeedback.updateTiles races with addTile
    // ------------------------------------------------------------------------------------------------

    /**
     * Structural half, deterministic: the tile collection must be thread-safe, because addTile is
     * called from the EDT while updateTiles iterates from a Central Station message thread.  It used to
     * be a plain HashSet.
     */
    @Test
    public void testTileCollectionIsThreadSafe() throws Exception
    {
        MarklinFeedback fb = new MarklinFeedback(model, 8901, null);

        Field tilesField = MarklinFeedback.class.getDeclaredField("tiles");
        tilesField.setAccessible(true);
        Object tiles = tilesField.get(fb);

        assertFalse(tiles instanceof HashSet,
            "tiles must not be a plain HashSet - " + tiles.getClass().getName());
    }

    /**
     * Behavioural half: run the real production shapes against each other - repeated updateTiles()
     * calls, as the CS feedback path does, while tiles are added, as the EDT does when a track diagram
     * window opens or a page is switched.  Neither may fail.
     *
     * This used to throw ConcurrentModificationException, inside a thread MarklinFeedback created and
     * never joined - so in production that thread simply died and those tiles stopped refreshing, with
     * nothing reported.  A default uncaught-exception handler is installed here to catch any such
     * escape, since a failure in a spawned thread would otherwise pass silently.
     */
    @Test
    public void testConcurrentUpdateAndAddIsSafe() throws Exception
    {
        final MarklinFeedback fb = new MarklinFeedback(model, 8902, null);
        final AtomicReference<Throwable> captured = new AtomicReference<>();

        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
        {
            if (e instanceof ConcurrentModificationException)
            {
                captured.compareAndSet(null, e);
            }
        });

        final JPanel visibleParent = new JPanel();
        final JPanel hiddenParent = new JPanel();
        hiddenParent.setVisible(false);

        assertTrue(visibleParent.isVisible(), "filler tiles must never be pruned");
        assertFalse(hiddenParent.isVisible(), "churn tiles must be pruned by updateTiles");

        // A null component keeps LayoutLabel cheap - updateImage() is a no-op for it, which isolates
        // the test to the collection access itself.
        final List<LayoutLabel> churn = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            for (int i = 0; i < FILLER_TILES; i++)
            {
                fb.addTile(new LayoutLabel(null, visibleParent, 30, ui, false));
            }

            for (int i = 0; i < CHURN_TILES; i++)
            {
                churn.add(new LayoutLabel(null, hiddenParent, 30, ui, false));
            }
        });

        final AtomicBoolean stop = new AtomicBoolean(false);

        // Continuously re-inserts the churn tiles.  Because updateTiles prunes them (their parent is
        // not visible), each re-insert is a genuine structural modification of the set.
        Thread adder = new Thread(() ->
        {
            while (!stop.get())
            {
                for (LayoutLabel l : churn)
                {
                    if (stop.get())
                    {
                        return;
                    }

                    fb.addTile(l);
                }
            }
        });

        adder.setDaemon(true);
        adder.start();

        long deadline = System.currentTimeMillis() + 10000;
        int rounds = 0;

        while (System.currentTimeMillis() < deadline)
        {
            fb.updateTiles();
            rounds++;

            if (captured.get() != null)
            {
                break;
            }
        }

        stop.set(true);
        adder.join(2000);

        assertTrue(rounds > 10, "the test should have completed several refresh rounds (was " + rounds + ")");

        assertNull(captured.get(),
            "concurrent updateTiles()/addTile() must not fail - " + captured.get());
    }

    // ------------------------------------------------------------------------------------------------
    // B10 - LayoutLabel mutates Swing state off the EDT
    // ------------------------------------------------------------------------------------------------

    /**
     * Every icon change must happen on the EDT, including the temporary change-highlight.
     *
     * That highlight used to be applied from a raw Thread - overlay, sleep, restore - which mutated a
     * Swing component off the EDT, the one place in LayoutLabel that did not marshal its work.  It now
     * applies the overlay inline (this code already runs on the EDT) and schedules the restore with a
     * Swing Timer, which also fires there.  JLabel.setIcon fires an "icon" property change, so the
     * thread doing it can be observed directly.
     */
    @Test
    public void testHighlightSetsTheIconOnTheEventDispatchThread() throws Exception
    {
        // A signal tile: the highlight branch requires isSignal() or isSwitch(), and edit == false.
        final LayoutDiagramComponent component = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.SIGNAL, 0, 0, 0, 0, 5, 10, MM2);

        final JPanel parent = new JPanel();
        final AtomicReference<LayoutLabel> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> ref.set(new LayoutLabel(component, parent, 30, ui, false)));

        final LayoutLabel label = ref.get();

        // The constructor loads the icon asynchronously; the highlight branch needs an icon already set.
        long deadline = System.currentTimeMillis() + 10000;

        while (label.getIcon() == null && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        assertNotNull(label.getIcon(), "the tile's initial icon should have loaded");

        final AtomicInteger onEdt = new AtomicInteger(0);
        final AtomicInteger offEdt = new AtomicInteger(0);

        label.addPropertyChangeListener("icon", new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if (SwingUtilities.isEventDispatchThread())
                {
                    onEdt.incrementAndGet();
                }
                else
                {
                    offEdt.incrementAndGet();
                }
            }
        });

        // What the CS accessory echo does when a signal changes state.
        label.updateImage(true);

        // Long enough for the overlay, the highlight duration, and the scheduled restore
        deadline = System.currentTimeMillis() + 8000;

        while (onEdt.get() < 3 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        // The refreshed icon, the highlight overlay, and the restore afterwards
        assertTrue(onEdt.get() >= 3,
            "expected the refresh, the highlight and the restore (saw " + onEdt.get() + ")");

        assertEquals(offEdt.get(), 0,
            "no icon change may be applied to a Swing component off the EDT");
    }

    /**
     * Diagram switching runs off the event thread, one action at a time.
     *
     * The click handler used to do all of its work inside SwingUtilities.invokeLater, so every sleep it
     * contains ran on the event thread: 350ms between a three-way's two drives, and a further second
     * when the same click also turns the track power on.  The UI was frozen for all of it - including
     * the repaint of the drive that had already moved.
     *
     * Both halves are asserted.  Off the event thread is the fix.  One at a time is what the event
     * queue used to provide for free, and it has to survive the move: a three-way is two sends with a
     * load-bearing gap between them, and another tile's click must not land in that gap.
     */
    @Test
    public void testDiagramSwitchingRunsOffTheEventThreadOneAtATime() throws Exception
    {
        AtomicBoolean ranOnEventThread = new AtomicBoolean(false);
        AtomicBoolean overlapped = new AtomicBoolean(false);
        AtomicInteger inFlight = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(2);

        Runnable action = () ->
        {
            if (SwingUtilities.isEventDispatchThread())
            {
                ranOnEventThread.set(true);
            }

            if (inFlight.incrementAndGet() > 1)
            {
                overlapped.set(true);
            }

            try
            {
                // Long enough that a second action would have to overlap this one if nothing were
                // serialising them - a thread per click would fail here
                Thread.sleep(120);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }

            inFlight.decrementAndGet();
            done.countDown();
        };

        // Submitted from the event thread, which is where a click submits them from
        SwingUtilities.invokeAndWait(() ->
        {
            LayoutLabel.submitSwitching(action);
            LayoutLabel.submitSwitching(action);
        });

        assertTrue(done.await(5, TimeUnit.SECONDS), "both switching actions should have run");

        assertFalse(ranOnEventThread.get(),
            "switching blocks - a three-way sleeps between its two drives - so it must not run on the "
            + "event thread");

        assertFalse(overlapped.get(),
            "two switching actions overlapped: a three-way's two sends must not interleave with another "
            + "tile's, which the event queue used to guarantee");
    }

    /**
     * An exception escaping a switching action stays visible.
     *
     * While switching ran on the event thread, anything that escaped it reached the default handler
     * and printed.  Moving the work to an executor put that at risk: submit() captures the throwable
     * into a Future, and this dispatch keeps no Future to read it back from, so the exception would
     * have vanished with no sign of it anywhere - the failure mode MarklinRoute's monitor loop already
     * carries a comment about.  execute() puts it back on the thread's normal path.
     */
    @Test
    public void testAnExceptionEscapingASwitchingActionIsNotSwallowed() throws Exception
    {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();

        AtomicReference<Throwable> seen = new AtomicReference<>();
        CountDownLatch reported = new CountDownLatch(1);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
        {
            seen.set(throwable);
            reported.countDown();
        });

        try
        {
            LayoutLabel.submitSwitching(() ->
            {
                throw new IllegalStateException("switching blew up");
            });

            assertTrue(reported.await(5, TimeUnit.SECONDS),
                "the exception was swallowed - submit() captures it into a Future nobody reads");

            assertEquals(seen.get().getMessage(), "switching blew up");
        }
        finally
        {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }
}
