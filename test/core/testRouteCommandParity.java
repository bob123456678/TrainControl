package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;
import support.SourceOf;

/**
 * Every kind of route command survives being written out and read back: the new route editor works in RouteCommand
 * objects and hands them to the same save path, which is only safe if the text form loses no field.  Every constructor
 * RouteCommand offers is exercised, so a kind added without a matching parse is caught here.
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

    /** Every command kind, written and read back, must describe the same thing. */
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
        corpus.add(RouteCommand.RouteCommandRoute("Test route 1"));
        corpus.add(RouteCommand.RouteCommandAutoLocomotive("Test loc 1", 87));
        corpus.add(RouteCommand.RouteCommandAutoLocomotive("Test loc 1", 88, false));
        corpus.add(RouteCommand.RouteCommandAutonomyLightsOn());
        corpus.add(RouteCommand.RouteCommandLightsOn());

        for (RouteCommand original : corpus)
        {
            // toLine, not toString: toString is a debug rendering, toLine is what a route's CSV is built from and
            // fromLine reads back.
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
     * A line the parser does not understand is refused rather than half-read: a route file may hold anything, and a
     * plausible but wrong command is worse than a failure.
     */
    @Test
    public void testRubbishIsRefused() throws Exception
    {
        // Refused by throwing, which RouteEditorFrame's parse expects; what matters is that it does not come back as a
        // command.
        try
        {
            RouteCommand parsed = RouteCommand.fromLine("this is not a command", false);

            assertNull(parsed, "a line that means nothing must not parse into a command");
        }
        catch (Exception expected)
        {
        }
    }

    /**
     * The command list offers no kind that route execution would silently ignore (OB-141; Adam: "a route command should
     * not be able to contain s88 sensors. these should only be in the conditions only.").  `MarklinRoute.execRoute` has
     * no `isFeedback()` branch and no final `else`, so a feedback row sent nothing, logged nothing and was a pure
     * delay.  The general rule: every kind `canBeACommand` allows must have a branch in `execRoute`, which also catches
     * adding an `isFeedback` branch without re-enabling the dropdown.
     *
     * MUTATION: make `canBeACommand` return true for FEEDBACK again and this fails, naming it.
     */
    @Test
    public void testEveryKindOfferedAsACommandIsOneExecutionActsOn() throws Exception
    {
        // The dispatch only, to the end of the method (walked on braces: SourceOf cuts the declaration at its closing
        // brace), and comments stripped, so a mention in prose or in the s88 TRIGGER machinery cannot read as a branch.
        String dispatch = SourceOf.withoutComments(SourceOf.declaration(
            "private void execRoute(boolean auto, int recursionLimit,"));

        // Kind -> the RouteCommand predicate execRoute would have to test to act on it.
        String[][] handledBy =
        {
            { "ACCESSORY",           "isAccessory()"          },
            { "SIGNAL",              "isAccessory()"          },
            { "THREE_WAY",           "isAccessory()"          },
            { "FEEDBACK",            "isFeedback()"           },
            { "FUNCTION",            "isFunction()"           },
            { "LOCOMOTIVE_SPEED",    "isLocomotiveSpeed()"    },
            { "LOCOMOTIVE_DIRECTION","isLocomotiveDirection()"},
            { "STOP",                "isStop()"               },
            { "FUNCTIONS_OFF",       "isFunctionsOff()"       },
            { "LIGHTS_ON",           "isLightsOn()"           },
            { "AUTONOMY_LIGHTS_ON",  "isAutonomyLightsOn()"   },
            { "ROUTE",               "isRoute()"              },
            { "AUTO_LOCOMOTIVE",     "isAutoLocomotive()"     },
            { "AUTO_LOCOMOTIVE_NOT_AT", "isAutoLocomotive()"  },
        };

        assertEquals(handledBy.length, org.traincontrol.base.CommandRow.Kind.values().length,
            "a kind has been added or removed and this table was not updated, so the sweep below is "
            + "no longer over every kind there is");

        StringBuilder offeredButIgnored = new StringBuilder();
        StringBuilder actedOnButHidden = new StringBuilder();

        for (String[] pair : handledBy)
        {
            org.traincontrol.base.CommandRow.Kind kind =
                org.traincontrol.base.CommandRow.Kind.valueOf(pair[0]);

            boolean offered = org.traincontrol.base.CommandRow.canBeACommand(kind);
            boolean executed = dispatch.contains(pair[1]);

            if (offered && !executed)
            {
                offeredButIgnored.append("\n  ").append(pair[0])
                    .append(" is offered in the command dropdown, but execRoute has no ")
                    .append(pair[1]).append(" branch, so a row of this kind does nothing at all");
            }

            if (!offered && executed)
            {
                actedOnButHidden.append("\n  ").append(pair[0])
                    .append(" has an ").append(pair[1])
                    .append(" branch in execRoute but cannot be added from the dropdown");
            }
        }

        assertEquals(offeredButIgnored.toString(), "",
            "the route editor offers a command kind that route execution ignores. A row like this is "
            + "saved, re-read and exported, and carries out nothing - all it costs is the loop's sleep, "
            + "so it is a delay wearing the name of an instruction (OB-141):" + offeredButIgnored);

        assertEquals(actedOnButHidden.toString(), "",
            "route execution acts on a command kind the editor will not let anybody add. If a branch "
            + "was added for one of these, canBeACommand should be letting it back into the dropdown:"
            + actedOnButHidden);
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
        if (command.isAutonomyLightsOn()) return "autonomy lights on";
        if (command.isLightsOn()) return "lights on";

        return "unknown";
    }
}
