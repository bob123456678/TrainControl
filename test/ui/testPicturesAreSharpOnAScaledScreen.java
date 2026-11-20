package ui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.LocomotivePlaceholder;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.ImageUtil;
import support.Display;

/**
 * On a scaled screen (Java 9 and later at a Windows scale above 100%) the diagram's squares, the tab icons and the
 * locomotive and function pictures are drawn at the screen's real pixels from finer art, not stretched (MT-1053); at
 * scale 1, and so on Java 8, exactly as before.  Painted through a scaled Graphics2D, so no scaled screen is needed.
 */
public class testPicturesAreSharpOnAScaledScreen
{
    private static final support.SandboxedWindow held = new support.SandboxedWindow();
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Display.needed("a diagram square needs the window's picture loader");

        // Before the model: MarklinControlStation.init reads the layout preference too (OB-111)
        held.hold(support.LayoutSandbox.open());
        model = support.TestStation.simulatedInDebug();
        ui = held.openWindow(model);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null) model.stop();
        }
        finally
        {
            held.close();
        }
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
     * At 125% a column of squares running down the page meets with no light line between them, at Small (squares 37.5
     * screen pixels apart) and at Large (75 pixels across, an odd number, so turning about a whole pixel shifts it).
     *
     * MUTATION: in `LayoutDiagramComponent.getImage`, turn about `getWidth(null)/2`; in `SharpIcon.paintIcon`, round
     * the edges with `Math.round` (red on Java 21, whose clip the edges follow), or round a tile's art down.
     */
    @Test
    public void testSquaresSideBySideMeet() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            BufferedImage column = run(square(componentType.STRAIGHT, 1, size), true, 1.25);
            java.util.List<Integer> light = new java.util.ArrayList<>();

            for (int y = 0; y < column.getHeight(); y++)
            {
                if ((column.getRGB(column.getWidth() / 2, y) & 0xff) > 200) light.add(y);
            }

            assertTrue(light.isEmpty(), "squares " + size + " across at 125% leave light rows in the track: " + light);
        }
    }

    /**
     * At 125% and 175% a run of straights, across the page and down it, draws the same track on either side of every
     * joint, though its squares cover a screen pixel more or less by where they lie: no edge lighter in every other
     * square.
     *
     * MUTATION: in `SharpIcon.paintIcon`, make a tile's art the pixels it covers (`width` by `height`).
     */
    @Test
    public void testARunOfStraightsLinesUp() throws Exception
    {
        for (double scale : new double[] {1.25, 1.75})
        {
            for (int size : new int[] {30, 46, 60})
            {
                for (int turn = 0; turn < 4; turn++)
                {
                    BufferedImage run = run(square(componentType.STRAIGHT, turn, size), turn % 2 == 1, scale);

                    for (int i = 1; i < 4; i++)
                    {
                        int joint = (int) Math.ceil(scale * size * i - 0.5);
                        java.util.List<Integer> differ = new java.util.ArrayList<>();

                        for (int across = 0; across < (int) (size * scale); across++)
                        {
                            if (turn % 2 == 1 ? run.getRGB(across, joint - 1) != run.getRGB(across, joint)
                                : run.getRGB(joint - 1, across) != run.getRGB(joint, across)) differ.add(across);
                        }

                        assertTrue(differ.isEmpty(), "straights " + size + " across, turned " + turn + ", at "
                            + scale + " differ across joint " + i + " at " + differ);
                    }
                }
            }
        }
    }

    /** Four of `square` in a row, or a column if `down`, on white at `scale`, each clipped as Swing paints a square. */
    private static BufferedImage run(Icon square, boolean down, double scale)
    {
        int size = square.getIconWidth(), along = (int) (4 * size * scale), across = (int) (size * scale);
        BufferedImage run = new BufferedImage(down ? across : along, down ? along : across, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = run.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, run.getWidth(), run.getHeight());
        g.scale(scale, scale);

        for (int i = 0; i < 4; i++)
        {
            java.awt.Graphics each = down ? g.create(0, i * size, size, size) : g.create(i * size, 0, size, size);

            square.paintIcon(null, each, 0, 0);
            each.dispose();
        }

        g.dispose();

        return run;
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
     * unscaled screen see what they saw before; a function icon is the picture `getLocImage` makes, as before.
     *
     * MUTATION: in `SharpIcon.paintIcon`, draw the sharp copy at a scale of 1 too (`>= 1`).
     */
    @Test
    public void testAtScaleOneNothingChanges() throws Exception
    {
        String function = TrainControlUI.class.getResource("resources/functions/FktIcon_a_ge_01.png").toString();
        Image picture = ui.getLocImage(function, TrainControlUI.BUTTON_ICON_WIDTH);

        assertSame(ui.getLocIcon(function, TrainControlUI.BUTTON_ICON_WIDTH).getImage(), picture,
            "the function icon's picture is not the one getLocImage makes");

        // As a scaled screen keeps it, with its 48-pixel original
        Icon functionIcon = new org.traincontrol.util.SharpIcon(picture,
            javax.imageio.ImageIO.read(new java.net.URL(function)));

        Icon[] icons = {square(componentType.SWITCH_LEFT, 1, 30), square(componentType.CURVE, 3, 60),
            square(componentType.SIGNAL, 2, TrainControlUI.MEDIUM_TILE_SIZE), TrainControlUI.TAB_ICON_LAYOUT,
            functionIcon, LocomotivePlaceholder.icon(TrainControlUI.LOC_ICON_WIDTH)};

        for (Icon icon : icons)
        {
            assertEquals(differing(painted(icon, 1), painted(new ImageIcon(((ImageIcon) icon).getImage()), 1)), 0,
                icon.getIconWidth() + "-pixel " + icon.getClass().getSimpleName() + " at scale 1 is not what it was");
        }
    }

    /**
     * At scale 1 the pictures are the ones the code before D191 made, built here the old way: a square's at Small,
     * Medium and Large in each turn, the locomotive tab's page badge, and a locomotive button's picture with the black
     * shadow of its name.
     *
     * MUTATION: move the button's first shadow (`Math.round(2 * scale)`) or the badge (`x`) one pixel right.
     */
    @Test
    public void testAtScaleOneThePicturesAreTheOldOnes() throws Exception
    {
        for (int size : new int[] {30, TrainControlUI.MEDIUM_TILE_SIZE, 60})
        {
            for (int turn = 0; turn < 4; turn++)
            {
                for (componentType kind : new componentType[] {componentType.CURVE, componentType.SWITCH_LEFT})
                {
                    String name = new LayoutDiagramComponent(kind, 0, 0, 0, 0, 0, 0,
                        Accessory.accessoryDecoderType.MM2).getTypeName();

                    assertEquals(differing(onClear(((ImageIcon) square(kind, turn, size)).getImage()),
                        onClear(oldSquare(name, size, turn))), 0, kind + " at " + size + ", turned " + turn);
                }
            }
        }

        javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) support.Reflect.get(TrainControlUI.class, ui,
            "KeyboardTab");
        java.lang.reflect.Method page = support.Reflect.method(TrainControlUI.class, "switchLocMapping", int.class);

        onTheWindow(page, 3);
        assertEquals(differing(onClear(((ImageIcon) tabs.getIconAt(0)).getImage()), oldBadge("3")), 0,
            "the locomotive tab's badge for page 3");
        onTheWindow(page, 1);

        // A real keyboard button showing a locomotive with a picture
        java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"), "d191-button.png");
        BufferedImage photo = new BufferedImage(300, 116, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = photo.createGraphics();

        pg.setColor(new java.awt.Color(60, 120, 60));
        pg.fillRect(20, 20, 260, 76);
        pg.dispose();
        javax.imageio.ImageIO.write(photo, "png", file);

        String url = file.toURI().toURL().toString();
        Locomotive loc = model.getLocomotives().get(0);
        @SuppressWarnings("unchecked")
        java.util.Map<JButton, Locomotive> keys = (java.util.Map<JButton, Locomotive>)
            support.Reflect.method(TrainControlUI.class, "currentLocMapping").invoke(ui);
        JButton button = keys.keySet().iterator().next();
        Locomotive had = keys.get(button);
        String urlWas = loc.getImageURL(), textWas = button.getText();

        try
        {
            loc.setImageURL(url);
            keys.put(button, loc);
            SwingUtilities.invokeAndWait(() -> button.setText("BR 218"));
            onTheWindow(support.Reflect.method(TrainControlUI.class, "repaintIcon", JButton.class, Locomotive.class,
                Integer.class), button, loc, 1);

            assertTrue(support.Components.waitFor(() -> button.getIcon() instanceof ImageIcon, 10000),
                "the button never showed its picture");

            BufferedImage old = (BufferedImage) ui.getLocImage(url, 66);
            BufferedImage shadow = ImageUtil.generateImageWithText("BR 218", java.awt.Color.BLACK, button.getFont(),
                old.getWidth(), old.getHeight(), 2, 1);
            BufferedImage shadow2 = ImageUtil.generateImageWithText("BR 218", java.awt.Color.BLACK, button.getFont(),
                old.getWidth(), old.getHeight(), 1, -1);

            assertEquals(differing(onClear(((ImageIcon) button.getIcon()).getImage()),
                onClear(ImageUtil.mergeImages(ImageUtil.mergeImages(old, shadow), shadow2))), 0,
                "the locomotive button's picture with its name's shadow");
        }
        finally
        {
            keys.put(button, had);
            loc.setImageURL(urlWas);
            SwingUtilities.invokeAndWait(() -> button.setText(textWas));
        }
    }

    /** Calls one of the window's own methods on the event thread. */
    private static void onTheWindow(java.lang.reflect.Method method, Object... arguments) throws Exception
    {
        AtomicReference<Exception> failed = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                method.invoke(ui, arguments);
            }
            catch (ReflectiveOperationException e)
            {
                failed.set(e);
            }
        });

        if (failed.get() != null) throw failed.get();
    }

    /** A square's picture as before D191: the 30 or 60 art, scaled smoothly, turned about a whole pixel. */
    private static BufferedImage oldSquare(String name, int size, int turn) throws Exception
    {
        BufferedImage art = javax.imageio.ImageIO.read(TrainControlUI.class.getResource(
            "resources/icons" + (size <= 30 ? 30 : 60) + "/" + name + ".gif"));
        Image img = size == art.getWidth() ? art : art.getScaledInstance(size, size, Image.SCALE_SMOOTH);

        if (turn == 0) return onClear(img);

        java.awt.geom.AffineTransform t = new java.awt.geom.AffineTransform();

        t.rotate(Math.toRadians((4 - turn) * 90), size / 2, size / 2);

        return new java.awt.image.AffineTransformOp(t, java.awt.image.AffineTransformOp.TYPE_BILINEAR).filter(
            LayoutDiagramComponent.toBufferedImage(new ImageIcon(img).getImage()), null);
    }

    /** The locomotive tab's icon with a page badge, as before D191. */
    private static BufferedImage oldBadge(String text)
    {
        BufferedImage out = onClear(((ImageIcon) TrainControlUI.TAB_ICON_CONTROL).getImage());
        Graphics2D g = out.createGraphics();

        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));

        java.awt.geom.Rectangle2D ink = g.getFont().createGlyphVector(g.getFontRenderContext(), text).getVisualBounds();
        int tall = 13, wide = Math.max(tall, (int) Math.ceil(ink.getWidth()) + 7);
        int x = Math.max(0, (out.getWidth() - wide) / 2), y = Math.max(0, (out.getHeight() - tall) / 2);

        g.setColor(new java.awt.Color(0x26, 0x75, 0xBF));
        g.fillRoundRect(x, y, wide, tall, tall, tall);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(text, (float) (x + (wide - ink.getWidth()) / 2 - ink.getX()),
            (float) (y + (tall - ink.getHeight()) / 2 - ink.getY()));
        g.dispose();

        return out;
    }

    /**
     * For a scaled screen a big locomotive picture's original is kept as a copy of its own no wider and no taller than
     * 888 pixels, never as a picture that still holds the full-size one; a tall, narrow one too (F311J-C3).
     *
     * MUTATION: in `TrainControlUI.keptOriginal`, keep `img.getScaledInstance(...)` itself; or bound the width only.
     */
    @Test
    public void testABigPictureIsKeptOnlyAsASmallCopy() throws Exception
    {
        Image kept = keptOriginal(javax.imageio.ImageIO.read(new java.net.URL(bigPicture())), 1.25);

        assertTrue(kept instanceof BufferedImage && kept.getWidth(null) <= 3 * TrainControlUI.LOC_ICON_WIDTH,
            "kept as a " + kept.getClass().getSimpleName() + " " + kept.getWidth(null) + " wide");

        Image tall = keptOriginal(new BufferedImage(800, 6000, BufferedImage.TYPE_INT_ARGB), 1.25);

        assertTrue(tall.getHeight(null) <= 3 * TrainControlUI.LOC_ICON_WIDTH, "kept " + tall.getHeight(null) + " tall");
    }

    /**
     * At scale 1 (Java 8, or a screen at 100%) no original is kept, so memory is as before: showing a big picture where
     * the application shows one leaves nothing under its address in the image cache.
     *
     * MUTATION: in `TrainControlUI.keptOriginal`, drop the `scale <= 1` return.
     */
    @Test
    public void testNothingIsKeptAtScaleOne() throws Exception
    {
        String url = bigPicture();

        assertNull(keptOriginal(javax.imageio.ImageIO.read(new java.net.URL(url)), 1), "an original kept at scale 1");

        // As the selector, the locomotive panel and a keyboard button ask for it, where the screen is not scaled
        ui.getLocIcon(url, 142);
        ui.getLocIcon(url, TrainControlUI.LOC_ICON_WIDTH, TrainControlUI.LOC_ICON_HEIGHT);
        ui.getLocIcon(url, 66);

        if (org.traincontrol.util.SharpIcon.screenScale(ui) <= 1)
        {
            assertNull(TrainControlUI.getImageCache().get(url), "the original is kept under its address");
        }
    }

    /** The address of a 2000-pixel-wide picture written for these claims. */
    private static String bigPicture() throws Exception
    {
        java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"), "d191-big.png");
        BufferedImage big = new BufferedImage(2000, 1500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();

        g.setColor(java.awt.Color.ORANGE);
        g.fillOval(200, 200, 1600, 1100);
        g.dispose();
        javax.imageio.ImageIO.write(big, "png", file);

        return file.toURI().toURL().toString();
    }

    /** What `TrainControlUI.keptOriginal` keeps of `original` for a screen at `scale`. */
    private static Image keptOriginal(Image original, double scale) throws Exception
    {
        return (Image) support.Reflect.method(TrainControlUI.class, "keptOriginal", Image.class, double.class)
            .invoke(null, original, scale);
    }

    /**
     * A picture whose original is smaller than the screen's size for it is drawn from the original smoothly
     * (bilinear), not area-averaged, which magnifies in blocks.
     *
     * MUTATION: in `SharpIcon(Image, Image)`, area-average whatever the sizes.
     */
    @Test
    public void testASmallOriginalIsEnlargedSmoothly()
    {
        BufferedImage small = new BufferedImage(40, 16, BufferedImage.TYPE_INT_ARGB);

        for (int x = 0; x < 40; x++)
        {
            for (int y = 0; y < 16; y++) small.setRGB(x, y, (x + y) % 2 == 0 ? 0xff000000 : 0xffffffff);
        }

        Icon icon = new org.traincontrol.util.SharpIcon(ImageUtil.getScaledImage(small, 34, 14), small);

        assertEquals(differing(painted(icon, 2), onClear(ImageUtil.getScaledImage(small, 68, 28))), 0,
            "a 40-pixel original drawn 68 pixels across is not the bilinear enlargement");
    }

    /**
     * The (i) mark at 125% and 200% is drawn from art made at the screen's pixels: the picture `InfoMark` makes for
     * that size, its white dot and stem mirror symmetric with the stem exactly in the middle (an odd width on an odd
     * size), not the 14-pixel picture stretched, which doubles some rows and columns and sits off center.
     *
     * MUTATION: in `InfoMark.load`, return `new ImageIcon(out)` again.
     */
    @Test
    public void testTheInfoMarkIsDrawnAtTheScreensPixels() throws Exception
    {
        java.lang.reflect.Method art = support.Reflect.method(org.traincontrol.gui.InfoMark.class, "art", int.class,
            int.class);

        for (double scale : new double[] {1.25, 2})
        {
            int size = (int) Math.ceil(14 * scale - 0.5);
            BufferedImage drawn = painted(org.traincontrol.gui.InfoMark.icon(), scale).getSubimage(0, 0, size, size);

            assertEquals(differing(drawn, onClear((Image) art.invoke(null, size, size))), 0,
                "the mark at " + scale + " is not the art made " + size + " pixels across");

            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    assertEquals(isWhite(drawn, x, y), isWhite(drawn, size - 1 - x, y),
                        "the mark at " + scale + " is not mirror symmetric at " + x + "," + y);
                }
            }

            int stem = 0;

            for (int x = 0; x < size; x++) if (isWhite(drawn, x, size * 8 / 14)) stem++;

            assertEquals(stem % 2, size % 2, "the stem at " + scale + " is " + stem + " wide on " + size);
            assertTrue(stem >= 2 * size / 14 - 1 && stem <= 2 * size / 14 + 1, "the stem at " + scale + " is " + stem);
        }
    }

    /**
     * At scale 1 the mark is the GIF with its dark disk turned gray, pixel for pixel, as before.
     *
     * MUTATION: in `SharpIcon.paintIcon`, draw the sharp copy at a scale of 1 too (`>= 1`).
     */
    @Test
    public void testTheInfoMarkAtScaleOneIsTheGif() throws Exception
    {
        BufferedImage gif = javax.imageio.ImageIO.read(TrainControlUI.class.getResource("resources/info.gif"));
        BufferedImage gray = new BufferedImage(gif.getWidth(), gif.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int x = 0; x < gif.getWidth(); x++)
        {
            for (int y = 0; y < gif.getHeight(); y++)
            {
                int p = gif.getRGB(x, y);

                gray.setRGB(x, y, (p & 0xff) < 128 ? (p & 0xff000000) | 0xCCCCCC : p);
            }
        }

        assertEquals(differing(painted(org.traincontrol.gui.InfoMark.icon(), 1), onClear(gray)), 0, "the mark at scale 1");
        assertEquals(differing(painted(new ImageIcon(org.traincontrol.gui.InfoMark.icon().getImage()), 1), onClear(gray)), 0,
            "its picture");
    }

    /**
     * The window icon is the 256-pixel art and smooth-scaled copies of it at each size in the list, made once, not the
     * one big picture a title bar shrinks without smoothing; and the four windows whose designer code sets the single
     * picture ask for the list too.  On Java 8 it stays the one picture.
     *
     * MUTATION: in `TrainControlUI.applyWindowIcon`, call `setIconImage` of the one picture on every Java.
     */
    @Test
    public void testTheWindowIconIsAListOfSmoothSizes() throws Exception
    {
        java.net.URL url = TrainControlUI.class.getResource("resources/locicon.png");
        BufferedImage art = javax.imageio.ImageIO.read(url);
        boolean java8 = System.getProperty("java.specification.version").indexOf('.') >= 0;
        java.util.List<Image> icons = iconsOf(ui);

        if (java8)
        {
            assertEquals(icons.size(), 1, "Java 8 keeps the one picture");

            return;
        }

        int[] sizes = {16, 20, 24, 32, 40, 48, 64, 256};

        assertEquals(icons.size(), sizes.length, "the icon list");

        for (int i = 0; i < sizes.length; i++)
        {
            assertEquals(icons.get(i).getWidth(null), sizes[i], "the icon at index " + i);

            BufferedImage smooth = sizes[i] == 256 ? art : onClear(ImageUtil.toTransparentBufferedImage(new ImageIcon(
                art.getScaledInstance(sizes[i], sizes[i], Image.SCALE_AREA_AVERAGING)).getImage()));

            assertTrue(differing(onClear(icons.get(i)), smooth) <= sizes[i] * sizes[i] / 20,
                "the " + sizes[i] + " icon is not smooth-scaled from the art");
        }

        // Every window that sets the picture in designer code asks for the list in its own
        for (String file : new String[] {"gui/TrainControlUI", "gui/LayoutPopupUI", "gui/layouteditor/LayoutEditor",
            "gui/locomotive/LocomotiveSelector"})
        {
            String body = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/org/traincontrol/"
                + file + ".java")), java.nio.charset.StandardCharsets.UTF_8);

            assertTrue(body.replaceAll("(?s)//GEN-BEGIN:initComponents.*?//GEN-END:initComponents", "")
                .contains("applyWindowIcon(this)"), file + " never asks for the list");
        }
    }

    private static java.util.List<Image> iconsOf(TrainControlUI window) throws Exception
    {
        return support.Components.onTheEventThread(window::getIconImages);
    }

    /**
     * A disabled SharpIcon is FlatLaf's disabled picture of the 1x picture at scale 1, and at 1.25 the same gray
     * treatment of the art made at the real size, not the grayed small picture stretched.
     *
     * MUTATION: in `SharpIcon`, take out `implements FlatLaf.DisabledIconProvider`.
     */
    @Test
    public void testADisabledSharpIconIsDrawnAtTheScreensPixels() throws Exception
    {
        com.formdev.flatlaf.FlatLaf laf = (com.formdev.flatlaf.FlatLaf) javax.swing.UIManager.getLookAndFeel();
        org.traincontrol.util.SharpIcon.Art art = (w, h) ->
        {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

            for (int x = 0; x < w; x++)
            {
                for (int y = 0; y < h; y++) out.setRGB(x, y, 0xff000000 | (x * 255 / w) << 16 | (y * 255 / h) << 8 | 0x80);
            }

            return out;
        };
        Icon icon = new org.traincontrol.util.SharpIcon(art.at(20, 20), art);
        Icon disabled = laf.getDisabledIcon(null, icon);

        assertEquals(differing(painted(disabled, 1), painted(laf.getDisabledIcon(null, new ImageIcon(
            ((ImageIcon) icon).getImage())), 1)), 0, "at scale 1 it is not FlatLaf's picture of today");
        assertTrue(differing(painted(disabled, 1), painted(icon, 1)) > 0, "the disabled icon is not grayed");

        int size = (int) Math.ceil(20 * 1.25 - 0.5);
        BufferedImage expected = onClear(((ImageIcon) laf.getDisabledIcon(null, new ImageIcon(art.at(size, size))))
            .getImage());

        assertEquals(differing(painted(disabled, 1.25).getSubimage(0, 0, size, size), expected), 0,
            "at 1.25 it is not the gray of the art made " + size + " pixels across");
    }

    private static boolean isWhite(BufferedImage image, int x, int y)
    {
        return (image.getRGB(x, y) & 0xffffffff) == 0xffffffff;
    }

    /** The icon a square of this kind shows at `size`, turned `turn` quarters, once its picture has arrived. */
    private static Icon square(componentType kind, int turn, int size) throws Exception
    {
        LayoutDiagramComponent c = new LayoutDiagramComponent(kind, 0, 0, turn, 0, 0, 0,
            Accessory.accessoryDecoderType.MM2);
        AtomicReference<LayoutLabel> label = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> label.set(new LayoutLabel(c, new JPanel(), size, ui, false)));

        // The picture is decoded off the event thread and applied on it, over a blank stand-in
        assertTrue(support.Components.waitFor(() -> label.get().getIcon() instanceof ImageIcon, 10000),
            kind + " at " + size + " never got its picture");

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
