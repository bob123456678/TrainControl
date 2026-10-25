package core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Messages stay short: the number of English messages over the cap for their kind is pinned, and may only go down.
 *
 * Adam asked to "shorten error messages and tooltips across the app to be more concise", and approved these caps, in
 * words of the English value (HTML tags count as nothing, a placeholder as one word):
 * <ul>
 * <li>tooltip 15: the key's text is set by `setToolTipText(`.</li>
 * <li>banner 20: an editor banner, said through `say(` or `waitFor(`.</li>
 * <li>exception 25: the text of a `new ...Exception(` or `...Error(`, which can reach a popup.</li>
 * <li>popup 30: inside a `JOptionPane.show...` call, a confirmation included.</li>
 * <li>finding 30: a constant of AutonomyChecks, TileGraph or GraphReducer (a setup-check finding).</li>
 * <li>why 30: a line of the Why Not report, from LayoutExplanations or the `autolayout.why.` family.</li>
 * <li>menu 8: a menu item's or a window's name.</li>
 * <li>other 40: anything else shown, including a key built at run time and so found nowhere as a literal.</li>
 * </ul>
 * A key is placed by the innermost call around each place in `src/` that names it, directly, through a string constant,
 * or through a local variable read within the next few statements; the strictest cap among them is its cap.  A key only
 * ever logged is out of scope, and `route.ui.frameHelp` and `ui.aboutText` are exempt by name.
 *
 * To bank an improvement, lower the kind's number as the failure says; never raise one.  When a count rises, shorten
 * the message to its kind's shape (what happened and why, then what to do) rather than raising the number.
 */
public class testMessagesStayShort
{
    /** The kinds, strictest cap first, each with the words its messages may have. */
    enum Kind
    {
        MENU(8), TOOLTIP(15), BANNER(20), EXCEPTION(25), POPUP(30), FINDING(30), WHY(30), OTHER(40), LOG(-1);

        final int cap;

        Kind(int cap)
        {
            this.cap = cap;
        }

        String label()
        {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Keys over each kind's cap, as they stand.  Lower a number whenever keys come under the cap; never raise one. */
    private static final String[] OVER_BY_KIND = {
        "menu: 3",
        "tooltip: 5",
        "banner: 1",
        "exception: 0",
        "popup: 1",
        "finding: 36",
        "why: 6",
        "other: 11",
    };

    private static final Set<String> EXEMPT = new HashSet<>(Arrays.asList("route.ui.frameHelp", "ui.aboutText"));

    private static final File BUNDLE = new File("src/org/traincontrol/resources/messages.properties");

    /** The classes whose string constants are setup-check findings. */
    private static final Set<String> FINDING_CLASSES = new HashSet<>(Arrays.asList("AutonomyChecks.java",
        "TileGraph.java", "GraphReducer.java"));

    private static final Pattern MENU = Pattern.compile("new (?:javax\\.swing\\.)?J(?:Menu|MenuItem|CheckBoxMenuItem"
        + "|RadioButtonMenuItem)");
    private static final Pattern CONSTANT = Pattern.compile("\\bstatic\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"[^\"]*\"\\s*$"
        + "|\\bfinal\\s+static\\s+String\\s+(\\w+)\\s*=\\s*\"[^\"]*\"\\s*$");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final Pattern ASSIGNED = Pattern.compile("^\\s*(?:final\\s+)?(?:[\\w.<>\\[\\]]+\\s+)?(\\w+)\\s*=[^=]");

    /** How far past an assignment a local variable is looked for, in statements. */
    private static final int LOCAL_REACH = 12;

    @Test
    public void testNoMoreMessagesOverTheirCap() throws Exception
    {
        Map<Kind, List<String>> over = overTheirCaps();

        Map<Kind, Integer> pinned = pinned();

        List<String> rose = new ArrayList<>();

        StringBuilder offenders = new StringBuilder();

        List<String> fell = new ArrayList<>();

        for (Kind kind : Kind.values())
        {
            if (kind == Kind.LOG) continue;

            int now = over.get(kind).size(), was = pinned.get(kind);

            if (now > was)
            {
                rose.add(kind.label() + " (" + now + ", pinned " + was + ")");

                offenders.append("\n").append(kind.label()).append(" messages over ").append(kind.cap)
                    .append(" words, ").append(now - was).append(" more than pinned, among:");

                for (String each : over.get(kind)) offenders.append("\n  ").append(each);
            }
            else if (now < was)
            {
                fell.add("\"" + kind.label() + ": " + was + "\" to \"" + kind.label() + ": " + now + "\"");
            }
        }

        assertTrue(rose.isEmpty(), "messages over their cap rose: " + rose + ".  The new ones are listed below with the"
            + " kind's other offenders (git diff shows which are yours)." + offenders + "\nShorten the message to its"
            + " kind's shape - what happened and why, then what to do - and keep every fact the reader needs to act."
            + "  Do not raise OVER_BY_KIND.");

        assertTrue(fell.isEmpty(), "fewer messages are over their cap than OVER_BY_KIND records.  Lower it so the"
            + " improvement is kept: change " + fell);
    }

    /** The pinned counts, every kind but LOG named once. */
    private static Map<Kind, Integer> pinned()
    {
        Map<Kind, Integer> pinned = new EnumMap<>(Kind.class);

        for (String entry : OVER_BY_KIND)
        {
            String[] parts = entry.split(": ");

            Kind kind = Kind.valueOf(parts[0].toUpperCase(java.util.Locale.ROOT));

            assertNull(pinned.put(kind, Integer.valueOf(parts[1])), "OVER_BY_KIND names " + kind.label() + " twice");
        }

        for (Kind kind : Kind.values())
        {
            assertTrue(kind == Kind.LOG || pinned.containsKey(kind), "OVER_BY_KIND does not name " + kind.label());
        }

        return pinned;
    }

    /**
     * @return for each kind, its keys over the cap, as "key (words/cap) at where", in key order
     */
    static Map<Kind, List<String>> overTheirCaps() throws IOException
    {
        Properties english = new Properties();

        assertTrue(BUNDLE.isFile(), "cannot find " + BUNDLE.getAbsolutePath() + " - this test reads the source, so it has"
            + " to run from the project root");

        try (InputStream in = new FileInputStream(BUNDLE))
        {
            english.load(in);
        }

        Map<String, Kind> placed = new HashMap<>();

        Map<String, String> where = new HashMap<>();

        place(english.stringPropertyNames(), placed, where);

        Map<Kind, List<String>> over = new EnumMap<>(Kind.class);

        for (Kind kind : Kind.values()) over.put(kind, new ArrayList<>());

        for (String key : new TreeSet<>(english.stringPropertyNames()))
        {
            if (EXEMPT.contains(key)) continue;

            Kind kind = placed.containsKey(key) ? placed.get(key) : Kind.OTHER;

            if (kind == Kind.LOG) continue;

            int words = words(english.getProperty(key));

            if (words > kind.cap)
            {
                over.get(kind).add(key + " (" + words + "/" + kind.cap + ")"
                    + (where.containsKey(key) ? " at " + where.get(key) : ""));
            }
        }

        return over;
    }

    /** Words as Adam's caps count them: tags are nothing, `{0}` is one. */
    static int words(String value)
    {
        String text = value.replaceAll("<[^>]*>", " ").trim();

        return text.isEmpty() ? 0 : text.split("\\s+").length;
    }

    /** One Java file: its text, and which characters are code rather than a literal or a comment. */
    private static final class Source
    {
        final String path;
        final String text;
        final boolean[] code;
        final List<int[]> literals = new ArrayList<>();
        final int[] newlines;

        Source(String path, String text)
        {
            this.path = path;
            this.text = text;
            this.code = new boolean[text.length()];

            List<Integer> breaks = new ArrayList<>();

            for (int at = text.indexOf('\n'); at >= 0; at = text.indexOf('\n', at + 1)) breaks.add(at);

            this.newlines = new int[breaks.size()];

            for (int k = 0; k < newlines.length; k++) newlines[k] = breaks.get(k);

            int i = 0, n = text.length();

            while (i < n)
            {
                char c = text.charAt(i), next = i + 1 < n ? text.charAt(i + 1) : '\0';

                if (c == '/' && next == '/')
                {
                    int end = text.indexOf('\n', i);

                    i = end < 0 ? n : end;
                }
                else if (c == '/' && next == '*')
                {
                    int end = text.indexOf("*/", i + 2);

                    i = end < 0 ? n : end + 2;
                }
                else if (c == '"' || c == '\'')
                {
                    int j = i + 1;

                    while (j < n && text.charAt(j) != c && text.charAt(j) != '\n')
                    {
                        if (text.charAt(j) == '\\') j++;

                        j++;
                    }

                    if (c == '"') literals.add(new int[] {i, Math.min(j + 1, n)});

                    i = Math.min(j + 1, n);
                }
                else
                {
                    code[i] = true;

                    i++;
                }
            }
        }

        /** The statement around `at`: from the code `;`, `{` or `}` before it to the code `;` after it. */
        int[] statement(int at)
        {
            int from = at, to = at;

            while (from > 0 && !(code[from - 1] && "{};".indexOf(text.charAt(from - 1)) >= 0)) from--;

            while (to < text.length() && !(code[to] && "{};".indexOf(text.charAt(to)) >= 0)) to++;

            return new int[] {from, to};
        }

        String line(int at)
        {
            int line = Arrays.binarySearch(newlines, at);

            return path + ":" + ((line < 0 ? -line - 1 : line) + 1);
        }

        String name()
        {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    /** Places every key that `src/` names, by the strictest kind among the statements that name it. */
    private static void place(Set<String> keys, Map<String, Kind> placed, Map<String, String> where) throws IOException
    {
        List<Source> sources = new ArrayList<>();

        List<File> files = new ArrayList<>();

        collect(new File("src"), files);

        Collections.sort(files);

        for (File file : files)
        {
            sources.add(new Source(file.getPath().replace(File.separatorChar, '/'),
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n")));
        }

        // A key held in a string constant is placed where the constant is used
        Map<String, String> constants = new TreeMap<>();

        for (Source source : sources)
        {
            for (int[] literal : source.literals)
            {
                String key = source.text.substring(literal[0] + 1, literal[1] - 1);

                if (!keys.contains(key)) continue;

                int[] statement = source.statement(literal[0]);

                String said = source.text.substring(statement[0], literal[1]);

                Matcher constant = CONSTANT.matcher(said);

                if (constant.find())
                {
                    if (FINDING_CLASSES.contains(source.name())) note(placed, where, key, Kind.FINDING, source.line(literal[0]));
                    else constants.put(constant.group(1) != null ? constant.group(1) : constant.group(2), key);

                    continue;
                }

                note(placed, where, key, kindAt(source, statement, literal[0], key), source.line(literal[0]));
            }
        }

        for (Source source : sources)
        {
            Matcher name = IDENTIFIER.matcher(source.text);

            while (name.find())
            {
                String key = constants.get(name.group());

                if (key == null || !source.code[name.start()]) continue;

                int[] statement = source.statement(name.start());

                if (CONSTANT.matcher(source.text.substring(statement[0], name.end()) + " = \"\"").find()) continue;

                note(placed, where, key, kindAt(source, statement, name.start(), key), source.line(name.start()));
            }
        }
    }

    /** The kind a key's place gives it, following a local variable it is assigned to a few statements on. */
    private static Kind kindAt(Source source, int[] statement, int at, String key)
    {
        Kind kind = kindOf(source, statement, at, key);

        Matcher assigned = ASSIGNED.matcher(source.text.substring(statement[0], statement[1]));

        if (kind != Kind.OTHER || !assigned.find()) return kind;

        Pattern read = Pattern.compile("\\b" + Pattern.quote(assigned.group(1)) + "\\b");

        int from = statement[1] + 1;

        for (int step = 0; step < LOCAL_REACH && from < source.text.length(); step++)
        {
            int[] next = source.statement(from);

            Matcher reads = read.matcher(source.text.substring(next[0], next[1]));

            while (reads.find())
            {
                if (!source.code[next[0] + reads.start()]) continue;

                Kind there = kindOf(source, next, next[0] + reads.start(), key);

                if (there.cap > 0 && (kind == Kind.OTHER || there.cap < kind.cap)) kind = there;
            }

            from = next[1] + 1;
        }

        return kind;
    }

    /**
     * The kind the innermost call around `at` that says one gives: a key passed to `say(` inside a lambda handed to
     * another method is that method's, not the banner's.
     */
    private static Kind kindOf(Source source, int[] statement, int at, String key)
    {
        int depth = 0;

        for (int i = at - 1; i >= statement[0]; i--)
        {
            if (!source.code[i]) continue;

            char c = source.text.charAt(i);

            if (c == ')') depth++;
            else if (c == '(' && depth > 0) depth--;
            else if (c == '(')
            {
                Kind kind = kindOfCall(callee(source.text, i));

                if (kind != null) return kind;
            }
        }

        if (source.name().equals("LayoutExplanations.java") || key.startsWith("autolayout.why.")) return Kind.WHY;

        return Kind.OTHER;
    }

    /** The name called at an opening parenthesis, with its qualifier and a `new` before it. */
    private static String callee(String text, int paren)
    {
        int end = paren;

        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;

        int start = end;

        while (start > 0 && (Character.isJavaIdentifierPart(text.charAt(start - 1)) || text.charAt(start - 1) == '.'))
        {
            start--;
        }

        int before = start;

        while (before > 0 && Character.isWhitespace(text.charAt(before - 1))) before--;

        boolean made = before >= 3 && text.startsWith("new", before - 3);

        return (made ? "new " : "") + text.substring(start, end);
    }

    private static Kind kindOfCall(String callee)
    {
        String name = callee.substring(callee.lastIndexOf('.') + 1);

        if (name.equals("setToolTipText")) return Kind.TOOLTIP;
        if (name.equals("say") || name.equals("waitFor")) return Kind.BANNER;
        if (callee.startsWith("new ") && (name.endsWith("Exception") || name.endsWith("Error"))) return Kind.EXCEPTION;
        if (callee.endsWith("JOptionPane." + name) && name.startsWith("show")) return Kind.POPUP;
        if (MENU.matcher(callee).matches() || name.equals("setTitle")) return Kind.MENU;
        if (name.equals("log") || name.equals("logf")) return Kind.LOG;

        return null;
    }

    /** The strictest kind wins; a log-only key stays out of scope until something shows it. */
    private static void note(Map<String, Kind> placed, Map<String, String> where, String key, Kind kind, String line)
    {
        Kind was = placed.get(key);

        boolean stricter = was == null || was == Kind.LOG
            || (kind != Kind.LOG && kind.cap < was.cap);

        if (!stricter) return;

        placed.put(key, kind);
        where.put(key, line);
    }

    private static void collect(File from, List<File> into)
    {
        File[] children = from.listFiles();

        if (children == null) return;

        for (File child : children)
        {
            if (child.isDirectory()) collect(child, into);
            else if (child.getName().endsWith(".java")) into.add(child);
        }
    }
}
