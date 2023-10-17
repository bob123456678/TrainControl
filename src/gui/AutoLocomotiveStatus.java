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
import marklin.MarklinRoute;
import model.ViewListener;

/**
 *
 * @author Adam
 */
public final class AutoLocomotiveStatus extends javax.swing.JPanel {

    private final Locomotive locomotive;
    private List<List<Edge>> paths;
    private final Layout layout;
    private final ViewListener control;
    
    /**
     * Creates new form AutoLocomotiveStatus
     * @param loc
     * @param control
     */
    public AutoLocomotiveStatus(Locomotive loc, ViewListener control) 
    {
        this.layout = control.getAutoLayout();
        this.locomotive = loc;
        this.control = control;
        initComponents();
        
        this.locName.setText(locomotive.getName());
        
        updateState(loc);
        
        this.setVisible(true);
    }

    /**
     * Refreshes the available routes shown in the UI
     * @param someLoc 
     */
    public void updateState(Locomotive someLoc)
    {
        // We only need to update if the callback corresponding to our locomotive was fired
        if (someLoc == null || someLoc.equals(locomotive))
        {
            // true -> Only include unique starts/end pairs
            this.paths = layout.getPossiblePaths(locomotive, true);

            DefaultListModel<String> pathList = new DefaultListModel<>();
            
            this.locDest.setForeground(new Color(0, 0, 115));
                        
            // Grey out locomotives on inactive points / not on the graph
            if ((
                    layout.getLocomotiveLocation(locomotive) != null && !layout.getLocomotiveLocation(locomotive).isActive()) ||
                    layout.getLocomotiveLocation(locomotive) == null
            )
            {
                this.locName.setForeground(Color.LIGHT_GRAY);
            }
            else
            {
                this.locName.setForeground(Color.BLACK);
            }

            // Locomotive is running - show the path and hide the list
            if (layout.getActiveLocomotives().containsKey(locomotive))
            {
                List<Point> milestones = layout.getReachedMilestones(locomotive);
                
                this.locDest.setText(Edge.pathToString(layout.getActiveLocomotives().get(locomotive)));
                
                this.locStation.setText("@" + milestones.get(milestones.size() - 1).getName());
                
                this.locDest.setForeground(new Color(204, 0, 0));
                this.locAvailPaths.setVisible(false);
            }
            // Layout is in auto mode but loc is not running - show status message and hide the list
            else if (layout.isAutoRunning())
            {
                if (layout.getLocomotiveLocation(locomotive) != null)
                {
                    this.locDest.setText("No active path.");
                    this.locStation.setText("@" + layout.getLocomotiveLocation(locomotive).getName());
                }
                else
                {
                    this.locStation.setText("?????");
                    this.locDest.setText("Locomotive is not placed on the graph.");
                }
                
                this.locAvailPaths.setVisible(false);
            }
            // Layout is standing by.  Show the list.
            else
            {
                if (!this.paths.isEmpty())
                {
                    this.locDest.setText("Double-click a path to execute");
                    
                    this.locStation.setText("@" + layout.getLocomotiveLocation(locomotive).getName());
                }
                else if (layout.getLocomotiveLocation(locomotive) != null)
                {
                    this.locDest.setText("No available paths.");
                    this.locStation.setText("@" +  layout.getLocomotiveLocation(locomotive).getName());
                }
                else
                {
                    this.locStation.setText("?????");
                    this.locDest.setText("Locomotive is not placed on the graph.");
                }
                
                // Sort the list
                this.paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));
                
                for (List<Edge> path : this.paths)
                {
                    pathList.add(pathList.getSize(), "-> " + path.get(path.size() - 1).getEnd().getName());
                        //Edge.pathToString(path));
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
        locStation = new javax.swing.JLabel();

        setBackground(new java.awt.Color(238, 238, 238));
        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        setFocusable(false);
        setMaximumSize(new java.awt.Dimension(219, 223));
        setPreferredSize(new java.awt.Dimension(219, 223));

        locName.setFont(new java.awt.Font("Tahoma", 1, 16)); // NOI18N
        locName.setText("locName");
        locName.setFocusable(false);

        locDest.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        locDest.setForeground(new java.awt.Color(0, 0, 115));
        locDest.setText("locDest");
        locDest.setFocusable(false);

        locAvailPaths.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        locAvailPaths.setFocusable(false);
        locAvailPaths.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseMoved(java.awt.event.MouseEvent evt) {
                locAvailPathsMouseMoved(evt);
            }
        });
        locAvailPaths.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                locAvailPathsMouseClicked(evt);
            }
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                locAvailPathsMouseEntered(evt);
            }
        });
        jScrollPane1.setViewportView(locAvailPaths);

        locStation.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        locStation.setForeground(new java.awt.Color(0, 0, 115));
        locStation.setText("locStation");
        locStation.setFocusable(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(locName)
                            .addComponent(locStation)
                            .addComponent(locDest))
                        .addGap(0, 156, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 226, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(locName)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locStation)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locDest)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void locAvailPathsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseClicked
        
        JList list = (JList) evt.getSource();
        
        if (evt.getClickCount() == 2) {
            // Double-click detected
            int index = list.locationToIndex(evt.getPoint());
                        
            if (!layout.isAutoRunning() && !this.paths.isEmpty())
            {
                if (!this.control.getPowerState())
                {
                    JOptionPane.showMessageDialog(this, "To start autonomy, please turn the track power on, or cycle the power.");
                    return;
                }
                
                // Ensure there are no automatic routes
                for (String routeName : this.control.getRouteList())
                {
                    MarklinRoute r = this.control.getRoute(routeName);

                    if (r.isEnabled())
                    {
                        this.control.log(r.toString());
                        JOptionPane.showMessageDialog(this, "Please first disable all automatic routes.");
                        return;
                    }
                }
                
                new Thread( () -> {
                    boolean success = this.layout.executePath(this.paths.get(index), locomotive, locomotive.getPreferredSpeed());
                    
                    if (!success)
                    {
                        JOptionPane.showMessageDialog(this, "Auto route could not be executed: check log.");
                    }
                }).start();
            }
        } 
    }//GEN-LAST:event_locAvailPathsMouseClicked

    private void locAvailPathsMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseEntered
         
       
    }//GEN-LAST:event_locAvailPathsMouseEntered

    private void locAvailPathsMouseMoved(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseMoved
        
        // Show a tooltip with the path
        /*JList list = (JList) evt.getSource();

        int index = list.locationToIndex(evt.getPoint());
                
        if (index < this.paths.size() && index >= 0)
        {
            List<String> strings = new LinkedList<>();
            this.paths.get(index).forEach((e) -> {
                strings.add(e.toString());
            });
            
            list.setToolTipText("<html>" + String.join("<br>", strings) + "</html>");
        }
        else
        {
            list.setToolTipText("");
        }*/      
    }//GEN-LAST:event_locAvailPathsMouseMoved


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList<String> locAvailPaths;
    private javax.swing.JLabel locDest;
    private javax.swing.JLabel locName;
    private javax.swing.JLabel locStation;
    // End of variables declaration//GEN-END:variables
}
