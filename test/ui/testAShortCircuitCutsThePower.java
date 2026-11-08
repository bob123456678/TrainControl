package ui;

import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Packets;

/**
 * A short circuit: the station cuts the track power by itself and says only Overload, never Stop (OB-426, seen live
 * on a CS3 after a derailment).  TrainControl shows the power off and warns, once per cut.
 */
public class testAShortCircuitCutsThePower
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the warning is a window, and a window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));
        model = support.TestStation.simulatedWithWindowInDebug();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Overload on output 3 turns the power off and warns, naming the output; a second Overload while it is off, as a
     * lasting short sends, is logged without another warning; Go turns the power back on.
     * MUTATION: take `CMD_SYSSUB_OVERLOAD` out of the system-frame test in `MarklinControlStation.receiveMessage`; the
     * power stays on.  Or drop `wasOn &&` before `showPowerCut`; the second Overload warns again.
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
            assertEquals(warning.title, I18n.t("ui.powerCutTitle"));
            assertEquals(warning.message, I18n.f("ui.powerCut", 3), "the warning must name the output that tripped");

            model.receiveMessage(Packets.overload(3));

            assertFalse(answering.awaitAsked(2, 2000), "a repeated Overload while the power is off warned again");
        }

        model.receiveMessage(Packets.powerGo());
        assertTrue(Components.waitFor(model::getPowerState, 5000), "Go after a short circuit must show the power on");
    }
}
