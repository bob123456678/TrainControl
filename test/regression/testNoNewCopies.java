package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
    private static final int GROUPS_ALLOWED = 136;

    /** Each group: the classes holding its copies with how many each, and the tokens of its longest copy. */
    private static final String[] GROUPS = {
        "ArrivalSidePrompt:1,FacingPrompt:1 (69)",
        "ArrivalSidePrompt:1,FacingPrompt:1 (60)",
        "AutonomyBuilder:1,AutonomyChecks:1 (75)",
        "AutonomyBuilder:2 (80)",
        "AutonomyBuilder:2 (78)",
        "AutonomyBuilder:2 (64)",
        "AutonomyBuilder:4 (84)",
        "AutonomyChecks:2 (83)",
        "AutonomyChecks:2 (63)",
        "AutonomyChecks:3 (108)",
        "AutonomyCompanionStore:1,AutonomySession:2 (99)",
        "AutonomyCompanionStore:2 (85)",
        "AutonomyCompanionStore:2 (77)",
        "AutonomyCompanionStore:2 (71)",
        "AutonomyCompanionStore:2 (67)",
        "AutonomyCompanionStore:2 (65)",
        "AutonomyCompanionStore:2 (63)",
        "AutonomyCompanionStore:2 (61)",
        "AutonomyCompanionStore:3 (136)",
        "AutonomyCompanionStore:3 (86)",
        "AutonomyCompanionStore:5 (185)",
        "AutonomyEditorPanel:1,EditorPrompts:1 (80)",
        "AutonomyEditorPanel:2 (66)",
        "AutonomyEditorPanel:3 (108)",
        "AutonomyEditorPanel:4 (146)",
        "AutonomyEditorPanel:4 (98)",
        "AutonomyMenu:2 (64)",
        "AutonomySession:2 (93)",
        "AutonomySession:2 (77)",
        "AutonomySession:2 (76)",
        "AutonomySession:2 (71)",
        "AutonomySession:2 (70)",
        "AutonomySession:2 (68)",
        "AutonomySession:2 (67)",
        "AutonomySession:2 (66)",
        "AutonomySession:2 (64)",
        "AutonomySession:2 (64)",
        "AutonomySession:2 (63)",
        "AutonomySession:2 (60)",
        "AutonomySession:3 (67)",
        "AutonomySettingsTab:11 (315)",
        "AutonomySettingsTab:2 (145)",
        "AutonomyViewerPanel:3 (62)",
        "CS2File:2 (98)",
        "CS2File:2 (79)",
        "CS2File:2 (76)",
        "CS2Message:1,LayoutDiagramComponent:2 (101)",
        "CS2Message:1,RouteCommand:1 (91)",
        "CS2Message:2 (242)",
        "Conversion:2 (79)",
        "CurrentKeyRow:1,TileAnnotation:1 (89)",
        "EditorMassAssign:2 (74)",
        "FeedbackEvents:1,FullAutonomyExample:1 (60)",
        "GraphLocAssign:1,LayoutRightclickAutonomyMenu:1 (72)",
        "HomeStaging:2 (72)",
        "HomeStaging:2 (64)",
        "ImageUtil:2 (113)",
        "Layout:1,TrainControlUI:1 (94)",
        "Layout:2 (85)",
        "Layout:2 (63)",
        "Layout:4 (189)",
        "LayoutDiagram:2 (122)",
        "LayoutDiagram:2 (78)",
        "LayoutDiagramComponent:2 (189)",
        "LayoutDiagramComponent:2 (171)",
        "LayoutEditor:1,TrainControlUI:1 (68)",
        "LayoutEditor:2 (73)",
        "LayoutEditor:3 (86)",
        "LayoutEditorRightclickMenu:2 (65)",
        "LayoutEditorUndo:2 (84)",
        "LayoutEditorUndo:2 (67)",
        "LayoutFileActions:2 (74)",
        "LayoutLabel:2 (63)",
        "LocIconActions:3 (83)",
        "LocIconActions:3 (75)",
        "LocIconCropDialog:1,TileAnnotation:1 (95)",
        "LocIconCropDialog:2 (66)",
        "LocomotiveKeyboardMapping:1,MarklinAccessory:1,TimetablePath:1 (105)",
        "MarklinControlStation:1,TrainControlUI:1 (72)",
        "MarklinControlStation:1,TrainControlUI:1 (72)",
        "MarklinControlStation:3 (64)",
        "MarklinLocomotive:2 (75)",
        "MarklinRoute:2 (85)",
        "MarklinSimpleComponent:2 (88)",
        "NodeAnd:1,NodeOr:1 (82)",
        "NodeAnd:1,NodeOr:1,NodeRouteCommand:1 (207)",
        "NodeExpression:2 (71)",
        "NodeExpression:2 (66)",
        "RightClickMenuListener:2 (64)",
        "RouteCommand:1,TrainControlUI:1 (138)",
        "RouteCommand:2 (109)",
        "RouteCommand:2 (64)",
        "RouteEditorFrame:2 (85)",
        "RouteEditorFrame:2 (76)",
        "RouteEditorFrame:2 (75)",
        "RouteEditorFrame:2 (73)",
        "RouteEditorFrame:2 (73)",
        "RouteEditorFrame:2 (65)",
        "RouteEditorFrame:2 (65)",
        "RouteEditorFrame:2 (62)",
        "RouteEditorFrame:2 (61)",
        "RowIcons:2 (77)",
        "RowIcons:2 (64)",
        "StationCaption:2 (86)",
        "TileAnnotation:1,TileOverlay:1 (101)",
        "TileAnnotation:2 (84)",
        "TileAnnotation:2 (68)",
        "TileAnnotation:3,TileOverlay:1 (86)",
        "TileGraph:1,TilePorts:1 (80)",
        "TileGraph:1,TileSelection:1 (72)",
        "TileGraph:2 (65)",
        "TileGraph:2 (64)",
        "TrainControlUI:2 (225)",
        "TrainControlUI:2 (209)",
        "TrainControlUI:2 (177)",
        "TrainControlUI:2 (93)",
        "TrainControlUI:2 (89)",
        "TrainControlUI:2 (83)",
        "TrainControlUI:2 (81)",
        "TrainControlUI:2 (79)",
        "TrainControlUI:2 (69)",
        "TrainControlUI:2 (67)",
        "TrainControlUI:2 (65)",
        "TrainControlUI:2 (62)",
        "TrainControlUI:2 (61)",
        "TrainControlUI:2 (60)",
        "TrainControlUI:2 (60)",
        "TrainControlUI:3 (168)",
        "TrainControlUI:3 (123)",
        "TrainControlUI:3 (101)",
        "TrainControlUI:3 (96)",
        "TrainControlUI:3 (95)",
        "TrainControlUI:3 (75)",
        "TrainControlUI:3 (66)",
        "TrainControlUI:4 (294)",
        "TrainControlUI:4 (276)",
    };

    private static final String ADVICE = "Call the method that already does it, or move the code into one both call "
        + "(CLAUDE.md, 'One rule, one place').  Do not raise GROUPS_ALLOWED or add to GROUPS.";

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

        List<String> rose = new ArrayList<>();
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
                    rose.add("NEW " + here.get(i).entry() + " in " + here.get(i).places);
                }
                else if (i >= here.size())
                {
                    fell.add("remove \"" + key + " (" + was.get(i) + ")\"");
                }
                else if (here.get(i).tokens > was.get(i))
                {
                    rose.add("GREW from " + was.get(i) + ": " + here.get(i).entry() + " in " + here.get(i).places);
                }
                else if (here.get(i).tokens < was.get(i))
                {
                    fell.add("change \"" + key + " (" + was.get(i) + ")\" to \"" + here.get(i).entry() + "\"");
                }
            }
        }

        assertTrue(rose.isEmpty() && found.size() <= GROUPS_ALLOWED,
            "copies: there are " + found.size() + " groups of 60 or more copied tokens, against " + GROUPS_ALLOWED
            + " pinned.  These are new or longer than pinned:\n  " + String.join("\n  ", rose) + "\n" + ADVICE);

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
