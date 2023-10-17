/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package gui;

import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;


/**
 *
 * @author adamo
 */
public class AutoJSONExport extends javax.swing.JPanel {

    TrainControlUI tcui;
    
    /**
     * Creates new form AutoJSONExport
     * @param text
     */
    public AutoJSONExport(String text, TrainControlUI tcui) {
        initComponents();
        this.jsonTextArea.setText(text);
        jsonTextArea.setLineWrap(true);
        jsonTextArea.setWrapStyleWord(true);
        this.tcui = tcui;
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
        jsonTextArea = new javax.swing.JTextArea();
        jsonSaveAs = new javax.swing.JButton();

        jsonTextArea.setColumns(50);
        jsonTextArea.setFont(new java.awt.Font("Monospaced", 0, 16)); // NOI18N
        jsonTextArea.setRows(25);
        jScrollPane1.setViewportView(jsonTextArea);

        jsonSaveAs.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jsonSaveAs.setText("Save to File...");
        jsonSaveAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jsonSaveAsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jsonSaveAs)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 700, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 386, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jsonSaveAs)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jsonSaveAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jsonSaveAsActionPerformed
        this.jsonSaveAs.setEnabled(false);
        new Thread(() ->
        {
            try
            {
                JFileChooser fc = this.tcui.getJSONFileChooser(JFileChooser.FILES_ONLY);
                fc.setSelectedFile(new File("TC_autonomy_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis()) + ".json"));
                int i = fc.showSaveDialog(this);

                if (i == JFileChooser.APPROVE_OPTION)
                {
                    File f = fc.getSelectedFile();

                    byte[] json = this.jsonTextArea.getText().getBytes();

                    Files.write(Paths.get(f.getPath()), json);
                    tcui.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, f.getParent());
                }
            }
            catch (HeadlessException | IOException e)
            {
                JOptionPane.showMessageDialog(this, "Error writing file.");

                if (this.tcui.getModel().isDebug())
                {
                    e.printStackTrace();
                }
            }
            
            this.jsonSaveAs.setEnabled(true);
        }).start();
    }//GEN-LAST:event_jsonSaveAsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton jsonSaveAs;
    private javax.swing.JTextArea jsonTextArea;
    // End of variables declaration//GEN-END:variables
}
