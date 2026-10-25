package support;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

/**
 * The mouse, as AWT delivers it: real `MouseEvent`s dispatched to a component's own listeners, at a point on it.
 *
 * Every gesture is posted to the event thread and then settled ({@link Components#settle}), so a modal dialog the
 * gesture opens does not block the test: answer it with {@link Answering}.  The events carry what a listener asks of
 * them - the button, the click count, the modifiers, the popup trigger and the point on screen - in the order AWT sends
 * them: a click is a press, a release and a click; a drag is a press, drags reported to the pressed component, the
 * pointer entering the square it is over, and a release, again to the pressed component, with no click after it.
 *
 * Gathered from the hand-built `new MouseEvent(...)` of several test classes (FR-136 Phase 6).
 */
public final class Mouse
{
    private Mouse()
    {
    }

    /**
     * The pointer arrives over the component, at its middle.
     *
     * @param on the component
     */
    public static void enter(Component on)
    {
        post(on, MouseEvent.MOUSE_ENTERED, middle(on), 0, 0, MouseEvent.NOBUTTON, false);
    }

    /**
     * The pointer leaves the component.
     *
     * @param on the component
     */
    public static void exit(Component on)
    {
        post(on, MouseEvent.MOUSE_EXITED, middle(on), 0, 0, MouseEvent.NOBUTTON, false);
    }

    /**
     * One left click at the middle: the pointer enters, then press, release and click.
     *
     * @param on the component
     */
    public static void click(Component on)
    {
        click(on, MouseEvent.BUTTON1, 1, 0);
    }

    /**
     * A click with any button, any count and any modifiers: the pointer enters, then press, release and click.  A count
     * of 2 is the second click of a double click, sent after a first.
     *
     * @param on the component
     * @param button `MouseEvent.BUTTON1`, `BUTTON2` or `BUTTON3`
     * @param count how many clicks this is: 1, or 2 for a double click
     * @param modifiers extended modifiers held, such as `InputEvent.SHIFT_DOWN_MASK`, or 0
     */
    public static void click(Component on, int button, int count, int modifiers)
    {
        enter(on);

        for (int n = 1; n <= count; n++)
        {
            Point at = middle(on);

            post(on, MouseEvent.MOUSE_PRESSED, at, modifiers | downMask(button), n, button, false);
            post(on, MouseEvent.MOUSE_RELEASED, at, modifiers, n, button, button == MouseEvent.BUTTON3);
            post(on, MouseEvent.MOUSE_CLICKED, at, modifiers, n, button, false);
        }
    }

    /**
     * Presses a button over the component and holds it.
     *
     * @param on the component
     * @param button which button
     */
    public static void press(Component on, int button)
    {
        post(on, MouseEvent.MOUSE_PRESSED, middle(on), downMask(button), 1, button, false);
    }

    /**
     * Lets a button go.  AWT sends a release to the component the press was on, wherever the pointer is.
     *
     * @param pressed the component the press was on
     * @param button which button
     */
    public static void release(Component pressed, int button)
    {
        post(pressed, MouseEvent.MOUSE_RELEASED, middle(pressed), 0, 1, button, false);
    }

    /**
     * Drags with the left button from one component to another: press on the first, move off it, the pointer entering
     * the second, move again, and release - every drag and the release reported to the first, as AWT does, at the
     * second's place in the first's coordinates.  No click follows, because the pointer moved.
     *
     * @param from where the press is
     * @param to where the pointer is let go
     */
    public static void drag(Component from, Component to)
    {
        press(from, MouseEvent.BUTTON1);

        Point there = Components.onTheEventThread(() -> SwingUtilities.convertPoint(to, middle(to), from));

        post(from, MouseEvent.MOUSE_DRAGGED, there, InputEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON, false);

        exit(from);
        enter(to);

        post(from, MouseEvent.MOUSE_DRAGGED, there, InputEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON, false);
        post(from, MouseEvent.MOUSE_RELEASED, there, 0, 1, MouseEvent.BUTTON1, false);
    }

    /**
     * A right click (press, release with the popup trigger, click) and the pop-up menu it opened.
     *
     * @param on the component
     * @param millis how long to wait for the menu
     * @return the menu on screen, or null when none opened in time
     */
    public static JPopupMenu rightClick(Component on, long millis)
    {
        click(on, MouseEvent.BUTTON3, 1, 0);

        Components.waitFor(() -> Components.popupShowing() != null, millis);

        return Components.popupShowing();
    }

    /**
     * Chooses an item of an open menu, as a click on it does: the menu closes, then the item acts.  Posted, so a dialog
     * the item opens can be answered.
     *
     * @param item the item
     */
    public static void choose(final JMenuItem item)
    {
        SwingUtilities.invokeLater(() ->
        {
            MenuSelectionManager.defaultManager().clearSelectedPath();

            item.doClick(0);
        });

        Components.settle();
    }

    /**
     * Closes whatever menu is open, as a click elsewhere does.
     */
    public static void closeMenus()
    {
        Components.onTheEventThread(() ->
        {
            MenuSelectionManager.defaultManager().clearSelectedPath();

            return null;
        });
    }

    private static int downMask(int button)
    {
        switch (button)
        {
            case MouseEvent.BUTTON1: return InputEvent.BUTTON1_DOWN_MASK;
            case MouseEvent.BUTTON2: return InputEvent.BUTTON2_DOWN_MASK;
            case MouseEvent.BUTTON3: return InputEvent.BUTTON3_DOWN_MASK;
            default: return 0;
        }
    }

    private static Point middle(Component on)
    {
        return new Point(Math.max(1, on.getWidth() / 2), Math.max(1, on.getHeight() / 2));
    }

    /** One event, posted to the event thread and dispatched to the component; then the thread is let finish. */
    private static void post(final Component on, final int id, final Point at, final int modifiers, final int count,
        final int button, final boolean popupTrigger)
    {
        SwingUtilities.invokeLater(() ->
        {
            Point screen = new Point(at);

            if (on.isShowing()) SwingUtilities.convertPointToScreen(screen, on);

            on.dispatchEvent(new MouseEvent(on, id, System.currentTimeMillis(), modifiers, at.x, at.y, screen.x,
                screen.y, count, popupTrigger, button));
        });

        Components.settle();
    }
}
