package org.traincontrol.gui;

/**
 * A question answered off the event thread whose caller can wait for the answer to settle: the settle and the bounded
 * await, on a monitor the owner keeps (FR-136).  The ask, the answer and what counts as outstanding stay with each user.
 */
abstract class SettledQuestion
{
    /** The owner's monitor, the one `settle` and `awaitSettled` must agree on. */
    abstract Object settledLock();

    /** Whether an ask is still outstanding; read under the lock. */
    abstract boolean outstanding();

    /** Gives the job up; called under the lock, just before the waiters are woken. */
    abstract void giveUp();

    /**
     * Gives the job up and wakes anybody waiting, both under the one monitor `awaitSettled` waits on, so a waiter
     * cannot find an ask outstanding and go to sleep in the instant between the two.
     */
    final void settle()
    {
        Object lock = settledLock();

        synchronized (lock)
        {
            giveUp();

            lock.notifyAll();
        }
    }

    /**
     * Waits at most `millis` until nothing is outstanding; false when the wait ran out or was interrupted.  BOUNDED:
     * a wait with no deadline would turn a dead worker into a hung window rather than a stale answer.
     */
    final boolean awaitSettled(long millis)
    {
        long deadline = System.currentTimeMillis() + millis;

        Object lock = settledLock();

        synchronized (lock)
        {
            while (outstanding())
            {
                long left = deadline - System.currentTimeMillis();

                if (left <= 0) return false;

                try
                {
                    lock.wait(left);
                }
                catch (InterruptedException stopped)
                {
                    Thread.currentThread().interrupt();

                    return false;
                }
            }
        }

        return true;
    }
}
