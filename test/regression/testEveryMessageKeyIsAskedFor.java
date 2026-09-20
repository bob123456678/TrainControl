package regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Nothing is left in the message bundles that no screen asks for.
 *
 * **Eight files, translated, maintained - and 118 of the lines were the text of windows that left the
 * build** (UIX-C4). `GraphViewer`'s menus, `GraphEdgeEdit`'s labels, `GraphLocExclude`'s lists and the
 * old route editor's commands were all still there in all eight languages, and the live keys sat among
 * them: somebody reading the bundle to find out what a screen says could not tell which half was the
 * screen. Nothing at runtime cost anything - that is why they survived four releases.
 *
 * **What counts as asking for a key.** The exact key spelled as a string literal anywhere in `src/` or
 * `test/`, or a literal that is a PROPER PREFIX of it - because the code builds keys by concatenation
 * in at least five places (`route.kind.`, `autosetup.ui.side`, `autosetup.ui.facing`, and the two path
 * preferences), and a rule that knew only those five would report every builder written after it as
 * dead text and invite somebody to delete a live line. The prefix rule costs precision - a key is
 * shielded by any literal it happens to start with - and it buys never being wrong in the direction
 * that breaks a window.
 *
 * **The floors are what stop this reporting a clean bill of health about nothing.** A regex that
 * silently stopped matching literals would call every key dead, which is loud; one that matched
 * everything would call every key live, which is silent. `testItReadTheBundleAndTheSource` is the
 * guard against the quiet half.
 *
 * **The way past.** A key added before the screen that will use it is a key this reports. That is what
 * `EXPECTED` is for: raise it, with the key named in a comment, and lower it when the screen lands.
 *
 * @author Adam
 */
public class testEveryMessageKeyIsAskedFor
{
    /**
     * The bundle the other seven are translations of.
     */
    private static final String BUNDLE = "src/org/traincontrol/resources/messages.properties";

    /**
     * How many unreferenced keys are allowed to stand, and why.
     *
     * Zero since 2026-09-19, when the 118 the deleted windows left behind came out.
     */
    private static final int EXPECTED = 0;

    /**
     * A Java string literal, escapes and all.
     */
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    /**
     * This file, which is not allowed to shield a key: it prints the dead ones in its own failure
     * message, and a key named there would otherwise look asked-for on the next run.
     */
    private static final String MYSELF = "testEveryMessageKeyIsAskedFor.java";

    /**
     * Every key in the bundle is spelled somewhere in the source.
     *
     * @throws IOException from the files
     */
    @Test
    public void testNoKeyIsLeftBehindByADeletedWindow() throws IOException
    {
        List<String> dead = deadKeys();

        assertTrue(dead.size() <= EXPECTED,
            dead.size() + " message keys are in all eight bundles and nothing asks for them, against "
            + EXPECTED + " allowed.  Either the screen that used them has gone - in which case the "
            + "lines should go with it, in all eight files - or a screen is about to use them, in "
            + "which case raise EXPECTED and name them: " + dead);
    }

    /**
     * The reader found a bundle and a source tree, rather than quietly finding nothing.
     *
     * @throws IOException from the files
     */
    @Test
    public void testItReadTheBundleAndTheSource() throws IOException
    {
        int keys = keysOf(new File(BUNDLE)).size();
        int literals = literals().size();

        assertTrue(keys > 1800,
            "only " + keys + " keys were read out of " + BUNDLE + "; the bundle has over two thousand, "
            + "so the reader is broken and everything else this class says is about nothing");

        assertTrue(literals > 15000,
            "only " + literals + " string literals were found in src/ and test/; there are over twenty "
            + "thousand, and a literal reader that has stopped matching makes every key look dead");
    }

    /**
     * The keys nothing asks for.
     *
     * @return the dead keys, in bundle order
     * @throws IOException from the files
     */
    private static List<String> deadKeys() throws IOException
    {
        Set<String> literals = literals();
        List<String> prefixes = new ArrayList<>();

        for (String literal : literals)
        {
            if (literal.length() >= 6 && literal.contains(".")) prefixes.add(literal);
        }

        List<String> dead = new ArrayList<>();

        for (String key : keysOf(new File(BUNDLE)))
        {
            if (literals.contains(key)) continue;

            boolean built = false;

            for (String prefix : prefixes)
            {
                if (key.startsWith(prefix) && !key.equals(prefix))
                {
                    built = true;

                    break;
                }
            }

            if (!built) dead.add(key);
        }

        return dead;
    }

    /**
     * The keys of a bundle.
     *
     * @param bundle the file
     * @return its keys
     * @throws IOException from the file
     */
    private static List<String> keysOf(File bundle) throws IOException
    {
        List<String> keys = new ArrayList<>();

        for (String line : Files.readAllLines(bundle.toPath(), StandardCharsets.ISO_8859_1))
        {
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;

            keys.add(line.substring(0, line.indexOf('=')));
        }

        return keys;
    }

    /**
     * Every string literal in `src/` and `test/`, this class's own file excepted.
     *
     * @return the literals
     * @throws IOException from the files
     */
    private static Set<String> literals() throws IOException
    {
        Set<String> out = new LinkedHashSet<>();

        for (String where : new String[] {"src", "test"})
        {
            collect(new File(where), out);
        }

        return out;
    }

    /**
     * Reads one folder's Java files into the literal set.
     *
     * @param folder where to look
     * @param out the literals found so far
     * @throws IOException from the files
     */
    private static void collect(File folder, Set<String> out) throws IOException
    {
        File[] found = folder.listFiles();

        if (found == null) return;

        for (File file : found)
        {
            if (file.isDirectory())
            {
                collect(file, out);

                continue;
            }

            if (!file.getName().endsWith(".java") || MYSELF.equals(file.getName())) continue;

            Matcher m = LITERAL.matcher(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

            while (m.find()) out.add(m.group(1));
        }
    }
}
