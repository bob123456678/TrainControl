package core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;

/**
 * Mass Assign Lengths and the Unmeasured Track display: which squares they ask for, and how a piece's whole length is
 * shared out.
 *
 * **The definition, as ruled after review MAL (2026-09-16).**  The first version asked only for the track past the last
 * switch, on the reasoning that every length rule stops there.  Only the room rule does: the FR-087 allowance
 * (`Layout.measuredRouteIn`) adds whole legs back until a reversal, and the tail and berth walks spend the switch square
 * and the track before it.  Measured on Adam's railway, that reach is every leg - so, asked how to cover it, Adam chose
 * **"Every leg, cut at switches"**, and for the switch squares themselves **"One length for all switches"**.
 *
 * So:
 *
 *  - every leg on the pages autonomy uses is cut into PIECES at its switches, and each square belongs to exactly one
 *    piece - a sensor square that ends several legs is in the first of them;
 *  - a switch square is in no piece: its share of a piece's length would, in one direction or the other, sit on the
 *    square the room rule does not count, so the room rule would come out wrong by that share.  Switches are asked for
 *    together, one turnout length per page;
 *  - a piece needs a length only while its TOTAL is 0 - Adam, 2026-09-06: *"It is only indeterminate if the entire
 *    logical segment has length 0."*  A square inside a measured piece may hold 0; the store keeps 0 and "not given" as
 *    the same thing, so a whole length of 0 is refused.
 *
 * Every railway here is its own, built in memory.
 *
 * @author Adam
 */
public class testMassAssignLengths
{
    private File folder;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        folder = Files.createTempDirectory("tc-mass-assign-lengths").toFile();
        session = new AutonomySession(folder);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(folder);
    }

    // ------------------------------------------------------------------------------------------------ which squares

    /**
     * Every leg is cut into pieces at its switches, each square is in one piece, and the switch is in none (MAL-B1).
     *
     * The first half is the one the first version failed: 2,1 lies BEFORE the switch on the way into the berth, and the
     * FR-087 allowance and the tail walk both read it.  The branch to 3,0 leads to no station at all and is still a leg a
     * route runs along, so it is asked for too.
     */
    @Test
    public void testEveryLegIsCutIntoPiecesAtItsSwitches() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        assertEquals(tileSets(session.stretchesALengthRuleReads()),
            new HashSet<>(Arrays.asList(set(key(1, 1), key(2, 1)), set(key(4, 1), key(5, 1)), set(key(3, 0)))),
            "the pieces are the track either side of the switch and the branch: " + describe(session.stretchesALengthRuleReads()));

        assertEquals(session.switchesALengthRuleReads(), set(key(3, 1)), "the switch square is asked for on its own");

        assertNoSquareIsInTwoPieces(session.stretchesALengthRuleReads());
    }

    /**
     * Two switches back to back leave no piece between them, and both are asked for as switches.
     *
     * Adam, 2026-09-16: *"what happens when two switches are back to back"*.  There is no track square between them, so
     * there is nothing to measure there; the room rule stops at the nearer switch, and the legs that cross both add both
     * switches' lengths.
     */
    @Test
    public void testTwoSwitchesBackToBackLeaveNoPieceBetweenThem() throws IOException
    {
        openTwoSwitchesBackToBack();

        List<AutonomySession.Stretch> pieces = session.stretchesALengthRuleReads();

        assertFalse(pieces.isEmpty(), "precondition: the fixture built no legs at all");

        for (AutonomySession.Stretch piece : pieces)
        {
            assertFalse(piece.getTiles().isEmpty(), "an empty piece would be a prompt about nothing: " + describe(pieces));
            assertFalse(piece.getTiles().contains(key(2, 1)) || piece.getTiles().contains(key(3, 1)),
                "a switch square is inside a piece: " + describe(pieces));
        }

        assertEquals(session.switchesALengthRuleReads(), set(key(2, 1), key(3, 1)), "both switches are asked for");

        assertNoSquareIsInTwoPieces(pieces);
    }

    /**
     * A piece is measured once its total is above 0, and a square inside it may hold 0 (Adam's ruling of 2026-09-06).
     */
    @Test
    public void testAPieceIsMeasuredWhenItsTotalIsAboveZero() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setTileLength(key(4, 1), 3);

        assertFalse(session.squaresNeedingALength().contains(key(5, 1)),
            "a square inside a piece that has a length is still highlighted - the whole piece is what is measured, and"
            + " a short square may rightly hold nothing");

        assertTrue(session.squaresNeedingALength().contains(key(2, 1)),
            "control: the unmeasured piece before the switch is no longer highlighted");

        session.setTileLength(key(4, 1), 0);

        assertTrue(session.squaresNeedingALength().contains(key(4, 1)),
            "a piece set back to 0 is measured again, but 0 and 'not given' are the same thing in the store");
    }

    /**
     * The walk is per page: a piece on another page is not asked for from this one.
     */
    @Test
    public void testTheWalkAsksOnlyAboutThisPage() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        assertEquals(session.stretchesNeedingALengthOn("main").size(), 3, "the pieces on this page were not offered");
        assertTrue(session.stretchesNeedingALengthOn("elsewhere").isEmpty(),
            "a page with no track on it was offered a piece from another page");
        assertEquals(session.switchesNeedingALengthOn("main"), set(key(3, 1)));
        assertTrue(session.switchesNeedingALengthOn("elsewhere").isEmpty());
    }

    // ------------------------------------------------------------------------------------------------ sharing it out

    /**
     * Units never cross a switch: the room past it is exactly the piece past it, and the leg adds up (MAL-B1).
     *
     * Asked of the reducer the running layout is built from, so this is the number the room rule and the FR-087
     * allowance will read - not a sum this test does itself.
     */
    @Test
    public void testUnitsNeverCrossASwitch() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        assertTrue(session.assignStretchLength(piece(key(1, 1)), 5));
        assertTrue(session.assignStretchLength(piece(key(4, 1)), 7));
        assertTrue(session.assignSwitchLength(session.switchesNeedingALengthOn("main"), 2));
        assertTrue(session.assignStretchLength(piece(key(3, 0)), 1), "the branch is a piece of its own");

        session.rebuild();

        GraphReducer.ReducedEdge in = edge(key(1, 1), key(5, 1));

        assertEquals(in.getRoomAtTheEnd(), 7,
            "the room past the switch is not the piece past the switch, so a share of it landed on the switch or before it");

        assertEquals(in.getLength(), length(2, 1) + 2 + 7,
            "the leg does not add up to the track before the switch, the switch and the piece past it");

        assertTrue(session.squaresNeedingALength().isEmpty(), "everything has a length and something is still highlighted");
    }

    /**
     * The unit over goes to the square a train stands on first (MAL-B2).
     *
     * That square's own length is an allowance the tail and berth walks never spend, so a unit there is a unit less of
     * rail they can spend - the tail reaches further, which is the refusing direction.  Measured on the review's probe:
     * split 3 and 4 a four-unit tail claimed two squares; split 4 and 3 it claimed four.
     */
    @Test
    public void testTheUnitOverGoesToTheSquareATrainStandsOn() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        assertTrue(session.assignStretchLength(piece(key(5, 1)), 7), "a whole length the piece can hold was refused");

        assertEquals(length(5, 1) + length(4, 1), 7, "the shares do not add up to what was typed");
        assertEquals(length(5, 1), 4, "the unit over should go to the berth, whose length is not spent by its own tail");
        assertEquals(length(4, 1), 3);
    }

    /**
     * Fewer units than squares can be entered, and 0 cannot.
     *
     * Two squares, one unit: one square gets it and the other rightly holds nothing.  The first version demanded a unit
     * per square, so a short piece drawn with several squares could not be entered at all.
     */
    @Test
    public void testFewerUnitsThanSquaresCanBeEnteredButNotZero() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        AutonomySession.Stretch beforeTheSwitch = piece(key(1, 1));

        assertEquals(session.leastWholeLengthOf(beforeTheSwitch), 1, "the least a piece can be given is one unit");

        assertFalse(session.assignStretchLength(beforeTheSwitch, 0), "0 was accepted, and it means no length at all");
        assertEquals(length(1, 1) + length(2, 1), 0, "a refused length still wrote something");

        assertTrue(session.assignStretchLength(beforeTheSwitch, 1), "one unit over two squares was refused");
        assertEquals(length(1, 1) + length(2, 1), 1);

        assertFalse(pieceNeedsALength(key(1, 1)), "a piece whose total is now 1 is still asked for");
    }

    /**
     * One turnout length goes to every switch on the page that has none, and a switch already measured keeps its own.
     */
    @Test
    public void testOneTurnoutLengthGoesToEverySwitchStillWithout() throws IOException
    {
        openTwoSwitchesBackToBack();

        session.setTileLength(key(2, 1), 3);

        assertEquals(session.switchesNeedingALengthOn("main"), set(key(3, 1)), "a measured switch is asked for again");

        assertFalse(session.assignSwitchLength(session.switchesNeedingALengthOn("main"), 0), "0 was accepted for a switch");
        assertEquals(length(3, 1), 0, "a refused switch length still wrote something");

        assertTrue(session.assignSwitchLength(session.switchesNeedingALengthOn("main"), 2));

        assertEquals(length(3, 1), 2, "the switch without a length did not get the turnout length");
        assertEquals(length(2, 1), 3, "the switch that already had a length was overwritten");
    }

    // ------------------------------------------------------------------------------------------------ the display

    /**
     * The highlight is a display choice, and it marks the pieces and switches still needing a length.
     *
     * Asked of the editor's own `annotationFor`, which is what each square paints from.  The toggle is set directly, so
     * the test does not write Adam's remembered view settings.
     */
    @Test
    public void testTheHighlightIsADisplayChoiceAndMarksWhatIsStillUnmeasured() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        panel.getShowUnmeasured().setSelected(false);

        assertFalse(marked(panel, key(4, 1)), "the highlight showed with the display choice off");

        panel.getShowUnmeasured().setSelected(true);

        for (TileKey square : Arrays.asList(key(1, 1), key(2, 1), key(3, 1), key(4, 1), key(5, 1), key(3, 0)))
        {
            assertTrue(marked(panel, square), square + " is on a leg with no length and is not highlighted");
        }
    }

    /**
     * An edit through Control+E updates the highlight (MAL-B3).
     *
     * Control+E reaches `promptLengthFor` -> `applyLength` -> `setupChanged()` and never the panel's `refresh()`, and the
     * highlighted squares were forgotten only in `refresh()` - so the square showed its new number and its old amber.
     * The dialog cannot be driven here, so the write is made the way `applyLength` makes it and `setupChanged` is called
     * as it calls it.
     */
    @Test
    public void testAnEditThroughControlEUpdatesTheHighlight() throws Exception
    {
        openBerthBehindASwitch(key(5, 1));

        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        panel.getShowUnmeasured().setSelected(true);

        assertTrue(marked(panel, key(4, 1)), "precondition: the square is highlighted before it is measured");

        session.setTileLength(key(4, 1), 3);

        java.lang.reflect.Method setupChanged = AutonomyEditorPanel.class.getDeclaredMethod("setupChanged");
        setupChanged.setAccessible(true);
        setupChanged.invoke(panel);

        assertFalse(marked(panel, key(4, 1)), "the square was measured through Control+E's path and is still amber");
        assertFalse(marked(panel, key(5, 1)), "the rest of a piece that now has a length is still amber");
    }

    /**
     * A square whose only mark is "needs a length" still paints (OB-007).
     */
    @Test
    public void testASquareMarkedOnlyAsUnmeasuredIsNotBlank()
    {
        TileAnnotation plain = new TileAnnotation(null, -1, false);
        TileAnnotation marked = new TileAnnotation(null, -1, false).needsALength();

        assertTrue(plain.isBlank(), "control: an annotation with nothing on it is blank");
        assertFalse(marked.isBlank(), "a square whose only mark is 'needs a length' would never be painted");
        assertNotEquals(marked, plain, "the mark is left out of equals, so a repaint could be skipped as unchanged");
        assertNotEquals(marked.hashCode(), plain.hashCode(), "the mark is left out of hashCode");
    }

    /**
     * Escape stops the walk rather than writing what was typed (MAL-B4).
     *
     * JDK 8's option pane answers Escape with `Integer.valueOf(CLOSED_OPTION)`, not null, and both dialogs checked only
     * for null and the Cancel button - so Escape fell through to reading the field.  Asked of the one decision both
     * dialogs now make through.
     */
    @Test
    public void testEscapeStopsRatherThanAnswering()
    {
        Object[] answers = { "OK", "Skip", "Cancel" };

        assertEquals(AutonomyEditorPanel.dialogAnswer(Integer.valueOf(JOptionPane.CLOSED_OPTION), answers),
            AutonomyEditorPanel.ANSWER_STOP, "Escape read the field instead of stopping");

        assertEquals(AutonomyEditorPanel.dialogAnswer(null, answers), AutonomyEditorPanel.ANSWER_STOP, "the close box");
        assertEquals(AutonomyEditorPanel.dialogAnswer("Cancel", answers), AutonomyEditorPanel.ANSWER_STOP, "Cancel");
        assertEquals(AutonomyEditorPanel.dialogAnswer("Skip", answers), AutonomyEditorPanel.ANSWER_SKIP, "Skip");
        assertEquals(AutonomyEditorPanel.dialogAnswer("OK", answers), AutonomyEditorPanel.ANSWER_OK, "OK");
    }

    /**
     * The walk's prompt comes back where it was left, and a new round starts it afresh.
     *
     * Adam, on MT-454, 2026-09-16: *"when the popup closes and reopens, make sure it remembers its location unless I
     * reopen a new mass assignment round from the right click menu.  right now, every skip press re centers it, which
     * covers some of the track diagram."*  Each prompt is a new dialog, and `JOptionPane.createDialog` centres every one.
     *
     * Asked of the real walk rather than of a helper: it is started on the event thread, and this test moves the prompt,
     * presses Skip on it, and reads where the next prompt opened - so the call site is what is tested.  Then it presses
     * Cancel, starts a second round, and requires that round's first prompt NOT to open where the first round left it.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheWalkPromptStaysWhereItWasLeftUntilANewRound() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the walk's prompt needs a display");

        openBerthBehindASwitch(key(5, 1));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        final java.lang.reflect.Method walk = AutonomyEditorPanel.class.getDeclaredMethod("massAssignLengths");
        walk.setAccessible(true);

        final java.awt.Point movedTo = new java.awt.Point(37, 41);

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try { walk.invoke(panel); } catch (Exception e) { throw new RuntimeException(e); }
        });

        javax.swing.JDialog first = awaitPrompt(null);

        javax.swing.SwingUtilities.invokeAndWait(() -> first.setLocation(movedTo));

        answer(first, org.traincontrol.util.I18n.t("autosetup.ui.btnSkipOne"));

        javax.swing.JDialog second = awaitPrompt(first);

        final java.awt.Point[] secondAt = new java.awt.Point[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> secondAt[0] = second.getLocation());

        answer(second, org.traincontrol.util.I18n.t("ui.cancel"));

        assertEquals(secondAt[0], movedTo, "the next prompt of the same walk was centred again instead of opening where"
            + " the last one was left - Adam: \"every skip press re centers it\"");

        // A NEW ROUND, as the right-click menu starts one: it opens afresh.
        awaitNoPrompt();

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try { walk.invoke(panel); } catch (Exception e) { throw new RuntimeException(e); }
        });

        javax.swing.JDialog fresh = awaitPrompt(second);

        final java.awt.Point[] freshAt = new java.awt.Point[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> freshAt[0] = fresh.getLocation());

        answer(fresh, org.traincontrol.util.I18n.t("ui.cancel"));

        awaitNoPrompt();

        assertNotEquals(freshAt[0], movedTo, "a new round from the menu opened where the last round left its prompt");
    }

    /**
     * The number field has the keyboard focus, and Enter after typing a number submits it and moves on.
     *
     * Adam, on MT-454, 2026-09-16: *"make sure the entry field is focused, and when I hit enter after typing the number,
     * it submits and goes to the next one"*.  The field asked for focus when it was added to the prompt - before the
     * dialog was on screen - and when the dialog did gain focus, `JOptionPane`'s own handler gave it to the OK button.
     * So what he typed went nowhere, and Enter on the empty field counted as Skip.
     *
     * Asked of the real walk.  A desktop that never lets the test's window take focus cannot show either half, and says
     * so as a skip rather than passing.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheNumberFieldHasFocusAndEnterSubmits() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the walk's prompt needs a display");

        openBerthBehindASwitch(key(5, 1));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        final java.lang.reflect.Method walk = AutonomyEditorPanel.class.getDeclaredMethod("massAssignLengths");
        walk.setAccessible(true);

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try { walk.invoke(panel); } catch (Exception e) { throw new RuntimeException(e); }
        });

        final javax.swing.JDialog first = awaitPrompt(null);

        // THE FOCUS: waited for, because it arrives after the window is shown.
        final java.awt.Component[] owner = new java.awt.Component[1];
        final boolean[] focused = new boolean[1];
        long giveUp = System.currentTimeMillis() + 5000;

        while (System.currentTimeMillis() < giveUp)
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                focused[0] = first.isFocused();
                owner[0] = first.getFocusOwner();
            });

            if (focused[0] && owner[0] instanceof javax.swing.JTextField) break;

            Thread.sleep(50);
        }

        if (!focused[0])
        {
            answer(first, org.traincontrol.util.I18n.t("ui.cancel"));
            awaitNoPrompt();

            throw new org.testng.SkipException("this desktop did not give the prompt the keyboard focus, so where the focus"
                + " goes inside it cannot be seen");
        }

        final javax.swing.JTextField field = owner[0] instanceof javax.swing.JTextField
            ? (javax.swing.JTextField) owner[0] : null;

        if (field == null)
        {
            answer(first, org.traincontrol.util.I18n.t("ui.cancel"));
            awaitNoPrompt();

            fail("the prompt has the focus but the number field does not - it is on " + owner[0]
                + ", so what is typed goes nowhere (Adam: \"make sure the entry field is focused\")");
        }

        // ENTER AFTER TYPING, as a key press on the field.
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            field.setText("3");

            field.dispatchEvent(new java.awt.event.KeyEvent(field, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CHAR_UNDEFINED));
        });

        javax.swing.JDialog second = awaitPrompt(first);

        answer(second, org.traincontrol.util.I18n.t("ui.cancel"));
        awaitNoPrompt();

        int written = 0;

        for (AutonomySession.Stretch piece : session.stretchesALengthRuleReads())
        {
            int total = 0;

            for (TileKey square : piece.getTiles()) total += session.getStore().getTileLength(square);

            if (total == 3) written++;
        }

        assertEquals(written, 1, "Enter after typing 3 did not give the first piece its length before moving on");
    }

    // ------------------------------------------------------------------------------------------------ station maxima

    /**
     * The stations Mass Assign Max Train Lengths asks about: those on this page with no maximum, row by row.
     *
     * Adam, 2026-09-17: *"add a similar feature to walk stations that don't have a max length set up, so I can enter
     * it"*.  Nothing on this railway is measured and no locomotive has a length, which is where the `NO_MAX_TRAIN_LENGTH`
     * notice stays quiet - the walk must not, because opening it is saying lengths are being modelled.
     */
    @Test
    public void testTheMaximumWalkAsksAboutStationsWithNone() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setStation(key(3, 0), true);
        session.setPointProperty(key(3, 0), "maxTrainLength", 6);

        assertFalse(session.getStore().measuresAnyTrack(), "precondition: something on this railway is measured");

        assertEquals(session.stationsWithoutAMaximumOn("main"), Arrays.asList(key(5, 1)),
            "the stations without a maximum are not the ones asked about - 3,0 has one and 1,1 is no station");

        session.setPointProperty(key(3, 0), "maxTrainLength", 0);

        assertEquals(session.stationsWithoutAMaximumOn("main"), Arrays.asList(key(3, 0), key(5, 1)),
            "a maximum of 0 is any length, so that station is asked about too - and the upper row first");

        assertTrue(session.stationsWithoutAMaximumOn("elsewhere").isEmpty(), "a station on another page was offered");
    }

    /**
     * A maximum of 0 is refused, and a station that already has one keeps it.
     *
     * 0 is "any length" to the railway (`Point.validateTrainLength`), which is what the station already has.
     */
    @Test
    public void testAMaximumOfZeroIsRefusedAndOneAlreadySetIsKept() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        assertFalse(session.assignMaxTrainLength(key(5, 1), 0), "a maximum of 0 was taken");
        assertNull(session.getPointProperty(key(5, 1), "maxTrainLength"), "a refused 0 was written anyway");

        assertTrue(session.assignMaxTrainLength(key(5, 1), 8), "a real maximum was refused");
        assertEquals(session.getPointProperty(key(5, 1), "maxTrainLength"), 8);

        assertFalse(session.assignMaxTrainLength(key(5, 1), 9), "a station that has a maximum was given another");
        assertEquals(session.getPointProperty(key(5, 1), "maxTrainLength"), 8, "the maximum already set was overwritten");

        assertFalse(session.assignMaxTrainLength(key(1, 1), 5), "a square that is not a station was given a maximum");
    }

    /**
     * The walk itself: a maximum typed and entered is written to the station asked about, and the walk moves on.
     *
     * Asked of the real walk on the event thread, through the same prompt Mass Assign Lengths uses - so its title is the
     * walk's own name, Enter in the number box submits, and Skip leaves the next station as it was.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheMaximumWalkWritesWhatIsTypedAndMovesOn() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the walk's prompt needs a display");

        openBerthBehindASwitch(key(5, 1));

        session.setStation(key(3, 0), true);

        assertEquals(session.stationsWithoutAMaximumOn("main"), Arrays.asList(key(3, 0), key(5, 1)), "precondition");

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        final java.lang.reflect.Method walk = AutonomyEditorPanel.class.getDeclaredMethod("massAssignMaxTrainLengths");
        walk.setAccessible(true);

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try { walk.invoke(panel); } catch (Exception e) { throw new RuntimeException(e); }
        });

        final javax.swing.JDialog first = awaitPrompt(null, MAXIMA);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JTextField field = findField(first.getContentPane());

            assertNotNull(field, "the prompt has no number box");

            field.setText("7");
        });

        // OK, NOT ENTER.  Enter goes through `JTextField.NotifyAction`, which acts on the FOCUSED text
        // component - so dispatching it only works while this desktop has given the prompt the keyboard
        // focus, and a battery machine does not always.  This claim is about what the walk writes and where
        // it goes next; Enter has its own claim in `testTheNumberFieldHasFocusAndEnterSubmits`, which waits
        // for the focus and skips rather than fails without it.
        answer(first, org.traincontrol.util.I18n.t("ui.ok"));

        javax.swing.JDialog second = awaitPrompt(first, MAXIMA);

        answer(second, org.traincontrol.util.I18n.t("autosetup.ui.btnSkipOne"));

        awaitNoPrompt(MAXIMA);

        assertEquals(session.getPointProperty(key(3, 0), "maxTrainLength"), 7,
            "the maximum typed for the first station was not written to it");

        assertNull(session.getPointProperty(key(5, 1), "maxTrainLength"), "the skipped station was given a maximum");

        assertEquals(session.stationsWithoutAMaximumOn("main"), Arrays.asList(key(5, 1)),
            "the walk's own list does not know the first station now has a maximum");
    }

    /**
     * Clear All Max Train Lengths takes every maximum off, on every page, and counts only the ones above 0.
     *
     * Adam, 2026-09-17: *"Add a right click menu open to clear all max station train lengths (grouped with the other
     * clear options)"*.  The second page is the half a per-page clear would fail; the 0 is the half that would count
     * stations with nothing to lose, because the setup writes an explicit 0 on every destination.
     */
    @Test
    public void testClearAllMaxTrainLengthsClearsEveryPage() throws IOException
    {
        openBerthAndASecondPage();

        session.setPointProperty(key(5, 1), "maxTrainLength", 8);
        session.setStation(key(3, 0), true);
        session.setPointProperty(key(3, 0), "maxTrainLength", 0);
        session.setPointProperty(new TileKey("other", 3, 1), "maxTrainLength", 5);

        assertEquals(new HashSet<>(session.tilesWithAMaxTrainLength()),
            set(key(5, 1), new TileKey("other", 3, 1)), "the stations with a maximum are not the ones counted");

        assertEquals(session.clearEveryMaxTrainLength(), 2, "the clear did not report the two maxima it took");

        assertNull(session.getPointProperty(key(5, 1), "maxTrainLength"), "the maximum on this page survived");
        assertNull(session.getPointProperty(new TileKey("other", 3, 1), "maxTrainLength"),
            "the maximum on the other page survived - Clear All is across every page");

        assertTrue(session.tilesWithAMaxTrainLength().isEmpty());

        assertTrue(session.stationsWithoutAMaximumOn("main").contains(key(5, 1)),
            "Mass Assign Max Train Lengths does not offer the station whose maximum was just cleared");
    }

    /**
     * The menu item sits with the other clears, carries the count, greys when there is nothing to clear, and its
     * tooltip is the sentence the confirmation shows.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheClearMaxTrainLengthsItemCountsAndGreys() throws Exception
    {
        openBerthAndASecondPage();

        session.setPointProperty(key(5, 1), "maxTrainLength", 8);
        session.setPointProperty(new TileKey("other", 3, 1), "maxTrainLength", 5);

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        javax.swing.JMenuItem item = clearMaximaItem(panel);

        assertNotNull(item, "the Bulk Tools menu has no Clear All Max Train Lengths item");
        assertTrue(item.isEnabled(), "the item is greyed with two maxima to clear");
        assertEquals(item.getText(), org.traincontrol.util.I18n.f("autolayout.ui.menuClearAllMaxTrainLengths", 2));
        assertEquals(item.getToolTipText().replaceAll("<[^>]*>", ""),
            org.traincontrol.util.I18n.f("autolayout.ui.confirmClearAllMaxTrainLengths", 2),
            "the tooltip is not the sentence the confirmation shows");

        session.clearEveryMaxTrainLength();

        item = clearMaximaItem(panel);

        assertFalse(item.isEnabled(), "the item offers to clear maxima on a railway that has none");
        assertEquals(item.getToolTipText().replaceAll("<[^>]*>", ""),
            org.traincontrol.util.I18n.t("autosetup.ui.infoNoMaxTrainLengthsToClear"), "the greyed item does not say why");
    }

    /** The item, off the Bulk Tools menu as the right-click menu builds it, found by the clear it sits after. */
    private static javax.swing.JMenuItem clearMaximaItem(AutonomyEditorPanel panel) throws Exception
    {
        final javax.swing.JMenuItem[] found = new javax.swing.JMenuItem[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu bulk = panel.buildBulkMenuForTest();

            String lengths = org.traincontrol.util.I18n.f("autolayout.ui.menuClearAllTrackLengths", 0);
            lengths = lengths.substring(0, lengths.indexOf('(')).trim();

            for (int i = 0; i + 1 < bulk.getItemCount(); i++)
            {
                javax.swing.JMenuItem item = bulk.getItem(i);

                // GROUPED WITH THE OTHER CLEARS: the item directly after Clear All Track Lengths.
                if (item != null && item.getText() != null && item.getText().startsWith(lengths))
                {
                    found[0] = bulk.getItem(i + 1);
                }
            }
        });

        return found[0];
    }

    // ------------------------------------------------------------------------------------ the 2026-09-19 review round

    /**
     * A square two roads cross is in no piece, is asked for on its own, and counts on both roads (SET-B2).
     *
     * Adam, 2026-09-19, shown that such a square went into whichever leg was walked first and was missing from the
     * other: *"For crossings: if its length is set, count that length once in each direction."*  The reduction
     * already counts it on both roads - it adds every tile of every leg - so the only thing that could be wrong was
     * the walk putting it inside one road's piece, whose whole length is then shared over its squares and counted on
     * the other road as well.
     *
     * Measured before the fix, on this fixture: the crossing sat in the east-west piece, the north-south prompt
     * covered four squares instead of five, and the north-south legs measured 9 and 10 where 7 and 8 had been typed.
     */
    @Test
    public void testACrossingIsCutOutOfEveryPieceAndCountsOnBothRoads() throws IOException
    {
        openACrossing();

        TileKey crossing = key(3, 2);

        assertEquals(session.sharedSquaresALengthRuleReads(), set(crossing),
            "the square two roads cross is not asked for on its own");

        List<AutonomySession.Stretch> pieces = session.stretchesNeedingALength();

        for (AutonomySession.Stretch piece : pieces)
        {
            assertFalse(piece.getTiles().contains(crossing),
                "the crossing is inside a piece, so its share is counted on the other road too: " + describe(pieces));
        }

        assertEquals(pieces.size(), 4,
            "each road should be cut into two pieces at the crossing: " + describe(pieces));

        assertNoSquareIsInTwoPieces(pieces);

        // EVERY PIECE 6, THE CROSSING 3.  Each road is then 6 + 6 + 3 = 15 of track, of which the reduction counts
        // everything but the square the train starts on - 3 of it - so both legs measure 12.
        for (AutonomySession.Stretch piece : pieces) assertTrue(session.assignStretchLength(piece, 6), "6 was refused");

        assertTrue(session.assignSwitchLength(session.sharedSquaresNeedingALengthOn("main"), 3),
            "the crossing would not take a length of its own");

        session.rebuild();

        assertEquals(edge(key(1, 2), key(5, 2)).getLength(), 12, "the east-west road does not add up");
        assertEquals(edge(key(3, 0), key(3, 4)).getLength(), 12, "the north-south road does not add up");

        assertEquals(session.getStore().getTileLength(crossing), 3,
            "the crossing holds something other than the one length typed for it");

        assertTrue(session.squaresNeedingALength().isEmpty(),
            "everything has a length and something is still highlighted: " + session.squaresNeedingALength());
    }

    /**
     * An unmeasured crossing is highlighted and offered, and the walk's counts include it.
     */
    @Test
    public void testAnUnmeasuredCrossingIsOfferedAndHighlighted() throws IOException
    {
        openACrossing();

        assertTrue(session.squaresNeedingALength().contains(key(3, 2)),
            "the crossing is in no piece now, so nothing would ever ask the operator to measure it");

        assertEquals(session.sharedSquaresNeedingALengthOn("main"), set(key(3, 2)));
        assertTrue(session.sharedSquaresNeedingALengthOn("elsewhere").isEmpty(), "another page was offered it");

        session.setTileLength(key(3, 2), 3);

        assertFalse(session.squaresNeedingALength().contains(key(3, 2)), "a measured crossing is still highlighted");
        assertTrue(session.sharedSquaresNeedingALengthOn("main").isEmpty(), "a measured crossing is still offered");
    }

    /**
     * A crossing only one road runs over is ordinary track, and stays inside its piece.
     *
     * Both halves of the rule are needed: the geometry says the square carries two roads, and the legs say more than
     * one of them is used.  Without the second half every square in front of a switch qualifies - each leg through
     * the points runs over it - and the pieces would be cut to bits; without the first, a crossing whose other road
     * is bare track nobody can reach becomes a prompt of its own.
     */
    @Test
    public void testACrossingOnlyOneRoadUsesStaysOrdinaryTrack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CROSSING, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 2, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.rebuild();

        assertTrue(session.getRoutes(key(3, 2)).size() > 1,
            "precondition: this square does not carry two roads, so it cannot show what the second half of the rule"
            + " is for");

        assertTrue(session.sharedSquaresALengthRuleReads().isEmpty(),
            "a crossing with track on one road only is asked for on its own, although it is ordinary track there");

        List<AutonomySession.Stretch> pieces = session.stretchesNeedingALength();

        assertEquals(pieces.size(), 1, "the one road was cut into pieces: " + describe(pieces));
        assertTrue(pieces.get(0).getTiles().contains(key(3, 2)), "the crossing is not in the piece it belongs to");
    }

    /**
     * 1,2 - 2,2 - CROSSING 3,2 - 4,2 - 5,2 east to west, and 3,0 - 3,1 - the crossing - 3,3 - 3,4 north to south,
     * each road running between two sensors.  Both roads carry track, so the crossing is a square two legs run over.
     */
    private void openACrossing() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CROSSING, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 2, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 1, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 3, 1, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 4, 1, 0, 8, 14, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.rebuild();
    }


    /**
     * A negative maximum train length is refused at the door, and one already stored can be cleared (SET-B1).
     *
     * `promptNumber` is shared with `priority`, where a negative is meaningful, so the refusal is asked per key.  The
     * layer below does not clamp: `Layout.fromJSON` invalidates the WHOLE configuration for a maximum below 0, so a
     * number typed here took the railway out of autonomy with only a log line - and Clear All Max Train Lengths, which
     * counted only maxima above 0, greyed itself on the one setting that needed taking off.
     *
     * Measured before the fix, on the fixture below: `Layout.fromJSON` came back `isValid() == false` and
     * `tilesWithAMaxTrainLength()` was empty.
     */
    @Test
    public void testANegativeMaximumTrainLengthIsRefused() throws IOException
    {
        assertNotNull(AutonomyEditorPanel.whyNotThisNumber("maxTrainLength", -3),
            "the door takes a negative maximum, which stops the configuration loading");

        assertNull(AutonomyEditorPanel.whyNotThisNumber("maxTrainLength", 0), "0 is any length, and is allowed");
        assertNull(AutonomyEditorPanel.whyNotThisNumber("maxTrainLength", 5), "a real maximum is allowed");

        assertNull(AutonomyEditorPanel.whyNotThisNumber("priority", -3),
            "a negative priority is meaningful and was refused with the maximum");

        // AND THE ONE ALREADY WRITTEN can be taken off, which is the only remedy for a railway that will not load.
        openBerthBehindASwitch(key(5, 1));

        session.setPointProperty(key(5, 1), "maxTrainLength", -3);

        assertEquals(session.tilesWithAMaxTrainLength(), Arrays.asList(key(5, 1)),
            "Clear All Max Train Lengths cannot see a negative maximum, so it greys itself on the setting that is"
            + " stopping the railway loading");

        assertEquals(session.clearEveryMaxTrainLength(), 1, "the clear did not take it off");
        assertNull(session.getPointProperty(key(5, 1), "maxTrainLength"));
    }

    /**
     * After a walk, Segment Length shows the whole run and writes the whole run (SET-B3).
     *
     * `behaviour.md` section 5b: *"a run of plain track has one square that speaks for it, and both doors write
     * there, so measuring a run through both does not count it twice."*  Mass Assign Lengths is a third door and it
     * shares a piece's whole length over every square (MAL-B2), so the leader came to hold a share.  Measured before
     * the fix on this fixture: the dialog opened on 1 for a run measured as 7, and writing 4 left the run at 6.
     */
    @Test
    public void testTheSingleDoorSpeaksForTheWholeRunAfterAWalk() throws Exception
    {
        // NO DISPLAY IS NEEDED (SVA-C5): the panel is built without one here and no dialog is opened - the two
        // public seams are asked directly.  The skip that stood here would have hidden the claim on a headless run.
        openARunOfTwoPlainSquares();

        List<AutonomySession.Stretch> pieces = session.stretchesNeedingALength();

        assertEquals(pieces.size(), 1, "precondition: the fixture made " + pieces.size() + " pieces");
        assertTrue(session.assignStretchLength(pieces.get(0), 7), "the walk refused 7");

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        TileKey leader = panel.squareTheLengthWouldGoOn(key(3, 1));

        assertNotNull(leader, "precondition: the menu offers no length on a plain square of the run");
        assertTrue(session.getStore().getTileLength(leader) < 7,
            "precondition: the walk did not share the length out, so there is nothing for this to be about");

        // The walk gave the four squares of the piece 2, 2, 1 and 2; the RUN is the two plain squares, so what
        // the dialog should open on is 3 - measured, not assumed.
        assertEquals(panel.lengthShownFor(key(3, 1)), 3,
            "Segment Length opens on one square's share rather than on what the run measures");

        panel.setRunLength(key(3, 1), 4);

        int run = session.getStore().getTileLength(key(2, 1)) + session.getStore().getTileLength(key(3, 1));

        assertEquals(run, 4, "the run was given 4 through Segment Length and measures " + run);

        assertEquals(panel.lengthShownFor(key(2, 1)), 4, "the dialog opened on a follower disagrees with its run");
    }

    /**
     * 1,1 sensor - 2,1 - 3,1 - 4,1 station: a run of two plain squares between two sensors.
     */
    private void openARunOfTwoPlainSquares() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(key(4, 1), true);
        session.rebuild();
    }

    private static final String LENGTHS = "autosetup.ui.menuMassAssignLengths";
    private static final String MAXIMA = "autosetup.ui.menuMassAssignMaxTrainLengths";

    private static javax.swing.JTextField findField(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JTextField) return (javax.swing.JTextField) child;

            if (child instanceof java.awt.Container)
            {
                javax.swing.JTextField found = findField((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static javax.swing.JDialog awaitPrompt(javax.swing.JDialog notThisOne) throws Exception
    {
        return awaitPrompt(notThisOne, LENGTHS);
    }

    private static javax.swing.JDialog awaitPrompt(javax.swing.JDialog notThisOne, String titleKey) throws Exception
    {
        String title = org.traincontrol.util.I18n.t(titleKey);
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window != notThisOne && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle()))
                {
                    return (javax.swing.JDialog) window;
                }
            }

            Thread.sleep(50);
        }

        // WHAT WAS ON SCREEN INSTEAD, because "no prompt appeared" is the same message whether the walk never
        // opened one or an earlier dialog is still up in front of it (VB2 round).
        StringBuilder open = new StringBuilder();

        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (!window.isShowing()) continue;

            open.append(open.length() > 0 ? ", " : "").append(window.getClass().getSimpleName());

            if (window instanceof javax.swing.JDialog) open.append(" \"").append(((javax.swing.JDialog) window).getTitle()).append("\"");
            if (window instanceof javax.swing.JFrame) open.append(" \"").append(((javax.swing.JFrame) window).getTitle()).append("\"");
        }

        fail("no " + title + " prompt appeared.  Showing now: " + (open.length() == 0 ? "nothing" : open));

        return null;
    }

    private static void awaitNoPrompt() throws Exception
    {
        awaitNoPrompt(LENGTHS);
    }

    private static void awaitNoPrompt(String titleKey) throws Exception
    {
        String title = org.traincontrol.util.I18n.t(titleKey);
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            boolean showing = false;

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) showing = true;
            }

            if (!showing) return;

            Thread.sleep(50);
        }

        fail("the " + title + " prompt did not close");
    }

    /** Presses one of the prompt's buttons, the way a click does: by giving the option pane that value. */
    private static void answer(javax.swing.JDialog dialog, String button) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            JOptionPane pane = findPane(dialog.getContentPane());

            assertNotNull(pane, "the prompt has no option pane to answer");

            for (Object option : pane.getOptions())
            {
                if (button.equals(option)) pane.setValue(option);
            }
        });
    }

    private static JOptionPane findPane(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof JOptionPane) return (JOptionPane) child;

            if (child instanceof java.awt.Container)
            {
                JOptionPane found = findPane((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    // ------------------------------------------------------------------------------------------------ the railways

    /**
     * 1,1 sensor - 2,1 - SWITCH 3,1 - 4,1 - 5,1 sensor, with the switch's branch going to a sensor at 3,0 so that it is a
     * real fork.  The given square becomes a parking berth.
     */
    private void openBerthBehindASwitch(TileKey berth) throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        wire(page, 3, 1, 7);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(berth, true);
        session.setAutoDestination(berth, false);
        session.rebuild();
    }

    /**
     * The berth railway on "main", and a second page, "other": 1,1 sensor - 2,1 - 3,1 sensor, with 3,1 a station.
     */
    private void openBerthAndASecondPage() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        wire(page, 3, 1, 7);

        page.setPageId("1");

        LayoutDiagram other = new LayoutDiagram("other", 9, 4, null, null);

        other.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 21, 21, accessoryDecoderType.MM2, null);
        other.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        other.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 22, 22, accessoryDecoderType.MM2, null);

        other.setPageId("2");

        session.open(Arrays.asList(page, other));
        session.initialize("Lengths");
        session.setStation(key(5, 1), true);
        session.setStation(new TileKey("other", 3, 1), true);
        session.rebuild();
    }

    /**
     * 1,1 sensor - SWITCH 2,1 - SWITCH 3,1 - 4,1 sensor, both toes west, each branching north to a sensor above it.  No
     * square between the two switches.
     */
    private void openTwoSwitchesBackToBack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 2, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 8, 8, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 8, 14, accessoryDecoderType.MM2, null);

        wire(page, 2, 1, 7);
        wire(page, 3, 1, 8);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(key(4, 1), true);
        session.rebuild();
    }

    private static void wire(LayoutDiagram page, int x, int y, int address)
    {
        page.getComponent(x, y).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, address, org.traincontrol.base.Accessory.accessoryType.SWITCH, accessoryDecoderType.MM2,
            "Switch " + address, false, 0));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static TileKey key(int x, int y)
    {
        return new TileKey("main", x, y);
    }

    private static Set<TileKey> set(TileKey... keys)
    {
        return new HashSet<>(Arrays.asList(keys));
    }

    private int length(int x, int y)
    {
        return session.getStore().getTileLength(key(x, y));
    }

    private AutonomySession.Stretch piece(TileKey containing)
    {
        for (AutonomySession.Stretch piece : session.stretchesALengthRuleReads())
        {
            if (piece.getTiles().contains(containing)) return piece;
        }

        fail("no piece contains " + containing + ": " + describe(session.stretchesALengthRuleReads()));

        return null;
    }

    private boolean pieceNeedsALength(TileKey containing)
    {
        for (AutonomySession.Stretch piece : session.stretchesNeedingALengthOn("main"))
        {
            if (piece.getTiles().contains(containing)) return true;
        }

        return false;
    }

    private GraphReducer.ReducedEdge edge(TileKey start, TileKey end)
    {
        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            if (edge.getStart().equals(start) && edge.getEnd().equals(end)) return edge;
        }

        fail("no leg from " + start + " to " + end);

        return null;
    }

    private static Set<Set<TileKey>> tileSets(List<AutonomySession.Stretch> pieces)
    {
        Set<Set<TileKey>> out = new HashSet<>();

        for (AutonomySession.Stretch piece : pieces) out.add(new HashSet<>(piece.getTiles()));

        return out;
    }

    private static void assertNoSquareIsInTwoPieces(List<AutonomySession.Stretch> pieces)
    {
        Set<TileKey> seen = new HashSet<>();

        for (AutonomySession.Stretch piece : pieces)
        {
            for (TileKey square : piece.getTiles())
            {
                assertTrue(seen.add(square), square + " is in two pieces, so the second answer overwrites the first: "
                    + describe(pieces));
            }
        }
    }

    private static boolean marked(AutonomyEditorPanel panel, TileKey tile)
    {
        TileAnnotation annotation = panel.annotationFor(tile);

        return annotation != null && annotation.isUnmeasured();
    }

    private static String describe(List<AutonomySession.Stretch> stretches)
    {
        StringBuilder out = new StringBuilder("[");

        for (AutonomySession.Stretch stretch : stretches) out.append(stretch.getTiles()).append("; ");

        return out.append("]").toString();
    }

    private static void delete(File file)
    {
        if (file == null) return;

        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children) delete(child);
        }

        file.delete();
    }

    /**
     * A square that stops being a station keeps no maximum train length (SET-C2).
     *
     * The setting is offered only inside the station menu and read only of a square a journey ends at, so on a
     * square that is not a station it decides nothing - but the bulk clear counts every point carrying one, and
     * said "on {0} stations" about squares no menu would ever show one on.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testADemotedStationKeepsNoMaximumTrainLength() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setPointProperty(key(5, 1), "maxTrainLength", 8);

        assertEquals(session.tilesWithAMaxTrainLength(), Arrays.asList(key(5, 1)), "precondition");

        session.setStation(key(5, 1), false);

        assertTrue(session.tilesWithAMaxTrainLength().isEmpty(),
            "a square that is no longer a station still carries a maximum, and the bulk clear counts it as a station");

        assertNull(session.getPointProperty(key(5, 1), "maxTrainLength"));
    }

    /**
     * A leg that runs over one square twice - a figure of eight - is cut at it too (SVA-C2, VB2-B3).
     *
     * The rule counts how often a crossing is run OVER rather than how many legs run over it, because a single leg
     * that uses both of a crossing's roads reads that square twice: its own answer would then be counted twice, and
     * that is the very error Adam's ruling is about.  Counting legs would have missed it, and nothing pinned the
     * difference until this.
     *
     * The fixture is one leg between two sensors that goes east through the crossing, round a loop, and back through
     * it southbound.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testALegThatCrossesItsOwnSquareTwiceIsCutAtIt() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CROSSING, 3, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        // The loop back over the top: W-N at 5,2, up the side, S-W at 5,0, along, and E-S at 3,0 into the crossing.
        page.addComponent(componentType.CURVE, 5, 2, 2, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, 1, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CURVE, 5, 0, 3, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CURVE, 3, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 1, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.addComponent(componentType.STRAIGHT, 3, 3, 1, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 4, 1, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.rebuild();

        TileKey crossing = key(3, 2);

        // PRECONDITION: one leg really does run over the crossing twice.  Without it this passes on a fixture where
        // the loop never joined up, which would test nothing at all.
        int twice = 0;

        for (GraphReducer.ReducedEdge leg : session.getReducer().getEdges())
        {
            int over = 0;

            for (GraphReducer.TileStep step : leg.getPath())
            {
                if (crossing.equals(step.getTile())) over++;
            }

            if (over > 1) twice++;
        }

        assertTrue(twice > 0, "precondition: no leg runs over the crossing twice, so the fixture does not show this");

        assertTrue(session.sharedSquaresALengthRuleReads().contains(crossing),
            "a square one leg runs over twice is left inside that leg's piece, so its share is counted on both of"
            + " the roads the leg uses");

        for (AutonomySession.Stretch piece : session.stretchesNeedingALength())
        {
            assertFalse(piece.getTiles().contains(crossing), "the crossing is in a piece: " + describe(session.stretchesNeedingALength()));
        }
    }
}
