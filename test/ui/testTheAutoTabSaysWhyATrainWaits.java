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
     * MUTATION: in `whyNotToolTip`, key each reason by the Point's own name (`String station = entry.getKey()`), and the
     * square's two copies are listed apart, as "Approach (eastbound)" and "Approach (westbound)".
     */
    @Test
    public void testHoveringTheLineListsEachStationsReason() throws Exception
    {
        needsADisplay();

        try
        {
            open("single-switch");

            Locomotive mover = train(MOVER, false);

            Locomotive blocker = train(BLOCKER, false);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);
            stand(BLOCKER, APPROACH_X, APPROACH_Y);

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            assertNotNull(line.getIcon(), "a train with nowhere to go has no (i) mark on its second line");

            Mouse.enter(line);

            assertEquals(line.getCursor().getType(), Cursor.HAND_CURSOR, "the pointer does not invite a click");

            assertEquals(entriesOf(tooltipAfterHover(line)), Arrays.asList(
                "Approach: " + I18n.f("autolayout.why.occupied", BLOCKER),
                "BranchPlatform: " + I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", MOVER),
                "WestEnd: " + I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", MOVER)),
                "the tooltip does not give each station once, with the reason it cannot be reached");
        }
        finally
        {
            close();
        }
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
        needsADisplay();

        try
        {
            open("single-switch");

            Locomotive mover = train(MOVER, true);

            train(BLOCKER, true);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);
            stand(BLOCKER, BRANCH_X, BRANCH_Y);

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
        }
        finally
        {
            close();
        }
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
        needsADisplay();

        try
        {
            open("single-switch");

            Locomotive mover = train(MOVER, false);

            train(BLOCKER, false);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);
            stand(BLOCKER, APPROACH_X, APPROACH_Y);

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
        }
        finally
        {
            close();
        }
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
        needsADisplay();

        try
        {
            open("single-switch");

            Locomotive mover = train(MOVER, false);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);

            AutoLocomotiveStatus card = refreshedUntil(mover, I18n.t("autolayout.ui.doubleClickExecute"));

            assertSaysNothing(secondLine(card));
        }
        finally
        {
            close();
        }
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
        needsADisplay();

        try
        {
            open("single-switch");

            Locomotive nowhere = train(MOVER, false);

            SwingUtilities.invokeAndWait(() -> railway().getLocomotivesToRun().add(nowhere));

            AutoLocomotiveStatus card = refreshedUntil(nowhere, I18n.t("autolayout.ui.locNotOnGraph"));

            assertSaysNothing(secondLine(card));
        }
        finally
        {
            close();
        }
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
        needsADisplay();

        try
        {
            open("single-switch");

            Locomotive mover = train(MOVER, false);

            Locomotive blocker = train(BLOCKER, false);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);
            stand(BLOCKER, APPROACH_X, APPROACH_Y);

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            assertNotNull(tooltipAfterHover(line), "precondition: hovering a stuck train gives no reasons");

            SwingUtilities.invokeAndWait(() -> railway().moveLocomotive(null, railway().getLocomotiveLocation(blocker).getName(),
                true));

            AutoLocomotiveStatus card = redrawnUntil(mover, I18n.t("autolayout.ui.doubleClickExecute"), true);

            assertSame(secondLine(card), line, "precondition: the redraw built the card again, so the old line is gone");

            assertNull(line.getToolTipText(), "the old reasons are still on the line, before the pointer moves");

            assertSaysNothing(line);
        }
        finally
        {
            close();
        }
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
        needsADisplay();

        Layout layout = null;

        try
        {
            open("single-switch");

            Locomotive mover = train(MOVER, false);

            Locomotive blocker = train(BLOCKER, false);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);
            stand(BLOCKER, APPROACH_X, APPROACH_Y);

            layout = railway();

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            startBehindTheCardsBack(layout, mover);

            String tip = tooltipAfterHover(line);

            assertNotNull(tip, "the card gave no reason while autonomy ran");

            assertTrue(tip.startsWith("<html"), "the reason is not wrapped as a tooltip: " + tip);

            assertTrue(tip.contains(I18n.t("autolayout.why.paused")), "the tooltip does not say the train is paused: " + tip);

            assertFalse(tip.contains("Approach"), "the tooltip lists stations beside the pause: " + tip);
        }
        finally
        {
            if (layout != null) Settle.gracefully(layout, Settle.PATIENCE_MS);

            close();
        }
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
        needsADisplay();

        Layout layout = null;

        try
        {
            open("single-switch");

            Locomotive mover = train(MOVER, false);

            Locomotive blocker = train(BLOCKER, false);

            stand(MOVER, PLATFORM_X, PLATFORM_Y);
            stand(BLOCKER, APPROACH_X, APPROACH_Y);

            layout = railway();

            JLabel line = secondLine(refreshedUntilNoPaths(mover));

            SwingUtilities.invokeAndWait(() -> railway().getLocomotiveLocation(mover).setActive(false));

            startBehindTheCardsBack(layout, null);

            String[] window = clickAndRead(line);

            assertEquals(window[1],
                I18n.f("autolayout.ui.whyStanding", MOVER, "MainPlatform") + "\n"
                + "\n"
                + I18n.f("autolayout.why.startInactive", "MainPlatform"),
                "the window does not say why autonomy will not start the train, and only that");
        }
        finally
        {
            if (layout != null) Settle.gracefully(layout, Settle.PATIENCE_MS);

            close();
        }
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
        needsADisplay();

        try
        {
            open("live-snapshot");

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
        }
        finally
        {
            close();
        }
    }

    // ---------------------------------------------------------------- what the user sees

    /** The line carries no mark, the ordinary pointer, no tooltip however long the pointer stays, and opens nothing. */
    private static void assertSaysNothing(JLabel line) throws Exception
    {
        assertNull(line.getIcon(), "the line carries a mark although there is nothing to explain");

        Mouse.enter(line);

        assertEquals(line.getCursor().getType(), Cursor.DEFAULT_CURSOR, "the pointer invites a click that explains nothing");

        assertNull(tooltipAfterHover(line), "the line gives reasons although there is nothing to explain");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Mouse.click(line);

            assertFalse(answering.awaitAsked(1, 1500), "the click opened a window although there is nothing to explain: "
                + answering.asked());
        }
    }

    /** What hovering the line gives, which arrives a moment after the pointer: null when nothing comes. */
    private static String tooltipAfterHover(JLabel line) throws Exception
    {
        Mouse.exit(line);
        Mouse.enter(line);

        Components.waitFor(() -> line.getToolTipText() != null, 3000);

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

    /** The sandboxed railway, with its own window, in simulation and with no Central Station. */
    private static void open(String scenario) throws Exception
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
