package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLayout;
import org.traincontrol.marklin.MarklinLayoutComponent;

/**
 *
 * @author Adam
 */
public class LayoutGrid
{
    private LayoutLabel[][] grid;
    public final int maxWidth;
    public final int maxHeight;
    
    // Should the .text property be rendered in non-empty cells?
    public static final boolean ALLOW_TEXT_ANYWHERE = true;
    
    // Prefix that denotes a station label
    // Used to show autonomy locations on the layout
    public static final String LAYOUT_STATION_PREFIX = "Point:";
    public static final String LAYOUT_STATION_EMPTY = "[---]";
    public static final String LAYOUT_STATION_OCCUPIED = "[xxx]";
    public static final int LAYOUT_STATION_MAX_LENGTH = 10;
    public static final int LAYOUT_STATION_OPACITY = 210;
    
    // Component that holds the layout
    private JPanel container;
    
    private boolean cacheable = false;
    
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
        // JPanel container; // no longer needed - included as class field for caching
        
        parent.removeAll();
        
        // We need a non scaling panel for small layouts
        // if (width * size < parent.getWidth() || height * size < parent.getHeight() || popup)
        // {
            container = new JPanel();
            container.setBackground(Color.white);
            
            // Things mess up without this
            if (MarklinLayout.IGNORE_PADDING || layout.getEdit())
            {
                parent.setLayout(new FlowLayout());
            }
            else
            {
                // If we want to left-align smaller layouts
                parent.setLayout(new FlowLayout(FlowLayout.LEFT));
            }
            
            // We can only cache these panels
            cacheable = true;
        // }
        // else
        // {
        //     container = parent;
        // }

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
                    grid[x][y] = new LayoutLabel(null, master, size, ui, false);
                    gbc.gridwidth = 0;
                    gbc.gridheight = 0;
                    gbc.gridx = x;
                    gbc.gridy = y;
                    container.add(grid[x][y], gbc);  
                    continue;
                }
                // End GBC fix
                
                MarklinLayoutComponent c = layout.getComponent(x + offsetX, y  + offsetY);
                                
                // The edit value ensures that the icon is disabled in edit mode, and it disables clickability/events
                grid[x][y] = new LayoutLabel(c, master, size, ui, layout.getEdit());
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
                    
                    // Autonomy Station label 
                    if (c.getLabel().startsWith(LAYOUT_STATION_PREFIX) && !layout.getEdit())
                    {
                        // Hide text initially
                        text.setText("");
                        
                        // This callback will populate the label
                        ui.addLayoutStation(c.getLabel().replace(LAYOUT_STATION_PREFIX, ""), text);
                        text.setToolTipText(c.getLabel().replace(LAYOUT_STATION_PREFIX, ""));
                        
                        text.addMouseListener(new MouseAdapter()  
                        {  
                            @Override
                            public void mouseClicked(MouseEvent e)  
                            {  
                                if (e.getButton() == MouseEvent.BUTTON3)
                                {                              
                                    javax.swing.SwingUtilities.invokeLater(new Thread(() ->
                                    {
                                        LayoutRightclickAutonomyMenu menu = new LayoutRightclickAutonomyMenu(ui, text.getToolTipText());

                                        menu.show(e.getComponent(), e.getX(), e.getY());      
                                    }));
                                }
                            }  
                        }); 
                    }
                    // Regular labels
                    else if (!layout.getEditHideText())
                    {
                        text.setText(c.getLabel());
                    }
                    
                    text.setForeground(Color.BLACK);
                    text.setBackground(Color.WHITE);
                    text.setFont(new Font("Segoe UI", Font.PLAIN, size / 2));
                    
                    // Shift on-tile labels down
                    // Current limitation if we wanted to use borders: if you have a text element and an on-tile label in the same row
                    // , they both get shifted down by the same amount.  Therefore, do this multiline hack.
                    if (!c.isText() && !layout.getEditHideText())
                    {
                        //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        gbc.gridheight = 0;
                        text.setText("<html><br>" + text.getText().replaceAll(" ", "&nbsp;") + "</html>");      
                        
                        // Show the correct cursor
                        if (c.isClickable() && !layout.getEdit()) text.setCursor(new Cursor(Cursor.HAND_CURSOR));
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
                
                // Show address labels
                if (c != null && layout.getEdit() && layout.getEditShowAddress() && !c.isText() && c.isClickable())
                {
                    JLabel text = new JLabel();
                    text.setForeground(Color.RED);
                    text.setOpaque(true);
                    text.setBackground(new Color(255, 255, 255, LayoutGrid.LAYOUT_STATION_OPACITY)); // yellow
                    text.setFont(new Font("Segoe UI", Font.PLAIN, size / 3));
                    
                    //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                    gbc.gridheight = 0;
                    
                    // For uncouplers, show the precise address
                    String redOrGreen = "";
                    
                    if (c.isUncoupler())
                    {
                        if (c.isLogicalGreen())
                        {
                            redOrGreen = "g";
                        }
                        else
                        {
                            redOrGreen = "r";
                        }
                    }
                    
                    // Add the protocol
                    String protocol = "";

                    if (c.getProtocol() != null && c.getProtocol() != MarklinAccessory.accessoryDecoderType.MM2)
                    {
                        protocol = "<br>" + c.getProtocol().toString().toLowerCase() + "";
                    }
                    
                    text.setText("<html>" + c.getLogicalAddress() + redOrGreen + protocol + "</html>");      
                    
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
    
    /**
     * Return the container that was generated
     * @return 
     */
    public JPanel getContainer()
    {
        return container;
    }
    
    public boolean isCacheable()
    {
        return cacheable;
    }
    
    /**
     * Gets the coordinates of the specified layout label
     * @param target
     * @return 
     */
    public int[] getCoordinates(LayoutLabel target)
    {
        for (int x = 0; x < grid.length; x++)
        {
            for (int y = 0; y < grid[x].length; y++)
            {
                if (grid[x][y] == target)
                {
                    return new int[]{x, y};
                }
            }
        }

        return new int[]{-1, -1};
    }
    
    public LayoutLabel getValueAt(int x, int y)
    {
        if (x < 0 || x >= grid.length || y < 0 || y >= grid[x].length)
        {
            return null;
        }
        
        return grid[x][y];
    }
    
    /**
    * Retrieves a specific column from the grid.
    * @param colIndex The index of the column to retrieve.
    * @return A List of LayoutLabel objects representing the row.
    * @throws IndexOutOfBoundsException if the colIndex is invalid.
    */
    public List<LayoutLabel> getColumn(int colIndex)
    {
       if (colIndex < 0 || colIndex >= grid.length)
       {
           throw new IndexOutOfBoundsException("Column index out of bounds: " + colIndex);
       }
       
       return Arrays.asList(grid[colIndex]);
    }

   /**
    * Retrieves a specific column from the grid.
    * @param rowIndex The index of the column to retrieve.
    * @return A List of LayoutLabel objects representing the column.
    * @throws IndexOutOfBoundsException if the columnIndex is invalid.
    */
    public List<LayoutLabel> getRow(int rowIndex)
    {
        if (grid.length == 0 || rowIndex < 0 || rowIndex >= grid[0].length)
        {
            throw new IndexOutOfBoundsException("Row index out of bounds: " + rowIndex);
        }

        List<LayoutLabel> column = new ArrayList<>();
        for (LayoutLabel[] grid1 : grid)
        {
            column.add(grid1[rowIndex]);
        }

        return column;
    }
}
