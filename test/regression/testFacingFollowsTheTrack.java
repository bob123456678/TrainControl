package regression;

import java.io.File;
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
 * The assertion below is the rule rather than a list of expected answers: every facing offered for a
 * square must be a side that square's own track actually uses. That is layout-independent, it fails
 * loudly on the curve, and it cannot be satisfied by a table of answers copied out of the code.
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

        File folder = new File("test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        session = new AutonomySession(folder);
        session.open(pages);
    }

    /**
     * Every facing offered is a side the square's own track uses.
     */
    @Test
    public void testAFacingIsAlwaysASideTheTrackActuallyUses()
    {
        if (session == null || session.getReducer() == null) return;

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
