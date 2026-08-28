package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.util.I18n;

/**
 * Track diagram editor
 * @author Adam
 */
public class LayoutEditor extends PositionAwareJFrame
{
    public static enum tool {MOVE, COPY};
    public static enum bulk {ROW, COL};

    // Max rows or columns
    public static final int MAX_SIZE = 60;
    
    private final TrainControlUI parent;
    private final int size;
    /**
     * The page being edited.
     *
     * NOT final since 2026-08-22, when switching pages stopped closing the window (OB-005). It is read
     * live everywhere, including from the listeners the grid installs, so a switch is a matter of
     * pointing this at the new page and redrawing - but anything CACHED from it has to be rebuilt in
     * the same breath, which is what arriveAt does and why nothing else may assign to this.
     */
    private LayoutDiagram layout;
    private LayoutGrid grid;
    
    private int lastX = -1;
    private int lastY = -1;
    private LayoutDiagramComponent lastComponent = null;
    private tool toolFlag = null;
    
    private int lastHoveredX = -1;
    private int lastHoveredY = -1;
    //private LayoutLabel lastHoveredLabel = null;
    
    // Floating ghost for drag and drop
    private JWindow dragWindow; 
    private JLabel ghostLabel;
    
    // Default size of new layouts
    public static final int DEFAULT_NEW_SIZE_ROWS = 16;
    public static final int DEFAULT_NEW_SIZE_COLS = 21;
    
    // New tile borders
    private static final int NEW_COMPONENT_BORDER_WIDTH = 2;
    private static final Color NEW_COMPONENT_BORDER_ACTIVE_COLOR = Color.RED;
    
    // Layout tile borders
    private static final int COMPONENT_BORDER_WIDTH = 1;
    private static final Color COMPONENT_BORDER_COPIED_COLOR = Color.RED;
    private static final Color COMPONENT_BORDER_HOVERED_COLOR = Color.BLUE;
    private static final Color COMPONENT_BORDER_DEFAULT_COLOR = Color.LIGHT_GRAY;

    /**
     * The colour a picked square is outlined in.
     *
     * Red, and the strongest line on the diagram.  It was a mid green, which is a quiet colour on a
     * page of track already drawn in greens and greys - so on a busy diagram the edge of a selection
     * had to be looked for, and the whole point of a selection is knowing at a glance what the next
     * key press is about to happen to.
     */
    private static final Color COMPONENT_BORDER_SELECTED_COLOR = new Color(210, 0, 0);

    /**
     * Where a group being dragged would land, in a paler shade of the picking colour.
     *
     * A different COLOUR, not a paler shade of the selection.  The squares a group currently occupies
     * and the squares it would move to are both worth seeing at once, and two reds differing only in
     * strength read as one thing drawn twice - which is exactly how a pale red landing box looked
     * beside a red selection.  Blue says "somewhere else" at a glance and needs no comparison.
     *
     * This is the answer to "where will this end up", which is the question a group drag raises and a
     * single-tile drag does not - one tile follows the cursor and can be seen, twenty cannot.
     */
    private static final Color COMPONENT_BORDER_LANDING_COLOR = new Color(0, 90, 220);

    /**
     * The wash over the grip: the yellow this application already uses to say "look here", the same
     * one the autonomy editor flashes a tested path in.
     */
    private static final Color HANDLE_FILL = new Color(255, 214, 0);

    /**
     * The grip's mark: a yellow wash over the square and a four-way arrow on top of it.
     *
     * A Border rather than anything else, because a border is the only thing painted AFTER the
     * component itself - and the square underneath is a piece of track drawn as an opaque image, so
     * anything painted before it is simply not there.  It also reserves no space, which is what the
     * thick line it replaces got wrong.
     *
     * The four-way arrow because that is what a grip looks like everywhere else, and because it says
     * what the yellow alone cannot: not "this square is interesting" but "take hold here and move".
     */
    private static final class SelectionGrip implements javax.swing.border.Border
    {
        @Override
        public boolean isBorderOpaque()
        {
            return false;
        }

        @Override
        public java.awt.Insets getBorderInsets(java.awt.Component on)
        {
            // Nothing: this draws over the tile rather than around it
            return new java.awt.Insets(0, 0, 0, 0);
        }

        @Override
        public void paintBorder(java.awt.Component on, java.awt.Graphics g, int x, int y,
            int width, int height)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

            try
            {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                // Translucent, so the track underneath still reads - the grip marks a square, it does
                // not replace it
                g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.55f));

                g2.setColor(HANDLE_FILL);
                g2.fillRect(x, y, width, height);

                g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 1f));

                arrows(g2, x, y, width, height);
            }
            finally
            {
                g2.dispose();
            }
        }

        /**
         * The four-way arrow, drawn as one stroke each way with a head on every end.
         */
        private void arrows(java.awt.Graphics2D g, int x, int y, int width, int height)
        {
            int middleX = x + width / 2;
            int middleY = y + height / 2;

            // Short of the edges, so the arrow reads as one glyph on the square rather than as
            // something continuing onto the squares beside it
            int reach = Math.max(4, Math.min(width, height) / 2 - 3);
            int head = Math.max(2, reach / 3);

            g.setColor(java.awt.Color.BLACK);
            g.setStroke(new java.awt.BasicStroke(Math.max(1.6f, reach / 6f),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

            g.drawLine(middleX - reach, middleY, middleX + reach, middleY);
            g.drawLine(middleX, middleY - reach, middleX, middleY + reach);

            // west, east, north, south
            head(g, middleX - reach, middleY, head, -1, 0);
            head(g, middleX + reach, middleY, head, 1, 0);
            head(g, middleX, middleY - reach, head, 0, -1);
            head(g, middleX, middleY + reach, head, 0, 1);
        }

        private void head(java.awt.Graphics2D g, int tipX, int tipY, int size, int dx, int dy)
        {
            int[] xs = {tipX, tipX - dx * size - dy * size, tipX - dx * size + dy * size};
            int[] ys = {tipY, tipY - dy * size - dx * size, tipY - dy * size + dx * size};

            g.fillPolygon(xs, ys, 3);
        }
    }

    /**
     * The squares picked out for a group operation.
     *
     * Selection is a STATE rather than a key held down, which is the whole point of it: dragging a
     * group needs both hands free, so the selection cannot be something the user is holding shift to
     * maintain.  Shift-click picks and unpicks, Escape lets everything go.
     *
     * The geometry lives in TileSelection, where it can be tested without a mouse.
     */
    private final org.traincontrol.base.TileSelection selection =
        new org.traincontrol.base.TileSelection();

    /** True while a drag that began on a picked square is in progress. */
    private boolean groupDragging = false;

    /**
     * Where a shift-drag started, or -1 when one is not in progress.
     *
     * Picking one square at a time is fine for three of them and hopeless for a row: a diagram may be
     * sixty squares wide, and asking somebody to shift-click sixty times is not a feature, it is a
     * dare.  Dragging a box is how every other program does this, and the arithmetic for it was
     * already written and tested - it simply had nothing calling it.
     */
    private int boxAnchorX = -1;
    private int boxAnchorY = -1;

    /**
     * Whether dragging picks squares rather than moving them.
     *
     * Shift-drag has always done this and nothing said so. A feature reached only by holding a key
     * nobody mentioned is a feature most people do not have - Adam's words were that it is not
     * intuitive the thing exists - so there is a button, and the key still works for anybody who
     * already knows it.
     */
    private boolean selectMode = false;

    /**
     * The rectangle being dragged out right now, or empty between drags.
     *
     * Kept apart from the real selection so that letting go somewhere unintended changes nothing, and
     * so the borders can show what WOULD be picked while the button is still down. Without it a box
     * drag was invisible until it finished, which is the wrong way round: the moment you need to see
     * the rectangle is while you are still deciding where to stop.
     */
    private final org.traincontrol.base.TileSelection previewSelection =
        new org.traincontrol.base.TileSelection();

    /**
     * Where the group currently being dragged would land, or empty when nothing is being dragged.
     *
     * A single tile being dragged carries a ghost of itself under the cursor, which answers "where is
     * this going" by being there. A group cannot: the cursor is on one square of twenty, and the ghost
     * would have to be the whole shape. So the destination squares are outlined instead, which says
     * the same thing about all of them at once.
     */
    private final org.traincontrol.base.TileSelection landingSelection =
        new org.traincontrol.base.TileSelection();

    /**
     * What a group copy took: one entry per square of the BOUNDING BOX, holes included.
     *
     * The bounding box rather than only the picked squares, because a piece of railway with gaps
     * punched in it is not the piece the user pointed at - and pasting one over existing track would
     * leave the old tiles showing through the holes, which reads as a paste that half worked.
     */
    private java.util.List<CarriedTile> groupClipboard;

    /**
     * One square of a copied group, by its offset from the top left of what was copied.
     */
    private static final class CarriedTile
    {
        private final int dx;
        private final int dy;
        private final LayoutDiagramComponent component;

        CarriedTile(int dx, int dy, LayoutDiagramComponent component)
        {
            this.dx = dx;
            this.dy = dy;
            this.component = component;
        }
    }
    
    // When true, the diagram does not get repainted, i.e. during bulk operations
    private boolean pauseRepaint = false;
    
    // Undo history
    Deque<List<LayoutDiagramComponent>> previousLayoutComponents = new ConcurrentLinkedDeque<>();
    Deque<List<LayoutDiagramComponent>> previousLayoutComponentsRedo = new ConcurrentLinkedDeque<>();

    /**
     * The names on the squares, one snapshot per component snapshot.
     *
     * A caption belongs to the setup rather than to the tile - which is what stops a rename having to
     * rewrite every page - so it cannot ride in the component snapshot beside it.  Kept in step with
     * that stack instead: pushed together, popped together, and the same size at every moment.
     *
     * Only this page's captions.  Restoring the others would undo edits made somewhere this editor was
     * never looking.
     */
    Deque<java.util.Map<String, Object>> previousCaptions = new ConcurrentLinkedDeque<>();

    Deque<java.util.Map<String, Object>> previousCaptionsRedo = new ConcurrentLinkedDeque<>();

    /**
     * What the setup currently says about this page's captions.
     *
     * @return caption square to station square, or an empty map when there is no setup to ask
     */
    private java.util.Map<String, Object> captionSnapshot()
    {
        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        return autonomy == null
            ? new java.util.LinkedHashMap<String, Object>()
            : autonomy.snapshotPage(layout.getName());
    }

    /**
     * Puts this page's captions back as a snapshot found them.
     *
     * @param captions a snapshot, or null to do nothing - a null means the stacks disagreed, and
     *        leaving the captions alone is a better answer than clearing them
     */
    private void restoreCaptions(java.util.Map<String, Object> captions)
    {
        if (captions == null) return;

        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        if (autonomy == null) return;

        autonomy.restorePage(layout.getName(), captions);

        // Straight to disk, for the same reason the move itself goes straight to disk - see
        // rememberAutonomy.  An undo that only reached memory would be forgotten by the reset that
        // follows the next edit, and the thing it undid would come back.
        autonomy.saveQuietly();
    }

    /**
     * Writes the setup out, now, after this window has changed it.
     *
     * Nothing else does.  The session lives on the main window and is thrown away and rebuilt after
     * every edit to a diagram - so a move that only reached memory was forgotten the moment the
     * editor closed, which is why a station's name and label still did not travel with it however
     * carefully the move itself carried them.
     *
     * Without reconciling: this window is in the middle of rearranging the diagram, so at any moment
     * half of it disagrees with the setup, and a reconcile would delete everything on the half that
     * has not caught up yet.
     *
     * Failure is logged rather than shown.  A dialog per dragged tile would be unusable, and the move
     * itself stands either way - it is only the memory of it that is at risk.
     */
    /**
     * The setup as it stood when this window opened, for Cancel to put back.  Null when there was no
     * autonomy session to ask, and cleared once the edits have been kept.
     */
    private org.json.JSONObject autonomyAsOpened;

    /**
     * Whether the last `settleUnsavedWork` ended in the user choosing Discard.
     *
     * The exit path needs to complete a track-mode discard, and "settleUnsavedWork returned true" is
     * not the same fact: it returns true when there was nothing to settle and nothing was asked. A
     * first version undid on that, so closing the application with a clean editor open rewound the
     * setup to whatever it was when the editor opened - throwing away anything autonomy had done
     * since, which is train positions and facings.
     */
    private boolean settledByDiscarding;

    /**
     * The session the disk half of the undo point was written through, so dispose can give it back
     * without asking for one.
     */
    private org.traincontrol.automationui.AutonomySession autonomySessionForTheNote;

    /**
     * Follows a locomotive's new name into the snapshot Cancel would put back.
     *
     * This window holds the setup as it was when it opened, so that cancelling can undo every edit made
     * in it.  A locomotive renamed or deleted while it is open is repaired in the live store - but the
     * snapshot still names the old one, and cancelling wrote that back.  A configuration naming a
     * locomotive that is not in the database is refused by parseAuto, which invalidates the whole
     * layout: the rename would have quietly armed that, to go off whenever somebody pressed Cancel.
     *
     * Repaired rather than refused.  The alternative is blocking locomotive renames while any editor is
     * open, which takes away something reasonable to do - the two windows are about different things,
     * and one of them being open is no reason the other cannot be used.
     *
     * @param from the old name
     * @param to the new name, or null when it was deleted
     */
    public void autonomyLocomotiveRenamed(String from, String to)
    {
        org.traincontrol.automationui.AutonomyCompanionStore
            .repairLocomotiveInSetup(this.autonomyAsOpened, from, to);

        // And every snapshot on the undo stacks, which is the door Cancel is not.
        //
        // Each entry is a page snapshot holding this page's point properties - placements, homes and
        // exclusions, by name - and restoring one writes it back AND SAVES.  So without this, an undo
        // after a rename put the old name back on disk, and a configuration naming a locomotive that is
        // not in the database is refused by parseAuto, which invalidates the whole layout.  Cancel and
        // Ctrl+Z are two ways of saying the same thing, and only one of them was covered.
        for (java.util.Map<String, Object> was : this.previousCaptions)
        {
            org.traincontrol.automationui.AutonomyCompanionStore
                .repairLocomotiveInPageSnapshot(was, from, to);
        }

        for (java.util.Map<String, Object> was : this.previousCaptionsRedo)
        {
            org.traincontrol.automationui.AutonomyCompanionStore
                .repairLocomotiveInPageSnapshot(was, from, to);
        }
    }

    /**
     * Takes the undo point: what the setup looks like right now, in memory and on disk.
     *
     * **Both halves in one call, because they were two and they drifted immediately.** The in-memory
     * half is what Cancel restores. The disk half (OB-108) is what a restart restores, and it exists
     * because a snapshot that lives in memory is lost by exactly the event it exists to survive - the
     * process dying with an editor open, after the setup has been written per gesture and the diagram
     * has not been written at all.
     *
     * They have to be taken at the same moments or the disk half describes a different edit from the
     * one the window is showing. That is not hypothetical: the first version took the disk half in the
     * constructor only, so a page or mode switch moved the in-memory undo point and left the disk one
     * pointing at the window's opening - which would have reverted work the user was asked about and
     * chose to SAVE on the way here.
     *
     * @param live the session to snapshot, or null when there is none
     */
    private void takeTheUndoPoint(org.traincontrol.automationui.AutonomySession live)
    {
        this.autonomyAsOpened = live == null ? null : live.snapshotSetup();

        this.autonomySessionForTheNote = live;

        if (live != null) live.beginEditSession();
    }

    /**
     * Gives the disk half back, because this window is closing.
     *
     * **In dispose, which is the only thing every ending passes through.** There are seven ways this
     * window closes - Save, Cancel, the window X, closing autonomy mode, a jump to another page, a
     * jump to a square, and a construction failure - and the first attempt at this wired three of
     * them, all on the track-editor side of an `if (isAutonomyMode())`. So every autonomy edit left
     * its note behind, and the next session build reverted the setup to before the editor opened.
     * That is worse than the defect being fixed: it destroys saved work rather than unsaved work.
     *
     * A page or mode SWITCH does not come through here - the frame survives one - which is right,
     * because the window is still open and still mid-edit. `arriveAt` re-takes the note instead.
     *
     * Held rather than asked for, so that closing cannot build a session just to delete a file. Any
     * session on this layout folder writes the same note, so a stale one still clears it.
     */
    @Override
    public void dispose()
    {
        if (this.autonomySessionForTheNote != null)
        {
            // Said out loud when it fails.  The note lives under OneDrive and the delete can lose to a
            // sync client - in which case the store leaves a harmless one behind instead, and this is
            // the only place that knows it happened.
            if (!this.autonomySessionForTheNote.endEditSession() && parent.getModel() != null)
            {
                parent.getModel().log("Could not clear the record of this editing session");
            }

            this.autonomySessionForTheNote = null;
        }

        super.dispose();
    }

    /**
     * Puts the autonomy setup back the way it was when this window opened.
     *
     * Called from the Cancel path, beside the re-read of the pages that undoes the diagram: the two
     * halves have to be undone together or they are left describing different railways.
     */
    private void undoAutonomyEdits()
    {
        if (this.autonomyAsOpened == null) return;

        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        if (autonomy != null && !autonomy.restoreSetup(this.autonomyAsOpened)
            && parent.getModel() != null)
        {
            parent.getModel().log("Could not put the autonomy setup back after cancelling");
        }

        this.autonomyAsOpened = null;
    }

    private void rememberAutonomy(org.traincontrol.automationui.AutonomySession autonomy)
    {
        if (autonomy == null) return;

        if (!autonomy.saveQuietly() && parent.getModel() != null)
        {
            parent.getModel().log("Could not save the autonomy setup after a diagram edit");
        }
    }

    /**
     * How much taller than its old floor this window may not be shrunk past.
     *
     * The sidebar has grown - a Diagram Size heading and its two buttons, and the autonomy column
     * when that mode is on - and at the old minimum the controls at the bottom of it were the first
     * thing to be squeezed out.
     */
    private static final int EXTRA_MINIMUM_HEIGHT = 50;

    public static final int MAX_UNDO_HISTORY = 100;
    
    // Repaint state
    private final ReentrantLock lock = new ReentrantLock();
    private boolean isRunning = false;
    private boolean needsRerun = false;
    
    // Reference to the right click menu
    LayoutEditorRightclickMenu popup;

    /**
     * Popup window showing editable train layouts
     * @param l reference to the layout
     * @param size size of each tile, in pixels
     * @param ui
     * @param pageIndex
     */
    public LayoutEditor(LayoutDiagram l, int size, TrainControlUI ui, int pageIndex)
    {
        initComponents();

        // The panel the designer laid out, remembered before anything can wrap it.
        //
        // The sidebar puts this inside a panel of its own, so getContentPane() stops being the thing
        // the form built - and three places below reach for its GroupLayout to swap one control for
        // another.  Those want the form's panel whether or not there is a sidebar, so they ask for it
        // by name.
        this.formPane = getContentPane();

        this.ExtLayoutPanel.setLayout(new FlowLayout());
        this.parent = ui;
        this.size = size;
        this.layout = l;

        // The autonomy setup exactly as it stands now, before anything in this window can touch it.
        //
        // Every gesture that moves track writes the setup to disk as it goes - it has to, because a
        // setup that lags the diagram is one reconcile away from being deleted - so Cancel had nothing
        // to undo those writes with.  The diagram was re-read from disk and the setup was not, and a
        // cancelled drag left a station recorded on a square the track had been moved away from.
        //
        // Taken here rather than at the first edit, because by the time an edit reports itself the
        // change has already been made to the live session.
        org.traincontrol.automationui.AutonomySession opened = ui == null
            ? null : ui.getAutonomySession();

        takeTheUndoPoint(opened);
        
        // Mirror address preference
        this.showAddressCheckbox.setSelected(l.getShowAddress());

        mountGridToggle();
        
        this.setFocusable(true);
        this.requestFocusInWindow();
        
        // What the form calls the palette, before autonomy mode can rename it.
        //
        // Read rather than restated: the text lives in the generated form, which cannot be edited by
        // hand, so anything that wants to put it back has to have kept a copy.
        this.paletteHeading = this.jLabel1.getText();

        buildPalette();

        // So the heading reads correctly before anything has been pressed
        showDiagramSize();
    }

    /**
     * Shows or hides the track lengths, from the keyboard (OB-019).
     *
     * G because the letters that mean anything were taken: Ctrl+D is aDdresses and Ctrl+L is Labels,
     * and lengths are the third number this diagram can write on a tile. lenGth is the best of what
     * was left.
     *
     * Only in autonomy mode, because that is the only mode with lengths to show, and silently in the
     * other one. A dialog would be the wrong answer to a key nobody meant to press: every other
     * shortcut in this dispatcher that has nothing to act on does nothing, and a modal that has to be
     * dismissed is a worse interruption than the one it is complaining about.
     *
     * Through doClick rather than setSelected: the checkbox's own listener writes the preference and
     * redraws, and a shortcut that set the field directly would toggle the display without remembering
     * it - the sort of difference nobody finds until they wonder why the setting keeps resetting.
     */
    private void toggleTrackLengths()
    {
        if (autonomyPanel == null) return;

        autonomyPanel.getShowLengths().doClick();
    }

    /**
     * The palette of track pieces, in the panel the form built for it.
     *
     * Extracted from the constructor for OB-017. Autonomy mode EMPTIES this panel and gives it a
     * BorderLayout to hold the setup column, and leaving that mode used only to remove the column -
     * which was enough for as long as coming back meant a new window, because a new window ran the
     * constructor. Since the editor stopped closing to switch (OB-005), leaving autonomy mode is the
     * first time that teardown has ever had to put anything back, and it put back nothing: an empty
     * palette under a heading still reading "Autonomy Tools".
     *
     * Rebuilt rather than hidden, because the panel's LAYOUT changes too - a GridBagLayout of tiles
     * against a BorderLayout of one column - and a hidden component in the wrong layout is not the
     * same as one that is not there.
     */
    private void buildPalette()
    {
        this.newComponents.removeAll();

        // Display the items in a grid
        this.newComponents.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE; // Prevent scaling
        gbc.weightx = 0; // Components won't stretch horizontally
        gbc.weighty = 0; // Components won't stretch vertically
        gbc.gridx = 0; // Starting column
        gbc.gridy = 0; // Starting row
        gbc.anchor = GridBagConstraints.NORTH; // Anchor to top
        gbc.insets = new java.awt.Insets(2, 2, 2, 2); // Top, left, bottom, right padding

        int cols = 3;
        
        // Initialize components we can place
        for (LayoutDiagramComponent.componentType type : LayoutDiagramComponent.componentType.values())
        {
            this.newComponents.add(this.getLabel(type, "text"), gbc);

            // Move to the next grid position
            gbc.gridx++;
            if (gbc.gridx >= cols)
            {
                gbc.gridx = 0;
                gbc.gridy++;
            }
        }
        
        // Add a filler at the bottom to push everything up
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = cols;
        gbc.weighty = 1; // absorbs vertical space
        
        JPanel filler = new JPanel();
        filler.setOpaque(false);                // transparent
        filler.setPreferredSize(new Dimension(0, 0)); // no default height
        filler.setMinimumSize(new Dimension(0, 0));   // no minimum height
        this.newComponents.add(filler, gbc);

        if (this.paletteHeading != null) this.jLabel1.setText(this.paletteHeading);

        this.newComponents.revalidate();
        this.newComponents.repaint();
    }

    /** What the palette heading says when this is a track diagram editor - see buildPalette */
    private String paletteHeading;

    public boolean hasToolFlag()
    {
        return this.toolFlag != null;
    }
    
    /**
     * The column a label sits in, for callers outside this class.
     */
    public int getGridX(LayoutLabel label)
    {
        return getX(label);
    }

    /**
     * The row a label sits in.
     */
    public int getGridY(LayoutLabel label)
    {
        return getY(label);
    }

    private int getX(LayoutLabel label)
    {
        return grid.getCoordinates(label)[0];
    }
    
    private int getY(LayoutLabel label)
    {
        return grid.getCoordinates(label)[1];
    }
    
    /**
     * Generates a new label based on the specified type, so that it can be placed on the diagram
     * @param type
     * @param defaultText
     * @return 
     */
    private LayoutLabel getLabel(LayoutDiagramComponent.componentType type, String defaultText)
    {
        try
        {
            LayoutDiagramComponent component = new LayoutDiagramComponent(type, 0, 0, 0, 0, 0, 0, Accessory.accessoryDecoderType.MM2);
            
            // Set a default address, otherwise switches will become unclickable after saving
            if (component.isClickable())
            {
                component.setLogicalAddress(1, Accessory.accessoryDecoderType.MM2, false);
            }
            
            if (type == LayoutDiagramComponent.componentType.TEXT)
            {
                component.setLabel(defaultText);   
            }
            
            LayoutLabel newLabel = new LayoutLabel(component, this, this.size, parent, true);
            
            // We need to add the text back on top of the icon
            if (type == LayoutDiagramComponent.componentType.TEXT)
            {
                newLabel.setText(defaultText);
            
                newLabel.setForeground(Color.black);
                newLabel.setFont(new Font("Sans Serif", Font.PLAIN, this.size / 2));
                newLabel.setVerticalTextPosition(JLabel.CENTER); 
                newLabel.setHorizontalTextPosition(JLabel.CENTER);
            }
            
            newLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            newLabel.setBorder(BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR, NEW_COMPONENT_BORDER_WIDTH));
            
            return newLabel;
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.toString());
            this.parent.getModel().log(e);
        }
        
        return null;
    }
    
    /**
     * Key press on a tile
     * @param e
     * @param label 
     */
    public void receiveKeyEvent(KeyEvent e, LayoutLabel label)
    {
        // The last mutating entry point without this guard.  It has no callers today, but a key
        // listener wired to it later would paste tiles in autonomy mode without anyone noticing.
        if (isAutonomyMode()) return;
        
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V)
        {
            if (this.hasToolFlag())
            {
                this.executeTool(label, null);
            }
        }
    }
    
    /**
     * Checks if any of the tiles in the new component box are highlighted, indicating an active tool
     * @return 
     */
    public boolean addBoxHighlighted()
    {
        for (Component component : this.newComponents.getComponents())
        {
            if (component instanceof JLabel)
            {
                JLabel label = (JLabel) component;
                Border border = label.getBorder();

                if (border instanceof LineBorder)
                {
                    LineBorder lineBorder = (LineBorder) border;
                    if (lineBorder.getLineColor().equals(NEW_COMPONENT_BORDER_ACTIVE_COLOR))
                    {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
   
    /**
     * The name of the station on a square, for the autonomy editor's tooltip (FR, 2026-08-27).
     *
     * Asked of the autonomy session, not of any caption drawn nearby. A caption is a label somebody
     * chose to place on some square, and the square this is about usually has no label at all - which
     * is the whole reason for saying its name on hover.
     *
     * Null for anything that is not a station, which is what a JLabel wants for "no tooltip": most of
     * a diagram is plain track, and a tooltip that appeared everywhere saying nothing would be worse
     * than none.
     *
     * @param label the square under the pointer
     * @return the station's name, or null
     */
    private String stationNameOn(LayoutLabel label)
    {
        if (label == null || this.parent == null || this.layout == null) return null;

        int x = getX(label);
        int y = getY(label);

        // The palette, which is not on the railway at all.
        if (x < 0 || y < 0) return null;

        return this.parent.autonomyStationNameAt(
            new org.traincontrol.automationui.TileGraph.TileKey(this.layout.getName(), x, y));
    }

    public void receiveMoveEvent(MouseEvent e, LayoutLabel label)
    {
        // In autonomy mode there is no PLACEMENT preview - nothing is being placed - but the blue
        // outline still says where the pointer is (OB-091).
        //
        // Adam: "also, add the blue outline hover effect to the autonomy editor." Returning here took
        // the whole gesture away, tooltip and outline together, on the reasoning that the preview had
        // nothing to show. The outline is not a preview: it is the answer to "which square am I about
        // to right-click", and this editor's menus act on exactly that square.
        //
        // `lastHoveredX/Y` are deliberately NOT set. They are where a paste would land, and nothing is
        // pasted here - leaving them alone keeps this from teaching the placement code a position it
        // has no business acting on.
        //
        // Nothing moves: highlightLabel sizes the outline to the room the resting border takes, which
        // is a line when the grid is on and an overlay when it is off.
        if (isAutonomyMode())
        {
            if (label == null || (this.popup != null && this.popup.isVisible())) return;

            // And the square's NAME, which is the one thing this window is about that the diagram
            // does not already show (Adam, 2026-08-27).
            //
            // Set outside the invokeLater: a tooltip is read when the pointer settles, not when the
            // outline is painted, and putting it in the queue behind a repaint only delays it.
            label.setToolTipText(stationNameOn(label));

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                this.clearBordersFromChildren(this.grid.getContainer());
                this.highlightLabel(label, COMPONENT_BORDER_HOVERED_COLOR);
            });

            return;
        }

        if (this.popup != null && this.popup.isVisible()) return;

        lastHoveredX = getX(label);
        lastHoveredY = getY(label);
     
        if (label != null)
        {
            //label.setBackground(Color.red);
            
            if (lastHoveredX == -1)
            {
                label.setToolTipText(
                    I18n.f("layout.ui.tooltipPlaceNewComponent", label.getComponent().getUserFriendlyTypeName())
                );
            }
            else
            {
                String toolTipText = I18n.t("layout.ui.tooltipRightClickOptions");

                String componentString = "";

                if (this.hasToolFlag())
                {
                    if (lastComponent != null)
                    {
                        componentString = lastComponent.getUserFriendlyTypeName();
                    }
                    
                    if (!componentString.isEmpty())
                    {
                        label.setToolTipText(
                            I18n.f("layout.ui.tooltipPasteTile", componentString, toolTipText)
                        );
                    }
                }
                else if (this.layout.getComponent(lastHoveredX, lastHoveredY) != null)
                {
                    componentString = this.layout.getComponent(lastHoveredX, lastHoveredY).getUserFriendlyTypeName();
                    if (!componentString.isEmpty())
                    {
                        label.setToolTipText(
                            I18n.f("layout.ui.tooltipCutTile", componentString, toolTipText)
                        );
                    }
                }
                else if (this.canUndo())
                {
                    label.setToolTipText(toolTipText);
                }
            }
                
            if (lastHoveredX != -1 && lastHoveredY != -1)
            {
                if (isSelectionHandle(label))
                {
                    // The four-way arrow, which is what a grip looks like everywhere else
                    label.setCursor(new Cursor(Cursor.MOVE_CURSOR));
                }
                else if (this.hasToolFlag())
                {
                    label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                }
                else
                {
                    label.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    this.clearBordersFromChildren(this.grid.getContainer());
                    this.highlightLabel(label, COMPONENT_BORDER_HOVERED_COLOR);

                    // The hover clears every border on the page, which took the picked squares and
                    // the grip with it - so moving the pointer across a selection erased the one
                    // control that can move it.  Drawn back, after.
                    if (!this.selection.isEmpty()) this.refreshSelectionBorders();
                });
            }
        }
        
        // lastHoveredLabel = label;
    }
    
    /**
     * The square a drag started on, so that a click can be told from a drag on release.
     */
    private LayoutLabel dragSource = null;

    /**
     * The square that drags the whole selection: the top right corner of what is picked.
     *
     * With picking switched on, EVERY drag draws a new box - that is what the mode is - so the
     * "start a drag on a picked square and the group moves" gesture could not be reached at all
     * without turning the mode off first.  Which works, and which nobody would guess.
     *
     * So one square of the selection is a grip instead.  Top right rather than top left, because a
     * selection is usually dragged out left-to-right and the pointer is already over there when the
     * button comes up; and a corner rather than the middle, because the middle of a selection is
     * where its contents are and the grip has to be somewhere the user is not aiming at anyway.
     *
     * The corner of the BOUNDING BOX, which need not be a picked square itself - an L-shaped
     * selection has an empty corner.  Being able to grab the corner of the box you can see is worth
     * more than the grip always sitting on something chosen.
     *
     * @return the square, or null when nothing is picked
     */
    private int[] selectionHandle()
    {
        return this.selection.handle();
    }

    /**
     * @param label a square
     * @return whether that square is the grip
     */
    private boolean isSelectionHandle(LayoutLabel label)
    {
        if (label == null) return false;

        int[] handle = selectionHandle();

        return handle != null && getX(label) == handle[0] && getY(label) == handle[1];
    }

    public void beginDrag(MouseEvent e, LayoutLabel label)
    {
        // Dragging MOVES track.  In autonomy mode the user is deciding which way trains may run, not
        // rearranging their railway, and a drag that quietly relaid the diagram would be the worst kind
        // of accident: silent, and to the thing everything else is derived from.
        if (isAutonomyMode()) return;

        // The grip comes first, or picking mode would swallow it.
        //
        // While the mode is on every drag draws a box, which is what the mode is for - so this one
        // square is the exception, and it is the only way to move a group without first turning the
        // mode off.  Checked before the box branch rather than after, because the box branch returns.
        if (label != null && isSelectionHandle(label))
        {
            beginGroupDrag(label);

            return;
        }

        // Shift held: this is a box, not a move.  Recorded and otherwise ignored - nothing is picked
        // until the button comes up, so a shift-drag that changes its mind can simply be released
        // back where it started.
        if (label != null && (e.isShiftDown() || this.selectMode)
            && getX(label) >= 0 && getY(label) >= 0)
        {
            this.boxAnchorX = getX(label);
            this.boxAnchorY = getY(label);
            this.dragSource = label;
            this.groupDragging = false;

            return;
        }

        this.boxAnchorX = -1;
        this.boxAnchorY = -1;

        // A drag that starts on a picked square moves the WHOLE selection.
        //
        // This is what the selection is a state for: the user has already said which squares they
        // mean, so both hands are free for the drag.  Starting on an unpicked square is the old
        // single-tile drag, unchanged - and does not disturb the selection, so a mis-aimed drag does
        // not throw away the picking that preceded it.
        if (label != null && !this.selection.isEmpty()
            && this.selection.contains(getX(label), getY(label)))
        {
            beginGroupDrag(label);

            return;
        }

        this.groupDragging = false;

        if (label != null && label.getComponent() != null)
        {
            // Remembered so that a press and release on one square can be told from a drag.
            this.dragSource = label;

            if (getX(label) == -1 && getY(label) == -1)
            {
                this.initCopy(label, label.getComponent(), false);
                this.highlightLabel(label, NEW_COMPONENT_BORDER_ACTIVE_COLOR);
            }
            else
            {
                this.initCopy(label, null, true);
            }
          
            // Create a floating window with a copy of the label
            ghostLabel = new JLabel(label.getIcon());
            ghostLabel.setText(label.getComponent().getLabel());
            ghostLabel.setSize(label.getSize());
            
            // Only show border if there is no label
            if ("".equals(label.getComponent().getLabel()))
            {
                ghostLabel.setBorder(new LineBorder(Color.BLACK, 1));
            }
            
            dragWindow = new JWindow();
            dragWindow.setBackground(new Color(0,0,0,0)); // transparent
            dragWindow.getContentPane().add(ghostLabel);
            dragWindow.pack();
            dragWindow.setVisible(false);
        }
    }

    /**
     * Picks the whole selection up, from a square inside it or from its grip.
     */
    private void beginGroupDrag(LayoutLabel label)
    {
        this.dragSource = label;
        this.groupDragging = true;

        // The tiles themselves, not a count of them.
        //
        // It used to read "12 tiles", which says how MANY are moving and nothing about which - and on
        // a diagram where the answer is a shape, a number is the one fact nobody needs.  Dragging a
        // curve into a run of straights is a thing the eye can check in the moment the group is over
        // the gap; it cannot check it against the word twelve.
        javax.swing.Icon carried = selectionPreview();

        ghostLabel = carried == null
            ? new JLabel(I18n.f("layout.ui.dragGroup", this.selection.size())) : new JLabel(carried);

        ghostLabel.setOpaque(true);
        ghostLabel.setBackground(Color.WHITE);
        ghostLabel.setBorder(new LineBorder(COMPONENT_BORDER_SELECTED_COLOR, 2));

        dragWindow = new JWindow();
        dragWindow.getContentPane().add(ghostLabel);
        dragWindow.pack();
        dragWindow.setVisible(false);
    }

    /**
     * The picked squares drawn as one picture, for the thing that follows the pointer.
     *
     * Laid out as they sit on the diagram - the same gaps, the same shape - because that is what a
     * user is lining up when they drag a group, and a tidy row of icons would be a different shape
     * from the one actually being moved.
     *
     * Scaled down where the selection is large, since a group of forty squares at full size would be
     * a window bigger than the diagram it is being dragged over.
     *
     * @return the picture, or null where there is nothing to draw
     */
    private javax.swing.Icon selectionPreview()
    {
        int[] bounds = this.selection.bounds();

        if (bounds == null || grid == null) return null;

        int across = bounds[2] - bounds[0] + 1;
        int down = bounds[3] - bounds[1] + 1;

        // Big enough to read, small enough to see past
        double scale = Math.min(1.0, MAX_DRAG_PREVIEW / (double) (Math.max(across, down) * size));

        int cell = Math.max(4, (int) Math.round(size * scale));

        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(
            Math.max(1, across * cell), Math.max(1, down * cell),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = picture.createGraphics();

        try
        {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            for (org.traincontrol.base.TileSelection.At at : this.selection.all())
            {
                LayoutLabel from = grid.getValueAt(at.getX(), at.getY());

                if (from == null || from.getIcon() == null) continue;

                int x = (at.getX() - bounds[0]) * cell;
                int y = (at.getY() - bounds[1]) * cell;

                // Through an image rather than by asking the icon to paint at a scale, because an
                // ImageIcon paints at its own size and would spill over the cell beside it
                java.awt.image.BufferedImage one = new java.awt.image.BufferedImage(
                    Math.max(1, from.getIcon().getIconWidth()),
                    Math.max(1, from.getIcon().getIconHeight()),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);

                java.awt.Graphics2D oneG = one.createGraphics();

                try
                {
                    from.getIcon().paintIcon(from, oneG, 0, 0);
                }
                finally
                {
                    oneG.dispose();
                }

                g.drawImage(one, x, y, cell, cell, null);
            }
        }
        finally
        {
            g.dispose();
        }

        return new javax.swing.ImageIcon(picture);
    }

    /**
     * The longest side a dragged group is drawn at, in pixels.  Past this it is scaled down: the
     * picture is there to say WHAT is moving, and one big enough to hide the diagram underneath
     * stops answering the question it was asked.
     */
    private static final int MAX_DRAG_PREVIEW = 260;

    public void updateDrag(MouseEvent e, LayoutLabel label)
    {
        if (isAutonomyMode()) return;

        // A group being dragged, shown where it would land
        if (this.groupDragging && this.dragSource != null)
        {
            LayoutLabel over = getLastHoveredLabel();

            if (over != null && getX(over) >= 0 && getY(over) >= 0)
            {
                this.landingSelection.clear();

                for (org.traincontrol.base.TileSelection.At at
                    : this.selection.movedBy(getX(over) - getX(this.dragSource),
                        getY(over) - getY(this.dragSource)))
                {
                    this.landingSelection.add(at.getX(), at.getY());
                }

                this.refreshSelectionBorders();
            }
        }

        // A box being dragged out, shown while it is still being decided
        if (this.boxAnchorX >= 0 && this.boxAnchorY >= 0)
        {
            LayoutLabel over = getLastHoveredLabel();

            if (over != null && getX(over) >= 0 && getY(over) >= 0)
            {
                this.previewSelection.clear();
                this.previewSelection.addRectangle(this.boxAnchorX, this.boxAnchorY,
                    getX(over), getY(over));

                this.refreshSelectionBorders();
            }

            return;
        }

        if (dragWindow != null)
        {
            java.awt.Point screenPoint = e.getLocationOnScreen();
            dragWindow.setLocation(screenPoint.x + 10, screenPoint.y + 10);
            dragWindow.setVisible(true);
        }
    }

    public void endDrag(MouseEvent e, LayoutLabel label)
    {
        if (isAutonomyMode()) return;

        // A box closes here, and never opened a drag window - so this is checked before that branch
        // rather than inside it.
        if (this.boxAnchorX >= 0 && this.boxAnchorY >= 0)
        {
            LayoutLabel to = getLastHoveredLabel();

            int anchorX = this.boxAnchorX;
            int anchorY = this.boxAnchorY;

            this.boxAnchorX = -1;
            this.boxAnchorY = -1;
            this.dragSource = null;

            this.previewSelection.clear();

            // Released on the square it started on is a shift-CLICK, and receiveClickEvent toggles
            // that one.  Handling it here as well would toggle it twice, which is to say not at all.
            if (to == null || (getX(to) == anchorX && getY(to) == anchorY))
            {
                this.refreshSelectionBorders();

                return;
            }

            this.selection.addRectangle(anchorX, anchorY, getX(to), getY(to));

            // One box was all that was asked for
            if (this.selectOnce) setSelectMode(false);

            this.refreshSelectionBorders();

            return;
        }

        if (dragWindow != null)
        {
            dragWindow.dispose();
            dragWindow = null;
            
            LayoutLabel target = getLastHoveredLabel();

            LayoutLabel source = this.dragSource;

            this.dragSource = null;

            if (this.groupDragging)
            {
                this.groupDragging = false;

                this.landingSelection.clear();

                if (target != null && source != null)
                {
                    moveSelection(getX(target) - getX(source), getY(target) - getY(source));
                }

                return;
            }

            // Nothing under the pointer.  Hovering the palette sets the hovered square to -1,-1, so a
            // plain click there reached executeTool with no target: execCopy then built a component at
            // (-1,-1) and addComponent threw IndexOutOfBounds - unchecked, on the EDT, on every single
            // palette click.  Only IOException is caught around it.  The palette appeared to work
            // because receiveClickEvent re-arms the tool afterwards, so nothing is left to do here.
            if (target == null) return;

            // A press and release on the SAME square is a click, not a drag.  Executing there cut the
            // tile and dropped it straight back, which changes no diagram - but snapshotLayout runs
            // first, so merely clicking a tile pushed an undo entry, cleared the redo stack, and left
            // the editor asking whether to save work the user had not done.
            if (source != null && target == source)
            {
                resetClipboard();
                return;
            }

            // Snap to grid logic
            executeTool(target, null);
        }
    }
    
    /**
     * The autonomy tools, shown in place of the component palette when autonomy mode is on.
     *
     * Mounted into newComponents - the palette container - because that is one of the few containers in
     * this form whose layout manager is already replaced from hand-written code, so nothing generated has
     * to be touched.  Deliberately NOT ExtLayoutPanel, which LayoutGrid clears and re-lays out on every
     * redraw.
     */
    private AutonomyEditorPanel autonomyPanel;

    // The panel that holds the diagram with the findings list beneath it, once autonomy mode has
    // fitted one.  Kept so a second call does not stack a second copy.
    private javax.swing.JPanel autonomyFindings;

    private AutonomyBanner autonomyBanner;

    // The column that holds Addresses and the lengths toggle while autonomy mode is on
    private javax.swing.JPanel autonomyVisibility;

    /**
     * Turns autonomy setup on or off.
     *
     * Does not call layout.setEdit(), so the diagram keeps looking the way it does when trains are
     * running - station labels and text stay visible, which is what the user is reasoning about.
     *
     * @param session the setup to edit, or null to go back to editing the diagram
     */
    public void setAutonomyMode(org.traincontrol.automationui.AutonomySession session)
    {
        // Growing and shrinking the page is EDITING the diagram, and this mode does not edit the
        // diagram - it decides which way trains may run over one.  So the two size buttons and the
        // heading over them go away with the palette they sit under.
        //
        // Toggle Visibility stays.  Text labels and addresses are about what is DRAWN, which is a
        // question in autonomy mode as much as any other - the addresses are half of how somebody
        // checks that the square they are setting up is the sensor they meant.
        if (this.diagramSize != null) this.diagramSize.setVisible(session == null);
        if (this.plusButton != null) this.plusButton.setVisible(session == null);
        if (this.minusButton != null) this.minusButton.setVisible(session == null);

        if (session == null)
        {
            if (autonomyPanel != null)
            {
                this.newComponents.remove(autonomyPanel);
                autonomyPanel = null;

                // And the track pieces come back, under their own heading (OB-017).
                buildPalette();
            }

            if (autonomyBanner != null)
            {
                this.jScrollPane1.setColumnHeaderView(null);
                autonomyBanner = null;
            }

            // put the Addresses box back where the form had it
            if (autonomyVisibility != null && formPane.getLayout() instanceof javax.swing.GroupLayout)
            {
                ((javax.swing.GroupLayout) formPane.getLayout())
                    .replace(autonomyVisibility, this.showAddressCheckbox);

                autonomyVisibility = null;
            }

            // And the findings list comes out from under the diagram (GC-A2).
            //
            // The third of these, after the palette and the visibility box, and the one that had TWO
            // ways to go wrong. Mounting it replaces jScrollPane1 - the diagram's own scroll pane -
            // with a stack holding the diagram above and the findings below. Nothing put that back, so:
            //
            //   - leaving autonomy mode left a findings list sitting under the TRACK editor's diagram,
            //     describing a setup that window was no longer editing; and
            //   - autonomyFindings stayed non-null, so coming BACK skipped the mount block entirely -
            //     the new session's findings were built and orphaned, while the previous session's
            //     frozen list stayed on screen looking current.
            //
            // The second is the one that would have been believed. A stale list of findings is not
            // obviously stale: it is a plausible list about the right railway, and the only thing wrong
            // with it is that it stopped being true when the session changed.
            if (autonomyFindings != null)
            {
                autonomyFindings.remove(this.jScrollPane1);

                if (formPane.getLayout() instanceof javax.swing.GroupLayout)
                {
                    ((javax.swing.GroupLayout) formPane.getLayout())
                        .replace(autonomyFindings, this.jScrollPane1);
                }

                autonomyFindings = null;
            }
        }
        else if (autonomyPanel == null)
        {
            // The panel needs a way to the RUNNING layout for its "why is it not moving" test - the
            // one question that is about locomotives and occupancy rather than about track, and so
            // cannot be answered from the setup alone.
            autonomyPanel = new AutonomyEditorPanel(session, layout.getName(), new Runnable()
            {
                @Override
                public void run()
                {
                    refreshAutonomyAnnotations();

                    // The main window draws the same setup on its own diagram, and it has no way to
                    // know an edit happened in here - so it is told, after every one.
                    parent.refreshStaticAutonomyLayer();
                }
            });

            // Which window is the main one.  It cannot be found from here: this panel sits in a
            // JFrame, a JFrame has no owner, and the walk up the window tree therefore ends at the
            // editor - which is why every menu item needing it used to open a dialog saying "null".
            autonomyPanel.setMainWindow(parent);

            // Asked for on every use rather than held, because loading a configuration replaces the
            // Layout wholesale and a kept reference would answer about the previous one without
            // saying so.
            autonomyPanel.setLayoutSource(() -> parent.getModel().getAutoLayout());

            // Setup mode writes to a page only when a station name is put on a square.  The caption is
            // part of the tile art, so the grid is rebuilt rather than repainted, and the annotations
            // are laid back on top of the new one.
            autonomyPanel.setOnDiagramChanged(new Runnable()
            {
                @Override
                public void run()
                {
                    refreshGrid();

                    javax.swing.SwingUtilities.invokeLater(() -> refreshAutonomyAnnotations());
                }
            });

            autonomyPanel.setOnReveal(new java.util.function.Consumer<
                org.traincontrol.automationui.TileGraph.TileKey>()
            {
                @Override
                public void accept(org.traincontrol.automationui.TileGraph.TileKey tile)
                {
                    reveal(tile);
                }
            });

            // A finding on another page: close this window and open one showing that page, at that
            // square.  The editor is built around a single diagram - the field is final and the grid,
            // the annotations and the page exclusion all follow from it - so swapping pages in place
            // would mean rebuilding most of the window, and reopening it does the same thing honestly.
            //
            // Nothing is lost by closing: every edit made here has already gone into the shared session,
            // which is why "exit without saving" has to reload the store to undo them.  This path is
            // deliberately not that one.
            autonomyPanel.setOnJumpToPage(new java.util.function.Consumer<
                org.traincontrol.automationui.TileGraph.TileKey>()
            {
                @Override
                public void accept(org.traincontrol.automationui.TileGraph.TileKey tile)
                {
                    layout.setEdit(false);

                    dispose();

                    javax.swing.SwingUtilities.invokeLater(() ->
                    {
                        parent.autonomyEditorClosed();
                        parent.openAutonomyEditor(tile);
                    });
                }
            });

            // Going to a link's other end.  Same close-and-reopen as a finding on another page, but it
            // asks first: a finding is the window taking the user somewhere as part of showing them a
            // result, and this is the user choosing to leave a page they were working on.
            autonomyPanel.setOnJumpToLink(new java.util.function.Consumer<
                org.traincontrol.automationui.TileGraph.TileKey>()
            {
                @Override
                public void accept(org.traincontrol.automationui.TileGraph.TileKey tile)
                {
                    jumpToSquare(tile);
                }
            });

            autonomyPanel.setLocomotiveNames(new java.util.function.Supplier<java.util.List<String>>()
            {
                @Override
                public java.util.List<String> get()
                {
                    return parent.getModel() == null
                        ? new java.util.ArrayList<String>() : parent.getModel().getLocList();
                }
            });

            this.newComponents.removeAll();
            this.newComponents.setLayout(new java.awt.BorderLayout());
            this.newComponents.add(autonomyPanel, java.awt.BorderLayout.CENTER);

            // A strip across the top of the diagram for messages.  Mounted as the scroll pane's column
            // header - the same unused slot the main window's "Show autonomy" checkbox uses - because
            // the content pane is a generated GroupLayout that would ignore a BorderLayout constraint.
            //
            // Width is the point: a sentence needs the window, and the tools column is 280px, which is
            // how a three-line message used to pull that column apart.
            autonomyBanner = new AutonomyBanner();

            this.jScrollPane1.setColumnHeaderView(autonomyBanner);

            autonomyPanel.setBanner(autonomyBanner);

            // Put the lengths toggle directly under Addresses, inside the window's own Toggle
            // Visibility group.  GroupLayout cannot have a component added to it after the fact, but it
            // can REPLACE one - so the Addresses box is swapped for a small column holding both, which
            // lands the new toggle exactly where it belongs without touching the generated form.
            if (formPane.getLayout() instanceof javax.swing.GroupLayout)
            {
                javax.swing.JPanel visibility = new javax.swing.JPanel();
                visibility.setLayout(new javax.swing.BoxLayout(visibility,
                    javax.swing.BoxLayout.Y_AXIS));
                visibility.setOpaque(false);

                ((javax.swing.GroupLayout) formPane.getLayout())
                    .replace(this.showAddressCheckbox, visibility);

                this.showAddressCheckbox.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                autonomyPanel.getShowLengths().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

                visibility.add(this.showAddressCheckbox);

                // The same gap the form leaves between Text Labels and Addresses.  Stacked straight
                // into a BoxLayout these two touched, so the pair read as one control with two lines
                // rather than as two switches of the same kind as the one above them.
                visibility.add(javax.swing.Box.createVerticalStrut(HEADING_GAP));

                visibility.add(autonomyPanel.getShowLengths());

                // What the captions say, beside the other two view switches (FR-030).
                //
                // Off by default: this window is where a railway is named, so the captions name
                // stations. Ticked, they name whichever train is parked there, which is what the
                // running diagram shows and what this editor used to show.
                autonomyPanel.getShowParkedTrains().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

                visibility.add(javax.swing.Box.createVerticalStrut(HEADING_GAP));
                visibility.add(autonomyPanel.getShowParkedTrains());

                // A label, because unlike its neighbours this one is a choice rather than a switch and
                // "All" alone does not say what it is about.
                //
                // In the window's own heading style, copied off jLabel1 rather than restated, so it
                // reads as a heading of the same kind as every other one here - and so it follows if
                // that style is ever changed in the form.
                javax.swing.JLabel directionsLabel =
                    new javax.swing.JLabel(I18n.t("autosetup.ui.labelDirections"));
                directionsLabel.setFont(this.jLabel1.getFont());
                directionsLabel.setForeground(this.jLabel1.getForeground());
                directionsLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                // And the same air under this heading as the form leaves under its own, which is what
                // the sidebar's headings use too - see HEADING_GAP
                directionsLabel.setBorder(
                    javax.swing.BorderFactory.createEmptyBorder(HEADING_GAP + 6, 0, HEADING_GAP, 0));

                visibility.add(directionsLabel);

                javax.swing.JComboBox<String> choice = autonomyPanel.getShowDirections();
                choice.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                choice.setMaximumSize(new java.awt.Dimension(200, 24));

                visibility.add(choice);

                autonomyVisibility = visibility;
            }

            // Lengths and addresses are mutually exclusive: both print a number on the tile, and two
            // numbers on one square is unreadable.  Wired here because the Addresses box belongs to
            // this window, not to the panel.
            //
            // Applied once on opening too, because Lengths is remembered between visits now - so it
            // can arrive already on, with Addresses also on and two numbers on every square.
            if (autonomyPanel.getShowLengths().isSelected() && this.showAddressCheckbox.isSelected())
            {
                this.showAddressCheckbox.setSelected(false);
                toggleAddresses();
            }

            autonomyPanel.getShowLengths().addActionListener(e ->
            {
                if (autonomyPanel.getShowLengths().isSelected()
                    && this.showAddressCheckbox.isSelected())
                {
                    this.showAddressCheckbox.setSelected(false);
                    toggleAddresses();
                }
            });

            // The findings go across the bottom, under the diagram and the full width of it.
            //
            // GroupLayout.replace on the scroll pane that holds the grid, which is the same way the
            // visibility box is fitted: the content pane is generated, so nothing here edits it -
            // the grid's slot is taken by a panel holding the grid AND the list beneath it.
            //
            // Full width because these are sentences.  Down the side they wrapped to four words a line
            // beside the very diagram they describe, which is where the reader has to look to act on
            // them.
            if (autonomyFindings == null && autonomyPanel.getFindingsPanel() != null)
            {
                javax.swing.JPanel stack = new javax.swing.JPanel(new BorderLayout());
                stack.setOpaque(false);

                // Guarded like the two above it.  All three reach for the form's own layout, and the
                // form is generated - so if it is ever rebuilt with anything other than a GroupLayout,
                // two of these would decline and the third would throw.
                if (formPane.getLayout() instanceof javax.swing.GroupLayout)
                {
                    ((javax.swing.GroupLayout) formPane.getLayout())
                        .replace(this.jScrollPane1, stack);
                }

                stack.add(this.jScrollPane1, BorderLayout.CENTER);

                javax.swing.JScrollPane list = autonomyPanel.getFindingsPanel();

                // Taller than the panel asked for: it is across the window now rather than down the
                // side, and its rows are the window's own size rather than the smaller hint size.
                list.setPreferredSize(new java.awt.Dimension(100, 190));

                // The count goes UNDER the list, not above it.  In the sidebar it was a headline over a
                // column that did not contain the things it counted; here it is a total, which is what
                // it always was.
                javax.swing.JPanel foot = new javax.swing.JPanel(new BorderLayout());
                foot.setOpaque(false);
                foot.add(list, BorderLayout.CENTER);
                foot.add(autonomyPanel.getBanner(), BorderLayout.SOUTH);

                stack.add(foot, BorderLayout.SOUTH);

                autonomyFindings = stack;
            }

            // The column heading belongs to the window, not to the palette that used to fill it - in
            // autonomy mode "New Components" describes something that is no longer there.
            this.jLabel1.setText(I18n.t("autosetup.ui.titleCap"));

            // The window is not editing the layout in this mode, and saying so in the title bar is the
            // cheapest way to keep somebody from wondering which of the two editors they opened.
            //
            // Posted, not called: render() sets the title from inside its own invokeLater and this
            // method runs synchronously straight after it, so a direct call would be overwritten a
            // moment later by the one it was meant to replace.
            javax.swing.SwingUtilities.invokeLater(() ->
                setTitle(I18n.f("autosetup.ui.windowTitle", this.layout.getName())));

            // Drawn again, because the first draw happened before this window knew what it was.
            // render() builds the grid and setAutonomyMode runs after it, so the labels were rendered
            // as the track diagram editor's - raw "Point:" text - and would have stayed that way until
            // something else happened to rebuild them.
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                refreshGrid();

                javax.swing.SwingUtilities.invokeLater(() -> refreshAutonomyAnnotations());
            });
        }

        this.newComponents.revalidate();
        this.newComponents.repaint();

        // entering the mode draws what is already decided; leaving it takes the marks with it
        refreshAutonomyAnnotations();
    }

    /**
     * Redraws what the autonomy editor is saying over every tile - directions, lengths, selection.
     *
     * The editor walks its own grid and asks the panel about each square, because only the editor knows
     * which labels exist right now; the panel only knows what has been decided.  Grid indexes equal
     * diagram coordinates here because edit mode pins minx and miny to 0 (LayoutDiagram.checkBounds);
     * anything drawing over a NON-edit grid would have to add the layout's own offsets.
     */
    /**
     * Closes an autonomy-mode editor and puts the shared diagram back as it found it.
     *
     * render() calls setEdit() on the LayoutDiagram the MAIN WINDOW also paints, and nothing used to
     * clear it: the flag was only ever reset by re-parsing the pages after a diagram save, which
     * autonomy mode never does.  So closing this window left the main diagram building its grid in edit
     * mode - labels wired to an editor that no longer exists, station labels suppressed, and a
     * ClassCastException on the first click anywhere.
     *
     * The window's always-on-top state is restored here too, for the same reason: its usual home is the
     * diagram-save path, which autonomy mode also skips.
     */
    private void closeAutonomyMode()
    {
        layout.setEdit(false);

        dispose();

        // The main window shows the same pages, and they have just changed shape.  It also has to put
        // back what opening this window changed - always-on-top and the disabled Edit button - which
        // normally happens at the end of layoutEditingComplete, a path autonomy mode never takes.
        javax.swing.SwingUtilities.invokeLater(() -> parent.autonomyEditorClosed());
    }

    /**
     * Whether this editor window is setting autonomy up rather than editing the track.    /**
     * Whether this editor window is setting autonomy up rather than editing the track.
     *
     * The single question every mouse path asks, so that "autonomy mode" cannot mean one thing to
     * clicking and another to dragging.
     * @return
     */
    public boolean isAutonomyMode()
    {
        return autonomyPanel != null && autonomyPanel.isVisible();
    }

    /**
     * Scrolls a square into view and flashes it, so that a finding or a naming prompt is about a tile
     * the user can actually see.
     *
     * @param tile
     */
    public void reveal(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        if (grid == null || tile == null || !layout.getName().equals(tile.getPage())) return;

        LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

        if (label == null) return;

        label.scrollRectToVisible(new java.awt.Rectangle(0, 0, label.getWidth(), label.getHeight()));

        // The diagram's own yellow flash, not a border: a border here replaced the tile's grid line
        // and had to be cleared to null afterwards, which left the square without one.
        label.flashHighlight();
    }

    public void refreshAutonomyAnnotations()
    {
        if (grid == null) return;

        boolean active = autonomyPanel != null && autonomyPanel.isVisible();

        for (int x = 0; grid.getValueAt(x, 0) != null; x++)
        {
            for (int y = 0; grid.getValueAt(x, y) != null; y++)
            {
                grid.getValueAt(x, y).setAutonomyAnnotation(!active ? null
                    : autonomyPanel.annotationFor(
                        new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), x, y)));
            }
        }
    }

    /**
     * @return the autonomy tools, or null when the editor is editing the diagram
     */
    public AutonomyEditorPanel getAutonomyPanel()
    {
        return autonomyPanel;
    }

    public void receiveClickEvent(MouseEvent e, LayoutLabel label)
    {    
        // In autonomy mode a click configures the track rather than editing it.  Routed here rather
        // than through a second listener because LayoutLabel hard-casts its parent to this class and
        // calls this method - so this is where a click already arrives, and adding a branch is smaller
        // and less surprising than intercepting events before they get here.
        if (isAutonomyMode())
        {
            int x = getX(label);
            int y = getY(label);

            if (x >= 0 && y >= 0)
            {
                org.traincontrol.automationui.TileGraph.TileKey tile =
                    new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), x, y);

                // Right-click opens the properties menu, the way it does on the graph.  Left-click
                // applies the selected tool.  Without this branch a right-click ran the tool as well,
                // which looked like a menu that failed to appear.
                if (javax.swing.SwingUtilities.isRightMouseButton(e))
                {
                    autonomyPanel.tileRightClicked(tile, layout.getComponent(x, y),
                        label, e.getX(), e.getY());
                }
                else
                {
                    autonomyPanel.tileClicked(tile, layout.getComponent(x, y), e.isShiftDown());
                }
            }

            return;
        }

        // New label to place
        if (getX(label) == -1 && getY(label) == -1)
        {
            this.initCopy(label, label.getComponent(), false);
            this.highlightLabel(label, NEW_COMPONENT_BORDER_ACTIVE_COLOR);
            return;
        }

        // Shift-click picks a square out, or puts it back.
        //
        // Before anything else on a grid square, because every other branch below does something to
        // the diagram and picking must not.  Toggling rather than adding, so one square too many is
        // corrected by clicking it again rather than by starting over.
        if (e.isShiftDown() && javax.swing.SwingUtilities.isLeftMouseButton(e))
        {
            this.selection.toggle(getX(label), getY(label));

            this.refreshSelectionBorders();

            return;
        }
               
        LayoutDiagramComponent lc = layout.getComponent(getX(label), getY(label));
        
        // Support double clicks
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1)
        {
            this.resetClipboard();
            if (lc != null && lc.isClickable())
            {
                this.editAddress(label);
            }
            else if (lc != null && lc.isText())
            {
                this.editText(label);
            }
            
            // Propagate the hover event.  Should be done for BUTTON3 at minimum
            receiveMoveEvent(e, label);
        }
        else if (e.getButton() == MouseEvent.BUTTON3)
        {
            // Propagate the hover event.  Should be done for BUTTON3 at minimum
            receiveMoveEvent(e, label);
        
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                popup = new LayoutEditorRightclickMenu(this, parent, label, lc);

                popup.show(e.getComponent(), e.getX(), e.getY());      
            });
        }
        else if (e.getButton() == MouseEvent.BUTTON2)
        {
            this.rotate(label);
            
            // Propagate the hover event.  Should be done for BUTTON3 at minimum
            receiveMoveEvent(e, label);
        }
        else 
        {
            if (this.hasToolFlag())
            {
                executeTool(label, null);
                
                // Propagate the hover event.  Should be done for BUTTON3 at minimum
                receiveMoveEvent(e, label);
            }
            else
            {
                // Click to cut
                if (label != null && label.getComponent() != null)
                {
                    this.initCopy(label, null, true);
                }
                else
                {
                    // Propagate the hover event.  Should be done for BUTTON3 at minimum
                    receiveMoveEvent(e, label);
                }
            }
        }
    }
        
    /**
     * Executes the currently active tool
     * @param label 
     * @param bulkFlag 
     */
    synchronized public void executeTool(LayoutLabel label, bulk bulkFlag)
    {     
        this.snapshotLayout();
        
        if (bulkFlag == bulk.COL)
        {
            int startCol = this.lastX;
            int destCol = this.getX(label);
            
            boolean isMove = (this.toolFlag == tool.MOVE);

            if (startCol != -1 && destCol != -1 && startCol != destCol)
            {
                List<LayoutLabel> destColumn = grid.getColumn(destCol);
                List<LayoutLabel> sourceColumn = grid.getColumn(startCol);

                // Which squares in the column carry track, noted BEFORE any of it is deleted
                java.util.Set<Integer> occupied = new java.util.LinkedHashSet<>();

                pauseRepaint = true;

                try
                {   
                    // Clear existing tiles.  Quietly: applyBulkPlan below tells the setup about this
                    // whole line at once, and knows which of these squares are being vacated rather
                    // than destroyed - which a delete on its own cannot know.
                    for (LayoutLabel l : destColumn)
                    {
                        if (l.getComponent() != null) this.delete(l, false);
                    }
                    
                    for (int i = 0; i < sourceColumn.size(); i++)
                    {
                        LayoutLabel sourceLabel = sourceColumn.get(i);
                        LayoutLabel destLabel = destColumn.get(i);
                        this.lastX = startCol;
                        this.lastY = i;
                        this.lastComponent = sourceLabel.getComponent();

                        if (this.lastComponent != null)
                        {
                            occupied.add(i);

                            // Quietly: applyBulkPlan below tells the setup about this whole line at
                            // once, and knows which squares are arriving rather than being destroyed
                            execCopy(destLabel, false, false);
                        }

                        // Tool will get reset
                        //this.toolFlag = tool.COPY;
                    }
                    
                    for (LayoutLabel l : sourceColumn)
                    {
                        if (isMove && l.getComponent() != null) this.delete(l, false);
                    }      
                }
                finally
                {
                    pauseRepaint = false;
                }

                // And the setup follows the column.
                //
                // execCopy carries it for a single tile, but only on a MOVE - and this passes false,
                // because a bulk move copies the whole line first and deletes the source line
                // afterwards rather than tile by tile.  So a column that was moved took its track and
                // left behind everything autonomy knew about it: the stations, the names, the lengths,
                // the facings, the link pairings, the switched-off links.  All of it stayed on the
                // column the track had walked away from, where the next reconcile - finding a station
                // on a square with no sensor - threw it away for good.
                //
                // Adam found it as links coming unpaired, which is the half of it that shows: a pairing
                // is mutual, so the partner is left pointing at a square that is now bare.
                applyBulkPlan(planBulkLine(layout.getName(), true, startCol, destCol,
                    sourceColumn.size(), occupied, isMove));

                this.resetClipboard();
                refreshGrid();
            }
        }
        else if (bulkFlag == bulk.ROW)
        {
            int startRow = this.lastY;
            int destRow = this.getY(label);

            boolean isMove = (this.toolFlag == tool.MOVE);
            
            if (startRow != -1 && destRow != -1 && startRow != destRow)
            {
                List<LayoutLabel> destinationRow = grid.getRow(destRow);
                List<LayoutLabel> sourceRow = grid.getRow(startRow);

                java.util.Set<Integer> occupied = new java.util.LinkedHashSet<>();

                pauseRepaint = true;

                try
                {
                    // Clear existing tiles - quietly, see the column above
                    for (LayoutLabel l : destinationRow)
                    {
                        if (l.getComponent() != null) this.delete(l, false);
                    }
                    
                    for (int i = 0; i < sourceRow.size(); i++)
                    {
                        LayoutLabel sourceLabel = sourceRow.get(i);
                        LayoutLabel destLabel = destinationRow.get(i);
                        this.lastX = i;
                        this.lastY = startRow;
                        this.lastComponent = sourceLabel.getComponent();

                        if (this.lastComponent != null)
                        {
                            occupied.add(i);

                            // Quietly - see the column above
                            execCopy(destLabel, false, false);
                        }

                        //this.toolFlag = tool.COPY;
                    }
                    
                    for (LayoutLabel l : sourceRow)
                    {
                        if (isMove && l.getComponent() != null) this.delete(l, false);
                    }
                    
                }
                finally
                {
                    pauseRepaint = false;
                }

                // The setup follows the row - see the column above for what was being lost.
                applyBulkPlan(planBulkLine(layout.getName(), false, startRow, destRow,
                    sourceRow.size(), occupied, isMove));

                this.resetClipboard(); // this will only allow us to copy the row/col once.  if we don't want to do this, we need to manually put the original tile back on the clipboard, and specify the tool
                refreshGrid();
            }
        }
        else
        {
            execCopy(label, toolFlag == tool.MOVE);
        }
        
        // Tile is on the main diagram- update borders
        if (lastX != -1 || lastY != -1)
        {
            this.clearBordersFromChildren(this.newComponents);
        }
    }
    
    /**
     * What a whole-column or whole-row replacement does to the setup.
     *
     * Two separate things, and the difference between them is the whole point:
     *
     *   - the squares being BUILT OVER.  The line is cleared and other tiles are written into it, so
     *     whatever the setup said about those squares is about track that is gone.  Reconcile cannot
     *     find these on its own: it drops setup from squares that are now EMPTY, and one of these is
     *     not empty, it is occupied by something else.
     *   - the squares being VACATED, when this is a move.  Their setup travels to where their track
     *     went, exactly as it does for a single dragged tile.
     *
     * A copy has only the first sort.  Two squares cannot both be one station, so nothing travels -
     * but the line being copied onto is still being built over, and letting that data sit there was
     * how a copied column ended up carrying somebody else's station names.
     *
     * A function of coordinates rather than of labels, so that it can be checked without a window:
     * see testLayoutEditorBulkEdits, which walks the combinations.
     *
     * @param page the page being edited
     * @param column true for a column, false for a row
     * @param from the line being taken
     * @param to the line being written over
     * @param span how many squares long the line is
     * @param occupied indices along the source line that carry track
     * @param move whether the source line is being emptied
     * @return the plan, possibly empty, never null
     */
    public static BulkPlan planBulkLine(String page, boolean column, int from, int to, int span,
        java.util.Set<Integer> occupied, boolean move)
    {
        BulkPlan plan = new BulkPlan();

        if (page == null || from == to || from < 0 || to < 0 || span <= 0) return plan;

        for (int i = 0; i < span; i++)
        {
            org.traincontrol.automationui.TileGraph.TileKey source = column ? new org.traincontrol.automationui.TileGraph.TileKey(page, from, i) : new org.traincontrol.automationui.TileGraph.TileKey(page, i, from);

            org.traincontrol.automationui.TileGraph.TileKey dest = column ? new org.traincontrol.automationui.TileGraph.TileKey(page, to, i) : new org.traincontrol.automationui.TileGraph.TileKey(page, i, to);

            plan.builtOver.add(dest);

            if (move && occupied != null && occupied.contains(i)) plan.moves.put(source, dest);
        }

        return plan;
    }

    /**
     * The two halves of a bulk edit, kept apart so that each can be checked on its own.
     *
     * Public so that the rule can be tested without a window - see testLayoutEditorBulkEdits.  The
     * editor is a JFrame that wants a running TrainControlUI behind it, and a rule that can only be
     * checked by building one is a rule that does not get checked.
     */
    public static final class BulkPlan
    {
        public final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey> moves = new java.util.LinkedHashMap<>();

        public final java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> builtOver = new java.util.LinkedHashSet<>();

    }

    /**
     * Tells the setup what the diagram just did.
     */
    private void applyBulkPlan(BulkPlan plan)
    {
        if (plan == null || (plan.moves.isEmpty() && plan.builtOver.isEmpty())) return;

        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        if (autonomy == null) return;

        // One call, both halves.  Which squares are only passing through, and what order the two
        // halves have to happen in, are the store's business - see AutonomyCompanionStore.moveTiles.
        // Working that out here is what this path got wrong the first time.
        if (autonomy.moveTiles(plan.moves, plan.builtOver)) rememberAutonomy(autonomy);
    }

    /**
     * Tells the setup that these squares have been built over - once, for the whole gesture.
     *
     * Per square it was ruinous, and invisibly so.  Each call rebuilt the entire graph TWICE - moveTiles
     * calls touched(), which rebuilds, and then rebuilds again - and wrote the whole setup to disk,
     * every file of it, atomically.  Select all, copy, paste is three clicks and covers the bounding
     * box INCLUDING blank squares: on a sixty by thirty page that is eighteen hundred iterations, some
     * thirty-six hundred graph rebuilds and eighteen hundred whole-setup disk writes, on the event
     * thread, with repainting suppressed so nothing on screen moves while it happens.  The layout
     * folder is under OneDrive here, so each write may also wake a sync client.
     *
     * The shape is the one applyBulkPlan already used: collect, tell once, and save only if anything
     * actually changed.  moveSelection and the four shift operations do the same for their gestures.
     *
     * @param builtOver the squares whose track has been replaced
     */
    private void forgetBuiltOver(java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> builtOver)
    {
        if (builtOver == null || builtOver.isEmpty()) return;

        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        if (autonomy == null) return;

        if (autonomy.forgetTiles(builtOver)) rememberAutonomy(autonomy);
    }

    /**
     * Copies lastComponent on the clipboard to the location designated by destLabel
     * @param destLabel
     * @param move
     */
    synchronized private void execCopy(LayoutLabel destLabel, boolean move)
    {
        execCopy(destLabel, move, true);
    }

    /**
     * @param tellAutonomy false when the CALLER is going to tell the setup what happened
     *
     * The same flag delete(LayoutLabel, boolean) carries, and for the same reason - which is why it is
     * here at all.  When delete was given it, this method was left as it was, and it is called from the
     * same two bulk loops: so a column move cleared its line quietly and then announced every landing
     * square LOUDLY, one at a time, with no moves map.
     *
     * That is not merely wasteful.  A landing square announced with no moves map cannot be told apart
     * from a square being built over by something unrelated, so the rule that spares a station's label
     * when the station itself is what lands on it - see AutonomyCompanionStore.forgetSquares - cannot
     * apply, and the label is dropped a moment before applyBulkPlan would have carried it.  The bulk
     * path lost station names again, by a third route, from the fix for the second one.
     */
    synchronized private void execCopy(LayoutLabel destLabel, boolean move, boolean tellAutonomy)
    {        
        try
        {
            // We need to duplicate the component, otherwise its coordinates won't actually change
            LayoutDiagramComponent newComponent = new LayoutDiagramComponent(lastComponent);
            newComponent.setX(getX(destLabel));
            newComponent.setY(getY(destLabel));

            if (newComponent.isText() && this.layout.getEditHideText())
            {
                this.toggleText();
            }
   
            if (move)
            {
                layout.addComponent(null, lastX, lastY);
            }
            
            layout.addComponent(newComponent, getX(destLabel), getY(destLabel));

            // Everything autonomy had written about that square goes with it.
            //
            // None of it is part of the diagram - the station designation, the name, the facings, the
            // arrival restrictions, the length, the caption - and every one of them is keyed by the
            // SQUARE, so moving the tile used to leave the lot behind on coordinates that now hold no
            // track.  The next reconcile then found a station on a square with no sensor and dropped
            // it, which is a setup destroyed by nudging a tile one square left.
            //
            // The caption alone used to follow, which was worse than nothing following: the name
            // moved and the station under it did not, so the diagram looked right and the setup was
            // in pieces.
            //
            // Only on a MOVE.  Copying a tile does not copy what was written about the square it
            // came from: two squares cannot both be one station.
            //
            // A copy still has to clear the square it lands ON, which a move does through moveTile.
            // Dropping a tile from the palette onto a set-up station used to replace the sensor and
            // leave the station, its name, its length and its facings behind, describing a sensor that
            // was no longer there - and reconcile cannot find that, because the square is not empty.
            if (!move && tellAutonomy)
            {
                org.traincontrol.automationui.AutonomySession landing = parent.getAutonomySession();

                if (landing != null)
                {
                    landing.forgetTiles(java.util.Collections.singletonList(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), getX(destLabel), getY(destLabel))));

                    rememberAutonomy(landing);
                }
            }

            if (move && lastX >= 0 && lastY >= 0)
            {
                org.traincontrol.automationui.AutonomySession autonomy =
                    parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTile(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), lastX, lastY),
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), getX(destLabel), getY(destLabel)));

                    rememberAutonomy(autonomy);
                }
            }
            
            // Avoid clearing if we are placing new items
            if (lastX != -1 || lastY != -1)
            {
                this.clearBordersFromChildren(this.newComponents);
                
                if (move)
                {
                    resetClipboard();
                    // reset tool now in clipboard
                }
            }
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex.getMessage());
            this.parent.getModel().log(ex);
        }
                        
        // Re-highlight copied tile
        this.clearBordersFromChildren(this.grid.getContainer());
        
        refreshGrid();
    }
    
    public LayoutDiagram getMarklinLayout()
    {
        return layout;
    }
    
    /**
     * Resets the contents of the clipboard
     */
    synchronized private void resetClipboard()
    {
        this.lastX = -1;
        this.lastY = -1;
        this.lastComponent = null;
        this.toolFlag = null;

        // The group goes too.  It was never cleared anywhere, and paste prefers it - so ONE group copy
        // hijacked every later paste for the rest of the session: a user who cut a single tile and
        // pasted it got the old group back instead, spread over its whole bounding box, while the cut
        // tile stayed where it was.
        this.groupClipboard = null;
        this.clearBordersFromChildren(this.newComponents);
    }
    
    /**
     * Adds this label's location to the clipboard
     * @param label
     * @param component - the component at the label location, or one that's specified
     * @param move 
     */
    synchronized public void initCopy(LayoutLabel label, LayoutDiagramComponent component, boolean move)
    {
        // Picking up a single tile puts the group down.
        //
        // Clearing it only in resetClipboard was not enough: that runs on Escape and on a click that
        // turns out not to be a drag, but NOT on cut or copy - and both paste paths prefer the group.
        // So copying a group, then later cutting one tile and pasting, stamped the old group's whole
        // bounding box over the layout and left the cut tile where it was.
        this.groupClipboard = null;

        this.lastX = getX(label);
        this.lastY = getY(label);
        this.pauseRepaint = false;
                
        if (component != null)
        {
            lastComponent = component;
        }
        else
        {
            lastComponent = layout.getComponent(lastX, lastY);
        }
        
        this.toolFlag = move ? tool.MOVE : tool.COPY;
        
        // For colored border highlight
        this.clearBordersFromChildren(this.grid.getContainer());
        
        // Delete after pasting instead
        /* if (move)
        {
            delete(label);
        }*/
        
        this.clearBordersFromChildren(this.newComponents);
    }
    
    /**
     * The squares currently picked out.
     */
    public org.traincontrol.base.TileSelection getSelection()
    {
        return this.selection;
    }

    /**
     * Lets everything go.  What Escape does.
     */
    public void clearSelection()
    {
        if (this.selection.isEmpty()) return;

        this.selection.clear();

        this.refreshSelectionBorders();
    }

    /**
     * Draws the outline round every picked square, and takes it off the rest.
     */
    private void refreshSelectionBorders()
    {
        this.clearBordersFromChildren(this.grid.getContainer());

        for (org.traincontrol.base.TileSelection.At at : this.selection.all())
        {
            LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

            if (label != null) this.highlightLabel(label, COMPONENT_BORDER_SELECTED_COLOR);
        }

        // The rectangle currently being dragged out, in the same colour as the rest.  Drawn after,
        // so a square that is both already picked and inside the new box looks picked either way -
        // which is what it will be.
        for (org.traincontrol.base.TileSelection.At at : this.previewSelection.all())
        {
            LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

            if (label != null) this.highlightLabel(label, COMPONENT_BORDER_SELECTED_COLOR);
        }

        // And where a group being dragged would land, in the paler shade.  Last, so a square that is
        // both picked and a landing square shows the landing - during a drag that is the live answer.
        for (org.traincontrol.base.TileSelection.At at : this.landingSelection.all())
        {
            LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

            if (label != null) this.highlightLabel(label, COMPONENT_BORDER_LANDING_COLOR);
        }

        // The grip, last of all and drawn thicker, so it is visible even where it sits on a square
        // already outlined as picked.  A control nobody can see is a control nobody uses, and while
        // picking is on this is the only way to move what has been picked.
        int[] handle = selectionHandle();

        LayoutLabel grip = handle == null || !this.landingSelection.isEmpty()
            ? null : this.grid.getValueAt(handle[0], handle[1]);

        // The square that WAS the grip stops saying so.  The corner moves as squares are added to
        // the selection, and a tooltip left behind on the old one offers a drag that would do
        // something else entirely.
        if (this.handleLabel != null && this.handleLabel != grip)
        {
            this.handleLabel.setToolTipText(null);
        }

        this.handleLabel = grip;

        if (grip != null)
        {
            // Drawn as a BORDER, over the tile rather than behind it.
            //
            // A background will not do: the tile art is an opaque image, so a coloured background is
            // hidden by whatever track is drawn on that square - and the corner of a selection is as
            // likely to be a piece of track as an empty square.  A border paints AFTER the component
            // does, so this is the one hook that can put something on top.
            //
            // And it takes no space at all, which the four-pixel line it replaces did: a border is
            // laid inside the label, so a thick one squeezed the tile art and opened a visible gap
            // around the square.  A mark that moves the thing it marks is not a mark.
            grip.setBorder(new SelectionGrip());

            grip.setToolTipText(I18n.t("layout.ui.tooltipSelectionHandle"));
        }
    }

    /** The square currently drawn as the grip, so it can be told when it stops being one. */
    private LayoutLabel handleLabel;

    /**
     * Turns picking-by-drag on or off.
     *
     * @param on true to make a drag pick squares rather than move them
     */
    /**
     * Turns picking on for ONE box, then turns it off again.
     *
     * For the right-click menu.  Dragging to pick cannot be the default over track, because a drag
     * that starts on a tile has to go on moving that tile - that is the older gesture and the one
     * people use constantly.  So a drag across a diagram picks nothing unless the mode is on, and the
     * mode is a button somebody has to find, turn on, use, and remember to turn off.
     *
     * This is the same thing without the remembering: pick one box and the editor goes back to
     * normal by itself.  The button stays for the times somebody wants to pick several boxes in a
     * row.
     */
    public void selectOnce()
    {
        setSelectMode(true);

        this.selectOnce = true;
    }

    /** Whether the picking mode currently on is the one-box kind. */
    private boolean selectOnce = false;

    public void setSelectMode(boolean on)
    {
        // Turning it off, by whatever route, also cancels the one-box promise
        if (!on) this.selectOnce = false;

        this.selectMode = on;
    }

    /**
     * @return whether a drag currently picks squares
     */
    public boolean isSelectMode()
    {
        return this.selectMode;
    }

    /**
     * Takes a copy of everything picked, as the rectangle it occupies.
     *
     * @return true if there was something to copy
     */
    synchronized public boolean copySelection()
    {
        int[] bounds = this.selection.bounds();

        if (bounds == null) return false;

        java.util.List<CarriedTile> taken = new java.util.ArrayList<>();

        try
        {
            for (int x = bounds[0]; x <= bounds[2]; x++)
            {
                for (int y = bounds[1]; y <= bounds[3]; y++)
                {
                    LayoutDiagramComponent lc = layout.getComponent(x, y);

                    taken.add(new CarriedTile(x - bounds[0], y - bounds[1],
                        lc == null ? null : new LayoutDiagramComponent(lc)));
                }
            }
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex);

            return false;
        }

        this.groupClipboard = taken;

        return true;
    }

    /**
     * Whether a group copy is waiting to be pasted.
     */
    public boolean hasGroupClipboard()
    {
        return this.groupClipboard != null && !this.groupClipboard.isEmpty();
    }

    /**
     * Pastes a copied group with its top left corner at a square, as ONE undoable step.
     *
     * OVERWRITES what is there.  Merging would mean deciding, per square, whether the copy or the
     * original wins - and either answer is wrong half the time.  Overwriting is at least the answer
     * the user can predict, and undo takes it back in one step.
     *
     * @param atX the column to put the top left corner on
     * @param atY the row
     * @return true if anything was pasted
     */
    synchronized public boolean pasteSelection(int atX, int atY)
    {
        if (!hasGroupClipboard() || atX < 0 || atY < 0) return false;

        int width = 0;
        int height = 0;

        for (CarriedTile tile : this.groupClipboard)
        {
            width = Math.max(width, tile.dx);
            height = Math.max(height, tile.dy);
        }

        // Squares, not pixels - see moveSelection
        if (atX + width >= layout.getSx() || atY + height >= layout.getSy())
        {
            javax.swing.JOptionPane.showMessageDialog(this,
                I18n.t("layout.ui.errorPasteWouldLeaveTheDiagram"));

            return false;
        }

        this.snapshotLayout();

        // Every square this writes over, told to the setup ONCE when the gesture is done - see
        // forgetBuiltOver for what per-square cost
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> builtOver =
            new java.util.LinkedHashSet<>();

        boolean was = this.pauseRepaint;

        this.pauseRepaint = true;

        try
        {
            org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

            for (CarriedTile tile : this.groupClipboard)
            {
                int x = atX + tile.dx;
                int y = atY + tile.dy;

                LayoutDiagramComponent placing = tile.component == null
                    ? null : new LayoutDiagramComponent(tile.component);

                if (placing != null)
                {
                    placing.setX(x);
                    placing.setY(y);
                }

                layout.addComponent(placing, x, y);

                // Collected, not announced.  See below the loop for why.
                builtOver.add(new org.traincontrol.automationui.TileGraph.TileKey(
                    layout.getName(), x, y));
            }

            forgetBuiltOver(builtOver);

            // What was just pasted becomes the selection, so it can be nudged into place
            this.selection.clear();

            for (CarriedTile tile : this.groupClipboard)
            {
                this.selection.add(atX + tile.dx, atY + tile.dy);
            }
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex);
        }
        finally
        {
            this.pauseRepaint = was;
        }

        this.refreshGrid();

        this.refreshSelectionBorders();

        return true;
    }

    /**
     * Moves every picked square by a delta, as ONE undoable step.
     *
     * Read everything first, then clear, then write.  A group that overlaps where it is going - which
     * is every short drag - would otherwise erase tiles it had already placed: move a row one square
     * right, and clearing the second source square deletes the tile just written there from the first.
     *
     * Refused WHOLE if any square would land off the diagram, rather than clipped.  Clipping would
     * drop a column of track off the side with nothing on screen saying so.
     *
     * @param dx columns to move by
     * @param dy rows to move by
     * @return true if the move happened
     */
    synchronized public boolean moveSelection(int dx, int dy)
    {
        if (this.selection.isEmpty() || (dx == 0 && dy == 0)) return false;

        // The DIAGRAM's size in squares.  LayoutGrid.maxWidth and maxHeight are PIXELS - width times
        // tile size - so passing them here compared a column number against a pixel count and the
        // refusal never fired.  Since the move clears every source square before writing any
        // destination, the first write past the edge threw with the group already deleted.
        if (!this.selection.fitsAfterMove(dx, dy, layout.getSx(), layout.getSy()))
        {
            javax.swing.JOptionPane.showMessageDialog(this,
                I18n.t("layout.ui.errorSelectionWouldLeaveTheDiagram"));

            return false;
        }

        this.snapshotLayout();

        boolean was = this.pauseRepaint;

        this.pauseRepaint = true;

        try
        {
            // Read
            java.util.List<org.traincontrol.base.TileSelection.At> from = this.selection.all();
            java.util.List<LayoutDiagramComponent> carried = new java.util.ArrayList<>();

            for (org.traincontrol.base.TileSelection.At at : from)
            {
                LayoutDiagramComponent lc = layout.getComponent(at.getX(), at.getY());

                carried.add(lc == null ? null : new LayoutDiagramComponent(lc));
            }

            org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

            // What autonomy holds about these squares moves with them, ALL of it and all at once.
            //
            // Not per tile inside the loops below.  A group dragged one square right has every source
            // square landing on another source square, so a per-tile move reads a store that the
            // previous iteration has already written - which made the group eat itself: two set-up
            // squares side by side, dragged right, and the first move overwrote the second square
            // before the second came to read it.  Dragging LEFT happened to work, which made it look
            // intermittent rather than wrong.
            //
            // And the whole setup, not the caption alone.  The station designation, the name, the
            // facings, the arrival restrictions, the length and the placement are all keyed by
            // SQUARE, so a moved tile used to leave every one of them behind on coordinates that now
            // hold no track - and the next reconcile dropped the station for good.
            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                org.traincontrol.automationui.TileGraph.TileKey> moving =
                new java.util.LinkedHashMap<>();

            for (org.traincontrol.base.TileSelection.At at : from)
            {
                moving.put(
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), at.getX(), at.getY()),
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), at.getX() + dx, at.getY() + dy));
            }

            // Clear
            for (org.traincontrol.base.TileSelection.At at : from)
            {
                layout.addComponent(null, at.getX(), at.getY());
            }

            // Write
            for (int i = 0; i < from.size(); i++)
            {
                org.traincontrol.base.TileSelection.At at = from.get(i);

                int toX = at.getX() + dx;
                int toY = at.getY() + dy;

                LayoutDiagramComponent carrying = carried.get(i);

                if (carrying != null)
                {
                    carrying.setX(toX);
                    carrying.setY(toY);
                }

                layout.addComponent(carrying, toX, toY);
            }

            // The setup follows the track, and only once the track has actually moved.
            //
            // This ran BEFORE the two loops above, which was wrong in a way that took a corrupted
            // layout to notice: moveTiles rebuilds the graph from the pages, and at that moment the
            // pages still had every tile at its old square while the store had already been told the
            // stations were at their new ones.  The graph was therefore built from a diagram and a
            // setup that disagreed about every square in the selection - and the result of that was
            // saved.
            //
            // Written out here too, for the same reason: nothing else saves this session, and the
            // reset that follows an edit to a diagram throws it away.
            if (autonomy != null)
            {
                autonomy.moveTiles(moving);

                rememberAutonomy(autonomy);
            }

            // The move is finished, so the selection is finished with.
            //
            // It used to travel with the tiles, on the reasoning that a group could then be dragged
            // twice - and dragging is the only thing that calls this, so that was the whole of the
            // argument.  Adam asked for the opposite after using it: a selection that outlives its
            // move is a selection still armed when the user has moved on, and the next Delete or
            // Control+X is aimed at squares they stopped thinking about.  Picking again is three
            // clicks; noticing that twenty squares are still picked is luck.
            this.selection.clear();
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex);
        }
        finally
        {
            this.pauseRepaint = was;
        }

        this.refreshGrid();

        this.refreshSelectionBorders();

        return true;
    }

    /**
     * Picks out a whole row, or adds it to what is already picked.
     *
     * Dragging a box works, and on a diagram sixty squares wide dragging one accurately across all
     * sixty is its own small ordeal - the pointer has to stay in the row the whole way. Naming the
     * row is exact, takes one click, and cannot go one square wrong.
     *
     * @param y the row
     */
    public void selectRow(int y)
    {
        if (y < 0 || y >= layout.getSy()) return;

        this.selection.addRectangle(0, y, layout.getSx() - 1, y);

        this.refreshSelectionBorders();
    }

    /**
     * Picks out a whole column.
     *
     * @param x the column
     */
    public void selectColumn(int x)
    {
        if (x < 0 || x >= layout.getSx()) return;

        this.selection.addRectangle(x, 0, x, layout.getSy() - 1);

        this.refreshSelectionBorders();
    }

    /**
     * Picks out everything on the page.
     */
    public void selectAll()
    {
        this.selection.addRectangle(0, 0, layout.getSx() - 1, layout.getSy() - 1);

        this.refreshSelectionBorders();
    }

    /**
     * Copies the picked squares and then clears them, as one step.
     *
     * The verb the group was missing.  Copy, paste, rotate, fill and delete were all there and cut
     * was not, so moving a run of track to another part of the diagram meant copying it, pasting it,
     * and going back to delete the originals - three actions for one idea, with the third the easy
     * one to forget and the one that leaves a duplicate railway behind.
     *
     * Copy first and only delete if it took, because a cut that lost the tiles and kept nothing on
     * the clipboard is the one outcome that cannot be undone by pasting.
     *
     * @return true if anything was cut
     */
    synchronized public boolean cutSelection()
    {
        if (this.selection.isEmpty()) return false;

        if (!this.copySelection()) return false;

        // Held across the delete and put back afterwards.
        //
        // delete() ends by resetting the clipboard - it has to, because the single-tile clipboard it
        // shares would otherwise go on offering a tile that has been removed - and that wiped the copy
        // taken one line above.  A cut therefore took the track away and left nothing to paste, which
        // is the one outcome this method's own comment says it was written to avoid.
        java.util.List<CarriedTile> carried = this.groupClipboard;

        boolean cut = this.deleteSelection();

        this.groupClipboard = carried;

        return cut;
    }

    /**
     * Puts the armed tile on every picked square, as ONE undoable step.
     *
     * The verb the selection was missing.  Copy, paste, rotate and delete were all there, and the most
     * ordinary thing anybody wants a row of squares for - laying a run of straight track, or turning a
     * line of plain track into a row of nothing - had to be done a square at a time, which is the
     * thing the selection exists to avoid.
     *
     * Needs a tile armed from the palette; with nothing armed there is nothing to put down, and it
     * says so rather than clearing the squares, because "fill with nothing" is Delete and the two
     * should not be one gesture with a hidden mode.
     *
     * @return true if anything was filled
     */
    synchronized public boolean fillSelection()
    {
        if (this.selection.isEmpty()) return false;

        if (!this.hasToolFlag() || this.lastComponent == null)
        {
            JOptionPane.showMessageDialog(this, I18n.t("layout.ui.errorNothingToFillWith"));

            return false;
        }

        this.snapshotLayout();

        // Every square this writes over, told to the setup ONCE when the gesture is done - see
        // forgetBuiltOver for what per-square cost
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> builtOver =
            new java.util.LinkedHashSet<>();

        boolean was = this.pauseRepaint;

        this.pauseRepaint = true;

        try
        {
            org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

            for (org.traincontrol.base.TileSelection.At at : this.selection.all())
            {
                LayoutDiagramComponent placing = new LayoutDiagramComponent(this.lastComponent);

                placing.setX(at.getX());
                placing.setY(at.getY());

                layout.addComponent(placing, at.getX(), at.getY());

                // Collected, not announced - see pasteSelection
                builtOver.add(new org.traincontrol.automationui.TileGraph.TileKey(
                    layout.getName(), at.getX(), at.getY()));
            }

            forgetBuiltOver(builtOver);
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex);
        }
        finally
        {
            this.pauseRepaint = was;
        }

        this.refreshGrid();

        this.refreshSelectionBorders();

        return true;
    }

    /**
     * Deletes every picked square, as ONE undoable step.
     *
     * One snapshot for the group rather than one per square: a user who erases a yard and changes
     * their mind means the yard, not the last tile of it.
     *
     * @return true if anything was picked to delete
     */
    synchronized public boolean deleteSelection()
    {
        if (this.selection.isEmpty()) return false;

        this.snapshotLayout();

        // Suppressed so the per-tile delete does not take a snapshot of its own inside the group one
        boolean was = this.pauseRepaint;

        this.pauseRepaint = true;

        try
        {
            for (org.traincontrol.base.TileSelection.At at : this.selection.all())
            {
                LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

                if (label != null) this.delete(label);
            }
        }
        finally
        {
            this.pauseRepaint = was;
        }

        this.clearSelection();

        this.refreshGrid();

        return true;
    }

    /**
     * Rotates every picked square where it stands, as one undoable step.
     *
     * In place, not around the group: turning a yard through ninety degrees would move every tile of
     * it, which is a different operation and one nobody asked for.
     *
     * @return true if anything was picked to rotate
     */
    synchronized public boolean rotateSelection()
    {
        if (this.selection.isEmpty()) return false;

        this.snapshotLayout();

        boolean was = this.pauseRepaint;

        this.pauseRepaint = true;

        try
        {
            for (org.traincontrol.base.TileSelection.At at : this.selection.all())
            {
                LayoutDiagramComponent lc = layout.getComponent(at.getX(), at.getY());

                if (lc == null) continue;

                lc.rotate();

                try
                {
                    layout.addComponent(lc, at.getX(), at.getY());
                }
                catch (IOException ex)
                {
                    this.parent.getModel().log(ex);
                }
            }
        }
        finally
        {
            this.pauseRepaint = was;
        }

        this.refreshGrid();

        this.refreshSelectionBorders();

        return true;
    }

    /**
     * Deletes this label from the layout
     * @param label 
     */
    synchronized public void delete(LayoutLabel label)
    {
        delete(label, true);
    }

    /**
     * @param tellAutonomy false when the CALLER is going to tell the setup what happened
     *
     * A bulk column or row move deletes the line it is vacating, one square at a time, and then tells
     * the setup that the whole line moved.  If each of those deletes had already announced itself, the
     * announcement would be wrong: it says the track is gone, and the track is not gone, it is one
     * column to the right.  The captions were the visible half of that - every station name on a moved
     * column was thrown away by the clearing loop moments before the thing that would have carried it
     * ran - and it is why this parameter exists rather than the callers reaching past delete().
     */
    synchronized public void delete(LayoutLabel label, boolean tellAutonomy)
    {
        LayoutDiagramComponent lc = layout.getComponent(getX(label), getY(label));

        if (lc != null)
        {       
            try
            {
                if (!this.pauseRepaint)
                {
                    this.snapshotLayout();
                }

                layout.addComponent(null, getX(label), getY(label));

                // What autonomy had written about that square goes with it.  A caption on the square,
                // and any caption elsewhere naming it: both are about track that no longer exists.
                // Left behind, the label stayed where it was naming nothing, with no way to remove it
                // - and putting any tile back on that square made it look like the new tile's label.
                org.traincontrol.automationui.AutonomySession autonomy =
                    tellAutonomy ? parent.getAutonomySession() : null;

                // Only when something was actually forgotten.
                //
                // forgetCaptionsAt returns whether it changed anything, and this ignored it - so
                // deleting a square that had no caption still wrote the whole setup to disk, every
                // file of it.  Deleting a selection is one call per square.
                if (autonomy != null && autonomy.forgetCaptionsAt(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), getX(label), getY(label))))
                {
                    rememberAutonomy(autonomy);
                }

                this.resetClipboard();
            }
            catch (IOException ex)
            {
                // A tile edit that fails should say so.  This was silent, so the component simply did
                // not appear and nothing explained why.  Logged rather than shown as a dialog: the
                // editor calls this per placement, and a dialog per failed tile would be worse.
                this.parent.getModel().log(ex);
            }

            refreshGrid();
        }
    }
        
    /**
     * Rotates the specified label
     * @param label 
     */
    synchronized public void rotate(LayoutLabel label)
    {       
        LayoutDiagramComponent lc = layout.getComponent(getX(label), getY(label));
        
        if (lc != null)
        {    
            this.snapshotLayout();

            lc.rotate();

            try
            {
                layout.addComponent(lc, grid.getCoordinates(label)[0], grid.getCoordinates(label)[1]);
            }
            catch (IOException ex)
            {
                // A tile edit that fails should say so.  This was silent, so the component simply did
                // not appear and nothing explained why.  Logged rather than shown as a dialog: the
                // editor calls this per placement, and a dialog per failed tile would be worse.
                this.parent.getModel().log(ex);
            }

            refreshGrid();
        }
    }
    
    /**
     * Changes the text
     * @param label 
     */
    public void editText(LayoutLabel label)
    {       
        LayoutDiagramComponent lc = layout.getComponent(getX(label), getY(label));
                            
        if (lc != null)
        {       
            String newText = (String) JOptionPane.showInputDialog(
                this,
                I18n.t("layout.ui.promptEnterTileLabel"),
                I18n.t("layout.ui.dialogEditLabel"),
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                lc.getLabel() // Default value
            );
            
            if (newText != null)
            {
                this.snapshotLayout();

                lc.setLabel(newText);
            }

            try
            {
                layout.addComponent(lc, grid.getCoordinates(label)[0], grid.getCoordinates(label)[1]);
                this.resetClipboard();
            }
            catch (IOException ex)
            {
                // A tile edit that fails should say so.  This was silent, so the component simply did
                // not appear and nothing explained why.  Logged rather than shown as a dialog: the
                // editor calls this per placement, and a dialog per failed tile would be worse.
                this.parent.getModel().log(ex);
            }

            refreshGrid();
        }
    }
    
    /**
     * Changes the text using dropdown options, used for autonomy stations
     * @param label
    */
    public void editTextWithDropdown(LayoutLabel label)
    {
        // Station captions are not text on the diagram any more.
        //
        // They were, and this dialog is what wrote them: it picked a Point by name and stored
        // "Point:<name>" into a text square.  That binding broke whenever the station was renamed, named
        // nothing at all once a station became several Points, and made saving the layout the only way
        // to record a caption - which is what put autonomy in the business of rewriting layout files.
        //
        // A caption now belongs to the autonomy setup and points at the sensor SQUARE, so it is set
        // where the rest of autonomy is set.  Said plainly rather than removed from the menu, because
        // somebody who used to do it here needs to be told where it went.
        JOptionPane.showMessageDialog(
            this,
            I18n.t("layout.ui.infoStationLabelsMovedToAutonomy")
        );
    }
    
     /**
     * Changes the address
     * @param label 
     */
    public void editAddress(LayoutLabel label)
    {               
        LayoutDiagramComponent lc = layout.getComponent(getX(label), getY(label));
                    
        if (lc != null)
        {     
            try
            {
                JTextField textField = new JTextField()
                {
                    @Override
                    public void addNotify()
                    {
                        super.addNotify();
                        javax.swing.Timer focusTimer = new javax.swing.Timer(50, e -> requestFocusInWindow());
                        focusTimer.setRepeats(false);
                        focusTimer.start();
                    }
                };
                
                textField.addKeyListener(new KeyAdapter()
                {
                    @Override
                    public void keyReleased(KeyEvent evt) {
                        TrainControlUI.validateInt(evt, false); // Call your validation method
                    }
                });
       
                textField.setText(Integer.toString(lc.getLogicalAddress()));

                // 91r == addr 182
                // 91g == addr 183
                
                // Create and display the JPanel LayoutEditorAddressPopup
                LayoutEditorAddressPopup addressPopup = new LayoutEditorAddressPopup(lc, parent);
                
                addressPopup.setAddress(Integer.toString(lc.getLogicalAddress()));
                addressPopup.getGreenButton().setSelected(lc.isLogicalGreen());
                
                if (!lc.isUncoupler())
                {
                    addressPopup.getGreenButton().setVisible(false);
                    addressPopup.getGreenButton().setSelected(false);
                }
                
                int result = JOptionPane.showOptionDialog(
                    this,
                    addressPopup,
                    I18n.t("layout.ui.editAddress"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    TrainControlUI.OK_CANCEL_OPTS,
                    TrainControlUI.OK_CANCEL_OPTS[0]
                );

                // Process the input when OK is clicked
                if (result == JOptionPane.OK_OPTION)
                {
                    this.snapshotLayout();
                    
                    // Retrieve the address from LayoutEditorAddressPopup and use it
                    int newAddress = Integer.parseInt(addressPopup.getAddress());
                    lc.setLogicalAddress(newAddress, addressPopup.getProtocol(), addressPopup.getGreenButton().isSelected());
                    
                    layout.addComponent(lc, grid.getCoordinates(label)[0], grid.getCoordinates(label)[1]);
                    lc.setProtocol(addressPopup.getProtocol());
                    
                    this.resetClipboard();
                }
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(this, I18n.f("error.generic", ex.getMessage()));
                this.parent.getModel().log(ex);
            }

            refreshGrid();
        }
    }
    
    /**
     * What a tile's border looks like when nothing is happening to it.
     *
     * OB-028: "in the autonomy editor, the gray grid is not needed. show the track diagram as it appears
     * in the viewer, without the tile borders. make sure the borders return in the editor."
     *
     * NO border at all in autonomy mode, so the tiles sit flush exactly as they do in the viewer.
     *
     * The first attempt used an empty border of the same thickness, reasoning that keeping the insets
     * would stop the artwork shifting when a hover swapped it for a coloured line. Adam: "The grid is
     * correctly gone, but now there is a gap between tiles (essentially a white grid)." Of course it
     * is - an inset with nothing drawn in it shows the panel behind, so the grey grid was replaced by
     * a white one, which is not what "as it appears in the viewer" means.
     *
     * The shift it was guarding against cannot happen: `receiveMoveEvent` returns immediately in
     * autonomy mode - "hover previews what a diagram edit would place; in autonomy mode nothing is
     * being placed" - so nothing ever swaps this border for another one. The care was real and aimed
     * at the wrong mode; FR-006's version of it, for the layout editor, still applies.
     *
     * The palette keeps its visible border in both modes: those tiles are a menu of things to place, not
     * a picture of a railway, and the border is what separates one from the next.
     *
     * @param palette whether this is the palette of new components rather than the diagram
     * @param autonomy whether the editor is in autonomy mode
     * @return the border to leave the tile with
     */
    public static Border restingBorder(boolean palette, boolean autonomy)
    {
        return restingBorder(palette, autonomy, showGrid());
    }

    /**
     * What a square's border looks like when nothing is hovering it.
     *
     * @param palette a tile in the toolbox rather than on the diagram, which keeps its thicker line
     * @param autonomy the autonomy editor, which draws its grid ON the squares rather than around them
     * @param grid whether the grey grid is being drawn
     * @return the border, or NULL when the grid is off.  Null rather than an empty border of the same
     *         width, because an empty border still takes up room and the room shows the panel behind
     *         it: a white grid where the grey one used to be (MT-127).  The hover outline answers the
     *         same question, so nothing moves - see overlayLine.
     */
    public static Border restingBorder(boolean palette, boolean autonomy, boolean grid)
    {
        if (palette)
        {
            return BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR,
                NEW_COMPONENT_BORDER_WIDTH);
        }

        // NULL when the grid is off, not an empty border of the same width.
        //
        // An empty border still takes up room, and the room shows the panel behind it - a white grid
        // where the grey one used to be, which is MT-127 and which testEditorSurfaceRules pins.  Tiles
        // with the grid off have to sit flush, exactly as they do in the viewer.
        //
        // That leaves FR-006's other half - "make sure hovering doesn't increase tile widths when it is
        // off" - to be answered on the HIGHLIGHT side instead, by a border that paints without
        // reserving anything.  See overlayLine.
        if (!grid) return null;

        // BOTH editors reserve the room, and this used to be the one place they differed (OB-091).
        //
        // The autonomy editor drew its grid with overlayLine - painted on the square, reserving
        // nothing - on the reasoning that MT-127 requires its tiles to sit flush. That reads across
        // two states as though it were one. MT-127 is about the grid being OFF: "the grid is correctly
        // gone, but now there is a gap between tiles (essentially a white grid)", and the branch above
        // answers it by returning null, which is unchanged.
        //
        // With the grid ON, reserving nothing means the cell is sized as though there were no line and
        // then has one painted over it, so the line eats a pixel of the tile art. Adam: "enabling the
        // grid widens the track diagram in the layout editor (how it always was, because there is a
        // double line in between cells), but not in the autonomy editor. Make the behavior of the
        // autonomy editor match so that there are no tile truncations."
        //
        // So the widening is not a defect to be avoided here - it is what makes room for the line, and
        // the layout editor has always paid it. overlayLine is still what the HOVER outline uses, where
        // the resting border is null and nothing may move.
        return BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR, COMPONENT_BORDER_WIDTH);
    }

    /**
     * A line drawn ON a component rather than around it: it paints, and it reserves no space.
     *
     * The hover outline has to be visible without changing anything's size.  An ordinary LineBorder
     * takes its width in insets, so putting one on a square that was resting with no border at all made
     * that square a pixel bigger in each direction and pushed the diagram along in front of the pointer
     * (FR-006).  A border is allowed to paint outside what it reserves; nothing else on the square draws
     * in that outermost pixel, so the line has it to itself.
     *
     * Only needed where the resting border is null.  With the grid on, both borders are one-pixel lines
     * and the sizes already match.
     *
     * @param color the outline colour
     * @param width how thick to draw it
     * @return a border that paints a line and reports no insets
     */
    private static Border overlayLine(final Color color, final int width)
    {
        return new javax.swing.border.AbstractBorder()
        {
            @Override
            public void paintBorder(java.awt.Component c, java.awt.Graphics g,
                int x, int y, int w, int h)
            {
                java.awt.Color was = g.getColor();

                g.setColor(color);

                for (int ring = 0; ring < width; ring++)
                {
                    g.drawRect(x + ring, y + ring, w - 1 - ring * 2, h - 1 - ring * 2);
                }

                g.setColor(was);
            }

            @Override
            public java.awt.Insets getBorderInsets(java.awt.Component c)
            {
                return new java.awt.Insets(0, 0, 0, 0);
            }

            @Override
            public java.awt.Insets getBorderInsets(java.awt.Component c, java.awt.Insets insets)
            {
                insets.set(0, 0, 0, 0);

                return insets;
            }
        };
    }

    /**
     * Puts the grid switch into the window's own Toggle Visibility group.
     *
     * FR-006: "make the gray grid an option you can toggle in the visible elements.  on by default,
     * but persisted if turned off."
     *
     * By REPLACING the Text Labels box with a small column holding both, which is the trick the
     * autonomy sidebar already uses one control along: a GroupLayout cannot have a component added to
     * it after the fact, but it can swap one for another - so this lands where it belongs without
     * touching the generated form, which must not be edited by hand.
     *
     * Text Labels rather than Addresses, because Addresses is the one autonomy mode swaps for a column
     * of its own; two replacements of one component would fight.
     */
    private void mountGridToggle()
    {
        if (!(formPane.getLayout() instanceof javax.swing.GroupLayout)) return;

        showGridCheckbox = new javax.swing.JCheckBox(I18n.t("layout.ui.grid"), showGrid());

        showGridCheckbox.setFont(this.showTextCheckbox.getFont());
        showGridCheckbox.setFocusable(false);
        showGridCheckbox.setOpaque(false);
        showGridCheckbox.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("layout.ui.tooltipGrid")));

        showGridCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                setShowGrid(showGridCheckbox.isSelected());
            }
        });

        javax.swing.JPanel column = new javax.swing.JPanel();

        column.setLayout(new javax.swing.BoxLayout(column, javax.swing.BoxLayout.Y_AXIS));
        column.setOpaque(false);

        ((javax.swing.GroupLayout) formPane.getLayout()).replace(this.showTextCheckbox, column);

        this.showTextCheckbox.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        showGridCheckbox.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        column.add(this.showTextCheckbox);
        column.add(javax.swing.Box.createVerticalStrut(HEADING_GAP));
        column.add(showGridCheckbox);
    }

    private javax.swing.JCheckBox showGridCheckbox;

    /**
     * Whether the editor draws its grey grid.
     *
     * On by default and remembered when turned off (FR-006).  Static because the border is: the label
     * that asks has no editor to ask, and the answer is a property of the application rather than of
     * one window.
     *
     * @return true when the grid should be drawn
     */
    public static boolean showGrid()
    {
        return TrainControlUI.getPrefs() == null
            || TrainControlUI.getPrefs().getBoolean(TrainControlUI.EDITOR_GRID_PREF, true);
    }

    /**
     * Remembers the choice and redraws every square to it.
     *
     * @param show whether to draw the grid
     */
    public void setShowGrid(boolean show)
    {
        if (TrainControlUI.getPrefs() != null)
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.EDITOR_GRID_PREF, show);
        }

        // The borders as they stand are the old answer, so they are all put back.  Both panels: the
        // palette keeps its own thicker line either way, and asking it costs nothing.
        clearBordersFromChildren(this.newComponents);

        if (this.grid != null) clearBordersFromChildren(this.grid.getContainer());
    }

    /**
     * Picks a station label up, so the drag has something in it (FR-035).
     *
     * Adam: "snapshot the label so users can see it is being moved (make it follow the cursor while
     * held down)."
     *
     * On the LAYERED PANE's drag layer rather than in the diagram. The diagram is a grid whose
     * components are laid out by their constraints, and a floating copy has no cell to be in; the drag
     * layer is the one place in a Swing window that exists for something that is on top of everything
     * and belongs to no layout.
     *
     * The grab offset is where the pointer took hold of the pill, so the label hangs off the cursor at
     * the same spot it was picked up. Started from the square rather than the label itself, there is no
     * such spot and the caller centres it.
     *
     * @param shot a picture of the caption
     * @param grabX where in that picture the pointer took hold
     * @param grabY likewise
     */
    public void showCaptionGhost(java.awt.Image shot, int grabX, int grabY)
    {
        hideCaptionGhost();

        if (shot == null) return;

        this.captionGhost = new JLabel(new javax.swing.ImageIcon(shot));
        this.captionGhost.setSize(this.captionGhost.getPreferredSize());
        this.captionGrab = new java.awt.Point(grabX, grabY);

        getLayeredPane().add(this.captionGhost, javax.swing.JLayeredPane.DRAG_LAYER);
    }

    /**
     * Moves the picked-up label to the pointer (FR-035).
     *
     * @param at where the pointer is, in this window's coordinates
     */
    public void moveCaptionGhost(java.awt.Point at)
    {
        if (this.captionGhost == null || at == null) return;

        java.awt.Rectangle was = this.captionGhost.getBounds();

        this.captionGhost.setLocation(at.x - this.captionGrab.x, at.y - this.captionGrab.y);

        // Both rectangles, because the layer has to forget where it was as well as learn where it is.
        getLayeredPane().repaint(was);
        getLayeredPane().repaint(this.captionGhost.getBounds());
    }

    /**
     * Puts the picked-up label down (FR-035).
     *
     * Safe to call when nothing was picked up, which matters: it runs from the release handler, and a
     * release arrives whether or not a drag ever started.
     */
    public void hideCaptionGhost()
    {
        if (this.captionGhost == null) return;

        java.awt.Rectangle was = this.captionGhost.getBounds();

        getLayeredPane().remove(this.captionGhost);

        this.captionGhost = null;

        getLayeredPane().repaint(was);
    }

    /** The floating copy of a caption being dragged, or null when nothing is. */
    private JLabel captionGhost;

    /** Where in that copy the pointer took hold of it. */
    private java.awt.Point captionGrab = new java.awt.Point();

    /**
     * Marks the square a dragged station label would land on (FR-035).
     *
     * The drag feedback, and deliberately the cheap kind: the label itself is not carried under the
     * pointer. What matters while dragging is WHERE IT WILL GO, and a line round that square says it
     * without a second copy of the caption floating over the diagram - which on a grid this dense
     * would cover the very thing being aimed at.
     *
     * Green for a square that will take it, red for one that will refuse, so the answer arrives before
     * the mouse comes up rather than as a message afterwards.
     *
     * @param label the square under the pointer, or null for none
     * @param allowed whether dropping there would work
     */
    public void showCaptionDropTarget(LayoutLabel label, boolean allowed)
    {
        clearCaptionDropTarget();

        if (label == null) return;

        this.captionDropTarget = label;

        highlightLabel(label, allowed ? new Color(0, 150, 0) : NEW_COMPONENT_BORDER_ACTIVE_COLOR);
    }

    /**
     * Takes the drag mark off whichever square is wearing it (FR-035).
     */
    public void clearCaptionDropTarget()
    {
        if (this.captionDropTarget == null) return;

        LayoutLabel was = this.captionDropTarget;

        // Cleared BEFORE the border is put back, so a repaint that arrives in the middle of this does
        // not find a target that is half restored.
        this.captionDropTarget = null;

        was.setBorder(restingBorder(false, isAutonomyMode()));
    }

    /** The square currently wearing the drag mark, so it can be given its own border back. */
    private LayoutLabel captionDropTarget;

    private void highlightLabel(JLabel label, Color color)
    {
        if (label != null)
        {
            boolean palette = this.getX((LayoutLabel) label) == -1;

            int width = palette ? NEW_COMPONENT_BORDER_WIDTH : COMPONENT_BORDER_WIDTH;

            // Sized the same as what this square was resting in, so hovering moves nothing.
            //
            // Asked of the ROOM the resting border takes, not of whether there is one.  It used to test
            // for null, which was the same question for as long as the only border that reserved
            // nothing was no border at all - and stopped being the same question the moment the
            // autonomy editor got a grid that paints without reserving (OB-056).  A palette tile rests
            // in a line and keeps one; anything resting in nothing, or in an overlay, gets an overlay.
            Border resting = restingBorder(palette, isAutonomyMode());

            boolean reservesRoom = resting != null
                && resting.getBorderInsets(label).left > 0;

            label.setBorder(reservesRoom
                ? BorderFactory.createLineBorder(color, width) : overlayLine(color, width));
        }
    }
    
    private void clearBordersFromChildren(JPanel panel)
    {
        if (panel != null)
        {
            for (java.awt.Component component : panel.getComponents())
            {
                if (component instanceof JLabel)
                {
                    JLabel label = (JLabel) component;

                    // Every TILE, asked of what it is rather than of whether it currently has a
                    // border.
                    //
                    // This used to be "has a border, or we are in autonomy mode" - because the resting
                    // border was null in autonomy mode, and a label already put back to nothing had to
                    // not be skipped on the way to a highlight and back, or the first hover left a line
                    // behind for good. Asking what a component IS rather than whether it currently has
                    // a border keeps that working whether the resting border is a line or nothing at
                    // all - and with the grid off it is nothing, which is what the old condition would
                    // have quietly turned into "any label that has ever been touched".
                    // Spacers excluded - they are the grid's own padding, not squares (OB-055).  The
                    // constructor never gives them a border; this is the door that was putting one on.
                    if (label instanceof LayoutLabel && !((LayoutLabel) label).isSpacer())
                    {
                        label.setBorder(restingBorder(newComponents.equals(panel), isAutonomyMode()));
                    }
                }
            }
            
            // Highlight copied tile border
            if (this.hasToolFlag() && layout.getComponent(lastX, lastY) != null)
            {
                this.highlightLabel(this.grid.getValueAt(lastX, lastY), COMPONENT_BORDER_COPIED_COLOR);
            }

            // And the PICKED squares, which this used to wipe.
            //
            // Every hover of a tile clears the borders and redraws the blue one, so a selection
            // vanished the instant the pointer moved on - while the selection itself stayed, and
            // Delete still preferred it.  A user who could see no green anywhere and pressed Delete to
            // remove the tile under the cursor deleted a whole invisible row instead.
            if (panel == this.grid.getContainer())
            {
                for (org.traincontrol.base.TileSelection.At at : this.selection.all())
                {
                    LayoutLabel picked = this.grid.getValueAt(at.getX(), at.getY());

                    if (picked != null) this.highlightLabel(picked, COMPONENT_BORDER_SELECTED_COLOR);
                }
            }
        }
    }
    // The four "shift the whole diagram" wrappers used to live here.
    //
    // Each inserted a row or a column at the hovered square and pushed everything past it along, and
    // each has been taken off the menu in favour of picking the squares that are in the way and
    // dragging them.  Removed rather than left behind: a method with no caller is the half of a
    // removal that gets forgotten, and the next person to read this file would have to work out
    // whether it was still wanted.


    /**
     * Grows the diagram by one: a column on the right and a row at the bottom.
     *
     * NOT a row at the top, which is what was asked for and what the first version of this did.
     *
     * Inserting a row at the top moves every tile on the page down by one - and everything autonomy
     * knows about that page is keyed by SQUARE. Stations, protecting signals, barred arrival sides,
     * parking and reversing marks, home locomotives, station captions: all of them name a square, and
     * none of them would move. A user with a set-up page who pressed "+" to make room would find
     * every station one row above its platform, every signal pairing pointing at plain track, and
     * every arrival restriction applied to the wrong square - silently, with the diagram still
     * looking exactly right.
     *
     * Doing it properly means rewriting every key the companion store holds for that page, which is a
     * change with its own test suite and not one to make in passing. Growing at the right and the
     * bottom moves nothing, so it is safe today; the mirror property that made top-and-bottom
     * attractive is kept, because shrinkEdges takes away exactly these two.
     *
     * FOR ADAM: the top row is deliberately not done. Say the word and it becomes a proper
     * shift-the-page operation with the store rewritten to match.
     */
    /**
     * Inserts a row or a column at the hovered square and pushes everything past it along.
     *
     * Restored after being taken out with the rest of the bulk operations when multi-select arrived.
     * The reasoning then was that making room by dragging what is in the way out of it can be seen
     * before it happens, and that is still true - but it is a different job.  Dragging moves the
     * squares you picked; this moves everything below or to the right of one square, which on a
     * diagram of two hundred tiles is not a selection anybody wants to make by hand.
     *
     * Undoable, which is what makes it safe to reach for: each one takes a snapshot first, so a
     * mis-aimed shift is one Control+Z away.
     *
     * The autonomy setup FOLLOWS the shift.  Everything it holds about a page is keyed by SQUARE, so
     * moving the track without moving those keys leaves every station, name, facing and restriction on
     * coordinates the track has walked away from - which is what these four did until moveTiles was
     * added below.
     *
     * That sentence used to read the other way round: "the reason these do not appear in autonomy mode
     * is that shifting moves the track without moving those keys." It was true when it was written and
     * false eighteen lines later in the same method, and it was stated as established fact, which is
     * the form a reader trusts without checking (TD-12).
     */
    public void shiftUp()
    {
        // Refused on the last row, rather than quietly meaning something else.
        //
        // LayoutDiagram.shiftUp normalises any start row past sy - 2 to the FIRST row, so hovering the
        // bottom row - the natural gesture for "take this empty row away" - shifted the entire page up
        // by one and destroyed row 0.  The map handed to the setup is built from the row the user
        // pointed at, so it came out empty: the track moved and every station, name, length and
        // pairing on the page stayed on the square it used to be on.  Nothing was dropped by the next
        // reconcile either, because every square still had a tile - the whole page's setup was simply
        // attached to the wrong tiles, silently.
        if (lastHoveredY < 0 || lastHoveredY > layout.getSy() - 2) return;

        this.snapshotLayout();

        try
        {
            if (lastHoveredY > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(false, lastHoveredY + 1, layout.getSy() - 1, 0, -1);

                layout.shiftUp(lastHoveredY);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    rememberAutonomy(autonomy);
                }

                refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }
    
    public void shiftDown()
    {
        // No refusal here, unlike shiftUp and shiftLeft, and the reason is worth stating rather than
        // being left to be worked out from a side effect two files away (TD-12).
        //
        // LayoutDiagram normalises an out-of-range start to the FIRST row or column in all four
        // directions, which is what made the destructive pair dangerous.  This pair calls
        // addRowsAndColumns BEFORE the range is checked, so the page has already grown by one and the
        // out-of-range start cannot be reached from a hover.  If that ever stops being true - if the
        // growth moves or becomes conditional - this needs shiftUp's guard, which costs nothing.
        this.snapshotLayout();
        
        try
        {
            if (lastHoveredY > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(false, lastHoveredY, layout.getSy() - 1, 0, 1);

                layout.shiftDown(lastHoveredY);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    rememberAutonomy(autonomy);
                }

                refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }
    
    public void shiftLeft()
    {
        // Refused on the last column - see shiftUp, which has the same normalisation behind it
        if (lastHoveredX < 0 || lastHoveredX > layout.getSx() - 2) return;

        this.snapshotLayout();

        try
        {
            if (lastHoveredX > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(true, lastHoveredX + 1, layout.getSx() - 1, -1, 0);

                layout.shiftLeft(lastHoveredX);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    rememberAutonomy(autonomy);
                }

                refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }
    
    public void shiftRight()
    {
        // No refusal here, unlike shiftUp and shiftLeft, and the reason is worth stating rather than
        // being left to be worked out from a side effect two files away (TD-12).
        //
        // LayoutDiagram normalises an out-of-range start to the FIRST row or column in all four
        // directions, which is what made the destructive pair dangerous.  This pair calls
        // addRowsAndColumns BEFORE the range is checked, so the page has already grown by one and the
        // out-of-range start cannot be reached from a hover.  If that ever stops being true - if the
        // growth moves or becomes conditional - this needs shiftUp's guard, which costs nothing.
        this.snapshotLayout();

        try
        {
            if (lastHoveredX > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(true, lastHoveredX, layout.getSx() - 1, 1, 0);

                layout.shiftRight(lastHoveredX);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    rememberAutonomy(autonomy);
                }

                refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }

    /**
     * Which squares a shift moves, and where to.
     *
     * The whole page from one row or column onwards, moved by one.  A square that is not in the
     * range is left out entirely rather than mapped to itself, so moveTiles can tell the difference
     * between a square that moved and a square something arrived on.
     *
     * @param across true to shift columns, false to shift rows
     * @param from the first row or column that moves
     * @param to the last one
     * @param dx how far each moves horizontally
     * @param dy and vertically
     * @return the moves
     */
    private java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
        org.traincontrol.automationui.TileGraph.TileKey> setupShift(boolean across, int from, int to,
        int dx, int dy)
    {
        java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
            org.traincontrol.automationui.TileGraph.TileKey> moving = new java.util.LinkedHashMap<>();

        int otherEnd = across ? layout.getSy() - 1 : layout.getSx() - 1;

        for (int line = from; line <= to; line++)
        {
            for (int other = 0; other <= otherEnd; other++)
            {
                int x = across ? line : other;
                int y = across ? other : line;

                moving.put(
                    new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), x, y),
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), x + dx, y + dy));
            }
        }

        return moving;
    }

    /**
     * Writes the current width and height into the Diagram Size heading's tooltip.
     *
     * On the heading rather than on the buttons: the buttons say what they do, and this says what
     * they would be doing it to.  Re-read after every grow and shrink, because a number written once
     * at startup is wrong from the first press.
     */
    private void showDiagramSize()
    {
        if (this.diagramSize == null) return;

        // The heading's own text as well, which the form points at the visibility key - two headings
        // reading "Diagram Size" and nothing saying Toggle Visibility.  Set here rather than in the
        // form, which is generated and not mine to edit; say the word and it moves into the .form.
        this.diagramSize.setText(I18n.t("layout.ui.sectionDiagramSize"));

        this.diagramSize.setToolTipText(I18n.f("layout.ui.tooltipDiagramSize",
            layout.getSx() + " x " + layout.getSy()));
    }

    public void growEdges()
    {
        if (layout.getSx() >= MAX_SIZE || layout.getSy() >= MAX_SIZE)
        {
            JOptionPane.showMessageDialog(this, I18n.f("layout.ui.errorMaxSizeExceeded", MAX_SIZE));

            return;
        }

        this.snapshotLayout();

        try
        {
            layout.addRowsAndColumns(1, 1);

            showDiagramSize();

            // The picked squares are let go of.  A selection that outlived a resize would still name
            // coordinates by number, and after a SHRINK some of those numbers are off the diagram -
            // the next group move would then read a square that no longer exists.  Growing is safe
            // today, but the two have to behave the same way or the difference becomes a trap.
            clearSelection();

            refreshGrid();
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }

    /**
     * Shrinks the diagram by one, undoing exactly what growEdges adds.
     *
     * Refused outright when either edge holds track. Trimming what it could would take a piece of
     * railway off the diagram to save the user a scroll, which is not a trade anybody would accept if
     * they were asked - so this asks, by refusing and saying why.
     */
    public void shrinkEdges()
    {
        if (!layout.edgesAreEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("layout.ui.errorEdgesNotEmpty"));

            return;
        }

        this.snapshotLayout();

        try
        {
            layout.trimEdges();

            showDiagramSize();

            clearSelection();

            refreshGrid();
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }

    public void addRowsAndColumns(int rows, int cols)
    {
        if (layout.getSx() >= MAX_SIZE || layout.getSy() >= MAX_SIZE)
        {
            JOptionPane.showMessageDialog(
                this,
                I18n.f("layout.ui.errorMaxSizeExceeded", MAX_SIZE)
            );
            return;
        }
        
        try
        {
            layout.addRowsAndColumns(rows, cols);
            
            refreshGrid();
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }
    
    /**
     * Toggles the display of text
     */
    public void toggleText()
    {
        try
        {
            this.layout.setEditHideText(!this.layout.getEditHideText());
            
            this.showTextCheckbox.setSelected(!this.layout.getEditHideText());

            refreshGrid();
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }
    
    /**
     * Threaded version of drawGrid
     */
    private void refreshGrid()
    {
        if (!pauseRepaint)
        {
            lock.lock();
            try
            {
                // If the method is already running, set the rerun flag and return
                if (isRunning)
                {
                    needsRerun = true;
                    return;
                }

                isRunning = true; // Mark as running

                // Execute the method logic
                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    try
                    {
                        drawGrid();
                        this.clearBordersFromChildren(this.grid.getContainer());
                    }
                    finally
                    {
                        lock.lock();
                        try
                        {
                            isRunning = false; // Reset running state
                            if (needsRerun) // Check if another execution is needed
                            {
                                needsRerun = false;
                                refreshGrid(); // Execute again
                            }
                        }
                        finally
                        {
                            lock.unlock();
                        }
                    }
                });
            }
            finally
            {
                lock.unlock();
            }
        }
    }
    
    /**
     * Toggles the display of text
     */
    public void toggleAddresses()
    {
        try
        {
            this.layout.setShowAddress(!this.layout.getShowAddress());
            
            this.showAddressCheckbox.setSelected(this.layout.getShowAddress());
            
            refreshGrid();
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }
    
    public void clear()
    {
        try
        {
            int confirmation = JOptionPane.showOptionDialog(
                this,
                I18n.t("layout.ui.confirmDeleteTrackDiagram"),
                I18n.t("layout.ui.dialogPleaseConfirm"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[0] // default selection = "Yes"
            );

            if (confirmation == JOptionPane.YES_OPTION)
            {
                this.snapshotLayout();
                
                layout.clear();
                this.resetClipboard();

                refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }   
    }
    
    /**
     * Refreshes the layout
     */
    synchronized private void drawGrid()
    {        
        // Ensures the grid is a minimum size.  This will automatically initialize the grid if the track diagram is blank
        if (this.layout.getSx() < DEFAULT_NEW_SIZE_COLS || this.layout.getSy() < DEFAULT_NEW_SIZE_ROWS)
        {
            this.addRowsAndColumns(DEFAULT_NEW_SIZE_ROWS - this.layout.getSy(),
                    DEFAULT_NEW_SIZE_COLS - this.layout.getSx());            
        }
        
        try
        {       
            // The outgoing grid lets go of the panel before the new one takes it.
            //
            // A grid hides itself until its tiles have decoded and arms two timers to reveal it; both
            // outlive a rebuild and both still hold this panel.  Left armed, the old grid's grace
            // timer drops a spinner into the middle of the new one, and a FlowLayout with an extra
            // component in it pushes the tiles along - which is a row that comes out half drawn a
            // moment after a resize.
            if (grid != null) grid.discard();

            grid = new LayoutGrid(this.layout, size,
                this.ExtLayoutPanel,
                this,
                true, parent);

            // One row of slack at the bottom of the scrollable area.
            //
            // The panel is laid out to exactly the height of the grid, so the last row sits flush
            // against the edge of the viewport - and anything that takes a few pixels (a horizontal
            // scrollbar appearing, a border, a slightly taller row of tiles in autonomy mode) pushes
            // it out of sight.  It comes back if the window is stretched, which is not something the
            // user should have to work out.
            //
            // A row rather than a fixed number of pixels, because the tile size is a setting.
            this.ExtLayoutPanel.setPreferredSize(new Dimension(
                grid.maxWidth, grid.maxHeight + size));

            grid.getContainer().revalidate();
            this.ExtLayoutPanel.revalidate();
            grid.getContainer().repaint();
            this.ExtLayoutPanel.repaint();

            // a rebuilt grid is all new labels, which know nothing about what the autonomy editor was
            // showing on their squares
            refreshAutonomyAnnotations();
        }
        catch (Exception e)
        {
            this.parent.getModel().log(e.getMessage());
            this.parent.getModel().log(e);
        }
    }
    
    /**
     * Checks if there is any history to undo
     * @return 
     */
    public boolean canUndo()
    {
        return !this.previousLayoutComponents.isEmpty();
    }
    
    /**
     * Checks if there is any history to redo
     * @return 
     */
    public boolean canRedo()
    {
        return !this.previousLayoutComponentsRedo.isEmpty();
    }
    
    /**
     * Creates a copy of the current layout
     * @return 
     */
    private List<LayoutDiagramComponent> deepCopyLayout()
    {
        List<LayoutDiagramComponent> history = new ArrayList<>();
        
        for (LayoutDiagramComponent lc : this.layout.getAll())
        {
            try
            {
                history.add(new LayoutDiagramComponent(lc));
            }
            catch (IOException ex)
            {
                this.parent.getModel().log(ex);
            }
        }
        
        return history;
    }
    
    /**
     * Saves a previous version of the layout
     */
    synchronized private void snapshotLayout()
    {        
        // Enforce size limit
        if (this.previousLayoutComponents.size() >= LayoutEditor.MAX_UNDO_HISTORY)
        {
            this.previousLayoutComponents.removeLast();
        }
                
        this.previousLayoutComponents.push(deepCopyLayout());
        this.previousCaptions.push(captionSnapshot());

        while (this.previousCaptions.size() > this.previousLayoutComponents.size())
        {
            this.previousCaptions.removeLast();
        }

        this.previousLayoutComponentsRedo.clear();
        this.previousCaptionsRedo.clear();
    }
    
    /**
     * Restores previous layout state
     */
    synchronized public void undo()
    {
        try
        {     
            if (!this.previousLayoutComponents.isEmpty())
            {         
                List<LayoutDiagramComponent> history = this.previousLayoutComponents.pop();
                this.previousLayoutComponentsRedo.push(deepCopyLayout());

                // The names on the squares travel with the squares.
                //
                // A caption belongs to the SETUP rather than to the tile - that is what stops a rename
                // rewriting every page - so it does not ride the component snapshot.  But this editor
                // moves and deletes captions as it moves and deletes tiles, so without this Ctrl+Z
                // brought a deleted platform back with no name on it, or moved a tile back and left
                // its name at the square it had been dragged to.
                java.util.Map<String, Object> captionsBefore = captionSnapshot();

                java.util.Map<String, Object> captionsToRestore =
                    this.previousCaptions.isEmpty() ? null : this.previousCaptions.pop();

                this.previousCaptionsRedo.push(captionsBefore);
                
                // Delete all existing components
                for (LayoutDiagramComponent lc : this.layout.getAll())
                {
                    layout.addComponent(null, lc.getX(), lc.getY());
                }

                // Placed previous components
                for (LayoutDiagramComponent lc : history)
                {
                    layout.addComponent(lc, lc.getX(), lc.getY());
                }

                // The setup is put back AFTER the diagram, not before it.
                //
                // restoreCaptions rebuilds the graph from the pages, so doing it first derived a graph
                // from the pre-undo diagram against the post-undo setup - the two disagreeing about
                // every square the undo touched - and nothing rebuilt afterwards.  It is the same
                // ordering moveSelection and the four shift operations were fixed for, and the comment
                // there ("it took a corrupted layout to notice") applies word for word.
                restoreCaptions(captionsToRestore);

                this.refreshGrid();
            }
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex);
        }
    }
    
    /**
     * Restores previous layout state
     */
    synchronized public void redo()
    {
        try
        {     
            if (!this.previousLayoutComponentsRedo.isEmpty())
            {         
                List<LayoutDiagramComponent> history = this.previousLayoutComponentsRedo.pop();
                this.previousLayoutComponents.push(deepCopyLayout());

                // The captions go forward with it, exactly as they came back
                java.util.Map<String, Object> captionsBefore = captionSnapshot();

java.util.Map<String, Object> captionsToRestore = this.previousCaptionsRedo.isEmpty()
                    ? null : this.previousCaptionsRedo.pop();

                this.previousCaptions.push(captionsBefore);
                
                // Enforce undo limit
                if (this.previousLayoutComponents.size() >= LayoutEditor.MAX_UNDO_HISTORY)
                {
                    this.previousLayoutComponents.removeLast();
                }

                while (this.previousCaptions.size() > this.previousLayoutComponents.size())
                {
                    this.previousCaptions.removeLast();
                }
                
                // Delete all existing components
                for (LayoutDiagramComponent lc : this.layout.getAll())
                {
                    layout.addComponent(null, lc.getX(), lc.getY());
                }

                // Placed previous components
                for (LayoutDiagramComponent lc : history)
                {
                    layout.addComponent(lc, lc.getX(), lc.getY());
                }

                // The setup after the diagram - see undo() above for why
                restoreCaptions(captionsToRestore);

                this.refreshGrid();
            }
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex);
        }
    }
    
    /**
     * Sizes the window to the diagram it is showing, within what the screen can hold.
     *
     * The default when the user has expressed no preference for this page. Reported 2026-08-22
     * (OB-003): "when switching between different pages in the editor, the window size varies and is
     * often too small".
     *
     * Both halves of that are the same cause. Window bounds are remembered PER PAGE - the index is
     * the page name and the tile size - so every page comes up at whatever size it was last left at,
     * which for a page opened once months ago on a smaller diagram is too small for what is on it
     * now. That is right when the user chose that size and wrong when nobody ever did, and the two
     * were indistinguishable because the fit was only ever computed for a brand-new window.
     *
     * So the fit is computed for any page with no remembered bounds, and CAPPED: a diagram wider than
     * the screen used to produce a window wider than the screen, with its right-hand edge and the
     * scrollbar that would have reached it both off the side.
     *
     * The minimum is set here rather than beside the preferred size because pack() honours it, and a
     * floor taller than the screen would defeat the cap - hence the clamp on both.
     */
    private void sizeForDiagram()
    {
        // The sidebar takes width from the diagram unless it is asked for as well
        int sideways = sidebar == null ? 0 : sidebar.getPreferredSize().width;

        java.awt.Rectangle usable = usableScreen();

        this.setMinimumSize(new Dimension(
            Math.min(550 + sideways + (this.size == 60 ? 200 : 0), usable.width),
            Math.min(630 + EXTRA_MINIMUM_HEIGHT + (this.size == 60 ? 320 : 0), usable.height)));

        // Never SMALLER than the window already is (MT-096).
        //
        // "It is still too small - but I think the window persistence is getting in the way." It was:
        // with one entry per page, a page sized once on a small diagram handed that size to every
        // later visit, and the fit was only computed for a brand-new window.
        //
        // Now there is one entry and the fit runs on every arrival, so it has to be able to leave a
        // window alone as well as grow it - otherwise switching from a big page to a small one would
        // shrink the window under somebody who had just made it bigger on purpose. Growing only means
        // a page that needs room gets it, and a size you chose is a floor rather than a suggestion.
        int wide = Math.max(getWidth(), grid.maxWidth + 210 + sideways);
        int high = Math.max(getHeight(), grid.maxHeight + 160);

        this.setPreferredSize(new Dimension(
            Math.min(wide, usable.width),
            Math.min(high, usable.height)));

        pack();

        // Back on screen if the fit pushed it off the bottom or the right, which it can when the
        // window was already sitting near an edge - the size changed under a position chosen for the
        // old one.
        int x = Math.max(usable.x, Math.min(getX(), usable.x + usable.width - getWidth()));
        int y = Math.max(usable.y, Math.min(getY(), usable.y + usable.height - getHeight()));

        if (x != getX() || y != getY()) setLocation(x, y);
    }

    public void render()
    {        
        javax.swing.SwingUtilities.invokeLater(() ->
        {
          try
          {
            layout.setEdit();
            this.setAlwaysOnTop(parent.isAlwaysOnTop());

            // Before the grid and before pack(), so the window is sized with the sidebar in it.
            //
            // Here rather than in the constructor because it asks which mode this window is in, and
            // that is decided by setAutonomyMode - which runs between the constructor and this, since
            // render() queues its work rather than doing it.
            mountSidebar();

            drawGrid();

            setTitle(
                I18n.f("app.ui.windowLayoutEditorTitle", this.layout.getName())
            );

            // The index FIRST, because everything below asks what has been remembered.
            //
            // ONE entry for the whole window, not one per page (MT-095). It was the page name and the
            // tile size, so every page had its own remembered position and its own remembered size -
            // which was defensible when a page change meant a new window, and became "the window
            // location memory is messing with the single window view" the moment the window stopped
            // closing: clicking a tab moved the window and resized it, because the tab you clicked
            // had its own idea of where the editor lives.
            //
            // There is one editor window now. It should be where you left it, whatever page it is
            // showing.
            this.setWindowIndex(EDITOR_WINDOW_KEY);

            if (!this.isLoaded())
            {
                sizeForDiagram();

                // Applied over the top, and only if the user actually set one.  A remembered size is a
                // decision; the diagram fit is what to do in the absence of one.
                loadWindowBounds();
            }
            else
            {
                // A window whose size was remembered keeps the floor the form gave it, which is the
                // one place the taller minimum would otherwise not reach - the form is generated and
                // cannot be edited by hand.
                // Plus the sidebar, which is new since those bounds were remembered: without it the
                // floor is a width the diagram used to have all of and now has to share.
                int sideways = sidebar == null ? 0 : sidebar.getPreferredSize().width;

                this.setMinimumSize(new Dimension(getMinimumSize().width + sideways,
                    Math.max(getMinimumSize().height, 650 + EXTRA_MINIMUM_HEIGHT)));
            }

            saveWindowBounds();
            
            setVisible(true);

            // Hide the window on close so that LayoutLabels know they can be deleted
            addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent e)
                {
                    confirmExit();
                }
            });            
          }
          catch (RuntimeException failed)
          {
            // The Edit button was disabled before this window was asked for, and openLayoutEditor
            // wraps the CONSTRUCTION in a catch that hands it back - with a comment saying why: without
            // it, autonomy setup is unreachable until the application is restarted.  But this body runs
            // on a later event, outside that catch, so anything thrown here escaped to the event
            // thread's default handler and left the button disabled for the session with no window to
            // show for it.
            //
            // Same remedy, at the other end of the queue.
            parent.autonomyEditorClosed();

            if (parent.getModel() != null) parent.getModel().log(failed);

            dispose();
          }
        });
    }
    
    /**
     * Closes this page and opens another, at a given square.
     *
     * Nothing is thrown away: an edit made here has already gone into the shared session, and the
     * window that opens is looking at the same session.  The question exists because the window
     * DISAPPEARS - work that has not been saved is easy to forget about once the window it was done in
     * has gone, and a page change is the moment to be reminded, not afterwards.
     *
     * So it asks rather than discarding, unlike confirmExit: answering yes here loses nothing.
     *
     * @param tile the square to open on
     */
    public void jumpToSquare(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        if (tile == null) return;

        // The same question the sidebar asks, and the same three answers (OB-046).
        //
        // This used to ask its own YES/NO, and "yes" neither saved nor discarded - it just left, and
        // because the setup is shared the edits survived into the window that opened next. So the user
        // was asked about their unsaved work and nothing was done with it either way.
        if (!settleUnsavedWork()) return;

        layout.setEdit(false);

        dispose();

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            parent.autonomyEditorClosed();
            parent.openAutonomyEditor(tile);
        });
    }

    /**
     * Whether this window may be left, asking about unsaved work first.
     *
     * Closing only.  Switching page or mode asks its own question - see leaveFor, which can offer to
     * SAVE because the window is coming straight back, where closing can only offer to throw the work
     * away.  What counts as unsaved is the same test in both, and is the first line of each.
     *
     * In autonomy mode the diagram was never touched, so the undo stack is empty and the question that
     * matters is whether the setup has unsaved edits.  Answering yes discards them here, before the
     * caller does anything: they live in the shared session, so leaving them would mean the question
     * was asked and its answer ignored.
     *
     * @return true when the window may go
     */
    private boolean mayLeave()
    {
        if (isAutonomyMode())
        {
            if (!autonomyPanel.isDirty()) return true;

            int result = JOptionPane.showOptionDialog(
                this,
                I18n.t("autosetup.ui.confirmExitWithoutSaving"),
                I18n.t("layout.ui.dialogExitConfirmation"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[0]
            );

            if (result != JOptionPane.YES_OPTION) return false;

            String failed = autonomyPanel.discardEdits();

            if (failed != null)
            {
                JOptionPane.showMessageDialog(this,
                    I18n.f("autosetup.ui.errorDiscardFailed", failed));

                return false;
            }

            return true;
        }

        if (!canUndo()) return true;

        int result = JOptionPane.showOptionDialog(
            this,
            I18n.t("layout.ui.confirmExitWithoutSaving"),
            I18n.t("layout.ui.dialogExitConfirmation"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            TrainControlUI.YES_NO_OPTS,
            TrainControlUI.YES_NO_OPTS[0] // default selection = "Yes"
        );

        return result == JOptionPane.YES_OPTION;
    }

    /**
     * The same save / discard / cancel question, asked by the main window before it exits.
     *
     * Commit febb8529's rule is "one save/discard/cancel question, asked wherever a page is left", and
     * it was enforced at every door out of this window - switching page, switching mode, jumping to a
     * link's other end - but not at the biggest one. `WindowClosed` never consulted the editor and
     * called System.exit, so closing the application discarded whatever was open in it without asking
     * (OB-070).
     *
     * In autonomy mode it was worse than a silent discard: the exit capture saves the setup, so work
     * the user was about to Cancel could be COMMITTED on the way out.
     *
     * Public so the window can ask; it delegates rather than restating the rule, because a second copy
     * of that dialog would be a second thing to keep in step.
     *
     * @return true when it is all right to proceed - saved, discarded, or nothing to settle
     */
    public boolean maySettleBeforeExit()
    {
        return settleUnsavedWork();
    }

    /**
     * Completes a track-mode Discard, once the exit is certain.
     *
     * A track-diagram edit has two halves in two places. The diagram is discarded by
     * `layoutEditingComplete` re-reading the pages from disk; the autonomy setup those same gestures
     * wrote - dragging a captioned tile writes it immediately, per gesture - is put back by
     * `undoAutonomyEdits`, and that is the CALLER'S job. The sidebar switch does it, and so does the
     * editor's own X, under a comment reading "Both halves of the edit, or neither". Application exit
     * did not, so answering Discard on the way out put the diagram back and left the caption on the
     * square it had been dragged to - and the next reconciling save pruned whatever no longer matched.
     *
     * It was unreachable until it was not: before exit began disposing the editor, the pre-edit note
     * was left on disk and the NEXT start completed the discard by accident.
     *
     * TWO conditions, and the first version of this fix had neither right.
     *
     * It ran on "settleUnsavedWork returned true", which is also what a clean editor returns - so with
     * nothing edited at all it rewound the setup to the editor-open snapshot, discarding whatever
     * autonomy had done in the meantime. `settledByDiscarding` is the actual question.
     *
     * And it ran from `maySettleBeforeExit`, which is the FIRST thing the exit does - before the
     * dialog that asks about running trains, which can still refuse. So the rewind happened and then
     * the application carried on, with `autonomyAsOpened` consumed and the editor's own Cancel left
     * with nothing to put back. It is called from the exit path now, beside the dispose, once
     * everything that can say no has said yes.
     *
     * Safe after a Save: saving nulls `autonomyAsOpened`, so this cannot put back a setup the user has
     * just chosen to keep - and `settledByDiscarding` is false there anyway.
     */
    public void completeExitDiscard()
    {
        if (this.settledByDiscarding && !isAutonomyMode()) undoAutonomyEdits();
    }

    /**
     * Save, discard, or stay - asked once, wherever a page is being left with work on it.
     *
     * Three answers, not two. Closing offers two because closing is final: the window is going whatever
     * happens, and the only question is whether the work goes with it. LEAVING a page is not final -
     * the user is coming straight back to the same editor somewhere else - and a two-button "throw it
     * away or stay here" makes them close the window, save, and reopen it, which is the whole thing the
     * sidebar exists to stop them doing.
     *
     * Save is the default because it is the answer that cannot lose anything, and this appears on a
     * gesture as small as clicking a tab.
     *
     * Shared with the link jump since OB-046. That path asked its own YES/NO question - and its "yes"
     * neither saved nor discarded, it simply left. The setup is SHARED, so the edits then survived into
     * the window that opened next: the user had answered a question about their unsaved work and
     * nothing had been done with it either way. Adam: "settings should be saved or discarded before
     * leaving."
     *
     * @return whether the caller may go ahead; false means the user chose to stay, or the save or the
     *         discard failed and said so
     */
    private boolean settleUnsavedWork()
    {
        boolean unsaved = isAutonomyMode() ? autonomyPanel.isDirty() : canUndo();

        // Cleared at the top, so a run that asks nothing cannot leave a Discard from last time behind.
        this.settledByDiscarding = false;

        if (unsaved)
        {
            Object[] answers = {
                I18n.t("layout.ui.switchSave"),
                I18n.t("layout.ui.switchDiscard"),
                I18n.t("layout.ui.switchCancel")
            };

            int answer = JOptionPane.showOptionDialog(
                this,
                I18n.t("layout.ui.confirmSwitchWithUnsavedWork"),
                I18n.t("layout.ui.dialogSwitchConfirmation"),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                answers,
                answers[0]
            );

            // Cancelled, or the dialog was closed.  Put the sidebar back to what is actually on
            // screen: nothing moved, and a control showing the page they did not go to would be lying
            // about where they are.
            if (answer != 0 && answer != 1)
            {
                syncSidebar();
                return false;
            }

            if (answer == 0 && !saveBeforeLeaving())
            {
                syncSidebar();
                return false;
            }

            // Discarding the SETUP has to happen here, because the setup is shared: the window that
            // opens next is looking at the same session, so edits left in it would survive a discard.
            // Discarding the DIAGRAM happens by itself - layoutEditingComplete re-reads the pages from
            // disk, and undoAutonomyEdits below puts the setup back as it was found.
            this.settledByDiscarding = answer == 1;

            if (answer == 1 && isAutonomyMode())
            {
                String failed = autonomyPanel.discardEdits();

                if (failed != null)
                {
                    JOptionPane.showMessageDialog(this,
                        I18n.f("autosetup.ui.errorDiscardFailed", failed));

                    syncSidebar();
                    return false;
                }
            }
        }



        return true;
    }

    /**
     * Closes this window and opens the editor again, on another page or in the other mode.
     *
     * The same exit and reopen the user used to do by hand, which is what the sidebar replaces.  It is
     * not a lighter operation than closing: the window is built around one diagram and one mode, the
     * diagram is re-read from disk on the way out, and the setup is put back as it was - so switching
     * asks what closing asks, and for the same reason.
     *
     * @param page the page to open
     * @param autonomy whether to open the setup rather than the track
     */
    private void leaveFor(String page, boolean autonomy)
    {
        // Save, discard, or stay - three answers, not two.
        //
        // Closing offers two because closing is final: the window is going whatever happens, and the
        // only question is whether the work goes with it.  Switching is not final - the user is coming
        // straight back to the same editor on a different page - and a two-button "throw it away or
        // stay here" makes them close the window, save, and reopen it, which is the whole thing the
        // sidebar exists to stop them doing.
        //
        // Save is the default because it is the answer that cannot lose anything, and this dialog
        // appears on a gesture as small as clicking a tab.
        if (!settleUnsavedWork()) return;
        // The window STAYS.
        //
        // Everything above this line is unchanged - the same three answers, the same save, the same
        // discard of a shared setup.  What changed is what happens after them: this used to dispose()
        // and ask the main window to open a fresh editor, and the gap between the two was a visible
        // flash of the desktop on a gesture as small as clicking a tab.
        //
        // The teardown still runs in full.  A switch is still an exit as far as the rest of the
        // application is concerned: the diagram is re-read from disk, the setup is put back as it was
        // found, and the main window is told.  Only the frame survives.
        // Posted rather than run straight from the callback - but read the correction below before
        // relying on what that buys.  OB-016, corrected by GC-A1.
        //
        // The reasoning was: autonomyEditorClosed and layoutRefreshComplete both end by calling
        // repaintLayout, which POSTS its work and builds the main window's grid inside that task;
        // arriveAt sets layout.setEdit(true) and the main window shares the LayoutDiagram, so a repaint
        // running after that flag is set builds the VIEWER in edit mode. Posting arriveAt was meant to
        // put it behind that repaint.
        //
        // IT DOES NOT. repaintLayout submits to LayoutGridRenderer - a single-thread ExecutorService -
        // and only calls invokeLater from inside THAT, so its EDT task is not queued when this one is.
        // The order between them is whatever the executor decides. An extra invokeLater cannot order
        // against a task that has not been posted yet, and the comment that used to stand here claimed
        // it could.
        //
        // What actually fixed the reported symptom is in LayoutGrid: the LAYOUT decision now asks
        // whether that grid is inside an editor rather than reading the shared flag, so the viewer no
        // longer changes shape whoever wins the race. The posting is kept because it is harmless and
        // narrows the window on the half that is still shared - see GC-A1 for the rest, which is that
        // clickability is still decided by the same flag.
        if (isAutonomyMode())
        {
            layout.setEdit(false);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                parent.autonomyEditorClosed();

                javax.swing.SwingUtilities.invokeLater(() -> arriveAt(page, autonomy));
            });

            return;
        }

        // Both halves of the edit, or neither - see confirmExit
        undoAutonomyEdits();

        javax.swing.SwingUtilities.invokeLater(() ->
            parent.layoutEditingComplete(() ->
                javax.swing.SwingUtilities.invokeLater(() -> arriveAt(page, autonomy))));
    }

    /**
     * Re-points this window at another page, or the other mode, without closing it.
     *
     * What openLayoutEditor does to a NEW frame, done to this one - and it has to do the same things,
     * because the checks it makes are not about the window.  A setup can stop being editable between
     * one click and the next: unloaded, or a train started.  Opening used to answer that by refusing
     * and leaving the old window shut; here there is a window on screen either way, so an impossible
     * mode falls back to the track and the sidebar is put back to say so.
     *
     * @param page the page to show
     * @param autonomy whether to show the setup rather than the track
     */
    private void arriveAt(String page, boolean wanted)
    {
        try
        {
            boolean autonomy = wanted;

            // Still editable?  Asked again rather than assumed from the click, for the same reason
            // openLayoutEditor asks: the answer can change while a save dialog is on screen.
            org.traincontrol.automationui.AutonomySession session =
                autonomy ? parent.editableAutonomySession() : null;

            if (autonomy && (session == null || parent.isAutonomyBusy()))
            {
                javax.swing.JOptionPane.showMessageDialog(this,
                    I18n.t(session == null ? "autosetup.ui.errorNoSetupToEdit"
                        : "autolayout.errorCannotEditWhileRunning"));

                autonomy = false;
                session = null;
            }

            // The main window's own idea of which page is being edited, which the editor has always
            // taken from it rather than the other way round.
            parent.selectLayoutPage(page);

            LayoutDiagram arriving = parent.getModel().getLayout(page);

            if (arriving == null)
            {
                // The page went away while the dialog was up - renamed, or deleted by a reload.  There
                // is nothing to show and nothing to be done about it here.
                confirmExitWithoutAsking();

                return;
            }

            // Everything remembered about squares on the page being LEFT.
            //
            // A TileKey is a page name and a pair of coordinates, and a selection carried across a
            // switch would name squares on a page that is no longer on screen - the same class of bug
            // as the setup keys that outlive a move.  Cleared before the diagram changes underneath
            // them, not after.
            this.selection.clear();
            this.previewSelection.clear();
            this.landingSelection.clear();

            this.toolFlag = null;

            // The undo history goes with the page, and this one would have been a data-loss bug.
            //
            // Each entry is a snapshot of a page's COMPONENTS, with nothing in it that says which page
            // it came from - it did not need one, because the window edited a single page for its whole
            // life. Carried across a switch, one Ctrl+Z on the new page would have written the old
            // page's track over it.
            //
            // Cleared rather than kept per page: the diagram has just been re-read from disk by the
            // teardown, so every snapshot in here describes a file state that no longer exists anyway.
            this.previousLayoutComponents.clear();
            this.previousLayoutComponentsRedo.clear();

            this.layout = arriving;

            // Leaving the mode BEFORE the new diagram is drawn.
            //
            // setAutonomyMode(null) takes the setup panel and its banner out of the window; run after
            // the grid, it would be tearing down controls that the new grid has already been wired to.
            setAutonomyMode(null);

            // The grid, the sidebar, the title and the size - the parts of render() that are about
            // WHICH diagram this is.  Not the parts that are about being a window: no pack of a
            // remembered size, no second window listener, no setVisible on something already visible.
            layout.setEdit();

            mountSidebar();

            drawGrid();

            setTitle(I18n.f("app.ui.windowLayoutEditorTitle", this.layout.getName()));

            if (session != null) setAutonomyMode(session);

            // Remembered, exactly as opening one used to record it - and only when the mode asked for
            // is the mode arrived at, because a fallback is not a choice.
            if (autonomy == (session != null)) parent.rememberEditorChoice(session != null);

            // The same one entry as on the way in - see EDITOR_WINDOW_KEY.
            //
            // The window does NOT move on a switch. Only the size is allowed to change, and only
            // upwards: sizeForDiagram grows it if the arriving page needs more room and leaves it
            // alone if it does not.
            setWindowIndex(EDITOR_WINDOW_KEY);

            sizeForDiagram();

            saveWindowBounds();

            // The editor is still open, so the button that opens one stays shut.  Both teardowns above
            // hand it back - autonomyEditorClosed directly, layoutEditingComplete through the refresh
            // it finishes with - because both of them are telling the application an editor CLOSED,
            // which for every other caller it had.
            parent.setEditLayoutEnabled(false);

            // The undo point moves with the switch, and this is the part a reused window gets wrong
            // if nobody moves it.
            //
            // autonomyAsOpened is what Cancel restores, and it was taken when the WINDOW opened. That
            // is right for a window that edits one page and then closes, and wrong in both directions
            // once it can carry on:
            //
            //   - arriving from the track editor, undoAutonomyEdits above has just consumed it, so
            //     Cancel on the new page would have had nothing to put back;
            //   - arriving from the setup editor it survives untouched, so Cancel on the new page
            //     would have undone the setup all the way to when the window opened - including work
            //     the user was explicitly asked about and chose to SAVE on the way here.
            //
            // The prompt at the top of leaveFor has already settled what happens to the previous
            // page's edits. Whatever the setup says now is what this page found.
            // Both halves of it - see takeTheUndoPoint.  The disk half used to be taken in the
            // constructor and nowhere else, so a switch moved this one and left that one behind.
            takeTheUndoPoint(parent.getAutonomySession());

            syncSidebar();

            revalidate();
            repaint();
        }
        catch (RuntimeException failed)
        {
            // A half-switched window is worse than none: it is showing one page and wired to another.
            if (parent.getModel() != null) parent.getModel().log(failed);

            confirmExitWithoutAsking();
        }
    }

    /**
     * Gives up on this window when a switch cannot be completed, without asking anything.
     *
     * The questions have already been asked and answered by the time a switch gets this far - asking
     * again would be asking about work that is no longer anywhere.
     */
    private void confirmExitWithoutAsking()
    {
        layout.setEdit(false);

        parent.setEditLayoutEnabled(true);

        dispose();
    }

    /**
     * Writes what this window has been editing, for somebody who is switching away from it.
     *
     * The same two saves the Save button makes, and the same refusal while trains are running.  Split
     * out rather than called through the button's handler because that handler CLOSES the window on
     * success, which is the one thing a switch must not do before it is ready.
     *
     * @return true when it was written, so the caller may leave
     */
    private boolean saveBeforeLeaving()
    {
        try
        {
            if (parent.getModel() != null && parent.getModel().isAutonomyRunning())
            {
                JOptionPane.showMessageDialog(this,
                    I18n.t("autolayout.errorCannotEditWhileRunning"));

                return false;
            }

            if (isAutonomyMode()) return autonomyPanel.save();

            layout.saveChanges(null, false);

            // Kept, so that undoAutonomyEdits below cannot put back a setup the user has just saved.
            // The snapshot exists for Cancel, and this is not one.
            this.autonomyAsOpened = null;

            return true;
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(this, I18n.f("error.generic", ex.getMessage()));

            return false;
        }
    }

    /**
     * If there are unsaved changes, checks with the user prior to closng the window
     */
    private void confirmExit()
    {
        if (!mayLeave()) return;

        if (isAutonomyMode())
        {
            closeAutonomyMode();
            return;
        }
        
        // Both halves of the edit, or neither.  layoutEditingComplete re-reads the pages from disk,
        // which undoes the diagram; this undoes what the same gestures wrote into the autonomy setup.
        undoAutonomyEdits();

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            parent.layoutEditingComplete();
        });
        
        this.dispose();    
    }
    
    /**
     * The strip down the side of the window: which page, and which of the two editors.
     *
     * Both of these used to mean closing the window and opening it again from the main one - and for
     * the mode, answering a dialog about which editor you wanted before you could get back to the one
     * you had just left.  On a railway with eight pages that is the whole of the work.
     *
     * It is not a lighter operation than it was: switching still closes and reopens, and still asks
     * about unsaved work.  What changes is that the user does not have to know that.
     *
     * Hidden when it would say nothing - one page, and no setup to switch to.  A control that offers
     * one choice is furniture.
     */
    private void mountSidebar()
    {
        java.util.List<String> pages = parent.getModel() == null
            ? new java.util.ArrayList<String>() : parent.getModel().getLayoutList();

        boolean offersPages = pages.size() > 1;
        boolean offersModes = parent.editableAutonomySession() != null || isAutonomyMode();

        if (!offersPages && !offersModes) return;

        sidebar = new javax.swing.JPanel();
        sidebar.setLayout(new javax.swing.BoxLayout(sidebar, javax.swing.BoxLayout.Y_AXIS));
        // Less on the right than the left (MT-097).
        //
        // Even padding looked wrong once the pages became a LIST: a list has its own inset before the
        // text and a selection bar that runs to its edge, so the eight on the right read as sixteen
        // and pushed the strip away from the diagram it belongs to.
        sidebar.setBorder(new javax.swing.border.EmptyBorder(8, 8, 8, 2));

        if (offersPages)
        {
            sidebar.add(heading(I18n.t("layout.ui.sidebarPages")));
            sidebar.add(scrollable(buildPageControl(pages), pages.size()));
            sidebar.add(javax.swing.Box.createVerticalStrut(12));
        }

        sidebar.add(heading(I18n.t("layout.ui.sidebarMode")));
        sidebar.add(buildModeControl());

        sidebar.add(javax.swing.Box.createVerticalGlue());

        // One width, whatever the pages are called.
        //
        // A page named "Lower level, back road and the carriage sidings" is a page name like any other,
        // and a strip that grows to fit it takes that width off the diagram for as long as the window
        // is open.  The buttons truncate instead and say the whole name in a tooltip.
        sidebar.setPreferredSize(new java.awt.Dimension(SIDEBAR_WIDTH, sidebar.getPreferredSize().height));
        sidebar.setMaximumSize(new java.awt.Dimension(SIDEBAR_WIDTH, Short.MAX_VALUE));
        sidebar.setMinimumSize(new java.awt.Dimension(SIDEBAR_WIDTH, 0));

        // Wrapped rather than added.
        //
        // The window's own contents are laid out by the form, which is generated and must not be
        // edited by hand, so there is nowhere in it to put this.  Putting the form's panel inside a
        // new one leaves it exactly as the designer built it.
        javax.swing.JPanel wrapper = new javax.swing.JPanel(new java.awt.BorderLayout());

        wrapper.add(sidebar, java.awt.BorderLayout.WEST);
        wrapper.add(formPane, java.awt.BorderLayout.CENTER);

        setContentPane(wrapper);
    }

    /**
     * Moves to the next page along, or the previous one (FR-036).
     *
     * Adam: "just have them call existing components to reuse the same guards/warnings."  So this ends
     * at `leaveFor`, which is exactly what clicking a row of the page list calls. The unsaved-work
     * question, the mode the new page opens in and the latch that stops a second switch starting
     * inside the first all belong to that method, and none of them is spelled out again here.
     *
     * IT WRAPS. On a railway of eight pages, stopping dead at the last one means noticing which end
     * you are at before you can decide which key to press; coming round to the first is what "scroll
     * through pages" does everywhere else. Nothing is lost by it - the pages are a ring the user is
     * looking through, not a list being consumed.
     *
     * Silent when there is nowhere to go: one page, or a switch already under way. A key that reports
     * "you cannot do that" on a railway with a single page is noise about a situation the user can see.
     *
     * @param by 1 for the next page, -1 for the one before
     */
    private void stepPage(int by)
    {
        if (switching || parent == null || parent.getModel() == null) return;

        java.util.List<String> pages = parent.getModel().getLayoutList();

        if (pages == null || pages.size() < 2) return;

        int at = pages.indexOf(layout.getName());

        // The page being looked at is not in the list, which is a state this cannot reason from.
        if (at < 0) return;

        int next = (at + by + pages.size()) % pages.size();

        leaveFor(pages.get(next), isAutonomyMode());
    }

    /**
     * Which page, as a column of tabs.
     *
     * The control type lives here and nowhere else - swap the body and the rest of the window does not
     * notice.  Toggle buttons in a group because they read as tabs down the side and stay readable at
     * eight pages; a list or a drop-down would work the same way and take the same three lines.
     */
    private javax.swing.JComponent buildPageControl(java.util.List<String> pages)
    {
        // A list, since OB-004.  It was a column of toggle buttons, which is what the comment above
        // said would happen: "exactly the sort of thing that gets decided again after somebody has
        // used it".  Twenty buttons down the side of a window read as twenty things to press; twenty
        // rows read as a list of pages, which is what they are.
        pageList = new javax.swing.JList<>(pages.toArray(new String[0]));

        pageList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        pageList.setSelectedValue(layout.getName(), true);

        // docs/UI-standards.md: regular text is Segoe UI Plain 14, black.
        //
        // 12 when this became a list (OB-014), carried over from the toggle buttons it replaced -
        // where 12 was right, because those were BUTTONS. Rows of a list are text.
        pageList.setFont(new java.awt.Font("Segoe UI", 0, 14));
        pageList.setForeground(java.awt.Color.BLACK);
        pageList.setFixedCellHeight(24);
        pageList.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        pageList.setFocusable(false);

        // The whole name for whichever row the pointer is on, since a long one is cut off by the
        // strip's fixed width - the tooltip the buttons carried, per row.
        pageList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e)
            {
                int row = pageList.locationToIndex(e.getPoint());

                pageList.setToolTipText(row < 0 ? null : pageList.getModel().getElementAt(row));
            }
        });

        // On the CLICK, not on the selection change.
        //
        // A ListSelectionListener also fires when the selection is set in code - which syncSidebar
        // does on every cancelled switch - and the switching guard would have to carry that. Worse,
        // the arrow keys would move the selection and start a switch per row travelled through.
        // A click is the gesture that means "go here", so it is the one that is listened for.
        pageList.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                int row = pageList.locationToIndex(e.getPoint());

                if (row < 0 || switching) return;

                String page = pageList.getModel().getElementAt(row);

                if (page.equals(layout.getName())) return;

                leaveFor(page, isAutonomyMode());
            }
        });

        pageList.setMaximumSize(
            new java.awt.Dimension(SIDEBAR_WIDTH - 14, Short.MAX_VALUE));

        return pageList;
    }

    /**
     * Which editor, as a pair of tabs.
     *
     * Same rule as the pages: the control type is confined to this method, because which control this
     * ought to be is exactly the sort of thing that gets decided again after somebody has used it.
     *
     * The setup half is disabled rather than hidden when there is nothing to set up.  Hidden, the
     * window would look different for a reason the user cannot see; disabled with a tooltip says what
     * is missing, and the missing thing - a configuration - is something they can go and load.
     */
    private javax.swing.JComponent buildModeControl()
    {
        javax.swing.JPanel column = new javax.swing.JPanel();
        column.setLayout(new javax.swing.BoxLayout(column, javax.swing.BoxLayout.Y_AXIS));

        trackModeButton = modeTab(I18n.t("layout.ui.sidebarTrack"), false);
        autonomyModeButton = modeTab(I18n.t("layout.ui.sidebarAutonomy"), true);

        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

        group.add(trackModeButton);
        group.add(autonomyModeButton);

        column.add(trackModeButton);
        column.add(autonomyModeButton);

        if (parent.editableAutonomySession() == null)
        {
            autonomyModeButton.setEnabled(false);
            autonomyModeButton.setToolTipText(
                AutonomyEditorPanel.wrapped(I18n.t("layout.ui.hintNoAutonomyToEdit")));
        }
        else if (parent.isAutonomyBusy())
        {
            autonomyModeButton.setEnabled(false);
            autonomyModeButton.setToolTipText(
                AutonomyEditorPanel.wrapped(I18n.t("autolayout.errorCannotEditWhileRunning")));
        }

        return column;
    }

    private javax.swing.JToggleButton modeTab(String text, final boolean autonomy)
    {
        // A radio button since OB-004, which is what this pair always meant: two mutually exclusive
        // views of one diagram.  A JRadioButton IS a JToggleButton, so the group, the fields and
        // syncSidebar are all unchanged - only what it looks like.
        javax.swing.JToggleButton tab = new javax.swing.JRadioButton(text);

        // docs/UI-standards.md: regular text is Segoe UI Plain 14, black.
        //
        // Bold 12 until OB-015, which was the button rule applied to something that had stopped being
        // a button: a radio button's label is a choice written out, and the standard reads it as text.
        tab.setFont(new java.awt.Font("Segoe UI", 0, 14));
        tab.setForeground(java.awt.Color.BLACK);

        tab.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        tab.setFocusable(false);
        tab.setOpaque(false);

        tab.setSelected(isAutonomyMode() == autonomy);

        tab.addActionListener(e ->
        {
            if (switching || isAutonomyMode() == autonomy)
            {
                syncSidebar();
                return;
            }

            leaveFor(layout.getName(), autonomy);
        });

        return tab;
    }

    /**
     * The page tabs, scrolling when there are more of them than the strip can show.
     *
     * A railway of twenty pages is a railway of twenty pages; without this the strip simply grows past
     * the bottom of the window and the last few cannot be reached at all.  No border and no horizontal
     * bar: it should look like the column it replaces until it needs to scroll.
     */
    private javax.swing.JComponent scrollable(javax.swing.JComponent column, int pages)
    {
        if (pages <= SIDEBAR_TABS_BEFORE_SCROLLING) return column;

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(column,
            javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        scroll.getVerticalScrollBar().setUnitIncrement(24);

        // Rows, not buttons: 24 to match pageList.setFixedCellHeight, so the strip shows a whole
        // number of pages rather than most of one.
        int height = SIDEBAR_TABS_BEFORE_SCROLLING * 24;

        scroll.setPreferredSize(new java.awt.Dimension(SIDEBAR_WIDTH - 16, height));
        scroll.setMaximumSize(new java.awt.Dimension(SIDEBAR_WIDTH - 16, height));

        return scroll;
    }

    private javax.swing.JLabel heading(String text)
    {
        javax.swing.JLabel label = new javax.swing.JLabel(text);

        label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        // docs/UI-standards.md: section headings are Segoe UI Semibold 13 in 0,0,155
        label.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13));
        label.setForeground(new java.awt.Color(0, 0, 155));

        // The same air under a heading as the form leaves under its own - see HEADING_GAP
        label.setBorder(new javax.swing.border.EmptyBorder(0, 0, HEADING_GAP, 0));

        return label;
    }

    /** One width for the strip, so a long page name cannot take it off the diagram */
    private static final int SIDEBAR_WIDTH = 150;

    /**
     * One remembered position and size for the editor, whatever page it is showing (MT-095).
     *
     * It used to be the page name and the tile size, which gave every page its own entry. That was
     * defensible while a page change meant a new window; once the window stopped closing, switching
     * tabs picked the window up and put it somewhere else, because the arriving page remembered
     * somewhere else.
     */
    private static final String EDITOR_WINDOW_KEY = "editor";

    /**
     * How many pages the strip shows before it starts scrolling.
     *
     * Eight when the pages were toggle BUTTONS, each 26px tall with its own border. They are rows of a
     * list now at 24px, and the strip runs the height of the window - so eight was leaving most of the
     * column empty and scrolling a railway of twelve pages for no reason (MT-056).
     */
    private static final int SIDEBAR_TABS_BEFORE_SCROLLING = 20;

    /**
     * The air under a blue heading, and between the checkboxes in the visibility column.
     *
     * One number because Adam noticed the two were different: the form leaves this much under its own
     * headings and between its own checkboxes, and everything added by hand beside them has to leave
     * the same or the column reads as two columns that happen to be touching.
     */
    static final int HEADING_GAP = 6;

    /**
     * Puts the sidebar back to what is actually on screen.
     *
     * Selecting a tab is a REQUEST, and a request that is refused - the user answered no to losing
     * their work - must not leave the control showing where they did not go.
     */
    private void syncSidebar()
    {
        switching = true;

        try
        {
            if (pageList != null) pageList.setSelectedValue(layout.getName(), true);

            if (trackModeButton != null) trackModeButton.setSelected(!isAutonomyMode());
            if (autonomyModeButton != null) autonomyModeButton.setSelected(isAutonomyMode());
        }
        finally
        {
            switching = false;
        }
    }

    /** The panel the form built, which the sidebar wraps rather than replaces */
    private java.awt.Container formPane;

    /** The strip down the side, or null when it would have nothing to offer */
    private javax.swing.JPanel sidebar;

    /** Which page, as a list - see buildPageControl */
    private javax.swing.JList<String> pageList;

    private javax.swing.JToggleButton trackModeButton;

    private javax.swing.JToggleButton autonomyModeButton;

    /** Set while the sidebar is being put back, so that doing so does not read as a click */
    private boolean switching;

    public int getLayoutSize()
    {
        return this.size;
    }
    
    /**
     * Gets the last label that was hovered by the user
     * @return 
     */
    private LayoutLabel getLastHoveredLabel()
    {
        return grid.getValueAt(this.lastHoveredX, this.lastHoveredY);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        ExtLayoutPanel = new javax.swing.JPanel();
        newComponents = new javax.swing.JPanel();
        saveButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        showTextCheckbox = new javax.swing.JCheckBox();
        showAddressCheckbox = new javax.swing.JCheckBox();
        toggleVisibility = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        diagramSize = new javax.swing.JLabel();
        plusButton = new javax.swing.JButton();
        minusButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMinimumSize(new java.awt.Dimension(750, 650));
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        ExtLayoutPanel.setBackground(new java.awt.Color(255, 255, 255));
        ExtLayoutPanel.setPreferredSize(new java.awt.Dimension(750, 474));
        ExtLayoutPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                ExtLayoutPanelMouseEntered(evt);
            }
        });

        javax.swing.GroupLayout ExtLayoutPanelLayout = new javax.swing.GroupLayout(ExtLayoutPanel);
        ExtLayoutPanel.setLayout(ExtLayoutPanelLayout);
        ExtLayoutPanelLayout.setHorizontalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 750, Short.MAX_VALUE)
        );
        ExtLayoutPanelLayout.setVerticalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 474, Short.MAX_VALUE)
        );

        jScrollPane1.setViewportView(ExtLayoutPanel);

        newComponents.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout newComponentsLayout = new javax.swing.GroupLayout(newComponents);
        newComponents.setLayout(newComponentsLayout);
        newComponentsLayout.setHorizontalGroup(
            newComponentsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 182, Short.MAX_VALUE)
        );
        newComponentsLayout.setVerticalGroup(
            newComponentsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        saveButton.setFont(new java.awt.Font("Segoe UI", 1, 11)); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/traincontrol/resources/messages"); // NOI18N
        saveButton.setText(bundle.getString("layout.ui.saveChanges")); // NOI18N
        saveButton.setFocusable(false);
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        cancelButton.setFont(new java.awt.Font("Segoe UI", 1, 11)); // NOI18N
        cancelButton.setText(bundle.getString("ui.cancel")); // NOI18N
        cancelButton.setFocusable(false);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(0, 0, 155));
        jLabel1.setText(bundle.getString("layout.ui.newComponents")); // NOI18N

        showTextCheckbox.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        showTextCheckbox.setSelected(true);
        showTextCheckbox.setText(bundle.getString("layout.ui.textLabels")); // NOI18N
        showTextCheckbox.setToolTipText("Control+L");
        showTextCheckbox.setFocusable(false);
        showTextCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showTextCheckboxActionPerformed(evt);
            }
        });

        showAddressCheckbox.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        showAddressCheckbox.setText(bundle.getString("layout.ui.addresses")); // NOI18N
        showAddressCheckbox.setToolTipText("Control+D");
        showAddressCheckbox.setFocusable(false);
        showAddressCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showAddressCheckboxActionPerformed(evt);
            }
        });

        toggleVisibility.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        toggleVisibility.setForeground(new java.awt.Color(0, 0, 155));
        toggleVisibility.setText(bundle.getString("layout.ui.toggleVisibility")); // NOI18N

        diagramSize.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        diagramSize.setForeground(new java.awt.Color(0, 0, 155));
        diagramSize.setText(bundle.getString("layout.ui.toggleVisibility")); // NOI18N

        plusButton.setFont(new java.awt.Font("Segoe UI Black", 0, 11)); // NOI18N
        plusButton.setText("+");
        plusButton.setToolTipText("");
        plusButton.setFocusable(false);
        plusButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plusButtonActionPerformed(evt);
            }
        });

        minusButton.setFont(new java.awt.Font("Segoe UI Black", 0, 11)); // NOI18N
        minusButton.setText("-");
        minusButton.setFocusable(false);
        minusButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                minusButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 675, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(newComponents, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(saveButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cancelButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(showTextCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(showAddressCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(toggleVisibility, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jSeparator1)
                        .addComponent(diagramSize, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(plusButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(minusButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(newComponents, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(diagramSize)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(plusButton)
                            .addComponent(minusButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(toggleVisibility)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(showTextCheckbox)
                        .addGap(4, 4, 4)
                        .addComponent(showAddressCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(saveButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 449, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        
        // Handle key shortcuts
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            // The shortcuts that belong to autonomy mode too, handled BEFORE the guard that turns the
            // rest of them off (GC-B2).
            //
            // It was below the return, which made it unreachable in the only mode where it does
            // anything - and toggleTrackLengths returns in the other mode, so the key did nothing at
            // all, anywhere. Filed as fixed and would have come back as "does not work".
            //
            // The guard's own sentence says why this one is different: "Every shortcut below places,
            // cuts, rotates or retextures a tile." This one shows and hides a number.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_G)
            {
                toggleTrackLengths();

                return;
            }

            // Control+L and Control+D, for the same reason and by the same rule (MT-109).
            //
            // Adam: "Control+G works, but control +L does not in the autonomy editor." Of course it
            // did not - it was below the guard, exactly where Control+G had been. Moving one key above
            // a guard fixes the key somebody just tried and leaves its neighbours where they were, and
            // these three are neighbours in every sense: all show or hide something ABOUT the diagram
            // without changing it.
            //
            // Control+D was not reported. It is here because it is the third of the same three, and
            // finding out later that the sweep stopped at the two that were mentioned is worse than
            // the original bug.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_L)
            {
                toggleText();

                return;
            }

            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_D)
            {
                toggleAddresses();

                return;
            }

            // Plus and minus walk through the pages, in BOTH editors (FR-036).
            //
            // Above the guard below, and for the reason its own sentence gives: moving to another page
            // neither places, cuts, rotates nor retextures anything. Adam asked for it in "the
            // layout/autonomy editor", and MT-109 is the ticket about keys that were filed as fixed
            // while sitting below that line doing nothing at all.
            //
            // Four keycodes for two keys. The main row reports plus as VK_EQUALS unless shift is held,
            // the numpad reports VK_ADD, and a keyboard laid out for another language may report either
            // - so all of them are taken rather than making the shortcut depend on which key somebody
            // reached for.
            if (!evt.isControlDown())
            {
                int code = evt.getKeyCode();

                if (code == KeyEvent.VK_PLUS || code == KeyEvent.VK_ADD
                    || code == KeyEvent.VK_EQUALS)
                {
                    stepPage(1);

                    return;
                }

                if (code == KeyEvent.VK_MINUS || code == KeyEvent.VK_SUBTRACT)
                {
                    stepPage(-1);

                    return;
                }
            }

            // Every shortcut below places, cuts, rotates or retextures a tile.  None of them mean
            // anything while setting autonomy up, and all of them would edit the diagram silently.
            if (isAutonomyMode()) return;

            // The selection shortcuts live HERE, in the handler that actually runs.
            //
            // They were first written as key bindings on the root pane's WHEN_IN_FOCUSED_WINDOW map,
            // which in this window is dead: every control is setFocusable(false) and tiles are JLabels
            // that never take focus, so the FRAME is the focus owner - and for a heavyweight focus
            // owner the focus manager walks the parent chain, which for a top-level frame is empty.
            // The bindings never fired at all, and Delete went on deleting whatever the mouse happened
            // to be over rather than what the user had picked.
            //
            // Selection first, then the single-tile behaviour, so nothing that worked before changes
            // while nothing is picked.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_V)
            {
                if (this.hasGroupClipboard() && getLastHoveredLabel() != null)
                {
                    this.pasteSelection(getX(getLastHoveredLabel()), getY(getLastHoveredLabel()));
                }
                else if (this.hasToolFlag() && getLastHoveredLabel() != null)
                {
                    this.executeTool(getLastHoveredLabel(), null);
                }
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X)
            {
                // The verb the selection was missing.  Copy, paste, rotate and delete were all there
                // for a group and cut was not, so moving several squares to another page meant
                // copying them, pasting them, and then going back to delete the originals by hand -
                // three steps for one idea, with the third easy to forget.
                if (!this.selection.isEmpty())
                {
                    this.cutSelection();
                }
                else
                {
                    this.initCopy(getLastHoveredLabel(), null, true);
                }
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_C)
            {
                if (!this.selection.isEmpty())
                {
                    this.copySelection();
                }
                else
                {
                    this.initCopy(getLastHoveredLabel(), null, false);
                }
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_R)
            {
                this.rotate(getLastHoveredLabel());
            }
            // Shift+C and Shift+R pasted a whole column or row from the hovered tile to the edge
            // of the diagram.  Both went with the menu items they belonged to: they are what
            // selecting the squares you mean and dragging them replaces, and a mis-aimed one wrote
            // over a whole row with undo as the only way back.
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_T)
            {
                this.editText(getLastHoveredLabel());
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_A)
            {
                this.editAddress(getLastHoveredLabel());
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_I)
            {
                // growEdges, the same thing the menu does.  This called addRowsAndColumns directly, so
                // the keyboard and the menu grew the diagram DIFFERENTLY and only the menu one had a
                // matching shrink.
                this.growEdges();
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_Z)
            {
                this.undo();
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_Y)
            {
                this.redo();
            }
            else if (evt.getKeyCode() == KeyEvent.VK_DELETE)
            {
                if (!this.selection.isEmpty())
                {
                    this.deleteSelection();
                }
                else
                {
                    this.delete(getLastHoveredLabel());
                }
            }
            else if (evt.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                // Escape lets go of everything the editor is holding, which is what a user pressing it
                // means: the picked squares, the copied group, the armed tool - and the picking MODE,
                // which stayed on afterwards with its button still pressed.  Letting go of the
                // squares but not of the mode is the half of Escape nobody asks for.
                this.clearSelection();
                this.resetClipboard();
                this.setSelectMode(false);
            }
        });
    }//GEN-LAST:event_formKeyPressed
    
    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        try
        {
            if (parent.getModel().isAutonomyRunning())
            {
                JOptionPane.showMessageDialog(this, I18n.t("autolayout.errorCannotEditWhileRunning"));
                return;
            }

            // In autonomy mode this window never touched the diagram, so Save means the autonomy setup
            // - the thing the user has actually been editing.
            if (isAutonomyMode())
            {
                // Only on a save that happened.  The edits are not lost either way - they live in
                // the shared session - but closing on a failure tells the user the opposite of what
                // the failure dialog just told them.
                if (autonomyPanel.save())
                {
                    closeAutonomyMode();
                }

                return;
            }

            layout.saveChanges(null, false);

            // Kept, so nothing can put them back.  The snapshot exists only for Cancel.
            this.autonomyAsOpened = null;

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                parent.layoutEditingComplete();
            });
            
            dispose();
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(this, I18n.f("error.generic", ex.getMessage()));
        }        
    }//GEN-LAST:event_saveButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        confirmExit();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void ExtLayoutPanelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ExtLayoutPanelMouseEntered
        clearBordersFromChildren(this.grid.getContainer());
    }//GEN-LAST:event_ExtLayoutPanelMouseEntered

    private void showAddressCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAddressCheckboxActionPerformed

        // The other half of the exclusion: turning addresses on turns lengths off, since both write a
        // number on the tile and two numbers on one square cannot be read.
        if (this.showAddressCheckbox.isSelected() && autonomyPanel != null
            && autonomyPanel.getShowLengths().isSelected())
        {
            autonomyPanel.getShowLengths().setSelected(false);
            autonomyPanel.refresh();
        }

        toggleAddresses();
    }//GEN-LAST:event_showAddressCheckboxActionPerformed

    private void showTextCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showTextCheckboxActionPerformed
        toggleText();
    }//GEN-LAST:event_showTextCheckboxActionPerformed

    private void plusButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plusButtonActionPerformed
        growEdges();
    }//GEN-LAST:event_plusButtonActionPerformed

    private void minusButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minusButtonActionPerformed
        shrinkEdges();
    }//GEN-LAST:event_minusButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel diagramSize;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton minusButton;
    private javax.swing.JPanel newComponents;
    private javax.swing.JButton plusButton;
    private javax.swing.JButton saveButton;
    private javax.swing.JCheckBox showAddressCheckbox;
    private javax.swing.JCheckBox showTextCheckbox;
    private javax.swing.JLabel toggleVisibility;
    // End of variables declaration//GEN-END:variables
}
