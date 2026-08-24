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
        layout.getPoint("PR B").setHomeLoc(GOING);
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

        layout.getPoint("PR B").setHomeLoc(GOING);

        layout.locomotiveRenamed(GOING, "Somewhere else entirely");

        assertEquals(layout.getPoint("PR B").getHomeLoc(), "Somewhere else entirely",
            "the home assignment still names the old locomotive. It is held by NAME, so nothing about "
            + "renaming the object repairs it - and on the next load it is a locomotive that is not in "
            + "the database, which invalidates the whole configuration");

        layout.getPoint("PR B").setHomeLoc(GOING);
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

        List<String> missing = new ArrayList<>();

        for (String field : holdersIn(text))
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
     * Fields declared as a collection OF locomotives, or keyed by one.
     */
    private List<String> holdersIn(String text)
    {
        List<String> out = new ArrayList<>();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "private final (?:Map<Locomotive,[^>]*>|Set<Locomotive>|List<Locomotive>) (\\w+)")
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
}
