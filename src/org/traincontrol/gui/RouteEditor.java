package org.traincontrol.gui;

import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinRoute;

/**
 * UI for editing routes 
 */
public class RouteEditor extends javax.swing.JFrame
{    
    private final String helpMessage = "In the Route Commands field, one per line, enter the accessory name and state, separated by a comma."
                    + "\nFor example, Switch 20,turn or Signal 21,green."
                    + "\nYou can also skip the accessory type, such as 20,straight or 21,red."
                    + "\nFor even more brevity, replace turn/red with 1 or straight/green with 0."
                    + "\nAn optional third number specifies a delay before execution, in milliseconds."
                    + "\n\nOptionally, specify a Triggering S88 sensor address to automatically trigger this route when Automatic Execution is set to On. "
                    + "\n\nAdditionally, the S88 Condition sensors allow you to specify one or more sensor addresses "
                    + "(in the same format as routes, one per line) as occupied (1) or clear (0), all of which must be true for the route to automatically execute. "
                    + "\nFor example, if the Triggering S88 address is 10, and the S88 Condition is \"11,1\", then "
                    + "the route would only fire if S88 11 was indicating occupied at the time address 10 was triggered.\n\n"
                    + "Conditional Accessories behave just like S88 conditions: if specified, all accessory state must also match\n"
                    + "the specified values in order for the route to fire.\n\n" 
                    + "In addition to accessories, you can set commands for locomotives and functions:" + "\n"
                    + "locspeed,Locomotive name,50 (sets speed to 50)\n" 
                    + "locspeed,Locomotive name,-1 (instant stop)\n" 
                    + "locfunc,Locomotive name,20,1 (toggles F20).\n\n"
                    + "Use the wizard until you are comfortable typing commands.";
    
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
     * @param conditionS88s
     * @param conditionAccString
     */
    public RouteEditor(String windowTitle, TrainControlUI parent, String routeName, String routeContent, boolean isEnabled, int s88, MarklinRoute.s88Triggers triggerType, String conditionS88s, String conditionAccString)
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
        this.conditionS88.setText(conditionS88s);
        this.conditionAccs.setText(conditionAccString);

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
        
        locNameListItemStateChanged(null);
        
        if (!edit)
        {
            routeContents.setLineWrap(true);
            routeContents.setText("");

            routeContents.addMouseListener(new MouseListener() {

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
    
    private JTextArea getConditionS88s()
    {
        return conditionS88;
    }
    
    private JRadioButton getTriggerClearThenOccupied()
    {
        return triggerClearThenOccupied;
    }
    
    public void appendCommand(String command)
    {
        //String newVal = this.routeContents.getText() + "\n" + Integer.toString(accessory) + "," + (state ? "1" : "0") + "\n";
        this.routeContents.setText(this.routeContents.getText().trim() + "\n" + command + "\n");
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
        jScrollPane2 = new javax.swing.JScrollPane();
        conditionS88 = new javax.swing.JTextArea();
        jSeparator1 = new javax.swing.JSeparator();
        jScrollPane3 = new javax.swing.JScrollPane();
        conditionAccs = new javax.swing.JTextArea();
        jLabel13 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        routeContents = new javax.swing.JTextArea();
        addStopCommand = new javax.swing.JButton();
        captureCommands = new javax.swing.JCheckBox();
        jPanel6 = new javax.swing.JPanel();
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
        jLabel7 = new javax.swing.JLabel();
        Help = new javax.swing.JButton();
        jLabel14 = new javax.swing.JLabel();
        s88Panel = new javax.swing.JPanel();
        s88CondAddr = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        s88Occupied = new javax.swing.JRadioButton();
        s88Clear = new javax.swing.JRadioButton();
        addS88Condition = new javax.swing.JButton();
        jLabel17 = new javax.swing.JLabel();
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
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

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
        jLabel2.setText("Accessory Command Wizard");

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
        jLabel9.setText("Optional S88 Conditions");

        conditionS88.setColumns(11);
        conditionS88.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        conditionS88.setRows(3);
        conditionS88.setWrapStyleWord(true);
        jScrollPane2.setViewportView(conditionS88);

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        conditionAccs.setColumns(11);
        conditionAccs.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        conditionAccs.setRows(3);
        conditionAccs.setWrapStyleWord(true);
        jScrollPane3.setViewportView(conditionAccs);

        jLabel13.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(0, 0, 115));
        jLabel13.setText("Optional Accessory Conditions");

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
                                .addComponent(executionAuto)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(18, 18, 18)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 11, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel9)
                    .addComponent(jLabel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane2)
                    .addComponent(jScrollPane3))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator1)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jLabel4)
                                .addComponent(s88, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel9))
                        .addGap(0, 0, 0)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
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
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jLabel13)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)))))
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

        captureCommands.setText("Capture Commands");
        captureCommands.setToolTipText("Select this to automatically capture commands from the track diagram and keyboard.");

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
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 189, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addStopCommand)
                    .addComponent(captureCommands))
                .addGap(7, 7, 7))
        );

        jPanel6.setBackground(new java.awt.Color(245, 245, 245));
        jPanel6.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel6.setFocusable(false);
        jPanel6.setMinimumSize(new java.awt.Dimension(259, 196));
        jPanel6.setName(""); // NOI18N

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
        jLabel11.setText("Accessory State");

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

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(accAddr, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel10)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(accTypeTurnout)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(accTypeSignal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(accType3Way)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(jLabel11)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(accState, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, 12, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel12)
                    .addComponent(delay, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(addToRouteButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(addToRouteButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel12)
                            .addComponent(addToRouteButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(addToRouteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(delay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel8)
                            .addComponent(jLabel10)
                            .addComponent(jLabel11))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(accAddr, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(accTypeTurnout)
                            .addComponent(accType3Way)
                            .addComponent(accTypeSignal)
                            .addComponent(accState, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jSeparator3))
                .addGap(36, 36, 36))
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

        jLabel14.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel14.setForeground(new java.awt.Color(0, 0, 115));
        jLabel14.setText("S88 Condition Editing Wizard");

        s88Panel.setBackground(new java.awt.Color(245, 245, 245));
        s88Panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

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

        javax.swing.GroupLayout s88PanelLayout = new javax.swing.GroupLayout(s88Panel);
        s88Panel.setLayout(s88PanelLayout);
        s88PanelLayout.setHorizontalGroup(
            s88PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(s88PanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(s88PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(s88CondAddr, javax.swing.GroupLayout.PREFERRED_SIZE, 116, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(s88PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(s88PanelLayout.createSequentialGroup()
                        .addComponent(jLabel16)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(s88PanelLayout.createSequentialGroup()
                        .addComponent(s88Occupied)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(s88Clear)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(addS88Condition)))
                .addContainerGap())
        );
        s88PanelLayout.setVerticalGroup(
            s88PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(s88PanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(s88PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(s88PanelLayout.createSequentialGroup()
                        .addComponent(jLabel16)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(s88PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(s88Occupied)
                            .addComponent(s88Clear)
                            .addComponent(addS88Condition, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(s88PanelLayout.createSequentialGroup()
                        .addComponent(jLabel15)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(s88CondAddr, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel17.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(0, 0, 115));
        jLabel17.setText("Locomotive Command Wizard");

        s88Panel1.setBackground(new java.awt.Color(245, 245, 245));
        s88Panel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

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

        jButton1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jButton1.setText("Save Changes");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jButton2.setText("Cancel");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
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
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(routeName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Help, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(s88Panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(s88Panel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jButton1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton2))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel14)
                            .addComponent(jLabel17)
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
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel17)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(s88Panel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel14)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(s88Panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton2))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void HelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_HelpActionPerformed
        JOptionPane.showMessageDialog(this, this.helpMessage);
    }//GEN-LAST:event_HelpActionPerformed

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
                            Accessory.accessoryType.SWITCH, address, false
                        ) + delayString + "\n";
                        
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address + 1, false
                        );
                    }
                    else if (this.accState.getSelectedItem().toString().equals(LEFT))
                    {
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address, true
                        ) + delayString + "\n";
                        
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address + 1, false
                        );               
                    }
                    else if (this.accState.getSelectedItem().toString().equals(RIGHT))
                    {
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address, false
                        ) + delayString + "\n";
                        
                        newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address + 1, true
                        );
                    }
                }
                else if (this.accTypeTurnout.isSelected())
                {
                    newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SWITCH, address, this.accState.getSelectedItem().toString().equals(TURNOUT)
                    ) + delayString;   
                }
                else if (this.accTypeSignal.isSelected())
                {
                    newEntry += MarklinAccessory.toAccessorySettingString(
                            Accessory.accessoryType.SIGNAL, address, this.accState.getSelectedItem().toString().equals(RED)
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
            RouteCommand.RouteCommandStop().toString()
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
                                
                newEntry += address + "," + (this.s88Occupied.isSelected() ? "1" : "0");
               
                this.conditionS88.setText((this.conditionS88.getText() + newEntry).trim());

                this.s88CondAddr.setText("");
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, "Invalid address specified - must be an integer");
                this.accAddr.setText("");
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

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        this.setVisible(false);
        this.dispose();
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        
        boolean status;
        
        if (isEdit())
        {
            status = RouteCallback(this.getOriginalRouteName(), getRouteName().getText(), getRouteContents().getText(), getS88().getText(),
                getExecutionAuto().isSelected(),
                getTriggerClearThenOccupied().isSelected() ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
                getConditionS88s().getText(), getConditionAccs().getText()
            );
        }
        else
        {
            status = RouteCallback("", getRouteName().getText(), getRouteContents().getText(), getS88().getText(),
                getExecutionAuto().isSelected(),
                getTriggerClearThenOccupied().isSelected() ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
                getConditionS88s().getText(), getConditionAccs().getText()
            );
        }
        
        // Close the window on success
        if (status)
        {
            this.setVisible(false);
            this.dispose();
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    /**
     * Callback to edit or add a route
     * @param origName
     * @param routeName
     * @param routeContent
     * @param s88
     * @param isEnabled
     * @param triggerType
     * @param conditionS88s
     * @param conditionAccs
     * @return 
     */
    public boolean RouteCallback(String origName, String routeName, String routeContent, String s88, boolean isEnabled, MarklinRoute.s88Triggers triggerType,
            String conditionS88s, String conditionAccs)
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
                RouteCommand rc = RouteCommand.fromLine(line);
                
                if (rc != null)
                {
                    newRoute.add(rc);
                }
            }
            
            List<RouteCommand> newAccConditions = new LinkedList<>();
            
            for (String line : conditionAccs.split("\n"))
            {
                if (line.trim().length() > 0)
                {
                    RouteCommand rc = RouteCommand.fromLine(line);
                    
                    if (!rc.isAccessory())
                    {
                        throw new Exception("Accessory Conditions must be accessory commands.");
                    }
                    
                    //int address = Math.abs(Integer.parseInt(line.split(",")[0].trim()));
                    //boolean state = line.split(",")[1].trim().equals("1");
                  
                    //RouteCommand rc = RouteCommand.RouteCommandAccessory(address, state);

                    newAccConditions.add(rc);
                }
            }
  
            Map<Integer, Boolean> newConditions = new HashMap<>();
            
            for (String line : conditionS88s.split("\n"))
            {
                if (line.trim().length() > 0)
                {
                    int address = Math.abs(Integer.parseInt(line.split(",")[0].trim()));
                    boolean state = line.split(",")[1].trim().equals("1");

                    newConditions.put(address, state);
                }
            }
            
            if (!newRoute.isEmpty())
            {    
                // Editing a route
                if (!"".equals(origName))
                {
                    parent.getModel().editRoute(origName, routeName, newRoute,
                        Math.abs(Integer.parseInt(s88)), triggerType, isEnabled, newConditions, newAccConditions);
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
                        Math.abs(Integer.parseInt(s88)), triggerType, isEnabled, newConditions, newAccConditions);
                }
                
                parent.refreshRouteList();

                // Ensure route changes are synced
                parent.getModel().syncWithCS2();
                parent.repaintLayout();
                parent.repaintLoc();
                
                return true;
            }
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(this, "Error parsing route (" + e.getMessage() + ").\n\nBe sure to enter comma-separated values, one pair per line.\n\nTrigger S88 must be an integer and Condition S88s must be comma-separated.");
        
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
    private javax.swing.JButton Help;
    private javax.swing.JTextField accAddr;
    private javax.swing.JComboBox<String> accState;
    private javax.swing.JRadioButton accType3Way;
    private javax.swing.JRadioButton accTypeSignal;
    private javax.swing.JRadioButton accTypeTurnout;
    private javax.swing.JButton addLocCommand;
    private javax.swing.JButton addS88Condition;
    private javax.swing.JButton addStopCommand;
    private javax.swing.JButton addToRouteButton;
    private javax.swing.JButton addToRouteButton1;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.ButtonGroup buttonGroup4;
    private javax.swing.ButtonGroup buttonGroup5;
    private javax.swing.JCheckBox captureCommands;
    private javax.swing.JComboBox<String> commandTypeList;
    private javax.swing.JTextArea conditionAccs;
    private javax.swing.JTextArea conditionS88;
    private javax.swing.JTextField delay;
    private javax.swing.JRadioButton executionAuto;
    private javax.swing.JRadioButton executionManual;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
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
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JTextField locDelay;
    private javax.swing.JRadioButton locFuncOff;
    private javax.swing.JRadioButton locFuncOn;
    private javax.swing.JLabel locFuncStateLabel;
    private javax.swing.JComboBox<String> locNameList;
    private javax.swing.JSlider locSpeedSlider;
    private javax.swing.JTextArea routeContents;
    private javax.swing.JTextField routeName;
    private javax.swing.JTextField s88;
    private javax.swing.JRadioButton s88Clear;
    private javax.swing.JTextField s88CondAddr;
    private javax.swing.JRadioButton s88Occupied;
    private javax.swing.JPanel s88Panel;
    private javax.swing.JPanel s88Panel1;
    private javax.swing.JRadioButton triggerClearThenOccupied;
    private javax.swing.JRadioButton triggerOccupiedThenClear;
    // End of variables declaration//GEN-END:variables
}
