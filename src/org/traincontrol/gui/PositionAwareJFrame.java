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
import static org.traincontrol.gui.TrainControlUI.REMEMBER_WINDOW_LOCATION;

/**
 * Frame that remembers where it was located
 */
public class PositionAwareJFrame extends JFrame
{
    private final Preferences prefs;
    private final String thisWindowName;
    protected String thisWindowIndex = "";
    public static final String MAIN_WINDOW_NAME = "TrainControlUI_";
    
    private String getWindowName()
    {
        return this.thisWindowName + "_" + this.thisWindowIndex;
    }

    public PositionAwareJFrame()
    {
        this.prefs = TrainControlUI.getPrefs();

        // Use the class name as the window identifier
        this.thisWindowName = getClass().getSimpleName();

        // Load the saved position, size, and state
        // Call this manually because we don't know when the window is ready
        // loadWindowBounds(true);

        // Add a listener to save the position, size, and state when the window is moved, resized, or state changes
        this.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentMoved(ComponentEvent e)
            {
                saveWindowBounds(false);
            }

            @Override
            public void componentResized(ComponentEvent e)
            {
                saveWindowBounds(true);
            }
        });
        
        // Add a listener to save the position, size, and state when the window is closed
        this.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowBounds(false);
            }
        });  

        this.addWindowStateListener(e -> saveWindowBounds(false));
    }

    protected void saveWindowBounds(boolean isResized)
    {
        try
        {
            String windowName = this.getWindowName();
            if (prefs.getBoolean(REMEMBER_WINDOW_LOCATION, false))
            {
                if (!windowName.equals(MAIN_WINDOW_NAME) || !isResized)
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

    protected void loadWindowBounds(boolean rememberSize)
    {
        try
        {
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

                    // Only restore size and state if windowName is not "TrainControlUI"
                    if (rememberSize)
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
}
