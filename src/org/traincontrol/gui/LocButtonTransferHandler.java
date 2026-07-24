package org.traincontrol.gui;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/**
 * This class enables drag and drop of locomotives between the letter buttons.
 * A drop is executed as a cut on the source button, followed by a paste on the destination button.
 * @author Adam
 */
public class LocButtonTransferHandler extends TransferHandler
{
    // Identifies a drag originating from a locomotive button.  Never leaves this JVM.
    private static final DataFlavor FLAVOR = createFlavor();

    // Pixels the mouse must travel before a click turns into a drag
    private static final int DRAG_THRESHOLD = 5;

    private final TrainControlUI ui;

    private LocButtonTransferHandler(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Makes the given letter button both a drag source and a drop target
     * @param ui
     * @param button
     */
    public static void enable(TrainControlUI ui, JButton button)
    {
        button.setTransferHandler(new LocButtonTransferHandler(ui));

        MouseAdapter dragListener = new MouseAdapter()
        {
            private Point pressedAt;

            @Override
            public void mousePressed(MouseEvent e)
            {
                // Right-clicks open the popup menu and must never start a drag
                pressedAt = SwingUtilities.isLeftMouseButton(e) ? e.getPoint() : null;
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                // Require some movement so that a click with a slight wiggle stays a click
                if (pressedAt == null || pressedAt.distance(e.getPoint()) < DRAG_THRESHOLD) return;

                JButton source = (JButton) e.getSource();

                // Only allow moving locomotives when the power is off
                // if (ui.getModel().getPowerState()) return;

                // Dragging an empty button would paste a null locomotive onto the destination
                if (!ui.buttonHasLocomotive(source)) return;

                pressedAt = null;

                // The button never receives mouseReleased once the drag starts, so it would stay pressed
                source.getModel().setArmed(false);
                source.getModel().setPressed(false);

                source.getTransferHandler().exportAsDrag(source, e, TransferHandler.MOVE);
            }
        };

        button.addMouseListener(dragListener);
        button.addMouseMotionListener(dragListener);
    }

    @Override
    public int getSourceActions(JComponent c)
    {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c)
    {
        JButton source = (JButton) c;

        // Drag a picture of the button itself.  We can't use the icon because it is null whenever
        // images are disabled, the locomotive has no image, the image failed, or it is still loading.
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        source.paint(g);
        g.dispose();

        setDragImage(image);
        setDragImageOffset(new Point(source.getWidth() / 2, source.getHeight() / 2));

        return new ButtonTransferable(source);
    }

    @Override
    public boolean canImport(TransferSupport support)
    {
        // Only allow moving locomotives when the power is off
        // if (ui.getModel().getPowerState()) return false;

        if (!support.isDrop() || !support.isDataFlavorSupported(FLAVOR)) return false;

        JButton source = getSource(support);

        // Never drop onto the originating button, and never drag an empty button
        return source != null && source != support.getComponent() && ui.buttonHasLocomotive(source);
    }

    @Override
    public boolean importData(TransferSupport support)
    {
        if (!canImport(support)) return false;

        // Cut from the source, then paste onto the destination
        ui.setCopyTarget(getSource(support), true);
        ui.doPaste((JButton) support.getComponent(), false, false);

        return true;
    }

    /**
     * Returns the button the drag originated from, or null if unavailable
     * @param support
     * @return
     */
    private static JButton getSource(TransferSupport support)
    {
        try
        {
            return (JButton) support.getTransferable().getTransferData(FLAVOR);
        }
        catch (UnsupportedFlavorException | IOException e)
        {
            return null;
        }
    }

    private static DataFlavor createFlavor()
    {
        try
        {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + JButton.class.getName());
        }
        catch (ClassNotFoundException e)
        {
            return new DataFlavor(JButton.class, "Locomotive button");
        }
    }

    /**
     * Passes the source button to the drop target, by reference, within this JVM only
     */
    private static final class ButtonTransferable implements Transferable
    {
        private final JButton button;

        ButtonTransferable(JButton button)
        {
            this.button = button;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors()
        {
            return new DataFlavor[]{FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
        {
            if (!FLAVOR.equals(flavor)) throw new UnsupportedFlavorException(flavor);

            return button;
        }
    }
}
