package core;

import org.traincontrol.base.Feedback;
import org.traincontrol.gui.LayoutLabel;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The s88 debounce gate, which turns out to have exactly one circumstance in which it does anything.
 *
 * IGNORE_SUB_INTERVAL is 0, so "has enough time passed" is always yes - unless the subtraction comes
 * out negative, and the only way that happens is the wall clock moving backwards.  The gate was
 * therefore not a debounce at all in practice; it was a rule that said to ignore sensors after a clock
 * correction, which is the one moment nothing should be ignored.
 *
 * @author Adam
 */
public class testFeedback
{
    /**
     * The smallest thing that is a Feedback, so the gate can be asked directly.
     */
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
     * System.currentTimeMillis is not monotonic, and every s88 update is gated on it.  An NTP
     * correction that steps the clock back thirty seconds made every sensor that had fired inside that
     * window refuse its next transitions until real time caught up - and because the state is only
     * recorded when it CHANGES, the model was left holding the wrong occupancy while a driving thread
     * waited for an arrival it had already been told about.
     *
     * Asking the gate directly rather than through MarklinFeedback is deliberate: the defect is in the
     * comparison, and a test that needed a control station to reach it would be testing the plumbing.
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
     * The precondition that keeps the test above honest: a fix that made readyForUpdate return true
     * unconditionally would satisfy it, and would also be the right answer only by accident.
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
