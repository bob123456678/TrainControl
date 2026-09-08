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

            assertTrue(bar.getMenuCount() > before,
                "no menu was added to the bar during the connect, so this test is not asking about "
                + "anything - OB-187 is about a menu that arrives after the bar has been greyed");

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
