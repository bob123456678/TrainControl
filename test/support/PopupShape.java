package support;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * What a popup looks like on screen: how wide its window is, the text above any list, and the list's lines when they
 * sit in a scroll pane.  Read from a showing JOptionPane, so it says what the user sees.
 */
public final class PopupShape
{
    /** About as wide as a popup may be (Adam approved Details areas and widths so popups fit). */
    public static final int MAX_WIDTH = 640;

    /** The window's width in pixels. */
    public final int width;

    /** The text of the labels and text areas outside any scroll pane, in order, one per line. */
    public final String sentence;

    /** The lines of the list in a scroll pane, or empty when there is none. */
    public final List<String> details;

    private PopupShape(int width, String sentence, List<String> details)
    {
        this.width = width;
        this.sentence = sentence;
        this.details = details;
    }

    /**
     * @param pane a showing option pane
     * @return its shape
     */
    public static PopupShape of(JOptionPane pane)
    {
        Window window = SwingUtilities.getWindowAncestor(pane);

        Object message = pane.getMessage();

        StringBuilder sentence = new StringBuilder();

        List<String> details = new ArrayList<>();

        if (message instanceof Component) read((Component) message, sentence, details);
        else sentence.append(String.valueOf(message));

        return new PopupShape(window == null ? -1 : window.getWidth(), sentence.toString(), details);
    }

    private static void read(Component c, StringBuilder sentence, List<String> details)
    {
        boolean listed = SwingUtilities.getAncestorOfClass(JScrollPane.class, c) != null;

        String label = c instanceof JLabel ? ((JLabel) c).getText() : null;

        String text = label != null ? label.replaceAll("<[^>]*>", " ").trim()
            : c instanceof JTextComponent ? ((JTextComponent) c).getText() : null;

        if (listed && text != null) details.addAll(Arrays.asList(text.split("\n")));
        else if (text != null && !text.isEmpty()) sentence.append(sentence.length() == 0 ? "" : "\n").append(text);

        if (listed && c instanceof JList)
        {
            for (int at = 0; at < ((JList<?>) c).getModel().getSize(); at++)
            {
                details.add(String.valueOf(((JList<?>) c).getModel().getElementAt(at)));
            }
        }

        if (c instanceof Container) for (Component child : ((Container) c).getComponents()) read(child, sentence, details);
    }

    /** Words as the message caps count them. */
    public static int words(String text)
    {
        String plain = text.replaceAll("<[^>]*>", " ").trim();

        return plain.isEmpty() ? 0 : plain.split("\\s+").length;
    }

    @Override
    public String toString()
    {
        return width + " px wide, saying \"" + sentence + "\" over " + details.size() + " listed lines " + details;
    }
}
