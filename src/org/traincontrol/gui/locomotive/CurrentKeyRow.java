package org.traincontrol.gui.locomotive;

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
import java.util.function.UnaryOperator;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.traincontrol.util.I18n;

/**
 * The line over the active locomotive's name: its key, its page and its decoder (FR-128).  From the left, a keycap
 * and the page as plain words in the label's own font and color (`pageFont`, `pageColour`: read, not written down
 * again); floated to the right edge, a navy decoder pill with white text, TYPE and ADDRESS divided by a white line
 * that stays inside the rim.
 *
 * `pageWords` is the one rule that words the page, for this row, the page tabs and the page label by the arrows.  Only
 * the NAME is cut (to the pixel here), never the number, its full stop or "Page 5"; with no room for a letter and the
 * ellipsis the words are "5." alone.  The tooltip always has the whole line.
 *
 * Drawn, not laid out: the window's generated GroupLayout fixes this row's slot at 296 x 18 (`CurrentKeyLabel`),
 * nothing below it may move, and the page name has to be measured and cut to the pixel.  Pill words are 12 point (the
 * decoder pill's 13) in 18 pixel shapes.  A pill is painted in `StationCaption`'s style, but not with its symbol
 * LABEL_FONT.
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

    /**
     * The decoder pill's words: one notch larger than the keycap's letter ("Can we increase the font size inside the
     * blue mfx+address pill slightly?").
     */
    public static final float DECODER_SIZE = 13f;

    /** The ellipsis a cut page name ends in. */
    public static final String ELLIPSIS = String.valueOf((char) 0x2026);

    /** The separator in the tooltip, the one the old line used between its parts. */
    private static final String BULLET = " " + (char) 0x2022 + " ";

    /**
     * Measured as drawn (anti-aliased, fractional advances, as the drawing's hints), so a width taken here is the width
     * on screen.
     */
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /** The keycap's letter: medium weight - Segoe UI Semibold, and bold of the plain family where that is not installed. */
    private static final Font KEY_FONT = keyFont();

    /** The decoder pill's words. */
    private static final Font TEXT_FONT = new Font("Segoe UI", Font.PLAIN, 12).deriveFont(DECODER_SIZE);

    private static Font keyFont()
    {
        Font semibold = new Font("Segoe UI Semibold", Font.PLAIN, 12).deriveFont(SIZE);

        // A name that is not installed does not fail, it resolves to Dialog - so ask what came back
        if ("Segoe UI Semibold".equalsIgnoreCase(semibold.getFamily())) return semibold;

        return new Font("Segoe UI", Font.BOLD, 12).deriveFont(SIZE);
    }

    /**
     * The two fonts the pills are drawn in, so a test can ask what size they are.  The page's words have no font of
     * their own (`pageFont`).
     *
     * @return the keycap letter's font and the decoder pill's
     */
    public static Font[] fonts()
    {
        return new Font[] {KEY_FONT, TEXT_FONT};
    }

    /**
     * What goes in front of a page's name when the page numbers are shown: the number, a full stop and a space, "5. ".
     *
     * @param page the page, counting from 1
     * @return the prefix
     */
    public static String pageNumberPrefix(int page)
    {
        return page + ". ";
    }

    /**
     * THE ONE RULE for how a page is worded, for every place that follows "Show Page Numbers" (FR-128): this row, the
     * page tabs and the page label by the arrows.  "If no name, use Page 5.  If yes name, MMM if show is off, and 5.
     * MMM if show is on."  Only the NAME is cut, in the caller's own way; a name with no room at all leaves "5.", so
     * the words of a page are never empty.
     *
     * @param page the page, counting from 1
     * @param name the page's name as the operator typed it, or null or empty for none
     * @param showNumber whether "Show Page Numbers" is ticked
     * @param cutName this place's way of shortening a name that is too long for it: given the name, trimmed and not
     *        empty, it gives back what fits, or empty for no room at all.  Not asked of a page with no name.
     * @return the page's words
     */
    public static String pageWords(int page, String name, boolean showNumber, UnaryOperator<String> cutName)
    {
        String named = name == null ? "" : name.trim();

        if (named.isEmpty()) return I18n.f("page.ui.pageNo", page);

        String cut = cutName.apply(named);

        if (cut == null || cut.isEmpty()) return page + ".";

        return showNumber ? pageNumberPrefix(page) + cut : cut;
    }

    /**
     * What the row says.  A value, so the window can ask whether what it is about to show is what it shows already; the
     * decoder is two parts because the pill draws them apart.
     */
    public static final class Content
    {
        private final String key;
        private final int page;
        private final String name;
        private final String decoderType;
        private final String address;
        private final boolean showNumber;

        /**
         * With the page's number shown, as the row drew it before it was a setting.
         *
         * @param key the key's letter, as on its button
         * @param page the page the key was chosen on
         * @param pageName that page's name as the operator typed it, or empty for a page with none
         * @param decoderType the decoder's type as the program words it ("MFX", "Multi-Unit"), or empty when no
         *        locomotive is on the key
         * @param address the locomotive's address, or empty
         */
        public Content(String key, int page, String pageName, String decoderType, String address)
        {
            this(key, page, pageName, decoderType, address, true);
        }

        /**
         * @param key the key's letter, as on its button
         * @param page the page the key was chosen on
         * @param pageName that page's name as the operator typed it, or empty for a page with none
         * @param decoderType the decoder's type as the program words it ("MFX", "Multi-Unit"), or empty when no
         *        locomotive is on the key
         * @param address the locomotive's address, or empty
         * @param showNumber whether the page's number goes before its name - the Preferences menu's "Show Page Numbers".
         *        A page with no name reads "Page 5" either way.  It is part of what the row says, so the window's check
         *        for whether the line has changed notices a change to it and the line is redrawn at once.
         */
        public Content(String key, int page, String pageName, String decoderType, String address, boolean showNumber)
        {
            this.key = key == null ? "" : key.trim();
            this.page = page;
            this.name = pageName == null ? "" : pageName.trim();
            this.decoderType = decoderType == null ? "" : decoderType.trim();
            this.address = address == null ? "" : address.trim();
            this.showNumber = showNumber;
        }

        /** @return whether the page's number goes before its name in the row (it is always in the tooltip) */
        public boolean showsNumber()
        {
            return showNumber;
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
         * The whole line in words, for the tooltip and a screen reader: the key, the page as the program words it
         * everywhere else ("Page 5 (Example)", or "Page 5" with no name) and the decoder ("MFX 54").  It begins with
         * the key, never a tag, so Swing takes it as plain text.
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
                && address.equals(c.address) && showNumber == c.showNumber;
        }

        @Override
        public int hashCode()
        {
            return java.util.Objects.hash(key, page, name, decoderType, address, showNumber);
        }

        @Override
        public String toString()
        {
            return getTooltip();
        }
    }

    /**
     * Where each piece goes in a box of a given size and what is drawn in it; the painting and the tests both read
     * this.
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

        /** The page's words as drawn, as `pageWords` words them. */
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
     * A plain JComponent has no accessible context, which `setContent` names: a label's role, with the line in words.
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
     * The font the page's words are drawn in: the label's own, inherited (Segoe UI Semibold 13 in the form), asked and
     * not written down again.
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
     * The color the page's words are drawn in: the label's foreground, inherited as the font is (rgb(0,0,115) in the
     * form).
     *
     * @return the color
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
     * Where everything goes in a box this size, or null when there is nothing to show.  The decoder pill is sized
     * first, from the right edge; what is left between the keycap and it, less the "5. " and a gap, is the page name's.
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

            // centered in the pill, which is the whole of PAD at each end when the pill is as wide as its words
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

        // The page's words, from the left, as `pageWords` words them; the "5. " and the "Page 5" are never cut.
        final Font pageFont = pageFont();

        String number = String.valueOf(c.getPage());
        String prefix = c.showsNumber() ? pageNumberPrefix(c.getPage()) : "";

        // What is left, once the shapes that never give way have their room, is the name's.  A pixel is kept back for
        // the kerning between the "5. " and the name.
        final double nameRoom = width - x - reserved - Math.ceil(advance(pageFont, prefix)) - 1;

        // The cut name is kept as well as the words, to be told apart from them ("" for no name, or no room).
        final String[] drawnName = {""};

        String pageText = pageWords(c.getPage(), c.getPageName(), c.showsNumber(),
            named -> drawnName[0] = fit(named, nameRoom, pageFont));

        String name = drawnName[0];

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

    /** How tall the capitals are: the height a pill's words are centered on, rather than the line they sit in. */
    private static double capHeight(Font font)
    {
        return font.createGlyphVector(FRC, "H").getVisualBounds().getHeight();
    }

    /** The natural size: only its height is a promise, as the slot gives the row its width. */
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

    /** THE PAGE'S WORDS, in the label's font and color with nothing round them, centered on their capitals. */
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
     * A key: a thin outline all round and a heavier one along the bottom; the face is the panel's own, nothing is
     * filled.
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

    /** The words' color: the look and feel's own for a label. */
    private static Color foreground()
    {
        Color c = UIManager.getColor("Label.foreground");

        return c == null ? Color.BLACK : c;
    }

    /** The outline's color: the look and feel's own for the edge of a component. */
    private static Color outline()
    {
        Color c = UIManager.getColor("Component.borderColor");

        return c == null ? new Color(170, 170, 170) : c;
    }
}
