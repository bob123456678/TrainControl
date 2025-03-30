package org.traincontrol.gui;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import org.traincontrol.marklin.MarklinLayout;
import org.traincontrol.marklin.MarklinLayoutComponent;

/**
 * Work in progress - track diagram editor
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
    private final MarklinLayout layout;
    private LayoutGrid grid;
    
    private int lastX = -1;
    private int lastY = -1;
    private MarklinLayoutComponent lastComponent = null;
    private tool toolFlag = null;
    private bulk bulkFlag = null;
    
    private int lastHoveredX = -1;
    private int lastHoveredY = -1;
    //private LayoutLabel lastHoveredLabel = null;
    
    // Default size of new layouts
    public static final int DEFAULT_NEW_SIZE_ROWS = 16;
    public static final int DEFAULT_NEW_SIZE_COLS = 21;
    
    // When true, the diagram does not get repainted, i.e. during bulk operations
    private boolean pauseRepaint = false;
    
    // Undo history
    Deque<List<MarklinLayoutComponent>> previousLayoutComponents = new ArrayDeque<>();
    public static final int MAX_UNDO_HISTORY = 20;

    /**
     * Popup window showing editable train layouts
     * @param l reference to the layout
     * @param size size of each tile, in pixels
     * @param ui
     * @param pageIndex
     */
    public LayoutEditor(MarklinLayout l, int size, TrainControlUI ui, int pageIndex)
    {
        initComponents();
        
        this.ExtLayoutPanel.setLayout(new FlowLayout());
        this.parent = ui;
        this.size = size;
        this.layout = l;
        
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
        gbc.insets = new java.awt.Insets(2, 2, 2, 2); // Top, left, bottom, right padding

        int cols = 3;
        
        // Initialize components we can place
        for (MarklinLayoutComponent.componentType type : MarklinLayoutComponent.componentType.values())
        {
            this.newComponents.add(this.getLabel(type, "T"), gbc);

            // Move to the next grid position
            gbc.gridx++;
            if (gbc.gridx >= cols)
            {
                gbc.gridx = 0;
                gbc.gridy++;
            }
        }
    }
    
    public boolean hasToolFlag()
    {
        return this.toolFlag != null;
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
    private LayoutLabel getLabel(MarklinLayoutComponent.componentType type, String defaultText)
    {
        try
        {
            MarklinLayoutComponent component = new MarklinLayoutComponent(type,0,0,0,0,0,0);
            
            if (type == MarklinLayoutComponent.componentType.TEXT)
            {
                component.setLabel(defaultText);   
            }
            
            LayoutLabel newLabel = new LayoutLabel(component, this, this.size, parent, true);
            
            // We need to add the text back on top of the icon
            if (type == MarklinLayoutComponent.componentType.TEXT)
            {
                newLabel.setText(defaultText);
            
                newLabel.setForeground(Color.black);
                newLabel.setFont(new Font("Sans Serif", Font.PLAIN, this.size / 2));
                newLabel.setVerticalTextPosition(JLabel.CENTER); 
                newLabel.setHorizontalTextPosition(JLabel.CENTER);
            }
            
            newLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
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
                    if (lineBorder.getLineColor().equals(Color.BLUE))
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
        lastHoveredX = getX(label);
        lastHoveredY = getY(label);
     
        if (label != null)
        {
            label.setBackground(Color.red);
            
            if (lastHoveredX == -1)
            {
                label.setToolTipText("Click to start placing a new \"" + label.getComponent().getType().toString().toLowerCase() + "\" tile");
            }
            else
            {
                String toolTipText = "Right-click for options";

                if (this.hasToolFlag())
                {
                    label.setToolTipText("Click to paste / " + toolTipText);
                }
                else if (this.layout.getComponent(lastHoveredX, lastHoveredY) != null)
                {
                    label.setToolTipText("Click to cut / " + toolTipText);     
                }
                else
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

                javax.swing.SwingUtilities.invokeLater(new Thread(() ->
                {
                    this.clearBordersFromChildren(this.grid.getContainer());
                    this.highlightLabel(label, java.awt.Color.RED);
                }));
            }
        }
        
        // lastHoveredLabel = label;
    }
    
    public void receiveClickEvent(MouseEvent e, LayoutLabel label)
    {
        // New label to place
        if (getX(label) == -1 && getY(label) == -1)
        {
            this.initCopy(label, label.getComponent(), false);
            this.highlightLabel(label, java.awt.Color.BLUE);
            return;
        }
        
        MarklinLayoutComponent lc = layout.getComponent(getX(label), getY(label));
        
        if (e.getButton() == MouseEvent.BUTTON3)
        {        
            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                LayoutEditorRightclickMenu menu = new LayoutEditorRightclickMenu(this, parent, label, lc);

                menu.show(e.getComponent(), e.getX(), e.getY());      
            }));
        }
        else 
        {
            if (this.hasToolFlag())
            {
                executeTool(label);
            }
            else
            {
                // Click to cut
                if (label != null && label.getComponent() != null)
                {
                    this.initCopy(label, null, true);
                }
            }
        }
    }
    
    public void setBulkRow()
    {
        this.bulkFlag = bulk.ROW;
    }
    
    public void setBulkColumn()
    {
        this.bulkFlag = bulk.COL;
    }
    
    /**
     * Executes the currently active tool
     * @param label 
     */
    synchronized public void executeTool(LayoutLabel label)
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

                    //this.toolFlag = tool.COPY;
                    this.bulkFlag = null;
                }
                
                for (LayoutLabel l : sourceColumn)
                {
                    if (isMove && l.getComponent() != null) this.delete(l);
                }
                
                pauseRepaint = false;
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
                
                pauseRepaint = false;
                this.resetClipboard(); // this will only allow us to copy the row/col once.  if we don't want to do this, we need to manually set toolFlag and bulkFlag here, as deletion resets the flag
                refreshGrid();
                
                // TODO - defer redraw
            }
        }
        else
        {
            if (toolFlag == tool.COPY)
            {
                execCopy(label, false);

            }
            else if (toolFlag == tool.MOVE)
            {
                execCopy(label, true);
            }
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
            MarklinLayoutComponent newComponent = new MarklinLayoutComponent(lastComponent);
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
    
    public MarklinLayout getMarklinLayout()
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
        this.bulkFlag = null;
        this.clearBordersFromChildren(this.newComponents);
    }
    
    /**
     * Adds this label's location to the clipboard
     * @param label
     * @param component - the component at the label location, or one that's specified
     * @param move 
     */
    synchronized public void initCopy(LayoutLabel label, MarklinLayoutComponent component, boolean move)
    {
        this.lastX = getX(label);
        this.lastY = getY(label);
        this.bulkFlag = null;
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
        
        // For Blue highlight
        this.clearBordersFromChildren(this.grid.getContainer());
        
        // Delete after pasting instead
        /* if (move)
        {
            delete(label);
        }*/
        
        this.clearBordersFromChildren(this.newComponents);
    }
    
    /**
     * Deletes this label from the layout
     * @param x 
     */
    synchronized public void delete(LayoutLabel x)
    {
        try
        {
            if (!this.pauseRepaint)
            {
                this.snapshotLayout();
            }
            
            layout.addComponent(null, grid.getCoordinates(x)[0], grid.getCoordinates(x)[1]);
            this.resetClipboard();
        }
        catch (IOException ex)
        {

        }
        
        refreshGrid();
    }
        
    /**
     * Rotates the specified label
     * @param label 
     */
    synchronized public void rotate(LayoutLabel label)
    {       
        MarklinLayoutComponent lc = layout.getComponent(getX(label), getY(label));
        
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
        MarklinLayoutComponent lc = layout.getComponent(getX(label), getY(label));
                    
        if (lc != null)
        {       
            String newText = (String) javax.swing.JOptionPane.showInputDialog(
                this, 
                "Enter new label text:", 
                "Edit Label", 
                javax.swing.JOptionPane.PLAIN_MESSAGE,
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
            }
            catch (IOException ex)
            {

            }

            refreshGrid();
        }
    }
    
     /**
     * Changes the address
     * @param label 
     */
    public void editAddress(LayoutLabel label)
    {               
        MarklinLayoutComponent lc = layout.getComponent(getX(label), getY(label));
                    
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

                // Display the input dialog
                int result = JOptionPane.showConfirmDialog(
                        null,
                        textField,
                        "Enter new address:",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );
        
                // Process the input when OK is clicked
                if (result == JOptionPane.OK_OPTION)
                {
                    this.snapshotLayout();

                    lc.setLogicalAddress(Integer.parseInt(textField.getText()));
                    layout.addComponent(lc, grid.getCoordinates(label)[0], grid.getCoordinates(label)[1]);
                }
            }
            catch (Exception ex)
            {

            }

            refreshGrid();
        }
    }
    
    private void highlightLabel(JLabel label, Color color)
    {
        if (label != null)
        {
            label.setBorder(BorderFactory.createLineBorder(color, 1));
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
                        label.setBorder(BorderFactory.createLineBorder(java.awt.Color.LIGHT_GRAY, 1));
                    }
                }
            }
            
            // Highlight copied tile in blue
            if (this.hasToolFlag() && layout.getComponent(lastX, lastY) != null)
            {
                this.highlightLabel(this.grid.getValueAt(lastX, lastY), Color.BLUE);
            }
        }
    }
    
    public void shiftDown()
    {
        this.snapshotLayout();
        
        try
        {
            if (lastHoveredY > -1)
            {
                layout.shiftDown(lastHoveredY);

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
                layout.shiftRight(lastHoveredX);

                refreshGrid();
            }
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
            JOptionPane.showMessageDialog(this, "No more than " + MAX_SIZE + " rows or columns are allowed.");
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
        // Check if we are doing bulk actions
        if (!pauseRepaint)
        {
            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                drawGrid();
                
                // Makes sure the right things get highlighted
                this.clearBordersFromChildren(this.grid.getContainer());
            }));
        }
    }
    
    /**
     * Toggles the display of text
     */
    public void toggleAddresses()
    {
        try
        {
            this.layout.setEditShowAddress(!this.layout.getEditShowAddress());
            
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
            int confirmation = JOptionPane.showConfirmDialog(
                this,
                "This will delete everything on the track diagram. Are you sure you want to proceed?",
                "Please Confirm",
                JOptionPane.YES_NO_OPTION
            );

            if (confirmation == JOptionPane.YES_OPTION)
            {
                // Reset all history - if we want this to work, the restore method needs to increase diagram size correctly
                this.previousLayoutComponents.clear();
                
                layout.clear();
                this.resetClipboard();
                // lastHoveredX = lastHoveredY = -1;

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
        
        grid = new LayoutGrid(this.layout, size,
            this.ExtLayoutPanel, 
            this,
            true, parent);
                
        grid.getContainer().revalidate();
        this.ExtLayoutPanel.revalidate();
        grid.getContainer().repaint();
        this.ExtLayoutPanel.repaint();
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
     * Saves a previous version of the layout
     */
    synchronized private void snapshotLayout()
    {
        List<MarklinLayoutComponent> history = new ArrayList<>();
        
        for (MarklinLayoutComponent lc : this.layout.getAll())
        {
            try
            {
                history.add(new MarklinLayoutComponent(lc));
            }
            catch (IOException ex)
            {
                this.parent.getModel().log(ex);
            }
        }
        
        // Enforce size limit
        if (this.previousLayoutComponents.size() >= LayoutEditor.MAX_UNDO_HISTORY)
        {
            this.previousLayoutComponents.removeLast();
        }
        
        this.previousLayoutComponents.push(history);
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
                List<MarklinLayoutComponent> history = this.previousLayoutComponents.pop();
                
                // Delete all existing components
                for (MarklinLayoutComponent lc : this.layout.getAll())
                {
                    layout.addComponent(null, lc.getX(), lc.getY());
                }

                // Placed previous components
                for (MarklinLayoutComponent lc : history)
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
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            layout.setEdit();
            this.setAlwaysOnTop(parent.isAlwaysOnTop());
            drawGrid();

            setTitle("Layout Editor: " + this.layout.getName() + this.parent.getWindowTitleString());

            // Scale the popup according to the size of the layout
            if (!this.isLoaded())
            {
                this.setPreferredSize(new Dimension(grid.maxWidth + 210, grid.maxHeight + 130));
                this.setMinimumSize(new Dimension(550, 550));
                pack();
            }

            // Remember window location for different layouts and sizes
            this.setWindowIndex(this.layout.getName()+ "_editor_" + this.getLayoutSize());

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
                    //e.getComponent().setVisible(false);
                }
            });            
        }));
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

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
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
            .addGap(0, 431, Short.MAX_VALUE)
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
        saveButton.setText("Save Changes");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        cancelButton.setFont(new java.awt.Font("Segoe UI", 1, 11)); // NOI18N
        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(0, 0, 155));
        jLabel1.setText("New Components");

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
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                        .addComponent(saveButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 446, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        
        // Handle key shortcuts
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_V)
            {
                if (this.hasToolFlag() && getLastHoveredLabel() != null)
                {
                    this.executeTool(getLastHoveredLabel());
                }
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X)
            {
                this.initCopy(getLastHoveredLabel(), null, true);

            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_C)
            {
                this.initCopy(getLastHoveredLabel(), null, false);
            }
            else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_R)
            {
                this.rotate(getLastHoveredLabel());
            }
            else if (evt.isShiftDown() && evt.getKeyCode() == KeyEvent.VK_C)
            {          
                this.setBulkColumn();
                this.executeTool(getLastHoveredLabel());
            }
            else if (evt.isShiftDown() && evt.getKeyCode() == KeyEvent.VK_R)
            {
                this.setBulkRow();
                this.executeTool(getLastHoveredLabel());
            }
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
                this.addRowsAndColumns(1, 1);
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
            else if (evt.getKeyCode() == KeyEvent.VK_DELETE)
            {
                this.delete(getLastHoveredLabel());
            }
            else if (evt.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                this.resetClipboard();
            }
        }));
    }//GEN-LAST:event_formKeyPressed
    
    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        try
        {
            layout.saveChanges(null, false);
            parent.layoutEditingComplete();
            dispose();
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }        
    }//GEN-LAST:event_saveButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        
        parent.layoutEditingComplete();
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void ExtLayoutPanelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ExtLayoutPanelMouseEntered
        clearBordersFromChildren(this.grid.getContainer());
    }//GEN-LAST:event_ExtLayoutPanelMouseEntered

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel newComponents;
    private javax.swing.JButton saveButton;
    // End of variables declaration//GEN-END:variables
}
