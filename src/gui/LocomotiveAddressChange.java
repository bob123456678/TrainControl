/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package gui;

import base.Locomotive;
import marklin.MarklinLocomotive;
import marklin.MarklinLocomotive.decoderType;

/**
 * UI for changing locomotive functions
 */
public class LocomotiveAddressChange extends javax.swing.JPanel 
{
    MarklinLocomotive loc;
    
    /**
     * Creates new form LocomotiveFunctionAssign
     * @param l
     */
    public LocomotiveAddressChange(Locomotive l)
    {
        this.loc = (MarklinLocomotive) l;
        initComponents();
        
        this.decoderTypeInput.setModel(new javax.swing.DefaultComboBoxModel(decoderType.values()));
                
        this.address.setText(Integer.toString(this.loc.getAddress()));
        this.decoderTypeInput.setSelectedItem(this.loc.getDecoderType());
    }
    
    public String getAddress()
    {
        return this.address.getText();
    }
    
    public decoderType getDecoderType()
    {
        return (decoderType) this.decoderTypeInput.getSelectedItem();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        decoderTypeInput = new javax.swing.JComboBox<>();
        decoderTypeLabel = new javax.swing.JLabel();
        addressLabel = new javax.swing.JLabel();
        address = new javax.swing.JTextField();

        setMinimumSize(new java.awt.Dimension(213, 97));

        decoderTypeInput.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "MFX", "MM2", "DCC", "Multi-unit" }));
        decoderTypeInput.setFocusable(false);
        decoderTypeInput.setMinimumSize(new java.awt.Dimension(100, 27));
        decoderTypeInput.setPreferredSize(new java.awt.Dimension(100, 27));

        decoderTypeLabel.setForeground(new java.awt.Color(0, 0, 115));
        decoderTypeLabel.setText("Decoder Type");

        addressLabel.setForeground(new java.awt.Color(0, 0, 115));
        addressLabel.setText("Address");

        address.setText("jTextField1");
        address.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                addressKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(decoderTypeLabel)
                    .addComponent(addressLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 81, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(decoderTypeInput, 0, 144, Short.MAX_VALUE)
                    .addComponent(address))
                .addGap(14, 14, 14))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addressLabel)
                    .addComponent(address, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(decoderTypeInput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(decoderTypeLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addressKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_addressKeyReleased
        TrainControlUI.validateInt(evt, true);
        TrainControlUI.limitLength(evt, 6);
    }//GEN-LAST:event_addressKeyReleased


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField address;
    private javax.swing.JLabel addressLabel;
    private javax.swing.JComboBox<String> decoderTypeInput;
    private javax.swing.JLabel decoderTypeLabel;
    // End of variables declaration//GEN-END:variables
}
