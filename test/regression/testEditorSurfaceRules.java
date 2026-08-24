package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.LayoutEditor;

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
        assertTrue(PANEL.isFile(),
            "cannot find " + PANEL.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        List<String> lines = Files.readAllLines(PANEL.toPath(), StandardCharsets.UTF_8);

        int writes = 0;
        boolean redrawn = false;

        for (int i = 0; i < lines.size(); i++)
        {
            if (!lines.get(i).contains("session.setFacing(")) continue;

            writes++;

            // The redraw lives in radio(), the helper every one of these answers is built with -
            // not beside this particular write. It was beside this one once, which is exactly how
            // TD-1 happened: the station and turning radios sat in the same helper and got nothing.
            for (int j = 0; j < lines.size(); j++)
            {
                if (!lines.get(j).contains("private javax.swing.JMenuItem radio(")) continue;

                for (int k = j; k < Math.min(lines.size(), j + 30); k++)
                {
                    if (lines.get(k).contains("placementChanged()")) redrawn = true;
                }
            }

            // The TOGGLE helper as well, which this used to leave out (TD-1).
            //
            // Pinning radio() alone said "the facing redraws" and let its sibling keep the bug: toggle()
            // carries four setup writes reachable from the track diagram's own menu, and one of them -
            // whether trains may arrive by a side - decides how a square SPLITS, which is the case the
            // rebuild exists for. A rule with two implementations needs both named, which is the shape
            // this whole file is about.
            for (int j = 0; j < lines.size(); j++)
            {
                if (!lines.get(j).contains("private javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey")) continue;

                boolean rebuilt = false;

                for (int k = j; k < Math.min(lines.size(), j + 40); k++)
                {
                    if (lines.get(k).contains("placementChanged()")) rebuilt = true;
                }

                assertTrue(rebuilt,
                    "the menu's toggle helper writes the setup and only refreshes. Four settings go "
                    + "through it, and one of them changes how a square splits - so the running layout "
                    + "keeps Points the setup no longer has, and everything that goes through their "
                    + "names quietly stops working (TD-1)");
            }
        }

        assertEquals(writes, 1,
            "the facing MENU writes to the setup from " + writes + " places in AutonomyEditorPanel. Two "
            + "copies of this menu is how OB-039 survived being fixed: the redraw goes on the copy "
            + "somebody is looking at, and the other one keeps the bug");

        // And project-wide, which is the half this used to claim without checking (NR-4).
        //
        // The count above is about ONE file, and its message said "the facing is written from one
        // place" as though that were a property of the codebase. It was not: a third writer added in
        // any other file passed, and the surface OB-039 was actually reported from - the track
        // diagram's own menu - is one of them.
        //
        // A named LIST rather than a count, following testTriggerWaitsSayNothing: a count says
        // something is wrong and a list says what, and adding a writer means saying here which file it
        // is in and why it is allowed to be a second one.
        assertEquals(filesWriting("setFacing("),
            Arrays.asList("AutonomyEditorPanel.java", "AutonomySession.java",
                "LayoutRightclickAutonomyMenu.java"),
            "a facing is written to the setup from a file this rule has not been told about. Each of "
            + "the three known ones redraws in its own way - the menu through placementChanged(), the "
            + "diagram's right-click through updateVisiblePoints() as part of MOVING a locomotive, and "
            + "the session's own migration before anything is on screen - so a fourth has to say which "
            + "of those it is, or it is a copy of a menu that will keep the bug the others had fixed");

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
        assertTrue(PANEL.isFile(),
            "cannot find " + PANEL.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

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
        assertTrue(PANEL.isFile(),
            "cannot find " + PANEL.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        // The CODE, with the comments taken out.
        //
        // This read the raw source, and the only "finally" within its window was the word inside the
        // comment that explains why the clear is in a finally - so the assertion was satisfied by
        // writing rather than by doing, and it failed in both directions: rewording that comment broke
        // the build, and moving the clear into Done's handler WITH its comment left the test green.
        // The real keyword sat 27 characters outside the window it was searching (TD-3).
        //
        // Two sibling tests already had this fix when this one was written, and a third gained it since:
        // a test that reads source has to read the code.
        String source = withoutComments(
            new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8));

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
        assertTrue(PANEL.isFile(),
            "cannot find " + PANEL.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

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

        // Not assertNull any more.  MT-127 is a rule about ROOM - "there is now a gap between tiles
        // (essentially a white grid)" - and asserting null pinned the implementation that happened to
        // satisfy it, which then made the grid toggle undeliverable in this editor (OB-056). The grid
        // is drawn here now, by a border that paints and reserves nothing.
        assertEquals(autonomy == null ? 0 : autonomy.getBorderInsets(new javax.swing.JLabel()).left, 0,
            "the autonomy editor's tiles must sit flush, exactly as they do in the viewer. A border "
            + "that takes up room shows the panel behind it in that room - which is a white grid where "
            + "the grey one used to be (MT-127)");

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

        assertTrue(grid.isFile(),
            "cannot find " + grid.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

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
        assertTrue(PANEL.isFile(),
            "cannot find " + PANEL.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

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

    /**
     * The running layout is rebuilt from the setup in exactly ONE place.
     *
     * TD-9, from the three-day history review. `rebuildRunningLayoutFromSetup` was lifted out of
     * `autonomyEditorClosed` so that an edit made from the diagram's own menu could ask for the same
     * thing - its javadoc says so - and the original was left behind as a second copy of the same
     * sixteen lines, comments and all.
     *
     * That duplication has already cost once. The stale-capture defect (NR-1) was reported at one of
     * the two sites, and fixing it there left the other one wrong; the commit that fixed it says "the
     * reviewer found one site; there were two". The ten-line explanation of WHY the load must skip its
     * capture step was then written into both places rather than one.
     *
     * So this asserts the thing that stops a third copy: the load that rebuilds the running layout
     * without capturing appears once in the whole file. It is deliberately about the CALL rather than
     * about the comment - a comment can be copied and still be true; a second call is the defect.
     */
    @Test
    public void testTheRunningLayoutIsRebuiltFromOnePlace() throws Exception
    {
        File source = new File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(source.isFile(),
            "cannot find " + source.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

        int found = 0;

        for (String line : text.split("\n"))
        {
            String code = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;

            if (code.contains("load(activeDiagramConfiguration, false, false)")) found++;
        }

        assertEquals(found, 1,
            "the rebuild-from-setup load appears " + found + " times. It was extracted into "
            + "rebuildRunningLayoutFromSetup precisely so there would be one of it, and the last time "
            + "there were two, a fix went into one of them and left the other wrong (TD-9)");
    }

    /**
     * Which files under src/ write through a given call, by simple name, sorted.
     *
     * Comments stripped, for the reason TD-3 exists: a rule about what the code DOES cannot be
     * satisfied by what a comment mentions.
     *
     * @param call the text of the call, such as ".setFacing("
     * @return the simple names of the files containing it
     */
    private List<String> filesWriting(String call) throws Exception
    {
        File src = new File("src");

        assertTrue(src.isDirectory(), "cannot find " + src.getAbsolutePath()
            + " - a test that reads the source cannot pass by not finding it");

        List<File> sources = new ArrayList<>();

        collect(src, sources);

        java.util.Set<String> found = new java.util.TreeSet<>();

        for (File file : sources)
        {
            String body = withoutComments(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

            // The declaration is not a write, and neither is a reference to it in a javadoc - which
            // the comment stripping above has already taken care of.
            body = body.replace("public void setFacing(", "");

            if (body.contains(call)) found.add(file.getName());
        }

        return new ArrayList<>(found);
    }

    private void collect(File from, List<File> into)
    {
        File[] children = from.listFiles();

        if (children == null) return;

        for (File child : children)
        {
            if (child.isDirectory()) collect(child, into);
            else if (child.getName().endsWith(".java")) into.add(child);
        }
    }

    /**
     * A source file with its comments taken out.
     *
     * Copied rather than shared with the three other tests that do this: a test helper reaching into
     * another test class is a dependency between things that are supposed to fail independently.
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

    /**
     * Turning the grid off moves nothing, and hovering moves nothing either.
     *
     * FR-006: "make the gray grid an option you can toggle in the visible elements.  on by default, but
     * persisted if turned off.  make sure hovering (blue/red outlines) doesn't increase tile widths
     * when it is off."
     *
     * The second half is the one with teeth, and it was already broken in autonomy mode, which has had
     * the grid off since it was written: the resting border there was NULL. A border occupies space, so
     * a square with none is a pixel smaller in each direction than one with a line - and putting a
     * coloured line on it to show the pointer is over it made it grow, which pushes every square after
     * it along. Off, the resting border is an EMPTY border of the same width instead.
     *
     * So the property is: all three borders a square can be wearing - grid on, grid off, hovered - take
     * up exactly the same room. Asked of the insets rather than of the picture, because insets are what
     * the layout manager reads.
     *
     * The three-argument form is used deliberately: the two-argument one asks the stored preference,
     * and a test that reads the operator's settings is a test whose answer depends on their settings.
     */
    @Test
    public void testTheGridTakesUpTheSameRoomOnAsOff()
    {
        javax.swing.JLabel square = new javax.swing.JLabel();

        assertNotNull(LayoutEditor.restingBorder(false, false, true),
            "the grid is not drawn at all with the toggle on");

        assertNull(LayoutEditor.restingBorder(false, false, false),
            "with the grid off a square must rest in NO border. An empty border of the same width was "
            + "tried and is wrong: it still takes up room, and the room shows the panel behind it - a "
            + "white grid where the grey one used to be, which is MT-127 and which the autonomy "
            + "editor's own rule below pins");

        assertEquals(
            LayoutEditor.restingBorder(false, true, true).getBorderInsets(square).left, 0,
            "the autonomy editor draws its grid ON the squares (OB-056), so it must reserve no room. "
            + "A border that takes up room shows the panel behind it in that room - a white grid where "
            + "the grey one used to be, which is MT-127 and which the autonomy editor's own rule "
            + "below pins");

        assertTrue(LayoutEditor.restingBorder(false, false, true).getBorderInsets(square).left > 0,
            "the grid border takes up no room, so nothing above tests anything");
    }

    /**
     * And the hover outline is the same size as both, which is what stops the diagram shifting.
     */
    @Test
    public void testHoveringDoesNotResizeASquare() throws Exception
    {
        javax.swing.JLabel square = new javax.swing.JLabel();

        // The outline highlightLabel puts on a DIAGRAM square while the grid is off. Reached by
        // reflection because it is the private half of a rule whose public half is restingBorder, and
        // the two only mean anything together: what a square rests in, and what it wears when hovered.
        java.lang.reflect.Method overlay =
            LayoutEditor.class.getDeclaredMethod("overlayLine", java.awt.Color.class, int.class);

        overlay.setAccessible(true);

        javax.swing.border.Border hover =
            (javax.swing.border.Border) overlay.invoke(null, java.awt.Color.BLUE, 1);

        java.awt.Insets hovered = hover.getBorderInsets(square);

        assertEquals(hovered.left, 0,
            "hovering a square reserves space while the grid is off, so it grows by a pixel and the "
            + "pointer pushes the diagram along in front of it. With no grid a square rests in NO "
            + "border, so its outline has to reserve nothing either (FR-006)");

        assertEquals(hovered.top, 0, "hovering a square reserves height while the grid is off");
        assertEquals(hovered.bottom, 0);
        assertEquals(hovered.right, 0);

        // And it still draws something - a border that reserves nothing AND paints nothing would pass
        // the assertions above while making the hover invisible.
        java.awt.image.BufferedImage picture =
            new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = picture.createGraphics();

        hover.paintBorder(square, g, 0, 0, 20, 20);

        g.dispose();

        assertEquals(picture.getRGB(0, 0), java.awt.Color.BLUE.getRGB(),
            "the hover outline reserves no space and draws nothing either, so hovering a square with "
            + "the grid off shows nothing at all");
    }

    /**
     * Emptying the page cache hands its caption labels back first.
     *
     * MT-134 item 3 is "open and close the editor a dozen times on a big layout and watch memory", and
     * Adam asked for a test. The heap growth itself is a hands-on observation - there is no UI harness
     * here to open a real editor a dozen times - but the DEFECT behind it is a rule about source, and
     * that is what rots.
     *
     * Every page in the cache holds a grid, and every grid holds the caption labels it registered with
     * the window. `discard()` asks the window whether its container is still cached and stands down if
     * it is - so at the moment the cache is thrown away, nothing has handed those labels back and
     * nothing ever will: the containers become unreachable while `layoutStations` still holds a
     * reference to every label inside them. One page's worth per cache reset, for the life of the
     * session.
     *
     * The lazy prune cannot recover them either. It needs a successor label for the same square, and a
     * caption that was CLEARED never gets one.
     *
     * So: the assignment that empties the cache must be preceded by the hand-back. Read from source
     * rather than exercised, because the thing being pinned is that a future edit which adds a second
     * way to empty the cache does not forget - which is exactly how this arrived the first time.
     */
    @Test
    public void testEmptyingThePageCacheHandsItsLabelsBack() throws Exception
    {
        File source = new File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(source.isFile(),
            "cannot find " + source.getAbsolutePath() + " - a test that reads the source cannot pass "
            + "by not finding it. This returned quietly, so renaming or moving that file would have "
            + "taken this rule with it and said nothing");

        String[] lines = withoutComments(new String(
            Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8)).split("\n");

        List<String> unguarded = new ArrayList<>();

        for (int at = 0; at < lines.length; at++)
        {
            String line = lines[at];

            // The FIELD's own initialiser is not a reset - there is nothing to hand back yet
            if (!line.contains("layoutCache = new HashMap")) continue;
            if (line.contains("public HashMap")) continue;

            // Somewhere in the handful of lines above it, the hand-back
            boolean handedBack = false;

            for (int back = Math.max(0, at - 6); back < at; back++)
            {
                if (lines[back].contains("forgetCachedPageLabels()")) handedBack = true;
            }

            if (!handedBack) unguarded.add((at + 1) + ":  " + line.trim());
        }

        assertEquals(unguarded, new ArrayList<String>(),
            "the page cache is emptied without handing its caption labels back first. Every container "
            + "in it is about to become unreachable while layoutStations still holds a reference to "
            + "every label inside it - one page's worth per reset, for the life of the session. That "
            + "is the leak MT-134 asks about, and discard() cannot cover it: it stands down when the "
            + "container is still cached, and the reset is what makes it not cached: " + unguarded);
    }

    /**
     * The caption menu items name the station they are about.
     *
     * FR-014, Adam: "the show station name here right click menu option in the autonomy editor should
     * clearly indicate the current station being shown, in cases where the user just sees [---] on the
     * diagram."
     *
     * A caption square draws the station's OCCUPANT, and a station with no train on it draws as
     * `GraphLocAssign.NONE_LABEL` - three dashes. So on most of the railway most of the time, a
     * captioned square says nothing whatever about which station it is captioning, and the menu was
     * the only way to find out. It did not say either: one item read "Show a Station Name Here..." and
     * the other "Clear This Square", and neither named anything.
     *
     * Two halves, and the second is the one worth a test.
     *
     * The names have to be in `addCaptionItems`, because BOTH menus build their caption items through
     * it - the editor's own right-click and the deep menu handed to the track diagram. Half the
     * defects in this file's history are a fix applied to one of a pair, and this file already holds
     * three tests about exactly that. The deep menu happens to carry a `title()` naming the station
     * already; the editor's own menu has none, and that is the menu Adam was looking at. So a fix
     * written at a call site could have been written at the one that was already fine.
     *
     * Asserted as "the un-named keys are not used anywhere" rather than "the named keys are used
     * here", because that is the version a second copy of the menu cannot get past.
     */
    @Test
    public void testTheCaptionItemsNameTheStationTheyAreAbout() throws Exception
    {
        assertTrue(PANEL.isFile(),
            "cannot find " + PANEL.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it");

        String source = new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8);

        // The clear item is ALWAYS about a station - it is only offered when one is captioned - so
        // there is no reading of the code in which an un-named clear is correct.
        assertFalse(source.contains("\"autosetup.ui.menuClearStationHere\""),
            "a caption is cleared through a menu item that does not say what it is clearing. That "
            + "item only appears when a station IS captioned, and the square it sits on reads [---] "
            + "whenever no train is standing there - so this asks the user to confirm removing "
            + "something whose identity is not shown anywhere on screen (FR-014)");

        // The named variants exist and are formatted, not looked up flat: I18n.f is what puts the
        // station into them, and a plain t() on a key holding {0} would show the placeholder.
        for (String key : new String[] {"menuShowStationHereNamed", "menuClearStationHereNamed"})
        {
            assertTrue(source.contains("I18n.f(\"autosetup.ui." + key + "\""),
                "autosetup.ui." + key + " is not formatted with a station name. It carries a {0}, so "
                + "asking for it with I18n.t would put the placeholder itself on the menu");
        }

        // And both live in the shared method, so a menu built somewhere else cannot quietly offer an
        // un-named pair of its own.
        int at = source.indexOf("private void addCaptionItems(");

        assertTrue(at > 0, "addCaptionItems is gone. It is the one place both menus build their "
            + "caption items, and this rule is about it being one place - so if it was split, this "
            + "test should be rewritten rather than deleted");

        int ends = source.indexOf("\n    }\n", at);

        assertTrue(ends > at, "could not find the end of addCaptionItems");

        String body = source.substring(at, ends);

        assertTrue(body.contains("menuShowStationHereNamed") && body.contains("menuClearStationHereNamed"),
            "the caption items are named somewhere other than addCaptionItems, which is the shared "
            + "method both menus use. A name added at one call site is missing from the other, and "
            + "the deep menu is the one that already showed the station - so a fix written there "
            + "would have changed nothing for the menu FR-014 is about");

        assertTrue(body.contains("describeTile("),
            "addCaptionItems does not ask describeTile for the station's name. That is the method "
            + "that falls back to the s88 address or the coordinates when a square has no authored "
            + "name, which is the case where the user has least else to go on");
    }
}
