package support;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;

/**
 * Asking the window to recompute the marks a standing train puts on the diagram, and WAITING for the answer (OB-192).
 *
 * The work is on a worker, so a test that reads `coveredTrack` straight after the ask reads whatever was there before.
 * The wait lives here once rather than in each of the five classes: forgotten at one call site, a class does not fail,
 * it passes or fails on the state before the gesture.
 *
 * Also the railway's own side, to judge the marks against: one covering train left standing, and the squares its
 * edges cover.
 *
 * @author Adam
 */
public final class CoveredMarks
{
    /**
     * How long a refresh is given to land; generous, so a worker that has died reports as a stuck refresh and not as a
     * hang.
     */
    public static final long PATIENCE_MS = 60000;

    private CoveredMarks()
    {
    }

    /** Recomputes the covered and blocked sets and waits until the window has them. */
    public static void refresh(TrainControlUI ui) throws Exception
    {
        Method refresh = TrainControlUI.class.getDeclaredMethod("refreshCoveredTrack");

        refresh.setAccessible(true);
        refresh.invoke(ui);

        settle(ui);
    }

    /**
     * WAITS for a refresh somebody else asked for, without asking for one: the wait half of `refresh`, for the tests
     * that drive a production DOOR.  They must NOT call `refresh`, which invokes the window's own recompute and so
     * would prove only that the recompute works, not that the door asked for it.
     */
    public static void settle(TrainControlUI ui) throws Exception
    {
        Method settled = TrainControlUI.class.getDeclaredMethod("awaitCoveredTrack", long.class);

        settled.setAccessible(true);

        if (!Boolean.TRUE.equals(settled.invoke(ui, PATIENCE_MS)))
        {
            throw new IllegalStateException("the window did not finish working out the covered and"
                + " blocked squares within " + PATIENCE_MS + "ms, so everything asked of it below is"
                + " about the state BEFORE this refresh");
        }
    }

    /**
     * Every square inside an edge the railway counts as covered by a standing train, its two ends left out, worked out
     * from the covered EDGES and the reducer rather than asked of the window under test.
     */
    public static Set<TileKey> tilesOfTheCoveredEdges(AutonomySession session, Layout layout)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (Edge edge : layout.edgesCoveredByStandingTrains().keySet())
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            TileKey from = session.getStationIndex().squareOf(edge.getStart().getName());
            TileKey to = session.getStationIndex().squareOf(edge.getEnd().getName());

            if (from == null || to == null) continue;

            for (GraphReducer.ReducedEdge reduced : session.getReducer().getEdges())
            {
                boolean sameWay = from.equals(reduced.getStart()) && to.equals(reduced.getEnd());
                boolean otherWay = to.equals(reduced.getStart()) && from.equals(reduced.getEnd());

                if (!sameWay && !otherWay) continue;

                for (GraphReducer.TileStep step : reduced.getPath())
                {
                    if (step.getTile() == null) continue;

                    if (step.getTile().equals(from) || step.getTile().equals(to)) continue;

                    out.add(step.getTile());
                }

                break;
            }
        }

        return out;
    }

    /**
     * Takes every train off the running layout but the first that covers track at this length (a train whose recorded
     * arrival side leads nowhere covers nothing), and returns it, its length as it was; null, with nothing taken off,
     * when none covers.
     */
    public static Locomotive leaveOneTrainThatCovers(Layout layout, int length)
    {
        Point keep = null;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == null || point.getArrivedFrom() == null) continue;

            Locomotive candidate = point.getCurrentLocomotive();

            Integer was = candidate.getTrainLength();

            candidate.setTrainLength(length);

            boolean covers = layout.edgesCoveredByStandingTrains().containsValue(candidate);

            candidate.setTrainLength(was);

            if (covers)
            {
                keep = point;

                break;
            }
        }

        if (keep == null) return null;

        for (Point point : layout.getPoints())
        {
            if (point == keep || point.getCurrentLocomotive() == null) continue;

            layout.moveLocomotive(null, point.getName(), true);
        }

        return keep.getCurrentLocomotive();
    }
}
