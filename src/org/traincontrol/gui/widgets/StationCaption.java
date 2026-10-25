package org.traincontrol.gui.widgets;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.TrainControlUI;

/**
 * A station caption, drawn as a rounded pill instead of a rectangle of text (FR-028): the same badge as on the auto loc
 * panels in the autonomy tab.
 *
 * A JLabel subclass rather than a new kind of component, because the caption is registered, colored, re-texted and
 * clicked from a dozen places that all know it as a JLabel.  What changes is the painting: the fill comes from
 * `getBackground()`, so every color the running diagram already uses to say something still says it, through the tile
 * art (those colors carry their own alpha), and the text is legible on top of whatever the color turned out to be.
 *
 * @author Adam
 */
public class StationCaption extends JLabel
{
    /**
     * The blue of the station badge on an autonomy locomotive panel, and of the diagram's own station badges and route:
     * the same value, so the three cannot drift apart.  The Auto tab's badge asks this constant.
     */
    public static final Color PILL = org.traincontrol.automationui.DiagramColours.STATION;

    /**
     * The resting fill, with the diagram's own translucency: the tile art shows through, which stops a row of captions
     * reading as holes punched in the diagram.
     */
    public static final Color PILL_AT_REST =
        new Color(PILL.getRed(), PILL.getGreen(), PILL.getBlue(), LayoutGrid.LAYOUT_STATION_OPACITY);

    /**
     * The other resting color: light gray, the default, for a diagram somebody wants quieter (FR-031); blue is the
     * optional setting (`TrainControlUI.stationLabelsAreGrey`).  Light enough that readableOn makes the text black.
     */
    public static final Color PILL_GREY =
        new Color(196, 198, 202, LayoutGrid.LAYOUT_STATION_OPACITY);

    /**
     * A running train's destination: `DiagramColours.DESTINATION`, solid rather than translucent.  A pill with this
     * fill draws a ring in a dark orange.
     */
    public static final Color DESTINATION_FILL = new Color(org.traincontrol.automationui.DiagramColours.DESTINATION.getRed(),
        org.traincontrol.automationui.DiagramColours.DESTINATION.getGreen(),
        org.traincontrol.automationui.DiagramColours.DESTINATION.getBlue());

    /** Whether this fill is the destination's, by color and not by alpha (a ring belongs round it). */
    public static boolean isDestination(Color fill)
    {
        return fill != null && (fill.getRGB() & 0xFFFFFF) == (DESTINATION_FILL.getRGB() & 0xFFFFFF);
    }

    /** The ring round a destination, in a dark orange (`DiagramColours.DESTINATION_RING`). */
    private static void ring(Graphics2D g, int x, int y, int w, int h)
    {
        g.setColor(org.traincontrol.automationui.DiagramColours.DESTINATION_RING);
        outline(g, x, y, w, h, Math.max(1.4f, h / 12f));
    }

    /**
     * An outline round a pill, inside it by half its own width on every side.  Not `drawRoundRect(x, y, w - 1, h - 1)`:
     * with anti-aliasing and stroke normalizing that puts the left and top half outside the fill and the right and
     * bottom wholly inside it, so the pill looks heavier at the lower right.
     */
    private static void outline(Graphics2D g, int x, int y, int w, int h, float width)
    {
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setStroke(new java.awt.BasicStroke(width));

        double in = width / 2.0, arc = h - width;

        g.draw(new java.awt.geom.RoundRectangle2D.Double(x + in, y + in, w - width, h - width, arc, arc));
    }

    /** A pill's fill, as the exact shape - see `outline`. */
    private static void fillPill(Graphics2D g, int x, int y, int w, int h)
    {
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, h, h));
    }

    /**
     * Whichever resting color the operator has chosen (FR-031).  Asked when a caption is colored rather than stored: a
     * preference read once into a constant needs a restart.
     *
     * @return the fill for a caption with nothing else to say
     */
    public static Color restingFill()
    {
        return TrainControlUI.stationLabelsAreGrey() ? PILL_GREY : PILL_AT_REST;
    }

    /**
     * Room at the ends, so the text does not run into the curve.  A fraction of the height, because the caption font is
     * sized from the tile; kept small, as every non-text pixel covers somebody else's track.
     */
    private static final double SIDE_PADDING = 0.27;

    /**
     * How much smaller a caption is than the diagram's other text: a pill is taller than its text, and rows are one
     * tile apart.
     */
    public static final float FONT_SCALE = 0.81f;

    /** The room above and below the letters in a pill on end, as a share of its text's size. */
    private static final double ON_END_PAD = 0.12;

    /** Whether this label is a caption at all: the user's own writing on the diagram must not become blue pills. */
    private boolean pill = false;

    /** The size of the square this caption belongs to, in pixels, or 0 before it is told. */
    private int tile = 0;

    /**
     * How far LEFT of its own square the caption's cell was allowed to start, in pixels: centring a wide caption means
     * starting left of its square, which insets cannot express, so the cell is moved back a column when the grid is
     * built.  The left inset is measured back from it and never goes past what was bought.
     */
    private int backShift = 0;

    /**
     * How far past its square's leading edge this caption sits - see captionOffset: down the square for a caption lying
     * across it, right of it for one stood on end.
     */
    private int offset = 0;

    /**
     * Whether this caption is stood on end, reading upwards, on north-south track: laid across the rail, a caption
     * covered the track it named, and on a busy column its neighbors too.
     */
    private boolean rotated = false;

    /** How far ABOVE its own square the caption's cell was allowed to start: the vertical twin of `backShift`. */
    private int upShift = 0;

    /**
     * Turns this label into a pill, or back into ordinary text.  Opacity is taken over: the fill is painted here and
     * the label itself left transparent, so callers go on setting a background color and it means the same thing.
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
     * Whether this label's letters are drawn with a white halo round them, as an address's are, so writing laid over
     * black track can be read.  For text that is not a pill: a pill has its own fill.
     *
     * @param on whether to draw the halo
     */
    public void setHalo(boolean on)
    {
        this.halo = on;

        repaint();
    }

    /** Whether the letters are drawn with a halo (`setHalo`). */
    private boolean halo = false;

    /** The halo's stroke width: a sixth of the letters' size, never under two pixels, half of it outside them. */
    private float haloWidth()
    {
        return Math.max(2f, getFont().getSize2D() / 6f);
    }

    /**
     * The halo round the letters, where the label's own painter will draw them: a round-joined white stroke round each
     * letter's outline.  Inside the label's bounds, so no row of the diagram moves (OB-115).
     *
     * @param g the label's graphics
     */
    private void paintHalo(Graphics g)
    {
        String text = getText();

        if (text == null || text.isEmpty() || getFont() == null) return;

        // Writing set as HTML too (MT-716): on a square that also has track it is `<html><br>` and the words, which the
        // plain-text path below would skip.
        if (javax.swing.plaf.basic.BasicHTML.isHTMLString(text))
        {
            paintHtmlHalo(g, text);

            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            java.awt.FontMetrics fm = g2.getFontMetrics(getFont());

            java.awt.Insets in = getInsets();

            java.awt.Rectangle view = new java.awt.Rectangle(in.left, in.top, getWidth() - in.left - in.right,
                getHeight() - in.top - in.bottom);

            java.awt.Rectangle icon = new java.awt.Rectangle(), place = new java.awt.Rectangle();

            // where JLabel's own painter puts the text, and what of it fits
            String shown = javax.swing.SwingUtilities.layoutCompoundLabel(this, fm, text, getIcon(), getVerticalAlignment(),
                getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), view, icon, place,
                getIconTextGap());

            if (shown == null || shown.isEmpty()) return;

            java.awt.Shape letters = new java.awt.font.TextLayout(shown, getFont(), g2.getFontRenderContext())
                .getOutline(java.awt.geom.AffineTransform.getTranslateInstance(place.x, place.y + fm.getAscent()));

            g2.setColor(AddressLabel.HALO);
            g2.setStroke(new java.awt.BasicStroke(haloWidth(), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(letters);
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * The halo round writing set as HTML: each line outlined where the HTML view draws it.
     *
     * @param g the label's graphics
     * @param text the label's HTML
     */
    private void paintHtmlHalo(Graphics g, String text)
    {
        Object property = getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);

        if (!(property instanceof javax.swing.text.View)) return;

        javax.swing.text.View view = (javax.swing.text.View) property;

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            java.awt.FontMetrics fm = g2.getFontMetrics(getFont());

            java.awt.Insets in = getInsets();

            java.awt.Rectangle viewR = new java.awt.Rectangle(in.left, in.top, getWidth() - in.left - in.right,
                getHeight() - in.top - in.bottom);

            java.awt.Rectangle icon = new java.awt.Rectangle(), place = new java.awt.Rectangle();

            // where JLabel's own painter puts the view
            javax.swing.SwingUtilities.layoutCompoundLabel(this, fm, text, getIcon(), getVerticalAlignment(),
                getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), viewR, icon, place,
                getIconTextGap());

            javax.swing.text.Document document = view.getDocument();

            String words = document.getText(0, document.getLength());

            java.awt.geom.Path2D letters = new java.awt.geom.Path2D.Double();

            int start = 0;

            while (start < words.length())
            {
                int end = words.indexOf('\n', start);

                if (end < 0) end = words.length();

                // From its first letter: the space HTML leaves after a <br> ends the line above, not this one, and
                // measured from it the halo was drawn a line too high
                int first = start;

                while (first < end && Character.isWhitespace(words.charAt(first))) first++;

                String line = words.substring(first, end).replace('\u00a0', ' ');

                if (!line.trim().isEmpty())
                {
                    java.awt.Shape where = view.modelToView(first, place, javax.swing.text.Position.Bias.Forward);

                    if (where != null)
                    {
                        java.awt.Rectangle at = where.getBounds();

                        letters.append(new java.awt.font.TextLayout(line, getFont(), g2.getFontRenderContext())
                            .getOutline(java.awt.geom.AffineTransform.getTranslateInstance(at.x, at.y + fm.getAscent())),
                            false);
                    }
                }

                start = end + 1;
            }

            g2.setColor(AddressLabel.HALO);
            g2.setStroke(new java.awt.BasicStroke(haloWidth(), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(letters);
        }
        catch (javax.swing.text.BadLocationException e)
        {
            // no halo, as before: the words are drawn regardless
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * Stands this caption on end, or lays it flat again.
     *
     * @param on whether the caption reads upwards, for track that runs up the square
     */
    public void setRotated(boolean on)
    {
        this.rotated = on;

        place();
    }

    /**
     * @return whether this caption reads upwards rather than across
     */
    public boolean isRotated()
    {
        return rotated;
    }

    /**
     * The text as it is actually DRAWN, with its arrows turned to match the caption: the rotation turns the arrows too,
     * so a glyph is chosen for where it ends up (a quarter turn anticlockwise sends right to up, so north is drawn with
     * the east glyph).  At drawing time, so `getText` still returns what the caller set.  Replaced in ONE pass:
     * sequential replacements would send north to east and on to south.
     *
     * @return what to draw
     */
    private String drawnText()
    {
        String text = getText();

        if (!rotated || text == null || text.isEmpty()) return text;

        StringBuilder out = new StringBuilder(text.length());

        for (int at = 0; at < text.length(); at++)
        {
            String one = text.substring(at, at + 1);

            if (ARROW_N.equals(one)) out.append(ARROW_E);
            else if (ARROW_E.equals(one)) out.append(ARROW_S);
            else if (ARROW_S.equals(one)) out.append(ARROW_W);
            else if (ARROW_W.equals(one)) out.append(ARROW_N);
            else out.append(one);
        }

        return out.toString();
    }

    /**
     * Tells this caption about the square it belongs to, and places it on it.
     *
     * @param tile the size of a square in pixels
     * @param backShift how far left of its own square the cell was allowed to start
     * @param upShift how far above its own square the cell was allowed to start
     * @param offset how far past the square's leading edge the caption sits
     */
    public void setTileGeometry(int tile, int backShift, int upShift, int offset)
    {
        this.tile = tile;
        this.backShift = backShift;
        this.upShift = upShift;
        this.offset = offset;

        place();
    }

    /**
     * Puts the caption where it belongs on its square, from what it currently says.  Called on every change of text,
     * because the width is the whole question (a dash at rest, a locomotive's name a moment later).  Measured from the
     * font rather than getPreferredSize, which would ask the border that is about to be set.
     */
    private void place()
    {
        if (!pill || tile <= 0) return;

        int wide = width();

        // Centered on the square, whichever way the difference goes (OB-118): an empty station shows a dash narrower
        // than a tile, a locomotive name is wider, and one expression covers both:
        //
        //     left = backShift + (tile - wide) / 2
        //
        // The clamp stays because a caption cannot start before its own cell.
        if (rotated)
        {
            // Stood on end: centered along the rail, which now runs up the square, and set off to the side by the
            // distance a flat caption is set below it.  The two clamps are the two shifts: insets cannot be negative,
            // so a name longer than the room bought above the square starts at the top of it.
            int top = Math.max(0, upShift + (tile - wide) / 2);

            // Across: centered on the line between its square and the next (OB-327), the middle of the gap between
            // badges.
            setBorder(javax.swing.BorderFactory.createEmptyBorder(top, Math.max(0, backShift + tile - thickness() / 2),
                0, 0));

            return;
        }

        int left = Math.max(0, backShift + (tile - wide) / 2);

        setBorder(javax.swing.BorderFactory.createEmptyBorder(offset, left, 0, 0));
    }

    /** How wide the pill needs to be for what this caption currently says. */
    private int width()
    {
        java.awt.Font font = getFont();

        if (font == null) return 0;

        // The DRAWN text, so a rotated caption is measured with the arrows it will actually be given.
        String text = drawnText();

        if (text == null || text.isEmpty()) return 0;

        if (rotated)
        {
            return getFontMetrics(drawnFont()).stringWidth(text) + (int) Math.round(thickness() * SIDE_PADDING * 2);
        }

        return getFontMetrics(font).stringWidth(text)
            + (int) Math.round(lineHeight() * SIDE_PADDING * 2);
    }

    @Override
    public void setText(String text)
    {
        super.setText(text);

        // Every caller goes through here, so this is the one place that has to put the caption back where it belongs.
        place();
    }

    /**
     * How far down its square a caption sits, in pixels from the top of the square.  A proportion of the tile size and
     * line height, so the same rule holds at 20 pixels and at 80: one line past the rail through the middle, plus a
     * nudge.  The one answer serves both orientations (stood on end, it is the distance to the right of a rail running
     * up the square).  Public and taking numbers so it can be checked at 60 pixels without building a grid.
     *
     * @param tile the tile size in pixels
     * @param line how tall one line of the caption's text is
     * @return how far past the square's leading edge the caption sits
     */
    public static int captionOffset(int tile, int line)
    {
        if (line <= 0) return 0;

        return Math.max(0, Math.min(line, tile)) + nudge(tile);
    }

    /**
     * How far below where the geometry puts it a caption actually sits: nine pixels at the sixty-pixel view.  A
     * fraction of the tile, as everything else about a caption is derived from the tile; applied to both orientations,
     * since a caption hard against the track it names reads as sitting on the track.
     *
     * @param tile the tile size in pixels
     * @return the extra offset
     */
    private static int nudge(int tile)
    {
        return Math.max(1, tile * 3 / 20);
    }

    /**
     * Black or white, whichever can be read on this fill: white on the blue pill is the look, white on a light fill is
     * unreadable.  The weights are the usual perceived-brightness ones (the eye takes most of its brightness from
     * green).
     *
     * @param fill the pill color
     * @return the color to write on it
     */
    public static Color readableOn(Color fill)
    {
        if (fill == null) return Color.WHITE;

        double brightness =
            (0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue()) / 255.0;

        return brightness > 0.6 ? Color.BLACK : Color.WHITE;
    }

    /**
     * The font the diagram's labels are drawn in, and the four arrows that go in them (OB-116).  In Segoe UI the
     * horizontal triangles (U+25BA and U+25C4, the only ones it can draw) are half the height of the vertical ones, and
     * the geometric ones come out as empty boxes.  `Segoe UI Symbol` draws every word at exactly the same size and has
     * the geometric triangles, so the four arrows become one matched set (U+25B2 up is 78.3 x 70.0, U+25B6 right the
     * exact transpose).
     *
     * Chosen once, at class load, and guarded: on a machine without that font the pointers stay, since a squat arrow is
     * better than an empty box.
     */
    public static final String LABEL_FONT;

    /** The font that has the matched arrows, if this machine has it. */
    private static final String SYMBOL_FONT = "Segoe UI Symbol";

    /** North. */
    public static final String ARROW_N = "\u25B2";

    /** South. */
    public static final String ARROW_S = "\u25BC";

    /** East, and west - the pair that depends on which font is available. */
    public static final String ARROW_E;

    /** West. */
    public static final String ARROW_W;

    static
    {
        // Installed, and then able, in that order: `new Font("no such family", ...)` resolves to Dialog, and
        // `canDisplay` then answers for Dialog.  The family list is the only thing that answers "is it installed"; read
        // once here, as enumerating fonts can be slow.
        java.awt.Font symbol = new java.awt.Font(SYMBOL_FONT, java.awt.Font.PLAIN, 12);

        boolean installed = false;

        try
        {
            for (String family : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames())
            {
                if (SYMBOL_FONT.equalsIgnoreCase(family))
                {
                    installed = true;

                    break;
                }
            }
        }
        catch (RuntimeException headless)
        {
            // No graphics environment at all, which is a machine with no diagram to draw on either.
            installed = false;
        }

        boolean matched = installed && symbol.canDisplay(0x25B6) && symbol.canDisplay(0x25C0)
            && symbol.canDisplay(0x25B2) && symbol.canDisplay(0x25BC);

        LABEL_FONT = matched ? SYMBOL_FONT : "Segoe UI";

        ARROW_E = matched ? "\u25B6" : "\u25BA";
        ARROW_W = matched ? "\u25C0" : "\u25C4";
    }

    /**
     * Whether this arrow goes BEFORE the name rather than after it.  South and west lead: put in front, a direction
     * that points back the way the text came from points at its own label, and on a caption stood on end (read from the
     * bottom up) the start of the string is the bottom of the pill.  Asked of the glyph, because the two places that
     * join a name to an arrow have the string and not the direction.
     *
     * @param arrow the arrow, with or without the space it is usually carried with
     * @return whether it leads
     */
    public static boolean arrowLeads(String arrow)
    {
        if (arrow == null) return false;

        String bare = arrow.trim();

        return ARROW_S.equals(bare) || ARROW_W.equals(bare);
    }

    /**
     * A name and its arrow, joined with the arrow on the side it points towards.  The one place that join happens, so a
     * caption showing one train places the arrow as one showing two does.
     *
     * @param name the station or locomotive name, already cut to length
     * @param arrow the arrow, or null or empty for none
     * @return the caption text
     */
    public static String withArrow(String name, String arrow)
    {
        String bare = arrow == null ? "" : arrow.trim();

        if (bare.isEmpty()) return name;

        return arrowLeads(bare) ? bare + " " + name : name + " " + bare;
    }

    /**
     * The text color to use on a pill, keeping what the caller meant by the one it asked for.  Red (a station not yet
     * reached) stays red, lightened or darkened until it can be read; forcing white would throw the distinction away.
     * A neutral gray (a PLACEHOLDER: an unnamed station's em-dash, a yard name not for this editor) would otherwise
     * fall through to `readableOn` and come back as the same color a name does, so it is the readable color carried
     * part of the way back towards the pill: dimmer than a name on any fill, and still legible.
     *
     * @param fill the pill color
     * @param wanted the color the caller asked for
     * @return a color that says the same thing and can be read
     */
    public static Color onPill(Color fill, Color wanted)
    {
        Color readable = readableOn(fill);

        boolean dark = readable == Color.WHITE;

        if (wanted != null && wanted.getRed() > 150
            && wanted.getGreen() < 100 && wanted.getBlue() < 100)
        {
            // Still red, still meaning "not reached yet", and legible on either ground.
            return dark ? new Color(255, 150, 150) : new Color(150, 0, 0);
        }

        // THE ROUTE'S BLUE: a station a running train has not reached yet, said in the color the route ahead is drawn
        // in - itself on a light pill, lifted to a light blue on the blue one, where the route's own blue would disappear.
        if (wanted != null && wanted.getBlue() > 150 && wanted.getRed() < 80 && wanted.getGreen() < 80)
        {
            return dark ? new Color(165, 185, 255) : wanted;
        }

        if (isNeutralGrey(wanted))
        {
            // Still dimmer than a name, on whichever ground, but not so far towards the pill that it stops being
            // readable on the navy and pale gray fills.
            return blend(readable, fill, 0.45);
        }

        return readable;
    }

    /**
     * Whether a color is one of the grays the editors use to mean "this is a placeholder".  By the shape of the color,
     * since the constant is written out longhand at four places in LayoutGrid; black and white are excluded, being the
     * answer colors that a rule dimming them would dim on every ordinary caption.
     *
     * @param wanted the color the caller asked for
     * @return whether it is a neutral gray
     */
    private static boolean isNeutralGrey(Color wanted)
    {
        if (wanted == null) return false;

        int r = wanted.getRed();

        return r == wanted.getGreen() && r == wanted.getBlue() && r > 60 && r < 220;
    }

    /** A color moved part of the way towards another: howFar 0 is `from` unchanged, 1 is `towards`. */
    private static Color blend(Color from, Color towards, double howFar)
    {
        return new Color(
            (int) Math.round(from.getRed() + (towards.getRed() - from.getRed()) * howFar),
            (int) Math.round(from.getGreen() + (towards.getGreen() - from.getGreen()) * howFar),
            (int) Math.round(from.getBlue() + (towards.getBlue() - from.getBlue()) * howFar));
    }

    /**
     * Where the pill is actually drawn, in this component's own coordinates: the single answer that painting,
     * hit-testing and sizing all ask.  Flat, the pill is as wide as its text and one line tall; stood on end those
     * swap, and the insets mean the same either way: how far past the leading edge of its square it sits.
     *
     * @return the drawn rectangle, or null when there is nothing drawn
     */
    private java.awt.Rectangle pillBounds()
    {
        if (!pill) return null;

        java.awt.Insets pad = getInsets();

        int top = pad == null ? 0 : pad.top;
        int left = pad == null ? 0 : pad.left;

        if (rotated)
        {
            int len = width();
            int thick = thickness();

            if (len <= 0 || thick <= 0) return null;

            return new java.awt.Rectangle(left, top, thick, len);
        }

        int high = getHeight() - top - (pad == null ? 0 : pad.bottom);
        int wide = getWidth() - left - (pad == null ? 0 : pad.right);

        if (high <= 0 || wide <= 0) return null;

        return new java.awt.Rectangle(left, top, wide, high);
    }

    /**
     * Where this caption is actually drawn inside its own component, for a drag snapshot (FR-035): the cell runs to the
     * bottom of the diagram, so the component is mostly empty space.
     *
     * @return the drawn rectangle, or null when nothing is drawn
     */
    public java.awt.Rectangle drawnBounds()
    {
        return pillBounds();
    }

    @Override
    public java.awt.Dimension getPreferredSize()
    {
        java.awt.Dimension was = super.getPreferredSize();

        if (!pill) return was;

        if (rotated)
        {
            // Asked of the pill rather than of the text: super.getPreferredSize measures along the text, so a rotated
            // caption would get a box one line tall and its name's length wide, then draw itself out of the bottom of
            // it.
            java.awt.Insets pad = getInsets();

            return new java.awt.Dimension(
                thickness() + (pad == null ? 0 : pad.left + pad.right),
                width() + (pad == null ? 0 : pad.top + pad.bottom));
        }

        // Room at the ends measured from the LINE height, not the component height, which includes however far down the
        // square the caption was pushed.
        was.width += (int) Math.round(lineHeight() * SIDE_PADDING * 2);

        return was;
    }

    /**
     * How tall one line of this caption's text is: the pill's own height, as distinct from the component's, which
     * carries a top border.
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
     * The font the text is DRAWN in: the caption's own, flat or stood on end, at full size (FR-118).  A pill on end is
     * only as thick as its letters (`thickness`, OB-327), which leaves it room beside the station badges either side,
     * no taller across their track than the sensor's contact (13 pixels at 30, 26 at 60):
     * `testAPillOnEndClearsTheStationsBesideIt`.
     *
     * @return the font
     */
    private java.awt.Font drawnFont()
    {
        return getFont();
    }

    /**
     * How thick the pill is across its text: the line's height flat, and on end as thick as the letters need (OB-327),
     * since the font's line runs higher than any letter and was width spent against the stations either side.
     *
     * @return the pill's thickness in pixels
     */
    private int thickness()
    {
        if (!rotated) return lineHeight();

        java.awt.Font font = drawnFont();

        if (font == null) return lineHeight();

        java.awt.font.FontRenderContext frc = getFontMetrics(font).getFontRenderContext();

        double letters = font.createGlyphVector(frc, "Hgjy").getVisualBounds().getHeight();

        return (int) Math.ceil(letters + 2 * font.getSize2D() * ON_END_PAD);
    }

    /**
     * Only the pill takes the mouse - the room around it belongs to the diagram underneath.  A caption is placed with
     * insets, which Swing hit-tests as part of the component, and this one is z-ordered in front of the tiles with
     * click and hover listeners: without this its centring room took every click on the tile to its left, and, since
     * the hover handler reports the STATION, a Ctrl+X cut the train off a platform instead of the tile pointed at.
     *
     * @param x in this component's coordinates
     * @param y in this component's coordinates
     * @return whether the pointer is on the part of this caption that is actually drawn
     */
    @Override
    public boolean contains(int x, int y)
    {
        if (!pill) return super.contains(x, y);

        java.awt.Rectangle drawn = pillBounds();

        return drawn != null && drawn.contains(x, y);
    }

    /**
     * Draws a caption that has been stood on end, here rather than by JLabel, which lays text out along its own width.
     * The transform is a quarter turn anticlockwise (`rotate(-PI/2)`), sending local +x to screen up so the text reads
     * from the bottom of the pill; translating to the bottom first puts the start of the text there, not off the top of
     * the diagram.
     */
    private void paintOnEnd(Graphics g, java.awt.Rectangle drawn)
    {
        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.translate(drawn.x, drawn.y + drawn.height);
            g2.rotate(-Math.PI / 2);

            // From here on this is the flat case exactly: a pill `drawn.height` long and `drawn.width`
            // thick, with the text along it.
            int len = drawn.height;
            int thick = drawn.width;

            if (getBackground() != null)
            {
                g2.setColor(getBackground());
                fillPill(g2, 0, 0, len, thick);

                if (isDestination(getBackground())) ring(g2, 0, 0, len, thick);
                else if (namesSomething()) edge(g2, 0, 0, len, thick);
            }

            // In the middle of the pill, measured as drawn - the same as the flat pill
            paintText(g2, 0, 0, len, thick);
        }
        finally
        {
            g2.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (pill && rotated)
        {
            java.awt.Rectangle drawn = pillBounds();

            // And NOT super.paintComponent: JLabel would draw the text a second time, flat, across the top of the
            // rotated pill.
            if (drawn != null) paintOnEnd(g, drawn);

            return;
        }

        if (pill && getBackground() != null && !getText().isEmpty())
        {
            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(getBackground());

                // Inside the insets, which is what lets the caption be moved without being stretched: its place on the
                // square is set with a top border (the cell runs to the bottom of the diagram), so a pill painted over
                // the whole component would grow downwards from the top of the square instead of moving down it.
                java.awt.Rectangle drawn = pillBounds();

                if (drawn == null) return;

                // An arc as tall as the pill gives semicircular ends at any width; a fixed arc turns into a rounded
                // rectangle once the name is long.
                fillPill(g2, drawn.x, drawn.y, drawn.width, drawn.height);

                if (isDestination(getBackground())) ring(g2, drawn.x, drawn.y, drawn.width, drawn.height);
                else if (namesSomething()) edge(g2, drawn.x, drawn.y, drawn.width, drawn.height);
            }
            finally
            {
                g2.dispose();
            }
        }

        // The pill's text is drawn here, in the middle of it: the label's own painter places it by the width measured
        // for layout, and at a larger display scale (125 or 150 per cent) the glyphs come out wider, all of the
        // difference landing at the right end.
        if (pill)
        {
            java.awt.Rectangle drawn = pillBounds();

            if (drawn != null) paintText(g, drawn.x, drawn.y, drawn.width, drawn.height);

            return;
        }

        // The diagram's own writing with a halo: white round the letters first, so they read against black tracks, then
        // the label draws over it.
        if (halo) paintHalo(g);

        super.paintComponent(g);
    }

    /**
     * Draws the caption's text in the middle of a pill `len` long and `thick` deep, measured as it is drawn, in a
     * graphics already turned for a rotated caption; x and y are the pill's left and top.
     */
    private void paintText(Graphics g, int x, int y, int len, int thick)
    {
        String text = drawnText();

        if (text == null || text.isEmpty() || getFont() == null) return;

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(drawnFont());
            g2.setColor(getForeground());

            // Measured as drawn, in this graphics, which carries its scale.  Across by the ink, not the advance, which
            // carries the first and last letters' side room.  Up and down by the capitals: the ascent includes room
            // above them that no letter fills, so text centered by ascent and descent sat low (13 pixels of pill above
            // "Carlton", 8 below).
            java.awt.font.FontRenderContext frc = g2.getFontRenderContext();
            java.awt.geom.Rectangle2D ink = drawnFont().createGlyphVector(frc, text).getVisualBounds();
            double capitals = drawnFont().createGlyphVector(frc, "H").getVisualBounds().getHeight();

            float left = (float) (x + (len - ink.getWidth()) / 2.0 - ink.getX());
            float baseline = (float) (y + (thick + capitals) / 2.0);

            g2.drawString(text, left, baseline);
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * Whether this caption names something - a train, a station - rather than holding a placeholder dash or an arrow
     * alone.  Only these get the edge, so an empty station gets no louder.
     *
     * @return whether the text has a letter or a digit in it
     */
    private boolean namesSomething()
    {
        String text = getText();

        if (text == null) return false;

        for (int i = 0; i < text.length(); i++)
        {
            if (Character.isLetterOrDigit(text.charAt(i))) return true;
        }

        return false;
    }

    /**
     * A faint edge round a pill, a shade darker than its fill, so a caption parts from a signal or a switch drawn under
     * it.
     */
    private void edge(Graphics2D g, int x, int y, int w, int h)
    {
        Color f = getBackground();

        g.setColor(new Color(Math.max(0, f.getRed() - 70), Math.max(0, f.getGreen() - 70), Math.max(0, f.getBlue() - 70),
            Math.min(255, f.getAlpha() + 30)));
        outline(g, x, y, w, h, 1f);
    }
}
