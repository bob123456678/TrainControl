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
        corpus.add(RouteCommand.RouteCommandRoute("Test route 1"));
        corpus.add(RouteCommand.RouteCommandAutoLocomotive("Test loc 1", 87));
        corpus.add(RouteCommand.RouteCommandAutonomyLightsOn());
        corpus.add(RouteCommand.RouteCommandLightsOn());

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
        // Refused by throwing, which is what the callers expect - RouteEditorFrame wraps its parse in a
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

    /**
     * The command list offers no kind that route execution would silently ignore (OB-141).
     *
     * Adam: "a route command should not be able to contain s88 sensors. these should only be in the
     * conditions only. remove s88 from the 'Kind' dropdown. advise what happens in the model when this
     * is selected (if anything)."
     *
     * **What happened in the model was nothing at all**, and that is the finding rather than the
     * dropdown. `MarklinRoute.execRoute` dispatches on a chain of `rc.isAccessory()` / `isStop()` /
     * `isFunctionsOff()` / `isAutonomyLightsOn()` / `isLightsOn()` / `isLocomotiveSpeed()` /
     * `isLocomotiveDirection()` / `isFunction()` / `isRoute()`, and the chain has no `isFeedback()`
     * branch and no final `else`. So a feedback row fell through every branch, sent nothing, and
     * logged nothing. Its one observable effect was the sleep the loop takes per row, which made an
     * s88 command a pure delay wearing the name of an instruction. It was still saved, re-read and
     * exported, so the route kept a sentence in it that could never be obeyed.
     *
     * The model already agreed with Adam: `RouteCommand.isConditionCommand()` has always answered true
     * for feedback. Only the dropdown disagreed.
     *
     * **The general rule, rather than the one case.** Offering a kind as a command is a promise that
     * executing it does something, so this checks the promise against the dispatch itself: every kind
     * `canBeACommand` allows must have a branch in `execRoute`. That is what would have caught this,
     * and it is what will catch the next one - including the reverse mistake of adding an `isFeedback`
     * branch later without re-enabling the dropdown, which fails this test with the opposite message.
     *
     * MUTATION: make `canBeACommand` return true for FEEDBACK again and this fails, naming it.
     */
    @Test
    public void testEveryKindOfferedAsACommandIsOneExecutionActsOn() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/marklin/MarklinRoute.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        // The dispatch only, so a mention in a comment or in the s88 TRIGGER machinery - which is a
        // different thing entirely and does use feedback - cannot be read as a branch.
        int from = source.indexOf("private void execRoute(boolean auto, int recursionLimit,");

        assertTrue(from >= 0, "execRoute has moved or changed shape - this test is reading nothing");

        // TO THE END OF THE METHOD, not to the end of the file (TSX-C12).
        //
        // `substring(from)` took `execRoute` and the 363 lines after it, and one of the predicates the
        // table below asks about already lives out there - `if (r.isAccessory())`, inside `toCSV`.
        // Nothing was vacuous, because ACCESSORY is both offered and dispatched; what was wrong is
        // that the bound the comment claims was not the bound the code took, and the two kinds that
        // are NOT offered were protected by nothing but their predicate happening to be absent from
        // unrelated methods.
        //
        // Walked on braces, which is what the method's own extent is.
        int opens = source.indexOf('{', from);

        assertTrue(opens > from, "execRoute has no body - this test is reading nothing");

        int depth = 0;
        int closes = -1;

        for (int i = opens; i < source.length(); i++)
        {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) { closes = i; break; }
        }

        assertTrue(closes > opens,
            "could not find the end of execRoute, so the extent this test reads is not a method");

        String dispatch = source.substring(from, closes);

        // Comments stripped, for the reason three findings this round were about: prose that describes
        // a branch reads exactly like the branch.
        dispatch = dispatch.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");

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
