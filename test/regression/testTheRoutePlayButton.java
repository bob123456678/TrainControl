package regression;

import java.awt.Rectangle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * The play button on a route cell is drawn where it can be pressed (FR-043).
 *
 * Adam: "update the route table view so that there is a green play button (execute) icon on the right
 * side of each cell... wire it so that no confirmation is needed prior to execution when this is
 * pressed... make all other left or clicks on the table cells open the right-click menu."
 *
 * **Most of that cannot be asserted from here**, and saying so is more useful than a test that pretends
 * otherwise: whether the triangle reads as a play button, whether it is the right size beside the text,
 * and whether the spinner turns are all questions for MT-213. What CAN be pinned is the part that has
 * no appearance at all and would fail silently - a control drawn in one place and pressed in another.
 *
 * A table cell is a stamp rather than a component, so there is no JButton whose bounds answer both
 * questions. The renderer works out where to paint from the cell's rectangle, and the click works out
 * what it hit from the same rectangle; if those two ever disagree the button is simply dead, and it
 * looks perfectly normal while being dead. So they go through one method, and this is the test that
 * says so.
 */
public class testTheRoutePlayButton
{
    /**
     * The button sits inside its cell, against the right edge, and is about the height of the text.
     *
     * The numbers are not asserted exactly - "about the same height as the text" is a judgement, and a
     * test that pins 13 pixels would fail the next time somebody nudges it for good reason. What is
     * asserted is what would make it unusable: falling outside the cell, drifting away from the right
     * edge, or growing to fill the row.
     */
    @Test
    public void testTheButtonIsInsideTheCellAndAgainstItsRightEdge()
    {
        // A cell the size the route table actually uses: three columns across the route panel, and a
        // row height Swing gives a 14pt label.
        Rectangle cell = new Rectangle(0, 0, 180, 22);

        Rectangle button = TrainControlUI.routePlayButtonBox(cell);

        assertTrue(cell.contains(button),
            "the play button is drawn outside its own cell (" + button + " in " + cell + "), so part "
            + "of it belongs to the neighbouring route - and a click there would run the wrong one");

        assertTrue(button.x > cell.x + cell.width / 2,
            "the play button is not on the RIGHT side of the cell, which is where Adam asked for it "
            + "and where the route's name is not: " + button + " in " + cell);

        // BIG ENOUGH TO HIT, which is the whole of MT-217.
        //
        // The first version made the pressable box the same thirteen pixels square as the glyph, eight
        // in from the right edge of a thirty-pixel row - about one and a half percent of the cell,
        // against its border. The geometry was correct and Adam still could not press it. So what is
        // asserted here is not where the target is but how much of it there is.
        assertTrue(button.height >= cell.height - 2,
            "the pressable strip is " + button.height + " pixels tall in a " + cell.height
            + "-pixel row, so there is a band above or below the button where a click misses - which "
            + "is what \"play buttons not clickable\" was");

        assertTrue(button.width >= 20,
            "the pressable strip is only " + button.width + " pixels wide, which is back to being a "
            + "target that has to be aimed at precisely (MT-217)");

        assertTrue(button.width * button.height >= 500,
            "the pressable area is " + (button.width * button.height) + " square pixels; the version "
            + "Adam could not press was 169");
    }

    /**
     * The glyph is inside the strip that presses it, and smaller than it.
     *
     * Two rectangles on purpose: the mark is the size Adam asked for - "about the same height as the
     * text" - and the target is the size a target has to be. Collapsing them into one number is
     * exactly what made the button unhittable, so this is the pair being kept apart.
     */
    @Test
    public void testTheGlyphSitsInsideTheStrip()
    {
        Rectangle cell = new Rectangle(0, 0, 180, 30);

        Rectangle strip = TrainControlUI.routePlayButtonBox(cell);
        Rectangle glyph = TrainControlUI.routePlayGlyphBox(strip);

        assertTrue(strip.contains(glyph),
            "the play triangle is drawn outside the strip that presses it (" + glyph + " in " + strip
            + "), so there is a place you can see the button and not press it");

        assertTrue(glyph.height < strip.height,
            "the triangle fills the strip's whole height, so it is no longer 'about the same height "
            + "as the text' - which is the size Adam asked for");

        int leftGap = glyph.x - strip.x;
        int rightGap = (strip.x + strip.width) - (glyph.x + glyph.width);

        assertTrue(Math.abs(leftGap - rightGap) <= 1,
            "the triangle is not centred across its strip - " + leftGap + " left, " + rightGap
            + " right");
    }

    /**
     * It follows the cell rather than being drawn at a fixed place.
     *
     * The route table is three columns of whatever width the panel happens to be, so a button worked
     * out from anything but the cell it belongs to would be right in one column and wrong in the other
     * two. This is the specific way that fails, and it fails invisibly.
     */
    @Test
    public void testTheButtonMovesWithItsCell()
    {
        Rectangle first = TrainControlUI.routePlayButtonBox(new Rectangle(0, 0, 180, 30));
        Rectangle second = TrainControlUI.routePlayButtonBox(new Rectangle(180, 0, 180, 30));
        Rectangle lower = TrainControlUI.routePlayButtonBox(new Rectangle(0, 30, 180, 30));
        Rectangle wider = TrainControlUI.routePlayButtonBox(new Rectangle(0, 0, 300, 30));

        assertEquals(second.x - first.x, 180,
            "the button in the second column is not 180 pixels right of the one in the first, so it is "
            + "not following its cell across the table");

        assertEquals(lower.y - first.y, 30,
            "the button in the second row is not a row lower, so it is not following its cell down");

        assertTrue(wider.x > first.x,
            "a wider cell did not move the button right, so it is measured from the left edge rather "
            + "than the right - the route panel is resizable, and the button would drift into the "
            + "middle of the name as it widens");
    }

    /**
     * The renderer and the click ask the same method where the button is.
     *
     * The two tests above prove the RULE. This proves it is the only rule in play: a second copy of the
     * arithmetic in either place would pass everything above and still put the button somewhere the
     * click cannot reach.
     *
     * MUTATION: inline the rectangle into either `isOverTheRoutePlayButton` or the renderer's
     * `paintComponent` and this fails.
     */
    @Test
    public void testBothSidesAskTheSameMethod() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), StandardCharsets.UTF_8);

        String code = ui.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");

        int declared = countOf(code, "static java.awt.Rectangle routePlayButtonBox(");
        int asked = countOf(code, "routePlayButtonBox(") - declared;

        assertEquals(declared, 1,
            "routePlayButtonBox is declared " + declared + " times - there should be exactly one place "
            + "that knows where the button goes");

        assertEquals(asked, 2,
            "routePlayButtonBox is called " + asked + " times; it should be exactly two - the renderer "
            + "that draws the glyph inside it, and the click that has to hit it. Fewer means one of "
            + "them has grown its own copy of the arithmetic, and a button drawn where it cannot be "
            + "pressed looks entirely normal");

        // The third reader of that geometry is the border, and it takes the WIDTH rather than the
        // rectangle - so it cannot go through the same method, and this is what holds it to the same
        // number instead. Without it the strip and the space reserved from the text could drift, and
        // a long route name would creep back under the icon, which is OB-146 returning.
        assertTrue(code.contains("createEmptyBorder(0, 0, 0, ROUTE_PLAY_STRIP)"),
            "the renderer no longer reserves ROUTE_PLAY_STRIP from the text. Either the reservation "
            + "has gone - and route names will run under the play icon again (OB-146) - or it has "
            + "been given a number of its own, which is the same drift by a slower route");
    }

    /**
     * The hand cursor asks the same question the click asks (OB-147).
     *
     * Adam: "the play button in routes should show a finger/pointer icon when hovered to indicate
     * clickability."
     *
     * The cursor is how a surface says "this part is a control", so it has to be true of exactly the
     * pixels that ARE one. Working it out separately is how a hand appears over something that does
     * nothing - the affordance and the guard answering differently, which OB-057 and OB-090 were both
     * about. So the hover asks isOverTheRoutePlayButton, the same method the click asks.
     *
     * MUTATION: give the hover its own test - a bare x-coordinate comparison, say - and this fails.
     */
    @Test
    public void testTheHoverAsksTheSameQuestionAsTheClick() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), StandardCharsets.UTF_8);

        int from = ui.indexOf("private void RouteListMouseMoved(");

        assertTrue(from >= 0, "RouteListMouseMoved has gone - this test is reading nothing");

        String body = ui.substring(from, ui.indexOf("//GEN-LAST:event_RouteListMouseMoved", from));

        assertTrue(body.contains("routesExecuting.contains("),
            "the hover no longer asks whether the route is already running, so a greyed button still "
            + "shows a hand - the interface offering an action it will refuse (MT-217)");

        assertTrue(body.contains("isOverTheRoutePlayButton(evt)"),
            "the hover no longer asks isOverTheRoutePlayButton, so the hand cursor and the click have "
            + "separate opinions about where the button is - and a hand over pixels that do not run "
            + "the route is the interface promising something it will not do");

        assertTrue(body.contains("HAND_CURSOR"),
            "the hover no longer sets a hand cursor, so nothing tells the user the strip is a control "
            + "(OB-147)");

        int exited = ui.indexOf("private void RouteListMouseExited(");

        assertTrue(exited >= 0, "RouteListMouseExited has gone");

        String leaving = ui.substring(exited,
            ui.indexOf("//GEN-LAST:event_RouteListMouseExited", exited));

        assertTrue(leaving.contains("setCursor"),
            "leaving the route table no longer restores the cursor. A cursor belongs to the component "
            + "rather than to a pixel, so the hand would be left behind and whatever the pointer "
            + "crossed next would claim to be clickable");
    }

    /**
     * A running route's button is greyed, refuses the click, and shows no hand (MT-217).
     *
     * Adam: "there is no animation / change in state while it is running.  It should gray out and then
     * become reenabled once the route finishes."
     *
     * Three surfaces have to agree about one fact, and they are the three that made OB-057 and OB-090:
     * what the button LOOKS like, what the click DOES, and what the cursor PROMISES. A greyed button
     * that still fires is a lie; a hand over it is the same lie in a different form; and either would
     * also let the same route be started twice, which is the thing the greying exists to say cannot
     * happen.
     *
     * MUTATION: delete any one of the three `routesExecuting.contains(...)` guards and this fails.
     */
    @Test
    public void testARunningRouteIsShownAndTreatedAsDisabled() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), StandardCharsets.UTF_8);

        String code = withoutComments(ui);

        assertEquals(countOf(code, "routesExecuting.contains("), 4,
            "the running-route check appears " + countOf(code, "routesExecuting.contains(")
            + " times; it should be exactly four - the cell wash, the greyed triangle, the click "
            + "that refuses it, and the cursor that stops promising it. Fewer means one of those "
            + "surfaces disagrees with the rest about whether the button is available");

        assertFalse(code.contains("routeSpinnerTimer"),
            "the ANIMATION timer is back. Adam asked for a state rather than an animation - a disabled "
            + "button needs two repaints in its life, not sixteen a second, and a state does not have "
            + "to be caught happening to be seen. Note this is not the one-shot Timer in routeStarted, "
            + "which holds the state for a fixed second and is checked just below");

        assertTrue(code.contains("ROUTE_MINIMUM_VISIBLE_MS"),
            "there is no floor under how long the button stays grey. That floor is the whole fix: a "
            + "route can finish faster than the grey can be seen, so clearing purely on the route's "
            + "own ending puts the button back before anything was noticed - which is what Adam "
            + "reported three times");

        assertTrue(code.contains("routeFinished(route);"),
            "the route's own ending no longer puts the button back, so the grey is on a fixed timer "
            + "again - which lies the other way, telling the operator a slow route has finished when "
            + "pressing it again would still be refused");

        assertFalse(code.contains("drawArc("),
            "the spinner arc is back; the button greys now instead");
    }

    /**
     * Source with its comments taken out.
     *
     * Every scan in this class needs it, and for the reason three findings this round were about: the
     * prose in this codebase describes the code beside it closely enough to be mistaken for it, so a
     * comment saying what something used to do reads exactly like it still doing it.
     */
    private static String withoutComments(String source)
    {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static int countOf(String text, String needle)
    {
        int count = 0;

        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1))
        {
            count++;
        }

        return count;
    }
}
