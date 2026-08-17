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

    private static final Color INFO_BACKGROUND = new Color(232, 240, 254);
    private static final Color INFO_TEXT = new Color(0, 0, 155);

    private static final Color WARNING_BACKGROUND = new Color(255, 244, 214);
    private static final Color WARNING_TEXT = new Color(120, 80, 0);

    private final JLabel message = new JLabel(" ");

    private final Timer clear;

    public AutonomyBanner()
    {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        setBackground(INFO_BACKGROUND);

        message.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        message.setForeground(INFO_TEXT);

        add(message, BorderLayout.CENTER);

        // Fixed height whether or not there is anything to say, so the diagram below never moves.
        setPreferredSize(new java.awt.Dimension(10, 26));

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

        clear.restart();
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

    private void hold(String text, boolean warning)
    {
        // One line, always.  A JLabel given HTML wraps to the width it is offered, and this strip is
        // one line tall - so a long message pushed the bar open and shoved the diagram down, or was
        // clipped mid-sentence.  nowrap keeps it on one line and lets the end run off, which is the
        // better failure: the front of a sentence is the part that carries it.
        String shown = text == null || text.trim().isEmpty() ? " " : text;

        if (shown.startsWith("<html>"))
        {
            shown = "<html><nobr>" + shown.substring("<html>".length());
        }

        message.setText(shown);
        message.setForeground(warning ? WARNING_TEXT : INFO_TEXT);

        setBackground(warning ? WARNING_BACKGROUND : INFO_BACKGROUND);

        repaint();
    }
}
