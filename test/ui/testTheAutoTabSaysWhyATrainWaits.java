package ui;

import java.awt.Cursor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomy.AutoLocomotiveStatus;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Mouse;
import support.Settle;

/**
 * The Auto tab's answer to "why is this train not moving": a card whose train has no available path says so, an (i) mark
 * and a hand cursor invite the user to ask, hovering the second line gives the reasons as a tooltip, and clicking it
 * opens the whole list in a window (FR-017).  Each claim stands trains on the single-switch railway in a sandbox,
 * refreshes the tab as the window does, and reads the card, its tooltip and the window the click opens.
 *
 * The tab asks what a hand-driven send meets while autonomy is not running (`explainCannotStart` has no hand reason),
 * so the reasons on show in standby are the stations': switched off, not accepting the train, occupied, a terminus it
 * could not leave.  Autonomy's own refusals (paused, switched-off start) reach a card only when it has not been redrawn
 * since the run began.
 */
public class testTheAutoTabSaysWhyATrainWaits
{
    private static final String PAGE = "1 - Junction";

    /** A train that cannot reverse, stood on the main platform, and the one that stands in its way. */
    private static final String MOVER = "Mover Train";
    private static final String BLOCKER = "Blocker Train";

    private static final int PLATFORM_X = 7, PLATFORM_Y = 3, APPROACH_X = 3, APPROACH_Y = 3, BRANCH_X = 6, BRANCH_Y = 2;

    private static final long PATIENCE_MS = 30000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static int nextAddress;

    // ---------------------------------------------------------------- the claims

    /**
     * A non-reversible train on a platform whose only way out is blocked has no available path: the card says so with
     * the (i) mark and a hand cursor, and hovering the line lists, per station, the reason a hand-driven send meets
     * there (the occupant, the terminus it could not leave).
     *
     * MUTATION: in `fileByStation`, file each reason under the Point's own name (`point` for its `stationName`), and the
     * square's two copies are listed apart, as "Approach (eastbound)" and "Approach (westbound)".
     */
    @Test
    public void testHoveringTheLineListsEachStationsReason() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            Locomotive mover = aMoverBlockedAtTheApproach();

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            assertNotNull(line.getIcon(), "a train with nowhere to go has no (i) mark on its second line");

            Mouse.enter(line);

            assertEquals(line.getCursor().getType(), Cursor.HAND_CURSOR, "the pointer does not invite a click");

            assertEquals(entriesOf(tooltipAfterHover(line)), Arrays.asList(
                "Approach: " + I18n.f("autolayout.why.occupied", BLOCKER),
                "BranchPlatform: " + I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", MOVER),
                "WestEnd: " + I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", MOVER)),
                "the tooltip does not give each station once, with the reason it cannot be reached");
        });
    }

    /**
     * The click opens a window titled for the train, saying where it stands and listing every station that cannot be
     * reached, in name order, each with its own reason: switched off, not accepting this train, occupied.
     *
     * MUTATION: in `whyNotReport`, choose the heading by `layout.isAutoRunning()` the wrong way round
     * (`byHand ? "whyHeaderCandidates" : "whyHeaderByHand"`), and the heading is autonomy's.
     */
    @Test
    public void testClickingTheLineOpensTheWholeList() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            Locomotive mover = aMoverAndABlockerAtTheBranch();

            railway().getPoint("WestEnd").setActive(false);

            for (String copy : new String[] {"Approach (eastbound)", "Approach (westbound)"})
            {
                railway().getPoint(copy).setExcludedLocs(new HashSet<>(Arrays.asList(mover)));
            }

            AutoLocomotiveStatus card = refreshedUntilNoPaths(mover);

            String[] window = clickAndRead(secondLine(card));

            assertEquals(window[0], I18n.f("autolayout.ui.whyTitle", MOVER), "the window is not titled for the train");

            assertEquals(window[1],
                I18n.f("autolayout.ui.whyStanding", MOVER, "MainPlatform") + "\n"
                + "\n"
                + I18n.f("autolayout.ui.whyHeaderByHand", 3) + "\n"
                + "    Approach - " + I18n.f("autolayout.why.excluded", MOVER) + "\n"
                + "    BranchPlatform - " + I18n.f("autolayout.why.occupied", BLOCKER) + "\n"
                + "    WestEnd - " + I18n.t("autolayout.why.inactive"),
                "the window does not say where the train stands, then list each station with its own reason");
        });
    }

    /**
     * A station the train can be sent to by one copy is in neither the window nor the tooltip, though its other copy is
     * refused.
     *
     * MUTATION: in `fileByStation`, skip the Points with no reason (`if (destinations.getReasons().get(point) == null)
     * continue;`), and Approach is listed with its other copy's reason.
     */
    @Test
    public void testAStationItCanBeSentToIsNotListed() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            Locomotive mover = aMoverAndABlockerAtTheBranch();

            // Westbound it can go; eastbound Approach is an intermediate terminus.
            java.util.Map<String, String> reasons = railway().explainDestinations(mover, true);

            assertTrue(reasons.containsKey("Approach (westbound)") && reasons.get("Approach (westbound)") == null
                && reasons.get("Approach (eastbound)") != null,
                "precondition: Approach is not reachable by one copy and refused by the other: " + reasons);

            AutoLocomotiveStatus card = card(mover);

            String window = (String) said(card, "whyNotReport");
            String tooltip = (String) said(card, "whyNotToolTip");

            assertTrue(window.contains("BranchPlatform"), "precondition: the window lists no refused station: " + window);

            assertFalse(window.contains("Approach"),
                "the window lists Approach as a station it cannot be sent to, and it can: " + window);

            assertFalse(tooltip != null && tooltip.contains("Approach"),
                "the tooltip lists Approach as a station it cannot be sent to, and it can: " + tooltip);
        });
    }

    /**
     * Paths found off the event thread for a train that has since left its square are not offered: the card lists no
     * path to double-click and says the train is not on the graph.
     *
     * MUTATION: in `updateState`, keep a late answer (`this.paths = answer == null ? ... : answer`), and its paths are
     * listed under a train that stands nowhere.
     */
    @Test
    public void testPathsForATrainThatHasLeftAreNotOffered() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            final Locomotive mover = train(MOVER, true);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);

            final List<List<org.traincontrol.automation.Edge>> late = railway().getPossiblePaths(mover, true);

            assertFalse(late.isEmpty(), "precondition: the train has no path to be late with");

            // The card the answer was asked for, before the train leaves.
            final AutoLocomotiveStatus card = refreshedUntil(mover, I18n.t("autolayout.ui.doubleClickExecute"));

            SwingUtilities.invokeAndWait(() -> railway().moveLocomotive(null,
                railway().getLocomotiveLocation(mover).getName(), true));

            SwingUtilities.invokeAndWait(() -> card.updateState(mover, late, true));

            Field list = AutoLocomotiveStatus.class.getDeclaredField("locAvailPaths");

            list.setAccessible(true);

            assertEquals(((javax.swing.JList<?>) list.get(card)).getModel().getSize(), 0,
                "paths that arrived after the train left its square are offered to double-click");

            assertEquals(secondLine(card).getText(), I18n.t("autolayout.ui.locNotOnGraph"),
                "the card does not say the train is off the graph");
        });
    }

    /**
     * A card built before its search lands does not say its train has nowhere to go: no "No available paths" and no
     * (i) for a train that has paths, until the answer says so.
     *
     * MUTATION: in `updateState`, take the deferred build's "no answer yet" for "no paths" (`noPathsNow = true` and
     * the "No available paths" text), and the new card says it with the (i).
     */
    @Test
    public void testACardWaitingForItsSearchDoesNotSayItHasNoPaths() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            final Locomotive mover = train(MOVER, true);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);

            assertFalse(railway().getPossiblePaths(mover, true).isEmpty(), "precondition: the train has nowhere to go");

            final AutoLocomotiveStatus[] card = new AutoLocomotiveStatus[1];

            SwingUtilities.invokeAndWait(() -> card[0] = new AutoLocomotiveStatus(mover, ui, true));

            assertNotEquals(secondLine(card[0]).getText(), I18n.t("autolayout.ui.noAvailPaths"),
                "a card waiting for its search says the train has no available paths, and it has some");

            assertNull(secondLine(card[0]).getIcon(), "a card waiting for its search offers a reason to hover for");
        });
    }

    /** What a card's private answer method says, asked on the event thread as the card asks it. */
    private static Object said(final AutoLocomotiveStatus card, String method) throws Exception
    {
        final java.lang.reflect.Method asked = AutoLocomotiveStatus.class.getDeclaredMethod(method);

        asked.setAccessible(true);

        final Object[] out = new Object[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                out[0] = asked.invoke(card);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new RuntimeException(failed);
            }
        });

        return out[0];
    }

    /**
     * In the window the line saying where the train stands and the heading are bold and the station lines are not, so
     * the two read apart.  A heading is told by position, not by its words, which the other languages translate.
     *
     * MUTATION: in `write`, take a line to be a heading when it IS indented (`line.startsWith("    ")`), and the
     * station lines turn bold and the headings plain.
     */
    @Test
    public void testTheHeadingsAreBoldAndTheStationLinesAreNot() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            Locomotive mover = aMoverBlockedAtTheApproach();

            AutoLocomotiveStatus card = refreshedUntilNoPaths(mover);

            List<String> bold = new ArrayList<>();
            List<String> plain = new ArrayList<>();

            clickAndRead(secondLine(card), pane ->
            {
                javax.swing.text.StyledDocument doc = pane.getStyledDocument();

                String text = doc.getText(0, doc.getLength());

                int from = 0;

                for (String line : text.split("\n", -1))
                {
                    if (!line.isEmpty())
                    {
                        boolean isBold = StyleConstants.isBold(doc.getCharacterElement(from).getAttributes());

                        (isBold ? bold : plain).add(line);
                    }

                    from += line.length() + 1;
                }
            });

            assertEquals(bold, Arrays.asList(I18n.f("autolayout.ui.whyStanding", MOVER, "MainPlatform"),
                I18n.f("autolayout.ui.whyHeaderByHand", 3)), "the bold lines are not the two headings");

            assertEquals(plain.size(), 3, "the station lines are not plain: " + plain);

            for (String line : plain) assertTrue(line.startsWith("    "), "a plain line is not a station's: " + line);
        });
    }

    /**
     * A train with a way to go has nothing to explain: its line asks to double-click a path, carries no mark and the
     * ordinary pointer, hovering it gives no tooltip, and clicking it opens no window.
     *
     * MUTATION: in the label's `mouseClicked`, open the window whatever `noPathsNow` says, and the click opens one.
     */
    @Test
    public void testATrainWithAWayToGoHasNothingToExplain() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            Locomotive mover = train(MOVER, false);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);

            AutoLocomotiveStatus card = refreshedUntil(mover, I18n.t("autolayout.ui.doubleClickExecute"));

            assertSaysNothing(secondLine(card));
        });
    }

    /**
     * A train that is on the list but not on the track diagram is told apart from one with nowhere to go: its card says it
     * is not placed, and there is nothing to explain.
     *
     * MUTATION: set `noPathsNow` in the not-on-the-graph branch too, and the card gets a mark and a tooltip.
     */
    @Test
    public void testATrainNotOnTheDiagramHasNothingToExplain() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            Locomotive nowhere = train(MOVER, false);

            SwingUtilities.invokeAndWait(() -> railway().getLocomotivesToRun().add(nowhere));

            AutoLocomotiveStatus card = refreshedUntil(nowhere, I18n.t("autolayout.ui.locNotOnGraph"));

            assertSaysNothing(secondLine(card));
        });
    }

    /**
     * When the way clears, the explanation goes with it: after the blocker leaves and the tab is redrawn, the line asks for
     * a double-click again, the mark is gone, the tooltip the hover had given is gone, and a click opens nothing.
     *
     * MUTATION: drop `this.locDest.setToolTipText(null)` from `updateState`, and the old reasons stay on the line.
     */
    @Test
    public void testTheExplanationGoesWhenTheWayClears() throws Exception
    {
        onTheRailway("single-switch", () ->
        {
            Locomotive mover = aMoverBlockedAtTheApproach();

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            assertNotNull(tooltipAfterHover(line), "precondition: hovering a stuck train gives no reasons");

            SwingUtilities.invokeAndWait(() -> railway().moveLocomotive(null,
                railway().getLocomotiveLocation(model.getLocByName(BLOCKER)).getName(), true));

            AutoLocomotiveStatus card = redrawnUntil(mover, I18n.t("autolayout.ui.doubleClickExecute"), true);

            assertSame(secondLine(card), line, "precondition: the redraw built the card again, so the old line is gone");

            assertNull(line.getToolTipText(), "the old reasons are still on the line, before the pointer moves");

            assertSaysNothing(line);
        });
    }

    /**
     * Starting autonomy turns the card to "No active path." and takes the explanation away: the mark, the tooltip and
     * the window are for a train standing by, not for one the run is handling.
     *
     * MUTATION: drop `this.noPathsNow = false` from `updateState`, and the started run's card still explains.
     */
    @Test
    public void testWhileAutonomyRunsTheCardOffersNoExplanation() throws Exception
    {
        needsADisplay();

        Layout layout = null;

        try
        {
            // The frozen railway: the small one's setup has errors, which Start refuses
            open("live-snapshot");

            Locomotive mover = aTrainWithNowhereToGo();

            layout = railway();

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            assertNotNull(tooltipAfterHover(line), "precondition: hovering a stuck train gives no reasons");

            Settle.simulate(layout);

            Components.press(startButton());

            final Layout started = layout;

            assertTrue(Settle.until(started::isAutoRunning, PATIENCE_MS), "autonomy did not start");

            AutoLocomotiveStatus card = redrawnUntil(mover, I18n.t("autolayout.ui.noActivePath"), true);

            assertSame(secondLine(card), line, "precondition: the redraw built the card again, so the old line is gone");

            assertNull(line.getToolTipText(), "the reasons are still on the line, before the pointer moves");

            assertSaysNothing(line);
        }
        finally
        {
            if (layout != null && layout.isAutoRunning()) Settle.gracefully(layout, Settle.PATIENCE_MS);

            close();
        }
    }

    /**
     * A card that was not redrawn when autonomy started explains autonomy's refusal, not the standby list: a paused train
     * is "paused", on its own, wrapped for a tooltip.
     *
     * MUTATION: in `whyNotToolTip`, skip the `explainCannotStart` answer, and the tooltip lists the stations instead.
     */
    @Test
    public void testACardNotRedrawnSinceStartSaysThePauseInTheTooltip() throws Exception
    {
        onAStuckCardThenAutonomyStarts((layout, mover, line) ->
        {
            startBehindTheCardsBack(layout, mover);

            String tip = tooltipAfterHover(line);

            assertNotNull(tip, "the card gave no reason while autonomy ran");

            assertTrue(tip.startsWith("<html"), "the reason is not wrapped as a tooltip: " + tip);

            assertTrue(tip.contains(I18n.t("autolayout.why.paused")), "the tooltip does not say the train is paused: " + tip);

            assertFalse(tip.contains("Approach"), "the tooltip lists stations beside the pause: " + tip);
        });
    }

    /**
     * The same card, clicked, opens a window that says where the train stands and then the one reason, with no list of
     * stations under it: a train that is switched off at its own station is told so.
     *
     * MUTATION: in `whyNotReport`, drop the `return` after the `explainCannotStart` answer, and the list follows it.
     */
    @Test
    public void testACardNotRedrawnSinceStartSaysTheSwitchedOffStationInTheWindow() throws Exception
    {
        onAStuckCardThenAutonomyStarts((layout, mover, line) ->
        {
            SwingUtilities.invokeAndWait(() -> railway().getLocomotiveLocation(mover).setActive(false));

            startBehindTheCardsBack(layout, null);

            String[] window = clickAndRead(line);

            assertEquals(window[1],
                I18n.f("autolayout.ui.whyStanding", MOVER, "MainPlatform") + "\n"
                + "\n"
                + I18n.f("autolayout.why.startInactive", "MainPlatform"),
                "the window does not say why autonomy will not start the train, and only that");
        });
    }

    /**
     * On a big railway the tooltip stops at twelve stations and says there are more, while the window lists them all:
     * every station but the one the train stands on is switched off, and the window's count matches its lines.
     *
     * MUTATION: let the tooltip show thirteen (`shown == 13`), and it lists one more than the twelve.
     */
    @Test
    public void testATallListIsCutShortInTheTooltipAndWholeInTheWindow() throws Exception
    {
        onTheRailway("live-snapshot", () ->
        {
            Locomotive mover = aTrainWithNowhereToGo();

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            String tip = tooltipAfterHover(line);

            assertNotNull(tip, "hovering a train with no station to go to gave nothing");

            assertTrue(tip.endsWith("<br>...</html>"), "the tooltip does not say there are more stations: " + tip);

            assertEquals(tip.split("<br>").length, 13, "the tooltip does not stop at twelve stations: " + tip);

            String shown = clickAndRead(line)[1];

            List<String> stations = new ArrayList<>();

            for (String each : shown.split("\n"))
            {
                if (each.startsWith("    ")) stations.add(each);
            }

            assertTrue(stations.size() > 12, "precondition: the frozen railway has no thirteenth station to cut: " + stations);

            assertTrue(shown.contains(I18n.f("autolayout.ui.whyHeaderByHand", stations.size())), "the window's count is not its"
                + " " + stations.size() + " lines: " + shown);
        });
    }

    // ---------------------------------------------------------------- what the user sees

    /** The line carries no mark, the ordinary pointer, no tooltip however long the pointer stays, and opens nothing. */
    private static void assertSaysNothing(JLabel line) throws Exception
    {
        assertNull(line.getIcon(), "the line carries a mark although there is nothing to explain");

        Mouse.enter(line);

        assertEquals(line.getCursor().getType(), Cursor.DEFAULT_CURSOR, "the pointer invites a click that explains nothing");

        assertNull(tooltipAfterHover(line, 3000), "the line gives reasons although there is nothing to explain");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Mouse.click(line);

            assertFalse(answering.awaitAsked(1, 1500), "the click opened a window although there is nothing to explain: "
                + answering.asked());
        }
    }

    /**
     * What hovering the line gives, which arrives a moment after the pointer: worked out off the event thread under the
     * railway's monitor, which a big railway's searches hold for seconds.
     */
    private static String tooltipAfterHover(JLabel line) throws Exception
    {
        return tooltipAfterHover(line, PATIENCE_MS);
    }

    /** The same, giving up after `patience`: null when nothing comes by then. */
    private static String tooltipAfterHover(JLabel line, long patience) throws Exception
    {
        Mouse.exit(line);
        Mouse.enter(line);

        Components.waitFor(() -> line.getToolTipText() != null, patience);

        return line.getToolTipText();
    }

    /** The tooltip's lines, sorted, without the markup. */
    private static List<String> entriesOf(String tooltip)
    {
        assertNotNull(tooltip, "hovering gave no tooltip");

        assertTrue(tooltip.startsWith("<html>") && tooltip.endsWith("</html>"), "the tooltip is not a list: " + tooltip);

        List<String> out = new ArrayList<>(Arrays.asList(
            tooltip.substring("<html>".length(), tooltip.length() - "</html>".length()).split("<br>")));

        Collections.sort(out);

        return out;
    }

    private interface Reading
    {
        void read(JTextPane pane) throws Exception;
    }

    /** Clicks the line and returns the window's title and its text once the answer has arrived. */
    private static String[] clickAndRead(JLabel line) throws Exception
    {
        return clickAndRead(line, pane -> { });
    }

    /**
     * Clicks the line, waits for the window to fill in (it opens saying nothing and fills when the answer arrives),
     * lets the reading look at it, and presses its OK.
     */
    private static String[] clickAndRead(JLabel line, final Reading reading) throws Exception
    {
        final String[] shown = new String[2];

        final Exception[] failed = new Exception[1];

        try (Answering answering = Answering.start(message ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof JDialog) || !window.isShowing()) continue;

                JTextPane pane = Components.find(window, JTextPane.class, null);

                if (pane == null) continue;

                Components.waitFor(() -> !pane.getText().trim().isEmpty(), PATIENCE_MS);

                try
                {
                    shown[0] = ((JDialog) window).getTitle();
                    shown[1] = Components.onTheEventThread(() -> pane.getDocument().getText(0, pane.getDocument().getLength()))
                        .replaceAll("\n+$", "");

                    Components.onTheEventThread(() ->
                    {
                        reading.read(pane);

                        return null;
                    });
                }
                catch (Exception e)
                {
                    failed[0] = e;
                }
            }

            return Answering.pressAt(0);
        }))
        {
            Mouse.click(line);

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "the click opened no window");

            Components.waitFor(() -> shown[1] != null || failed[0] != null, PATIENCE_MS);
        }

        if (failed[0] != null) throw failed[0];

        assertNotNull(shown[1], "the window had no text pane");

        return shown;
    }

    // ---------------------------------------------------------------- the railway

    /**
     * A train that can reverse, on BottomMainB of the frozen railway, with every other station switched off, so its card
     * has nowhere to send it and a long list of reasons.
     */
    private static Locomotive aTrainWithNowhereToGo() throws Exception
    {
        final Locomotive mover = train(MOVER, true);

        TileKey square = null;

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if ("BottomMainB".equals(session.getStore().getPointName(key))) square = key;
        }

        assertNotNull(square, "precondition: the frozen railway has no BottomMainB");

        stand(MOVER, square);

        SwingUtilities.invokeAndWait(() ->
        {
            Point at = railway().getLocomotiveLocation(mover);

            for (Point point : railway().getPoints())
            {
                if (point.isDestination() && !point.isSamePlaceAs(at)) point.setActive(false);
            }
        });

        return mover;
    }

    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the Auto tab is a window");
    }

    /** What a claim does on the open railway. */
    private interface OnTheRailway
    {
        void run() throws Exception;
    }

    /** Skips without a display; otherwise opens the scenario's railway, runs the claim on it, and closes it. */
    private static void onTheRailway(String scenario, OnTheRailway claim) throws Exception
    {
        needsADisplay();

        try
        {
            open(scenario);

            claim.run();
        }
        finally
        {
            close();
        }
    }

    /** What a claim does with the railway, the train that cannot move and its card's second line. */
    private interface OnAStuckCard
    {
        void run(Layout layout, Locomotive mover, JLabel line) throws Exception;
    }

    /**
     * Opens the single-switch railway with the mover stuck behind the blocker, hands the claim the card's second line,
     * and stops any autonomy the claim started before closing.
     */
    private static void onAStuckCardThenAutonomyStarts(OnAStuckCard claim) throws Exception
    {
        needsADisplay();

        Layout layout = null;

        try
        {
            open("single-switch");

            Locomotive mover = aMoverBlockedAtTheApproach();

            layout = railway();

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            claim.run(layout, mover, line);
        }
        finally
        {
            if (layout != null) Settle.gracefully(layout, Settle.PATIENCE_MS);

            close();
        }
    }

    /** A train that cannot reverse on the main platform, and the blocker on the approach, which it cannot pass. */
    private static Locomotive aMoverBlockedAtTheApproach() throws Exception
    {
        Locomotive mover = train(MOVER, false);

        train(BLOCKER, false);

        stand(MOVER, PLATFORM_X, PLATFORM_Y);
        stand(BLOCKER, APPROACH_X, APPROACH_Y);

        return mover;
    }

    /** A train that can reverse on the main platform, and a blocker on the branch platform. */
    private static Locomotive aMoverAndABlockerAtTheBranch() throws Exception
    {
        Locomotive mover = train(MOVER, true);

        train(BLOCKER, true);

        stand(MOVER, PLATFORM_X, PLATFORM_Y);
        stand(BLOCKER, BRANCH_X, BRANCH_Y);

        return mover;
    }

    /** The sandboxed railway, with its own window, in simulation and with no Central Station. */
    private static void open(String scenario) throws Exception
    {
        // Inside a try, so a failure part way through puts the layout preference back (OB-111).
        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor(scenario));

            if ("live-snapshot".equals(scenario)) sandbox.withNoTrains();

            model = support.TestStation.simulatedWithWindowInDebug();

            model.setNetworkCommState(false);

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "there is no window");

            assertTrue(Components.waitFor(() -> ui.getAutonomySession() != null && model.getAutoLayoutIfLoaded() != null,
                PATIENCE_MS), "the window built no railway");

            session = ui.getAutonomySession();

            nextAddress = 3401;
        }
        catch (Throwable failed)
        {
            close();

            throw failed;
        }
    }

    private static void close() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            ui = null;
            model = null;
            session = null;

            if (sandbox != null) sandbox.close();

            sandbox = null;
        }
    }

    private static Layout railway()
    {
        return model.getAutoLayout();
    }

    /** A train made for the claim, in memory only; reversible where the claim wants termini to accept it. */
    private static Locomotive train(String name, boolean reversible)
    {
        Locomotive made = model.newMM2Locomotive(name, nextAddress++);

        made.setReversible(reversible);

        return made;
    }

    private static void stand(String name, int x, int y) throws Exception
    {
        stand(name, new TileKey(PAGE, x, y));
    }

    private static void stand(final String name, final TileKey tile) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (String copy : session.facingsFor(tile).keySet())
            {
                if (railway().getPoint(copy) != null && railway().getLocomotiveLocation(model.getLocByName(name)) == null)
                {
                    railway().moveLocomotive(name, copy, false);
                }
            }

            session.placeLocomotive(tile, name);
        });

        assertNotNull(railway().getLocomotiveLocation(model.getLocByName(name)), "precondition: " + name + " could not be stood"
            + " on " + tile);
    }

    /**
     * Autonomy begun without the window being told, as a card sees it in the moment before its redraw: the run goes, the
     * card does not.  A paused train (when one is given) is the reason the run will not move it.
     */
    private static void startBehindTheCardsBack(Layout layout, Locomotive paused) throws Exception
    {
        if (paused != null) paused.setAutonomyPaused(true);

        layout.runLocomotives();

        assertTrue(layout.isAutoRunning(), "precondition: autonomy did not start");
    }

    private static JButton startButton() throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField("startAutonomy");

        field.setAccessible(true);

        return (JButton) field.get(ui);
    }

    // ---------------------------------------------------------------- the Auto tab

    /** Rebuilds the tab's cards, as a placement does, and waits until the train's new card says this. */
    private static AutoLocomotiveStatus refreshedUntil(Locomotive who, String second) throws Exception
    {
        return redrawnUntil(who, second, false);
    }

    /**
     * Redraws the tab and waits until the train's card says this.  A redraw "from outside" (a train arriving, a run
     * starting) updates the cards that are there, so the line the user is pointing at is the same label afterwards.
     */
    private static AutoLocomotiveStatus redrawnUntil(Locomotive who, String second, boolean external) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.repaintAutoLocList(external));

        assertTrue(Components.waitFor(() ->
        {
            AutoLocomotiveStatus card = card(who);

            try
            {
                return card != null && second.equals(secondLine(card).getText());
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        }, PATIENCE_MS), "the card of " + who.getName() + " never said \"" + second + "\"; it says \"" + saying(who) + "\"");

        Components.settle();

        return card(who);
    }

    private static AutoLocomotiveStatus refreshedUntilNoPaths(Locomotive who) throws Exception
    {
        return refreshedUntil(who, I18n.t("autolayout.ui.noAvailPaths"));
    }

    private static String saying(Locomotive who)
    {
        try
        {
            AutoLocomotiveStatus card = card(who);

            return card == null ? "(no card)" : secondLine(card).getText();
        }
        catch (Exception e)
        {
            return e.toString();
        }
    }

    /** The train's card on the Auto tab as drawn now: the tab is rebuilt by a redraw, so it is looked for each time. */
    private static AutoLocomotiveStatus card(Locomotive who)
    {
        List<AutoLocomotiveStatus> all = Components.all(ui.getContentPane(), AutoLocomotiveStatus.class,
            c -> who.equals(c.getLocomotive()));

        return all.isEmpty() ? null : all.get(0);
    }

    /** The card's second line, the one the user hovers and clicks. */
    private static JLabel secondLine(AutoLocomotiveStatus card) throws Exception
    {
        Field field = AutoLocomotiveStatus.class.getDeclaredField("locDest");

        field.setAccessible(true);

        return (JLabel) field.get(card);
    }
}
