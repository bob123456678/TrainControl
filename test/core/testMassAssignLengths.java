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

    /**
     * How many squares of Adam's own **1 - Main** the crossing prompt really asks about (VC2-C5, SVA-C2).
     *
     * **MT-459 says "which may be fewer" and has never said a number.**  The page has four squares SHAPED
     * like a crossing - the crossing at 18,10 and the double curves at 20,10, 21,10 and 11,11 - and the walk
     * asks only about the ones trains actually run over on BOTH roads.  Three validation rounds deferred
     * measuring it, so the entry has been telling him to expect a count he cannot check.
     *
     * Read off the frozen snapshot of his railway rather than a fixture, because the number is a fact about
     * his layout and nothing else.  If the reduction ever stops seeing one of those roads, this says so in
     * the same breath as the manual test goes stale.
     *
     * @throws Exception from the sandbox
     */
    @Test
    public void testHowManySharedSquaresHisOwnMainPageAsksAbout() throws Exception
    {
        // OPENED INSIDE THE TRY (TSX-B8, OB-111).  The open redirects the machine-global layout
        // preference; anything throwing between it and the try - including a SkipException, which is
        // not a failure at all - would leave that preference pointing at a folder under %TEMP%, which
        // is the railway TrainControl opens next time it starts.
        support.LayoutSandbox sandbox = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            try
            {
                java.util.List<org.traincontrol.base.LayoutDiagram> pages = new java.util.ArrayList<>();

                for (String name : model.getLayoutList()) pages.add(model.getLayout(name));

                assertFalse(pages.isEmpty(), "precondition: the snapshot opened with no pages at all");

                session.open(pages);
                session.initialize("Lengths");
                session.rebuild();

                java.util.Set<TileKey> shared = session.sharedSquaresALengthRuleReads();

                int onMain = 0;

                for (TileKey square : shared)
                {
                    if ("1 - Main".equals(square.getPage())) onMain++;
                }

                // MEASURED 2026-09-20: NONE of them, on that page.
                //
                // All four squares shaped like a crossing on 1 - Main are run over on one road only, so
                // the crossing prompt has nothing to ask about there.  MT-459 said "which may be fewer";
                // the answer is fewer by all four, and the entry now says so.
                assertEquals(onMain, 0,
                    "the crossing prompt asks about " + onMain + " squares of 1 - Main.  It asked about"
                    + " none when this was measured, and MT-459 tells him so - if it has moved, the entry"
                    + " has gone stale: " + shared);

                // THE CONTROL: the walk does find shared squares elsewhere, so nought on this page is an
                // answer about the page rather than about a rule that has stopped running.
                assertFalse(shared.isEmpty(),
                    "the rule found no shared squares anywhere on his railway, so the nought above says"
                    + " nothing about 1 - Main");
            }
            finally
            {
                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
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
     * Fewer units than squares can be entered, and so can 0 - which answers the piece and reads as no length (OB-274).
     *
     * Two squares, one unit: one square gets it and the other rightly holds nothing.  The first version demanded a unit
     * per square, so a short piece drawn with several squares could not be entered at all.
     *
     * 0 was refused until 2026-09-23 - *"0 is the same as no length at all"* - which left a piece that genuinely has
     * none offered for ever.  Adam: *"allow a length of 0 as a length that is set deliberately ... same meaning to the
     * model, but this will allow everything to get assigned without what appears to be a skip."*  So 0 is accepted, the
     * piece stops being asked about, and its squares still read as unmeasured to every length rule.
     */
    @Test
    public void testFewerUnitsThanSquaresCanBeEnteredAndZeroIsAnAnswer() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        AutonomySession.Stretch beforeTheSwitch = piece(key(1, 1));

        assertEquals(session.leastWholeLengthOf(beforeTheSwitch), 0, "the least a piece can be given is 0 since OB-274");

        assertTrue(pieceNeedsALength(key(1, 1)), "precondition: the piece is not asked about before anything is typed");

        assertTrue(session.assignStretchLength(beforeTheSwitch, 0), "0 was refused, though it is now an answer");

        assertEquals(length(1, 1) + length(2, 1), 0, "a deliberate 0 reads as a length");

        assertFalse(pieceNeedsALength(key(1, 1)),
            "a piece answered 0 is still offered by the walk - the phantom skip OB-274 is about");

        assertFalse(session.squaresNeedingALength().contains(key(2, 1)),
            "a square answered 0 is still highlighted as needing a length");

        assertFalse(session.getStore().measuresAnyTrack(), "a deliberate 0 made the railway read as measured");

        // CONTROL, on the other piece of the same railway: one unit over two squares is still shared and still measures.
        AutonomySession.Stretch theBerth = piece(key(5, 1));

        assertTrue(session.assignStretchLength(theBerth, 1), "one unit over two squares was refused");
        assertEquals(length(5, 1) + length(4, 1), 1);
        assertFalse(pieceNeedsALength(key(5, 1)), "a piece whose total is now 1 is still asked for");
    }

    /**
     * 0 answers the switches too - two switches back to back are his adjacent tracks - and a switch already measured
     * keeps its length (OB-274).
     */
    @Test
    public void testZeroAnswersTheSwitchesAndKeepsAMeasuredOne() throws IOException
    {
        openTwoSwitchesBackToBack();

        session.setTileLength(key(2, 1), 3);

        java.util.Set<TileKey> switches = session.switchesNeedingALengthOn("main");

        assertEquals(switches, set(key(3, 1)), "precondition: the switch without a length is the one asked about");

        assertTrue(session.assignSwitchLength(java.util.Arrays.asList(key(2, 1), key(3, 1)), 0),
            "0 was refused for the switches");

        assertTrue(session.switchesNeedingALengthOn("main").isEmpty(), "a switch answered 0 is still offered");
        assertFalse(session.squaresNeedingALength().contains(key(3, 1)), "a switch answered 0 is still highlighted");
        assertEquals(length(3, 1), 0, "a switch answered 0 reads as a length");
        assertEquals(length(2, 1), 3, "answering 0 overwrote the switch that was already measured");
    }

    /**
     * One turnout length goes to every switch on the page that has none, and a switch already measured keeps its own.
     *
     * This also refused 0 until OB-274 made a deliberate 0 an answer; `testZeroAnswersTheSwitchesAndKeepsAMeasuredOne`
     * is where 0 is asked about now.
     */
    @Test
    public void testOneTurnoutLengthGoesToEverySwitchStillWithout() throws IOException
    {
        openTwoSwitchesBackToBack();

        session.setTileLength(key(2, 1), 3);

        assertEquals(session.switchesNeedingALengthOn("main"), set(key(3, 1)), "a measured switch is asked for again");

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
     * so as a skip rather than passing - which is why this is the ONLY test here that presses Enter: `NotifyAction`
     * acts on the focused text component, so every other walk test presses OK instead (VC2-C2).
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
     * The stations Mass Assign Station Max Train Lengths asks about: those on this page with no maximum, row by row.
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
     * walk's own name, and Skip leaves the next station as it was.  It presses OK rather than Enter, because Enter acts
     * on whichever text component has the keyboard focus and a battery machine does not always give a window one
     * (VC2-C2); `testTheNumberFieldHasFocusAndEnterSubmits` is where Enter is pinned.
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
     * Clear All Station Max Train Lengths takes every maximum off, on every page, and counts only the ones above 0.
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
            "Mass Assign Station Max Train Lengths does not offer the station whose maximum was just cleared");
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

        assertNotNull(item, "the Bulk Tools menu has no Clear All Station Max Train Lengths item");
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

    // ------------------------------------------------------------------------------ OB-273: route tiles take no length

    /**
     * A route tile is in no piece, and does not cut the piece it sits in (Adam, 2026-09-23, OB-273).
     *
     * *"a route tile should not need or accept a length.  it just implicitly connects things as if it were a
     * crossing."*  The railway here is sensor, straight, ROUTE TILE, straight, sensor: one leg, which the route
     * tile conducts straight through.  Before the ruling the route tile was an ordinary square of the piece.
     */
    @Test
    public void testARouteTileIsInNoPieceAndDoesNotCutIt() throws IOException
    {
        openARouteTileInARun();

        AutonomySession.Stretch piece = piece(key(2, 1));

        assertNotNull(piece, "precondition: the straight beside the route tile is in no piece at all, so the route"
            + " tile may not be conducting and nothing below is about it");

        assertFalse(piece.getTiles().contains(key(3, 1)), "the route tile is a square of a piece: " + piece);

        assertTrue(piece.getTiles().contains(key(4, 1)),
            "the piece stops at the route tile instead of running on across it: " + piece);
    }

    /**
     * His example, as he gave it: three squares and a route tile, 4 typed, comes out 2, 1, 1 - and nothing on the
     * route tile.
     *
     * *"for example 3 regular tiles, 1 route, length 4 = 3 tiles have length 1.  i'd prefer one to have length 2, and
     * two others length 1"*.  The even share is unchanged (OB-275: *"let's stick to a then"*); what changed is that the
     * route tile is not one of the squares it is shared over.
     */
    @Test
    public void testALengthIsSharedAroundARouteTileAndNotOnIt() throws IOException
    {
        openARouteTileInARun();

        AutonomySession.Stretch piece = piece(key(2, 1));

        assertEquals(piece.getTiles().size(), 4, "precondition: sensor, straight, straight, sensor - " + piece);

        assertTrue(session.assignStretchLength(piece, 5), "a whole length the piece can hold was refused");

        assertEquals(length(3, 1), 0, "the route tile was given a share of the piece's length");

        assertEquals(length(1, 1) + length(2, 1) + length(4, 1) + length(5, 1), 5,
            "the shares of the squares either side do not add up to what was typed");
    }

    /**
     * A route tile with track on all four sides conducts two roads, like a crossing - and is still not asked for a
     * length on its own the way a crossing is (OB-273).
     */
    @Test
    public void testARouteTileIsNotAskedForOnItsOwnLikeACrossing() throws IOException
    {
        openARouteTileCrossroads();

        assertTrue(session.getRoutes(key(2, 2)).size() > 1,
            "precondition: the route tile conducts one road, not two, so it is not the crossing shape at all");

        assertFalse(session.sharedSquaresALengthRuleReads().contains(key(2, 2)),
            "a route tile two roads cross is asked for a length on its own, as a crossing is");

        assertFalse(session.squaresNeedingALength().contains(key(2, 2)),
            "a route tile is listed as a square still needing a length");
    }

    /**
     * A berth whose approach runs over a route tile is not half measured because of it (OB-273) - with the control
     * that a plain square left unmeasured still makes it so.
     *
     * The notice Adam saw on TopR1ParkLong and TopR1ParkShort: every square that takes a length was measured, and the
     * squares counted as unmeasured were route tiles.
     */
    @Test
    public void testARouteTileDoesNotMakeABerthHalfMeasured() throws IOException
    {
        openARouteTileInARun();

        session.setAutoDestination(key(5, 1), false);

        // The berth measured too: its own square counts, as the berth rule counts it (TDY-B2).
        session.getStore().setTileLength(key(5, 1), 2);

        session.getStore().setTileLength(key(2, 1), 2);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "CONTROL: with the straight at 4,1 unmeasured the berth is not reported half measured, so the check"
            + " below could pass by never firing at all");

        session.getStore().setTileLength(key(4, 1), 2);

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "the berth is reported half measured when every square that takes a length is measured - the route tile"
            + " is being counted as unmeasured track");
    }

    /**
     * The reducer never names a route tile as the unmeasured track after the last switch (OB-273).
     *
     * That list is what the "trains turn round here and its track has no length recorded" notice asks for, so a
     * route tile on it would be a notice asking for a length the tile does not take.
     */
    @Test
    public void testTheReducerNeverAsksForARouteTilesLength() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.ROUTE, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        wire(page, 3, 1, 7);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(key(5, 1), true);
        session.rebuild();

        GraphReducer.ReducedEdge arriving = null;

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            if (edge.getEnd().equals(key(5, 1)) && edge.getStart().equals(key(1, 1))) arriving = edge;
        }

        assertNotNull(arriving, "precondition: no edge runs from 1,1 over the switch and the route tile to 5,1");

        boolean overTheRouteTile = false;

        for (GraphReducer.TileStep step : arriving.getPath()) if (key(4, 1).equals(step.getTile())) overTheRouteTile = true;

        assertTrue(overTheRouteTile, "precondition: the edge does not run over the route tile - " + arriving.getPath());

        assertFalse(session.getReducer().unmeasuredAfterTheLastSwitch(arriving).contains(key(4, 1)),
            "the route tile is named as unmeasured track after the switch, so the notice asks for its length");
    }

    /**
     * 1,1 sensor - 2,1 - ROUTE TILE 3,1 - 4,1 - 5,1 sensor, with 5,1 a station.
     */
    private void openARouteTileInARun() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.ROUTE, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(key(5, 1), true);
        session.rebuild();
    }

    /**
     * A route tile's length is folded into the track beside it when the setup is opened, keeping the total (Adam,
     * 2026-09-23: *"Fold them, they were likely auto set during the mass assignment run."*).
     *
     * Five route tiles on his railway held a length of 1 from before OB-273, and nothing reads a route tile's length
     * now - so the piece they sat in measured one unit less than he had given it.  Written to the file and read back,
     * because the fold is what opening a setup does.
     *
     * MUTATION: dropping the fold from `open` leaves the route tile's length where it was.
     */
    @Test
    public void testARouteTilesLengthIsFoldedIntoTheTrackBesideIt() throws IOException
    {
        openARouteTileInARun();

        session.getStore().setTileLength(key(2, 1), 2);
        session.getStore().setTileLength(key(3, 1), 1);
        session.getStore().setTileLength(key(4, 1), 3);

        session.save();

        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.ROUTE, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));

        assertEquals(session.getStore().getTileLength(key(3, 1)), 0,
            "the route tile at 3,1 still holds a length after the setup was opened - Adam: \"Fold them\"");

        int beside = session.getStore().getTileLength(key(2, 1)) + session.getStore().getTileLength(key(4, 1));

        assertEquals(beside, 6, "the track either side of the route tile measures " + beside + " rather than the 2 + 1 +"
            + " 3 the piece measured - the route tile's unit was dropped rather than folded");

        assertEquals(session.getFoldedRouteTiles().keySet(), java.util.Collections.singleton(key(3, 1)),
            "the fold does not say which route tile it moved");
    }

    /**
     * The fold never puts a length on a sensor square (AUT-C4).
     *
     * A sensor square's length is the last place of every rail arriving at it and the first thing a train standing there
     * spends (OB-278), so a unit folded onto it lengthens every OTHER approach to that sensor as well - a train arriving
     * from the far side is then judged a unit longer than the track, and its tail claimed a unit short.  The fold took
     * "plain track" to mean anything that is not a switch, and a sensor is not a switch.  Here the route tile has a
     * sensor on the side asked first and a straight on the other, and the straight takes the unit.
     *
     * MUTATION: let the fold take a sensor square again and this fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testTheFoldNeverPutsALengthOnASensor() throws IOException
    {
        // 1,1 sensor - 2,1 straight - 3,1 ROUTE - 4,1 sensor - 5,1 straight - 6,1 sensor.
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.ROUTE, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 6, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Fold");

        session.getStore().setTileLength(key(3, 1), 1);
        session.getStore().setTileLength(key(4, 1), 2);

        session.save();

        session.open(Arrays.asList(page));

        assertEquals(session.getStore().getTileLength(key(4, 1)), 2, "the route tile's unit was folded onto the sensor at"
            + " 4,1 - which lengthens every other approach to that sensor as well");

        assertEquals(session.getStore().getTileLength(key(2, 1)), 1, "the route tile's unit did not go to the straight at"
            + " 2,1, the plain track beside it");
    }

    /**
     * Opening Adam's own railway folds his five route tiles, and no other square (Adam, 2026-09-23: *"Fold them"*).
     *
     * On the frozen snapshot of his measured layout.  The five held a length of 1 each; nothing else on it is a route
     * tile with a length, so the list is exact.
     *
     * @throws Exception from the sandbox
     */
    @Test
    public void testHisFiveRouteTilesAreFoldedWhenHisRailwayIsOpened() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            AutonomySession his = new AutonomySession(sandbox.getFolder());

            his.open(support.LayoutSandbox.wiredPages(model));

            Set<TileKey> expected = new java.util.LinkedHashSet<>(Arrays.asList(
                new TileKey("2 - Bottom", 19, 3), new TileKey("2 - Bottom", 4, 11), new TileKey("1 - Main", 6, 7),
                new TileKey("1 - Main", 16, 12), new TileKey("1 - Main", 15, 13)));

            assertEquals(new java.util.LinkedHashSet<>(his.getFoldedRouteTiles().keySet()), expected,
                "opening his railway folded " + his.getFoldedRouteTiles() + " rather than his five route tiles");

            for (TileKey routeTile : expected)
            {
                assertEquals(his.getStore().getTileLength(routeTile), 0, routeTile + " still holds a length");
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A route tile at 2,2 with a sensor on each side - two roads through it, north-south and east-west.
     */
    private void openARouteTileCrossroads() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 6, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 2, 1, 1, 0, 21, 21, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 1, 2, 0, 0, 22, 22, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.ROUTE, 2, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 2, 0, 0, 23, 23, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 3, 1, 0, 24, 24, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(key(2, 1), true);
        session.setStation(key(2, 3), true);
        session.setStation(key(1, 2), true);
        session.setStation(key(3, 2), true);
        session.rebuild();
    }

    // ------------------------------------------------------------------------------ FR-094: the train-length walk

    /**
     * A train-length door with no window behind it: a map of lengths, and a record of what was written.
     *
     * The walk's two halves live on the main window - the run list and `applyTrainLength` - and these claims are about
     * the WALK: what it asks, what it refuses and what it writes.  The door the window really supplies is pinned
     * separately, by `testTheWalkAsksTheRefusalsListAndWritesThroughTheOneDoor`.
     */
    private static final class Lengths implements AutonomyEditorPanel.TrainLengthDoor
    {
        final java.util.Map<String, Integer> length = new java.util.TreeMap<>();
        final java.util.List<String> written = new java.util.ArrayList<>();

        Lengths(Object... pairs)
        {
            for (int i = 0; i < pairs.length; i += 2) length.put((String) pairs[i], (Integer) pairs[i + 1]);
        }

        @Override
        public java.util.List<String> trainsWithoutALength()
        {
            java.util.List<String> out = new java.util.ArrayList<>();

            for (java.util.Map.Entry<String, Integer> e : length.entrySet())
            {
                if (e.getValue() == null || e.getValue() <= 0) out.add(e.getKey());
            }

            return out;
        }

        @Override
        public void applyTrainLength(String train, int units)
        {
            length.put(train, units);
            written.add(train + "=" + units);
        }

        /** Every train and its length - what the walk goes through when none is missing (MT-533). */
        public java.util.Map<String, Integer> trainLengths()
        {
            return new java.util.TreeMap<>(length);
        }
    }

    private static void invokeTheTrainWalk(final AutonomyEditorPanel panel) throws Exception
    {
        final java.lang.reflect.Method walk = AutonomyEditorPanel.class.getDeclaredMethod("massAssignTrainLengths");
        walk.setAccessible(true);

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try { walk.invoke(panel); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private static void type(final javax.swing.JDialog prompt, final String text) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JTextField field = findField(prompt.getContentPane());

            assertNotNull(field, "the prompt has no number box");

            field.setText(text);
        });
    }

    /**
     * The walk asks about each train with no length in turn, writes what is typed, and leaves a skipped one alone.
     *
     * Adam, 2026-09-23: *"add a bulk tool to the autonomy editor to set missing train lengths, similar to how the station
     * lengths are set."*  So it is the station walk's shape - the same prompt, titled with the walk's own name, OK and
     * Skip - over the trains the door says have no length, and a train that already has one is never asked about.
     * OK rather than Enter, for the reason `testTheMaximumWalkWritesWhatIsTypedAndMovesOn` gives.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheTrainWalkWritesWhatIsTypedAndSkipLeavesATrainAlone() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the walk's prompt needs a display");

        openBerthBehindASwitch(key(5, 1));

        Lengths lengths = new Lengths("Alpha", 0, "Bravo", 0, "Charlie", 5);

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        panel.setTrainLengthDoorForTest(lengths);

        invokeTheTrainWalk(panel);

        javax.swing.JDialog first = awaitPrompt(null, TRAINS);

        assertTrue(promptText(first).contains("Alpha"), "the first prompt does not name the first train: " + promptText(first));

        type(first, "3");
        answer(first, org.traincontrol.util.I18n.t("ui.ok"));

        javax.swing.JDialog second = awaitPrompt(first, TRAINS);

        assertTrue(promptText(second).contains("Bravo"), "the second prompt does not name the second train");

        answer(second, org.traincontrol.util.I18n.t("autosetup.ui.btnSkipOne"));

        awaitNoPrompt(TRAINS);

        // AND THE WALK FINISHED.  The last prompt closing is not the walk ending: the answer is written on the event
        // thread after the prompt's modal loop returns, so the test thread can see the prompt gone before the write.
        // An empty task queued behind the walk runs only once the walk has returned.
        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertEquals(lengths.written, Arrays.asList("Alpha=3"),
            "the walk wrote something other than the one length typed - a skip must write nothing, and a train that"
            + " already has a length must not be asked about");
    }

    /**
     * A length of 0, or one past the locomotive menu's own maximum, is refused with a sentence and asked again.
     *
     * 0 is what a train with no length already holds, so writing it would answer the question with the state it was
     * asked about; and the locomotive menu offers 0 to `TrainControlUI.ROUTE_TRAIN_LENGTH_MAX`, so a longer train here
     * would be a number that dropdown cannot show.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testATrainLengthOfZeroOrPastTheMaximumIsRefusedAndAskedAgain() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the walk's prompt needs a display");

        openBerthBehindASwitch(key(5, 1));

        Lengths lengths = new Lengths("Alpha", 0);

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        panel.setTrainLengthDoorForTest(lengths);

        invokeTheTrainWalk(panel);

        javax.swing.JDialog prompt = awaitPrompt(null, TRAINS);

        for (String refused : new String[] { "0", String.valueOf(org.traincontrol.gui.TrainControlUI.ROUTE_TRAIN_LENGTH_MAX + 1) })
        {
            type(prompt, refused);
            answer(prompt, org.traincontrol.util.I18n.t("ui.ok"));

            dismissTheRefusal(TRAINS);

            assertTrue(lengths.written.isEmpty(), "a train length of " + refused + " was written: " + lengths.written);

            prompt = awaitPrompt(prompt, TRAINS);
        }

        type(prompt, "4");
        answer(prompt, org.traincontrol.util.I18n.t("ui.ok"));

        awaitNoPrompt(TRAINS);

        // AND THE WALK FINISHED.  The last prompt closing is not the walk ending: the answer is written on the event
        // thread after the prompt's modal loop returns, so the test thread can see the prompt gone before the write.
        // An empty task queued behind the walk runs only once the walk has returned.
        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertEquals(lengths.written, Arrays.asList("Alpha=4"), "the length typed after the refusals was not written once");
    }

    /** The rule the walk's refusal asks, at its edges. */
    @Test
    public void testATrainLengthIsOneToTheLocomotiveMenusMaximum()
    {
        int max = org.traincontrol.gui.TrainControlUI.ROUTE_TRAIN_LENGTH_MAX;

        assertFalse(AutonomyEditorPanel.acceptsATrainLength(0), "0 is the length a train without one already has");
        assertTrue(AutonomyEditorPanel.acceptsATrainLength(1));
        assertTrue(AutonomyEditorPanel.acceptsATrainLength(max));
        assertFalse(AutonomyEditorPanel.acceptsATrainLength(max + 1), "the locomotive menu cannot show " + (max + 1));
    }

    /**
     * The Bulk Tools item is never greyed, and says in its label how many trains are missing a length (MT-533).
     *
     * Adam, 2026-09-24: *"This option should never be greyed out completely (show the number of missing trains in
     * parens)."*  It greyed when every train had a length, which is also why MT-533 could not be run: nothing short of a
     * train with no length could open the walk.
     *
     * MUTATION: grey it again with none missing, or drop the count from its label, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheTrainWalkItemCountsAndIsNeverGreyed() throws Exception
    {
        openBerthBehindASwitch(key(5, 1));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        panel.setTrainLengthDoorForTest(new Lengths("Alpha", 0, "Bravo", 0, "Charlie", 5));

        javax.swing.JMenuItem item = trainWalkItem(panel);

        assertNotNull(item, "the Bulk Tools menu has no Mass Assign Locomotive Train Lengths item");
        assertTrue(item.isEnabled(), "the item is greyed with two trains to ask about");
        assertEquals(item.getToolTipText().replaceAll("<[^>]*>", ""),
            org.traincontrol.util.I18n.f("autosetup.ui.tooltipMassAssignTrainLengths", 2));

        panel.setTrainLengthDoorForTest(new Lengths("Charlie", 5));

        item = trainWalkItem(panel);

        assertNotNull(item, "the Bulk Tools menu has no Mass Assign Locomotive Train Lengths item when every train has a"
            + " length");

        assertTrue(item.isEnabled(), "the item is greyed when every train has a length - Adam, MT-533: \"This option"
            + " should never be greyed out completely\"");

        assertEquals(item.getText(), org.traincontrol.util.I18n.f(TRAINS_COUNTED, 0), "the item does not say how many"
            + " trains are missing a length - Adam, MT-533: \"show the number of missing trains in parens\"");

        panel.setTrainLengthDoorForTest(new Lengths("Alpha", 0, "Bravo", 0, "Charlie", 5));

        assertEquals(trainWalkItem(panel).getText(), org.traincontrol.util.I18n.f(TRAINS_COUNTED, 2),
            "the item's count is not the number of trains missing a length");
    }

    /**
     * With every train measured, the walk goes through all of them, each with the length it has, and Skip keeps it
     * (MT-533).
     *
     * What an item that is never greyed does when nothing is missing: the lengths there are, to look at and to change.
     *
     * MUTATION: say every train has a length and walk nothing again, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testWithEveryTrainMeasuredTheWalkShowsEachAndSkipKeepsIt() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the walk's prompt needs a display");

        openBerthBehindASwitch(key(5, 1));

        Lengths lengths = new Lengths("Charlie", 5, "Delta", 2);

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        panel.setTrainLengthDoorForTest(lengths);

        invokeTheTrainWalk(panel);

        javax.swing.JDialog first = awaitPrompt(null, TRAINS);

        assertTrue(promptText(first).contains("Charlie") && promptText(first).contains("5"), "the first prompt does not"
            + " name Charlie and the length it has: " + promptText(first));

        answer(first, org.traincontrol.util.I18n.t("autosetup.ui.btnSkipOne"));

        javax.swing.JDialog second = awaitPrompt(first, TRAINS);

        assertTrue(promptText(second).contains("Delta"), "the second prompt does not name Delta");

        type(second, "4");
        answer(second, org.traincontrol.util.I18n.t("ui.ok"));

        awaitNoPrompt(TRAINS);

        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertEquals(lengths.written, Arrays.asList("Delta=4"), "the walk wrote something other than the one length"
            + " typed - Skip must keep the length a train has");
    }

    /**
     * In its every-train mode the walk says what is true of the trains it goes through (TDU-C7).
     *
     * MT-533 made the item never greyed, and with no train missing a length it goes through every train with the length
     * it has.  Two sentences written for the other mode came with it: typed 0, it said *"0 means the train has no length,
     * which is what it has now"* of a train its own prompt had just said has one; and with no train placed at all, the
     * item's tooltip said every locomotive has a length while a click said none is placed.
     *
     * MUTATION: answer 0 in that mode with the missing-length sentence, or choose the tooltip on the missing count alone,
     * and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheWalkOfEveryTrainSaysWhatIsTrueOfIt() throws Exception
    {
        openBerthBehindASwitch(key(5, 1));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        // NO TRAIN AT ALL: the tooltip says so, as the click does.
        panel.setTrainLengthDoorForTest(new Lengths());

        javax.swing.JMenuItem item = trainWalkItem(panel);

        assertNotNull(item, "the Bulk Tools menu has no train walk with no trains placed");

        assertEquals(item.getToolTipText().replaceAll("<[^>]*>", ""),
            org.traincontrol.util.I18n.t("autosetup.ui.infoNoTrainToMeasure"), "with no train placed, the train walk's"
            + " tooltip does not say so - it says every locomotive has a length, and a click says none is placed");

        if (java.awt.GraphicsEnvironment.isHeadless()) return;

        // EVERY TRAIN MEASURED: 0 typed for one that has a length.
        panel.setTrainLengthDoorForTest(new Lengths("Charlie", 5));

        invokeTheTrainWalk(panel);

        javax.swing.JDialog first = awaitPrompt(null, TRAINS);

        type(first, "0");
        answer(first, org.traincontrol.util.I18n.t("ui.ok"));

        String refused = refusalText(TRAINS);

        assertFalse(refused.contains(org.traincontrol.util.I18n.f("autosetup.ui.errorTrainLengthOutOfRange",
            org.traincontrol.gui.TrainControlUI.ROUTE_TRAIN_LENGTH_MAX).replaceAll("<[^>]*>", "").substring(60)),
            "0 typed for Charlie, whose prompt says it is 5 long, was answered with the sentence for a train with no"
            + " length - \"which is what it has now\": " + refused);

        javax.swing.JDialog again = awaitPrompt(null, TRAINS);

        answer(again, org.traincontrol.util.I18n.t("ui.cancel"));

        awaitNoPrompt(TRAINS);
    }

    /** The text of the refusal the walk shows over its own prompt, which is then closed the way its OK button does. */
    private static String refusalText(String walkTitleKey) throws Exception
    {
        String walkTitle = org.traincontrol.util.I18n.t(walkTitleKey);
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                final javax.swing.JDialog dialog = (javax.swing.JDialog) window;

                if (walkTitle.equals(dialog.getTitle())) continue;

                final JOptionPane[] pane = new JOptionPane[1];

                javax.swing.SwingUtilities.invokeAndWait(() -> pane[0] = findPane(dialog.getContentPane()));

                if (pane[0] == null) continue;

                String text = promptText(dialog);

                javax.swing.SwingUtilities.invokeAndWait(() -> pane[0].setValue(JOptionPane.OK_OPTION));

                return text;
            }

            Thread.sleep(50);
        }

        fail("the walk did not refuse the length with a message");

        return null;
    }

    /**
     * Train lengths and the stations' maximum train lengths are named apart (MT-533).
     *
     * Adam, 2026-09-24: *"better disambiguate labels for 'train lengths' from 'max train lengths', since the latter deals
     * with stations."*  Read from the English bundle, which is the one the sentence was about.
     *
     * @throws Exception reading the bundle
     */
    @Test
    public void testTrainLengthsAndStationMaximaAreNamedApart() throws Exception
    {
        java.util.Properties english = new java.util.Properties();

        try (java.io.InputStream in = new java.io.FileInputStream("src/org/traincontrol/resources/messages.properties"))
        {
            english.load(in);
        }

        assertTrue(String.valueOf(english.getProperty(TRAINS_COUNTED)).contains("Locomotive"), "the train walk's label"
            + " does not say it is about locomotives: " + english.getProperty(TRAINS_COUNTED));

        assertTrue(String.valueOf(english.getProperty(MAXIMA)).contains("Station"), "the maximum walk's label does not say"
            + " it is about stations: " + english.getProperty(MAXIMA));

        assertTrue(String.valueOf(english.getProperty("autolayout.ui.menuClearAllMaxTrainLengths")).contains("Station"),
            "Clear All Station Max Train Lengths does not say it is about stations: "
            + english.getProperty("autolayout.ui.menuClearAllMaxTrainLengths"));
    }

    /**
     * One-way run is greyed on a page left out of autonomy, as Mass Assign Lengths is (MT-528, OB-235).
     *
     * Adam, 2026-09-24, on MT-528: *"works, but one-way run isn't."*  Nothing on a page autonomy takes no notice of is
     * built, so a run closed one way there closes nothing.
     *
     * MUTATION: leave One-way run enabled on a page left out, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testOneWayRunIsGreyedOnAPageLeftOut() throws Exception
    {
        openBerthBehindASwitch(key(5, 1));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        assertTrue(bulkItemNamed(panel, org.traincontrol.util.I18n.t("autosetup.ui.toolOneWay")).isEnabled(),
            "precondition: One-way run is greyed on a page autonomy uses");

        session.setPageExcluded("main", true);

        javax.swing.SwingUtilities.invokeAndWait(() -> panel.refresh());

        assertFalse(bulkItemNamed(panel, org.traincontrol.util.I18n.t("autosetup.ui.menuMassAssignLengths")).isEnabled(),
            "precondition: Mass Assign Lengths is not greyed on a page left out (MT-528)");

        assertFalse(bulkItemNamed(panel, org.traincontrol.util.I18n.t("autosetup.ui.toolOneWay")).isEnabled(),
            "One-way run is offered on a page left out of autonomy - Adam, MT-528: \"works, but one-way run isn't\"");
    }

    /**
     * The One-Way Run BUTTON is greyed on a page left out too, and a run armed when the page is left out is put down
     * (OB-235, TDD-C7) - the parts MT-568 checks that the Bulk Tools claim above does not.
     *
     * The TOOL, not only its button (TDD2-C7): un-pressing a toggle fires no action, so the button and the tool it arms
     * are two things, and a tool left armed with its button up waits for the next click once the page is ticked back in.
     * And the prompt it put up for its first square goes with it.
     *
     * MUTATION: leave the button enabled on a page left out, leave it armed, put the button down and leave the tool,
     * or leave the prompt, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheOneWayButtonIsGreyedAndPutDownOnAPageLeftOut() throws Exception
    {
        openBerthBehindASwitch(key(5, 1));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("oneWayButton");

        field.setAccessible(true);

        final javax.swing.AbstractButton oneWay = (javax.swing.AbstractButton) field.get(panel);

        assertTrue(oneWay.isEnabled(), "precondition: the One-Way Run button is greyed on a page autonomy uses");

        // ARMED, as a person arms it, then the page left out.
        javax.swing.SwingUtilities.invokeAndWait(() -> oneWay.doClick());

        assertTrue(oneWay.isSelected(), "precondition: One-Way Run did not arm");

        session.setPageExcluded("main", true);

        javax.swing.SwingUtilities.invokeAndWait(() -> panel.refresh());

        assertFalse(oneWay.isEnabled(), "the One-Way Run button is offered on a page left out of autonomy (OB-235)");

        assertFalse(oneWay.isSelected(), "One-Way Run was left armed on a page left out of autonomy - a click there waits"
            + " for its second square (OB-235)");

        java.lang.reflect.Field toolField = AutonomyEditorPanel.class.getDeclaredField("tool");

        toolField.setAccessible(true);

        assertEquals(String.valueOf(toolField.get(panel)), "NONE", "the One-Way Run button was put down on a page left"
            + " out, and the tool it arms was not - tick the page back in and the next click starts a one-way run with"
            + " nothing pressed (TDD2-C7)");

        java.lang.reflect.Field hintField = AutonomyEditorPanel.class.getDeclaredField("hint");

        hintField.setAccessible(true);

        String hint = ((javax.swing.JLabel) hintField.get(panel)).getText();

        assertFalse(hint != null && hint.contains(org.traincontrol.util.I18n.t("autosetup.ui.promptOneWayFrom")),
            "One-Way Run was put down on a page left out and its prompt still asks for the first square (TDD2-C7): "
            + hint);
    }

    /** The Bulk Tools item with this text. */
    private static javax.swing.JMenuItem bulkItemNamed(AutonomyEditorPanel panel, String text) throws Exception
    {
        final javax.swing.JMenuItem[] found = new javax.swing.JMenuItem[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu bulk = panel.buildBulkMenuForTest();

            for (int i = 0; i < bulk.getItemCount(); i++)
            {
                javax.swing.JMenuItem item = bulk.getItem(i);

                if (item != null && text.equals(item.getText())) found[0] = item;
            }
        });

        assertNotNull(found[0], "precondition: Bulk Tools has no item " + text);

        return found[0];
    }

    /**
     * The door the main window supplies asks the list the Atomic Routes refusal names, and writes through the one
     * train-length door.
     *
     * Read from the source because the window is the half these claims replace with a stand-in.  Two halves, each the
     * reason FR-094 was filed: the refusal on MT-470 named trains and there was nowhere to go - so the walk must ask the
     * SAME list - and `applyTrainLength` is where the findings and the grey are told, so a walk that set the field
     * itself would leave both describing the length before last.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheWalkAsksTheRefusalsListAndWritesThroughTheOneDoor() throws Exception
    {
        String panel = new String(Files.readAllBytes(new File("src/org/traincontrol/gui/AutonomyEditorPanel.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8).replace("\r", "");

        int at = panel.indexOf("private TrainLengthDoor trainLengthDoor()");

        assertTrue(at > 0, "the default door is not declared that way any more, so this checked nothing");

        String door = panel.substring(at, panel.indexOf("\n    }\n", at));

        assertTrue(door.contains("window.trainsWithoutALength()"),
            "the walk no longer asks the list the Atomic Routes refusal names");
        assertTrue(door.contains("window.applyTrainLength("),
            "the walk no longer writes through applyTrainLength, so the findings and the grey are not told");

        String ui = new String(Files.readAllBytes(new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8).replace("\r", "");

        int list = ui.indexOf("public java.util.List<String> trainsWithoutALength()");

        assertTrue(list > 0, "TrainControlUI.trainsWithoutALength is not declared that way any more");

        assertTrue(ui.substring(list, ui.indexOf("\n    }\n", list)).contains("trainsWithNoLength()"),
            "TrainControlUI.trainsWithoutALength no longer asks Layout.trainsWithNoLength, the refusal's own list");

        int walk = panel.indexOf("private void massAssignTrainLengths()");

        assertTrue(walk > 0, "the walk is not declared that way any more");

        assertFalse(panel.substring(walk, panel.indexOf("\n    }\n", walk)).contains("setTrainLength("),
            "the walk sets a length itself, past applyTrainLength");
    }

    /** The Mass Assign Locomotive Train Lengths item, off the Bulk Tools menu as the right-click menu builds it. */
    private static javax.swing.JMenuItem trainWalkItem(AutonomyEditorPanel panel) throws Exception
    {
        final javax.swing.JMenuItem[] found = new javax.swing.JMenuItem[1];

        final String text = org.traincontrol.util.I18n.t(TRAINS);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu bulk = panel.buildBulkMenuForTest();

            for (int i = 0; i < bulk.getItemCount(); i++)
            {
                javax.swing.JMenuItem item = bulk.getItem(i);

                // BY ITS NAME, since its text carries the count (MT-533) - or by the text it had before.
                if (item != null && (TRAIN_WALK_ITEM.equals(item.getName()) || text.equals(item.getText())))
                {
                    found[0] = item;
                }
            }
        });

        return found[0];
    }

    /** What the prompt says above its number box, with the markup taken out. */
    private static String promptText(final javax.swing.JDialog prompt) throws Exception
    {
        final StringBuilder out = new StringBuilder();

        javax.swing.SwingUtilities.invokeAndWait(() -> collectLabels(prompt.getContentPane(), out));

        return out.toString().replaceAll("<[^>]*>", "");
    }

    private static void collectLabels(java.awt.Container container, StringBuilder out)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JLabel) out.append(((javax.swing.JLabel) child).getText()).append(' ');

            if (child instanceof java.awt.Container) collectLabels((java.awt.Container) child, out);
        }
    }

    /** Waits for the refusal the walk shows over its own prompt, and closes it the way its OK button does. */
    private static void dismissTheRefusal(String walkTitleKey) throws Exception
    {
        String walkTitle = org.traincontrol.util.I18n.t(walkTitleKey);
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                final javax.swing.JDialog dialog = (javax.swing.JDialog) window;

                if (walkTitle.equals(dialog.getTitle())) continue;

                final JOptionPane[] pane = new JOptionPane[1];

                javax.swing.SwingUtilities.invokeAndWait(() -> pane[0] = findPane(dialog.getContentPane()));

                if (pane[0] == null) continue;

                javax.swing.SwingUtilities.invokeAndWait(() -> pane[0].setValue(JOptionPane.OK_OPTION));

                return;
            }

            Thread.sleep(50);
        }

        fail("the walk did not refuse the length with a message");
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
     * number typed here took the railway out of autonomy with only a log line - and Clear All Station Max Train Lengths, which
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
            "Clear All Station Max Train Lengths cannot see a negative maximum, so it greys itself on the setting that is"
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
     * Segment Length's 0 is a deliberate 0, and Clear takes the length away (Adam, 2026-09-23).
     *
     * Asked whether a 0 typed into Segment Length should go on clearing the length, now that a 0 answered in Mass
     * Assign Lengths is kept as an answer (OB-274): *"no, add a clear button."*  So 0 records every square of the run
     * as answered - Mass Assign Lengths and Unmeasured Track leave them alone, and every length rule still reads them
     * as unmeasured - and clearing is a button of its own, which also answers an empty field.
     *
     * MUTATION: `applyLengthAnswer` writing 0 as a plain length again leaves the run unanswered; Clear answering 0
     * leaves it answered.
     */
    @Test
    public void testSegmentLengthZeroIsAnAnswerAndClearTakesItAway() throws IOException
    {
        openARunOfTwoPlainSquares();

        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        TileKey[] run = { key(2, 1), key(3, 1) };

        panel.applyLengthAnswer(key(3, 1), 0);

        for (TileKey square : run)
        {
            assertTrue(session.getStore().isTileLengthAnswered(square),
                "Segment Length was given 0 on the run and " + square + " is not recorded as answered - so Mass Assign"
                + " Lengths offers it again, and the 0 typed on purpose reads as a length never given");

            assertEquals(session.getStore().getTileLength(square), 0,
                "an answered 0 is still a 0: " + square + " measures " + session.getStore().getTileLength(square));
        }

        panel.applyLengthAnswer(key(3, 1), null);

        for (TileKey square : run)
        {
            assertFalse(session.getStore().isTileLengthAnswered(square),
                "Clear left " + square + " answered, so there is no way back from a deliberate 0 to \"not given\"");
        }
    }

    /**
     * An answered 0 is not reported as a berth's missing length (Adam, 2026-09-23: *"stop listing answered zeros as
     * missing"*).
     *
     * The half-measured notice counts the squares on a berth's approach that have no length; a square answered 0 has
     * been given one on purpose, and the rules still read it as 0.  CONTROL first: unanswered, it is reported.
     */
    @Test
    public void testAnAnsweredZeroDoesNotMakeABerthHalfMeasured() throws IOException
    {
        openARouteTileInARun();

        session.setAutoDestination(key(5, 1), false);

        // The berth measured too: its own square counts, as the berth rule counts it (TDY-B2).
        session.getStore().setTileLength(key(5, 1), 2);

        session.getStore().setTileLength(key(2, 1), 2);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "CONTROL: with the straight at 4,1 unmeasured the berth is not reported half measured, so the check below"
            + " could pass by never firing at all");

        session.getStore().answerTileLengthZero(key(4, 1));

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "the straight at 4,1 was answered 0 on purpose and the berth is still reported half measured - an answered"
            + " zero listed as missing");
    }

    /**
     * A berth whose own measured squares hold its longest train is not warned about, whatever lies unmeasured behind
     * them (OB-288).
     *
     * Adam, 2026-09-24: *"&lt;station&gt; can refuse trains that would otherwise fit shows up on all berths, even though
     * we have measured the s88 tile to match the berth max train size.  likely an artifact from before we included the
     * station length in the measurement."*  Since OB-278 the berth walk spends the berth's own square first, so a train
     * the berth takes is spent there before it reaches anything unmeasured, and no refusal can come of the hole behind.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testABerthThatHoldsItsLongestTrainIsNotWarnedAbout() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        // WHAT ADAM HAS: the berth's own square measured, the approach behind it not.
        session.setTileLength(key(5, 1), 3);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "CONTROL: a berth measured on its own square with nothing behind, and no longest train, is not reported - so"
            + " the claim below could pass by never firing at all");

        assertTrue(session.assignMaxTrainLength(key(5, 1), 3), "precondition: the berth was not given a longest train");

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "a berth whose own square measures 3, taking trains of 3 at most, is warned that it can refuse trains that"
            + " would otherwise fit - no train it takes ever reaches an unmeasured square (OB-288)");

        // AND ONE IT TAKES THAT IS LONGER THAN WHAT IS MEASURED IS STILL WARNED ABOUT: its fourth unit reaches the hole.
        session.setPointProperty(key(5, 1), "maxTrainLength", 4);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "a berth taking trains of 4, with 3 measured before an unmeasured square, is no longer warned about - and a"
            + " four-unit train is refused there for the hole in the measurements");
    }

    /**
     * A square with no length between a berth and its switch does not end the count - the berth walk passes it for
     * nothing (Adam, 2026-09-24, on MT-552).
     *
     * *"BottomMainPost (rightmost station) is measured on both sides ... trains of length 3 can hold there"* - *"look at
     * 22,8 and 22,9"*.  Its own square 1, 22,7 with no length, 22,8 and 22,9 1 each, then the switch at 22,10.
     * `Layout.whyABerthCannotHoldIt` spends a train back from the berth and passes a square with no length for nothing,
     * so a three-unit train is spent at 22,9, short of the switch, and nothing refuses it.  OB-288's count stopped at the
     * first square with no length and warned about a refusal the walk never makes.  What it does stop at is the switch:
     * the walk refuses a train whose spending reaches track another road runs over.
     *
     * MUTATION: stop the count at a square with no length again, or let it run on past the switch, and this fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testASquareWithNoLengthBeforeTheSwitchDoesNotEndTheCount() throws IOException
    {
        openBerthBehindALongerRun();

        TileKey berth = key(7, 1);

        // BOTTOMMAINPOST'S SHAPE: the berth 1, the square behind it nothing, the two after it 1 each, then the switch.
        session.setTileLength(berth, 1);
        session.setTileLength(key(5, 1), 1);
        session.setTileLength(key(4, 1), 1);
        session.setPointProperty(berth, "maxTrainLength", 3);

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "a berth taking trains of 3, with 1"
            + " on its own square, nothing on the next and 1 on each of the two before the switch, is warned that it can"
            + " refuse trains that would otherwise fit - the walk passes the square with no length and a three-unit train"
            + " is spent before the switch.  Adam, MT-552: \"trains of length 3 can hold there\"");

        // CONTROL: a train of 4 is not spent before the switch, and is warned about.
        session.setPointProperty(berth, "maxTrainLength", 4);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "a berth taking trains of 4, with 3"
            + " measured before the switch, is not warned about - so the claim above could pass by never firing");

        // AND THE SWITCH ENDS THE COUNT: measured track beyond it is another road's, and reaching it is the refusal.
        session.setTileLength(key(2, 1), 5);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "measured track beyond the switch"
            + " was counted as room for the berth's longest train - a train spent there lies across the points");
    }

    /**
     * The count stops where the berth rule does: at a permanent turnout, and at a crossing, as well as at a switch (TDA-C7).
     *
     * The rules the count stands for stop earlier than a switch.  The room behind a platform ends at `boundsTheRoom` -
     * a switch or a permanent turnout (OB-233, *"a permanent turnout is still the last switch"*) - and the berth rule
     * refuses as soon as a place it claims is on another road, which a crossing's square is.  Run on past either, the
     * count added track beyond them and left quiet a berth whose longest train the rule refuses.
     *
     * MUTATION: stop the count at a switch only, and this fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testTheCountStopsAtATurnoutOrACrossingAsTheRuleDoes() throws IOException
    {
        // A PERMANENT TURNOUT between a berth and measured track.  The turnout lets trains run from 7,1 to 1,1 only, so the
        // berth is 1,1: its own square 1, 2,1 with no length, the turnout, and 5 on 4,1 beyond it.
        TileKey atTheTurnout = key(1, 1);

        openBerthBehindALongerRun(componentType.CUSTOM_PERM_LEFT, atTheTurnout);

        session.setTileLength(atTheTurnout, 1);
        session.setTileLength(key(4, 1), 5);
        session.setPointProperty(atTheTurnout, "maxTrainLength", 2);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(atTheTurnout), "a berth taking trains of 2,"
            + " with 1 measured before a permanent turnout, is not warned about - the count ran on past the turnout and"
            + " counted the 5 beyond it, where the room walk stops (OB-233)");

        // A CROSSING between the berth and its measured track: another road's square, which the berth rule refuses on.
        openBerthBehindACrossing();

        TileKey berth = key(7, 1);

        session.setTileLength(berth, 1);
        session.setTileLength(key(4, 1), 3);
        session.setPointProperty(berth, "maxTrainLength", 3);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "a berth taking trains of 3, with 1"
            + " measured before a crossing, is not warned about - the count ran on past the crossing and counted the 3"
            + " beyond it, and the berth rule refuses a three-unit train at the crossing");

        // AND ONLY THE SQUARES BEFORE IT ARE COUNTED (TDA2-C6): the crossing, the switch and 2,1 have no length either,
        // and measuring them changes nothing - the rule has refused by then.
        assertEquals(session.stationsWithAHalfMeasuredApproach().get(berth), Integer.valueOf(1), "the half-measured"
            + " notice counts squares beyond the crossing, which the operator would measure for nothing - only 6,1 lies"
            + " between the berth and it");
    }

    /**
     * With nothing spent before the crossing, a parking berth refuses every train there, and its run-in notice says 0 or
     * nothing - never the room to the switch (TDA3-C1).
     *
     * The berth rule, once anything on the approach is measured, claims the crossing's square before it spends anything
     * on it, so with the two squares between the berth and the crossing at no length every train is refused.  The notice
     * quoted the room walk's 4, back to the switch, and said a train of up to 4 fits.  Answered 0 on purpose, those
     * squares are nothing the half-measured notice names, so the run-in notice says 0; left unanswered, the half-measured
     * notice names them, and the run-in notice says nothing.
     *
     * MUTATION: take the berth rule's figure only where it spent something, and this fails.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testNothingSpentBeforeTheCrossingIsSaidAsNothing() throws Exception
    {
        openBerthBehindACrossing();

        TileKey berth = key(7, 1);

        session.setTileLength(key(5, 1), 1);
        session.setTileLength(key(4, 1), 3);
        session.setTileLength(key(3, 1), 1);
        session.setTileLength(key(2, 1), 1);
        session.answerTileLengthsZero(Arrays.asList(berth, key(6, 1)));
        session.setPointProperty(berth, "maxTrainLength", 5);
        session.rebuild();

        java.lang.reflect.Method runIns = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "runInsShorterThanTheBerth");

        runIns.setAccessible(true);

        java.util.Map<TileKey, int[]> said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertTrue(said.containsKey(berth) && said.get(berth)[1] == 0, "a parking berth with nothing but answered zeros"
            + " before a crossing refuses every train there, and its run-in notice does not say 0: "
            + (said.containsKey(berth) ? Arrays.toString(said.get(berth)) : "no entry"));

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "precondition: answered zeros are"
            + " named by the half-measured notice");

        // UNANSWERED: the half-measured notice names the two squares, and the run-in notice says nothing.
        session.setTileLength(berth, 0);
        session.setTileLength(key(6, 1), 0);
        session.rebuild();

        said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "precondition: two unanswered squares"
            + " before the crossing are not named by the half-measured notice");

        assertFalse(said.containsKey(berth), "with nothing measured before the crossing the run-in notice quotes the room"
            + " to the switch, a figure the berth rule never gives - the half-measured notice beside it names the squares"
            + " (TDA3-C1): " + (said.containsKey(berth) ? Arrays.toString(said.get(berth)) : ""));
    }

    /**
     * The berth rule itself refuses at the crossing, which the crossing claims above take as given (TDA3-C2): asked of the
     * railway built from this page, every square measured, a two-unit train is held before the crossing and a three-unit
     * one is refused.  The notices model the rule with `endsTheBerthsRoom`; this asks the rule.
     *
     * @throws Exception from the build
     */
    @Test
    public void testTheBerthRuleRefusesAtTheCrossing() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            try
            {
                openBerthBehindACrossing();

                TileKey berth = key(7, 1);

                for (int x = 2; x <= 7; x++) session.setTileLength(key(x, 1), x == 4 ? 3 : 1);

                session.rebuild();

                model.parseAuto(session.buildConfiguration());

                org.traincontrol.automation.Layout layout = model.getAutoLayout();

                assertNotNull(layout, "precondition: the page did not build a railway");

                String name = session.getStationIndex().nameOf(berth);

                org.traincontrol.automation.Edge in = null;

                for (org.traincontrol.automation.Edge edge : layout.getEdges())
                {
                    if (edge.getEnd() != null && edge.getEnd().getName().startsWith(name)
                        && edge.getPlaceIds().size() > 3) in = edge;
                }

                assertNotNull(in, "precondition: no rail into the berth over the crossing on the built railway");

                org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDA3-C2 train", 2310);

                try
                {
                    train.setTrainLength(2);

                    assertNull(org.traincontrol.automation.Layout.whyABerthCannotHoldIt(java.util.Arrays.asList(in), train),
                        "the berth rule refused a two-unit train, which is spent on the berth's own square and the one"
                        + " before the crossing");

                    train.setTrainLength(3);

                    assertNotNull(org.traincontrol.automation.Layout.whyABerthCannotHoldIt(java.util.Arrays.asList(in),
                        train), "the berth rule held a three-unit train whose spending reaches the crossing - the two"
                        + " notices say it refuses there, and it does not (TDA3-C2)");
                }
                finally
                {
                    model.deleteLoc("TDA3-C2 train");
                }
            }
            finally
            {
                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The same 0 behind a SWITCH (TDA4-C1): every square between the berth and its switch answered 0, and track beyond
     * the switch measured - the berth rule judges the approach, claims the switch for nothing, and refuses every train.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testNothingSpentBeforeTheSwitchIsSaidAsNothing() throws Exception
    {
        openBerthBehindALongerRun(componentType.SWITCH_LEFT, key(7, 1));

        TileKey berth = key(7, 1);

        session.answerTileLengthsZero(Arrays.asList(key(4, 1), key(5, 1), key(6, 1), berth));
        session.setTileLength(key(2, 1), 1);
        session.setPointProperty(berth, "maxTrainLength", 3);
        session.rebuild();

        java.lang.reflect.Method runIns = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "runInsShorterThanTheBerth");

        runIns.setAccessible(true);

        java.util.Map<TileKey, int[]> said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertTrue(said.containsKey(berth) && said.get(berth)[1] == 0, "a parking berth with nothing but answered zeros"
            + " before its switch refuses every train, and its run-in notice does not say 0: "
            + (said.containsKey(berth) ? Arrays.toString(said.get(berth)) : "no entry"));
    }

    /**
     * No 0 where nothing refuses (TDA5-C1): behind a permanent turnout whose other road ends at the berth, the berth rule
     * passes that road over, and the room walk, with nothing measured after the turnout, does not judge - so a train is
     * admitted, and the run-in notice must not say that everything longer than 0 is refused.
     *
     * MUTATION: take the berth rule's figure at every switch, turnout and crossing, and this fails.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testNoZeroWhereNothingRefuses() throws Exception
    {
        TileKey berth = key(1, 1);

        openBerthBehindALongerRun(componentType.CUSTOM_PERM_LEFT, berth);

        session.answerTileLengthsZero(Arrays.asList(berth, key(2, 1)));
        session.setTileLength(key(4, 1), 5);
        session.setPointProperty(berth, "maxTrainLength", 3);
        session.rebuild();

        java.lang.reflect.Method runIns = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "runInsShorterThanTheBerth");

        runIns.setAccessible(true);

        java.util.Map<TileKey, int[]> said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertFalse(said.containsKey(berth), "behind a permanent turnout whose other road ends at the berth nothing refuses"
            + " a three-unit train, and the run-in notice says everything longer than 0 is (TDA5-C1): "
            + (said.containsKey(berth) ? Arrays.toString(said.get(berth)) : ""));
    }

    /**
     * Nothing measured on the leg at all - answered zeros before the crossing, and nothing beyond it on this leg - is not
     * judged by the berth rule, which admits every train, and the run-in notice says nothing (TDA4-C3).  The railway does
     * measure track elsewhere, on the crossing's other road.
     *
     * MUTATION: take the berth rule's figure without asking whether it judges the leg, and this fails - the notice then
     * says a train longer than 0 is refused at a berth that refuses nothing.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testALegNothingMeasuresIsNotSaidAsNothing() throws Exception
    {
        openBerthBehindACrossing();

        TileKey berth = key(7, 1);

        session.answerTileLengthsZero(Arrays.asList(berth, key(6, 1)));
        session.setTileLength(key(5, 2), 1);
        session.setPointProperty(berth, "maxTrainLength", 5);
        session.rebuild();

        java.lang.reflect.Method runIns = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "runInsShorterThanTheBerth");

        runIns.setAccessible(true);

        java.util.Map<TileKey, int[]> said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertFalse(said.containsKey(berth), "a berth on a leg nothing measures - which the berth rule does not judge - is"
            + " said to refuse every train: " + (said.containsKey(berth) ? Arrays.toString(said.get(berth)) : ""));
    }

    /**
     * A parking berth's crossing on a leg with no switch is said too (TDA3-C2): the room walk finds no switch there and
     * answers nothing, and the berth rule stops at the crossing.
     *
     * MUTATION: take the berth rule's figure only where the room walk found a switch, and this fails.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testACrossingOnALegWithNoSwitchIsSaid() throws Exception
    {
        openBerthBehindACrossing(false);

        TileKey berth = key(7, 1);

        for (int x = 2; x <= 7; x++) session.setTileLength(key(x, 1), x == 4 ? 3 : 1);

        session.setPointProperty(berth, "maxTrainLength", 3);
        session.rebuild();

        java.lang.reflect.Method runIns = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "runInsShorterThanTheBerth");

        runIns.setAccessible(true);

        java.util.Map<TileKey, int[]> said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertTrue(said.containsKey(berth) && said.get(berth)[1] == 2, "a parking berth set to take a train of 3, with 2"
            + " measured before a crossing on a leg with no switch, is not warned: "
            + (said.containsKey(berth) ? Arrays.toString(said.get(berth)) : "no entry"));
    }

    /**
     * A parking berth's run-in notice stops where the berth rule does, at a crossing as well as at a switch (TDA2-C6).
     *
     * `runInsShorterThanTheBerth` quoted the room back to the switch - the room rule's number - and the berth rule refuses
     * earlier, as soon as the train it spends reaches the crossing's square, which is another road's.  With every square
     * measured and 2 of them before the crossing, a berth set to take a train of 3 refuses that train, and neither
     * notice said so: nothing was unmeasured, and 6 of room to the switch is not short of 3.  A platform autonomy may
     * choose is not judged by the berth rule, and keeps the room rule's number.
     *
     * MUTATION: take the room to the switch alone again, and this fails.
     *
     * @throws Exception from the fixture or the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testTheRunInNoticeStopsAtACrossingForABerth() throws Exception
    {
        openBerthBehindACrossing();

        TileKey berth = key(7, 1);

        // EVERY SQUARE MEASURED: 1 and 1 before the crossing, and 1, 3, 1 and 1 from it back to 2,1.
        session.setTileLength(berth, 1);
        session.setTileLength(key(6, 1), 1);
        session.setTileLength(key(5, 1), 1);
        session.setTileLength(key(4, 1), 3);
        session.setTileLength(key(3, 1), 1);
        session.setTileLength(key(2, 1), 1);
        session.setPointProperty(berth, "maxTrainLength", 3);
        session.rebuild();

        java.lang.reflect.Method runIns = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "runInsShorterThanTheBerth");

        runIns.setAccessible(true);

        assertFalse(session.stationsWithAHalfMeasuredApproach().containsKey(berth), "precondition: every square is"
            + " measured, and the berth is warned about as half measured");

        java.util.Map<TileKey, int[]> said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertTrue(said.containsKey(berth) && said.get(berth)[1] == 2, "a berth set to take a train of 3, with 2 measured"
            + " before a crossing, is not warned that a train longer than 2 is refused - the berth rule refuses it at the"
            + " crossing: " + (said.containsKey(berth) ? Arrays.toString(said.get(berth)) : "no entry"));

        // THE CONTROL: a platform autonomy may choose is judged by the room rule alone, back to the switch - 6.
        session.setAutoDestination(berth, true);
        session.rebuild();

        said = (java.util.Map<TileKey, int[]>) runIns.invoke(session);

        assertFalse(said.containsKey(berth), "control: a platform autonomy may choose, with 6 of room to the switch, is"
            + " warned that a train of 3 is refused - the berth rule does not judge it");
    }

    /** The same run with a permanent turnout, or a switch, at 3,1, and the berth where asked. */
    private void openBerthBehindALongerRun(componentType atThree, TileKey berth) throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 11, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(atThree, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        if (atThree == componentType.SWITCH_LEFT) wire(page, 3, 1, 7);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(berth, true);
        session.setAutoDestination(berth, false);
        session.rebuild();
    }

    /**
     * 1,1 sensor - 2,1 - 3,1 switch - 4,1 - 5,1 crossing - 6,1 - 7,1 berth.  The crossing's other road is one a train can
     * drive (TDA3-C2): the switch's branch curves north and east at 3,0 to a sensor at 4,0, and round the curve at 5,0
     * over the crossing to a station at 5,2.  With sensors at 5,0 and 5,2 and nothing leading onto them, the build
     * emitted no rail over the crossing at all, and the berth rule had nothing there to refuse on.
     */
    private void openBerthBehindACrossing() throws IOException
    {
        openBerthBehindACrossing(true);
    }

    /**
     * The same, with a plain straight at 3,1 where the switch was when asked - a leg the room walk finds no switch on
     * (TDA3-C2) - and the crossing's other road fed from a sensor at 1,0 along row 0 instead.
     */
    private void openBerthBehindACrossing(boolean withTheSwitch) throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 11, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        if (withTheSwitch)
        {
            page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.CURVE, 3, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }
        else
        {
            page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.FEEDBACK, 1, 0, 0, 0, 10, 16, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.STRAIGHT, 2, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.STRAIGHT, 3, 0, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CROSSING, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        // THE CROSSING'S OTHER ROAD: a sensor at 4,0, the curve at 5,0, over the crossing to a station at 5,2.
        page.addComponent(componentType.FEEDBACK, 4, 0, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.CURVE, 5, 0, 3, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 2, 1, 0, 9, 15, accessoryDecoderType.MM2, null);

        if (withTheSwitch) wire(page, 3, 1, 7);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Crossing");
        session.setStation(key(7, 1), true);
        session.setAutoDestination(key(7, 1), false);
        session.setStation(key(5, 2), true);
        session.rebuild();
    }

    /**
     * 1,1 sensor - 2,1 - 3,1 switch - 4,1 - 5,1 - 6,1 - 7,1 berth, with the switch's branch to a sensor at 3,0: a berth with
     * three plain squares between it and its switch.
     */
    private void openBerthBehindALongerRun() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 11, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        wire(page, 3, 1, 7);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Lengths");
        session.setStation(key(7, 1), true);
        session.setAutoDestination(key(7, 1), false);
        session.rebuild();
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

    /** The train walk's menu item's name, which does not change with the count its text carries (MT-533). */
    private static final String TRAIN_WALK_ITEM = "massAssignTrainLengths";

    /** The train walk's label, with the count of trains missing a length (MT-533). */
    private static final String TRAINS_COUNTED = "autosetup.ui.menuMassAssignTrainLengthsCounted";
    private static final String TRAINS = "autosetup.ui.menuMassAssignTrainLengths";

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
     * A square that stops being a station remembers its maximum train length, and has it again when it is made a station
     * again (Adam, 2026-09-24, OB-291).
     *
     * *"make max train length be remembered if a station is changed to a non-station, and then restored if it is changed
     * back to a station.  don't modify the behavior of this attribute: it is still to be ignored for non-stations."*
     * SET-C2 took it off on the way down, because the bulk clear counted every square carrying one and said "on {0}
     * stations" about squares that are not.  It is kept now, and the count still asks about stations only.
     *
     * MUTATION: clear the maximum in `AutonomySession.setStation` again, or count every square that carries one, and this
     * fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testADemotedStationRemembersItsMaximumTrainLength() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setPointProperty(key(5, 1), "maxTrainLength", 8);

        assertEquals(session.tilesWithAMaxTrainLength(), Arrays.asList(key(5, 1)), "precondition");

        session.setStation(key(5, 1), false);

        assertEquals(session.getPointProperty(key(5, 1), "maxTrainLength"), 8, "the square was made pass-through and lost"
            + " its maximum of 8 - Adam, OB-291: \"make max train length be remembered if a station is changed to a"
            + " non-station\"");

        assertTrue(session.tilesWithAMaxTrainLength().isEmpty(), "a square that is no longer a station is counted by"
            + " Clear All Station Max Train Lengths, which says \"on {0} stations\" (SET-C2) - \"it is still to be ignored for"
            + " non-stations\"");

        session.setStation(key(5, 1), true);

        assertEquals(session.tilesWithAMaxTrainLength(), Arrays.asList(key(5, 1)), "made a station again, the square does"
            + " not have its maximum back - Adam, OB-291: \"and then restored if it is changed back to a station\"");
    }

    /**
     * A NEGATIVE maximum is counted by the bulk clear on any square, station or not (SET-B1 kept with OB-291).
     *
     * `Layout.fromJSON` refuses a maximum below 0 on any point and invalidates the whole configuration, so a remembered
     * one on a square that is not a station still stops the railway loading - and the bulk clear is the door that takes
     * one off.  Counting stations only would grey it on the very setting that is stopping the railway.
     *
     * MUTATION: count stations only, negatives included, and this fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testANegativeMaximumOnADemotedStationCanStillBeCleared() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setPointProperty(key(5, 1), "maxTrainLength", -1);

        session.setStation(key(5, 1), false);

        assertEquals(session.getPointProperty(key(5, 1), "maxTrainLength"), -1, "precondition: the negative was not kept");

        assertEquals(session.tilesWithAMaxTrainLength(), Arrays.asList(key(5, 1)), "a negative maximum on a square that"
            + " is not a station stops the railway loading, and Clear All Station Max Train Lengths does not offer to clear it");

        assertEquals(session.clearEveryMaxTrainLength(), 1);

        assertNull(session.getPointProperty(key(5, 1), "maxTrainLength"), "the bulk clear left the negative in place");
    }

    /**
     * A maximum remembered on a square that is not a station does not make the railway one that models lengths (OB-291).
     *
     * *"No max train length"* is listed for every station only on a railway that models lengths somewhere - a measured
     * track, a train with a length, or a station with a maximum (REL-C2).  A maximum on a square that is not a station
     * decides nothing, and Adam: *"it is still to be ignored for non-stations"*.  So on a railway that models nothing
     * else, a demoted station's remembered maximum must not start the notice on every other station.
     *
     * The control first: with the maximum on a station, the other station IS listed, so the fixture can say it.
     *
     * MUTATION: let `modelsAnyLength` read a maximum off any square, and this fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testARememberedMaximumDoesNotStartTheLengthNotices() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setStation(key(1, 1), true);

        session.setPointProperty(key(5, 1), "maxTrainLength", 8);

        assertEquals(noMaximumNoticesAbout(key(1, 1)), 1, "precondition: with a station carrying a maximum, the other"
            + " station is not listed as having none, so this fixture cannot show the notice at all");

        session.setStation(key(5, 1), false);

        assertEquals(noMaximumNoticesAbout(key(1, 1)), 0, "the only maximum on the railway is remembered on a square that"
            + " is no longer a station, and it still makes the railway one that models lengths: 1,1 is listed as having"
            + " none - Adam, OB-291: \"it is still to be ignored for non-stations\"");
    }

    /** How many "no max train length" findings the setup gives about this square. */
    private int noMaximumNoticesAbout(TileKey square)
    {
        int seen = 0;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.NO_MAX_TRAIN_LENGTH.equals(finding.getMessageKey())
                && square.equals(finding.getTile()))
            {
                seen++;
            }
        }

        return seen;
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

        // THE SECOND SENSOR SITS DIRECTLY BELOW THE CROSSING (VC2-C5).
        //
        // With a straight between them the second crossing fell at index 10 of a 13-square leg, and the
        // off-by-one VB2-B3 named - counting `i + 1 < leg.size() - 1`, which drops the LAST intermediate
        // square - still counted it, so this fixture could not tell that mutation from the rule.  Putting
        // the sensor here makes the crossing the last intermediate square on the leg.
        page.addComponent(componentType.FEEDBACK, 3, 3, 1, 0, 6, 12, accessoryDecoderType.MM2, null);

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

        // AND THE ARITHMETIC, which is the point of cutting it out (VC2-C5).  Every piece 4, the crossing 3: the leg
        // runs over the crossing twice, so it counts 3 twice, and the pieces' totals are counted once each.  What
        // the reduction reports is everything but the square the train starts on.
        for (AutonomySession.Stretch piece : session.stretchesNeedingALength())
        {
            assertTrue(session.assignStretchLength(piece, 4), "4 was refused for a piece");
        }

        assertTrue(session.assignSwitchLength(session.sharedSquaresNeedingALengthOn("main"), 3),
            "the crossing would not take a length");

        session.rebuild();

        int pieces = 0;

        for (AutonomySession.Stretch piece : session.stretchesALengthRuleReads()) pieces++;

        for (GraphReducer.ReducedEdge leg : session.getReducer().getEdges())
        {
            int over = 0;

            for (GraphReducer.TileStep step : leg.getPath())
            {
                if (crossing.equals(step.getTile())) over++;
            }

            if (over < 2) continue;

            // THE ENDPOINT TERM IS NOT ZERO HERE, which OP2-C5 supposed it was.
            //
            // Measured 2026-09-20 rather than reasoned about: `getTileLength(leg.getStart())` is 2 on
            // this fixture, so the subtraction is doing work and the claim in the message can fail.
            // The review read the leg as starting on a sensor, which is in no piece and so never
            // measured; what a piece's length actually does is share itself over the piece's squares,
            // and the square this leg starts on takes a share.  Recorded here so the next reader does
            // not re-raise it.
            int expected = 4 * pieces + 3 * over - session.getStore().getTileLength(leg.getStart());

            assertEquals(leg.getLength(), expected,
                "the figure of eight does not add up: its pieces total " + (4 * pieces) + ", it runs over the"
                + " crossing " + over + " times at 3 each, and the reduction does not count the square it starts"
                + " on");
        }
    }

    /**
     * A berth whose approach is half measured is warned about, and a whole one is not (Adam, 2026-09-19, on RTX-C2).
     *
     * *"As long as lengths are specified on the berth, it will work, right?  We want clear warnings to the user if
     * so, then it's fine."*  It does work whole; half measured is the trap, and it is the state this very tool
     * leaves behind between sittings - Mass Assign Lengths gives every switch a length in a step of its own, and
     * MT-454 reaches that step by skipping pieces.  The berth then takes no train at all, because the room walk
     * judges as soon as anything is measured and an unmeasured square is worth nothing to it.
     *
     * Three states, in the order an operator meets them: nothing measured (no warning, nothing is known), the
     * switch measured and the piece not (warned), everything measured (no warning).
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testAHalfMeasuredApproachIsWarnedAbout() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        assertTrue(session.stationsWithAHalfMeasuredApproach().isEmpty(),
            "a railway with nothing measured is warned about, and nothing is known there");

        // WHAT THE SWITCH STEP OF THE WALK LEAVES, with a piece skipped.
        session.setTileLength(key(3, 1), 1);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "the berth behind a measured switch and an unmeasured piece takes no train at all, and nothing says so");

        boolean warned = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.HALF_MEASURED_APPROACH.equals(finding.getMessageKey())
                && key(5, 1).equals(finding.getTile()))
            {
                warned = true;
            }
        }

        assertTrue(warned, "the editor's findings say nothing about a berth that is closed to every train");

        // AND MEASURING THE REST ENDS IT.
        session.setTileLength(key(4, 1), 2);
        session.setTileLength(key(2, 1), 2);
        session.setTileLength(key(1, 1), 2);
        session.setTileLength(key(5, 1), 2);

        assertTrue(session.stationsWithAHalfMeasuredApproach().isEmpty(),
            "the approach is measured throughout and the warning is still up: "
            + session.stationsWithAHalfMeasuredApproach());
    }

    /**
     * The warning is about the berths the rule runs on, and about ONE approach at a time (OP2-B2, OP2-B3).
     *
     * `whyABerthCannotHoldIt` returns at once for a station autonomy chooses - Adam's own exemption, and the
     * default for a station - so a warning about those would be about a refusal that never happens.  And the
     * walk judges the approach a train arrives on: a station with one approach measured throughout and another
     * with nothing measured is half measured on neither.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testTheHalfMeasuredWarningMatchesTheRuleItQuotes() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        session.setTileLength(key(3, 1), 1);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)),
            "precondition: the parking berth is not warned about, so the exemption below shows nothing");

        // A STATION AUTONOMY CHOOSES is exempt from the rule, so it is exempt from the warning.
        session.setAutoDestination(key(5, 1), true);

        assertTrue(session.stationsWithAHalfMeasuredApproach().isEmpty(),
            "an ordinary platform is warned about a refusal that never happens there - the berth rule exempts"
            + " every station autonomy may choose");
    }

    /**
     * The berth's own square counts, as the berth rule counts it: measured alone it makes the approach half measured,
     * and unmeasured it is one of the squares still to measure (TDY-B2).
     *
     * OB-278 made the square a train stands on rail the walk spends first, and `Layout.whyABerthCannotHoldIt` counts it
     * among the measured places - so a berth measured and nothing behind it is JUDGED, and refuses every train longer
     * than it, claiming the unmeasured squares behind for nothing.  The notice walked the leg's path, which leaves out
     * the square the leg ends on, so it never saw that state - the first one the berth rule's own advice produces
     * ("measure the berth square first").
     *
     * MUTATION: stop the notice looking at the leg's end and both claims fail.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testTheBerthsOwnSquareCountsInTheHalfMeasuredNotice() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        // THE BERTH MEASURED, AND NOTHING BEHIND IT.
        session.setTileLength(key(5, 1), 2);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)), "a berth measured on its own"
            + " square with nothing behind it is judged by the berth rule, which then refuses every train longer than the"
            + " berth - and the notice says nothing: " + session.stationsWithAHalfMeasuredApproach());

        // AND THE OTHER WAY ROUND: everything behind measured, the berth not.
        session.setTileLength(key(5, 1), 0);
        session.setTileLength(key(4, 1), 2);
        session.setTileLength(key(3, 1), 1);
        session.setTileLength(key(2, 1), 2);

        assertTrue(session.stationsWithAHalfMeasuredApproach().containsKey(key(5, 1)), "the approach is measured and"
            + " the berth itself is not, which the berth rule counts as a square with no length - and the notice does"
            + " not: " + session.stationsWithAHalfMeasuredApproach());
    }

    /**
     * Segment Length opens empty on a run with no length and no answer, so pressing OK untouched records nothing
     * (GUI-B2).
     *
     * A 0 submitted in Segment Length is an answer since 2026-09-23 (Adam: *"no, add a clear button"*) - kept, never
     * offered again by Mass Assign Lengths, and no longer listed as missing.  The box opened showing what the run
     * measures, which for an unmeasured run was "0", with OK the default: Enter on an unmeasured square wrote the
     * deliberate answer nobody gave.  Mass Assign Lengths' own prompt opens empty for exactly this reason.  An answered
     * 0 still shows as 0, and a length as itself.
     *
     * MUTATION: prefill the measured length whatever it is, as it was, and the first claim fails.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testSegmentLengthOpensEmptyWhereNothingHasBeenSaid() throws IOException
    {
        openBerthBehindASwitch(key(5, 1));

        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        assertEquals(panel.segmentLengthPrefill(key(4, 1), true), "", "Segment Length opens holding a 0 on a run nobody"
            + " has measured or answered, so pressing OK records a deliberate 0 - off Mass Assign Lengths and off every"
            + " list of what still needs measuring - that nobody typed (GUI-B2)");

        session.answerTileLengthsZero(java.util.Collections.singleton(key(4, 1)));

        assertEquals(panel.segmentLengthPrefill(key(4, 1), false), "0", "a square answered 0 opens empty, hiding the"
            + " answer that was given");

        session.setTileLength(key(2, 1), 3);

        assertEquals(panel.segmentLengthPrefill(key(2, 1), false), "3", "a measured square does not open on its length");
    }

    /**
     * Segment Length on a run re-derives the railway once, not once per square (AUS-C1).
     *
     * `touched()` is a full builder construction - a new graph, a new reduction, a new station index - and the
     * single-square setter calls it every time.  Writing a run square by square paid for one of those per square,
     * on the event thread, and then the running layout's rebuild on top.  Counted through the graph's identity,
     * which is what a rebuild replaces.
     *
     * @throws IOException from the fixture
     */
    @Test
    public void testSettingARunsLengthRebuildsOnce() throws IOException
    {
        openARunOfTwoPlainSquares();

        java.util.Map<TileKey, Integer> run = new java.util.LinkedHashMap<>();

        run.put(key(2, 1), 3);
        run.put(key(3, 1), 0);

        Object before = session.getGraph();

        session.setTileLengths(run);

        assertNotSame(session.getGraph(), before, "precondition: nothing was re-derived at all, so nothing was written");

        assertEquals(session.getStore().getTileLength(key(2, 1)), 3, "the run did not take its length");
        assertEquals(session.getStore().getTileLength(key(3, 1)), 0, "the follower was not cleared");

        // AND ONE PER CALL, not one per square: a second write of two squares re-derives once more, so two writes
        // of two squares are two derivations and not four.
        Object after = session.getGraph();

        run.put(key(2, 1), 4);

        session.setTileLengths(run);

        assertNotSame(session.getGraph(), after, "the second write re-derived nothing");
    }
}
