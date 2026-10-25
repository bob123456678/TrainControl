package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.traincontrol.automationui.TileAnnotation;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * The track diagram and the autonomy editor draw the arrows trains may follow, and take the clicks that set
 * them.
 *
 * One of the topic classes split from the former `testAutonomyDiagramSession` (FR-136 Phase 3): the whole
 * chain, from a decision somebody made to the graph a train could run on.  The session and layout folder
 * every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testTheDiagramDrawsWhatTrainsMayDo extends AutonomySessionFixture
{

    /**
     * A page left out grays the path type with the tools that test by it (Adam, FR-105: "when a page is excluded in the
     * autonomy editor, also disable/grey out the auto/manual radio buttons"): Test a Path and Why not Moving? were grayed,
     * and the pair choosing which paths they test stayed live, choosing nothing.
     *
     * MUTATION: leave the path type live on a page left out, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testAPageLeftOutGreysThePathType() throws Exception
    {
        threeStationsInARow("FR-105");

        session.setPageExcluded("main", true);

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { });

            panel[0].refresh();
        });

        assertFalse(panelPart(panel[0], "whyButton").isEnabled(), "precondition: Why not Moving? is live on a page left"
            + " out");

        for (String part : new String[] {"pathTypeLabel", "pathTypeAuto", "pathTypeManual"})
        {
            assertFalse(panelPart(panel[0], part).isEnabled(), part + " is live on a page left out of autonomy, beside"
                + " the tools it chooses for, greyed (FR-105)");
        }

        // AND LIVE AGAIN WITH THE PAGE BACK IN
        session.setPageExcluded("main", false);

        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0].refresh());

        for (String part : new String[] {"pathTypeLabel", "pathTypeAuto", "pathTypeManual"})
        {
            assertTrue(panelPart(panel[0], part).isEnabled(), part + " stayed greyed with the page back in (FR-105)");
        }
    }

    /**
     * A tooltip short enough for one line is not padded to the width a long one wraps at (Adam, MT-613: "the 'edit
     * autonomy on page' tooltip has a large whitespace on the right side of the message"): every tooltip was set 320
     * pixels wide, so a short reason sat at the left of a box twice its length.  A long one still wraps.
     *
     * MUTATION: set every tooltip at the wrapping width, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testAShortTooltipIsNotPaddedToTheWrappingWidth() throws Exception
    {
        java.lang.reflect.Method wrapped =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("wrapped", String.class);

        wrapped.setAccessible(true);

        String reason = org.traincontrol.util.I18n.t("autolayout.errorCannotEditWhileRunning");

        String shown = String.valueOf(wrapped.invoke(null, reason));

        assertFalse(shown.contains("width"), "a tooltip of one short line was padded to the wrapping width (MT-613): "
            + shown);

        assertTrue(shown.contains(reason), "a short tooltip lost its text: " + shown);

        String sentence = org.traincontrol.util.I18n.t("autosetup.ui.tooltipEditAutonomy");

        String longer = String.valueOf(wrapped.invoke(null, sentence + "  " + sentence + "  " + sentence));

        // The width the editor wraps at is its own, asked rather than restated
        int width = (Integer) privateConstant(org.traincontrol.gui.AutonomyEditorPanel.class, "TOOLTIP_WIDTH");

        assertTrue(longer.contains("width: " + width + "px"), "a tooltip three sentences long no longer wraps: " + longer);
    }

    /**
     * A one-way run of plain track is drawn once on the track diagram, at its first square, as the editor draws it (OB-321;
     * Adam, 2026-10-04: *"after import, redundant arrows can shown in the viewer, on a single straight segment with
     * multiple straight tracks that only allows travel in one direction. The editor correctly collapses then"*).
     *
     * A run's direction is stored on every square of it, and the diagram drew each square's: a red arrow on every square of
     * every one-way straight.
     *
     * MUTATION: draw every square's arrows again, or none of the run's, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAOneWayRunIsDrawnOnceOnTheDiagram() throws Exception
    {
        List<TileKey> run = aRunOfFourBetweenTwoStations("OB-321");

        TileKey leader = session.runLeaders().get(run.get(0));

        assertTrue(session.setOneWayRun(new TileKey("main", 1, 1), new TileKey("main", 6, 1)) > 0,
            "precondition: the run could not be made one-way");

        session.rebuild();

        java.util.Map<TileKey, TileAnnotation> drawn = session.staticAnnotations(run, true, false);

        assertTrue(drawn.get(leader) != null && !drawn.get(leader).isBlank(), "the run's first square draws nothing, so a"
            + " one-way run has no arrow on the diagram at all");

        for (TileKey square : run)
        {
            if (square.equals(leader)) continue;

            assertTrue(drawn.get(square) == null || drawn.get(square).isBlank(), square + " draws its own arrows though it"
                + " says only what the run's first square says (OB-321)");
        }
    }

    /**
     * A run whose squares say different things is brought to one, its narrowest - what a train finds - and every square
     * of it stores that, as its first square draws it (OB-361; Adam, on OB-321: *"Any disagreement should be corrected so
     * that the viewer displays the source of truth, and the editor flags issues, rather than surprising the user."*).  A
     * train passes every square of a run, so one square closed in a run open one way closes the run: every square is
     * stored closed, the first draws it closed and the rest draw nothing, on the track diagram as in the editor, and the
     * log says it once.
     *
     * MUTATION: leave a run's squares as they were stored, draw a square that follows its run, or draw the first square's
     * own direction, and this fails.
     *
     * @throws Exception from the session or the panel
     */
    @Test
    public void testARunThatSaysTwoThingsIsBroughtToWhatATrainFinds() throws Exception
    {
        List<TileKey> run = aRunOfFourBetweenTwoStations("OB-361 odd");

        TileKey leader = session.runLeaders().get(run.get(0));

        assertTrue(session.setOneWayRun(new TileKey("main", 1, 1), new TileKey("main", 6, 1)) > 0,
            "precondition: the run could not be made one-way");

        TileKey odd = new TileKey("main", 4, 1);

        assertFalse(odd.equals(leader), "precondition: the square closed is the run's first");

        session.takeSquaresBroughtToTheirArrows();

        // ONE SQUARE CLOSED ALONE, as no door of the editor does now but a file or an older version can
        session.setDirection(odd, new RouteId(0, 0), Direction.NONE);

        // STORED: every square of the run closed, as a train finds it
        for (TileKey square : run)
        {
            assertEquals(session.getStore().getTileDirection(square, new RouteId(0, 0)), Direction.NONE, square + " is"
                + " stored otherwise than closed, in a run a closed square closes (OB-361)");
        }

        // SAID ONCE, under the run's first square
        assertEquals(session.getSquaresBroughtToTheirArrows().keySet(), java.util.Collections.singleton(leader), "the run"
            + " brought to one direction is not said once, under its first square: "
            + session.getSquaresBroughtToTheirArrows());

        session.takeSquaresBroughtToTheirArrows();
        session.rebuild();

        assertTrue(session.takeSquaresBroughtToTheirArrows().isEmpty(), "the run was said again by the next rebuild");

        // DRAWN: the first square closed, the rest nothing, as in the editor
        java.util.Map<TileKey, TileAnnotation> drawn = session.staticAnnotations(run, true, false);

        assertTrue(drawn.get(leader) != null && drawn.get(leader).getMarks().size() == 1
            && drawn.get(leader).getMarks().get(0).getDirection() == Direction.NONE, "the run's first square does not draw"
            + " the run closed, and a train can pass no square of it: " + (drawn.get(leader) == null ? null
                : drawn.get(leader).getMarks()));

        org.traincontrol.gui.AutonomyEditorPanel panel = aPanel();

        for (TileKey square : run)
        {
            if (!square.equals(leader))
            {
                assertTrue(drawn.get(square) == null || drawn.get(square).isBlank(), square + " draws its own arrows though"
                    + " it follows its run (OB-361)");
            }

            List<TileAnnotation.Mark> diagram = drawn.get(square) == null
                ? new ArrayList<TileAnnotation.Mark>() : drawn.get(square).getMarks();

            assertEquals(diagram, panel.annotationFor(square).getMarks(), square + " is drawn otherwise on the track diagram"
                + " than in the editor (OB-361)");
        }
    }

    /**
     * A click on a run whose track simply stops sets every square of it, the last one too (OB-361): it was left out, as
     * a boundary, and the run said two things.
     *
     * MUTATION: leave the square beside the end of the track out again, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testARunToTheEndOfTheTrackIsSetToItsEnd() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 6, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 64, 64, accessoryDecoderType.MM2, null);

        for (int x = 2; x <= 4; x++) page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("OB-361 end of the track");
        session.setStation(new TileKey("main", 1, 1), true);

        TileKey leader = session.runLeaders().get(new TileKey("main", 3, 1));

        assertNotNull(leader, "precondition: the straights are not a run");

        assertEquals(session.runs().get(leader).getTiles().size(), 3, "precondition: the three straights are not one run");

        session.takeSquaresBroughtToTheirArrows();

        assertTrue(session.setRunDirection(leader, new RouteId(0, 0), Direction.TOWARD_A) > 0, "precondition: the run"
            + " could not be made one way");

        for (TileKey square : session.runs().get(leader).getTiles())
        {
            assertNotNull(session.getStore().getTileDirection(square, new RouteId(0, 0)), square + " of the run clicked one"
                + " way was left both ways");
        }

        assertTrue(session.takeSquaresBroughtToTheirArrows().isEmpty(), "the click left its run for the correction to"
            + " bring into line");
    }

    /**
     * Track laid on a blank square beside a one-way run takes the run's direction and is saved with the edit, so the next
     * open has nothing to correct.  The store held nothing about the blank square, so the edit's answer said
     * nothing was forgotten, the editor did not save, and the next open logged the run as a setup that "did not say the
     * same on every square" - about track just laid.  The drop is done as the track diagram editor does it: the tile
     * placed, then `forgetTiles` on its square, and a save when that says something changed.
     *
     * MUTATION: answer only what the store forgot again, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTrackLaidBesideAOneWayRunIsSavedWithTheEdit() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 64, 64, accessoryDecoderType.MM2, null);

        for (int x = 2; x <= 4; x++)
        {
            page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("RSA75-C1");
        session.setStation(new TileKey("main", 1, 1), true);

        TileKey leader = session.runLeaders().get(new TileKey("main", 3, 1));

        assertTrue(session.setRunDirection(leader, new RouteId(0, 0), Direction.TOWARD_A) > 0, "precondition: the run"
            + " could not be made one way");

        session.save();

        // A STRAIGHT DROPPED ON THE BLANK SQUARE AT THE END OF THE RUN, as the editor drops one
        TileKey laid = new TileKey("main", 5, 1);

        page.addComponent(componentType.STRAIGHT, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        if (session.forgetTiles(Arrays.asList(laid))) session.saveQuietly();

        assertNotNull(session.getStore().getTileDirection(laid, new RouteId(0, 0)), "precondition: the straight laid did"
            + " not take its run's direction");

        AutonomySession reopened = new AutonomySession(layout);

        reopened.open(Arrays.asList(page));

        assertNotNull(reopened.getStore().getTileDirection(laid, new RouteId(0, 0)), "the straight laid beside the one-way"
            + " run took the run's direction in memory and was not saved with the edit (RSA75-C1)");

        assertTrue(reopened.getSquaresBroughtToTheirArrows().isEmpty(), "the next open logged the track just laid as a"
            + " correction (RSA75-C1): " + reopened.getSquaresBroughtToTheirArrows());
    }

    /**
     * A run whose squares point opposite ways - no train can pass it - is left as it is and is a warning, about the run,
     * naming its squares (OB-361); a click on its arrow sets one way for all of it, and the warning goes.  Closing it in
     * the correction would hide which way was meant behind a setting that looks chosen.
     *
     * MUTATION: say nothing, or close the run, or name another square, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testARunPointingOppositeWaysIsAWarning() throws Exception
    {
        List<TileKey> run = aRunOfFourBetweenTwoStations("OB-361 opposite");

        TileKey leader = session.runLeaders().get(run.get(0));

        TileKey west = new TileKey("main", 2, 1);
        TileKey east = new TileKey("main", 5, 1);

        // ONE END ONE WAY, THE OTHER THE OTHER, as no door of the editor does but a file or an older version can
        session.setDirection(west, new RouteId(0, 0), Direction.TOWARD_A);
        session.setDirection(east, new RouteId(0, 0), Direction.TOWARD_B);

        assertEquals(session.getStore().getTileDirection(east, new RouteId(0, 0)), Direction.TOWARD_B, "the run pointing"
            + " opposite ways was not left as it is");
        assertNotEquals(session.getStore().getTileDirection(west, new RouteId(0, 0)), Direction.NONE, "the run pointing"
            + " opposite ways was closed");

        org.traincontrol.automationui.AutonomyChecks.Finding said = null;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if ("autosetup.ui.checkRunPointsOppositeWays".equals(finding.getMessageKey())) said = finding;
        }

        assertNotNull(said, "a run no train can pass, its squares pointing opposite ways, is not a warning: "
            + session.check());

        assertEquals(said.getSeverity(), org.traincontrol.automationui.AutonomyChecks.Severity.WARNING, "graded");

        assertEquals(said.getTile(), leader, "the warning is not about the run's first square, where its arrow is");

        assertEquals(new LinkedHashSet<>(said.getRelated()), new LinkedHashSet<>(run), "the warning does not name the"
            + " run's squares, so a click on it cannot outline the run");

        // THE ARROW CLICKED: one way for all of it
        session.setRunDirection(leader, new RouteId(0, 0), Direction.TOWARD_A);

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            assertNotEquals(finding.getMessageKey(), "autosetup.ui.checkRunPointsOppositeWays", "the warning stayed after"
                + " the run was set one way");
        }
    }

    /**
     * The track diagram draws the restrictions, the directions trains may run, or both - each its own choice (FR-110; Adam,
     * 2026-10-04: *"we also need to add an option to view the green arrows in the viewer, not just the red, similar to
     * the editor."*, and on MT-675: *"allow the user to choose: restrictions, allowances, or both.  don't make them
     * mutually exclusive."*).  The station ingress arrows follow the restrictions (2026-08-28).
     *
     * MUTATION: draw the green without its option, drop it with it, draw the red without its option, or draw nothing
     * with the green alone, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheGreenArrowsAreDrawnWhenAskedFor() throws Exception
    {
        List<TileKey> run = aRunOfFourBetweenTwoStations("FR-110");

        TileKey leader = session.runLeaders().get(run.get(0));

        TileAnnotation restrictions = session.staticAnnotationFor(leader, true);

        assertNotNull(restrictions, "precondition: an open run's first square says nothing with the arrows on");

        assertEquals(session.staticAnnotations(run, true, false).get(leader), restrictions, "without the option, the run's"
            + " first square is drawn otherwise than with the restrictions alone");

        assertEquals(session.staticAnnotations(run, true, true).get(leader),
            session.staticAnnotationFor(leader, true).withAllowedDirections(), "with the option, the run's first square"
            + " is not drawn with the green arrows (FR-110)");

        // THE GREEN ALONE: drawn, with no red and no ingress arrows (MT-675)
        assertEquals(session.staticAnnotations(run, false, true).get(leader),
            session.staticAnnotationFor(leader, true, false).withAllowedDirections().withoutRestrictions(), "with only the"
            + " allowed directions chosen, the run's first square is not drawn in green alone (MT-675)");

        assertNull(session.staticAnnotations(run, false, false).get(leader), "with neither chosen, the run's first square"
            + " is drawn anyway");
    }

    /**
     * The green arrows are a light green with a dark green edge, on the track diagram and in the autonomy editor alike
     * (Adam, 2026-10-04, after MT-675's *"green arrows are hard to see due to low contrast"* made them the signals' green:
     * *"The green I asked for on the track diagram is too bright/green"*, and of six drawn on the real tiles: *"F looks
     * best.  let's see about using that in the editor too."*).
     *
     * MUTATION: drop the edge, or paint either surface another green, and this fails.
     */
    @Test
    public void testTheGreenArrowsAreLightGreenWithADarkEdge() throws Exception
    {
        // The two greens are the annotation's own, asked rather than restated
        Color lightGreen = (Color) privateConstant(TileAnnotation.class, "ONE_WAY");
        Color darkGreen = (Color) privateConstant(TileAnnotation.class, "ONE_WAY_EDGE");

        List<TileAnnotation.Mark> marks = new ArrayList<>();

        marks.add(new TileAnnotation.Mark(Side.W, Side.E, Direction.BOTH));

        TileAnnotation diagram = new TileAnnotation(marks, -1, false, null, false, false, false, null, true, null)
            .withAllowedDirections();

        TileAnnotation editor = new TileAnnotation(marks, -1, false, null, false, false, false, null, false, null);

        for (TileAnnotation annotation : Arrays.asList(diagram, editor))
        {
            String which = annotation == diagram ? "the track diagram's" : "the autonomy editor's";

            assertTrue(pixelsOf(annotation, lightGreen) > 0, which + " green arrows are not the light green");

            assertTrue(pixelsOf(annotation, darkGreen) > 0, which + " green arrows have no dark green edge");

            assertEquals(pixelsOf(annotation, new Color(0, 255, 0)), 0, which + " green arrows are the signals' green");
        }
    }

    /**
     * Every arrow sits on its rail, the green and the red alike: a Y's toe arrow is as wide each side of the toe, and a
     * straight's arrows as tall above as below - the lit pixels of a 30-pixel tile from 9 to 20, about 15.0, where the
     * permanent Y's toe and straight track are drawn.  A red arrow's outline, rounded to the pixel grid, hung half a pixel
     * right and down, from 9 to 21; the green one's new edge would have too (Adam, 2026-10-04: *"your down arrow on the Y
     * may still be slightly off center"*).
     *
     * MUTATION: stroke an arrow's outline on the pixel grid again, or anchor the arrows half a pixel over, and this fails.
     */
    @Test
    public void testTheArrowsSitOnTheArtsCentreLine()
    {
        // ONE WAY INTO A Y'S TOE, the green alone: the toe's arrow and nothing else on the square
        List<TileAnnotation.Mark> intoTheToe = new ArrayList<>();

        intoTheToe.add(new TileAnnotation.Mark(Side.W, Side.S, Direction.TOWARD_B));
        intoTheToe.add(new TileAnnotation.Mark(Side.E, Side.S, Direction.TOWARD_B));

        assertCentred(new TileAnnotation(intoTheToe, -1, false, null, false, false, false, null, true, null)
            .withAllowedDirections().withoutRestrictions(), true, "a Y's green toe arrow");

        // ONE WAY OUT OF IT, the red alone
        List<TileAnnotation.Mark> outOfTheToe = new ArrayList<>();

        outOfTheToe.add(new TileAnnotation.Mark(Side.W, Side.S, Direction.TOWARD_A));
        outOfTheToe.add(new TileAnnotation.Mark(Side.E, Side.S, Direction.TOWARD_A));

        assertCentred(new TileAnnotation(outOfTheToe, -1, false, null, false, false, false, null, true, null), true,
            "a Y's red toe arrow");

        // A STRAIGHT ONE WAY EAST: the green arrow at the east alone, then the red at the west alone
        List<TileAnnotation.Mark> east = new ArrayList<>();

        east.add(new TileAnnotation.Mark(Side.W, Side.E, Direction.TOWARD_B));

        assertCentred(new TileAnnotation(east, -1, false, null, false, false, false, null, true, null)
            .withAllowedDirections().withoutRestrictions(), false, "a straight's green arrow");

        assertCentred(new TileAnnotation(east, -1, false, null, false, false, false, null, true, null), false,
            "a straight's red arrow");
    }

    /**
     * A private constant of a class, read by reflection so a claim asks the program's rule instead of restating it.
     *
     * @param owner the class that declares it
     * @param name the constant's name
     * @return its value
     * @throws Exception when the class has no such constant
     */
    private static Object privateConstant(Class<?> owner, String name) throws Exception
    {
        java.lang.reflect.Field field = owner.getDeclaredField(name);

        field.setAccessible(true);

        return field.get(null);
    }

    /** Asserts an annotation's lit pixels on a 30-pixel tile run from 9 to 20 across its rail, about 15.0. */
    private static void assertCentred(TileAnnotation annotation, boolean columns, String which)
    {
        int[] lit = extentOf(paintedOnBlack(annotation, 30), 0, 30, 0, 30, columns);

        assertEquals(lit[0] + "-" + lit[1], "9-20", which + " is not centred on its rail - " + (columns ? "columns" : "rows")
            + " " + lit[0] + " to " + lit[1] + " of a 30-pixel tile, whose rail is at 15.0");
    }

    /** An annotation painted on black, on a square of this size. */
    private static BufferedImage paintedOnBlack(TileAnnotation annotation, int size)
    {
        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, size, size);

            annotation.paint(g, size, size, false);
        }
        finally
        {
            g.dispose();
        }

        return shot;
    }

    /**
     * The first and last column (or row) holding anything but black, within a window of the image.
     *
     * @param columns true for columns, false for rows
     */
    private static int[] extentOf(BufferedImage shot, int x0, int x1, int y0, int y1, boolean columns)
    {
        int first = Integer.MAX_VALUE;
        int last = -1;

        for (int x = x0; x < x1; x++)
        {
            for (int y = y0; y < y1; y++)
            {
                if ((shot.getRGB(x, y) & 0xFFFFFF) == 0) continue;

                int at = columns ? x : y;

                first = Math.min(first, at);
                last = Math.max(last, at);
            }
        }

        assertTrue(last >= 0, "precondition: nothing was painted where the arrow should be");

        return new int[] {first, last};
    }

    /** How many pixels of exactly this color an annotation paints on black. */
    private static int pixelsOf(TileAnnotation annotation, Color colour)
    {
        int size = 48;

        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, size, size);

            annotation.paint(g, size, size, false);
        }
        finally
        {
            g.dispose();
        }

        int count = 0;

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                if ((shot.getRGB(x, y) & 0xFFFFFF) == (colour.getRGB() & 0xFFFFFF)) count++;
            }
        }

        return count;
    }

    /**
     * The green arrows are painted on an annotation that draws the restrictions, and with no wash under them, which the
     * restrictions do not have either (FR-110).
     *
     * MUTATION: skip the open sides whatever the option, or wash the square with them, and this fails.
     */
    @Test
    public void testTheGreenArrowsArePaintedWithoutAWash()
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        marks.add(new TileAnnotation.Mark(Side.W, Side.E, Direction.BOTH));

        int[] restrictions = greenAndCorner(new TileAnnotation(marks, -1, false, null, false, false, false, null, true,
            null));

        int[] green = greenAndCorner(new TileAnnotation(marks, -1, false, null, false, false, false, null, true, null)
            .withAllowedDirections());

        assertEquals(restrictions[0], 0, "precondition: the restrictions alone paint green on open track");

        assertTrue(green[0] > 0, "asked for the green arrows, an open straight paints none (FR-110)");

        assertEquals(green[1], Color.BLACK.getRGB(), "the green arrows came with a wash over the square");
    }

    /**
     * Allowed only paints the directions trains may run and none of the shut ones (Adam, 2026-10-04: *"In addition to
     * "restrictions only", add "allowed only" to the autonomy editor."*).
     *
     * MUTATION: draw the shut sides under Allowed only, and this fails.
     */
    @Test
    public void testAllowedOnlyPaintsTheGreenAndNoneOfTheRed()
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        // ONE WAY, west to east: out by the east is open, out by the west is shut
        marks.add(new TileAnnotation.Mark(Side.W, Side.E, Direction.TOWARD_B));

        int[] all = greenAndRed(new TileAnnotation(marks, -1, false, null, false, false, false, null, false, null));

        int[] allowed = greenAndRed(new TileAnnotation(marks, -1, false, null, false, false, false, null, false, null)
            .withoutRestrictions());

        assertTrue(all[0] > 0 && all[1] > 0, "precondition: Show All does not paint a one-way square green and red");

        assertTrue(allowed[0] > 0, "Allowed only paints no green arrow on a one-way square");

        assertEquals(allowed[1], 0, "Allowed only paints the shut side's red arrow");
    }

    /**
     * The autonomy editor offers Allowed only right after Restrictions only, draws a one-way square under it in green
     * alone, and remembers it - and a view remembered before it was added opens as it did: the box is remembered by
     * what a view means, not by its place (Adam, 2026-10-04).
     *
     * MUTATION: remember the box by its place, or leave Allowed only drawing the red, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testTheEditorOffersAllowedOnlyAndRemembersEachViewByItsMeaning() throws Exception
    {
        threeStationsInARow("Allowed only");

        final TileKey straight = new TileKey("main", 2, 1);

        session.setDirection(straight, new RouteId(0, 0), Direction.TOWARD_B);
        session.rebuild();

        // A test may read the user's settings; it may not decide them - put back as found, unset included
        java.util.prefs.Preferences prefs =
            org.traincontrol.util.Util.preferencesFor(org.traincontrol.gui.AutonomyEditorPanel.class);

        final String key = "autonomyEditorDirections";

        boolean had = prefs.get(key, null) != null;
        int was = prefs.getInt(key, 1);

        try
        {
            // VIEWS REMEMBERED BEFORE ALLOWED ONLY, which open as they did
            String[][] remembered = {{"2", "autosetup.ui.directionsNone"}, {"3", "autosetup.ui.directionsArrivals"},
                {"1", "autosetup.ui.directionsRestrictions"}, {"0", "autosetup.ui.directionsAll"}};

            for (String[] view : remembered)
            {
                prefs.putInt(key, Integer.parseInt(view[0]));

                assertEquals(aPanel().getShowDirections().getSelectedItem(), org.traincontrol.util.I18n.t(view[1]),
                    "a view remembered as " + view[0] + " before Allowed only was added opens as another");
            }

            // ALLOWED ONLY, right after Restrictions only
            final org.traincontrol.gui.AutonomyEditorPanel panel = aPanel();

            final javax.swing.JComboBox<String> box = panel.getShowDirections();

            assertEquals(box.getItemAt(1), org.traincontrol.util.I18n.t("autosetup.ui.directionsRestrictions"),
                "precondition: Restrictions only is not the second view");

            assertEquals(box.getItemAt(2), org.traincontrol.util.I18n.t("autosetup.ui.directionsAllowed"), "the editor"
                + " does not offer Allowed only right after Restrictions only");

            final TileAnnotation[] drawn = new TileAnnotation[2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                box.setSelectedItem(org.traincontrol.util.I18n.t("autosetup.ui.directionsAll"));
                drawn[0] = panel.annotationFor(straight);

                box.setSelectedItem(org.traincontrol.util.I18n.t("autosetup.ui.directionsAllowed"));
                drawn[1] = panel.annotationFor(straight);
            });

            assertTrue(greenAndRed(drawn[0])[1] > 0, "precondition: under Show All the one-way square has no red arrow");

            int[] allowed = greenAndRed(drawn[1]);

            assertTrue(allowed[0] > 0 && allowed[1] == 0, "under Allowed only the one-way square is not drawn in green"
                + " alone: green " + allowed[0] + ", red " + allowed[1]);

            assertTrue(panel.isShowingDirections(), "Allowed only is not counted as showing directions, so the editor draws no arrows under it");

            assertEquals(prefs.getInt(key, -1),
                (int) (Integer) privateConstant(org.traincontrol.gui.AutonomyEditorPanel.class, "VIEW_ALLOWED"),
                "Allowed only is not remembered by its own value");

            assertEquals(aPanel().getShowDirections().getSelectedItem(),
                org.traincontrol.util.I18n.t("autosetup.ui.directionsAllowed"), "Allowed only does not open again");
        }
        finally
        {
            if (had) prefs.putInt(key, was);
            else prefs.remove(key);
        }
    }

    /** A fresh autonomy editor panel on the main page, built on the event thread. */
    private org.traincontrol.gui.AutonomyEditorPanel aPanel() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { }));

        return panel[0];
    }

    /** An annotation painted on black: how many of its pixels are green, and how many red. */
    private static int[] greenAndRed(TileAnnotation annotation)
    {
        int size = 48;

        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, size, size);

            annotation.paint(g, size, size, false);
        }
        finally
        {
            g.dispose();
        }

        int green = 0;
        int red = 0;

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                Color c = new Color(shot.getRGB(x, y));

                if (c.getGreen() > 150 && c.getRed() < 120 && c.getBlue() < 140) green++;

                if (c.getRed() > 150 && c.getGreen() < 80 && c.getBlue() < 80) red++;
            }
        }

        return new int[] {green, red};
    }

    /**
     * A Y's arrows lie along its track (MT-676; Adam, 2026-10-04: *"the arrows on two permanent Y's are not aligned.
     * [M]ake the arrow at the base point straight down, and the left and right aligned just like on curved tiles.  Right
     * now, the angles are just a bit off, and they are asymmetrical."*): the toe's straight out of the square, and each
     * leg's along the chord from the toe, as on a curve - so the two legs mirror each other.
     *
     * MUTATION: aim a Y's arrows from one point of the square again, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testAYsArrowsLieAlongItsTrack() throws Exception
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        // toe south, legs west and east
        marks.add(new TileAnnotation.Mark(Side.W, Side.S, Direction.BOTH));
        marks.add(new TileAnnotation.Mark(Side.E, Side.S, Direction.BOTH));

        TileAnnotation y = new TileAnnotation(marks, -1, false, null, false, false, false, null, false, null);

        double[] toe = headingOf(y, Side.S);
        double[] west = headingOf(y, Side.W);
        double[] east = headingOf(y, Side.E);

        assertTrue(Math.abs(toe[0]) < 1e-9 && toe[1] > 0, "the arrow at a Y's toe does not point straight out of the"
            + " square (MT-676): " + Arrays.toString(toe));

        assertTrue(west[0] < 0 && west[1] < 0 && Math.abs(Math.abs(west[0]) - Math.abs(west[1])) <= 2, "the arrow on a"
            + " Y's west leg does not lie along the chord from the toe, as on a curve (MT-676): " + Arrays.toString(west));

        assertTrue(Math.abs(east[0] + west[0]) <= 1 && Math.abs(east[1] - west[1]) <= 1, "the arrows on a Y's two legs do"
            + " not mirror each other (MT-676): west " + Arrays.toString(west) + ", east " + Arrays.toString(east));
    }

    /** Which way an annotation aims its arrow at a side, on a 48-pixel square. */
    private static double[] headingOf(TileAnnotation annotation, Side side) throws Exception
    {
        java.lang.reflect.Method heading = TileAnnotation.class.getDeclaredMethod("heading", Side.class, int.class,
            int.class);

        heading.setAccessible(true);

        return (double[]) heading.invoke(annotation, side, 48, 48);
    }

    /** An annotation painted on black: how many of its pixels are green, and the color of its corner. */
    private static int[] greenAndCorner(TileAnnotation annotation)
    {
        int size = 48;

        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, size, size);

            annotation.paint(g, size, size, false);
        }
        finally
        {
            g.dispose();
        }

        int green = 0;

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                Color c = new Color(shot.getRGB(x, y));

                if (c.getGreen() > 150 && c.getRed() < 120 && c.getBlue() < 140) green++;
            }
        }

        return new int[] {green, shot.getRGB(1, 1)};
    }

    /**
     * Allow Every Path opens both ways every square whose setting shuts a move - one-way track, closed track, and a switch
     * still at its base-to-forks default - and says how many squares (FR-108; Adam, 2026-10-04: *"add a bulk tool to
     * allow all paths, i.e. remove any red arrows in one go. With warning."*).
     *
     * MUTATION: leave any of the three out of the count or the opening, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAllowEveryPathOpensWhatTheSettingShuts() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 7, 5, null, null);

        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 2, 3, 0, 0, 1, 1, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("FR-108");

        TileKey oneWay = new TileKey("main", 2, 1);
        TileKey closed = new TileKey("main", 4, 1);
        TileKey turnout = new TileKey("main", 2, 3);

        session.setDirection(oneWay, new RouteId(0, 0), Direction.TOWARD_B);
        session.setDirection(closed, new RouteId(0, 0), Direction.NONE);
        session.rebuild();

        java.util.Map<TileKey, List<RouteId>> shut = session.routesNotOpenBothWays();

        assertEquals(new java.util.HashSet<>(shut.keySet()), new java.util.HashSet<>(Arrays.asList(oneWay, closed,
            turnout)), "Allow Every Path does not count exactly the one-way, the closed and the defaulted switch's squares");

        assertEquals(session.allowEveryPath(), 3, "Allow Every Path does not say it opened the three squares");

        for (TileKey square : Arrays.asList(oneWay, closed, turnout))
        {
            for (RouteId route : session.getGraph().getRoutes(square).keySet())
            {
                assertEquals(session.getGraph().getDirection(square, route), Direction.BOTH, square + "'s road " + route
                    + " is not open both ways after Allow Every Path");
            }
        }

        assertTrue(session.routesNotOpenBothWays().isEmpty(), "after Allow Every Path, some square still counts as shut: "
            + session.routesNotOpenBothWays());
    }

    /**
     * A turnout with no address is opened only as far as its blades let a train through (FR-108).  At its default, or set
     * to run the one way its blades do, it is not counted - it refuses nothing a train could do, though its red arrow
     * still shows the one way its blades run; closed, it is counted; opened, it runs the one way the hardware
     * does, into its toe.
     *
     * MUTATION: count a road only the hardware restricts, or leave a closed one out, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testAllowEveryPathLeavesAPermanentTurnoutAsItsBladesRun() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 5, 5, null, null);

        page.addComponent(componentType.CUSTOM_PERM_LEFT, 2, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("FR-108 permanent");

        TileKey turnout = new TileKey("main", 2, 2);

        assertFalse(session.routesNotOpenBothWays().containsKey(turnout), "a turnout with no address, at its default, is"
            + " counted as shut, though nothing a setting does opens it further");

        RouteId road = session.getGraph().getRoutes(turnout).keySet().iterator().next();

        Route route = session.getGraph().getRoutes(turnout).get(road);

        assertTrue(route.getA() == Side.S || route.getB() == Side.S, "precondition: the turnout's road does not end at its"
            + " toe, the south side");

        Side fork = route.other(Side.S);

        // SET TO RUN THE WAY ITS BLADES DO, which is not "both ways" and shuts nothing
        session.setDirection(turnout, road, route.getA() == Side.S ? Direction.TOWARD_A : Direction.TOWARD_B);
        session.rebuild();

        assertFalse(session.routesNotOpenBothWays().containsKey(turnout), "a turnout with no address, set to run into its"
            + " toe as its blades do, is counted as shut, though it refuses nothing a train could do");

        session.setDirection(turnout, road, Direction.NONE);
        session.rebuild();

        // CLOSED AT ITS TOE, so both its roads (OB-351): a road shut shuts the arm it leaves by, and every road leaves by
        // the toe - whose arrow is the whole of what the square says
        assertEquals(session.routesNotOpenBothWays().get(turnout), new ArrayList<>(session.getGraph().getRoutes(turnout)
            .keySet()), "a turnout with no address, closed, is not counted as shut on both its roads");

        assertEquals(session.allowEveryPath(), 1, "Allow Every Path does not say it opened the closed turnout");

        assertTrue(leavesBy(turnout, fork, Side.S), "opened, the turnout does not let a train run into its toe");

        assertFalse(leavesBy(turnout, Side.S, fork), "opened, the turnout lets a train out through blades it cannot throw");
    }

    /** Whether a train that entered a square by one side may leave it by another. */
    private boolean leavesBy(TileKey tile, Side entry, Side exit)
    {
        for (org.traincontrol.automationui.TileGraph.Exit leaving : session.getGraph().exits(tile, entry))
        {
            if (leaving.getSide() == exit) return true;
        }

        return false;
    }

    /** Stations at 1,1 and 6,1 with four straights between them, in a configuration of this name: the straights. */
    private List<TileKey> aRunOfFourBetweenTwoStations(String configuration) throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 64, 64, accessoryDecoderType.MM2, null);

        for (int x = 2; x <= 5; x++)
        {
            page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.addComponent(componentType.FEEDBACK, 6, 1, 0, 0, 65, 65, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize(configuration);

        for (int x : new int[] {1, 6}) session.setStation(new TileKey("main", x, 1), true);

        List<TileKey> run = new ArrayList<>();

        for (int x = 2; x <= 5; x++) run.add(new TileKey("main", x, 1));

        TileKey leader = session.runLeaders().get(run.get(0));

        assertNotNull(leader, "precondition: the straights are not a run");

        assertEquals(new LinkedHashSet<>(session.runs().get(leader).getTiles()), new LinkedHashSet<>(run),
            "precondition: the four straights are not one run");

        return run;
    }

    /**
     * A plain square marked out of service draws its cross while trains are running, not only while
     * the setup is being edited.
     *
     * The running diagram badges a square only when it is worth badging, and the list of what that
     * means is written six lines above the expression: *"this is a station, trains turn round here,
     * autonomy is not using it."*  The third had no term in it.  So a plain sensor switched off got no
     * badge at all and drew nothing, while the editor - which badges every Point in the graph - drew
     * its cross.  The one drawing that says "nothing may pass here" appeared while setting the railway
     * up and vanished while running it, which is the half the operator is looking at when a train does
     * not arrive.
     *
     * MUTATION: dropping the `|| shut` term from `worthABadge` fails this and nothing else.
     */
    @Test
    public void testAShutPlainSquareDrawsItsCrossOnTheRunningDiagram() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Shut", null);
        session.getStore().setActiveConfiguration("Shut");

        // The MIDDLE sensor, which is neither a station nor anywhere trains turn round - so the only
        // thing that could earn it a badge is being switched off.
        TileKey plain = new TileKey("main", 4, 1);

        session.rebuild();

        org.traincontrol.automationui.TileAnnotation open = session.staticAnnotationFor(plain);

        assertTrue(open == null || open.getBadge() == null,
            "control: an ordinary plain sensor already carries a badge on the running diagram, so "
            + "this fixture cannot show what switching one off adds.  Got: " + open);

        session.setPointProperty(plain, "active", Boolean.FALSE);

        session.rebuild();

        org.traincontrol.automationui.TileAnnotation shut = session.staticAnnotationFor(plain);

        assertNotNull(shut, "a square switched off is described as nothing at all on the running "
            + "diagram, so it draws exactly like one in use");

        assertNotNull(shut.getBadge(),
            "a square switched off earns no badge on the running diagram, so the cross the editor "
            + "draws for it disappears the moment autonomy starts");

        assertTrue(shut.getBadge().isImpassable(),
            "the badge for a square nothing may use does not say so, so it draws as an ordinary "
            + "point rather than as a cross");
    }

    /**
     * And the running graph is told, so trains do not simply drive through it.
     *
     * The editor offers **Out of service** on every square, station or not, and its handler writes
     * `active` and touches nothing else - no direction, no arrow.  The builder used to drop that
     * property for anything that is not a station, on the reasoning that "the arrows say that through
     * the derivation".  They do not: nothing sets an arrow when the square is switched off.
     *
     * So the cross was a promise the runtime never kept.  `Layout.isPathClear` refuses a path whose
     * intermediate point is inactive, which is exactly what the cross means, and it can only do that
     * if the flag reaches the graph.
     *
     * MUTATION: putting back `if ("active".equals(key) && !point.isStation()) continue;` fails this.
     */
    @Test
    public void testAShutPlainSquareReachesTheRunningGraph() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("ShutBuild", null);
        session.getStore().setActiveConfiguration("ShutBuild");

        TileKey plain = new TileKey("main", 4, 1);

        session.rebuild();

        // THE CONTROL, ASKED OF THE COPIES.
        //
        // It used to compare `pointNameForTile` - a BASE name - against the names in the built
        // configuration, which are the emitted COPY names.  This square splits into two copies, so the
        // builder suffixes both and the base name is never in that list: the control could not fail,
        // and what it exists to catch is a fixture that ships the square already inactive, which would
        // make the whole test vacuous.
        java.util.List<String> beforeAnything =
            session.pointNamesFor(session.pointNameForTile(plain));

        assertFalse(beforeAnything.isEmpty(), "the square emitted no Points to begin with");

        for (String copy : beforeAnything)
        {
            assertFalse(inactivePointNames(session).contains(copy),
                "control: " + copy + " is already inactive in the built graph before anything switched "
                + "it off, so this fixture cannot show the flag arriving");
        }

        session.setPointProperty(plain, "active", Boolean.FALSE);

        session.rebuild();

        String name = session.pointNameForTile(plain);

        assertNotNull(name, "the square stopped being a Point when it was switched off");

        // EVERY COPY OF IT, not just one.  A square with track on both sides is emitted once per
        // arrival side, and a train barred from one side and admitted from the other is a square that
        // is half switched off - which is not a state the menu can express or the cross can mean.
        java.util.List<String> copies = session.pointNamesFor(name);

        // TWO, not one.  The comment above is about a square that became SEVERAL Points, so a
        // fixture that emits one satisfies the loop below while testing nothing it is written for -
        // and this is the sibling of the floor put on the same shape 2,800 lines down.
        assertTrue(copies.size() >= 2,
            "precondition: this needs a square that became several Points, and it got " + copies.size()
            + " - " + copies);

        for (String copy : copies)
        {
            assertTrue(inactivePointNames(session).contains(copy),
                "a plain square switched off is built as an ACTIVE point (" + copy + "), so the "
                + "runtime never learns about it and trains carry on running through the square the "
                + "editor draws a cross on.  Inactive points in the build: "
                + inactivePointNames(session));
        }
    }

    /**
     * The names of the points the built configuration marks inactive.
     *
     * @param session the setup to build
     * @return the names, empty when nothing is switched off
     */
    private java.util.List<String> inactivePointNames(AutonomySession session) throws Exception
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject p = points.getJSONObject(i);

            if (p.has("active") && !p.optBoolean("active", true)) out.add(p.getString("name"));
        }

        return out;
    }

    /**
     * Two sensors with two plain tiles between them.
     */
    /**
     * A ROAD THE OPERATOR HAS SHUT DRAWS NO ARROW, EVEN WHERE THE BLADES WOULD HAVE ONE.
     *
     * `directionMarks` is what the diagram draws.  It used to override the authored answer whenever
     * it was not `NONE`, on the reasoning that a route the hardware restricts is one-way whatever the
     * user chose - true for the DEFAULT, which is `BOTH`, and false for the case that matters.
     *
     * On a permanent turnout every route is directed at the toe, so `TOWARD_A` - toward the fork -
     * permits only an entry the blades refuse: nothing passes, which is exactly what an operator who
     * shuts that arm means.  The override turned that into an arrow pointing at the toe, so the
     * drawing showed trains running along a road that had been closed.  Nothing covered
     * `directionMarks` at all before this claim, which is how it survived.
     *
     * MUTATION: drop the `isPassable` branch from `directionMarks` and the shut route draws an arrow
     * again; make `isPassable` answer true for everything and the same claim fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testAShutRouteDrawsNoArrow() throws IOException
    {
        session.open(Arrays.asList(permanentTurnout()));

        TileKey toe = new TileKey("main", 3, 1);

        java.util.Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes =
            session.getRoutes(toe);

        assertFalse(routes.isEmpty(), "the fixture gave the permanent turnout no routes at all");

        for (java.util.Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

            assertNotNull(route.getDirectedToward(),
                "precondition: this tile's routes should be directed at the toe, and nothing below is"
                + " about a permanent turnout if they are not");

            // THE ANSWER THAT PERMITS ONLY THE ENTRY THE BLADES REFUSE: a closure, not a direction.
            //
            // Travel TOWARD A means entering at B, which `isTraversableFrom` allows only when the
            // route is directed at A.  So the shut answer is the one naming the side the route is NOT
            // directed at - the first draft of this line named the allowed one and asserted the arrow
            // was absent, which failed against correct code.
            Direction shut = route.getDirectedToward() == route.getA() ? Direction.TOWARD_B
                : Direction.TOWARD_A;

            session.setDirection(toe, entry.getKey(), shut);
        }

        for (TileAnnotation.Mark mark : session.directionMarks(toe))
        {
            assertEquals(mark.getDirection(), Direction.NONE,
                "the diagram draws an arrow along a road the operator has shut: the answer stored"
                + " permits only an entry the blades refuse, so no train can pass either way, and the"
                + " arrow says trains run toward the toe");
        }

        // AND THE DEFAULT STILL DRAWS THE ONE ROAD THERE IS.  A rule that drew nothing for these tiles
        // would take away the arrow that says which way the turnout can be used at all.
        for (java.util.Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            session.setDirection(toe, entry.getKey(), Direction.BOTH);
        }

        // A MARK PER ROAD, which is what keeps the loop below from running over nothing (FR-136)
        assertEquals(session.directionMarks(toe).size(), routes.size(),
            "the turnout is open in the direction the blades allow, and the diagram draws no mark for its roads");

        for (TileAnnotation.Mark mark : session.directionMarks(toe))
        {
            assertNotEquals(mark.getDirection(), Direction.NONE,
                "the turnout is open in the direction the blades allow, and the diagram draws nothing");
        }
    }

    /**
     * A run of track with a permanently-set turnout at its far end: no address, trailing moves only.
     *
     * @return the page
     * @throws IOException from the diagram
     */
    private LayoutDiagram permanentTurnout() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CUSTOM_PERM_LEFT, 3, 1, 1, 0, 0, 0,
            accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * THE TWO ARCS OF A DOUBLE CURVE ARE TWO PIECES OF METAL (Adam, 2026-09-22, on OB-238).
     *
     * *"It is two pieces of metal.  Imagine two parallel tracks simple appearing on one tile for visual
     * convenience.  Two distinct, not connected paths."*
     *
     * Occupancy is per Point, and a square is emitted as one Point per side a train can arrive by, so
     * the builder groups those copies under a `block` - two trains cannot stand on one square.  That
     * grouping was keyed by the TILE alone, so a train on one arc of a double curve made the other arc
     * read occupied and `isPathClear` refused track that is physically free.  The reduction has keyed
     * these tiles per ROAD; this is the same rule at the occupancy door.
     *
     * MUTATION: key `blockFor` by the tile alone and this finds one name where it wants two.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testEachArcOfADoubleCurveIsItsOwnPieceOfMetal() throws IOException
    {
        session.open(Arrays.asList(doubleCurveCrossroads()));

        TileKey curve = new TileKey("main", 2, 2);

        session.rebuild();

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        java.util.Map<String, Integer> blocks = new java.util.LinkedHashMap<>();

        int copies = 0;

        for (Object o : built.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            // FOUND BY THE BLOCK ITSELF, which is the only thing in the emitted point that names the
            // square: the bare tile before this fix, and the tile plus its road after it.
            String block = point.optString("block", null);

            if (block == null || !block.startsWith(curve.toString())) continue;

            copies++;

            blocks.put(block, blocks.containsKey(block) ? blocks.get(block) + 1 : 1);
        }

        assertTrue(copies > 1,
            "precondition: the double curve must be emitted as several copies carrying a block, or"
            + " there is nothing here to group - found " + copies);

        assertEquals(blocks.size(), 2,
            "the two arcs of a double curve came back as " + blocks.size() + " piece(s) of metal: "
            + blocks + ".  They are two arcs in opposite corners that never touch, so a train on one"
            + " does not stand on the other - grouping them refuses a second train track that is"
            + " physically free");

        for (java.util.Map.Entry<String, Integer> entry : blocks.entrySet())
        {
            assertNotEquals(entry.getKey(), curve.toString(),
                "an arc is still grouped under the bare tile, which is the grouping this is about");
        }
    }

    /**
     * A station on a double-curve sensor is drawn on the curve that carries the contact (MT-712; Adam, 2026-10-10: *"the
     * station gets rendered on the track without the sensor rather than the one with the sensor"*).
     *
     * The square is two curves and one contact, and its art draws the contact on one of them - the E-S curve as drawn,
     * turned with the square.  The badge was put on the square's FIRST road, which is the other one.  Which curve has the
     * contact is read here off the art itself - the white inside the contact's ring at the middle of its curve - so the
     * claim cannot agree with a wrong rule about which road that is.
     *
     * MUTATION: badge the square's first road again, and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testADoubleCurveStationIsDrawnOnTheCurveWithTheContact() throws Exception
    {
        for (int orientation = 0; orientation < 4; orientation++)
        {
            session = new AutonomySession(layout);

            LayoutDiagram page = doubleCurveCrossroads(orientation);

            session.open(Arrays.asList(page));
            session.initialize("contact " + orientation);

            TileKey curve = new TileKey("main", 2, 2);

            session.setStation(curve, true);

            Set<Side> contact = contactRoadOnTheArt(page.getComponent(2, 2), orientation);

            TileAnnotation annotation = session.staticAnnotationFor(curve, false, false);

            assertNotNull(annotation, "precondition: the station at orientation " + orientation + " has no annotation");
            assertNotNull(annotation.getBadge(), "precondition: the station at orientation " + orientation + " has no badge");

            Set<Side> badged = new LinkedHashSet<>(Arrays.asList(annotation.getBadge().getA(), annotation.getBadge().getB()));

            assertEquals(badged, contact, "a station on a double-curve sensor turned " + orientation + " is drawn on the curve"
                + " " + badged + ", and the contact is on " + contact + " - Adam: \"the station gets rendered on the track"
                + " without the sensor rather than the one with the sensor\"");
        }
    }

    /**
     * And the arrows on BOTH its curves lie along their rails (MT-712; Adam, 2026-10-10: *"They are not parallel with the
     * track on one side."*).
     *
     * The track diagram's annotation of a sensor square said it was not curved, so an arrow was aimed from the middle of
     * the badge's road - right on a curve, which has one road, and some thirty degrees off on a double curve's other one.
     * Both roads one-way here, so each has a red arrow at its shut end; each arrow's ink is centered on its own curve's
     * chord.
     *
     * MUTATION: annotate a sensor square as not curved again, and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testADoubleCurvesArrowsLieAlongBothItsCurves() throws Exception
    {
        int size = 60;

        for (int orientation = 0; orientation < 4; orientation++)
        {
            session = new AutonomySession(layout);

            LayoutDiagram page = doubleCurveCrossroads(orientation);

            session.open(Arrays.asList(page));
            session.initialize("arrows " + orientation);

            TileKey curve = new TileKey("main", 2, 2);

            for (java.util.Map.Entry<RouteId, Route> route : session.getGraph().getRoutes(curve).entrySet())
            {
                session.setDirection(curve, route.getKey(), Direction.TOWARD_A);
            }

            TileAnnotation annotation = session.staticAnnotationFor(curve, true, false);

            assertNotNull(annotation, "precondition: the square at orientation " + orientation + " has no annotation");

            BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = shot.createGraphics();

            try
            {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, size, size);

                annotation.paint(g, size, size);
            }
            finally
            {
                g.dispose();
            }

            for (Route road : session.getGraph().getRoutes(curve).values())
            {
                double[] a = midpointOf(road.getA(), size), b = midpointOf(road.getB(), size);

                double sumX = 0, sumY = 0;
                int red = 0;

                for (int x = 0; x < size; x++)
                {
                    for (int y = 0; y < size; y++)
                    {
                        int rgb = shot.getRGB(x, y);

                        if (((rgb >> 16) & 0xFF) < 150 || ((rgb >> 8) & 0xFF) > 90 || (rgb & 0xFF) > 90) continue;

                        if (Math.hypot(x + 0.5 - b[0], y + 0.5 - b[1]) > size * 0.4) continue;

                        sumX += x + 0.5;
                        sumY += y + 0.5;
                        red++;
                    }
                }

                assertTrue(red > 10, "precondition: no red arrow at the shut end " + road.getB() + " of the curve "
                    + road.getA() + "-" + road.getB() + " at orientation " + orientation);

                double cx = sumX / red, cy = sumY / red;

                // distance of the arrow's middle from the line through the curve's two edge midpoints
                double dx = b[0] - a[0], dy = b[1] - a[1];
                double off = Math.abs(dy * (cx - a[0]) - dx * (cy - a[1])) / Math.hypot(dx, dy);

                assertTrue(off <= 3, "the red arrow at " + road.getB() + " on the double curve's " + road.getA() + "-"
                    + road.getB() + " curve, turned " + orientation + ", is centred " + Math.round(off) + " pixels off its"
                    + " rail - Adam: \"They are not parallel with the track on one side.\"");
            }
        }
    }

    /**
     * Which of a double-curve sensor's two roads its art draws the contact on: the one whose middle - the middle of the
     * chord between its two sides - is the white inside the contact's ring.
     */
    private static Set<Side> contactRoadOnTheArt(org.traincontrol.base.LayoutDiagramComponent square, int orientation)
        throws IOException
    {
        int size = 60;

        BufferedImage art = org.traincontrol.base.LayoutDiagramComponent.toBufferedImage(square.getImage(size, true));

        Set<Side> found = null;

        for (Route road : org.traincontrol.automationui.TilePorts.ports(componentType.FEEDBACK_DOUBLE_CURVE, orientation, 0))
        {
            double[] a = midpointOf(road.getA(), size), b = midpointOf(road.getB(), size);

            int rgb = art.getRGB((int) ((a[0] + b[0]) / 2), (int) ((a[1] + b[1]) / 2));

            boolean white = ((rgb >> 16) & 0xFF) > 230 && ((rgb >> 8) & 0xFF) > 230 && (rgb & 0xFF) > 230
                && ((rgb >>> 24) & 0xFF) > 200;

            if (!white) continue;

            assertNull(found, "precondition: both curves of the double-curve art turned " + orientation + " are white in"
                + " the middle");

            found = new LinkedHashSet<>(Arrays.asList(road.getA(), road.getB()));
        }

        assertNotNull(found, "precondition: neither curve of the double-curve art turned " + orientation + " has the"
            + " contact's white in its middle");

        return found;
    }

    /** The middle of a side of a square this size, x and y. */
    private static double[] midpointOf(Side side, int size)
    {
        switch (side)
        {
            case N: return new double[] {size / 2.0, 0};
            case E: return new double[] {size, size / 2.0};
            case S: return new double[] {size / 2.0, size};
            default: return new double[] {0, size / 2.0};
        }
    }

    /**
     * A double-curve sensor with track on both of its arcs: north, west, east and south all reachable.
     *
     * @return the page
     * @throws IOException from the diagram
     */
    private LayoutDiagram doubleCurveCrossroads() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 6, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 2, 1, 1, 0, 21, 21, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 22, 22, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK_DOUBLE_CURVE, 2, 2, 0, 0, 25, 25,
            accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 2, 0, 0, 23, 23, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 3, 1, 0, 24, 24, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * The same, the double curve turned to this orientation - every orientation has track on all four sides.
     *
     * @param orientation the double curve's orientation
     * @return the page
     * @throws IOException from the diagram
     */
    private LayoutDiagram doubleCurveCrossroads(int orientation) throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 6, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 2, 1, 1, 0, 21, 21, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 22, 22, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK_DOUBLE_CURVE, 2, 2, orientation, 0, 25, 25,
            accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 2, 0, 0, 23, 23, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 3, 1, 0, 24, 24, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * A restricted piece of TRACK gets its arrow on the ordinary diagram, not only a sensor (FR-037).
     *
     * Adam: "the arrows are not showing up for me when I toggle the option."  They were - on the few
     * squares that happen to be Points, which on a real railway is not where a restriction lives. A
     * one-way run is a property of track: straights, curves, the squares either side of a switch, and
     * almost none of those carry a sensor.
     *
     * **This is the test that was missing rather than the one that failed.** The four I wrote for
     * FR-037 checked the preference, the guards, the menu grouping and the refresh call. Every one was
     * true, and not one of them could notice that the arrows never reached any track. Asking "does a
     * restricted straight get a mark" would have failed the moment it was written.
     *
     * The option is set through the preference the code reads, and put back afterwards, so this cannot
     * leave the running application drawing arrows nobody asked for.
     *
     * MUTATION: computing the marks after the Point test - which is what shipped - fails this.
     */
    @Test
    public void testARestrictedStraightGetsAnArrowOnTheDiagram() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("FR037", null);
        session.getStore().setActiveConfiguration("FR037");

        // A STRAIGHT, deliberately: the feedbacks are at 1, 3 and 5, so this square is track and
        // nothing else - exactly the kind the option appeared not to work on.
        TileKey straight = new TileKey("main", 2, 1);
        TileKey west = new TileKey("main", 1, 1);
        TileKey east = new TileKey("main", 5, 1);

        assertFalse(session.getReducer().getPoints().containsKey(straight),
            "precondition: the square being tested is a Point, so it would have drawn an arrow even "
            + "with the defect this test is about");

        assertTrue(session.setOneWayRun(west, east) > 0,
            "precondition: the run could not be made one-way, so there is no restriction to draw");

        session.rebuild();

        // WHETHER IT WAS STORED AT ALL, not just what it reads as.
        //
        // This captured `getBoolean(key, false)` and put that back unconditionally - written when the
        // default WAS false. The default became true the next day, so on any machine where nobody had
        // ever set the key - which is every machine, including Adam's - the capture read false and the
        // finally wrote an explicit false into his live preferences. Running the suite once switched
        // off the feature he had just asked to be on by default, with nothing on screen connecting
        // the two.
        //
        // Found by a reviewer. A test may read the user's settings; it may not decide them.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        boolean had = prefs.get(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null) != null;

        boolean was = prefs.getBoolean(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

        try
        {
            org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, false);

            org.traincontrol.automationui.TileAnnotation off = session.staticAnnotationFor(straight);

            assertTrue(off == null || off.isBlank(),
                "a plain piece of track is described on the diagram with the option OFF, so the "
                + "default has stopped being a bare diagram");

            org.traincontrol.gui.TrainControlUI.getPrefs().putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

            org.traincontrol.automationui.TileAnnotation on = session.staticAnnotationFor(straight);

            assertNotNull(on,
                "a one-way straight is described as nothing at all with the option ON - which is what "
                + "Adam saw: the arrows were added after the test that returns null for anything that "
                + "is not a sensor, and a restriction is a fact about track");

            assertFalse(on.isBlank(),
                "the annotation for a one-way straight carries nothing to draw, so the square stays "
                + "as bare as it was with the option off");
        }
        finally
        {
            // Put back as it was, INCLUDING having been unset.
            if (had)
            {
                prefs.putBoolean(
                    org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, was);
            }
            else
            {
                prefs.remove(org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            }
        }
    }

    /**
     * The station ingress arrows follow the same switch as the travel restrictions (Adam, 2026-08-28).
     *
     * "Couple the station ingress arrows to this setting in the track viewer, but be careful not to
     * disturb their behavior in the editor."
     *
     * A chevron saying which way into a platform is shut is the same kind of statement as a one-way
     * arrow - both say where a train may not go, both come from this setup - so a switch that turned
     * off one and left the other would be a setting that half works.
     *
     * The preference is set through the key the code reads and put back afterwards, because these are
     * the real preferences of whoever runs the suite.
     *
     * MUTATION: drawing the arrivals unconditionally - which is what the viewer did before - fails the
     * first half.
     */
    @Test
    public void testTheIngressArrowsFollowTheSettingInTheViewer() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Ingress", null);
        session.getStore().setActiveConfiguration("Ingress");

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Platform");

        session.rebuild();

        java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
            session.arrivalSides(station);

        assertTrue(ways.size() > 1,
            "precondition: the station has fewer than two ways in, so barring one leaves nothing to "
            + "draw a chevron about");

        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<>(Arrays.asList(ways.get(0))));

        session.rebuild();

        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        boolean had = prefs.get(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null) != null;
        boolean was = prefs.getBoolean(
            org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

        try
        {
            prefs.putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, false);

            org.traincontrol.automationui.TileAnnotation off =
                session.staticAnnotationFor(station);

            assertTrue(off == null || off.getArrivals().isEmpty(),
                "the barred way into a platform is still drawn on the diagram with travel "
                + "restrictions switched OFF, so the setting turns off the arrows and leaves the "
                + "chevrons - which is the half-working state the coupling was asked for to stop");

            prefs.putBoolean(
                org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

            org.traincontrol.automationui.TileAnnotation on =
                session.staticAnnotationFor(station);

            assertNotNull(on, "a station with a barred way in is described as nothing at all");

            assertFalse(on.getArrivals().isEmpty(),
                "the barred way into a platform is not drawn with travel restrictions switched ON, "
                + "so the coupling took the chevrons away instead of following the switch");
        }
        finally
        {
            if (had)
            {
                prefs.putBoolean(
                    org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, was);
            }
            else
            {
                prefs.remove(org.traincontrol.gui.TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            }
        }
    }

    /**
     * A link is never offered a direction, and ordinary track always is.
     *
     * The guard this pins is the only thing standing between a user and a setting that silently does
     * nothing.  A link's route is a stub - the same side twice - so "toward A" and "toward B" name the
     * same place, and the traversal would allow both whichever was chosen.  See the note on
     * TileGraph.PORTAL_ROUTE.
     */
    @Test
    public void testALinkIsNotOfferedADirection() throws Exception
    {
        LayoutDiagram page = throughStationPage();

        page.addComponent(componentType.LINK, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        session.open(Arrays.asList(page));
        session.rebuild();

        assertFalse(session.canCarryDirection(new TileKey("main", 6, 1)),
            "a link was offered the four direction answers, none of which can mean anything on it");

        assertTrue(session.canCarryDirection(new TileKey("main", 2, 1)),
            "ordinary track must still be able to carry a direction, or the rule has gone too far");
    }

    /**
     * The autonomy editor's Show Trains switch is on until it is turned off, and says what it is set to (FR-119) - what
     * its diagram's squares ask as they paint.
     *
     * MUTATION: answer the squares anything but the switch's state, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testTheEditorHasAShowTrainsSwitch() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel panel = anEditorOverATwoEndedStation();

        final java.lang.reflect.Method box = org.traincontrol.gui.AutonomyEditorPanel.class.getMethod("getShowTrains");
        final java.lang.reflect.Method showing = org.traincontrol.gui.AutonomyEditorPanel.class.getMethod("isShowingTrains");

        final javax.swing.JCheckBox[] trains = new javax.swing.JCheckBox[1];
        final boolean[] seen = new boolean[3];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                trains[0] = (javax.swing.JCheckBox) box.invoke(panel);

                boolean was = trains[0].isSelected();

                trains[0].setSelected(true);
                seen[0] = (Boolean) showing.invoke(panel);

                trains[0].setSelected(false);
                seen[1] = (Boolean) showing.invoke(panel);

                trains[0].setSelected(was);
                seen[2] = was;
            }
            catch (ReflectiveOperationException failed)
            {
                throw new RuntimeException(failed);
            }
        });

        assertTrue(seen[0], "the editor's Show Trains switch, ticked, says not to show the trains (FR-119)");
        assertFalse(seen[1], "the editor's Show Trains switch, unticked, says to show the trains (FR-119)");
        assertEquals(trains[0].getText(), org.traincontrol.util.I18n.t("autosetup.ui.btnShowTrains"), "the switch is not"
            + " the one called Show Trains");
    }

    /**
     * A sensor with no name is named as it becomes a station (FR-120; Adam, 2026-10-10: *"when an unnamed sensor is
     * converted to a station, pop up a box to ask for its name.  if none is provided, changes are cancelled.  names are
     * remembered so future changes are applied.  we apply a name duplication check here."*).  Through the editor's own
     * answer to the station menu, `setUsage`, with its question answered here: Cancel, and a blank name, leave the square a
     * sensor and unnamed; a name another square has - in any case - is refused and asked for again, with the name typed
     * and why; a free name makes it a station of that name; and a square with a name already is not asked.
     *
     * MUTATION: make an unnamed sensor a station without asking, or take a name another square has, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testAnUnnamedSensorIsNamedAsItBecomesAStation() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel panel = anEditorOverATwoEndedStation();

        final TileKey unnamed = new TileKey("main", 5, 1);
        final TileKey named = new TileKey("main", 1, 1);

        session.setPointName(named, "North Yard");

        final List<String[]> asked = new java.util.ArrayList<>();
        final java.util.LinkedList<String> answers = new java.util.LinkedList<>();

        java.lang.reflect.Field question = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("askForAName");

        question.setAccessible(true);

        question.set(panel, (java.util.function.BiFunction<String, String, String>) (message, typed) ->
        {
            asked.add(new String[] {message, typed});

            return answers.isEmpty() ? null : answers.removeFirst();
        });

        // CANCEL: nothing changes
        answers.add(null);
        answerTheStationMenu(panel, unnamed, true);

        assertEquals(asked.size(), 1, "an unnamed sensor was made a station without being asked its name - Adam: \"when"
            + " an unnamed sensor is converted to a station, pop up a box to ask for its name\"");
        assertFalse(session.getStore().isStation(unnamed), "Cancel made the unnamed sensor a station - Adam: \"if none is"
            + " provided, changes are cancelled\"");

        // BLANK: nothing changes
        answers.add("   ");
        answerTheStationMenu(panel, unnamed, true);

        assertFalse(session.getStore().isStation(unnamed), "a blank name made the unnamed sensor a station");
        assertNull(session.getStore().getPointName(unnamed), "a blank name named the sensor");

        // ANOTHER SQUARE'S NAME, in another case: refused, and asked again with what was typed
        asked.clear();
        answers.add(" north yard ");
        answers.add("Far Yard");
        answerTheStationMenu(panel, unnamed, true);

        assertEquals(asked.size(), 2, "a name another square has was taken, not refused and asked for again - Adam: \"we"
            + " apply a name duplication check here\"");
        assertEquals(asked.get(1)[1], "north yard", "asked again, the name typed was not kept for correcting");
        assertTrue(asked.get(1)[0].contains(org.traincontrol.util.I18n.f("autosetup.ui.errorPointNameTaken", "north yard")),
            "asked again without saying the name is taken: " + asked.get(1)[0]);

        assertTrue(session.getStore().isStation(unnamed), "a free name did not make the sensor a station");
        assertEquals(session.getStore().getPointName(unnamed), "Far Yard", "the station is not called what was typed");

        // NAMED ALREADY: not asked
        asked.clear();
        answerTheStationMenu(panel, named, true);

        assertTrue(asked.isEmpty(), "a sensor with a name was asked for one as it became a station - Adam: \"names are"
            + " remembered so future changes are applied\"");
        assertTrue(session.getStore().isStation(named), "a named sensor was not made a station");
    }

    /** The editor over `pageWithATwoEndedStation`, its three sensors plain points. */
    private org.traincontrol.gui.AutonomyEditorPanel anEditorOverATwoEndedStation() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));
        session.initialize("Default");

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { }));

        return panel[0];
    }

    /**
     * Show Trains off hides the train mark the editor's own annotation draws where the setup puts a train, as well as the
     * trains' icons and tails: under the icon while the trains show, it appeared when they were switched off.
     *
     * MUTATION: mark the setup's train whatever the switch says, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testShowTrainsOffHidesTheSetupsTrainMarkToo() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel panel = anEditorOverATwoEndedStation();

        final TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Platform");
        session.placeLocomotive(station, "Probe Train");

        assertNotNull(session.getLocomotiveNameAt(station), "precondition: no train placed on the station");

        final java.lang.reflect.Field occupied = TileAnnotation.class.getDeclaredField("occupied");

        occupied.setAccessible(true);

        final boolean[] marked = new boolean[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                javax.swing.JCheckBox trains = panel.getShowTrains();
                boolean was = trains.isSelected();

                trains.setSelected(true);
                marked[0] = (Boolean) occupied.get(panel.annotationFor(station));

                trains.setSelected(false);
                marked[1] = (Boolean) occupied.get(panel.annotationFor(station));

                trains.setSelected(was);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new RuntimeException(failed);
            }
        });

        assertTrue(marked[0], "precondition: the editor does not mark the setup's train with Show Trains on");
        assertFalse(marked[1], "with Show Trains off the editor still marks where the setup puts a train (RSA63-C3)");
    }

    /**
     * Show Trains redraws every square in its window, not only the squares whose annotation it changes.  The
     * trains' icons are drawn from the railway, by squares that ask the switch as they paint; where the railway has a train
     * and the setup places none - the setup unable to build, the last valid railway kept - the switch changes no
     * annotation, and redrawing only the changed squares left that train drawn with the trains switched off.
     *
     * Here the panel shares its window with a square it knows nothing about, standing for such a square on the diagram:
     * each click of the switch, off and back on, asks for that square to be redrawn.
     *
     * MUTATION: drop the switch's redraw of its window, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testShowTrainsRedrawsEverySquareInItsWindow() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel panel = anEditorOverATwoEndedStation();

        final boolean[] redrawn = new boolean[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            final javax.swing.JRootPane window = new javax.swing.JRootPane();
            final javax.swing.JPanel square = new javax.swing.JPanel();

            window.setSize(800, 600);
            window.getContentPane().setLayout(null);
            window.getContentPane().add(panel);
            window.getContentPane().add(square);
            window.doLayout();

            panel.setBounds(0, 0, 400, 600);
            square.setBounds(500, 100, 30, 30);

            final java.awt.Rectangle where = javax.swing.SwingUtilities.convertRectangle(square,
                new java.awt.Rectangle(0, 0, 30, 30), window);

            final List<java.awt.Rectangle> dirty = new ArrayList<>();

            javax.swing.RepaintManager was = javax.swing.RepaintManager.currentManager(window);

            javax.swing.RepaintManager.setCurrentManager(new javax.swing.RepaintManager()
            {
                @Override
                public void addDirtyRegion(javax.swing.JComponent c, int x, int y, int w, int h)
                {
                    dirty.add(javax.swing.SwingUtilities.convertRectangle(c, new java.awt.Rectangle(x, y, w, h), window));

                    super.addDirtyRegion(c, x, y, w, h);
                }
            });

            javax.swing.JCheckBox trains = panel.getShowTrains();
            boolean before = trains.isSelected();

            try
            {
                trains.setSelected(true);

                for (int click = 0; click < 2; click++)
                {
                    dirty.clear();

                    trains.doClick();

                    for (java.awt.Rectangle r : dirty) redrawn[click] |= r.contains(where);
                }
            }
            finally
            {
                javax.swing.RepaintManager.setCurrentManager(was);

                if (trains.isSelected() != before) trains.doClick();
            }
        });

        assertTrue(redrawn[0], "switching the trains off did not redraw a square in the editor's window whose annotation"
            + " it does not change - a train the railway has there stays drawn (RSA64-C1)");
        assertTrue(redrawn[1], "switching the trains back on did not redraw a square in the editor's window whose"
            + " annotation it does not change - a train the railway has there is not drawn back (RSA64-C1)");
    }

    /**
     * A route button on a running line takes direction clicks like the track it sits on (OB-332; Adam, 2026-10-11:
     * *"sometimes a direction arrow is placed on a route tile, but those are greyed out in the autonomy editor and can't be clicked/changed.  We need the direction arrows to be editable and clickable."*).  The graph runs the line through the button, so a direction is stored on it and its arrow
     * drawn there, and the editor grayed every button and refused its click - six such arrows on his Oles kreds sandbox,
     * route 41 among them.  Not grayed, a click changes its direction; a button beside the line, with no track through
     * it, is still grayed and takes no click.
     *
     * MUTATION: gray every route button again, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testARouteButtonOnALineTakesADirectionClick() throws Exception
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(), "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, "file:///" + pageFile.getAbsolutePath().replace('\\', '/'),
            null);

        // a sensor, track, a sensor, a ROUTE BUTTON on the line, a sensor - and another button beside the line, under it
        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.ROUTE, 4, 1, 0, 0, 41, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.ROUTE, 4, 3, 0, 0, 42, 0, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Default");

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, "main", () -> { }));

        final TileKey button = new TileKey("main", 4, 1), beside = new TileKey("main", 4, 3);

        assertEquals(session.getRoutes(button).size(), 1, "precondition: the line does not run through the route button");
        assertTrue(session.getRoutes(beside).isEmpty(), "precondition: track runs through the button beside the line");

        final RouteId road = session.getRoutes(button).keySet().iterator().next();

        final Direction before = session.getGraph().getDirection(button, road);

        final boolean[] seen = new boolean[4];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            TileAnnotation on = panel[0].annotationFor(button), off = panel[0].annotationFor(beside);

            seen[0] = on == null || !on.isIgnored();
            seen[1] = off != null && off.isIgnored();
            seen[2] = panel[0].clickChangesDirections(button);
            seen[3] = panel[0].clickChangesDirections(beside);

            panel[0].tileClicked(button, page.getComponent(4, 1), false);
        });

        assertTrue(seen[0], "a route button on the line is greyed in the editor - Adam: " + "\"those are greyed out in the"
            + " autonomy editor and can't be clicked/changed\"");
        assertTrue(seen[2], "a route button on the line offers no direction click - Adam: \"We need the direction arrows"
            + " to be editable and clickable.\"");
        assertNotEquals(session.getGraph().getDirection(button, road), before, "a click on a route button on the line"
            + " left its direction " + before);

        assertTrue(seen[1], "a route button beside the line, with no track through it, is not greyed");
        assertFalse(seen[3], "a route button beside the line, with no track through it, offers a direction click");
    }

    /**
     * A square out of service offers no direction click in the editor: it draws no arrows (round 127), and its
     * click went on cycling the hidden setting under the hand pointer, the square looking the same afterwards.
     *
     * MUTATION: let the click change a square out of service again, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testAClickChangesNoDirectionOnASquareOutOfService() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel panel = anEditorOverATwoEndedStation();

        final TileKey square = new TileKey("main", 3, 1);

        assertTrue(panel.clickChangesDirections(square), "precondition: a click does not change the square's directions"
            + " while it is in service");

        session.setPointProperty(square, "active", Boolean.FALSE);

        final RouteId road = session.getRoutes(square).keySet().iterator().next();

        final Direction before = session.getGraph().getDirection(square, road);

        assertFalse(panel.clickChangesDirections(square), "the editor offers a direction click on a square out of service,"
            + " whose arrows it does not draw (RSA63-C5)");

        final java.lang.reflect.Method cycle = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("cycle",
            TileKey.class);

        cycle.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                cycle.invoke(panel, square);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new RuntimeException(failed);
            }
        });

        assertEquals(session.getGraph().getDirection(square, road), before, "a click changed the hidden directions of a"
            + " square out of service (RSA63-C5)");
    }
}
