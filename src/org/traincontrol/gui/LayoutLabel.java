package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.border.Border;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.util.I18n;
import org.traincontrol.util.ImageUtil;

/**
 * Tiles inside track diagrams
 * @author Adam
 */
public final class LayoutLabel extends JLabel
{
    private LayoutDiagramComponent component;
    
    private final Container parent;
    private String imageName;
    private final int size;
    private final TrainControlUI tcUI;
    
    // Temporarily highlight changed tiles
    private static final int HIGHLIGHT_DURATION = 2250;
    private static final int CLICK_TIMEOUT = HIGHLIGHT_DURATION + 250;
    private long lastClicked = 0;

    /**
     * Diagram switching runs here rather than on the event thread.
     *
     * One thread, not a pool, and not a thread per click: the event queue used to serialise these
     * actions for free, and a three-way's two sends must not interleave with another tile's.  Daemon
     * so that it never holds the JVM open by itself.
     */
    private static final ExecutorService SWITCHING = Executors.newFixedThreadPool(1, runnable ->
    {
        Thread worker = new Thread(runnable, "LayoutSwitching");
        worker.setDaemon(true);
        return worker;
    });

    /**
     * Runs one diagram switching action off the event thread.
     *
     * execute rather than submit: submit captures whatever the action throws into a Future, and there
     * is no Future kept here to read it back from, so an escaping exception would disappear with no
     * sign of it anywhere.  On the event thread it used to reach the default handler and print, and
     * with execute it still does.
     *
     * Separate and public so the dispatch can be tested on its own: what has to hold is that the
     * action does not run on the event thread, that two of them never overlap, and that an exception
     * escaping one stays visible.
     *
     * @param action the switching work, including any sleeps it needs
     */
    public static void submitSwitching(Runnable action)
    {
        SWITCHING.execute(action);
    }
    
    private Icon lastIcon;
    private boolean edit;

    /**
     * What autonomy says is happening on this square, or null for nothing.
     *
     * Painted over the icon rather than baked into it, for three reasons that are all about the existing
     * rendering.  The icon cache is shared by every tile of a type, so recolouring one would recolour
     * them all or need a cache entry per state.  updateImage only refreshes when the icon NAME changes,
     * which autonomy state never does - hence the repaint below, which cannot ride that path.  And the
     * transient yellow highlight already works by swapping the icon out and back from lastIcon, so a
     * second effect doing the same would fight it for the one slot it restores from.
     *
     * Volatile: written from the monitor's publish and read while painting.
     */
    private volatile TileOverlay autonomyOverlay;

    // What the autonomy editor draws here - directions, lengths, selection.  Separate from the overlay
    // because they answer different questions and are cleared at different times.
    private volatile org.traincontrol.automationui.TileAnnotation autonomyAnnotation;
    
    public LayoutLabel(LayoutDiagramComponent c, Container parent, int size, TrainControlUI tcUI, boolean edit)
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

                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (e.getButton() == MouseEvent.BUTTON1)
                    {
                        ((LayoutEditor) parent).beginDrag(e, clicked);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (e.getButton() == MouseEvent.BUTTON1)
                    {
                        ((LayoutEditor) parent).endDrag(e, clicked);
                    }
                }
            });
            
            this.addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
                public void mouseDragged(MouseEvent e)
                {
                    ((LayoutEditor) parent).updateDrag(e, clicked); 
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
                                    javax.swing.SwingUtilities.invokeLater(() -> 
                                    {
                                        tcUI.editRoute(component.getRoute().getName());
                                    });
                                    
                                    return;
                                }
                                
                                javax.swing.SwingUtilities.invokeLater(() -> 
                                {
                                    boolean powerOnFirst = false;

                                    if (!tcUI.getModel().getPowerState())
                                    {
                                        Object[] options = {
                                            I18n.t("layout.ui.optionTurnPowerOnAndProceed"),
                                            I18n.t("layout.ui.optionProceed"),
                                            I18n.t("ui.cancel")
                                        };

                                        int choice = JOptionPane.showOptionDialog(
                                            tcUI,
                                            I18n.t("layout.ui.confirmAccessorySwitchPowerOff"),
                                            I18n.t("layout.ui.dialogPleaseConfirm"),
                                            JOptionPane.YES_NO_CANCEL_OPTION,
                                            JOptionPane.QUESTION_MESSAGE,
                                            null,
                                            options,
                                            options[0]
                                        );

                                        switch (choice)
                                        {
                                            case 0: // Power on
                                                // Done on the worker below, with the wait that follows it
                                                powerOnFirst = true;
                                                break;
                                            case 2: // No
                                                return;
                                            default:
                                                break;
                                        }
                                    }
                                    // Warn user of switching accessories along active routes
                                    else if (tcUI.getModel().hasAutoLayout() && tcUI.getModel().isAutonomyRunning())
                                    {
                                        Collection<Accessory> activeAccs = tcUI.getModel().getAutoLayout().getActiveAccs();
                                        
                                        if (activeAccs.contains(c.getAccessory()) || 
                                                (c.getAccessory2() != null && activeAccs.contains(c.getAccessory2())))
                                        {
                                            Object[] options = {
                                                I18n.t("ui.ok"),
                                                I18n.t("ui.cancel")
                                            };

                                            int choice = JOptionPane.showOptionDialog(
                                                tcUI,
                                                I18n.t("layout.ui.confirmAccessoryActiveRoute"),
                                                I18n.t("layout.ui.dialogPleaseConfirm"),
                                                JOptionPane.YES_NO_CANCEL_OPTION,
                                                JOptionPane.QUESTION_MESSAGE,
                                                null,
                                                options,
                                                options[0]
                                            );
                                            
                                            switch (choice)
                                            {
                                                case 0: // ok
                                                    break;
                                                case 1: // cancel
                                                    return;                                                    
                                                default:
                                                    break;
                                            }
                                        }                                
                                    }

                                    lastClicked = System.currentTimeMillis();

                                    // Everything below this point blocks, so none of it belongs on the
                                    // event thread.  A three-way sleeps between its two drives - that gap
                                    // is what keeps the turnout out of the both-diverging combination -
                                    // and turning the power on waits a further second for the track to
                                    // come up.  Run here, those sleeps froze the whole UI, including the
                                    // repaint of the drive that had already moved.
                                    //
                                    // The sends and the gap between them stay together on one worker.  The
                                    // dialogs above have already been answered, on the thread they belong
                                    // on.
                                    final boolean powerOn = powerOnFirst;

                                    submitSwitching(() ->
                                    {
                                        if (powerOn)
                                        {
                                            tcUI.getModel().go();

                                            if (tcUI.getModel().getNetworkCommState())
                                            {
                                                try
                                                {
                                                    tcUI.getModel().waitForPowerState(true);

                                                    // We need a significant delay because the power might take some time to come on
                                                    Thread.sleep(1000);
                                                }
                                                catch (InterruptedException ex)
                                                {
                                                    Thread.currentThread().interrupt();
                                                }
                                            }
                                        }

                                        component.execSwitching();
                                    });
                                });
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
     * Sets the image based on the component's state.  On a cache miss the decode/scale runs off the EDT
     * (so it never blocks Swing) and the cached result is then applied on the EDT; a cache hit goes
     * straight to the EDT.
     * @param update are we updating an existing image?
     */
    private void setImage(boolean update)
    {
        // Only the decode needs to move off the EDT; text tiles and cache hits go straight through.
        if (this.component != null && !this.component.isText())
        {
            final Map<String, Image> imageCache = TrainControlUI.getImageCache();
            final String key = this.component.getImageKey(size, edit);

            if (imageCache.get(key) == null)
            {
                // Decode/scale off the EDT, cache it, then run the (now cache-hit) Swing work on the EDT.
                this.tcUI.getTileImageLoader().submit(() ->
                {
                    try
                    {
                        Image img = this.component.getImage(size, edit);

                        if (img != null)
                        {
                            imageCache.putIfAbsent(key, img);
                        }
                    }
                    catch (IOException ex)
                    {
                        this.tcUI.getModel().log(ex.getMessage());
                    }

                    setImageOnEDT(update);
                });

                return;
            }
        }

        setImageOnEDT(update);
    }

    /**
     * Applies the (already-cached) image and its Swing updates on the EDT.
     * @param update are we updating an existing image?
     */
    private void setImageOnEDT(boolean update)
    {
        javax.swing.SwingUtilities.invokeLater(() ->
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

                            // The cache is a ConcurrentHashMap, which rejects null values.
                            // getImage() never returns null today (it throws instead), but guard
                            // defensively so a null can never reach the cache.
                            if (img != null)
                            {
                                imageCache.put(key, img);
                            }
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
                            // Both of these run on the EDT: this block already does, and a Swing Timer
                            // fires there too.  The overlay and the restore used to be applied from a
                            // raw thread, mutating a Swing component off the EDT - the one place in this
                            // class that did not marshal its work.
                            this.setIcon(ImageUtil.addHighlightOverlay((ImageIcon) this.getIcon()));

                            javax.swing.Timer restore = new javax.swing.Timer(HIGHLIGHT_DURATION, (restoreEvent) ->
                            {
                                if ((System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
                                {
                                    this.setIcon(lastIcon);
                                }
                            });

                            restore.setRepeats(false);
                            restore.start();
                        }
                        
                        // Show a tooltip in the UI
                        if (!edit && !"".equals(this.component.toSimpleString()))
                        {
                            // We don't need tooltips if address labels are on
                            //if (!tcUI.showLayoutAddresses())
                            //{
                                this.setToolTipText(this.component.toSimpleString());
                            //}
                            
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
        });
    }
    
    /**
     * Refreshes the tile's image
     * @param highlight
     */
    /**
     * Whether this tile belongs to the diagram EDITOR rather than to an operating view.
     *
     * The two draw autonomy from different places - the editor from the panel beside it, the operating
     * views from the setup on disk - so the one must not publish over the other.
     * @return
     */
    public boolean isEditMode()
    {
        return edit;
    }

    /**
     * Flashes this tile the same yellow the diagram uses when a route or the Central Station changes
     * something.
     *
     * The same overlay and the same duration as that highlight, deliberately: it is the gesture a user
     * of this application already reads as "look here", and inventing a second one - a border, say -
     * both means something new to learn and disturbs the tile's own border, which in the editor is the
     * grid line.
     */
    public void flashHighlight()
    {
        // An empty square carries an EmptyIcon, which is not null and not an ImageIcon - so the cast
        // below threw where the null check passed, and it escaped mid-action because the menu's flash
        // sits outside its own try/catch.
        if (!(this.getIcon() instanceof ImageIcon)) return;

        // The plain icon is captured ONCE and kept until the flash ends.  Capturing it on every call
        // meant a second flash arriving before the first had finished recorded the already-highlighted
        // icon as the thing to restore - so the tile was put back yellow and stayed that way, which is
        // exactly what repeated clicking on one run produced.
        if (flashTimer != null && flashTimer.isRunning())
        {
            flashTimer.stop();
        }
        else
        {
            flashRestore = this.getIcon();
        }

        this.setIcon(ImageUtil.addHighlightOverlay((ImageIcon) flashRestore));

        flashTimer = new javax.swing.Timer(HIGHLIGHT_DURATION, (event) ->
        {
            this.setIcon(flashRestore);

            flashRestore = null;
            flashTimer = null;
        });

        flashTimer.setRepeats(false);
        flashTimer.start();
    }

    // The icon to put back when the flash ends, and the timer that will do it
    private Icon flashRestore;
    private javax.swing.Timer flashTimer;

    /**
     * Sets what autonomy is showing on this square, repainting if it changed.
     *
     * Repaints here rather than through updateImage, which returns early unless the icon name changed -
     * and autonomy state does not change the icon name, so riding that path would show nothing.
     *
     * @param overlay what to show, or null for nothing
     */
    public void setAutonomyOverlay(TileOverlay overlay)
    {
        TileOverlay effective = overlay == null || overlay.isBlank() ? null : overlay;

        if (effective == null ? autonomyOverlay == null : effective.equals(autonomyOverlay)) return;

        autonomyOverlay = effective;

        // repaint() is safe from any thread; the monitor publishes from its own worker
        this.repaint();
    }

    /**
     * @return what autonomy is showing here, or null
     */
    public TileOverlay getAutonomyOverlay()
    {
        return autonomyOverlay;
    }

    /**
     * Sets what the autonomy editor is showing on this square - directions, lengths, selection -
     * repainting if it changed.  Same discipline as the overlay, for the same reason: none of it
     * changes the icon name, so updateImage would show nothing.
     *
     * @param annotation what to show, or null for nothing
     */
    public void setAutonomyAnnotation(org.traincontrol.automationui.TileAnnotation annotation)
    {
        org.traincontrol.automationui.TileAnnotation effective =
            annotation == null || annotation.isBlank() ? null : annotation;

        if (effective == null ? autonomyAnnotation == null
            : effective.equals(autonomyAnnotation)) return;

        autonomyAnnotation = effective;

        this.repaint();
    }

    /**
     * Draws the tile, then whatever autonomy is saying about it.
     *
     * After super, so it lands over the icon and never touches setIcon - which is what lets it coexist
     * with the transient highlight.  Note that station and address labels are z-ordered above tiles by
     * LayoutGrid, so this can never cover their text.
     *
     * @param g
     */
    @Override
    protected void paintComponent(java.awt.Graphics g)
    {
        super.paintComponent(g);

        TileOverlay overlay = autonomyOverlay;
        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        if ((overlay == null || overlay.isBlank())
            && (annotation == null || annotation.isBlank())) return;

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

        try
        {
            if (overlay != null) overlay.paint(g2, getWidth(), getHeight());

            // over the wash, so the editing marks stay legible while monitoring is also on
            if (annotation != null) annotation.paint(g2, getWidth(), getHeight());
        }
        finally
        {
            g2.dispose();
        }
    }

    public void updateImage(boolean highlight)
    {
        // TODO improve the way highlighting is done, delete global variables
        // No thread needed: the check below is trivial and setImage() already marshals its
        // Swing work to the EDT via invokeLater, so callers (e.g. the CS feedback thread) are
        // not blocked.  Avoids spawning a raw thread per tile on every state change.
        if (this.component != null)
        {
            if (!this.component.getImageName(size, edit).equals(this.imageName) || highlight)
            {
                this.setImage(true);
            }
        }
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
    
    public LayoutDiagramComponent getComponent()
    {
        return component;
    }
}
