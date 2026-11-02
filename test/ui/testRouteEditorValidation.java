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
     * With Automatically Fire off, the conditions say under themselves, in red, that they are only checked when it is
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
        return open(null);
    }

    /** An editor opened on a route, as a double-click on it opens one; null for a new route. */
    private static org.traincontrol.gui.RouteEditorFrame open(final org.traincontrol.base.Route route) throws Exception
    {
        final org.traincontrol.gui.RouteEditorFrame[] frame =
            new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> frame[0] = new org.traincontrol.gui.RouteEditorFrame(null,
            route == null ? null : route.getName(), route));

        return frame[0];
    }

    private static void close(org.traincontrol.gui.RouteEditorFrame frame) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.dispose());
    }

    /** The frame given these condition lines, packed and showing, on the event thread. */
    private static void showRows(org.traincontrol.gui.RouteEditorFrame frame,
        java.util.List<org.traincontrol.base.ConditionOutline.Row> rows) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame.setConditionRowsForTest(rows);
            frame.pack();
            frame.setVisible(true);
        });
    }

    /** The frame given these condition lines, on the event thread. */
    private static void setRows(org.traincontrol.gui.RouteEditorFrame frame,
        java.util.List<org.traincontrol.base.ConditionOutline.Row> rows) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));
    }

    /** The English message bundle, read from the source folder. */
    private static java.util.Properties englishBundle() throws Exception
    {
        return bundle("");
    }

    /** One language's message bundle, read from the source folder: "" for English, "_de" for German. */
    private static java.util.Properties bundle(String suffix) throws Exception
    {
        java.util.Properties out = new java.util.Properties();

        try (java.io.InputStream in = new java.io.FileInputStream("src/org/traincontrol/resources/messages" + suffix
            + ".properties"))
        {
            out.load(in);
        }

        return out;
    }

    private static void needsADisplay()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window - this needs a display");
        }
    }
    /**
     * Deleting an unrelated condition does not turn a leading "or" into "and": `(A or B) and C` minus `C` is still
     * `(A or B)`, a group one level in, not `A and B` with nothing in red.
     *
     * MUTATION: in `ConditionOutline.removed`, put the first row of the result at depth 0, and the OR reads as AND.
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
     * MUTATION: in `ConditionOutline.removed`, call `cutOut` on the group holding the piece rather than the piece,
     * and the 3 goes with it.
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

            setRows(frame, outline);

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
     * FR-068 (MT-320): a nested group that is NOT the first term can be built, with Group Selected (OB-396).  Four
     * conditions joined by "or" become `3 or (4 and (1 or 2))`: grouping 4, 1 and 2 and choosing All on the new
     * caption nests them under "and", and grouping 1 and 2 inside that and choosing At least one nests them under "or"
     * again, each step through the button and the caption.
     *
     * MUTATION: in `ConditionOutline.run`, refuse a run that does not start at its group's first item (`from > 0`),
     * and Group Selected is grayed for 4, 1 and 2.
     */
    @Test
    public void testANestedGroupThatIsNotTheFirstTermCanBeBuilt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, flat(3, "OR", 4, "OR", 1, "OR", 2));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 2, 6);
            press(frame, "route.ui.menuGroupSelected");
            chooseRule(table, 1, "route.ui.conditionCaptionAll");

            assertEquals(levels(frame), " 0:3 0OR 1:4 1AND 1:1 1AND 1:2",
                "Group Selected on 4, 1 and 2 of '3 or 4 or 1 or 2', then All on its caption, should nest them under"
                + " 'and'");

            selectLines(table, 4, 6);
            press(frame, "route.ui.menuGroupSelected");
            chooseRule(table, 2, "route.ui.conditionCaptionAny");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the grouped outline is flagged: " + depths(frame));

            assertEquals(reads(frame), "Or(x,Group(And(x,Group(Or(x,x)))))",
                "the outline built by grouping does not mean \"3 or (4 and (1 or 2))\". Depths: "
                + depths(frame) + ", meaning: " + reads(frame) + ". FR-068 asks whether a bracket that is not the"
                + " first term can be built at all");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * A condition that starts with a group can be built (Adam: "the first condition for some reason cannot be indented
     * for proper grouping."), with Group Selected (OB-396): "2 and 3 and 1 and 4" becomes "(2 or 3) and (1 or 4)", each
     * new group's rule chosen on its caption.
     *
     * MUTATION: in `ConditionOutline.run`, refuse a run that starts at the group's first item (`from > 0`), and the
     * first grouping does nothing.
     */
    @Test
    public void testAConditionThatStartsWithAGroupCanBeBuilt() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, flat(2, "AND", 3, "AND", 1, "AND", 4));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 0, 2);
            press(frame, "route.ui.menuGroupSelected");
            chooseRule(table, 1, "route.ui.conditionCaptionAny");

            selectLines(table, 4, 6);
            press(frame, "route.ui.menuGroupSelected");
            chooseRule(table, 2, "route.ui.conditionCaptionAny");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the grouped outline is flagged: " + depths(frame));

            assertEquals(reads(frame), "And(Group(Or(x,x)),Group(Or(x,x)))",
                "(2 or 3) and (1 or 4) could not be built - the first condition cannot go in a group, so a"
                + " condition cannot start with one. Adam, 2026-09-14: \"the first condition for some reason"
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
            // 2 or 3 with the OR alone one level in, as a stored route can have it.
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> typed = flat(2, "OR", 3);

            typed.set(1, typed.get(1).atDepth(1));

            setRows(frame, typed);

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

            setRows(frame, rows);

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

            setRows(frame, rows);

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

            setRows(frame, rows);

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
     * A group whose words disagree, as an older route can arrive, has a red caption with a warning triangle whose
     * tooltip says which of the two things is wrong (OB-396: a group's words show on its caption).  The caption is read
     * as the table PAINTS it, and one label paints every caption, so a group that is fine must carry neither.
     *
     * MUTATION: in `ConditionTable.captionFor`, set the icon whatever the problem, and the second group wears the
     * triangle.
     */
    @Test
    public void testARedWordCarriesAWarningThatSaysWhy() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            // (2 or 3 and 4) and (5 or 6): the AND on line 3 differs from the OR already joining its group.
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> mixed =
                fromLevels(" 1:2 1OR 1:3 1AND 1:4 0AND 1:5 1OR 1:6");

            setRows(frame, mixed);

            // Each caption is read the moment it is painted: one label paints them all.  The flagged one goes first so
            // the second read is where a stale warning shows.
            final Painted[] cell = new Painted[2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                cell[0] = new Painted(frame.conditionCellForTest(3));
                cell[1] = new Painted(frame.conditionCellForTest(7));
            });

            assertTrue(cell[0].icon != null, "the group mixing 'or' and 'and' has no warning triangle on its caption -"
                + " painted '" + cell[0].text + "'; the outline says "
                + org.traincontrol.base.ConditionOutline.whatIsWrong(frame.conditionRowsForTest()) + ". Depths:" + depths(frame));

            assertTrue(String.valueOf(cell[0].tooltip).contains(org.traincontrol.util.I18n.t("route.ui.conditionWordDisagrees")),
                "the mixed group's caption tooltip does not say that its words disagree: " + cell[0].tooltip);

            assertNull(cell[1].icon, "the (5 or 6) group is not flagged and wears the triangle of the caption painted before it");
            assertNull(cell[1].tooltip, "the (5 or 6) group is not flagged and wears a tooltip (MT-868: none on a"
                + " caption that is not flagged)");

            // 2 or 3 with the OR alone indented, as a stored route can have it: it joins nothing.
            final java.util.List<org.traincontrol.base.ConditionOutline.Row> stranded = flat(2, "OR", 3);

            stranded.set(1, stranded.get(1).atDepth(1));

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionRowsForTest(stranded);
                cell[0] = new Painted(frame.conditionCellForTest(1));
            });

            assertTrue(cell[0].icon != null, "the group holding the stranded OR has no warning triangle. Depths:" + depths(frame));

            assertTrue(String.valueOf(cell[0].tooltip).contains(org.traincontrol.util.I18n.t("route.ui.conditionWordJoinsNothing")),
                "the stranded OR's caption tooltip does not say that it joins nothing: " + cell[0].tooltip + ". Depths:"
                + depths(frame));
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
     * FR-144, OB-396: "1 or 2 or 3 and 4", arriving mixed, becomes "(1 or 2 or 3) and 4" by selecting the first three
     * conditions and pressing Group Selected: the new group keeps the word that joined them.
     * Alt+Up then moves 4 past the whole group and Alt+Down back, every line keeping its level; Ungroup lifts the
     * three back out, joined by their parent's "and", so it never makes a mixed level.
     *
     * MUTATION: in `ConditionOutline.grouped`, join the wrapped items by the parent's word left once the run is out
     * (`group.getWord()`) rather than the first word inside the run, and the group joins by "and".
     * MUTATION: drop the group branch of `ConditionOutline.moved`, and 4 trades places with 3 inside the group.
     */
    @Test
    public void testSeveralConditionsIndentAtOnceAndOneMovesPastTheGroup() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, flat(1, "OR", 2, "OR", 3, "AND", 4));

            assertFalse(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "precondition: 'or' and 'and' side by side at one level should be flagged");

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 0, 4);
            press(frame, "route.ui.menuGroupSelected");

            assertEquals(levels(frame), " 1:1 1OR 1:2 1OR 1:3 0AND 0:4",
                "the three selected conditions go one level in, joined by 'or', the 'and' stays");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "(1 or 2 or 3) and 4 is flagged: " + levels(frame));

            clickCondition(table, 6, NOTHING_TO_EDIT, 0);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 0:4 0AND 1:1 1OR 1:2 1OR 1:3",
                "Alt+Up moved 4 up past the whole group, with the 'and' between them and every level kept");

            support.Keys.press(table, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 1:1 1OR 1:2 1OR 1:3 0AND 0:4", "and Alt+Down moved it back down past it");

            clickCondition(table, 2, NOTHING_TO_EDIT, 0);
            press(frame, "route.ui.menuUngroup");

            assertEquals(levels(frame), " 0:1 0AND 0:2 0AND 0:3 0AND 0:4",
                "Ungroup lifts the three into the outer level, joined by its 'and'");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-379: "(a and b and c) or (d and e and f)", Add Condition with f selected adds g at the end of the second
     * group (OB-396), and Alt+Up pressed four times gives "(a and b and c and g) or (d and e and f)" with g still
     * selected; Alt+Down takes it back.
     *
     * MUTATION: drop the edge branch of `ConditionOutline.plan`, and g is swapped with c.
     */
    @Test
    public void testAConditionAddedAtTheEndMovesUpIntoTheGroupAbove() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26"));

            final javax.swing.JTable table = conditionTable(frame);

            clickCondition(table, 10, NOTHING_TO_EDIT, 0);
            press(frame, "route.ui.menuAddCondition");

            assertEquals(levels(frame), " 1:21 1AND 1:22 1AND 1:23 0OR 1:24 1AND 1:25 1AND 1:26 1AND 1:1",
                "precondition: Add Condition with f selected adds g (sensor 1) at the end of the second group");

            for (int press = 1; press <= 4; press++)
            {
                support.Keys.press(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);
            }

            assertEquals(levels(frame), " 1:21 1AND 1:22 1AND 1:23 1AND 1:1 0OR 1:24 1AND 1:25 1AND 1:26",
                "four presses of Alt+Up on g should give (a and b and c and g) or (d and e and f)");

            assertEquals(selectedLine(table), 6, "g is no longer the selected line after the move");

            support.Keys.press(table, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 1:21 1AND 1:22 1AND 1:23 0OR 1:1 1AND 1:24 1AND 1:25 1AND 1:26",
                "Alt+Down on g takes it back to the front of the second group");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * FR-147: a right-click on a condition offers Insert Condition Above and Below and the condition's own actions,
     * each enabled exactly where it does something (OB-396: Group Selected and Ungroup in place of Indent and
     * Outdent); Insert joins a new condition at the line's level by its word and selects it; a right-click on the
     * trash works no trash; below the lines it offers Add Condition.
     *
     * MUTATION: in `ConditionOutline.inserted`, join the new condition with `Joiner.AND` always, and "21 or 22 and 1"
     * is flagged.
     * MUTATION: in `offerRowMenu`, add Duplicate before `table.addOwnItems(menu)`, and it sits above Group Selected
     * (FR-163: Duplicate between dividers of its own, above Delete).
     */
    @Test
    public void testARightClickInsertsAConditionAndOffersTheLinesActions() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 1:21 1OR 1:22 0AND 0:23"));

            final javax.swing.JTable table = conditionTable(frame);

            javax.swing.JPopupMenu menu = rightClickBelow(table);

            assertNotNull(menu, "a right-click below the lines opened no menu");
            assertEquals(offered(menu), "+route.ui.menuAddCondition", "the menu below the lines");

            support.Mouse.choose(item(menu, "route.ui.menuAddCondition"));

            assertEquals(levels(frame), " 1:21 1OR 1:22 0AND 0:23 0AND 0:1", "Add Condition adds one at the end");
            assertEquals(selectedLine(table), 6, "and selects it");

            menu = rightClickCell(table, 2, NOTHING_TO_EDIT);

            assertNotNull(menu, "a right-click on condition 22 opened no menu");

            assertEquals(offered(menu), "+route.ui.menuInsertConditionAbove +route.ui.menuInsertConditionBelow"
                + " | +route.ui.menuMoveUp -route.ui.menuMoveDown -route.ui.menuGroupSelected +route.ui.menuUngroup"
                + " +route.ui.menuSplitHere | +route.ui.menuDuplicate | +ui.delete", "the menu on 22 in (21 or 22) and 23"
                + " and 1, enabled (+) or not (-), its dividers as |");

            assertEquals(selectedLine(table), 2, "the line right-clicked is the one selected");

            support.Mouse.choose(item(menu, "route.ui.menuInsertConditionBelow"));

            assertEquals(levels(frame), " 1:21 1OR 1:22 1OR 1:1 0AND 0:23 0AND 0:1",
                "Insert Condition Below puts sensor 1 under 22, in its group and joined by its 'or'");
            assertEquals(selectedLine(table), 4, "and the new condition is selected");

            menu = rightClickCell(table, 4, DELETE_MARK);

            assertNotNull(menu, "a right-click on the trash opened no menu");
            assertEquals(levels(frame), " 1:21 1OR 1:22 1OR 1:1 0AND 0:23 0AND 0:1",
                "a right-click on the trash worked the trash");

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
     * OB-396: Add Condition puts a new condition at the end of the innermost group holding the selection, joined by
     * that group's word; with nothing selected, or from the menu below the lines, at the end of the outermost.
     *
     * MUTATION: in `ConditionTable.addRow`, add to `top` whatever is selected, and the condition added with 22
     * selected lands after the group.
     */
    @Test
    public void testAddConditionJoinsWithTheLevelsWord() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0OR 0:22"));

            final javax.swing.JTable table = conditionTable(frame);

            javax.swing.JPopupMenu menu = rightClickBelow(table);

            assertNotNull(menu, "a right-click below the lines opened no menu");

            support.Mouse.choose(item(menu, "route.ui.menuAddCondition"));

            assertEquals(levels(frame), " 0:21 0OR 0:22 0OR 0:1", "Add Condition after \"21 or 22\" joins with its"
                + " level's 'or'");

            setRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23"));

            clickCondition(table, 2, NOTHING_TO_EDIT, 0);
            press(frame, "route.ui.menuAddCondition");

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 1:23 1OR 1:1", "Add Condition with 22 selected, in"
                + " \"21 and (22 or 23)\", joins the group with its 'or'");
            assertEquals(selectedLine(table), 6, "and selects the new condition");

            setRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23"));

            press(frame, "route.ui.menuAddCondition");

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 1:23 0AND 0:1", "Add Condition with nothing selected"
                + " joins the outermost level with its 'and'");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the outline Add Condition made is flagged: "
                + org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()));
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * A capture into the conditions joins with the last condition's level word, as Add Condition does.
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
     * FR-147, OB-396: what an edit changed: after Alt+Up, Group Selected, Ungroup or Add Condition, the conditions it
     * moved or made are selected and bold in "Reads as".
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
            showRows(frame, flat(21, "AND", 22, "AND", 23));

            final javax.swing.JTable table = conditionTable(frame);

            clickCondition(table, 4, NOTHING_TO_EDIT, 0);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 0:21 0AND 0:23 0AND 0:22", "precondition: 23 moved up");
            assertEquals(selectedLine(table), 2, "23 is selected after its move");
            assertEquals(bold(frame), "[S88 #23]", "after the move, Reads as: " + frame.readsAsForTest());

            selectLines(table, 2, 4);
            press(frame, "route.ui.menuGroupSelected");

            assertEquals(levels(frame), " 0:21 0AND 1:23 1AND 1:22", "precondition: 23 and 22 grouped");
            assertEquals(bold(frame), "[S88 #23, S88 #22]", "after Group Selected, Reads as: " + frame.readsAsForTest());

            press(frame, "route.ui.menuUngroup");

            assertEquals(levels(frame), " 0:21 0AND 0:23 0AND 0:22", "precondition: the group taken apart");
            assertEquals(bold(frame), "[S88 #23, S88 #22]", "after Ungroup, Reads as: " + frame.readsAsForTest());

            press(frame, "route.ui.menuAddCondition");

            assertEquals(selectedLine(table), 6, "the condition Add Condition made is selected");
            assertEquals(bold(frame), "[S88 #1]", "after Add Condition, Reads as: " + frame.readsAsForTest());
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * FR-147: deleting a condition standing before a group with the group's word after it, as a stored route can
     * have it, takes the "and" that now joins nothing, not the "or": "(21 and 22) or (24 and 25)" is left.
     *
     * MUTATION: in `ConditionOutline.removed`, drop the branch for a condition before a group whose first line is a
     * word, and the "or" goes.
     */
    @Test
    public void testDeletingAnOutdentedConditionKeepsTheWordBetweenTheGroups() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 1:21 1AND 1:22 0OR 0:1 1AND 1:24 1AND 1:25"));

            final javax.swing.JTable table = conditionTable(frame);

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
     * OB-382: Delete from the right-click menu inside a selection of several conditions removes every one of them,
     * with the words between them, and selects the line now where the first one was; outside the selection, and from
     * a line's own trash mark, it removes that one condition.
     *
     * MUTATION: in `offerRowMenu`, have Delete call `table.removeAt(row)`, and only the line clicked goes.
     */
    @Test
    public void testDeleteFromTheMenuRemovesEverySelectedLine() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0OR 0:22 0OR 0:23 0OR 0:24"));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 2, 4);
            rightClickAndChoose(table, 4, "ui.delete");

            assertEquals(levels(frame), " 0:21 0OR 0:24",
                "Delete inside the selection 22 and 23 should remove both, and the word between them");
            assertEquals(selectedRows(table), "[2]", "the line now where 22 was is selected");

            setRows(frame, fromLevels(" 1:21 1OR 1:22 0AND 0:23 0AND 0:24"));

            selectLines(table, 0, 2);
            rightClickAndChoose(table, 0, "ui.delete");

            assertEquals(levels(frame), " 0:23 0AND 0:24",
                "deleting the whole group (21 or 22) should leave 23 and 24; Reads as: " + plainReading(frame));

            setRows(frame, fromLevels(" 0:21 0OR 0:22 0OR 0:23 0OR 0:24"));

            selectLines(table, 0, 2);
            rightClickAndChoose(table, 4, "ui.delete");

            assertEquals(levels(frame), " 0:21 0OR 0:22 0OR 0:24",
                "Delete on a line outside the selection removes that line alone");

            selectLines(table, 0, 2);
            clickCondition(table, 4, DELETE_MARK, 0);

            assertEquals(levels(frame), " 0:21 0OR 0:22",
                "a line's own trash mark removes that line alone, whatever was selected");
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * OB-382: a delete changes no word but those of the conditions it takes.  22 and 24 of "(21 and 22) or (24 and 25)"
     * leave "(21) or (25)", not "21 and 25"; 22 and 23 of "(21 or 22) and (23 or 24)", by the Delete key, leave
     * "(21) and (24)"; 21 and 22 of "21 or (22 and 23)" leave 23.  And a delete outside a group whose first item is a
     * group keeps that group's word: 4 of "3 or ((1 or 2) and 4)" leaves "3 or ((1 or 2))".
     *
     * MUTATION: in `ConditionOutline.removed`, take each piece out of `top` with `top.elements.remove`, and 22 and 24
     * are left in place.
     * MUTATION: in `ConditionTable.removeRows`, flatten every row deeper than the row above plus one, as the old tidy
     * did, and "(1 or 2)" reads "1 and 2".
     */
    @Test
    public void testDeletingASelectionAcrossAGroupBoundaryLeavesAValidOutline() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 1:21 1AND 1:22 0OR 1:24 1AND 1:25"));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 2, 4);
            rightClickAndChoose(table, 2, "ui.delete");

            assertEquals(levels(frame), " 1:21 0OR 1:25",
                "deleting 22 and 24 should leave (21) or (25); Reads as: " + plainReading(frame));
            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the outline the delete left is flagged: " + levels(frame));

            setRows(frame, fromLevels(" 1:21 1OR 1:22 0AND 1:23 1OR 1:24"));

            selectLines(table, 2, 4);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DELETE);

            assertEquals(levels(frame), " 1:21 0AND 1:24",
                "the Delete key on 22 and 23 should leave (21) and (24); Reads as: " + plainReading(frame));

            setRows(frame, fromLevels(" 0:21 0OR 1:22 1AND 1:23"));

            selectLines(table, 0, 2);
            rightClickAndChoose(table, 2, "ui.delete");

            assertEquals(levels(frame), " 1:23",
                "deleting 21 and 22 of 21 or (22 and 23) should leave 23; Reads as: " + plainReading(frame));

            setRows(frame, fromLevels(" 0:3 0OR 2:1 2OR 2:2 1AND 1:4"));

            clickCondition(table, 6, DELETE_MARK, 0);

            assertEquals(levels(frame), " 0:3 0OR 2:1 2OR 2:2",
                "the trash on 4 of 3 or ((1 or 2) and 4) should leave 3 or ((1 or 2)); Reads as: "
                + plainReading(frame));
            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "the outline the trash left is flagged: " + levels(frame));
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
     * MUTATION: in `ConditionOutline.wordOfTheLevel`, drop the walk back to the group's first line, and Below joins
     * "and".
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
            showRows(frame, fromLevels(" 0:21"));

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
     * FR-150, OB-396: a group's word shows on its caption, so a right-click on a caption offers Add Condition into that
     * group, joined by its word: on "(21 and 22) or (24 and 25)", each caption's group gains one at its end.
     *
     * MUTATION: in `ConditionTable.chooseOffARow`, clear the selection on a caption as below the lines, and every
     * case adds to the outermost group.
     */
    @Test
    public void testInsertOnAWordLineJoinsByThatWord() throws Exception
    {
        needsADisplay();

        final String twoGroups = " 1:21 1AND 1:22 0OR 1:24 1AND 1:25";

        // which caption, top to bottom; the outline after
        final Object[][] cases = {
            {0, " 1:21 1AND 1:22 0OR 1:24 1AND 1:25 0OR 0:1"},
            {1, " 1:21 1AND 1:22 1AND 1:1 0OR 1:24 1AND 1:25"},
            {2, " 1:21 1AND 1:22 0OR 1:24 1AND 1:25 1AND 1:1"},
        };

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(twoGroups));

            final javax.swing.JTable table = conditionTable(frame);

            for (Object[] c : cases)
            {
                setRows(frame, fromLevels(twoGroups));

                int caption = captionLines(table).get((Integer) c[0]);
                java.awt.Rectangle line = support.Components.onTheEventThread(() -> table.getCellRect(caption, 3, true));
                javax.swing.JPopupMenu menu = support.Mouse.rightClickAt(table,
                    new java.awt.Point(line.x + line.width / 2, line.y + line.height / 2), 5000);

                assertNotNull(menu, "a right-click on caption " + c[0] + " opened no menu");
                assertEquals(offered(menu), "+route.ui.menuAddCondition", "the menu on caption " + c[0]);

                support.Mouse.choose(item(menu, "route.ui.menuAddCondition"));

                assertEquals(levels(frame), c[1], "Add Condition from caption " + c[0] + " of" + twoGroups);
                assertEquals(bold(frame), "[S88 #1]", "the new condition is not the selected one");
                assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                    "Add Condition from caption " + c[0] + " left a red word: " + levels(frame));
            }
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /**
     * FR-151: after Insert Condition Above or Below, Add Condition from the menu or the button, Alt+Up, Alt+Down or
     * Move Down, the keyboard is on the new or moved line's address cell, so a digit starts editing it as after one
     * click; the action itself opens no editor.
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
            showRows(frame, fromLevels(three));

            final javax.swing.JTable table = conditionTable(frame);
            final javax.swing.JTextField elsewhere = support.Components.find(frame, javax.swing.JTextField.class,
                f -> f.isEditable() && f.isShowing());

            assertNotNull(elsewhere, "the route editor has no field to put the keyboard in");

            clickCondition(table, 2, ADDRESS, 0);

            assertEquals(digitEdits(frame, table), "line 2 column " + ADDRESS,
                "precondition: one click on line 2's address and a digit do not edit it, so the fixture's keyboard"
                + " does not reach the table");

            // What is done, and the outline row that then holds the condition it made or moved
            final Object[][] cases = {
                {"Insert Condition Above on line 2", 2},
                {"Insert Condition Below on line 2", 4},
                {"Add Condition below the lines", 6},
                {"the Add Condition button", 6},
                {"Alt+Up on line 4", 2},
                {"Alt+Down on line 0", 2},
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
                else if (did.startsWith("Add")) support.Mouse.choose(item(rightClickBelow(table),
                    "route.ui.menuAddCondition"));
                else if (did.equals("the Add Condition button")) press(frame, "route.ui.menuAddCondition");
                else if (did.startsWith("Alt+")) altArrow(table, did.startsWith("Alt+Up") ? 4 : 0,
                    did.startsWith("Alt+Up") ? java.awt.event.KeyEvent.VK_UP : java.awt.event.KeyEvent.VK_DOWN);
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
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));

            final javax.swing.JTable table = conditionTable(frame);

            rightClickAndChoose(table, 2, "route.ui.menuInsertConditionBelow");

            asIfFocused(frame, owner -> typeADigit(owner, '7'));
            commit(table);

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:7 0AND 0:23",
                "7 typed on the new condition did not make it sensor 7");
            assertEquals(bold(frame), "[S88 #7]", "Reads as " + frame.readsAsForTest());

            clickCondition(table, 0, ADDRESS, 0, 2);

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
                ? "line " + call(table, "rowOf", table.getEditingRow()) + " column " + table.getEditingColumn()
                : "nothing";

            if (table.isEditing()) table.getCellEditor().cancelCellEditing();

            return out;
        });
    }

    /** Keys go where the window sends the keyboard; it answers as focus owner, as a test window rarely is one. */
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
     * MUTATION: in `build`, lay the middle panel out with a plain `new GridLayout(2, 1, 0, 8)`, and the extra is
     * halved.
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

            final java.awt.Container room = javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class,
                conditions);

            final int[] packed = support.Components.onTheEventThread(() -> new int[]{frame.getHeight(),
                commands.getParent().getHeight(), room.getHeight(),
                section(commands, conditions).getHeight(), section(conditions, commands).getHeight()});

            assertEquals(packed[3], packed[4], "at the packed size the two sections are not as tall as each other");

            // As tall as the screen allows, as maximizing does: Windows keeps a window within the screen's height.
            final int spare = support.Components.onTheEventThread(() ->
                frame.getGraphicsConfiguration().getBounds().height - frame.getHeight());

            assertTrue(spare >= 150, "precondition: the screen has no room for a taller window: " + spare);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setSize(frame.getWidth(), frame.getHeight() + spare);
                frame.validate();
            });

            support.Components.settle();

            final int[] tall = support.Components.onTheEventThread(() -> new int[]{frame.getHeight(),
                commands.getParent().getHeight(), room.getHeight()});

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

    /** The section holding a table: its ancestor that sits beside the other table's section. */
    private static java.awt.Container section(javax.swing.JTable table, javax.swing.JTable other)
    {
        return support.RouteEditing.section(table, other);
    }

    /**
     * OB-396: the handle of each line the last edit changed carries an amber mark on its left, which stays until the
     * next edit; the fading yellow is gone.  Move Down on the first of "21 and 22 and 23" marks the two conditions
     * that traded places, never the 23, still a second and a half later; Add Condition then marks only the new one.
     *
     * MUTATION: in `ConditionTable.showChanges`, drop the `changed.add`, and no handle is marked.
     */
    @Test
    public void testAnEditShowsWhichLinesItChanged() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));

            final javax.swing.JTable table = conditionTable(frame);

            assertEquals(marked(table), "[]", "precondition: a route as loaded has no line marked");

            rightClickAndChoose(table, 0, "route.ui.menuMoveDown");

            assertEquals(levels(frame), " 0:22 0AND 0:21 0AND 0:23", "precondition: Move Down on 21 swaps it with 22");
            assertEquals(marked(table), "[0, 2]", "just after Move Down the handles of 22 and 21 are not both marked");

            Thread.sleep(1500);

            assertEquals(marked(table), "[0, 2]", "1.5 seconds after Move Down the marks are gone, before any edit");

            press(frame, "route.ui.menuAddCondition");

            assertEquals(marked(table), "[6]", "after Add Condition only the new condition's handle is marked");
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /** The amber of the mark on a changed line's handle (OB-396). */
    private static final java.awt.Color AMBER = new java.awt.Color(0xE0, 0xA5, 0x26);

    /** The outline rows whose handle cell, painted, shows the amber mark down its left edge. */
    private static String marked(final javax.swing.JTable table)
    {
        return support.Components.onTheEventThread(() ->
        {
            java.util.List<Integer> out = new java.util.ArrayList<>();

            for (int line = 0; line < table.getRowCount(); line++)
            {
                java.awt.Component cell = table.prepareRenderer(table.getCellRenderer(line, HANDLE), line, HANDLE);
                java.awt.Rectangle box = table.getCellRect(line, HANDLE, true);
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(box.width, box.height,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);

                cell.setBounds(0, 0, box.width, box.height);
                cell.paint(image.getGraphics());

                if (new java.awt.Color(image.getRGB(1, box.height / 2)).equals(AMBER))
                {
                    int row = (Integer) call(table, "rowOf", line);

                    out.add(row);
                }
            }

            return out.toString();
        });
    }

    /**
     * FR-147: a right-click inside a selection of several conditions keeps the selection (Insert Below then selects
     * the new line alone); outside it, it picks the line clicked.
     *
     * MUTATION: in `offerRowMenu`, always select the line clicked, and the selection of two is lost.
     */
    @Test
    public void testInsertFromAWordLineOrASelection() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23 0AND 0:24"));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 2, 4);

            javax.swing.JPopupMenu menu = rightClickCell(table, 4, NOTHING_TO_EDIT);

            assertNotNull(menu, "a right-click inside the selection opened no menu");
            assertEquals(selectedRows(table), "[2, 4]", "a right-click inside a selection of several lines changed it");

            support.Mouse.choose(item(menu, "route.ui.menuInsertConditionBelow"));

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23 0AND 0:1 0AND 0:24",
                "Insert Condition Below from inside the selection puts one under the line clicked");
            assertEquals(selectedRows(table), "[6]", "the new condition alone is selected");

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
     * FR-147: an inserted condition is unsaved work: Cancel asks first, and the saved route keeps its conditions.
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

            clickCondition(table, 2, ADDRESS, 0, 2);

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

    /** A right-click on an outline row's line where no press edits anything, and the item chosen; it must be enabled. */
    private static void rightClickAndChoose(javax.swing.JTable table, int line, String key)
    {
        support.RouteEditing.rightClickAndChoose(table, line, key);
    }

    private static String plainReading(org.traincontrol.gui.RouteEditorFrame frame)
    {
        return support.Components.onTheEventThread(() -> frame.readsAsForTest()).replaceAll("<[^>]*>", "")
            .replaceAll("\\s+", " ").trim();
    }

    private static javax.swing.JTable conditionTable(org.traincontrol.gui.RouteEditorFrame frame)
    {
        return table(frame, "ConditionTable");
    }

    /** The route editor's table of one class, by its simple name. */
    private static javax.swing.JTable table(org.traincontrol.gui.RouteEditorFrame frame, final String kind)
    {
        return support.RouteEditing.table(frame, kind);
    }

    /** The outline row of the selected line, or -1: the table shows no line for a joining word (OB-396). */
    private static int selectedLine(final javax.swing.JTable table)
    {
        return support.Components.onTheEventThread(() -> (Integer) call(table, "rowOf", table.getSelectedRow()));
    }

    /** The outline rows of the selected conditions, as "[2, 4]". */
    private static String selectedRows(final javax.swing.JTable table)
    {
        return support.Components.onTheEventThread(() ->
        {
            java.util.List<Integer> out = new java.util.ArrayList<>();

            for (int line : table.getSelectedRows())
            {
                int row = (Integer) call(table, "rowOf", line);

                if (row >= 0) out.add(row);
            }

            return out.toString();
        });
    }

    /** A right-click in one cell of the line showing an outline row. */
    private static javax.swing.JPopupMenu rightClickCell(final javax.swing.JTable table, int row, int column)
    {
        return support.RouteEditing.rightClickCell(table, row, column);
    }

    /** A right-click below the last line, where the table has no line. */
    private static javax.swing.JPopupMenu rightClickBelow(final javax.swing.JTable table)
    {
        return support.RouteEditing.rightClickBelow(table);
    }

    private static int screen(final javax.swing.JTable table, final int row)
    {
        return support.RouteEditing.screen(table, row);
    }

    private static java.util.List<Integer> captionLines(final javax.swing.JTable table)
    {
        return support.RouteEditing.captionLines(table);
    }

    private static Object call(Object table, String name, int argument)
    {
        return support.RouteEditing.call(table, name, argument);
    }

    /** Presses a button of the route editor by its text, as a click does, and lets its work finish. */
    private static void press(final org.traincontrol.gui.RouteEditorFrame frame, String key)
    {
        support.RouteEditing.press(frame, key);
    }

    /** Selects a line and presses Alt and an arrow on the table. */
    private static void altArrow(final javax.swing.JTable table, int row, int arrow) throws Exception
    {
        final int line = screen(table, row);

        javax.swing.SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(line, line));

        support.Keys.press(table, arrow, java.awt.event.InputEvent.ALT_DOWN_MASK);
    }

    /** The menu's items as their message keys, each marked + when enabled and - when not; a divider as |. */
    private static String offered(final javax.swing.JPopupMenu menu)
    {
        return support.RouteEditing.offered(menu);
    }

    private static javax.swing.JMenuItem item(javax.swing.JPopupMenu menu, String key)
    {
        return support.RouteEditing.item(menu, key);
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

                cells[0] = frame.conditionCellForTest(0, 3).getText();
                cells[1] = ((javax.swing.JLabel) frame.commandCellForTest(0, 5)).getText();
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

            clickCell(table, 0, 5, 0);

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
     * MUTATION: in `shortly`, give the direction its stored word, and Reads as says "backward".
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

    /** The condition table's columns (OB-396): the handle, the address, one no sensor can edit, and the trash. */
    private static final int HANDLE = 0;
    private static final int ADDRESS = 2;
    private static final int NOTHING_TO_EDIT = 4;
    private static final int DELETE_MARK = 5;

    /** Clicks the first outline row's line and Shift-clicks the last's, where a press neither edits nor moves. */
    private static void selectLines(javax.swing.JTable table, int first, int last) throws Exception
    {
        clickCondition(table, first, NOTHING_TO_EDIT, 0);
        clickCondition(table, last, NOTHING_TO_EDIT, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
    }

    /** A click in one cell of the line showing an outline row. */
    private static void clickCondition(javax.swing.JTable table, int row, int column, int modifiers) throws Exception
    {
        clickCell(table, screen(table, row), column, modifiers, 1);
    }

    /** The same, as a click of `count` presses. */
    private static void clickCondition(javax.swing.JTable table, int row, int column, int modifiers, int count)
        throws Exception
    {
        clickCell(table, screen(table, row), column, modifiers, count);
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
        support.RouteEditing.clickCell(table, line, column, modifiers, count);
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

    /** The depths of the outline, for a failure message. */
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
        java.util.Properties english = englishBundle();

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

            setRows(frame, rows);

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

            setRows(frame, rows);

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
        java.util.Properties english = englishBundle();

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

            setRows(frame, rows);

            final String[][] cell = new String[3][2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                for (int at = 0; at < 3; at++)
                {
                    javax.swing.JLabel painted = frame.conditionCellForTest(at * 2, 3);

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

    /**
     * OB-396: only the Kind text indents with its group's depth; Name/Address starts at one x, and its text at one
     * inset, at every depth.
     *
     * MUTATION: in the Kind renderer, drop `level * INSET_LEFT`, and the kinds no longer indent.
     */
    @Test
    public void testOnlyTheKindIndentsAndTheOtherColumnsAlign() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 2:23 2AND 2:24"));

            final javax.swing.JTable table = conditionTable(frame);

            final int[][] seen = support.Components.onTheEventThread(() ->
            {
                int[][] out = new int[3][3];

                for (int at = 0; at < 3; at++)
                {
                    int line = (Integer) call(table, "lineOf", at * 2);

                    out[at][0] = ((javax.swing.JComponent) table.prepareRenderer(table.getCellRenderer(line, 1), line, 1))
                        .getInsets().left;
                    out[at][1] = table.getCellRect(line, ADDRESS, true).x;
                    out[at][2] = ((javax.swing.JComponent) table.prepareRenderer(table.getCellRenderer(line, ADDRESS),
                        line, ADDRESS)).getInsets().left;
                }

                return out;
            });

            assertTrue(seen[0][0] < seen[1][0] && seen[1][0] < seen[2][0], "the Kind text of depths 0, 1 and 2 starts"
                + " at insets " + seen[0][0] + ", " + seen[1][0] + ", " + seen[2][0] + ", not further in at each depth");

            for (int at = 1; at < 3; at++)
            {
                assertEquals(seen[at][1], seen[0][1], "Name/Address of the depth " + at + " condition starts at another x");
                assertEquals(seen[at][2], seen[0][2], "Name/Address text of the depth " + at + " condition is indented");
            }
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: a condition's gray Protocol cell stops inside its group's outline at every depth, and nothing is painted
     * between the innermost outline and the table's right edge.
     *
     * MUTATION: in `ConditionTable.prepareRenderer`, drop the matte border, and the gray runs out past the outline.
     */
    @Test
    public void testTheGrayStaysInsideTheOutline() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 2:23 2AND 2:24"));

            final javax.swing.JTable table = conditionTable(frame);

            final String[] wrong = support.Components.onTheEventThread(() ->
            {
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(table.getWidth(),
                    table.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = image.createGraphics();

                table.clearSelection();
                table.paint(g);
                g.dispose();

                int gray = new java.awt.Color(242, 242, 242).getRGB();
                java.awt.Rectangle protocol = table.getCellRect(0, 4, true);
                int right = protocol.x + protocol.width;

                for (int at = 0; at < 4; at++)
                {
                    int line = (Integer) call(table, "lineOf", at * 2);
                    java.awt.Rectangle cell = table.getCellRect(line, 4, true);
                    int edge = (Integer) call(table, "outlineRight", Math.min(at, 2));
                    int y = cell.y + cell.height / 2;

                    // MT-868: nor down through the gap under a line where a group ends, its bottom edge drawn there.
                    for (int below = cell.y + cell.height; below < cell.y + table.getRowHeight(line); below++)
                    {
                        if (image.getRGB(edge - 4, below) == gray)
                        {
                            return new String[]{"the depth " + Math.min(at, 2) + " Protocol cell of condition " + at
                                + " is gray at y=" + below + ", under its content, which ends at "
                                + (cell.y + cell.height)};
                        }
                    }

                    if (image.getRGB(edge - 4, y) != gray)
                    {
                        return new String[]{"the depth " + at + " Protocol cell is not gray inside its outline"};
                    }

                    for (int x = edge + 1; x < right; x++)
                    {
                        if (image.getRGB(x, y) == gray)
                        {
                            return new String[]{"the depth " + at + " Protocol cell is gray at x=" + x + ", past its"
                                + " outline at " + edge};
                        }
                    }
                }

                return new String[]{null};
            });

            assertNull(wrong[0], wrong[0]);
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: a selected condition is painted in the selection's colors from Kind to Protocol only, never on its
     * handle or trash, and a caption line in the selection is never painted as selected.
     *
     * MUTATION: in `HandledTable.isCellSelected`, drop `column != HANDLE && !isMark(column) &&`, and the handle is
     * blue.
     */
    @Test
    public void testTheSelectionCoversKindToProtocolOnly() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23"));

            final javax.swing.JTable table = conditionTable(frame);
            final int line = screen(table, 2);
            final int caption = captionLines(table).get(1);

            final String painted = support.Components.onTheEventThread(() ->
            {
                table.setRowSelectionInterval(caption, line);

                StringBuilder out = new StringBuilder();

                for (int column = 0; column < table.getColumnCount(); column++)
                {
                    java.awt.Color ground = table.prepareRenderer(table.getCellRenderer(line, column), line, column)
                        .getBackground();
                    java.awt.Color above = table.prepareRenderer(table.getCellRenderer(caption, column), caption, column)
                        .getBackground();

                    out.append(ground.equals(table.getSelectionBackground()) ? "S" : "-")
                       .append(above.equals(table.getSelectionBackground()) ? "S" : "-").append(' ');
                }

                return out.toString().trim();
            });

            assertEquals(painted, "-- S- S- S- S- -- --", "each column of the selected condition and of the caption"
                + " above it: S where painted as selected, from the handle to the copy mark");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: no line shows a joining word; each group's caption names its word, the outer "At least one must be
     * true" and the nested "All of these must be true".
     *
     * MUTATION: in `ConditionTable.captionText`, always answer `route.ui.conditionCaptionAll`, and the outer caption is
     * wrong.
     */
    @Test
    public void testNoWordHasALineAndEachCaptionNamesItsGroupsWord() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            setRows(frame, fromLevels(" 0:21 0OR 1:22 1AND 1:23"));

            final javax.swing.JTable table = conditionTable(frame);

            final String shown = support.Components.onTheEventThread(() ->
            {
                StringBuilder out = new StringBuilder();

                for (int line = 0; line < table.getRowCount(); line++)
                {
                    out.append(" [").append(table.getValueAt(line, 1)).append(']');
                }

                return out.toString();
            });

            assertEquals(captionLines(table).size(), 2, "two groups should have two captions: " + shown);
            assertEquals((int) support.Components.onTheEventThread(() -> table.getRowCount()), 5,
                "the table should hold two captions and three conditions, no line for a word: " + shown);
            assertFalse(shown.contains("[" + I18n.t("route.ui.joinOr") + "]")
                || shown.contains("[" + I18n.t("route.ui.joinAnd") + "]"), "a word has a line of its own: " + shown);

            final String[] captions = new String[2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                captions[0] = frame.conditionCellForTest(1).getText();
                captions[1] = frame.conditionCellForTest(3).getText();
            });

            assertEquals(captions[0], I18n.t("route.ui.conditionCaptionAny"), "the outer group's caption");
            assertEquals(captions[1], I18n.t("route.ui.conditionCaptionAll"), "the nested group's caption");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: clicking a caption offers the two rules, and choosing one changes every word at that group's level and
     * no other; a group that arrived mixed is settled by it; a group of one condition keeps the choice for the next
     * condition Add Condition puts in it.
     *
     * MUTATION: in `ConditionTable.reword`, pass `ConditionOutline.Joiner.AND` for the word, and choosing "At least
     * one" changes nothing.
     */
    @Test
    public void testChoosingACaptionsRuleChangesEveryWordAtItsLevel() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23 0AND 0:24"));

            final javax.swing.JTable table = conditionTable(frame);

            chooseRule(table, 0, "route.ui.conditionCaptionAny");

            assertEquals(levels(frame), " 0:21 0OR 1:22 1OR 1:23 0OR 0:24",
                "choosing At least one on the outer caption should make both of its words 'or', the nested one kept");

            setRows(frame, fromLevels(" 0:21 0OR 0:22 0AND 0:23"));

            chooseRule(table, 0, "route.ui.conditionCaptionAll");

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23", "choosing All on a mixed group");
            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "a mixed group is still flagged after its rule was chosen: " + levels(frame));

            setRows(frame, fromLevels(" 0:21"));

            chooseRule(table, 0, "route.ui.conditionCaptionAny");

            assertEquals(captionText(table, 0), I18n.t("route.ui.conditionCaptionAny"),
                "the caption of a group of one condition did not take the rule chosen for it");

            javax.swing.SwingUtilities.invokeAndWait(table::clearSelection);

            press(frame, "route.ui.menuAddCondition");

            assertEquals(levels(frame), " 0:21 0OR 0:1",
                "the rule chosen for a group of one condition is not the word the next one joins by");
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    /** The text of a caption, top to bottom, as it is painted. */
    private static String captionText(final javax.swing.JTable table, int caption)
    {
        return support.RouteEditing.captionText(table, caption);
    }

    /** Clicks the caption on a caption line and chooses one of its rules from the menu that opens. */
    private static void chooseRule(final javax.swing.JTable table, int caption, String key) throws Exception
    {
        support.RouteEditing.chooseRule(table, caption, key);
    }

    /**
     * FR-161: Undo (Ctrl+Z or its button) takes back each edit to the conditions in turn - a group made, an address
     * typed, a condition added, one deleted, one moved - and Redo (Ctrl+Y or its button) makes them again.  A new edit
     * after an Undo leaves nothing to redo, and a route loaded has nothing to undo; the buttons are grayed when there is
     * nothing for them to do.
     *
     * MUTATION: in `ConditionTable.showChanges`, drop the `remember(before)` call, and Undo has nothing to take back.
     * MUTATION: in `History.step`, drop the push of `now` onto the other side, and Redo has nothing to make again.
     * MUTATION: in the conditions' `setValueAt`, drop the return for a line built as it was, and Undo is offered.
     */
    @Test
    public void testUndoAndRedoWalkThroughEveryEdit() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));

            final javax.swing.JTable table = conditionTable(frame);
            final java.util.List<String> seen = new java.util.ArrayList<>();

            assertFalse(enabled(frame, "route.ui.menuUndo"), "Undo is offered on a route just loaded");
            assertFalse(enabled(frame, "route.ui.menuRedo"), "Redo is offered on a route just loaded");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                table.getModel().setValueAt("21", (Integer) call(table, "lineOf", 0), ADDRESS));

            assertFalse(enabled(frame, "route.ui.menuUndo"), "an address committed as it was is an edit to undo");

            seen.add(levels(frame));

            selectLines(table, 2, 4);
            press(frame, "route.ui.menuGroupSelected");
            seen.add(levels(frame));

            javax.swing.SwingUtilities.invokeAndWait(() ->
                table.getModel().setValueAt("31", (Integer) call(table, "lineOf", 0), ADDRESS));
            seen.add(levels(frame));

            clickCondition(table, 0, NOTHING_TO_EDIT, 0);
            press(frame, "route.ui.menuAddCondition");
            seen.add(levels(frame));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.deleteConditionForTest(0));
            seen.add(levels(frame));

            clickCondition(table, 0, NOTHING_TO_EDIT, 0);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK);
            seen.add(levels(frame));

            for (int at = 1; at < seen.size(); at++)
            {
                assertNotEquals(seen.get(at), seen.get(at - 1), "edit " + at + " changed nothing: " + seen);
            }

            assertTrue(enabled(frame, "route.ui.menuUndo"), "Undo is grayed after " + (seen.size() - 1) + " edits");

            walk(seen, true, "outline", () -> levels(frame), key -> press(frame, key), table);

            assertFalse(enabled(frame, "route.ui.menuUndo"), "Undo is offered with every edit taken back");
            assertTrue(enabled(frame, "route.ui.menuRedo"), "Redo is grayed with every edit taken back");

            walk(seen, false, "outline", () -> levels(frame), key -> press(frame, key), table);

            assertFalse(enabled(frame, "route.ui.menuRedo"), "Redo is offered with every edit made again");

            ctrl(table, java.awt.event.KeyEvent.VK_Z);
            ctrl(table, java.awt.event.KeyEvent.VK_Z);

            assertTrue(enabled(frame, "route.ui.menuRedo"), "Redo is grayed after two Undos");

            press(frame, "route.ui.menuAddCondition");

            assertFalse(enabled(frame, "route.ui.menuRedo"), "Redo is offered after a new edit");
            assertTrue(enabled(frame, "route.ui.menuUndo"), "Undo is grayed after a new edit");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame.setConditionRowsForTest(fromLevels(" 0:21 0AND 0:22")));

            assertFalse(enabled(frame, "route.ui.menuUndo"), "Undo is offered on a route just loaded after edits");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * Undo back through every edit `seen` holds, or Redo forward through them: by the button on even steps and the key
     * on odd ones, each step leaving the state `seen` holds before (or after) that edit.
     */
    private static void walk(java.util.List<String> seen, boolean back, String what,
        java.util.function.Supplier<String> state, java.util.function.Consumer<String> button, javax.swing.JTable table)
        throws Exception
    {
        for (int step = 0; step < seen.size() - 1; step++)
        {
            int at = back ? seen.size() - 2 - step : step + 1;

            if (at % 2 == 0) button.accept(back ? "route.ui.menuUndo" : "route.ui.menuRedo");
            else ctrl(table, back ? java.awt.event.KeyEvent.VK_Z : java.awt.event.KeyEvent.VK_Y);

            assertEquals(state.get(), seen.get(at), back ? "Undo back to the " + what + " before edit " + (at + 1)
                : "Redo of edit " + at);
        }
    }

    /** Ctrl and a letter, pressed on the table as the keyboard sends it: see `support.Keys.chord`. */
    private static void ctrl(final javax.swing.JTable table, final int letter) throws Exception
    {
        support.Keys.chord(table, letter, java.awt.event.InputEvent.CTRL_DOWN_MASK);
    }

    /**
     * MT-868: the trash on a line inside a selection of several deletes every selected condition, as one edit that one
     * Undo brings back; the trash on a line outside the selection deletes that line alone, and with Shift or Ctrl held
     * it only selects.  The Delete key deletes the
     * selection too, but not while a cell is being edited.
     *
     * MUTATION: in `HandledTable.changeSelection`, drop `|| isMark(column)`, and the press selects its line alone,
     * so the trash on 22 deletes 22 only.
     * MUTATION: in `HandledTable.deleteSelected`, return at once, and the Delete key deletes nothing.
     * MUTATION: in `actOnRowMarks`, drop the Shift-or-Ctrl return, and a Shift press on the trash deletes the range.
     */
    @Test
    public void testTheTrashAndTheDeleteKeyTakeTheWholeSelection() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23 0AND 0:24"));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 0, 2);
            clickCondition(table, 2, DELETE_MARK, 0);

            assertEquals(levels(frame), " 0:23 0AND 0:24", "the trash on 22, with 21 and 22 selected");

            press(frame, "route.ui.menuUndo");

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23 0AND 0:24", "one Undo after the trash");

            selectLines(table, 0, 2);
            clickCondition(table, 6, DELETE_MARK, 0);

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23", "the trash on 24, outside the selection");

            selectLines(table, 0, 0);
            clickCondition(table, 4, DELETE_MARK, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
            clickCondition(table, 4, DELETE_MARK, java.awt.event.InputEvent.CTRL_DOWN_MASK);

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23", "the trash pressed with Shift, then Ctrl");

            selectLines(table, 2, 4);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DELETE);

            assertEquals(levels(frame), " 0:21", "the Delete key, with 22 and 23 selected");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame.setConditionRowsForTest(fromLevels(" 0:21 0AND 0:22")));

            selectLines(table, 0, 2);
            javax.swing.SwingUtilities.invokeAndWait(() -> table.editCellAt((Integer) call(table, "lineOf", 0), ADDRESS));
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DELETE);

            assertEquals(levels(frame), " 0:21 0AND 0:22", "the Delete key while an address is being edited");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * MT-868: Ungroup lifts every nested group a selected condition is in, as one edit: conditions in two sibling
     * groups lift both, and a condition at the top level beside a group's conditions lifts that group.  A group inside
     * another one selected stays a group.
     *
     * MUTATION: in `ConditionOutline.ungroupables`, return only the first group, and of two sibling groups only the
     * first is lifted.
     * MUTATION: in `ConditionOutline.ungroupables`, add every touched group, and lifting an outer group picked by its
     * caption lifts its inner group too.
     */
    @Test
    public void testUngroupLiftsEveryGroupTheSelectionTouches() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final String twoGroups = " 0:21 0AND 1:22 1OR 1:23 0AND 1:24 1OR 1:25";

            showRows(frame, fromLevels(twoGroups));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 2, 6);
            press(frame, "route.ui.menuUngroup");

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23 0AND 0:24 0AND 0:25",
                "Ungroup with 22, 23 and 24 selected, in two groups");

            press(frame, "route.ui.menuUndo");

            assertEquals(levels(frame), twoGroups, "one Undo after Ungroup of two groups");

            selectLines(table, 0, 4);
            press(frame, "route.ui.menuUngroup");

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23 0AND 1:24 1OR 1:25",
                "Ungroup with 21, 22 and 23 selected: 21 is in no group to lift");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame.setConditionRowsForTest(fromLevels(" 0:21 0AND 1:22 1OR 2:23 2AND 2:24")));

            selectLines(table, 2, 6);
            press(frame, "route.ui.menuUngroup");

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 1:23 1AND 1:24",
                "Ungroup with all of (22 or (23 and 24)) selected keeps (23 and 24) a group");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * MT-868: the row of actions under the conditions sits at the foot of their frame, so adding or deleting a condition
     * leaves it where it was.
     *
     * MUTATION: in `footed`, add `foot` to `framed` at `BorderLayout.NORTH`, and the row is not at the frame's foot.
     */
    @Test
    public void testTheActionsStayAtTheFootOfTheFrame() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22"));

            final javax.swing.JTable table = conditionTable(frame);
            final javax.swing.AbstractButton add = notOnTheCommands(frame, "route.ui.menuAddCondition");
            final java.util.concurrent.Callable<int[]> where = footOf(frame, table, add);

            final int[] two = support.Components.onTheEventThread(where);

            assertEquals(two[1], two[2], "with two conditions, the row of actions does not end at the frame's foot");

            press(frame, "route.ui.menuAddCondition");
            press(frame, "route.ui.menuAddCondition");
            press(frame, "route.ui.menuAddCondition");

            final int[] five = support.Components.onTheEventThread(where);

            assertEquals(five[0], two[0], "Add Condition moved when three conditions were added");

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.deleteConditionForTest(0));

            assertEquals(support.Components.onTheEventThread(where)[0], two[0],
                "Add Condition moved when a condition was deleted");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: a new condition added to a complex one, then moved to the top.  "21 and (22 or (23 and 24) or
     * 25) and 26" with Add Condition, nothing selected, reads "... and 1"; Alt+Up takes 1 to the top a step at a time,
     * past the group whole rather than into it, the meaning the same at every step, and no further.  Undo puts it back
     * at the end, and a drag by its handle to the gap above 21 takes it to the top in one go.
     *
     * MUTATION: in `ConditionOutline.plan`, skip the group-beside branch (`if (false)`), and 1 does not move past the
     * group as a whole.
     */
    @Test
    public void testANewConditionAddedToAComplexOneMovesToTheTop() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 2:23 2AND 2:24 1OR 1:25 0AND 0:26"));

            final javax.swing.JTable table = conditionTable(frame);
            final org.traincontrol.base.NodeExpression meant = both(both(both(on(21),
                either(either(on(22), both(on(23), on(24))), on(25))), on(26)), on(1));

            press(frame, "route.ui.menuAddCondition");

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 2:23 2AND 2:24 1OR 1:25 0AND 0:26 0AND 0:1",
                "Add Condition with nothing selected");
            assertEquivalent(heldBy(frame), meant, null, "after Add Condition");

            final String[] steps = {
                " 0:21 0AND 1:22 1OR 2:23 2AND 2:24 1OR 1:25 0AND 0:1 0AND 0:26",
                " 0:21 0AND 0:1 0AND 1:22 1OR 2:23 2AND 2:24 1OR 1:25 0AND 0:26",
                " 0:1 0AND 0:21 0AND 1:22 1OR 2:23 2AND 2:24 1OR 1:25 0AND 0:26",
                " 0:1 0AND 0:21 0AND 1:22 1OR 2:23 2AND 2:24 1OR 1:25 0AND 0:26"};

            for (int at = 0; at < steps.length; at++)
            {
                support.Keys.chord(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);

                assertEquals(levels(frame), steps[at], "Alt+Up " + (at + 1) + " on the new condition");
                assertEquivalent(heldBy(frame), meant, null, "after Alt+Up " + (at + 1));
            }

            press(frame, "route.ui.menuUndo");
            press(frame, "route.ui.menuUndo");
            press(frame, "route.ui.menuUndo");

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 2:23 2AND 2:24 1OR 1:25 0AND 0:26 0AND 0:1",
                "three Undos after the moves");

            final int top = support.Components.onTheEventThread(() ->
                table.getCellRect((Integer) call(table, "lineOf", 0), 1, true).y + 2);

            // Alone: a press on a handle inside a selection of several drags them all.
            clickCondition(table, 12, NOTHING_TO_EDIT, 0);
            drag(table, 12, new java.awt.Point(8, top));

            assertEquals(levels(frame), steps[2], "the new condition dragged to the gap above 21");
            assertEquivalent(heldBy(frame), meant, null, "after the drag");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: "a or b or c or d" made "(a and b) or (c and d)", both ways the editor offers.  Group Selected
     * on a and b, then "All of these must be true" on its caption, and the same for c and d; or "All of these must be
     * true" on the outer caption, then Split Here at c.  Both give the same outline and the same truth table, and Undo
     * walks the second way back to where it began.
     *
     * MUTATION: in `ConditionOutline.split`, join the two parts by `word` rather than `opposite(word)`, and Split Here
     * gives "(a and b) and (c and d)".
     */
    @Test
    public void testAOrBOrCOrDBecomesTwoAndGroups() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            final String anyOfFour = " 0:21 0OR 0:22 0OR 0:23 0OR 0:24";
            final String pairs = " 1:21 1AND 1:22 0OR 1:23 1AND 1:24";
            final org.traincontrol.base.NodeExpression meant = either(both(on(21), on(22)), both(on(23), on(24)));

            showRows(frame, fromLevels(anyOfFour));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 0, 2);
            press(frame, "route.ui.menuGroupSelected");
            chooseRule(table, 1, "route.ui.conditionCaptionAll");

            assertEquals(levels(frame), " 1:21 1AND 1:22 0OR 0:23 0OR 0:24", "a and b grouped, then made 'all'");

            selectLines(table, 4, 6);
            press(frame, "route.ui.menuGroupSelected");
            chooseRule(table, 2, "route.ui.conditionCaptionAll");

            assertEquals(levels(frame), pairs, "c and d grouped, then made 'all'");
            assertEquivalent(heldBy(frame), meant, null, "by Group Selected and the captions");

            setRows(frame, fromLevels(anyOfFour));

            chooseRule(table, 0, "route.ui.conditionCaptionAll");

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23 0AND 0:24", "the outer caption made 'all'");

            clickCondition(table, 4, NOTHING_TO_EDIT, 0);
            press(frame, "route.ui.menuSplitHere");

            assertEquals(levels(frame), pairs, "Split Here at c");
            assertEquivalent(heldBy(frame), meant, null, "by the outer caption and Split Here");

            press(frame, "route.ui.menuUndo");
            press(frame, "route.ui.menuUndo");

            assertEquals(levels(frame), anyOfFour, "two Undos after the caption and Split Here");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * MT-868: Alt+Down and Alt+Up move every selected condition, together, and they stay selected; where the first
     * has nowhere to go, none moves.  A whole group selected moves past its neighbor as one, as a drag moves it, and
     * does not merge into it.
     *
     * MUTATION: in `ConditionTable.shiftChosen`, move only `chosen.first()`, and Alt+Down on 21 and 22 moves 21 alone.
     * MUTATION: in `ConditionOutline.movedTogether`, drop the return through `movedByPieces`, and the group (23 or 24)
     * merges into (21 or 22).
     */
    @Test
    public void testAltUpAndDownMoveTheWholeSelection() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23 0AND 0:24"));

            final javax.swing.JTable table = conditionTable(frame);

            selectLines(table, 0, 2);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 0:23 0AND 0:21 0AND 0:22 0AND 0:24", "Alt+Down on 21 and 22");
            assertEquals(selectedRows(table), "[2, 4]", "21 and 22 stay selected");

            support.Keys.press(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23 0AND 0:24",
                "Alt+Up twice on 21 and 22: back, then nowhere to go");
            assertEquals(selectedRows(table), "[0, 2]", "21 and 22 stay selected at the top");

            setRows(frame, fromLevels(" 1:21 1OR 1:22 0AND 1:23 1OR 1:24"));

            selectLines(table, 4, 6);
            support.Keys.chord(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 1:23 1OR 1:24 0AND 1:21 1OR 1:22",
                "Alt+Up on the whole group (23 or 24) should move it past (21 or 22), not into it; Reads as: "
                + plainReading(frame));

            support.Keys.chord(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(levels(frame), " 1:23 1OR 1:24 0AND 1:21 1OR 1:22", "and at the top it goes nowhere");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: Group Selected applies to two or more conditions that are a run of one group and not all of it, and
     * wraps them in a group with the rule they already had, so the conditions mean the same (by truth table) and the
     * new caption names the parent's rule; for one condition, or a whole group, it is grayed.  Ctrl+G does the same.
     *
     * MUTATION: in `ConditionOutline.run`, drop `|| to - from + 1 == items.size()`, and Group Selected is offered on
     * a whole group.
     * MUTATION: in `ConditionOutline.grouped`, join the wrapped items by `opposite(word)`, and "21 and (22 or 23)"
     * no longer means "21 and 22 and 23".
     */
    @Test
    public void testGroupSelectedKeepsTheMeaning() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));

            final javax.swing.JTable table = conditionTable(frame);

            assertFalse(enabled(frame, "route.ui.menuGroupSelected"), "Group Selected is offered with nothing selected");

            clickCondition(table, 2, NOTHING_TO_EDIT, 0);

            assertFalse(enabled(frame, "route.ui.menuGroupSelected"), "Group Selected is offered for one condition");

            selectLines(table, 0, 4);

            assertFalse(enabled(frame, "route.ui.menuGroupSelected"), "Group Selected is offered for the whole group");

            org.traincontrol.base.NodeExpression before =
                org.traincontrol.base.ConditionOutline.toExpression(frame.conditionRowsForTest());

            selectLines(table, 2, 4);
            press(frame, "route.ui.menuGroupSelected");

            assertEquals(levels(frame), " 0:21 0AND 1:22 1AND 1:23", "Group Selected on 22 and 23 of an 'and' group");
            assertEquals(selectedRows(table), "[2, 4]", "the grouped conditions stay selected");
            assertEquals(captionText(table, 1), I18n.t("route.ui.conditionCaptionAll"),
                "the new group's caption names its parent's rule");
            assertEquivalent(org.traincontrol.base.ConditionOutline.toExpression(frame.conditionRowsForTest()), before,
                null, "Group Selected on 22 and 23 of 21 and 22 and 23");

            setRows(frame, fromLevels(" 0:21 0OR 0:22 0OR 0:23"));

            final org.traincontrol.base.NodeExpression either =
                org.traincontrol.base.ConditionOutline.toExpression(frame.conditionRowsForTest());

            selectLines(table, 0, 2);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_DOWN_MASK);

            assertEquals(levels(frame), " 1:21 1OR 1:22 0OR 0:23", "Ctrl+G on 21 and 22 of an 'or' group");
            assertEquals(captionText(table, 1), I18n.t("route.ui.conditionCaptionAny"),
                "the new group's caption names its parent's rule");
            assertEquivalent(org.traincontrol.base.ConditionOutline.toExpression(frame.conditionRowsForTest()), either,
                null, "Ctrl+G on 21 and 22 of 21 or 22 or 23");

            chooseRule(table, 1, "route.ui.conditionCaptionAll");

            assertEquals(levels(frame), " 1:21 1AND 1:22 0OR 0:23", "only the caption makes it an 'and' group");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: Ungroup lifts the innermost group holding the selection into its parent, its conditions joined by the
     * parent's word; at the outermost level it is grayed.  Ctrl+Shift+G does the same.
     *
     * MUTATION: in `ConditionTable.ungroupSelected`, pass `lifted.getWord()` for the word, and the outline comes out
     * mixed.
     */
    @Test
    public void testUngroupLiftsIntoTheParentWithItsWord() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0OR 1:22 1AND 2:23 2OR 2:24 1AND 1:25"));

            final javax.swing.JTable table = conditionTable(frame);

            clickCondition(table, 0, NOTHING_TO_EDIT, 0);

            assertFalse(enabled(frame, "route.ui.menuUngroup"), "Ungroup is offered at the outermost level");

            clickCondition(table, 6, NOTHING_TO_EDIT, 0);
            press(frame, "route.ui.menuUngroup");

            assertEquals(levels(frame), " 0:21 0OR 1:22 1AND 1:23 1AND 1:24 1AND 1:25",
                "Ungroup on 24 should lift (23 or 24) into its 'and' group");
            assertEquals(selectedRows(table), "[4, 6]", "the lifted conditions are selected");

            support.Keys.press(table, java.awt.event.KeyEvent.VK_G,
                java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);

            assertEquals(levels(frame), " 0:21 0OR 0:22 0OR 0:23 0OR 0:24 0OR 0:25",
                "Ctrl+Shift+G should lift the 'and' group into the outer 'or'");
            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "Ungroup left a mixed level: " + levels(frame));
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: a condition dragged by its handle into a nested group's outline joins that group, taking its word; at a
     * gap that ends groups at several depths, the pointer's x picks which: at the handle the outermost, over the
     * nested outline's columns the nested group.
     *
     * MUTATION: in `ConditionTable.dropInto`, pass `ConditionOutline.Joiner.AND` for the word, and the drop into the
     * 'or' group makes it mixed.
     */
    @Test
    public void testADragIntoANestedGroupTakesItsWord() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23 0AND 0:24"));

            final javax.swing.JTable table = conditionTable(frame);

            java.awt.Rectangle under23 = support.Components.onTheEventThread(() ->
                table.getCellRect(screen(table, 4), ADDRESS, true));

            drag(table, 6, new java.awt.Point(under23.x + 10, under23.y + 3));

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 1:24 1OR 1:23",
                "24 dragged above 23 should join the 'or' group, joined by 'or'");
            assertEquals(selectedRows(table), "[4]", "the dragged condition stays selected");

            setRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23"));

            final java.awt.Rectangle last = support.Components.onTheEventThread(() ->
                table.getCellRect(table.getRowCount() - 1, ADDRESS, true));

            drag(table, 0, new java.awt.Point(8, last.y + last.height - 2));

            assertEquals(levels(frame), " 1:22 1OR 1:23 0AND 0:21",
                "21 dropped below the last line at the handle should land at the end of the outer group");

            setRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23"));

            drag(table, 0, new java.awt.Point(last.x + 10, last.y + last.height - 2));

            assertEquals(levels(frame), " 1:22 1OR 1:23 1OR 1:21",
                "21 dropped below the last line over the nested outline should land at the end of the nested group");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * OB-396: a press on a handle inside a selection of several keeps them for a drag, which takes them all; let go
     * without a drag, it was a click, and the line is selected alone, so a Shift-click then reaches from it.
     *
     * MUTATION: in `HandledTable.released`, drop the `changeSelection` of the clicked line, and the click leaves all
     * three selected.
     */
    @Test
    public void testAClickOnAHandleInASelectionSelectsItsLineAlone() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));

            final javax.swing.JTable table = conditionTable(frame);

            clickCondition(table, 0, HANDLE, 0);
            clickCondition(table, 4, HANDLE, java.awt.event.InputEvent.SHIFT_DOWN_MASK);

            assertEquals(selectedRows(table), "[0, 2, 4]", "precondition: Shift-click selects 21 to 23");

            clickCondition(table, 2, HANDLE, 0);

            assertEquals(selectedRows(table), "[2]", "a click on 22's handle should select 22 alone");

            clickCondition(table, 4, HANDLE, java.awt.event.InputEvent.SHIFT_DOWN_MASK);

            assertEquals(selectedRows(table), "[2, 4]", "Shift-click on 23 should reach from 22");

            final java.awt.Rectangle last = support.Components.onTheEventThread(() ->
                table.getCellRect(table.getRowCount() - 1, ADDRESS, true));

            clickCondition(table, 0, HANDLE, 0);
            clickCondition(table, 2, HANDLE, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
            drag(table, 2, new java.awt.Point(8, last.y + last.height - 2));

            assertEquals(levels(frame), " 0:23 0AND 0:21 0AND 0:22", "a drag from 22 inside the selection takes 21 too");
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * FR-162: the commands have the conditions' handles.  A grip with its tooltip at the left of every row, and no
     * arrows; a selection painted on the row's own cells, never the handle or the marks; a drag by a handle in a
     * selection moves every selected row to where it is let go, and one outside it moves its own row, each left
     * selected; and the trash on a row in a selection deletes the selection.
     *
     * MUTATION: in `CommandTable.chosenRows`, answer only `getSelectedRow()`, and the drag from 12 in the selection of
     * 12 and 13 moves 12 alone.
     */
    @Test
    public void testCommandsMoveByTheirHandles() throws Exception
    {
        inAnEditor(frame ->
        {
            final javax.swing.JTable table = showCommands(frame, 11, 12, 13, 14);

            final javax.swing.JLabel handle = support.Components.onTheEventThread(() ->
                (javax.swing.JLabel) frame.commandCellForTest(0, HANDLE));

            assertNotNull(handle.getIcon(), "a command row has no grip in its handle");
            assertEquals(handle.getToolTipText(), I18n.t("route.ui.tooltipCommandHandle"), "the command handle's tooltip");

            selectLines(table, 1, 2);

            // Per column of a selected row: + where it holds a mark of the selection, an arrow's name where it holds one.
            String painted = support.Components.onTheEventThread(() ->
            {
                StringBuilder out = new StringBuilder();

                for (int column = 0; column < table.getColumnCount(); column++)
                {
                    Object value = table.getValueAt(1, column);
                    boolean arrow = org.traincontrol.gui.RouteEditorFrame.markMoveUp().equals(value)
                        || org.traincontrol.gui.RouteEditorFrame.markMoveDown().equals(value);

                    out.append(arrow ? value : table.isCellSelected(1, column) ? "+" : "-");
                }

                return out.toString();
            });

            assertEquals(painted, "-+++++++--", "a selected command is painted from # to Delay, never on the handle, the"
                + " trash or the copy mark, and has no arrows");

            final java.awt.Rectangle last = support.Components.onTheEventThread(() -> table.getCellRect(3, 1, true));

            drag(table, 1, new java.awt.Point(60, last.y + last.height - 2));

            assertEquals(state(frame, table), " 11 14 12 13 [2, 3]", "12 and 13, dragged from 12's handle below 14,"
                + " should land there and stay selected");

            drag(table, 0, new java.awt.Point(60, last.y + 3));

            assertEquals(state(frame, table), " 14 12 11 13 [2]", "11, outside the selection, dragged above 13, should"
                + " land there alone and be selected");

            selectLines(table, 0, 1);
            clickCell(table, 1, COMMAND_TRASH, 0);

            assertEquals(targets(frame), " 11 13", "the trash on 12 in the selection of 14 and 12 should delete both");
        });
    }

    /**
     * FR-162: Duplicate in the commands' menu copies every selected command directly below the last of them, as one
     * run in their order, and selects the copies; so does the copy mark on a row in the selection, and on a row
     * outside it the mark copies that row alone, directly under it.
     *
     * MUTATION: in `CommandTable.isMark`, answer `column == DELETE` alone, and the copy mark on 12 selects 12 alone, so
     * only it is copied.
     */
    @Test
    public void testDuplicateCopiesTheSelectedCommands() throws Exception
    {
        inAnEditor(frame ->
        {
            final javax.swing.JTable table = showCommands(frame, 11, 12, 13, 14);

            selectLines(table, 0, 1);
            clickCondition(table, 3, NOTHING_TO_EDIT, java.awt.event.InputEvent.CTRL_DOWN_MASK);

            assertEquals(selectedRows(table), "[0, 1, 3]", "precondition: Ctrl-click adds 14 to 11 and 12");

            rightClickAndChoose(table, 3, "route.ui.menuDuplicate");

            assertEquals(state(frame, table), " 11 12 13 14 11 12 14 [4, 5, 6]", "Duplicate on 11, 12 and 14 should"
                + " copy them below 14, in their order, and select the copies");

            clickCell(table, 5, COMMAND_COPY, 0);

            assertEquals(state(frame, table), " 11 12 13 14 11 12 14 11 12 14 [7, 8, 9]", "the copy mark on 12 in the"
                + " selection should copy the selection below it");

            clickCell(table, 0, COMMAND_COPY, 0);

            assertEquals(state(frame, table), " 11 11 12 13 14 11 12 14 11 12 14 [1]", "the copy mark on 11, outside"
                + " the selection, should copy 11 alone, under it");
        });
    }

    /**
     * FR-162: a right-click on a command offers Insert Command Above and Below, Move Up, Move Down, Duplicate and
     * Delete, each enabled exactly where it does something; Insert puts a new command beside the row and selects it,
     * and the moves take the selection together and leave it selected.  Below the rows it offers Add Command.
     *
     * MUTATION: in `CommandTable.canShiftChosen`, answer `!locked && !chosen.isEmpty()`, and Move Up is offered on the
     * first command.
     * MUTATION: in `offerRowMenu`, drop the divider after Duplicate, and Duplicate shares Delete's (FR-163: Duplicate
     * between dividers of its own).
     */
    @Test
    public void testTheCommandMenuOffersWhatEachRowCanDo() throws Exception
    {
        inAnEditor(frame ->
        {
            final javax.swing.JTable table = showCommands(frame, 11, 12, 13);

            javax.swing.JPopupMenu menu = rightClickBelow(table);

            assertNotNull(menu, "a right-click below the commands opened no menu");
            assertEquals(offered(menu), "+route.ui.menuAddCommand", "the menu below the commands");

            support.Mouse.choose(item(menu, "route.ui.menuAddCommand"));

            assertEquals(targets(frame), " 11 12 13 _", "Add Command adds one at the end");

            String first = offered(rightClickCell(table, 0, NOTHING_TO_EDIT));

            support.Mouse.closeMenus();

            menu = rightClickCell(table, 3, NOTHING_TO_EDIT);

            assertEquals(first + " / " + offered(menu), "+route.ui.menuInsertCommandAbove"
                + " +route.ui.menuInsertCommandBelow | -route.ui.menuMoveUp +route.ui.menuMoveDown | +route.ui.menuDuplicate"
                + " | +ui.delete / +route.ui.menuInsertCommandAbove +route.ui.menuInsertCommandBelow | +route.ui.menuMoveUp"
                + " -route.ui.menuMoveDown | +route.ui.menuDuplicate | +ui.delete", "the menus on the first command and the"
                + " last, enabled (+) or not (-), their dividers as |");

            support.Mouse.choose(item(menu, "ui.delete"));
            rightClickAndChoose(table, 1, "route.ui.menuInsertCommandAbove");

            assertEquals(state(frame, table), " 11 _ 12 13 [1]", "Delete takes the command right-clicked; Insert"
                + " Command Above puts a new command above 12, selected");

            rightClickAndChoose(table, 2, "route.ui.menuInsertCommandBelow");
            clickCondition(table, 1, NOTHING_TO_EDIT, java.awt.event.InputEvent.CTRL_DOWN_MASK);

            assertEquals(state(frame, table), " 11 _ 12 _ 13 [1, 3]", "Insert Command Below puts a new command under"
                + " 12, selected");

            rightClickAndChoose(table, 3, "ui.delete");
            selectLines(table, 1, 2);
            rightClickAndChoose(table, 2, "route.ui.menuMoveUp");

            assertEquals(state(frame, table), " 12 13 11 [0, 1]", "Delete inside the selection takes both new"
                + " commands; Move Up on 12 and 13 moves both above 11, still selected");

            menu = rightClickCell(table, 1, NOTHING_TO_EDIT);

            assertTrue(offered(menu).contains("-route.ui.menuMoveUp"), "Move Up is offered with the selection at the top: "
                + offered(menu));

            support.Mouse.choose(item(menu, "route.ui.menuMoveDown"));

            assertEquals(state(frame, table), " 11 12 13 [1, 2]", "Move Down on 12 and 13 should move both below 11");
        });
    }

    /**
     * FR-162: Duplicate in the conditions' menu copies the selected conditions directly below the last of them, in
     * that one's group and joined by its word, selects the copies, and Undo takes them back.  A whole group selected is
     * copied as a group, as a move takes it whole.
     *
     * MUTATION: in `ConditionOutline.duplicated`, add the copies at the end of `into`, and 21 and 22 copied land after
     * 23.
     */
    @Test
    public void testDuplicateCopiesTheSelectedConditions() throws Exception
    {
        inAnEditor(frame ->
        {
            showRows(frame, fromLevels(" 0:21"));

            final javax.swing.JTable table = conditionTable(frame);

            assertEquals(duplicated(frame, table, " 0:21 0AND 0:22 0AND 0:23", 0, 2) + selectedRows(table),
                " 0:21 0AND 0:22 0AND 0:21 0AND 0:22 0AND 0:23[4, 6]", "Duplicate on 21 and 22 should copy both below"
                + " 22, in order, and select the copies");

            ctrl(table, java.awt.event.KeyEvent.VK_Z);

            assertEquals(levels(frame), " 0:21 0AND 0:22 0AND 0:23", "Undo takes the copies back");
            assertEquals(duplicated(frame, table, " 1:21 1OR 1:22 0AND 0:23", 0, 2),
                " 1:21 1OR 1:22 0AND 1:21 1OR 1:22 0AND 0:23", "the whole group (21 or 22) should be copied as a group,"
                + " below it");
            assertEquals(duplicated(frame, table, " 0:21 0AND 1:22 1OR 1:23", 2, 2), " 0:21 0AND 1:22 1OR 1:22 1OR 1:23",
                "22 should be copied into its 'or' group, under it, joined by 'or'");
        });
    }

    /**
     * FR-163: on the commands, as on the conditions, Alt+Down and Alt+Up move every selected command one place and leave
     * them selected, and none moves past the end; the Delete key removes every selected command, but not while a cell
     * is being edited, where Delete is the cell's own.
     *
     * MUTATION: in `HandledTable.listen`, drop the `alt DOWN` binding, and Alt+Down moves no command.
     * MUTATION: in `HandledTable.deleteSelected`, return at once, and the Delete key removes no command.
     */
    @Test
    public void testAltArrowsAndTheDeleteKeyActOnTheSelectedCommands() throws Exception
    {
        inAnEditor(frame ->
        {
            final javax.swing.JTable table = showCommands(frame, 11, 12, 13, 14);

            selectLines(table, 1, 2);
            support.Keys.chord(table, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(state(frame, table), " 11 14 12 13 [2, 3]", "Alt+Down with 12 and 13 selected");

            for (int up = 0; up < 3; up++)
            {
                support.Keys.chord(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);
            }

            assertEquals(state(frame, table), " 12 13 11 14 [0, 1]", "Alt+Up three times: twice to the top, and"
                + " the third moves nothing");

            selectLines(table, 1, 2);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DELETE);

            assertEquals(state(frame, table), " 12 14 [1]", "the Delete key with 13 and 11 selected");

            javax.swing.SwingUtilities.invokeAndWait(() -> table.editCellAt(0, COMMAND_TARGET));
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DELETE);

            assertEquals(targets(frame), " 12 14", "the Delete key while a target is being edited");
        });
    }

    /**
     * FR-163: Undo (Ctrl+Z on the commands, or the Undo under them) takes back each edit to the commands in turn: a
     * capture, a target typed, a command added by Add Command, one inserted, a drag, Move Up, Alt+Up, Duplicate, the copy
     * mark, the trash, the menu's Delete and the Delete key; Redo (Ctrl+Y, or its button) makes them again.  A route
     * opened, and a target committed as it was, leave nothing to undo; a new edit leaves nothing to redo.  Each table's
     * Undo takes back its own edits only, and the last hundred are kept.
     *
     * MUTATION: in `CommandTable.noteEdit`, drop `history.remember(shown)`, and Undo has nothing to take back.
     * MUTATION: in `Entry.sameAs`, answer false, and a target committed as it was is offered to undo.
     * MUTATION: in `History.remember`, drop the `removeLast` past a hundred, and a hundred Undos leave one more.
     */
    @Test
    public void testUndoAndRedoWalkThroughEveryCommandEdit() throws Exception
    {
        inAnEditor(route(11, 12, 13), frame ->
        {
            final javax.swing.JTable table = showCommands(frame);
            final java.util.List<String> seen = new java.util.ArrayList<>();

            assertEquals(undoAndRedo(frame), "-Undo -Redo", "a route just opened");

            target(table, 0, "11");

            assertEquals(undoAndRedo(frame), "-Undo -Redo", "a target committed as it was");

            seen.add(targets(frame));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.appendCommand(RouteCommand.RouteCommandAccessory(16,
                Accessory.accessoryDecoderType.MM2, true).toLine(null).trim()));
            seen.add(targets(frame));

            target(table, 0, "15");
            seen.add(targets(frame));

            pressOnTheCommands(frame, "route.ui.menuAddCommand");
            seen.add(targets(frame));

            rightClickAndChoose(table, 1, "route.ui.menuInsertCommandBelow");
            seen.add(targets(frame));

            final java.awt.Rectangle last = support.Components.onTheEventThread(() -> table.getCellRect(5, 1, true));

            drag(table, 0, new java.awt.Point(60, last.y + last.height - 2));
            seen.add(targets(frame));

            rightClickAndChoose(table, 5, "route.ui.menuMoveUp");
            seen.add(targets(frame));

            support.Keys.chord(table, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);
            seen.add(targets(frame));

            rightClickAndChoose(table, 0, "route.ui.menuDuplicate");
            seen.add(targets(frame));

            clickCell(table, 3, COMMAND_COPY, 0);
            seen.add(targets(frame));

            clickCell(table, 0, COMMAND_TRASH, 0);
            seen.add(targets(frame));

            rightClickAndChoose(table, 1, "ui.delete");
            seen.add(targets(frame));

            selectLines(table, 1, 1);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_DELETE);
            seen.add(targets(frame));

            for (int at = 1; at < seen.size(); at++)
            {
                assertNotEquals(seen.get(at), seen.get(at - 1), "edit " + at + " changed nothing: " + seen);
            }

            walk(seen, true, "commands", () -> targets(frame), key -> pressOnTheCommands(frame, key), table);

            assertEquals(undoAndRedo(frame), "-Undo +Redo", "every edit taken back");

            walk(seen, false, "commands", () -> targets(frame), key -> pressOnTheCommands(frame, key), table);

            ctrl(table, java.awt.event.KeyEvent.VK_Z);
            target(table, 0, "17");

            assertEquals(undoAndRedo(frame), "+Undo -Redo", "a new edit after an Undo");

            final javax.swing.JTable conditions = conditionTable(frame);

            press(frame, "route.ui.menuAddCondition");
            ctrl(table, java.awt.event.KeyEvent.VK_Z);

            assertEquals(targets(frame) + " " + frame.conditionCount(), seen.get(seen.size() - 2) + " 1",
                "Ctrl+Z on the commands, after a command edit and then a condition added, takes back the command edit");

            ctrl(conditions, java.awt.event.KeyEvent.VK_Z);

            assertEquals(targets(frame) + " " + frame.conditionCount(), seen.get(seen.size() - 2) + " 0",
                "Ctrl+Z on the conditions takes back the condition only");

            for (int edit = 1; edit <= 101; edit++) target(table, 0, String.valueOf(100 + edit));

            for (int undo = 0; undo < 100; undo++) pressOnTheCommands(frame, "route.ui.menuUndo");

            assertEquals(frame.commandRowForTest(0).getTarget() + " " + undoAndRedo(frame), "101 -Undo +Redo",
                "a hundred Undos after 101 edits: back to the first, the edit before it no longer kept");
        });
    }

    /**
     * Adam: ""Reads as" is hidden when there is only one condition, which it shouldn't be.  also, there is still a gap
     * when there are 0 conditions.  The top of the table should snap to the label on those cases."  One condition
     * reads as itself; with none, the heading's line is only as tall as the heading, so the table sits under it.
     *
     * MUTATION: in `readingOfTheConditions`, blank the reading below two conditions again, and one condition reads as
     * nothing; in `HeadingLine.getPreferredSize`, count the reading's height while it is hidden, and the gap is back.
     */
    @Test
    public void testOneConditionReadsAsItselfAndNoneLeavesNoGap() throws Exception
    {
        inAnEditor(frame ->
        {
            java.lang.reflect.Field named = org.traincontrol.gui.RouteEditorFrame.class.getDeclaredField("readsAs");

            named.setAccessible(true);

            final javax.swing.JLabel reads = (javax.swing.JLabel) named.get(frame);

            // The heading's line and, first in it, the heading itself
            int[] heights = support.Components.onTheEventThread(() -> new int[] {
                reads.getParent().getPreferredSize().height,
                reads.getParent().getComponent(0).getPreferredSize().height});

            assertFalse(support.Components.onTheEventThread(reads::isVisible), "with no conditions, the blank reading"
                + " still takes a line");

            assertEquals(heights[0], heights[1], "with no conditions, the heading's line is " + heights[0]
                + " pixels tall and the heading " + heights[1] + ": the table does not sit right under the heading");

            final java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = new java.util.ArrayList<>();

            rows.add(org.traincontrol.base.ConditionOutline.Row.condition(0, RouteCommand.RouteCommandFeedback(5, true)));

            javax.swing.SwingUtilities.invokeAndWait(() -> frame.setConditionRowsForTest(rows));

            assertTrue(support.Components.onTheEventThread(reads::isVisible), "one condition hides the reading");

            assertTrue(frame.readsAsForTest().contains("S88 #5"), "one condition does not read as itself: "
                + frame.readsAsForTest());
        });
    }

    /**
     * Adam: "for the "automatically fire" checkbox, make it not focusable to follow the rest of the UI".
     * No checkbox in the editor takes the keyboard: Automatically Fire and Capture are clicked, not typed into.
     *
     * MUTATION: drop `enabledBox.setFocusable(false)`, and Automatically Fire takes the focus.
     */
    @Test
    public void testNoCheckBoxTakesTheKeyboard() throws Exception
    {
        inAnEditor(frame ->
        {
            java.util.List<javax.swing.JCheckBox> boxes = support.Components.all(frame, javax.swing.JCheckBox.class,
                null);

            assertTrue(boxes.size() >= 2, "precondition: the editor has no Automatically Fire and Capture to ask");

            java.util.List<javax.swing.JCheckBox> focusable = support.Components.all(frame,
                javax.swing.JCheckBox.class, javax.swing.JCheckBox::isFocusable);

            assertTrue(focusable.isEmpty(), "these checkboxes take the keyboard: " + focusable.stream()
                .map(javax.swing.JCheckBox::getText).collect(java.util.stream.Collectors.toList()));
        });
    }

    /**
     * FR-163, Adam: "Anchor the command + to the bottom of its frame, just like the conditions."  Under the commands,
     * inside their frame and above Capture, a row of Add Command, a divider, Undo and Redo, looking as the conditions'
     * row does (the same icons, words, font and flat style); it stays put as commands are added; Undo and Redo are
     * grayed when there is nothing to undo or redo, and the tooltips name Ctrl+Z and Ctrl+Y.
     *
     * MUTATION: in `footed`, add `foot` to `framed` at `BorderLayout.NORTH`, and the row is not at the frame's foot.
     */
    @Test
    public void testTheCommandsActionsSitAtTheFootOfTheirFrame() throws Exception
    {
        inAnEditor(route(11, 12), frame ->
        {
            final javax.swing.JTable table = showCommands(frame);
            final javax.swing.AbstractButton add = commandButton(frame, "route.ui.menuAddCommand");
            final javax.swing.AbstractButton undo = commandButton(frame, "route.ui.menuUndo");
            final javax.swing.AbstractButton redo = commandButton(frame, "route.ui.menuRedo");
            final javax.swing.AbstractButton capture = commandButton(frame, "route.ui.frameCapture");

            String row = support.Components.onTheEventThread(() ->
            {
                StringBuilder out = new StringBuilder();

                for (java.awt.Component part : add.getParent().getComponents())
                {
                    out.append(out.length() == 0 ? "" : " ")
                        .append(part instanceof javax.swing.AbstractButton ? ((javax.swing.AbstractButton) part).getText()
                        : "|");
                }

                return out.toString();
            });

            assertEquals(row, I18n.t("route.ui.menuAddCommand") + " | " + I18n.t("route.ui.menuUndo") + " "
                + I18n.t("route.ui.menuRedo"), "the row under the commands");
            assertEquals(lookOf(add) + " / " + lookOf(undo) + " / " + lookOf(redo),
                lookOf(notOnTheCommands(frame, "route.ui.menuAddCondition")) + " / "
                + lookOf(notOnTheCommands(frame, "route.ui.menuUndo")) + " / "
                + lookOf(notOnTheCommands(frame, "route.ui.menuRedo")), "the commands' row should look as the"
                + " conditions' does");
            assertEquals(undoAndRedo(frame), "-Undo -Redo", "nothing to undo or redo on a route just opened");

            String tips = support.Components.onTheEventThread(() -> add.getToolTipText() + " / "
                + undo.getToolTipText() + " / " + redo.getToolTipText());

            assertEquals(tips, I18n.t("route.ui.tooltipAddCommand") + " / " + I18n.t("route.ui.tooltipUndoCommands")
                + " / " + I18n.t("route.ui.tooltipRedo"), "the tooltips");
            assertTrue(tips.contains("Ctrl+Z") && tips.contains("Ctrl+Y"), "the tooltips name the keys: " + tips);

            final int[] two = support.Components.onTheEventThread(footOf(frame, table, add));
            final int below = support.Components.onTheEventThread(() ->
                javax.swing.SwingUtilities.convertPoint(capture, 0, 0, frame.getRootPane()).y);

            assertEquals(two[1], two[2], "with two commands, the row does not end at the foot of their frame");
            assertTrue(two[3] <= below, "the row ends at " + two[3] + ", below Capture at " + below);

            for (int more = 0; more < 3; more++) pressOnTheCommands(frame, "route.ui.menuAddCommand");

            assertEquals(targets(frame), " 11 12 _ _ _", "Add Command adds a command at the end");
            assertEquals(support.Components.onTheEventThread(footOf(frame, table, add))[0], two[0],
                "Add Command moved when three commands were added");
            assertTrue(frame.offersToAddCommands(), "the commands no longer offer Add Command");
            assertEquals(undoAndRedo(frame), "+Undo -Redo", "after a command was added");
        });
    }

    /**
     * Where a section's row of actions is, through one of its buttons: the button's top in the window, the row's bottom
     * and the bottom inside the frame round the table, and the row's bottom in the window.
     */
    private static java.util.concurrent.Callable<int[]> footOf(final org.traincontrol.gui.RouteEditorFrame frame,
        final javax.swing.JTable table, final javax.swing.AbstractButton button)
    {
        return () ->
        {
            java.awt.Container framed = javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class,
                table).getParent();
            java.awt.Container row = button.getParent();

            return new int[]{javax.swing.SwingUtilities.convertPoint(button, 0, 0, frame.getRootPane()).y,
                javax.swing.SwingUtilities.convertPoint(row, 0, row.getHeight(), framed).y,
                framed.getHeight() - framed.getInsets().bottom,
                javax.swing.SwingUtilities.convertPoint(row, 0, row.getHeight(), frame.getRootPane()).y};
        };
    }

    /**
     * FR-163, Adam: "add the duplicate icon to the condition rows, so the two sections look the same".  A condition's
     * line ends in the trash and then the copy mark, as a command's row does; the copy mark on a condition outside the
     * selection copies it alone, directly below it in its group, and on one in the selection copies the selection as
     * Duplicate does; Undo takes a copy back.
     *
     * MUTATION: in `duplicateRow`, return unless `table == commands`, and the copy mark on a condition copies nothing.
     */
    @Test
    public void testTheCopyMarkOnAConditionCopiesItOrTheSelection() throws Exception
    {
        inAnEditor(frame ->
        {
            final javax.swing.JTable commands = showCommands(frame, 11);

            showRows(frame, fromLevels(" 0:21 0AND 1:22 1OR 1:23"));

            final javax.swing.JTable table = conditionTable(frame);
            final int line = screen(table, 2);

            String marks = support.Components.onTheEventThread(() ->
            {
                StringBuilder out = new StringBuilder();

                for (javax.swing.JTable each : new javax.swing.JTable[]{commands, table})
                {
                    int row = each == table ? line : 0;
                    int columns = each.getColumnCount();

                    for (int column = columns - 2; column < columns; column++)
                    {
                        javax.swing.JLabel cell = (javax.swing.JLabel) each.prepareRenderer(
                            each.getCellRenderer(row, column), row, column);

                        out.append(" ").append(each.getValueAt(row, column)).append(cell.getIcon() != null ? "+" : "-");
                    }
                }

                return out.toString();
            });

            String trash = org.traincontrol.gui.RouteEditorFrame.markDelete();
            String copy = org.traincontrol.gui.RouteEditorFrame.markCopy();

            assertEquals(marks, " " + trash + "+ " + copy + "+ " + trash + "+ " + copy + "+", "the last two columns of"
                + " a command, then of a condition: the mark, + where it draws an icon");

            selectLines(table, 0, 0);
            clickCondition(table, 2, CONDITION_COPY, 0);

            assertEquals(levels(frame) + selectedRows(table), " 0:21 0AND 1:22 1OR 1:22 1OR 1:23[4]", "the copy mark on"
                + " 22, outside the selection of 21: 22 copied under it, in its 'or' group, and selected");

            ctrl(table, java.awt.event.KeyEvent.VK_Z);

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 1:23", "Undo takes the copy back");

            setRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));
            selectLines(table, 0, 2);
            clickCondition(table, 0, CONDITION_COPY, 0);

            assertEquals(levels(frame) + selectedRows(table), " 0:21 0AND 0:22 0AND 0:21 0AND 0:22 0AND 0:23[4, 6]",
                "the copy mark on 21 with 21 and 22 selected copies both below 22, and selects the copies");
        });
    }

    /**
     * FR-163: the route editor's Help names the commands' keys in every language, as it names the conditions': Alt with
     * the arrows, and the Undo and Redo keys the conditions' tooltips name; the commands' Undo tooltip names the same
     * key, and their handle's tooltip the Alt arrows.
     *
     * MUTATION: in messages_de.properties, drop the paragraph about the commands' keys from `route.ui.frameHelp`, and
     * the German Help names none.
     */
    @Test
    public void testTheHelpNamesTheCommandsKeys() throws Exception
    {
        StringBuilder missing = new StringBuilder();

        for (String language : new String[]{"", "_da", "_de", "_es", "_fr", "_it", "_nl", "_pl"})
        {
            java.util.Properties words = bundle(language);
            String undo = keyIn(words.getProperty("route.ui.tooltipUndo"));
            String redo = keyIn(words.getProperty("route.ui.tooltipRedo"));

            // The commands' part: from the first heading (a paragraph in capitals) to the next.
            String commands = "";
            int headings = 0;

            for (String paragraph : words.getProperty("route.ui.frameHelp").split("\n\n"))
            {
                if (paragraph.equals(paragraph.toUpperCase(java.util.Locale.ROOT)) && !paragraph.isEmpty()) headings++;
                else if (headings == 1) commands += paragraph;
            }

            String[][] asked = {{"Help", commands, "Alt+"}, {"Help", commands, undo}, {"Help", commands, redo},
                {"Undo tooltip", words.getProperty("route.ui.tooltipUndoCommands"), undo},
                {"handle tooltip", words.getProperty("route.ui.tooltipCommandHandle"), "Alt+"}};

            for (String[] ask : asked)
            {
                if (!ask[1].contains(ask[2])) missing.append(" messages").append(language).append(": ").append(ask[0])
                    .append(" lacks ").append(ask[2]).append(";");
            }
        }

        assertEquals(missing.toString(), "", "the commands' keys, named where the conditions' are");
    }

    /** The key a tooltip names in its closing brackets: "Ctrl+Z" from "Takes back ... (Ctrl+Z)." */
    private static String keyIn(String tooltip)
    {
        java.util.regex.Matcher key = java.util.regex.Pattern.compile("\\(([^()]+)\\)\\.?$").matcher(tooltip);

        assertTrue(key.find(), "no key in brackets at the end of " + tooltip);

        return key.group(1);
    }

    /** A route of accessory commands at these addresses, ordinary, with no conditions. */
    private static org.traincontrol.base.Route route(int... addresses)
    {
        java.util.List<RouteCommand> commands = new java.util.ArrayList<>();

        for (int address : addresses)
        {
            commands.add(RouteCommand.RouteCommandAccessory(address, Accessory.accessoryDecoderType.MM2, true));
        }

        return new org.traincontrol.marklin.MarklinRoute(null, "Opened", 4243, commands, 0,
            org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);
    }

    /** Types a target into a command's cell and commits it, as closing its editor does (through the model). */
    private static void target(final javax.swing.JTable table, final int row, final String typed) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> table.getModel().setValueAt(typed, row, COMMAND_TARGET));
    }

    /** The button in the commands' section saying this. */
    private static javax.swing.AbstractButton commandButton(final org.traincontrol.gui.RouteEditorFrame frame,
        String key)
    {
        return support.RouteEditing.commandButton(frame, key);
    }

    private static void pressOnTheCommands(final org.traincontrol.gui.RouteEditorFrame frame, String key)
    {
        support.RouteEditing.pressOnTheCommands(frame, key);
    }

    /** The commands' Undo and Redo, each + when it can be pressed and - when grayed: "+Undo -Redo". */
    private static String undoAndRedo(final org.traincontrol.gui.RouteEditorFrame frame)
    {
        javax.swing.AbstractButton undo = commandButton(frame, "route.ui.menuUndo");
        javax.swing.AbstractButton redo = commandButton(frame, "route.ui.menuRedo");

        return support.Components.onTheEventThread(() -> (undo.isEnabled() ? "+" : "-") + "Undo "
            + (redo.isEnabled() ? "+" : "-") + "Redo");
    }

    /** What a flat button looks like: its words' font, its style, its icons' size, and whether it takes the keyboard. */
    private static String lookOf(final javax.swing.AbstractButton button)
    {
        return support.Components.onTheEventThread(() -> button.getFont() + " "
            + button.getClientProperty("JButton.buttonType") + " " + button.getIcon().getIconWidth() + "x"
            + button.getIcon().getIconHeight() + " grayed " + (button.getDisabledIcon() != null) + " focusable "
            + button.isFocusable());
    }

    /** An outline, these outline rows selected, Duplicate chosen on the last: the outline it leaves. */
    private static String duplicated(org.traincontrol.gui.RouteEditorFrame frame, javax.swing.JTable table,
        String outline, int first, int last) throws Exception
    {
        setRows(frame, fromLevels(outline));
        selectLines(table, first, last);
        rightClickAndChoose(table, last, "route.ui.menuDuplicate");

        return levels(frame);
    }

    /** A claim on a route editor of its own, its menus and the window closed after. */
    private static void inAnEditor(EditorClaim claim) throws Exception
    {
        inAnEditor(null, claim);
    }

    /** The same, on an editor opened on a route. */
    private static void inAnEditor(org.traincontrol.base.Route route, EditorClaim claim) throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open(route);

        try
        {
            claim.run(frame);
        }
        finally
        {
            support.Mouse.closeMenus();

            close(frame);
        }
    }

    private interface EditorClaim
    {
        void run(org.traincontrol.gui.RouteEditorFrame frame) throws Exception;
    }

    private static final int COMMAND_TARGET = 3;
    private static final int COMMAND_TRASH = 8;
    private static final int COMMAND_COPY = 9;
    private static final int CONDITION_COPY = 6;

    /** The frame with accessory commands at these addresses, packed and showing; its command table. */
    private static javax.swing.JTable showCommands(org.traincontrol.gui.RouteEditorFrame frame, int... addresses)
        throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (int address : addresses)
            {
                frame.appendCommand(RouteCommand.RouteCommandAccessory(address, Accessory.accessoryDecoderType.MM2, true)
                    .toLine(null).trim());
            }

            frame.pack();
            frame.setVisible(true);
        });

        return table(frame, "CommandTable");
    }

    /** Each command's target in order, " 11 12" , a new one's as "_". */
    private static String targets(org.traincontrol.gui.RouteEditorFrame frame)
    {
        return support.Components.onTheEventThread(() ->
        {
            StringBuilder out = new StringBuilder();

            for (int row = 0; row < frame.commandRowCountForTest(); row++)
            {
                String target = frame.commandRowForTest(row).getTarget();

                out.append(" ").append(target == null || target.isEmpty() ? "_" : target);
            }

            return out.toString();
        });
    }

    /** The commands' targets and the selected rows: " 11 12 [1]". */
    private static String state(org.traincontrol.gui.RouteEditorFrame frame, javax.swing.JTable table)
    {
        return targets(frame) + " " + selectedRows(table);
    }

    /** Presses an outline row's handle, drags to a point of the table and lets go there, as the mouse sends it. */
    private static void drag(final javax.swing.JTable table, int row, final java.awt.Point to) throws Exception
    {
        support.RouteEditing.drag(table, row, to);
    }

    /** Whether a button of the route editor, found by its text, can be pressed. */
    private static boolean enabled(final org.traincontrol.gui.RouteEditorFrame frame, String key)
    {
        javax.swing.AbstractButton button = notOnTheCommands(frame, key);

        assertNotNull(button, "the route editor has no button " + key);

        return support.Components.onTheEventThread(button::isEnabled);
    }

    /** The button saying this outside the commands' section: the conditions' Undo, not the commands'. */
    private static javax.swing.AbstractButton notOnTheCommands(final org.traincontrol.gui.RouteEditorFrame frame,
        String key)
    {
        return support.RouteEditing.notOnTheCommands(frame, key);
    }

    /** The commands' section of the window: their frame, the row under it, and Capture. */
    private static java.awt.Container commandSection(final org.traincontrol.gui.RouteEditorFrame frame)
    {
        return support.RouteEditing.commandSection(frame);
    }

    /**
     * OB-396, Adam: "put the red "these conditions" warning on the same line as Only When These Conditions are Met, in
     * the upper-right corner of its frame."  In every language, at the packed width and at the window's narrowest: the
     * heading at the left in full, the notice at the section's right on the heading's line or, where both do not fit,
     * on the line under it, never over the heading; the reading on a line of its own under them, above the table.  In
     * English at the packed width they share one line; with a notice too long to share the line, at the narrowest it
     * wraps.
     *
     * MUTATION: in `HeadingLine.doLayout`, set the notice at x 0 rather than `width - note.width`, and it is not at
     * the section's right.
     */
    @Test
    public void testTheNoticeSitsOnTheHeadingsLineAndTheReadingUnderThem() throws Exception
    {
        needsADisplay();

        java.util.Locale was = java.util.Locale.getDefault();
        java.util.List<String> wrapped = new java.util.ArrayList<>();

        try
        {
            for (String language : new String[]{"en", "de", "fr", "es", "it", "nl", "da", "pl"})
            {
                java.util.Locale.setDefault(new java.util.Locale(language));
                I18n.setLocale(new java.util.Locale(language));

                final org.traincontrol.gui.RouteEditorFrame frame = open();

                try
                {
                    javax.swing.SwingUtilities.invokeAndWait(() ->
                    {
                        frame.setAutoFireForTest(false);
                        frame.setConditionRowsForTest(fromLevels(" 0:21 0OR 0:22"));
                        frame.pack();
                        frame.setVisible(true);
                    });

                    support.Components.settle();

                    boolean apart = headingLine(frame, language + " at the packed width");

                    if (language.equals("en")) assertFalse(apart, "in English at the packed width the notice wrapped");

                    javax.swing.SwingUtilities.invokeAndWait(() ->
                    {
                        frame.setSize(200, frame.getHeight());
                        frame.validate();
                    });

                    support.Components.settle();

                    if (headingLine(frame, language + " at the narrowest, " + frame.getWidth() + " wide")) wrapped.add(language);

                    if (!language.equals("en")) continue;

                    final javax.swing.JLabel notice = (javax.swing.JLabel) privateField(frame, "unasked");

                    // Word by word until it cannot share the line with the heading, yet fits the line alone
                    javax.swing.SwingUtilities.invokeAndWait(() ->
                    {
                        String[] words = notice.getText().split(" ");
                        int line = notice.getParent().getWidth();
                        int beside = line - support.Components.find(frame, javax.swing.JLabel.class,
                            l -> I18n.t("route.ui.frameConditions").equals(l.getText())).getPreferredSize().width / 2;

                        for (int word = 0; notice.getPreferredSize().width < beside; word++)
                        {
                            notice.setText(notice.getText() + " " + words[word % words.length]);
                        }

                        frame.validate();
                    });

                    support.Components.settle();

                    assertTrue(headingLine(frame, "a notice too long to share the line, at the narrowest"),
                        "a notice too long to share the line did not wrap under the heading");
                }
                finally
                {
                    close(frame);
                }
            }
        }
        finally
        {
            java.util.Locale.setDefault(was);
            I18n.setLocale(was);
        }

        System.out.println("OB-396: the notice wraps under the heading at the narrowest in " + wrapped);
    }

    /**
     * Checks the heading's line of the conditions in one window.
     *
     * @return whether the notice stands under the heading rather than beside it
     */
    private static boolean headingLine(final org.traincontrol.gui.RouteEditorFrame frame, final String where)
        throws Exception
    {
        final javax.swing.JTable table = conditionTable(frame);
        final javax.swing.JLabel reading = support.Components.find(frame, javax.swing.JLabel.class,
            l -> l.getText() != null && l.getText().equals(frame.readsAsForTest()) && l.isShowing());
        final javax.swing.JLabel notice = (javax.swing.JLabel) privateField(frame, "unasked");
        final javax.swing.JLabel heading = support.Components.find(frame, javax.swing.JLabel.class,
            l -> I18n.t("route.ui.frameConditions").equals(l.getText()) && l.isShowing());

        assertNotNull(reading, where + ": the reading is not showing");
        assertTrue(support.Components.onTheEventThread(notice::isShowing), where
            + ": the notice is not showing with Automatically Fire off");
        assertNotNull(heading, where + ": the conditions' heading is not showing");
        assertSame(notice.getParent(), heading.getParent(), where + ": the notice is not on the heading's line");

        final boolean[] apart = new boolean[1];

        String problem = support.Components.onTheEventThread(() ->
        {
            java.awt.Container line = heading.getParent();
            java.awt.Rectangle head = heading.getBounds();
            java.awt.Rectangle note = notice.getBounds();
            java.awt.Rectangle read = javax.swing.SwingUtilities.convertRectangle(reading.getParent(),
                reading.getBounds(), line);
            javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) javax.swing.SwingUtilities
                .getAncestorOfClass(javax.swing.JScrollPane.class, table);
            int tableTop = javax.swing.SwingUtilities.convertPoint(scroll, 0, 0, line).y;

            apart[0] = Math.abs(head.getCenterY() - note.getCenterY()) > 3;

            if (head.x != 0) return "the heading starts at " + head.x;
            if (head.width < heading.getPreferredSize().width) return "the heading is cut to " + head.width;
            if (head.intersects(note)) return "the notice " + note + " lies over the heading " + head;
            if (Math.abs(note.x + note.width - line.getWidth()) > 1) return "the notice ends at " + (note.x + note.width)
                + ", not at the section's right " + line.getWidth();
            if (apart[0] && note.y < head.y + head.height) return "the notice is neither beside nor under the heading";
            if (read.y < Math.max(head.y + head.height, note.y + note.height)) return "the reading is not under them";
            if (read.y + read.height > tableTop) return "the reading runs into the table";

            return null;
        });

        assertNull(problem, where + ": " + problem);

        return apart[0];
    }

    /**
     * OB-396, Adam: "enforce a minimum window width at the trigger dropdown".  In every language the window cannot be
     * made narrower than its top line: sized down to 200 pixels, Name, Automatically Fire, S88 and Trigger stand on
     * one line inside it and the Trigger dropdown keeps the width its longest choice asks for.
     *
     * MUTATION: in the constructor, drop `setMinimumSize`, and the window sized down cuts the dropdown off.
     */
    @Test
    public void testTheWindowIsNeverNarrowerThanItsTopLine() throws Exception
    {
        needsADisplay();

        java.util.Locale was = java.util.Locale.getDefault();

        try
        {
            for (String language : new String[]{"en", "de", "fr", "es", "it", "nl", "da", "pl"})
            {
                java.util.Locale.setDefault(new java.util.Locale(language));
                I18n.setLocale(new java.util.Locale(language));

                final org.traincontrol.gui.RouteEditorFrame frame = open();

                try
                {
                    javax.swing.SwingUtilities.invokeAndWait(() ->
                    {
                        frame.pack();
                        frame.setVisible(true);
                        frame.setSize(200, frame.getHeight());
                        frame.validate();
                    });

                    support.Components.settle();

                    final javax.swing.JComboBox<?> trigger = (javax.swing.JComboBox<?>) privateField(frame, "triggerBox");
                    final java.awt.Component name = (java.awt.Component) privateField(frame, "nameField");

                    String problem = support.Components.onTheEventThread(() ->
                    {
                        java.awt.Container line = trigger.getParent();
                        java.awt.Rectangle at = trigger.getBounds();
                        int asks = trigger.getPreferredSize().width;

                        if (!trigger.isShowing()) return "the dropdown is not showing";
                        if (at.width < asks) return "the dropdown is " + at.width + " wide where it asks " + asks;
                        if (at.x + at.width > line.getWidth()) return "the dropdown runs past the line's end";
                        if (Math.abs(at.getCenterY() - name.getBounds().getCenterY()) > 2)
                        {
                            return "the dropdown is not on the name's line";
                        }

                        return null;
                    });

                    assertNull(problem, language + ", the window " + frame.getWidth() + " wide: " + problem);
                }
                finally
                {
                    close(frame);
                }
            }
        }
        finally
        {
            java.util.Locale.setDefault(was);
            I18n.setLocale(was);
        }
    }

    /**
     * OB-396, Adam: "If I have A and B and C, the current setup doesn't make it easy to change it to A and B or C."
     * Split Here cuts the selected condition's group just above it, the two parts joined by the other rule: "21 and 22
     * and 23" at 23 is "(21 and 22) or 23", at 22 "21 or (22 and 23)".  A nested group whose parent joins by the other
     * rule gives the parent its two parts; one whose parent joins by the same rule stays, with the other rule.  Each
     * by truth table against an expression written here.  Grayed with nothing selected, with two, and on a group's
     * first condition; the button, Ctrl+Shift+S and the right-click menu do it.
     *
     * MUTATION: in `ConditionOutline.split`, join the two parts by `word` rather than `other`, and "21 and 22 and 23"
     * split at 23 still means "21 and 22 and 23".
     */
    @Test
    public void testSplitHereCutsTheGroupAboveTheCondition() throws Exception
    {
        needsADisplay();

        final org.traincontrol.gui.RouteEditorFrame frame = open();

        try
        {
            showRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));

            final javax.swing.JTable table = conditionTable(frame);

            assertFalse(enabled(frame, "route.ui.menuSplitHere"), "Split Here is offered with nothing selected");

            clickCondition(table, 0, HANDLE, 0);

            assertFalse(enabled(frame, "route.ui.menuSplitHere"), "Split Here is offered on the group's first condition");

            clickCondition(table, 2, HANDLE, 0);
            clickCondition(table, 4, HANDLE, java.awt.event.InputEvent.SHIFT_DOWN_MASK);

            assertFalse(enabled(frame, "route.ui.menuSplitHere"), "Split Here is offered for two conditions");

            clickCondition(table, 4, HANDLE, 0);
            press(frame, "route.ui.menuSplitHere");

            assertEquals(levels(frame), " 1:21 1AND 1:22 0OR 0:23", "Split Here at 23 of 21 and 22 and 23");
            assertEquivalent(heldBy(frame), either(both(on(21), on(22)), on(23)), null, "21 and 22 and 23 split at 23");
            assertEquals(selectedRows(table), "[4]", "23 stays selected");

            setRows(frame, fromLevels(" 0:21 0AND 0:22 0AND 0:23"));

            clickCondition(table, 2, HANDLE, 0);
            support.Keys.press(table, java.awt.event.KeyEvent.VK_S,
                java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);

            assertEquals(levels(frame), " 0:21 0OR 1:22 1AND 1:23", "Ctrl+Shift+S at 22 of 21 and 22 and 23");
            assertEquivalent(heldBy(frame), either(on(21), both(on(22), on(23))), null, "21 and 22 and 23 split at 22");

            setRows(frame, fromLevels(" 0:21 0OR 1:22 1AND 1:23 1AND 1:24"));

            clickCondition(table, 2, HANDLE, 0);

            assertFalse(enabled(frame, "route.ui.menuSplitHere"), "Split Here is offered on a nested group's first");

            rightClickAndChoose(table, 6, "route.ui.menuSplitHere");

            assertEquals(levels(frame), " 0:21 0OR 1:22 1AND 1:23 0OR 0:24",
                "Split Here at 24 of 21 or (22 and 23 and 24) gives the 'or' its two parts");
            assertEquivalent(heldBy(frame), either(on(21), either(both(on(22), on(23)), on(24))), null,
                "21 or (22 and 23 and 24) split at 24");

            setRows(frame, fromLevels(" 0:21 0AND 1:22 1AND 1:23 1AND 1:24"));

            clickCondition(table, 4, HANDLE, 0);
            press(frame, "route.ui.menuSplitHere");

            assertEquals(levels(frame), " 0:21 0AND 1:22 1OR 2:23 2AND 2:24",
                "Split Here at 23 of 21 and (22 and 23 and 24) keeps the group, with 'or'");
            assertEquivalent(heldBy(frame), both(on(21), either(on(22), both(on(23), on(24)))), null,
                "21 and (22 and 23 and 24) split at 23");
            assertTrue(org.traincontrol.base.ConditionOutline.problems(frame.conditionRowsForTest()).isEmpty(),
                "Split Here left a mixed group: " + levels(frame));
        }
        finally
        {
            close(frame);
        }
    }

    /** The expression the editor holds. */
    private static org.traincontrol.base.NodeExpression heldBy(org.traincontrol.gui.RouteEditorFrame frame)
    {
        return org.traincontrol.base.ConditionOutline.toExpression(frame.conditionRowsForTest());
    }

    private static org.traincontrol.base.NodeExpression on(int sensor)
    {
        return new org.traincontrol.base.NodeRouteCommand(feedback(sensor));
    }

    private static org.traincontrol.base.NodeExpression both(org.traincontrol.base.NodeExpression left,
        org.traincontrol.base.NodeExpression right)
    {
        return new org.traincontrol.base.NodeAnd(left, right);
    }

    private static org.traincontrol.base.NodeExpression either(org.traincontrol.base.NodeExpression left,
        org.traincontrol.base.NodeExpression right)
    {
        return new org.traincontrol.base.NodeOr(left, right);
    }

    /** Locomotives and routes the practical claim adds to the model, and takes away again. */
    private static final String PRACTICAL = "OB396 practical ";

    /**
     * OB-396, Adam: "Be sure to test this with many practical combinations/depths of conditions or nestings (such as
     * autonomy loc not at station + all existing conditions) so that we know the editor can manage them correctly."
     *
     * Every kind of condition the editor offers (switch in two protocols, signal, sensor on and off, train at, train
     * not at), alone and in nine shapes up to four levels deep, is stored in a route of a real model and opened in the
     * real editor.  Each is checked against the shape written here by hand, not against the editor's own reading of
     * it: one caption per group naming its word and no line for a word, the outline and what Save writes back
     * equivalent to it by truth table, the reading's brackets and words, nothing flagged, the same conditions, and each
     * condition drawn inside as many outlines as it has groups round it.  The deeper shapes then take a user's edits
     * through the gestures (a caption's rule, Group Selected, Ungroup, Add Condition into a nested group set to
     * another kind, Alt+Up and Alt+Down past and across a group's edge, drags into and out of nested groups, Delete in a
     * nested group, Split Here), each checked the same way against the shape it should leave; every shape ends with
     * Save and a reopen that shows it again, a nested group with its parent's rule included.
     *
     * MUTATION: in `ConditionOutline.grouped`, join the wrapped items by `opposite(word)`, and Group Selected on F5off
     * and S12 in shape 2 captions the new group "At least one" inside the outer "all".
     */
    @Test
    public void testPracticalConditionSetsLoadEditSaveAndReopen() throws Exception
    {
        needsADisplay();

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        org.traincontrol.gui.TrainControlUI ui = null;

        try
        {
            sandbox = support.LayoutSandbox.open();
            model = support.TestStation.simulatedWithWindowInDebug();
            model.setNetworkCommState(false);
            ui = (org.traincontrol.gui.TrainControlUI) model.getGUI();

            assertNotNull(ui, "the model has no window, so Save would have nowhere to write");

            for (int turn = 0; turn < 2; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            final String x = PRACTICAL + "X";
            final String y = PRACTICAL + "Y";

            assertNotNull(model.newMM2Locomotive(x, 65), "the locomotive " + x + " could not be added");
            assertNotNull(model.newMM2Locomotive(y, 66), "the locomotive " + y + " could not be added");

            Accessory signal = null;
            int sig = 0;

            for (int address = 1; address <= 320 && signal == null; address++)
            {
                for (Accessory.accessoryDecoderType type : Accessory.accessoryDecoderType.values())
                {
                    Accessory at = model.getAccessoryByAddressIfPresent(address, type);

                    if (at != null && at.isSignal() && signal == null)
                    {
                        signal = at;
                        sig = address;
                    }
                }
            }

            assertNotNull(signal, "precondition: the fixture layout has no signal, so no condition can be shown as one");

            final java.util.Map<String, RouteCommand> terms = new java.util.LinkedHashMap<>();

            terms.put("X21n", RouteCommand.RouteCommandAutoLocomotive(x, 21, false));
            terms.put("X22n", RouteCommand.RouteCommandAutoLocomotive(x, 22, false));
            terms.put("X23n", RouteCommand.RouteCommandAutoLocomotive(x, 23, false));
            terms.put("X9n", RouteCommand.RouteCommandAutoLocomotive(x, 9, false));
            terms.put("X6n", RouteCommand.RouteCommandAutoLocomotive(x, 6, false));
            terms.put("Y22", RouteCommand.RouteCommandAutoLocomotive(y, 22, true));
            terms.put("A21", RouteCommand.RouteCommandAutoLocomotive(x, 21, true));
            terms.put("B21", RouteCommand.RouteCommandAutoLocomotive(y, 21, true));
            terms.put("S12", RouteCommand.RouteCommandAccessory(switchAt(model, 12), Accessory.accessoryDecoderType.MM2,
                false));
            terms.put("S7turn", RouteCommand.RouteCommandAccessory(switchAt(model, 7), Accessory.accessoryDecoderType.DCC,
                true));
            terms.put("S3turn", RouteCommand.RouteCommandAccessory(switchAt(model, 3), Accessory.accessoryDecoderType.MM2,
                true));
            terms.put("S5straight", RouteCommand.RouteCommandAccessory(switchAt(model, 5),
                Accessory.accessoryDecoderType.MM2, false));
            terms.put("G30", RouteCommand.RouteCommandAccessory(sig, signal.getDecoderType(), false));
            terms.put("R4", RouteCommand.RouteCommandAccessory(sig, signal.getDecoderType(), true));

            for (int sensor = 1; sensor <= 9; sensor++)
            {
                terms.put("F" + sensor + "on", RouteCommand.RouteCommandFeedback(sensor, true));
                terms.put("F" + sensor + "off", RouteCommand.RouteCommandFeedback(sensor, false));
            }

            final String[] singles = {"X21n", "Y22", "F5off", "F1on", "S12", "S7turn", "G30"};

            for (String single : singles) practicalShape(ui, terms, "1 " + single, single, new Object[0][]);

            practicalShape(ui, terms, "2", "X21n & F5off & S12 & G30 & Y22", new Object[][]{
                {"Group Selected on F5off and S12", (Step) f -> f.select("F5off", "S12").press("route.ui.menuGroupSelected"),
                    "X21n & (F5off & S12) & G30 & Y22"},
                {"At least one on the new group's caption", (Step) f -> f.rule(1, "route.ui.conditionCaptionAny"),
                    "X21n & (F5off | S12) & G30 & Y22"},
                {"At least one on the outer caption", (Step) f -> f.rule(0, "route.ui.conditionCaptionAny"),
                    "X21n | (F5off | S12) | G30 | Y22"},
                {"Y22 dragged to the end of the nested group", (Step) f -> f.drag("Y22", f.gapBefore("G30"), true),
                    "X21n | (F5off | S12 | Y22) | G30"},
                {"Alt+Up on G30, past the group", (Step) f -> f.alt("G30", java.awt.event.KeyEvent.VK_UP),
                    "X21n | G30 | (F5off | S12 | Y22)"},
                {"Ungroup with S12 selected", (Step) f -> f.select("S12", "S12").press("route.ui.menuUngroup"),
                    "X21n | G30 | F5off | S12 | Y22"},
                {"Delete G30", (Step) f -> f.delete("G30"), "X21n | F5off | S12 | Y22"},
            });

            practicalShape(ui, terms, "3", "X21n & (F3off | F4off)", new Object[0][]);
            practicalShape(ui, terms, "4", "(A21 | B21) & S7turn", new Object[0][]);

            practicalShape(ui, terms, "5", "F1on & (F2off | (S3turn & R4))", new Object[][]{
                {"At least one on the innermost caption", (Step) f -> f.rule(2, "route.ui.conditionCaptionAny"),
                    "F1on & (F2off | (S3turn | R4))"},
                {"Ungroup with S3turn selected", (Step) f -> f.select("S3turn", "S3turn").press("route.ui.menuUngroup"),
                    "F1on & (F2off | S3turn | R4)"},
                {"Group Selected on F2off and S3turn", (Step) f -> f.select("F2off", "S3turn")
                    .press("route.ui.menuGroupSelected"), "F1on & ((F2off | S3turn) | R4)"},
                {"All on the new group's caption", (Step) f -> f.rule(2, "route.ui.conditionCaptionAll"),
                    "F1on & ((F2off & S3turn) | R4)"},
                {"Add Condition with R4 selected, set to X not at 21", (Step) f -> f.select("R4", "R4")
                    .add(CommandRow.Kind.AUTO_LOCOMOTIVE_NOT_AT, x, "21"), "F1on & ((F2off & S3turn) | R4 | X21n)"},
                {"Alt+Up on X21n", (Step) f -> f.alt("X21n", java.awt.event.KeyEvent.VK_UP),
                    "F1on & ((F2off & S3turn) | X21n | R4)"},
                {"Alt+Up on X21n, past the group", (Step) f -> f.alt("X21n", java.awt.event.KeyEvent.VK_UP),
                    "F1on & (X21n | (F2off & S3turn) | R4)"},
                {"Alt+Up on X21n at its group's edge, with no group across it", (Step) f -> f.alt("X21n",
                    java.awt.event.KeyEvent.VK_UP), "F1on & (X21n | (F2off & S3turn) | R4)"},
                {"R4 dragged out to the end of the outer group", (Step) f -> f.drag("R4", f.gapAtEnd(), false),
                    "F1on & (X21n | (F2off & S3turn)) & R4"},
                {"R4 dragged into the innermost group", (Step) f -> f.drag("R4", f.gapBefore("S3turn"), true),
                    "F1on & (X21n | (F2off & R4 & S3turn))"},
                {"Delete S3turn", (Step) f -> f.delete("S3turn"), "F1on & (X21n | (F2off & R4))"},
            });

            practicalShape(ui, terms, "6", "((F1on & F2on) | (F3on & F4on)) & X9n", new Object[][]{
                {"Alt+Down on F2on, across into the next group", (Step) f -> f.alt("F2on",
                    java.awt.event.KeyEvent.VK_DOWN), "((F1on) | (F2on & F3on & F4on)) & X9n"},
                {"Alt+Up on F2on, back across", (Step) f -> f.alt("F2on", java.awt.event.KeyEvent.VK_UP),
                    "((F1on & F2on) | (F3on & F4on)) & X9n"},
                {"All on the middle caption", (Step) f -> f.rule(1, "route.ui.conditionCaptionAll"),
                    "((F1on & F2on) & (F3on & F4on)) & X9n"},
                {"Ungroup with F3on selected", (Step) f -> f.select("F3on", "F3on").press("route.ui.menuUngroup"),
                    "((F1on & F2on) & F3on & F4on) & X9n"},
                {"Group Selected on F3on and F4on", (Step) f -> f.select("F3on", "F4on")
                    .press("route.ui.menuGroupSelected"), "((F1on & F2on) & (F3on & F4on)) & X9n"},
                {"At least one on the new group's caption", (Step) f -> f.rule(3, "route.ui.conditionCaptionAny"),
                    "((F1on & F2on) & (F3on | F4on)) & X9n"},
                {"X9n dragged to the end of the innermost first group", (Step) f -> f.drag("X9n",
                    f.gapBeforeTheCaptionOf("F3on"), true), "(F1on & F2on & X9n) & (F3on | F4on)"},
                {"Delete F1on", (Step) f -> f.delete("F1on"), "(F2on & X9n) & (F3on | F4on)"},
                {"Add Condition with F3on selected, set to sensor 8", (Step) f -> f.select("F3on", "F3on")
                    .add(CommandRow.Kind.FEEDBACK, "8", null), "(F2on & X9n) & (F3on | F4on | F8on)"},
            });

            practicalShape(ui, terms, "7", "F1on | (F2on & (F3on | (S5straight & X6n)))", new Object[][]{
                {"At least one on the deepest caption", (Step) f -> f.rule(3, "route.ui.conditionCaptionAny"),
                    "F1on | (F2on & (F3on | (S5straight | X6n)))"},
                {"Ungroup with X6n selected", (Step) f -> f.select("X6n", "X6n").press("route.ui.menuUngroup"),
                    "F1on | (F2on & (F3on | S5straight | X6n))"},
                {"Alt+Down on F2on, past the group", (Step) f -> f.alt("F2on", java.awt.event.KeyEvent.VK_DOWN),
                    "F1on | ((F3on | S5straight | X6n) & F2on)"},
                {"F1on dragged into the deepest group", (Step) f -> f.drag("F1on", f.gapBefore("S5straight"), true),
                    "(F3on | F1on | S5straight | X6n) & F2on"},
                {"Group Selected on F1on and S5straight", (Step) f -> f.select("F1on", "S5straight")
                    .press("route.ui.menuGroupSelected"), "(F3on | (F1on | S5straight) | X6n) & F2on"},
                {"All on the new group's caption", (Step) f -> f.rule(2, "route.ui.conditionCaptionAll"),
                    "(F3on | (F1on & S5straight) | X6n) & F2on"},
                {"Delete S5straight", (Step) f -> f.delete("S5straight"), "(F3on | (F1on) | X6n) & F2on"},
                {"Add Condition with F1on selected, set to Y at 22", (Step) f -> f.select("F1on", "F1on")
                    .add(CommandRow.Kind.AUTO_LOCOMOTIVE, y, "22"), "(F3on | (F1on & Y22) | X6n) & F2on"},
            });

            practicalShape(ui, terms, "8", "X21n & X22n & X23n & F9on", new Object[0][]);

            practicalShape(ui, terms, "10", "X21n & F5off & S12", new Object[][]{
                {"Group Selected on F5off and S12, kept as a group of the same rule", (Step) f -> f.select("F5off", "S12")
                    .press("route.ui.menuGroupSelected"), "X21n & (F5off & S12)"},
            });

            practicalShape(ui, terms, "11", "X21n & F5off & S12 & G30", new Object[][]{
                {"Split Here at S12", (Step) f -> f.select("S12", "S12").press("route.ui.menuSplitHere"),
                    "(X21n & F5off) | (S12 & G30)"},
                {"Split Here at F5off, inside a nested group", (Step) f -> f.select("F5off", "F5off")
                    .press("route.ui.menuSplitHere"), "X21n | F5off | (S12 & G30)"},
            });
            practicalShape(ui, terms, "9", "F1on | F2on | (F3on & F4on) | (S12 & (A21 | B21))", new Object[0][]);
        }
        finally
        {
            if (model != null)
            {
                for (String name : new java.util.ArrayList<>(model.getRouteList()))
                {
                    if (name.startsWith(PRACTICAL)) model.deleteRoute(name);
                }

                try { model.deleteLoc(PRACTICAL + "X"); } catch (Exception ignored) { }
                try { model.deleteLoc(PRACTICAL + "Y"); } catch (Exception ignored) { }
            }

            if (ui != null)
            {
                final org.traincontrol.gui.TrainControlUI closing = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /** An address no signal of the layout answers at, from a preferred one up, so the condition is shown as a switch. */
    private static int switchAt(org.traincontrol.marklin.MarklinControlStation model, int preferred)
    {
        return support.RouteEditing.switchAt(model, preferred);
    }

    /** One edit of the practical claim, done through the editor as a user does it. */
    private interface Step
    {
        void on(Practical editor) throws Exception;
    }

    /**
     * One shape: stored in a route, opened, checked; each step done and checked; saved, and the saved route checked;
     * reopened and checked again.
     */
    private static void practicalShape(org.traincontrol.gui.TrainControlUI ui, java.util.Map<String, RouteCommand> terms,
        String shape, String written, Object[][] steps) throws Exception
    {
        final String name = PRACTICAL + shape;
        final Spec loaded = Spec.of(written);

        assertTrue(ui.getModel().newRoute(name, new java.util.ArrayList<>(java.util.Collections.singletonList(
            RouteCommand.RouteCommandAccessory(80, Accessory.accessoryDecoderType.MM2, true))), 0,
            org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, loaded.node(terms)),
            "precondition: the route for shape " + shape + " was not made");

        assertEquivalent(ui.getModel().getRoute(name).getConditions(), loaded.node(terms), terms,
            "precondition: the model did not store shape " + shape + " as written");

        Practical editor = Practical.open(ui, name, terms);

        try
        {
            editor.check(loaded, "shape " + shape + " (" + written + ") as opened");

            for (Object[] step : steps)
            {
                ((Step) step[1]).on(editor);

                editor.check(Spec.of((String) step[2]), "shape " + shape + " after " + step[0]);
            }

            Spec last = steps.length == 0 ? loaded : Spec.of((String) steps[steps.length - 1][2]);

            editor.save();

            assertEquivalent(ui.getModel().getRoute(name).getConditions(), last.node(terms), terms,
                "shape " + shape + ": what Save wrote back to the route");

            editor = Practical.open(ui, name, terms);

            editor.check(last, "shape " + shape + " reopened after Save");
        }
        finally
        {
            editor.close();
        }
    }

    /** A route editor on one practical shape, and the gestures the steps use. */
    private static final class Practical
    {
        final org.traincontrol.gui.RouteEditorFrame frame;
        final javax.swing.JTable table;
        final java.util.Map<String, RouteCommand> terms;

        private Practical(org.traincontrol.gui.RouteEditorFrame frame, java.util.Map<String, RouteCommand> terms)
        {
            this.frame = frame;
            this.table = conditionTable(frame);
            this.terms = terms;
        }

        static Practical open(org.traincontrol.gui.TrainControlUI ui, String name, java.util.Map<String, RouteCommand> terms)
            throws Exception
        {
            final org.traincontrol.gui.RouteEditorFrame[] made = new org.traincontrol.gui.RouteEditorFrame[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                made[0] = new org.traincontrol.gui.RouteEditorFrame(ui, name);
                made[0].pack();
                made[0].setVisible(true);
            });

            support.Components.settle();

            return new Practical(made[0], terms);
        }

        void close() throws Exception
        {
            support.Mouse.closeMenus();

            javax.swing.SwingUtilities.invokeAndWait(frame::dispose);
        }

        /** The outline row of the one condition that is this term. */
        int row(String term)
        {
            String key = key(terms.get(term));
            int found = -1;

            for (int at = 0; at < frame.conditionRowsForTest().size(); at++)
            {
                org.traincontrol.base.ConditionOutline.Row line = frame.conditionRowsForTest().get(at);

                if (!line.isJoiner() && key(line.getCommand()).equals(key))
                {
                    assertEquals(found, -1, term + " stands twice in the outline: " + names(this));

                    found = at;
                }
            }

            assertTrue(found >= 0, term + " is not in the outline: " + names(this));

            return found;
        }

        /** Clicks the first term's handle and Shift-clicks the last's: a click on a handle selects and moves nothing. */
        Practical select(String first, String last) throws Exception
        {
            clickCell(table, screen(table, row(first)), HANDLE, 0, 1);
            clickCell(table, screen(table, row(last)), HANDLE, java.awt.event.InputEvent.SHIFT_DOWN_MASK, 1);

            return this;
        }

        void press(String key)
        {
            testRouteEditorValidation.press(frame, key);
        }

        void rule(int caption, String key) throws Exception
        {
            chooseRule(table, caption, key);
        }

        void alt(String term, int arrow) throws Exception
        {
            altArrow(table, row(term), arrow);
        }

        /** Selects a term alone, then Delete from its menu, which acts on the whole selection. */
        void delete(String term) throws Exception
        {
            select(term, term);
            rightClickAndChoose(table, row(term), "ui.delete");
        }

        /** The y of the gap above a term's line. */
        int gapBefore(String term)
        {
            int line = screen(table, row(term));

            return support.Components.onTheEventThread(() -> table.getCellRect(line, 1, true).y + 2);
        }

        /** The y of the gap above the caption of the group a term opens. */
        int gapBeforeTheCaptionOf(String term)
        {
            int line = screen(table, row(term)) - 1;

            assertTrue((Boolean) support.Components.onTheEventThread(() -> call(table, "isCaptionLine", line)),
                "precondition: " + term + " does not open a group");

            return support.Components.onTheEventThread(() -> table.getCellRect(line, 1, true).y + 2);
        }

        /** The y of the gap under the last line. */
        int gapAtEnd()
        {
            return support.Components.onTheEventThread(() ->
            {
                java.awt.Rectangle last = table.getCellRect(table.getRowCount() - 1, 1, true);

                return last.y + last.height - 2;
            });
        }

        /** Drags a term alone by its handle to a gap, the pointer over the nested outlines or at the handles. */
        void drag(String term, int y, boolean deep) throws Exception
        {
            int x = deep ? support.Components.onTheEventThread(() -> table.getCellRect(0, ADDRESS, true).x + 10) : 8;

            // Alone: a press on a handle inside a selection of several drags them all.
            select(term, term);

            testRouteEditorValidation.drag(table, row(term), new java.awt.Point(x, y));
        }

        /** Add Condition, then the new condition's kind, address and setting set as their cells set them. */
        void add(CommandRow.Kind kind, String target, String setting) throws Exception
        {
            press("route.ui.menuAddCondition");

            final int row = selectedLine(table);

            assertTrue(row >= 0, "Add Condition selected no condition");

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.setConditionKindForTest(row, kind);

                int line = (Integer) call(table, "lineOf", row);

                table.getModel().setValueAt(target, line, ADDRESS);

                if (setting != null) table.getModel().setValueAt(setting, line, 3);
            });
        }

        /** Save, which has to find nothing wrong and close the window. */
        void save()
        {
            assertEquals(support.Components.onTheEventThread(() -> frame.problemsForTest()).toString(), "[]",
                "Save would refuse the route");

            testRouteEditorValidation.press(frame, "route.ui.frameSave");

            assertTrue(support.Components.waitFor(() -> !support.Components.onTheEventThread(frame::isDisplayable),
                10000), "Save did not close the editor");
        }

        /**
         * What the editor holds and shows, against a shape written by hand: its captions, the expression and the
         * reading, nothing flagged, the conditions, and the outlines drawn round each condition.
         */
        void check(Spec expected, String did)
        {
            java.util.List<org.traincontrol.base.ConditionOutline.Row> rows = frame.conditionRowsForTest();

            java.util.List<Boolean> words = new java.util.ArrayList<>();

            expected.captions(words);

            java.util.List<Integer> captions = captionLines(table);
            int conditions = 0;

            for (org.traincontrol.base.ConditionOutline.Row line : rows) if (!line.isJoiner()) conditions++;

            assertEquals(captions.size(), words.size(), did + ": one caption per group: " + names(this));
            assertEquals((int) support.Components.onTheEventThread(() -> table.getRowCount()), captions.size()
                + conditions, did + ": a line other than a caption or a condition: " + names(this));

            for (int at = 0; at < words.size(); at++)
            {
                if (words.get(at) == null) continue;

                assertEquals(captionText(table, at), I18n.t(words.get(at) ? "route.ui.conditionCaptionAny"
                    : "route.ui.conditionCaptionAll"), did + ": caption " + at + ": " + names(this));
            }

            assertEquivalent(org.traincontrol.base.ConditionOutline.toExpression(rows), expected.node(terms), terms,
                did + ": the outline the editor holds, " + names(this));

            assertEquals(skeleton(support.Components.onTheEventThread(frame::readsAsForTest)),
                expected.terms().isEmpty() ? "" : expected.skeleton(), did + ": the reading's brackets and words");

            assertTrue(org.traincontrol.base.ConditionOutline.problems(rows).isEmpty(), did + ": flagged: "
                + names(this));

            java.util.List<String> held = new java.util.ArrayList<>(names(this));
            java.util.List<String> meant = new java.util.ArrayList<>(expected.terms());

            java.util.Collections.sort(held);
            java.util.Collections.sort(meant);

            assertEquals(held, meant, did + ": the conditions");

            java.util.Map<String, Integer> depth = new java.util.HashMap<>();

            expected.depths(1, depth);

            for (String term : expected.terms())
            {
                assertEquals(outlinesRound(table, screen(table, row(term))), (int) depth.get(term),
                    did + ": the outlines drawn round " + term);
            }
        }
    }

    /** The conditions of an editor's outline by the names the terms give them, in order. */
    private static java.util.List<String> names(Practical editor)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (org.traincontrol.base.ConditionOutline.Row line : editor.frame.conditionRowsForTest())
        {
            if (line.isJoiner()) continue;

            String found = "?" + key(line.getCommand());

            for (java.util.Map.Entry<String, RouteCommand> each : editor.terms.entrySet())
            {
                if (key(each.getValue()).equals(key(line.getCommand()))) found = each.getKey();
            }

            out.add(found);
        }

        return out;
    }

    private static String key(RouteCommand command)
    {
        return command.toLine(null).trim();
    }

    /**
     * How many group outlines are drawn round a condition line: the outline's color crossed, left to right, through
     * the middle of its Kind cell up to the text, in the table as painted.
     */
    private static int outlinesRound(final javax.swing.JTable table, final int line)
    {
        return support.Components.onTheEventThread(() ->
        {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(table.getWidth(), table.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics g = image.getGraphics();

            // Painted unselected: the edge of a selected line's fill blends into colors near the outline's.
            javax.swing.ListSelectionModel selection = table.getSelectionModel();
            int[] selected = table.getSelectedRows();
            int anchor = selection.getAnchorSelectionIndex();
            int lead = selection.getLeadSelectionIndex();

            selection.clearSelection();
            table.paint(g);
            g.dispose();

            for (int each : selected) selection.addSelectionInterval(each, each);

            selection.setAnchorSelectionIndex(anchor);
            if (selection instanceof javax.swing.DefaultListSelectionModel) ((javax.swing.DefaultListSelectionModel) selection).moveLeadSelectionIndex(lead);

            java.awt.Rectangle kind = table.getCellRect(line, 1, true);
            int y = kind.y + kind.height / 2;
            int crossed = 0;
            boolean on = false;

            for (int x = kind.x; x < kind.x + 80 && x < image.getWidth(); x++)
            {
                java.awt.Color seen = new java.awt.Color(image.getRGB(x, y));
                boolean outline = Math.abs(seen.getRed() - 0x9D) <= 30 && Math.abs(seen.getGreen() - 0xB8) <= 30
                    && Math.abs(seen.getBlue() - 0xD9) <= 30;

                if (outline && !on) crossed++;

                on = outline;
            }

            return crossed;
        });
    }

    /** The reading as brackets and words, each condition an x: "x and (x or x)". */
    private static String skeleton(String reading)
    {
        String and = I18n.t("route.ui.joinAnd");
        String or = I18n.t("route.ui.joinOr");
        String marked = reading.replace("<b>" + and + "</b>", " \u0001 ").replace("<b>" + or + "</b>", " \u0002 ")
            .replaceAll("<[^>]*>", "");
        int colon = marked.indexOf(':');

        if (colon >= 0) marked = marked.substring(colon + 1);

        StringBuilder out = new StringBuilder();
        boolean inTerm = false;

        for (char c : marked.toCharArray())
        {
            String token = c == '(' ? "(" : c == ')' ? ")" : c == '\u0001' ? and : c == '\u0002' ? or : null;

            if (token != null)
            {
                inTerm = false;
                out.append(token.equals(")") || out.length() == 0 || out.charAt(out.length() - 1) == '(' ? "" : " ")
                    .append(token);
            }
            else if (!Character.isWhitespace(c) && !inTerm)
            {
                inTerm = true;
                out.append(out.length() == 0 || out.charAt(out.length() - 1) == '(' ? "" : " ").append('x');
            }
        }

        return out.toString().trim();
    }

    /** Two expressions over the same conditions agree for every combination of the conditions being true. */
    private static void assertEquivalent(org.traincontrol.base.NodeExpression held,
        org.traincontrol.base.NodeExpression meant, java.util.Map<String, RouteCommand> terms, String what)
    {
        java.util.List<String> keys = new java.util.ArrayList<>();

        collect(held, keys);
        collect(meant, keys);

        assertTrue(keys.size() <= 12, what + ": too many conditions for a truth table: " + keys);

        for (int truth = 0; truth < 1 << keys.size(); truth++)
        {
            java.util.Set<String> on = new java.util.HashSet<>();

            for (int at = 0; at < keys.size(); at++) if ((truth >> at & 1) != 0) on.add(keys.get(at));

            if (truthOf(held, on) != truthOf(meant, on))
            {
                fail(what + " is not equivalent to what was meant: with " + on + " true it reads " + truthOf(held, on)
                    + ", meant " + truthOf(meant, on) + ".  Held: " + shape(held));
            }
        }
    }

    private static void collect(org.traincontrol.base.NodeExpression node, java.util.List<String> into)
    {
        if (node instanceof org.traincontrol.base.NodeRouteCommand)
        {
            String key = key(((org.traincontrol.base.NodeRouteCommand) node).getRouteCommand());

            if (!into.contains(key)) into.add(key);
        }
        else if (node instanceof org.traincontrol.base.NodeAnd)
        {
            collect(((org.traincontrol.base.NodeAnd) node).getLeft(), into);
            collect(((org.traincontrol.base.NodeAnd) node).getRight(), into);
        }
        else if (node instanceof org.traincontrol.base.NodeOr)
        {
            collect(((org.traincontrol.base.NodeOr) node).getLeft(), into);
            collect(((org.traincontrol.base.NodeOr) node).getRight(), into);
        }
        else if (node instanceof org.traincontrol.base.NodeGroup)
        {
            for (org.traincontrol.base.NodeExpression inside : ((org.traincontrol.base.NodeGroup) node).getExpressions())
            {
                collect(inside, into);
            }
        }
    }

    /** An expression's truth with these conditions true and the rest false; a group holds when all of it does. */
    private static boolean truthOf(org.traincontrol.base.NodeExpression node, java.util.Set<String> on)
    {
        if (node == null) return true;

        if (node instanceof org.traincontrol.base.NodeRouteCommand)
        {
            return on.contains(key(((org.traincontrol.base.NodeRouteCommand) node).getRouteCommand()));
        }

        if (node instanceof org.traincontrol.base.NodeAnd)
        {
            return truthOf(((org.traincontrol.base.NodeAnd) node).getLeft(), on)
                && truthOf(((org.traincontrol.base.NodeAnd) node).getRight(), on);
        }

        if (node instanceof org.traincontrol.base.NodeOr)
        {
            return truthOf(((org.traincontrol.base.NodeOr) node).getLeft(), on)
                || truthOf(((org.traincontrol.base.NodeOr) node).getRight(), on);
        }

        for (org.traincontrol.base.NodeExpression inside : ((org.traincontrol.base.NodeGroup) node).getExpressions())
        {
            if (!truthOf(inside, on)) return false;
        }

        return true;
    }

    /**
     * A condition written by hand: "X21n & (F3off | F4off)", a term the name of a condition, `&` and `|` the words,
     * brackets a group.  A level takes one word; "(F1on)" is a group of one.
     */
    private static final class Spec
    {
        final String term;
        final java.util.List<Spec> items = new java.util.ArrayList<>();
        Boolean or;

        private Spec(String term)
        {
            this.term = term;
        }

        static Spec of(String text)
        {
            java.util.List<String> tokens = new java.util.ArrayList<>();
            java.util.regex.Matcher token = java.util.regex.Pattern.compile("[()&|]|[A-Za-z0-9]+").matcher(text);

            while (token.find()) tokens.add(token.group());

            int[] at = {0};
            Spec out = level(tokens, at);

            assertEquals(at[0], tokens.size(), "a hand-written shape has an unmatched bracket: " + text);

            return out;
        }

        private static Spec level(java.util.List<String> tokens, int[] at)
        {
            Spec group = new Spec(null);

            while (at[0] < tokens.size() && !tokens.get(at[0]).equals(")"))
            {
                String next = tokens.get(at[0]++);

                if (next.equals("&") || next.equals("|"))
                {
                    boolean or = next.equals("|");

                    assertTrue(group.or == null || group.or == or, "a hand-written level mixes its words");

                    group.or = or;
                }
                else if (next.equals("("))
                {
                    group.items.add(level(tokens, at));
                    at[0]++;
                }
                else
                {
                    group.items.add(new Spec(next));
                }
            }

            return group;
        }

        /** The expression as a stored route holds it: a bracket is a NodeGroup round the words inside it. */
        org.traincontrol.base.NodeExpression node(java.util.Map<String, RouteCommand> terms)
        {
            return node(terms, false);
        }

        private org.traincontrol.base.NodeExpression node(java.util.Map<String, RouteCommand> terms, boolean bracket)
        {
            if (term != null)
            {
                assertNotNull(terms.get(term), "no condition is called " + term);

                return new org.traincontrol.base.NodeRouteCommand(terms.get(term));
            }

            org.traincontrol.base.NodeExpression out = null;

            for (Spec item : items)
            {
                org.traincontrol.base.NodeExpression next = item.node(terms, true);

                out = out == null ? next : Boolean.TRUE.equals(or) ? new org.traincontrol.base.NodeOr(out, next)
                    : new org.traincontrol.base.NodeAnd(out, next);
            }

            return bracket ? new org.traincontrol.base.NodeGroup(new java.util.ArrayList<>(
                java.util.Collections.singletonList(out))) : out;
        }

        /** Each group's word, outermost first and in order; null for a group of one, which has none to show. */
        void captions(java.util.List<Boolean> into)
        {
            if (term != null) return;

            into.add(items.size() < 2 ? null : or);

            for (Spec item : items) item.captions(into);
        }

        java.util.List<String> terms()
        {
            java.util.List<String> out = new java.util.ArrayList<>();

            if (term != null) out.add(term);

            for (Spec item : items) out.addAll(item.terms());

            return out;
        }

        /** How many groups stand round each term, the outermost included. */
        void depths(int depth, java.util.Map<String, Integer> into)
        {
            for (Spec item : items)
            {
                if (item.term != null) into.put(item.term, depth);
                else item.depths(depth + 1, into);
            }
        }

        /** As the reading writes it: each condition an x, each nested group in brackets. */
        String skeleton()
        {
            StringBuilder out = new StringBuilder();

            for (Spec item : items)
            {
                if (out.length() > 0) out.append(' ').append(I18n.t(Boolean.TRUE.equals(or) ? "route.ui.joinOr"
                    : "route.ui.joinAnd")).append(' ');

                out.append(item.term != null ? "x" : "(" + item.skeleton() + ")");
            }

            return out.toString();
        }
    }

    /** A feedback condition on one sensor. */
    private static org.traincontrol.base.RouteCommand feedback(int address)
    {
        return org.traincontrol.base.RouteCommand.RouteCommandFeedback(address, true);
    }
}
