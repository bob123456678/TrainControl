/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import automation.Point;
import base.Locomotive;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;

/**
 *
 * @author Adam
 */
public class GraphLocAssign extends javax.swing.JPanel {
    TrainControlUI parent;
    Point p;

    /**
     * Creates new form GraphLocAssign
     * @param parent
     * @param p
     * @param newOnly - do we allow the selection of locomotives not currently on the graph?
     */
    public GraphLocAssign(TrainControlUI parent, Point p, boolean newOnly) {
        initComponents();
        
        List<String> locs;
        if (newOnly)
        {
            locs = new LinkedList<>(parent.getModel().getLocList());
            locs.removeAll(parent.getModel().getAutoLayout().getLocomotivesToRun());
        }
        else
        {
            locs = new LinkedList<>(parent.getModel().getAutoLayout().getLocomotivesToRun());
        }
        
        Collections.sort(locs);

        this.locAssign.setModel(new DefaultComboBoxModel(locs.toArray()));
        this.parent = parent;
        this.p = p;
        
        // Select current locomotive if possible
        if (p.getCurrentLocomotive() != null)
        {
            this.locAssign.setSelectedItem(p.getCurrentLocomotive().getName());
        }
        
        // Always show
        boolean visibility = true;
                
        if (newOnly)
        {
            this.arrivalFunc.setSelectedIndex(0);
            this.departureFunc.setSelectedIndex(0);
            this.trainLength.setSelectedIndex(0);

            visibility = true;
        }
        
        updateValues();

        this.arrivalFunc.setVisible(visibility);
        this.arrivalFuncLabel.setVisible(visibility);
        this.departureFunc.setVisible(visibility);
        this.departureFuncLabel.setVisible(visibility);
        this.reversible.setVisible(visibility);
        this.trainLength.setVisible(visibility);
    }
    
    public final void updateValues()
    {
        if (this.locAssign.getModel().getSize() > 0)
        {
            String locomotive = (String) this.locAssign.getSelectedItem();
            Locomotive loc = this.parent.getModel().getLocByName(locomotive);
            
            this.reversible.setSelected(loc.isReversible());
            
            if (loc.getArrivalFunc() != null)
            {
                this.arrivalFunc.setSelectedIndex(loc.getArrivalFunc() + 1);
            }
            else
            {
                this.arrivalFunc.setSelectedIndex(0);
            }
            
            // Items are numbers starting at 0, so we go by the max number
            if (loc.getTrainLength() < this.trainLength.getItemCount())
            {
                this.trainLength.setSelectedIndex(loc.getTrainLength());
            }
            else
            {
                this.trainLength.setSelectedIndex(this.trainLength.getItemCount() - 1);
            } 
            
            if (loc.getDepartureFunc() != null)
            {
                this.departureFunc.setSelectedIndex(loc.getDepartureFunc() + 1);
            }
            else
            {
                this.departureFunc.setSelectedIndex(0);
            }    
            
            if (loc.getPreferredSpeed() > 0)
            {
                this.speed.setValue(loc.getPreferredSpeed());
            }
            else
            {
                this.speed.setValue(this.parent.getModel().getAutoLayout().getDefaultLocSpeed());
            }
            
            updateSpeedLabel();
        }
    }
    
    public void updateSpeedLabel()
    {
        this.speedLabel.setText("Speed (" + this.speed.getValue() + ")");
    }
    
    public boolean isReversible()
    {
        return this.reversible.isSelected();
    }
    
    public Integer getSpeed()
    {
        return this.speed.getValue();
    }
    
    public Integer getTrainLength()
    {
        return Integer.valueOf(this.trainLength.getSelectedItem().toString());
    }
    
    public Integer getDepartureFunc()
    {
        if ("None".equals((String) this.departureFunc.getSelectedItem()))
        {
            return null;
        }
        
        return new Integer((String) this.departureFunc.getSelectedItem());
    }
    
    public Integer getArrivalFunc()
    {
        if ("None".equals((String) this.arrivalFunc.getSelectedItem()))
        {
            return null;
        }
        
        return new Integer((String) this.arrivalFunc.getSelectedItem());
    }
    
    public String getLoc()
    {
        return (String) this.locAssign.getSelectedItem();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        locAssign = new javax.swing.JComboBox<>();
        reversible = new javax.swing.JCheckBox();
        arrivalFunc = new javax.swing.JComboBox<>();
        arrivalFuncLabel = new javax.swing.JLabel();
        departureFuncLabel = new javax.swing.JLabel();
        departureFunc = new javax.swing.JComboBox<>();
        speedLabel = new javax.swing.JLabel();
        speed = new javax.swing.JSlider();
        departureFuncLabel1 = new javax.swing.JLabel();
        trainLength = new javax.swing.JComboBox<>();

        locAssign.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        locAssign.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                locAssignActionPerformed(evt);
            }
        });

        reversible.setText("Reversible");
        reversible.setFocusable(false);
        reversible.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reversibleActionPerformed(evt);
            }
        });

        arrivalFunc.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "None", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32" }));
        arrivalFunc.setFocusable(false);

        arrivalFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        arrivalFuncLabel.setText("Arrival Function");

        departureFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel.setText("Departure Function");

        departureFunc.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "None", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32" }));
        departureFunc.setFocusable(false);

        speedLabel.setForeground(new java.awt.Color(0, 0, 115));
        speedLabel.setText("Speed");

        speed.setMinimum(1);
        speed.setMinorTickSpacing(5);
        speed.setPaintLabels(true);
        speed.setPaintTicks(true);
        speed.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                speedStateChanged(evt);
            }
        });

        departureFuncLabel1.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel1.setText("Train Length");

        trainLength.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20" }));
        trainLength.setFocusable(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(reversible)
                    .addComponent(locAssign, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(arrivalFuncLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(arrivalFunc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(speedLabel)
                    .addComponent(speed, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(departureFuncLabel)
                            .addComponent(departureFuncLabel1))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(trainLength, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(departureFunc, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(locAssign, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(reversible)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(arrivalFunc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(arrivalFuncLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(departureFuncLabel)
                    .addComponent(departureFunc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(departureFuncLabel1)
                    .addComponent(trainLength, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(speedLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(speed, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void reversibleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reversibleActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_reversibleActionPerformed

    private void locAssignActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_locAssignActionPerformed
       updateValues();
    }//GEN-LAST:event_locAssignActionPerformed

    private void speedStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_speedStateChanged
        updateSpeedLabel();
    }//GEN-LAST:event_speedStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> arrivalFunc;
    private javax.swing.JLabel arrivalFuncLabel;
    private javax.swing.JComboBox<String> departureFunc;
    private javax.swing.JLabel departureFuncLabel;
    private javax.swing.JLabel departureFuncLabel1;
    private javax.swing.JComboBox<String> locAssign;
    private javax.swing.JCheckBox reversible;
    private javax.swing.JSlider speed;
    private javax.swing.JLabel speedLabel;
    private javax.swing.JComboBox<String> trainLength;
    // End of variables declaration//GEN-END:variables
}
