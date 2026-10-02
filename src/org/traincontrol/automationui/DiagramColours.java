package org.traincontrol.automationui;

import java.awt.Color;

/**
 * The colours the track diagram draws a run and its train in, in one place (Adam, FR-106, 2026-10-02: "Make sure it is
 * easy to adjust as needed").  Change one here and every square that draws it follows: the run's line and arrowheads
 * (`TileOverlay`), the station badges (`TileAnnotation`) and the train's line along its tail (`LayoutLabel.TRAIN_MARK`).
 * The graph window's stylesheet, `graph.css`, went with that window.  NOT HERE (RSA25-C3): the station captions' navy
 * (`StationCaption`), a parking berth's grey, the restriction arrows and the autonomy editor's traced and tested paths
 * (`TileAnnotation`) - colours of the setup and its editor, not of a run, each kept beside what draws it.
 */
public final class DiagramColours
{
    /**
     * A square autonomy uses - the station badges: a blue, rgb(0,0,200).
     */
    public static final Color STATION = new Color(0, 0, 200);

    /**
     * The path ahead of a running train: the stations' blue (Adam, FR-106: "I might prefer the same blue as stations for
     * pending paths"; chosen over the dark red it was, rgb(196,0,0), which reads as "stop" on a railway).
     */
    public static final Color PATH_AHEAD = STATION;

    /**
     * Track a running train has driven and still holds: a dark grey (Adam, FR-106: "just the tail plus dark grey for
     * completed"; it was a green, rgb(0,196,33)).  Drawn only while the run holds it - what non-atomic routes give back
     * behind the train is drawn as nothing.
     */
    public static final Color PATH_DRIVEN = new Color(85, 85, 85);

    /**
     * The arrowheads along a run, which say which way it goes: white, to read on the blue and the grey (they were black,
     * on red and green).
     */
    public static final Color PATH_ARROW = Color.WHITE;

    /**
     * The thin dark edge round each arrowhead (RSA25-C4): on a sensor the arrowhead lands on its contact, a white circle,
     * and all but the part over the line disappeared.
     */
    public static final Color PATH_ARROW_EDGE = new Color(40, 40, 40);

    /**
     * Track held clear so another path can run: a light grey, receding from the running path.
     */
    public static final Color PATH_HELD = new Color(238, 238, 238);

    /**
     * A train: its line along the track it lies on, the whole length of its tail, standing or running (MT-309, FR-106),
     * and the cross of a square nothing can pass: an orange, rgb(255,102,0).
     */
    public static final Color TRAIN = new Color(255, 102, 0);

    private DiagramColours()
    {
    }
}
