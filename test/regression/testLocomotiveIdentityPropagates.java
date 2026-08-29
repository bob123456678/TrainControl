package regression;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Renaming or deleting a locomotive reaches everything that was holding it.
 *
 * Adam: "we need to make sure that adding/removing/renaming locomotives is propagated across all
 * state", and then which state matters most - "autonomy state, routes, and home locomotives are the
 * most sensitive."
 *
 * **Why this keeps going wrong.** A locomotive is held two ways. Most things hold the OBJECT, and those
 * survive a rename for nothing - a rename changes the object, so every reference to it is already
 * right - but they have to be swept on a delete. A few things hold the NAME, and those are the reverse:
 * a delete leaves a name that resolves to nothing, and a rename leaves one that used to. Neither kind
 * announces itself, and the sweep is a list. `locDeleted`'s own comment says what that costs: "A sweep
 * over everything that names this locomotive is a list, and a list is a thing one can be missing from."
 *
 * It had been missing three, and this file is what stops a fourth being missed quietly. The source
 * guard at the bottom is the part that lasts: it fails the build when the running layout grows a new
 * collection of locomotives that the deletion sweep says nothing about.
 *
 * @author Adam
 */
public class testLocomotiveIdentityPropagates
{
    private static MarklinControlStation model;

    /** Named so nothing in the operator's own database can collide with them */
    private static final String KEPT = "Propagation test kept";
    private static final String GOING = "Propagation test going";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);

        for (int address : new int[]{47451, 47452, 47453})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }
    }

    @AfterClass
    public static void tearDownClass()
    {
        // The test locomotives are rows in the operator's own database
        for (String name : new String[]{KEPT, GOING, GOING + " renamed"})
        {
            if (model != null && model.getLocByName(name) != null) model.deleteLoc(name);
        }

        if (model != null) model.stop();
    }

    // --- autonomy state ---------------------------------------------------------------------------

    /**
     * Every collection in the running layout lets go of a deleted locomotive.
     *
     * Six of them are keyed by the locomotive itself, plus the timetable, which holds it inside its
     * entries. Three were being swept and three were not:
     *
     *  - `takingPath` is a claim on a slot in the cap on how many trains may run. Left behind it lowers
     *    that cap for the rest of the session - the railway gets quieter and nothing says why.
     *  - `locomotivePendingS88` is the sensor a locomotive was said to be heading for, and a route
     *    condition waits on it. Left behind, whatever is waiting waits for ever.
     *  - the timetable holds the locomotive itself, so executing it afterwards drives something that is
     *    not in the database, and every save writes the entry back out.
     */
    @Test
    public void testTheRunningLayoutLetsGoOfADeletedLocomotive() throws Exception
    {
        Layout layout = layout();

        Locomotive going = loc(GOING);

        layout.getPoint("PR A").setLocomotive(going);
        layout.getPoint("PR B").setHomeLoc(going);
        layout.getPoint("PR B").setExcludedLocs(
            new java.util.HashSet<>(Arrays.asList(going)));

        layout.setLocomotivesToRun(Arrays.asList(going));

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("PR A", "PR B"));

        layout.setTimetable(Arrays.asList(
            new org.traincontrol.automation.TimetablePath(going, path, 0)));

        assertFalse(layout.getTimetable().isEmpty(), "the timetable entry was not made");

        layout.locDeleted(going);

        assertNull(layout.getPoint("PR A").getCurrentLocomotive(),
            "a deleted locomotive is still standing on a point");

        assertNull(layout.getPoint("PR B").getHomeLoc(),
            "a deleted locomotive is still the home of a station");

        assertFalse(layout.getPoint("PR B").getExcludedLocs().contains(going),
            "a deleted locomotive is still excluded from a station");

        assertFalse(layout.getLocomotivesToRun().contains(going),
            "a deleted locomotive is still in the run list");

        assertTrue(layout.getTimetable().isEmpty(),
            "a deleted locomotive is still in the timetable. Executing it drives a locomotive that is "
            + "not in the database, and the entry is written back out on every save");

        assertTrue(collectionsHolding(layout, going).isEmpty(),
            "a deleted locomotive is still held by: " + collectionsHolding(layout, going));
    }

    /**
     * And the setup - which is the other half of autonomy state, and the half that outlives a restart.
     */
    @Test
    public void testTheSetupFollowsARename() throws Exception
    {
        org.traincontrol.automationui.AutonomyCompanionStore store =
            new org.traincontrol.automationui.AutonomyCompanionStore(null);

        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");

        store.getConfiguration("Only").put("points", new org.json.JSONObject()
            .put("1:4,4", new org.json.JSONObject()
                .put("loc", new org.json.JSONObject().put("name", GOING))
                .put("home", GOING)
                .put("excludedLocs", new org.json.JSONArray().put(GOING))));

        store.locomotiveRenamed(GOING, KEPT);

        org.json.JSONObject point =
            store.getConfiguration("Only").getJSONObject("points").getJSONObject("1:4,4");

        assertEquals(point.getJSONObject("loc").getString("name"), KEPT, "the placement did not follow");
        assertEquals(point.getString("home"), KEPT, "the home did not follow");
        assertEquals(point.getJSONArray("excludedLocs").getString(0), KEPT,
            "the exclusion did not follow");
    }

    // --- home locomotives -------------------------------------------------------------------------

    /**
     * A home assignment is held by NAME, so it is the one that breaks on a rename rather than a delete.
     */
    @Test
    public void testAHomeFollowsARenameAndGoesOnADelete() throws Exception
    {
        Layout layout = layout();

        Locomotive going = loc(GOING);

        layout.getPoint("PR B").setHomeLoc(going);

        // A rename is nothing this has to be told about any more.
        //
        // This assertion used to be that the repair had FOLLOWED the rename, because a Point held the
        // locomotive's name and Layout.locomotiveRenamed existed to walk every point fixing it. The
        // Point holds the locomotive now, so the object that gets renamed IS the object the assignment
        // points at - there is nothing to repair, and that method is gone.
        String was = going.getName();

        try
        {
            going.rename("Somewhere else entirely");

            assertSame(layout.getPoint("PR B").getHomeLoc(), going,
                "the assignment stopped pointing at its locomotive when the locomotive was renamed");
        }
        finally
        {
            going.rename(was);
        }

        layout.setHomeLocomotive("PR B", GOING);

        layout.locDeleted(going);

        assertNull(layout.getPoint("PR B").getHomeLoc(),
            "a deleted locomotive is still the home of a station");

        assertFalse(layout.getHomeStations().containsKey(going),
            "the home claim outlived the locomotive, so nothing placed there can ever have a home");
    }

    // --- routes ------------------------------------------------------------------------------------

    /**
     * A route names locomotives in its COMMANDS and in its CONDITIONS, and only one was being repaired.
     *
     * The condition is the sharper half. `Route.evaluate` answers false for a locomotive it cannot
     * find, so a renamed one leaves a condition that can never be true - and the route simply stops
     * firing, with nothing on screen to say why.
     */
    @Test
    public void testARouteFollowsARenameInBothItsHalves() throws Exception
    {
        MarklinRoute route = new MarklinRoute(model, "Propagation test route", 0,
            new ArrayList<>(Arrays.asList(RouteCommand.RouteCommandLocomotiveSpeed(GOING, 40))),
            0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            NodeExpression.fromList(
                Arrays.asList(RouteCommand.RouteCommandAutoLocomotive(GOING, 47451))));

        route.locomotiveRenamed(GOING, KEPT);

        assertEquals(route.getRoute().get(0).getName(), KEPT,
            "the route command still names the old locomotive");

        assertEquals(NodeExpression.toList(route.getConditions()).get(0).getName(), KEPT,
            "the route CONDITION still names the old locomotive. Route.evaluate answers false for a "
            + "locomotive it cannot find, so this route can never fire again and nothing says why");
    }

    /**
     * And a deleted locomotive is taken out of a route, which nothing did at all.
     */
    @Test
    public void testARouteDropsCommandsForADeletedLocomotive() throws Exception
    {
        MarklinRoute route = new MarklinRoute(model, "Propagation test route two", 0,
            new ArrayList<>(Arrays.asList(
                RouteCommand.RouteCommandLocomotiveSpeed(GOING, 40),
                RouteCommand.RouteCommandAccessory(1,
                    org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true))),
            0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        route.locomotiveDeleted(GOING);

        assertEquals(route.getRoute().size(), 1,
            "the command for a deleted locomotive is still in the route. It does nothing when the "
            + "route fires, silently - which looks exactly like a route that ran");

        assertTrue(route.getRoute().get(0).isAccessory(),
            "the wrong command was removed - the accessory command is not about a locomotive");
    }

    /**
     * A CONDITION naming a deleted locomotive is reported and left alone.
     *
     * The reviewer was right that nothing covered this: the test above builds its route with null
     * conditions, so a conditions sweep that did not exist could not be caught missing.
     *
     * It was right about the gap and wrong about the remedy, which is worth writing down because the
     * remedy was mine. `locomotiveDeleted`'s own javadoc used to say a condition should be removed for
     * the same reason a command is - "a condition that can never be true is a route that can never
     * run". Conditions are combined with AND, so removing the dead term does not restore the route the
     * operator wrote: it produces a route that fires on what is LEFT, which is a weaker condition than
     * they ever agreed to. These routes throw switches and signals ahead of moving trains. A route that
     * has quietly stopped firing is safe; one that quietly starts firing is not.
     *
     * So the condition stays and the operator is told.
     */
    @Test
    public void testARouteConditionNamingADeletedLocomotiveIsReportedNotRemoved() throws Exception
    {
        MarklinRoute route = new MarklinRoute(model, "Propagation test route three", 0,
            new ArrayList<>(Arrays.asList(
                RouteCommand.RouteCommandLocomotiveSpeed(GOING, 40),
                RouteCommand.RouteCommandAccessory(1,
                    org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true))),
            0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            NodeExpression.fromList(
                Arrays.asList(RouteCommand.RouteCommandAutoLocomotive(GOING, 47451))));

        boolean stillNamed = route.locomotiveDeleted(GOING);

        assertTrue(stillNamed,
            "a condition naming the deleted locomotive was not reported. It cannot be satisfied, so "
            + "this route will never fire again - and nothing on screen would say why");

        assertEquals(NodeExpression.toList(route.getConditions()).size(), 1,
            "the condition was removed. Conditions are ANDed, so dropping the dead term makes the "
            + "route fire on a weaker condition than the operator ever wrote - and these routes throw "
            + "switches ahead of moving trains");

        assertEquals(route.getRoute().size(), 1,
            "the COMMAND for the deleted locomotive should still be removed - it can do nothing, and "
            + "leaving it makes the route look complete when it is not");

        assertTrue(route.getRoute().get(0).isAccessory(),
            "the wrong command was removed");
    }

    /**
     * And a route that never mentioned it reports nothing.
     */
    @Test
    public void testARouteWithoutTheLocomotiveReportsNothing() throws Exception
    {
        MarklinRoute route = new MarklinRoute(model, "Propagation test route four", 0,
            new ArrayList<>(Arrays.asList(RouteCommand.RouteCommandAccessory(1,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true))),
            0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            NodeExpression.fromList(Arrays.asList(RouteCommand.RouteCommandFeedback(47451, true))));

        assertFalse(route.locomotiveDeleted(GOING),
            "a route that never named the locomotive was reported as affected by its deletion");

        assertEquals(route.getRoute().size(), 1, "and nothing of its own was taken away");
    }

    // --- the guard that outlasts all of the above --------------------------------------------------

    /**
     * Every collection of locomotives in the running layout is named in the deletion sweep.
     *
     * This is the part worth keeping. The three gaps above were each found by reading, and the reason
     * they were there is that nothing connected "a new field holding locomotives" to "the sweep that
     * has to let go of them". Source-level, like the store's collections guard, and for the same
     * reason: the fault is textual and invisible to a test that drives the model, because the model is
     * the half that works.
     */
    @Test
    public void testEveryLocomotiveHolderIsNamedInTheSweep() throws Exception
    {
        File source = new File("src/org/traincontrol/automation/Layout.java");

        assertTrue(source.isFile(), "cannot find " + source.getAbsolutePath()
            + " - a test that reads the source cannot pass by not finding it");

        String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

        // The CODE of the sweep, not its prose. A guard about what a method DOES must not be
        // satisfiable by a comment mentioning the field - which is TD-3, one file over.
        String sweep = withoutComments(bodyOf(text, "locDeleted"));

        assertNotNull(sweep, "locDeleted is not there any more, so nothing sweeps at all");

        List<String> holders = holdersIn(text);

        // A regex that stops matching - the nested-generics fix above already shows it has happened
        // once - returns an empty list and this test passes having swept nothing. Seven holders exist
        // in Layout.java today; three is a floor well under that, not a pin on the count.
        assertTrue(holders.size() >= 3,
            "only " + holders.size() + " locomotive-holding fields were found in Layout.java - the "
            + "patterns holdersIn() looks for have gone stale, and this is now checking almost nothing");

        List<String> missing = new ArrayList<>();

        for (String field : holders)
        {
            if (sweep.contains(field)) continue;

            // Cleared through a method rather than by name, with the reason. This is the same shape as
            // the store's collections guard: an exemption is a statement about the code, in a place
            // that fails if it stops being true.
            if ("locomotivePendingS88".equals(field) && sweep.contains("updatePendingS88")) continue;

            missing.add(field);
        }

        assertEquals(missing, new ArrayList<String>(),
            "the running layout holds locomotives in collections the deletion sweep says nothing "
            + "about: " + missing + ". Each one keeps a deleted locomotive alive somewhere - a claim "
            + "that lowers the cap on running trains, a sensor something is waiting on, a timetable "
            + "entry that drives it. Sweep it, or say here why it does not need sweeping");
    }

    /**
     * Nothing compares a home or a placement against a NAME.
     *
     * The one hazard of holding objects instead of strings, and the type system does not cover it:
     * `String.equals` takes an `Object`, so `loc.getName().equals(point.getHomeLoc())` went on
     * compiling when the home became a `Locomotive` - and simply answered false for ever. Two
     * production sites did exactly that, and neither failed loudly:
     *
     *  - `HomeStaging` decided whether a home was ASSIGNED or merely positional, so every assigned
     *    home was quietly treated as positional and the strict contract stopped applying.
     *  - the locomotive status panel decided whether a train was standing at its assigned home.
     *
     * A test caught the first. Nothing would have caught the second, which is why this exists: it is
     * textual, so it sees what the compiler cannot.
     */
    @Test
    public void testNoHomeOrPlacementIsComparedWithAName() throws Exception
    {
        File src = new File("src");

        assertTrue(src.isDirectory(), "cannot find " + src.getAbsolutePath()
            + " - a test that reads the source cannot pass by not finding it");

        List<File> sources = new ArrayList<>();

        collect(src, sources);

        // A wrong working directory, or `src` gutted, gives an empty sweep and an empty `suspect` list
        // that looks identical to "nothing is wrong". Over a hundred files exist under src/ today; 50
        // is a floor well under that, not a pin on the count.
        assertTrue(sources.size() >= 50,
            "only " + sources.size() + " files were found under src/ - this ran from the wrong working "
            + "directory, or the tree has shrunk enough that this scan is not checking much of "
            + "anything");

        List<String> suspect = new ArrayList<>();

        for (File file : sources)
        {
            String[] lines = withoutComments(new String(
                Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).split("\n");

            for (int at = 0; at < lines.length; at++)
            {
                if (isSuspectLine(lines[at]))
                {
                    suspect.add(file.getName() + ":" + (at + 1) + "  " + lines[at].trim());
                }
            }
        }

        assertEquals(suspect, new ArrayList<String>(),
            "a locomotive is being used where a NAME is expected. Every one of these compiles: "
            + "String.equals, Collection.contains and JComboBox.setSelectedItem all take an Object, "
            + "so the comparison silently never matches - and Swing quietly ignores a selection it "
            + "cannot find, which is how opening the home dialog came to clear the assignment it was "
            + "showing. Take .getName() first: " + suspect);
    }

    /**
     * A positive control for {@link #isSuspectLine(String)} (TST-C15).
     *
     * The scan above is line-scoped with no floor: a rename of the accessors it looks for, or the
     * pattern going stale in some other way, degenerates it to zero matches over every file in `src` -
     * indistinguishable from "nothing is wrong". This proves the detector can still find the shape of
     * bug it was written for, using the exact lines the two original production defects looked like,
     * so a change that silences the real scan silences this one too.
     */
    @Test
    public void testTheNameComparisonScanCanStillCatchAKnownBadLine() throws Exception
    {
        // HomeStaging's defect: a strict equals against an accessor answering a Locomotive.
        assertTrue(isSuspectLine("if (loc.getName().equals(point.getHomeLoc())) { return ASSIGNED; }"),
            "the scan no longer flags getHomeLoc() compared with a name via .equals() - the shape of "
            + "the HomeStaging defect it was written for - so it would not have caught the original "
            + "bug either");

        // The locomotive status panel's defect: a Locomotive handed to a combo box of names.
        assertTrue(isSuspectLine("combo.setSelectedItem(point.getHomeLoc());"),
            "the scan no longer flags getHomeLoc() handed to setSelectedItem() - the shape of the "
            + "HomeLocomotiveMenu defect it was written for - so it would not have caught the original "
            + "bug either");

        // The permitted crossing: taking the name first must NOT be flagged, even though the line
        // would otherwise match the same comparedWithAName shape as the first control above.
        assertFalse(isSuspectLine("if (loc.getName().equals(point.getHomeLoc().getName())) { return ASSIGNED; }"),
            "the scan flags the correct, converted form as suspect, which means a real fix would show "
            + "up as a false positive forever");
    }

    /**
     * One line, judged the way {@code testNoHomeOrPlacementIsComparedWithAName} judges every line in
     * `src`. Extracted so the same logic can be driven by a known-bad line as a control (TST-C15).
     */
    private boolean isSuspectLine(String line)
    {
        // An accessor that answers a LOCOMOTIVE, used anywhere a NAME is expected.
        //
        // The first version of this required getName().equals( on the line, which is one of
        // several shapes the mistake takes - and not the shape of the defect it was written
        // for.  That one was a Locomotive handed to setSelectedItem on a combo box of names
        // (gui/HomeLocomotiveMenu.java): it compiles, Swing quietly ignores a selection it
        // cannot find, and the dialog showed "(none)" for a station that had a home - so
        // pressing OK cleared the assignment it was displaying.  No equals anywhere in it.
        //
        // Found by review, which is the second time this guard has been the thing at fault
        // rather than the code it watches.
        if (!line.contains("getHomeLoc()") && !line.contains("getCurrentLocomotive()")
            && !line.contains("getBlockLocomotive()")) return false;

        // Compared against a name, either way round
        boolean comparedWithAName = line.contains("getName().equals(")
            || line.contains("getHomeLoc().equals(")
            || line.contains("getCurrentLocomotive().equals(")
            || line.contains("getBlockLocomotive().equals(");

        // Or handed to a combo box that holds names.
        //
        // NOT contains/indexOf/remove, which were tried and cry wolf: they take an Object, so
        // they are equally the RIGHT call on a collection of locomotives, and a guard reading
        // source text one line at a time cannot tell which it is looking at.  The first run
        // flagged Layout.locomotivesToRun.remove(getCurrentLocomotive()) - a Set<Locomotive>,
        // where removing by object is exactly correct.  A guard that has to be argued with is
        // one somebody eventually adds an exemption list to, and then it watches nothing.
        //
        // setSelectedItem is different: every combo box in this application that a locomotive
        // could be offered to is built from NAMES, so a Locomotive reaching one is wrong every
        // time - and it is the shape of the live defect (gui/HomeLocomotiveMenu.java).
        boolean handedToNames = line.contains("setSelectedItem(");

        // Unless the name is taken first, which is how the boundary is meant to be crossed
        boolean converted = line.contains("getHomeLoc().getName()")
            || line.contains("getCurrentLocomotive().getName()")
            || line.contains("getBlockLocomotive().getName()");

        return (comparedWithAName || handedToNames) && !converted;
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

    /**
     * Fields declared as a collection OF locomotives, or keyed by one.
     */
    private List<String> holdersIn(String text)
    {
        List<String> out = new ArrayList<>();

        // Nested generics too.  This used [^>]* for the value type, so `Map<Locomotive,
        // List<Edge>>` and `Map<Locomotive, List<Point>>` were invisible to it - both happen to be
        // swept today, and a future one would have slipped past the guard meant to be the part that
        // lasts.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "private final (?:Map<Locomotive,.*?>|Set<Locomotive>|List<Locomotive>) (\\w+)")
            .matcher(withoutComments(text));

        while (m.find()) out.add(m.group(1));

        // The timetable holds locomotives inside its entries rather than being keyed by one, so it does
        // not match the shape above - and it was one of the three that had been missed.
        if (withoutComments(text).contains("private final List<TimetablePath> timetable"))
        {
            out.add("timetable");
        }

        return out;
    }

    private String bodyOf(String source, String name)
    {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "^[ \t]*(?:synchronized )?public void " + name + "\\s*\\(", java.util.regex.Pattern.MULTILINE)
            .matcher(source);

        if (!m.find()) return null;

        int open = source.indexOf('{', m.end());

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }

        return null;
    }

    /**
     * Copied rather than shared with the other tests that do this: a test helper reaching into another
     * test class is a dependency between things that are supposed to fail independently.
     */
    private String withoutComments(String body)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/') inLine = true;
            else if (c == '/' && next == '*') inBlock = true;
            else out.append(c);
        }

        return out.toString();
    }

    // ------------------------------------------------------------------------------------------

    /**
     * Which of the layout's locomotive collections still mention this one, by reflection.
     *
     * The behavioural half of the guard above: that one asks whether the sweep NAMES every holder, and
     * this asks whether anything is actually left in one afterwards.
     */
    private List<String> collectionsHolding(Layout layout, Locomotive loc) throws Exception
    {
        List<String> holding = new ArrayList<>();

        for (Field field : Layout.class.getDeclaredFields())
        {
            field.setAccessible(true);

            Object value = field.get(layout);

            if (value instanceof java.util.Map && ((java.util.Map<?, ?>) value).containsKey(loc))
            {
                holding.add(field.getName());
            }
            else if (value instanceof java.util.Collection
                && ((java.util.Collection<?>) value).contains(loc))
            {
                holding.add(field.getName());
            }
        }

        return holding;
    }

    private Locomotive loc(String name) throws Exception
    {
        Locomotive existing = model.getLocByName(name);

        if (existing != null) return existing;

        model.newMM2Locomotive(name, 78);

        return model.getLocByName(name);
    }

    private Layout layout() throws Exception
    {
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"PR A\", \"station\": true, \"s88\": 47451},"
            + "  {\"name\": \"PR B\", \"station\": true, \"s88\": 47452}"
            + "],"
            + "\"edges\": [{\"start\": \"PR A\", \"end\": \"PR B\", \"length\": 1}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        Layout layout = Layout.fromJSON(json, model);

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        return layout;
    }

    /**
     * The window repairs the setup on disk when it has no session to repair in memory.
     *
     * Testing the rule leaves the CALL as the only uncovered part, and in this codebase that is where
     * the defect usually is - renamePage was faultless and had no caller for weeks (MT-135), and
     * HomeLocomotiveMenu lost four of its five callers with its tests still green (DD-A6). OB-062 is
     * the same shape again: repairLocomotiveOnDisk can be perfect and change nothing.
     *
     * So this asks the source whether the null-session path actually calls it, rather than returning
     * the way it used to.
     */
    @Test
    public void testTheWindowRepairsTheSetupOnDiskWithNoSession() throws Exception
    {
        File source = new File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(source.isFile(),
            "cannot find " + source.getAbsolutePath() + " - a test that reads the source cannot pass "
            + "by not finding it");

        String body = withoutComments(new String(
            Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));

        int at = body.indexOf("private void repairAutonomyLocomotive");

        assertTrue(at > 0, "repairAutonomyLocomotive is gone - this rule has nothing to watch");

        // to the end of that method: the next member declaration at class level
        int end = body.indexOf("\n    private ", at + 10);
        int alt = body.indexOf("\n    public ", at + 10);

        if (alt > 0 && (end < 0 || alt < end)) end = alt;

        String method = end > 0 ? body.substring(at, end) : body.substring(at);

        assertTrue(method.contains("repairLocomotiveOnDisk"),
            "repairAutonomyLocomotive does not repair the setup on disk when no session is built, so "
            + "a locomotive renamed before anything has touched autonomy keeps its old name in every "
            + "placement, home and exclusion - until parseAuto refuses the whole layout over it, days "
            + "later. The store offers repairLocomotiveOnDisk for exactly this: " + method.trim());
    }

    /**
     * Renaming a locomotive rewrites the station labels on the track diagram.
     *
     * OB-081, Adam: "When autonomy is loaded: Renaming a locomotive does not immediately propagate to
     * the labels in the track diagram viewer."
     *
     * The refresh block after a rename redraws the locomotive buttons, the mappings, the route list,
     * the selector and the layout's own callbacks - and not the labels beside the stations, which are
     * written by updateStationLabels and reached through updateVisiblePoints.
     *
     * Every other door that changes which locomotive stands where already calls it, each carrying the
     * same note: "The label still says the locomotive's name until something rewrites it." Renaming
     * changes exactly that and was the one door that did not.
     *
     * Read from source because the alternative is a Swing harness for a repaint. What is pinned is the
     * CALL, which is what was missing - the method it calls is exercised everywhere else.
     */
    @Test
    public void testRenamingALocomotiveRewritesTheStationLabels() throws Exception
    {
        File source = new File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(source.isFile(), "cannot find " + source.getAbsolutePath());

        String body = withoutComments(new String(
            Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));

        // EVERY renameLoc call site, not the first one.
        //
        // There are two: the edit dialog, and accepting a name the Central Station proposes. They are
        // near-copies of one another, so when the first was missing this call the second was too -
        // and a rule that stops at the first match would have reported the pair as covered after
        // fixing one of them. That is this codebase's most frequent defect, and a guard against it
        // should not be able to commit it.
        java.util.List<Integer> sites = new ArrayList<>();

        for (int at = body.indexOf("renameLoc("); at >= 0; at = body.indexOf("renameLoc(", at + 1))
        {
            // the declaration in the model, not a call from the window
            if (body.lastIndexOf("model.renameLoc(", at + 16) != at - 6
                && body.lastIndexOf("model.renameLoc(", at + 16) != at - 11) continue;

            sites.add(at);
        }

        assertEquals(sites.size(), 2,
            "expected two renameLoc call sites in the window - the edit dialog and the Central "
            + "Station proposal. Found " + sites.size() + ", so either a door has been added without "
            + "this rule being told, or one has gone and the rule is watching less than it says");

        // The refresh block that follows it, bounded by the catch that closes the method.
        //
        // NOT bounded by the next javadoc, which is what the first version of this did:
        // withoutComments has already removed every javadoc, so that search never matched, the window
        // became the whole rest of the file, and the rule passed no matter what the code did.
        //
        // A guard that cannot fail is worse than no guard - it reports the thing it watches as
        // covered. This one was written, run, seen green, and only found by removing the call it is
        // supposed to protect and watching it stay green.
        List<String> silent = new ArrayList<>();

        for (int at : sites)
        {
            // The refresh block that follows, bounded by the next call site or by the catch that
            // closes the method - whichever comes first.
            //
            // NOT bounded by the next javadoc, which is what the first version of this did:
            // withoutComments has already removed every javadoc, so that search never matched, the
            // window became the whole rest of the file, and the rule passed no matter what. A guard
            // that cannot fail is worse than no guard - it reports the thing it watches as covered.
            // Found by removing the call it protects and watching it stay green.
            int end = body.indexOf("catch (Exception", at);

            int nextSite = sites.indexOf(at) + 1 < sites.size() ? sites.get(sites.indexOf(at) + 1) : -1;

            if (nextSite > at && (end < 0 || nextSite < end)) end = nextSite;

            assertTrue(end > at && end - at < 6000,
                "a refresh block could not be bounded, so this rule would read the rest of the file "
                + "and pass whatever the code did");

            if (!body.substring(at, end).contains("updateVisiblePoints"))
            {
                silent.add(body.substring(at, Math.min(at + 160, end)).trim());
            }
        }

        assertEquals(silent, new ArrayList<String>(),
            "a locomotive rename does not rewrite the station labels, so the track diagram goes on "
            + "showing the old name beside the train until something unrelated repaints it (OB-081): "
            + silent);
    }
}
