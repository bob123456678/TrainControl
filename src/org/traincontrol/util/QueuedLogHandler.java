package org.traincontrol.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Hands every record to another handler from a thread of its own, so the thread that logs never waits on the output:
 * the Central Station's receive threads log, and a console that stops taking output would stop them (OB-421).
 *
 * Records are written in the order they were published, each with its own time stamp.  A full queue drops the record
 * rather than wait, and the next line written says how many were dropped.  {@link #flush()} waits, for a bounded time,
 * until everything published before it has been written.
 */
public final class QueuedLogHandler extends Handler
{
    /** How many records wait for a console that is not taking output before the newest are dropped. */
    public static final int CAPACITY = 10000;

    /** The longest flush and close wait: an exit is never held up by a console that is not taking output. */
    private static final long WAIT_MS = 2000;

    private final Handler target;

    private final BlockingQueue<Entry> queue;

    /** Dropped since the last record that was queued. */
    private final AtomicLong dropped = new AtomicLong();

    private final AtomicLong queued = new AtomicLong();

    private final Object progress = new Object();

    /** Guarded by progress. */
    private long written = 0;

    private final Thread writer;

    private volatile boolean closed = false;

    private static final class Entry
    {
        final LogRecord record;

        /** Records dropped just before this one. */
        final long droppedBefore;

        Entry(LogRecord record, long droppedBefore)
        {
            this.record = record;
            this.droppedBefore = droppedBefore;
        }
    }

    /** @param target the handler that writes, called only from this handler's own thread */
    public QueuedLogHandler(Handler target)
    {
        this.target = target;
        this.queue = new ArrayBlockingQueue<>(CAPACITY);
        this.writer = new Thread(this::write, "log-writer");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /** Queues the record and returns at once, whether or not there was room. */
    @Override
    public void publish(LogRecord record)
    {
        if (record == null || this.closed) return;

        long before = this.dropped.getAndSet(0);

        if (this.queue.offer(new Entry(record, before)))
        {
            this.queued.incrementAndGet();
        }
        else
        {
            this.dropped.addAndGet(before + 1);
        }
    }

    private void write()
    {
        try
        {
            while (true)
            {
                Entry next = this.queue.take();

                if (next.droppedBefore > 0) sayDropped(next.droppedBefore);

                emit(next.record);

                // Drops with nothing queued after them are said once the queue is empty
                if (this.queue.isEmpty())
                {
                    long trailing = this.dropped.getAndSet(0);

                    if (trailing > 0) sayDropped(trailing);
                }

                synchronized (this.progress)
                {
                    this.written++;
                    this.progress.notifyAll();
                }
            }
        }
        catch (InterruptedException closing)
        {
            // close() ends the thread
        }
    }

    private void sayDropped(long count)
    {
        emit(new LogRecord(Level.WARNING, I18n.f("log.consoleLinesDropped", count)));
    }

    private void emit(LogRecord record)
    {
        try
        {
            this.target.publish(record);
        }
        catch (RuntimeException e)
        {
            // The writer outlives a bad record; the target reports its own errors
        }
    }

    /**
     * Waits until every record published before the call has been written, or the time is up.
     *
     * @return whether everything was written in time
     */
    private boolean drain(long timeoutMs)
    {
        long goal = this.queued.get();
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        synchronized (this.progress)
        {
            while (this.written < goal)
            {
                long left = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime());

                if (left <= 0) return false;

                try
                {
                    this.progress.wait(left);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void flush()
    {
        drain(WAIT_MS);
    }

    /**
     * Writes what is queued, waiting no longer than a flush does, then stops.  The target is closed only once the queue
     * has emptied: a writer still stuck in it holds its lock.
     */
    @Override
    public void close()
    {
        boolean emptied = drain(WAIT_MS);

        this.closed = true;
        this.writer.interrupt();

        if (emptied) this.target.close();
    }
}
