package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import support.SourceOf;

/**
 * Rules about the editor's surface, each the same shape of defect: a decision written down in more than one place, or
 * not written down at all.  Source-level where the fault is textual, following `testNoSelfRecursiveWrappers`; a real
 * call where the rule could be lifted out into a function (OB-028's was).
 *
 * - **OB-039** - the facing menu existed twice, so a redraw added to one copy left the other reporting the bug.
 * - **OB-040** - the de-clutter while a signal is being picked reaches the graying, the arrows and the lengths, and is
 *   turned off again down every one of the window's four exits.
 * - **OB-028** - a tile's border at rest differed between the palette, the layout editor and autonomy mode.
 *
 * The setup is told about a facing from exactly ONE place, and that place redraws.  The fault is textual, and invisible
 * to a test that drives the model, the half that always worked.
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

    /** The form of `openLayoutEditor` that holds the refusals; the overloads above it begin with the same words. */
    private static final String OPEN_LAYOUT_EDITOR = "private void openLayoutEditor(String page, Boolean autonomy,"
        + "\n        final org.traincontrol.automationui.TileGraph.TileKey reveal, boolean remember,"
        + "\n        final java.util.List<org.traincontrol.automationui.TileGraph.TileKey> outline)";

    /**
     * The facing menu appears even when the square holds one facing.  "No menu" reads as a fault rather than as an
     * answer, so `buildFacingMenu` never returns null for want of choices.  Nor may the menu override the railway: a
     * facing has to be one the BUILD can hold (`placementCopy` falls back to `startableCopy` and quietly turns the
     * train round), so the menu shows the one facing, ticked, and a disabled line saying why there is nothing to
     * choose.  Checked as the gate, since opening the menu needs a window and a session.
     */
    @Test
    public void testTheFacingMenuAppearsWithASingleChoice() throws Exception
    {
        // The vanishing is asked of the whole program, so a copy of the menu in another file cannot bring it back
        String code = SourceOf.allCode().replaceAll("\\s+", " ");

        String source = SourceOf.declaration("    javax.swing.JMenu buildFacingMenu(final TileKey target)")
            .replaceAll("\\s+", " ");

        assertFalse(code.contains("if (facings.size() <= 1) return null;"),
            "the facing menu still vanishes when a square holds one facing, so the operator is given "
            + "no answer at all rather than the answer that there is nothing to choose");

        // GRAYED, NOT HIDDEN, where there is no facing (OB-347).  Adam's rule: "rather than hiding it, grey it out."
        assertFalse(code.contains("if (facings.isEmpty()) return null;"),
            "the menu is hidden on a square with NO facing, where Adam asked for it greyed (OB-347)");

        assertTrue(source.contains("if (facings.isEmpty()) { facingMenu.setEnabled(false);")
                && source.contains("autosetup.ui.hintFacingNoWayOut"),
            "the menu on a square with NO facing is not greyed with its reason (OB-347)");

        assertTrue(source.contains("if (facings.size() == 1)")
                && source.contains("autosetup.ui.facingOnlyOne"),
            "the single-choice case has no line explaining itself, so the menu shows one radio and "
            + "no reason - which is the same silence in a different shape");
    }
    /**
     * Every door that gives a locomotive a name asks whether the name is usable (MT-270).
     *
     * The doors setting a name must agree with `RouteCommand.isNameUsable`: the Add Locomotive dialog, the rename
     * dialog, the route editor and the one applying a name the Central Station proposes.  A comma in a name re-parses
     * as two commands in routes, which store locomotives by name in a comma-separated format; brackets are allowed by
     * Adam's own ruling.  A door census rather than driving the dialog, because the defect is a door that does not ask.
     *
     * MUTATION: remove the call from any listed door and this fails, naming the door.
     */
    @Test
    public void testEveryDoorThatNamesALocomotiveChecksTheName() throws Exception
    {
        // LOCATED PER DOOR: each door is its own declaration, found in whichever file holds it, so one of a pair going
        // silent is named, and so is a door that moved.
        final String[][] doors =
        {
            {"private void AddLocButtonActionPerformed(java.awt.event.ActionEvent evt)", "the Add Locomotive dialog"},
            {"    void changeLocAddress(Locomotive l, MouseEvent evt)", "the rename dialog"},
            {"private void checkForRenameMenuItemActionPerformed(java.awt.event.ActionEvent evt)",
                "the Central Station name"},
            {"private List<String> problemsWith(CommandRow row)", "the route editor"},
        };

        final java.util.List<String> silent = new java.util.ArrayList<>();

        for (String[] door : doors)
        {
            if (!codeOf(door[0]).contains("isNameUsable(")) silent.add(door[1] + " (" + door[0] + ")");
        }

        assertTrue(silent.isEmpty(),
            "a door that gives a locomotive a name never asks whether the name is usable: " + silent
            + ".  Routes store locomotives by name in a comma-separated format, so a comma in one "
            + "re-parses every route that mentions it into something else - and those routes were "
            + "legal when they were written (MT-270)");

        // AND THE RULE ITSELF still refuses what it is for, so the doors above are asking something
        // that answers.  Brackets are allowed on Adam's ruling; the comma is the refusal.
        assertFalse(org.traincontrol.base.RouteCommand.isNameUsable("a,P[)(]"),
            "the name Adam typed is accepted by the rule the doors ask, so asking it changes nothing");

        assertTrue(org.traincontrol.base.RouteCommand.isNameUsable("SBB 460 (2)"),
            "brackets are refused, and Adam ruled they should just be allowed");
    }
    /**
     * One writer, and it repaints.  The redraw is asked of the helpers' whole declarations, not of a window of lines
     * after them.
     */
    @Test
    public void testTheFacingIsWrittenFromOnePlaceAndThatPlaceRedraws() throws Exception
    {
        // A PREFIX, so a writer cannot escape this count by spelling: the facing menu calls setFacingAndMove, which an
        // exact match for "setFacing(" would not see.
        //
        // COUNTED OVER THE PROGRAM, by any handle a door holds the session by (`session`, `session()`, `getSession()`,
        // `getAutonomySession()`), so a write moved out of the editor or behind an accessor is still counted.
        int writes = count(FACING_WRITE, SourceOf.allCode());

        // The redraw lives in radio(), the helper every one of these answers is built with.  `placementChanged(null)`,
        // not `placementChanged()`: the bare name would be satisfied by the declaration itself, which is not a call.
        boolean redrawn = codeOf("    javax.swing.JMenuItem radio(").contains("placementChanged(null)");

        // The TOGGLE helper as well: pinning radio() alone lets its sibling keep the bug, since toggle() carries four
        // setup writes reachable from the track diagram's menu, one of which (whether trains may arrive by a side)
        // decides how a square SPLITS.  Its whole declaration, not a window of lines after its signature.
        assertTrue(codeOf("    javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey")
                .contains("placementChanged(null)"),
            "the menu's toggle helper writes the setup and only refreshes. Four settings go "
            + "through it, and one of them changes how a square splits - so the running layout "
            + "keeps Points the setup no longer has, and everything that goes through their "
            + "names quietly stops working (TD-1)");

        // TWO, AND THE SECOND IS NOT A MENU.
        //
        // This counted `session.setFacing(` in the whole file, so it could not tell a second copy of the
        // facing menu - the thing it exists to catch - from a placement door that records a heading.
        // The second write is the latter: the "edit or assign locomotive" item, which was writing WHO
        // was placed and not WHICH WAY, and so left `placementCopy` to fall through to copy 0.  It was
        // the sibling of `TrainControlUI.rememberPlacement`, fixed three commits earlier and not
        // swept - the shape every placement defect in this panel has had.
        //
        // Raised with the second one named rather than the check loosened, which is this file's own
        // convention twenty lines down: "adding a writer means saying here which file it is in and why
        // it is allowed to be a second one".  A THIRD write still fails, and the message says what to
        // ask about it.
        // FIVE OVER THE PROGRAM: the editor's two, and the three in the files the list below names -
        // GraphLocAssign.commitAndRecord, the diagram's placeFacing and the window's rememberPlacement.
        // The edit-or-assign door does not write the facing itself, because the TRACK DIAGRAM has the
        // same item and an assignment made there has to be recorded too.  Adam: "there needs to
        // be parity across the board."  A sixth write fails, and the message says what to ask about it.
        assertEquals(writes, 5,
            "the facing is written from " + writes + " places in the program. Five are known.  Two are the "
            + "autonomy editor's: the facing menu in EditorTileMenus (which moves the railway too, SPEC-B4) and "
            + "AutonomyEditorPanel.placeLocomotive - the "
            + "add-to-autonomy door, which was the LAST one recording who without which way "
            + "(CONF-B2), and the worst of them, because placeLocomotive does not clear the facing on "
            + "the non-null path and so left the previous occupant's direction attached to the "
            + "arriving train.  Three are the doors the list below names by file: GraphLocAssign.commitAndRecord, "
            + "the track diagram's placeFacing and the window's rememberPlacement.  A sixth is either a second "
            + "copy of the menu - which is how OB-039 "
            + "survived being fixed, the redraw going on the copy somebody is looking at while the "
            + "other keeps the bug - or the edit-or-assign door writing its own facing again instead "
            + "of going through GraphLocAssign.commitAndRecord, which is how it and the diagram's "
            + "copy of the same item came a week apart (REG8-B2)");

        // AND EACH OF THE FIVE IS WHERE IT IS SAID TO BE.  A total lets one door stop writing the facing
        // while a second copy of the menu starts, and stays five; named place by place, either one is reported.
        final String[] facingWriters =
        {
            "    javax.swing.JMenu buildFacingMenu(final TileKey target)",
            "    void placeLocomotive(TileKey tile)",
            "public static void commitAndRecord(GraphLocAssign edit)",
            "private void placeFacing(String locName, String pointName,",
            "private void rememberPlacement(org.traincontrol.automation.Point point,",
        };

        final List<String> misplaced = new ArrayList<>();

        int named = 0;

        for (String writer : facingWriters)
        {
            int here = count(FACING_WRITE, codeOf(writer));

            named += here;

            if (here != 1) misplaced.add(writer + " writes it " + here + " times");
        }

        if (writes != named) misplaced.add((writes - named) + " writes outside those five");

        assertEquals(misplaced, new ArrayList<String>(),
            "the facing is not written once by each of the five doors known to write it: " + misplaced + ".  A door"
            + " that stopped is a placement recorded without which way, and a write somewhere new is a second copy of"
            + " the menu - how OB-039 survived being fixed - or a door writing its own facing again instead of going"
            + " through GraphLocAssign.commitAndRecord (REG8-B2)");

        // AND BOTH ASSIGNMENT DOORS GO THROUGH THE ONE THAT RECORDS: `commitChanges` moves the running layout and
        // nothing else, so a door calling it directly makes an assignment the setup will not remember.  The one
        // `edit.commitChanges()` allowed is inside commitAndRecord.
        assertEquals(outside("edit.commitChanges()", "public static void commitAndRecord(GraphLocAssign edit)"), 0,
            "an assignment is committed without being recorded. commitChanges moves the running"
            + " layout only, so the placement is gone at the next configuration load - use"
            + " GraphLocAssign.commitAndRecord, which does both (REG8-B2)");

        for (String door : new String[] {
            "    void addLocomotiveSettings(javax.swing.JPopupMenu menu, TileKey target)",
            "private LayoutRightclickAutonomyMenu(TrainControlUI ui," })
        {
            assertTrue(codeOf(door).contains("GraphLocAssign.commitAndRecord("),
                door + " no longer commits an assignment at all, so the check above passes because"
                + " the door has gone rather than because it was fixed");
        }

        // And project-wide, as a named LIST rather than a count, following testTriggerWaitsSayNothing: a list says what
        // is wrong, and adding a writer means saying here which file it is in and why it is allowed to be a second one.
        assertEquals(filesWriting("setFacing("),
            Arrays.asList("AutonomyEditorPanel.java", "AutonomySession.java",
                // GraphLocAssign is the DIALOG both assignment doors show, so what accepting it means is written once,
                // with it; both callers repaint right after.
                "GraphLocAssign.java", "LayoutRightclickAutonomyMenu.java",
                // RunningLayoutSync records the facing of a train the rebuild could not put back
                // (`recordTheTrainsNotPutBack`), and the put-back tells the diagram after.
                "RunningLayoutSync.java",
                // SessionImport is the legacy import's facings, written behind AutonomyViewerPanel.importLegacyGraph,
                // which reloads the railway after.
                "SessionImport.java",
                "TrainControlUI.java"),
            "a facing is written to the setup from a file this rule has not been told about. Each of "
            + "the three known ones redraws in its own way - the menu through placementChanged(), the "
            + "diagram's right-click through updateVisiblePoints() as part of MOVING a locomotive, and "
            + "the session's own migration before anything is on screen - so a fourth has to say which "
            + "of those it is, or it is a copy of a menu that will keep the bug the others had fixed");

        // THE FOURTH, SAYING WHICH OF THOSE IT IS (Adam, 2026-09-06).
        //
        // `TrainControlUI.rememberPlacement` is the second half of the third one.  The rule already
        // knows about the track diagram's right-click menu; this is the same diagram's DRAG, which
        // moves a locomotive by hand and lands in a different class because the drop is handled by the
        // window rather than by the popup.  It redraws the same way - `updateVisiblePoints()` is
        // called three lines above the write - so it is the third case, not a fourth kind.
        //
        // It was added because it was the only placement door that recorded WHO without recording
        // WHICH WAY: a pasted train had no facing, `placementCopy` fell through to copy 0, and the
        // next capture wrote that arbitrary side back as though somebody had chosen it. Adam reported
        // it three times before it was found.

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
        // Counted over the whole program's code, so a second copy built in another file is counted too
        int built = SourceOf.occurrences("menuFacingGroup");

        assertEquals(built, 1,
            "the \"{loc} Is Facing...\" submenu is assembled " + built + " times. One of them is "
            + "buildFacingMenu, which exists precisely so that the track diagram and the editor offer "
            + "the same menu rather than two that drift apart");
    }

    /**
     * The signal-focus flag is cleared where every exit passes.
     *
     * OB-040 quietens the whole diagram while the protecting-signals window is up, and an editor that stays gray and
     * arrowless looks broken.  The window has four ways out (Done, Escape, the close box, a click on the diagram), so
     * the flag is cleared in the `finally` around it rather than in any handler; moving it into a handler is the
     * natural edit that would break it.
     */
    @Test
    public void testTheSignalFocusIsAlwaysTurnedOffAgain() throws Exception
    {
        // The CODE, with the comments taken out: read raw, the only "finally" was the word inside the comment
        // explaining the clear, so rewording it broke the build and moving the clear into Done's handler left the test
        // green.
        //
        // Every clear in the program, each INSIDE a finally block: the finally's OWN brace has not closed when the
        // clear is reached.  Counting every brace between the two would let a clear in a later method's `if` pass.
        String source = SourceOf.allCode();

        int clears = 0;

        for (int at = source.indexOf("signalWindowOpen = false"); at >= 0;
            at = source.indexOf("signalWindowOpen = false", at + 1))
        {
            clears++;

            int block = source.lastIndexOf("finally", at);

            int open = block < 0 ? -1 : source.indexOf('{', block);

            int reached = open;

            for (int depth = 0; open >= 0 && reached < at; reached++)
            {
                char c = source.charAt(reached);

                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) break;
            }

            assertTrue(block >= 0 && open >= 0 && reached == at,
                "the signal focus is turned off somewhere other than the finally block. The window has four "
                + "ways out and only the finally sees all of them; anywhere else leaves the editor grey and "
                + "arrowless with nothing on screen saying why");
        }

        assertTrue(clears > 0, "nothing ever turns the signal focus off (OB-040)");
    }

    /**
     * And every part of the de-clutter is asked the same question.  Graying, arrows and lengths are three decisions in
     * `annotationFor`; one on and the others off reads as a half-dressed diagram, and three numbers that must agree is
     * the shape that produced OB-037.
     */
    @Test
    public void testTheDeclutterIsOneDecision() throws Exception
    {
        // The method that makes the three decisions; `focused` is its local.
        String decisions = codeOf("public org.traincontrol.automationui.TileAnnotation annotationFor(TileKey tile)");

        int uses = 0;

        for (String line : decisions.split("\n"))
        {
            if (line.contains("focused") && !line.contains("isFocusedOnSignals()")) uses++;
        }

        // Three: the graying, the arrows, the lengths.  The declaration is excluded above, so this
        // counts the places that USE the answer rather than the place that works it out.
        assertEquals(uses, 3,
            "the signal focus is used at " + uses + " places in annotationFor, not the three it "
            + "has to reach - the greying, the arrows and the lengths. A de-clutter that is on for one "
            + "of them and off for the others looks half-dressed rather than focused");

        // AND NOTHING ELSE ASKS IT: counted in annotationFor alone, a de-clutter of its own in another method was not
        // seen.
        assertEquals(outside("isFocusedOnSignals()",
                "public org.traincontrol.automationui.TileAnnotation annotationFor(TileKey tile)",
                "public boolean isFocusedOnSignals()"), 0,
            "the signal focus is asked somewhere besides annotationFor, so the de-clutter is decided twice - and two "
            + "decisions that have to agree are the shape that produced OB-037");

        assertEquals(outside("signalWindowOpen", "    boolean signalWindowOpen;",
                "    void pairGuardSignals(AutonomyEditorPanel.Guard guard, TileKey station)",
                "public boolean isFocusedOnSignals()"), 0,
            "the flag under the signal focus is read somewhere besides isFocusedOnSignals, so a de-clutter is decided"
            + " there without asking the one question annotationFor asks");
    }
    /**
     * The autonomy editor shows the railway, not a grid over it (OB-028).
     *
     * The rule is stated as a function: the palette keeps its visible border in both modes, the layout editor keeps its
     * gray grid, and autonomy mode gets no border at all.  No border, not an invisible one: an empty border would keep
     * the insets but show the panel behind, so a gray grid becomes a white one, and the insets are not needed since
     * `receiveMoveEvent` returns immediately in autonomy mode.
     */
    @Test
    public void testTheAutonomyEditorHasNoVisibleGrid()
    {
        // The grid state is passed, never left to the machine (OB-091): the two-argument form asks `showGrid()`, a live
        // user preference, so the test would assert whatever the person at this computer last clicked.  Both states are
        // checked.
        javax.swing.border.Border editing =
            org.traincontrol.gui.layouteditor.LayoutEditor.restingBorder(false, false, true);

        javax.swing.border.Border autonomy =
            org.traincontrol.gui.layouteditor.LayoutEditor.restingBorder(false, true, true);

        javax.swing.border.Border palette =
            org.traincontrol.gui.layouteditor.LayoutEditor.restingBorder(true, true, true);

        assertNull(org.traincontrol.gui.layouteditor.LayoutEditor.restingBorder(false, false, false),
            "with the grid off the layout editor must rest in no border at all (MT-127)");

        assertTrue(editing instanceof javax.swing.border.LineBorder,
            "the layout editor lost its grid - OB-028 asks for the borders to RETURN in the editor");

        // Not assertNull: MT-127 is a rule about ROOM, and asserting null pinned an implementation that made the grid
        // toggle undeliverable in this editor (OB-056).  Here the grid is drawn by a border that paints and reserves
        // nothing.  With the grid ON both editors reserve the same room, so no tile is truncated by a line drawn over
        // art sized without it (OB-091).
        assertEquals(autonomy.getBorderInsets(new javax.swing.JLabel()).left,
            editing.getBorderInsets(new javax.swing.JLabel()).left,
            "the two editors reserve different room for the same grid, so a tile is a pixel narrower "
            + "in one of them (OB-091)");

        assertNull(org.traincontrol.gui.layouteditor.LayoutEditor.restingBorder(false, true, false),
            "with the grid off the autonomy editor must rest in no border either - that is MT-127, "
            + "and it is about this state rather than the one above");

        assertTrue(palette instanceof javax.swing.border.LineBorder,
            "the palette needs its borders in both modes - those tiles are a menu of things to place, "
            + "and the border is what separates one from the next");
    }

    /**
     * A grid knows whether IT is in an editor, not whether an editor is open somewhere.  `layout.getEdit()` is the flag
     * the two editors share for their mutual exclusion, so while either is open it is true for every grid on screen,
     * the viewer included, which would draw the editor's grid and attach mouse listeners that cast their parent to
     * `LayoutEditor`.  A grid asks `inEditor`; this requires the bare question to be gone.
     */
    @Test
    public void testTheViewerIsNotToldItIsAnEditor() throws Exception
    {
        // Every line of the program's code: a grid's code moved to another file is still read.
        List<String> bare = new java.util.ArrayList<>();

        for (String line : SourceOf.allCode().split("\n"))
        {
            if (!line.contains("layout.getEdit()")) continue;

            String trimmed = line.trim();

            // The one place it is legitimately asked: working out the answer everything else uses
            if (line.contains("inEditor =")) continue;

            bare.add(trimmed);
        }

        assertEquals(bare, new java.util.ArrayList<String>(),
            "a grid asks layout.getEdit() directly. That flag says an editor is OPEN, not that this "
            + "grid is in one, and the difference is everything the viewer looked like while the "
            + "autonomy editor was up. Use the inEditor answer computed once at the top");
    }

    /**
     * Renaming a station does not re-place its label (MT-116).  `AutonomySession.placeCaption` MOVES a station's
     * caption when it already has one, right when somebody asks for the name on a particular square and wrong as a side
     * effect of a rename.  A caption points at the station's SQUARE and looks the name up, so the rename path must ask
     * whether the station is labeled already before placing anything.
     */
    @Test
    public void testARenameOnlyLabelsAStationThatHasNoLabel() throws Exception
    {
        // Every line of the program's code, so a rename door in another file is asked as well
        String[] lines = SourceOf.allCode().split("\n");

        // Every place that names a square and then labels it - there are two.
        int renames = 0;

        for (int i = 0; i < lines.length; i++)
        {
            if (!lines[i].contains("session.setPointName(")) continue;

            renames++;

            String after = "";

            for (int j = i; j < Math.min(lines.length, i + 30); j++)
            {
                after += lines[j] + " ";
            }

            if (!after.contains("placeLabelFor(")) continue;

            assertTrue(after.contains("getLabelledStationTiles"),
                "the rename at \"" + lines[i].trim() + "\" places a label without first asking whether the "
                + "station already has one. placeCaption MOVES an existing caption, so that turns "
                + "every rename into a move and the label wanders between neighbouring squares "
                + "(MT-116)");
        }

        assertTrue(renames >= 2,
            "expected both rename paths - the single prompt and the naming walk - and found "
            + renames + ". If one was removed, this test should be updated rather than made to pass");
    }

    /**
     * The running layout is rebuilt from the setup in exactly ONE place.  `rebuildRunningLayoutFromSetup` exists so
     * that an edit from the diagram's menu and `autonomyEditorClosed` ask for the same thing, and a second copy would
     * drift.  So the load that rebuilds without capturing appears once in the whole program: the CALL, not a comment,
     * which can be copied and still be true.
     */
    @Test
    public void testTheRunningLayoutIsRebuiltFromOnePlace() throws Exception
    {
        // Over the whole program's code, so a copy in another file is counted too: as the window spells it, and as
        // RunningLayoutSync, which holds the rebuild, does
        int found = SourceOf.occurrences("load(activeDiagramConfiguration, false, false)")
            + SourceOf.occurrences("load(this.ui.getActiveDiagramConfiguration(), false, false)");

        assertEquals(found, 1,
            "the rebuild-from-setup load appears " + found + " times. It was extracted into "
            + "rebuildRunningLayoutFromSetup precisely so there would be one of it, and the last time "
            + "there were two, a fix went into one of them and left the other wrong (TD-9)");
    }

    /**
     * Which files under src/ write through a given call, by simple name, sorted.  Comments are stripped, since a rule
     * about what the code DOES cannot be satisfied by what a comment mentions.
     *
     * @param call the text of the call, such as ".setFacing("
     * @return the simple names of the files containing it
     */
    private List<String> filesWriting(String call) throws Exception
    {
        File src = new File("src");

        assertTrue(src.isDirectory(), "cannot find " + src.getAbsolutePath()
            + " - a test that reads the source cannot pass by not finding it");

        java.util.Set<String> found = new java.util.TreeSet<>();

        for (File file : javaFilesUnder(src))
        {
            String body = SourceOf.withoutComments(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

            // The declaration is not a write.
            body = body.replace("public void setFacing(", "");

            if (body.contains(call)) found.add(file.getName());
        }

        return new ArrayList<>(found);
    }

    /**
     * The code of one declaration, comments blanked, from whichever file under src/ holds it.
     *
     * @param signature enough of the declaration to name it once in the program
     * @return its code
     */
    private static String codeOf(String signature)
    {
        return SourceOf.withoutComments(SourceOf.declaration(signature));
    }

    /**
     * A member of one class, for a signature more than one class declares: the class is found wherever it is, and the
     * member in it.
     *
     * @param type enough of the class's declaration to name it once in the program
     * @param signature enough of the member's declaration to name it once in that class
     * @return the member's text, comments included
     */
    private static String memberOf(String type, String signature)
    {
        return SourceOf.declarationIn(SourceOf.declaration(type), signature);
    }

    /**
     * How many times a text is in the program's code outside the given declarations: a negative asked of the whole
     * program where the needle has a place it is allowed.
     *
     * @param needle the text
     * @param allowed the declarations it may be in
     * @return the number of places elsewhere
     */
    private static int outside(String needle, String... allowed)
    {
        int inside = 0;

        for (String signature : allowed) inside += count(codeOf(signature), needle);

        return SourceOf.occurrences(needle) - inside;
    }

    /** A write of a locomotive's facing through the setup, by whatever the door calls its handle on the session. */
    private static final java.util.regex.Pattern FACING_WRITE =
        java.util.regex.Pattern.compile("(?i:session)(?:\\(\\))?\\.setFacing");

    /**
     * How many times a pattern matches in a text.
     *
     * @param pattern the pattern
     * @param text the text to look in
     * @return the count
     */
    private static int count(java.util.regex.Pattern pattern, String text)
    {
        int found = 0;

        for (java.util.regex.Matcher m = pattern.matcher(text); m.find(); ) found++;

        return found;
    }

    /**
     * How many times a text is in another, without overlaps.
     *
     * @param text the text to look in
     * @param needle the text to count
     * @return the count
     */
    private static int count(String text, String needle)
    {
        int found = 0;

        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) found++;

        return found;
    }

    /**
     * Every door that runs a route asks whether it is already running.
     *
     * `executeRoute`'s own comment says *"Every door that runs a route comes through here."*, so the guard belongs in
     * it: clicking the row and confirming, or right-clicking and choosing Execute, must not start a second run (two
     * threads throwing the same accessories).  The button graying is the AFFORDANCE and the check inside executeRoute
     * the GUARD (OB-057, OB-090); the play-button branch must NOT ask again, since a second copy per door is how the
     * other two came to be missing.
     *
     * MUTATION: moving the check back out of `executeRoute` and into the play-button branch fails
     * both halves of this; putting `routesExecuting` into the window's own `RouteListMouseClicked` fails the last one.
     */
    @Test
    public void testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning() throws Exception
    {
        String funnel = codeOf("    void executeRoute(String route)");

        assertTrue(funnel.contains("routesExecuting.contains(route)"),
            "executeRoute does not ask whether the route is already running, and it is the method "
            + "every door comes through - so the row click and the right-click menu can start a "
            + "second run of a route that is already going.  Body: " + funnel);

        assertTrue(funnel.indexOf("routesExecuting.contains(route)") < funnel.indexOf("routeStarted("),
            "executeRoute asks whether the route is running only after marking it as started, which "
            + "answers its own question");

        // AND THE DOOR DOES NOT ASK IT AGAIN.  Counting every mention would be wrong: the cell painter and the hover
        // read the same set to decide what to DRAW, the affordance rather than the guard.
        String door = codeOf("    void RouteListMouseClicked(java.awt.event.MouseEvent evt)");

        // NOT VACUOUS WHEN THE HANDLER MOVES: this is a NetBeans `//GEN-FIRST` method that gets regenerated, and an
        // empty string contains nothing; `SourceOf.declaration` fails loudly wherever the handler has gone.

        assertFalse(door.contains("routesExecuting"),
            "the route row's own click handler asks whether the route is already running instead of "
            + "leaving it to executeRoute.  One door asking for itself is how the other two came to "
            + "be missing it.  Body: " + door);

        // AND THE WINDOW'S OWN HANDLER: the one-line GEN handler in TrainControlUI hands the click to the route list;
        // it must not take the question back, or the door asks for itself while the panel's copy stays clean.
        String handler = codeOf("private void RouteListMouseClicked(java.awt.event.MouseEvent evt)");

        assertFalse(handler.contains("routesExecuting"),
            "the window's own route-row click handler asks whether the route is already running instead of handing the "
            + "click to the route list and leaving the question to executeRoute.  Body: " + handler);
    }

    /**
     * Switching an accessory by hand asks both halves of the question a route asks.
     *
     * `MarklinRoute.heldReason` refuses an accessory on a locked path and a signal protecting a platform somebody is
     * standing at; the second is the one `getActiveAccs` cannot see (a protecting signal is driven by occupancy), so
     * the diagram's accessory tile must ask it too.  The rule lives on `Layout` and THREE doors ask it: the route, the
     * diagram tile and the switch keyboard.  `Layout.clearsProtection(accessory, commandingGreen)` carries the aspect
     * half; each door supplies the way IT is about to command the accessory (a tile toggles, `!isStraight()`; a
     * keyboard button sets an absolute state), since reading the aspect at the keyboard would answer where the
     * accessory is, not where it is sent.
     *
     * MUTATION: deleting either call to `aboutToClearProtection`, dropping `commandingGreen` from the
     * rule, or taking the question off any one of the three doors fails this.
     */
    @Test
    public void testSwitchingAnAccessoryByHandAsksAboutProtectingSignals() throws Exception
    {
        // The tile's constructor, which wires its click, and the helper the click asks
        String label = codeOf("public LayoutLabel(LayoutDiagramComponent c, Container parent, int size, "
            + "TrainControlUI tcUI, boolean edit,");

        String helper = codeOf(
            "private static boolean aboutToClearProtection(TrainControlUI tcUI, Accessory accessory)");

        // BOTH LIMBS, and the aspect: grepping the whole file for the method name would pass with either of the two
        // calls deleted (the accessory and its twin on a three-way).  And the name says nothing about whether the
        // ASPECT is asked with it: without that, clicking a protecting signal to RED, the protective act, raises a
        // warning the route door deliberately does not.
        assertTrue(label.contains("aboutToClearProtection(tcUI, c.getAccessory())")
                && label.contains("aboutToClearProtection(tcUI, c.getAccessory2())"),
            "the diagram's accessory tile does not ask about BOTH accessories of the tile, so a "
            + "three-way's second drive can be switched with no warning");

        assertTrue(helper.contains("clearsProtection(accessory, !accessory.isStraight())"),
            "the tile's protecting-signal question does not ask which way the click will throw the "
            + "signal.  Setting a protecting signal to RED is the protective act, and the route door "
            + "explicitly permits it - a warning here is the over-strictness that door had removed");

        // AND THE HELPER STILL ASKS THE RULE: gutting it (its last line to `return false;`) passes the call assertions
        // above and undoes the guard at the tile door.
        assertTrue(helper.contains("clearsProtection(accessory"),
            "the tile's protecting-signal helper no longer asks the shared rule at all, so the doors "
            + "are back to different definitions of one question.  Body: " + helper);

        // THE BODY, not the file: read whole, this passes on the method name appearing in a comment of `MarklinRoute`.
        String held = codeOf("private String[] heldReason(RouteCommand rc)");

        assertTrue(held.contains("protectsAnOccupiedSquare"),
            "the route no longer asks the shared rule, so the two doors are working from different "
            + "definitions of the same question again.  Body: " + held);

        // The rule the doors delegate to, declared once in the program, on Layout, where the doors reach it
        SourceOf.declaration("public boolean protectsAnOccupiedSquare(Accessory accessory)");

        assertNotNull(org.traincontrol.automation.Layout.class.getMethod("protectsAnOccupiedSquare",
                org.traincontrol.base.Accessory.class),
            "the rule the doors delegate to is not on Layout, so one of them is carrying a copy");

        // AND THE ASPECT IS PART OF THE RULE, not of one caller, so the keyboard can ask it without copying it:
        // dropping the direction would let every door warn about a click that sets a signal to danger.
        String rule = codeOf("public boolean clearsProtection(Accessory accessory, boolean commandingGreen)");

        assertTrue(rule.contains("commandingGreen") && rule.contains("protectsAnOccupiedSquare"),
            "the shared rule no longer asks both halves - which way the accessory is being commanded, "
            + "and whether it protects somewhere occupied.  Body: " + rule);

        // AND THE THIRD DOOR: the switch keyboard has to ask the same questions, and is the door most likely to be
        // forgotten, having never had a guard.  Missing, `SourceOf.declaration` fails: a turnout on a locked path could
        // be thrown from it under a running train.
        String keyboard = codeOf(
            "private boolean keyboardAutonomyAllows(int address, javax.swing.JToggleButton button)");

        assertTrue(keyboard.contains("clearsProtection") && keyboard.contains("getActiveAccs"),
            "the switch keyboard asks fewer than both halves of the question the other two doors ask.  "
            + "Body: " + keyboard);

        // AND THE POWER QUESTION (MT-261 ruling 4).  Adam: "yes, route through the same guards."  A command sent with
        // the power off does nothing, and the keyboard must not send it into the dark leaving the button looking as
        // though it worked.
        String asks = codeOf(
            "private KeyboardSwitch keyboardSwitchAllowed(int address, javax.swing.JToggleButton button)");

        assertTrue(asks.contains("getPowerState") && asks.contains("confirmAccessorySwitchPowerOff"),
            "the switch keyboard no longer asks about the track power, so a click with the power off "
            + "goes nowhere and says so nowhere.  Body: " + asks);

        assertTrue(asks.contains("keyboardAutonomyAllows"),
            "the power question is asked and the two autonomy ones are not, which is one guard where "
            + "there should be three.  Body: " + asks);

        assertTrue(SourceOf.allCode().contains("keyboardSwitchAllowed(switchId, b)"),
            "the switch keyboard's guard exists and nothing calls it, which is the shape of a fix that "
            + "was written and not wired");
    }

    /**
     * Turning the grid off moves nothing, and hovering moves nothing either.
     *
     * FR-006: "make the gray grid an option you can toggle in the visible elements.  on by default, but
     * persisted if turned off.  make sure hovering (blue/red outlines) doesn't increase tile widths
     * when it is off."
     *
     * The second half has the teeth: a border occupies space, so a colored outline grows a square and pushes the rest
     * along.  With the grid off a square rests in NO border, so its hover outline reserves nothing either; with it on
     * both editors reserve the same room (OB-091).  Asked of the insets, which the layout manager reads.  The
     * three-argument form is used deliberately: the two-argument one asks the stored preference, which would make the
     * answer depend on the operator's settings.
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

        // The two editors reserve the SAME room with the grid ON (OB-091): reserving nothing would size the cell as
        // though there were no line and then paint one over the tile art.  MT-127 (grid OFF) is pinned above.  Adam:
        // "make the behavior of the autonomy editor match so that there are no tile truncations."
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

    /** And the hover outline is the same size as both, which is what stops the diagram shifting. */
    @Test
    public void testHoveringDoesNotResizeASquare() throws Exception
    {
        javax.swing.JLabel square = new javax.swing.JLabel();

        // The outline highlightLabel puts on a DIAGRAM square while the grid is off, reached by reflection as the
        // private half of a rule whose public half is restingBorder: what a square rests in and what it wears when
        // hovered.
        java.lang.reflect.Method overlay =
            LayoutEditor.class.getDeclaredMethod("overlayLine", java.awt.Color.class, int.class);

        overlay.setAccessible(true);

        // The color a hovered square really wears, asked of the editor: handing the outline a color of this test's own
        // would prove only that the outline paints what it is given.
        java.lang.reflect.Field hoveredColour =
            LayoutEditor.class.getDeclaredField("COMPONENT_BORDER_HOVERED_COLOR");

        hoveredColour.setAccessible(true);

        java.awt.Color wornWhenHovered = (java.awt.Color) hoveredColour.get(null);

        java.lang.reflect.Field restingColour =
            LayoutEditor.class.getDeclaredField("COMPONENT_BORDER_DEFAULT_COLOR");

        restingColour.setAccessible(true);

        java.awt.Color wornAtRest = (java.awt.Color) restingColour.get(null);

        // SEEN: opaque, and not the color of the grid line a square rests in with the grid on
        assertEquals(wornWhenHovered.getAlpha(), 255,
            "the colour a hovered square wears is not opaque, so hovering one with the grid off shows nothing at all");

        assertNotEquals(wornWhenHovered, wornAtRest,
            "a hovered square wears the colour of the grid line it rests in, so the hover cannot be seen");

        javax.swing.border.Border hover =
            (javax.swing.border.Border) overlay.invoke(null, wornWhenHovered, 1);

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

        assertEquals(picture.getRGB(0, 0), wornWhenHovered.getRGB(),
            "the hover outline reserves no space and draws nothing either, so hovering a square with "
            + "the grid off shows nothing at all");
    }

    /**
     * Emptying the page cache hands its caption labels back first.
     *
     * MT-134 item 3 is "open and close the editor a dozen times on a big layout and watch memory"; the defect behind it
     * is a rule about source.  `discard()` stands down while its container is still cached, so when the cache is thrown
     * away nothing hands the labels back: `layoutStations` would hold every label in unreachable containers, a page's
     * worth per reset, and the lazy prune cannot recover a CLEARED caption.  So the assignment that empties the cache
     * must be preceded by the hand-back, read from source so a second way to empty the cache cannot forget.
     */
    @Test
    public void testEmptyingThePageCacheHandsItsLabelsBack() throws Exception
    {
        // Every line of the program's code, so a reset written in another file is asked as well
        String[] lines = SourceOf.allCode().split("\n");

        List<String> unguarded = new ArrayList<>();

        for (int at = 0; at < lines.length; at++)
        {
            String line = lines[at];

            // The FIELD's own initializer is not a reset - there is nothing to hand back yet
            if (!line.contains("layoutCache = new HashMap")) continue;
            if (line.contains("public HashMap")) continue;

            // Somewhere in the handful of lines above it, the hand-back
            boolean handedBack = false;

            for (int back = Math.max(0, at - 6); back < at; back++)
            {
                if (lines[back].contains("forgetCachedPageLabels()")) handedBack = true;
            }

            if (!handedBack) unguarded.add(line.trim());
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
     * A station with no train draws as `GraphLocAssign.NONE_LABEL` (three dashes), so the menu is the only way to tell
     * which station a captioned square is about.  The names have to be in `addCaptionItems`, which BOTH menus build
     * their caption items through (the deep menu already has a `title()`, the editor's own has none).  Asserted as "the
     * un-named keys are used nowhere", which a second copy of the menu cannot get past.
     */
    @Test
    public void testTheCaptionItemsNameTheStationTheyAreAbout() throws Exception
    {
        // The clear item is ALWAYS about a station (it is only offered when one is captioned), so an un-named clear is
        // never correct; asked of the whole program so a menu built in another file cannot offer it either.
        String code = SourceOf.allCode();

        assertFalse(code.contains("\"autosetup.ui.menuClearStationHere\""),
            "a caption is cleared through a menu item that does not say what it is clearing. That "
            + "item only appears when a station IS captioned, and the square it sits on reads [---] "
            + "whenever no train is standing there - so this asks the user to confirm removing "
            + "something whose identity is not shown anywhere on screen (FR-014)");

        // Each named variant is asked for in the form its own VALUE requires (MT-162): the test reads the bundle rather
        // than assuming both keys carry a {0}, since a guard that must be edited whenever what it guards legitimately
        // changes teaches people to edit guards.  A value with a placeholder must be formatted, or I18n.t would put the
        // {0} on the menu; one without must NOT be, since I18n.f with an argument nothing consumes thinks it says
        // something it does not.
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

            assertEquals(code.contains("I18n.f(\"autosetup.ui." + key + "\""), placeholder,
                "autosetup.ui." + key + " reads \"" + value + "\", which " + (placeholder
                    ? "carries a placeholder - so it has to be fetched with I18n.f, or the {0} itself "
                      + "appears on the menu"
                    : "carries no placeholder - so fetching it with I18n.f passes an argument nothing "
                      + "uses, which is a caller under the impression it is naming the station"));
        }

        // And both live in the shared method, so a menu built elsewhere cannot offer an un-named pair of its own.
        // Gone, `SourceOf.declaration` fails: if it was split, rewrite this test rather than delete it.
        String body = SourceOf.declaration("private void addCaptionItems(");

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
     * The shortcut to the full editor is grayed by the editor's own reasons for refusing.
     *
     * FR-026: "to the 'autonomy setup' right click menu on the track viewer, add a shortcut to open
     * the full editor.  deactivate when inappropriate."
     *
     * `openLayoutEditor` already refuses four ways, each with its own dialog; writing those conditions out again on the
     * menu item would be a rule written in two places.  So the item asks `whyAutonomyEditorCannotOpen`, which must
     * answer for every refusal openLayoutEditor makes, matched by the MESSAGE KEY each shows, which a fifth guard would
     * bring.
     *
     * MUTATION: adding a fifth refusal to openLayoutEditor without answering for it here fails this;
     * so does replacing the menu item's `refusal == null` with any hand-written condition.
     */
    @Test
    public void testTheFullEditorShortcutAsksTheEditorsOwnRefusals() throws Exception
    {
        // Gone, `SourceOf.declaration` fails: it is the one answer to 'would the editor open'.
        String predicate = codeOf("public String whyAutonomyEditorCannotOpen()");

        // The five-argument form, which the others hand on to.  Not found, SourceOf fails rather than this test reading
        // nothing.
        String opener = codeOf(OPEN_LAYOUT_EDITOR);

        java.util.List<String> refusals = new java.util.ArrayList<>();

        // A refusal is said as its own sentence, or where a run holds the setup through the edit refusal, whose key the
        // predicate must answer with
        for (int at = 0; (at = opener.indexOf("I18n.t(" + QUOTE, at)) >= 0; at++)
        {
            int from = at + ("I18n.t(" + QUOTE).length();
            int to = opener.indexOf(QUOTE, from);

            if (to > from) refusals.add(opener.substring(from, to));
        }

        if (opener.contains("refuseWhileARunHoldsTheSetup(")) refusals.add("RunGates.NO_EDITS_WHILE_RUNNING");

        // THREE LITERALS AND ONE INDIRECTION (OB-126): the fourth refusal goes through `reasonLayoutIsNotEditable()`,
        // since the Edit button has three causes of grayness.  The count exists to notice a refusal being added or
        // removed.
        assertTrue(refusals.size() >= 3,
            "openLayoutEditor shows fewer messages than the refusals it had when this was written, "
            + "so what this test is matching against has changed shape: " + refusals);

        assertTrue(opener.contains("reasonLayoutIsNotEditable()"),
            "openLayoutEditor no longer asks which of the Edit button's three reasons applies, so "
            + "either a refusal has gone or it is back to naming one reason whatever is true "
            + "(OB-126)");

        // And the case that indirection covers still has an answer in the menu's predicate.
        assertTrue(predicate.contains("autosetup.ui.errorEditorAlreadyOpen"),
            "openLayoutEditor can still refuse because an editor is open, and "
            + "whyAutonomyEditorCannotOpen no longer has anything to say about it - the shortcut "
            + "would be live and then complain");

        for (String key : refusals)
        {
            assertTrue(predicate.contains(key),
                "openLayoutEditor refuses with \"" + key + "\" and whyAutonomyEditorCannotOpen has "
                + "nothing to say about it. The menu item that offers the editor is greyed by that "
                + "method, so this refusal is one the user meets as an error box after pressing a "
                + "live item - which is the state FR-026 asked for the item NOT to be in");
        }

        String setup = codeOf("private void addSetupMenu()");

        assertTrue(setup.contains("whyAutonomyEditorCannotOpen()"),
            "the setup menu no longer asks the window whether the editor would open, so whatever "
            + "greys the shortcut now is a second copy of the rule");

        // Asking is not enough: the answer has to decide.  The variable is read back rather than matched by a literal
        // name.
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
     * The covered-track wash survives a highlight, stays out of the editors, and blocks what it draws.
     *
     * Each once had a green test over the RULE while nothing asserted the DRAWING:
     * - a highlight cannot take the mark away (MT-309): both marks are PAINTED over the icon, never tinted into it;
     * - there is exactly ONE wash, the painted one: the same tile with more transparency, applied in `paintComponent`
     *   as the icon is painted and before the train mark;
     * - coverage is per edge for routing and per square for drawing, which differ at a switch, so a path over
     *   track that shares metal with a covered edge is refused;
     * - only the track diagram viewer draws it.
     * Source-level, because it is all about what a Swing component draws.
     *
     * MUTATION: deleting the `paintCoveredMark` call, dropping the `edit` guard in `coveredRoads`, removing the lock
     * sweep in isPathClear, fading without asking `isTrackBlocked`, or fading after the train mark rather than before
     * it, each fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheCoveredWashIsDrawnWhereItShouldBe() throws Exception
    {
        // The program's code, so each mark is found wherever the tile's drawing goes
        String label = SourceOf.allCode();

        // THE HIGHLIGHT CANNOT TAKE THE MARK WITH IT (MT-309): the mark is PAINTED over the icon, so the next paint
        // draws the train on top of whatever icon the restore puts back.  THE TIMER IS A FIELD, because a flash
        // starting over it has to be able to end it.
        int timer = label.indexOf("new javax.swing.Timer(HIGHLIGHT_DURATION");

        assertTrue(timer >= 0,
            "the highlight timer has gone, so this checked the absence of something that is not there");

        assertTrue(label.contains("paintCoveredMark(mark)"),
            "nothing paints the train mark, so a covered square is drawn as ordinary track and the"
            + " transient highlight has nothing left to overwrite because there was never anything"
            + " there (Adam, 2026-09-07 and MT-309)");

        // AND THE WASH IS THE PAINTED ONE, DRAWN UNDER THE LINE.  It is FENCED, on the window's answer rather than on
        // anything the tile works out ("can we just grey out the tiles just like blocked edges WHILE AUTONOMY IS
        // RUNNING"), and IN `paintComponent` (MT-278 ITEM 1).  Adam: *"I want the shading to instead be the same tile
        // with more transparency exactly like what happens when edges are locked in autonomy mode."*  A fade applies to
        // the icon AS IT IS PAINTED, which only the painting method can do; a fill drawn afterwards cannot.
        String painting = SourceOf.withoutComments(memberOf("public final class LayoutLabel extends JLabel",
            "protected void paintComponent(java.awt.Graphics g)"));

        assertTrue(painting.contains("tcUI.isTrackBlocked(square)"),
            "the refusal mark is drawn without asking whether the square is blocked, so it either"
            + " never appears or appears on free track");

        // AND IT IS APPLIED TO THE ICON, NOT PAINTED OVER IT, which is what makes it survive a highlight.
        assertTrue(painting.contains("AlphaComposite.getInstance(")
            && painting.contains("BLOCKED_ALPHA"),
            "nothing fades the square while the railway refuses it. Adam asked for \"the same tile"
            + " with more transparency\", which is a composite set before `super.paintComponent` -"
            + " not a colour drawn after it");

        // And it is UNDER everything autonomy draws, so a square that is both still reads as both.
        int faded = painting.indexOf("BLOCKED_ALPHA");
        int mark = painting.indexOf("paintCoveredMark(mark)");

        assertTrue(faded > 0 && mark > 0 && faded < mark,
            "the fade is applied after the train mark rather than before it, so the square carrying"
            + " the train is the one square whose train is drawn faint");

        // AND THERE IS ONLY ONE OF IT: bringing the wash back as a tinted icon is the version an accessory highlight
        // can take away with it (the first report above).  The helper was ImageUtil's; a tinting helper anywhere is a
        // second way.
        assertFalse(SourceOf.allCode().contains("addCoveredOverlay("),
            "there are two ways of drawing the covered wash again - one painted over the icon and one"
            + " tinted into it. The tinted one is the version a transient highlight overwrites, which"
            + " is Adam's report of 2026-09-07");

        // AND THE MARK IS THE VIEWER'S ONLY.
        assertTrue(label.contains("if (edit || square == null"),
            "the train mark is drawn in the editors too. An editor is where the railway is arranged,"
            + " and what happens to be standing on it while you arrange it is a fact about right now"
            + " (Adam: \"only the track diagram viewer\")");

        // AND WHAT IS DRAWN IS WHAT IS REFUSED.
        assertTrue(codeOf("private boolean isPathClear(").contains("for (Edge sharing : e.getLockEdges())"),
            "a path over track that SHARES metal with a covered edge is still allowed. Coverage is"
            + " recorded per edge and drawn per square, and at a switch those differ - so the picture"
            + " protected more than the railway did, and a train could run onto a shaded switch");
    }

    /**
     * The arrival side is written and read in ONE vocabulary, the BUILD's (OB-182).
     *
     * `arrivedFrom` is written by the arrival and three operator doors and consumed by the walk that blocks the track
     * behind a standing train, so they must agree what "N" means.  The geometry (`Layout.sideTowards`) answers by the
     * compass direction of the neighboring POINT, which on a reduced run that turns corners is wrong; the build's
     * answer is the reduced edge's entry side.  So `AutonomyBuilder` writes `entrySide` into each emitted edge,
     * `parseAuto` reads it onto the `Edge`, and `Layout.entrySideOf` is the one definition both the arrival write and
     * the tail walk call.
     *
     * MUTATION: pointing either reader back at `sideTowards`, or handing either door `null` instead of
     * the session's arrival sides, fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheArrivalSideIsWrittenAndReadInOneVocabulary() throws Exception
    {
        // The program's code, so a reader or a writer is found in whichever file holds it
        String layout = SourceOf.allCode();

        // ONE DEFINITION, and both readers call it.
        assertTrue(layout.contains("public String entrySideOf(Edge edge, Point at)"),
            "the shared definition of which side an edge comes in by has gone, so the arrival write and"
            + " the tail walk are each working it out again - which is the state that blocked no track"
            + " on any curve (OB-182)");

        assertTrue(layout.contains("arrived.setArrivedFrom(entrySideOf(path.get(path.size() - 1), arrived));"),
            "the arrival no longer writes the shared answer, so what it stores and what the walk looks"
            + " for can differ again");

        assertTrue(layout.contains("String cameInBy = owner.entrySideOf(candidate, here);"),
            "the tail walk no longer reads the shared answer, so a side written correctly by the"
            + " arrival matches nothing and the track behind the train stays open");

        // AND THE BUILD ACTUALLY SUPPLIES IT, or entrySideOf falls back to the geometry everywhere and
        // the three assertions above are satisfied by a program that has changed nothing.
        assertTrue(layout.contains("json.put(\"entrySide\", edge.getEntrySide().name())"),
            "the builder no longer writes the entry side into the configuration, so every edge falls"
            + " back to the geometry and the fix is inert while looking present");

        assertTrue(layout.contains("e.setEntrySide(edge.getString(\"entrySide\"))"),
            "the configuration carries the entry side and parseAuto does not read it, so the model"
            + " never sees it");

        // AND THE DOORS OFFER IT.  A door handing null gets the geometric list, which is the answer
        // Adam reported as wrong.
        assertTrue(layout.contains("getAutonomySession().unbarredArrivalSides(aimed)"),
            "the paste door offers the geometric sides again - the side a NEIGHBOUR lies on rather than"
            + " the side the track comes in by, which is OB-182 as reported; and since OB-204 the sides"
            + " a train may actually have ARRIVED by, which leaves out the ones the operator has barred");

        assertTrue(layout.contains("sidesOf(editor.session.unbarredArrivalSides(target))"),
            "the arrived-from menu offers the geometric sides again, so it and the paste prompt can"
            + " name different sides for the same square - and a side the operator barred is not one a"
            + " train arrived by at all (OB-204)");
    }
    /**
     * One menu holds both ends of the train, headed, and the viewer gets it too.
     *
     * Adam's rule: the "arrived from" setting lives in the '<locomotive> is facing' menu for both surfaces,
     * *"with clear intro headings for each set of options."*
     *
     * `testBothAutonomyMenusNameTheSquareTheSameWay` checks the two menus agree about a square's name, not about
     * membership.  The viewer's route is `TrainControlUI.facingMenuFor`, which asks for the facing menu and nothing
     * else, so this asserts what that menu contains.  One menu also removes the ability to drift.
     *
     * MUTATION: adding the tail menu separately in the editor again fails the last assertion.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheFacingMenuHoldsBothEndsOfTheTrain() throws Exception
    {
        String facing = codeOf("    javax.swing.JMenu buildFacingMenu(final TileKey target)");

        assertTrue(facing.contains("buildArrivedFromMenu(target)"),
            "the facing menu does not carry the arrival side, so the track diagram viewer - which asks"
            + " for this menu and no other - still cannot set it (Adam, 2026-09-07)");

        assertTrue(facing.contains("headingFacing") && facing.contains("headingArrivedFrom"),
            "one or both sets of compass points has no heading. Two sets one after another read as"
            + " eight ways of saying one thing, and they are opposite ends of the train");

        // AND IT IS NOT ALSO ADDED SEPARATELY, which is what put it in one surface and not the other.  Asked of the
        // whole program: this once read `addLocomotiveItems` only when that method existed, and the claim stopped
        // running.
        String code = SourceOf.allCode();

        assertFalse(code.contains("menu.add(tailMenu)"),
            "the arrival side is still added as its own submenu as well, so there are two doors"
            + " again and only one of them is on the diagram");

        assertFalse(code.contains("javax.swing.JMenu tailMenu = buildArrivedFromMenu(target);"),
            "the editor still builds a separate tail submenu, which is the arrangement that gave the"
            + " editor a setting the viewer did not have");
    }

    /**
     * The home picker offers the train in your hand AND the one on the platform.
     *
     * Adam: *"in the home locomotive assignment box, 'use current' should be the train currently parked at
     * the station.  Rename the current behavior to 'use active', and add a second button for 'use current'
     * if a train is parked there."*
     *
     * Each is offered only when its locomotive is in the list, so neither button can pick what the list would refuse,
     * and not twice when they are the same train.  Source-level: the picker is a modal dialog, a hang in a test.
     *
     * MUTATION: dropping the `names.contains` guard on either button fails this; dropping the
     * `!useActive.equals(useParked)` clause fails the last assertion.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheHomePickerOffersBothTrains() throws Exception
    {
        String picker = codeOf("static String pickLocomotive(java.awt.Component owner,");

        assertTrue(picker.contains("btnUseParkedLocomotive"),
            "the picker offers no way to choose the train parked at the station, which is the button"
            + " Adam asked for and the more common of the two gestures");

        assertTrue(picker.contains("btnUseActiveLocomotive"),
            "the picker no longer offers the locomotive being driven. That shortcut was not replaced,"
            + " it was renamed - the old label said \"use current\" and meant this");

        assertFalse(picker.contains("btnUseCurrentLocomotive"),
            "the old \"use current\" label is still used for the ACTIVE locomotive, which is the"
            + " naming Adam asked to have corrected - current is the train on the platform");

        // NEITHER BUTTON CAN PICK WHAT THE LIST WOULD REFUSE.
        assertTrue(picker.contains("useParked != null && names.contains(useParked)"),
            "the parked shortcut is offered without checking the list contains that locomotive, so it"
            + " can choose something the picker itself refuses");

        assertTrue(picker.contains("useActive != null && names.contains(useActive)"),
            "the active shortcut lost the list check it has always had");

        // AND NOT TWICE FOR ONE TRAIN.
        assertTrue(picker.contains("!useActive.equals(useParked)"),
            "driving the locomotive you are standing at offers two buttons that do the same thing,"
            + " which is the ordinary case rather than an edge one");

        // THE HOME DOOR SUPPLIES BOTH.
        String home = codeOf("    void promptHome(TileKey tile)");

        assertTrue(home.contains("locomotiveAt(tile)"),
            "the home dialog does not ask what is parked at this square, so its new button can never"
            + " appear");
    }

    /**
     * The station label and the facing menu answer from one place (OB-181).
     *
     * The label reads which COPY of a split square the train stands on, which IS its direction however it got there;
     * the stored facing is written only when somebody places a train BY HAND, so a menu reading it would disagree for
     * every train that has moved since.  `AutonomySession.facingOnTheRailway` is the one reading, the stored facing the
     * fallback (an empty square has no train).  The WRITE was already shared through `setFacingAndMove`; this closes
     * the last half.
     *
     * MUTATION: pointing either surface back at `getFacing(target)` alone fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheLabelAndTheMenuAgreeAboutFacing() throws Exception
    {
        // Gone, `SourceOf.declaration` fails: label and menu would each be working it out again.
        SourceOf.declaration("public Side facingOnTheRailway(TileKey square,");

        String panel = codeOf("    javax.swing.JMenu buildFacingMenu(final TileKey target)");

        assertTrue(panel.contains("session.facingOnTheRailway(target, showing)"),
            "the facing menu reads the stored value again rather than the railway, so it disagrees with"
            + " the station label for every train that has moved since it was placed (OB-181)");

        assertTrue(SourceOf.allCode().contains("session.facingOnTheRailway(square,"),
            "the station label works out the facing itself again, so the two surfaces can drift apart"
            + " the way they did before");

        // AND THE WRITE STAYS SHARED.  Reading from one place while writing to two would put them back
        // out of step on the first change.
        assertTrue(panel.contains("session.setFacingAndMove(target, facing)"),
            "the facing menu no longer writes through setFacingAndMove, so setting a facing updates the"
            + " setup without moving the train to the copy that holds it - and the menu disagrees with"
            + " the label again the moment it is used");
    }

    /**
     * A rebuild does not move the trains, and closing the editor writes the file (OB-183).
     *
     * Adam's rulings: the railway wins (**"option 1"**), and **"on exit or editor load or editor close, save
     * to the setup file. this is how it was in 2.8.x."**
     *
     * The rebuild regenerates every placement from the setup, safe only while the setup knows where the trains are:
     * `openLayoutEditor` captures on the way in, which stops being true once anything moves a train.  Placements only:
     * folding the whole running layout back would delete a declined edit.
     *
     * MUTATION: dropping either helper from the rebuild restores the teleport; dropping the capture from the close
     * leaves the file saying where the trains started until the next open.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testARebuildLeavesTheTrainsWhereTheyAre() throws Exception
    {
        String rebuild = codeOf("    void rebuildRunningLayoutFromSetup(boolean sayIfDeclined,");

        int read = rebuild.indexOf("whereTheTrainsAre()");
        int loaded = rebuild.indexOf("getAutonomyViewerPanel().load(");
        int back = rebuild.indexOf("putTheTrainsBack(");

        assertTrue(read > 0 && loaded > 0 && back > 0,
            "the rebuild no longer carries the placements across, so every train goes back where the"
            + " setup last said it was - which is the teleport Adam reported (OB-183)");

        // THE ORDER, because each half is useless in the wrong place: reading after the load reads the
        // answer the load just invented, and putting back before it is overwritten by the load itself.
        assertTrue(read < loaded && loaded < back,
            "the placements are read or restored on the wrong side of the reload, so the rebuild either"
            + " records what it just overwrote or is overwritten by it");

        // AND THE THIRD SAVE MOMENT.  Exit, editor open and editor close all call captureRunningLayout, so
        // the file always says where the trains are.
        String closed = codeOf("public void autonomyEditorClosed()");

        assertTrue(closed.contains("captureRunningLayout()"),
            "closing the editor no longer writes the setup file, so where the trains are is lost until"
            + " something else captures - Adam: \"on exit or editor load or editor close\" (OB-183)");

        assertTrue(closed.indexOf("rebuildRunningLayoutFromSetup()")
            < closed.indexOf("captureRunningLayout()"),
            "the close captures BEFORE it rebuilds, so it folds a layout that predates the edit over the configuration and removes what that layout does not carry - which is how a declined edit was silently deleted (ACC-B3)");
    }

    /**
     * Moving an arrow redraws the arrows, not the whole diagram (OB-185).  A direction restriction changes no tile art,
     * so the panel has a light redraw door beside the heavy one, which rebuilds the grid and flashes.  The running
     * layout is still rebuilt, since a restriction is a real change to the graph.
     *
     * MUTATION: pointing the one-way branch back at `setupChanged()` fails this, and so does an `annotationsChanged`
     * that reaches for `onDiagramChanged`.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testAnArrowChangeDoesNotRebuildTheDiagram() throws Exception
    {
        // Gone, `SourceOf.declaration` fails: the light redraw door has gone (OB-185).
        String light = codeOf("private void annotationsChanged()");

        assertTrue(light.contains("onAnnotationsChanged.run()"),
            "the light door does not refresh the annotations, so moving an arrow draws nothing");

        assertTrue(light.contains("rebuildRunningLayoutSoon()"),
            "the light door does not rebuild the running layout. A one-way restriction is a real change"
            + " to the graph, and a diagram agreeing with a railway that has moved on is the defect the"
            + " heavy door exists to prevent - the point was to stop the FLICKER, not the rebuild");

        // AND THE ONE-WAY TOOL USES IT: the door existing and nothing calling it is what a refactor leaves behind.
        assertTrue(SourceOf.allCode().contains("if (changed >= 0) annotationsChanged();"),
            "the one-way tool does not use the light door, so the flicker Adam reported is still there"
            + " on the one tool that is used many times in a row (OB-185)");
    }

    /**
     * A hover's border pass asks the grid setting once, before its loop over the squares.  The setting is a preference,
     * and on Windows every read is an uncached registry round trip (22 microseconds); asked per square it cost 16 ms a
     * hover over the main page's 431 squares.  Pinned as source: the preferences are a static final the JIT may fold,
     * so the reads cannot be counted from a test.
     *
     * MUTATION: ask the setting per square again, and this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testAHoverAsksTheGridSettingOncePerPass() throws Exception
    {
        String pass = codeOf("    void clearBordersFromChildren(JPanel panel)");

        int loop = pass.indexOf("for (java.awt.Component component : panel.getComponents())");

        assertTrue(loop > 0, "the border pass no longer loops over the panel's squares, so this checked nothing");

        assertFalse(pass.substring(loop).contains("showGrid()") || pass.substring(loop).contains("restingBorder("),
            "the border pass asks the grid setting for every square - a registry read on Windows, on every square the"
            + " pointer enters");

        assertTrue(pass.substring(0, loop).contains("showGrid()"), "the border pass does not ask the grid setting before"
            + " its loop, so the squares are not put back to it");
    }

    /**
     * A redraw of the editor's diagram asks the grid setting once, not once per square: `LayoutGrid` reads it once a
     * build and hands it to each square.  Pinned as source, as the hover's pass is
     * (`testAHoverAsksTheGridSettingOncePerPass`).
     *
     * MUTATION: let the grid make its squares without the setting again, and this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testARedrawAsksTheGridSettingOnce() throws Exception
    {
        String grid = codeOf("public LayoutGrid(LayoutDiagram layout, int size, JPanel parent, Container master, "
            + "boolean popup, TrainControlUI ui)");

        // ONCE A BUILD, before the squares are made (the ruler's `coordinatesVisible` asks it too, once, for itself)
        int asked = grid.indexOf("final Boolean gridLines = inEditor ? LayoutEditor.showGrid() : null;");

        int made = grid.indexOf("new LayoutLabel(c, master, size, ui, inEditor, gridLines)");

        assertTrue(asked > 0 && made > asked, "the grid does not ask the setting once, before it makes its squares");

        assertTrue(grid.contains("new LayoutLabel(c, master, size, ui, inEditor, gridLines)"), "the grid makes its squares"
            + " without handing them the setting, so each asks it for itself");

        assertFalse(SourceOf.allCode().contains("new LayoutLabel(c, master, size, ui, inEditor)"), "the grid still makes a square that"
            + " asks the setting for itself");
    }

    /**
     * The main window's marks ask the one-way arrows option once a pass, not once per square: a pass describes every
     * square, and a click in the autonomy editor makes several (measured at a fifth of the click).  The pass reads it,
     * and the green arrows' option (FR-110), before it asks the session for the marks, and hands both over.  Pinned as
     * source, as the grid setting is (`testARedrawAsksTheGridSettingOnce`).
     *
     * MUTATION: let the pass ask for the marks without either option, or the session or a square ask one again, and
     * this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheMarksAskTheArrowsOptionOncePerPass() throws Exception
    {
        String pass = codeOf("public void showStaticAutonomyLayer(boolean show)");

        int loop = pass.indexOf("session.staticAnnotations(marked, ");

        assertTrue(loop > 0, "the marks are no longer asked of the session for the railway's squares, so this checked"
            + " nothing");

        for (String option : new String[] {"diagramShowsRestrictionArrows()", "diagramShowsAllowedDirections()"})
        {
            assertTrue(pass.substring(0, loop).contains(option), "the marks do not ask " + option + " before they are"
                + " asked for");

            assertFalse(pass.substring(loop).contains(option), "the marks ask " + option + " for every square - a read"
                + " of the Windows settings, several passes a click");
        }

        assertTrue(pass.substring(loop).startsWith("session.staticAnnotations(marked, arrows, allowedToo)"), "the marks"
            + " are asked for without the options, so they are read again for each square");

        // The session's pass is SessionRuns' (FR-136); AutonomySession's is a one-line call to it, and both are read
        String all = codeOf("public Map<TileKey, TileAnnotation> staticAnnotations(")
            + SourceOf.withoutComments(memberOf("final class SessionRuns", "Map<TileKey, TileAnnotation> staticAnnotations("));

        assertFalse(all.contains("diagramShowsRestrictionArrows()") || all.contains("diagramShowsAllowedDirections()")
            || all.contains("staticAnnotationFor(tile)"), "the session's pass asks an option for itself though it was"
            + " handed both");

        String square = codeOf("public TileAnnotation staticAnnotationFor(TileKey tile, boolean arrows)");

        assertFalse(square.contains("diagramShowsRestrictionArrows()"), "a square's annotation asks the arrows option for"
            + " itself though it was handed it");
    }

    /**
     * Only the session, and its import for one flag, write the session's state.  `AutonomySession`'s `store`, `graph`,
     * `reducer` and `dirty`, and `CARRIED_SETTINGS`, are package-private for the classes extracted from it, which
     * leaves them open to the whole package: `rebuild` alone publishes `reducer` (OB-298), `graph` and `store` are the
     * session's to replace, the import's `owner.dirty = true` is the only outside assignment of `dirty`, and nothing
     * calls `CARRIED_SETTINGS.set(`.
     *
     * MUTATION: have any other class assign `owner.graph`, `owner.reducer` or `owner.store`, assign `owner.dirty` from
     * anywhere but SessionImport, or call `CARRIED_SETTINGS.set(`, and this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testOnlyTheSessionAndItsImportWriteItsState() throws Exception
    {
        String code = SourceOf.allCode();

        // An assignment through another object (`owner.graph = `, `session.dirty |= `), not `==`; `this.` is a class
        // writing its own field.
        java.util.regex.Pattern held = java.util.regex.Pattern.compile("(?<!this)\\.\\s*(reducer|graph|store)\\s*[-+*/|&^]?=(?!=)");
        java.util.regex.Pattern flag = java.util.regex.Pattern.compile("(?<!this)\\.\\s*dirty\\s*[-+*/|&^]?=(?!=)");

        java.util.regex.Matcher each = held.matcher(code);

        boolean found = each.find();

        assertFalse(found, "something assigns the session's " + (found ? each.group(1) : "state") + " through another"
            + " object, near: " + (found ? code.substring(Math.max(0, each.start() - 60), each.end() + 30).replace('\n', ' ') : ""));

        int writes = count(flag, code);
        int ours = count(flag, codeOf("final class SessionImport"));

        assertTrue(ours > 0, "the import no longer sets the session's dirty flag, so this checked nothing");

        assertEquals(writes, ours, "something other than SessionImport assigns the session's dirty flag through another"
            + " object: " + writes + " in the program, " + ours + " of them in the import");

        assertEquals(SourceOf.occurrences("CARRIED_SETTINGS.set("), 0, "something rewrites CARRIED_SETTINGS, the list"
            + " every import shares");
    }

    /**
     * What a caption says is one choice, not three switches (FR-061).
     *
     * Adam: *"add a Text Labels label and dropdown right above Track Directions, with the following
     * options: Station Names, Parked Locomotives, Home Locomotives, and None.  Station Names should be
     * default, with the setting remembered between open."*
     *
     * The four options exclude one another, which finishes OB-174.  Checked on the CONTROL: the four states, the
     * default, and that the two internal flags never both set; where the label sits is asserted by source order.
     *
     * MUTATION: making CAPTIONS_PARKED the default fails the second assertion; setting both flags in
     * applyCaptionMode fails the exclusivity one.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testWhatACaptionSaysIsOneChoice() throws Exception
    {
        // The program's code (FR-136), so each part of the choice is found in whichever file holds it
        String panel = SourceOf.allCode();

        for (String key : new String[] { "captionsStations", "captionsParked", "captionsHomes",
            "captionsNone" })
        {
            assertTrue(panel.contains("autosetup.ui." + key),
                "the caption choice does not offer " + key + ". Adam named all four options, and one"
                + " missing is a state the operator can no longer ask for");
        }

        assertTrue(panel.contains("PREF_CAPTION_MODE"),
            "the caption choice is not remembered between opens, which Adam asked for explicitly");

        assertTrue(panel.contains("VIEW_PREFS.getInt(PREF_CAPTION_MODE, CAPTIONS_STATIONS)"),
            "the caption choice does not default to Station Names. This window is where a railway is"
            + " named, and Adam asked for that default by name");

        // THE TWO INTERNAL FLAGS ARE EXCLUSIVE BY CONSTRUCTION, which is what retires OB-174.
        // Gone, `SourceOf.declaration` fails: nothing turns the one choice into what the drawing code reads
        String applier = codeOf("private void applyCaptionMode()");

        assertTrue(applier.contains("showParkedTrains.setSelected(mode == CAPTIONS_PARKED)")
            && applier.contains("showHomeLocomotives.setSelected(mode == CAPTIONS_HOMES)"),
            "the two caption flags are not set from the mode, so they can both be on at once - which"
            + " is a state the dropdown cannot show and the operator cannot get out of (FR-061)");

        // THE TEXT SWITCH IS THE TEXT LABELS BOX'S, NOT THE DROPDOWN'S (FR-133): choosing a caption option
        // changes the captions and nothing else, and the box is applied by its own method.
        assertFalse(applier.contains("turnTextLabelsOff()") || applier.contains("turnTextLabelsOn()"),
            "the caption choice drives the diagram's text switch again. The Text Labels box under Show Trains"
            + " is the text's own setting (FR-133) and the dropdown only decides the captions");

        String textApplier = codeOf("private void applyTextLabels()");

        assertTrue(textApplier.contains("turnTextLabelsOff()") && textApplier.contains("turnTextLabelsOn()"),
            "the Text Labels box does not drive the diagram's text switch both ways");

        // AND AT OPEN AS WELL: the box is remembered between opens and the text switch is not, so skipping it at open
        // restored an unticked box over text still drawn.
        assertTrue(codeOf("public void applyRememberedCaptionMode()").contains("applyTextLabels()"),
            "the remembered Text Labels box is not applied to the diagram when the window opens, so it comes"
            + " back as a box saying one thing over a diagram drawing the other (RGD-C3)");

        // AND THE OTHER DIRECTION: the plain text switch moving ticks and unticks the box.
        assertTrue(panel.contains("public void textLabelsChanged(boolean shown)"),
            "nothing tells the Text Labels box the text switch moved, so the box goes on saying the"
            + " opposite of the diagram (RGD-C3, FR-133)");

        assertTrue(panel.contains("autonomyPanel.textLabelsChanged("),
            "the text toggle does not tell the autonomy panel, so the method above is never called and"
            + " the Text Labels box goes on saying the opposite of the diagram (RGD-C3)");

        // AND THE OLD COUPLING IS GONE; leaving it would be the thing a future reader copies.
        assertFalse(panel.contains("if (showParkedTrains.isSelected()) turnTextLabelsOn()")
            || panel.contains("if (showHomeLocomotives.isSelected()) turnTextLabelsOn()"),
            "a tick box still reaches for the text switch on its own. That coupling is what the one"
            + " choice replaces, and two mechanisms for it is worse than the one it had");

        // WHERE IT SITS: right above Track Directions, in the window that mounts it.
        String editor = codeOf("public void setAutonomyMode(org.traincontrol.automationui.AutonomySession session)");

        int captionsAt = editor.indexOf("visibility.add(captionChoice)");
        int directionsAt = editor.indexOf("visibility.add(directionsLabel)");

        assertTrue(captionsAt > 0 && directionsAt > 0,
            "the sidebar no longer mounts both controls, so the ordering below is comparing this test"
            + " to itself");

        assertTrue(captionsAt < directionsAt,
            "the caption choice is not above Track Directions, which is where Adam asked for it");
    }

    /**
     * The tail can be set, and not un-set.
     *
     * Adam: **"Given the other items above, when would we not know?  Clearing should not be possible, only
     * setting."**
     *
     * A wrong answer is corrected by picking the right side; clearing only offered a way to turn the blocking off,
     * which is not a fact about the railway.  The automatic clear stays: `Point.setLocomotive` drops the side when the
     * occupant changes, the railway retracting a fact it no longer holds.
     *
     * MUTATION: restoring the null-writing radio fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheTailCanBeSetAndNotUnset() throws Exception
    {
        String menu = codeOf("    javax.swing.JMenu buildArrivedFromMenu(final TileKey target)");

        assertTrue(menu.contains("session.setArrivedFrom(target, side)"),
            "the menu no longer sets the arrival side at all, so the absence checked below is the"
            + " absence of a menu rather than the absence of a clear");

        assertFalse(menu.contains("setArrivedFrom(target, null)"),
            "the menu still offers a way to clear the arrival side. Adam, 2026-09-07: \"clearing"
            + " should not be possible, only setting\" - a wrong answer is corrected by choosing the"
            + " right side, and what clearing offered was a way to switch the tail blocking off");

        assertFalse(menu.contains("on.setArrivedFrom(null)"),
            "the menu clears the side on the running layout, so the setup and the railway now"
            + " disagree about whether clearing is possible at all");

        // AND IT SHOWS WHAT IS RECORDED, even when the geometry does not offer it: the radios are a ButtonGroup ticked
        // by equality, so a recorded side missing from the list ticks NOTHING and the section reads "no tail recorded"
        // while that side goes on steering the tail walk (OB-177 for the sibling menu).
        assertTrue(menu.contains("recorded != null && !sides.contains(recorded) ? recorded : null"),
            "the arrived-from menu drops a recorded side the geometry does not offer, so it opens with"
            + " every choice blank for a square that HAS a tail recorded - a value still steering the"
            + " tail walk, unseen (RGD-C4)");

        // SHOWN, NOT OFFERED.  The tail walk matches stored sides against the geometry, so a side the geometry
        // does not have can never match: offering it is offering a choice guaranteed to do nothing.
        assertFalse(menu.contains("sides.add(recorded)"),
            "a recorded side the track does not have is offered as a choice again. It can never match"
            + " the tail walk, so setting it blocks nothing - it is a statement about the square, and"
            + " belongs in the menu disabled (RGD-C4)");

        assertTrue(menu.contains("gone.setEnabled(false)"),
            "the recorded-but-absent side is not disabled, so it reads as an answer the operator may"
            + " choose rather than as a fault to correct (RGD-C4)");
    }

    /**
     * One reader of a placement, because the two disagreed about the shape.  A placement is normally an object, but a
     * hand-edited or older `autonomy.json` can hold a BARE STRING, which `getLocomotiveNameAt` has always handled.
     * `nameOfPlacedLocomotive` (the occupant-change guards) has to read it too: null is "nobody here", so re-placing a
     * train on its own square would read as a CHANGE of occupant and throw away an arrival side that was still true.
     *
     * MUTATION: restoring the `instanceof JSONObject` early return fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testOneReaderOfAPlacement() throws Exception
    {
        String namer = codeOf("private static String nameOfPlacedLocomotive(Object placement)");

        assertFalse(namer.contains("if (!(placement instanceof org.json.JSONObject)) return null"),
            "the occupant guard answers null for a placement stored as a bare string, so a train"
            + " re-placed on the square it is already standing on reads as a change of occupant and"
            + " loses an arrival side that is still true (RGD-C5)");

        assertTrue(namer.contains("String.valueOf(placement)"),
            "the shared reader no longer handles the bare-string placement shape that"
            + " getLocomotiveNameAt was written to handle, so the two readers of one shape disagree"
            + " again (RGD-C5)");

        String byTile = codeOf("public String getLocomotiveNameAt(TileKey tile)");

        assertTrue(byTile.contains("nameOfPlacedLocomotive(getPointProperty(tile, \"loc\"))"),
            "the by-square reader has its own copy of the shape knowledge again. There were three"
            + " before and they did not agree; the point of the fix was that there is one (RGD-C5)");
    }
    /**
     * Dragging a locomotive puts its name on the clipboard, and cannot fail because of it.
     *
     * The write is deliberate (Adam: **"c26 is deliberate, that is the dragging functionality you implemented on the
     * keyboard."**), so it is pinned, not removed.  The guard matters: `setContents` throws `IllegalStateException`
     * while another application holds the clipboard, and uncaught it would leave the mouse handler after a cut had
     * emptied the source key and before the paste, deleting a mapping.  The gesture itself is tested in that class;
     * this asserts that the write is there and cannot take the drag down.
     *
     * MUTATION: removing the try fails the second assertion; removing the write fails the first.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheDragPutsTheNameOnTheClipboardSafely() throws Exception
    {
        String drag = codeOf("public void setCopyTarget(JButton button, boolean cut)");

        int wrote = drag.indexOf("getSystemClipboard()");

        assertTrue(wrote >= 0,
            "starting a drag no longer puts the locomotive name on the system clipboard. That is"
            + " deliberate - Adam, 2026-09-07: \"c26 is deliberate, that is the dragging"
            + " functionality you implemented on the keyboard\" - so removing it is a regression,"
            + " not a tidy-up");

        // THE CATCH IS AFTER THE WRITE, and names what it tolerates: "a try somewhere above" is satisfied by any
        // unrelated try earlier in the method.
        int caught = drag.indexOf("catch (IllegalStateException", wrote);

        assertTrue(caught > wrote,
            "the clipboard write is not wrapped in a catch for the clipboard being unavailable. It"
            + " throws whenever another application is holding it, and it runs AFTER a cut has emptied"
            + " the source key - so a transient lock on somebody else's clipboard deletes a mapping"
            + " (C26, IND9-C5)");
    }
    /**
     * The Sync menu comes back even when the sync does not.  doSync grays Sync and the functions menu and re-enables
     * them when the worker finishes, with no finally: a RuntimeException out of syncWithCS2 would leave both grayed for
     * the rest of the session.  Source-level, since a test cannot make the Central Station misbehave.
     *
     * MUTATION: removing the catch around syncWithCS2 fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testAFailedSyncGivesTheMenusBack() throws Exception
    {
        String sync = codeOf("public void doSync(Component c)");

        int call = sync.indexOf("syncWithCS2()");
        int back = sync.indexOf("syncMenuItem.setEnabled(true)");

        assertTrue(call >= 0 && back >= 0,
            "doSync no longer both runs the sync and re-enables the menu, so the ordering below is"
            + " comparing this test to itself");

        assertTrue(sync.substring(0, call).contains("try"),
            "the call to syncWithCS2 is not inside a try. If it throws, the worker thread dies and"
            + " Sync and the functions menu stay greyed until the application is restarted (C23)");

        // WHAT THE CATCH DOES, not that a catch exists: `catch (RuntimeException e) { log(e); return; }` would restore
        // the grayed menus with this test green.  The failure code is what carries the operator to the log.
        assertTrue(sync.substring(call, back).contains("syncResult = -1"),
            "nothing between the sync and the re-enable turns a failure into the code this method"
            + " knows how to report. A catch that logs and returns leaves Sync and the functions menu"
            + " greyed until the application is restarted, which is C23 unfixed (IND9-C5)");
    }
    /**
     * Nothing that only draws an accessory may create one.  `getAccessoryState` registers a switch when the address is
     * unknown, right on a path about to command the thing and wrong elsewhere; the window's two keyboard paints called
     * it, and opening a keyboard page registered sixty-four switches that persisted.  Asserted as an ABSENCE across the
     * whole window, since a check listing the sites it knows would report clean about a third.  Source-level because
     * the fault is which of two near-identical methods a paint calls.
     *
     * MUTATION: restoring either call to getAccessoryState fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testDrawingAnAccessoryDoesNotCreateOne() throws Exception
    {
        // Asked by what each call is made on, since the registering form has callers that are not paints: the window's
        // paints called it on `model`, a route condition on `control`.  What may remain are the declarations, a
        // locomotive waiting for an accessory it has commanded, the locomotive's delegation to its station, and the API
        // example.
        int registering = SourceOf.occurrences("getAccessoryState(")
            - SourceOf.occurrences("boolean getAccessoryState(")
            - SourceOf.occurrences("this.getAccessoryState(id, type)")
            - SourceOf.occurrences("this.network.getAccessoryState(id, type)")
            - SourceOf.occurrences("data.getAccessoryState(");

        assertEquals(registering, 0,
            "something calls getAccessoryState, which REGISTERS a switch when the address is unknown."
            + " Every use in the window is a paint - use getAccessoryStateIfPresent, which gives the same"
            + " answer and invents nothing (C13)");

        assertTrue(SourceOf.allCode().contains("model.getAccessoryStateIfPresent("),
            "the window no longer reads accessory state at all, so the check above passes for the"
            + " wrong reason - it is now asserting that a call that is not there is not there");

        assertFalse(codeOf("public static boolean evaluate(RouteCommand rc, ViewListener control)")
                .contains("getAccessoryState("),
            "evaluating a route condition registers the accessory it asks about. Conditions are"
            + " evaluated on a timer while a route waits, so one condition on a mistyped address makes"
            + " a permanent switch nobody created (C13)");
    }
    /**
     * A dismissed arrival-side question leaves the railway exactly as it was: "Simply don't place the train, leave it
     * on the clipboard as if no paste had been done."
     *
     * An ORDERING rule: the answer has to be in hand before `moveLocomotive` runs, or the train is already lifted off
     * its old square and closing the dialog would place it anyway without recording which way round.  Source-level,
     * since a test driving the model sees both statements already done.  The null reads two ways: on a square trains
     * may turn at it means dismissed; elsewhere only "nothing to record", and refusing that would refuse every paste
     * onto plain track.
     *
     * MUTATION: moving the forPlacement call back below moveLocomotive fails the ordering assertion; dropping the
     * mayTurnHere half of the guard fails the last one.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testADismissedArrivalQuestionPlacesNothing() throws Exception
    {
        // Renamed, `SourceOf.declaration` fails: point it at whatever handles a paste onto the diagram
        String drop = codeOf("private boolean locomotiveGestureOnDiagram(int keyCode, boolean controlPressed)");

        int asked = drop.indexOf("ArrivalSidePrompt.forPlacement(");
        int moved = drop.indexOf("moveLocomotive(placing.getName()");

        assertTrue(asked >= 0,
            "the drop no longer asks which side the train came in by, so a train put down on a square"
            + " trains may turn at gets a guessed tail on a square where guessing is what fails");

        assertTrue(moved >= 0,
            "the paste no longer moves the locomotive here, so the ordering below is comparing this"
            + " test to itself");

        assertTrue(asked < moved,
            "the arrival-side question is asked AFTER the train has been moved, which is the defect:"
            + " a dismissal then has nothing left to decline. Adam: \"simply don't place the train,"
            + " leave it on the clipboard as if no paste had been done\"");

        // AND THE ANSWER MUST ACTUALLY STOP IT.  Asking early and moving anyway would satisfy the
        // ordering above and still place the train.
        String between = drop.substring(asked, moved);

        assertTrue(between.contains("return"),
            "nothing between the question and the move can abandon the paste, so the answer is asked"
            + " early and then ignored - the train lands either way");

        // AND IT ASKS THE PROMPT'S OWN QUESTION.  `mayTurnHere` alone is not enough: forPlacement
        // answers null in three situations and only one of them is a dismissal.
        assertTrue(between.contains("ArrivalSidePrompt.wouldAsk("),
            "the abandon guard decides for itself whether a question was asked, instead of asking the"
            + " prompt. forPlacement returns null when it declined to ask as well as when it was"
            + " dismissed, and treating those alike refuses pastes onto squares nobody was ever asked"
            + " about (IND9-B5)");

        // AND THE OLD SITE IS GONE.  Two doors asking the same question is how the facing menu drifted
        // (OB-039), and here the second one would be the un-undoable copy.
        assertFalse(codeOf("private void rememberPlacement(").contains("forPlacement("),
            "rememberPlacement asks the arrival-side question again. It runs after the move, so that"
            + " copy is the one a dismissal cannot undo - which is the whole defect, kept");
    }
    /**
     * Both autonomy menus head themselves with a name, and it is the same name (OB-112).  The diagram's menu opens with
     * the same bold, disabled name the editor's does.  The naming sits on the session and neither menu has its own
     * copy; the heading goes on AFTER the emptiness test, since a menu with nothing to offer must stay unshown rather
     * than become a gray box with a station name in it.
     *
     * MUTATION: inlining describeTile's body back into AutonomyEditorPanel fails the delegation check; moving
     * menu.headline() above the getComponentCount test fails the ordering one.
     */
    @Test
    public void testBothAutonomyMenusNameTheSquareTheSameWay() throws Exception
    {
        // Gone, `SourceOf.declaration` fails: every heading and caption item in the editor is named by it
        String describe = codeOf("    String describeTile(TileKey tile)");

        assertTrue(describe.contains("session.describeTile(tile)"),
            "AutonomyEditorPanel names squares itself again instead of asking the session. The "
            + "diagram\u2019s menu asks the session, so this is one square with two names in two "
            + "windows - which is the defect this whole file is about");

        assertFalse(describe.contains("isFeedback()"),
            "the sensor-address fallback is written out in the panel as well as in the session, so "
            + "there are two copies of the rule again and only one of them will get the next fix");

        assertTrue(codeOf("private void headline()").contains("session.describeTile("),
            "the diagram\u2019s menu names the clicked square some other way, so the heading OB-112 "
            + "asked for can disagree with the one in the editor");

        String show = codeOf("static void showFor(");

        int counted = show.indexOf("getComponentCount()");
        int headed = show.indexOf("headline()");

        assertTrue(counted >= 0 && headed > counted,
            "the heading goes on before the menu is known to have anything in it, so a right-click "
            + "with nothing to offer now opens a one-item grey box - which is what showFor was made "
            + "the only way in to prevent");
    }

    /**
     * The caption-hiding rule is asked about the right two things.  `LayoutGrid.hidesStationCaptions` has its truth
     * table checked elsewhere; this reads the call.  It must be asked whether this grid is in an editor AT ALL and
     * whether that editor is the autonomy one, not `layout.getEdit()` alone, the flag BOTH editors share for their
     * mutual exclusion.
     *
     * MUTATION: passing `layout.getEdit()` instead of `inEditor`, or dropping the autonomy-mode test, fails this.
     */
    @Test
    public void testTheCaptionHideRuleIsAskedWithTheRightTwoQuestions() throws Exception
    {
        // The program's code (FR-136), so the call is read wherever the grid's build goes
        String grid = SourceOf.allCode();

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
     * The s88 trigger door never puts a question on the screen.  Adam's ruling was "ask me, at the two human doors";
     * the third door has nobody at it, and a route fired by a sensor that raised a modal dialog would block its thread
     * forever and throw its turnout whenever somebody pressed OK.  The model-side tests reach this code with `getGUI()`
     * null, so the confirm branch passes for both values of `auto`.  Reads three things: the ask is gated on `!auto`,
     * the chained-route call passes `auto` on rather than a constant, and the door itself is still `execRoute(true)`.
     *
     * MUTATION: deleting `!auto &&`, or hard-coding false in the chained call, fails this.
     */
    @Test
    public void testTheS88DoorIsNeverAskedAnything() throws Exception
    {
        // The route's run, wherever it is (FR-136); renamed, `SourceOf.declaration` fails
        String route = codeOf("private void execRoute(boolean auto, int recursionLimit, boolean overrideConflicts)");

        int asks = route.indexOf("confirmRouteConflictMidway(");

        assertTrue(asks > 0, "nothing in MarklinRoute asks the mid-route question any more");

        // FOLLOW THE GUARD, NOT THE DISTANCE: a check of the 220 characters before the call reports on where the code
        // is written, not what it says (a gate named `askable` and moved out of the window is as strong).  Whatever
        // `askable` is defined as has to contain `!auto`, and the call has to be behind it.
        String around = route.contains("askable")
            ? route.substring(route.indexOf("askable ="),
                Math.min(route.length(), route.indexOf("askable =") + 220))
            : route.substring(Math.max(0, asks - 220), asks);

        assertTrue(route.substring(Math.max(0, asks - 220), asks).contains("askable")
                || route.substring(Math.max(0, asks - 220), asks).contains("!auto"),
            "the mid-route question is no longer behind the gate at all - neither `askable` nor "
            + "`!auto` appears anywhere near the call");

        assertTrue(around.contains("!auto"),
            "the mid-route confirmation is no longer gated on !auto, so a route fired by an s88 "
            + "sensor would raise a modal dialog with nobody at the machine - blocking its own "
            + "thread, never clearing isExecuting, and throwing the turnout whenever somebody "
            + "eventually pressed OK");

        assertTrue(route.contains("r.execRoute(auto, recursionLimit - 1, false)"),
            "a chained route is no longer told which door it was reached through. Hard-coding this "
            + "was harmless while auto meant only \"do not pop the emergency-stop notice\"; it stopped "
            + "being harmless when auto came to mean \"a person is standing here to be asked\"");

        assertTrue(codeOf("public final synchronized boolean executeAutoRoute()").contains("this.execRoute(true)"),
            "the s88 trigger door no longer calls execRoute(true), so whatever it is now, it is not "
            + "the door the rule above is about");
    }

    /**
     * The two rules that take the gaps out of a right-click menu, each asked its own question.  A DIVIDER has nothing
     * to separate when nothing follows it or a divider does; a HEADING is followed by its own divider always (title()
     * writes the pair), so for a heading the question is asked one component further along.  Widening the heading's
     * condition (OB-112) must not narrow the divider's: two dividers in a row collapse even with no THIRD, or the empty
     * band of OB-054 comes back.  No fixture menu produces two adjacent dividers, so that half needs cases of its own.
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
     * Builds a menu of the given shape, tidies it, and reads the shape back: `I` an item, `-` a divider, `H` a heading
     * (a disabled item, which is what title() leaves behind).
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
     * The way OUT of the editor does what every other way out does.  The diagram is discarded by
     * `layoutEditingComplete` re-reading the pages, but the autonomy setup the same gestures wrote (a dragged captioned
     * tile writes it per gesture) is put back by `undoAutonomyEdits`, the CALLER'S job: the sidebar switch and the
     * editor's X do it.  Closing the APPLICATION must too: `maySettleBeforeExit` must not be a bare delegation to
     * `settleUnsavedWork`, which completes a Discard by itself only in autonomy mode, or Discard on the way out would
     * leave the caption where it was dragged and the next reconciling save would prune entries that matched nothing.
     *
     * Reads the source, a weaker thing than running it: `settleUnsavedWork` puts up a modal dialog and nothing in the
     * suite builds a LayoutEditor.  The behavior is MT-201, by hand.
     *
     * MUTATION: taking the `settledByDiscarding` test out of `completeExitDiscard`, or calling it from
     * `maySettleBeforeExit` again, each fails one of these.
     */
    @Test
    public void testLeavingCompletesBothHalvesOfADiscard() throws Exception
    {
        // The track diagram editor's, since the route editor has one by the same name
        String asks = SourceOf.withoutComments(memberOf("public class LayoutEditor extends PositionAwareJFrame",
            "public boolean maySettleBeforeExit()"));

        assertTrue(asks.contains("settleUnsavedWork()"),
            "the exit path stopped asking about unsaved work at all, which is worse than what this "
            + "test was written for");

        String completes = codeOf("public void completeExitDiscard()");

        assertTrue(completes.contains("undoAutonomyEdits()"),
            "the exit path settles the diagram and not the setup it shares with it. Answer Discard "
            + "while the TRACK editor is open and the diagram goes back while the autonomy setup "
            + "keeps the edit - and the next save prunes whatever no longer matches");

        // In TRACK mode only.  Autonomy mode completes its own discard inside settleUnsavedWork, and
        // undoing there as well would put back a setup the user may have just chosen to keep.
        assertTrue(completes.contains("!isAutonomyMode()"),
            "the setup is put back in both modes now. The autonomy editor discards its own edits "
            + "already, so this undoes them twice - and after a Save it would undo the save");

        // And only when the user actually said Discard: settleUnsavedWork also returns true when nothing was asked, and
        // undoing then would rewind the setup to the editor-open snapshot.
        assertTrue(completes.contains("settledByDiscarding"),
            "the exit path undoes on \"nothing needed settling\" as well as on Discard, which "
            + "rewinds a setup nobody chose to discard");

        // AFTER everything that can refuse the exit: the trains dialog may still say no, and the application would
        // carry on running with the undo already spent.  `WindowClosed` ends in System.exit and could not be tested, so
        // its first question is `everyOpenWindowMaySettle`: the ORDER is asked of `WindowClosed`, the question of that
        // method.
        String exit = codeOf("private void WindowClosed(")
            .replace("everyOpenWindowMaySettle()", "maySettleBeforeExit()");

        String asksBoth = codeOf("public boolean everyOpenWindowMaySettle()");

        // BOTH WINDOWS, NAMED: "contains maySettleBeforeExit" was satisfied by the route editor's call alone, so
        // deleting the layout editor's left File > Exit discarding a page without asking (OB-070).
        assertTrue(asksBoth.contains("openEditor") && asksBoth.contains("maySettleBeforeExit()"),
            "the exit no longer asks the open track diagram editor whether it may settle, so closing the"
            + " application throws away an edited page without asking (OB-070)");

        assertTrue(asksBoth.contains("routeEditor") && asksBoth.contains("maySettleBeforeExit()"),
            "the exit no longer asks the open route editor whether it may settle, so closing the application"
            + " throws away unsaved typing without asking (UIX-B1)");

        // ASKED ONCE: a dirty diagram editor asked twice lets Discard-then-Save write the
        // discarded work to disk.  `core.testCancelUndoesAutonomyEdits.testTheExitAsksOneQuestionPerEditor`
        // counts the dialogs; this counts the calls, because the two fail differently.
        int settleCalls = 0;

        for (int at = exit.indexOf("maySettleBeforeExit()"); at >= 0;
            at = exit.indexOf("maySettleBeforeExit()", at + 1))
        {
            settleCalls++;
        }

        assertEquals(settleCalls, 1,
            "the exit asks whether a window may settle " + settleCalls + " times; twice means one editor is asked"
            + " two questions about the same page, and a Discard followed by a Save then writes the discarded"
            + " work to disk on the way out");

        int settles = exit.indexOf("maySettleBeforeExit()");
        int completes2 = exit.indexOf("completeExitDiscard()");

        // Proved present before they are compared: indexOf answers -1 for an absent term, so deleting the settle call
        // outright would leave a present completeExitDiscard() reading as "after".
        assertTrue(settles >= 0,
            "maySettleBeforeExit() is no longer called from WindowClosed - the exit stopped asking "
            + "about unsaved work at all, which is worse than what this test was written for");

        assertTrue(completes2 >= 0,
            "completeExitDiscard() is no longer called from WindowClosed - a Discard on the way out "
            + "no longer completes the second half of it");

        assertTrue(completes2 > settles,
            "the discard is completed before the exit is certain. Anything between those two can "
            + "still return, and then the setup has been rewound and the application is still up");
    }

    /**
     * Escape puts the autonomy editor's tools down, and brings their buttons up with them (OB-119).
     *
     * Adam: "escape should turn off test a path in the autonomy editor." A tool left LOOKING armed while the panel
     * thinks nothing is armed sends the next click to `cycle()`, which changes a square's direction, so a read-only
     * inspection tool would silently edit the railway.  Escape itself is bound in `LayoutEditor.escapePressed`
     * (FR-065), which `testEscapeClosesTheEditor` presses; what is left here is that putting the tools down puts all of
     * them down.  Read rather than run: the panel needs a session, a page and a live diagram.
     *
     * MUTATION: dropping the `setSelected(false)` loop from `putToolsDown`, or its early return, each fails one of
     * these.
     */
    @Test
    public void testEscapePutsTheAutonomyToolsDown() throws Exception
    {
        String down = codeOf("public boolean putToolsDown()");

        assertTrue(down.contains("setSelected(false)"),
            "the tool buttons are left looking pressed after Escape. The panel then thinks no tool is "
            + "armed while the button says one is, and the next click falls through to cycle(), which "
            + "CHANGES a square - a read-only tool silently editing the railway");

        assertTrue(down.contains("Tool.NONE"), "Escape does not actually disarm the tool");

        assertTrue(down.contains("clearGesture()"),
            "a half-finished gesture survives Escape - a one-way run waiting for its far end would "
            + "swallow the next click anywhere on the diagram");

        assertTrue(down.contains("anythingIsArmed()") && down.contains("return false"),
            "putToolsDown no longer says whether there was anything to put down. LayoutEditor's Escape "
            + "decides from that answer whether to let go of the gesture or to close the editor, so "
            + "without it one press would do both");

        // ONE DEFINITION OF ARMED, asked by both the methods that let go.  Written out twice they come
        // apart at the next tool, and a gesture missing from one list survives being canceled.
        assertTrue(codeOf("    void cancelPendingGesture()").contains(
            "anythingIsArmed()"),
            "cancelPendingGesture has its own copy of what 'armed' means again");

        String armed = codeOf("private boolean anythingIsArmed()");

        for (String held : new String[] { "tool != Tool.NONE", "testFrom", "oneWayFrom",
            "pendingPortal", "signalFor", "blockingFor" })
        {
            assertTrue(armed.contains(held),
                "anythingIsArmed does not count " + held + ", so a gesture waiting on it survives "
                + "Escape and swallows the next click anywhere on the diagram");
        }
    }

    /**
     * The grid actually asks the caption rules, and asks them the right things.  `captionOffset`, `runsNorthSouth` and
     * `onPill` were lifted out of the constructor and each has a test of the rule; this tests the grid still calls them
     * with the right arguments, the price `hidesStationCaptions`' javadoc names.  Read rather than run: the constructor
     * needs a diagram, a window and a UI, and a dropped call or wrong argument is visible in the text.
     *
     * MUTATION: passing `true` instead of `runsNorthSouth(c)` fails the second assertion; swapping `onPill` back to
     * `readableOn` fails the third.
     */
    @Test
    public void testTheGridAsksTheCaptionRulesItsQuestions() throws Exception
    {
        String grid = SourceOf.withoutComments(SourceOf.declaration("public class LayoutGrid\n"));

        assertTrue(grid.contains("StationCaption.captionOffset("),
            "nothing asks captionOffset where a caption goes, so the rule is computed by something "
            + "else - or by nothing, and every caption sits wherever the layout drops it");

        // The ORIENTATION, the argument that can be wrong without anything failing: it decides which WAY a caption is
        // turned, and can be a constant without anything falling over.
        assertTrue(grid.contains("runsNorthSouth(c)"),
            "the grid no longer asks which way the rails run on THIS square. Pass a constant and every "
            + "caption is turned the same way: half of them read upwards along track that runs across");

        // The ANSWER and not just the call.  `setRotated(` alone would be satisfied by `setRotated(false)`,
        // which is the whole defect with the guard still in place.
        assertTrue(grid.contains("setRotated(onEnd)"),
            "the caption is not told what runsNorthSouth answered - pass a constant and either every "
            + "caption stands on end or none does, and half of them lie across the rail they name, "
            + "which is what the rotation was asked for to stop");

        assertTrue(grid.contains("onEnd = runsNorthSouth(c)"),
            "what the caption is told to do is no longer THIS square's orientation, so the rotation "
            + "and the placement can now disagree about which way the rails run");

        // BOTH shifts are paid back before the constraints are used again.  A caption borrows room to center itself by
        // moving gbc, which the address label for the same square then REUSES; leaving the shift in draws that label a
        // whole tile away.  Read rather than run: the behavioral version needs a square with both an autonomy caption
        // and an address label.  Asserted by POSITION, not by counting, since both statements appear three times in
        // this file: the shift is undone between adding the caption and the next thing that reads gbc.
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

        // onPill and not readableOn, at BOTH places that color a caption: asserting only that onPill appears
        // somewhere would let one of the two call sites go back to readableOn.
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

        // The one call allowed is onPill's own, which asks it about the fill
        assertEquals(outside("readableOn(", "public static Color readableOn(Color fill)",
                "public static Color onPill(Color fill, Color wanted)"), 0,
            "the grid asks readableOn directly. It answers by the FILL alone, so a placeholder and a "
            + "name come back identical - which is exactly the defect a reviewer found by disbelieving "
            + "the comment above this call and running it");
    }

    /**
     * The excluded-locomotives prompt still asks whether it is breaking a home.  `homeBrokenBy` has a test of its rule,
     * but nothing tested that `promptLocomotives` calls it: excluding a locomotive from the station it is homed to
     * would go through with no warning, and the call's comment says it is "warned rather than refused", true only if
     * the operator is told.
     *
     * MUTATION: deleting the `homeBrokenBy` call from `promptLocomotives` fails this.
     */
    @Test
    public void testExcludingALocomotiveStillWarnsAboutItsHome() throws Exception
    {
        String prompt = SourceOf.withoutComments(memberOf("final class EditorPrompts", "    void promptLocomotives("));

        assertTrue(prompt.contains("homeBrokenBy("),
            "the excluded-locomotives prompt no longer asks whether the exclusion breaks a home, so "
            + "excluding a locomotive from the station it is homed to happens silently");

        assertTrue(prompt.contains("confirmExcludingHome"),
            "the prompt asks homeBrokenBy and then does not warn about the answer, which is the same "
            + "as not asking");

        // And the other side of the same pair, which is where the rule is shared from.
        String home = codeOf("    void promptHome(");

        assertTrue(home.contains("homeChoices("),
            "the home prompt builds its list some other way than homeChoices, so the two sides of "
            + "this pairing can now disagree about which locomotives may be offered");
    }

    /**
     * Hovering a station label outlines its square, once.
     *
     * Adam: "make sure hovering the label triggers the cell hover effect (blue outline)."
     * A caption sits on top of its square, so moving onto the name sends the square a mouseExited.  The label forwards
     * to `receiveMoveEvent`, the method the tiles call, so the outline is the same one and not a second drawn to match;
     * and it forwards ONLY from the caption, since on the square forwarding again would draw the outline twice.
     *
     * MUTATION: dropping the `handle != square` test makes the square forward for itself and fails the second
     * assertion; drawing a border here instead of forwarding fails the first.
     */
    @Test
    public void testHoveringANameOutlinesItsSquare() throws Exception
    {
        // The caption's drag, which carries its pointer handlers
        String drag = SourceOf.declaration("private static void dragCaption(");

        assertTrue(SourceOf.withoutComments(drag).contains("editor.receiveMoveEvent(e, square)"),
            "hovering a station label no longer tells the editor where the pointer is, so the blue "
            + "outline goes out as soon as the pointer moves onto the name - which is the bigger of "
            + "the two things to point at");

        // EACH handler, asked separately: counting them, or looking for the guard anywhere, passes when the
        // guard is taken off mouseEntered alone and left on mouseMoved.
        for (String handler : new String[] { "mouseEntered", "mouseMoved" })
        {
            // Missing, `SourceOf.declarationIn` fails: the drag no longer handles it
            String body = SourceOf.withoutComments(
                SourceOf.declarationIn(drag, "public void " + handler + "(MouseEvent e)"));

            assertTrue(body.contains("editor.receiveMoveEvent(e, square)"),
                handler + " no longer tells the editor where the pointer is, so the blue outline "
                + (handler.equals("mouseEntered") ? "never appears when the pointer moves onto a name"
                    : "goes out as soon as the pointer moves along the name it is already on"));

            assertTrue(body.contains("if (handle != square)"),
                handler + " forwards for the square as well as for the label, so the same outline is "
                + "drawn twice for one movement");
        }
    }

    /**
     * A right-click on a station label opens the square's menu.
     *
     * Adam: "right-clicks on the label don't propagate to the tile underneath it." A caption covers the middle of its
     * square and takes every click there.  The click is handed to `receiveClickEvent`, the tile's own listener's
     * method, so it is the same menu; and CONVERTED first, since a popup opens at the coordinates it is given and the
     * caption's are not the square's.
     *
     * MUTATION: dropping the conversion leaves the menu opening in the wrong place, which this cannot see; dropping the
     * forward entirely, or letting the square forward for itself, fails below.
     */
    @Test
    public void testARightClickOnANameReachesItsSquare() throws Exception
    {
        // Without a click handler, `SourceOf.declarationIn` fails
        String body = SourceOf.withoutComments(SourceOf.declarationIn(
            SourceOf.declaration("private static void dragCaption("), "public void mouseClicked(MouseEvent e)"));

        assertTrue(body.contains("editor.receiveClickEvent("),
            "a right-click on a station label is swallowed by the label, so the autonomy menu does "
            + "not open on the middle of a captioned square - which is where people aim");

        assertTrue(body.contains("isRightMouseButton(e)"),
            "every click on a label is now forwarded to the square, not only the right-click Adam "
            + "asked for - a left-click would run the selected tool as well as whatever the label does");

        assertTrue(body.contains("convertMouseEvent("),
            "the click is forwarded without converting its coordinates, so the menu opens offset by "
            + "however far the label sits from the corner of its tile");

        assertTrue(body.contains("if (handle != square"),
            "the square forwards its own clicks to itself, so a right-click on plain track opens the "
            + "menu twice");
    }

    /**
     * The autonomy editor says which station the pointer is on.
     *
     * Adam: "on hover of a tile in the autonomy editor, show the station name as the tooltip." The hover handler once
     * returned early, which "took the whole gesture away, tooltip and outline together"; OB-091 put back only the
     * outline, so the tooltip is the part to guard.  The name is asked of the SESSION, not of a nearby caption: the
     * square under the pointer usually has none, which is the case this exists for.
     *
     * MUTATION: reading the name off a caption, or dropping the setToolTipText call, fails this.
     */
    @Test
    public void testTheAutonomyEditorNamesTheSquareUnderThePointer() throws Exception
    {
        String hover = codeOf("public void receiveMoveEvent(");

        // Through what the square says, which since FR-112 is the name and the click's line together
        String says = codeOf("private void showWhatAClickDoes(");

        assertTrue(hover.contains("showWhatAClickDoes(label)") && says.contains("stationNameOn(label)")
            && says.contains("setToolTipText("),
            "the autonomy editor no longer names the square under the pointer, so a station with no "
            + "label on the diagram is anonymous in the one window whose job is arranging stations");

        String lookup = codeOf("private String stationNameOn(");

        assertTrue(lookup.contains("autonomyStationNameAt("),
            "the name comes from somewhere other than the autonomy session - a caption drawn nearby "
            + "is a label somebody placed, and the squares this is for have none");

        // And the LABEL's tooltip, which is a different question with a different answer.
        String grid = codeOf("public LayoutGrid(LayoutDiagram layout, int size, JPanel parent, Container master, "
            + "boolean popup, TrainControlUI ui)");

        // Scoped to the EDITOR's branch by position: `contains` alone passes with the line deleted, because
        // the running diagram sets the same tooltip in its own branch.
        int editorBranch = grid.indexOf("autonomyEditor && captioned != null");
        int draggable = grid.indexOf("dragCaption(text, text,");

        assertTrue(editorBranch > 0 && draggable > editorBranch,
            "cannot find the autonomy editor's caption branch, so this check cannot see the region "
            + "it is about");

        assertTrue(grid.substring(editorBranch, draggable).contains("setToolTipText(captionName)"),
            "a station label carries no tooltip in the EDITOR, so hovering the name says nothing - "
            + "and the square underneath cannot answer for it, because a caption sits on blank space "
            + "BESIDE its platform rather than on it");

        assertTrue(lookup.contains("x < 0 || y < 0"),
            "the palette is no longer excluded, so hovering a tile that is not on the railway asks "
            + "the session about coordinates of minus one");
    }

    /**
     * Plus and minus change pages in BOTH editors, through the switch that already exists (FR-036).
     *
     * Adam: "make the +/- keys scroll through pages in the layout/autonomy editor.  just
     * have them call existing components to reuse the same guards/warnings."
     *
     * Where the keys sit: the handler returns early in autonomy mode, so a page key below that line does nothing in the
     * autonomy editor (MT-109).  What they call: `leaveFor` carries the unsaved-work question and the latch that stops
     * a second switch starting inside the first.  Read rather than run: driving a key needs the window, a multi-page
     * railway and the frame as focus owner.
     *
     * MUTATION: moving the block below the autonomy guard fails the position assertion; having stepPage change the page
     * itself instead of calling leaveFor fails the last one.
     */
    @Test
    public void testThePageKeysUseTheSwitchThatAlreadyExists() throws Exception
    {
        // The editor's key handler, comments and all, since the guard is found by its own sentence
        String editor = memberOf("public class LayoutEditor extends PositionAwareJFrame",
            "private void formKeyPressed(java.awt.event.KeyEvent evt)");

        assertTrue(editor.contains("stepPage(1)") && editor.contains("stepPage(-1)"),
            "the page keys are gone, so + and - no longer move between pages");

        // ABOVE the guard, identified by the guard's OWN sentence rather than by a method name that
        // appears a dozen times in this file.
        int guard = editor.indexOf(
            "// Every shortcut below places, cuts, rotates or retextures a tile");

        assertTrue(guard > 0, "cannot find the autonomy guard, so this check cannot see what it is "
            + "about");

        assertTrue(editor.indexOf("stepPage(1)") < guard,
            "the page keys sit below the guard that returns in autonomy mode, so they do nothing in "
            + "the autonomy editor - which is half of what FR-036 asked for, and exactly where "
            + "MT-109's keys were found doing nothing");

        // And the switch is the one that already exists, with its guards.
        String step = codeOf("private void stepPage(int by)");

        assertTrue(step.contains("leaveFor("),
            "the page keys move between pages by some other means than leaveFor, so they skip the "
            + "unsaved-work question and the latch that stops two switches overlapping - which is "
            + "precisely what Adam asked them to reuse");

        // THE LATCH THAT SPANS THE SWITCH: `switching` is assigned only inside syncSidebar, so it is never true when a
        // second switch could start; `changingPage` is set when a switch is committed and cleared in arriveAt, so it is
        // true across the whole gap leaveFor posts its work into.
        assertTrue(step.contains("changingPage"),
            "stepPage does not check whether a switch is already IN FLIGHT. leaveFor posts its work "
            + "and returns, so the page has not changed yet - and holding the key queues one full "
            + "teardown per auto-repeat, every one of them to the same destination");

        String leave = codeOf("private void leaveFor(String page, boolean autonomy)");

        assertTrue(leave.contains("changingPage = true"),
            "nothing raises the latch when a switch is committed, so it can never be true and every "
            + "door that asks it is asking a constant");

        // BOTH PRESENT, then ordered.  `indexOf` answers -1 for something absent, and -1 is less than
        // every real index, so deleting the unsaved-work question outright would make the order check pass.
        int asks = leave.indexOf("settleUnsavedWork()");
        int raises = leave.indexOf("changingPage = true");

        assertTrue(asks >= 0,
            "leaveFor no longer asks about unsaved work, so switching page throws the edit away");

        assertTrue(raises >= 0,
            "leaveFor no longer raises the switch latch, so nothing stops a second switch starting "
            + "inside the first");

        assertTrue(asks < raises,
            "the latch is raised before the user has been asked about unsaved work, so answering "
            + "\"stay here\" would leave the window refusing every further switch");

        String arrive = codeOf("private void arriveAt(String page, boolean wanted)");

        assertTrue(arrive.contains("changingPage = false"),
            "nothing lowers the latch when the switch arrives, so the first page change would be the "
            + "last one this window ever made");
    }

    /**
     * The excluded-page label and the banner's Fix Setup bring an open editor forward, as the Edit item does.
     *
     * Adam, on the label: *"just make it attempt to click the edit button if it's enabled"*.  With an editor open the
     * Edit item is enabled and `openLayoutEditor` brings that window forward (OB-058), so the label and Fix Setup must
     * not ask `refuseWhileEditorOpen` first, which would say "Close the editor first".  Read from the source: the
     * dialog is modal.
     *
     * MUTATION: put `refuseWhileEditorOpen()` back in either and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheWaysIntoTheEditorBringAnOpenOneForward() throws Exception
    {
        String label = codeOf("public void openAutonomyEditorIfItCan()");

        String ui = codeOf("public void refreshAutonomyPrompt()");

        assertTrue(label.contains("openAutonomyEditor(null)"), "the label no longer opens the editor");

        assertFalse(label.contains("refuseWhileEditorOpen()"), "the excluded-page label refuses with 'Close the"
            + " editor first' where the Edit item it imitates brings the open editor forward (GUI-C8)");

        int fix = ui.indexOf("I18n.t(\"autosetup.ui.btnFixSetup\")");

        assertTrue(fix >= 0, "cannot find the banner's Fix Setup button");

        int next = ui.indexOf("autosetup.ui.btnLoadConfiguration", fix);

        assertTrue(next > fix, "cannot find the banner's Load button, which bounds Fix Setup's action");

        String fixSetup = ui.substring(fix, next);

        assertTrue(fixSetup.contains("openAutonomyEditor(null)"), "Fix Setup no longer opens the editor");

        assertFalse(fixSetup.contains("refuseWhileEditorOpen()"), "Fix Setup refuses with 'Close the editor"
            + " first' where the Edit item brings the open editor forward - and the editor is what fixes the setup"
            + " (GUI-C8)");
    }

    /**
     * The Facing menu ticks nothing where nothing says which way the train faces, except on a square that holds one
     * facing, where every copy faces that way.  The first facing is not ticked as "the one a placement with no recorded
     * facing gets": the build stands such a train on a copy it can start from, which at BottomMainA faces the other
     * way.
     *
     * MUTATION: tick the first facing again and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheFacingMenuTicksOnlyWhatIsKnown() throws Exception
    {
        assertFalse(SourceOf.allCode().contains("facing == facings.get(0)"), "the Facing menu ticks the first facing when nothing"
            + " says which way the train faces - the build no longer stands it that way (GUI3-C4)");
    }

    /**
     * Why not Moving? asks the start question of the tier it is showing: the editor's one call to
     * `Layout.explainCannotStart(loc, byHand)` is what makes it reach the operator.  Reverted to the one-argument form,
     * a train on Manual is told autonomy's reasons again (OB-225) and every claim on the rule stays green.
     *
     * MUTATION: call the one-argument form from `composeWhy` and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testWhyNotMovingAsksTheTierItShows() throws Exception
    {
        String panel = SourceOf.allCode();

        assertTrue(panel.contains("explainCannotStart(standing, byHand)"), "Why not Moving? no longer asks the start"
            + " question of the tier it shows, so on Manual it gives autonomy's reasons (OB-225, GUI3-C1)");
        assertFalse(panel.contains("explainCannotStart(standing)"), "Why not Moving? asks autonomy's start question"
            + " whatever the tier (OB-225, GUI3-C1)");
    }

    /**
     * "Is an editor open" is asked of the EDITOR, not of the button that opens one: `!editLayoutButton.isEnabled()` is
     * true only while the button has one gray cause, and OB-126 gave it a second (no local layout), which would gray
     * every Layout and Autonomy menu item on a Central Station layout, including the ones that switch back.
     *
     * MUTATION: deriving it from the button again fails this.
     */
    @Test
    public void testWhetherAnEditorIsOpenIsAskedOfTheEditor() throws Exception
    {
        String open = codeOf("public boolean isLayoutEditorOpen()");

        // AND INTO anEditorWindow, which asks the editor windows
        if (open.contains("anEditorWindow()"))
        {
            open = codeOf("private LayoutEditor anEditorWindow()");
        }

        assertTrue(open.contains("openEditor"),
            "whether an editor is open is decided without looking at the editor, so anything else "
            + "that greys the Edit button - such as there being no local layout - now reads as an "
            + "editor being open, and every menu that guards on it greys itself");

        assertFalse(open.contains("editLayoutButton"),
            "the answer is read off the Edit button again. The button's greyness has more than one "
            + "cause now, so it cannot stand for this one: on a Central Station layout the Layout and "
            + "Autonomy menus both go dead, with the way back inside them");
    }

    /**
     * Every caption on the diagram is shown or hidden by the same rule.  FR-023 added a second reason to hide a
     * station's name beside the overlay switch, and a third place deciding (`showStaticAutonomyLayer` setting every
     * label to its own `show` wholesale) overwrote both.  One rule, and every site asks it.
     */
    @Test
    public void testEveryCaptionVisibilityDecisionAsksTheRule() throws Exception
    {
        String source = SourceOf.allCode();

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

        // And where `visible` comes from, the hole the disjunct above leaves: refreshCaptionVisibility, the path the
        // toggle uses, passes a local called `visible` rather than naming the rule on the line, so that line could
        // become a constant.
        int assignments = 0;

        for (String line : source.split("\n"))
        {
            String trimmed = line.trim();

            if (!trimmed.startsWith("boolean visible =")) continue;

            assignments++;

            assertTrue(trimmed.contains("captionShouldShow") || trimmed.contains("captionIsActive"),
                "a caption's visibility is decided by something other than the shared rule: "
                + trimmed + ".  That local is handed straight to setVisible, so whatever it is "
                + "computed from IS the rule for that path");
        }

        // A loop over nothing asserts nothing: renaming that local would make the block above a no-op that passes.
        assertTrue(assignments > 0,
            "no line assigns a `boolean visible` any more, so the check above ran over nothing and "
            + "proved nothing - find what the caption deciders are handed now and check that instead");

        for (String decider : deciders)
        {
            assertTrue(decider.contains("captionShouldShow") || decider.contains("visible"),
                "a caption's visibility is being set from something other than the shared rule: "
                + decider + ".  Setting them wholesale is what made Show Inactive Labels appear to do "
                + "nothing (FR-023)");
        }

        // And the blanket setter by name, because it is the one that got it wrong.
        String body = codeOf("public void showStaticAutonomyLayer(boolean show)");

        assertTrue(body.contains("captionIsActive("),
            "showStaticAutonomyLayer no longer asks captionIsActive, so it is back to setting every "
            + "caption wholesale - which is precisely the state Adam reported as 'Flipping the new "
            + "setting has no effect'");
    }

    /**
     * The blocked-points picker offers every stored entry, refuses the station itself, and names what it draws.
     *
     * This dialog is the only way to edit blockedPoints, and `setBlockingPoints` REPLACES the stored list:
     * - the station itself is never offered (OB-083), nor a caption pointing AT it;
     * - every stored entry is offered, whatever the filters say, or it is deleted on OK or made permanent;
     * - the check box says what it is: carried entries are squares that have lost their name, `getPointName` returns
     *   null for those, and the box would render with no text.
     * Source-level because a dialog cannot be driven from a test here.  The self-caption qualifier changes nothing the
     * operator can see (the carry loop puts every stored entry back), so it is kept for the statement it makes.
     *
     * MUTATION: in `AutonomyEditorPanel.promptBlockingPoints`, put the check-box label back to
     * `session.getStore().getPointName(tile)` and this fails.  Second: drop the `&& !already.contains(tile)` qualifier
     * from the self-caption filter in EditorPrompts (the whole statement is the needle; the qualifier also sits in
     * the stations-only filter).
     */
    @Test
    public void testTheBlockedPointsPickerCarriesAndNamesWhatIsStored() throws Exception
    {
        // The whole method, wherever it is, with its comments blanked so a rule is proved by the code and not the prose
        // about it.  Gone, `SourceOf.declaration` fails: it is the only door onto blockedPoints.
        String body = SourceOf.withoutComments(memberOf("final class EditorPrompts", "    void promptBlockingPoints("));

        // What is stored is read before the offer is built, because it decides the offer.  The left term is proved
        // present first: indexOf answers -1 for an absent needle, which would make the ordering assertion pass
        // vacuously.
        assertTrue(body.contains("getBlockingPoints(station)"),
            "getBlockingPoints(station) is no longer called here - it may have been renamed or "
            + "reparameterised, and without this check the ordering assertion below would pass "
            + "vacuously (indexOf returns -1, which sorts before everything)");

        assertTrue(body.indexOf("getBlockingPoints(station)") < body.indexOf("getNamedTiles()"),
            "promptBlockingPoints builds its list of choices before reading what is already stored. "
            + "The stored entries are what the filters have to be qualified BY (FSR-C5), so a read "
            + "that happens afterwards can only tick boxes - and an entry the filters hid is then "
            + "deleted the moment OK is pressed, because setBlockingPoints replaces the list (FBR-A2)");

        assertTrue(body.contains("tile.equals(station)"),
            "the station itself is no longer refused as a square that holds it back.  Standing there "
            + "already decides whether it is free, so watching itself makes it a station nothing can "
            + "be sent to (OB-083)");

        // The self-caption filter's own statement, whole: the qualifier `!already.contains(tile)` also appears in the
        // isStation filter above, so a looser needle would stay green under the mutation.  Whitespace is collapsed so a
        // re-wrap does not read as a removal.
        String flat = body.replaceAll("\\s+", " ");

        assertTrue(flat.contains("!editor.session.getStore().isStation(tile) && !already.contains(tile)) continue;"),
            "the stations-only filter is gone, or is no longer qualified by what is already stored: a stored entry that is not a "
            + "station would be hidden, and the dialog is the only way to remove it (FSR-C5)");

        assertTrue(flat.contains("station.equals(editor.session.getCaptionTarget(tile)) && !already.contains(tile)) continue;"),
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

        assertTrue(body.contains("new javax.swing.JCheckBox(\n                editor.describeTile(tile)")
                || body.contains("new javax.swing.JCheckBox(editor.describeTile(tile)"),
            "the check boxes are labelled with something other than describeTile.  The loop above "
            + "exists to offer squares that have LOST their name, and getPointName is a plain map "
            + "lookup that returns null for exactly those - so the box drawn for one renders with no "
            + "text at all, ticked, asking the operator to keep or remove a thing it will not name "
            + "(RA-C2).  describeTile falls back to the s88 address and then to the coordinates");
    }

    /**
     * EVERY branch that raises the latch, not just the one that was looked at.  `leaveFor` returns from two places and
     * both raise `changingPage`: the track branch gets the guarantee from `layoutEditingCompleteThen`, and the autonomy
     * branch must not call `autonomyEditorClosed()` bare, or a throw there leaves the latch up for the window's life.
     *
     * MUTATION: unwrapping either branch's guarantee fails this.  So does adding a third raise without one.
     */
    @Test
    public void testEveryLatchRaiseHasAWayDown() throws Exception
    {
        String leave = codeOf("private void leaveFor(String page, boolean autonomy)");

        java.util.List<Integer> raises = new java.util.ArrayList<>();

        for (int at = leave.indexOf("changingPage = true"); at >= 0;
            at = leave.indexOf("changingPage = true", at + 1))
        {
            raises.add(at);
        }

        // The loop above asserts nothing if it runs over nothing.
        assertTrue(raises.size() >= 2,
            "leaveFor no longer has two branches that raise the switch latch - it had one per mode, "
            + "and this check is written per raise so that a branch added without a way to lower it "
            + "fails. Found " + raises.size());

        for (int i = 0; i < raises.size(); i++)
        {
            int from = raises.get(i);
            int to = i + 1 < raises.size() ? raises.get(i + 1) : leave.length();

            String branch = leave.substring(from, to);

            assertTrue(branch.contains("arriveAt(page, autonomy)"),
                "a branch of leaveFor raises the switch latch and never posts arriveAt, which is the "
                + "only thing that lowers it - that window would refuse every page and mode change "
                + "for the rest of its life, in silence");

            // Directly, or through the helper whose finally does it for the caller.
            assertTrue(branch.contains("finally") || branch.contains("layoutEditingCompleteThen"),
                "a branch of leaveFor posts arriveAt only if the work before it returns normally, so "
                + "a throw in that work strands the latch up - which is a worse fault than the "
                + "overlapping switches the latch exists to stop, and is the fault it was already "
                + "fixed for once on the other branch");
        }

        // The helper the track branch leans on really does make that promise, in three places: the worker posts the EDT
        // half whatever the refresh does, the EDT half runs the continuation from a finally (any earlier and the Edit
        // Layout button is re-enabled with the editor still open), and the helper covers a throw before there is a
        // worker.
        String helper = codeOf("public void layoutEditingCompleteThen(Runnable after)");

        assertTrue(helper.contains("catch") && helper.contains("once.run()"),
            "layoutEditingCompleteThen no longer runs its continuation when the refresh cannot even "
            + "be started, so the track branch of leaveFor lost that limb of the guarantee it "
            + "delegates - and the check above accepts that branch on the strength of calling this "
            + "method.  Body: " + helper);

        String async = codeOf("public void layoutEditingComplete(Runnable after)");

        assertTrue(async.contains("finally") && async.indexOf("finally") < async.indexOf("invokeLater"),
            "the refresh worker no longer posts its EDT half from a finally, so a layout that will "
            + "not parse leaves the editor's switch latch raised for the life of the window - which "
            + "is the fault this whole rule exists for.  Body: " + async);

        String edt = codeOf("private void layoutRefreshComplete(Runnable after)");

        assertTrue(edt.contains("finally") && edt.contains("after.run()"),
            "layoutRefreshComplete no longer runs the continuation from a finally.  That is where the "
            + "dozen statements are, and running it anywhere earlier puts the editor's arriveAt on "
            + "the EDT before the refresh - which re-enables Edit Layout with the editor open.  "
            + "Body: " + edt);
    }

    /**
     * The gray Edit Layout button explains WHICH of its reasons applies (OB-126): no layout loaded, a Central Station
     * layout, an editor already open.  Both doors that ask whether it is gray must name the one that applies.  Checks
     * the pairing rather than the sentence, since the sentence is only wrong when the pairing is.
     *
     * MUTATION: hard-coding either dialog back to a single key fails this.  So does dropping a branch from the
     * explanation while leaving it in the rule.
     */
    @Test
    public void testTheGreyEditButtonSaysWhyItIsGrey() throws Exception
    {
        // THE TWO DOORS both ask, rather than assuming.
        for (String door : new String[] {
            "private void editLayoutButtonActionPerformed(java.awt.event.ActionEvent evt)", OPEN_LAYOUT_EDITOR })
        {
            String body = codeOf(door);

            assertTrue(body.contains("reasonLayoutIsNotEditable()"),
                "a door that refuses because the Edit button is grey no longer asks which of the "
                + "three reasons applies, so it is back to naming one of them whatever is true: "
                + door);

            assertFalse(body.contains("\"autosetup.ui.errorEditorAlreadyOpen\""),
                "a door names the already-open message directly again, so somebody with no layout "
                + "loaded is told to close an editor that does not exist: " + door);
        }

        // THE EXPLANATION covers every branch of the RULE.
        String rule = codeOf("private boolean layoutCanBeEdited()");
        String why = codeOf("public String whyLayoutCannotBeEdited()");

        // THE FLAG, not an inference (Adam: "we just need a layout loaded flag in the TrainControlUI class,
        // rather than something that infers").
        assertTrue(rule.contains("isLayoutLoaded()") && rule.contains("isLocalLayout()"),
            "layoutCanBeEdited has been rewritten, so the pairing below is comparing against "
            + "something else - read it before trusting this test");

        assertFalse(rule.contains("getLayoutList()"),
            "layoutCanBeEdited asks the model directly again instead of reading the stored answer, "
            + "so it can disagree with everything else that was asked at a different moment - which "
            + "is the defect behind OB-127 and OB-128");

        assertTrue(why.contains("isLayoutLoaded()"),
            "the explanation no longer has an answer for \"no layout loaded\", which is one of the "
            + "reasons the rule greys the button for");

        assertTrue(why.contains("isLocalLayout()"),
            "the explanation no longer has an answer for a Central Station layout, which is the "
            + "reason OB-126 was filed about");

        assertTrue(why.contains("noEditorOpen"),
            "the explanation no longer has an answer for an editor already being open, which is the "
            + "other half of what makes the button grey");

        // And each answer is a key that exists.
        String bundle = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/resources/messages.properties")),
            java.nio.charset.StandardCharsets.UTF_8);

        java.util.regex.Matcher keys =
            java.util.regex.Pattern.compile("\"([a-z]+\\.[a-zA-Z.]+)\"").matcher(why);

        int found = 0;

        while (keys.find())
        {
            found++;

            assertTrue(bundle.contains("\n" + keys.group(1) + "="),
                "whyLayoutCannotBeEdited answers with " + keys.group(1) + ", which is not a key in "
                + "messages.properties - the dialog would show the key itself");
        }

        assertEquals(found, 3,
            "expected three message keys in whyLayoutCannotBeEdited, one per reason the button is "
            + "grey, and found " + found);
    }

    /**
     * The Layout menu names its two halves, and the guard does not undo them.
     *
     * Adam: "Add a 'Local Layout' and 'Central Station Layout' headings into the Layouts jmenu.  Move
     * 'save as picture' into the same group as the popout option."
     *
     * The trap is the second assertion: `guardLayoutMenu` enables or disables every child from one flag, and a heading
     * is disabled because it is a label, so without an exemption it comes back enabled, live and doing nothing.
     *
     * MUTATION: removing the exemption fails this.  So does putting the export back at the foot of the menu.
     */
    @Test
    public void testTheLayoutMenuNamesItsGroups() throws Exception
    {
        // Gone, `SourceOf.declaration` fails: nothing mounts the Layout menu headings any more
        String mount = codeOf("private void mountLayoutHeadings()");

        assertTrue(mount.contains("headingLocalLayout") && mount.contains("headingCentralStationLayout"),
            "one of the two Layout menu headings is gone, so a menu whose halves do different things "
            + "to different railways no longer says which is which");

        // Placed relative to the items they head, not at fixed indices - everything above them moves.
        assertTrue(mount.contains("indexOnLayoutMenu(chooseLocalDataFolderMenuItem)")
                && mount.contains("indexOnLayoutMenu(switchCSLayoutMenuItem)"),
            "the headings are placed by counting rather than by looking their neighbours up, so the "
            + "next item added to this menu puts them in the wrong place");

        // THE TRAP.
        String guard = codeOf("private void guardLayoutMenu()");

        assertTrue(guard.contains("child == localHeading || child == centralStationHeading"),
            "the headings are no longer exempt from the enable/disable sweep, so they come back "
            + "ENABLED whenever no editor is open - a bold, live menu item that does nothing");

        // And a heading really is created disabled, or the exemption protects nothing.
        String heading = codeOf("private javax.swing.JMenuItem menuHeading(String text)");

        assertTrue(heading.contains("setEnabled(false)"),
            "a heading is no longer created disabled, so it is an ordinary menu item in bold that "
            + "does nothing when clicked");

        // The export sits with the pop-out.
        String export = codeOf("private void buildDiagramExportMenu()");

        assertTrue(export.contains("indexOnLayoutMenu(popUpAllMenuItem)"),
            "the picture export is no longer placed beside the pop-out - it goes back to the foot of "
            + "the menu, below the Central Station group it has nothing to do with");

        assertFalse(export.contains("layoutMenu.addSeparator()"),
            "the export still adds a separator of its own, which now cuts the pop-out group in half");
    }

    /** Manage Configurations sits directly under the configuration it manages. */
    @Test
    public void testManageSitsUnderTheConfiguration() throws Exception
    {
        // The method that builds the autonomy menu, wherever it is (FR-136)
        String menu = codeOf("private void buildItems()");

        int choose = menu.indexOf("add(choose);");
        int manage = menu.indexOf("add(manageMenu(");
        int pages = menu.indexOf("add(pages);");

        assertTrue(choose >= 0, "the configuration submenu is no longer added");
        assertTrue(manage >= 0, "Manage Configurations is no longer added to the autonomy menu");
        assertTrue(pages >= 0, "the pages submenu is no longer added");

        assertTrue(choose < manage,
            "Manage Configurations is added before the configuration submenu it manages");

        assertTrue(manage < pages,
            "Manage Configurations has drifted back down the menu, below the editing tools - it was "
            + "two groups away from the thing it acts on and they are one subject");
    }

    /**
     * The pop-out and the picture export sit BELOW the Central Station section.
     *
     * Adam: "Move pop up all... and save as picture to below the central station layout section."
     * The menu test above places the export relative to the pop-out; this pins where the pair itself sits.
     *
     * MUTATION: dropping the re-home block from mountLayoutHeadings fails this.
     */
    @Test
    public void testThePopOutAndExportSitBelowTheStationSection() throws Exception
    {
        String mount = codeOf("private void mountLayoutHeadings()");

        int heading = mount.indexOf("layoutMenu.add(centralStationHeading");
        int removed = mount.indexOf("layoutMenu.remove(popUpAllMenuItem)");
        int readded = mount.indexOf("layoutMenu.add(popUpAllMenuItem)");
        int export = mount.indexOf("layoutMenu.add(exportDiagramItem)");

        // Both present before either is ordered - indexOf answers -1 for something absent.
        assertTrue(heading >= 0, "the Central Station heading is no longer mounted");
        assertTrue(removed >= 0 && readded >= 0,
            "the pop-out is no longer taken out from between the two sections and put below them, so "
            + "it is back under the Local Layout heading it has nothing to do with");
        assertTrue(export >= 0, "the picture export no longer travels with the pop-out");

        assertTrue(heading < removed,
            "the pop-out is re-homed before the Central Station heading is mounted, so it lands above "
            + "the section it is supposed to sit below");

        assertTrue(readded < export,
            "the export no longer follows the pop-out - they were put together deliberately, both "
            + "being ways of getting the diagram into a window or a file");

        // And the separators are tidied by walking, not by naming generated fields.
        String tidy = codeOf("private void tidyLayoutMenuSeparators()");

        assertFalse(tidy.contains("jSeparator"),
            "the separator tidy names a generated field. The NetBeans designer renumbers those "
            + "whenever the form is touched, so a rule that names one is a designer save away from "
            + "being wrong - which is how cropOverlay disappeared twice on 2026-08-28");
    }

    /**
     * No dialog is raised from a background thread (OB-137).
     *
     * Adam: "while importing routes from Json, the route table freezes up / looks weird."  A dialog raised off the
     * event thread has the chooser and the window behind it painted from two threads.  The scan covers every source
     * file and every blocking dialog form, and its floors matter as much as the assertion: a scan that stops finding
     * calls is a check that no dialogs exist, which always passes.  invokeAndWait counts as marshalling alongside
     * invokeLater; examples/ is skipped.
     *
     * MUTATION: wrapping any of these calls back inside a `new Thread(` in its own method fails this.
     */
    @Test
    public void testNoDialogIsShownOffTheEventThread() throws Exception
    {
        // Everything that BLOCKS.  A message dialog is listed deliberately: it was used to show a whole AutoJSONExport
        // panel built on the background thread.
        String[] needles =
        {
            "showOpenDialog(", "showSaveDialog(", "showInputDialog(",
            "showMessageDialog(", "showConfirmDialog(", "showOptionDialog("
        };

        // Where a member begins; not a Java parser: the shape caught is `new Thread(() -> { ... showSomethingDialog(
        // ... })`.
        String[] memberStarts = { "    private ", "    public ", "    protected ", "    static " };

        java.util.List<String> offenders = new java.util.ArrayList<>();
        java.util.Set<String> filesWithDialogs = new java.util.TreeSet<>();

        int examined = 0;

        for (java.io.File f : javaFilesUnder(new java.io.File("src")))
        {
            String path = f.getPath().replace('\\', '/');

            if (path.contains("/examples/")) continue;

            String code = SourceOf.withoutComments(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8));

            // Where each package-private method begins: a line four spaces in, a type and a name, no modifier
            java.util.List<Integer> bareStarts = new java.util.ArrayList<>();

            java.util.regex.Matcher bare = java.util.regex.Pattern.compile(
                "\n    (?!(?:private|public|protected|static|return|new|else|throw)\\b)[A-Za-z_][\\w.<>\\[\\], ?]*\\s+\\w+\\(")
                .matcher(code);

            while (bare.find()) bareStarts.add(bare.start() + 1);

            for (String needle : needles)
            {
                for (int at = code.indexOf(needle); at >= 0; at = code.indexOf(needle, at + 1))
                {
                    examined++;
                    filesWithDialogs.add(f.getName());

                    int from = -1;

                    for (String s : memberStarts)
                    {
                        from = Math.max(from, code.lastIndexOf(s, at));
                    }

                    // And a package-private method, with no modifier to find it by: otherwise its dialogs are read as
                    // the member above's.
                    for (int start : bareStarts)
                    {
                        if (start < at) from = Math.max(from, start);
                    }

                    if (from < 0) from = 0;

                    String before = code.substring(from, at);

                    int thread = Math.max(before.lastIndexOf("new Thread("),
                        before.lastIndexOf("LayoutGridRenderer.submit("));

                    if (thread < 0) continue;

                    // Marshalled back after the thread opened is the correct shape, in both forms.
                    String inside = before.substring(thread);

                    if (inside.contains("invokeLater") || inside.contains("invokeAndWait")) continue;

                    String member = code.substring(from, code.indexOf('(', from));
                    member = member.substring(member.lastIndexOf(' ') + 1);

                    offenders.add(f.getName() + "#" + member + " " + needle);
                }
            }
        }

        assertTrue(examined >= 200,
            "this scan examined only " + examined + " dialog calls. It used to find 322, so either "
            + "the needles have gone stale or the walk is not reaching the source - and a scan that "
            + "finds nothing asserts that no dialogs exist rather than where they are shown");

        assertTrue(filesWithDialogs.size() >= 12,
            "dialogs were found in only " + filesWithDialogs.size() + " files (" + filesWithDialogs
            + "). The first version of this test read exactly one file and missed six broken methods "
            + "in five others, which is the whole reason it walks the tree now");

        assertEquals(offenders.toString(), "[]",
            "a modal dialog is shown from a background thread. Swing is single-threaded, so the "
            + "dialog and the window behind it are laid out from two threads at once - which is what "
            + "Adam saw as the route table freezing and looking wrong (OB-137): " + offenders);
    }

    /** Every .java file under a directory, recursively. */
    private static java.util.List<java.io.File> javaFilesUnder(java.io.File dir)
    {
        java.util.List<java.io.File> found = new java.util.ArrayList<>();

        java.io.File[] entries = dir.listFiles();

        if (entries == null) return found;

        java.util.Arrays.sort(entries);

        for (java.io.File e : entries)
        {
            if (e.isDirectory())
            {
                found.addAll(javaFilesUnder(e));
            }
            else if (e.getName().endsWith(".java"))
            {
                found.add(e);
            }
        }

        return found;
    }

    /**
     * Every door that writes a point property ends by announcing it (MT-246).
     *
     * Adam: *"Setting a home locomotive via track diagram viewer does not activate the send home button, and
     * it is not persisted when closing the app."*  Both halves are one omission: a write never announced.  On the
     * track diagram `onDiagramChanged` (`session.save()`) alone makes an edit outlive the process and
     * `rebuildRunningLayoutFromSetup` alone tells the running `Layout`, which every affordance reads.  Asked of the
     * source: these methods are private and open modal dialogs.
     *
     * MUTATION: removing `setupChanged();` from any one of the nine fails this.
     */

    @Test
    public void testEveryDoorThatWritesTheSetupAnnouncesItWiderSweep() throws Exception
    {
        // The panel's class, wherever it is: the rule for the doors of the panel that supplies the tile menus to both
        // surfaces.
        String source = SourceOf.declaration("public class AutonomyEditorPanel extends JPanel");

        java.util.List<String> examined = new java.util.ArrayList<>();

        // Asked as text so a fixture can be asked the same question (see silentWriters), of every class in the panel's
        // family: a door moved into a helper is still one of its doors.
        java.util.Map<String, String> family = editorFamily();

        java.util.List<String> silent = new java.util.ArrayList<>();

        for (String member : family.values()) silent.addAll(silentWriters(member, writers(), examined));

        // Each helper the panel makes for itself is in the family, however the panel is held.
        java.util.regex.Matcher made = java.util.regex.Pattern.compile("final (\\w+) \\w+ = new \\1\\(this\\);")
            .matcher(SourceOf.withoutComments(source));

        while (made.find())
        {
            assertTrue(family.containsKey(made.group(1)), made.group(1) + " is made by the panel for itself but is not"
                + " in the family this sweep reads, so the setup writes moved into it are not checked: " + family.keySet());
        }

        int checked = examined.size();

        // The radio exemption in silentWriters is only sound while `radio` really does announce.
        assertTrue(source.contains("placementChanged(null)"),
            "AutonomyEditorPanel no longer calls placementChanged anywhere, so the `radio(` "
            + "exemption below is exempting doors that announce nothing (VD11-A1)");

        // Gone, `SourceOf.declaration` fails: the radio helper has gone, and the exemption with it should too
        assertTrue(codeOf("    javax.swing.JMenuItem radio(").contains("placementChanged(null)"),
            "radio() no longer announces, so every radio door on this menu is silent and the "
            + "exemption is hiding them (VD11-A1)");

        // And `toggle`, which the scan exempts the same way: a checkbox's lambda says nothing and `toggle` announces
        // after the action, so the exemption is only sound while the helper's own body still does.
        assertTrue(codeOf("    javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey,")
            .contains("placementChanged(null)"),
            "toggle() no longer announces, so every checkbox door on these menus is silent and the exemption is "
            + "hiding them (GSE-C1)");

        // And the light door's exemption is only sound while it announces (MT-334), as for radio above.
        assertTrue(codeOf("private void annotationsChanged()")
            .contains("rebuildRunningLayoutSoon()"),
            "annotationsChanged no longer rebuilds the running layout, so the four direction doors "
            + "exempted through it announce nothing to the railway (MT-334)");

        assertTrue(checked >= 7,
            "only " + checked + " writers of a point property were found in AutonomyEditorPanel and its helpers, "
            + "which is fewer than it has - so this scan is reading the wrong thing and would pass "
            + "having examined almost nothing");

        assertEquals(silent, new java.util.ArrayList<String>(),
            silent.size() + " door(s) write a point property and never call setupChanged(), so an "
            + "edit made from the TRACK DIAGRAM is neither saved nor shown to the running layout.  "
            + "The user sets a home and the Return Home item stays greyed, because "
            + "triageReturnToHome asks the running layout; and the edit dies with the process, "
            + "because that panel has no Save (MT-246).  They are: " + silent);
    }

    /**
     * The autonomy editor's panel and its helpers, each as its class name and its source: the panel's class and every
     * class under gui/ that holds the panel in a `final` field (windows that mount it hold it in a reassigned field and
     * announce their own way).  Found in the source, so a helper is swept the day it is written.
     *
     * @return each class's name and its source
     * @throws Exception if a source file cannot be read
     */
    private static java.util.Map<String, String> editorFamily() throws Exception
    {
        java.util.Map<String, String> family = new java.util.LinkedHashMap<>();

        family.put("AutonomyEditorPanel", SourceOf.declaration("public class AutonomyEditorPanel extends JPanel"));

        java.util.regex.Pattern holds = java.util.regex.Pattern.compile("\\bfinal\\s+AutonomyEditorPanel\\s+\\w+\\s*;");

        for (java.io.File f : javaFilesUnder(new java.io.File("src/org/traincontrol/gui")))
        {
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            if (holds.matcher(SourceOf.withoutComments(text)).find())
            {
                family.put(f.getName().replace(".java", ""), text);
            }
        }

        return family;
    }

    /**
     * A lambda that writes the setup is not announced by another lambda's `setupChanged()`: a scan of the enclosing
     * METHOD would pass an item on its neighbors' announcement.
     *
     * A fixture, because the real menu has no silent item: block lambdas that do and do not announce, expression
     * lambdas handed to `toggle` (announces) and `item` (does not), and a method whose write sits in an `if` with the
     * announcement after it, which must still pass: a lambda is a scope, a branch is not.
     *
     * MUTATION: in `silentWriters`, ask the enclosing method instead of the write's own lambda, and this fails.
     *
     * @throws Exception never
     */
    @Test
    public void testASilentLambdaIsNotCoveredByItsNeighboursAnnouncement() throws Exception
    {
        String fixture = String.join("\n",
            "public class Fixture",
            "{",
            "    public javax.swing.JPopupMenu buildTileMenu(TileKey target)",
            "    {",
            "        menu.add(item(\"Announced\", () ->",
            "        {",
            "            session.setHome(target, null, null);",
            "",
            "            setupChanged();",
            "        }));",
            "",
            "        menu.add(item(\"Silent\", () ->",
            "        {",
            "            session.setPointName(target, \"x\");",
            "        }));",
            "",
            "        menu.add(toggle(\"Announced by the helper\", \"hint\", true,",
            "            on -> session.setAutoDestination(target, on)));",
            "",
            "        menu.add(item(\"Silent too\", () -> session.setLinkName(target, \"y\")));",
            "",
            "        return menu;",
            "    }",
            "",
            "    private void promptLength(TileKey tile)",
            "    {",
            "        if (tile != null)",
            "        {",
            "            session.setTileLength(tile, 3);",
            "        }",
            "",
            "        setupChanged();",
            "    }",
            "}",
            "");

        java.util.List<String> examined = new java.util.ArrayList<>();

        java.util.List<String> silent = silentWriters(fixture, java.util.Arrays.asList("session.setHome(",
            "session.setPointName(", "session.setAutoDestination(", "session.setLinkName(", "session.setTileLength("),
            examined);

        assertEquals(examined.size(), 5, "the scan did not find the fixture's five writes, so whatever it reports says"
            + " nothing about how it judged them: " + examined);

        assertEquals(silent.size(), 2, "a lambda that writes the setup and announces nothing passed, because ANOTHER"
            + " lambda in the same method calls setupChanged() - the scan reads the enclosing method, so every item on"
            + " buildTileMenu passes on its neighbours' announcement (GSE-C1).  Reported: " + silent);

        assertTrue(silent.get(0).contains("session.setPointName(") && silent.get(1).contains("session.setLinkName("),
            "the two silent writes are not the ones reported: " + silent);
    }

    /**
     * Every write of the setup in a source that nothing announces, one entry per write (MT-246).
     *
     * Asked of the write's own lambda, not of its method: a lambda runs when its item is clicked, so it is the lambda
     * that must announce.  The scope is the innermost lambda running the write, its method only where no lambda does;
     * an `if` or `try` is not a scope.  `radio(...)` and `toggle(...)` announce for their caller (they call
     * `placementChanged(null)`), so a lambda handed to either counts; the rule calling this asserts both still
     * announce.  Comments and literals are blanked, offsets kept.
     *
     * @param source the Java source
     * @param writers the `session.name(` fragments that write the setup
     * @param examined filled with the door of every write looked at, so a caller can tell a scan that found nothing
     *        silent from one that found nothing at all
     * @return each write nothing announces, as its door, its line and the line's text
     */
    private static java.util.List<String> silentWriters(String source, java.util.List<String> writers,
        java.util.List<String> examined)
    {
        source = source.replace("\r\n", "\n");

        String code = blankedLiterals(source);

        String[] lines = source.split("\n", -1);

        java.util.List<String> helpers = java.util.Arrays.asList("radio", "toggle");

        // EVERY WRITER, not two of them, in the order the file has them
        java.util.TreeSet<Integer> writes = new java.util.TreeSet<>();

        for (String setter : writers)
        {
            for (int at = code.indexOf(setter); at >= 0; at = code.indexOf(setter, at + 1)) writes.add(at);
        }

        java.util.List<String> silent = new java.util.ArrayList<>();

        for (int write : writes)
        {
            int line = 0;

            for (int i = 0; i < write; i++) if (code.charAt(i) == '\n') line++;

            // The enclosing method: back to the nearest declaration at class indent, whatever its access.
            int start = line;

            while (start > 0 && !(lines[start].startsWith("    ") && lines[start].length() > 4
                && Character.isJavaIdentifierStart(lines[start].charAt(4)))) start--;

            String name = lines[start].trim();

            examined.add(name);

            // setupChanged and the light annotationsChanged door may write nothing and announce nothing.
            if (name.contains("setupChanged") || name.contains("annotationsChanged")) continue;

            int declared = 0;

            for (int i = 0; i < start; i++) declared += lines[i].length() + 1;

            int body = code.indexOf('{', declared);

            String scope = null;
            String call = "";

            // AN EXPRESSION LAMBDA: an arrow earlier in the write's own statement, whose expression reaches the write.
            int statement = write;

            while (statement > 0 && ";{}".indexOf(code.charAt(statement - 1)) < 0) statement--;

            for (int arrow = code.lastIndexOf("->", write); scope == null && arrow >= statement;
                arrow = code.lastIndexOf("->", arrow - 1))
            {
                int end = expressionEnd(code, arrow);

                if (end > write)
                {
                    scope = code.substring(arrow, end);
                    call = receivingCall(code, arrow);
                }
            }

            // A BLOCK LAMBDA: the nearest enclosing brace with an arrow in front of it.  The braces of an if, a loop
            // or a try are walked out of.
            for (int i = write - 1, depth = 0; scope == null && i > body; i--)
            {
                char c = code.charAt(i);

                if (c == '}')
                {
                    depth++;
                }
                else if (c == '{' && depth > 0)
                {
                    depth--;
                }
                else if (c == '{')
                {
                    int before = i - 1;

                    while (before > 0 && Character.isWhitespace(code.charAt(before))) before--;

                    if (before > 0 && code.charAt(before) == '>' && code.charAt(before - 1) == '-')
                    {
                        scope = code.substring(i, closingBrace(code, i) + 1);
                        call = receivingCall(code, before - 1);
                    }
                }
            }

            // AND THE METHOD, where no lambda runs the write
            if (scope == null) scope = code.substring(declared, closingBrace(code, body) + 1);

            // placementChanged() ends in setupChanged(); annotationsChanged() is the light door that tells the running
            // layout without the grid rebuild (MT-334).
            boolean announces = scope.contains("setupChanged()")
                || scope.contains("placementChanged(")
                || scope.contains("annotationsChanged()")
                || helpers.contains(call);

            if (!announces) silent.add(name + " - line " + (line + 1) + ": " + lines[line].trim());
        }

        return silent;
    }

    /**
     * Java source with its comments and its string and character literals blanked to spaces, every newline and
     * offset kept.
     *
     * @param source the source, with LF line endings
     * @return text of the same length that is code only
     */
    private static String blankedLiterals(String source)
    {
        char[] out = source.toCharArray();

        for (int i = 0; i < out.length; i++)
        {
            char c = source.charAt(i);

            if (c == '/' && i + 1 < out.length && source.charAt(i + 1) == '/')
            {
                while (i < out.length && source.charAt(i) != '\n') out[i++] = ' ';
            }
            else if (c == '/' && i + 1 < out.length && source.charAt(i + 1) == '*')
            {
                int end = source.indexOf("*/", i + 2);

                end = end < 0 ? out.length : end + 2;

                for (; i < end; i++) if (out[i] != '\n') out[i] = ' ';

                i--;
            }
            else if (c == '"' || c == '\'')
            {
                for (i++; i < out.length && source.charAt(i) != c && source.charAt(i) != '\n'; i++)
                {
                    if (source.charAt(i) == '\\' && i + 1 < out.length) out[i++] = ' ';

                    out[i] = ' ';
                }
            }
        }

        return new String(out);
    }

    /**
     * Where the brace that closes the one at `open` is.
     *
     * @param code source with its literals blanked
     * @param open the offset of an opening brace
     * @return the offset of the brace that closes it, or the last offset where none does
     */
    private static int closingBrace(String code, int open)
    {
        int depth = 0;

        for (int i = open; i < code.length(); i++)
        {
            if (code.charAt(i) == '{') depth++;
            else if (code.charAt(i) == '}' && --depth == 0) return i;
        }

        return code.length() - 1;
    }

    /**
     * Where an expression lambda's expression ends: the comma, semicolon or bracket that closes the argument it is.
     *
     * @param code source with its literals blanked
     * @param arrow the offset of the lambda's arrow
     * @return the offset just past the expression
     */
    private static int expressionEnd(String code, int arrow)
    {
        int depth = 0;

        for (int i = arrow + 2; i < code.length(); i++)
        {
            char c = code.charAt(i);

            if (c == '(' || c == '{' || c == '[')
            {
                depth++;
            }
            else if (c == ')' || c == '}' || c == ']')
            {
                if (depth-- == 0) return i;
            }
            else if (depth == 0 && (c == ',' || c == ';'))
            {
                return i;
            }
        }

        return code.length();
    }

    /**
     * The method a lambda is handed to: the name before the bracket that holds it, or nothing where it is not an
     * argument.
     *
     * @param code source with its literals blanked
     * @param from the offset of the lambda's arrow
     * @return the method's name, or ""
     */
    private static String receivingCall(String code, int from)
    {
        int parens = 0;
        int braces = 0;

        for (int i = from - 1; i >= 0; i--)
        {
            char c = code.charAt(i);

            if (c == '}')
            {
                braces++;
            }
            else if (c == '{')
            {
                if (braces == 0) return "";

                braces--;
            }
            else if (braces > 0)
            {
                continue;
            }
            else if (c == ';')
            {
                return "";
            }
            else if (c == ')')
            {
                parens++;
            }
            else if (c == '(')
            {
                if (parens == 0)
                {
                    int end = i;

                    while (i > 0 && Character.isJavaIdentifierPart(code.charAt(i - 1))) i--;

                    return code.substring(i, end);
                }

                parens--;
            }
        }

        return "";
    }

    /**
     * Every mutator `AutonomySession` has, read from its source rather than listed here: *"a guard that lists the cases
     * it knows about finds the cases it knows about"*.  Every `public` method whose name begins with a verb that
     * changes something, so a new mutator is covered the day it is written.
     *
     * @return the `session.name(` fragments to look for
     */
    private static java.util.List<String> writers() throws Exception
    {
        // The session's class, wherever it is (FR-136)
        String source = SourceOf.declaration("public class AutonomySession\n");

        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "public\\s+(?:synchronized\\s+)?(?:void|int|boolean)\\s+"
            + "((?:set|clear|forget|move|add|remove|pair|unpair|rename|place|write)[A-Za-z0-9_]*)\\s*\\(")
            .matcher(source);

        java.util.List<String> out = new java.util.ArrayList<>();

        while (m.find())
        {
            String fragment = "session." + m.group(1) + "(";

            if (!out.contains(fragment)) out.add(fragment);
        }

        assertTrue(out.size() >= 20,
            "only " + out.size() + " mutators were found on AutonomySession, which is fewer than it "
            + "has - so this derived the wrong list and the rule below would pass having looked for "
            + "almost nothing");

        return out;
    }
    /**
     * Every door that saves the setup reads whether the save worked.  `saveQuietly()` returns false rather than
     * throwing, so a caller that discards the answer turns a failed write into silence, and disk is authoritative after
     * the session rebuild: an undo that does not stick, a page excluded in memory and offered to autonomy on disk.
     * Read from source, since failing a real write needs a window and an unwritable file; what is pinned is that the
     * answer is CONSULTED.
     *
     * MUTATION: dropping the `!` from any call site fails this.
     */
    @Test
    public void testEverySaveOfTheSetupReadsWhetherItWorked() throws Exception
    {
        java.util.List<String> silent = new java.util.ArrayList<>();

        // EVERY FILE UNDER src/, not a list: a door in a file a list does not name would be outside the sweep.
        java.util.List<java.io.File> sources = new java.util.ArrayList<>();

        sources.addAll(javaFilesUnder(new java.io.File("src")));

        assertTrue(sources.size() > 50,
            "only " + sources.size() + " source files were found, so this rule is scanning almost "
            + "nothing and would pass whatever the code did");

        // The answer has to go somewhere.  "A `!` anywhere in the statement" is satisfied by
        // `if (this.model != null) session.saveQuietly();` (the `!=`); "a `!` applying to the call" flags
        // `saveQuietly() != true`, and an over-strict guard is one Adam has ruled worse than no guard.  So a discarded
        // call is `saveQuietly();` and nothing else, while every use puts something other than a semicolon after it.
        java.util.regex.Pattern discarded =
            java.util.regex.Pattern.compile("saveQuietly\\(\\)\\s*;");

        for (java.io.File source : sources)
        {
            String body = SourceOf.withoutComments(new String(
                java.nio.file.Files.readAllBytes(source.toPath()),
                java.nio.charset.StandardCharsets.UTF_8));

            // The declaration itself, and the session's own `return saveQuietly();`, which looks like a discarded call.
            if (source.getName().equals("AutonomySession.java")) continue;

            for (int at = body.indexOf("saveQuietly()"); at >= 0;
                at = body.indexOf("saveQuietly()", at + 1))
            {
                int from = Math.max(0, body.lastIndexOf(';', at) + 1);

                // A little past the call, so the semicolon after it is in view.
                String around = body.substring(from,
                    Math.min(body.length(), at + "saveQuietly()".length() + 4));

                if (discarded.matcher(around).find())
                {
                    silent.add(source.getName() + ": " + around.trim());
                }
            }
        }

        assertEquals(silent, new java.util.ArrayList<String>(),
            "a door saves the autonomy setup and discards the answer.  saveQuietly returns false "
            + "instead of throwing, and disk is authoritative after the session rebuild - so a failed "
            + "write there is silent, and what comes back next load is the state the write was meant "
            + "to replace (ACC-C6, OV2-C3): " + silent);
    }

    /**
     * The sendable-destination rule is written ONCE, anywhere in the program.
     *
     * A station autonomy may send a train to is `isDestination() && isActive() && isAutoDestination()
     * && !isReversing()`, and it lives in `Layout.isSendableDestination`. Everything else asks.
     *
     * **A guard that reads only `Layout.java` and counts clauses on ONE PHYSICAL LINE cannot see the copies
     * it exists to find**:
     *
     * - `AutoLocomotiveStatus.notChosenByAutonomy` lost two limbs and marked no parking berth;
     * - `AutonomyEditorPanel.countDestinations` has three clauses of four, overcounting by every reversing
     *   station in the number an operator reads when nothing moves;
     * - the decomposed spelling in `barredFromAutonomy` is the rule as a list of reasons.
     * So this asks the question the other way round: `isAutoDestination()` is the clause a copy cannot
     * be written without, so every file that mentions it is either the rule, an excused reader that
     * asks it for a DIFFERENT question, or a copy. The excused list is short and each entry says what
     * its different question is - which is what makes a new copy visible: it arrives unexcused.
     *
     * MUTATION: spelling the conjunction into any other class fails this by name.
     *
     * @throws Exception on a failure to read the sources
     */
    @Test
    public void testTheSendableDestinationRuleIsWrittenOnce() throws Exception
    {
        // Where the rule lives, and the places that legitimately name one clause for another purpose.
        java.util.Map<String, String> excused = new java.util.LinkedHashMap<>();

        excused.put("automation/Layout.java",
            "the rule itself, and `barredFromAutonomy` which decomposes it into named reasons");

        excused.put("automation/LayoutExplanations.java",
            "the body of Layout's `whyNotOfferedToOperator` (FR-136), which asks a DIFFERENT question of the clause: a"
            + " terminus a train that cannot reverse would be stranded at is kept out of the menu only where autonomy"
            + " may choose it, since a square it never chooses is a berth the operator puts trains away in (OB-205)");

        excused.put("automation/TrainFit.java",
            "the bodies of Layout's length rules (FR-136), which ask a DIFFERENT question of the clause: whether a train"
            + " stays (a berth, refused across another road) or passes (a station autonomy may choose, which its"
            + " measured route in may hold) - FR-087");

        excused.put("automation/Point.java", "the accessor");

        excused.put("automationui/AutonomyChecks.java",
            "names the clause in a comment explaining why a notice is drawn");

        excused.put("automation/HomeStaging.java",
            "asks a DIFFERENT question of the same clause: whether a square is a PARKING BERTH, which is where a"
            + " train that cannot reverse may be left facing out of - Adam's turn rulings of 2026-09-15 (AMV-B1,"
            + " AMW-B3).  Sendability is not asked here at all: Return Home is manual operation and ignores it, which"
            + " is why `canRest` tests `isDestination` and `isActive` and stops");

        excused.put("automationui/AutonomySession.java",
            "names the runtime rule in a comment, and narrows a set deliberately (MON-C1)");

        excused.put("gui/autonomy/AutoLocomotiveStatus.java",
            "a DELIBERATE lock-free copy - the rule is synchronized on the Layout and this runs on the"
            + " event thread three times per repaint. Kept honest by testWhyStuck"
            + " .testTheDashAgreesWithTheRule, which compares its answer with the rule's over every"
            + " case rather than asserting a mark (MON-B1)");

        java.util.List<String> unexcused = new java.util.ArrayList<>();

        java.util.List<java.io.File> sources = javaFilesUnder(new java.io.File("src"));

        assertTrue(sources.size() > 50, "only " + sources.size() + " sources were found, so this swept"
            + " almost nothing - the walk is broken rather than the program clean");

        for (java.io.File file : sources)
        {
            String body = SourceOf.withoutComments(new String(java.nio.file.Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8));

            if (!body.contains("isAutoDestination()")) continue;

            String where = file.getPath().replace(java.io.File.separatorChar, '/');

            boolean known = false;

            for (String excusedFile : excused.keySet())
            {
                if (where.endsWith(excusedFile)) known = true;
            }

            if (!known) unexcused.add(where);
        }

        assertTrue(unexcused.isEmpty(),
            "these ask whether a station is one autonomy may send a train to, and the rule is"
            + " Layout.isSendableDestination. A copy is how the dash came to mark no parking berth at"
            + " all and how the \"why is nothing considered\" count came to overcount by every"
            + " reversing station. Call the rule, or excuse it here saying what different question it"
            + " is asking: " + unexcused);

        // Still once inside the rule's own file: the sweep above cannot see a copy written next to the original.
        int conjunctions = 0;

        for (String line : SourceOf.allCode().split("\n"))
        {
            if (line.contains("isAutoDestination()") && line.contains("isReversing()")) conjunctions++;
        }

        assertEquals(conjunctions, 1,
            "the sendable-destination conjunction is written out " + conjunctions + " times in the program."
            + " There must be exactly one, inside isSendableDestination");
    }
    /**
     * The three gestures that change what track is blocked each tell the diagram.
     *
     * Adam: *"yes, this greyout should appear at idle and be regenerated if a placement or
     * train/track length is changed."*
     *
     * Two gestures are driven in `ui.testTheGreyAppearsAtIdleToo`; the third, `promptTrainLength`, opens a modal, so
     * its second half is `applyTrainLength`, driven there.  A door that stops calling `applyTrainLength` passes every
     * behavioral test, so the call is read here, and the fence too, so a merge cannot bring it back.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testEveryDoorThatChangesWhatIsBlockedTellsTheDiagram() throws Exception
    {
        // The fence is gone, asked of the whole program so it cannot return in whichever method the decision moves to.
        assertFalse(SourceOf.allCode().contains("greyed = isAutonomyBusy()"),
            "the grey wash is fenced on a running railway again.  The refusal it draws is not:"
            + " Layout.isPathClear sweeps the covered edges in every tier at every time, so at idle a"
            + " manual send is refused over track the diagram paints as free (W7B-B1).  Adam: \"yes,"
            + " this greyout should appear at idle\"");

        // THE LENGTH DOOR ASKS FOR THE MARKS AGAIN.
        String apply = codeOf("public void applyTrainLength(Locomotive l, int units)");

        assertTrue(apply.contains("blockedTrackChanged()"),
            "setting a train's length no longer asks the diagram to work the marks out again.  How far"
            + " a tail reaches is the length in hops, and Layout.edgesCoveredByStandingTrains reads it"
            + " off the locomotive every time - so the railway refuses different track the instant"
            + " this returns, with no rebuild to tell the drawing.  Adam: \"be regenerated if a"
            + " placement or train/track length is changed\"");

        // AND THE DIALOG STILL GOES THROUGH IT, which is the half the behavioral test cannot see.
        String prompt = codeOf("public void promptTrainLength(Locomotive l, MouseEvent evt)");

        assertTrue(prompt.contains("applyTrainLength("),
            "the train-length dialog no longer ends in applyTrainLength.  Everything that has to"
            + " happen once a length is chosen lives there - the findings, the log line and the grey -"
            + " and a door that writes the number itself gets none of it while every test of"
            + " applyTrainLength goes on passing");

        assertFalse(prompt.contains("l.setTrainLength("),
            "promptTrainLength writes the length itself as well as calling applyTrainLength, which is"
            + " two doors onto one field again - the shape MT-246 and OB-039 were both filed for");

        // AND THE PLACEMENT DOORS, which reach the marks through updateVisiblePoints.
        String gesture = codeOf("private boolean locomotiveGestureOnDiagram(int keyCode, boolean controlPressed)");

        assertTrue(gesture.contains("this.updateVisiblePoints();"),
            "the diagram's Control+X / Control+V / Delete door no longer refreshes the marks, so a"
            + " train moved from the keyboard leaves its old squares greyed - which is OB-180 exactly");
    }

    /**
     * The track diagram's own autonomy menu refreshes before it binds anything to a square: unrefreshed, the first
     * right-click on a run binds every item to the clicked square, not the run's leader.  Read from the source,
     * because the menu cannot be opened without a display.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheDiagramMenuRefreshesBeforeItBinds() throws Exception
    {
        String door = codeOf("public javax.swing.JPopupMenu buildAutonomyTileMenu(");

        int refreshes = door.indexOf("autonomyTileMenus.refreshRunLeaders()");
        int builds = door.indexOf("autonomyTileMenus.buildTileMenu(");

        assertTrue(refreshes >= 0,
            "the diagram's autonomy menu is built without refreshing the panel first, so it binds a run to the"
            + " square that was clicked rather than the square that speaks for the run (AUS-B1)");

        assertTrue(builds >= 0, "the door no longer builds a tile menu at all, so the check above tests nothing");

        assertTrue(refreshes < builds,
            "the refresh comes after the menu is built, which is the same as not refreshing at all");
    }


    /**
     * A cut asks for no autonomy setup it is not going to tell (OB-236): `deleteSelection` reads the session only when
     * it will tell it what was emptied.  Read rather than run, since every door reaching a cut has the session built.
     *
     * MUTATION: ask for the session before asking whether it will be told, and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testACutAsksForNoSetupItWillNotTell() throws Exception
    {
        // The method wherever it is; its body is LayoutEditorSelection's, the editor's form is a delegator.
        String body = codeOf("    boolean deleteSelection(boolean tellAutonomy)");

        int ask = body.indexOf("getAutonomySession()");

        assertTrue(ask > 0, "deleteSelection no longer asks for the session at all");

        String statement = body.substring(body.lastIndexOf(";", ask) + 1, body.indexOf(";", ask));

        assertTrue(statement.contains("tellAutonomy"), "deleteSelection asks for the autonomy setup whether or not it will"
            + " tell it, which on a cut builds one nobody needed (OB-236): " + statement.trim());
    }

    /**
     * The route editor's copy mark is drawn in outlines in whatever ink it is given, as its arrows, trash and plus are
     * (OB-258): a white-filled front sheet would be a solid white sheet on a selected row.  Painted with no display.
     *
     * MUTATION: fill the front sheet again, and this fails.
     */
    @Test
    public void testTheCopyMarkIsOutlinesInAnyInk() throws Exception
    {
        final int size = 24;

        final java.awt.Color ground = new java.awt.Color(38, 117, 191);

        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = picture.createGraphics();

        g.setColor(ground);
        g.fillRect(0, 0, size, size);

        org.traincontrol.gui.widgets.RowIcons.copy(size, java.awt.Color.WHITE).paintIcon(null, g, 0, 0);

        g.dispose();

        // The front sheet as the mark measures it, asked of RowIcons so a copy of its proportions cannot go stale.
        java.lang.reflect.Method sheet =
            org.traincontrol.gui.widgets.RowIcons.class.getDeclaredMethod("frontSheet", int.class, int.class);

        sheet.setAccessible(true);

        java.awt.Rectangle front = (java.awt.Rectangle) sheet.invoke(null, size, size);

        boolean inked = false;

        for (int y = front.y + 2; y < front.y + front.height - 2; y++)
        {
            for (int x = front.x - 1; x <= front.x + 1; x++) inked |= ((picture.getRGB(x, y) >> 16) & 0xFF) > 120;
        }

        assertTrue(inked, "precondition: the copy mark's front sheet has no left edge in white ink, so nothing was drawn");

        assertEquals(picture.getRGB(front.x + front.width / 2, front.y + front.height / 2) & 0xFFFFFF,
            ground.getRGB() & 0xFFFFFF, "the copy"
            + " mark's front sheet is filled rather than outlined, so on a selected row it is a solid sheet among outlines"
            + " (OB-258)");
    }

    /**
     * The "why is this train not moving" answer reads no setup collection on its worker.  `composeWhy` runs off the
     * event thread, and the setup's live collections (turn squares, barred sides, switched-off squares, the reduction)
     * are edited there: reading them mid-walk can throw or draw routes from half an edit.
     *
     * MUTATION: put `session.shutTiles()` back into `composeWhy`'s `findPath` call, and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheWhyWorkerReadsNoSetupCollection() throws Exception
    {
        String worker = codeOf("    AutonomyEditorPanel.WhyAnswer composeWhy(");

        for (String asked : new String[] {
            ".mandatoryTurnTiles(", ".mayTurnTiles(", ".barredArrivals(", ".shutTiles(", ".getReducer(" })
        {
            assertFalse(worker.contains(asked), "composeWhy runs on the WhyRenderer thread and calls "
                + asked.substring(1) + ") - a setup collection the event thread edits, read while a click may be"
                + " changing it, so the answer can throw part-way or draw routes from half an edit.  Capture it in"
                + " applyWhy, beside the station index (GSE-C3)");
        }
    }

    /**
     * The direction buttons' worker commands the locomotive it was pressed for and leaves Swing to the event thread:
     * `activeLoc` is captured before the thread starts, and the buttons are set in an `invokeLater`.
     *
     * MUTATION: command `this.activeLoc` again inside either thread, or set a button outside the `invokeLater`, and
     * this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheDirectionButtonsCommandTheLocomotiveTheyWerePressedFor() throws Exception
    {
        for (String declaration : new String[] { "private void backwardLoc()", "private void forwardLoc()" })
        {
            String body = codeOf(declaration);

            int started = body.indexOf("new Thread(");

            assertTrue(started >= 0, declaration + " no longer starts a thread, so this rule needs rewriting rather"
                + " than passing: " + body);

            String worker = parenthesised(body, started + "new Thread".length());

            // WHAT THE WORKER HANDS BACK belongs to the event thread, and may do both.
            for (int back = worker.indexOf("invokeLater("); back >= 0; back = worker.indexOf("invokeLater("))
            {
                int open = back + "invokeLater".length();

                worker = worker.substring(0, back) + worker.substring(open + parenthesised(worker, open).length());
            }

            assertFalse(worker.contains("setSelected("), declaration + " sets the direction buttons from its worker"
                + " thread.  Swing is single-threaded, so Forward and Backward are changed while the event thread may"
                + " be painting them (GST-C2): " + worker);

            assertFalse(worker.contains("activeLoc"), declaration + " reads the shown locomotive again on its worker,"
                + " so a press followed quickly by picking another locomotive stops and turns that one instead"
                + " (GST-C2): " + worker);
        }
    }

    /**
     * The text from an opening parenthesis to the one that closes it, both included.  Counted, not matched by a
     * pattern, because a thread's body holds lambdas and calls of its own.
     *
     * @param code the source
     * @param open the index of the opening parenthesis
     * @return the span, or the rest of the source when nothing closes it
     */
    private static String parenthesised(String code, int open)
    {
        int depth = 0;

        for (int i = open; i < code.length(); i++)
        {
            char c = code.charAt(i);

            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return code.substring(open, i + 1);
        }

        return code.substring(open);
    }
}
