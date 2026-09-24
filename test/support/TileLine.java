package support;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * A whole line of squares moved or copied at once, in the two halves the setup store is handed: the squares that
 * travel, and the squares built over.
 *
 * The editor's whole-row and whole-column gesture built this with `LayoutEditor.planBulkLine` until the gesture was
 * replaced by multi-select (6f60b118, 2026-08-19) and its unreachable code was removed (DCN-C3).  Dragging or pasting a
 * selected column still asks `AutonomyCompanionStore.moveTiles` the same question, so the store's rules are tested
 * with lines built here.
 *
 * @author Adam
 */
public final class TileLine
{
    /** Where each square that travels goes */
    public final Map<TileKey, TileKey> moves = new LinkedHashMap<>();

    /** Every square of the line written onto, occupied or not */
    public final Set<TileKey> builtOver = new LinkedHashSet<>();

    /**
     * A line taken from one column or row and written onto another.
     *
     * Every square of the destination line is built over - one whose source was empty has its tile deleted and
     * nothing put back.  On a move, each source square that carries track travels to the square opposite it; on a
     * copy nothing travels, because two squares cannot both be one station.
     *
     * @param page the page
     * @param column true for a column, false for a row
     * @param from the line being taken
     * @param to the line being written over
     * @param span how many squares long the line is
     * @param occupied indices along the source line that carry track
     * @param move whether the source line is being emptied
     * @return the line, empty when it is moved onto itself
     */
    public static TileLine of(String page, boolean column, int from, int to, int span, Set<Integer> occupied,
        boolean move)
    {
        TileLine line = new TileLine();

        if (page == null || from == to || from < 0 || to < 0 || span <= 0) return line;

        for (int i = 0; i < span; i++)
        {
            TileKey source = column ? new TileKey(page, from, i) : new TileKey(page, i, from);
            TileKey dest = column ? new TileKey(page, to, i) : new TileKey(page, i, to);

            line.builtOver.add(dest);

            if (move && occupied != null && occupied.contains(i)) line.moves.put(source, dest);
        }

        return line;
    }
}
