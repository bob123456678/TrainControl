package regression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.Util;
import support.Components;
import support.Packets;
import support.Restart;

/**
 * A class that restarts the program (`support.Restart`) leaves the run's saved data as it found it.  The classes after
 * it in a battery lane read the same data folder, and an armed route left there makes their Start ask a question
 * nobody answers.
 *
 * MUTATION: drop the `putBack()` call from `Restart.close`, and the saved database still holds the armed route.
 *
 * @author Adam
 */
public class testARestartLeavesTheRunsDataAsItFoundIt
{
    private static final String ROUTE = "RL armed route";

    private static final int SENSOR = 4796;

    @Test
    public void testTheSavedDataIsAsBeforeOnceTheRestartCloses() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the restart opens the window");

        Path locomotives = Paths.get(Util.dataPath(MarklinControlStation.DATA_FILE_NAME));
        Path window = Paths.get(Util.dataPath(TrainControlUI.DATA_FILE_NAME));

        byte[] locomotivesBefore = read(locomotives);
        byte[] windowBefore = read(window);

        Restart restart = Restart.open(new java.io.File("test/test_layout"));

        try
        {
            MarklinControlStation first = restart.getModel();

            first.receiveMessage(Packets.s88(SENSOR, false));

            assertTrue(Components.waitFor(() -> first.isFeedbackSet(Integer.toString(SENSOR)), 10000),
                "precondition: the route's sensor is not known");

            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(253, Accessory.accessoryDecoderType.MM2, true));

            assertTrue(first.newRoute(ROUTE, commands, SENSOR, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null),
                "precondition: the route was not made");

            MarklinControlStation second = restart.again();

            assertTrue(second.getRoute(ROUTE) != null && second.getRoute(ROUTE).isEnabled(),
                "precondition: the restart did not save the armed route, so there was nothing to leave behind");
        }
        finally
        {
            restart.close();
        }

        assertTrue(Arrays.equals(read(locomotives), locomotivesBefore),
            "the restart left the run's locomotive database changed, armed route and all, for the next class to read");

        assertTrue(Arrays.equals(read(window), windowBefore), "the restart left the run's window state changed");
    }

    /** The file's bytes, or null when there is none. */
    private static byte[] read(Path file) throws java.io.IOException
    {
        return Files.exists(file) ? Files.readAllBytes(file) : null;
    }
}
