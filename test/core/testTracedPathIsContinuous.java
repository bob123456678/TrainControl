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
 * A route drawn on the diagram is one line, with nothing missing out of the middle of it.
 *
 * Adam reported gaps in the line the autonomy editor draws when a path is tested - "drawn segments are
 * not connected over route tiles" - and later narrowed it: the running track diagram draws the same
 * route correctly, and only the editor's testing mode breaks.
 *
 * That narrowing rules out most of the machinery, and reading ruled out the rest of what I could check
 * from the outside: the reduced graph really does walk over route buttons (the sample layout has 43 of
 * them and the reduction crosses five), the editor annotates every square of its grid rather than a
 * filtered set, the two tracers compute their sides with the same method, and the registry that
 * publishes to the running diagram explicitly refuses to touch the editor's tiles.
 *
 * So this pins the one thing left that a test can reach: whether the sequence of squares the editor
 * draws through is CONTINUOUS. A line is drawn from the middle of each square to the sides it enters
 * and leaves by, and those sides come from comparing a square with its neighbours in the sequence - so
 * two squares that are not neighbours produce two stubs pointing at nothing, which is exactly what a
 * gap looks like.
 *
 * If this fails it names the two squares and what sits between them, which is the diagnosis.
 * If it passes, the fault is in the painting rather than in the route, and this stays as the guard
 * that says so.
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
        model = init(null, true, false, false, false);

        File folder = new File("test/test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        session = new AutonomySession(folder);
        session.open(pages);

        graph = session.getGraph();
        reducer = session.getReducer();
    }

    /**
     * Every run of track the reduction knows is a chain of neighbouring squares.
     *
     * Built exactly the way AutonomyEditorPanel.trace builds it - the squares between two sensors, with
     * the sensors themselves put back in between, because the reduction hands over the track and the
     * Points separately.
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

                // The same square twice is not a break - consecutive edges share the Point between
                // them, so a run built by concatenation names it twice and the drawing skips it
                if (previous.equals(here)) continue;

                if (TileGraph.gridSideTowards(previous, here) != null) continue;

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
     * And route buttons are part of those runs rather than something the track goes round.
     *
     * A route button carries no track meaning of its own and whatever line it sits on runs beneath it,
     * so a layout that threads them through running track - this one has 43 - depends on the reduction
     * walking over them. If it stopped doing that, the runs would still be continuous by the test
     * above, because they would simply take another way.
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
     * A square autonomy takes no notice of still shows the route that runs over it.
     *
     * This is the fault, and Adam found it from the picture: "it is just the route tiles with no
     * connecting edge, could it be the greyout?" It was.
     *
     * A route button carries no track meaning of its own, so the editor greys it - there is nothing on
     * it for the user to set, and offering a length or a direction would invite a click that does
     * nothing. But the greying returned before drawing anything else, and that swallowed the tested
     * route as well. "Nothing here to configure" had quietly come to mean "nothing here to draw",
     * which are different things: a drawn route is not an invitation to change something, it is an
     * answer to a question that was asked.
     *
     * It broke only in the editor because the running diagram draws routes through a different painter
     * that has no notion of ignored at all - which is exactly the asymmetry Adam reported.
     *
     * Painted for real and read back off the image, because the whole bug was that a value was correct
     * and never reached the screen. Anything short of looking at the pixels would have passed while it
     * was broken.
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

    /**
     * One annotation, greyed or not, carrying the given traces.
     */
    private static org.traincontrol.automationui.TileAnnotation annotation(boolean ignored,
        java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> traces)
    {
        return new org.traincontrol.automationui.TileAnnotation(null, -1, false, null, ignored,
            false, false, traces);
    }

    /**
     * Paints one tile and says whether anything strongly coloured landed on it.
     *
     * The greying is white and a mid grey hatch, so anything with real colour in it came from
     * something else - which is all this needs to know, and it does not tie the test to the exact
     * shade the route happens to be drawn in today.
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

                // Grey is where the three channels agree.  A coloured line is where they do not.
                if (Math.max(r, Math.max(green, b)) - Math.min(r, Math.min(green, b)) > 60)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * What is at a square, for a failure message somebody can act on.
     */
    private static String describe(TileGraph.TileKey tile)
    {
        LayoutDiagramComponent component = graph.getTiles().get(tile);

        return component == null ? "empty" : String.valueOf(component.getType());
    }
}
