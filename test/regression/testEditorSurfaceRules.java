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
    /**
     * A double quote, as a string.  Named so the source-reading checks below can look for
     * quoted Java without a line of escapes standing between the reader and what it matches.
     */
    private static final String QUOTE = String.valueOf((char) 34);

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

                // Sixty, not forty.  The method grew an error guard - C11, it called its action
                // bare where its sibling wraps one - and the rebuild moved from line 20 to line 53,
                // past a window that had been generous when it was written. Sixty still stops inside
                // this method, which ends at 59 lines, so it cannot borrow a placementChanged() from
                // whatever comes next; that is the property worth keeping, not the number.
                for (int k = j; k < Math.min(lines.size(), j + 60); k++)
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

        // Carriage returns stripped, for consistency with the rule below rather than because this
        // one needs it (FBR-C8, corrected by RA-A1): this test COUNTS occurrences of a marker and
        // never bounds a window on a brace, so line endings cannot change its answer. A source read
        // that behaves differently depending on how git wrote the file is not a guard, and having
        // one of these normalise and not the other would be an invitation to remove the wrong one.
        String source = new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8)
            .replace("\r", "");

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
        // The GRID STATE is passed, never left to the machine (OB-091).
        //
        // These used the two-argument form, which asks `showGrid()` - and that reads a live user
        // preference out of the Java Preferences store. So this test asserted whatever the person at
        // this computer last clicked. It passed for weeks because the preference defaults to true, and
        // failed the moment Adam turned the grid off while testing something else: the layout editor's
        // border is legitimately null with the grid off, and the first assertion below calls that
        // "the layout editor lost its grid".
        //
        // Same family as the carriage-return dependence (FBR-C8): a guard whose answer comes from the
        // environment is not a guard. Both states are checked now, explicitly.
        javax.swing.border.Border editing =
            org.traincontrol.gui.LayoutEditor.restingBorder(false, false, true);

        javax.swing.border.Border autonomy =
            org.traincontrol.gui.LayoutEditor.restingBorder(false, true, true);

        javax.swing.border.Border palette =
            org.traincontrol.gui.LayoutEditor.restingBorder(true, true, true);

        assertNull(org.traincontrol.gui.LayoutEditor.restingBorder(false, false, false),
            "with the grid off the layout editor must rest in no border at all (MT-127)");

        assertTrue(editing instanceof javax.swing.border.LineBorder,
            "the layout editor lost its grid - OB-028 asks for the borders to RETURN in the editor");

        // Not assertNull any more.  MT-127 is a rule about ROOM - "there is now a gap between tiles
        // (essentially a white grid)" - and asserting null pinned the implementation that happened to
        // satisfy it, which then made the grid toggle undeliverable in this editor (OB-056). The grid
        // is drawn here now, by a border that paints and reserves nothing.
        // With the grid ON both editors reserve the same room, so no tile is truncated by a line
        // drawn over art that was sized without it (OB-091). MT-127's rule is the assertion above,
        // about the grid being OFF.
        assertEquals(autonomy.getBorderInsets(new javax.swing.JLabel()).left,
            editing.getBorderInsets(new javax.swing.JLabel()).left,
            "the two editors reserve different room for the same grid, so a tile is a pixel narrower "
            + "in one of them (OB-091)");

        assertNull(org.traincontrol.gui.LayoutEditor.restingBorder(false, true, false),
            "with the grid off the autonomy editor must rest in no border either - that is MT-127, "
            + "and it is about this state rather than the one above");

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

        // The two editors reserve the SAME room with the grid on (OB-091).
        //
        // This asserted the opposite - that the autonomy editor reserves none - citing MT-127. That
        // read one rule across two states: MT-127 is about the grid being OFF, and the assertion above
        // is the one that pins it. With the grid ON, reserving nothing means the cell is sized as
        // though there were no line and then has one painted over it, so the line eats a pixel of the
        // tile art. Adam: "make the behavior of the autonomy editor match so that there are no tile
        // truncations."
        assertEquals(
            LayoutEditor.restingBorder(false, true, true).getBorderInsets(square).left,
            LayoutEditor.restingBorder(false, false, true).getBorderInsets(square).left,
            "the two editors reserve different amounts of room for the same grid, so a tile is a "
            + "pixel narrower in one of them and its art is truncated by the line drawn over it "
            + "(OB-091)");

        assertNull(LayoutEditor.restingBorder(false, true, false),
            "with the grid OFF the autonomy editor must still rest in no border at all - that is what "
            + "MT-127 is about, and it is the state the assertion above used to be applied to");

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

        // Carriage returns stripped, because the rule below bounds its window on a newline followed
        // by the closing brace - the indexOf a few lines down - and this repository checks out
        // CRLF on Windows (FBR-C8). It passed in the tree it was written in and was red on a fresh
        // clone: a guard that depends on how git happened to write the file is not a guard.
        //
        // FSR-C7 raised that the comment here described rules this site does not have, and the fix
        // for it swapped the two comments instead of correcting them, so each was then true of the
        // other site (RA-A1). Which is FSR-C7 again, in mirror image, produced by fixing FSR-C7.
        String source = new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8)
            .replace("\r", "");

        // The clear item is ALWAYS about a station - it is only offered when one is captioned - so
        // there is no reading of the code in which an un-named clear is correct.
        assertFalse(source.contains("\"autosetup.ui.menuClearStationHere\""),
            "a caption is cleared through a menu item that does not say what it is clearing. That "
            + "item only appears when a station IS captioned, and the square it sits on reads [---] "
            + "whenever no train is standing there - so this asks the user to confirm removing "
            + "something whose identity is not shown anywhere on screen (FR-014)");

        // Each named variant is asked for in the form its own VALUE requires.
        //
        // This used to assert that both keys were fetched with I18n.f, on the stated grounds that
        // "it carries a {0}". That was true of both when it was written and is the kind of fact a
        // test should not be holding in its head: MT-162 took the placeholder out of
        // menuShowStationHereNamed - the menu was naming the same station twice in two lines - and
        // this failed, naming a reason that had stopped being true. A guard that has to be edited
        // whenever the thing it guards legitimately changes teaches people to edit guards.
        //
        // So it reads the bundle. A value with a placeholder must be formatted, because I18n.t would
        // put the {0} on the menu; a value without one must NOT be, because I18n.f with an argument
        // nothing consumes is a caller that thinks it is saying something it is not. Both directions
        // matter, and only the first was ever checked.
        java.util.Properties bundle = new java.util.Properties();

        try (java.io.InputStream in = new java.io.FileInputStream(
            "src/org/traincontrol/resources/messages.properties"))
        {
            bundle.load(in);
        }

        for (String key : new String[] {"menuShowStationHereNamed", "menuClearStationHereNamed"})
        {
            String value = bundle.getProperty("autosetup.ui." + key);

            assertNotNull(value, "autosetup.ui." + key + " is gone from the bundle");

            boolean placeholder = value.contains("{0}");

            assertEquals(source.contains("I18n.f(\"autosetup.ui." + key + "\""), placeholder,
                "autosetup.ui." + key + " reads \"" + value + "\", which " + (placeholder
                    ? "carries a placeholder - so it has to be fetched with I18n.f, or the {0} itself "
                      + "appears on the menu"
                    : "carries no placeholder - so fetching it with I18n.f passes an argument nothing "
                      + "uses, which is a caller under the impression it is naming the station"));
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

    /**
     * The shortcut to the full editor is greyed by the editor's own reasons for refusing.
     *
     * FR-026: "to the 'autonomy setup' right click menu on the track viewer, add a shortcut to open
     * the full editor.  deactivate when inappropriate."
     *
     * "Inappropriate" is not a new judgement - `openLayoutEditor` already refuses four different ways,
     * each with its own dialog. Writing those four conditions out again on the menu item is this
     * application's most repeated defect, and this file exists because of it: a rule written in two
     * places drifts, and the drift shows up as an item that is dead when it should work, or live and
     * then followed by an error box.
     *
     * So the check is not that the item is disabled under some condition. It is that the item asks
     * `whyAutonomyEditorCannotOpen`, and that `whyAutonomyEditorCannotOpen` answers for every refusal
     * openLayoutEditor actually makes - matched by the MESSAGE KEY each refusal shows, because that is
     * the thing the two methods must agree about and the thing a fifth guard added later would bring
     * with it.
     *
     * MUTATION: adding a fifth refusal to openLayoutEditor without answering for it here fails this;
     * so does replacing the menu item's `refusal == null` with any hand-written condition.
     */
    @Test
    public void testTheFullEditorShortcutAsksTheEditorsOwnRefusals() throws Exception
    {
        String ui = codeOnly(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/TrainControlUI.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        String predicate = bodyOf(ui, "public String whyAutonomyEditorCannotOpen()");

        assertFalse(predicate.isEmpty(),
            "whyAutonomyEditorCannotOpen is gone. It is the one answer to 'would the editor open', "
            + "and the menu item and openLayoutEditor are both supposed to be reading it");

        // The last parameter rather than the first line: the three-argument overload above it
        // begins with the same words, and indexOf would stop on that one.
        String opener = bodyOf(ui, "reveal, boolean remember)");

        assertFalse(opener.isEmpty(), "openLayoutEditor's four-argument form could not be found, so "
            + "this test is reading nothing and would pass however far the two had drifted");

        java.util.List<String> refusals = new java.util.ArrayList<>();

        String opening = "I18n.t(" + QUOTE;

        for (int at = opening.length(); (at = opener.indexOf(opening, at)) >= 0; at++)
        {
            int from = at + opening.length();
            int to = opener.indexOf(QUOTE, from);

            if (to > from) refusals.add(opener.substring(from, to));
        }

        assertTrue(refusals.size() >= 4,
            "openLayoutEditor shows fewer messages than the four refusals it had when this was "
            + "written, so what this test is matching against has changed shape: " + refusals);

        for (String key : refusals)
        {
            assertTrue(predicate.contains(key),
                "openLayoutEditor refuses with \"" + key + "\" and whyAutonomyEditorCannotOpen has "
                + "nothing to say about it. The menu item that offers the editor is greyed by that "
                + "method, so this refusal is one the user meets as an error box after pressing a "
                + "live item - which is the state FR-026 asked for the item NOT to be in");
        }

        String menu = codeOnly(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        String setup = bodyOf(menu, "private void addSetupMenu()");

        assertTrue(setup.contains("whyAutonomyEditorCannotOpen()"),
            "the setup menu no longer asks the window whether the editor would open, so whatever "
            + "greys the shortcut now is a second copy of the rule");

        // Asking is not enough - the answer has to be the thing that decides.  Reading the variable
        // back out rather than matching a literal name, so this says "what it asked for is what it
        // used" and not "somebody once called it refusal".
        int assignment = setup.indexOf(" = ui.whyAutonomyEditorCannotOpen()");
        int names = setup.lastIndexOf(" ", assignment - 1);

        String answer = setup.substring(names + 1, assignment);

        assertTrue(setup.contains("if (" + answer + " == null)"),
            "the shortcut asks whyAutonomyEditorCannotOpen and then decides on something else, so "
            + "the refusal it was told about is not what greys it. That is the same rule in two "
            + "places again, which is the whole reason the method exists");

        assertTrue(setup.contains("setToolTipText(I18n.t(" + answer + "))"),
            "the disabled shortcut no longer says WHY. A greyed menu item with no reason on it is "
            + "indistinguishable from a broken one, which is the note LayoutRightclickAutonomyMenu "
            + "already makes about the editor-open case above it");

        assertTrue(setup.contains("menuOpenFullEditor"),
            "the shortcut FR-026 asked for is not on the setup menu any more");
    }

    /**
     * The text of one method, from its declaration to the brace that closes it.
     *
     * Brace counting rather than a regex, because both methods here contain nested blocks and a lambda,
     * and a match that stopped at the first } would hand back a fragment that happens to contain
     * whatever the assertions were looking for.
     *
     * @param source the file
     * @param declaration the exact declaration line, or the first line of it
     * @return the body, or "" when the declaration is not there
     */
    private static String bodyOf(String source, String declaration)
    {
        int at = source.indexOf(declaration);

        if (at < 0) return "";

        int open = source.indexOf('{', at + declaration.length());

        if (open < 0) return "";

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(at, i + 1);
        }

        return "";
    }

    /**
     * Both autonomy menus head themselves with a name, and it is the same name.
     *
     * OB-112. Adam, right-clicking LowerBack on the diagram with a setup loaded: "nothing at the top
     * there." The editor\u2019s menu had opened with a bold, disabled name since it was built; the
     * diagram\u2019s - which is the one people actually reach for - had none.
     *
     * The fix that would have been wrong is three lines of naming copied into the second menu, which
     * is this file\u2019s whole subject: one decision written twice drifts, and here the drift would be
     * two menus calling one square different things while both are on screen. So the rule sits on the
     * session and this asserts that neither menu has its own.
     *
     * The heading is also checked to go on AFTER the emptiness test. A heading is not an item, and a
     * menu with nothing to offer must stay unshown rather than become a grey box with a station name
     * in it - the exact fault showFor was centralised to prevent.
     *
     * MUTATION: inlining describeTile\u2019s body back into AutonomyEditorPanel fails the delegation
     * check; moving menu.headline() above the getComponentCount test fails the ordering one.
     */
    @Test
    public void testBothAutonomyMenusNameTheSquareTheSameWay() throws Exception
    {
        String panel = codeOnly(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/AutonomyEditorPanel.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        String describe = bodyOf(panel, "private String describeTile(TileKey tile)");

        assertFalse(describe.isEmpty(), "the editor no longer has describeTile, which every heading "
            + "and caption item in that window is named by");

        assertTrue(describe.contains("session.describeTile(tile)"),
            "AutonomyEditorPanel names squares itself again instead of asking the session. The "
            + "diagram\u2019s menu asks the session, so this is one square with two names in two "
            + "windows - which is the defect this whole file is about");

        assertFalse(describe.contains("isFeedback()"),
            "the sensor-address fallback is written out in the panel as well as in the session, so "
            + "there are two copies of the rule again and only one of them will get the next fix");

        String menu = codeOnly(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(bodyOf(menu, "private void headline()").contains("session.describeTile("),
            "the diagram\u2019s menu names the clicked square some other way, so the heading OB-112 "
            + "asked for can disagree with the one in the editor");

        String show = bodyOf(menu, "static void showFor(");

        int counted = show.indexOf("getComponentCount()");
        int headed = show.indexOf("headline()");

        assertTrue(counted >= 0 && headed > counted,
            "the heading goes on before the menu is known to have anything in it, so a right-click "
            + "with nothing to offer now opens a one-item grey box - which is what showFor was made "
            + "the only way in to prevent");
    }

    /**
     * The caption-hiding rule is asked about the right two things.
     *
     * `LayoutGrid.hidesStationCaptions` has its truth table checked elsewhere, and a truth table is
     * exactly what an extracted rule cannot tell you the most important thing about: whether the
     * caller hands it the right arguments. That has produced two defects in this repository already -
     * a rule lifted out is a rule whose call site is the only untested part of it.
     *
     * So this reads the call. It must be asked whether this grid is in an editor AT ALL, and whether
     * that editor is the autonomy one - not `layout.getEdit()` alone, which is the flag BOTH editors
     * share for their mutual exclusion and which was wrong in the viewer for exactly this reason.
     *
     * MUTATION: passing `layout.getEdit()` instead of `inEditor`, or dropping the autonomy-mode test,
     * fails this.
     */
    @Test
    public void testTheCaptionHideRuleIsAskedWithTheRightTwoQuestions() throws Exception
    {
        String grid = codeOnly(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/LayoutGrid.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        int at = grid.indexOf("hidesStationCaptions(inEditor,");

        assertTrue(at > 0,
            "nothing asks hidesStationCaptions whether this grid is in an editor. Either the rule is "
            + "no longer used or it is being asked something else, and both mean captions appear "
            + "where FR-030 says they should not");

        String call = grid.substring(at, Math.min(grid.length(), at + 200));

        assertTrue(call.contains("isPageExcludedFromAutonomy("),
            "the rule is no longer asked whether this page is left out of autonomy, so a page nobody "
            + "routes over draws station names again - and neither visibility switch can reach them");

        assertTrue(call.contains("isAutonomyMode()"),
            "the second thing the rule is asked is not whether the editor is the autonomy one. "
            + "layout.getEdit() is true in BOTH editors - it is the flag they share for their mutual "
            + "exclusion - so anything that asks it alone hides the captions in the window that "
            + "exists to set them");
    }

    /**
     * The s88 trigger door never puts a question on the screen.
     *
     * Adam\u2019s ruling was "ask me, at the two human doors" - and the third door has nobody at it.
     * A route fired by a sensor that raised a modal dialog would block its own thread forever, never
     * reach the finally that clears isExecuting, and throw its turnout whenever somebody eventually
     * happened past and pressed OK, against whatever was on the path by then.
     *
     * **A test-coverage review found that deleting the `!auto` term fails nothing in 1089 tests.**
     * The model-side tests reach this code with `getGUI()` null, so the confirm branch is unreachable
     * from them and passes for both values of `auto`. The rule with the worst consequence had no
     * automated cover at all.
     *
     * So this reads the source, in the way this file already does for rules that are textual. It
     * checks three things, because there are three ways to lose it: the ask must be gated on `!auto`,
     * the chained-route call must pass `auto` on rather than a constant, and the door itself must
     * still be `execRoute(true)`.
     *
     * MUTATION: deleting `!auto &&`, or hard-coding false in the chained call, fails this.
     */
    @Test
    public void testTheS88DoorIsNeverAskedAnything() throws Exception
    {
        String route = codeOnly(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/marklin/MarklinRoute.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        int asks = route.indexOf("confirmRouteConflictMidway(");

        assertTrue(asks > 0, "nothing in MarklinRoute asks the mid-route question any more");

        String around = route.substring(Math.max(0, asks - 220), asks);

        assertTrue(around.contains("!auto"),
            "the mid-route confirmation is no longer gated on !auto, so a route fired by an s88 "
            + "sensor would raise a modal dialog with nobody at the machine - blocking its own "
            + "thread, never clearing isExecuting, and throwing the turnout whenever somebody "
            + "eventually pressed OK");

        assertTrue(route.contains("r.execRoute(auto, recursionLimit - 1, false)"),
            "a chained route is no longer told which door it was reached through. Hard-coding this "
            + "was harmless while auto meant only \"do not pop the emergency-stop notice\"; it stopped "
            + "being harmless when auto came to mean \"a person is standing here to be asked\"");

        assertTrue(route.contains("this.execRoute(true)"),
            "the s88 trigger door no longer calls execRoute(true), so whatever it is now, it is not "
            + "the door the rule above is about");
    }

    /**
     * The two rules that take the gaps out of a right-click menu, each asked its own question.
     *
     * `tidy` is what stops a menu assembled from a dozen independent blocks showing the dividers of
     * the blocks that had nothing to offer. It has two rules and they are NOT the same question:
     *
     *   - a DIVIDER has nothing to separate when nothing follows it, or when a divider does;
     *   - a HEADING is followed by its own divider always, because title() writes the pair together,
     *     so for a heading the question has to be asked one component further along.
     *
     * Those shared one variable for a day. Widening it for the heading - which is what OB-112 needed -
     * quietly narrowed it for the divider, and two dividers in a row stopped being collapsed unless a
     * THIRD followed. That is the empty band between two lines that OB-054 was filed for, put back by
     * the fix for something else, in the same method, on the same day I wrote a commit message about
     * rules being right where they are written and wrong one level out.
     *
     * **A reviewer then found that mutating the divider half back left the whole suite green**: no
     * fixture menu produces two adjacent dividers, so the rule that OB-054 exists for was correct and
     * completely uncovered. Which is how it came back the first time.
     *
     * MUTATION: giving the two rules one shared condition again fails the first case here.
     */
    @Test
    public void testTidyCollapsesTheGapsItWasWrittenFor()
    {
        // OB-054 itself: a block that had nothing to offer left its divider behind, next to another.
        assertEquals(shapeAfterTidy("I--I"), "I-I",
            "two dividers in a row were left as two, which is an empty band between two lines - the "
            + "exact thing OB-054 was filed for");

        assertEquals(shapeAfterTidy("I---I"), "I-I", "three in a row were not collapsed either");

        assertEquals(shapeAfterTidy("-I"), "I", "a divider at the top has nothing above it to divide");

        assertEquals(shapeAfterTidy("I-"), "I",
            "a divider at the bottom has nothing below it to divide");

        // And the heading rule, which is the one the shared condition was widened for.
        assertEquals(shapeAfterTidy("H-I"), "H-I",
            "a heading with something under it was removed - that is a heading doing its job");

        assertEquals(shapeAfterTidy("H-"), "",
            "a heading over an empty section was kept. title() writes the heading and its divider "
            + "together, so a heading followed by a divider and nothing else is a title for nothing");

        assertEquals(shapeAfterTidy("I-H-"), "I",
            "a heading over an empty section at the END of a menu was kept, along with the divider "
            + "that was only there to separate it");
    }

    /**
     * Builds a menu of the given shape, tidies it, and reads the shape back.
     *
     * `I` an item, `-` a divider, `H` a heading - a disabled item, which is what title() leaves
     * behind and what tidy recognises one by.
     */
    private String shapeAfterTidy(String shape)
    {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        for (char part : shape.toCharArray())
        {
            if (part == '-')
            {
                menu.addSeparator();
                continue;
            }

            javax.swing.JMenuItem item = new javax.swing.JMenuItem(String.valueOf(part));

            item.setEnabled(part != 'H');

            menu.add(item);
        }

        org.traincontrol.gui.AutonomyEditorPanel.tidy(menu);

        StringBuilder out = new StringBuilder();

        for (java.awt.Component one : menu.getComponents())
        {
            out.append(one instanceof javax.swing.JSeparator ? '-' : one.isEnabled() ? 'I' : 'H');
        }

        return out.toString();
    }

    /**
     * The way OUT of the editor does what every other way out does.
     *
     * A track-diagram edit has two halves in two places. The diagram is discarded by
     * `layoutEditingComplete` re-reading the pages from disk; the autonomy setup those same gestures
     * wrote - dragging a captioned tile writes it immediately, per gesture - is put back by
     * `undoAutonomyEdits`, and that is the CALLER'S job. The sidebar switch does it. The editor's own
     * X does it, under a comment reading "Both halves of the edit, or neither".
     *
     * Closing the APPLICATION did not. `maySettleBeforeExit` was a bare delegation to
     * `settleUnsavedWork`, which completes a Discard by itself only in autonomy mode - so answering
     * Discard on the way out put the diagram back and left the caption on the square it had been
     * dragged to. The two then described different railways, and the next reconciling save resolved
     * that by pruning the entries that no longer matched anything: the precise loss the pre-edit note
     * exists to prevent.
     *
     * It was unreachable until it was not. Before application exit began disposing the editor, the note
     * was left behind on disk and the NEXT start completed the discard by accident. The fix for a real
     * defect removed the accident that had been covering this one.
     *
     * **This reads the source, and that is a weaker thing than running it.** `settleUnsavedWork` puts a
     * modal dialog on the screen and there is no seam to answer it from here; nothing in the suite
     * builds a LayoutEditor. The behaviour is MT-201, by hand. What this catches is a reader deciding
     * the method looks over-complicated and giving it back its one line.
     *
     * MUTATION: taking the `settledByDiscarding` test out of `completeExitDiscard`, or calling it from
     * `maySettleBeforeExit` again, each fails one of these.
     */
    @Test
    public void testLeavingCompletesBothHalvesOfADiscard() throws Exception
    {
        String editor = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/LayoutEditor.java")), java.nio.charset.StandardCharsets.UTF_8);

        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")), java.nio.charset.StandardCharsets.UTF_8);

        String asks = withoutComments(bodyOf(editor, "public boolean maySettleBeforeExit()"));

        assertTrue(asks.contains("settleUnsavedWork()"),
            "the exit path stopped asking about unsaved work at all, which is worse than what this "
            + "test was written for");

        String completes = withoutComments(bodyOf(editor, "public void completeExitDiscard()"));

        assertTrue(completes.contains("undoAutonomyEdits()"),
            "the exit path settles the diagram and not the setup it shares with it. Answer Discard "
            + "while the TRACK editor is open and the diagram goes back while the autonomy setup "
            + "keeps the edit - and the next save prunes whatever no longer matches");

        // In TRACK mode only.  Autonomy mode completes its own discard inside settleUnsavedWork, and
        // undoing there as well would put back a setup the user may have just chosen to keep.
        assertTrue(completes.contains("!isAutonomyMode()"),
            "the setup is put back in both modes now. The autonomy editor discards its own edits "
            + "already, so this undoes them twice - and after a Save it would undo the save");

        // And only when the user actually said Discard.
        //
        // The first version undid whenever settleUnsavedWork returned true, which it also does when
        // there was nothing to settle and nothing was asked - so closing the application with a clean
        // editor open rewound the setup to the editor-open snapshot, losing whatever autonomy had
        // done since. A reviewer found it by asking what the method does when `unsaved` is false.
        assertTrue(completes.contains("settledByDiscarding"),
            "the exit path undoes on \"nothing needed settling\" as well as on Discard, which "
            + "rewinds a setup nobody chose to discard");

        // AFTER everything that can refuse the exit.
        //
        // It was called from maySettleBeforeExit, the first thing the exit does - so the rewind
        // happened, the trains dialog then said no, and the application carried on running with the
        // undo already spent.
        String exit = withoutComments(bodyOf(ui, "private void WindowClosed("));

        assertTrue(exit.indexOf("completeExitDiscard()") > exit.indexOf("maySettleBeforeExit()"),
            "the discard is completed before the exit is certain. Anything between those two can "
            + "still return, and then the setup has been rewound and the application is still up");
    }

    /**
     * Escape puts the autonomy editor's tools down, and brings their buttons up with them.
     *
     * OB-119. Adam: "escape should turn off test a path in the autonomy editor."
     *
     * The track editor has done this for a while and its own comment says why in a sentence worth
     * keeping: Escape "lets go of everything the editor is holding ... the picked squares, the copied
     * group, the armed tool - and the picking MODE, which stayed on afterwards with its button still
     * pressed. Letting go of the squares but not of the mode is the half of Escape nobody asks for."
     *
     * The autonomy panel had none of it, and the button half matters more here than in the track
     * editor. A tool left LOOKING armed while the panel thinks nothing is armed sends the next click to
     * `cycle()`, which changes a square's direction - so a read-only inspection tool that appeared to
     * be armed would silently edit the railway. That exact fault has been fixed here once already, when
     * a second tool button arrived without a button group.
     *
     * Read rather than run: the panel needs a session, a page and a live diagram to stand up, and the
     * binding is WHEN_IN_FOCUSED_WINDOW so there is no component to send a key to in a headless test.
     * What this catches is the parts being separated - the binding removed, or the buttons stopped
     * being cleared with the tool.
     *
     * MUTATION: dropping the `setSelected(false)` loop from `putToolsDown`, or the `installEscape()`
     * call from the constructor, each fails one of these.
     */
    @Test
    public void testEscapePutsTheAutonomyToolsDown() throws Exception
    {
        String panel = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/AutonomyEditorPanel.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String install = withoutComments(bodyOf(panel, "private void installEscape()"));

        assertTrue(install.contains("VK_ESCAPE"),
            "the autonomy editor no longer binds Escape at all");

        assertTrue(install.contains("putToolsDown()"),
            "Escape is bound to something other than putting the tools down");

        assertTrue(install.contains("WHEN_IN_FOCUSED_WINDOW"),
            "Escape is bound only while this panel has focus. The click that armed the tool leaves "
            + "focus on the diagram, so the key would work only if the user had happened to click a "
            + "control in this column first");

        // And that anybody installs it.
        assertTrue(withoutComments(bodyOf(panel, "public AutonomyEditorPanel(")).contains(
            "installEscape()"),
            "nothing calls installEscape, so the binding exists and is never made");

        String down = withoutComments(bodyOf(panel, "public void putToolsDown()"));

        assertTrue(down.contains("setSelected(false)"),
            "the tool buttons are left looking pressed after Escape. The panel then thinks no tool is "
            + "armed while the button says one is, and the next click falls through to cycle(), which "
            + "CHANGES a square - a read-only tool silently editing the railway");

        assertTrue(down.contains("Tool.NONE"), "Escape does not actually disarm the tool");

        assertTrue(down.contains("clearGesture()"),
            "a half-finished gesture survives Escape - a one-way run waiting for its far end would "
            + "swallow the next click anywhere on the diagram");
    }

    /**
     * The grid actually asks the caption rules, and asks them the right things.
     *
     * Three rules were lifted out of the constructor so they could be tested without building a window
     * - `captionOffset`, `runsNorthSouth` and `onPill` - and all three got a test of the rule. None got
     * a test that the grid still calls it, which is the half that has cost this project four defects,
     * three of them in the last week.
     *
     * The javadoc on `hidesStationCaptions` states the price plainly: "what that leaves uncovered is
     * whether the caller passes the right two booleans, which is the usual price of pulling a rule out
     * of its call site". That rule has had a call-site check since the day it was written. Its three
     * neighbours did not, and there is no reason for the difference beyond nobody having noticed.
     *
     * Read rather than run, for the reason the one above it is: the constructor needs a diagram, a
     * window and a UI to reach, and what can silently break here is the call being dropped or handed
     * the wrong argument - both visible in the text.
     *
     * MUTATION: passing `true` instead of `runsNorthSouth(c)` fails the second assertion; swapping
     * `onPill` back to `readableOn` fails the third.
     */
    @Test
    public void testTheGridAsksTheCaptionRulesItsQuestions() throws Exception
    {
        String grid = withoutComments(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/LayoutGrid.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(grid.contains("StationCaption.captionOffset("),
            "nothing asks captionOffset where a caption goes, so the rule is computed by something "
            + "else - or by nothing, and every caption sits wherever the layout drops it");

        // The ORIENTATION, which is the argument that can be wrong without anything failing.
        //
        // It moved on 2026-08-27, from captionOffset to setRotated: rotating the north-south captions
        // made the offset the same for both, so the orientation now decides which WAY a caption is
        // turned rather than how far down it sits. Still asked of THIS square, and still the thing
        // that can be a constant without anything falling over.
        assertTrue(grid.contains("runsNorthSouth(c)"),
            "the grid no longer asks which way the rails run on THIS square. Pass a constant and every "
            + "caption is turned the same way: half of them read upwards along track that runs across");

        // The ANSWER and not just the call.  `setRotated(` alone would be satisfied by
        // `setRotated(false)`, which is the whole defect with the guard still in place - and it is
        // the mistake this morning's onPill assertion made, so it is not a hypothetical one.
        assertTrue(grid.contains("setRotated(onEnd)"),
            "the caption is not told what runsNorthSouth answered - pass a constant and either every "
            + "caption stands on end or none does, and half of them lie across the rail they name, "
            + "which is what the rotation was asked for to stop");

        assertTrue(grid.contains("onEnd = runsNorthSouth(c)"),
            "what the caption is told to do is no longer THIS square's orientation, so the rotation "
            + "and the placement can now disagree about which way the rails run");

        // BOTH shifts are paid back before the constraints are used again.
        //
        // A caption borrows room to centre itself - a column to the left when flat, a row above when
        // stood on end - by moving gbc, and gbc is then REUSED by the address label for the same
        // square. Leaving the shift in draws that label a whole tile away, over its neighbour's. That
        // is not hypothetical: it happened with gridx, fifteen times on one page of Adam's layout, and
        // the bounds harness could not see it because it built its grids with addresses switched off.
        //
        // gridy joined it on 2026-08-27 with the rotation, and the mutation proved the point - taking
        // the payback out again left the whole suite green.
        //
        // Read rather than run, and that is a real limitation: the behavioural version needs a square
        // that carries BOTH an autonomy caption and an address label, which means standing up an
        // autonomy graph. What this catches is the payback being dropped, which is how the bug
        // actually arrived both times.
        // Asserted by POSITION, not by counting.
        //
        // Counting was the first attempt and it was worthless: both statements appear three times in
        // this file, so dropping one left two and the check passed. Both mutations survived it. The
        // property was never "it appears often enough" - it is that the shift is undone in the window
        // between adding the caption and the next thing that reads gbc.
        int added = grid.indexOf("container.add(text, gbc);");
        int reused = grid.indexOf("getShowAddress()");

        assertTrue(added >= 0 && reused > added,
            "cannot find the caption being added and the address label reading the constraints after "
            + "it, so this check cannot see the window it is about");

        String between = grid.substring(added, reused);

        assertTrue(between.contains("gbc.gridx = x;"),
            "the column a caption borrows is never given back before the constraints are reused, so "
            + "the address label for a captioned square is drawn a tile to the LEFT, on top of its "
            + "neighbour's - fifteen of them on one page of Adam's layout the last time");

        assertTrue(between.contains("gbc.gridy = y;"),
            "the row a rotated caption borrows is never given back, so the address label for that "
            + "square is drawn a whole tile ABOVE it - the same defect gridx already had, in the "
            + "direction the rotation just added");

        // And the caption's OWN line height, not the tile size or a guess.
        assertTrue(grid.contains("text.lineHeight()"),
            "captionOffset is no longer given the caption's line height, so where a caption sits stops "
            + "depending on how tall its text actually is");

        // onPill and not readableOn, at BOTH places that colour a caption.
        //
        // The first version of this asserted that onPill appears somewhere in the file, and there are
        // two call sites - so swapping one of them back to readableOn left it green. That is the same
        // looseness this whole test exists to remove, committed inside the test removing it.
        int asks = 0;

        for (int at = grid.indexOf("StationCaption.onPill("); at >= 0;
            at = grid.indexOf("StationCaption.onPill(", at + 1))
        {
            asks++;
        }

        assertEquals(asks, 2,
            "the grid colours captions through onPill at " + asks + " places, and there are two: the "
            + "resting pill and the one under a standing train. A caption coloured any other way gets "
            + "its placeholder and its name in the same colour");

        assertFalse(grid.contains("readableOn("),
            "the grid asks readableOn directly. It answers by the FILL alone, so a placeholder and a "
            + "name come back identical - which is exactly the defect a reviewer found by disbelieving "
            + "the comment above this call and running it");
    }

    /**
     * The excluded-locomotives prompt still asks whether it is breaking a home.
     *
     * `homeBrokenBy` was made static and public so its rule could be tested without a window, and it
     * has a test. Nothing tested that `promptLocomotives` calls it - so excluding a locomotive from
     * the station it is homed to would go through silently, with no warning, and the rule would sit
     * there passing its own test.
     *
     * The warning is the whole feature. The comment beside the call says it is "warned rather than
     * refused, like the home warning: an operator may well mean it" - which is only true if the
     * operator is told.
     *
     * MUTATION: deleting the `homeBrokenBy` call from `promptLocomotives` fails this.
     */
    @Test
    public void testExcludingALocomotiveStillWarnsAboutItsHome() throws Exception
    {
        String panel = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/AutonomyEditorPanel.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String prompt = withoutComments(bodyOf(panel, "private void promptLocomotives("));

        assertTrue(prompt.contains("homeBrokenBy("),
            "the excluded-locomotives prompt no longer asks whether the exclusion breaks a home, so "
            + "excluding a locomotive from the station it is homed to happens silently");

        assertTrue(prompt.contains("confirmExcludingHome"),
            "the prompt asks homeBrokenBy and then does not warn about the answer, which is the same "
            + "as not asking");

        // And the other side of the same pair, which is where the rule is shared from.
        String home = withoutComments(bodyOf(panel, "private void promptHome("));

        assertTrue(home.contains("homeChoices("),
            "the home prompt builds its list some other way than homeChoices, so the two sides of "
            + "this pairing can now disagree about which locomotives may be offered");
    }

    /**
     * Java source with its // comments stripped, so a check reads code and not the prose about it.
     */
    private static String codeOnly(String source)
    {
        StringBuilder out = new StringBuilder();

        for (String line : source.split("\n", -1))
        {
            int slashes = line.indexOf("//");

            out.append(slashes >= 0 ? line.substring(0, slashes) : line).append("\n");
        }

        return out.toString();
    }

    /**
     * Every caption on the diagram is shown or hidden by the same rule.
     *
     * FR-023 added a second reason to hide a station's name - it is one autonomy will never choose -
     * beside the one that already existed, the overlay switch. Adam reported the new setting having no
     * effect, and the reason was a THIRD place deciding: `showStaticAutonomyLayer` set every label to
     * its own `show` parameter wholesale, ran whenever the overlay was drawn, and overwrote the other
     * two without asking anything about the station.
     *
     * That is this codebase's most repeated defect - one decision written in more than one place -
     * and it is exactly what the DR findings are about. So this insists there is one rule and that
     * every site asks it, rather than trusting three call sites to stay in step.
     */
    @Test
    public void testEveryCaptionVisibilityDecisionAsksTheRule() throws Exception
    {
        String source = codeOnly(new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/TrainControlUI.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(source.contains("private boolean captionIsActive("),
            "captionIsActive is gone.  It is the one answer to 'should this station's name be drawn', "
            + "and the sites below are supposed to share it");

        // Every line that shows or hides one of the diagram's caption labels.
        java.util.List<String> deciders = new java.util.ArrayList<>();

        for (String line : source.split("\n"))
        {
            String trimmed = line.trim();

            if (!trimmed.contains(".setVisible(")) continue;

            // The caption labels, and nothing else this window shows or hides.
            if (!trimmed.startsWith("value.setVisible") && !trimmed.contains("label.setVisible"))
            {
                continue;
            }

            deciders.add(trimmed);
        }

        assertEquals(deciders.size(), 3,
            "there are now " + deciders.size() + " places setting a caption label's visibility, not "
            + "three.  A new one is not wrong in itself - but it has to ask the same rule, and this "
            + "test is here because the third one did not: " + deciders);

        for (String decider : deciders)
        {
            assertTrue(decider.contains("captionShouldShow") || decider.contains("visible"),
                "a caption's visibility is being set from something other than the shared rule: "
                + decider + ".  Setting them wholesale is what made Show Inactive Labels appear to do "
                + "nothing (FR-023)");
        }

        // And the blanket setter in particular, by name, because it is the one that got it wrong.
        int at = source.indexOf("public void showStaticAutonomyLayer(");

        assertTrue(at > 0, "showStaticAutonomyLayer is gone - if renamed, rename it here");

        String body = source.substring(at, Math.min(source.length(), at + 2600));

        assertTrue(body.contains("captionIsActive("),
            "showStaticAutonomyLayer no longer asks captionIsActive, so it is back to setting every "
            + "caption wholesale - which is precisely the state Adam reported as 'Flipping the new "
            + "setting has no effect'");
    }

    /**
     * The blocked-points picker offers every stored entry, refuses the station itself, and names what
     * it draws.
     *
     * RA-C2.  Nothing automated pinned any of this dialog's rules: the test that did -
     * `testTheBlockedPointsPickerOffersOnlySquaresThatResolve` - was deleted with the FBR-B3/C7 revert,
     * correctly, because it pinned the filters that revert removed (FBR-D20).  The filters went and the
     * source rule went with them, and the rules that SURVIVED the revert were left with nothing.  Store
     * level `setBlockingPoints` behaviour is tested by four classes; the dialog's offer-and-carry logic
     * is tested by none, and it is where the last three defects in this family were.
     *
     * The three rules, and what each costs when it goes:
     *
     *   - **The station itself is never offered** (OB-083).  Watching itself makes it a station nothing
     *     can ever be sent to, and the second door - a caption that points AT this station, carrying a
     *     name of its own - is the back way into the same state.
     *   - **Every stored entry is offered, whatever the filters say** (FSR-C5).  This dialog is the only
     *     way to edit blockedPoints and `setBlockingPoints` REPLACES the stored list, so an entry it
     *     hides is either deleted on OK (FBR-A2) or permanent, and both are worse than showing it.
     *   - **The check box says what it is** (RA-C2).  The carried entries are precisely the squares that
     *     have lost their name, and `getPointName` returns null for those - so the box that exists to
     *     let the operator remove something they cannot identify rendered with no text at all.
     *
     * Source-level for the same reason as the caption rule above: the fault is textual - a filter that
     * stops being qualified, a label fetched from the wrong accessor - and a dialog cannot be driven
     * from a test in this application.
     *
     * Mutation this must fail: in `AutonomyEditorPanel.promptBlockingPoints`, put the check-box label
     * back to `session.getStore().getPointName(tile)`.  Run 2026-08-25: 1 of 14 tests in this class
     * fails, this one.  Second mutation: drop the `&& !already.contains(tile)` qualifier from the
     * self-caption filter, which is FSR-C5 undone.  Run 2026-08-25: this test fails again, and nothing
     * else in the class moves.
     */
    @Test
    public void testTheBlockedPointsPickerCarriesAndNamesWhatIsStored() throws Exception
    {
        assertTrue(PANEL.isFile(), "cannot find " + PANEL.getAbsolutePath()
            + " - a test that reads the source cannot pass by not finding it");

        // Carriage returns stripped, because the window below ends on a newline and four spaces and a
        // closing brace, and this repository checks out CRLF on Windows (FBR-C8)
        String source = new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8)
            .replace("\r", "");

        int at = source.indexOf("private void promptBlockingPoints(");

        assertTrue(at > 0, "promptBlockingPoints is gone.  It is the only door onto blockedPoints, so "
            + "if it was renamed, rename it here; if it was removed, the restriction can no longer be "
            + "edited at all and this test should say so rather than be deleted");

        int ends = source.indexOf("\n    }\n", at);

        assertTrue(ends > at, "could not find the end of promptBlockingPoints");

        // Comments stripped, so a rule is proved by the code and not by the prose about it - this
        // method's comments name every one of the identifiers below, several times each
        String body = codeOnly(source.substring(at, ends));

        assertTrue(body.length() > 800, "promptBlockingPoints reads as only " + body.length()
            + " characters of code, so the window closed early and every assertion below is being made "
            + "about a fragment");

        // What is stored has to be read before the offer is built, because it is what decides the
        // offer.  Read after, it can only be used to tick boxes that are already on the list.
        assertTrue(body.indexOf("getBlockingPoints(station)") < body.indexOf("getNamedTiles()"),
            "promptBlockingPoints builds its list of choices before reading what is already stored. "
            + "The stored entries are what the filters have to be qualified BY (FSR-C5), so a read "
            + "that happens afterwards can only tick boxes - and an entry the filters hid is then "
            + "deleted the moment OK is pressed, because setBlockingPoints replaces the list (FBR-A2)");

        assertTrue(body.contains("tile.equals(station)"),
            "the station itself is no longer refused as a square that holds it back.  Standing there "
            + "already decides whether it is free, so watching itself makes it a station nothing can "
            + "be sent to (OB-083)");

        assertTrue(body.contains("getCaptionTarget(tile)") && body.contains("!already.contains(tile)"),
            "the self-caption filter is gone, or is no longer qualified by what is already stored. "
            + "Unqualified it hides a stored entry, and this dialog is the only way to remove one - so "
            + "the restriction becomes permanent with nothing on screen saying it is there (FSR-C5). "
            + "Absent altogether, a caption pointing at this station is offered as though it were "
            + "somewhere else, which is self-selection by the back door (OB-083)");

        assertTrue(body.contains("for (TileKey held : already)"),
            "the carried-entries loop is gone.  It is what puts a stored entry the filters never "
            + "reached onto the list - a square that has since lost its name, or the station itself "
            + "from before that was refused - and without it those entries cannot be removed by "
            + "anybody, ever (FSR-C5)");

        assertTrue(body.contains("new javax.swing.JCheckBox(\n                describeTile(tile)")
                || body.contains("new javax.swing.JCheckBox(describeTile(tile)"),
            "the check boxes are labelled with something other than describeTile.  The loop above "
            + "exists to offer squares that have LOST their name, and getPointName is a plain map "
            + "lookup that returns null for exactly those - so the box drawn for one renders with no "
            + "text at all, ticked, asking the operator to keep or remove a thing it will not name "
            + "(RA-C2).  describeTile falls back to the s88 address and then to the coordinates");
    }

}
