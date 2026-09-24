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
 * 12,7 on the other, both on 1 - Main.  The question is asked the way a placement door asks it - owned by the main
 * window - off the event thread, so the test can click; and the click is TunnelPre's own tile on the main diagram,
 * through the listener every s88 tile has.
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
}
