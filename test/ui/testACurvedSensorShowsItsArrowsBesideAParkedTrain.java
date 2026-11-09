package ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Feedback;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import support.Reflect;

/**
 * An active sensor's red arrows on a curve show beside an autonomy train parked on it (OB-400): out from the contact,
 * clear of the locomotive and its halo, and not faded with the square the train blocks.  A straight is drawn as before.
 *
 * Each picture is a sensor between two straights in the diagram's own container, painted through `LayoutLabel` and
 * `TileOverlay`; the train's covered and blocked track is set on a main window made without its constructor, the two
 * maps being all the tiles ask of it.
 */
public class testACurvedSensorShowsItsArrowsBesideAParkedTrain
{
    /** Small, Medium (drawn from the Large art) and Large. */
    private static final int[] SIZES = {30, 46, 60};

    /**
     * On a curve and a double curve, at every orientation, facing either way, at every size: every solid red pixel of
     * the arrows is drawn as it is with no train there.
     *
     * MUTATION: drop `paintCurvedSensorsArrowsWhole` from `LayoutLabel.paintComponent`, or set `CURVE_ARROWS_OUT` in
     * `docs/tools/tile-icons.py` back to 0 and redraw the icons, and this fails.
     */
    @Test
    public void testTheArrowsShowInFullBesideTheTrain() throws Exception
    {
        needsADisplay();

        StringBuilder wrong = new StringBuilder();

        for (componentType type : new componentType[] {componentType.FEEDBACK_CURVE,
            componentType.FEEDBACK_DOUBLE_CURVE})
        {
            for (int orientation = 0; orientation < 4; orientation++)
            {
                for (int size : SIZES)
                {
                    BufferedImage bare = drawn(type, orientation, size, false, true);

                    for (boolean facingA : new boolean[] {true, false})
                    {
                        BufferedImage parked = drawn(type, orientation, size, true, facingA);

                        int arrows = 0, lost = 0, changed = 0;

                        for (int y = size; y < 2 * size; y++)
                        {
                            for (int x = size; x < 2 * size; x++)
                            {
                                if (bare.getRGB(x, y) != parked.getRGB(x, y)) changed++;

                                if (!solidRed(bare.getRGB(x, y))) continue;

                                arrows++;

                                if (apart(bare.getRGB(x, y), parked.getRGB(x, y))) lost++;
                            }
                        }

                        String what = type + " at orientation " + orientation + ", " + size + " pixels, the train facing "
                            + (facingA ? "one way" : "the other");

                        assertTrue(arrows > 0, "precondition: the active " + what + " draws no red arrows");
                        assertTrue(changed > size * size / 10, "precondition: no train was drawn on the " + what);

                        if (lost > 0) wrong.append("\n  ").append(what).append(": ").append(lost).append(" of ")
                            .append(arrows).append(" red pixels changed");
                    }
                }
            }
        }

        assertEquals(wrong.toString(), "", "an active curved sensor's arrows are faded or covered where an autonomy"
            + " train is parked on it - Adam: \"S88 active arrows are barely visible on a curved track when an autonomy"
            + " train is parked there\"");
    }

    /**
     * A straight is left as it was: its arrows fade with the square at `BLOCKED_ALPHA`, where a parked train blocks it.
     *
     * MUTATION: let `paintCurvedSensorsArrowsWhole` draw a straight's arrows too, and this fails.
     */
    @Test
    public void testAStraightSensorsArrowsStillFadeWithItsSquare() throws Exception
    {
        needsADisplay();

        for (int orientation = 0; orientation < 2; orientation++)
        {
            for (int size : SIZES)
            {
                BufferedImage bare = drawn(componentType.FEEDBACK, orientation, size, false, true);
                BufferedImage parked = drawn(componentType.FEEDBACK, orientation, size, true, true);

                int arrows = 0, faded = 0;

                for (int y = size; y < 2 * size; y++)
                {
                    for (int x = size; x < 2 * size; x++)
                    {
                        if (!solidRed(bare.getRGB(x, y))) continue;

                        arrows++;

                        // red at 40% over white comes out a pink, rgb(245,153,153); covered by the locomotive, not red
                        if (!solidRed(parked.getRGB(x, y))) faded++;
                    }
                }

                String what = "the straight active sensor at orientation " + orientation + ", " + size + " pixels";

                assertTrue(arrows > 0, "precondition: " + what + " draws no red arrows");

                assertEquals(faded, arrows, what + " draws " + (arrows - faded) + " of its arrows' " + arrows + " red"
                    + " pixels at full strength under a parked train; only a curve's are (OB-400)");
            }
        }
    }

    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("painting a diagram needs a display");
    }

    /** The art's arrow red and its near shades, not the pink of their soft edge. */
    private static boolean solidRed(int rgb)
    {
        return ((rgb >> 16) & 0xFF) > 180 && ((rgb >> 8) & 0xFF) < 80 && (rgb & 0xFF) < 80;
    }

    /** More than a tenth apart in a channel: the halo's soft outer edge reaches a level or two further. */
    private static boolean apart(int a, int b)
    {
        for (int shift = 0; shift <= 16; shift += 8)
        {
            if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > 24) return true;
        }

        return false;
    }

    /**
     * An active sensor of the type in the middle of three squares by three, a straight on each of its track's sides,
     * painted; with the train, parked on its contact road, facing that road's first side or its second, its square
     * blocked (OB-280: on a double curve, that road only) and covered with the square behind.
     */
    private static BufferedImage drawn(componentType type, int orientation, int size, boolean train, boolean facingA)
        throws Exception
    {
        final BufferedImage[] shot = new BufferedImage[1];
        final JPanel[] grid = new JPanel[1];
        final Exception[] failed = new Exception[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                grid[0] = laidOut(type, orientation, size, train, facingA);
            }
            catch (Exception e)
            {
                failed[0] = e;
            }
        });

        if (failed[0] != null) throw failed[0];

        // the tiles take their pictures in an invokeLater: let those run, then paint
        SwingUtilities.invokeAndWait(() ->
        {
            shot[0] = new BufferedImage(3 * size, 3 * size, BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = shot[0].createGraphics();

            try
            {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 3 * size, 3 * size);

                grid[0].doLayout();
                grid[0].paint(g);
            }
            finally
            {
                g.dispose();
            }
        });

        return shot[0];
    }

    private static JPanel laidOut(componentType type, int orientation, int size, boolean train, boolean facingA)
        throws Exception
    {
        List<Route> roads = TilePorts.ports(type, orientation, 0);
        Route road = TilePorts.contactRoad(type, orientation);

        TileKey middle = new TileKey("1", 1, 1);
        TileKey behind = middle.beside(facingA ? road.getB() : road.getA());

        TrainControlUI window = null;

        if (train)
        {
            Set<RouteId> own = new LinkedHashSet<>(Collections.singletonList(new RouteId(0, roads.indexOf(road))));

            Map<TileKey, Set<RouteId>> covered = new HashMap<>();
            covered.put(middle, own);
            covered.put(behind, new LinkedHashSet<>(Collections.singletonList(new RouteId(0, 0))));

            Map<TileKey, Set<RouteId>> blocked = new HashMap<>();
            blocked.put(middle, type == componentType.FEEDBACK_DOUBLE_CURVE ? own : new LinkedHashSet<RouteId>());

            window = unconstructedWindow();

            setOn(window, "coveredTrack", covered);
            setOn(window, "blockedTrack", blocked);
        }

        JPanel grid = LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setBackground(Color.WHITE);
        grid.setSize(3 * size, 3 * size);

        LayoutDiagramComponent sensor = new LayoutDiagramComponent(type, 1, 1, orientation, 0, 7, 7,
            Accessory.accessoryDecoderType.MM2);

        Feedback occupied = new Feedback("7")
        {
            @Override
            public int getUID()
            {
                return 7;
            }

            @Override
            public void setState(boolean val)
            {
                _setState(val);
            }

            @Override
            public void addTile(LayoutLabel l)
            {
            }
        };

        occupied.setState(true);
        sensor.setFeedback(occupied);

        LayoutLabel centre = square(sensor, size, window, middle);

        if (train) centre.setAutonomyOverlay(TileOverlay.parked(facingA ? road.getA() : road.getB()));

        grid.add(centre);

        for (Side side : new Side[] {road.getA(), road.getB()})
        {
            TileKey at = middle.beside(side);

            grid.add(square(new LayoutDiagramComponent(componentType.STRAIGHT, at.getX(), at.getY(),
                side == Side.E || side == Side.W ? 0 : 1, 0, 0, 0, Accessory.accessoryDecoderType.MM2), size, window,
                at));
        }

        return grid;
    }

    /** A tile for the component, its picture put in the shared cache first so no window's loader is asked for it. */
    private static LayoutLabel square(LayoutDiagramComponent c, int size, TrainControlUI window, TileKey at)
        throws Exception
    {
        TrainControlUI.getImageCache().putIfAbsent(c.getImageKey(size, false), c.getImage(size, false));

        LayoutLabel label = new LayoutLabel(c, null, size, window, false);

        label.setSquare(at);
        label.setBounds(at.getX() * size, at.getY() * size, size, size);

        return label;
    }

    /** A main window allocated without its constructor: these tiles ask it only for the two maps set on it. */
    private static TrainControlUI unconstructedWindow() throws Exception
    {
        java.lang.reflect.Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");

        field.setAccessible(true);

        Object unsafe = field.get(null);

        return TrainControlUI.class.cast(unsafe.getClass().getMethod("allocateInstance", Class.class)
            .invoke(unsafe, TrainControlUI.class));
    }

    private static void setOn(TrainControlUI window, String name, Object value) throws Exception
    {
        Reflect.set(TrainControlUI.class, window, name, value);
    }
}
