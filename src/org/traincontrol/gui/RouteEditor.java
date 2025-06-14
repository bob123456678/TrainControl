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
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinRoute;

/**
 * UI for editing routes 
 */
public class RouteEditor extends PositionAwareJFrame
{    
    private final String helpMessage = "Routes are simply a series of commands executed in sequence. Use the wizard until you are comfortable typing commands.\n\nIn the Route Commands field, one per line, enter the accessory name and state, separated by a comma. For example: "
                    + "Switch 20,turn or Signal 21,green. "
                    + "You can also skip the accessory type: 20,straight or 21,red. "  
                    + "For even more brevity, replace turn/red with 1 or straight/green with 0. "
                    + "An optional third number specifies a delay before execution, in milliseconds."
                    + "\n\nIf you want your route to execute automatically, specify a Triggering S88 sensor address and set Automatic Execution to \"On\"."
                    + "\n\nOptional Conditions allow you to specify logic consiting of S88 sensors and/or accessory states (in the same format as above) which must also evaluate "
                    + "to true for the route to automatically execute.  Boolean logic with OR and parentheses is allowed. "
                    + "For example, if the Triggering S88 address is 10, and the S88 Condition is \"Feedback 11,0\", then "
                    + "the route would only fire if S88 11 was indicating clear at the time S88 10 was triggered.\n\n"
                    + "Beyond accessories, route commands can also reference other routes, locomotives, and functions:" + "\n"
                    + "locspeed,Locomotive name,50 (sets speed to 50)\n" 
                    + "locspeed,Locomotive name,-1 (instant stop)\n" 
                    + "locfunc,Locomotive name,20,1 (toggles F20).";
    
    public static final String TURNOUT = "Turn (1)";
    public static final String STRAIGHT = "Straight (0)";
    public static final String RED = "Red (1)";
    public static final String GREEN = "Green (0)";
    public static final String LEFT = "Left (1,0)";
    public static final String STRAIGHT3 = "Straight (0,0)";
    public static final String RIGHT = "Right (0,1)";
    
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
            this.saveButton.setToolTipText("To ensure consistency, routes from the Central Station cannot be edited.  Duplicate the route instead.");
        }
        else
        {
            this.saveButton.setToolTipText("");
        }
                
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
        jPanel4 = new javax.swing.JPanel();
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
        jLabel14 = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();

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
        jLabel1.setText("Route Name");

        jLabel2.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(0, 0, 115));
        jLabel2.setText("Route Editing Wizard");

        jPanel2.setBackground(new java.awt.Color(245, 245, 245));
        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel2.setFocusable(false);
        jPanel2.setMinimumSize(new java.awt.Dimension(259, 196));
        jPanel2.setName(""); // NOI18N

        jLabel4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(0, 0, 115));
        jLabel4.setText("Triggering S88");

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
        jLabel3.setText("Trigger Condition");

        buttonGroup1.add(triggerClearThenOccupied);
        triggerClearThenOccupied.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        triggerClearThenOccupied.setText("Clear then Occupied");
        triggerClearThenOccupied.setFocusable(false);

        buttonGroup1.add(triggerOccupiedThenClear);
        triggerOccupiedThenClear.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        triggerOccupiedThenClear.setText("Occupied then Clear");
        triggerOccupiedThenClear.setFocusable(false);

        jLabel5.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(0, 0, 115));
        jLabel5.setText("Automatic Execution");

        buttonGroup2.add(executionManual);
        executionManual.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        executionManual.setText("Off");
        executionManual.setFocusable(false);

        buttonGroup2.add(executionAuto);
        executionAuto.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        executionAuto.setText("On");
        executionAuto.setFocusable(false);

        jLabel9.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(0, 0, 115));
        jLabel9.setText("Optional Conditions");

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        conditionAccs.setColumns(11);
        conditionAccs.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        conditionAccs.setRows(3);
        conditionAccs.setWrapStyleWord(true);
        jScrollPane3.setViewportView(conditionAccs);

        testButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        testButton.setText("Test");
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
                    .addComponent(jLabel9)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 187, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(testButton))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane3)))
                .addContainerGap())
        );

        jLabel6.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel6.setForeground(new java.awt.Color(0, 0, 115));
        jLabel6.setText("Optional Settings");

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
        addStopCommand.setText("Add Special Command...");
        addStopCommand.setToolTipText("Customize your routes with non-Marklin commands.");
        addStopCommand.setFocusable(false);
        addStopCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addStopCommandActionPerformed(evt);
            }
        });

        captureCommands.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        captureCommands.setText("Capture Commands");
        captureCommands.setToolTipText("Select this to automatically capture commands from the track diagram and keyboard.");
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
        jLabel7.setText("Route Commands");

        Help.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        Help.setText("Help");
        Help.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                HelpActionPerformed(evt);
            }
        });

        saveButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        saveButton.setText("Save Changes");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        cancelButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        cancelButton.setText("Cancel");
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
        jLabel8.setText("Accessory Address");

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
        addToRouteButton.setText("Add to Route");
        addToRouteButton.setFocusable(false);
        addToRouteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addToRouteButtonActionPerformed(evt);
            }
        });

        jLabel10.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(0, 0, 115));
        jLabel10.setText("Accessory Type");

        jLabel11.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(0, 0, 115));
        jLabel11.setText("State");

        accState.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accState.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Turnout", "Signal", "3-way Turnout" }));
        accState.setMaximumSize(new java.awt.Dimension(136, 26));
        accState.setName(""); // NOI18N

        buttonGroup4.add(accTypeTurnout);
        accTypeTurnout.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accTypeTurnout.setSelected(true);
        accTypeTurnout.setText("Switch");
        accTypeTurnout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accTypeTurnoutActionPerformed(evt);
            }
        });

        buttonGroup4.add(accType3Way);
        accType3Way.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accType3Way.setText("3-way Switch");
        accType3Way.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accType3WayActionPerformed(evt);
            }
        });

        buttonGroup4.add(accTypeSignal);
        accTypeSignal.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        accTypeSignal.setText("Signal");
        accTypeSignal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accTypeSignalActionPerformed(evt);
            }
        });

        jLabel12.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(0, 0, 115));
        jLabel12.setText("Delay (ms)");

        delay.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        delay.setText("0");
        delay.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                delayKeyReleased(evt);
            }
        });

        addToRouteButton1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addToRouteButton1.setText("Add as Condition");
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
        jLabel17.setText("Protocol");

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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 26, Short.MAX_VALUE)
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

        Logic.addTab("Accessory Commands", jPanel1);

        s88Panel1.setBackground(new java.awt.Color(245, 245, 245));

        jLabel18.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel18.setForeground(new java.awt.Color(0, 0, 115));
        jLabel18.setText("Locomotive Name");

        addLocCommand.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addLocCommand.setText("Add to Route");
        addLocCommand.setFocusable(false);
        addLocCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addLocCommandActionPerformed(evt);
            }
        });

        commandTypeList.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        commandTypeList.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Speed", "F0", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24", "F25", "F26", "F27", "F28", "F29", "F30", "F31" }));
        commandTypeList.setMaximumSize(new java.awt.Dimension(136, 26));
        commandTypeList.setName(""); // NOI18N
        commandTypeList.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                commandTypeListItemStateChanged(evt);
            }
        });

        locFuncStateLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locFuncStateLabel.setForeground(new java.awt.Color(0, 0, 115));
        locFuncStateLabel.setText("Function State");

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
        locFuncOn.setText("On");

        buttonGroup3.add(locFuncOff);
        locFuncOff.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locFuncOff.setText("Off");

        jLabel21.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel21.setForeground(new java.awt.Color(0, 0, 115));
        jLabel21.setText("Command Type");

        locSpeedSlider.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locSpeedSlider.setMajorTickSpacing(20);
        locSpeedSlider.setMinorTickSpacing(10);
        locSpeedSlider.setPaintLabels(true);
        locSpeedSlider.setPaintTicks(true);

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);

        jLabel19.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(0, 0, 115));
        jLabel19.setText("Delay (ms)");

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

        Logic.addTab("Locomotive Commands", locPanel);

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
        jLabel15.setText("S88 Address");

        jLabel16.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel16.setForeground(new java.awt.Color(0, 0, 115));
        jLabel16.setText("S88 State");

        buttonGroup5.add(s88Occupied);
        s88Occupied.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        s88Occupied.setSelected(true);
        s88Occupied.setText("Occupied (1)");

        buttonGroup5.add(s88Clear);
        s88Clear.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        s88Clear.setText("Clear (0)");

        addS88Condition.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addS88Condition.setText("Add S88 Condition");
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 446, Short.MAX_VALUE)
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
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

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(s88CPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(s88CPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(9, Short.MAX_VALUE))
        );

        Logic.addTab("S88 Conditions", jPanel4);

        jLabel20.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel20.setForeground(new java.awt.Color(0, 0, 115));
        jLabel20.setText("Locomotive Name");

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
        jLabel22.setText("Location (S88 Sensor)");

        autoLocS88.setMaximumSize(new java.awt.Dimension(90, 26));
        autoLocS88.setMinimumSize(new java.awt.Dimension(90, 26));
        autoLocS88.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                autoLocS88KeyReleased(evt);
            }
        });

        addAutoLocCondition.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addAutoLocCondition.setText("Add Condition");
        addAutoLocCondition.setToolTipText("In autonomy mode, only fire this route if this locomotive is at the specified S88 location.");
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 435, Short.MAX_VALUE)
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

        Logic.addTab("Autonomy Conditions", autoCPanel);

        addGroup.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addGroup.setText("Group Highlighted");
        addGroup.setToolTipText("Wrap the highlighted optional conditions in parentheses.");
        addGroup.setFocusable(false);
        addGroup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addGroupActionPerformed(evt);
            }
        });

        addOR.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        addOR.setText("Insert \"OR\"");
        addOR.setFocusable(false);
        addOR.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addORActionPerformed(evt);
            }
        });

        jLabel13.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(0, 0, 115));
        jLabel13.setText("In the optional conditions field, group multiple conditions in parentheses, and use OR operators, to form logical expressions. ");

        jLabel14.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel14.setForeground(new java.awt.Color(0, 0, 115));
        jLabel14.setText("Conditions on consecutive lines are implicitly ANDed.");

        jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout cLogicPanelLayout = new javax.swing.GroupLayout(cLogicPanel);
        cLogicPanel.setLayout(cLogicPanelLayout);
        cLogicPanelLayout.setHorizontalGroup(
            cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cLogicPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel14)
                    .addComponent(jLabel13))
                .addGap(1, 1, 1)
                .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(addOR, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(addGroup, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        cLogicPanelLayout.setVerticalGroup(
            cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cLogicPanelLayout.createSequentialGroup()
                .addGroup(cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(cLogicPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel13)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel14))
                    .addGroup(cLogicPanelLayout.createSequentialGroup()
                        .addGap(9, 9, 9)
                        .addGroup(cLogicPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jSeparator4)
                            .addGroup(cLogicPanelLayout.createSequentialGroup()
                                .addComponent(addOR, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(addGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap(15, Short.MAX_VALUE))
        );

        Logic.addTab("Condition Logic", cLogicPanel);

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
    }// </editor-fold>//GEN-END:initComponents

    private void HelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_HelpActionPerformed
        JOptionPane.showMessageDialog(this, this.helpMessage);
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
                    this.conditionAccs.setText((this.conditionAccs.getText() + newEntry).trim());
                }
                
                this.accAddr.setText("");
            }
            catch (Exception e)
            {
                this.parent.getModel().log(e);
                JOptionPane.showMessageDialog(this, "Invalid address specified - must be an integer");
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
                "Add to route: ",
                "Special Route Commands",
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
                JOptionPane.showMessageDialog(this, "There are no other routes in the database.  Add one first.");
                return;
            }

            // Show second dialog to select a route
            String selectedRoute = (String) JOptionPane.showInputDialog(
                    this,
                    "Choose a route:",
                    "Select Route",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    routes.toArray(new String[0]), // Convert List<String> to String[]
                    routes.isEmpty() ? null : routes.get(0) // Default selection (first route if available)
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
                        JOptionPane.showMessageDialog(this, "A route cannot trigger itself. Please pick a different route.");
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
                
                this.conditionAccs.setText((this.conditionAccs.getText() + newEntry).trim());

                this.s88CondAddr.setText("");
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, "Invalid address specified - must be an integer");
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
                JOptionPane.showMessageDialog(this, "Locomotive names with commas are not supported.  Renamed the locomotive first.");
                return;
            }
            
            RouteCommand rc;
            if (commandTypeList.getSelectedIndex() == 0)
            {
                rc = RouteCommand.RouteCommandLocomotive((String) locNameList.getSelectedItem(), locSpeedSlider.getValue());
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
                JOptionPane.showMessageDialog(this, "Invalid delay specified - must be an integer");
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
        else
        {
            this.locSpeedSlider.setEnabled(false);
            this.locFuncOff.setEnabled(true);
            this.locFuncOn.setEnabled(true);
        }
    }//GEN-LAST:event_commandTypeListItemStateChanged

    private void locNameListItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_locNameListItemStateChanged
        List<String> dropdownModel = new ArrayList<>(Arrays.asList("Speed"));
            
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
                conditionExpression = NodeExpression.fromTextRepresentation(conditionAccs.getText().trim(), parent.getModel());
                result = conditionExpression.evaluate(parent.getModel());
            }
            
            JOptionPane.showMessageDialog(this, 
                "Triggering S88 condition is: " + (status ? "TRUE" : "false") + "\n"
                + "Optional condition is: " + (result ? "TRUE" : "false") + "\n\n"
                + "Route " + ((status && result) ? "WOULD" : "would not") + " be triggered."
            );
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(this, "There is an error in the condition expression.");
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
                
                this.conditionAccs.setText((this.conditionAccs.getText() + newEntry).trim());
                this.autoLocS88.setText(this.s88.getText());
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, "Invalid s88 address specified - must be an integer");
                this.autoLocS88.setText(this.s88.getText());
            }
        }
    }//GEN-LAST:event_addAutoLocConditionActionPerformed

    private void LogicStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_LogicStateChanged
        this.autoLocS88.setText(this.s88.getText());
    }//GEN-LAST:event_LogicStateChanged

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
            JOptionPane.showMessageDialog(this, "The route name and commands cannot be empty.");

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
                        throw new Exception("The route cannot reference itself");
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
                        JOptionPane.showMessageDialog(this, "A route called " + routeName + " already exists.  Please pick a different name.");
                        return false;
                    }
                    
                    parent.getModel().editRoute(origName, routeName, newRoute,
                        Math.abs(Integer.parseInt(s88)), triggerType, isEnabled, conditionExpression);
                }
                // New route
                else
                {
                    if (parent.getModel().getRouteList().contains(routeName))
                    {
                        JOptionPane.showMessageDialog(this, "A route called " + routeName + " already exists.  Please pick a different name.");
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
                JOptionPane.showMessageDialog(this, "Error parsing route (" + message + ").\n\nBe sure to enter comma-separated values, one pair per line.\n\nTrigger S88 must be an integer and Condition S88s must be comma-separated.");
            }
            else
            {
                JOptionPane.showMessageDialog(this, "Error parsing logic.  Ensure parentheses are matched and OR tokens aren't dangling.");
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
    private javax.swing.JLabel jLabel14;
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
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
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
    private javax.swing.JRadioButton s88Occupied;
    private javax.swing.JPanel s88Panel1;
    private javax.swing.JButton saveButton;
    private javax.swing.JButton testButton;
    private javax.swing.JRadioButton triggerClearThenOccupied;
    private javax.swing.JRadioButton triggerOccupiedThenClear;
    // End of variables declaration//GEN-END:variables
}
