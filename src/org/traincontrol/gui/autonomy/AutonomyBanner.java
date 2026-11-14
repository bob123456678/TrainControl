package org.traincontrol.gui.autonomy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.traincontrol.util.I18n;

/**
 * A strip of autonomy notices, wrapped to its width, up to four lines tall and scrolled past that (OB-366, MT-864);
 * hidden while it has nothing to say.  In the autonomy editor it floats over the bottom of the diagram (see floatOver)
 * with an X; above the main window's diagram it is a header with no X, since a dismissed strip would leave its state
 * nowhere to act.  Not a popup.
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
     * The longest an ordinary message stays, however much of it there is: the time grows with the reading, up to this,
     * for the long answers somebody asked for.
     */
    private static final int LINGER_MAX_MS = 30000;

    /**
     * Roughly the time to read one character, deliberately slower than 200 words a minute: banner text is read once, in
     * passing.
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
     * What a banner message is set in, shared so that anything beside one can match it.  FOURTEEN, which
     * docs/UI-standards.md calls regular text (OB-151).  The excluded-page label and the findings count take this font
     * rather than naming a size of their own.
     */
    public static final Font MESSAGE_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    /**
     * The message itself: a JEditorPane in a scroll pane rather than a JLabel, since a label fits "this tile is now one
     * way" but not "why is it not moving" (a train, a dozen stations and each reason).  The pane wraps to the width it
     * is given and the scroll pane caps how tall the bar can grow.
     */
    private final JEditorPane message = new JEditorPane();

    private final JScrollPane scroller = new JScrollPane(message);

    /**
     * The one thing the message invites the user to do, where there is one: the banner also carries states such as
     * "this layout has a setup nobody has loaded", which need a way to act.
     */
    private final JButton action = new JButton();

    /** What the button currently does, so that a second offer replaces the first rather than firing both. */
    private java.awt.event.ActionListener acting;

    private final Timer clear;

    /** The message as it was handed in, so isSaying is not left reading HTML scaffolding. */
    private String saying;

    /** Holds the offer button, and is taken out of the layout with it - see the constructor. */
    private JPanel right;

    /** The right-hand column: the offer, and the X where the strip has one. */
    private final JPanel east;

    /** The X that hides the strip until the next notice, where floatOver gave it one; otherwise null. */
    private JButton close;

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

        // The scrollbar is painted in the banner's own color, so it is invisible while the message fits; hold() makes
        // it opaque whenever there is more to read than fits, so a long answer never stops in the middle with nothing
        // saying so.
        scroller.getVerticalScrollBar().setOpaque(false);
        scroller.getVerticalScrollBar().setBorder(null);

        // Vertically only.  A message that has to be scrolled sideways to be read is a message that
        // has not been shown, and wrapping is what the editor pane is here for.
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        scroller.getVerticalScrollBar().setUnitIncrement(16);

        // Centered in the band and never laid out taller than it (OB-151, OB-191); CentredButNeverTaller says why no
        // GridBagConstraints can say both.
        JPanel centred = new JPanel(new CentredButNeverTaller());

        centred.setOpaque(false);

        centred.add(scroller);

        add(centred, BorderLayout.CENTER);

        // An offer that goes with the message, for the states where saying what is wrong is only half
        // the answer.  Hidden unless something has been offered, so an ordinary message is unchanged.
        action.setFocusable(false);
        action.setVisible(false);

        // The standard's button font
        action.setFont(new Font("Segoe UI", Font.BOLD, 12));
        action.setMargin(new Insets(0, 10, 0, 10));

        right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

        right.setOpaque(false);
        right.add(action);

        // Kept out of the layout entirely while there is no offer to make, rather than sitting there
        // empty: an invisible button inside a visible panel still reserves the panel's insets, which
        // is a few pixels of nothing on the right of every ordinary message.
        right.setVisible(false);

        east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(right, BorderLayout.CENTER);

        add(east, BorderLayout.EAST);

        clear = new Timer(LINGER_MS, e -> hold(" ", false));
        clear.setRepeats(false);

        // Nothing to say yet
        setVisible(false);
    }

    /**
     * Floats this strip over the bottom of a diagram's visible area, with an X at its top right that hides it until the
     * next notice (OB-366), on a higher layer of a JLayeredPane so the diagram never moves for a notice.
     *
     * @param diagram the diagram's scroll pane
     * @return what to mount where the scroll pane was
     */
    public JLayeredPane floatOver(final JScrollPane diagram)
    {
        final JLayeredPane deck = new JLayeredPane();

        deck.setLayout(new OverTheDiagram(diagram, this));

        deck.add(diagram, JLayeredPane.DEFAULT_LAYER);
        deck.add(this, JLayeredPane.PALETTE_LAYER);

        // The scroll pane lays its own viewport out, narrower or shorter as its scrollbars come and go, without asking
        // the deck; the strip follows the viewport's edges.
        followedViewport = diagram.getViewport();

        followingViewport = new java.awt.event.ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                deck.revalidate();
            }

            @Override
            public void componentMoved(ComponentEvent e)
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

        close = new JButton("×");

        close.setName("dismissNotice");
        close.setToolTipText(I18n.t("autosetup.ui.tooltipDismissNotice"));
        close.setFont(new Font("Segoe UI", Font.BOLD, 12));
        close.setMargin(new Insets(0, 4, 0, 4));
        close.putClientProperty("JButton.buttonType", "toolBarButton");

        // Reached with Tab and pressed with Space or Enter, but never given the keyboard by a click (OB-200): the
        // editor's shortcuts are its window's.  While Tab has put the keyboard here, every other key goes on to the
        // window's own handler, so Escape and the shortcuts do what they always do.
        close.setRequestFocusEnabled(false);

        close.addKeyListener(new java.awt.event.KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) close.doClick();
                else if (e.getKeyCode() != KeyEvent.VK_SPACE) toTheWindow(e);
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                if (e.getKeyCode() != KeyEvent.VK_SPACE) toTheWindow(e);
            }
        });

        close.addActionListener(e -> dismiss());

        JPanel corner = new JPanel(new BorderLayout());
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

    /** Hides the strip until the next notice; the timer is stopped with it, so nothing else changes. */
    private void dismiss()
    {
        dismissed = true;

        clear.stop();

        showOrHide();
    }

    /** Closes the strip as its X does, until the next notice. */
    public void hideUntilNextNotice()
    {
        dismiss();
    }

    /** Shown while it has something to say or to offer and the user has not closed it. */
    private void showOrHide()
    {
        boolean shown = !dismissed && (!blank || right.isVisible());

        // The keyboard goes back to the window before the X holding it goes
        if (!shown && close != null && close.isFocusOwner())
        {
            Window window = SwingUtilities.getWindowAncestor(this);

            if (window != null) window.requestFocusInWindow();
        }

        setVisible(shown);
    }

    /** A key the X does not use, handed to its window's own key handler. */
    private void toTheWindow(KeyEvent e)
    {
        Window window = SwingUtilities.getWindowAncestor(this);

        if (window == null) return;

        for (java.awt.event.KeyListener each : window.getKeyListeners())
        {
            if (e.getID() == KeyEvent.KEY_PRESSED) each.keyPressed(e);
            else if (e.getID() == KeyEvent.KEY_RELEASED) each.keyReleased(e);
        }
    }

    /** Says something, for a few seconds. */
    public void show(String text)
    {
        show(text, false);
    }

    /**
     * @param warning whether this is something that went wrong, rather than something that happened
     */
    public void show(String text, boolean warning)
    {
        hold(text, warning);

        // Long enough to read what is actually there: an answer taken away before it has been read is a question that
        // has to be asked again, which is worse than a stale sentence on screen at the next click.
        clear.setInitialDelay(lingerFor(text));
        clear.restart();
    }

    /**
     * How long to leave a message up, from how much of it there is; HTML markup is not counted, so a table does not win
     * time for its own tags.
     *
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
     * Says something and leaves it there, for a state the user is in: "Now click the far end of the run" must survive
     * until they do.
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

    /** The button this banner offers, for whoever mounts it to size it like its neighbors. */
    public JButton getActionButton()
    {
        return action;
    }

    /** Whether this banner is currently saying anything. */
    public boolean isSaying()
    {
        // The document always carries a body wrapper, so the markup cannot be the test - what
        // is being asked is whether there is anything to READ.
        return saying != null && !saying.trim().isEmpty();
    }

    /**
     * As tall as the message or the button, whichever is taller, from a one-line floor up to the most lines and the
     * border; past that the message scrolls.
     */
    @Override
    public Dimension getPreferredSize()
    {
        Insets pad = getInsets();

        int most = mostHeight() + pad.top + pad.bottom;

        Dimension natural = super.getPreferredSize();

        // The message's own idea of how tall it needs to be, which the scroll pane will not report
        // - a viewport asks for whatever it was given rather than for what is inside it.
        int wanted = message.getPreferredSize().height + INSET_HEIGHT;

        int height = Math.max(MINIMUM_HEIGHT, Math.min(most, Math.max(wanted, natural.height)));

        return new Dimension(10, height);
    }

    /** The height of the most lines a notice is shown at, in the message's own font. */
    private int mostHeight()
    {
        return MOST_LINES * message.getFontMetrics(MESSAGE_FONT).getHeight();
    }

    /** Tall enough for a line of text with room around it. */
    private static final int MINIMUM_HEIGHT = 26;

    /** The most lines of a message the strip shows; the rest is scrolled to (OB-366, MT-864). */
    private static final int MOST_LINES = 4;

    /** The border this panel draws round its message, which the height has to allow for. */
    private static final int INSET_HEIGHT = 10;

    private void hold(String text, boolean warning)
    {
        // Wrapped, not cut off: the bar grows to a few lines and scrolls past that, so nothing is lost.
        String body = text == null || text.trim().isEmpty() ? "&nbsp;" : text;

        if (body.startsWith("<html>")) body = body.substring("<html>".length());

        if (body.endsWith("</html>")) body = body.substring(0, body.length() - "</html>".length());

        Color ink = warning ? WARNING_TEXT : INFO_TEXT;

        // The font and the color go into the DOCUMENT: an editor pane showing HTML takes neither from setFont nor from
        // setForeground, so a message set the ordinary way comes out in the default serif face in black.
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

        SwingUtilities.invokeLater(() ->
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
    private static final class OverTheDiagram implements LayoutManager
    {
        private final JScrollPane diagram;
        private final AutonomyBanner band;

        OverTheDiagram(JScrollPane diagram, AutonomyBanner band)
        {
            this.diagram = diagram;
            this.band = band;
        }

        @Override
        public void layoutContainer(Container deck)
        {
            diagram.setBounds(0, 0, deck.getWidth(), deck.getHeight());

            // Now, so the viewport's edges are this layout's and not the last one's
            diagram.doLayout();

            if (!band.isVisible()) return;

            java.awt.Rectangle view = SwingUtilities.convertRectangle(diagram, diagram.getViewport().getBounds(), deck);

            // At the viewport's width first: how tall a wrapped message is depends on how wide it is
            band.setBounds(view.x, band.getY(), view.width, Math.max(1, band.getHeight()));
            band.validate();

            int high = Math.min(band.getPreferredSize().height, view.height);

            band.setBounds(view.x, view.y + view.height - high, view.width, high);
        }

        @Override
        public Dimension preferredLayoutSize(Container deck)
        {
            return diagram.getPreferredSize();
        }

        @Override
        public Dimension minimumLayoutSize(Container deck)
        {
            return diagram.getMinimumSize();
        }

        @Override
        public void addLayoutComponent(String name, Component comp)
        {
        }

        @Override
        public void removeLayoutComponent(Component comp)
        {
        }
    }

    /**
     * Centers one child in its container and never lays it out taller than the container is (OB-151, OB-191): a LONGER
     * message is given exactly the band, so the scroll pane's AS_NEEDED scrollbar appears.  `GridBagLayout` cannot
     * express both.  ONE CHILD, deliberately.
     */
    private static final class CentredButNeverTaller implements LayoutManager
    {
        @Override
        public void layoutContainer(Container parent)
        {
            if (parent.getComponentCount() == 0) return;

            Component only = parent.getComponent(0);

            Insets pad = parent.getInsets();

            int width = Math.max(0, parent.getWidth() - pad.left - pad.right);
            int band = Math.max(0, parent.getHeight() - pad.top - pad.bottom);

            int wants = only.getPreferredSize().height;

            // AND NEVER NOTHING: a text component reports its preferred height by laying its view out at the size it
            // has, so one given no height reports none and `min(0, band)` keeps it there.  A child that asks for
            // nothing is given the band.
            int height = wants <= 0 ? band : Math.min(wants, band);

            only.setBounds(pad.left, pad.top + (band - height) / 2, width, height);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent)
        {
            Insets pad = parent.getInsets();

            Dimension only = parent.getComponentCount() == 0
                ? new Dimension() : parent.getComponent(0).getPreferredSize();

            return new Dimension(only.width + pad.left + pad.right, only.height + pad.top + pad.bottom);
        }

        /**
         * Nothing, and that is the point: reporting the child's own minimum would let the message be shrunk to five
         * pixels.  This layout never needs any space taken from it; it fits whatever it is given.
         */
        @Override
        public Dimension minimumLayoutSize(Container parent)
        {
            return new Dimension(0, 0);
        }

        @Override
        public void addLayoutComponent(String name, Component comp)
        {
        }

        @Override
        public void removeLayoutComponent(Component comp)
        {
        }
    }
}
