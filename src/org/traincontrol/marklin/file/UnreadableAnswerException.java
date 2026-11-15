package org.traincontrol.marklin.file;

import java.io.IOException;

/**
 * A Central Station answered, but with something a reader cannot use (another shape, a list with no way to match its
 * records).  The sync leaves what it holds as it is and goes on (FR-169).
 *
 * @author Adam
 */
public final class UnreadableAnswerException extends IOException
{
    public UnreadableAnswerException(String what)
    {
        super(what);
    }
}
