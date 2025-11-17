package org.traincontrol.gui;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import org.traincontrol.automation.Edge;
import org.traincontrol.base.Accessory;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.graphstream.graph.Graph;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.util.I18n;

/**
 *
 * @author Adam
 */
public class GraphEdgeEdit extends javax.swing.JFrame
{
    TrainControlUI parent;
    Edge e;
    Graph graph;

    /**
     * Creates new form GraphLocAssign
     * @param parent
     * @param graph
     * @param e
     */
    public GraphEdgeEdit(TrainControlUI parent, Graph graph, Edge e)
    {
        initComponents();
        this.parent = parent;
        this.e = e;
        this.graph = graph;
        
        updateValues();
        
        this.pack();
        this.setTitle("Edit Edge " + e.getName());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 

        this.setVisible(true);

        this.setAlwaysOnTop(parent.isAlwaysOnTop());
        requestFocus();
        toFront();
    }
    
    /**
     * Returns if the checkbox to capture commands is selected
     * @return 
     */
    public boolean isCaptureCommandsSelected()
    {
        return this.captureCommands.isSelected();
    }
    
    /**
     * Appends a command when capturing commands is on
     * @param command 
     */
    public void appendCommand(String command)
    {
        this.configCommands.setText(this.configCommands.getText().trim() + "\n" + command);
        this.configCommands.setText(RouteEditor.filterConfigCommands(this.configCommands.getText()));
    }
        
    public final void updateValues()
    {
        // Configure lock edge list
        this.lockEdges.removeAll();
        DefaultListModel elementList = new DefaultListModel();
        
        List<String> edgeNames = new LinkedList<>();

        for (Edge e2 : parent.getModel().getAutoLayout().getEdges())
        {
            edgeNames.add(e2.getName());
        }

        Collections.sort(edgeNames);
        
        List<Integer> selected = new LinkedList<>();

        for (String s : edgeNames)
        {
            // Shouldn't be a lock edge with itself
            if (s.equals(e.getName())) continue;
            
            // Store index of current lock edges
            if (e.getLockEdges().contains(parent.getModel().getAutoLayout().getEdge(s)))
            {
                selected.add(elementList.size());
            }
            
            elementList.addElement(s);
        }
        
        this.lockEdges.setModel(elementList);
                
        this.lockEdges.setSelectedIndices(selected.stream()
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .toArray());
        
        // Populate command list
        String config = "";

        for (Entry<String, Accessory.accessorySetting> command : e.getConfigCommands().entrySet())
        {
            config += command.getKey() + "," + command.getValue().toString().toLowerCase() + "\n";
        }
        
        if (e.getLength() < this.edgeLength.getItemCount())
        {
            this.edgeLength.setSelectedIndex(e.getLength());
        }
        else
        {
            this.edgeLength.setSelectedIndex(this.edgeLength.getItemCount() - 1);
        } 
        
        this.configCommands.setText(config.trim());
        
        // Preview lock edges on the graph
        this.updateUILockedEdges();
    }
    
    /**
     * Applies the lock edge selections and length from the UI
     */
    public void applyLockEdges()
    {
        List<String> selectedLockEdges = this.lockEdges.getSelectedValuesList();
        
        e.clearLockEdges();
        
        for (String edge : selectedLockEdges)
        {
            e.addLockEdge(parent.getModel().getAutoLayout().getEdge(edge));
        }
        
        e.setLength(this.edgeLength.getSelectedIndex());
        
        // Reset highlighed lock edges
        this.parent.highlightLockedEdges(null, null);
    }
    
    /**
     * Applies configuration commands as specified in the UI
     * @param test - true to execute commands but not save
     * @throws java.lang.Exception
     */
    public void validateAndApplyConfigCommands(boolean test) throws Exception
    {
        String[] commands = this.configCommands.getText().trim().split("\n");
        
        // Reset highlighed lock edges
        this.parent.highlightLockedEdges(null, null);
        
        if (test && this.configCommands.getText().length() == 0)
        {
            throw new Exception(I18n.t("autolayout.ui.edgeConfigEdit"));
        }
                
        for (String s : commands)
        {
            if (s.split(",").length == 2)
            {
                String command = s.split(",")[0].trim();
                String setting = s.split(",")[1].trim();
                
                Edge.validateConfigCommand(command, setting, parent.getModel());
            }
            else if (s.trim().length() > 0)
            {
                throw new Exception(
                    I18n.f("autolayout.ui.errorCommandMustBeCommaSeparated", s)
                );
            }
        }
        
        if (!test) e.clearAllConfigCommands();
        
        for (String s : commands)
        {
            if (s.split(",").length == 2)
            {
                String command = s.split(",")[0].trim();
                String setting = s.split(",")[1].trim();
                
                if (!test)
                {
                    e.addConfigCommand(command, Accessory.stringToAccessorySetting(setting));    
                }
                else
                {
                    Accessory a = this.parent.getModel().getAccessoryByName(command);

                    if (a != null) a.setState(Accessory.stringToAccessorySetting(setting));
                }
            }
        }   
    }
    
    /**
     * Gets all accessories in the command list
     * @return 
     */
    public List<MarklinAccessory> getCommandAccessories()
    {
        String[] commands = this.configCommands.getText().trim().split("\n");
        
        List<MarklinAccessory> output = new ArrayList<>();
                
        for (String s : commands)
        {
            try
            {
                if (s.split(",").length == 2)
                {
                    String command = s.split(",")[0].trim();
                    String setting = s.split(",")[1].trim();

                    Edge.validateConfigCommand(command, setting, parent.getModel());
                    
                    output.add(this.parent.getModel().getAccessoryByName(command));
                }
            }
            catch (Exception e)
            {
                // Invalid command, skip it
            }
        }
 
        return output;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        arrivalFuncLabel = new javax.swing.JLabel();
        departureFuncLabel = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        configCommands = new javax.swing.JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();
        lockEdges = new javax.swing.JList<>();
        departureFuncLabel1 = new javax.swing.JLabel();
        edgeLength = new javax.swing.JComboBox<>();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        captureCommands = new javax.swing.JCheckBox();
        testCommands = new javax.swing.JButton();
        departureFuncLabel2 = new javax.swing.JLabel();
        testCommands1 = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();

        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMaximumSize(new java.awt.Dimension(1024, 611));
        setMinimumSize(new java.awt.Dimension(560, 611));
        setPreferredSize(new java.awt.Dimension(560, 611));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                formKeyReleased(evt);
            }
        });

        arrivalFuncLabel.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        arrivalFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/traincontrol/resources/messages"); // NOI18N
        arrivalFuncLabel.setText(bundle.getString("autolayout.ui.signalSwitchCommands")); // NOI18N
        arrivalFuncLabel.setToolTipText(bundle.getString("autolayout.ui.tooltip.signalSwitchCommands")); // NOI18N

        departureFuncLabel.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        departureFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel.setText(bundle.getString("autolayout.ui.edgeLength")); // NOI18N

        configCommands.setColumns(10);
        configCommands.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        configCommands.setRows(5);
        configCommands.setToolTipText(bundle.getString("autolayout.ui.tooltip.signalSwitchCommandsHint")); // NOI18N
        jScrollPane2.setViewportView(configCommands);

        lockEdges.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        lockEdges.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        lockEdges.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lockEdgesMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(lockEdges);

        departureFuncLabel1.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        departureFuncLabel1.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel1.setText(bundle.getString("autolayout.ui.lockEdges")); // NOI18N
        departureFuncLabel1.setToolTipText(bundle.getString("autolayout.ui.tooltip.lockEdges")); // NOI18N

        edgeLength.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        edgeLength.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20" }));
        edgeLength.setToolTipText(bundle.getString("autolayout.ui.tooltip.maxTrainLengthOnEdge")); // NOI18N
        edgeLength.setFocusable(false);

        okButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        okButton.setText(bundle.getString("ui.ok")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        cancelButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        cancelButton.setText(bundle.getString("ui.cancel")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        captureCommands.setText(bundle.getString("autolayout.ui.captureCommands")); // NOI18N
        captureCommands.setToolTipText(bundle.getString("autolayout.ui.tooltip.captureCommands")); // NOI18N
        captureCommands.setFocusable(false);

        testCommands.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        testCommands.setText(bundle.getString("ui.test")); // NOI18N
        testCommands.setToolTipText(bundle.getString("autolayout.ui.tooltip.executeCommandTest")); // NOI18N
        testCommands.setFocusable(false);
        testCommands.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testCommandsActionPerformed(evt);
            }
        });

        departureFuncLabel2.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        departureFuncLabel2.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel2.setText(bundle.getString("autolayout.ui.commandOptions")); // NOI18N
        departureFuncLabel2.setToolTipText(bundle.getString("autolayout.ui.tooltip.commandOptions")); // NOI18N

        testCommands1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        testCommands1.setText(bundle.getString("ui.highlight")); // NOI18N
        testCommands1.setToolTipText(bundle.getString("autolayout.ui.tooltip.highlightOnTrackDiagram")); // NOI18N
        testCommands1.setFocusable(false);
        testCommands1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testCommands1ActionPerformed(evt);
            }
        });

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cancelButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(arrivalFuncLabel)))
                            .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(departureFuncLabel2)
                                    .addComponent(testCommands, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(testCommands1, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(captureCommands, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(4, 4, 4)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(departureFuncLabel1)
                            .addComponent(departureFuncLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(edgeLength, 0, 332, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(arrivalFuncLabel)
                    .addComponent(departureFuncLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 483, Short.MAX_VALUE)
                            .addComponent(jScrollPane2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(departureFuncLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(edgeLength, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(departureFuncLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(testCommands)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(testCommands1)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(captureCommands))
                    .addComponent(jSeparator1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cancelButton)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(okButton)
                        .addContainerGap())))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void lockEdgesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lockEdgesMouseClicked
        this.updateUILockedEdges();
    }//GEN-LAST:event_lockEdgesMouseClicked

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        
        try
        {
            validateAndApplyConfigCommands(false);
            applyLockEdges();

            parent.updateEdgeLength(e, graph);


            parent.repaintAutoLocList(false);
            parent.getModel().getAutoLayout().refreshUI();
            
            this.setVisible(false);
            this.dispose();
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(
                this,
                I18n.f("autolayout.ui.errorEditingEdge", e.getMessage())
            );
        }
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        this.setVisible(false);
        
        // Clear highlighted edges
        parent.repaintAutoLocList(false);
        parent.getModel().getAutoLayout().refreshUI();
        
        this.dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // Clear highlighted edges
        parent.repaintAutoLocList(false);
        parent.getModel().getAutoLayout().refreshUI();
    }//GEN-LAST:event_formWindowClosing

    private void testCommandsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testCommandsActionPerformed
        
        this.captureCommands.setSelected(false);
        
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {   
            try
            {
                this.validateAndApplyConfigCommands(true);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this,
                    I18n.f("error.generic",  e.getMessage())
                );
            }
        }));
    }//GEN-LAST:event_testCommandsActionPerformed

    /**
     * Highlights tiles on the layout
     */
    private void highlightTiles()
    {
        // The "true" will highlight the tiles
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        { 
            if (!parent.getModel().getLayoutList().isEmpty())
            {
                parent.jumpToLayoutTab();
                
                for (MarklinAccessory a : this.getCommandAccessories())
                {
                    a.updateTiles(true);
                } 
            }
        }));
    }
    
    private void testCommands1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testCommands1ActionPerformed
        highlightTiles();
    }//GEN-LAST:event_testCommands1ActionPerformed

    private void formKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ESCAPE)
        {    
            dispose(); // Close the window
        }
    }//GEN-LAST:event_formKeyReleased

    /**
     * Updates the lock edges shown on the graph for easier editing
     */
    private void updateUILockedEdges()
    {
        List<Edge> selectedLockEdges = new LinkedList<>();
        
        for (String edge : this.lockEdges.getSelectedValuesList())
        {
            selectedLockEdges.add(parent.getModel().getAutoLayout().getEdge(edge));
        }
        
        this.parent.highlightLockedEdges(e, selectedLockEdges);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel arrivalFuncLabel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JCheckBox captureCommands;
    private javax.swing.JTextArea configCommands;
    private javax.swing.JLabel departureFuncLabel;
    private javax.swing.JLabel departureFuncLabel1;
    private javax.swing.JLabel departureFuncLabel2;
    private javax.swing.JComboBox<String> edgeLength;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JList<String> lockEdges;
    private javax.swing.JButton okButton;
    private javax.swing.JButton testCommands;
    private javax.swing.JButton testCommands1;
    // End of variables declaration//GEN-END:variables
}
