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
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * This class represents the model for a track diagram
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
    //
    // Taken from the setup layer rather than declared here, so the side that decides whether a station
    // HAS a label and the side that draws it cannot drift apart.  Every existing reference to this name
    // still works; only where the string is defined has moved.
    public static final String LAYOUT_STATION_PREFIX =
        org.traincontrol.automationui.AutonomySession.STATION_LABEL_PREFIX;
    public static final String LAYOUT_STATION_EMPTY = "[---]";
    public static final String LAYOUT_STATION_OCCUPIED = "[xxx]";
    public static final int LAYOUT_STATION_MAX_LENGTH = 10;
    public static final int LAYOUT_STATION_OPACITY = 210;
    public static final int LAYOUT_ADDRESS_OPACITY = 200;

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
    public LayoutGrid(LayoutDiagram layout, int size, JPanel parent, Container master, boolean popup, TrainControlUI ui)
    {          
        // Which editor this is.  layout.getEdit() is true in BOTH - the autonomy editor borrows the
        // diagram editor's edit flag for its mutual exclusion - so keying label rendering on it changed
        // the track diagram editor as well, where the raw "Point:" text is exactly what the user needs
        // to see and edit.
        boolean autonomyEditor = master instanceof LayoutEditor
            && ((LayoutEditor) master).isAutonomyMode();

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
            if (LayoutDiagram.IGNORE_PADDING || layout.getEdit())
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
                
                LayoutDiagramComponent c = layout.getComponent(x + offsetX, y  + offsetY);

                // The square this cell is, and the station whose caption belongs on it.
                //
                // A caption is no longer text drawn into the diagram - it is part of the autonomy setup,
                // pointing at the sensor's square - so it is asked for rather than found by reading a
                // prefix off a label.  It can therefore sit on a square carrying no component at all,
                // which is why the text below is drawn for a caption as well as for a label.
                final org.traincontrol.automationui.TileGraph.TileKey square =
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), x + offsetX, y + offsetY);

                final org.traincontrol.automationui.TileGraph.TileKey captioned =
                    ui == null ? null : ui.autonomyCaptionAt(square);

                // The edit value ensures that the icon is disabled in edit mode, and it disables clickability/events
                grid[x][y] = new LayoutLabel(c, master, size, ui, layout.getEdit());
                gbc.anchor = GridBagConstraints.BASELINE_LEADING;

                boolean drawsText = captioned != null
                    || (c != null && (ALLOW_TEXT_ANYWHERE && c.hasLabel()
                        || !ALLOW_TEXT_ANYWHERE && c.isText()));

                if (drawsText)
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
                if (drawsText)
                {
                    JLabel text = new JLabel();

                    // Black unless something below has a reason to say otherwise
                    Color labelColour = Color.BLACK;
                    
                    // What the user wrote on this square, which is a different thing from the caption
                    final String own = c == null || c.getLabel() == null ? "" : c.getLabel();

                    // The station name, for a caption to show when no train is standing on it
                    final String captionName = captioned == null
                        ? null : ui.autonomyStationNameAt(captioned);

                    // Autonomy station caption, live.
                    //
                    // Not on a page autonomy has been told to ignore.  There is no Point behind the
                    // caption there - the graph is built without that page entirely - so the label
                    // would sit waiting for a state that never arrives, and clicking it would go
                    // looking for something that was never built.  The name is still drawn, below, as
                    // ordinary text: leaving the platform nameless would be a stranger answer than
                    // leaving it unwired.
                    if (captioned != null && !layout.getEdit()
                        && !ui.isPageExcludedFromAutonomy(layout.getName()))
                    {
                        // Blank until autonomy says otherwise.
                        //
                        // A caption belongs to the running setup: it shows what is standing at that
                        // station, and with nothing loaded there is no answer to give.  Seeding it
                        // with the station's name instead was tried and was worse - an unloaded
                        // diagram covered in names that look like live captions and are not, which is
                        // the placeholder state this comment exists to stop somebody restoring.
                        text.setText("");

                        // This callback will populate the label
                        ui.addLayoutStation(captioned, text, parent);
                        text.setToolTipText(captionName);

                        final org.traincontrol.automationui.TileGraph.TileKey station = captioned;

                        text.addMouseListener(new MouseAdapter()
                        {
                            @Override
                            public void mouseClicked(MouseEvent e)
                            {
                                if (e.getButton() == MouseEvent.BUTTON3)
                                {
                                    javax.swing.SwingUtilities.invokeLater(() ->
                                    {
                                        LayoutRightclickAutonomyMenu menu =
                                            new LayoutRightclickAutonomyMenu(ui, station);

                                        menu.show(e.getComponent(), e.getX(), e.getY());
                                    });
                                }
                                // Left-clicking a station will activate its locomotive
                                else
                                {
                                    // By SQUARE.  A square where trains may turn round is several
                                    // Points, none of them called what the diagram says, and a lookup
                                    // by the text of the caption simply returned null.
                                    org.traincontrol.automation.Point standing =
                                        ui.getAutonomyPointForTile(station);

                                    if (standing != null)
                                    {
                                        Locomotive atStation = standing.getCurrentLocomotive();
                                        Locomotive active = ui.getActiveLoc();

                                        if (atStation != null && !atStation.equals(active))
                                        {
                                            ui.jumpToLocomotive(atStation.getName());
                                        }
                                    }
                                }
                            }
                        });
                    }
                    // Regular labels
                    else if (!layout.getEditHideText())
                    {
                        if (captioned != null && autonomyEditor)
                        {
                            // In the AUTONOMY editor, the placeholder the running diagram shows when
                            // nothing is standing there - so the square looks like what it will look
                            // like.
                            text.setText(LAYOUT_STATION_EMPTY);

                            // Greyed HERE and nowhere else.  This placeholder says only "a caption
                            // lands on this square", and in the editor it sits on top of the arrows
                            // that say which way trains may arrive - which are the thing somebody has
                            // opened the editor to look at.  Kept rather than hidden: where the
                            // captions are is worth seeing while arranging them.
                            labelColour = new Color(150, 150, 150);
                        }
                        else if (captioned != null)
                        {
                            // A caption the diagram cannot act on: an excluded page, or the TRACK
                            // DIAGRAM editor, where it is drawn so the square is visibly spoken for
                            // but is not that editor to change - captions are edited where autonomy
                            // is edited.
                            text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                            if (layout.getEdit()) labelColour = new Color(150, 150, 150);
                        }
                        else
                        {
                            // Everything else: what the user wrote there.
                            text.setText(own);

                            // Writing of their own, greyed while autonomy is being edited: it is still
                            // worth seeing - it says what part of the railway this is - and it is not
                            // what the editor is for.
                            if (autonomyEditor) labelColour = new Color(150, 150, 150);
                        }
                    }


                    text.setForeground(labelColour);
                    text.setBackground(Color.WHITE);
                    text.setFont(new Font("Segoe UI", Font.PLAIN, size / 2));
                    
                    // Shift on-tile labels down
                    // Current limitation if we wanted to use borders: if you have a text element and an on-tile label in the same row
                    // , they both get shifted down by the same amount.  Therefore, do this multiline hack.
                    if (c != null && !c.isText() && !layout.getEditHideText())
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
                if (c != null &&
                        layout.getShowAddress() &&
                        !c.isText() && c.isClickable())
                {
                    JLabel text = new JLabel();
                    
                    // Cascade click event
                    final JLabel outer = grid[x][y];
                    
                    if (!layout.getEdit())
                    {
                        text.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                   
                    text.addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            // Manually trigger the mouseClicked event of grid[x][y]
                            for (MouseListener listener : outer.getMouseListeners())
                            {
                                listener.mouseClicked(e);
                            }
                        }
                        
                        @Override
                        public void mouseEntered(MouseEvent e)
                        {
                            // Manually trigger the mouseClicked event of grid[x][y]
                            for (MouseListener listener : outer.getMouseListeners())
                            {
                                listener.mouseEntered(e);
                            }
                        }
                    });

                    text.setForeground(Color.RED);
                    text.setOpaque(true);
                    text.setBackground(new Color(255, 255, 255, LayoutGrid.LAYOUT_ADDRESS_OPACITY)); // yellow
                    text.setFont(new Font("Segoe UI", Font.PLAIN, size / 3)); 
                    
                    // To avoid a bug where feedback doesn't yet exist, turn off tooltips in the editor
                    if (!layout.getEdit())
                    {
                        text.setToolTipText(c.toSimpleString());
                    }
                    
                    //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                    gbc.gridheight = 0;
                    gbc.anchor = GridBagConstraints.NORTHWEST;
                    
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

                    if (c.getProtocol() != null && c.getProtocol() != Accessory.accessoryDecoderType.MM2)
                    {
                        protocol = "<br>" + c.getProtocol().toString().toLowerCase() + "";
                    }
                    
                    text.setText("<html>" + c.getLogicalAddress() + redOrGreen + protocol + "</html>");      
                    
                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);
                }
                                                                              
                // Register the tile with autonomy, if it is watching.
                //
                // Here because this is the only place that knows both the page and the square: a
                // LayoutLabel is told neither, and LayoutGrid keeps no reference to the LayoutDiagram
                // after the constructor returns.
                if (c != null && ui != null)
                {
                    // Which page this square is on, told to the label itself.  Its right-click menu used
                    // to ask the main window which page was showing, which is the wrong page in every
                    // popup window.
                    grid[x][y].setAutonomyPage(layout.getName());

                    if (ui.getDiagramTileRegistry() != null)
                    {
                        ui.getDiagramTileRegistry().register(
                            new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), c.getX(), c.getY()),
                            grid[x][y]);
                    }
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
            throw new IndexOutOfBoundsException(
                I18n.f("error.columnIndexOutOfBounds", colIndex)
            );
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
            throw new IndexOutOfBoundsException(
                I18n.f("error.rowIndexOutOfBounds", rowIndex)
            );
        }

        List<LayoutLabel> column = new ArrayList<>();
        for (LayoutLabel[] grid1 : grid)
        {
            column.add(grid1[rowIndex]);
        }

        return column;
    }
}
