package org.traincontrol.gui;

import java.awt.Color;
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
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.text.AbstractDocument;
import org.traincontrol.marklin.MarklinLayout;
import org.traincontrol.marklin.MarklinLayoutComponent;

/**
 * Work in progress - track diagram editor
 * @author Adam
 */
public class LayoutEditor extends PositionAwareJFrame
{
    public static enum tool {MOVE, COPY};
    
    private final TrainControlUI parent;
    private final int size;
    private final MarklinLayout layout;
    private LayoutGrid grid;
    
    private int lastX = -1;
    private int lastY = -1;
    private MarklinLayoutComponent lastComponent = null;
    private tool toolFlag = null;
    
    private int lastHoveredX = -1;
    private int lastHoveredY = -1;
    //private LayoutLabel lastHoveredLabel = null;
    
    // Default size of new layouts
    public static final int DEFAULT_NEW_SIZE_ROWS = 8;
    public static final int DEFAULT_NEW_SIZE_COLS = 10;

    /**
     * Popup window showing train layouts
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
            System.out.println(type.toString());
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
            System.out.println("ERROR");
            e.printStackTrace();
        }
        
        return null;
    }
    
    public void receiveKeyEvent(KeyEvent e, LayoutLabel label)
    {
                    System.out.println("paste");

        
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V)
        {
            if (this.hasToolFlag())
            {
                this.executeTool(label);
            }
        }
    }
    
    public void receiveMoveEvent(MouseEvent e, LayoutLabel label)
    {
        lastHoveredX = getX(label);
        lastHoveredY = getY(label);
     
        label.setBackground(Color.red);
        
        if (lastHoveredX != -1 && lastHoveredY != -1)
        {
            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                this.clearBordersFromChildren(this.grid.getContainer());
                this.highlightLabel(label);
            }));
        }
        
        // lastHoveredLabel = label;
    }
    
    public void receiveClickEvent(MouseEvent e, LayoutLabel label)
    {
        System.out.println(label);
                
        System.out.println(Arrays.toString(grid.getCoordinates(label)));
        
        // Child component
        if (getX(label) == -1 && getY(label) == -1)
        {
            System.out.println("INIT COPY");
            this.initCopy(label, label.getComponent(), false);
            this.highlightLabel(label);
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
            executeTool(label);
        }
    }
    
    /**
     * Executes the currently active tool
     * @param label 
     */
    public void executeTool(LayoutLabel label)
    {
        switch (this.toolFlag)
        {
            case COPY:
                
                execCopy(label, false);
                
                break;
                
            case MOVE:
                
                execCopy(label, true);
                
                break;
        }
        
        if (lastX != -1 || lastY != -1)
        {
            this.clearBordersFromChildren(this.newComponents);
            
            if (this.toolFlag == tool.MOVE)
            {
                this.toolFlag = null;
            }
        }
    }
    
    /**
     * Copies lastComponent on the clipboard to the location designated by destLabel
     * @param destLabel
     * @param move 
     */
    private void execCopy(LayoutLabel destLabel, boolean move)
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
            
            System.out.println(lastX);
            System.out.println(lastY);
            
            // Avoid clearing if we are placing new items
            if (lastX != -1 || lastY != -1)
            {
                this.clearBordersFromChildren(this.newComponents);
                
                if (move) resetClipboard();
            }
        }
        catch (IOException ex)
        {
            
        }
                
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            drawGrid();
        }));
    }
    
    /**
     * Resets the contents of the clipboard
     */
    private void resetClipboard()
    {
        this.lastX = -1;
        this.lastY = -1;
        this.lastComponent = null;
    }
    
    /**
     * Adds this label's location to the clipboard
     * @param label
     * @param component - the component at the label location, or one that's specified
     * @param move 
     */
    public void initCopy(LayoutLabel label, MarklinLayoutComponent component, boolean move)
    {
        this.lastX = getX(label);
        this.lastY = getY(label);
        
        if (component != null)
        {
            lastComponent = component;
        }
        else
        {
            this.lastComponent = layout.getComponent(lastX, lastY);
        }
        
        this.toolFlag = move ? tool.MOVE : tool.COPY;
        
        if (move)
        {
            delete(label);
        }
        
        this.clearBordersFromChildren(this.newComponents);
    }
    
    /**
     * Deletes this label from the layout
     * @param x 
     */
    public void delete(LayoutLabel x)
    {
        try
        {
            layout.addComponent(null, grid.getCoordinates(x)[0], grid.getCoordinates(x)[1]);
        }
        catch (IOException ex)
        {

        }
        
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            drawGrid();
        }));
    }
        
    /**
     * Rotates the specified label
     * @param label 
     */
    public void rotate(LayoutLabel label)
    {       
        MarklinLayoutComponent lc = layout.getComponent(getX(label), getY(label));
        
        if (lc != null)
        {       
            lc.rotate();

            try
            {
                layout.addComponent(lc, grid.getCoordinates(label)[0], grid.getCoordinates(label)[1]);
            }
            catch (IOException ex)
            {

            }

            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                drawGrid();
            }));
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
                    lc.setLabel(newText);
                }

            try
            {
                layout.addComponent(lc, grid.getCoordinates(label)[0], grid.getCoordinates(label)[1]);
            }
            catch (IOException ex)
            {

            }

            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                drawGrid();
            }));
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

                            lc.setLogicalAddress(Integer.parseInt(textField.getText()));
                        


                        layout.addComponent(lc, grid.getCoordinates(label)[0], grid.getCoordinates(label)[1]);
                }
            }
            catch (Exception ex)
            {

            }

            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                drawGrid();
            }));
        }
    }
    
    private void highlightLabel(JLabel label)
    {
        if (label != null)
        {
            label.setBorder(BorderFactory.createLineBorder(java.awt.Color.RED, 1));
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
        }
    }
    
    public void shiftDown()
    {
        try
        {
            if (lastHoveredY > -1)
            {
                layout.shiftDown(lastHoveredY);

                javax.swing.SwingUtilities.invokeLater(new Thread(() ->
                {
                    drawGrid();
                }));
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
        try
        {
            if (lastHoveredX > -1)
            {
                layout.shiftRight(lastHoveredX);

                javax.swing.SwingUtilities.invokeLater(new Thread(() ->
                {
                    drawGrid();
                }));
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
        try
        {
            layout.addRowsAndColumns(rows, cols);
            
            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                drawGrid();
            }));
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
            
            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                drawGrid();
            }));
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
    public void toggleAddresses()
    {
        try
        {
            this.layout.setEditShowAddress(!this.layout.getEditShowAddress());
            
            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                drawGrid();
            }));
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
                layout.clear();
                lastHoveredX = lastHoveredY = -1;

                javax.swing.SwingUtilities.invokeLater(new Thread(() ->
                {
                    drawGrid();
                }));
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
        //this.ExtLayoutPanel.removeAll();
        if (grid != null)
        {
            //grid.getContainer().removeAll();
        }
        
        if (this.layout.getSx() <= 1 && this.layout.getSy() <= 1)
        {
            this.addRowsAndColumns(DEFAULT_NEW_SIZE_ROWS, DEFAULT_NEW_SIZE_COLS);            
        }
        
        grid = new LayoutGrid(this.layout, size,
            this.ExtLayoutPanel, 
            this,
            true, parent);
                
        setTitle("Layout Editor: " + this.layout.getName() + this.parent.getWindowTitleString());

        // Scale the popup according to the size of the layout
        if (!this.isLoaded())
        {
            this.setPreferredSize(new Dimension(grid.maxWidth + 150, grid.maxHeight + 150));
            this.setMinimumSize(new Dimension(500, 500));
            pack();
        }
            
        grid.getContainer().revalidate();
        this.ExtLayoutPanel.revalidate();
        grid.getContainer().repaint();
        this.ExtLayoutPanel.repaint();
        
        // Remember window location for different layouts and sizes
        this.setWindowIndex(this.layout.getName()+ "_editor_" + this.getLayoutSize());
        
        // Only load location once
        if (!this.isLoaded())
        {
            loadWindowBounds();
        }
        
        saveWindowBounds();
    }
    
    public String getLayoutTitle()
    {
        return this.layout.getName();
    }
    
    public void render()
    {        
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            layout.setEdit();
            this.setAlwaysOnTop(parent.isAlwaysOnTop());
            drawGrid();

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
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
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

        jButton1.setFont(new java.awt.Font("Segoe UI", 1, 11)); // NOI18N
        jButton1.setText("Save Changes");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setFont(new java.awt.Font("Segoe UI", 1, 11)); // NOI18N
        jButton2.setText("Cancel");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
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
                    .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                        .addComponent(jButton1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton2))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 446, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        
        // Handle key shortcuts
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
        else if (evt.getKeyCode() == KeyEvent.VK_DELETE)
        {
            this.delete(getLastHoveredLabel());
        }
    }//GEN-LAST:event_formKeyPressed
    
    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        try
        {
            layout.saveChanges(null, false);
            parent.layoutEditingComplete();
            setVisible(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }        
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        dispose();
    }//GEN-LAST:event_jButton2ActionPerformed

    private void ExtLayoutPanelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ExtLayoutPanelMouseEntered
        clearBordersFromChildren(this.grid.getContainer());
    }//GEN-LAST:event_ExtLayoutPanelMouseEntered

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel newComponents;
    // End of variables declaration//GEN-END:variables
}
