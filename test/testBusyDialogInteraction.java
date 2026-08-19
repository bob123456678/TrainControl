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
    @BeforeClass
    public static void setUpClass()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("BusyDialog is a window - this needs a display");
        }
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
     */
    @Test(timeOut = 30000)
    public void testWorkThatThrowsStillDismissesTheDialog() throws Exception
    {
        final AtomicBoolean finished = new AtomicBoolean(false);

        SwingUtilities.invokeAndWait(() ->
        {
            BusyDialog.run(null, "about to fail",
                () ->
                {
                    throw new RuntimeException("the station fell over");
                },
                () -> finished.set(true));
        });

        assertTrue(finished.get(),
            "work that threw left the dialog up - it is undecorated and application modal, so that is "
            + "a frozen program with no way out");
    }

    /**
     * The real thing: a Central Station sync, run the way the sync wrapper runs it.
     *
     * This is the interaction that matters, and the one the wrapper exists for.  A station is served
     * over HTTP, the sync runs inside BusyDialog exactly as TrainControlUI.syncWithCS2 arranges it, and
     * the result is read the moment run() returns - which is what every one of the sixteen call sites
     * does.  What it proves: the databases are replaced off the event thread, the caller still gets its
     * answer, and the interface is not left holding a dialog.
     *
     * Built here rather than by constructing TrainControlUI, which would bring up the whole application
     * and, on close, write the operator's locomotive database.
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

        org.traincontrol.marklin.MarklinControlStation model =
            org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

        model.stop();

        String was = org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS;

        org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS =
            "127.0.0.1:" + station.getAddress().getPort();

        final int[] result = {-1};
        final AtomicBoolean syncOnEDT = new AtomicBoolean(true);

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

            assertFalse(model.getLocList().isEmpty(),
                "the sync brought in no locomotives, so the wrapper returned before the work was done");
        }
        finally
        {
            org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS = was;
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
}
