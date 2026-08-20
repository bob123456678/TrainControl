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
     * A DCC accessory stays DCC.
     *
     * The one that got away.  The corpus above is twelve commands and every one of them is MM2, so the
     * editor's hardcoded "everything is MM2" was invisible to it.  MM2 and DCC are separate address
     * spaces - MarklinAccessory puts them at different UIDs - so this is not a switch that fails to
     * throw, it is a DIFFERENT switch throwing, or a phantom one being invented.
     */
    @Test
    public void testADccAccessoryDoesNotBecomeMm2()
    {
        RouteCommand original =
            RouteCommand.RouteCommandAccessory(3, Accessory.accessoryDecoderType.DCC, true);

        CommandRow row = CommandRow.of(original);

        assertEquals(row.getProtocol(), Accessory.accessoryDecoderType.DCC,
            "the row lost the decoder type, so the editor has nothing left to save it with");

        // Deliberately handed MM2, the way the editor's save does, to prove the ROW wins
        RouteCommand rebuilt = row.toCommand(Accessory.accessoryDecoderType.MM2);

        assertEquals(rebuilt.getProtocol(), Accessory.accessoryDecoderType.DCC,
            "a DCC accessory came back as MM2, which is a different physical decoder - the route now "
            + "throws the wrong turnout, or invents one that does not exist");

        assertEquals(rebuilt.toLine(null), original.toLine(null));
    }

    /**
     * A row the user built from scratch takes the protocol it is given.
     *
     * The other half of the same rule: carrying the original must not mean ignoring the caller, or a
     * new command could never be anything but the default.
     */
    @Test
    public void testANewRowTakesTheProtocolItIsGiven()
    {
        CommandRow fresh = new CommandRow(CommandRow.Kind.ACCESSORY, "9", "turn");

        assertEquals(fresh.toCommand(Accessory.accessoryDecoderType.DCC).getProtocol(),
            Accessory.accessoryDecoderType.DCC,
            "a row with no protocol of its own must take the editor's choice");
    }

    /**
     * Delays survive the trip, on every kind that can carry one.
     *
     * A delay is how a layout keeps a slow point motor from being overtaken by the next command, or
     * two motors from drawing at once.  Losing them all on one Save leaves a route that still lists
     * correctly, still runs, and fires everything at once.
     */
    @Test
    public void testDelaysSurvive()
    {
        List<RouteCommand> corpus = Arrays.asList(
            RouteCommand.RouteCommandAccessory(12, Accessory.accessoryDecoderType.MM2, true),
            RouteCommand.RouteCommandAccessory(4, Accessory.accessoryDecoderType.DCC, false),
            RouteCommand.RouteCommandLocomotiveSpeed("BR 628", 40),
            RouteCommand.RouteCommandLocomotiveDirection("BR 628", Locomotive.locDirection.DIR_BACKWARD),
            RouteCommand.RouteCommandFunction("BR 628", 4, true)
        );

        for (RouteCommand original : corpus)
        {
            original.setDelay(500);

            CommandRow row = CommandRow.of(original);

            assertEquals(row.getDelay(), 500,
                "the row lost the delay of a " + row.getKind() + " command");

            RouteCommand rebuilt = row.toCommand(Accessory.accessoryDecoderType.MM2);

            assertEquals(rebuilt.getDelay(), 500,
                "a " + row.getKind() + " command lost its delay on the way back.  The route still "
                + "lists and still runs - it just fires everything at once");

            assertEquals(rebuilt.toLine(null), original.toLine(null));
        }
    }

    /**
     * No delay stays the ABSENCE of a delay, rather than becoming a zero.
     *
     * RouteCommand treats those as different: toLine only writes a positive delay, so a command built
     * with an explicit zero stops equalling its own round trip.
     */
    @Test
    public void testNoDelayStaysNoDelay()
    {
        RouteCommand original = RouteCommand.RouteCommandAccessory(
            7, Accessory.accessoryDecoderType.MM2, true);

        RouteCommand rebuilt = CommandRow.of(original).toCommand(Accessory.accessoryDecoderType.MM2);

        assertEquals(rebuilt, original,
            "a command with no delay came back unequal to itself, so every unedited row would look "
            + "changed");
    }

    /**
     * A kind with no controls comes back as null, so the editor keeps the original.
     */
    @Test
    public void testEveryCommandTheModelCanExpressHasControls()
    {
        // This test used to say the opposite: that autonomy-lights-on had no controls and had to be
        // refused so it could be kept exactly as found.  That was true and is no longer - Adam asked
        // for the special commands the editor was missing, so all four of them (lights, autonomy
        // lights, triggering another route, and an auto-locomotive condition) now build.
        //
        // The invariant the old test protected is still worth having and still holds: a command the
        // editor cannot represent must come back as null so it is preserved untouched rather than
        // approximated into something that nearly means the same.  It just has no example left to
        // point at, because there is nothing the editor cannot represent - which is the point.
        assertNotNull(CommandRow.of(RouteCommand.RouteCommandAutonomyLightsOn()),
            "autonomy lights on is buildable now, so it must read back as a row");

        assertNotNull(CommandRow.of(RouteCommand.RouteCommandLightsOn()),
            "and so is lights on");

        assertNotNull(CommandRow.of(RouteCommand.RouteCommandRoute("some route")),
            "and triggering another route");

        assertNotNull(CommandRow.of(RouteCommand.RouteCommandAutoLocomotive("some train", 21)),
            "and the auto-locomotive condition, which is the one ConditionRows uses as its own "
            + "example - the editor could not build the row its documentation illustrates");
    }

    /**
     * A signal can be commanded in a signal's words.
     *
     * A signal and a switch are the same device - the type only decides which picture is drawn - so
     * the same two states carry two pairs of names, and Accessory has understood all four for as long
     * as it has existed. The editor understood two, so a route built against a signal in the words a
     * signal uses was refused at Save with a message about a vocabulary the user had no reason to
     * expect.
     */
    @Test
    public void testASignalMayBeCommandedInRedAndGreen()
    {
        RouteCommand red = new CommandRow(CommandRow.Kind.ACCESSORY, "12", "red").toCommand();
        RouteCommand turn = new CommandRow(CommandRow.Kind.ACCESSORY, "12", "turn").toCommand();

        assertEquals(red.getSetting(), turn.getSetting(),
            "red is a signal's word for what a switch calls turn - they are one state of one device");

        RouteCommand green = new CommandRow(CommandRow.Kind.ACCESSORY, "12", "green").toCommand();
        RouteCommand straight = new CommandRow(CommandRow.Kind.ACCESSORY, "12", "straight").toCommand();

        assertEquals(green.getSetting(), straight.getSetting(),
            "and green is straight");

        assertNotEquals(red.getSetting(), green.getSetting(),
            "and they are not the same state as each other, which would make the whole pair useless");
    }

    /**
     * A word that is none of the four is still refused.
     *
     * Widening what is accepted is exactly the change that turns a strict reader into one that
     * guesses, and a guess here throws a real switch.
     */
    @Test
    public void testAnInventedSettingIsStillRefused()
    {
        try
        {
            new CommandRow(CommandRow.Kind.ACCESSORY, "12", "sideways").toCommand();

            fail("a setting that is not one of the four names for the two states must be refused, "
                + "not coerced into whichever is nearer");
        }
        catch (IllegalArgumentException expected)
        {
            // what should happen
        }
    }

    /**
     * And each of them survives the trip back out again.
     *
     * Reading a command into a row is half of it; the half that loses data is writing it back. Both
     * of the data-loss bugs this editor has had were invisible until Save.
     */
    @Test
    public void testTheNewKindsRoundTrip()
    {
        assertTrue(CommandRow.of(RouteCommand.RouteCommandLightsOn()).toCommand().isLightsOn(),
            "lights on came back as something else");

        assertTrue(CommandRow.of(RouteCommand.RouteCommandAutonomyLightsOn())
            .toCommand().isAutonomyLightsOn(), "autonomy lights on came back as something else");

        RouteCommand route = CommandRow.of(RouteCommand.RouteCommandRoute("some route")).toCommand();

        assertTrue(route.isRoute(), "the route command came back as something else");
        assertEquals(route.getName(), "some route", "and it forgot which route it triggers");

        RouteCommand auto =
            CommandRow.of(RouteCommand.RouteCommandAutoLocomotive("some train", 21)).toCommand();

        assertTrue(auto.isAutoLocomotive(), "the auto-locomotive came back as something else");
        assertEquals(auto.getName(), "some train", "and it forgot which train");
        assertEquals(auto.getAddress(), 21, "and it forgot which sensor");
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

        // Was a null here, when autonomy-lights-on had no controls.  The positions rule is what this
        // test is about and it is unchanged: one entry per command, in order, so that a row's index is
        // the command's index - which is what lets a kept command be written back where it was found.
        assertNotNull(rows.get(1), "autonomy lights on is buildable now");
        assertNotNull(rows.get(2));
    }

    /**
     * A misspelt setting is refused, not guessed.
     *
     * The setting column is typed rather than chosen, and the reading used to be
     * "backward".equalsIgnoreCase(setting) ? BACKWARD : FORWARD - so "backwards", or "back", or a
     * mis-hit key, silently became FORWARD.  The table went on showing what the user typed, and the
     * locomotive ran the other way when the route fired.
     *
     * That is the exact failure RouteCommand.fromLine was hardened against, in a class whose purpose
     * is to take the syntax risk away.  An address typo was already refused with a dialog naming the
     * row; a direction typo has to be refused the same way.
     */
    @Test
    public void testAMisspeltSettingIsRefusedRatherThanGuessed()
    {
        String[][] typos = {
            {"LOCOMOTIVE_DIRECTION", "BR 628", "backwards"},
            {"LOCOMOTIVE_DIRECTION", "BR 628", "back"},
            {"ACCESSORY", "12", "turned"},
            {"FEEDBACK", "21", "yes"},
            {"FUNCTION", "BR 628", "4:enabled"},
        };

        for (String[] typo : typos)
        {
            CommandRow row = new CommandRow(
                CommandRow.Kind.valueOf(typo[0]), typo[1], typo[2]);

            try
            {
                row.toCommand(Accessory.accessoryDecoderType.MM2);

                fail("\"" + typo[2] + "\" was accepted as a " + typo[0] + " setting and quietly "
                    + "turned into whichever value the code defaults to.  A route saved this way does "
                    + "the opposite of what the row on screen says");
            }
            catch (IllegalArgumentException expected)
            {
                // The offending PART, which for a function is what follows the colon - naming
                // "enabled" rather than "4:enabled" points at the half that is wrong
                String offending = typo[2].contains(":")
                    ? typo[2].substring(typo[2].indexOf(':') + 1) : typo[2];

                assertTrue(expected.getMessage().contains(offending),
                    "the refusal must quote what was typed, so the user can see which cell is wrong: "
                    + expected.getMessage());
            }
        }
    }

    /**
     * And the spellings that ARE right still work, in either case.
     */
    @Test
    public void testTheRealSettingsAreStillAccepted()
    {
        assertEquals(new CommandRow(CommandRow.Kind.LOCOMOTIVE_DIRECTION, "BR 628", "BACKWARD")
            .toCommand(Accessory.accessoryDecoderType.MM2).getDirection(),
            Locomotive.locDirection.DIR_BACKWARD, "case must not matter");

        assertEquals(new CommandRow(CommandRow.Kind.LOCOMOTIVE_DIRECTION, "BR 628", "forward")
            .toCommand(Accessory.accessoryDecoderType.MM2).getDirection(),
            Locomotive.locDirection.DIR_FORWARD);

        assertTrue(new CommandRow(CommandRow.Kind.ACCESSORY, "12", "turn")
            .toCommand(Accessory.accessoryDecoderType.MM2).getSetting());

        assertFalse(new CommandRow(CommandRow.Kind.ACCESSORY, "12", "straight")
            .toCommand(Accessory.accessoryDecoderType.MM2).getSetting());
    }
}
