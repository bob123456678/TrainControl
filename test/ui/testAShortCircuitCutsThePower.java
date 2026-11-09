package ui;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import javax.swing.JOptionPane;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Display;
import support.Packets;

/**
 * A short circuit: the station cuts the track power by itself and may say only Overload, never Stop (OB-426).
 * TrainControl shows the power off and warns, once per cut.
 */
public class testAShortCircuitCutsThePower
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Display.needed("the warning is a window, and a window needs a display");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));
        model = support.TestStation.simulatedWithWindowInDebug();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Overload on output 3 turns the power off and warns over the main window, naming the output; a second Overload, as
     * a lasting short sends, does not warn again; Go turns the power back on.
     * MUTATION: take `CMD_SYSSUB_OVERLOAD` out of the system-frame test in `MarklinControlStation.receiveMessage`; the
     * power stays on.  Or drop `!this.powerCutWarned &&` before `showPowerCut`; the second Overload warns again.
     */
    @Test
    public void testAShortCircuitTurnsThePowerOffAndWarnsOnce() throws Exception
    {
        model.go();
        assertTrue(Components.waitFor(model::getPowerState, 5000), "precondition: the power did not come on");

        try (Answering answering = Answering.start(message -> null))
        {
            model.receiveMessage(Packets.overload(3));

            assertTrue(Components.waitFor(() -> !model.getPowerState(), 5000),
                "an Overload from the station must show the power off");
            assertTrue(answering.awaitAsked(1, 10000), "an Overload from the station must warn");

            Answering.Question warning = answering.questions().get(0);
            assertEquals(warning.type, JOptionPane.WARNING_MESSAGE);
            assertTrue(warning.owner instanceof TrainControlUI, "the warning must hang off the main window");
            assertEquals(warning.title, I18n.t("ui.powerCutTitle"));
            assertEquals(warning.message, I18n.f("ui.powerCut", 3), "the warning must name the output that tripped");

            model.receiveMessage(Packets.overload(3));

            assertFalse(answering.awaitAsked(2, 2000), "a repeated Overload while the power is off warned again");
        }

        model.receiveMessage(Packets.powerGo());
        assertTrue(Components.waitFor(model::getPowerState, 5000), "Go after a short circuit must show the power on");
    }

    /**
     * A Stop is no short circuit and does not warn; an Overload after it, as a station may send them, still warns.
     * MUTATION: in `MarklinControlStation.receiveMessage`, call `showPowerCut` on every power-off, not only an
     * Overload; the Stop warns.  Or set `powerCutWarned` on a Stop too; the Overload after it never warns.
     */
    @Test
    public void testAStopDoesNotWarnButAnOverloadAfterItDoes() throws Exception
    {
        model.go();
        assertTrue(Components.waitFor(model::getPowerState, 5000), "precondition: the power did not come on");

        try (Answering answering = Answering.start(message -> null))
        {
            model.receiveMessage(Packets.powerStop());

            assertTrue(Components.waitFor(() -> !model.getPowerState(), 5000), "a Stop must show the power off");
            assertFalse(answering.awaitAsked(1, 2000), "a Stop warned of a short circuit");

            model.receiveMessage(Packets.overload(1));

            assertTrue(answering.awaitAsked(1, 10000), "an Overload after a Stop must still warn");
        }

        model.receiveMessage(Packets.powerGo());
        assertTrue(Components.waitFor(model::getPowerState, 5000), "Go must show the power on");
    }
}
