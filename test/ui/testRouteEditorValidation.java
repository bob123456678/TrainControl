package ui;

import java.awt.GraphicsEnvironment;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.traincontrol.util.I18n;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.ThreeWaySwitch;
import support.SourceOf;

/**
 * A row that looks right and means nothing: an accessory row turned into a locomotive command used to build, save and
 * do nothing when run.  Changing the kind clears the row, and Save checks each row against the layout; both are tested
 * here.
 */
public class testRouteEditorValidation
{
    /**
     * A manual route saves with the sensor field left blank (OB-178); Adam: "just treat this as 0."  The save and the
     * check share one reader.  The field is digits-only, so `enteredS88`'s -1 branch is unreachable from the UI and
     * not asserted.
     *
     * MUTATION: make `enteredS88` return -1 for an empty field and the first assertion fails; drop
     * the auto-fire rule and the second does.
     */
    @Test
    public void testAManualRouteNeedsNoSensor() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame editor = open();

        try
        {
        final java.util.List<String> blank = new java.util.ArrayList<>();
        final java.util.List<String> automatic = new java.util.ArrayList<>();
        final java.util.List<String> rubbish = new java.util.ArrayList<>();
        final java.util.List<String> zero = new java.util.ArrayList<>();
        final java.util.List<String> letters = new java.util.ArrayList<>();

        // The field is digits-only and drops anything else typed, so the filter is taken off: what is set is what the
        // editor reads.
        final javax.swing.JTextField s88 = (javax.swing.JTextField) privateField(editor, "s88Field");

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            ((javax.swing.text.AbstractDocument) s88.getDocument()).setDocumentFilter(null);

            editor.setAutoFireForTest(false);
            editor.setS88TextForTest("");

            blank.addAll(editor.problemsForTest());

            // THE OTHER HALF, which must keep refusing: a route that fires ITSELF has nothing to fire it without a
            // sensor.
            editor.setAutoFireForTest(true);

            automatic.addAll(editor.problemsForTest());

            // A SPACE, not letters: the field is digits-only, so "east" cannot be typed.  Whitespace alone must read as
            // none.
            editor.setAutoFireForTest(false);
            editor.setS88TextForTest("   ");

            rubbish.addAll(editor.problemsForTest());

            // WHAT AN EXPLICIT 0 IS REFUSED FOR, which is all a blank field is (Adam: "just treat this as 0")
            editor.setS88TextForTest("0");

            zero.addAll(editor.problemsForTest());

            // AND A SENSOR THE EDITOR CANNOT READ, so the refusal looked for below is seen to be there when it is earned
            editor.setS88TextForTest("east");

            letters.addAll(editor.problemsForTest());
        });

        final String notANumber = I18n.t("route.ui.frameS88NotANumber");

        assertTrue(letters.contains(notANumber),
            "precondition: a sensor field the editor cannot read is not refused with " + notANumber
            + ", so the claims below look for a message that is never said.  Problems: " + letters);

        assertFalse(blank.contains(notANumber),
            "a manual route with the sensor field left blank was refused as not-a-number.  Blank is "
            + "the answer a manual route gives - the field only means anything with auto-fire on "
            + "(OB-178).  Problems: " + blank);

        assertEquals(blank, zero,
            "a manual route with the sensor field left blank is refused for something a sensor of 0 is not.  "
            + "Blank is treated as 0 (OB-178).  Blank: " + blank + "; 0: " + zero);

        assertTrue(automatic.size() > blank.size(),
            "auto-fire with no sensor was accepted.  That route can never fire itself, and saved it "
            + "sits in the list marked automatic doing nothing: " + automatic);

        assertFalse(rubbish.contains(notANumber),
            "a sensor field holding only spaces was refused as not-a-number.  It is trimmed and it "
            + "is empty, which is the same answer a cleared field gives: " + rubbish);

        assertEquals(rubbish, blank,
            "a sensor field holding only spaces is refused for something a cleared field is not: " + rubbish
            + " against " + blank);
        }
        finally
        {
            close(editor);
        }
    }

    /** Changing the kind does not leave the old target behind, tested through the table model whose row got saved. */
    @Test
    public void testChangingTheKindClearsTheRow() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame.appendCommand(RouteCommand.RouteCommandAccessory(3,
                Accessory.accessoryDecoderType.MM2, true).toLine(null).trim());

            frame.setCommandKindForTest(0, CommandRow.Kind.LOCOMOTIVE_SPEED);
        });

        CommandRow row = frame.commandRowForTest(0);

        assertEquals(row.getKind(), CommandRow.Kind.LOCOMOTIVE_SPEED, "the kind did not change");

        assertEquals(row.getTarget(), "",
            "the accessory's address stayed behind as the locomotive's NAME, which is how a route "
            + "ends up commanding a locomotive called 3");

        close(frame);
    }

    /**
     * The route editor asks about a LOGICAL address, not `MAX_MM2_ADDRESS` (raw, counted from zero).  A shape check:
     * the editors here have no parent window, so `problemsWith` returns before any per-row rule.  The rule itself is
     * asserted by `core.testAccessory.testTheTopLogicalAddressOfEachProtocolIsValid`.
     *
     * MUTATION: put `Accessory.isValidAddress(address, speaks)` back in `addressProblem` and this fails.
     */
    @Test
    public void testTheAddressCheckAsksTheLogicalRule() throws Exception
    {
        String body = SourceOf.declaration("private void addressProblem(");

        assertTrue(body.contains("Accessory.isValidLogicalAddress("),
            "the route editor no longer asks the logical-address rule. Accessory's maxima are RAW and "
            + "say so; asking them with the number the operator typed refuses the last address of each "
            + "protocol - 320 on MM2, 2048 on DCC - which the diagram editor accepts, and Adam would "
            + "rather have no check than one that refuses something legal (S14-C1)");

        assertFalse(body.contains("Accessory.isValidAddress("),
            "the route editor asks the RAW rule as well as the logical one. Two rules over one number "
            + "is how these two editors came to disagree in the first place");
    }

    /** A three-way row starts with the pause its two motors need. */
    @Test
    public void testAThreeWayRowStartsWithItsPause() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame.appendCommand(RouteCommand.RouteCommandAccessory(3,
                Accessory.accessoryDecoderType.MM2, true).toLine(null).trim());

            frame.setCommandKindForTest(0, CommandRow.Kind.THREE_WAY);
        });

        assertEquals(frame.commandRowForTest(0).getDelay(), ThreeWaySwitch.SETTLE,
            "a three-way with no pause sends its second motor while the first is still moving");

        close(frame);
    }

    /** A three-way row is saved as the two commands it stands for, in order. */
    @Test
    public void testAThreeWayIsSavedAsBothOfItsCommands() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame.appendCommand(RouteCommand.RouteCommandAccessory(1,
                Accessory.accessoryDecoderType.MM2, false).toLine(null).trim());

            frame.setCommandKindForTest(0, CommandRow.Kind.THREE_WAY);
            frame.setCommandTargetForTest(0, "1");
            frame.setCommandSettingForTest(0, ThreeWaySwitch.wordFor(ThreeWaySwitch.Position.LEFT));
        });

        java.util.List<RouteCommand> saved = frame.commandsAsSaved();

        assertEquals(saved.size(), 2, "one row, two motors - a three-way saved as one command is "
            + "half a point");

        assertEquals(saved.get(0).getAddress(), 2,
            "left settles the SECOND address first; sending them the other way round drives the "
            + "point through the position in between on its way");

        assertFalse(saved.get(0).getSetting(), "and settles it straight");

        assertEquals(saved.get(0).getDelay(), ThreeWaySwitch.SETTLE, "with the pause on the first");

        assertEquals(saved.get(1).getAddress(), 1, "then turns the first");

        assertTrue(saved.get(1).getSetting());

        close(frame);
    }

    /**
     * The two messages that refuse a name say what is refused: a comma, and only a comma, since brackets are allowed
     * (Adam: "bracketed loc names should just be allowed").  Read from the English bundle.
     *
     * MUTATION: put "or a bracket" back in either and this fails.
     */
    @Test
    public void testTheNameRulesSayOnlyWhatIsRefused() throws Exception
    {
        java.util.Properties english = new java.util.Properties();

        try (java.io.InputStream in = org.traincontrol.gui.RouteEditorFrame.class
            .getResourceAsStream("/org/traincontrol/resources/messages.properties"))
        {
            english.load(in);
        }

        for (String key : new String[] {"route.ui.frameNameNotUsable", "loc.ui.errorLocomotiveNameUnusable"})
        {
            String said = english.getProperty(key);

            assertNotNull(said, "precondition: the bundle has no " + key);

            assertTrue(said.contains("comma"), key + " no longer says a comma is refused: " + said);

            assertFalse(said.toLowerCase().contains("bracket"), key + " says a name cannot hold a bracket, which"
                + " `RouteCommand.isNameUsable` allows (Adam, 2026-09-04): " + said);
        }

        assertTrue(org.traincontrol.base.RouteCommand.isNameUsable("SBB 460 (2)")
            && !org.traincontrol.base.RouteCommand.isNameUsable("A, B"), "control: the rule itself has changed");
    }

    /**
     * With Automatically Fire off, the conditions say above themselves, in red, that they are only checked when it is
     * on; ticking it takes the warning away, and a route with no conditions has none.
     *
     * MUTATION: show the reading alone whatever the tick, or leave the warning up once it is ticked, and this fails.
     */
    @Test
    public void testConditionsSayTheyAreUncheckedWithoutAutomaticFire() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final boolean[] warns = new boolean[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setAutoFireForTest(false);
                warns[0] = frame.warnsTheConditionsAreUnaskedForTest();
            });

            assertFalse(warns[0], "a route with no conditions warns about its conditions");

            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAutoLocomotive("BR 218", 21, true)));

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(rows);
                warns[0] = frame.warnsTheConditionsAreUnaskedForTest();
            });

            assertTrue(warns[0], "with Automatically Fire off, the conditions do not say they are only checked when it is"
                + " on: " + frame.readsAsForTest());

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setAutoFireForTest(true);
                warns[0] = frame.warnsTheConditionsAreUnaskedForTest();
            });

            assertFalse(warns[0], "with Automatically Fire ticked, the conditions still say they are not checked");

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setAutoFireForTest(false);
                warns[0] = frame.warnsTheConditionsAreUnaskedForTest();
            });

            assertTrue(warns[0], "unticking Automatically Fire again did not bring the warning back");
        }
        finally
        {
            close(frame);
        }
    }

    private static Object privateField(Object owner, String name) throws Exception
    {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);

        field.setAccessible(true);

        return field.get(owner);
    }

    private static org.traincontrol.gui.RouteEditorFrame open() throws Exception
    {
        final org.traincontrol.gui.RouteEditorFrame[] frame =
            new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            frame[0] = new org.traincontrol.gui.RouteEditorFrame(null, null));

        return frame[0];
    }

    private static void close(org.traincontrol.gui.RouteEditorFrame frame) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.dispose());
    }

    private static void needsADisplay()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window - this needs a display");
        }
    }
    /**
     * Deleting an unrelated condition does not turn a leading "or" into "and": `tidy()` once forced row 0 flat, so
     * `(A or B) and C` minus `C` read as `A and B` with nothing in red, and Save wrote the changed meaning.
     *
     * MUTATION: restoring `at == 0 ? 0` fails the second assertion.
     */
    @Test
    public void testDeletingAConditionDoesNotChangeTheGroupAboveIt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            java.util.List<org.traincontrol.base.ConditionOutline.Row> outline = new java.util.ArrayList<>();

            outline.add(org.traincontrol.base.ConditionOutline.Row.condition(1, feedback(1)));
            outline.add(org.traincontrol.base.ConditionOutline.Row.joining(1, org.traincontrol.base.ConditionOutline.Joiner.OR));
            outline.add(org.traincontrol.base.ConditionOutline.Row.condition(1, feedback(2)));
            outline.add(org.traincontrol.base.ConditionOutline.Row.joining(0, org.traincontrol.base.ConditionOutline.Joiner.AND));
            outline.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(3)));

            javax.swing.SwingUtilities.invokeAndWait(
                () -> frame.setConditionRowsForTest(outline));

            // THE PRECONDITION: it really does mean `(A or B) and C` before the edit.
            assertTrue(reads(frame).equals("And(Group(Or(x,x)),x)"),
                "the fixture does not start as an OR group inside an AND, so the assertion below "
                + "pass against an outline that never had one: " + reads(frame));

            // The LAST condition, which has nothing to do with the group.
            javax.swing.SwingUtilities.invokeAndWait(() -> frame.deleteConditionForTest(4));

            assertTrue(reads(frame).contains("Or("),
                "deleting an unrelated condition turned the group's OR into an AND.  tidy() forced "
                + "row 0 flat, and a condition beginning with a bracketed group legitimately starts "
                + "one level in - so the outline read back as `A and B`, nothing was flagged, and "
                + "Save would write a route that fires at times nobody asked for (FR3-B1).  Now: "
                + reads(frame));
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * Deleting the FIRST condition of a group takes the group's own word, not the outer one: deleting the `2` of
     * `1 or (2 and 3)` once removed the `or`, changing when the route fires and leaving the word red.  The word that
     * belongs to a row is the adjacent joiner at the SAME depth.
     *
     * MUTATION: put `if (line - 1 >= 0 && rows.get(line - 1).isJoiner()) rows.remove(line - 1);` back
     * without the depth test and the second claim goes red, reading `And(x,x)`.
     */
    @Test
    public void testDeletingTheFirstConditionOfAGroupKeepsTheOuterWord() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> outline =
                new java.util.ArrayList<>();

            outline.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(1)));
            outline.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                org.traincontrol.base.ConditionOutline.Joiner.OR));
            outline.add(org.traincontrol.base.ConditionOutline.Row.condition(1, feedback(2)));
            outline.add(org.traincontrol.base.ConditionOutline.Row.joining(1,
                org.traincontrol.base.ConditionOutline.Joiner.AND));
            outline.add(org.traincontrol.base.ConditionOutline.Row.condition(1, feedback(3)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(outline));

            // THE EXACT SHAPE, because `startsWith("Or(")` is satisfied by a FLAT outline and the claim below is about
            // depth.
            assertEquals(reads(frame), "Or(x,Group(And(x,x)))",
                "the fixture is not an OR in front of a bracketed group, so nothing below is about one: "
                + reads(frame));

            // THE FIRST CONDITION OF THE GROUP, which is row 2.
            javax.swing.SwingUtilities.invokeAndWait(() -> frame.deleteConditionForTest(2));

            // AND THE EXACT SHAPE AFTER IT: a startsWith check passes for an outline swept down to one condition, or
            // emptied.
            assertEquals(reads(frame), "Or(x,Group(x))",
                "deleting the first condition of the group did not leave `1 or (3)`.  The word that"
                + " belongs to a row is the joiner at its own depth; taking the OUTER one turns this"
                + " into an AND, and taking the whole following term deletes a condition nobody asked"
                + " to lose.  Now: " + reads(frame));

            assertEquals(frame.conditionRowsForTest().size(), 3,
                "the outline should hold three lines - the two conditions and the word between them -"
                + " and holds " + frame.conditionRowsForTest().size() + ": "
                + frame.conditionRowsForTest());

            // AND STILL SAVEABLE: the word the old branch left joined nothing and Save refused.  The CONDITION
            // complaint only, since this fixture has no name or command.
            assertFalse(frame.problemsForTest().contains(I18n.t("route.ui.frameLogicDisagrees")),
                "the outline left by deleting one condition disagrees with itself, so Save refuses the"
                + " route and the only way out is Discard: " + frame.problemsForTest());
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * Deleting a joining word takes the term it joins with it, leaving no orphan (OB-240).  An orphan changes when the
     * route fires: `tidy()` allows a run with no word and `toExpression` folds it with AND.  Two shapes, `1 or 2` and
     * `1 or (2 and 3)`, since the term after a word may be a whole group.
     *
     * MUTATION: put back the bare `rows.remove(line)` for a joiner and the first pair of claims goes red
     * with `And(x,x)`; keep the single-line removal but drop the group loop and the second does.
     */
    @Test
    public void testDeletingAJoiningWordTakesTheTermItJoinsWithIt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> pair =
                new java.util.ArrayList<>();

            pair.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(1)));
            pair.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                org.traincontrol.base.ConditionOutline.Joiner.OR));
            pair.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(2)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(pair));

            assertTrue(reads(frame).startsWith("Or("),
                "the fixture does not start as an OR of two conditions, so nothing below is about an OR: "
                + reads(frame));

            // THE WORD ITSELF, which is row 1.
            javax.swing.SwingUtilities.invokeAndWait(() -> frame.deleteConditionForTest(1));

            assertEquals(frame.conditionRowsForTest().size(), 1,
                "deleting the joining word left " + frame.conditionRowsForTest().size() + " lines behind."
                + "  The word joined two conditions and one of them has nothing to be joined to any more"
                + " - Adam's orphan entry (OB-240)");

            assertFalse(reads(frame).contains("And("),
                "deleting the OR left both conditions with nothing between them, and a wordless run is"
                + " folded with AND - so the route now fires only when BOTH sensors are on, which is the"
                + " opposite of what was typed and nothing says so: " + reads(frame));

            // AND THE GROUP, where the term after the word is three lines rather than one.
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> group =
                new java.util.ArrayList<>();

            group.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(1)));
            group.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                org.traincontrol.base.ConditionOutline.Joiner.OR));
            group.add(org.traincontrol.base.ConditionOutline.Row.condition(1, feedback(2)));
            group.add(org.traincontrol.base.ConditionOutline.Row.joining(1,
                org.traincontrol.base.ConditionOutline.Joiner.AND));
            group.add(org.traincontrol.base.ConditionOutline.Row.condition(1, feedback(3)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(group));

            assertTrue(reads(frame).contains("Or("),
                "the second fixture is not an OR in front of a group: " + reads(frame));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.deleteConditionForTest(1));

            assertEquals(frame.conditionRowsForTest().size(), 1,
                "deleting the word in front of a bracketed group left part of the group behind: "
                + frame.conditionRowsForTest() + ", reading " + reads(frame));
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * FR-068 (MT-320): a nested group that is NOT the first term can be built by indenting.  Seven lines are typed flat,
     * as the plus does, then indented into `3 or (4 and (1 or 2))`: the group goes on the right because a line may be at
     * most one level deeper than the line above it (`ConditionTable.indent`).
     *
     * MUTATION: dropping `ConditionTable.indent`'s one-level rule does not fail this (it only ever
     * admits more); forcing every indent to be refused does, at the first assertion.
     */
    @Test
    public void testANestedGroupThatIsNotTheFirstTermCanBeBuilt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            // FLAT, the only shape the plus can produce: a new condition takes the depth of the line above it.
            java.util.List<org.traincontrol.base.ConditionOutline.Row> typed =
                new java.util.ArrayList<>();

            typed.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(3)));
            typed.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                org.traincontrol.base.ConditionOutline.Joiner.OR));
            typed.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(4)));
            typed.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                org.traincontrol.base.ConditionOutline.Joiner.AND));
            typed.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(1)));
            typed.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                org.traincontrol.base.ConditionOutline.Joiner.OR));
            typed.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(2)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(typed));

            // PRECONDITION: typed flat, it is one level that disagrees with itself, the state the editor draws in red
            // and refuses to save.
            assertFalse(org.traincontrol.base.ConditionOutline.problems(
                frame.conditionRowsForTest()).isEmpty(),
                "seven lines typed flat with both words on them should be flagged as a level that "
                + "disagrees with itself, and were not - so the indenting below is not resolving "
                + "anything and this test would pass on an outline that never needed it");

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                // The AND and everything after it, one level in: "4 and 1 or 2" becomes the group
                // that the leading OR joins to sensor 3.
                frame.indentConditionForTest(2, 1);
                frame.indentConditionForTest(3, 1);
                frame.indentConditionForTest(4, 1);
                frame.indentConditionForTest(5, 1);
                frame.indentConditionForTest(6, 1);

                // And the innermost pair one level further, which is the bracket after the start.
                frame.indentConditionForTest(4, 1);
                frame.indentConditionForTest(5, 1);
                frame.indentConditionForTest(6, 1);
            });

            assertTrue(org.traincontrol.base.ConditionOutline.problems(
                frame.conditionRowsForTest()).isEmpty(),
                "the indented outline is still flagged, so the editor would refuse to save the very "
                + "shape this test says it can build: "
                + org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest())
                + " in " + depths(frame));

            // THE RULE THAT DECIDES WHICH ORDER IS TYPEABLE: line 2 follows the outer OR at depth 0 so it can never be
            // deeper than 1, and `ConditionOutline.of` puts a left-child group's bracket at depth 2.
            final String before = depths(frame);

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.indentConditionForTest(2, 1));

            assertEquals(depths(frame), before,
                "a line immediately after a depth-0 joiner was allowed to jump to depth 2, so the "
                + "one-level rule has gone - which would make the outline able to draw a nesting "
                + "with a hole in the middle. Depths: " + depths(frame));

            assertEquals(reads(frame), "Or(x,Group(And(x,Group(Or(x,x)))))",
                "the outline built by indenting does not mean \"3 or (4 and (1 or 2))\". Depths: "
                + depths(frame) + ", meaning: " + reads(frame) + ". FR-068 asks whether a bracket "
                + "that is not the first term can be built at all, and this is the gesture that "
                + "does it");
        }
        finally
        {
            close(frame);
        }
    }

    /** The depths of the outline, for a failure message. */
    /**
     * A condition that starts with a group can be built by indenting (Adam: "the first condition for some reason cannot
     * be indented for proper grouping.").  The first line now counts as having an unindented line above it.
     */
    @Test
    public void testAConditionThatStartsWithAGroupCanBeBuilt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> typed = flat(2, "OR", 3, "AND", 1, "OR", 4);

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(typed));

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.indentConditionForTest(0, 1);
                frame.indentConditionForTest(1, 1);
                frame.indentConditionForTest(2, 1);
                frame.indentConditionForTest(4, 1);
                frame.indentConditionForTest(5, 1);
                frame.indentConditionForTest(6, 1);
            });

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the indented outline is still flagged: " + depths(frame));

            assertEquals(reads(frame), "And(Group(Or(x,x)),Group(Or(x,x)))",
                "(2 or 3) and (1 or 4) could not be built by indenting - the first condition cannot go in, so a"
                + " condition cannot start with a group. Adam, 2026-09-14: \"the first condition for some reason"
                + " cannot be indented for proper grouping\". Depths:" + depths(frame));
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * A joining word indented on its own is shown in red, not read as nothing; Save refuses it until its neighbors are
     * indented too.
     */
    @Test
    public void testAWordIndentedAloneIsFlagged() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> typed = flat(2, "OR", 3);

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(typed));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.indentConditionForTest(1, 1));

            assertEquals(depths(frame), " 0? 1OR 0?",
                "precondition: indenting the OR on its own did not put it one level in, so there is no stranded word"
                + " to flag. Depths:" + depths(frame));

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).contains(1),
                "the OR indented past the conditions either side of it is not flagged - so it is read as nothing and"
                + " \"2 or 3\" is saved as \"2 and 3\" with nothing in red. Adam, 2026-09-14: \"the first or is read"
                + " as an and here\". Depths:" + depths(frame));
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * "Reads as" says the joining words in bold and the settings in the color of what they do, colored by the WORD
     * because the same true/false means "on" for a sensor and "turn" for a switch.
     */
    @Test
    public void testReadsAsBoldsTheWordsAndColoursTheSettings() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                org.traincontrol.base.RouteCommand.RouteCommandFeedback(2, false)));
            rows.add(org.traincontrol.base.ConditionOutline.Row.joining(0, org.traincontrol.base.ConditionOutline.Joiner.OR));
            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                org.traincontrol.base.RouteCommand.RouteCommandFeedback(3, true)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));

            final String[] text = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> text[0] = frame.readsAsForTest());

            String or = org.traincontrol.util.I18n.t("route.ui.joinOr");

            assertTrue(text[0].contains("<b>" + or + "</b>"),
                "the joining word is not bold in \"Reads as\": " + text[0]);

            assertTrue(text[0].contains("color=\"" + org.traincontrol.gui.RouteEditorFrame.READS_STOP_COLOUR + "\">off</font>"),
                "\"off\" is not red in \"Reads as\": " + text[0]);

            assertTrue(text[0].contains("color=\"" + org.traincontrol.gui.RouteEditorFrame.READS_GO_COLOUR + "\">on</font>"),
                "\"on\" is not green in \"Reads as\": " + text[0]);
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * Choosing the kind a condition line already is leaves it alone: the table reset a line to its kind's starting
     * address on every commit of the Kind cell.  A switch and a sensor are used since without a layout every accessory
     * reads as a switch.
     */
    @Test
    public void testChoosingTheKindALineAlreadyIsKeepsIt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAccessory(40, Accessory.accessoryDecoderType.MM2, true)));
            rows.add(org.traincontrol.base.ConditionOutline.Row.joining(0, org.traincontrol.base.ConditionOutline.Joiner.AND));
            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(5)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));

            final String[] before = { shown(frame, 0), shown(frame, 2) };

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionKindForTest(0, CommandRow.Kind.ACCESSORY);
                frame.setConditionKindForTest(2, CommandRow.Kind.FEEDBACK);
            });

            assertEquals(shown(frame, 0), before[0],
                "choosing Switch on a Switch 40 line changed it - the Kind drop-down reset the line to its kind's"
                + " starting address. Adam, 2026-09-14: 'if I click on Kind -> Signal and exit out, it snaps back to"
                + " Switch 1'");

            assertEquals(shown(frame, 2), before[1],
                "choosing Sensor on a sensor 5 line changed it - the same reset, on a kind that has no layout question");
        }
        finally
        {
            close(frame);
        }
    }

    /** Switch and Signal are one command, so moving a line between them keeps its address. */
    @Test
    public void testSwitchingBetweenSwitchAndSignalKeepsTheAddress() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAccessory(40, Accessory.accessoryDecoderType.MM2, true)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));

            final String before = shown(frame, 0);

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionKindForTest(0, CommandRow.Kind.SIGNAL));

            assertEquals(shown(frame, 0), before,
                "choosing Signal on a Switch 40 turn line lost its address or its setting - Switch and Signal are"
                + " stored as the same accessory command, so the line should still be address 40, set the same way");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * A red word carries a warning triangle whose tooltip says which of the two things is wrong.  The cell is read as
     * the table PAINTS it, and the renderer reuses one label down the column, so an unflagged line must carry neither.
     */
    @Test
    public void testARedWordCarriesAWarningThatSaysWhy() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            // 2 or 3 and 4: the AND differs from the OR already joining the level.
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> mixed = flat(2, "OR", 3, "AND", 4);

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(mixed));

            // Each cell is read the moment it is painted: the renderer hands back ONE label, so reading two references
            // afterwards reads the last cell twice.  The flagged line is painted first so the second read is where a
            // stale warning shows.
            final Painted[] cell = new Painted[2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                cell[0] = new Painted(frame.conditionCellForTest(3));
                cell[1] = new Painted(frame.conditionCellForTest(1));
            });

            assertTrue(cell[0].icon != null, "the red AND in '2 or 3 and 4' has no warning triangle - painted '"
                + cell[0].text + "'; the outline says "
                + org.traincontrol.base.ConditionOutline.whatIsWrong(frame.conditionRowsForTest()) + ". Depths:" + depths(frame));

            assertTrue(String.valueOf(cell[0].tooltip).contains(org.traincontrol.util.I18n.t("route.ui.conditionWordDisagrees")),
                "the red AND's tooltip does not say that it differs from the word already joining its level: " + cell[0].tooltip);

            assertNull(cell[1].icon, "the OR in '2 or 3 and 4' is not flagged and wears the triangle of the line painted before it");
            assertNull(cell[1].tooltip, "the OR in '2 or 3 and 4' is not flagged and wears the tooltip of the line painted before it");

            // 2 or 3 with the OR alone indented: it joins nothing.
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> stranded = flat(2, "OR", 3);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(stranded);
                frame.indentConditionForTest(1, 1);
                cell[0] = new Painted(frame.conditionCellForTest(1));
            });

            assertTrue(cell[0].icon != null, "the OR indented on its own has no warning triangle. Depths:" + depths(frame));

            assertTrue(String.valueOf(cell[0].tooltip).contains(org.traincontrol.util.I18n.t("route.ui.conditionWordJoinsNothing")),
                "the stranded OR's tooltip does not say that it joins nothing: " + cell[0].tooltip + ". Depths:" + depths(frame));
        }
        finally
        {
            close(frame);
        }
    }

    /** What one painted cell showed, copied out before the renderer's shared label paints the next one. */
    private static final class Painted
    {
        final javax.swing.Icon icon;
        final String tooltip;
        final String text;

        Painted(javax.swing.JLabel label)
        {
            icon = label.getIcon();
            tooltip = label.getToolTipText();
            text = label.getText();
        }
    }

    /**
     * FR-144: "1 or 2 or 3 and 4" typed flat becomes "(1 or 2 or 3) and 4" by selecting the first three conditions and
     * pressing the indent mark once; 4's arrows then move it past the whole group and back, every line keeping its
     * level; outdenting the selection undoes the indent.  Every step is a mouse press on the condition table.
     *
     * MUTATION: drop `ConditionTable.changeSelection`'s override, and the press collapses the selection to one line,
     * so only sensor 1 moves in.
     * MUTATION: drop the group branch of `ConditionOutline.moved`, and 4 trades places with 3 inside the group.
     */
    @Test
    public void testSeveralConditionsIndentAtOnceAndOneMovesPastTheGroup() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame.setConditionRowsForTest(flat(1, "OR", 2, "OR", 3, "AND", 4)));

            assertFalse(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "precondition: 'or' and 'and' side by side at one level should be flagged");

            final javax.swing.JTable table = support.Components.find(frame, javax.swing.JTable.class,
                t -> "ConditionTable".equals(t.getClass().getSimpleName()));

            assertNotNull(table, "the route editor has no condition table");

            selectLines(table, 0, 4);
            clickCell(table, 0, INDENT_MARK, 0);

            assertEquals(levels(frame), " 1:1 1OR 1:2 1OR 1:3 0AND 0:4",
                "the three selected conditions and the words between them go one level in, the 'and' stays");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "(1 or 2 or 3) and 4 is flagged: " + levels(frame));

            clickCell(table, 6, UP_ARROW, 0);

            assertEquals(levels(frame), " 0:4 0AND 1:1 1OR 1:2 1OR 1:3",
                "4 moved up past the whole group, with the 'and' between them and every level kept");

            clickCell(table, 0, DOWN_ARROW, 0);

            assertEquals(levels(frame), " 1:1 1OR 1:2 1OR 1:3 0AND 0:4", "and moved back down past it");

            selectLines(table, 0, 4);
            clickCell(table, 0, OUTDENT_MARK, 0);

            assertEquals(levels(frame), " 0:1 0OR 0:2 0OR 0:3 0AND 0:4", "outdenting the selection undoes it");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-379: "(a and b and c) or (d and e and f)", the plus adds g, and g's up arrow pressed four times gives
     * "(a and b and c and g) or (d and e and f)" with g still selected; its down arrow takes it back.
     *
     * MUTATION: drop the edge branch of `ConditionOutline.plan`, and the fourth press gives "(a and b and g) or (c and d
     * and e and f)".
     */
    @Test
    public void testAConditionAddedAtTheEndMovesUpIntoTheGroupAbove() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(
                fromLevels(" 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26")));

            final javax.swing.JTable table = conditionTable(frame);

            clickCell(table, frame.conditionRowsForTest().size(), UP_ARROW, 0);

            assertEquals(levels(frame), " 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26 1AND 1:1",
                "precondition: the plus adds g (sensor 1) at the end of the second group");

            int g = frame.conditionRowsForTest().size() - 1;

            for (int press = 1; press <= 4; press++)
            {
                clickCell(table, g, UP_ARROW, 0);

                g = selectedLine(table);
            }

            assertEquals(levels(frame), " 1:21 1AND 1:22 1AND 1:23 1AND 1:1 0OR 1:24 1AND 1:25 1AND 1:26",
                "four presses of g's up arrow should give (a and b and c and g) or (d and e and f)");

            assertEquals(g, 6, "g is no longer the selected line after the move");

            clickCell(table, g, DOWN_ARROW, 0);

            assertEquals(levels(frame), " 1:21 1AND 1:22 1AND 1:23 0OR 1:1 1AND 1:24 1AND 1:25 1AND 1:26",
                "g's down arrow takes it back to the front of the second group");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * FR-147: a right-click on a condition offers Insert Condition Above and Below and the line's own actions, each
     * enabled exactly where its mark does something; Insert puts a new condition at the line's level, joined by its
     * level's word, and selects it; a right-click on a mark opens the menu without working the mark; below the lines it
     * offers Add Condition.
     *
     * MUTATION: in `ConditionOutline.inserted`, join the new condition with `Joiner.AND` always, and Insert Condition
     * Below on 22 gives "21 or 22 and 1", flagged.
     */
    @Test
    public void testARightClickInsertsAConditionAndOffersTheLinesActions() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 1:21 1OR 1:22 0AND 0:23"));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);

            // Under the plus, where the table is empty: the window opens tall enough for a few more lines
            java.awt.Rectangle plus = support.Components.onTheEventThread(
                () -> table.getCellRect(table.getRowCount() - 1, 0, true));

            javax.swing.JPopupMenu menu = support.Mouse.rightClickAt(table,
                new java.awt.Point(plus.x + 40, plus.y + plus.height + 6), 5000);

            assertNotNull(menu, "a right-click below the lines opened no menu");
            assertEquals(offered(menu), "+route.ui.menuAddCondition", "the menu below the lines");

            support.Mouse.choose(item(menu, "route.ui.menuAddCondition"));

            assertEquals(levels(frame), " 1:21 1OR 1:22 0AND 0:23 0AND 0:1", "Add Condition adds one at the end");
            assertEquals(selectedLine(table), 6, "and selects it");

            menu = rightClickCell(table, 2, NOTHING_TO_EDIT);

            assertNotNull(menu, "a right-click on condition 22 opened no menu");

            assertEquals(offered(menu), "+route.ui.menuInsertConditionAbove +route.ui.menuInsertConditionBelow"
                + " +route.ui.menuMoveUp -route.ui.menuMoveDown +route.ui.menuIndent +route.ui.menuOutdent"
                + " +ui.delete", "the menu on 22 in (21 or 22) and 23 and 1, enabled (+) or not (-)");

            assertEquals(selectedLine(table), 2, "the line right-clicked is the one selected");

            support.Mouse.choose(item(menu, "route.ui.menuInsertConditionBelow"));

            assertEquals(levels(frame), " 1:21 1OR 1:22 1OR 1:1 0AND 0:23 0AND 0:1",
                "Insert Condition Below puts sensor 1 under 22, in its group and joined by its 'or'");
            assertEquals(selectedLine(table), 4, "and the new condition is selected");

            menu = rightClickCell(table, 4, UP_ARROW);

            assertNotNull(menu, "a right-click on the up arrow opened no menu");
            assertEquals(levels(frame), " 1:21 1OR 1:22 1OR 1:1 0AND 0:23 0AND 0:1",
                "a right-click on a mark worked the mark");

            support.Mouse.choose(item(menu, "route.ui.menuInsertConditionAbove"));

            assertEquals(levels(frame), " 1:21 1OR 1:22 1OR 1:1 1OR 1:1 0AND 0:23 0AND 0:1",
                "Insert Condition Above puts one above, in the same group");
            assertEquals(selectedLine(table), 4, "and selects it");
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * Add Condition and the plus join the new condition with the word of the last condition's level, as Insert
     * Condition Below does: "21 or 22" plus Add gives "21 or 22 or 1".
     *
     * MUTATION: in `ConditionTable.addRow`, join with `Joiner.AND` always, and Add gives "21 or 22 and 1", flagged.
     */
    @Test
    public void testAddConditionJoinsWithTheLevelsWord() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 0:21 0OR 0:22"));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);

            java.awt.Rectangle plus = support.Components.onTheEventThread(
                () -> table.getCellRect(table.getRowCount() - 1, 0, true));

            javax.swing.JPopupMenu menu = support.Mouse.rightClickAt(table,
                new java.awt.Point(plus.x + 40, plus.y + plus.height + 6), 5000);

            assertNotNull(menu, "a right-click below the lines opened no menu");

            support.Mouse.choose(item(menu, "route.ui.menuAddCondition"));

            assertEquals(levels(frame), " 0:21 0OR 0:22 0OR 0:1", "Add Condition after \"21 or 22\" joins with its"
                + " level's 'or'");

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(
                fromLevels(" 0:21 0AND 1:22 1OR 1:23")));

            clickCell(table, frame.conditionRowsForTest().size(), UP_ARROW, 0);

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 1:23 1OR 1:1", "the plus after \"21 and (22 or 23)\""
                + " joins with the group's 'or'");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the outline the plus made is flagged: "
                + org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()));
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * A capture into the conditions joins with the word of the last condition's level, as Add Condition does: "21 or
     * 22" plus a captured accessory is an outline Save takes.
     *
     * MUTATION: in `appendCommand`, join a captured condition with `Joiner.AND` always, and the outline is flagged.
     */
    @Test
    public void testACaptureIntoTheConditionsJoinsWithTheLevelsWord() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 0:21 0OR 0:22"));
                frame.setCapturingIntoConditions(true);
                frame.appendCommand(RouteCommand.RouteCommandAccessory(23, Accessory.accessoryDecoderType.MM2, true)
                    .toLine(null).trim());
            });

            assertTrue(levels(frame).startsWith(" 0:21 0OR 0:22 0OR 0:"), "a capture after \"21 or 22\" does not join"
                + " with its level's 'or': " + levels(frame));

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the outline the capture made is flagged: "
                + org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()));
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * FR-147, what an edit changed: after a move, an indent, an outdent or the plus, the condition that changed is
     * selected and "Reads as" shows it in bold, where it now sits in the sentence.
     *
     * MUTATION: in `describe`, drop the bold round a chosen condition, and no condition is bold.
     */
    @Test
    public void testTheReadingShowsTheConditionAnEditChanged() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(flat(21, "AND", 22, "AND",
                23)));

            final javax.swing.JTable table = conditionTable(frame);

            clickCell(table, 4, UP_ARROW, 0);

            assertEquals(levels(frame), " 0:21 0AND 0:23 0AND 0:22", "precondition: 23 moved up");
            assertEquals(selectedLine(table), 2, "23 is selected after its move");
            assertEquals(bold(frame), "[S88 #23]", "after the move, Reads as: " + frame.readsAsForTest());

            clickCell(table, 2, INDENT_MARK, 0);

            assertEquals(levels(frame), " 0:21 0AND 1:23 0AND 0:22", "precondition: 23 indented");
            assertEquals(bold(frame), "[S88 #23]", "after the indent, Reads as: " + frame.readsAsForTest());

            clickCell(table, 2, OUTDENT_MARK, 0);

            assertEquals(bold(frame), "[S88 #23]", "after the outdent, Reads as: " + frame.readsAsForTest());

            clickCell(table, frame.conditionRowsForTest().size(), UP_ARROW, 0);

            assertEquals(selectedLine(table), 6, "the condition the plus added is selected");
            assertEquals(bold(frame), "[S88 #1]", "after the plus, Reads as: " + frame.readsAsForTest());
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * FR-147: deleting a condition that was inserted, moved to the front of the next group and outdented takes the
     * "and" that now joins nothing, not the "or": "(21 and 22) or (24 and 25)" is left as it was.
     *
     * MUTATION: in `ConditionTable.removeAt`, always take the word before a condition when it is at the condition's own
     * depth, and the "or" between the groups goes.
     */
    @Test
    public void testDeletingAnOutdentedConditionKeepsTheWordBetweenTheGroups() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 1:21 1AND 1:22 0OR 1:24 1AND 1:25"));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);

            rightClickAndChoose(table, 2, "route.ui.menuInsertConditionBelow");
            rightClickAndChoose(table, 4, "route.ui.menuMoveDown");
            rightClickAndChoose(table, 4, "route.ui.menuOutdent");

            assertEquals(levels(frame), " 1:21 1AND 1:22 0OR 0:1 1AND 1:24 1AND 1:25",
                "precondition: the inserted condition, moved down and outdented, stands before the second group");

            rightClickAndChoose(table, 4, "ui.delete");

            assertEquals(levels(frame), " 1:21 1AND 1:22 0OR 1:24 1AND 1:25",
                "deleting it should leave (21 and 22) or (24 and 25); Reads as: " + plainReading(frame));

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the outline the delete left is flagged: "
                + org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()));
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * FR-147: Insert Condition Above and Below, from a right-click, put one new condition next to the line clicked, at
     * its depth and joined by its level's word, change nothing else, and select it, bold in "Reads as"; flat lists,
     * nested groups and one condition.
     *
     * MUTATION: in `ConditionOutline.wordOfTheLevel`, drop the walk back to the group's first line, and Insert Below on
     * the last line of "(22 or 23 or 24)" joins with "and".
     */
    @Test
    public void testInsertConditionPutsOneNextToTheLineClicked() throws Exception
    {
        needsADisplay();

        final String flatAnd = " 0:21 0AND 0:22 0AND 0:23";
        final String flatOr = " 0:21 0OR 0:22 0OR 0:23";
        final String twoGroups = " 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26";

        // start, line, Above or Below, the outline after, the selected line after, how Reads as reads after
        final Object[][] cases = {
            {flatAnd, 0, "Above", " 0:1 0AND 0:21 0AND 0:22 0AND 0:23", 0, "#1 and #21 and #22 and #23"},
            {flatAnd, 2, "Above", " 0:21 0AND 0:1 0AND 0:22 0AND 0:23", 2, "#21 and #1 and #22 and #23"},
            {flatAnd, 4, "Below", " 0:21 0AND 0:22 0AND 0:23 0AND 0:1", 6, "#21 and #22 and #23 and #1"},
            {flatOr, 0, "Above", " 0:1 0OR 0:21 0OR 0:22 0OR 0:23", 0, "#1 or #21 or #22 or #23"},
            {flatOr, 2, "Below", " 0:21 0OR 0:22 0OR 0:1 0OR 0:23", 4, "#21 or #22 or #1 or #23"},
            {twoGroups, 0, "Above", " 1:1 1AND 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26", 0,
                "(#1 and #21 and #22 and #23) or (#24 and #25 and #26)"},
            {twoGroups, 0, "Below", " 1:21 1AND 1:1 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26", 2,
                "(#21 and #1 and #22 and #23) or (#24 and #25 and #26)"},
            {twoGroups, 4, "Above", " 1:21 1AND 1:22 1AND 1:1 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26", 4,
                "(#21 and #22 and #1 and #23) or (#24 and #25 and #26)"},
            {twoGroups, 4, "Below", " 1:21 1AND 1:22 1AND 1:23 1AND 1:1 0OR 1:24 1AND 1:25 1AND 1:26", 6,
                "(#21 and #22 and #23 and #1) or (#24 and #25 and #26)"},
            {twoGroups, 6, "Above", " 1:21 1AND 1:22 1AND 1:23 0OR 1:1 1AND 1:24 1AND 1:25 1AND 1:26", 6,
                "(#21 and #22 and #23) or (#1 and #24 and #25 and #26)"},
            {twoGroups, 6, "Below", " 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:1 1AND 1:25 1AND 1:26", 8,
                "(#21 and #22 and #23) or (#24 and #1 and #25 and #26)"},
            {twoGroups, 10, "Above", " 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:1 1AND 1:26", 10,
                "(#21 and #22 and #23) or (#24 and #25 and #1 and #26)"},
            {twoGroups, 10, "Below", " 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26 1AND 1:1", 12,
                "(#21 and #22 and #23) or (#24 and #25 and #26 and #1)"},
            {" 0:21 0AND 1:22 1OR 1:23 1OR 1:24 0AND 0:25", 2, "Above",
                " 0:21 0AND 1:1 1OR 1:22 1OR 1:23 1OR 1:24 0AND 0:25", 2, "#21 and (#1 or #22 or #23 or #24) and #25"},
            {" 0:21 0AND 1:22 1OR 1:23 1OR 1:24 0AND 0:25", 6, "Below",
                " 0:21 0AND 1:22 1OR 1:23 1OR 1:24 1OR 1:1 0AND 0:25", 8, "#21 and (#22 or #23 or #24 or #1) and #25"},
            {" 1:21 1OR 2:22 2AND 2:23 0AND 0:24", 2, "Above", " 1:21 1OR 2:1 2AND 2:22 2AND 2:23 0AND 0:24", 2,
                "(#21 or (#1 and #22 and #23)) and #24"},
            {" 1:21 1OR 2:22 2AND 2:23 0AND 0:24", 4, "Below", " 1:21 1OR 2:22 2AND 2:23 2AND 2:1 0AND 0:24", 6,
                "(#21 or (#22 and #23 and #1)) and #24"},
            {" 0:21", 0, "Above", " 0:1 0AND 0:21", 0, "#1 and #21"},
            {" 0:21", 0, "Below", " 0:21 0AND 0:1", 2, "#21 and #1"},
        };

        insertsAsTheCasesSay(cases);
    }

    private static void insertsAsTheCasesSay(final Object[][] cases) throws Exception
    {
        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 0:21"));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);

            for (Object[] c : cases)
            {
                javax.swing.SwingUtilities.invokeAndWait(() ->
                    frame.setConditionRowsForTest(fromLevels((String) c[0])));

                String did = "Insert Condition " + c[2] + " on line " + c[1] + " of" + c[0];

                rightClickAndChoose(table, (Integer) c[1], "route.ui.menuInsertCondition" + c[2]);

                assertEquals(levels(frame), c[3], did);
                assertEquals(selectedLine(table), c[4], did + ": the new condition is not the selected line");
                assertEquals(bold(frame), "[S88 #1]", did + ": Reads as " + frame.readsAsForTest());
                assertEquals(readsLike(frame), c[5], did + ": Reads as " + plainReading(frame));
                assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                    did + " left a red word: " + levels(frame));
            }
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * FR-150: Insert Condition on a joining word puts one new condition at the word's own level, joined by that word,
     * and selects it: on the "or" of "(21 and 22) or (24 and 25)", "(21 and 22) or new or (24 and 25)", Above or Below.
     *
     * MUTATION: in `offerConditionMenu`, enable the two inserts on a condition line only, and they are grayed here.
     */
    @Test
    public void testInsertOnAWordLineJoinsByThatWord() throws Exception
    {
        needsADisplay();

        final String twoGroups = " 1:21 1AND 1:22 0OR 1:24 1AND 1:25";
        final String orGroup = " 0:21 0AND 1:22 1OR 1:23";

        final Object[][] cases = {
            {twoGroups, 3, "Above", " 1:21 1AND 1:22 0OR 0:1 0OR 1:24 1AND 1:25", 4,
                "(#21 and #22) or #1 or (#24 and #25)"},
            {twoGroups, 3, "Below", " 1:21 1AND 1:22 0OR 0:1 0OR 1:24 1AND 1:25", 4,
                "(#21 and #22) or #1 or (#24 and #25)"},
            {twoGroups, 1, "Above", " 1:21 1AND 1:1 1AND 1:22 0OR 1:24 1AND 1:25", 2,
                "(#21 and #1 and #22) or (#24 and #25)"},
            {twoGroups, 1, "Below", " 1:21 1AND 1:1 1AND 1:22 0OR 1:24 1AND 1:25", 2,
                "(#21 and #1 and #22) or (#24 and #25)"},
            {twoGroups, 5, "Above", " 1:21 1AND 1:22 0OR 1:24 1AND 1:1 1AND 1:25", 6,
                "(#21 and #22) or (#24 and #1 and #25)"},
            {orGroup, 3, "Below", " 0:21 0AND 1:22 1OR 1:1 1OR 1:23", 4, "#21 and (#22 or #1 or #23)"},
            {orGroup, 1, "Above", " 0:21 0AND 0:1 0AND 1:22 1OR 1:23", 2, "#21 and #1 and (#22 or #23)"},
        };

        insertsAsTheCasesSay(cases);
    }

    /**
     * FR-151: after Insert Condition Above or Below, Add Condition, the plus, or an arrow, the keyboard is on the new
     * or moved line's address cell, so a digit starts editing it as after one click; the action itself opens no editor.
     *
     * MUTATION: in `ConditionTable.pick`, select the row alone with `setRowSelectionInterval`, and a digit edits
     * nothing.
     */
    @Test
    public void testANewOrMovedConditionIsReadyToTypeInto() throws Exception
    {
        needsADisplay();

        final String three = " 0:21 0AND 0:22 0AND 0:23";
        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(three));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);
            final javax.swing.JTextField elsewhere = support.Components.find(frame, javax.swing.JTextField.class,
                f -> f.isEditable() && f.isShowing());

            assertNotNull(elsewhere, "the route editor has no field to put the keyboard in");

            clickCell(table, 2, ADDRESS, 0);

            assertEquals(digitEdits(frame, table), "line 2 column 5",
                "precondition: one click on line 2's address and a digit do not edit it, so the fixture's keyboard"
                + " does not reach the table");

            // What is done, and the line that then holds the condition it made or moved
            final Object[][] cases = {
                {"Insert Condition Above on line 2", 2},
                {"Insert Condition Below on line 2", 4},
                {"Add Condition below the lines", 6},
                {"the plus", 6},
                {"the up arrow on line 4", 2},
                {"the down arrow on line 0", 2},
                {"Move Down on line 2", 4},
            };

            for (Object[] c : cases)
            {
                String did = (String) c[0];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    frame.setConditionRowsForTest(fromLevels(three));
                    elsewhere.requestFocusInWindow();
                });

                support.Components.settle();

                assertSame(support.Keys.focusOwnerOf(frame), elsewhere, "precondition: the keyboard is in a field");

                if (did.startsWith("Insert")) rightClickAndChoose(table, 2, "route.ui.menuInsertCondition"
                    + did.split(" ")[2]);
                else if (did.startsWith("Add")) rightClickAndChoose(table, 5, "route.ui.menuAddCondition");
                else if (did.equals("the plus")) clickCell(table, 5, UP_ARROW, 0);
                else if (did.startsWith("the up arrow")) clickCell(table, 4, UP_ARROW, 0);
                else if (did.startsWith("the down arrow")) clickCell(table, 0, DOWN_ARROW, 0);
                else rightClickAndChoose(table, 2, "route.ui.menuMoveDown");

                int line = (Integer) c[1];

                assertEquals(selectedLine(table), line, did + " did not select the line: " + levels(frame));
                assertFalse(support.Components.onTheEventThread(() -> table.isEditing()),
                    did + " opened an editor by itself");
                assertEquals(digitEdits(frame, table), "line " + line + " column " + ADDRESS,
                    "after " + did + " a digit does not edit the line's address: " + levels(frame));
            }
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * FR-151: a digit typed on a line's address replaces what the cell held, as in a spreadsheet; a double-click opens
     * the cell to change part of it.
     *
     * MUTATION: in `ConditionTable.editCellAt`, drop the `selectAll`, and the new condition becomes sensor 17.
     */
    @Test
    public void testTypingOnAnAddressReplacesIt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 0:21 0AND 0:22 0AND 0:23"));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);

            rightClickAndChoose(table, 2, "route.ui.menuInsertConditionBelow");

            asIfFocused(frame, owner -> typeADigit(owner, '7'));
            commit(table);

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:7 0AND 0:23",
                "7 typed on the new condition did not make it sensor 7");
            assertEquals(bold(frame), "[S88 #7]", "Reads as " + frame.readsAsForTest());

            clickCell(table, 0, ADDRESS, 0, 2);

            final java.awt.Component field = support.Components.onTheEventThread(() -> table.getEditorComponent());

            assertNotNull(field, "a double-click on line 0's address did not open it");

            typeADigit(field, '7');
            commit(table);

            assertEquals(levels(frame), " 0:217 0AND 0:22 0AND 0:7 0AND 0:23",
                "7 typed after a double-click on sensor 21 did not add to it");
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    private static void typeADigit(final java.awt.Component on, final char digit)
    {
        support.Components.onTheEventThread(() ->
        {
            long now = System.currentTimeMillis();

            on.dispatchEvent(new java.awt.event.KeyEvent(on, java.awt.event.KeyEvent.KEY_PRESSED, now, 0,
                java.awt.event.KeyEvent.VK_0 + digit - '0', digit));
            on.dispatchEvent(new java.awt.event.KeyEvent(on, java.awt.event.KeyEvent.KEY_TYPED, now, 0,
                java.awt.event.KeyEvent.VK_UNDEFINED, digit));
            on.dispatchEvent(new java.awt.event.KeyEvent(on, java.awt.event.KeyEvent.KEY_RELEASED, now + 1, 0,
                java.awt.event.KeyEvent.VK_0 + digit - '0', digit));

            return null;
        });

        support.Components.settle();
    }

    private static void commit(final javax.swing.JTable table)
    {
        support.Components.onTheEventThread(() -> table.isEditing() && table.getCellEditor().stopCellEditing());
    }

    /** Presses 7 where the window sends the keyboard and says which cell it began editing, then cancels the edit. */
    private static String digitEdits(final org.traincontrol.gui.RouteEditorFrame frame, final javax.swing.JTable table)
    {
        asIfFocused(frame, owner -> support.Keys.press(owner, java.awt.event.KeyEvent.VK_7));

        return support.Components.onTheEventThread(() ->
        {
            String out = table.isEditing()
                ? "line " + table.getEditingRow() + " column " + table.getEditingColumn() : "nothing";

            if (table.isEditing()) table.getCellEditor().cancelCellEditing();

            return out;
        });
    }

    /**
     * Keys go where the window sends the keyboard, which answers as the focus owner (test windows are rarely focused).
     */
    private static void asIfFocused(final org.traincontrol.gui.RouteEditorFrame frame,
        final java.util.function.Consumer<java.awt.Component> keys)
    {
        final java.awt.Component owner = support.Keys.focusOwnerOf(frame);
        java.awt.KeyboardFocusManager real = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();

        java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(new java.awt.DefaultKeyboardFocusManager()
        {
            @Override
            public java.awt.Component getFocusOwner()
            {
                return owner;
            }
        });

        try
        {
            keys.accept(owner);
        }
        finally
        {
            java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(real);
        }
    }

    /**
     * FR-152: a taller window gives its extra height to the conditions; the commands keep the height they have packed.
     *
     * MUTATION: in `build`, lay the middle panel out with a plain `new GridLayout(2, 1, 0, 8)`, and the commands take
     * half of the extra height.
     */
    @Test
    public void testATallerWindowGivesTheConditionsTheRoom() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.pack();
                frame.setVisible(true);
            });

            support.Components.settle();

            final javax.swing.JTable conditions = conditionTable(frame);
            final javax.swing.JTable commands = support.Components.find(frame, javax.swing.JTable.class,
                t -> "CommandTable".equals(t.getClass().getSimpleName()));

            assertNotNull(commands, "the route editor has no command table");

            final int[] packed = support.Components.onTheEventThread(() -> new int[]{frame.getHeight(),
                commands.getParent().getHeight(), conditions.getParent().getHeight(),
                section(commands).getHeight(), section(conditions).getHeight()});

            assertEquals(packed[3], packed[4], "at the packed size the two sections are not as tall as each other");

            // As tall as the screen allows, as maximizing does: Windows keeps a window within the screen's height.
            final int room = support.Components.onTheEventThread(() ->
                frame.getGraphicsConfiguration().getBounds().height - frame.getHeight());

            assertTrue(room >= 150, "precondition: the screen has no room for a taller window: " + room);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setSize(frame.getWidth(), frame.getHeight() + room);
                frame.validate();
            });

            support.Components.settle();

            final int[] tall = support.Components.onTheEventThread(() -> new int[]{frame.getHeight(),
                commands.getParent().getHeight(), conditions.getParent().getHeight()});

            int grown = tall[0] - packed[0];

            assertTrue(grown >= 150, "precondition: the window did not grow: " + grown);
            assertTrue(tall[2] - packed[2] >= grown * 9 / 10, "the window grew by " + grown
                + " pixels and the conditions by only " + (tall[2] - packed[2]));
            assertTrue(tall[1] - packed[1] <= grown / 20, "the window grew by " + grown + " pixels and the commands by "
                + (tall[1] - packed[1]));
        }
        finally
        {
            close(frame);
        }
    }

    private static java.awt.Container section(javax.swing.JTable table)
    {
        return javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, table).getParent();
    }

    /**
     * FR-153: the lines an edit changed are drawn pale, fading back to white within about a second.  Move Down on the
     * first of "21 and 22 and 23" lights the two conditions that traded places, never the 23.
     *
     * MUTATION: in `ConditionTable.shift`, drop the `showChanges` call, and the moved line is white at once.
     */
    @Test
    public void testAnEditShowsWhichLinesItChanged() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 0:21 0AND 0:22 0AND 0:23"));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);

            rightClickAndChoose(table, 0, "route.ui.menuMoveDown");

            assertEquals(levels(frame), " 0:22 0AND 0:21 0AND 0:23", "precondition: Move Down on 21 swaps it with 22");

            // A selected line wears the selection's color, so the selection is cleared to see the line's own.
            javax.swing.SwingUtilities.invokeAndWait(table::clearSelection);

            assertNotEquals(background(table, 2), java.awt.Color.WHITE,
                "just after Move Down the moved line is drawn as every other line is");

            long until = System.currentTimeMillis() + 1500;

            // The fade's own time is what is tested, so the claim waits it out, looking at the untouched line meanwhile
            while (System.currentTimeMillis() < until)
            {
                assertEquals(background(table, 4), java.awt.Color.WHITE, "the line Move Down left alone is lit");

                Thread.sleep(100);
            }

            assertEquals(background(table, 2), java.awt.Color.WHITE,
                "1.5 seconds after Move Down the moved line has not faded back to white");
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    private static java.awt.Color background(final javax.swing.JTable table, final int line)
    {
        return support.Components.onTheEventThread(() ->
            table.prepareRenderer(table.getCellRenderer(line, 4), line, 4).getBackground());
    }

    /**
     * FR-150: a right-click on a joining word offers both inserts; FR-147: a right-click inside a selection of several
     * lines keeps it, so the menu's Indent acts on them all and Insert Below selects the new line alone; outside the
     * selection it picks the line clicked.
     *
     * MUTATION: in `offerConditionMenu`, select the line clicked whether or not it is already selected, and the
     * selection of three is lost.
     */
    @Test
    public void testInsertFromAWordLineOrASelection() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(fromLevels(" 1:21 1AND 1:22 0OR 1:24 1AND 1:25"));
                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = conditionTable(frame);

            for (int word : new int[]{1, 3})
            {
                javax.swing.JPopupMenu menu = rightClickCell(table, word, NOTHING_TO_EDIT);

                assertNotNull(menu, "a right-click on the word on line " + word + " opened no menu");
                assertTrue(item(menu, "route.ui.menuInsertConditionAbove").isEnabled()
                    && item(menu, "route.ui.menuInsertConditionBelow").isEnabled(),
                    "a word line does not offer both inserts: " + offered(menu));

                support.Mouse.closeMenus();
            }

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(
                fromLevels(" 0:21 0AND 0:22 0AND 0:23 0AND 0:24")));

            selectLines(table, 2, 4);

            javax.swing.JPopupMenu menu = rightClickCell(table, 4, NOTHING_TO_EDIT);

            assertNotNull(menu, "a right-click inside the selection opened no menu");
            assertEquals(java.util.Arrays.toString(support.Components.onTheEventThread(() -> table.getSelectedRows())),
                "[2, 3, 4]", "a right-click inside a selection of several lines changed it");

            support.Mouse.choose(item(menu, "route.ui.menuInsertConditionBelow"));

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23 0AND 0:1 0AND 0:24",
                "Insert Condition Below from inside the selection puts one under the line clicked");
            assertEquals(java.util.Arrays.toString(support.Components.onTheEventThread(() -> table.getSelectedRows())),
                "[6]", "the new condition alone is selected");

            selectLines(table, 2, 4);

            rightClickAndChoose(table, 0, "route.ui.menuInsertConditionAbove");

            assertEquals(levels(frame), " 0:1 0AND 0:21 0AND 0:22 0AND 0:23 0AND 0:1 0AND 0:24",
                "Insert Condition Above on a line outside the selection puts one above that line");
            assertEquals(selectedLine(table), 0, "and selects it");
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * FR-147: an inserted condition is unsaved work: Cancel asks before closing, and the saved route keeps its
     * conditions.
     *
     * MUTATION: in `stateSignature`, leave out the condition rows, and Cancel closes without asking.
     */
    @Test
    public void testAnInsertedConditionIsTypedIntoAndCancelKeepsTheSavedRoute() throws Exception
    {
        needsADisplay();

        final org.traincontrol.marklin.MarklinRoute route = new org.traincontrol.marklin.MarklinRoute(null, "Saved",
            4243, new java.util.ArrayList<>(java.util.Collections.singletonList(
                RouteCommand.RouteCommandAccessory(80, Accessory.accessoryDecoderType.MM2, true))), 0,
            org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, true,
            org.traincontrol.base.ConditionOutline.toExpression(fromLevels(" 1:21 1AND 1:22 0OR 0:23")));

        final org.traincontrol.gui.RouteEditorFrame[] opened = new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            opened[0] = new org.traincontrol.gui.RouteEditorFrame(null, route.getName(), route);
            opened[0].pack();
            opened[0].setVisible(true);
        });

        final org.traincontrol.gui.RouteEditorFrame frame = opened[0];

        try
        {
            final javax.swing.JTable table = conditionTable(frame);

            assertEquals(levels(frame), " 1:21 1AND 1:22 0OR 0:23", "precondition: the saved route's conditions");
            assertFalse(frame.hasUnsavedWork(), "precondition: the route opens with nothing unsaved");

            rightClickAndChoose(table, 0, "route.ui.menuInsertConditionBelow");

            clickCell(table, 2, 5, 0, 2);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                if (table.getEditorComponent() instanceof javax.swing.JTextField)
                {
                    ((javax.swing.JTextField) table.getEditorComponent()).setText("7");
                    table.getCellEditor().stopCellEditing();
                }
            });

            assertEquals(levels(frame), " 1:21 1AND 1:7 1AND 1:22 0OR 0:23",
                "a double-click on the new condition's address and typing 7 did not make it sensor 7");
            assertEquals(bold(frame), "[S88 #7]", "Reads as " + frame.readsAsForTest());

            final javax.swing.JButton cancel = support.Components.find(frame, javax.swing.JButton.class,
                b -> I18n.t("route.ui.frameCancel").equals(b.getText()));

            assertNotNull(cancel, "the route editor has no Cancel button");

            try (support.Answering answering = support.Answering.start(asked -> support.Answering.pressAt(0)))
            {
                javax.swing.SwingUtilities.invokeLater(() -> cancel.doClick(0));

                assertTrue(answering.awaitAsked(1, 10000), "Cancel after an insert closed without asking");

                support.Components.settle();
            }

            assertFalse(support.Components.onTheEventThread(() -> frame.isDisplayable()),
                "answering Yes to Cancel's question left the window open");
            assertEquals(levelsOf(org.traincontrol.base.ConditionOutline.of(route.getConditions())),
                " 1:21 1AND 1:22 0OR 0:23", "Cancel changed the saved route's conditions");
        }
        finally
        {
            close(frame);
        }
    }

    /** "Reads as" cut to brackets, sensors and joining words, in English: "(#21 and #1) or #23". */
    private static String readsLike(org.traincontrol.gui.RouteEditorFrame frame)
    {
        String and = I18n.t("route.ui.joinAnd");
        String or = I18n.t("route.ui.joinOr");
        String reading = plainReading(frame);

        reading = reading.substring(reading.indexOf("S88 #") < 0 ? reading.length()
            : Math.max(0, reading.lastIndexOf(':', reading.indexOf("S88 #")) + 1));

        java.util.regex.Matcher part = java.util.regex.Pattern.compile("\\(|\\)|#\\d+|(?<![\\w#])("
            + java.util.regex.Pattern.quote(and) + "|" + java.util.regex.Pattern.quote(or) + ")(?!\\w)")
            .matcher(reading);

        StringBuilder out = new StringBuilder();

        while (part.find())
        {
            String token = part.group().equals(and) ? "and" : part.group().equals(or) ? "or" : part.group();

            boolean tight = token.equals(")") || out.length() == 0 || out.charAt(out.length() - 1) == '(';

            out.append(tight ? "" : " ").append(token);
        }

        return out.toString();
    }

    /** A right-click on a line where no press edits anything, and the menu item chosen; it must be enabled. */
    private static void rightClickAndChoose(javax.swing.JTable table, int line, String key)
    {
        javax.swing.JPopupMenu menu = rightClickCell(table, line, NOTHING_TO_EDIT);

        assertNotNull(menu, "a right-click on line " + line + " opened no menu");

        javax.swing.JMenuItem chosen = item(menu, key);

        assertTrue(chosen.isEnabled(), key + " is grayed on line " + line + ": " + offered(menu));

        support.Mouse.choose(chosen);
    }

    private static String plainReading(org.traincontrol.gui.RouteEditorFrame frame)
    {
        return support.Components.onTheEventThread(() -> frame.readsAsForTest()).replaceAll("<[^>]*>", "")
            .replaceAll("\\s+", " ").trim();
    }

    private static javax.swing.JTable conditionTable(org.traincontrol.gui.RouteEditorFrame frame)
    {
        javax.swing.JTable table = support.Components.find(frame, javax.swing.JTable.class,
            t -> "ConditionTable".equals(t.getClass().getSimpleName()));

        assertNotNull(table, "the route editor has no condition table");

        return table;
    }

    private static int selectedLine(final javax.swing.JTable table)
    {
        return support.Components.onTheEventThread(() -> table.getSelectedRow());
    }

    private static javax.swing.JPopupMenu rightClickCell(final javax.swing.JTable table, int line, int column)
    {
        java.awt.Rectangle cell = support.Components.onTheEventThread(() -> table.getCellRect(line, column, true));

        return support.Mouse.rightClickAt(table, new java.awt.Point(cell.x + cell.width / 2,
            cell.y + cell.height / 2), 5000);
    }

    /** The menu's items as their message keys, each marked + when enabled and - when not. */
    private static String offered(final javax.swing.JPopupMenu menu)
    {
        String[] keys = {"route.ui.menuInsertConditionAbove", "route.ui.menuInsertConditionBelow",
            "route.ui.menuAddCondition", "route.ui.menuMoveUp", "route.ui.menuMoveDown", "route.ui.menuIndent",
            "route.ui.menuOutdent", "ui.delete"};

        StringBuilder out = new StringBuilder();

        for (java.awt.Component part : menu.getComponents())
        {
            if (!(part instanceof javax.swing.JMenuItem)) continue;

            javax.swing.JMenuItem item = (javax.swing.JMenuItem) part;
            String named = item.getText();

            for (String key : keys)
            {
                if (I18n.t(key).equals(item.getText())) named = key;
            }

            out.append(out.length() == 0 ? "" : " ").append(item.isEnabled() ? "+" : "-").append(named);
        }

        return out.toString();
    }

    private static javax.swing.JMenuItem item(javax.swing.JPopupMenu menu, String key)
    {
        for (java.awt.Component part : menu.getComponents())
        {
            if (part instanceof javax.swing.JMenuItem && I18n.t(key).equals(((javax.swing.JMenuItem) part).getText()))
            {
                return (javax.swing.JMenuItem) part;
            }
        }

        throw new AssertionError("the menu has no " + key + ": " + offered(menu));
    }

    /** The bold parts of "Reads as" other than the joining words, each cut to its sensor: "[S88 #23]". */
    private static String bold(org.traincontrol.gui.RouteEditorFrame frame)
    {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.regex.Matcher bold = java.util.regex.Pattern.compile("<b>(.*?)</b>").matcher(
            support.Components.onTheEventThread(() -> frame.readsAsForTest()));

        while (bold.find())
        {
            String text = bold.group(1).replaceAll("<[^>]*>", "").trim();

            if (text.equals(I18n.t("route.ui.joinAnd")) || text.equals(I18n.t("route.ui.joinOr"))) continue;

            java.util.regex.Matcher sensor = java.util.regex.Pattern.compile("S88 #\\d+").matcher(text);

            out.add(sensor.find() ? sensor.group() : text);
        }

        return out.toString();
    }

    /** An outline written as `levels` prints one: " 1:21 1AND 0:4", each condition on a sensor that is on. */
    private static java.util.List<org.traincontrol.base.ConditionOutline.Row> fromLevels(String levels)
    {
        java.util.List<org.traincontrol.base.ConditionOutline.Row> out = new java.util.ArrayList<>();

        for (String part : levels.trim().split(" "))
        {
            int depth = part.charAt(0) - '0';

            out.add(part.charAt(1) == ':'
                ? org.traincontrol.base.ConditionOutline.Row.condition(depth, feedback(Integer.parseInt(part.substring(2))))
                : org.traincontrol.base.ConditionOutline.Row.joining(depth,
                    org.traincontrol.base.ConditionOutline.Joiner.valueOf(part.substring(1))));
        }

        return out;
    }

    /**
     * OB-367: a setting is shown in the user's language, in the cell and in "Reads as", and stored as the word the
     * route file holds.
     *
     * MUTATION: in `greyWhatCannotBeEdited`, drop the `settingLabel` line, and the cells read "off" and "turn".
     */
    @Test
    public void testTheSettingsAreShownInTheUsersLanguage() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();
        final java.util.Locale was = org.traincontrol.util.I18n.getLocale();

        try
        {
            final String[] cells = new String[3];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.util.I18n.setLocale(java.util.Locale.GERMAN);

                if (frame.commandRowCountForTest() == 0) frame.addCommandRowForTest();

                frame.setCommandKindForTest(0, CommandRow.Kind.ACCESSORY);
                frame.setCommandTargetForTest(0, "12");
                frame.setCommandSettingForTest(0, "turn");
                frame.setAutoFireForTest(true);

                java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

                rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                    org.traincontrol.base.RouteCommand.RouteCommandFeedback(5, false)));
                rows.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                    org.traincontrol.base.ConditionOutline.Joiner.OR));
                rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                    org.traincontrol.base.RouteCommand.RouteCommandFeedback(6, true)));

                frame.setConditionRowsForTest(rows);

                cells[0] = frame.conditionCellForTest(0, 6).getText();
                cells[1] = ((javax.swing.JLabel) frame.commandCellForTest(0, 6)).getText();
                cells[2] = frame.readsAsForTest();
            });

            assertEquals(cells[0], "aus", "the condition's setting cell in German");
            assertEquals(cells[1], "abzweigend", "the command's setting cell in German");
            assertTrue(cells[2].contains(">aus<") && cells[2].contains(">ein<"), "Reads as, in German: " + cells[2]);
            assertEquals(shown(frame, 0), "FEEDBACK 5 off", "stored as before");
            assertEquals(frame.commandRowForTest(0).getSetting(), "turn", "stored as before");
        }
        finally
        {
            org.traincontrol.util.I18n.setLocale(was);

            close(frame);
        }
    }

    /**
     * OB-367: a command's setting dropdown offers the words in the user's language and still holds the stored words.
     *
     * MUTATION: in `chooseSetting`, drop the `setRenderer` call, and the dropdown offers "straight" and "turn".
     */
    @Test
    public void testTheSettingDropdownSpeaksTheUsersLanguage() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();
        final java.util.Locale was = org.traincontrol.util.I18n.getLocale();

        try
        {
            final java.util.List<String> offered = new java.util.ArrayList<>();
            final java.util.List<Object> held = new java.util.ArrayList<>();

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.util.I18n.setLocale(java.util.Locale.GERMAN);

                if (frame.commandRowCountForTest() == 0) frame.addCommandRowForTest();

                frame.setCommandKindForTest(0, CommandRow.Kind.ACCESSORY);
                frame.setCommandTargetForTest(0, "12");
                frame.setCommandSettingForTest(0, "turn");

                frame.pack();
                frame.setVisible(true);
            });

            final javax.swing.JTable table = support.Components.find(frame, javax.swing.JTable.class,
                t -> "CommandTable".equals(t.getClass().getSimpleName()));

            assertNotNull(table, "the route editor has no command table");

            clickCell(table, 0, 6, 0);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                if (!(table.getEditorComponent() instanceof javax.swing.JComboBox)) return;

                javax.swing.JComboBox<?> box = (javax.swing.JComboBox<?>) table.getEditorComponent();
                javax.swing.JList<Object> list = new javax.swing.JList<>();

                @SuppressWarnings("unchecked")
                javax.swing.ListCellRenderer<Object> renderer = (javax.swing.ListCellRenderer<Object>) box.getRenderer();

                for (int i = 0; i < box.getItemCount(); i++)
                {
                    held.add(box.getItemAt(i));

                    offered.add(((javax.swing.JLabel) renderer.getListCellRendererComponent(list, box.getItemAt(i), i,
                        false, false)).getText());
                }

                table.getCellEditor().cancelCellEditing();
            });

            assertEquals(held, java.util.Arrays.asList("straight", "turn"), "a click on the setting cell opened no"
                + " dropdown of the stored words");
            assertEquals(offered, java.util.Arrays.asList("gerade", "abzweigend"), "the dropdown in German");
        }
        finally
        {
            org.traincontrol.util.I18n.setLocale(was);

            close(frame);
        }
    }

    /**
     * OB-367: a direction condition reads in the user's language in "Reads as".
     *
     * MUTATION: in `shortly`, give the direction its stored word instead of `settingLabel`'s, and Reads as says
     * "backward".
     */
    @Test
    public void testADirectionReadsInTheUsersLanguage() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();
        final java.util.Locale was = org.traincontrol.util.I18n.getLocale();

        try
        {
            final String[] readsAs = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.util.I18n.setLocale(java.util.Locale.GERMAN);

                java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

                rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                    org.traincontrol.base.RouteCommand.RouteCommandFeedback(5, true)));
                rows.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                    org.traincontrol.base.ConditionOutline.Joiner.AND));
                rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                    org.traincontrol.base.RouteCommand.RouteCommandLocomotiveDirection("Example train",
                        org.traincontrol.base.Locomotive.locDirection.DIR_BACKWARD)));

                frame.setConditionRowsForTest(rows);

                readsAs[0] = frame.readsAsForTest();
            });

            assertTrue(readsAs[0].contains("Example train r\u00fcckw\u00e4rts"), "Reads as, in German: " + readsAs[0]);
        }
        finally
        {
            org.traincontrol.util.I18n.setLocale(was);

            close(frame);
        }
    }

    /** The condition table's columns a press acts on, and one no condition on a sensor can edit (the protocol). */
    private static final int UP_ARROW = 0;
    private static final int DOWN_ARROW = 1;
    private static final int INDENT_MARK = 2;
    private static final int OUTDENT_MARK = 3;
    private static final int NOTHING_TO_EDIT = 7;

    private static final int ADDRESS = 5;

    /** Clicks the first line and Shift-clicks the last, where a press neither edits nor moves anything. */
    private static void selectLines(javax.swing.JTable table, int first, int last) throws Exception
    {
        clickCell(table, first, NOTHING_TO_EDIT, 0);
        clickCell(table, last, NOTHING_TO_EDIT, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
    }

    /** A press, release and click in one cell's middle, as the mouse sends them. */
    private static void clickCell(final javax.swing.JTable table, final int line, final int column, final int modifiers)
        throws Exception
    {
        clickCell(table, line, column, modifiers, 1);
    }

    /** The same, as a click of `count` presses: 2 is a double-click. */
    private static void clickCell(final javax.swing.JTable table, final int line, final int column, final int modifiers,
        final int count) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            java.awt.Rectangle cell = table.getCellRect(line, column, true);

            int x = cell.x + cell.width / 2;
            int y = cell.y + cell.height / 2;
            long now = System.currentTimeMillis();

            table.dispatchEvent(new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_PRESSED, now,
                modifiers | java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, count, false,
                java.awt.event.MouseEvent.BUTTON1));
            table.dispatchEvent(new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_RELEASED, now + 1,
                modifiers, x, y, count, false, java.awt.event.MouseEvent.BUTTON1));
            table.dispatchEvent(new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_CLICKED, now + 1,
                modifiers, x, y, count, false, java.awt.event.MouseEvent.BUTTON1));
        });
    }

    /** Each line as depth and sensor, or depth and word: " 1:1 1OR 0:4". */
    private static String levels(org.traincontrol.gui.RouteEditorFrame frame)
    {
        return levelsOf(frame.conditionRowsForTest());
    }

    private static String levelsOf(java.util.List<org.traincontrol.base.ConditionOutline.Row> rows)
    {
        StringBuilder out = new StringBuilder();

        for (org.traincontrol.base.ConditionOutline.Row row : rows)
        {
            out.append(" ").append(row.getDepth()).append(row.isJoiner() ? String.valueOf(row.getJoiner())
                : ":" + CommandRow.of(row.getCommand()).getTarget());
        }

        return out.toString();
    }

    /** A condition line's address and setting as stored, read the way the table reads them. */
    private static String shown(org.traincontrol.gui.RouteEditorFrame frame, int line)
    {
        CommandRow row = CommandRow.of(frame.conditionRowsForTest().get(line).getCommand());

        return row.getKind() + " " + row.getTarget() + " " + row.getSetting();
    }

    /** Conditions on sensors joined by the given words, every line at the outermost level: what the plus types. */
    private static java.util.List<org.traincontrol.base.ConditionOutline.Row> flat(Object... parts)
    {
        java.util.List<org.traincontrol.base.ConditionOutline.Row> out = new java.util.ArrayList<>();

        for (Object part : parts)
        {
            if (part instanceof Integer)
            {
                out.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback((Integer) part)));
            }
            else
            {
                out.add(org.traincontrol.base.ConditionOutline.Row.joining(0,
                    org.traincontrol.base.ConditionOutline.Joiner.valueOf((String) part)));
            }
        }

        return out;
    }

    private static String depths(org.traincontrol.gui.RouteEditorFrame frame)
    {
        StringBuilder out = new StringBuilder();

        for (org.traincontrol.base.ConditionOutline.Row row : frame.conditionRowsForTest())
        {
            out.append(" ").append(row.getDepth())
               .append(row.isJoiner() ? String.valueOf(row.getJoiner()) : "?");
        }

        return out.toString();
    }

    /**
     * The SHAPE of the expression the outline means - "And(Or(x,x),x)"; the top node's class alone cannot tell `(A or
     * B) and C` from its defect.
     */
    private static String reads(org.traincontrol.gui.RouteEditorFrame frame)
    {
        return shape(org.traincontrol.base.ConditionOutline.toExpression(
            frame.conditionRowsForTest()));
    }

    /** One expression as a bracketed shape, so two trees can be compared by reading. */
    private static String shape(org.traincontrol.base.NodeExpression node)
    {
        if (node == null) return "(nothing)";

        if (node instanceof org.traincontrol.base.NodeAnd)
        {
            return "And(" + shape(((org.traincontrol.base.NodeAnd) node).getLeft()) + ","
                + shape(((org.traincontrol.base.NodeAnd) node).getRight()) + ")";
        }

        if (node instanceof org.traincontrol.base.NodeOr)
        {
            return "Or(" + shape(((org.traincontrol.base.NodeOr) node).getLeft()) + ","
                + shape(((org.traincontrol.base.NodeOr) node).getRight()) + ")";
        }

        if (node instanceof org.traincontrol.base.NodeGroup)
        {
            StringBuilder out = new StringBuilder("Group(");

            for (org.traincontrol.base.NodeExpression inside
                : ((org.traincontrol.base.NodeGroup) node).getExpressions())
            {
                out.append(shape(inside));
            }

            return out.append(")").toString();
        }

        return "x";
    }

    /**
     * The two train-at-a-sensor kinds say they are autonomy's, and the reading says which way round each is (FR-141);
     * Adam: Prefix with "Autonomy: " if so.  The labels are read from the English bundle.
     *
     * MUTATION: read a not-at command as AUTO_LOCOMOTIVE in `CommandRow.of` and the reading says "at" for both lines.
     */
    @Test
    public void testTheTrainAtASensorKindsSayAutonomyAndWhichWayRound() throws Exception
    {
        java.util.Properties english = new java.util.Properties();

        try (java.io.InputStream in = new java.io.FileInputStream("src/org/traincontrol/resources/messages.properties"))
        {
            english.load(in);
        }

        assertEquals(english.getProperty("route.kind." + CommandRow.Kind.AUTO_LOCOMOTIVE.name()),
            "Autonomy: Train At A Sensor", "the at kind does not say it is autonomy's");

        assertEquals(english.getProperty("route.kind." + CommandRow.Kind.AUTO_LOCOMOTIVE_NOT_AT.name()),
            "Autonomy: Train Not At A Sensor", "the not-at kind does not say it is autonomy's, or not which way round");

        assertEquals(english.getProperty("route.reads.trainNotAt"), "{0} not at S88 #{1}",
            "the reading of a not-at line does not say not");

        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAutoLocomotive("BR 218", 21, true)));
            rows.add(org.traincontrol.base.ConditionOutline.Row.joining(0, org.traincontrol.base.ConditionOutline.Joiner.AND));
            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAutoLocomotive("BR 212", 22, false)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));

            final String[] text = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> text[0] = frame.readsAsForTest());

            assertTrue(text[0].contains(I18n.f("route.reads.trainAt", "BR 218", "21")),
                "\"Reads as\" does not say the first train is at its sensor: " + text[0]);

            assertTrue(text[0].contains(I18n.f("route.reads.trainNotAt", "BR 212", "22")),
                "\"Reads as\" does not say the second train is NOT at its sensor: " + text[0]);

            assertFalse(text[0].contains(I18n.f("route.reads.trainAt", "BR 212", "22")),
                "\"Reads as\" says the not-at train is at its sensor: " + text[0]);

            final String[] kind = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> kind[0] = frame.conditionCellForTest(2).getText());

            assertEquals(kind[0], CommandRow.labelFor(CommandRow.Kind.AUTO_LOCOMOTIVE_NOT_AT),
                "the not-at line's Kind cell does not show the not-at kind");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * Choosing Not At on an At line, or the other way, keeps its train and its sensor (FR-141): one condition asked
     * either way round, as Switch and Signal keep their address.
     *
     * MUTATION: drop the at-or-not-at arm in the condition table's `setValueAt` and the kind does not change.
     */
    @Test
    public void testSwitchingBetweenAtAndNotAtKeepsTheTrainAndTheSensor() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAutoLocomotive("BR 218", 21, true)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));

            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame.setConditionKindForTest(0, CommandRow.Kind.AUTO_LOCOMOTIVE_NOT_AT));

            assertEquals(frame.conditionRowsForTest().get(0).getCommand(),
                RouteCommand.RouteCommandAutoLocomotive("BR 218", 21, false),
                "choosing Not At did not make the same train and sensor a not-at condition");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame.setConditionKindForTest(0, CommandRow.Kind.AUTO_LOCOMOTIVE));

            assertEquals(frame.conditionRowsForTest().get(0).getCommand(),
                RouteCommand.RouteCommandAutoLocomotive("BR 218", 21, true),
                "choosing At again did not turn it back");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * A train-at-a-sensor line's setting says it is the sensor's address (FR-141): the cell paints it as one with a
     * tooltip, on both kinds; a sensor line's own setting is left as it was.
     *
     * MUTATION: have `ConditionTable.shownAs` hand back the value as it is and the first assertion fails.
     */
    @Test
    public void testATrainAtASensorsSettingSaysItIsTheSensorAddress() throws Exception
    {
        java.util.Properties english = new java.util.Properties();

        try (java.io.InputStream in = new java.io.FileInputStream("src/org/traincontrol/resources/messages.properties"))
        {
            english.load(in);
        }

        assertEquals(english.getProperty("route.ui.frameSensorAddress"), "Sensor address: {0}",
            "the setting of a train-at-a-sensor line does not say it is the sensor address");

        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAutoLocomotive("BR 218", 21, true)));
            rows.add(org.traincontrol.base.ConditionOutline.Row.joining(0, org.traincontrol.base.ConditionOutline.Joiner.AND));
            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0,
                RouteCommand.RouteCommandAutoLocomotive("BR 212", 1022, false)));
            rows.add(org.traincontrol.base.ConditionOutline.Row.joining(0, org.traincontrol.base.ConditionOutline.Joiner.AND));
            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0, feedback(5)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));

            final String[][] cell = new String[3][2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                for (int at = 0; at < 3; at++)
                {
                    javax.swing.JLabel painted = frame.conditionCellForTest(at * 2, 6);

                    cell[at][0] = painted.getText();
                    cell[at][1] = painted.getToolTipText();
                }
            });

            assertEquals(cell[0][0], I18n.f("route.ui.frameSensorAddress", "21"),
                "the at line's setting is a bare number, not a sensor address");

            assertEquals(cell[0][1], I18n.t("route.ui.frameSensorAddressTip"),
                "the at line's setting has no tooltip saying what it is");

            assertEquals(cell[1][0], I18n.f("route.ui.frameSensorAddress", "1022"),
                "the not-at line's setting is a bare number, not a sensor address");

            assertEquals(cell[1][1], I18n.t("route.ui.frameSensorAddressTip"),
                "the not-at line's setting has no tooltip saying what it is");

            assertEquals(cell[2][0], "on", "a sensor line's own setting changed");

            assertNull(cell[2][1], "a sensor line's setting wears the train line's tooltip");

            assertEquals(frame.conditionRowsForTest().get(0).getCommand().getAddress(), 21,
                "painting the address changed the condition");
        }
        finally
        {
            close(frame);
        }
    }

    /** A feedback condition on one sensor. */
    private static org.traincontrol.base.RouteCommand feedback(int address)
    {
        return org.traincontrol.base.RouteCommand.RouteCommandFeedback(address, true);
    }
}
