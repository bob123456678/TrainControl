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

    private static final String EDITOR = "src/org/traincontrol/gui/LayoutEditor.java";

    /**
     * The collaborators (FR-136), one row each: the field of the owner that holds it, the file it is declared in, its
     * class name - for the static delegators, which reach it as `Class.method(` rather than through the field - the
     * owner's file and class name, and the prefix the collaborator calls its owner by (`ui.`, `owner.`, `editor.`).
     */
    private static final String[][] COLLABORATORS = {
        { "backupAction", "src/org/traincontrol/gui/BackupAction.java", "BackupAction", WINDOW, "TrainControlUI", "ui." },
        { "updateFlow", "src/org/traincontrol/gui/UpdateFlow.java", "UpdateFlow", WINDOW, "TrainControlUI", "ui." },
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
        { "selectionOps", "src/org/traincontrol/gui/LayoutEditorSelection.java", "LayoutEditorSelection", EDITOR,
            "LayoutEditor", "editor." },
        { "reshapeOps", "src/org/traincontrol/gui/LayoutEditorReshape.java", "LayoutEditorReshape", EDITOR,
            "LayoutEditor", "editor." },
        { "undoOps", "src/org/traincontrol/gui/LayoutEditorUndo.java", "LayoutEditorUndo", EDITOR,
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
    };

    /**
     * A delegator and the collaborator it hands to do not call each other for ever (FR-136).
     *
     * The extractions leave a one-line delegator on the window, the session and the track editor wherever something
     * outside a moved cluster calls it, and the moved code calls back into its owner through `ui`, `owner` or `editor`.
     * The same mechanical edit
     * that made `syncWithCS2` call itself can make a delegator call its own name instead of the collaborator's, or make
     * the moved body call the owner's delegator that leads straight back to it.  Either is a StackOverflowError the
     * compiler accepts.
     *
     * Methods are told apart by name AND number of arguments: the editor's `deleteSelection()` hands to the collaborator's,
     * which calls `editor.deleteSelection(true)`, a different method by design.
     *
     * The static delegators (`askAboutTheUpdate`, `whenNoModalDialog`, `routeNamedIn`, `detectImportFormat`) hand over as
     * `Class.method(` and the collaborator calls the owner's statics as `TrainControlUI.method(`, so both spellings are
     * read as well.
     *
     * The self-call half reads every method of the owner that shares a name with a method of the collaborator, not only
     * those whose body still names the collaborator: a delegator rewritten to call itself names it nowhere.
     *
     * MUTATION: in BackupAction, make `writeTheBackup` call `this.ui.backupData()`, and this fails; so does making
     * UpdateFlow's static `whenNoModalDialog` call `TrainControlUI.whenNoModalDialog(`, or SessionRuns' `runs()` return
     * `owner.runs()`, or LayoutEditorUndo's `undo()` call `editor.undo()`.  And so does making TrainControlUI's `combineLinkedPages` delegator call `this.combineLinkedPages()`
     * (or a bare `combineLinkedPages()`) instead of `layoutFileActions.combineLinkedPages()`.
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

            // What each side calls on the other: the owner through `<field>.` (or `Class.` for a static), the
            // collaborator through its back-prefix (or `OwnerClass.` for a static).
            java.util.Map<String, java.util.Set<String>> windowCalls = merged(
                callsThrough(window, each[0] + "."), callsThrough(window, each[2] + "."));
            java.util.Map<String, java.util.Set<String>> helperCalls = merged(
                callsThrough(helper, each[5]), callsThrough(helper, each[4] + "."));

            int checked = 0;

            for (java.util.Map.Entry<String, java.util.Set<String>> delegator : windowCalls.entrySet())
            {
                for (String handedTo : delegator.getValue())
                {
                    checked++;

                    java.util.Set<String> back = helperCalls.get(handedTo);

                    assertFalse(back != null && back.contains(delegator.getKey()),
                        each[4] + "." + delegator.getKey() + " hands to " + each[2] + "." + handedTo
                        + ", which calls back into " + each[4] + "." + delegator.getKey() + " - a loop the compiler accepts");
                }
            }

            assertTrue(checked > 0, each[4] + " hands nothing to " + each[0] + ", so this checks nothing");

            // And a delegator that calls its own name, bare or through `this.`, beside the collaborator's.  Every method of
            // the owner named like one of the collaborator's is read, since a delegator rewritten that way names the
            // collaborator nowhere; the arity test in `callsItself` leaves another overload alone.
            java.util.Set<String> collaboratorNames = new java.util.HashSet<>();

            for (String line : helper)
            {
                String declared = methodDeclaredAt(line);

                if (declared != null) collaboratorNames.add(declared);
            }

            int delegators = 0;

            for (int i = 0; i < window.size(); i++)
            {
                String name = methodDeclaredAt(window.get(i));

                if (name == null || !collaboratorNames.contains(name)) continue;

                delegators++;

                int arity = arityOfTheDeclarationAt(window, i);

                for (String line : bodyOf(window, i))
                {
                    assertFalse(callsItself(line, name, arity), each[4] + "." + name + " calls itself instead of "
                        + each[0] + "." + name + ".  Line: " + line.trim());
                }
            }

            assertTrue(delegators > 0, "no method of " + each[4] + " is named like one of " + each[2] + "'s, so this"
                + " checks nothing");
        }
    }

    /**
     * How many parameters the method declared at a line takes, reading on while the parameters run onto the next lines.
     */
    private static int arityOfTheDeclarationAt(List<String> lines, int at)
    {
        String declaration = lines.get(at).trim();

        for (int k = at + 1; declaration.lastIndexOf(')') < declaration.indexOf('(') && k < lines.size(); k++)
        {
            declaration += " " + lines.get(k).trim();
        }

        return argumentCount(declaration.substring(declaration.indexOf('(') + 1, declaration.lastIndexOf(')')));
    }

    /**
     * Two maps of method name to the names it calls, as one.
     */
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

    /**
     * The name of the method a line declares at a class's top level (four spaces in), or null.
     */
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
     * The session's one-line delegators call their collaborator, never themselves (FR-136).
     *
     * `AutonomySession` keeps each public method whose body moved to a package-private collaborator, as a one-line
     * call to it; a mechanical rewrite that drops the collaborator's name leaves a method calling itself.
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

            // A signature wrapped over several lines, whole
            for (int next = i + 1; declaration.lastIndexOf(')') < declaration.indexOf('(') && next < lines.size(); next++)
            {
                declaration += " " + lines.get(next).trim();
            }

            // A ONE-STATEMENT BODY, the delegator's shape, whether it still calls the collaborator or has been
            // rewritten to call itself
            List<String> statements = new ArrayList<>();

            for (String line : bodyOf(lines, i))
            {
                String code = withoutComments(line).replace("{", "").replace("}", "").trim();

                if (!code.isEmpty()) statements.add(code);
            }

            if (statements.size() != 1) continue;

            String statement = statements.get(0);

            int arity = argumentCount(declaration.substring(declaration.indexOf('(') + 1, declaration.lastIndexOf(')')));

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
     * The railway's delegators and the helpers they hand to do not call each other for ever (FR-136).
     *
     * Layout keeps every public and `synchronized` method, and each private one that something outside a moved cluster
     * calls, as a one-line call to a package-private helper; the helper calls back into the railway through `owner.`,
     * or `Layout.` for a static.  Both loops are read: a delegator calling its own name instead of the helper's, and a
     * helper's method calling back into the delegator that leads to it.  Either is a StackOverflowError the compiler
     * accepts.
     *
     * The helpers' own hand-offs are read too: a helper keeps overloads that hand to one another by the bare name
     * (`explainCannotStart` 2 to 1, `walkStandingTrains` 2 to 3, `walkOneTail` 6 to 7), and one rewritten to call its own
     * arity goes through neither the railway nor the owner, so nothing above would see it.
     *
     * MUTATION: make LayoutExplanations.explainCannotStart(Locomotive) end `return owner.explainCannotStart(loc);`, or
     * make Layout.whyNoRouteFitsTo call `whyNoRouteFitsTo(loc, start, end)`, and this fails; so does making
     * LayoutExplanations' `explainCannotStart(loc, byHand)` hand to `explainCannotStart(loc, false)`, or StandingTrains'
     * six-argument `walkOneTail` call `walkOneTail(` with six arguments.
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

            // What each side calls on the other: the railway through its field (or `Class.` for a static), the helper
            // through `owner.` (or `Layout.` for a static)
            java.util.Map<String, java.util.Set<String>> railwayCalls = merged(
                callsThrough(railway, each[0] + "."), callsThrough(railway, each[2] + "."));
            java.util.Map<String, java.util.Set<String>> helperCalls = merged(
                callsThrough(helper, "owner."), callsThrough(helper, "Layout."));

            int checked = 0;

            for (java.util.Map.Entry<String, java.util.Set<String>> delegator : railwayCalls.entrySet())
            {
                for (String handedTo : delegator.getValue())
                {
                    checked++;

                    java.util.Set<String> back = helperCalls.get(handedTo);

                    assertFalse(back != null && back.contains(delegator.getKey()),
                        "Layout." + delegator.getKey() + " hands to " + each[2] + "." + handedTo
                        + ", which calls back into Layout." + delegator.getKey() + " - a loop the compiler accepts");
                }
            }

            assertTrue(checked > 0, "Layout hands nothing to " + each[0] + ", so this checks nothing");

            // And a delegator that calls its own name, bare or through `this.`, instead of the helper's.  Every method of
            // Layout named like one of the helper's is read, since a delegator rewritten that way names the helper nowhere.
            java.util.Set<String> helperNames = new java.util.HashSet<>();

            for (String line : helper)
            {
                String name = methodDeclaredAt(line);

                if (name != null) helperNames.add(name);
            }

            int delegators = 0;

            for (int i = 0; i < railway.size(); i++)
            {
                String name = methodDeclaredAt(railway.get(i));

                if (name == null || !helperNames.contains(name)) continue;

                delegators++;

                String declaration = railway.get(i).trim();

                // A declaration whose parameters run onto the next lines
                for (int k = i + 1; declaration.lastIndexOf(')') < declaration.indexOf('(') && k < railway.size(); k++)
                {
                    declaration += " " + railway.get(k).trim();
                }

                int arity = argumentCount(declaration.substring(declaration.indexOf('(') + 1,
                    declaration.lastIndexOf(')')));

                for (String line : bodyOf(railway, i))
                {
                    assertFalse(callsItself(line, name, arity),
                        "Layout." + name + " calls itself instead of " + each[0] + "." + name + ".  Line: " + line.trim());
                }
            }

            assertTrue(delegators > 0, "no method of Layout is named like one of " + each[2] + "'s, so this checks nothing");

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
     * Only the railway assigns its `edges` and `points` (FR-136).
     *
     * They are package-private so that `StandingTrains` can read them, which leaves them open to every class of the
     * `automation` package.  Layout writes them under its monitor and builds the adjacency beside them (`createPoint`,
     * `createEdge`), so a write from another class would skip both.  So no other file of the package assigns either
     * through another object or calls a mutator on it, and Layout declares both `final`.
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

        List<File> files = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walked = Files.walk(
            new File("src/org/traincontrol/automation").toPath()))
        {
            walked.filter(path -> path.toString().endsWith(".java")).forEach(path -> files.add(path.toFile()));
        }

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
     * The autonomy editor's helpers (FR-136): the field of AutonomyEditorPanel that holds each, the file it is declared
     * in, and its class name - for a static delegator, which reaches it as `Class.method(`.
     */
    private static final String[][] EDITOR_HELPERS = {
        { "tileMenus", "src/org/traincontrol/gui/EditorTileMenus.java", "EditorTileMenus" },
        { "prompts", "src/org/traincontrol/gui/EditorPrompts.java", "EditorPrompts" },
        { "signalPairing", "src/org/traincontrol/gui/EditorSignals.java", "EditorSignals" },
        { "massAssignment", "src/org/traincontrol/gui/EditorMassAssign.java", "EditorMassAssign" },
        { "whyAnswers", "src/org/traincontrol/gui/EditorWhy.java", "EditorWhy" },
    };

    /**
     * The autonomy editor's delegators and the helpers they hand to do not call each other for ever, and each delegator
     * is one call to its helper (FR-136).
     *
     * AutonomyEditorPanel keeps each method that something outside a moved cluster calls as a one-line call to a
     * package-private helper, which calls back into the panel through `editor.`, or `AutonomyEditorPanel.` for a static.
     * Both loops are read: a delegator calling its own name instead of the helper's, and a helper's method calling back
     * into the delegator that leads to it.  Either is a StackOverflowError the compiler accepts.  Every method of the
     * panel named like one of a helper's is read, since a delegator rewritten that way names the helper nowhere; and each
     * must be that one call, so a claim that reads the helper's body is reading what the panel runs.
     *
     * MUTATION: make EditorTileMenus.buildFacingMenu begin `if (target == null) return editor.buildFacingMenu(target);`,
     * or make the panel's buildFacingMenu `return buildFacingMenu(target);`, and this fails.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testAnEditorDelegatorAndItsHelperDoNotCallEachOtherForEver() throws Exception
    {
        List<String> panel = Files.readAllLines(
            new File("src/org/traincontrol/gui/AutonomyEditorPanel.java").toPath(), StandardCharsets.UTF_8);

        for (String[] each : EDITOR_HELPERS)
        {
            File source = new File(each[1]);

            assertTrue(source.isFile(), "cannot find " + source.getAbsolutePath());

            List<String> helper = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);

            // What each side calls on the other: the panel through its field (or `Class.` for a static), the helper
            // through `editor.` (or `AutonomyEditorPanel.` for a static)
            java.util.Map<String, java.util.Set<String>> panelCalls = merged(
                callsThrough(panel, each[0] + "."), callsThrough(panel, each[2] + "."));
            java.util.Map<String, java.util.Set<String>> helperCalls = merged(
                callsThrough(helper, "editor."), callsThrough(helper, "AutonomyEditorPanel."));

            int checked = 0;

            for (java.util.Map.Entry<String, java.util.Set<String>> delegator : panelCalls.entrySet())
            {
                for (String handedTo : delegator.getValue())
                {
                    checked++;

                    java.util.Set<String> back = helperCalls.get(handedTo);

                    assertFalse(back != null && back.contains(delegator.getKey()),
                        "AutonomyEditorPanel." + delegator.getKey() + " hands to " + each[2] + "." + handedTo
                        + ", which calls back into AutonomyEditorPanel." + delegator.getKey()
                        + " - a loop the compiler accepts");
                }
            }

            assertTrue(checked > 0, "AutonomyEditorPanel hands nothing to " + each[0] + ", so this checks nothing");

            java.util.Set<String> helperNames = new java.util.HashSet<>();

            for (String line : helper)
            {
                String name = methodDeclaredAt(line);

                if (name != null) helperNames.add(name);
            }

            int delegators = 0;

            for (int i = 0; i < panel.size(); i++)
            {
                String name = methodDeclaredAt(panel.get(i));

                if (name == null || !helperNames.contains(name)) continue;

                delegators++;

                String declaration = panel.get(i).trim();

                // A declaration whose parameters run onto the next lines
                for (int k = i + 1; declaration.lastIndexOf(')') < declaration.indexOf('(') && k < panel.size(); k++)
                {
                    declaration += " " + panel.get(k).trim();
                }

                int arity = argumentCount(declaration.substring(declaration.indexOf('(') + 1,
                    declaration.lastIndexOf(')')));

                List<String> statements = new ArrayList<>();

                for (String line : bodyOf(panel, i))
                {
                    assertFalse(callsItself(line.replace("this." + name + "(", name + "("), name, arity),
                        "AutonomyEditorPanel." + name + " calls itself instead of " + each[0] + "." + name
                        + ".  Line: " + line.trim());

                    String code = withoutComments(line).replace("{", "").replace("}", "").trim();

                    if (!code.isEmpty()) statements.add(code);
                }

                assertTrue(statements.size() == 1 && (statements.get(0).contains(each[0] + "." + name + "(")
                        || statements.get(0).contains(each[2] + "." + name + "(")),
                    "AutonomyEditorPanel." + name + " is named like " + each[2] + "'s method but is not one call to it,"
                    + " so the panel runs code the claims that read " + each[2] + " do not see: " + statements);
            }

            assertTrue(delegators > 0, "no method of AutonomyEditorPanel is named like one of " + each[2]
                + "'s, so this checks nothing");
        }
    }

    /**
     * Whether a line calls the wrapper's own name from inside its own body - qualified or not.
     *
     * The bug this file was written for was `this.syncWithCS2()`, and `this.` + name + `(` is what
     * caught it - but the mechanical rewrite that produced it is exactly the kind of edit that also
     * produces the UNQUALIFIED spelling, `syncWithCS2()`, with no `this.` in front at all - inside a
     * lambda, for instance, or after a second mechanical pass tidies the first one's `this.` away.  The
     * substring check alone cannot see that spelling, so the regex below is checked as well: a bare
     * occurrence of the name, immediately preceded by neither a dot nor a word character, so that it
     * matches `-> ` + name + `(` and a plain `name(` but not `this.` + name + `(` a second time and
     * not `this.model.` + name + `(`, which is the call the wrapper is supposed to make.
     *
     * MUTATION this catches: change the recursive call this file already guards against from
     * `this.syncWithCS2();` to the unqualified `syncWithCS2();`.  Before this method existed, the
     * substring check on `"this." + name + "("` no longer matched and the test went green over the
     * same infinite recursion it was written to catch.
     */
    private static boolean callsItself(String line, String name, int arity)
    {
        String code = withoutComments(line);

        // THE SAME ARITY, or it is another overload rather than recursion.
        //
        // `saveState(boolean)` ends `saveState(backup, !backup);` - one overload handing to the other,
        // the ordinary Java idiom, and the two-argument form calls nothing at all. Reading the bare
        // name alone reported that as an infinite recursion in working code, which is worse than the
        // gap the bare-name check closed: saveState is the only overloaded method in this file\u2019s
        // scope, so this would have been red for ever and somebody would have deleted it.
        //
        // A `this.` in front is the same call, and gets the same arity test: `this.name(a)` for a one-argument `name`
        // is the loop, and `this.name(a, b)` is another overload.
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
     * How many arguments a call carries.
     *
     * Commas inside a nested call are not separators, so depth is tracked. Good enough for what this
     * has to tell apart - `name(a)` from `name(a, b)` - and the alternative is a Java parser.
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
     * The signature itself is never included, even when its opening brace shares its line (K&R style,
     * against the Allman style this file mostly uses) - only what follows that brace is. Otherwise
     * `public void syncWithCS2() {` would be handed to `callsItself` as a line "calling" syncWithCS2,
     * since the bare-name check added for TST-B6 cannot otherwise tell a declaration's own name from a
     * call to it - both are just the name followed by `(`, preceded by a space.
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
