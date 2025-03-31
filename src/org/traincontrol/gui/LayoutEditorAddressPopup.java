package org.traincontrol.gui;

import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.Timer;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLayoutComponent;

public class LayoutEditorAddressPopup extends javax.swing.JPanel
{
    /**
     * Creates new form LayourEditorAddressPopup
     * @param lc
     */
    public LayoutEditorAddressPopup(MarklinLayoutComponent lc)
    {
        initComponents();
        
        this.mm2Radio.setVisible(false);
        this.dccRadio.setVisible(false);
        this.mm2Radio.setSelected(false);
        this.dccRadio.setSelected(false);

        if (lc.isLink())
        {
            this.helpLabel.setText("Enter the layout page number to link to.  The first page has address 1.");
        }
        else if (lc.isRoute())
        {
            this.helpLabel.setText("Check the Routes tab for the IDs of your routes.");
        }
        else if (lc.isSwitch() || lc.isSignal() || lc.isLamp() || lc.isUncoupler())
        {
            this.helpLabel.setText("Valid accessory addresses range from 1 to 320 (Marklin) or 2048 (DCC).");
            
            if (lc.isUncoupler())
            {
                this.helpLabel.setText("<html>" + this.helpLabel.getText() + "<br>There can be two uncouplers on the same address.  The checkbox differentiates this.</html>");
            }
            
            if (lc.getProtocol() == MarklinAccessory.accessoryDecoderType.MM2)
            {
                this.mm2Radio.setSelected(true);
            }
            else
            {
                this.dccRadio.setSelected(true);
            }
            
            this.mm2Radio.setVisible(true);
            this.dccRadio.setVisible(true);
        }
        else if (lc.isFeedback())
        {
            this.helpLabel.setText("Check your Central Station for S88 addresses/bus ranges.");
        }
        else
        {
            this.helpLabel.setVisible(false);
        }
    }

    public JTextField getAddress()
    {
        return address;
    }
    
    public MarklinAccessory.accessoryDecoderType getProtocol()
    {
        if (this.mm2Radio.isSelected())
        {
            return MarklinAccessory.accessoryDecoderType.MM2;
        }
        else if (this.dccRadio.isSelected())
        {
            return MarklinAccessory.accessoryDecoderType.DCC;
        }
        else
        {
            return null;
        }
    }

    public JCheckBox getGreenButton()
    {
        return greenButton;
    }
  
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        address = new javax.swing.JTextField();
        greenButton = new javax.swing.JCheckBox();
        helpLabel = new javax.swing.JLabel();
        mm2Radio = new javax.swing.JRadioButton();
        dccRadio = new javax.swing.JRadioButton();

        address.addAncestorListener(new javax.swing.event.AncestorListener() {
            public void ancestorMoved(javax.swing.event.AncestorEvent evt) {
            }
            public void ancestorAdded(javax.swing.event.AncestorEvent evt) {
                addressAncestorAdded(evt);
            }
            public void ancestorRemoved(javax.swing.event.AncestorEvent evt) {
            }
        });
        address.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                addressKeyReleased(evt);
            }
        });

        greenButton.setText("Controlled by Green Button");

        helpLabel.setText("help text...");

        buttonGroup1.add(mm2Radio);
        mm2Radio.setText("MM2");

        buttonGroup1.add(dccRadio);
        dccRadio.setText("DCC");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(greenButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(address)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(helpLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(mm2Radio)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(dccRadio)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(helpLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(address, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(greenButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(mm2Radio)
                    .addComponent(dccRadio))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addressKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_addressKeyReleased
        TrainControlUI.validateInt(evt, false);
        TrainControlUI.limitLength(evt, 6);
    }//GEN-LAST:event_addressKeyReleased

    private void addressAncestorAdded(javax.swing.event.AncestorEvent evt) {//GEN-FIRST:event_addressAncestorAdded
        // This will be triggered when the JTextField is added to a container
        Timer focusTimer = new Timer(50, e -> address.requestFocusInWindow());
        focusTimer.setRepeats(false); // Ensure the timer only fires once
        focusTimer.start();

    }//GEN-LAST:event_addressAncestorAdded


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField address;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JRadioButton dccRadio;
    private javax.swing.JCheckBox greenButton;
    private javax.swing.JLabel helpLabel;
    private javax.swing.JRadioButton mm2Radio;
    // End of variables declaration//GEN-END:variables
}
