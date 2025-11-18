package org.traincontrol.gui;

import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.BadLocationException;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;

/**
 * UI for editing routes 
 */
public class RouteEditor extends PositionAwareJFrame
{        
    public static final String TURNOUT   = I18n.t("route.ui.turnout");
    public static final String STRAIGHT  = I18n.t("route.ui.straight");
    public static final String RED       = I18n.t("route.ui.red");
    public static final String GREEN     = I18n.t("route.ui.green");
    public static final String LEFT      = I18n.t("route.ui.left");
    public static final String STRAIGHT3 = I18n.t("route.ui.straight3");
    public static final String RIGHT     = I18n.t("route.ui.right");
    
    private final TrainControlUI parent;
    
    private final String originalRouteName;
    private final boolean edit;

    /**
     * Creates new form RouteEditor
     * @param windowTitle
     * @param parent
     * @param routeName
     * @param routeContent
     * @param isEnabled
     * @param s88
     * @param triggerType
     * @param conditionString
     * @param locked
     */
    public RouteEditor(String windowTitle, TrainControlUI parent, String routeName, String routeContent, boolean isEnabled, int s88, MarklinRoute.s88Triggers triggerType, String conditionString, boolean locked)
    {        
        initComponents();
        this.parent = parent;
        
        this.setTitle(windowTitle);
        
        edit = !"".equals(routeContent);
        originalRouteName = routeName;
                
        this.routeName.setText(routeName);
        this.routeContents.setText(routeContent);
        
        if (isEnabled)
        {
            this.executionAuto.setSelected(true);
        }
        else
        {
            this.executionManual.setSelected(true);
        }
        
        this.s88.setText(Integer.toString(s88));
        this.conditionAccs.setText(conditionString);

        if (triggerType == MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED)
        {
            this.triggerClearThenOccupied.setSelected(true);
        }
        else
        {
            this.triggerOccupiedThenClear.setSelected(true);
        }
        
        // Set locomotive list
        List<String> locs = new LinkedList<>(parent.getModel().getLocList());
     
        Collections.sort(locs);
        
        locNameList.setModel(
            new DefaultComboBoxModel(locs.toArray())
        );
        
        locNameListAuto.setModel(
            new DefaultComboBoxModel(locs.toArray())
        );
        
        locNameListItemStateChanged(null);
        
        if (!edit)
        {
            routeContents.setLineWrap(true);
            routeContents.setText("");

            routeContents.addMouseListener(new MouseListener()
            {
                @Override
                public void mouseClicked(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    /**if (routeContents.getText().contains(message))
                    {
                        routeContents.setText("");
                    }*/
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }
            });
        }
        
        this.updateSettingSelections();
        this.accState.setPrototypeDisplayValue("XXXXXXXXXXXXXX");
        this.captureCommands.setSelected(false);
        
        this.pack();
        
        // Only load location once
        if (!this.isLoaded())
        {
            loadWindowBounds();
        }
        
        saveWindowBounds();
        
        // Check if the route is locked
        this.saveButton.setEnabled(!locked);
        
        if (locked)
        {
            this.saveButton.setToolTipText(
                I18n.t("layout.ui.tooltipCentralStationRoutesCannotBeEditedDuplicateInstead")
            );
        }
        else
        {
            this.saveButton.setToolTipText("");
        }
        
        // Internationalize locomotive commands
        DefaultComboBoxModel<String> defaultModel = (DefaultComboBoxModel<String>) commandTypeList.getModel();

        defaultModel.removeElementAt(0);
        defaultModel.insertElementAt(I18n.t("route.ui.speed"), 0);

        defaultModel.removeElementAt(1);
        defaultModel.insertElementAt(I18n.t("route.ui.forward"), 1);

        defaultModel.removeElementAt(2);
        defaultModel.insertElementAt(I18n.t("route.ui.backward"), 2);
        
        this.setVisible(true);
        
        this.setAlwaysOnTop(parent.isAlwaysOnTop());
    }
    
    public JTextArea getConditionAccs()
    {
        return conditionAccs;
    }

    private JRadioButton getExecutionAuto()
    {
        return executionAuto;
    }

    private JRadioButton getExecutionManual()
    {
        return executionManual;
    }

    private JTextArea getRouteContents()
    {
        return routeContents;
    }

    private JTextField getRouteName()
    {
        return routeName;
    }

    private JTextField getS88()
    {
        return s88;
    }
    
    private JRadioButton getTriggerClearThenOccupied()
    {
        return triggerClearThenOccupied;
    }
    
    public void appendCommand(String command)
    {
        this.routeContents.setText(this.routeContents.getText().trim() + "\n" + command + "\n");
        this.routeContents.setText(filterConfigCommands(this.routeContents.getText()));
    }
    
    /**
     * Deduplicate lines
     * @param text
     * @return 
     */
    public static String filterConfigCommands(String text)
    {
        String[] lines = text.split("\n");
        Map<String, String> map = new LinkedHashMap<>();

        for (String line : lines)
        {
            String[] keyValue = line.split(",", 2); // Split on the first comma
            if (keyValue.length == 2)
            {
                String key = keyValue[0];
                String value = keyValue[1];
                map.put(key, value); // This keeps the latest value for each key
            }
            else
            {
                map.put(line, "");
            }
        }

        StringBuilder filteredCommands = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet())
        {
            if ("".equals(entry.getValue()))
            {
                filteredCommands.append(entry.getKey()).append("\n");
            }
            else
            {
                filteredCommands.append(entry.getKey()).append(",").append(entry.getValue()).append("\n");
            }
        }

        return filteredCommands.toString().trim();
    }
        
    public boolean isCaptureCommandsSelected()
    {
        return this.captureCommands.isSelected();
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
        buttonGroup2 = new javax.swing.ButtonGroup();
        buttonGroup3 = new javax.swing.ButtonGroup();
        buttonGroup4 = new javax.swing.ButtonGroup();
        buttonGroup5 = new javax.swing.ButtonGroup();
        buttonGroup6 = new javax.swing.ButtonGroup();
        routeName = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        s88 = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        triggerClearThenOccupied = new javax.swing.JRadioButton();
        triggerOccupiedThenClear = new javax.swing.JRadioButton();
        jLabel5 = new javax.swing.JLabel();
        executionManual = new javax.swing.JRadioButton();
        executionAuto = new javax.swing.JRadioButton();
        jLabel9 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        jScrollPane3 = new javax.swing.JScrollPane();
        conditionAccs = new javax.swing.JTextArea();
        testButton = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        routeContents = new javax.swing.JTextArea();
        addStopCommand = new javax.swing.JButton();
        captureCommands = new javax.swing.JCheckBox();
        jLabel7 = new javax.swing.JLabel();
        Help = new javax.swing.JButton();
        saveButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        Logic = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        accPanel = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        accAddr = new javax.swing.JTextField();
        addToRouteButton = new javax.swing.JButton();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        accState = new javax.swing.JComboBox<>();
        accTypeTurnout = new javax.swing.JRadioButton();
        accType3Way = new javax.swing.JRadioButton();
        accTypeSignal = new javax.swing.JRadioButton();
        jLabel12 = new javax.swing.JLabel();
        delay = new javax.swing.JTextField();
        addToRouteButton1 = new javax.swing.JButton();
        jSeparator3 = new javax.swing.JSeparator();
        MM2 = new javax.swing.JRadioButton();
        DCC = new javax.swing.JRadioButton();
        jLabel17 = new javax.swing.JLabel();
        locPanel = new javax.swing.JPanel();
        s88Panel1 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        addLocCommand = new javax.swing.JButton();
        commandTypeList = new javax.swing.JComboBox<>();
        locFuncStateLabel = new javax.swing.JLabel();
        locNameList = new javax.swing.JComboBox<>();
        locDelay = new javax.swing.JTextField();
        locFuncOn = new javax.swing.JRadioButton();
        locFuncOff = new javax.swing.JRadioButton();
        jLabel21 = new javax.swing.JLabel();
        locSpeedSlider = new javax.swing.JSlider();
        jSeparator2 = new javax.swing.JSeparator();
        jLabel19 = new javax.swing.JLabel();
        s88CondPanel = new javax.swing.JPanel();
        s88CPanel = new javax.swing.JPanel();
        s88CondAddr = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        s88Occupied = new javax.swing.JRadioButton();
        s88Clear = new javax.swing.JRadioButton();
        addS88Condition = new javax.swing.JButton();
        autoCPanel = new javax.swing.JPanel();
        jLabel20 = new javax.swing.JLabel();
        locNameListAuto = new javax.swing.JComboBox<>();
        jLabel22 = new javax.swing.JLabel();
        autoLocS88 = new javax.swing.JTextField();
        addAutoLocCondition = new javax.swing.JButton();
        cLogicPanel = new javax.swing.JPanel();
        addGroup = new javax.swing.JButton();
        addOR = new javax.swing.JButton();
        jLabel13 = new javax.swing.JLabel();
        addAND = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Route Editor");
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setResizable(false);

        routeName.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        routeName.setText("jTextField1");
        routeName.setMinimumSize(new java.awt.Dimension(159, 26));
        routeName.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                routeNameKeyReleased(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(0, 0, 115));
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/traincontrol/resources/messages"); // NOI18N
        jLabel1.setText(bundle.getString("route.ui.routeName")); // NOI18N

        jLabel2.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(0, 0, 115));
        jLabel2.setText(bundle.getString("route.ui.editingWizard")); // NOI18N

        jPanel2.setBackground(new java.awt.Color(245, 245, 245));
        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel2.setFocusable(false);
        jPanel2.setMinimumSize(new java.awt.Dimension(259, 196));
        jPanel2.setName(""); // NOI18N

        jLabel4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(0, 0, 115));
        jLabel4.setText(bundle.getString("route.ui.trigS88")); // NOI18N

        s88.setColumns(6);
        s88.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        s88.setMaximumSize(new java.awt.Dimension(90, 26));
        s88.setMinimumSize(new java.awt.Dimension(90, 26));
        s88.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                s88KeyReleased(evt);
            }
        });

        jLabel3.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(0, 0, 115));
        jLabel3.setText(bundle.getString("route.ui.trigCondition")); // NOI18N

        buttonGroup1.add(triggerClearThenOccupied);
        triggerClearThenOccupied.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        triggerClearThenOccupied.setSelected(true);
        triggerClearThenOccupied.setText(bundle.getString("route.ui.clearThenOccupied")); // NOI18N
        triggerClearThenOccupied.setFocusable(false);

        buttonGroup1.add(triggerOccupiedThenClear);
        triggerOccupiedThenClear.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        triggerOccupiedThenClear.setText(bundle.getString("route.ui.occupiedThenClear")); // NOI18N
        triggerOccupiedThenClear.setFocusable(false);

        jLabel5.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(0, 0, 115));
        jLabel5.setText(bundle.getString("route.ui.automaticExec")); // NOI18N

        buttonGroup2.add(executionManual);
        executionManual.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        executionManual.setText(bundle.getString("ui.off")); // NOI18N
        executionManual.setFocusable(false);

        buttonGroup2.add(executionAuto);
        executionAuto.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        executionAuto.setText(bundle.getString("ui.on")); // NOI18N
        executionAuto.setFocusable(false);

        jLabel9.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(0, 0, 115));
        jLabel9.setText(bundle.getString("route.ui.optionalConditions")); // NOI18N

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        conditionAccs.setColumns(11);
        conditionAccs.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        conditionAccs.setRows(3);
        conditionAccs.setToolTipText("");
        conditionAccs.setPreferredSize(null);
        jScrollPane3.setViewportView(conditionAccs);

        testButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        testButton.setText(bundle.getString("ui.test")); // NOI18N
        testButton.setFocusable(false);
        testButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(triggerOccupiedThenClear, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(triggerClearThenOccupied, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jLabel4)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(s88, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel3)
                            .addComponent(jLabel5)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(executionManual)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(executionAuto))
                            .addComponent(testButton))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(18, 18, 18)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 11, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addGap(99, 99, 99))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 228, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator1)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(s88, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, 0)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(triggerClearThenOccupied)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(triggerOccupiedThenClear)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(executionManual)
                            .addComponent(executionAuto))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 36, Short.MAX_VALUE)
                        .addComponent(testButton))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane3)))
                .addContainerGap())
        );

        jLabel6.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel6.setForeground(new java.awt.Color(0, 0, 115));
        jLabel6.setText(bundle.getString("route.ui.optionalSettings")); // NOI18N

        jPanel5.setBackground(new java.awt.Color(245, 245, 245));
        jPanel5.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel5.setFocusable(false);
        jPanel5.setMinimumSize(new java.awt.Dimension(259, 196));
        jPanel5.setName(""); // NOI18N

        routeContents.setColumns(20);
        routeContents.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        routeContents.setRows(5);
        jScrollPane1.setViewportView(routeContents);

        addStopCommand.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addStopCommand.setText(bundle.getString("route.ui.addSpecialCommand")); // NOI18N
        addStopCommand.setToolTipText(bundle.getString("route.ui.tooltip.addSpecialCommand")); // NOI18N
        addStopCommand.setFocusable(false);
        addStopCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addStopCommandActionPerformed(evt);
            }
        });

        captureCommands.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        captureCommands.setText(bundle.getString("autolayout.ui.captureCommands")); // NOI18N
        captureCommands.setToolTipText(bundle.getString("autolayout.ui.tooltip.captureCommands")); // NOI18N
        captureCommands.setFocusable(false);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(addStopCommand)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(captureCommands)))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addContainerGap(14, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 189, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addStopCommand)
                    .addComponent(captureCommands))
                .addGap(7, 7, 7))
        );

        jLabel7.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel7.setForeground(new java.awt.Color(0, 0, 115));
        jLabel7.setText(bundle.getString("route.ui.routeCommands")); // NOI18N

        Help.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        Help.setText(bundle.getString("ui.help")); // NOI18N
        Help.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                HelpActionPerformed(evt);
            }
        });

        saveButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        saveButton.setText(bundle.getString("route.ui.saveChanges")); // NOI18N
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        cancelButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        cancelButton.setText(bundle.getString("ui.cancel")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        Logic.setBackground(new java.awt.Color(255, 255, 255));
        Logic.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        Logic.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        Logic.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                LogicStateChanged(evt);
            }
        });

        accPanel.setBackground(new java.awt.Color(245, 245, 245));
        accPanel.setFocusable(false);
        accPanel.setMinimumSize(new java.awt.Dimension(259, 196));
        accPanel.setName(""); // NOI18N

        jLabel8.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(0, 0, 115));
        jLabel8.setText(bundle.getString("route.ui.accessoryAddress")); // NOI18N

        accAddr.setColumns(6);
        accAddr.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accAddr.setMaximumSize(new java.awt.Dimension(90, 26));
        accAddr.setMinimumSize(new java.awt.Dimension(90, 26));
        accAddr.setPreferredSize(new java.awt.Dimension(90, 26));
        accAddr.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                accAddrKeyReleased(evt);
            }
        });

        addToRouteButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addToRouteButton.setText(bundle.getString("route.ui.addToRoute")); // NOI18N
        addToRouteButton.setFocusable(false);
        addToRouteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addToRouteButtonActionPerformed(evt);
            }
        });

        jLabel10.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(0, 0, 115));
        jLabel10.setText(bundle.getString("route.ui.accessoryType")); // NOI18N

        jLabel11.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(0, 0, 115));
        jLabel11.setText(bundle.getString("route.ui.state")); // NOI18N

        accState.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accState.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Turnout", "Signal", "3-way Turnout" }));
        accState.setMaximumSize(new java.awt.Dimension(136, 26));
        accState.setName(""); // NOI18N

        buttonGroup4.add(accTypeTurnout);
        accTypeTurnout.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accTypeTurnout.setSelected(true);
        accTypeTurnout.setText(bundle.getString("route.ui.switch")); // NOI18N
        accTypeTurnout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accTypeTurnoutActionPerformed(evt);
            }
        });

        buttonGroup4.add(accType3Way);
        accType3Way.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accType3Way.setText(bundle.getString("route.ui.switchThreeWay")); // NOI18N
        accType3Way.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accType3WayActionPerformed(evt);
            }
        });

        buttonGroup4.add(accTypeSignal);
        accTypeSignal.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accTypeSignal.setText(bundle.getString("route.ui.signal")); // NOI18N
        accTypeSignal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accTypeSignalActionPerformed(evt);
            }
        });

        jLabel12.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(0, 0, 115));
        jLabel12.setText(bundle.getString("route.ui.delayMS")); // NOI18N

        delay.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        delay.setText("0");
        delay.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                delayKeyReleased(evt);
            }
        });

        addToRouteButton1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addToRouteButton1.setText(bundle.getString("route.ui.addAsCondition")); // NOI18N
        addToRouteButton1.setFocusable(false);
        addToRouteButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addToRouteButton1ActionPerformed(evt);
            }
        });

        jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);

        buttonGroup6.add(MM2);
        MM2.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        MM2.setSelected(true);
        MM2.setText("MM2");

        buttonGroup6.add(DCC);
        DCC.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        DCC.setText("DCC");

        jLabel17.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(0, 0, 115));
        jLabel17.setText(bundle.getString("route.ui.protocol")); // NOI18N

        javax.swing.GroupLayout accPanelLayout = new javax.swing.GroupLayout(accPanel);
        accPanel.setLayout(accPanelLayout);
        accPanelLayout.setHorizontalGroup(
            accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, accPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(accAddr, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel10)
                    .addGroup(accPanelLayout.createSequentialGroup()
                        .addComponent(accTypeTurnout)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(accTypeSignal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(accType3Way)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(accState, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(accPanelLayout.createSequentialGroup()
                        .addComponent(MM2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(DCC))
                    .addComponent(jLabel17))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 58, Short.MAX_VALUE)
                .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, 12, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel12)
                    .addComponent(delay, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(addToRouteButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(addToRouteButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        accPanelLayout.setVerticalGroup(
            accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(accPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(accPanelLayout.createSequentialGroup()
                        .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel12)
                            .addComponent(addToRouteButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(addToRouteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(delay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(accPanelLayout.createSequentialGroup()
                        .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel8)
                            .addComponent(jLabel10)
                            .addComponent(jLabel11)
                            .addComponent(jLabel17))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(accPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(accAddr, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(accTypeTurnout)
                            .addComponent(accType3Way)
                            .addComponent(accTypeSignal)
                            .addComponent(accState, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(MM2)
                            .addComponent(DCC)))
                    .addComponent(jSeparator3))
                .addGap(36, 36, 36))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(accPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(accPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        Logic.addTab(bundle.getString("route.ui.accessoryCommands"), jPanel1); // NOI18N

        s88Panel1.setBackground(new java.awt.Color(245, 245, 245));

        jLabel18.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel18.setForeground(new java.awt.Color(0, 0, 115));
        jLabel18.setText(bundle.getString("route.ui.locomotiveName")); // NOI18N

        addLocCommand.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addLocCommand.setText(bundle.getString("route.ui.addToRoute")); // NOI18N
        addLocCommand.setFocusable(false);
        addLocCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addLocCommandActionPerformed(evt);
            }
        });

        commandTypeList.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        commandTypeList.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Speed", "Forward", "Backward", "F0", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24", "F25", "F26", "F27", "F28", "F29", "F30", "F31" }));
        commandTypeList.setMaximumSize(new java.awt.Dimension(136, 26));
        commandTypeList.setName(""); // NOI18N
        commandTypeList.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                commandTypeListItemStateChanged(evt);
            }
        });

        locFuncStateLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locFuncStateLabel.setForeground(new java.awt.Color(0, 0, 115));
        locFuncStateLabel.setText(bundle.getString("route.ui.functionState")); // NOI18N

        locNameList.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locNameList.setMaximumSize(new java.awt.Dimension(136, 26));
        locNameList.setName(""); // NOI18N
        locNameList.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                locNameListItemStateChanged(evt);
            }
        });

        locDelay.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locDelay.setText("0");
        locDelay.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                locDelayKeyReleased(evt);
            }
        });

        buttonGroup3.add(locFuncOn);
        locFuncOn.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locFuncOn.setSelected(true);
        locFuncOn.setText(bundle.getString("ui.on")); // NOI18N

        buttonGroup3.add(locFuncOff);
        locFuncOff.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locFuncOff.setText(bundle.getString("ui.off")); // NOI18N

        jLabel21.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel21.setForeground(new java.awt.Color(0, 0, 115));
        jLabel21.setText(bundle.getString("route.ui.commandType")); // NOI18N

        locSpeedSlider.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locSpeedSlider.setMajorTickSpacing(20);
        locSpeedSlider.setMinorTickSpacing(10);
        locSpeedSlider.setPaintLabels(true);
        locSpeedSlider.setPaintTicks(true);

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);

        jLabel19.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(0, 0, 115));
        jLabel19.setText(bundle.getString("route.ui.delayMS")); // NOI18N

        javax.swing.GroupLayout s88Panel1Layout = new javax.swing.GroupLayout(s88Panel1);
        s88Panel1.setLayout(s88Panel1Layout);
        s88Panel1Layout.setHorizontalGroup(
            s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(s88Panel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel18, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(locNameList, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel21, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(commandTypeList, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(s88Panel1Layout.createSequentialGroup()
                        .addComponent(locFuncOn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(locFuncOff))
                    .addComponent(locFuncStateLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(locSpeedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 242, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 12, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(s88Panel1Layout.createSequentialGroup()
                        .addComponent(locDelay, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(addLocCommand, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel19))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        s88Panel1Layout.setVerticalGroup(
            s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(s88Panel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(locSpeedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(s88Panel1Layout.createSequentialGroup()
                        .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jLabel18, javax.swing.GroupLayout.Alignment.TRAILING)
                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(locFuncStateLabel)
                                    .addComponent(jLabel21)))
                            .addComponent(jLabel19))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(s88Panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(commandTypeList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(locNameList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(locFuncOn)
                            .addComponent(locFuncOff)
                            .addComponent(locDelay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(addLocCommand, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jSeparator2))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout locPanelLayout = new javax.swing.GroupLayout(locPanel);
        locPanel.setLayout(locPanelLayout);
        locPanelLayout.setHorizontalGroup(
            locPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(s88Panel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        locPanelLayout.setVerticalGroup(
            locPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(locPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(s88Panel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        Logic.addTab(bundle.getString("route.ui.locCommands"), locPanel); // NOI18N

        s88CPanel.setBackground(new java.awt.Color(245, 245, 245));

        s88CondAddr.setColumns(6);
        s88CondAddr.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        s88CondAddr.setMaximumSize(new java.awt.Dimension(90, 26));
        s88CondAddr.setMinimumSize(new java.awt.Dimension(90, 26));
        s88CondAddr.setPreferredSize(new java.awt.Dimension(113, 26));
        s88CondAddr.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                s88CondAddrKeyReleased(evt);
            }
        });

        jLabel15.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel15.setForeground(new java.awt.Color(0, 0, 115));
        jLabel15.setText(bundle.getString("route.ui.s88Address")); // NOI18N

        jLabel16.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel16.setForeground(new java.awt.Color(0, 0, 115));
        jLabel16.setText(bundle.getString("route.ui.s88State")); // NOI18N

        buttonGroup5.add(s88Occupied);
        s88Occupied.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        s88Occupied.setSelected(true);
        s88Occupied.setText(bundle.getString("route.ui.occupied1")); // NOI18N

        buttonGroup5.add(s88Clear);
        s88Clear.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        s88Clear.setText(bundle.getString("route.ui.clear0")); // NOI18N

        addS88Condition.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addS88Condition.setText(bundle.getString("route.ui.addS88Condition")); // NOI18N
        addS88Condition.setFocusable(false);
        addS88Condition.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addS88ConditionActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout s88CPanelLayout = new javax.swing.GroupLayout(s88CPanel);
        s88CPanel.setLayout(s88CPanelLayout);
        s88CPanelLayout.setHorizontalGroup(
            s88CPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(s88CPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(s88CPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(s88CondAddr, javax.swing.GroupLayout.PREFERRED_SIZE, 116, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(s88CPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(s88CPanelLayout.createSequentialGroup()
                        .addComponent(jLabel16)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(s88CPanelLayout.createSequentialGroup()
                        .addComponent(s88Occupied)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(s88Clear)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 478, Short.MAX_VALUE)
                        .addComponent(addS88Condition)))
                .addContainerGap())
        );
        s88CPanelLayout.setVerticalGroup(
            s88CPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(s88CPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(s88CPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(s88CPanelLayout.createSequentialGroup()
                        .addComponent(jLabel16)
                        .addGap(7, 7, 7)
                        .addGroup(s88CPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(s88Occupied)
                            .addComponent(s88Clear)
                            .addComponent(addS88Condition, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(s88CPanelLayout.createSequentialGroup()
                        .addComponent(jLabel15)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(s88CondAddr, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout s88CondPanelLayout = new javax.swing.GroupLayout(s88CondPanel);
        s88CondPanel.setLayout(s88CondPanelLayout);
        s88CondPanelLayout.setHorizontalGroup(
            s88CondPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(s88CPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        s88CondPanelLayout.setVerticalGroup(
            s88CondPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(s88CondPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(s88CPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        Logic.addTab(bundle.getString("route.ui.s88ConditionsPanel"), s88CondPanel); // NOI18N

        jLabel20.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel20.setForeground(new java.awt.Color(0, 0, 115));
        jLabel20.setText(bundle.getString("route.ui.locName")); // NOI18N

        locNameListAuto.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locNameListAuto.setMaximumSize(new java.awt.Dimension(136, 26));
        locNameListAuto.setName(""); // NOI18N
        locNameListAuto.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                locNameListAutoItemStateChanged(evt);
            }
        });

        jLabel22.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel22.setForeground(new java.awt.Color(0, 0, 115));
        jLabel22.setText(bundle.getString("route.ui.locationS88Sensor")); // NOI18N

        autoLocS88.setMaximumSize(new java.awt.Dimension(90, 26));
        autoLocS88.setMinimumSize(new java.awt.Dimension(90, 26));
        autoLocS88.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                autoLocS88KeyReleased(evt);
            }
        });

        addAutoLocCondition.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addAutoLocCondition.setText(bundle.getString("route.ui.addCondition")); // NOI18N
        addAutoLocCondition.setToolTipText(bundle.getString("route.ui.tooltip.addConditionHelp")); // NOI18N
        addAutoLocCondition.setFocusable(false);
        addAutoLocCondition.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addAutoLocConditionActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout autoCPanelLayout = new javax.swing.GroupLayout(autoCPanel);
        autoCPanel.setLayout(autoCPanelLayout);
        autoCPanelLayout.setHorizontalGroup(
            autoCPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autoCPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autoCPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel20)
                    .addComponent(locNameListAuto, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(autoCPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(autoLocS88, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel22, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 467, Short.MAX_VALUE)
                .addComponent(addAutoLocCondition)
                .addContainerGap())
        );
        autoCPanelLayout.setVerticalGroup(
            autoCPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autoCPanelLayout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addGroup(autoCPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel22)
                    .addComponent(jLabel20))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(autoCPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locNameListAuto, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(autoLocS88, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addAutoLocCondition, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(14, Short.MAX_VALUE))
        );

        Logic.addTab(bundle.getString("route.ui.autonomyConditionsPanel"), autoCPanel); // NOI18N

        addGroup.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addGroup.setText(bundle.getString("route.ui.groupHighlighted")); // NOI18N
        addGroup.setToolTipText(bundle.getString("route.ui.tooltip.groupHighlighted")); // NOI18N
        addGroup.setFocusable(false);
        addGroup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addGroupActionPerformed(evt);
            }
        });

        addOR.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addOR.setText(bundle.getString("route.ui.insertOr")); // NOI18N
        addOR.setFocusable(false);
        addOR.setMaximumSize(new java.awt.Dimension(130, 24));
        addOR.setMinimumSize(new java.awt.Dimension(130, 24));
        addOR.setPreferredSize(new java.awt.Dimension(130, 24));
        addOR.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addORActionPerformed(evt);
            }
        });

        jLabel13.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(0, 0, 115));
        jLabel13.setText(bundle.getString("route.ui.tooltip.conditionLogicHelp")); // NOI18N

        addAND.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addAND.setText(bundle.getString("route.ui.insertAnd")); // NOI18N
        addAND.setFocusable(false);
        addAND.setMaximumSize(new java.awt.Dimension(130, 24));
        addAND.setMinimumSize(new java.awt.Dimension(130, 24));
        addAND.setPreferredSize(new java.awt.Dimension(130, 24));
        addAND.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addANDActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout cLogicPanelLayout = new javax.swing.GroupLayout(cLogicPanel);
        cLogicPanel.setLayout(cLogicPanelLayout);
        cLogicPanelLayout.setHorizontalGroup(
            cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, cLogicPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel13, javax.swing.GroupLayout.DEFAULT_SIZE, 946, Short.MAX_VALUE)
                    .addGroup(cLogicPanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(addAND, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(addOR, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(addGroup)))
                .addContainerGap())
        );
        cLogicPanelLayout.setVerticalGroup(
            cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cLogicPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel13)
                .addGap(15, 15, 15)
                .addGroup(cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAND, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addOR, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        Logic.addTab(bundle.getString("route.ui.conditionLogicPanel"), cLogicPanel); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(routeName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Help, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel7)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel6)
                            .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(Logic)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(saveButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cancelButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel1))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(routeName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Help, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(Logic, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveButton)
                    .addComponent(cancelButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        Logic.getAccessibleContext().setAccessibleName(bundle.getString("route.ui.accessoryCommands")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void HelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_HelpActionPerformed
        JOptionPane.showMessageDialog(this, I18n.t("route.ui.helpMessage"));
    }//GEN-LAST:event_HelpActionPerformed

    private String getProtocol()
    {
        if (this.DCC.isSelected())
        {
            return this.DCC.getText();
        }
        
        return this.MM2.getText();
    }
    
    private void addAcc(boolean isConditional)
    {
        String newEntry = "\n";
        
        if (!"".equals(this.accAddr.getText()))
        {            
            try
            {
                int address = Math.abs(Integer.parseInt(this.accAddr.getText()));
                
                String delayString = "";
                
                if (!"".equals(this.delay.getText()) && !isConditional)
                {
                    int delayVal = Math.abs(Integer.parseInt(this.delay.getText()));

                    if (delayVal > 0)
                    {
                        delayString = "," + delayVal;
                    }
                }
                
                if (this.accType3Way.isSelected())
                {
                    if (this.accState.getSelectedItem().toString().equals(STRAIGHT3))
                    {
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address, getProtocol(), false
                        ) + delayString + "\n";
                        
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address + 1, getProtocol(), false
                        );
                    }
                    else if (this.accState.getSelectedItem().toString().equals(LEFT))
                    {
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address, getProtocol(), true
                        ) + delayString + "\n";
                        
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address + 1, getProtocol(), false
                        );               
                    }
                    else if (this.accState.getSelectedItem().toString().equals(RIGHT))
                    {
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address, getProtocol(), false
                        ) + delayString + "\n";
                        
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address + 1, getProtocol(), true
                        );
                    }
                }
                else if (this.accTypeTurnout.isSelected())
                {
                    newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address, getProtocol(),
                            this.accState.getSelectedItem().toString().equals(TURNOUT)
                    ) + delayString;   
                }
                else if (this.accTypeSignal.isSelected())
                {
                    newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SIGNAL, address, getProtocol(), 
                            this.accState.getSelectedItem().toString().equals(RED)
                    ) + delayString;
                }
                
                if (!isConditional)
                {
                    this.routeContents.setText((this.routeContents.getText() + newEntry).trim());
                }
                else
                {
                    this.conditionAccs.setText((this.conditionAccs.getText().trim() + (this.conditionAccs.getText().trim().isEmpty() ? "" : "\nAND ") + newEntry).trim());
                }
                
                this.accAddr.setText("");
            }
            catch (Exception e)
            {
                this.parent.getModel().log(e);
                JOptionPane.showMessageDialog(
                    this,
                    I18n.t("route.ui.errorInvalidAddressMustBeInteger")
                );
                this.accAddr.setText("");
            }
        }
    }
    
    private void addToRouteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addToRouteButtonActionPerformed
        addAcc(false);
    }//GEN-LAST:event_addToRouteButtonActionPerformed

    private void accTypeTurnoutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_accTypeTurnoutActionPerformed
        updateSettingSelections();
    }//GEN-LAST:event_accTypeTurnoutActionPerformed

    private void accType3WayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_accType3WayActionPerformed
        updateSettingSelections();
    }//GEN-LAST:event_accType3WayActionPerformed

    private void accTypeSignalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_accTypeSignalActionPerformed
        updateSettingSelections();
    }//GEN-LAST:event_accTypeSignalActionPerformed

    private void accAddrKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_accAddrKeyReleased
        TrainControlUI.validateInt(evt, false);
        TrainControlUI.limitLength(evt, 5);
    }//GEN-LAST:event_accAddrKeyReleased

    private void s88KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_s88KeyReleased
        TrainControlUI.validateInt(evt, false);
        TrainControlUI.limitLength(evt, 5);       
    }//GEN-LAST:event_s88KeyReleased

    private void delayKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_delayKeyReleased
         TrainControlUI.validateInt(evt, false);
         TrainControlUI.limitLength(evt, 6);
    }//GEN-LAST:event_delayKeyReleased

    private void addStopCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addStopCommandActionPerformed
        
        String[] options = { RouteCommand.RouteCommandLightsOn().toString(),
            RouteCommand.RouteCommandAutonomyLightsOn().toString(),
            RouteCommand.RouteCommandFunctionsOff().toString(), 
            RouteCommand.RouteCommandStop().toString(),
            RouteCommand.COMMAND_ROUTE_PREFIX
        };
        
        String selectedValue = (String) JOptionPane.showInputDialog(
            this,
            I18n.t("route.ui.promptAddToRoute"),
            I18n.t("route.ui.dialogSpecialRouteCommands"),
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        // Prompt the user to choose the route to invoke
        if (RouteCommand.COMMAND_ROUTE_PREFIX.equals(selectedValue))
        {
            // Get list of routes
            List<String> routes = this.parent.getModel().getRouteList();

            if (routes.isEmpty())
            {
                JOptionPane.showMessageDialog(
                    this,
                    I18n.t("route.ui.errorNoOtherRoutesInDatabaseAddFirst")
                );
                this.parent.getModel().logf(
                    I18n.f("route.ui.logNoOtherRoutesInDatabase")
                );
                return;
            }

            // Show second dialog to select a route
            String selectedRoute = (String) JOptionPane.showInputDialog(
                this,
                I18n.t("route.ui.promptChooseRoute"),
                I18n.t("route.ui.dialogSelectRoute"),
                JOptionPane.QUESTION_MESSAGE,
                null,
                routes.toArray(new String[0]),
                routes.isEmpty() ? null : routes.get(0)
            );

            if (selectedRoute != null)
            {
                // Get route ID and append to selectedValue
                String otherRouteName = this.parent.getModel().getRoute(selectedRoute).getName();
                selectedValue = selectedValue + " " + otherRouteName;

                if (isEdit())
                {
                    MarklinRoute thisRoute = this.parent.getModel().getRoute(this.getOriginalRouteName());

                    if (thisRoute != null && thisRoute.getName().equals(otherRouteName))
                    {
                        JOptionPane.showMessageDialog(
                            this,
                            I18n.t("route.ui.errorRouteCannotTriggerItselfPickDifferent")
                        );
                        this.parent.getModel().logf(
                            I18n.f("route.ui.logRouteTriedToTriggerItself", otherRouteName)
                        );
                        return;
                    }
                }
            }
            else
            {
                return;
            }
        }
        
        if (selectedValue != null)
        {              
            try 
            {
                int caretPosition = routeContents.getCaretPosition();
                int lineNumber = routeContents.getLineOfOffset(caretPosition);
                int startOfLine = routeContents.getLineStartOffset(lineNumber);
                int endOfLine = routeContents.getLineEndOffset(lineNumber);

                // First line or start of line - add here
                if (startOfLine == endOfLine - 1 || caretPosition == 0)
                {
                    routeContents.insert(selectedValue + "\n", caretPosition);
                }
                // Add as next line
                else
                {
                    int insertPosition = routeContents.getLineEndOffset(lineNumber);
                    routeContents.insert("\n" + selectedValue + "\n", insertPosition);
                }
            }
            catch (BadLocationException ex)
            {
                this.routeContents.setText((this.routeContents.getText() + "\n" + selectedValue).trim());
            }   
            
            // Clean up
            this.routeContents.setText(this.routeContents.getText().replaceAll("\n{2,}", "\n").trim());
        }
    }//GEN-LAST:event_addStopCommandActionPerformed

    private void addToRouteButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addToRouteButton1ActionPerformed
        addAcc(true);
    }//GEN-LAST:event_addToRouteButton1ActionPerformed

    private void s88CondAddrKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_s88CondAddrKeyReleased
        TrainControlUI.validateInt(evt, false);
        TrainControlUI.limitLength(evt, 5);
    }//GEN-LAST:event_s88CondAddrKeyReleased

    private void addS88ConditionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addS88ConditionActionPerformed
        
        String newEntry = "\n";
        
        if (!"".equals(this.s88CondAddr.getText()))
        {            
            try
            {
                int address = Math.abs(Integer.parseInt(this.s88CondAddr.getText()));
                                
                newEntry += RouteCommand.RouteCommandFeedback(address, this.s88Occupied.isSelected()).toLine(null);
                
                this.conditionAccs.setText((this.conditionAccs.getText().trim() + (this.conditionAccs.getText().trim().isEmpty() ? "" : "\nAND ") + newEntry).trim());

                this.s88CondAddr.setText("");
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(
                    this,
                    I18n.t("route.ui.errorInvalidAddressMustBeInteger")
                );                
                this.s88CondAddr.setText("");
            }
        }
    }//GEN-LAST:event_addS88ConditionActionPerformed

    private void routeNameKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_routeNameKeyReleased
        TrainControlUI.limitLength(evt, 60);
    }//GEN-LAST:event_routeNameKeyReleased

    private void addLocCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addLocCommandActionPerformed
        
        if (locNameList.getModel().getSize() > 0)
        {
            // Sanity check
            if (((String) locNameList.getSelectedItem()).contains(","))
            {
                JOptionPane.showMessageDialog(this, I18n.t("route.ui.errorCommaLoc"));
                return;
            }
            
            RouteCommand rc;
            if (commandTypeList.getSelectedIndex() == 0)
            {
                rc = RouteCommand.RouteCommandLocomotiveSpeed((String) locNameList.getSelectedItem(), locSpeedSlider.getValue());
            }
            else if (commandTypeList.getSelectedIndex() == 1)
            {
                rc = RouteCommand.RouteCommandLocomotiveDirection((String) locNameList.getSelectedItem(), Locomotive.locDirection.DIR_FORWARD);
            }
            else if (commandTypeList.getSelectedIndex() == 2)
            {
                rc = RouteCommand.RouteCommandLocomotiveDirection((String) locNameList.getSelectedItem(), Locomotive.locDirection.DIR_BACKWARD);
            }
            else
            {
                rc = RouteCommand.RouteCommandFunction((String) locNameList.getSelectedItem(), commandTypeList.getSelectedIndex() - 1, locFuncOn.isSelected());
            }
            
            try
            {
                if (!"".equals(locDelay.getText().trim()))
                {
                    rc.setDelay(Math.abs(Integer.parseInt(locDelay.getText())));
                }
                
                this.routeContents.setText((this.routeContents.getText() + "\n" + rc.toLine(null)).trim());
            }
            catch (NumberFormatException e)
            {
                JOptionPane.showMessageDialog(this, I18n.t("route.ui.invalidDelay"));
            }
        }
    }//GEN-LAST:event_addLocCommandActionPerformed

    private void locDelayKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_locDelayKeyReleased
        TrainControlUI.validateInt(evt, false);
        TrainControlUI.limitLength(evt, 6);
    }//GEN-LAST:event_locDelayKeyReleased

    private void commandTypeListItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_commandTypeListItemStateChanged
        if (commandTypeList.getSelectedIndex() == 0)
        {
            this.locSpeedSlider.setEnabled(true);
            this.locFuncOff.setEnabled(false);
            this.locFuncOn.setEnabled(false);
        }
        else if (commandTypeList.getSelectedIndex() == 1 || commandTypeList.getSelectedIndex() == 2)
        {
            this.locSpeedSlider.setEnabled(false);
            this.locFuncOff.setEnabled(false);
            this.locFuncOn.setEnabled(false);
        }
        else
        {
            this.locSpeedSlider.setEnabled(false);
            this.locFuncOff.setEnabled(true);
            this.locFuncOn.setEnabled(true);
        }
    }//GEN-LAST:event_commandTypeListItemStateChanged

    private void locNameListItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_locNameListItemStateChanged
        List<String> dropdownModel = new ArrayList<>(Arrays.asList(I18n.t("route.ui.speed"), I18n.t("route.ui.forward"), I18n.t("route.ui.backward")));
            
        if (this.locNameList.getModel().getSize() > 0)
        {
            for (int i = 0; i < this.parent.getModel().getLocByName((String) this.locNameList.getSelectedItem()).getNumF(); i++)
            {
                dropdownModel.add("F" + Integer.toString(i));
            }
            
            int oldSelectedIndex = this.commandTypeList.getSelectedIndex();

            commandTypeList.setModel(
                new javax.swing.DefaultComboBoxModel<>(dropdownModel.toArray(new String[0])) 
            );
            
            if (commandTypeList.getModel().getSize() > oldSelectedIndex)
            {
                commandTypeList.setSelectedIndex(oldSelectedIndex);
            }
        }
        
        commandTypeListItemStateChanged(null);
    }//GEN-LAST:event_locNameListItemStateChanged

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        this.setVisible(false);
        this.dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        
        boolean status;
        
        // Better UX
        if ("".equals(s88.getText()))
        {
            s88.setText("0");
        }
        
        if (isEdit())
        {
            status = RouteCallback(this.getOriginalRouteName(), getRouteName().getText(), getRouteContents().getText(), getS88().getText(),
                getExecutionAuto().isSelected(),
                getTriggerClearThenOccupied().isSelected() ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
                getConditionAccs().getText()
            );
        }
        else
        {
            status = RouteCallback("", getRouteName().getText(), getRouteContents().getText(), getS88().getText(),
                getExecutionAuto().isSelected(),
                getTriggerClearThenOccupied().isSelected() ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
                getConditionAccs().getText()
            );
        }
        
        // Close the window on success
        if (status)
        {
            this.setVisible(false);
            this.dispose();
        }
    }//GEN-LAST:event_saveButtonActionPerformed

    private void addGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addGroupActionPerformed
        int start = conditionAccs.getSelectionStart();
        int end = conditionAccs.getSelectionEnd();

        if (start == end)
        {
            // No selection, add parentheses around everything
            String text = conditionAccs.getText();
            conditionAccs.setText("(" + text + ")");
        }
        else
        {
            // Add parentheses around the selected text
            String text = conditionAccs.getText();
            String selectedText = text.substring(start, end);
            String newText = text.substring(0, start) + "(" + selectedText + ")" + text.substring(end);
            conditionAccs.setText(newText);
        }
    }//GEN-LAST:event_addGroupActionPerformed

    private void addORActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addORActionPerformed
        
        conditionAccs.setText(conditionAccs.getText().trim() + "\nOR\n");
    }//GEN-LAST:event_addORActionPerformed

    private void testButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testButtonActionPerformed
        
        try
        {
            Boolean status = parent.getModel().getFeedbackState(this.getS88().getText().trim());

            NodeExpression conditionExpression;  
            boolean result = true;

            if (!conditionAccs.getText().trim().isEmpty())
            {      
                conditionExpression = NodeExpression.fromTextRepresentation(
                    conditionAccs.getText().trim(),
                    parent.getModel()
                );
                result = conditionExpression.evaluate(parent.getModel());
            }

            JOptionPane.showMessageDialog(
                this,
                I18n.f(
                    "route.ui.messageTriggeringConditionSummary",
                    (status ? I18n.t("route.ui.valueTrue") : I18n.t("route.ui.valueFalse")),
                    (result ? I18n.t("route.ui.valueTrue") : I18n.t("route.ui.valueFalse")),
                    ((status && result) ? I18n.t("route.ui.valueWould") : I18n.t("route.ui.valueWouldNot"))
                )
            );
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(
                this,
                I18n.t("route.ui.errorConditionExpressionInvalid")
            );
        }
    }//GEN-LAST:event_testButtonActionPerformed

    private void locNameListAutoItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_locNameListAutoItemStateChanged
        // TODO add your handling code here:
    }//GEN-LAST:event_locNameListAutoItemStateChanged

    private void autoLocS88KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_autoLocS88KeyReleased
        TrainControlUI.validateInt(evt, false);
        TrainControlUI.limitLength(evt, 5); 
    }//GEN-LAST:event_autoLocS88KeyReleased

    private void addAutoLocConditionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addAutoLocConditionActionPerformed
        String newEntry = "\n";
        
        if (!"".equals(this.autoLocS88.getText()) && this.locNameListAuto.getModel().getSize() > 0)
        {            
            try
            {
                int address = Math.abs(Integer.parseInt(this.autoLocS88.getText()));
                                
                newEntry += RouteCommand.RouteCommandAutoLocomotive(this.locNameListAuto.getSelectedItem().toString(), address).toLine(null);
                
                this.conditionAccs.setText((this.conditionAccs.getText().trim() + (this.conditionAccs.getText().trim().isEmpty() ? "" : "\nAND ") + newEntry).trim());
                this.autoLocS88.setText(this.s88.getText());
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(
                    this,
                    I18n.t("route.ui.errorInvalidS88AddressMustBeInteger")
                );                
                this.autoLocS88.setText(this.s88.getText());
            }
        }
    }//GEN-LAST:event_addAutoLocConditionActionPerformed

    private void LogicStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_LogicStateChanged
        this.autoLocS88.setText(this.s88.getText());
    }//GEN-LAST:event_LogicStateChanged

    private void addANDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addANDActionPerformed
        conditionAccs.setText(conditionAccs.getText().trim() + "\nAND\n");
    }//GEN-LAST:event_addANDActionPerformed

    /**
     * Callback to edit or add a route
     * @param origName
     * @param routeName
     * @param routeContent
     * @param s88
     * @param isEnabled
     * @param triggerType
     * @param conditions
     * @return 
     */
    public boolean RouteCallback(String origName, String routeName, String routeContent, String s88, boolean isEnabled, MarklinRoute.s88Triggers triggerType,
            String conditions)
    {
        if (routeName == null)
        {
            return false;
        }
        
        // Remove trailing spaces in route names
        routeName = routeName.trim();
        routeContent = routeContent.trim();

        if ("".equals(routeName)  || "".equals(routeContent))
        {
            JOptionPane.showMessageDialog(
                this,
                I18n.t("route.ui.errorRouteNameAndCommandsCannotBeEmpty")
            );
            
            return false;
        }
                                      
        // Add route
        try
        {
            List<RouteCommand> newRoute = new LinkedList<>();

            for (String line : routeContent.split("\n"))
            {
                RouteCommand rc = RouteCommand.fromLine(line, false);
                
                if (rc != null)
                {
                    // Nice to have, but we technically don't need this check as the route won't fire itself 
                    if (rc.isRoute() && rc.getName().equals(routeName))
                    {
                        throw new Exception(
                            I18n.t("route.ui.errorRouteCannotReferenceItself")
                        );
                    }

                    newRoute.add(rc);
                }
            }
            
            // Parse conditions
            NodeExpression conditionExpression = null;
            
            if (conditions.trim().length() > 0)
            {
                conditionExpression = NodeExpression.fromTextRepresentation(conditions, parent.getModel());
            }
  
            if (!newRoute.isEmpty())
            {    
                // Editing a route
                if (!"".equals(origName))
                {
                    // Renaming to the same name as an existing route
                    if (!origName.equals(routeName) && this.parent.getModel().getRoute(routeName) != null)
                    {
                        JOptionPane.showMessageDialog(
                            this,
                            I18n.f("route.ui.errorRouteAlreadyExistsPickDifferentName", routeName)
                        );
                        return false;
                    }
                    
                    boolean updateRoute = parent.getModel().getRoute(origName) != null && parent.getModel().getRoute(origName).hasTiles();
                    
                    parent.getModel().editRoute(origName, routeName, newRoute,
                        Math.abs(Integer.parseInt(s88)), triggerType, isEnabled, conditionExpression);
                    
                    if (updateRoute)
                    {
                        parent.layoutEditingComplete();
                    }
                }
                // New route
                else
                {
                    if (parent.getModel().getRouteList().contains(routeName))
                    {
                        JOptionPane.showMessageDialog(
                            this,
                            I18n.f("route.ui.errorRouteAlreadyExistsPickDifferentName", routeName)
                        );
                        return false;
                    }
                                        
                    parent.getModel().newRoute(routeName, newRoute,
                        Math.abs(Integer.parseInt(s88)), triggerType, isEnabled, conditionExpression);
                    
                    // Ensure route changes are synced
                    // Moved into this condition as this is no longer needed when editing, since CS routes can't be edited
                    parent.getModel().syncWithCS2();
                    parent.repaintLayout();
                    parent.repaintLoc();
                }
                
                parent.refreshRouteList();
                
                return true;
            }
        }
        catch (Exception e)
        {
            String message = e.getMessage();
            if (message != null)
            {
                JOptionPane.showMessageDialog(
                    this,
                    I18n.f("route.ui.errorParsingRouteWithMessage", message)
                );
            }
            else
            {
                JOptionPane.showMessageDialog(
                    this,
                    I18n.t("route.ui.errorParsingLogicEnsureParenthesesAndOrTokens")
                );
            }
            
            parent.log(e.toString());
            return false;
        }
        
        return true;
    }
    
    private void updateSettingSelections()
    {
        if (this.accType3Way.isSelected())
        {
            this.accState.setModel(new DefaultComboBoxModel(new String[]{LEFT, STRAIGHT3, RIGHT}));
        }
        else if (this.accTypeTurnout.isSelected())
        {
            this.accState.setModel(new DefaultComboBoxModel(new String[]{STRAIGHT, TURNOUT}));
        }
        else if (this.accTypeSignal.isSelected())
        {
            this.accState.setModel(new DefaultComboBoxModel(new String[]{RED, GREEN}));
        }  
    }
    
    public String getOriginalRouteName()
    {
        return originalRouteName;
    }

    public boolean isEdit()
    {
        return edit;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton DCC;
    private javax.swing.JButton Help;
    private javax.swing.JTabbedPane Logic;
    private javax.swing.JRadioButton MM2;
    private javax.swing.JTextField accAddr;
    private javax.swing.JPanel accPanel;
    private javax.swing.JComboBox<String> accState;
    private javax.swing.JRadioButton accType3Way;
    private javax.swing.JRadioButton accTypeSignal;
    private javax.swing.JRadioButton accTypeTurnout;
    private javax.swing.JButton addAND;
    private javax.swing.JButton addAutoLocCondition;
    private javax.swing.JButton addGroup;
    private javax.swing.JButton addLocCommand;
    private javax.swing.JButton addOR;
    private javax.swing.JButton addS88Condition;
    private javax.swing.JButton addStopCommand;
    private javax.swing.JButton addToRouteButton;
    private javax.swing.JButton addToRouteButton1;
    private javax.swing.JPanel autoCPanel;
    private javax.swing.JTextField autoLocS88;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.ButtonGroup buttonGroup4;
    private javax.swing.ButtonGroup buttonGroup5;
    private javax.swing.ButtonGroup buttonGroup6;
    private javax.swing.JPanel cLogicPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JCheckBox captureCommands;
    private javax.swing.JComboBox<String> commandTypeList;
    private javax.swing.JTextArea conditionAccs;
    private javax.swing.JTextField delay;
    private javax.swing.JRadioButton executionAuto;
    private javax.swing.JRadioButton executionManual;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JTextField locDelay;
    private javax.swing.JRadioButton locFuncOff;
    private javax.swing.JRadioButton locFuncOn;
    private javax.swing.JLabel locFuncStateLabel;
    private javax.swing.JComboBox<String> locNameList;
    private javax.swing.JComboBox<String> locNameListAuto;
    private javax.swing.JPanel locPanel;
    private javax.swing.JSlider locSpeedSlider;
    private javax.swing.JTextArea routeContents;
    private javax.swing.JTextField routeName;
    private javax.swing.JTextField s88;
    private javax.swing.JPanel s88CPanel;
    private javax.swing.JRadioButton s88Clear;
    private javax.swing.JTextField s88CondAddr;
    private javax.swing.JPanel s88CondPanel;
    private javax.swing.JRadioButton s88Occupied;
    private javax.swing.JPanel s88Panel1;
    private javax.swing.JButton saveButton;
    private javax.swing.JButton testButton;
    private javax.swing.JRadioButton triggerClearThenOccupied;
    private javax.swing.JRadioButton triggerOccupiedThenClear;
    // End of variables declaration//GEN-END:variables
}
