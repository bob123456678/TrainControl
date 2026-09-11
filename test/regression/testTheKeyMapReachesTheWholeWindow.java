package regression;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The locomotive keys work from anywhere in the window, not only from whatever holds the focus.
 *
 * Adam, 2026-09-11: **"it should take the keystrokes - any part of the app should respect the key
 * mapping (locomotive selector) and all related keyboard shortcuts."**
 *
 * **Why focus could not be the answer.** The map is a `KeyListener`, and a `KeyListener` hears only
 * the component that holds the keyboard. Four rounds of `OB-170` answered that by putting the focus
 * back on the tabbed pane, which is right when nothing in particular has it - and cannot be right when
 * the operator is USING what has it. Clicking a row in the route table or a button in the autonomy
 * editor left the railway deaf to the letters, and the fix for that cannot be to take the keyboard off
 * what somebody is working in.
 *
 * **What replaced it, and what that buys.** `letTheWholeWindowDriveTrains` registers a key event POST
 * processor: the focus manager runs it after the focused component has been offered the key, and only
 * for keys that came back unconsumed. So the precedence rule is the toolkit's - a component that wants
 * a key keeps it, and the map gets what nothing claimed - rather than a list, kept in this window, of
 * which keys each widget needs.
 *
 * The three claims below are that rule's three halves: it reaches a component that is not the
 * keyboard, it does NOT fire twice when the focused component is one the map already listens to, and
 * it leaves a text component's letters alone.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout`.
 *
 * MUTATION: remove the `letTheWholeWindowDriveTrains()` call from `display()` and the first test
 * fails, and so does the third - on its control, which is what that control is for; drop the
 * `JTextComponent` guard and the third fails on its own claim. The second test is not mutation-backed
 * and says so in its own words.
 *
 * @author Adam
 */
public class testTheKeyMapReachesTheWholeWindow
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("a keyboard needs a window");
        }

        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        final MarklinControlStation loaded = model;

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui = new TrainControlUI();
                ui.setViewListener(loaded, new java.util.concurrent.CountDownLatch(1));
                ui.display();
            }
            catch (Exception cannotStart)
            {
                throw new RuntimeException(cannotStart);
            }
        });

        settle();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                ui.stopDrivingTrainsFromTheWholeWindow();

                javax.swing.SwingUtilities.invokeAndWait(() -> ui.dispose());
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A letter pressed on something that is not the keyboard still picks a locomotive.
     *
     * The component is found rather than named - anything in the window that is not a place to type
     * and is not the tabbed pane the map already listens to. What it IS does not matter; that it is
     * not the keyboard is the whole claim.
     */
    @Test
    public void testALetterSomewhereElseInTheWindowStillPicksALocomotive() throws Exception
    {
        javax.swing.JButton before = currentButton();

        assertNotNull(before, "no locomotive button is active, so nothing could be seen to change");

        int letter = aLetterOtherThan(before);

        java.awt.Component elsewhere = somethingThatIsNotTheKeyboard();

        assertNotNull(elsewhere,
            "nothing in this window can be found that is neither the keyboard nor a place to type, so "
            + "the state Adam described cannot be set up - the fixture is wrong, not the window");

        // AND IT REALLY HOLDS THE KEYBOARD.  Without this the key is delivered to whatever has the
        // focus - which after start-up is the tabbed pane, the one component the map already listens
        // to - and the test passes with the whole feature switched off, measuring the listener it was
        // written to be independent of.  Found by mutation, 2026-09-11.
        pressForReal(elsewhere, letter);

        assertNotSame(currentButton(), before,
            "a letter pressed while " + elsewhere.getClass().getSimpleName() + " held the keyboard "
            + "selected no locomotive. The map is a KeyListener on the tabbed pane, so it hears only "
            + "that pane - and an operator clicking anything else in this window silently loses the "
            + "letters (Adam, 2026-09-11: any part of the app should respect the key mapping)");

        assertSame(currentButton(), mapping().get(letter),
            "a letter reached the map from elsewhere in the window but chose the wrong button");
    }

    /**
     * A key pressed on the keyboard itself is acted on once, not twice.
     *
     * The map is attached to the tabbed pane as a listener AND reachable through the post-processor,
     * so a key pressed there passes it twice. It is the same `KeyEvent` object both times, which is
     * how the second visit is recognised and dropped.
     *
     * **A key that COUNTS, not a letter.** The first version pressed a letter and asserted the button
     * it names is active - and selecting the same locomotive twice lands in the same place, so it
     * could not tell one hop from two. "Next keyboard page" moves by one each time it runs, so a
     * double shows up in the number.
     *
     * **What this holds, honestly.** Removing the `theKeyTheMapAlreadySaw` guard leaves it GREEN:
     * measured on Windows, 2026-09-11. On the path a real keystroke takes, an event the focused
     * component's own listener has already handled does not come back to the post-processor, so the
     * double this describes does not arise here and the guard is insurance rather than something this
     * test proves. It is kept because "does a post-processor see an event the focus owner's listener
     * consumed" is a platform question, not a language one, and the cost of being wrong about it is
     * every shortcut in this window firing twice - two pages, two dialogs, two emergency stops.
     *
     * So this is a regression test rather than a proof: it goes red the day that double appears, from
     * a platform, a toolkit version, or a change to how the map is attached. A claim no mutation can
     * make red today is worth keeping only when it says what it cannot prove, which is why this
     * paragraph is here.
     *
     * On the keyboard tab deliberately: the same key means "next layout page" while the LAYOUT tab is
     * showing, which is a different branch and not this claim.
     */
    @Test
    public void testAKeyOnTheKeyboardItselfIsNotActedOnTwice() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> tabs().setSelectedIndex(0));

        settle();

        java.awt.Component keyboard = (java.awt.Component) field("KeyboardTab");

        int before = (int) field("keyboardNumber");

        pressForReal(keyboard, java.awt.event.KeyEvent.VK_EQUALS);

        int after = (int) field("keyboardNumber");

        assertEquals(after, before + 1,
            "one press of \"next keyboard page\" moved " + (after - before) + " pages. The map "
            + "listens to this component AND sees the key again through the post-processor, so "
            + "without the already-seen guard one keystroke is acted on twice");
    }

    /**
     * A letter typed into a text component is typing, not a shortcut.
     *
     * A text component claims a letter on KEY_TYPED, which is after the post-processor runs, so this
     * is the one case the "was it consumed" test cannot settle and the only one that needs naming.
     * Taking a letter out of a half-typed line would be a worse fault than the one being fixed.
     */
    @Test
    public void testALetterTypedIntoATextBoxIsNotAShortcut() throws Exception
    {
        int wasOn = tabs().getSelectedIndex();

        javax.swing.text.JTextComponent typing = findSomewhereToType();

        assertNotNull(typing,
            "no tab in this window has a text box that can hold the keyboard, so the one case the "
            + "consumed test cannot settle cannot be exercised - the fixture is wrong, not the window");

        javax.swing.JButton before = currentButton();

        int letter = aLetterOtherThan(before);

        pressForReal(typing, letter);

        assertSame(currentButton(), before,
            "a letter typed into " + typing.getClass().getSimpleName() + " also selected a "
            + "locomotive. Text components consume a letter at KEY_TYPED, which is after this runs, "
            + "so they have to be stepped around by name");

        // THE CONTROL, and this test is worth nothing without it: "the letter did nothing" is also
        // what a window with no routing at all looks like.  The same letter, from somewhere that is
        // not a text box, has to work.
        java.awt.Component elsewhere = somethingThatIsNotTheKeyboard();

        assertNotNull(elsewhere, "nothing to use as a control, so the claim above proves nothing");

        pressForReal(elsewhere, letter);

        assertNotSame(currentButton(), before,
            "control: the same letter did nothing from " + elsewhere.getClass().getSimpleName()
            + " either, so the claim above is about a window that routes no keys at all rather than "
            + "about typing being left alone");

        // The tab goes back, so this test cannot decide what the others are looking at.
        final int back = wasOn;

        javax.swing.SwingUtilities.invokeAndWait(() -> tabs().setSelectedIndex(back));

        settle();
    }

    /**
     * A text box that can really hold the keyboard, looked for on every tab in turn.
     *
     * The two places anybody types in this window - the log and the JSON pane - are not on the tab
     * that is showing at start-up, and a component on a tab nobody is looking at is not showing and
     * cannot take the focus. Looking only at the tab that happens to be up made this a SKIP, and a
     * skipped claim is not a kept one.
     *
     * @return the text box, with its tab selected, or null when no tab has one
     */
    private static javax.swing.text.JTextComponent findSomewhereToType() throws Exception
    {
        for (int which = 0; which < tabs().getTabCount(); which++)
        {
            if (!tabs().isEnabledAt(which)) continue;

            final int show = which;

            javax.swing.SwingUtilities.invokeAndWait(() -> tabs().setSelectedIndex(show));

            settle();

            javax.swing.text.JTextComponent found = somewhereToType(ui.getContentPane());

            if (found != null) return found;
        }

        return null;
    }

    private static javax.swing.JTabbedPane tabs()
    {
        try
        {
            return (javax.swing.JTabbedPane) field("KeyboardTab");
        }
        catch (Exception noSuchField)
        {
            throw new RuntimeException(noSuchField);
        }
    }

    /**
     * Presses a key the way the operating system does, through the queue.
     *
     * **Both halves of the journey, which `press` deliberately does not have.** The focus manager's
     * own `dispatchEvent` reaches the post-processor and never offers the event to the component's
     * KeyListeners; a posted event goes the whole way - to the focus owner first, and to the
     * post-processor afterwards. Only that order can show a key being acted on twice, and the first
     * version of this test used the short path and so passed with the guard removed (found by
     * mutation, 2026-09-11).
     *
     * The focus has to be real for this, not merely the window's most recent: a posted key event is
     * delivered to whatever the focus manager says holds the keyboard now.
     *
     * @param on the component that should receive it
     * @param keyCode the key
     */
    private static void pressForReal(java.awt.Component on, int keyCode) throws Exception
    {
        focusOn(on);

        java.awt.Component holding = java.awt.KeyboardFocusManager
            .getCurrentKeyboardFocusManager().getFocusOwner();

        if (holding != on)
        {
            throw new SkipException("this window does not hold the keyboard for real (it is on "
                + (holding == null ? "no component in this JVM" : holding.getClass().getSimpleName())
                + "), so a posted key would not be delivered to " + on.getClass().getSimpleName()
                + " and the double this asks about could not happen either way");
        }

        final java.awt.event.KeyEvent pressed = new java.awt.event.KeyEvent(on,
            java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode,
            (char) keyCode);

        java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(pressed);

        settle();
    }

    /**
     * Puts the keyboard on one component, and says so plainly when it will not go.
     *
     * The whole of these tests rests on this: the key has to be delivered somewhere that is NOT the
     * component the map already listens to, or what is being measured is that listener.
     *
     * @param on what should hold the keyboard
     */
    private static void focusOn(java.awt.Component on) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> on.requestFocusInWindow());

        settle();

        assertSame(ui.getMostRecentFocusOwner(), on,
            "the keyboard would not go to " + on.getClass().getSimpleName() + ", so this test would "
            + "be about whatever holds it instead - which is the tabbed pane, the one component the "
            + "map already listens to");
    }


    /**
     * Something in this window that is neither the keyboard nor a place to type.
     */
    private static java.awt.Component somethingThatIsNotTheKeyboard() throws Exception
    {
        java.awt.Component keyboard = (java.awt.Component) field("KeyboardTab");

        return search(ui.getContentPane(), keyboard);
    }

    private static java.awt.Component search(java.awt.Container in, java.awt.Component keyboard)
    {
        for (java.awt.Component child : in.getComponents())
        {
            if (child == keyboard) continue;

            // NO KEY LISTENER OF ITS OWN, which is the whole point and was missing: the map is
            // attached as a listener to several components, not just the tabbed pane, and a key
            // dispatched at one of those is handled by that listener whether or not anything routes
            // it. The first version of this test passed with the routing switched off for exactly
            // that reason (found by mutation, 2026-09-11). A component with no key listener can only
            // be served by the routing, so it is the only honest fixture.
            //
            // It must also be SHOWING and focusable, or it cannot hold the keyboard to be asked.
            if (child.isFocusable() && child.isEnabled() && child.isShowing()
                && child.getKeyListeners().length == 0
                && !(child instanceof javax.swing.text.JTextComponent)
                && !(child instanceof javax.swing.JButton))
            {
                return child;
            }

            if (child instanceof java.awt.Container)
            {
                java.awt.Component found = search((java.awt.Container) child, keyboard);

                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * A place in this window somebody could be typing in.
     */
    private static javax.swing.text.JTextComponent somewhereToType(java.awt.Container in)
    {
        for (java.awt.Component child : in.getComponents())
        {
            // SHOWING AND FOCUSABLE, because this one has to actually take the keyboard: a text box
            // on a tab nobody is looking at cannot, and the fixture would fail on it rather than
            // measure anything.
            if (child instanceof javax.swing.text.JTextComponent && child.isEnabled()
                && child.isShowing() && child.isFocusable())
            {
                return (javax.swing.text.JTextComponent) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.text.JTextComponent found = somewhereToType((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static javax.swing.JButton currentButton() throws Exception
    {
        return (javax.swing.JButton) field("currentButton");
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<Integer, javax.swing.JButton> mapping() throws Exception
    {
        return (java.util.Map<Integer, javax.swing.JButton>) field("buttonMapping");
    }

    /**
     * A mapped letter whose button is not the one already active, so "it changed" is answerable.
     */
    private static int aLetterOtherThan(javax.swing.JButton taken) throws Exception
    {
        for (int key = java.awt.event.KeyEvent.VK_A; key <= java.awt.event.KeyEvent.VK_Z; key++)
        {
            javax.swing.JButton button = mapping().get(key);

            if (button != null && button != taken) return key;
        }

        fail("no letter maps to a button other than the active one, so pressing one could not change "
            + "anything - the fixture is wrong, not the window");

        return -1;
    }

    private static Object field(String name) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 5; pass++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
