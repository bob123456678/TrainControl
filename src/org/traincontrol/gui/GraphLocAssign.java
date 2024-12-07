package org.traincontrol.gui;

import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/**
 *
 * @author Adam
 */
public class GraphLocAssign extends javax.swing.JPanel
{
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
            
            for (Locomotive l : parent.getModel().getAutoLayout().getLocomotivesToRun())
            {
                locs.remove(l.getName());
            }
        }
        else
        {
            locs = new LinkedList<>();
            
            for (Locomotive l : parent.getModel().getAutoLayout().getLocomotivesToRun())
            {
                locs.add(l.getName());
            }
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
        
        // Give the dropdown focus so you can filter with the keyboard
        this.locAssign.addAncestorListener(new AncestorListener()
        {
            @Override
            public void ancestorRemoved(AncestorEvent event) {}

            @Override
            public void ancestorMoved(AncestorEvent event) {}

            @Override
            public void ancestorAdded(AncestorEvent event) {
                event.getComponent().requestFocusInWindow();
            }
        });
    }
    
    public final void updateValues()
    {
        if (this.locAssign.getModel().getSize() > 0)
        {
            String locomotive = (String) this.locAssign.getSelectedItem();
            Locomotive loc = this.parent.getModel().getLocByName(locomotive);
            
            // Dynamically set number of selectable functions
            List<String> arrivalFuncModel= new ArrayList<>(Arrays.asList("None"));
            List<String> departureFuncModel= new ArrayList<>(Arrays.asList("None"));
            
            for (int i = 0; i < loc.getNumF(); i++)
            {
                arrivalFuncModel.add(Integer.toString(i));
                departureFuncModel.add(Integer.toString(i));
            }
            
            arrivalFunc.setModel(
                new javax.swing.DefaultComboBoxModel<>(arrivalFuncModel.toArray(new String[0])) 
            );

            departureFunc.setModel(
                new javax.swing.DefaultComboBoxModel<>(departureFuncModel.toArray(new String[0])) 
            );
            
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
        
        return Integer.valueOf((String) this.departureFunc.getSelectedItem());
    }
    
    public Integer getArrivalFunc()
    {
        if ("None".equals((String) this.arrivalFunc.getSelectedItem()))
        {
            return null;
        }
        
        return Integer.valueOf((String) this.arrivalFunc.getSelectedItem());
    }
    
    public String getLoc()
    {
        return (String) this.locAssign.getSelectedItem();
    }
    
    /**
     * Applies all changes from the UI
     */
    public void commitChanges()
    {
        parent.getModel().getAutoLayout().moveLocomotive(getLoc(), p.getName(), false);

        parent.getModel().getLocByName(getLoc()).setReversible(isReversible());
        parent.getModel().getLocByName(getLoc()).setArrivalFunc(getArrivalFunc());
        parent.getModel().getLocByName(getLoc()).setDepartureFunc(getDepartureFunc());
        parent.getModel().getLocByName(getLoc()).setPreferredSpeed(getSpeed());
        parent.getModel().getLocByName(getLoc()).setTrainLength(getTrainLength());

        parent.getModel().getAutoLayout().applyDefaultLocCallbacks(parent.getModel().getLocByName(getLoc()));
        
        parent.repaintAutoLocList(false);  
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

        setMaximumSize(new java.awt.Dimension(262, 246));
        setPreferredSize(new java.awt.Dimension(262, 246));

        locAssign.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locAssign.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        locAssign.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                locAssignActionPerformed(evt);
            }
        });

        reversible.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        reversible.setText("Reversible");

        arrivalFunc.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        arrivalFunc.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "None", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31" }));

        arrivalFuncLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        arrivalFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        arrivalFuncLabel.setText("Arrival Function");

        departureFuncLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        departureFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel.setText("Departure Function");

        departureFunc.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        departureFunc.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "None", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31" }));

        speedLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
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

        departureFuncLabel1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        departureFuncLabel1.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel1.setText("Train Length");

        trainLength.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        trainLength.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20" }));

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
