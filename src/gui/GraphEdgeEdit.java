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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;

/**
 *
 * @author Adam
 */
public class GraphEdgeEdit extends javax.swing.JPanel {
    TrainControlUI parent;
    Edge e;

    /**
     * Creates new form GraphLocAssign
     * @param parent
     * @param e
     */
    public GraphEdgeEdit(TrainControlUI parent, Edge e) {
        initComponents();
        this.parent = parent;
        this.e = e;
        
        updateValues();
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

        for (Entry<String, String> command : e.getConfigCommands().entrySet())
        {
            config += command.getKey() + "," + command.getValue() + "\n";
        }

        // TODO - add validate config command to Edge
        
        this.configCommands.setText(config.trim());
    }
    
    /**
     * Applies the lock edge selections from the UI
     */
    public void applyLockEdges()
    {
        List<String> selectedLockEdges = this.lockEdges.getSelectedValuesList();
        
        e.clearLockEdges();
        
        for (String edge : selectedLockEdges)
        {
            e.addLockEdge(parent.getModel().getAutoLayout().getEdge(edge));
        }
    }
    
    /**
     * Applies configuration commands as specified in the UI
     */
    public void validateAndApplyConfigCommands() throws Exception
    {
        String[] commands = this.configCommands.getText().split("\n");
        
        for (String s : commands)
        {
            if (s.split(",").length == 2)
            {
                String command = s.split(",")[0].trim();
                String setting = s.split(",")[1].trim();
                
                e.validateConfigCommand(command, setting, parent.getModel());
            }
            else
            {
                throw new Exception("Command " + s + " must be comma-separated. Example: Signal 1,turn");
            }
        }
        
        e.clearAllConfigCommands();
        
        for (String s : commands)
        {
            if (s.split(",").length == 2)
            {
                String command = s.split(",")[0].trim();
                String setting = s.split(",")[1].trim();
                
                e.addConfigCommand(command, setting);     
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

        arrivalFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        arrivalFuncLabel.setText("Signal/Switch Commands");

        departureFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel.setText("Lock Edges");

        configCommands.setColumns(10);
        configCommands.setRows(5);
        jScrollPane2.setViewportView(configCommands);

        lockEdges.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(lockEdges);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(arrivalFuncLabel)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(departureFuncLabel)
                        .addGap(0, 197, Short.MAX_VALUE))
                    .addComponent(jScrollPane1))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(arrivalFuncLabel)
                    .addComponent(departureFuncLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane2)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 391, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel arrivalFuncLabel;
    private javax.swing.JTextArea configCommands;
    private javax.swing.JLabel departureFuncLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> lockEdges;
    // End of variables declaration//GEN-END:variables
}
