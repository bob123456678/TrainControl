package org.traincontrol.gui.autonomy;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.gui.LayoutLabel;

/**
 * Which on-screen tiles stand for which square of which page.
 *
 * The same square can be on screen more than once (the main window and a popup can show the same page, and the diagram
 * is redrawn on every page switch), so this is a set per square rather than a single label.  Monitoring publishes one
 * picture and every copy of a tile shows it.
 *
 * Stale labels are pruned the way the accessory and feedback tile sets prune theirs: on iteration, by asking whether
 * the label's window is still up, so nothing has to deregister and a closed popup cannot leak its page.
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

        // Registering a new label for a square is the moment the old ones for it became rubbish.  The prune in publish
        // cannot do this on the main window: it asks whether the label's parent is VISIBLE, and the main window's grid
        // parent is the tab, visible for the life of the application, so every redraw left the old page here forever.
        //
        // Judged by isDisplayable rather than visibility: a label whose grid was replaced was removed from a realized
        // window and has no peer.  Only the labels already here are judged, never the arriving one, which may not be
        // attached yet.
        //
        // And only labels of the SAME window: the main window caches a page's grid and re-attaches it when the user
        // returns, so its labels are detached but alive whenever another page shows, and judging them from a popup
        // rebuilding the same page would leave the re-attached page registered nowhere, with no train ever lighting it
        // again.
        for (Iterator<LayoutLabel> i = here.iterator(); i.hasNext();)
        {
            LayoutLabel existing = i.next();

            if (existing != label && existing.sharesWindowWith(label) && !existing.isDisplayable())
            {
                i.remove();
            }
        }

        here.add(label);

        // A tile built after the last publish would otherwise show nothing until something next moved,
        // which is exactly what happens when a page is switched mid-run.
        TileOverlay current = lastPublished.get(key);

        if (current != null) label.setAutonomyOverlay(current);

        org.traincontrol.automationui.TileAnnotation annotation = lastAnnotated.get(key);

        if (annotation != null && annotation != BLANK && !label.isEditMode())
        {
            label.setAutonomyAnnotation(annotation);
        }
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
     * Sets what the setup says about one square, on every copy of it that is on screen.
     *
     * Separate from publish(), which is the whole-layout picture of what trains are doing.  This one is
     * per tile and changes rarely, so it is pushed as it changes rather than republished wholesale.
     *
     * @param key
     * @param annotation what to draw, or null for nothing
     */
    public void annotate(TileKey key, org.traincontrol.automationui.TileAnnotation annotation)
    {
        if (key == null) return;

        lastAnnotated.put(key, annotation == null
            ? BLANK : annotation);

        Set<LayoutLabel> here = tiles.get(key);

        if (here == null) return;

        for (Iterator<LayoutLabel> i = here.iterator(); i.hasNext();)
        {
            LayoutLabel label = i.next();

            if (!label.isParentVisible())
            {
                i.remove();
                continue;
            }

            // Never over the editor's own tiles: the editor draws directions, lengths and selection into this same slot
            // from the panel beside it, and the static layer carries none of those, so publishing here would wipe every
            // arrow in the editor on any edit.
            if (label.isEditMode()) continue;

            label.setAutonomyAnnotation(annotation);
        }
    }

    // A stand-in for "nothing", because a ConcurrentHashMap cannot hold a null value
    private static final org.traincontrol.automationui.TileAnnotation BLANK =
        new org.traincontrol.automationui.TileAnnotation(null, -1, false);

    private final Map<TileKey, org.traincontrol.automationui.TileAnnotation> lastAnnotated =
        new ConcurrentHashMap<>();

    /**
     * @return how many squares are registered, for diagnostics
     */
    /**
     * The labels currently showing this square, if any.
     *
     * A copy, because the caller walks it while the grid may be rebuilding underneath.
     *
     * @param key the square
     * @return its labels, possibly empty, never null
     */
    public Set<LayoutLabel> labelsFor(TileKey key)
    {
        Set<LayoutLabel> here = key == null ? null : tiles.get(key);

        return here == null ? java.util.Collections.<LayoutLabel>emptySet()
            : new java.util.LinkedHashSet<>(here);
    }

    /**
     * What this square was last told to show, as a label built for it is handed it.
     *
     * @param key the square
     * @return its overlay, or null where it shows none
     */
    public TileOverlay overlayAt(TileKey key)
    {
        return key == null ? null : lastPublished.get(key);
    }

    public int size()
    {
        return tiles.size();
    }

    /**
     * Blanks every square showing an annotation that is not among those kept, and forgets it.  What `clearAnnotations`
     * is for - the square that has LEFT the setup, which a redraw walking the graph cannot reach - without blanking the
     * squares the redraw sets again: those keep their mark, so the check that skips an unchanged one sees it.
     *
     * @param keep the squares the redraw sets
     */
    public void clearAnnotationsExcept(java.util.Set<TileKey> keep)
    {
        for (TileKey key : new java.util.ArrayList<>(lastAnnotated.keySet()))
        {
            if (keep.contains(key)) continue;

            annotate(key, null);

            lastAnnotated.remove(key);
        }
    }

    /**
     * Blanks every square that is currently showing an annotation, and forgets it.  Narrower than reset: the label
     * registry and monitoring state are left alone, because this runs whenever the static layer is redrawn.  It exists
     * for the square that has LEFT the setup (a page just excluded, track just deleted), which the redraw walking the
     * graph cannot reach.
     */
    public void clearAnnotations()
    {
        for (TileKey key : new java.util.ArrayList<>(lastAnnotated.keySet()))
        {
            annotate(key, null);
        }

        lastAnnotated.clear();
    }

    /**
     * Forgets everything.  Only for a layout being replaced wholesale; ordinary page switching prunes
     * itself.
     */
    public void reset()
    {
        tiles.clear();
        lastPublished.clear();
        lastAnnotated.clear();
    }
}
