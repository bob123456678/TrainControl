package org.traincontrol.gui.autonomyeditor;

import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.Set;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * This panel's Station Settings and train windows: one of each per surface (FR-111).  They follow every edit, close when
 * the square or the setup they are about has gone, and outline what they are about in the editor.
 */
final class StationWindows
{
    private final AutonomyEditorPanel editor;
    private final AutonomySession session;

    private StationSettingsWindow station;
    private TrainWindow train;

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
        Window owner = editor.ownerWindow();

        if (station != null && station.getOwner() != owner) station.closeWindow();

        if (station == null) station = new StationSettingsWindow(editor, owner);

        station.aim(square);

        outline();

        return station;
    }

    TrainWindow train(TileKey square)
    {
        Window owner = editor.ownerWindow();

        if (train != null && train.getOwner() != owner) train.closeWindow();

        if (train == null) train = new TrainWindow(editor, owner);

        train.aim(square);

        return train;
    }

    /** Right-clicking another square switches a window already open (FR-111). */
    void rightClicked(TileKey square)
    {
        if (station != null && station.tile != null && !square.equals(station.tile) && editor.offersStationSettings(square))
        {
            station(square);
        }

        if (train != null && train.tile != null && !square.equals(train.tile) && editor.offersStationSettings(square))
        {
            train(square);
        }
    }

    /** Something may have changed: read again once this gesture is over, after the rebuild it posted. */
    void changed()
    {
        if (reloadPending || (station == null && train == null)) return;

        reloadPending = true;

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            reloadPending = false;

            if (station != null) station.reload();

            if (train != null) train.reload();

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

    void closed(StationWindowBase window)
    {
        if (window == station) station = null;

        if (window == train) train = null;

        outline();
    }

    void closeAll()
    {
        if (station != null) station.goAway();

        if (train != null) train.goAway();
    }

    boolean outlines(TileKey square)
    {
        return outlined.contains(square);
    }

    StationSettingsWindow stationWindow()
    {
        return station;
    }

    TrainWindow trainWindow()
    {
        return train;
    }

    /** In the editor; the track diagram has no outline layer, and flashes the square instead (`flashSquare`). */
    private void outline()
    {
        if (editor.menuOnly) return;

        Set<TileKey> now = new LinkedHashSet<>();

        if (station != null && station.tile != null)
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
