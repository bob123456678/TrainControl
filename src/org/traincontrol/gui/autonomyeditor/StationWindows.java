package org.traincontrol.gui.autonomyeditor;

import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.Set;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * This panel's Station Settings window, one per surface (FR-111).  It follows every edit, closes when the square or the
 * setup it is about has gone, and outlines what it is about in the editor.
 */
final class StationWindows
{
    private final AutonomyEditorPanel editor;
    private final AutonomySession session;

    private StationSettingsWindow station;

    /** What the window on screen outlines: its square and guard signals. */
    private final Set<TileKey> outlined = new LinkedHashSet<>();

    private boolean reloadPending;

    StationWindows(AutonomyEditorPanel editor, AutonomySession session)
    {
        this.editor = editor;
        this.session = session;
    }

    /** The window for this surface, made anew when another window asks than the one it hangs off. */
    StationSettingsWindow station(TileKey square)
    {
        return stations(java.util.Collections.singletonList(square));
    }

    /** The same window about several squares at once (FR-052), or one. */
    StationSettingsWindow stations(java.util.List<TileKey> squares)
    {
        Window owner = editor.ownerWindow();

        if (station != null && station.getOwner() != owner) station.closeWindow();

        if (station == null) station = new StationSettingsWindow(editor, owner);

        station.aimAt(squares);

        outline();

        return station;
    }

    /** Right-clicking another square switches the window already open, on the tab it shows (FR-111). */
    void rightClicked(TileKey square)
    {
        if (station != null && station.tile != null && !station.isSeveral() && !square.equals(station.tile)
            && editor.offersStationSettings(square))
        {
            station(square);
        }
    }

    /**
     * Station Settings steps aside while guard signals are picked, their window up or its click awaited, and comes back
     * where it was when that ends, however it ends (MT-956).  Before the guard window shows, which would hide with it.
     */
    void followTheGuardPick()
    {
        if (station != null) station.stepAside(editor.isFocusedOnSignals());
    }

    /** Something may have changed: read again once this gesture is over, after the rebuild it posted. */
    void changed()
    {
        if (reloadPending || station == null) return;

        reloadPending = true;

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            reloadPending = false;

            if (station != null) station.reload();

            outline();
        });
    }

    /** Whether a window on this square may stay open: the setup is the one it opened on, and it is still a Point. */
    boolean stillAbout(TileKey square)
    {
        // The track diagram's panel outlives its setup: a load replaces the session under it
        boolean current = !editor.menuOnly || editor.parentWindow() == null || editor.parentWindow().getAutonomySession() == session;

        return current && session.getReducer() != null && session.getReducer().getPoints().containsKey(square);
    }

    void closed(StationWindowBase window, TileKey square)
    {
        // A guard pick its pencil started goes with the window (MT-948), and a tail pick about its square (MT-959)
        if (window == station && square != null)
        {
            editor.endGuardPick(square);
            editor.endTailPick(square);
        }

        if (window == station) station = null;

        outline();
    }

    void closeAll()
    {
        if (station != null) station.goAway();
    }

    boolean outlines(TileKey square)
    {
        return outlined.contains(square);
    }

    StationSettingsWindow stationWindow()
    {
        return station;
    }

    /** In the editor; the track diagram has no outline layer, and flashes the square instead (`flashSquare`). */
    private void outline()
    {
        if (editor.menuOnly) return;

        Set<TileKey> now = new LinkedHashSet<>();

        if (station != null && station.tile != null && !editor.isPickingStations())
        {
            now.add(station.tile);
            now.addAll(session.getProtectingSignals(station.tile));
            now.addAll(session.getEntrySignals(station.tile));
        }

        if (now.equals(outlined)) return;

        outlined.clear();
        outlined.addAll(now);

        editor.redrawTheMarks();
    }
}
