package ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layout.LoadingSpinner;
import org.traincontrol.marklin.MarklinControlStation;
import support.Components;
import support.Settle;
import support.WindowField;

/**
 * At start-up the hourglass stands in the track diagram's place until the diagram is first drawn, so a slow start does
 * not show an empty diagram (OB-467).  Every component put into the diagram's panel is recorded as the window is built,
 * on a sandboxed copy of the test layout.
 */
public class testTheDiagramSaysItIsLoadingAtStart
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    // What was put into each panel, in order, from before the window existed
    private static final List<Component[]> added = Collections.synchronizedList(new ArrayList<>());

    private static final AWTEventListener RECORDER = e ->
    {
        if (e.getID() == ContainerEvent.COMPONENT_ADDED)
        {
            added.add(new Component[] {((ContainerEvent) e).getContainer(), ((ContainerEvent) e).getChild()});
        }
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        sandbox = support.LayoutSandbox.open();

        Toolkit.getDefaultToolkit().addAWTEventListener(RECORDER, AWTEvent.CONTAINER_EVENT_MASK);

        try
        {
            model = support.TestStation.simulatedWithWindow();

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "the model has no window");

            Settle.theDiagram(ui);
        }
        finally
        {
            Toolkit.getDefaultToolkit().removeAWTEventListener(RECORDER);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The first thing in the diagram's place is the hourglass, before the diagram; once the diagram is drawn the
     * hourglass is gone and the diagram is shown.
     *
     * MUTATION: leave out the call to `showTheDiagramIsLoadingIfBlank` on the event thread in
     * `TrainControlUI.repaintLayout`, and the diagram is the first thing put in its place.
     */
    @Test(timeOut = 120000)
    public void testTheHourglassStandsUntilTheFirstDrawing() throws Exception
    {
        final JPanel inner = WindowField.of(ui, "InnerLayoutPanel");

        List<Component> intoTheDiagram = new ArrayList<>();

        synchronized (added)
        {
            for (Component[] each : added) if (each[0] == inner) intoTheDiagram.add(each[1]);
        }

        assertFalse(intoTheDiagram.isEmpty(), "precondition: nothing was put in the diagram's place at start-up");

        assertTrue(intoTheDiagram.get(0) instanceof LoadingSpinner, "the first thing in the diagram's place at start-up"
            + " is not the hourglass but " + intoTheDiagram.get(0).getClass().getName());

        assertTrue(intoTheDiagram.stream().anyMatch(c -> !(c instanceof LoadingSpinner)), "precondition: the diagram"
            + " was never drawn");

        // The diagram itself may stand behind its own hourglass while its pictures decode
        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> shown(inner)).size() == 1, 20000),
            "the diagram's place does not end with one thing shown: "
            + Components.onTheEventThread(() -> shownNames(inner)));

        assertFalse(Components.onTheEventThread(() -> shown(inner).get(0) instanceof LoadingSpinner),
            "the hourglass is still in the diagram's place after the diagram was drawn");
    }

    /** The class names of the components visible in the panel. */
    private static List<String> shownNames(Container in)
    {
        List<String> names = new ArrayList<>();

        for (Component c : shown(in)) names.add(c.getClass().getSimpleName());

        return names;
    }

    private static List<Component> shown(Container in)
    {
        List<Component> visible = new ArrayList<>(Arrays.asList(in.getComponents()));

        visible.removeIf(c -> !c.isVisible());

        return visible;
    }
}
