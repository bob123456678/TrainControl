package gui;

import base.Locomotive;
import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import marklin.MarklinLocomotive;
import util.Conversion;

/**
 *
 */
public class LocomotiveStats extends javax.swing.JPanel
{
    TrainControlUI tcui;
    
    /**
     * Creates new form LocomotiveStats
     * @param tcui
     */
    public LocomotiveStats(TrainControlUI tcui)
    {
        initComponents();
        this.tcui = tcui;
        
        refresh();
    }
    
    /**
     * Long wrapper to correctly sort timestamps in the table
     */
    private class TimestampString implements Comparable<TimestampString>
    {
        private final Long value;

        public TimestampString(Long value)
        {
            this.value = value;
        }

        public Long getValue()
        {
            return value;
        }

        @Override
        public String toString()
        {
            return Conversion.convertSecondsToHMmSs(value);
        }

        @Override
        public int compareTo(TimestampString other)
        {
            return this.value.compareTo(other.getValue());
        }
    }

    public final void refresh()
    {
        javax.swing.SwingUtilities.invokeLater(new Thread(() ->
        {
            // Aggregate stats
            Long todaysTotalRuntime = 0L;
            int todaysLocsRun = 0;
            Long totalRuntime = 0L;
            int totalLocsRun = 0;

            String col[] = {"Locomotive", "Overall Runtime", "Today's Runtime", "Last Run", "First Run", "Days Run"};

            // Ensure correct sorting
            DefaultTableModel tableModel = new DefaultTableModel(col, 0)
            {
                @Override
                public Class getColumnClass(int column)
                {
                    switch (column)
                    {
                        case 1:
                            return TimestampString.class;
                        case 2:
                            return TimestampString.class;
                        case 5:
                            return Integer.class;
                        default:
                            return String.class;
                    }
                }
                
                // Disable editing
                @Override
                public boolean isCellEditable(int row, int column)
                {  
                    return false;  
                }
            };

            List<Locomotive> sortedLocs = new ArrayList();

            for (Locomotive l : tcui.getModel().getLocomotives())
            {
                sortedLocs.add(l);
            }

            if (!sortedLocs.isEmpty())
            {               
                sortedLocs.sort((Locomotive l1, Locomotive l2) -> Long.valueOf(l2.getTotalRuntime()).compareTo(l1.getTotalRuntime()));
            }

            for (Locomotive l : sortedLocs)
            {            
                Object[] data = {l.getName(), new TimestampString(l.getTotalRuntime()), new TimestampString(l.getRuntimeToday()), 
                    l.getOperatingDate(true), l.getOperatingDate(false), l.getNumDaysRun()};

                tableModel.addRow(data);
                
                // Populate stats
                todaysTotalRuntime += l.getRuntimeToday();
                if (l.getRuntimeToday() > 0) todaysLocsRun +=1;
                
                totalRuntime += l.getTotalRuntime();
                if (l.getTotalRuntime() > 0) totalLocsRun +=1;
            }

            this.todaysRuntimeVal.setText(Conversion.convertSecondsToHMmSs(todaysTotalRuntime));
            this.locCountVal.setText(Integer.toString(todaysLocsRun));
            this.locomotivesLabel.setText(todaysLocsRun == 1 ? "locomotive" : "locomotives");
            
            this.cumulativeRuntimeVal.setText(Conversion.convertSecondsToHMmSs(totalRuntime));
            this.locCountCumulativeVal.setText(Integer.toString(totalLocsRun));
            this.locomotivesCumulativeLabel.setText(totalLocsRun == 1 ? "locomotive" : "locomotives");
            
            this.statsTable.setModel(tableModel);
            this.statsTable.setAutoCreateRowSorter(true);
        }));
    }
    
    private void filterTable()
    {
        String text = this.filterField.getText();
        
        if (text.trim().length() == 0)
        {
            ((TableRowSorter<TableModel>) statsTable.getRowSorter()).setRowFilter(null);
        }
        else
        {
            ((TableRowSorter<TableModel>) statsTable.getRowSorter()).setRowFilter(RowFilter.regexFilter("(?i)" + text));
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

        jScrollPane1 = new javax.swing.JScrollPane();
        statsTable = new javax.swing.JTable();
        exportData = new javax.swing.JButton();
        refresh = new javax.swing.JButton();
        filterLabel = new javax.swing.JLabel();
        filterField = new javax.swing.JTextField();
        todaysRuntimeVal = new javax.swing.JLabel();
        todaysRuntimeLabel = new javax.swing.JLabel();
        cumulativeRuntimeLabel = new javax.swing.JLabel();
        cumulativeRuntimeVal = new javax.swing.JLabel();
        byLabel = new javax.swing.JLabel();
        locCountVal = new javax.swing.JLabel();
        locomotivesLabel = new javax.swing.JLabel();
        byLabel1 = new javax.swing.JLabel();
        locCountCumulativeVal = new javax.swing.JLabel();
        locomotivesCumulativeLabel = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        viewUsageGraph = new javax.swing.JButton();

        setBackground(new java.awt.Color(238, 238, 238));
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
        });

        statsTable.setModel(new javax.swing.table.DefaultTableModel(
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
        jScrollPane1.setViewportView(statsTable);

        exportData.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        exportData.setText("Export Raw Data");
        exportData.setFocusable(false);
        exportData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportDataActionPerformed(evt);
            }
        });

        refresh.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        refresh.setText("Refresh");
        refresh.setFocusable(false);
        refresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshActionPerformed(evt);
            }
        });

        filterLabel.setForeground(new java.awt.Color(0, 0, 115));
        filterLabel.setText("Filter List:");

        filterField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                filterFieldKeyReleased(evt);
            }
            public void keyTyped(java.awt.event.KeyEvent evt) {
                filterFieldKeyTyped(evt);
            }
        });

        todaysRuntimeVal.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        todaysRuntimeVal.setText("jLabel2");

        todaysRuntimeLabel.setForeground(new java.awt.Color(0, 0, 115));
        todaysRuntimeLabel.setText("Today's runtime:");

        cumulativeRuntimeLabel.setForeground(new java.awt.Color(0, 0, 115));
        cumulativeRuntimeLabel.setText("Cumulative runtime:");

        cumulativeRuntimeVal.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        cumulativeRuntimeVal.setText("jLabel5");

        byLabel.setForeground(new java.awt.Color(0, 0, 115));
        byLabel.setText("by");

        locCountVal.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        locCountVal.setText("jLabel2");

        locomotivesLabel.setForeground(new java.awt.Color(0, 0, 115));
        locomotivesLabel.setText("locomotives");

        byLabel1.setForeground(new java.awt.Color(0, 0, 115));
        byLabel1.setText("by");

        locCountCumulativeVal.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        locCountCumulativeVal.setText("jLabel2");

        locomotivesCumulativeLabel.setForeground(new java.awt.Color(0, 0, 115));
        locomotivesCumulativeLabel.setText("locomotives");

        viewUsageGraph.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        viewUsageGraph.setText("30-day Usage Graph");
        viewUsageGraph.setFocusable(false);
        viewUsageGraph.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewUsageGraphActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator1)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 753, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(filterLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(filterField, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(viewUsageGraph)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exportData)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(refresh))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(todaysRuntimeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(todaysRuntimeVal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(byLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(locCountVal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(locomotivesLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cumulativeRuntimeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cumulativeRuntimeVal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(byLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(locCountCumulativeVal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(locomotivesCumulativeLabel)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 405, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(todaysRuntimeLabel)
                    .addComponent(todaysRuntimeVal)
                    .addComponent(byLabel)
                    .addComponent(locCountVal)
                    .addComponent(locomotivesLabel)
                    .addComponent(cumulativeRuntimeLabel)
                    .addComponent(cumulativeRuntimeVal)
                    .addComponent(byLabel1)
                    .addComponent(locCountCumulativeVal)
                    .addComponent(locomotivesCumulativeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exportData)
                    .addComponent(refresh)
                    .addComponent(filterLabel)
                    .addComponent(filterField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(viewUsageGraph))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void exportDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportDataActionPerformed
        this.exportData.setEnabled(false);
        new Thread(() ->
        {
            try
            {
                JFileChooser fc = new JFileChooser(TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, new File(".").getAbsolutePath()));
                fc.setSelectedFile(new File("TC_locstats_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis()) + ".csv"));
                int i = fc.showSaveDialog(this);

                if (i == JFileChooser.APPROVE_OPTION)
                {
                    File f = fc.getSelectedFile();
                    
                    String data = "";
                    
                    for (MarklinLocomotive l : this.tcui.getModel().getLocomotives())
                    {
                        for (Entry<String, Long> e : l.getHistoricalOperatingTime().entrySet())
                        {
                            data += "\"" + l.getName() + "\"," + e.getKey() + "," + e.getValue() + "\n";
                        }
                    }

                    Files.write(Paths.get(f.getPath()), data.trim().getBytes());
                    TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, f.getParent());
                }
            }
            catch (HeadlessException | IOException e)
            {
                JOptionPane.showMessageDialog(this, "Error writing file.");

                this.tcui.getModel().log(e);
            }
            
            this.exportData.setEnabled(true);
            this.tcui.resetFocus();
        }).start();
    }//GEN-LAST:event_exportDataActionPerformed

    private void refreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshActionPerformed
        this.refresh();
        this.tcui.resetFocus();
    }//GEN-LAST:event_refreshActionPerformed

    private void filterFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_filterFieldKeyReleased
        filterTable();
    }//GEN-LAST:event_filterFieldKeyReleased

    private void filterFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_filterFieldKeyTyped
        filterTable();
    }//GEN-LAST:event_filterFieldKeyTyped

    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        this.tcui.resetFocus();
    }//GEN-LAST:event_formMouseClicked

    private void viewUsageGraphActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewUsageGraphActionPerformed
        
        javax.swing.SwingUtilities.invokeLater(new Thread(() -> 
        {
            new UsageHistogram(this.tcui);
        }));
    }//GEN-LAST:event_viewUsageGraphActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel byLabel;
    private javax.swing.JLabel byLabel1;
    private javax.swing.JLabel cumulativeRuntimeLabel;
    private javax.swing.JLabel cumulativeRuntimeVal;
    private javax.swing.JButton exportData;
    private javax.swing.JTextField filterField;
    private javax.swing.JLabel filterLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JLabel locCountCumulativeVal;
    private javax.swing.JLabel locCountVal;
    private javax.swing.JLabel locomotivesCumulativeLabel;
    private javax.swing.JLabel locomotivesLabel;
    private javax.swing.JButton refresh;
    private javax.swing.JTable statsTable;
    private javax.swing.JLabel todaysRuntimeLabel;
    private javax.swing.JLabel todaysRuntimeVal;
    private javax.swing.JButton viewUsageGraph;
    // End of variables declaration//GEN-END:variables
}
