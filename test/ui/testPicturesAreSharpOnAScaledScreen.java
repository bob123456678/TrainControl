package ui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.LocomotivePlaceholder;
import org.traincontrol.marklin.MarklinControlStation;
import support.Display;

/**
 * On a scaled screen (Java 9 and later at a Windows scale above 100%) the diagram's squares, the tab icons and the
 * locomotive and function pictures are drawn at the screen's real pixels from finer art, not stretched (MT-1053); at
 * scale 1, and so on Java 8, exactly as before.  Painted through a scaled Graphics2D, so no scaled screen is needed.
 */
public class testPicturesAreSharpOnAScaledScreen
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Display.needed("a diagram square needs the window's picture loader");

        // Before the model: MarklinControlStation.init reads the layout preference too (OB-111)
        sandbox = support.LayoutSandbox.open();
        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());
        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (ui != null)
        {
            final TrainControlUI closing = ui;

            SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        if (model != null) model.stop();
        if (sandbox != null) sandbox.close();
    }

    /**
     * A Large square at twice the scale is the 120-pixel art and a Small one the 60-pixel art, pixel for pixel, turned
     * as the square is turned; not the square's own picture stretched.
     *
     * MUTATION: in `LayoutLabel.setImageOnEDT`, put back `ICONS.computeIfAbsent(img, ImageIcon::new)`.
     */
    @Test
    public void testASquareIsDrawnFromTheFinerArt() throws Exception
    {
        for (componentType kind : new componentType[] {componentType.STRAIGHT, componentType.SWITCH_LEFT,
            componentType.CURVE, componentType.SIGNAL})
        {
            for (int size : new int[] {30, 60})
            {
                for (int turn = 0; turn < 2; turn++)
                {
                    Icon square = square(kind, turn, size);
                    String name = new LayoutDiagramComponent(kind, 0, 0, 0, 0, 0, 0,
                        Accessory.accessoryDecoderType.MM2).getTypeName();
                    String file = size == 60 ? "icons120/" + name + ".png" : "icons60/" + name + ".gif";
                    BufferedImage art = turned(onClear(javax.imageio.ImageIO.read(
                        TrainControlUI.class.getResource("resources/" + file))), turn);

                    assertEquals(differing(painted(square, 2), art), 0, kind + " at " + size + ", turned " + turn
                        + ", at twice the scale is not the art for twice its size");
                }
            }
        }
    }

    /**
     * A tab icon at twice the scale is its 512-pixel art area-averaged to 60 pixels, not its 30-pixel picture
     * stretched.
     *
     * MUTATION: in `TrainControlUI.getTabIcon`, return `new ImageIcon(scaledImage)`.
     */
    @Test
    public void testATabIconIsDrawnFromItsLargeArt()
    {
        Image art = new ImageIcon(TrainControlUI.class.getResource("resources/tabs/loc.png")).getImage();

        // Made into a picture of its own once, then drawn
        assertEquals(differing(painted(TrainControlUI.TAB_ICON_CONTROL, 2),
            onClear(onClear(art.getScaledInstance(60, 60, Image.SCALE_SMOOTH)))), 0,
            "the locomotive tab's icon at twice the scale is not its art made 60 pixels across");
    }

    /**
     * At scale 1 every one of these paints exactly what a plain ImageIcon of its picture paints, so Java 8 and an
     * unscaled screen see what they saw before; the function icon is the picture `getLocImage` makes, as before.
     *
     * MUTATION: in `SharpIcon.paintIcon`, draw the sharp copy at a scale of 1 too (`>= 1`).
     */
    @Test
    public void testAtScaleOneNothingChanges() throws Exception
    {
        String function = TrainControlUI.class.getResource("resources/functions/FktIcon_a_ge_01.png").toString();
        ImageIcon functionIcon = ui.getLocIcon(function, TrainControlUI.BUTTON_ICON_WIDTH);

        assertSame(functionIcon.getImage(), ui.getLocImage(function, TrainControlUI.BUTTON_ICON_WIDTH),
            "the function icon's picture is not the one getLocImage makes");

        Icon[] icons = {square(componentType.SWITCH_LEFT, 1, 30), square(componentType.CURVE, 3, 60),
            square(componentType.SIGNAL, 2, TrainControlUI.MEDIUM_TILE_SIZE), TrainControlUI.TAB_ICON_LAYOUT,
            functionIcon, LocomotivePlaceholder.icon(TrainControlUI.LOC_ICON_WIDTH)};

        for (Icon icon : icons)
        {
            assertEquals(differing(painted(icon, 1), painted(new ImageIcon(((ImageIcon) icon).getImage()), 1)), 0,
                icon.getIconWidth() + "-pixel " + icon.getClass().getSimpleName() + " at scale 1 is not what it was");
        }
    }

    /** The icon a square of this kind shows at `size`, turned `turn` quarters, once its picture has arrived. */
    private static Icon square(componentType kind, int turn, int size) throws Exception
    {
        LayoutDiagramComponent c = new LayoutDiagramComponent(kind, 0, 0, turn, 0, 0, 0,
            Accessory.accessoryDecoderType.MM2);
        AtomicReference<LayoutLabel> label = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> label.set(new LayoutLabel(c, new JPanel(), size, ui, false)));

        // The picture is decoded off the event thread and applied on it, over a blank stand-in
        long deadline = System.currentTimeMillis() + 10000;

        while (!(label.get().getIcon() instanceof ImageIcon) && System.currentTimeMillis() < deadline) Thread.sleep(20);

        assertTrue(label.get().getIcon() instanceof ImageIcon, kind + " at " + size + " never got its picture");

        return label.get().getIcon();
    }

    /** The icon painted at `scale` onto a clear picture of its size at that scale. */
    private static BufferedImage painted(Icon icon, double scale)
    {
        BufferedImage out = new BufferedImage((int) Math.round(icon.getIconWidth() * scale),
            (int) Math.round(icon.getIconHeight() * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();

        g.scale(scale, scale);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        return out;
    }

    /** The picture drawn onto a clear one of its size, as an icon draws it. */
    private static BufferedImage onClear(Image picture)
    {
        Image loaded = new ImageIcon(picture).getImage();
        BufferedImage out = new BufferedImage(loaded.getWidth(null), loaded.getHeight(null),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();

        g.drawImage(loaded, 0, 0, null);
        g.dispose();

        return out;
    }

    /** The picture turned as `LayoutDiagramComponent.getImage` turns a square: a quarter to the left per turn. */
    private static BufferedImage turned(BufferedImage art, int turn)
    {
        BufferedImage out = art;

        for (int i = 0; i < turn; i++)
        {
            BufferedImage was = out;

            out = new BufferedImage(was.getHeight(), was.getWidth(), BufferedImage.TYPE_INT_ARGB);

            for (int x = 0; x < out.getWidth(); x++)
            {
                for (int y = 0; y < out.getHeight(); y++) out.setRGB(x, y, was.getRGB(was.getWidth() - 1 - y, x));
            }
        }

        return out;
    }

    /** How many pixels differ; every pixel when the sizes do. */
    private static int differing(BufferedImage a, BufferedImage b)
    {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return a.getWidth() * a.getHeight();

        int differ = 0;

        for (int x = 0; x < a.getWidth(); x++)
        {
            for (int y = 0; y < a.getHeight(); y++) if (a.getRGB(x, y) != b.getRGB(x, y)) differ++;
        }

        return differ;
    }
}
