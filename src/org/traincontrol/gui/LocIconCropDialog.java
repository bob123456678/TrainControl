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
    private static final double MAX_ZOOM = 32.0;

    /**
     * How far OUT the zoom goes, as a fraction of the picture fitting the panel.
     *
     * Adam: "now we can't zoom out beyond the image width to add white space. Add a 0.5x zoom
     * allowance."
     *
     * Zooming out to the point where the whole picture fits is not far enough, because the frame is
     * as large as the panel allows - so at that point the two are roughly the same size and there is
     * nowhere to put white without dragging the picture off to one side. Half again shrinks the
     * photograph well inside the frame, and the margin all the way round is white.
     *
     * It is a separate constant from the reshaping and the panning: those move where the frame is,
     * and this moves how big the picture is under it. Both can produce white and they are not the
     * same gesture.
     */
    private static final double MIN_ZOOM = 0.5;

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

        // OB-124.  A dialog does not reliably inherit its owner's icon, so it is asked for.
        TrainControlUI.applyWindowIcon(this);

        this.cropPanel = new CropPanel(source, outWidth, outHeight);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // WRAPPED, because a JLabel does not and this one is three sentences long.
        //
        // A plain JLabel reports whatever width its text needs on one line - 1247 pixels for this
        // text, measured - and pack() then makes the whole dialog that wide. Adam: "the window is
        // still too wide (wider than the main window)". The picture panel asks for 600 and was being
        // overruled by a sentence.
        //
        // An explicit width in the HTML rather than a fixed pixel size on the label: the text differs
        // in every language - German is reliably the longest - so what has to be pinned is how far it
        // runs before wrapping, not how tall it ends up being.
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
        return crop(parent, source, outWidth, outHeight, null);
    }

    /**
     * The same, opening on a remembered view and reporting back the one the user settled on (OB-125).
     *
     * One array in both directions because it is one thing - the view - and two arrays that could
     * describe different views would be a trap for whoever wired them up. It is read on the way in and
     * overwritten on the way out, and only when there is a result: cancelling must change nothing at
     * all, including what the caller is about to store.
     *
     * @param parent the component to centre on
     * @param source the picture to crop
     * @param outWidth width of the icon to produce
     * @param outHeight height of the icon to produce
     * @param view five numbers to open on, and where the view used is written back; may be null, and
     *        is ignored on the way in when it does not describe a view
     * @return the cropped icon, or null if the user cancelled
     */
    public static BufferedImage crop(Component parent, BufferedImage source, int outWidth,
        int outHeight, double[] view)
    {
        if (source == null) return null;

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);

        LocIconCropDialog dialog = new LocIconCropDialog(owner, I18n.t("loc.ui.cropTitle"), source,
            outWidth, outHeight);

        dialog.cropPanel.setView(view);

        dialog.setVisible(true);

        if (dialog.result != null) dialog.cropPanel.copyViewInto(view);

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
         * 0 = {@link #MIN_ZOOM} of the picture fitting the panel, 1 = {@link #MAX_ZOOM} times it.
         */
        private double zoomFraction = 0.0;

        /**
         * Whether the opening zoom has been chosen yet - see {@link #startAtCover}.
         */
        private boolean viewStarted = false;

        /**
         * A view handed in to open on, waiting for the panel to have a size (OB-125).
         *
         * Not applied when it arrives. The panel has no width until it is laid out, and every one of
         * these numbers is interpreted against the crop window, which is derived from that width - so
         * a view set on a panel of zero width clamps against nothing and is then replaced by the
         * opening view the moment the panel is measured. `setZoomFraction` carries a comment about
         * exactly that happening to a single number.
         *
         * Held here and taken up in {@link #startAtCover}, which is where the opening view is decided
         * and the first place the size is known.
         */
        private double[] pendingView = null;

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

        /**
         * The whole surface, and what the crop is composed onto.
         *
         * It was a dark grey, with the area outside the frame darkened further on top - and once the
         * frame was allowed to hang off the picture, those two produced exactly what Adam asked to be
         * rid of: black bars above and below a wide frame. There is nothing dark in this dialog now
         * except the frame itself and the text on the grip.
         *
         * White rather than a pale grey, because it is not a background: it is the colour the crop
         * takes wherever the frame is not over the photograph, so the panel has to be showing the
         * truth.
         */
        private static final Color PAPER = Color.WHITE;

        /**
         * What is outside the frame is faded, not darkened.
         *
         * The dimming still earns its place - it is how the user tells what is being kept from what
         * is being discarded, and how they know there is more photograph to drag towards. Doing it
         * with white instead of black keeps that and removes the bars: over the picture it reads as
         * "faded out", and over the paper it is invisible because there is nothing there to fade.
         */
        private static final Color VEIL = new Color(255, 255, 255, 165);
        private static final Color CHECKER_LIGHT = new Color(228, 228, 232);
        private static final Color CHECKER_DARK = new Color(200, 200, 206);
        /**
         * The frame and its furniture, dark so they are legible against white paper AND against a
         * photograph. A white outline was right over a dark backdrop and disappears over this one.
         */
        private static final Color FRAME = new Color(28, 30, 34);

        private static final Color GUIDE = new Color(0, 0, 0, 38);

        private static final Color GRIP_FILL = new Color(255, 255, 255, 235);

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

            setBackground(PAPER);
            setOpaque(true);

            // The only thing that says the picture can be dragged before the user tries it.  The
            // instruction line above says so in words, and words above a picture are read second.
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));

            // Wide enough to show the whole crop window at a useful size without being taller than a
            // laptop screen once the buttons and the instruction line are added underneath.
            setPreferredSize(new Dimension(600, 420));

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
                    CropPanel.this.startAtCover();
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
                //
                // The PRECISE rotation where there is one: a trackpad reports fractions of a notch,
                // and getWheelRotation() rounds those toward zero - so on a laptop a gentle scroll
                // reported 0 over and over and the zoom did not move at all.  That is the likeliest
                // reason this looked broken.
                double notches = e.getPreciseWheelRotation();

                if (notches == 0) notches = e.getWheelRotation();

                if (notches == 0) return;

                setZoomFraction(this.zoomFraction - notches * 0.05);

                if (this.zoomObserver != null) this.zoomObserver.zoomChanged(this.zoomFraction);

                // Consumed, so an ancestor scroll pane - this dialog has none today, but it is the
                // ordinary way a wheel event goes missing - cannot also act on it.
                e.consume();
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
         * Opens on a view taken from a previous crop, rather than on the covering centre one (OB-125).
         *
         * Ignored rather than rejected when it does not describe a view: the note it comes from is a
         * best-effort sidecar that anything may have written over, and the covering crop is a perfectly
         * good answer when it cannot be read.
         *
         * @param view five numbers as {@link #copyViewInto} writes them, or null for the default
         */
        public void setView(double[] view)
        {
            if (!viewIsUsable(view)) return;

            this.pendingView = view.clone();

            // So it is taken up the next time the panel is measured, whether or not an opening view
            // has already been chosen.
            this.viewStarted = false;
        }

        /**
         * The view as it stands, for storing against the crop about to be written.
         *
         * @param out five numbers: where in the source picture the middle of the crop window sits (x,
         *        y), the zoom fraction, and the shape and size of the window
         */
        public void copyViewInto(double[] out)
        {
            if (out == null || out.length < 5) return;

            out[0] = this.centerX;
            out[1] = this.centerY;
            out[2] = this.zoomFraction;
            out[3] = this.frameAspect;
            out[4] = this.frameSize;
        }

        /**
         * Whether five numbers describe a view this panel could open on.
         *
         * The centre is deliberately NOT range-checked against the picture: a view stored over a
         * photograph that has since been edited can name a point outside the one there now, and
         * `clampCenter` turns that into a nearby framing, which is a better answer than discarding
         * the whole view.
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

            // Zoom is a fraction; the two frame numbers are ratios and cannot be zero or negative
            // without making the crop window degenerate.
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

        /**
         * Puts the crop window back to the icon's own shape, filling the panel.
         */
        public void resetFrame()
        {
            this.frameAspect = (double) this.outWidth / this.outHeight;
            this.frameSize = 1.0;

            clampCenter();
        }

        /**
         * The scale at which the whole picture fits inside the panel.
         *
         * **It depends on the PANEL and the picture, and on nothing else.** That is the point of it.
         * It used to be derived from the crop window, so every reshape and every move of the frame
         * silently rescaled the photograph underneath - Adam: "resizing or moving it also scales the
         * background image. It should stay put unless zoomed."
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
         * The smallest the picture can be drawn - zoom 0, and {@link #MIN_ZOOM} of fitting the panel.
         *
         * It is a floor on how far OUT the zoom goes, not on what the crop may contain. The old floor
         * was the latter: the picture had to cover the frame, so below it the crop would have taken in
         * area that is not in the photograph and something would have had to be invented. That is now
         * a thing the user asks for on purpose, and what gets invented is white.
         *
         * @return panel pixels per source pixel at zoom 0
         */
        public double getMinScale()
        {
            return fitScale() * MIN_ZOOM;
        }

        /**
         * How far in the zoom goes, as a multiple of the whole picture fitting the panel.
         *
         * Raised from 8 at Adam's request - "add more zoomability" - and it can be raised again
         * without spoiling the control, because the slider is LOGARITHMIC: equal movements of it are
         * equal RATIOS rather than equal amounts. Extending either end therefore costs nothing in the
         * middle, which is where the dialog opens and where most of the adjusting happens.
         *
         * The span is MIN_ZOOM to MAX_ZOOM - half the picture fitting the panel, up to thirty-two
         * times it - so the slider covers a ratio of sixty-four from end to end.
         *
         * @return panel pixels per source pixel
         */
        public double getScale()
        {
            // The opening position is settled HERE, not only on a resize.
            //
            // It was only on a resize and on a paint, which is true of a dialog on a screen and false
            // of everything else - so anything asking this panel a question before it had been shown
            // got the zoomed-out view rather than the one it opens at. Its own tests found that: a
            // crop taken with nobody having touched anything came back letterboxed on white.
            //
            // Safe against recursion: startAtCover asks cropWindow and getMinScale, and neither of
            // those asks this.
            startAtCover();

            return getMinScale() * Math.pow(MAX_ZOOM / MIN_ZOOM, this.zoomFraction);
        }

        /**
         * Puts the zoom where the picture just covers the crop window, which is where this dialog
         * has always opened.
         *
         * Done once, when the panel first has a size, rather than in the constructor - the zoom is
         * expressed against the panel and there is no panel to express it against until then.
         *
         * Only the STARTING position depends on the frame. Everything after it is independent, which
         * is the whole of what changed here: opening on a centre crop that fills the icon is right,
         * and having the photograph jump every time the frame is nudged is not.
         */
        private void startAtCover()
        {
            if (this.viewStarted || getWidth() <= 0) return;

            this.viewStarted = true;

            // A REMEMBERED VIEW instead of the covering crop (OB-125).
            //
            // Shape and zoom first, then the centre: the window is derived from the shape and the
            // scale from the zoom, so clamping a centre before them clamps it against the wrong
            // rectangle.
            if (this.pendingView != null)
            {
                double[] view = this.pendingView;

                this.pendingView = null;

                this.frameAspect = view[3];
                this.frameSize = view[4];
                this.zoomFraction = view[2];
                this.centerX = view[0];
                this.centerY = view[1];

                // The photograph may not be the one this view was taken over - it is the user's file
                // and they may have edited or replaced it. Clamping is what makes that a slightly
                // wrong framing rather than a blank white rectangle.
                clampCenter();

                if (this.zoomObserver != null) this.zoomObserver.zoomChanged(this.zoomFraction);

                return;
            }

            Rectangle window = cropWindow();

            double floor = getMinScale();

            if (floor <= 0) return;

            double cover = Math.max((double) window.width / this.source.getWidth(),
                (double) window.height / this.source.getHeight());

            // Where the covering scale sits along the slider, which spans MIN_ZOOM to MAX_ZOOM and is
            // logarithmic - so this is a ratio of ratios rather than a proportion of a distance.
            // Below zero would mean the picture already covers the window at the smallest scale
            // offered, which the clamp below handles.
            double fraction = Math.log(cover / floor) / Math.log(MAX_ZOOM / MIN_ZOOM);

            this.zoomFraction = Math.max(0.0, Math.min(1.0, fraction));

            if (this.zoomObserver != null) this.zoomObserver.zoomChanged(this.zoomFraction);
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
            // The opening position is settled FIRST, so that what the caller asked for lands on top
            // of it rather than under it.
            //
            // Without this the order decided the outcome: a zoom set before anything had read the
            // panel was silently replaced by the opening value the moment something did, because that
            // is where startAtCover runs. Its own test caught it - the fixture asked for full
            // zoom-out and got the covering crop.
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

        /**
         * Returns the view to where it started: zoomed out, centred on the middle of the picture.
         *
         * The starting position is a centre crop, which is right far more often than any other single
         * guess - a photograph of a locomotive has the locomotive in the middle of it.
         */
        public void resetView()
        {
            // Back to how the dialog opened, which is the picture covering the frame - not to zoom 0,
            // which is now "the whole photograph visible" and would leave white down two sides of an
            // icon nobody asked to change.
            this.viewStarted = false;
            this.zoomFraction = 0.0;
            this.centerX = this.source.getWidth() / 2.0;
            this.centerY = this.source.getHeight() / 2.0;

            // The frame's shape too, because it is now something the user can get wrong and this is
            // the only way back to the icon's own shape without eyeballing it.
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
            // The picture may now hang off the frame, so this no longer forces the frame to be
            // covered - that rule is what stopped anybody spilling onto white, and Adam asked for
            // the spill.
            //
            // What is left is the one thing that has to stay true: some of the picture must remain
            // under the frame. Let it go entirely and the dialog shows a blank white rectangle and no
            // way back except Reset, with nothing on screen saying which direction the photograph
            // went.
            Rectangle window = cropWindow();

            double scale = getScale();

            if (scale <= 0) return;

            // A margin rather than a single pixel: a sliver of photograph at the very edge of the
            // frame is not enough to drag back by.
            double keep = Math.min(24.0, Math.min(window.width, window.height) / 3.0) / scale;

            double halfW = window.width / (2.0 * scale);
            double halfH = window.height / (2.0 * scale);

            // Derived rather than guessed at. The picture is drawn with its `centerX` under the
            // middle of the window, so its left edge sits at -centerX*scale from that middle and its
            // right edge srcW*scale further on. Requiring `keep` of overlap at each side gives:
            //
            //     centerX  >  keep - halfW              (the right edge stays inside the frame)
            //     centerX  <  srcW + halfW - keep       (the left edge does)
            this.centerX = Math.max(keep - halfW,
                Math.min(this.source.getWidth() + halfW - keep, this.centerX));

            this.centerY = Math.max(keep - halfH,
                Math.min(this.source.getHeight() + halfH - keep, this.centerY));
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

                // The white the frame can be pulled out onto is the whole panel now, painted above.
                // It used to be a rectangle around the picture over a dark backdrop, which meant the
                // backdrop showed at the edges of a big panel - and those were half of what Adam saw
                // as black bars.
                //
                // The checkerboard only where the PICTURE is, and only inside the window.  Locomotive
                // icons are routinely transparent PNGs, and a transparent area has to be
                // distinguishable from a pale part of the photograph - the user would otherwise find
                // out what they had cropped only after it was on the button.
                //
                // Not over the white: white is what a transparent pixel becomes out there, so a
                // checkerboard would be saying the opposite of what will happen.
                Rectangle transparent = window.intersection(picture);

                if (!transparent.isEmpty()) paintCheckerboard(g2, transparent);

                g2.drawImage(this.source, drawX, drawY, drawWidth, drawHeight, null);

                // Everything outside the window FADED rather than darkened.  What is being discarded
                // is exactly as informative as what is being kept - it is how the user knows there is
                // more picture to drag towards - so the dimming stays and only its colour changes.
                //
                // Over the photograph this reads as washed out; over the paper it does nothing at all,
                // because white over white is white. That is the whole trick: the bars are gone
                // without losing what they were for.
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

                // Dark, and two pixels of it.  The frame is the one thing in this dialog that must
                // never be ambiguous, and it now has to stay legible over three different things: the
                // photograph, the faded photograph, and white paper. A white line with a dark halo was
                // right when everything behind it was dark; over paper it is the halo doing all the
                // work, one pixel wide.
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

            g2.setColor(GRIP_FILL);
            g2.fillRoundRect(cx - half, cy - half, half * 2, half * 2, 7, 7);

            // An outline rather than a drop shadow.  The shadow was what separated it from a dark
            // backdrop; on white it would be the only dark smudge left in the dialog, and a border is
            // what actually makes a white control visible on white.
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
         * Whether a rectangle lies wholly within the picture.
         *
         * The question the transparency shortcut turns on: a crop that is entirely photograph can be
         * handed straight out and keeps whatever transparency the photograph had. One that reaches
         * past the edge cannot, because what is past the edge is white.
         *
         * @param region a rectangle in source coordinates
         * @return true when nothing outside the picture is included
         */
        private boolean wholelyInside(Rectangle region)
        {
            return region.x >= 0 && region.y >= 0
                && region.x + region.width <= this.source.getWidth()
                && region.y + region.height <= this.source.getHeight();
        }

        /**
         * What the frame actually covers, at the picture's own resolution.
         *
         * Where the rectangle lies inside the photograph this is the photograph. Where it reaches
         * past the edge - which the user can now ask for - the rest is white, because that is what
         * the dialog has been showing them under the frame while they dragged it there.
         *
         * Drawn rather than sub-imaged: getSubimage throws on a rectangle that is not wholly inside,
         * and clipping the rectangle to make it legal would quietly return a different crop.
         *
         * @param region the rectangle in source coordinates, possibly overhanging
         * @return an image of exactly that rectangle
         */
        private BufferedImage contentOf(Rectangle region)
        {
            if (wholelyInside(region))
            {
                return ImageUtil.toTransparentBufferedImage(
                    this.source.getSubimage(region.x, region.y, region.width, region.height));
            }

            BufferedImage out = new BufferedImage(region.width, region.height,
                BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = out.createGraphics();

            try
            {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, region.width, region.height);

                // Offset so the photograph lands where the frame is standing over it.  Anything that
                // falls outside is clipped by the image's own bounds and leaves the white showing.
                g.drawImage(this.source, -region.x, -region.y, null);
            }
            finally
            {
                g.dispose();
            }

            return out;
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

            BufferedImage cut = contentOf(region);

            // Unchanged at the icon's own shape, deliberately.
            //
            // That is what this dialog did before the frame could be reshaped, and it is still the
            // common case - so the common case keeps its transparency and gains no border. Padding
            // everything onto white would flatten the transparent icons the Central Station itself
            // supplies, for the benefit of a case the user has not asked for.
            if (isIconShaped() && wholelyInside(region))
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

            // NOT clamped into the picture any more.
            //
            // It used to be, and the comment here said why: rounding four quantities can put the last
            // row a pixel outside, and getSubimage throws rather than clipping. That reasoning still
            // holds for anything calling getSubimage - so getCroppedImage checks before it does, and
            // composes instead when the rectangle reaches past the edge.
            //
            // Clamping here would defeat the point. The frame is allowed to hang off the photograph
            // now, and this rectangle is how far off: flatten it back inside and the crop silently
            // becomes a different, smaller one, stretched to the icon - which is exactly the failure
            // this method's own javadoc warns about two paragraphs up.
            return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
        }
    }
}
