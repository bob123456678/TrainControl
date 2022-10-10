/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import javax.swing.JOptionPane;
import model.ViewListener;

/**
 *
 * @author Adam
 */
public final class LocomotiveSelector extends javax.swing.JFrame {

    private final ViewListener model;
    private final TrainControlUI parent;
    
    // The padding beween locomotive buttons
    private static final int PADDING = 6;
    
    // How quickly we scroll through the window
    private static final int SCROLL_SPEED = 25;
        
    /**
     * Creates new form LocomotiveSelector
     * @param model
     * @param ui
     */
    public LocomotiveSelector(ViewListener model, TrainControlUI ui)
    {
        this.model = model;
        this.parent = ui;

        initComponents();
    }
    
    public void init()
    {
        // For some reason, the color set in the form gets ignored
        getContentPane().setBackground(new Color(238,238,238));
        
        this.setAlwaysOnTop(true);
        pack();
        
        refreshLocSelectorList();
    }

    public synchronized void refreshLocSelectorList()
    {
        new Thread(() -> {

            this.MainLocList.removeAll();

            this.MainLocList.setLayout(new FlowLayout(FlowLayout.LEFT, PADDING, PADDING));

            for (String s : model.getLocList())
            {
                LocomotiveSelectorItem loc = new LocomotiveSelectorItem(model.getLocByName(s), parent);

                this.MainLocList.add(loc);
                loc.setVisible(true);
            }

            this.LocFilterBox.setText("");
            filterLocList();
            
        }).start();
    }
    
    private void filterLocList()
    {
        String filter = this.LocFilterBox.getText().toLowerCase();
        
        for (Component b : this.MainLocList.getComponents())
        {
            LocomotiveSelectorItem bb = (LocomotiveSelectorItem) b;
            if ("".equals(filter) || bb.getText().toLowerCase().contains(filter))
            {
                b.setVisible(true);
            }
            else
            {
                b.setVisible(false);
            }
        }
        
        updateScrollArea();
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        locListLabel = new javax.swing.JLabel();
        LocFilterBox = new javax.swing.JTextField();
        renameLabel = new javax.swing.JLabel();
        LocScroller = new javax.swing.JScrollPane();
        MainLocList = new javax.swing.JPanel();
        SyncWithCS = new javax.swing.JButton();
        closeOnLocSel = new javax.swing.JCheckBox();

        setTitle("Locomotive Selector");
        setAutoRequestFocus(false);
        setBackground(new java.awt.Color(238, 238, 238));
        setForeground(new java.awt.Color(238, 238, 238));
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMaximumSize(new java.awt.Dimension(2000, 4000));
        setMinimumSize(new java.awt.Dimension(800, 670));
        setPreferredSize(new java.awt.Dimension(800, 670));
        addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                formFocusGained(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });
        addWindowStateListener(new java.awt.event.WindowStateListener() {
            public void windowStateChanged(java.awt.event.WindowEvent evt) {
                formWindowStateChanged(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        locListLabel.setForeground(new java.awt.Color(0, 0, 155));
        locListLabel.setText("Assign Locomotive to Active Button:");

        LocFilterBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LocFilterBoxActionPerformed(evt);
            }
        });
        LocFilterBox.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                LocFilterBoxKeyReleased(evt);
            }
            public void keyTyped(java.awt.event.KeyEvent evt) {
                LocFilterBoxKeyTyped(evt);
            }
        });

        renameLabel.setForeground(new java.awt.Color(0, 0, 155));
        renameLabel.setText("Filter List:");

        LocScroller.setHorizontalScrollBar(null);
        LocScroller.setMaximumSize(null);
        LocScroller.setMinimumSize(new java.awt.Dimension(670, 500));
        LocScroller.setPreferredSize(new java.awt.Dimension(670, 1000));

        MainLocList.setBackground(new java.awt.Color(238, 238, 238));
        MainLocList.setMaximumSize(null);
        MainLocList.setMinimumSize(new java.awt.Dimension(670, 600));
        MainLocList.setPreferredSize(new java.awt.Dimension(670, 600));
        MainLocList.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                MainLocListComponentResized(evt);
            }
        });
        MainLocList.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                MainLocListKeyPressed(evt);
            }
        });

        javax.swing.GroupLayout MainLocListLayout = new javax.swing.GroupLayout(MainLocList);
        MainLocList.setLayout(MainLocListLayout);
        MainLocListLayout.setHorizontalGroup(
            MainLocListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 742, Short.MAX_VALUE)
        );
        MainLocListLayout.setVerticalGroup(
            MainLocListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 600, Short.MAX_VALUE)
        );

        LocScroller.setViewportView(MainLocList);

        SyncWithCS.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SyncWithCS.setText("Sync with Central Station DB");
        SyncWithCS.setFocusable(false);
        SyncWithCS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SyncWithCSActionPerformed(evt);
            }
        });

        closeOnLocSel.setSelected(true);
        closeOnLocSel.setText("Close Window on Assignment");
        closeOnLocSel.setFocusPainted(false);
        closeOnLocSel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeOnLocSelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(LocScroller, javax.swing.GroupLayout.DEFAULT_SIZE, 770, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(locListLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(closeOnLocSel))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(renameLabel)
                        .addGap(15, 15, 15)
                        .addComponent(LocFilterBox, javax.swing.GroupLayout.PREFERRED_SIZE, 232, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(SyncWithCS)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locListLabel)
                    .addComponent(closeOnLocSel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(LocScroller, javax.swing.GroupLayout.DEFAULT_SIZE, 573, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(LocFilterBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(renameLabel)
                    .addComponent(SyncWithCS))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        parent.childWindowKeyEvent(evt);
    }//GEN-LAST:event_formKeyPressed

    private void MainLocListKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_MainLocListKeyPressed
        parent.childWindowKeyEvent(evt);
    }//GEN-LAST:event_MainLocListKeyPressed

    private void LocFilterBoxKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_LocFilterBoxKeyReleased
        filterLocList();
    }//GEN-LAST:event_LocFilterBoxKeyReleased

    private void LocFilterBoxKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_LocFilterBoxKeyTyped
        filterLocList();
    }//GEN-LAST:event_LocFilterBoxKeyTyped

    private void LocFilterBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LocFilterBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_LocFilterBoxActionPerformed

    private void MainLocListComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_MainLocListComponentResized
        
       
    }//GEN-LAST:event_MainLocListComponentResized

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        updateScrollArea();
    }//GEN-LAST:event_formComponentResized

    private void SyncWithCSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SyncWithCSActionPerformed

        new Thread(() -> {
            Integer r = this.model.syncWithCS2();
            this.refreshLocSelectorList();

            JOptionPane.showMessageDialog(this, "Sync complete.  Items added: " + r.toString());
        }).start();

    }//GEN-LAST:event_SyncWithCSActionPerformed

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        updateScrollArea();
    }//GEN-LAST:event_formComponentShown

    private void closeOnLocSelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeOnLocSelActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_closeOnLocSelActionPerformed

    private void formWindowStateChanged(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowStateChanged
        updateScrollArea();
    }//GEN-LAST:event_formWindowStateChanged

    private void formFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_formFocusGained
        updateScrollArea();
    }//GEN-LAST:event_formFocusGained

    public boolean doCloseWindow()
    {
        return this.closeOnLocSel.isSelected();
    }
    
    public synchronized void updateScrollArea()
    {
        try
        {
            if (this.MainLocList.getComponentCount() > 0 && this.MainLocList.getComponent(0).getWidth() > 0)
            {            
                Integer cols = (this.LocScroller.getWidth() + PADDING) / this.MainLocList.getComponent(0).getWidth();
                
                Integer itemH = this.MainLocList.getComponent(0).getHeight();
                Integer totalItems = 0;
                
                for (Component c: this.MainLocList.getComponents())
                {
                    if (c.isVisible())
                    {
                        totalItems += 1;
                    }
                }
                
                Integer rows = (int) Math.ceil((double) totalItems / (double) cols) ;

                this.MainLocList.setPreferredSize(new Dimension(this.getWidth(), ( rows * ( itemH + PADDING) ) + (int) (itemH * 1.5) ));
                     
                // This determines the scolling speed
                if (this.LocScroller.getVerticalScrollBar() != null)
                {
                    this.LocScroller.getVerticalScrollBar().setUnitIncrement(SCROLL_SPEED);
                    this.LocScroller.repaint();
                }
            }  
        }
        catch (Exception e)
        {
            // System.out.println(e.toString());
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField LocFilterBox;
    private javax.swing.JScrollPane LocScroller;
    private javax.swing.JPanel MainLocList;
    private javax.swing.JButton SyncWithCS;
    private javax.swing.JCheckBox closeOnLocSel;
    private javax.swing.JLabel locListLabel;
    private javax.swing.JLabel renameLabel;
    // End of variables declaration//GEN-END:variables
}
