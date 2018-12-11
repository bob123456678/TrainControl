/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import marklin.MarklinLayoutComponent;

/**
 *
 * @author Adam
 */
public final class LayoutLabel extends JLabel
{
    private MarklinLayoutComponent component;
    
    private JTabbedPane parent;
    private String imageName;
    private int size;
    
    public LayoutLabel(MarklinLayoutComponent c, JTabbedPane parent, int size)
    {
        this.component = c;
        this.size = size;
        this.parent = parent;
        
        this.setSize(size, size);
        this.setForeground(Color.white);
        
        this.setImage();
        
        if (this.component != null)
        {
            if (this.component.isSwitch() || this.component.isSignal() 
                    || this.component.isUncoupler() || this.component.isFeedback())
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
    
    public void setImage()
    {
        if (this.component != null)
        {            
            try
            {
                this.setIcon(new javax.swing.ImageIcon(
                        this.component.getImage(size)
                ));
            } catch (IOException ex)
            {
                Logger.getLogger(LayoutLabel.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            this.imageName = component.getImageName(size);      
        }
    }
    
    public void updateImage()
    {
        if (this.component != null)
        {
            if (!this.component.getImageName(size).equals(this.imageName))
            {
                this.setImage();
                this.repaint();
                this.parent.repaint(); 
            }
        }
    }
}
