package org.traincontrol.gui.autonomyeditor;

import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;

/** Station Settings' four shortcuts (FR-176): each names one combination of four settings, and nothing else. */
public enum StationPreset
{
    STATION("autosetup.ui.presetStation", "autosetup.ui.presetStationTip", true, false, false),
    TERMINUS("autosetup.ui.presetTerminus", "autosetup.ui.presetTerminusTip", true, false, true),
    PARKING("autosetup.ui.presetParking", "autosetup.ui.presetParkingTip", false, false, false),
    REVERSING_POINT("autosetup.ui.presetReversingPoint", "autosetup.ui.presetReversingPointTip", false, true, false);

    final String labelKey;
    final String tipKey;
    final boolean fullAutonomy;
    final boolean may;
    final boolean must;

    StationPreset(String labelKey, String tipKey, boolean fullAutonomy, boolean may, boolean must)
    {
        this.labelKey = labelKey;
        this.tipKey = tipKey;
        this.fullAutonomy = fullAutonomy;
        this.may = may;
        this.must = must;
    }

    /** The preset this square's settings match, however they got there, or null. */
    static StationPreset matching(AutonomySession session, TileKey tile)
    {
        if (!StationBoxes.of(session, tile).stops || !StationBoxes.of(session, tile).runsThrough) return null;

        boolean must = session.isMustTurnAround(tile);
        boolean may = session.isTurnAround(tile) && !must;

        for (StationPreset preset : values())
        {
            if (preset.fullAutonomy == session.isAutoDestination(tile) && preset.may == may && preset.must == must)
            {
                return preset;
            }
        }

        return null;
    }
}
