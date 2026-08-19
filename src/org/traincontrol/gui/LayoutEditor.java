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
     * Distinct from the red of a copied tile and the blue of a hovered one, because all three can be
     * on screen at once and they mean different things.
     */
    private static final Color COMPONENT_BORDER_SELECTED_COLOR = new Color(0, 150, 60);

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
    Deque<java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey>> previousCaptions =
        new ConcurrentLinkedDeque<>();

    Deque<java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey>> previousCaptionsRedo =
        new ConcurrentLinkedDeque<>();

    /**
     * What the setup currently says about this page's captions.
     *
     * @return caption square to station square, or an empty map when there is no setup to ask
     */
    private java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey> captionSnapshot()
    {
        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        return autonomy == null
            ? new java.util.LinkedHashMap<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey>()
            : autonomy.captionsOnPage(layout.getName());
    }

    /**
     * Puts this page's captions back as a snapshot found them.
     *
     * @param captions a snapshot, or null to do nothing - a null means the stacks disagreed, and
     *        leaving the captions alone is a better answer than clearing them
     */
    private void restoreCaptions(java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey> captions)
    {
        if (captions == null) return;

        org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

        if (autonomy != null) autonomy.restoreCaptionsOnPage(layout.getName(), captions);
    }

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
        
        this.ExtLayoutPanel.setLayout(new FlowLayout());
        this.parent = ui;
        this.size = size;
        this.layout = l;
        
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
                if (this.hasToolFlag())
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
                });
            }
        }
        
        // lastHoveredLabel = label;
    }
    
    /**
     * The square a drag started on, so that a click can be told from a drag on release.
     */
    private LayoutLabel dragSource = null;

    public void beginDrag(MouseEvent e, LayoutLabel label)
    {
        // Dragging MOVES track.  In autonomy mode the user is deciding which way trains may run, not
        // rearranging their railway, and a drag that quietly relaid the diagram would be the worst kind
        // of accident: silent, and to the thing everything else is derived from.
        if (isAutonomyMode()) return;

        // Shift held: this is a box, not a move.  Recorded and otherwise ignored - nothing is picked
        // until the button comes up, so a shift-drag that changes its mind can simply be released
        // back where it started.
        if (label != null && e.isShiftDown() && getX(label) >= 0 && getY(label) >= 0)
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
            this.dragSource = label;
            this.groupDragging = true;

            ghostLabel = new JLabel(I18n.f("layout.ui.dragGroup", this.selection.size()));
            ghostLabel.setOpaque(true);
            ghostLabel.setBackground(Color.WHITE);
            ghostLabel.setBorder(new LineBorder(COMPONENT_BORDER_SELECTED_COLOR, 2));

            dragWindow = new JWindow();
            dragWindow.getContentPane().add(ghostLabel);
            dragWindow.pack();
            dragWindow.setVisible(false);

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

    public void updateDrag(MouseEvent e, LayoutLabel label)
    {
        if (isAutonomyMode()) return;

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

            // Released on the square it started on is a shift-CLICK, and receiveClickEvent toggles
            // that one.  Handling it here as well would toggle it twice, which is to say not at all.
            if (to == null || (getX(to) == anchorX && getY(to) == anchorY)) return;

            this.selection.addRectangle(anchorX, anchorY, getX(to), getY(to));

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
                ((javax.swing.GroupLayout) getContentPane().getLayout())
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
            if (getContentPane().getLayout() instanceof javax.swing.GroupLayout)
            {
                javax.swing.JPanel visibility = new javax.swing.JPanel();
                visibility.setLayout(new javax.swing.BoxLayout(visibility,
                    javax.swing.BoxLayout.Y_AXIS));
                visibility.setOpaque(false);

                ((javax.swing.GroupLayout) getContentPane().getLayout())
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

                ((javax.swing.GroupLayout) getContentPane().getLayout())
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

                pauseRepaint = true;

                try
                {   
                    // Clear existing tiles
                    for (LayoutLabel l : destColumn)
                    {
                        if (l.getComponent() != null) this.delete(l);
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
                            execCopy(destLabel, false);
                        }

                        // Tool will get reset
                        //this.toolFlag = tool.COPY;
                    }
                    
                    for (LayoutLabel l : sourceColumn)
                    {
                        if (isMove && l.getComponent() != null) this.delete(l);
                    }      
                }
                finally
                {
                    pauseRepaint = false;
                }
                
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

                pauseRepaint = true;

                try
                {
                    // Clear existing tiles
                    for (LayoutLabel l : destinationRow)
                    {
                        if (l.getComponent() != null) this.delete(l);
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
                            execCopy(destLabel, false);
                        }

                        //this.toolFlag = tool.COPY;
                    }
                    
                    for (LayoutLabel l : sourceRow)
                    {
                        if (isMove && l.getComponent() != null) this.delete(l);
                    }
                    
                }
                finally
                {
                    pauseRepaint = false;
                }
                
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

            // Whatever autonomy had written on that square goes with it.
            //
            // A caption is not part of the diagram - it belongs to the setup, keyed by the square
            // it sits on - so moving the tile underneath one used to leave it behind, pointing at
            // track that is no longer there.  On a layout being rearranged that is every label,
            // replaced by hand, which is most of the reason not to rearrange one.
            //
            // Only on a MOVE.  Copying a tile does not copy what was written about the square it
            // came from: two squares cannot both be where one station name is shown.
            if (move && lastX >= 0 && lastY >= 0)
            {
                org.traincontrol.automationui.AutonomySession autonomy =
                    parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveCaption(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), lastX, lastY),
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            layout.getName(), getX(destLabel), getY(destLabel)));
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
                // longer there.  A copy does NOT bring the source's captions with it - two squares
                // cannot both be where one station name is shown - so this only forgets.
                if (autonomy != null)
                {
                    autonomy.forgetCaptionsAt(new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), x, y));
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

            // Captions read first too, for exactly the reason the tiles are.
            //
            // moveCaption reads the live store and writes to it, so calling it per tile inside the
            // write loop below made a group that overlaps its own footprint eat itself: two captioned
            // squares side by side, dragged one to the right, and the first move overwrote the second
            // square's caption before the second move came to read it - so one caption travelled two
            // squares and the other was destroyed.  Dragging LEFT happened to work, which made it look
            // intermittent rather than wrong.
            java.util.List<org.traincontrol.automationui.TileGraph.TileKey> carriedCaptions =
                new java.util.ArrayList<>();

            for (org.traincontrol.base.TileSelection.At at : from)
            {
                carriedCaptions.add(autonomy == null ? null
                    : autonomy.getCaptionTarget(new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), at.getX(), at.getY())));
            }

            // Clear
            for (org.traincontrol.base.TileSelection.At at : from)
            {
                layout.addComponent(null, at.getX(), at.getY());

                // The caption goes with the tile, so the square it is leaving keeps nothing
                if (autonomy != null)
                {
                    autonomy.forgetCaptionsAt(new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), at.getX(), at.getY()));
                }
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

                // Written from what was READ, not moved from a store the previous iterations have
                // already changed.  A destination that carried a caption of its own loses it: the
                // square now holds different track, and a caption that outlived the tile it described
                // is the hazard the delete path exists to prevent.
                if (autonomy != null)
                {
                    org.traincontrol.automationui.TileGraph.TileKey landing =
                        new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), toX, toY);

                    autonomy.forgetCaptionsAt(landing);

                    if (carriedCaptions.get(i) != null)
                    {
                        autonomy.setCaption(landing, carriedCaptions.get(i));
                    }
                }
            }

            // The selection travels with the tiles, so a group can be nudged twice
            java.util.List<org.traincontrol.base.TileSelection.At> landed =
                this.selection.movedBy(dx, dy);

            this.selection.clear();

            for (org.traincontrol.base.TileSelection.At at : landed)
            {
                this.selection.add(at.getX(), at.getY());
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
                // just been replaced.  A fill does not carry captions with it - there is one tile
                // being copied and many squares receiving it, so there is nothing to move.
                if (autonomy != null)
                {
                    autonomy.forgetCaptionsAt(new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), at.getX(), at.getY()));
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
                org.traincontrol.automationui.AutonomySession autonomy = parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.forgetCaptionsAt(new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), getX(label), getY(label)));
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
            grid = new LayoutGrid(this.layout, size,
                this.ExtLayoutPanel,
                this,
                true, parent);

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
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> captionsBefore = captionSnapshot();

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
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey> captionsBefore = captionSnapshot();

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
            drawGrid();

            setTitle(
                I18n.f("app.ui.windowLayoutEditorTitle", this.layout.getName())
            );

            // Scale the popup according to the size of the layout
            if (!this.isLoaded())
            {
                this.setPreferredSize(new Dimension(grid.maxWidth + 210, grid.maxHeight + 160));
                this.setMinimumSize(new Dimension(
                        550 + (this.size == 60 ? 200 : 0), 
                        630 + (this.size == 60 ? 320 : 0))
                );
                pack();
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
     * If there are unsaved changes, checks with the user prior to closng the window
     */
    private void confirmExit()
    {
        // In autonomy mode the diagram was never touched, so the undo stack is empty and the question
        // that matters is whether the autonomy setup has unsaved edits.
        if (isAutonomyMode())
        {
            if (autonomyPanel.isDirty())
            {
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

                if (result != JOptionPane.YES_OPTION) return;

                // And actually throw them away.  Answering yes used to close the window and nothing
                // else: the edits stayed in the live session, kept being drawn on the diagram, and were
                // written out by the next save from anywhere - so the question was asked and its answer
                // ignored.
                String failed = autonomyPanel.discardEdits();

                if (failed != null)
                {
                    JOptionPane.showMessageDialog(this,
                        I18n.f("autosetup.ui.errorDiscardFailed", failed));

                    return;
                }
            }

            closeAutonomyMode();
            return;
        }

        if (canUndo())
        {
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

            if (result != JOptionPane.YES_OPTION)
            {
                return;
            }
        }
        
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            parent.layoutEditingComplete();
        });
        
        this.dispose();    
    }
    
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
        jLabel2 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMinimumSize(new java.awt.Dimension(300, 180));
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        ExtLayoutPanel.setBackground(new java.awt.Color(255, 255, 255));
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
            .addGap(0, 434, Short.MAX_VALUE)
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

        jLabel2.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(0, 0, 155));
        jLabel2.setText(bundle.getString("layout.ui.toggleVisibility")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 675, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(newComponents, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(saveButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cancelButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(showTextCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(showAddressCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSeparator1))
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
                        .addComponent(jLabel2)
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
                this.initCopy(getLastHoveredLabel(), null, true);
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
                // means: the picked squares, the copied group, and the armed tool alike
                this.clearSelection();
                this.resetClipboard();
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JPanel newComponents;
    private javax.swing.JButton saveButton;
    private javax.swing.JCheckBox showAddressCheckbox;
    private javax.swing.JCheckBox showTextCheckbox;
    // End of variables declaration//GEN-END:variables
}
