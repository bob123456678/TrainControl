package gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
     * @param ui
     */
    public LayoutGrid(MarklinLayout layout, int size, JPanel parent, Container master, boolean popup, TrainControlUI ui)
    {  
        // Calculate boundaries
        int offsetX = layout.getMinx();
        int offsetY = layout.getMiny();

        int width = layout.getMaxx() - layout.getMinx() + 1;
        int height = layout.getMaxy() - layout.getMiny() + 1;

        // Increment width to fix GBC ui issue
        width = width + 1;
        
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
        container.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints(); 
        container.setSize(width * size, height * size);
        container.setMaximumSize(new Dimension(width * size, height * size));
        
        maxWidth = width * size;
        maxHeight = height * size;
               
        grid = new LayoutLabel[width][height];
                       
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                // GBC fix - we create a dummy column at the end with nothing in it to ensure long labels don't misalign things
                if (x == (width - 1))
                {
                    grid[x][y] = new LayoutLabel(null, master, size, ui);
                    gbc.gridwidth = 0;
                    gbc.gridx = x;
                    gbc.gridy = y;
                    container.add(grid[x][y], gbc);  

                    continue;
                }
                // End GBC fix
                
                MarklinLayoutComponent c = layout.getComponent(x + offsetX, y  + offsetY);
                
                grid[x][y] = new LayoutLabel(c, master, size, ui);
                
                if (c != null && c.isText())
                {
                    // Text labels can overflow.  This ensures that they don't widen other cells.
                    gbc.gridwidth = 0;
                    gbc.anchor = GridBagConstraints.BASELINE_LEADING;
                }
                else
                {
                    gbc.gridwidth = 1;
                }
                
                gbc.gridx = x;
                gbc.gridy = y;
                
                container.add(grid[x][y], gbc);     
                
                // Set references for each tile accessory
                if (c != null)
                {
                    // If popup is true, LayoutLabel.isParentVisible will be used to clean up stale label references
                    if ((c.isSwitch() || c.isSignal()) && c.getAccessory() != null)
                    {
                        c.getAccessory().addTile(grid[x][y]);
                    }
                    
                    if (c.isFeedback() && c.getFeedback() != null)
                    {
                        c.getFeedback().addTile(grid[x][y]);
                    }
                    
                    if (c.isThreeWay() && c.getAccessory2() != null)
                    {
                        c.getAccessory2().addTile(grid[x][y]); 
                    }          
                    
                    if (c.isRoute() && c.getRoute() != null)
                    {
                        c.getRoute().addTile(grid[x][y]);
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
