package gui;

import automation.Edge;
import automation.Point;
import base.Locomotive;
import base.RouteCommand;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import marklin.MarklinControlStation;
import marklin.MarklinLayoutComponent;
import marklin.MarklinLocomotive;
import marklin.MarklinRoute;
import model.View;
import model.ViewListener;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

/**
 * UI for controlling trains and switches using the keyboard
 */
public class TrainControlUI extends javax.swing.JFrame implements View 
{
    // Preferences fields
    public static String IP_PREF = "initIP";
    public static String LAYOUT_OVERRIDE_PATH_PREF = "LayoutOverridePath";
    public static String SLIDER_SETTING_PREF = "SliderSetting";
    public static String ROUTE_SORT_PREF = "RouteSorting";

    // Constants
    // Width of locomotive images
    public static final Integer LOC_ICON_WIDTH = 240;
    // Maximum displayed locomotive name length
    public static final Integer MAX_LOC_NAME = 30;

    // Load images
    public static final boolean LOAD_IMAGES = true;
    
    // How much to increment speed when the arrow keys are pressed
    public static final int SPEED_STEP = 4;
    
    // View listener (model) reference
    private ViewListener model;
    
    // Graph viewer instance
    private GraphViewer graphViewer;
    
    // The active locomotive
    private MarklinLocomotive activeLoc;
    
    // The active locomotive button
    private javax.swing.JButton currentButton;
    
    private final HashMap<Integer, javax.swing.JButton> buttonMapping;
    private final HashMap<javax.swing.JButton, Integer> rButtonMapping;
    private final HashMap<javax.swing.JButton, JLabel> labelMapping;
    private final HashMap<javax.swing.JButton, javax.swing.JSlider> sliderMapping;
    private final HashMap<javax.swing.JSlider, javax.swing.JButton> rSliderMapping;
    private final List<HashMap<javax.swing.JButton, Locomotive>> locMapping;
    private final HashMap<javax.swing.JToggleButton, Integer> functionMapping;
    private final HashMap<Integer, javax.swing.JToggleButton> rFunctionMapping;
    private final HashMap<Integer, javax.swing.JToggleButton> switchMapping;
    private LayoutGrid trainGrid;
    private ExecutorService LayoutGridRenderer = Executors.newFixedThreadPool(1);

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
    private static final int NUM_LOC_MAPPINGS = 8;
        
    // Maximum number of functions
    private static final int NUM_FN = 33;
    
    // Data save file name
    private static final String DATA_FILE_NAME = "UIState.data";
    public static String AUTONOMY_FILE_NAME = "autonomy.json";

    // Image cache
    private static HashMap<String, Image> imageCache;
    
    // Preferences
    private final Preferences prefs;
    
    // Locomotive clipboard 
    private Locomotive copyTarget = null;
    private JButton copyTargetButton = null;
    private int copyTargetPage = 0;
    
    // Locomotive selector
    private LocomotiveSelector selector;
        
    /**
     * Creates new form MarklinUI
     */
    public TrainControlUI()
    {
        System.setProperty("org.graphstream.ui", "swing");
        
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
        this.sliderMapping = new HashMap<>();
        this.rSliderMapping = new HashMap<>();
        
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
        
        // Speed slider mappings
        this.sliderMapping.put(AButton, ASlider);
        this.sliderMapping.put(BButton, BSlider);
        this.sliderMapping.put(CButton, CSlider);
        this.sliderMapping.put(DButton, DSlider);
        this.sliderMapping.put(EButton, ESlider);
        this.sliderMapping.put(FButton, FSlider);
        this.sliderMapping.put(GButton, GSlider);
        this.sliderMapping.put(HButton, HSlider);
        this.sliderMapping.put(IButton, ISlider);
        this.sliderMapping.put(JButton, JSlider);
        this.sliderMapping.put(KButton, KSlider);
        this.sliderMapping.put(LButton, LSlider);
        this.sliderMapping.put(MButton, MSlider);
        this.sliderMapping.put(NButton, NSlider);
        this.sliderMapping.put(OButton, OSlider);
        this.sliderMapping.put(PButton, PSlider);
        this.sliderMapping.put(QButton, QSlider);
        this.sliderMapping.put(RButton, RSlider);
        this.sliderMapping.put(SButton, SSlider);
        this.sliderMapping.put(TButton, TSlider);
        this.sliderMapping.put(UButton, USlider);
        this.sliderMapping.put(VButton, VSlider);
        this.sliderMapping.put(WButton, WSlider);
        this.sliderMapping.put(XButton, XSlider);
        this.sliderMapping.put(YButton, YSlider);
        this.sliderMapping.put(ZButton, ZSlider);

        this.rSliderMapping.put(ASlider, AButton);
        this.rSliderMapping.put(BSlider, BButton);
        this.rSliderMapping.put(CSlider, CButton);
        this.rSliderMapping.put(DSlider, DButton);
        this.rSliderMapping.put(ESlider, EButton);
        this.rSliderMapping.put(FSlider, FButton);
        this.rSliderMapping.put(GSlider, GButton);
        this.rSliderMapping.put(HSlider, HButton);
        this.rSliderMapping.put(ISlider, IButton);
        this.rSliderMapping.put(JSlider, JButton);
        this.rSliderMapping.put(KSlider, KButton);
        this.rSliderMapping.put(LSlider, LButton);
        this.rSliderMapping.put(MSlider, MButton);
        this.rSliderMapping.put(NSlider, NButton);
        this.rSliderMapping.put(OSlider, OButton);
        this.rSliderMapping.put(PSlider, PButton);
        this.rSliderMapping.put(QSlider, QButton);
        this.rSliderMapping.put(RSlider, RButton);
        this.rSliderMapping.put(SSlider, SButton);
        this.rSliderMapping.put(TSlider, TButton);
        this.rSliderMapping.put(USlider, UButton);
        this.rSliderMapping.put(VSlider, VButton);
        this.rSliderMapping.put(WSlider, WButton);
        this.rSliderMapping.put(XSlider, XButton);
        this.rSliderMapping.put(YSlider, YButton);
        this.rSliderMapping.put(ZSlider, ZButton);
        
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
        
        this.sliderSetting.setSelected(this.prefs.getBoolean(SLIDER_SETTING_PREF, false));

        // Set selected route sort radio button
        this.sortByID.setSelected(!this.prefs.getBoolean(ROUTE_SORT_PREF, false));
        this.sortByName.setSelected(this.prefs.getBoolean(ROUTE_SORT_PREF, false));

        // Load autonomy data
        try
        {
            // Read object using ObjectInputStream
            ObjectInputStream obj_in = new ObjectInputStream(
                new FileInputStream(TrainControlUI.AUTONOMY_FILE_NAME)
            );
            
            // Read an object
            Object obj = obj_in.readObject();

            if (obj instanceof String)
            {
                // Cast object
                this.autonomyJSON.setText((String) obj);
            }
        }
        catch (Exception e)
        {
            
        }
        
        // Right-clicks on the route list
        this.RouteList.addMouseListener(new RightClickRouteMenu(this));   

        // Hide initially
        locCommandPanels.remove(this.locCommandTab);
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
                    newMap.put(this.rButtonMapping.get(b), loc.getName());
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
        
        if (!this.autonomyJSON.getText().trim().equals(""))
        {
            try 
            {
                ObjectOutputStream obj_out = new ObjectOutputStream(
                    new FileOutputStream(TrainControlUI.AUTONOMY_FILE_NAME));

                // Write object out to disk
                obj_out.writeObject(this.autonomyJSON.getText());
            }
            catch (IOException iOException)
            {
                this.model.log("Could not save autonomy JSON. " 
                    + iOException.getMessage());
            }
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
    synchronized public static Map<String,Image> getImageCache()
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
            this.switchMapping.get(i).setText((Integer.valueOf(i + offset)).toString());
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
            //this.KeyboardTab.remove(this.RoutePanel);
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
        showLocSelector();
    }
    
    public void showLocSelector()
    {
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
        copyTargetButton = button;
        copyTargetPage = this.locMappingNumber;
        
        // Put locomotive name in clipboard
        if (button != null)
        {
            StringSelection selection = new StringSelection(copyTarget.getName());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        }
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
        copyTargetButton = null;
        copyTargetPage = 0;
    }
    
    public boolean buttonHasLocomotive(JButton b)
    {
        return this.currentLocMapping().get(b) != null;
    }
    
    public Locomotive getButtonLocomotive(JButton b)
    {
        return this.currentLocMapping().get(b);
    }
    
    /**
     * Gets the locomotive which will be swapped to where the pasted loc came from
     * @return 
     */
    public Locomotive getSwapTarget()
    {
        return this.locMapping.get(this.copyTargetPage - 1).get(this.copyTargetButton);
    }
    
    /**
     * Gets the locomotive to be paster
     * @return 
     */
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
    
    public void applyPreferredSpeed(Locomotive l)
    {
        if (l != null)
        {
            new Thread(() -> {
                this.model.getLocByName(l.getName()).applyPreferredSpeed();
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
                this.model.getLocByName(l.getName()).savePreferredFunctions();
            }).start();
        }
    }
    
    public void savePreferredSpeed(Locomotive l)
    {
        if (l != null)
        {
            new Thread(() -> {
                this.model.getLocByName(l.getName()).savePreferredSpeed();
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
     * @param swap 
     */
    public void doPaste(JButton b, boolean swap)
    {
        if (b != null)
        {
            // Move the current locomotive to the source of the copy
            if (swap)
            {
                this.locMapping.get(this.copyTargetPage - 1).put(copyTargetButton, this.currentLocMapping().get(b));

                // If we are swapping to the same button that is currently active, activate the paste target so that everything gets repainted correctly
                if (copyTargetButton.equals(this.currentButton))
                {
                    displayCurrentButtonLoc(b);
                }
            }
            
            this.currentLocMapping().put(b, copyTarget);

            // If we are pasting to the same button that is currently active, activate the paste target (on the current page)
            if (b.equals(this.currentButton))
            {
                displayCurrentButtonLoc(this.currentButton);
            }
            
            repaintMappings();  
            // this.lastLocMappingPainted = this.locMappingNumber;
            
            clearCopyTarget();
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
                    repaintIcon(b, l, this.locMappingNumber);
                }
                
                this.sliderMapping.get(b).setEnabled(true);
                this.sliderMapping.get(b).setValue(l.getSpeed());      
            }
            else
            {
                if (!this.labelMapping.get(b).getText().equals("---"))
                {
                    this.labelMapping.get(b).setText("---");
                    repaintIcon(b, l, this.locMappingNumber);
                }
                
                this.sliderMapping.get(b).setValue(0);
                this.sliderMapping.get(b).setEnabled(false);   
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
        new Thread(() -> 
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
        }).start();
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
    public Image getLocImage(String url, int size) throws IOException
    {
        String key = url + Integer.toString(size);
        
        if (!TrainControlUI.getImageCache().containsKey(key))
        {
            Image img = ImageIO.read(new URL(url));
            
            if (img != null)
            {
                float aspect = (float) img.getHeight(null) / (float) img.getWidth(null);
                TrainControlUI.getImageCache().put(key, img.getScaledInstance(size, (int) (size * aspect), 1));
            }
        }

        return TrainControlUI.getImageCache().get(key);        
    }
    
    /**
     * Repaints a locomotive button
     * @param b
     * @param l 
     * @param correspondingLocMappingNumber
     */
    private void repaintIcon(JButton b, Locomotive l, Integer correspondingLocMappingNumber)
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
                        ImageIcon ic = new javax.swing.ImageIcon(
                            getLocImage(l.getImageURL(), 65)
                        );
                        
                        // The active page has changed since this thread was called.  No need to update the UI.
                        if (this.locMappingNumber != correspondingLocMappingNumber)
                        {
                            return;
                        }
                        
                        b.setIcon(ic);  

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
            
            // Pre-compute this so we can check if it has changed
            String locLabel = "Page " + this.locMappingNumber + " Button " 
                + this.currentButton.getText()
                + " (" + this.activeLoc.getDecoderTypeLabel() + " " + this.model.getLocAddress(this.activeLoc.getName())
                + ")";

            // Only repaint icon if the locomotive is changed
            // Visual stuff
            if (!this.ActiveLocLabel.getText().equals(name) || !locLabel.equals(CurrentKeyLabel.getText()))
            {
                new Thread(() -> {
                    repaintIcon(this.currentButton, this.activeLoc, this.locMappingNumber);
                    
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

                this.CurrentKeyLabel.setText(locLabel);
                
                for (int i = 0; i < this.activeLoc.getNumF(); i++)
                {
                    final JToggleButton bt = this.rFunctionMapping.get(i);
                    final int functionType = this.activeLoc.getFunctionType(i);

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
                                }
                                else
                                {
                                    //bt.setText("F" + Integer.toString(fNo));
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
                            //bt.setText("F" + Integer.toString(fNo));
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
            this.repaintMappings();
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

            Locomotive current = this.currentLocMapping().get(this.currentButton);
            
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
        new Thread(() ->
        {
            this.model.go();
        }).start();
    }
    
    private void stop()
    {
        new Thread(() ->
        {
            this.model.stop();
        }).start();
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
        buttonGroup2 = new javax.swing.ButtonGroup();
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
        QSlider = new javax.swing.JSlider();
        WSlider = new javax.swing.JSlider();
        ESlider = new javax.swing.JSlider();
        RSlider = new javax.swing.JSlider();
        TSlider = new javax.swing.JSlider();
        YSlider = new javax.swing.JSlider();
        USlider = new javax.swing.JSlider();
        OSlider = new javax.swing.JSlider();
        PSlider = new javax.swing.JSlider();
        ISlider = new javax.swing.JSlider();
        ASlider = new javax.swing.JSlider();
        SSlider = new javax.swing.JSlider();
        DSlider = new javax.swing.JSlider();
        FSlider = new javax.swing.JSlider();
        GSlider = new javax.swing.JSlider();
        HSlider = new javax.swing.JSlider();
        JSlider = new javax.swing.JSlider();
        KSlider = new javax.swing.JSlider();
        LSlider = new javax.swing.JSlider();
        MSlider = new javax.swing.JSlider();
        NSlider = new javax.swing.JSlider();
        BSlider = new javax.swing.JSlider();
        VSlider = new javax.swing.JSlider();
        CSlider = new javax.swing.JSlider();
        XSlider = new javax.swing.JSlider();
        ZSlider = new javax.swing.JSlider();
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
        AltEmergencyStop = new javax.swing.JButton();
        jLabel8 = new javax.swing.JLabel();
        ZeroButton = new javax.swing.JButton();
        FullSpeedLabel = new javax.swing.JLabel();
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
        PrimaryControls = new javax.swing.JLabel();
        sliderSetting = new javax.swing.JCheckBox();
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
        jScrollPane5 = new javax.swing.JScrollPane();
        RouteList = new javax.swing.JTable();
        AddRouteButton = new javax.swing.JButton();
        sortByName = new javax.swing.JRadioButton();
        sortByID = new javax.swing.JRadioButton();
        BulkEnable = new javax.swing.JButton();
        BulkDisable = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
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
        autoPanel = new javax.swing.JPanel();
        validateButton = new javax.swing.JButton();
        startAutonomy = new javax.swing.JButton();
        locCommandPanels = new javax.swing.JTabbedPane();
        autonomyPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        autonomyJSON = new javax.swing.JTextArea();
        jLabel6 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        locCommandTab = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        autoLocPanel = new javax.swing.JPanel();
        exportJSON = new javax.swing.JButton();
        gracefulStop = new javax.swing.JButton();
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
        LocTypeDCC = new javax.swing.JRadioButton();
        checkDuplicates = new javax.swing.JButton();
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
        setFocusable(false);
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMinimumSize(new java.awt.Dimension(1078, 572));
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
        LocControlPanel.setMaximumSize(new java.awt.Dimension(803, 585));
        LocControlPanel.setMinimumSize(new java.awt.Dimension(803, 585));
        LocControlPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                LocControlPanelKeyPressed(evt);
            }
        });

        jLabel5.setForeground(new java.awt.Color(0, 0, 155));
        jLabel5.setText("Locomotive Mapping");

        LocContainer.setBackground(new java.awt.Color(245, 245, 245));
        LocContainer.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        LocContainer.setMaximumSize(new java.awt.Dimension(773, 366));
        LocContainer.setMinimumSize(new java.awt.Dimension(773, 366));

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

        QSlider.setMajorTickSpacing(10);
        QSlider.setMinorTickSpacing(5);
        QSlider.setFocusable(false);
        QSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        QSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        QSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        QSlider.setRequestFocusEnabled(false);
        QSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        WSlider.setFocusable(false);
        WSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        WSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        WSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        WSlider.setRequestFocusEnabled(false);
        WSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        ESlider.setFocusable(false);
        ESlider.setMaximumSize(new java.awt.Dimension(60, 26));
        ESlider.setMinimumSize(new java.awt.Dimension(60, 26));
        ESlider.setPreferredSize(new java.awt.Dimension(60, 26));
        ESlider.setRequestFocusEnabled(false);
        ESlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        RSlider.setFocusable(false);
        RSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        RSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        RSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        RSlider.setRequestFocusEnabled(false);
        RSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        TSlider.setFocusable(false);
        TSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        TSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        TSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        TSlider.setRequestFocusEnabled(false);
        TSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        YSlider.setFocusable(false);
        YSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        YSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        YSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        YSlider.setRequestFocusEnabled(false);
        YSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        USlider.setFocusable(false);
        USlider.setMaximumSize(new java.awt.Dimension(60, 26));
        USlider.setMinimumSize(new java.awt.Dimension(60, 26));
        USlider.setPreferredSize(new java.awt.Dimension(60, 26));
        USlider.setRequestFocusEnabled(false);
        USlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        OSlider.setFocusable(false);
        OSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        OSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        OSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        OSlider.setRequestFocusEnabled(false);
        OSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        PSlider.setFocusable(false);
        PSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        PSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        PSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        PSlider.setRequestFocusEnabled(false);
        PSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        ISlider.setFocusable(false);
        ISlider.setMaximumSize(new java.awt.Dimension(60, 26));
        ISlider.setMinimumSize(new java.awt.Dimension(60, 26));
        ISlider.setPreferredSize(new java.awt.Dimension(60, 26));
        ISlider.setRequestFocusEnabled(false);
        ISlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        ASlider.setFocusable(false);
        ASlider.setMaximumSize(new java.awt.Dimension(60, 26));
        ASlider.setMinimumSize(new java.awt.Dimension(60, 26));
        ASlider.setPreferredSize(new java.awt.Dimension(60, 26));
        ASlider.setRequestFocusEnabled(false);
        ASlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        SSlider.setFocusable(false);
        SSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        SSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        SSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        SSlider.setRequestFocusEnabled(false);
        SSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        DSlider.setFocusable(false);
        DSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        DSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        DSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        DSlider.setRequestFocusEnabled(false);
        DSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        FSlider.setFocusable(false);
        FSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        FSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        FSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        FSlider.setRequestFocusEnabled(false);
        FSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        GSlider.setFocusable(false);
        GSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        GSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        GSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        GSlider.setRequestFocusEnabled(false);
        GSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        HSlider.setFocusable(false);
        HSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        HSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        HSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        HSlider.setRequestFocusEnabled(false);
        HSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        JSlider.setFocusable(false);
        JSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        JSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        JSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        JSlider.setRequestFocusEnabled(false);
        JSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        KSlider.setFocusable(false);
        KSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        KSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        KSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        KSlider.setRequestFocusEnabled(false);
        KSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        LSlider.setFocusable(false);
        LSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        LSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        LSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        LSlider.setRequestFocusEnabled(false);
        LSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        MSlider.setFocusable(false);
        MSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        MSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        MSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        MSlider.setRequestFocusEnabled(false);
        MSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        NSlider.setFocusable(false);
        NSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        NSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        NSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        NSlider.setRequestFocusEnabled(false);
        NSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        BSlider.setFocusable(false);
        BSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        BSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        BSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        BSlider.setRequestFocusEnabled(false);
        BSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        VSlider.setFocusable(false);
        VSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        VSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        VSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        VSlider.setRequestFocusEnabled(false);
        VSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        CSlider.setFocusable(false);
        CSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        CSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        CSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        CSlider.setRequestFocusEnabled(false);
        CSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        XSlider.setFocusable(false);
        XSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        XSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        XSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        XSlider.setRequestFocusEnabled(false);
        XSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        ZSlider.setFocusable(false);
        ZSlider.setMaximumSize(new java.awt.Dimension(60, 26));
        ZSlider.setMinimumSize(new java.awt.Dimension(60, 26));
        ZSlider.setPreferredSize(new java.awt.Dimension(60, 26));
        ZSlider.setRequestFocusEnabled(false);
        ZSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateSliderSpeed(evt);
            }
        });

        javax.swing.GroupLayout LocContainerLayout = new javax.swing.GroupLayout(LocContainer);
        LocContainer.setLayout(LocContainerLayout);
        LocContainerLayout.setHorizontalGroup(
            LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocContainerLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(QButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(QLabel, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(QSlider, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(WLabel)
                            .addComponent(WButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(WSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(ELabel)
                            .addComponent(EButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ESlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(RSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(RLabel)
                            .addComponent(RButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(TSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(TLabel)
                            .addComponent(TButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(YSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(YLabel)
                            .addComponent(YButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(USlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(ULabel)
                            .addComponent(UButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(IButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ILabel)
                            .addComponent(ISlider, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(OSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(OLabel)
                            .addComponent(OButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(PLabel)
                            .addComponent(PButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(PSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(LocContainerLayout.createSequentialGroup()
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(ASlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(AButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ALabel, javax.swing.GroupLayout.Alignment.LEADING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(SSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(SLabel)
                                    .addComponent(SButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(DLabel)
                                    .addComponent(DButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(DSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(FSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(FLabel)
                                    .addComponent(FButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(GSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(GLabel)
                                    .addComponent(GButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(HSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(HLabel)
                                    .addComponent(HButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(JSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(JLabel)
                                    .addComponent(JButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(KSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(KLabel)
                                    .addComponent(KButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(LocContainerLayout.createSequentialGroup()
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(ZSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(ZButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ZLabel, javax.swing.GroupLayout.Alignment.LEADING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(XSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(XLabel)
                                    .addComponent(XButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(CSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(CLabel)
                                    .addComponent(CButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(VSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(VLabel)
                                    .addComponent(VButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(BSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(BLabel)
                                    .addComponent(BButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(NSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(NLabel)
                                    .addComponent(NButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(MLabel)
                                    .addComponent(MButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(MSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(LocContainerLayout.createSequentialGroup()
                                .addComponent(PrevLocMapping)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(NextLocMapping))
                            .addGroup(LocContainerLayout.createSequentialGroup()
                                .addGap(3, 3, 3)
                                .addComponent(LocMappingNumberLabel))
                            .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(LLabel)
                                .addComponent(LButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(LSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
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
                .addGap(0, 0, 0)
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(QSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(WSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(ESlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(RSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(YSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(OSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(PSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(ISlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(TSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(USlider, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(1, 1, 1)
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
                .addGap(24, 24, 24)
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
                .addGap(0, 0, 0)
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(ASlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(SSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(DSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(FSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(HSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(GSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(JSlider, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(KSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(LSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
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
                .addGap(24, 24, 24)
                .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                            .addComponent(ZButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(XButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(CButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(VButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(BButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(MButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(NButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, 0)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(ZSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(XSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(CSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(VSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(NSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(BSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(MSlider, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(ZLabel)
                            .addComponent(XLabel)
                            .addComponent(CLabel)
                            .addComponent(VLabel)
                            .addComponent(BLabel)
                            .addComponent(NLabel)
                            .addComponent(MLabel)))
                    .addGroup(LocContainerLayout.createSequentialGroup()
                        .addComponent(LocMappingNumberLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(LocContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(PrevLocMapping)
                            .addComponent(NextLocMapping))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel6.setBackground(new java.awt.Color(245, 245, 245));
        jPanel6.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        UpArrow.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        UpArrow.setText("↑");
        UpArrow.setToolTipText("Increase Speed");
        UpArrow.setFocusable(false);
        UpArrow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UpArrowLetterButtonPressed(evt);
            }
        });

        DownArrow.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        DownArrow.setText("↓");
        DownArrow.setToolTipText("Decrease Speed");
        DownArrow.setFocusable(false);
        DownArrow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DownArrowLetterButtonPressed(evt);
            }
        });

        RightArrow.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        RightArrow.setText("→");
        RightArrow.setToolTipText("Switch Direction");
        RightArrow.setFocusable(false);
        RightArrow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RightArrowLetterButtonPressed(evt);
            }
        });

        LeftArrow.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
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

        jLabel7.setText("Increment Speed");

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

        ZeroButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        ZeroButton.setText("0");
        ZeroButton.setFocusable(false);
        ZeroButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ZeroButtonActionPerformed(evt);
            }
        });

        FullSpeedLabel.setText("100% Speed");

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

        ZeroPercentSpeedLabel.setText("0% Speed");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(ZeroPercentSpeedLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(FullSpeedLabel))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addGroup(jPanel6Layout.createSequentialGroup()
                                    .addComponent(OneButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(TwoButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(ShiftButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(SlowStopLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addGroup(jPanel6Layout.createSequentialGroup()
                                    .addComponent(ThreeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(FourButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(SpacebarButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(jLabel8))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addGroup(jPanel6Layout.createSequentialGroup()
                                    .addComponent(FiveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(SixButton, javax.swing.GroupLayout.DEFAULT_SIZE, 66, Short.MAX_VALUE))
                                .addComponent(AltEmergencyStop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(EStopLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addComponent(DirectionLabel)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel6Layout.createSequentialGroup()
                                        .addComponent(SevenButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(0, 0, Short.MAX_VALUE))
                                    .addComponent(LeftArrow, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel6Layout.createSequentialGroup()
                                        .addComponent(EightButton, javax.swing.GroupLayout.DEFAULT_SIZE, 66, Short.MAX_VALUE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(NineButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(ZeroButton, javax.swing.GroupLayout.DEFAULT_SIZE, 66, Short.MAX_VALUE))
                                    .addGroup(jPanel6Layout.createSequentialGroup()
                                        .addComponent(RightArrow, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                            .addGroup(jPanel6Layout.createSequentialGroup()
                                                .addComponent(UpArrow, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(DownArrow, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                            .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel6Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {EightButton, FiveButton, FourButton, NineButton, OneButton, SevenButton, SixButton, ThreeButton, TwoButton, ZeroButton});

        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(NineButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ZeroButton))
                    .addComponent(EightButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(SevenButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(SixButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(FiveButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(FourButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ThreeButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(TwoButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(OneButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ZeroPercentSpeedLabel)
                    .addComponent(FullSpeedLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(SpacebarButton)
                    .addComponent(ShiftButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(AltEmergencyStop)
                    .addComponent(LeftArrow)
                    .addComponent(RightArrow, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(UpArrow)
                    .addComponent(DownArrow))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(EStopLabel)
                    .addComponent(DirectionLabel)
                    .addComponent(jLabel7)
                    .addComponent(SlowStopLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        PrimaryControls.setForeground(new java.awt.Color(0, 0, 155));
        PrimaryControls.setText("Primary Keyboard Controls");

        sliderSetting.setText("Sliders change active loc");
        sliderSetting.setFocusable(false);
        sliderSetting.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sliderSettingActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout LocControlPanelLayout = new javax.swing.GroupLayout(LocControlPanel);
        LocControlPanel.setLayout(LocControlPanelLayout);
        LocControlPanelLayout.setHorizontalGroup(
            LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(LocControlPanelLayout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sliderSetting))
                    .addComponent(PrimaryControls)
                    .addComponent(LocContainer, javax.swing.GroupLayout.PREFERRED_SIZE, 731, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addContainerGap(57, Short.MAX_VALUE))
        );
        LocControlPanelLayout.setVerticalGroup(
            LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(sliderSetting))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(LocContainer, javax.swing.GroupLayout.PREFERRED_SIZE, 338, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
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
            .addGap(0, 529, Short.MAX_VALUE)
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

        layoutNewWindow.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        layoutNewWindow.setText("Large");
        layoutNewWindow.setFocusable(false);
        layoutNewWindow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                layoutNewWindowActionPerformed(evt);
            }
        });

        smallButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        smallButton.setText("Small");
        smallButton.setFocusable(false);
        smallButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smallButtonActionPerformed(evt);
            }
        });

        jLabel19.setText("Show in pop-up:");

        allButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
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
                    .addComponent(LayoutArea, javax.swing.GroupLayout.DEFAULT_SIZE, 773, Short.MAX_VALUE)
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
                .addComponent(LayoutArea, javax.swing.GroupLayout.DEFAULT_SIZE, 458, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(LayoutList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(layoutListLabel)
                        .addComponent(sizeLabel)
                        .addComponent(SizeList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(layoutNewWindow)
                        .addComponent(smallButton)
                        .addComponent(jLabel19))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(allButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );

        KeyboardTab.addTab("Layout", layoutPanel);

        RoutePanel.setBackground(new java.awt.Color(238, 238, 238));
        RoutePanel.setFocusable(false);

        jLabel2.setForeground(new java.awt.Color(0, 0, 115));
        jLabel2.setText("Routes (Click to Execute / Right-click to Edit)");

        RouteList.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        RouteList.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        RouteList.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        RouteList.setFocusable(false);
        RouteList.setGridColor(new java.awt.Color(0, 0, 0));
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

        AddRouteButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        AddRouteButton.setText("Add New Route");
        AddRouteButton.setFocusable(false);
        AddRouteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddRouteButtonActionPerformed(evt);
            }
        });

        buttonGroup2.add(sortByName);
        sortByName.setText("Sort by Name");
        sortByName.setFocusable(false);
        sortByName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortByNameActionPerformed(evt);
            }
        });

        buttonGroup2.add(sortByID);
        sortByID.setText("Sort by ID");
        sortByID.setFocusable(false);
        sortByID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortByIDActionPerformed(evt);
            }
        });

        BulkEnable.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        BulkEnable.setText("Bulk Enable");
        BulkEnable.setFocusable(false);
        BulkEnable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BulkEnableActionPerformed(evt);
            }
        });

        BulkDisable.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        BulkDisable.setText("Bulk Disable");
        BulkDisable.setFocusable(false);
        BulkDisable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BulkDisableActionPerformed(evt);
            }
        });

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator2.setMinimumSize(new java.awt.Dimension(10, 10));
        jSeparator2.setPreferredSize(new java.awt.Dimension(30, 10));

        javax.swing.GroupLayout RoutePanelLayout = new javax.swing.GroupLayout(RoutePanel);
        RoutePanel.setLayout(RoutePanelLayout);
        RoutePanelLayout.setHorizontalGroup(
            RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RoutePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5)
                    .addGroup(RoutePanelLayout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(0, 457, Short.MAX_VALUE))
                    .addGroup(RoutePanelLayout.createSequentialGroup()
                        .addComponent(AddRouteButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(BulkEnable)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(BulkDisable)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sortByName)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(sortByID)))
                .addContainerGap())
        );
        RoutePanelLayout.setVerticalGroup(
            RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(RoutePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 458, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 81, Short.MAX_VALUE)
                .addGroup(RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(AddRouteButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, RoutePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(sortByName, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(BulkEnable)
                        .addComponent(BulkDisable)
                        .addComponent(sortByID, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
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

        autoPanel.setBackground(new java.awt.Color(238, 238, 238));

        validateButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        validateButton.setText("Validate JSON / Instant Stop");
        validateButton.setFocusable(false);
        validateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                validateButtonActionPerformed(evt);
            }
        });

        startAutonomy.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        startAutonomy.setText("Start Autonomous Operation");
        startAutonomy.setEnabled(false);
        startAutonomy.setFocusable(false);
        startAutonomy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startAutonomyActionPerformed(evt);
            }
        });

        locCommandPanels.setBackground(new java.awt.Color(255, 255, 255));
        locCommandPanels.setFocusable(false);
        locCommandPanels.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                locCommandPanelsMouseClicked(evt);
            }
        });

        autonomyPanel.setBackground(new java.awt.Color(255, 255, 255));

        autonomyJSON.setColumns(20);
        autonomyJSON.setFont(new java.awt.Font("Monospaced", 0, 16)); // NOI18N
        autonomyJSON.setRows(5);
        autonomyJSON.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                autonomyJSONKeyReleased(evt);
            }
        });
        jScrollPane2.setViewportView(autonomyJSON);

        jLabel6.setForeground(new java.awt.Color(0, 0, 115));

        jLabel9.setForeground(new java.awt.Color(0, 0, 115));
        jLabel9.setText("Documentation");
        jLabel9.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel9.setFocusable(false);
        jLabel9.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                documentationClicked(evt);
            }
        });

        javax.swing.GroupLayout autonomyPanelLayout = new javax.swing.GroupLayout(autonomyPanel);
        autonomyPanel.setLayout(autonomyPanelLayout);
        autonomyPanelLayout.setHorizontalGroup(
            autonomyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autonomyPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autonomyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 688, Short.MAX_VALUE)
                    .addGroup(autonomyPanelLayout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, autonomyPanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jLabel9)))
                .addContainerGap())
        );
        autonomyPanelLayout.setVerticalGroup(
            autonomyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autonomyPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE)
                .addGap(18, 18, 18))
        );

        locCommandPanels.addTab("Autonomy JSON", autonomyPanel);

        locCommandTab.setMaximumSize(new java.awt.Dimension(718, 5000));

        jScrollPane4.setBackground(new java.awt.Color(238, 238, 238));
        jScrollPane4.setPreferredSize(new java.awt.Dimension(718, 421));

        autoLocPanel.setBackground(new java.awt.Color(238, 238, 238));
        autoLocPanel.setEnabled(false);
        autoLocPanel.setFocusable(false);
        autoLocPanel.setMaximumSize(new java.awt.Dimension(716, 5000));
        autoLocPanel.setLayout(new java.awt.GridLayout(100, 3, 5, 5));
        jScrollPane4.setViewportView(autoLocPanel);

        javax.swing.GroupLayout locCommandTabLayout = new javax.swing.GroupLayout(locCommandTab);
        locCommandTab.setLayout(locCommandTabLayout);
        locCommandTabLayout.setHorizontalGroup(
            locCommandTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        locCommandTabLayout.setVerticalGroup(
            locCommandTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        locCommandPanels.addTab("Locomotive Commands", locCommandTab);

        exportJSON.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        exportJSON.setText("Export State to JSON");
        exportJSON.setEnabled(false);
        exportJSON.setFocusable(false);
        exportJSON.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportJSONActionPerformed(evt);
            }
        });

        gracefulStop.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        gracefulStop.setText("Graceful Stop");
        gracefulStop.setToolTipText("Active locomotives will stop at the next station.");
        gracefulStop.setEnabled(false);
        gracefulStop.setFocusable(false);
        gracefulStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gracefulStopActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout autoPanelLayout = new javax.swing.GroupLayout(autoPanel);
        autoPanel.setLayout(autoPanelLayout);
        autoPanelLayout.setHorizontalGroup(
            autoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autoPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(locCommandPanels)
                    .addGroup(autoPanelLayout.createSequentialGroup()
                        .addComponent(validateButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exportJSON)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(gracefulStop)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(startAutonomy)))
                .addContainerGap())
        );
        autoPanelLayout.setVerticalGroup(
            autoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, autoPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(locCommandPanels)
                .addGap(2, 2, 2)
                .addGroup(autoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(autoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(startAutonomy)
                        .addComponent(gracefulStop))
                    .addGroup(autoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(validateButton)
                        .addComponent(exportJSON)))
                .addContainerGap())
        );

        KeyboardTab.addTab("Autonomy", autoPanel);

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

        buttonGroup1.add(LocTypeDCC);
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
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(LocAddressInput, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(LocTypeMM2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(LocTypeMFX)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(LocTypeDCC)
                                        .addGap(0, 0, Short.MAX_VALUE))
                                    .addComponent(checkDuplicates, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))))
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
        syncLocStateButton.setFocusable(false);
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
                    .addComponent(SyncButton, javax.swing.GroupLayout.DEFAULT_SIZE, 352, Short.MAX_VALUE)
                    .addComponent(TurnOnLightsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(TurnOffFnButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(syncLocStateButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                        .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 384, Short.MAX_VALUE))
                        .addGap(404, 404, Short.MAX_VALUE))
                    .addGroup(ManageLocPanelLayout.createSequentialGroup()
                        .addGroup(ManageLocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(EditExistingLocLabel3)
                            .addComponent(AddNewLocLabel)
                            .addComponent(EditExistingLocLabel1)
                            .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(54, 54, 54))))
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
        jScrollPane3.setViewportView(debugArea);

        javax.swing.GroupLayout logPanelLayout = new javax.swing.GroupLayout(logPanel);
        logPanel.setLayout(logPanelLayout);
        logPanelLayout.setHorizontalGroup(
            logPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(logPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 773, Short.MAX_VALUE)
                .addContainerGap())
        );
        logPanelLayout.setVerticalGroup(
            logPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(logPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 494, Short.MAX_VALUE)
                .addContainerGap())
        );

        KeyboardTab.addTab("Log", logPanel);

        LocFunctionsPanel.setBackground(new java.awt.Color(255, 255, 255));
        LocFunctionsPanel.setToolTipText(null);
        LocFunctionsPanel.setMinimumSize(new java.awt.Dimension(326, 560));
        LocFunctionsPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                LocFunctionsPanelMouseEntered(evt);
            }
        });
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
        SpeedSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                MainSpeedSliderClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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

        locIcon.setFocusable(false);
        locIcon.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        FunctionTabs.setBackground(new java.awt.Color(255, 255, 255));
        FunctionTabs.setTabPlacement(javax.swing.JTabbedPane.BOTTOM);
        FunctionTabs.setFocusable(false);
        FunctionTabs.setMinimumSize(new java.awt.Dimension(290, 78));
        FunctionTabs.setPreferredSize(new java.awt.Dimension(318, 173));

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));
        jPanel1.setPreferredSize(new java.awt.Dimension(313, 123));

        F8.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
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
        F10.setSelected(true);
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
        F0.setToolTipText("Alt-0 / ~");
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
                .addGap(0, 20, Short.MAX_VALUE))
        );

        FunctionTabs.addTab("F20-32", F12Panel);

        javax.swing.GroupLayout LocFunctionsPanelLayout = new javax.swing.GroupLayout(LocFunctionsPanel);
        LocFunctionsPanel.setLayout(LocFunctionsPanelLayout);
        LocFunctionsPanelLayout.setHorizontalGroup(
            LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(CurrentKeyLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ActiveLocLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addComponent(PowerOff, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(OnButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(LocFunctionsPanelLayout.createSequentialGroup()
                        .addGroup(LocFunctionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
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
                .addComponent(FunctionTabs, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE)
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

    public ViewListener getModel()
    {
        return model;
    }
    
    private void LocControlPanelKeyPressed(java.awt.event.KeyEvent evt)//GEN-FIRST:event_LocControlPanelKeyPressed
    {//GEN-HEADEREND:event_LocControlPanelKeyPressed
        int keyCode = evt.getKeyCode();
        boolean altPressed = (evt.getModifiers() & KeyEvent.ALT_MASK) != 0;
        boolean controlPressed = (evt.getModifiers() & KeyEvent.CTRL_MASK) != 0 || (evt.getModifiers() & KeyEvent.CTRL_DOWN_MASK) != 0;
        
        if (altPressed && keyCode == KeyEvent.VK_G)
        {
            go();
        }
        else if (altPressed && keyCode == KeyEvent.VK_P)
        {
            this.applyPreferredFunctions(this.activeLoc);
        }
        else if (altPressed && keyCode == KeyEvent.VK_V)
        {
            this.applyPreferredSpeed(this.activeLoc);
        }
        else if (altPressed && keyCode == KeyEvent.VK_O)
        {
            this.locFunctionsOff(this.activeLoc);
        }
        else if (altPressed && keyCode == KeyEvent.VK_S)
        {
            this.savePreferredFunctions(this.activeLoc);
        }
        else if (altPressed && keyCode == KeyEvent.VK_U)
        {
            this.savePreferredSpeed(this.activeLoc);
        }
        else if (this.buttonMapping.containsKey(keyCode))
        {
            this.displayCurrentButtonLoc(this.buttonMapping.get(evt.getKeyCode()));
        }
        else if (keyCode == KeyEvent.VK_UP)
        {            
            if (altPressed)
            {
                this.UpArrowLetterButtonPressedAlt(null);
            }
            else
            {
                this.UpArrowLetterButtonPressed(null);
            }
        }
        else if (keyCode == KeyEvent.VK_DOWN)
        {            
            if (altPressed)
            {
                this.DownArrowLetterButtonPressedAlt(null);          
            }
            else
            {
               this.DownArrowLetterButtonPressed(null);
            }
        }
        else if (keyCode == KeyEvent.VK_RIGHT && !altPressed)
        {
            this.RightArrowLetterButtonPressed(null);
        }
        else if (keyCode == KeyEvent.VK_LEFT && !altPressed)
        {
            this.LeftArrowLetterButtonPressed(null);
        }
        else if (keyCode == KeyEvent.VK_SPACE)
        {
            this.SpacebarButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_0 && !altPressed && !controlPressed)
        {
            this.ZeroButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_1 && !altPressed && !controlPressed)
        {
            this.OneButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_2 && !altPressed && !controlPressed)
        {
            this.TwoButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_3 && !altPressed && !controlPressed)
        {
            this.ThreeButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_4 && !altPressed && !controlPressed)
        {
            this.FourButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_5 && !altPressed && !controlPressed)
        {
            this.FiveButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_6 && !altPressed && !controlPressed)
        {
            this.SixButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_7 && !altPressed && !controlPressed)
        {
            this.SevenButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_8 && !altPressed && !controlPressed)
        {
            this.EightButtonActionPerformed(null);
        }
        else if (keyCode == KeyEvent.VK_9 && !altPressed && !controlPressed)
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
        else if (keyCode == KeyEvent.VK_COMMA || (keyCode == KeyEvent.VK_LEFT && altPressed))
        {
            //if (this.KeyboardTab.getSelectedIndex() == 0)
            //{
            this.PrevLocMappingActionPerformed(null);
            //}    
        }
        else if (keyCode == KeyEvent.VK_PERIOD || (keyCode == KeyEvent.VK_RIGHT && altPressed))
        {
            //if (this.KeyboardTab.getSelectedIndex() == 0)
            // {
            this.NextLocMappingActionPerformed(null);
            // }
        }
        else if (keyCode == KeyEvent.VK_NUMPAD0 || keyCode == KeyEvent.VK_BACK_QUOTE || (keyCode == KeyEvent.VK_0 && altPressed && !controlPressed))
        {
            this.switchF(0);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD1 || keyCode == KeyEvent.VK_F1 || (keyCode == KeyEvent.VK_1 && altPressed && !controlPressed))
        {
            this.switchF(1);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD2 || keyCode == KeyEvent.VK_F2 || (keyCode == KeyEvent.VK_2 && altPressed && !controlPressed))
        {
            this.switchF(2);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD3 || keyCode == KeyEvent.VK_F3 || (keyCode == KeyEvent.VK_3 && altPressed && !controlPressed))
        {
            this.switchF(3);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD4 || keyCode == KeyEvent.VK_F4 || (keyCode == KeyEvent.VK_4 && altPressed && !controlPressed))
        {
            this.switchF(4);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD5 || keyCode == KeyEvent.VK_F5 || (keyCode == KeyEvent.VK_5 && altPressed && !controlPressed))
        {
            this.switchF(5);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD6 || keyCode == KeyEvent.VK_F6 || (keyCode == KeyEvent.VK_6 && altPressed && !controlPressed))
        {
            this.switchF(6);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD7 || keyCode == KeyEvent.VK_F7 || (keyCode == KeyEvent.VK_7 && altPressed && !controlPressed))
        {
            this.switchF(7);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD8 || keyCode == KeyEvent.VK_F8 || (keyCode == KeyEvent.VK_8 && altPressed && !controlPressed))
        {
            this.switchF(8);
        }
        else if (keyCode == KeyEvent.VK_NUMPAD9 || keyCode == KeyEvent.VK_F9 || (keyCode == KeyEvent.VK_9 && altPressed && !controlPressed))
        {
            this.switchF(9);
        }
        else if (keyCode == KeyEvent.VK_F10 || (keyCode == KeyEvent.VK_0 && !altPressed && controlPressed))
        {
            this.switchF(10);
        }
        else if (keyCode == KeyEvent.VK_F11 || (keyCode == KeyEvent.VK_1 && !altPressed && controlPressed))
        {
            this.switchF(11);
        }
        else if (keyCode == KeyEvent.VK_F12 || (keyCode == KeyEvent.VK_2 && !altPressed && controlPressed))
        {
            this.switchF(12);
        }
        else if (keyCode == KeyEvent.VK_F13 || (keyCode == KeyEvent.VK_3 && !altPressed && controlPressed))
        {
            this.switchF(13);
        }
        else if (keyCode == KeyEvent.VK_F14 || (keyCode == KeyEvent.VK_4 && !altPressed && controlPressed))
        {
            this.switchF(14);
        }
        else if (keyCode == KeyEvent.VK_F15 || (keyCode == KeyEvent.VK_5 && !altPressed && controlPressed))
        {
            this.switchF(15);
        }
        else if (keyCode == KeyEvent.VK_F16 || (keyCode == KeyEvent.VK_6 && !altPressed && controlPressed))
        {
            this.switchF(16);
        }
        else if (keyCode == KeyEvent.VK_F17 || (keyCode == KeyEvent.VK_7 && !altPressed && controlPressed))
        {
            this.switchF(17);
        }
        else if (keyCode == KeyEvent.VK_F18 || (keyCode == KeyEvent.VK_8 && !altPressed && controlPressed))
        {
            this.switchF(18);
        }
        else if (keyCode == KeyEvent.VK_F19 || (keyCode == KeyEvent.VK_9 && !altPressed && controlPressed))
        {
            this.switchF(19);
        }
        else if (keyCode == KeyEvent.VK_F20 || (keyCode == KeyEvent.VK_0 && altPressed && controlPressed))
        {
            this.switchF(20);
        }
        else if (keyCode == KeyEvent.VK_F21 || (keyCode == KeyEvent.VK_1 && altPressed && controlPressed))
        {
            this.switchF(21);
        }
        else if (keyCode == KeyEvent.VK_F22 || (keyCode == KeyEvent.VK_2 && altPressed && controlPressed))
        {
            this.switchF(22);
        }
        else if (keyCode == KeyEvent.VK_F23 || (keyCode == KeyEvent.VK_3 && altPressed && controlPressed))
        {
            this.switchF(23);
        }
        else if (keyCode == KeyEvent.VK_F24 || (keyCode == KeyEvent.VK_4 && altPressed && controlPressed))
        {
            this.switchF(24);
        }
        else if (keyCode == KeyEvent.VK_5 && altPressed && controlPressed)
        {
            this.switchF(25);
        }
        else if (keyCode == KeyEvent.VK_6 && altPressed && controlPressed)
        {
            this.switchF(26);
        }
        else if (keyCode == KeyEvent.VK_7 && altPressed && controlPressed)
        {
            this.switchF(27);
        }
        else if (keyCode == KeyEvent.VK_8 && altPressed && controlPressed)
        {
            this.switchF(28);
        }
        else if (keyCode == KeyEvent.VK_9 && altPressed && controlPressed)
        {
            this.switchF(29);
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
        else if (keyCode == KeyEvent.VK_BACK_SPACE && !altPressed)
        {
            // Easy tab cycling
            this.KeyboardTab.setSelectedIndex(
                (this.KeyboardTab.getSelectedIndex() + 1) 
                    % this.KeyboardTab.getComponentCount()
            );
        } 
        else if (keyCode == KeyEvent.VK_BACK_SPACE && altPressed)
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
        else if (keyCode == KeyEvent.VK_SLASH)
        {
            // Cycle function tabs
            this.FunctionTabs.setSelectedIndex(
                (this.FunctionTabs.getSelectedIndex() + 1) 
                    % this.FunctionTabs.getComponentCount()
            );
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

    public void deleteRoute(MouseEvent evt)
    {
        Object route = getRouteAtCursor(evt);

        if (route != null)
        {            
            int dialogResult = JOptionPane.showConfirmDialog(RoutePanel, "Delete route " + route.toString() + "?", "Route Deletion", JOptionPane.YES_NO_OPTION);
            if(dialogResult == JOptionPane.YES_OPTION)
            {
                this.model.deleteRoute(route.toString());
                refreshRouteList();

                // Ensure route changes are synced
                this.model.syncWithCS2();
                this.repaintLayout();
            }
        }
    }
    
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
        int switchId = Integer.parseInt(b.getText());

        new Thread(() -> {
            this.model.setAccessoryState(switchId, b.isSelected());
        }).start();
    }//GEN-LAST:event_UpdateSwitchState

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

    public int getRouteId (Object route)
    {
        return this.model.getRouteId(route.toString());
    }
    
    public void executeRoute(String route)
    {
        new Thread(() -> {
            this.model.execRoute(route);
            refreshRouteList();
        }).start();
    }
    
    public String getRouteTooltip(String route)
    {
        MarklinRoute currentRoute = this.model.getRoute(route);
        return currentRoute.getName() + " (ID: " + getRouteId(route) + " | " + (currentRoute.isEnabled() ? "Auto" : "Manual") + ")";
    }
    
    private void RouteListMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_RouteListMouseClicked
    {//GEN-HEADEREND:event_RouteListMouseClicked
        if (SwingUtilities.isLeftMouseButton(evt))
        {
            //Object route = this.RouteList.getValueAt(this.RouteList.getSelectedRow(), this.RouteList.getSelectedColumn());
            Object route = this.getRouteAtCursor(evt);

            if (route != null)
            {
                int dialogResult = JOptionPane.showConfirmDialog(RoutePanel, "Execute route " + route.toString() + "? (ID: " + getRouteId(route) + ")", "Route Execution", JOptionPane.YES_NO_OPTION);
                if(dialogResult == JOptionPane.YES_OPTION)
                {
                    executeRoute(route.toString());
                }
            }   
        }
    }//GEN-LAST:event_RouteListMouseClicked

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

    private void MainSpeedSliderClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_MainSpeedSliderClicked
        
        if (evt.getClickCount() == 2 && SwingUtilities.isRightMouseButton(evt))
        {
            this.switchDirection();
        }
        else
        {
            setLocSpeed(SpeedSlider.getValue());
        }
    }//GEN-LAST:event_MainSpeedSliderClicked

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
    
    private void syncLocStateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncLocStateButtonActionPerformed
        
        int dialogResult = JOptionPane.showConfirmDialog(this, "This function will query the Central Station for the current function status and direction of all locomotives, and may take several minutes. Continue?", "Sync State", JOptionPane.YES_NO_OPTION);
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

    private void FiveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_FiveButtonActionPerformed
        setLocSpeed(44);
    }//GEN-LAST:event_FiveButtonActionPerformed

    private void SixButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SixButtonActionPerformed
        setLocSpeed(55);
    }//GEN-LAST:event_SixButtonActionPerformed

    private void OneButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OneButtonActionPerformed
        setLocSpeed(0);
    }//GEN-LAST:event_OneButtonActionPerformed

    private void TwoButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TwoButtonActionPerformed
        setLocSpeed(11);
    }//GEN-LAST:event_TwoButtonActionPerformed

    private void ThreeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ThreeButtonActionPerformed
        setLocSpeed(22);
    }//GEN-LAST:event_ThreeButtonActionPerformed

    private void FourButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_FourButtonActionPerformed
        setLocSpeed(33);
    }//GEN-LAST:event_FourButtonActionPerformed

    private void SevenButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SevenButtonActionPerformed
        setLocSpeed(66);
    }//GEN-LAST:event_SevenButtonActionPerformed

    private void NineButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_NineButtonActionPerformed
        setLocSpeed(88);
    }//GEN-LAST:event_NineButtonActionPerformed

    private void EightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EightButtonActionPerformed
        setLocSpeed(77);
    }//GEN-LAST:event_EightButtonActionPerformed

    private void ZeroButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ZeroButtonActionPerformed
        setLocSpeed(100);
    }//GEN-LAST:event_ZeroButtonActionPerformed

    private void AltEmergencyStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AltEmergencyStopActionPerformed
        new Thread(() ->
        {
            this.model.stopAllLocs();
        }).start();
    }//GEN-LAST:event_AltEmergencyStopActionPerformed

    private void ShiftButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ShiftButtonActionPerformed
        setLocSpeed(0);
    }//GEN-LAST:event_ShiftButtonActionPerformed

    private void SpacebarButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SpacebarButtonActionPerformed
        stopLoc();
    }//GEN-LAST:event_SpacebarButtonActionPerformed

    private void LeftArrowLetterButtonPressed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LeftArrowLetterButtonPressed
        switchDirection();
    }//GEN-LAST:event_LeftArrowLetterButtonPressed

    private void RightArrowLetterButtonPressed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RightArrowLetterButtonPressed
        switchDirection();
    }//GEN-LAST:event_RightArrowLetterButtonPressed

    private void DownArrowLetterButtonPressedAlt(java.awt.event.ActionEvent evt) {                                              
        
        if (this.activeLoc != null)
        {
            setLocSpeed(Math.max(this.activeLoc.getSpeed() - SPEED_STEP * 2, 0));
        }
    } 
    
    private void UpArrowLetterButtonPressedAlt(java.awt.event.ActionEvent evt) {                                            
        if (this.activeLoc != null)
        {
            setLocSpeed(Math.min(this.activeLoc.getSpeed() + SPEED_STEP * 2, 100));
        }
    }
    
    private void DownArrowLetterButtonPressed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DownArrowLetterButtonPressed
        if (this.activeLoc != null)
        {
            setLocSpeed(Math.max(this.activeLoc.getSpeed() - SPEED_STEP, 0));
        }
    }//GEN-LAST:event_DownArrowLetterButtonPressed

    private void UpArrowLetterButtonPressed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_UpArrowLetterButtonPressed
        if (this.activeLoc != null)
        {
            setLocSpeed(Math.min(this.activeLoc.getSpeed() + SPEED_STEP, 100));
        }
    }//GEN-LAST:event_UpArrowLetterButtonPressed

    private void sliderClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sliderClicked

        sliderClickedSynced(evt);
    }//GEN-LAST:event_sliderClicked

    private synchronized void sliderClickedSynced(java.awt.event.MouseEvent evt)
    {
        if (evt.getClickCount() == 2 && SwingUtilities.isRightMouseButton(evt))
        {
            JSlider slider = (JSlider) evt.getSource();

            new Thread(() ->
            {
                JButton b = this.rSliderMapping.get(slider);
                
                Locomotive l = this.currentLocMapping().get(b);

                if (l != null)
                {                    
                    // System.out.println(l.getName() + " switch dir");
                    l.setSpeed(0);
                    l.switchDirection();
                    
                    // Change active loc if setting selected
                    if (this.prefs.getBoolean(SLIDER_SETTING_PREF, false))
                    {
                        this.displayCurrentButtonLoc(b);
                    }
                }

            }).start();
        }
        else
        {
            updateSliderSpeed(evt);
        }
    }
        
    private void updateSliderSpeed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_updateSliderSpeed
        JSlider slider = (JSlider) evt.getSource();
        
        new Thread(() ->
        {
            JButton b = this.rSliderMapping.get(slider);
            
            Locomotive l = this.currentLocMapping().get(b);

            if (l != null)
            {
                // System.out.println(l.getName() + " setting speed " + slider.getValue());
               
                if (l.getSpeed() != slider.getValue())
                {
                    l.setSpeed(slider.getValue());
                }
                
                // Change active loc if setting selected
                if (this.prefs.getBoolean(SLIDER_SETTING_PREF, false))
                {
                    this.displayCurrentButtonLoc(b);
                }
            }
        }).start();  
    }//GEN-LAST:event_updateSliderSpeed

    private void NextLocMappingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_NextLocMappingActionPerformed
        this.switchLocMapping(this.locMappingNumber + 1);
    }//GEN-LAST:event_NextLocMappingActionPerformed

    private void PrevLocMappingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PrevLocMappingActionPerformed
        this.switchLocMapping(this.locMappingNumber - 1);
    }//GEN-LAST:event_PrevLocMappingActionPerformed

    private void LetterButtonPressed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LetterButtonPressed
        this.displayCurrentButtonLoc((javax.swing.JButton) evt.getSource());

        // Show selector if no locomotive is assigned
        if (this.activeLoc == null)
        {
            showLocSelector();
        }
    }//GEN-LAST:event_LetterButtonPressed

    private void SpeedSliderDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_SpeedSliderDragged
       setLocSpeed(SpeedSlider.getValue());
    }//GEN-LAST:event_SpeedSliderDragged

    private void sliderSettingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sliderSettingActionPerformed
        this.prefs.putBoolean(SLIDER_SETTING_PREF, this.sliderSetting.isSelected());
    }//GEN-LAST:event_sliderSettingActionPerformed

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
        this.selector.refreshLocSelectorList();

        // Rest form
        JOptionPane.showMessageDialog(this,
            "Locomotive added successfully");

        this.LocAddressInput.setText("");
        this.LocNameInput.setText("");
    }//GEN-LAST:event_AddLocButtonActionPerformed

    /**
     * Callback to edit or add a route
     * @param origName
     * @param routeName
     * @param routeContent
     * @param s88
     * @param isEnabled
     * @param triggerType
     * @param conditionS88s
     * @return 
     */
    public boolean RouteCallback(String origName, String routeName, String routeContent, String s88, boolean isEnabled, MarklinRoute.s88Triggers triggerType,
            String conditionS88s)
    {
        if (routeName == null)
        {
            return false;
        }
        
        // Remove trailing spaces in route names
        routeName = routeName.trim();

        if ("".equals(routeName)  || "".equals(routeContent))
        {
            return false;
        }
                      
        // Add route
        try
        {
            List<RouteCommand> newRoute = new LinkedList<>();

            for (String line : routeContent.split("\n"))
            {
                if (line.trim().length() > 0)
                {
                    int address = Math.abs(Integer.parseInt(line.split(",")[0].trim()));
                    boolean state = line.split(",")[1].trim().equals("1");
                    
                    RouteCommand rc = RouteCommand.RouteCommandAccessory(address, state);
                    
                    if (line.split(",").length > 2)
                    {
                        rc.setDelay(Math.abs(Integer.parseInt(line.split(",")[2].trim())));     
                    }

                    newRoute.add(rc);
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
            
            if (newRoute.size() > 0)
            {    
                // Editing a route
                if (!"".equals(origName))
                {
                    this.model.editRoute(origName, routeName, newRoute,
                                 Math.abs(Integer.parseInt(s88)), triggerType, isEnabled, newConditions);
                                 // TODO read delays from UI
                }
                // New route
                else
                {
                    if (this.model.getRouteList().contains(routeName))
                    {
                        JOptionPane.showMessageDialog(this, "A route called " + routeName + " already exists.  Please pick a different name.");
                        return false;
                    }
                                        
                    this.model.newRoute(routeName, newRoute,
                             Math.abs(Integer.parseInt(s88)), triggerType, isEnabled, newConditions);
                             // TODO read delays from UI
                }
                
                refreshRouteList();

                // Ensure route changes are synced
                this.model.syncWithCS2();
                this.repaintLayout();
            }
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(this, "Error parsing route.  Be sure to enter comma-separated numbers only, one pair per line.\n\nTrigger S88 must be an integer and Condition S88s must be comma-separated.");
        }
        
        return true;
    }
    
    private void AddRouteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddRouteButtonActionPerformed
        
        String proposedName = "Route %s";
        int i = 1;
                
        while (this.model.getRoute(String.format(proposedName, i)) != null)
        {
            i++;
        }
                
        RouteEditor edit = new RouteEditor(String.format(proposedName, i), "", false, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, "");

        int dialogResult = JOptionPane.showConfirmDialog(this, edit, "Add New Route", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if(dialogResult == JOptionPane.OK_OPTION)
        {
            RouteCallback("", edit.getRouteName().getText(), edit.getRouteContents().getText(), edit.getS88().getText(),
                  edit.getExecutionAuto().isSelected(),
                edit.getTriggerClearThenOccupied().isSelected() ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
                edit.getConditionS88s().getText()
            );
        }
    }//GEN-LAST:event_AddRouteButtonActionPerformed
 
    public void editRoute(MouseEvent evt)
    {
        Object route = getRouteAtCursor(evt);

        if (route != null)
        {          
            MarklinRoute currentRoute = this.model.getRoute(route.toString());
            
            RouteEditor edit = new RouteEditor(route.toString(), currentRoute.toCSV(), currentRoute.isEnabled(), currentRoute.getS88(), currentRoute.getTriggerType(),
                currentRoute.getConditionS88String());
            int dialogResult = JOptionPane.showConfirmDialog(this, edit, "Edit Route: " + route.toString() + " (ID: " + currentRoute.getId() + ")",  JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if(dialogResult == JOptionPane.OK_OPTION)
            {
                RouteCallback(route.toString(), edit.getRouteName().getText(), edit.getRouteContents().getText(), edit.getS88().getText(),
                    edit.getExecutionAuto().isSelected(),
                    edit.getTriggerClearThenOccupied().isSelected() ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR,
                    edit.getConditionS88s().getText()
                );
            }
        }
    }
    
    public Object getRouteAtCursor(MouseEvent evt)
    {
        try
        {
            return this.RouteList.getValueAt(this.RouteList.rowAtPoint(evt.getPoint()), this.RouteList.columnAtPoint(evt.getPoint()));
        }
        catch (Exception e)
        {
            return null;
        }
        
    }
    
    public void duplicateRoute(MouseEvent evt)
    {
        Object route = getRouteAtCursor(evt);

        if (route != null)
        {
            MarklinRoute currentRoute = this.model.getRoute(route.toString());
            
            if (currentRoute != null)
            {
                String proposedName = currentRoute.getName() + " (Copy %s)";
                
                int i = 1;
                
                while (this.model.getRoute(String.format(proposedName, i)) != null)
                {
                    i++;
                }
                
                this.model.newRoute(String.format(proposedName, i), currentRoute.getRoute(), 
                        currentRoute.getS88(), currentRoute.getTriggerType(), false, currentRoute.getConditionS88s()); 

                refreshRouteList();

                // Ensure route changes are synced
                this.model.syncWithCS2();
                this.repaintLayout();
            }
        }  
    }
        
    private void sortByNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortByNameActionPerformed
        this.prefs.putBoolean(ROUTE_SORT_PREF, true);
        this.refreshRouteList();
    }//GEN-LAST:event_sortByNameActionPerformed

    private void sortByIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortByIDActionPerformed
        this.prefs.putBoolean(ROUTE_SORT_PREF, false);
        this.refreshRouteList();
    }//GEN-LAST:event_sortByIDActionPerformed

    private void BulkEnableOrDisable(boolean enable)
    {
        String searchString = JOptionPane.showInputDialog(this, "Enter search string; matching routes with S88 will be " + (enable ? "enabled" : "disabled") +". * matches all.", "*");
        
        if (!"".equals(searchString))
        {
            for (String routeName : this.model.getRouteList())
            {
                MarklinRoute r = this.model.getRoute(routeName);
                
                if (r.hasS88() || r.isEnabled())
                {
                    if (r.getName().contains(searchString) || "*".equals(searchString))
                    {
                         this.model.editRoute(r.getName(), r.getName(), r.getRoute(),
                                    r.getS88(), r.getTriggerType(), enable, r.getConditionS88s());
                    }
                }
            }
            
            refreshRouteList();

            // Ensure route changes are synced
            this.model.syncWithCS2();
            this.repaintLayout();
        }
    }
    
    public void enableOrDisableRoute(String routeName, boolean enable)
    {  
        new Thread(() -> 
        {
            MarklinRoute r = this.model.getRoute(routeName);

            if (r.hasS88())
            {
                this.model.editRoute(r.getName(), r.getName(), r.getRoute(), r.getS88(), r.getTriggerType(), enable, r.getConditionS88s());

                refreshRouteList();

                // Ensure route changes are synced
                this.model.syncWithCS2();
                this.repaintLayout();
            }
            else
            {
                JOptionPane.showMessageDialog(this, "Route must have an S88 configured to fire automatically.");
            }
        }).start();
    }
    
    private void BulkEnableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BulkEnableActionPerformed
 
        BulkEnableOrDisable(true);
    }//GEN-LAST:event_BulkEnableActionPerformed

    private void BulkDisableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BulkDisableActionPerformed
        BulkEnableOrDisable(false);
    }//GEN-LAST:event_BulkDisableActionPerformed

    private void checkDuplicatesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkDuplicatesActionPerformed
        
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
        
        Map<Integer, Set<MarklinLocomotive>> locs = this.model.getDuplicateLocAddresses();
        String message;
        
        if (locs.containsKey(locAddress))
        {   
            message = "Locomotive address is already in use.  See log for details.";
        }
        else
        {
            message = "Address is not in use.  See log for details.";
        }
        
        if (locs.size() > 0)
        {
            List<Integer> sortedLocs = new ArrayList(locs.keySet());
            Collections.sort(sortedLocs, Collections.reverseOrder());
            
            for (Integer addr : sortedLocs)
            {
                for (MarklinLocomotive l : locs.get(addr))
                {
                    this.log("\t" + l.getName() + " [" + l.getDecoderTypeLabel() + "]");
                }
                
                this.log("---- Address " + addr + " ----");
            }   

            this.log("Duplicate locomotive address report:");
        }
        else
        {
            this.log("There are no duplicate locomotive addresses in the database.");
        }
        
        JOptionPane.showMessageDialog(this, message);
    }//GEN-LAST:event_checkDuplicatesActionPerformed

    private void startAutonomyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startAutonomyActionPerformed

        for (String routeName : this.model.getRouteList())
        {
            MarklinRoute r = this.model.getRoute(routeName);

            if (r.isEnabled())
            {
                this.model.log(r.toString());
                JOptionPane.showMessageDialog(this, "Please first disable all automatic routes.");
                return;
            }
        }
        
        if (this.model.getAutoLayout().getLocomotivesToRun().isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Please add some locomotives to the graph.");
            return;
        }
        
        if (this.model.getAutoLayout().isValid() && !this.model.getAutoLayout().isRunning())
        {
            new Thread( () -> {
                this.model.getAutoLayout().runLocomotives();
            }).start();

            this.startAutonomy.setEnabled(false);
            this.gracefulStop.setEnabled(true);
        }
        else if (this.model.getAutoLayout().isRunning())
        {
            JOptionPane.showMessageDialog(this, "Please wait for active locomotives to stop.");
        }
    }//GEN-LAST:event_startAutonomyActionPerformed

    private void validateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_validateButtonActionPerformed

        new Thread( () -> {
            
            this.model.parseAuto(this.autonomyJSON.getText());

            if (null == this.model.getAutoLayout() || !this.model.getAutoLayout().isValid())
            {
                locCommandPanels.remove(this.locCommandTab);

                this.startAutonomy.setEnabled(false);
                JOptionPane.showMessageDialog(this, "Validation failed.  Check log for details.");

                this.KeyboardTab.requestFocus();
                
                this.exportJSON.setEnabled(false);      
            }
            else
            {
                locCommandPanels.addTab("Locomotive Commands", this.locCommandTab);

                this.startAutonomy.setEnabled(true);

                // Advance to locomotive tab
                this.locCommandPanels.setSelectedIndex(
                    1
                    //(this.locCommandPanels.getSelectedIndex() + 1)
                    //% this.locCommandPanels.getComponentCount()
                );

                this.KeyboardTab.requestFocus();
                
                this.renderAutoLayoutGraph();
                
                this.graphViewer.requestFocus();
                
                this.exportJSON.setEnabled(true);
                this.gracefulStop.setEnabled(false);
            }

            // Stop all locomotives
            AltEmergencyStopActionPerformed(null);
            
        }).start();
    }//GEN-LAST:event_validateButtonActionPerformed

    private void documentationClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_documentationClicked
        try
        {
            Desktop.getDesktop().browse(new URI("https://github.com/bob123456678/TrainControl/blob/master/src/examples/Readme.md"));
        }
        catch (IOException | URISyntaxException e1) {
        }
    }//GEN-LAST:event_documentationClicked

    private void autonomyJSONKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_autonomyJSONKeyReleased

    }//GEN-LAST:event_autonomyJSONKeyReleased

    private void locCommandPanelsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locCommandPanelsMouseClicked
        this.KeyboardTab.requestFocus();
    }//GEN-LAST:event_locCommandPanelsMouseClicked

    private void LocFunctionsPanelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_LocFunctionsPanelMouseEntered
        this.KeyboardTab.requestFocus();
    }//GEN-LAST:event_LocFunctionsPanelMouseEntered

    private void exportJSONActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportJSONActionPerformed
        
        JTextArea textArea = new JTextArea();
        textArea.setColumns(50);
        textArea.setRows(30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setSize(textArea.getPreferredSize().width, textArea.getPreferredSize().height);
        
        try
        {
            textArea.setText(this.getModel().getAutoLayout().toJSON());
            JOptionPane.showMessageDialog(this, new JScrollPane(textArea), "JSON for current state", JOptionPane.PLAIN_MESSAGE);     
        }
        catch (Exception e)
        {
            if (this.getModel().isDebug())
            {
                e.printStackTrace();
            }
            
            this.log("JSON error: " + e.getMessage());
            
            JOptionPane.showMessageDialog(this, "Failed to geenrate JSON.  Check log for details.");
        }
    }//GEN-LAST:event_exportJSONActionPerformed

    private void gracefulStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gracefulStopActionPerformed
        this.getModel().getAutoLayout().stopLocomotives();
        this.gracefulStop.setEnabled(false);
        this.startAutonomy.setEnabled(true);
    }//GEN-LAST:event_gracefulStopActionPerformed

    /**
     * Disables the start autonomy button
     */
    public void greyOutAutonomy()
    {
        this.startAutonomy.setEnabled(false);
        AltEmergencyStopActionPerformed(null);
    }
    
    /**
     * Renders a graph visualization of the automated layout
     */
    synchronized private void renderAutoLayoutGraph()
    {
        if (this.graphViewer != null)
        {
            this.graphViewer.dispose();
        }
        
        // Do we set coordinates manually?
        boolean setPoints = true;
        for (Point p : this.model.getAutoLayout().getPoints())
        {
            if (!p.coordinatesSet())
            {
                this.model.log(p.getName() + " has no coordinate info - enabling auto graph layout.");
                setPoints = false;
                break;
            }
        }
        
        Graph graph = new SingleGraph("Layout Graph"); 
        graphViewer = new GraphViewer(graph, this, !setPoints);

        // Custom stylsheet
        URL resource = TrainControlUI.class.getResource("resources/graph.css");

        int maxY = 0;
        
        for (Point p : this.model.getAutoLayout().getPoints())
        {
            if (p.coordinatesSet() && p.getY() > maxY)
            {
                maxY = p.getY();
            }
        }
        
        try
        {
            graph.setAttribute("ui.stylesheet", "url('" + resource.toURI() +"')");
                                    
            for (Point p : this.model.getAutoLayout().getPoints())
            {
                graph.addNode(p.getUniqueId());
                
                graph.getNode(p.getUniqueId()).setAttribute("weight", 3);
                
                // Set manual coordinates
                if (setPoints)
                {
                    graph.getNode(p.getUniqueId()).setAttribute("x", p.getX());
                    graph.getNode(p.getUniqueId()).setAttribute("y", p.getY());
                }
                
                if (p.isOccupied() && p.getCurrentLocomotive() != null)
                {
                    graph.getNode(p.getUniqueId()).setAttribute("ui.label", p.getName() + "  [" + p.getCurrentLocomotive().getName() + "]");
                    graph.getNode(p.getUniqueId()).setAttribute("ui.class", "occupied");
                }
                else
                {
                    graph.getNode(p.getUniqueId()).setAttribute("ui.label", p.getName());
                    graph.getNode(p.getUniqueId()).setAttribute("ui.class", "unoccupied");
                }
                
                // Different styles for stations and non-stations
                if (p.isDestination())
                {
                    if (p.isTerminus())
                    {
                        graph.getNode(p.getUniqueId()).setAttribute("ui.style", "shape: box; size: 20px;");
                    }
                    else
                    {
                        graph.getNode(p.getUniqueId()).setAttribute("ui.style", "shape: circle; size: 20px;");
                    }
                }
                else
                {
                    graph.getNode(p.getUniqueId()).setAttribute("ui.style", "shape: diamond; size: 17px;");
                }
            }

            for (Edge e : this.model.getAutoLayout().getEdges())
            {
                graph.addEdge(e.getUniqueId(), graph.getNode(e.getStart().getUniqueId()), graph.getNode(e.getEnd().getUniqueId()), true);
                //graph.getEdge(e.getUniqueId()).setAttribute("ui.label", e.getStart().getCurrentLocomotive() != null ?  e.getStart().getCurrentLocomotive().getName() : "" );
                //graph.getEdge(e.getUniqueId()).setAttribute("ui.style", e.getStart().getCurrentLocomotive() != null ? "fill-color: rgb(255,165,0);" : "fill-color: rgb(0,0,0);" );    
                graph.getEdge(e.getUniqueId()).setAttribute("ui.class", "inactive");
            }
            
            // Callback fires at the beginning and end of each path
            this.model.getAutoLayout().setCallback("GraphCallback", (List<Edge> edges, Locomotive l, Boolean locked) -> {
                
                synchronized(graph)
                {  
                    // Update locomotive panel
                    for (Object o : this.autoLocPanel.getComponents())
                    {
                        AutoLocomotiveStatus status = (AutoLocomotiveStatus) o;
                        status.updateState(null);
                    }
                
                    for (Edge e : edges)
                    {                        
                        for (Edge e2 : e.getLockEdges())
                        {
                            // Grey out locked-lock edges
                            if (locked)
                            {
                                graph.getEdge(e2.getUniqueId()).setAttribute("ui.class",  "locked" );
                            }
                            // Reset unlocked lock edges
                            else
                            {
                                graph.getEdge(e2.getUniqueId()).setAttribute("ui.class",  "inactive" );
                            }
                        }
                    }
                    
                    List<Point> milestones = null;
                    
                    if (l != null)
                    {
                        milestones = this.model.getAutoLayout().getReachedMilestones(l.getName());
                    }
                    
                    // Update edge colors and labels
                    for (Edge e : edges)
                    {
                        // Make active edges red
                        graph.getEdge(e.getUniqueId()).setAttribute("ui.class", locked ? "active" : "inactive" );
                        // graph.getEdge(e.getUniqueId()).setAttribute("ui.label", locked ? l.getName() : "" );

                        // Update point labels
                        for (Point p : Arrays.asList(e.getStart(), e.getEnd()))    
                        {
                            if (p.isOccupied() && p.getCurrentLocomotive() != null)
                            {
                                graph.getNode(p.getUniqueId()).setAttribute("ui.label", p.getName() + "  [" + p.getCurrentLocomotive().getName() + "]");
                                graph.getNode(p.getUniqueId()).setAttribute("ui.class", "occupied");
                            }
                            else
                            {
                                graph.getNode(p.getUniqueId()).setAttribute("ui.label", p.getName());
                                graph.getNode(p.getUniqueId()).setAttribute("ui.class", "unoccupied");
                            }
                                                        
                            // Point reached and route is active
                            if (locked)
                            {
                                if (milestones != null && milestones.contains(p))
                                {
                                    graph.getNode(p.getUniqueId()).setAttribute("ui.class", "completed");
                                }
                                else
                                {
                                    graph.getNode(p.getUniqueId()).setAttribute("ui.class", "active");
                                }
                            }
                            
                            // Different styles for stations and non-stations
                            if (p.isDestination())
                            {
                                if (p.isTerminus())
                                {
                                    graph.getNode(p.getUniqueId()).setAttribute("ui.style", "shape: box; size: 20px;");
                                }
                                else
                                {
                                    graph.getNode(p.getUniqueId()).setAttribute("ui.style", "shape: circle; size: 20px;");
                                }
                            }
                            else
                            {
                                graph.getNode(p.getUniqueId()).setAttribute("ui.style", "shape: diamond; size: 17px;");
                            }
                        }    
                    }
                    
                    // Mark completed edges green
                    if (milestones != null && locked)
                    {
                        for (int i = 1; i < milestones.size(); i++)
                        {
                            graph.getEdge(Edge.getEdgeUniqueId(milestones.get(i - 1), milestones.get(i)))
                                .setAttribute("ui.class", "completed" );
                        }
                    }
                    
                    // Highlight start and destination if path is active
                    if (milestones != null && locked && !edges.isEmpty())
                    {
                        if (!milestones.contains(edges.get(edges.size() - 1).getEnd()))
                        {
                            graph.getNode(edges.get(edges.size() - 1).getEnd().getUniqueId()).setAttribute("ui.class", "end");
                        }
                        
                        if (!milestones.contains(edges.get(0).getStart()))
                        {
                            graph.getNode(edges.get(0).getStart().getUniqueId()).setAttribute("ui.class", "start");
                        }
                    }
                             
                    // Update button visibility
                    if (!this.model.getAutoLayout().isRunning())
                    {
                        this.exportJSON.setEnabled(true);
                        this.gracefulStop.setEnabled(false);
                    }
                    else
                    {
                        this.exportJSON.setEnabled(false);
                        // this.gracefulStop.setEnabled(true);
                    }
                }

                return null;                
            });
            
            this.repaintAutoLocList();
        } 
        catch (URISyntaxException ex)
        {
            this.model.log("Error loading graph UI.");
        }        
    }
    
    /**
     * Repaints the auto locomotive list based on auto layout state
     */
    public void repaintAutoLocList()
    {
        // Display locomotive status and possible paths
        this.autoLocPanel.removeAll();

        // Number of columns in the grid
        int gridCols = 3;

        autoLocPanel.setLayout(new java.awt.GridLayout(
                (int) Math.ceil((double) this.model.getAutoLayout().getLocomotivesToRun().size() / gridCols), 
                gridCols, // cols
                5, // padding
                5)
        );

        for (String loc : this.model.getAutoLayout().getLocomotivesToRun())
        {
            this.autoLocPanel.add(new AutoLocomotiveStatus(this.model.getLocByName(loc), this.model.getAutoLayout()));
        }

        // Sometimes the list doesn't repaint until you click on it.  Alernative might be to do this before rendering the graph.
        this.autoLocPanel.repaint(1000);
        this.locCommandPanels.repaint(1000);
    }
         
    public class CustomTableRenderer extends DefaultTableCellRenderer
    {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected,
                    hasFocus, row, column);

            if (value != null)
            {
                String name = (String) value;
                
                if (model.getRoute(name).isEnabled() && model.getRoute(name).hasS88())
                {
                    // set to red bold font
                    c.setForeground(Color.RED);
                    c.setFont(new Font("Dialog", Font.BOLD, 12));
                } 
                else 
                {
                    // stay at default
                    c.setForeground(Color.BLACK);
                    c.setFont(new Font("Dialog", Font.PLAIN, 12));
                }
            }
  
            return c;
        }
    }
    
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
        
        if (this.prefs.getBoolean(ROUTE_SORT_PREF, false))
        {
            Collections.sort(names);
        }
 
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
        this.RouteList.setShowGrid(true);     
               
        this.RouteList.getColumnModel().getColumn(0).setCellRenderer(new CustomTableRenderer());
        this.RouteList.getColumnModel().getColumn(1).setCellRenderer(new CustomTableRenderer());
        this.RouteList.getColumnModel().getColumn(2).setCellRenderer(new CustomTableRenderer());
        this.RouteList.getColumnModel().getColumn(3).setCellRenderer(new CustomTableRenderer());
        
        // this.RouteList.setToolTipText("Left click route to execute, right click to edit");
    }
        
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AButton;
    private javax.swing.JLabel ALabel;
    private javax.swing.JSlider ASlider;
    private javax.swing.JLabel ActiveLocLabel;
    private javax.swing.JButton AddLocButton;
    private javax.swing.JLabel AddNewLocLabel;
    private javax.swing.JButton AddRouteButton;
    private javax.swing.JButton AltEmergencyStop;
    private javax.swing.JButton BButton;
    private javax.swing.JLabel BLabel;
    private javax.swing.JSlider BSlider;
    private javax.swing.JToggleButton Backward;
    private javax.swing.JButton BulkDisable;
    private javax.swing.JButton BulkEnable;
    private javax.swing.JButton CButton;
    private javax.swing.JLabel CLabel;
    private javax.swing.JButton CS3OpenBrowser;
    private javax.swing.JSlider CSlider;
    private javax.swing.JLabel CurrentKeyLabel;
    private javax.swing.JButton DButton;
    private javax.swing.JLabel DLabel;
    private javax.swing.JSlider DSlider;
    private javax.swing.JLabel DirectionLabel;
    private javax.swing.JButton DownArrow;
    private javax.swing.JButton EButton;
    private javax.swing.JLabel ELabel;
    private javax.swing.JSlider ESlider;
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
    private javax.swing.JSlider FSlider;
    private javax.swing.JButton FiveButton;
    private javax.swing.JToggleButton Forward;
    private javax.swing.JButton FourButton;
    private javax.swing.JLabel FullSpeedLabel;
    private javax.swing.JTabbedPane FunctionTabs;
    private javax.swing.JButton GButton;
    private javax.swing.JLabel GLabel;
    private javax.swing.JSlider GSlider;
    private javax.swing.JButton HButton;
    private javax.swing.JLabel HLabel;
    private javax.swing.JSlider HSlider;
    private javax.swing.JButton IButton;
    private javax.swing.JLabel ILabel;
    private javax.swing.JSlider ISlider;
    private javax.swing.JPanel InnerLayoutPanel;
    private javax.swing.JButton JButton;
    private javax.swing.JLabel JLabel;
    private javax.swing.JSlider JSlider;
    private javax.swing.JButton KButton;
    private javax.swing.JLabel KLabel;
    private javax.swing.JSlider KSlider;
    private javax.swing.JLabel KeyboardLabel;
    private javax.swing.JLabel KeyboardLabel1;
    private javax.swing.JLabel KeyboardNumberLabel;
    private javax.swing.JPanel KeyboardPanel;
    private javax.swing.JTabbedPane KeyboardTab;
    private javax.swing.JButton LButton;
    private javax.swing.JLabel LLabel;
    private javax.swing.JSlider LSlider;
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
    private javax.swing.JRadioButton LocTypeDCC;
    private javax.swing.JRadioButton LocTypeMFX;
    private javax.swing.JRadioButton LocTypeMM2;
    private javax.swing.JButton MButton;
    private javax.swing.JLabel MLabel;
    private javax.swing.JSlider MSlider;
    private javax.swing.JPanel ManageLocPanel;
    private javax.swing.JButton NButton;
    private javax.swing.JLabel NLabel;
    private javax.swing.JSlider NSlider;
    private javax.swing.JButton NextKeyboard;
    private javax.swing.JButton NextLocMapping;
    private javax.swing.JButton NineButton;
    private javax.swing.JButton OButton;
    private javax.swing.JLabel OLabel;
    private javax.swing.JSlider OSlider;
    private javax.swing.JButton OnButton;
    private javax.swing.JButton OneButton;
    private javax.swing.JButton OverrideCS2DataPath;
    private javax.swing.JButton PButton;
    private javax.swing.JLabel PLabel;
    private javax.swing.JSlider PSlider;
    private javax.swing.JButton PowerOff;
    private javax.swing.JButton PrevKeyboard;
    private javax.swing.JButton PrevLocMapping;
    private javax.swing.JLabel PrimaryControls;
    private javax.swing.JButton QButton;
    private javax.swing.JLabel QLabel;
    private javax.swing.JSlider QSlider;
    private javax.swing.JButton RButton;
    private javax.swing.JLabel RLabel;
    private javax.swing.JSlider RSlider;
    private javax.swing.JButton RightArrow;
    private javax.swing.JTable RouteList;
    private javax.swing.JPanel RoutePanel;
    private javax.swing.JButton SButton;
    private javax.swing.JLabel SLabel;
    private javax.swing.JSlider SSlider;
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
    private javax.swing.JSlider TSlider;
    private javax.swing.JButton ThreeButton;
    private javax.swing.JButton TurnOffFnButton;
    private javax.swing.JButton TurnOnLightsButton;
    private javax.swing.JButton TwoButton;
    private javax.swing.JButton UButton;
    private javax.swing.JLabel ULabel;
    private javax.swing.JSlider USlider;
    private javax.swing.JButton UpArrow;
    private javax.swing.JButton VButton;
    private javax.swing.JLabel VLabel;
    private javax.swing.JSlider VSlider;
    private javax.swing.JButton WButton;
    private javax.swing.JLabel WLabel;
    private javax.swing.JSlider WSlider;
    private javax.swing.JButton XButton;
    private javax.swing.JLabel XLabel;
    private javax.swing.JSlider XSlider;
    private javax.swing.JButton YButton;
    private javax.swing.JLabel YLabel;
    private javax.swing.JSlider YSlider;
    private javax.swing.JButton ZButton;
    private javax.swing.JLabel ZLabel;
    private javax.swing.JSlider ZSlider;
    private javax.swing.JButton ZeroButton;
    private javax.swing.JLabel ZeroPercentSpeedLabel;
    private javax.swing.JButton allButton;
    private javax.swing.JPanel autoLocPanel;
    private javax.swing.JPanel autoPanel;
    private javax.swing.JTextArea autonomyJSON;
    private javax.swing.JPanel autonomyPanel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JButton checkDuplicates;
    private javax.swing.JButton clearButton;
    private javax.swing.JTextArea debugArea;
    private javax.swing.JButton exportJSON;
    private javax.swing.JButton gracefulStop;
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
    private javax.swing.JLabel jLabel9;
    private javax.swing.JList jList1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JLabel layoutListLabel;
    private javax.swing.JButton layoutNewWindow;
    private javax.swing.JPanel layoutPanel;
    private javax.swing.JTabbedPane locCommandPanels;
    private javax.swing.JPanel locCommandTab;
    private javax.swing.JLabel locIcon;
    private javax.swing.JPanel logPanel;
    private javax.swing.JLabel sizeLabel;
    private javax.swing.JCheckBox sliderSetting;
    private javax.swing.JButton smallButton;
    private javax.swing.JRadioButton sortByID;
    private javax.swing.JRadioButton sortByName;
    private javax.swing.JButton startAutonomy;
    private javax.swing.JButton syncLocStateButton;
    private javax.swing.JButton validateButton;
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
        
        this.LayoutGridRenderer.submit(new Thread(() -> {
            //InnerLayoutPanel.setVisible(false);
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
        }));
    }
}
