package core;

import org.traincontrol.base.Feedback;
import org.traincontrol.gui.LayoutLabel;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The s88 debounce gate, which turns out to have exactly one circumstance in which it does anything.
 *
 * IGNORE_SUB_INTERVAL is 0, so "has enough time passed" is always yes unless the subtraction comes out negative, which
 * only the wall clock moving backwards does.  The gate was a rule that ignored sensors after a clock correction, the
 * one moment nothing should be ignored.
 *
 * @author Adam
 */
public class testFeedback
{
    /** The smallest thing that is a Feedback, so the gate can be asked directly. */
    private static class Probe extends Feedback
    {
        Probe()
        {
            super("probe");
        }

        @Override
        public int getUID()
        {
            return 1;
        }

        @Override
        public void setState(boolean val)
        {
            _setState(val);
        }

        @Override
        public void addTile(LayoutLabel l)
        {
        }

        boolean ready(long time)
        {
            return readyForUpdate(time);
        }
    }

    /**
     * A sensor is not ignored because the clock went backwards.
     *
     * System.currentTimeMillis is not monotonic, and every s88 update is gated on it.  A clock stepped back thirty
     * seconds would make every sensor that had fired inside that window refuse its next transitions, and because the
     * state is only recorded when it CHANGES, the model would hold the wrong occupancy while a driving thread waited
     * for an arrival it had already been told about.  The gate is asked directly rather than through MarklinFeedback:
     * the defect is in the comparison.
     */
    @Test
    public void testAFeedbackIsNotGatedByAClockThatWentBackwards() throws Exception
    {
        Probe probe = new Probe();

        // Establishes lastEvent at "now" - _setState only stamps it when the state actually changes
        probe.setState(true);

        long steppedBack = System.currentTimeMillis() - 30000;

        assertTrue(probe.ready(steppedBack),
            "a transition arriving with the clock stepped back 30s was refused, so every sensor that "
                + "fired in the stepped-over window goes deaf until real time catches up");
    }

    /**
     * The gate still lets an ordinary, forward-moving update through.
     *
     * The precondition that keeps the test above honest: a gate that returned true unconditionally would satisfy it.
     */
    @Test
    public void testAnOrdinaryUpdateIsStillAccepted() throws Exception
    {
        Probe probe = new Probe();

        probe.setState(true);

        assertTrue(probe.ready(System.currentTimeMillis() + 1),
            "a normal forward-moving update was refused");
    }
}
