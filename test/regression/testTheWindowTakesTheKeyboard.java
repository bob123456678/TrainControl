package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * A keystroke right after start-up lands on the keyboard and changes the active key (OB-168, OB-170).
 *
 * Adam, OB-168: **"ensure the UI is focused once the window is rendered so that keystrokes are
 * registered on the main traincontrol window (locomotive letters, etc.). this feels like a
 * regression."** Then OB-170, against a build that already carried the fix: **"when I start the app,
 * the main window is not in focus and keystrokes don't go to it. could it be related to the startup
 * loading notice?"**
 *
 * Adam asked for this test in those words - *"a keystroke right after startup changes the active
 * key"* - and that is the whole claim. It is worth being precise about what carries it, because the
 * obvious version of this test cannot fail.
 *
 * **The frame is not the thing that holds the keyboard.** `setFocusable(false)` is called on it when
 * the form is built, so it can never be the focus owner itself; the letter keys are read by
 * KeyListeners on `KeyboardTab` and on the locomotive panel, and one of those has to hold the focus
 * before a letter reaches either. `takeTheKeyboard` therefore asks the tabbed pane for it.
 *
 * **Why the tests below ask `getMostRecentFocusOwner` and not `getFocusOwner`.** A test JVM's window
 * is very often not the OS's focused window - the terminal that launched it usually still is - and
 * `getFocusOwner` answers null for every window that is not. Asking it would make these tests
 * environmental rather than deterministic. `Window.getMostRecentFocusOwner` is the honest question:
 * it is *the child this window will hand the keyboard to the moment it becomes focused*, which is
 * exactly what "the keystroke goes to the keyboard" means, and `requestFocusInWindow` sets it on a
 * showing window whether or not that window is focused (`Component.requestFocusHelper` reaches
 * `KeyboardFocusManager.setMostRecentFocusOwner` for any request that clears
 * `isRequestFocusAccepted`, which does not ask about the focused window).
 *
 * So each test below presses its letter on the component the window has arranged to receive it, and
 * a build that arranged for the wrong component fails at that press rather than at an assertion
 * about focus plumbing.
 */
public class testTheWindowTakesTheKeyboard
{
    /**
     * An application that has started: sandbox, model and window, in the order start-up uses them.
     */
    private static final class Started
    {
        support.LayoutSandbox sandbox;

        org.traincontrol.marklin.MarklinControlStation model;

        org.traincontrol.gui.TrainControlUI ui;

        void close() throws Exception
        {
            if (ui != null)
            {
                final org.traincontrol.gui.TrainControlUI window = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Start-up, in the order `MarklinControlStation.init` performs it: build the window, hand it the
     * model, then `display()`.
     *
     * `display()` is the point of all this and is not replaced by anything cheaper - it is the method
     * that calls `takeTheKeyboard`, and a test that called `takeTheKeyboard` directly would be
     * testing a method rather than a start-up.
     *
     * BEFORE the model, not after it (OB-111): `init` reads the machine-global layout preference and
     * would otherwise open Adam's real railway.
     */
    private static Started start() throws Exception
    {
        Started up = new Started();

        up.sandbox = support.LayoutSandbox.open();

        up.model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

        final org.traincontrol.marklin.MarklinControlStation model = up.model;
        final org.traincontrol.gui.TrainControlUI[] made = new org.traincontrol.gui.TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                made[0] = new org.traincontrol.gui.TrainControlUI();
                made[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));
                made[0].display();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        up.ui = made[0];

        settle();

        // AND THE RAISE HAS TO FINISH (OB-170, fifth pass).
        //
        // `takeTheKeyboard` posts the raise, the raise hands its always-on-top flag back four hundred
        // milliseconds later, and only THEN is the keyboard asked for - because the flag's restore is
        // a native window-style change that resets which component holds the focus, which is what
        // undid the fourth pass.  A test that drained the event queue and read the answer would be
        // reading it before start-up had given one.
        //
        // Bounded, and it does not assert: a machine where the keyboard never arrives is what the
        // tests below are for, and they say so in their own words.
        for (int waited = 0; waited < 4000; waited += 100)
        {
            java.awt.Component willGetIt = up.ui.getMostRecentFocusOwner();

            Object tab = field(up.ui, "KeyboardTab");

            if (willGetIt != null && isTheKeyboard(willGetIt, (java.awt.Component) tab)) break;

            Thread.sleep(100);

            settle();
        }

        return up;
    }

    /**
     * Lets everything the last gesture POSTED actually run.
     *
     * `takeTheKeyboard` and `displayCurrentButtonLoc` both work through `invokeLater` - the first
     * because focus asked for during the event that made the window visible is asked for too early,
     * the second because it repaints - so a test that read its answer straight after the gesture
     * would read it before the gesture had happened.
     *
     * Several passes rather than one, because a posted task may post another.
     */
    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 5; pass++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }

    /**
     * A private field of the window, by name.
     */
    private static Object field(Object of, String name) throws Exception
    {
        java.lang.reflect.Field f =
            org.traincontrol.gui.TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(of);
    }

    /**
     * The letter-to-button map the window actually uses, read from it rather than rebuilt here.
     */
    @SuppressWarnings("unchecked")
    private static java.util.Map<Integer, javax.swing.JButton> mapping(
        org.traincontrol.gui.TrainControlUI ui) throws Exception
    {
        return (java.util.Map<Integer, javax.swing.JButton>) field(ui, "buttonMapping");
    }

    /**
     * A mapped letter whose button is not the one already active, so that "the active key changed"
     * is a question the keystroke can answer.
     */
    private static int aLetterOtherThan(org.traincontrol.gui.TrainControlUI ui,
        javax.swing.JButton taken) throws Exception
    {
        java.util.Map<Integer, javax.swing.JButton> map = mapping(ui);

        for (int key = java.awt.event.KeyEvent.VK_A; key <= java.awt.event.KeyEvent.VK_Z; key++)
        {
            javax.swing.JButton button = map.get(key);

            if (button != null && button != taken) return key;
        }

        fail("no letter on the keyboard maps to a button other than the active one, so pressing one "
            + "could not change anything - the fixture is wrong, not the window");

        return -1;
    }

    /**
     * Presses a letter on whatever component is holding the keyboard.
     *
     * Dispatched to that component rather than posted to the queue, because the queue would route it
     * by the OS's idea of the focus owner - which in a test JVM is usually a terminal window. The
     * component under test is the one the WINDOW says will receive it, and the tests below assert
     * separately that that component is the keyboard.
     */
    private static void press(java.awt.Component on, int keyCode) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> on.dispatchEvent(
            new java.awt.event.KeyEvent(on, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, keyCode, (char) keyCode)));

        settle();
    }

    /**
     * Whether this is the keyboard, or something inside it.
     */
    private static boolean isTheKeyboard(java.awt.Component what, java.awt.Component keyboard)
    {
        return what == keyboard
            || (what != null && javax.swing.SwingUtilities.isDescendingFrom(what, (java.awt.Container) keyboard));
    }

    /**
     * Something else in this window that can hold the keyboard and is NOT a place anybody types.
     *
     * Found rather than named, because naming one would tie this test to a particular control and the
     * claim is about any of them: whatever the focus landed on while the window was starting, if it
     * consumes no letters then the keyboard has to get it back.
     *
     * Text components are excluded here and looked for separately below, because the guard's rule is
     * exactly that distinction. Running both tests against the same component would make one of them
     * a statement about the other's fixture.
     */
    private static java.awt.Component somethingThatDoesNotType(java.awt.Container in,
        java.awt.Component keyboard)
    {
        for (java.awt.Component child : in.getComponents())
        {
            if (isTheKeyboard(child, keyboard)) continue;

            if (child.isFocusable() && child.isShowing() && child.isEnabled()
                && !(child instanceof javax.swing.text.JTextComponent)
                && !(child instanceof java.awt.Container && ((java.awt.Container) child).getComponentCount() > 0))
            {
                return child;
            }

            if (child instanceof java.awt.Container)
            {
                java.awt.Component found =
                    somethingThatDoesNotType((java.awt.Container) child, keyboard);

                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * A place in this window somebody could be typing in.
     *
     * Two of them exist - the log area and the JSON pane - and both can be clicked into, which is what
     * makes the guard worth having. Found rather than named for the same reason as above.
     */
    private static javax.swing.text.JTextComponent somewhereToType(java.awt.Container in)
    {
        for (java.awt.Component child : in.getComponents())
        {
            if (child instanceof javax.swing.text.JTextComponent
                && child.isFocusable() && child.isEnabled())
            {
                return (javax.swing.text.JTextComponent) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.text.JTextComponent found =
                    somewhereToType((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * Opens whatever tabs are hiding a component, so that it can be clicked into.
     *
     * Both of this window's text areas sit on tabs that are not the selected one, and a JTabbedPane
     * makes the children of every unselected tab invisible - so a focus request on one is refused
     * before it starts (`Component.isRequestFocusAccepted` checks `isVisible`). Without this the
     * caret test arranged nothing and then asserted about it.
     *
     * @param on the component to bring into view
     */
    private static void openTheTabsAbove(java.awt.Component on) throws Exception
    {
        final java.awt.Component target = on;

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            java.awt.Component child = target;

            for (java.awt.Container parent = target.getParent(); parent != null;
                 child = parent, parent = parent.getParent())
            {
                if (parent instanceof javax.swing.JTabbedPane)
                {
                    ((javax.swing.JTabbedPane) parent).setSelectedComponent(child);
                }
            }
        });

        settle();
    }

    /**
     * The thing Adam asked for: start the application, press a letter, watch the active key move.
     *
     * MUTATION this catches: take `takeTheKeyboard()` off the end of `display()`. The window then
     * hands the keyboard to whatever its traversal policy nominates - not the tabbed pane - and the
     * letter reaches a component with no KeyListener on it, so `currentButton` does not move.
     */
    @Test(timeOut = 300000)
    public void testAKeystrokeRightAfterStartupChangesTheActiveKey() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the keyboard is on a window");
        }

        Started up = start();

        try
        {
            java.awt.Component keyboard = (java.awt.Component) field(up.ui, "KeyboardTab");

            assertNotNull(keyboard, "the window has no keyboard tab at all");

            java.awt.Component willGetIt = up.ui.getMostRecentFocusOwner();

            assertNotNull(willGetIt,
                "the window will hand the keyboard to nothing at all when it is focused, so no "
                + "keystroke after start-up can reach anything - which is OB-168 exactly");

            assertTrue(isTheKeyboard(willGetIt, keyboard),
                "after start-up the window will hand the keyboard to " + willGetIt.getClass().getName()
                + " rather than to the tabbed pane that reads the locomotive letters - so the first "
                + "letter Adam presses does nothing, which is what he reported twice");

            javax.swing.JButton before = (javax.swing.JButton) field(up.ui, "currentButton");

            int letter = aLetterOtherThan(up.ui, before);

            press(willGetIt, letter);

            javax.swing.JButton now = (javax.swing.JButton) field(up.ui, "currentButton");

            assertSame(now, mapping(up.ui).get(letter),
                "pressing " + java.awt.event.KeyEvent.getKeyText(letter) + " right after start-up left "
                + "the active key on " + (now == null ? "nothing" : now.getText())
                + " - the keystroke did not reach the keyboard");

            assertNotSame(now, before,
                "the active key did not move, so the keystroke changed nothing");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * OB-170 proper: the keyboard comes back when the window does.
     *
     * **Why OB-168's fix was not enough, and why this is the shape of the test.** That fix asked once,
     * from the end of `display()`. `requestFocusInWindow` returns false and does nothing while the
     * window is not the focused window, and `toFront()` on Windows does not reliably make a starting
     * process's window one - so on a machine where anything else held the focus during start-up the
     * request was made at a moment it could not be granted, and then never made again. Adam asked
     * whether the start-up notice was involved: not through ordering, since `StartupSplash` closes
     * synchronously on the event thread before `display()` is posted, but it is that same shape - an
     * always-on-top window that held the focus and then vanished leaves the focus wherever the window
     * manager likes.
     *
     * This reproduces that without an OS: something else in the window is given the keyboard after
     * start-up, and then the window gains focus. A build that asks only once leaves the keystroke
     * with whatever took it.
     *
     * MUTATION this catches: remove the `addWindowFocusListener` block from `takeTheKeyboard` and
     * keep the single posted request. The precondition below still holds, and the press then leaves
     * `currentButton` where it was.
     */
    @Test(timeOut = 300000)
    public void testTheKeyboardComesBackWhenTheWindowGainsFocus() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the keyboard is on a window");
        }

        Started up = start();

        try
        {
            java.awt.Component keyboard = (java.awt.Component) field(up.ui, "KeyboardTab");

            final java.awt.Component elsewhere =
                somethingThatDoesNotType(up.ui.getContentPane(), keyboard);

            assertNotNull(elsewhere,
                "nothing else in this window can hold the keyboard without being a place to type, so "
                + "the state OB-170 describes cannot be set up - the fixture is wrong, not the window");

            javax.swing.SwingUtilities.invokeAndWait(() -> elsewhere.requestFocusInWindow());

            settle();

            assertSame(up.ui.getMostRecentFocusOwner(), elsewhere,
                "precondition: the keyboard was supposed to be taken away from the tabbed pane, and "
                + "it was not - so what follows would pass whatever the window did");

            javax.swing.JButton before = (javax.swing.JButton) field(up.ui, "currentButton");

            // Whether the activation was DELIVERED, which is a separate question from what it did.
            //
            // Without this the test below is silently vacuous whenever the event is dropped, and it
            // was: see the comment on the two dispatches that follow.
            final boolean[] delivered = { false };

            java.awt.event.WindowFocusListener watcher = new java.awt.event.WindowAdapter()
            {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e)
                {
                    delivered[0] = true;
                }
            };

            up.ui.addWindowFocusListener(watcher);

            // AWAY, AND THEN BACK.  Both halves are needed, and the first one is not decoration.
            //
            // `DefaultKeyboardFocusManager.dispatchEvent` drops a WINDOW_GAINED_FOCUS aimed at the
            // window that is already the focused window - "if (newFocusedWindow == oldFocusedWindow)
            // break" - before any listener is reached.  A test JVM's window often does hold the focus
            // for real, and then a lone synthetic activation is a no-op that every assertion after it
            // would be about nothing.
            //
            // This is also the gesture the test is named for: the operator alt-tabs away and comes
            // back, or a splash takes the focus during start-up and gives it up again.
            javax.swing.SwingUtilities.invokeAndWait(() -> up.ui.dispatchEvent(
                new java.awt.event.WindowEvent(up.ui,
                    java.awt.event.WindowEvent.WINDOW_LOST_FOCUS)));

            settle();

            javax.swing.SwingUtilities.invokeAndWait(() -> up.ui.dispatchEvent(
                new java.awt.event.WindowEvent(up.ui,
                    java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS)));

            settle();

            up.ui.removeWindowFocusListener(watcher);

            assertTrue(delivered[0],
                "the window never received the activation at all, so nothing below is a statement "
                + "about what the window does when it comes to the front");

            java.awt.Component willGetIt = up.ui.getMostRecentFocusOwner();

            assertTrue(isTheKeyboard(willGetIt, keyboard),
                "the window came to the front and left the keyboard with "
                + (willGetIt == null ? "nothing" : willGetIt.getClass().getName())
                + " - so the letters still do nothing, which is OB-170: the request made once during "
                + "start-up was dropped and never made again");

            int letter = aLetterOtherThan(up.ui, before);

            press(willGetIt, letter);

            assertSame(field(up.ui, "currentButton"), mapping(up.ui).get(letter),
                "pressing " + java.awt.event.KeyEvent.getKeyText(letter)
                + " after the window came to the front did not change the active key");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * Coming to the foreground gives the always-on-top setting back (OB-170, third pass).
     *
     * **The fix works by breaking a rule, so this is the test that it puts the rule back.**  Windows
     * refuses a foreground change asked for by a process that is not already in the foreground, and
     * the one exception is a topmost window - so the window is made topmost, raised, and set back to
     * whatever the operator chose.  Leaving it topmost would be a worse fault than the one being
     * fixed: the application would sit above everything else for the rest of the session, and the menu
     * item that controls it would disagree with the window.
     *
     * Both directions, because a one-directional test passes on `setAlwaysOnTop(false)` unconditionally
     * - which is exactly the wrong answer for an operator who has the setting turned on.
     *
     * **The restore is on a timer, and that is the fix rather than an implementation detail.**  Handing
     * the flag back in the same breath as the raise - which a `finally` does - was the fourth-pass
     * defect: `toFront()` posts a raise, and by the time the window manager acted on it the window was
     * an ordinary one again.  So this waits for the flag to come back rather than reading it
     * immediately, and the wait is bounded so a restore that never happens fails rather than hangs.
     *
     * MUTATION this catches: dropping the restore, or replacing it with a bare
     * `setAlwaysOnTop(false)`.
     */
    @Test(timeOut = 300000)
    public void testComingToTheForegroundGivesTheOnTopSettingBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the window is a window");
        }

        Started up = start();

        boolean onTopWas = org.traincontrol.gui.TrainControlUI.getPrefs().getBoolean(
            org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF, false);

        try
        {
            java.lang.reflect.Method raise =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod("comeToTheForeground");
            raise.setAccessible(true);

            for (final boolean onTop : new boolean[] { false, true })
            {
                // THE PREFERENCE, not the window's current flag (OB-170, seventh pass).
                //
                // The raise restores what the OPERATOR chose, because the window arrives at it still
                // topmost on purpose - `display()` no longer takes the flag off, and reading it back
                // would leave the application above everything else for ever.  So the setting under
                // test is the stored one, and a test that only moved the window's flag would be
                // asserting against whatever happened to be in the preferences store.
                org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                    org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF, onTop);

                javax.swing.SwingUtilities.invokeAndWait(() -> up.ui.setAlwaysOnTop(!onTop));

                settle();

                assertEquals(up.ui.isAlwaysOnTop(), !onTop,
                    "precondition: the window would not take the always-on-top setting at all, so "
                    + "the restore below could pass without restoring anything");

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        raise.invoke(up.ui);
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new RuntimeException(e);
                    }
                });

                // Bounded, because the restore is posted rather than immediate - up to two attempts
                // at FOREGROUND_SETTLE_MS each, and then some slack for a busy event thread.
                for (int waited = 0; waited < 4000 && up.ui.isAlwaysOnTop() != onTop; waited += 100)
                {
                    Thread.sleep(100);

                    settle();
                }

                assertEquals(up.ui.isAlwaysOnTop(), onTop,
                    "coming to the front left always-on-top at " + up.ui.isAlwaysOnTop() + " when the "
                    + "operator's setting is " + onTop + " - the flag is how the raise gets past "
                    + "Windows' foreground rule, and it has to be handed back afterwards");
            }
        }
        finally
        {
            org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF, onTopWas);

            up.close();
        }
    }

    /**
     * And an iconified window is un-iconified, without un-maximising a maximised one.
     *
     * A window in the iconified state cannot be activated at all, so the raise would be a no-op on the
     * one occasion somebody most needs it - the application already running, minimised, and asked for
     * again.  The maximised bits live in the same field, which is why the ICONIFIED bit is cleared
     * rather than the state replaced.
     *
     * MUTATION this catches: removing the un-iconify entirely.  The maximise half would catch
     * `setExtendedState(java.awt.Frame.NORMAL)`, and on THIS window it cannot run at all - the frame
     * is built `setResizable(false)` and refuses the maximised bits - so it is guarded by what the
     * fixture actually managed to arrange rather than left to pass on a state nobody set.
     */
    @Test(timeOut = 300000)
    public void testComingToTheForegroundUnMinimisesWithoutUnMaximising() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the window is a window");
        }

        Started up = start();

        try
        {
            java.lang.reflect.Method raise =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod("comeToTheForeground");
            raise.setAccessible(true);

            javax.swing.SwingUtilities.invokeAndWait(() -> up.ui.setExtendedState(
                java.awt.Frame.ICONIFIED | java.awt.Frame.MAXIMIZED_BOTH));

            settle();

            // WHAT THE FIXTURE ACTUALLY GOT, before anything is asserted about what changed it.
            //
            // This window is built `setResizable(false)`, and a non-resizable frame refuses the
            // maximised bits - so the second assertion below is about a state this fixture may not be
            // able to reach.  Read here rather than assumed, and the assertion is made conditional on
            // it: an assertion whose precondition silently failed is worse than no assertion, because
            // it reads as coverage.
            final int arranged = up.ui.getExtendedState();

            assertEquals(arranged & java.awt.Frame.ICONIFIED, java.awt.Frame.ICONIFIED,
                "precondition: the window would not take the iconified state at all, so nothing below "
                + "is a statement about un-minimising it");

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    raise.invoke(up.ui);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            settle();

            assertEquals(up.ui.getExtendedState() & java.awt.Frame.ICONIFIED, 0,
                "the window is still iconified after being asked to come to the front, and an "
                + "iconified window cannot be activated at all");

            // ONLY WHERE THE FIXTURE COULD SET IT.  On this window it cannot - `setResizable(false)`
            // is in the form - so this half is exercised by any future window that can, and says so
            // rather than passing quietly on a state nobody arranged.
            if ((arranged & java.awt.Frame.MAXIMIZED_BOTH) != 0)
            {
                assertEquals(up.ui.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH,
                    java.awt.Frame.MAXIMIZED_BOTH,
                    "coming to the front un-maximised a window somebody had maximised - the two states "
                    + "share one field, and this one is not ours to change");
            }
        }
        finally
        {
            up.close();
        }
    }

    /**
     * And it does NOT take the caret back from something that already has it.
     *
     * **This is the fix attacking itself.** Asking for the keyboard on every activation is a bigger
     * hammer than asking once, and the obvious way to write it is worse than the fault it cures:
     * alt-tabbing away from a half-typed station name and back would empty the caret out of the field
     * every single time. `focusTheKeyboard` is therefore conditional, and this is the condition.
     *
     * The focus owner is asked of the `KeyboardFocusManager`, which answers null for every window the
     * OS has not focused - so this installs one that answers, which is the only way to put a test JVM
     * in the state a person's desktop is in. Nothing else about focus is faked: the fake is restored
     * before the window is disposed.
     *
     * MUTATION this catches: delete the `isDescendingFrom` early return from `focusTheKeyboard`.
     */
    @Test(timeOut = 300000)
    public void testComingBackToTheWindowDoesNotTakeTheCaretBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the keyboard is on a window");
        }

        Started up = start();

        java.awt.KeyboardFocusManager real =
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();

        try
        {
            java.awt.Component keyboard = (java.awt.Component) field(up.ui, "KeyboardTab");

            final java.awt.Component typing = somewhereToType(up.ui.getContentPane());

            assertNotNull(typing,
                "nothing in this window can be typed into, so there is no caret to steal - the "
                + "fixture is wrong, not the window");

            openTheTabsAbove(typing);

            javax.swing.SwingUtilities.invokeAndWait(() -> typing.requestFocusInWindow());

            settle();

            assertSame(up.ui.getMostRecentFocusOwner(), typing,
                "precondition: the caret is supposed to be somewhere other than the keyboard");

            java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(
                new java.awt.DefaultKeyboardFocusManager()
                {
                    @Override
                    public java.awt.Component getFocusOwner()
                    {
                        return typing;
                    }
                });

            java.lang.reflect.Method focus =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod("focusTheKeyboard");
            focus.setAccessible(true);

            final java.lang.reflect.Method call = focus;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    call.invoke(up.ui);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            settle();

            assertSame(up.ui.getMostRecentFocusOwner(), typing,
                "coming back to the window took the keyboard away from "
                + typing.getClass().getName() + ", which already had it - so anybody who alt-tabs "
                + "away from a half-typed name and back loses their place, which is a worse fault "
                + "than the one being fixed");
        }
        finally
        {
            java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(real);

            up.close();
        }
    }
    /**
     * `display()` does not take the topmost flag off after showing the window (OB-170, seventh pass).
     *
     * **The one invariant here that no behavioural test can hold.**  `setViewListener` makes the frame
     * topmost before it is shown, and says why - *"start with true to ensure keyboard events register
     * properly"*.  That is not a preference, it is the mechanism: a topmost window is the one case
     * Windows lets a process raise itself without already owning the foreground.  `display()` used to
     * put the operator's setting back on the very next line, and showing a window is asynchronous, so
     * the style was gone before the window manager had acted on the show.
     *
     * 2.8.1 survives that because its main window is the FIRST window the process shows and is
     * activated by the one-time right a process gets when the user starts it.  3.0.0 shows a splash
     * first, which spends that right - so the frame has nothing but the trick, and the trick was being
     * undone microseconds after it was played.
     *
     * **Measured: putting the restore back passes all five tests in this class.**  Whether Windows
     * grants the foreground is not something this process can read back, so there is nothing for a
     * behavioural test to assert.  The invariant is pinned where it lives instead - the same device
     * `testTheWindowAttachesItsRefreshCallback` uses, and for the same reason.
     *
     * The restore has not gone away: `takeTheKeyboard`'s raise holds the flag for four hundred
     * milliseconds and hands the operator's setting back afterwards.
     */
    @Test
    public void testDisplayDoesNotUndoTheTopmostTrickItDependsOn() throws Exception
    {
        java.io.File source = new java.io.File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(source.exists(), "cannot find " + source.getAbsolutePath()
            + " - this test reads the window's source, so it has to run from the project root");

        String body = withoutComments(new String(java.nio.file.Files.readAllBytes(source.toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        int opens = body.indexOf("public void display()");

        assertTrue(opens > 0, "display() has been renamed or removed");

        int shows = body.indexOf("setVisible(true);", opens);

        assertTrue(shows > opens, "display() no longer shows the window");

        int closes = body.indexOf("\n    }", shows);

        assertTrue(closes > shows, "cannot find the end of display()");

        String afterTheShow = body.substring(shows, closes);

        assertFalse(afterTheShow.contains("setAlwaysOnTop"),
            "display() takes the always-on-top flag off after showing the window.  That flag is how "
            + "the window gets the foreground at all when a splash has already spent the process's one "
            + "chance at it, and removing it in the same breath as the show is what left the keyboard "
            + "dead on start-up through six attempts at fixing something else.  The restore belongs to "
            + "takeTheKeyboard's raise, which holds it long enough to be used.  What display() says "
            + "after the show:\n" + afterTheShow);
    }

    /**
     * Java source with its comments removed, so a check reads the code and not the prose about it.
     *
     * The paragraph above this test explains at length why the restore must not be in `display()`, and
     * a search of the raw text would find `setAlwaysOnTop` in that explanation.
     */
    private static String withoutComments(String source)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < source.length(); i++)
        {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/') inLine = true;
            else if (c == '/' && next == '*') inBlock = true;
            else out.append(c);
        }

        return out.toString();
    }

    /**
     * The menu bar is greyed out for as long as the connecting notice is up.
     *
     * Adam: **"just gray out the menu bar before it loads, too."**  The notice is drawn on the window
     * itself now rather than in a splash of its own (OB-170), and the menu bar is part of the window
     * rather than of its content - so swapping the content in left every menu looking ready while none
     * of them could do anything, because they command a model that does not exist until the connect
     * is over.
     *
     * **And exactly the ones it took, back.**  Several menus are switched off by state; re-enabling
     * everything afterwards would switch those on, which is a worse fault than the one being fixed.
     * The second half of this test is that property, and it is the half a naive implementation fails.
     *
     * Neither method shows anything, which is why they are separate from `showConnecting` and why
     * this test can run in a battery: putting a window on the operator's screen is a thing this suite
     * is not allowed to do.
     *
     * MUTATION this catches: `ungreyTheMenus` walking the menu bar instead of its own list.
     */
    @Test(timeOut = 300000)
    public void testTheMenusAreGreyedWhileTheWindowIsConnecting() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the menu bar is on a window");
        }

        // BEFORE the window, not after it (OB-111): its construction reads the machine-global layout
        // preference and would otherwise open Adam's real railway.
        support.LayoutSandbox sandbox = support.LayoutSandbox.open();

        final org.traincontrol.gui.TrainControlUI[] made = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> made[0] = new org.traincontrol.gui.TrainControlUI());

            final org.traincontrol.gui.TrainControlUI ui = made[0];

            javax.swing.JMenuBar bar = ui.getJMenuBar();

            assertNotNull(bar, "the window has no menu bar, so this proves nothing");

            assertTrue(bar.getMenuCount() > 1, "one menu is not a menu bar");

            // One that is already off, so the restore has something it could get wrong.
            javax.swing.JMenu alreadyOff = bar.getMenu(bar.getMenuCount() - 1);

            javax.swing.SwingUtilities.invokeAndWait(() -> alreadyOff.setEnabled(false));

            java.util.List<javax.swing.JMenu> wereOn = new java.util.ArrayList<>();

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                if (bar.getMenu(i) != null && bar.getMenu(i).isEnabled()) wereOn.add(bar.getMenu(i));
            }

            assertFalse(wereOn.isEmpty(), "every menu was already off, so greying them proves nothing");

            // GREYED.
            call(ui, "greyTheMenus");

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                javax.swing.JMenu menu = bar.getMenu(i);

                if (menu == null) continue;

                assertFalse(menu.isEnabled(),
                    "the " + menu.getText() + " menu is still live while the window says it is "
                    + "connecting, and everything in it commands a model that does not exist yet");
            }

            // AND BACK, EXACTLY.
            call(ui, "ungreyTheMenus");

            for (javax.swing.JMenu menu : wereOn)
            {
                assertTrue(menu.isEnabled(),
                    "the " + menu.getText() + " menu never came back after the connect, so the "
                    + "application starts with a menu bar it cannot use");
            }

            assertFalse(alreadyOff.isEnabled(),
                "a menu that was switched off before the notice went up was switched ON by taking it "
                + "down - so whatever state had disabled it has been overruled by the start-up");
        }
        finally
        {
            if (made[0] != null)
            {
                final org.traincontrol.gui.TrainControlUI window = made[0];

                javax.swing.SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            sandbox.close();
        }
    }

    /**
     * A private no-argument method of the window, by name, on the event thread.
     */
    private static void call(org.traincontrol.gui.TrainControlUI on, String name) throws Exception
    {
        final java.lang.reflect.Method m =
            org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod(name);

        m.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                m.invoke(on);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
    }
}
