package org.traincontrol.gui.locomotive;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import org.traincontrol.util.ImageUtil;

/**
 * FR-022 - lets the user say WHICH PART of a picture becomes a locomotive icon, instead of scaling the whole picture to
 * the icon's slot (a portrait photograph, or a small locomotive on a platform, arrived as a postage stamp of
 * background).
 *
 * Hand written rather than built in the form editor, for the reason buildPathPreferenceMenu gives: there is no .form
 * file for this dialog and there must not be one.
 *
 * The crop WINDOW starts at the shape the icon is displayed at and the picture moves underneath it: zoom is a slider
 * and the wheel, panning is a drag.  Nothing here writes to disk; the dialog is handed a decoded image and hands back a
 * new one.
 *
 * @author Adam
 */
public class LocIconCropDialog extends JDialog
{
    // The application's look and feel, before this class can be instantiated.  A static block, not the constructor's
    // first line: `JDialog`'s constructor creates the root pane from whatever look and feel is installed at that
    // instant, so a cold-built window (a test's) got `MetalRootPaneUI` and an operating-system title bar instead of
    // FlatLaf's.  Headless-guarded because a static initializer that throws leaves the class unusable for the process.
    static
    {
        if (!java.awt.GraphicsEnvironment.isHeadless()) TrainControlUI.installLookAndFeel();
    }

    /**
     * How far the picture may be enlarged beyond the point where it just fills the crop window.  The picture goes soft
     * long before this, which is its own signal that the crop is too small.
     */
    private static final double MAX_ZOOM = 32.0;

    /**
     * How far OUT the zoom goes, as a fraction of the picture fitting the panel.  Adam: "now we can't zoom out beyond
     * the image width to add white space. Add a 0.5x zoom allowance."  At fit the frame and picture are about the same
     * size, leaving nowhere to put white; half again leaves a white margin all the way round.
     */
    private static final double MIN_ZOOM = 0.5;

    /**
     * Margin, in pixels, between the crop window and the edge of the panel: the only place the surrounding picture
     * shows, which is how the user knows which way to drag.
     */
    private static final int WINDOW_MARGIN = 44;

    /** The result, or null.  Read once, after the modal dialog returns. */
    private BufferedImage result = null;

    private final CropPanel cropPanel;

    /** Builds the dialog.  Private: callers go through {@link #crop}, which owns the modal show-and-read sequence. */
    private LocIconCropDialog(Window owner, String title, BufferedImage source, int outWidth,
        int outHeight)
    {
        super(owner, title, ModalityType.APPLICATION_MODAL);


        // OB-124.  A dialog does not reliably inherit its owner's icon, so it is asked for.
        TrainControlUI.applyWindowIcon(this);

        this.cropPanel = new CropPanel(source, outWidth, outHeight);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Wrapped, because a JLabel reports the width its text needs on one line (1247 pixels here) and pack() would
        // make the dialog that wide.  An explicit width in the HTML, not a pixel size on the label: the text differs
        // per language.
        JLabel instructions = new JLabel("<html><body style='width:420px'>"
            + I18n.t("loc.ui.cropInstructions") + "</body></html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 2));
        content.add(instructions, BorderLayout.NORTH);

        content.add(this.cropPanel, BorderLayout.CENTER);

        // Zoom on its own row above the buttons.  Beside them it reads as a third button and gets
        // clicked by somebody looking for OK.
        JPanel controls = new JPanel(new BorderLayout());
        controls.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel zoomRow = new JPanel(new BorderLayout(8, 0));
        zoomRow.add(new JLabel(I18n.t("loc.ui.cropZoom")), BorderLayout.WEST);

        // Integer ticks over a continuous zoom: the slider's own resolution is the only quantization,
        // and a thousand steps is finer than the pixel it moves the picture by.
        final JSlider zoom = new JSlider(0, 1000, 0);
        zoom.setToolTipText(I18n.t("loc.ui.tooltip.cropZoom"));
        zoomRow.add(zoom, BorderLayout.CENTER);

        JButton reset = new JButton(I18n.t("loc.ui.cropReset"));
        reset.setToolTipText(I18n.t("loc.ui.tooltip.cropReset"));
        zoomRow.add(reset, BorderLayout.EAST);

        controls.add(zoomRow, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JButton ok = new JButton(I18n.t("ui.ok"));
        JButton cancel = new JButton(I18n.t("ui.cancel"));

        buttons.add(cancel);
        buttons.add(Box.createHorizontalStrut(2));
        buttons.add(ok);

        controls.add(buttons, BorderLayout.SOUTH);

        content.add(controls, BorderLayout.SOUTH);

        setContentPane(content);

        // The slider drives the panel and the panel drives the slider back; the guard flag stops setValue's change
        // event from looping.
        zoom.addChangeListener(event ->
        {
            if (this.cropPanel.isSyncingZoom()) return;

            this.cropPanel.setZoomFraction(zoom.getValue() / 1000.0);
        });

        this.cropPanel.setZoomObserver(fraction ->
        {
            this.cropPanel.setSyncingZoom(true);
            zoom.setValue((int) Math.round(fraction * 1000.0));
            this.cropPanel.setSyncingZoom(false);
        });

        reset.addActionListener(event ->
        {
            // Just resetView: it restores the covering view and sets the slider under the syncingZoom guard.  A
            // zoom.setValue(0) after it would fire the listener back at MIN_ZOOM and leave white down two sides of an
            // icon nobody asked to change.
            this.cropPanel.resetView();
        });

        ok.addActionListener(event ->
        {
            this.result = this.cropPanel.getCroppedImage();
            dispose();
        });

        cancel.addActionListener(event ->
        {
            this.result = null;
            dispose();
        });

        // Escape cancels, matching every other dialog in this application.
        KeyStroke escape = KeyStroke.getKeyStroke("ESCAPE");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                LocIconCropDialog.this.result = null;
                dispose();
            }
        });

        getRootPane().setDefaultButton(ok);

        // Closing is a cancel (result is only assigned by OK); DISPOSE rather than the default HIDE, which would leave
        // the native window behind.
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        pack();

        // Resizable, but not smaller than it packs to: below that the buttons are cut off (OB-107).
        setMinimumSize(getSize());

        setLocationRelativeTo(owner);
    }

    /**
     * Shows the crop dialog and returns what the user chose.  Must be called on the event dispatch thread; it blocks
     * there until the dialog closes.
     *
     * @param parent the component the dialog should center on, may be null
     * @param source the picture to crop, must not be null
     * @param outWidth width of the icon to produce
     * @param outHeight height of the icon to produce
     * @return the cropped icon, or null if the user canceled, which the caller must read as "leave everything as it
     * was"
     */
    public static BufferedImage crop(Component parent, BufferedImage source, int outWidth,
        int outHeight)
    {
        return crop(parent, source, outWidth, outHeight, null);
    }

    /**
     * The same, opening on a remembered view and reporting back the one the user settled on (OB-125).  One array in
     * both directions, overwritten on the way out only when there is a result: canceling must change nothing, including
     * what the caller is about to store.
     *
     * @param parent the component to center on
     * @param source the picture to crop
     * @param outWidth width of the icon to produce
     * @param outHeight height of the icon to produce
     * @param view five numbers to open on, and where the view used is written back; may be null, and is ignored on the
     * way
     *        in when it does not describe a view
     * @return the cropped icon, or null if the user canceled
     */
    public static BufferedImage crop(Component parent, BufferedImage source, int outWidth,
        int outHeight, double[] view)
    {
        if (source == null) return null;

        Window owner = ownerOf(parent);

        LocIconCropDialog dialog = new LocIconCropDialog(owner, I18n.t("loc.ui.cropTitle"), source,
            outWidth, outHeight);

        dialog.cropPanel.setView(view);

        dialog.setVisible(true);

        if (dialog.result != null) dialog.cropPanel.copyViewInto(view);

        return dialog.result;
    }

    /** Told the zoom level whenever the panel changes it by itself, so the slider can follow. */
    public interface ZoomObserver
    {
        /**
         * @param fraction the new zoom, 0 (MIN_ZOOM of the picture fitting the panel, white on every side) to 1 (fully
         *                 zoomed in, at MAX_ZOOM)
         */
        void zoomChanged(double fraction);
    }

    /**
     * The picture, the crop window over it, and the mouse handling that moves one against the other.  Separable from
     * the dialog: it can be painted into a BufferedImage with no display attached.
     */
    public static class CropPanel extends JPanel
    {
        private final BufferedImage source;

        private final int outWidth;
        private final int outHeight;

        /**
         * The view is held as which point of the SOURCE picture is under the middle of the crop window, and how far in
         * we are, not as a scale and offset in panel pixels, so resizing the panel does not make the crop drift.
         */
        private double centerX;
        private double centerY;

        /**
         * 0 = {@link #MIN_ZOOM} of the picture fitting the panel, 1 = {@link #MAX_ZOOM} times it.
         */
        private double zoomFraction = 0.0;

        /** Whether the opening zoom has been chosen yet - see {@link #startAtCover}. */
        private boolean viewStarted = false;

        /**
         * A view handed in to open on, waiting for the panel to have a size (OB-125): a view set on a zero-width panel
         * clamps against nothing and is replaced by the opening view.  Taken up in {@link #startAtCover}.
         */
        private double[] pendingView = null;

        /**
         * The shape of the crop window, as width over height: starts at the icon's own shape, and pulling an edge or
         * corner changes it, the rest being filled with white by {@link #getCroppedImage}.  A ratio, so it means the
         * same at any panel size.
         */
        private double frameAspect;

        /**
         * How big the crop window is, as a fraction of the largest window of its shape that fits.  Pulling an edge
         * inward makes this smaller; outward grows the shape instead.
         */
        private double frameSize = 1.0;

        /** Which part of the window the current drag grabbed, or null when the drag is a pan. */
        private String dragEdge = null;

        /**
         * How close to an edge counts as grabbing it, in panel pixels.  Generous, since a mis-grab that pans is cheaper
         * to undo than one that reshapes the frame.
         */
        private static final int GRIP = 10;

        /**
         * Half the side of the square in the middle that pans the picture.  Adam: "drag frame, but add a control in the
         * middle to move it around, so the aspect change becomes deliberate."  Splitting by where you take hold keeps a
         * drag near an edge unambiguous.
         */
        private static final int PAN_GRIP = 26;

        private ZoomObserver zoomObserver = null;

        private boolean syncingZoom = false;

        private int dragFromX = 0;
        private int dragFromY = 0;

        /**
         * The whole surface, and what the crop is composed onto.  White, not a pale gray: it is the color the crop
         * takes wherever the frame is not over the photograph, so the panel has to show the truth.
         */
        private static final Color PAPER = Color.WHITE;

        /**
         * What is outside the frame is faded, not darkened: it still shows what is discarded, and over the paper it is
         * invisible, so a frame hanging off the picture leaves no black bars.
         */
        private static final Color VEIL = new Color(255, 255, 255, 165);
        private static final Color CHECKER_LIGHT = new Color(228, 228, 232);
        private static final Color CHECKER_DARK = new Color(200, 200, 206);
        /** The frame and its furniture, dark so they are legible against white paper and against a photograph. */
        private static final Color FRAME = new Color(28, 30, 34);

        private static final Color GUIDE = new Color(0, 0, 0, 38);

        private static final Color GRIP_FILL = new Color(255, 255, 255, 235);

        /**
         * @param source the picture to crop, must not be null
         * @param outWidth width of the icon that will be produced; only its RATIO to outHeight is used, as the crop
         *        window's starting shape
         * @param outHeight height of the icon that will be produced
         */
        public CropPanel(BufferedImage source, int outWidth, int outHeight)
        {
            this.source = source;
            this.outWidth = Math.max(1, outWidth);
            this.outHeight = Math.max(1, outHeight);

            this.frameAspect = (double) this.outWidth / this.outHeight;

            this.centerX = source.getWidth() / 2.0;
            this.centerY = source.getHeight() / 2.0;

            setBackground(PAPER);
            setOpaque(true);

            // The only thing that says the picture can be dragged before the user tries it.
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));

            // Wide enough to show the whole crop window at a useful size without being taller than a
            // laptop screen once the buttons and the instruction line are added underneath.
            setPreferredSize(new Dimension(600, 420));

            addComponentListener(new java.awt.event.ComponentAdapter()
            {
                /**
                 * The crop window is derived from the panel size, so a resize can push it outside the picture and
                 * nothing else re-checks that.
                 * @param e the resize
                 */
                @Override
                public void componentResized(java.awt.event.ComponentEvent e)
                {
                    CropPanel.this.startAtCover();
                    CropPanel.this.clampCenter();
                    CropPanel.this.repaint();
                }
            });

            addMouseListener(new MouseAdapter()
            {
                /**
                 * Decided once, when the button goes down, and held for the whole drag: asking again would turn a
                 * reshape into a pan.
                 */
                @Override
                public void mousePressed(MouseEvent e)
                {
                    String grabbed = CropPanel.this.grabAt(e.getX(), e.getY());

                    CropPanel.this.dragEdge = grabbed == null || "pan".equals(grabbed) ? null : grabbed;

                    CropPanel.this.dragFromX = e.getX();
                    CropPanel.this.dragFromY = e.getY();
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    CropPanel.this.dragEdge = null;
                }
            });

            addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if (CropPanel.this.dragEdge != null)
                    {
                        CropPanel.this.resizeTo(CropPanel.this.dragEdge, e.getX(), e.getY());

                        return;
                    }

                    CropPanel.this.panBy(e.getX() - CropPanel.this.dragFromX,
                        e.getY() - CropPanel.this.dragFromY);

                    CropPanel.this.dragFromX = e.getX();
                    CropPanel.this.dragFromY = e.getY();
                }

                /** The cursor is the only thing that says an edge can be pulled before somebody tries. */
                @Override
                public void mouseMoved(MouseEvent e)
                {
                    setCursor(java.awt.Cursor.getPredefinedCursor(
                        CropPanel.cursorFor(CropPanel.this.grabAt(e.getX(), e.getY()))));
                }
            });

            addMouseWheelListener((MouseWheelEvent e) ->
            {
                // One notch is a twentieth of the whole range.  Negative rotation is "away from the user", which every
                // other program treats as zooming in.  The PRECISE rotation: a trackpad reports fractions of a notch,
                // which getWheelRotation() rounds to zero, so a gentle scroll did not move the zoom.
                double notches = e.getPreciseWheelRotation();

                if (notches == 0) notches = e.getWheelRotation();

                if (notches == 0) return;

                setZoomFraction(this.zoomFraction - notches * 0.05);

                if (this.zoomObserver != null) this.zoomObserver.zoomChanged(this.zoomFraction);

                // Consumed, so an ancestor scroll pane cannot also act on it.
                e.consume();
            });
        }

        /** The pointer shape for whatever is under it (`grabAt`'s result), as a java.awt.Cursor constant. */
        private static int cursorFor(String grabbed)
        {
            if (grabbed == null || "pan".equals(grabbed))
            {
                return java.awt.Cursor.MOVE_CURSOR;
            }

            switch (grabbed)
            {
                case "N": return java.awt.Cursor.N_RESIZE_CURSOR;
                case "S": return java.awt.Cursor.S_RESIZE_CURSOR;
                case "E": return java.awt.Cursor.E_RESIZE_CURSOR;
                case "W": return java.awt.Cursor.W_RESIZE_CURSOR;
                case "NE": return java.awt.Cursor.NE_RESIZE_CURSOR;
                case "NW": return java.awt.Cursor.NW_RESIZE_CURSOR;
                case "SE": return java.awt.Cursor.SE_RESIZE_CURSOR;
                case "SW": return java.awt.Cursor.SW_RESIZE_CURSOR;
                default: return java.awt.Cursor.MOVE_CURSOR;
            }
        }

        /**
         * Registers the listener that keeps the zoom slider in step with the wheel.
         * @param observer the observer, or null to remove
         */
        public void setZoomObserver(ZoomObserver observer)
        {
            this.zoomObserver = observer;
        }

        /**
         * Whether the panel is currently pushing a zoom value INTO the slider.
         * @return true while a slider update originated here
         */
        public boolean isSyncingZoom()
        {
            return this.syncingZoom;
        }

        /**
         * Marks a slider update as originating from the panel, so the slider's change listener does not send it back.
         * @param syncing whether a push is in progress
         */
        public void setSyncingZoom(boolean syncing)
        {
            this.syncingZoom = syncing;
        }

        /**
         * The crop window, in panel coordinates: centered, and shaped to {@link #frameAspect} (the icon's ratio until
         * an edge has been dragged).  It is `largestWindow` scaled down by {@link #frameSize}, so once that is below 1
         * it sits inside the margin.  Recomputed on every use, because the panel is resizable.
         *
         * @return the crop window, never null and never empty
         */
        public Rectangle cropWindow()
        {
            Rectangle biggest = largestWindow(this.frameAspect);

            int width = Math.max(1, (int) Math.round(biggest.width * this.frameSize));
            int height = Math.max(1, (int) Math.round(biggest.height * this.frameSize));

            return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        }

        /**
         * The largest window of the given shape (width over height) that fits inside the panel's margins, centered; a
         * pull outward past the panel edge stops here.
         */
        private Rectangle largestWindow(double aspect)
        {
            int availableWidth = Math.max(1, getWidth() - 2 * WINDOW_MARGIN);
            int availableHeight = Math.max(1, getHeight() - 2 * WINDOW_MARGIN);

            int width = availableWidth;
            int height = (int) Math.round(width / aspect);

            if (height > availableHeight)
            {
                height = availableHeight;
                width = (int) Math.round(height * aspect);
            }

            width = Math.max(1, width);
            height = Math.max(1, height);

            return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        }

        /**
         * Whether the crop window is still the shape the icon is drawn at.  At that shape the picture fills the icon
         * exactly and nothing is added; otherwise it is fitted inside the icon and the rest is white.  A tolerance, not
         * equality: the shape arrives through pixel arithmetic on a resizable panel.
         *
         * @return true when no white will be added
         */
        public boolean isIconShaped()
        {
            return Math.abs(this.frameAspect - (double) this.outWidth / this.outHeight) < 0.002;
        }

        /**
         * Which part of the crop window is under a point: "pan" for the control in the middle, otherwise the edges
         * grabbed as a combination of N, S, E and W, or null for anywhere else.
         */
        private String grabAt(int x, int y)
        {
            Rectangle w = cropWindow();

            if (Math.abs(x - w.getCenterX()) <= PAN_GRIP && Math.abs(y - w.getCenterY()) <= PAN_GRIP)
            {
                return "pan";
            }

            // Only along the side it belongs to, so the corners are the only place two of them meet.
            boolean nearLeft = Math.abs(x - w.x) <= GRIP && y >= w.y - GRIP && y <= w.y + w.height + GRIP;
            boolean nearRight = Math.abs(x - (w.x + w.width)) <= GRIP
                && y >= w.y - GRIP && y <= w.y + w.height + GRIP;
            boolean nearTop = Math.abs(y - w.y) <= GRIP && x >= w.x - GRIP && x <= w.x + w.width + GRIP;
            boolean nearBottom = Math.abs(y - (w.y + w.height)) <= GRIP
                && x >= w.x - GRIP && x <= w.x + w.width + GRIP;

            String edge = (nearTop ? "N" : nearBottom ? "S" : "")
                + (nearLeft ? "W" : nearRight ? "E" : "");

            return edge.isEmpty() ? null : edge;
        }

        /**
         * Reshapes the crop window because an edge was dragged to (x, y).  The window stays centered and changes
         * symmetrically, so pulling the right edge moves the left one too.
         */
        private void resizeTo(String edge, int x, int y)
        {
            Rectangle w = cropWindow();

            double width = w.width;
            double height = w.height;

            if (edge.contains("E")) width = 2 * (x - w.getCenterX());
            if (edge.contains("W")) width = 2 * (w.getCenterX() - x);
            if (edge.contains("S")) height = 2 * (y - w.getCenterY());
            if (edge.contains("N")) height = 2 * (w.getCenterY() - y);

            // A floor rather than a clamp to the panel: a window a few pixels across is not a crop, and zero would
            // divide.
            width = Math.max(2 * GRIP + 2 * PAN_GRIP, width);
            height = Math.max(2 * GRIP + 2 * PAN_GRIP, height);

            this.frameAspect = width / height;

            Rectangle biggest = largestWindow(this.frameAspect);

            // Measured against the biggest window of the NEW shape, so pulling outward stops at the panel edge.
            this.frameSize = Math.max(0.05, Math.min(1.0,
                biggest.width == 0 ? 1.0 : width / biggest.width));

            clampCenter();

            repaint();
        }

        /**
         * Sets the shape of the crop window directly: the same change as dragging an edge, without a pointer, so the
         * arithmetic is testable.
         *
         * @param aspect width over height; zero and negatives are ignored rather than throwing
         */
        public void setFrameAspect(double aspect)
        {
            if (aspect <= 0 || Double.isNaN(aspect)) return;

            this.frameAspect = aspect;

            // The largest window of its shape that fits, as a drag to the panel's edge would produce; there is no
            // pointer to take a size from.
            this.frameSize = 1.0;

            clampCenter();

            repaint();
        }

        /**
         * Opens on a view taken from a previous crop, rather than on the covering center one (OB-125).  Ignored, not
         * rejected, when it does not describe a view: the sidecar it comes from may have been written over by anything.
         *
         * @param view five numbers as {@link #copyViewInto} writes them, or null for the default
         */
        public void setView(double[] view)
        {
            if (!viewIsUsable(view)) return;

            this.pendingView = view.clone();

            // So it is taken up the next time the panel is measured, whether or not an opening view was already chosen.
            this.viewStarted = false;
        }

        /**
         * The view as it stands, for storing against the crop about to be written.
         *
         * @param out five numbers: where in the source picture the middle of the crop window sits (x, y), the zoom
         *        fraction, and the shape and size of the window
         */
        public void copyViewInto(double[] out)
        {
            if (out == null || out.length < 5) return;

            out[0] = this.centerX;
            out[1] = this.centerY;
            out[2] = this.zoomFraction;
            out[3] = this.frameAspect;
            out[4] = this.frameSize;

            // And the crop's width in the photograph's own pixels (OB-257), where there is room: the zoom and frame
            // size are relative to the panel, so alone they crop another rectangle in a dialog of another shape
            if (out.length >= 6) out[5] = cropWindow().width / getScale();
        }

        /**
         * Whether five numbers describe a view this panel could open on.  The center is deliberately not range-checked:
         * a photograph edited since can make it fall outside, and `clampCenter` turns that into a nearby framing.
         *
         * @param view the numbers to check, may be null
         * @return whether they can be used
         */
        public static boolean viewIsUsable(double[] view)
        {
            if (view == null || view.length < 5) return false;

            for (int i = 0; i < 5; i++)
            {
                if (Double.isNaN(view[i]) || Double.isInfinite(view[i])) return false;
            }

            // Zoom is a fraction; the two frame numbers are ratios and cannot be zero or negative.
            return view[2] >= 0.0 && view[2] <= 1.0 && view[3] > 0.0 && view[4] > 0.0;
        }

        /**
         * The shape of the crop window.
         * @return width over height
         */
        public double getFrameAspect()
        {
            return this.frameAspect;
        }

        /** Puts the crop window back to the icon's own shape, filling the panel. */
        public void resetFrame()
        {
            this.frameAspect = (double) this.outWidth / this.outHeight;
            this.frameSize = 1.0;

            clampCenter();
        }

        /**
         * The scale at which the whole picture fits inside the panel.  It depends on the panel and the picture alone,
         * so reshaping or moving the frame never rescales the photograph underneath (Adam: "It should stay put unless
         * zoomed.").
         *
         * @return panel pixels per source pixel with the whole picture showing
         */
        private double fitScale()
        {
            int availableWidth = Math.max(1, getWidth() - 2 * WINDOW_MARGIN);
            int availableHeight = Math.max(1, getHeight() - 2 * WINDOW_MARGIN);

            return Math.min((double) availableWidth / this.source.getWidth(),
                (double) availableHeight / this.source.getHeight());
        }

        /**
         * The smallest the picture can be drawn - zoom 0, {@link #MIN_ZOOM} of fitting the panel.  A floor on how far
         * OUT the zoom goes, not on what the crop may contain: what the crop takes beyond the photograph is white.
         *
         * @return panel pixels per source pixel at zoom 0
         */
        public double getMinScale()
        {
            return fitScale() * MIN_ZOOM;
        }

        /**
         * How far in the zoom goes, as a multiple of the whole picture fitting the panel.  The slider is logarithmic,
         * so equal movements are equal ratios and extending either end costs nothing in the middle; it spans MIN_ZOOM
         * to MAX_ZOOM, a ratio of sixty-four.
         *
         * @return panel pixels per source pixel
         */
        public double getScale()
        {
            // The opening position is settled here, not only on a resize or paint: anything asking this panel a
            // question before it was shown would otherwise get the zoomed-out view.
            //
            // Not recursion-safe by the call graph: `startAtCover` calls `clampCenter`, which asks `getScale()`.  What
            // stops it is `startAtCover`'s own guard, which returns unless the view is still at its opening position.
            startAtCover();

            return getMinScale() * Math.pow(MAX_ZOOM / MIN_ZOOM, this.zoomFraction);
        }

        /**
         * Puts the zoom where the picture just covers the crop window, where this dialog has always opened.  Done once,
         * when the panel first has a size.  Only the STARTING position depends on the frame; after it the photograph
         * must not jump when the frame is nudged.
         */
        private void startAtCover()
        {
            if (this.viewStarted || getWidth() <= 0) return;

            this.viewStarted = true;

            // A remembered view instead of the covering crop (OB-125).  Shape and zoom first, then the center: clamping
            // a center before them clamps it against the wrong rectangle.
            if (this.pendingView != null)
            {
                double[] view = this.pendingView;

                this.pendingView = null;

                this.frameAspect = view[3];
                this.frameSize = view[4];
                this.zoomFraction = view[2];
                this.centerX = view[0];
                this.centerY = view[1];

                // The same rectangle of the photograph, where the view says how wide it was (OB-257): the zoom is
                // worked out from that width against this panel, not taken as a fraction of a panel that may have had
                // another shape
                if (view.length >= 6 && view[5] > 0 && !Double.isInfinite(view[5]))
                {
                    double floor = getMinScale();

                    if (floor > 0)
                    {
                        double wanted = cropWindow().width / view[5];

                        this.zoomFraction = Math.max(0.0, Math.min(1.0,
                            Math.log(wanted / floor) / Math.log(MAX_ZOOM / MIN_ZOOM)));
                    }
                }

                // The photograph may not be the one this view was taken over; clamping makes that a slightly wrong
                // framing, not a blank rectangle.
                clampCenter();

                if (this.zoomObserver != null) this.zoomObserver.zoomChanged(this.zoomFraction);

                return;
            }

            Rectangle window = cropWindow();

            double floor = getMinScale();

            if (floor <= 0) return;

            double cover = Math.max((double) window.width / this.source.getWidth(),
                (double) window.height / this.source.getHeight());

            // Where the covering scale sits along the logarithmic slider, so a ratio of ratios.  Below zero would mean
            // the picture already covers the window at the smallest scale offered, which the clamp below handles.
            double fraction = Math.log(cover / floor) / Math.log(MAX_ZOOM / MIN_ZOOM);

            this.zoomFraction = Math.max(0.0, Math.min(1.0, fraction));

            if (this.zoomObserver != null) this.zoomObserver.zoomChanged(this.zoomFraction);
        }

        /**
         * Sets the zoom, clamping it into range and keeping the middle of the crop window over the same part of the
         * picture.  About the window center rather than the pointer: it is the only fixed point that behaves the same
         * from the wheel, the slider or the reset button.
         *
         * @param fraction 0 (MIN_ZOOM, half size with white on every side) to 1 (MAX_ZOOM); out of range values are
         *        clamped, because the wheel routinely asks for them
         */
        public void setZoomFraction(double fraction)
        {
            // The opening position is settled first, so what the caller asked for lands on top of it: a zoom set before
            // anything read the panel would otherwise be replaced by the opening value the moment something did.
            startAtCover();

            this.zoomFraction = Math.max(0.0, Math.min(1.0, fraction));

            clampCenter();

            repaint();
        }

        /**
         * The current zoom.
         * @return 0 to 1
         */
        public double getZoomFraction()
        {
            return this.zoomFraction;
        }

        /** Returns the view to where it started: zoomed out, centered on the middle of the picture. */
        public void resetView()
        {
            // Back to how the dialog opened, the picture covering the frame, not to zoom 0, which would leave white
            // down two sides of an icon nobody asked to change.
            this.viewStarted = false;
            this.zoomFraction = 0.0;
            this.centerX = this.source.getWidth() / 2.0;
            this.centerY = this.source.getHeight() / 2.0;

            // The frame's shape too: it is the only way back to the icon's own shape without eyeballing it.
            resetFrame();

            startAtCover();

            clampCenter();

            repaint();
        }

        /**
         * Moves the picture under the crop window by a distance measured in panel pixels.
         *
         * @param dx panel pixels the picture should move right
         * @param dy panel pixels the picture should move down
         */
        public void panBy(int dx, int dy)
        {
            double scale = getScale();

            // Dividing by the scale makes the drag track the pointer: at 4x the picture moves a quarter as far in its
            // own pixels.
            this.centerX -= dx / scale;
            this.centerY -= dy / scale;

            clampCenter();

            repaint();
        }

        /**
         * Pulls the view back inside the picture, after every change.  Where the picture is smaller than the window in
         * a dimension (rounding at the boundary), it is centered instead of clamped, so the failure is a centered crop.
         */
        private void clampCenter()
        {
            // The picture may hang off the frame, so the frame need not be covered; what has to stay true is that some
            // of the picture remains under it, or the dialog shows a blank rectangle with nothing saying which way the
            // photograph went.
            Rectangle window = cropWindow();

            double scale = getScale();

            if (scale <= 0) return;

            // A margin rather than a single pixel: a sliver at the very edge of the frame is not enough to drag back
            // by.
            double keep = Math.min(24.0, Math.min(window.width, window.height) / 3.0) / scale;

            double halfW = window.width / (2.0 * scale);
            double halfH = window.height / (2.0 * scale);

            // The picture is drawn with `centerX` under the middle of the window, so requiring `keep` of overlap at
            // each side gives:
            //
            //     centerX  >  keep - halfW              (the right edge stays inside the frame)
            //     centerX  <  srcW + halfW - keep       (the left edge does)
            this.centerX = Math.max(keep - halfW,
                Math.min(this.source.getWidth() + halfW - keep, this.centerX));

            this.centerY = Math.max(keep - halfH,
                Math.min(this.source.getHeight() + halfH - keep, this.centerY));
        }

        /** Paints the backdrop, the picture, and the crop window over it. */
        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                g2.setColor(PAPER);
                g2.fillRect(0, 0, getWidth(), getHeight());

                startAtCover();

                Rectangle window = cropWindow();

                double scale = getScale();

                int drawWidth = (int) Math.round(this.source.getWidth() * scale);
                int drawHeight = (int) Math.round(this.source.getHeight() * scale);

                int drawX = (int) Math.round(window.getCenterX() - this.centerX * scale);
                int drawY = (int) Math.round(window.getCenterY() - this.centerY * scale);

                Rectangle picture = new Rectangle(drawX, drawY, drawWidth, drawHeight);

                // The checkerboard only where the PICTURE is, and only inside the window: locomotive icons are often
                // transparent PNGs, which must be distinguishable from a pale part of the photograph.  Not over the
                // white, which is what a transparent pixel becomes out there.
                Rectangle transparent = window.intersection(picture);

                if (!transparent.isEmpty()) paintCheckerboard(g2, transparent);

                g2.drawImage(this.source, drawX, drawY, drawWidth, drawHeight, null);

                // Everything outside the window faded rather than darkened: it still shows what is discarded, reads as
                // washed out over the photograph, and does nothing over the paper (white over white), so there are no
                // bars.
                g2.setColor(VEIL);
                g2.fillRect(0, 0, getWidth(), window.y);
                g2.fillRect(0, window.y + window.height, getWidth(),
                    getHeight() - window.y - window.height);
                g2.fillRect(0, window.y, window.x, window.height);
                g2.fillRect(window.x + window.width, window.y, getWidth() - window.x - window.width,
                    window.height);

                g2.setColor(GUIDE);

                for (int third = 1; third <= 2; third++)
                {
                    int x = window.x + window.width * third / 3;
                    int y = window.y + window.height * third / 3;

                    g2.drawLine(x, window.y, x, window.y + window.height);
                    g2.drawLine(window.x, y, window.x + window.width, y);
                }

                // Dark, and two pixels of it: the frame must never be ambiguous over the photograph, the faded
                // photograph or white paper.
                g2.setColor(FRAME);
                g2.drawRect(window.x, window.y, window.width - 1, window.height - 1);
                g2.drawRect(window.x + 1, window.y + 1, window.width - 3, window.height - 3);

                paintHandles(g2, window);

                paintPanGrip(g2, window);
            }
            finally
            {
                g2.dispose();
            }
        }

        /**
         * The eight blocks on the frame's edges and corners that can be pulled; drawn because an invisible affordance
         * is not one (the cursor only says so once the pointer is already there).
         */
        private void paintHandles(Graphics2D g2, Rectangle window)
        {
            final int arm = 14;
            final int thick = 3;

            g2.setColor(FRAME);

            int midX = window.x + window.width / 2;
            int midY = window.y + window.height / 2;

            // Corners drawn as two short arms rather than a square: a square at the corner of a frame
            // reads as a selection handle you can drag anywhere, and these only move two edges.
            for (int cx : new int[] {window.x, window.x + window.width - arm})
            {
                for (int cy : new int[] {window.y, window.y + window.height - thick})
                {
                    g2.fillRect(cx, cy, arm, thick);
                }
            }

            for (int cx : new int[] {window.x, window.x + window.width - thick})
            {
                for (int cy : new int[] {window.y, window.y + window.height - arm})
                {
                    g2.fillRect(cx, cy, thick, arm);
                }
            }

            // And one on the middle of each side, which is where somebody reaches to change only the
            // width or only the height.
            g2.fillRect(midX - arm / 2, window.y, arm, thick);
            g2.fillRect(midX - arm / 2, window.y + window.height - thick, arm, thick);
            g2.fillRect(window.x, midY - arm / 2, thick, arm);
            g2.fillRect(window.x + window.width - thick, midY - arm / 2, thick, arm);
        }

        /**
         * The control in the middle that moves the picture.  Adam: "add a control in the middle to move it around, so
         * the aspect change becomes deliberate."  Dragging elsewhere inside the frame still pans.  A four-way arrow,
         * which needs no words in eight languages.
         */
        private void paintPanGrip(Graphics2D g2, Rectangle window)
        {
            int cx = (int) window.getCenterX();
            int cy = (int) window.getCenterY();

            int half = PAN_GRIP - 8;

            g2.setColor(GRIP_FILL);
            g2.fillRoundRect(cx - half, cy - half, half * 2, half * 2, 7, 7);

            // An outline rather than a drop shadow: a border is what makes a white control visible on white.
            g2.setColor(FRAME);
            g2.drawRoundRect(cx - half, cy - half, half * 2, half * 2, 7, 7);

            int arm = half - 4;
            int head = 3;

            g2.drawLine(cx - arm, cy, cx + arm, cy);
            g2.drawLine(cx, cy - arm, cx, cy + arm);

            // Arrowheads, so it reads as "move" rather than as a target or a crosshair.
            g2.drawLine(cx - arm, cy, cx - arm + head, cy - head);
            g2.drawLine(cx - arm, cy, cx - arm + head, cy + head);
            g2.drawLine(cx + arm, cy, cx + arm - head, cy - head);
            g2.drawLine(cx + arm, cy, cx + arm - head, cy + head);
            g2.drawLine(cx, cy - arm, cx - head, cy - arm + head);
            g2.drawLine(cx, cy - arm, cx + head, cy - arm + head);
            g2.drawLine(cx, cy + arm, cx - head, cy + arm - head);
            g2.drawLine(cx, cy + arm, cx + head, cy + arm - head);
        }

        /**
         * Whether a rectangle (in source coordinates) lies wholly within the picture: a crop that is entirely
         * photograph can be handed straight out and keeps its transparency; one that reaches past the edge cannot,
         * because that is white.
         */
        private boolean wholelyInside(Rectangle region)
        {
            return region.x >= 0 && region.y >= 0
                && region.x + region.width <= this.source.getWidth()
                && region.y + region.height <= this.source.getHeight();
        }

        /**
         * What the frame actually covers, in the rectangle's own proportions: the photograph at its own resolution
         * where the rectangle lies inside it, white where it reaches past the edge, **scaled down to at most the icon's
         * size** on the way out.  Drawn rather than sub-imaged: getSubimage throws on a rectangle that is not wholly
         * inside, and clipping it would quietly return a different crop.
         *
         * @param region the rectangle in source coordinates, possibly overhanging
         * @return an image of that rectangle - the rectangle itself when it lies wholly inside the source, otherwise
         * the
         *         same shape scaled to fit the icon
         */
        private BufferedImage contentOf(Rectangle region)
        {
            if (wholelyInside(region))
            {
                return ImageUtil.toTransparentBufferedImage(
                    this.source.getSubimage(region.x, region.y, region.width, region.height));
            }

            // At the size the caller can use, not at the source's.  At zoomFraction = 0 the rectangle is about twice
            // the source in each direction, so its area is quadratic in the source and independent of the dialog's size
            // (a 4032 x 3024 phone photograph asked for about 134 MB, an 8000 x 6000 one 502 MB).  The OutOfMemoryError
            // landed in the OK listener on the event thread, so OK did nothing and the dialog stayed open.
            //
            // The shape is kept exactly, since the caller fits this into the icon and a changed ratio would stretch the
            // photograph.  Nothing is lost: getCroppedImage's own scale-down was already one bilinear step, done here
            // before the memory is spent.  The branch above needs no such bound: wholly inside, the rectangle is at
            // most the source.
            double shrink = Math.min(1.0, Math.min((double) this.outWidth / region.width,
                (double) this.outHeight / region.height));

            int width = Math.max(1, (int) Math.round(region.width * shrink));
            int height = Math.max(1, (int) Math.round(region.height * shrink));

            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = out.createGraphics();

            try
            {
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);

                // Offset so the photograph lands where the frame is standing over it; what falls outside is clipped by
                // the image's bounds and leaves the white showing.  The offset is in the rectangle's own coordinates,
                // so the scale goes on first and both it and the picture are shrunk by the same amount.
                g.scale(shrink, shrink);

                g.drawImage(this.source, -region.x, -region.y, null);
            }
            finally
            {
                g.dispose();
            }

            return out;
        }

        /** Fills a rectangle with the gray checkerboard that stands for "nothing here". */
        private void paintCheckerboard(Graphics2D g2, Rectangle area)
        {
            final int square = 10;

            g2.setColor(CHECKER_LIGHT);
            g2.fillRect(area.x, area.y, area.width, area.height);

            g2.setColor(CHECKER_DARK);

            for (int y = 0; y < area.height; y += square)
            {
                for (int x = (y / square) % 2 == 0 ? 0 : square; x < area.width; x += 2 * square)
                {
                    g2.fillRect(area.x + x, area.y + y, Math.min(square, area.width - x),
                        Math.min(square, area.height - y));
                }
            }
        }

        /**
         * Produces the icon the current view describes, cut and then scaled once, never twice.  A rectangle wholly
         * inside the picture is cut at its own resolution and resampled by `ImageUtil.getScaledImage` (as `getLocImage`
         * does); an overhanging one is composed and resampled in the same step by `Graphics2D`, so the `getScaledImage`
         * below it is an identity scale (`fit` is exactly 1).
         *
         * @return a new image of exactly the requested icon size, never null
         */
        public BufferedImage getCroppedImage()
        {
            Rectangle region = sourceRect();

            BufferedImage cut = contentOf(region);

            // Unchanged at the icon's own shape, deliberately: the common case keeps its transparency and gains no
            // border, where padding everything onto white would flatten the transparent icons the Central Station
            // itself supplies.
            if (isIconShaped() && wholelyInside(region))
            {
                return ImageUtil.getScaledImage(cut, this.outWidth, this.outHeight);
            }

            // The frame is a different shape from the icon, so the picture cannot fill it: fitted whole and centered
            // with the remainder white (Adam: "if the user adjusts the aspect of the frame, fill the rest of the
            // displayed icon with a white background").  Fitted rather than stretched, which would undo the shape
            // chosen.
            double fit = Math.min((double) this.outWidth / cut.getWidth(),
                (double) this.outHeight / cut.getHeight());

            int drawWidth = Math.max(1, (int) Math.round(cut.getWidth() * fit));
            int drawHeight = Math.max(1, (int) Math.round(cut.getHeight() * fit));

            BufferedImage scaled = ImageUtil.getScaledImage(cut, drawWidth, drawHeight);

            BufferedImage padded = new BufferedImage(this.outWidth, this.outHeight,
                BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = padded.createGraphics();

            try
            {
                // Painted rather than left transparent: the icon is drawn onto a colored button, so transparent padding
                // would show the button through.
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, this.outWidth, this.outHeight);

                g.drawImage(scaled, (this.outWidth - drawWidth) / 2,
                    (this.outHeight - drawHeight) / 2, null);
            }
            finally
            {
                g.dispose();
            }

            return padded;
        }

        /** The picture being cropped, as it was handed in, for a test that needs to know its size. */
        public java.awt.image.BufferedImage source()
        {
            return this.source;
        }

        /**
         * The rectangle of the SOURCE picture the current view describes, in source pixels.  Separate from the cut so
         * the clamp can be tested: a view that slid off the edge does not throw, it produces a SMALLER rectangle,
         * stretched to the icon size, so the only symptom is a locomotive that looks slightly wrong.
         *
         * @return a rectangle in source coordinates that may overhang the picture, never empty
         */
        public Rectangle sourceRect()
        {
            Rectangle window = cropWindow();

            double scale = getScale();

            int x = (int) Math.round(this.centerX - window.width / (2.0 * scale));
            int y = (int) Math.round(this.centerY - window.height / (2.0 * scale));
            int width = (int) Math.round(window.width / scale);
            int height = (int) Math.round(window.height / scale);

            // Not clamped into the picture: the frame may hang off the photograph and this rectangle is how far off;
            // flattening it back inside would silently become a smaller crop stretched to the icon.  getCroppedImage
            // checks before getSubimage, which throws on an outside rectangle, and composes instead.
            return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
        }
    }

    /** The window a crop window opens over and belongs to, or null. */
    static Window ownerOf(Component parent)
    {
        // A window is its own owner: the main window is what the crop mark and Set Local Locomotive Icon hand in, and
        // the modal crop window had no owner (MT-703).
        if (parent instanceof Window) return (Window) parent;

        return parent == null ? null : SwingUtilities.getWindowAncestor(parent);
    }
}
