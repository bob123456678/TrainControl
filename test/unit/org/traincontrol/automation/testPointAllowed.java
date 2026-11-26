package org.traincontrol.automation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;

/**
 * `Point.Allowed` on its own (FR-260): one Allowed trains cell, and two cells read as one, which is what a train ending
 * its trip on a plain copy meets (its stop cell and its through cell).  `core.testTrainsBySide` is its integration
 * test.
 */
public class testPointAllowed
{
    private static final Locomotive A = new MarklinLocomotive(null, 3131, Locomotive.decoderType.MM2, "Cell A");
    private static final Locomotive B = new MarklinLocomotive(null, 3132, Locomotive.decoderType.MM2, "Cell B");
    private static final Locomotive C = new MarklinLocomotive(null, 3133, Locomotive.decoderType.MM2, "Cell C");

    private static Point.Allowed white(Locomotive... listed)
    {
        return new Point.Allowed(new HashSet<>(Arrays.asList(listed)), true);
    }

    private static Point.Allowed black(Locomotive... listed)
    {
        return new Point.Allowed(new HashSet<>(Arrays.asList(listed)), false);
    }

    private static Set<Locomotive> allowed(Point.Allowed cell)
    {
        Set<Locomotive> out = new HashSet<>();

        for (Locomotive each : Arrays.asList(A, B, C))
        {
            if (!cell.excludes(each)) out.add(each);
        }

        return out;
    }

    /**
     * A whitelist lets in only its trains, an empty one nobody; a blacklist all but its trains; All everybody, and a
     * missing train is never kept out.
     *
     * MUTATION: in `Allowed.excludes`, drop `!= only` (read every list as a blacklist) and this fails.
     */
    @Test
    public void testACellKeepsOutWhatItSays()
    {
        assertEquals(allowed(white(A)), Collections.singleton(A), "a whitelist let in trains it does not name");
        assertEquals(allowed(white()), Collections.emptySet(), "an empty whitelist let a train in");
        assertEquals(allowed(black(A)), new HashSet<>(Arrays.asList(B, C)), "a blacklist kept out the wrong trains");
        assertEquals(allowed(Point.Allowed.ALL), new HashSet<>(Arrays.asList(A, B, C)), "All kept a train out");
        assertFalse(white().excludes(null), "no train was kept out");
    }

    /**
     * Two cells read as one let in exactly the trains both let in, for each pairing of whitelist and blacklist.
     *
     * MUTATION: in `Allowed.and`, make a whitelist and a blacklist keep the whitelist whole (drop the `removeAll`) and
     * this fails.
     */
    @Test
    public void testTwoCellsReadAsOneLetInWhatBothDo()
    {
        Point.Allowed[] cells = {white(A, B), white(B, C), black(A), black(B, C), Point.Allowed.ALL, white()};

        for (Point.Allowed one : cells)
        {
            for (Point.Allowed other : cells)
            {
                Set<Locomotive> both = allowed(one);

                both.retainAll(allowed(other));

                assertEquals(allowed(one.and(other)), both, "two cells read as one let in other trains than both do");
            }
        }
    }
}
