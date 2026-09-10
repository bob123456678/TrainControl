package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Every door from a user-interface class onto the railway's monitor is written down here.
 *
 * **THE RULE, ONCE, INSTEAD OF ONCE PER DOOR.**  `Layout` has two dozen `synchronized` methods, and
 * they all take the same monitor: the one a driving thread holds for the whole of
 * `configureAndLockPath` - a `CONFIGURE_SLEEP` per edge and again per accessory, "several seconds on a
 * six-edge path" (`Layout.java`) - and the one `AutoLocomotiveStatus.findPaths` holds for a whole-graph
 * search on every panel refresh with nothing running at all.  A user interface class runs on the event
 * thread unless it has gone to some trouble not to.  So the event thread queues on that monitor, and
 * what the operator sees is a window that does not repaint and controls that do not answer while the
 * trains go on running.  That is OB-192, which Adam has now reported twice:
 *
 * *"starting autonomous operation from the current track state ... makes the UI unresponsive.  Trains
 * still run, but nothing is repainted, and controls are stuck."*
 *
 * **IT HAS BEEN FIXED AT SEVEN DOORS IN TWO ROUNDS, EACH TIME BY FINDING ONE MORE CALLER**, because
 * the rule lived as prose at the call sites: `Layout.getEdges`'s comment, `refreshCoveredTrack`'s
 * javadoc, `behaviour.md` §5c.  A comment written on 2026-08-24 forbidding exactly this was read, four
 * days later, as being about something else, and a refresh was added underneath it with a comment
 * proving it safe - "`getPoints()` is deliberately unsynchronized, so this takes no Layout monitor" -
 * which was false when it was written, because the method's other half reached
 * `edgesCoveredByStandingTrains`.  Eight hours later it was Adam's every-run freeze.
 *
 * **AND THE TRIPWIRE THAT EXISTED PINNED ONE INSTANCE.**
 * `testTheDiagramRefreshDoesNotWaitOnTheRailway` grew a source guard for this, and it grepped `gui/`
 * for one method name - `triageReturnToHome` - while the sentence in its own failure message was
 * general.  It reported clean about every door it had never heard of, and there were four.  This class
 * is that guard with the *list of `synchronized` `Layout` methods* in place of the one name, and the
 * narrow one has been deleted rather than kept beside it: two guards for one rule is how the two come
 * to disagree.
 *
 * **WHAT IT CANNOT SEE, SAID OUT LOUD.**  Source cannot tell which thread a line runs on.  This is
 * therefore an INVENTORY, not a proof: every call from a user-interface class to one of these methods
 * must appear in the list below, saying which thread it is on and - where it is on the event thread -
 * why that is accepted.  Its value is that a NEW door cannot be added without somebody reading this
 * and answering the question, which is one more reading than the seven doors this defect was made of
 * ever got.  `testTheDiagramRefreshDoesNotWaitOnTheRailway` still MEASURES the two doors that matter
 * most, with a monitor actually held; that is the proof, and this is the census.
 *
 * **THE WAY PAST IS A LINE**, and it has to be, because there are legitimate calls: a question already
 * asked on a worker, a change of state made from a thread the method itself started, a menu item the
 * operator has just clicked.  A check with no way past is one people delete.  What the line may not be
 * is silent: every entry says `OFF THE EVENT THREAD` with the thread named, or `ON THE EVENT THREAD`
 * with the reason, and `testEveryAllowanceSaysWhichThreadItIsOn` holds that shape.
 *
 * MUTATION: put `layout.getPossiblePaths(locomotive, true)` back in
 * `LayoutRightclickAutonomyMenu`'s constructor, or `isChoosableByAutonomy` back in
 * `TrainControlUI.captionIsActive`, and `testEveryDoorOntoTheRailwaysMonitorIsWrittenDown` fails,
 * quoting the line and naming the member it is in.
 *
 * @author Adam
 */
public class testNothingOnTheEventThreadTakesTheRailwaysMonitor
{
    /** The railway, whose monitor this is about. */
    private static final File LAYOUT = new File("src/org/traincontrol/automation/Layout.java");

    /**
     * The packages that draw the application.
     *
     * `gui` is where the event thread lives.  `automationui` is included because the window asks the
     * session constantly and from the event thread, so a new question added there is a new door by one
     * remove - which is exactly the shape the covered marks arrived by.
     */
    private static final String[] SURFACES =
    {
        "src/org/traincontrol/gui",
        "src/org/traincontrol/automationui"
    };

    /**
     * Methods that are not themselves `synchronized` but reach one, so a call to them is a door too.
     *
     * `Layout.triageReturnToHome` is the one the narrow tripwire was written for: it builds a
     * `HomeStaging.snapshot`, which calls `Layout.getHomeStations` - `synchronized`.  Naming it here is
     * what lets that tripwire be deleted rather than kept.
     *
     * **SIX MORE SINCE 2026-09-10 (E8-C4), and they are the ones the UI touches.**  A review traced
     * this class's blind spots: it reads `synchronized` METHOD DECLARATIONS, so it cannot see a member
     * that takes the same monitor with a `synchronized (this)` BLOCK, and it cannot see one that
     * reaches the monitor through a call.  Twelve `Layout` members do the second, five of them already
     * called from this application - all five correctly, on workers - which means `executePath` on the
     * event thread would have passed this guard in silence while holding the monitor for a whole
     * dispatch.  That is precisely OB-192's shape.
     *
     *   - `executePath`, `executeTimetable`, `runLocomotives` - reach it through `isPathClear` and
     *     `configureAndLockPath`, and each holds it for the length of a dispatch or a run.
     *   - `configureAndLockPath` - `synchronized (this)` block, `Layout.java:3180`.
     *   - `getPathValidationFailureCount`, `hasShownPathValidationAlert` - `synchronized (this)`
     *     blocks, and a public `int` and `boolean`: exactly the shape a status panel or a tooltip
     *     reads from `actionPerformed`.
     *
     * This list cannot be derived from the source the way the `synchronized` ones can - it would need a
     * call graph - so it grows by hand when somebody finds another.  That is a weakness, and it is why
     * the list above it is the one that does the work.
     */
    private static final String[] REACHES_THE_MONITOR =
    {
        "triageReturnToHome",

        // Reached through isPathClear and configureAndLockPath, and held for a whole dispatch.
        "executePath", "executeTimetable", "runLocomotives",

        // `synchronized (this)` blocks, which the declaration scanner above cannot see.
        "configureAndLockPath", "getPathValidationFailureCount", "hasShownPathValidationAlert"
    };

    /**
     * Every door, and what makes it acceptable.
     *
     * Keyed `File.java#member`, so all the calls one member makes share one line.  The value must begin
     * `OFF THE EVENT THREAD: ` or `ON THE EVENT THREAD: `.
     */
    private static final Map<String, String> ALLOWED = new LinkedHashMap<>();

    static
    {
        // ------------------------------------------------------------ off the event thread

        // ------------------------------------------------------- the five dispatch doors (E8-C4)
        //
        // These reach the monitor through `isPathClear` and `configureAndLockPath` rather than by
        // being `synchronized` themselves, so the scanner above could not see them and this guard was
        // silent about them until 2026-09-10.  All five were already on workers - what they were not
        // was written down, which is what makes a sixth one indistinguishable from them.
        //
        // `executePath` holds the monitor for a whole dispatch, across a CONFIGURE_SLEEP per accessory
        // of the path.  On the event thread that is OB-192 exactly: a frozen interface with trains
        // still running.
        ALLOWED.put("AutoLocomotiveStatus.java#locAvailPathsMouseClicked",
            "OFF THE EVENT THREAD: the click handler dispatches the run on a `new Thread`, and the"
            + " completion is put back on the event thread with invokeLater.");

        ALLOWED.put("LayoutRightclickAutonomyMenu.java#destinationItem",
            "OFF THE EVENT THREAD: the menu item's action starts a `new Thread` for the dispatch."
            + "  The menu is built on the event thread; nothing it builds runs the railway there.");

        ALLOWED.put("TrainControlUI.java#executeTimetableActionPerformed",
            "OFF THE EVENT THREAD: the button disables itself on the event thread and then runs the"
            + " timetable on a `new Thread`, re-enabling through invokeLater.");

        ALLOWED.put("TrainControlUI.java#startAutonomyActionPerformed",
            "OFF THE EVENT THREAD: `runLocomotives` is dispatched on a `new Thread`, which is why the"
            + " Return Home button is disabled directly rather than through refreshReturnHomeButton.");

        ALLOWED.put("AutoLocomotiveStatus.java#findPaths",
            "OFF THE EVENT THREAD: AutonomyRenderer, submitted by repaintAutoLocList; its own javadoc"
            + " says why the search and the legend reads both happen there");

        ALLOWED.put("AutoLocomotiveStatus.java#whyNotReport",
            "OFF THE EVENT THREAD: the `new Thread` showWhyNot starts before it opens the window");

        ALLOWED.put("AutoLocomotiveStatus.java#whyNotToolTip",
            "OFF THE EVENT THREAD: the `new Thread` explainOnHover starts (OB-079 - hovering this"
            + " label during a dispatch used to freeze the whole window)");

        ALLOWED.put("LayoutRightclickAutonomyMenu.java#gatherPathOptions",
            "OFF THE EVENT THREAD: the worker showFor starts before it builds the menu, which is the"
            + " whole reason that method exists");

        ALLOWED.put("TrainControlUI.java#workOutCaptionVisibility",
            "OFF THE EVENT THREAD: CoveredTrackRenderer, and the answer is painted by an invokeLater");

        ALLOWED.put("TrainControlUI.java#workOutReturnHomeTriage",
            "OFF THE EVENT THREAD: ReturnHomeTriageRenderer, and the answer is painted by an"
            + " invokeLater");

        ALLOWED.put("TrainControlUI.java#requestReturnToHome",
            "OFF THE EVENT THREAD: asked at the end of a staging run from inside the `new Thread` this"
            + " method has already started - the same thread that has been blocking on executeTimetable");

        ALLOWED.put("TrainControlUI.java#repaintTimetable",
            "OFF THE EVENT THREAD: this method bounces itself onto a thread when it finds itself on the"
            + " event thread, and takes the monitor only after that");

        ALLOWED.put("TrainControlUI.java#exportJSONActionPerformed",
            "OFF THE EVENT THREAD: the `new Thread` this action starts; only the export is on it, and"
            + " the window it feeds is built inside an invokeLater (OB-137)");

        ALLOWED.put("AutonomyEditorPanel.java#composeWhy",
            "OFF THE EVENT THREAD: WhyRenderer, submitted by applyWhy - which captures the Layout and"
            + " the station index on the event thread first, because both of those BUILD, and paints"
            + " the answer in an invokeLater.  This was the last ON THE EVENT THREAD allowance for a"
            + " graph walk (D3-C1, Adam 2026-09-09: \"I would rather take it off EDT\")");

        ALLOWED.put("AutonomySession.java#routesCoveredByStandingTrains",
            "OFF THE EVENT THREAD: reached only from TrainControlUI.workOutCoveredTrack, on"
            + " CoveredTrackRenderer");

        ALLOWED.put("AutonomySession.java#tilesBlockedByStandingTrains",
            "OFF THE EVENT THREAD: reached only from TrainControlUI.workOutCoveredTrack, on"
            + " CoveredTrackRenderer");

        // ------------------------------------------------------------ on the event thread, and why

        ALLOWED.put("AutoLocomotiveStatus.java#updateState",
            "ON THE EVENT THREAD: the fallback search, reached only by a caller that did not gather"
            + " first.  Every caller that ships passes haveFound, because repaintAutoLocList runs"
            + " findPaths on AutonomyRenderer - so this is the branch for a caller that has no worker,"
            + " and there is none today");

        ALLOWED.put("AutoLocomotiveStatus.java#timetableStart",
            "ON THE EVENT THREAD: the same fallback, for the same reason - findPaths gathers this on"
            + " the worker and stamps it, and this reads the Layout only when nothing gathered");

        ALLOWED.put("AutonomyViewerPanel.java#load",
            "ON THE EVENT THREAD: loading a configuration captures the outgoing one first, and"
            + " prepareAutonomyReload has already stopped everything that was moving, so nothing is"
            + " holding the monitor by the time this runs");

        ALLOWED.put("GraphLocAssign.java#commitChanges",
            "ON THE EVENT THREAD (D3-C4): placing a train is a change of state the operator has just"
            + " confirmed in a modal dialog, and the repaint that follows must not run before it");

        ALLOWED.put("LayoutRightclickAutonomyMenu.java#placeFacing",
            "ON THE EVENT THREAD (D3-C4): the same placement, from the diagram's menu");

        ALLOWED.put("LayoutRightclickAutonomyMenu.java#removeLocomotiveHere",
            "ON THE EVENT THREAD (D3-C4): taking a train off a square, offered only when"
            + " isAutonomyBusy() is false");

        ALLOWED.put("TrainControlUI.java#locomotiveGestureOnDiagram",
            "ON THE EVENT THREAD (D3-C4): a paste or a clear on the diagram, one gesture at a time");

        ALLOWED.put("TrainControlUI.java#putTheTrainsBack",
            "ON THE EVENT THREAD: a setup rebuild puts every placement back into the layout it has just"
            + " built, and that layout is not the one anything else holds yet");

        ALLOWED.put("TrainControlUI.java#deleteLoc",
            "ON THE EVENT THREAD: a locomotive leaving the database, from a confirmed dialog");

        ALLOWED.put("TrainControlUI.java#saveState",
            "ON THE EVENT THREAD: writing the session out, at shutdown and on an explicit save");

        ALLOWED.put("TrainControlUI.java#captureRunningLayout",
            "ON THE EVENT THREAD: capturing the running layout into the setup, on an explicit gesture");

        ALLOWED.put("TrainControlUI.java#validateButtonActionPerformed",
            "ON THE EVENT THREAD: comparing the edited JSON with the running layout's, on the button"
            + " press that asks for it");
    }

    /**
     * Every call from a user-interface class to a method that takes the railway's monitor is listed.
     *
     * @throws Exception on a failure to read the tree
     */
    @Test
    public void testEveryDoorOntoTheRailwaysMonitorIsWrittenDown() throws Exception
    {
        Set<String> guarded = doorsOntoTheMonitor();

        List<String> unlisted = new ArrayList<>();

        for (File source : surfaceSources())
        {
            String code = withoutComments(read(source));

            for (String door : guarded)
            {
                Matcher calls = Pattern.compile("\\.\\s*" + door + "\\s*\\(").matcher(code);

                while (calls.find())
                {
                    String key = source.getName() + "#" + memberAround(code, calls.start());

                    if (ALLOWED.containsKey(key)) continue;

                    unlisted.add(key + "\n        " + lineAround(code, calls.start()));
                }
            }
        }

        assertTrue(unlisted.isEmpty(),
            "a user interface class calls a method that takes the railway's monitor, and this test does"
            + " not know which thread it is on:\n\n    " + String.join("\n    ", unlisted)
            + "\n\nEvery `synchronized` method of `Layout` takes the monitor a dispatch holds across a"
            + " CONFIGURE_SLEEP per accessory of a path, and that `AutoLocomotiveStatus.findPaths`"
            + " holds for a search of the whole graph with nothing running at all.  On the event"
            + " thread, waiting for it is OB-192: no repaints, no controls, and the trains still"
            + " running.\n\nMove the question to a worker and paint the answer when it lands -"
            + " `refreshCoveredTrack` and `refreshCaptionVisibility` are the shape - or, if this call"
            + " really is safe, add its `File.java#member` to ALLOWED in this test saying which thread"
            + " it is on and why");
    }

    /**
     * The guard knows what it is guarding.
     *
     * A source scanner that silently matches nothing reports clean about everything, which is how a
     * headline number in this repository turned out to be false by thirty squares of fifty-eight.  So
     * the parsed list is checked against a handful of names read out of `Layout.java` by eye - if a
     * change to how those declarations are written stops the pattern matching them, this says so
     * instead of the census going quietly empty.
     *
     * @throws Exception on a failure to read the railway
     */
    @Test
    public void testTheGuardCanStillFindTheSynchronizedMethods() throws Exception
    {
        Set<String> guarded = doorsOntoTheMonitor();

        assertTrue(guarded.size() >= 20,
            "only " + guarded.size() + " methods that take the railway's monitor were found in "
            + LAYOUT + ", where there were twenty-four when this was written.  The declarations are"
            + " written some way this no longer reads, and a guard that finds nothing passes"
            + " everything: " + guarded);

        for (String named : new String[]{ "getPossiblePaths", "isChoosableByAutonomy",
            "isOfferableToOperator", "edgesCoveredByStandingTrains", "getHomeStations",
            "moveLocomotive", "explainDestinations" })
        {
            assertTrue(guarded.contains(named),
                "`Layout." + named + "` is no longer read as taking the railway's monitor.  Either it"
                + " has stopped being `synchronized` - in which case take it out of this list - or the"
                + " pattern above has stopped matching how it is declared, and every call to it is now"
                + " invisible to this test");
        }

        assertTrue(guarded.contains("triageReturnToHome"),
            "`triageReturnToHome` is not being guarded.  It is not itself `synchronized` - it reaches"
            + " `getHomeStations`, which is - so it comes from REACHES_THE_MONITOR rather than from the"
            + " source, and it is the one door the deleted narrow tripwire existed for");
    }

    /**
     * Nothing is allowed without saying which thread it is on.
     *
     * The way past this guard is a line, and a line that says only "fine" is the silent exemption the
     * whole class is written against.  Two prefixes, so that reading the list answers the only question
     * that matters about a door: is it on the thread that draws the window.
     */
    @Test
    public void testEveryAllowanceSaysWhichThreadItIsOn()
    {
        for (Map.Entry<String, String> each : ALLOWED.entrySet())
        {
            assertTrue(each.getValue().startsWith("OFF THE EVENT THREAD")
                || each.getValue().startsWith("ON THE EVENT THREAD"),
                each.getKey() + " is allowed to take the railway's monitor without saying which thread"
                + " it does it on.  Begin the reason `OFF THE EVENT THREAD: ` and name the thread, or"
                + " `ON THE EVENT THREAD: ` and say why that is accepted here");
        }
    }

    /**
     * Every allowance is still a door.
     *
     * An allowlist rots in the direction that matters: a member that stops calling the railway leaves
     * its line behind, and the line then licenses whatever is written into that member next.  The
     * covered-track fix moved four calls in one commit; without this, four permissions would have
     * stayed.
     *
     * @throws Exception on a failure to read the tree
     */
    @Test
    public void testEveryAllowanceIsStillADoor() throws Exception
    {
        Set<String> guarded = doorsOntoTheMonitor();

        Set<String> found = new LinkedHashSet<>();

        for (File source : surfaceSources())
        {
            String code = withoutComments(read(source));

            for (String door : guarded)
            {
                Matcher calls = Pattern.compile("\\.\\s*" + door + "\\s*\\(").matcher(code);

                while (calls.find())
                {
                    found.add(source.getName() + "#" + memberAround(code, calls.start()));
                }
            }
        }

        List<String> stale = new ArrayList<>();

        for (String key : ALLOWED.keySet())
        {
            if (!found.contains(key)) stale.add(key);
        }

        assertTrue(stale.isEmpty(),
            "these are allowed to take the railway's monitor and no longer do: " + stale
            + ".  Take them out.  A permission left behind for a member that has stopped calling the"
            + " railway is a permission for whatever is written into that member next, which is the"
            + " door this whole class exists to close");
    }

    /**
     * The narrow tripwire this generalises is gone.
     *
     * Two guards for one rule is how the two come to disagree - the narrow one pinned
     * `triageReturnToHome` while its own failure message stated the rule generally, and it reported
     * clean about four doors.  Stated as a property so that restoring it, rather than adding to this,
     * is caught.
     *
     * @throws Exception on a failure to read the test
     */
    @Test
    public void testTheOneMethodTripwireHasNotComeBack() throws Exception
    {
        File was = new File("test/regression/testTheDiagramRefreshDoesNotWaitOnTheRailway.java");

        assertTrue(was.isFile(), was + " is gone, and it holds the two MEASURED doors - the ones with a"
            + " monitor actually held.  This class is the census; that one is the proof, and neither"
            + " replaces the other");

        String code = read(was);

        assertTrue(!code.contains("testNothingOnTheEventThreadAsksWhetherAnythingIsAwayFromHome"),
            "the one-method tripwire is back beside this class.  It greps for `triageReturnToHome`"
            + " alone, which is one of the names in REACHES_THE_MONITOR here - so it now pins an"
            + " instance of a rule this file pins whole, and two guards for one rule drift apart");
    }

    // ---------------------------------------------------------------- reading the source

    /**
     * Every method whose call takes the railway's monitor.
     *
     * The `synchronized` methods of `Layout`, read out of its source, plus the handful that reach one.
     *
     * @return their names
     * @throws Exception on a failure to read the railway
     */
    private static Set<String> doorsOntoTheMonitor() throws Exception
    {
        Set<String> out = new LinkedHashSet<>();

        assertTrue(LAYOUT.isFile(),
            "run this from the project root - " + LAYOUT.getAbsolutePath() + " is not there");

        String code = withoutComments(read(LAYOUT));

        // A DECLARATION, never a `synchronized (x)` block: the modifier may come before or after the
        // access one - both spellings are in this file - and what follows it is a return type and a
        // name, which a block's parenthesis cannot be.
        Matcher declared = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|private|protected|static|final|abstract)[ \\t]+)*"
            + "synchronized[ \\t]+(?:(?:public|private|protected|static|final)[ \\t]+)*"
            + "[\\w<>\\[\\],\\.\\? \\t]+?[ \\t]+(\\w+)[ \\t]*\\(").matcher(code);

        while (declared.find()) out.add(declared.group(1));

        for (String reaches : REACHES_THE_MONITOR) out.add(reaches);

        return out;
    }

    /**
     * Every source file of the packages that draw the application.
     *
     * @return the files
     */
    private static List<File> surfaceSources()
    {
        List<File> out = new ArrayList<>();

        for (String where : SURFACES)
        {
            File folder = new File(where);

            assertTrue(folder.isDirectory(),
                "run this from the project root - " + folder.getAbsolutePath() + " is not there");

            for (File child : folder.listFiles())
            {
                if (child.getName().endsWith(".java")) out.add(child);
            }
        }

        assertTrue(out.size() > 40,
            "only " + out.size() + " user interface sources were found, which is far fewer than this"
            + " application has - the folders above have moved and this is scanning almost nothing");

        return out;
    }

    /**
     * The class member a position in the source is inside.
     *
     * Members of these classes are declared at exactly four spaces of indent and their bodies are
     * deeper, so the nearest such line above a call is the member it is in - and an anonymous class or
     * a lambda inside a member is stepped over rather than named, which is what the allowlist wants: a
     * listener built in a constructor belongs to that constructor.
     *
     * A field initialiser is not a member declaration for this purpose - it has an `=` before its
     * parenthesis - so a call inside one is attributed to the member above it.  That is wrong, and it
     * is harmless: the failure message quotes the file and the line, so the reader finds it anyway.
     *
     * @param code the source, comments already out
     * @param at where the call is
     * @return the member's name, or `?` where there is none above it
     */
    private static String memberAround(String code, int at)
    {
        int from = code.lastIndexOf('\n', at) + 1;

        while (true)
        {
            int to = code.indexOf('\n', from);

            String line = code.substring(from, to < 0 ? code.length() : to);

            String named = declaredName(line);

            if (named != null) return named;

            if (from == 0) return "?";

            from = code.lastIndexOf('\n', from - 2) + 1;
        }
    }

    /**
     * The name a class-member declaration declares, or null where this line is not one.
     *
     * @param line one line of source, comments already out
     * @return the member's name, or null
     */
    private static String declaredName(String line)
    {
        if (line.length() < 6) return null;

        if (!line.startsWith("    ") || line.charAt(4) == ' ' || line.charAt(4) == '}'
            || line.charAt(4) == '{' || line.charAt(4) == '@') return null;

        int open = line.indexOf('(');

        if (open < 0) return null;

        String head = line.substring(0, open);

        // A field whose value is built by a call is not a member declaration for this purpose.
        if (head.indexOf('=') >= 0) return null;

        Matcher last = Pattern.compile("(\\w+)\\s*$").matcher(head);

        if (!last.find()) return null;

        String name = last.group(1);

        // Never a control word: none of these can begin a member at class indent, and reading one as a
        // member name would put a nonsense key in the allowlist.
        for (String keyword : new String[]{ "if", "for", "while", "switch", "catch", "return", "new",
            "synchronized", "else", "do" })
        {
            if (keyword.equals(name)) return null;
        }

        return name;
    }

    /**
     * The line a call is on, for a message somebody can act on.
     *
     * @param code the source
     * @param at where the call is
     * @return that line, trimmed
     */
    private static String lineAround(String code, int at)
    {
        int from = code.lastIndexOf('\n', at) + 1;

        int to = code.indexOf('\n', at);

        return code.substring(from, to < 0 ? code.length() : to).trim();
    }

    /**
     * A file, as text.
     *
     * @param source the file
     * @return its content
     * @throws Exception on a failure to read it
     */
    private static String read(File source) throws Exception
    {
        return new String(java.nio.file.Files.readAllBytes(source.toPath()), "UTF-8");
    }

    /**
     * The same source with its comments taken out.
     *
     * Necessary rather than tidy: this rule is written out at length in the javadoc of every method
     * that used to break it, and half of those javadocs name the very methods scanned for here.
     * Counting raw occurrences would count the explanations.
     *
     * @param code the source
     * @return the code alone
     */
    private static String withoutComments(String code)
    {
        StringBuilder out = new StringBuilder(code.length());

        boolean block = false;
        boolean line = false;
        boolean quoted = false;

        for (int at = 0; at < code.length(); at++)
        {
            char here = code.charAt(at);
            char next = at + 1 < code.length() ? code.charAt(at + 1) : '\0';

            if (block)
            {
                if (here == '\n') out.append(here);

                if (here == '*' && next == '/') { block = false; at++; }

                continue;
            }

            if (line)
            {
                if (here == '\n') { line = false; out.append(here); }

                continue;
            }

            if (quoted)
            {
                if (here == '\\') { at++; continue; }

                if (here == '"' || here == '\n') quoted = false;

                if (here == '\n') out.append(here);

                continue;
            }

            if (here == '/' && next == '*') { block = true; at++; continue; }

            if (here == '/' && next == '/') { line = true; at++; continue; }

            if (here == '"') { quoted = true; continue; }

            out.append(here);
        }

        return out.toString();
    }
}
