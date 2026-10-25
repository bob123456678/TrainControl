package support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The program's source, as text, for the tests that read it - found by what a declaration says, never by which file
 * holds it.
 *
 * A source-reading claim used to open one named file, `indexOf` a signature and cut up to `"\n    }"`.  Moved to
 * another file, the declaration was then "not found" (a loud red) or, worse, a negative claim such as
 * `assertFalse(ui.contains("void autoLoadOff("))` stayed green over a file the code had left (FR-136).  Here a
 * declaration is looked for in every `.java` file under `src/`, must be there exactly once, and a negative claim asks
 * {@link #allCode()}, which still holds the code wherever it moves.
 *
 * The rules every method shares:
 * <ul>
 * <li>Line endings are `\n`, whatever the file uses: a line break is not part of any rule.</li>
 * <li>A signature is matched in the code only, so a comment that quotes it is not a second declaration; the text
 * handed back is the source as written, comments included.</li>
 * <li>A miss or a second match fails the calling claim with an {@link AssertionError} naming the signature and, for a
 * second match, every file and line.</li>
 * </ul>
 *
 * Tests run from the project root, as every runner here does, so `src/` is relative to it.
 */
public final class SourceOf
{
    private static final File SRC = new File("src");

    /** Each `.java` file under `src/`, by its path from the project root with `/` separators, in path order. */
    private static Map<String, String> files;

    /** The same files with their comments blanked, offset for offset. */
    private static Map<String, String> code;

    private static String all;

    private static String allCode;

    private SourceOf()
    {
    }

    /**
     * The declaration that starts with this signature, from its first character to the brace that closes its body
     * (or the `;` that ends a field or an abstract method), wherever it is under `src/`.
     *
     * @param signature enough of the declaration to name it once, e.g. `"private void checkAutoLayoutLatency("`
     * @return the declaration's text
     */
    public static String declaration(String signature)
    {
        Found found = find(signature);

        return found.text.substring(found.at, endOf(found.code, found.at));
    }

    /**
     * What is between the braces of the declaration that starts with this signature: its body without the signature,
     * so a claim about what the body calls cannot be met by the declaration's own name or parameters.
     *
     * @param signature enough of the declaration to name it once
     * @return the text after its opening brace and before its closing one
     */
    public static String body(String signature)
    {
        Found found = find(signature);

        int end = endOf(found.code, found.at);

        int open = openingOf(found.code, found.at);

        if (open < 0 || open >= end)
        {
            throw new AssertionError("`" + signature + "` in " + found.path + " has no body: it ends at a `;`");
        }

        return found.text.substring(open + 1, end - 1);
    }

    /**
     * The whole text of the one file that declares this signature, for a claim that compares places in the file
     * holding it (the order of two calls, say).  A claim about one declaration asks {@link #declaration} instead,
     * which does not care where its neighbors went.
     *
     * @param signature enough of a declaration to name it once
     * @return the file's text
     */
    public static String fileDeclaring(String signature)
    {
        return find(signature).text;
    }

    /**
     * The declaration that starts with this signature in text a claim already holds - a body, say - by the same rules
     * as {@link #declaration}: once, in the code, to its closing brace.
     *
     * @param text the text to look in
     * @param signature enough of the declaration to name it once there
     * @return the declaration's text
     */
    public static String declarationIn(String text, String signature)
    {
        text = text.replace("\r\n", "\n");

        String stripped = withoutComments(text);

        List<Integer> at = occurrencesIn(stripped, signature.replace("\r\n", "\n"));

        if (at.size() != 1)
        {
            throw new AssertionError("`" + signature + "` is in the code of the text given " + at.size()
                + " times, not once");
        }

        return text.substring(at.get(0), endOf(stripped, at.get(0)));
    }

    /**
     * Every `.java` file under `src/`, one after another, comments and all - for a claim that something is said
     * nowhere in the program.  Usually {@link #allCode()} is the one wanted: a comment elsewhere may well name a call
     * that the code no longer makes.
     *
     * @return the text of every source file
     */
    public static synchronized String all()
    {
        if (all == null) all = String.join("\n", files().values());

        return all;
    }

    /**
     * {@link #all()} with every comment blanked: for a claim that the program does something nowhere, or somewhere.
     *
     * @return the code of every source file
     */
    public static synchronized String allCode()
    {
        if (allCode == null) allCode = String.join("\n", code().values());

        return allCode;
    }

    /**
     * The code of every `.java` file under one folder of `src/`, its subfolders included, comments blanked and joined
     * as {@link #allCode()} joins the whole program: for a claim about a layer rather than a class - "nothing in the
     * automation package names the dialog" - which a class split out of the layer's biggest file cannot escape.
     *
     * @param folder the folder from the project root, e.g. `"src/org/traincontrol/automation/"`; a missing trailing
     *     `/` is added, so `automation` never takes in `automationui`
     * @return the code of every source file under it, in path order
     */
    public static String codeUnder(String folder)
    {
        String prefix = folder.replace('\\', '/');

        if (!prefix.endsWith("/")) prefix += "/";

        List<String> under = new ArrayList<>();

        for (Map.Entry<String, String> each : code().entrySet())
        {
            if (each.getKey().startsWith(prefix)) under.add(each.getValue());
        }

        if (under.isEmpty())
        {
            throw new AssertionError("there is no .java file under " + prefix + " - moved or renamed, so the claim"
                + " that reads it needs the new folder rather than passing over nothing");
        }

        return String.join("\n", under);
    }

    /**
     * How many times this text is in the program's code (comments blanked), counted without overlaps - for a claim
     * such as "exactly one door calls it" or "nothing calls it any more".
     *
     * @param text the text to count
     * @return the number of places, 0 when none
     */
    public static int occurrences(String text)
    {
        return occurrencesIn(allCode(), text.replace("\r\n", "\n")).size();
    }

    /**
     * The text with every comment blanked to spaces, keeping its line breaks, string literals and length, so an
     * offset in one is the same place in the other.
     *
     * @param source Java source
     * @return the same text with `//` and block comments as spaces
     */
    public static String withoutComments(String source)
    {
        char[] out = source.toCharArray();

        int n = out.length;

        int i = 0;

        while (i < n)
        {
            char c = out[i];

            char next = i + 1 < n ? out[i + 1] : '\0';

            if (c == '"' || c == '\'')
            {
                i = pastLiteral(out, i);
            }
            else if (c == '/' && next == '/')
            {
                while (i < n && out[i] != '\n')
                {
                    if (out[i] != '\r') out[i] = ' ';

                    i++;
                }
            }
            else if (c == '/' && next == '*')
            {
                int end = source.indexOf("*/", i + 2);

                end = end < 0 ? n : end + 2;

                for (; i < end; i++)
                {
                    if (out[i] != '\n' && out[i] != '\r') out[i] = ' ';
                }
            }
            else
            {
                i++;
            }
        }

        return new String(out);
    }

    /** Where a signature was found: its file, that file's text and code, and the offset. */
    private static final class Found
    {
        final String path;

        final String text;

        final String code;

        final int at;

        Found(String path, String text, String code, int at)
        {
            this.path = path;
            this.text = text;
            this.code = code;
            this.at = at;
        }
    }

    private static Found find(String signature)
    {
        signature = signature.replace("\r\n", "\n");

        List<String> where = new ArrayList<>();

        Found found = null;

        for (Map.Entry<String, String> each : code().entrySet())
        {
            for (int at : occurrencesIn(each.getValue(), signature))
            {
                String text = files().get(each.getKey());

                found = new Found(each.getKey(), text, each.getValue(), at);

                where.add(each.getKey() + ":" + lineOf(text, at));
            }
        }

        if (where.isEmpty())
        {
            throw new AssertionError("`" + signature + "` is declared nowhere under src/ - renamed or removed, so the"
                + " claim that reads it needs its new name rather than deleting");
        }

        if (where.size() > 1)
        {
            throw new AssertionError("`" + signature + "` is in the code " + where.size() + " times, at " + where
                + " - give enough of the declaration to name one");
        }

        return found;
    }

    private static List<Integer> occurrencesIn(String haystack, String needle)
    {
        List<Integer> at = new ArrayList<>();

        if (needle.isEmpty()) throw new IllegalArgumentException("an empty text is everywhere");

        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length()))
        {
            at.add(i);
        }

        return at;
    }

    /** The offset just past the declaration starting at `at`, in code whose comments are blanked. */
    private static int endOf(String code, int at)
    {
        int open = openingOf(code, at);

        if (open < 0)
        {
            int semicolon = firstAtDepthZero(code, at, ';');

            if (semicolon < 0) throw new AssertionError("the declaration at offset " + at + " never ends");

            return semicolon + 1;
        }

        char[] chars = code.toCharArray();

        int depth = 0;

        for (int i = open; i < chars.length; )
        {
            char c = chars[i];

            if (c == '"' || c == '\'')
            {
                i = pastLiteral(chars, i);

                continue;
            }

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i + 1;

            i++;
        }

        throw new AssertionError("the body of the declaration at offset " + at + " does not close");
    }

    /** The declaration's opening brace, or -1 when a `;` ends it first. */
    private static int openingOf(String code, int at)
    {
        int brace = firstAtDepthZero(code, at, '{');

        int semicolon = firstAtDepthZero(code, at, ';');

        if (brace < 0) return -1;

        return semicolon >= 0 && semicolon < brace ? -1 : brace;
    }

    /** The first `wanted` outside parentheses and literals, from `at`. */
    private static int firstAtDepthZero(String code, int at, char wanted)
    {
        char[] chars = code.toCharArray();

        int parens = 0;

        for (int i = at; i < chars.length; )
        {
            char c = chars[i];

            if (c == '"' || c == '\'')
            {
                i = pastLiteral(chars, i);

                continue;
            }

            if (c == '(') parens++;
            else if (c == ')') parens--;
            else if (c == wanted && parens <= 0) return i;

            i++;
        }

        return -1;
    }

    /** The offset just past the string or char literal opening at `at`. */
    private static int pastLiteral(char[] chars, int at)
    {
        char quote = chars[at];

        int i = at + 1;

        while (i < chars.length && chars[i] != quote && chars[i] != '\n')
        {
            if (chars[i] == '\\') i++;

            i++;
        }

        return Math.min(i + 1, chars.length);
    }

    private static int lineOf(String text, int at)
    {
        int line = 1;

        for (int i = 0; i < at; i++)
        {
            if (text.charAt(i) == '\n') line++;
        }

        return line;
    }

    private static synchronized Map<String, String> files()
    {
        if (files != null) return files;

        if (!SRC.isDirectory())
        {
            throw new AssertionError("cannot find " + SRC.getAbsolutePath() + " - a claim that reads the source runs"
                + " from the project root");
        }

        List<File> found = new ArrayList<>();

        collect(SRC, found);

        Map<String, String> read = new LinkedHashMap<>();

        List<String> paths = new ArrayList<>();

        Map<String, File> byPath = new LinkedHashMap<>();

        for (File each : found)
        {
            String path = "src/" + SRC.toURI().relativize(each.toURI()).getPath();

            paths.add(path);
            byPath.put(path, each);
        }

        Collections.sort(paths);

        for (String path : paths)
        {
            try
            {
                read.put(path, new String(Files.readAllBytes(byPath.get(path).toPath()), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n"));
            }
            catch (IOException e)
            {
                throw new AssertionError("cannot read " + path, e);
            }
        }

        files = Collections.unmodifiableMap(read);

        return files;
    }

    private static synchronized Map<String, String> code()
    {
        if (code != null) return code;

        Map<String, String> stripped = new LinkedHashMap<>();

        for (Map.Entry<String, String> each : files().entrySet())
        {
            stripped.put(each.getKey(), withoutComments(each.getValue()));
        }

        code = Collections.unmodifiableMap(stripped);

        return code;
    }

    private static void collect(File folder, List<File> into)
    {
        File[] children = folder.listFiles();

        if (children == null) return;

        for (File child : children)
        {
            if (child.isDirectory()) collect(child, into);
            else if (child.getName().endsWith(".java")) into.add(child);
        }
    }
}
