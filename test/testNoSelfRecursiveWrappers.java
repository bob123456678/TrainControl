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

        if (!source.isFile()) return;

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
                assertFalse(withoutComments(line).contains("this." + name + "("),
                    name + " calls this." + name + "(...) from inside its own body.  A wrapper that "
                    + "forwards to the model must call this.model." + name + "(...) - calling itself "
                    + "is an infinite recursion the compiler accepts, the battery does not notice, and "
                    + "the user meets as a StackOverflowError.  Line: " + line.trim());
            }
        }

        assertTrue(checked > 0,
            "no forwarding wrappers were found at all, so this test is checking nothing - the shape it "
            + "looks for has probably changed");
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
     */
    private static List<String> bodyOf(List<String> lines, int declaredAt)
    {
        List<String> body = new ArrayList<>();

        int depth = 0;
        boolean started = false;

        for (int i = declaredAt; i < lines.size(); i++)
        {
            String line = lines.get(i);

            for (char c : line.toCharArray())
            {
                if (c == '{')
                {
                    depth++;
                    started = true;
                }
                else if (c == '}')
                {
                    depth--;
                }
            }

            if (started) body.add(line);

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
