package regression;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The Autonomy menu is in the bar from the first frame, and the real one takes its place.
 *
 * Adam, OB-202: *"while the hourglass is open, the autonomy jmenu isn't visible.  the sudden appearance
 * looks odd.  I added autonomyTopMenu, anchor your menu there, and grey it out if there is no autonomy."*
 *
 * The real menu is built from the configurations on disk and cannot exist until the connect is over, so
 * it used to be APPENDED to the bar from `setViewListener` - part-way through start-up, in front of the
 * operator. What is in the form now is a placeholder holding that slot, and `mountAutonomyMenu` swaps the
 * real menu into it.
 *
 * **So what this measures is that the bar never changes shape**: a menu with the right heading is there
 * before the mount and after it, at the same index, and the count does not move. Asserting only that the
 * placeholder exists would pass a version that appended the real menu beside it - two Autonomy menus,
 * which is a worse bug than the one being fixed.
 *
 * The window is built WITHOUT a model, which is what makes the "before" state reachable: `setViewListener`
 * is what mounts the real menu, and a test that called it would have nothing left to measure.
 *
 * @author Adam
 */
public class testTheAutonomyMenuHoldsItsSlot
{
    private static support.LayoutSandbox sandbox;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("a menu bar needs a window");
        }

        // The window follows a saved preference and would otherwise open the operator's own railway.
        sandbox = support.LayoutSandbox.open();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui = new TrainControlUI();
            }
            catch (Exception cannotStart)
            {
                throw new RuntimeException(cannotStart);
            }
        });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null) javax.swing.SwingUtilities.invokeAndWait(() -> ui.dispose());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Before anything mounts a real menu, the bar already carries the heading - greyed.
     *
     * MUTATION: taking the `setText` out of the constructor leaves the form's hard-coded "Autonomy",
     * which passes an English run and fails every other language - so this compares against the bundle
     * rather than against the word.
     */
    @Test
    public void testTheSlotIsHeldBeforeAnythingIsMounted() throws Exception
    {
        javax.swing.JMenu anchor = autonomyMenuInTheBar();

        assertNotNull(anchor, "there is no Autonomy menu in the bar before the mount, so the bar grows a"
            + " heading part-way through start-up - which is what OB-202 is: \"the sudden appearance"
            + " looks odd\".  The bar holds: " + headings());

        assertFalse(anchor.isEnabled(),
            "the Autonomy menu is enabled on a window with no model behind it, so it opens onto a list"
            + " of things none of which can be done");

        assertNotNull(anchor.getToolTipText(),
            "the Autonomy menu is greyed and says nothing about why.  A menu that is off for a reason"
            + " nobody can read teaches less than one that is missing");
    }

    /**
     * Mounting the real menu changes the bar's contents, not its shape.
     *
     * The claim that would fail on the version this replaces - which appended - and on a version that
     * forgot to remove the placeholder.
     */
    @Test
    public void testTheRealMenuTakesTheSlotRatherThanASecondOne() throws Exception
    {
        final int[] before = new int[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            before[0] = ui.getJMenuBar().getMenuCount();
            before[1] = indexOfAutonomy();
        });

        assertTrue(before[1] >= 0, "no Autonomy menu to begin with, so this measures nothing");

        javax.swing.SwingUtilities.invokeAndWait(() -> ui.mountAutonomyMenu());

        final int[] after = new int[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            after[0] = ui.getJMenuBar().getMenuCount();
            after[1] = indexOfAutonomy();
        });

        assertEquals(after[0], before[0],
            "the menu bar has " + after[0] + " menus after mounting the autonomy menu and had "
            + before[0] + " before, so the real menu was ADDED beside the placeholder instead of"
            + " taking its place.  The bar holds: " + headings());

        assertEquals(after[1], before[1],
            "the Autonomy menu moved from index " + before[1] + " to " + after[1] + " when the real"
            + " one was mounted, so the headings shuffle along in front of the operator - which is the"
            + " half of OB-202 that is about the bar changing shape while it is being looked at");

        assertEquals(countAutonomyMenus(), 1,
            "there are now " + countAutonomyMenus() + " menus in the bar carrying the autonomy"
            + " heading, so the placeholder was left in beside the real one: " + headings());

        // AND IT IS THE REAL ONE, not the placeholder with a new name.
        javax.swing.SwingUtilities.invokeAndWait(() ->
            before[0] = autonomyMenuInTheBar() instanceof org.traincontrol.gui.AutonomyMenu ? 1 : 0);

        assertEquals(before[0], 1,
            "the menu in the autonomy slot is still the placeholder after mounting, so nothing was"
            + " swapped and the menu opens onto nothing");
    }

    /**
     * Whatever is standing in the slot answers the window's own question about being usable.
     *
     * The two askers are the placeholder and the real menu, and the point of `autonomyMenuIsUsable` is
     * that there is one answer rather than two copies of a three-part condition.
     *
     * MUTATION: giving either asker its own copy of the condition fails this as soon as the two drift.
     */
    @Test
    public void testTheSlotAgreesWithTheWindowAboutBeingUsable() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> ui.refreshAutonomyAnchor());

        javax.swing.JMenu anchor = autonomyMenuInTheBar();

        assertNotNull(anchor, "nothing in the autonomy slot to ask");

        assertEquals(anchor.isEnabled(), ui.autonomyMenuIsUsable(),
            "the menu in the autonomy slot is " + (anchor.isEnabled() ? "enabled" : "disabled")
            + " and the window says it is " + (ui.autonomyMenuIsUsable() ? "usable" : "not usable")
            + ".  They are meant to be one question - `autonomyMenuIsUsable` - so a disagreement is a"
            + " second copy of the condition having grown back");

        assertEquals(anchor.getToolTipText() == null, ui.whyAutonomyIsUnavailable() == null,
            "the slot " + (anchor.getToolTipText() == null ? "says nothing" : "gives a reason")
            + " while the window " + (ui.whyAutonomyIsUnavailable() == null ? "has none" : "has one"));
    }

    /**
     * The menu in the bar whose heading is the autonomy one, or null.
     */
    private static javax.swing.JMenu autonomyMenuInTheBar()
    {
        int at = indexOfAutonomy();

        return at < 0 ? null : ui.getJMenuBar().getMenu(at);
    }

    private static int indexOfAutonomy()
    {
        javax.swing.JMenuBar bar = ui.getJMenuBar();

        String heading = I18n.t("autosetup.ui.menuAutonomy");

        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            if (bar.getMenu(i) != null && heading.equals(bar.getMenu(i).getText())) return i;
        }

        return -1;
    }

    private static int countAutonomyMenus()
    {
        javax.swing.JMenuBar bar = ui.getJMenuBar();

        String heading = I18n.t("autosetup.ui.menuAutonomy");

        int found = 0;

        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            if (bar.getMenu(i) != null && heading.equals(bar.getMenu(i).getText())) found++;
        }

        return found;
    }

    /**
     * What the bar says, for a message somebody can act on.
     */
    private static String headings()
    {
        javax.swing.JMenuBar bar = ui.getJMenuBar();

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            out.append(i == 0 ? "" : ", ")
                .append(bar.getMenu(i) == null ? "null" : bar.getMenu(i).getText());
        }

        return out.toString();
    }
}
