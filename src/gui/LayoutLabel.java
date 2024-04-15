package gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JLabel;
import marklin.MarklinLayoutComponent;

/**
 *
 * @author Adam
 */
public final class LayoutLabel extends JLabel
{
    private MarklinLayoutComponent component;
    
    private final Container parent;
    private String imageName;
    private final int size;
    private final TrainControlUI tcUI;
    
    public LayoutLabel(MarklinLayoutComponent c, Container parent, int size, TrainControlUI tcUI)
    {
        this.component = c;
        this.size = size;
        this.parent = parent;
        this.tcUI = tcUI;
        
        this.setSize(size, size);
        // This will ensure that long text labels don't mess up the grid layout - no longer needed when using gridbaglayout
        /*this.setMinimumSize(new Dimension(size, size));
        this.setPreferredSize(new Dimension(size, size));
        this.setMaximumSize(new Dimension(size, size));*/
        this.setForeground(Color.white);
        
        this.setImage(false);
        
        if (this.component != null)
        {
            if (this.component.isSwitch() || this.component.isSignal() 
                    || this.component.isUncoupler() || this.component.isFeedback()
                    || this.component.isRoute() || this.component.isLink())
            {
                if (this.component.isFeedback())
                {
                    this.addMouseListener(new MouseAdapter()  
                    {  
                        @Override
                        public void mouseClicked(MouseEvent e)  
                        {  
                           component.execSwitching();
                           
                           // So that possible routes get dynamically updated
                           tcUI.repaintAutoLocList(true);
                        }  
                    }); 
                }
                else if (this.component.isLink())
                {
                    this.addMouseListener(new MouseAdapter()  
                    {  
                        @Override
                        public void mouseClicked(MouseEvent e)  
                        {                  
                            if (parent instanceof LayoutPopupUI)
                            {
                                ((LayoutPopupUI) parent).goToLayoutPage(component.getRawAddress()); 
                            }
                            else
                            {
                                tcUI.goToLayoutPage(component.getRawAddress());
                            }
                        }  
                    }); 
                }
                else
                {
                    this.addMouseListener(new MouseAdapter()  
                    {  
                        @Override
                        public void mouseClicked(MouseEvent e)  
                        {  
                           component.execSwitching();
                        }  
                    });    
                }
            }
            // Blank tiles need to be the same size
            else
            {
                this.setIcon(new EmptyIcon(size, size)); 
            }
        }
        // Blank tiles need to be the same size
        else
        {
            this.setIcon(new EmptyIcon(size, size)); 
        }
    }
    
    /**
     * Checks if the parent window is visible
     * Used for pruning old label references
     * @return 
     */
    public boolean isParentVisible()
    {
        return this.parent.isVisible();
    }
    
    /**
     * Sets the image based on the component's state
     * @param update are we updating an existing image?
     */
    private synchronized void setImage(boolean update)
    {
        new Thread(() ->
        {
            if (this.component != null)
            {            
                // Special handling for text labels
                if (this.component.isText())
                {
                    // Text labels are now rendered at the grid level
                    /*this.setText(this.component.getLabel());
                    this.setForeground(Color.black);
                    this.setFont(new Font("Sans Serif", Font.PLAIN, this.size / 2));*/
                }
                else
                {
                    try
                    {
                        // Cache icons in memory to speed up rendering
                        Map<String,Image> imageCache = TrainControlUI.getImageCache();
                        String key = this.component.getImageKey(size);
                        
                        Image img;
                        
                        if (!imageCache.containsKey(key))
                        {
                            img = this.component.getImage(size);

                            imageCache.put(key, img);
                        }
                        else
                        {
                            img = imageCache.get(key);
                        }
                        
                        this.setIcon(new javax.swing.ImageIcon(
                            img     
                        )); 
                        
                        // Show a tooltip in the UI
                        if (!"".equals(this.component.toSimpleString()))
                        {
                            this.setToolTipText(this.component.toSimpleString());
                            
                            // Change the cursor to indicate the component is clickable
                            this.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                        }
                    }
                    catch (IOException ex)
                    {
                        this.tcUI.getModel().log(ex.getMessage());
                    }

                    this.imageName = component.getImageName(size);
                }
                
                if (update)
                {
                    this.repaint();
                    this.parent.repaint(); 
                    
                    if (this.component.isFeedback())
                    {
                        tcUI.repaintAutoLocList(true);
                    }
                }
            }
        }).start();
    }
    
    /**
     * Refreshes the tile's image
     */
    public void updateImage()
    {
        new Thread(() -> 
        {
            if (this.component != null)
            {
                if (!this.component.getImageName(size).equals(this.imageName))
                {
                    this.setImage(true);    
                }
            }
        }).start();
    }
    
    /**
     * An empty icon with arbitrary width and height.
     */
    private final class EmptyIcon implements Icon
    {
        private int width;
        private int height;

        public EmptyIcon()
        {
            this(0, 0);
        }

        public EmptyIcon(int width, int height)
        {
            this.width = width;
            this.height = height;
        }

        @Override
        public int getIconHeight()
        {
            return height;
        }

        @Override
        public int getIconWidth()
        {
            return width;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {}
    }
}
