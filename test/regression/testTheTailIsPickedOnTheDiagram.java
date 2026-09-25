package regression;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The tail question is put on the diagram: the sensors are lit, and a click on one answers it (FR-100).
 *
 * Adam, 2026-09-24: *"when asking about the tail, instead of showing the list of points, highlight possible squares on
 * the diagram and ask the user to click one.  only show the list if there are options on another page."*  And: *"you
 * can probably piggyback off the highlight feature and s88 click events."*
 *
 * On the frozen railway (OB-111): a five-unit train at Tunnel from the north has reached TunnelPre on the one rail and
 * 12,7 on the other, both on 1 - Main.  The question is asked owned by the main window, as a placement door asks it,
 * but off the event thread, so the test can click - a placement door asks on the event thread, which
 * `testAQuestionAskedOnTheEventThreadIsAnsweredByAClick` covers (TDU-C2); and the click is TunnelPre's own tile on the
 * main diagram, through the listener every s88 tile has.
 *
 * MUTATION: ask with the list again, and the click answers nothing.
 *
 * @author Adam
 */
public class testTheTailIsPickedOnTheDiagram
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("clicking the diagram needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        TailCrossedPrompt.answerForTests(null);

        if (sandbox != null) sandbox.close();
    }

    /**
     * The list, not the diagram, where a choice's sensor is on another page or is not drawn (FR-100).
     *
     * Adam: *"only show the list if there are options on another page."*  Not drawn is the same case seen from the
     * window: a square nobody can see cannot be clicked.
     *
     * MUTATION: drop either half of `putsTheQuestionOnTheDiagram`, and this fails.
     */
    @Test
    public void testAChoiceOnAnotherPageOrNotDrawnKeepsTheList()
    {
        TileKey train = new TileKey("1 - Main", 5, 5);
        TileKey near = new TileKey("1 - Main", 2, 2);
        TileKey far = new TileKey("2 - Bottom", 3, 3);

        assertTrue(TailCrossedPrompt.putsTheQuestionOnTheDiagram(train, java.util.Arrays.asList(near,
            new TileKey("1 - Main", 7, 5)), square -> true), "precondition: two sensors on the train's own page, both"
            + " drawn, are not put on the diagram");

        assertFalse(TailCrossedPrompt.putsTheQuestionOnTheDiagram(train, java.util.Arrays.asList(near, far),
            square -> true), "a question with one of its sensors on another page was put on the diagram - Adam, FR-100:"
            + " \"only show the list if there are options on another page\"");

        assertFalse(TailCrossedPrompt.putsTheQuestionOnTheDiagram(train, java.util.Arrays.asList(near,
            new TileKey("1 - Main", 7, 5)), square -> !square.equals(near)), "a question with a sensor that is drawn"
            + " nowhere was put on the diagram, where it cannot be clicked");
    }

    @Test
    public void testAClickOnALitSensorAnswersTheQuestion() throws Exception
    {
        TailCrossedPrompt.answerForTests(null);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "precondition: the frozen railway built no autonomy layout");

        Point tunnel = layout.getPoint("Tunnel (southbound)");

        assertNotNull(tunnel, "precondition: no Tunnel (southbound)");

        List<TailCrossedPrompt.Choice> choices = TailCrossedPrompt.choicesFor(layout, tunnel, "N", 5, null);

        TailCrossedPrompt.Choice tunnelPre = null;

        for (TailCrossedPrompt.Choice choice : choices)
        {
            if (choice.getFarthest().getName().startsWith("TunnelPre")) tunnelPre = choice;
        }

        assertTrue(choices.size() > 1 && tunnelPre != null, "precondition: a five-unit train at Tunnel from the north is"
            + " not offered TunnelPre and something else");

        AutonomySession session = ui.getAutonomySession();

        assertNotNull(session, "precondition: the window has no autonomy setup");

        TileKey square = session.getStationIndex().squareOf(tunnelPre.getFarthest());

        assertNotNull(square, "precondition: TunnelPre has no square");

        final java.util.Set<LayoutLabel> labels = ui.getDiagramTileRegistry().labelsFor(square);

        assertFalse(labels.isEmpty(), "precondition: TunnelPre's square " + square + " is not drawn on the main diagram");

        final LayoutLabel label = labels.iterator().next();

        ExecutorService asker = Executors.newSingleThreadExecutor();

        try
        {
            Future<TailCrossedPrompt.Answer> asked = asker.submit(() ->
                TailCrossedPrompt.askAfterPlacement(layout, tunnel, "N", 5, "FR-100 train", ui, null));

            Thread.sleep(2000);

            javax.swing.SwingUtilities.invokeAndWait(() -> label.dispatchEvent(new java.awt.event.MouseEvent(label,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false,
                java.awt.event.MouseEvent.BUTTON1)));

            TailCrossedPrompt.Answer answer;

            try
            {
                answer = asked.get(10, TimeUnit.SECONDS);
            }
            catch (TimeoutException unanswered)
            {
                fail("TunnelPre's tile was clicked on the diagram and the tail question is still waiting - it was not put"
                    + " on the diagram.  Adam, FR-100: \"highlight possible squares on the diagram and ask the user to"
                    + " click one\"");

                return;
            }

            assertTrue(answer.wasAnswered(), "the click on TunnelPre was taken as no answer");

            assertEquals(answer.getRoad(), tunnelPre.getRoad(), "the click on TunnelPre recorded another road");
        }
        finally
        {
            // WHATEVER WAS PUT UP IS TAKEN DOWN, so a failure here cannot leave a modal list holding the next class.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window instanceof javax.swing.JDialog && window.isShowing()) window.dispose();
                }
            });

            asker.shutdownNow();
        }
    }

    /** The frozen railway's Tunnel question, and TunnelPre's choice and tile on the main diagram. */
    private static final class TunnelQuestion
    {
        Layout layout;
        Point tunnel;
        TailCrossedPrompt.Choice tunnelPre;
        LayoutLabel label;
    }

    private static TunnelQuestion tunnelQuestion()
    {
        TunnelQuestion q = new TunnelQuestion();

        q.layout = model.getAutoLayout();

        assertNotNull(q.layout, "precondition: the frozen railway built no autonomy layout");

        q.tunnel = q.layout.getPoint("Tunnel (southbound)");

        assertNotNull(q.tunnel, "precondition: no Tunnel (southbound)");

        for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(q.layout, q.tunnel, "N", 5, null))
        {
            if (choice.getFarthest().getName().startsWith("TunnelPre")) q.tunnelPre = choice;
        }

        assertNotNull(q.tunnelPre, "precondition: a five-unit train at Tunnel from the north is not offered TunnelPre");

        TileKey square = ui.getAutonomySession().getStationIndex().squareOf(q.tunnelPre.getFarthest());

        java.util.Set<LayoutLabel> labels = ui.getDiagramTileRegistry().labelsFor(square);

        assertFalse(labels.isEmpty(), "precondition: TunnelPre's square is not drawn on the main diagram");

        q.label = labels.iterator().next();

        return q;
    }

    private static void click(LayoutLabel label, int count) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> label.dispatchEvent(new java.awt.event.MouseEvent(label,
            java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, count, false,
            java.awt.event.MouseEvent.BUTTON1)));
    }

    /** Takes down whatever a question put up, so a failure cannot leave a dialog holding the next class. */
    private static void takeDownDialogs() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()) window.dispose();
            }
        });
    }

    /**
     * A double-click on a lit sensor answers the question and does not then flip the sensor (TDU-B3).
     *
     * The first click answers and takes the question down; the second arrived with nothing waiting, and the sensor's own
     * click flipped it - announced as a real sensor change, which is what the question's taking the click exists to
     * prevent, and which an armed route acts on.
     *
     * MUTATION: let the second click of a double-click reach the sensor, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testADoubleClickAnswersAndDoesNotFlipTheSensor() throws Exception
    {
        TailCrossedPrompt.answerForTests(null);

        final TunnelQuestion q = tunnelQuestion();

        String sensor = q.tunnelPre.getFarthest().getS88();

        boolean before = model.getFeedbackState(sensor);

        ExecutorService asker = Executors.newSingleThreadExecutor();

        try
        {
            Future<TailCrossedPrompt.Answer> asked = asker.submit(() ->
                TailCrossedPrompt.askAfterPlacement(q.layout, q.tunnel, "N", 5, "FR-100 train", ui, null));

            Thread.sleep(2000);

            click(q.label, 1);
            click(q.label, 2);

            assertTrue(asked.get(10, TimeUnit.SECONDS).wasAnswered(), "precondition: the click on TunnelPre did not"
                + " answer the question");

            for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            assertEquals(model.getFeedbackState(sensor), before, "the second click of a double-click on TunnelPre, the"
                + " sensor that answered the tail question, flipped the sensor - a faked occupancy change (TDU-B3)");

            // AND ONLY THAT DOUBLE-CLICK (TDU2-C1): a later one on the same sensor, with nothing asked, flips it and flips
            // it back, as a double-click on a sensor always has - the guard is put down once its double-click is over.
            click(q.label, 1);
            click(q.label, 2);

            for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            assertEquals(model.getFeedbackState(sensor), before, "a later double-click on TunnelPre, with no question"
                + " waiting, flipped it once and not back - the answering click's guard was never put down, and the"
                + " sensor is left showing a change nobody made (TDU2-C1)");
        }
        finally
        {
            model.setFeedbackState(sensor, before);

            takeDownDialogs();

            asker.shutdownNow();
        }
    }

    /**
     * The paste door reads every part of its landing before the question is asked (TDU-B2).
     *
     * Since FR-100 the question waits on the diagram with the window live, and a second Control+V in that wait runs the
     * paste door again and writes the same fields for its own train - so the first train's road, side and facing were
     * read back from the second's.  Read before the question, they are this train's.  Asked of the source: the wait is a
     * secondary loop on the event thread, which a test cannot hold open while it pastes.
     *
     * MUTATION: read any of them after the question again, and this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testThePasteReadsItsLandingBeforeTheQuestionIsAsked() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");

        int start = source.indexOf("private void rememberPlacement(");

        assertTrue(start > 0, "precondition: TrainControlUI has no rememberPlacement");

        int end = source.indexOf("\n    /**", start);

        String body = source.substring(start, end < 0 ? source.length() : end)
            .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");

        int asked = body.indexOf("askAfterPlacement(");

        assertTrue(asked > 0, "precondition: the paste door no longer asks the tail question");

        String after = body.substring(asked);

        for (String field : new String[] {"tailAtTheLanding", "roadBeforeTheLanding", "squareBeforeTheLanding",
            "sideBeforeTheLanding", "facingAtTheLanding", "facingChosenAtTheLanding"})
        {
            assertFalse(after.contains(field), "the paste door reads " + field + " after the tail question, which a second"
                + " paste made while the question waits overwrites (TDU-B2)");
        }
    }

    /**
     * Asked on the event thread, as every placement door asks it, the question waits in a secondary loop and a click
     * answers it (TDU-C2).
     *
     * The other claims here ask off the event thread, which is the latch branch; every real door is on the event thread.
     *
     * MUTATION: leave the secondary loop without exiting it on the answer, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAQuestionAskedOnTheEventThreadIsAnsweredByAClick() throws Exception
    {
        TailCrossedPrompt.answerForTests(null);

        final TunnelQuestion q = tunnelQuestion();

        final java.util.concurrent.atomic.AtomicReference<TailCrossedPrompt.Answer> got =
            new java.util.concurrent.atomic.AtomicReference<>();

        try
        {
            javax.swing.SwingUtilities.invokeLater(() -> got.set(TailCrossedPrompt.askAfterPlacement(q.layout, q.tunnel,
                "N", 5, "FR-100 train", ui, null)));

            Thread.sleep(2000);

            click(q.label, 1);

            long giveUp = System.currentTimeMillis() + 10000;

            while (got.get() == null && System.currentTimeMillis() < giveUp) Thread.sleep(50);

            assertNotNull(got.get(), "asked on the event thread, the tail question was not answered by a click on TunnelPre"
                + " - the secondary loop did not hand the answer back");

            assertTrue(got.get().wasAnswered(), "the click on TunnelPre was taken as no answer");

            assertEquals(got.get().getRoad(), q.tunnelPre.getRoad(), "the click on TunnelPre recorded another road");
        }
        finally
        {
            takeDownDialogs();
        }
    }

    /**
     * With an editor open the question is the list, not the main window's diagram (TDU-C1).
     *
     * From the autonomy editor's Place door the question was put on the main window's diagram - lit on the editor's
     * squares too, which do not take the click - with its small window at the top of a main window the editor covers.
     *
     * MUTATION: put the question on the diagram with an editor open, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testWithAnEditorOpenTheQuestionIsTheList() throws Exception
    {
        TailCrossedPrompt.answerForTests(null);

        final TunnelQuestion q = tunnelQuestion();

        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        java.lang.reflect.Field open = TrainControlUI.class.getDeclaredField("openEditor");

        open.setAccessible(true);

        java.lang.reflect.Field armed = TailCrossedPrompt.class.getDeclaredField("armed");

        armed.setAccessible(true);

        ExecutorService asker = Executors.newSingleThreadExecutor();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                editor[0] = new org.traincontrol.gui.LayoutEditor(model.getLayout("1 - Main"), 30, ui, 0);
                editor[0].pack();
            });

            open.set(ui, editor[0]);

            assertTrue(ui.isLayoutEditorOpen(), "precondition: the window does not know the editor is open");

            asker.submit(() -> TailCrossedPrompt.askAfterPlacement(q.layout, q.tunnel, "N", 5, "FR-100 train", ui, null));

            Thread.sleep(2000);

            assertNull(armed.get(null), "with an editor open the tail question was put on the main window's diagram, which"
                + " the editor's squares do not answer (TDU-C1)");
        }
        finally
        {
            takeDownDialogs();

            // A question left waiting on the diagram is answered Not known, so nothing holds the next class.
            if (armed.get(null) != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> { });

                TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);

                java.awt.Window[] windows = java.awt.Window.getWindows();

                for (java.awt.Window window : windows)
                {
                    if (window instanceof javax.swing.JDialog && window.isShowing()) window.dispose();
                }

                TailCrossedPrompt.answerForTests(null);
            }

            open.set(ui, null);

            if (editor[0] != null) javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());

            asker.shutdownNow();
        }
    }

    /**
     * A tail question answered after the square has changed writes nothing onto whatever stands there by then (TDU2-A1).
     *
     * Since FR-100 the question waits on the diagram with the window live - Start, the hand doors, another placement all
     * still work - and the door that asked writes its answer when it comes back.  Answered after a run has brought another
     * train onto the same copy, Cancel wrote the first train's (empty) road over the second train's driven one, on the
     * running layout: the second train's tail then stopped at the switch, and a third could be routed into it.  Here the
     * paste door asks for train A at Tunnel; while it waits, train B is put on the same copy with the road it drove in by,
     * as a run's arrival puts it; then Cancel.
     *
     * MUTATION: write the answer without asking whether the placement still stands, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testALateAnswerDoesNotWriteOverAnotherTrain() throws Exception
    {
        TailCrossedPrompt.answerForTests(null);

        final TunnelQuestion q = tunnelQuestion();

        org.traincontrol.base.Locomotive first = model.newMM2Locomotive("TDU2-A1 first", 2303);
        org.traincontrol.base.Locomotive second = model.newMM2Locomotive("TDU2-A1 second", 2304);

        final java.util.concurrent.atomic.AtomicBoolean returned = new java.util.concurrent.atomic.AtomicBoolean(false);

        try
        {
            first.setTrainLength(5);
            second.setTrainLength(5);

            // TRAIN A PUT DOWN AT TUNNEL, arriving from the north, as the paste door leaves it before it asks.
            assertTrue(q.layout.moveLocomotive(first.getName(), q.tunnel.getName(), false), "precondition: the first train"
                + " could not be put at Tunnel");

            q.tunnel.setArrivedFrom("N");

            final TileKey tile = ui.getAutonomySession().getStationIndex().squareOf(q.tunnel.getName());

            java.lang.reflect.Field tailField = TrainControlUI.class.getDeclaredField("tailAtTheLanding");
            tailField.setAccessible(true);
            tailField.set(ui, "N");

            final java.lang.reflect.Method remember = TrainControlUI.class.getDeclaredMethod("rememberPlacement",
                Point.class, TileKey.class);
            remember.setAccessible(true);

            // THE PASTE DOOR, on the event thread as it runs: it asks, and the question waits on the diagram.
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                try
                {
                    remember.invoke(ui, q.tunnel, tile);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
                finally
                {
                    returned.set(true);
                }
            });

            Thread.sleep(2000);

            java.lang.reflect.Field armed = TailCrossedPrompt.class.getDeclaredField("armed");
            armed.setAccessible(true);

            assertNotNull(armed.get(null), "precondition: the paste door's tail question is not waiting on the diagram");

            // WHILE IT WAITS: train B on the same copy, with the road it drove in by - TunnelPre's.
            assertTrue(q.layout.moveLocomotive(second.getName(), q.tunnel.getName(), false), "precondition: the second"
                + " train could not be put at Tunnel");

            q.tunnel.setArrivedFrom("N");
            q.tunnel.setArrivedAlong(q.tunnelPre.getRoad());

            java.util.List<org.traincontrol.automation.Edge> driven = q.tunnel.getArrivedAlong();

            assertNotNull(driven, "precondition: the second train's road did not take");

            // CANCEL on the small window the question left up.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                    if (!org.traincontrol.util.I18n.t("autolayout.ui.askArrivalSideTitle").equals(
                        ((javax.swing.JDialog) window).getTitle())) continue;

                    javax.swing.JButton cancel = buttonNamed((java.awt.Container) window,
                        org.traincontrol.util.I18n.t("ui.cancel"));

                    if (cancel != null) cancel.doClick();
                }
            });

            long giveUp = System.currentTimeMillis() + 10000;

            while (!returned.get() && System.currentTimeMillis() < giveUp) Thread.sleep(50);

            assertTrue(returned.get(), "precondition: the paste door did not return after Cancel");

            assertEquals(q.tunnel.getArrivedAlong(), driven, "the first train's tail question, answered after a second"
                + " train came to Tunnel on the same copy, wrote over the second train's road - its tail then stops at the"
                + " switch and another train can be routed into it (TDU2-A1)");
        }
        finally
        {
            takeDownDialogs();

            q.tunnel.setLocomotive(null);
            q.tunnel.setArrivedFrom(null);
            q.tunnel.setArrivedAlong(null);

            model.deleteLoc("TDU2-A1 first");
            model.deleteLoc("TDU2-A1 second");
        }
    }

    /**
     * Every placement door asks whether its placement still stands between the question and its writes (TDU2-A1) - the
     * paste door is asked by the claim above; the right-click Place and the locomotive dialog are asked here, of their
     * source, because each needs a menu or a dialog to reach.
     *
     * MUTATION: write either door's answer without asking, and this fails naming it.
     *
     * @throws Exception from the files
     */
    @Test
    public void testEveryPlacementDoorAsksWhetherItStillStands() throws Exception
    {
        for (String file : new String[] {"src/org/traincontrol/gui/TrainControlUI.java",
            "src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "src/org/traincontrol/gui/GraphLocAssign.java"})
        {
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)),
                java.nio.charset.StandardCharsets.UTF_8);

            int asked = source.indexOf("askAfterPlacement(");

            assertTrue(asked > 0, "precondition: " + file + " no longer asks the tail question");

            int checked = source.indexOf("whereTheAnswerGoes(", asked);
            int writes = source.indexOf("setArrivedAlong(", asked);

            assertTrue(writes > asked, "precondition: " + file + " no longer writes the answer's road");

            assertTrue(checked > asked && checked < writes, file + " writes the tail question's answer without asking"
                + " whether its placement still stands - a late answer goes over the train now there (TDU2-A1)");
        }
    }

    /** The button with this text, anywhere in the container. */
    private static javax.swing.JButton buttonNamed(java.awt.Container container, String text)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) child).getText()))
            {
                return (javax.swing.JButton) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.JButton found = buttonNamed((java.awt.Container) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * What a late answer finds, and what it leaves: the Tunnel copy the answer went to, and TunnelPre's road.
     */
    private static final class LateAnswer
    {
        Point tunnelNow;
        java.util.List<org.traincontrol.automation.Edge> tunnelPreRoad;
    }

    /**
     * The paste door asks for a five-unit train at Tunnel from the north; `meanwhile` runs while the question waits;
     * then TunnelPre is clicked - read again from the diagram, since a rebuild may have drawn it anew - or the small
     * window is cancelled; and the door returns.
     *
     * @param train the train pasted
     * @param meanwhile what happens in the wait
     * @param click true to click TunnelPre, false to cancel
     * @return what the answer found
     * @throws Exception from the event thread
     */
    private LateAnswer pasteAndAnswerLate(org.traincontrol.base.Locomotive train, java.util.concurrent.Callable<Void> meanwhile,
        boolean click) throws Exception
    {
        TailCrossedPrompt.answerForTests(null);

        final TunnelQuestion q = tunnelQuestion();

        LateAnswer out = new LateAnswer();

        out.tunnelPreRoad = q.tunnelPre.getRoad();

        train.setTrainLength(5);

        assertTrue(q.layout.moveLocomotive(train.getName(), q.tunnel.getName(), false), "precondition: the train could"
            + " not be put at Tunnel");

        q.tunnel.setArrivedFrom("N");

        final TileKey tile = ui.getAutonomySession().getStationIndex().squareOf(q.tunnel.getName());

        java.lang.reflect.Field tailField = TrainControlUI.class.getDeclaredField("tailAtTheLanding");
        tailField.setAccessible(true);
        tailField.set(ui, "N");

        final java.lang.reflect.Method remember = TrainControlUI.class.getDeclaredMethod("rememberPlacement",
            Point.class, TileKey.class);
        remember.setAccessible(true);

        final java.util.concurrent.atomic.AtomicBoolean returned = new java.util.concurrent.atomic.AtomicBoolean(false);

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try
            {
                remember.invoke(ui, q.tunnel, tile);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
            finally
            {
                returned.set(true);
            }
        });

        Thread.sleep(2000);

        java.lang.reflect.Field armed = TailCrossedPrompt.class.getDeclaredField("armed");
        armed.setAccessible(true);

        assertNotNull(armed.get(null), "precondition: the paste door's tail question is not waiting on the diagram");

        // WHATEVER HAPPENS IN THE WAIT, the question is put down and the door let go, so a failure here cannot leave it
        // waiting for the next claim's click.
        try
        {
            meanwhile.call();
        }
        catch (Throwable failed)
        {
            cancelTheQuestion();

            long giveUp = System.currentTimeMillis() + 10000;

            while (!returned.get() && System.currentTimeMillis() < giveUp) Thread.sleep(50);

            throw failed;
        }

        if (click)
        {
            TileKey square = ui.getAutonomySession().getStationIndex().squareOf(q.tunnelPre.getFarthest().getName());

            java.util.Set<LayoutLabel> labels = ui.getDiagramTileRegistry().labelsFor(square);

            assertFalse(labels.isEmpty(), "precondition: TunnelPre is not drawn after the wait");

            click(labels.iterator().next(), 1);
        }
        else
        {
            cancelTheQuestion();
        }

        long giveUp = System.currentTimeMillis() + 10000;

        while (!returned.get() && System.currentTimeMillis() < giveUp) Thread.sleep(50);

        assertTrue(returned.get(), "precondition: the paste door did not return after the answer");

        out.tunnelNow = model.getAutoLayout().getPoint(q.tunnel.getName());

        return out;
    }

    /** Cancel on the small window a waiting tail question leaves up. */
    private static void cancelTheQuestion() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                if (!org.traincontrol.util.I18n.t("autolayout.ui.askArrivalSideTitle").equals(
                    ((javax.swing.JDialog) window).getTitle())) continue;

                javax.swing.JButton cancel = buttonNamed((java.awt.Container) window,
                    org.traincontrol.util.I18n.t("ui.cancel"));

                if (cancel != null) cancel.doClick();
            }
        });
    }

    /** The names a road is written by, for comparing roads of two builds of the railway. */
    private static String named(java.util.List<org.traincontrol.automation.Edge> road)
    {
        return org.traincontrol.automation.Layout.namesOfRoad(road);
    }

    /** Takes a train off every copy and out of the setup, so the next claim starts from an empty Tunnel. */
    private static void clearTunnel(String... names) throws Exception
    {
        takeDownDialogs();

        Point tunnel = model.getAutoLayout().getPoint("Tunnel (southbound)");

        if (tunnel != null)
        {
            tunnel.setLocomotive(null);
            tunnel.setArrivedFrom(null);
            tunnel.setArrivedAlong(null);
        }

        for (String name : names) model.deleteLoc(name);
    }

    /**
     * An answer given after the running railway was rebuilt reaches the railway (TDU3-B1).
     *
     * A rebuild in the wait - any setup change from the diagram, a page left out - builds new copies of every square and
     * puts each train back on its new one.  The door asked the copy it had placed the train on, which the rebuild leaves
     * as it was, so its check passed and the answer went to that copy and to the setup: the railway's own copy of Tunnel
     * never got the road, and the train's tail stopped at the switch with another train routable into it.
     *
     * MUTATION: ask the copy the door placed the train on rather than the running railway's, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAnAnswerAfterARebuildReachesTheRailway() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDU3-B1 train", 2305);

        try
        {
            final Point before = model.getAutoLayout().getPoint("Tunnel (southbound)");

            LateAnswer late = pasteAndAnswerLate(train, () ->
            {
                // WHAT A SETUP CHANGE FROM THE DIAGRAM POSTS.
                javax.swing.SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

                for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

                Point now = model.getAutoLayout().getPoint("Tunnel (southbound)");

                assertTrue(now != null && now != before, "precondition: the rebuild did not build a new Tunnel copy");

                assertTrue(now.getCurrentLocomotive() == train, "precondition: the rebuild did not put the train back"
                    + " on Tunnel");

                return null;
            }, true);

            assertEquals(named(late.tunnelNow.getArrivedAlong()), named(late.tunnelPreRoad), "TunnelPre was clicked after"
                + " the railway was rebuilt, and the railway's Tunnel copy did not get the road - the train's tail stops at"
                + " the switch and another train can be routed into it (TDU3-B1)");
        }
        finally
        {
            clearTunnel("TDU3-B1 train");
        }
    }

    /**
     * The same train on the copy, from the same side, with a road it did not have when the question was asked - the
     * road a run brings it back by: a late Cancel leaves that road (TDD3-C7, the road condition alone).
     *
     * MUTATION: drop the road from `placementStillStands`, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testALateAnswerLeavesTheRoadARunBroughtTheSameTrainBy() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDD3-C7 road", 2306);

        try
        {
            final String[] driven = new String[1];

            LateAnswer late = pasteAndAnswerLate(train, () ->
            {
                Point tunnel = model.getAutoLayout().getPoint("Tunnel (southbound)");

                // THE ROAD ALONE: the same train, the same side, and the road a run drove it back in by.
                tunnel.setArrivedAlong(tunnelQuestion().tunnelPre.getRoad());

                driven[0] = named(tunnel.getArrivedAlong());

                return null;
            }, false);

            assertEquals(named(late.tunnelNow.getArrivedAlong()), driven[0], "a late Cancel wrote over the road a run"
                + " brought the same train back by - its tail stops at the switch (TDD3-C7)");
        }
        finally
        {
            clearTunnel("TDD3-C7 road");
        }
    }

    /**
     * Another train put on the copy by hand, with no road, the same side: a late click on TunnelPre does not give it
     * the first train's road (TDD3-C7, the train condition alone).
     *
     * MUTATION: drop the train from `placementStillStands`, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testALateAnswerDoesNotGiveAnotherTrainItsRoad() throws Exception
    {
        org.traincontrol.base.Locomotive first = model.newMM2Locomotive("TDD3-C7 first", 2307);
        org.traincontrol.base.Locomotive second = model.newMM2Locomotive("TDD3-C7 second", 2308);

        try
        {
            second.setTrainLength(5);

            LateAnswer late = pasteAndAnswerLate(first, () ->
            {
                Layout layout = model.getAutoLayout();
                Point tunnel = layout.getPoint("Tunnel (southbound)");

                assertTrue(layout.moveLocomotive(second.getName(), tunnel.getName(), false), "precondition: the second"
                    + " train could not be put at Tunnel");

                tunnel.setArrivedFrom("N");
                tunnel.setArrivedAlong(null);

                return null;
            }, true);

            assertNull(late.tunnelNow.getArrivedAlong(), "the first train's late answer gave the second train, put on the"
                + " copy by hand, the first train's road (TDD3-C7)");
        }
        finally
        {
            clearTunnel("TDD3-C7 first", "TDD3-C7 second");
        }
    }

    /**
     * The same train, turned in the wait: a late click on TunnelPre - a road for a train that came in from the north -
     * is not written for a train now recorded from the south (TDD3-C7, the side condition alone).
     *
     * MUTATION: drop the side from `placementStillStands`, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testALateAnswerIsNotWrittenForTheOtherSide() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDD3-C7 side", 2309);

        try
        {
            LateAnswer late = pasteAndAnswerLate(train, () ->
            {
                model.getAutoLayout().getPoint("Tunnel (southbound)").setArrivedFrom("S");

                return null;
            }, true);

            assertNull(late.tunnelNow.getArrivedAlong(), "a late click on TunnelPre was written for a train recorded"
                + " since as having come in from the other side (TDD3-C7)");
        }
        finally
        {
            clearTunnel("TDD3-C7 side");
        }
    }
}
