package gui;

import base.Locomotive;
import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import marklin.MarklinControlStation;
import marklin.MarklinLayout;
import model.View;
import model.ViewListener;

/**
 * UI for controlling trains and switches using the keyboard
 */
public class TrainControlUI extends javax.swing.JFrame implements View 
{
    // Preferences fields
    public static String IP_PREF = "initIP";
    
    // View listener (model) reference
    ViewListener model;
    
    // The active locomotive
    Locomotive activeLoc;
    
    // The active locomotive button
    javax.swing.JButton currentButton;
    
    HashMap<Integer, javax.swing.JButton> buttonMapping;
    HashMap<javax.swing.JButton, Integer> rButtonMapping;
    HashMap<javax.swing.JButton, JLabel> labelMapping;
    List<HashMap<javax.swing.JButton, Locomotive>> locMapping;
    HashMap<javax.swing.JToggleButton, Integer> functionMapping;
    HashMap<Integer, javax.swing.JToggleButton> rFunctionMapping;
    HashMap<Integer, javax.swing.JToggleButton> switchMapping;
    LayoutGrid trainGrid;
    
    // The keyboard being displayed
    private int keyboardNumber = 1;
    
    // The locomotive mapping page being displayed
    private int locMappingNumber = 1;
    
    // The locomotive mapping that was just painted
    private int lastLocMappingPainted = 0;
    
    // Number of keys per page
    private static final int KEYBOARD_KEYS = 63;
    
    // Total number of keyboards >= 1
    private static final int NUM_KEYBOARDS = 4;
    
    // Total number of locomotive mappings >= 1
    private static final int NUM_LOC_MAPPINGS = 4;
        
    // Maximum number of functions
    private static final int NUM_FN = 32;
    
    // Data save file name
    private static final String DATA_FILE_NAME = "UIState.data";
    
    // Image cache
    private final HashMap<String, Image> imageCache;
    
    // Preferences
    private final Preferences prefs;

    /**
     * Creates new form MarklinUI
     */
    public TrainControlUI()
    {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if ("Nimbus".equals(info.getName()))
                {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(TrainControlUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        
        initComponents();
        
        this.prefs = Preferences.userNodeForPackage(TrainControlUI.class);
        
        // Mappings allowing us to programatically access UI components
        this.buttonMapping = new HashMap<>();
        this.rButtonMapping = new HashMap<>();
        this.labelMapping = new HashMap<>();
        this.switchMapping = new HashMap<>();
        this.functionMapping = new HashMap<>();
        this.rFunctionMapping = new HashMap<>();
        this.imageCache = new HashMap<>();
        
        this.locMapping = new ArrayList<>();
    
        for (int i = 0; i < TrainControlUI.NUM_LOC_MAPPINGS; i++)
        {
            this.locMapping.add(new HashMap<>());
        }

        // Map function buttons to numbers
        this.functionMapping.put(F0, 0);
        this.functionMapping.put(F1, 1);
        this.functionMapping.put(F2, 2);
        this.functionMapping.put(F3, 3);
        this.functionMapping.put(F4, 4);
        this.functionMapping.put(F5, 5);
        this.functionMapping.put(F6, 6);
        this.functionMapping.put(F7, 7);
        this.functionMapping.put(F8, 8);
        this.functionMapping.put(F9, 9);
        this.functionMapping.put(F10, 10);
        this.functionMapping.put(F11, 11);
        this.functionMapping.put(F12, 12);
        this.functionMapping.put(F13, 13);
        this.functionMapping.put(F14, 14);
        this.functionMapping.put(F15, 15);
        this.functionMapping.put(F16, 16);
        this.functionMapping.put(F17, 17);
        this.functionMapping.put(F18, 18);
        this.functionMapping.put(F19, 19);
        this.functionMapping.put(F20, 20);
        this.functionMapping.put(F21, 21);
        this.functionMapping.put(F22, 22);
        this.functionMapping.put(F23, 23);
        this.functionMapping.put(F24, 24);
        this.functionMapping.put(F25, 25);
        this.functionMapping.put(F26, 26);
        this.functionMapping.put(F27, 27);
        this.functionMapping.put(F28, 28);
        this.functionMapping.put(F29, 29);
        this.functionMapping.put(F30, 30);
        this.functionMapping.put(F31, 31);
        
        // Map numbers back to the corresponding buttons
        for (javax.swing.JToggleButton b : this.functionMapping.keySet())
        {
            this.rFunctionMapping.put(this.functionMapping.get(b),b);
        }
        
        // Map keyboard buttons to buttons
        this.buttonMapping.put(KeyEvent.VK_A, AButton);
        this.buttonMapping.put(KeyEvent.VK_B, BButton);
        this.buttonMapping.put(KeyEvent.VK_C, CButton);
        this.buttonMapping.put(KeyEvent.VK_D, DButton);
        this.buttonMapping.put(KeyEvent.VK_E, EButton);
        this.buttonMapping.put(KeyEvent.VK_F, FButton);
        this.buttonMapping.put(KeyEvent.VK_G, GButton);
        this.buttonMapping.put(KeyEvent.VK_H, HButton);
        this.buttonMapping.put(KeyEvent.VK_I, IButton);
        this.buttonMapping.put(KeyEvent.VK_J, JButton);
        this.buttonMapping.put(KeyEvent.VK_K, KButton);
        this.buttonMapping.put(KeyEvent.VK_L, LButton);
        this.buttonMapping.put(KeyEvent.VK_M, MButton);
        this.buttonMapping.put(KeyEvent.VK_N, NButton);
        this.buttonMapping.put(KeyEvent.VK_O, OButton);
        this.buttonMapping.put(KeyEvent.VK_P, PButton);
        this.buttonMapping.put(KeyEvent.VK_Q, QButton);
        this.buttonMapping.put(KeyEvent.VK_R, RButton);
        this.buttonMapping.put(KeyEvent.VK_S, SButton);
        this.buttonMapping.put(KeyEvent.VK_T, TButton);
        this.buttonMapping.put(KeyEvent.VK_U, UButton);
        this.buttonMapping.put(KeyEvent.VK_V, VButton);
        this.buttonMapping.put(KeyEvent.VK_W, WButton);
        this.buttonMapping.put(KeyEvent.VK_X, XButton);
        this.buttonMapping.put(KeyEvent.VK_Y, YButton);
        this.buttonMapping.put(KeyEvent.VK_Z, ZButton);
        
        // Map numbers back to the corresponding buttons
        for (Integer keyCode : this.buttonMapping.keySet())
        {
            this.rButtonMapping.put(this.buttonMapping.get(keyCode), keyCode);
        }
        
        // Map buttons to labels
        this.labelMapping.put(AButton, ALabel);
        this.labelMapping.put(BButton, BLabel);
        this.labelMapping.put(CButton, CLabel);
        this.labelMapping.put(DButton, DLabel);
        this.labelMapping.put(EButton, ELabel);
        this.labelMapping.put(FButton, FLabel);
        this.labelMapping.put(GButton, GLabel);
        this.labelMapping.put(HButton, HLabel);
        this.labelMapping.put(IButton, ILabel);
        this.labelMapping.put(JButton, JLabel);
        this.labelMapping.put(KButton, KLabel);
        this.labelMapping.put(LButton, LLabel);
        this.labelMapping.put(MButton, MLabel);
        this.labelMapping.put(NButton, NLabel);
        this.labelMapping.put(OButton, OLabel);
        this.labelMapping.put(PButton, PLabel);
        this.labelMapping.put(QButton, QLabel);
        this.labelMapping.put(RButton, RLabel);
        this.labelMapping.put(SButton, SLabel);
        this.labelMapping.put(TButton, TLabel);
        this.labelMapping.put(UButton, ULabel);
        this.labelMapping.put(VButton, VLabel);
        this.labelMapping.put(WButton, WLabel);
        this.labelMapping.put(XButton, XLabel);
        this.labelMapping.put(YButton, YLabel);
        this.labelMapping.put(ZButton, ZLabel);
        
        // Map switch addresses to buttons
        this.switchMapping.put(1,SwitchButton1);
        this.switchMapping.put(2,SwitchButton2);
        this.switchMapping.put(3,SwitchButton3);
        this.switchMapping.put(4,SwitchButton4);
        this.switchMapping.put(5,SwitchButton5);
        this.switchMapping.put(6,SwitchButton6);
        this.switchMapping.put(7,SwitchButton7);
        this.switchMapping.put(8,SwitchButton8);
        this.switchMapping.put(9,SwitchButton9);
        this.switchMapping.put(10,SwitchButton10);
        this.switchMapping.put(11,SwitchButton11);
        this.switchMapping.put(12,SwitchButton12);
        this.switchMapping.put(13,SwitchButton13);
        this.switchMapping.put(14,SwitchButton14);
        this.switchMapping.put(15,SwitchButton15);
        this.switchMapping.put(16,SwitchButton16);
        this.switchMapping.put(17,SwitchButton17);
        this.switchMapping.put(18,SwitchButton18);
        this.switchMapping.put(19,SwitchButton19);
        this.switchMapping.put(20,SwitchButton20);
        this.switchMapping.put(21,SwitchButton21);
        this.switchMapping.put(22,SwitchButton22);
        this.switchMapping.put(23,SwitchButton23);
        this.switchMapping.put(24,SwitchButton24);
        this.switchMapping.put(25,SwitchButton25);
        this.switchMapping.put(26,SwitchButton26);
        this.switchMapping.put(27,SwitchButton27);
        this.switchMapping.put(28,SwitchButton28);
        this.switchMapping.put(29,SwitchButton29);
        this.switchMapping.put(30,SwitchButton30);
        this.switchMapping.put(31,SwitchButton31);
        this.switchMapping.put(32,SwitchButton32);
        this.switchMapping.put(33,SwitchButton33);
        this.switchMapping.put(34,SwitchButton34);
        this.switchMapping.put(35,SwitchButton35);
        this.switchMapping.put(36,SwitchButton36);
        this.switchMapping.put(37,SwitchButton37);
        this.switchMapping.put(38,SwitchButton38);
        this.switchMapping.put(39,SwitchButton39);
        this.switchMapping.put(40,SwitchButton40);
        this.switchMapping.put(41,SwitchButton41);
        this.switchMapping.put(42,SwitchButton42);
        this.switchMapping.put(43,SwitchButton43);
        this.switchMapping.put(44,SwitchButton44);
        this.switchMapping.put(45,SwitchButton45);
        this.switchMapping.put(46,SwitchButton46);
        this.switchMapping.put(47,SwitchButton47);
        this.switchMapping.put(48,SwitchButton48);
        this.switchMapping.put(49,SwitchButton49);
        this.switchMapping.put(50,SwitchButton50);
        this.switchMapping.put(51,SwitchButton51);
        this.switchMapping.put(52,SwitchButton52);
        this.switchMapping.put(53,SwitchButton53);
        this.switchMapping.put(54,SwitchButton54);
        this.switchMapping.put(55,SwitchButton55);
        this.switchMapping.put(56,SwitchButton56);
        this.switchMapping.put(57,SwitchButton57);
        this.switchMapping.put(58,SwitchButton58);
        this.switchMapping.put(59,SwitchButton59);
        this.switchMapping.put(60,SwitchButton60);
        this.switchMapping.put(61,SwitchButton61);
        this.switchMapping.put(62,SwitchButton62);
        this.switchMapping.put(63,SwitchButton63);
        
        // Prevent the tabbed pane from being stupid
        this.KeyboardTab.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke("LEFT"), "none");
        this.KeyboardTab.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke("RIGHT"), "none");
           
        // Changing tabs
        //setupTabTraversalKeys(this.KeyboardTab);
    }
    
    /*private static void setupTabTraversalKeys(JTabbedPane tabbedPane)
    {
      KeyStroke ctrlTab = KeyStroke.getKeyStroke("ctrl TAB");
      KeyStroke ctrlShiftTab = KeyStroke.getKeyStroke("ctrl shift TAB");

      // Remove ctrl-tab from normal focus traversal
      Set<AWTKeyStroke> forwardKeys = new HashSet<AWTKeyStroke>(tabbedPane.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
      forwardKeys.remove(ctrlTab);
      tabbedPane.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forwardKeys);

      // Remove ctrl-shift-tab from normal focus traversal
      Set<AWTKeyStroke> backwardKeys = new HashSet<AWTKeyStroke>(tabbedPane.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
      backwardKeys.remove(ctrlShiftTab);
      tabbedPane.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backwardKeys);

      // Add keys to the tab's input map
      InputMap inputMap = tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      inputMap.put(ctrlTab, "navigateNext");
      inputMap.put(ctrlShiftTab, "navigatePrevious");
    }*/
    
    public Preferences getPrefs()
    {
        return this.prefs;
    }
    
    /**
     * Saves initialized component database to a file
     */
    public void saveState()
    {
        List<Map<Integer,String>> l = new ArrayList<>();
        
        for (int i = 0; i < this.locMapping.size(); i++)
        {
            Map<Integer,String> newMap = new HashMap<>();
            
            for (JButton b : this.locMapping.get(i).keySet())
            {
                Locomotive loc = this.locMapping.get(i).get(b);

                if (loc != null)
                {
                    newMap.put(this.rButtonMapping.get(b),loc.getName());
                }
            }
            
            l.add(newMap);
        }
        
        try
        {
            // Write object with ObjectOutputStream to disk using
            // FileOutputStream
            ObjectOutputStream obj_out = new ObjectOutputStream(
                new FileOutputStream(TrainControlUI.DATA_FILE_NAME));

            // Write object out to disk
            obj_out.writeObject(l);

            this.model.log("Saving UI state to disk.");
        } catch (IOException iOException)
        {
            this.model.log("Could not save UI state. " 
                + iOException.getMessage());
        }
    }

    /**
     * Restores list of initialized components from a file
     * @return 
     */
    public final List<Map<Integer,String>> restoreState()
    {
        List<Map<Integer,String>> instance = new ArrayList<>();
        
        try
        {
            // Read object using ObjectInputStream
            ObjectInputStream obj_in = new ObjectInputStream(
                new FileInputStream(TrainControlUI.DATA_FILE_NAME)
            );
            
            // Read an object
            Object obj = obj_in.readObject();

            if (obj instanceof List)
            {
                // Cast object
                instance = (List<Map<Integer,String>>) obj;
            }

            this.model.log("UI state loaded from file.");
        } 
        catch (IOException iOException)
        {
            this.model.log("No data file found, "
                    + "UI initializing with default data");
        } 
        catch (ClassNotFoundException classNotFoundException)
        {
            this.model.log("Bad data file for UI");            
        }

        return instance;
    }
    
    /**
     * Logs a message
     * @param message 
     */
    public void log(String message)
    {
        this.debugArea.insert(message + "\n", 0);
    }             
    
    public int getKeyboardOffset()
    {
        return (this.keyboardNumber - 1) * TrainControlUI.KEYBOARD_KEYS;
    }
    
    private void switchLocMapping(int locPageNum)
    {
        if (locPageNum <= TrainControlUI.NUM_LOC_MAPPINGS && locPageNum >= 1)
        {
            this.locMappingNumber = locPageNum;
        }
         
        if (TrainControlUI.NUM_LOC_MAPPINGS > 1)
        {
            if (this.locMappingNumber == 1)
            {
                this.PrevLocMapping.setEnabled(false);
            }
            else
            {
                this.PrevLocMapping.setEnabled(true);
            }

            if (this.locMappingNumber == TrainControlUI.NUM_LOC_MAPPINGS)
            {
                this.NextLocMapping.setEnabled(false);
            }
            else
            {
                this.NextLocMapping.setEnabled(true);
            }
            
            this.LocMappingNumberLabel.setText("Page " + this.locMappingNumber);
       }
       else 
       {
            this.NextLocMapping.setVisible(false);
            this.PrevLocMapping.setVisible(false);
            this.LocMappingNumberLabel.setText("");
       } 
        
       repaintMappings();        
    }
    
    private void switchKeyboard(int keyboardNum)
    {
        if (keyboardNum <= TrainControlUI.NUM_KEYBOARDS && keyboardNum >= 1)
        {
            this.keyboardNumber = keyboardNum;
        }
        
        if (this.keyboardNumber == 1)
        {
            this.PrevKeyboard.setEnabled(false);
        }
        else
        {
            this.PrevKeyboard.setEnabled(true);
        }
        
        if (this.keyboardNumber == TrainControlUI.NUM_KEYBOARDS)
        {
            this.NextKeyboard.setEnabled(false);
        }
        else
        {
            this.NextKeyboard.setEnabled(true);
        }
        
        this.KeyboardNumberLabel.setText("Keyboard " + this.keyboardNumber);
        
        Integer offset = this.getKeyboardOffset();
        
        for (Integer i = 1; i <= TrainControlUI.KEYBOARD_KEYS; i++)
        {
            this.switchMapping.get(i).setText((new Integer(i + offset)).toString());
        }
        
        repaintSwitches();
    }
    
    public void setViewListener(ViewListener listener) throws IOException
    {
        // Set the model reference
        this.model = listener;
                    
        // Add list of locomotives to dropdown
        refreshLocList();
                
        // Add list of routes to tab
        refreshRouteList();
        
        // Add list of layouts to tab
        this.LayoutList.setModel(new DefaultComboBoxModel(listener.getLayoutList().toArray()));
              
        // Add the first locomotive to the mapping
        if (!this.model.getLocList().isEmpty())
        {
            this.currentLocMapping().put(QButton, 
            this.model.getLocByName(this.model.getLocList().get(0)));
        }
        
        List<Map<Integer, String>> saveStates = this.restoreState();
        
        for (int j = 0; j < saveStates.size() && j < TrainControlUI.NUM_LOC_MAPPINGS; j++)
        {
            Map<Integer, String> saveState = saveStates.get(j);
     
            for(Integer i : saveState.keySet())
            {
                JButton b = this.buttonMapping.get(i);

                Locomotive l = this.model.getLocByName(saveState.get(i));

                if (l != null && b != null)
                {
                    this.model.log("Loading mapping for page " + Integer.toString(j + 1) + ", " + l.getName());
                    this.locMapping.get(j).put(b, l);
                }
            }
        }
        
        // Display the locomotive
        displayCurrentButtonLoc(QButton);
        
        // Display loc mapping page
        this.switchLocMapping(this.locMappingNumber);
        
        // Display keyboard
        this.switchKeyboard(this.keyboardNumber);
        
        // Display layout if applicable
        if (this.model.getLayoutList().isEmpty())
        {
            this.KeyboardTab.remove(this.layoutPanel);
        }
        else
        {
            repaintLayout();
        }
        
        // Hide routes if no routes
        if (this.model.getRouteList().isEmpty())
        {
            this.KeyboardTab.remove(this.RoutePanel);
        }
        
        HandScrollListener scrollListener = new HandScrollListener(InnerLayoutPanel);
        LayoutArea.getViewport().addMouseMotionListener(scrollListener);
        LayoutArea.getViewport().addMouseListener(scrollListener);
        
        // Periodically repaint the locomotive state
        /*ActionListener timerListener = new ActionListener(){
            public void actionPerformed(ActionEvent event){
             repaintLoc();
            }
        };
        Timer displayTimer = new Timer(500, timerListener);
        displayTimer.start();*/
                
        // Show window
        this.setVisible(true);
    }
    
    private void switchF(int fn)
    {        
        if (this.activeLoc != null)
        {
            this.fireF(fn, this.activeLoc.getF(fn) ? false : true);
        }
    }
    
    private void fireF(int fn, boolean state)
    {        
        if (this.activeLoc != null)
        {
            new Thread(() -> {
                this.activeLoc.setF(fn, state);
            }).start();
            //repaintLoc();
        }
    }
    
    private void mapLocToCurrentButton(String s)
    {
        Locomotive l = this.model.getLocByName(s);
         
        if (l != null)
        {
            // Unset if same as current loc
            if (this.currentLocMapping().get(this.currentButton) != null &&
                    this.currentLocMapping().get(this.currentButton).equals(l))
            {
                this.currentLocMapping().put(this.currentButton, null);
            }
            else
            {
                this.currentLocMapping().put(this.currentButton, l);
            }
            displayCurrentButtonLoc(this.currentButton);
        }
    }
    
    private void repaintMappings()
    {               
        // Only repaint a button if the locomotive has changed
        for(JButton b : this.labelMapping.keySet())
        {
            // Grey out if the active page corresponds to the active loc
            if (b.equals(this.currentButton) 
                    && this.lastLocMappingPainted == this.locMappingNumber)
            {
                b.setEnabled(false);
                this.labelMapping.get(b).setForeground(Color.red);
            }
            else
            {
                b.setEnabled(true);
                this.labelMapping.get(b).setForeground(Color.black);
            }
            
            Locomotive l = this.currentLocMapping().get(b);  
            
            if (l != null)
            {
                String name = l.getName();
                
                if (name.length() > 9)
                {
                    name = name.substring(0,9);
                }
                
                if (!this.labelMapping.get(b).getText().equals(name))
                {
                    this.labelMapping.get(b).setText(name);   
                    repaintIcon(b, l);
                }
            }
            else
            {
                if (!this.labelMapping.get(b).getText().equals("---"))
                {
                    this.labelMapping.get(b).setText("---");
                    repaintIcon(b, l);
                }
            }
        }        
    }
    
    @Override
    public void updatePowerState()
    {
        if (this.model.getPowerState())
        {
            this.PowerOff.setEnabled(true);
            this.OnButton.setEnabled(false);
        }
        else
        {
            this.PowerOff.setEnabled(false);
            this.OnButton.setEnabled(true);
        }
    }
    
    @Override
    public void repaintSwitches()
    {
        int offset = this.getKeyboardOffset();
        
        for (int i = 1; i <= TrainControlUI.KEYBOARD_KEYS; i++)
        {
            if (this.model.getAccessoryState(i + offset))
            {
                this.switchMapping.get(new Integer(i)).setSelected(true);
            }
            else
            {
                this.switchMapping.get(new Integer(i)).setSelected(false);
            }
        }
    }
    
    /**
     * Visually configures a button without an image
     * @param b 
     */
    private void noImageButton(JButton b)
    {
        b.setContentAreaFilled(true);
        b.setIcon(null);
        b.setForeground(new java.awt.Color(0, 0, 0));
        b.setOpaque(false);
    }
    
    /**
     * Returns a scaled locomotive image
     * @param url
     * @param size
     * @return
     * @throws IOException 
     */
    private Image getLocImage(String url, int size) throws IOException
    {
        String key = url + Integer.toString(size);
        
        if (!this.imageCache.containsKey(key))
        {
            Image img = ImageIO.read(new URL(url));
            float aspect = (float) img.getHeight(null) / (float) img.getWidth(null);
            this.imageCache.put(key, img.getScaledInstance(size, (int) (size * aspect), 1));
        }

        return this.imageCache.get(key);        
    }
    
    /**
     * Repaints a locomotive button
     * @param b
     * @param l 
     */
    private void repaintIcon(JButton b, Locomotive l)
    {
        if (b != null)
        {
            if (l == null)
            {
                noImageButton(b);
            }
            else if (l.getImageURL() != null)
            {
                try 
                {
                    b.setIcon(new javax.swing.ImageIcon(
                        getLocImage(l.getImageURL(), 65)
                    ));  
                    
                    b.setHorizontalTextPosition(SwingConstants.CENTER);
                    
                    b.setForeground(new java.awt.Color(255, 255, 255));
                    b.setContentAreaFilled(false);
                } 
                catch (IOException | NullPointerException e)
                {
                    noImageButton(b);

                    this.log("Failed to load image " + l.getImageURL());
                }
            }
            else
            {
                noImageButton(b);
            }
        }
    }
    
    @Override
    public void repaintLoc()
    {     
        if (this.activeLoc != null)
        {            
            String name = this.activeLoc.getName();

            if (name.length() > 18)
            {
                name = name.substring(0, 18);
            }

            // Only repaint icon if the locomotive is changed
            // Visual stuff
            if (!this.ActiveLocLabel.getText().equals(name))
            {
                new Thread(() -> {
                    repaintIcon(this.currentButton, this.activeLoc);
                    
                    if (this.activeLoc.getImageURL() != null)
                    {
                        try 
                        {
                            locIcon.setIcon(new javax.swing.ImageIcon(
                                getLocImage(this.activeLoc.getImageURL(), 85)
                            ));      
                            locIcon.setText("");
                        }
                        catch (Exception e)
                        {
                            locIcon.setIcon(null);
                        }
                    }
                    else
                    {
                        locIcon.setIcon(null);
                    }

                }).start();

                this.ActiveLocLabel.setText(name);

                this.CurrentKeyLabel.setText("Page " + this.locMappingNumber + " Button " 
                        + this.currentButton.getText()
                        + "  (" + this.model.getLocAddress(this.activeLoc.getName())
                        + ")"
                );

                for (int i = 0; i < this.activeLoc.getNumF(); i++)
                {
                    this.rFunctionMapping.get(i).setVisible(true);
                    this.rFunctionMapping.get(i).setEnabled(true);
                }
                for (int i = this.activeLoc.getNumF(); i < NUM_FN; i++)
                {
                    this.rFunctionMapping.get(i).setVisible(true);
                    this.rFunctionMapping.get(i).setEnabled(false);
                }

                this.Backward.setVisible(true);
                this.Forward.setVisible(true);
                this.SpeedSlider.setVisible(true);
                this.StopLoc.setVisible(true);
                this.FunctionTabs.setVisible(true);
            }

            // Loc state
            if (this.activeLoc.goingForward())
            {
                this.Forward.setSelected(true);
                this.Backward.setSelected(false);
            }
            else
            {
                this.Backward.setSelected(true);
                this.Forward.setSelected(false);
            }

            for (int i = 0; i < this.activeLoc.getNumF(); i++)
            {
                this.rFunctionMapping.get(i).setSelected(this.activeLoc.getF(i));
            }
            for (int i = this.activeLoc.getNumF(); i < NUM_FN; i++)
            {

                this.rFunctionMapping.get(i).setSelected(this.activeLoc.getF(i));
            }

            this.SpeedSlider.setValue(this.activeLoc.getSpeed());  
        }
        else
        {
            locIcon.setIcon(null);
            locIcon.setText("");

            this.ActiveLocLabel.setText("No Locomotive");

            this.CurrentKeyLabel.setText("Page " + this.locMappingNumber + " Button " 
                       + this.currentButton.getText()    
            );

            this.Backward.setVisible(false);
            this.Forward.setVisible(false);
            this.SpeedSlider.setVisible(false);
            this.StopLoc.setVisible(false);
            this.FunctionTabs.setVisible(false);

            for (int i = 0; i < NUM_FN; i++)
            {
                this.rFunctionMapping.get(i).setVisible(false);
            }
        }
    }
    
    private Map<JButton, Locomotive> currentLocMapping()
    {
        return this.locMapping.get(this.locMappingNumber - 1);
    }
    
    private void displayCurrentButtonLoc(javax.swing.JButton b)
    {
        if (this.currentButton != null)
        {
            this.currentButton.setEnabled(true);
            this.labelMapping.get(this.currentButton).setForeground(Color.black);
        }
        
        if (b != null)
        {
            this.currentButton = b;

            this.currentButton.setEnabled(false);

            this.activeLoc = this.currentLocMapping().get(this.currentButton);
            
            this.labelMapping.get(this.currentButton).setForeground(Color.red);
        }
        
        this.lastLocMappingPainted = this.locMappingNumber;
        
        repaintLoc();  
        repaintMappings();
    }

    private void setLocSpeed(int speed)
    {
        if (this.activeLoc != null)
        {
            new Thread(() -> {
                this.activeLoc.setSpeed(speed);
            }).start();
            //repaintLoc();  not needed because the network will update it
        }
    }     
    
    private void stopLoc()
    {
        if (this.activeLoc != null)
        {
            new Thread(() -> {
                this.activeLoc.stop();
            }).start();
            //repaintLoc();
        }
    }
    
    private void backwardLoc()
    {
        if (this.activeLoc != null && this.activeLoc.goingForward())
        {
            new Thread(() -> {
                this.activeLoc.stop().setDirection(Locomotive.locDirection.DIR_BACKWARD);
                this.Forward.setSelected(false);
                this.Backward.setSelected(true);
                //repaintLoc();  
            }).start();
        } 
    }
    
    private void forwardLoc()
    {
        if(this.activeLoc != null && this.activeLoc.goingBackward())
        {
            new Thread(() -> {
                this.activeLoc.stop().setDirection(Locomotive.locDirection.DIR_FORWARD);
                this.Forward.setSelected(true);
                this.Backward.setSelected(false);
                //repaintLoc();  
            }).start();
        }
    }
    
    private void switchDirection()
    {
        if(this.activeLoc != null)
        {
             if (this.activeLoc.goingForward())
             {
                 backwardLoc();
             }
             else
             {
                 forwardLoc();
             }
        }  
    }
      
    private void go()
    {
        this.model.go();
    }
    
    private void stop()
    {
        this.model.stop();
    }
    
    private void refreshLocList()
    {
        List<String> locs = model.getLocList();
        
        List<String> filteredLocs = new ArrayList<>();
        
        String filter = this.FilterLocomotive.getText().toLowerCase();
        
        if ("".equals(filter))
        {
             this.LocFunctionsPanel.requestFocus();
        }
        
        for(String loc : locs)
        {
            if ("".equals(filter) || loc.toLowerCase().contains(filter))
            {
                filteredLocs.add(loc);
            }
        }
        
        // Add list of locomotives to dropdown
        this.LocomotiveList.setListData(filteredLocs.toArray());     
        
        // Highlight the correct menu item
        if (null != this.activeLoc)
        {
            this.LocomotiveList.setSelectedValue(this.activeLoc.getName(), true);
        }
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
        jList1 = new javax.swing.JList();
        jPopupMenu1 = new javax.swing.JPopupMenu();
        buttonGroup1 = new javax.swing.ButtonGroup();
        KeyboardTab = new javax.swing.JTabbedPane();
        LocControlPanel = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        LocContainer = new javax.swing.JPanel();
        CButton = new javax.swing.JButton();
        VButton = new javax.swing.JButton();
        ZButton = new javax.swing.JButton();
        XButton = new javax.swing.JButton();
        MButton = new javax.swing.JButton();
        BButton = new javax.swing.JButton();
        NButton = new javax.swing.JButton();
        XLabel = new javax.swing.JLabel();
        ZLabel = new javax.swing.JLabel();
        VLabel = new javax.swing.JLabel();
        CLabel = new javax.swing.JLabel();
        OButton = new javax.swing.JButton();
        PButton = new javax.swing.JButton();
        AButton = new javax.swing.JButton();
        SButton = new javax.swing.JButton();
        DButton = new javax.swing.JButton();
        FButton = new javax.swing.JButton();
        GButton = new javax.swing.JButton();
        HButton = new javax.swing.JButton();
        JButton = new javax.swing.JButton();
        LButton = new javax.swing.JButton();
        KButton = new javax.swing.JButton();
        BLabel = new javax.swing.JLabel();
        NLabel = new javax.swing.JLabel();
        MLabel = new javax.swing.JLabel();
        DLabel = new javax.swing.JLabel();
        SLabel = new javax.swing.JLabel();
        ALabel = new javax.swing.JLabel();
        PLabel = new javax.swing.JLabel();
        YButton = new javax.swing.JButton();
        OLabel = new javax.swing.JLabel();
        RButton = new javax.swing.JButton();
        TButton = new javax.swing.JButton();
        WButton = new javax.swing.JButton();
        EButton = new javax.swing.JButton();
        QButton = new javax.swing.JButton();
        IButton = new javax.swing.JButton();
        UButton = new javax.swing.JButton();
        KLabel = new javax.swing.JLabel();
        LLabel = new javax.swing.JLabel();
        HLabel = new javax.swing.JLabel();
        JLabel = new javax.swing.JLabel();
        FLabel = new javax.swing.JLabel();
        GLabel = new javax.swing.JLabel();
        ELabel = new javax.swing.JLabel();
        WLabel = new javax.swing.JLabel();
        QLabel = new javax.swing.JLabel();
        TLabel = new javax.swing.JLabel();
        YLabel = new javax.swing.JLabel();
        ULabel = new javax.swing.JLabel();
        ILabel = new javax.swing.JLabel();
        RLabel = new javax.swing.JLabel();
        PrevLocMapping = new javax.swing.JButton();
        NextLocMapping = new javax.swing.JButton();
        LocMappingNumberLabel = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        UpArrow = new javax.swing.JButton();
        DownArrow = new javax.swing.JButton();
        RightArrow = new javax.swing.JButton();
        LeftArrow = new javax.swing.JButton();
        SpacebarButton = new javax.swing.JButton();
        SlowStopLabel = new javax.swing.JLabel();
        EStopLabel = new javax.swing.JLabel();
        ShiftButton = new javax.swing.JButton();
        DirectionLabel = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        OtherDirectionLabel = new javax.swing.JLabel();
        AltEmergencyStop = new javax.swing.JButton();
        jLabel8 = new javax.swing.JLabel();
        PrimaryControls = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        ZeroButton = new javax.swing.JButton();
        EightButton = new javax.swing.JButton();
        NineButton = new javax.swing.JButton();
        SevenButton = new javax.swing.JButton();
        FourButton = new javax.swing.JButton();
        ThreeButton = new javax.swing.JButton();
        TwoButton = new javax.swing.JButton();
        OneButton = new javax.swing.JButton();
        SixButton = new javax.swing.JButton();
        FiveButton = new javax.swing.JButton();
        ZeroPercentSpeedLabel = new javax.swing.JLabel();
        FullSpeedLabel = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        layoutPanel = new javax.swing.JPanel();
        LayoutList = new javax.swing.JComboBox();
        layoutListLabel = new javax.swing.JLabel();
        LayoutArea = new javax.swing.JScrollPane();
        InnerLayoutPanel = new javax.swing.JPanel();
        sizeLabel = new javax.swing.JLabel();
        SizeList = new javax.swing.JComboBox();
        layoutNewWindow = new javax.swing.JButton();
        RoutePanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        DeleteRouteButton = new javax.swing.JButton();
        jScrollPane5 = new javax.swing.JScrollPane();
        RouteList = new javax.swing.JTable();
        KeyboardPanel = new javax.swing.JPanel();
        KeyboardLabel = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        SwitchButton1 = new javax.swing.JToggleButton();
        SwitchButton2 = new javax.swing.JToggleButton();
        SwitchButton3 = new javax.swing.JToggleButton();
        SwitchButton4 = new javax.swing.JToggleButton();
        SwitchButton5 = new javax.swing.JToggleButton();
        SwitchButton6 = new javax.swing.JToggleButton();
        SwitchButton7 = new javax.swing.JToggleButton();
        SwitchButton8 = new javax.swing.JToggleButton();
        SwitchButton9 = new javax.swing.JToggleButton();
        SwitchButton10 = new javax.swing.JToggleButton();
        SwitchButton11 = new javax.swing.JToggleButton();
        SwitchButton12 = new javax.swing.JToggleButton();
        SwitchButton13 = new javax.swing.JToggleButton();
        SwitchButton14 = new javax.swing.JToggleButton();
        SwitchButton15 = new javax.swing.JToggleButton();
        SwitchButton16 = new javax.swing.JToggleButton();
        SwitchButton17 = new javax.swing.JToggleButton();
        SwitchButton18 = new javax.swing.JToggleButton();
        SwitchButton19 = new javax.swing.JToggleButton();
        SwitchButton21 = new javax.swing.JToggleButton();
        SwitchButton22 = new javax.swing.JToggleButton();
        SwitchButton23 = new javax.swing.JToggleButton();
        SwitchButton24 = new javax.swing.JToggleButton();
        SwitchButton26 = new javax.swing.JToggleButton();
        SwitchButton27 = new javax.swing.JToggleButton();
        SwitchButton29 = new javax.swing.JToggleButton();
        SwitchButton28 = new javax.swing.JToggleButton();
        SwitchButton30 = new javax.swing.JToggleButton();
        SwitchButton31 = new javax.swing.JToggleButton();
        SwitchButton32 = new javax.swing.JToggleButton();
        SwitchButton33 = new javax.swing.JToggleButton();
        SwitchButton34 = new javax.swing.JToggleButton();
        SwitchButton35 = new javax.swing.JToggleButton();
        SwitchButton36 = new javax.swing.JToggleButton();
        SwitchButton37 = new javax.swing.JToggleButton();
        SwitchButton38 = new javax.swing.JToggleButton();
        SwitchButton39 = new javax.swing.JToggleButton();
        SwitchButton40 = new javax.swing.JToggleButton();
        SwitchButton41 = new javax.swing.JToggleButton();
        SwitchButton42 = new javax.swing.JToggleButton();
        SwitchButton43 = new javax.swing.JToggleButton();
        SwitchButton44 = new javax.swing.JToggleButton();
        SwitchButton45 = new javax.swing.JToggleButton();
        SwitchButton46 = new javax.swing.JToggleButton();
        SwitchButton47 = new javax.swing.JToggleButton();
        SwitchButton48 = new javax.swing.JToggleButton();
        SwitchButton49 = new javax.swing.JToggleButton();
        SwitchButton50 = new javax.swing.JToggleButton();
        SwitchButton51 = new javax.swing.JToggleButton();
        SwitchButton52 = new javax.swing.JToggleButton();
        SwitchButton53 = new javax.swing.JToggleButton();
        SwitchButton54 = new javax.swing.JToggleButton();
        SwitchButton55 = new javax.swing.JToggleButton();
        SwitchButton57 = new javax.swing.JToggleButton();
        SwitchButton58 = new javax.swing.JToggleButton();
        SwitchButton59 = new javax.swing.JToggleButton();
        SwitchButton60 = new javax.swing.JToggleButton();
        SwitchButton61 = new javax.swing.JToggleButton();
        SwitchButton62 = new javax.swing.JToggleButton();
        SwitchButton63 = new javax.swing.JToggleButton();
        SwitchButton20 = new javax.swing.JToggleButton();
        SwitchButton56 = new javax.swing.JToggleButton();
        SwitchButton25 = new javax.swing.JToggleButton();
        jPanel10 = new javax.swing.JPanel();
        PrevKeyboard = new javax.swing.JButton();
        KeyboardNumberLabel = new javax.swing.JLabel();
        NextKeyboard = new javax.swing.JButton();
        KeyboardLabel1 = new javax.swing.JLabel();
        ManageLocPanel = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        LocTypeMM2 = new javax.swing.JRadioButton();
        jLabel4 = new javax.swing.JLabel();
        AddLocButton = new javax.swing.JButton();
        LocAddressInput = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        LocNameInput = new javax.swing.JTextField();
        LocTypeMFX = new javax.swing.JRadioButton();
        AddNewLocLabel = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        DeleteLocButton = new javax.swing.JButton();
        RenameLocButton = new javax.swing.JButton();
        SyncLocomotive = new javax.swing.JButton();
        EditExistingLocLabel = new javax.swing.JLabel();
        EditExistingLocLabel1 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        clearButton = new javax.swing.JButton();
        SyncButton = new javax.swing.JButton();
        TurnOffFnButton = new javax.swing.JButton();
        TurnOnLightsButton = new javax.swing.JButton();
        EditExistingLocLabel2 = new javax.swing.JLabel();
        jPanel11 = new javax.swing.JPanel();
        FilterLocomotive = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        logPanel = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        debugArea = new javax.swing.JTextArea();
        LocFunctionsPanel = new javax.swing.JPanel();
        OnButton = new javax.swing.JButton();
        PowerOff = new javax.swing.JButton();
        ActiveLocLabel = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        LocomotiveList = new javax.swing.JList();
        SpeedSlider = new javax.swing.JSlider();
        Backward = new javax.swing.JToggleButton();
        Forward = new javax.swing.JToggleButton();
        StopLoc = new javax.swing.JButton();
        CurrentKeyLabel = new javax.swing.JLabel();
        locIcon = new javax.swing.JLabel();
        changeLocomotiveLabel = new javax.swing.JLabel();
        FunctionTabs = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        F8 = new javax.swing.JToggleButton();
        F9 = new javax.swing.JToggleButton();
        F7 = new javax.swing.JToggleButton();
        F12 = new javax.swing.JToggleButton();
        F13 = new javax.swing.JToggleButton();
        F10 = new javax.swing.JToggleButton();
        F11 = new javax.swing.JToggleButton();
        F14 = new javax.swing.JToggleButton();
        F15 = new javax.swing.JToggleButton();
        F0 = new javax.swing.JToggleButton();
        F3 = new javax.swing.JToggleButton();
        F1 = new javax.swing.JToggleButton();
        F2 = new javax.swing.JToggleButton();
        F4 = new javax.swing.JToggleButton();
        F5 = new javax.swing.JToggleButton();
        F6 = new javax.swing.JToggleButton();
        jPanel3 = new javax.swing.JPanel();
        F24 = new javax.swing.JToggleButton();
        F16 = new javax.swing.JToggleButton();
        F25 = new javax.swing.JToggleButton();
        F19 = new javax.swing.JToggleButton();
        F17 = new javax.swing.JToggleButton();
        F23 = new javax.swing.JToggleButton();
        F28 = new javax.swing.JToggleButton();
        F29 = new javax.swing.JToggleButton();
        F26 = new javax.swing.JToggleButton();
        F27 = new javax.swing.JToggleButton();
        F30 = new javax.swing.JToggleButton();
        F31 = new javax.swing.JToggleButton();
        F18 = new javax.swing.JToggleButton();
        F20 = new javax.swing.JToggleButton();
        F21 = new javax.swing.JToggleButton();
        F22 = new javax.swing.JToggleButton();

        jList1.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(jList1);

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Marklin Layout Controller v" + MarklinControlStation.VERSION);
        setAlwaysOnTop(true);
        setBackground(new java.awt.Color(255, 255, 255));
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                WindowClosed(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                LocControlPanelKeyPressed(evt);
            }
        });

        KeyboardTab.setBackground(new java.awt.Color(255, 255, 255));
        KeyboardTab.setToolTipText(null);
        KeyboardTab.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                LocControlPanelKeyPressed(evt);
            }
        });

        LocControlPanel.setBackground(new java.awt.Color(238, 238, 238));
        LocControlPanel.setToolTipText(null);
        LocControlPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                LocControlPanelKeyPressed(evt);
            }
        });

        jLabel5.setForeground(new java.awt.Color(0, 0, 115));
        jLabel5.setText("Locomotive Mapping");

        LocContainer.setBackground(new java.awt.Color(245, 245, 245));
        LocContainer.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        CButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        CButton.setText("C");
        CButton.setFocusable(false);
        CButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        VButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        VButton.setText("V");
        VButton.setFocusable(false);
        VButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        ZButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        ZButton.setText("Z");
        ZButton.setFocusable(false);
        ZButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        XButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        XButton.setText("X");
        XButton.setFocusable(false);
        XButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        MButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        MButton.setText("M");
        MButton.setFocusable(false);
        MButton.setMaximumSize(new java.awt.Dimension(39, 23));
        MButton.setMinimumSize(new java.awt.Dimension(39, 23));
        MButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        BButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        BButton.setText("B");
        BButton.setFocusable(false);
        BButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        NButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        NButton.setText("N");
        NButton.setFocusable(false);
        NButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        XLabel.setText("jLabel26");

        ZLabel.setText("jLabel25");

        VLabel.setText("jLabel28");

        CLabel.setText("jLabel27");

        OButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        OButton.setText("O");
        OButton.setFocusable(false);
        OButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        PButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        PButton.setText("P");
        PButton.setFocusable(false);
        PButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        AButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        AButton.setText("A");
        AButton.setFocusable(false);
        AButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        SButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SButton.setText("S");
        SButton.setFocusable(false);
        SButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        DButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        DButton.setText("D");
        DButton.setFocusable(false);
        DButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        FButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        FButton.setText("F");
        FButton.setFocusable(false);
        FButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        GButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        GButton.setText("G");
        GButton.setFocusable(false);
        GButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        HButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        HButton.setText("H");
        HButton.setFocusable(false);
        HButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        JButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        JButton.setText("J");
        JButton.setFocusable(false);
        JButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        LButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        LButton.setText("L");
        LButton.setFocusable(false);
        LButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        KButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        KButton.setText("K");
        KButton.setFocusable(false);
        KButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        BLabel.setText("jLabel29");

        NLabel.setText("jLabel30");

        MLabel.setText("jLabel31");

        DLabel.setText("jLabel18");

        SLabel.setText("jLabel17");

        ALabel.setText("jLabel16");

        PLabel.setText("jLabel15");

        YButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        YButton.setText("Y");
        YButton.setFocusable(false);
        YButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        OLabel.setText("jLabel14");

        RButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        RButton.setText("R");
        RButton.setFocusable(false);
        RButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        TButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        TButton.setText("T");
        TButton.setFocusable(false);
        TButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        WButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        WButton.setText("W");
        WButton.setFocusable(false);
        WButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        EButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        EButton.setText("E");
        EButton.setFocusable(false);
        EButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        QButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        QButton.setText("Q");
        QButton.setFocusable(false);
        QButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        IButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        IButton.setText("I");
        IButton.setFocusable(false);
        IButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        UButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        UButton.setText("U");
        UButton.setFocusable(false);
        UButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LetterButtonPressed(evt);
            }
        });

        KLabel.setText("jLabel23");

        LLabel.setText("jLabel24");

        HLabel.setText("jLabel21");

        JLabel.setText("jLabel22");

        FLabel.setText("jLabel19");

        GLabel.setText("jLabel20");

        ELabel.setText("jLabel8");

        WLabel.setText("jLabel7");

        QLabel.setText("jLabel6");

        TLabel.setText("jLabel10");

        YLabel.setText("jLabel11");

        ULabel.setText("jLabel12");

        ILabel.setText("jLabel13");

        RLabel.setText("jLabel9");

        PrevLocMapping.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        PrevLocMapping.setText("<<< ,");
        PrevLocMapping.setFocusable(false);
        PrevLocMapping.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PrevLocMappingActionPerformed(evt);
            }
        });

        NextLocMapping.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        NextLocMapping.setText(". >>>");
        NextLocMapping.setFocusable(false);
        NextLocMapping.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NextLocMappingActionPerformed(evt);
            }
        });

        LocMappingNumberLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        LocMappingNumberLabel.setText("Page");
        LocMappingNumberLabel.setFocusable(false);

        javax.swing.GroupLayout LocContainerLayout = new javax.swing.GroupLayout(LocContainer);
        LocContainer.setLayout(LocContainerLayout);
        LocContainerLayout.setHorizontalGroup(
            LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocContainerLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ZButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ZLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(XLabel)
                            .addComponent(XButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(CLabel)
                            .addComponent(CButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(VLabel)
                            .addComponent(VButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(BLabel)
                            .addComponent(BButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(NLabel)
                            .addComponent(NButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(MLabel)
                            .addComponent(MButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(LocContainerLayout.createSequentialGroup()
                                .addGap(75, 75, 75)
                                .addComponent(PrevLocMapping)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(NextLocMapping))
                            .addGroup(LocContainerLayout.createSequentialGroup()
                                .addGap(78, 78, 78)
                                .addComponent(LocMappingNumberLabel))))
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(AButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ALabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(SLabel)
                            .addComponent(SButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(DLabel)
                            .addComponent(DButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(FLabel)
                            .addComponent(FButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(GLabel)
                            .addComponent(GButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(HLabel)
                            .addComponent(HButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(JLabel)
                            .addComponent(JButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(KLabel)
                            .addComponent(KButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(LLabel)
                            .addComponent(LButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(QButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(QLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(WLabel)
                            .addComponent(WButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ELabel)
                            .addComponent(EButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RLabel)
                            .addComponent(RButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(TLabel)
                            .addComponent(TButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(YLabel)
                            .addComponent(YButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ULabel)
                            .addComponent(UButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ILabel)
                            .addComponent(IButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(OLabel)
                            .addComponent(OButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(PLabel)
                            .addComponent(PButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        LocContainerLayout.setVerticalGroup(
            LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocContainerLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(WButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(EButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(RButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(TButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(YButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(UButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(QButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(IButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(OButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(PButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(QLabel)
                    .addComponent(WLabel)
                    .addComponent(ELabel)
                    .addComponent(RLabel)
                    .addComponent(TLabel)
                    .addComponent(YLabel)
                    .addComponent(ULabel)
                    .addComponent(ILabel)
                    .addComponent(OLabel)
                    .addComponent(PLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(GButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(HButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(JButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(KButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(LButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(AButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(DButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(FButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ALabel)
                    .addComponent(SLabel)
                    .addComponent(DLabel)
                    .addComponent(FLabel)
                    .addComponent(GLabel)
                    .addComponent(HLabel)
                    .addComponent(JLabel)
                    .addComponent(KLabel)
                    .addComponent(LLabel))
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                            .addComponent(ZButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(XButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(CButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(VButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(BButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(MButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(NButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(ZLabel)
                            .addComponent(XLabel)
                            .addComponent(CLabel)
                            .addComponent(VLabel)
                            .addComponent(BLabel)
                            .addComponent(NLabel)
                            .addComponent(MLabel))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, LocContainerLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(LocMappingNumberLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(PrevLocMapping)
                            .addComponent(NextLocMapping))
                        .addGap(22, 22, 22))))
        );

        jPanel6.setBackground(new java.awt.Color(245, 245, 245));
        jPanel6.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        UpArrow.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        UpArrow.setText("↑");
        UpArrow.setToolTipText("Increase Speed");
        UpArrow.setFocusable(false);
        UpArrow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpArrowLetterButtonPressed(evt);
            }
        });

        DownArrow.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        DownArrow.setText("↓");
        DownArrow.setToolTipText("Decrease Speed");
        DownArrow.setFocusable(false);
        DownArrow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DownArrowLetterButtonPressed(evt);
            }
        });

        RightArrow.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        RightArrow.setText("→");
        RightArrow.setToolTipText("Switch Direction");
        RightArrow.setFocusable(false);
        RightArrow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RightArrowLetterButtonPressed(evt);
            }
        });

        LeftArrow.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        LeftArrow.setText("←");
        LeftArrow.setFocusable(false);
        LeftArrow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LeftArrowLetterButtonPressed(evt);
            }
        });

        SpacebarButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SpacebarButton.setText("Spacebar");
        SpacebarButton.setToolTipText("Spacebar: emergency stop");
        SpacebarButton.setFocusable(false);
        SpacebarButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SpacebarButtonActionPerformed(evt);
            }
        });

        SlowStopLabel.setText("Slow Stop");

        EStopLabel.setText("Stop All");

        ShiftButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        ShiftButton.setText("Shift");
        ShiftButton.setToolTipText("Shift: stop locomotive");
        ShiftButton.setFocusable(false);
        ShiftButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ShiftButtonActionPerformed(evt);
            }
        });

        DirectionLabel.setText("Direction");

        jLabel7.setText("Speed");

        OtherDirectionLabel.setText("Direction");

        AltEmergencyStop.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        AltEmergencyStop.setText("Enter");
        AltEmergencyStop.setToolTipText("Shift: stop locomotive");
        AltEmergencyStop.setFocusable(false);
        AltEmergencyStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AltEmergencyStopActionPerformed(evt);
            }
        });

        jLabel8.setText("Instant Stop");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(EStopLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(AltEmergencyStop, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(jLabel8)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(SpacebarButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ShiftButton, javax.swing.GroupLayout.PREFERRED_SIZE, 153, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SlowStopLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(LeftArrow, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(DirectionLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel7)
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(DownArrow, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(UpArrow, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(7, 7, 7)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(OtherDirectionLabel)
                    .addComponent(RightArrow, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ShiftButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(RightArrow, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(UpArrow)
                        .addGap(1, 1, 1)
                        .addComponent(DownArrow))
                    .addComponent(LeftArrow, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(AltEmergencyStop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(SpacebarButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(EStopLabel)
                        .addComponent(jLabel8))
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(SlowStopLabel)
                        .addComponent(DirectionLabel)
                        .addComponent(jLabel7)
                        .addComponent(OtherDirectionLabel)))
                .addContainerGap())
        );

        PrimaryControls.setForeground(new java.awt.Color(0, 0, 155));
        PrimaryControls.setText("Primary Controls");

        jPanel7.setBackground(new java.awt.Color(245, 245, 245));
        jPanel7.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        ZeroButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        ZeroButton.setText("0");
        ZeroButton.setFocusable(false);
        ZeroButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ZeroButtonActionPerformed(evt);
            }
        });

        EightButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        EightButton.setText("8");
        EightButton.setFocusable(false);
        EightButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EightButtonActionPerformed(evt);
            }
        });

        NineButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        NineButton.setText("9");
        NineButton.setFocusable(false);
        NineButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NineButtonActionPerformed(evt);
            }
        });

        SevenButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SevenButton.setText("7");
        SevenButton.setFocusable(false);
        SevenButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SevenButtonActionPerformed(evt);
            }
        });

        FourButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        FourButton.setText("4");
        FourButton.setFocusable(false);
        FourButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FourButtonActionPerformed(evt);
            }
        });

        ThreeButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        ThreeButton.setText("3");
        ThreeButton.setFocusable(false);
        ThreeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ThreeButtonActionPerformed(evt);
            }
        });

        TwoButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        TwoButton.setText("2");
        TwoButton.setFocusable(false);
        TwoButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TwoButtonActionPerformed(evt);
            }
        });

        OneButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        OneButton.setText("1");
        OneButton.setFocusable(false);
        OneButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OneButtonActionPerformed(evt);
            }
        });

        SixButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SixButton.setText("6");
        SixButton.setFocusable(false);
        SixButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SixButtonActionPerformed(evt);
            }
        });

        FiveButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        FiveButton.setText("5");
        FiveButton.setFocusable(false);
        FiveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FiveButtonActionPerformed(evt);
            }
        });

        ZeroPercentSpeedLabel.setText("0%");

        FullSpeedLabel.setText("100%");

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(OneButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(TwoButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ThreeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(FourButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(FiveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(SixButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(SevenButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(EightButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(NineButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ZeroButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(ZeroPercentSpeedLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(FullSpeedLabel)))
                .addContainerGap())
        );

        jPanel7Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {EightButton, FiveButton, FourButton, NineButton, OneButton, SevenButton, SixButton, ThreeButton, TwoButton, ZeroButton});

        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(SevenButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(FourButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ThreeButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(TwoButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(OneButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SixButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(FiveButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(EightButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(NineButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(ZeroButton, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ZeroPercentSpeedLabel)
                    .addComponent(FullSpeedLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel6.setForeground(new java.awt.Color(0, 0, 155));
        jLabel6.setText("Locomotive Speed");

        javax.swing.GroupLayout LocControlPanelLayout = new javax.swing.GroupLayout(LocControlPanel);
        LocControlPanel.setLayout(LocControlPanelLayout);
        LocControlPanelLayout.setHorizontalGroup(
            LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5)
                            .addGroup(LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(LocContainer, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(PrimaryControls)))
                    .addComponent(jLabel6))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        LocControlPanelLayout.setVerticalGroup(
            LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(LocContainer, javax.swing.GroupLayout.PREFERRED_SIZE, 223, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(PrimaryControls)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        KeyboardTab.addTab("Locomotive Control", LocControlPanel);

        layoutPanel.setBackground(new java.awt.Color(238, 238, 238));
        layoutPanel.setFocusable(false);

        LayoutList.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        LayoutList.setFocusable(false);
        LayoutList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                LayoutListMouseClicked(evt);
            }
        });
        LayoutList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LayoutListActionPerformed(evt);
            }
        });

        layoutListLabel.setForeground(new java.awt.Color(0, 0, 115));
        layoutListLabel.setText("Choose Layout");

        LayoutArea.setBackground(new java.awt.Color(255, 255, 255));

        InnerLayoutPanel.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout InnerLayoutPanelLayout = new javax.swing.GroupLayout(InnerLayoutPanel);
        InnerLayoutPanel.setLayout(InnerLayoutPanelLayout);
        InnerLayoutPanelLayout.setHorizontalGroup(
            InnerLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 731, Short.MAX_VALUE)
        );
        InnerLayoutPanelLayout.setVerticalGroup(
            InnerLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 453, Short.MAX_VALUE)
        );

        LayoutArea.setViewportView(InnerLayoutPanel);

        sizeLabel.setForeground(new java.awt.Color(0, 0, 115));
        sizeLabel.setText("Size");

        SizeList.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "30", "60" }));
        SizeList.setFocusable(false);
        SizeList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                SizeListMouseClicked(evt);
            }
        });
        SizeList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SizeListActionPerformed(evt);
            }
        });

        layoutNewWindow.setText("Show in new window");
        layoutNewWindow.setFocusable(false);
        layoutNewWindow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                layoutNewWindowActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layoutPanelLayout = new javax.swing.GroupLayout(layoutPanel);
        layoutPanel.setLayout(layoutPanelLayout);
        layoutPanelLayout.setHorizontalGroup(
            layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layoutPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(LayoutArea, javax.swing.GroupLayout.DEFAULT_SIZE, 723, Short.MAX_VALUE)
                    .addGroup(layoutPanelLayout.createSequentialGroup()
                        .addComponent(layoutListLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LayoutList, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sizeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(SizeList, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(layoutNewWindow)))
                .addContainerGap())
        );
        layoutPanelLayout.setVerticalGroup(
            layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layoutPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(LayoutArea)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(LayoutList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(layoutListLabel)
                    .addComponent(sizeLabel)
                    .addComponent(SizeList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(layoutNewWindow))
                .addContainerGap())
        );

        KeyboardTab.addTab("Layout", layoutPanel);

        RoutePanel.setBackground(new java.awt.Color(238, 238, 238));
        RoutePanel.setFocusable(false);

        jLabel2.setForeground(new java.awt.Color(0, 0, 115));
        jLabel2.setText("Routes");

        DeleteRouteButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        DeleteRouteButton.setText("Delete Route");
        DeleteRouteButton.setFocusable(false);
        DeleteRouteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeleteRouteButtonActionPerformed(evt);
            }
        });

        RouteList.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        RouteList.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        RouteList.setFocusable(false);
        RouteList.setGridColor(new java.awt.Color(238, 238, 238));
        RouteList.setIntercellSpacing(new java.awt.Dimension(0, 0));
        RouteList.setRowHeight(30);
        RouteList.setRowSelectionAllowed(false);
        RouteList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        RouteList.setTableHeader(null);
        RouteList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                RouteListMouseClicked(evt);
            }
        });
        jScrollPane5.setViewportView(RouteList);

        javax.swing.GroupLayout RoutePanelLayout = new javax.swing.GroupLayout(RoutePanel);
        RoutePanel.setLayout(RoutePanelLayout);
        RoutePanelLayout.setHorizontalGroup(
            RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RoutePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 723, Short.MAX_VALUE)
                    .addGroup(RoutePanelLayout.createSequentialGroup()
                        .addGroup(RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(DeleteRouteButton))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        RoutePanelLayout.setVerticalGroup(
            RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RoutePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 433, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(DeleteRouteButton)
                .addContainerGap())
        );

        KeyboardTab.addTab("Routes", RoutePanel);

        KeyboardPanel.setBackground(new java.awt.Color(238, 238, 238));
        KeyboardPanel.setToolTipText(null);
        KeyboardPanel.setFocusable(false);

        KeyboardLabel.setForeground(new java.awt.Color(0, 0, 115));
        KeyboardLabel.setText("Signals and Switches");
        KeyboardLabel.setFocusable(false);

        jPanel9.setBackground(new java.awt.Color(245, 245, 245));
        jPanel9.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel9.setFocusable(false);

        SwitchButton1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton1.setText("1");
        SwitchButton1.setFocusable(false);
        SwitchButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton2.setText("2");
        SwitchButton2.setFocusable(false);
        SwitchButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton3.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton3.setText("3");
        SwitchButton3.setFocusable(false);
        SwitchButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton4.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton4.setText("3");
        SwitchButton4.setFocusable(false);
        SwitchButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton5.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton5.setText("3");
        SwitchButton5.setFocusable(false);
        SwitchButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton6.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton6.setText("3");
        SwitchButton6.setFocusable(false);
        SwitchButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton7.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton7.setText("3");
        SwitchButton7.setFocusable(false);
        SwitchButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton8.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton8.setText("3");
        SwitchButton8.setFocusable(false);
        SwitchButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton9.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton9.setText("3");
        SwitchButton9.setFocusable(false);
        SwitchButton9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton10.setText("3");
        SwitchButton10.setFocusable(false);
        SwitchButton10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton11.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton11.setText("3");
        SwitchButton11.setFocusable(false);
        SwitchButton11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton12.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton12.setText("3");
        SwitchButton12.setFocusable(false);
        SwitchButton12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton13.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton13.setText("3");
        SwitchButton13.setFocusable(false);
        SwitchButton13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton14.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton14.setText("3");
        SwitchButton14.setFocusable(false);
        SwitchButton14.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton15.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton15.setText("3");
        SwitchButton15.setFocusable(false);
        SwitchButton15.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton16.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton16.setText("3");
        SwitchButton16.setFocusable(false);
        SwitchButton16.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton17.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton17.setText("3");
        SwitchButton17.setFocusable(false);
        SwitchButton17.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton18.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton18.setText("3");
        SwitchButton18.setFocusable(false);
        SwitchButton18.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton19.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton19.setText("3");
        SwitchButton19.setFocusable(false);
        SwitchButton19.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton21.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton21.setText("3");
        SwitchButton21.setFocusable(false);
        SwitchButton21.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton22.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton22.setText("3");
        SwitchButton22.setFocusable(false);
        SwitchButton22.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton23.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton23.setText("3");
        SwitchButton23.setFocusable(false);
        SwitchButton23.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton24.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton24.setText("3");
        SwitchButton24.setFocusable(false);
        SwitchButton24.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton26.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton26.setText("3");
        SwitchButton26.setFocusable(false);
        SwitchButton26.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton27.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton27.setText("3");
        SwitchButton27.setFocusable(false);
        SwitchButton27.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton29.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton29.setText("3");
        SwitchButton29.setFocusable(false);
        SwitchButton29.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton28.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton28.setText("3");
        SwitchButton28.setFocusable(false);
        SwitchButton28.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton30.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton30.setText("3");
        SwitchButton30.setFocusable(false);
        SwitchButton30.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton31.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton31.setText("3");
        SwitchButton31.setFocusable(false);
        SwitchButton31.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton32.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton32.setText("3");
        SwitchButton32.setFocusable(false);
        SwitchButton32.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton33.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton33.setText("3");
        SwitchButton33.setFocusable(false);
        SwitchButton33.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton34.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton34.setText("3");
        SwitchButton34.setFocusable(false);
        SwitchButton34.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton35.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton35.setText("3");
        SwitchButton35.setFocusable(false);
        SwitchButton35.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton36.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton36.setText("3");
        SwitchButton36.setFocusable(false);
        SwitchButton36.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton37.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton37.setText("3");
        SwitchButton37.setFocusable(false);
        SwitchButton37.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton38.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton38.setText("3");
        SwitchButton38.setFocusable(false);
        SwitchButton38.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton39.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton39.setText("3");
        SwitchButton39.setFocusable(false);
        SwitchButton39.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton40.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton40.setText("3");
        SwitchButton40.setFocusable(false);
        SwitchButton40.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton41.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton41.setText("3");
        SwitchButton41.setFocusable(false);
        SwitchButton41.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton42.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton42.setText("3");
        SwitchButton42.setFocusable(false);
        SwitchButton42.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton43.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton43.setText("3");
        SwitchButton43.setFocusable(false);
        SwitchButton43.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton44.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton44.setText("3");
        SwitchButton44.setFocusable(false);
        SwitchButton44.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton45.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton45.setText("3");
        SwitchButton45.setFocusable(false);
        SwitchButton45.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton46.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton46.setText("3");
        SwitchButton46.setFocusable(false);
        SwitchButton46.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton47.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton47.setText("3");
        SwitchButton47.setFocusable(false);
        SwitchButton47.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton48.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton48.setText("3");
        SwitchButton48.setFocusable(false);
        SwitchButton48.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton49.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton49.setText("3");
        SwitchButton49.setFocusable(false);
        SwitchButton49.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton50.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton50.setText("3");
        SwitchButton50.setFocusable(false);
        SwitchButton50.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton51.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton51.setText("3");
        SwitchButton51.setFocusable(false);
        SwitchButton51.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton52.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton52.setText("3");
        SwitchButton52.setFocusable(false);
        SwitchButton52.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton53.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton53.setText("3");
        SwitchButton53.setFocusable(false);
        SwitchButton53.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton54.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton54.setText("3");
        SwitchButton54.setFocusable(false);
        SwitchButton54.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton55.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton55.setText("3");
        SwitchButton55.setFocusable(false);
        SwitchButton55.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton57.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton57.setText("3");
        SwitchButton57.setFocusable(false);
        SwitchButton57.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton58.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton58.setText("3");
        SwitchButton58.setFocusable(false);
        SwitchButton58.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton59.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton59.setText("3");
        SwitchButton59.setFocusable(false);
        SwitchButton59.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton60.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton60.setText("3");
        SwitchButton60.setFocusable(false);
        SwitchButton60.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton61.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton61.setText("3");
        SwitchButton61.setFocusable(false);
        SwitchButton61.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton62.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton62.setText("3");
        SwitchButton62.setFocusable(false);
        SwitchButton62.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton63.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton63.setText("3");
        SwitchButton63.setFocusable(false);
        SwitchButton63.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton20.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton20.setText("3");
        SwitchButton20.setFocusable(false);
        SwitchButton20.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton56.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton56.setText("3");
        SwitchButton56.setFocusable(false);
        SwitchButton56.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        SwitchButton25.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SwitchButton25.setText("3");
        SwitchButton25.setFocusable(false);
        SwitchButton25.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpdateSwitchState(evt);
            }
        });

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(SwitchButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton6, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton7, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton8, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton9, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addComponent(SwitchButton28, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton29, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(jPanel9Layout.createSequentialGroup()
                                    .addComponent(SwitchButton10, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(18, 18, 18)
                                    .addComponent(SwitchButton11, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(jPanel9Layout.createSequentialGroup()
                                    .addComponent(SwitchButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(18, 18, 18)
                                    .addComponent(SwitchButton20, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addComponent(SwitchButton12, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton13, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton14, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton15, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton16, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton17, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel9Layout.createSequentialGroup()
                                        .addComponent(SwitchButton30, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(SwitchButton31, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(SwitchButton32, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel9Layout.createSequentialGroup()
                                        .addComponent(SwitchButton21, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(SwitchButton22, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(SwitchButton23, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addGap(18, 18, 18)
                                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addGroup(jPanel9Layout.createSequentialGroup()
                                        .addComponent(SwitchButton24, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(SwitchButton25, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(SwitchButton26, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel9Layout.createSequentialGroup()
                                        .addComponent(SwitchButton33, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(SwitchButton34, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(SwitchButton35, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                        .addGap(18, 18, Short.MAX_VALUE)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(SwitchButton18, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(SwitchButton27, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(SwitchButton36, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(SwitchButton37, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton38, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton39, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton40, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton41, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton42, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton43, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton44, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(SwitchButton45, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(SwitchButton46, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(SwitchButton55, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addComponent(SwitchButton47, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton48, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton49, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton50, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton51, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton52, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton53, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton54, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addComponent(SwitchButton56, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton57, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton58, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton59, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton60, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton61, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton62, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(SwitchButton63, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SwitchButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton6, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton7, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton8, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton9, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SwitchButton10, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton11, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton12, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton13, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton14, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton15, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton16, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton17, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton18, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SwitchButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton21, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton22, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton23, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton24, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton26, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton27, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton20, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton25, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SwitchButton29, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton28, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton30, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton31, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton32, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton33, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton34, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton35, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton36, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SwitchButton37, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton38, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton39, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton40, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton41, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton42, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton43, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton44, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton45, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SwitchButton46, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton47, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton48, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton49, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton50, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton51, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton52, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton53, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton54, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SwitchButton55, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton57, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton58, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton59, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton60, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton61, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton62, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton63, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(SwitchButton56, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel10.setBackground(new java.awt.Color(245, 245, 245));
        jPanel10.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel10.setFocusable(false);

        PrevKeyboard.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        PrevKeyboard.setText("<<< -");
        PrevKeyboard.setFocusable(false);
        PrevKeyboard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PrevKeyboardActionPerformed(evt);
            }
        });

        KeyboardNumberLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        KeyboardNumberLabel.setText("Keyboard ");
        KeyboardNumberLabel.setFocusable(false);

        NextKeyboard.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        NextKeyboard.setText("+ >>>");
        NextKeyboard.setFocusable(false);
        NextKeyboard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NextKeyboardActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(KeyboardNumberLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 207, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(PrevKeyboard)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(NextKeyboard)
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(PrevKeyboard)
                    .addComponent(KeyboardNumberLabel)
                    .addComponent(NextKeyboard))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        KeyboardLabel1.setForeground(new java.awt.Color(0, 0, 115));
        KeyboardLabel1.setText("Change Keyboard");
        KeyboardLabel1.setFocusable(false);

        javax.swing.GroupLayout KeyboardPanelLayout = new javax.swing.GroupLayout(KeyboardPanel);
        KeyboardPanel.setLayout(KeyboardPanelLayout);
        KeyboardPanelLayout.setHorizontalGroup(
            KeyboardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(KeyboardPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(KeyboardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(KeyboardLabel)
                    .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(KeyboardLabel1)
                    .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        KeyboardPanelLayout.setVerticalGroup(
            KeyboardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(KeyboardPanelLayout.createSequentialGroup()
                .addGap(9, 9, 9)
                .addComponent(KeyboardLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(KeyboardLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        KeyboardTab.addTab("Keyboard", KeyboardPanel);

        ManageLocPanel.setBackground(new java.awt.Color(238, 238, 238));
        ManageLocPanel.setToolTipText(null);
        ManageLocPanel.setFocusable(false);
        ManageLocPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                ManageLocPanelMouseClicked(evt);
            }
        });

        jPanel2.setBackground(new java.awt.Color(245, 245, 245));
        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        jPanel2.setFocusable(false);

        buttonGroup1.add(LocTypeMM2);
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

        LocAddressInput.setInheritsPopupMenu(true);

        jLabel3.setText("Locomotive Address");
        jLabel3.setFocusable(false);

        jLabel1.setText("Locomotive Name");
        jLabel1.setFocusable(false);

        LocNameInput.setInheritsPopupMenu(true);

        buttonGroup1.add(LocTypeMFX);
        LocTypeMFX.setText("MFX");
        LocTypeMFX.setFocusable(false);
        LocTypeMFX.setInheritsPopupMenu(true);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(AddLocButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(jLabel1))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(LocNameInput)
                                .addComponent(LocAddressInput, javax.swing.GroupLayout.PREFERRED_SIZE, 116, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(LocTypeMM2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(LocTypeMFX)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                    .addComponent(LocAddressInput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(LocTypeMM2)
                    .addComponent(LocTypeMFX))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(AddLocButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        AddNewLocLabel.setForeground(new java.awt.Color(0, 0, 115));
        AddNewLocLabel.setText("Add New Locomotive");

        jPanel5.setBackground(new java.awt.Color(245, 245, 245));
        jPanel5.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        DeleteLocButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        DeleteLocButton.setText("Delete Selected Loc");
        DeleteLocButton.setFocusable(false);
        DeleteLocButton.setInheritsPopupMenu(true);
        DeleteLocButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeleteLocButtonActionPerformed(evt);
            }
        });

        RenameLocButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        RenameLocButton.setText("Rename");
        RenameLocButton.setFocusable(false);
        RenameLocButton.setInheritsPopupMenu(true);
        RenameLocButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RenameLocButtonActionPerformed(evt);
            }
        });

        SyncLocomotive.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SyncLocomotive.setText("Sync");
        SyncLocomotive.setFocusable(false);
        SyncLocomotive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SyncLocomotiveActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(DeleteLocButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(RenameLocButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(SyncLocomotive, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(RenameLocButton)
                    .addComponent(SyncLocomotive))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(DeleteLocButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        EditExistingLocLabel.setForeground(new java.awt.Color(0, 0, 115));
        EditExistingLocLabel.setText("Edit Existing Locomotive");

        EditExistingLocLabel1.setForeground(new java.awt.Color(0, 0, 115));
        EditExistingLocLabel1.setText("Tools");

        jPanel8.setBackground(new java.awt.Color(245, 245, 245));
        jPanel8.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        clearButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        clearButton.setText("Reset Key Mappings");
        clearButton.setFocusable(false);
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        SyncButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        SyncButton.setText("Sync With CS2");
        SyncButton.setFocusable(false);
        SyncButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SyncButtonActionPerformed(evt);
            }
        });

        TurnOffFnButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        TurnOffFnButton.setText("Turn Off All Functions");
        TurnOffFnButton.setFocusable(false);
        TurnOffFnButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TurnOffFnButtonActionPerformed(evt);
            }
        });

        TurnOnLightsButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        TurnOnLightsButton.setText("Turn On Lights");
        TurnOnLightsButton.setFocusable(false);
        TurnOnLightsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TurnOnLightsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(clearButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(SyncButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(TurnOffFnButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(TurnOnLightsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(clearButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(SyncButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TurnOnLightsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TurnOffFnButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        EditExistingLocLabel2.setForeground(new java.awt.Color(0, 0, 115));
        EditExistingLocLabel2.setText("Filter Locomotive List");

        jPanel11.setBackground(new java.awt.Color(245, 245, 245));
        jPanel11.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        FilterLocomotive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FilterLocomotiveActionPerformed(evt);
            }
        });
        FilterLocomotive.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                FilterLocomotiveKeyReleased(evt);
            }
            public void keyTyped(java.awt.event.KeyEvent evt) {
                FilterLocomotiveKeyTyped(evt);
            }
        });

        jLabel9.setText("Locomotive Name");
        jLabel9.setFocusable(false);

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addGap(7, 7, 7)
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(FilterLocomotive, javax.swing.GroupLayout.PREFERRED_SIZE, 118, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(FilterLocomotive, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout ManageLocPanelLayout = new javax.swing.GroupLayout(ManageLocPanel);
        ManageLocPanel.setLayout(ManageLocPanelLayout);
        ManageLocPanelLayout.setHorizontalGroup(
            ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ManageLocPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ManageLocPanelLayout.createSequentialGroup()
                        .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(EditExistingLocLabel2)
                            .addComponent(EditExistingLocLabel)
                            .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(jPanel8, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel5, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel11, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGap(393, 491, Short.MAX_VALUE))
                    .addGroup(ManageLocPanelLayout.createSequentialGroup()
                        .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(AddNewLocLabel)
                            .addComponent(EditExistingLocLabel1))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        ManageLocPanelLayout.setVerticalGroup(
            ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ManageLocPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(AddNewLocLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(EditExistingLocLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(EditExistingLocLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(EditExistingLocLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16))
        );

        KeyboardTab.addTab("Manage Locomotives", ManageLocPanel);

        logPanel.setBackground(new java.awt.Color(238, 238, 238));

        debugArea.setColumns(20);
        debugArea.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        debugArea.setRows(5);
        debugArea.setFocusable(false);
        jScrollPane3.setViewportView(debugArea);

        javax.swing.GroupLayout logPanelLayout = new javax.swing.GroupLayout(logPanel);
        logPanel.setLayout(logPanelLayout);
        logPanelLayout.setHorizontalGroup(
            logPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(logPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 733, Short.MAX_VALUE)
                .addContainerGap())
        );
        logPanelLayout.setVerticalGroup(
            logPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(logPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 481, Short.MAX_VALUE)
                .addContainerGap())
        );

        KeyboardTab.addTab("Log", logPanel);

        LocFunctionsPanel.setBackground(new java.awt.Color(255, 255, 255));
        LocFunctionsPanel.setToolTipText(null);
        LocFunctionsPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                LocControlPanelKeyPressed(evt);
            }
        });

        OnButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        OnButton.setText("ON");
        OnButton.setFocusable(false);
        OnButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OnButtonActionPerformed(evt);
            }
        });

        PowerOff.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        PowerOff.setText("Power OFF");
        PowerOff.setFocusable(false);
        PowerOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PowerOffActionPerformed(evt);
            }
        });

        ActiveLocLabel.setFont(new java.awt.Font("Tahoma", 0, 19)); // NOI18N
        ActiveLocLabel.setText("Locomotive Name");
        ActiveLocLabel.setFocusable(false);

        jScrollPane2.setFocusable(false);

        LocomotiveList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        LocomotiveList.setFocusable(false);
        LocomotiveList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                LocomotiveListMouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(LocomotiveList);

        SpeedSlider.setMajorTickSpacing(10);
        SpeedSlider.setMinorTickSpacing(5);
        SpeedSlider.setPaintLabels(true);
        SpeedSlider.setPaintTicks(true);
        SpeedSlider.setToolTipText(null);
        SpeedSlider.setValue(0);
        SpeedSlider.setFocusable(false);
        SpeedSlider.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                SpeedSliderDragged(evt);
            }
        });
        SpeedSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                SpeedSliderDragged(evt);
            }
        });

        Backward.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        Backward.setText("<<<<<");
        Backward.setFocusable(false);
        Backward.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BackwardActionPerformed(evt);
            }
        });

        Forward.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        Forward.setText(">>>>>");
        Forward.setFocusable(false);
        Forward.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ForwardActionPerformed(evt);
            }
        });

        StopLoc.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        StopLoc.setText("Stop");
        StopLoc.setFocusable(false);
        StopLoc.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                StopLocActionPerformed(evt);
            }
        });

        CurrentKeyLabel.setBackground(new java.awt.Color(255, 255, 255));
        CurrentKeyLabel.setForeground(new java.awt.Color(0, 0, 115));
        CurrentKeyLabel.setText("Key Name");
        CurrentKeyLabel.setToolTipText(null);
        CurrentKeyLabel.setFocusable(false);

        changeLocomotiveLabel.setBackground(new java.awt.Color(255, 255, 255));
        changeLocomotiveLabel.setForeground(new java.awt.Color(0, 0, 115));
        changeLocomotiveLabel.setText("Change Locomotive");
        changeLocomotiveLabel.setToolTipText(null);
        changeLocomotiveLabel.setFocusable(false);

        FunctionTabs.setBackground(new java.awt.Color(255, 255, 255));
        FunctionTabs.setTabPlacement(javax.swing.JTabbedPane.BOTTOM);
        FunctionTabs.setFocusable(false);

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));

        F8.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F8.setText("F8");
        F8.setFocusable(false);
        F8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F9.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F9.setText("F9");
        F9.setFocusable(false);
        F9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F7.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F7.setText("F7");
        F7.setFocusable(false);
        F7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F12.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F12.setText("F12");
        F12.setFocusable(false);
        F12.setMaximumSize(new java.awt.Dimension(45, 23));
        F12.setMinimumSize(new java.awt.Dimension(45, 23));
        F12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F13.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F13.setText("F13");
        F13.setFocusable(false);
        F13.setMaximumSize(new java.awt.Dimension(45, 23));
        F13.setMinimumSize(new java.awt.Dimension(45, 23));
        F13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F10.setText("F10");
        F10.setFocusable(false);
        F10.setMaximumSize(new java.awt.Dimension(45, 23));
        F10.setMinimumSize(new java.awt.Dimension(45, 23));
        F10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F11.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F11.setText("F11");
        F11.setFocusable(false);
        F11.setMaximumSize(new java.awt.Dimension(45, 23));
        F11.setMinimumSize(new java.awt.Dimension(45, 23));
        F11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F14.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F14.setText("F14");
        F14.setFocusable(false);
        F14.setMaximumSize(new java.awt.Dimension(45, 23));
        F14.setMinimumSize(new java.awt.Dimension(45, 23));
        F14.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F15.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F15.setText("F15");
        F15.setFocusable(false);
        F15.setMaximumSize(new java.awt.Dimension(45, 23));
        F15.setMinimumSize(new java.awt.Dimension(45, 23));
        F15.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F0.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F0.setText("F0");
        F0.setFocusable(false);
        F0.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F3.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F3.setText("F3");
        F3.setFocusable(false);
        F3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F1.setText("F1");
        F1.setFocusable(false);
        F1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F2.setText("F2");
        F2.setFocusable(false);
        F2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F4.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F4.setText("F4");
        F4.setFocusable(false);
        F4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F5.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F5.setText("F5");
        F5.setFocusable(false);
        F5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F6.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F6.setText("F6");
        F6.setFocusable(false);
        F6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(F8, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F4, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F0, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F12, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(F9, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F13, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F5, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F1, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(F14, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F15, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(F2, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F3, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(F6, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F7, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(F10, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F11, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {F0, F1, F10, F11, F12, F13, F14, F15, F2, F3, F4, F5, F6, F7, F8, F9});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F1)
                    .addComponent(F0)
                    .addComponent(F2)
                    .addComponent(F3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F4)
                    .addComponent(F5)
                    .addComponent(F6)
                    .addComponent(F7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F8)
                    .addComponent(F9)
                    .addComponent(F10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F15, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        FunctionTabs.addTab("F0-15", jPanel1);

        jPanel3.setBackground(new java.awt.Color(255, 255, 255));

        F24.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F24.setText("F24");
        F24.setFocusable(false);
        F24.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F16.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F16.setText("F16");
        F16.setFocusable(false);
        F16.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F25.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F25.setText("F25");
        F25.setFocusable(false);
        F25.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F19.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F19.setText("F19");
        F19.setFocusable(false);
        F19.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F17.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F17.setText("F17");
        F17.setFocusable(false);
        F17.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F23.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F23.setText("F23");
        F23.setFocusable(false);
        F23.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F28.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F28.setText("F28");
        F28.setFocusable(false);
        F28.setMaximumSize(new java.awt.Dimension(45, 23));
        F28.setMinimumSize(new java.awt.Dimension(45, 23));
        F28.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F29.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F29.setText("F29");
        F29.setFocusable(false);
        F29.setMaximumSize(new java.awt.Dimension(45, 23));
        F29.setMinimumSize(new java.awt.Dimension(45, 23));
        F29.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F26.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F26.setText("F26");
        F26.setFocusable(false);
        F26.setMaximumSize(new java.awt.Dimension(45, 23));
        F26.setMinimumSize(new java.awt.Dimension(45, 23));
        F26.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F27.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F27.setText("F27");
        F27.setFocusable(false);
        F27.setMaximumSize(new java.awt.Dimension(45, 23));
        F27.setMinimumSize(new java.awt.Dimension(45, 23));
        F27.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F30.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F30.setText("F30");
        F30.setFocusable(false);
        F30.setMaximumSize(new java.awt.Dimension(45, 23));
        F30.setMinimumSize(new java.awt.Dimension(45, 23));
        F30.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F31.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F31.setText("F31");
        F31.setFocusable(false);
        F31.setMaximumSize(new java.awt.Dimension(45, 23));
        F31.setMinimumSize(new java.awt.Dimension(45, 23));
        F31.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F18.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F18.setText("F18");
        F18.setFocusable(false);
        F18.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F20.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F20.setText("F20");
        F20.setFocusable(false);
        F20.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F21.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F21.setText("F21");
        F21.setFocusable(false);
        F21.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F22.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F22.setText("F22");
        F22.setFocusable(false);
        F22.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(F24, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F20, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F16, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F28, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(F25, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F29, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F21, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F17, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(F30, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F31, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(F18, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F19, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(F22, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F23, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(F26, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(F27, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F17)
                    .addComponent(F16)
                    .addComponent(F18)
                    .addComponent(F19))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F20)
                    .addComponent(F21)
                    .addComponent(F22)
                    .addComponent(F23))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F24)
                    .addComponent(F25)
                    .addComponent(F26, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F27, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F28, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F29, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F30, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F31, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        FunctionTabs.addTab("F16-31", jPanel3);

        javax.swing.GroupLayout LocFunctionsPanelLayout = new javax.swing.GroupLayout(LocFunctionsPanel);
        LocFunctionsPanel.setLayout(LocFunctionsPanelLayout);
        LocFunctionsPanelLayout.setHorizontalGroup(
            LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ActiveLocLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(CurrentKeyLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(locIcon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addComponent(PowerOff, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(OnButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, LocFunctionsPanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                                .addComponent(Backward, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(Forward, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(SpeedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 276, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 276, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(changeLocomotiveLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(FunctionTabs)
                            .addComponent(StopLoc, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        LocFunctionsPanelLayout.setVerticalGroup(
            LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(PowerOff)
                    .addComponent(OnButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addComponent(CurrentKeyLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ActiveLocLabel))
                    .addComponent(locIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(Backward)
                    .addComponent(Forward))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(SpeedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(FunctionTabs, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(StopLoc)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(changeLocomotiveLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        PowerOff.getAccessibleContext().setAccessibleName("");
        ActiveLocLabel.getAccessibleContext().setAccessibleName("LocomotiveNameBig");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(KeyboardTab, javax.swing.GroupLayout.PREFERRED_SIZE, 758, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(LocFunctionsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(KeyboardTab, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(LocFunctionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        KeyboardTab.getAccessibleContext().setAccessibleName("");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void LocomotiveListMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_LocomotiveListMouseClicked
    {//GEN-HEADEREND:event_LocomotiveListMouseClicked
        if (LocomotiveList.getSelectedValue() != null)
        {
            this.mapLocToCurrentButton(LocomotiveList.getSelectedValue().toString());
            this.LocFunctionsPanel.requestFocus();
            this.FilterLocomotive.setText("");
            this.refreshLocList();
        }
    }//GEN-LAST:event_LocomotiveListMouseClicked

    private void PowerOffActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_PowerOffActionPerformed
    {//GEN-HEADEREND:event_PowerOffActionPerformed
        stop();
    }//GEN-LAST:event_PowerOffActionPerformed

    private void SpeedSliderDragged(java.awt.event.MouseEvent evt)//GEN-FIRST:event_SpeedSliderDragged
    {//GEN-HEADEREND:event_SpeedSliderDragged
        setLocSpeed(SpeedSlider.getValue());
    }//GEN-LAST:event_SpeedSliderDragged

    private void LocControlPanelKeyPressed(java.awt.event.KeyEvent evt)//GEN-FIRST:event_LocControlPanelKeyPressed
    {//GEN-HEADEREND:event_LocControlPanelKeyPressed
        int keyCode = evt.getKeyCode();
        boolean altPressed = (evt.getModifiers() & KeyEvent.ALT_MASK) != 0;

        if (this.buttonMapping.containsKey(keyCode))
        {
            this.displayCurrentButtonLoc(this.buttonMapping.get(evt.getKeyCode()));
        }
        else if (keyCode == KeyEvent.VK_UP)
        {
            this.UpArrowLetterButtonPressed(null);
        }
        else if (keyCode == KeyEvent.VK_DOWN)
        {
            this.DownArrowLetterButtonPressed(null);
        }
        else if (keyCode == KeyEvent.VK_RIGHT)
        {
            this.RightArrowLetterButtonPressed(null);
        }
        else if (keyCode == KeyEvent.VK_LEFT)
        {
            this.LeftArrowLetterButtonPressed(null);
        }
        else if (keyCode == KeyEvent.VK_SPACE)
        {
            this.SpacebarButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_0 && !altPressed)
        {
            this.ZeroButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_1 && !altPressed)
        {
            this.OneButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_2 && !altPressed)
        {
            this.TwoButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_3 && !altPressed)
        {
            this.ThreeButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_4 && !altPressed)
        {
            this.FourButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_5 && !altPressed)
        {
            this.FiveButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_6 && !altPressed)
        {
            this.SixButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_7 && !altPressed)
        {
            this.SevenButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_8 && !altPressed)
        {
            this.EightButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_9 && !altPressed)
        {
            this.NineButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_MINUS || keyCode == KeyEvent.VK_UNDERSCORE)
        {
            if (this.KeyboardTab.getSelectedIndex() == 1)
            {
                int currentIndex = this.LayoutList.getSelectedIndex();
                
                if (currentIndex > 0)
                {
                     this.LayoutList.setSelectedIndex(currentIndex - 1);
                     this.repaintLayout();
                }
            }
            else
            {
                this.PrevKeyboardActionPerformed(null);
            }
        }
        else if (keyCode == KeyEvent.VK_EQUALS || keyCode == KeyEvent.VK_PLUS)
        {
            if (this.KeyboardTab.getSelectedIndex() == 1)
            {
                int currentIndex = this.LayoutList.getSelectedIndex();
                
                if (currentIndex < this.LayoutList.getItemCount() - 1)
                {
                     this.LayoutList.setSelectedIndex(currentIndex + 1);
                     this.repaintLayout();
                }
            }
            else
            {
                this.NextKeyboardActionPerformed(null);
            } 
        }
        else if (keyCode == KeyEvent.VK_COMMA)
        {
            //if (this.KeyboardTab.getSelectedIndex() == 0)
            //{
            this.PrevLocMappingActionPerformed(null);
            //}    
        }
        else if (keyCode == KeyEvent.VK_PERIOD)
        {
            //if (this.KeyboardTab.getSelectedIndex() == 0)
            // {
            this.NextLocMappingActionPerformed(null);
            // }
        }
        else if (keyCode == KeyEvent.VK_NUMPAD0 || keyCode == KeyEvent.VK_BACK_QUOTE || (keyCode == KeyEvent.VK_0 && altPressed))
        {
            this.switchF(0);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD1 || keyCode == KeyEvent.VK_F1 || (keyCode == KeyEvent.VK_1 && altPressed))
        {
            this.switchF(1);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD2 || keyCode == KeyEvent.VK_F2 || (keyCode == KeyEvent.VK_2 && altPressed))
        {
            this.switchF(2);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD3 || keyCode == KeyEvent.VK_F3 || (keyCode == KeyEvent.VK_3 && altPressed))
        {
            this.switchF(3);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD4 || keyCode == KeyEvent.VK_F4 || (keyCode == KeyEvent.VK_4 && altPressed))
        {
            this.switchF(4);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD5 || keyCode == KeyEvent.VK_F5 || (keyCode == KeyEvent.VK_5 && altPressed))
        {
            this.switchF(5);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD6 || keyCode == KeyEvent.VK_F6 || (keyCode == KeyEvent.VK_6 && altPressed))
        {
            this.switchF(6);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD7 || keyCode == KeyEvent.VK_F7 || (keyCode == KeyEvent.VK_7 && altPressed))
        {
            this.switchF(7);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD8 || keyCode == KeyEvent.VK_F8 || (keyCode == KeyEvent.VK_8 && altPressed))
        {
            this.switchF(8);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD9 || keyCode == KeyEvent.VK_F9 || (keyCode == KeyEvent.VK_9 && altPressed))
        {
            this.switchF(9);
        }
        else if (keyCode == KeyEvent.VK_F10)
        {
            this.switchF(10);
        }
        else if (keyCode == KeyEvent.VK_F11)
        {
            this.switchF(11);
        }
        else if (keyCode == KeyEvent.VK_F12)
        {
            this.switchF(12);
        }
        else if (keyCode == KeyEvent.VK_F13)
        {
            this.switchF(13);
        }
        else if (keyCode == KeyEvent.VK_F14)
        {
            this.switchF(14);
        }
        else if (keyCode == KeyEvent.VK_F15)
        {
            this.switchF(15);
        }
        else if (keyCode == KeyEvent.VK_F16)
        {
            this.switchF(16);
        }
        else if (keyCode == KeyEvent.VK_ESCAPE)
        {
            stop();
        }
        else if (keyCode == KeyEvent.VK_F2)
        {
            go();
        }
        else if (keyCode == KeyEvent.VK_SHIFT)
        {
            ShiftButtonActionPerformed(null);
        }  
        else if (keyCode == KeyEvent.VK_ENTER)
        {
            AltEmergencyStopActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_BACK_SPACE)
        {
            // Easy tab cycling
            this.KeyboardTab.setSelectedIndex(
                (this.KeyboardTab.getSelectedIndex() + 1) 
                    % this.KeyboardTab.getComponentCount()
            );
        } 
        else if (keyCode == KeyEvent.VK_CONTROL)
        {
            if (this.KeyboardTab.getSelectedIndex() == 0)
            {
                this.KeyboardTab.setSelectedIndex(this.KeyboardTab.getComponentCount() - 1);
            }
            else
            {
                // Easy tab cycling
                this.KeyboardTab.setSelectedIndex(
                    this.KeyboardTab.getSelectedIndex() - 1
                        % this.KeyboardTab.getComponentCount()
                );
            }
        } 
    }//GEN-LAST:event_LocControlPanelKeyPressed

    private void ProcessFunction(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ProcessFunction
    {//GEN-HEADEREND:event_ProcessFunction
        javax.swing.JToggleButton b = 
            (javax.swing.JToggleButton) evt.getSource();
        
        Integer fNumber = this.functionMapping.get(b);
        Boolean state = b.isSelected();
        
        this.fireF(fNumber, state);
    }//GEN-LAST:event_ProcessFunction

    private void BackwardActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BackwardActionPerformed
    {//GEN-HEADEREND:event_BackwardActionPerformed
        backwardLoc();
    }//GEN-LAST:event_BackwardActionPerformed

    private void ForwardActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ForwardActionPerformed
    {//GEN-HEADEREND:event_ForwardActionPerformed
        forwardLoc();
    }//GEN-LAST:event_ForwardActionPerformed

    private void OnButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_OnButtonActionPerformed
    {//GEN-HEADEREND:event_OnButtonActionPerformed
       go();
    }//GEN-LAST:event_OnButtonActionPerformed

    private void WindowClosed(java.awt.event.WindowEvent evt)//GEN-FIRST:event_WindowClosed
    {//GEN-HEADEREND:event_WindowClosed
        model.saveState();
        this.saveState();
        //model.stop();
        this.dispose();
        System.exit(0);
    }//GEN-LAST:event_WindowClosed

    private void StopLocActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_StopLocActionPerformed
    {//GEN-HEADEREND:event_StopLocActionPerformed
        stopLoc();
    }//GEN-LAST:event_StopLocActionPerformed

    private void TurnOffFnButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_TurnOffFnButtonActionPerformed
    {//GEN-HEADEREND:event_TurnOffFnButtonActionPerformed
        this.model.allFunctionsOff();
    }//GEN-LAST:event_TurnOffFnButtonActionPerformed

    private void SyncButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_SyncButtonActionPerformed
    {//GEN-HEADEREND:event_SyncButtonActionPerformed
        Integer r = this.model.syncWithCS2();
        refreshLocList();
        refreshRouteList();

        JOptionPane.showMessageDialog(ManageLocPanel, "Sync complete.  Items added: " + r.toString());
    }//GEN-LAST:event_SyncButtonActionPerformed

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_clearButtonActionPerformed
    {//GEN-HEADEREND:event_clearButtonActionPerformed
        int dialogResult = JOptionPane.showConfirmDialog(LocControlPanel, "Are you sure you want to clear all mappings?", "Reset Keyboard", JOptionPane.YES_NO_OPTION);
        if(dialogResult == JOptionPane.YES_OPTION)
        {
            this.activeLoc = null;
            
            for (JButton key : this.currentLocMapping().keySet())
            {
                this.currentLocMapping().put(key, null);
            }
            repaintMappings();
            repaintLoc();
        }
    }//GEN-LAST:event_clearButtonActionPerformed

    private void RenameLocButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_RenameLocButtonActionPerformed
    {//GEN-HEADEREND:event_RenameLocButtonActionPerformed
        Object value = this.LocomotiveList.getSelectedValue();

        if (value == null)
        {
            JOptionPane.showMessageDialog(this,
                "Error: no locomotive selected");

            return;
        }

        Locomotive l = this.model.getLocByName(value.toString());

        if (l != null)
        {
            String newName = JOptionPane.showInputDialog(this, "Enter new name:");

            if (newName != null)
            {
                if (newName.trim().length() == 0)
                {
                    JOptionPane.showMessageDialog(this,
                        "Please enter a locomotive name");
                    return;
                }

                if (newName.length() >= 30)
                {
                    JOptionPane.showMessageDialog(this,
                        "Please enter a locomotive name under 30 characters");
                    return;
                }

                this.model.renameLoc(l.getName(), newName);

                refreshLocList();
                repaintLoc();
                repaintMappings();
            }
        }
    }//GEN-LAST:event_RenameLocButtonActionPerformed

    private void DeleteLocButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_DeleteLocButtonActionPerformed
    {//GEN-HEADEREND:event_DeleteLocButtonActionPerformed
        Object value = this.LocomotiveList.getSelectedValue();

        if (value == null)
        {
            JOptionPane.showMessageDialog(this,
                "Error: no locomotive selected");
        }
        else
        {
            Locomotive l = this.model.getLocByName(value.toString());

            if (l != null)
            {
                // Also d elete locomotive from active loc list
                if (l.equals(this.activeLoc))
                {
                    this.activeLoc = null;
                }

                List<JButton> keys = new LinkedList(this.currentLocMapping().keySet());
                for (JButton key : keys)
                {
                    if (this.currentLocMapping().get(key) == l)
                    {
                        this.currentLocMapping().put(key, null);
                    }
                }

                this.model.deleteLoc(value.toString());
                refreshLocList();
                repaintLoc();
                repaintMappings();
            }
        }
    }//GEN-LAST:event_DeleteLocButtonActionPerformed

    private void AddLocButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_AddLocButtonActionPerformed
    {//GEN-HEADEREND:event_AddLocButtonActionPerformed
        // TODO - make this generic

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

        if (locName.length() >= 30)
        {
            JOptionPane.showMessageDialog(this,
                "Please enter a locomotive name under 30 characters");
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
        else
        {
            type = marklin.MarklinLocomotive.decoderType.MM2;
        }

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
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(this,
                "Please enter a numerical address");
            return;
        }

        if (type == marklin.MarklinLocomotive.decoderType.MM2)
        {
            if (locAddress > marklin.MarklinLocomotive.MM2_MAX_ADDR)
            {
                JOptionPane.showMessageDialog(this,
                    "MM2 address out of range");
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
        else
        {
            this.model.newMM2Locomotive(locName, locAddress);
        }

        // Add list of locomotives to dropdown
        refreshLocList();

        // Rest form
        JOptionPane.showMessageDialog(this,
            "Locomotive added successfully");

        this.LocAddressInput.setText("");
        this.LocNameInput.setText("");
    }//GEN-LAST:event_AddLocButtonActionPerformed

    private void DeleteRouteButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_DeleteRouteButtonActionPerformed
    {//GEN-HEADEREND:event_DeleteRouteButtonActionPerformed
        String route = this.RouteList.getValueAt(this.RouteList.getSelectedRow(), this.RouteList.getSelectedColumn()).toString();
        
        if (route != null)
        {
            int dialogResult = JOptionPane.showConfirmDialog(RoutePanel, "Delete route " + route + "?", "Route Deletion", JOptionPane.YES_NO_OPTION);
            if(dialogResult == JOptionPane.YES_OPTION)
            {
                this.model.deleteRoute(route);
                refreshRouteList();
            }
        }
    }//GEN-LAST:event_DeleteRouteButtonActionPerformed

    private void NextKeyboardActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_NextKeyboardActionPerformed
    {//GEN-HEADEREND:event_NextKeyboardActionPerformed
        this.switchKeyboard(this.keyboardNumber + 1);
    }//GEN-LAST:event_NextKeyboardActionPerformed

    private void PrevKeyboardActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_PrevKeyboardActionPerformed
    {//GEN-HEADEREND:event_PrevKeyboardActionPerformed
        this.switchKeyboard(this.keyboardNumber - 1);
    }//GEN-LAST:event_PrevKeyboardActionPerformed

    private void UpdateSwitchState(java.awt.event.ActionEvent evt)//GEN-FIRST:event_UpdateSwitchState
    {//GEN-HEADEREND:event_UpdateSwitchState
        javax.swing.JToggleButton b = (javax.swing.JToggleButton) evt.getSource();
        Integer switchId = Integer.parseInt(b.getText());

        new Thread(() -> {
            this.model.setAccessoryState(switchId, b.isSelected());
        }).start();
    }//GEN-LAST:event_UpdateSwitchState

    private void FiveButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_FiveButtonActionPerformed
    {//GEN-HEADEREND:event_FiveButtonActionPerformed
        setLocSpeed(44);
    }//GEN-LAST:event_FiveButtonActionPerformed

    private void SixButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_SixButtonActionPerformed
    {//GEN-HEADEREND:event_SixButtonActionPerformed
        setLocSpeed(55);
    }//GEN-LAST:event_SixButtonActionPerformed

    private void OneButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_OneButtonActionPerformed
    {//GEN-HEADEREND:event_OneButtonActionPerformed
        setLocSpeed(0);
    }//GEN-LAST:event_OneButtonActionPerformed

    private void TwoButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_TwoButtonActionPerformed
    {//GEN-HEADEREND:event_TwoButtonActionPerformed
        setLocSpeed(11);
    }//GEN-LAST:event_TwoButtonActionPerformed

    private void ThreeButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ThreeButtonActionPerformed
    {//GEN-HEADEREND:event_ThreeButtonActionPerformed
        setLocSpeed(22);
    }//GEN-LAST:event_ThreeButtonActionPerformed

    private void FourButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_FourButtonActionPerformed
    {//GEN-HEADEREND:event_FourButtonActionPerformed
        setLocSpeed(33);
    }//GEN-LAST:event_FourButtonActionPerformed

    private void SevenButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_SevenButtonActionPerformed
    {//GEN-HEADEREND:event_SevenButtonActionPerformed
        setLocSpeed(66);
    }//GEN-LAST:event_SevenButtonActionPerformed

    private void NineButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_NineButtonActionPerformed
    {//GEN-HEADEREND:event_NineButtonActionPerformed
        setLocSpeed(88);
    }//GEN-LAST:event_NineButtonActionPerformed

    private void EightButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_EightButtonActionPerformed
    {//GEN-HEADEREND:event_EightButtonActionPerformed
        setLocSpeed(77);
    }//GEN-LAST:event_EightButtonActionPerformed

    private void ZeroButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ZeroButtonActionPerformed
    {//GEN-HEADEREND:event_ZeroButtonActionPerformed
        setLocSpeed(100);
    }//GEN-LAST:event_ZeroButtonActionPerformed

    private void AltEmergencyStopActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_AltEmergencyStopActionPerformed
    {//GEN-HEADEREND:event_AltEmergencyStopActionPerformed
        this.model.stopAllLocs();
    }//GEN-LAST:event_AltEmergencyStopActionPerformed

    private void ShiftButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ShiftButtonActionPerformed
    {//GEN-HEADEREND:event_ShiftButtonActionPerformed
        setLocSpeed(0);
    }//GEN-LAST:event_ShiftButtonActionPerformed

    private void SpacebarButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_SpacebarButtonActionPerformed
    {//GEN-HEADEREND:event_SpacebarButtonActionPerformed
        stopLoc();
    }//GEN-LAST:event_SpacebarButtonActionPerformed

    private void LeftArrowLetterButtonPressed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_LeftArrowLetterButtonPressed
    {//GEN-HEADEREND:event_LeftArrowLetterButtonPressed
        switchDirection();
    }//GEN-LAST:event_LeftArrowLetterButtonPressed

    private void RightArrowLetterButtonPressed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_RightArrowLetterButtonPressed
    {//GEN-HEADEREND:event_RightArrowLetterButtonPressed
        switchDirection();
    }//GEN-LAST:event_RightArrowLetterButtonPressed

    private void DownArrowLetterButtonPressed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_DownArrowLetterButtonPressed
    {//GEN-HEADEREND:event_DownArrowLetterButtonPressed
        if (this.activeLoc != null)
        {
            setLocSpeed(Math.max(this.activeLoc.getSpeed() - 5,0));
        }
    }//GEN-LAST:event_DownArrowLetterButtonPressed

    private void UpArrowLetterButtonPressed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_UpArrowLetterButtonPressed
    {//GEN-HEADEREND:event_UpArrowLetterButtonPressed
        if (this.activeLoc != null)
        {
            setLocSpeed(Math.min(this.activeLoc.getSpeed() + 5,100));
        }
    }//GEN-LAST:event_UpArrowLetterButtonPressed

    private void LetterButtonPressed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_LetterButtonPressed
    {//GEN-HEADEREND:event_LetterButtonPressed
        this.displayCurrentButtonLoc((javax.swing.JButton) evt.getSource());
    }//GEN-LAST:event_LetterButtonPressed

    private void LayoutListMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_LayoutListMouseClicked
    {//GEN-HEADEREND:event_LayoutListMouseClicked
        
    }//GEN-LAST:event_LayoutListMouseClicked

    private void LayoutListActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_LayoutListActionPerformed
    {//GEN-HEADEREND:event_LayoutListActionPerformed
        repaintLayout();
    }//GEN-LAST:event_LayoutListActionPerformed

    private void SizeListMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_SizeListMouseClicked
    {//GEN-HEADEREND:event_SizeListMouseClicked
        // TODO add your handling code here:
    }//GEN-LAST:event_SizeListMouseClicked

    private void SizeListActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_SizeListActionPerformed
    {//GEN-HEADEREND:event_SizeListActionPerformed
        repaintLayout();
    }//GEN-LAST:event_SizeListActionPerformed

    private void RouteListMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_RouteListMouseClicked
    {//GEN-HEADEREND:event_RouteListMouseClicked
                String route = this.RouteList.getValueAt(this.RouteList.getSelectedRow(), this.RouteList.getSelectedColumn()).toString();
        
        if (route != null)
        {
            int dialogResult = JOptionPane.showConfirmDialog(RoutePanel, "Execute route " + route + "?", "Route Execution", JOptionPane.YES_NO_OPTION);
            if(dialogResult == JOptionPane.YES_OPTION)
            {
                this.model.execRoute(route);
                refreshRouteList();
            }
        }   
    }//GEN-LAST:event_RouteListMouseClicked

    private void TurnOnLightsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_TurnOnLightsButtonActionPerformed
    {//GEN-HEADEREND:event_TurnOnLightsButtonActionPerformed
        List<String> locs = new ArrayList<>();
        
        for (Map<JButton, Locomotive> m : this.locMapping)
        {
            for (Locomotive l : m.values())
            {
                locs.add(l.getName());
            }
        }
                
        this.model.lightsOn(locs);
    }//GEN-LAST:event_TurnOnLightsButtonActionPerformed

    private void PrevLocMappingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PrevLocMappingActionPerformed
        this.switchLocMapping(this.locMappingNumber - 1);
    }//GEN-LAST:event_PrevLocMappingActionPerformed

    private void NextLocMappingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_NextLocMappingActionPerformed
        this.switchLocMapping(this.locMappingNumber + 1);
    }//GEN-LAST:event_NextLocMappingActionPerformed

    private void FilterLocomotiveKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_FilterLocomotiveKeyTyped
        
    }//GEN-LAST:event_FilterLocomotiveKeyTyped

    private void FilterLocomotiveKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_FilterLocomotiveKeyReleased
        
        refreshLocList();
    }//GEN-LAST:event_FilterLocomotiveKeyReleased

    private void FilterLocomotiveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_FilterLocomotiveActionPerformed

    }//GEN-LAST:event_FilterLocomotiveActionPerformed

    private void ManageLocPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ManageLocPanelMouseClicked
        //this.LocFunctionsPanel.requestFocus();
    }//GEN-LAST:event_ManageLocPanelMouseClicked

    private void SyncLocomotiveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SyncLocomotiveActionPerformed
        
        Object value = this.LocomotiveList.getSelectedValue();

        if (value == null)
        {
            if (this.activeLoc != null)
            {
                this.model.syncLocomotive(this.activeLoc.getName());
            }
            else
            {
                JOptionPane.showMessageDialog(this,
                    "Error: no locomotive selected");
            }
        }
        else
        {
            this.model.syncLocomotive(value.toString());    
        }
        
        repaintLoc();
    }//GEN-LAST:event_SyncLocomotiveActionPerformed

    public void childWindowKeyEvent(java.awt.event.KeyEvent evt)
    {
        this.LocControlPanelKeyPressed(evt);
    }
    
    private void layoutNewWindowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_layoutNewWindowActionPerformed
        
        LayoutPopupUI popup = new LayoutPopupUI(
                this.model.getLayout(this.LayoutList.getSelectedItem().toString()), 
                Integer.parseInt(this.SizeList.getSelectedItem().toString()), 
                this
        );
        
        popup.render();
    }//GEN-LAST:event_layoutNewWindowActionPerformed

    private void refreshRouteList()
    {
        DefaultTableModel tableModel = new DefaultTableModel() {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
         };
        
        tableModel.setColumnCount(4);
        
        String[] items = new String[4];
        
        List<String> names = this.model.getRouteList();
        
        int i = 0;
        while (!names.isEmpty())
        {
            items[i++] = names.get(0);
            names.remove(0);
                    
            if (i == 4 || names.isEmpty())
            {
                i = 0;
                
                tableModel.addRow(items);
               
                items = new String[4];
            }
        }
        
        this.RouteList.setModel(tableModel);
    }
        
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AButton;
    private javax.swing.JLabel ALabel;
    private javax.swing.JLabel ActiveLocLabel;
    private javax.swing.JButton AddLocButton;
    private javax.swing.JLabel AddNewLocLabel;
    private javax.swing.JButton AltEmergencyStop;
    private javax.swing.JButton BButton;
    private javax.swing.JLabel BLabel;
    private javax.swing.JToggleButton Backward;
    private javax.swing.JButton CButton;
    private javax.swing.JLabel CLabel;
    private javax.swing.JLabel CurrentKeyLabel;
    private javax.swing.JButton DButton;
    private javax.swing.JLabel DLabel;
    private javax.swing.JButton DeleteLocButton;
    private javax.swing.JButton DeleteRouteButton;
    private javax.swing.JLabel DirectionLabel;
    private javax.swing.JButton DownArrow;
    private javax.swing.JButton EButton;
    private javax.swing.JLabel ELabel;
    private javax.swing.JLabel EStopLabel;
    private javax.swing.JLabel EditExistingLocLabel;
    private javax.swing.JLabel EditExistingLocLabel1;
    private javax.swing.JLabel EditExistingLocLabel2;
    private javax.swing.JButton EightButton;
    private javax.swing.JToggleButton F0;
    private javax.swing.JToggleButton F1;
    private javax.swing.JToggleButton F10;
    private javax.swing.JToggleButton F11;
    private javax.swing.JToggleButton F12;
    private javax.swing.JToggleButton F13;
    private javax.swing.JToggleButton F14;
    private javax.swing.JToggleButton F15;
    private javax.swing.JToggleButton F16;
    private javax.swing.JToggleButton F17;
    private javax.swing.JToggleButton F18;
    private javax.swing.JToggleButton F19;
    private javax.swing.JToggleButton F2;
    private javax.swing.JToggleButton F20;
    private javax.swing.JToggleButton F21;
    private javax.swing.JToggleButton F22;
    private javax.swing.JToggleButton F23;
    private javax.swing.JToggleButton F24;
    private javax.swing.JToggleButton F25;
    private javax.swing.JToggleButton F26;
    private javax.swing.JToggleButton F27;
    private javax.swing.JToggleButton F28;
    private javax.swing.JToggleButton F29;
    private javax.swing.JToggleButton F3;
    private javax.swing.JToggleButton F30;
    private javax.swing.JToggleButton F31;
    private javax.swing.JToggleButton F4;
    private javax.swing.JToggleButton F5;
    private javax.swing.JToggleButton F6;
    private javax.swing.JToggleButton F7;
    private javax.swing.JToggleButton F8;
    private javax.swing.JToggleButton F9;
    private javax.swing.JButton FButton;
    private javax.swing.JLabel FLabel;
    private javax.swing.JTextField FilterLocomotive;
    private javax.swing.JButton FiveButton;
    private javax.swing.JToggleButton Forward;
    private javax.swing.JButton FourButton;
    private javax.swing.JLabel FullSpeedLabel;
    private javax.swing.JTabbedPane FunctionTabs;
    private javax.swing.JButton GButton;
    private javax.swing.JLabel GLabel;
    private javax.swing.JButton HButton;
    private javax.swing.JLabel HLabel;
    private javax.swing.JButton IButton;
    private javax.swing.JLabel ILabel;
    private javax.swing.JPanel InnerLayoutPanel;
    private javax.swing.JButton JButton;
    private javax.swing.JLabel JLabel;
    private javax.swing.JButton KButton;
    private javax.swing.JLabel KLabel;
    private javax.swing.JLabel KeyboardLabel;
    private javax.swing.JLabel KeyboardLabel1;
    private javax.swing.JLabel KeyboardNumberLabel;
    private javax.swing.JPanel KeyboardPanel;
    private javax.swing.JTabbedPane KeyboardTab;
    private javax.swing.JButton LButton;
    private javax.swing.JLabel LLabel;
    private javax.swing.JScrollPane LayoutArea;
    private javax.swing.JComboBox LayoutList;
    private javax.swing.JButton LeftArrow;
    private javax.swing.JTextField LocAddressInput;
    private javax.swing.JPanel LocContainer;
    private javax.swing.JPanel LocControlPanel;
    private javax.swing.JPanel LocFunctionsPanel;
    private javax.swing.JLabel LocMappingNumberLabel;
    private javax.swing.JTextField LocNameInput;
    private javax.swing.JRadioButton LocTypeMFX;
    private javax.swing.JRadioButton LocTypeMM2;
    private javax.swing.JList LocomotiveList;
    private javax.swing.JButton MButton;
    private javax.swing.JLabel MLabel;
    private javax.swing.JPanel ManageLocPanel;
    private javax.swing.JButton NButton;
    private javax.swing.JLabel NLabel;
    private javax.swing.JButton NextKeyboard;
    private javax.swing.JButton NextLocMapping;
    private javax.swing.JButton NineButton;
    private javax.swing.JButton OButton;
    private javax.swing.JLabel OLabel;
    private javax.swing.JButton OnButton;
    private javax.swing.JButton OneButton;
    private javax.swing.JLabel OtherDirectionLabel;
    private javax.swing.JButton PButton;
    private javax.swing.JLabel PLabel;
    private javax.swing.JButton PowerOff;
    private javax.swing.JButton PrevKeyboard;
    private javax.swing.JButton PrevLocMapping;
    private javax.swing.JLabel PrimaryControls;
    private javax.swing.JButton QButton;
    private javax.swing.JLabel QLabel;
    private javax.swing.JButton RButton;
    private javax.swing.JLabel RLabel;
    private javax.swing.JButton RenameLocButton;
    private javax.swing.JButton RightArrow;
    private javax.swing.JTable RouteList;
    private javax.swing.JPanel RoutePanel;
    private javax.swing.JButton SButton;
    private javax.swing.JLabel SLabel;
    private javax.swing.JButton SevenButton;
    private javax.swing.JButton ShiftButton;
    private javax.swing.JButton SixButton;
    private javax.swing.JComboBox SizeList;
    private javax.swing.JLabel SlowStopLabel;
    private javax.swing.JButton SpacebarButton;
    private javax.swing.JSlider SpeedSlider;
    private javax.swing.JButton StopLoc;
    private javax.swing.JToggleButton SwitchButton1;
    private javax.swing.JToggleButton SwitchButton10;
    private javax.swing.JToggleButton SwitchButton11;
    private javax.swing.JToggleButton SwitchButton12;
    private javax.swing.JToggleButton SwitchButton13;
    private javax.swing.JToggleButton SwitchButton14;
    private javax.swing.JToggleButton SwitchButton15;
    private javax.swing.JToggleButton SwitchButton16;
    private javax.swing.JToggleButton SwitchButton17;
    private javax.swing.JToggleButton SwitchButton18;
    private javax.swing.JToggleButton SwitchButton19;
    private javax.swing.JToggleButton SwitchButton2;
    private javax.swing.JToggleButton SwitchButton20;
    private javax.swing.JToggleButton SwitchButton21;
    private javax.swing.JToggleButton SwitchButton22;
    private javax.swing.JToggleButton SwitchButton23;
    private javax.swing.JToggleButton SwitchButton24;
    private javax.swing.JToggleButton SwitchButton25;
    private javax.swing.JToggleButton SwitchButton26;
    private javax.swing.JToggleButton SwitchButton27;
    private javax.swing.JToggleButton SwitchButton28;
    private javax.swing.JToggleButton SwitchButton29;
    private javax.swing.JToggleButton SwitchButton3;
    private javax.swing.JToggleButton SwitchButton30;
    private javax.swing.JToggleButton SwitchButton31;
    private javax.swing.JToggleButton SwitchButton32;
    private javax.swing.JToggleButton SwitchButton33;
    private javax.swing.JToggleButton SwitchButton34;
    private javax.swing.JToggleButton SwitchButton35;
    private javax.swing.JToggleButton SwitchButton36;
    private javax.swing.JToggleButton SwitchButton37;
    private javax.swing.JToggleButton SwitchButton38;
    private javax.swing.JToggleButton SwitchButton39;
    private javax.swing.JToggleButton SwitchButton4;
    private javax.swing.JToggleButton SwitchButton40;
    private javax.swing.JToggleButton SwitchButton41;
    private javax.swing.JToggleButton SwitchButton42;
    private javax.swing.JToggleButton SwitchButton43;
    private javax.swing.JToggleButton SwitchButton44;
    private javax.swing.JToggleButton SwitchButton45;
    private javax.swing.JToggleButton SwitchButton46;
    private javax.swing.JToggleButton SwitchButton47;
    private javax.swing.JToggleButton SwitchButton48;
    private javax.swing.JToggleButton SwitchButton49;
    private javax.swing.JToggleButton SwitchButton5;
    private javax.swing.JToggleButton SwitchButton50;
    private javax.swing.JToggleButton SwitchButton51;
    private javax.swing.JToggleButton SwitchButton52;
    private javax.swing.JToggleButton SwitchButton53;
    private javax.swing.JToggleButton SwitchButton54;
    private javax.swing.JToggleButton SwitchButton55;
    private javax.swing.JToggleButton SwitchButton56;
    private javax.swing.JToggleButton SwitchButton57;
    private javax.swing.JToggleButton SwitchButton58;
    private javax.swing.JToggleButton SwitchButton59;
    private javax.swing.JToggleButton SwitchButton6;
    private javax.swing.JToggleButton SwitchButton60;
    private javax.swing.JToggleButton SwitchButton61;
    private javax.swing.JToggleButton SwitchButton62;
    private javax.swing.JToggleButton SwitchButton63;
    private javax.swing.JToggleButton SwitchButton7;
    private javax.swing.JToggleButton SwitchButton8;
    private javax.swing.JToggleButton SwitchButton9;
    private javax.swing.JButton SyncButton;
    private javax.swing.JButton SyncLocomotive;
    private javax.swing.JButton TButton;
    private javax.swing.JLabel TLabel;
    private javax.swing.JButton ThreeButton;
    private javax.swing.JButton TurnOffFnButton;
    private javax.swing.JButton TurnOnLightsButton;
    private javax.swing.JButton TwoButton;
    private javax.swing.JButton UButton;
    private javax.swing.JLabel ULabel;
    private javax.swing.JButton UpArrow;
    private javax.swing.JButton VButton;
    private javax.swing.JLabel VLabel;
    private javax.swing.JButton WButton;
    private javax.swing.JLabel WLabel;
    private javax.swing.JButton XButton;
    private javax.swing.JLabel XLabel;
    private javax.swing.JButton YButton;
    private javax.swing.JLabel YLabel;
    private javax.swing.JButton ZButton;
    private javax.swing.JLabel ZLabel;
    private javax.swing.JButton ZeroButton;
    private javax.swing.JLabel ZeroPercentSpeedLabel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JLabel changeLocomotiveLabel;
    private javax.swing.JButton clearButton;
    private javax.swing.JTextArea debugArea;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JList jList1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JLabel layoutListLabel;
    private javax.swing.JButton layoutNewWindow;
    private javax.swing.JPanel layoutPanel;
    private javax.swing.JLabel locIcon;
    private javax.swing.JPanel logPanel;
    private javax.swing.JLabel sizeLabel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void repaintLayout()
    {        
        this.trainGrid = new LayoutGrid(this.model.getLayout(this.LayoutList.getSelectedItem().toString()), Integer.parseInt(this.SizeList.getSelectedItem().toString()), InnerLayoutPanel, KeyboardTab, false);
        
        // Important!
        this.KeyboardTab.repaint();        
    }
}
