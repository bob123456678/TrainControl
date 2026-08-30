package regression;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Which way a train can be pointing is a fact about the TRACK it is standing on.
 *
 * MT-125, Adam: "feedback 1016/1015 offer south and west as facing directions, instead of north and
 * east."
 *
 * The choices were worked out as "the side the train came in by, reversed" - a train that entered by
 * the west side is pointing east. That is right on a straight and wrong on a curve, and the difference
 * is the whole bug: on a curve joining north to east, a train that enters by the north side leaves by
 * the EAST side. It is pointing east, not south. South is not a direction that square has any track in.
 *
 * So the rule is not "the opposite compass point". It is "the other end of the piece of track I am
 * standing on" - which happens to BE the opposite compass point on a straight, which is why this
 * survived on every square anybody checked.
 *
 * Two assertions, because one of them turned out not to be able to fail. The RULE - every facing
 * offered for a square is a side that square's own track actually uses - is layout-independent and
 * sweeps every square, but it reads the same map the facings are built from, so every answer the real
 * branch gives satisfies it by construction (TA-B8). The TABLE - five named squares and where a train
 * standing on each of them can be pointing, worked out from the track rather than from the code - is
 * what actually pins the rule the bug was about, and the curve MT-125 was reported on is the first
 * line of it.
 *
 * @author Adam
 */
public class testFacingFollowsTheTrack
{
    private static MarklinControlStation model;
    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        File folder = new File("test/test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        // A throwaway copy, not the tracked fixture itself (TST-C17). `session.open` runs
        // `migrateStationLabels`, which calls `store.save()` and `page.saveChanges(...)` the moment a
        // `Point:` label turns up in a page - not the case today, but `test/test_layout` is checked in and
        // shared with every other class in the suite, and this one is the only sibling that opened a
        // session on it directly instead of copying `config/autonomy` first, following
        // `testDiscardedEditsDoNotDeleteSetup`'s and `testErrorsStopTheSetupRunning`'s precedent.
        File temp = File.createTempFile("tc-facings", "");

        assertTrue(temp.delete(), "making room for a directory of the same name");
        assertTrue(new File(temp, "config/autonomy").mkdirs(), "could not make the copy");

        File from = new File(folder, "config/autonomy");

        for (File one : from.listFiles())
        {
            if (one.isFile())
            {
                java.nio.file.Files.copy(one.toPath(),
                    new File(temp, "config/autonomy/" + one.getName()).toPath());
            }
        }

        temp.deleteOnExit();

        session = new AutonomySession(temp);
        session.open(pages);
    }

    /**
     * The facings offered on five named squares, written out by hand from the track under them.
     *
     * TA-B8 of the 2026-08-24 test suite audit: the rule test below checks that every facing offered
     * is a side `session.getRoutes(tile)` uses - and `onwardFrom`, which produces the facings, reads
     * that same map to produce them.  Every answer the real branch gives satisfies that oracle by
     * construction, so the test could only ever fail on the compass fallback.  In particular a version
     * that offered BOTH ends of every route across the square - including the side the train arrived
     * by, which is MT-125's own defect class turned round - passed it, because the arrival side is
     * track too.
     *
     * So this is a table of answers, and it is written down from the LAYOUT rather than taken from the
     * code.  Each line reads: the square, the track on it, and where a train standing there can be
     * pointing.  The derivation, in full, for the first line - the curve MT-125 was reported about:
     *
     *   `1 - Main:0,11` carries one piece of track, joining its NORTH side to its EAST side.  Only one
     *   of those two ends is reachable in the reduced graph - the north one - so the only train that
     *   can be standing here came in by the north.  It came down the north leg and round the curve, so
     *   its front is at the east end: it is pointing EAST.  It is not pointing south, and south is the
     *   answer the compass rule gives; there is no track at all on the south side of this square.
     *
     * The straights are here for the same reason a control is: on `1 - Main:0,3` the two rules agree,
     * and a table containing only curves would not notice a change that broke straights.  `6,1` is
     * reachable from both ends and so offers both facings, in the order the arrival sides are visited
     * (N, E, S, W) - which is load-bearing, because the FIRST answer is the facing a placement with
     * nothing recorded on it actually gets.
     *
     * Mutation this must fail: in `AutonomySession.onwardFrom`, offer every end of every route
     * regardless of which side the train arrived by - `if (route.getA() != null) out.add(route.getA());
     * if (route.getB() != null) out.add(route.getB());`.  Run 2026-08-25: the rule test below stays
     * GREEN under it, and this one fails on its first line - `1 - Main:0,11 offers [N, E] where a
     * train on its track can only be pointing [E]`.  The other four lines would fail too; the loop
     * stops at the first.
     */
    @Test
    public void testTheFacingsOfferedOnNamedSquaresAreTheOtherEndOfTheirTrack()
    {
        assertNotNull(session, "the session did not open, so nothing below tests anything");

        assertNotNull(session.getReducer(), "the graph did not reduce, so there are no squares to "
            + "ask about - this class used to return silently here and report as green");

        // square, the track on it, where a train standing on it can be pointing
        Object[][] expected =
        {
            {"1 - Main:0,11", "N-E", Arrays.asList(Side.E)},
            {"1 - Main:12,9", "S-W", Arrays.asList(Side.W)},
            {"1 - Main:0,3",  "N-S", Arrays.asList(Side.N)},
            {"1 - Main:2,1",  "E-W", Arrays.asList(Side.W)},
            {"1 - Main:6,1",  "E-W", Arrays.asList(Side.W, Side.E)}
        };

        for (Object[] line : expected)
        {
            String name = (String) line[0];

            TileKey tile = squareNamed(name);

            assertNotNull(tile, "the layout no longer has the square " + name + ", so the answers "
                + "written down here are about track that is gone.  Re-derive the table rather than "
                + "deleting the line");

            // The premise the hand-written answer was derived from.  Asserted so that a layout or
            // reduction change says which square's track moved, rather than reading as a facing bug
            assertEquals(describe(session.getRoutes(tile)), line[1],
                name + " no longer carries the track the expected facing was worked out from");

            assertEquals(session.facingChoices(tile), line[2],
                name + " offers " + session.facingChoices(tile) + " where a train on its track can "
                + "only be pointing " + line[2] + ".  A facing is the OTHER end of the piece of track "
                + "the train is standing on - never the side it came in by, and only on a straight the "
                + "opposite compass point (MT-125)");
        }
    }

    /**
     * The square with this key, or null.
     *
     * By its printed key rather than by rebuilding a TileKey, so that a square which has moved fails
     * as "the layout no longer has it" instead of as a facing that is missing.
     */
    private static TileKey squareNamed(String key)
    {
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (key.equals(tile.toString())) return tile;
        }

        return null;
    }

    /**
     * Every facing offered is a side the square's own track uses.
     *
     * The layout-independent half.  It cannot catch a facing that points at the wrong end of the right
     * track - that is what the table above is for - but it sweeps every square rather than five.
     *
     * Mutation the assertNotNull must fail: make `AutonomySession.getReducer` return null.  This test
     * used to `return` silently on that and report as green having run no assertion; both tests in the
     * class now fail (run 2026-08-25).
     */
    @Test
    public void testAFacingIsAlwaysASideTheTrackActuallyUses()
    {
        assertNotNull(session, "the session did not open, so nothing below tests anything");

        // Was `if (... == null) return;`, which turned a reduction that produced nothing into a green
        // class with no assertion run at all
        assertNotNull(session.getReducer(),
            "the graph did not reduce, so there are no squares to check");

        int checked = 0;
        int curved = 0;

        StringBuilder wrong = new StringBuilder();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            List<Side> offered = session.facingChoices(tile);

            if (offered.isEmpty()) continue;

            Map<RouteId, Route> routes = session.getRoutes(tile);

            if (routes.isEmpty()) continue;

            checked++;

            if (isCurved(routes)) curved++;

            for (Side facing : offered)
            {
                if (uses(routes, facing)) continue;

                wrong.append("\n  ").append(tile).append(" offers ").append(facing)
                     .append(", but its track runs ").append(describe(routes));
            }
        }

        assertTrue(checked > 0, "no square with a route and a facing to check - is the sample layout "
            + "present, and did the graph reduce?");

        assertTrue(curved > 0, "no CURVED square was checked, so this proves nothing about the case "
            + "that was broken. The sample layout has curved feedback tiles - TopMainR1Inter and "
            + "TopMainR2Inter - so a run finding none of them means the reduction changed");

        assertEquals(wrong.toString(), "",
            "a square offers a facing pointing at no track (MT-125). A facing is the other end of the "
            + "piece of track the train is standing on, which is the opposite compass point only on a "
            + "straight:" + wrong);
    }

    private boolean uses(Map<RouteId, Route> routes, Side side)
    {
        for (Route route : routes.values())
        {
            if (route.getA() == side || route.getB() == side) return true;
        }

        return false;
    }

    private boolean isCurved(Map<RouteId, Route> routes)
    {
        for (Route route : routes.values())
        {
            if (route.getA() == null || route.getB() == null) continue;

            // Not a straight through and not a stub: the two ends are neither the same side nor
            // opposite sides, which is what makes the compass shortcut wrong
            if (route.getA() != route.getB() && route.getA().opposite() != route.getB()) return true;
        }

        return false;
    }

    private String describe(Map<RouteId, Route> routes)
    {
        StringBuilder out = new StringBuilder();

        for (Route route : routes.values())
        {
            if (out.length() > 0) out.append(" and ");

            out.append(route.getA()).append("-").append(route.getB());
        }

        return out.toString();
    }
}
