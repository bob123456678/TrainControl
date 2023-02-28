/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    public LayoutLabel(MarklinLayoutComponent c, Container parent, int size)
    {
        this.component = c;
        this.size = size;
        this.parent = parent;
        
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
                    || this.component.isRoute())
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
        new Thread(() -> {
            if (this.component != null)
            {       
                // Special handling for text labels
                if (this.component.isText())
                {
                    this.setText(this.component.getLabel());
                    this.setForeground(Color.black);
                    this.setFont(new Font("Sans Serif", Font.PLAIN, this.size / 2));                
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
                        }
                    }
                    catch (IOException ex)
                    {
                        Logger.getLogger(LayoutLabel.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    this.imageName = component.getImageName(size);  
                }
                
                if (update)
                {
                    this.repaint();
                    this.parent.repaint(); 
                }
            }
        }).start();
    }
    
    /**
     * Refreshes the tile's image
     */
    public void updateImage()
    {
        new Thread(() -> {
            if (this.component != null)
            {
                if (!this.component.getImageName(size).equals(this.imageName))
                {
                    this.setImage(true);    
                }
            }
        }).start();
    }
}
