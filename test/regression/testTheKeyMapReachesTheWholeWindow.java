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
 * Adam: "it should take the keystrokes - any part of the app should respect the key mapping (locomotive selector) and
 * all related keyboard shortcuts."
 *
 * The map is a `KeyListener`, which hears only the component that holds the keyboard; putting the focus back on the
 * tabbed pane (OB-170) cannot work when the operator is USING what has it, such as a route table row.
 * `letTheWholeWindowDriveTrains` registers a key event POST processor instead: the focus manager runs it after the
 * focused component has been offered the key, and only for keys that came back unconsumed, so a component that wants a
 * key keeps it.
 *
 * The three claims are that rule's halves: it reaches a component that is not the keyboard, it does NOT fire twice when
 * the focused component is one the map already listens to, and it leaves a text component's letters alone.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout`.
 *
 * MUTATION: remove the `letTheWholeWindowDriveTrains()` call from `display()` and the first test fails, and so does the
 * third on its control; drop the `JTextComponent` guard and the third fails on its own claim.  The second test is not
 * mutation-backed and says so.
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

        model = support.TestStation.simulated();

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
     * A pre-release's window title leaves out what TrainControl is for (Adam: "when it's a pre release version, don't
     * show "for marklin central station 2 & 3" in the window title, as it doesn't fit anyway"); a release keeps it.
     * Asked of the window `display()` has shown.
     *
     * MUTATION: build the pre-release title with `app.uititle` again, and this fails.
     */
    @Test
    public void testAPreReleaseTitleLeavesOutTheCentralStation() throws Exception
    {
        final String[] title = new String[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> title[0] = ui.getTitle());

        String forTheStation = org.traincontrol.util.I18n.f("app.uititle", "").trim();

        if (MarklinControlStation.IS_PRE_RELEASE)
        {
            org.testng.Assert.assertTrue(title[0].startsWith(
                org.traincontrol.util.I18n.f("app.title", MarklinControlStation.versionForDisplay())),
                "the pre-release title does not begin with the version and its build: " + title[0]);

            org.testng.Assert.assertFalse(title[0].contains(forTheStation), "the pre-release title still says \""
                + forTheStation + "\": " + title[0]);
        }
        else
        {
            org.testng.Assert.assertTrue(title[0].startsWith(org.traincontrol.util.I18n.f("app.uititle",
                org.traincontrol.util.I18n.f("app.title", MarklinControlStation.RAW_VERSION))),
                "a release's title lost what TrainControl is for: " + title[0]);
        }
    }

    /**
     * A letter pressed on something that is not the keyboard still picks a locomotive.  The component is found rather
     * than named: anything that is not a place to type and not the tabbed pane the map already listens to.
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

        // AND IT REALLY HOLDS THE KEYBOARD: otherwise the key goes to the tabbed pane, which the map already listens
        // to, and the test passes with the feature switched off.
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
     * A key pressed on the keyboard itself is acted on once, not twice.  The map is attached to the tabbed pane as a
     * listener AND reachable through the post-processor; it is the same `KeyEvent` both times, which is how the second
     * visit is dropped.
     *
     * **A key that COUNTS, not a letter:** selecting the same locomotive twice lands in the same place, whereas "next
     * keyboard page" moves by one each time it runs.  On the keyboard tab deliberately: the same key means "next layout
     * page" while the LAYOUT tab is showing.
     *
     * **What this holds, honestly:** removing the `theKeyTheMapAlreadySaw` guard leaves it GREEN (measured on Windows),
     * because an event the focused component's own listener has handled does not come back to the post-processor.  The
     * guard is kept because "does a post-processor see an event the focus owner consumed" is a platform question, and
     * being wrong means every shortcut firing twice.  So this is a regression test rather than a proof: it goes red the
     * day that double appears.
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
     * A letter typed into a text component is typing, not a shortcut.  A text component claims a letter on KEY_TYPED,
     * after the post-processor runs, so this is the one case the "was it consumed" test cannot settle.
     */
    @Test
    public void testALetterTypedIntoATextBoxIsNotAShortcut() throws Exception
    {
        int wasOn = tabs().getSelectedIndex();

        // THE TAB GOES BACK EVEN ON A FAILURE (OB-267): a different tab left showing changes what the same keystroke
        // means for the next test.
        try
        {
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

        // THE CONTROL: "the letter did nothing" is also what a window with no routing looks like, so the same letter
        // from somewhere that is not a text box has to work.
        java.awt.Component elsewhere = somethingThatIsNotTheKeyboard();

        assertNotNull(elsewhere, "nothing to use as a control, so the claim above proves nothing");

        pressForReal(elsewhere, letter);

        assertNotSame(currentButton(), before,
            "control: the same letter did nothing from " + elsewhere.getClass().getSimpleName()
            + " either, so the claim above is about a window that routes no keys at all rather than "
            + "about typing being left alone");

        }
        finally
        {
            // The tab goes back, so this test cannot decide what the others are looking at.
            final int back = wasOn;

            javax.swing.SwingUtilities.invokeAndWait(() -> tabs().setSelectedIndex(back));

            settle();
        }
    }

    /**
     * A text box that can really hold the keyboard, looked for on every tab in turn: the log and the JSON pane are not
     * on the tab showing at start-up and cannot take the focus there, and a skipped claim is not a kept one.  Null when
     * no tab has one.
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
     * Presses a key the way the operating system does, through the queue: to the focus owner first, the post-processor
     * afterwards.  The focus manager's own `dispatchEvent` reaches only the post-processor, so only this order can show
     * a key acted on twice.  The focus has to be real, since a posted key event goes to whatever the focus manager says
     * holds the keyboard now.
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
     * Puts the keyboard on one component, and says so plainly when it will not go: the key must reach somewhere the map
     * does not already listen.
     */
    private static void focusOn(java.awt.Component on) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> on.requestFocusInWindow());

        settle();

        // A DESKTOP THAT WILL NOT GIVE THE WINDOW THE KEYBOARD CANNOT SHOW THIS: asserted, it failed at random on a
        // loaded battery machine; skipped, the class says it could not be asked.
        if (ui.getMostRecentFocusOwner() != on)
        {
            throw new org.testng.SkipException("this desktop would not give the keyboard to "
                + on.getClass().getSimpleName() + ", so the key map cannot be asked about it here");
        }

    }

    /**
     * The floor: at least one of this class's claims was actually asked.  A class where every claim skips reports green
     * while asking nothing.  Asked of the RESULTS rather than of a counter, which sat at one of eight skip gates in the
     * keyboard class and failed that whole class whenever that gesture was unavailable.  Filtered to this class, as a
     * run of several classes shares one context.
     */
    @org.testng.annotations.AfterClass(alwaysRun = true)
    public static void testSomethingWasActuallyAsked(org.testng.ITestContext context)
    {
        int passed = 0;

        for (org.testng.ITestResult result : context.getPassedTests().getAllResults())
        {
            if (result.getTestClass().getRealClass() == testTheKeyMapReachesTheWholeWindow.class) passed++;
        }

        if (passed == 0)
        {
            throw new AssertionError("every claim in this class skipped, so it reported green having"
                + " asked nothing: this desktop would not give a window the keyboard at all (OP2-C8)");
        }
    }



    /** Something in this window that is neither the keyboard nor a place to type. */
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

            // NO KEY LISTENER OF ITS OWN: the map is attached as a listener to several components, and a key dispatched
            // at one of those is handled whether or not anything routes it, so only a component with none can be served
            // by the routing alone.  It must also be SHOWING and focusable to hold the keyboard.
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

    /** A place in this window somebody could be typing in. */
    private static javax.swing.text.JTextComponent somewhereToType(java.awt.Container in)
    {
        for (java.awt.Component child : in.getComponents())
        {
            // SHOWING AND FOCUSABLE, because this one has to actually take the keyboard.
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

    /** A mapped letter whose button is not the one already active, so "it changed" is answerable. */
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
