import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;

/**
 * A command taken apart into columns and put back together is the command it started as.
 *
 * This is what makes the new route editor safe to open a route in.  It shows each command as three
 * dropdowns, and if that trip loses anything then opening somebody's route and pressing Save would
 * quietly change what their railway does - the failure would be invisible until a train took the wrong
 * turning.
 *
 * The kinds with no controls yet must come back as null rather than as something approximate, so the
 * editor can keep them exactly as it found them.
 */
public class testCommandRow
{
    /**
     * Every kind the editor offers survives being shown as columns.
     */
    @Test
    public void testEveryEditableKindRoundTrips()
    {
        List<RouteCommand> corpus = Arrays.asList(
            RouteCommand.RouteCommandAccessory(12, Accessory.accessoryDecoderType.MM2, true),
            RouteCommand.RouteCommandAccessory(3, Accessory.accessoryDecoderType.MM2, false),
            RouteCommand.RouteCommandFeedback(21, true),
            RouteCommand.RouteCommandFeedback(22, false),
            RouteCommand.RouteCommandFunction("BR 628", 4, true),
            RouteCommand.RouteCommandFunction("BR 628", 0, false),
            RouteCommand.RouteCommandLocomotiveSpeed("BR 628", 40),
            RouteCommand.RouteCommandLocomotiveSpeed("BR 628", 0),
            RouteCommand.RouteCommandLocomotiveDirection("BR 628", Locomotive.locDirection.DIR_FORWARD),
            RouteCommand.RouteCommandLocomotiveDirection("BR 628", Locomotive.locDirection.DIR_BACKWARD),
            RouteCommand.RouteCommandStop(),
            RouteCommand.RouteCommandFunctionsOff()
        );

        for (RouteCommand original : corpus)
        {
            CommandRow row = CommandRow.of(original);

            assertNotNull(row, "the editor claims to handle this kind but cannot show it: " + original);

            RouteCommand rebuilt = row.toCommand(Accessory.accessoryDecoderType.MM2);

            assertEquals(rebuilt.toLine(null), original.toLine(null),
                "a " + row.getKind() + " command changed by being shown as columns and put back.  A "
                + "route opened in the editor and saved unchanged would not be unchanged");
        }
    }

    /**
     * A kind with no controls comes back as null, so the editor keeps the original.
     */
    @Test
    public void testAKindWithNoControlsIsRefusedRatherThanApproximated()
    {
        assertNull(CommandRow.of(RouteCommand.RouteCommandAutonomyLightsOn()),
            "a kind the editor has no controls for must be refused, so it can be kept exactly as "
            + "found rather than turned into something that nearly means the same");
    }

    /**
     * A row that cannot be made into a command says which part is wrong.
     */
    @Test
    public void testABadRowSaysWhatIsWrongWithIt()
    {
        try
        {
            new CommandRow(CommandRow.Kind.ACCESSORY, "not a number", "turn")
                .toCommand(Accessory.accessoryDecoderType.MM2);

            fail("an address that is not a number must not save");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(String.valueOf(e.getMessage()).contains("address"),
                "the message should name the part that is wrong, got: " + e.getMessage());
        }

        try
        {
            new CommandRow(CommandRow.Kind.LOCOMOTIVE_SPEED, "", "40")
                .toCommand(Accessory.accessoryDecoderType.MM2);

            fail("a speed with no locomotive must not save");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(String.valueOf(e.getMessage()).contains("locomotive"),
                "the message should say a locomotive is missing, got: " + e.getMessage());
        }

        try
        {
            new CommandRow(CommandRow.Kind.FUNCTION, "BR 628", "on")
                .toCommand(Accessory.accessoryDecoderType.MM2);

            fail("a function with no number must not save");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(String.valueOf(e.getMessage()).contains("function"),
                "the message should say the function number is missing, got: " + e.getMessage());
        }
    }

    /**
     * The kinds with nothing to point at say so, so the editor can grey those cells.
     */
    @Test
    public void testTheKindsWithNoTargetSaySo()
    {
        assertFalse(CommandRow.hasTarget(CommandRow.Kind.STOP));
        assertFalse(CommandRow.hasSetting(CommandRow.Kind.FUNCTIONS_OFF));

        assertTrue(CommandRow.hasTarget(CommandRow.Kind.ACCESSORY));
        assertTrue(CommandRow.hasSetting(CommandRow.Kind.LOCOMOTIVE_SPEED));
    }

    /**
     * A list keeps its shape, with a null where a command has no controls.
     */
    @Test
    public void testAListKeepsItsPositions()
    {
        List<RouteCommand> commands = Arrays.asList(
            RouteCommand.RouteCommandFeedback(1, true),
            RouteCommand.RouteCommandAutonomyLightsOn(),
            RouteCommand.RouteCommandStop());

        List<CommandRow> rows = CommandRow.of(commands);

        assertEquals(rows.size(), 3, "a row per command, so positions still line up");
        assertNotNull(rows.get(0));
        assertNull(rows.get(1), "the kind with no controls is a hole, not a missing entry");
        assertNotNull(rows.get(2));
    }
}
