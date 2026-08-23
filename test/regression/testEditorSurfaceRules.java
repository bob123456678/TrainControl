package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Three rules about the editor's surface, each of which was a defect Adam reported and each of which
 * has the same shape: a decision written down in more than one place, or not written down at all.
 *
 * - **OB-039** - the facing menu existed twice, so a redraw added to one copy left the other reporting
 *   the bug.
 * - **OB-040** - the de-clutter while a signal is being picked has to reach the greying, the arrows and
 *   the lengths, and has to be turned off again down every one of the window's four exits.
 * - **OB-028** - what a tile's border looks like at rest differs between the palette, the layout editor
 *   and autonomy mode, which is three answers to one question.
 *
 * Source-level where the fault is textual, following `testNoSelfRecursiveWrappers`; a real call where
 * the rule could be lifted out into a function, which is what OB-028's was.
 *
 * Which way a train is pointing is recorded in one place, and redrawing follows it.
 *
 * OB-039: "when changing the orientation of a loc from the track diagram, the direction on the label is
 * not updated." The facing was written to the setup and nothing repainted, so the caption went on
 * stating the opposite of what had just been chosen.
 *
 * The reason it is worth a test rather than just a fix is the shape of it. The facing menu existed
 * TWICE in `AutonomyEditorPanel` - once for the deep menu inside Autonomy Setup and once handed to the
 * track diagram - and the two copies had already drifted. A redraw added to the copy in front of
 * whoever is reading fixes the surface being looked at and leaves the other one reporting the same bug
 * later, which is how this file's defects have tended to arrive.
 *
 * So this asserts the thing that stops it recurring: the setup is told about a facing from exactly ONE
 * place, and that place redraws.
 *
 * Source-level on purpose, following `testNoSelfRecursiveWrappers`. The fault is textual - a second
 * copy of a menu, or a missing call after a mutation - and it is invisible to a test that drives the
 * model, because the model is the half that was always working.
 *
 * @author Adam
 */
public class testEditorSurfaceRules
{
    private static final File PANEL =
        new File("src/org/traincontrol/gui/AutonomyEditorPanel.java");

    /**
     * One writer, and it repaints.
     *
     * The window is generous - a dozen lines - because the call sits inside a lambda with a comment
     * explaining itself. It is not generous enough to reach the next menu item.
     */
    @Test
    public void testTheFacingIsWrittenFromOnePlaceAndThatPlaceRedraws() throws Exception
    {
        if (!PANEL.isFile()) return;

        List<String> lines = Files.readAllLines(PANEL.toPath(), StandardCharsets.UTF_8);

        int writes = 0;
        boolean redrawn = false;

        for (int i = 0; i < lines.size(); i++)
        {
            if (!lines.get(i).contains("session.setFacing(")) continue;

            writes++;

            for (int j = i; j < Math.min(lines.size(), i + 12); j++)
            {
                if (lines.get(j).contains("placementChanged()")) redrawn = true;
            }
        }

        assertEquals(writes, 1,
            "the facing MENU writes to the setup from " + writes + " places in AutonomyEditorPanel. Two "
            + "copies of this menu is how OB-039 survived being fixed: the redraw goes on the copy "
            + "somebody is looking at, and the other one keeps the bug. "
            + "Scoped to this file on purpose. LayoutRightclickAutonomyMenu also writes a facing, as "
            + "part of MOVING a locomotive to a station, and it saves and repaints on its own - a "
            + "different operation that happens to set the same field, not a second copy of this menu");

        assertTrue(redrawn,
            "the facing is recorded but nothing redraws (OB-039). The caption carries the arrow saying "
            + "which way the train points, and it is drawn from the running layout - so a facing "
            + "written to the setup and not followed by placementChanged() leaves the diagram stating "
            + "the opposite of what was just chosen");
    }

    /**
     * The submenu itself is built once.
     *
     * `buildFacingMenu` is public so the track diagram can carry the same menu the editor does. A
     * second `menuFacingGroup` heading built by hand is that method being reimplemented next to it.
     */
    @Test
    public void testTheFacingSubmenuIsBuiltOnce() throws Exception
    {
        if (!PANEL.isFile()) return;

        String source = new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8);

        int built = 0;
        int at = source.indexOf("menuFacingGroup");

        while (at >= 0)
        {
            built++;
            at = source.indexOf("menuFacingGroup", at + 1);
        }

        assertEquals(built, 1,
            "the \"{loc} Is Facing...\" submenu is assembled " + built + " times. One of them is "
            + "buildFacingMenu, which exists precisely so that the track diagram and the editor offer "
            + "the same menu rather than two that drift apart");
    }

    /**
     * The signal-focus flag is cleared where every exit passes.
     *
     * OB-040 quietens the whole diagram while the protecting-signals window is up - no arrows, no
     * lengths, everything that is not a signal greyed. That is a good state to be in for a few seconds
     * and a terrible one to be stuck in: an editor that stays grey and arrowless looks broken, and
     * nothing on screen would say why.
     *
     * The window has four ways out - Done, Escape, the close box, and "click it on the diagram" - so
     * the flag is cleared in the `finally` around it rather than in any handler. This checks it stayed
     * there, because moving it into a handler is the natural-looking edit that would break it.
     */
    @Test
    public void testTheSignalFocusIsAlwaysTurnedOffAgain() throws Exception
    {
        if (!PANEL.isFile()) return;

        String source = new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8);

        int at = source.indexOf("signalWindowOpen = false");

        assertTrue(at > 0, "nothing ever turns the signal focus off (OB-040)");

        String before = source.substring(Math.max(0, at - 400), at);

        assertTrue(before.contains("finally"),
            "the signal focus is turned off somewhere other than the finally block. The window has four "
            + "ways out and only the finally sees all of them; anywhere else leaves the editor grey and "
            + "arrowless with nothing on screen saying why");
    }

    /**
     * And every part of the de-clutter is asked the same question.
     *
     * Greying, arrows and lengths are three separate decisions in `annotationFor`. A de-clutter that is
     * on for one of them and off for the others reads as a diagram that is half-dressed rather than
     * focused - and three numbers that have to agree is exactly the shape that produced OB-037.
     */
    @Test
    public void testTheDeclutterIsOneDecision() throws Exception
    {
        if (!PANEL.isFile()) return;

        List<String> lines = Files.readAllLines(PANEL.toPath(), StandardCharsets.UTF_8);

        int uses = 0;

        for (String line : lines)
        {
            if (line.contains("focused") && !line.trim().startsWith("//")
                && !line.contains("isFocusedOnSignals()")) uses++;
        }

        // Three: the greying, the arrows, the lengths.  The declaration is excluded above, so this
        // counts the places that USE the answer rather than the place that works it out.
        assertEquals(uses, 3,
            "the signal focus is used at " + uses + " places in AutonomyEditorPanel, not the three it "
            + "has to reach - the greying, the arrows and the lengths. A de-clutter that is on for one "
            + "of them and off for the others looks half-dressed rather than focused");
    }
    /**
     * The autonomy editor shows the railway, not a grid over it.
     *
     * OB-028. The rule is small enough to state as a function and is tested as one: the palette keeps
     * its visible border in both modes, the layout editor keeps its grey grid, and autonomy mode gets
     * no border at all.
     *
     * **No border, not an invisible one.** The first version returned an empty border of the same
     * thickness, to keep the insets so a hover could not shift the artwork. Adam: "The grid is
     * correctly gone, but now there is a gap between tiles (essentially a white grid)" - an inset with
     * nothing drawn in it shows the panel behind, so a grey grid became a white one. The shift it
     * guarded against cannot happen anyway: `receiveMoveEvent` returns immediately in autonomy mode,
     * so nothing swaps this border for another.
     */
    @Test
    public void testTheAutonomyEditorHasNoVisibleGrid()
    {
        javax.swing.border.Border editing =
            org.traincontrol.gui.LayoutEditor.restingBorder(false, false);

        javax.swing.border.Border autonomy =
            org.traincontrol.gui.LayoutEditor.restingBorder(false, true);

        javax.swing.border.Border palette =
            org.traincontrol.gui.LayoutEditor.restingBorder(true, true);

        assertTrue(editing instanceof javax.swing.border.LineBorder,
            "the layout editor lost its grid - OB-028 asks for the borders to RETURN in the editor");

        assertNull(autonomy,
            "the autonomy editor's tiles must sit flush, exactly as they do in the viewer. A border "
            + "that draws nothing still takes up room, and the room shows the panel behind it - which "
            + "is a white grid where the grey one used to be (MT-127)");

        assertTrue(palette instanceof javax.swing.border.LineBorder,
            "the palette needs its borders in both modes - those tiles are a menu of things to place, "
            + "and the border is what separates one from the next");
    }

    /**
     * A grid knows whether IT is in an editor, not whether an editor is open somewhere.
     *
     * `layout.getEdit()` is the flag the two editors share for their mutual exclusion. While either was
     * open, every grid on screen answered yes to it - the viewer included - so the viewer drew the
     * editor's grey grid, greyed its captions, dropped its hand cursors and tooltips, and attached
     * mouse listeners that cast their parent to `LayoutEditor`, which the viewer is not.
     *
     * One line of that constructor already asked the question properly, as a conjunction with the
     * host; the other six asked the short version. This requires the short version to be gone.
     */
    @Test
    public void testTheViewerIsNotToldItIsAnEditor() throws Exception
    {
        File grid = new File("src/org/traincontrol/gui/LayoutGrid.java");

        if (!grid.isFile()) return;

        List<String> lines = Files.readAllLines(grid.toPath(), StandardCharsets.UTF_8);

        List<String> bare = new java.util.ArrayList<>();

        for (String line : lines)
        {
            if (!line.contains("layout.getEdit()")) continue;

            String trimmed = line.trim();

            if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;

            // The one place it is legitimately asked: working out the answer everything else uses
            if (line.contains("inEditor =")) continue;

            bare.add(trimmed);
        }

        assertEquals(bare, new java.util.ArrayList<String>(),
            "LayoutGrid asks layout.getEdit() directly. That flag says an editor is OPEN, not that this "
            + "grid is in one, and the difference is everything the viewer looked like while the "
            + "autonomy editor was up. Use the inEditor answer computed once at the top");
    }

    /**
     * Renaming a station does not re-place its label.
     *
     * MT-116, Adam: "Weird - the label moves around to adjacent cells on rename."
     *
     * `AutonomySession.placeCaption` MOVES a station's caption when it already has one rather than
     * refusing - which is right when somebody has asked for the name to be shown on a particular
     * square, and wrong as a side effect of a rename. The label had a place somebody chose; the rename
     * says nothing about where it should go; and the search picks whichever neighbouring square is free
     * this time round. So it wandered.
     *
     * Nothing needs re-placing for the text to change - a caption points at the station's SQUARE and
     * looks the name up - so the rename path must ask whether the station is labelled already before
     * placing anything.
     */
    @Test
    public void testARenameOnlyLabelsAStationThatHasNoLabel() throws Exception
    {
        if (!PANEL.isFile()) return;

        List<String> lines = Files.readAllLines(PANEL.toPath(), StandardCharsets.UTF_8);

        // Every place that names a square and then labels it - there are two, and the reason this
        // test exists is that the fix went on one of them first.
        int renames = 0;

        for (int i = 0; i < lines.size(); i++)
        {
            if (!lines.get(i).contains("session.setPointName(")) continue;

            renames++;

            String after = "";

            for (int j = i; j < Math.min(lines.size(), i + 30); j++)
            {
                after += lines.get(j) + " ";
            }

            if (!after.contains("placeLabelFor(")) continue;

            assertTrue(after.contains("getLabelledStationTiles"),
                "the rename at line " + (i + 1) + " places a label without first asking whether the "
                + "station already has one. placeCaption MOVES an existing caption, so that turns "
                + "every rename into a move and the label wanders between neighbouring squares "
                + "(MT-116)");
        }

        assertTrue(renames >= 2,
            "expected both rename paths - the single prompt and the naming walk - and found "
            + renames + ". If one was removed, this test should be updated rather than made to pass");
    }


}
