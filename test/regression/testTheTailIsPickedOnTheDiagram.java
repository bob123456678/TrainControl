package regression;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
}
