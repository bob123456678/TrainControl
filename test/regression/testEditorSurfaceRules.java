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
import support.SourceOf;

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

    /** The form of `openLayoutEditor` that holds the refusals; the overloads above it begin with the same words. */
    private static final String OPEN_LAYOUT_EDITOR = "private void openLayoutEditor(String page, Boolean autonomy,"
        + "\n        final org.traincontrol.automationui.TileGraph.TileKey reveal, boolean remember,"
        + "\n        final java.util.List<org.traincontrol.automationui.TileGraph.TileKey> outline)";

    /**
     * The facing menu appears even when the square holds one facing (Adam, 2026-09-06).
     *
     * **"I also can't manually change 75 407 DB's direction when at bottommainpost."**
     *
     * `buildFacingMenu` returned null below two choices, so on a square holding one facing the
     * question was simply absent - and "no menu" reads as a fault rather than as an answer.
     *
     * **It is an answer, and not one the menu may override.** A facing has to be one the BUILD can
     * hold: `placementCopy` matches it against the copies the split made, and anything else falls
     * back to `startableCopy` and quietly turns the train round. Offering a second facing where the
     * railway holds one would be `facingsThatCannotBeHeld` manufactured on purpose - so the menu shows
     * the one facing, ticked, and a disabled line saying why there is nothing to choose.
     *
     * Checked as the gate rather than by opening the menu, which needs a window and a session.
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

        // GREYED, NOT REFUSED, where there is no facing (OB-347; Adam, 2026-10-13: "rather than hiding it, grey it out."),
        // which is what this used to hold the other way round
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
     * Adam, 2026-09-06: **"I added a locomotive named a,P[)(] and it went trough.  So, make sure this
     * doesn't blow things up elsewhere."**
     *
     * `RouteCommand.isNameUsable`'s own rule is that the doors setting a locomotive's name have to
     * agree, and four did: the rename dialog, both route-editor doors, and the one applying a name the
     * Central Station proposes. Every one of those RENAMES. Nothing asked about the name a locomotive
     * is BORN with, so the character the other four exist to keep out could be typed in there instead.
     *
     * **What it costs is not that locomotive.** Routes store locomotives by name in a comma-separated
     * format, so a comma in a name re-parses as two commands - and the route naming it was legal when
     * it was written. Brackets are allowed by Adam's own ruling; the comma is what `a,P[)(]` was
     * refused for.
     *
     * Checked as a door census rather than by driving the dialog, because the defect is a door that
     * does not ask - and a test that drives the four that do would have passed throughout.
     *
     * MUTATION: remove the call from any listed door and this fails, naming the door.
     */
    @Test
    public void testEveryDoorThatNamesALocomotiveChecksTheName() throws Exception
    {
        // COUNTED PER FILE, not merely present (DIR-C7).
        //
        // The first version asserted `source.contains("isNameUsable(")` per FILE, and
        // `TrainControlUI` holds TWO independent doors - the rename dialog and the one that applies a
        // name the Central Station proposes.  Removing the call from either left the other`s
        // occurrence satisfying the check, so the javadoc`s "remove the call from any listed door and
        // this fails" was untrue of exactly the file with more than one.
        //
        // LOCATED PER DOOR since FR-136, which a count per file could not do: each door is its own declaration,
        // found in whichever file holds it, so one of a pair going silent is named, and so is a door that moved.
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
     * One writer, and it repaints.
     *
     * The redraw is asked of the helpers' whole declarations, wherever they are, rather than of a window
     * of lines after them.
     */
    @Test
    public void testTheFacingIsWrittenFromOnePlaceAndThatPlaceRedraws() throws Exception
    {
        // A PREFIX, so a writer cannot escape this count by spelling (SPEC-B4).
        //
        // The facing menu now calls setFacingAndMove, which writes a facing and stands the train
        // on the copy that faces that way - and an exact match for "setFacing(" stopped seeing it
        // the moment it was renamed. A guard that a rename walks out of is not guarding.
        //
        // COUNTED OVER THE PROGRAM (FR-136).  Counted over AutonomyEditorPanel's file, a write moved out of it
        // left the count; over every file, each write is one of the doors the message below names.  And by any
        // handle a door holds the session by - `session`, `session()`, `getSession()`, `getAutonomySession()` - so a
        // write moved out behind an accessor is still counted.
        int writes = count(FACING_WRITE, SourceOf.allCode());

        // The redraw lives in radio(), the helper every one of these answers is built with -
        // not beside this particular write. It was beside this one once, which is exactly how
        // TD-1 happened: the station and turning radios sat in the same helper and got nothing.
        //
        // `placementChanged(null)`, not `placementChanged()`: the door now says WHICH
        // placements it edited, and a radio edits none (D2-A1).  Matching the bare name
        // would be satisfied by the declaration itself, which is not a call.
        boolean redrawn = codeOf("    javax.swing.JMenuItem radio(").contains("placementChanged(null)");

        // The TOGGLE helper as well, which this used to leave out (TD-1).
        //
        // Pinning radio() alone said "the facing redraws" and let its sibling keep the bug: toggle()
        // carries four setup writes reachable from the track diagram's own menu, and one of them -
        // whether trains may arrive by a side - decides how a square SPLITS, which is the case the
        // rebuild exists for. A rule with two implementations needs both named, which is the shape
        // this whole file is about.
        //
        // Its whole declaration, not sixty lines after its signature: the window had to grow once already when
        // the method did (C11), and a declaration cannot borrow a placementChanged() from whatever comes next.
        assertTrue(codeOf("    javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey")
                .contains("placementChanged(null)"),
            "the menu's toggle helper writes the setup and only refreshes. Four settings go "
            + "through it, and one of them changes how a square splits - so the running layout "
            + "keeps Points the setup no longer has, and everything that goes through their "
            + "names quietly stops working (TD-1)");

        // TWO, AND THE SECOND IS NOT A MENU (REG6-B5).
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
        // TWO, since REG8-B2 moved the third out.  The edit-or-assign door used to write the facing
        // here; it now goes through `GraphLocAssign.commitAndRecord`, because the TRACK DIAGRAM has the
        // same item and never had the second half at all - an assignment made there reverted on the
        // next configuration load.  Adam: "there needs to be parity across the board."
        //
        // That is a reduction in writers, which is the direction this check wants.  A THIRD here still
        // fails, and the message says what to ask about it.
        //
        // FIVE OVER THE PROGRAM (FR-136): the editor's two, and the three in the files the list below names -
        // GraphLocAssign.commitAndRecord, the diagram's placeFacing and the window's rememberPlacement.
        assertEquals(writes, 5,
            "the facing is written from " + writes + " places in the program. Five are known.  Two are in "
            + "AutonomyEditorPanel: the facing menu (which moves the railway too, SPEC-B4) and placeLocomotive - the "
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

        // AND EACH OF THE FIVE IS WHERE IT IS SAID TO BE (FR-136).  A total lets one door stop writing the facing
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

        // AND BOTH ASSIGNMENT DOORS GO THROUGH THE ONE THAT RECORDS.
        //
        // `commitChanges` moves the running layout and nothing else.  A door that calls it directly
        // has made an assignment the setup will not remember - which is exactly what the track
        // diagram's item did, silently, for as long as it existed.
        //
        // Asked of the whole program (FR-136): the one `edit.commitChanges()` allowed is the one inside
        // commitAndRecord, which records as well.
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
                // GraphLocAssign ADDED BY REG8-B2, with the reason this list asks for.  It is the
                // DIALOG both assignment doors show, so what accepting it means - commit, place,
                // record the facing - belongs with it rather than being written out twice.  It was
                // written twice, and the track diagram copy never had the recording half at all: an
                // assignment made there reverted at the next configuration load.  It redraws through
                // its callers, both of which repaint immediately after calling it.
                "GraphLocAssign.java", "LayoutRightclickAutonomyMenu.java",
                // SessionImport is the legacy import's facings (FR-136), written behind
                // AutonomyViewerPanel.importLegacyGraph, which reloads the railway after.
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
        // Counted over the whole program's code (FR-136), so a second copy built in another file is counted too
        int built = SourceOf.occurrences("menuFacingGroup");

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
        //
        // EVERY CLEAR IN THE PROGRAM, each INSIDE a finally block (FR-136): the 400 characters before it were a
        // proxy for that, and a block that had closed before the clear satisfied it as well.
        //
        // Inside means the finally's OWN brace has not closed when the clear is reached.  Counting every brace
        // between the two let a clear in a later method's `if` pass: the finally and its method close, two more
        // open, and the sum still says "inside".
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
     * And every part of the de-clutter is asked the same question.
     *
     * Greying, arrows and lengths are three separate decisions in `annotationFor`. A de-clutter that is
     * on for one of them and off for the others reads as a diagram that is half-dressed rather than
     * focused - and three numbers that have to agree is exactly the shape that produced OB-037.
     */
    @Test
    public void testTheDeclutterIsOneDecision() throws Exception
    {
        // The method that makes the three decisions, wherever it is (FR-136); `focused` is its local.
        String decisions = codeOf("public org.traincontrol.automationui.TileAnnotation annotationFor(TileKey tile)");

        int uses = 0;

        for (String line : decisions.split("\n"))
        {
            if (line.contains("focused") && !line.contains("isFocusedOnSignals()")) uses++;
        }

        // Three: the greying, the arrows, the lengths.  The declaration is excluded above, so this
        // counts the places that USE the answer rather than the place that works it out.
        assertEquals(uses, 3,
            "the signal focus is used at " + uses + " places in annotationFor, not the three it "
            + "has to reach - the greying, the arrows and the lengths. A de-clutter that is on for one "
            + "of them and off for the others looks half-dressed rather than focused");

        // AND NOTHING ELSE ASKS IT (FR-136).  Counted in annotationFor alone, a de-clutter of its own in another
        // method was not seen, whether it asks the panel's question or reads the flag under it.
        assertEquals(outside("isFocusedOnSignals()",
                "public org.traincontrol.automationui.TileAnnotation annotationFor(TileKey tile)",
                "public boolean isFocusedOnSignals()"), 0,
            "the signal focus is asked somewhere besides annotationFor, so the de-clutter is decided twice - and two "
            + "decisions that have to agree are the shape that produced OB-037");

        assertEquals(outside("signalWindowOpen", "private boolean signalWindowOpen;",
                "    void pairGuardSignals(Guard guard, TileKey station)",
                "public boolean isFocusedOnSignals()"), 0,
            "the flag under the signal focus is read somewhere besides isFocusedOnSignals, so a de-clutter is decided"
            + " there without asking the one question annotationFor asks");
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
        // Every line of the program's code (FR-136): a grid's code moved to another file is still read.
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
        // Every line of the program's code (FR-136), so a rename door in another file is asked as well
        String[] lines = SourceOf.allCode().split("\n");

        // Every place that names a square and then labels it - there are two, and the reason this
        // test exists is that the fix went on one of them first.
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
        // Over the whole program's code (FR-136), so a copy in another file is counted too
        int found = SourceOf.occurrences("load(activeDiagramConfiguration, false, false)");

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

        java.util.Set<String> found = new java.util.TreeSet<>();

        for (File file : javaFilesUnder(src))
        {
            String body = SourceOf.withoutComments(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

            // The declaration is not a write, and neither is a reference to it in a javadoc - which
            // the comment stripping above has already taken care of.
            body = body.replace("public void setFacing(", "");

            if (body.contains(call)) found.add(file.getName());
        }

        return new ArrayList<>(found);
    }

    /**
     * The code of one declaration, comments blanked, from whichever file under src/ holds it (FR-136).
     *
     * @param signature enough of the declaration to name it once in the program
     * @return its code
     */
    private static String codeOf(String signature)
    {
        return SourceOf.withoutComments(SourceOf.declaration(signature));
    }

    /**
     * A member of one class, for a signature more than one class declares: the class is found wherever it is, and
     * the member in it (FR-136).
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
     * How many times a text is in the program's code outside the given declarations - a negative asked of the
     * whole program where the needle has a place it is allowed (FR-136).
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

    /**
     * A write of a locomotive's facing through the setup, by whatever the door calls its handle on the session.
     */
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
     * Every door that runs a route asks whether it is already running (SVN-B7).
     *
     * `executeRoute`'s own comment has always said *"Every door that runs a route comes through
     * here."*  Every door did; the guard was not in it.  It sat on the play button alone - so clicking
     * the same route's row and confirming, or right-clicking it and choosing Execute, started a second
     * run of a route that was already running: two threads throwing the same accessories, each
     * unlocking what the other had locked.
     *
     * The button greys while its route runs and that is the AFFORDANCE; the check inside executeRoute
     * is the GUARD, and OB-057 and OB-090 are both this shape - the two must ask one question.  Which
     * is also why the play-button branch must NOT ask it again: a second copy per door is exactly how
     * the other two came to be missing.
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

        // AND THE DOOR DOES NOT ASK IT AGAIN.
        //
        // Counting every mention would be wrong: the cell painter and the hover both read the same
        // set to decide what to DRAW, and drawing is the affordance rather than the guard.  What must
        // not come back is a door deciding for itself whether to call executeRoute, because that is
        // the arrangement under which two of the three doors were missing the question entirely.
        String door = codeOf("    void RouteListMouseClicked(java.awt.event.MouseEvent evt)");

        // NOT VACUOUS WHEN THE HANDLER MOVES (V33-C7).
        //
        // `bodyOf` answered "" when it could not find the declaration, and an empty string contains
        // nothing - so a renamed or REGENERATED handler turned the assertion below into a no-op that
        // still read as protection.  This one is a NetBeans `//GEN-FIRST` method, which is exactly
        // the kind that gets regenerated.  `SourceOf.declaration` fails loudly instead, wherever the
        // handler has gone (FR-136).

        assertFalse(door.contains("routesExecuting"),
            "the route row's own click handler asks whether the route is already running instead of "
            + "leaving it to executeRoute.  One door asking for itself is how the other two came to "
            + "be missing it.  Body: " + door);

        // AND THE WINDOW'S OWN HANDLER.  The one-line GEN handler in TrainControlUI hands the click to the route list
        // (FR-136); it must not take the question back, or the door asks for itself from the window while the panel's
        // copy stays clean.
        String handler = codeOf("private void RouteListMouseClicked(java.awt.event.MouseEvent evt)");

        assertFalse(handler.contains("routesExecuting"),
            "the window's own route-row click handler asks whether the route is already running instead of handing the "
            + "click to the route list and leaving the question to executeRoute.  Body: " + handler);
    }

    /**
     * Switching an accessory by hand asks both halves of the question a route asks (SVN-B16).
     *
     * `MarklinRoute.heldReason` refuses two things: an accessory on a locked path, and a signal
     * protecting a platform somebody is standing at.  The diagram's own accessory tile asked only the
     * first - so a route setting a platform's signal green was refused, and clicking the same signal
     * green on the same window was not.  The second is the one `getActiveAccs` cannot see, because a
     * protecting signal is driven by occupancy rather than by an active path.
     *
     * The rule lives on `Layout` now so every door can ask it, and there are THREE of them: the route,
     * the diagram tile, and the switch keyboard, which asked nothing at all until `V31-C2` (`SVN-B17`,
     * `AU-C12`).  This pins that all three do.
     *
     * The aspect half moved with the rule.  `Layout.clearsProtection(accessory, commandingGreen)`
     * carries it, and each door supplies only which way IT is about to command the accessory - a tile
     * toggles, so its green direction is `!isStraight()`; a keyboard button sets an absolute state, so
     * its green direction is "the button is not selected".  Reading the aspect at the keyboard would
     * answer about where the accessory is rather than where it is being sent.
     *
     * MUTATION: deleting either call to `aboutToClearProtection`, dropping `commandingGreen` from the
     * rule, or taking the question off any one of the three doors fails this.
     */
    @Test
    public void testSwitchingAnAccessoryByHandAsksAboutProtectingSignals() throws Exception
    {
        // The tile's constructor, which wires its click, and the helper the click asks (FR-136: found wherever
        // they are)
        String label = codeOf("public LayoutLabel(LayoutDiagramComponent c, Container parent, int size, "
            + "TrainControlUI tcUI, boolean edit,");

        String helper = codeOf(
            "private static boolean aboutToClearProtection(TrainControlUI tcUI, Accessory accessory)");

        // BOTH LIMBS, and the aspect (TS3-B3, WK3-B1).
        //
        // This used to grep the whole file for the method name, so deleting either of the two calls -
        // the accessory and its twin on a three-way - left it passing, which is exactly the vacuous
        // shape TCX-A3 was fixed for the day before. And the name being present says nothing about
        // whether the ASPECT is asked with it: without that, clicking a protecting signal to RED, the
        // protective act, raises a warning the route door deliberately does not.
        assertTrue(label.contains("aboutToClearProtection(tcUI, c.getAccessory())")
                && label.contains("aboutToClearProtection(tcUI, c.getAccessory2())"),
            "the diagram's accessory tile does not ask about BOTH accessories of the tile, so a "
            + "three-way's second drive can be switched with no warning");

        assertTrue(helper.contains("clearsProtection(accessory, !accessory.isStraight())"),
            "the tile's protecting-signal question does not ask which way the click will throw the "
            + "signal.  Setting a protecting signal to RED is the protective act, and the route door "
            + "explicitly permits it - a warning here is the over-strictness that door had removed");

        // AND THE HELPER STILL ASKS THE RULE (V32-B2).
        //
        // The three assertions above are about the CALLS.  Gutting the helper they call - its last
        // line to `return false;` - passes every one of them, and SVN-B16 is undone at the tile door
        // with the test still green.  That is the same shape as TCX-A3, which is what these
        // assertions replaced.
        assertTrue(helper.contains("clearsProtection(accessory"),
            "the tile's protecting-signal helper no longer asks the shared rule at all, so the doors "
            + "are back to different definitions of one question.  Body: " + helper);

        // THE BODY, not the file (V33-C6).
        //
        // Read whole, this passes on the method name appearing in a comment - and `MarklinRoute`'s
        // comments discuss this rule at length, which is how the assertion would have survived the
        // call being deleted.
        String held = codeOf("private String[] heldReason(RouteCommand rc)");

        assertTrue(held.contains("protectsAnOccupiedSquare"),
            "the route no longer asks the shared rule, so the two doors are working from different "
            + "definitions of the same question again.  Body: " + held);

        // The rule the doors delegate to, declared once in the program - SourceOf fails on none or on a copy -
        // and on Layout, where the doors reach it
        SourceOf.declaration("public boolean protectsAnOccupiedSquare(Accessory accessory)");

        assertNotNull(org.traincontrol.automation.Layout.class.getMethod("protectsAnOccupiedSquare",
                org.traincontrol.base.Accessory.class),
            "the rule the doors delegate to is not on Layout, so one of them is carrying a copy");

        // AND THE ASPECT IS PART OF THE RULE, not of one caller (V31-C2).
        //
        // It used to be a line in the tile's own helper, which is why the keyboard could not ask it
        // without copying it.  Dropping the direction from the signature would let every door warn
        // about a click that sets a signal to danger - the over-strictness the route door had removed.
        String rule = codeOf("public boolean clearsProtection(Accessory accessory, boolean commandingGreen)");

        assertTrue(rule.contains("commandingGreen") && rule.contains("protectsAnOccupiedSquare"),
            "the shared rule no longer asks both halves - which way the accessory is being commanded, "
            + "and whether it protects somewhere occupied.  Body: " + rule);

        // AND THE THIRD DOOR (SVN-B17, V31-C2, AU-C12).
        //
        // The switch keyboard went straight to setAccessoryState with no check of any kind, while the
        // two doors above asked two questions each.  It is the door most likely to be forgotten again,
        // because it is the one that never had a guard to begin with.
        // Missing, `SourceOf.declaration` fails: the switch keyboard has no guard at all - a turnout on a locked
        // path can be thrown from it under a running train, which is AU-A2 through the one door that never asked
        String keyboard = codeOf(
            "private boolean keyboardAutonomyAllows(int address, javax.swing.JToggleButton button)");

        assertTrue(keyboard.contains("clearsProtection") && keyboard.contains("getActiveAccs"),
            "the switch keyboard asks fewer than both halves of the question the other two doors ask.  "
            + "Body: " + keyboard);

        // AND THE POWER QUESTION (MT-261 ruling 4).
        //
        // Adam: "yes, route through the same guards."  A command sent with the power off does nothing,
        // and the keyboard used to send it into the dark leaving the button looking as though it had
        // worked - the one question of the three that is about whether anything can happen at all.
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

        // THE COLOUR A HOVERED SQUARE REALLY WEARS, asked of the editor rather than chosen here: handing the outline
        // a colour of this test's own and reading the same colour back proves only that the outline paints what it
        // is given.
        java.lang.reflect.Field hoveredColour =
            LayoutEditor.class.getDeclaredField("COMPONENT_BORDER_HOVERED_COLOR");

        hoveredColour.setAccessible(true);

        java.awt.Color wornWhenHovered = (java.awt.Color) hoveredColour.get(null);

        java.lang.reflect.Field restingColour =
            LayoutEditor.class.getDeclaredField("COMPONENT_BORDER_DEFAULT_COLOR");

        restingColour.setAccessible(true);

        java.awt.Color wornAtRest = (java.awt.Color) restingColour.get(null);

        // SEEN: opaque, and not the colour of the grid line a square rests in with the grid on
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
        // Every line of the program's code (FR-136), so a reset written in another file is asked as well
        String[] lines = SourceOf.allCode().split("\n");

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
        // The clear item is ALWAYS about a station - it is only offered when one is captioned - so
        // there is no reading of the code in which an un-named clear is correct.  Asked of the whole
        // program's code (FR-136), so a menu built in another file cannot offer it either.
        String code = SourceOf.allCode();

        assertFalse(code.contains("\"autosetup.ui.menuClearStationHere\""),
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

            assertEquals(code.contains("I18n.f(\"autosetup.ui." + key + "\""), placeholder,
                "autosetup.ui." + key + " reads \"" + value + "\", which " + (placeholder
                    ? "carries a placeholder - so it has to be fetched with I18n.f, or the {0} itself "
                      + "appears on the menu"
                    : "carries no placeholder - so fetching it with I18n.f passes an argument nothing "
                      + "uses, which is a caller under the impression it is naming the station"));
        }

        // And both live in the shared method, so a menu built somewhere else cannot quietly offer an
        // un-named pair of its own.
        // Gone, `SourceOf.declaration` fails: it is the one place both menus build their caption items, and
        // this rule is about it being one place - so if it was split, this test should be rewritten rather
        // than deleted
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
        // Gone, `SourceOf.declaration` fails: it is the one answer to 'would the editor open', and the menu item
        // and openLayoutEditor are both supposed to be reading it
        String predicate = codeOf("public String whyAutonomyEditorCannotOpen()");

        // The five-argument form, which the others hand on to with the squares a finding names beside the
        // one revealed (RLV9-C5).  Not found, SourceOf fails rather than this test reading nothing and passing
        // however far the two had drifted.
        String opener = codeOf(OPEN_LAYOUT_EDITOR);

        java.util.List<String> refusals = new java.util.ArrayList<>();

        // A refusal is said either as its own sentence or, where a run holds the setup, as the one that names what runs
        // (`whyTheSetupIsHeld`, which takes the autonomy sentence's key)
        for (String opening : new String[] {"I18n.t(" + QUOTE, "whyTheSetupIsHeld(" + QUOTE})
        {
            for (int at = opening.length(); (at = opener.indexOf(opening, at)) >= 0; at++)
            {
                int from = at + opening.length();
                int to = opener.indexOf(QUOTE, from);

                if (to > from) refusals.add(opener.substring(from, to));
            }
        }

        // THREE LITERALS AND ONE INDIRECTION since OB-126.
        //
        // The fourth refusal used to be a literal here. It now goes through
        // `reasonLayoutIsNotEditable()`, because the Edit button has three causes of greyness and this
        // door was naming the first one whatever was true - telling somebody with no layout loaded to
        // close an editor they do not have.
        //
        // The count is kept as a shape check, at the shape it now is: it exists to notice a refusal
        // being added or removed without this test being looked at, and lowering it without putting
        // the indirection below in its place would have been the test quietly checking less.
        assertTrue(refusals.size() >= 3,
            "openLayoutEditor shows fewer messages than the refusals it had when this was written, "
            + "so what this test is matching against has changed shape: " + refusals);

        assertTrue(opener.contains("reasonLayoutIsNotEditable()"),
            "openLayoutEditor no longer asks which of the Edit button's three reasons applies, so "
            + "either a refusal has gone or it is back to naming one reason whatever is true "
            + "(OB-126)");

        // And the case that indirection covers still has an answer in the menu's predicate, which is
        // what this whole test is about: a live menu item that leads to an error box.
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
     * The covered-track wash survives a highlight, stays out of the editors, and blocks what it draws.
     *
     * Adam, 2026-09-07, four reports against the length work, and **every one of them had a green test
     * over it**. That is the finding, and each has the same shape: the tests asserted the RULE and
     * nothing asserted the DRAWING or the fixture had no switch in it.
     *
     * - **"Shaded tiles get overwritten if an accessory change highlights the same square."** The
     *   restore put back `lastIcon`, the ungreyed icon. Nothing tested it because nothing tests icons:
     *   `testATrainCoversTheTrackBehindIt` asserts which EDGES are covered, which was right the whole
     *   time. The comment above the restore even argued for it.
     * - **"EN57-203 is allowed to traverse a blocked/shaded switch."** Coverage is per edge for
     *   routing and per square for drawing, and at a switch those differ. Every tail fixture in the
     *   suite is a straight chain of two or three points with no switch and no lock edges, so no test
     *   could tell the two apart - the same reason `VAL8-A1`'s one-way-rail gap went unseen.
     * - **"Slightly lighter."** A colour nothing asserts.
     * - **"Don't show shading in the autonomy or diagram editor."** The greying was written with no
     *   `edit` guard at all, and no test builds a label in editor mode and looks at its icon.
     *
     * Source-level, because all four are about what a Swing component draws: an icon comparison needs
     * a rendered label and a real image, and this file's convention is to assert the decision rather
     * than the pixels.
     *
     * **Restated for MT-309**, where both marks became things PAINTED over the icon rather than a
     * greyed copy of it. The first report is now impossible by construction and the rule is asked of
     * the mechanism that carries it.
     *
     * **And restated again on 2026-09-09**, when Adam put the wash back beside the line: *"'train is
     * here' should also mean 'track is blocked' - that is the whole point.  it's the same as greying
     * out edges, just in a different way."*  This asked for the absence of a wash until then, which
     * was the right rule for one day.  What it asks now is that there is exactly ONE wash and that it
     * is the painted one: the icon-tinting helper stays deleted, because a tinted icon is the version
     * a highlight can take away with it, which is the first report on this list.
     *
     * **And restated once more for MT-278 item 1**, which moved the refusal mark out of
     * `paintCoveredMark` and into `paintComponent`: Adam asked for *"the same tile with more
     * transparency"*, and a fade has to be applied to the icon as it is painted rather than drawn over
     * it afterwards.  The rule is the one it always was - the mark is applied at PAINT time and never
     * tinted into the image - and a composite satisfies it for the same reason a fill did: every
     * repaint re-applies it, so a highlight cannot take it away.
     *
     * MUTATION: deleting the `paintCoveredMark` call fails the first; dropping the `edit` guard in
     * `coveredRoads` fails the second; removing the lock sweep in isPathClear fails the fourth; fading
     * without asking `isTrackBlocked`, or after the train mark rather than before it, fails the
     * third.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheCoveredWashIsDrawnWhereItShouldBe() throws Exception
    {
        // The program's code, so each mark is found wherever the tile's drawing goes (FR-136)
        String label = SourceOf.allCode();

        // THE HIGHLIGHT CANNOT TAKE THE MARK WITH IT (MT-309 restates this rule).
        //
        // Adam, 2026-09-07: "shaded tiles get overwritten if an accessory change highlights the same
        // square."  While the mark WAS the icon, the rule was "the restore must put the greyed copy
        // back", and this asked for exactly that spelling.
        //
        // The mark is a line PAINTED over the icon now, so the hazard is gone by construction rather
        // than by a branch: whatever icon the restore puts back, the next paint draws the train on
        // top of it.  What has to hold instead is that the paint really does it - which is the two
        // assertions below, and they are the same rule asked of the mechanism that now carries it.
        // THE TIMER IS A FIELD SINCE UIX-B2, because a flash starting over it has to be able to end it - it was
        // a local called `restore`, and this rule knew only that spelling.
        int timer = label.indexOf("new javax.swing.Timer(HIGHLIGHT_DURATION");

        assertTrue(timer >= 0,
            "the highlight timer has gone, so this checked the absence of something that is not there");

        assertTrue(label.contains("paintCoveredMark(mark)"),
            "nothing paints the train mark, so a covered square is drawn as ordinary track and the"
            + " transient highlight has nothing left to overwrite because there was never anything"
            + " there (Adam, 2026-09-07 and MT-309)");

        // AND THE WASH IS THE PAINTED ONE, DRAWN UNDER THE LINE (2026-09-09).
        //
        // Adam asked for both marks: "'train is here' should also mean 'track is blocked' ... it's the
        // same as greying out edges, just in a different way."  Two things have to hold about the one
        // that came back.
        //
        // It is FENCED, on the window's answer rather than on anything the tile works out: "can we
        // just grey out the tiles just like blocked edges WHILE AUTONOMY IS RUNNING".
        //
        // **IN `paintComponent` SINCE MT-278 ITEM 1, and it had to move there.**  Adam: *"I want the
        // shading to instead be the same tile with more transparency exactly like what happens when
        // edges are locked in autonomy mode."*  A fade is applied to the icon AS IT IS PAINTED, which
        // is a thing only the method that paints it can do - a fill drawn afterwards cannot make what
        // is underneath more transparent.  It was a `fillRect` in `paintCoveredMark` until then.
        String painting = SourceOf.withoutComments(memberOf("public final class LayoutLabel extends JLabel",
            "protected void paintComponent(java.awt.Graphics g)"));

        assertTrue(painting.contains("tcUI.isTrackBlocked(square)"),
            "the refusal mark is drawn without asking whether the square is blocked, so it either"
            + " never appears or appears on free track");

        // AND IT IS APPLIED TO THE ICON, NOT PAINTED OVER IT.  That is what makes it survive a
        // highlight - see the ImageUtil claim below for the other half of the same rule.
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

        // AND THERE IS ONLY ONE OF IT.  The icon-tinting helper was deleted when the wash was removed,
        // and bringing the wash back as a fill is what keeps that deletion honest: a tinted icon is
        // the version an accessory highlight can take away with it, which is the first report above.
        //
        // Asked of the whole program (FR-136): the helper was ImageUtil's, and a tinting helper anywhere is the
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
     * The arrival side is written and read in ONE vocabulary - the BUILD’s (OB-182, MON-A1).
     *
     * `arrivedFrom` is written by the arrival and by three operator doors, and consumed by the walk
     * that blocks the track behind a standing train. They have to agree about what "N" means, and two
     * answers were available:
     *
     * - **the geometry** - `Layout.sideTowards`, the compass direction of the neighbouring POINT. A
     *   Point is the far end of a reduced run that can cross several tiles and turn corners, so a rail
     *   leaving north and curving east reaches a neighbour lying east and this answers "E".
     * - **the build** - the reduced edge’s entry side, the side the metal actually comes in by, which
     *   is what the builder splits the square on.
     *
     * Adam met the difference twice: a paste prompt offering the wrong pair, and *"placing a train on
     * bottommainc asks about arrival from the west or the north, whereas it should be east or west"*
     * (OB-182).
     *
     * **The first attempt corrected the doors alone and had to be reverted the same day.** The labels
     * became right, the walk went on matching the geometry, nothing matched on a curve, and the track
     * behind a standing train was not blocked at all. A wrong label is cosmetic; a protection that
     * silently does nothing is not.
     *
     * So the build’s answer was given to the model: `AutonomyBuilder` writes `entrySide` into each
     * emitted edge, `parseAuto` reads it onto the runtime `Edge`, and `Layout.entrySideOf` is the one
     * definition both the arrival write and the tail walk call. The doors then speak the same language
     * as the thing that consumes what they write.
     *
     * MUTATION: pointing either reader back at `sideTowards`, or handing either door `null` instead of
     * the session’s arrival sides, fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheArrivalSideIsWrittenAndReadInOneVocabulary() throws Exception
    {
        // The program's code (FR-136), so a reader or a writer is found in whichever file holds it
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
     * Adam, 2026-09-07: *"the 'arrived from' menu option is only visible in the autonomy editor, not
     * the track diagram viewer.  Move the setting into the '<locomotive> is facing' menu for both, with
     * clear intro headings for each set of options."*
     *
     * **Why no test caught it.** The two menus were both built and both correct; what was missing was
     * one of them being ADDED on one surface. `testBothAutonomyMenusNameTheSquareTheSameWay` checks
     * that the two menus agree about a square's name - it was written for exactly this class of fault
     * and asks about naming rather than membership, so a menu absent from one surface is invisible to
     * it. The viewer's route into these menus is `TrainControlUI.facingMenuFor`, which asks for the
     * facing menu and nothing else, and nothing asserted what that menu contains.
     *
     * Folding the items in rather than nesting a submenu also removes the ability to drift: there is
     * one menu now, so the two surfaces cannot offer different things.
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

        // AND IT IS NOT ALSO ADDED SEPARATELY, which is what put it in one surface and not the other.
        //
        // Asked of the whole program (FR-136).  This read `addLocomotiveItems` only when that method existed,
        // and it has gone - so the claim had stopped running at all.
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
     * Adam, 2026-09-07: *"in the home locomotive assignment box, 'use current' should be the train
     * currently parked at the station.  Rename the current behavior to 'use active', and add a second
     * button for 'use current' if a train is parked there."*
     *
     * The old button said "use current" and meant the locomotive being DRIVEN - the one question the
     * word "current" does not obviously answer in a dialog about a station. They are different trains
     * and both are worth a shortcut: active is the one in your hand, current is the one standing in
     * front of you, and giving that one its own home is the more common gesture.
     *
     * **Each is offered only when its locomotive is in the list**, so neither button can pick something
     * the list itself would refuse - the property the single button already had and the one most easily
     * lost when a second is added beside it. And they are not offered twice when they are the same
     * train, which is the ordinary case of driving the loco you are standing at.
     *
     * Source-level: the picker is a modal dialog, and a modal dialog in a test is a hang rather than a
     * failure - the rule this file follows for `addLocMappingPage`.
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
     * Adam: **"moving a train in the track diagram editor from tunnelleftpark to bottommaina showed its
     * direction as eastbound in its station label, but westbound in the right click menu."**
     *
     * They had different sources, and the split was deliberate - which is why it drifted rather than
     * being caught. The label reads which COPY of a split square the train is standing on, because that
     * IS its direction and it is true however the train got there; it was moved off the stored facing
     * on purpose, because the stored value is written only when somebody places a train BY HAND, so the
     * arrow appeared for a train you had placed and vanished for one autonomy had driven there. The
     * MENU was left on the stored value. From that day the two agreed only while a hand-placed train
     * had not moved.
     *
     * `AutonomySession.facingOnTheRailway` is the one reading, and the stored facing is the fallback
     * rather than the source: an empty square has no train whose direction it could be.
     *
     * The WRITE was already shared - both surfaces go through `setFacingAndMove`, which writes the setup
     * and moves the train to the matching copy - so this closes the last half.
     *
     * MUTATION: pointing either surface back at `getFacing(target)` alone fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheLabelAndTheMenuAgreeAboutFacing() throws Exception
    {
        // Gone, `SourceOf.declaration` fails: the shared reading of which way a standing train faces has gone, so
        // the label and the menu are each working it out again - which is how they came to disagree (OB-181)
        SourceOf.declaration("public Side facingOnTheRailway(TileKey square,");

        // The facing menu, and the program's code for the label, wherever each is (FR-136)
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
     * Adam reported it as a locomotive teleporting when a home was changed, and ruled on the fix:
     * **"option 1"** - the railway wins - and **"on exit or editor load or editor close, save to the
     * setup file. this is how it was in 2.8.x."**
     *
     * The rebuild regenerates every placement from the setup. That is safe only while the setup knows
     * where the trains are, which `openLayoutEditor` arranges by capturing on the way in - and which
     * stops being true the moment anything moves a train afterwards. His ruling settles it: where a
     * train IS is a fact, and where the file thinks it is is a record.
     *
     * **Placements only.** Folding the whole running layout back is what deleted a declined edit
     * (ACC-B3), because the capture removes what the layout does not carry and the layout at that
     * moment predates the edit. Carrying the placements across carries nothing that was authored.
     *
     * MUTATION: dropping either helper from the rebuild restores the teleport; dropping the capture
     * from the close leaves the file saying where the trains started until the next open.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testARebuildLeavesTheTrainsWhereTheyAre() throws Exception
    {
        String rebuild = codeOf("public void rebuildRunningLayoutFromSetup(boolean sayIfDeclined,");

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

        // AND THE THIRD SAVE MOMENT.  Exit and editor-open already called captureRunningLayout; close
        // did not, so between a close and the next open the file said where the trains had started.
        String closed = codeOf("public void autonomyEditorClosed()");

        assertTrue(closed.contains("captureRunningLayout()"),
            "closing the editor no longer writes the setup file, so where the trains are is lost until"
            + " something else captures - Adam: \"on exit or editor load or editor close\" (OB-183)");

        assertTrue(closed.indexOf("rebuildRunningLayoutFromSetup()")
            < closed.indexOf("captureRunningLayout()"),
            "the close captures BEFORE it rebuilds, so it folds a layout that predates the edit over the configuration and removes what that layout does not carry - which is how a declined edit was silently deleted (ACC-B3)");
    }

    /**
     * Moving an arrow redraws the arrows, not the whole diagram (OB-185).
     *
     * Adam: **"changing any pathing arrow in the track autonomy editor makes the whole screen
     * flicker."** There was one redraw door and it rebuilt the grid - every label destroyed and made
     * again - which flashes even when the tile art is identical. A direction restriction changes no
     * tile art at all: what moves is the annotation drawn over the tile.
     *
     * So the panel has a light door beside the heavy one. The running layout is still rebuilt, because
     * a restriction is a real change to the graph and a diagram agreeing with a railway that has moved
     * on is the failure the heavy door exists to prevent.
     *
     * MUTATION: pointing the one-way branch back at `setupChanged()` fails this, and so does an
     * `annotationsChanged` that reaches for `onDiagramChanged`.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testAnArrowChangeDoesNotRebuildTheDiagram() throws Exception
    {
        // Gone, `SourceOf.declaration` fails: the light redraw door has gone, so every arrow change rebuilds the
        // whole diagram again (OB-185)
        String light = codeOf("private void annotationsChanged()");

        assertTrue(light.contains("onAnnotationsChanged.run()"),
            "the light door does not refresh the annotations, so moving an arrow draws nothing");

        assertTrue(light.contains("rebuildRunningLayoutSoon()"),
            "the light door does not rebuild the running layout. A one-way restriction is a real change"
            + " to the graph, and a diagram agreeing with a railway that has moved on is the defect the"
            + " heavy door exists to prevent - the point was to stop the FLICKER, not the rebuild");

        // AND THE ONE-WAY TOOL USES IT.  The door existing and nothing calling it is the shape a
        // refactor leaves behind.
        assertTrue(SourceOf.allCode().contains("if (changed >= 0) annotationsChanged();"),
            "the one-way tool does not use the light door, so the flicker Adam reported is still there"
            + " on the one tool that is used many times in a row (OB-185)");
    }

    /**
     * A hover's border pass asks the grid setting once, before its loop over the squares (speed, 2026-10-04).
     *
     * The setting is a preference, and on Windows every read of one is a registry round trip that nothing caches - 22
     * microseconds each, measured.  The pass runs on every square the pointer enters, twice with a selection, and asked
     * the setting twice per square: 16 ms a hover over the main page's 431 squares, nearly all of it the registry.
     *
     * Pinned as source: the reads cannot be counted from a test, because the preferences are a static final the JIT may
     * fold, and a store swapped in by reflection is not seen.
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
     * A redraw of the editor's diagram asks the grid setting once, not once per square (speed, 2026-10-04).
     *
     * Each square read the setting - a Windows registry read, 22 microseconds, nothing caching it - as it was made, so
     * every redraw of the editor's diagram (every edit, every Undo) made one read per square.  `LayoutGrid` reads it once a
     * build and hands it to each square.
     *
     * Pinned as source, as the hover's pass is (`testAHoverAsksTheGridSettingOncePerPass`).
     *
     * MUTATION: let the grid make its squares without the setting again, and this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testARedrawAsksTheGridSettingOnce() throws Exception
    {
        // The grid's build, wherever it is (FR-136)
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
     * The main window's marks ask the one-way arrows option once a pass, not once per square (speed, 2026-10-04).
     *
     * Each square's annotation read the option - a read of the Windows settings - for itself, and a pass describes every
     * square of the railway; a click in the autonomy editor makes several passes.  Measured at a fifth of the click, on a
     * fast processor and a slow one alike.  The pass reads it, and the green arrows' option (FR-110), before it asks the
     * session for the marks, and hands both over.
     *
     * Pinned as source, as the grid setting is (`testARedrawAsksTheGridSettingOnce`): the preferences are a static final
     * the JIT may fold, so the reads cannot be counted from a test.
     *
     * MUTATION: let the pass ask for the marks without either option, or the session or a square ask one again, and this
     * fails.
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
     * Only the session, and its import for one flag, write the session's state (FR-136).
     *
     * `AutonomySession`'s `store`, `graph`, `reducer` and `dirty`, and `CARRIED_SETTINGS`, are package-private so the
     * classes extracted from it can reach them, which leaves them open to the whole package.  `reducer` is handed out
     * only once reduced (OB-298) and `rebuild` alone publishes it; `graph` and `store` are the session's to replace;
     * `CARRIED_SETTINGS` is one list every import shares.  So no code outside the class assigns the first three through
     * another object, the import's `owner.dirty = true` is the only assignment of `dirty` from outside, and nothing
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
     * The point is that the four options **exclude one another**, which is what finishes OB-174.
     * Before this there was a master switch and two tick boxes that were not independent of it:
     * ticking either while the text was off changed nothing anybody could see, so each box turned the
     * master on for you. That coupling had to be remembered at every door that touched either box, and
     * this codebase's way of forgetting such a thing is to fix one of a pair.
     *
     * Checked on the CONTROL rather than by opening a window: the four states, the default, and the
     * fact that the two internal flags can never both be set. Where the label sits is asserted by
     * source order below, because "right above Track Directions" is a fact about the sidebar that no
     * headless assertion can see.
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

        // AND AT OPEN AS WELL (RGD-C3).  The box is remembered between opens and the text switch is
        // not, so a version that skipped it when the window opened restored an unticked box over text
        // still drawn.
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

        // AND THE OLD COUPLING IS GONE.  Leaving it would be harmless today and would be the thing a
        // future reader copies.
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
     * The tail can be set, and not un-set (Adam, 2026-09-07).
     *
     * Annotating the behaviour reference beside the paragraph on `arrivedFrom`: **"Given the other
     * items above, when would we not know?  Clearing should not be possible, only setting."**
     *
     * The menu carried a "Not known" option, defended in a comment on the grounds that a mistaken
     * answer would otherwise be permanent. It would not: the same menu lists every side the geometry
     * offers, so correcting a wrong answer means picking the right one. What clearing actually offered
     * was a way to turn the blocking off, which is not a fact about the railway.
     *
     * And the premise is now false. Autonomy writes the side on arrival; a hand placement works it out,
     * asking where the answer is genuinely open. Every train that reaches a square has an answer, so
     * "not known" describes only state saved before the feature existed - a transitional case, not a
     * choice worth offering forever.
     *
     * The automatic clear stays: `Point.setLocomotive` drops the side when the occupant changes,
     * because the new train did not arrive the way the old one did. That is the railway retracting a
     * fact it no longer holds, not a person declaring ignorance.
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

        // AND IT SHOWS WHAT IS RECORDED, even when the geometry does not offer it (RGD-C4).
        //
        // The sibling menu thirty lines below learned this as OB-177 and this half was not swept. The
        // radios are a ButtonGroup ticked by equality, so a recorded side missing from the list ticks
        // NOTHING: the section reads as "no tail recorded" while the side it does not show goes on
        // steering the tail walk. A track edit that re-plumbs a square leaves exactly that state.
        assertTrue(menu.contains("recorded != null && !sides.contains(recorded) ? recorded : null"),
            "the arrived-from menu drops a recorded side the geometry does not offer, so it opens with"
            + " every choice blank for a square that HAS a tail recorded - a value still steering the"
            + " tail walk, unseen (RGD-C4)");

        // SHOWN, NOT OFFERED.  The first attempt copied the facing menu's answer - add it to the list
        // - and Adam met it as a regression: "75 407 DB's menu now lets you select arrival from the
        // west at BottomMainPost, which makes no sense". The tail walk matches stored sides against the
        // geometry, so a side the geometry does not have can never match: offering it is offering a
        // choice guaranteed to do nothing.
        assertFalse(menu.contains("sides.add(recorded)"),
            "a recorded side the track does not have is offered as a choice again. It can never match"
            + " the tail walk, so setting it blocks nothing - it is a statement about the square, and"
            + " belongs in the menu disabled (RGD-C4)");

        assertTrue(menu.contains("gone.setEnabled(false)"),
            "the recorded-but-absent side is not disabled, so it reads as an answer the operator may"
            + " choose rather than as a fault to correct (RGD-C4)");
    }

    /**
     * One reader of a placement, because the two disagreed about the shape (RGD-C5).
     *
     * A placement is normally an object - `{"name": ..., "speed": ...}` - because `parseAuto` resets
     * whatever a placement omits, so a train's length and functions travel with its name. A hand-edited
     * or older `autonomy.json` can hold a BARE STRING, and `getLocomotiveNameAt` has always handled it:
     * *"drawing nothing on an occupied platform is the one wrong answer a label must not give."*
     *
     * `nameOfPlacedLocomotive` was written later for the occupant-change guards and answered null for
     * that shape. Null is "nobody here", so re-placing a train on the square it already occupies - which
     * every door does when it writes a placement back unchanged - read as a CHANGE of occupant and threw
     * away an arrival side that was still true. That is the exact case the guard exists to prevent, and
     * the cost is tail blocking quietly off for that square until the next arrival.
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
     * Dragging a locomotive puts its name on the clipboard, and cannot fail because of it (C26).
     *
     * A review filed the clipboard write as an unwanted side effect of starting a drag. Adam,
     * 2026-09-07: **"c26 is deliberate, that is the dragging functionality you implemented on the
     * keyboard."** So it is pinned here rather than removed - the next reviewer will see the same
     * unexplained write and reach the same wrong conclusion unless something says otherwise.
     *
     * The guard round it is not decoration. Windows hands the system clipboard to one process at a
     * time and `setContents` throws `IllegalStateException` while another application holds it, which
     * is ordinary and transient. Uncaught, that came out of the mouse handler **after** a cut had
     * emptied the source key and **before** the paste could put the locomotive down: a moment of bad
     * luck deleted a mapping. Four of the five tests in `ui.testTheKeyboardDrag` failed on it the
     * first time they ran, and not one of them is about the clipboard.
     *
     * The behaviour of the gesture itself is in that class. This asserts only the two things that are
     * textual: that the write is still there, and that it still cannot take the drag down with it.
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

        // THE CATCH IS AFTER THE WRITE, and names what it tolerates (IND9-C5).  "A try appears
        // somewhere above" is satisfied by any unrelated try earlier in the method, so the guard could
        // go permanently vacuous on an edit that has nothing to do with the clipboard.
        int caught = drag.indexOf("catch (IllegalStateException", wrote);

        assertTrue(caught > wrote,
            "the clipboard write is not wrapped in a catch for the clipboard being unavailable. It"
            + " throws whenever another application is holding it, and it runs AFTER a cut has emptied"
            + " the source key - so a transient lock on somebody else's clipboard deletes a mapping"
            + " (C26, IND9-C5)");
    }
    /**
     * The Sync menu comes back even when the sync does not (C23).
     *
     * doSync greys Sync and the functions menu, hands off to a worker thread, and re-enables them when
     * the worker finishes. There is no finally: a RuntimeException out of syncWithCS2 kills the worker,
     * and **both menus stay greyed for the rest of the session** with nothing on screen saying why. The
     * only route back is to restart the application.
     *
     * The off-EDT half of this finding was already fixed - both setEnabled calls moved inside
     * singlePass, commented - and the finally half was left. Half a finding fixed is how a row gets
     * marked done while the part that strands the user survives.
     *
     * Source-level because the fault is the absence of a catch around a call that only throws when the
     * Central Station misbehaves, which a test cannot arrange.
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

        // WHAT THE CATCH DOES, not that a catch exists (IND9-C5).  "There is a catch between the call
        // and the re-enable" is satisfied by `catch (RuntimeException e) { log(e); return; }`, which
        // is C23 fully restored - menus greyed for the session - with this test green.  The failure
        // code is the observable thing: it is what carries the operator to the log.
        assertTrue(sync.substring(call, back).contains("syncResult = -1"),
            "nothing between the sync and the re-enable turns a failure into the code this method"
            + " knows how to report. A catch that logs and returns leaves Sync and the functions menu"
            + " greyed until the application is restarted, which is C23 unfixed (IND9-C5)");
    }
    /**
     * Nothing that only draws an accessory may create one (C13).
     *
     * `getAccessoryState` registers a switch when the address is unknown. That is right on a path that
     * is about to command the thing and wrong everywhere else - and the window’s two keyboard paints
     * called it, the second in a loop over all sixty-four keys. **Opening a keyboard page registered
     * sixty-four switches**, whether or not anything was wired to them, and they persisted.
     *
     * Source-level because the fault is which of two near-identical methods a paint routine calls, and
     * because the phantom appears in a database this test cannot safely write to.
     *
     * Asserted as an ABSENCE across the whole window rather than at the two known sites: a third paint
     * path added later would be the same defect, and a check that lists the two it knows about would
     * report clean about it. That failure mode is why the sweep this came from existed at all.
     *
     * MUTATION: restoring either call to getAccessoryState fails this.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testDrawingAnAccessoryDoesNotCreateOne() throws Exception
    {
        // ASKED OF THE WHOLE PROGRAM (FR-136), by what each call is made on, since the registering form has
        // callers that are not paints: the window's paints called it on `model`, a route condition on `control`.
        // What may remain are the declarations, a locomotive waiting for an accessory it has commanded, the
        // locomotive's delegation to its station, and the API example.
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
     * A dismissed arrival-side question leaves the railway exactly as it was (Adam, 2026-09-07).
     *
     * **"Simply don't place the train, leave it on the clipboard as if no paste had been done."**
     *
     * The question was asked from `rememberPlacement`, which runs AFTER the drop: by the time the
     * operator saw it the train had already been lifted off its old square and put down on the new
     * one, the clipboard had been emptied, and there was nothing left for a dismissal to decline. So
     * closing the dialog placed the train anyway and merely declined to record which way round it was
     * - the worst of the three possible outcomes, because the railway had moved and the record of it
     * had not.
     *
     * Which makes this an ORDERING rule, not a value one: the answer has to be in hand before
     * `moveLocomotive` runs, or no answer can undo anything. Source-level for that reason - the fault
     * is the order of two statements inside a mouse handler, and a test that drives the model sees
     * both of them already done.
     *
     * The null is also read in two different ways and the guard has to tell them apart. On a square
     * trains may turn at the question is asked, so null means dismissed; everywhere else the answer is
     * worked out rather than asked and null means only "there was nothing to record". Treating the
     * second as a dismissal would refuse every paste onto plain track.
     *
     * MUTATION: moving the forPlacement call back below moveLocomotive fails the ordering assertion;
     * dropping the mayTurnHere half of the guard fails the last one.
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

        // AND IT ASKS THE PROMPT'S OWN QUESTION (IND9-B5).  `mayTurnHere` alone was not enough:
        // forPlacement answers null in three situations and only one of them is a dismissal, so a
        // square with no compass-resolvable side was refused every paste, silently and permanently.
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
        // The route's run, wherever it is (FR-136); renamed, `SourceOf.declaration` fails
        String route = codeOf("private void execRoute(boolean auto, int recursionLimit, boolean overrideConflicts)");

        int asks = route.indexOf("confirmRouteConflictMidway(");

        assertTrue(asks > 0, "nothing in MarklinRoute asks the mid-route question any more");

        // FOLLOW THE GUARD, NOT THE DISTANCE (DIR-C7 sweep, 2026-09-06).
        //
        // This read the 220 characters before the call and looked for `!auto` in them.  That held
        // while the gate was written inline; `DIR-A3` gave it a name - `askable` - and the gate moved
        // out of the window while remaining exactly as strong.  A proximity check reports on where the
        // code is written rather than on what it says.
        //
        // The guard is now followed by name: whatever `askable` is defined as has to contain `!auto`,
        // and the call has to be behind it.
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
        // The track diagram editor's, since the route editor has one by the same name (FR-136)
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
        // THE EXIT'S FIRST QUESTION MOVED INTO A METHOD OF ITS OWN (VB2-B1, SVT-B1): `WindowClosed` ends in
        // System.exit and could not be tested, so what it does before it saves anything is
        // `everyOpenWindowMaySettle`.  The ORDER is still asked of `WindowClosed` - the settle question has
        // to come before the discard is completed - and the question itself is asked of that method, so
        // neither can go missing by moving into the other.
        String exit = codeOf("private void WindowClosed(")
            .replace("everyOpenWindowMaySettle()", "maySettleBeforeExit()");

        String asksBoth = codeOf("public boolean everyOpenWindowMaySettle()");

        // BOTH WINDOWS, NAMED (VC2-B2).  "contains maySettleBeforeExit" was satisfied by the route editor's call
        // alone, so deleting the layout editor's left every test green and File > Exit discarded a page without
        // asking - which is OB-070's original defect, on the window it was raised for.
        assertTrue(asksBoth.contains("openEditor") && asksBoth.contains("maySettleBeforeExit()"),
            "the exit no longer asks the open track diagram editor whether it may settle, so closing the"
            + " application throws away an edited page without asking (OB-070)");

        assertTrue(asksBoth.contains("routeEditor") && asksBoth.contains("maySettleBeforeExit()"),
            "the exit no longer asks the open route editor whether it may settle, so closing the application"
            + " throws away unsaved typing without asking (UIX-B1)");

        // ASKED ONCE (VB2-B1, VC2-B1).  The layout editor's own call stood in `WindowClosed` as well when the
        // route editor's was added, so a dirty diagram editor was asked twice and Discard-then-Save wrote the
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

        // Proved present before they are compared - indexOf answers -1 for an absent term, which is
        // less than every real index, so deleting the settle call outright (a worse fault than
        // reordering it - the exit stops asking about unsaved work at all) would otherwise still leave
        // a present completeExitDiscard() reading as "after" and this passing on the strength of a
        // call that is no longer there (TST-B7).
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
     * **WHERE ESCAPE IS BOUND MOVED, and this test moved with it (FR-065).**  It used to pin
     * `installEscape` - a `WHEN_IN_FOCUSED_WINDOW` binding on this panel - including an assertion that
     * it was WHEN_IN_FOCUSED_WINDOW, on the reasoning that the click which armed the tool leaves focus
     * on the diagram.  The 2026-09-09 review's B5 measured what that actually meant: bindings of that
     * kind are never consulted in this window at all, because `LayoutEditor` makes every control
     * `setFocusable(false)` so the FRAME keeps the keyboard, and a top-level frame has no parent chain
     * for the focus manager to walk.  Escape lives in `LayoutEditor.escapePressed` now, reached from
     * the frame's own `KeyListener`, and `testEscapeClosesTheEditor` presses the key and looks at the
     * window.
     *
     * So what is left here is the half that is still a property of THIS file: that putting the tools
     * down puts all of them down, and brings their buttons up.  Read rather than run, because the
     * panel needs a session, a page and a live diagram to stand up.
     *
     * MUTATION: dropping the `setSelected(false)` loop from `putToolsDown`, or its early return, each
     * fails one of these.
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
        // apart at the next tool, and a gesture missing from one list survives being cancelled.
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
        // The grid's class, wherever it is (FR-136)
        String grid = SourceOf.withoutComments(SourceOf.declaration("public class LayoutGrid\n"));

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

        // Asked of the whole program (FR-136): the one call allowed is onPill's own, which asks it about the fill
        assertEquals(outside("readableOn(", "public static Color readableOn(Color fill)",
                "public static Color onPill(Color fill, Color wanted)"), 0,
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
        String prompt = codeOf("    void promptLocomotives(");

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
     * Adam, 2026-08-27: "make sure hovering the label triggers the cell hover effect (blue outline)."
     *
     * A caption is a component stacked on top of its square, so moving the pointer onto the name sends
     * the square a mouseExited and the outline goes out - on the larger and more obvious of the two
     * things to aim at.
     *
     * Two things are asserted and the second matters as much as the first. The label forwards to
     * `receiveMoveEvent`, which is the method the tiles themselves call, so the outline is the same
     * one rather than a second drawn to look like it - two appearances would drift. And it forwards
     * ONLY from the caption: on the square this is already happening, and forwarding again would draw
     * the same outline twice for one movement.
     *
     * MUTATION: dropping the `handle != square` test makes the square forward for itself and fails
     * the second assertion; drawing a border here instead of forwarding fails the first.
     */
    @Test
    public void testHoveringANameOutlinesItsSquare() throws Exception
    {
        // The caption's drag, which carries its pointer handlers, wherever it is (FR-136)
        String drag = SourceOf.declaration("private static void dragCaption(");

        assertTrue(SourceOf.withoutComments(drag).contains("editor.receiveMoveEvent(e, square)"),
            "hovering a station label no longer tells the editor where the pointer is, so the blue "
            + "outline goes out as soon as the pointer moves onto the name - which is the bigger of "
            + "the two things to point at");

        // EACH handler, asked separately.
        //
        // Counting them, and looking for the guard anywhere, was the first version and a mutation
        // walked through it: taking the guard off mouseEntered alone left it on mouseMoved, so the
        // name was still in the file and the count was still right. That is the third assertion this
        // week satisfied by one of two sites, so each one is now asked about itself.
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
     * Adam, 2026-08-27: "right-clicks on the label don't propagate to the tile underneath it."
     *
     * A caption covers the middle of its square and takes every click landing there, so the autonomy
     * menu - which is how everything about a square is set - did not open on the part of the diagram
     * people aim at.
     *
     * Handed to `receiveClickEvent`, the method the tile's own listener calls, so it is the same menu
     * rather than a second one built to match. And CONVERTED first: a popup opens at the coordinates
     * it is given, and the caption's are not the square's - the menu would appear offset by however
     * far the label sits from its tile.
     *
     * MUTATION: dropping the conversion leaves the menu opening in the wrong place, which this cannot
     * see; dropping the forward entirely, or letting the square forward for itself, fails below.
     */
    @Test
    public void testARightClickOnANameReachesItsSquare() throws Exception
    {
        // The caption's drag, wherever it is (FR-136); without a click handler, `SourceOf.declarationIn` fails
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
     * Adam, 2026-08-27: "on hover of a tile in the autonomy editor, show the station name as the
     * tooltip."
     *
     * This editor has had no tooltip at all, and the code says why: returning early once "took the
     * whole gesture away, tooltip and outline together", and OB-091 put back only the outline.
     *
     * The name is asked of the SESSION and not of any caption drawn nearby, which is the assertion
     * worth having. A caption is a label somebody chose to place on some square; the square under the
     * pointer usually has none, and that is exactly the case this exists for.
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

        // Scoped to the EDITOR's branch by position.
        //
        // `contains` alone passed with the line deleted, because the running diagram sets the same
        // tooltip in its own branch and the name was still somewhere in the file. Fourth time this
        // week that a check has been satisfied by the site it was not about.
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
     * Adam, 2026-08-27: "make the +/- keys scroll through pages in the layout/autonomy editor.  just
     * have them call existing components to reuse the same guards/warnings."
     *
     * Two things can be wrong here without anything failing to compile, and both have happened before
     * in this exact handler.
     *
     * WHERE THE KEYS SIT. The handler returns early in autonomy mode, guarding shortcuts that "place,
     * cut, rotate or retexture a tile". A page key below that line does nothing in the autonomy
     * editor - which is half of what was asked for - and MT-109 is the ticket about keys filed as
     * fixed while sitting exactly there.
     *
     * WHAT THEY CALL. `leaveFor` carries the unsaved-work question and the latch that stops a second
     * switch starting inside the first. A page key that moved the diagram itself would skip all of it.
     *
     * Read rather than run: driving a key into this window needs the window, a railway of several
     * pages, and the focus owner to be the frame - which is the very thing that makes root-pane
     * bindings dead here.
     *
     * MUTATION: moving the block below the autonomy guard fails the position assertion; having
     * stepPage change the page itself instead of calling leaveFor fails the last one.
     */
    @Test
    public void testThePageKeysUseTheSwitchThatAlreadyExists() throws Exception
    {
        // The editor's key handler, comments and all, since the guard is found by its own sentence; LayoutEditor's,
        // since other windows have a formKeyPressed too (FR-136)
        String editor = memberOf("public class LayoutEditor extends PositionAwareJFrame",
            "private void formKeyPressed(java.awt.event.KeyEvent evt)");

        assertTrue(editor.contains("stepPage(1)") && editor.contains("stepPage(-1)"),
            "the page keys are gone, so + and - no longer move between pages");

        // ABOVE the guard, identified by the guard's OWN sentence rather than by a method name that
        // appears a dozen times in this file - which is how the first version of this check fooled
        // itself into passing.
        int guard = editor.indexOf(
            "// Every shortcut below places, cuts, rotates or retextures a tile.");

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

        // THE LATCH THAT SPANS THE SWITCH, not merely the word.
        //
        // This asserted `contains("switching")` and passed, while the property it claimed was false:
        // `switching` is assigned only inside syncSidebar, synchronously, so it is never true when a
        // second switch could start. A validator found it. The token was present and the sentence
        // above it was wrong - which is the most expensive kind of green there is, because it reads
        // as coverage.
        //
        // `changingPage` is the one that answers the question: set when a switch is committed, cleared
        // in arriveAt where it lands, so it is true across the whole gap leaveFor posts its work into.
        assertTrue(step.contains("changingPage"),
            "stepPage does not check whether a switch is already IN FLIGHT. leaveFor posts its work "
            + "and returns, so the page has not changed yet - and holding the key queues one full "
            + "teardown per auto-repeat, every one of them to the same destination");

        String leave = codeOf("private void leaveFor(String page, boolean autonomy)");

        assertTrue(leave.contains("changingPage = true"),
            "nothing raises the latch when a switch is committed, so it can never be true and every "
            + "door that asks it is asking a constant");

        // BOTH PRESENT, then ordered.
        //
        // `indexOf` answers -1 for something absent, and -1 is less than every real index - so
        // deleting the unsaved-work question outright made this pass, which is the very mutation the
        // message below names (reviewer, 2026-08-28).
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
     * The excluded-page label and the banner's Fix Setup bring an open editor forward, as the Edit item does (GUI-C8).
     *
     * Adam, 2026-09-19, on the label: *"just make it attempt to click the edit button if it's enabled"*.  With an
     * editor already open the Edit item IS enabled, and `openLayoutEditor` brings that window forward (OB-058).  The
     * label and Fix Setup asked `refuseWhileEditorOpen` first, which in exactly that state shows "Close the editor
     * first" - telling the operator to close the window the Edit item would have handed them.  Loading is still
     * refused while an editor is open, which is what that guard is for.
     *
     * Read from the source: an open editor in a test would need the whole window, and the dialog this is about is
     * modal.
     *
     * MUTATION: put `refuseWhileEditorOpen()` back in either and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheWaysIntoTheEditorBringAnOpenOneForward() throws Exception
    {
        String label = codeOf("public void openAutonomyEditorIfItCan()");

        // The banner, wherever it is (FR-136)
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
     * The Facing menu ticks nothing where nothing says which way the train faces (GUI3-C4) - except on a square that
     * holds one facing, where every copy faces that way and the facing IS known (DCN4-C3).
     *
     * It ticked the first facing, as "the one a placement with no recorded facing actually gets" - which stopped being
     * so when the build began standing such a train on a copy it can start from (GUI2-B1): at BottomMainA that faces the
     * other way, so the menu's tick contradicted the train.  With nothing recorded and nothing on the railway to ask,
     * nothing is known, and a tick would say otherwise.
     *
     * MUTATION: tick the first facing again and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheFacingMenuTicksOnlyWhatIsKnown() throws Exception
    {
        // Asked of the whole program (FR-136)
        assertFalse(SourceOf.allCode().contains("facing == facings.get(0)"), "the Facing menu ticks the first facing when nothing"
            + " says which way the train faces - the build no longer stands it that way (GUI3-C4)");
    }

    /**
     * Why not Moving? asks the start question of the tier it is showing (GUI4-D1).
     *
     * `Layout.explainCannotStart(loc, byHand)` is claimed on its own, and the editor's one call to it is what makes it
     * reach the operator: reverted to the one-argument form, a train on Manual is told autonomy's reasons again - the
     * shape OB-225 was - and every claim on the rule stays green.
     *
     * MUTATION: call the one-argument form from `composeWhy` and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testWhyNotMovingAsksTheTierItShows() throws Exception
    {
        // Asked of the whole program (FR-136)
        String panel = SourceOf.allCode();

        assertTrue(panel.contains("explainCannotStart(standing, byHand)"), "Why not Moving? no longer asks the start"
            + " question of the tier it shows, so on Manual it gives autonomy's reasons (OB-225, GUI3-C1)");
        assertFalse(panel.contains("explainCannotStart(standing)"), "Why not Moving? asks autonomy's start question"
            + " whatever the tier (OB-225, GUI3-C1)");
    }

    /**
     * "Is an editor open" is asked of the EDITOR, not of the button that opens one.
     *
     * This was `!editLayoutButton.isEnabled()`, which was a true answer for as long as the button
     * being grey had exactly one cause. OB-126 gave it a second - there is no local layout to edit -
     * and six callers went on reading it as "an editor is open". Switching to a Central Station layout
     * therefore greyed every item in the Layout menu, including the ones that switch back, and did the
     * same to the Autonomy menu. Both escape hatches were inside the dead menus: restart required,
     * from a change meant to stop the application offering an editor it could not open.
     *
     * A reviewer found it within the hour. Nothing tested the predicate, which is how a proxy for a
     * fact gets a second job and nobody notices.
     *
     * MUTATION: deriving it from the button again fails this.
     */
    @Test
    public void testWhetherAnEditorIsOpenIsAskedOfTheEditor() throws Exception
    {
        String open = codeOf("public boolean isLayoutEditorOpen()");

        // AND INTO anEditorWindow, which asks the editor windows (RSA28-B1)
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
        // The program's code (FR-136), so a caption decided in another file is counted and asked too
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

        // AND WHERE `visible` COMES FROM, which is the hole the disjunct above leaves.
        //
        // One of the three deciders - refreshCaptionVisibility, the path the toggle actually uses -
        // passes a local called `visible` rather than naming the rule on the same line. That is
        // perfectly good code, and it is why the disjunct exists; but it meant the line assigning that
        // local could be changed to a constant with nothing failing, restoring FR-023's reported
        // symptom on the one path a user reaches. Found by a reviewer; the first repair was to demand
        // the rule on the setVisible line itself, which failed against correct code.
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

        // A loop over nothing asserts nothing.
        //
        // Renaming that local, or writing `final boolean visible =`, made the block above a no-op that
        // passed (reviewer, 2026-08-28).
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

        // And the blanket setter in particular, by name, because it is the one that got it wrong.
        // Gone, `SourceOf.declaration` fails - if renamed, rename it here
        String body = codeOf("public void showStaticAutonomyLayer(boolean show)");

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
        // The whole method, wherever it is, with its comments blanked so a rule is proved by the code and not
        // by the prose about it - this method's comments name every one of the identifiers below, several
        // times each.  Gone, `SourceOf.declaration` fails: it is the only door onto blockedPoints, so if it
        // was renamed, rename it here; if it was removed, the restriction can no longer be edited at all and
        // this test should say so rather than be deleted
        String body = codeOf("    void promptBlockingPoints(");

        // What is stored has to be read before the offer is built, because it is what decides the
        // offer.  Read after, it can only be used to tick boxes that are already on the list.
        //
        // The left term is proved present first (TST-C7): indexOf returns -1 for an absent needle, and
        // -1 is less than every real index, so a rename or reparameterisation of getBlockingPoints(station)
        // would otherwise make the ordering assertion below pass vacuously instead of failing.
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

    /**
     * EVERY branch that raises the latch, not just the one that was looked at.
     *
     * `leaveFor` returns from two places and both raise `changingPage`. The repair that moved the raise
     * down to sit immediately before the posted work was applied to both, and its comment was pasted
     * onto both - but only the track branch actually got the guarantee the comment describes, from
     * `layoutEditingCompleteThen`. The autonomy branch called `autonomyEditorClosed()` bare, with the
     * post that lowers the latch after it in the same lambda, so a throw in a method that parses the
     * configuration and rebuilds the graph left the latch up for the life of the window (validator,
     * 2026-08-28).
     *
     * MUTATION: unwrapping either branch's guarantee fails this. So does adding a third raise without
     * one.
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

        // The helper the track branch leans on really does make that promise - in three places
        // (SVN-A3).
        //
        // It used to be one `finally`, here, and it covered the wrong three statements.
        // `layoutEditingComplete` lowers the button, reads a field and starts a thread; the dozen
        // statements the guarantee is about are in `layoutRefreshComplete`, on the far side of that
        // thread. So the continuation ran AT ONCE - the editor's `arriveAt` was queued on the EDT
        // before the refresh was queued at all - and the refresh, arriving last, re-enabled the Edit
        // Layout button with the editor still open.
        //
        // Three links now, and the chain is only as good as the weakest: the worker posts the EDT half
        // whatever the refresh does, the EDT half runs the continuation from a finally, and the helper
        // still covers a throw that happens before there is a worker at all.
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
     * The grey Edit Layout button explains WHICH of its reasons applies (OB-126).
     *
     * OB-126 gave the button two more causes - no layout loaded, and a Central Station layout - and
     * both doors that ask whether it is grey went on showing the message for the first one. So the
     * answer to "why can I not edit?" was "close the editor you already have open", given to somebody
     * with no editor and no layout (validator, 2026-08-28).
     *
     * That is the same fault OB-126 itself was: a predicate that gained a second meaning and kept its
     * first answer. This checks the pairing rather than the sentence, because the sentence is only
     * ever wrong when the pairing is.
     *
     * MUTATION: hard-coding either dialog back to a single key fails this. So does dropping a branch
     * from the explanation while leaving it in the rule.
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

        // THE FLAG, not the inference it replaced (Adam, 2026-08-28: "we just need a layout loaded
        // flag in the TrainControlUI class, rather than something that infers").
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
     * THE TRAP is the second assertion. `guardLayoutMenu` sets every child of this menu enabled or
     * disabled from one flag, and a heading is disabled because it is a LABEL - not because it is
     * unavailable. Without an exemption it comes back enabled the moment no editor is open: bold,
     * live, and doing nothing when clicked.
     *
     * MUTATION: removing the exemption fails this. So does putting the export back at the foot of the
     * menu, where it sat below the Central Station group it has nothing to do with.
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

    /**
     * Manage Configurations sits directly under the configuration it manages.
     *
     * It was the last item on the autonomy menu, two groups below the thing it acts on.
     */
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
     * Adam, 2026-08-29: "Move pop up all... and save as picture to below the central station layout
     * section." They were between the two source sections, which put them under the Local Layout
     * heading - and neither is about where the diagram came from. The UX review had the same
     * complaint from the other side (UXR-C11).
     *
     * The existing menu test checks the export is placed relative to the POP-OUT, which stays true
     * wherever the pair ends up, so nothing pinned where the pair itself sits.
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
        // Gone, `SourceOf.declaration` fails: nothing collapses runs of separators, so taking the pop-out out
        // from between two of them leaves two rules in a row where it used to be
        String tidy = codeOf("private void tidyLayoutMenuSeparators()");

        assertFalse(tidy.contains("jSeparator"),
            "the separator tidy names a generated field. The NetBeans designer renumbers those "
            + "whenever the form is touched, so a rule that names one is a designer save away from "
            + "being wrong - which is how cropOverlay disappeared twice on 2026-08-28");
    }

    /**
     * No dialog is raised from a background thread (OB-137, widened by VB-B1).
     *
     * Adam: "while importing routes from Json, the route table freezes up / looks weird." The whole
     * of `importRoutesMenuItemActionPerformed` ran inside `new Thread(...)`, and the first thing it
     * did there was show a MODAL JFileChooser - a Swing dialog from a thread that is not the event
     * thread, so the chooser and the window behind it were laid out and painted from two threads at
     * once.
     *
     * THE FIRST VERSION OF THIS TEST READ ONE FILE AND ONE NEEDLE. It said it asserted over "every
     * handler that opens a chooser", and it meant every handler in TrainControlUI.java that calls
     * showOpenDialog - so a save dialog one class over, a modal confirm, a modal input, and a Swing
     * panel constructed off the thread and then shown modally were all invisible to it. It passed
     * for the whole time six other methods were wrong in exactly the way it was written to catch.
     *
     * Every source file now, and every dialog form that blocks. The floors matter as much as the
     * assertion: a scan that quietly stops finding calls has turned into a check that no dialogs
     * exist anywhere, which is not the same thing and always passes.
     *
     * invokeAndWait counts as marshalling alongside invokeLater. LocomotiveStats#exportData uses the
     * former and was a false positive while this only knew the latter - the guard is only as good as
     * the ways it knows a call can be made correctly, so a miss there is this test's bug and not the
     * code's. examples/ is skipped: sample code with a main, not the application.
     *
     * MUTATION: wrapping any of these calls back inside a `new Thread(` in its own method fails this.
     */
    @Test
    public void testNoDialogIsShownOffTheEventThread() throws Exception
    {
        // Everything that BLOCKS. A message dialog is on the list deliberately: three of the six
        // methods VB-B1 found used showMessageDialog to display a whole AutoJSONExport panel, built
        // on the background thread, and "message" made them read like harmless toasts.
        String[] needles =
        {
            "showOpenDialog(", "showSaveDialog(", "showInputDialog(",
            "showMessageDialog(", "showConfirmDialog(", "showOptionDialog("
        };

        // Where a member begins. Not a Java parser, and it does not need to be: the shape being
        // caught is `new Thread(() -> { ... showSomethingDialog( ... })` inside one member.
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

                    // And a package-private method, with no modifier to find it by, as the window's collaborators
                    // declare theirs (FR-136): otherwise its dialogs are read as the member above's.
                    for (int start : bareStarts)
                    {
                        if (start < at) from = Math.max(from, start);
                    }

                    if (from < 0) from = 0;

                    String before = code.substring(from, at);

                    int thread = Math.max(before.lastIndexOf("new Thread("),
                        before.lastIndexOf("LayoutGridRenderer.submit("));

                    if (thread < 0) continue;

                    // Marshalled back after the thread opened is the correct shape, and both forms
                    // of it count.
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

    /**
     * Every .java file under a directory, recursively.
     */
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
     * Adam, 2026-09-03, on his own railway: *"Setting a home locomotive via track diagram viewer does
     * not activate the send home button, and it is not persisted when closing the app."* Both halves
     * are one omission, and it was in **seven of the nine** doors that write a point property.
     *
     * `AutonomyEditorPanel` supplies the tile menus for two surfaces. In the layout editor there is a
     * Save and a close, and closing rebuilds. On the **track diagram** there is neither:
     * `TrainControlUI` wires `onDiagramChanged` to `session.save()`, so that runnable is the only
     * thing that makes an edit outlive the process, and `rebuildRunningLayoutFromSetup` is the only
     * thing that tells the running `Layout` about it. Every affordance that asks a question about
     * homes, priorities, lengths or usage reads the running layout - which is why a home set from the
     * diagram left "Return Locomotives Home" greyed: `triageReturnToHome` asked a layout nobody had
     * told.
     *
     * **The two doors that already worked are the two that had been reported before**
     * (`placementChanged`, `promptName`), each fixed on its own at the time. That is why this is a
     * rule and not a ninth patch.
     *
     * Asked of the source rather than by driving the panel: these methods are private, they open modal
     * dialogs, and what is being checked is that a line is present - which is exactly what a surface
     * rule is for.
     *
     * MUTATION: removing `setupChanged();` from any one of the nine fails this.
     */

    @Test
    public void testEveryDoorThatWritesTheSetupAnnouncesItWiderSweep() throws Exception
    {
        // THE PANEL'S CLASS, wherever it is (FR-136): this is the rule for the doors of the panel that supplies
        // the tile menus to both surfaces, and other classes announce their writes in their own ways.
        String source = SourceOf.declaration("public class AutonomyEditorPanel extends JPanel");

        java.util.List<String> examined = new java.util.ArrayList<>();

        // ASKED OF THE SOURCE AS TEXT (GSE-C1), so that a fixture can be asked the same question - see silentWriters.
        //
        // OF EVERY CLASS IN THE PANEL'S FAMILY (FR-136): a door moved into one of the panel's helpers is still one of
        // its doors, and reading the panel's class alone would stop seeing it.
        java.util.Map<String, String> family = editorFamily();

        java.util.List<String> silent = new java.util.ArrayList<>();

        for (String member : family.values()) silent.addAll(silentWriters(member, writers(), examined));

        // And each helper the panel makes for itself is in the family, so none is left out by holding the panel some
        // other way
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

        // AND `toggle`, WHICH THE SCAN EXEMPTS THE SAME WAY (GSE-C1).  Each write is asked about its own lambda now,
        // and a checkbox's lambda says nothing: `toggle` announces after the action, for every checkbox on these
        // menus - so the exemption is only sound while the helper's own body still does.
        assertTrue(codeOf("    javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey,")
            .contains("placementChanged(null)"),
            "toggle() no longer announces, so every checkbox door on these menus is silent and the exemption is "
            + "hiding them (GSE-C1)");

        // AND THE LIGHT DOOR'S EXEMPTION IS ONLY SOUND WHILE IT ANNOUNCES (MT-334).
        //
        // Same shape as the radio check above and for the same reason: a door accepted here because
        // of what a helper does has to be re-examined whenever that helper changes.
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
     * The autonomy editor's panel and its helpers, each as its class name and its source (FR-136).
     *
     * The panel's class, and every class under gui/ that holds the panel in a `final` field: the package-private
     * helpers its tile menus, prompts and signal pairing moved into, which write the setup on the panel's behalf.  The
     * windows that mount the panel hold it in a field they reassign, and announce their writes their own way.  Found in
     * the source rather than listed, so a helper is swept the day it is written.
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
     * A lambda that writes the setup is not announced by another lambda's `setupChanged()` (GSE-C1).
     *
     * The rule above took each write's enclosing METHOD and asked whether that method announced anything.
     * `buildTileMenu` is one method that builds a whole menu, and several of its items call `setupChanged()` - so every
     * item on it passed on its neighbours' announcement, and an item whose own lambda wrote the setup and said nothing
     * would have passed too.  A lambda runs when its item is clicked, not when the menu is built.
     *
     * A fixture, because the real menu has no silent item to find.  One method with four items: a block lambda that
     * announces, one that does not, an expression lambda handed to `toggle` - which announces for whatever it runs -
     * and one handed to `item`, which does not.  And a second method whose write sits in an `if` with the announcement
     * after it, at the method's own level, which must still pass: a lambda is a scope, a branch is not.
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
     * Every write of the setup in a source that nothing announces, one entry per write (MT-246, VD10-B2, VD11-A1,
     * GSE-C1).
     *
     * **Asked of the write's own lambda, not of the method it is in (GSE-C1).**  The first version took each write's
     * enclosing method and asked whether it announced anything.  `buildTileMenu` is one method that builds a whole
     * menu, and several of its items call `setupChanged()` - so every item on it passed on its neighbours'
     * announcement, and one whose own lambda wrote the setup and said nothing would have passed too.  A lambda runs
     * when its item is clicked, not when the menu is built, so it is the lambda that has to announce.
     *
     * So the scope of a write is the innermost lambda that runs it - a block lambda's braces, or an expression
     * lambda's expression - and its method only where no lambda does.  An `if` or a `try` is not a scope: their braces
     * are walked out of, so a door that writes inside a branch and announces after it still passes.
     *
     * **Two helpers announce for their caller (VD11-A1).**  `radio(...)` and `toggle(...)` run the action they are
     * given and then call `placementChanged(null)` themselves, so a lambda handed to either is announced whatever its
     * own body says.  Named here, not a list of writers - which is the thing this rule kept being caught by - and each
     * is asserted to still announce by the rule that calls this.
     *
     * Read with comments and literals blanked and every offset kept, so a brace in a comment or an arrow in a string
     * is not code.
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

        // EVERY WRITER, not two of them (VD10-B2), in the order the file has them
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

            // The enclosing method: back to the nearest declaration at class indent, whatever its access - the
            // panel's helpers declare theirs package-private (FR-136).
            int start = line;

            while (start > 0 && !(lines[start].startsWith("    ") && lines[start].length() > 4
                && Character.isJavaIdentifierStart(lines[start].charAt(4)))) start--;

            String name = lines[start].trim();

            examined.add(name);

            // setupChanged is itself allowed to write nothing and announce nothing.  So is the
            // LIGHT door beside it, which is the same announcement without the grid rebuild.
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

            // placementChanged() counts: it ends in setupChanged().  And annotationsChanged() counts (MT-334): the
            // rule is "the running layout is told", and the light door tells it through rebuildRunningLayoutSoon,
            // without the grid rebuild that was the flicker Adam reported.
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
     * offset kept (GSE-C1).
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
     * Where the brace that closes the one at `open` is (GSE-C1).
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
     * Where an expression lambda's expression ends: the comma, semicolon or bracket that closes the argument it is
     * (GSE-C1).
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
     * argument (GSE-C1).
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
     * Every mutator `AutonomySession` has, read from its source rather than listed here (VD11-A1).
     *
     * **This was a list three times, and each list was short.** `MT-246`'s rule named the two setters
     * that finding happened to involve. `VD10-B2` widened it to twelve, in a commit whose own message
     * says *"a guard that lists the cases it knows about finds the cases it knows about"* - and that
     * list missed seven more, one of which (`setDirections`) differs from a listed one by a single
     * character, and one of which (`setBlockingPoints`) could never match anything because both real
     * writers go through `getStore()`.
     *
     * A list is the defect. So the question is asked of the type: every `public` method on
     * `AutonomySession` whose name begins with a verb that changes something. A new mutator is covered
     * the day it is written, and a renamed one cannot slip through a stale string.
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
     * Every door that saves the setup reads whether the save worked (ACC-C6, swept by OV2-C3).
     *
     * `saveQuietly()` returns false rather than throwing - that is the whole of what "quietly" means -
     * so a caller that discards the answer turns a failed write into silence. **Disk is authoritative
     * after the session rebuild**, so what comes back on the next load is the state the failed write
     * was meant to replace: an undo that does not stick, a page excluded in memory and offered to
     * autonomy on disk.
     *
     * `ACC-C6` found one such door and fixed it. It did not sweep, and `OV2-C3` found a third the same
     * day - which is this codebase's own fix-one-site-sweep-the-siblings rule being broken by the
     * commit that was applying it. **This rule exists so the sweep is not a matter of remembering.**
     *
     * Read from source because the alternative is making a real write fail, which needs a window and
     * an unwritable file. What is pinned is that the answer is CONSULTED - which is exactly what was
     * missing at all three sites.
     *
     * MUTATION: dropping the `!` from any call site fails this.
     */
    @Test
    public void testEverySaveOfTheSetupReadsWhetherItWorked() throws Exception
    {
        java.util.List<String> silent = new java.util.ArrayList<>();

        // EVERY FILE UNDER src/, not a list of four (FR3-C3).
        //
        // The first version named four files.  All three callers happened to be inside them, so it
        // held the tree it was written for - and a fourth door added anywhere else would have been
        // outside the sweep entirely, which is the failure this rule exists to prevent, arriving at
        // the rule itself.
        java.util.List<java.io.File> sources = new java.util.ArrayList<>();

        sources.addAll(javaFilesUnder(new java.io.File("src")));

        assertTrue(sources.size() > 50,
            "only " + sources.size() + " source files were found, so this rule is scanning almost "
            + "nothing and would pass whatever the code did");

        // THE ANSWER HAS TO GO SOMEWHERE, which is a different question from "is there a `!` nearby".
        //
        // Two wrong versions before this one.  Asking whether the statement contained `!` anywhere is
        // satisfied by `if (this.model != null) session.saveQuietly();`, which discards the answer -
        // the `!=` supplies the character.  Asking that a `!` APPLIES to the call was worse: it
        // flagged `saveQuietly() != true`, which consults the answer perfectly well, and an
        // over-strict guard is one Adam has ruled is worse than no guard.
        //
        // What actually separates them is whether the value is used at all.  A discarded call is
        // written `saveQuietly();` and nothing else; every use - negated, compared, assigned,
        // returned - puts something other than a semicolon after it.
        java.util.regex.Pattern discarded =
            java.util.regex.Pattern.compile("saveQuietly\\(\\)\\s*;");

        for (java.io.File source : sources)
        {
            String body = SourceOf.withoutComments(new String(
                java.nio.file.Files.readAllBytes(source.toPath()),
                java.nio.charset.StandardCharsets.UTF_8));

            // The declaration itself, not a call - and the session's own `return saveQuietly();`, which hands the
            // answer to its caller and which the pattern below cannot tell from a discarded one.
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
     * The sendable-destination rule is written ONCE, anywhere in the program (MON-C3).
     *
     * A station autonomy may send a train to is `isDestination() && isActive() && isAutoDestination()
     * && !isReversing()`, and it lives in `Layout.isSendableDestination`. Everything else asks.
     *
     * **The version of this guard that shipped could not see a single one of the copies it exists to
     * find.** It read only `Layout.java`, and it counted only lines carrying two of the clauses on ONE
     * PHYSICAL LINE. So it was blind to:
     *
     * - `AutoLocomotiveStatus.notChosenByAutonomy`, which had lost two limbs and marked no parking berth
     *   for as long as it had existed (MON-B1);
     * - `AutonomyEditorPanel.countDestinations`, three clauses of four, overcounting by every reversing
     *   station in the number an operator reads when nothing moves (MON-C2);
     * - the decomposed spelling in `barredFromAutonomy`, which is the rule as a list of reasons.
     *
     * It passed throughout. "Guard knows only what it lists", enforced on the one file that was already
     * behaving.
     *
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

        excused.put("gui/AutoLocomotiveStatus.java",
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

        // AND STILL ONCE INSIDE THE RULE'S OWN FILE, which is the check this replaced - kept, because
        // the file-level sweep above cannot see a second copy written NEXT TO the original.  Over the
        // whole program's code now (FR-136), so a second copy is counted whichever file it is in.
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
     * The three gestures that change what track is blocked each tell the diagram (W7B-B1).
     *
     * Adam, 2026-09-09: *"yes, this greyout should appear at idle and be regenerated if a placement or
     * train/track length is changed."*
     *
     * **Why this is a source-level check and the behaviour is tested elsewhere.**  Two of the three
     * gestures are pinned by driving them: `ui.testTheGreyAppearsAtIdleToo` presses the diagram's own
     * Delete key for a placement and rebuilds the setup for a tile length, and both claims fail if the
     * mark does not follow.  The third cannot be driven - `promptTrainLength` opens a modal, and a
     * test cannot answer one without a robot - so the half after the dialog was lifted into
     * `applyTrainLength`, which that class DOES drive.
     *
     * **And extracting a rule moves the bug to the call.**  A door that stops calling
     * `applyTrainLength` and sets the length itself passes every behavioural test there is, because
     * the extracted method still works.  This is the part that only the source can say.
     *
     * The fence is checked here too, in the one place that decided it, because a fence is a line that
     * can come back in a merge and every claim about the idle grey is silently about the running case
     * once it does.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testEveryDoorThatChangesWhatIsBlockedTellsTheDiagram() throws Exception
    {
        // THE FENCE IS GONE, and this is the one place that decided it.  Asked of the whole program (FR-136), so
        // the fence cannot come back in whichever method the decision moves to.
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

        // AND THE DIALOG STILL GOES THROUGH IT, which is the half the behavioural test cannot see.
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
     * The track diagram's own autonomy menu refreshes before it binds anything to a square (AUS-B1).
     *
     * That panel is built once per session and kept.  It learns which squares belong to a run - and which square
     * speaks for each run - only when it refreshes, and nothing told it when the EDITOR changed the railway: after a
     * page was brought back into autonomy, the first right-click on a run there bound every item to the clicked
     * square rather than the run's leader, so Segment Length wrote a follower and the run measured leader plus
     * follower.  One gesture wide and self-correcting on the next click, which is why it went unnoticed.
     *
     * Asked of the source, because the panel is reached through a menu this suite cannot open without a display and
     * the binding happens inside `buildTileMenu`: what has to hold is that the refresh comes first.
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
     * A cut asks for no autonomy setup it is not going to tell (OB-236; Adam, 2026-10-06: "fix the mentioned OB's that are
     * valid"): `deleteSelection` reads the session only when it is going to tell it what was emptied, and a cut is not.
     *
     * Read rather than run: every door that reaches a cut has had the session built by the editor's opening, which is
     * why the ask never showed - and why only the source can say whether it is made.
     *
     * MUTATION: ask for the session before asking whether it will be told, and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testACutAsksForNoSetupItWillNotTell() throws Exception
    {
        // The method, wherever it is (FR-136); renamed, `SourceOf.declaration` fails rather than look for nothing.  Its body
        // is LayoutEditorSelection's; the editor's `synchronized public` form is a delegator.
        String body = codeOf("    boolean deleteSelection(boolean tellAutonomy)");

        int ask = body.indexOf("getAutonomySession()");

        assertTrue(ask > 0, "deleteSelection no longer asks for the session at all");

        String statement = body.substring(body.lastIndexOf(";", ask) + 1, body.indexOf(";", ask));

        assertTrue(statement.contains("tellAutonomy"), "deleteSelection asks for the autonomy setup whether or not it will"
            + " tell it, which on a cut builds one nobody needed (OB-236): " + statement.trim());
    }

    /**
     * The route editor's copy mark is drawn in outlines in whatever ink it is given, as the arrows, the trash and the plus
     * beside it are (OB-258).  Its front sheet was filled white to hide the corner of the one behind, so on a selected row -
     * drawn in the selection's ink, white on the selection's blue - it was a solid white sheet among outlines.
     *
     * Painted with no display, into a picture of the selection's blue.
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

        org.traincontrol.gui.RowIcons.copy(size, java.awt.Color.WHITE).paintIcon(null, g, 0, 0);

        g.dispose();

        // The front sheet, as the mark measures it: asked of RowIcons rather than worked out again here, where a
        // copy of its proportions would go on sampling the old place after the mark moved (FR-136)
        java.lang.reflect.Method sheet =
            org.traincontrol.gui.RowIcons.class.getDeclaredMethod("frontSheet", int.class, int.class);

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
     * The "why is this train not moving" answer reads no setup collection on its worker (GSE-C3).
     *
     * `composeWhy` runs on `WhyRenderer`, off the event thread, and it asked the session for the turn squares, the
     * barred arrival sides, the switched-off squares and the reduction as it went.  Those are the setup's live
     * collections, and the event thread edits them - a click on the diagram while the answer is being worked out
     * changes them under the worker, which can throw part-way through a walk or draw routes from half an edit.
     * `applyWhy` already captures the railway and the station index on the event thread for the same reason, and
     * these belong beside them.
     *
     * MUTATION: put `session.shutTiles()` back into `composeWhy`'s `findPath` call, and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheWhyWorkerReadsNoSetupCollection() throws Exception
    {
        String worker = codeOf("private WhyAnswer composeWhy(");

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
     * The direction buttons' worker commands the locomotive it was pressed for, and leaves Swing to the event thread
     * (GST-C2).
     *
     * `forwardLoc` and `backwardLoc` stop the shown locomotive and set its direction on a new thread, because stopping
     * talks to the Central Station.  The thread read `activeLoc` again when it ran, so a press followed quickly by
     * picking another locomotive stopped and turned the second one; and it ticked the Forward and Backward buttons from
     * that thread, which is Swing off the event thread.  The locomotive is captured before the thread starts, and the
     * buttons are set in an `invokeLater` - which may read `activeLoc`, on the thread that writes it.
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
     * The text from an opening parenthesis to the one that closes it, both included.
     *
     * Counted rather than matched by a pattern, because a thread's body holds lambdas and calls of its own, and a
     * match that stopped at the first close would hand back a fragment.
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
