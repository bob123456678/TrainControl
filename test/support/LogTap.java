package support;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * A log handler that keeps each record's message, for a test that reads what the program logged.  The caller adds it
 * to the logger it listens at and removes it when done; neither is done here.
 */
public final class LogTap extends Handler
{
    private final List<String> into;

    private final boolean skipEmpty;

    private LogTap(List<String> into, boolean skipEmpty)
    {
        this.into = into;
        this.skipEmpty = skipEmpty;
    }

    /** Adds every message but a null one, and nothing for a null record, to `into`. */
    public static Handler messagesInto(List<String> into)
    {
        return new LogTap(into, true);
    }

    /** Adds every record's message to `into`, null included. */
    public static Handler everyMessageInto(List<String> into)
    {
        return new LogTap(into, false);
    }

    @Override
    public void publish(LogRecord record)
    {
        if (!skipEmpty || record != null && record.getMessage() != null) into.add(record.getMessage());
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }
}
