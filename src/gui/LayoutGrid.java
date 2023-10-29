package gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import marklin.MarklinLayout;
import marklin.MarklinLayoutComponent;
import util.ImageUtil;

/**
 *
 * @author Adam
 */
public class LayoutGrid
{
    public LayoutLabel[][] grid;
    public final int maxWidth;
    public final int maxHeight;
    
    // Should the .text property be rendered in non-empty cells?
    public static final boolean ALLOW_TEXT_ANYWHERE = true;
    
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
        height = height + 1;

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
            if (MarklinLayout.IGNORE_PADDING)
            {
                parent.setLayout(new FlowLayout());
            }
            else
            {
                // If we want to left-align smaller layouts
                parent.setLayout(new FlowLayout(FlowLayout.LEFT));
            }
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
                if (x == (width - 1) || y == (height - 1))
                {
                    grid[x][y] = new LayoutLabel(null, master, size, ui);
                    gbc.gridwidth = 0;
                    gbc.gridheight = 0;
                    gbc.gridx = x;
                    gbc.gridy = y;
                    container.add(grid[x][y], gbc);  
                    continue;
                }
                // End GBC fix
                
                MarklinLayoutComponent c = layout.getComponent(x + offsetX, y  + offsetY);
                
                grid[x][y] = new LayoutLabel(c, master, size, ui);
                gbc.anchor = GridBagConstraints.BASELINE_LEADING;

                if (c != null && (ALLOW_TEXT_ANYWHERE && c.hasLabel() || !ALLOW_TEXT_ANYWHERE && c.isText()))
                {
                    // Text labels can overflow.  This ensures that they don't widen other cells.
                    gbc.gridwidth = 0;
                }
                else
                {
                    gbc.gridwidth = 1;
                }
                
                gbc.gridx = x;
                gbc.gridy = y;
                gbc.gridheight = 1; // Height will always be 1, except if rendering text
                
                // grid[x][y].setBorder(new LineBorder(Color.BLUE, 1)); // for debugging only

                container.add(grid[x][y], gbc);  
                
                // Render text separately
                if (c != null && (ALLOW_TEXT_ANYWHERE && c.hasLabel() || !ALLOW_TEXT_ANYWHERE && c.isText()))
                {
                    JLabel text = new JLabel();
                    text.setText(c.getLabel());
                    text.setForeground(Color.BLACK);
                    text.setBackground(Color.WHITE);
                    text.setFont(new Font("Sans Serif", Font.PLAIN, size / 2));
                    
                    // Shift on-tile labels down
                    // Current limitation if we wanted to use borders: if you have a text element and an on-tile label in the same row
                    // , they both get shifted down by the same amount.  Therefore, do this multiline hack.
                    if (!c.isText())
                    {
                        //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        gbc.gridheight = 0;
                        text.setText("<html><br>" + text.getText().replaceAll(" ", "&nbsp;") + "</html>");      
                        
                        // How the correct cursor
                        if (c.isClickable()) text.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                    else
                    {
                        //text.setBorder(new EmptyBorder(5 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        // 11 * (size / 30) at left to center
                        gbc.gridheight = 0;    
                    }
                    
                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);
                }
                                                                              
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
