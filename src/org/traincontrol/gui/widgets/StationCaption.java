package org.traincontrol.gui.widgets;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JLabel;
import javax.swing.BorderFactory;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import org.traincontrol.automationui.DiagramColours;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.ImageUtil;

/**
 * A station caption, drawn as a rounded pill instead of a rectangle of text (FR-028), the same badge as on the auto loc
 * panels in the autonomy tab.
 *
 * A JLabel subclass because callers register, color, re-text and click it as a JLabel.  The fill comes from
 * `getBackground()`, so every color the diagram already uses says what it said.
 *
 * @author Adam
 */
public class StationCaption extends JLabel
{
    /** The blue of the station badge on an autonomy panel and the diagram's station badges and route: one value. */
    public static final Color PILL = DiagramColours.STATION;

    /** The resting fill, with the diagram's own translucency. */
    public static final Color PILL_AT_REST =
        new Color(PILL.getRed(), PILL.getGreen(), PILL.getBlue(), LayoutGrid.LAYOUT_STATION_OPACITY);

    /** The other resting color: light gray, the default (FR-031); blue is the optional setting. */
    public static final Color PILL_GREY =
        new Color(196, 198, 202, LayoutGrid.LAYOUT_STATION_OPACITY);

    /** A running train's destination: `DiagramColours.DESTINATION`, solid, drawn with a ring. */
    public static final Color DESTINATION_FILL = new Color(DiagramColours.DESTINATION.getRed(),
        DiagramColours.DESTINATION.getGreen(),
        DiagramColours.DESTINATION.getBlue());

    /** Whether this fill is the destination's, by color and not by alpha (a ring belongs round it). */
    public static boolean isDestination(Color fill)
    {
        return fill != null && (fill.getRGB() & 0xFFFFFF) == (DESTINATION_FILL.getRGB() & 0xFFFFFF);
    }

    /** The ring round a destination, in a dark orange (`DiagramColours.DESTINATION_RING`). */
    private static void ring(Graphics2D g, int x, int y, int w, int h)
    {
        g.setColor(DiagramColours.DESTINATION_RING);
        outline(g, x, y, w, h, Math.max(1.4f, h / 12f));
    }

    /**
     * An outline inside a pill by half its width; `drawRoundRect(x, y, w - 1, h - 1)` would put half outside the fill.
     */
    private static void outline(Graphics2D g, int x, int y, int w, int h, float width)
    {
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setStroke(new BasicStroke(width));

        double in = width / 2.0, arc = h - width;

        g.draw(new RoundRectangle2D.Double(x + in, y + in, w - width, h - width, arc, arc));
    }

    /** A pill's fill, as the exact shape - see `outline`. */
    private static void fillPill(Graphics2D g, int x, int y, int w, int h)
    {
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
    }

    /**
     * Whichever resting color the operator has chosen (FR-031), asked when a caption is colored, not read once.
     *
     * @return the fill for a caption with nothing else to say
     */
    public static Color restingFill()
    {
        return TrainControlUI.stationLabelsAreGrey() ? PILL_GREY : PILL_AT_REST;
    }

    /** Room at the ends, so the text does not run into the curve: a fraction of the height, kept small. */
    private static final double SIDE_PADDING = 0.27;

    /** How much smaller a caption is than the diagram's other text: a pill is taller than its text. */
    public static final float FONT_SCALE = 0.81f;

    /** The room above and below the letters in a pill on end, as a share of its text's size. */
    private static final double ON_END_PAD = 0.12;

    /** Whether this label is a caption at all: the user's own writing on the diagram must not become blue pills. */
    private boolean pill = false;

    /** The size of the square this caption belongs to, in pixels, or 0 before it is told. */
    private int tile = 0;

    /** How far LEFT of its square the cell may start (centring a wide caption, which insets cannot express). */
    private int backShift = 0;

    /** How far past its square's leading edge this caption sits - see captionOffset. */
    private int offset = 0;

    /** Whether this caption is stood on end, reading upwards, so it does not cover north-south track. */
    private boolean rotated = false;

    /** How far ABOVE its own square the caption's cell was allowed to start: the vertical twin of `backShift`. */
    private int upShift = 0;

    /**
     * Turns this label into a pill, or back into text.  The fill is painted here and the label left transparent, so
     * callers go on setting a background color.
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

    /** @return whether this label draws itself as a pill */
    public boolean isPill()
    {
        return pill;
    }

    /**
     * Whether the letters get a white halo, as an address's do, so writing over black track can be read.
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

    /** The halo round the letters: a round-joined white stroke on each outline, inside the bounds (OB-115). */
    private void paintHalo(Graphics g)
    {
        String text = getText();

        if (text == null || text.isEmpty() || getFont() == null) return;

        // Writing set as HTML too (MT-716): `<html><br>` and the words, which the plain-text path below would skip.
        if (BasicHTML.isHTMLString(text))
        {
            paintHtmlHalo(g, text);

            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            ImageUtil.smooth(g2);

            FontMetrics fm = g2.getFontMetrics(getFont());

            Rectangle place = new Rectangle();

            String shown = layOut(fm, text, place);

            if (shown == null || shown.isEmpty()) return;

            drawHalo(g2, new TextLayout(shown, getFont(), g2.getFontRenderContext())
                .getOutline(AffineTransform.getTranslateInstance(place.x, place.y + fm.getAscent())));
        }
        finally
        {
            g2.dispose();
        }
    }

    /** Where JLabel's own painter puts the text, into `place`, and what of the text fits. */
    private String layOut(FontMetrics fm, String text, Rectangle place)
    {
        Insets in = getInsets();

        Rectangle view = new Rectangle(in.left, in.top, getWidth() - in.left - in.right,
            getHeight() - in.top - in.bottom);

        return javax.swing.SwingUtilities.layoutCompoundLabel(this, fm, text, getIcon(), getVerticalAlignment(),
            getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), view,
            new Rectangle(), place, getIconTextGap());
    }

    /** The halo itself: the letters' outline, stroked wide in the halo's color. */
    private void drawHalo(Graphics2D g2, Shape letters)
    {
        g2.setColor(AddressLabel.HALO);
        g2.setStroke(new BasicStroke(haloWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(letters);
    }

    /** The halo round writing set as HTML: each line outlined where the HTML view draws it. */
    private void paintHtmlHalo(Graphics g, String text)
    {
        Object property = getClientProperty(BasicHTML.propertyKey);

        if (!(property instanceof View)) return;

        View view = (View) property;

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            ImageUtil.smooth(g2);

            FontMetrics fm = g2.getFontMetrics(getFont());

            Rectangle place = new Rectangle();

            // where JLabel's own painter puts the view
            layOut(fm, text, place);

            javax.swing.text.Document document = view.getDocument();

            String words = document.getText(0, document.getLength());

            Path2D letters = new Path2D.Double();

            int start = 0;

            while (start < words.length())
            {
                int end = words.indexOf('\n', start);

                if (end < 0) end = words.length();

                // From its first letter: the space HTML leaves after a <br> ends the line above, not this one.
                int first = start;

                while (first < end && Character.isWhitespace(words.charAt(first))) first++;

                String line = words.substring(first, end).replace('\u00a0', ' ');

                if (!line.trim().isEmpty())
                {
                    Shape where = view.modelToView(first, place, javax.swing.text.Position.Bias.Forward);

                    if (where != null)
                    {
                        Rectangle at = where.getBounds();

                        letters.append(new TextLayout(line, getFont(), g2.getFontRenderContext())
                            .getOutline(AffineTransform.getTranslateInstance(at.x, at.y + fm.getAscent())),
                            false);
                    }
                }

                start = end + 1;
            }

            drawHalo(g2, letters);
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

    /** Stands this caption on end, or lays it flat again. */
    public void setRotated(boolean on)
    {
        this.rotated = on;

        place();
    }

    /** @return whether this caption reads upwards rather than across */
    public boolean isRotated()
    {
        return rotated;
    }

    /**
     * The text as DRAWN, arrows turned to match the caption (north is drawn with the east glyph); `getText` is
     * unchanged.  One pass, because sequential replacements would send north to east and on to south.
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
     * Tells this caption about its square, and places it there.
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

    /** Puts the caption where it belongs on its square, from its current text; called on every change of text. */
    private void place()
    {
        if (!pill || tile <= 0) return;

        int wide = width();

        // Centered on the square (OB-118): left = backShift + (tile - wide) / 2, clamped because a caption cannot start
        // before its own cell.
        if (rotated)
        {
            // Stood on end: centered along the rail, set off to the side as a flat caption is set below it.
            int top = Math.max(0, upShift + (tile - wide) / 2);

            // Across: centered on the line between its square and the next (OB-327).
            setBorder(BorderFactory.createEmptyBorder(top, Math.max(0, backShift + tile - thickness() / 2), 0, 0));

            return;
        }

        int left = Math.max(0, backShift + (tile - wide) / 2);

        setBorder(BorderFactory.createEmptyBorder(offset, left, 0, 0));
    }

    /** How wide the pill needs to be for what this caption currently says. */
    private int width()
    {
        Font font = getFont();

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

        // Every caller goes through here, so this is the one place that puts the caption back.
        place();
    }

    /**
     * How far down its square a caption sits: a proportion of tile size and line height.  Public and taking numbers so
     * it can be checked without building a grid.
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

    /** How far below the geometry's place a caption sits, so it does not read as sitting on the track it names. */
    private static int nudge(int tile)
    {
        return Math.max(1, tile * 3 / 20);
    }

    /**
     * Black or white, whichever can be read on this fill.
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
     * The diagram's label font and its four arrows (OB-116), chosen at class load; the pointers stay without `Segoe UI
     * Symbol`.
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
        // Installed, and then able: `new Font("no such family", ...)` resolves to Dialog, so only the family list says
        // whether it is installed.
        Font symbol = new Font(SYMBOL_FONT, Font.PLAIN, 12);

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
     * Whether this arrow goes BEFORE the name: south and west lead, so a direction pointing back along the text points
     * at its own label (and on a caption on end the string's start is the pill's bottom).
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
     * A name and its arrow, joined with the arrow on the side it points towards: the one place that join happens.
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
     * The text color to use on a pill, keeping what the caller meant.  Red (not yet reached) is lightened or darkened
     * until readable; a neutral gray placeholder is the readable color carried part way back towards the fill.
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

        // THE ROUTE'S BLUE: a station a running train has not reached yet.
        if (wanted != null && wanted.getBlue() > 150 && wanted.getRed() < 80 && wanted.getGreen() < 80)
        {
            return dark ? new Color(165, 185, 255) : wanted;
        }

        if (isNeutralGrey(wanted))
        {
            // Still dimmer than a name, but readable on the navy and pale gray fills.
            return blend(readable, fill, 0.45);
        }

        return readable;
    }

    /**
     * Whether a color is a neutral gray, which the editors use for a placeholder (black and white excluded).
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
     * Where the pill is drawn, in this component's coordinates: the one answer for painting, hit-testing and sizing.
     */
    private Rectangle pillBounds()
    {
        if (!pill) return null;

        Insets pad = getInsets();

        int top = pad == null ? 0 : pad.top;
        int left = pad == null ? 0 : pad.left;

        if (rotated)
        {
            int len = width();
            int thick = thickness();

            if (len <= 0 || thick <= 0) return null;

            return new Rectangle(left, top, thick, len);
        }

        int high = getHeight() - top - (pad == null ? 0 : pad.bottom);
        int wide = getWidth() - left - (pad == null ? 0 : pad.right);

        if (high <= 0 || wide <= 0) return null;

        return new Rectangle(left, top, wide, high);
    }

    /**
     * Where this caption is drawn inside its own component, for a drag snapshot (FR-035).
     *
     * @return the drawn rectangle, or null when nothing is drawn
     */
    public Rectangle drawnBounds()
    {
        return pillBounds();
    }

    @Override
    public Dimension getPreferredSize()
    {
        Dimension was = super.getPreferredSize();
        if (!pill)
        {
            int most = widest(getText());
            if (most > 0) was.width += Math.max(0, most - getFontMetrics(getFont()).stringWidth(getText()));

            return was;
        }

        if (rotated)
        {
            // Asked of the pill: super.getPreferredSize measures along the text, wrong for a rotated caption.
            Insets pad = getInsets();

            return new Dimension(
                thickness() + (pad == null ? 0 : pad.left + pad.right),
                width() + (pad == null ? 0 : pad.top + pad.bottom));
        }

        // Room at the ends measured from the LINE height, not the component height, which includes the top border.
        was.width += (int) Math.round(lineHeight() * SIDE_PADDING * 2);

        return was;
    }

    /** The widest the text lays out at a common scale in any text smoothing (the scaled Graphics' wider advances). */
    private int widest(String text)
    {
        Font font = getFont();
        int most = 0;
        if (font == null || text == null || text.isEmpty() || BasicHTML.isHTMLString(text)) return 0;
        Object[] smoothing = {RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP};
        for (double scale : new double[] {1, 1.25, 1.5, 1.75, 2, 2.25})
        {
            for (Object how : smoothing)
            {
                Graphics2D g = new java.awt.image.BufferedImage(1, 1, 1).createGraphics();
                g.scale(scale, scale);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, how);
                most = Math.max(most, g.getFontMetrics(font).stringWidth(text));
                g.dispose();
            }
        }
        return most;
    }

    /**
     * How tall one line of the caption's text is: the pill's own height, not the component's.
     *
     * @return the line height, or a sane guess before the font is known
     */
    public int lineHeight()
    {
        Font font = getFont();

        if (font == null) return 12;

        return getFontMetrics(font).getHeight();
    }

    /**
     * The font the text is DRAWN in: the caption's own at full size (FR-118).  A pill on end is only as thick as its
     * letters (`thickness`, OB-327).
     *
     * @return the font
     */
    private Font drawnFont()
    {
        return getFont();
    }

    /** How thick the pill is across its text: the line's height flat, as thick as the letters on end (OB-327). */
    private int thickness()
    {
        if (!rotated) return lineHeight();

        Font font = drawnFont();

        if (font == null) return lineHeight();

        FontRenderContext frc = getFontMetrics(font).getFontRenderContext();

        double letters = font.createGlyphVector(frc, "Hgjy").getVisualBounds().getHeight();

        return (int) Math.ceil(letters + 2 * font.getSize2D() * ON_END_PAD);
    }

    /**
     * Only the pill takes the mouse; the insets' centring room belongs to the diagram underneath.
     *
     * @param x in this component's coordinates
     * @param y in this component's coordinates
     * @return whether the pointer is on the part of this caption that is actually drawn
     */
    @Override
    public boolean contains(int x, int y)
    {
        if (!pill) return super.contains(x, y);

        Rectangle drawn = pillBounds();

        return drawn != null && drawn.contains(x, y);
    }

    /** Draws a caption stood on end: a quarter turn anticlockwise from the pill's bottom, where the text starts. */
    private void paintOnEnd(Graphics g, Rectangle drawn)
    {
        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            ImageUtil.antialias(g2);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.translate(drawn.x, drawn.y + drawn.height);
            g2.rotate(-Math.PI / 2);

            // From here on this is the flat case exactly: a pill `drawn.height` long and `drawn.width` thick.
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
            Rectangle drawn = pillBounds();

            // And NOT super.paintComponent: JLabel would draw the text a second time, flat.
            if (drawn != null) paintOnEnd(g, drawn);

            return;
        }

        if (pill && getBackground() != null && !getText().isEmpty())
        {
            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                ImageUtil.antialias(g2);

                g2.setColor(getBackground());

                // Inside the insets, so the caption can move without being stretched: a pill painted over the whole
                // component would grow downwards from the top.
                Rectangle drawn = pillBounds();

                if (drawn == null) return;

                // An arc as tall as the pill gives semicircular ends at any width.
                fillPill(g2, drawn.x, drawn.y, drawn.width, drawn.height);

                if (isDestination(getBackground())) ring(g2, drawn.x, drawn.y, drawn.width, drawn.height);
                else if (namesSomething()) edge(g2, drawn.x, drawn.y, drawn.width, drawn.height);
            }
            finally
            {
                g2.dispose();
            }
        }

        // The pill's text is drawn here, in the middle: the label's own painter places it by the layout width.
        if (pill)
        {
            Rectangle drawn = pillBounds();

            if (drawn != null) paintText(g, drawn.x, drawn.y, drawn.width, drawn.height);

            return;
        }

        // The diagram's own writing with a halo: white round the letters first, then the label over it.
        if (halo) paintHalo(g);

        super.paintComponent(g);
    }

    /** Draws the text in the middle of a pill `len` long and `thick` deep, in a graphics already turned if rotated. */
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

            // Measured as drawn, in this graphics: across by the ink, up and down by the capitals.
            FontRenderContext frc = g2.getFontRenderContext();
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

    /** Whether this caption names something (a train, a station) rather than a placeholder dash or an arrow alone. */
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

    /** A faint edge round a pill, a shade darker than its fill, so it parts from a signal or switch drawn under it. */
    private void edge(Graphics2D g, int x, int y, int w, int h)
    {
        Color f = getBackground();

        g.setColor(new Color(Math.max(0, f.getRed() - 70), Math.max(0, f.getGreen() - 70), Math.max(0, f.getBlue() - 70),
            Math.min(255, f.getAlpha() + 30)));
        outline(g, x, y, w, h, 1f);
    }
}
