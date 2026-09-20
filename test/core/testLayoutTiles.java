package core;

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
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.RouteEditorFrame;
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

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before init(), not after: init() reads TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF as soon as
        // showUI constructs the real window, so a sandbox opened afterwards protects nothing.  Without
        // this, showUI = true below opens and can write to Adam's own railway (OB-111), and can also
        // raise the modal "create a track diagram?" prompt that no test here will ever click, stalling
        // the whole battery.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();
        assertNotNull(ui, "the UI must be available for these tests");

        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);

        if (sandbox != null) sandbox.close();
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
    // UIX-B1 and UIX-B2, the 2026-09-19 review round
    // ------------------------------------------------------------------------------------------------

    /**
     * The two highlights do not put each other's stale picture back (UIX-B2).
     *
     * A tile has two independent "restore the icon later" timers over it: the accessory highlight in `setImage`,
     * 2.25 seconds when a switch or signal changes from anywhere but a click here, and the flash the route editor's
     * *Highlight on Diagram* starts for five seconds.  Each captured whatever icon it found and set it back when it
     * fired, so whichever fired last won - and a switch thrown during a flash ended up drawn in the position it had
     * BEFORE it was thrown, staying that way until that accessory changed again.
     *
     * Both orders are asked, because each one is a separate restore and the fix is a separate line.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheFlashAndTheAccessoryHighlightDoNotUndoEachOther() throws Exception
    {
        // WHAT IS ASKED IS WHICH RESTORE IS STILL ARMED, not which picture is showing.  A tile's image is made on a
        // worker and set through invokeLater, so the icon at a given instant on a bare label is not something to pin;
        // what goes wrong is a timer left holding a picture that has since been overtaken, and that is what is here.

        // ONE: the flash is up and the accessory changes.  The flash holds the position the switch was in BEFORE it
        // was thrown, so it must be given up rather than allowed to restore over the new picture.
        LayoutLabel label = warmedSignalLabel();

        SwingUtilities.invokeAndWait(() -> label.flashHighlight());

        assertTrue(label.isFlashOutstanding(), "precondition: the flash did not start");

        SwingUtilities.invokeAndWait(() -> label.updateImage(true));

        // THE HIGHLIGHT IS LAID ON IN A LATER PASS: setImage queues its work with invokeLater, so the call above has
        // only queued it.
        assertTrue(awaitHighlight(label),
            "precondition: the accessory change did not highlight the tile, so nothing below is tested");

        assertFalse(label.isFlashOutstanding(),
            "the flash is still armed after the accessory changed, so it will draw the switch in the position it was"
            + " in before it was thrown - and leave it that way until that accessory changes again");

        // TWO: the accessory highlight is up and a flash starts.  The flash must capture the tile's own picture, not
        // the yellow-washed copy of it - so the highlight is ended first and only one restore is ever outstanding.
        LayoutLabel second = warmedSignalLabel();

        SwingUtilities.invokeAndWait(() -> second.updateImage(true));

        assertTrue(awaitHighlight(second), "precondition: the accessory highlight never went up");

        SwingUtilities.invokeAndWait(() -> second.flashHighlight());

        assertTrue(second.isFlashOutstanding(), "precondition: the flash did not start");

        assertFalse(second.isAccessoryHighlightOutstanding(),
            "the accessory highlight is still armed under the flash, so the flash captured the washed picture and"
            + " will put it back - the tile stays yellow until that accessory changes again");

        SwingUtilities.invokeAndWait(() -> second.endFlash());
    }

    /**
     * A second accessory highlight stops the first, so only one restore ever fires (VC2-C3).
     *
     * A three-way drives twice, 350 ms apart, and each drive highlights the tile.  The first timer used to be left
     * running: it fired 2.25 seconds after ITS start, part-way through the second highlight, and put the plain icon
     * back over it - the tile stopped looking highlighted while it still was.  Counted rather than looked at,
     * because what distinguishes the two is how many restores happen, not which picture is up.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testASecondHighlightStopsTheFirstsRestore() throws Exception
    {
        LayoutLabel label = warmedSignalLabel();

        SwingUtilities.invokeAndWait(() -> label.updateImage(true));

        assertTrue(awaitHighlight(label), "precondition: the first highlight never went up");

        int before = label.accessoryRestores();

        // THE SECOND DRIVE, well inside the first highlight's 2250 ms.
        SwingUtilities.invokeAndWait(() -> label.updateImage(true));

        assertTrue(awaitHighlight(label), "precondition: the second highlight never went up");

        // Long enough for BOTH timers to have fired, if both were still armed.
        Thread.sleep(3200);

        SwingUtilities.invokeAndWait(() -> { });

        int restores = label.accessoryRestores() - before;

        assertEquals(restores, 1,
            "the tile was put back " + restores + " times after the second highlight went up; two means the first"
            + " highlight's timer was left running and fired over the second one, and none means the second"
            + " highlight never ended");
    }

    /**
     * Any square whose picture is replaced gives up an outstanding flash, not only a switch or a signal (SVB-B1).
     *
     * The first fix dropped the flash inside the accessory-highlight branch, which two ordinary cases never reach:
     * a switch thrown by clicking its own flashed tile - the click exclusion skips that branch - and a square the
     * route editor flashes that is not a switch or signal at all, such as an s88 named by a condition.  In both the
     * flash timer was left holding the picture the tile had before, and put it back seconds later: a switch drawn in
     * the position it was thrown out of, or a sensor drawn clear with a train standing on it.
     *
     * Asked of a FEEDBACK square, which takes no accessory branch at all.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAnyReplacedPictureGivesUpAnOutstandingFlash() throws Exception
    {
        final LayoutDiagramComponent sensor = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.FEEDBACK, 0, 0, 0, 0, 7, 12, MM2);

        final JPanel parent = new JPanel();
        final AtomicReference<LayoutLabel> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> ref.set(new LayoutLabel(sensor, parent, 30, ui, false)));

        final LayoutLabel label = ref.get();

        long deadline = System.currentTimeMillis() + 10000;

        while (label.getIcon() == null && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        assertNotNull(label.getIcon(), "the sensor tile never drew");

        SwingUtilities.invokeAndWait(() -> label.flashHighlight());

        assertTrue(label.isFlashOutstanding(), "precondition: the flash did not start");

        // THE PICTURE IS REPLACED.  `updateImage(false)` returns without doing anything unless the image NAME
        // changed, and a bare feedback component with nothing bound to it always draws the same picture - so
        // asked that way this proved nothing and the flash simply timed out inside the wait (VB2-B2).
        // `updateImage(true)` replaces it, which is what an occupancy change does; a FEEDBACK square takes no
        // accessory-highlight branch at all, so what is tested is the path that branch does not cover.
        SwingUtilities.invokeAndWait(() -> label.updateImage(true));

        assertFalse(label.isAccessoryHighlightOutstanding(),
            "precondition: this square took the accessory-highlight branch, so it is not the path this is for");

        // BOUNDED WELL INSIDE THE FLASH'S OWN HOLD, which is HIGHLIGHT_DURATION - 2250 ms (VB2-B2, corrected by
        // VC2-C1).  A flash that merely timed out cannot pass this, and the elapsed time is asserted below so that
        // a slow machine says so rather than passing for the wrong reason.
        long waitingFrom = System.currentTimeMillis();

        long armed = waitingFrom + 1200;

        while (label.isFlashOutstanding() && System.currentTimeMillis() < armed)
        {
            Thread.sleep(20);
        }

        long waited = System.currentTimeMillis() - waitingFrom;

        assertFalse(label.isFlashOutstanding(),
            "the flash is still armed after this square was redrawn, so it will put the old picture back - a sensor"
            + " drawn clear under a standing train, or a switch drawn in the position it was thrown out of");

        assertTrue(waited < 2000,
            "the flash went quiet after " + waited + " ms, which is long enough to be its own 2250 ms hold running"
            + " out rather than the redraw giving it up");
    }

    /**
     * Waits for an accessory highlight to go up, which happens a pass or two after `updateImage` is called.
     *
     * Since SVT-B2 the timer nulls the field when it fires, so this cannot answer yes about a highlight that ended
     * long ago - which is what made the second half of the flash test vacuous.
     *
     * @param label the label
     * @return whether it went up
     * @throws Exception while waiting
     */
    private static boolean awaitHighlight(LayoutLabel label) throws Exception
    {
        long deadline = System.currentTimeMillis() + 5000;

        while (!label.isAccessoryHighlightOutstanding() && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        return label.isAccessoryHighlightOutstanding();
    }

    /**
     * A drawn signal tile whose picture has settled: its image loaded, one highlight run and expired.
     *
     * **The first highlight on a fresh label is not the one to measure.**  The picture is made on a worker and set
     * through `invokeLater`, so the overlay and the image can land in either order while a label is new - which is
     * not what happens on a drawn diagram, where the image is already there and the switch changing is one event.
     * One highlight is run and waited out, so what follows is the ordinary case.
     *
     * @return the label
     * @throws Exception from the event thread
     */
    private static LayoutLabel warmedSignalLabel() throws Exception
    {
        LayoutLabel label = drawnSignalLabel();

        SwingUtilities.invokeAndWait(() -> label.updateImage(true));

        awaitHighlight(label);

        // The highlight is 2250 ms and its restore is a Swing Timer; waited out, then confirmed gone rather than
        // assumed gone (SVT-B2).
        long deadline = System.currentTimeMillis() + 10000;

        while (label.isAccessoryHighlightOutstanding() && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(50);
        }

        assertFalse(label.isAccessoryHighlightOutstanding(), "the warm-up highlight never ended");

        return label;
    }

    /**
     * Waits for the label's icon to stop being the one it was, because the picture arrives on a worker.
     *
     * @param label the label
     * @param previous what it was showing
     * @return what it is showing now
     * @throws Exception while waiting
     */
    private static Icon awaitIconChange(LayoutLabel label, Icon previous) throws Exception
    {
        long deadline = System.currentTimeMillis() + 10000;

        while (label.getIcon() == previous && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        return label.getIcon();
    }

    /**
     * A signal tile with its picture loaded, on a label that is not an editor's.
     *
     * The highlight branch in `setImage` requires `isSignal()` or `isSwitch()`, `edit == false`, and an icon already
     * set - the constructor loads it asynchronously, so it is waited for.
     *
     * @return the label
     * @throws Exception from the event thread
     */
    private static LayoutLabel drawnSignalLabel() throws Exception
    {
        final LayoutDiagramComponent component = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.SIGNAL, 0, 0, 0, 0, 5, 10, MM2);

        final JPanel parent = new JPanel();
        final AtomicReference<LayoutLabel> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> ref.set(new LayoutLabel(component, parent, 30, ui, false)));

        LayoutLabel label = ref.get();

        long deadline = System.currentTimeMillis() + 10000;

        while (label.getIcon() == null && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        assertNotNull(label.getIcon(), "the tile's initial icon should have loaded");

        return label;
    }

    /**
     * The exit itself asks every open window, and a route editor holding unsaved typing can refuse it (UIX-B1).
     *
     * `WindowClosed` asked the open layout editor whether it might settle (OB-070) and never asked the route
     * editor, so File > Exit and the main window's X disposed it with the process and took whatever had been typed
     * with them - silently, because that window's own discard question lives on its X and on Escape.
     *
     * **The decision, not the predicate under it** (SVT-B1).  The handler ends in `System.exit`, so what is asked
     * here is `everyOpenWindowMaySettle` - everything the exit does before it saves anything - with the discard
     * dialog answered through the route editor's test hook.  Answering No must stop the exit; answering Yes must
     * let it carry on.
     *
     * Here rather than in a class of its own because it needs exactly what this one already opens: the real main
     * window, on a sandbox.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheExitAsksEveryOpenWindow() throws Exception
    {
        assertFalse(ui.routeEditorHasUnsavedWork(), "precondition: something is already holding unsaved work");

        assertTrue(ui.everyOpenWindowMaySettle(), "precondition: the exit is already blocked by something else");

        final AtomicReference<RouteEditorFrame> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> ref.set(new RouteEditorFrame(ui, null, null)));

        final RouteEditorFrame editor = ref.get();

        Field field = TrainControlUI.class.getDeclaredField("routeEditor");
        field.setAccessible(true);

        Object was = field.get(ui);

        try
        {
            SwingUtilities.invokeAndWait(() -> editor.setVisible(true));

            field.set(ui, editor);

            assertFalse(editor.hasUnsavedWork(), "a window nobody has typed into has nothing to lose");
            assertTrue(ui.everyOpenWindowMaySettle(), "an untouched route editor stopped the exit");

            // WHAT TYPING LOOKS LIKE from outside: the capture door appends a command row, which is one of the six
            // things `stateSignature` covers.
            SwingUtilities.invokeAndWait(() -> editor.appendCommand("Switch 90,turn"));

            assertTrue(editor.hasUnsavedWork(), "a command typed into the route editor is not counted as unsaved work");
            assertTrue(ui.routeEditorHasUnsavedWork(), "the exit cannot see that the route editor has unsaved work");

            RouteEditorFrame.discardAnswerForTest = Boolean.FALSE;

            assertFalse(ui.everyOpenWindowMaySettle(),
                "the exit carries on although the route editor was asked and said no, so the typing goes with the"
                + " process");

            RouteEditorFrame.discardAnswerForTest = Boolean.TRUE;

            assertTrue(ui.everyOpenWindowMaySettle(), "the exit is refused although the route editor said yes");
        }
        finally
        {
            RouteEditorFrame.discardAnswerForTest = null;

            field.set(ui, was);

            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * The route editor notices its route being changed or deleted underneath it (GUX-B1).
     *
     * This window is not modal and does not hold the route: Enable/Disable in the route list, a delete, an import or
     * another editor can change it while it is open, and Save is a delete-and-re-add of everything the window holds.
     * So a toggle made while an editor was open was silently undone by that editor's Save, and after a delete the
     * Save simply failed with a message that had no remedy - the typing could not be kept at all.
     *
     * Asked of the question Save now puts, on all three states.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheRouteEditorNoticesItsRouteMoving() throws Exception
    {
        final String name = "Moving route";
        final int id = 7703;

        List<org.traincontrol.base.RouteCommand> commands = new ArrayList<>();

        commands.add(org.traincontrol.base.RouteCommand.RouteCommandAccessory(93, MM2, true));

        model.newRoute(name, id, commands, 0, org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            false, null);

        final AtomicReference<RouteEditorFrame> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> ref.set(new RouteEditorFrame(ui, name, model.getRoute(name))));

        final RouteEditorFrame editor = ref.get();

        try
        {
            assertNull(editor.howTheRouteMoved(), "nothing has touched the route and the editor says it moved");

            // WHAT THE ROUTE LIST'S OWN TOGGLE DOES: a delete and a re-add with the flag flipped.
            model.editRoute(name, name, commands, 0,
                org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

            assertEquals(editor.howTheRouteMoved(), "changed",
                "the route was enabled while this editor was open and the editor cannot tell, so its Save switches"
                + " it off again without saying anything");

            // A RENAME IS NOT A DELETION (OP2-C9).
            //
            // This looked the route up by NAME only, so a rename under the window read as "gone" - and the
            // message that followed named the two causes that had not happened, deleted or replaced by an
            // import, and offered save-as-new: a second route beside the renamed one, carrying a fresh id
            // that no route tile and no autonomy selection follows.
            model.editRoute(name, "Moving route renamed", commands, 0,
                org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

            assertNull(model.getRoute(name), "precondition: the rename left the old name in the database");

            assertEquals(editor.howTheRouteMoved(), "renamed",
                "the route was renamed while this editor was open and the editor reports it as gone, so Save"
                + " offers to add it back as a new route - with a new id that the diagram's route tiles and"
                + " the autonomy selection do not follow");

            assertEquals(editor.nameNowHeldById(), "Moving route renamed",
                "the editor cannot say what the route is called now, so it has nothing to offer to save onto");

            // AND PUT BACK, so the rest of this method is about the name it started with.
            model.editRoute("Moving route renamed", name, commands, 0,
                org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

            // A RENAME IS A CHANGE TOO (OP2-B5).  `Route.locomotiveRenamed` rewrites the route's commands and
            // conditions IN PLACE, so nothing about the route object changes identity - and the signature used
            // to compare the conditions by object, which made a rename invisible and let Save put the dead name
            // back.
            model.newMM2Locomotive("Moving loc", 62);

            List<org.traincontrol.base.RouteCommand> driving = new ArrayList<>();

            driving.add(org.traincontrol.base.RouteCommand.RouteCommandAccessory(93, MM2, true));
            driving.add(org.traincontrol.base.RouteCommand.RouteCommandLocomotiveSpeed("Moving loc", 20));

            model.editRoute(name, name, driving, 0,
                org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            final AtomicReference<RouteEditorFrame> second = new AtomicReference<>();

            SwingUtilities.invokeAndWait(() -> second.set(new RouteEditorFrame(ui, name, model.getRoute(name))));

            try
            {
                assertNull(second.get().howTheRouteMoved(), "precondition: the second editor starts out of step");

                assertTrue(model.renameLoc("Moving loc", "Moved loc"), "precondition: the rename was refused");

                assertEquals(second.get().howTheRouteMoved(), "changed",
                    "a locomotive this route drives was renamed while the editor was open and the editor cannot"
                    + " tell, so its Save writes the old name back into every command that drives it");
            }
            finally
            {
                SwingUtilities.invokeAndWait(() -> second.get().dispose());

                try { model.deleteLoc("Moved loc"); } catch (Exception ignored) { }
                try { model.deleteLoc("Moving loc"); } catch (Exception ignored) { }
            }

            model.deleteRoute(name);

            assertEquals(editor.howTheRouteMoved(), "gone",
                "the route was deleted while this editor was open and the editor cannot tell, so its Save fails with"
                + " a message that leaves nowhere to put the typing");
        }
        finally
        {
            try { model.deleteRoute(name); } catch (Exception ignored) { }

            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
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
