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
import java.util.regex.Pattern;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Comments in `src/` stay lean (CLAUDE.md, "Comments stay lean"): history in a comment and comment blocks over 15 lines
 * are counted, and neither count may go up.  A comment states the current rule and why; what the code used to do, which
 * round found it and who said what on which date belong in the commit message and the tracker.
 *
 * Two ratchets, each a total plus a per-file breakdown, so a swap between files (one trimmed, another grown) fails and
 * names both:
 * <ul>
 * <li>HISTORY: comment lines (in `//`, block and javadoc comments, never in a string literal or in code) carrying one of
 * the markers below.</li>
 * <li>LONG BLOCKS: a run of more than 15 consecutive `//` lines, or a non-javadoc block comment over 15 lines.  A
 * javadoc contract may be long.</li>
 * </ul>
 * Not counted: `test/`, the NetBeans generated regions (from a line holding `//GEN-BEGIN` to the one holding `//GEN-END`,
 * which includes the "Variables declaration - do not modify" block).
 *
 * The history markers, each measured against the whole of `src/` and kept only where most of its matches were history:
 * <ul>
 * <li>a date `20\d\d-\d\d-\d\d` (also covers "(Adam, 2026-...)" and "until 2026-..."): 392 lines, all attributions or "as
 * of" stories.</li>
 * <li>"(Adam, 20" and "until \d": 176 and 2 lines, all dated; kept for the day a date is written another way.</li>
 * <li>"used to be", or "used to" after it, this, that, they, there, here, which, who, and, but, else: 119 lines, nearly
 * all "it used to do X".  A bare "used to" is NOT counted: "a socket used to send" and "is used to" state a role.</li>
 * <li>"Round N" / "round N" and "review round": 15 lines, all review or build rounds.</li>
 * <li>"found by" only before a reviewer, review, sweep, rendering, audit or an id such as RSA36 or OV2-C3: 6 lines, all
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
    private static final int HISTORY_ALLOWED = 337;

    /** WHICH files carry them, so that a swap between files fails and names both.  Files at zero are not listed. */
    private static final String[] HISTORY_BY_FILE = {
        "src/org/traincontrol/automation/Edge.java (7)",
        "src/org/traincontrol/automation/HomeStaging.java (2)",
        "src/org/traincontrol/automation/Point.java (13)",
        "src/org/traincontrol/automation/TimetablePath.java (2)",
        "src/org/traincontrol/automationui/AutonomyBuilder.java (2)",
        "src/org/traincontrol/automationui/AutonomyChecks.java (2)",
        "src/org/traincontrol/automationui/AutonomyRefreshCallback.java (1)",
        "src/org/traincontrol/automationui/DiagramColours.java (7)",
        "src/org/traincontrol/automationui/DiagramMonitor.java (7)",
        "src/org/traincontrol/automationui/GraphReducer.java (1)",
        "src/org/traincontrol/automationui/LayoutPageEdit.java (2)",
        "src/org/traincontrol/automationui/SessionImport.java (2)",
        "src/org/traincontrol/automationui/StationIndex.java (3)",
        "src/org/traincontrol/automationui/TileGraph.java (1)",
        "src/org/traincontrol/automationui/TileOverlay.java (9)",
        "src/org/traincontrol/automationui/TilePorts.java (4)",
        "src/org/traincontrol/base/CommandRow.java (1)",
        "src/org/traincontrol/base/ConditionOutline.java (3)",
        "src/org/traincontrol/base/LayoutDiagram.java (1)",
        "src/org/traincontrol/base/LayoutDiagramComponent.java (5)",
        "src/org/traincontrol/base/Locomotive.java (4)",
        "src/org/traincontrol/base/NodeExpression.java (2)",
        "src/org/traincontrol/base/Route.java (3)",
        "src/org/traincontrol/base/RouteCommand.java (4)",
        "src/org/traincontrol/gui/AddLocomotive.java (4)",
        "src/org/traincontrol/gui/AddressLabel.java (1)",
        "src/org/traincontrol/gui/ArrivalSidePrompt.java (4)",
        "src/org/traincontrol/gui/AutoLocomotiveStatus.java (7)",
        "src/org/traincontrol/gui/AutonomyEditorPanel.java (8)",
        "src/org/traincontrol/gui/AutonomyMenu.java (13)",
        "src/org/traincontrol/gui/AutonomyOverlayToggle.java (9)",
        "src/org/traincontrol/gui/AutonomyReport.java (3)",
        "src/org/traincontrol/gui/AutonomyViewerPanel.java (20)",
        "src/org/traincontrol/gui/BusyDialog.java (3)",
        "src/org/traincontrol/gui/CurrentKeyRow.java (9)",
        "src/org/traincontrol/gui/DiagramMonitorDriver.java (4)",
        "src/org/traincontrol/gui/DiagramTileRegistry.java (1)",
        "src/org/traincontrol/gui/EditorTileMenus.java (3)",
        "src/org/traincontrol/gui/FacingPrompt.java (4)",
        "src/org/traincontrol/gui/GraphLocAssign.java (11)",
        "src/org/traincontrol/gui/HomeLocomotiveMenu.java (2)",
        "src/org/traincontrol/gui/LayoutEditor.java (5)",
        "src/org/traincontrol/gui/LayoutEditorAddressPopup.java (3)",
        "src/org/traincontrol/gui/LayoutEditorReshape.java (1)",
        "src/org/traincontrol/gui/LayoutEditorRightclickMenu.java (3)",
        "src/org/traincontrol/gui/LayoutEditorSelection.java (4)",
        "src/org/traincontrol/gui/LayoutFileActions.java (1)",
        "src/org/traincontrol/gui/LayoutLabel.java (1)",
        "src/org/traincontrol/gui/LoadingSpinner.java (7)",
        "src/org/traincontrol/gui/LocIconActions.java (1)",
        "src/org/traincontrol/gui/LocIconCropDialog.java (10)",
        "src/org/traincontrol/gui/LocomotiveMenuItems.java (1)",
        "src/org/traincontrol/gui/LocomotivePlaceholder.java (2)",
        "src/org/traincontrol/gui/LocomotiveSelector.java (2)",
        "src/org/traincontrol/gui/ManualReversalPrompt.java (10)",
        "src/org/traincontrol/gui/PositionAwareJFrame.java (3)",
        "src/org/traincontrol/gui/QuickFileChooser.java (1)",
        "src/org/traincontrol/gui/RightClickFunctionMenu.java (2)",
        "src/org/traincontrol/gui/RightClickMenuListener.java (2)",
        "src/org/traincontrol/gui/RightClickSelectorMenu.java (1)",
        "src/org/traincontrol/gui/StartupSplash.java (1)",
        "src/org/traincontrol/gui/TailCrossedPrompt.java (15)",
        "src/org/traincontrol/gui/TrainControlUI.java (4)",
        "src/org/traincontrol/gui/UsageHistogram.java (2)",
        "src/org/traincontrol/marklin/MarklinControlStation.java (4)",
        "src/org/traincontrol/marklin/MarklinFeedback.java (1)",
        "src/org/traincontrol/marklin/MarklinLocomotive.java (2)",
        "src/org/traincontrol/marklin/MarklinRoute.java (23)",
        "src/org/traincontrol/marklin/file/CS2File.java (6)",
        "src/org/traincontrol/marklin/udp/CSDetect.java (1)",
        "src/org/traincontrol/marklin/udp/NetworkProxy.java (3)",
        "src/org/traincontrol/model/View.java (4)",
        "src/org/traincontrol/model/ViewListener.java (1)",
        "src/org/traincontrol/util/ImageUtil.java (2)",
        "src/org/traincontrol/util/JarUpdater.java (7)",
        "src/org/traincontrol/util/SelfUpdate.java (3)",
        "src/org/traincontrol/util/Util.java (4)",
    };

    /** Runs of more than 15 `//` lines plus non-javadoc block comments over 15 lines, as they stand.  Never raise it. */
    private static final int LONG_BLOCKS_ALLOWED = 54;

    /** WHICH files carry them.  Files at zero are not listed. */
    private static final String[] LONG_BLOCKS_BY_FILE = {
        "src/org/traincontrol/automation/Point.java (2)",
        "src/org/traincontrol/automationui/AutonomyBuilder.java (1)",
        "src/org/traincontrol/automationui/GraphReducer.java (1)",
        "src/org/traincontrol/automationui/LayoutPageEdit.java (4)",
        "src/org/traincontrol/base/ConditionOutline.java (1)",
        "src/org/traincontrol/gui/AddLocomotive.java (1)",
        "src/org/traincontrol/gui/AutoLocomotiveStatus.java (2)",
        "src/org/traincontrol/gui/AutonomyMenu.java (2)",
        "src/org/traincontrol/gui/AutonomyOverlayToggle.java (2)",
        "src/org/traincontrol/gui/DiagramExport.java (1)",
        "src/org/traincontrol/gui/DiagramTileRegistry.java (1)",
        "src/org/traincontrol/gui/LayoutEditorRightclickMenu.java (1)",
        "src/org/traincontrol/gui/LayoutEditorSelection.java (5)",
        "src/org/traincontrol/gui/LayoutGrid.java (2)",
        "src/org/traincontrol/gui/LayoutLabel.java (2)",
        "src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java (1)",
        "src/org/traincontrol/gui/LocIconCropDialog.java (2)",
        "src/org/traincontrol/gui/LocomotiveFunctionAssign.java (1)",
        "src/org/traincontrol/gui/ManualReversalPrompt.java (4)",
        "src/org/traincontrol/gui/PositionAwareJFrame.java (1)",
        "src/org/traincontrol/gui/RightClickFunctionMenu.java (1)",
        "src/org/traincontrol/marklin/MarklinFeedback.java (1)",
        "src/org/traincontrol/marklin/MarklinLocomotive.java (2)",
        "src/org/traincontrol/marklin/MarklinRoute.java (7)",
        "src/org/traincontrol/marklin/file/CS2File.java (6)",
    };

    @Test
    public void testHistoryInCommentsOnlyGoesDown() throws Exception
    {
        Map<String, List<String>> found = new TreeMap<>();

        scanSource(found, null);

        ratchet("lines of history in comments", found, HISTORY_ALLOWED, HISTORY_BY_FILE, "HISTORY_ALLOWED",
            "HISTORY_BY_FILE", "a comment line with a date, 'used to', 'Round N' or 'found by <reviewer>' in it");
    }

    @Test
    public void testLongCommentBlocksOnlyGoDown() throws Exception
    {
        Map<String, List<String>> found = new TreeMap<>();

        scanSource(null, found);

        ratchet("comment blocks over " + BLOCK_LINES + " lines", found, LONG_BLOCKS_ALLOWED, LONG_BLOCKS_BY_FILE,
            "LONG_BLOCKS_ALLOWED", "LONG_BLOCKS_BY_FILE", "a run of more than " + BLOCK_LINES
            + " // lines or a non-javadoc block comment of more than " + BLOCK_LINES + " lines");
    }

    /**
     * The counts against the pinned ones: a rise fails naming the offenders, a fall fails asking for the number to be
     * lowered so that the gain is kept.
     */
    private void ratchet(String what, Map<String, List<String>> found, int allowed, String[] pinnedList,
        String totalName, String listName, String kind)
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
            + "file's other offenders (git diff shows which are yours)." + offenders + "\nsee CLAUDE.md, 'Comments "
            + "stay lean': state the current rule, put history in the commit message.  Do not raise " + totalName + ".");

        assertTrue(total == allowed && fell.isEmpty(),
            what + ": " + total + " remain, fewer than the " + allowed + " recorded.  Lower " + totalName + " to " + total
            + " so the improvement is kept, and in " + listName + ": " + fell);
    }

    /** Fills either map (the other may be null) with `path:line: text` for each offender, per file. */
    private void scanSource(Map<String, List<String>> history, Map<String, List<String>> longBlocks)
        throws IOException
    {
        File src = new File("src");

        assertTrue(src.isDirectory(), "cannot find " + src.getAbsolutePath()
            + " - this test reads the source, so it has to run from the project root");

        List<File> files = new ArrayList<>();

        collect(src, files);

        Collections.sort(files);

        for (File file : files)
        {
            String path = file.getPath().replace(File.separatorChar, '/');

            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");

            Map<Integer, String> historyLines = new LinkedHashMap<>();

            List<String> blocks = new ArrayList<>();

            scanFile(path, source, historyLines, blocks);

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
        }
    }

    /**
     * One file: the comment lines that carry a history marker (by line number) and the long blocks, leaving out the
     * generated regions.  Comments are found the way javac finds them, so a `//` or a quote inside a string literal is
     * never a comment.
     */
    private void scanFile(String path, String source, Map<Integer, String> historyLines, List<String> blocks)
    {
        Set<Integer> generated = generatedLines(source);

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

                if (!codeOnLine && !generated.contains(line)) fullLineComments.add(line);

                i = end;
            }
            else if (c == '/' && next == '*')
            {
                int end = source.indexOf("*/", i + 2);

                end = end < 0 ? n : end + 2;

                String text = source.substring(i, end);

                String[] parts = text.split("\n", -1);

                for (int k = 0; k < parts.length; k++) noteHistory(path, line + k, parts[k], generated, historyLines);

                boolean javadoc = text.startsWith("/**") && !text.startsWith("/**/");

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
