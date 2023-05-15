/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import automation.Edge;
import automation.Layout;
import automation.Point;
import base.Locomotive;
import java.awt.Color;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListModel;

/**
 *
 * @author Adam
 */
public class AutoLocomotiveStatus extends javax.swing.JPanel {

    private final Locomotive locomotive;
    private List<List<Edge>> paths;
    private final Layout layout;
    
    /**
     * Creates new form AutoLocomotiveStatus
     * @param loc
     * @param layout
     */
    public AutoLocomotiveStatus(Locomotive loc, Layout layout) {
        this.layout = layout;
        this.locomotive = loc;
        initComponents();
        
        this.locName.setText(locomotive.getName());
        
        updateState(loc);
        
        this.setVisible(true);
    }

    public void updateState(Locomotive someLoc)
    {
        // We only need to update if the callback corresponding to our locomotive was fired
        if (someLoc == null || someLoc.equals(locomotive))
        {
            this.paths = layout.getPossiblePaths(locomotive);

            DefaultListModel<String> pathList = new DefaultListModel<>();
            
            this.locDest.setForeground(new Color(0, 0, 115));

            // Locomotive is running - show the path and hide the list
            if (layout.getActiveLocomotives().containsKey(locomotive.getName()))
            {
                List<Point> milestones = layout.getReachedMilestones(locomotive.getName());
                
                this.locDest.setText(Edge.pathToString(layout.getActiveLocomotives().get(locomotive.getName())) + " [" + milestones.get(milestones.size() - 1).getName() + "]"  );
                this.locDest.setForeground(new Color(204, 0, 0));
                this.locAvailPaths.setVisible(false);
            }
            // Layout is in auto mode but loc is not running - show stauts message and hide the list
            else if (layout.isRunning())
            {
                this.locDest.setText("No active path.");
                this.locAvailPaths.setVisible(false);
            }
            // Layout is standing by.  Show the list.
            else
            {
                if (!this.paths.isEmpty())
                {
                    this.locDest.setText("Double-click a path to execute");
                }
                else
                {
                    this.locDest.setText("No available paths.");
                }
                
                for (List<Edge> path : this.paths)
                {
                    pathList.add(pathList.getSize(), Edge.pathToString(path));
                }
                
                this.locAvailPaths.setVisible(true);
            }

            this.locAvailPaths.setModel(pathList);
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        locName = new javax.swing.JLabel();
        locDest = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        locAvailPaths = new javax.swing.JList<>();

        setBackground(new java.awt.Color(238, 238, 238));
        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        setFocusable(false);
        setMaximumSize(new java.awt.Dimension(240, 205));
        setPreferredSize(new java.awt.Dimension(240, 205));

        locName.setFont(new java.awt.Font("Tahoma", 1, 16)); // NOI18N
        locName.setText("jLabel1");
        locName.setFocusable(false);

        locDest.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        locDest.setForeground(new java.awt.Color(0, 0, 115));
        locDest.setText("jLabel2");
        locDest.setFocusable(false);

        locAvailPaths.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        locAvailPaths.setFocusable(false);
        locAvailPaths.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                locAvailPathsMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(locAvailPaths);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(locName)
                            .addComponent(locDest))
                        .addGap(0, 150, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(locName)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locDest)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 132, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void locAvailPathsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseClicked
        JList list = (JList)evt.getSource();
        if (evt.getClickCount() == 2) {
            // Double-click detected
            int index = list.locationToIndex(evt.getPoint());
                        
            if (!layout.isRunning() && !this.paths.isEmpty())
            {
                new Thread( () -> {
                    boolean success = this.layout.executePath(this.paths.get(index), locomotive, locomotive.getPreferredSpeed());
                    
                    if (!success)
                    {
                        JOptionPane.showMessageDialog(this, "Route could not be executed - likely occupied. Check log.");
                    }
                }).start();
            }
        } 
    }//GEN-LAST:event_locAvailPathsMouseClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList<String> locAvailPaths;
    private javax.swing.JLabel locDest;
    private javax.swing.JLabel locName;
    // End of variables declaration//GEN-END:variables
}
