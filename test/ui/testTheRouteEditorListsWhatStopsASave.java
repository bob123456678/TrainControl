package ui;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.RouteEditorFrame;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.PopupShape;

/**
 * Save in the route editor, with the route not yet right, says so in one short sentence and lists every problem in a
 * scrolling Details area, in a popup that fits the screen: a route pasted in can have a fault in every row.
 */
public class testTheRouteEditorListsWhatStopsASave
{
    /**
     * A blank route has no name and no command: Save names both, under the sentence, in the Details list.
     *
     * MUTATION: put the problems back into the message's text, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheProblemsAreListedUnderOneSentence() throws Exception
    {
        if (GraphicsEnvironment.isHeadless()) throw new SkipException("the route editor is a window");

        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        SwingUtilities.invokeAndWait(() ->
        {
            frame[0] = new RouteEditorFrame(null, null);
            frame[0].setVisible(true);
        });

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("route.ui.frameGoAndFixIt"))))
        {
            List<String> problems = new ArrayList<>();

            SwingUtilities.invokeAndWait(() -> problems.addAll(frame[0].problemsForTest()));

            assertTrue(problems.size() >= 2, "precondition: a blank route has fewer than two problems: " + problems);

            AbstractButton save = Components.withText(frame[0], I18n.t("route.ui.frameSave"));

            assertNotNull(save, "precondition: the route editor has no Save button");

            SwingUtilities.invokeLater(save::doClick);

            assertTrue(answering.awaitAsked(1, 15000), "Save on a blank route said nothing");

            PopupShape shown = answering.shapes().get(0);

            assertEquals(shown.sentence, I18n.t("route.ui.frameCannotSaveYet"), "the popup's text above the list is not"
                + " its one sentence: " + shown);

            assertEquals(shown.details.size(), problems.size(), "the Details list does not hold every problem: "
                + shown);

            for (int at = 0; at < problems.size(); at++)
            {
                assertTrue(shown.details.get(at).endsWith(problems.get(at)), "problem " + (at + 1) + " is not in the"
                    + " Details list: " + shown);
            }

            assertTrue(shown.width <= PopupShape.MAX_WIDTH, "the popup is " + shown.width + " px wide, more than "
                + PopupShape.MAX_WIDTH + ": " + shown);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }
}
