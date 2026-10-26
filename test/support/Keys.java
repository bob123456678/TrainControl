package support;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * Presses keys as the keyboard does: a real KEY_PRESSED, then a KEY_RELEASED, dispatched to a component.
 *
 * Dispatched rather than posted: a posted key goes wherever the operating system says the focus is, usually the
 * terminal that started the run, while a dispatched one reaches the component named and the window's own key map and
 * listeners see it as they see a real key.  To press where the window would send the keyboard, ask
 * {@link #focusOwnerOf} and press on that.  Each press returns once the event thread has done the work the key
 * queued; a key that starts a thread of its own still has to be waited for.
 */
public final class Keys
{
    private Keys()
    {
    }

    /** Presses and releases a key, with no modifier held. */
    public static void press(Component on, int keyCode)
    {
        press(on, keyCode, 0);
    }

    /**
     * Presses and releases a key while modifiers are held (`InputEvent.ALT_DOWN_MASK`, `CTRL_DOWN_MASK`,
     * `SHIFT_DOWN_MASK`, or'd together).
     */
    public static void press(final Component on, final int keyCode, final int modifiers)
    {
        // A letter or a digit types itself; F1 is 0x70, which is also 'p', and types nothing
        final boolean printable = (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
            || (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z);

        final char typed = printable ? (char) keyCode : KeyEvent.CHAR_UNDEFINED;

        Components.onTheEventThread(() ->
        {
            long now = System.currentTimeMillis();

            on.dispatchEvent(new KeyEvent(on, KeyEvent.KEY_PRESSED, now, modifiers, keyCode, typed));
            on.dispatchEvent(new KeyEvent(on, KeyEvent.KEY_RELEASED, now + 1, modifiers, keyCode, typed));

            return null;
        });

        Components.settle();
    }

    /**
     * Where this window sends the keyboard: the component that has it, or will have it once the window is focused (a
     * test window often is not); null when there is nowhere to send it.
     */
    public static Component focusOwnerOf(final Window window)
    {
        return Components.onTheEventThread(window::getMostRecentFocusOwner);
    }
}
