package org.traincontrol.base;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of a "check for renamed locomotives" scan: what can be applied, and whether anything was declined.
 *
 * Both are needed because an empty proposal list means either that the Central Station and the local database agree or
 * that every rename it wanted was refused, and the user has to be told which.
 *
 * @author Adam
 */
public class RenameProposals
{
    private final List<String[]> proposals;
    private final int refused;

    public RenameProposals(List<String[]> proposals, int refused)
    {
        this.proposals = proposals;
        this.refused = refused;
    }

    /**
     * The renames that can be applied, in the order they must be applied in
     * @return
     */
    public List<String[]> getProposals()
    {
        return Collections.unmodifiableList(this.proposals);
    }

    /**
     * How many candidate renames were declined.
     *
     * Counts declined candidates rather than certain renames: the duplicate-address refusals fire
     * before the names are compared, so some of what they skip would have produced no proposal anyway.
     * It is a "was anything held back" signal, not a total - the log carries the detail.
     *
     * @return
     */
    public int getRefusedCount()
    {
        return this.refused;
    }

    /**
     * Whether anything was declined, and the log therefore has something worth reading
     * @return
     */
    public boolean hasRefusals()
    {
        return this.refused > 0;
    }
}
