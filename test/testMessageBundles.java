import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.traincontrol.util.I18n;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Lint for the message bundles.
 *
 * Guards two constraints that are invisible on inspection and that both fail silently:
 *
 *  - A straight apostrophe in a value is eaten by MessageFormat, which I18n.f() uses.  Today the cost is
 *    one hint message losing its quote marks, but the day someone adds a {0} to a value containing one,
 *    the placeholder silently stops being substituted.  The convention is the \\u2019 escape, which is
 *    correct whether or not a value goes through MessageFormat - the MessageFormat escape '' would
 *    render literally for the majority of keys, which are read through I18n.t and never see a
 *    MessageFormat pass.
 *
 *  - A non-ASCII byte is misread by Java 8, which loads .properties as ISO-8859-1, so a literal
 *    typographic character mojibakes at runtime.  Every non-ASCII character has to be a \\uXXXX escape.
 *
 * Needs no model, no socket and no display.
 */
public class testMessageBundles
{
    private static final String BUNDLE_DIR = "/org/traincontrol/resources/";
    private static final String ENGLISH_BUNDLE = "messages.properties";

    /**
     * The key a line defines, or null if the line is blank, a comment, or carries no '='.
     */
    private static String keyOf(String line)
    {
        String trimmed = line.trim();

        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!"))
        {
            return null;
        }

        int split = line.indexOf('=');

        return split < 0 ? null : line.substring(0, split).trim();
    }

    /**
     * Every message bundle on the test classpath, discovered rather than listed, so that adding a
     * locale brings it under these checks automatically.
     */
    private List<File> bundles() throws Exception
    {
        URL dir = testMessageBundles.class.getResource(BUNDLE_DIR);

        assertNotNull(dir, BUNDLE_DIR + " is not on the test classpath");

        // Resources are compiled to a directory, not a jar, so the URL is expected to be a file.
        // Asserting it explains the failure if that ever stops being true.
        assertEquals(dir.getProtocol(), "file", "expected " + BUNDLE_DIR + " to resolve to a directory");

        File[] found = new File(dir.toURI()).listFiles(
            (folder, name) -> name.startsWith("messages") && name.endsWith(".properties"));

        assertNotNull(found, BUNDLE_DIR + " is not a readable directory");

        List<File> output = new ArrayList<>(Arrays.asList(found));

        // A lint that silently finds nothing is worse than no lint at all
        assertTrue(output.size() >= 2,
            "expected the English bundle and at least one translation, found " + output);

        return output;
    }

    /**
     * No value may contain a straight apostrophe.
     *
     * Comments are skipped: several translations legitimately use apostrophes in their comments.  Keys
     * are skipped for the same reason values are checked from the first '=' onwards.  No bundle uses
     * line continuations, so every value is wholly contained in its own key=value line - that is
     * asserted separately below so this check cannot quietly miss a continued value.
     */
    @Test
    public void testNoStraightApostropheInAnyValue() throws Exception
    {
        List<String> offenders = new ArrayList<>();

        for (File bundle : bundles())
        {
            int lineNumber = 0;

            // ISO-8859-1 never throws on a stray byte, so a non-ASCII character is reported by
            // testBundlesAreAsciiOnly rather than crashing this check
            for (String line : Files.readAllLines(bundle.toPath(), StandardCharsets.ISO_8859_1))
            {
                lineNumber++;

                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!"))
                {
                    continue;
                }

                int split = line.indexOf('=');

                if (split < 0)
                {
                    continue;
                }

                if (line.substring(split + 1).indexOf('\'') >= 0)
                {
                    offenders.add(bundle.getName() + ":" + lineNumber + " (" + line.substring(0, split) + ")");
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "MessageFormat eats a straight apostrophe - use the \\u2019 escape instead. Offending values: "
            + offenders);
    }

    /**
     * Every bundle must be pure ASCII, because Java 8 reads .properties as ISO-8859-1.
     */
    @Test
    public void testBundlesAreAsciiOnly() throws Exception
    {
        List<String> offenders = new ArrayList<>();

        for (File bundle : bundles())
        {
            byte[] content = Files.readAllBytes(bundle.toPath());

            for (int i = 0; i < content.length; i++)
            {
                if ((content[i] & 0xFF) > 127)
                {
                    offenders.add(bundle.getName() + " at byte " + i);
                    break;
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "Java 8 reads .properties as ISO-8859-1, so a literal non-ASCII character mojibakes - "
            + "use a \\uXXXX escape. Offending files: " + offenders);
    }

    /**
     * No bundle may use line continuations.
     *
     * This is what lets the apostrophe check above treat one line as one complete value.  If a
     * continuation is ever introduced, that check would silently stop inspecting the continued part,
     * so fail here instead and make the reason explicit.
     */
    @Test
    public void testNoLineContinuations() throws Exception
    {
        List<String> offenders = new ArrayList<>();

        for (File bundle : bundles())
        {
            int lineNumber = 0;

            for (String line : Files.readAllLines(bundle.toPath(), StandardCharsets.ISO_8859_1))
            {
                lineNumber++;

                // A trailing backslash continues the value onto the next line - unless it is itself
                // escaped by a preceding backslash
                int trailing = 0;

                for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--)
                {
                    trailing++;
                }

                if (trailing % 2 == 1)
                {
                    offenders.add(bundle.getName() + ":" + lineNumber);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "testNoStraightApostropheInAnyValue assumes one value per line. Continued lines: " + offenders);
    }

    /**
     * No bundle may define the same key twice.
     *
     * A .properties file silently keeps only the LAST definition, so a duplicate means one of the two
     * values is dead - and when they differ, the surviving text may not be the one anyone intended.
     * autolayout.ui.errorAddEdge was exactly that: a variant carrying {0} was shadowed by a
     * placeholder-less one, so the reason an edge failed to be added was silently discarded.
     */
    @Test
    public void testNoDuplicateKeys() throws Exception
    {
        List<String> offenders = new ArrayList<>();

        for (File bundle : bundles())
        {
            Map<String, Integer> seen = new HashMap<>();

            int lineNumber = 0;

            for (String line : Files.readAllLines(bundle.toPath(), StandardCharsets.ISO_8859_1))
            {
                lineNumber++;

                String key = keyOf(line);

                if (key == null)
                {
                    continue;
                }

                Integer earlier = seen.put(key, lineNumber);

                if (earlier != null)
                {
                    offenders.add(bundle.getName() + " " + key
                        + " (lines " + earlier + " and " + lineNumber + ")");
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "a .properties file keeps only the last definition, so one of these values is dead: "
            + offenders);
    }

    /**
     * Every translation defines exactly the same keys as the English bundle.
     *
     * A key missing from a translation falls back through ResourceBundle's parent chain and quietly
     * renders in English, so it is easy to leave one behind without noticing.  An extra key is dead
     * weight.  Neither shows up at runtime, which is why it is worth asserting here.
     */
    @Test
    public void testTranslationsMatchEnglishKeySet() throws Exception
    {
        Map<String, Set<String>> byBundle = new HashMap<>();

        for (File bundle : bundles())
        {
            Set<String> keys = new HashSet<>();

            for (String line : Files.readAllLines(bundle.toPath(), StandardCharsets.ISO_8859_1))
            {
                String key = keyOf(line);

                if (key != null)
                {
                    keys.add(key);
                }
            }

            byBundle.put(bundle.getName(), keys);
        }

        Set<String> english = byBundle.get(ENGLISH_BUNDLE);

        assertNotNull(english, ENGLISH_BUNDLE + " was not found among " + byBundle.keySet());

        List<String> offenders = new ArrayList<>();

        for (Map.Entry<String, Set<String>> bundle : byBundle.entrySet())
        {
            if (ENGLISH_BUNDLE.equals(bundle.getKey()))
            {
                continue;
            }

            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(bundle.getValue());

            Set<String> extra = new TreeSet<>(bundle.getValue());
            extra.removeAll(english);

            if (!missing.isEmpty() || !extra.isEmpty())
            {
                offenders.add(bundle.getKey() + " missing=" + missing + " extra=" + extra);
            }
        }

        assertTrue(offenders.isEmpty(),
            "translations are out of step with " + ENGLISH_BUNDLE + ": " + offenders);
    }

    /**
     * A whole number in a message is an identifier, and identifiers are not grouped.
     *
     * MessageFormat sends a bare {0} through the locale's NumberFormat, so the track diagram tooltip
     * for feedback 1001 read "Feedback 1,001" - and "1.001" or "1 001" for anyone running a European
     * locale, since the static MessageFormat.format uses the default locale rather than the bundle's.
     * The same path carries s88 UIDs, DCC addresses and route ids, all of which pass four digits.
     *
     * Asserted by digits rather than whole strings so the test does not depend on which locale the
     * bundle was loaded for.
     */
    @Test
    public void testAWholeNumberInAMessageIsNotGrouped()
    {
        assertTrue(I18n.f("layout.feedbackUid", 1001).contains("1001"),
            "a feedback UID must read as typed, got: " + I18n.f("layout.feedbackUid", 1001));

        assertTrue(I18n.f("layout.switchAddr", 2048, "").contains("2048"),
            "a DCC address must read as typed, got: " + I18n.f("layout.switchAddr", 2048, ""));

        assertTrue(I18n.f("layout.route", 9001, "Yard").contains("9001"),
            "a route id must read as typed, got: " + I18n.f("layout.route", 9001, "Yard"));
    }

    /**
     * No placeholder may ask for a format of its own.
     *
     * I18n.f hands whole numbers to MessageFormat as text, so that identifiers are not grouped.  A
     * {0,number} or {0,choice} placeholder would then be given a String and throw
     * IllegalArgumentException - at run time, inside whichever dialog happened to use that message.
     * There are none today, in any bundle; this is what keeps the premise of that conversion true.
     */
    @Test
    public void testNoPlaceholderAsksForItsOwnFormat() throws Exception
    {
        Pattern formatted = Pattern.compile("\\{\\s*\\d+\\s*,");

        List<String> offenders = new ArrayList<>();

        for (File bundle : bundles())
        {
            List<String> lines = Files.readAllLines(bundle.toPath(), StandardCharsets.ISO_8859_1);

            for (int i = 0; i < lines.size(); i++)
            {
                if (keyOf(lines.get(i)) == null) continue;

                String value = lines.get(i).substring(lines.get(i).indexOf('=') + 1);

                if (formatted.matcher(value).find())
                {
                    offenders.add(bundle.getName() + ":" + (i + 1) + " " + lines.get(i).trim());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "these placeholders ask MessageFormat for a format, which I18n.f can no longer satisfy "
            + "for whole numbers: " + offenders);
    }
}
