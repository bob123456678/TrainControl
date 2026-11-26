package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.CopyScan;

/**
 * No new copies: `src/` holds the groups of copied code (a run of 60 tokens or more standing in two places, names and
 * literals not counted) pinned below, and none may appear or grow.  A group falls to a merge into one method, and the
 * baseline is lowered to bank it.  `support.CopyScan` is the port of docs/tools/dupscan.py (same clusters on the same
 * tree) and gives each group's places.
 *
 * A group is named by the classes that hold its copies and how many each, so a class moving to another package, or a
 * copy edited in place, is the same group; two groups of one class set are told apart by their sizes.
 */
public class testNoNewCopies
{
    /** The groups as they stand.  Never raise it. */
    private static final int GROUPS_ALLOWED = 65;

    /**
     * The tokens standing in a second or later copy in `test/`, summed over its groups: an upper bound, so a commit that
     * removes copies need not touch it.  Never raise it; `docs/tools/codesize.py` prints the figure now, to lower it to.
     */
    private static final long TEST_COPIED_TOKENS_ALLOWED = 176566;

    /** Each group: the classes holding its copies with how many each, and the tokens of its longest copy. */
    private static final String[] GROUPS = {
        "AutonomyBuilder:1,AutonomyChecks:1 (75)",
        "AutonomyChecks:2 (63)",
        "AutonomyCompanionStore:2 (65)",
        "AutonomyCompanionStore:2 (64)",
        "AutonomyEditorPanel:1,EditorPrompts:1 (80)",
        "AutonomyEditorPanel:2 (66)",
        "AutonomyEditorPanel:3 (98)",
        "AutonomySession:2 (76)",
        "AutonomySession:2 (73)",
        "AutonomySession:2 (70)",
        "AutonomySession:2 (68)",
        "AutonomySession:2 (67)",
        "AutonomySession:2 (67)",
        "AutonomySession:2 (64)",
        "AutonomySession:2 (64)",
        "AutonomySession:2 (63)",
        "AutonomySession:3 (67)",
        "AutonomySettingsTab:2 (138)",
        "CS2File:2 (83)",
        "CS2File:2 (79)",
        "CS2Message:1,LayoutDiagramComponent:2 (101)",
        "CS2Message:1,RouteCommand:1 (91)",
        "CS2Message:2 (242)",
        "Conversion:2 (79)",
        "CurrentKeyRow:1,TileAnnotation:1 (89)",
        "FeedbackEvents:1,FullAutonomyExample:1 (60)",
        "HomeStaging:2 (60)",
        "ImageUtil:2 (113)",
        "Layout:1,TrainControlUI:1 (94)",
        "Layout:2 (63)",
        "LayoutDiagram:2 (122)",
        "LayoutDiagram:2 (78)",
        "LayoutDiagramComponent:2 (189)",
        "LayoutDiagramComponent:2 (171)",
        "LocomotiveIconCropDialog:1,TileAnnotation:1 (95)",
        "LocomotiveIconCropDialog:2 (66)",
        "LocomotiveButtonSlot:1,MarklinAccessory:1,TimetablePath:1 (105)",
        "MarklinControlStation:3 (64)",
        "MarklinLocomotive:2 (75)",
        "MarklinRoute:2 (85)",
        "MarklinSimpleComponent:2 (88)",
        "NodeAnd:1,NodeOr:1 (82)",
        "NodeAnd:1,NodeOr:1,NodeRouteCommand:1 (207)",
        "NodeExpression:2 (69)",
        "NodeExpression:2 (66)",
        "RouteCommand:1,TrainControlUI:1 (138)",
        "RouteCommand:2 (109)",
        "RouteCommand:2 (64)",
        "RouteEditorWindow:2 (65)",
        "RowIcons:2 (64)",
        "TileAnnotation:1,TileOverlay:1 (101)",
        "TileAnnotation:3,TileOverlay:1 (86)",
        "TileGraph:1,TilePorts:1 (80)",
        "TileGraph:1,TileSelection:1 (72)",
        "TrainControlUI:2 (225)",
        "TrainControlUI:2 (209)",
        "TrainControlUI:4 (179)", // the form-accessor runs the gui collaborators read through, lately AutonomyLifecycle
        "TrainControlUI:2 (93)",
        "TrainControlUI:2 (81)",
        "TrainControlUI:2 (62)",
        "TrainControlUI:2 (61)",
        "TrainControlUI:2 (60)",
        "TrainControlUI:3 (123)",
        "TrainControlUI:3 (101)",
        "TrainControlUI:3 (95)",
    };

    private static final String ADVICE = "Call the method that already does it, or move the code into one both call "
        + "(CLAUDE.md, 'One rule, one place').  Do not raise GROUPS_ALLOWED or add to GROUPS.";

    /**
     * The copies in `test/` only go down, as one total: the tokens standing in a second or later copy, over every group.
     * A total rather than pinned groups, since cutting a long copy in two leaves two shorter groups behind.  Every run
     * is paired however many places hold it: with the default cut-off, a copy past the fortieth would drop its group
     * from the total.
     *
     * MUTATION: pasting a 70-token claim body into a second test class fails this.
     */
    @Test
    public void testTestCopiesOnlyGoDown() throws Exception
    {
        List<CopyScan.Group> found = CopyScan.scan(new File("test"), Integer.MAX_VALUE);

        long copied = 0;

        for (CopyScan.Group g : found) copied += (long) g.tokens * (g.places.size() - 1);

        Collections.sort(found, new Comparator<CopyScan.Group>()
        {
            @Override
            public int compare(CopyScan.Group a, CopyScan.Group b)
            {
                return Long.compare((long) b.tokens * (b.places.size() - 1), (long) a.tokens * (a.places.size() - 1));
            }
        });

        if (copied <= TEST_COPIED_TOKENS_ALLOWED) return;

        Set<String> changed = changedFiles();

        Map<String, CopyScan.Group> largest = new LinkedHashMap<>();

        for (int i = 0; i < found.size(); i++)
        {
            CopyScan.Group g = found.get(i);

            List<String> places = g.places;

            if (i < 5 || touches(g, changed))
            {
                largest.put(g.entry() + " in " + places.subList(0, Math.min(6, places.size()))
                    + (places.size() > 6 ? " ..." : ""), g);
            }
        }

        fail("test copies rose: " + copied + " tokens stand in a second copy, against " + TEST_COPIED_TOKENS_ALLOWED
            + " pinned.  The groups with a copy in a file changed on this branch, then the largest:" + "\n" + "  "
            + mineFirst(largest, changed) + "\n" + "A claim's own set-up may stay written in it; shared steps belong in a"
            + " helper (test/support, or the class).  Do not raise TEST_COPIED_TOKENS_ALLOWED.");
    }

    private static Set<String> changedNames;

    /** The base names of the files this branch changed against dev, untracked ones too; empty when git cannot say. */
    private static synchronized Set<String> changedFiles()
    {
        if (changedNames != null) return changedNames;

        changedNames = new HashSet<>();

        String[][] asks = {{"diff", "--name-only", "dev"}, {"diff", "--name-only", "HEAD"},
            {"ls-files", "--others", "--exclude-standard"}};

        for (int i = 0; i < asks.length; i++)
        {
            try
            {
                File out = File.createTempFile("copies-git", ".txt");

                try
                {
                    List<String> command = new ArrayList<>(Arrays.asList("git"));

                    command.addAll(Arrays.asList(asks[i]));

                    Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(out).start();

                    process.getOutputStream().close();

                    if (!process.waitFor(30, TimeUnit.SECONDS))
                    {
                        process.destroyForcibly();
                    }
                    else if (process.exitValue() == 0)
                    {
                        for (String line : Files.readAllLines(out.toPath(), StandardCharsets.UTF_8))
                        {
                            changedNames.add(line.substring(line.lastIndexOf('/') + 1));
                        }

                        if (i == 0) i++;  // dev named the whole branch; HEAD would add nothing
                    }
                }
                finally
                {
                    out.delete();
                }
            }
            catch (Exception e)
            {
                // no git: the groups then come in the scan's order
            }
        }

        return changedNames;
    }

    private static boolean touches(CopyScan.Group g, Set<String> changed)
    {
        for (String place : g.places) if (changed.contains(place.substring(0, place.indexOf(':')))) return true;

        return false;
    }

    /** The listed groups, those with a copy in one of the `changed` files first and marked, then the rest. */
    static String mineFirst(Map<String, CopyScan.Group> lines, Set<String> changed)
    {
        List<String> mine = new ArrayList<>();
        List<String> rest = new ArrayList<>();

        for (Map.Entry<String, CopyScan.Group> e : lines.entrySet())
        {
            boolean here = touches(e.getValue(), changed);

            (here ? mine : rest).add((here ? "(in a file you changed) " : "") + e.getKey());
        }

        mine.addAll(rest);

        return String.join("\n  ", mine);
    }

    /**
     * The scan of `src/` against the baseline: a group that is not pinned, or is longer than its pin, fails with its
     * places; a group that is gone or shorter fails asking for the baseline to be lowered.
     *
     * MUTATION: pasting a 70-token method into a second source file fails this; merging a pinned group without lowering
     * the baseline fails it too.
     */
    @Test
    public void testCopiesOnlyGoDown() throws Exception
    {
        Map<String, List<Integer>> pinned = new TreeMap<>();

        for (String entry : GROUPS)
        {
            int open = entry.lastIndexOf(" (");

            String key = entry.substring(0, open);

            if (!pinned.containsKey(key)) pinned.put(key, new ArrayList<Integer>());

            pinned.get(key).add(Integer.parseInt(entry.substring(open + 2, entry.length() - 1)));
        }

        assertEquals(GROUPS.length, GROUPS_ALLOWED, "GROUPS lists " + GROUPS.length + " but GROUPS_ALLOWED is "
            + GROUPS_ALLOWED + " - keep the two together");

        Map<String, List<CopyScan.Group>> now = new TreeMap<>();

        List<CopyScan.Group> found = CopyScan.scan(new File("src"));

        for (CopyScan.Group g : found)
        {
            if (!now.containsKey(g.key())) now.put(g.key(), new ArrayList<CopyScan.Group>());

            now.get(g.key()).add(g);
        }

        Map<String, CopyScan.Group> rose = new LinkedHashMap<>();
        List<String> fell = new ArrayList<>();

        Set<String> keys = new TreeSet<>(now.keySet());

        keys.addAll(pinned.keySet());

        for (String key : keys)
        {
            List<CopyScan.Group> here = now.containsKey(key) ? now.get(key) : new ArrayList<CopyScan.Group>();

            Collections.sort(here, new Comparator<CopyScan.Group>()
            {
                @Override
                public int compare(CopyScan.Group a, CopyScan.Group b)
                {
                    return b.tokens - a.tokens;
                }
            });

            List<Integer> was = pinned.containsKey(key) ? pinned.get(key) : new ArrayList<Integer>();

            Collections.sort(was, Collections.reverseOrder());

            for (int i = 0; i < Math.max(here.size(), was.size()); i++)
            {
                if (i >= was.size())
                {
                    rose.put("NEW " + here.get(i).entry() + " in " + here.get(i).places, here.get(i));
                }
                else if (i >= here.size())
                {
                    fell.add("remove \"" + key + " (" + was.get(i) + ")\"");
                }
                else if (here.get(i).tokens > was.get(i))
                {
                    rose.put("GREW from " + was.get(i) + ": " + here.get(i).entry() + " in " + here.get(i).places,
                        here.get(i));
                }
                else if (here.get(i).tokens < was.get(i))
                {
                    fell.add("change \"" + key + " (" + was.get(i) + ")\" to \"" + here.get(i).entry() + "\"");
                }
            }
        }

        if (!rose.isEmpty() || found.size() > GROUPS_ALLOWED)
        {
            fail("copies: there are " + found.size() + " groups of 60 or more copied tokens, against " + GROUPS_ALLOWED
                + " pinned.  These are new or longer than pinned, those with a copy in a file changed on this branch"
                + " first:" + "\n" + "  " + mineFirst(rose, changedFiles()) + "\n" + ADVICE);
        }

        assertTrue(fell.isEmpty() && found.size() == GROUPS_ALLOWED,
            "copies: " + found.size() + " groups remain, fewer than the " + GROUPS_ALLOWED + " recorded.  Lower "
            + "GROUPS_ALLOWED to " + found.size() + " so the improvement is kept, and in GROUPS: " + fell);
    }

    /**
     * The scan sees a copy under other names, literals and comments, ignores imports and the package, and does not see a
     * short run; a class moving to another package is the same group.
     *
     * MUTATION: counting names, literals or comments as themselves in `CopyScan`, or lowering `MIN`, fails this.
     */
    @Test
    public void testTheScanSeesACopyAndNotAShortRun()
    {
        Map<String, String> sources = new LinkedHashMap<>();

        sources.put("org/x/one/A.java", source("one", "A", "alpha", 1, 12, "// the first"));
        sources.put("org/x/two/B.java", source("two", "B", "omega", 2, 12, "/* another comment\n over two lines */"));

        List<CopyScan.Group> groups = CopyScan.scan(sources);

        assertEquals(groups.size(), 1, "a method copied with other names, numbers and comments is one group");
        assertEquals(groups.get(0).key(), "A:1,B:1");
        assertTrue(groups.get(0).tokens >= CopyScan.MIN, "the group is " + groups.get(0).tokens + " tokens");

        Map<String, String> moved = new LinkedHashMap<>();

        moved.put("org/x/one/sub/A.java", sources.get("org/x/one/A.java"));
        moved.put("org/x/two/B.java", sources.get("org/x/two/B.java"));

        assertEquals(CopyScan.scan(moved).get(0).entry(), groups.get(0).entry(),
            "a class moved to a sub-package is the same group");

        Map<String, String> shorter = new LinkedHashMap<>();

        shorter.put("org/x/one/A.java", source("one", "A", "alpha", 1, 8, ""));
        shorter.put("org/x/two/B.java", source("two", "B", "omega", 2, 8, ""));

        assertEquals(CopyScan.scan(shorter).size(), 0, "a run under 60 tokens is not a copy");
    }

    /**
     * The failure message names first the group with a copy in a file the branch changed.
     *
     * MUTATION: listing the groups in scan order, not changed ones first, fails this.
     */
    @Test
    public void testTheMessageNamesTheChangedFilesGroupFirst()
    {
        Map<String, String> sources = new LinkedHashMap<>();

        for (String c : new String[] {"A", "B", "C", "D"})
        {
            String text = source(c.toLowerCase(), c, c, 1, 12, "");

            sources.put(c + ".java", c.compareTo("C") < 0 ? text : text.replace(" + ", " - "));
        }

        Map<String, CopyScan.Group> lines = new LinkedHashMap<>();

        for (CopyScan.Group g : CopyScan.scan(sources)) lines.put("NEW " + g.entry() + " in " + g.places, g);

        assertEquals(lines.size(), 2, "two groups of copies");

        String text = mineFirst(lines, new HashSet<>(Arrays.asList("D.java")));

        assertTrue(text.startsWith("(in a file you changed) NEW C:1,D:1"), "the changed file's group first: " + text);
        assertTrue(text.contains("\n  NEW A:1,B:1"), "then the other: " + text);
    }

    /** A class with one method of `statements` statements of 6 tokens each, and 6 more around them. */
    private static String source(String pkg, String cls, String name, int literal, int statements, String comment)
    {
        StringBuilder body = new StringBuilder();

        for (int i = 0; i < statements; i++)
        {
            body.append("        ").append(name).append(i).append(" = ").append(name).append(i).append(" + ")
                .append(literal).append(";\n");
        }

        return "package org.x." + pkg + ";\nimport java.util." + (pkg.equals("one") ? "List" : "Map") + ";\n"
            + comment + "\npublic class " + cls + " {\n    void " + name + "()\n    {\n" + body + "    }\n}\n";
    }
}
