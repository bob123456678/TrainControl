package org.traincontrol.gui.autonomy;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.traincontrol.util.I18n;

/**
 * A strip of autonomy notices: the message wrapped to the strip's width, as tall as it needs up to three lines, and
 * scrolled past that (OB-366).  It is hidden while it has nothing to say.
 *
 * In the autonomy editor it floats over the bottom of the diagram (see floatOver), with an X that hides it until the
 * next notice, so the diagram keeps its size and place whatever the strip says.  Above the main window's diagram it is
 * a header that grows to what it says and has no X: what it says there is a state with a button to act on it, which a
 * dismissed strip would leave nowhere to act.
 *
 * It is not a popup: a modal for "that tile is now one way" would be worse than saying nothing.
 *
 * @author Adam
 */
public class AutonomyBanner extends JPanel
{
    /**
     * How long an ordinary message stays before fading out.  Long enough to read a sentence twice,
     * short enough that a stale message is not still on screen for the next click.
     */
    private static final int LINGER_MS = 6000;

    /**
     * The longest an ordinary message will stay, however much of it there is.
     *
     * Six seconds is right for a sentence and wrong for a list of a dozen stations and the reason each
     * one was refused - which is the same banner, and is an answer somebody asked for rather than a
     * notice they happened to be near. The time grows with the reading, up to this.
     */
    private static final int LINGER_MAX_MS = 30000;

    /**
     * Roughly the time to read one character, in milliseconds.
     *
     * Two hundred words a minute is about a thousand characters, which is sixty milliseconds each;
     * this is deliberately slower, because banner text is read once, in passing, by somebody who was
     * looking at their railway a moment ago.
     */
    private static final int MS_PER_CHARACTER = 90;

    /**
     * A light gray, and no line around it: gray is quiet and needs no edge to be a distinct area, where a white strip
     * with a border reads as one more panel rather than as a strip saying something.
     */
    private static final Color PANEL_BACKGROUND = new Color(242, 242, 242);

    private static final Color INFO_BACKGROUND = PANEL_BACKGROUND;
    private static final Color INFO_TEXT = new Color(0, 0, 155);

    private static final Color WARNING_BACKGROUND = new Color(255, 244, 214);
    private static final Color WARNING_TEXT = new Color(120, 80, 0);

    /**
     * What a banner message is set in, shared so that anything sitting beside one can match it rather
     * than guess at it.
     *
     * FOURTEEN, which is what docs/UI-standards.md calls regular text (OB-151). Adam: "increase font
     * size of 'this layout has an autonomy' and 'this page is left out of autonomy' slightly, to match
     * the standard size."
     *
     * Changing it here moves the strip's notices too: the excluded-page label and the findings count
     * both take this font rather than naming a size of their own, which is why one number was enough.
     */
    public static final java.awt.Font MESSAGE_FONT =
        new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);

    /**
     * The message itself.
     *
     * A JEditorPane rather than a JLabel, and in a scroll pane.  A one-line label suits "this tile is
     * now one way" and fails the answer to "why is it not moving", which is a train, a dozen stations
     * and the reason each one was refused: an answer somebody asked a question to get.
     *
     * An editor pane wraps to the width it is given, which a label will not do without being told a
     * pixel count; the scroll pane caps how tall the bar can grow and hands over the rest.  So a
     * sentence still occupies one line and a long answer can be read.
     */
    private final javax.swing.JEditorPane message = new javax.swing.JEditorPane();

    private final javax.swing.JScrollPane scroller = new javax.swing.JScrollPane(message);

    /**
     * The one thing the message invites the user to do, where there is one.
     *
     * On the track diagram the banner is not only used for things that just happened - it also carries
     * "this layout has a setup nobody has loaded", and a sentence saying so with no way to act on it is
     * a sentence that has to be read twice and then acted on somewhere else.
     */
    private final javax.swing.JButton action = new javax.swing.JButton();

    /**
     * What the button currently does, so that a second offer replaces the first rather than firing both.
     */
    private java.awt.event.ActionListener acting;

    private final Timer clear;

    /** The message as it was handed in, so isSaying is not left reading HTML scaffolding. */
    private String saying;

    /** Holds the offer button, and is taken out of the layout with it - see the constructor. */
    private javax.swing.JPanel right;

    /** The right-hand column: the offer, and the X where the strip has one. */
    private final javax.swing.JPanel east;

    /** The X that hides the strip until the next notice, where floatOver gave it one; otherwise null. */
    private javax.swing.JButton close;

    /** Whether the user closed the notice showing.  The next notice clears it. */
    private boolean dismissed;

    /** Whether the message is empty, as it is before the first and after a timed one fades. */
    private boolean blank = true;

    public AutonomyBanner()
    {
        setLayout(new BorderLayout());

        // Padding, and nothing else: the color that carries meaning is on the text.
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        setBackground(INFO_BACKGROUND);

        message.setContentType("text/html");
        message.setEditable(false);
        message.setOpaque(false);
        message.setFocusable(false);
        message.setBorder(null);
        message.setFont(MESSAGE_FONT);
        message.setForeground(INFO_TEXT);

        // The pane's own HTML rendering ignores setFont, so the family and size are put into the
        // document itself - see hold(), which writes them into every message.
        message.setText(" ");

        scroller.setBorder(null);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);

        // The scrollbar is painted in the banner's own color, so it is invisible while the message fits and a
        // look-and-feel track down the edge does not read as a rendering fault; hold() makes it opaque whenever there
        // is more to read than fits, so a long answer never stops in the middle with nothing saying there is more.
        scroller.getVerticalScrollBar().setOpaque(false);
        scroller.getVerticalScrollBar().setBorder(null);

        // Vertically only.  A message that has to be scrolled sideways to be read is a message that
        // has not been shown, and wrapping is what the editor pane is here for.
        scroller.setHorizontalScrollBarPolicy(
            javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scroller.setVerticalScrollBarPolicy(
            javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        scroller.getVerticalScrollBar().setUnitIncrement(16);

        // Centered in the band and never laid out taller than it (OB-151, OB-191); CentredButNeverTaller says why no
        // GridBagConstraints can say both.
        javax.swing.JPanel centred = new javax.swing.JPanel(new CentredButNeverTaller());

        centred.setOpaque(false);

        centred.add(scroller);

        add(centred, BorderLayout.CENTER);

        // An offer that goes with the message, for the states where saying what is wrong is only half
        // the answer.  Hidden unless something has been offered, so an ordinary message is unchanged.
        action.setFocusable(false);
        action.setVisible(false);

        // The standard's button font
        action.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        action.setMargin(new java.awt.Insets(0, 10, 0, 10));

        right = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));

        right.setOpaque(false);
        right.add(action);

        // Kept out of the layout entirely while there is no offer to make, rather than sitting there
        // empty: an invisible button inside a visible panel still reserves the panel's insets, which
        // is a few pixels of nothing on the right of every ordinary message.
        right.setVisible(false);

        east = new javax.swing.JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(right, BorderLayout.CENTER);

        add(east, BorderLayout.EAST);

        clear = new Timer(LINGER_MS, e -> hold(" ", false));
        clear.setRepeats(false);

        // Nothing to say yet
        setVisible(false);
    }

    /**
     * Floats this strip over the bottom of a diagram's visible area, inside its frame and just above its horizontal
     * scrollbar, with an X at its top right that hides it until the next notice (OB-366; Adam: "I'd rather have the
     * band float and have an x to dismiss each time it surfaces, so the user can be in control.").
     *
     * A JLayeredPane holds the two: the diagram fills it and is laid out as if the strip were not there, so it never
     * moves for a notice, and the strip lies on a higher layer, which the layered pane paints again over whatever the
     * diagram repaints or scrolls under it.
     *
     * @param diagram the diagram's scroll pane
     * @return what to mount where the scroll pane was
     */
    public javax.swing.JLayeredPane floatOver(final javax.swing.JScrollPane diagram)
    {
        final javax.swing.JLayeredPane deck = new javax.swing.JLayeredPane();

        deck.setLayout(new OverTheDiagram(diagram, this));

        deck.add(diagram, javax.swing.JLayeredPane.DEFAULT_LAYER);
        deck.add(this, javax.swing.JLayeredPane.PALETTE_LAYER);

        // The scroll pane lays its own viewport out, narrower or shorter as its scrollbars come and go, without asking
        // the deck; the strip follows the viewport's edges.
        followedViewport = diagram.getViewport();

        followingViewport = new java.awt.event.ComponentAdapter()
        {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e)
            {
                deck.revalidate();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e)
            {
                deck.revalidate();
            }
        };

        followedViewport.addComponentListener(followingViewport);

        // A click on the strip stays on it.  Swing hands a click to the topmost component under the pointer that
        // listens for clicks, and without a listener of its own the strip's padding would pass it to a square below.
        addMouseListener(new java.awt.event.MouseAdapter()
        {
        });

        close = new javax.swing.JButton("×");

        close.setName("dismissNotice");
        close.setToolTipText(I18n.t("autosetup.ui.tooltipDismissNotice"));
        close.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        close.setMargin(new java.awt.Insets(0, 4, 0, 4));
        close.putClientProperty("JButton.buttonType", "toolBarButton");

        // Reached with Tab and pressed with Space or Enter, but never given the keyboard by a click (OB-200): the
        // editor's shortcuts are its window's.  While Tab has put the keyboard here, every other key goes on to the
        // window's own handler, so Escape and the shortcuts do what they always do.
        close.setRequestFocusEnabled(false);

        close.addKeyListener(new java.awt.event.KeyAdapter()
        {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e)
            {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) close.doClick();
                else if (e.getKeyCode() != java.awt.event.KeyEvent.VK_SPACE) toTheWindow(e);
            }

            @Override
            public void keyReleased(java.awt.event.KeyEvent e)
            {
                if (e.getKeyCode() != java.awt.event.KeyEvent.VK_SPACE) toTheWindow(e);
            }
        });

        close.addActionListener(e -> dismiss());

        javax.swing.JPanel corner = new javax.swing.JPanel(new BorderLayout());
        corner.setOpaque(false);
        corner.add(close, BorderLayout.NORTH);

        east.add(corner, BorderLayout.EAST);

        return deck;
    }

    /** Stops following the diagram's viewport, for the editor to call when it drops this strip; the viewport outlives it. */
    public void letGoOfTheDiagram()
    {
        if (followedViewport != null) followedViewport.removeComponentListener(followingViewport);

        followedViewport = null;
        followingViewport = null;
    }

    /** What floatOver listens to and with; both null before it and after letGoOfTheDiagram. */
    private javax.swing.JViewport followedViewport;
    private java.awt.event.ComponentListener followingViewport;

    /**
     * Hides the strip until the next notice; the timer is stopped with it, so nothing else changes.
     */
    private void dismiss()
    {
        dismissed = true;

        clear.stop();

        showOrHide();
    }

    /**
     * Shown while it has something to say or to offer and the user has not closed it.
     */
    private void showOrHide()
    {
        boolean shown = !dismissed && (!blank || right.isVisible());

        // The keyboard goes back to the window before the X holding it goes
        if (!shown && close != null && close.isFocusOwner())
        {
            java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);

            if (window != null) window.requestFocusInWindow();
        }

        setVisible(shown);
    }

    /**
     * A key the X does not use, handed to its window's own key handler.
     */
    private void toTheWindow(java.awt.event.KeyEvent e)
    {
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);

        if (window == null) return;

        for (java.awt.event.KeyListener each : window.getKeyListeners())
        {
            if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) each.keyPressed(e);
            else if (e.getID() == java.awt.event.KeyEvent.KEY_RELEASED) each.keyReleased(e);
        }
    }

    /**
     * Says something, for a few seconds.
     *
     * @param text
     */
    public void show(String text)
    {
        show(text, false);
    }

    /**
     * @param text
     * @param warning whether this is something that went wrong, rather than something that happened
     */
    public void show(String text, boolean warning)
    {
        hold(text, warning);

        // Long enough to read what is actually there.  A stale sentence on screen at the next click is
        // a nuisance; an answer taken away before it has been read is a question that has to be asked
        // again, and the second is the worse of the two.
        clear.setInitialDelay(lingerFor(text));
        clear.restart();
    }

    /**
     * How long to leave a message up, from how much of it there is.
     *
     * HTML markup is not counted - it is not read - so a list built as a table does not win time for
     * its own tags.
     *
     * @param text the message, possibly HTML
     * @return milliseconds, between the ordinary linger and the cap
     */
    private static int lingerFor(String text)
    {
        if (text == null) return LINGER_MS;

        int readable = 0;
        boolean inTag = false;

        for (int at = 0; at < text.length(); at++)
        {
            char c = text.charAt(at);

            if (c == '<') inTag = true;
            else if (c == '>') inTag = false;
            else if (!inTag) readable++;
        }

        return Math.max(LINGER_MS, Math.min(LINGER_MAX_MS, readable * MS_PER_CHARACTER));
    }

    /**
     * Says something and leaves it there - for a state the user is in, rather than a thing that just
     * happened.  "Now click the far end of the run" has to survive until they do.
     *
     * @param text
     */
    public void showUntilChanged(String text)
    {
        clear.stop();

        hold(text, false);
    }

    /**
     * Says something and offers one thing to do about it, until told otherwise.
     *
     * @param text the message
     * @param buttonText what the button says, or null for no button
     * @param onPress what pressing it does
     */
    public void offer(String text, String buttonText, final Runnable onPress)
    {
        clear.stop();

        hold(text, false);

        if (acting != null) action.removeActionListener(acting);

        if (buttonText == null || onPress == null)
        {
            acting = null;
            action.setVisible(false);
            right.setVisible(false);

            // And the banner itself goes when it has nothing to offer: as a header, an empty band would hold the
            // checkbox under it down with a gap the user cannot act on or get rid of.
            setVisible(false);

            revalidate();
            repaint();

            return;
        }

        acting = e -> onPress.run();

        action.setText(buttonText);
        action.addActionListener(acting);
        action.setVisible(true);
        right.setVisible(true);

        setVisible(true);

        revalidate();
        repaint();
    }

    /**
     * The button this banner offers, for whoever mounts it to size it like its neighbors.
     * @return
     */
    public javax.swing.JButton getActionButton()
    {
        return action;
    }

    /**
     * Whether this banner is currently saying anything.
     * @return
     */
    public boolean isSaying()
    {
        // The document always carries a body wrapper now, so the markup cannot be the test - what
        // is being asked is whether there is anything to READ.
        return saying != null && !saying.trim().isEmpty();
    }

    /**
     * As tall as the message or the button, whichever is taller, from a one-line floor up to the most lines and the
     * border; past that the message scrolls.
     */
    @Override
    public java.awt.Dimension getPreferredSize()
    {
        java.awt.Insets pad = getInsets();

        int most = mostHeight() + pad.top + pad.bottom;

        java.awt.Dimension natural = super.getPreferredSize();

        // The message's own idea of how tall it needs to be, which the scroll pane will not report
        // - a viewport asks for whatever it was given rather than for what is inside it.
        int wanted = message.getPreferredSize().height + INSET_HEIGHT;

        int height = Math.max(MINIMUM_HEIGHT, Math.min(most, Math.max(wanted, natural.height)));

        return new java.awt.Dimension(10, height);
    }

    /**
     * The height of the most lines a notice is shown at, in the message's own font.
     */
    private int mostHeight()
    {
        return MOST_LINES * message.getFontMetrics(MESSAGE_FONT).getHeight();
    }

    /**
     * Tall enough for a line of text with room around it.
     */
    private static final int MINIMUM_HEIGHT = 26;

    /**
     * The most lines of a message the strip shows; the rest is scrolled to (OB-366, Adam: "cap the height at 3 lines
     * with a scrollbar").
     */
    private static final int MOST_LINES = 3;

    /** The border this panel draws round its message, which the height has to allow for. */
    private static final int INSET_HEIGHT = 10;

    private void hold(String text, boolean warning)
    {
        // Wrapped, not cut off: the bar grows to a few lines and scrolls past that, so nothing is lost.
        String body = text == null || text.trim().isEmpty() ? "&nbsp;" : text;

        if (body.startsWith("<html>")) body = body.substring("<html>".length());

        if (body.endsWith("</html>")) body = body.substring(0, body.length() - "</html>".length());

        java.awt.Color ink = warning ? WARNING_TEXT : INFO_TEXT;

        // The font and the color go into the DOCUMENT: an editor pane showing HTML takes neither
        // from setFont nor from setForeground, so a message set the ordinary way came out in the
        // default serif face in black.
        message.setText("<html><body style=\"font-family:'" + MESSAGE_FONT.getFamily()
            + "'; font-size:" + MESSAGE_FONT.getSize() + "pt; color:rgb("
            + ink.getRed() + "," + ink.getGreen() + "," + ink.getBlue() + "); margin:0\">"
            + body + "</body></html>");

        // Back to the top, so a second answer does not open halfway down where the first was left
        saying = body;

        blank = text == null || text.trim().isEmpty();

        message.setCaretPosition(0);

        // Every notice surfaces, even one saying what the dismissed one said (OB-366, "each time it surfaces")
        dismissed = false;

        showOrHide();

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            scroller.getVerticalScrollBar().setValue(0);

            // And the bar becomes visible when there is more than fits: posted, because that depends on the height
            // the message has just been laid out to, which it does not have until this pass is over.
            boolean more = message.getPreferredSize().height > mostHeight();

            scroller.getVerticalScrollBar().setOpaque(more);

            scroller.getVerticalScrollBar().setVisible(true);

            revalidate();
            repaint();
        });

        setBackground(warning ? WARNING_BACKGROUND : INFO_BACKGROUND);

        repaint();
    }

    /**
     * Lays the diagram over the whole of its layered pane, as if nothing floated over it, and the strip over the
     * bottom of the diagram's viewport: the viewport's width, the strip's own height up to the viewport's.
     */
    private static final class OverTheDiagram implements java.awt.LayoutManager
    {
        private final javax.swing.JScrollPane diagram;
        private final AutonomyBanner band;

        OverTheDiagram(javax.swing.JScrollPane diagram, AutonomyBanner band)
        {
            this.diagram = diagram;
            this.band = band;
        }

        @Override
        public void layoutContainer(java.awt.Container deck)
        {
            diagram.setBounds(0, 0, deck.getWidth(), deck.getHeight());

            // Now, so the viewport's edges are this layout's and not the last one's
            diagram.doLayout();

            if (!band.isVisible()) return;

            java.awt.Rectangle view = javax.swing.SwingUtilities.convertRectangle(diagram,
                diagram.getViewport().getBounds(), deck);

            // At the viewport's width first: how tall a wrapped message is depends on how wide it is
            band.setBounds(view.x, band.getY(), view.width, Math.max(1, band.getHeight()));
            band.validate();

            int high = Math.min(band.getPreferredSize().height, view.height);

            band.setBounds(view.x, view.y + view.height - high, view.width, high);
        }

        @Override
        public java.awt.Dimension preferredLayoutSize(java.awt.Container deck)
        {
            return diagram.getPreferredSize();
        }

        @Override
        public java.awt.Dimension minimumLayoutSize(java.awt.Container deck)
        {
            return diagram.getMinimumSize();
        }

        @Override
        public void addLayoutComponent(String name, java.awt.Component comp)
        {
        }

        @Override
        public void removeLayoutComponent(java.awt.Component comp)
        {
        }
    }

    /**
     * Centers one child in its container, and never lays it out taller than the container is.
     *
     * - **Centered** (OB-151).  A message shorter than the band gets the leftover space split equally above and below
     *   it, where the band is taller than the message: at its one-line floor, or beside the offer button.
     *
     * - **Never taller than the band** (OB-191).  A message LONGER than the band is given exactly the
     *   band.  That is what makes the scroll pane's viewport smaller than its own contents, which is
     *   the only condition under which an AS_NEEDED scrollbar appears - so the part that does not fit
     *   becomes reachable rather than merely absent.
     *
     * `GridBagLayout` can express neither half safely.  `BOTH` gives the whole band to a one-line
     * notice and loses the centring; `HORIZONTAL` keeps the preferred height and, when the band is
     * smaller, shrinks toward the child's MINIMUM - five pixels, measured - rather than to the band.
     * What is wanted is `min(preferred, band)`, and that is a sentence rather than a setting.
     *
     * ONE CHILD, deliberately: this is not a general layout, and a second child would silently be
     * ignored.  The container it is given holds the message and nothing else.
     */
    private static final class CentredButNeverTaller implements java.awt.LayoutManager
    {
        @Override
        public void layoutContainer(java.awt.Container parent)
        {
            if (parent.getComponentCount() == 0) return;

            java.awt.Component only = parent.getComponent(0);

            java.awt.Insets pad = parent.getInsets();

            int width = Math.max(0, parent.getWidth() - pad.left - pad.right);
            int band = Math.max(0, parent.getHeight() - pad.top - pad.bottom);

            int wants = only.getPreferredSize().height;

            // AND NEVER NOTHING.  A text component reports its preferred height by laying its view out at the size it
            // currently has, so one given no height reports none, and `min(0, band)` keeps it there pass after pass.
            // The first pass of a message never laid out (at `pack()` the viewport has no extent) lands in that state.
            // So a child that asks for nothing is given the band, which lets it measure itself on the next pass.
            int height = wants <= 0 ? band : Math.min(wants, band);

            only.setBounds(pad.left, pad.top + (band - height) / 2, width, height);
        }

        @Override
        public java.awt.Dimension preferredLayoutSize(java.awt.Container parent)
        {
            java.awt.Insets pad = parent.getInsets();

            java.awt.Dimension only = parent.getComponentCount() == 0
                ? new java.awt.Dimension() : parent.getComponent(0).getPreferredSize();

            return new java.awt.Dimension(only.width + pad.left + pad.right,
                only.height + pad.top + pad.bottom);
        }

        /**
         * Nothing, and that is the point: reporting the child's own minimum is what let the message be shrunk to five
         * pixels.  This layout never needs any space taken from it; it fits whatever it is given.
         */
        @Override
        public java.awt.Dimension minimumLayoutSize(java.awt.Container parent)
        {
            return new java.awt.Dimension(0, 0);
        }

        @Override
        public void addLayoutComponent(String name, java.awt.Component comp)
        {
        }

        @Override
        public void removeLayoutComponent(java.awt.Component comp)
        {
        }
    }
}
