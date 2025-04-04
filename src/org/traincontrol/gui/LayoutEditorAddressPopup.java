package org.traincontrol.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.Timer;
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.MarklinLayoutComponent;
import org.traincontrol.marklin.MarklinRoute;

public class LayoutEditorAddressPopup extends javax.swing.JPanel
{
    private final TrainControlUI tcui;
    private final MarklinLayoutComponent lc;
    
    /**
     * Creates new form LayourEditorAddressPopup
     * @param lc
     * @param tcui
     */
    public LayoutEditorAddressPopup(MarklinLayoutComponent lc, TrainControlUI tcui)
    {
        initComponents();
        
        this.tcui = tcui;
        this.lc = lc;
        
        this.mm2Radio.setVisible(false);
        this.dccRadio.setVisible(false);
        this.mm2Radio.setSelected(false);
        this.dccRadio.setSelected(false);
        this.addressSelector.setVisible(false);

        if (lc.isLink())
        {
            this.helpLabel.setText("Select the page to link to. More can be added through the Layouts menu.");
            
            // Set the model for the addressSelector
            this.addressSelector.setModel(new DefaultComboBoxModel<>(tcui.getModel().getLayoutList().toArray(new String[0])));        
            this.addressSelector.setVisible(true);
            this.address.setVisible(false);
        }
        else if (lc.isRoute())
        {
            this.helpLabel.setText("Check the Routes tab to edit/create routes.");
            
            // We set the selection by the string, so showing numbered routes won't work yet...
            List<String> numberedRoutes = tcui.getModel().getRouteList().stream().map(this.tcui.getModel()::getRoute).map(this::addRouteId).collect(Collectors.toList());
            
            if (numberedRoutes.isEmpty())
            {
                this.addressSelector.setEnabled(false);
            }
            else
            {
                this.addressSelector.setModel(new DefaultComboBoxModel<>(numberedRoutes.toArray(new String[0])));
                //this.addressSelector.setModel(new DefaultComboBoxModel<>(tcui.getModel().getRouteList().toArray(new String[0]))); 
            }     
            
            this.addressSelector.setVisible(true);

            this.address.setVisible(false);
        }
        else if (lc.isSwitch() || lc.isSignal() || lc.isLamp() || lc.isUncoupler())
        {
            this.helpLabel.setText("Accessory addresses range from 1 to 320 (Marklin MM2) or 2048 (DCC).");
            
            if (lc.isUncoupler())
            {
                this.helpLabel.setText("<html>" + this.helpLabel.getText() + "<br>There can be two uncouplers on the same address.  The checkbox differentiates this.</html>");
            }
            
            if (lc.getProtocol() == Accessory.accessoryDecoderType.MM2)
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

    public void setAddress(String addr)
    {
        if (this.address.isVisible())
        {
            address.setText(addr);
        }
        else if (this.addressSelector.isVisible())
        {
            if (lc.isRoute())
            {
                this.addressSelector.setSelectedItem(addRouteId(tcui.getModel().getRoute(Integer.parseInt(addr))));
            }
            else
            {
                if (this.addressSelector.getItemCount() > Integer.parseInt(addr) - 1)
                {
                    this.addressSelector.setSelectedIndex(Integer.parseInt(addr) - 1);
                }
            }
        }
    }
    
    private String addRouteId(MarklinRoute r)
    {
        if (r == null) return "";
        
        return r.getId() + ". " + r.getName();
    }
    
    public String getAddress()
    {
        if (this.address.isVisible())
        {
            return address.getText();
        }
        else
        {
            return Integer.toString(this.addressSelector.getSelectedIndex() + 1);
        }
    }
    
    public Accessory.accessoryDecoderType getProtocol()
    {
        if (this.mm2Radio.isSelected())
        {
            return Accessory.accessoryDecoderType.MM2;
        }
        else if (this.dccRadio.isSelected())
        {
            return Accessory.accessoryDecoderType.DCC;
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
        addressSelector = new javax.swing.JComboBox<>();

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
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(addressSelector, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(addressSelector, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addressKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_addressKeyReleased
        TrainControlUI.validateInt(evt, false);
        TrainControlUI.limitLength(evt, 5);
    }//GEN-LAST:event_addressKeyReleased

    private void addressAncestorAdded(javax.swing.event.AncestorEvent evt) {//GEN-FIRST:event_addressAncestorAdded
        // This will be triggered when the JTextField is added to a container
        Timer focusTimer = new Timer(50, e -> address.requestFocusInWindow());
        focusTimer.setRepeats(false); // Ensure the timer only fires once
        focusTimer.start();

    }//GEN-LAST:event_addressAncestorAdded


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField address;
    private javax.swing.JComboBox<String> addressSelector;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JRadioButton dccRadio;
    private javax.swing.JCheckBox greenButton;
    private javax.swing.JLabel helpLabel;
    private javax.swing.JRadioButton mm2Radio;
    // End of variables declaration//GEN-END:variables
}
