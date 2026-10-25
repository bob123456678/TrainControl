package core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A route drawn on the diagram is one line, with nothing missing out of the middle of it.  Adam reported gaps in the
 * line the autonomy editor draws when a path is tested ("drawn segments are not connected over route tiles"), only in
 * the editor's testing mode.
 *
 * A line is drawn from the middle of each square to the sides it enters and leaves by, and those sides come from
 * comparing a square with its neighbors in the sequence, so two squares that are not neighbors produce two stubs
 * pointing at nothing.  If this fails it names the two squares and what sits between them.
 */
public class testTracedPathIsContinuous
{
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static TileGraph graph;
    private static GraphReducer reducer;

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

        session = new AutonomySession(folder);
        session.open(pages);

        graph = session.getGraph();
        reducer = session.getReducer();
    }

    /**
     * Every run of track the reduction knows is a chain of neighboring squares, built the way AutonomyEditorPanel.trace
     * builds it: the squares between two sensors, with the sensors themselves put back in between.
     */
    @Test
    public void testEveryReducedRunIsAChainOfNeighbours()
    {
        assertFalse(reducer.getEdges().isEmpty(), "the sample layout should reduce to some runs");

        List<String> breaks = new ArrayList<>();

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            List<TileGraph.TileKey> sequence = new ArrayList<>();

            sequence.add(edge.getStart());

            for (GraphReducer.TileStep step : edge.getPath()) sequence.add(step.getTile());

            sequence.add(edge.getEnd());

            for (int at = 1; at < sequence.size(); at++)
            {
                TileGraph.TileKey previous = sequence.get(at - 1);
                TileGraph.TileKey here = sequence.get(at);

                // The same square twice is not a break: consecutive edges share the Point between them.
                if (previous.equals(here)) continue;

                if (TileGraph.gridSideTowards(previous, here) != null) continue;

                // A PORTAL IS NOT A GAP: a link or tunnel joins two squares that are deliberately not neighbors, and
                // each page gets its own end of the run.  Checked through the store's own pairing, since two unrelated
                // consecutive links would be a real break.
                if (here.equals(session.getStore().getPortalPartner(previous))) continue;

                breaks.add(previous + " -> " + here + " (" + describe(previous)
                    + " to " + describe(here) + ")");
            }
        }

        assertTrue(breaks.isEmpty(),
            "the line is drawn from square to square, so squares that are not neighbours are drawn as "
            + "two stubs pointing at nothing - which is the gap.  " + breaks.size()
            + " of them: " + breaks);
    }

    /**
     * And route buttons are part of those runs rather than something the track goes round: this layout threads 43 of
     * them through running track.
     */
    @Test
    public void testRouteButtonsAreWalkedOver()
    {
        int crossings = 0;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                LayoutDiagramComponent component = graph.getTiles().get(step.getTile());

                if (component != null && component.isRoute()) crossings++;
            }
        }

        assertTrue(crossings > 0,
            "no run crosses a route button, on a layout with 43 of them threaded through its track.  "
            + "Either they have stopped conducting, or the runs are going round them - and a route "
            + "that goes round a button somebody placed on the main line is not the route the diagram "
            + "shows");
    }

    /**
     * A square autonomy takes no notice of still shows the route that runs over it (Adam: "it is just the route tiles
     * with no connecting edge, could it be the greyout?").  The graying returned before drawing anything else and
     * swallowed the tested route; the running diagram uses a painter with no notion of ignored.  Painted for real and
     * read back off the image, because the value was correct and never reached the screen.
     */
    @Test
    public void testAGreyedSquareStillShowsTheRouteThroughIt()
    {
        java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> through =
            java.util.Arrays.asList(new org.traincontrol.automationui.TileAnnotation.Trace(
                org.traincontrol.automationui.TilePorts.Side.W,
                org.traincontrol.automationui.TilePorts.Side.E, true));

        assertTrue(drawsSomethingOtherThanGrey(annotation(true, through)),
            "a route tested through a route button drew nothing but the greying, so the line came "
            + "apart wherever it crossed one - which on a layout that threads them through its "
            + "running track is most of the way along");

        assertTrue(drawsSomethingOtherThanGrey(annotation(false, through)),
            "and a square that is NOT greyed must still draw it, or this test proves nothing");

        assertFalse(drawsSomethingOtherThanGrey(annotation(true,
            java.util.Collections.<org.traincontrol.automationui.TileAnnotation.Trace>emptyList())),
            "a greyed square with no route through it draws only the greying - the fix must not turn "
            + "into 'draw everything on ignored squares', which is what the greying is there to stop");
    }

    /** One annotation, grayed or not, carrying the given traces. */
    private static org.traincontrol.automationui.TileAnnotation annotation(boolean ignored,
        java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> traces)
    {
        return new org.traincontrol.automationui.TileAnnotation(null, -1, false, null, ignored,
            false, false, traces);
    }

    /**
     * Paints one tile and says whether anything strongly colored landed on it: the graying is white and mid gray, so
     * any real color came from something else.
     */
    private static boolean drawsSomethingOtherThanGrey(
        org.traincontrol.automationui.TileAnnotation annotation)
    {
        int size = 60;

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            annotation.paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                int argb = image.getRGB(x, y);

                if (((argb >> 24) & 0xFF) < 40) continue;

                int r = (argb >> 16) & 0xFF;
                int green = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // Gray is where the three channels agree.  A colored line is where they do not.
                if (Math.max(r, Math.max(green, b)) - Math.min(r, Math.min(green, b)) > 60)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** What is at a square, for a failure message somebody can act on. */
    private static String describe(TileGraph.TileKey tile)
    {
        LayoutDiagramComponent component = graph.getTiles().get(tile);

        return component == null ? "empty" : String.valueOf(component.getType());
    }
}
