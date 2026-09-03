package core;

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

    /**
     * No message uses a printf placeholder, because nothing formats one.
     *
     * A specific mistake, made six times in one session and caught by a reviewer rather than by
     * anything here.  I18n.f formats with MessageFormat, which understands {0} and passes %s through
     * untouched - and DISCARDS the argument, silently.  So a message written with %s does not throw,
     * does not log, and does not show what it was given: the user reads "Saved to %s", or a menu that
     * says "Selection (%s)", or - the one that mattered - a route editor promising to write out a
     * condition it cannot edit, and printing "%s" where the condition should be.
     *
     * Nothing in TrainControl calls String.format on a bundle value, so a percent-s in one is always
     * a mistake, which makes this a rule rather than a judgement.
     */
    @Test
    public void testNoMessageUsesAPrintfPlaceholder() throws Exception
    {
        List<String> offenders = new ArrayList<>();

        for (File file : bundles())
        {
            java.util.Properties properties = valuesOf(file);

            for (String key : properties.stringPropertyNames())
            {
                String value = properties.getProperty(key);

                if (value.contains("%s") || value.contains("%d"))
                {
                    offenders.add(file.getName() + ":" + key);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "these messages use a printf placeholder, which MessageFormat leaves in the text and "
            + "whose argument it throws away - so the user is shown the placeholder itself instead of "
            + "the value.  Use {0}: " + offenders);
    }

    /**
     * Every message handed to I18n.f actually has somewhere to put its argument.
     *
     * The other half of the rule above.  A key called with arguments and containing no placeholder is
     * either a message that has lost its value, or a call that should have been I18n.t - and the two
     * are worth telling apart, so this only looks at keys whose call site passes at least one
     * argument.
     */
    @Test
    public void testEveryFormattedMessageHasAPlaceholder() throws Exception
    {
        java.util.Properties english = null;

        for (File file : bundles())
        {
            if ("messages.properties".equals(file.getName())) english = valuesOf(file);
        }

        assertNotNull(english, "the English bundle was not found");

        List<String> offenders = new ArrayList<>();

        List<File> sources = javaSources(new File("src"));

        // javaSources returns empty, not a failure, when listFiles() is null - run from anywhere but
        // the project root and this scans zero files, indistinguishable from "no offenders".
        // testJavadocsAreAttached and testNoSelfRecursiveWrappers guard the same hazard the same way.
        assertFalse(sources.isEmpty(),
            "precondition: nothing was scanned under src/ - run from the project root");

        for (File source : sources)
        {
            String text = new String(java.nio.file.Files.readAllBytes(source.toPath()), "UTF-8");

            java.util.regex.Matcher m = Pattern.compile(
                "I18n\\.f\\(\\s*\"([^\"]+)\"\\s*,").matcher(text);

            while (m.find())
            {
                String key = m.group(1);
                String value = english.getProperty(key);

                if (value != null && !value.contains("{0}"))
                {
                    offenders.add(key + " (" + source.getName() + ")");
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "these keys are given arguments by their caller but have nowhere to put them, so whatever "
            + "was passed is silently dropped: " + offenders);
    }

    /**
     * Every word of an English route-command kind starts with a capital (OB-142).
     *
     * Adam: "the Kind dropdown in the route editor has inconsistent capitalization for its items.
     * make all words start with capitals." It was a genuine mixture - "Locomotive Function" beside
     * "Locomotive speed", "Three-way Switch" beside "Stop everything" - and the two sit next to each
     * other in one dropdown, which is where inconsistency is most visible.
     *
     * **English only, deliberately.** This is a rule about English capitalisation and applying it to
     * the other seven would be wrong rather than merely unnecessary: French, Spanish, Italian, Dutch,
     * Danish and Polish all use sentence case for interface labels, and German capitalises nouns for
     * reasons of its own that a rule about every word would trample. The bundles are checked for
     * matching key sets, ASCII and placeholders elsewhere in this class; how a language capitalises
     * is that language's business.
     *
     * A word here is a run between spaces, and a hyphenated pair counts as two - "Three-Way", not
     * "Three-way" - because that is the case the report was actually about.
     *
     * MUTATION: change any of these back to sentence case and this fails, naming the key.
     */
    @Test
    public void testTheRouteKindLabelsAreCapitalised() throws Exception
    {
        java.util.Properties english = valuesOf(new File("src" + BUNDLE_DIR + ENGLISH_BUNDLE));

        StringBuilder wrong = new StringBuilder();

        int checked = 0;

        for (String key : english.stringPropertyNames())
        {
            if (!key.startsWith("route.kind.")) continue;

            checked++;

            String value = english.getProperty(key);

            for (String word : value.split("[ \\-]+"))
            {
                if (word.isEmpty()) continue;

                char first = word.charAt(0);

                // A word that opens with a digit - "3-way", were it ever written that way - has no
                // case to get wrong, and neither has a placeholder.
                if (!Character.isLetter(first)) continue;

                if (Character.isUpperCase(first)) continue;

                wrong.append("\n  ").append(key).append(" = \"").append(value)
                     .append("\"  (the word \"").append(word).append("\" is lower case)");
            }
        }

        assertTrue(checked >= 12,
            "only " + checked + " route.kind.* labels were found, so this checked almost nothing - "
            + "either the prefix has changed or the bundle is not being read");

        assertEquals(wrong.toString(), "",
            "a route command kind is not capitalised the way the rest of the dropdown is. These sit "
            + "beside each other in one list, which is exactly where a mixture shows (OB-142):" + wrong);
    }

    /**
     * A bundle's key/value pairs.
     *
     * Read as ISO-8859-1, which is what Java 8's PropertyResourceBundle does, so what this sees is
     * what the application sees.
     */
    private static java.util.Properties valuesOf(File file) throws Exception
    {
        java.util.Properties properties = new java.util.Properties();

        try (java.io.InputStreamReader in = new java.io.InputStreamReader(
            new java.io.FileInputStream(file), "ISO-8859-1"))
        {
            properties.load(in);
        }

        return properties;
    }

    /**
     * Every .java file under a directory.
     */
    private static List<File> javaSources(File dir)
    {
        List<File> found = new ArrayList<>();

        File[] children = dir.listFiles();

        if (children == null) return found;

        for (File child : children)
        {
            if (child.isDirectory()) found.addAll(javaSources(child));
            else if (child.getName().endsWith(".java")) found.add(child);
        }

        return found;
    }

    /**
     * A plain yes/no confirmation uses TrainControl's own button text.
     *
     * showConfirmDialog does not take button labels: Swing supplies them from the look-and-feel, which
     * follows the JVM's locale rather than the language the user picked in TrainControl.  So a German
     * user on an English Windows got German dialogs with English Yes/No buttons - in four places, and
     * nowhere else, because every other confirmation passes YES_NO_OPTS.
     *
     * What this allows is a confirmation whose MESSAGE is a component rather than a string.  Those are
     * input dialogs wearing a confirmation's clothes - a panel of controls with OK and Cancel - and
     * their buttons are the least of what is unusual about them.
     *
     * So the check is on the MESSAGE argument, the one after the parent, and not on the call as a
     * whole: the TITLE is always built from I18n, so a rule that looked anywhere in the call flagged
     * every one of them.  That was the first version of this test, and it is worth saying because the
     * looser rule looked perfectly reasonable until it was run.
     */
    @Test
    public void testConfirmationsUseTranslatedButtons() throws Exception
    {
        List<String> offenders = new ArrayList<>();

        List<File> sources = javaSources(new File("src"));

        assertFalse(sources.isEmpty(),
            "precondition: nothing was scanned under src/ - run from the project root");

        for (File source : sources)
        {
            String text = new String(java.nio.file.Files.readAllBytes(source.toPath()), "UTF-8");

            int at = text.indexOf("showConfirmDialog(");

            while (at >= 0)
            {
                String message = secondArgument(text, at + "showConfirmDialog(".length());

                if (message != null && message.startsWith("I18n."))
                {
                    offenders.add(source.getName() + ": " + message);
                }

                at = text.indexOf("showConfirmDialog(", at + 1);
            }
        }

        assertTrue(offenders.isEmpty(),
            "these are plain yes/no confirmations built with showConfirmDialog, whose buttons come "
            + "from the look-and-feel and follow the SYSTEM language rather than the one the user "
            + "chose.  Use showOptionDialog with TrainControlUI.YES_NO_OPTS - and remember it returns "
            + "an index, not YES_OPTION: " + offenders);
    }

    /**
     * The second argument of a call, given the offset just past its opening bracket.
     *
     * Bracket-aware, so a first argument that is itself a call - which the parent component often is -
     * does not end the argument early.
     */
    private static String secondArgument(String text, int from)
    {
        int depth = 0;
        int firstComma = -1;

        for (int i = from; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (c == '(') depth++;
            else if (c == ')')
            {
                if (depth == 0) return null;
                depth--;
            }
            else if (c == ',' && depth == 0)
            {
                if (firstComma < 0)
                {
                    firstComma = i;
                }
                else
                {
                    return text.substring(firstComma + 1, i).trim();
                }
            }
        }

        return null;
    }
    /**
     * The two train-length warnings name what they are about (OB-153, OB-154).
     *
     * Adam, OB-153: "autosetup.ui.checkNoTrainLength is prefilled with the station name, not the train
     * at that station name.  state both ({train} and {station} has no...)"  OB-154:
     * "autosetup.ui.checkNoMaxTrainLength does not specify the station name".
     *
     * Both panels build a finding's text as
     *
     *     subject = finding.getTile() == null ? finding.getSubject() : describeTile(finding.getTile())
     *
     * which is right for every finding whose subject IS the point it is about - an unnamed Point's name
     * is its coordinate, and the tile's description is the more useful of the two. FR-046's
     * train-length warning is the first whose subject is a LOCOMOTIVE, so that preference threw the
     * train's name away and the warning named the station it was standing at.
     *
     * The fix passes BOTH, leaving {0} meaning what it always meant so no other message moved.
     *
     * BOTH HALVES, because either alone passes while the bug is present. A message that says {1} with
     * nothing supplying it renders the literal text "{1}" - which is what the reader would see - and
     * panels that pass an argument no message asks for change nothing at all.
     */
    @Test
    public void testTheLengthWarningsNameTheTrainAndTheStation() throws Exception
    {
        List<String> offenders = new ArrayList<>();

        for (File bundle : bundles())
        {
            java.util.Properties values = valuesOf(bundle);

            String train = values.getProperty("autosetup.ui.checkNoTrainLength");
            String station = values.getProperty("autosetup.ui.checkNoMaxTrainLength");

            // {1} is the train, {0} the place it stands - a warning naming only one of them sends the
            // reader to a station to look for a train it did not name.
            if (train == null || !train.contains("{0}") || !train.contains("{1}"))
            {
                offenders.add(bundle.getName() + " checkNoTrainLength must name the train {1} and "
                    + "where it stands {0}: " + train);
            }

            if (station == null || !station.contains("{0}"))
            {
                offenders.add(bundle.getName() + " checkNoMaxTrainLength must name the station {0}: "
                    + station);
            }
        }

        assertTrue(offenders.isEmpty(), "the length warnings do not name their subject: " + offenders);

        // A FINDING THAT CARRIES A TILE MUST PUT ITS SENTENCE IN {1} (MT-223).
        //
        // Both panels build a finding's text as `tile == null ? getSubject() : describeTile(tile)` and
        // pass that as {0}, with the finding's own subject as {1}. So {0} silently changes meaning the
        // day somebody gives an existing finding a tile - which is what happened here, when the
        // duplicate-sensor error gained a square to jump to and its sentence would have been swallowed
        // by the tile description.
        //
        // Checked for the one finding that names its whole subject in a sentence. There is no way to
        // ask a bundle which findings carry tiles, so this pins the one that changed rather than
        // pretending to a general rule.
        for (File bundle : bundles())
        {
            String duplicate = valuesOf(bundle).getProperty("autosetup.ui.checkDuplicateSensorPage");

            assertTrue(duplicate != null && duplicate.contains("{1}") && !duplicate.contains("{0}"),
                bundle.getName() + " must render the duplicate-sensor sentence from {1}: it carries a "
                + "tile now, so {0} is the square's description and would swallow the sentence that "
                + "names the sensor and both pages - " + duplicate);
        }

        // and something has to supply the {1} they now ask for
        for (String source : new String[] {"src/org/traincontrol/gui/AutonomyViewerPanel.java",
            "src/org/traincontrol/gui/AutonomyEditorPanel.java"})
        {
            File file = new File(source);

            assertTrue(file.isFile(), source + " was not found - run this from the project root");

            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            // The FIRST TWO arguments, and no claim about what follows them (OB-171).
            //
            // This asked for the call including its closing bracket, which pinned the argument COUNT
            // as well as the order - so adding a third, for the message that says how many squares
            // still need a length, failed a rule that is about {1} being the subject.
            assertTrue(text.contains("describe(finding.getMessageKey(), subject, finding.getSubject()"),
                source + " no longer passes the finding's own subject alongside the tile description, "
                + "so {1} in the length warning has nothing to fill it and renders as the literal "
                + "text \"{1}\" (OB-153)");
        }
    }
    /**
     * Nothing asks for a key the bundle does not have.
     *
     * `CMT-C2` counted 229 keys present in all eight bundles that nothing in `src/` asks for.  That is
     * noise: it costs a reader time and nothing else.  **The direction that costs an OPERATOR
     * something is the other one**, and it had no test - a key renamed in the bundle and not at its
     * call site puts the raw key on screen, in the one place a person is being told what went wrong.
     *
     * Literal calls only, which is all a textual rule can see.  Five families are built by
     * concatenation - a route kind, a side, a facing, and two path preferences, each an enum name
     * appended to a prefix - and they are listed here rather than skipped quietly, so that a sixth
     * cannot be added without somebody noticing that this test would not see it.
     *
     * MUTATION this catches: renaming any key in `messages.properties` without its call site.
     */
    @Test
    public void testNothingAsksForAKeyThatIsNotThere() throws Exception
    {
        java.util.Properties english = null;

        for (File bundle : bundles())
        {
            if (ENGLISH_BUNDLE.equals(bundle.getName())) english = valuesOf(bundle);
        }

        assertNotNull(english, "no English bundle among " + bundles());

        assertTrue(english.size() > 1000, "the bundle did not parse: " + english.size() + " keys");

        java.util.List<String> built = Arrays.asList(
            "route.kind.", "autosetup.ui.side", "autosetup.ui.facing",
            "autolayout.ui.pathPreference", "autolayout.ui.tooltip.pathPreference");

        java.util.regex.Pattern call =
            java.util.regex.Pattern.compile("I18n\\.[tf]\\(\\s*\"([^\"]+)\"");

        java.util.List<String> missing = new ArrayList<>();

        List<File> sources = javaSources(new File("src"));

        // A FLOOR, like the two other scans in this file (TSX-B6).
        //
        // `javaSources` answers empty rather than failing when `listFiles()` returns null - run from
        // anywhere but the project root and this scans nothing, which is indistinguishable from
        // finding no offenders.  The two older scans here carry this guard with that reason written
        // out; this one was added afterwards and did not get it.
        assertFalse(sources.isEmpty(),
            "precondition: nothing was scanned under src/ - run from the project root");

        for (File source : sources)
        {
            // I18n's own javadoc shows the idiom with example keys.
            if (source.getName().equals("I18n.java")) continue;

            java.util.regex.Matcher m = call.matcher(new String(
                java.nio.file.Files.readAllBytes(source.toPath()),
                java.nio.charset.StandardCharsets.UTF_8));

            while (m.find())
            {
                String asked = m.group(1);

                if (english.containsKey(asked) || built.contains(asked)) continue;

                missing.add(asked + " (" + source.getName() + ")");
            }
        }

        assertTrue(missing.isEmpty(),
            "something asks for a message key that is not in the bundle, so it would show the operator "
            + "the raw key instead of a sentence: " + missing);
    }
}
