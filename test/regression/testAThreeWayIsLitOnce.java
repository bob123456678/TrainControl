package regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.ConditionOutline;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.RouteEditorFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.ImageUtil;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Highlight on Diagram lights a three-way once, as commanded, where a route commands one of its decoders and checks the
 * other (OB-286, GUI2-C4).
 *
 * A square both commanded and checked is drawn as commanded - the stronger statement.  The rule was asked per address,
 * and since GUI-C5 a three-way answers to two: commanded under its first and checked under its second, it was washed
 * yellow and then orange, and the orange stayed.  On the frozen railway's 1 - Main, which has three-ways; a route whose
 * command sets one of them and whose condition asks its other decoder.
 *
 * MUTATION: light the checked accessories without leaving out the squares already commanded, and this fails.
 *
 * @author Adam
 */
public class testAThreeWayIsLitOnce
{
    private static final String ROUTE = "OB-286 route";

    /**
     * The three-way ends the highlight in the commanded colour.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAThreeWayCommandedAndCheckedIsLitAsCommanded() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the highlight is a drawing, and drawing needs a display");
        }

        // BEFORE init, which opens whatever the layout preference names (OB-111).
        support.LayoutSandbox sandbox = null;

        MarklinControlStation model = null;
        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        try
        {
            // OPENED INSIDE THE TRY (TSX-B8, OB-111): anything thrown between the open and the close would leave the
            // layout preference pointing at a folder under %TEMP%.
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            // showUI, because the registry of labels is filled by the grid the window mounts.
            model = init(null, true, true, false, true);

            model.setNetworkCommState(false);

            final TrainControlUI ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "precondition: there is no window");

            settle();
            settle();

            // A THREE-WAY ON 1 - MAIN, and the label it is drawn as.
            String page = "1 - Main";
            LayoutDiagramComponent threeWay = null;

            for (LayoutDiagramComponent tile : model.getLayout(page).getAll())
            {
                if (tile != null && tile.getType() == LayoutDiagramComponent.componentType.SWITCH_THREE
                    && tile.getLogicalAddress() > 0)
                {
                    threeWay = tile;

                    break;
                }
            }

            assertNotNull(threeWay, "precondition: the frozen railway's 1 - Main has no three-way");

            final TileKey square = new TileKey(page, threeWay.getX(), threeWay.getY());

            List<LayoutLabel> labels = new ArrayList<>(ui.getDiagramTileRegistry().labelsFor(square));

            assertFalse(labels.isEmpty(), "precondition: no label is registered for the three-way at " + square);

            final LayoutLabel label = labels.get(0);

            SwingUtilities.invokeAndWait(() -> label.endFlash());

            settle();

            final ImageIcon plain = (ImageIcon) label.getIcon();

            final int first = threeWay.getLogicalAddress();
            final Accessory.accessoryDecoderType protocol = threeWay.getProtocol() == null
                ? Accessory.accessoryDecoderType.MM2 : threeWay.getProtocol();

            assertTrue(threeWay.answersToAccessoryAddress(first + 1, protocol), "precondition: the three-way does not"
                + " answer to its second decoder");

            // THE ROUTE: commands the first decoder, and checks the second.
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(first, protocol, true));

            model.newRoute(ROUTE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            final MarklinControlStation m = model;

            SwingUtilities.invokeAndWait(() ->
            {
                frame[0] = new RouteEditorFrame(ui, ROUTE, m.getRoute(ROUTE));
                frame[0].setConditionRowsForTest(Arrays.asList(ConditionOutline.Row.condition(0,
                    RouteCommand.RouteCommandAccessory(first + 1, protocol, true))));
            });

            final java.lang.reflect.Method highlight = RouteEditorFrame.class.getDeclaredMethod("highlightOnDiagram");

            highlight.setAccessible(true);

            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    highlight.invoke(frame[0]);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            settle();

            assertTrue(label.isFlashOutstanding(), "precondition: the three-way was not lit at all");

            int[] now = pixels(label.getIcon());

            assertTrue(Arrays.equals(now, pixels(ImageUtil.addHighlightOverlay(plain, ImageUtil.HIGHLIGHT))),
                "a three-way whose first decoder the route commands and whose second it checks was not left in the"
                + " commanded colour"
                + (Arrays.equals(now, pixels(ImageUtil.addHighlightOverlay(plain, ImageUtil.HIGHLIGHT_CONDITION)))
                    ? " - it was washed as checked over the top (OB-286)" : ""));
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if (frame[0] != null) frame[0].dispose();

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window instanceof javax.swing.JDialog && window.isShowing()) window.dispose();
                }
            });

            if (model != null)
            {
                try { model.deleteRoute(ROUTE); } catch (Exception ignored) { }

                model.stop();
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** What an icon draws, pixel by pixel. */
    private static int[] pixels(javax.swing.Icon icon)
    {
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        icon.paintIcon(null, g, 0, 0);

        g.dispose();

        return image.getRGB(0, 0, w, h, null, 0, w);
    }

    private static void settle() throws Exception
    {
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
    }
}
