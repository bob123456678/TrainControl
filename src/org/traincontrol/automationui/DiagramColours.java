package org.traincontrol.automationui;

import java.awt.Color;

/**
 * The colors the track diagram draws a run and its train in, in one place (Adam, FR-106: "Make sure it is easy to
 * adjust as needed").  Change one here and every square that draws it follows: the run's line and arrowheads
 * (`TileOverlay`), the station badges (`TileAnnotation`) and the train's line along its tail
 * (`LayoutLabel.TRAIN_MARK`).  Not here: the station captions' navy (`StationCaption`), a parking berth's gray, the
 * restriction arrows and the autonomy editor's traced and tested paths (`TileAnnotation`) - colors of the setup and its
 * editor, not of a run.
 */
public final class DiagramColours
{
    /**
     * A square autonomy uses - the station badges: a blue, rgb(0,0,200).
     */
    public static final Color STATION = new Color(0, 0, 200);

    /**
     * The path ahead of a running train: the stations' blue (Adam, FR-106: "I might prefer the same blue as stations
     * for pending paths"); the dark red it was reads as "stop" on a railway.
     */
    public static final Color PATH_AHEAD = STATION;

    /**
     * Track a running train has driven and still holds: a dark gray (Adam, FR-106: "just the tail plus dark grey for
     * completed").  Drawn only while the run holds it; what non-atomic routes give back behind the train is drawn as
     * nothing.
     */
    public static final Color PATH_DRIVEN = new Color(85, 85, 85);

    /** The arrowheads along a run, which say which way it goes: white, to read on the blue and the gray. */
    public static final Color PATH_ARROW = Color.WHITE;

    /**
     * The thin dark edge round each arrowhead: on a sensor the arrowhead lands on its white circle of a contact, and
     * all but the part over the line would disappear.
     */
    public static final Color PATH_ARROW_EDGE = new Color(40, 40, 40);

    /**
     * Track held clear so another path can run: a light gray line along it, faint, over the wash below.
     */
    public static final Color PATH_HELD = new Color(238, 238, 238);

    /**
     * And the wash over the whole of a held square, which is most of what says "held": white at 45%, paling
     * the tile so it recedes from the running path rather than adding a color.
     */
    public static final Color PATH_HELD_WASH = Color.WHITE;

    /** How heavily `PATH_HELD_WASH` is laid over a held square, from 0 (not at all) to 1 (hiding it). */
    public static final float PATH_HELD_WASH_ALPHA = 0.45f;

    /**
     * A running train's destination caption: the orange a train's line shows on white (`TRAIN_SEE_THROUGH` laid on
     * white, rgb(255,171,115)), solid, ringed in `DESTINATION_RING` by the pill that wears it.  Adam: "Make the yellow
     * labels (trains on their way somewhere) have the same orange background color as occupied train tiles, just
     * without the fading", and "Go with as the line actually looks on white".  Its text is black, which reads on it at
     * about eleven to one.
     */
    public static final Color DESTINATION = new Color(255, 171, 115);

    /**
     * The ring round a destination's caption: the train's orange, rgb(255,102,0), darkened by a fifth (Adam: "give the
     * pill a dark orange border instead of the blue border").
     */
    public static final Color DESTINATION_RING = new Color(204, 82, 0);

    /**
     * A train: its line along the track it lies on, the whole length of its tail, standing or running (MT-309, FR-106):
     * a soft orange, rgb(245,140,60) (Adam: "let's go with the soft orange"), since rgb(255,102,0) was the loudest
     * thing on a running diagram; the X of a square nothing can pass keeps that orange
     * (`TileAnnotation.POINT_IMPASSABLE`).
     */
    public static final Color TRAIN = new Color(245, 140, 60);

    /**
     * A train's line, see-through: rgb(255,102,0) at 55%, so the track it lies on shows through (Adam: "can we change
     * to the see through tail line").  What the line is drawn in unless `LayoutLabel.tailColour` is set to the soft
     * orange.
     */
    public static final Color TRAIN_SEE_THROUGH = new Color(255, 102, 0, 140);

    /**
     * A see-through train's line where it lies over its own run line, `PATH_DRIVEN`'s dark gray: an orange that, laid
     * at 75% over that gray, comes out as `TRAIN_SEE_THROUGH` does over a track square where a standing train lies,
     * rgb(209,125,69), so a train is the same orange running as standing (Adam: "Can we brighten the color of the
     * orange train tail while running so that it more closely matches the normal stopped color?  Remember it is seen
     * against the dark gray track color with chevrons").  Still see-through, so the white chevrons under it show,
     * lighter.
     */
    public static final Color TRAIN_OVER_RUN = new Color(250, 138, 64, 191);

    /**
     * A running train whose icon cannot be read is drawn as a dot: this fill, ringed in `TRAIN_DOT_RING`.
     */
    public static final Color TRAIN_DOT = Color.BLACK;

    /** The ring round `TRAIN_DOT`, so it reads on the dark line under it. */
    public static final Color TRAIN_DOT_RING = Color.WHITE;

    private DiagramColours()
    {
    }
}
