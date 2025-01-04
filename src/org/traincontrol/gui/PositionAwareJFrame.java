package org.traincontrol.gui;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import org.traincontrol.util.Conversion;

/**
 * Frame that remembers where it was located using Preferences
 */
public class PositionAwareJFrame extends JFrame
{
    // The preference key
    public static final String REMEMBER_WINDOW_LOCATION = "WindowLocation" + Conversion.getFolderHash(10);

    // Preferences store - can be changed
    // TODO - could be replaced and de-coupled by an abstract method call
    private final Preferences prefs = TrainControlUI.getPrefs();
    
    // By default, this is our class name
    private final String thisWindowName;
    
    // Custom index for the window if there are multiple windows with the same class name
    private String thisWindowIndex = "";
        
    // Have we loaded the window?
    private boolean loaded = false;
    
    public PositionAwareJFrame()
    {
        // Use the class name as the window identifier
        this.thisWindowName = getClass().getSimpleName();

        // Load the saved position, size, and state
        // Call this manually because we don't know when the window is ready
        // loadWindowBounds();

        // Add a listener to save the position, size, and state when the window is moved, resized, or state changes
        this.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentMoved(ComponentEvent e)
            {
                saveWindowBounds();
            }

            @Override
            public void componentResized(ComponentEvent e)
            {
                saveWindowBounds();
            }
        });
        
        // Add a listener to save the position, size, and state when the window is closed
        this.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                saveWindowBounds();
            }
        });  

        this.addWindowStateListener(e -> saveWindowBounds());
    }

    /**
     * Called automatically when the window is adjusted by the user,
     * but should be called manually if the window is programmatically changed
     */
    protected void saveWindowBounds()
    {
        try
        {
            String windowName = this.getWindowName();
            if (prefs.getBoolean(REMEMBER_WINDOW_LOCATION, false))
            {
                if (isVisible())
                {
                    prefs.putInt(windowName + "_x", this.getX());
                    prefs.putInt(windowName + "_y", this.getY());
                    prefs.putInt(windowName + "_width", this.getWidth());
                    prefs.putInt(windowName + "_height", this.getHeight());
                    prefs.putInt(windowName + "_state", this.getExtendedState());
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error saving window state: ");
            e.printStackTrace();
        }
    }

    /**
     * Call this just before the window is shown
     */
    protected void loadWindowBounds()
    {
        try
        {
            this.loaded = true;
            
            if (!prefs.getBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, false)) return;

            String windowName = this.getWindowName();
            if (prefs.get(windowName + "_x", null) != null && prefs.get(windowName + "_y", null) != null)
            {
                int x = prefs.getInt(windowName + "_x", this.getX());
                int y = prefs.getInt(windowName + "_y", this.getY());

                // Get the bounds of all screens
                GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
                boolean isInBounds = false;
                int tolerance = 10; // Tolerance for edge snapping

                for (GraphicsDevice screen : screens)
                {
                    Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
                    if (x >= screenBounds.x - tolerance && x <= screenBounds.x + screenBounds.width + tolerance &&
                        y >= screenBounds.y - tolerance && y <= screenBounds.y + screenBounds.height + tolerance)
                    {
                        isInBounds = true;
                        break;
                    }
                }

                // Check if the window is within the bounds of any screen
                if (isInBounds)
                {
                    this.setLocation(x, y);

                    // Only restore size and state if window is resiazable
                    if (this.isResizable() &&
                            prefs.get(windowName + "_width", null) != null &&
                            prefs.get(windowName + "_height", null) != null &&
                            prefs.get(windowName + "_state", null) != null
                    )
                    {
                        int width = prefs.getInt(windowName + "_width", this.getWidth());
                        int height = prefs.getInt(windowName + "_height", this.getHeight());
                        int state = prefs.getInt(windowName + "_state", this.getExtendedState());
                        this.setSize(width, height);
                        this.setExtendedState(state);
                    }
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error saving window state: ");
            e.printStackTrace();
        }
    }
    
    /**
     * Sets a custom index for this window, in case there are multiple of one class
     * @param thisWindowIndex 
     */
    public void setWindowIndex(String thisWindowIndex)
    {
        this.thisWindowIndex = thisWindowIndex;
    }

    /**
     * Checks if the loadWindowBounds method has previously been called
     * @return 
     */
    protected boolean isLoaded()
    {
        return this.loaded;
    }
    
    /**
     * Gets the fully qualified window name, including the index if set
     * @return 
     */
    private String getWindowName()
    {
        return this.thisWindowName + "_" + this.thisWindowIndex;
    }
}
