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

    // Max rows or columns
    public static final int MAX_SIZE = 60;
    
    final TrainControlUI parent;
    private final int size;
    /**
     * The page being edited: not final, as switching pages keeps the window (OB-005); only `arriveAt` assigns it,
     * rebuilding whatever was cached from it.  Read live, also by `LayoutEditorSelection`.
     */
    LayoutDiagram layout;
    LayoutGrid grid;
    
    int lastX = -1;
    int lastY = -1;
    LayoutDiagramComponent lastComponent = null;
    tool toolFlag = null;
    
    int lastHoveredX = -1;
    int lastHoveredY = -1;
    //private LayoutLabel lastHoveredLabel = null;

    /**
     * The square under the pointer in autonomy mode (MT-258), apart from `lastHoveredX/Y`, which are where a paste
     * lands; Control+S, the hover outline and the menu all use it.
     */
    private LayoutLabel autonomyHover = null;
    
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

    /** A highlight's record in `borderState` starts with this, then colour, width and whether it reserves room. */
    private static final String HIGHLIGHT_KEY_PREFIX = "hl:";

    /**
     * The colour a picked square is outlined in.
     *
     * Red, the strongest line on the diagram, as a selection says what the next key press acts on.
     */
    private static final Color COMPONENT_BORDER_SELECTED_COLOR = new Color(210, 0, 0);

    /**
     * Where a dragged group would land: blue, a different colour from the selection, as twenty tiles cannot follow the
     * cursor as one does.
     */
    private static final Color COMPONENT_BORDER_LANDING_COLOR = new Color(0, 90, 220);

    /** The wash over the grip: the application's "look here" yellow. */
    private static final Color HANDLE_FILL = new Color(255, 214, 0);

    /**
     * The grip's mark, a yellow wash and a four-way arrow: a Border, the only thing painted after the opaque tile; it
     * reserves no space.
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
            // Nothing: drawn over the tile
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

                // Translucent, so the track still reads
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

        private void arrows(java.awt.Graphics2D g, int x, int y, int width, int height)
        {
            int middleX = x + width / 2;
            int middleY = y + height / 2;

            // Short of the edges, so it reads as one glyph
            int reach = Math.max(4, Math.min(width, height) / 2 - 3);
            int head = Math.max(2, reach / 3);

            g.setColor(java.awt.Color.BLACK);
            g.setStroke(new java.awt.BasicStroke(Math.max(1.6f, reach / 6f),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

            g.drawLine(middleX - reach, middleY, middleX + reach, middleY);
            g.drawLine(middleX, middleY - reach, middleX, middleY + reach);

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
     * A state, not a key held, so a group can be dragged with both hands free.  Shift-click picks and unpicks, Escape
     * lets go; the geometry is TileSelection's.
     */
    final org.traincontrol.base.TileSelection selection =
        new org.traincontrol.base.TileSelection();

    /** True while a drag that began on a picked square is in progress. */
    private boolean groupDragging = false;

    /**
     * Where a shift-drag started, or -1 when one is not in progress.
     *
     * Picking a row of sixty one shift-click at a time is not a feature.
     */
    private int boxAnchorX = -1;
    private int boxAnchorY = -1;

    /** Whether dragging picks squares rather than moving them: shift-drag does too, but a button is discoverable. */
    private boolean selectMode = false;

    /** The rectangle being dragged out now, apart from the real selection so letting go elsewhere changes nothing. */
    private final org.traincontrol.base.TileSelection previewSelection =
        new org.traincontrol.base.TileSelection();

    /** Where the dragged group would land, outlined, or empty when nothing is dragged. */
    private final org.traincontrol.base.TileSelection landingSelection =
        new org.traincontrol.base.TileSelection();

    /**
     * What a group copy took: one entry per square of the BOUNDING BOX, holes included.
     *
     * A piece of railway with holes is not what the user pointed at, and pasting it would show old tiles through them.
     */
    java.util.List<CarriedTile> groupClipboard;

    /**
     * One square of a copied group, by its offset from the top left of what was copied.
     */
    static final class CarriedTile
    {
        final int dx;
        final int dy;
        final LayoutDiagramComponent component;

        CarriedTile(int dx, int dy, LayoutDiagramComponent component)
        {
            this.dx = dx;
            this.dy = dy;
            this.component = component;
        }
    }
    
    /** Where each carried tile came from, for moving a cut's setup; recorded by a copy too, to keep one shape. */
    java.util.List<org.traincontrol.automationui.TileGraph.TileKey> clipboardOrigins;

    /**
     * Whether the clipboard was CUT: a cut's setup follows the track, a copy's stays.  Cleared after one paste, or a
     * second would put one station's setup on two squares.
     */
    boolean clipboardWasCut = false;

    /**
     * The squares a cut actually emptied, the picked ones, not the clipboard's bounding box: only these may have their
     * setup moved.
     */
    java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> clipboardCutSquares;

    /**
     * The cut squares that held a tile, so `emptyCutOrigins` tells an undone cut from an outstanding one (an empty
     * square is empty either way).
     */
    java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> clipboardCutHadTiles;

    /** The generated `newComponents`, for `LayoutEditorSelection`. */
    javax.swing.JPanel newComponents()
    {
        return this.newComponents;
    }

    /**
     * The group and single-tile operations (FR-136): clipboard, paste, move, fill, delete, rotate.  Several fields and
     * methods here are package-private for it.
     */
    private final LayoutEditorSelection selectionOps = new LayoutEditorSelection(this);

    /** Shifting the page's contents and resizing it (FR-136); reads the hovered square at the moment of a gesture. */
    private final LayoutEditorReshape reshapeOps = new LayoutEditorReshape(this);

    // When true, the diagram does not get repainted, i.e. during bulk operations
    boolean pauseRepaint = false;
    
    // Undo history
    Deque<List<LayoutDiagramComponent>> previousLayoutComponents = new ConcurrentLinkedDeque<>();
    Deque<List<LayoutDiagramComponent>> previousLayoutComponentsRedo = new ConcurrentLinkedDeque<>();

    /**
     * The captions, one snapshot per component snapshot, pushed and popped together: a caption is the setup's, not the
     * tile's.  This page's only, so an undo touches nothing elsewhere.
     */
    Deque<java.util.Map<String, Object>> previousCaptions = new ConcurrentLinkedDeque<>();

    Deque<java.util.Map<String, Object>> previousCaptionsRedo = new ConcurrentLinkedDeque<>();

    /** Undo and redo (FR-136). */
    private final LayoutEditorUndo undoOps = new LayoutEditorUndo(this);

    /** The setup as this window opened, for Cancel; null with no session, cleared once the edits are kept. */
    private org.json.JSONObject autonomyAsOpened;

    /**
     * Whether the last `settleUnsavedWork` ended in Discard; its true answer also means nothing to settle, and undoing
     * on that would rewind work autonomy did since.
     */
    private boolean settledByDiscarding;

    /** The session the disk undo note was written through, so dispose can give it back. */
    private org.traincontrol.automationui.AutonomySession autonomySessionForTheNote;

    /**
     * Follows a locomotive's rename into the snapshot Cancel would put back, or a restored name missing from the
     * database would invalidate the whole layout.
     * @param from the old name
     * @param to the new name, or null when it was deleted
     */
    public void autonomyLocomotiveRenamed(String from, String to)
    {
        org.traincontrol.automationui.AutonomyCompanionStore
            .repairLocomotiveInSetup(this.autonomyAsOpened, from, to);

        // And every undo snapshot, as restoring one writes and saves the page's point properties.
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
     * Takes the undo point, in memory (for Cancel) and on disk (OB-108, for a restart after the process dies with an
     * editor open), always together: a page or mode switch moves both.
     * @param live the session to snapshot, or null when there is none
     */
    private void takeTheUndoPoint(org.traincontrol.automationui.AutonomySession live)
    {
        this.autonomyAsOpened = live == null ? null : live.snapshotSetup();

        this.autonomySessionForTheNote = live;

        if (live != null) live.beginEditSession();
    }

    /**
     * Gives the disk note back in dispose, the one thing every way of closing passes through; a left note would revert
     * saved work at the next session build.  A page or mode switch keeps the frame, and `arriveAt` re-takes the note.
     */
    @Override
    public void dispose()
    {
        java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(this.pointerWatch);

        if (this.autonomySessionForTheNote != null)
        {
            // Said when it fails (a sync client can win the delete); the note left is harmless.
            if (!this.autonomySessionForTheNote.endEditSession() && parent.getModel() != null)
            {
                parent.getModel().logf("autosetup.log.editSessionNotCleared");
            }

            this.autonomySessionForTheNote = null;
        }

        super.dispose();

        // And Edit asked again
        if (parent != null) parent.editorWindowClosed();
    }

    /**
     * @param ui a main window
     * @return whether this is that window's editor
     */
    boolean isEditorOf(TrainControlUI ui)
    {
        return this.parent == ui;
    }

    /**
     * Whether the setup changed since this window opened, which closing must ask (MT-406): the per-gesture rebuild
     * saves and clears the session's flag, so the opening snapshot is compared too (Adam: *"a one-way run (or any other
     * edits to arrows) persist after I press cancel.  They are not undone by cancelling."*).
     * @return true when there is something Cancel would throw away
     */
    private boolean hasUnsavedAutonomyWork()
    {
        if (autonomyPanel.isDirty()) return true;

        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        return this.autonomyAsOpened != null && autonomy != null
            && !this.autonomyAsOpened.similar(autonomy.snapshotSetup());
    }

    /**
     * Throws away this window's autonomy edits, putting the opening setup back in memory and on disk (MT-406), as the
     * per-gesture save already wrote them.  Lifted trains come back; moved ones stay moved.
     * @return null when it worked, or the reason it did not
     */
    private String discardAutonomyWork()
    {
        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        String failed;

        if (this.autonomyAsOpened == null || autonomy == null) failed = autonomyPanel.discardEdits();
        else if (!autonomy.restoreSetup(this.autonomyAsOpened)) failed = I18n.t("autosetup.log.restoreFailedAfterCancel");
        else failed = null;

        // The railway follows at once, before the window goes, or a fold in between would write the discarded edit
        // back.  Declines while autonomy runs.
        if (failed == null && autonomy != null) parent.rebuildRunningLayoutFromSetup();

        return failed;
    }

    /** Puts the setup back as the window opened it, with the page re-read beside it, as both halves undo together. */
    private void undoAutonomyEdits()
    {
        if (this.autonomyAsOpened == null) return;

        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        if (autonomy != null && !autonomy.restoreSetup(this.autonomyAsOpened)
            && parent.getModel() != null)
        {
            parent.getModel().logf("autosetup.log.restoreFailedAfterCancel");
        }

        this.autonomyAsOpened = null;
    }

    void rememberAutonomy(org.traincontrol.automationui.AutonomySession autonomy)
    {
        if (autonomy == null) return;

        if (!autonomy.saveQuietly() && parent.getModel() != null)
        {
            parent.getModel().logf("autosetup.log.saveFailedAfterEdit");
        }
    }

    /**
     * Added to the window's base floor of 630 so Cancel is never cut off at any diagram size (OB-342): 80 leaves it 5
     * pixels clear at 125%.  `sizeForDiagram` also never goes below what the layout asks for, which differs at 150%.
     */
    private static final int EXTRA_MINIMUM_HEIGHT = 80;

    public static final int MAX_UNDO_HISTORY = 100;
    
    // Repaint state
    private final ReentrantLock lock = new ReentrantLock();
    private boolean isRunning = false;
    private boolean needsRerun = false;

    /** Whether the pending redraw has begun reading; a request before that is part of it. */
    private boolean redrawStarted = false;
    
    // Reference to the right click menu
    LayoutEditorRightclickMenu popup;

    /**
     * `FlowLayout`'s gap around the diagram, included in the panel's size, or a CENTER layout puts the diagram at a
     * negative x no scroll reaches.
     */
    private static final int DIAGRAM_GAP = 5;

    /** Slack beyond the diagram's width, so it can be scrolled past its edges. */
    private static final int DIAGRAM_SLACK_COLUMNS = 1;

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

        // The designer's panel, kept before the sidebar wraps it: three places swap controls in its GroupLayout.
        this.formPane = getContentPane();

        // Centred, with gaps this class sizes the panel by (OB-172), so the left edge can be scrolled to.
        this.ExtLayoutPanel.setLayout(new FlowLayout(FlowLayout.CENTER, DIAGRAM_GAP, DIAGRAM_GAP));
        this.parent = ui;
        this.size = size;
        this.layout = l;

        // A failed build disposes its frame, or an invisible editor would grey Edit for the session.
        try
        {
            // The setup as it stands, before anything here touches it: gestures write it as they go, so Cancel needs
            // this snapshot.
            org.traincontrol.automationui.AutonomySession opened = ui == null
                ? null : ui.getAutonomySession();

            takeTheUndoPoint(opened);
        
            // Mirror address preference
            this.showAddressCheckbox.setSelected(l.getShowAddress());

            // The box says what the page shows: it may arrive with text hidden (FR-133, OB-364)
            this.showTextCheckbox.setSelected(!l.getEditHideText());

            mountGridToggle();
        
            this.setFocusable(true);
            this.requestFocusInWindow();
        
            // The form's palette heading, read before autonomy mode renames it.
            this.paletteHeading = this.jLabel1.getText();

            buildPalette();

            showDiagramSize();

        // The pointer, watched wherever it arrives; removed in dispose
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(this.pointerWatch, java.awt.AWTEvent.MOUSE_EVENT_MASK);
        }
        catch (RuntimeException | Error failed)
        {
            dispose();

            throw failed;
        }
    }

    /**
     * Control+G: shows or hides the track lengths (OB-019), in autonomy mode only, silently otherwise; through doClick
     * so the preference is written.
     */
    private void toggleTrackLengths()
    {
        if (autonomyPanel == null) return;

        autonomyPanel.getShowLengths().doClick();
    }

    /**
     * The palette of track pieces (OB-017), rebuilt when leaving autonomy mode, which emptied the panel and changed its
     * layout.
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

    /** The palette heading in a track diagram editor. */
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

    int getX(LayoutLabel label)
    {
        return grid.getCoordinates(label)[0];
    }
    
    int getY(LayoutLabel label)
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
        // A key listener wired here later would paste tiles in autonomy mode unnoticed.
        if (isAutonomyMode()) return;
        
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V)
        {
            if (this.hasToolFlag())
            {
                this.executeTool(label);
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
     * The station on a square, from the session, for the autonomy editor's tooltip; null for no tooltip.
     * @param label the square under the pointer
     * @return the station's name, or null
     */
    private String stationNameOn(LayoutLabel label)
    {
        if (label == null || this.parent == null || this.layout == null) return null;

        int x = getX(label);
        int y = getY(label);

        // The palette.
        if (x < 0 || y < 0) return null;

        return this.parent.autonomyStationNameAt(
            new org.traincontrol.automationui.TileGraph.TileKey(this.layout.getName(), x, y));
    }

    /**
     * What the square under the pointer says in autonomy mode: its station, and a hand with one short line where a
     * click changes directions, by the panel's own rule (FR-112; Adam: *"show a pointer mouse icon over tiles that can
     * have their arrows changed (i.e., most of them), and a brief hover tooltip "Click to change allowed directions".
     * Make sure that tooltip is very short."*).
     * @param label the square under the pointer
     */
    private void showWhatAClickDoes(LayoutLabel label)
    {
        String name = stationNameOn(label);

        int x = getX(label);
        int y = getY(label);

        boolean turns = autonomyPanel != null && x >= 0 && y >= 0 && autonomyPanel.clickChangesDirections(
            new org.traincontrol.automationui.TileGraph.TileKey(this.layout.getName(), x, y));

        label.setCursor(turns ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);

        // The line only until a square has been clicked (MT-697: *"Stop showing it after the user has clicked any
        // square"*); the hand stays
        String says = turns && !squareClicked ? I18n.t("autosetup.ui.tooltipClickToChangeDirections") : null;

        label.setToolTipText(name == null ? says : says == null ? name
            : "<html>" + escaped(name) + "<br>" + escaped(says) + "</html>");
    }

    /** Text as HTML shows it literally. */
    private static String escaped(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * The square under the pointer in autonomy mode, or null (OB-198), for Control+H, S and E.  A stale label from a
     * page left behind is forgotten, not merely refused.
     * @return the square, or null when the pointer is over none this window can answer about
     */
    public org.traincontrol.automationui.TileGraph.TileKey hoveredSquare()
    {
        LayoutLabel over = autonomyHover;

        if (over == null || layout == null) return null;

        int x = getX(over);
        int y = getY(over);

        // A label left by a page change, or the palette's.
        if (x < 0 || y < 0)
        {
            autonomyHover = null;

            return null;
        }

        return new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), x, y);
    }

    public void receiveMoveEvent(MouseEvent e, LayoutLabel label)
    {
        // In autonomy mode there is no PLACEMENT preview (nothing is being placed), but the blue outline still says
        // where the pointer is (OB-091; Adam: "also, add the blue outline hover effect to the autonomy editor."): it
        // answers "which square am I about to right-click", and this editor's menus act on exactly that square.
        // `lastHoveredX/Y` are deliberately NOT set: they are where a paste would land.  Nothing moves: highlightLabel
        // sizes the outline to the room the resting border takes, a line when the grid is on and an overlay when it is
        // off.
        if (isAutonomyMode())
        {
            if (label == null || (this.popup != null && this.popup.isVisible())) return;

            // WHICH SQUARE, kept where a shortcut can find it (MT-258 item 4): in its own field, not `lastHoveredX/Y`,
            // because Control+S has to name the square under the pointer and the placement variables hold -1,-1 and a
            // null label here.
            autonomyHover = label;

            // And the square's NAME, the one thing this window is about that the diagram does not already show, with
            // the hand and the line a square a click turns carries (FR-112).  Set outside the invokeLater: a tooltip is
            // read when the pointer settles, not when the outline is painted.
            showWhatAClickDoes(label);

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

        this.pointerOnGrid = lastHoveredX != -1;
     
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
                    label.setCursor(new Cursor(Cursor.MOVE_CURSOR));
                }
                else if (this.hasToolFlag() && !this.selectMode)
                {
                    label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                }
                else
                {
                    // Inherited, not copied: a crosshair while a drag picks (setSelectMode), else the arrow.
                    label.setCursor(null);
                }

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    this.clearBordersFromChildren(this.grid.getContainer());
                    this.highlightLabel(label, COMPONENT_BORDER_HOVERED_COLOR);

                    // The hover cleared every border, the selection, grip, box and landing too: drawn back.
                    if (!this.selection.isEmpty() || this.boxAnchorX >= 0 || !this.landingSelection.isEmpty())
                    {
                        this.refreshSelectionBorders();
                    }
                });
            }
        }
        
        // lastHoveredLabel = label;
    }
    
    /** The square a drag started on, so a click can be told from a drag on release. */
    private LayoutLabel dragSource = null;

    /**
     * The square of a box Escape dropped with the button down: its release's click is ignored, once.  Forgotten by the
     * next press, and by any release that makes no click.
     */
    private LayoutLabel clickToIgnore = null;

    /** Whether the pointer moved with the button down since the last left press; AWT then makes no click. */
    private boolean pressMoved = false;

    /**
     * Whether the pointer is over this grid, which the selection's redraw asks before drawing the hover outline back.
     */
    private boolean pointerOnGrid = false;

    /**
     * The grip that drags the whole selection: the top right corner of its bounding box, where a left-to-right drag
     * leaves the pointer; with picking on, every other drag draws a new box.
     * @return the square, or null when nothing is picked
     */
    private int[] selectionHandle()
    {
        return this.selection.handle();
    }

    private boolean isSelectionHandle(LayoutLabel label)
    {
        if (label == null) return false;

        int[] handle = selectionHandle();

        return handle != null && getX(label) == handle[0] && getY(label) == handle[1];
    }

    public void beginDrag(MouseEvent e, LayoutLabel label)
    {
        // A new press: any click to ignore was the last press's
        this.clickToIgnore = null;

        this.pressMoved = false;

        // Dragging moves track; in autonomy mode that would be a silent accident.
        if (isAutonomyMode()) return;

        // The grip first, or picking mode would swallow it.
        if (label != null && isSelectionHandle(label))
        {
            beginGroupDrag(label);

            return;
        }

        // Shift held: a box, picked on release.
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

        // From a picked square the whole selection moves; from an unpicked one only that tile, keeping the selection.
        if (label != null && !this.selection.isEmpty()
            && this.selection.contains(getX(label), getY(label)))
        {
            beginGroupDrag(label);

            return;
        }

        this.groupDragging = false;

        if (label != null && label.getComponent() != null)
        {
            // Placing is not dragging (OB-169; Adam: "clicking a tile in 'new components' followed by a square on the
            // diagram no longer places that tile."): with a palette tile held a press starts nothing, and the click
            // places, the tool staying armed.
            if (placingFromPalette() && (getX(label) != -1 || getY(label) != -1)) return;

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

    /** Picks the whole selection up, from a square inside it or from its grip. */
    private void beginGroupDrag(LayoutLabel label)
    {
        this.dragSource = label;
        this.groupDragging = true;

        // The tiles themselves, not a count: the eye checks the shape against the gap.
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
     * The picked squares as one picture laid out as on the diagram, scaled down for a large group.
     * @return the picture, or null where there is nothing to draw
     */
    private javax.swing.Icon selectionPreview()
    {
        int[] bounds = this.selection.bounds();

        if (bounds == null || grid == null) return null;

        int across = bounds[2] - bounds[0] + 1;
        int down = bounds[3] - bounds[1] + 1;

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

                // Through an image: an ImageIcon paints at its own size and would spill over
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

    /** The longest side a dragged group is drawn at, in pixels, so it does not hide the diagram. */
    private static final int MAX_DRAG_PREVIEW = 260;

    public void updateDrag(MouseEvent e, LayoutLabel label)
    {
        // A move with the button down: the release makes no click
        this.pressMoved = true;

        if (isAutonomyMode()) return;

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

        // The click to ignore goes if this release makes none, or the square's next click would be swallowed.
        if (this.clickToIgnore != null && (this.pressMoved || this.getLastHoveredLabel() != this.clickToIgnore))
        {
            this.clickToIgnore = null;
        }

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

                if (target != null && source != null
                    && !moveSelection(getX(target) - getX(source), getY(target) - getY(source)))
                {
                    // A refused move repaints too (OB-161), or the landing outlines stay behind its dialog.
                    this.refreshSelectionBorders();
                }

                return;
            }

            // Nothing under the pointer (the palette is -1,-1): executing would throw on the event thread.
            if (target == null) return;

            // A press and release on one square is a click: executing would push an undo entry and mark the page
            // unsaved for nothing.
            if (source != null && target == source)
            {
                resetClipboard();
                return;
            }

            // Snap to grid logic
            executeTool(target);
        }
    }
    
    /**
     * The autonomy tools, mounted in place of the palette in newComponents, whose layout is already hand-set; not
     * ExtLayoutPanel, which LayoutGrid re-lays out on every redraw.
     */
    private AutonomyEditorPanel autonomyPanel;

    // The diagram with the findings beneath, once fitted; kept so a second call does not stack a copy.
    private javax.swing.JPanel autonomyFindings;

    private AutonomyBanner autonomyBanner;

    // The column holding Addresses and the lengths toggle in autonomy mode
    private javax.swing.JPanel autonomyVisibility;

    /**
     * Turns autonomy setup on or off, without layout.setEdit(), so labels and text stay as when trains run.
     * @param session the setup to edit, or null to go back to editing the diagram
     */
    public void setAutonomyMode(org.traincontrol.automationui.AutonomySession session)
    {
        // Resizing the page is editing the diagram, so its buttons go with the palette; Toggle Visibility stays.
        if (this.diagramSize != null) this.diagramSize.setVisible(session == null);
        if (this.plusButton != null) this.plusButton.setVisible(session == null);
        if (this.minusButton != null) this.minusButton.setVisible(session == null);

        // And the Text Labels box, which the autonomy editor has its own copy of (FR-133): hidden, as it is the form's.
        if (this.showTextCheckbox != null) this.showTextCheckbox.setVisible(session == null);

        if (this.gapUnderTextLabels != null) this.gapUnderTextLabels.setVisible(session == null);

        if (session == null)
        {
            if (autonomyPanel != null)
            {
                this.newComponents.remove(autonomyPanel);
                autonomyPanel = null;

                buildPalette();
            }

            autonomyBanner = null;

            // The Addresses box back where the form had it
            if (autonomyVisibility != null && formPane.getLayout() instanceof javax.swing.GroupLayout)
            {
                ((javax.swing.GroupLayout) formPane.getLayout())
                    .replace(autonomyVisibility, this.showAddressCheckbox);

                autonomyVisibility = null;
            }

            // And the findings come out from under the diagram, and the field is cleared, or coming back would skip the
            // mount and show the previous session's frozen list.
            if (autonomyFindings != null)
            {
                if (this.jScrollPane1.getParent() != null) this.jScrollPane1.getParent().remove(this.jScrollPane1);

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
            // The panel needs the running layout for Why Not Moving?, a question the setup cannot answer.
            autonomyPanel = new AutonomyEditorPanel(session, layout.getName(), new Runnable()
            {
                @Override
                public void run()
                {
                    refreshAutonomyAnnotations();

                    // The main window draws the same setup and must be told of each edit.
                    parent.refreshStaticAutonomyLayer();
                }
            });

            // The main window cannot be found from a JFrame, which has no owner.
            autonomyPanel.setMainWindow(parent);

            // Where the railway is, so the arrived-from menu and `setFacingAndMove` work here as on the track diagram.
            autonomyPanel.setRunningLayoutSource(
                () -> parent == null || parent.getModel() == null
                    ? null : parent.getModel().getAutoLayoutIfLoaded());

            if (session != null)
            {
                session.setRunningLayoutSource(
                    () -> parent == null || parent.getModel() == null
                        ? null : parent.getModel().getAutoLayoutIfLoaded());
            }

            // Asked on every use: a load replaces the Layout wholesale.
            autonomyPanel.setLayoutSource(() -> parent.getModel().getAutoLayoutIfLoaded());

            // A caption is tile art, so the grid is rebuilt, the annotations laid back on top.
            autonomyPanel.setOnDiagramChanged(new Runnable()
            {
                @Override
                public void run()
                {
                    refreshGrid();

                    javax.swing.SwingUtilities.invokeLater(() -> refreshAutonomyAnnotations());
                }
            });

            // And the light door (OB-185): only the arrows re-set, no flashing rebuild.
            autonomyPanel.setOnAnnotationsChanged(new Runnable()
            {
                @Override
                public void run()
                {
                    refreshAutonomyAnnotations();
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

            // A finding on another page: close and reopen on that page, as the editor is built around one diagram.
            // Nothing is lost, as every edit is already in the shared session.
            autonomyPanel.setOnJumpToPage(new java.util.function.Consumer<
                org.traincontrol.automationui.TileGraph.TileKey>()
            {
                @Override
                public void accept(org.traincontrol.automationui.TileGraph.TileKey tile)
                {
                    layout.setEdit(false);

                    // Carried before the window goes: the user closed nothing
                    final boolean clicked = squareClicked;

                    parent.autonomyEditorClosedFromTheEditor(
                        () -> parent.openCarryingTheClick(clicked, () -> parent.openAutonomyEditor(tile)));

                    dispose();
                }
            });

            // And the finding's other squares, outlined there as here
            autonomyPanel.setOnJumpToNotice((at, named) ->
            {
                layout.setEdit(false);

                // Carried before the window goes: the user closed nothing
                final boolean clicked = squareClicked;

                parent.autonomyEditorClosedFromTheEditor(
                    () -> parent.openCarryingTheClick(clicked, () -> parent.openAutonomyEditor(at, named)));

                dispose();
            });

            // A link's other end: the same close-and-reopen, but it asks first, as the user chose to leave.
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

            autonomyBanner = new AutonomyBanner();

            autonomyPanel.setBanner(autonomyBanner);

            // The lengths toggle under Addresses: GroupLayout can replace a component, so Addresses is swapped for a
            // column holding both.
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

                // The form's gap, or the two would read as one control.
                visibility.add(javax.swing.Box.createVerticalStrut(CHECKBOX_GAP));

                visibility.add(autonomyPanel.getShowLengths());

                // Directly under Track Lengths: one subject.
                autonomyPanel.getShowUnmeasured().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                visibility.add(autonomyPanel.getShowUnmeasured());

                // And the trains (FR-119), a subject of their own
                visibility.add(javax.swing.Box.createVerticalStrut(CHECKBOX_GAP));

                autonomyPanel.getShowTrains().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                visibility.add(autonomyPanel.getShowTrains());

                // And the diagram's text (FR-133, OB-312)
                visibility.add(javax.swing.Box.createVerticalStrut(CHECKBOX_GAP));

                autonomyPanel.getShowTextLabels().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                visibility.add(autonomyPanel.getShowTextLabels());

                // What the captions say, as one choice (FR-061; Adam: "add a Text Labels label and dropdown right above
                // Track Directions, with the following options: Station Names, Parked Locomotives, Home Locomotives,
                // and None."), in jLabel1's heading style.
                javax.swing.JLabel captionsLabel =
                    new javax.swing.JLabel(I18n.t("autosetup.ui.captionsHeading"));
                captionsLabel.setFont(this.jLabel1.getFont());
                captionsLabel.setForeground(this.jLabel1.getForeground());
                captionsLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                captionsLabel.setBorder(
                    javax.swing.BorderFactory.createEmptyBorder(HEADING_GAP + 6, 0, HEADING_GAP, 0));

                visibility.add(captionsLabel);

                javax.swing.JComboBox<String> captionChoice = autonomyPanel.getCaptionChoice();
                captionChoice.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

                // Preferred too: the column takes its child's preferred width.  "Locs" fits 150 (Adam: "use Locs if
                // shorter text is needed in the dropdown").
                captionChoice.setPreferredSize(new java.awt.Dimension(150, 24));
                captionChoice.setMaximumSize(new java.awt.Dimension(150, 24));

                visibility.add(captionChoice);
                javax.swing.JLabel directionsLabel =
                    new javax.swing.JLabel(I18n.t("autosetup.ui.labelDirections"));
                directionsLabel.setFont(this.jLabel1.getFont());
                directionsLabel.setForeground(this.jLabel1.getForeground());
                directionsLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                // The form's air under a heading (HEADING_GAP)
                directionsLabel.setBorder(
                    javax.swing.BorderFactory.createEmptyBorder(HEADING_GAP + 6, 0, HEADING_GAP, 0));

                visibility.add(directionsLabel);

                javax.swing.JComboBox<String> choice = autonomyPanel.getShowDirections();
                choice.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

                // The Station Labels box's width in every language (OB-368): a long phrase clips, the popup shows it.
                choice.setPreferredSize(captionChoice.getPreferredSize());
                choice.setMaximumSize(captionChoice.getMaximumSize());

                visibility.add(choice);

                autonomyVisibility = visibility;
            }

            // Lengths and addresses exclude each other (two numbers on a tile are unreadable); applied once now, as
            // both can arrive on.
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

            // The findings across the bottom, full width, as sentences: GroupLayout.replace swaps the grid's scroll
            // pane for a panel of grid and list.
            if (autonomyFindings == null)
            {
                javax.swing.JPanel stack = new javax.swing.JPanel(new BorderLayout());
                stack.setOpaque(false);

                // Guarded as the two above: all reach for the generated form's layout.
                if (formPane.getLayout() instanceof javax.swing.GroupLayout)
                {
                    ((javax.swing.GroupLayout) formPane.getLayout())
                        .replace(this.jScrollPane1, stack);
                }

                // The notices float over the diagram, which keeps its size (OB-366)
                stack.add(autonomyBanner.floatOver(this.jScrollPane1), BorderLayout.CENTER);

                javax.swing.JPanel foot = new javax.swing.JPanel(new BorderLayout());
                foot.setOpaque(false);

                javax.swing.JScrollPane list = autonomyPanel.getFindingsPanel();

                if (list != null)
                {
                    // Taller here, across the window at its own size.
                    list.setPreferredSize(new java.awt.Dimension(100, 190));

                    foot.add(list, BorderLayout.CENTER);
                }

                // The count under the list: it is a total.
                foot.add(autonomyPanel.getBanner(), BorderLayout.SOUTH);

                stack.add(foot, BorderLayout.SOUTH);

                autonomyFindings = stack;
            }

            // The column heading is the window's, and "New Components" would be wrong here.
            this.jLabel1.setText(I18n.t("autosetup.ui.titleCap"));

            // Said in the title; posted, as render()'s own invokeLater would overwrite it.
            javax.swing.SwingUtilities.invokeLater(() -> setTitle(titleFor(true)));

            // Drawn again: render() built the grid before this mode was known.
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                // The remembered caption mode, now the panel has a window (MT-292), before the redraw.
                autonomyPanel.applyRememberedCaptionMode();

                refreshGrid();

                javax.swing.SwingUtilities.invokeLater(() -> refreshAutonomyAnnotations());
            });
        }

        this.newComponents.revalidate();
        this.newComponents.repaint();

        refreshAutonomyAnnotations();

        // And the pointer says whether a drag picks
        this.showWhetherADragPicks();
    }

    /**
     * Closes an autonomy-mode editor and puts the shared diagram back: render() set edit mode on the LayoutDiagram the
     * main window paints too, and the window's always-on-top state.
     */
    private void closeAutonomyMode()
    {
        layout.setEdit(false);

        // The main window puts back what opening this changed (always-on-top, Edit), which autonomy mode's path skips.
        parent.autonomyEditorClosedFromTheEditor(null);

        dispose();
    }

    /** Whether this editor is setting autonomy up: the one question every mouse path asks. */
    public boolean isAutonomyMode()
    {
        return autonomyPanel != null && autonomyPanel.isVisible();
    }

    /** Scrolls a square into view and flashes it, for a finding or a prompt. */
    public void reveal(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        if (grid == null || tile == null || !layout.getName().equals(tile.getPage())) return;

        LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

        if (label == null) return;

        label.scrollRectToVisible(new java.awt.Rectangle(0, 0, label.getWidth(), label.getHeight()));

        // One flash at a time (MT-454), or a walk leaves a trail of yellow squares.
        if (lastRevealed != null && lastRevealed != label) lastRevealed.endFlash();

        lastRevealed = label;

        // The diagram's flash, not a border, which would take the grid line with it.
        label.flashHighlight();
    }

    /**
     * Whether a square has been clicked in this visit, after which the click tooltip's line is not said (MT-697: *"Stop
     * showing it after the user has clicked any square"*, *"have that tooltip tracking state reset after the editor is
     * closed"*): the window's, so the next opening starts afresh; reopenings hand it on (`carryTheClick`).
     */
    private boolean squareClicked = false;

    /** The next editor opened by one of this window's reopenings starts knowing a square was clicked. */
    void carryTheClick()
    {
        squareClicked = true;
    }

    private LayoutLabel lastRevealed;

    /**
     * Redraws the autonomy annotations over every tile, the editor asking the panel per label; grid indexes are diagram
     * coordinates in edit mode (LayoutDiagram.checkBounds).
     */
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

        // And the square under the pointer asked again (FR-112): an armed tool changes what a click does.
        if (active && hoveredSquare() != null) showWhatAClickDoes(autonomyHover);
    }

    /** @return the autonomy tools, or null when the editor is editing the diagram */
    public AutonomyEditorPanel getAutonomyPanel()
    {
        return autonomyPanel;
    }

    /**
     * The square of the train drawn under a click, for Test a Path and Why Not Moving? (OB-354): a drawn train covers
     * captions and reaches past its tile on a curve.
     * @param e the click, in its own component's coordinates
     * @return the square the train stands on, or null where none is drawn there or the tool does not ask
     */
    LayoutLabel trainClicked(MouseEvent e)
    {
        if (e == null || e.getComponent() == null || grid == null || autonomyPanel == null
            || !autonomyPanel.takesATrainForItsStation())
        {
            return null;
        }

        if (e.getComponent() instanceof LayoutLabel && ((LayoutLabel) e.getComponent()).trainDrawnAt(e.getPoint()))
        {
            return (LayoutLabel) e.getComponent();
        }

        for (int x = 0; grid.getValueAt(x, 0) != null; x++)
        {
            for (int y = 0; grid.getValueAt(x, y) != null; y++)
            {
                LayoutLabel square = grid.getValueAt(x, y);

                if (square.getParent() != null && square.trainDrawnAt(
                    javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), square)))
                {
                    return square;
                }
            }
        }

        return null;
    }

    public void receiveClickEvent(MouseEvent e, LayoutLabel label)
    {
        // The click of a box Escape dropped, ignored once, if a left click; any other click finds it gone.
        LayoutLabel ignore = this.clickToIgnore;

        this.clickToIgnore = null;

        if (ignore != null && label == ignore && e != null && e.getButton() == MouseEvent.BUTTON1) return;

        // In autonomy mode a click configures the track; LayoutLabel already routes it here.
        if (isAutonomyMode())
        {
            // A train clicked is its station (OB-354; Adam: "if clicking on a locomotive in test a path / why not
            // moving, make it register a click on the station.")
            LayoutLabel train = javax.swing.SwingUtilities.isRightMouseButton(e) ? null : trainClicked(e);

            int x = getX(train != null ? train : label);
            int y = getY(train != null ? train : label);

            if (x >= 0 && y >= 0)
            {
                org.traincontrol.automationui.TileGraph.TileKey tile =
                    new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), x, y);

                // Right-click opens the menu and must not run the tool too.
                if (javax.swing.SwingUtilities.isRightMouseButton(e))
                {
                    autonomyPanel.tileRightClicked(tile, layout.getComponent(x, y),
                        label, e.getX(), e.getY());
                }
                else
                {
                    // Any square clicked (MT-697)
                    squareClicked = true;

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

        // Shift-click toggles a square's pick, before anything that would change the diagram.
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
                popup = menuFor(label, lc);

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
                executeTool(label);
                
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
     * @param label where the tool is used
     */
    synchronized public void executeTool(LayoutLabel label)
    {
        this.snapshotLayout();

        execCopy(label, toolFlag == tool.MOVE);
        
        // Tile is on the main diagram- update borders
        if (lastX != -1 || lastY != -1)
        {
            this.clearBordersFromChildren(this.newComponents);
        }
    }
    
    /**
     * Which way round a track tile put down should lie to join the track beside it (OB-292, OB-360), by the port table
     * the autonomy graph uses.  A switch by its base first (Adam: *"the base of a switch behaves like a straight track
     * would, so that at least something is aligned (for switches, there's always some guessing involved, which is
     * ok)"*), then by roads with every end on track (*"a curve connects if it can"*), then by ends (*"meet the single
     * neighbor"*); a tie keeps the way it is held, else the nearest turn.
     * @param layout the page
     * @param placing the tile about to be put down
     * @param x its column
     * @param y its row
     * @return the orientation to put it down in
     */
    static int orientationThatJoins(org.traincontrol.base.LayoutDiagram layout, LayoutDiagramComponent placing, int x,
        int y)
    {
        int held = placing.getOrientation();

        if (layout == null || !org.traincontrol.automationui.TilePorts.isRoutable(placing.getType())
            || org.traincontrol.automationui.TilePorts.isTransparent(placing.getType())) return held;

        int ways = LayoutDiagramComponent.getNumOrientations(placing.getType());
        int best = held;
        int bestScore = joinScore(layout, placing.getType(), held, x, y);

        // The held way first, then a quarter turn either way, then the half: the first of equals stays.
        for (int turn : new int[] { 1, ways - 1, 2 })
        {
            int way = (held + turn) % ways;

            if (way == held) continue;

            int score = joinScore(layout, placing.getType(), way, x, y);

            if (score > bestScore)
            {
                best = way;
                bestScore = score;
            }
        }

        return best;
    }

    /**
     * How much track this tile meets this way round: 1000 if its switch's base is on track, 10 per road with every end
     * on track, 1 per side with an end that is.
     */
    private static int joinScore(org.traincontrol.base.LayoutDiagram layout,
        LayoutDiagramComponent.componentType type, int orientation, int x, int y)
    {
        org.traincontrol.automationui.TilePorts.Side toe =
            org.traincontrol.automationui.TilePorts.deriveToe(type, orientation);

        int score = toe != null && meetsTrack(layout, x, y, toe) ? 1000 : 0;

        java.util.Set<org.traincontrol.automationui.TilePorts.Side> met =
            java.util.EnumSet.noneOf(org.traincontrol.automationui.TilePorts.Side.class);

        for (int state = 0; state < org.traincontrol.automationui.TilePorts.getStateCount(type); state++)
        {
            for (org.traincontrol.automationui.TilePorts.Route road
                : org.traincontrol.automationui.TilePorts.ports(type, orientation, state))
            {
                boolean a = meetsTrack(layout, x, y, road.getA());
                boolean b = meetsTrack(layout, x, y, road.getB());

                if (a && b) score += 10;

                if (a) met.add(road.getA());
                if (b) met.add(road.getB());
            }
        }

        return score + met.size();
    }

    /** Whether the square beside this one, on this side, has a road reaching the edge between them. */
    private static boolean meetsTrack(org.traincontrol.base.LayoutDiagram layout, int x, int y,
        org.traincontrol.automationui.TilePorts.Side side)
    {
        int nx = x + (side == org.traincontrol.automationui.TilePorts.Side.E ? 1
            : side == org.traincontrol.automationui.TilePorts.Side.W ? -1 : 0);
        int ny = y + (side == org.traincontrol.automationui.TilePorts.Side.S ? 1
            : side == org.traincontrol.automationui.TilePorts.Side.N ? -1 : 0);

        LayoutDiagramComponent beside = layout.getComponent(nx, ny);

        if (beside == null) return false;

        org.traincontrol.automationui.TilePorts.Side back = side.opposite();

        for (int state = 0; state < org.traincontrol.automationui.TilePorts.getStateCount(beside.getType()); state++)
        {
            for (org.traincontrol.automationui.TilePorts.Route road
                : org.traincontrol.automationui.TilePorts.ports(beside.getType(), beside.getOrientation(), state))
            {
                if (road.touches(back)) return true;
            }
        }

        return false;
    }

    /** Delegates to `LayoutEditorSelection.execCopy`. */
    synchronized private void execCopy(LayoutLabel destLabel, boolean move)
    {
        this.selectionOps.execCopy(destLabel, move);
    }
    
    public LayoutDiagram getMarklinLayout()
    {
        return layout;
    }
    
    /** Delegates to `LayoutEditorSelection.resetClipboard`. */
    synchronized void resetClipboard()
    {
        this.selectionOps.resetClipboard();
    }

    /** Delegates to `LayoutEditorSelection.placingFromPalette`. */
    private boolean placingFromPalette()
    {
        return this.selectionOps.placingFromPalette();
    }
    
    /**
     * Adds this label's location to the clipboard
     *
     * Delegates to `LayoutEditorSelection.initCopy`.
     *
     * @param label
     * @param component - the component at the label location, or one that's specified
     * @param move
     */
    synchronized public void initCopy(LayoutLabel label, LayoutDiagramComponent component, boolean move)
    {
        this.selectionOps.initCopy(label, component, move);
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
    void refreshSelectionBorders()
    {
        this.clearBordersFromChildren(this.grid.getContainer());

        // The square under the pointer keeps its outline, if the pointer is still on this grid; drawn before the picked
        // squares, so a picked one still shows picked.
        LayoutLabel hovered = this.pointerOnGrid ? this.getLastHoveredLabel() : null;

        if (hovered != null) this.highlightLabel(hovered, COMPONENT_BORDER_HOVERED_COLOR);

        for (org.traincontrol.base.TileSelection.At at : this.selection.all())
        {
            LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

            if (label != null) this.highlightLabel(label, COMPONENT_BORDER_SELECTED_COLOR);
        }

        // The box being dragged, after, so a square already picked and inside it looks picked.
        for (org.traincontrol.base.TileSelection.At at : this.previewSelection.all())
        {
            LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

            if (label != null) this.highlightLabel(label, COMPONENT_BORDER_SELECTED_COLOR);
        }

        // And where a dragged group would land, in blue, last: the live answer.
        for (org.traincontrol.base.TileSelection.At at : this.landingSelection.all())
        {
            LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

            if (label != null) this.highlightLabel(label, COMPONENT_BORDER_LANDING_COLOR);
        }

        // The grip last of all, visible even on a picked square.
        int[] handle = selectionHandle();

        LayoutLabel grip = handle == null || !this.landingSelection.isEmpty()
            ? null : this.grid.getValueAt(handle[0], handle[1]);

        // The old grip stops saying so, or its tooltip would offer the wrong drag.
        if (this.handleLabel != null && this.handleLabel != grip)
        {
            this.handleLabel.setToolTipText(null);
        }

        this.handleLabel = grip;

        if (grip != null)
        {
            // A border, painted after the opaque tile art and taking no space, where a background would be hidden.
            grip.setBorder(new SelectionGrip());

        // Set directly, so the record is dropped (OB-157).
        forgetBorderState(grip);

            grip.setToolTipText(I18n.t("layout.ui.tooltipSelectionHandle"));
        }
    }

    /** The square drawn as the grip, so it can be told when it stops being one. */
    private LayoutLabel handleLabel;

    /** The key for Select by Dragging a Box (FR-107): M for marquee; the main window's Control+M never reaches it. */
    public static final String SHORTCUT_SELECT_BY_DRAGGING = "Control+M";

    /**
     * Turns picking on for one box, then off, for the right-click menu: a drag on track must move it, so picking by
     * drag is a mode, and this spares remembering to turn it off.
     */
    public void selectOnce()
    {
        setSelectMode(true);

        this.selectOnce = true;
    }

    /** Whether the picking mode on is the one-box kind. */
    private boolean selectOnce = false;

    /** Turns picking-by-drag on or off. */
    public void setSelectMode(boolean on)
    {
        // Off, by any route, cancels the one-box promise
        if (!on) this.selectOnce = false;

        this.selectMode = on;

        this.showWhetherADragPicks();
    }

    /**
     * The pointer says whether a drag picks: a crosshair while it does (MT-661; Adam: "I'd rather not have text popping
     * up, why not just change the cursor?").  Squares inherit it; the grip keeps its own.  None picks in Autonomy
     * Setup.
     */
    private void showWhetherADragPicks()
    {
        if (this.grid != null && this.grid.getContainer() != null)
        {
            boolean picks = this.selectMode && !this.isAutonomyMode();

            this.grid.getContainer().setCursor(picks ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : null);
        }
    }

    /** @return whether a drag currently picks squares */
    public boolean isSelectMode()
    {
        return this.selectMode;
    }

    /**
     * Takes a copy of everything picked, as the rectangle it occupies.
     *
     * Delegates to `LayoutEditorSelection.copySelection`.
     * @return true if there was something to copy
     */
    synchronized public boolean copySelection()
    {
        return this.selectionOps.copySelection();
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
     * Delegates to `LayoutEditorSelection.pasteSelection`.
     *
     * @param atX the column to put the top left corner on
     * @param atY the row
     * @return true if anything was pasted
     */
    synchronized public boolean pasteSelection(int atX, int atY)
    {
        return this.selectionOps.pasteSelection(atX, atY);
    }

    /**
     * Moves every picked square by a delta, as ONE undoable step.
     *
     * Delegates to `LayoutEditorSelection.moveSelection`.
     *
     * @param dx columns to move by
     * @param dy rows to move by
     * @return true if the move happened
     */
    synchronized public boolean moveSelection(int dx, int dy)
    {
        return this.selectionOps.moveSelection(dx, dy);
    }

    /**
     * Picks out a whole row, or adds it to what is already picked.
     *
     * Delegates to `LayoutEditorSelection.selectRow`.
     *
     * @param y the row
     */
    public void selectRow(int y)
    {
        this.selectionOps.selectRow(y);
    }

    /**
     * Picks out a whole column.
     *
     * Delegates to `LayoutEditorSelection.selectColumn`.
     *
     * @param x the column
     */
    public void selectColumn(int x)
    {
        this.selectionOps.selectColumn(x);
    }

    /**
     * Picks out everything on the page.
     *
     * Delegates to `LayoutEditorSelection.selectAll`.
     */
    public void selectAll()
    {
        this.selectionOps.selectAll();
    }

    /**
     * Copies the picked squares and then clears them, as one step; delegates to `LayoutEditorSelection.cutSelection`.
     * @return true if anything was cut
     */
    synchronized public boolean cutSelection()
    {
        return this.selectionOps.cutSelection();
    }

    /**
     * Puts the armed tile on every picked square, as ONE undoable step.
     *
     * Delegates to `LayoutEditorSelection.fillSelection`.
     *
     * @return true if anything was filled
     */
    synchronized public boolean fillSelection()
    {
        return this.selectionOps.fillSelection();
    }

    /**
     * Deletes every picked square, as ONE undoable step.
     *
     * Delegates to `LayoutEditorSelection.deleteSelection`.
     *
     * @return true if anything was picked to delete
     */
    synchronized public boolean deleteSelection()
    {
        return this.selectionOps.deleteSelection();
    }

    /**
     * Empties every picked square; delegates to `LayoutEditorSelection.deleteSelection`.
     * @param tellAutonomy true for a plain delete, so the setup forgets the squares; false for a cut, whose paste
     *        carries the setup on (forgetCaptionsAt would destroy the captions)
     * @return true if anything was picked to delete
     */
    synchronized public boolean deleteSelection(boolean tellAutonomy)
    {
        return this.selectionOps.deleteSelection(tellAutonomy);
    }

    /**
     * Rotates every picked square where it stands, as one undoable step.
     *
     * Delegates to `LayoutEditorSelection.rotateSelection`.
     *
     * @return true if anything was picked to rotate
     */
    synchronized public boolean rotateSelection()
    {
        return this.selectionOps.rotateSelection();
    }

    /**
     * Deletes this label from the layout
     *
     * Delegates to `LayoutEditorSelection.delete`.
     *
     * @param label 
     */
    synchronized public void delete(LayoutLabel label)
    {
        this.selectionOps.delete(label);
    }

    /**
     * Delegates to `LayoutEditorSelection.delete`.
     * @param tellAutonomy false when the caller tells the setup: a cut's paste says where the block went, and
     *        forgetCaptionsAt would delete captions it cannot bring back
     */
    synchronized public void delete(LayoutLabel label, boolean tellAutonomy)
    {
        this.selectionOps.delete(label, tellAutonomy);
    }
        
    /**
     * Rotates the specified label
     *
     * Delegates to `LayoutEditorSelection.rotate`.
     *
     * @param label 
     */
    synchronized public void rotate(LayoutLabel label)
    {
        this.selectionOps.rotate(label);
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

                // Only when something was edited: a cancel must not disarm the tool.  The re-add above is a no-op then.
                if (newText != null) this.resetClipboard();
            }
            catch (IOException ex)
            {
                // Logged, not a dialog per failed tile.
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
        // Station captions belong to the autonomy setup now, keyed by square; said here, so whoever looks finds where.
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
                // Told the page, so a link is not offered it.
                LayoutEditorAddressPopup addressPopup = new LayoutEditorAddressPopup(lc, parent,
                    layout == null ? null : layout.getName());
                
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

                    // A link may be aimed at nothing, which setLogicalAddress cannot express; picking the first page
                    // would be the operator's choice made silently.
                    if (lc.isLink() && newAddress <= 0)
                    {
                        lc.setLinkedPageIndex(-1);
                    }
                    else
                    {
                        lc.setLogicalAddress(newAddress, addressPopup.getProtocol(), addressPopup.getGreenButton().isSelected());
                    }
                    
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
     * A tile's resting border (OB-028: "in the autonomy editor, the gray grid is not needed. show the track diagram as
     * it appears in the viewer, without the tile borders. make sure the borders return in the editor.").  None in
     * autonomy mode, so tiles sit flush; the palette keeps its border in both modes.
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
     * @param palette a tile in the toolbox rather than on the diagram, which keeps its thicker line
     * @param autonomy the autonomy editor, which draws its grid ON the squares rather than around them
     * @param grid whether the grey grid is being drawn
     * @return the border, or null when the grid is off: an empty border's room shows the panel through (MT-127)
     */
    public static Border restingBorder(boolean palette, boolean autonomy, boolean grid)
    {
        if (palette)
        {
            return BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR,
                NEW_COMPONENT_BORDER_WIDTH);
        }

        // Null when the grid is off (MT-127, testEditorSurfaceRules); the hover answers with overlayLine (FR-006).
        if (!grid) return null;

        // Both editors reserve the room with the grid on (OB-091; Adam: "enabling the grid widens the track diagram in
        // the layout editor (how it always was, because there is a double line in between cells), but not in the
        // autonomy editor. Make the behavior of the autonomy editor match so that there are no tile truncations.").
        return BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR, COMPONENT_BORDER_WIDTH);
    }

    /**
     * A line drawn ON a component, reserving no space, so the hover outline on a borderless square moves nothing
     * (FR-006); a border may paint outside what it reserves.
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
     * Puts the grid switch into Toggle Visibility (FR-006: "make the gray grid an option you can toggle in the visible
     * elements.  on by default, but persisted if turned off.") by replacing Text Labels with a column of both; not
     * Addresses, which autonomy mode replaces.
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

        gapUnderTextLabels = javax.swing.Box.createVerticalStrut(CHECKBOX_GAP);
        gapUnderTextLabels.setVisible(this.showTextCheckbox.isVisible());

        column.add(gapUnderTextLabels);
        column.add(showGridCheckbox);
    }

    private javax.swing.JCheckBox showGridCheckbox;

    /**
     * The gap under Text Labels, hidden with it in the autonomy editor (OB-289; Adam: *"slightly too much
     * spacing/padding below "visible elements" in autonomy editor.  make it be consistent with other labels"*).
     */
    private java.awt.Component gapUnderTextLabels;

    /**
     * Whether the editor draws its grey grid, on by default and remembered (FR-006); static, as the label asking has no
     * editor.
     * @return true when the grid should be drawn
     */
    public static boolean showGrid()
    {
        return TrainControlUI.getPrefs() == null
            || TrainControlUI.getPrefs().getBoolean(TrainControlUI.EDITOR_GRID_PREF, true);
    }

    /**
     * Remembers the choice and redraws every square to it.
     * @param show whether to draw the grid
     */
    public void setShowGrid(boolean show)
    {
        if (TrainControlUI.getPrefs() != null)
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.EDITOR_GRID_PREF, show);
        }

        // And the box says so, whoever asked (OB-179): Control+K is the other door.
        if (showGridCheckbox != null) showGridCheckbox.setSelected(show);

        // Every border is the old answer, so all are put back, the palette's too.
        clearBordersFromChildren(this.newComponents);

        if (this.grid != null) clearBordersFromChildren(this.grid.getContainer());

        // And the axis numbers, which follow the grid (OB-172): a full redraw, as the ruler is built in `drawGrid`.
        drawGrid();

        // And the outlines drawn back, as after every redraw
        drawTheOutlinesBack();
    }

    /**
     * After a redraw, the track editor's outlines drawn back (selection, grip, box, landing, the pointer's blue), or
     * they look gone until the pointer moves; the autonomy editor gets the border pass alone.
     */
    private void drawTheOutlinesBack()
    {
        if (this.grid == null) return;

        if (isAutonomyMode()) this.clearBordersFromChildren(this.grid.getContainer());
        else refreshSelectionBorders();
    }

    /**
     * The pointer gone off the squares: its blue comes off, the rest is drawn back.  Posted, after any outline the last
     * square's hover posted.
     */
    private void pointerOffTheSquares()
    {
        this.pointerOnGrid = false;

        javax.swing.SwingUtilities.invokeLater(this::drawTheOutlinesBack);
    }

    /**
     * The pointer left a square of the track editor: onto another, whose hover answers, or off the squares altogether,
     * which the margin does not always see.
     * @param e the exit, in the square's coordinates
     * @param label the square left
     */
    public void receiveExitEvent(MouseEvent e, LayoutLabel label)
    {
        if (isAutonomyMode() || this.grid == null || label == null || getX(label) < 0) return;

        JPanel squares = this.grid.getContainer();

        // From the square, or an address label on it that passed its exit on
        java.awt.Component from = e.getComponent() != null ? e.getComponent() : label;

        java.awt.Point to = javax.swing.SwingUtilities.convertPoint(from, e.getPoint(), squares);

        // Onto a square (or something over one) and in view
        if (squares.contains(to) && squares.getVisibleRect().contains(to) && aSquareAt(squares, to)) return;

        pointerOffTheSquares();
    }

    /**
     * Whether a square of the grid, not its padding, lies under a point of its panel, whatever is drawn over it.
     * @param squares the grid's panel
     * @param at a point of it
     * @return whether a square is there
     */
    private static boolean aSquareAt(JPanel squares, java.awt.Point at)
    {
        java.awt.Component top = squares.getComponentAt(at);

        if (top instanceof LayoutLabel) return !((LayoutLabel) top).isSpacer();

        for (java.awt.Component c : squares.getComponents())
        {
            if (c instanceof LayoutLabel && !((LayoutLabel) c).isSpacer() && c.getBounds().contains(at)) return true;
        }

        return false;
    }

    /**
     * Hears the pointer arrive anywhere in this window, so it is found off the squares even where a menu or dialog took
     * the square's exit.  Holds the window weakly, as the toolkit keeps it for life; removed in `dispose` too.
     */
    private static final class PointerWatch implements java.awt.event.AWTEventListener
    {
        private final java.lang.ref.WeakReference<LayoutEditor> editor;

        PointerWatch(LayoutEditor editor)
        {
            this.editor = new java.lang.ref.WeakReference<>(editor);
        }

        @Override
        public void eventDispatched(java.awt.AWTEvent event)
        {
            if (event.getID() != MouseEvent.MOUSE_ENTERED) return;

            LayoutEditor watching = this.editor.get();

            if (watching == null)
            {
                java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(this);

                return;
            }

            watching.pointerArrived(((MouseEvent) event).getComponent());
        }
    }

    /**
     * The right-click menu for a square, asking where the pointer is once it goes: it took the square's exit, so
     * nothing else would ask.
     * @param label the square
     * @param lc what is drawn on it, or null
     * @return the menu, not yet shown
     */
    LayoutEditorRightclickMenu menuFor(LayoutLabel label, LayoutDiagramComponent lc)
    {
        LayoutEditorRightclickMenu menu = new LayoutEditorRightclickMenu(this, parent, label, lc);

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
        {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
            {
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e)
            {
                javax.swing.SwingUtilities.invokeLater(LayoutEditor.this::askWhereThePointerIs);
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e)
            {
                javax.swing.SwingUtilities.invokeLater(LayoutEditor.this::askWhereThePointerIs);
            }
        });

        return menu;
    }

    /** Where the pointer is, once what covered the squares has gone: over one in view, the outline stays. */
    private void askWhereThePointerIs()
    {
        if (!this.pointerOnGrid || isAutonomyMode() || this.grid == null) return;

        if (this.popup != null && this.popup.isVisible()) return;

        JPanel squares = this.grid.getContainer();

        java.awt.Point at = this.pointerOver.apply(squares);

        if (at != null && squares.getVisibleRect().contains(at) && aSquareAt(squares, at)) return;

        pointerOffTheSquares();
    }

    /** Where the pointer is over the squares' panel, or null: a field, so a claim can say where without moving it. */
    private java.util.function.Function<JPanel, java.awt.Point> pointerOver = squares -> squares.getMousePosition(true);

    private final PointerWatch pointerWatch = new PointerWatch(this);

    /**
     * The pointer arrived on something in this window: off the squares, unless on a square, something over one, or a
     * menu or tooltip, which are about that square.
     * @param on what the pointer arrived on
     */
    void pointerArrived(java.awt.Component on)
    {
        if (!this.pointerOnGrid || isAutonomyMode() || this.grid == null || on == null) return;

        // This window or anything in it; the window's own entry is the pointer over a quiet part.
        if (on != this && javax.swing.SwingUtilities.getWindowAncestor(on) != this) return;

        if (this.popup != null && this.popup.isVisible()) return;

        if (on instanceof javax.swing.JPopupMenu || on instanceof javax.swing.JToolTip
            || javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JPopupMenu.class, on) != null
            || javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JToolTip.class, on) != null) return;

        if (on.getParent() == this.grid.getContainer() && !(on instanceof LayoutLabel && ((LayoutLabel) on).isSpacer()))
        {
            return;
        }

        pointerOffTheSquares();
    }

    /**
     * Picks a station label up for the drag (FR-035: "snapshot the label so users can see it is being moved (make it
     * follow the cursor while held down)."), on the layered pane's drag layer, hanging at the grab offset.
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
     * @param at where the pointer is, in this window's coordinates
     */
    public void moveCaptionGhost(java.awt.Point at)
    {
        if (this.captionGhost == null || at == null) return;

        java.awt.Rectangle was = this.captionGhost.getBounds();

        this.captionGhost.setLocation(at.x - this.captionGrab.x, at.y - this.captionGrab.y);

        // Both rectangles: where it was and where it is.
        getLayeredPane().repaint(was);
        getLayeredPane().repaint(this.captionGhost.getBounds());
    }

    /** Puts the picked-up label down (FR-035); safe with nothing picked up, as every release calls it. */
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
     * Marks where a dragged station label would land (FR-035): green where it will take, red where it will refuse.
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

    /** Takes the drag mark off whichever square wears it (FR-035). */
    public void clearCaptionDropTarget()
    {
        if (this.captionDropTarget == null) return;

        LayoutLabel was = this.captionDropTarget;

        // Cleared before the border is put back, so a repaint between finds no half-restored target.
        this.captionDropTarget = null;

        was.setBorder(restingBorder(false, isAutonomyMode()));

        // Set directly, so the record is dropped (OB-157).
        forgetBorderState(was);
    }

    /** The square wearing the drag mark, so it can be given its own border back. */
    private LayoutLabel captionDropTarget;

    /**
     * The border each tile wears, as a key (OB-157), since borders do not compare equal.  Weak, so a rebuilt grid takes
     * its records with it.
     */
    private final java.util.Map<JLabel, String> borderState = new java.util.WeakHashMap<>();

    /**
     * Sets a tile's border unless it already wears that one (OB-157): setBorder always repaints, and this runs per
     * mouse move.  The key must name everything the border depends on (palette, autonomy mode, grid), or a stale
     * outline stays.
     * @param label the tile
     * @param border what it should wear
     * @param key what that border IS, for comparing against next time
     */
    private void applyBorder(JLabel label, Border border, String key)
    {
        if (label == null) return;

        if (key.equals(this.borderState.get(label))) return;

        label.setBorder(border);

        this.borderState.put(label, key);
    }

    /**
     * Forgets a tile's border key, for the paths setting a border without applyBorder (OB-157), or the next refresh
     * skips a repaint.
     * @param label the tile whose border something else has just changed
     */
    private void forgetBorderState(JLabel label)
    {
        if (label != null) this.borderState.remove(label);
    }

    private void highlightLabel(JLabel label, Color color)
    {
        if (label != null)
        {
            // Never the grid's padding row and column (OB-161, OB-055): nothing would clear an outline there.
            if (label instanceof LayoutLabel && ((LayoutLabel) label).isSpacer()) return;

            boolean palette = this.getX((LayoutLabel) label) == -1;

            int width = palette ? NEW_COMPONENT_BORDER_WIDTH : COMPONENT_BORDER_WIDTH;

            // Sized as the resting border's room, so hovering moves nothing (OB-056).
            Border resting = restingBorder(palette, isAutonomyMode());

            boolean reservesRoom = resting != null
                && resting.getBorderInsets(label).left > 0;

            applyBorder(label,
                reservesRoom
                    ? BorderFactory.createLineBorder(color, width) : overlayLine(color, width),
                HIGHLIGHT_KEY_PREFIX + color.getRGB() + ":" + width + ":" + reservesRoom);
        }
    }
    
    void clearBordersFromChildren(JPanel panel)
    {
        if (panel != null)
        {
            // Asked once per pass: each preference read is a registry round trip on Windows.
            final boolean palette = newComponents.equals(panel);
            final boolean autonomy = isAutonomyMode();
            final boolean grid = showGrid();
            final Border resting = restingBorder(palette, autonomy, grid);
            final String restingKey = "rest:" + palette + ":" + autonomy + ":" + grid;

            for (java.awt.Component component : panel.getComponents())
            {
                if (component instanceof JLabel)
                {
                    JLabel label = (JLabel) component;

                    // Every tile but spacers (OB-055), by what it is, not whether it has a border, or a hover leaves a
                    // line.
                    if (label instanceof LayoutLabel && !((LayoutLabel) label).isSpacer())
                    {
                        applyBorder(label, resting, restingKey);
                    }
                }
            }
            
            // Highlight copied tile border
            if (this.hasToolFlag() && layout.getComponent(lastX, lastY) != null)
            {
                this.highlightLabel(this.grid.getValueAt(lastX, lastY), COMPONENT_BORDER_COPIED_COLOR);
            }

            // And the picked squares, or a hover would hide a selection Delete still acts on.
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
    /** Whether shiftUp would do anything (`LayoutEditorReshape.canShiftUp`). */
    public boolean canShiftUp()
    {
        return this.reshapeOps.canShiftUp();
    }

    /** Whether the page can grow by this much within MAX_SIZE (`LayoutEditorReshape.roomToGrow`). */
    public boolean roomToGrow(int rows, int columns)
    {
        return this.reshapeOps.roomToGrow(rows, columns);
    }

    /** Whether shiftLeft would do anything (`LayoutEditorReshape.canShiftLeft`). */
    public boolean canShiftLeft()
    {
        return this.reshapeOps.canShiftLeft();
    }

    /** Whether shiftDown would do anything (`LayoutEditorReshape.canShiftDown`). */
    public boolean canShiftDown()
    {
        return this.reshapeOps.canShiftDown();
    }

    /** Whether shiftRight would do anything (`LayoutEditorReshape.canShiftRight`). */
    public boolean canShiftRight()
    {
        return this.reshapeOps.canShiftRight();
    }
    /** Inserts a row at the hovered square, pushing the rest along (`LayoutEditorReshape.shiftUp`). */
    public void shiftUp()
    {
        this.reshapeOps.shiftUp();
    }
    
    /** Delegates to `LayoutEditorReshape.shiftDown`. */
    public void shiftDown()
    {
        this.reshapeOps.shiftDown();
    }
    
    /** Delegates to `LayoutEditorReshape.shiftLeft`. */
    public void shiftLeft()
    {
        this.reshapeOps.shiftLeft();
    }
    
    /** Delegates to `LayoutEditorReshape.shiftRight`. */
    public void shiftRight()
    {
        this.reshapeOps.shiftRight();
    }

    /** Writes the page's size into the Diagram Size heading's tooltip, re-read after every grow and shrink. */
    void showDiagramSize()
    {
        if (this.diagramSize == null) return;

        // The heading's text too, which the generated form points at the visibility key.
        this.diagramSize.setText(I18n.t("layout.ui.sectionDiagramSize"));

        this.diagramSize.setToolTipText(I18n.f("layout.ui.tooltipDiagramSize",
            layout.getSx() + " x " + layout.getSy()));

        // And the two buttons' tooltips, which the generated form leaves empty.
        if (this.plusButton != null)
        {
            this.plusButton.setToolTipText(I18n.t("layout.ui.tooltipGrowDiagram"));
        }

        if (this.minusButton != null)
        {
            this.minusButton.setToolTipText(I18n.t("layout.ui.tooltipShrinkDiagram"));
        }
    }

    /** Grows the diagram by one: a column on the right and a row at the bottom (`LayoutEditorReshape.growEdges`). */
    public void growEdges()
    {
        this.reshapeOps.growEdges();
    }



    /**
     * Shrinks the diagram by one, undoing exactly what growEdges adds.
     *
     * Delegates to `LayoutEditorReshape.shrinkEdges`.
     */
    public void shrinkEdges()
    {
        this.reshapeOps.shrinkEdges();
    }

    /** Delegates to `LayoutEditorReshape.addRowsAndColumns`. */
    public void addRowsAndColumns(int rows, int cols)
    {
        this.reshapeOps.addRowsAndColumns(rows, cols);
    }
    
    /**
     * Turns the diagram's text labels on, idempotently (OB-174), unlike `toggleText`; for a ticked Text Labels box in
     * the autonomy editor (FR-133).  Costs no rebuild when nothing changes.
     */
    public void showTextLabels()
    {
        if (!this.layout.getEditHideText()) return;

        toggleText();
    }

    /** Hides the diagram's writing, idempotently (FR-061, FR-133); the captions stay (OB-272). */
    public void hideTextLabels()
    {
        if (this.layout.getEditHideText()) return;

        toggleText();
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

            // And the autonomy panel's Text Labels box, this switch's visible state there (FR-133), after the rebuild.
            if (autonomyPanel != null && autonomyPanel.isVisible())
            {
                autonomyPanel.textLabelsChanged(!this.layout.getEditHideText());
            }
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
    void refreshGrid()
    {
        if (!pauseRepaint)
        {
            lock.lock();
            try
            {
                // Rerun only once it has begun: a request before then is part of the pending redraw.
                if (isRunning)
                {
                    if (redrawStarted) needsRerun = true;
                    return;
                }

                isRunning = true; // Mark as running
                redrawStarted = false;

                // Execute the method logic
                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    lock.lock();
                    try
                    {
                        redrawStarted = true;
                    }
                    finally
                    {
                        lock.unlock();
                    }

                    try
                    {
                        drawGrid();

                        // The track editor's outlines drawn back (`drawTheOutlinesBack`)
                        drawTheOutlinesBack();
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
    

    /** Tells the setup every square of this page has gone, in one call (`forgetBuiltOver` says why). */
    private void forgetWholePage()
    {
        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        if (autonomy == null) return;

        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> everywhere =
            new java.util.ArrayList<>();

        for (int x = 0; x < layout.getSx(); x++)
        {
            for (int y = 0; y < layout.getSy(); y++)
            {
                everywhere.add(new org.traincontrol.automationui.TileGraph.TileKey(
                    layout.getName(), x, y));
            }
        }

        if (autonomy.forgetTiles(everywhere)) rememberAutonomy(autonomy);
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

    // No coordinates toggle (OB-179; Adam: "coordinates should always be on with the grid, off without, and the menu
    // option gone."): `LayoutGrid.coordinatesVisible` holds the rule, `setShowGrid` the switch.

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
                TrainControlUI.YES_NO_OPTS[1] // NOT [0]: a reflexive Enter on this one destroys something // default selection = "Yes"
            );

            if (confirmation == JOptionPane.YES_OPTION)
            {
                this.snapshotLayout();
                
                // The setup forgets the page too, or its entries outlive the squares and the exit's unreconciled save
                // writes them.  Emptied first, then forgotten: forgetWholePage rebuilds from the squares.
                layout.clear();

                forgetWholePage();
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
            // The old grid lets go first, or its armed reveal timers drop a spinner into the new one.
            if (grid != null) grid.discard();

            grid = new LayoutGrid(this.layout, size,
                this.ExtLayoutPanel,
                this,
                true, parent);

            // A new panel, so it is told whether a drag picks
            this.showWhetherADragPicks();

            // A row of slack below and a column beside, plus the layout's gaps: else a scrollbar pushes the last row
            // out of sight, and a too-wide CENTER row sits at a negative x no scroll reaches.
            this.ExtLayoutPanel.setPreferredSize(new Dimension(
                grid.maxWidth + 2 * DIAGRAM_GAP + DIAGRAM_SLACK_COLUMNS * size,
                grid.maxHeight + size + 2 * DIAGRAM_GAP));

            grid.getContainer().revalidate();
            this.ExtLayoutPanel.revalidate();
            grid.getContainer().repaint();
            this.ExtLayoutPanel.repaint();

            // New labels know nothing of the autonomy annotations
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
     *
     * Delegates to `LayoutEditorUndo.canUndo`.
     *
     * @return 
     */
    public boolean canUndo()
    {
        return this.undoOps.canUndo();
    }
    
    /**
     * Checks if there is any history to redo
     *
     * Delegates to `LayoutEditorUndo.canRedo`.
     *
     * @return 
     */
    public boolean canRedo()
    {
        return this.undoOps.canRedo();
    }
    
    /** Delegates to `LayoutEditorUndo.snapshotLayout`. */
    synchronized void snapshotLayout()
    {
        this.undoOps.snapshotLayout();
    }
    
    /**
     * Restores previous layout state
     *
     * Delegates to `LayoutEditorUndo.undo`.
     */
    synchronized public void undo()
    {
        this.undoOps.undo();
    }
    
    /**
     * Restores previous layout state
     *
     * Delegates to `LayoutEditorUndo.redo`.
     */
    synchronized public void redo()
    {
        this.undoOps.redo();
    }
    
    /**
     * Sizes the window to its diagram, capped to the screen, when the user has no preference (OB-003).  Never below
     * what shows every control at any size, scale and language (OB-342): `EXTRA_MINIMUM_HEIGHT` over the base, the
     * diagram's 550 plus the sidebar, and the layout's own minimum plus chrome; data-worded controls have no minimum
     * width.
     */
    private void sizeForDiagram()
    {
        int sideways = sidebar == null ? 0 : sidebar.getPreferredSize().width;

        java.awt.Rectangle usable = usableScreen();

        this.setMinimumSize(new Dimension(
            // In proportion, Small to Large (FR-122)
            Math.min(550 + sideways + Math.max(0, (this.size - 30) * 200 / 30), usable.width),
            Math.min(630 + EXTRA_MINIMUM_HEIGHT + Math.max(0, (this.size - 30) * 320 / 30), usable.height)));

        // Never smaller than the window is (MT-096): a size the user chose is a floor.
        int wide = Math.max(getWidth(), grid.maxWidth + 210 + sideways);
        int high = Math.max(getHeight(), grid.maxHeight + 160);

        this.setPreferredSize(new Dimension(
            Math.min(wide, usable.width),
            Math.min(high, usable.height)));

        pack();

        // Never below the layout's own minimum plus chrome (OB-342).
        int asked = getContentPane().getMinimumSize().height + getHeight() - getContentPane().getHeight();

        if (asked > getMinimumSize().height)
        {
            setMinimumSize(new Dimension(getMinimumSize().width, Math.min(asked, usable.height)));
        }

        // Back on screen if the fit pushed it off an edge.
        int x = Math.max(usable.x, Math.min(getX(), usable.x + usable.width - getWidth()));
        int y = Math.max(usable.y, Math.min(getY(), usable.y + usable.height - getHeight()));

        if (x != getX() || y != getY()) setLocation(x, y);
    }

    /**
     * The window's title for its page and mode (MT-669), with the configuration the track editor has loaded or the
     * autonomy editor edits (FR-127).
     * @param autonomy whether the window is the autonomy editor
     * @return the title
     */
    private String titleFor(boolean autonomy)
    {
        String configuration = this.parent == null ? null
            : autonomy ? this.parent.getEditedConfiguration() : this.parent.getActiveDiagramConfiguration();

        if (configuration == null)
        {
            return I18n.f(autonomy ? "autosetup.ui.windowTitle" : "app.ui.windowLayoutEditorTitle", this.layout.getName());
        }

        return I18n.f(autonomy ? "autosetup.ui.windowTitleConfiguration" : "app.ui.windowLayoutEditorTitleConfiguration",
            this.layout.getName(), configuration);
    }

    /** Sets the title again after a configuration change (FR-127); event thread only. */
    void refreshTitle()
    {
        setTitle(titleFor(isAutonomyMode()));
    }

    public void render()
    {        
        javax.swing.SwingUtilities.invokeLater(() ->
        {
          try
          {
            layout.setEdit();
            this.setAlwaysOnTop(parent.isAlwaysOnTop());

            // Before the grid and pack(), so the size includes the sidebar; here, as setAutonomyMode has run by now.
            mountSidebar();

            drawGrid();

            // The mode's own title (MT-669)
            setTitle(titleFor(isAutonomyMode()));

            // The index first: one remembered entry for the window, whatever the page (MT-095).
            this.setWindowIndex(EDITOR_WINDOW_KEY);

            if (!this.isLoaded())
            {
                sizeForDiagram();

                // Over the top, only if the user set one.
                loadWindowBounds();
            }
            else
            {
                // A remembered size keeps the form's floor plus the sidebar, once (OB-342).
                int sideways = sidebar == null ? 0 : sidebar.getPreferredSize().width;

                this.setMinimumSize(new Dimension(Math.max(getMinimumSize().width, 550 + sideways),
                    Math.max(getMinimumSize().height, 650 + EXTRA_MINIMUM_HEIGHT)));
            }

            saveWindowBounds();
            
            setVisible(true);

            // And in front of the window that opened it (OB-173; Adam: "when clicking edit, the autonomy editor appears
            // below other windows, so I might not even notice it's there... we managed this carefully in 2.7.x"), as
            // `showOpenEditor` does.
            toFront();

            requestFocus();

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
          catch (RuntimeException | Error failed)
          {
            // Edit is handed back on any failure here, error or exception, as this runs outside openLayoutEditor's
            // catch.
            parent.autonomyEditorClosed();

            if (failed instanceof RuntimeException && parent.getModel() != null)
            {
                parent.getModel().log((RuntimeException) failed);
            }

            dispose();

            if (failed instanceof Error) throw (Error) failed;
          }
        });
    }
    
    /**
     * Closes this page and opens another at a square, asking about unsaved work first (the window disappears); nothing
     * is lost, as edits are in the shared session.
     * @param tile the square to open on
     */
    public void jumpToSquare(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        if (tile == null) return;

        // The sidebar's question and three answers (OB-046).
        if (!settleUnsavedWork()) return;

        layout.setEdit(false);

        // Carried before the window goes: the user closed nothing
        final boolean clicked = squareClicked;

        parent.autonomyEditorClosedFromTheEditor(
            () -> parent.openCarryingTheClick(clicked, () -> parent.openAutonomyEditor(tile)));

        dispose();
    }

    /**
     * Whether this window may be left, asking about unsaved work: closing can only offer to discard (`leaveFor` can
     * save).  In autonomy mode it asks about the setup, discarding it here on yes, as it lives in the shared session.
     *
     * @return true when the window may go
     */
    private boolean mayLeave()
    {
        if (isAutonomyMode())
        {
            if (!hasUnsavedAutonomyWork()) return true;

            int result = JOptionPane.showOptionDialog(
                this,
                I18n.t("autosetup.ui.confirmExitWithoutSaving"),
                I18n.t("layout.ui.dialogExitConfirmation"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[1] // NOT [0]: a reflexive Enter on this one destroys something
            );

            if (result != JOptionPane.YES_OPTION) return false;

            String failed = discardAutonomyWork();

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
            TrainControlUI.YES_NO_OPTS[1] // NOT [0]: a reflexive Enter on this one destroys something // default selection = "Yes"
        );

        return result == JOptionPane.YES_OPTION;
    }

    /**
     * The save / discard / cancel question, asked by the main window before exiting (OB-070): the exit capture would
     * commit work the user meant to Cancel.
     * @return true when it is all right to proceed - saved, discarded, or nothing to settle
     */
    public boolean maySettleBeforeExit()
    {
        // Restored and in front first (MT-703), or the exit waits on a hidden question
        if ((getExtendedState() & java.awt.Frame.ICONIFIED) != 0)
        {
            setExtendedState(getExtendedState() & ~java.awt.Frame.ICONIFIED);
        }

        toFront();

        if (!settleUnsavedWork()) return false;

        // A Discard has already rebuilt the railway from the restored setup.
        return true;
    }

    /**
     * Completes a track-mode Discard once the exit is certain: `undoAutonomyEdits` puts back the setup the gestures
     * wrote.  On `settledByDiscarding`, as a clean editor settles too, and only after everything that can refuse the
     * exit has said yes, or Cancel would have nothing left to put back.
     */
    public void completeExitDiscard()
    {
        if (this.settledByDiscarding && !isAutonomyMode()) undoAutonomyEdits();
    }

    /**
     * Save, discard or stay, wherever a page is left with work on it: leaving is not final, so Save is offered, and
     * is the default (OB-046; Adam: "settings should be saved or discarded before leaving.").
     * @return whether the caller may go ahead; false means the user chose to stay, or the save or the discard failed
     *         and said so
     */
    private boolean settleUnsavedWork()
    {
        boolean unsaved = isAutonomyMode() ? hasUnsavedAutonomyWork() : canUndo();

        // Cleared first, so no Discard from last time stands.
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

            // Cancelled, or the dialog was closed.  Put the sidebar back to what is actually on screen: nothing moved,
            // and a control showing the page they did not go to would be lying about where they are.
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

            // Discarding the SETUP has to happen here, because the setup is shared: the window that opens next looks at
            // the same session, so edits left in it would survive a discard.  Discarding the DIAGRAM happens by itself
            // (layoutEditingComplete re-reads the pages from disk), and undoAutonomyEdits below puts the setup back as
            // it was found.
            this.settledByDiscarding = answer == 1;

            if (answer == 1 && isAutonomyMode())
            {
                String failed = discardAutonomyWork();

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
     * Closes this window and opens the editor again, on another page or in the other mode: the exit and reopen the
     * sidebar does for the user.  Not a lighter operation than closing: the window is built around one diagram and one
     * mode, the diagram is re-read from disk on the way out, and the setup is put back as it was, so switching asks
     * what closing asks, for the same reason.
     *
     * @param page the page to open
     * @param autonomy whether to open the setup rather than the track
     */
    private void leaveFor(String page, boolean autonomy)
    {
        // NOBODY IS POINTING AT THE OLD PAGE ANY MORE (OB-198).  `hoveredSquare` refuses a stale label anyway, but a
        // field that is never cleared is one the next reader has to remember to distrust.
        autonomyHover = null;

        // Save, discard, or stay: see settleUnsavedWork.
        if (!settleUnsavedWork()) return;

        // Committed: from here the switch is going to happen, and nothing else may start one until it has (FR-036).
        // After the question, so answering "stay here" leaves this alone.  The window STAYS: only the frame survives,
        // and the teardown still runs in full, because a switch is an exit as far as the rest of the application is
        // concerned (the diagram is re-read from disk, the setup is put back as it was found, and the main window is
        // told).
        //
        // The work is posted (OB-016) rather than run straight from the callback, but that does not order it against
        // the main window's repaint: autonomyEditorClosed and layoutRefreshComplete both end by calling repaintLayout,
        // which submits to LayoutGridRenderer, a single-thread ExecutorService, and only calls invokeLater from inside
        // THAT, so its EDT task is not queued when this one is.  The viewer is not built in edit mode because of
        // LayoutGrid: the LAYOUT decision asks whether that grid is inside an editor rather than reading the shared
        // flag, so the viewer does not change shape whoever wins the race.  The posting is kept because it is harmless
        // and narrows the window on the half that is still shared (clickability is decided by the same flag).
        if (isAutonomyMode())
        {
            layout.setEdit(false);

            // Raised HERE, immediately before the work is posted: raised earlier, with `undoAutonomyEdits` (which
            // parses every page) still to run, a throw would leave it up with nothing on its way to lower it, and the
            // editor would refuse every page and mode change in silence until it was closed and reopened.
            changingPage = true;

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                // POSTED FROM A FINALLY, like the other branch gets from layoutEditingCompleteThen:
                // autonomyEditorClosed reaches rebuildRunningLayoutFromSetup, and a throw in it would leave the latch
                // up with nothing on its way to lower it.
                try
                {
                    parent.autonomyEditorClosed();
                }
                finally
                {
                    javax.swing.SwingUtilities.invokeLater(() -> arriveAt(page, autonomy));
                }
            });

            return;
        }

        // Both halves of the edit, or neither - see confirmExit
        undoAutonomyEdits();

        // Raised HERE, as in the autonomy branch above.
        changingPage = true;

        javax.swing.SwingUtilities.invokeLater(() ->
            parent.layoutEditingCompleteThen(() ->
                javax.swing.SwingUtilities.invokeLater(() -> arriveAt(page, autonomy))));
    }

    /**
     * Re-points this window at another page, or the other mode, without closing it: what openLayoutEditor does to a NEW
     * frame, done to this one, with the same checks (a setup can stop being editable between one click and the next, so
     * an impossible mode falls back to the track and the sidebar is put back to say so).
     *
     * @param page the page to show
     * @param autonomy whether to show the setup rather than the track
     */
    private void arriveAt(String page, boolean wanted)
    {
        // Arrived, whatever happens below.  Cleared FIRST so that a failure inside this method cannot leave the window
        // refusing every further switch, a worse fault than the one the latch exists to stop.
        changingPage = false;

        // CLOSED ON THE WAY (MT-649): the switch's work is posted, so a close can land before it arrives, and arriving
        // at a closed window would put it back together out of sight and grey Edit and Edit Autonomy Paths for the
        // session; the close has already given them back.
        if (!isDisplayable()) return;

        // THE SQUARE UNDER THE POINTER IS FORGOTTEN (Adam: "on page switch - yes, clear the active square"): Delete and
        // the other keys that act on it would ask the old page's coordinates on the new page until the pointer moved,
        // in both editors, the autonomy editor's square being read against the page now showing.
        this.lastHoveredX = -1;
        this.lastHoveredY = -1;
        this.autonomyHover = null;
        this.pointerOnGrid = false;

        try
        {
            boolean autonomy = wanted;

            // Still editable?  Asked again rather than assumed from the click, for the same reason
            // openLayoutEditor asks: the answer can change while a save dialog is on screen.
            org.traincontrol.automationui.AutonomySession session =
                autonomy ? parent.editableAutonomySession() : null;

            if (autonomy && (session == null || parent.aRunHoldsTheSetup()))
            {
                javax.swing.JOptionPane.showMessageDialog(this,
                    session == null ? I18n.t("autosetup.ui.errorNoSetupToEdit")
                        : parent.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning"));

                autonomy = false;
                session = null;
            }

            // The main window's own idea of which page is being edited, which the editor takes from it rather than the
            // other way round.
            parent.selectLayoutPage(page);

            LayoutDiagram arriving = parent.getModel().getLayout(page);

            if (arriving == null)
            {
                // The page went away while the dialog was up - renamed, or deleted by a reload.  There
                // is nothing to show and nothing to be done about it here.
                confirmExitWithoutAsking();

                return;
            }

            // Everything remembered about squares on the page being LEFT: a TileKey is a page name and a pair of
            // coordinates, and a selection carried across a switch would name squares on a page that is no longer on
            // screen.  Cleared before the diagram changes underneath them, not after.  A pending cut is not stood down
            // here: Discard on the way out restores the cut track, and a restored square is not empty, which `cutMoves`
            // asks per square; a setup is still not carried ACROSS pages (paste snapshots only the destination page and
            // the source page's undo history is thrown away here, so a cross-page move could not be undone), because
            // `cutMoves` skips an origin whose page is not the page being pasted onto.  So leaving the page and COMING
            // BACK does not cost the cut: the squares are still empty and it is the same page again.

            this.selection.clear();
            this.previewSelection.clear();
            this.landingSelection.clear();

            this.toolFlag = null;

            // AND THE PALETTE'S OUTLINE WITH IT, as resetClipboard puts it back: the palette would show a piece armed,
            // the sign for what the next click does, when nothing was held
            this.clearBordersFromChildren(this.newComponents);

            // The undo history goes with the page.  Each entry is a snapshot of a page's COMPONENTS with nothing in it
            // that says which page it came from, so carried across a switch one Ctrl+Z on the new page would write the
            // old page's track over it.  Cleared rather than kept per page: the diagram has just been re-read from disk
            // by the teardown, so every snapshot in here describes a file state that no longer exists.
            this.previousLayoutComponents.clear();
            this.previousLayoutComponentsRedo.clear();

            this.layout = arriving;

            this.showTextCheckbox.setSelected(!arriving.getEditHideText());

            // Leaving the mode BEFORE the new diagram is drawn: setAutonomyMode(null) takes the setup panel and its
            // banner out of the window, and run after the grid it would be tearing down controls that the new grid has
            // already been wired to.
            setAutonomyMode(null);

            // The grid, the sidebar, the title and the size: the parts of render() that are about WHICH diagram this
            // is, not those about being a window (no pack of a remembered size, no second window listener, no
            // setVisible on something visible).
            layout.setEdit();

            mountSidebar();

            drawGrid();

            // THE ARRIVING MODE'S TITLE, from the start (MT-669; Adam: *"when switching pages, the autonomy editor
            // window title briefly shows the layout editor (not autonomy editor) title for a split second, before being
            // updated."*): the track editor's was set here and the autonomy editor's only by the step setAutonomyMode
            // posts, so the wrong one would show while the new page drew.
            setTitle(titleFor(session != null));

            if (session != null) setAutonomyMode(session);

            // Remembered, and only when the mode asked for is the mode arrived at, because a fallback is not a choice.
            if (autonomy == (session != null)) parent.rememberEditorChoice(session != null);

            // The same one entry as on the way in (see EDITOR_WINDOW_KEY).  The window does NOT move on a switch: only
            // the size may change, and only upwards, as sizeForDiagram grows it if the arriving page needs more room
            // and leaves it alone if not.
            setWindowIndex(EDITOR_WINDOW_KEY);

            sizeForDiagram();

            saveWindowBounds();

            // The editor is still open, so the button that opens one stays shut.  Both teardowns above hand it back
            // (autonomyEditorClosed directly, layoutEditingComplete through the refresh it finishes with), because both
            // tell the application an editor CLOSED.
            parent.setEditLayoutEnabled(false);

            // The undo point moves with the switch.  autonomyAsOpened is what Cancel restores, taken when the WINDOW
            // opened, which is right for a window that edits one page and then closes and wrong in both directions once
            // it carries on: arriving from the track editor, undoAutonomyEdits above has just consumed it, so Cancel on
            // the new page would have nothing to put back; arriving from the setup editor it survives untouched, so
            // Cancel on the new page would undo the setup all the way to when the window opened, including work the
            // user was asked about and chose to SAVE on the way here.  The prompt at the top of leaveFor has already
            // settled what happens to the previous page's edits; whatever the setup says now is what this page found.
            // Both halves of it (see takeTheUndoPoint): the disk half moves with a switch too.
            takeTheUndoPoint(parent.getAutonomySession());

            syncSidebar();

            revalidate();
            repaint();
        }
        catch (RuntimeException | Error failed)
        {
            // A half-switched window is worse than none: it is showing one page and wired to another.  An Error too,
            // given up and then thrown on: past a catch of exceptions only, the window stays half switched and counted
            // as an open editor.
            if (failed instanceof RuntimeException && parent.getModel() != null)
            {
                parent.getModel().log((RuntimeException) failed);
            }

            confirmExitWithoutAsking();

            if (failed instanceof Error) throw (Error) failed;
        }
    }

    /**
     * Gives up on this window when a switch cannot be completed, without asking anything: the questions have already
     * been asked and answered by the time a switch gets this far, and asking again would be about work that is no
     * longer anywhere.
     */
    private void confirmExitWithoutAsking()
    {
        layout.setEdit(false);

        parent.setEditLayoutEnabled(true);

        // Every other door out of this window pairs setEditLayoutEnabled(true) with a repaintLayout() (see
        // TrainControlUI's own layoutEditingComplete); without it the Central Station "Switch"/"Download" menu items,
        // only re-decided from repaintPathLabel which repaintLayout calls, stay however guardLayoutMenu last left them,
        // greyed while this editor was open.
        parent.repaintLayout();

        dispose();
    }

    /**
     * Writes what this window has been editing, for somebody who is switching away from it: the same two saves the Save
     * button makes, and the same refusal while trains are running.  Split out rather than called through the button's
     * handler because that handler CLOSES the window on success, the one thing a switch must not do before it is ready.
     *
     * @return true when it was written, so the caller may leave
     */
    private boolean saveBeforeLeaving()
    {
        try
        {
            // A ROUTE TOO (`TrainControlUI.aRunHoldsTheSetup`)
            if ((parent.getModel() != null && parent.getModel().isAutonomyRunning()) || parent.aRunHoldsTheSetup())
            {
                JOptionPane.showMessageDialog(this,
                    parent.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning"));

                return false;
            }

            // AND THE UNDO POINT TAKEN AGAIN, as the track editor's below drops it: kept, a close before the switch
            // arrives would ask to "throw away everything changed since you last saved" straight after a save
            if (isAutonomyMode())
            {
                if (!autonomyPanel.save()) return false;

                org.traincontrol.automationui.AutonomySession saved = parent.getAutonomySession();

                this.autonomyAsOpened = saved == null ? null : saved.snapshotSetup();

                return true;
            }

            layout.saveChanges(null, false);

            // Kept, so that undoAutonomyEdits below cannot put back a setup the user has just saved.
            // The snapshot exists for Cancel, and this is not one.
            this.autonomyAsOpened = null;

            // AND THE UNDO HISTORY WITH IT, as the arrival clears it: kept, a close before the switch arrives would ask
            // "close the editor without saving changes?" straight after a save
            this.previousLayoutComponents.clear();
            this.previousLayoutComponentsRedo.clear();
            this.previousCaptions.clear();
            this.previousCaptionsRedo.clear();

            return true;
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(this, I18n.f("error.generic", ex.getMessage()));

            return false;
        }
    }

    /**
     * What Escape does: let go of whatever is being held, and close only when there is nothing left.  **TWO REQUESTS,
     * ONE KEY, AND THE ORDER IS THE ANSWER TO BOTH**: FR-065 asks that Escape close the editor (Adam: *"escape closes
     * autonomy/track editor - same as closing via button, with warning shown as needed."*), and a review finding asks
     * that Escape put an armed tool down.  Closing outright would lose the second, expensively: a user who armed the
     * wrong tool, or picked the wrong squares, presses Escape to think again and would get the window shut, through
     * `mayLeave`, which asks whether to throw their unsaved work away.  Letting go first costs one extra press when
     * something is held, and nothing in the case FR-065 is about: an editor holding nothing closes on the first Escape.
     * `confirmExit` and not `dispose`, so this is the button's own path: the unsaved-work prompt, the autonomy-mode
     * branch, and the undo of autonomy edits all come with it.
     */
    private void escapePressed()
    {
        if (letGoOfWhateverIsHeld()) return;

        confirmExit();
    }

    /**
     * Drops the gesture in progress, and says whether there was one.  Each mode has its own idea of what it is holding,
     * and each already has one method that lets go of all of it, so this asks rather than reimplements.  The track
     * editor's list is the picked squares, the copied group, the armed tool and the picking MODE: letting go of the
     * squares but not of the mode is the half of Escape nobody asks for.  `resetClipboard` is what takes the tool flag
     * and the group with it.
     *
     * @return whether anything was let go of
     */
    private boolean letGoOfWhateverIsHeld()
    {
        if (isAutonomyMode()) return autonomyPanel.putToolsDown();

        // A BOX BEING DRAWN IS HELD TOO: left out, the release would pick it (picking off), and with nothing else held
        // Escape would close the editor with the button still down.
        boolean boxOpen = this.boxAnchorX >= 0 && this.boxAnchorY >= 0;

        boolean holding = boxOpen || !this.selection.isEmpty() || this.hasGroupClipboard()
            || this.hasToolFlag() || this.isSelectMode();

        if (!holding) return false;

        if (boxOpen)
        {
            // AND THE CLICK ITS PRESS WILL END IN, if the pointer never leaves the square: a press and release on one
            // square are a click, which with Shift picked the square and with picking on picked the tile up
            this.clickToIgnore = this.dragSource;

            this.boxAnchorX = -1;
            this.boxAnchorY = -1;
            this.dragSource = null;
            this.previewSelection.clear();
        }

        this.clearSelection();
        this.resetClipboard();
        this.setSelectMode(false);

        // AND THE DIAGRAM'S BORDERS PUT BACK: `resetClipboard` puts back only the palette's, so a tile picked up would
        // keep its red outline until the pointer entered another square, and `clearSelection` puts them back only when
        // there was a selection to let go of.  The square under the pointer keeps its blue one.
        if (this.grid != null) this.refreshSelectionBorders();

        return true;
    }

    /** If there are unsaved changes, checks with the user prior to closing the window. */
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

        // COUNTED BEFORE THE EDITOR CLOSES: no train is sent between here and the reset
        parent.layoutEditingCompleteFromTheEditor();

        this.dispose();
    }
    
    /**
     * The strip down the side of the window: which page, and which of the two editors.  Switching is not a lighter
     * operation than closing and reopening (it still closes and reopens, and still asks about unsaved work); the strip
     * means the user does not have to know that.  Hidden when it would say nothing (one page, and no setup to switch
     * to): a control that offers one choice is furniture.
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
        // Less on the right than the left (MT-097): even padding looks wrong now the pages are a LIST, as a list has
        // its own inset before the text and a selection bar that runs to its edge, so the eight on the right read as
        // sixteen.
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

        // One width, whatever the pages are called: a strip that grows to fit a long page name takes that width off the
        // diagram for as long as the window is open.  The buttons truncate instead and say the whole name in a tooltip.
        sidebar.setPreferredSize(new java.awt.Dimension(SIDEBAR_WIDTH, sidebar.getPreferredSize().height));
        sidebar.setMaximumSize(new java.awt.Dimension(SIDEBAR_WIDTH, Short.MAX_VALUE));
        sidebar.setMinimumSize(new java.awt.Dimension(SIDEBAR_WIDTH, 0));

        // Wrapped rather than added: the window's own contents are laid out by the form, which is generated, so there
        // is nowhere in it to put this; putting the form's panel inside a new one leaves it as the designer built it.
        javax.swing.JPanel wrapper = new javax.swing.JPanel(new java.awt.BorderLayout());

        wrapper.add(sidebar, java.awt.BorderLayout.WEST);
        wrapper.add(formPane, java.awt.BorderLayout.CENTER);

        setContentPane(wrapper);
    }

    /**
     * Moves to the next page along, or the previous one (FR-036; Adam: "just have them call existing components to
     * reuse the same guards/warnings."), so it ends at `leaveFor`, which clicking a row of the page list calls too: the
     * unsaved-work question, the mode the new page opens in and the latch that stops a second switch starting inside
     * the first belong to that method.  IT WRAPS: stopping dead at the last page means noticing which end you are at
     * before you can decide which key to press, and the pages are a ring the user is looking through, not a list being
     * consumed.  Silent when there is nowhere to go (one page, or a switch already under way).
     *
     * @param by 1 for the next page, -1 for the one before
     */
    private void stepPage(int by)
    {
        // `changingPage` as well as `switching`: the first says a switch is in flight, which is
        // exactly the state auto-repeat produces and the one `switching` cannot see.
        if (switching || changingPage || parent == null || parent.getModel() == null) return;

        java.util.List<String> pages = parent.getModel().getLayoutList();

        if (pages == null || pages.size() < 2) return;

        int at = pages.indexOf(layout.getName());

        // The page being looked at is not in the list, which is a state this cannot reason from.
        if (at < 0) return;

        int next = (at + by + pages.size()) % pages.size();

        leaveFor(pages.get(next), isAutonomyMode());
    }

    /**
     * Which page, as a list of rows; the control type lives here and nowhere else, so swapping the body leaves the rest
     * of the window unaware.
     */
    private javax.swing.JComponent buildPageControl(java.util.List<String> pages)
    {
        // A list (OB-004): twenty buttons down the side of a window read as twenty things to press, twenty rows read as
        // a list of pages, which is what they are.
        pageList = new javax.swing.JList<>(pages.toArray(new String[0]));

        pageList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        pageList.setSelectedValue(layout.getName(), true);

        // docs/UI-standards.md: regular text is Segoe UI Plain 14, black (OB-014).  Rows of a list are text, not
        // buttons.
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

        // On the CLICK, not on the selection change: a ListSelectionListener also fires when the selection is set in
        // code (syncSidebar does on every cancelled switch), and the arrow keys would move the selection and start a
        // switch per row travelled through.  A click is the gesture that means "go here".
        pageList.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                int row = pageList.locationToIndex(e.getPoint());

                // And not while one is already in flight: two quick clicks on two different rows would queue two
                // switches, and the second would read a page name the first had not yet changed.
                if (row < 0 || switching || changingPage) return;

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
     * Which editor, as a pair of radio buttons: the control type is confined to this method, because which control this
     * ought to be is the sort of thing that gets decided again after somebody has used it.  The setup half is disabled
     * rather than hidden when there is nothing to set up: hidden, the window would look different for a reason the user
     * cannot see; disabled with a tooltip says what is missing, and the missing thing (a configuration) is something
     * they can go and load.
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
        else if (parent.aRunHoldsTheSetup())
        {
            autonomyModeButton.setEnabled(false);
            autonomyModeButton.setToolTipText(
                AutonomyEditorPanel.wrapped(parent.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning")));
        }

        return column;
    }

    private javax.swing.JToggleButton modeTab(String text, final boolean autonomy)
    {
        // A radio button (OB-004): two mutually exclusive views of one diagram.  A JRadioButton IS a JToggleButton, so
        // the group, the fields and syncSidebar need no change.
        javax.swing.JToggleButton tab = new javax.swing.JRadioButton(text);

        // docs/UI-standards.md: regular text is Segoe UI Plain 14, black (OB-015): a radio button's label is a choice
        // written out, and the standard reads it as text, not as a button.
        tab.setFont(new java.awt.Font("Segoe UI", 0, 14));
        tab.setForeground(java.awt.Color.BLACK);

        tab.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        tab.setFocusable(false);
        tab.setOpaque(false);

        tab.setSelected(isAutonomyMode() == autonomy);

        tab.addActionListener(e ->
        {
            if (switching || changingPage || isAutonomyMode() == autonomy)
            {
                syncSidebar();
                return;
            }

            leaveFor(layout.getName(), autonomy);
        });

        return tab;
    }

    /**
     * The page list, scrolling when there are more of them than the strip can show: without this the strip would grow
     * past the bottom of the window and the last few pages could not be reached at all.  No border and no horizontal
     * bar, so it looks like the column it replaces until it needs to scroll.
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
     * One remembered position and size for the editor, whatever page it is showing (MT-095): per-page entries would
     * make switching tabs pick the window up and put it somewhere else.
     */
    private static final String EDITOR_WINDOW_KEY = "editor";

    /**
     * How many pages the strip shows before it starts scrolling: the pages are rows of a list at 24px and the strip
     * runs the height of the window, so a small number would scroll a railway of twelve pages for no reason (MT-056).
     */
    private static final int SIDEBAR_TABS_BEFORE_SCROLLING = 20;

    /**
     * The air under a blue heading: the form leaves this much under its own, and everything added by hand beside them
     * leaves the same or the column reads as two columns that happen to be touching.
     */
    static final int HEADING_GAP = 6;

    /**
     * The air between one checkbox and the next under Visible Elements: what the form leaves between its own two
     * (OB-312; Adam: "check spacing between visible elements checkboxes").
     */
    static final int CHECKBOX_GAP = 4;

    /**
     * Puts the sidebar back to what is actually on screen: selecting a tab is a REQUEST, and a refused one (the user
     * answered no to losing their work) must not leave the control showing where they did not go.
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

    /**
     * Set while a page or mode switch has been ASKED FOR and has not yet arrived.  `switching` cannot answer this: it
     * is true only inside `syncSidebar`, synchronously, so it is false at every moment a second switch could be
     * started, while `leaveFor` answers the unsaved-work question, posts the rest of the work and returns before the
     * page has changed (anything reading `layout.getName()` meanwhile gets the page being left).  Holding + would queue
     * one full teardown per auto-repeat, all to the same destination, each re-reading the diagram from disk.  Set after
     * the question is answered, so cancelling never sets it, and cleared in `arriveAt`, where both branches end up once
     * the new page is in place.
     */
    private boolean changingPage;

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
            // The shortcuts that belong to autonomy mode too, handled BEFORE the guard that turns the rest of them off:
            // below the return they would be unreachable in the only mode where they do anything.  The guard's own
            // sentence says why this one is different: "Every shortcut below places, cuts, rotates or retextures a
            // tile."  This one shows and hides a number.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_G)
            {
                toggleTrackLengths();

                return;
            }

            // Control+L and Control+D, for the same reason and by the same rule (MT-109; Adam: "Control+G works, but
            // control +L does not in the autonomy editor."): all three show or hide something ABOUT the diagram without
            // changing it, so all three sit above the guard.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_L)
            {
                // IN THE AUTONOMY EDITOR, THE NEXT CAPTION OPTION (OB-272: *"make control+L cycle the options"*), the
                // dropdown's four (FR-133); the Text Labels box is not part of the cycle.  The same test `toggleText`
                // uses to decide the dropdown is showing.  In the track editor it is the text switch, the only text
                // setting there.
                if (autonomyPanel != null && autonomyPanel.isVisible()) autonomyPanel.cycleCaptionMode();
                else toggleText();

                return;
            }

            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_D)
            {
                toggleAddresses();

                return;
            }

            // Control+K draws and hides the grid, and with it the column and row numbers (OB-179): the numbers are part
            // of the grid, so the key that showed them (FR-057) is the key that shows the grid, which has no shortcut
            // of its own besides the checkbox.  Above the guard with Control+G, L and D, by its own rule: this shows
            // and hides scenery.  Both modes, because the coordinates are what warnings are written in and the track
            // editor is where squares get moved to the coordinates a warning named.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_K)
            {
                setShowGrid(!showGrid());

                return;
            }

            // Control+H sets the home locomotive of the square under the pointer (Adam: "set control+H for home."): a
            // second door onto the menu item, as Control+S is to Rename.  Autonomy mode only, and it takes
            // `hoveredSquare` for the reason MT-258 item 4 records: the placement variables are deliberately not set in
            // that mode, so asking them gets -1,-1 and a key that fires and finds nothing.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_H)
            {
                org.traincontrol.automationui.TileGraph.TileKey over = hoveredSquare();

                if (over != null && autonomyPanel != null) autonomyPanel.promptHomeFor(over);

                return;
            }

            // Control+S names the square under the pointer (MT-257 item 4; Adam: "add the keyboard shortcut back to the
            // autonomy editor, we already have the function in the right click menu there."): a second door onto
            // `Rename` on the autonomy editor's right-click menu.  Above the guard below, by its own rule ("Every
            // shortcut below places, cuts, rotates or retextures a tile."): naming a square does none of the four.  It
            // takes the square the pointer is over, the same the right-click menu would open on, so the key and the
            // menu cannot disagree about which one is meant.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_S)
            {
                // `hoveredSquare`, NOT the placement variables (MT-258 item 4): autonomy mode never sets those, so the
                // key would fire, find nothing, and do nothing in the only mode where it means anything.  Nor
                // `autonomyHover` directly (OB-198): nothing clears that field, so a page change left it naming a
                // square on the page before.
                org.traincontrol.automationui.TileGraph.TileKey over = hoveredSquare();

                if (over != null && autonomyPanel != null) autonomyPanel.promptNameFor(over);

                return;
            }

            // Control+E measures the square under the pointer (FR-066; Adam: *"a keyboard shortcut for setting a
            // square's length"*, and for the key **"let's do E"**).  E is the e in "length"; Control+D is taken twice,
            // by this editor's addresses toggle and the main window's locomotive adder.  The main window's key chain
            // filters Control (OB-197), so Control+E selects no locomotive button there, and
            // `regression.testNoTwoShortcutsShareAKey` keeps it filtering.  Above the guard below, by its own rule:
            // measuring a square places, cuts, rotates and retextures nothing.  `hoveredSquare` for Control+S's reason
            // (MT-258 item 4), the one door that refuses a label the page change left behind (OB-198).
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_E)
            {
                org.traincontrol.automationui.TileGraph.TileKey over = hoveredSquare();

                if (over != null && autonomyPanel != null) autonomyPanel.promptLengthFor(over);

                return;
            }

            // Control+N shows a station's name on the square under the pointer (FR-086; MT-397: *"let's add a hotkey
            // for 'show station name here' too"*).  N for name, free here.  Above the guard below with the other keys
            // that are, by that guard's own rule: a caption places, cuts, rotates and retextures nothing.  The square
            // under the pointer, as Control+S and Control+E take it.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_N)
            {
                org.traincontrol.automationui.TileGraph.TileKey over = hoveredSquare();

                if (over != null && autonomyPanel != null) autonomyPanel.showStationNameFor(over);

                return;
            }

            // Control+B is the same door onto the STATION's own maximum.  Control+M was asked for and is taken in the
            // MAIN window, which toggles the menu bar with it; it is not taken in this editor, as the main window's
            // `KeyEventPostProcessor` returns at once unless the focus owner `isDescendingFrom` that window, but the
            // choice stands on its own.  `regression.testNoTwoShortcutsShareAKey` prints what is free in both windows
            // (B J O P Q U W), and **B** is for berth, the word this project uses for the platform the maximum belongs
            // to.  Above the page-turning guard for Control+E's reason: setting a maximum places, cuts, rotates and
            // retextures nothing.  `hoveredSquare` rather than the placement variables, again for Control+E's reason.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_B)
            {
                org.traincontrol.automationui.TileGraph.TileKey over = hoveredSquare();

                if (over != null && autonomyPanel != null)
                {
                    autonomyPanel.promptMaxTrainLengthFor(over);
                }

                return;
            }

            // Plus and minus walk through the pages, in BOTH editors (FR-036).  Above the guard below, for its own
            // sentence's reason: moving to another page places, cuts, rotates and retextures nothing (MT-109 is the
            // ticket about keys filed as fixed while sitting below that line doing nothing).  Four keycodes for two
            // keys: the main row reports plus as VK_EQUALS unless shift is held, the numpad reports VK_ADD, and a
            // keyboard laid out for another language may report either.
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

            // ESCAPE SITS ABOVE THE GUARD, with Control+G, L, D and K (FR-065; Adam: "escape closes autonomy/track
            // editor - same as closing via button, with warning shown as needed."): below the guard it would do nothing
            // in autonomy mode, and `AutonomyEditorPanel`'s own Escape binding cannot cover for it, for the reason the
            // paragraph below this guard spells out about key bindings in this window.
            if (evt.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                escapePressed();

                return;
            }

            // Every shortcut below places, cuts, rotates or retextures a tile.  None of them mean
            // anything while setting autonomy up, and all of them would edit the diagram silently.
            if (isAutonomyMode()) return;

            // The selection shortcuts live HERE, in the handler that actually runs: key bindings on the root pane's
            // WHEN_IN_FOCUSED_WINDOW map are dead in this window, as every control is setFocusable(false) and tiles are
            // JLabels that never take focus, so the FRAME is the focus owner, and for a heavyweight focus owner the
            // focus manager walks the parent chain, which for a top-level frame is empty.  Selection first, then the
            // single-tile behaviour, so nothing changes while nothing is picked.
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_V)
            {
                if (this.hasGroupClipboard() && getLastHoveredLabel() != null)
                {
                    this.pasteSelection(getX(getLastHoveredLabel()), getY(getLastHoveredLabel()));
                }
                else if (this.hasToolFlag() && getLastHoveredLabel() != null)
                {
                    this.executeTool(getLastHoveredLabel());
                }
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X)
            {
                // Cut completes copy, paste, rotate and delete for a group: without it, moving several squares to
                // another page means copying, pasting, and going back to delete the originals by hand.
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
            // There is no Shift+C or Shift+R (a whole column or row pasted from the hovered tile): selecting the
            // squares you mean and dragging them replaces them, and a mis-aimed one would write over a whole row with
            // undo as the only way back.
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
                // growEdges, the same thing the menu does, so the keyboard and the menu grow the diagram alike and both
                // have the matching shrink.
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
            // SELECT BY DRAGGING A BOX, as its item on the right-click menu (FR-107)
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_M)
            {
                this.selectOnce();
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
        });
    }//GEN-LAST:event_formKeyPressed
    
    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        try
        {
            // A ROUTE TOO (`TrainControlUI.aRunHoldsTheSetup`)
            if (parent.getModel().isAutonomyRunning() || parent.aRunHoldsTheSetup())
            {
                JOptionPane.showMessageDialog(this, parent.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning"));
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

            // COUNTED BEFORE THE EDITOR CLOSES: no train is sent between here and the reset
            parent.layoutEditingCompleteFromTheEditor();

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
        // The margin: the pointer is over no square - and the grip stays
        pointerOffTheSquares();
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
