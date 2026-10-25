package support;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

/**
 * Finds a component in a window the way a reader of the window would, and drives it.
 *
 * Find by type with a test, by the text the user reads, or by `setName`.  The search goes into a frame's menu bar
 * and into every menu's items, which a plain walk of the children misses.  It runs on the event thread, and
 * returns null when nothing matches, so the test says what was missing.  Find by text, through I18n, wherever the text
 * says what the component is: a field name moves when the code is split, and the words do not.
 *
 * Then drive it as the user would: {@link #press} a button or menu item, {@link #select} a list's item or the tab
 * holding a component.  A press is posted, not waited for, so a dialog it opens can be answered with
 * {@link Answering}.
 *
 * Gathered from the private `find`, `itemCalled` and `buttonIn` of many test classes (FR-136 Phase 6).
 */
public final class Components
{
    private Components()
    {
    }

    /**
     * The first component of this type, depth first, that passes the test.
     *
     * @param <T> the type
     * @param root where to look: a window, a panel, a menu
     * @param type the type wanted
     * @param which the test, or null for the first of the type
     * @return the component, or null
     */
    public static <T extends Component> T find(final Component root, final Class<T> type, final Predicate<? super T> which)
    {
        List<T> found = all(root, type, which);

        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Every component of this type, depth first, that passes the test.
     *
     * @param <T> the type
     * @param root where to look
     * @param type the type wanted
     * @param which the test, or null for every one of the type
     * @return the components, in order
     */
    public static <T extends Component> List<T> all(final Component root, final Class<T> type,
        final Predicate<? super T> which)
    {
        return onTheEventThread(() ->
        {
            List<T> found = new ArrayList<>();

            walk(root, type, which, found);

            return found;
        });
    }

    /**
     * The button, menu item, check box or radio button that says exactly this.
     *
     * @param root where to look
     * @param text its text, as the program writes it (I18n.t, never English typed into a test)
     * @return the component, or null
     */
    public static AbstractButton withText(Component root, final String text)
    {
        return find(root, AbstractButton.class, b -> text.equals(b.getText()));
    }

    /**
     * The component given this name with `setName`.
     *
     * @param <T> the type
     * @param root where to look
     * @param name its name
     * @param type the type wanted
     * @return the component, or null
     */
    public static <T extends Component> T named(Component root, final String name, Class<T> type)
    {
        return find(root, type, c -> name.equals(c.getName()));
    }

    /**
     * Presses a button or menu item, as a click does: posted to the event thread, and returning at once.
     *
     * @param button what to press
     */
    public static void press(final AbstractButton button)
    {
        SwingUtilities.invokeLater(button::doClick);
    }

    /**
     * Selects an item of a combo box, as picking it from the list does, and waits for the selection's own work.
     *
     * @param box the combo box
     * @param item the item
     */
    public static void select(final JComboBox<?> box, final Object item)
    {
        onTheEventThread(() ->
        {
            box.setSelectedItem(item);

            return null;
        });
    }

    /**
     * Brings forward every tab holding this component, as clicking those tabs does.
     *
     * @param inside the component to show
     */
    public static void select(final Component inside)
    {
        onTheEventThread(() ->
        {
            for (Component c = inside; c != null && c.getParent() != null; c = c.getParent())
            {
                if (c.getParent() instanceof JTabbedPane) ((JTabbedPane) c.getParent()).setSelectedComponent(c);
            }

            return null;
        });
    }

    /**
     * The pop-up menu on screen now, as a right-click left it.
     *
     * @return the menu, or null when none is open
     */
    public static JPopupMenu popupShowing()
    {
        return onTheEventThread(() ->
        {
            for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath())
            {
                if (element instanceof JPopupMenu && ((JPopupMenu) element).isVisible()) return (JPopupMenu) element;
            }

            return null;
        });
    }

    /**
     * Lets the event thread finish what is queued on it, and what that queues in turn.
     */
    public static void settle()
    {
        for (int turn = 0; turn < 6; turn++) onTheEventThread(() -> null);
    }

    /**
     * Waits for a condition, asking it every 25 ms.
     *
     * @param condition what to wait for
     * @param millis how long at most
     * @return whether it came true in time
     */
    public static boolean waitFor(BooleanSupplier condition, long millis)
    {
        long until = System.currentTimeMillis() + millis;

        while (!condition.getAsBoolean())
        {
            if (System.currentTimeMillis() >= until) return false;

            try
            {
                Thread.sleep(25);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return true;
    }

    /**
     * Runs this on the event thread, waiting for it, and gives back what it returned.  A failure inside comes out as an
     * unchecked exception.
     *
     * @param <T> what it returns
     * @param work the work
     * @return its result
     */
    public static <T> T onTheEventThread(final Callable<T> work)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            try
            {
                return work.call();
            }
            catch (RuntimeException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        }

        final List<T> result = new ArrayList<>();

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    result.add(work.call());
                }
                catch (RuntimeException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(e);
        }
        catch (java.lang.reflect.InvocationTargetException e)
        {
            throw e.getCause() instanceof RuntimeException ? (RuntimeException) e.getCause()
                : new IllegalStateException(e.getCause());
        }

        return result.get(0);
    }

    /**
     * Depth first: the children and a menu's items.  A frame's menu bar is among its root pane's children.  A menu
     * that fills itself only as it opens holds nothing until it has been opened.
     */
    private static <T extends Component> void walk(Component c, Class<T> type, Predicate<? super T> which, List<T> found)
    {
        if (type.isInstance(c) && (which == null || which.test(type.cast(c)))) found.add(type.cast(c));

        if (c instanceof JMenu)
        {
            for (Component item : ((JMenu) c).getMenuComponents()) walk(item, type, which, found);
        }
        else if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents()) walk(child, type, which, found);
        }
    }
}
