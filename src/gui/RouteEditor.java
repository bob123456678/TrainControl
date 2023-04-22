/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Color;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import marklin.MarklinRoute;

/**
 *
 * @author Adam
 */
public class RouteEditor extends javax.swing.JPanel {
    
    private final String helpMessage = "In the Route Commands field, one per line, enter the accessory address (integer) and state (0 or 1), separated by a comma."
                    + "\nFor example, 20,1 would set switch 20 to turnout, or signal 20 to red."
                    + "\nAn optional third number specifies a delay before execution, in milliseconds."
                    + "\n\nOptionally, specify a Triggering S88 sensor address to automatically trigger this route when Automatic Execution is set to On. "
                    + "\n\nAdditionally, the S88 Condition sensors allow you to specify one or more sensor addresses "
                    + "\n(in the same format as routes, one per line) as occupied (1) or clear (0), all of which must be true for the route to automatically execute. "
                    + "\nFor example, if the Triggering S88 address is 10, and the S88 Condition is \"11,1\", then "
                    + "\nthe route would only fire if S88 11 was indicating occupied at the time address 10 was triggered.";
    
    public static final String TURNOUT = "Turnout (1)";
    public static final String STRAIGHT = "Straight (0)";
    public static final String RED = "Red (1)";
    public static final String GREEN = "Green (0)";
    public static final String LEFT = "Left (1,0)";
    public static final String STRAIGHT3 = "Straight (0,0)";
    public static final String RIGHT = "Right (0,1)";

    /**
     * Creates new form RouteEditor
     * @param routeName
     * @param routeContent
     * @param isEnabled
     * @param s88
     * @param triggerType
     * @param conditionS88s
     */
    public RouteEditor(String routeName, String routeContent, boolean isEnabled, int s88, MarklinRoute.s88Triggers triggerType, String conditionS88s) {
        initComponents();
        
        boolean edit = !"".equals(routeContent);
                
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

        if (triggerType == MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED)
        {
            this.triggerClearThenOccupied.setSelected(true);
        }
        else
        {
            this.triggerOccupiedThenClear.setSelected(true);
        }
        
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
    }

    public JRadioButton getExecutionAuto() {
        return executionAuto;
    }

    public JRadioButton getExecutionManual() {
        return executionManual;
    }

    public JTextArea getRouteContents() {
        return routeContents;
    }

    public JTextField getRouteName() {
        return routeName;
    }

    public JTextField getS88() {
        return s88;
    }
    
    public JTextArea getConditionS88s() {
        return conditionS88;
    }
    
    public JRadioButton getTriggerClearThenOccupied() {
        return triggerClearThenOccupied;
    }

    public JRadioButton getTriggerOccupiedThenClear() {
        return triggerOccupiedThenClear;
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
        jLabel6 = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        routeContents = new javax.swing.JTextArea();
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
        jLabel7 = new javax.swing.JLabel();
        Help = new javax.swing.JButton();

        routeName.setText("jTextField1");
        routeName.setMinimumSize(new java.awt.Dimension(159, 26));

        jLabel1.setForeground(new java.awt.Color(0, 0, 115));
        jLabel1.setText("Route Name");

        jLabel2.setForeground(new java.awt.Color(0, 0, 115));
        jLabel2.setText("Route Editing Wizard");

        jPanel2.setBackground(new java.awt.Color(245, 245, 245));
        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel2.setFocusable(false);
        jPanel2.setMinimumSize(new java.awt.Dimension(259, 196));
        jPanel2.setName(""); // NOI18N

        jLabel4.setForeground(new java.awt.Color(0, 0, 115));
        jLabel4.setText("Triggering S88");

        s88.setColumns(6);
        s88.setText("jTextField1");
        s88.setMaximumSize(new java.awt.Dimension(90, 26));
        s88.setMinimumSize(new java.awt.Dimension(90, 26));
        s88.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                s88KeyReleased(evt);
            }
        });

        jLabel3.setForeground(new java.awt.Color(0, 0, 115));
        jLabel3.setText("Trigger Condition");

        buttonGroup1.add(triggerClearThenOccupied);
        triggerClearThenOccupied.setText("Clear then Occupied");
        triggerClearThenOccupied.setFocusable(false);
        triggerClearThenOccupied.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                triggerClearThenOccupiedActionPerformed(evt);
            }
        });

        buttonGroup1.add(triggerOccupiedThenClear);
        triggerOccupiedThenClear.setText("Occupied then Clear");
        triggerOccupiedThenClear.setFocusable(false);
        triggerOccupiedThenClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                triggerOccupiedThenClearActionPerformed(evt);
            }
        });

        jLabel5.setForeground(new java.awt.Color(0, 0, 115));
        jLabel5.setText("Automatic Execution");

        buttonGroup2.add(executionManual);
        executionManual.setText("Off");
        executionManual.setFocusable(false);
        executionManual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                executionManualActionPerformed(evt);
            }
        });

        buttonGroup2.add(executionAuto);
        executionAuto.setText("On");
        executionAuto.setFocusable(false);
        executionAuto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                executionAutoActionPerformed(evt);
            }
        });

        jLabel9.setForeground(new java.awt.Color(0, 0, 115));
        jLabel9.setText("Additional S88 Conditions");

        conditionS88.setColumns(13);
        conditionS88.setRows(5);
        conditionS88.setWrapStyleWord(true);
        jScrollPane2.setViewportView(conditionS88);

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
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
                        .addGap(0, 2, Short.MAX_VALUE))
                    .addComponent(triggerOccupiedThenClear, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(triggerClearThenOccupied, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 11, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 186, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9))
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
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
                                    .addComponent(executionAuto)))
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 187, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );

        jLabel6.setForeground(new java.awt.Color(0, 0, 115));
        jLabel6.setText("Optional Settings");

        jPanel5.setBackground(new java.awt.Color(245, 245, 245));
        jPanel5.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel5.setFocusable(false);
        jPanel5.setMinimumSize(new java.awt.Dimension(259, 196));
        jPanel5.setName(""); // NOI18N

        routeContents.setColumns(20);
        routeContents.setRows(5);
        jScrollPane1.setViewportView(routeContents);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel6.setBackground(new java.awt.Color(245, 245, 245));
        jPanel6.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel6.setFocusable(false);
        jPanel6.setMinimumSize(new java.awt.Dimension(259, 196));
        jPanel6.setName(""); // NOI18N

        jLabel8.setForeground(new java.awt.Color(0, 0, 115));
        jLabel8.setText("Accessory Address");

        accAddr.setColumns(6);
        accAddr.setMaximumSize(new java.awt.Dimension(90, 26));
        accAddr.setMinimumSize(new java.awt.Dimension(90, 26));
        accAddr.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accAddrActionPerformed(evt);
            }
        });
        accAddr.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                accAddrKeyReleased(evt);
            }
        });

        addToRouteButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        addToRouteButton.setText("Add to Route");
        addToRouteButton.setFocusable(false);
        addToRouteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addToRouteButtonActionPerformed(evt);
            }
        });

        jLabel10.setForeground(new java.awt.Color(0, 0, 115));
        jLabel10.setText("Accessory Type");

        jLabel11.setForeground(new java.awt.Color(0, 0, 115));
        jLabel11.setText("Accessory State");

        accState.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Turnout", "Signal", "3-way Turnout" }));
        accState.setMaximumSize(new java.awt.Dimension(136, 26));
        accState.setName(""); // NOI18N
        accState.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accStateActionPerformed(evt);
            }
        });

        buttonGroup4.add(accTypeTurnout);
        accTypeTurnout.setSelected(true);
        accTypeTurnout.setText("Turnout");
        accTypeTurnout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accTypeTurnoutActionPerformed(evt);
            }
        });

        buttonGroup4.add(accType3Way);
        accType3Way.setText("3-way Turnout");
        accType3Way.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accType3WayActionPerformed(evt);
            }
        });

        buttonGroup4.add(accTypeSignal);
        accTypeSignal.setText("Signal");
        accTypeSignal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accTypeSignalActionPerformed(evt);
            }
        });

        jLabel12.setForeground(new java.awt.Color(0, 0, 115));
        jLabel12.setText("Delay (ms)");

        delay.setText("0");
        delay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                delayActionPerformed(evt);
            }
        });
        delay.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                delayKeyReleased(evt);
            }
        });

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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(accState, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(delay))
                    .addComponent(jLabel12))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(addToRouteButton)
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(jLabel10)
                    .addComponent(jLabel11)
                    .addComponent(jLabel12))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(accAddr, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(accTypeTurnout)
                    .addComponent(accType3Way)
                    .addComponent(accTypeSignal)
                    .addComponent(accState, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(delay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addToRouteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(121, Short.MAX_VALUE))
        );

        jLabel7.setForeground(new java.awt.Color(0, 0, 115));
        jLabel7.setText("Route Commands");

        Help.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        Help.setText("Help");
        Help.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                HelpActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel6, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(routeName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(Help))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jLabel1))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addGap(164, 164, 164)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel6)
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, 72, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void triggerClearThenOccupiedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_triggerClearThenOccupiedActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_triggerClearThenOccupiedActionPerformed

    private void executionManualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_executionManualActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_executionManualActionPerformed

    private void executionAutoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_executionAutoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_executionAutoActionPerformed

    private void HelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_HelpActionPerformed
        JOptionPane.showMessageDialog(this, this.helpMessage);        // TODO add your handling code here:
    }//GEN-LAST:event_HelpActionPerformed

    private void addToRouteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addToRouteButtonActionPerformed
        
        String newEntry = "\n";
        
        if (!"".equals(this.accAddr.getText()))
        {            
            try
            {
                int address = Math.abs(Integer.parseInt(this.accAddr.getText()));
                
                String delayString = "";
                
                if (!"".equals(this.delay.getText()))
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
                        newEntry += address + "," + "0" + delayString + "\n";
                        newEntry += (address + 1) + "," + "0";
                    }
                    else if (this.accState.getSelectedItem().toString().equals(LEFT))
                    {
                        newEntry += address + "," + "1" + delayString + "\n";
                        newEntry += (address + 1) + "," + "0";                    
                    }
                    else if (this.accState.getSelectedItem().toString().equals(RIGHT))
                    {
                        newEntry += address + "," + "0" + delayString + "\n";
                        newEntry += (address + 1) + "," + "1";
                    }
                }
                else if (this.accTypeTurnout.isSelected())
                {
                    if (this.accState.getSelectedItem().toString().equals(TURNOUT))
                    {
                        newEntry += address + "," + "1" + delayString;
                    }
                    else
                    {
                        newEntry += address + "," + "0" + delayString;
                    }      
                }
                else if (this.accTypeSignal.isSelected())
                {
                    if (this.accState.getSelectedItem().toString().equals(RED))
                    {
                        newEntry += address + "," + "1" + delayString;
                    }
                    else
                    {
                        newEntry += address + "," + "0" + delayString;
                    }
                }
                
                this.routeContents.setText((this.routeContents.getText() + newEntry).trim());
                this.accAddr.setText("");
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, "Invalid address specified - must be an integer");
                this.accAddr.setText("");
            }
        }
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

    private void accAddrActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_accAddrActionPerformed
 
    }//GEN-LAST:event_accAddrActionPerformed

    private void accStateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_accStateActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_accStateActionPerformed

    private void triggerOccupiedThenClearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_triggerOccupiedThenClearActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_triggerOccupiedThenClearActionPerformed

    private void accAddrKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_accAddrKeyReleased
        try
        {
            int addr = Integer.parseInt(this.accAddr.getText());
            
            if (addr < 0)
            {
                this.accAddr.setText("");    
            }
        }
        catch (Exception e)
        {
            this.accAddr.setText("");
        }
    }//GEN-LAST:event_accAddrKeyReleased

    private void s88KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_s88KeyReleased
        try
        {
            int addr = Integer.parseInt(this.s88.getText());
            
            if (addr < 0)
            {
                this.s88.setText("");    
            }
        }
        catch (Exception e)
        {
            this.s88.setText("");
        }
    }//GEN-LAST:event_s88KeyReleased

    private void delayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_delayActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_delayActionPerformed

    private void delayKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_delayKeyReleased
        try
        {
            int addr = Integer.parseInt(this.delay.getText());
            
            if (addr < 0)
            {
                this.delay.setText("0");    
            }
        }
        catch (Exception e)
        {
            this.delay.setText("");
        }
    }//GEN-LAST:event_delayKeyReleased

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
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Help;
    private javax.swing.JTextField accAddr;
    private javax.swing.JComboBox<String> accState;
    private javax.swing.JRadioButton accType3Way;
    private javax.swing.JRadioButton accTypeSignal;
    private javax.swing.JRadioButton accTypeTurnout;
    private javax.swing.JButton addToRouteButton;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.ButtonGroup buttonGroup4;
    private javax.swing.JTextArea conditionS88;
    private javax.swing.JTextField delay;
    private javax.swing.JRadioButton executionAuto;
    private javax.swing.JRadioButton executionManual;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
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
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JTextArea routeContents;
    private javax.swing.JTextField routeName;
    private javax.swing.JTextField s88;
    private javax.swing.JRadioButton triggerClearThenOccupied;
    private javax.swing.JRadioButton triggerOccupiedThenClear;
    // End of variables declaration//GEN-END:variables
}
