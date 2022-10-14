package gui;

import base.Locomotive;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import marklin.MarklinControlStation;
import marklin.MarklinLocomotive;
import model.View;
import model.ViewListener;

/**
 * UI for controlling trains and switches using the keyboard
 */
public class TrainControlUI extends javax.swing.JFrame implements View 
{
    // Preferences fields
    public static String IP_PREF = "initIP";
    public static String LAYOUT_OVERRIDE_PATH_PREF = "LayoutOverridePath";

    // Constants
    // Width of locomotive images
    public static final Integer LOC_ICON_WIDTH = 240;
    // Maximum displayed locomotive name length
    public static final Integer MAX_LOC_NAME = 30;

    // Load images
    public static final boolean LOAD_IMAGES = true;
    
    // View listener (model) reference
    private ViewListener model;
    
    // The active locomotive
    private MarklinLocomotive activeLoc;
    
    // The active locomotive button
    private javax.swing.JButton currentButton;
    
    private final HashMap<Integer, javax.swing.JButton> buttonMapping;
    private final HashMap<javax.swing.JButton, Integer> rButtonMapping;
    private final HashMap<javax.swing.JButton, JLabel> labelMapping;
    private final List<HashMap<javax.swing.JButton, Locomotive>> locMapping;
    private final HashMap<javax.swing.JToggleButton, Integer> functionMapping;
    private final HashMap<Integer, javax.swing.JToggleButton> rFunctionMapping;
    private final HashMap<Integer, javax.swing.JToggleButton> switchMapping;
    private LayoutGrid trainGrid;
    
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
    private static final int NUM_FN = 33;
    
    // Data save file name
    private static final String DATA_FILE_NAME = "UIState.data";
    
    // Image cache
    private static HashMap<String, Image> imageCache;
    
    // Preferences
    private final Preferences prefs;
    
    // Locomotive clipboard 
    private Locomotive copyTarget = null;
    
    // Locomotive selector
    private LocomotiveSelector selector;
        
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
        this.functionMapping.put(F32, 32);
        
        // Map numbers back to the corresponding buttons
        for (javax.swing.JToggleButton b : this.functionMapping.keySet())
        {
            this.rFunctionMapping.put(this.functionMapping.get(b), b);
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
            
            // Add right click events
            this.buttonMapping.get(keyCode).addMouseListener(new RightClickMenuListener(this, this.buttonMapping.get(keyCode)));
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
     * Returns a reference to the image cache, initializing it if needed
     * @return 
     */
    public static Map<String,Image> getImageCache()
    {
        if (imageCache == null)
        {
            imageCache = new HashMap<>();
        }
        
        return imageCache;
    }
    
    /**
     * Logs a message
     * @param message 
     */
    @Override
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
                    // this.model.log("Loading mapping for page " + Integer.toString(j + 1) + ", " + l.getName());
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
        
         // Hide CS3 app button on non-CS3 controllers
        if (!this.model.isCS3())
        {
            this.CS3OpenBrowser.setVisible(false);
        }
        
        // Display layout if applicable
        if (this.model.getLayoutList().isEmpty())
        {
            this.KeyboardTab.remove(this.layoutPanel);
            repaintPathLabel();
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
                
        // Generate list of locomotives
        selector = new LocomotiveSelector(this.model, this);
        selector.init();

        // Show window        
        this.setVisible(true);
    }
    
    public LocomotiveSelector getLocSelector()
    {
        return this.selector;
    }
    
    private void switchF(int fn)
    {        
        if (this.activeLoc != null)
        {
            this.fireF(fn, !this.activeLoc.getF(fn));
        }
    }
    
    private void fireF(int fn, boolean state)
    {        
        if (this.activeLoc != null)
        {            
            if (this.activeLoc.isFunctionPulse(fn))
            {
                new Thread(() -> {
                    this.activeLoc.toggleF(fn, MarklinLocomotive.PULSE_FUNCTION_DURATION);
                }).start();
            }
            else
            {
                new Thread(() -> {
                    this.activeLoc.setF(fn, state);
                }).start();
            }
            
            //repaintLoc();
        }
    }
    
    public void selectLocomotiveActivated(JButton button)
    {
        // Make sure this button is selected
        button.doClick();
        this.selector.setVisible(true);
        this.selector.updateScrollArea();
    }
    
    /**
     * Clipboard copy
     * @param button 
     */
    public void setCopyTarget(JButton button)
    {
        copyTarget = this.currentLocMapping().get(button);
    }
    
    /**
     * Copies a locomotive mapping to the next page
     * @param button 
     */
    public void copyToNextPage(JButton button)
    {        
        this.nextLocMapping().put(button, this.currentLocMapping().get(button));
        
        if (button.equals(this.currentButton))
        {
            displayCurrentButtonLoc(this.currentButton);
        }
    }
    
    /**
     * Has the copy target been set?
     * @return 
     */
    public boolean hasCopyTarget()
    {
        return copyTarget != null;
    }
    
    public void clearCopyTarget()
    {
        copyTarget = null;
    }
    
    public boolean buttonHasLocomotive(JButton b)
    {
        return this.currentLocMapping().get(b) != null;
    }
    
    public Locomotive getButtonLocomotive(JButton b)
    {
        return this.currentLocMapping().get(b);
    }
    
    public Locomotive getCopyTarget()
    {
        return copyTarget;
    }
    
    /**
     * Apply function presets
     * @param l 
     */
    public void applyPreferredFunctions(Locomotive l)
    {
        if (l != null)
        {
            new Thread(() -> {
                this.model.getLocByName(l.getName()).applyPreferredFunctions();
            }).start();
        }
    }
    
    /**
     * Save function presets
     * @param l 
     */
    public void savePreferredFunctions(Locomotive l)
    {
        if (l != null)
        {
            new Thread(() -> {
                this.model.getLocByName(l.getName()).savePrefferedFunctions();
            }).start();
        }
    }
    
    
    public void locFunctionsOff(Locomotive l)
    {
        if (l != null)
        {
            new Thread(() -> {
                this.model.locFunctionsOff(this.model.getLocByName(l.getName()));
            }).start();
        }
    }
    
    /**
     * Synchronizes the state of a locomotive w/ the Central Station
     * @param l 
     */
    public void syncLocomotive(Locomotive l)
    {
        if (l != null)
        {
            new Thread(() -> {
                this.model.syncLocomotive(l.getName());
                repaintLoc();
            }).start();
        }   
    }
        
    /**
     * Pastes to copied locomotive to a given UI button
     * @param b 
     */
    public void doPaste(JButton b)
    {
        if (b != null)
        {
            this.currentLocMapping().put(b, copyTarget);

            if (b.equals(this.currentButton))
            {
                displayCurrentButtonLoc(this.currentButton);
            }

            repaintMappings();  
            this.lastLocMappingPainted = this.locMappingNumber;
            
            copyTarget = null;
        }
    }
    
    public void mapLocToCurrentButton(String s)
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
        
        this.repaintMappings();
    }
    
    private synchronized void repaintMappings()
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
                this.switchMapping.get(i).setSelected(true);
            }
            else
            {
                this.switchMapping.get(i).setSelected(false);
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
    synchronized public Image getLocImage(String url, int size) throws IOException
    {
        String key = url + Integer.toString(size);
        
        if (!this.getImageCache().containsKey(key))
        {
            Image img = ImageIO.read(new URL(url));
            
            if (img != null)
            {
                float aspect = (float) img.getHeight(null) / (float) img.getWidth(null);
                this.getImageCache().put(key, img.getScaledInstance(size, (int) (size * aspect), 1));
            }
        }

        return this.getImageCache().get(key);        
    }
    
    /**
     * Repaints a locomotive button
     * @param b
     * @param l 
     */
    private void repaintIcon(JButton b, Locomotive l)
    {
        new Thread(() -> 
        {
            if (b != null)
            {
                if (l == null)
                {
                    noImageButton(b);
                }
                else if (LOAD_IMAGES && l.getImageURL() != null)
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
        }).start();
    }
    
    @Override
    public void repaintLoc()
    {     
        if (this.activeLoc != null)
        {            
            String name = this.activeLoc.getName();

            if (name.length() > MAX_LOC_NAME)
            {
                name = name.substring(0, MAX_LOC_NAME);
            }

            // Only repaint icon if the locomotive is changed
            // Visual stuff
            if (!this.ActiveLocLabel.getText().equals(name))
            {
                new Thread(() -> {
                    repaintIcon(this.currentButton, this.activeLoc);
                    
                    if (LOAD_IMAGES && this.activeLoc.getImageURL() != null)
                    {
                        try 
                        {
                            locIcon.setIcon(new javax.swing.ImageIcon(
                                getLocImage(this.activeLoc.getImageURL(), LOC_ICON_WIDTH)
                            ));      
                            locIcon.setText("");
                            locIcon.setVisible(true);
                        }
                        catch (Exception e)
                        {
                            locIcon.setIcon(null);
                            locIcon.setVisible(false);
                        }
                    }
                    else
                    {
                        locIcon.setIcon(null);
                        locIcon.setVisible(false);
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
                    final JToggleButton bt = this.rFunctionMapping.get(i);
                    final int functionType = this.activeLoc.getFunctionType(i);
                    final int fNo = i;

                    bt.setVisible(true);
                    bt.setEnabled(true);
                    
                    String targetURL = this.activeLoc.getFunctionIconUrl(functionType, false, true);
                    
                    bt.setHorizontalTextPosition(JButton.CENTER);
                    bt.setVerticalTextPosition(JButton.CENTER);
                    
                    new Thread(() -> {
                        try
                        {
                            if (functionType > 0 && LOAD_IMAGES)
                            {
                                Image icon = getLocImage(targetURL, 35);

                                if (icon != null)
                                {
                                    bt.setIcon(
                                        new javax.swing.ImageIcon(
                                            icon
                                        )
                                    );

                                    bt.setText("");                                    
                                    //bt.setMargin(new Insets(0,0,0,0));
                                }
                            }
                            else
                            {
                                bt.setIcon(null);
                                bt.setText("");                                    
                                //bt.setText("F" + Integer.toString(fNo));
                            }
                        }
                        catch (IOException e)
                        {
                            this.log("Icon not found: " + targetURL);
                        } 
                    }).start();
                    
                }
                
                for (int i = this.activeLoc.getNumF(); i < NUM_FN; i++)
                {
                    this.rFunctionMapping.get(i).setVisible(true);
                    this.rFunctionMapping.get(i).setEnabled(false);
                    
                    //this.rFunctionMapping.get(i).setText("F" + Integer.toString(i));
                    this.rFunctionMapping.get(i).setText("");                                    
                    this.rFunctionMapping.get(i).setIcon(null);
                }
                
                // Hide unnecessary function tabs
                if (this.activeLoc.getNumF() < 20)
                {
                    FunctionTabs.remove(this.F12Panel);
                }
                else
                {
                    FunctionTabs.add("F20-32", this.F12Panel);
                }

                this.Backward.setVisible(true);
                this.Forward.setVisible(true);
                this.SpeedSlider.setVisible(true);
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

            this.ActiveLocLabel.setText("No Locomotive (Right-click button)");

            this.CurrentKeyLabel.setText("Page " + this.locMappingNumber + " Button " 
                       + this.currentButton.getText()    
            );

            this.Backward.setVisible(false);
            this.Forward.setVisible(false);
            this.SpeedSlider.setVisible(false);
            this.FunctionTabs.setVisible(false);

            for (int i = 0; i < NUM_FN; i++)
            {
                this.rFunctionMapping.get(i).setVisible(false);
            }
        }
    }
    
    private Map<JButton, Locomotive> nextLocMapping()
    {
        return this.locMapping.get(this.locMappingNumber % TrainControlUI.NUM_LOC_MAPPINGS);
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

            Locomotive current = this.currentLocMapping().get(this.currentButton) ;
            
            if (current != null)
            {
                this.activeLoc = this.model.getLocByName(current.getName());
            }
            else
            {
                this.activeLoc = null;
            }
            
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
                //this.activeLoc.stop();
                this.activeLoc.instantStop();
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
        smallButton = new javax.swing.JButton();
        jLabel19 = new javax.swing.JLabel();
        allButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
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
        EditExistingLocLabel1 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        clearButton = new javax.swing.JButton();
        SyncButton = new javax.swing.JButton();
        TurnOffFnButton = new javax.swing.JButton();
        TurnOnLightsButton = new javax.swing.JButton();
        syncLocStateButton = new javax.swing.JButton();
        jPanel12 = new javax.swing.JPanel();
        jLabel32 = new javax.swing.JLabel();
        LayoutPathLabel = new javax.swing.JLabel();
        OverrideCS2DataPath = new javax.swing.JButton();
        CS3OpenBrowser = new javax.swing.JButton();
        EditExistingLocLabel3 = new javax.swing.JLabel();
        logPanel = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        debugArea = new javax.swing.JTextArea();
        LocFunctionsPanel = new javax.swing.JPanel();
        OnButton = new javax.swing.JButton();
        PowerOff = new javax.swing.JButton();
        ActiveLocLabel = new javax.swing.JLabel();
        SpeedSlider = new javax.swing.JSlider();
        Backward = new javax.swing.JToggleButton();
        Forward = new javax.swing.JToggleButton();
        CurrentKeyLabel = new javax.swing.JLabel();
        locIcon = new javax.swing.JLabel();
        FunctionTabs = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        F8 = new javax.swing.JToggleButton();
        F9 = new javax.swing.JToggleButton();
        F7 = new javax.swing.JToggleButton();
        F10 = new javax.swing.JToggleButton();
        F11 = new javax.swing.JToggleButton();
        F0 = new javax.swing.JToggleButton();
        F3 = new javax.swing.JToggleButton();
        F1 = new javax.swing.JToggleButton();
        F2 = new javax.swing.JToggleButton();
        F4 = new javax.swing.JToggleButton();
        F5 = new javax.swing.JToggleButton();
        F6 = new javax.swing.JToggleButton();
        jLabel10 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jLabel22 = new javax.swing.JLabel();
        jLabel23 = new javax.swing.JLabel();
        jLabel41 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        F12 = new javax.swing.JToggleButton();
        jLabel28 = new javax.swing.JLabel();
        F13 = new javax.swing.JToggleButton();
        jLabel27 = new javax.swing.JLabel();
        F14 = new javax.swing.JToggleButton();
        jLabel29 = new javax.swing.JLabel();
        F15 = new javax.swing.JToggleButton();
        F16 = new javax.swing.JToggleButton();
        F17 = new javax.swing.JToggleButton();
        F18 = new javax.swing.JToggleButton();
        jLabel25 = new javax.swing.JLabel();
        jLabel30 = new javax.swing.JLabel();
        jLabel31 = new javax.swing.JLabel();
        F19 = new javax.swing.JToggleButton();
        jLabel42 = new javax.swing.JLabel();
        F12Panel = new javax.swing.JPanel();
        jLabel24 = new javax.swing.JLabel();
        jLabel26 = new javax.swing.JLabel();
        F22 = new javax.swing.JToggleButton();
        F23 = new javax.swing.JToggleButton();
        jLabel33 = new javax.swing.JLabel();
        jLabel34 = new javax.swing.JLabel();
        F20 = new javax.swing.JToggleButton();
        F21 = new javax.swing.JToggleButton();
        jLabel40 = new javax.swing.JLabel();
        jLabel35 = new javax.swing.JLabel();
        F24 = new javax.swing.JToggleButton();
        jLabel44 = new javax.swing.JLabel();
        F27 = new javax.swing.JToggleButton();
        jLabel45 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        F25 = new javax.swing.JToggleButton();
        F26 = new javax.swing.JToggleButton();
        F28 = new javax.swing.JToggleButton();
        jLabel36 = new javax.swing.JLabel();
        F29 = new javax.swing.JToggleButton();
        jLabel37 = new javax.swing.JLabel();
        F32 = new javax.swing.JToggleButton();
        jLabel38 = new javax.swing.JLabel();
        F30 = new javax.swing.JToggleButton();
        jLabel39 = new javax.swing.JLabel();
        F31 = new javax.swing.JToggleButton();

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
            .addGap(0, 780, Short.MAX_VALUE)
        );
        InnerLayoutPanelLayout.setVerticalGroup(
            InnerLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 454, Short.MAX_VALUE)
        );

        LayoutArea.setViewportView(InnerLayoutPanel);

        sizeLabel.setForeground(new java.awt.Color(0, 0, 115));
        sizeLabel.setText("Size");

        SizeList.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Small", "Large" }));
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

        layoutNewWindow.setText("Large");
        layoutNewWindow.setFocusable(false);
        layoutNewWindow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                layoutNewWindowActionPerformed(evt);
            }
        });

        smallButton.setText("Small");
        smallButton.setFocusable(false);
        smallButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smallButtonActionPerformed(evt);
            }
        });

        jLabel19.setText("Show in pop-up:");

        allButton.setText("All");
        allButton.setFocusable(false);
        allButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allButtonActionPerformed(evt);
            }
        });

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

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
                        .addComponent(jLabel19)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(smallButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(layoutNewWindow, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(allButton)))
                .addContainerGap())
        );
        layoutPanelLayout.setVerticalGroup(
            layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layoutPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(LayoutArea, javax.swing.GroupLayout.DEFAULT_SIZE, 468, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(LayoutList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(layoutListLabel)
                        .addComponent(sizeLabel)
                        .addComponent(SizeList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(layoutNewWindow)
                        .addComponent(smallButton)
                        .addComponent(jLabel19))
                    .addComponent(allButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
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
                .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 445, Short.MAX_VALUE)
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
        SyncButton.setText("Sync Central Station Loc/Layout DB");
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
        TurnOnLightsButton.setText("Turn On All Lights");
        TurnOnLightsButton.setFocusable(false);
        TurnOnLightsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TurnOnLightsButtonActionPerformed(evt);
            }
        });

        syncLocStateButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        syncLocStateButton.setText("Sync Loc State");
        syncLocStateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                syncLocStateButtonActionPerformed(evt);
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
                    .addComponent(SyncButton, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
                    .addComponent(syncLocStateButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(TurnOnLightsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(TurnOffFnButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                .addComponent(syncLocStateButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TurnOnLightsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TurnOffFnButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel12.setBackground(new java.awt.Color(245, 245, 245));
        jPanel12.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        jLabel32.setText("Layout files are currently being loaded from:");

        LayoutPathLabel.setText("jLabel43");

        OverrideCS2DataPath.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        OverrideCS2DataPath.setText("Choose Local Data Folder");
        OverrideCS2DataPath.setFocusable(false);
        OverrideCS2DataPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OverrideCS2DataPathActionPerformed(evt);
            }
        });

        CS3OpenBrowser.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        CS3OpenBrowser.setText("Open CS3 Web App");
        CS3OpenBrowser.setFocusable(false);
        CS3OpenBrowser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CS3OpenBrowserActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel32)
                    .addComponent(LayoutPathLabel)
                    .addComponent(OverrideCS2DataPath)
                    .addComponent(CS3OpenBrowser))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel32)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(LayoutPathLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(OverrideCS2DataPath)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(CS3OpenBrowser)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        EditExistingLocLabel3.setForeground(new java.awt.Color(0, 0, 115));
        EditExistingLocLabel3.setText("CS2 Layout UI Override");

        javax.swing.GroupLayout ManageLocPanelLayout = new javax.swing.GroupLayout(ManageLocPanel);
        ManageLocPanel.setLayout(ManageLocPanelLayout);
        ManageLocPanelLayout.setHorizontalGroup(
            ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ManageLocPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ManageLocPanelLayout.createSequentialGroup()
                        .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(EditExistingLocLabel3))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(ManageLocPanelLayout.createSequentialGroup()
                        .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(AddNewLocLabel)
                            .addComponent(EditExistingLocLabel1))
                        .addGap(112, 418, Short.MAX_VALUE))))
        );
        ManageLocPanelLayout.setVerticalGroup(
            ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ManageLocPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(AddNewLocLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(EditExistingLocLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, 183, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(EditExistingLocLabel3)
                .addGap(2, 2, 2)
                .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        KeyboardTab.addTab("Tools", ManageLocPanel);

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
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 723, Short.MAX_VALUE)
                .addContainerGap())
        );
        logPanelLayout.setVerticalGroup(
            logPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(logPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 506, Short.MAX_VALUE)
                .addContainerGap())
        );

        KeyboardTab.addTab("Log", logPanel);

        LocFunctionsPanel.setBackground(new java.awt.Color(255, 255, 255));
        LocFunctionsPanel.setToolTipText(null);
        LocFunctionsPanel.setMinimumSize(new java.awt.Dimension(326, 560));
        LocFunctionsPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                LocControlPanelKeyPressed(evt);
            }
        });

        OnButton.setBackground(new java.awt.Color(204, 255, 204));
        OnButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        OnButton.setText("ON");
        OnButton.setToolTipText("Alt-G");
        OnButton.setFocusable(false);
        OnButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OnButtonActionPerformed(evt);
            }
        });

        PowerOff.setBackground(new java.awt.Color(255, 204, 204));
        PowerOff.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        PowerOff.setText("Power OFF");
        PowerOff.setToolTipText("Escape");
        PowerOff.setFocusable(false);
        PowerOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PowerOffActionPerformed(evt);
            }
        });

        ActiveLocLabel.setFont(new java.awt.Font("Tahoma", 0, 19)); // NOI18N
        ActiveLocLabel.setText("Locomotive Name");
        ActiveLocLabel.setFocusable(false);
        ActiveLocLabel.setMaximumSize(new java.awt.Dimension(296, 23));
        ActiveLocLabel.setMinimumSize(new java.awt.Dimension(296, 23));
        ActiveLocLabel.setPreferredSize(new java.awt.Dimension(296, 23));
        ActiveLocLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                ActiveLocLabelMouseReleased(evt);
            }
        });

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
        Backward.setText("<<<<< REV");
        Backward.setFocusable(false);
        Backward.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BackwardActionPerformed(evt);
            }
        });

        Forward.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        Forward.setText("FWD >>>>>");
        Forward.setFocusable(false);
        Forward.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ForwardActionPerformed(evt);
            }
        });

        CurrentKeyLabel.setBackground(new java.awt.Color(255, 255, 255));
        CurrentKeyLabel.setForeground(new java.awt.Color(0, 0, 115));
        CurrentKeyLabel.setText("Key Name");
        CurrentKeyLabel.setToolTipText(null);
        CurrentKeyLabel.setFocusable(false);

        locIcon.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        FunctionTabs.setBackground(new java.awt.Color(255, 255, 255));
        FunctionTabs.setTabPlacement(javax.swing.JTabbedPane.BOTTOM);
        FunctionTabs.setFocusable(false);
        FunctionTabs.setMinimumSize(new java.awt.Dimension(290, 78));
        FunctionTabs.setPreferredSize(new java.awt.Dimension(318, 173));

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));
        jPanel1.setPreferredSize(new java.awt.Dimension(313, 123));

        F8.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F8.setText("F8");
        F8.setFocusable(false);
        F8.setMaximumSize(new java.awt.Dimension(75, 35));
        F8.setMinimumSize(new java.awt.Dimension(75, 35));
        F8.setPreferredSize(new java.awt.Dimension(65, 35));
        F8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F9.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F9.setText("F9");
        F9.setFocusable(false);
        F9.setMaximumSize(new java.awt.Dimension(75, 35));
        F9.setMinimumSize(new java.awt.Dimension(75, 35));
        F9.setPreferredSize(new java.awt.Dimension(65, 35));
        F9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F7.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F7.setText("F7");
        F7.setFocusable(false);
        F7.setMaximumSize(new java.awt.Dimension(75, 35));
        F7.setMinimumSize(new java.awt.Dimension(75, 35));
        F7.setPreferredSize(new java.awt.Dimension(65, 35));
        F7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F10.setText("F10");
        F10.setFocusable(false);
        F10.setMaximumSize(new java.awt.Dimension(75, 35));
        F10.setMinimumSize(new java.awt.Dimension(75, 35));
        F10.setPreferredSize(new java.awt.Dimension(65, 35));
        F10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F11.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F11.setText("F11");
        F11.setFocusable(false);
        F11.setMaximumSize(new java.awt.Dimension(75, 35));
        F11.setMinimumSize(new java.awt.Dimension(75, 35));
        F11.setPreferredSize(new java.awt.Dimension(65, 35));
        F11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F0.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F0.setText("F0");
        F0.setFocusable(false);
        F0.setMaximumSize(new java.awt.Dimension(75, 35));
        F0.setMinimumSize(new java.awt.Dimension(75, 35));
        F0.setPreferredSize(new java.awt.Dimension(65, 35));
        F0.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F3.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F3.setText("F3");
        F3.setFocusable(false);
        F3.setMaximumSize(new java.awt.Dimension(75, 35));
        F3.setMinimumSize(new java.awt.Dimension(75, 35));
        F3.setPreferredSize(new java.awt.Dimension(65, 35));
        F3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F1.setText("F1");
        F1.setFocusable(false);
        F1.setMaximumSize(new java.awt.Dimension(75, 35));
        F1.setMinimumSize(new java.awt.Dimension(75, 35));
        F1.setPreferredSize(new java.awt.Dimension(65, 35));
        F1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F2.setText("F2");
        F2.setFocusable(false);
        F2.setMaximumSize(new java.awt.Dimension(75, 35));
        F2.setMinimumSize(new java.awt.Dimension(75, 35));
        F2.setPreferredSize(new java.awt.Dimension(65, 35));
        F2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F4.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F4.setText("F4");
        F4.setFocusable(false);
        F4.setMaximumSize(new java.awt.Dimension(75, 35));
        F4.setMinimumSize(new java.awt.Dimension(75, 35));
        F4.setPreferredSize(new java.awt.Dimension(65, 35));
        F4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F5.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F5.setText("F5");
        F5.setFocusable(false);
        F5.setMaximumSize(new java.awt.Dimension(75, 35));
        F5.setMinimumSize(new java.awt.Dimension(75, 35));
        F5.setPreferredSize(new java.awt.Dimension(65, 35));
        F5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F6.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F6.setText("F6");
        F6.setFocusable(false);
        F6.setMaximumSize(new java.awt.Dimension(75, 35));
        F6.setMinimumSize(new java.awt.Dimension(75, 35));
        F6.setPreferredSize(new java.awt.Dimension(65, 35));
        F6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel10.setForeground(new java.awt.Color(0, 0, 115));
        jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel10.setText("F0");

        jLabel13.setForeground(new java.awt.Color(0, 0, 115));
        jLabel13.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel13.setText("F4");

        jLabel14.setForeground(new java.awt.Color(0, 0, 115));
        jLabel14.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel14.setText("F8");

        jLabel15.setForeground(new java.awt.Color(0, 0, 115));
        jLabel15.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel15.setText("F6");

        jLabel16.setForeground(new java.awt.Color(0, 0, 115));
        jLabel16.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel16.setText("F1");

        jLabel17.setForeground(new java.awt.Color(0, 0, 115));
        jLabel17.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel17.setText("F3");

        jLabel18.setForeground(new java.awt.Color(0, 0, 115));
        jLabel18.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel18.setText("F5");

        jLabel20.setForeground(new java.awt.Color(0, 0, 115));
        jLabel20.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel20.setText("F7");

        jLabel21.setForeground(new java.awt.Color(0, 0, 115));
        jLabel21.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel21.setText("F10");

        jLabel22.setForeground(new java.awt.Color(0, 0, 115));
        jLabel22.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel22.setText("F9");

        jLabel23.setForeground(new java.awt.Color(0, 0, 115));
        jLabel23.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel23.setText("F11");

        jLabel41.setForeground(new java.awt.Color(0, 0, 115));
        jLabel41.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel41.setText("F2");

        jLabel11.setForeground(new java.awt.Color(0, 0, 115));
        jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel11.setText("F12");

        F12.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F12.setText("F12");
        F12.setFocusable(false);
        F12.setMaximumSize(new java.awt.Dimension(75, 35));
        F12.setMinimumSize(new java.awt.Dimension(75, 35));
        F12.setPreferredSize(new java.awt.Dimension(65, 35));
        F12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel28.setForeground(new java.awt.Color(0, 0, 115));
        jLabel28.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel28.setText("F13");

        F13.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F13.setText("F13");
        F13.setFocusable(false);
        F13.setMaximumSize(new java.awt.Dimension(75, 35));
        F13.setMinimumSize(new java.awt.Dimension(75, 35));
        F13.setPreferredSize(new java.awt.Dimension(65, 35));
        F13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel27.setForeground(new java.awt.Color(0, 0, 115));
        jLabel27.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel27.setText("F14");

        F14.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F14.setText("F14");
        F14.setFocusable(false);
        F14.setMaximumSize(new java.awt.Dimension(75, 35));
        F14.setMinimumSize(new java.awt.Dimension(75, 35));
        F14.setPreferredSize(new java.awt.Dimension(65, 35));
        F14.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel29.setForeground(new java.awt.Color(0, 0, 115));
        jLabel29.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel29.setText("F15");

        F15.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F15.setText("F15");
        F15.setFocusable(false);
        F15.setMaximumSize(new java.awt.Dimension(75, 35));
        F15.setMinimumSize(new java.awt.Dimension(75, 35));
        F15.setPreferredSize(new java.awt.Dimension(65, 35));
        F15.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F16.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F16.setText("F16");
        F16.setFocusable(false);
        F16.setMaximumSize(new java.awt.Dimension(75, 35));
        F16.setMinimumSize(new java.awt.Dimension(75, 35));
        F16.setPreferredSize(new java.awt.Dimension(65, 35));
        F16.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F17.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F17.setText("F17");
        F17.setFocusable(false);
        F17.setMaximumSize(new java.awt.Dimension(75, 35));
        F17.setMinimumSize(new java.awt.Dimension(75, 35));
        F17.setPreferredSize(new java.awt.Dimension(65, 35));
        F17.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F18.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F18.setText("F18");
        F18.setFocusable(false);
        F18.setMaximumSize(new java.awt.Dimension(75, 35));
        F18.setMinimumSize(new java.awt.Dimension(75, 35));
        F18.setPreferredSize(new java.awt.Dimension(65, 35));
        F18.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel25.setForeground(new java.awt.Color(0, 0, 115));
        jLabel25.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel25.setText("F16");

        jLabel30.setForeground(new java.awt.Color(0, 0, 115));
        jLabel30.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel30.setText("F17");

        jLabel31.setForeground(new java.awt.Color(0, 0, 115));
        jLabel31.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel31.setText("F18");

        F19.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F19.setText("F19");
        F19.setFocusable(false);
        F19.setMaximumSize(new java.awt.Dimension(75, 35));
        F19.setMinimumSize(new java.awt.Dimension(75, 35));
        F19.setPreferredSize(new java.awt.Dimension(65, 35));
        F19.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel42.setForeground(new java.awt.Color(0, 0, 115));
        jLabel42.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel42.setText("F19");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel14, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel10, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(F8, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(F4, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(F0, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel13, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(12, 12, 12)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel18, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel22, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel16, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F9, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(F5, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(F1, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addComponent(F2, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(F3, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(F6, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel41, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGap(12, 12, 12)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(F7, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(jLabel21, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(F10, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(12, 12, 12)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(F11, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel23, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jLabel20, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel11, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F12, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel28, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F13, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(F14, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(F15, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel27, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGap(12, 12, 12)
                                .addComponent(jLabel29, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(F16, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel25, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel30, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F17, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(12, 12, 12)
                                .addComponent(F18, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(F19, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(15, 15, 15)
                                .addComponent(jLabel31, javax.swing.GroupLayout.PREFERRED_SIZE, 59, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel42, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                .addContainerGap())
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {F0, F1, F10, F11, F2, F3, F4, F5, F6, F7, F8, F9});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F0, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(jLabel16)
                    .addComponent(jLabel17)
                    .addComponent(jLabel41))
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(2, 2, 2)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(jLabel18)
                    .addComponent(jLabel20)
                    .addComponent(jLabel15))
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel21)
                        .addComponent(jLabel22)
                        .addComponent(jLabel23))
                    .addComponent(jLabel14))
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F15, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(jLabel27)
                    .addComponent(jLabel28)
                    .addComponent(jLabel29))
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(2, 2, 2)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel25)
                    .addComponent(jLabel31)
                    .addComponent(jLabel30)
                    .addComponent(jLabel42)))
        );

        FunctionTabs.addTab("F0-19", jPanel1);

        F12Panel.setBackground(new java.awt.Color(255, 255, 255));

        jLabel24.setForeground(new java.awt.Color(0, 0, 115));
        jLabel24.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel24.setText("F23");

        jLabel26.setForeground(new java.awt.Color(0, 0, 115));
        jLabel26.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel26.setText("F20");

        F22.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F22.setText("F22");
        F22.setFocusable(false);
        F22.setMaximumSize(new java.awt.Dimension(75, 35));
        F22.setMinimumSize(new java.awt.Dimension(75, 35));
        F22.setPreferredSize(new java.awt.Dimension(65, 35));
        F22.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F23.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F23.setText("F23");
        F23.setFocusable(false);
        F23.setMaximumSize(new java.awt.Dimension(75, 35));
        F23.setMinimumSize(new java.awt.Dimension(75, 35));
        F23.setPreferredSize(new java.awt.Dimension(65, 35));
        F23.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel33.setForeground(new java.awt.Color(0, 0, 115));
        jLabel33.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel33.setText("F22");

        jLabel34.setForeground(new java.awt.Color(0, 0, 115));
        jLabel34.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel34.setText("F21");

        F20.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F20.setText("F20");
        F20.setFocusable(false);
        F20.setMaximumSize(new java.awt.Dimension(75, 35));
        F20.setMinimumSize(new java.awt.Dimension(75, 35));
        F20.setPreferredSize(new java.awt.Dimension(65, 35));
        F20.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F21.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F21.setText("F21");
        F21.setFocusable(false);
        F21.setMaximumSize(new java.awt.Dimension(75, 35));
        F21.setMinimumSize(new java.awt.Dimension(75, 35));
        F21.setPreferredSize(new java.awt.Dimension(65, 35));
        F21.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel40.setForeground(new java.awt.Color(0, 0, 115));
        jLabel40.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel40.setText("F27");

        jLabel35.setForeground(new java.awt.Color(0, 0, 115));
        jLabel35.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel35.setText("F29");

        F24.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F24.setText("F24");
        F24.setFocusable(false);
        F24.setMaximumSize(new java.awt.Dimension(75, 35));
        F24.setMinimumSize(new java.awt.Dimension(75, 35));
        F24.setPreferredSize(new java.awt.Dimension(65, 35));
        F24.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel44.setForeground(new java.awt.Color(0, 0, 115));
        jLabel44.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel44.setText("F30");

        F27.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F27.setText("F27");
        F27.setFocusable(false);
        F27.setMaximumSize(new java.awt.Dimension(75, 35));
        F27.setMinimumSize(new java.awt.Dimension(75, 35));
        F27.setPreferredSize(new java.awt.Dimension(65, 35));
        F27.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel45.setForeground(new java.awt.Color(0, 0, 115));
        jLabel45.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel45.setText("F31");

        jLabel12.setForeground(new java.awt.Color(0, 0, 115));
        jLabel12.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel12.setText("F24");

        F25.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F25.setText("F25");
        F25.setFocusable(false);
        F25.setMaximumSize(new java.awt.Dimension(75, 35));
        F25.setMinimumSize(new java.awt.Dimension(75, 35));
        F25.setPreferredSize(new java.awt.Dimension(65, 35));
        F25.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F26.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F26.setText("F26");
        F26.setFocusable(false);
        F26.setMaximumSize(new java.awt.Dimension(75, 35));
        F26.setMinimumSize(new java.awt.Dimension(75, 35));
        F26.setPreferredSize(new java.awt.Dimension(65, 35));
        F26.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        F28.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F28.setText("F28");
        F28.setFocusable(false);
        F28.setMaximumSize(new java.awt.Dimension(75, 35));
        F28.setMinimumSize(new java.awt.Dimension(75, 35));
        F28.setPreferredSize(new java.awt.Dimension(65, 35));
        F28.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel36.setForeground(new java.awt.Color(0, 0, 115));
        jLabel36.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel36.setText("F28");

        F29.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F29.setText("F29");
        F29.setFocusable(false);
        F29.setMaximumSize(new java.awt.Dimension(75, 35));
        F29.setMinimumSize(new java.awt.Dimension(75, 35));
        F29.setPreferredSize(new java.awt.Dimension(65, 35));
        F29.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel37.setForeground(new java.awt.Color(0, 0, 115));
        jLabel37.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel37.setText("F32");

        F32.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F32.setText("F32");
        F32.setFocusable(false);
        F32.setMaximumSize(new java.awt.Dimension(75, 35));
        F32.setMinimumSize(new java.awt.Dimension(75, 35));
        F32.setPreferredSize(new java.awt.Dimension(65, 35));
        F32.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel38.setForeground(new java.awt.Color(0, 0, 115));
        jLabel38.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel38.setText("F26");

        F30.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F30.setText("F30");
        F30.setFocusable(false);
        F30.setMaximumSize(new java.awt.Dimension(75, 35));
        F30.setMinimumSize(new java.awt.Dimension(75, 35));
        F30.setPreferredSize(new java.awt.Dimension(65, 35));
        F30.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        jLabel39.setForeground(new java.awt.Color(0, 0, 115));
        jLabel39.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel39.setText("F25");

        F31.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        F31.setText("F31");
        F31.setFocusable(false);
        F31.setMaximumSize(new java.awt.Dimension(75, 35));
        F31.setMinimumSize(new java.awt.Dimension(75, 35));
        F31.setPreferredSize(new java.awt.Dimension(65, 35));
        F31.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ProcessFunction(evt);
            }
        });

        javax.swing.GroupLayout F12PanelLayout = new javax.swing.GroupLayout(F12Panel);
        F12Panel.setLayout(F12PanelLayout);
        F12PanelLayout.setHorizontalGroup(
            F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(F12PanelLayout.createSequentialGroup()
                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(F12PanelLayout.createSequentialGroup()
                        .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel26, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F20, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel34, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F21, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel33, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F22, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(F23, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel24, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, F12PanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel37, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel12, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(F32, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(F28, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(F24, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel36, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel35, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(F29, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(F25, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel39, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(F12PanelLayout.createSequentialGroup()
                                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(jLabel44, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(F30, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel38, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(12, 12, 12)
                                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(F31, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel40, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jLabel45, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(F12PanelLayout.createSequentialGroup()
                                .addComponent(F26, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(F27, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap())
        );
        F12PanelLayout.setVerticalGroup(
            F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(F12PanelLayout.createSequentialGroup()
                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F20, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F21, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F22, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F23, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel26)
                    .addComponent(jLabel33)
                    .addComponent(jLabel34)
                    .addComponent(jLabel24))
                .addGap(0, 0, 0)
                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F25, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F24, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F26, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F27, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(jLabel38)
                    .addComponent(jLabel39)
                    .addComponent(jLabel40))
                .addGap(0, 0, 0)
                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(F28, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F29, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F30, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(F31, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(2, 2, 2)
                .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(F12PanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel44)
                        .addComponent(jLabel35)
                        .addComponent(jLabel45))
                    .addComponent(jLabel36))
                .addGap(0, 0, 0)
                .addComponent(F32, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(jLabel37)
                .addGap(0, 32, Short.MAX_VALUE))
        );

        FunctionTabs.addTab("F20-32", F12Panel);

        javax.swing.GroupLayout LocFunctionsPanelLayout = new javax.swing.GroupLayout(LocFunctionsPanel);
        LocFunctionsPanel.setLayout(LocFunctionsPanelLayout);
        LocFunctionsPanelLayout.setHorizontalGroup(
            LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ActiveLocLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addComponent(PowerOff, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(OnButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(CurrentKeyLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                                .addComponent(Backward, javax.swing.GroupLayout.DEFAULT_SIZE, 141, Short.MAX_VALUE)
                                .addGap(18, 18, 18)
                                .addComponent(Forward, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(SpeedSlider, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(FunctionTabs, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, LocFunctionsPanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(locIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 269, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        LocFunctionsPanelLayout.setVerticalGroup(
            LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(PowerOff)
                    .addComponent(OnButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(CurrentKeyLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ActiveLocLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(Backward)
                    .addComponent(Forward))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(SpeedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(8, 8, 8)
                .addComponent(FunctionTabs, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
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

    private void LocControlPanelKeyPressed(java.awt.event.KeyEvent evt)//GEN-FIRST:event_LocControlPanelKeyPressed
    {//GEN-HEADEREND:event_LocControlPanelKeyPressed
        int keyCode = evt.getKeyCode();
        boolean altPressed = (evt.getModifiers() & KeyEvent.ALT_MASK) != 0;
        
        if (altPressed && keyCode == KeyEvent.VK_G)
        {
            go();
        }
        else if (altPressed && keyCode == KeyEvent.VK_P)
        {
            this.applyPreferredFunctions(this.activeLoc);
        }
        else if (altPressed && keyCode == KeyEvent.VK_O)
        {
            this.locFunctionsOff(this.activeLoc);
        }
        else if (altPressed && keyCode == KeyEvent.VK_S)
        {
            this.savePreferredFunctions(this.activeLoc);
        }
        else if (this.buttonMapping.containsKey(keyCode))
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
                     //this.repaintLayout();
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
                     //this.repaintLayout();
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

    private void WindowClosed(java.awt.event.WindowEvent evt)//GEN-FIRST:event_WindowClosed
    {//GEN-HEADEREND:event_WindowClosed
        model.saveState();
        this.saveState();
        //model.stop();
        this.dispose();
        System.exit(0);
    }//GEN-LAST:event_WindowClosed

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
                new Thread(() -> {
                    this.model.execRoute(route);
                    refreshRouteList();
                }).start();
            }
        }   
    }//GEN-LAST:event_RouteListMouseClicked

    private void PrevLocMappingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PrevLocMappingActionPerformed
        this.switchLocMapping(this.locMappingNumber - 1);
    }//GEN-LAST:event_PrevLocMappingActionPerformed

    private void NextLocMappingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_NextLocMappingActionPerformed
        this.switchLocMapping(this.locMappingNumber + 1);
    }//GEN-LAST:event_NextLocMappingActionPerformed

    public void childWindowKeyEvent(java.awt.event.KeyEvent evt)
    {
        this.LocControlPanelKeyPressed(evt);
    }
    
    private void layoutNewWindowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_layoutNewWindowActionPerformed
        
        new Thread(() -> {
            LayoutPopupUI popup = new LayoutPopupUI(
                    this.model.getLayout(this.LayoutList.getSelectedItem().toString()),
                    this.layoutSizes.get("Large"),
                    this
            );

            popup.render();
        }).start();
    }//GEN-LAST:event_layoutNewWindowActionPerformed

    private void ProcessFunction(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ProcessFunction
        javax.swing.JToggleButton b =
        (javax.swing.JToggleButton) evt.getSource();

        Integer fNumber = this.functionMapping.get(b);
        Boolean state = b.isSelected();

        this.fireF(fNumber, state);
    }//GEN-LAST:event_ProcessFunction

    private void ForwardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ForwardActionPerformed
        forwardLoc();
    }//GEN-LAST:event_ForwardActionPerformed

    private void BackwardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BackwardActionPerformed
        backwardLoc();
    }//GEN-LAST:event_BackwardActionPerformed

    private void SpeedSliderDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_SpeedSliderDragged
        setLocSpeed(SpeedSlider.getValue());
    }//GEN-LAST:event_SpeedSliderDragged

    private void PowerOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PowerOffActionPerformed
        stop();
    }//GEN-LAST:event_PowerOffActionPerformed

    private void OnButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OnButtonActionPerformed
        go();
    }//GEN-LAST:event_OnButtonActionPerformed

    private void smallButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallButtonActionPerformed
        
        new Thread(() -> {
            LayoutPopupUI popup = new LayoutPopupUI(
                    this.model.getLayout(this.LayoutList.getSelectedItem().toString()),
                    this.layoutSizes.get("Small"),
                    this
            );

            popup.render();
        }).start();
    }//GEN-LAST:event_smallButtonActionPerformed

    private void allButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allButtonActionPerformed
        
        int size = this.LayoutList.getItemCount();
        for (int i = 0; i < size; i++)
        {
            String layoutName = LayoutList.getItemAt(i).toString();

            new Thread(() -> {
  
                LayoutPopupUI popup = new LayoutPopupUI(
                    this.model.getLayout(layoutName),
                    this.layoutSizes.get(this.SizeList.getSelectedItem().toString()),
                    this
                );

                popup.render();
            }).start();
        } 
    }//GEN-LAST:event_allButtonActionPerformed

    private void ManageLocPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ManageLocPanelMouseClicked
        //this.LocFunctionsPanel.requestFocus();
    }//GEN-LAST:event_ManageLocPanelMouseClicked

    private void CS3OpenBrowserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CS3OpenBrowserActionPerformed
        String url = this.model.getCS3AppUrl();

        if(Desktop.isDesktopSupported())
        {
            Desktop desktop = Desktop.getDesktop();

            try
            {
                desktop.browse(new URI(url));
            }
            catch (IOException | URISyntaxException e) {}
        }
        else
        {
            Runtime runtime = Runtime.getRuntime();

            try
            {
                runtime.exec("xdg-open " + url);
            }
            catch (IOException e) {}
        }
    }//GEN-LAST:event_CS3OpenBrowserActionPerformed

    private void OverrideCS2DataPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OverrideCS2DataPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int i = fc.showOpenDialog(this);
        if (i == JFileChooser.APPROVE_OPTION)
        {
            File f = fc.getSelectedFile();
            String filepath = f.getPath();

            this.prefs.put(LAYOUT_OVERRIDE_PATH_PREF, filepath);
        }

        this.model.syncWithCS2();
        this.LayoutList.setModel(new DefaultComboBoxModel(this.model.getLayoutList().toArray()));
        this.repaintLayout();

        // Show the layout tab if it wasn't already visible
        if (!this.KeyboardTab.getTitleAt(1).contains("Layout"))
        {
            this.KeyboardTab.add(this.layoutPanel, 1);
            this.KeyboardTab.setTitleAt(1, "Layout");
        }
    }//GEN-LAST:event_OverrideCS2DataPathActionPerformed

    private void TurnOnLightsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TurnOnLightsButtonActionPerformed

        new Thread(() ->
            {
                List<String> locs = new ArrayList<>();

                for (Map<JButton, Locomotive> m : this.locMapping)
                {
                    for (Locomotive l : m.values())
                    {
                        locs.add(l.getName());
                    }
                }

                this.model.lightsOn(locs);
            }).start();
    }//GEN-LAST:event_TurnOnLightsButtonActionPerformed

    private void TurnOffFnButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TurnOffFnButtonActionPerformed
        new Thread(() ->
            {
                this.model.allFunctionsOff();
            }).start();
    }//GEN-LAST:event_TurnOffFnButtonActionPerformed

    private void SyncButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SyncButtonActionPerformed
        Integer r = this.model.syncWithCS2();
        refreshRouteList();
        this.selector.refreshLocSelectorList();

        JOptionPane.showMessageDialog(ManageLocPanel, "Sync complete.  Items added: " + r.toString());
    }//GEN-LAST:event_SyncButtonActionPerformed

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
        int dialogResult = JOptionPane.showConfirmDialog(ManageLocPanel, "Are you sure you want to clear all mappings?", "Reset Keyboard", JOptionPane.YES_NO_OPTION);
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

    public void renameLocomotive (String s)
    {
        Locomotive l = this.model.getLocByName(s);

        if (l != null)
        {
            String newName = JOptionPane.showInputDialog(this, "Enter new name:", s);

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

                if (this.model.getLocByName(newName) != null)
                {
                    JOptionPane.showMessageDialog(this,
                        "Locomotive " + newName + " already exists in the locomotive DB.  Rename or delete it first.");
                    return;
                }

                this.model.renameLoc(l.getName(), newName);

                clearCopyTarget();
                repaintLoc();
                repaintMappings();
                selector.refreshLocSelectorList();
            }
        }
    }
    
    public void deleteLoc(String value)
    {
        Locomotive l = this.model.getLocByName(value);

            if (l != null)
            {
                // Also delete locomotive from active loc list
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

                this.model.deleteLoc(value);
                clearCopyTarget();
                repaintLoc();
                repaintMappings();
                selector.refreshLocSelectorList();
            }
    }
    
    private void AddLocButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddLocButtonActionPerformed
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
        this.selector.refreshLocSelectorList();

        // Rest form
        JOptionPane.showMessageDialog(this,
            "Locomotive added successfully");

        this.LocAddressInput.setText("");
        this.LocNameInput.setText("");
    }//GEN-LAST:event_AddLocButtonActionPerformed

    private void syncLocStateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncLocStateButtonActionPerformed
        
        int dialogResult = JOptionPane.showConfirmDialog(RoutePanel, "This function will query the Central Station for the current function status and direction of all locomotives, and may take several minutes. Continue?", "Sync State", JOptionPane.YES_NO_OPTION);
        if(dialogResult == JOptionPane.YES_OPTION)
        {
            new Thread(() -> {
                for (String s : this.model.getLocList())
                {
                    this.model.syncLocomotive(s);
                }
            }).start();
        }
    }//GEN-LAST:event_syncLocStateButtonActionPerformed

    private void ActiveLocLabelMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ActiveLocLabelMouseReleased
        this.selector.setVisible(true);
        this.selector.updateScrollArea();
    }//GEN-LAST:event_ActiveLocLabelMouseReleased

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
    private javax.swing.JButton CS3OpenBrowser;
    private javax.swing.JLabel CurrentKeyLabel;
    private javax.swing.JButton DButton;
    private javax.swing.JLabel DLabel;
    private javax.swing.JButton DeleteRouteButton;
    private javax.swing.JLabel DirectionLabel;
    private javax.swing.JButton DownArrow;
    private javax.swing.JButton EButton;
    private javax.swing.JLabel ELabel;
    private javax.swing.JLabel EStopLabel;
    private javax.swing.JLabel EditExistingLocLabel1;
    private javax.swing.JLabel EditExistingLocLabel3;
    private javax.swing.JButton EightButton;
    private javax.swing.JToggleButton F0;
    private javax.swing.JToggleButton F1;
    private javax.swing.JToggleButton F10;
    private javax.swing.JToggleButton F11;
    private javax.swing.JToggleButton F12;
    private javax.swing.JPanel F12Panel;
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
    private javax.swing.JToggleButton F32;
    private javax.swing.JToggleButton F4;
    private javax.swing.JToggleButton F5;
    private javax.swing.JToggleButton F6;
    private javax.swing.JToggleButton F7;
    private javax.swing.JToggleButton F8;
    private javax.swing.JToggleButton F9;
    private javax.swing.JButton FButton;
    private javax.swing.JLabel FLabel;
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
    private javax.swing.JLabel LayoutPathLabel;
    private javax.swing.JButton LeftArrow;
    private javax.swing.JTextField LocAddressInput;
    private javax.swing.JPanel LocContainer;
    private javax.swing.JPanel LocControlPanel;
    private javax.swing.JPanel LocFunctionsPanel;
    private javax.swing.JLabel LocMappingNumberLabel;
    private javax.swing.JTextField LocNameInput;
    private javax.swing.JRadioButton LocTypeMFX;
    private javax.swing.JRadioButton LocTypeMM2;
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
    private javax.swing.JButton OverrideCS2DataPath;
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
    private javax.swing.JButton allButton;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton clearButton;
    private javax.swing.JTextArea debugArea;
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
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel35;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabel37;
    private javax.swing.JLabel jLabel38;
    private javax.swing.JLabel jLabel39;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel40;
    private javax.swing.JLabel jLabel41;
    private javax.swing.JLabel jLabel42;
    private javax.swing.JLabel jLabel44;
    private javax.swing.JLabel jLabel45;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JList jList1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JLabel layoutListLabel;
    private javax.swing.JButton layoutNewWindow;
    private javax.swing.JPanel layoutPanel;
    private javax.swing.JLabel locIcon;
    private javax.swing.JPanel logPanel;
    private javax.swing.JLabel sizeLabel;
    private javax.swing.JButton smallButton;
    private javax.swing.JButton syncLocStateButton;
    // End of variables declaration//GEN-END:variables

    // Lap strings in the size dropdown to icon sizes
    Map<String, Integer> layoutSizes = Stream.of(new String[][] {
        { "Small", "30" }, 
        { "Large", "60" }, 
      }).collect(Collectors.toMap(data -> data[0], data -> Integer.parseInt(data[1])));
    
    public void repaintPathLabel()
    {
        // Set UI label
        if ("".equals(this.prefs.get(LAYOUT_OVERRIDE_PATH_PREF, "")))
        {
            LayoutPathLabel.setText(this.prefs.get(IP_PREF, ""));
        }
        else
        {
            LayoutPathLabel.setText(this.prefs.get(LAYOUT_OVERRIDE_PATH_PREF, ""));
        }
    }
    
    @Override
    public synchronized void repaintLayout()
    {    
        repaintPathLabel();
                  
        new Thread(() -> {
            InnerLayoutPanel.setVisible(false);
            this.trainGrid = new LayoutGrid(
                    this.model.getLayout(this.LayoutList.getSelectedItem().toString()), 
                    this.layoutSizes.get(this.SizeList.getSelectedItem().toString()), 
                    InnerLayoutPanel, 
                    KeyboardTab, 
                    false
            );
            InnerLayoutPanel.setVisible(true);

            // Important!
            this.KeyboardTab.repaint();    
        }).start();
    }
}
