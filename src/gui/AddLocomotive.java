package gui;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import javax.swing.JOptionPane;
import marklin.MarklinLocomotive;
import model.ViewListener;

public class AddLocomotive extends javax.swing.JFrame
{
    private final ViewListener model;
    private final TrainControlUI parent;
    
    /**
     * Creates new form AddLocomotive
     * @param model
     * @param ui
     */
    public AddLocomotive(ViewListener model, TrainControlUI ui)
    {
        this.model = model;
        this.parent = ui;   
        
        this.setAlwaysOnTop(true);
        
        initComponents();
        
        this.requestFocus();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        LocTypeMM2 = new javax.swing.JRadioButton();
        jLabel4 = new javax.swing.JLabel();
        AddLocButton = new javax.swing.JButton();
        LocAddressInput = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        LocNameInput = new javax.swing.JTextField();
        LocTypeMFX = new javax.swing.JRadioButton();
        LocTypeDCC = new javax.swing.JRadioButton();
        checkDuplicates = new javax.swing.JButton();
        AddNewLocLabel = new javax.swing.JLabel();

        setTitle("Add Locomotive");
        setBackground(new java.awt.Color(238, 238, 238));
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));

        jPanel2.setBackground(new java.awt.Color(245, 245, 245));
        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel2.setFocusable(false);

        LocTypeMM2.setText("MM2");
        LocTypeMM2.setFocusable(false);
        LocTypeMM2.setInheritsPopupMenu(true);

        jLabel4.setText("Locomotive Type");
        jLabel4.setFocusable(false);

        AddLocButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        AddLocButton.setText("Add");
        AddLocButton.setFocusable(false);
        AddLocButton.setInheritsPopupMenu(true);
        AddLocButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddLocButtonActionPerformed(evt);
            }
        });

        LocAddressInput.setColumns(5);
        LocAddressInput.setInheritsPopupMenu(true);
        LocAddressInput.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                LocAddressInputKeyReleased(evt);
            }
        });

        jLabel3.setText("Locomotive Address");
        jLabel3.setToolTipText("Enter as integer or hex for MFX, and integer to DCC/MM2.");
        jLabel3.setFocusable(false);

        jLabel1.setText("Locomotive Name");
        jLabel1.setFocusable(false);

        LocNameInput.setInheritsPopupMenu(true);
        LocNameInput.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                LocNameInputKeyReleased(evt);
            }
        });

        LocTypeMFX.setText("MFX");
        LocTypeMFX.setFocusable(false);
        LocTypeMFX.setInheritsPopupMenu(true);

        LocTypeDCC.setSelected(true);
        LocTypeDCC.setText("DCC");
        LocTypeDCC.setFocusable(false);

        checkDuplicates.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        checkDuplicates.setText("Check Duplicates");
        checkDuplicates.setFocusable(false);
        checkDuplicates.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkDuplicatesActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(AddLocButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(jLabel1))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(LocNameInput)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(LocAddressInput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(checkDuplicates, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(LocTypeMM2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(LocTypeMFX)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(LocTypeDCC)
                                .addGap(0, 0, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(LocNameInput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(LocAddressInput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(checkDuplicates))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(LocTypeMM2)
                    .addComponent(LocTypeMFX)
                    .addComponent(LocTypeDCC))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(AddLocButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        AddNewLocLabel.setForeground(new java.awt.Color(0, 0, 115));
        AddNewLocLabel.setText("Add Locomotive to Database");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(AddNewLocLabel)
                        .addGap(195, 195, 195))
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(4, 4, 4))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(AddNewLocLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void AddLocButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddLocButtonActionPerformed
        // TODO - make this generic
        new Thread(()->
            {
                String locName = this.LocNameInput.getText();

                if (locName == null)
                {
                    return;
                }

                if (locName.trim().length() == 0)
                {
                    JOptionPane.showMessageDialog(this,
                        "Please enter a locomotive name");
                    return;
                }

                if (locName.length() >= TrainControlUI.MAX_LOC_NAME_DATABASE)
                {
                    JOptionPane.showMessageDialog(this,
                        "Please enter a locomotive name under " + TrainControlUI.MAX_LOC_NAME_DATABASE + " characters");
                    return;
                }

                if (this.model.getLocByName(locName) != null)
                {
                    JOptionPane.showMessageDialog(this,
                        "A locomotive by this name already exists.");
                    return;
                }

                marklin.MarklinLocomotive.decoderType type;

                if (this.LocTypeMFX.isSelected())
                {
                    type = marklin.MarklinLocomotive.decoderType.MFX;
                }
                else if (this.LocTypeDCC.isSelected())
                {
                    type = marklin.MarklinLocomotive.decoderType.DCC;
                }
                else
                {
                    type = marklin.MarklinLocomotive.decoderType.MM2;
                }

                int locAddress;

                try
                {
                    if (this.LocTypeMFX.isSelected() && this.LocAddressInput.getText().contains("0x"))
                    {
                        locAddress = Integer.parseInt(this.LocAddressInput.getText().replace("0x", ""), 16);
                    }
                    else
                    {
                        locAddress = Integer.parseInt(this.LocAddressInput.getText());
                    }
                }
                catch (NumberFormatException e)
                {
                    JOptionPane.showMessageDialog(this,
                        "Please enter a numerical address");
                    return;
                }

                locAddress = Math.abs(locAddress);

                if (type == marklin.MarklinLocomotive.decoderType.MM2)
                {
                    if (locAddress > marklin.MarklinLocomotive.MM2_MAX_ADDR)
                    {
                        JOptionPane.showMessageDialog(this,
                            "MM2 address out of range");
                        return;
                    }
                }

                if (type == marklin.MarklinLocomotive.decoderType.MM2)
                {
                    if (locAddress > marklin.MarklinLocomotive.DCC_MAX_ADDR)
                    {
                        JOptionPane.showMessageDialog(this,
                            "DCC address out of range");
                        return;
                    }
                }

                if (type == marklin.MarklinLocomotive.decoderType.MFX)
                {
                    if (locAddress > marklin.MarklinLocomotive.MFX_MAX_ADDR)
                    {
                        JOptionPane.showMessageDialog(this,
                            "MFX address out of range");
                        return;
                    }
                }

                if (type == marklin.MarklinLocomotive.decoderType.MFX)
                {
                    this.model.newMFXLocomotive(locName, locAddress);
                }
                else if (type == marklin.MarklinLocomotive.decoderType.DCC)
                {
                    this.model.newDCCLocomotive(locName, locAddress);
                }
                else
                {
                    this.model.newMM2Locomotive(locName, locAddress);
                }

                // Add list of locomotives to dropdown
                this.parent.getLocSelector().refreshLocSelectorList();

                // Rest form
                JOptionPane.showMessageDialog(this, "Locomotive added successfully");

                this.LocAddressInput.setText("");
                this.LocNameInput.setText("");
  
            }).start();
    }//GEN-LAST:event_AddLocButtonActionPerformed

    private void LocAddressInputKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_LocAddressInputKeyReleased
        TrainControlUI.validateInt(evt, true);
        TrainControlUI.limitLength(evt, 6);
    }//GEN-LAST:event_LocAddressInputKeyReleased

    private void LocNameInputKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_LocNameInputKeyReleased
        TrainControlUI.limitLength(evt, TrainControlUI.MAX_LOC_NAME_DATABASE);
    }//GEN-LAST:event_LocNameInputKeyReleased

    private void checkDuplicatesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkDuplicatesActionPerformed

        new Thread(()->
        {
            int locAddress;

            try
            {
                if (this.LocTypeMFX.isSelected())
                {
                    locAddress = Integer.parseInt(this.LocAddressInput.getText().replace("0x", ""), 16);
                }
                else
                {
                    locAddress = Integer.parseInt(this.LocAddressInput.getText());
                }
            }
            catch (NumberFormatException e)
            {
                JOptionPane.showMessageDialog(this,
                    "Please enter a numerical address");
                return;
            }

            Map<Integer, Set<MarklinLocomotive>> locs = this.model.getDuplicateLocAddresses();
            String message;

            if (locs.containsKey(locAddress))
            {
                message = "Locomotive address is already in use.  See log for details.";
            }
            else
            {
                message = "Address is free.  See log for details.";
            }

            if (!locs.isEmpty())
            {
                List<Integer> sortedLocs = new ArrayList(locs.keySet());
                Collections.sort(sortedLocs, Collections.reverseOrder());

                for (Integer addr : sortedLocs)
                {
                    for (MarklinLocomotive l : locs.get(addr))
                    {
                        this.model.log("\t" + l.getName() + " [" + l.getDecoderTypeLabel() + "]");
                    }

                    this.model.log("---- Address " + addr + " ----");
                }

                this.model.log("Duplicate locomotive address report:");
            }
            else
            {
                this.model.log("There are no duplicate locomotive addresses in the database.");
            }

            JOptionPane.showMessageDialog(this, message);
        }).start();
    }//GEN-LAST:event_checkDuplicatesActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AddLocButton;
    private javax.swing.JLabel AddNewLocLabel;
    private javax.swing.JTextField LocAddressInput;
    private javax.swing.JTextField LocNameInput;
    private javax.swing.JRadioButton LocTypeDCC;
    private javax.swing.JRadioButton LocTypeMFX;
    private javax.swing.JRadioButton LocTypeMM2;
    private javax.swing.JButton checkDuplicates;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel2;
    // End of variables declaration//GEN-END:variables
}
