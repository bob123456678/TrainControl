package support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;

/**
 * The whole-railway safety claim for the long runs: at every poll, no two trains hold the same track.  `poll()` fails
 * naming the two trains, the edge and the time since the watch began; `requireOverlap` fails a run on which two trains
 * were never out at once, which would have passed the claim for nothing.
 *
 * Held means the edges of a running path its tail has not given back, read inside the monitor the dispatcher writes
 * `activeLocomotives` and `releasedEarly` under, so a poll never sees a release half done.  Two trains clash when one
 * holds an edge the other holds or lists as a lock edge, or one is headed for the square the other stands on.
 */
public final class TrackWatch
{
    private final Layout layout;
    private final long started = System.currentTimeMillis();
    private int pollsWithTwoOut;

    public TrackWatch(Layout layout)
    {
        this.layout = layout;
    }

    /** Checks the railway as it is now, failing on the first clash. */
    @SuppressWarnings("unchecked")
    public void poll() throws Exception
    {
        Map<Locomotive, Set<Edge>> givenBack =
            (Map<Locomotive, Set<Edge>>) Reflect.get(Layout.class, layout, "releasedEarly");

        Map<Locomotive, List<Edge>> held = new LinkedHashMap<>();
        Map<Locomotive, Point> heading = new LinkedHashMap<>();
        String clash = null;

        synchronized (layout.getActiveLocomotives())
        {
            for (Map.Entry<Locomotive, List<Edge>> journey : layout.getActiveLocomotives().entrySet())
            {
                List<Edge> path = journey.getValue();

                if (path == null || path.isEmpty()) continue;

                Set<Edge> given = givenBack.get(journey.getKey());
                List<Edge> still = new ArrayList<>();

                for (Edge e : path)
                {
                    if (given == null || !given.contains(e)) still.add(e);
                }

                held.put(journey.getKey(), still);
                heading.put(journey.getKey(), path.get(path.size() - 1).getEnd());
            }

            List<Locomotive> out = new ArrayList<>(held.keySet());

            if (out.size() >= 2) pollsWithTwoOut++;

            for (int i = 0; i < out.size() && clash == null; i++)
            {
                for (int j = i + 1; j < out.size() && clash == null; j++)
                {
                    clash = clash(out.get(i), held.get(out.get(i)), heading.get(out.get(i)),
                        out.get(j), held.get(out.get(j)), heading.get(out.get(j)));
                }
            }
        }

        assertTrue(clash == null, clash + ", " + (System.currentTimeMillis() - started) / 1000.0 + " s after the run began");
    }

    /** Fails unless at least `min` polls saw two trains out at once. */
    public void requireOverlap(int min)
    {
        if (pollsWithTwoOut < min)
        {
            fail("only " + pollsWithTwoOut + " polls saw two trains out at once, and the claim that no two hold one track"
                + " needs at least " + min + " to mean anything");
        }
    }

    /** How many polls saw two trains out at once. */
    public int pollsWithTwoOut()
    {
        return pollsWithTwoOut;
    }

    private static String clash(Locomotive a, List<Edge> heldA, Point toA, Locomotive b, List<Edge> heldB, Point toB)
    {
        String edge = sharing(heldA, heldB);

        if (edge == null) edge = sharing(heldB, heldA);

        if (edge != null) return a.getName() + " and " + b.getName() + " both hold the track at " + edge;

        if (toA != null && b.equals(toA.getCurrentLocomotive()))
            return a.getName() + " is headed for " + toA.getName() + ", where " + b.getName() + " stands";

        if (toB != null && a.equals(toB.getCurrentLocomotive()))
            return b.getName() + " is headed for " + toB.getName() + ", where " + a.getName() + " stands";

        return null;
    }

    /** The name of an edge in `mine` that `theirs` also holds or lists as a lock edge, or null. */
    private static String sharing(List<Edge> mine, List<Edge> theirs)
    {
        for (Edge e : mine)
        {
            for (Edge t : theirs)
            {
                if (e == t || t.getLockEdges().contains(e)) return e.getName();
            }
        }

        return null;
    }
}
