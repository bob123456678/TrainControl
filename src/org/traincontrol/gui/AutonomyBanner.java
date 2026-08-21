package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A one-line message across the top of the editor window.
 *
 * What this replaces: messages used to go into a label in the tools column, where a sentence had to
 * wrap to three lines in a 280px space - so the common case was a paragraph squeezed into a corner,
 * and the moment it wrapped it dragged the whole column out of shape.  A strip across the top has the
 * width a sentence needs, sits where the eye already goes after clicking, and cannot disturb anything
 * because it is a fixed-height row of its own.
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
     * notice they happened to be near. The time now grows with the reading, up to this.
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

    private static final Color PANEL_BACKGROUND = Color.WHITE;
    private static final Color PANEL_LINE = new Color(204, 204, 204);

    private static final Color INFO_BACKGROUND = PANEL_BACKGROUND;
    private static final Color INFO_TEXT = new Color(0, 0, 155);

    private static final Color WARNING_BACKGROUND = new Color(255, 244, 214);
    private static final Color WARNING_TEXT = new Color(120, 80, 0);

    /**
     * What a banner message is set in, shared so that anything sitting beside one can match it rather
     * than guess at it.
     */
    public static final java.awt.Font MESSAGE_FONT =
        new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13);

    /**
     * The message itself.
     *
     * A JEditorPane rather than a JLabel, and in a scroll pane.
     *
     * The label was kept to ONE line, with nobr forced on so that a long message ran off the right
     * edge rather than pushing the bar open and shoving the diagram down.  That is the right trade
     * for "this tile is now one way".  It is the wrong one for the answer to "why is it not moving",
     * which is a train, a dozen stations and the reason each one was refused - an answer somebody
     * asked a question to get, and which was arriving with everything past the first few words off
     * the side of the window with no way to reach it.
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

    public AutonomyBanner()
    {
        setLayout(new BorderLayout());

        // A panel, the way every other panel in this application is one: white, with a single line
        // round it.  It was a pale blue bar with no edge running the width of the window, which reads
        // as something that has been stuck on top of the interface rather than as part of it.  The
        // blue stays where it belongs - on the text, in the colour headings use.
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_LINE, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

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

        // Vertically only.  A message that has to be scrolled sideways to be read is a message that
        // has not been shown, and wrapping is what the editor pane is here for.
        scroller.setHorizontalScrollBarPolicy(
            javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scroller.setVerticalScrollBarPolicy(
            javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        scroller.getVerticalScrollBar().setUnitIncrement(16);

        add(scroller, BorderLayout.CENTER);

        // An offer that goes with the message, for the states where saying what is wrong is only half
        // the answer.  Hidden unless something has been offered, so an ordinary message is unchanged.
        action.setFocusable(false);
        action.setVisible(false);
        action.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        action.setMargin(new java.awt.Insets(0, 10, 0, 10));

        javax.swing.JPanel right = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));

        right.setOpaque(false);
        right.add(action);

        add(right, BorderLayout.EAST);

        // Height is settled by getPreferredSize below rather than fixed here

        clear = new Timer(LINGER_MS, e -> hold(" ", false));
        clear.setRepeats(false);
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

            // And the banner itself goes when it has nothing to offer.
            //
            // The fixed height above is right where this is the only message channel - inside the editor,
            // where a strip that appears and disappears would shove the diagram up and down on every
            // click.  As a header it is wrong: with nothing to say it left an empty band across the top
            // holding the checkbox down, which is a gap the user cannot act on or get rid of.
            setVisible(false);

            revalidate();
            repaint();

            return;
        }

        acting = e -> onPress.run();

        action.setText(buttonText);
        action.addActionListener(acting);
        action.setVisible(true);

        setVisible(true);

        revalidate();
        repaint();
    }

    /**
     * The button this banner offers, for whoever mounts it to size it like its neighbours.
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
     * A floor, not a fixed height.
     *
     * The floor is what stops the strip changing height as messages come and go, which is the whole
     * reason the editor can carry one without the diagram below it moving.  Fixing the height outright
     * was fine while the only thing in here was a line of text, and clipped the bottom off the button
     * the moment one was added - a button is taller than a label, and the strip has insets of its own.
     */
    @Override
    public java.awt.Dimension getPreferredSize()
    {
        java.awt.Dimension natural = super.getPreferredSize();

        // The message's own idea of how tall it needs to be, which the scroll pane will not report
        // - a viewport asks for whatever it was given rather than for what is inside it.
        int wanted = message.getPreferredSize().height + INSET_HEIGHT;

        int height = Math.max(MINIMUM_HEIGHT, Math.min(MAXIMUM_HEIGHT, Math.max(wanted, natural.height)));

        return new java.awt.Dimension(10, height);
    }

    /**
     * Tall enough for a line of text with room around it, whether or not there is any.
     */
    private static final int MINIMUM_HEIGHT = 26;

    /**
     * And no taller than this, whatever is in it.
     *
     * Roughly six lines.  Past that the bar is not a bar any more, it is a panel that has eaten the
     * top of the diagram - so the rest is scrolled to rather than shown.  The number is what the
     * answer to "why is it not moving" usually needs without touching the scrollbar at all.
     */
    private static final int MAXIMUM_HEIGHT = 120;

    /** The border this panel draws round its message, which the height has to allow for. */
    private static final int INSET_HEIGHT = 10;

    private void hold(String text, boolean warning)
    {
        // Wrapped, not cut off.  This used to force nobr and let a long message run off the right
        // edge, on the reasoning that the front of a sentence is the part that carries it - true of
        // a notice, and quite wrong for an answer somebody asked a question to get.  The bar grows to
        // a few lines and scrolls past that, so nothing is lost and nothing is shoved out of shape.
        String body = text == null || text.trim().isEmpty() ? "&nbsp;" : text;

        if (body.startsWith("<html>")) body = body.substring("<html>".length());

        if (body.endsWith("</html>")) body = body.substring(0, body.length() - "</html>".length());

        java.awt.Color ink = warning ? WARNING_TEXT : INFO_TEXT;

        // The font and the colour go into the DOCUMENT: an editor pane showing HTML takes neither
        // from setFont nor from setForeground, so a message set the ordinary way came out in the
        // default serif face in black.
        message.setText("<html><body style=\"font-family:'" + MESSAGE_FONT.getFamily()
            + "'; font-size:" + MESSAGE_FONT.getSize() + "pt; color:rgb("
            + ink.getRed() + "," + ink.getGreen() + "," + ink.getBlue() + "); margin:0\">"
            + body + "</body></html>");

        // Back to the top, so a second answer does not open halfway down where the first was left
        saying = body;

        message.setCaretPosition(0);

        javax.swing.SwingUtilities.invokeLater(() ->
            scroller.getVerticalScrollBar().setValue(0));

        setBackground(warning ? WARNING_BACKGROUND : INFO_BACKGROUND);

        repaint();
    }
}
