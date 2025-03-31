package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.border.Border;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLayoutComponent;
import org.traincontrol.util.ImageUtil;

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
    
    // Temporarily highlight changed tiles
    private static final int HIGHLIGHT_DURATION = 2000;
    private static final int CLICK_TIMEOUT = 2500;
    private long lastClicked = 0;
    
    private Icon lastIcon;
    private boolean edit;
    
    public LayoutLabel(MarklinLayoutComponent c, Container parent, int size, TrainControlUI tcUI, boolean edit)
    {
        this.component = c;
        this.size = size;
        this.parent = parent;
        this.tcUI = tcUI;
        this.edit = edit;
        
        this.setSize(size, size);
        // This will ensure that long text labels don't mess up the grid layout - no longer needed when using gridbaglayout
        /*this.setMinimumSize(new Dimension(size, size));
        this.setPreferredSize(new Dimension(size, size));
        this.setMaximumSize(new Dimension(size, size));*/
        this.setForeground(Color.white);
        
        this.setImage(false);
        
        // Edit mode callback
        if (edit)
        {
            LayoutLabel clicked = this;
            this.addMouseListener(new MouseAdapter()  
                {  
                    @Override
                    public void mouseClicked(MouseEvent e)  
                    {  
                        ((LayoutEditor) parent).receiveClickEvent(e, clicked);
                    }
                    
                    @Override
                    public void mouseEntered(MouseEvent e)  
                    {  
                        ((LayoutEditor) parent).receiveMoveEvent(e, clicked);
                    }
                });

            // Add a border around the icons
            Border blackBorder = BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1); 
            this.setBorder(blackBorder);
        }
        
        if (this.component != null)
        {
            if (this.component.isSwitch() || this.component.isSignal() 
                    || this.component.isUncoupler() || this.component.isFeedback()
                    || this.component.isRoute() || this.component.isLink())
            {
                // Regular mouse events
                if (!edit)
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
                                // Edit route on right-click
                                if (e.getButton() == MouseEvent.BUTTON3 && component.isRoute() && (!tcUI.getModel().getPowerState() || !tcUI.getModel().getNetworkCommState())) 
                                {
                                    javax.swing.SwingUtilities.invokeLater(new Thread(() -> 
                                    {
                                        tcUI.editRoute(component.getRoute().getName());
                                    }));
                                    
                                    return;
                                }
                                
                                javax.swing.SwingUtilities.invokeLater(new Thread(() -> 
                                {
                                    if (!tcUI.getModel().getPowerState())
                                    {
                                        Object[] options = {"Turn Power On & Proceed", "Proceed", "Cancel"};

                                        int choice = JOptionPane.showOptionDialog(
                                            tcUI,
                                            "Switching this accessory will only update the UI because the power is off. Proceed?",
                                            "Please Confirm",
                                            JOptionPane.YES_NO_CANCEL_OPTION,
                                            JOptionPane.QUESTION_MESSAGE,
                                            null,
                                            options,
                                            options[0]
                                        );

                                        switch (choice)
                                        {
                                            case 0: // Power on
                                                tcUI.getModel().go();
                                                // return;

                                                if (tcUI.getModel().getNetworkCommState())
                                                try
                                                {
                                                    tcUI.getModel().waitForPowerState(true);

                                                    // We need a significant delay because the power might take some time to come on
                                                    Thread.sleep(1000);
                                                } 
                                                catch (InterruptedException ex) { }    

                                                break;
                                            case 2: // No
                                                return;
                                            default:
                                                break;
                                        }
                                    }

                                    lastClicked = System.currentTimeMillis();
                                    component.execSwitching();

                                }));
                            }  
                        });    
                    }
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
    private void setImage(boolean update)
    {
        javax.swing.SwingUtilities.invokeLater(
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
                        String key = this.component.getImageKey(size, edit);
                        
                        Image img;
                        
                        if (!imageCache.containsKey(key))
                        {
                            img = this.component.getImage(size, edit);

                            imageCache.put(key, img);
                        }
                        else
                        {
                            img = imageCache.get(key);
                        }
                        
                        boolean hadIcon = (this.getIcon() != null);
                        lastIcon = new javax.swing.ImageIcon(
                            img     
                        );
                        
                        this.setIcon(lastIcon); 
                        
                        // Temporarily highlight changes when they happen from a route/CS/keyboard command
                        if (!edit && (this.component.isSignal() || this.component.isSwitch()) && hadIcon && (System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
                        {
                            new Thread(() -> 
                            {
                                this.setIcon(ImageUtil.addHighlightOverlay((ImageIcon) this.getIcon()));

                                try
                                {
                                    Thread.sleep(HIGHLIGHT_DURATION);
                                } 
                                catch (InterruptedException ex) { }

                                if ((System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
                                {
                                    this.setIcon(lastIcon);
                                }
                            }).start();
                        }
                        
                        // Show a tooltip in the UI
                        if (!edit && !"".equals(this.component.toSimpleString()))
                        {
                            // Add the protocol
                            String protocol = "";
                            
                            if (this.component.getAccessory() != null && 
                                    this.component.getAccessory().getDecoderType() != MarklinAccessory.accessoryDecoderType.MM2 )
                            {
                                protocol = " (" + this.component.getAccessory().getDecoderType() + ")";
                            }
                            
                            this.setToolTipText(this.component.toSimpleString() + protocol);
                            
                            // Change the cursor to indicate the component is clickable
                            this.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                        }
                    }
                    catch (IOException ex)
                    {
                        this.tcUI.getModel().log(ex.getMessage());
                    }

                    this.imageName = component.getImageName(size, edit);
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
        }));
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
                if (!this.component.getImageName(size, edit).equals(this.imageName))
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
    
    public MarklinLayoutComponent getComponent()
    {
        return component;
    }
}
