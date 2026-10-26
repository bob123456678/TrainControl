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
 * MT-125, Adam: "feedback 1016/1015 offer south and west as facing directions, instead of north and east."
 *
 * The choices were worked out as "the side the train came in by, reversed", which is right on a straight and wrong on
 * a curve: on a curve joining north to east, a train that enters by the north side leaves by the EAST side, and south
 * is not a direction that square has any track in.  So the rule is "the other end of the piece of track I am standing
 * on", which is the opposite compass point only on a straight.
 *
 * Two assertions.  The RULE (every facing offered for a square is a side that square's own track uses) is
 * layout-independent and sweeps every square, but it reads the same map the facings are built from, so it cannot fail
 * by itself.  The TABLE (five named squares and where a train standing on each can be pointing, worked out from the
 * track rather than the code) pins the rule the bug was about, the curve MT-125 was reported on first.
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
        model = support.TestStation.simulated();

        File folder = new File("test/test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        // A throwaway copy, not the tracked fixture itself: `session.open` runs `migrateStationLabels`, which saves the
        // moment a `Point:` label turns up in a page, and `test/test_layout` is shared with every other class.
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
     * The facings offered on five named squares, written out by hand from the track under them.  The RULE test below
     * checks that every facing offered is a side `session.getRoutes(tile)` uses; a version that offered BOTH ends of
     * every route (including the side the train arrived by, MT-125's own defect class turned round) passed it, because
     * the arrival side is track too.  The facing menu now reads `facingsFor`, the BUILDER's `facingByName`, so the
     * premise (`getRoutes`, what track is on the square) and the answer come from different places and the two
     * assertions are independent.
     *
     * Each line reads: the square, the track on it, and where a train standing there can be pointing.  The first is the
     * curve MT-125 was reported about: `1 - Main:0,11` carries one piece of track joining its NORTH side to its EAST
     * side.  A train that came down the north leg and round the curve is pointing EAST; one that came in by the east is
     * pointing NORTH.  It is never pointing SOUTH, which is the answer the compass rule gives, and there is no track on
     * that side.
     *
     * The straights are here as controls: on `1 - Main:0,3` the two rules agree, and a table of only curves would not
     * notice a change that broke straights.  A square reachable from both ends offers both facings in the order the
     * arrival sides are visited (N, E, S, W), which is load-bearing: on a square with no side barred the FIRST answer
     * is the facing a placement with nothing recorded gets (`startableCopy` takes the first copy trains may arrive at).
     *
     * MUTATION: in `AutonomySession.onwardFrom`, offer every end of every route regardless of which side the train
     * arrived by - `if (route.getA() != null) out.add(route.getA()); if (route.getB() != null)
     * out.add(route.getB());`.  The rule test stays GREEN under it and this one fails on its first line
     * (`1 - Main:0,11 offers [N, E] where a train on its track can only be pointing [E]`).
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
            // Both ends: on the real reduction the east end is reachable too, so a train here can have come in either
            // way and can be pointing either way.
            {"1 - Main:0,11", "N-E", Arrays.asList(Side.E, Side.N)},
            // Reachable from 11,7 and from 16,10 - two directions, so both ends of the S-W track are arrival sides and
            // both are facings.
            {"1 - Main:12,9", "S-W", Arrays.asList(Side.W, Side.S)},
            // A STRAIGHT, and the control for the two curves above: reached from 0,11 and from 2,1,
            // so both ends again - and on a straight both rules agree, which is the point of having it.
            {"1 - Main:0,3",  "N-S", Arrays.asList(Side.S, Side.N)},
            // The line that still discriminates: reached only from 6,1, one arrival side, so one possible heading.  The
            // mutation this class was written against makes this square offer both, and the other four lines can no
            // longer catch it.
            {"1 - Main:2,1",  "E-W", Arrays.asList(Side.W)},
            {"1 - Main:6,1",  "E-W", Arrays.asList(Side.W, Side.E)}
        };

        java.util.List<String> disagreed = new java.util.ArrayList<>();

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

            // Every line, not the first one that disagrees: one wrong square is a square, five wrong squares is a rule.
            if (!session.facingChoices(tile).equals(line[2]))
            {
                disagreed.add(name + " offers " + session.facingChoices(tile)
                    + " and the table says " + line[2]);
            }
        }

        assertTrue(disagreed.isEmpty(),
            "a facing is the OTHER end of the piece of track the train is standing on - never the side"
            + " it came in by, and only on a straight the opposite compass point (MT-125). These"
            + " squares disagree with the table: " + disagreed);
    }

    /**
     * The square with this key, or null, found by its printed key so a square that has moved fails as "the layout no
     * longer has it".
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
     * Every facing offered is a side the square's own track uses: the layout-independent half.  It cannot catch a
     * facing that points at the wrong end of the right track (the table above does) but it sweeps every square rather
     * than five.
     *
     * MUTATION: make `AutonomySession.getReducer` return null and the assertNotNull fails.
     */
    @Test
    public void testAFacingIsAlwaysASideTheTrackActuallyUses()
    {
        assertNotNull(session, "the session did not open, so nothing below tests anything");

        // A reduction that produced nothing must fail, not return and report green with no assertion run.
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
