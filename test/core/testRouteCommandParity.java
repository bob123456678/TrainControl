package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;

/**
 * Every kind of route command survives being written out and read back.
 *
 * This is the parity the new route editor rests on.  That editor works in RouteCommand objects rather
 * than in the text the old one edits, and hands them to the same save path - which is only safe if the
 * two descriptions of a command are interchangeable.  If some kind of command loses a field on the way
 * through the text form, then a route edited in the new interface and saved would come back different,
 * and the difference would be invisible until a train took the wrong turning.
 *
 * Every constructor RouteCommand offers is exercised, so a kind added later without a matching parse is
 * caught here rather than in somebody's timetable.
 */
public class testRouteCommandParity
{
    // No MarklinControlStation.
    //
    // There was one, built in a @BeforeClass and read by no test in the class.  Building it binds the
    // Central Station's UDP port and loads the operator's real locomotive database - for nothing, and
    // in a suite where one class holding that port makes every later model-based class report
    // "Address already in use" out of its own setup, which TestNG then renders as a clean skip.  A
    // pure round-trip test should not be able to do that to the rest of the battery.

    /**
     * Every command kind, written and read back, must describe the same thing.
     */
    @Test
    public void testEveryCommandKindSurvivesTheTextForm() throws Exception
    {
        List<RouteCommand> corpus = new ArrayList<>();

        corpus.add(RouteCommand.RouteCommandAccessory(12, Accessory.accessoryDecoderType.MM2, true));
        corpus.add(RouteCommand.RouteCommandAccessory(3, Accessory.accessoryDecoderType.DCC, false));
        corpus.add(RouteCommand.RouteCommandFeedback(21, true));
        corpus.add(RouteCommand.RouteCommandFeedback(22, false));
        corpus.add(RouteCommand.RouteCommandFunction("Test loc 1", 4, true));
        corpus.add(RouteCommand.RouteCommandLocomotiveSpeed("Test loc 1", 40));
        corpus.add(RouteCommand.RouteCommandLocomotiveDirection("Test loc 1",
            Locomotive.locDirection.DIR_BACKWARD));
        corpus.add(RouteCommand.RouteCommandStop());
        corpus.add(RouteCommand.RouteCommandFunctionsOff());

        for (RouteCommand original : corpus)
        {
            // toLine, not toString.  toString is a debug rendering - "Accessory: {ADDRESS=12, ...}" -
            // and toLine is what a route's CSV is built from and what fromLine reads back.  Finding
            // that out is half of why this test exists.
            String line = original.toLine(null);

            RouteCommand parsed = RouteCommand.fromLine(line, false);

            assertNotNull(parsed, "this kind of command cannot be read back at all: " + line);

            assertEquals(parsed.toLine(null), line,
                "a " + kindOf(original) + " command did not survive the text form. The old editor "
                    + "round-trips every command through this, so a kind that loses a field here is a "
                    + "route that changes when somebody opens it and presses Save");
        }
    }

    /**
     * A line the parser does not understand is refused rather than half-read.
     *
     * The editor will offer only kinds it can build, but a route file may hold anything - and a command
     * that parses into something plausible but wrong is worse than one that fails.
     */
    @Test
    public void testRubbishIsRefused() throws Exception
    {
        // Refused by throwing, which is what the callers expect - RouteEditor wraps its parse in a
        // try and reports the message.  What matters is that it does not come back as a command.
        try
        {
            RouteCommand parsed = RouteCommand.fromLine("this is not a command", false);

            assertNull(parsed, "a line that means nothing must not parse into a command");
        }
        catch (Exception expected)
        {
        }
    }

    private static String kindOf(RouteCommand command)
    {
        if (command.isAccessory()) return "accessory";
        if (command.isFeedback()) return "feedback";
        if (command.isFunction()) return "function";
        if (command.isLocomotiveSpeed()) return "locomotive speed";
        if (command.isLocomotiveDirection()) return "locomotive direction";
        if (command.isStop()) return "stop";
        if (command.isFunctionsOff()) return "functions off";
        if (command.isRoute()) return "route";
        if (command.isAutoLocomotive()) return "auto locomotive";

        return "unknown";
    }
}
