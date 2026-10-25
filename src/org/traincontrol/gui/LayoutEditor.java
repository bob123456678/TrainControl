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
            // The panel needs a way to the RUNNING layout for its "why is it not moving" test, the one question about
            // locomotives and occupancy rather than track, which cannot be answered from the setup alone.
            autonomyPanel = new AutonomyEditorPanel(session, layout.getName(), new Runnable()
            {
                @Override
                public void run()
                {
                    refreshAutonomyAnnotations();

                    // The main window draws the same setup on its own diagram and has no way to know an edit happened
                    // in here, so it is told after every one.
                    parent.refreshStaticAutonomyLayer();
                }
            });

            // Which window is the main one.  It cannot be found from here: this panel sits in a JFrame, a JFrame has no
            // owner, and the walk up the window tree ends at the editor.
            autonomyPanel.setMainWindow(parent);

            // WHERE THE RAILWAY IS, so the arrived-from menu can be built here too: supplied only from the track
            // diagram's door, the editor could never show that menu, and `setFacingAndMove` would degrade to writing
            // the setup until somebody opened the diagram's menu once, a feature that works on one of two surfaces
            // depending on what you opened earlier.  Asked, not built: each reader takes no railway as nothing to show.
            autonomyPanel.setRunningLayoutSource(
                () -> parent == null || parent.getModel() == null
                    ? null : parent.getModel().getAutoLayoutIfLoaded());

            if (session != null)
            {
                session.setRunningLayoutSource(
                    () -> parent == null || parent.getModel() == null
                        ? null : parent.getModel().getAutoLayoutIfLoaded());
            }

            // Asked for on every use rather than held, because loading a configuration replaces the Layout wholesale
            // and a kept reference would answer about the previous one without saying so.
            autonomyPanel.setLayoutSource(() -> parent.getModel().getAutoLayoutIfLoaded());

            // Setup mode writes to a page only when a station name is put on a square; the caption is part of the tile
            // art, so the grid is rebuilt rather than repainted, and the annotations are laid back on top of the new
            // one.
            autonomyPanel.setOnDiagramChanged(new Runnable()
            {
                @Override
                public void run()
                {
                    refreshGrid();

                    javax.swing.SwingUtilities.invokeLater(() -> refreshAutonomyAnnotations());
                }
            });

            // AND THE LIGHT DOOR BESIDE IT (OB-185): a change that only moves the arrows re-sets the annotations on the
            // labels already there rather than rebuilding every one, which flashes even when the tile art is identical.
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

            // A finding on another page: close this window and open one showing that page, at that square.  The editor
            // is built around a single diagram (the grid, the annotations and the page exclusion all follow from it),
            // so swapping pages in place would mean rebuilding most of the window, and reopening does the same thing
            // honestly.  Nothing is lost by closing: every edit made here has already gone into the shared session, and
            // this is deliberately not "exit without saving", which reloads the store to undo them.
            autonomyPanel.setOnJumpToPage(new java.util.function.Consumer<
                org.traincontrol.automationui.TileGraph.TileKey>()
            {
                @Override
                public void accept(org.traincontrol.automationui.TileGraph.TileKey tile)
                {
                    layout.setEdit(false);

                    // COUNTED BEFORE THE WINDOW GOES, and the click with it: the user closed nothing
                    final boolean clicked = squareClicked;

                    parent.autonomyEditorClosedFromTheEditor(
                        () -> parent.openCarryingTheClick(clicked, () -> parent.openAutonomyEditor(tile)));

                    dispose();
                }
            });

            // AND A FINDING'S OTHER SQUARES WITH IT: a guard notice about a station on another page names its signal
            // too, which the editor opened there outlines beside the station, as this one's list does
            autonomyPanel.setOnJumpToNotice((at, named) ->
            {
                layout.setEdit(false);

                // COUNTED BEFORE THE WINDOW GOES, and the click with it: the user closed nothing
                final boolean clicked = squareClicked;

                parent.autonomyEditorClosedFromTheEditor(
                    () -> parent.openCarryingTheClick(clicked, () -> parent.openAutonomyEditor(at, named)));

                dispose();
            });

            // Going to a link's other end: the same close-and-reopen as a finding on another page, but it asks first,
            // as a finding is the window taking the user somewhere to show a result and this is the user choosing to
            // leave a page they were working on.
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

            // The notices, floated over the bottom of the diagram when the findings are mounted below it
            autonomyBanner = new AutonomyBanner();

            autonomyPanel.setBanner(autonomyBanner);

            // Put the lengths toggle directly under Addresses, inside the window's own Toggle Visibility group:
            // GroupLayout cannot have a component added after the fact but can REPLACE one, so the Addresses box is
            // swapped for a small column holding both, without touching the generated form.
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

                // The same gap the form leaves between Text Labels and Addresses; stacked straight into a BoxLayout the
                // two would touch and read as one control with two lines.
                visibility.add(javax.swing.Box.createVerticalStrut(CHECKBOX_GAP));

                visibility.add(autonomyPanel.getShowLengths());

                // Directly under Track Lengths, with no gap: the two are one subject, what is measured and what is not
                // yet.
                autonomyPanel.getShowUnmeasured().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                visibility.add(autonomyPanel.getShowUnmeasured());

                // AND THE TRAINS, this editor's own (FR-119), a subject of their own, so a gap above
                visibility.add(javax.swing.Box.createVerticalStrut(CHECKBOX_GAP));

                autonomyPanel.getShowTrains().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                visibility.add(autonomyPanel.getShowTrains());

                // AND THE DIAGRAM'S TEXT under it, a subject of its own, so the same gap (FR-133, OB-312)
                visibility.add(javax.swing.Box.createVerticalStrut(CHECKBOX_GAP));

                autonomyPanel.getShowTextLabels().setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                visibility.add(autonomyPanel.getShowTextLabels());

                // WHAT THE CAPTIONS SAY, as one choice (FR-061; Adam: "add a Text Labels label and dropdown right above
                // Track Directions, with the following options: Station Names, Parked Locomotives, Home Locomotives,
                // and None."): it replaces two tick boxes and the form's own Text Labels switch, which were not
                // independent (ticking either box while the text was off changed nothing anybody could see, OB-174),
                // and options that exclude one another cannot be in a state that needs correcting.  In the window's own
                // heading style, copied off jLabel1 rather than restated.
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

                // PREFERRED as well as maximum: BoxLayout caps the
                // WIDTH at the maximum, but the column still takes its preferred width from its widest child, and this
                // combo prefers whatever "Parked Locomotives" needs plus the arrow.  The option text is shortened to
                // "Locs" (Adam: "use Locs if shorter text is needed in the dropdown") so 150 is a width the words fit
                // rather than one they are cut off at.
                captionChoice.setPreferredSize(new java.awt.Dimension(150, 24));
                captionChoice.setMaximumSize(new java.awt.Dimension(150, 24));

                visibility.add(captionChoice);
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

                // The Station Labels box's width in every language (OB-368): a phrase longer than it clips in the
                // box and reads in full in the popup, instead of widening the column and the window.
                choice.setPreferredSize(captionChoice.getPreferredSize());
                choice.setMaximumSize(captionChoice.getMaximumSize());

                visibility.add(choice);

                autonomyVisibility = visibility;
            }

            // Lengths and addresses are mutually exclusive: both print a number on the tile, and two numbers on one
            // square is unreadable.  Wired here because the Addresses box belongs to this window, not to the panel;
            // applied once on opening too, as Lengths is remembered between visits and can arrive already on, with
            // Addresses also on.
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

            // The findings go across the bottom, under the diagram and the full width of it: GroupLayout.replace on the
            // scroll pane that holds the grid, as the visibility box is fitted (the content pane is generated), so the
            // grid's slot is taken by a panel holding the grid AND the list beneath it.  Full width because these are
            // sentences; down the side they wrapped to four words a line beside the very diagram they describe.
            if (autonomyFindings == null)
            {
                javax.swing.JPanel stack = new javax.swing.JPanel(new BorderLayout());
                stack.setOpaque(false);

                // Guarded like the two above it: all three reach for the form's own layout, and the form is generated,
                // so if it is ever rebuilt with anything other than a GroupLayout, two would decline and the third
                // would throw.
                if (formPane.getLayout() instanceof javax.swing.GroupLayout)
                {
                    ((javax.swing.GroupLayout) formPane.getLayout())
                        .replace(this.jScrollPane1, stack);
                }

                // The notices float over the bottom of the diagram, which keeps its size and place as they come, grow
                // and go (OB-366)
                stack.add(autonomyBanner.floatOver(this.jScrollPane1), BorderLayout.CENTER);

                javax.swing.JPanel foot = new javax.swing.JPanel(new BorderLayout());
                foot.setOpaque(false);

                javax.swing.JScrollPane list = autonomyPanel.getFindingsPanel();

                if (list != null)
                {
                    // Taller than the panel asked for: it is across the window now rather than down the
                    // side, and its rows are the window's own size rather than the smaller hint size.
                    list.setPreferredSize(new java.awt.Dimension(100, 190));

                    foot.add(list, BorderLayout.CENTER);
                }

                // The count goes UNDER the list, not above it: it is a total, where in the sidebar it was a headline
                // over a column that did not contain the things it counted.
                foot.add(autonomyPanel.getBanner(), BorderLayout.SOUTH);

                stack.add(foot, BorderLayout.SOUTH);

                autonomyFindings = stack;
            }

            // The column heading belongs to the window, not to the palette that fills it: in autonomy mode "New
            // Components" describes something that is no longer there.
            this.jLabel1.setText(I18n.t("autosetup.ui.titleCap"));

            // The window is not editing the layout in this mode, and saying so in the title bar is the cheapest way to
            // keep somebody from wondering which of the two editors they opened.  Posted, not called: render() sets the
            // title from inside its own invokeLater and this method runs synchronously straight after it, so a direct
            // call would be overwritten a moment later.
            javax.swing.SwingUtilities.invokeLater(() -> setTitle(titleFor(true)));

            // Drawn again, because the first draw happens before this window knows what it is: render() builds the grid
            // and setAutonomyMode runs after it, so the labels would be rendered as the track diagram editor's (raw
            // "Point:" text) until something else rebuilt them.
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                // THE REMEMBERED CAPTION MODE, now that the panel is in a window (MT-292): the panel applies its own
                // mode while it is being built, and the half that reaches this diagram asks `getWindowAncestor`, which
                // is null then, so a remembered None would come back as a word and not as an effect.  Before the redraw
                // below, so the grid is built once with the captions the operator asked for.
                autonomyPanel.applyRememberedCaptionMode();

                refreshGrid();

                javax.swing.SwingUtilities.invokeLater(() -> refreshAutonomyAnnotations());
            });
        }

        this.newComponents.revalidate();
        this.newComponents.repaint();

        // entering the mode draws what is already decided; leaving it takes the marks with it
        refreshAutonomyAnnotations();

        // AND THE POINTER SAYS WHETHER A DRAG PICKS IN THIS MODE
        this.showWhetherADragPicks();
    }

    /**
     * Closes an autonomy-mode editor and puts the shared diagram back as it found it: render() calls setEdit() on the
     * LayoutDiagram the MAIN WINDOW also paints, and the flag is otherwise only reset by re-parsing the pages after a
     * diagram save, which autonomy mode never does.  Without this the main diagram would keep building its grid in edit
     * mode (labels wired to an editor that no longer exists, station labels suppressed, a ClassCastException on the
     * first click).  The window's always-on-top state is restored here too, for the same reason.
     */
    private void closeAutonomyMode()
    {
        layout.setEdit(false);

        // The main window shows the same pages, which have just changed shape, and has to put back what opening this
        // window changed (always-on-top and the disabled Edit button), normally done at the end of
        // layoutEditingComplete, a path autonomy mode never takes.  COUNTED BEFORE THE WINDOW GOES: no train is sent
        // between here and that.
        parent.autonomyEditorClosedFromTheEditor(null);

        dispose();
    }

    /**
     * Whether this editor window is setting autonomy up rather than editing the track: the single question every mouse
     * path asks, so that "autonomy mode" cannot mean one thing to clicking and another to dragging.
     */
    public boolean isAutonomyMode()
    {
        return autonomyPanel != null && autonomyPanel.isVisible();
    }

    /**
     * Scrolls a square into view and flashes it, so that a finding or a naming prompt is about a tile the user can
     * actually see.
     */
    public void reveal(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        if (grid == null || tile == null || !layout.getName().equals(tile.getPage())) return;

        LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

        if (label == null) return;

        label.scrollRectToVisible(new java.awt.Rectangle(0, 0, label.getWidth(), label.getHeight()));

        // ONE FLASH AT A TIME: every step of Mass Assign Lengths and Name Everything reveals a square and the flash
        // holds for over two seconds, so a walk moved on with Skip would leave a trail of yellow squares, each looking
        // like the one being asked about (MT-454); the square revealed last has its flash ended first.
        if (lastRevealed != null && lastRevealed != label) lastRevealed.endFlash();

        lastRevealed = label;

        // The diagram's own yellow flash, not a border: a border replaces the tile's grid line and has to be cleared to
        // null afterwards, which leaves the square without one.
        label.flashHighlight();
    }

    /**
     * Whether a square has been clicked in this visit to the editor, after which the click's tooltip line is not said
     * (MT-697: *"Stop showing it after the user has clicked any square"*, and *"have that tooltip tracking state reset
     * after the editor is closed"*).  The window's, not the page's or the preferences': every opening builds a new
     * window, so the next visit starts not knowing, and a page switch inside one visit keeps it.  Three doors change
     * page by closing this window and opening another (a finding or a notice on another page, a link's other end), and
     * the user closed nothing, so they hand it on (`carryTheClick`).
     */
    private boolean squareClicked = false;

    /** The next editor opened by one of this window's own reopenings starts knowing a square was clicked. */
    void carryTheClick()
    {
        squareClicked = true;
    }

    /** The square `reveal` flashed last, so the next reveal can end that flash. */
    private LayoutLabel lastRevealed;

    /**
     * Redraws what the autonomy editor is saying over every tile (directions, lengths, selection): the editor walks its
     * own grid and asks the panel about each square, as only the editor knows which labels exist right now.  Grid
     * indexes equal diagram coordinates because edit mode pins minx and miny to 0 (LayoutDiagram.checkBounds).
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

        // AND THE SQUARE UNDER THE POINTER ASKED AGAIN (FR-112): a tool armed or put down changes what a click there
        // does, and the pointer has not moved to ask.  Every refresh of the panel comes through here.
        if (active && hoveredSquare() != null) showWhatAClickDoes(autonomyHover);
    }

    /**
     * @return the autonomy tools, or null when the editor is editing the diagram
     */
    public AutonomyEditorPanel getAutonomyPanel()
    {
        return autonomyPanel;
    }

    /**
     * The square of the train drawn under a click, where the autonomy tool armed takes a train for its station - Test a
     * Path and Why Not Moving? (OB-354): a train is drawn over the captions and reaches past its tile on a curve, so
     * the click lands on a caption or on the next square, neither of which is the station it stands on.
     *
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

        // The square clicked first, where its own train is drawn there too
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
        // THE CLICK OF A BOX ESCAPE DROPPED, ignored once: kept only by a release on its square with the pointer held
        // still, so this is the left click that release makes.  Only the very next click is asked, and only a left
        // click is ignored: any other click finds the record gone.
        LayoutLabel ignore = this.clickToIgnore;

        this.clickToIgnore = null;

        if (ignore != null && label == ignore && e != null && e.getButton() == MouseEvent.BUTTON1) return;

        // In autonomy mode a click configures the track rather than editing it.  Routed here rather than through a
        // second listener because LayoutLabel hard-casts its parent to this class and calls this method, so a click
        // already arrives here.
        if (isAutonomyMode())
        {
            // A TRAIN CLICKED IS ITS STATION, to the tools that ask for a station (OB-354; Adam: "if clicking on a
            // locomotive in test a path / why not moving, make it register a click on the station.")
            LayoutLabel train = javax.swing.SwingUtilities.isRightMouseButton(e) ? null : trainClicked(e);

            int x = getX(train != null ? train : label);
            int y = getY(train != null ? train : label);

            if (x >= 0 && y >= 0)
            {
                org.traincontrol.automationui.TileGraph.TileKey tile =
                    new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), x, y);

                // Right-click opens the properties menu, the way it does on the graph; left-click applies the selected
                // tool.  A right-click must not run the tool as well.
                if (javax.swing.SwingUtilities.isRightMouseButton(e))
                {
                    autonomyPanel.tileRightClicked(tile, layout.getComponent(x, y),
                        label, e.getX(), e.getY());
                }
                else
                {
                    // ANY SQUARE CLICKED, and the click's tooltip line has done its job for this visit (MT-697's note)
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

        // Shift-click picks a square out, or puts it back.  Before anything else on a grid square, because every other
        // branch below does something to the diagram and picking must not.  Toggling rather than adding, so one square
        // too many is corrected by clicking it again.
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
     * Which way round a track tile put down on a square should lie so that it joins the track beside it (OB-292,
     * OB-360).  Every tile with roads in the port table is turned, however many; a lamp, a text label, a route button,
     * a turntable and the scissors drawings are not.  The way round that meets the most track wins: a switch first by
     * its base - the end every position of its points shares, where a straight's end would be (Adam, OB-360: *"the base
     * of a switch behaves like a straight track would, so that at least something is aligned (for switches, there's
     * always some guessing involved, which is ok)"*) - then by roads with every end on track (a curve with track on
     * both sides joins both: *"a curve connects if it can"*), then by ends on track, so one end beats none (*"meet the
     * single neighbor"*).  The way it is held wins whenever it meets as much as any other, and otherwise the nearest
     * turn from it does (a signal has four ways round, and which end it faces is the operator's).  A neighbour joins
     * where any of its roads, in any position of its points, reaches the edge the two squares share - the same port
     * table the autonomy graph is built from, so a tile laid this way is one the graph runs over.
     *
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

        // The held way first, then a quarter turn either way before the half turn: the first of equals stays.
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
     * How much track a tile of this type, this way round, on this square, meets: a thousand if the base of its switch is
     * on track, ten for every road, in any position of its points, with both its ends on track (or its one end, for a
     * tile whose road ends in the square), and one for every side with an end that is.
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

    /** Whether the square beside this one, on this side, has a road that reaches the edge between them. */
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

        // THE SQUARE UNDER THE POINTER KEEPS ITS OUTLINE: the pass above put every square back and nothing drew the
        // hover again, so Escape, and every hover over a selection, would leave it showing nothing until the pointer
        // entered another.  Asked of where the pointer IS (over a square of this grid, not since gone to the margin,
        // the palette or another page) and not of which square last wore the blue: the square under the pointer can be
        // wearing another outline (picked up, picked or the grip).  Drawn before the picked squares, so a picked square
        // still shows picked.
        LayoutLabel hovered = this.pointerOnGrid ? this.getLastHoveredLabel() : null;

        if (hovered != null) this.highlightLabel(hovered, COMPONENT_BORDER_HOVERED_COLOR);

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

        // And where a group being dragged would land, in blue.  Last, so a square that is both picked and a landing
        // square shows the landing: during a drag that is the live answer.
        for (org.traincontrol.base.TileSelection.At at : this.landingSelection.all())
        {
            LayoutLabel label = this.grid.getValueAt(at.getX(), at.getY());

            if (label != null) this.highlightLabel(label, COMPONENT_BORDER_LANDING_COLOR);
        }

        // The grip, last of all, so it is visible even where it sits on a square already outlined as picked: while
        // picking is on it is the only way to move what has been picked.
        int[] handle = selectionHandle();

        LayoutLabel grip = handle == null || !this.landingSelection.isEmpty()
            ? null : this.grid.getValueAt(handle[0], handle[1]);

        // The square that WAS the grip stops saying so: the corner moves as squares are added to the selection, and a
        // tooltip left on the old one would offer a drag that does something else entirely.
        if (this.handleLabel != null && this.handleLabel != grip)
        {
            this.handleLabel.setToolTipText(null);
        }

        this.handleLabel = grip;

        if (grip != null)
        {
            // Drawn as a BORDER, over the tile rather than behind it: the tile art is an opaque image, so a coloured
            // background is hidden by whatever track is drawn on that square, and the corner of a selection is as
            // likely to be track as an empty square.  A border paints AFTER the component does, and takes no space (a
            // thick one is laid inside the label, squeezing the tile art and opening a gap round the square).
            grip.setBorder(new SelectionGrip());

        // Set directly rather than through applyBorder, so the record has to be dropped (OB-157).
        forgetBorderState(grip);

            grip.setToolTipText(I18n.t("layout.ui.tooltipSelectionHandle"));
        }
    }

    /** The square currently drawn as the grip, so it can be told when it stops being one. */
    private LayoutLabel handleLabel;

    /**
     * The key for Select by Dragging a Box (FR-107): M for the marquee, free in this window, and the main window's own
     * Control+M never reaches it.
     */
    public static final String SHORTCUT_SELECT_BY_DRAGGING = "Control+M";

    /**
     * Turns picking on for ONE box, then turns it off again, for the right-click menu.  Dragging to pick cannot be the
     * default over track, because a drag that starts on a tile has to go on moving that tile; so a drag across a
     * diagram picks nothing unless the mode is on, and the mode is a button somebody has to find, turn on, use, and
     * remember to turn off.  This is the same without the remembering; the button stays for picking several boxes in a
     * row.
     */
    public void selectOnce()
    {
        setSelectMode(true);

        this.selectOnce = true;
    }

    /** Whether the picking mode currently on is the one-box kind. */
    private boolean selectOnce = false;

    /** Turns picking-by-drag on or off: `on` true makes a drag pick squares rather than move them. */
    public void setSelectMode(boolean on)
    {
        // Turning it off, by whatever route, also cancels the one-box promise
        if (!on) this.selectOnce = false;

        this.selectMode = on;

        this.showWhetherADragPicks();
    }

    /**
     * The diagram's pointer says whether a drag picks: a crosshair while it does (MT-661; Adam: "I'd rather not have
     * text popping up, why not just change the cursor?").  Squares inherit it, the one under the pointer included; only
     * the selection's grip keeps its own.  Asked again by every redraw, which builds a new panel, and by a switch of
     * mode: in Autonomy Setup no drag picks, so picking carried there shows the arrow until it comes back.
     */
    private void showWhetherADragPicks()
    {
        if (this.grid != null && this.grid.getContainer() != null)
        {
            boolean picks = this.selectMode && !this.isAutonomyMode();

            this.grid.getContainer().setCursor(picks ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : null);
        }
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
     * Delegates to `LayoutEditorSelection.copySelection`.
     *
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
     * Copies the picked squares and then clears them, as one step.
     *
     * Delegates to `LayoutEditorSelection.cutSelection`.
     *
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
     * Empties every picked square.  Delegates to `LayoutEditorSelection.deleteSelection`.
     *
     * @param tellAutonomy whether the setup should forget what was on those squares: TRUE for a plain delete, where the
     * track is
     *        going and its name should go with it; FALSE for a CUT, where the paste that follows carries the setup to
     * wherever
     *        the block lands, and forgetting the captions here would destroy the one part of it drawn on the diagram,
     * because
     *        forgetCaptionsAt deletes the caption on the square AND every caption naming it.
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
     *
     * @param tellAutonomy false when the CALLER is going to tell the setup what happened: a cut empties its squares one
     * at a time
     *        and the paste that follows tells the setup where the block went (deleteSelection), so announcing each
     * delete would
     *        say the track is gone when it is on its way somewhere else, and forgetCaptionsAt would delete the caption
     * on the
     *        square and every caption naming it, which the paste cannot bring back.
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

                // ONLY WHEN SOMETHING WAS ACTUALLY EDITED: cancelling a label edit must not disarm whatever tool the
                // user had picked up.  The re-add above stays unconditional: it puts the tile back where it was, and on
                // a cancel that is a no-op; skipping it as well would risk a blank square to fix a clipboard.
                if (newText != null) this.resetClipboard();
            }
            catch (IOException ex)
            {
                // A tile edit that fails should say so, not leave the component silently absent.  Logged rather than
                // shown as a dialog: the editor calls this per placement, and a dialog per failed tile would be worse.
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
        // Station captions are not text on the diagram: a caption belongs to the autonomy setup and points at the
        // sensor SQUARE, so it is set where the rest of autonomy is set, as a "Point:<name>" binding in a text square
        // broke whenever the station was renamed, named nothing once a station became several Points, and made saving
        // the layout the only way to record a caption.  Said plainly rather than removed from the menu, so somebody
        // looking for it here is told where it went.
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
                // Told which page this square is on, so a link is not offered it.
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

                    // A LINK MAY BE AIMED AT NOTHING, AND ONLY THIS PATH CAN SAY SO: zero from the popup means the
                    // operator chose "(none)", or the arrow was already pointing nowhere and they pressed OK without
                    // changing it.  setLogicalAddress cannot express that (it subtracts one for a link and throws on a
                    // negative), and aiming the link at the first page in the alphabet would silently make a choice
                    // that is the operator's.
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
     * What a tile's border looks like when nothing is happening to it (OB-028: "in the autonomy editor, the gray grid
     * is not needed. show the track diagram as it appears in the viewer, without the tile borders. make sure the
     * borders return in the editor.").  NO border at all in autonomy mode, so the tiles sit flush as in the viewer; an
     * empty border of the same thickness would leave a gap between tiles, an inset with nothing drawn in it showing the
     * panel behind, a white grid in place of the grey one.  The artwork does not shift when the hover swaps the border
     * for a coloured line (`receiveMoveEvent` gives this editor the blue outline too, OB-091), because overlayLine is
     * sized to the room the resting border takes (FR-006).  The palette keeps its visible border in both modes: those
     * tiles are a menu of things to place, not a picture of a railway, and the border is what separates one from the
     * next.
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
     * @return the border, or NULL when the grid is off: an empty border of the same width still takes up room and the
     * room shows
     *         the panel behind it, a white grid in place of the grey one (MT-127).  The hover outline answers the same
     * question,
     *         so nothing moves; see overlayLine.
     */
    public static Border restingBorder(boolean palette, boolean autonomy, boolean grid)
    {
        if (palette)
        {
            return BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR,
                NEW_COMPONENT_BORDER_WIDTH);
        }

        // NULL when the grid is off, not an empty border of the same width (MT-127, pinned by testEditorSurfaceRules):
        // tiles sit flush, as in the viewer, and hovering must not widen them either (FR-006), which is answered on the
        // HIGHLIGHT side by a border that paints without reserving anything (see overlayLine).
        if (!grid) return null;

        // BOTH editors reserve the room when the grid is on (OB-091): painting the grid with overlayLine, reserving
        // nothing, would size the cell as though there were no line and then paint one over it, eating a pixel of the
        // tile art (Adam: "enabling the grid widens the track diagram in the layout editor (how it always was, because
        // there is a double line in between cells), but not in the autonomy editor. Make the behavior of the autonomy
        // editor match so that there are no tile truncations.").  overlayLine stays the HOVER outline, where the
        // resting border is null and nothing may move.
        return BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR, COMPONENT_BORDER_WIDTH);
    }

    /**
     * A line drawn ON a component rather than around it: it paints, and it reserves no space.  The hover outline has to
     * be visible without changing anything's size: an ordinary LineBorder takes its width in insets, so one put on a
     * square resting with no border at all would make that square a pixel bigger in each direction and push the diagram
     * along in front of the pointer (FR-006).  A border is allowed to paint outside what it reserves, and nothing else
     * on the square draws in that outermost pixel.  Only needed where the resting border is null: with the grid on,
     * both borders are one-pixel lines and the sizes already match.
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
     * Puts the grid switch into the window's own Toggle Visibility group (FR-006: "make the gray grid an option you can
     * toggle in the visible elements.  on by default, but persisted if turned off.").  By REPLACING the Text Labels box
     * with a small column holding both, the trick the autonomy sidebar uses: a GroupLayout cannot have a component
     * added after the fact but can swap one for another, so this lands where it belongs without touching the generated
     * form.  Text Labels rather than Addresses, because Addresses is the one autonomy mode swaps for a column of its
     * own; two replacements of one component would fight.
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
     * The gap between Text Labels and Grid, which goes with Text Labels (OB-289): the autonomy editor hides Text
     * Labels, which its caption choice replaces (FR-061), and the gap left standing would put itself at the top of the
     * column, twice the space under Visible Elements that every other heading has under it (Adam: *"slightly too much
     * spacing/padding below "visible elements" in autonomy editor.  make it be consistent with other labels"*).
     */
    private java.awt.Component gapUnderTextLabels;

    /**
     * Whether the editor draws its grey grid: on by default and remembered when turned off (FR-006).  Static because
     * the border is: the label that asks has no editor to ask, and the answer is a property of the application rather
     * than of one window.
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

        // AND THE CHECKBOX SAYS SO, whoever asked (OB-179): there are two doors onto this, the box in the tools column
        // and Control+K, and a key that turns the grid off and leaves the box ticked is a window disagreeing with
        // itself about a setting the user can see.  Here, as this is the one method that knows the answer changed;
        // setting the box to what it already is costs nothing.
        if (showGridCheckbox != null) showGridCheckbox.setSelected(show);

        // The borders as they stand are the old answer, so all are put back; both panels, as the palette keeps its own
        // thicker line either way and asking it costs nothing.
        clearBordersFromChildren(this.newComponents);

        if (this.grid != null) clearBordersFromChildren(this.grid.getContainer());

        // AND THE AXIS NUMBERS, which follow the grid (OB-172): this method rewrites the CELL borders and nothing else,
        // so the ruler round the outside would keep what it was given when the diagram was last built.  A full redraw
        // rather than reaching into the grid to swap one border: the ruler is built from the diagram's offsets and
        // dimensions, which live in `drawGrid`, and duplicating that is how the two come to disagree.
        drawGrid();

        // AND THE OUTLINES DRAWN BACK, as after every redraw: Control+K and the Grid box would take the picked squares,
        // the grip, a box being drawn and the pointer's blue off until the pointer entered another square
        drawTheOutlinesBack();
    }

    /**
     * After a redraw, the track editor's outlines drawn back rather than only taken down: the picked squares, the
     * selection's grip, a box being drawn, a landing and the pointer's blue all still act, and would otherwise look
     * gone after an undo, a redo, Control+K or the Grid box until the pointer next moved.  `refreshSelectionBorders`
     * begins with the same border pass.  The autonomy editor draws its own hover and has no selection here, so it gets
     * the border pass alone.
     */
    private void drawTheOutlinesBack()
    {
        if (this.grid == null) return;

        if (isAutonomyMode()) this.clearBordersFromChildren(this.grid.getContainer());
        else refreshSelectionBorders();
    }

    /**
     * The pointer gone off the squares (into the margin, or straight off a square onto the tools column, the Grid box
     * or another window, which crosses no margin): over no square, so its blue comes off, and the rest (the picked
     * squares, the grip, a box being drawn) is drawn back.  Posted, so it lands after any outline the last square's
     * hover has posted, which would otherwise draw the blue back.
     */
    private void pointerOffTheSquares()
    {
        this.pointerOnGrid = false;

        javax.swing.SwingUtilities.invokeLater(this::drawTheOutlinesBack);
    }

    /**
     * The pointer has left a square of the track editor.  Onto another square, whose own hover moves the outline:
     * nothing here.  Off the squares altogether, which the margin does not always see since a pointer can go straight
     * onto the tools column: it is over no square.  The autonomy editor's hover is its own, and the palette's squares
     * are not the grid's.
     *
     * @param e the exit, in the square's coordinates
     * @param label the square left
     */
    public void receiveExitEvent(MouseEvent e, LayoutLabel label)
    {
        if (isAutonomyMode() || this.grid == null || label == null || getX(label) < 0) return;

        JPanel squares = this.grid.getContainer();

        // FROM WHAT WAS LEFT: the square, or an address label lying on it that passed its exit on
        java.awt.Component from = e.getComponent() != null ? e.getComponent() : label;

        java.awt.Point to = javax.swing.SwingUtilities.convertPoint(from, e.getPoint(), squares);

        // ONTO A SQUARE - another, this one with a menu or a dialog opened over it, or something drawn over one - AND
        // IN VIEW: the half of a square the view has scrolled out of sight is no square to be over
        if (squares.contains(to) && squares.getVisibleRect().contains(to) && aSquareAt(squares, to)) return;

        pointerOffTheSquares();
    }

    /**
     * Whether a square of the grid - not its padding - lies under a point of the grid's panel, whatever is drawn over
     * it: an accessory's address label lies on the top left of its square, and the padding along the last column and
     * row is no square.
     *
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
     * Hears the pointer arrive anywhere in this window, so that a pointer gone off the squares without a square's exit
     * saying so is still found off them: out from under a menu, or a dialog, that took the square's exit while the
     * pointer was over the square and closed with it over the tools column.  The toolkit keeps what it is given for the
     * life of the program, so this holds the window weakly and goes once the window has, besides being taken off in
     * `dispose`.
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
     * The right-click menu for a square, made to ask where the pointer is once it goes: it takes the square's exit
     * while the pointer is still over the square, and while it is open the watch leaves the outline alone, so,
     * dismissed with the pointer over the tools column or over a part of the window nothing listens on, nothing else
     * would ask again and the Grid box would draw the blue back.
     *
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

    /**
     * Where the pointer is, asked once what covered the squares has gone: over a square in view, the outline stays;
     * anywhere else, the pointer is off the squares.
     */
    private void askWhereThePointerIs()
    {
        if (!this.pointerOnGrid || isAutonomyMode() || this.grid == null) return;

        if (this.popup != null && this.popup.isVisible()) return;

        JPanel squares = this.grid.getContainer();

        java.awt.Point at = this.pointerOver.apply(squares);

        if (at != null && squares.getVisibleRect().contains(at) && aSquareAt(squares, at)) return;

        pointerOffTheSquares();
    }

    /**
     * Where the pointer is over the squares' panel, in its coordinates, or null when it is not over this window: the
     * screen's own pointer, a field so that a claim can say where without moving the real one.
     */
    private java.util.function.Function<JPanel, java.awt.Point> pointerOver = squares -> squares.getMousePosition(true);

    /** This window's watch on where the pointer arrives. */
    private final PointerWatch pointerWatch = new PointerWatch(this);

    /**
     * The pointer has arrived on something in this window.  Off the squares, unless it is a square or something drawn
     * over one, which the square's own hover answers, or a menu or a tooltip, which leave the square they cover as it
     * was: a menu is about that square.
     *
     * @param on what the pointer arrived on
     */
    void pointerArrived(java.awt.Component on)
    {
        if (!this.pointerOnGrid || isAutonomyMode() || this.grid == null || on == null) return;

        // THIS WINDOW, or anything in it.  The window's own entry counts: AWT hands it on only where nothing that
        // listens lies under the pointer, and every square listens, so it is the pointer back over a quiet part.
        if (on != this && javax.swing.SwingUtilities.getWindowAncestor(on) != this) return;

        if (this.popup != null && this.popup.isVisible()) return;

        if (on instanceof javax.swing.JPopupMenu || on instanceof javax.swing.JToolTip
            || javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JPopupMenu.class, on) != null
            || javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JToolTip.class, on) != null) return;

        // A SQUARE, or something drawn over one
        if (on.getParent() == this.grid.getContainer() && !(on instanceof LayoutLabel && ((LayoutLabel) on).isSpacer()))
        {
            return;
        }

        pointerOffTheSquares();
    }

    /**
     * Picks a station label up, so the drag has something in it (FR-035: "snapshot the label so users can see it is
     * being moved (make it follow the cursor while held down).").  On the LAYERED PANE's drag layer rather than in the
     * diagram: the diagram is a grid whose components are laid out by their constraints, and a floating copy has no
     * cell to be in; the drag layer exists for something on top of everything that belongs to no layout.  The grab
     * offset is where the pointer took hold of the pill, so the label hangs off the cursor at the same spot it was
     * picked up; started from the square rather than the label itself there is no such spot and the caller centres it.
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
     * Puts the picked-up label down (FR-035); safe to call when nothing was picked up, as it runs from the release
     * handler and a release arrives whether or not a drag ever started.
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
     * Marks the square a dragged station label would land on (FR-035): the drag feedback that says WHERE IT WILL GO, as
     * distinct from the ghost above, which says what is being carried; both ship.  Green for a square that will take
     * it, red for one that will refuse, so the answer arrives before the mouse comes up rather than as a message
     * afterwards.
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

        // Set directly rather than through applyBorder, so the record has to be dropped (OB-157).
        forgetBorderState(was);
    }

    /** The square currently wearing the drag mark, so it can be given its own border back. */
    private LayoutLabel captionDropTarget;

    /**
     * The border each tile is currently wearing, as a key rather than as an object (OB-157): borders are built fresh
     * every time and do not compare equal, so "is it already right?" cannot be asked of the component, and a short
     * string describing what was applied can be.  WEAK, so that a rebuilt grid takes its records with it: the keys are
     * labels that no longer exist once drawGrid has run, and holding them would keep a whole discarded grid alive.
     */
    private final java.util.Map<JLabel, String> borderState = new java.util.WeakHashMap<>();

    /**
     * Sets a tile's border, unless it is already wearing that one (OB-157): setBorder repaints whether or not anything
     * changed, and refreshSelectionBorders runs on every mouse-motion event of a selection drag, so a few hundred tiles
     * would be repainted per mouse move to change the outline on one or two.  The key has to describe everything the
     * border depends on: a resting border is a different border in the palette, in autonomy mode, and with the grey
     * grid off, and any of those can change while the same tiles stay on screen; a key that left one out would skip a
     * needed repaint and leave a stale outline behind, which is worse than the flicker.
     *
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
     * Forgets what a tile was wearing, for the paths that set a border without going through applyBorder (OB-157): the
     * caption drag mark being put back, and the drag grip, which must both say so, or the next refresh trusts a key
     * that no longer describes the tile and skips a repaint it needs.
     *
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
            // Never the grid's padding (OB-161): the grid is one row taller and one column wider than the diagram, and
            // that row and column are blank labels holding the GridBagLayout together (OB-055).  getValueAt returns
            // them for any coordinate one past the edge, so a group dragged onto the last row would have its landing
            // outline painted onto the padding, and clearBordersFromChildren skips spacers (the grid's own furniture,
            // not squares), so nothing would take it off again.  Asked of what the label IS, which is how the clearing
            // side asks it.
            if (label instanceof LayoutLabel && ((LayoutLabel) label).isSpacer()) return;

            boolean palette = this.getX((LayoutLabel) label) == -1;

            int width = palette ? NEW_COMPONENT_BORDER_WIDTH : COMPONENT_BORDER_WIDTH;

            // Sized the same as what this square was resting in, so hovering moves nothing.  Asked of the ROOM the
            // resting border takes, not of whether there is one (OB-056): once the autonomy editor has a grid that
            // paints without reserving, null is not the same question.  A palette tile rests in a line and keeps one;
            // anything resting in nothing, or in an overlay, gets an overlay.
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
            // ASKED ONCE FOR THE PASS: the grid setting is a preference, and on Windows each read is a registry round
            // trip that nothing caches, so asking per square made thousands of them on every square the pointer
            // entered.  The resting border is the same for every tile of one panel, and so is its key.
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

                    // Every TILE, asked of what it is rather than of whether it currently has a border: a label already
                    // put back to nothing must not be skipped on the way to a highlight and back, or the first hover
                    // would leave a line behind for good, and with the grid off the resting border is nothing.  Spacers
                    // excluded: they are the grid's own padding, not squares (OB-055), and the constructor never gives
                    // them a border.
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

            // And the PICKED squares.  Every hover clears the borders and redraws the blue one, so a selection would
            // vanish the instant the pointer moved on while Delete still preferred it: a user who could see no green
            // and pressed Delete to remove the tile under the cursor would delete a whole invisible row instead.
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
    /**
     * Whether shiftUp would do anything: true when there is a hovered square this can work from
     * (`LayoutEditorReshape.canShiftUp`).
     */
    public boolean canShiftUp()
    {
        return this.reshapeOps.canShiftUp();
    }

    /**
     * Whether the page has room to grow by this many rows and columns, the result still within MAX_SIZE
     * (`LayoutEditorReshape.roomToGrow`).
     */
    public boolean roomToGrow(int rows, int columns)
    {
        return this.reshapeOps.roomToGrow(rows, columns);
    }

    /**
     * Whether shiftLeft would do anything: true when there is a hovered square this can work from
     * (`LayoutEditorReshape.canShiftLeft`).
     */
    public boolean canShiftLeft()
    {
        return this.reshapeOps.canShiftLeft();
    }

    /**
     * Whether shiftDown would do anything: true when there is a hovered square this can work from
     * (`LayoutEditorReshape.canShiftDown`).
     */
    public boolean canShiftDown()
    {
        return this.reshapeOps.canShiftDown();
    }

    /**
     * Whether shiftRight would do anything: true when there is a hovered square this can work from
     * (`LayoutEditorReshape.canShiftRight`).
     */
    public boolean canShiftRight()
    {
        return this.reshapeOps.canShiftRight();
    }
    /**
     * Inserts a row or a column at the hovered square and pushes everything past it along
     * (`LayoutEditorReshape.shiftUp`).
     */
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

    /**
     * Writes the current width and height into the Diagram Size heading's tooltip: on the heading rather than on the
     * buttons, as the buttons say what they do and this says what they would be doing it to.  Re-read after every grow
     * and shrink, since a number written once at startup is wrong from the first press.
     */
    void showDiagramSize()
    {
        if (this.diagramSize == null) return;

        // The heading's own text as well, which the form points at the visibility key (two headings reading "Diagram
        // Size" and nothing saying Toggle Visibility).  Set here rather than in the form, which is generated.
        this.diagramSize.setText(I18n.t("layout.ui.sectionDiagramSize"));

        this.diagramSize.setToolTipText(I18n.f("layout.ui.tooltipDiagramSize",
            layout.getSx() + " x " + layout.getSy()));

        // AND THE TWO BUTTONS: `layout.ui.tooltipGrowDiagram` and `layout.ui.tooltipShrinkDiagram` exist in all eight
        // bundles, but the form sets `plusButton` to the empty string and `minusButton` to nothing at all, and the form
        // is generated, so the tooltips go here beside the heading.  "+" and "-" say almost nothing on their own, and
        // the shrink refuses if either the last column or the last row holds track, which is what its tooltip says.
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
     * Turns the diagram's text labels on, leaving them on if they already are (OB-174): `toggleText` flips, the wrong
     * verb for a caller that needs them ON, and a flip would turn the text OFF for somebody who already had it on.  The
     * autonomy editor calls this for a ticked Text Labels box (FR-133); the captions do not depend on it, the grid asks
     * the caption dropdown for those.  It does not touch the flag when nothing needs to change, so it costs no grid
     * rebuild in the ordinary case.
     */
    public void showTextLabels()
    {
        if (!this.layout.getEditHideText()) return;

        toggleText();
    }

    /**
     * Hides the writing on the diagram, idempotently: the other half of the pair (FR-061).  The station captions stay,
     * since the caption dropdown draws them whatever this switch says (OB-272).  The autonomy editor calls it for an
     * unticked Text Labels box (FR-133); a twin of `showTextLabels` rather than a call to `toggleText`, for the reason
     * given there.
     */
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

            // AND THE AUTONOMY PANEL'S TEXT LABELS BOX, which is the visible state of this switch in autonomy mode
            // (FR-133): the checkbox above is hidden there, so without this the diagram's writing would change under a
            // box still saying the other thing.  (Control+L does not reach here in autonomy mode: it steps the caption
            // dropdown instead.) After the rebuild.
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
                // If the method is already running, set the rerun flag and return, but ONLY ONCE IT HAS BEGUN: a
                // pending redraw that has not started reads the state as it then is, so a second request before it is
                // part of it, and queueing another would draw the same diagram twice.
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

                        // THE TRACK EDITOR'S OUTLINES DRAWN BACK, not only taken down - see drawTheOutlinesBack
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
    

    /**
     * Tells the setup that every square on this page has gone: one call for the page rather than one per square, for
     * the reason forgetBuiltOver gives at length (per square this rebuilds the graph twice and writes the whole setup
     * to disk, and a page is hundreds of squares).
     */
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

    // There is no `toggleCoordinates` or `showingCoordinates` (OB-179; Adam: "coordinates should always be on with the
    // grid, off without, and the menu option gone.").  The numbers are a property of the grid (see
    // `LayoutGrid.coordinatesVisible`, where that rule is written down), so turning them on and off is `setShowGrid`,
    // which redraws because the numbers live in a border whose insets change the size of the panel.

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
                
                // WHAT AUTONOMY KNEW ABOUT THIS PAGE GOES WITH IT: emptying the whole page must tell the setup, as
                // deleting one square does, or every station, name, length, facing, signal pairing, portal and
                // placement would stay keyed to squares that now hold nothing; a reconciling save would eventually
                // prune them, but the non-reconciling writes on the way out (the window captures the running layout and
                // saves without reconciling when it closes) commit that state to disk first.
                //
                // EMPTIED FIRST, THEN FORGOTTEN: forgetWholePage rebuilds the tile graph, which is traced from the
                // SQUARES, so forgetting first would rebuild from the diagram the next line is about to throw away and
                // nothing would rebuild afterwards: Clear Page would leave autonomy holding every tile it had just
                // deleted.
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
            // The outgoing grid lets go of the panel before the new one takes it: a grid hides itself until its tiles
            // have decoded and arms two timers to reveal it, both outliving a rebuild and holding this panel, so left
            // armed the old grid's grace timer would drop a spinner into the new one and the extra component push the
            // tiles along (a half-drawn row).
            if (grid != null) grid.discard();

            grid = new LayoutGrid(this.layout, size,
                this.ExtLayoutPanel,
                this,
                true, parent);

            // A NEW PANEL, so it is told whether a drag picks: picking goes on through a redraw (an edit, Undo, the
            // grid's key, a switch of mode) and the crosshair goes with the old panel
            this.showWhetherADragPicks();

            // One row of slack at the bottom of the scrollable area, and the gaps the layout will add.  The panel is
            // laid out to exactly the height of the grid, so the last row would sit flush against the edge of the
            // viewport, and anything that takes a few pixels (a horizontal scrollbar appearing, a border, a slightly
            // taller row of tiles in autonomy mode) would push it out of sight; a row rather than a fixed number of
            // pixels, because the tile size is a setting.  `FlowLayout` lays the diagram out as a row of `maxWidth + 2
            // * DIAGRAM_GAP`, and a CENTRE layout given a row wider than its container places it at a NEGATIVE x,
            // outside the panel where no scrolling can reach it (only on a narrow window); the horizontal slack is the
            // same thing as the row of slack, in the other direction.
            this.ExtLayoutPanel.setPreferredSize(new Dimension(
                grid.maxWidth + 2 * DIAGRAM_GAP + DIAGRAM_SLACK_COLUMNS * size,
                grid.maxHeight + size + 2 * DIAGRAM_GAP));

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
     * Sizes the window to the diagram it is showing, within what the screen can hold: the default when the user has
     * expressed no preference (OB-003).  There is one remembered window for the editor, so the fit runs on every
     * arrival, and CAPPED to the screen: a diagram wider than the screen must not produce a window with its right-hand
     * edge and scrollbar off the side.  The minimum is set here rather than beside the preferred size because pack()
     * honours it, and a floor taller than the screen would defeat the cap, hence the clamp on both.
     *
     * THE RULE FOR THE FLOOR, in both directions: at its smallest width and its smallest height the window shows every
     * control, at each diagram size and in each language (OB-342).  The HEIGHT is `EXTRA_MINIMUM_HEIGHT` over the
     * size's base.  The WIDTH is the diagram's 550 and the sidebar, growing with the size; the tools column and the
     * sidebar need about 380 pixels together (376 in German), so nothing of the window's own asks for more.  A control
     * whose words are data, like the status line under the findings (521 pixels in English, 655 in German), has no
     * minimum width and is cut with an ellipsis where the window is narrow (see `AutonomyEditorPanel.banner`).
     *
     * AT EVERY DISPLAY SCALE: the interface is scaled by the display (`TrainControlUI`'s `flatlaf.uiScale`, dpi / 96),
     * and most of the column is not scaled, so scaling the pixels does not do.  The window's own layout knows what it
     * needs at any scale and in any language, so the floor is never below its minimum height plus the window's own
     * chrome (the title bar and the borders, which the layout does not hold), and the fixed numbers stand where that is
     * less.  Still clamped to the screen: where the screen is shorter than the floor, the controls below that are out
     * of reach, as for any window.
     */
    private void sizeForDiagram()
    {
        // The sidebar takes width from the diagram unless it is asked for as well
        int sideways = sidebar == null ? 0 : sidebar.getPreferredSize().width;

        java.awt.Rectangle usable = usableScreen();

        this.setMinimumSize(new Dimension(
            // In proportion to the square, Small to Large - the Medium size between them (FR-122)
            Math.min(550 + sideways + Math.max(0, (this.size - 30) * 200 / 30), usable.width),
            Math.min(630 + EXTRA_MINIMUM_HEIGHT + Math.max(0, (this.size - 30) * 320 / 30), usable.height)));

        // Never SMALLER than the window already is (MT-096): there is one remembered entry and the fit runs on every
        // arrival, so it has to be able to leave a window alone as well as grow it, or switching from a big page to a
        // small one would shrink the window under somebody who had made it bigger on purpose.  A size you chose is a
        // floor rather than a suggestion.
        int wide = Math.max(getWidth(), grid.maxWidth + 210 + sideways);
        int high = Math.max(getHeight(), grid.maxHeight + 160);

        this.setPreferredSize(new Dimension(
            Math.min(wide, usable.width),
            Math.min(high, usable.height)));

        pack();

        // NEVER BELOW WHAT THE WINDOW'S OWN LAYOUT ASKS FOR (OB-342): the layout's minimum is what the column of tools
        // needs at this display scale, with the sidebar, in this language; the chrome is what the layout does not hold,
        // known now that pack() has given the window its peer.  The layout's minimum is a few pixels more than the
        // controls need: the margin.
        int asked = getContentPane().getMinimumSize().height + getHeight() - getContentPane().getHeight();

        if (asked > getMinimumSize().height)
        {
            setMinimumSize(new Dimension(getMinimumSize().width, Math.min(asked, usable.height)));
        }

        // Back on screen if the fit pushed it off the bottom or the right, which it can when the window was already
        // sitting near an edge: the size changed under a position chosen for the old one.
        int x = Math.max(usable.x, Math.min(getX(), usable.x + usable.width - getWidth()));
        int y = Math.max(usable.y, Math.min(getY(), usable.y + usable.height - getHeight()));

        if (x != getX() || y != getY()) setLocation(x, y);
    }

    /**
     * The window's title for the page it shows, in one mode or the other (MT-669), with a configuration beside it
     * (FR-127): the one LOADED for the track editor, and for the autonomy editor the one it EDITS, the setup's chosen
     * one, which is not loaded when its errors stop it loading or autonomy has been stopped.
     *
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

    /**
     * Sets the title again, for a configuration loaded, switched, renamed or unloaded while the window is open (FR-127).
     * Event thread only.
     */
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

            // Before the grid and before pack(), so the window is sized with the sidebar in it.  Here rather than in
            // the constructor because it asks which mode this window is in, decided by setAutonomyMode, which runs
            // between the constructor and this as render() queues its work rather than doing it.
            mountSidebar();

            drawGrid();

            // The mode's own title, which setAutonomyMode has decided by now (MT-669)
            setTitle(titleFor(isAutonomyMode()));

            // The index FIRST, because everything below asks what has been remembered.  ONE entry for the whole window,
            // not one per page (MT-095): there is one editor window, and it should be where you left it whatever page
            // it is showing.
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
                // A window whose size was remembered keeps the floor the form gave it, the one place the taller minimum
                // would otherwise not reach (the form is generated).  Plus the sidebar, once and not on top of a floor
                // that has it already: a window that has been through `sizeForDiagram` has the sidebar in its floor,
                // and adding it again would make the floor 150 pixels wider (OB-342).  So the floor is the diagram's
                // own 550 and the sidebar, or what it is already if that is wider.
                int sideways = sidebar == null ? 0 : sidebar.getPreferredSize().width;

                this.setMinimumSize(new Dimension(Math.max(getMinimumSize().width, 550 + sideways),
                    Math.max(getMinimumSize().height, 650 + EXTRA_MINIMUM_HEIGHT)));
            }

            saveWindowBounds();
            
            setVisible(true);

            // AND IN FRONT OF THE WINDOW THAT OPENED IT (OB-173; Adam: "when clicking edit, the autonomy editor appears
            // below other windows, so I might not even notice it's there... we managed this carefully in 2.7.x"):
            // `setVisible(true)` shows a window and does not decide where in the stack it lands.  The two calls
            // `TrainControlUI.showOpenEditor()` already makes to bring this window forward when the button is pressed a
            // second time, so the two doors behave alike.  No always-on-top trick: `comeToTheForeground` needs one
            // because start-up asks for the foreground from a process that does not have it; this runs from a click on
            // a window that does.
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
            // The Edit button was disabled before this window was asked for, and openLayoutEditor wraps the
            // CONSTRUCTION in a catch that hands it back, or autonomy setup is unreachable until the application is
            // restarted.  This body runs on a later event, outside that catch, so the same remedy is needed here.  AND
            // FOR AN ERROR TOO, given back and then thrown on, as the constructor's catch and openLayoutEditor's do:
            // past a catch of exceptions only, the hidden frame stays counted and the gate shut for the session.
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
     * Closes this page and opens another, at a given square.  Nothing is thrown away: an edit made here has already
     * gone into the shared session, and the window that opens is looking at the same session.  The question exists
     * because the window DISAPPEARS: work that has not been saved is easy to forget once the window it was done in has
     * gone, and a page change is the moment to be reminded.  So it asks rather than discarding, unlike confirmExit:
     * answering yes here loses nothing.
     *
     * @param tile the square to open on
     */
    public void jumpToSquare(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        if (tile == null) return;

        // The same question the sidebar asks, and the same three answers (OB-046): a "yes" that neither saved nor
        // discarded would leave the user asked about their unsaved work with nothing done either way.
        if (!settleUnsavedWork()) return;

        layout.setEdit(false);

        // COUNTED BEFORE THE WINDOW GOES, and the click with it: the user closed nothing
        final boolean clicked = squareClicked;

        parent.autonomyEditorClosedFromTheEditor(
            () -> parent.openCarryingTheClick(clicked, () -> parent.openAutonomyEditor(tile)));

        dispose();
    }

    /**
     * Whether this window may be left, asking about unsaved work first.  Closing only: switching page or mode asks its
     * own question (see leaveFor), which can offer to SAVE because the window is coming straight back, where closing
     * can only offer to throw the work away.  What counts as unsaved is the same test in both, and is the first line of
     * each.  In autonomy mode the diagram was never touched, so the undo stack is empty and the question that matters
     * is whether the setup has unsaved edits; answering yes discards them here, before the caller does anything, as
     * they live in the shared session and leaving them would mean the question was asked and its answer ignored.
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
     * The same save / discard / cancel question, asked by the main window before it exits: the rule is "one
     * save/discard/cancel question, asked wherever a page is left", and the biggest door out of this window is closing
     * the application (OB-070).  In autonomy mode a silent discard would be worse, since the exit capture saves the
     * setup, so work the user was about to Cancel could be COMMITTED on the way out.  Public so the window can ask; it
     * delegates rather than restating the rule, as a second copy of that dialog would be a second thing to keep in
     * step.
     *
     * @return true when it is all right to proceed - saved, discarded, or nothing to settle
     */
    public boolean maySettleBeforeExit()
    {
        // BACK AND IN FRONT FIRST (MT-703): its question about unsaved work is its own, and a minimised window hides
        // what it owns, so the exit would wait on a question nobody could see
        if ((getExtendedState() & java.awt.Frame.ICONIFIED) != 0)
        {
            setExtendedState(getExtendedState() & ~java.awt.Frame.ICONIFIED);
        }

        toFront();

        if (!settleUnsavedWork()) return false;

        // THE RUNNING LAYOUT HAS FOLLOWED A DISCARD BY NOW: Discard in autonomy mode restores the setup as the editor
        // opened it, and `discardAutonomyWork`, inside settleUnsavedWork, rebuilds the railway from it before it
        // returns, so the save on the way out folds a railway that no longer carries the discarded edit, and if the
        // exit is then refused, the setup and the railway agree, as after a Cancel.
        return true;
    }

    /**
     * Completes a track-mode Discard, once the exit is certain.  A track-diagram edit has two halves in two places: the
     * diagram is discarded by `layoutEditingComplete` re-reading the pages from disk; the autonomy setup those same
     * gestures wrote (dragging a captioned tile writes it per gesture) is put back by `undoAutonomyEdits`, the CALLER'S
     * job, as the sidebar switch and the editor's own X do, or answering Discard would leave the caption on the square
     * it had been dragged to.  It runs on `settledByDiscarding`, not on "settleUnsavedWork returned true", which a
     * clean editor returns too (that would rewind the setup to the editor-open snapshot and discard whatever autonomy
     * had done meanwhile), and it is called from the exit path beside the dispose, once everything that can say no has
     * said yes, not from `maySettleBeforeExit` (the FIRST thing the exit does, before the dialog about running trains,
     * which can still refuse): otherwise the rewind would happen, the application carry on, `autonomyAsOpened` be
     * consumed and the editor's own Cancel be left with nothing to put back.  Safe after a Save: saving nulls
     * `autonomyAsOpened`, and `settledByDiscarding` is false there anyway.
     */
    public void completeExitDiscard()
    {
        if (this.settledByDiscarding && !isAutonomyMode()) undoAutonomyEdits();
    }

    /**
     * Save, discard, or stay: asked once, wherever a page is being left with work on it.  Three answers, not two:
     * closing offers two because closing is final, but LEAVING a page is not (the user is coming straight back to the
     * same editor somewhere else), and a two-button "throw it away or stay here" makes them close the window, save, and
     * reopen it, which is what the sidebar exists to stop.  Save is the default because it is the answer that cannot
     * lose anything, and this appears on a gesture as small as clicking a tab.  Shared with the link jump (OB-046),
     * whose own YES/NO neither saved nor discarded, so the edits survived into the next window through the shared setup
     * (Adam: "settings should be saved or discarded before leaving.").
     *
     * @return whether the caller may go ahead; false means the user chose to stay, or the save or the discard failed
     * and said so
     */
    private boolean settleUnsavedWork()
    {
        boolean unsaved = isAutonomyMode() ? hasUnsavedAutonomyWork() : canUndo();

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
