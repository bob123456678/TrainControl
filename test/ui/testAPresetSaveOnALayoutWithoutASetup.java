package ui;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Components;
import support.Keys;
import support.Mouse;
import support.SettingsWindow;
import support.WindowField;

/**
 * Save Current Speed as Preset writes the setup's copy of the train too (MT-912); on a layout with no autonomy setup
 * it writes none, from the key menu and Alt+U, for a moving train and one at rest.  On the "one-page" layout, which has
 * no setup folder.
 */
public class testAPresetSaveOnALayoutWithoutASetup
{
    private static final String NAME = "PN train";
    private static final long WAIT = 8000;

    private static support.SettingsFixture fixture;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static MarklinLocomotive loc;
    private static JButton key;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        fixture = support.SettingsFixture.open(support.Scenario.folderFor("one-page"));

        model = fixture.model;
        ui = fixture.ui;

        model.setNetworkCommState(false);

        fixture.forget(NAME);

        loc = model.newMFXLocomotive(NAME, 3601);
        key = SettingsWindow.showOnKeyA(ui, loc);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (fixture != null)
            {
                SettingsWindow.power(ui, model, false);
                fixture.forget(NAME);
                fixture.shutDown();
            }
        }
        finally
        {
            if (fixture != null) fixture.closeSandbox();
        }
    }

    /**
     * No setup folder before, and none after the key menu's save and Alt+U, with the train moving and at rest.
     *
     * MUTATION: in `TrainControlUI.recordInTheSetup`, take the session from `getAutonomySession()` and save it whether
     * or not a record changed, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAPresetSaveWritesNoSetup() throws Exception
    {
        final java.io.File setup = new java.io.File(fixture.sandbox.getFolder(), "config" + java.io.File.separator + "autonomy");

        assertFalse(setup.exists(), "precondition: this layout has an autonomy setup");
        assertNull(ui.getActiveDiagramConfiguration(), "precondition: a setup is loaded");

        int speed = 50;

        for (boolean moving : new boolean[] { true, false })
        {
            SettingsWindow.power(ui, model, moving);

            try
            {
                loc.setSpeed(++speed);

                final int menu = speed;

                Mouse.choose((JMenuItem) Components.withText(SettingsWindow.rightClickKey(key),
                    I18n.t("loc.ui.menuSaveSpeedAsPreset")));

                assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == menu, WAIT), "the key menu did not save the speed");

                loc.setSpeed(++speed);

                final int altU = speed;

                Keys.press(WindowField.<Component>of(ui, "KeyboardTab"), KeyEvent.VK_U, InputEvent.ALT_DOWN_MASK);

                assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == altU, WAIT), "Alt+U did not save the speed");

                Components.settle();

                assertFalse(setup.exists(), "a preset save " + (moving ? "for a moving train" : "at rest") + " wrote a setup");
            }
            finally
            {
                loc.setSpeed(0);

                SettingsWindow.power(ui, model, false);
            }
        }
    }
}
