package regression;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Preferences > Debug switches the echo of sent commands, in debug and simulation only (Adam, 2026-09-24).
 *
 * *"it would be handy to have a preference option (only when in debug+simulate mode) to turn on and off echoing, so we
 * don't need to recompile every time"* - then *"this could go in the preferences menu, under a debug heading visible only
 * in this mode"*, and *"I'd rather have debug be a non-bold heading with a submenu that has the options"*.  Echoing is
 * `MarklinControlStation.DEBUG_SIMULATE_PACKETS`: with it on, a simulation answers every command as a Central Station
 * would, which is what lets the window follow a throttle reversal there (MT-488).
 *
 * In a window started simulating and in debug, on a sandbox.  A test run is unattended, so the stored choice is neither
 * read into the flag nor written from it - a test must not switch echo on the operator's next launch.
 *
 * MUTATION: leave the checkbox out of the flag, or mount the menu whatever the mode, and a claim fails.
 *
 * @author Adam
 */
public class testTheDebugMenuSwitchesTheEcho
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static boolean echoWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the menu is part of the window, and the window needs a display");
        }

        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        // Simulating, with a window, in debug.
        model = init(null, true, true, false, true);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

        if (sandbox != null) sandbox.close();
    }

    /**
     * Preferences has a Debug submenu, in the ordinary face, holding the echo switch.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testPreferencesHasADebugSubmenu() throws Exception
    {
        JMenu debug = debugMenu();

        assertNotNull(debug, "in debug and simulation the Preferences menu has no Debug submenu.  Adam, 2026-09-24:"
            + " \"a preference option (only when in debug+simulate mode) to turn on and off echoing\"");

        assertFalse(debug.getFont().isBold(), "Debug is drawn bold - Adam: \"I'd rather have debug be a non-bold heading"
            + " with a submenu that has the options\"");

        assertNotNull(echoItem(debug), "the Debug submenu has no Echo Sent Commands");
    }

    /**
     * Ticking it switches echo on, and unticking switches it off.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testPreferencesHasADebugSubmenu")
    public void testTheSwitchTurnsTheEchoOnAndOff() throws Exception
    {
        final JCheckBoxMenuItem echo = echoItem(debugMenu());

        final boolean was = echo.isSelected();

        assertEquals(was, MarklinControlStation.DEBUG_SIMULATE_PACKETS, "the switch does not show whether echo is on");

        SwingUtilities.invokeAndWait(() -> echo.doClick());

        assertEquals(MarklinControlStation.DEBUG_SIMULATE_PACKETS, !was, "choosing Echo Sent Commands did not switch"
            + " echo " + (was ? "off" : "on"));

        SwingUtilities.invokeAndWait(() -> echo.doClick());

        assertEquals(MarklinControlStation.DEBUG_SIMULATE_PACKETS, was, "choosing it again did not switch echo back");
    }

    /**
     * Mounted only in debug and simulation, and the stored choice applied there - asked of the source, since this window
     * is in that mode and a second window in another is a second start in one JVM.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testItIsOfferedOnlyInDebugAndSimulation() throws Exception
    {
        String code = new String(Files.readAllBytes(Paths.get("src/org/traincontrol/gui/TrainControlUI.java")),
            StandardCharsets.UTF_8).replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\\r\\n]*", " ");

        int at = code.indexOf("private void mountDebugMenu()");

        assertTrue(at >= 0, "there is no mountDebugMenu");

        String body = code.substring(at, code.indexOf("\n    }", at));

        assertTrue(body.contains("isDebug()") && body.contains("isSimulation()"), "the Debug submenu is not held to"
            + " debug and simulation - \"visible only in this mode\": " + body);

        assertTrue(body.contains("ECHO_COMMANDS_PREF"), "the stored choice is not read when the menu is mounted, so it"
            + " has to be made again on every launch - \"so we don't need to recompile every time\"");
    }

    private static JMenu debugMenu() throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField("interfaceMenu");

        field.setAccessible(true);

        JMenu preferences = (JMenu) field.get(ui);

        String name = I18n.t("ui.main.toolbar.debug");

        for (int i = 0; i < preferences.getItemCount(); i++)
        {
            JMenuItem item = preferences.getItem(i);

            if (item instanceof JMenu && name.equals(item.getText())) return (JMenu) item;
        }

        return null;
    }

    private static JCheckBoxMenuItem echoItem(JMenu debug)
    {
        String name = I18n.t("ui.main.toolbar.echoCommands");

        for (int i = 0; i < debug.getItemCount(); i++)
        {
            JMenuItem item = debug.getItem(i);

            if (item instanceof JCheckBoxMenuItem && name.equals(item.getText())) return (JCheckBoxMenuItem) item;
        }

        return null;
    }
}
