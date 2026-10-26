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
 * Eight files, translated, maintained: 118 of the lines were the text of windows that left the build (`GraphViewer`'s
 * menus, `GraphEdgeEdit`'s labels, the old route editor's commands), and the live keys sat among them, so somebody
 * reading the bundle could not tell which half was the screen.
 *
 * **What counts as asking for a key:** the exact key spelled as a string literal anywhere in `src/` or `test/`, or a
 * key beginning with one of the five prefixes `BUILDERS` names - the five places the code builds a key by
 * concatenation.  The first version let ANY literal that was a prefix shield a key, and that hid 119 dead keys
 * (`"autolayout."` inside an `assertFalse(...startsWith(...))` shielded ninety-eight on its own).  The narrow rule has
 * the opposite risk, a builder written in future and not added here reporting its keys as dead, and that is the
 * direction to fail in: a visible failure that names the keys.
 *
 * **The floors stop this reporting a clean bill of health about nothing:** a regex that stopped matching literals would
 * call every key dead, which is loud, while one that matched everything would call every key live, which is silent;
 * `testItReadTheBundleAndTheSource` guards the quiet half.
 *
 * **The way past:** a key added before the screen that will use it is reported.  Raise `EXPECTED`, with the key named
 * in a comment, and lower it when the screen lands.
 *
 * @author Adam
 */
public class testEveryMessageKeyIsAskedFor
{
    /** The bundle the other seven are translations of. */
    private static final String BUNDLE = "src/org/traincontrol/resources/messages.properties";

    /** How many unreferenced keys are allowed to stand: zero since the 237 the deleted windows left behind came out. */
    private static final int EXPECTED = 0;

    /**
     * The places the code builds a message key by concatenation rather than spelling it out; nothing else shields a
     * key.  Add to this list, with the place named beside the entry, when a new builder is written.
     */
    private static final String[] BUILDERS =
    {
        // Route.getKind() -> route.kind.<kind>
        "route.kind.",

        // The side and facing menus in AutonomyEditorPanel
        "autosetup.ui.side",
        "autosetup.ui.facing",

        // The path preference list and its tooltips, in AutonomyViewerPanel
        "autolayout.ui.pathPreference",
        "autolayout.ui.tooltip.pathPreference",

        // AutonomyEditorPanel.bulkClearCounted -> <key> or <key>AtOnce, plus One and Few, for the three bulk clears.
        "autolayout.ui.confirmClearAll",
        // and for Allow Every Path (FR-108), which warns as they do
        "autolayout.ui.confirmAllowEveryPath",

        // ... and their tooltips' short forms through the same builder (FR-112): every bulk tool's, and Clear All
        // Locomotives' through clearLocomotivesTip
        "autosetup.ui.tipClearAll",
        "autosetup.ui.tipAllowEveryPath",
        "autosetup.ui.tipClearLocomotives",

        // The legacy import's two counted sentences, <key> + I18n.countForm in AutonomyViewerPanel's
        // sharedSquaresSentence and leftBehindSentence.  The plural is spelled there as a literal; the singular and
        // Polish forms are named whole here, because a bare prefix would shield the two Heading keys too
        "autosetup.ui.importSharedSquaresOne",
        "autosetup.ui.importSharedSquaresFew",
        "autosetup.ui.leftBehindOne",
        "autosetup.ui.leftBehindFew",

        // LocomotiveActions.deleteLoc's question and the import's guessed facings, <key> + I18n.countForm the same way;
        // the bulk clears' counted forms are under the confirmClearAll and confirmAllowEveryPath builders above
        "ui.confirmDeleteFromDatabaseWithRoutesOne",
        "ui.confirmDeleteFromDatabaseWithRoutesFew",
        "autosetup.ui.facingsGuessedOne",
        "autosetup.ui.facingsGuessedFew",

        // RouteEditorFrame.settingLabel -> route.ui.setting.<word>, the setting words a route stores (OB-367)
        "route.ui.setting.",
    };

    /**
     * A block comment, or a line comment, with any string literals inside it.  Commented-out code asks for nothing: two
     * keys read as live because only a disabled menu inside a block comment named them.  Strings are not stripped
     * first, so a `"//"` inside a literal can start a false comment; that removes literals rather than inventing them,
     * the safe direction (a live key reported dead, loudly).
     */
    private static final Pattern COMMENT = Pattern.compile(
        "/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    /** A Java string literal, escapes and all. */
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    /** This file, which is not allowed to shield a key: it prints the dead ones in its own failure message. */
    private static final String MYSELF = "testEveryMessageKeyIsAskedFor.java";

    /** Every key in the bundle is spelled somewhere in the source. */
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

    /** The reader found a bundle and a source tree, rather than quietly finding nothing. */
    @Test
    public void testItReadTheBundleAndTheSource() throws IOException
    {
        int keys = keysOf(new File(BUNDLE)).size();
        int literals = literals().size();

        assertTrue(keys > 1700,
            "only " + keys + " keys were read out of " + BUNDLE + "; the bundle has over 1,700 of them, "
            + "so the reader is broken and everything else this class says is about nothing");

        assertTrue(literals > 15000,
            "only " + literals + " string literals were found in src/ and test/; there are over twenty "
            + "thousand, and a literal reader that has stopped matching makes every key look dead");
    }

    /** The keys nothing asks for, in bundle order. */
    private static List<String> deadKeys() throws IOException
    {
        Set<String> literals = literals();

        List<String> dead = new ArrayList<>();

        for (String key : keysOf(new File(BUNDLE)))
        {
            if (literals.contains(key)) continue;

            boolean built = false;

            for (String prefix : BUILDERS)
            {
                if (key.startsWith(prefix))
                {
                    built = true;

                    break;
                }
            }

            if (!built) dead.add(key);
        }

        return dead;
    }

    /** The keys of a bundle. */
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

    /** Every string literal in `src/` and `test/`, this class's own file excepted. */
    private static Set<String> literals() throws IOException
    {
        Set<String> out = new LinkedHashSet<>();

        for (String where : new String[] {"src", "test"})
        {
            collect(new File(where), out);
        }

        return out;
    }

    /** Reads one folder's Java files into the literal set. */
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

            Matcher m = LITERAL.matcher(COMMENT.matcher(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).replaceAll(" "));

            while (m.find()) out.add(m.group(1));
        }
    }
}
