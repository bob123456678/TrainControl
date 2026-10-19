package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * A wrapper must not call itself.
 *
 * This exists because of a specific bug, and it is worth saying plainly what happened. Sixteen call
 * sites were mechanically rewritten from `this.model.syncWithCS2()` to `this.syncWithCS2()` so they
 * would go through a new wrapper - and the rewrite also caught the two calls INSIDE that wrapper. The
 * wrapper then called itself: off the event thread directly, and on it through a worker, which is also
 * off it. Every Central Station sync became a StackOverflowError.
 *
 * Nothing caught it. The suite tests the model's sync, which was the half still working; the compiler
 * has no opinion about recursion; and the battery was green. It took a reviewer reading the method.
 *
 * So this reads the source instead. That is an unusual thing for a test to do and it is the right tool
 * here: the fault is a textual one introduced by a textual edit, it is invisible to every other check,
 * and the shape - a method whose body's only call to that name is itself - is precise enough to test
 * without false alarms. It costs nothing and it covers a mistake that would otherwise be made again the
 * next time a delegating wrapper is introduced.
 */
public class testNoSelfRecursiveWrappers
{
    /**
     * The wrappers that delegate to the model, checked for delegating to themselves instead.
     *
     * Deliberately narrow. A general "no method may call itself" rule would be wrong - recursion is a
     * normal thing to write - so this looks only at methods on a UI class that share a name with a
     * model method they are supposed to be forwarding to.
     */
    @Test
    public void testAUiWrapperDoesNotCallItselfInsteadOfTheModel() throws Exception
    {
        File source = new File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(source.isFile(),
            "cannot find " + source.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);

        int checked = 0;

        for (int i = 0; i < lines.size(); i++)
        {
            String declaration = lines.get(i).trim();

            if (!declaration.startsWith("public ") || !declaration.contains("(")) continue;

            String name = nameOf(declaration);

            if (name == null || !forwardsToTheModel(lines, name)) continue;

            checked++;

            // Only within THIS method's body.  A name that exists on both the UI and the model is
            // ordinary - saveState is one - so what matters is whether the body calls the name it is
            // itself declaring, which is the recursion.
            for (String line : bodyOf(lines, i))
            {
                assertFalse(callsItself(line, name, argumentCount(
                        declaration.substring(declaration.indexOf('(') + 1,
                            declaration.lastIndexOf(')')))),
                    name + " calls itself from inside its own body instead of calling "
                    + "this.model." + name + "(...) - an infinite recursion the compiler accepts, the "
                    + "battery does not notice, and the user meets as a StackOverflowError.  Line: "
                    + line.trim());
            }
        }

        assertTrue(checked > 0,
            "no forwarding wrappers were found at all, so this test is checking nothing - the shape it "
            + "looks for has probably changed");
    }

    /**
     * The window's collaborators (FR-136): each field of TrainControlUI that holds one, the file it is declared in, and
     * its class name - for the static delegators, which reach it as `Class.method(` rather than through the field.
     */
    private static final String[][] COLLABORATORS = {
        { "backupAction", "src/org/traincontrol/gui/BackupAction.java", "BackupAction" },
        { "updateFlow", "src/org/traincontrol/gui/UpdateFlow.java", "UpdateFlow" },
        { "locIconActions", "src/org/traincontrol/gui/LocIconActions.java", "LocIconActions" },
        { "layoutFileActions", "src/org/traincontrol/gui/LayoutFileActions.java", "LayoutFileActions" },
        { "routeListPanel", "src/org/traincontrol/gui/RouteListPanel.java", "RouteListPanel" },
    };

    /**
     * A delegator and the collaborator it hands to do not call each other for ever (FR-136).
     *
     * The extractions leave a one-line delegator on the window wherever something outside a moved cluster calls it,
     * and the moved code calls back into the window through `ui`.  The same mechanical edit that made `syncWithCS2`
     * call itself can make a delegator call its own name instead of the collaborator's, or make the moved body call
     * the window's delegator that leads straight back to it.  Either is a StackOverflowError the compiler accepts.
     *
     * The static delegators (`askAboutTheUpdate`, `whenNoModalDialog`, `routeNamedIn`) hand over as `Class.method(` and
     * the collaborator calls the window's statics as `TrainControlUI.method(`, so both spellings are read as well.
     *
     * MUTATION: in BackupAction, make `writeTheBackup` call `this.ui.backupData()`, and this fails; so does making
     * UpdateFlow's static `whenNoModalDialog` call `TrainControlUI.whenNoModalDialog(`.
     */
    @Test
    public void testADelegatorAndItsCollaboratorDoNotCallEachOtherForEver() throws Exception
    {
        List<String> window = Files.readAllLines(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath(), StandardCharsets.UTF_8);

        for (String[] each : COLLABORATORS)
        {
            File source = new File(each[1]);

            assertTrue(source.isFile(), "cannot find " + source.getAbsolutePath());

            List<String> helper = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);

            // What each side calls on the other: the window through `this.<field>.` (or `Class.` for a static), the
            // collaborator through `ui.` (or `TrainControlUI.` for a static).
            java.util.Map<String, java.util.Set<String>> windowCalls = merged(
                callsThrough(window, "this." + each[0] + "."), callsThrough(window, each[2] + "."));
            java.util.Map<String, java.util.Set<String>> helperCalls = merged(
                callsThrough(helper, "ui."), callsThrough(helper, "TrainControlUI."));

            int checked = 0;

            for (java.util.Map.Entry<String, java.util.Set<String>> delegator : windowCalls.entrySet())
            {
                for (String handedTo : delegator.getValue())
                {
                    checked++;

                    java.util.Set<String> back = helperCalls.get(handedTo);

                    assertFalse(back != null && back.contains(delegator.getKey()),
                        "TrainControlUI." + delegator.getKey() + " hands to " + each[2] + "." + handedTo
                        + ", which calls back into TrainControlUI." + delegator.getKey() + " - a loop the compiler accepts");
                }
            }

            assertTrue(checked > 0, "TrainControlUI hands nothing to " + each[0] + ", so this checks nothing");

            // And a delegator that calls its own name, bare, beside the collaborator's.
            for (int i = 0; i < window.size(); i++)
            {
                String name = methodDeclaredAt(window.get(i));

                if (name == null) continue;

                String declaration = window.get(i).trim();

                // A declaration whose parameters run onto the next lines
                for (int k = i + 1; declaration.lastIndexOf(')') < declaration.indexOf('(') && k < window.size(); k++)
                {
                    declaration += " " + window.get(k).trim();
                }

                int arity = argumentCount(declaration.substring(declaration.indexOf('(') + 1,
                    declaration.lastIndexOf(')')));

                List<String> body = bodyOf(window, i);

                if (body.stream().noneMatch(line -> line.contains("this." + each[0] + "." + name + "(")
                    || line.contains(each[2] + "." + name + "("))) continue;

                for (String line : body)
                {
                    assertFalse(callsItself(line, name, arity), name + " calls itself instead of " + each[0]
                        + "." + name + ".  Line: " + line.trim());
                }
            }
        }
    }

    /**
     * Two maps of method name to the names it calls, as one.
     */
    private static java.util.Map<String, java.util.Set<String>> merged(java.util.Map<String, java.util.Set<String>> first,
        java.util.Map<String, java.util.Set<String>> second)
    {
        java.util.Map<String, java.util.Set<String>> both = new java.util.HashMap<>(first);

        second.forEach((name, called) -> both.computeIfAbsent(name, k -> new java.util.HashSet<>()).addAll(called));

        return both;
    }

    /**
     * For each method declared at the class's top level, the names it calls through a prefix.
     */
    private static java.util.Map<String, java.util.Set<String>> callsThrough(List<String> lines, String prefix)
    {
        java.util.Map<String, java.util.Set<String>> calls = new java.util.HashMap<>();

        java.util.regex.Pattern call = java.util.regex.Pattern.compile(
            "(?<!\\w)" + java.util.regex.Pattern.quote(prefix) + "(\\w+)\\s*\\(");

        for (int i = 0; i < lines.size(); i++)
        {
            String name = methodDeclaredAt(lines.get(i));

            if (name == null) continue;

            for (String line : bodyOf(lines, i))
            {
                java.util.regex.Matcher m = call.matcher(withoutComments(line));

                while (m.find())
                {
                    calls.computeIfAbsent(name, k -> new java.util.HashSet<>()).add(m.group(1));
                }
            }
        }

        return calls;
    }

    /**
     * The name of the method a line declares at a class's top level (four spaces in), or null.
     */
    private static String methodDeclaredAt(String line)
    {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "^    (?:(?:public|private|protected|static|final|synchronized)\\s+)*[\\w<>\\[\\],.?]+(?:<[^=]*>)?\\s+(\\w+)\\s*\\([^=;]*$")
            .matcher(line);

        if (!m.find()) return null;

        String name = m.group(1);

        return "new".equals(name) || "return".equals(name) ? null : name;
    }

    /**
     * Whether a line calls the wrapper's own name from inside its own body - qualified or not.
     *
     * The bug this file was written for was `this.syncWithCS2()`, and `this.` + name + `(` is what
     * caught it - but the mechanical rewrite that produced it is exactly the kind of edit that also
     * produces the UNQUALIFIED spelling, `syncWithCS2()`, with no `this.` in front at all - inside a
     * lambda, for instance, or after a second mechanical pass tidies the first one's `this.` away.  The
     * substring check alone cannot see that spelling, so the regex below is checked as well: a bare
     * occurrence of the name, immediately preceded by neither a dot nor a word character, so that it
     * matches `-> ` + name + `(` and a plain `name(` but not `this.` + name + `(` a second time and
     * not `this.model.` + name + `(`, which is the call the wrapper is supposed to make.
     *
     * MUTATION this catches: change the recursive call this file already guards against from
     * `this.syncWithCS2();` to the unqualified `syncWithCS2();`.  Before this method existed, the
     * substring check on `"this." + name + "("` no longer matched and the test went green over the
     * same infinite recursion it was written to catch.
     */
    private static boolean callsItself(String line, String name, int arity)
    {
        String code = withoutComments(line);

        // THE SAME ARITY, or it is another overload rather than recursion.
        //
        // `saveState(boolean)` ends `saveState(backup, !backup);` - one overload handing to the other,
        // the ordinary Java idiom, and the two-argument form calls nothing at all. Reading the bare
        // name alone reported that as an infinite recursion in working code, which is worse than the
        // gap the bare-name check closed: saveState is the only overloaded method in this file\u2019s
        // scope, so this would have been red for ever and somebody would have deleted it.
        java.util.regex.Matcher call = java.util.regex.Pattern.compile(
            "(^|[^.\\w])" + java.util.regex.Pattern.quote(name) + "\\s*\\(([^)]*)\\)")
            .matcher(code);

        while (call.find())
        {
            if (argumentCount(call.group(2)) == arity) return true;
        }

        return false;
    }

    /**
     * How many arguments a call carries.
     *
     * Commas inside a nested call are not separators, so depth is tracked. Good enough for what this
     * has to tell apart - `name(a)` from `name(a, b)` - and the alternative is a Java parser.
     *
     * @param args the text between the brackets
     * @return the number of top-level arguments
     */
    private static int argumentCount(String args)
    {
        if (args.trim().isEmpty()) return 0;

        int depth = 0;
        int count = 1;

        for (char c : args.toCharArray())
        {
            if (c == '(' || c == '<') depth++;
            else if (c == ')' || c == '>') depth--;
            else if (c == ',' && depth == 0) count++;
        }

        return count;
    }

    /**
     * A line with any trailing comment removed.
     *
     * Necessary because the fix for the bug this test exists for is itself explained in a comment that
     * quotes the wrong call - so the comment describing the mistake would otherwise be read as the
     * mistake.  Code is what is checked; prose about code is not.
     */
    private static String withoutComments(String line)
    {
        int at = line.indexOf("//");

        return at < 0 ? line : line.substring(0, at);
    }

    /**
     * The lines of a method body, from its declaration to its closing brace.
     *
     * The signature itself is never included, even when its opening brace shares its line (K&R style,
     * against the Allman style this file mostly uses) - only what follows that brace is. Otherwise
     * `public void syncWithCS2() {` would be handed to `callsItself` as a line "calling" syncWithCS2,
     * since the bare-name check added for TST-B6 cannot otherwise tell a declaration's own name from a
     * call to it - both are just the name followed by `(`, preceded by a space.
     */
    private static List<String> bodyOf(List<String> lines, int declaredAt)
    {
        List<String> body = new ArrayList<>();

        int depth = 0;
        boolean started = false;

        for (int i = declaredAt; i < lines.size(); i++)
        {
            String line = lines.get(i);
            String forChecking = line;

            for (int c = 0; c < line.length(); c++)
            {
                char ch = line.charAt(c);

                if (ch == '{')
                {
                    if (!started) forChecking = line.substring(c + 1);

                    depth++;
                    started = true;
                }
                else if (ch == '}')
                {
                    depth--;
                }
            }

            if (started) body.add(forChecking);

            if (started && depth <= 0) break;
        }

        return body;
    }

    /**
     * The method name from a declaration line, or null when the line is not one.
     */
    private static String nameOf(String declaration)
    {
        int open = declaration.indexOf('(');

        if (open < 0) return null;

        String before = declaration.substring(0, open).trim();

        int space = before.lastIndexOf(' ');

        if (space < 0) return null;

        String name = before.substring(space + 1);

        return name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0)) ? null : name;
    }

    /**
     * Whether any line delegates this name to the model, which is what makes it a forwarding wrapper.
     */
    private static boolean forwardsToTheModel(List<String> lines, String name)
    {
        for (String line : lines)
        {
            if (line.contains("this.model." + name + "(")) return true;
        }

        return false;
    }
}
