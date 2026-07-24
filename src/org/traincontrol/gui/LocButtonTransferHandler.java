package org.traincontrol.gui;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.ToIntFunction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;

/**
 * This class enables drag and drop of locomotives between the letter buttons, including across pages.
 * The locomotive is cut from the source button when the drag begins, and pasted onto the destination button when it is dropped.
 * Hovering over a page tab, or over the previous/next page buttons, turns the page mid-drag.
 * @author Adam
 */
public class LocButtonTransferHandler extends TransferHandler
{
    // Identifies a drag originating from a locomotive button.  Never leaves this JVM.
    private static final DataFlavor FLAVOR = createFlavor();

    // Pixels the mouse must travel before a click turns into a drag
    private static final int DRAG_THRESHOLD = 5;

    // Milliseconds the cursor must rest on a page control before the page is turned
    private static final int PAGE_HOVER_DELAY = 600;

    private final TrainControlUI ui;
    private final JTabbedPane tabs;

    private LocButtonTransferHandler(TrainControlUI ui, JTabbedPane tabs)
    {
        this.ui = ui;
        this.tabs = tabs;
    }

    /**
     * Makes the given letter button both a drag source and a drop target
     * @param ui
     * @param button
     * @param tabs - the page tabs, used to return to the source page if the drag is cancelled
     */
    public static void enable(TrainControlUI ui, JButton button, JTabbedPane tabs)
    {
        button.setTransferHandler(new LocButtonTransferHandler(ui, tabs));

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

                // Only allow moving locomotives when the power is off / in debug mode
                if (ui.getModel().getPowerState() && ui.getModel().getNetworkCommState()) return;

                // There is nothing to cut from an empty button
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

    /**
     * Makes hovering over the given component turn the page during a drag, so that locomotives can be moved across pages.
     * The component itself never accepts the drop.
     * @param component
     * @param tabs
     * @param offset - pages to move relative to the current page, or 0 to use the tab under the cursor
     */
    public static void enablePageSwitching(JComponent component, JTabbedPane tabs, int offset)
    {
        ToIntFunction<Point> pageAt;

        if (offset == 0)
        {
            pageAt = p -> tabs.indexAtLocation(p.x, p.y) + 1;
        }
        else
        {
            pageAt = p -> tabs.getSelectedIndex() + 1 + offset;
        }

        new DropTarget(component, DnDConstants.ACTION_MOVE, new PageSwitcher(tabs, pageAt));
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

        Origin origin = new Origin(source, ui.getLocMappingNumber());

        // Cut now, while the source page is still the one being shown.  Every page shares the same buttons,
        // so once the user turns the page we can no longer tell what was mapped to this button.
        ui.setCopyTarget(source, true);

        return new ButtonTransferable(origin);
    }

    @Override
    public boolean canImport(TransferSupport support)
    {
        // Only allow moving locomotives when the power is off
        if (ui.getModel().getPowerState() && ui.getModel().getNetworkCommState()) return false;

        if (!support.isDrop() || !support.isDataFlavorSupported(FLAVOR)) return false;

        Origin origin = getOrigin(support.getTransferable());

        // Dropping onto the button we started from is a no-op, though the same letter on another page is not
        return origin != null && ui.hasCopyTarget()
            && !(origin.button == support.getComponent() && origin.page == ui.getLocMappingNumber());
    }

    @Override
    public boolean importData(TransferSupport support)
    {
        if (!canImport(support)) return false;

        // The locomotive was cut when the drag began, so it only remains to paste it onto the current page
        ui.doPaste((JButton) support.getComponent(), false, false);

        return true;
    }

    @Override
    protected void exportDone(JComponent c, Transferable data, int action)
    {
        // The drop succeeded, or there is nothing left to put back
        if (action == MOVE || !ui.hasCopyTarget()) return;

        Origin origin = getOrigin(data);

        if (origin == null) return;

        // The drag was cancelled, so undo the cut - possibly on a page we have since navigated away from
        tabs.setSelectedIndex(origin.page - 1);
        ui.doPaste(origin.button, false, false);
    }

    /**
     * Returns the button and page the drag originated from, or null if unavailable
     * @param transferable
     * @return
     */
    private static Origin getOrigin(Transferable transferable)
    {
        try
        {
            return (Origin) transferable.getTransferData(FLAVOR);
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
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Origin.class.getName());
        }
        catch (ClassNotFoundException e)
        {
            return new DataFlavor(Origin.class, "Locomotive button");
        }
    }

    /**
     * The button, and the page it was showing, that a drag started from
     */
    private static final class Origin
    {
        private final JButton button;
        private final int page;

        Origin(JButton button, int page)
        {
            this.button = button;
            this.page = page;
        }
    }

    /**
     * Passes the drag origin to the drop target, by reference, within this JVM only
     */
    private static final class ButtonTransferable implements Transferable
    {
        private final Origin origin;

        ButtonTransferable(Origin origin)
        {
            this.origin = origin;
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

            return origin;
        }
    }

    /**
     * Turns the page when a drag rests over a page tab or a previous/next page button
     */
    private static final class PageSwitcher extends DropTargetAdapter
    {
        private final JTabbedPane tabs;
        private final ToIntFunction<Point> pageAt;
        private final Timer timer;

        private int pending = 0;

        PageSwitcher(JTabbedPane tabs, ToIntFunction<Point> pageAt)
        {
            this.tabs = tabs;
            this.pageAt = pageAt;

            this.timer = new Timer(PAGE_HOVER_DELAY, e -> switchPage());
            this.timer.setRepeats(false);
        }

        @Override
        public void dragOver(DropTargetDragEvent e)
        {
            // Page controls turn the page, they are never a destination themselves
            e.rejectDrag();

            if (!e.isDataFlavorSupported(FLAVOR))
            {
                cancel();
                return;
            }

            int page = pageAt.applyAsInt(e.getLocation());

            if (page != pending)
            {
                pending = page;

                if (isValid(page) && page != tabs.getSelectedIndex() + 1)
                {
                    timer.restart();
                }
                else
                {
                    timer.stop();
                }
            }
        }

        @Override
        public void dragExit(DropTargetEvent e)
        {
            cancel();
        }

        @Override
        public void drop(DropTargetDropEvent e)
        {
            cancel();
            e.rejectDrop();
        }

        private void switchPage()
        {
            if (isValid(pending))
            {
                tabs.setSelectedIndex(pending - 1);
            }

            // Re-arm, so that resting on the previous/next buttons keeps turning pages
            pending = 0;
        }

        private void cancel()
        {
            timer.stop();
            pending = 0;
        }

        private boolean isValid(int page)
        {
            return page >= 1 && page <= tabs.getTabCount();
        }
    }
}
