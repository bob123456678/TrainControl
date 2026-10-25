package org.traincontrol.gui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The coalesced worker of the covered marks, the caption visibility and the Return Home triage: one worker on a
 * question at a time, kept going while the answer is out of date, so the last ask always lands.  The flags, the
 * subject and one pass of the answer stay with each owner.
 */
final class CoalescedAsk
{
    private CoalescedAsk()
    {
    }

    /**
     * Puts one worker on the job unless one is already on it; the caller has set `dirty`.  Asks arrive faster than they
     * are served and dropping one leaves a stale answer (OB-180), so the worker runs `pass` until `dirty` stays clear: a
     * flag rather than a queue (`repaintAutoLocList` says why).  `settle` gives the job up; `reAsk` serves the gap.
     */
    static void ask(AtomicBoolean inFlight, AtomicBoolean dirty, ExecutorService pool, Runnable pass, Runnable settle,
        Runnable reAsk)
    {
        if (!inFlight.compareAndSet(false, true)) return;

        try
        {
            pool.submit(() -> work(dirty, pass, settle, reAsk));
        }
        catch (Exception refused)
        {
            // Submitting can be refused - the pool is shut down when the window closes - and the flag
            // is set by then.  Clearing it here keeps the latch from coming back at one remove.
            settle.run();
        }
    }

    private static void work(AtomicBoolean dirty, Runnable pass, Runnable settle, Runnable reAsk)
    {
        try
        {
            // Cleared BEFORE the pass that answers it, so an ask that arrives during the pass sets it
            // again and is served by the next turn of this loop rather than being lost to it.
            while (dirty.compareAndSet(true, false)) pass.run();
        }
        finally
        {
            settle.run();

            // AN ASK THAT ARRIVED IN THE GAP between the loop's last read of the flag and the settle
            // above found a worker still in flight, so it neither ran nor was picked up; on the last
            // refresh of a run nothing else asks.
            if (dirty.get()) reAsk.run();
        }
    }
}
