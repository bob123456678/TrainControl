import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
}
