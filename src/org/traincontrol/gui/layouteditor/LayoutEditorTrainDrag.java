package org.traincontrol.gui.layouteditor;

import java.awt.Container;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;

/**
 * Dragging a train drawn on a station to another station in the autonomy editor (FR-160).  This only watches the
 * pointer and marks the target; the move is `AutonomyEditorPanel.dropTrain`, the door the picker places through.
 * Everything runs on the event thread.
 */
final class LayoutEditorTrainDrag
{
    private final LayoutEditor editor;

    /** The square the train stands on, or null when no press began on a train. */
    private TileKey from;

    private String train;

    /** The square pressed, whose click the end of a drag must not turn into a selection. */
    private LayoutLabel pressedOn;

    /** Where the press was, in the diagram's coordinates. */
    private Point pressedAt;

    /** Whether the pointer has moved far enough for this to be a drag rather than a click. */
    private boolean carrying;

    LayoutEditorTrainDrag(LayoutEditor editor)
    {
        this.editor = editor;
    }

    /**
     * A left press: begins a possible drag when it lands on a drawn train, and not while a run holds the setup.
     * @return whether a drag may follow
     */
    boolean press(MouseEvent e)
    {
        // A drag the last press never finished is put down first.
        finish();

        reset();

        AutonomyEditorPanel panel = editor.getAutonomyPanel();

        // A tool waiting for a click takes a click on a train, and a press on one starts no drag
        if (panel == null || panel.isAToolArmed() || panel.setupIsHeldByARun() || editor.grid == null) return false;

        LayoutLabel square = editor.trainStandingUnder(e);

        TileKey tile = tileOf(square);

        String standing = tile == null ? null : panel.locomotiveAt(tile);

        if (standing == null) return false;

        from = tile;
        train = standing;
        pressedOn = e.getComponent() instanceof LayoutLabel ? (LayoutLabel) e.getComponent() : square;
        pressedAt = javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(),
            editor.grid.getContainer());

        return true;
    }

    /** The pointer moved with the button down: carries the train, and marks the square it would land on. */
    void move(MouseEvent e)
    {
        if (from == null) return;

        Container squares = editor.grid.getContainer();

        Point at = javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), squares);

        if (!carrying)
        {
            if (pressedAt.distance(at) < LayoutGrid.DRAG_SLOP) return;

            carrying = true;

            java.awt.image.BufferedImage shot = picture(train);

            editor.showCaptionGhost(shot, shot.getWidth() / 2, shot.getHeight() / 2);
        }

        editor.moveCaptionGhost(javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(),
            editor.getLayeredPane()));

        LayoutLabel over = LayoutGrid.squareUnder(squares, e.getComponent(), e);

        TileKey to = tileOf(over);

        boolean allowed = editor.getAutonomyPanel().canDropTrain(from, to);

        // The square it came from is not a target: letting go there changes nothing.
        editor.showCaptionDropTarget(to == null || to.equals(from) ? null : over, allowed);

        squares.setCursor(allowed ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : null);
    }

    /** The button went up: a train carried onto a square that takes it moves there, else nothing happens. */
    void release(MouseEvent e)
    {
        if (from == null) return;

        TileKey source = from;
        boolean wasCarrying = carrying;

        LayoutLabel over = wasCarrying
            ? LayoutGrid.squareUnder(editor.grid.getContainer(), e.getComponent(), e) : null;

        finish();

        if (!wasCarrying) return;

        TileKey to = tileOf(over);

        if (to != null) editor.getAutonomyPanel().dropTrain(source, to);
    }

    /**
     * Escape: puts a train being carried back where it was.
     * @return whether a drag was under way, so Escape has done its work
     */
    boolean cancel()
    {
        if (from == null || !carrying) return false;

        finish();

        return true;
    }

    /** Puts down whatever is carried or pressed, for a window that closes or changes page. */
    void abandon()
    {
        finish();

        reset();
    }

    private void reset()
    {
        from = null;
        train = null;
        pressedOn = null;
        pressedAt = null;
        carrying = false;
    }

    /** Takes the ghost, the mark and the pointer back, and keeps the click that ends the press from acting. */
    private void finish()
    {
        LayoutLabel pressed = pressedOn;

        boolean wasCarrying = carrying;

        reset();

        if (!wasCarrying) return;

        editor.hideCaptionGhost();
        editor.clearCaptionDropTarget();

        if (editor.grid != null && editor.grid.getContainer() != null) editor.grid.getContainer().setCursor(null);

        editor.ignoreTheClickOf(pressed);
    }

    private TileKey tileOf(LayoutLabel label)
    {
        if (label == null || editor.layout == null) return null;

        int x = editor.getGridX(label);
        int y = editor.getGridY(label);

        return x < 0 || y < 0 ? null : new TileKey(editor.layout.getName(), x, y);
    }

    /** The name on a small pill, the picture that follows the pointer. */
    private static java.awt.image.BufferedImage picture(String name)
    {
        java.awt.Font font = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 13);

        java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(1, 1,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D measure = probe.createGraphics();

        java.awt.FontMetrics metrics = measure.getFontMetrics(font);

        measure.dispose();

        int width = metrics.stringWidth(name) + 16;
        int height = metrics.getHeight() + 8;

        java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(40, 40, 40, 210));
            g.fillRoundRect(0, 0, width - 1, height - 1, 10, 10);
            g.setColor(java.awt.Color.WHITE);
            g.setFont(font);
            g.drawString(name, 8, 4 + metrics.getAscent());
        }
        finally
        {
            g.dispose();
        }

        return shot;
    }
}
