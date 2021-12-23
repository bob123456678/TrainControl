package gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import marklin.MarklinLayout;
import marklin.MarklinLayoutComponent;

/**
 *
 * @author Adam
 */
public class LayoutGrid
{
    public LayoutLabel[][] grid;
    public final int maxWidth;
    public final int maxHeight;

    /**
     * This class draws the train layout and ensures that proper event references are set in the model
     * @param layout reference to the layout from the model
     * @param size size of each tile, in pixels
     * @param parent panel to contain the layout
     * @param master container with the panel
     * @param popup is this layout being rendered in a separate window?
     */
    public LayoutGrid(MarklinLayout layout, int size, JPanel parent, Container master, boolean popup)
    {  
        // Calculate boundaries
        int offsetX = layout.getMinx();
        int offsetY = layout.getMiny();

        int width = layout.getMaxx() - layout.getMinx() + 1;
        int height = layout.getMaxy() - layout.getMiny() + 1;

        // Create layout                      
        JPanel container;
        
        parent.removeAll();
        
        // We need a non scaling panel for small layouts
        if (width * size < parent.getWidth() 
                || height * size < parent.getHeight() || popup)
        {
            container = new JPanel();
            container.setBackground(Color.white);
            
            // Things mess up without this
            parent.setLayout(new FlowLayout());
        }
        else
        {
            container = parent;
        }

        // Generate grid
        container.setLayout(new GridLayout(height, width, 0, 0));
        container.setSize(width * size, height * size);
        container.setMaximumSize(new Dimension(width * size, height * size));
        
        maxWidth = width * size;
        maxHeight = height * size;
               
        grid = new LayoutLabel[width][height];
                
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                MarklinLayoutComponent c = layout.getComponent(x + offsetX, y  + offsetY);
                
                grid[x][y] = new LayoutLabel(c, master, size);
                
                container.add(grid[x][y]);     
                
                // Set references for each tile accessory
                if (c != null)
                {
                    // If popup is true, LayoutLabel.isParentVisible will be used to clean up stale label references
                    if (c.isSwitch() || c.isSignal())
                    {
                        c.getAccessory().addTile(grid[x][y]);
                    }
                    
                    if (c.isFeedback())
                    {
                        c.getFeedback().addTile(grid[x][y]);
                    }
                    
                    if (c.isThreeWay())
                    {
                        c.getAccessory2().addTile(grid[x][y]); 
                    }                    
                }
            }
        }     
 
        if (!container.equals(parent))
        {
            parent.add(container);
        } 
    } 
}
