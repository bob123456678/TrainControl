package regression;

import java.lang.reflect.Field;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The locomotive mapping pages stop at fifty, in the method as well as in the menu (FR-033, MT-383).
 *
 * Adam, 2026-09-13: **"too much manual effort.  add an automated test for this."**  Adding fifty pages
 * by hand to find out whether the fifty-first is refused is exactly the kind of counting a person
 * should not be asked to do, and it is the kind a test does not mind.
 *
 * **Both halves, because the point of FR-033 was that there are two.**  `canAddLocMappingPage` is what
 * the menu greys itself on; `addLocMappingPage` refuses again on its own account, and its javadoc says
 * why - *"this method is public and the greying is one caller's manners, not the rule.  A guard that
 * lives only in the interface is a guard the next caller does not have."*  That is
 * `guard-and-affordance-same-question`, and a test that checked only the greying would pass with the
 * method's own refusal deleted.
 *
 * **What is deliberately NOT asserted: that loading cannot exceed fifty.**  `setViewListener` grows the
 * count past the ceiling without asking, on purpose, and the comment beside it gives the reason - a
 * saved state can hold more pages than the preference knows about, and clamping on load would throw
 * away every mapping past the fiftieth.  So the two sides look inconsistent and must stay that way.
 * Asserting symmetry here would be asserting the data loss that comment refuses.
 *
 * The window is built through `LayoutSandbox` (OB-111), so the layout preference is a throwaway copy
 * and Adam's own railway is never opened.
 *
 * @author Adam
 */
public class testFiftyLocomotiveMappingPages
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    private static int pagesWere;

    /** What the user-wide store said before this class ran; -1 when it said nothing (SVX-C7). */
    private static int storedWas;

    /**
     * Builds the window on a throwaway layout.
     *
     * @throws Exception when the window cannot be built, which is a broken harness rather than a
     *         failing guard
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the mapping pages live on a window");
        }

        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        pagesWere = pages();

        // AND WHAT THE STORE SAYS, because the passing path is not the only path (SVX-C7).
        //
        // Nothing here writes the preference and the comment below says so - but under the mutation
        // this class documents, `addLocMappingPage` succeeds and writes 51 to the store itself before
        // the assertion can object.  The run that CATCHES the regression would then leave Adam's
        // window opening with fifty-one mapping tabs: the same damage, from the same store, that the
        // rewrite exists to prevent.
        //
        // Two lines, and the sentence becomes true however this class dies.
        storedWas = java.util.prefs.Preferences.userNodeForPackage(TrainControlUI.class)
            .getInt(TrainControlUI.LOC_MAPPING_PAGES_PREF, -1);
    }

    /**
     * Puts the page count back, and the layout preference with it.
     *
     * The pages are a PREFERENCE, so leaving fifty behind would change what Adam's own window opens
     * with - which is the kind of damage `LayoutSandbox` exists to prevent for the layout and does not
     * cover for this.
     *
     * @throws Exception from the window
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final int back = pagesWere;

                javax.swing.SwingUtilities.invokeAndWait(() -> setPages(back));

                // AND THE STORE GOES BACK TO WHAT IT SAID (SVX-C7), whether or not this class was
                // what changed it.  `alwaysRun`, so a failed or mutated run undoes it too - which is
                // the run that can have written it.
                java.util.prefs.Preferences store =
                    java.util.prefs.Preferences.userNodeForPackage(TrainControlUI.class);

                if (storedWas < 0)
                {
                    store.remove(TrainControlUI.LOC_MAPPING_PAGES_PREF);
                }
                else if (store.getInt(TrainControlUI.LOC_MAPPING_PAGES_PREF, -1) != storedWas)
                {
                    store.putInt(TrainControlUI.LOC_MAPPING_PAGES_PREF, storedWas);
                }

                final TrainControlUI closing = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The ceiling is refused by the menu's own question and by the method behind it.
     *
     * MUTATION: delete the `canAddLocMappingPage()` guard from `addLocMappingPage` and the second
     * claim fails while the first still passes, which is the whole reason both are here.
     *
     * @throws Exception from the window
     */
    @Test(timeOut = 120000)
    public void testTheFiftiethPageIsTheLast() throws Exception
    {
        final int ceiling = TrainControlUI.MAX_LOC_MAPPINGS;

        // AT THE CEILING BY REFLECTION, NOT BY FILLING (and this is the whole design of the test).
        //
        // `addLocMappingPage` writes the page count into the Java Preferences on every successful
        // add, and that store is USER-WIDE and outlives the JVM. Filling to fifty therefore wrote
        // fifty into Adam's own settings; the first version of this test hung, was killed, and its
        // teardown never ran - so his window would have opened with fifty mapping tabs, and
        // `ui.testEveryLanguageFits` failed on a tab strip wanting 3307 pixels.
        //
        // The fill was never what this claims. The CEILING is, and setting the count directly touches
        // no preference - so nothing on the passing path can leave one wrong, which matters more than
        // exercising an add loop that `addLocMappingPage`'s own callers exercise anyway.
        //
        // NOT "however it dies", which the first wording claimed and SVX-C7 disproved: the mutation
        // this class documents - deleting the guard from `addLocMappingPage` - makes the call below
        // succeed, and a successful add writes the count to the preference before anything here can
        // object.  A test that is deliberately broken can still leave 51 behind. What it cannot do is
        // leave fifty behind by passing, which is what happened.
        setPages(ceiling);

        assertEquals(pages(), ceiling,
            "the page count could not be set to the ceiling, so nothing below is being asked at it");

        // THE AFFORDANCE: what the menu greys itself on.
        assertFalse(ui.canAddLocMappingPage(),
            "at " + ceiling + " pages the window still says another may be added, so the menu item"
            + " that asks this stays live at the ceiling (FR-033)");

        // AND THE GUARD, which is a separate rule and not the menu's manners.  Called directly, as
        // any other caller would: the count must not move.
        //
        // ON A DAEMON THREAD, because the refusal is a MODAL DIALOG.  `addLocMappingPage` says no by
        // showing one, so calling it through `invokeAndWait` deadlocks: the event thread goes into the
        // dialog's own loop and never comes back.  This test hung for twenty minutes that way before
        // the dialog was the answer rather than the obstacle.
        int atTheCeiling = pages();

        Thread refusing = new Thread(() -> ui.addLocMappingPage());

        refusing.setDaemon(true);

        refusing.start();

        // The dialog is the refusal, so it is asserted rather than merely dismissed: a guard that
        // returns quietly would pass a count check just as well, and the operator would be left
        // pressing a menu item that does nothing.
        java.awt.Window refusal = waitForDialog(12000);

        assertNotNull(refusal,
            "at the ceiling, addLocMappingPage neither added a page nor said anything - no dialog"
            + " appeared within twelve seconds. A guard that refuses in silence leaves the operator"
            + " pressing an item that does nothing.");

        javax.swing.SwingUtilities.invokeAndWait(() -> refusal.dispose());

        refusing.join(12000);

        assertFalse(refusing.isAlive(), "the refusal did not return after its dialog was closed");

        assertEquals(pages(), atTheCeiling,
            "addLocMappingPage added a " + (ceiling + 1) + "th page when called directly. The menu"
            + " greys itself on canAddLocMappingPage, but that is one caller's manners - the method is"
            + " public and refuses on its own account, which is the half of FR-033 a test of the"
            + " greying alone would miss.");
    }

    /**
     * The refusal dialog, identified by what it says, or null if it does not appear in time.
     *
     * **NOT "the first showing dialog" (SVX-C6).**  That is what this asked, and the window it waits
     * on is a whole `TrainControlUI`: a layout warning, a reconciliation report or anything else it
     * chooses to show would satisfy it, be asserted as the refusal, and then be DISPOSED by the caller
     * - which is a test that passes while proving nothing and closes something it did not open.
     *
     * Matched on the message rather than the title, because the title of a `showMessageDialog` comes
     * from the look and feel and the message is the sentence the guard was given.  Read in the same
     * language the application is running in, so this does not quietly become an English-only test.
     *
     * @param ceilingMs how long to wait
     * @return the dialog showing the too-many-pages refusal
     */
    private static java.awt.Window waitForDialog(long ceilingMs) throws Exception
    {
        long deadline = System.currentTimeMillis() + ceilingMs;

        String refusal = org.traincontrol.util.I18n.f("page.ui.errorTooManyPages",
            TrainControlUI.MAX_LOC_MAPPINGS);

        while (System.currentTimeMillis() < deadline)
        {
            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (w instanceof java.awt.Dialog && w.isShowing() && says(w, refusal)) return w;
            }

            Thread.sleep(50);
        }

        return null;
    }

    /**
     * Whether a dialog carries this sentence anywhere inside it.
     *
     * A `showMessageDialog` builds its own component tree - a JOptionPane holding a label - so the
     * text is found by walking it rather than by asking the window.
     *
     * @param inside the window
     * @param sentence what the guard says
     * @return whether it is in there
     */
    private static boolean says(java.awt.Component inside, String sentence)
    {
        // THE OPTION PANE'S OWN MESSAGE FIRST.  `showMessageDialog` keeps the sentence as the pane's
        // message and renders it into labels that may be wrapped, split or marked up, so comparing
        // the rendered text is a test of the look and feel rather than of the guard.
        if (inside instanceof javax.swing.JOptionPane
            && sentence.equals(String.valueOf(((javax.swing.JOptionPane) inside).getMessage())))
        {
            return true;
        }

        if (inside instanceof javax.swing.JLabel
            && ((javax.swing.JLabel) inside).getText() != null
            && ((javax.swing.JLabel) inside).getText().contains(sentence))
        {
            return true;
        }

        if (inside instanceof java.awt.Container)
        {
            for (java.awt.Component child : ((java.awt.Container) inside).getComponents())
            {
                if (says(child, sentence)) return true;
            }
        }

        return false;
    }

    /**
     * How many mapping pages the window has now, through the door the rest of the program uses.
     *
     * NOT BY REFLECTION (SVX-C6).  This was `getDeclaredField("numLocMappings")` throwing
     * `SkipException` on a miss, and it is called from `@BeforeClass` - so renaming the field would
     * have skipped the whole class from its own setup and the suite would have read green, with
     * nothing about the ceiling asked at all.  `test-green-is-not-no-failures`.
     *
     * A public accessor for the count already existed.  What has no public door is SETTING it, and
     * `setPages` says what it does about that.
     *
     * @return the count the window would act on
     */
    private static int pages()
    {
        return ui.getNumLocMappings();
    }

    /**
     * Sets the window's page count without touching the stored preference.
     *
     * The preference is user-wide and outlives the JVM, so a test that writes it can leave Adam's
     * window opening with whatever the test happened to need - which is what happened, at fifty. The
     * count is a field; the ceiling rules read the field; so the field is all this needs.
     */
    private static void setPages(int to)
    {
        try
        {
            Field count = TrainControlUI.class.getDeclaredField("numLocMappings");

            count.setAccessible(true);

            count.set(ui, to);
        }
        catch (ReflectiveOperationException e)
        {
            // FAIL, NOT SKIP (SVX-C6).
            //
            // This is not a fixture that has moved - it is the only way this class reaches the
            // ceiling, so a miss means the claim below it is not being asked.  Skipping would report
            // that as green.  If `numLocMappings` is ever renamed, this sentence is the instruction:
            // set the count some other way, do not delete the claim.
            throw new AssertionError("this class sets the page count by reflection because nothing"
                + " public does it, and the field it needs is gone: " + e + ". The ceiling claim"
                + " cannot be asked without it - give the window a way to set the count, or point"
                + " this at the new field. Do not let it skip: the mutation it guards against is one"
                + " that lets a person add a fifty-first mapping page.");
        }
    }
}
