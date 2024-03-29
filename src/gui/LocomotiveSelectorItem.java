/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Component;
import java.awt.Font;
import java.io.IOException;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import marklin.MarklinLocomotive;

/**
 *
 * @author Adam
 */
public final class LocomotiveSelectorItem extends javax.swing.JPanel {
    
    private final MarklinLocomotive loc;
    private TrainControlUI tcui;
    private final JPanel mainLocList;
    
    /**
     * Creates new form LocomotiveSelectorItem
     * @param loc
     * @param ui
     * @param mainLocList
     */
    public LocomotiveSelectorItem(MarklinLocomotive loc, TrainControlUI ui, JPanel mainLocList)
    {
        initComponents();
        
        this.loc = loc;
        this.tcui = ui;
        this.mainLocList = mainLocList;
                
        this.LocLabel.setText(loc.getName());
        
        this.refreshToolTip();
                
        // Set icon
        if (TrainControlUI.LOAD_IMAGES && loc.getImageURL() != null)
        {
            this.tcui.getImageLoader().submit(new Thread(() ->
            {
                try 
                {
                    ImageIcon ic = new javax.swing.ImageIcon(
                        tcui.getLocImage(loc.getImageURL(), 135)
                    );
                    
                    locIcon.setIcon(ic);      
                    locIcon.setText("");
                }
                catch (IOException e)
                {
                    tcui.getModel().log("Failed to load image: " + loc.getImageURL());
                    locIcon.setIcon(null);
                }
            }));
        }
        else
        {
            locIcon.setIcon(null);
        }
    }
    
    /**
     * Makes the tooltip indicate current keyboard mappings
     */
    public void refreshToolTip()
    {
        // Set tooltip and label color
        List<String> mappings = this.tcui.getAllLocButtonMappings(loc);
        if (!mappings.isEmpty())
        {
            locIcon.setToolTipText("Mapped to: " + String.join(", ", mappings));
            
            Font font = new Font("Tahoma", Font.BOLD, 17); 
            LocLabel.setFont(font);
            // LocLabel.setForeground(Color.GRAY);
        }
        else
        {
            locIcon.setToolTipText(null);
            
            Font font = new Font("Tahoma", Font.PLAIN, 18); 
            LocLabel.setFont(font);
            // LocLabel.setForeground(Color.BLACK);
        }
        
        LocLabel.setToolTipText(this.locIcon.getToolTipText());
        this.setToolTipText(this.locIcon.getToolTipText());
    }
    
    public String getText()
    {
        return this.loc.getName();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        locIcon = new javax.swing.JLabel();
        LocLabel = new javax.swing.JLabel();

        setBackground(new java.awt.Color(255, 255, 255));
        setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        setMaximumSize(new java.awt.Dimension(140, 95));
        setMinimumSize(new java.awt.Dimension(140, 95));
        setPreferredSize(new java.awt.Dimension(140, 95));
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                formMouseReleased(evt);
            }
        });

        locIcon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        locIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                locIconMouseReleased(evt);
            }
        });

        LocLabel.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        LocLabel.setText("jLabel1");
        LocLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                LocLabelMouseReleased(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(locIcon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(LocLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(locIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(LocLabel)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseReleased
        tcui.mapLocToCurrentButton(loc.getName());
        
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            // Update all other tooltips
            for (Component c: this.mainLocList.getComponents())
            {
                ((LocomotiveSelectorItem) c).refreshToolTip();
            }
        }));
        
        if (tcui.getLocSelector().doCloseWindow())
        {
            tcui.getLocSelector().setVisible(false);
        }  
    }//GEN-LAST:event_formMouseReleased

    private void LocLabelMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_LocLabelMouseReleased
        formMouseReleased(evt);
    }//GEN-LAST:event_LocLabelMouseReleased

    private void locIconMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locIconMouseReleased
        formMouseReleased(evt);
    }//GEN-LAST:event_locIconMouseReleased

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel LocLabel;
    private javax.swing.JLabel locIcon;
    // End of variables declaration//GEN-END:variables
}
