package ui;

import java.awt.GraphicsEnvironment;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.ThreeWaySwitch;

/**
 * A row that looks right and means nothing.
 *
 * Adam turned an accessory row into a locomotive command and got a command for a locomotive called
 * "3".  Nothing refused it: the address had simply stayed behind in the column that had become the
 * name column, the row built, the route saved, and it did nothing whatever when it ran.  A route
 * that quietly does nothing is the worst thing this editor can produce, because there is no error
 * anywhere to lead anybody back to it.
 *
 * Two things had to change and both are tested here.  Changing the kind clears the row, because the
 * two kinds' columns hold different sorts of thing and only both happen to accept text; and Save
 * checks each row against the layout, because "there is no locomotive called 3" is a question only
 * the layout can answer.
 */
public class testRouteEditorValidation
{
    /**
     * A manual route saves with the sensor field left blank (OB-178).
     *
     * Adam: *"if a route's auto-fire checkbox is unchecked, and the s88 field is blank, the save will
     * still fail asking the user to input an integer.  just treat this as 0."*
     *
     * **Blank is the answer a manual route gives.** The field only means anything with auto-fire on,
     * which has its own rule; without it the route is fired by hand and has no sensor. Refusing the
     * empty field made a perfectly good route unsaveable, and the message asked for an integer as
     * though something wrong had been typed.
     *
     * **And the two halves parsed it separately**, which is the other half of the defect: relaxing the
     * check without the save would have turned a refusal into an exception. One reader now.
     *
     * **What is NOT asserted, and why.** The field is digits-only, so letters cannot be typed into it
     * and `enteredS88`'s -1 branch is unreachable from the UI. It is kept as a defence rather than
     * removed - the field could gain another door - but a test for it would be a check that cannot
     * fail, so this says so instead of pretending.
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

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            editor.setAutoFireForTest(false);
            editor.setS88TextForTest("");

            blank.addAll(editor.problemsForTest());

            // THE OTHER HALF, which must keep refusing: a route that fires ITSELF has nothing to fire
            // it without a sensor, and would sit in the list marked automatic doing nothing.
            editor.setAutoFireForTest(true);

            automatic.addAll(editor.problemsForTest());

            // A SPACE, not letters.  The field is digits-only (`digitsOnlyField`), so "east" cannot
            // be typed into it at all - asserting that it is refused would be testing a state the UI
            // cannot reach, which is a check that can never fail.  A field holding only whitespace is
            // reachable and must read as none, like an empty one.
            editor.setAutoFireForTest(false);
            editor.setS88TextForTest("   ");

            rubbish.addAll(editor.problemsForTest());
        });

        assertFalse(named(blank, "S88"),
            "a manual route with the sensor field left blank was refused as not-a-number.  Blank is "
            + "the answer a manual route gives - the field only means anything with auto-fire on "
            + "(OB-178).  Problems: " + blank);

        assertTrue(automatic.size() > blank.size(),
            "auto-fire with no sensor was accepted.  That route can never fire itself, and saved it "
            + "sits in the list marked automatic doing nothing: " + automatic);

        assertFalse(named(rubbish, "S88"),
            "a sensor field holding only spaces was refused as not-a-number.  It is trimmed and it "
            + "is empty, which is the same answer a cleared field gives: " + rubbish);
        }
        finally
        {
            close(editor);
        }
    }

    /**
     * Whether any problem mentions a thing.
     *
     * @param problems what the editor refused
     * @param what a word from the message
     * @return true when one of them mentions it
     */
    private static boolean named(java.util.List<String> problems, String what)
    {
        for (String problem : problems)
        {
            if (problem != null && problem.toLowerCase().contains(what.toLowerCase())) return true;
        }

        return false;
    }
    /**
     * Changing the kind does not leave the old target behind.
     *
     * The one Adam found.  Tested through the table model, which is the thing that was wrong - the
     * row it built is what got saved.
     */
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
     * A three-way row starts with the pause its two motors need.
     */
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

    /**
     * A three-way row is saved as the two commands it stands for, in order.
     */
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
     * Deleting an unrelated condition does not turn a leading "or" into "and" (FR3-B1).
     *
     * **A silent change to when a route fires**, in the route editor that is a headline 3.0.0 feature,
     * over a path no test covered.
     *
     * `(A or B) and C` loads as `[A(1), or(1), B(1), and(0), C(0)]` - `ConditionOutline.write` bumps a
     * cross-operator left child a level, and `toExpression`'s own comment says such a condition *"opens
     * as an outline whose first row is one level in"*.
     *
     * Delete `C`, which has nothing to do with the group, and the table is left with
     * `[A(1), or(1), B(1)]`. `tidy()` then forced row 0 flat - `at == 0 ? 0` - giving
     * `[A(0), or(1), B(1)]`, which reads back as **`A and B`**: the `or` is alone in a one-item
     * sub-run with nothing to join, so the level-0 word defaults to AND.
     *
     * **And nothing objected.** `problems()` does not flag it - one joiner alone at its depth is not a
     * disagreement - so no row went red, and `everythingWrong`'s only condition-shape gate is
     * `hasProblems()`. Save wrote the changed meaning, which is the hazard the comment two lines above
     * that gate names: *"the route would then fire at times nobody asked for."*
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
     * FR-068: a nested group that is NOT the first term can be built here, by indenting.
     *
     * Adam, 2026-09-09: **"it should be representable already in the UI, right?"**  The question came
     * out of MT-320, which was about the editor MISREADING `3 or ((1 or 2) and 4)` when a route
     * already had it.  This is the other half: can somebody who has no such route make one?
     *
     * **Yes, and this is the gesture.**  Seven lines are typed flat - which is what the plus does,
     * every new condition arriving at the depth of the one above - and then indented, twice for the
     * innermost pair.  Nothing else is used: no file, no capture, no loading.
     *
     * The condition built is `3 or (4 and (1 or 2))`, which is Adam's condition with the AND's two
     * sides the other way round.  That is not a dodge and it is not an accident either, so it is
     * written down: **a line may be at most one level deeper than the line above it**
     * (`ConditionTable.indent`), and the outline `ConditionOutline.of` writes for a group in the
     * AND's LEFT position steps from depth 0 straight to depth 2 - the OR at 0, the AND at 1, the
     * bracket at 2, with no depth-1 line in front of the bracket to indent from.  Written with the
     * group on the RIGHT, every step is one, and AND means the same thing either way round.
     *
     * So the shape is available; the particular ORDER the loader writes it in is not typeable, and a
     * route that arrives carrying it opens and reads correctly, which is what MT-320 settled.
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
            // FLAT, which is the only shape the plus can produce: a new condition takes the depth of
            // the line above it, and the first line is always at the outermost level.
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

            // PRECONDITION: typed flat, it is one level that disagrees with itself - an OR and an AND
            // side by side - which is exactly the state the editor draws in red and refuses to save.
            // Indenting is the way out of it, and that is what this test is about.
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

            // AND THE RULE THAT DECIDES WHICH ORDER IS TYPEABLE, measured rather than claimed in
            // prose above.  Line 2 sits immediately after the outer OR, which is at depth 0, so it
            // can never be deeper than 1 - and depth 2 is exactly where `ConditionOutline.of` puts
            // the bracket when the group is the AND's LEFT child.  That is the whole reason the
            // group is built on the right here.
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

    /**
     * The depths of the outline, for a failure message.
     *
     * A condition outline that has gone wrong has gone wrong in its INDENTATION, and the meaning
     * alone does not say where.
     */
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
     * The SHAPE of the expression the outline currently means - "And(Or(x,x),x)".
     *
     * The class name of the top node alone is not enough: `(A or B) and C` is a NodeAnd whichever way
     * the group inside it reads, which is what made the first version of this helper unable to tell
     * the defect from the fix.
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

    /** A feedback condition on one sensor. */
    private static org.traincontrol.base.RouteCommand feedback(int address)
    {
        return org.traincontrol.base.RouteCommand.RouteCommandFeedback(address, true);
    }
}
