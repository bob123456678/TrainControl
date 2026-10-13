package org.traincontrol.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.traincontrol.util.I18n;

/**
 * The line over the active locomotive's name: its key, its page and its decoder, drawn as three small shapes
 * instead of a sentence (FR-128).
 *
 * Adam: *"the active locomotive info is currently text consisting of [letter] . Page [num] ([page name]) .
 * [decodertype] [address]. the page number could be condensed into a nice pill, along with the other info.  make it
 * look more modern."*  He chose, from mockups, the one he called D:
 *
 *   1. **a keycap** - the key's letter on a small rounded square with a heavier bottom edge, so it reads as a key;
 *   2. **a page pill** - a capsule with a thin neutral outline and no fill, holding the page's NUMBER in bold dark
 *      grey and then its name.  No word "Page" in it (*"I wouldn't like to see the word 'page' in it"*);
 *   3. **a decoder pill** - a solid navy capsule with white text, saying what the line said before: the decoder and
 *      the address, "MFX 54".
 *
 * **It is drawn, not laid out.**  The window's panel is a generated GroupLayout with this row's slot fixed at 296 x 18
 * (`CurrentKeyLabel`), and nothing below it may move.  A component that paints three shapes into the box it is given
 * can promise that; a row of three labels and a layout manager could only hope.  It also lets the one thing that has to
 * give way - the page NAME - be measured and cut to the pixel, which a label cannot do for a piece of a sentence.
 *
 * **What gives way, and what never does** (Adam: *"make sure we test with wide page names (like MMMMMMMMMMM)"*).  The
 * keycap, the page number and the decoder pill are drawn whole.  The page name takes whatever room is left and is cut
 * with an ellipsis when it does not fit; a name with no room for even one letter and the ellipsis is left out and the
 * number stands alone, as it does for a page that has no name.  The full words are in the tooltip.
 *
 * **Why not `StationCaption`.**  The diagram's captions are labels whose fill is a state colour, drawn at a size taken
 * from the tile; this is three fixed shapes in a fixed box.  What is borrowed is its house style for painting a pill:
 * the exact shape rather than `drawRoundRect(w - 1, h - 1)`, STROKE_PURE so the four sides of an outline are the same,
 * and text centred on the capitals rather than on the ascent.  Its `LABEL_FONT` is not used because that name is
 * "Segoe UI Symbol" on machines that have it, chosen for the diagram's arrows; it has no bold, and none of this row's
 * words are arrows.  "Segoe UI" is what `LABEL_FONT` falls back to and what the line it replaces was set in.
 *
 * **Colours come from the look and feel where Adam did not choose one**: the outline is the component border colour
 * and the words are the label foreground, so a theme that turns them light turns this row's too.  He chose the navy and
 * the white of the decoder pill (the navy is the exact colour the old line's text was), and the page number's dark
 * grey; the grey is replaced by the foreground itself on a dark theme, where it would be invisible.  This program ships
 * only the light theme, so that last branch is a safeguard and not a feature.
 *
 * @author Adam
 */
public class CurrentKeyRow extends JComponent
{
    /** The decoder pill's fill: the exact navy of the old line's text, `CurrentKeyLabel`'s foreground (Adam's choice). */
    public static final Color DECODER_FILL = new Color(0, 0, 115);

    /** The words on the decoder pill. */
    public static final Color DECODER_TEXT = Color.WHITE;

    /** The page number, bold, in the page pill (Adam's choice: about rgb 68,68,65). */
    public static final Color PAGE_NUMBER = new Color(68, 68, 65);

    /** How tall the three shapes are, in pixels, and the tallest this row ever is: the slot's own height. */
    public static final int PILL_HEIGHT = 18;

    /** The room between one shape and the next. */
    public static final int GAP = 5;

    /** The corner radius of the keycap. */
    private static final double KEY_RADIUS = 4;

    /** Room either side of the letter on the keycap, not counting its edge. */
    private static final int KEY_PAD = 5;

    /** Room between a pill's curved end and the words in it. */
    private static final int PAD = 8;

    /** The room between the page number and the page name. */
    private static final int INNER_GAP = 4;

    /** The text size, in points: 11 in an 18 pixel pill. */
    private static final float SIZE = 11f;

    /** The ellipsis a cut page name ends in. */
    public static final String ELLIPSIS = String.valueOf((char) 0x2026);

    /** The separator in the tooltip, the one the old line used between its parts. */
    private static final String BULLET = " " + (char) 0x2022 + " ";

    /**
     * Measured as they are drawn: anti-aliased, with fractional advances.  The drawing below sets the same two hints, so
     * a width taken here is the width on the screen at any scale, and nothing drawn runs past what was measured.
     */
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /** The keycap's letter: medium weight - Segoe UI Semibold, and bold of the plain family where that is not installed. */
    private static final Font KEY_FONT = keyFont();

    /** The page number: bold. */
    private static final Font NUMBER_FONT = new Font("Segoe UI", Font.BOLD, 11).deriveFont(SIZE);

    /** Every other word: the page name and the decoder. */
    private static final Font TEXT_FONT = new Font("Segoe UI", Font.PLAIN, 11).deriveFont(SIZE);

    private static Font keyFont()
    {
        Font semibold = new Font("Segoe UI Semibold", Font.PLAIN, 11).deriveFont(SIZE);

        // A name that is not installed does not fail, it resolves to Dialog - so ask what came back
        if ("Segoe UI Semibold".equalsIgnoreCase(semibold.getFamily())) return semibold;

        return new Font("Segoe UI", Font.BOLD, 11).deriveFont(SIZE);
    }

    /**
     * What the row says.  A value, so the window can ask whether what it is about to show is what it shows already.
     *
     * The window used to compare the line's HTML text for that, to decide whether the locomotive's picture needed
     * repainting.  This is the same question about the same four things.
     */
    public static final class Content
    {
        private final String key;
        private final int page;
        private final String name;
        private final String decoder;

        /**
         * @param key the key's letter, as on its button
         * @param page the page the key was chosen on
         * @param pageName that page's name as the operator typed it, or empty for a page with none
         * @param decoder the decoder and address as one text ("MFX 54"), or empty when no locomotive is on the key
         */
        public Content(String key, int page, String pageName, String decoder)
        {
            this.key = key == null ? "" : key.trim();
            this.page = page;
            this.name = pageName == null ? "" : pageName.trim();
            this.decoder = decoder == null ? "" : decoder.trim();
        }

        /** @return the key's letter */
        public String getKey()
        {
            return key;
        }

        /** @return the page number */
        public int getPage()
        {
            return page;
        }

        /** @return the page's name, or empty */
        public String getPageName()
        {
            return name;
        }

        /** @return the decoder and address, or empty */
        public String getDecoder()
        {
            return decoder;
        }

        /** @return whether a locomotive is on the key */
        public boolean hasDecoder()
        {
            return !decoder.isEmpty();
        }

        /**
         * The whole line in words, for the tooltip and for a screen reader: the key, the page as the program words it
         * everywhere else ("Page 5 (Example)", or "Page 5" with no name) and the decoder.  The page's words are the
         * bundle's own, so there is nothing new to translate.  It begins with the key, never with a tag, so Swing
         * takes it as plain text however the page was named.
         *
         * @return the line, with nothing cut
         */
        public String getTooltip()
        {
            StringBuilder text = new StringBuilder(key);

            if (text.length() > 0) text.append(BULLET);

            text.append(name.isEmpty() ? I18n.f("page.ui.pageNo", page) : I18n.f("page.ui.plainPageName", page, name));

            if (hasDecoder()) text.append(BULLET).append(decoder);

            return text.toString();
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;

            if (!(other instanceof Content)) return false;

            Content c = (Content) other;

            return page == c.page && key.equals(c.key) && name.equals(c.name) && decoder.equals(c.decoder);
        }

        @Override
        public int hashCode()
        {
            return java.util.Objects.hash(key, page, name, decoder);
        }

        @Override
        public String toString()
        {
            return getTooltip();
        }
    }

    /**
     * Where each piece goes in a box of a given size, and what is drawn in it.  The painting and the tests both read
     * this, so what a test measures is what is drawn and not a second account of it.
     */
    public static final class Geometry
    {
        /** The keycap, or null with no key. */
        public final Rectangle keycap;

        /** The page pill. */
        public final Rectangle page;

        /** The decoder pill, or null when no locomotive is on the key. */
        public final Rectangle decoder;

        /** The key's letter as drawn. */
        public final String key;

        /** The page number as drawn. */
        public final String number;

        /** The page name as drawn: whole, cut with an ellipsis, or empty when it has been left out. */
        public final String name;

        /** The decoder text as drawn, or empty. */
        public final String decoderText;

        final double keyX, numberX, nameX, decoderX;

        Geometry(Rectangle keycap, Rectangle page, Rectangle decoder, String key, String number, String name,
                 String decoderText, double keyX, double numberX, double nameX, double decoderX)
        {
            this.keycap = keycap;
            this.page = page;
            this.decoder = decoder;
            this.key = key;
            this.number = number;
            this.name = name;
            this.decoderText = decoderText;
            this.keyX = keyX;
            this.numberX = numberX;
            this.nameX = nameX;
            this.decoderX = decoderX;
        }

        /** @return the right-hand edge of the last shape, in pixels from the row's left */
        public int right()
        {
            int right = page.x + page.width;

            if (decoder != null) right = Math.max(right, decoder.x + decoder.width);

            return right;
        }

        /** @return the tallest bottom edge of any shape */
        public int bottom()
        {
            int bottom = page.y + page.height;

            if (keycap != null) bottom = Math.max(bottom, keycap.y + keycap.height);

            if (decoder != null) bottom = Math.max(bottom, decoder.y + decoder.height);

            return bottom;
        }
    }

    private Content content;

    public CurrentKeyRow()
    {
        setOpaque(false);

        // The panel drives locomotives from bare key presses, and a key must not stop at a row that only shows
        setFocusable(false);
        setRequestFocusEnabled(false);
    }

    /**
     * A plain JComponent has no accessible context at all, which is a null for anything that asks a screen reader's
     * questions of it - and for `setContent`, which names it.  A label's role, with the line in words for its name.
     */
    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext()
    {
        if (accessibleContext == null)
        {
            accessibleContext = new AccessibleJComponent()
            {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole()
                {
                    return javax.accessibility.AccessibleRole.LABEL;
                }
            };
        }

        return accessibleContext;
    }

    /** @return what the row shows, or null before it has been told */
    public Content getContent()
    {
        return content;
    }

    /**
     * Shows this.  The tooltip and the accessible name are the line in words.
     *
     * @param content what to show; null empties the row
     */
    public void setContent(Content content)
    {
        if (java.util.Objects.equals(this.content, content)) return;

        this.content = content;

        String words = content == null ? null : content.getTooltip();

        setToolTipText(words);

        getAccessibleContext().setAccessibleName(words);

        repaint();
    }

    /**
     * Where everything goes in a box this size, or null when there is nothing to show.
     *
     * @param width the box's width in pixels
     * @param height the box's height in pixels
     * @return the layout
     */
    public Geometry geometry(int width, int height)
    {
        Content c = content;

        if (c == null || height <= 0) return null;

        int h = Math.min(PILL_HEIGHT, height);
        int top = (height - h) / 2;
        int x = 0;

        Rectangle keycap = null;
        double keyAdvance = 0;

        if (!c.getKey().isEmpty())
        {
            keyAdvance = advance(KEY_FONT, c.getKey());

            keycap = new Rectangle(0, top, Math.max(h, (int) Math.ceil(keyAdvance) + 2 * KEY_PAD), h);

            x = keycap.width + GAP;
        }

        Rectangle decoder = null;
        double decoderAdvance = 0;
        int reserved = 0;

        if (c.hasDecoder())
        {
            decoderAdvance = advance(TEXT_FONT, c.getDecoder());

            int w = Math.max(h, (int) Math.ceil(decoderAdvance) + 2 * PAD);

            decoder = new Rectangle(0, top, w, h);

            reserved = w + GAP;
        }

        String number = String.valueOf(c.getPage());
        double numberAdvance = advance(NUMBER_FONT, number);

        // What is left, once the shapes that never give way have their room, is the name's
        double nameRoom = width - x - reserved - 2 * PAD - numberAdvance - INNER_GAP;

        String name = fit(c.getPageName(), nameRoom);
        double nameAdvance = name.isEmpty() ? 0 : advance(TEXT_FONT, name);

        double inside = name.isEmpty() ? numberAdvance : numberAdvance + INNER_GAP + nameAdvance;

        Rectangle page = new Rectangle(x, top, Math.max(h, (int) Math.ceil(inside) + 2 * PAD), h);

        // The words are centred in their pill, which is the whole of the room a pill with a number alone has
        double start = page.x + (page.width - inside) / 2.0;

        if (decoder != null) decoder.x = page.x + page.width + GAP;

        return new Geometry(keycap, page, decoder, c.getKey(), number, name, c.getDecoder(),
            keycap == null ? 0 : (keycap.width - keyAdvance) / 2.0,
            start, start + numberAdvance + INNER_GAP,
            decoder == null ? 0 : decoder.x + (decoder.width - decoderAdvance) / 2.0);
    }

    /**
     * The page name that fits in this much room: whole, or cut with an ellipsis, or empty when not even one letter
     * and the ellipsis fit.
     */
    private static String fit(String name, double room)
    {
        if (name.isEmpty() || advance(TEXT_FONT, name) <= room) return name;

        double dots = advance(TEXT_FONT, ELLIPSIS);

        for (int n = name.length() - 1; n >= 1; n--)
        {
            // never cut between the two halves of one character
            if (Character.isHighSurrogate(name.charAt(n - 1))) continue;

            String head = name.substring(0, n).trim();

            if (!head.isEmpty() && advance(TEXT_FONT, head) + dots <= room) return head + ELLIPSIS;
        }

        return "";
    }

    private static double advance(Font font, String text)
    {
        return font.getStringBounds(text, FRC).getWidth();
    }

    /** How tall the capitals are: the height a pill's words are centred on, rather than the line they sit in. */
    private static double capHeight(Font font)
    {
        return font.createGlyphVector(FRC, "H").getVisualBounds().getHeight();
    }

    /**
     * The natural size: every piece whole, and the slot's height.  Only its height is a promise - the row is given its
     * width by the slot it sits in, and gives way inside it.
     */
    @Override
    public Dimension getPreferredSize()
    {
        if (isPreferredSizeSet()) return super.getPreferredSize();

        Geometry whole = geometry(10000, PILL_HEIGHT);

        return new Dimension(whole == null ? 0 : whole.right(), PILL_HEIGHT);
    }

    @Override
    public Dimension getMinimumSize()
    {
        return isMinimumSizeSet() ? super.getMinimumSize() : new Dimension(0, PILL_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Geometry at = geometry(getWidth(), getHeight());

        if (at == null) return;

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            // Whatever happens to the numbers, nothing is drawn past the slot
            g2.clipRect(0, 0, getWidth(), getHeight());

            Color text = foreground();
            Color edge = outline();

            if (at.keycap != null)
            {
                paintKeycap(g2, at, edge, text);
            }

            // THE PAGE PILL: an outline and nothing inside it.  Inset by half the stroke on every side, the exact shape.
            Rectangle p = at.page;

            g2.setColor(edge);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(p.x + 0.5, p.y + 0.5, p.width - 1, p.height - 1, p.height - 1, p.height - 1));

            g2.setFont(NUMBER_FONT);
            g2.setColor(numberColour(text));
            g2.drawString(at.number, (float) at.numberX, (float) baseline(p.y, p.height, NUMBER_FONT));

            if (!at.name.isEmpty())
            {
                g2.setFont(TEXT_FONT);
                g2.setColor(text);
                g2.drawString(at.name, (float) at.nameX, (float) baseline(p.y, p.height, TEXT_FONT));
            }

            // THE DECODER PILL: solid navy, white words.
            if (at.decoder != null)
            {
                Rectangle d = at.decoder;

                g2.setColor(DECODER_FILL);
                g2.fill(new RoundRectangle2D.Double(d.x, d.y, d.width, d.height, d.height, d.height));

                g2.setFont(TEXT_FONT);
                g2.setColor(DECODER_TEXT);
                g2.drawString(at.decoderText, (float) at.decoderX, (float) baseline(d.y, d.height, TEXT_FONT));
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * A key: a thin outline all round and a heavier one along the bottom, so it stands up off the panel.  The ring is
     * the outer rounded rectangle less an inner one that is one pixel in from the top and the sides and two from the
     * bottom; the face is the panel's own, nothing is filled.
     */
    private void paintKeycap(Graphics2D g2, Geometry at, Color edge, Color text)
    {
        Rectangle k = at.keycap;

        Area ring = new Area(new RoundRectangle2D.Double(k.x, k.y, k.width, k.height, 2 * KEY_RADIUS, 2 * KEY_RADIUS));

        ring.subtract(new Area(new RoundRectangle2D.Double(k.x + 1, k.y + 1, k.width - 2, k.height - 3,
            2 * (KEY_RADIUS - 1), 2 * (KEY_RADIUS - 1))));

        g2.setColor(edge);
        g2.fill(ring);

        // The letter sits in the face, which is one pixel shorter below than above
        g2.setFont(KEY_FONT);
        g2.setColor(text);
        g2.drawString(at.key, (float) (k.x + at.keyX), (float) baseline(k.y, k.height - 1, KEY_FONT));
    }

    /** The baseline that puts the capitals of this font in the middle of a box. */
    private static double baseline(int top, int height, Font font)
    {
        return top + height / 2.0 + capHeight(font) / 2.0;
    }

    /** The words' colour: the look and feel's own for a label. */
    private static Color foreground()
    {
        Color c = UIManager.getColor("Label.foreground");

        return c == null ? Color.BLACK : c;
    }

    /** The outline's colour: the look and feel's own for the edge of a component. */
    private static Color outline()
    {
        Color c = UIManager.getColor("Component.borderColor");

        return c == null ? new Color(170, 170, 170) : c;
    }

    /** Adam's dark grey, or the foreground itself where the foreground is light and the grey would not show. */
    private static Color numberColour(Color foreground)
    {
        double brightness = (0.299 * foreground.getRed() + 0.587 * foreground.getGreen() + 0.114 * foreground.getBlue()) / 255.0;

        return brightness > 0.6 ? foreground : PAGE_NUMBER;
    }
}
