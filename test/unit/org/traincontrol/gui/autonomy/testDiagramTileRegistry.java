package org.traincontrol.gui.autonomy;

import java.util.Arrays;
import java.util.Collections;
import javax.swing.JPanel;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.gui.LayoutLabel;
import static support.Components.onTheEventThread;

/**
 * `DiagramTileRegistry.reset` on its own (OB-519): what a session reset leaves of the squares registered.  Its
 * integration tests are the page-window claims of `ui.testStartingAutonomyFromTheWindow`.
 */
public class testDiagramTileRegistry
{
    private static final TileKey SQUARE = new TileKey("Main", 1, 2);

    private static final TileOverlay PATH = new TileOverlay(TileOverlay.State.ACTIVE, false);

    private static final TileAnnotation MARK = new TileAnnotation(Collections.<TileAnnotation.Mark>emptyList(), 5, false);

    /**
     * A reset blanks a label still on screen and keeps it registered, so the next picture reaches it: a page window
     * left open is never built again to register it.
     *
     * MUTATION: in `DiagramTileRegistry.reset`, `tiles.clear()` instead of blanking, and this fails.
     */
    @Test
    public void testAResetBlanksALabelOnScreenAndKeepsIt()
    {
        Object[] seen = onTheEventThread(() ->
        {
            DiagramTileRegistry registry = new DiagramTileRegistry();
            LayoutLabel label = aLabel(true);

            registry.register(SQUARE, label);
            registry.publish(Collections.singletonMap(SQUARE, PATH));
            registry.annotate(SQUARE, MARK);

            assertTrue(PATH.equals(shown(label, "autonomyOverlay")) && MARK.equals(shown(label, "autonomyAnnotation")),
                "precondition: the label does not show what it was told");

            registry.reset();

            Object[] out = {shown(label, "autonomyOverlay"), shown(label, "autonomyAnnotation"),
                registry.overlayAt(SQUARE), null};

            registry.publish(Collections.singletonMap(SQUARE, PATH));

            out[3] = shown(label, "autonomyOverlay");

            return out;
        });

        assertEquals(Arrays.asList(seen), Arrays.asList(null, null, null, PATH), "after a reset, a label on screen shows"
            + " [overlay, annotation], the registry remembers, and the next picture shows");
    }

    /**
     * A reset lets go of a label off screen (a page put away, a grid replaced), as before.
     *
     * MUTATION: in `DiagramTileRegistry.reset`, leave out the `removeIf`, and this fails.
     */
    @Test
    public void testAResetLetsGoOfALabelOffScreen()
    {
        int[] left = onTheEventThread(() ->
        {
            DiagramTileRegistry registry = new DiagramTileRegistry();

            registry.register(SQUARE, aLabel(false));
            registry.reset();

            return new int[] {registry.labelsFor(SQUARE).size(), registry.size()};
        });

        assertEquals(left, new int[] {0, 0}, "a label off screen is still registered after a reset");
    }

    /** A label in a panel, displayable (given a peer, as a window on screen gives one) or not. */
    private static LayoutLabel aLabel(boolean displayable)
    {
        JPanel window = new JPanel();
        LayoutLabel label = new LayoutLabel(null, window, 30, null, false);

        window.add(label);

        if (displayable) window.addNotify();

        return label;
    }

    private static Object shown(LayoutLabel label, String name) throws ReflectiveOperationException
    {
        java.lang.reflect.Field field = LayoutLabel.class.getDeclaredField(name);

        field.setAccessible(true);

        return field.get(label);
    }
}
