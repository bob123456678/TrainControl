package org.traincontrol.gui;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.traincontrol.base.TileGraph.TileKey;
import org.traincontrol.base.TileOverlay;

/**
 * Which on-screen tiles stand for which square of which page.
 *
 * The same square can be on screen more than once - the main window and a popup can show the same page,
 * and the diagram is redrawn whenever a page is switched - so this is a set per square rather than a
 * single label.  Monitoring publishes one picture and every copy of a tile shows it.
 *
 * Stale labels are pruned the way the accessory and feedback tile sets already prune theirs: on
 * iteration, by asking whether the label's window is still up.  Nothing has to remember to deregister,
 * which is what keeps a closed popup from leaking its whole page.
 *
 * @author Adam
 */
public class DiagramTileRegistry
{
    private final Map<TileKey, Set<LayoutLabel>> tiles = new ConcurrentHashMap<>();

    // What each tile was last told to show, so a tile drawn after a publish can catch up
    private final Map<TileKey, TileOverlay> lastPublished = new ConcurrentHashMap<>();

    /**
     * Registers a label as showing this square.
     *
     * Called while a grid is being built, which is also the only place that knows both the page and the
     * square - a LayoutLabel is told neither.
     *
     * @param key
     * @param label
     */
    public void register(TileKey key, LayoutLabel label)
    {
        if (key == null || label == null) return;

        Set<LayoutLabel> here = tiles.get(key);

        if (here == null)
        {
            here = ConcurrentHashMap.newKeySet();

            Set<LayoutLabel> raced = tiles.putIfAbsent(key, here);

            if (raced != null) here = raced;
        }

        here.add(label);

        // A tile built after the last publish would otherwise show nothing until something next moved,
        // which is exactly what happens when a page is switched mid-run.
        TileOverlay current = lastPublished.get(key);

        if (current != null) label.setAutonomyOverlay(current);
    }

    /**
     * Shows a picture of the whole layout.
     *
     * Every registered tile is told what it should show, including the ones that should now show
     * nothing - a tile left lit after its train has gone reads as a train that is still there, so
     * clearing is as important as setting.
     *
     * @param overlays what each square should show; squares absent from it are cleared
     */
    public void publish(Map<TileKey, TileOverlay> overlays)
    {
        if (overlays == null) return;

        lastPublished.clear();
        lastPublished.putAll(overlays);

        for (Map.Entry<TileKey, Set<LayoutLabel>> entry : tiles.entrySet())
        {
            TileOverlay overlay = overlays.get(entry.getKey());

            for (Iterator<LayoutLabel> i = entry.getValue().iterator(); i.hasNext();)
            {
                LayoutLabel label = i.next();

                // the same pruning the device tile sets do: a label whose window has gone is dropped
                // on the next pass rather than needing anybody to deregister it
                if (!label.isParentVisible())
                {
                    i.remove();
                    continue;
                }

                label.setAutonomyOverlay(overlay);
            }
        }
    }

    /**
     * Clears every tile, for when autonomy stops.
     */
    public void clearOverlays()
    {
        publish(Collections.<TileKey, TileOverlay>emptyMap());
    }

    /**
     * @return how many squares are registered, for diagnostics
     */
    public int size()
    {
        return tiles.size();
    }

    /**
     * Forgets everything.  Only for a layout being replaced wholesale; ordinary page switching prunes
     * itself.
     */
    public void reset()
    {
        tiles.clear();
        lastPublished.clear();
    }
}
