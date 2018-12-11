package gui;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import marklin.MarklinLayout;
import marklin.MarklinLayoutComponent;

/**
 *
 * @author Adam
 */
public class LayoutGrid
{
    public LayoutLabel[][] grid;

    public LayoutGrid(MarklinLayout layout, int size, JPanel parent, JTabbedPane master)
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
                || height * size < parent.getHeight())
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
                    if (c.isSwitch() || c.isSignal())
                    {
                        c.getAccessory().setTile(grid[x][y]);
                    }
                    
                    if (c.isFeedback())
                    {
                        c.getFeedback().setTile(grid[x][y]);
                    }
                    
                    if (c.isThreeWay())
                    {
                        c.getAccessory2().setTile(grid[x][y]); 
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
