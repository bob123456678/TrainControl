package org.traincontrol.gui;

import org.traincontrol.base.Locomotive;
import static org.traincontrol.gui.TrainControlUI.LAST_USED_ICON_FOLDER;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicComboPopup;
import org.traincontrol.util.I18n;

/**
 * UI for changing locomotive functions
 */
public class LocomotiveFunctionAssign extends javax.swing.JPanel
{
    /**
     * The file a stored image URL names, or null when it does not name one.
     *
     * A locomotive's local image is stored as a URL string, so `new File(...)` on it is wrong.
     *
     * @param url the stored value
     * @return the file, or null for anything that is not a readable file: URL
     */
    private static File fileOf(String url)
    {
        if (url == null) return null;

        try
        {
            java.net.URI uri = new java.net.URI(url);

            if (!"file".equalsIgnoreCase(uri.getScheme())) return null;

            return new File(uri);
        }
        catch (java.net.URISyntaxException | IllegalArgumentException notAFileUrl)
        {
            return null;
        }
    }


    Locomotive loc;
    TrainControlUI parent;
    String customIconPath;
    
    /**
     * Creates new form LocomotiveFunctionAssign
     * @param l
     * @param parent
     * @param functionIndex // function to start at
     * @param standalone
     */
    public LocomotiveFunctionAssign(Locomotive l, TrainControlUI parent, int functionIndex, boolean standalone)
    {
        this.loc = l;
        this.parent = parent;
        initComponents();
        
        // Dynamically set number of selectable functions
        List<String> funcModel = new ArrayList<>();
        List<Object> iconModel = new ArrayList<>();

        for (int i = 0; i < loc.getNumF(); i++)
        {
            funcModel.add(Integer.toString(i));
        }
        
        // Optimization - fetch first icon only and apply it to all tiles
        ImageIcon icon = null;
        try
        {
            String targetURL = loc.getFunctionIconUrl(0, parent.getModel().isCS3() || !parent.getModel().getNetworkCommState(), true);
            icon = new ImageIcon(parent.getLocImage(targetURL, TrainControlUI.BUTTON_ICON_WIDTH));
        }
        catch (Exception e)
        {
            this.parent.getModel().logf("loc.ui.errorLoadingFunctionIcon", 0);
        }
        
        for (int i = 0; i <= this.loc.getNumFnIcons(); i++)
        {
            if (icon != null)
            {                
                iconModel.add(icon);
            }
            else
            {
                iconModel.add(Integer.toString(i));
            }
        }
                
        fNo.setModel(
            new javax.swing.DefaultComboBoxModel<>(funcModel.toArray(new String[0])) 
        );

        fIcon.setModel(
            new javax.swing.DefaultComboBoxModel(iconModel.toArray(new Object[0])) 
        );
                
        // Display current icon
        fNoItemStateChanged(null);
        
        // Snap to a specific function
        if (functionIndex < fNo.getModel().getSize())
        {
            fNo.setSelectedIndex(functionIndex);
        }
                
        if (standalone)
        {
            this.applyButton.setVisible(false);
            this.jSeparator1.setVisible(false);
            this.fNo.setVisible(false);
            this.fNoLabel.setVisible(false);
            this.fIconlabel.setText(
                I18n.f("loc.ui.functionIconLoading", fNo.getSelectedIndex(), I18n.t("loc.ui.loadingMessage"))
            );
            this.setPreferredSize(new Dimension(390, 260));
        }
        else
        {
            this.applyButton.setVisible(true);
            this.jSeparator1.setVisible(true);
            this.fNo.setVisible(true);
            this.fNoLabel.setVisible(true);
            this.fIconlabel.setText(
                I18n.f("loc.ui.functionIconLoading", fNo.getSelectedIndex(), I18n.t("loc.ui.loadingMessage"))
            );
            this.setPreferredSize(new Dimension(360, 375));
        }
        
        // Show function icons across multiple columns
        BasicComboPopup popup = (BasicComboPopup) fIcon.getAccessibleContext().getAccessibleChild(0);
        JScrollPane scrollPane = (JScrollPane) popup.getComponent(0);
        JList list = (JList) scrollPane.getViewport().getView();
       
        // Fix missing column bug
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(0);
        list.setFixedCellWidth(TrainControlUI.BUTTON_ICON_WIDTH + 12); 
        list.setFixedCellHeight(TrainControlUI.BUTTON_ICON_WIDTH + 12);
        
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1); // Auto-calculate
        popup.setMinimumSize(new Dimension(300, 400));

        // Show custom icon and controls
        displayCustomizationButtons();
               
        this.customFunctionIcon.setMinimumSize(new Dimension(50, 33));
        
        // Button to copy icons
        if (this.parent.getCopyTarget() == null)
        {
            this.copyCustomizations.setEnabled(false);
            this.copyCustomizations.setText(
                I18n.t("loc.ui.copyIconsFromClipboardLocomotive")
            );
        }
        else
        {
            this.copyCustomizations.setEnabled(true);
            this.copyCustomizations.setText(
                I18n.f("loc.ui.copyIconsFromLocomotive", this.parent.getCopyTarget().getName())
            );
        }
        
        // Now load the actual icons        
        fIcon.setEnabled(false);
        
        javax.swing.SwingUtilities.invokeLater(() -> 
        {
            for (int i = 0; i <= this.loc.getNumFnIcons(); i++)
            {
                try
                {
                    // Use "active" icons for the CS3 for a better look
                    String targetURL = loc.getFunctionIconUrl(i, parent.getModel().isCS3() || !parent.getModel().getNetworkCommState(), true);
                    iconModel.set(i, new ImageIcon(parent.getLocImage(targetURL, TrainControlUI.BUTTON_ICON_WIDTH)));
                }
                catch (Exception e)
                {
                    iconModel.set(i, Integer.toString(i));
                    this.parent.getModel().logf("loc.ui.errorLoadingFunctionIcon", i);
                }
            }
                        
            fIcon.setModel(
                new javax.swing.DefaultComboBoxModel(iconModel.toArray(new Object[0])) 
            );
            
            // Highlight the right one and grey out if needed
            fIconlabel.setText(fIconlabel.getText().replace(" " + I18n.t("loc.ui.loadingMessage"), ""));
            fNoItemStateChanged(null);
            displayCustomizationButtons();
        });
        
        javax.swing.DefaultComboBoxModel<String> defaultModel = (javax.swing.DefaultComboBoxModel<String>) functionTriggerType.getModel();

        // Replace item at index 0 with localized "Toggle"
        defaultModel.removeElementAt(0);
        defaultModel.insertElementAt(I18n.t("function.ui.toggle"), 0);

        // Replace item at index 1 with localized "Momentary"
        defaultModel.removeElementAt(1);
        defaultModel.insertElementAt(I18n.t("function.ui.momentary"), 1);
    }
    
    /**
     * Manages the correct display of the custom icon buttons
     * @param funcNo 
     */
    private void displayCustomizationButtons()
    {
        this.customIconPath = this.loc.getLocalFunctionImageURL(this.getFNo());
        displayCustomFunctionIcon(this.customIconPath);
        
        if (this.loc.isCustomFunctions())
        {
            this.resetButton.setEnabled(true);
        }
        else
        {
            this.resetButton.setEnabled(false);
        }
    }
    
    public void focusFno()
    {
        this.fNo.requestFocus();
    }
    
    public void focusImages()
    {
        this.fIcon.requestFocus();
    }
    
    /**
     * External call to apply shown values
     */
    public void doApply()
    {
        applyButtonActionPerformed(null);
    }
    
    public Integer getFIcon()
    {
        return this.fIcon.getSelectedIndex();
    }
    
    public final Integer getFNo()
    {
        return this.fNo.getSelectedIndex();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fNoLabel = new javax.swing.JLabel();
        fNo = new javax.swing.JComboBox<>();
        fIcon = new javax.swing.JComboBox<>();
        fIconlabel = new javax.swing.JLabel();
        applyButton = new javax.swing.JButton();
        resetButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        fIconlabel1 = new javax.swing.JLabel();
        functionTriggerType = new javax.swing.JComboBox<>();
        customFunctionIcon = new javax.swing.JLabel();
        useCustomFunctionIcon = new javax.swing.JButton();
        deleteCustomIcon = new javax.swing.JButton();
        copyCustomizations = new javax.swing.JButton();

        setMinimumSize(new java.awt.Dimension(360, 260));
        setName(""); // NOI18N
        setPreferredSize(new java.awt.Dimension(360, 375));

        fNoLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        fNoLabel.setForeground(new java.awt.Color(0, 0, 115));
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/traincontrol/resources/messages"); // NOI18N
        fNoLabel.setText(bundle.getString("loc.ui.functionNumber")); // NOI18N

        fNo.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        fNo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        fNo.setMinimumSize(new java.awt.Dimension(100, 27));
        fNo.setPreferredSize(new java.awt.Dimension(100, 27));
        fNo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                fNoItemStateChanged(evt);
            }
        });

        fIcon.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        fIcon.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        fIcon.setMaximumSize(new java.awt.Dimension(300, 600));
        fIcon.setMinimumSize(new java.awt.Dimension(300, 27));
        fIcon.setPreferredSize(new java.awt.Dimension(300, 27));

        fIconlabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        fIconlabel.setForeground(new java.awt.Color(0, 0, 115));
        fIconlabel.setText(bundle.getString("loc.ui.functionIcon")); // NOI18N

        applyButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        applyButton.setText(bundle.getString("ui.apply")); // NOI18N
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyButtonActionPerformed(evt);
            }
        });

        resetButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        resetButton.setText(bundle.getString("loc.ui.resetAllCustomizations")); // NOI18N
        resetButton.setFocusable(false);
        resetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetButtonActionPerformed(evt);
            }
        });

        fIconlabel1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        fIconlabel1.setForeground(new java.awt.Color(0, 0, 115));
        fIconlabel1.setText(bundle.getString("loc.ui.duration")); // NOI18N

        functionTriggerType.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        functionTriggerType.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Toggle", "Momentary", "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s", "10s", "11s", "12s", "13s", "14s", "15s", "16s", "17s", "18s", "19s", "20s", "21s", "22s", "23s", "24s", "25s", "26s", "27s", "28s", "29s", "30s" }));

        useCustomFunctionIcon.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        useCustomFunctionIcon.setText(bundle.getString("loc.ui.useCustomIcon")); // NOI18N
        useCustomFunctionIcon.setFocusable(false);
        useCustomFunctionIcon.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useCustomFunctionIconActionPerformed(evt);
            }
        });

        deleteCustomIcon.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        deleteCustomIcon.setText(bundle.getString("loc.ui.clearCustomIcon")); // NOI18N
        deleteCustomIcon.setFocusable(false);
        deleteCustomIcon.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteCustomIconActionPerformed(evt);
            }
        });

        copyCustomizations.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        copyCustomizations.setText(bundle.getString("loc.ui.copyIconsFromClipboardLocomotive")); // NOI18N
        copyCustomizations.setToolTipText(bundle.getString("loc.ui.toolip.copyIconHelp")); // NOI18N
        copyCustomizations.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyCustomizationsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(applyButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSeparator1)
                    .addComponent(resetButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(fIcon, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(fNo, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(functionTriggerType, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(copyCustomizations, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(fIconlabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(useCustomFunctionIcon)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deleteCustomIcon))
                            .addComponent(fNoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(fIconlabel1)
                            .addComponent(customFunctionIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(15, 15, 15))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(fNoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fNo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fIconlabel1)
                .addGap(5, 5, 5)
                .addComponent(functionTriggerType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fIconlabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fIcon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(useCustomFunctionIcon)
                    .addComponent(deleteCustomIcon))
                .addGap(4, 4, 4)
                .addComponent(customFunctionIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(applyButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 3, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(8, 8, 8)
                .addComponent(resetButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(copyCustomizations))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void applyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButtonActionPerformed
       
        int fTriggerType;
        if (this.functionTriggerType.getSelectedIndex() == 0)
        {
            fTriggerType = Locomotive.FUNCTION_TOGGLE;
        }
        else if (this.functionTriggerType.getSelectedIndex() == 1)
        {
            fTriggerType = Locomotive.FUNCTION_PULSE;
        }
        else
        {
            fTriggerType = this.functionTriggerType.getSelectedIndex() - 1;
        } 
        
        this.applyCustomFunctionIcon();
        
        loc.setFunctionType(getFNo(), getFIcon(), fTriggerType);
        parent.repaintLoc(true, null);
            
        this.fNo.setSelectedIndex((this.fNo.getSelectedIndex() + 1) % this.fNo.getItemCount());
    }//GEN-LAST:event_applyButtonActionPerformed

    private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetButtonActionPerformed

        // ON THE EVENT THREAD (VB-B1), and the thread now holds the only slow thing here.
        //
        // The thread existed to hold a modal confirm - everything the answer led to was already being
        // marshalled straight back with invokeLater, which is the tell: the background thread was
        // doing no background work at all. What IS slow is syncWithCS2, which talks to the station,
        // so that is what runs off the event thread now.
        this.resetButton.setEnabled(false);

        int dialogResult = JOptionPane.showOptionDialog(
            this,
            I18n.t("loc.ui.confirmResetFunctionsToCentralStation"),
            I18n.t("loc.ui.dialogConfirmReset"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            TrainControlUI.YES_NO_OPTS,
            // NOT [0] (VB-B2): Yes here throws away every custom function and icon on this
            // locomotive, so it is not the answer a reflexive Enter should give.
            TrainControlUI.YES_NO_OPTS[1]
        );

        if (dialogResult == JOptionPane.YES_OPTION)
        {
            this.loc.setCustomFunctions(false);
            this.loc.unsetLocalFunctionImageURLs();
            this.parent.repaintLoc(true, null);
            this.customIconPath = null;

            updateFNumber(this.fNo.getSelectedIndex());

            new Thread(() -> this.parent.syncWithCS2()).start();
        }

        // The button was only disabled to keep a second dialog from opening, so restore its correct state
        displayCustomizationButtons();
    }//GEN-LAST:event_resetButtonActionPerformed

    /**
     * Told when the function this panel is editing changes (FR-045).
     *
     * The panel is a form, so anything added to it has to go through the designer - which is why the
     * autonomy slot ticks live on the DIALOG around it instead. They still have to follow the fNo
     * dropdown, because switching function inside the panel changes what a tick would be about, and a
     * tick about a function nobody is looking at is worse than no tick.
     *
     * A hook rather than a public getter the dialog polls: there is no moment the dialog could poll at.
     */
    private java.util.function.IntConsumer onFunctionChanged;

    /**
     * @param listener told the new function number whenever it changes, or null
     */
    public void setOnFunctionChanged(java.util.function.IntConsumer listener)
    {
        this.onFunctionChanged = listener;
    }

    private void updateFNumber(int targetFNo)
    {
        if (this.onFunctionChanged != null) this.onFunctionChanged.accept(targetFNo);

        this.fIcon.setSelectedIndex(this.loc.sanitizeFIconIndex(this.loc.getFunctionType(targetFNo)));
        
        if (loc.isFunctionTimed(targetFNo) > 0)
        {
            this.functionTriggerType.setSelectedIndex(Math.min(loc.isFunctionTimed(targetFNo) + 1, this.functionTriggerType.getItemCount() - 1));
        }
        else if (loc.isFunctionPulse(targetFNo))
        {
            this.functionTriggerType.setSelectedIndex(1);
        }
        else
        {
            this.functionTriggerType.setSelectedIndex(0);
        }  
        
        this.fIconlabel.setText(
            I18n.f("loc.ui.functionIconLoading", targetFNo, "")
        );
        
        displayCustomizationButtons();
    }
    
    private void fNoItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_fNoItemStateChanged
        
        updateFNumber(this.fNo.getSelectedIndex());      
    }//GEN-LAST:event_fNoItemStateChanged

    private void useCustomFunctionIconActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useCustomFunctionIconActionPerformed

        // NO THREAD AT ALL (VB-B1).
        //
        // A modal JFileChooser opened on a raw thread, and so did every setEnabled, the icon preview
        // and the repaint that followed it. Nothing in this handler is slow: it reads a preference,
        // opens a chooser, and shows the chosen image. The thread bought nothing and cost correctness.
        String currentPath = null;

        if (this.loc.getLocalFunctionImageURL(this.fNo.getSelectedIndex()) != null)
        {
            // Through a URI, because that is what the value IS (validation pass).
            //
            // `getLocalFunctionImageURL` returns what `.toUri().toString()` produced twenty lines
            // below, so `new File(String)` on it makes a path with the scheme still on the front,
            // which never exists.  currentPath was therefore always null and the chooser never
            // opened where the current icon lives - a silent no-op since the day it was written.
            // The identical mistake was fixed in TrainControlUI's locomotive-icon chooser; this is
            // its twin, one class over.
            File currentIcon = fileOf(this.loc.getLocalFunctionImageURL(this.fNo.getSelectedIndex()));

            if (currentIcon != null && currentIcon.exists())
            {
                currentPath = currentIcon.getParent();
            }
        }

        this.useCustomFunctionIcon.setEnabled(false);
        this.deleteCustomIcon.setEnabled(false);

        JFileChooser fc = new JFileChooser(
            currentPath != null ? currentPath : TrainControlUI.getPrefs().get(LAST_USED_ICON_FOLDER, new File(".").getAbsolutePath())
        );

        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileFilter filter = new FileNameExtensionFilter("JPEG, PNG, GIF, BMP " + I18n.t("ui.images"), "jpg", "png", "gif", "jpeg", "jpe", "bmp");
        fc.setFileFilter(filter);

        fc.setAcceptAllFileFilterUsed(false);

        int i = fc.showOpenDialog(this);

        if (i == JFileChooser.APPROVE_OPTION)
        {
            File f = fc.getSelectedFile();

            TrainControlUI.getPrefs().put(LAST_USED_ICON_FOLDER, f.getParent());

            customIconPath = Paths.get(f.getAbsolutePath()).toUri().toString();
            this.displayCustomFunctionIcon(customIconPath);
        }

        this.useCustomFunctionIcon.setEnabled(true);
        this.deleteCustomIcon.setEnabled(true);
        parent.repaintLoc(true, null);
    }//GEN-LAST:event_useCustomFunctionIconActionPerformed

    private void deleteCustomIconActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteCustomIconActionPerformed
        
        // This allows us to delay the change until the OK button is pressed
        this.customIconPath = "reset";       
        
        this.displayCustomFunctionIcon(null);
    }//GEN-LAST:event_deleteCustomIconActionPerformed

    private void copyCustomizationsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyCustomizationsActionPerformed
        
        Locomotive copyFrom = this.parent.getCopyTarget();
        
        if (copyFrom != null)
        {
            this.loc.setFunctionTypes(copyFrom.getFunctionTypes(), copyFrom.getFunctionTriggerTypes());
            this.loc.setCustomFunctions(true);
            parent.repaintLoc(true, null);

            updateFNumber(this.fNo.getSelectedIndex()); 
        }
    }//GEN-LAST:event_copyCustomizationsActionPerformed

    private void displayCustomFunctionIcon(String path)
    {       
        if (path == null)
        {
            this.customFunctionIcon.setIcon(null);
            this.fIcon.setEnabled(true);
            
            this.deleteCustomIcon.setVisible(false);
            this.useCustomFunctionIcon.setVisible(true);
        }
        else
        {        
            try
            {
                this.fIcon.setEnabled(false);
                
                this.deleteCustomIcon.setVisible(true);
                this.useCustomFunctionIcon.setVisible(false);
                
                this.customFunctionIcon.setIcon(
                    new ImageIcon(parent.getLocImage(path, TrainControlUI.BUTTON_ICON_WIDTH))
                );                
                
            }
            catch (Exception ex)
            {
                this.parent.getModel().logf("loc.ui.errorInvalidIconPath", path);
            }      
        }
    }
    
    private void applyCustomFunctionIcon()
    {       
        if ("reset".equals(this.customIconPath))
        {
            this.loc.unsetLocalFunctionImageURL(this.getFNo());
        }
        else if (this.customIconPath != null)
        {
            this.loc.setLocalFunctionImageURL(this.getFNo(), this.customIconPath);

            this.parent.getModel().logf(
                "loc.ui.logSetCustomFunctionIcon",
                loc.getName(),
                this.getFNo(),
                this.loc.getLocalFunctionImageURL(this.getFNo())
            );
            
            this.parent.repaintLoc(true, null);
        }
        
        this.customIconPath = null;
    }
        
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton applyButton;
    private javax.swing.JButton copyCustomizations;
    private javax.swing.JLabel customFunctionIcon;
    private javax.swing.JButton deleteCustomIcon;
    private javax.swing.JComboBox<Object> fIcon;
    private javax.swing.JLabel fIconlabel;
    private javax.swing.JLabel fIconlabel1;
    private javax.swing.JComboBox<String> fNo;
    private javax.swing.JLabel fNoLabel;
    private javax.swing.JComboBox<String> functionTriggerType;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton resetButton;
    private javax.swing.JButton useCustomFunctionIcon;
    // End of variables declaration//GEN-END:variables
}
