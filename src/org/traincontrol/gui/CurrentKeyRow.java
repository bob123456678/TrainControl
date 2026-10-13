package org.traincontrol.gui;

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
 * The line over the active locomotive's name: its key, its page and its decoder (FR-128).
 *
 * Adam: *"the active locomotive info is currently text consisting of [letter] . Page [num] ([page name]) .
 * [decodertype] [address]. the page number could be condensed into a nice pill, along with the other info.  make it
 * look more modern."*  He chose, from mockups, the one he called D, and then (rounds 2 to 4, on seeing it) asked for
 * changes to it, so that it is now:
 *
 *   1. **a keycap** - the key's letter on a small rounded square with a heavier bottom edge, so it reads as a key;
 *   2. **the page, as plain words** - the name and then the number in parentheses, "Example (5)", with no pill round
 *      them: *"Change the page title to just the name with page number in parens after, no pill around that, in the
 *      standard blue label font.  Example: MMMM... (5)"*.  No word "Page" in it (*"I wouldn't like to see the word
 *      'page' in it"*).  The standard blue label font is the one the line was set in before this feature, the font and
 *      the colour of the generated `CurrentKeyLabel` the row sits in - Segoe UI Semibold 13, rgb(0,0,115);
 *   3. **a decoder pill** - a solid navy capsule with white text, in two parts the way the data is: the decoder TYPE and
 *      the ADDRESS, with a white vertical line between them ("MFX | 12"): *"add a vertical line from top to bottom
 *      (white) separating the type from the number ... while maintaining a blue border around it"*.  It floats to the
 *      RIGHT edge of the slot: *"Can we also float the address to the right?"*
 *
 * **The page's words are the label's, and are not chosen here.**  The row is a child of `CurrentKeyLabel` and inherits
 * its font and foreground, which is what `pageFont` and `pageColour` ask.  They are read, not written down again: a
 * change to the label in the form is a change to this line, as it was when the line was the label's own text.  A row not
 * yet in a label draws as any label would by default.
 *
 * **The pills' words are 12 point** (round 3: *"Increase font sizes by 1 notch to improve visibility - make sure
 * everything still fits vertically"*), in 18 pixel shapes.  At 12 point Segoe UI's capitals are about 8.4 pixels tall
 * and its letters hang about 2.8 below the baseline, and the baseline is set so the capitals sit in the middle of the
 * pill, so nothing is clipped and nothing touches the rim.  The page's words are 13 point Semibold with no shape round
 * them, in the same 18 pixels: its capitals are about 9.1 tall and its letters hang about 3.1 below the baseline, centred
 * the same way, and that is the size the line was set in in the slot before there were any pills.
 *
 * **The divider stays inside the border.**  The decoder pill's white line runs from one pixel below the pill's top edge
 * to one pixel above its bottom edge and is one pixel wide, so the navy rim is continuous top and bottom and the line
 * does not cut through it.
 *
 * **It is drawn, not laid out.**  The window's panel is a generated GroupLayout with this row's slot fixed at 296 x 18
 * (`CurrentKeyLabel`), and nothing below it may move.  A component that paints a few things into the box it is given can
 * promise that.  It also lets the one thing that has to give way - the page NAME - be measured and cut to the pixel,
 * which a label cannot do for a piece of a sentence.
 *
 * **What gives way, and what never does** (Adam: *"make sure we test with wide page names (like MMMMMMMMMMM)"*).  The
 * keycap and the decoder pill are drawn whole, and so are the page's " (5)": only the page NAME is cut, with an
 * ellipsis ("MMMM... (5)"), to whatever room is left between the keycap and the decoder pill, a gap short of each.  A
 * name with no room for even one letter and the ellipsis is left out, and the words are "(5)" alone, as they are for a
 * page that has no name.  A bare "(5)" rather than a bare "5": the parentheses are what say that the number is the
 * page's, as they do after a name, where a lone digit beside a key and an address would be read as either.  The full
 * words, "Page 5 (Example)", are in the tooltip.
 *
 * **Why not `StationCaption`.**  The diagram's captions are labels whose fill is a state colour, drawn at a size taken
 * from the tile; this is a few fixed shapes and a line of words in a fixed box.  What is borrowed is its house style for
 * painting a pill: the exact shape rather than `drawRoundRect(w - 1, h - 1)`, and text centred on the capitals rather
 * than on the ascent.  Its `LABEL_FONT` is not used because that name is "Segoe UI Symbol" on machines that have it,
 * chosen for the diagram's arrows; it has no bold, and none of this row's words are arrows.
 *
 * **Colours come from the look and feel where Adam did not choose one**: the keycap's edge is the component border
 * colour and its letter the label foreground, so a theme that turns them light turns them.  He chose the navy and the
 * white of the decoder pill (the navy is the exact colour of the line's text before FR-128) and, by asking for the
 * standard blue label font, the colour of the page's words.  This program ships only the light theme.
 *
 * @author Adam
 */
public class CurrentKeyRow extends JComponent
{
    /** The decoder pill's fill: the exact navy of the old line's text, `CurrentKeyLabel`'s foreground (Adam's choice). */
    public static final Color DECODER_FILL = new Color(0, 0, 115);

    /** The words on the decoder pill, and the line between its two parts. */
    public static final Color DECODER_TEXT = Color.WHITE;

    /** How tall the shapes are, in pixels, and the tallest this row ever is: the slot's own height. */
    public static final int PILL_HEIGHT = 18;

    /** The least room between one shape and the next; the decoder pill is at the right, so the page pill's may be more. */
    public static final int GAP = 5;

    /** The corner radius of the keycap. */
    private static final double KEY_RADIUS = 4;

    /** Room either side of the letter on the keycap, not counting its edge. */
    private static final int KEY_PAD = 5;

    /** Room between the decoder pill's curved ends and its words. */
    private static final int PAD = 8;

    /** Room each side of the divider in the decoder pill. */
    private static final int DIVIDER_PAD = 6;

    /** The text size, in points, of the keycap's letter and the decoder pill's words: 12 in an 18 pixel pill. */
    public static final float SIZE = 12f;

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

    /** The decoder pill's words. */
    private static final Font TEXT_FONT = new Font("Segoe UI", Font.PLAIN, 12).deriveFont(SIZE);

    private static Font keyFont()
    {
        Font semibold = new Font("Segoe UI Semibold", Font.PLAIN, 12).deriveFont(SIZE);

        // A name that is not installed does not fail, it resolves to Dialog - so ask what came back
        if ("Segoe UI Semibold".equalsIgnoreCase(semibold.getFamily())) return semibold;

        return new Font("Segoe UI", Font.BOLD, 12).deriveFont(SIZE);
    }

    /**
     * The two fonts the pills are drawn in - the keycap's letter and the decoder pill's words - so a test can ask what
     * size they are rather than take the constant's word for it.  The page's words have no font of their own: they are
     * the label's (`pageFont`).
     *
     * @return the keycap letter's font and the decoder pill's
     */
    public static Font[] fonts()
    {
        return new Font[] {KEY_FONT, TEXT_FONT};
    }

    /**
     * What the row says.  A value, so the window can ask whether what it is about to show is what it shows already.
     *
     * The window used to compare the line's HTML text for that, to decide whether the locomotive's picture needed
     * repainting.  This is the same question about the same things - and the decoder is two of them, the type and the
     * address, because the pill draws them apart and a string cut in two would be a guess about where.
     */
    public static final class Content
    {
        private final String key;
        private final int page;
        private final String name;
        private final String decoderType;
        private final String address;

        /**
         * @param key the key's letter, as on its button
         * @param page the page the key was chosen on
         * @param pageName that page's name as the operator typed it, or empty for a page with none
         * @param decoderType the decoder's type as the program words it ("MFX", "Multi-Unit"), or empty when no
         *        locomotive is on the key
         * @param address the locomotive's address, or empty
         */
        public Content(String key, int page, String pageName, String decoderType, String address)
        {
            this.key = key == null ? "" : key.trim();
            this.page = page;
            this.name = pageName == null ? "" : pageName.trim();
            this.decoderType = decoderType == null ? "" : decoderType.trim();
            this.address = address == null ? "" : address.trim();
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

        /** @return the decoder's type, or empty */
        public String getDecoderType()
        {
            return decoderType;
        }

        /** @return the address, or empty */
        public String getAddress()
        {
            return address;
        }

        /** @return whether a locomotive is on the key */
        public boolean hasDecoder()
        {
            return !decoderType.isEmpty() || !address.isEmpty();
        }

        /**
         * The whole line in words, for the tooltip and for a screen reader: the key, the page as the program words it
         * everywhere else ("Page 5 (Example)", or "Page 5" with no name) and the decoder as the line has always read,
         * the type and the address with a space between ("MFX 54").  The page's words are the bundle's own, so there is
         * nothing new to translate.  It begins with the key, never with a tag, so Swing takes it as plain text however
         * the page was named.
         *
         * @return the line, with nothing cut
         */
        public String getTooltip()
        {
            StringBuilder text = new StringBuilder(key);

            if (text.length() > 0) text.append(BULLET);

            text.append(name.isEmpty() ? I18n.f("page.ui.pageNo", page) : I18n.f("page.ui.plainPageName", page, name));

            if (hasDecoder())
            {
                text.append(BULLET).append(decoderType);

                if (!decoderType.isEmpty() && !address.isEmpty()) text.append(' ');

                text.append(address);
            }

            return text.toString();
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;

            if (!(other instanceof Content)) return false;

            Content c = (Content) other;

            return page == c.page && key.equals(c.key) && name.equals(c.name) && decoderType.equals(c.decoderType)
                && address.equals(c.address);
        }

        @Override
        public int hashCode()
        {
            return java.util.Objects.hash(key, page, name, decoderType, address);
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

        /** The page's words: the box they are set in, as tall as the pills and as wide as the words. */
        public final Rectangle page;

        /** The decoder pill, at the slot's right edge, or null when no locomotive is on the key. */
        public final Rectangle decoder;

        /** The white line between the decoder's type and its address, inside the pill's rim, or null for one part only. */
        public final Rectangle divider;

        /** The key's letter as drawn. */
        public final String key;

        /** The page number, as digits. */
        public final String number;

        /** The page name as drawn: whole, cut with an ellipsis, or empty when it has been left out. */
        public final String name;

        /** The page's words as drawn: the name, a space and the number in parentheses - "Example (5)" - or "(5)" alone. */
        public final String pageText;

        /** The decoder's type as drawn, or empty. */
        public final String decoderType;

        /** The address as drawn, or empty. */
        public final String decoderAddress;

        /** Where each piece of text starts, in pixels from the row's left. */
        public final double keyX, pageX, typeX, addressX;

        Geometry(Rectangle keycap, Rectangle page, Rectangle decoder, Rectangle divider, String key, String number,
                 String name, String pageText, String decoderType, String decoderAddress, double keyX, double pageX,
                 double typeX, double addressX)
        {
            this.keycap = keycap;
            this.page = page;
            this.decoder = decoder;
            this.divider = divider;
            this.key = key;
            this.number = number;
            this.name = name;
            this.pageText = pageText;
            this.decoderType = decoderType;
            this.decoderAddress = decoderAddress;
            this.keyX = keyX;
            this.pageX = pageX;
            this.typeX = typeX;
            this.addressX = addressX;
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

    /**
     * The font the page's words are drawn in: the one this row inherits from the label it sits in, `CurrentKeyLabel`'s
     * own (Segoe UI Semibold 13 in the form).  Asked, not written down again.  Before the row is in a label, an ordinary
     * label's.
     *
     * @return the font
     */
    public Font pageFont()
    {
        Font font = getFont();

        if (font == null) font = UIManager.getFont("Label.font");

        return font == null ? new Font("Dialog", Font.PLAIN, 13) : font;
    }

    /**
     * The colour the page's words are drawn in: the label's foreground, inherited as the font is (rgb(0,0,115) in the
     * form).
     *
     * @return the colour
     */
    public Color pageColour()
    {
        Color colour = getForeground();

        return colour == null ? foreground() : colour;
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
     * Left to right: the keycap and the page's words from the left edge, a `GAP` apart; the decoder pill from the RIGHT
     * edge.  The decoder pill is sized first, because it takes its place at the right, and what is left between the
     * keycap and it, less the " (5)" and a gap, is the page name's.
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

        // THE DECODER PILL, from the right edge: the type, a white line, the address
        Rectangle decoder = null, divider = null;
        double typeX = 0, addressX = 0;
        int reserved = 0;

        String type = c.getDecoderType(), address = c.getAddress();

        if (c.hasDecoder())
        {
            int typeWidth = type.isEmpty() ? 0 : (int) Math.ceil(advance(TEXT_FONT, type));
            int addressWidth = address.isEmpty() ? 0 : (int) Math.ceil(advance(TEXT_FONT, address));

            boolean both = !type.isEmpty() && !address.isEmpty();

            int inside = typeWidth + addressWidth + (both ? 2 * DIVIDER_PAD + 1 : 0);
            int w = Math.max(h, inside + 2 * PAD);

            decoder = new Rectangle(Math.max(0, width - w), top, w, h);

            // centred in the pill, which is the whole of PAD at each end when the pill is as wide as its words
            int start = decoder.x + (w - inside) / 2;

            typeX = start;

            if (both)
            {
                // one pixel in from the top and from the bottom, so the navy rim is unbroken above and below it
                divider = new Rectangle(start + typeWidth + DIVIDER_PAD, top + 1, 1, h - 2);

                addressX = divider.x + 1 + DIVIDER_PAD;
            }
            else
            {
                addressX = start;
            }

            reserved = w + GAP;
        }

        // THE PAGE'S WORDS, from the left: the name, then the number in parentheses.  The " (5)" is never cut.
        Font pageFont = pageFont();

        String number = String.valueOf(c.getPage());
        String suffix = " (" + number + ")";

        // What is left, once the shapes that never give way have their room, is the name's.  A pixel is kept back for the
        // words being measured whole and the name and the " (5)" apart, which the kerning between them can tell by a hair.
        double nameRoom = width - x - reserved - Math.ceil(advance(pageFont, suffix)) - 1;

        String name = fit(c.getPageName(), nameRoom, pageFont);

        // a page with no name is "(5)": the number in the same parentheses, without the name before it
        String pageText = name.isEmpty() ? "(" + number + ")" : name + suffix;

        Rectangle page = new Rectangle(x, top, (int) Math.ceil(advance(pageFont, pageText)), h);

        return new Geometry(keycap, page, decoder, divider, c.getKey(), number, name, pageText, type, address,
            keycap == null ? 0 : (keycap.width - keyAdvance) / 2.0, x, typeX, addressX);
    }

    /**
     * The page name that fits in this much room: whole, or cut with an ellipsis, or empty when not even one letter
     * and the ellipsis fit.
     */
    private static String fit(String name, double room, Font font)
    {
        if (name.isEmpty() || advance(font, name) <= room) return name;

        double dots = advance(font, ELLIPSIS);

        for (int n = name.length() - 1; n >= 1; n--)
        {
            // never cut between the two halves of one character
            if (Character.isHighSurrogate(name.charAt(n - 1))) continue;

            String head = name.substring(0, n).trim();

            if (!head.isEmpty() && advance(font, head) + dots <= room) return head + ELLIPSIS;
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
     * The natural size: every piece whole and the shapes a gap apart, and the slot's height.  Only its height is a
     * promise - the row is given its width by the slot it sits in, and gives way inside it.
     */
    @Override
    public Dimension getPreferredSize()
    {
        if (isPreferredSizeSet()) return super.getPreferredSize();

        Geometry whole = geometry(10000, PILL_HEIGHT);

        int width = whole == null ? 0 : whole.page.x + whole.page.width + (whole.decoder == null ? 0 : GAP + whole.decoder.width);

        return new Dimension(width, PILL_HEIGHT);
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

            paintPage(g2, at);

            if (at.decoder != null)
            {
                paintDecoder(g2, at);
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * THE PAGE'S WORDS: the name and the number in parentheses, in the label's font and colour, with nothing round them -
     * no outline, no fill, no line - centred on their capitals in the slot's height.
     */
    private void paintPage(Graphics2D g2, Geometry at)
    {
        Font font = pageFont();

        g2.setFont(font);
        g2.setColor(pageColour());
        g2.drawString(at.pageText, (float) at.pageX, (float) baseline(at.page.y, at.page.height, font));
    }

    /**
     * THE DECODER PILL: solid navy, white words, and a white line between the type and the address.  The line is
     * painted after the fill, inside the rim, so the navy edge is all that is at the top and bottom of it.
     */
    private void paintDecoder(Graphics2D g2, Geometry at)
    {
        Rectangle d = at.decoder;

        g2.setColor(DECODER_FILL);
        g2.fill(new RoundRectangle2D.Double(d.x, d.y, d.width, d.height, d.height, d.height));

        g2.setColor(DECODER_TEXT);

        if (at.divider != null)
        {
            g2.fillRect(at.divider.x, at.divider.y, at.divider.width, at.divider.height);
        }

        g2.setFont(TEXT_FONT);

        double base = baseline(d.y, d.height, TEXT_FONT);

        if (!at.decoderType.isEmpty()) g2.drawString(at.decoderType, (float) at.typeX, (float) base);

        if (!at.decoderAddress.isEmpty()) g2.drawString(at.decoderAddress, (float) at.addressX, (float) base);
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
}
