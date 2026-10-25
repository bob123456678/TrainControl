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
 * The locomotive mapping pages stop at fifty, in the method as well as in the menu (FR-033, MT-383; Adam: "too much
 * manual effort.  add an automated test for this.").
 *
 * Both halves: `canAddLocMappingPage` is what the menu grays itself on, and `addLocMappingPage` refuses again on its
 * own account (its javadoc: "A guard that lives only in the interface is a guard the next caller does not have.").  NOT
 * asserted: that loading cannot exceed fifty.  `setViewListener` grows the count past the ceiling on purpose, since a
 * saved state can hold more pages than the preference knows about and clamping would throw away their mappings.
 *
 * The window is built through `LayoutSandbox` (OB-111), so Adam's own railway is never opened.
 *
 * @author Adam
 */
public class testFiftyLocomotiveMappingPages
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    private static int pagesWere;

    /** What the user-wide store said before this class ran; -1 when it said nothing. */
    private static int storedWas;

    /** Builds the window on a throwaway layout. */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the mapping pages live on a window");
        }

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        pagesWere = pages();

        // AND WHAT THE STORE SAYS, because the passing path is not the only path: under the mutation this class
        // documents, `addLocMappingPage` succeeds and writes 51 to the store before the assertion can object.
        storedWas = TrainControlUI.getPrefs()
            .getInt(TrainControlUI.LOC_MAPPING_PAGES_PREF, -1);
    }

    /**
     * Puts the page count back, and the layout preference with it: the pages are a PREFERENCE, so leaving fifty behind
     * would change what Adam's window opens with.
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

                // AND THE STORE GOES BACK TO WHAT IT SAID, whether or not this class changed it: `alwaysRun`, so a
                // failed or mutated run undoes it too.
                java.util.prefs.Preferences store =
                    TrainControlUI.getPrefs();

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
     */
    @Test(timeOut = 120000)
    public void testTheFiftiethPageIsTheLast() throws Exception
    {
        final int ceiling = TrainControlUI.MAX_LOC_MAPPINGS;

        // AT THE CEILING BY REFLECTION, NOT BY FILLING: `addLocMappingPage` writes the page count into the user-wide
        // Java Preferences on every successful add, and filling to fifty once left fifty in Adam's own settings.
        // Setting the count directly touches no preference.  A deliberately broken run (the guard deleted) can still
        // leave 51 behind.
        setPages(ceiling);

        assertEquals(pages(), ceiling,
            "the page count could not be set to the ceiling, so nothing below is being asked at it");

        // THE AFFORDANCE: what the menu grays itself on.
        assertFalse(ui.canAddLocMappingPage(),
            "at " + ceiling + " pages the window still says another may be added, so the menu item"
            + " that asks this stays live at the ceiling (FR-033)");

        // AND THE GUARD, a separate rule and not the menu's manners, called directly: the count must not move.  ON A
        // DAEMON THREAD, because the refusal is a MODAL DIALOG and invokeAndWait would deadlock in its loop.
        int atTheCeiling = pages();

        Thread refusing = new Thread(() -> ui.addLocMappingPage());

        refusing.setDaemon(true);

        refusing.start();

        // The dialog is the refusal, so it is asserted rather than merely dismissed: a guard that returns quietly would
        // pass a count check.
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
     * The refusal dialog, identified by what it says, or null if it does not appear in time.  NOT "the first showing
     * dialog": another warning would satisfy it and then be DISPOSED by the caller.  Matched on the message, read in
     * the language the application runs in, since the title comes from the look and feel.
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

    /** Whether a dialog carries this sentence anywhere inside it, found by walking its component tree. */
    private static boolean says(java.awt.Component inside, String sentence)
    {
        // THE OPTION PANE'S OWN MESSAGE FIRST: the rendered labels may be wrapped, split or marked up.
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
     * How many mapping pages the window has now, through the door the rest of the program uses, NOT BY REFLECTION: a
     * renamed field would skip the whole class from `@BeforeClass` and the suite would read green.
     */
    private static int pages()
    {
        return ui.getNumLocMappings();
    }

    /** Sets the window's page count without touching the stored preference, which is user-wide and outlives the JVM. */
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
            // FAIL, NOT SKIP: this is the only way the class reaches the ceiling, so a miss means the claim is not
            // being asked.  If `numLocMappings` is renamed, set the count some other way; do not delete the claim.
            throw new AssertionError("this class sets the page count by reflection because nothing"
                + " public does it, and the field it needs is gone: " + e + ". The ceiling claim"
                + " cannot be asked without it - give the window a way to set the count, or point"
                + " this at the new field. Do not let it skip: the mutation it guards against is one"
                + " that lets a person add a fifty-first mapping page.");
        }
    }
}
