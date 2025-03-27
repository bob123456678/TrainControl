package org.traincontrol.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.JOptionPane;
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
        
        // Initialize components we can place
        this.jPanel1.add(this.getLabel(MarklinLayoutComponent.componentType.SIGNAL));
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
    
    private LayoutLabel getLabel(MarklinLayoutComponent.componentType type)
    {
        try
        {
            return new LayoutLabel(new MarklinLayoutComponent(type,0,0,0,0,0,0), this, 30, parent, true);
        }
        catch (Exception e)
        {
            
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
        System.out.println("MOVE");
        lastHoveredX = getX(label);
        lastHoveredY = getY(label);
        // lastHoveredLabel = label;
    }
    
    public void receiveClickEvent(MouseEvent e, LayoutLabel label)
    {
        System.out.println(label);
                
        System.out.println(Arrays.toString(grid.getCoordinates(label)));
        
        if (getX(label) == -1 && getY(label) == -1)
        {
            System.out.println("INIT COPY");
            this.initCopy(label, label.getComponent(), false);
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
            this.toolFlag = null;
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

            layout.addComponent(newComponent, getX(destLabel), getY(destLabel));
            
            if (move)
            {
                layout.addComponent(null, lastX, lastY);
            }
            
            System.out.println(lastX);
            System.out.println(lastY);
            // Avoid clearing if we are placing new items
            if (lastX != -1 || lastY != -1)
            {
                resetClipboard();
            }
        }
        catch (IOException ex)
        {
            
        }
                
        drawGrid();
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
        
        drawGrid();
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

            drawGrid();
        }
    }
    
    public void addRowsAndColumns(int num)
    {
        try
        {
            layout.addRowsAndColumns(num);
            drawGrid();
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
    
    /**
     * Refreshes the layout
     */
    private void drawGrid()
    {
        this.ExtLayoutPanel.removeAll();

        grid = new LayoutGrid(this.layout, size,
            this.ExtLayoutPanel, 
            this,
            true, parent, true);
        
        setTitle(this.layout.getName() + this.parent.getWindowTitleString());

        // Scale the popup according to the size of the layout
        if (!this.isLoaded())
        {
            this.setPreferredSize(new Dimension(grid.maxWidth + 100, grid.maxHeight + 100));
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
    }
    
    public String getLayoutTitle()
    {
        return this.layout.getName();
    }
    
    public void render()
    {        
        this.setAlwaysOnTop(parent.isAlwaysOnTop());
                      
        drawGrid();
             
        setVisible(true);
        
        // Hide the window on close so that LayoutLabels know they can be deleted
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                e.getComponent().setVisible(false);
            }
        });
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
        jPanel1 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMinimumSize(new java.awt.Dimension(150, 150));
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        ExtLayoutPanel.setBackground(new java.awt.Color(255, 255, 255));

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

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 182, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 675, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
    }//GEN-LAST:event_formKeyPressed
    
    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        try
        {
            layout.saveChanges();
            parent.layoutEditingComplete();
            setVisible(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }        
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        setVisible(false);
    }//GEN-LAST:event_jButton2ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
