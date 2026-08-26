package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;

/**
 * A station caption, drawn as a rounded pill instead of a rectangle of text.
 *
 * FR-028. Adam: "upgrade from [---] to blue ovals with white text, similar to what we have on the auto
 * loc panels in the autonomy tab."  The badge on those panels is a navy background, white text and a
 * fully rounded border, and this is the same thing on the diagram.
 *
 * **A JLabel subclass rather than a new kind of component**, because the caption is registered,
 * coloured, re-texted and clicked from a dozen places that all know it as a JLabel - the window's
 * caption registry, `updateStationLabels`, the click and hover listeners, the grid's own styling. A
 * component of its own would have meant changing every one of them to find out whether the pill looked
 * right, and the answer to that is a picture, not a refactor.
 *
 * What changes is the painting. The fill comes from `getBackground()`, so every colour the running
 * diagram already uses to say something - yellow for the destination, grey for a station that is shut,
 * navy at rest - still says it, and still says it through the tile art underneath because those colours
 * carry their own alpha. What changes is the SHAPE, and that the text is legible on top of whatever the
 * colour turned out to be.
 *
 * @author Adam
 */
public class StationCaption extends JLabel
{
    /**
     * The blue of the station badge on an autonomy locomotive panel.
     *
     * The same value, not a similar one: Adam asked for "similar to what we have on the auto loc
     * panels", and two blues a shade apart on one screen look like a mistake rather than a family.
     */
    public static final Color PILL = new Color(0, 0, 115);

    /**
     * The resting fill, with the diagram's own translucency.
     *
     * "Slight opacity" is in the request and was already true of the white captions - the tile art
     * shows through, which is what stops a row of captions reading as holes punched in the diagram.
     */
    public static final Color PILL_AT_REST =
        new Color(PILL.getRed(), PILL.getGreen(), PILL.getBlue(), LayoutGrid.LAYOUT_STATION_OPACITY);

    /**
     * The other resting colour: light grey, for a diagram somebody wants quieter (FR-031).
     *
     * Adam: "add a jmenu (preferences) setting for the station labels to be blue (default) or light
     * gray (non default)."  A page with thirty stations on it is thirty blue ovals, and on a busy
     * diagram that is a lot of one colour - so the alternative is a caption that is still a caption
     * and stops competing with the signals and the running path for attention.
     *
     * Light enough that the text on it goes black, which readableOn works out on its own.
     */
    public static final Color PILL_GREY =
        new Color(196, 198, 202, LayoutGrid.LAYOUT_STATION_OPACITY);

    /**
     * Whichever resting colour the operator has chosen (FR-031).
     *
     * Asked rather than stored, and asked at the moment a caption is coloured: a preference read once
     * into a constant is a preference that needs the application restarted, which is not what "persist
     * as with other settings" means for a switch sitting in a menu.
     *
     * @return the fill for a caption with nothing else to say
     */
    public static Color restingFill()
    {
        return TrainControlUI.stationLabelsAreGrey() ? PILL_GREY : PILL_AT_REST;
    }

    /**
     * Room at the ends, so the text does not run into the curve.
     *
     * A fraction of the height rather than a fixed number of pixels: the caption font is sized from the
     * tile, so a pill padded by four pixels looks generous at 20px tiles and cramped at 60.
     *
     * Cut back at Adam’s reading of the first version on his own layout - "reduce left and right
     * edge padding too". A caption is drawn between other things on a crowded diagram, and every pixel
     * of it that is not text is a pixel of somebody else’s track it covers.
     */
    private static final double SIDE_PADDING = 0.27;

    /**
     * How much smaller a caption is than the diagram's other text.
     *
     * Adam, looking at the first version on his own layout: "make the font about 10% smaller to better
     * fit between the tracks."  A pill is taller than the text it holds - that is what the shape costs
     * - so a caption set at the same size as the labels around it is a taller object than the one it
     * replaced, on a diagram whose rows are one tile apart.
     *
     * Then a tenth again, having seen that one: 0.81, which is two tenths off rather than one. The
     * number is here rather than at the call site so that the next reading of it is one edit.
     */
    public static final float FONT_SCALE = 0.81f;

    /**
     * Whether this label is a caption at all.
     *
     * The same JLabel class draws the user's own writing on the diagram - yard names, notes - and
     * turning those into blue pills would be answering a question nobody asked. Only what autonomy
     * puts on a square gets the pill.
     */
    private boolean pill = false;

    /**
     * The size of the square this caption belongs to, in pixels, or 0 before it is told.
     */
    private int tile = 0;

    /**
     * How far LEFT of its own square the caption's cell was allowed to start, in pixels.
     *
     * A caption is wider than the square it names, and centring it means starting left of that square
     * - which no border can express, because insets cannot be negative. So the cell itself is moved
     * back a column when the grid is built, and this is how much room that bought. The left inset is
     * then measured back from it: full at rest, less as the caption gets wider, and never past what
     * was bought, so the caption cannot escape into the column before the one its cell starts in.
     */
    private int backShift = 0;

    /**
     * How far down its square this caption sits - see captionOffset.
     */
    private int down = 0;

    /**
     * Turns this label into a pill, or back into ordinary text.
     *
     * Opacity is taken OVER: a JLabel fills its own rectangle when opaque, which is exactly the shape
     * being replaced, so the fill is painted here and the label itself is left transparent. Callers go
     * on setting a background colour and it goes on meaning the same thing.
     *
     * @param on whether to draw as a pill
     */
    public void setPill(boolean on)
    {
        this.pill = on;

        if (on)
        {
            setOpaque(false);
            setHorizontalAlignment(CENTER);
        }
    }

    /**
     * @return whether this label draws itself as a pill
     */
    public boolean isPill()
    {
        return pill;
    }

    /**
     * Tells this caption about the square it belongs to, and places it on it.
     *
     * @param tile the size of a square in pixels
     * @param backShift how far left of its own square the cell was allowed to start
     * @param down how far down the square the caption sits
     */
    public void setTileGeometry(int tile, int backShift, int down)
    {
        this.tile = tile;
        this.backShift = backShift;
        this.down = down;

        place();
    }

    /**
     * Puts the caption where it belongs on its square, from what it currently says.
     *
     * Called on every change of text, because the text is the only thing here that changes and the
     * width of a caption is the whole question: at rest a station shows a dash, and a moment later it
     * shows a locomotive's name and is four times as wide. A constraint set when the grid was built
     * cannot know either.
     *
     * Measured from the FONT rather than from getPreferredSize, which would ask the border that is
     * about to be set and answer differently every time it was called.
     */
    private void place()
    {
        if (!pill || tile <= 0) return;

        int wide = width();

        // Half the overflow, and never more than the cell was moved back by. Clamped rather than
        // allowed to run: a caption that started before its cell would be laid out at the cell's edge
        // anyway, and the clamp is what keeps a long name at the left edge of the diagram from being
        // treated differently from one in the middle.
        int half = Math.max(0, (wide - tile) / 2);

        int left = Math.max(0, backShift - Math.min(half, backShift));

        setBorder(javax.swing.BorderFactory.createEmptyBorder(down, left, 0, 0));
    }

    /**
     * How wide the pill needs to be for what this caption currently says.
     */
    private int width()
    {
        java.awt.Font font = getFont();

        if (font == null) return 0;

        String text = getText();

        if (text == null || text.isEmpty()) return 0;

        return getFontMetrics(font).stringWidth(text)
            + (int) Math.round(lineHeight() * SIDE_PADDING * 2);
    }

    @Override
    public void setText(String text)
    {
        super.setText(text);

        // Every caller goes through here - the grid when it seeds one, the running diagram every time
        // a train moves - so there is one place that has to remember to put the caption back where it
        // belongs, and it is this one.
        place();
    }

    /**
     * How far down its square a caption sits, in pixels from the top of the square.
     *
     * Adam: "land them so that they align just below straight tracks if the track goes east to west,
     * or centered over the track if north to south" - and then "remember to adjust for the 60px view",
     * which is why this takes the tile size and the line height rather than deciding in pixels. Every
     * answer here is a proportion of those two, so the same rule holds at 20 pixels and at 80.
     *
     * The rail runs across the middle of the square. A caption CENTRED on the square is centred on the
     * rail, which is what a north-south run wants: the track goes up the square and the caption lies
     * across it. An east-west run wants the caption clear of the rail instead, so it starts one line
     * down - which is exactly the offset the old multiline hack produced, a leading `<br>` being an
     * offset written in line heights, and it is what this diagram has looked like for a year.
     *
     * Public and taking numbers rather than components so that the thing worth checking - that it is
     * still right at 60 pixels - can be checked without building a grid.
     *
     * @param tile the tile size in pixels
     * @param line how tall one line of the caption's text is
     * @param northSouth whether the rails run up the square rather than across it
     * @return the top inset for the caption
     */
    public static int captionOffset(int tile, int line, boolean northSouth)
    {
        if (line <= 0) return 0;

        if (northSouth) return Math.max(0, (tile - line) / 2);

        return Math.max(0, Math.min(line, tile));
    }

    /**
     * Black or white, whichever can be read on this fill.
     *
     * White on navy is the look that was asked for, and white on the yellow that marks a destination is
     * unreadable - so the colour of the text follows the colour underneath it rather than being fixed.
     * The weights are the usual perceived-brightness ones: the eye takes far more of its brightness
     * from green than from blue.
     *
     * @param fill the pill colour
     * @return the colour to write on it
     */
    public static Color readableOn(Color fill)
    {
        if (fill == null) return Color.WHITE;

        double brightness =
            (0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue()) / 255.0;

        return brightness > 0.6 ? Color.BLACK : Color.WHITE;
    }

    /**
     * The text colour to use on a pill, keeping what the caller meant by the one it asked for.
     *
     * The running diagram says two things with the text colour: red for a station the train has not
     * reached yet, and plain for one it has. Those were chosen against a white label. On a navy pill
     * plain black is invisible and red is nearly so, and simply forcing white would throw away the
     * distinction rather than redraw it - so red stays red and is lightened or darkened until it can
     * be read, and everything else becomes whichever of black or white the fill allows.
     *
     * @param fill the pill colour
     * @param wanted the colour the caller asked for
     * @return a colour that says the same thing and can be read
     */
    public static Color onPill(Color fill, Color wanted)
    {
        boolean dark = readableOn(fill) == Color.WHITE;

        if (wanted != null && wanted.getRed() > 150
            && wanted.getGreen() < 100 && wanted.getBlue() < 100)
        {
            // Still red, still meaning "not reached yet", and legible on either ground.
            return dark ? new Color(255, 150, 150) : new Color(150, 0, 0);
        }

        return readableOn(fill);
    }

    @Override
    public java.awt.Dimension getPreferredSize()
    {
        java.awt.Dimension was = super.getPreferredSize();

        if (!pill) return was;

        // Room at the ends measured from the LINE height rather than from the component height, which
        // now includes however far down the square this caption has been pushed.
        was.width += (int) Math.round(lineHeight() * SIDE_PADDING * 2);

        return was;
    }

    /**
     * How tall one line of this caption's text is.
     *
     * The pill's own height, as distinct from the component's - a caption carries a top border to
     * place it on its square, so the two stopped being the same thing.
     *
     * @return the line height, or a sane guess before the font is known
     */
    public int lineHeight()
    {
        java.awt.Font font = getFont();

        if (font == null) return 12;

        return getFontMetrics(font).getHeight();
    }

    /**
     * Only the pill takes the mouse - the room around it belongs to the diagram underneath.
     *
     * A caption is placed with INSETS: a left inset to centre it on its square, a top inset to put it
     * below the rail. Insets are invisible to the eye and completely opaque to Swing, which hit-tests
     * the whole component - and this component is z-ordered to the front, over the tiles, carrying
     * click and hover listeners. So a caption bought a column of centring room and took every click in
     * it, on the tile to its LEFT.
     *
     * At rest a station shows a single dash, so the left inset is almost the whole tile: the worst
     * case is the ordinary state of most of the railway most of the time. What it cost was a switch
     * that would not throw when clicked in the middle, and - because the caption's hover handler
     * reports the STATION rather than the square under the pointer - a Ctrl+X that cut the train off
     * a platform instead of the tile being pointed at.
     *
     * Found by a reviewer asking getDeepestComponentAt what was under the middle of each tile. The
     * bounds harness could not see it: no tile MOVED, and nothing was asking what covered them.
     *
     * @param x in this component's coordinates
     * @param y in this component's coordinates
     * @return whether the pointer is on the part of this caption that is actually drawn
     */
    @Override
    public boolean contains(int x, int y)
    {
        if (!pill) return super.contains(x, y);

        java.awt.Insets pad = getInsets();

        int top = pad == null ? 0 : pad.top;
        int left = pad == null ? 0 : pad.left;

        return x >= left && x < getWidth() - (pad == null ? 0 : pad.right)
            && y >= top && y < getHeight() - (pad == null ? 0 : pad.bottom);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (pill && getBackground() != null && !getText().isEmpty())
        {
            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(getBackground());

                // INSIDE the insets, which is what lets the caption be moved without being stretched.
                //
                // Where a caption sits on its square is set with a top border - the label's cell runs
                // to the bottom of the diagram, so there is nothing to anchor against - and a pill
                // painted over the whole component would grow downwards from the top of the square
                // instead of moving down it.
                java.awt.Insets pad = getInsets();

                int top = pad == null ? 0 : pad.top;
                int left = pad == null ? 0 : pad.left;
                int high = getHeight() - top - (pad == null ? 0 : pad.bottom);
                int wide = getWidth() - left - (pad == null ? 0 : pad.right);

                if (high <= 0 || wide <= 0) return;

                // An arc as tall as the pill gives semicircular ends, which is the oval the request
                // asks for at any width - a fixed arc turns into a rectangle with rounded corners as
                // soon as the name is long.
                g2.fillRoundRect(left, top, wide, high, high, high);
            }
            finally
            {
                g2.dispose();
            }
        }

        super.paintComponent(g);
    }
}
