package ui;

import java.awt.Color;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.LocIconCropDialog.CropPanel;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import support.Components;
import support.Mouse;

/**
 * Re-cropping a locomotive's icon from the crop mark over the picture: the crop window opens over the photograph, the
 * part the operator chose is the part the locomotive keeps and the picture then shows, and Cancel (or Escape, or the
 * close box) leaves the icon as it was.  The photograph is a picture of the test's own in a temporary folder, never one
 * of the operator's; the crops go in the icon folder under this locomotive's name and are removed afterwards.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 *
 * @author Adam
 */
public class testRecroppingALocomotiveIcon
{
    private static final String NAME = "Recrop test train";
    private static final int ADDRESS = 3302;

    private static final long WAIT = 8000;

    /** The photograph: twice the icon's size and shape, red on the left half and blue on the right. */
    private static final int PHOTO_WIDTH = 2 * TrainControlUI.LOC_ICON_WIDTH;
    private static final int PHOTO_HEIGHT = 2 * TrainControlUI.LOC_ICON_HEIGHT;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static MarklinLocomotive loc;
    private static File folder;
    private static File photograph;

    /** The crops of this locomotive that exist before a claim, so that the claim's own can be told from them. */
    private List<String> before;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the crop mark is on a window");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedWithWindowInDebug();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        folder = java.nio.file.Files.createTempDirectory("recrop").toFile();

        photograph = new File(folder, "photograph.png");

        BufferedImage drawn = new BufferedImage(PHOTO_WIDTH, PHOTO_HEIGHT, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = drawn.createGraphics();

        g.setColor(Color.RED);
        g.fillRect(0, 0, PHOTO_WIDTH / 2, PHOTO_HEIGHT);
        g.setColor(Color.BLUE);
        g.fillRect(PHOTO_WIDTH / 2, 0, PHOTO_WIDTH / 2, PHOTO_HEIGHT);
        g.dispose();

        assertTrue(ImageIO.write(drawn, "png", photograph), "precondition: the photograph was not written");

        if (model.getLocByName(NAME) != null) model.deleteLoc(NAME);

        loc = model.newDCCLocomotive(NAME, ADDRESS);

        Components.onTheEventThread(() ->
        {
            if (ui.getActiveLoc() != loc) ui.mapLocToCurrentButton(NAME);

            return null;
        });

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == loc, WAIT), "precondition: the train is not the one on show");
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

            if (model != null && model.getLocByName(NAME) != null) model.deleteLoc(NAME);

            if (model != null) model.stop();
        }
        finally
        {
            if (folder != null)
            {
                File[] files = folder.listFiles();

                if (files != null) for (File f : files) f.delete();

                folder.delete();
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The train shows the photograph itself as its icon, as it does once an operator has picked one without cropping. */
    @BeforeMethod
    public void lay() throws Exception
    {
        closeEverythingOpen();

        before = crops();

        loc.setLocalImageURL(photograph.toURI().toString());

        ui.repaintLoc(true, null);

        Components.settle();
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        closeEverythingOpen();

        for (String crop : crops())
        {
            if (!before.contains(crop)) ui.deleteLocIcon(new File(crop).toURI().toString());
        }

        loc.setLocalImageURL(null);
    }

    // Choosing the crop

    /**
     * Zoomed in and moved to the blue half of the photograph, OK keeps that part: the icon is the blue half, the file
     * is the icon's size, the note beside it names the photograph, and the picture on screen is the new crop.  The
     * photograph itself is the operator's and stays.
     *
     * MUTATION: in `CropPanel.panBy`, move the center the other way (`this.centerX += dx / scale`), and this fails.
     */
    @Test
    public void testTheCropTheOperatorChoseIsTheOneKept() throws Exception
    {
        String old = loc.getLocalImageURL();

        JDialog dialog = openTheCrop();

        zoomTo(dialog, 500);
        pan(panelIn(dialog), -250, 0);
        pan(panelIn(dialog), -250, 0);

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> !old.equals(loc.getLocalImageURL()), WAIT), "OK did not give the train a new icon");

        String url = loc.getLocalImageURL();

        assertTrue(Util.isLocIconFile(url), "the crop was not kept among the application's own icons: " + url);

        BufferedImage kept = ImageIO.read(new File(new java.net.URI(url)));

        assertEquals(kept.getWidth(), TrainControlUI.LOC_ICON_WIDTH.intValue(), "the crop is not the icon's width");
        assertEquals(kept.getHeight(), TrainControlUI.LOC_ICON_HEIGHT.intValue(), "the crop is not the icon's height");

        assertTrue(isColor(kept.getRGB(4, kept.getHeight() / 2), Color.BLUE) && isColor(kept.getRGB(kept.getWidth() - 5,
            kept.getHeight() / 2), Color.BLUE), "the crop is not the blue half the operator moved the picture to");

        assertEquals(ui.cropSourceOf(url), photograph, "the crop does not remember the photograph it was cut from");
        assertTrue(photograph.isFile(), "re-cropping deleted the operator's own photograph");

        assertTrue(Components.waitFor(() -> shownIs(url), WAIT), "the picture on screen is not the crop that was kept");
    }

    /**
     * Pulling an edge of the frame in changes its shape: the crop kept is the photograph fitted inside the icon with
     * white either side, not stretched.
     *
     * MUTATION: in `CropPanel.resizeTo`, drop the `edge.contains("E")` line, and this fails.
     */
    @Test
    public void testPullingAnEdgeReshapesTheFrame() throws Exception
    {
        JDialog dialog = openTheCrop();

        CropPanel panel = panelIn(dialog);

        assertTrue(panel.isIconShaped(), "precondition: the frame does not open at the icon's shape");

        java.awt.Rectangle window = Components.onTheEventThread(panel::cropWindow);

        // The right edge, level with the middle but well off the grip in the middle, pulled in towards it
        int y = window.y + window.height / 2;

        drag(panel, new Point(window.x + window.width, y + 1), new Point(window.x + window.width / 2 + window.height / 2, y + 1));

        assertFalse(panel.isIconShaped(), "pulling the right edge in left the frame at the icon's shape");
        assertTrue(panel.getFrameAspect() < 1.5, "pulling the right edge in did not narrow the frame: " + panel.getFrameAspect());

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> Util.isLocIconFile(loc.getLocalImageURL()), WAIT), "OK did not give the train a crop");

        BufferedImage kept = ImageIO.read(new File(new java.net.URI(loc.getLocalImageURL())));

        int middle = kept.getHeight() / 2;

        assertEquals(kept.getWidth(), TrainControlUI.LOC_ICON_WIDTH.intValue(), "the crop is not the icon's width");

        assertTrue(isColor(kept.getRGB(3, middle), Color.WHITE) && isColor(kept.getRGB(kept.getWidth() - 4, middle), Color.WHITE),
            "the narrower frame was stretched across the icon instead of fitted inside it with white either side");

        assertTrue(isColor(kept.getRGB(kept.getWidth() / 2 - 15, middle), Color.RED)
            && isColor(kept.getRGB(kept.getWidth() / 2 + 15, middle), Color.BLUE),
            "the middle of the narrower crop is not the middle of the photograph");
    }

    // Leaving it alone

    /**
     * Cancel keeps the icon the train had: the same file, no new crop in the icon folder, and the same picture on
     * screen, though the view had been moved first.
     *
     * MUTATION: in `LocIconCropDialog`, have the Cancel button keep `this.cropPanel.getCroppedImage()`, and this fails.
     */
    @Test
    public void testCancelKeepsTheIconTheTrainHad() throws Exception
    {
        JDialog dialog = openTheCrop();

        zoomTo(dialog, 500);
        pan(panelIn(dialog), -250, 0);

        press(dialog, I18n.t("ui.cancel"));

        assertLeftAlone(dialog, "Cancel");
    }

    /**
     * Escape in the crop window is a Cancel.
     *
     * MUTATION: in the Escape action of `LocIconCropDialog`, keep `this.cropPanel.getCroppedImage()`, and this fails.
     */
    @Test
    public void testEscapeKeepsTheIconTheTrainHad() throws Exception
    {
        JDialog dialog = openTheCrop();

        zoomTo(dialog, 500);

        Components.onTheEventThread(() ->
        {
            javax.swing.JRootPane root = dialog.getRootPane();

            Object action = root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

            assertNotNull(action, "the crop window has no Escape key");

            root.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(root, 0, "escape"));

            return null;
        });

        assertLeftAlone(dialog, "Escape");
    }

    /**
     * The close box of the crop window is a Cancel as well.
     *
     * MUTATION: in `LocIconCropDialog`, make closing do nothing (`DO_NOTHING_ON_CLOSE`), and this fails.
     */
    @Test
    public void testTheCloseBoxKeepsTheIconTheTrainHad() throws Exception
    {
        JDialog dialog = openTheCrop();

        zoomTo(dialog, 500);

        Components.onTheEventThread(() ->
        {
            dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));

            return null;
        });

        assertLeftAlone(dialog, "the close box");
    }

    // Choosing again

    /**
     * A crop remembers the photograph and the view it was cut from: the next re-crop opens on the same view, over the
     * photograph and not over the crop, so zooming out shows what the first crop left out.  The first crop and its
     * note are removed once the second is kept.
     *
     * MUTATION: in `recropLocIcon`, ignore the note (`File source = null`), skip `deleteLocIconFile(superseded)`, or in
     * `cropLocIcon` write the note without the view, and this fails.
     */
    @Test
    public void testAnotherRecropStartsFromThePhotographAtTheSameView() throws Exception
    {
        JDialog dialog = openTheCrop();

        zoomTo(dialog, 500);
        pan(panelIn(dialog), -250, 0);

        double[] chosen = viewOf(panelIn(dialog));

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> Util.isLocIconFile(loc.getLocalImageURL()), WAIT), "OK did not give the train a crop");

        final String first = loc.getLocalImageURL();
        final File firstFile = new File(new java.net.URI(first));

        dialog = openTheCrop();

        CropPanel panel = panelIn(dialog);

        assertEquals(panel.source().getWidth(), PHOTO_WIDTH, "the second crop was cut from the crop, not from the photograph");
        assertEquals(panel.source().getHeight(), PHOTO_HEIGHT, "the second crop was cut from the crop, not from the photograph");

        double[] reopened = viewOf(panel);

        for (int i = 0; i < 5; i++)
        {
            assertEquals(reopened[i], chosen[i], 0.02, "the crop window did not open on the view it was left at (number " + i + ")");
        }

        // Back to the whole photograph, red and blue both
        press(dialog, I18n.t("loc.ui.cropReset"));

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> !first.equals(loc.getLocalImageURL()), WAIT), "OK did not give the train a new crop");

        BufferedImage second = ImageIO.read(new File(new java.net.URI(loc.getLocalImageURL())));

        assertTrue(isColor(second.getRGB(4, second.getHeight() / 2), Color.RED)
            && isColor(second.getRGB(second.getWidth() - 5, second.getHeight() / 2), Color.BLUE),
            "zooming out of the first crop did not bring back the rest of the photograph");

        assertFalse(firstFile.exists(), "the first crop was left in the icon folder after the second replaced it");
        assertFalse(noteOf(firstFile).exists(), "the first crop's note was left behind");
        assertEquals(ui.cropSourceOf(loc.getLocalImageURL()), photograph, "the second crop lost the photograph it came from");
    }

    /**
     * When the photograph has gone the crop is cropped itself, and its note still names the photograph so that it
     * can be found again if it comes back.
     *
     * MUTATION: in `recropLocIcon`, drop the `rememberCropSource(fresh, remembered)` line, and this fails.
     */
    @Test
    public void testAMissingPhotographFallsBackToTheCropAndKeepsThePath() throws Exception
    {
        JDialog dialog = openTheCrop();

        press(dialog, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> Util.isLocIconFile(loc.getLocalImageURL()), WAIT), "OK did not give the train a crop");

        final String first = loc.getLocalImageURL();

        File away = new File(folder, "away.png");

        assertTrue(photograph.renameTo(away), "precondition: the photograph could not be moved away");

        try
        {
            dialog = openTheCrop();

            CropPanel panel = panelIn(dialog);

            assertEquals(panel.source().getWidth(), TrainControlUI.LOC_ICON_WIDTH.intValue(),
                "with the photograph gone the crop window was not cut from the crop");

            press(dialog, I18n.t("ui.ok"));

            assertTrue(Components.waitFor(() -> !first.equals(loc.getLocalImageURL()), WAIT), "OK did not give the train a new crop");

            File note = noteOf(new File(new java.net.URI(loc.getLocalImageURL())));

            assertTrue(note.isFile(), "the new crop has no note");

            assertEquals(new String(java.nio.file.Files.readAllBytes(note.toPath()), java.nio.charset.StandardCharsets.UTF_8)
                .split("\n")[0].trim(), photograph.getAbsolutePath(), "the new crop's note lost the path of the photograph");
        }
        finally
        {
            assertTrue(away.renameTo(photograph), "the photograph could not be put back");
        }

        // And back again, the next crop is cut from the photograph
        JDialog back = openTheCrop();

        assertEquals(panelIn(back).source().getWidth(), PHOTO_WIDTH, "with the photograph back the crop was not cut from it");
    }

    // The window

    /** Left-clicks the crop mark over the picture, and the crop window that opens. */
    private static JDialog openTheCrop() throws Exception
    {
        java.lang.reflect.Field mark = TrainControlUI.class.getDeclaredField("cropOverlay");

        mark.setAccessible(true);

        final JLabel overlay = (JLabel) mark.get(ui);

        Components.onTheEventThread(() ->
        {
            overlay.setVisible(true);

            return null;
        });

        Mouse.click(overlay);

        JDialog[] found = new JDialog[1];

        assertTrue(Components.waitFor(() -> (found[0] = showing(I18n.t("loc.ui.cropTitle"))) != null, WAIT),
            "the crop mark opened no crop window");

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> panelIn(found[0]).getWidth() > 0), WAIT),
            "the crop window never got a size");

        return found[0];
    }

    private static JDialog showing(String title)
    {
        return Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing() && title.equals(((JDialog) w).getTitle())) return (JDialog) w;
            }

            return null;
        });
    }

    private static CropPanel panelIn(JDialog dialog)
    {
        return Components.find(dialog, CropPanel.class, null);
    }

    /** Sets the zoom slider, 0 to 1000, as dragging it does. */
    private static void zoomTo(JDialog dialog, int value)
    {
        JSlider slider = Components.find(dialog, JSlider.class, null);

        Components.onTheEventThread(() ->
        {
            slider.setValue(value);

            return null;
        });
    }

    /** Takes hold of the picture in the middle and moves it by this much. */
    private static void pan(CropPanel panel, int dx, int dy)
    {
        Point middle = new Point(panel.getWidth() / 2, panel.getHeight() / 2);

        drag(panel, middle, new Point(middle.x + dx, middle.y + dy));
    }

    private static void drag(CropPanel panel, Point from, Point to)
    {
        Components.onTheEventThread(() ->
        {
            long now = System.currentTimeMillis();

            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK, from.x, from.y,
                1, false, MouseEvent.BUTTON1));
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, now + 1, InputEvent.BUTTON1_DOWN_MASK, to.x, to.y,
                0, false, MouseEvent.NOBUTTON));
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, now + 2, 0, to.x, to.y, 1, false,
                MouseEvent.BUTTON1));

            return null;
        });

        Components.settle();
    }

    private static void press(JDialog dialog, String text)
    {
        JButton b = Components.find(dialog, JButton.class, x -> text.equals(x.getText()));

        assertNotNull(b, "the dialog has no \"" + text + "\" button");

        Components.press(b);
        Components.settle();
    }

    private static double[] viewOf(CropPanel panel)
    {
        double[] view = new double[6];

        Components.onTheEventThread(() ->
        {
            panel.copyViewInto(view);

            return null;
        });

        return view;
    }

    /** The window closed with nothing kept: the same icon file, no new crop, and the same picture on screen. */
    private void assertLeftAlone(JDialog dialog, String how)
    {
        String url = loc.getLocalImageURL();

        assertTrue(Components.waitFor(() -> !dialog.isShowing(), WAIT), how + " did not close the crop window");

        Components.settle();

        assertEquals(loc.getLocalImageURL(), url, how + " changed the train's icon");
        assertEquals(crops(), before, how + " left a new crop in the icon folder");
        assertTrue(photograph.isFile(), how + " deleted the operator's photograph");
        assertTrue(shownIs(url), "the picture on screen is not the icon the train kept after " + how);
    }

    /** The crops in the icon folder that carry this train's name. */
    private static List<String> crops()
    {
        List<String> found = new ArrayList<>();

        File[] files = new File(Util.LOC_ICON_FOLDER).listFiles();

        if (files != null)
        {
            for (File f : files)
            {
                if (f.getName().startsWith(Util.sanitizeFilename(NAME) + "_")) found.add(f.getAbsolutePath());
            }
        }

        java.util.Collections.sort(found);

        return found;
    }

    /** The file beside a crop that names the photograph it was cut from. */
    private static File noteOf(File crop) throws Exception
    {
        java.lang.reflect.Field suffix = TrainControlUI.class.getDeclaredField("CROP_SOURCE_SUFFIX");

        suffix.setAccessible(true);

        return new File(crop.getAbsolutePath() + suffix.get(null));
    }

    /** Whether the big picture of the train is the one the window makes of this icon. */
    private static boolean shownIs(String url)
    {
        return Components.onTheEventThread(() ->
        {
            try
            {
                JLabel picture = (JLabel) field("locIcon");

                return picture.getIcon() instanceof ImageIcon && ((ImageIcon) picture.getIcon()).getImage()
                    == ui.getLocImageMaxHeight(url, TrainControlUI.LOC_ICON_WIDTH, TrainControlUI.LOC_ICON_HEIGHT);
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Object field(String name) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }

    private static boolean isColor(int argb, Color color)
    {
        return Math.abs(((argb >> 16) & 255) - color.getRed()) < 40 && Math.abs(((argb >> 8) & 255) - color.getGreen()) < 40
            && Math.abs((argb & 255) - color.getBlue()) < 40;
    }

    private static void closeEverythingOpen()
    {
        Mouse.closeMenus();

        Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing()) w.dispose();
            }

            return null;
        });

        Components.settle();
    }
}
