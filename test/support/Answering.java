package support;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Answers the program's real dialogs as the user would: it presses their buttons, types into their fields and picks a
 * file in their file choosers.  It also keeps what each question said.
 *
 * A thread watches the screen while it is open.  Every new `JOptionPane` gets the {@link Answer} the rule gives for its
 * message, and every new `JFileChooser` gets the file given to {@link #choosing(File)}, or Cancel.  Answers are
 * posted to the event thread, so a dialog shown inside a click's own handler is answered too.  Close it in a finally,
 * or with try-with-resources:
 *
 *     try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
 *     {
 *         SwingUtilities.invokeLater(item::doClick);
 *         assertTrue(answering.awaitAsked(1, 10000));
 *     }
 *
 * Gathered from the private answerers of about ten test classes (FR-136 Phase 6).
 */
public final class Answering implements AutoCloseable
{
    /** How often the screen is looked at, in milliseconds. */
    private static final long LOOK_EVERY_MS = 50;

    /**
     * One answer to one question: the button to press and, for a question that asks for text, what to type first.
     */
    public static final class Answer
    {
        final String button;
        final int index;
        final String typed;

        private Answer(String button, int index, String typed)
        {
            this.button = button;
            this.index = index;
            this.typed = typed;
        }
    }

    /**
     * @param buttonText the button's text, as the program wrote it (through I18n)
     * @return an answer that presses that button
     */
    public static Answer press(String buttonText)
    {
        return new Answer(buttonText, -1, null);
    }

    /**
     * @param index the button's place, counting from 0 in the order the dialog shows them
     * @return an answer that presses that button
     */
    public static Answer pressAt(int index)
    {
        return new Answer(null, index, null);
    }

    /**
     * Types into the question's text field, or picks the item that reads so in its list, then presses its first button
     * (OK).
     *
     * @param text what to type or pick
     * @return the answer
     */
    public static Answer type(String text)
    {
        return new Answer(null, 0, text);
    }

    private final List<String> asked = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean going = new AtomicBoolean(true);
    private volatile Thread watching;
    private volatile File toChoose;

    private Answering()
    {
    }

    /**
     * Starts answering.
     *
     * @param rule given each question's message, the answer to give; null presses the first button
     * @return the answerer, to be closed
     */
    public static Answering start(final Function<String, Answer> rule)
    {
        final Answering answering = new Answering();

        final Set<Object> handled = Collections.newSetFromMap(new IdentityHashMap<>());

        answering.watching = new Thread(() ->
        {
            while (answering.going.get())
            {
                for (Window window : Window.getWindows())
                {
                    if (!(window instanceof JDialog) || !window.isShowing()) continue;

                    Container content = ((JDialog) window).getContentPane();

                    JOptionPane pane = first(content, JOptionPane.class);

                    if (pane != null && handled.add(pane))
                    {
                        String message = textOf(pane.getMessage());

                        answering.asked.add(message);

                        answer(pane, rule.apply(message), answering.asked);
                    }

                    JFileChooser chooser = first(content, JFileChooser.class);

                    if (chooser != null && handled.add(chooser)) choose(chooser, answering.toChoose);
                }

                try
                {
                    Thread.sleep(LOOK_EVERY_MS);
                }
                catch (InterruptedException stop)
                {
                    return;
                }
            }
        }, "answering");

        answering.watching.setDaemon(true);
        answering.watching.start();

        return answering;
    }

    /**
     * Picks this file in every file chooser from now on, and approves it.  Without it, a chooser is canceled.
     *
     * @param file the file or folder to pick; null to cancel again
     * @return this answerer
     */
    public Answering choosing(File file)
    {
        this.toChoose = file;

        return this;
    }

    /**
     * @return every question's message so far, in the order asked
     */
    public List<String> asked()
    {
        synchronized (asked)
        {
            return new ArrayList<>(asked);
        }
    }

    /**
     * Waits until this many questions have been asked.
     *
     * @param count how many
     * @param millis how long to wait at most
     * @return whether that many were asked in time
     */
    public boolean awaitAsked(int count, long millis)
    {
        long until = System.currentTimeMillis() + millis;

        while (asked.size() < count && System.currentTimeMillis() < until)
        {
            try
            {
                Thread.sleep(LOOK_EVERY_MS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return asked.size() >= count;
    }

    /**
     * Stops answering.  A dialog still open afterwards stays open.
     */
    @Override
    public void close()
    {
        going.set(false);

        try
        {
            watching.join(1000);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /** What a question says: its message, or the text of the labels and fields it is built from. */
    private static String textOf(Object message)
    {
        if (!(message instanceof Component)) return String.valueOf(message);

        StringBuilder said = new StringBuilder();

        for (Component c : all((Component) message))
        {
            String text = c instanceof javax.swing.JLabel ? ((javax.swing.JLabel) c).getText()
                : c instanceof JTextComponent ? ((JTextComponent) c).getText() : null;

            if (text != null && !text.isEmpty()) said.append(said.length() == 0 ? "" : "\n").append(text);
        }

        return said.toString();
    }

    /**
     * Gives the answer on the event thread.  An answer the dialog cannot take (no such button, nowhere to type) is
     * recorded in what was asked, starting "UNANSWERED: ", and the dialog is closed as its close box would.
     */
    private static void answer(final JOptionPane pane, final Answer answer, final List<String> asked)
    {
        SwingUtilities.invokeLater(() ->
        {
            try
            {
                if (answer != null && answer.typed != null) typeInto(pane, answer.typed);

                pressed(pane, answer).doClick();
            }
            catch (RuntimeException cannot)
            {
                asked.add("UNANSWERED: " + cannot.getMessage());

                pane.setValue(JOptionPane.CLOSED_OPTION);
            }
        });
    }

    /** The button an answer presses. */
    private static AbstractButton pressed(JOptionPane pane, Answer answer)
    {
        List<JButton> buttons = buttonsOf(pane);

        if (answer != null && answer.button != null)
        {
            for (JButton b : buttons)
            {
                if (answer.button.equals(b.getText())) return b;
            }

            throw new IllegalStateException("no button says \"" + answer.button + "\" on the question \""
                + textOf(pane.getMessage()) + "\"");
        }

        int at = answer == null ? 0 : answer.index;

        if (at >= buttons.size())
        {
            throw new IllegalStateException("the question \"" + textOf(pane.getMessage()) + "\" has " + buttons.size()
                + " buttons, not " + (at + 1));
        }

        return buttons.get(at);
    }

    /** The question's input, or the first text field in its message, given the text. */
    private static void typeInto(JOptionPane pane, String text)
    {
        JComboBox<?> box = first(pane, JComboBox.class);
        JList<?> list = first(pane, JList.class);

        if (pane.getWantsInput() && pane.getSelectionValues() != null && (box != null || list != null))
        {
            for (Object value : pane.getSelectionValues())
            {
                if (!text.equals(String.valueOf(value))) continue;

                if (box != null) box.setSelectedItem(value);
                else list.setSelectedValue(value, true);

                return;
            }

            throw new IllegalStateException("the question offers no \"" + text + "\"");
        }

        JTextComponent field = first(pane, JTextComponent.class);

        if (field == null) throw new IllegalStateException("the question has nowhere to type");

        field.setText(text);
    }

    /** The dialog's own buttons, in order: not those in its message, nor a list's or a combo box's arrows. */
    private static List<JButton> buttonsOf(JOptionPane pane)
    {
        Object message = pane.getMessage();

        List<JButton> buttons = new ArrayList<>();

        for (Component c : all(pane))
        {
            if (!(c instanceof JButton)) continue;

            if (message instanceof Component && SwingUtilities.isDescendingFrom(c, (Component) message)) continue;

            if (SwingUtilities.getAncestorOfClass(JComboBox.class, c) != null
                || SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, c) != null) continue;

            buttons.add((JButton) c);
        }

        return buttons;
    }

    private static void choose(final JFileChooser chooser, final File file)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (file == null)
            {
                chooser.cancelSelection();

                return;
            }

            chooser.setSelectedFile(file);
            chooser.approveSelection();
        });
    }

    private static <T> T first(Component root, Class<T> type)
    {
        for (Component c : all(root))
        {
            if (type.isInstance(c)) return type.cast(c);
        }

        return null;
    }

    /** The component and everything inside it, depth first. */
    private static List<Component> all(Component root)
    {
        List<Component> found = new ArrayList<>();

        found.add(root);

        if (root instanceof Container)
        {
            for (Component child : ((Container) root).getComponents()) found.addAll(all(child));
        }

        return found;
    }
}
