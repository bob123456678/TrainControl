package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * A keystroke right after start-up lands on the keyboard and changes the active key (OB-168, OB-170).
 *
 * The frame is not the thing that holds the keyboard: it is `setFocusable(false)`, and the letter keys are read by
 * KeyListeners on `KeyboardTab` and on the locomotive panel, so `takeTheKeyboard` asks the tabbed pane for it.
 *
 * The tests ask `getMostRecentFocusOwner`, not `getFocusOwner`: a test JVM's window is usually not the OS's focused
 * window (the launching terminal is), and `getFocusOwner` answers null for every such window.  The most recent owner
 * is the child the window hands the keyboard to the moment it is focused, and `requestFocusInWindow` sets it on any
 * showing window.  Each test presses its letter on the component the window arranged to receive it, so a build that
 * arranged for the wrong component fails at that press.
 */
public class testTheWindowTakesTheKeyboard
{
    /**
     * An application that has started: sandbox, model and window.
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
     * Start-up, in the order `MarklinControlStation.init` performs it: build the window, hand it the model, then
     * `display()`, which calls `takeTheKeyboard`; calling that directly would test a method rather than a start-up.
     *
     * BEFORE the model (OB-111): `init` reads the machine-global layout preference and would otherwise open Adam's real
     * railway.
     */
    private static Started start() throws Exception
    {
        Started up = new Started();

        try
        {
            up.sandbox = support.LayoutSandbox.open();

            up.model = support.TestStation.simulated();

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

            // AND THE RAISE HAS TO FINISH: `takeTheKeyboard` posts the raise, which hands its always-on-top flag back
            // 400 ms later, and only THEN asks for the keyboard (the flag's restore is a native window-style change
            // that resets the focus owner).  Bounded, and it does not assert: the tests below say so when the
            // keyboard never arrives.
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
        catch (Exception | Error failed)
        {
            // WHATEVER FAILED, THE PREFERENCE GOES BACK: a failed `init` or window constructor would otherwise leave
            // the machine-global layout preference pointing at a folder under %TEMP%.
            up.close();

            throw failed;
        }
    }

    /**
     * Lets everything the last gesture POSTED actually run: `takeTheKeyboard` and `displayCurrentButtonLoc` work
     * through `invokeLater`.  Several passes, because a posted task may post another.
     */
    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 5; pass++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }

    private static Object field(Object of, String name) throws Exception
    {
        java.lang.reflect.Field f =
            org.traincontrol.gui.TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(of);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<Integer, javax.swing.JButton> mapping(
        org.traincontrol.gui.TrainControlUI ui) throws Exception
    {
        return (java.util.Map<Integer, javax.swing.JButton>) field(ui, "buttonMapping");
    }

    /**
     * A mapped letter whose button is not the one already active, so "the active key changed" can be answered.
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
     * Presses a letter on whatever component is holding the keyboard, dispatched to it directly because the queue
     * would route by the OS's idea of the focus owner.
     */
    private static void press(java.awt.Component on, int keyCode) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> on.dispatchEvent(
            new java.awt.event.KeyEvent(on, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, keyCode, (char) keyCode)));

        settle();
    }

    private static boolean isTheKeyboard(java.awt.Component what, java.awt.Component keyboard)
    {
        return what == keyboard
            || (what != null && javax.swing.SwingUtilities.isDescendingFrom(what, (java.awt.Container) keyboard));
    }

    /**
     * Something else in this window that can hold the keyboard and is NOT a place anybody types.  Found rather than
     * named, so the claim is about any control.  Text components are excluded here and looked for separately below,
     * since running both tests against one component would make one a statement about the other's fixture.
     */
    private static java.awt.Component somethingThatDoesNotType(java.awt.Container in,
        java.awt.Component keyboard)
    {
        for (java.awt.Component child : in.getComponents())
        {
            // THE PANE ITSELF, not everything inside it: at start-up every showing focusable component is inside it.
            if (child == keyboard) continue;

            // A COMPONENT WITH CHILDREN IS STILL A COMPONENT, and nothing that carries the map itself counts: focus
            // resting on a KeyListener holder would still hear the keys.
            if (child.isFocusable() && child.isShowing() && child.isEnabled()
                && child.getKeyListeners().length == 0
                && !(child instanceof javax.swing.text.JTextComponent))
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
     * A place in this window somebody could be typing in: the log area and the JSON pane, both clickable.
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
     * Opens whatever tabs are hiding a component so it can be clicked into: a JTabbedPane makes the children of an
     * unselected tab invisible, and `Component.isRequestFocusAccepted` then refuses the request.
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
     * Start the application, press a letter, watch the active key move.
     *
     * MUTATION this catches: take `takeTheKeyboard()` off the end of `display()`; the letter then reaches a component
     * with no KeyListener and `currentButton` does not move.
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
     * The first fix asked once, from the end of `display()`, but `requestFocusInWindow` does nothing while the window
     * is not the focused window, and on Windows a starting process's window often is not one.  This reproduces that
     * without an OS: something else in the window is given the keyboard after start-up, then the window gains focus.
     *
     * MUTATION this catches: remove the `addWindowFocusListener` block from `takeTheKeyboard` and keep the single
     * posted request; the press then leaves `currentButton` where it was.
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

            // SKIPPED RATHER THAN FAILED when this desktop will not move the keyboard: that says nothing about the
            // window.
            if (up.ui.getMostRecentFocusOwner() != elsewhere)
            {
                throw new org.testng.SkipException("this desktop would not take the keyboard off the tabbed"
                    + " pane, so the state OB-170 describes cannot be set up here");
            }


            javax.swing.JButton before = (javax.swing.JButton) field(up.ui, "currentButton");

            // Whether the activation was DELIVERED, a separate question from what it did; without it the test below is
            // silently vacuous whenever the event is dropped.
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

            // AWAY, AND THEN BACK: `DefaultKeyboardFocusManager.dispatchEvent` drops a WINDOW_GAINED_FOCUS aimed at the
            // window that is already focused, so a lone synthetic activation can be a no-op.  This is also the gesture
            // the test is named for: alt-tab away and back.
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
     * Coming to the foreground gives the always-on-top setting back (OB-170).
     *
     * The fix works by breaking a rule: Windows refuses a foreground change from a process not already in the
     * foreground, except for a topmost window, so the window is made topmost, raised, and set back to whatever the
     * operator chose.  Leaving it topmost would put the application above everything else for the session.
     *
     * Both directions, because a one-directional test passes on `setAlwaysOnTop(false)` unconditionally.  The restore
     * is on a timer (handing the flag back in the same breath as the raise loses the raise), so this waits for it, the
     * wait bounded so a restore that never happens fails rather than hangs.
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

        // WHETHER IT WAS STORED: see the note at `testDiagramExport`.
        boolean onTopStored = org.traincontrol.gui.TrainControlUI.getPrefs()
            .get(org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF, null) != null;

        boolean onTopWas = org.traincontrol.gui.TrainControlUI.getPrefs().getBoolean(
            org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF, false);

        try
        {
            java.lang.reflect.Method raise =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod("comeToTheForeground");
            raise.setAccessible(true);

            for (final boolean onTop : new boolean[] { false, true })
            {
                // THE PREFERENCE, not the window's current flag: the raise restores what the OPERATOR chose, and the
                // window arrives still topmost on purpose.
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

                // Bounded, because the restore is posted: up to two attempts at FOREGROUND_SETTLE_MS plus slack.
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
            if (onTopStored)
            {
                org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                    org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF, onTopWas);
            }
            else
            {
                org.traincontrol.gui.TrainControlUI.getPrefs()
                    .remove(org.traincontrol.gui.TrainControlUI.ONTOP_SETTING_PREF);
            }

            up.close();
        }
    }

    /**
     * An iconified window is un-iconified, without un-maximizing a maximized one: a window in the iconified state
     * cannot be activated, and the maximized bits live in the same field, so the ICONIFIED bit is cleared rather than
     * the state replaced.
     *
     * MUTATION this catches: removing the un-iconify entirely.  The maximize half would catch
     * `setExtendedState(java.awt.Frame.NORMAL)`, but cannot run on THIS window (`setResizable(false)` refuses the
     * maximized bits), so it is guarded by what the fixture managed to arrange.
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

            // WHAT THE FIXTURE ACTUALLY GOT: this window is `setResizable(false)` and may refuse the maximized bits, so
            // the second assertion is conditional on it rather than passing on a state nobody set.
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

            // ONLY WHERE THE FIXTURE COULD SET IT; a window that can be maximized will exercise this half.
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
     * It does NOT take the caret back from something that already has it: alt-tabbing away from a half-typed station
     * name and back must not empty the caret out of the field, so `focusTheKeyboard` is conditional.
     *
     * The focus owner is asked of the `KeyboardFocusManager`, which answers null for every window the OS has not
     * focused, so this installs one that answers (the only way to put a test JVM in a person's desktop state); the
     * fake is restored before the window is disposed.
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
     * `display()` does not take the topmost flag off after showing the window (OB-170).
     *
     * `setViewListener` makes the frame topmost before it is shown, which is the mechanism: a topmost window is the one
     * case Windows lets a process raise itself without owning the foreground.  Showing is asynchronous, so a restore
     * on the next line removed the style before the window manager acted.  Whether Windows grants the foreground cannot
     * be read back, so no behavioral test can hold this (putting the restore back passes the rest of the class); it is
     * pinned in the source, as `testTheWindowAttachesItsRefreshCallback` does.  `takeTheKeyboard`'s raise still hands
     * the operator's setting back after 400 ms.
     */
    @Test
    public void testDisplayDoesNotUndoTheTopmostTrickItDependsOn() throws Exception
    {
        // The method, comments blanked, so a search of the raw text does not find `setAlwaysOnTop` in the explanation.
        String display = SourceOf.withoutComments(SourceOf.declaration("public void display()"));

        int shows = display.indexOf("setVisible(true);");

        assertTrue(shows > 0, "display() no longer shows the window");

        String afterTheShow = display.substring(shows);

        assertFalse(afterTheShow.contains("setAlwaysOnTop"),
            "display() takes the always-on-top flag off after showing the window.  That flag is how "
            + "the window gets the foreground at all when a splash has already spent the process's one "
            + "chance at it, and removing it in the same breath as the show is what left the keyboard "
            + "dead on start-up through six attempts at fixing something else.  The restore belongs to "
            + "takeTheKeyboard's raise, which holds it long enough to be used.  What display() says "
            + "after the show:\n" + afterTheShow);
    }

    /**
     * The menu bar is grayed out for as long as the connecting notice is up (OB-170).
     *
     * The menu bar is part of the window rather than of its content, so swapping the content in left every menu looking
     * ready while none could act: they command a model that does not exist until the connect is over.  Exactly the
     * ones it took are switched back, since several menus are off by state.  Neither method shows anything, so this
     * can run in a battery.
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

        // BEFORE the window (OB-111): its construction reads the machine-global layout preference.
        support.LayoutSandbox sandbox = null;

        final org.traincontrol.gui.TrainControlUI[] made = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            // Inside the try, so nothing can leave the preference behind.
            sandbox = support.LayoutSandbox.open();

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

            // GRAYED.
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

            if (sandbox != null) sandbox.close();
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

    /**
     * The floor: at least one of this class's claims was actually asked.  A class where every claim skips reports green
     * while asking nothing.  Asked of the RESULTS rather than a counter, which was incremented at only one of the skip
     * gates; filtered to this class, because a run of several classes shares one context.
     */
    @org.testng.annotations.AfterClass(alwaysRun = true)
    public static void testSomethingWasActuallyAsked(org.testng.ITestContext context)
    {
        int passed = 0;

        for (org.testng.ITestResult result : context.getPassedTests().getAllResults())
        {
            if (result.getTestClass().getRealClass() == testTheWindowTakesTheKeyboard.class) passed++;
        }

        if (passed == 0)
        {
            throw new AssertionError("every claim in this class skipped, so it reported green having"
                + " asked nothing: this desktop would not give a window the keyboard at all (OP2-C8)");
        }
    }
}
