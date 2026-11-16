package regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Comments stay lean (CLAUDE.md, "Comments stay lean"): history in a comment, comment blocks over 15 lines and review
 * ids are counted, and no count may go up.  A comment states the current rule and why; what the code used to do, which
 * round found it and who said what on which date belong in the commit message and the tracker.
 *
 * Three ratchets, each a total plus a per-file breakdown, so a swap between files (one trimmed, another grown) fails and
 * names both:
 * <ul>
 * <li>HISTORY: comment lines (in `//`, block and javadoc comments, never in a string literal or in code) carrying one of
 * the markers below.</li>
 * <li>LONG BLOCKS: a run of more than 15 consecutive `//` lines, or a non-javadoc block comment over 15 lines.  A
 * javadoc contract may be long.</li>
 * <li>REVIEW IDS: an id of a review finding in a comment of `src/` or `test/` - a prefix from the first column of
 * `triagedb.py findings`, a dash, an optional capital letter and digits.  The store is read when the test runs,
 * so a prefix added to it is guarded from that day.  OB, MT and FR are Adam's tracker ids, which comments may cite, and
 * are not counted.  The generated regions are counted too.</li>
 * </ul>
 * Two more ratchets read `COMMENT_PINS` (a file beside this one), one line per file of `src/` that needs one: COMMENT
 * SHARE, the comment characters (`//`, block and javadoc, delimiters included) as a fraction of the file, in tenths of
 * a percent, and PRIVATE JAVADOC, the javadoc blocks of over 3 lines on a declaration that is neither public nor
 * protected.  The members of a public or protected interface (explicit, or nested in one) are public and enum constants
 * have no modifier to read, so neither counts.  A file not in the pins may carry up to `SHARE_CEILING` and no long
 * private javadoc, and a file under `SHARE_FLOOR` characters is exempt from the share; a pinned file may not rise above
 * its pin, and a pin is lowered when its file is trimmed (a file well under its pin is not a failure).
 *
 * The first two count `test/` too, under pins of its own.  Not counted by them: the NetBeans generated regions (from a line holding `//GEN-BEGIN` to the one
 * holding `//GEN-END`, which includes the "Variables declaration - do not modify" block).
 *
 * The history markers, each measured against the whole of `src/` and kept only where most of its matches were history:
 * <ul>
 * <li>a date written year-month-day (also covers an Adam attribution and "until" before a date): 392 lines, all
 * attributions or "as of" stories.</li>
 * <li>an Adam attribution that opens with the year, and "until" before a digit: 176 and 2 lines, all dated; kept for the
 * day a date is written another way.</li>
 * <li>"used" and "to" followed by "be", or after it, this, that, they, there, here, which, who, and, but or else: 119
 * lines, nearly all "it once did X".  A bare "used" "to" is NOT counted: "a socket used to send" and "is used to" state
 * a role.</li>
 * <li>"Round" or "round" before a number, and the review-round phrase: 15 lines, all review or build rounds.</li>
 * <li>"found by" only before a reviewer, review, sweep, rendering, audit or a review id: 6 lines, all
 * how a bug was found.  A bare "found by" is not counted: "found by the square" states how a lookup works.</li>
 * </ul>
 * Dropped after measuring, because most matches state a live rule: "no longer" (182 lines, "a train no longer stands
 * here once..."), "was found" (17, mostly `@return what was found`) and "previously" (8, half of them rules).
 *
 * To bank an improvement, lower the total and edit the per-file list as the failure says; never raise either.  When a
 * count rises, trim the comment to the current rule rather than raising the number.
 */
public class testCommentsStayLean
{
    private static final int BLOCK_LINES = 15;

    /** Where the per-file pins live: `path<TAB>share in tenths of a percent, or -<TAB>count`, `#` for comments. */
    private static final String COMMENT_PINS = "test/regression/commentShare.pins";

    /** The share, in tenths of a percent, a file without a pin may carry (all of `src/` stood at 29.0% when set). */
    private static final int SHARE_CEILING = 250;

    /** A file with fewer characters than this has no share ceiling: a few lines of javadoc are most of it. */
    private static final int SHARE_FLOOR = 1000;

    /** Javadoc blocks over this many lines on a non-public, non-protected declaration are counted. */
    private static final int PRIVATE_JAVADOC_LINES = 3;

    private static final String LEAN_ADVICE = "see CLAUDE.md, 'Comments stay lean': state the current rule, put history "
        + "in the commit message.";

    private static final Pattern[] HISTORY_MARKERS = {
        Pattern.compile("20\\d\\d-\\d\\d-\\d\\d"),
        Pattern.compile("\\(Adam, 20"),
        Pattern.compile("\\buntil \\d"),
        Pattern.compile("\\bused to be\\b|\\b(?:it|this|that|they|there|here|which|who|and|but|else) used to\\b",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b[Rr]ound \\d+"),
        Pattern.compile("\\breview round\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b[Ff]ound by (?:a |an |the )?(?:review|independent|sweep|render|audit|RSA|OV|[A-Z]{2,}\\d*-)"),
    };

    /** Comment lines carrying a history marker, as they stand.  Lower it whenever some go; never raise it. */
    private static final int HISTORY_ALLOWED = 0;

    /** WHICH files carry them, so that a swap between files fails and names both.  Files at zero are not listed. */
    private static final String[] HISTORY_BY_FILE = {
    };

    /** Runs of more than 15 `//` lines plus non-javadoc block comments over 15 lines, as they stand.  Never raise it. */
    private static final int LONG_BLOCKS_ALLOWED = 0;

    /** WHICH files carry them.  Files at zero are not listed. */
    private static final String[] LONG_BLOCKS_BY_FILE = {
    };

    /** The same two counts in `test/`, pinned apart from `src/` so a trim in one cannot pay for growth in the other. */
    private static final int TEST_HISTORY_ALLOWED = 48;

    private static final String[] TEST_HISTORY_BY_FILE = {
        "test/core/testAPastedTrainKeepsItsDirection.java (4)",
        "test/core/testATrainCoversTheTrackBehindIt.java (2)",
        "test/core/testATrainTooLongIsRefusedTheBerth.java (4)",
        "test/core/testAccessory.java (1)",
        "test/core/testAdvancedRoutes.java (1)",
        "test/core/testAtomicWrite.java (1)",
        "test/core/testAutonomyDiagramReducer.java (2)",
        "test/core/testAutonomyPathValidation.java (1)",
        "test/core/testCS2Message.java (2)",
        "test/core/testCommandRow.java (1)",
        "test/core/testHomeStaging.java (2)",
        "test/core/testLayoutRenameKeys.java (1)",
        "test/core/testLayoutTiles.java (3)",
        "test/core/testLengthRulesUnderRandomLayouts.java (1)",
        "test/core/testLocomotive.java (1)",
        "test/core/testMockCentralStation.java (1)",
        "test/core/testNetworkProxy.java (2)",
        "test/core/testNonReversibleTrains.java (4)",
        "test/core/testParseCS2Routes.java (3)",
        "test/core/testParseCS3Routes.java (2)",
        "test/core/testRouteRoundTrip.java (1)",
        "test/core/testRoutes.java (1)",
        "test/core/testTheAutoTierScopeMatchesTheRuntime.java (1)",
        "test/core/testTheLengthGuardsOnTheRealLayout.java (2)",
        "test/regression/testEditorSurfaceRules.java (1)",
        "test/regression/testTheCheckerAgreesWithTheBuild.java (1)",
        "test/ui/testADirectionChangeIsNotSwallowed.java (1)",
        "test/ui/testRouteCapture.java (1)",
    };

    private static final int TEST_LONG_BLOCKS_ALLOWED = 3;

    private static final String[] TEST_LONG_BLOCKS_BY_FILE = {
        "test/core/testAutonomyDiagramReducer.java (1)",
        "test/core/testNonReversibleTrains.java (1)",
        "test/regression/testPageIdsAreDurable.java (1)",
    };

    /** Adam's tracker ids, which comments may cite (CLAUDE.md); every other prefix in the store is a review id. */
    private static final Set<String> TRACKER_PREFIXES = new HashSet<>(java.util.Arrays.asList("OB", "MT", "FR"));

    /** Review ids in comments, as they stand.  Lower it whenever some go; never raise it. */
    private static final int IDS_ALLOWED = 0;

    /** WHICH files carry them, so that a swap between files fails and names both.  Files at zero are not listed. */
    private static final String[] IDS_BY_FILE = {
    };

    @Test
    public void testHistoryInCommentsOnlyGoesDown() throws Exception
    {
        Map<String, List<String>> found = new TreeMap<>();

        scanSource(new String[]{"src"}, found, null, null);

        ratchet("lines of history in comments", found, HISTORY_ALLOWED, HISTORY_BY_FILE, "HISTORY_ALLOWED",
            "HISTORY_BY_FILE", "a comment line with a date, 'used to', 'Round N' or 'found by <reviewer>' in it",
            LEAN_ADVICE);
    }

    @Test
    public void testLongCommentBlocksOnlyGoDown() throws Exception
    {
        Map<String, List<String>> found = new TreeMap<>();

        scanSource(new String[]{"src"}, null, found, null);

        ratchet("comment blocks over " + BLOCK_LINES + " lines", found, LONG_BLOCKS_ALLOWED, LONG_BLOCKS_BY_FILE,
            "LONG_BLOCKS_ALLOWED", "LONG_BLOCKS_BY_FILE", "a run of more than " + BLOCK_LINES
            + " // lines or a non-javadoc block comment of more than " + BLOCK_LINES + " lines", LEAN_ADVICE);
    }

    @Test
    public void testHistoryInTestCommentsOnlyGoesDown() throws Exception
    {
        Map<String, List<String>> found = new TreeMap<>();

        scanSource(new String[]{"test"}, found, null, null);

        ratchet("lines of history in test comments", found, TEST_HISTORY_ALLOWED, TEST_HISTORY_BY_FILE,
            "TEST_HISTORY_ALLOWED", "TEST_HISTORY_BY_FILE", "a comment line with a date, 'used to', 'Round N' or "
            + "'found by <reviewer>' in it", LEAN_ADVICE);
    }

    @Test
    public void testLongTestCommentBlocksOnlyGoDown() throws Exception
    {
        Map<String, List<String>> found = new TreeMap<>();

        scanSource(new String[]{"test"}, null, found, null);

        ratchet("test comment blocks over " + BLOCK_LINES + " lines", found, TEST_LONG_BLOCKS_ALLOWED,
            TEST_LONG_BLOCKS_BY_FILE, "TEST_LONG_BLOCKS_ALLOWED", "TEST_LONG_BLOCKS_BY_FILE", "a run of more than "
            + BLOCK_LINES + " // lines or a non-javadoc block comment of more than " + BLOCK_LINES + " lines",
            LEAN_ADVICE);
    }

    @Test
    public void testNoReviewIdsInComments() throws Exception
    {
        Map<String, List<String>> found = new TreeMap<>();

        scanSource(new String[]{"src", "test"}, null, null, found);

        ratchet("review ids in comments", found, IDS_ALLOWED, IDS_BY_FILE, "IDS_ALLOWED", "IDS_BY_FILE",
            "a review-finding id in a comment", "Review ids stay out of comments: see CLAUDE.md.");
    }

    /**
     * A file's comments, as a share of its characters, stay under its pin (or, with no pin, under the ceiling).
     *
     * MUTATION: add a 40-line comment to a pinned file or to an unpinned one, and this fails naming the file, its share
     * and its pin.
     */
    @Test
    public void testCommentShareOnlyGoesDown() throws Exception
    {
        assertNoShapeFailures(true);
    }

    /**
     * The long javadoc on non-public declarations only goes down, per file, and a file without a pin has none.
     *
     * MUTATION: put a 5-line javadoc on a private method of an unpinned file, or lower a pinned count by one in the
     * pins file, and this fails naming the file, its count and its pin.
     */
    @Test
    public void testLongPrivateJavadocOnlyGoesDown() throws Exception
    {
        assertNoShapeFailures(false);
    }

    private void assertNoShapeFailures(boolean share) throws Exception
    {
        List<String> failures = shapeFailures(share);

        assertTrue(failures.isEmpty(), (share ? "comment share: " : "private javadoc: ") + failures);
    }

    /**
     * The ceiling is exercised on a fixture over SHARE_FLOOR characters, and the pin and the floor beside it.
     *
     * MUTATION: set SHARE_CEILING to 1000, or drop the floor test in `verdict`, and this fails.
     */
    @Test
    public void testTheShareCeilingHoldsForANewFile() throws Exception
    {
        StringBuilder fat = new StringBuilder("class Fat\n{\n");

        for (int i = 0; i < 30; i++)
        {
            fat.append("    // a comment line that says very little\n    int f").append(i).append(";\n");
        }

        int[] shape = shapeOf(fat.toString());

        assertTrue(shape[1] > SHARE_FLOOR && shape[0] > SHARE_CEILING, "fixture not over the ceiling: " + shape[0]);

        assertNotNull(verdict(true, "Fat.java", shape, null), "a new file over the ceiling passed");

        assertNull(verdict(true, "Fat.java", shape, new int[] {shape[0], 0}), "a file at its pin failed");

        assertNotNull(verdict(true, "Fat.java", shape, new int[] {shape[0] - 1, 0}), "a file over its pin passed");

        assertNull(verdict(true, "Fat.java", shape, new int[] {shape[0] + 200, 0}), "a file well under its pin failed");

        int[] small = shapeOf("class Small\n{\n    // a comment and little else\n}\n");

        assertTrue(small[1] < SHARE_FLOOR && small[0] > SHARE_CEILING, "the small fixture is not small and fat");

        assertNull(verdict(true, "Small.java", small, null), "a file under the floor failed the ceiling");
    }

    /**
     * Long javadoc is counted on private and package-private declarations only: not on the members of a public or
     * protected interface, nor on enum constants, and an annotation before the modifiers on the same line is skipped.
     * The fixture holds ten long javadocs; four sit on a declaration without public or protected.
     *
     * MUTATION: in `isPrivateDeclaration` drop the `I` interface test, the enum-constant test or the annotation strip,
     * and this fails with the count that kind adds.
     */
    @Test
    public void testLongPrivateJavadocIsCountedOnPrivateDeclarationsOnly() throws Exception
    {
        String doc = "    /**\n     * a\n     * b\n     * c\n     */\n";

        String source = "public interface Shown\n{\n" + doc + "    void inPublicInterface();\n}\n"
            + "interface Local\n{\n" + doc + "    void inPackageInterface();\n}\n"
            + "enum Colour\n{\n" + doc + "    RED,\n" + doc + "    GREEN(1);\n" + doc + "    Colour(int x) {}\n}\n"
            + "class Holder\n{\n" + doc + "    @Override public String toString() { return \"\"; }\n"
            + doc + "    @Override String hidden() { return \"\"; }\n" + doc + "    private int x;\n"
            + doc + "    @SuppressWarnings(\"x\") public void quiet() {}\n" + doc + "    public int y;\n    /**\n     * three\n     */\n    private int z;\n}\n";

        // Local's member, Colour's constructor, hidden() and x.
        assertEquals(shapeOf(source)[2], 4);
    }

    /** The shape of one source text, as `scanSource` reports it for a file: {share tenths, characters, javadocs}. */
    private int[] shapeOf(String source)
    {
        int[] shape = new int[2];

        scanFile("Fixture.java", source, new LinkedHashMap<Integer, String>(), new ArrayList<String>(), null,
            new LinkedHashMap<Integer, List<String>>(), shape);

        return new int[] {(shape[0] * 1000 + source.length() - 1) / Math.max(1, source.length()), source.length(),
            shape[1]};
    }

    /** The files whose share (or, when `share` is false, long private javadoc count) is off its pin or the default. */
    private List<String> shapeFailures(boolean share) throws Exception
    {
        Map<String, int[]> shapes = new TreeMap<>();

        scanSource(new String[]{"src"}, null, null, null, shapes);

        Map<String, int[]> pins = readPins();

        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, int[]> each : shapes.entrySet())
        {
            String failure = verdict(share, each.getKey(), each.getValue(), pins.get(each.getKey()));

            if (failure != null) failures.add(failure);
        }

        return failures;
    }

    /**
     * The failure for one file, or null.  The share fails only above its pin (or the ceiling); the javadoc count fails
     * above its pin and, to bank the gain, below it.
     */
    private String verdict(boolean share, String file, int[] shape, int[] pin)
    {
        boolean pinned = pin != null && (share ? pin[0] >= 0 : pin[1] > 0);

        int now = share ? (shape[1] < SHARE_FLOOR ? 0 : shape[0]) : shape[2];

        int limit = pinned ? pin[share ? 0 : 1] : share ? SHARE_CEILING : 0;

        String what = file + (share ? " has " + tenths(now) + " of its characters in comments, "
            : " has " + now + " javadoc blocks of over " + PRIVATE_JAVADOC_LINES
            + " lines on private or package-private declarations, ") + (pinned ? "pinned at " : "allowed ")
            + (share ? tenths(limit) : String.valueOf(limit));

        if (now > limit)
        {
            return what + ": trim the comments to the current rule (CLAUDE.md, 'Comments stay lean'); if you "
                + "trimmed and the pin is above the new figure, lower it in " + COMMENT_PINS + ".  Never raise it.";
        }

        return !share && pinned && now < limit ? what + ": it is down to " + now + ", so lower its pin in "
            + COMMENT_PINS + " to " + now + " to keep the gain." : null;
    }

    private static String tenths(int tenths)
    {
        return (tenths / 10) + "." + (tenths % 10) + "%";
    }

    /** The pins by path: {share tenths or -1, long private javadoc blocks}.  A pin for a missing file fails. */
    private Map<String, int[]> readPins() throws IOException
    {
        Map<String, int[]> pins = new TreeMap<>();

        for (String line : Files.readAllLines(new File(COMMENT_PINS).toPath(), StandardCharsets.UTF_8))
        {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;

            String[] part = line.split("\t");

            assertEquals(part.length, 3, COMMENT_PINS + " has a malformed line: " + line);

            assertTrue(new File(part[0]).isFile(), COMMENT_PINS + " pins " + part[0] + ", which is gone: remove its line");

            assertNull(pins.put(part[0], new int[] {part[1].equals("-") ? -1 : Integer.parseInt(part[1]),
                Integer.parseInt(part[2])}), COMMENT_PINS + " pins " + part[0] + " twice");
        }

        return pins;
    }

    /**
     * A trimmed javadoc keeps its shape: one `/**` opener on a line of its own, and every `*` line one column right of
     * it.  A trim that rewrites a block's body without its opener leaves a second opener and the body one column out.
     * A javadoc left over another is `testJavadocsAreAttached`'s.
     *
     * MUTATION: put a second `/**` line above any javadoc's first line, and this fails naming the file and line.
     */
    @Test
    public void testNoJavadocIsMisshapen() throws Exception
    {
        List<String> found = new ArrayList<>();

        for (String tree : new String[] {"src", "test"})
        {
            try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(new File(tree).toPath()))
            {
                for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) walk
                    .filter(p -> p.toString().endsWith(".java")).sorted()::iterator)
                {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

                    for (int i = 0; i < lines.size(); i++)
                    {
                        if (lines.get(i).matches("^\\s*[^\\s/*].*/\\*\\*\\s*$"))
                        {
                            found.add(path + ":" + (i + 1) + " an opener after code on its line");
                        }

                        Matcher opener = Pattern.compile("^( *)/\\*\\*\\s*$").matcher(lines.get(i));

                        if (!opener.matches()) continue;

                        String inside = opener.group(1) + "  *";

                        for (int j = i + 1; j < lines.size(); j++)
                        {
                            String line = lines.get(j);

                            if (j == i + 1 && line.trim().startsWith("/**"))
                            {
                                found.add(path + ":" + (j + 1) + " a second opener");
                            }
                            else if (line.startsWith(inside) && !line.startsWith(" " + inside))
                            {
                                found.add(path + ":" + (j + 1) + " a line one column right");
                            }

                            if (line.trim().endsWith("*/")) break;
                        }
                    }
                }
            }
        }

        assertTrue(found.isEmpty(), "javadocs out of shape: " + found);
    }

    /**
     * The counts against the pinned ones: a rise fails naming the offenders and giving the advice, a fall fails asking
     * for the number to be lowered so that the gain is kept.
     */
    private void ratchet(String what, Map<String, List<String>> found, int allowed, String[] pinnedList,
        String totalName, String listName, String kind, String advice)
    {
        Map<String, Integer> pinned = new TreeMap<>();

        int pinnedSum = 0;

        for (String entry : pinnedList)
        {
            int open = entry.lastIndexOf(" (");

            int count = Integer.parseInt(entry.substring(open + 2, entry.length() - 1));

            assertTrue(count > 0, listName + " lists " + entry + " - a file at zero leaves the list");
            assertNull(pinned.put(entry.substring(0, open), count), listName + " lists " + entry + " twice");

            pinnedSum += count;
        }

        assertEquals(pinnedSum, allowed, listName + " adds up to " + pinnedSum + " but " + totalName + " is " + allowed
            + " - keep the two together");

        int total = 0;

        List<String> rose = new ArrayList<>();

        List<String> fell = new ArrayList<>();

        StringBuilder offenders = new StringBuilder();

        Set<String> names = new HashSet<>(found.keySet());

        names.addAll(pinned.keySet());

        for (String file : new java.util.TreeSet<>(names))
        {
            List<String> here = found.containsKey(file) ? found.get(file) : Collections.<String>emptyList();

            int was = pinned.containsKey(file) ? pinned.get(file) : 0;

            total += here.size();

            if (here.size() > was)
            {
                rose.add(file + " (" + here.size() + ", pinned " + was + ")");

                offenders.append('\n').append(file).append(" has ").append(here.size()).append(", pinned ").append(was)
                    .append(", so ").append(here.size() - was).append(" more than pinned, among:");

                for (int i = 0; i < here.size() && i < 40; i++) offenders.append("\n  ").append(here.get(i));
            }
            else if (here.size() < was)
            {
                fell.add(file + (here.isEmpty() ? ": remove its line from " + listName
                    : ": change its line in " + listName + " to \"" + file + " (" + here.size() + ")\""));
            }
        }

        assertTrue(rose.isEmpty() && total <= allowed,
            what + ": there are now " + total + ", " + (total > allowed ? "up from " + allowed : "against " + allowed)
            + ", and these files rose: " + rose + ".  The new ones are " + kind + "; they are listed below with the "
            + "file's other offenders (git diff shows which are yours)." + offenders + "\n" + advice
            + "  Do not raise " + totalName + ".");

        assertTrue(total == allowed && fell.isEmpty(),
            what + ": " + total + " remain, fewer than the " + allowed + " recorded.  Lower " + totalName + " to " + total
            + " so the improvement is kept, and in " + listName + ": " + fell);
    }

    private void scanSource(String[] roots, Map<String, List<String>> history, Map<String, List<String>> longBlocks,
        Map<String, List<String>> ids) throws IOException
    {
        scanSource(roots, history, longBlocks, ids, null);
    }

    /**
     * Fills the maps asked for (the others may be null) with `path:line: text` for each offender, per file; `shapes`
     * gets {comment share in tenths, rounded up; characters; long private javadoc blocks} per file.
     */
    private void scanSource(String[] roots, Map<String, List<String>> history, Map<String, List<String>> longBlocks,
        Map<String, List<String>> ids, Map<String, int[]> shapes) throws IOException
    {
        List<File> files = new ArrayList<>();

        for (String root : roots)
        {
            File dir = new File(root);

            assertTrue(dir.isDirectory(), "cannot find " + dir.getAbsolutePath()
                + " - this test reads the source, so it has to run from the project root");

            collect(dir, files);
        }

        Collections.sort(files);

        Pattern idPattern = ids == null ? null : reviewIds();

        for (File file : files)
        {
            String path = file.getPath().replace(File.separatorChar, '/');

            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");

            Map<Integer, String> historyLines = new LinkedHashMap<>();

            List<String> blocks = new ArrayList<>();

            Map<Integer, List<String>> idLines = new LinkedHashMap<>();

            int[] shape = new int[2];

            scanFile(path, source, historyLines, blocks, idPattern, idLines, shape);

            if (shapes != null)
            {
                shapes.put(path, new int[] {(shape[0] * 1000 + source.length() - 1) / Math.max(1, source.length()),
                    source.length(), shape[1]});
            }

            if (history != null && !historyLines.isEmpty())
            {
                List<String> lines = new ArrayList<>();

                for (Map.Entry<Integer, String> each : historyLines.entrySet())
                {
                    lines.add(path + ":" + each.getKey() + ": " + each.getValue());
                }

                history.put(path, lines);
            }

            if (longBlocks != null && !blocks.isEmpty()) longBlocks.put(path, blocks);

            if (ids != null && !idLines.isEmpty())
            {
                List<String> lines = new ArrayList<>();

                for (Map.Entry<Integer, List<String>> each : idLines.entrySet())
                {
                    for (String id : each.getValue()) lines.add(path + ":" + each.getKey() + ": " + id);
                }

                ids.put(path, lines);
            }
        }
    }

    /** Any prefix the finding store holds except Adam's tracker ones, a dash, an optional capital letter and digits. */
    private Pattern reviewIds() throws IOException
    {
        Set<String> prefixes = new TreeSet<>();

        // The part of a first-column ref before its dash is a prefix of review ids.
        for (String line : support.Store.findings().split("\n"))
        {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;

            String ref = line.split("\t")[0].split(" @")[0];

            int dash = ref.indexOf('-');

            if (dash > 0 && !TRACKER_PREFIXES.contains(ref.substring(0, dash))) prefixes.add(ref.substring(0, dash));
        }

        assertTrue(prefixes.size() > 100, "only " + prefixes.size() + " prefixes were read from the finding store"
            + ", far fewer than it holds - its format has changed and review ids would go uncounted");

        StringBuilder any = new StringBuilder();

        for (String prefix : prefixes) any.append(any.length() == 0 ? "" : "|").append(Pattern.quote(prefix));

        return Pattern.compile("(?<![A-Za-z0-9_-])(?:" + any + ")-[A-Z]?\\d+[a-z]?(?![A-Za-z0-9_])");
    }

    /** Records each review id on a comment line, by line number; a null pattern records nothing. */
    private void noteIds(int line, String commentLine, Pattern idPattern, Map<Integer, List<String>> idLines)
    {
        if (idPattern == null) return;

        Matcher match = idPattern.matcher(commentLine);

        while (match.find())
        {
            List<String> here = idLines.get(line);

            if (here == null) idLines.put(line, here = new ArrayList<>());

            here.add(match.group());
        }
    }

    /**
     * One file: the comment lines that carry a history marker (by line number), the long blocks (both leaving out the
     * generated regions) and the review ids.  Comments are found the way javac finds them, so a `//` or a quote inside a
     * string literal is never a comment.  `shape` receives the comment characters and the long private javadocs.
     */
    private void scanFile(String path, String source, Map<Integer, String> historyLines, List<String> blocks,
        Pattern idPattern, Map<Integer, List<String>> idLines, int[] shape)
    {
        Set<Integer> generated = generatedLines(source);

        java.util.ArrayDeque<String> types = new java.util.ArrayDeque<>();

        StringBuilder header = new StringBuilder();

        List<Integer> fullLineComments = new ArrayList<>();

        int n = source.length();

        int line = 1;

        boolean codeOnLine = false;

        int i = 0;

        while (i < n)
        {
            char c = source.charAt(i);

            char next = i + 1 < n ? source.charAt(i + 1) : '\0';

            if (c == '\n')
            {
                line++;
                i++;
                codeOnLine = false;
            }
            else if (c == '"' || c == '\'')
            {
                i = pastLiteral(source, i);
                codeOnLine = true;
            }
            else if (c == '/' && next == '/')
            {
                int end = source.indexOf('\n', i);

                if (end < 0) end = n;

                noteHistory(path, line, source.substring(i, end), generated, historyLines);

                noteIds(line, source.substring(i, end), idPattern, idLines);

                shape[0] += end - i;

                if (!codeOnLine && !generated.contains(line)) fullLineComments.add(line);

                i = end;
            }
            else if (c == '/' && next == '*')
            {
                int end = source.indexOf("*/", i + 2);

                end = end < 0 ? n : end + 2;

                String text = source.substring(i, end);

                String[] parts = text.split("\n", -1);

                for (int k = 0; k < parts.length; k++)
                {
                    noteHistory(path, line + k, parts[k], generated, historyLines);

                    noteIds(line + k, parts[k], idPattern, idLines);
                }

                boolean javadoc = text.startsWith("/**") && !text.startsWith("/**/");

                shape[0] += text.length();

                if (javadoc && parts.length > PRIVATE_JAVADOC_LINES
                    && isPrivateDeclaration(types.peek(), source.substring(end, Math.min(n, end + 2000)))) shape[1]++;

                if (!javadoc && parts.length > BLOCK_LINES && !generated.contains(line))
                {
                    blocks.add(path + ":" + line + ": a block comment of " + parts.length + " lines");
                }

                line += parts.length - 1;
                i = end;
                codeOnLine = true;
            }
            else
            {
                if (!Character.isWhitespace(c)) codeOnLine = true;

                if (c == '{') types.push(typeOf(header.toString(), types.peek()));
                else if (c == '}' && !types.isEmpty()) types.pop();

                if (c == ';' || c == '{' || c == '}') header.setLength(0);
                else header.append(c);

                i++;
            }
        }

        int from = 0;

        for (int k = 1; k <= fullLineComments.size(); k++)
        {
            boolean ends = k == fullLineComments.size()
                || fullLineComments.get(k) != fullLineComments.get(k - 1) + 1;

            if (!ends) continue;

            if (k - from > BLOCK_LINES)
            {
                blocks.add(path + ":" + fullLineComments.get(from) + ": a run of " + (k - from) + " // lines");
            }

            from = k;
        }
    }

    /**
     * What the body opened after `header` is, for `isPrivateDeclaration`: "I" for the body of an interface whose
     * members are public (one declared public or protected, or nested in one), "E:Name" for an enum, else "C".
     */
    private String typeOf(String header, String parent)
    {
        Matcher kind = Pattern.compile("(?<![.\\w])(interface|enum|class)\\s+(\\w+)").matcher(header);

        if (!kind.find()) return "C";

        if (kind.group(1).equals("enum")) return "E:" + kind.group(2);

        return kind.group(1).equals("interface") && ("I".equals(parent)
            || Pattern.compile("\\b(?:public|protected)\\b").matcher(header).find()) ? "I" : "C";
    }

    /**
     * Whether the declaration in `after` (the text after a javadoc), inside a body that `typeOf` called `enclosing`
     * (null at top level), lacks a public or protected modifier.  Leading annotations are skipped by token, and the
     * modifiers are read before the declaration's `(`, `{`, `;` or `=`.  A member of a public interface and an enum
     * constant (a name followed by `,`, `;`, `}`, `(` or `{`, unless it is the enum's own constructor) are not private.
     */
    private boolean isPrivateDeclaration(String enclosing, String after)
    {
        if ("I".equals(enclosing)) return false;

        String rest = after.trim();

        while (rest.startsWith("@") && !rest.startsWith("@interface"))
        {
            int at = 1;

            while (at < rest.length() && (Character.isJavaIdentifierPart(rest.charAt(at)) || rest.charAt(at) == '.')) at++;

            rest = rest.substring(at).trim();

            if (rest.startsWith("("))
            {
                int depth = 0;

                int k = 0;

                do
                {
                    depth += rest.charAt(k) == '(' ? 1 : rest.charAt(k) == ')' ? -1 : 0;
                    k++;
                }
                while (depth > 0 && k < rest.length());

                rest = rest.substring(k).trim();
            }
        }

        if (enclosing != null && enclosing.startsWith("E:"))
        {
            Matcher constant = Pattern.compile("^(\\w+)\\s*([,;}({]|$)").matcher(rest);

            if (constant.find() && !(constant.group(1).equals(enclosing.substring(2)) && constant.group(2).equals("(")))
            {
                return false;
            }
        }

        Matcher end = Pattern.compile("[({;=]").matcher(rest);

        return !Pattern.compile("\\b(?:public|protected)\\b").matcher(end.find() ? rest.substring(0, end.start()) : rest)
            .find();
    }

    private void noteHistory(String path, int line, String commentLine, Set<Integer> generated,
        Map<Integer, String> historyLines)
    {
        if (generated.contains(line) || historyLines.containsKey(line)) return;

        for (Pattern marker : HISTORY_MARKERS)
        {
            if (marker.matcher(commentLine).find())
            {
                String text = commentLine.trim();

                historyLines.put(line, text.length() > 150 ? text.substring(0, 150) + "..." : text);

                return;
            }
        }
    }

    /** From the line holding `//GEN-BEGIN` to the one holding `//GEN-END`, both included. */
    private Set<Integer> generatedLines(String source)
    {
        Set<Integer> generated = new HashSet<>();

        boolean inside = false;

        int number = 0;

        for (String text : source.split("\n", -1))
        {
            number++;

            if (text.contains("//GEN-BEGIN")) inside = true;

            if (inside) generated.add(number);

            if (text.contains("//GEN-END")) inside = false;
        }

        return generated;
    }

    /** The offset just past the string or char literal opening at `at`. */
    private int pastLiteral(String source, int at)
    {
        char quote = source.charAt(at);

        int i = at + 1;

        while (i < source.length() && source.charAt(i) != quote && source.charAt(i) != '\n')
        {
            if (source.charAt(i) == '\\') i++;

            i++;
        }

        return Math.min(i + 1, source.length());
    }

    private void collect(File from, List<File> into)
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
