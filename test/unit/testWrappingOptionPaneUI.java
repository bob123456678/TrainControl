package unit;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.traincontrol.gui.widgets.WrappingOptionPaneUI;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import static support.Components.onTheEventThread;

/**
 * `WrappingOptionPaneUI` on its own (FR-254): where `wrap` breaks a popup's text, measured in a monospaced font so a
 * width is a count of letters, and the cap `createUI` wraps at.  `ui.testEveryLanguageFitsThePopups` is its
 * integration test, through the look and feel the application installs.
 */
public class testWrappingOptionPaneUI
{
    private static final FontMetrics MONO = new JLabel().getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, 12));

    /** The width of this many letters. */
    private static int letters(int n)
    {
        assertEquals(MONO.stringWidth("abcdefghij"), 10 * MONO.charWidth('m'), "precondition: the font is not monospaced");
        return n * MONO.charWidth('m');
    }

    /**
     * Text that fits is kept as given: a short line, a line exactly as wide as the cap, and the text's own line breaks,
     * a trailing one included, a Windows break read as one.
     *
     * MUTATION: in `wrap`, split without the `-1`, and the trailing line break is lost.
     */
    @Test
    public void testTextThatFitsIsKeptAsGiven()
    {
        assertEquals(WrappingOptionPaneUI.wrap("Saved.", MONO, letters(40)), "Saved.");
        assertEquals(WrappingOptionPaneUI.wrap("abcdefghij", MONO, letters(10)), "abcdefghij",
            "a line exactly as wide as the cap was broken");
        assertEquals(WrappingOptionPaneUI.wrap("First line\r\nSecond line\n", MONO, letters(20)),
            "First line\nSecond line\n", "the text's own line breaks were not kept");
    }

    /**
     * No line is wider than the cap, a word longer than the cap included, and every letter is kept.
     *
     * MUTATION: in `wrap`, measure the inner loop against `width + width / 4`, and lines pass the cap.
     */
    @Test
    public void testNoLineIsWiderThanTheCap()
    {
        String text = "The backup could not be copied because another program holds it open, so close that program"
            + " first. Pneumonoultramicroscopicsilicovolcanoconiosis";
        String wrapped = WrappingOptionPaneUI.wrap(text, MONO, letters(30));

        for (String line : wrapped.split("\n"))
        {
            assertTrue(MONO.stringWidth(line) <= letters(30), "\"" + line + "\" is wider than 30 letters: " + wrapped);
        }

        assertEquals(wrapped.replaceAll("\\s", ""), text.replaceAll("\\s", ""), "letters were lost: " + wrapped);
        assertTrue(wrapped.split("\n").length > 3, "control: the text was not broken: " + wrapped);
    }

    /**
     * A line breaks after the last space that fits, and the space is dropped, so no line ends or begins with one.
     *
     * MUTATION: in `wrap`, leave out `line.lastIndexOf(' ', end)` from the cut, and a word is cut in two.
     */
    @Test
    public void testALineBreaksAfterASpace()
    {
        assertEquals(WrappingOptionPaneUI.wrap("alpha beta gamma delta", MONO, letters(14)), "alpha beta\ngamma delta");
    }

    /**
     * A path with no spaces breaks after a separator, either kind, the separator kept at the end of its line.
     *
     * MUTATION: in `wrap`, drop the `+ 1` after the separators' `lastIndexOf`, and a line begins with the separator.
     */
    @Test
    public void testAPathBreaksAfterASeparator()
    {
        for (String path : new String[] {"C:\\Users\\someone\\Documents\\TrainControl\\backups\\layout.zip",
            "/home/someone/Documents/TrainControl/backups/layout.zip"})
        {
            String[] lines = WrappingOptionPaneUI.wrap(path, MONO, letters(20)).split("\n");

            assertTrue(lines.length > 2, "control: the path was not broken: " + String.join(" | ", lines));
            assertEquals(String.join("", lines), path, "the path's letters changed");

            for (int i = 0; i < lines.length - 1; i++)
            {
                assertTrue(lines[i].endsWith("\\") || lines[i].endsWith("/"), "line " + (i + 1) + " does not end with a"
                    + " separator: " + String.join(" | ", lines));
            }
        }
    }

    /**
     * A popup's cap grows with the interface scale: a long message wraps no wider than the cap at 100%, and at 150%
     * runs past it, up to one and a half times it.
     *
     * MUTATION: in `createUI`, wrap at `CAP` rather than `UIScale.scale(CAP)`, and at 150% the lines stop at 440 px.
     */
    @Test
    public void testTheCapGrowsWithTheScale() throws Exception
    {
        String text = "The layout was saved, but the copy in the backup folder could not be written because the disk"
            + " is full or the folder is read only, so free some room or choose another folder in the settings and"
            + " save the layout again before you close the program for the day.";
        int[] at100 = widestAndLines(text, "1.0"), at150 = widestAndLines(text, "1.5");
        int cap = WrappingOptionPaneUI.CAP;

        assertTrue(at100[1] > 1, "control: at 100% the message is one line, so nothing was wrapped");
        assertTrue(at100[0] <= cap, "at 100% a line is " + at100[0] + " px, over the " + cap + " px cap");
        assertTrue(at150[0] > cap && at150[0] <= cap * 3 / 2, "at 150% the widest line is " + at150[0]
            + " px, not between the " + cap + " px cap and one and a half times it");
    }

    /** The widest line of text's popup at this interface scale and how many lines it has; the look is put back. */
    private static int[] widestAndLines(String text, String scale) throws Exception
    {
        String was = System.getProperty("flatlaf.uiScale");
        LookAndFeel look = UIManager.getLookAndFeel();

        try
        {
            return onTheEventThread(() ->
            {
                System.setProperty("flatlaf.uiScale", scale);
                com.formdev.flatlaf.FlatLightLaf.setup();
                JOptionPane pane = new JOptionPane(text, JOptionPane.PLAIN_MESSAGE);
                pane.setUI((javax.swing.plaf.OptionPaneUI) WrappingOptionPaneUI.createUI(pane));
                Font font = UIManager.getFont("OptionPane.messageFont");
                FontMetrics fm = pane.getFontMetrics(font != null ? font : UIManager.getFont("Label.font"));
                List<String> lines = new ArrayList<>();
                textOf(pane, lines);
                int widest = 0;
                for (String line : lines) widest = Math.max(widest, fm.stringWidth(line));
                return new int[] {widest, lines.size()};
            });
        }
        finally
        {
            onTheEventThread(() ->
            {
                if (was == null) System.clearProperty("flatlaf.uiScale");
                else System.setProperty("flatlaf.uiScale", was);
                UIManager.setLookAndFeel(look);
                return null;
            });
        }
    }

    private static void textOf(Container c, List<String> lines)
    {
        for (Component child : c.getComponents())
        {
            if (child instanceof JLabel && ((JLabel) child).getText() != null && !((JLabel) child).getText().isEmpty())
            {
                lines.add(((JLabel) child).getText());
            }
            else if (child instanceof Container) textOf((Container) child, lines);
        }
    }
}
