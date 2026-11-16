package org.traincontrol.gui.autonomyeditor;

import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * Station Settings' shortcuts (FR-176): each names one combination of the two boxes, May choose this station and
 * Changing direction, and nothing else.  Off is an s88 square that is no station (MT-917), whose May choose is grayed,
 * so neither set nor read.  Station, Terminus and Parking are stations trains must stop at (MT-1017).
 */
public enum StationPreset
{
    STATION("autosetup.ui.presetStation", "autosetup.ui.presetStationTip", true, false, true, false, false),
    TERMINUS("autosetup.ui.presetTerminus", "autosetup.ui.presetTerminusTip", true, false, true, false, true),
    PARKING("autosetup.ui.presetParking", "autosetup.ui.presetParkingTip", true, false, false, false, false),
    REVERSING_POINT("autosetup.ui.presetReversingPoint", "autosetup.ui.presetReversingPointTip", true, true, false, true,
        false),
    OFF("autosetup.ui.presetOff", "autosetup.ui.presetOffTip", false, true, null, false, false);

    final String labelKey;
    final String tipKey;
    final boolean stops;
    final boolean runsThrough;
    final Boolean fullAutonomy;
    final boolean may;
    final boolean must;

    StationPreset(String labelKey, String tipKey, boolean stops, boolean runsThrough, Boolean fullAutonomy, boolean may,
        boolean must)
    {
        this.labelKey = labelKey;
        this.tipKey = tipKey;
        this.stops = stops;
        this.runsThrough = runsThrough;
        this.fullAutonomy = fullAutonomy;
        this.may = may;
        this.must = must;
    }

    /** The preset this square's settings match, however they got there, or null. */
    static StationPreset matching(AutonomySession session, TileKey tile)
    {
        StationBoxes boxes = StationBoxes.of(session, tile);

        boolean must = session.isMustTurnAround(tile);
        boolean may = session.isTurnAround(tile) && !must;

        for (StationPreset preset : values())
        {
            if (preset.stops == boxes.stops && preset.runsThrough == boxes.runsThrough && preset.may == may
                && preset.must == must
                && (preset.fullAutonomy == null || preset.fullAutonomy == session.isAutoDestination(tile)))
            {
                return preset;
            }
        }

        return null;
    }
}
