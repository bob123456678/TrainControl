package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * A wrapper must not call itself.
 *
 * This exists because of a specific bug, and it is worth saying plainly what happened. Sixteen call
 * sites were mechanically rewritten from `this.model.syncWithCS2()` to `this.syncWithCS2()` so they
 * would go through a new wrapper - and the rewrite also caught the two calls INSIDE that wrapper. The
 * wrapper then called itself: off the event thread directly, and on it through a worker, which is also
 * off it. Every Central Station sync became a StackOverflowError.
 *
 * Nothing caught it. The suite tests the model's sync, which was the half still working; the compiler
 * has no opinion about recursion; and the battery was green. It took a reviewer reading the method.
 *
 * So this reads the source instead. That is an unusual thing for a test to do and it is the right tool
 * here: the fault is a textual one introduced by a textual edit, it is invisible to every other check,
 * and the shape - a method whose body's only call to that name is itself - is precise enough to test
 * without false alarms. It costs nothing and it covers a mistake that would otherwise be made again the
 * next time a delegating wrapper is introduced.
 */
public class testNoSelfRecursiveWrappers
{
    /**
     * The wrappers that delegate to the model, checked for delegating to themselves instead.
     *
     * Deliberately narrow. A general "no method may call itself" rule would be wrong - recursion is a
     * normal thing to write - so this looks only at methods on a UI class that share a name with a
     * model method they are supposed to be forwarding to.
     */
    @Test
    public void testAUiWrapperDoesNotCallItselfInsteadOfTheModel() throws Exception
    {
        File source = new File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(source.isFile(),
            "cannot find " + source.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);

        int checked = 0;

        for (int i = 0; i < lines.size(); i++)
        {
            String declaration = lines.get(i).trim();

            if (!declaration.startsWith("public ") || !declaration.contains("(")) continue;

            String name = nameOf(declaration);

            if (name == null || !forwardsToTheModel(lines, name)) continue;

            checked++;

            // Only within THIS method's body.  A name that exists on both the UI and the model is
            // ordinary - saveState is one - so what matters is whether the body calls the name it is
            // itself declaring, which is the recursion.
            for (String line : bodyOf(lines, i))
            {
                assertFalse(callsItself(line, name, argumentCount(
                        declaration.substring(declaration.indexOf('(') + 1,
                            declaration.lastIndexOf(')')))),
                    name + " calls itself from inside its own body instead of calling "
                    + "this.model." + name + "(...) - an infinite recursion the compiler accepts, the "
                    + "battery does not notice, and the user meets as a StackOverflowError.  Line: "
                    + line.trim());
            }
        }

        assertTrue(checked > 0,
            "no forwarding wrappers were found at all, so this test is checking nothing - the shape it "
            + "looks for has probably changed");
    }

    private static final String WINDOW = "src/org/traincontrol/gui/TrainControlUI.java";

    private static final String SESSION = "src/org/traincontrol/automationui/AutonomySession.java";

    private static final String EDITOR = "src/org/traincontrol/gui/layouteditor/LayoutEditor.java";

    private static final String AUTONOMY_EDITOR = "src/org/traincontrol/gui/autonomyeditor/AutonomyEditorPanel.java";

    /**
     * The collaborators (FR-136), one row each: the field of the owner that holds it, the file it is declared in, its
     * class name - for the static delegators, which reach it as `Class.method(` rather than through the field - the
     * owner's file and class name, and the prefix the collaborator calls its owner by (`ui.`, `owner.`, `editor.`).
     */
    private static final String[][] COLLABORATORS = {
        { "backupAction", "src/org/traincontrol/gui/widgets/BackupAction.java", "BackupAction", WINDOW, "TrainControlUI", "ui." },
        { "updateFlow", "src/org/traincontrol/gui/widgets/UpdateFlow.java", "UpdateFlow", WINDOW, "TrainControlUI", "ui." },
        { "locIconActions", "src/org/traincontrol/gui/LocIconActions.java", "LocIconActions", WINDOW, "TrainControlUI",
            "ui." },
        { "layoutFileActions", "src/org/traincontrol/gui/LayoutFileActions.java", "LayoutFileActions", WINDOW,
            "TrainControlUI", "ui." },
        { "routeListPanel", "src/org/traincontrol/gui/RouteListPanel.java", "RouteListPanel", WINDOW, "TrainControlUI",
            "ui." },
        { "sessionImport", "src/org/traincontrol/automationui/SessionImport.java", "SessionImport", SESSION,
            "AutonomySession", "owner." },
        { "sessionRuns", "src/org/traincontrol/automationui/SessionRuns.java", "SessionRuns", SESSION,
            "AutonomySession", "owner." },
        { "selectionOps", "src/org/traincontrol/gui/layouteditor/LayoutEditorSelection.java", "LayoutEditorSelection", EDITOR,
            "LayoutEditor", "editor." },
        { "reshapeOps", "src/org/traincontrol/gui/layouteditor/LayoutEditorReshape.java", "LayoutEditorReshape", EDITOR,
            "LayoutEditor", "editor." },
        { "undoOps", "src/org/traincontrol/gui/layouteditor/LayoutEditorUndo.java", "LayoutEditorUndo", EDITOR,
            "LayoutEditor", "editor." },
        { "autonomySettingsTab", "src/org/traincontrol/gui/AutonomySettingsTab.java", "AutonomySettingsTab", WINDOW,
            "TrainControlUI", "ui." },
        { "locomotiveActions", "src/org/traincontrol/gui/LocomotiveActions.java", "LocomotiveActions", WINDOW,
            "TrainControlUI", "ui." },
        { "timetableTab", "src/org/traincontrol/gui/TimetableTab.java", "TimetableTab", WINDOW,
            "TrainControlUI", "ui." },
        { "coveredTrackView", "src/org/traincontrol/gui/CoveredTrackView.java", "CoveredTrackView", WINDOW,
            "TrainControlUI", "ui." },
        { "captionVisibility", "src/org/traincontrol/gui/CaptionVisibility.java", "CaptionVisibility", WINDOW,
            "TrainControlUI", "ui." },
        { "tileMenus", "src/org/traincontrol/gui/autonomyeditor/EditorTileMenus.java", "EditorTileMenus", AUTONOMY_EDITOR,
            "AutonomyEditorPanel", "editor." },
        { "prompts", "src/org/traincontrol/gui/autonomyeditor/EditorPrompts.java", "EditorPrompts", AUTONOMY_EDITOR,
            "AutonomyEditorPanel", "editor." },
        { "signalPairing", "src/org/traincontrol/gui/autonomyeditor/EditorSignals.java", "EditorSignals", AUTONOMY_EDITOR,
            "AutonomyEditorPanel", "editor." },
        { "massAssignment", "src/org/traincontrol/gui/autonomyeditor/EditorMassAssign.java", "EditorMassAssign", AUTONOMY_EDITOR,
            "AutonomyEditorPanel", "editor." },
        { "whyAnswers", "src/org/traincontrol/gui/autonomyeditor/EditorWhy.java", "EditorWhy", AUTONOMY_EDITOR,
            "AutonomyEditorPanel", "editor." },
        { "RunGates", "src/org/traincontrol/gui/RunGates.java", "RunGates", WINDOW, "TrainControlUI", "ui." },
        { "returnHomeController", "src/org/traincontrol/gui/ReturnHomeController.java", "ReturnHomeController", WINDOW,
            "TrainControlUI", "ui." },
        { "runningLayoutSync", "src/org/traincontrol/gui/RunningLayoutSync.java", "RunningLayoutSync", WINDOW,
            "TrainControlUI", "ui." },
        { "editorWindows", "src/org/traincontrol/gui/EditorWindows.java", "EditorWindows", WINDOW,
            "TrainControlUI", "ui." },
        { "autonomyLifecycle", "src/org/traincontrol/gui/AutonomyLifecycle.java", "AutonomyLifecycle", WINDOW,
            "TrainControlUI", "ui." },
        { "diagramLocGestures", "src/org/traincontrol/gui/DiagramLocGestures.java", "DiagramLocGestures", WINDOW,
            "TrainControlUI", "ui." },
    };

    /**
     * A delegator and the collaborator it hands to do not call each other for ever (FR-136): a StackOverflowError the
     * compiler accepts.  Methods are told apart by name AND number of arguments.
     *
     * MUTATION: in BackupAction, make `writeTheBackup` call `this.ui.backupData()`, and this fails; so does making
     * UpdateFlow's static `whenNoModalDialog` call `TrainControlUI.whenNoModalDialog(`, SessionRuns' `runs()` return
     * `owner.runs()`, LayoutEditorUndo's `undo()` call `editor.undo()`, TrainControlUI's `combineLinkedPages` delegator
     * call `this.combineLinkedPages()` (or a bare `combineLinkedPages()`) instead of
     * `layoutFileActions.combineLinkedPages()`, EditorTileMenus' `addFacingItems` begin
     * `if (target == null) return editor.addFacingItems(menu, target);`, or AutonomyEditorPanel's `addFacingItems` return
     * `addFacingItems(menu, target)`.
     */
    @Test
    public void testADelegatorAndItsCollaboratorDoNotCallEachOtherForEver() throws Exception
    {
        for (String[] each : COLLABORATORS)
        {
            assertTrue(new File(each[3]).isFile(), "cannot find " + new File(each[3]).getAbsolutePath());

            List<String> window = Files.readAllLines(new File(each[3]).toPath(), StandardCharsets.UTF_8);

            File source = new File(each[1]);

            assertTrue(source.isFile(), "cannot find " + source.getAbsolutePath());

            List<String> helper = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);

            // The collaborator calls its owner back through its back-prefix (or `OwnerClass.` for a static)
            assertNoLoopBetween(window, helper, each[0], each[2], each[4], each[5]);
        }
    }

    /**
     * Reads an owner's source and its collaborator's: no delegator hands to a method that calls back into it, and no
     * method of the owner named like one of the collaborator's calls its own name instead.  The owner calls the
     * collaborator through `<field>.` (or `Class.` for a static); the collaborator calls the owner through `backPrefix`
     * or `OwnerClass.`.
     */
    private static void assertNoLoopBetween(List<String> owner, List<String> helper, String field, String helperClass,
        String ownerClass, String backPrefix)
    {
        // What each side calls on the other
        java.util.Map<String, java.util.Set<String>> ownerCalls = merged(
            callsThrough(owner, field + "."), callsThrough(owner, helperClass + "."));
        java.util.Map<String, java.util.Set<String>> helperCalls = merged(
            callsThrough(helper, backPrefix), callsThrough(helper, ownerClass + "."));

        int checked = 0;

        for (java.util.Map.Entry<String, java.util.Set<String>> delegator : ownerCalls.entrySet())
        {
            for (String handedTo : delegator.getValue())
            {
                checked++;

                java.util.Set<String> back = helperCalls.get(handedTo);

                assertFalse(back != null && back.contains(delegator.getKey()),
                    ownerClass + "." + delegator.getKey() + " hands to " + helperClass + "." + handedTo
                    + ", which calls back into " + ownerClass + "." + delegator.getKey() + " - a loop the compiler accepts");
            }
        }

        assertTrue(checked > 0, ownerClass + " hands nothing to " + field + ", so this checks nothing");

        // And a delegator that calls its own name, bare or through `this.`, instead of the collaborator's.  Every method of
        // the owner named like one of the collaborator's is read, since a delegator rewritten that way names the
        // collaborator nowhere; the arity test in `callsItself` leaves another overload alone.
        java.util.Set<String> helperNames = declaredNames(helper);

        int delegators = 0;

        for (int i = 0; i < owner.size(); i++)
        {
            String name = methodDeclaredAt(owner.get(i));

            if (name == null || !helperNames.contains(name)) continue;

            delegators++;

            int arity = arityOfTheDeclarationAt(owner, i);

            for (String line : bodyOf(owner, i))
            {
                assertFalse(callsItself(line, name, arity), ownerClass + "." + name + " calls itself instead of "
                    + field + "." + name + ".  Line: " + line.trim());
            }
        }

        assertTrue(delegators > 0, "no method of " + ownerClass + " is named like one of " + helperClass + "'s, so this"
            + " checks nothing");
    }

    /** The names of the methods a file declares at a class's top level. */
    private static java.util.Set<String> declaredNames(List<String> lines)
    {
        java.util.Set<String> names = new java.util.HashSet<>();

        for (String line : lines)
        {
            String declared = methodDeclaredAt(line);

            if (declared != null) names.add(declared);
        }

        return names;
    }

    /** The `.java` files under a folder. */
    private static List<File> javaFilesUnder(String folder) throws java.io.IOException
    {
        List<File> files = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walked = Files.walk(new File(folder).toPath()))
        {
            walked.filter(path -> path.toString().endsWith(".java")).forEach(path -> files.add(path.toFile()));
        }

        return files;
    }

    /** The statements of a method's body, without comments or braces: what a one-line delegator is read by. */
    private static List<String> statementsOfTheBody(List<String> lines, int declaredAt)
    {
        List<String> statements = new ArrayList<>();

        for (String line : bodyOf(lines, declaredAt))
        {
            String code = withoutComments(line).replace("{", "").replace("}", "").trim();

            if (!code.isEmpty()) statements.add(code);
        }

        return statements;
    }

    /** How many parameters the method declared at a line takes, reading on while they run onto the next lines. */
    private static int arityOfTheDeclarationAt(List<String> lines, int at)
    {
        String declaration = lines.get(at).trim();

        for (int k = at + 1; declaration.lastIndexOf(')') < declaration.indexOf('(') && k < lines.size(); k++)
        {
            declaration += " " + lines.get(k).trim();
        }

        return argumentCount(declaration.substring(declaration.indexOf('(') + 1, declaration.lastIndexOf(')')));
    }

    /** Two maps of method name to the names it calls, as one. */
    private static java.util.Map<String, java.util.Set<String>> merged(java.util.Map<String, java.util.Set<String>> first,
        java.util.Map<String, java.util.Set<String>> second)
    {
        java.util.Map<String, java.util.Set<String>> both = new java.util.HashMap<>(first);

        second.forEach((name, called) -> both.computeIfAbsent(name, k -> new java.util.HashSet<>()).addAll(called));

        return both;
    }

    /**
     * For each method declared at the class's top level, the methods it calls through a prefix - each as `name/arguments`
     * (the declared one too), so that one overload handing to another is not read as a loop.
     */
    private static java.util.Map<String, java.util.Set<String>> callsThrough(List<String> lines, String prefix)
    {
        java.util.Map<String, java.util.Set<String>> calls = new java.util.HashMap<>();

        java.util.regex.Pattern call = java.util.regex.Pattern.compile(
            "(?<!\\w)" + java.util.regex.Pattern.quote(prefix) + "(\\w+)\\s*\\(");

        for (int i = 0; i < lines.size(); i++)
        {
            String name = methodDeclaredAt(lines.get(i));

            if (name == null) continue;

            String declared = name + "/" + arityOfTheDeclarationAt(lines, i);

            for (String line : bodyOf(lines, i))
            {
                String code = withoutComments(line);

                java.util.regex.Matcher m = call.matcher(code);

                while (m.find())
                {
                    calls.computeIfAbsent(declared, k -> new java.util.HashSet<>())
                        .add(m.group(1) + "/" + argumentCount(argumentsFrom(code, m.end())));
                }
            }
        }

        return calls;
    }

    /**
     * The text of a call's arguments: from just after its opening bracket to the bracket that closes it, or to the end of
     * the line when the call runs on.
     */
    private static String argumentsFrom(String code, int from)
    {
        int depth = 1;

        for (int at = from; at < code.length(); at++)
        {
            char c = code.charAt(at);

            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return code.substring(from, at);
        }

        return code.substring(from);
    }

    /** The name of the method a line declares at a class's top level (four spaces in), or null. */
    private static String methodDeclaredAt(String line)
    {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "^    (?:(?:public|private|protected|static|final|synchronized)\\s+)*[\\w<>\\[\\],.?]+(?:<[^=]*>)?\\s+(\\w+)\\s*\\([^=;]*$")
            .matcher(line);

        if (!m.find()) return null;

        String name = m.group(1);

        return "new".equals(name) || "return".equals(name) ? null : name;
    }

    /**
     * The session's one-line delegators call their collaborator, never themselves (FR-136): `AutonomySession` keeps
     * each public method whose body moved to a package-private collaborator as a one-line call to it, and a mechanical
     * rewrite that drops the collaborator's name leaves a method calling itself.
     *
     * MUTATION: make any of them call its own name instead of the collaborator's, and this fails.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testASessionDelegatorDoesNotCallItself() throws Exception
    {
        List<String> lines = java.util.Arrays.asList(
            support.SourceOf.declaration("public class AutonomySession\n").split("\n"));

        String[] collaborators = {"sessionImport.", "SessionImport.", "sessionRuns."};

        int checked = 0;

        for (int i = 0; i < lines.size(); i++)
        {
            String declaration = lines.get(i).trim();

            if (!declaration.startsWith("public ") || !declaration.contains("(")) continue;

            String name = nameOf(declaration);

            if (name == null) continue;

            // A ONE-STATEMENT BODY, the delegator's shape, whether it still calls the collaborator or has been
            // rewritten to call itself
            List<String> statements = statementsOfTheBody(lines, i);

            if (statements.size() != 1) continue;

            String statement = statements.get(0);

            // A signature wrapped over several lines, whole
            int arity = arityOfTheDeclarationAt(lines, i);

            assertFalse(callsItself(statement, name, arity),
                name + " calls itself instead of its collaborator - an infinite recursion the compiler accepts."
                + "  Line: " + statement);

            for (String collaborator : collaborators)
            {
                if (statement.contains(collaborator + name + "(")) checked++;
            }
        }

        assertTrue(checked >= 8, "only " + checked + " of the session's delegators were found, so this checked almost"
            + " nothing - the shape it looks for has probably changed");
    }

    /**
     * The railway's helpers (FR-136): the field of Layout that holds each, the file it is declared in, and its class
     * name - for the static delegators, which reach it as `Class.method(`.
     */
    private static final String[][] LAYOUT_HELPERS = {
        { "layoutExplanations", "src/org/traincontrol/automation/LayoutExplanations.java", "LayoutExplanations" },
        { "standingTrains", "src/org/traincontrol/automation/StandingTrains.java", "StandingTrains" },
        // All static, so no field: the railway hands to it as `TrainFit.`
        { "TrainFit", "src/org/traincontrol/automation/TrainFit.java", "TrainFit" },
    };

    /**
     * The railway's delegators and the helpers they hand to do not call each other for ever (FR-136).  Both loops are
     * read: a delegator calling its own name instead of the helper's, and a helper's method calling back into the
     * delegator that leads to it; so are the helpers' own hand-offs between overloads.
     *
     * MUTATION: make LayoutExplanations.explainCannotStart(Locomotive) end `return owner.explainCannotStart(loc);`, or
     * make Layout.whyNoRouteFitsTo call `whyNoRouteFitsTo(loc, start, end)`, and this fails; so does making
     * LayoutExplanations' `explainCannotStart(loc, byHand)` hand to `explainCannotStart(loc, false)`, or
     * StandingTrains' six-argument `walkOneTail` call `walkOneTail(` with six arguments.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testARailwayDelegatorAndItsHelperDoNotCallEachOtherForEver() throws Exception
    {
        List<String> railway = Files.readAllLines(
            new File("src/org/traincontrol/automation/Layout.java").toPath(), StandardCharsets.UTF_8);

        for (String[] each : LAYOUT_HELPERS)
        {
            File source = new File(each[1]);

            assertTrue(source.isFile(), "cannot find " + source.getAbsolutePath());

            List<String> helper = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);

            // The helper calls the railway back through `owner.` (or `Layout.` for a static)
            assertNoLoopBetween(railway, helper, each[0], each[2], "Layout", "owner.");

            // AND THE HELPER'S OWN HAND-OFFS, bare, at their own arity.  Another arity is another overload (the idiom), the
            // same arity is the helper calling itself.
            int handOffs = 0;

            for (int i = 0; i < helper.size(); i++)
            {
                String name = methodDeclaredAt(helper.get(i));

                if (name == null) continue;

                handOffs++;

                int arity = arityOfTheDeclarationAt(helper, i);

                for (String line : bodyOf(helper, i))
                {
                    assertFalse(callsItself(line, name, arity),
                        each[2] + "." + name + " calls itself.  Line: " + line.trim());
                }
            }

            assertTrue(handOffs > 0, each[2] + " declares no methods, so this checks nothing");
        }
    }

    /**
     * Only the railway assigns its `edges` and `points` (FR-136): Layout writes them under its monitor and builds the
     * adjacency beside them, so a write from another class would skip both.  No other file of the package assigns
     * either through another object or calls a mutator on it, and Layout declares both `final`.
     *
     * MUTATION: have any other class of the package call `layout.points.put(`, `owner.edges.remove(` or assign
     * `layout.points = `, and this fails; so does dropping `final` from either declaration in Layout.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testOnlyTheRailwayAssignsItsEdgesAndPoints() throws Exception
    {
        java.util.regex.Pattern write = java.util.regex.Pattern.compile("\\.\\s*(edges|points)\\s*(?:=(?!=)"
            + "|\\.\\s*(?:put|putAll|putIfAbsent|remove|clear|compute|computeIfAbsent|computeIfPresent|merge|replace"
            + "|replaceAll)\\s*\\("
            + "|\\.\\s*(?:keySet|values|entrySet)\\s*\\(\\s*\\)\\s*\\.\\s*(?:remove|removeAll|removeIf|retainAll|clear)\\s*\\()");
        java.util.regex.Pattern read = java.util.regex.Pattern.compile("\\.\\s*(?:edges|points)\\b");

        List<File> files = javaFilesUnder("src/org/traincontrol/automation");

        int readers = 0;

        for (File file : files)
        {
            if (file.getName().equals("Layout.java")) continue;

            String code = support.SourceOf.withoutComments(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

            java.util.regex.Matcher found = write.matcher(code);

            if (found.find())
            {
                fail(file.getName() + " writes the railway's " + found.group(1) + " through another object, near: "
                    + code.substring(Math.max(0, found.start() - 60), Math.min(code.length(), found.end() + 30))
                        .replace('\n', ' '));
            }

            if (read.matcher(code).find()) readers++;
        }

        assertTrue(readers > 0, "nothing outside Layout reads its edges or points any more, so this checked nothing");

        assertEquals(support.SourceOf.occurrences("final Map<String, Edge> edges;"), 1,
            "Layout no longer declares `edges` final");
        assertEquals(support.SourceOf.occurrences("final Map<String, Point> points;"), 1,
            "Layout no longer declares `points` final");
    }

    /**
     * A method of the autonomy editor panel named like one of its helpers' is one call to it (FR-136), so a claim that
     * reads the helper's body is reading what the panel runs.  Only the panel's rows of COLLABORATORS are read.
     *
     * MUTATION: add a statement of its own before the call in any of the panel's delegators, or make one call another
     * helper, and this fails.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testAnEditorPanelDelegatorIsOneCallToItsHelper() throws Exception
    {
        int rows = 0;

        for (String[] each : COLLABORATORS)
        {
            if (!AUTONOMY_EDITOR.equals(each[3])) continue;

            rows++;

            List<String> panel = Files.readAllLines(new File(each[3]).toPath(), StandardCharsets.UTF_8);

            java.util.Set<String> helperNames = declaredNames(
                Files.readAllLines(new File(each[1]).toPath(), StandardCharsets.UTF_8));

            int delegators = 0;

            for (int i = 0; i < panel.size(); i++)
            {
                String name = methodDeclaredAt(panel.get(i));

                if (name == null || !helperNames.contains(name)) continue;

                delegators++;

                List<String> statements = statementsOfTheBody(panel, i);

                assertTrue(statements.size() == 1 && (statements.get(0).contains(each[0] + "." + name + "(")
                        || statements.get(0).contains(each[2] + "." + name + "(")),
                    each[4] + "." + name + " is named like " + each[2] + "'s method but is not one call to it, so the"
                    + " panel runs code the claims that read " + each[2] + " do not see: " + statements);
            }

            assertTrue(delegators > 0, "no method of " + each[4] + " is named like one of " + each[2] + "'s, so this"
                + " checks nothing");
        }

        assertEquals(rows, 5, "the panel is expected to hand to five helpers");
    }

    /**
     * The autonomy editor panel, its five helpers (FR-136) and the two windows that stand in for its square menus, whose
     * writes go through `StationWindowBase.act` and so the panel (FR-111): the only classes that may reach `session`,
     * `promptName(` or `applyLength(` through a panel.
     */
    private static final String[] EDITOR_FILES = {
        "AutonomyEditorPanel", "EditorTileMenus", "EditorPrompts", "EditorSignals", "EditorMassAssign", "EditorWhy",
        "StationSettingsWindow", "TrainWindow" };

    /**
     * What a file reaches into an AutonomyEditorPanel for: each `session`, `promptName(` or `applyLength(` taken from
     * an expression of that type: a name the file declares with the type (field, parameter, local), a call of
     * `getAutonomyPanel()` or `liveSetupPanel()`, or a cast to the type.  `promptNameFor(` and `applyLengthAnswer(` are
     * other names and do not match.
     *
     * @param code a file's text without comments
     * @return one entry per hit, "member: the text around it"
     */
    private static List<String> reachesIntoThePanel(String code)
    {
        StringBuilder expressions = new StringBuilder(
            "getAutonomyPanel\\s*\\(\\s*\\)|liveSetupPanel\\s*\\(\\s*\\)|\\(\\s*AutonomyEditorPanel\\s*\\)\\s*\\w+\\s*\\)");

        for (String name : panelNames(code)) expressions.append("|(?<!\\w)").append(java.util.regex.Pattern.quote(name));

        java.util.regex.Matcher found = java.util.regex.Pattern.compile(
            "(?:" + expressions + ")\\s*\\.\\s*(session\\b|promptName\\s*\\(|applyLength\\s*\\()").matcher(code);

        List<String> hits = new ArrayList<>();

        while (found.find())
        {
            hits.add(found.group(1).replaceAll("\\W", "") + ": " + code.substring(Math.max(0, found.start() - 30),
                Math.min(code.length(), found.end() + 30)).replaceAll("\\s+", " "));
        }

        return hits;
    }

    /** The names a file declares with the type AutonomyEditorPanel: a field, a parameter or a local. */
    private static java.util.Set<String> panelNames(String code)
    {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();

        java.util.regex.Matcher declared = java.util.regex.Pattern.compile(
            "\\bAutonomyEditorPanel\\s+(\\w+)\\s*[;=,)]").matcher(code);

        while (declared.find()) names.add(declared.group(1));

        return names;
    }

    /**
     * Nothing outside the autonomy editor and its helpers reaches the panel's `promptName`, `applyLength` or `session`
     * (FR-136): each has a door that decides something first, and a caller that went round it would skip that.  The
     * control is the helpers themselves: the same reading must find them there, or it is reading nothing.
     *
     * MUTATION: in LayoutEditor, add `autonomyPanel.session.getStore();` or `autonomyPanel.promptName(over);` and this
     * fails.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testOnlyTheEditorsHelpersReachIntoThePanel() throws Exception
    {
        java.util.Set<String> inside = new java.util.HashSet<>(java.util.Arrays.asList(EDITOR_FILES));

        java.util.Set<String> seen = new java.util.HashSet<>();

        for (String name : EDITOR_FILES)
        {
            if ("AutonomyEditorPanel".equals(name)) continue;

            File helper = new File("src/org/traincontrol/gui/autonomyeditor/" + name + ".java");

            assertTrue(helper.isFile(), "cannot find " + helper.getAbsolutePath());

            String code = support.SourceOf.withoutComments(new String(Files.readAllBytes(helper.toPath()),
                StandardCharsets.UTF_8));

            assertTrue(panelNames(code).contains("editor"), name + " no longer declares its AutonomyEditorPanel as `editor`");

            for (String hit : reachesIntoThePanel(code)) seen.add(hit.substring(0, hit.indexOf(':')));
        }

        // `promptName` has no helper caller since the popup's Rename item moved into the Station Settings window; the
        // panel's own `promptNameFor` still calls it, so the outside check below still guards it.
        assertEquals(seen, new java.util.HashSet<>(java.util.Arrays.asList("session", "applyLength")),
            "the helpers are expected to reach both through `editor`, which is what shows the pattern matches");

        List<File> files = javaFilesUnder("src/org/traincontrol/gui");

        int outside = 0;
        boolean layoutEditorKnown = false;

        for (File file : files)
        {
            if (inside.contains(file.getName().replace(".java", ""))) continue;

            String code = support.SourceOf.withoutComments(new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8));

            outside++;

            if (file.getName().equals("LayoutEditor.java")) layoutEditorKnown = panelNames(code).contains("autonomyPanel");

            List<String> hits = reachesIntoThePanel(code);

            assertTrue(hits.isEmpty(), file.getName() + " reaches into AutonomyEditorPanel past its doors "
                + "(`promptNameFor`, `promptLengthFor`, and writes through the panel): " + hits);
        }

        assertTrue(outside > 20, "only " + outside + " files outside the editor were read, so this checked almost nothing");
        assertTrue(layoutEditorKnown, "LayoutEditor no longer declares `autonomyPanel`, so the names read are not the ones used");
    }

    /**
     * Whether a line calls the wrapper's own name from inside its own body, qualified or not.  Besides
     * `this.syncWithCS2()`, the mechanical rewrite also produces the UNQUALIFIED spelling (inside a lambda, or once
     * `this.` is tidied away), so a bare occurrence of the name, preceded by neither a dot nor a word character, is
     * checked too: it matches `-> name(` and `name(` but not `this.model.name(`, the call the wrapper is supposed to
     * make.
     *
     * MUTATION this catches: change the recursive call this file already guards against from `this.syncWithCS2();` to
     * the unqualified `syncWithCS2();`, which the substring check on `"this." + name + "("` no longer matches.
     */
    private static boolean callsItself(String line, String name, int arity)
    {
        String code = withoutComments(line);

        // THE SAME ARITY, or it is another overload rather than recursion: `saveState(boolean)` ends `saveState(backup,
        // !backup);`, one overload handing to the other.  A `this.` in front is the same call and gets the same arity
        // test.
        java.util.regex.Matcher call = java.util.regex.Pattern.compile(
            "(^|[^.\\w]|\\bthis\\.)" + java.util.regex.Pattern.quote(name) + "\\s*\\(([^)]*)\\)")
            .matcher(code);

        while (call.find())
        {
            if (argumentCount(call.group(2)) == arity) return true;
        }

        return false;
    }

    /**
     * How many arguments a call carries.  Commas inside a nested call are not separators, so depth is tracked; good
     * enough to tell `name(a)` from `name(a, b)`, and the alternative is a Java parser.
     *
     * @param args the text between the brackets
     * @return the number of top-level arguments
     */
    private static int argumentCount(String args)
    {
        if (args.trim().isEmpty()) return 0;

        int depth = 0;
        int count = 1;

        for (char c : args.toCharArray())
        {
            if (c == '(' || c == '<') depth++;
            else if (c == ')' || c == '>') depth--;
            else if (c == ',' && depth == 0) count++;
        }

        return count;
    }

    /**
     * A line with any trailing comment removed.
     *
     * Necessary because the fix for the bug this test exists for is itself explained in a comment that
     * quotes the wrong call - so the comment describing the mistake would otherwise be read as the
     * mistake.  Code is what is checked; prose about code is not.
     */
    private static String withoutComments(String line)
    {
        int at = line.indexOf("//");

        return at < 0 ? line : line.substring(0, at);
    }

    /**
     * The lines of a method body, from its declaration to its closing brace.
     *
     * The signature itself is never included, even when its opening brace shares its line (K&R style): otherwise
     * `public void syncWithCS2() {` would be handed to `callsItself` as a line "calling" syncWithCS2.
     */
    private static List<String> bodyOf(List<String> lines, int declaredAt)
    {
        List<String> body = new ArrayList<>();

        int depth = 0;
        boolean started = false;

        for (int i = declaredAt; i < lines.size(); i++)
        {
            String line = lines.get(i);
            String forChecking = line;

            for (int c = 0; c < line.length(); c++)
            {
                char ch = line.charAt(c);

                if (ch == '{')
                {
                    if (!started) forChecking = line.substring(c + 1);

                    depth++;
                    started = true;
                }
                else if (ch == '}')
                {
                    depth--;
                }
            }

            if (started) body.add(forChecking);

            if (started && depth <= 0) break;
        }

        return body;
    }

    /**
     * The method name from a declaration line, or null when the line is not one.
     */
    private static String nameOf(String declaration)
    {
        int open = declaration.indexOf('(');

        if (open < 0) return null;

        String before = declaration.substring(0, open).trim();

        int space = before.lastIndexOf(' ');

        if (space < 0) return null;

        String name = before.substring(space + 1);

        return name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0)) ? null : name;
    }

    /**
     * Whether any line delegates this name to the model, which is what makes it a forwarding wrapper.
     */
    private static boolean forwardsToTheModel(List<String> lines, String name)
    {
        for (String line : lines)
        {
            if (line.contains("this.model." + name + "(")) return true;
        }

        return false;
    }
}
