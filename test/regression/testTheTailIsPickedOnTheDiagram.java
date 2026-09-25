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

            // THE QUESTION IS ON THE DIAGRAM: TunnelPre lit (MT-574).
            assertTrue(q.label.isFlashOutstanding(), "precondition: TunnelPre is not lit while the tail question waits,"
                + " so the lights going out below would say nothing");

            click(q.label, 1);
            click(q.label, 2);

            TailCrossedPrompt.Answer answer = asked.get(10, TimeUnit.SECONDS);

            assertTrue(answer.wasAnswered(), "precondition: the click on TunnelPre did not answer the question");

            // THE TAIL BACK TO TUNNELPRE (MT-574): the road the placement draws the tail along.
            assertEquals(answer.getRoad(), q.tunnelPre.getRoad(), "the double-click on TunnelPre recorded another road, so"
                + " the tail is drawn somewhere else (MT-574)");

            for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            // AND THE LIGHTS GO OUT (MT-574).
            assertFalse(q.label.isFlashOutstanding(), "TunnelPre is still lit after the double-click answered the tail"
                + " question (MT-574)");

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

    /**
     * Another configuration loaded in the wait, holding the same train on the same copy: the answer follows the train -
     * into the configuration loaded now and onto the railway running (Adam, 2026-09-24, TDD5-C1: *"Follow the train."*).
     *
     * TDU4-C1 took the other way, dropping it, and the tail the operator had just given was then missing from the
     * railway in front of him: the running copy held the train with no road, and the track beyond the switch, where he
     * had said the tail lies, was free to route another train over.
     *
     * MUTATION: ask whether the configuration is the one the question was asked in, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAnAnswerFollowsTheTrainIntoAnotherConfiguration() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDU4-C1 train", 2312);

        final AutonomySession session = ui.getAutonomySession();
        final String was = session.getStore().getActiveConfiguration();
        final String copy = "TDU4-C1 copy";

        try
        {
            final TileKey[] tile = new TileKey[1];

            LateAnswer late = pasteAndAnswerLate(train, () ->
            {
                // A COPY OF THE CONFIGURATION AS IT STANDS - the train at Tunnel, from the north, no road - and loaded.
                session.getStore().createConfiguration(copy, was);

                javax.swing.SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(copy, false));

                for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

                assertEquals(ui.getAutonomySession().getStore().getActiveConfiguration(), copy, "precondition: the copy"
                    + " was not loaded");

                Point now = model.getAutoLayout().getPoint("Tunnel (southbound)");

                assertTrue(now != null && now.getCurrentLocomotive() == train && "N".equals(now.getArrivedFrom())
                    && now.getArrivedAlong() == null, "precondition: the copy does not hold the same train on Tunnel from"
                    + " the north with no road");

                tile[0] = ui.getAutonomySession().getStationIndex().squareOf(now.getName());

                return null;
            }, true);

            assertNotNull(ui.getAutonomySession().getArrivedAlong(tile[0]), "the answer was dropped when another"
                + " configuration was loaded in the wait, though the train stands there as it did (TDD5-C1: \"Follow the"
                + " train.\")");

            assertEquals(named(late.tunnelNow.getArrivedAlong()), named(late.tunnelPreRoad), "the running railway's copy"
                + " does not hold the road the operator gave, so the track beyond the switch is free under the tail"
                + " (TDD5-C1)");
        }
        finally
        {
            clearTunnel("TDU4-C1 train");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(was, false));

            try
            {
                ui.getAutonomySession().getStore().deleteConfiguration(copy);
            }
            catch (Exception gone)
            {
            }
        }
    }

    /**
     * The setup replaced in the wait - what closing the track-diagram editor does: the answer is not written, the new
     * railway's copy keeps no road, and the old setup is not saved over the new one's file (TDU4-C4, TDU4-C2).
     *
     * MUTATION: write where the session is not the window's any more, or save it, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAnAnswerAfterTheSetupIsReplacedIsNotWritten() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDU4-C2 train", 2313);

        final AutonomySession before = ui.getAutonomySession();
        final String active = before.getStore().getActiveConfiguration();
        final java.io.File[] file = new java.io.File[1];
        final long[] saved = new long[1];

        try
        {
            LateAnswer late = pasteAndAnswerLate(train, () ->
            {
                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    ui.resetAutonomySession();
                    ui.getAutonomyViewerPanel().load(active, false);
                });

                for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

                assertTrue(ui.getAutonomySession() != before, "precondition: the setup was not replaced");

                java.lang.reflect.Method fileOf = before.getStore().getClass().getDeclaredMethod("configurationFile",
                    String.class);

                fileOf.setAccessible(true);

                file[0] = (java.io.File) fileOf.invoke(before.getStore(), active);
                saved[0] = file[0].lastModified();

                Thread.sleep(1100);

                return null;
            }, true);

            assertNull(late.tunnelNow.getArrivedAlong(), "an answer given after the setup was replaced was written to the"
                + " new railway's copy, through a setup nothing reads (TDU4-C4)");

            assertEquals(file[0].lastModified(), saved[0], "the setup replaced in the wait was saved over the new one's"
                + " file, with the reconciling save the reset avoids (TDU4-C2)");
        }
        finally
        {
            clearTunnel("TDU4-C2 train");
        }
    }

    /**
     * The facing is written before the question, so an answer dropped in the wait does not take it with it (TDU3-C1,
     * TDU4-C4).
     *
     * MUTATION: write the facing with the road, after the question, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheFacingIsWrittenBeforeTheQuestion() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDU4-C4 facing", 2314);

        try
        {
            final TileKey tile = ui.getAutonomySession().getStationIndex().squareOf("Tunnel (southbound)");

            ui.getAutonomySession().setFacing(tile, null);

            // A HEADING CHOSEN AT THE LANDING, as the paste door's own question leaves it.
            facingField().set(ui, org.traincontrol.automationui.TilePorts.Side.S);

            pasteAndAnswerLate(train, () ->
            {
                // TAKEN OFF IN THE WAIT: the answer is dropped.
                model.getAutoLayout().moveLocomotive(null, "Tunnel (southbound)", true);

                return null;
            }, false);

            assertNotNull(ui.getAutonomySession().getFacing(tile), "a paste whose tail answer was dropped recorded no"
                + " facing - it waited for the answer, which only the road should (TDU3-C1)");
        }
        finally
        {
            facingField().set(ui, null);

            clearTunnel("TDU4-C4 facing");
        }
    }

    private static java.lang.reflect.Field facingField() throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("facingChosenAtTheLanding");

        field.setAccessible(true);

        return field;
    }

    /**
     * A dropped ANSWER is logged, naming the train and the square; a Cancel of a question made stale is not (TDU4-C3).
     *
     * MUTATION: log every dropped reply, or none, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testOnlyADroppedAnswerIsLogged() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDU4-C3 log", 2315);

        try
        {
            String dropped = org.traincontrol.util.I18n.f("autolayout.ui.logTailAnswerDropped", train.getName(), "Tunnel");

            String first = dropped.substring(0, Math.min(25, dropped.length()));

            // CANCELLED: nothing to say.
            pasteAndAnswerLate(train, () ->
            {
                model.getAutoLayout().moveLocomotive(null, "Tunnel (southbound)", true);

                return null;
            }, false);

            assertFalse(logged().contains(dropped), "a Cancel of a question made stale in the wait was logged as an"
                + " answer not recorded (TDU4-C3)");

            clearTunnel();

            // ANSWERED: said, naming the square as the diagram does.
            pasteAndAnswerLate(train, () ->
            {
                model.getAutoLayout().moveLocomotive(null, "Tunnel (southbound)", true);

                return null;
            }, true);

            assertTrue(logged().contains(dropped), "an answer dropped because the train was taken off in the wait was not"
                + " logged, naming the train and Tunnel (TDU4-C3): " + logged().substring(0, Math.min(400,
                    logged().length())) + " - looked for: " + first);
        }
        finally
        {
            clearTunnel("TDU4-C3 log");
        }
    }

    /** What the model has logged since the listener below was put on - every line the operator's log shows. */
    private static final StringBuilder LOGGED = new StringBuilder();

    static
    {
        java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).addHandler(
            new java.util.logging.Handler()
            {
                @Override
                public void publish(java.util.logging.LogRecord record)
                {
                    synchronized (LOGGED)
                    {
                        LOGGED.append(record.getMessage()).append('\n');
                    }
                }

                @Override
                public void flush()
                {
                }

                @Override
                public void close()
                {
                }
            });
    }

    /** The model's log, as the operator reads it. */
    private static String logged() throws Exception
    {
        for (int turn = 0; turn < 3; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

        synchronized (LOGGED)
        {
            return LOGGED.toString();
        }
    }

    /**
     * The right-click and locomotive-dialog doors ask the railway running when the answer comes back, not the one they
     * held before the question (TDU4-C4) - read, as the pin above is, because each needs a menu or a dialog to reach.
     *
     * MUTATION: pass either door the railway it held before the question, and this fails naming it.
     *
     * @throws Exception from the files
     */
    @Test
    public void testEveryDoorAsksTheRailwayRunningAtTheAnswer() throws Exception
    {
        for (String file : new String[] {"src/org/traincontrol/gui/TrainControlUI.java",
            "src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "src/org/traincontrol/gui/GraphLocAssign.java"})
        {
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)),
                java.nio.charset.StandardCharsets.UTF_8);

            int asked = source.indexOf("whereTheAnswerGoes(");

            assertTrue(asked > 0, "precondition: " + file + " no longer asks where the answer goes");

            String first = source.substring(asked, source.indexOf(",", asked));

            assertTrue(first.contains("runningNow("), file + " asks where the answer goes of a railway other than the one"
                + " running when it comes back (TDU4-C4): " + first);
        }
    }

    /**
     * The configuration renamed in the wait - the railway not rebuilt - keeps the answer (TDU5-C1): the same setup,
     * under a new name.  Asked only by the configuration's name, the check dropped the answer from the railway and from
     * the setup, and the log gave another configuration as the reason.
     *
     * MUTATION: ask for anything beyond the session - the configuration's name, the railway running - and this fails
     * (TDD5-C1: the answer follows the train).
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARenameInTheWaitKeepsTheAnswer() throws Exception
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive("TDU5-C1 train", 2316);

        final AutonomySession session = ui.getAutonomySession();
        final String was = session.getStore().getActiveConfiguration();
        final String renamed = was + " TDU5-C1";

        try
        {
            LateAnswer late = pasteAndAnswerLate(train, () ->
            {
                session.getStore().renameConfiguration(was, renamed);

                assertEquals(session.getStore().getActiveConfiguration(), renamed, "precondition: the rename did not"
                    + " take");

                return null;
            }, true);

            assertEquals(named(late.tunnelNow.getArrivedAlong()), named(late.tunnelPreRoad), "the answer was dropped after"
                + " the configuration was only renamed in the wait - the railway was not rebuilt, and the train's tail now"
                + " stops at the switch (TDU5-C1)");
        }
        finally
        {
            clearTunnel("TDU5-C1 train");

            try
            {
                if (renamed.equals(session.getStore().getActiveConfiguration()))
                {
                    session.getStore().renameConfiguration(renamed, was);
                }
            }
            catch (Exception back)
            {
            }
        }
    }

    /**
     * The right-click and locomotive-dialog doors keep every late-answer rule the paste door is claimed for above (TDU5-C4):
     * the facing written before the question, the save skipped for a setup let go, only an answer logged, and the square
     * named as the diagram names it - read, as the pins above are, because each door needs a menu or a dialog.
     *
     * And that each writes only where the answer goes somewhere (TDU2-A1).
     *
     * MUTATION: undo any of the five at either door, and this fails naming it.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheOtherDoorsKeepTheLateAnswerRules() throws Exception
    {
        String[][] doors = {
            {"src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "session.setFacing(station, facing)",
                "if (facing != null && session != null && setupStands)"},
            {"src/org/traincontrol/gui/GraphLocAssign.java", "session.setFacing(tile,", "if (!setupStands) return;"}};

        for (String[] door : doors)
        {
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(door[0])),
                java.nio.charset.StandardCharsets.UTF_8);

            int asked = source.indexOf("askAfterPlacement(");

            assertTrue(asked > 0, "precondition: " + door[0] + " no longer asks the tail question");

            int facing = source.indexOf(door[1]);

            assertTrue(facing > 0 && facing < asked, door[0] + " writes the facing after the tail question, where a"
                + " dropped answer takes it with it (TDU3-C1)");

            // THE WRITE WAITS ON WHERE THE ANSWER GOES (TDU2-A1): nothing is written where it goes nowhere.
            int where = source.indexOf("whereTheAnswerGoes(", asked);
            int write = source.indexOf("setArrivedAlong(", where);

            String between = where > 0 && write > where ? source.substring(where, write) : "";

            assertTrue(between.contains("landing != null)") || between.contains("answersTo != null)"), door[0]
                + " writes the tail question's answer without asking where it goes (TDU2-A1)");

            int noted = source.indexOf("noteADroppedAnswer(", asked);
            int answered = source.lastIndexOf("wasAnswered()", noted);

            assertTrue(noted > 0 && answered > asked, door[0] + " logs a dropped reply that carried no answer (TDU4-C3)");

            String call = source.substring(noted, source.indexOf(";", noted));

            assertTrue(call.contains("baseNameOf("), door[0] + " names the copy, not the square, when it logs a dropped"
                + " answer (TDU4-C3): " + call);

            int save = source.indexOf("session.save()", asked);
            int guard = source.lastIndexOf(door[2], save);

            assertTrue(save > 0 && guard > asked, door[0] + " saves a setup the window let go in the wait (TDU4-C2)");

            // AND AN EDITOR OPENED IN THE WAIT LETS THE SETUP GO TOO (RLU-C9, RLU2-C4): the paste door is claimed above.
            int stands = source.indexOf("setupStands =", asked);

            String rule = stands > 0 ? source.substring(stands, source.indexOf(";", stands)) : "";

            assertTrue(rule.contains("anEditorOpenedInTheWait()"), door[0] + " writes a late answer into a setup that an"
                + " editor opened in the wait holds a copy of (RLU-C9): " + rule);
        }
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

    /**
     * A tail answer given after the placement changed is written where the train now stands, or not at all - driven by
     * the gestures MT-576's steps make (Adam, 2026-09-25: automated tests supersede the MTs they answer).
     *
     * His 75 407 DB, given a length of 5, pasted at Tunnel from the north with Control+X and Control+V through the
     * diagram's own key door, the question left waiting on the diagram.  (a) While it waits, a setting on another station
     * is changed from the diagram's right-click - which rebuilds the railway - and then TunnelPre is clicked: the new
     * railway's Tunnel gets TunnelPre's road, made of its own rails.  The setting is put back.  (b) Pasted again; while it
     * waits the train is taken off Tunnel with its right-click Remove, and TunnelPre is clicked: nothing is written behind
     * Tunnel, and the log says the tail was not recorded because Tunnel changed while the question waited.
     *
     * `testAnAnswerAfterARebuildReachesTheRailway` and `testOnlyADroppedAnswerIsLogged` hold the same two rules with the
     * wait's events made by the calls the gestures make; this makes them by the gestures.
     *
     * MUTATION: write the answer to the copy the paste placed the train on, or write it after the train was taken off, and
     * this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheLateAnswersOfMT576ByTheirGestures() throws Exception
    {
        final org.traincontrol.base.Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "precondition: this data has no 75 407 DB, the train the steps use");

        final Integer lengthWas = train.getTrainLength();

        AutonomySession session = ui.getAutonomySession();

        TileKey tunnel = session.getStationIndex().squareOf("Tunnel (southbound)");
        TileKey other = squareNamed(session, "BottomMainB");

        assertNotNull(tunnel, "precondition: no Tunnel square");
        assertNotNull(other, "precondition: no BottomMainB square");

        try
        {
            // STEP 1: a length of 5, standing on BottomMainB, then cut and pasted at Tunnel from the north.
            train.setTrainLength(5);

            standOn(train, other);

            final java.util.concurrent.atomic.AtomicBoolean pasted = pasteAtTunnelFromTheNorth(train, other, tunnel);

            // (a) STEP 2: a setting on another station, from the diagram's right-click - BottomMainB's "Can Be Chosen in
            // Full Autonomy" - which rebuilds the railway.
            Point before = model.getAutoLayout().getPoint("Tunnel (southbound)");

            flipAutoDestination(other);

            Point rebuilt = null;

            for (long end = System.currentTimeMillis() + 20000; System.currentTimeMillis() < end; )
            {
                rebuilt = model.getAutoLayout().getPoint("Tunnel (southbound)");

                if (rebuilt != null && rebuilt != before) break;

                Thread.sleep(100);
            }

            assertTrue(rebuilt != null && rebuilt != before, "precondition: changing BottomMainB's setting from the diagram"
                + " did not rebuild the railway, so the answer is not late in the way the steps make it");

            assertTrue(rebuilt.getCurrentLocomotive() == train, "precondition: the rebuild did not put 75 407 DB back on"
                + " Tunnel");

            // STEP 3: TunnelPre clicked.
            clickTunnelPre(session);

            awaitAnswered(pasted);

            Point tunnelNow = model.getAutoLayout().getPoint("Tunnel (southbound)");

            TailCrossedPrompt.Choice tunnelPre = null;

            for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(model.getAutoLayout(), tunnelNow, "N", 5, null))
            {
                if (choice.getFarthest().getName().startsWith("TunnelPre")) tunnelPre = choice;
            }

            assertNotNull(tunnelPre, "precondition: the rebuilt railway offers a five-unit train at Tunnel no TunnelPre");

            assertEquals(named(tunnelNow.getArrivedAlong()), named(tunnelPre.getRoad()), "TunnelPre was clicked after a"
                + " setting was changed from the diagram, and the railway's Tunnel did not get the road to TunnelPre - the"
                + " grey behind Tunnel stops short (MT-576 step 3)");

            for (org.traincontrol.automation.Edge edge : tunnelNow.getArrivedAlong())
            {
                assertTrue(model.getAutoLayout().getEdge(edge.getName()) == edge, "the road written after the rebuild is"
                    + " made of the old railway's rails (MT-576 step 3): " + edge.getName());
            }

            // STEP 4: the setting put back.
            flipAutoDestination(other);

            for (int turn = 0; turn < 6; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            // (b) STEP 5: pasted at Tunnel from the north again, the question waiting.
            final java.util.concurrent.atomic.AtomicBoolean again = pasteAtTunnelFromTheNorth(train, tunnel, tunnel);

            int from = logged().length();

            // STEP 6: taken off Tunnel with its right-click Remove.
            removeFromTheDiagram(tunnel, train);

            // STEP 7: TunnelPre clicked.
            clickTunnelPre(session);

            awaitAnswered(again);

            Point after = model.getAutoLayout().getPoint("Tunnel (southbound)");

            assertTrue(after.getCurrentLocomotive() == null, "precondition: 75 407 DB is still on Tunnel after Remove");

            List<org.traincontrol.automation.Edge> road = after.getArrivedAlong();

            assertTrue(road == null || road.isEmpty(), "an answer given after 75 407 DB was taken off Tunnel was written"
                + " behind Tunnel anyway (MT-576 step 7): " + (road == null ? "none" : named(road)));

            String dropped = org.traincontrol.util.I18n.f("autolayout.ui.logTailAnswerDropped", train.getName(), "Tunnel");

            assertTrue(logged().substring(from).contains(dropped), "the log does not say where the tail of 75 407 DB lies"
                + " was not recorded because Tunnel changed while the question waited (MT-576 step 7)");
        }
        finally
        {
            cancelTheQuestion();

            TailCrossedPrompt.answerForTests(null);
            org.traincontrol.gui.FacingPrompt.answerForTests(null);
            org.traincontrol.gui.ArrivalSidePrompt.answerForTests(null);

            takeDownDialogs();

            train.setTrainLength(lengthWas);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                Point at = model.getAutoLayout().getLocomotiveLocation(train);

                if (at != null) model.getAutoLayout().moveLocomotive(null, at.getName(), false);
            });

            clearTunnel();
        }
    }

    /**
     * An answer given after an editor was opened in the wait is not written into the setup, and the log says why (RLU-C9).
     *
     * The editor holds the setup as it was when it opened, and its Cancel puts that back - so a road written into the
     * setup while it was open was taken back out by the Cancel, silently, and the rail the train lies on stopped being
     * held.  Every main-window door that writes the setup refuses while an editor is open; this one's late answer did
     * not ask.  His 75 407 DB, given a length of 5, pasted at Tunnel from the north with the question left waiting; the
     * autonomy editor opened as Autonomy > Edit Autonomy opens it; TunnelPre clicked on the main window.
     *
     * MUTATION: write the late answer whatever opened in the wait, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnAnswerAfterAnEditorOpenedIsNotWritten() throws Exception
    {
        final org.traincontrol.base.Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "precondition: this data has no 75 407 DB, the train the steps use");

        final Integer lengthWas = train.getTrainLength();

        AutonomySession session = ui.getAutonomySession();

        TileKey tunnel = session.getStationIndex().squareOf("Tunnel (southbound)");
        TileKey other = squareNamed(session, "BottomMainB");

        assertNotNull(tunnel, "precondition: no Tunnel square");
        assertNotNull(other, "precondition: no BottomMainB square");

        java.awt.Window editor = null;

        try
        {
            train.setTrainLength(5);

            standOn(train, other);

            final java.util.concurrent.atomic.AtomicBoolean pasted = pasteAtTunnelFromTheNorth(train, other, tunnel);

            String roadBefore = ui.getAutonomySession().getArrivedAlong(tunnel);

            // THE EDITOR, OPENED WHILE THE QUESTION WAITS.
            javax.swing.SwingUtilities.invokeLater(() -> ui.openAutonomyEditorOnPage(tunnel.getPage()));

            for (long end = System.currentTimeMillis() + 60000; editor == null && System.currentTimeMillis() < end; )
            {
                Thread.sleep(100);

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window.isShowing() && "LayoutEditor".equals(window.getClass().getSimpleName())) editor = window;
                }
            }

            assertNotNull(editor, "precondition: the autonomy editor did not open while the question waited");

            for (int turn = 0; turn < 6; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            int from = logged().length();

            // ON THE MAIN WINDOW'S DIAGRAM: the editor's own squares are lit too, and do not take the click.
            TileKey pre = null;

            for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(model.getAutoLayout(),
                model.getAutoLayout().getPoint("Tunnel (southbound)"), "N", 5, null))
            {
                if (choice.getFarthest().getName().startsWith("TunnelPre")) pre = session.getStationIndex().squareOf(
                    choice.getFarthest());
            }

            assertNotNull(pre, "precondition: no TunnelPre square to click");

            LayoutLabel onTheMainWindow = null;

            for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(pre))
            {
                if (javax.swing.SwingUtilities.getWindowAncestor(label) == ui) onTheMainWindow = label;
            }

            assertNotNull(onTheMainWindow, "precondition: TunnelPre is not drawn on the main window's diagram");

            click(onTheMainWindow, 1);

            awaitAnswered(pasted);

            assertEquals(ui.getAutonomySession().getArrivedAlong(tunnel), roadBefore, "TunnelPre, clicked after an editor"
                + " was opened in the wait, was written into the setup the editor holds a copy of - its Cancel would take it"
                + " back out (RLU-C9)");

            String dropped = org.traincontrol.util.I18n.f("autolayout.ui.logTailAnswerDroppedEditorOpened",
                train.getName(), "Tunnel");

            assertTrue(logged().substring(from).contains(dropped), "the log does not say the answer was not recorded"
                + " because an editor was opened while the question waited (RLU-C9): " + logged().substring(from));
        }
        finally
        {
            cancelTheQuestion();

            TailCrossedPrompt.answerForTests(null);
            org.traincontrol.gui.FacingPrompt.answerForTests(null);
            org.traincontrol.gui.ArrivalSidePrompt.answerForTests(null);

            if (editor != null)
            {
                final java.awt.Window closing = editor;

                // LATER, NOT AND-WAIT: closing may ask whether to keep what was changed.
                javax.swing.SwingUtilities.invokeLater(() -> closing.dispatchEvent(
                    new java.awt.event.WindowEvent(closing, java.awt.event.WindowEvent.WINDOW_CLOSING)));

                for (long end = System.currentTimeMillis() + 10000; closing.isShowing()
                    && System.currentTimeMillis() < end; )
                {
                    Thread.sleep(100);

                    takeDownDialogs();
                }

                if (closing.isShowing()) javax.swing.SwingUtilities.invokeLater(closing::dispose);
            }

            takeDownDialogs();

            train.setTrainLength(lengthWas);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                Point at = model.getAutoLayout().getLocomotiveLocation(train);

                if (at != null) model.getAutoLayout().moveLocomotive(null, at.getName(), false);
            });

            clearTunnel();
        }
    }

    /** The square the setup gives this name. */
    private static TileKey squareNamed(AutonomySession session, String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        return null;
    }

    /** Stands the train on a copy of the square trains may stand on, as a hand drive leaves it. */
    private static void standOn(org.traincontrol.base.Locomotive train, TileKey square) throws Exception
    {
        AutonomySession session = ui.getAutonomySession();

        String copy = null;

        for (String name : session.getStationIndex().pointNamesAt(square))
        {
            Point point = model.getAutoLayout().getPoint(name);

            if (point != null && point.isDestination() && copy == null) copy = name;
        }

        assertNotNull(copy, "precondition: no copy of " + square + " a train may stand on");

        final String onto = copy;

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            for (Point point : model.getAutoLayout().getPoints())
            {
                if (point.getCurrentLocomotive() != null) model.getAutoLayout().moveLocomotive(null, point.getName(), false);
            }

            model.getAutoLayout().moveLocomotive(train.getName(), onto, false);
        });

        assertNotNull(model.getAutoLayout().getLocomotiveLocation(train), "precondition: the train could not be stood on "
            + square);
    }

    /**
     * Control+X over one square and Control+V over Tunnel, through the diagram's key door, facing south so it arrives
     * from the north; returns once the tail question waits on the diagram, with a flag set when the paste door returns.
     */
    private static java.util.concurrent.atomic.AtomicBoolean pasteAtTunnelFromTheNorth(
        org.traincontrol.base.Locomotive train, TileKey from, TileKey tunnel) throws Exception
    {
        final java.lang.reflect.Method door = TrainControlUI.class.getDeclaredMethod("locomotiveGestureOnDiagram",
            int.class, boolean.class);

        door.setAccessible(true);

        final Object[] cut = new Object[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui.setHoveredDiagramTile(from.getPage(), from.getX(), from.getY());

                cut[0] = door.invoke(ui, java.awt.event.KeyEvent.VK_X, true);
            }
            catch (Exception refused)
            {
                cut[0] = refused;
            }
        });

        assertEquals(cut[0], Boolean.TRUE, "Control+X over the train was not taken");

        // FACING SOUTH, the way a train that came in from the north faces: what the cut remembered.
        java.lang.reflect.Field cutFacing = TrainControlUI.class.getDeclaredField("cutFacing");

        cutFacing.setAccessible(true);
        cutFacing.set(ui, org.traincontrol.automationui.TilePorts.Side.S);

        org.traincontrol.gui.FacingPrompt.answerForTests(org.traincontrol.automationui.TilePorts.Side.S);
        org.traincontrol.gui.ArrivalSidePrompt.answerForTests("N");
        TailCrossedPrompt.answerForTests(null);

        final java.util.concurrent.atomic.AtomicBoolean returned = new java.util.concurrent.atomic.AtomicBoolean(false);

        // LATER, NOT AND-WAIT: the paste door waits for the answer, with the window live.
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            try
            {
                ui.setHoveredDiagramTile(tunnel.getPage(), tunnel.getX(), tunnel.getY());

                door.invoke(ui, java.awt.event.KeyEvent.VK_V, true);
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

        java.lang.reflect.Field armed = TailCrossedPrompt.class.getDeclaredField("armed");

        armed.setAccessible(true);

        for (long end = System.currentTimeMillis() + 15000; armed.get(null) == null && !returned.get()
            && System.currentTimeMillis() < end; ) Thread.sleep(50);

        assertNotNull(armed.get(null), "precondition: the paste at Tunnel from the north left no tail question waiting on"
            + " the diagram (returned: " + returned.get() + ")");

        return returned;
    }

    /** The diagram's right-click on this square, as a right-click builds it. */
    private static javax.swing.JPopupMenu rightClickMenu(TileKey square) throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final java.lang.reflect.Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class,
            Point.class);

        gather.setAccessible(true);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
            TileKey.class, TileKey.class, Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

        make.setAccessible(true);

        final Point[] standing = new Point[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> standing[0] = ui.getAutonomyPointForTile(square));

        final Object options = gather.invoke(null, ui, standing[0]);

        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                menu[0] = (javax.swing.JPopupMenu) make.newInstance(ui, square, square, options);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        return menu[0];
    }

    private static javax.swing.JMenuItem itemNamed(java.awt.Container menu, String text)
    {
        java.awt.Component[] children = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component child : children)
        {
            if (child instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) child).getText()))
            {
                return (javax.swing.JMenuItem) child;
            }

            if (child instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = itemNamed((javax.swing.JMenu) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }

    /** "Can Be Chosen in Full Autonomy" on the square's right-click, under its setup menu, flipped. */
    private static void flipAutoDestination(TileKey square) throws Exception
    {
        javax.swing.JPopupMenu menu = rightClickMenu(square);

        final javax.swing.JMenuItem item = itemNamed(menu, org.traincontrol.util.I18n.t("autosetup.ui.menuAutoDestination"));

        assertNotNull(item, "the diagram's right-click on " + square + " has no Can Be Chosen in Full Autonomy item");

        javax.swing.SwingUtilities.invokeLater(item::doClick);

        for (int turn = 0; turn < 6; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /** The train's right-click Remove, on the diagram. */
    private static void removeFromTheDiagram(TileKey square, org.traincontrol.base.Locomotive train) throws Exception
    {
        javax.swing.JPopupMenu menu = rightClickMenu(square);

        final javax.swing.JMenuItem item = itemNamed(menu, org.traincontrol.util.I18n.f("layout.ui.menuRemoveLocomotive",
            train.getName()));

        assertNotNull(item, "the diagram's right-click on Tunnel has no Remove " + train.getName());

        javax.swing.SwingUtilities.invokeLater(item::doClick);

        for (long end = System.currentTimeMillis() + 10000; model.getAutoLayout().getLocomotiveLocation(train) != null
            && System.currentTimeMillis() < end; ) Thread.sleep(50);
    }

    private static void clickTunnelPre(AutonomySession session) throws Exception
    {
        TileKey square = null;

        for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(model.getAutoLayout(),
            model.getAutoLayout().getPoint("Tunnel (southbound)"), "N", 5, null))
        {
            if (choice.getFarthest().getName().startsWith("TunnelPre")) square = session.getStationIndex().squareOf(
                choice.getFarthest());
        }

        assertNotNull(square, "precondition: no TunnelPre square to click");

        java.util.Set<LayoutLabel> labels = ui.getDiagramTileRegistry().labelsFor(square);

        assertFalse(labels.isEmpty(), "precondition: TunnelPre is not drawn on the diagram");

        click(labels.iterator().next(), 1);
    }

    private static void awaitAnswered(java.util.concurrent.atomic.AtomicBoolean returned) throws Exception
    {
        for (long end = System.currentTimeMillis() + 15000; !returned.get() && System.currentTimeMillis() < end; )
        {
            Thread.sleep(50);
        }

        assertTrue(returned.get(), "the paste door did not return after TunnelPre was clicked");

        for (int turn = 0; turn < 6; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });
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

            // AND IN THE NEW RAILWAY'S OWN EDGES (TDU4-C4): the answer's road is the old railway's, whose edges end at
            // copies nothing runs on any more.
            for (org.traincontrol.automation.Edge edge : late.tunnelNow.getArrivedAlong())
            {
                assertTrue(model.getAutoLayout().getEdge(edge.getName()) == edge, "the road written after a rebuild is"
                    + " made of the old railway's edges, not the running railway's (TDU4-C4): " + edge.getName());
            }
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
