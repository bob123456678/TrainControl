package org.traincontrol.gui;

import com.formdev.flatlaf.ui.FlatLineBorder;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

/**
 * Displays the status of a locomotive in autonomous operation
 */
public final class AutoLocomotiveStatus extends javax.swing.JPanel
{
    private final Locomotive locomotive;
    private List<List<Edge>> paths;
    private final Layout layout;
    private final ViewListener control;
    private final TrainControlUI parent;
    
    /**
     * Creates new form AutoLocomotiveStatus
     * @param loc
     * @param parent
     */
    public AutoLocomotiveStatus(Locomotive loc, TrainControlUI parent) 
    {
        this.parent = parent;
        this.control = parent.getModel();
        this.layout = this.control.getAutoLayout();
        this.locomotive = loc;
        initComponents();
        
        this.locName.setText(locomotive.getName());
        
        // Style labels
        locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), new Color(0,0,115), 1, 999));
        locStation.setBackground(new Color(0,0,115));
        locStation.setForeground(new Color(255,255,255));
        
        updateState(loc);
        
        Font font = new Font("Arial Unicode MS", Font.PLAIN, 12); 
        if (font.canDisplay('\u23F8'))
        {
            pauseButton.setFont(font); 
            pauseButton.setText("\u23F8");
        }
                
        this.setVisible(true);
        
        // Propagate scroll event for short lists
        availablePathScroll.addMouseWheelListener(e ->
        {
            // JScrollBar verticalBar = availablePathScroll.getVerticalScrollBar();

            // If vertical scrollbar is not visible or can't scroll further
            //boolean noScrollNeeded = !verticalBar.isVisible();
                // || (verticalBar.getMaximum() - verticalBar.getVisibleAmount() <= verticalBar.getValue() &&
                // e.getWheelRotation() > 0) || // scrolling down at bottom
                // (verticalBar.getValue() == 0 && e.getWheelRotation() < 0); // scrolling up at top

            if (!availablePathScroll.getVerticalScrollBar().isVisible())
            {
                // Forward event to parent scroll pane
                parent.getAutoLocScroll().dispatchEvent(SwingUtilities.convertMouseEvent(
                    availablePathScroll, e, parent.getAutoLocScroll()));
            }
        });
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
            DefaultListModel<String> pathList = new DefaultListModel<>();
            
            this.locDest.setForeground(new Color(0, 0, 115));
            
            // Ensure consistent state
            this.pauseButton.setSelected(locomotive.isAutonomyPaused());
                     
            // Grey out locomotives on inactive points / not on the graph
            if ((layout.getLocomotiveLocation(locomotive) != null && !layout.getLocomotiveLocation(locomotive).isActive()) ||
                    layout.getLocomotiveLocation(locomotive) == null
            )
            {
                this.locName.setForeground(Color.LIGHT_GRAY);
                this.pauseButton.setVisible(false);
                
                // Grey out label
                locStation.setBackground(Color.LIGHT_GRAY);
                locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), Color.LIGHT_GRAY, 1, 999));
            }
            else
            {
                this.locName.setForeground(Color.BLACK);
                this.pauseButton.setVisible(true);
                
                // Restore label color
                locStation.setBackground(new Color(0,0,115));
                locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), new Color(0,0,115), 1, 999 ));
            }
            
            this.locStation.setToolTipText("");

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
                    this.locDest.setText(I18n.t("autolayout.ui.noActivePath"));
                    this.locStation.setText("@" + layout.getLocomotiveLocation(locomotive).getName());
                }
                else
                {
                    this.locStation.setText("?????");
                    this.locDest.setText(I18n.t("autolayout.ui.locNotOnGraph"));
                }
                                
                this.locAvailPaths.setVisible(false);
            }
            // Layout is standing by.  Show the list.
            else
            {                
                // true -> Only include unique starts/end pairs
                this.paths = layout.getPossiblePaths(locomotive, true);
                
                if (!this.paths.isEmpty())
                {
                    this.locDest.setText(I18n.t("autolayout.ui.doubleClickExecute"));
                    this.locStation.setText("@" + layout.getLocomotiveLocation(locomotive).getName()                     
                        + (layout.getLocomotiveLocation(locomotive).equals(layout.getTimetableStartingPoint(locomotive)) ? " *" : "")
                        + (layout.getLocomotiveLocation(locomotive).getExcludedLocs().contains(locomotive) ? " -" : "")

                    );
                }
                else if (layout.getLocomotiveLocation(locomotive) != null)
                {                    
                    this.locDest.setText(I18n.t("autolayout.ui.noAvailPaths"));
                    this.locStation.setText("@" +  layout.getLocomotiveLocation(locomotive).getName()
                        + (layout.getLocomotiveLocation(locomotive).equals(layout.getTimetableStartingPoint(locomotive)) ? " *" : "")
                        + (layout.getLocomotiveLocation(locomotive).getExcludedLocs().contains(locomotive) ? " -" : "")
                    );
                }
                else
                {
                    this.locStation.setText("?????");
                    this.locDest.setText(I18n.t("autolayout.ui.locNotOnGraph"));
                }
                
                this.locStation.setToolTipText(I18n.t("autolayout.ui.tooltip.currentLocation"));
                
                // Sort the list
                this.paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));
                
                for (List<Edge> path : this.paths)
                {
                    pathList.add(pathList.getSize(), "-> " + path.get(path.size() - 1).getEnd().getName()
                        + (path.get(path.size() - 1).getEnd().equals(layout.getTimetableStartingPoint(locomotive)) ? " *" : "")
                        + (path.get(path.size() - 1).getEnd().getExcludedLocs().contains(locomotive) ? " -" : "")
                    );
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
        availablePathScroll = new javax.swing.JScrollPane();
        locAvailPaths = new javax.swing.JList<>();
        locStation = new javax.swing.JLabel();
        pauseButton = new javax.swing.JToggleButton();

        setBackground(new java.awt.Color(255, 255, 255));
        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));
        setFocusable(false);
        setMaximumSize(new java.awt.Dimension(219, 223));
        setPreferredSize(new java.awt.Dimension(219, 223));

        locName.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        locName.setText("locName");
        locName.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        locName.setFocusable(false);
        locName.setMaximumSize(new java.awt.Dimension(205, 25));
        locName.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                locNameMouseClicked(evt);
            }
        });

        locDest.setFont(new java.awt.Font("Segoe UI", 1, 11)); // NOI18N
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
        availablePathScroll.setViewportView(locAvailPaths);

        locStation.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        locStation.setForeground(new java.awt.Color(0, 0, 115));
        locStation.setText("locStation");
        locStation.setFocusable(false);

        pauseButton.setText("P");
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/traincontrol/resources/messages"); // NOI18N
        pauseButton.setToolTipText(bundle.getString("autolayout.ui.tooltip.tempPauseLoc")); // NOI18N
        pauseButton.setFocusable(false);
        pauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(availablePathScroll)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(locName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pauseButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(locStation)
                            .addComponent(locDest))
                        .addGap(0, 143, Short.MAX_VALUE)))
                .addGap(6, 6, 6))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pauseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locStation)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locDest)
                .addGap(6, 6, 6)
                .addComponent(availablePathScroll, javax.swing.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE)
                .addGap(6, 6, 6))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void locAvailPathsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseClicked
        
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            JList list = (JList) evt.getSource();

            if (evt.getClickCount() == 2)
            {
                // Double-click detected
                int index = list.locationToIndex(evt.getPoint());

                if (!layout.isAutoRunning() && !this.paths.isEmpty())
                {
                    if (!this.control.getPowerState())
                    {
                        JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.powerOnToStart"));
                        return;
                    }

                    // Ensure there are no automatic routes
                    /* for (String routeName : this.control.getRouteList())
                    {
                        MarklinRoute r = this.control.getRoute(routeName);

                        if (r.isEnabled())
                        {
                            this.control.log(r.toString());
                            JOptionPane.showMessageDialog(this, "Please first disable all automatic routes.");
                            return;
                        }
                    }*/

                    new Thread(() ->
                    {
                        parent.ensureGraphUIVisible();

                        boolean success = this.layout.executePath(this.paths.get(index), locomotive, locomotive.getPreferredSpeed(), null);

                        if (!success)
                        {
                            JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.autoFailedCheckLog"));
                        }
                        
                    }).start();
                }
            } 
        }));
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

    private void pauseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButtonActionPerformed
        locomotive.setAutonomyPaused(pauseButton.isSelected());
    }//GEN-LAST:event_pauseButtonActionPerformed

    private void locNameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locNameMouseClicked
        
        if (parent.getActiveLoc() != locomotive)
        {
            parent.mapLocToCurrentButton(locomotive.getName());
        }
    }//GEN-LAST:event_locNameMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane availablePathScroll;
    private javax.swing.JList<String> locAvailPaths;
    private javax.swing.JLabel locDest;
    private javax.swing.JLabel locName;
    private javax.swing.JLabel locStation;
    private javax.swing.JToggleButton pauseButton;
    // End of variables declaration//GEN-END:variables
}
