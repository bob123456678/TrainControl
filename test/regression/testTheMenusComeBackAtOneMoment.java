package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * OB-187: the menus stopped being grey in stages as the connect finished.
 *
 * Adam, in MT-264: *"Looks good, but when the loading finishes, the menu options ungrey at different
 * times."*
 *
 * The mechanism is that the bar is greyed BEFORE it is finished being built.  `showConnecting` walks
 * the menu bar and disables every menu on it, and `connectingFinished` gives back exactly those - one
 * list, one moment.  The autonomy menu is not on the bar when that walk happens: it is created and
 * added by `mountAutonomyControls`, from `setViewListener`, which runs during the connect the notice
 * is describing.  It arrived enabled, into a bar where everything else was grey, and so came back at
 * its own moment several seconds before the rest.
 *
 * This drives the real start-up order - grey the bar, hand the window its model - rather than calling
 * the mounting method directly, because "arrives after the walk" is the whole defect and only the
 * order can show it.
 *
 * MUTATION: take the notice's hold out of `AutonomyMenu.refreshEnabled`, or stop `ungreyTheMenus`
 * asking that menu what it should be, and this fails.
 *
 * @author Adam
 */
public class testTheMenusComeBackAtOneMoment
{
    /**
     * A menu added while the notice is up is grey with the rest, and comes back with the rest.
     */
    @Test
    public void testAMenuMountedDuringTheConnectWaitsForTheOthers() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("a menu bar is on a window");
        }

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        org.traincontrol.gui.TrainControlUI ui = null;

        try
        {
            // BEFORE the model (OB-111): `init` reads the machine-global layout preference and would
            // otherwise open Adam's real railway.
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            model.stop();

            final org.traincontrol.gui.TrainControlUI[] made =
                new org.traincontrol.gui.TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(
                () -> made[0] = new org.traincontrol.gui.TrainControlUI());

            ui = made[0];

            javax.swing.JMenuBar bar = ui.getJMenuBar();

            assertNotNull(bar, "the window has no menu bar, so this proves nothing");

            int before = bar.getMenuCount();

            assertTrue(before > 1, "one menu is not a menu bar");

            // WHICH SLOT THE AUTONOMY HEADING IS IN, and what is sitting in it now.
            //
            // The menu used to be APPENDED during the connect, so "a menu arrived after the walk" was
            // a count going up. OB-202 changed that on purpose: a placeholder holds the slot from the
            // first frame, because the heading growing in front of the operator part-way through
            // start-up was its own defect, and `mountAutonomyMenu` REPLACES it. The premise this test
            // rests on is unchanged - something arrives in that slot after the bar has been greyed -
            // so it is now read as identity rather than as a count.
            int slot = -1;

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                if (bar.getMenu(i) != null
                    && org.traincontrol.util.I18n.t("autosetup.ui.menuAutonomy")
                        .equals(bar.getMenu(i).getText()))
                {
                    slot = i;
                }
            }

            assertTrue(slot >= 0, "no autonomy heading on the bar before the connect, so OB-202's"
                + " placeholder is not holding the slot and this test cannot watch it being replaced");

            javax.swing.JMenu placeholder = bar.getMenu(slot);

            // THE NOTICE GOES UP.  `greyTheMenus` is what `showConnecting` does to the bar, and it is
            // separate from it precisely so a test can do this without putting a window on somebody's
            // screen.
            call(ui, "greyTheMenus");

            // AND THE CONNECT HAPPENS UNDER IT, which is where the autonomy menu is built and added.
            final org.traincontrol.marklin.MarklinControlStation connected = model;
            final org.traincontrol.gui.TrainControlUI window = ui;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    window.setViewListener(connected, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            assertEquals(bar.getMenuCount(), before,
                "the bar changed shape during the connect. OB-202's placeholder exists so that it"
                + " cannot - the heading must be in its slot from the first frame and stay there");

            assertNotSame(bar.getMenu(slot), placeholder,
                "nothing replaced the placeholder during the connect, so this test is not asking"
                + " about anything - OB-187 is about a menu that arrives after the bar has been"
                + " greyed, and OB-202 turned that arrival from an append into a replacement");

            // THE CONTROL.  A menu that ought to be off anyway would be grey here for a reason that
            // has nothing to do with the notice, and the assertion below would pass for a build with
            // no fix in it at all.
            assertTrue(ui.isLayoutLoaded() && ui.canUseAutonomy(),
                "the sandbox layout is not one autonomy can be set up for, so the autonomy menu is "
                + "switched off by its own rule and this test cannot see the notice's hold at all");

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                javax.swing.JMenu menu = bar.getMenu(i);

                if (menu == null) continue;

                assertFalse(menu.isEnabled(),
                    "the " + menu.getText() + " menu is live while the window is still saying it is "
                    + "connecting, so it stops being grey at its own moment and the bar settles in "
                    + "stages (OB-187)");
            }

            // AND THE ONE MOMENT.
            call(ui, "ungreyTheMenus");

            javax.swing.JMenu autonomy = named(bar, org.traincontrol.gui.AutonomyMenu.class);

            assertNotNull(autonomy, "the autonomy menu is not on the bar after the connect");

            assertTrue(autonomy.isEnabled(),
                "the autonomy menu did not come back when the notice came off, so holding it during "
                + "the connect has turned a menu that ungreyed early into one that never ungreys");

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                javax.swing.JMenu menu = bar.getMenu(i);

                if (menu == null || menu == autonomy) continue;

                assertTrue(menu.isEnabled(),
                    "the " + menu.getText() + " menu did not come back when the notice came off");
            }
        }
        finally
        {
            if (ui != null)
            {
                final org.traincontrol.gui.TrainControlUI open = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> open.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * "Combine linked pages" sits immediately after "Duplicate Current Page" (Adam, 2026-09-10).
     *
     * **`add()` appends.** The item is mounted in code - the menu bar is generated, so everything the
     * diagram work added to this window is hand-written - and appending put it at the BOTTOM of Manage
     * Pages: past the divider and past Delete, in the group the menu's own gaps say is about destroying
     * a page. Combining pages is the same kind of act as duplicating one.
     *
     * **Found by searching for the item rather than by index**, which is the reason `mountEditPageMenu`
     * gives about this same menu: an index is a fact about the generated form and moves the next time
     * somebody adds an item in the designer. It also has to survive two removals and a separator sweep
     * that shift every index below them at runtime, which is why the test drives the real start-up
     * rather than calling the mounting method on a fresh menu.
     *
     * **And the two adjacent claims are both needed.** "After Duplicate" alone passes for an item at the
     * bottom of a menu whose last entry happens to be Duplicate; "not last" alone passes for an item
     * anywhere in the middle. Together they say one position.
     *
     * MUTATION: put `modifyLocalLayoutMenu.add(combinePagesItem)` back in `addCombinePagesItem` and this
     * fails, naming where the item landed.
     */
    @Test
    public void testCombineSitsRightAfterDuplicateCurrentPage() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("a menu is on a window");
        }

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        org.traincontrol.gui.TrainControlUI ui = null;

        try
        {
            // BEFORE the model (OB-111).
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            model.stop();

            final org.traincontrol.gui.TrainControlUI[] made =
                new org.traincontrol.gui.TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(
                () -> made[0] = new org.traincontrol.gui.TrainControlUI());

            ui = made[0];

            final org.traincontrol.marklin.MarklinControlStation connected = model;
            final org.traincontrol.gui.TrainControlUI window = ui;

            // The real start-up, which is what mounts the item and then takes two others off the menu.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    window.setViewListener(connected, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            javax.swing.JMenu manage = field(ui, "modifyLocalLayoutMenu", javax.swing.JMenu.class);

            Object duplicate = field(ui, "duplicateLayoutMenuItem", Object.class);
            Object combine = field(ui, "combinePagesItem", Object.class);

            assertNotNull(manage, "the Manage Pages menu is not there, so this proves nothing");
            assertNotNull(duplicate, "Duplicate Current Page is not there");
            assertNotNull(combine, "Combine linked pages was never mounted, so its place cannot be "
                + "asserted - mountAutonomyControls is what adds it");

            int at = indexOf(manage, combine);
            int after = indexOf(manage, duplicate);

            assertTrue(at >= 0, "Combine linked pages is not on the Manage Pages menu at all");
            assertTrue(after >= 0, "Duplicate Current Page is not on the Manage Pages menu");

            assertEquals(at, after + 1,
                "Combine linked pages is at position " + at + " and Duplicate Current Page at "
                + after + ", so it is not the next item.  `add()` appends, which put it below the "
                + "divider and below Delete - in the group the menu's gaps say is about destroying a "
                + "page (Adam, 2026-09-10).  The menu holds: " + shapeOf(manage));

            // AND NOT LAST, which is the half "after Duplicate" cannot see on a menu whose last entry
            // is Duplicate.
            assertTrue(at < manage.getMenuComponentCount() - 1,
                "Combine linked pages is the last entry on the menu: " + shapeOf(manage));
        }
        finally
        {
            if (ui != null)
            {
                final org.traincontrol.gui.TrainControlUI closing = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A private field of the window, by name.
     */
    private static <T> T field(org.traincontrol.gui.TrainControlUI on, String name, Class<T> kind)
        throws Exception
    {
        java.lang.reflect.Field f =
            org.traincontrol.gui.TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return kind.cast(f.get(on));
    }

    /**
     * Where a component sits on a menu, or -1.
     */
    private static int indexOf(javax.swing.JMenu menu, Object what)
    {
        for (int i = 0; i < menu.getMenuComponentCount(); i++)
        {
            if (menu.getMenuComponent(i) == what) return i;
        }

        return -1;
    }

    /**
     * The menu's entries in order, for a failure message somebody can act on.
     */
    private static String shapeOf(javax.swing.JMenu menu)
    {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < menu.getMenuComponentCount(); i++)
        {
            java.awt.Component c = menu.getMenuComponent(i);

            if (out.length() > 0) out.append(" | ");

            if (c instanceof javax.swing.JSeparator) out.append("----");
            else if (c instanceof javax.swing.JMenuItem) out.append(((javax.swing.JMenuItem) c).getText());
            else out.append(c.getClass().getSimpleName());
        }

        return out.toString();
    }

    /**
     * The first menu on the bar of the given kind.
     *
     * By type rather than by title: the menu's text is translated, so a title would make this test
     * pass or fail on the operator's language.
     *
     * @param bar the menu bar to look in
     * @param kind the class of menu wanted
     * @return the menu, or null when the bar holds none of that kind
     */
    private static javax.swing.JMenu named(javax.swing.JMenuBar bar, Class<?> kind)
    {
        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            if (kind.isInstance(bar.getMenu(i))) return bar.getMenu(i);
        }

        return null;
    }

    /**
     * A private no-argument method of the window, by name, on the event thread.
     *
     * @param on the window
     * @param name the method to call
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
