package regression;

import java.lang.reflect.Field;
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
import support.SourceOf;

/**
 * Renaming or deleting a locomotive reaches everything that was holding it.
 *
 * Adam: "we need to make sure that adding/removing/renaming locomotives is propagated across all state", and then
 * which state matters most - "autonomy state, routes, and home locomotives are the most sensitive."
 *
 * A locomotive is held two ways.  Things that hold the OBJECT survive a rename for free but have to be swept on a
 * delete; things that hold the NAME are the reverse.  Neither announces itself, and the sweep is a list (`locDeleted`'s
 * own comment: "A sweep over everything that names this locomotive is a list, and a list is a thing one can be missing
 * from.").  The source guard at the bottom is the part that lasts: it fails the build when the running layout grows a
 * new collection of locomotives that the deletion sweep says nothing about.
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
        model = support.TestStation.simulatedInDebug();

        for (int address : new int[]{47451, 47452, 47453})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        // The test locomotives are rows in the operator's own database
        for (String name : new String[]{KEPT, GOING, GOING + " renamed"})
        {
            if (model != null && model.getLocByName(name) != null) model.deleteLoc(name);
        }

        if (model != null) model.stop();
    }

    // autonomy state

    /**
     * Every collection in the running layout lets go of a deleted locomotive.
     *
     * Six are keyed by the locomotive itself, plus the timetable, which holds it inside its entries:
     *  - `takingPath` is a claim on a slot in the cap on how many trains may run; left behind it lowers the cap.
     *  - `locomotivePendingS88` is the sensor a locomotive was heading for; a route condition waits on it for ever.
     *  - the timetable holds the locomotive itself, so executing it drives something not in the database.
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

        // THE REFLECTION CAN SEE: before the delete the locomotive IS held, by the run list at least.  Without this the
        // emptiness asserted after the delete is the answer of a scan that may be looking at nothing.
        assertTrue(collectionsHolding(layout, going).contains("locomotivesToRun"),
            "precondition: the scan of the layout's collections does not see the run list holding the locomotive, so"
            + " 'nothing holds it after the delete' below would be true of any layout.  It sees: "
            + collectionsHolding(layout, going));

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

    /** And the setup, the half of autonomy state that outlives a restart. */
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

    // home locomotives

    /** A home assignment is held by NAME, so it breaks on a rename rather than a delete. */
    @Test
    public void testAHomeFollowsARenameAndGoesOnADelete() throws Exception
    {
        Layout layout = layout();

        Locomotive going = loc(GOING);

        layout.getPoint("PR B").setHomeLoc(going);

        // A rename needs no repair any more: a Point holds the locomotive itself, so the renamed object IS the one the
        // assignment points at.
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

    // routes

    /**
     * A route names locomotives in its COMMANDS and in its CONDITIONS, and only one was being repaired.
     * `Route.evaluate` answers false for a locomotive it cannot find, so a renamed one left a condition that could
     * never be true.
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

    /** And a deleted locomotive is taken out of a route. */
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
     * Conditions are combined with AND, so removing the dead term makes a route that fires on what is LEFT, a weaker
     * condition than the operator agreed to, and these routes throw switches and signals ahead of moving trains.  A
     * route that has quietly stopped firing is safe; one that quietly starts firing is not.  So the condition stays and
     * the operator is told.  The test above builds its route with null conditions, so it could not catch a missing
     * conditions sweep.
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

    /** And a route that never mentioned it reports nothing. */
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

    // the guard that outlasts all of the above

    /**
     * Every collection of locomotives in the running layout is named in the deletion sweep.
     *
     * Nothing connected "a new field holding locomotives" to "the sweep that has to let go of them".  Source-level,
     * like the store's collections guard: the fault is textual and invisible to a test that drives the model.
     */
    @Test
    public void testEveryLocomotiveHolderIsNamedInTheSweep() throws Exception
    {
        // The running layout's fields are the holders, read from its whole PACKAGE (FR-136): a cluster split out of
        // Layout with a locomotive map of its own is still the running layout, and is still swept.
        String text = SourceOf.codeUnder("src/org/traincontrol/automation/");

        // Except Return Home's planner, whose maps are a copy of the layout's, dropped with the plan
        // (`HomeStaging.snapshot`); that holds only while nothing in the package keeps a planner in a field, asked
        // here.
        assertFalse(keepsTheHomePlanner(text),
            "a field of the automation package now keeps Return Home's planner, so its locomotive maps outlive the"
            + " press and are holders the deletion sweep has to name");

        text = text.replace(SourceOf.withoutComments(SourceOf.fileDeclaring("public final class HomeStaging")), "");

        // The CODE of the sweep, not its prose, so a comment mentioning the field cannot satisfy the guard.
        String sweep = SourceOf.withoutComments(SourceOf.body("synchronized public void locDeleted(Locomotive l)"));

        List<String> holders = holdersIn(text);

        // A regex that stops matching returns an empty list and this passes having swept nothing; three is a floor well
        // under the eleven holders in Layout.java, not a pin on the count.
        assertTrue(holders.size() >= 3,
            "only " + holders.size() + " locomotive-holding fields were found in the automation package - the "
            + "patterns holdersIn() looks for have gone stale, and this is now checking almost nothing");

        List<String> missing = new ArrayList<>();

        for (String field : holders)
        {
            if (sweep.contains(field)) continue;

            // Cleared through a method rather than by name: an exemption is a statement about the code, in a place
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

    /** A field declared at class level: its modifiers, then a type (generic or array too) in group 1, then its name. */
    private static final java.util.regex.Pattern FIELD_DECLARATION = java.util.regex.Pattern.compile(
        "\n    (?:(?:private|public|protected|static|final|volatile|transient) )*"
        + "([\\w.]+(?:<[^;=(){}]*>)?(?:\\[\\])*) \\w+\\s*[;=]");

    /**
     * Whether any field of the code keeps Return Home's planner, as itself or inside a collection, map or array of
     * them.
     */
    private static boolean keepsTheHomePlanner(String code)
    {
        java.util.regex.Matcher field = FIELD_DECLARATION.matcher(code);

        while (field.find())
        {
            if (field.group(1).matches(".*\\bHomeStaging\\b.*")) return true;
        }

        return false;
    }

    /**
     * The search for a field keeping the planner sees the declarations a layout would write, and not what is not one:
     * `List<HomeStaging>` and `Map<String, HomeStaging.Plan>` once went by it.  Driven over made-up declarations.
     *
     * MUTATION: add `private List<HomeStaging> plans = new ArrayList<>();` to `Layout`, and
     * `testEveryLocomotiveHolderIsNamedInTheSweep` fails; narrow `FIELD_DECLARATION` to the plain type and this does.
     */
    @Test
    public void testTheSearchForAPlannerHeldInAFieldSeesEveryShapeOfField()
    {
        assertTrue(keepsTheHomePlanner("\n    private HomeStaging planner;"), "a plain field is not found");

        assertTrue(keepsTheHomePlanner("\n    private HomeStaging.Plan plan = null;"), "a nested type is not found");

        assertTrue(keepsTheHomePlanner("\n    private List<HomeStaging> plans = new ArrayList<>();"),
            "a list of planners is not found");

        assertTrue(keepsTheHomePlanner("\n    private final Map<String, HomeStaging.Plan> byName = new HashMap<>();"),
            "a map of plans, with a space inside the type, is not found");

        assertTrue(keepsTheHomePlanner("\n    static HomeStaging[] all;"), "an array of planners is not found");

        assertFalse(keepsTheHomePlanner("\n    private int plans;\n    private List<Edge> edges = new ArrayList<>();"),
            "fields that do not keep a planner are flagged");

        assertFalse(keepsTheHomePlanner("\n    public HomeStaging planFor(Locomotive l)\n    {\n        HomeStaging local = null;"),
            "a method that returns a planner, or a local variable, is flagged as a field");
    }

    /**
     * Nothing compares a home or a placement against a NAME.
     *
     * `String.equals` takes an `Object`, so `loc.getName().equals(point.getHomeLoc())` kept compiling when the home
     * became a `Locomotive` and answered false for ever.  Two production sites did that quietly: `HomeStaging` treated
     * every assigned home as positional, and the locomotive status panel misjudged a train at its assigned home.  The
     * check is textual because it sees what the compiler cannot.
     */
    @Test
    public void testNoHomeOrPlacementIsComparedWithAName() throws Exception
    {
        String[] lines = SourceOf.allCode().split("\n");

        // An empty scan looks like "nothing is wrong"; 100,000 lines is a floor well under the program's size, not a
        // pin.
        assertTrue(lines.length >= 100000,
            "only " + lines.length + " lines of code were found under src/ - this ran from the wrong working "
            + "directory, or the tree has shrunk enough that this scan is not checking much of "
            + "anything");

        List<String> suspect = new ArrayList<>();

        for (int at = 0; at < lines.length; at++)
        {
            if (isSuspectLine(lines[at]))
            {
                suspect.add("line " + (at + 1) + " of the program's code:  " + lines[at].trim());
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
     * A positive control for {@link #isSuspectLine(String)}: the scan above is line-scoped with no floor, so a stale
     * pattern degenerates it to zero matches, which looks like "nothing is wrong".  This proves the detector still
     * finds the shape of the two original defects.
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

        // The permitted crossing: taking the name first must NOT be flagged.
        assertFalse(isSuspectLine("if (loc.getName().equals(point.getHomeLoc().getName())) { return ASSIGNED; }"),
            "the scan flags the correct, converted form as suspect, which means a real fix would show "
            + "up as a false positive forever");
    }

    /**
     * One line, judged as {@code testNoHomeOrPlacementIsComparedWithAName} judges every line in `src`, so a known-bad
     * line can drive it as a control.
     */
    private boolean isSuspectLine(String line)
    {
        // An accessor that answers a LOCOMOTIVE, used anywhere a NAME is expected: a Locomotive handed to
        // setSelectedItem on a combo box of names (gui/HomeLocomotiveMenu.java) compiles, Swing ignores it, and the
        // dialog showed "(none)" for a station that had a home, so OK cleared the assignment.  No equals is involved.
        if (!line.contains("getHomeLoc()") && !line.contains("getCurrentLocomotive()")
            && !line.contains("getBlockLocomotive()")) return false;

        // Compared against a name, either way round
        boolean comparedWithAName = line.contains("getName().equals(")
            || line.contains("getHomeLoc().equals(")
            || line.contains("getCurrentLocomotive().equals(")
            || line.contains("getBlockLocomotive().equals(");

        // Or handed to a combo box that holds names.  NOT contains/indexOf/remove: they take an Object, so they are as
        // RIGHT on a collection of locomotives, and a line-at-a-time guard cannot tell; one that has to be argued with
        // gains an exemption list and then watches nothing.  Every combo box here that a locomotive could be offered
        // to is built from NAMES, so a Locomotive reaching setSelectedItem is wrong every time.
        boolean handedToNames = line.contains("setSelectedItem(");

        // Unless the name is taken first, which is how the boundary is meant to be crossed
        boolean converted = line.contains("getHomeLoc().getName()")
            || line.contains("getCurrentLocomotive().getName()")
            || line.contains("getBlockLocomotive().getName()");

        return (comparedWithAName || handedToNames) && !converted;
    }

    /** Fields declared as a collection OF locomotives, or keyed by one. */
    private List<String> holdersIn(String text)
    {
        List<String> out = new ArrayList<>();

        // Nested generics too: `[^>]*` for the value type made `Map<Locomotive, List<Edge>>` invisible.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "private final (?:Map<Locomotive,.*?>|Set<Locomotive>|List<Locomotive>) (\\w+)")
            .matcher(withoutComments(text));

        while (m.find()) out.add(m.group(1));

        // The timetable holds locomotives inside its entries rather than being keyed by one, so the shape above misses
        // it.
        if (withoutComments(text).contains("private final List<TimetablePath> timetable"))
        {
            out.add("timetable");
        }

        return out;
    }

    private String withoutComments(String body)
    {
        return SourceOf.withoutComments(body);
    }

    /**
     * Which of the layout's locomotive collections still mention this one, by reflection: the behavioral half of the
     * guard above, which asks whether the sweep NAMES every holder.
     */
    private List<String> collectionsHolding(Layout layout, Locomotive loc) throws Exception
    {
        List<String> holding = new ArrayList<>();

        holdingIn(layout, "", loc, holding, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>()));

        return holding;
    }

    /**
     * The fields of an object, and of the helper objects of the automation package it owns, that hold a locomotive, so
     * a locomotive map moved into a helper class the layout owns (FR-136) is still found.  `holding` receives `field`
     * or `helper.field` for each.
     */
    private void holdingIn(Object owner, String path, Locomotive loc, List<String> holding, java.util.Set<Object> seen)
        throws Exception
    {
        if (!seen.add(owner)) return;

        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass())
        {
            for (Field field : type.getDeclaredFields())
            {
                field.setAccessible(true);

                Object value = field.get(owner);

                if (value == null) continue;

                String name = path + field.getName();

                if (value instanceof java.util.Map && ((java.util.Map<?, ?>) value).containsKey(loc))
                {
                    holding.add(name);
                }
                else if (value instanceof java.util.Collection
                    && ((java.util.Collection<?>) value).contains(loc))
                {
                    holding.add(name);
                }
                else if (!(value instanceof java.util.Map) && !(value instanceof java.util.Collection)
                    && !(value instanceof Locomotive)
                    && value.getClass().getName().startsWith("org.traincontrol.automation.")
                    && !value.getClass().isEnum())
                {
                    holdingIn(value, name + ".", loc, holding, seen);
                }
            }
        }
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
     * Testing the rule leaves the CALL as the only uncovered part, and that is where the defect usually is
     * (`renamePage` had no caller for weeks, MT-135; OB-062 is the same shape): this asks the source whether the
     * null-session path actually calls `repairLocomotiveOnDisk`.
     */
    @Test
    public void testTheWindowRepairsTheSetupOnDiskWithNoSession() throws Exception
    {
        String method = withoutComments(SourceOf.declaration("private void repairAutonomyLocomotive("));

        assertTrue(method.contains("repairLocomotiveOnDisk"),
            "repairAutonomyLocomotive does not repair the setup on disk when no session is built, so "
            + "a locomotive renamed before anything has touched autonomy keeps its old name in every "
            + "placement, home and exclusion - until parseAuto refuses the whole layout over it, days "
            + "later. The store offers repairLocomotiveOnDisk for exactly this: " + method.trim());
    }

    /**
     * Deleting a locomotive rewrites the station labels and the timetable, as renaming one does.
     *
     * Both rename doors call `updateVisiblePoints()` and `repaintTimetable()`; the delete door called neither.
     * `refreshUI()` is not the same thing: the station captions are written only by `updateVisiblePoints` (OB-081), so
     * a deleted locomotive's caption stayed until something unrelated repainted it.  A source rule, because the
     * caption is drawn by a window and the door calling the method that draws it is the property worth pinning
     * (MT-149's fix went to the guard inside `repaintTimetable` and never to the doors).
     *
     * MUTATION, stated exactly: removing `updateVisiblePoints()` from the delete door fails the first assertion, and
     * removing `repaintTimetable()` fails the second.
     */
    @Test
    public void testDeletingALocomotiveRewritesTheStationLabelsToo() throws Exception
    {
        // Asked of the whole program: one delete door, wherever it is.
        assertEquals(SourceOf.occurrences("this.model.deleteLoc(") + SourceOf.occurrences("getModel().deleteLoc("), 1,
            "the delete door is not found exactly once - there is more than one now, or none.  This rule reads the"
            + " block after the first and would report the pair as covered after fixing one of them, which is this"
            + " codebase's most frequent defect and not one a guard against it should commit");

        // Bounded by the door's own method, so the rule cannot read the rest of the file and pass whatever the code
        // does.
        String door = withoutComments(SourceOf.declaration("    void deleteLoc(String value, MouseEvent evt)"));

        int at = door.indexOf("this.ui.getModel().deleteLoc(");

        assertTrue(at >= 0, "the delete door no longer calls the model's deleteLoc( itself");

        String block = door.substring(at);

        assertTrue(block.contains("updateVisiblePoints"),
            "deleting a locomotive does not rewrite the station labels, so a station it was standing "
            + "at goes on naming it - refreshUI() redraws the autonomy overlay and not the captions "
            + "(OB-081), which is why calling that instead is not the same thing (DAY-C2): " + block);

        assertTrue(block.contains("repaintTimetable"),
            "deleting a locomotive does not repaint the timetable, so it goes on naming a locomotive "
            + "the database no longer has - the same defect MT-149 was, arriving at the door the "
            + "rename fix never reached: " + block);
    }
    /**
     * Renaming a locomotive rewrites the station labels on the track diagram.
     *
     * Adam: "When autonomy is loaded: Renaming a locomotive does not immediately propagate to the labels in the track
     * diagram viewer."  The refresh block after a rename redrew the buttons, mappings, route list, selector and
     * callbacks, not the labels beside the stations, which `updateVisiblePoints` reaches.  Read from source because
     * the alternative is a Swing harness for a repaint; what is pinned is the CALL.
     */
    @Test
    public void testRenamingALocomotiveRewritesTheStationLabels() throws Exception
    {
        // EVERY renameLoc call site, not the first: there are two (the edit dialog, and accepting a name the Central
        // Station proposes), near-copies, and a rule that stops at the first match would call the pair covered after
        // fixing one.  Asked of the whole program, naming the doors that make the two calls.
        int found = SourceOf.occurrences("model.renameLoc(") + SourceOf.occurrences("getModel().renameLoc(");

        assertEquals(found, 2,
            "expected two renameLoc call sites in the window - the edit dialog and the Central "
            + "Station proposal. Found " + found + ", so either a door has been added without "
            + "this rule being told, or one has gone and the rule is watching less than it says");

        List<String> doors = new ArrayList<>();

        doors.add(withoutComments(SourceOf.declaration("    void changeLocAddress(Locomotive l, MouseEvent evt)")));
        doors.add(withoutComments(SourceOf.declaration("private void checkForRenameMenuItemActionPerformed(")));

        int counted = 0;

        for (String door : doors) counted += door.split("(?:model|getModel\\(\\))\\.renameLoc\\(", -1).length - 1;

        assertEquals(counted, 2, "the two doors named here no longer hold both of the program's renameLoc calls");

        // The refresh block that follows it, bounded by the catch that closes the method; NOT by the next javadoc,
        // which `withoutComments` has already removed, so the window would become the rest of the file and the rule
        // would pass whatever the code did.
        List<String> silent = new ArrayList<>();

        List<String> quiet = new ArrayList<>();

        for (String body : doors)
        {
            int at = Math.max(body.indexOf("model.renameLoc("), body.indexOf("getModel().renameLoc("));

            // The refresh block that follows, bounded as above.
            int end = body.indexOf("catch (Exception", at);

            assertTrue(end > at && end - at < 6000,
                "a refresh block could not be bounded, so this rule would read the rest of the file "
                + "and pass whatever the code did");

            String block = body.substring(at, end);

            if (!block.contains("updateVisiblePoints"))
            {
                silent.add(body.substring(at, Math.min(at + 160, end)).trim());
            }

            // AND THE TIMETABLE, which names locomotives too: the fix went to the guard inside repaintTimetable and
            // neither rename door was made to CALL it.
            if (!block.contains("repaintTimetable"))
            {
                quiet.add(body.substring(at, Math.min(at + 160, end)).trim());
            }
        }

        assertEquals(silent, new ArrayList<String>(),
            "a locomotive rename does not rewrite the station labels, so the track diagram goes on "
            + "showing the old name beside the train until something unrelated repaints it (OB-081): "
            + silent);

        assertEquals(quiet, new ArrayList<String>(),
            "a locomotive rename does not repaint the timetable, so it goes on naming a locomotive "
            + "that no longer exists under that name - which is MT-149, whose fix was to the guard "
            + "inside repaintTimetable and never to the doors that have to call it: " + quiet);
    }
}
