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
     * pressing indent once; 4 then moves up past the whole group to "4 and (1 or 2 or 3)" and back, every line keeping
     * its level; and outdenting the selection undoes the indent.
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

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.indentConditionsForTest(0, 4, 1));

            assertEquals(levels(frame), " 1:1 1OR 1:2 1OR 1:3 0AND 0:4",
                "the three selected conditions and the words between them go one level in, the 'and' stays");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "(1 or 2 or 3) and 4 is flagged: " + levels(frame));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.moveConditionForTest(6, -1));

            assertEquals(levels(frame), " 0:4 0AND 1:1 1OR 1:2 1OR 1:3",
                "4 moved up past the whole group, with the 'and' between them and every level kept");

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.moveConditionForTest(0, 1));

            assertEquals(levels(frame), " 1:1 1OR 1:2 1OR 1:3 0AND 0:4", "and moved back down past it");

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.indentConditionsForTest(0, 4, -1));

            assertEquals(levels(frame), " 0:1 0OR 0:2 0OR 0:3 0AND 0:4", "outdenting the selection undoes it");
        }
        finally
        {
            close(frame);
        }
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

    /** Each line as depth and sensor, or depth and word: " 1:1 1OR 0:4". */
    private static String levels(org.traincontrol.gui.RouteEditorFrame frame)
    {
        StringBuilder out = new StringBuilder();

        for (org.traincontrol.base.ConditionOutline.Row row : frame.conditionRowsForTest())
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
