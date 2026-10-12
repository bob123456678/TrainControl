package ui;

import support.TestStationAddress;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.BusyDialog;

/**
 * The spinner that now stands in front of a Central Station sync, and the ways it could go wrong.
 *
 * TrainControlUI.syncWithCS2 puts a modal BusyDialog in front of the model's sync at sixteen call
 * sites, so that fetching from a station no longer freezes the interface in silence.  Modal and on the
 * event thread is a combination with sharp edges, and these are them:
 *
 *   - the slow work must run OFF the event thread, or nothing has been gained
 *   - the finishing work must run ON it, because that is where Swing belongs
 *   - run() must not return until the work is done, because every caller reads a result afterwards
 *   - work that THROWS must still dismiss the dialog - it has no close button and is application
 *     modal, so a leaked one freezes the program with no way out
 *   - called off the event thread it must still work, because a caller will get that wrong one day
 *
 * Needs a display.  Run through runone-ui.sh, or the battery's automatic retry.
 */
public class testBusyDialogInteraction
{
    /**
     * One model for the whole class.
     *
     * init() binds the Central Station's UDP port, and stop() does not give it back promptly - so three
     * tests each building their own model meant two of them failing on "Address already in use", which
     * looks exactly like a product fault and is not one.
     */
    private static org.traincontrol.marklin.MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("BusyDialog is a window - this needs a display");
        }

        // THE OPERATOR’S RAILWAY IS NOT THIS TEST’S TO OPEN (OB-111).
        //
        // This class never had a sandbox. `init` reads the layout preference and loads whatever it
        // names, which on his machine is his live layout - so every battery opened it here too.
        sandbox = support.LayoutSandbox.open();

        model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

        model.stop();
    }

    @org.testng.annotations.AfterClass
    public static void putTheLayoutPreferenceBack()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The work happens off the event thread, the finishing on it, and run() waits for both.
     */
    @Test(timeOut = 30000)
    public void testTheWorkIsOffTheEventThreadAndTheFinishingOnIt() throws Exception
    {
        final AtomicBoolean workOnEDT = new AtomicBoolean(true);
        final AtomicBoolean doneOnEDT = new AtomicBoolean(false);
        final AtomicInteger order = new AtomicInteger();
        final AtomicInteger workFinishedAt = new AtomicInteger();
        final AtomicInteger doneRanAt = new AtomicInteger();

        SwingUtilities.invokeAndWait(() ->
        {
            BusyDialog.run(null, "working",
                () ->
                {
                    workOnEDT.set(SwingUtilities.isEventDispatchThread());

                    // Long enough that a run() which did not wait would be caught out
                    try
                    {
                        Thread.sleep(300);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }

                    workFinishedAt.set(order.incrementAndGet());
                },
                () ->
                {
                    doneOnEDT.set(SwingUtilities.isEventDispatchThread());
                    doneRanAt.set(order.incrementAndGet());
                });
        });

        assertFalse(workOnEDT.get(),
            "the slow half ran on the event thread, which is the freeze this exists to remove");

        assertTrue(doneOnEDT.get(),
            "the finishing half ran off the event thread, where Swing must not be touched");

        assertTrue(workFinishedAt.get() > 0 && doneRanAt.get() > workFinishedAt.get(),
            "the finishing work ran before the slow work had finished");
    }

    /**
     * run() does not return until the work is done, because every caller reads a result afterwards.
     *
     * This is what makes the sync wrapper's "return result[0]" honest.
     */
    @Test(timeOut = 30000)
    public void testRunDoesNotReturnBeforeTheWorkHasFinished() throws Exception
    {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<String> readAfterwards = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() ->
        {
            BusyDialog.run(null, "working",
                () ->
                {
                    try
                    {
                        Thread.sleep(200);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }

                    result.set("answered");
                }, null);

            // Read the moment run() returns, which is the same thing the sync wrapper does
            readAfterwards.set(result.get());
        });

        assertEquals(readAfterwards.get(), "answered",
            "run() returned before the work had finished, so every caller that reads a result after it "
            + "- which is all of them - would read nothing");
    }

    /**
     * Work that throws still dismisses the dialog.
     *
     * The dialog is undecorated and application-modal: it has no close button, and nothing else can
     * dispose it.  A leaked one is a frozen program.
     *
     * TST-B10: this used to assert only that the `whenDone` callback ran - which BusyDialog.run
     * schedules together with the dispose, in the same `finally`, but is not the dispose itself.
     * Stripping the dialog out of `run` entirely, so no window is ever built or shown, would still
     * run `whenDone` and pass. This now captures the actual BusyDialog instance while it is showing
     * - the only class in the suite that so much as opens the word "dialog" for this class - and
     * checks that instance stopped being displayable, which `dispose()` is what causes.
     */
    @Test(timeOut = 30000)
    public void testWorkThatThrowsStillDismissesTheDialog() throws Exception
    {
        final AtomicBoolean finished = new AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<org.traincontrol.gui.BusyDialog> captured =
            new java.util.concurrent.atomic.AtomicReference<>();

        SwingUtilities.invokeAndWait(() ->
        {
            BusyDialog.run(null, "about to fail",
                () ->
                {
                    // WAITED FOR, not sampled (2026-08-29).
                    //
                    // BusyDialog.run starts this worker before setVisible has put the dialog on
                    // screen, so asking once at the top of the work looked before there was anything
                    // to see and captured null - and the assertion below then reported "no dialog was
                    // showing" about a dialog that was a millisecond away from showing.
                    //
                    // Bounded, because a dialog appearing is an acknowledgement rather than a railway
                    // event, and this file waits on acknowledgements with a deadline. If it never
                    // appears the wait ends and the assertion says exactly that.
                    org.traincontrol.gui.BusyDialog seen = null;

                    long until = System.currentTimeMillis() + 5000;

                    while (seen == null && System.currentTimeMillis() < until)
                    {
                        seen = findShowingBusyDialog();

                        if (seen == null)
                        {
                            try
                            {
                                Thread.sleep(20);
                            }
                            catch (InterruptedException stop)
                            {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }

                    captured.set(seen);

                    throw new RuntimeException("the station fell over");
                },
                () -> finished.set(true));
        });

        assertTrue(finished.get(),
            "work that threw left the dialog up - it is undecorated and application modal, so that is "
            + "a frozen program with no way out");

        org.traincontrol.gui.BusyDialog dialog = captured.get();

        assertNotNull(dialog, "no BusyDialog was showing while the work ran, so this proves nothing "
            + "about whether one was ever dismissed");

        assertFalse(dialog.isDisplayable(),
            "the BusyDialog that was showing while the work ran is still displayable afterwards - "
            + "work that threw left it up, which since it is undecorated and application modal with "
            + "no close button is a frozen program");
    }

    /**
     * The BusyDialog currently on screen, if any - found rather than handed back, because run()
     * builds it as a local and never exposes it.
     */
    private static org.traincontrol.gui.BusyDialog findShowingBusyDialog()
    {
        for (java.awt.Window w : java.awt.Window.getWindows())
        {
            if (w instanceof org.traincontrol.gui.BusyDialog && w.isShowing())
            {
                return (org.traincontrol.gui.BusyDialog) w;
            }
        }

        return null;
    }

    /**
     * A Central Station sync run in the ARRANGEMENT the sync wrapper uses.
     *
     * Worth being exact about what this does and does not prove, because an earlier version of this
     * comment was not.  It builds the same shape TrainControlUI.syncWithCS2 builds - a station served
     * over HTTP, the sync inside BusyDialog, the result read the moment run() returns - and shows that
     * the shape works: the databases are replaced off the event thread, the caller still gets its
     * answer, and no dialog is left standing.
     *
     * What it does NOT do is call the wrapper.  It restates the wrapper's intent in the test, so it
     * passed while the shipped method called itself instead of the model and every sync was a
     * StackOverflowError.  testTheWrapperItselfSyncs below is the one that drives the real method, and
     * testNoSelfRecursiveWrappers covers the textual mistake that caused it.
     */
    @Test(timeOut = 120000)
    public void testASyncRunsInsideTheDialogAndStillAnswers() throws Exception
    {
        com.sun.net.httpserver.HttpServer station =
            com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);

        station.createContext("/", exchange ->
        {
            java.io.File file = exchange.getRequestURI().getPath().endsWith("/lokomotive.cs2")
                ? new java.io.File("test/lokomotive.cs2")
                : exchange.getRequestURI().getPath().endsWith("/fahrstrassen.cs2")
                    ? new java.io.File("test/fahrstrassen.cs2")
                    : exchange.getRequestURI().getPath().endsWith("/magnetartikel.cs2")
                        ? new java.io.File("test/magnetartikel.cs2") : null;

            if (file == null || !file.isFile())
            {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] body = java.nio.file.Files.readAllBytes(file.toPath());

            exchange.sendResponseHeaders(200, body.length);

            try (java.io.OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });

        station.start();

        String was = TestStationAddress.get();

        TestStationAddress.set("127.0.0.1:" + station.getAddress().getPort());

        final int[] result = {-1};
        final AtomicBoolean syncOnEDT = new AtomicBoolean(true);

        // TST-B10: `model` is init()'d from the operator's own, real LocDB (test/README.md) - it is
        // never empty to begin with, so "the list is not empty" passed whether or not the sync ran.
        // `model` is also shared across every test in this class, and this is not the only one that
        // syncs it against the same fixture - so a straight "is the fixture's name here yet" check
        // would be vacuously true whenever a sibling test happened to run first. Removing one of the
        // fixture's own locomotives first gives a known-absent name regardless of what ran before,
        // for the sync below to bring back - in memory only, since nothing here ever calls
        // saveState (test/README.md).
        String probe = fixtureLocomotiveNames().get(0);

        model.deleteLoc(probe);

        assertFalse(model.getLocList().contains(probe),
            "precondition: \"" + probe + "\" should have just been removed");

        try
        {
            // Exactly the shape TrainControlUI.syncWithCS2 uses
            SwingUtilities.invokeAndWait(() ->
            {
                BusyDialog.run(null, "reading from the station",
                    () ->
                    {
                        syncOnEDT.set(SwingUtilities.isEventDispatchThread());
                        result[0] = model.syncWithCS2();
                    }, null);
            });

            assertFalse(syncOnEDT.get(),
                "the sync ran on the event thread, so the interface froze for the whole fetch - which "
                + "is the thing this arrangement exists to prevent");

            assertTrue(result[0] >= 0,
                "the sync reported failure against a station that was answering, got " + result[0]);

            assertTrue(model.getLocList().contains(probe),
                "\"" + probe + "\", one of the fixture's own locomotives just removed above, did not "
                + "come back after the sync - \"the list is not empty\" would have passed whether or "
                + "not the wrapper had done any work at all, since it reads the operator's own, "
                + "already-populated database");
        }
        finally
        {
            TestStationAddress.set(was);
            station.stop(0);
        }
    }

    /**
     * Called off the event thread it still works, rather than deadlocking.
     *
     * Off the EDT the dispose can be posted before the dialog is ever shown, which would leave a modal
     * window nothing is left alive to close.  The guard bounces the whole call to the event thread.
     */
    @Test(timeOut = 30000)
    public void testCalledOffTheEventThreadItStillCompletes() throws Exception
    {
        final AtomicBoolean done = new AtomicBoolean(false);

        assertFalse(SwingUtilities.isEventDispatchThread(), "precondition: this test is not on the EDT");

        BusyDialog.run(null, "from a worker", () -> { }, () -> done.set(true));

        // Bounced to the event thread, so it completes there rather than here - wait for it
        long deadline = System.currentTimeMillis() + 10000;

        while (!done.get() && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(50);
        }

        assertTrue(done.get(),
            "a call from a worker thread never completed, which off the event thread is a deadlock "
            + "rather than a slow answer");
    }

    /**
     * The wrapper itself: it syncs, and it does not call itself.
     *
     * The one test in this class that touches TrainControlUI.syncWithCS2.  Everything else here builds
     * the same arrangement by hand and therefore could not tell the difference between a wrapper that
     * forwards to the model and one that forwards to itself - which is what shipped, and what turned
     * every Central Station sync into a StackOverflowError.
     *
     * A StackOverflowError is an Error rather than an Exception, so it would not fail a test that only
     * catches Exception.  Caught explicitly here.
     */
    @Test(timeOut = 180000)
    public void testTheWrapperItselfSyncs() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the wrapper puts a dialog on screen - this needs a display");
        }

        com.sun.net.httpserver.HttpServer station = servingTheSampleFiles();

        station.start();

        String was = TestStationAddress.get();

        TestStationAddress.set("127.0.0.1:" + station.getAddress().getPort());

        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        // TST-B10: see testASyncRunsInsideTheDialogAndStillAnswers above for why this is a removed
        // fixture locomotive rather than "the list is not empty" - `model` is shared with that test,
        // so by the time this one runs the fixture's names may already all be present regardless of
        // whether THIS sync does anything at all.
        String probe = fixtureLocomotiveNames().get(0);

        model.deleteLoc(probe);

        assertFalse(model.getLocList().contains(probe),
            "precondition: \"" + probe + "\" should have just been removed");

        try
        {
            SwingUtilities.invokeAndWait(() -> ui[0] = new org.traincontrol.gui.TrainControlUI());

            ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            final int[] answer = {-2};
            final Throwable[] threw = {null};

            // Off the event thread, which is the branch the Sync menu item takes, and the one that
            // recursed immediately
            Thread caller = new Thread(() ->
            {
                try
                {
                    answer[0] = ui[0].syncWithCS2();
                }
                catch (Throwable t)
                {
                    threw[0] = t;
                }
            });

            caller.start();
            caller.join(120000);

            assertNull(threw[0], "the wrapper threw " + threw[0] + " - a wrapper that forwards to "
                + "itself rather than to the model recurses until the stack runs out");

            assertTrue(answer[0] >= 0,
                "the wrapper reported failure against a station that was answering, got " + answer[0]);

            assertTrue(model.getLocList().contains(probe),
                "\"" + probe + "\", one of the fixture's own locomotives just removed above, did not "
                + "come back - the wrapper returned without the sync having brought anything in");
        }
        finally
        {
            TestStationAddress.set(was);

            closeQuietly(ui[0]);

            station.stop(0);
        }
    }

    /**
     * A second sync while one is running is turned away rather than run alongside it.
     *
     * Moving the sync off the event thread created a hazard the old code could not have: the event
     * thread WAS the swap, so a second sync could not begin until the first returned.  A modal dialog
     * runs a nested event loop, which blocks input but still dequeues runnables already posted - and
     * three call sites reach the wrapper from invokeLater or a raw thread.  Two workers inside the same
     * two hundred lines of database reconciliation is worse than the freeze the dialog removed.
     */
    @Test(timeOut = 180000)
    public void testASecondSyncIsTurnedAway() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the wrapper puts a dialog on screen - this needs a display");
        }

        final java.util.concurrent.CountDownLatch holdTheFirst =
            new java.util.concurrent.CountDownLatch(1);

        com.sun.net.httpserver.HttpServer station =
            com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);

        // The station answers slowly, so the two calls genuinely overlap rather than happening to miss
        station.createContext("/", exchange ->
        {
            try
            {
                holdTheFirst.await(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        station.start();

        String was = TestStationAddress.get();

        TestStationAddress.set("127.0.0.1:" + station.getAddress().getPort());

        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            SwingUtilities.invokeAndWait(() -> ui[0] = new org.traincontrol.gui.TrainControlUI());

            ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            final java.util.concurrent.CountDownLatch firstIsInside =
                new java.util.concurrent.CountDownLatch(1);

            Thread first = new Thread(() ->
            {
                firstIsInside.countDown();
                ui[0].syncWithCS2();
            });

            first.start();

            assertTrue(firstIsInside.await(30, java.util.concurrent.TimeUnit.SECONDS));

            // Long enough for the first to be genuinely inside the sync before the second arrives
            Thread.sleep(3000);

            final int[] second = {0};

            Thread other = new Thread(() -> second[0] = ui[0].syncWithCS2());

            other.start();
            other.join(30000);

            // The CONSTANT, not a literal.  A refusal has a value of its own precisely because -1
            // already means "the sync failed" to every caller - so a test written against -1 would go
            // on passing if the two were ever confused again.
            assertEquals(second[0], org.traincontrol.gui.TrainControlUI.SYNC_ALREADY_RUNNING,
                "a second sync ran while the first was still inside the reconciliation - two workers "
                + "replacing the same databases at once, which the event thread used to make "
                + "impossible");

            assertNotEquals(second[0], -1,
                "a refusal must not look like a failure: every caller reads -1 as one, and the Sync "
                + "menu reports it as such");

            holdTheFirst.countDown();

            first.join(60000);
        }
        finally
        {
            holdTheFirst.countDown();

            TestStationAddress.set(was);

            closeQuietly(ui[0]);

            station.stop(0);
        }
    }

    /**
     * Disposes the window WITHOUT the closing handler, which calls saveState and would write the
     * operator's own locomotive database.
     */
    private static void closeQuietly(org.traincontrol.gui.TrainControlUI ui) throws Exception
    {
        if (ui == null) return;

        SwingUtilities.invokeAndWait(() -> ui.dispose());
    }

    /**
     * The locomotives test/lokomotive.cs2 actually holds, read directly rather than assumed - a
     * fixed count would only say the fixture had not changed, and a name typed here by hand could
     * silently drift from the file it is supposed to describe.
     */
    private static java.util.List<String> fixtureLocomotiveNames() throws Exception
    {
        java.util.List<org.traincontrol.marklin.MarklinLocomotive> fromFixture =
            new org.traincontrol.marklin.file.CS2File(null, null).parseLocomotives(
                org.traincontrol.marklin.file.CS2File.parseFile(new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("test/lokomotive.cs2"),
                        java.nio.charset.StandardCharsets.UTF_8))));

        assertFalse(fromFixture.isEmpty(),
            "the fixture should contain locomotives, or this proves nothing");

        java.util.List<String> names = new java.util.ArrayList<>();

        for (org.traincontrol.marklin.MarklinLocomotive l : fromFixture) names.add(l.getName());

        return names;
    }

    /**
     * The mock station, serving the sample files this suite keeps.
     */
    private static com.sun.net.httpserver.HttpServer servingTheSampleFiles() throws Exception
    {
        com.sun.net.httpserver.HttpServer station =
            com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);

        station.createContext("/", exchange ->
        {
            java.io.File file = exchange.getRequestURI().getPath().endsWith("/lokomotive.cs2")
                ? new java.io.File("test/lokomotive.cs2")
                : exchange.getRequestURI().getPath().endsWith("/fahrstrassen.cs2")
                    ? new java.io.File("test/fahrstrassen.cs2")
                    : exchange.getRequestURI().getPath().endsWith("/magnetartikel.cs2")
                        ? new java.io.File("test/magnetartikel.cs2") : null;

            if (file == null || !file.isFile())
            {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] body = java.nio.file.Files.readAllBytes(file.toPath());

            exchange.sendResponseHeaders(200, body.length);

            try (java.io.OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });

        return station;
    }
}
