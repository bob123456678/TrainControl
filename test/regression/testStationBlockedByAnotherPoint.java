package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.LinkedList;
import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * A station can be held back while another point is in use (FR-001, Adam: "similar to excluding locomotives, we should
 * be able to exclude the autonomous selection of a station when another (specified) point is occupied.").
 *
 * The setting is remembered against SQUARES in the setup, and the build turns it into LOCK EDGES: every edge arriving
 * at the held-back station gains every edge that ends at the watched square.  `Edge.isLockHeld` asks whether the track
 * is HELD BY A ROUTE, not whether a train stands beyond it, so the station is unavailable while something runs over the
 * watched approach.  This tests the BUILD; the refusal itself is the existing lock behavior.
 *
 * @author Adam
 */
public class testStationBlockedByAnotherPoint
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-blocked").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /** The edge into the held-back station gains a lock edge that ends at the watched square. */
    @Test
    public void testTheStationsApproachIsLockedAgainstTheWatchedPoint() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        org.json.JSONArray edges = built.getJSONArray("edges");

        boolean lockedAgainstTheYard = false;
        int intoThePlatform = 0;

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            if (!"Bahnsteig".equals(edge.optString("end", null))) continue;

            intoThePlatform++;

            org.json.JSONArray locks = edge.optJSONArray("lockedges");

            if (locks == null) continue;

            for (int at = 0; at < locks.length(); at++)
            {
                if ("Abstellgleis".equals(locks.getJSONObject(at).optString("end", null)))
                {
                    lockedAgainstTheYard = true;
                }
            }
        }

        assertTrue(intoThePlatform > 0,
            "no edge arrives at the platform, so nothing below tests anything");

        // The same setting reaches the POINT as a name, the standing-train half
        org.json.JSONArray points = built.getJSONArray("points");

        boolean namedOnThePoint = false;

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            if (!"Bahnsteig".equals(point.optString("name", null))) continue;

            org.json.JSONArray watching = point.optJSONArray("blockedBy");

            if (watching == null) continue;

            for (int at = 0; at < watching.length(); at++)
            {
                if ("Abstellgleis".equals(watching.getString(at))) namedOnThePoint = true;
            }
        }

        assertTrue(namedOnThePoint,
            "the station carries no blockedBy naming the yard, so nothing holds it back while a train "
            + "is merely STANDING there - which is the half lock edges cannot express (FR-001)");

        assertTrue(lockedAgainstTheYard,
            "the approach to a station held back by the yard carries no lock edge ending at the yard, "
            + "so nothing stops autonomy choosing that station while the yard is in use (FR-001)");
    }

    /** With nothing set, nothing is added: the restriction costs nothing until it is asked for. */
    @Test
    public void testNoRestrictionAddsNoLocks() throws IOException
    {
        open();

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            if (!"Bahnsteig".equals(edge.optString("end", null))) continue;

            org.json.JSONArray locks = edge.optJSONArray("lockedges");

            if (locks == null) continue;

            for (int at = 0; at < locks.length(); at++)
            {
                assertNotEquals(locks.getJSONObject(at).optString("end", null), "Abstellgleis",
                    "a lock against the yard was emitted although nothing asked for one");
            }
        }
    }

    /** A station never watches itself, which would make it a station nothing can be sent to. */
    @Test
    public void testAStationDoesNotWatchItself() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(platform));

        assertTrue(session.getStore().getBlockingPoints(platform).isEmpty(),
            "a station was recorded as held back by itself. Standing there already decides whether it "
            + "is free, so this makes it a station nothing can ever be sent to");
    }

    /** The setting survives being written out and read back. */
    @Test
    public void testTheRestrictionSurvivesTheFile() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));
        session.save();

        AutonomySession reopened = new AutonomySession(layout);

        reopened.open(Arrays.asList(page()));

        assertEquals(reopened.getStore().getBlockingPoints(platform), Arrays.asList(yard),
            "the restriction did not survive the file, so it is lost on the next start");
    }

    /**
     * Deleting the watched square takes the restriction with it, rather than leaving one the next tile drawn there
     * would inherit.
     */
    @Test
    public void testDeletingTheWatchedSquareDropsTheRestriction() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));

        session.getStore().reconcile(new java.util.LinkedHashSet<>(Arrays.asList(platform)));

        assertTrue(session.getStore().getBlockingPoints(platform).isEmpty(),
            "a restriction watching a square that no longer exists survived. It can never be "
            + "satisfied, and whatever is drawn at those coordinates next inherits it");
    }

    /**
     * Setting the station to pass through keeps its restriction, and the build still applies it (Adam: "do allow
     * restrictions on non-stations, and let's not clear them when the type changes"): a route into the square still
     * waits on the watched one.
     *
     * MUTATION: clear the restriction in `AutonomySession.setStation` again, and this fails.
     */
    @Test
    public void testDemotingTheStationKeepsTheRestriction() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));

        assertEquals(session.getStore().getBlockingPoints(platform), Arrays.asList(yard),
            "precondition: the restriction did not take");

        session.setStation(platform, false);

        assertEquals(session.getStore().getBlockingPoints(platform), Arrays.asList(yard), "the square was made pass-through"
            + " and lost 'unavailable while the yard is occupied' - Adam, 2026-09-24: \"let's not clear them when the"
            + " type changes\"");

        org.json.JSONArray edges =
            new org.json.JSONObject(session.buildConfigurationForInspection()).getJSONArray("edges");

        boolean locked = false;

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONArray locks = edges.getJSONObject(i).optJSONArray("lockedges");

            if (!"Bahnsteig".equals(edges.getJSONObject(i).optString("end", null)) || locks == null) continue;

            for (int at = 0; at < locks.length(); at++)
            {
                if ("Abstellgleis".equals(locks.getJSONObject(at).optString("end", null))) locked = true;
            }
        }

        assertTrue(locked, "the restriction is kept on the pass-through square and no route into it waits on the yard -"
            + " kept and not applied");
    }

    /**
     * Every restriction is listed as a notice, on a station and on a square that is not one (Adam: "a simple info
     * notice on restrictions (in the list for any type of station or non station) would be better.").
     *
     * MUTATION: leave the notice out of `AutonomyChecks.run`, or list it for stations only, and this fails.
     */
    @Test
    public void testEveryRestrictionIsListedAsANotice() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        assertEquals(noticesAbout(platform), 0, "precondition: a notice about a restriction nobody has set");

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));

        assertEquals(noticesAbout(platform), 1, "a station unavailable while the yard is occupied is not listed as a"
            + " notice - Adam, 2026-09-24: \"a simple info notice on restrictions\"");

        session.setStation(platform, false);

        assertEquals(noticesAbout(platform), 1, "a square that is not a station, unavailable while the yard is occupied,"
            + " is not listed - \"in the list for any type of station or non station\"");
    }

    /** How many notices the setup's findings give about a restriction on this square. */
    private int noticesAbout(TileKey square)
    {
        int seen = 0;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if ("autosetup.ui.checkUnavailableWhileOccupied".equals(finding.getMessageKey())
                && square.equals(finding.getTile())
                && finding.getSeverity() == org.traincontrol.automationui.AutonomyChecks.Severity.NOTICE)
            {
                seen++;
            }
        }

        return seen;
    }

    /**
     * A restriction watching a square that is not on the graph is left out of the build, not written as null: the
     * standing-train half found no Point by name and wrote `"blockedBy": [null]`, which the railway's reader throws on
     * and invalidates the WHOLE configuration.
     */
    @Test
    public void testARestrictionWatchingASquareOffTheGraphIsLeftOut() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey offTheGraph = new TileKey("main", 8, 5);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(offTheGraph));

        assertEquals(session.getStore().getBlockingPoints(platform), Arrays.asList(offTheGraph),
            "precondition: the store did not keep a restriction watching a square off the graph");
        assertFalse(session.getReducer().getPoints().containsKey(offTheGraph),
            "precondition: the watched square is a Point of the reduction");

        org.json.JSONArray points =
            new org.json.JSONObject(session.buildConfigurationForInspection()).getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONArray watching = points.getJSONObject(i).optJSONArray("blockedBy");

            if (watching == null) continue;

            for (int at = 0; at < watching.length(); at++)
            {
                assertFalse(watching.isNull(at),
                    points.getJSONObject(i).optString("name") + " is written as held back by nothing: \"blockedBy\" holds"
                    + " a null, which the railway's reader throws on - and that invalidates the whole configuration"
                    + " (AMG-B1)");
            }
        }
    }

    private void open() throws IOException
    {
        session.open(Arrays.asList(page()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        session.setStation(new TileKey("main", 1, 1), true);
        session.setPointName(new TileKey("main", 1, 1), "Einfahrt");

        session.setStation(new TileKey("main", 3, 1), true);
        session.setPointName(new TileKey("main", 3, 1), "Bahnsteig");

        session.setStation(new TileKey("main", 3, 3), true);
        session.setPointName(new TileKey("main", 3, 3), "Abstellgleis");
    }

    /** A run into a platform, and a yard reached from the same throat. */
    private LayoutDiagram page() throws IOException
    {
        // Sensors side by side, as the other fixtures here build a run: a straight needs an orientation, adjacent
        // feedbacks connect on their own.
        LayoutDiagram diagram = new LayoutDiagram("main", 10, 6, null, null);

        diagram.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 2, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        // A separate run for the yard; the restriction needs an edge ENDING at the watched square, which is what gets
        // locked against.
        diagram.addComponent(componentType.FEEDBACK, 1, 3, 0, 0, 8, 14, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 2, 3, 0, 0, 9, 15, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 3, 3, 0, 0, 10, 16, accessoryDecoderType.MM2, null);

        diagram.setPageId("1");

        return diagram;
    }

    /**
     * A train STANDING on the watched point takes the station out of autonomy's choices (FR-001's other half).  The
     * build emits the setting as LOCK EDGES (a route running over the watched approach) and as the names below (a train
     * standing there); `Edge.isLockHeld` cannot do the second.  It is safe to ask the harder question here: a lock edge
     * is track a route must CROSS, and this is asked only of a path's DESTINATION and only about squares somebody
     * named.
     */
    @Test
    public void testATrainStandingOnTheWatchedPointHoldsTheStationBack() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertTrue(layout.isPathClear(path, driven, false),
            "the path is refused before anything is standing anywhere, so nothing below tests anything");

        layout.getPoint("BK YARD").setLocomotive(other);

        assertFalse(layout.isPathClear(path, driven, false),
            "a train is standing on the point this station is held back by, and autonomy would still "
            + "send another one to the station (FR-001)");

        layout.getPoint("BK YARD").setLocomotive(null);

        assertTrue(layout.isPathClear(path, driven, false),
            "the station did not come back when the watched point cleared, so the restriction is a "
            + "one-way door rather than a condition");
    }

    /**
     * A hand-driven route is refused too (Adam: "let's enforce the occupancy ruling in all modes and then rely on
     * isPathClear.").  The exemption for the train LEAVING the watched square keeps that from being a trap:
     * `testATrainLeavingTheWatchedPointMayStillBeSentThere` is it.
     *
     * MUTATION: putting either fence back in `Layout.isPathClear` fails this and leaves every other
     * claim in this class green.
     */
    @Test
    public void testAHandDrivenRouteIsRefusedThere() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        // Not auto running is what a hand dispatch looks like to this rule.
        layout.stopLocomotives();

        assertTrue(layout.isPathClear(path, driven, false),
            "the path is refused with nothing standing on the watched square, so the refusal below"
            + " would not be the restriction and this test would prove nothing");

        layout.getPoint("BK YARD").setLocomotive(other);

        assertFalse(layout.isPathClear(path, driven, false),
            "a route chosen BY HAND was allowed into a station held back by an occupied square."
            + " Adam's ruling of 2026-09-10 is that the restriction is enforced in every mode - one"
            + " answer, relied on through isPathClear (FR-001)");
    }

    /**
     * A name matching no point is DROPPED as the file is read, rather than kept and asked about: a Point holds the
     * points themselves, so such a name never becomes a restriction and is reported once.  The user-visible behavior is
     * unchanged.
     */
    @Test
    public void testAnUnresolvableRestrictionIsDroppedRatherThanCarried() throws Exception
    {
        Layout layout = built("Somewhere that was deleted");

        assertTrue(layout.getPoint("BK B").getBlockedBy().isEmpty(),
            "a restriction naming a point that does not exist was kept. It can never be satisfied or "
            + "cleared, and it is written back out on every save");

        Locomotive driven = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertTrue(layout.isPathClear(path, driven, false),
            "and the station is still usable - renaming a point should not quietly retire a platform");
    }

    /** The whole BLOCK is asked, not only the copy carrying the name. */
    @Test
    public void testACopyOfTheWatchedSquareCountsAsOccupied() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);
        layout.getPoint("BK YARD TWIN").setLocomotive(other);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertFalse(layout.isPathClear(path, driven, false),
            "a train on the OTHER copy of the watched square did not count. The two are one piece of "
            + "track, so asking only the copy that carries the name answers clear with a train "
            + "standing there");
    }

    /** And it survives being written out and read back. */
    @Test
    public void testTheRestrictionSurvivesTheConfiguration() throws Exception
    {
        Layout layout = built();

        Layout back = Layout.fromJSON(layout.toJSON(), model);

        assertNotNull(back, "the configuration did not parse: " + Layout.getLastError());

        assertEquals(back.getPoint("BK B").getBlockedBy(), Arrays.asList(back.getPoint("BK YARD")),
            "the restriction did not survive the configuration, so it is lost on the next start");

        // The POINT of the reloaded layout, not merely something with the same name: parseAuto resolves the file's
        // name.
        assertSame(back.getPoint("BK B").getBlockedBy().get(0), back.getPoint("BK YARD"),
            "the restriction came back as something other than the point it names");
    }

    /** A point never watches itself, which would make the station impossible rather than restricted. */
    @Test
    public void testAPointDoesNotWatchItself() throws Exception
    {
        Layout layout = built("BK B");

        assertTrue(layout.getPoint("BK B").getBlockedBy().isEmpty(),
            "a station was recorded as held back by itself. Standing there already decides whether it "
            + "is free, so this makes it a station nothing can ever be sent to");
    }

    /**
     * The train leaving the watched point may still be sent to the station that point holds back (Adam: "The condition
     * should not apply to trains leaving - only departing.").  Without the exemption the one movement that clears the
     * condition is the movement it forbids.  It is the choice `Edge.isOccupied` makes, for the reason `Edge.isLockHeld`
     * records.
     */
    @Test
    public void testATrainLeavingTheWatchedPointMayStillBeSentThere() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        Locomotive leaving = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("BK YARD").setLocomotive(leaving);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK YARD", "BK B"));

        assertTrue(layout.isPathClear(path, leaving, false),
            "the train standing on the watched point was refused the station that point holds back - "
            + "so the only movement that can clear the condition is the one it forbids, and the "
            + "station stays shut to everybody until somebody drives this train off by hand");
    }

    /**
     * And the rule still holds for everybody else, which the exemption could have taken with it: `standing == null ||
     * standing.equals(loc)` reduces to `true` if the second clause is ever right about the wrong locomotive.
     */
    @Test
    public void testTheExemptionIsOnlyForTheTrainThatIsLeaving() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive standing = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);
        layout.getPoint("BK YARD").setLocomotive(standing);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertFalse(layout.isPathClear(path, driven, false),
            "somebody ELSE is standing on the watched point and the station was still offered - the "
            + "exemption has swallowed the rule it was carved out of");
    }

    /**
     * The exemption asks the BLOCK, not the square: the yard is two Points sharing one s88, and a train on the twin
     * stands on the same track.  `testACopyOfTheWatchedSquareCountsAsOccupied` pins it for the train held back, this
     * for the one let out.
     */
    @Test
    public void testLeavingTheOtherHalfOfTheWatchedBlockIsAlsoExempt() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        Locomotive leaving = model.getLocByName(model.getLocList().get(0));

        // The TWIN, not the point named in the restriction
        layout.getPoint("BK YARD TWIN").setLocomotive(leaving);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK YARD", "BK B"));

        assertTrue(layout.isPathClear(path, leaving, false),
            "the train is standing on the other Point of the watched block - the same track - and was "
            + "treated as a different train, so it cannot leave the square it is on");
    }

    /**
     * The same run as built(), with a way OUT of the yard: built() has none, so the exemption has nothing to be asked
     * about.
     */
    private Layout builtWithAnApproachFromTheYard() throws Exception
    {
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"BK A\", \"station\": true, \"s88\": 47441},"
            + "  {\"name\": \"BK B\", \"station\": true, \"s88\": 47442,"
            + "   \"blockedBy\": [\"BK YARD\"]},"
            + "  {\"name\": \"BK YARD\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"},"
            + "  {\"name\": \"BK YARD TWIN\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"}"
            + "],"
            + "\"edges\": ["
            + "  {\"start\": \"BK A\", \"end\": \"BK B\", \"length\": " + APPROACH + "},"
            + "  {\"start\": \"BK YARD\", \"end\": \"BK B\", \"length\": " + APPROACH + "}"
            + "],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        theApproachHoldsTheTrainsUsedHere();

        Layout layout = Layout.fromJSON(json, model);

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        layout.runLocomotives();

        return layout;
    }

    private Layout built() throws Exception
    {
        return built("BK YARD");
    }

    /**
     * The reason names the watched square, rather than saying the station is busy (FR-017).  `firstClearOrWhyNot` reads
     * `Layout.lastError`, a static, and while autonomy runs it substitutes a generic "blocked by a train or a route in
     * progress", which covered this clause every time.  `blockingOccupantOf` is now one method called by the runtime
     * check and by this explanation, so the two cannot drift.
     */
    @Test
    public void testTheReasonNamesTheWatchedSquareRatherThanSayingBusy() throws Exception
    {
        Layout layout = built("BK YARD");

        assertTrue(layout.isAutoRunning(),
            "the fixture must be auto-running: the substitution this is about only happens then, and "
            + "so does the FR-001 rule itself");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        // setLocomotive, as every other test here does: moveLocomotive refuses while autonomy is running, the state
        // this rule needs.
        layout.getPoint("BK A").setLocomotive(loc);
        layout.getPoint("BK YARD").setLocomotive(other);

        assertEquals(layout.getPoint("BK YARD").getCurrentLocomotive(), other,
            "the fixture did not take: with nothing on the watched square there is no reason to give");

        String reason = layout.explainDestinations(loc).get("BK B");

        assertNotNull(reason, "BK B is held back while BK YARD is occupied, so it is not available");

        assertTrue(reason.contains("BK YARD"),
            "the reason does not name the square holding the station back. It reads as though the "
            + "station were temporarily busy, so the operator is told to wait for a condition that "
            + "waiting does not clear - which is the opposite of what the window was built for "
            + "(DR-B3).  Got: " + reason);

        assertFalse(reason.equals(org.traincontrol.util.I18n.t("autolayout.why.blockedWhileRunning")),
            "the reason is still the generic substitution.  Got: " + reason);
    }

    /**
     * A run from A to B, with a yard that B is held back by, two Points sharing a block (a square reachable from two
     * sides).  runLocomotives sets the auto-running flag and starts nothing: this layout has no run list.
     */
    private Layout built(String blocker) throws Exception
    {
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"BK A\", \"station\": true, \"s88\": 47441},"
            + "  {\"name\": \"BK B\", \"station\": true, \"s88\": 47442,"
            + "   \"blockedBy\": [\"" + blocker + "\"]},"
            + "  {\"name\": \"BK YARD\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"},"
            + "  {\"name\": \"BK YARD TWIN\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"}"
            + "],"
            + "\"edges\": [{\"start\": \"BK A\", \"end\": \"BK B\", \"length\": " + APPROACH + "}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        theApproachHoldsTheTrainsUsedHere();

        Layout layout = Layout.fromJSON(json, model);

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        layout.runLocomotives();

        return layout;
    }

    /**
     * A train STANDING on the watched point closes a square trains only pass, as well as a station (OB-295; Adam: "It
     * means trains shouldn't be sent to THIS square while trains are STANDING ON or hold a lock on the other specified
     * station(s).").  The standing half was asked only of a path's destination, which a square trains only pass never
     * is.
     *
     * MUTATION: ask the standing half of the destination only, and this fails.
     */
    @Test
    public void testATrainStandingOnTheWatchedPointClosesASquareTrainsPass() throws Exception
    {
        Layout layout = passingThrough();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK P"));
        path.add(layout.getEdge("BK P", "BK B"));

        assertTrue(layout.isPathClear(path, driven, false),
            "CONTROL: the route through BK P is refused with nothing standing anywhere, so nothing below tests anything");

        layout.getPoint("BK YARD TWIN").setLocomotive(other);

        assertFalse(layout.isPathClear(path, driven, false), "a train is standing on the square BK P is held back by, and"
            + " a route through BK P still goes (OB-295)");

        String reason = layout.explainDestinations(driven).get("BK B");

        assertTrue(reason != null && reason.contains("BK P") && reason.contains("BK YARD"), "the reason BK B cannot be"
            + " reached does not name the square passed and the one holding it back: " + reason);

        layout.getPoint("BK YARD TWIN").setLocomotive(null);

        assertTrue(layout.isPathClear(path, driven, false), "the square did not open again when the watched point cleared");
    }

    /** A run from A through P to B, where P (no station) is held back by the yard. */
    private Layout passingThrough() throws Exception
    {
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"BK A\", \"station\": true, \"s88\": 47441},"
            + "  {\"name\": \"BK P\", \"station\": false, \"s88\": 47444, \"blockedBy\": [\"BK YARD\"]},"
            + "  {\"name\": \"BK B\", \"station\": true, \"s88\": 47442},"
            + "  {\"name\": \"BK YARD\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"},"
            + "  {\"name\": \"BK YARD TWIN\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"}"
            + "],"
            + "\"edges\": [{\"start\": \"BK A\", \"end\": \"BK P\", \"length\": " + APPROACH + "},"
            + "  {\"start\": \"BK P\", \"end\": \"BK B\", \"length\": " + APPROACH + "}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        theApproachHoldsTheTrainsUsedHere();

        Layout layout = Layout.fromJSON(json, model);

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        layout.runLocomotives();

        return layout;
    }

    /**
     * How long the run into BK B is measured, LONGER THAN THE TRAINS on purpose: since MT-262 `measuredRoomAtTheEndOf`
     * reads it, and with a short approach five tests refused the path before the FR-001 rule was reached and the
     * refusal tests passed without it.  The room rule is tested in `core.testATrainTooLongIsRefusedTheBerth` and
     * `core.testAManualSendIsRefusedABerthTooShort`.
     */
    private static final int APPROACH = 20;

    /**
     * That the approach really is longer than the trains these tests use, which come from the operator's own database.
     */
    private static void theApproachHoldsTheTrainsUsedHere()
    {
        for (int which = 0; which < 2; which++)
        {
            Locomotive loc = model.getLocByName(model.getLocList().get(which));

            Integer length = loc.getTrainLength();

            assertTrue(length == null || length <= APPROACH,
                loc.getName() + " is " + length + " units long and this fixture measures its approach"
                + " at " + APPROACH + ", so the berth-room rule refuses every path below before the"
                + " rule this file is about is ever asked.  Raise APPROACH");
        }
    }

    private static MarklinControlStation model;

    @org.testng.annotations.BeforeClass
    public static void setUpModel() throws Exception
    {
        model = support.TestStation.simulatedInDebug();

        for (int address : new int[]{47441, 47442, 47443, 47444})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }
    }

    @org.testng.annotations.AfterClass
    public static void tearDownModel()
    {
        if (model != null) model.stop();
    }

    private void delete(File f)
    {
        if (f.isDirectory())
        {
            File[] kids = f.listFiles();

            if (kids != null) for (File kid : kids) delete(kid);
        }

        f.delete();
    }

    /**
     * Deleting a point takes it out of every list that was watching it (OB-080): `blockedBy` holds Points, so a deleted
     * station stayed in the lists of the stations it held back, a ghost blocker that fails closed and disappears
     * across a save and load.
     */
    @Test
    public void testDeletingAPointClearsItFromEveryBlockedByList() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        // STOPPED FIRST, because this test EDITS the railway and the fixture starts it: `Layout.deletePoint` refuses
        // while `isRunning()` (OB-057).
        layout.stopLocomotives();

        org.traincontrol.automation.Point watched = layout.getPoint("BK YARD");
        org.traincontrol.automation.Point station = layout.getPoint("BK B");

        assertTrue(station.getBlockedBy().contains(watched),
            "the fixture does not hold the station back, so nothing below tests anything");

        // BK YARD TWIN shares the block but has no edges, so it can be deleted cleanly
        layout.deletePoint("BK YARD TWIN");

        // and now the watched point itself, once nothing connects to it
        for (Edge e : new java.util.LinkedList<>(layout.getEdges()))
        {
            if (e.getStart().getName().equals("BK YARD") || e.getEnd().getName().equals("BK YARD"))
            {
                layout.deleteEdge(e.getStart().getName(), e.getEnd().getName());
            }
        }

        layout.deletePoint("BK YARD");

        assertTrue(station.getBlockedBy().isEmpty(),
            "the deleted point is still watching this station. Nothing stands on it and nothing can, "
            + "so the rule passes today - but the reference outlives the graph, and it vanishes across "
            + "a save and load, which makes the railway behave differently after a restart: "
            + station.getBlockedBy());
    }

    /**
     * The dialog that sets Unavailable While Occupied says what the rule now shuts: routes to the square and through
     * it, for every door that sends a train, not "Autonomy will not send a train to" it (OB-295).
     *
     * MUTATION: put back the old sentence in any of the eight languages, and this fails.
     */
    @Test
    public void testTheDialogSaysWhatTheRestrictionShuts() throws IOException
    {
        java.util.Map<String, String> said = new java.util.LinkedHashMap<>();

        said.put("messages.properties", "Autonomy will not send a train to");
        said.put("messages_da.properties", "Automatikken sender ikke et tog til");
        said.put("messages_de.properties", "Die Automatik schickt keinen Zug nach");
        said.put("messages_es.properties", "no enviar");
        said.put("messages_fr.properties", "enverra pas de train");
        said.put("messages_it.properties", "non mander");
        said.put("messages_nl.properties", "De automaat stuurt geen trein naar");
        said.put("messages_pl.properties", "Automatyka nie wy");

        for (java.util.Map.Entry<String, String> bundle : said.entrySet())
        {
            java.util.Properties read = new java.util.Properties();

            try (java.io.InputStream in = new java.io.FileInputStream("src/org/traincontrol/resources/" + bundle.getKey()))
            {
                read.load(in);
            }

            String sentence = read.getProperty("autosetup.ui.promptBlockedByPoints");

            assertNotNull(sentence, bundle.getKey() + " has no autosetup.ui.promptBlockedByPoints");

            assertFalse(sentence.contains(bundle.getValue()), bundle.getKey() + " still says only that autonomy will not"
                + " send a train to the square (ADU2-C6): " + sentence);

            if (bundle.getKey().equals("messages.properties"))
            {
                assertTrue(sentence.contains("to or through {0}") && sentence.contains("occupied"), "the dialog does not say"
                    + " trains are kept from routes to and through the square while a watched one is occupied: " + sentence);
            }
        }
    }
}
