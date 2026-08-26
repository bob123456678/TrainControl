package org.traincontrol.gui;

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
import org.traincontrol.util.I18n;
import org.traincontrol.util.ImageUtil;

/**
 * FR-022 - lets the user say WHICH PART of a picture becomes a locomotive icon.
 *
 * Before this, picking a local icon meant the whole picture was used: getLocImageMaxHeight scales it
 * to the icon width and then shrinks it again if it is too tall, so a photograph taken in portrait,
 * or one with the locomotive small in the middle of a platform, arrived as a postage stamp of mostly
 * background.  Nothing was wrong with the scaling - the picture simply was not the shape of the slot
 * it had to go in, and the only person who can say which part of it matters is the person who took
 * it.
 *
 * Hand written rather than built in the form editor, for the reason buildPathPreferenceMenu gives:
 * the generated blocks belong to the GUI builder and hand edits to them do not survive the next time
 * somebody opens the form.  There is no .form file for this dialog and there must not be one.
 *
 * The interaction is the one every photo tool uses, and deliberately so - the crop WINDOW is fixed,
 * locked to the shape the icon is actually displayed at, and the picture moves underneath it.  The
 * alternative, a rectangle the user drags around on a fixed picture, has to be resized by its corners
 * to zoom, and the corners are exactly where an aspect lock has to fight the user.  Here zoom is a
 * slider and the wheel, panning is a drag, and neither can produce a crop of the wrong shape.
 *
 * Nothing here writes to disk and nothing here touches the user's file.  The dialog is handed a
 * decoded image and hands back a new one; the caller decides where it goes.
 *
 * @author Adam
 */
public class LocIconCropDialog extends JDialog
{
    /**
     * How far the picture may be enlarged beyond the point where it just fills the crop window.
     *
     * Eight is generous on purpose.  The case this feature exists for is a locomotive that occupies a
     * small part of a large photograph, and a limit that stops before the user has it filling the
     * window would leave the feature unable to do the one job it was asked for.  The picture goes
     * soft long before this, which is its own signal that the crop is too small - a number cannot
     * tell the user that as well as their own eyes can.
     */
    private static final double MAX_ZOOM = 8.0;

    /**
     * Margin, in pixels, between the crop window and the edge of the panel.
     *
     * Not decoration: it is the only place the surrounding picture can be seen, and the surrounding
     * picture is how the user knows which way to drag.  Without it the window fills the panel and
     * panning becomes guesswork.
     */
    private static final int WINDOW_MARGIN = 44;

    /**
     * The result, or null.  Read once, after the modal dialog returns.
     */
    private BufferedImage result = null;

    private final CropPanel cropPanel;

    /**
     * Builds the dialog.  Private - callers go through {@link #crop}, which owns the modal
     * show-and-read sequence that makes the result meaningful.
     *
     * @param owner the window to centre on and block, may be null
     * @param title the dialog title, already localised
     * @param source the picture to crop
     * @param outWidth width of the icon that will be produced
     * @param outHeight height of the icon that will be produced
     */
    private LocIconCropDialog(Window owner, String title, BufferedImage source, int outWidth,
        int outHeight)
    {
        super(owner, title, ModalityType.APPLICATION_MODAL);

        this.cropPanel = new CropPanel(source, outWidth, outHeight);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel instructions = new JLabel(I18n.t("loc.ui.cropInstructions"));
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 2));
        content.add(instructions, BorderLayout.NORTH);

        content.add(this.cropPanel, BorderLayout.CENTER);

        // Zoom on its own row above the buttons.  Beside them it reads as a third button and gets
        // clicked by somebody looking for OK.
        JPanel controls = new JPanel(new BorderLayout());
        controls.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel zoomRow = new JPanel(new BorderLayout(8, 0));
        zoomRow.add(new JLabel(I18n.t("loc.ui.cropZoom")), BorderLayout.WEST);

        // Integer ticks over a continuous zoom: the slider's own resolution is the only quantisation,
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

        // The slider drives the panel, and the panel drives the slider back when the wheel is used.
        // The guard flag is what keeps that from looping: setValue fires the change listener, which
        // would set the zoom again, which would move the picture a second time for one wheel notch.
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
            this.cropPanel.resetView();
            zoom.setValue(0);
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

        // Escape cancels, matching every other dialog in this application.  A crop dialog that can
        // only be dismissed by finding the right button is a trap on the way to "I did not want this".
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

        // Closing the window is a cancel - result is only ever assigned by OK - and DISPOSE rather
        // than the JDialog default of HIDE, which would leave the native window behind every time an
        // icon was set.
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        pack();

        // Resizable, because a bigger window is a bigger crop window and somebody working on a large
        // photograph will want one - but not smaller than it packs to.  Below that the buttons start
        // to be cut off, which is the complaint OB-107 raised about another window in this
        // application.
        setMinimumSize(getSize());

        setLocationRelativeTo(owner);
    }

    /**
     * Shows the crop dialog and returns what the user chose.
     *
     * Must be called on the event dispatch thread; it blocks there until the dialog closes, which is
     * what makes the return value meaningful rather than a race.
     *
     * @param parent the component the dialog should centre on, may be null
     * @param source the picture to crop, must not be null
     * @param outWidth width of the icon to produce
     * @param outHeight height of the icon to produce
     * @return the cropped icon, or null if the user cancelled - which the caller must read as
     *         "leave everything exactly as it was", not as a failure
     */
    public static BufferedImage crop(Component parent, BufferedImage source, int outWidth,
        int outHeight)
    {
        if (source == null) return null;

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);

        LocIconCropDialog dialog = new LocIconCropDialog(owner, I18n.t("loc.ui.cropTitle"), source,
            outWidth, outHeight);

        dialog.setVisible(true);

        return dialog.result;
    }

    /**
     * Told the zoom level whenever the panel changes it by itself, so the slider can follow.
     */
    public interface ZoomObserver
    {
        /**
         * @param fraction the new zoom, 0 (the whole crop window just filled) to 1 (fully zoomed in)
         */
        void zoomChanged(double fraction);
    }

    /**
     * The picture, the crop window over it, and the mouse handling that moves one against the other.
     *
     * Public and separable from the dialog on purpose: it can be constructed, given a size and
     * painted into a BufferedImage with no display attached, which is how its appearance was checked
     * before it was ever shown to anybody.
     */
    public static class CropPanel extends JPanel
    {
        private final BufferedImage source;

        private final int outWidth;
        private final int outHeight;

        /**
         * The view is held as "which point of the SOURCE picture is under the middle of the crop
         * window, and how far in are we", rather than as a scale and an offset in panel pixels.
         *
         * That matters when the panel is resized, which happens every time the dialog is: an offset
         * measured in panel pixels means something different afterwards, so the crop would drift on
         * a resize.  A point in the picture means the same thing at any panel size.
         */
        private double centerX;
        private double centerY;

        /**
         * 0 = the picture just fills the crop window, 1 = {@link #MAX_ZOOM} times that.
         */
        private double zoomFraction = 0.0;

        /**
         * The shape of the crop window, as width over height.
         *
         * Starts at the icon's own shape, which is what it used to be locked to. The user can pull an
         * edge or a corner to change it, and then the picture no longer fills the icon - what is left
         * over is filled with white by {@link #getCroppedImage}.
         *
         * Held as a RATIO rather than as two pixel counts because the panel is resizable, and a shape
         * means the same thing at any panel size where a pair of pixel counts does not.
         */
        private double frameAspect;

        /**
         * How big the crop window is, as a fraction of the largest window of its shape that fits.
         *
         * 1.0 - the default, and what it always was - is a window that touches the available area on
         * two sides. Pulling an edge inward makes this smaller; pulling it outward grows the shape
         * instead, because there is nowhere further to go.
         */
        private double frameSize = 1.0;

        /**
         * Which part of the window the current drag grabbed, or null when the drag is a pan.
         */
        private String dragEdge = null;

        /**
         * How close to an edge counts as grabbing it, in panel pixels.
         *
         * Generous, because the alternative to hitting it is panning the picture by accident - and a
         * mis-grab that pans is much cheaper to undo than one that reshapes the frame.
         */
        private static final int GRIP = 10;

        /**
         * Half the side of the square in the middle that pans the picture.
         *
         * Adam asked for this: "drag frame, but add a control in the middle to move it around, so the
         * aspect change becomes deliberate." Before, dragging anywhere panned; adding edge dragging on
         * top of that would have made every drag near an edge ambiguous. Separating the two by WHERE
         * you take hold is what makes reshaping something you meant to do.
         */
        private static final int PAN_GRIP = 26;

        private ZoomObserver zoomObserver = null;

        private boolean syncingZoom = false;

        private int dragFromX = 0;
        private int dragFromY = 0;

        private static final Color BACKDROP = new Color(52, 54, 58);
        private static final Color SHADE = new Color(0, 0, 0, 150);
        private static final Color CHECKER_LIGHT = new Color(228, 228, 232);
        private static final Color CHECKER_DARK = new Color(200, 200, 206);
        private static final Color WINDOW_EDGE = new Color(255, 255, 255);
        private static final Color WINDOW_SHADOW = new Color(0, 0, 0, 160);
        private static final Color WINDOW_GUIDE = new Color(255, 255, 255, 90);

        /**
         * @param source the picture to crop, must not be null
         * @param outWidth width of the icon that will be produced - only its RATIO to outHeight is
         *        used here, and it is what locks the shape of the crop window
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

            setBackground(BACKDROP);
            setOpaque(true);

            // The only thing that says the picture can be dragged before the user tries it.  The
            // instruction line above says so in words, and words above a picture are read second.
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));

            // Wide enough to show the whole crop window at a useful size without being taller than a
            // laptop screen once the buttons and the instruction line are added underneath.
            setPreferredSize(new Dimension(720, 420));

            addComponentListener(new java.awt.event.ComponentAdapter()
            {
                /**
                 * The crop window is derived from the panel size, so resizing the dialog changes
                 * both the window and the scale that just covers it.  Nothing else re-checks that
                 * the window is still inside the picture, and a resize can push it out - the crop
                 * would then include area that is not in the photograph.
                 * @param e the resize
                 */
                @Override
                public void componentResized(java.awt.event.ComponentEvent e)
                {
                    CropPanel.this.clampCenter();
                    CropPanel.this.repaint();
                }
            });

            addMouseListener(new MouseAdapter()
            {
                /**
                 * Decided once, when the button goes down, and held for the whole drag.
                 *
                 * Asking again on every drag event would mean the gesture could change halfway
                 * through: reshape the frame until its edge slides under the pointer, and the rest of
                 * the same drag becomes a pan.
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

                /**
                 * The cursor is the only thing that says an edge can be pulled before somebody tries.
                 */
                @Override
                public void mouseMoved(MouseEvent e)
                {
                    setCursor(java.awt.Cursor.getPredefinedCursor(
                        CropPanel.cursorFor(CropPanel.this.grabAt(e.getX(), e.getY()))));
                }
            });

            addMouseWheelListener((MouseWheelEvent e) ->
            {
                // One notch is a twentieth of the whole range, so the full span is reachable in a few
                // flicks but a single notch is still a small adjustment.  Negative rotation is
                // "away from the user", which every other program treats as zooming in.
                setZoomFraction(this.zoomFraction - e.getWheelRotation() * 0.05);

                if (this.zoomObserver != null) this.zoomObserver.zoomChanged(this.zoomFraction);
            });
        }

        /**
         * The pointer shape for whatever is under it.
         *
         * @param grabbed what grabAt returned
         * @return a java.awt.Cursor constant
         */
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
         * Marks a slider update as originating from the panel, so the slider's change listener knows
         * not to send it straight back.
         * @param syncing whether a push is in progress
         */
        public void setSyncingZoom(boolean syncing)
        {
            this.syncingZoom = syncing;
        }

        /**
         * The crop window, in panel coordinates: centred, inset by {@link #WINDOW_MARGIN}, and locked
         * to the ratio the locomotive icon is displayed at.
         *
         * Recomputed on every use rather than cached, because it depends on the panel size and the
         * panel is resizable.  It is a handful of arithmetic against a repaint that scales an image.
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
         * The largest window of a given shape that fits inside the panel's margins.
         *
         * This is what cropWindow was, with the shape as a parameter rather than fixed to the icon's.
         * Kept separate because the resize needs it too: pulling an edge outward past the edge of the
         * panel has to stop somewhere, and this is where.
         *
         * @param aspect width over height
         * @return the window at that shape, centred, never empty
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
         * Whether the crop window is still the shape the icon is drawn at.
         *
         * The whole of what changes downstream hangs off this: at the icon's own shape the picture
         * fills the icon exactly and nothing is added, which is what this dialog has always done. Once
         * it differs, the picture is fitted inside the icon and the rest is white.
         *
         * A tolerance rather than an equality test, because the shape arrives through pixel arithmetic
         * on a resizable panel and lands a thousandth away from where it started.
         *
         * @return true when no white will be added
         */
        public boolean isIconShaped()
        {
            return Math.abs(this.frameAspect - (double) this.outWidth / this.outHeight) < 0.002;
        }

        /**
         * Which part of the crop window is under a point.
         *
         * @param x panel x
         * @param y panel y
         * @return "pan" for the control in the middle, otherwise the edges being grabbed as some
         *         combination of N, S, E and W, or null for anywhere else
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
         * Reshapes the crop window because an edge was dragged to a point.
         *
         * The window stays CENTRED and grows or shrinks symmetrically, so pulling the right edge moves
         * the left one too. That is the behaviour a centred frame has to have - the alternative is the
         * frame wandering off the middle of the panel, and the middle is where the picture under it
         * is being judged.
         *
         * @param edge which edges are being dragged, from grabAt
         * @param x panel x of the pointer
         * @param y panel y of the pointer
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

            // A floor rather than a clamp to the panel: a window a few pixels across is not a crop,
            // and one that can reach zero divides by it.
            width = Math.max(2 * GRIP + 2 * PAN_GRIP, width);
            height = Math.max(2 * GRIP + 2 * PAN_GRIP, height);

            this.frameAspect = width / height;

            Rectangle biggest = largestWindow(this.frameAspect);

            // Measured against the biggest window of the NEW shape, so pulling outward stops at the
            // panel edge instead of pretending the window is larger than it is drawn.
            this.frameSize = Math.max(0.05, Math.min(1.0,
                biggest.width == 0 ? 1.0 : width / biggest.width));

            clampCenter();

            repaint();
        }

        /**
         * Puts the crop window back to the icon's own shape, filling the panel.
         */
        /**
         * Sets the shape of the crop window directly.
         *
         * The dialog reshapes it by dragging an edge; this is the same change without a pointer, which
         * is what makes the arithmetic underneath testable and is how the padding is checked.
         *
         * @param aspect width over height; zero and negatives are ignored rather than throwing,
         *        because they can only arrive from arithmetic that has already gone wrong elsewhere
         */
        public void setFrameAspect(double aspect)
        {
            if (aspect <= 0 || Double.isNaN(aspect)) return;

            this.frameAspect = aspect;

            // The window is the largest of its shape that fits, which is what a drag to the edge of
            // the panel would have produced. The drag path sets a size of its own; this one has no
            // pointer to take it from.
            this.frameSize = 1.0;

            clampCenter();

            repaint();
        }

        /**
         * The shape of the crop window.
         * @return width over height
         */
        public double getFrameAspect()
        {
            return this.frameAspect;
        }

        public void resetFrame()
        {
            this.frameAspect = (double) this.outWidth / this.outHeight;
            this.frameSize = 1.0;

            clampCenter();
        }

        /**
         * The scale at which the picture exactly covers the crop window - the zoomed-all-the-way-out
         * position, and the floor for every other scale.
         *
         * It is a floor rather than a starting point: below it the crop would include area that is
         * not in the picture at all, which would have to be filled with something invented.  The user
         * asked to choose part of their photograph, not to be given a border around it.
         *
         * @return panel pixels per source pixel at zoom 0
         */
        public double getMinScale()
        {
            Rectangle window = cropWindow();

            return Math.max((double) window.width / this.source.getWidth(),
                (double) window.height / this.source.getHeight());
        }

        /**
         * The scale currently in force.
         * @return panel pixels per source pixel
         */
        public double getScale()
        {
            return getMinScale() * Math.pow(MAX_ZOOM, this.zoomFraction);
        }

        /**
         * Sets the zoom, clamping it into range and keeping the middle of the crop window over the
         * same part of the picture.
         *
         * Zooming about the window centre rather than the mouse pointer: the centre is where the
         * subject is once the user has framed it, and it is the only fixed point that behaves the
         * same whether the zoom came from the wheel, the slider or the reset button.
         *
         * @param fraction 0 (whole window filled) to 1 (maximum enlargement); out of range values are
         *        clamped rather than rejected, because the wheel routinely asks for them
         */
        public void setZoomFraction(double fraction)
        {
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

        /**
         * Returns the view to where it started: zoomed out, centred on the middle of the picture.
         *
         * The starting position is a centre crop, which is right far more often than any other single
         * guess - a photograph of a locomotive has the locomotive in the middle of it.
         */
        public void resetView()
        {
            this.zoomFraction = 0.0;
            this.centerX = this.source.getWidth() / 2.0;
            this.centerY = this.source.getHeight() / 2.0;

            // The frame's shape too, because it is now something the user can get wrong and this is
            // the only way back to the icon's own shape without eyeballing it.
            resetFrame();

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

            // Dividing by the scale is what makes the drag track the pointer: at four times
            // magnification the picture must move a quarter as far in its own pixels to keep up.
            this.centerX -= dx / scale;
            this.centerY -= dy / scale;

            clampCenter();

            repaint();
        }

        /**
         * Pulls the view back inside the picture.
         *
         * Called after every change, because pan and zoom can each push the crop window off the edge
         * and the rule that the window is always fully covered is the one thing that makes the result
         * predictable.  Where the picture is smaller than the window in a dimension - which the scale
         * floor prevents, but rounding can produce at the boundary - the picture is centred instead
         * of clamped, so the failure is a centred crop rather than a jump to one edge.
         */
        private void clampCenter()
        {
            Rectangle window = cropWindow();

            double scale = getScale();

            double halfWidth = window.width / (2.0 * scale);
            double halfHeight = window.height / (2.0 * scale);

            if (halfWidth * 2 >= this.source.getWidth())
            {
                this.centerX = this.source.getWidth() / 2.0;
            }
            else
            {
                this.centerX = Math.max(halfWidth,
                    Math.min(this.source.getWidth() - halfWidth, this.centerX));
            }

            if (halfHeight * 2 >= this.source.getHeight())
            {
                this.centerY = this.source.getHeight() / 2.0;
            }
            else
            {
                this.centerY = Math.max(halfHeight,
                    Math.min(this.source.getHeight() - halfHeight, this.centerY));
            }
        }

        /**
         * Paints the backdrop, the picture, and the crop window over it.
         * @param g the graphics to paint into
         */
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

                g2.setColor(BACKDROP);
                g2.fillRect(0, 0, getWidth(), getHeight());

                Rectangle window = cropWindow();

                // A checkerboard only inside the window, and only under the picture.  Locomotive
                // icons are routinely transparent PNGs, and against the flat backdrop a transparent
                // area is indistinguishable from a dark part of the photograph - the user would find
                // out what they had cropped only after it was on the button.
                paintCheckerboard(g2, window);

                double scale = getScale();

                int drawWidth = (int) Math.round(this.source.getWidth() * scale);
                int drawHeight = (int) Math.round(this.source.getHeight() * scale);

                int drawX = (int) Math.round(window.getCenterX() - this.centerX * scale);
                int drawY = (int) Math.round(window.getCenterY() - this.centerY * scale);

                g2.drawImage(this.source, drawX, drawY, drawWidth, drawHeight, null);

                // Everything outside the window darkened rather than hidden.  What is being discarded
                // is exactly as informative as what is being kept - it is how the user knows there is
                // more picture to drag towards.
                g2.setColor(SHADE);
                g2.fillRect(0, 0, getWidth(), window.y);
                g2.fillRect(0, window.y + window.height, getWidth(),
                    getHeight() - window.y - window.height);
                g2.fillRect(0, window.y, window.x, window.height);
                g2.fillRect(window.x + window.width, window.y, getWidth() - window.x - window.width,
                    window.height);

                g2.setColor(WINDOW_GUIDE);

                for (int third = 1; third <= 2; third++)
                {
                    int x = window.x + window.width * third / 3;
                    int y = window.y + window.height * third / 3;

                    g2.drawLine(x, window.y, x, window.y + window.height);
                    g2.drawLine(window.x, y, window.x + window.width, y);
                }

                // White inside, dark immediately outside it.  A single white line disappears against
                // a pale sky, which is the top half of most photographs of a locomotive, and the
                // frame is the one thing in this dialog that must never be ambiguous.
                g2.setColor(WINDOW_SHADOW);
                g2.drawRect(window.x - 1, window.y - 1, window.width + 1, window.height + 1);

                g2.setColor(WINDOW_EDGE);
                g2.drawRect(window.x, window.y, window.width - 1, window.height - 1);

                paintHandles(g2, window);

                paintPanGrip(g2, window);
            }
            finally
            {
                g2.dispose();
            }
        }

        /**
         * The eight blocks on the frame's edges and corners that can be pulled.
         *
         * Drawn because an invisible affordance is not one. The frame was fixed in shape until now, so
         * nobody has any reason to try dragging it, and the cursor only says so once the pointer is
         * already there.
         *
         * @param g2 the graphics to paint into
         * @param window the crop window
         */
        private void paintHandles(Graphics2D g2, Rectangle window)
        {
            final int arm = 14;
            final int thick = 3;

            g2.setColor(WINDOW_EDGE);

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
         * The control in the middle that moves the picture.
         *
         * Adam: "add a control in the middle to move it around, so the aspect change becomes
         * deliberate." Dragging anywhere else inside the frame still pans, so nothing anybody already
         * knows how to do stops working - this is what makes panning findable now that the edges mean
         * something different.
         *
         * A four-way arrow, because that is what it does and it needs no words in eight languages.
         *
         * @param g2 the graphics to paint into
         * @param window the crop window
         */
        private void paintPanGrip(Graphics2D g2, Rectangle window)
        {
            int cx = (int) window.getCenterX();
            int cy = (int) window.getCenterY();

            int half = PAN_GRIP - 8;

            g2.setColor(WINDOW_SHADOW);
            g2.fillRoundRect(cx - half - 1, cy - half - 1, half * 2 + 3, half * 2 + 3, 8, 8);

            g2.setColor(new Color(255, 255, 255, 210));
            g2.fillRoundRect(cx - half, cy - half, half * 2, half * 2, 7, 7);

            g2.setColor(new Color(40, 42, 46));

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
         * Fills a rectangle with the grey checkerboard that stands for "nothing here".
         * @param g2 the graphics to paint into
         * @param area the rectangle to fill
         */
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
         * Produces the icon the current view describes.
         *
         * Cut at the source picture's own resolution and then scaled once, through the same helpers
         * getLocImage uses, so a cropped icon and a whole-picture icon are resampled identically and
         * cannot look like they came from different programs.
         *
         * @return a new image of exactly the requested icon size, never null
         */
        public BufferedImage getCroppedImage()
        {
            Rectangle region = sourceRect();

            BufferedImage cut = ImageUtil.toTransparentBufferedImage(
                this.source.getSubimage(region.x, region.y, region.width, region.height));

            // Unchanged at the icon's own shape, deliberately.
            //
            // That is what this dialog did before the frame could be reshaped, and it is still the
            // common case - so the common case keeps its transparency and gains no border. Padding
            // everything onto white would flatten the transparent icons the Central Station itself
            // supplies, for the benefit of a case the user has not asked for.
            if (isIconShaped())
            {
                return ImageUtil.getScaledImage(cut, this.outWidth, this.outHeight);
            }

            // The frame is a different shape from the icon, so the picture cannot fill it. Fitted
            // whole and centred, with the remainder white (Adam: "if the user adjusts the aspect of
            // the frame, fill the rest of the displayed icon with a white background").
            //
            // Fitted rather than stretched: the point of choosing a shape is to keep what is inside it
            // looking right, and stretching it back to the icon's shape would undo exactly that.
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
                // Painted rather than left transparent. White is what was asked for, and it is also
                // the honest answer: the icon is drawn onto a coloured button, so transparent padding
                // would show the button through and read as the crop having gone wrong.
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

        /**
         * The picture being cropped, for a test that needs to know its size.
         *
         * @return the source image, exactly as it was handed in
         */
        public java.awt.image.BufferedImage source()
        {
            return this.source;
        }

        /**
         * The rectangle of the SOURCE picture the current view describes, in source pixels.
         *
         * Separated from the cut itself so that what the view amounts to can be examined without
         * producing an image - which is what makes the clamp testable, and the clamp is the part of
         * this that fails silently. A view that has slid off the edge does not throw and does not
         * produce a blank: it produces a SMALLER rectangle, which is then stretched to the icon size,
         * so the only visible symptom is a locomotive that looks slightly wrong.
         *
         * @return a rectangle wholly inside the source picture, never empty
         */
        public Rectangle sourceRect()
        {
            Rectangle window = cropWindow();

            double scale = getScale();

            int x = (int) Math.round(this.centerX - window.width / (2.0 * scale));
            int y = (int) Math.round(this.centerY - window.height / (2.0 * scale));
            int width = (int) Math.round(window.width / scale);
            int height = (int) Math.round(window.height / scale);

            // Rounding four independent quantities can put the last row or column a pixel outside the
            // picture, and getSubimage throws rather than clipping.  Clamped here, where losing a
            // pixel is invisible, instead of at the top of a stack trace the user cannot act on.
            x = Math.max(0, Math.min(this.source.getWidth() - 1, x));
            y = Math.max(0, Math.min(this.source.getHeight() - 1, y));
            width = Math.max(1, Math.min(this.source.getWidth() - x, width));
            height = Math.max(1, Math.min(this.source.getHeight() - y, height));

            return new Rectangle(x, y, width, height);
        }
    }
}
