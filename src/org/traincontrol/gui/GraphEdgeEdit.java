package org.traincontrol.gui;

import java.awt.Toolkit;
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
            throw new Exception("To test that a configuration matches the intended segment of track, enter the necessary switch and signal commands first.");
        }
                
        for (String s : commands)
        {
            if (s.split(",").length == 2)
            {
                String command = s.split(",")[0].trim();
                String setting = s.split(",")[1].trim();
                
                e.validateConfigCommand(command, setting, parent.getModel());
            }
            else if (s.trim().length() > 0)
            {
                throw new Exception("Command " + s + " must be comma-separated. Example: Signal 1,turn");
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

        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMaximumSize(new java.awt.Dimension(520, 611));
        setMinimumSize(new java.awt.Dimension(520, 611));
        setPreferredSize(new java.awt.Dimension(520, 611));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        arrivalFuncLabel.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        arrivalFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        arrivalFuncLabel.setText("Signal/Switch Commands");
        arrivalFuncLabel.setToolTipText("The commands needed to make this edge match a real segment of track.");

        departureFuncLabel.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        departureFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel.setText("Edge Length");

        configCommands.setColumns(10);
        configCommands.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        configCommands.setRows(5);
        configCommands.setToolTipText("Format: Signal 1,red/green or Switch 2,turn/straight");
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
        departureFuncLabel1.setText("Lock Edges");
        departureFuncLabel1.setToolTipText("Select all other edges that should be locked when this one is occupied.");

        edgeLength.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        edgeLength.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20" }));
        edgeLength.setFocusable(false);

        okButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        cancelButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        captureCommands.setText("Capture Commands");
        captureCommands.setToolTipText("Select this to automatically capture commands from the track diagram and keyboard.");
        captureCommands.setFocusable(false);

        testCommands.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        testCommands.setText("Test");
        testCommands.setToolTipText("Execute the commands for verification on the track diagram.");
        testCommands.setFocusable(false);
        testCommands.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testCommandsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(arrivalFuncLabel)
                        .addGap(58, 58, 58)
                        .addComponent(departureFuncLabel1)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 205, Short.MAX_VALUE)
                            .addComponent(testCommands, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1)
                        .addContainerGap())))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(captureCommands, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(9, 9, 9)
                        .addComponent(departureFuncLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(10, 10, 10)
                        .addComponent(edgeLength, 0, 180, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(arrivalFuncLabel)
                    .addComponent(departureFuncLabel1))
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(testCommands))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 507, Short.MAX_VALUE))
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(edgeLength, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(departureFuncLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(captureCommands, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
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
            JOptionPane.showMessageDialog(this,
                "Error editing edge: " + e.getMessage());
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
                    "Error: " + e.getMessage());
            }
        }));
    }//GEN-LAST:event_testCommandsActionPerformed

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
    private javax.swing.JComboBox<String> edgeLength;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> lockEdges;
    private javax.swing.JButton okButton;
    private javax.swing.JButton testCommands;
    // End of variables declaration//GEN-END:variables
}
