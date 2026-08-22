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
    private final LayoutDiagram layout;
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

        this.autonomyAsOpened = opened == null ? null : opened.snapshotSetup();
        
        // Mirror address preference
        this.showAddressCheckbox.setSelected(l.getShowAddress());
        
        this.setFocusable(true);
        this.requestFocusInWindow();
        
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

        // So the heading reads correctly before anything has been pressed
        showDiagramSize();
    }

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
   
    public void receiveMoveEvent(MouseEvent e, LayoutLabel label)
    {
        // hover previews what a diagram edit would place; in autonomy mode nothing is being placed
        if (isAutonomyMode()) return;

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
            }

            if (autonomyBanner != null)
            {
                this.jScrollPane1.setColumnHeaderView(null);
                autonomyBanner = null;
            }

            // put the Addresses box back where the form had it
            if (autonomyVisibility != null)
            {
                ((javax.swing.GroupLayout) formPane.getLayout())
                    .replace(autonomyVisibility, this.showAddressCheckbox);

                autonomyVisibility = null;
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
                visibility.add(autonomyPanel.getShowLengths());

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
                directionsLabel.setBorder(
                    javax.swing.BorderFactory.createEmptyBorder(6, 0, 2, 0));

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

                ((javax.swing.GroupLayout) formPane.getLayout())
                    .replace(this.jScrollPane1, stack);

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

                            execCopy(destLabel, false);
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

                            execCopy(destLabel, false);
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
     * Copies lastComponent on the clipboard to the location designated by destLabel
     * @param destLabel
     * @param move
     */
    synchronized private void execCopy(LayoutLabel destLabel, boolean move)
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
            if (!move)
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

                // Whatever was written about the square being written over is about track that is no
                // longer there.  A copy does NOT bring the source's settings with it - two squares
                // cannot both be one station - so this only forgets.
                //
                // ALL of it, not only the caption.  A paste over a set-up station used to leave the
                // designation, the name, the length, the facings, the arrival restrictions and the
                // link pairing sitting on a square whose sensor had just been replaced - and nothing
                // else finds those, because reconcile drops settings from squares that are EMPTY and
                // this one is occupied, just by something else.
                if (autonomy != null)
                {
                    autonomy.forgetTiles(java.util.Collections.singletonList(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), x, y)));

                    // And written down, as every other edit here is.  Left in memory only, a caption
                    // dropped by a paste reached disk only if something else happened to save later.
                    rememberAutonomy(autonomy);
                }
            }

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

                // Whatever was written about the square being written over is about track that has
                // just been replaced - all of it, see pasteSelection.  A fill carries nothing with it:
                // there is one tile being copied and many squares receiving it.
                if (autonomy != null)
                {
                    autonomy.forgetTiles(java.util.Collections.singletonList(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), at.getX(), at.getY())));

                    rememberAutonomy(autonomy);
                }
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

                if (autonomy != null)
                {
                    autonomy.forgetCaptionsAt(new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), getX(label), getY(label)));

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
    
    private void highlightLabel(JLabel label, Color color)
    {
        if (label != null)
        {
            label.setBorder(BorderFactory.createLineBorder(color, this.getX((LayoutLabel) label) == -1 ? NEW_COMPONENT_BORDER_WIDTH : COMPONENT_BORDER_WIDTH));
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
                    
                    // Don't reset components without a border, because they might be something else...
                    if (label.getBorder() != null)
                    {
                        label.setBorder(BorderFactory.createLineBorder(COMPONENT_BORDER_DEFAULT_COLOR, newComponents.equals(panel) ? NEW_COMPONENT_BORDER_WIDTH : 1));
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
     * Worth knowing, and the reason these do not appear in autonomy mode: everything the autonomy
     * setup holds about a page is keyed by SQUARE, and shifting the diagram moves the track without
     * moving those keys.  See the note on growEdges, which is why THAT one only ever grows at the
     * right and the bottom.
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

                restoreCaptions(this.previousCaptions.isEmpty() ? null : this.previousCaptions.pop());

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

                restoreCaptions(this.previousCaptionsRedo.isEmpty()
                    ? null : this.previousCaptionsRedo.pop());

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
                                
                this.refreshGrid();
            }
        }
        catch (IOException ex)
        {
            this.parent.getModel().log(ex);
        }
    }
    
    public void render()
    {        
        javax.swing.SwingUtilities.invokeLater(() ->
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

            // Scale the popup according to the size of the layout
            if (!this.isLoaded())
            {
                // The sidebar takes width from the diagram unless it is asked for as well
                int sideways = sidebar == null ? 0 : sidebar.getPreferredSize().width;

                this.setPreferredSize(
                    new Dimension(grid.maxWidth + 210 + sideways, grid.maxHeight + 160));
                this.setMinimumSize(new Dimension(
                        550 + sideways + (this.size == 60 ? 200 : 0),
                        630 + EXTRA_MINIMUM_HEIGHT + (this.size == 60 ? 320 : 0))
                );
                pack();
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

            // Remember window location for different layouts and sizes
            this.setWindowIndex(this.layout.getName() + "_editor_" + this.getLayoutSize());

            // Only load location once
            if (!this.isLoaded())
            {
                loadWindowBounds();
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

        if (isAutonomyMode() && autonomyPanel != null && autonomyPanel.isDirty())
        {
            int result = JOptionPane.showOptionDialog(
                this,
                I18n.t("autosetup.ui.confirmJumpWithUnsavedEdits"),
                I18n.t("layout.ui.dialogExitConfirmation"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[0]
            );

            if (result != JOptionPane.YES_OPTION) return;
        }

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
        boolean unsaved = isAutonomyMode() ? autonomyPanel.isDirty() : canUndo();

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
                return;
            }

            if (answer == 0 && !saveBeforeLeaving())
            {
                syncSidebar();
                return;
            }

            // Discarding the SETUP has to happen here, because the setup is shared: the window that
            // opens next is looking at the same session, so edits left in it would survive a discard.
            // Discarding the DIAGRAM happens by itself - layoutEditingComplete re-reads the pages from
            // disk, and undoAutonomyEdits below puts the setup back as it was found.
            if (answer == 1 && isAutonomyMode())
            {
                String failed = autonomyPanel.discardEdits();

                if (failed != null)
                {
                    JOptionPane.showMessageDialog(this,
                        I18n.f("autosetup.ui.errorDiscardFailed", failed));

                    syncSidebar();
                    return;
                }
            }
        }

        if (isAutonomyMode())
        {
            layout.setEdit(false);

            dispose();

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                parent.autonomyEditorClosed();

                // Remembered: picking a mode from the sidebar is the user saying which editor they
                // want, in the plainest way there is.
                parent.openLayoutEditor(page, autonomy, null, true);
            });

            return;
        }

        // Both halves of the edit, or neither - see confirmExit
        undoAutonomyEdits();

        javax.swing.SwingUtilities.invokeLater(() ->
            parent.layoutEditingComplete(() -> parent.openLayoutEditor(page, autonomy, null, true)));

        this.dispose();
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
        sidebar.setBorder(new javax.swing.border.EmptyBorder(8, 8, 8, 8));

        if (offersPages)
        {
            sidebar.add(heading(I18n.t("layout.ui.sidebarPages")));
            sidebar.add(buildPageControl(pages));
            sidebar.add(javax.swing.Box.createVerticalStrut(12));
        }

        sidebar.add(heading(I18n.t("layout.ui.sidebarMode")));
        sidebar.add(buildModeControl());

        sidebar.add(javax.swing.Box.createVerticalGlue());

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
     * Which page, as a column of tabs.
     *
     * The control type lives here and nowhere else - swap the body and the rest of the window does not
     * notice.  Toggle buttons in a group because they read as tabs down the side and stay readable at
     * eight pages; a list or a drop-down would work the same way and take the same three lines.
     */
    private javax.swing.JComponent buildPageControl(java.util.List<String> pages)
    {
        javax.swing.JPanel column = new javax.swing.JPanel();
        column.setLayout(new javax.swing.BoxLayout(column, javax.swing.BoxLayout.Y_AXIS));

        pageButtons = new java.util.LinkedHashMap<>();

        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

        for (final String page : pages)
        {
            javax.swing.JToggleButton tab = new javax.swing.JToggleButton(page);

            tab.setSelected(page.equals(layout.getName()));
            tab.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            tab.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            tab.setFocusable(false);
            tab.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, 26));

            tab.addActionListener(e ->
            {
                if (switching || page.equals(layout.getName()))
                {
                    syncSidebar();
                    return;
                }

                leaveFor(page, isAutonomyMode());
            });

            group.add(tab);
            column.add(tab);

            pageButtons.put(page, tab);
        }

        return column;
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
        javax.swing.JToggleButton tab = new javax.swing.JToggleButton(text);

        tab.setSelected(isAutonomyMode() == autonomy);
        tab.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        tab.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        tab.setFocusable(false);
        tab.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, 26));

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

    private javax.swing.JLabel heading(String text)
    {
        javax.swing.JLabel label = new javax.swing.JLabel(text);

        label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        label.setBorder(new javax.swing.border.EmptyBorder(0, 0, 4, 0));

        return label;
    }

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
            if (pageButtons != null)
            {
                for (java.util.Map.Entry<String, javax.swing.JToggleButton> tab
                    : pageButtons.entrySet())
                {
                    tab.getValue().setSelected(tab.getKey().equals(layout.getName()));
                }
            }

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

    private java.util.Map<String, javax.swing.JToggleButton> pageButtons;

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
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_D)
            {
                this.toggleAddresses();
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_L)
            {
                this.toggleText();
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
