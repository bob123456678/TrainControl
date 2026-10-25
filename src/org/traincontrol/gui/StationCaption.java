package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;

/**
 * A station caption, drawn as a rounded pill instead of a rectangle of text (FR-028): a blue background, white
 * text and a fully rounded border, the same badge as on the auto loc panels in the autonomy tab.
 *
 * **A JLabel subclass rather than a new kind of component**, because the caption is registered, colored, re-texted
 * and clicked from a dozen places that all know it as a JLabel (the window's caption registry, `updateStationLabels`,
 * the click and hover listeners, the grid's own styling).
 *
 * What changes is the painting.  The fill comes from `getBackground()`, so every color the running diagram already
 * uses to say something (yellow for the destination, gray for a station that is shut, the resting blue) still says
 * it, and still says it through the tile art underneath because those colors carry their own alpha.  What changes is
 * the SHAPE, and that the text is legible on top of whatever the color turned out to be.
 *
 * @author Adam
 */
public class StationCaption extends JLabel
{
    /**
     * The blue of the station badge on an autonomy locomotive panel, and of the diagram's own station badges and route.
     *
     * The same value, not a similar one: two blues a shade apart on one screen look like a mistake rather than a family.
     * The Auto tab's badge asks this constant, so the three cannot drift apart.
     */
    public static final Color PILL = org.traincontrol.automationui.DiagramColours.STATION;

    /**
     * The resting fill, with the diagram's own translucency: the tile art shows through, which stops a row of captions
     * reading as holes punched in the diagram.
     */
    public static final Color PILL_AT_REST =
        new Color(PILL.getRed(), PILL.getGreen(), PILL.getBlue(), LayoutGrid.LAYOUT_STATION_OPACITY);

    /**
     * The other resting color: light gray, for a diagram somebody wants quieter (FR-031).  It is the default; blue is
     * the optional setting (`TrainControlUI.stationLabelsAreGrey`).
     *
     * A page with thirty stations is thirty blue ovals, so the alternative is a caption that stops competing with the
     * signals and the running path for attention.  Light enough that the text on it goes black, which readableOn works
     * out on its own.
     */
    public static final Color PILL_GREY =
        new Color(196, 198, 202, LayoutGrid.LAYOUT_STATION_OPACITY);

    /**
     * A running train's destination: `DiagramColours.DESTINATION`, solid, not with the diagram's translucency the other
     * pills have.  A pill with this fill draws a ring in a dark orange: the fill says "destination", as every color
     * this caption carries says something, and the ring follows it.
     */
    public static final Color DESTINATION_FILL = new Color(org.traincontrol.automationui.DiagramColours.DESTINATION.getRed(),
        org.traincontrol.automationui.DiagramColours.DESTINATION.getGreen(),
        org.traincontrol.automationui.DiagramColours.DESTINATION.getBlue());

    /**
     * Whether this fill is the destination's, by color and not by alpha.
     *
     * @param fill the pill's fill
     * @return whether a ring belongs round it
     */
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
     * An outline round a pill, inside it by half its own width on EVERY side.
     *
     * Not `drawRoundRect(x, y, w - 1, h - 1)`: with anti-aliasing, and Java's stroke normalizing nudging it half a
     * pixel, that puts the outline's left and top half outside the fill and its right and bottom wholly inside it, with
     * a sliver of fill showing past them, so the pill looks heavier and tighter at the lower right.  Drawn as the exact
     * shape, with the stroke left pure, the four sides are the same.
     *
     * @param g the graphics, its color set
     * @param x the pill's left
     * @param y its top
     * @param w its length
     * @param h its depth
     * @param width how thick the outline is
     */
    private static void outline(Graphics2D g, int x, int y, int w, int h, float width)
    {
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setStroke(new java.awt.BasicStroke(width));

        double in = width / 2.0, arc = h - width;

        g.draw(new java.awt.geom.RoundRectangle2D.Double(x + in, y + in, w - width, h - width, arc, arc));
    }

    /**
     * A pill's fill, as the exact shape - see `outline`.
     *
     * @param g the graphics, its color set
     * @param x the pill's left
     * @param y its top
     * @param w its length
     * @param h its depth
     */
    private static void fillPill(Graphics2D g, int x, int y, int w, int h)
    {
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, h, h));
    }

    /**
     * Whichever resting color the operator has chosen (FR-031).
     *
     * Asked at the moment a caption is colored rather than stored: a preference read once into a constant needs the
     * application restarted, which is not what "persist as with other settings" means for a switch in a menu.
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
     * A fraction of the height rather than a fixed number of pixels: the caption font is sized from the tile, so a pill
     * padded by four pixels looks generous at 20px tiles and cramped at 60.  Kept small: every pixel of a caption that is
     * not text covers somebody else's track on a crowded diagram.
     */
    private static final double SIDE_PADDING = 0.27;

    /**
     * How much smaller a caption is than the diagram's other text: two tenths.
     *
     * A pill is taller than the text it holds, so a caption set at the same size as the labels around it is a taller
     * object than the one it replaced, on a diagram whose rows are one tile apart.  The number is here rather than at
     * the call site so that changing it is one edit.
     */
    public static final float FONT_SCALE = 0.81f;

    /** The room above and below the letters in a pill on end, as a share of its text's size. */
    private static final double ON_END_PAD = 0.12;

    /**
     * Whether this label is a caption at all.
     *
     * The same JLabel class draws the user's own writing on the diagram (yard names, notes), and those must not become
     * blue pills.  Only what autonomy puts on a square gets the pill.
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
     * How far past its square's leading edge this caption sits - see captionOffset.
     *
     * Down the square for a caption lying across it, right of the square for one stood on end. The
     * same number either way: it is the distance from the rail, and rotating the caption rotated the
     * direction that distance is measured in.
     */
    private int offset = 0;

    /**
     * Whether this caption is stood on end, reading upwards.
     *
     * On north-south track the caption lands just to the right of the tile, similar to how horizontal labels land
     * between tracks, rather than lying across the rail: laid across it, a caption covered the track it named, and on a
     * busy column its neighbors too.
     */
    private boolean rotated = false;

    /**
     * How far ABOVE its own square the caption's cell was allowed to start, in pixels.
     *
     * The vertical twin of `backShift`, and needed for the same reason: a caption stood on end is
     * taller than its square, centring it means starting above that square, and insets cannot be
     * negative. The cell is moved up a row when the grid is built and this is how much room that
     * bought.
     */
    private int upShift = 0;

    /**
     * Turns this label into a pill, or back into ordinary text.
     *
     * Opacity is taken OVER: a JLabel fills its own rectangle when opaque, which is exactly the shape
     * being replaced, so the fill is painted here and the label itself is left transparent. Callers go
     * on setting a background color and it goes on meaning the same thing.
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

    /**
     * How wide the halo's stroke is: a sixth of the letters' size, and never under two pixels.  That is thinner than an
     * address's two-sevenths, because the diagram's own writing is larger, at half the square.  Half of it lies outside
     * the letters: a pixel and a quarter at 30 pixels, two and a half at 60.
     */
    private float haloWidth()
    {
        return Math.max(2f, getFont().getSize2D() / 6f);
    }

    /**
     * The halo round the letters, where the label's own painter will draw them: the white of an address's, a round-joined
     * stroke round each letter's outline, so it follows the letters rather than boxing them.  Inside the label's bounds -
     * its size and its place on the diagram are as they were, so no row of the diagram moves (OB-115) - which the letters'
     * own side room leaves space for.
     *
     * @param g the label's graphics
     */
    private void paintHalo(Graphics g)
    {
        String text = getText();

        if (text == null || text.isEmpty() || getFont() == null) return;

        // WRITING SET AS HTML TOO (MT-716).  Writing on a square that also has track is set as `<html><br>` and the
        // words, to stand a line below the square, and the plain-text path below would skip it.
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
     * The halo round writing set as HTML: each line of its words outlined where the HTML view draws it - the view's own
     * answer for where a line starts, on its baseline in this label's font, which is the font the view is drawn in.
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
     * The text as it is actually DRAWN, with its arrows turned to match the caption.
     *
     * The rotation turns the whole drawing, arrows included, so a glyph is chosen for where it will END UP: a quarter
     * turn anticlockwise sends right to up, so a caption that means north is drawn with the east glyph (the cycle is N
     * to E to S to W and round again).  Done at drawing time rather than in `setText`, so `getText` goes on returning
     * what the caller set and a tooltip or a test gets the direction rather than the glyph that happens to draw it.
     * Replaced in ONE pass: four sequential replacements would send north to east and then that same east on to south.
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

        // CENTERED ON THE SQUARE, whichever way the difference goes (OB-118).  An empty station shows a dash, narrower than
        // a tile, and a locomotive name is wider; one expression covers both, because both ask where this square's center
        // line falls:
        //
        //     left = backShift + (tile - wide) / 2
        //
        // Wider than the tile, `(tile - wide) / 2` is minus half the overflow; narrower, it is the padding that centers
        // it.  The clamp stays because a caption cannot start before its own cell.
        if (rotated)
        {
            // Stood on end: centered ALONG the rail, which now runs up the square, and set off to the
            // side of it by the same distance a flat caption is set below it.
            //
            // The two clamps are the two shifts, and each says a caption cannot start before its own
            // cell. Insets cannot be negative, so a name longer than the room bought above the square
            // is centered as far as that room goes and then simply starts at the top of it.
            int top = Math.max(0, upShift + (tile - wide) / 2);

            // ACROSS: CENTERED ON THE LINE BETWEEN ITS SQUARE AND THE NEXT (OB-327), which is the middle of the gap
            // between this station's badge and the badge of a station one square over.
            setBorder(javax.swing.BorderFactory.createEmptyBorder(top, Math.max(0, backShift + tile - thickness() / 2),
                0, 0));

            return;
        }

        int left = Math.max(0, backShift + (tile - wide) / 2);

        setBorder(javax.swing.BorderFactory.createEmptyBorder(offset, left, 0, 0));
    }

    /**
     * How wide the pill needs to be for what this caption currently says.
     */
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

        // Every caller goes through here - the grid when it seeds one, the running diagram every time
        // a train moves - so there is one place that has to remember to put the caption back where it
        // belongs, and it is this one.
        place();
    }

    /**
     * How far down its square a caption sits, in pixels from the top of the square.
     *
     * Takes the tile size and the line height rather than deciding in pixels: every answer is a proportion of those
     * two, so the same rule holds at 20 pixels and at 80.
     *
     * The rail runs through the middle of the square, and a caption sits clear of it: one line past it, plus a nudge.
     * The one answer serves both orientations: stood on end, the caption sits that distance to the right of a rail that
     * runs up the square, so what was a branch is a direction, and the direction is the caption's business.
     *
     * Public and taking numbers rather than components so that the thing worth checking - that it is
     * still right at 60 pixels - can be checked without building a grid.
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
     * How far below where the geometry puts it a caption actually sits: nine pixels at the sixty-pixel view.
     *
     * A FRACTION OF THE TILE rather than a pixel count, because everything else about a caption (its font, its offset,
     * the room at the ends of its pill) is derived from the tile; a constant nine would be a seventh of the gap at one
     * size and half of it at another.  Applied to BOTH orientations: a caption hard against the track it names reads as
     * sitting on the track.
     *
     * @param tile the tile size in pixels
     * @return the extra offset
     */
    private static int nudge(int tile)
    {
        return Math.max(1, tile * 3 / 20);
    }

    /**
     * Black or white, whichever can be read on this fill.
     *
     * White on the blue pill is the look, and white on a light fill is unreadable, so the color of the text follows the
     * color underneath it rather than being fixed.  The weights are the usual perceived-brightness ones: the eye takes
     * far more of its brightness from green than from blue.
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
     * The font the diagram's labels are drawn in, and the four arrows that go in them (OB-116).
     *
     * In Segoe UI the horizontal triangles are far squatter than the vertical ones:
     *
     *     U+25B2 up      78.3 x 70.0     w/h 1.119
     *     U+25BA right   70.0 x 35.5     w/h 1.970     <- half the height of the up arrow
     *
     * and U+25BA and U+25C4 are the ONLY horizontal triangles Segoe UI can draw at all (U+25B6 and U+25C0, the geometric
     * ones that would be the right shape, come out as empty boxes).  The font is the thing to change, not the
     * character: `Segoe UI Symbol` draws every word this application puts on a diagram at EXACTLY the same size as
     * Segoe UI, and it has the geometric triangles:
     *
     *     U+25B2 up      78.3 x 70.0     w/h 1.119
     *     U+25B6 right   70.0 x 78.3     w/h 0.894     <- the exact transpose
     *
     * So the four arrows become one matched set, rotations of each other, and no station name changes shape by a pixel.
     *
     * Chosen once, at class load, and guarded: on a machine without that font the pointers stay, since a squat arrow is
     * a great deal better than an empty box.
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
        // INSTALLED, and then able - in that order, because the second question does not imply the first (C1).
        //
        // `new Font("no such family", ...)` does not fail: it resolves to Dialog, and `canDisplay` then answers for
        // Dialog, so a missing font asked whether it can draw a triangle says yes and every label would silently render in
        // Dialog.  The family list is the only thing that answers "is it installed", and it is read once here rather than
        // per label: on some systems enumerating fonts is slow enough to notice.
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
     * Whether this arrow goes BEFORE the name rather than after it.
     *
     * South and west lead: a direction reads better leading than trailing when it points back the way the text came
     * from, and put in front it points at its own label.  South matters more on a caption stood on end, which reads
     * from the BOTTOM up, so the start of the string is the bottom of the pill, where a downward arrow wants to be.
     *
     * Asked of the GLYPH rather than of the compass point, because the two places that join a name to an arrow have the
     * string and not the direction.
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
     * A name and its arrow, joined with the arrow on the side it points towards.
     *
     * The ONE place that join happens: two spellings of the same rule are two rules that eventually disagree, and a
     * caption showing one train must place the arrow as a caption showing two does.
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
     * The text color to use on a pill, keeping what the caller meant by the one it asked for.
     *
     * The running diagram says two things with the text color: red for a station the train has not reached yet, and
     * plain for one it has.  On a blue pill plain black is invisible and red is nearly so, and simply forcing white
     * would throw away the distinction rather than redraw it, so red stays red and is lightened or darkened until it can
     * be read.
     *
     * GRAY is the third thing: the editors gray a square that is a PLACEHOLDER rather than an answer (an unnamed
     * station's em-dash, a yard name that is not what this editor is for).  Gray is not red, so it would otherwise fall
     * through to `readableOn` and come back as the same white or black a NAME does.
     *
     * Redrawn rather than forced: a neutral gray comes back as the readable color carried part of the way back towards
     * the pill, which is dimmer than a name on any fill and still legible on both, the same move the red branch makes.
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
            // Still dimmer than a name, on whichever ground.  Not so far towards the pill that it
            // stops being readable: a bit under halfway keeps a usable separation on both the navy and
            // the pale gray resting fills, which are the only two this is ever asked about.
            return blend(readable, fill, 0.45);
        }

        return readable;
    }

    /**
     * Whether a color is one of the grays the editors use to mean "this is a placeholder".
     *
     * By the shape of the color rather than by an equality test against the one constant, because
     * that constant is written out longhand at four places in LayoutGrid and a fifth would not be
     * surprising. Black and white are excluded: they are the ANSWER colors, and a rule that dimmed
     * them would dim every ordinary caption on the diagram.
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

    /**
     * A color moved part of the way towards another.
     *
     * @param from the color to start at
     * @param towards the color to move towards
     * @param howFar 0 for `from` unchanged, 1 for `towards`
     * @return the blended color
     */
    private static Color blend(Color from, Color towards, double howFar)
    {
        return new Color(
            (int) Math.round(from.getRed() + (towards.getRed() - from.getRed()) * howFar),
            (int) Math.round(from.getGreen() + (towards.getGreen() - from.getGreen()) * howFar),
            (int) Math.round(from.getBlue() + (towards.getBlue() - from.getBlue()) * howFar));
    }

    /**
     * Where the pill is actually drawn, in this component's own coordinates.
     *
     * The single answer that painting, hit-testing and sizing all ask, because they must agree.
     *
     * Flat, the pill is as wide as its text and one line tall. Stood on end those swap - the length
     * runs DOWN the screen, because the text has been turned a quarter - and the insets that place it
     * mean the same thing either way: how far past the leading edge of its square it sits.
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
     * Where this caption is actually drawn inside its own component, for a drag snapshot (FR-035).
     *
     * A caption's cell runs to the bottom of the diagram and its pill is placed within that by insets,
     * so the component is mostly empty space. Anything wanting a picture of the caption wants this
     * rectangle and not the component's own bounds.
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
            // Asked of the pill rather than of the text, because the two are no longer the same shape.
            // super.getPreferredSize measures a JLabel the way JLabel draws one - along the text - and
            // a rotated caption that asked for that room would be given a box one line tall and the
            // length of its name wide, then draw itself straight out of the bottom of it.
            java.awt.Insets pad = getInsets();

            return new java.awt.Dimension(
                thickness() + (pad == null ? 0 : pad.left + pad.right),
                width() + (pad == null ? 0 : pad.top + pad.bottom));
        }

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
     * The font the text is DRAWN in: the caption's own, flat or stood on end, at full size (FR-118).  A pill on end is
     * only as thick as its letters (`thickness`, OB-327), which leaves it room beside the station badges either side:
     * they are no taller across their track than the sensor's contact, 13 pixels at 30 and 26 at 60.
     * `testAPillOnEndClearsTheStationsBesideIt`.
     *
     * @return the font
     */
    private java.awt.Font drawnFont()
    {
        return getFont();
    }

    /**
     * How thick the pill is across its text: the line's height flat, and on end as thick as the letters need (OB-327) -
     * from the top of a tall letter to the foot of one that hangs below the line, and a little room either side.  The
     * font's line runs higher than any letter, which on end was width spent against the stations either side.
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
     * Only the pill takes the mouse - the room around it belongs to the diagram underneath.
     *
     * A caption is placed with INSETS, which are invisible to the eye and completely opaque to Swing: it hit-tests the
     * whole component, and this one is z-ordered to the front, over the tiles, carrying click and hover listeners.  So
     * without this a caption's centring room took every click on the tile to its LEFT (at rest a station shows a single
     * dash, so the left inset is almost the whole tile), and, because the caption's hover handler reports the STATION
     * rather than the square under the pointer, a Ctrl+X cut the train off a platform instead of the tile pointed at.
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
     * Draws a caption that has been stood on end.
     *
     * Painted here rather than by JLabel, because JLabel lays text out along its own width and there
     * is no way to tell it the text now runs the other way. Everything a caption needs is already
     * worked out in this class - the pill, its padding, the color that can be read on it - so what is
     * left is a transform and one drawString.
     *
     * The transform is a quarter turn ANTICLOCKWISE, which on a screen whose y grows downwards is
     * `rotate(-PI/2)`: it sends local +x to screen up, so the text reads from the bottom of the pill
     * towards the top. Translating to the BOTTOM of the pill first is what puts the start of the text
     * there rather than off the top of the diagram.
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

            // And NOT super.paintComponent - JLabel would draw the text a second time, flat, across
            // the top of the rotated pill.
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

                // INSIDE the insets, which is what lets the caption be moved without being stretched.
                //
                // Where a caption sits on its square is set with a top border - the label's cell runs
                // to the bottom of the diagram, so there is nothing to anchor against - and a pill
                // painted over the whole component would grow downwards from the top of the square
                // instead of moving down it.
                java.awt.Rectangle drawn = pillBounds();

                if (drawn == null) return;

                // An arc as tall as the pill gives semicircular ends, which is the oval the request
                // asks for at any width - a fixed arc turns into a rectangle with rounded corners as
                // soon as the name is long.
                fillPill(g2, drawn.x, drawn.y, drawn.width, drawn.height);

                if (isDestination(getBackground())) ring(g2, drawn.x, drawn.y, drawn.width, drawn.height);
                else if (namesSomething()) edge(g2, drawn.x, drawn.y, drawn.width, drawn.height);
            }
            finally
            {
                g2.dispose();
            }
        }

        // THE PILL'S TEXT IS DRAWN HERE, IN THE MIDDLE OF IT: the label's own painter places it by the width the text
        // measured for layout, and painted at a larger scale (a display at 125 or 150 per cent) the glyphs come out wider
        // than that, all of the difference landing at the right end.  Centered by the width it is drawn at, the two ends
        // get the same room whatever the scale.
        if (pill)
        {
            java.awt.Rectangle drawn = pillBounds();

            if (drawn != null) paintText(g, drawn.x, drawn.y, drawn.width, drawn.height);

            return;
        }

        // THE DIAGRAM'S OWN WRITING WITH A HALO: white round the letters first, so they can be read against black
        // backgrounds like tracks, then the label draws them over it as it always has.
        if (halo) paintHalo(g);

        super.paintComponent(g);
    }

    /**
     * Draws the caption's text in the middle of a pill `len` long and `thick` deep, measured as it is drawn.
     *
     * @param g the graphics, already turned for a rotated caption
     * @param x the pill's left
     * @param y the pill's top
     * @param len its length along the text
     * @param thick its depth across the text
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

            // MEASURED AS DRAWN, in this graphics, which carries its scale.
            //
            // ACROSS BY THE INK, not the advance: the advance carries the first letter's and the last one's side room,
            // which is not the same at the two ends.  UP AND DOWN BY THE CAPITALS: the font's ascent includes room above
            // the capitals that no letter fills, so text centered by ascent and descent sat low (at 15 points and twice the
            // size, 13 pixels of pill above "Carlton" and 8 below).  A letter that hangs below the line still does, from
            // the same line as every other caption's.
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
     * alone.  Only these get the edge (D2), so an empty station gets no louder.
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
     * A faint edge round a pill, a shade darker than its fill (D2 of the look's design pass), so a caption parts from a
     * signal or a switch drawn under it.
     */
    private void edge(Graphics2D g, int x, int y, int w, int h)
    {
        Color f = getBackground();

        g.setColor(new Color(Math.max(0, f.getRed() - 70), Math.max(0, f.getGreen() - 70), Math.max(0, f.getBlue() - 70),
            Math.min(255, f.getAlpha() + 30)));
        outline(g, x, y, w, h, 1f);
    }
}
