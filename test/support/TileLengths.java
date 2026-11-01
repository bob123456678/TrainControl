package support;

import java.util.LinkedHashSet;
import java.util.Set;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * The tiles a session's reduction knows, and one length given to all of them, for a test that needs a measured railway.
 */
public final class TileLengths
{
    private TileLengths()
    {
    }

    /** The tiles along the reduction's edges, in path order: not the points' own squares. */
    public static Set<TileKey> onTheEdges(AutonomySession session)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) out.add(step.getTile());
            }
        }

        return out;
    }

    /**
     * Gives every tile the reduction knows, the points' and the edges', this length.  Collected first and written
     * afterwards: `setTileLength` rebuilds the reducer, so writing while walking its collections re-derives the graph
     * under the walk.
     */
    public static void measureEveryTileAs(AutonomySession session, int units)
    {
        Set<TileKey> everyTile = new LinkedHashSet<>();

        for (TileKey tile : session.getReducer().getPoints().keySet()) everyTile.add(tile);

        everyTile.addAll(onTheEdges(session));

        for (TileKey tile : everyTile) session.setTileLength(tile, units);
    }
}
