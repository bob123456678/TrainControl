package org.traincontrol.gui.autonomyeditor;

import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;

/** Station Settings' two section boxes as the setup says them (FR-111): neither is Nothing Can Pass. */
final class StationBoxes
{
    /** Trains can stop here. */
    final boolean stops;

    /** Trains can pass through. */
    final boolean runsThrough;

    private StationBoxes(boolean stops, boolean runsThrough)
    {
        this.stops = stops;
        this.runsThrough = runsThrough;
    }

    static StationBoxes of(AutonomySession session, TileKey tile)
    {
        // Stored only when off: an old station reads both on, an old out-of-service square both off
        if (Boolean.FALSE.equals(session.getPointProperty(tile, "active"))) return new StationBoxes(false, false);

        return new StationBoxes(session.getStore().isStation(tile), !session.isStopOnly(tile));
    }
}
