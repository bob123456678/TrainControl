/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package gui;

import base.Locomotive;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;

/**
 *
 */
public class LocomotiveStats extends javax.swing.JPanel {

    /**
     * Creates new form LocomotiveStats
     * @param tcui
     */
    public LocomotiveStats(TrainControlUI tcui)
    {
        initComponents();
        
        String col[] = {"Locomotive", "Total Runtime", "Last Run", "First Run", "Days Run"};

        DefaultTableModel tableModel = new DefaultTableModel(col, 0);
        
        List<Locomotive> sortedLocs = new ArrayList();

        for (String s : tcui.getModel().getLocList())
        {
            //if (tcui.getModel().getLocByName(s).getTotalRuntime() > 0)
            //{
                sortedLocs.add( tcui.getModel().getLocByName(s));
            //}
        }

        if (!sortedLocs.isEmpty())
        {               
            sortedLocs.sort((Locomotive l1, Locomotive l2) -> Long.valueOf(l2.getTotalRuntime()).compareTo(l1.getTotalRuntime()));
        }
        
        for (Locomotive l : sortedLocs)
        {            
            Object[] data = {l.getName(), convertSecondsToHMmSs(l.getTotalRuntime()), l.getOperatingDate(true), l.getOperatingDate(false), l.getNumDaysRun()};
            
            tableModel.addRow(data);
        }
        
        this.jTable1.setModel(tableModel);
        this.jTable1.setAutoCreateRowSorter(true);
    }
    
    private String convertSecondsToHMmSs(long ms)
    {
        long seconds = ms / 1000;
        
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        
        return String.format("%d:%02d:%02d", h,m,s);
    }
    
    private String convertSecondsToDate(long timestamp)
    {
        if (timestamp == 0) return "Never";
        
        Instant i = Instant.ofEpochMilli( timestamp );
         
        return i.toString().split("T")[0];
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(jTable1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 669, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 367, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    // End of variables declaration//GEN-END:variables
}