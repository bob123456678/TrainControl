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

            field.dispatchEvent(new java.awt.event.KeyEvent(field, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CHAR_UNDEFINED));
        });

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

        fail("no " + title + " prompt appeared");

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
}
