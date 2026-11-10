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
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import org.traincontrol.util.ImageUtil;

/**
 * Tiles inside track diagrams
 * @author Adam
 */
public final class LayoutLabel extends JLabel
{
    /**
     * Which square of which page this tile is, so it can ask whether a train is lying across it.
     *
     * Set by `LayoutGrid`.  Null on a tile the grid did not key (the blank filler squares), and asking about null is
     * answered "not covered" rather than guarded at every call.
     */
    private org.traincontrol.automationui.TileGraph.TileKey square;

    /**
     * Tells this tile which square it is drawing.
     *
     * @param key the square, or null
     */
    public void setSquare(org.traincontrol.automationui.TileGraph.TileKey key)
    {
        this.square = key;
    }

    private LayoutDiagramComponent component;
    
    private final Container parent;
    private String imageName;
    private final int size;
    private final TrainControlUI tcUI;
    
    /** The name of the thread that asks a route tile's conflict question and then runs the route. */
    public static final String ROUTE_CONFLICT_THREAD = "TrainControl route conflict";

    // Temporarily highlight changed tiles
    private static final int HIGHLIGHT_DURATION = 2250;
    private static final int CLICK_TIMEOUT = HIGHLIGHT_DURATION + 250;
    private long lastClicked = 0;

    /**
     * Diagram switching runs here rather than on the event thread.
     *
     * One thread, not a pool, and not a thread per click: the actions must stay serialized, as the event queue
     * serialized them for free, and a three-way's two sends must not interleave with another tile's.  Daemon so
     * that it never holds the JVM open by itself.
     */
    private static final ExecutorService SWITCHING = Executors.newFixedThreadPool(1, runnable ->
    {
        Thread worker = new Thread(runnable, "LayoutSwitching");
        worker.setDaemon(true);
        return worker;
    });

    /**
     * Runs one diagram switching action off the event thread, one at a time.  execute rather than submit, so an
     * exception escaping the action reaches the default handler instead of vanishing in a Future.  Public so the
     * dispatch can be tested.
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
     * What autonomy says is happening on this square, or null.  Painted over the icon rather than baked in: the icon
     * cache is shared by every tile of a type, and the transient highlight already owns the one slot it restores from.
     * Volatile: written from the monitor's publish, read while painting.
     */
    private volatile TileOverlay autonomyOverlay;

    /** Whether this square draws its trains, asked as it paints: the autonomy editor's Show Trains switch (FR-119). */
    private java.util.function.BooleanSupplier trainsShown = () -> true;

    /** Has this square ask `shown` whether to draw the trains on it (FR-119). */
    public void setTrainsShown(java.util.function.BooleanSupplier shown)
    {
        this.trainsShown = shown == null ? () -> true : shown;
    }

    // What the autonomy editor draws here - directions, lengths, selection.  Separate from the overlay
    // because they answer different questions and are cleared at different times.
    private volatile org.traincontrol.automationui.TileAnnotation autonomyAnnotation;
    
    /**
     * Whether this label is one of the grid's spacers (the dummy last row and column) rather than a square.  A flag,
     * not "has no component": blank squares in the middle of a layout have none either, and the editor draws on them.
     */
    private boolean spacer;

    /** Says this label is one of the grid's spacers; called by LayoutGrid as it builds them. */
    void markSpacer()
    {
        this.spacer = true;

        // and take off anything already put on, since the border is set in the constructor
        this.setBorder(null);

        // It keeps its room: with the spacer at zero, one caption moved 351 other things by eight pixels
        // (testACaptionNeverMovesAnythingElse).
    }

    /**
     * @return true when this label is a spacer rather than a square of the diagram
     */
    public boolean isSpacer()
    {
        return this.spacer;
    }

    public LayoutLabel(LayoutDiagramComponent c, Container parent, int size, TrainControlUI tcUI, boolean edit)
    {
        this(c, parent, size, tcUI, edit, null);
    }

    /** A square, given the editor's grid setting by the grid that makes it (null: ask it), asked once a build. */
    public LayoutLabel(LayoutDiagramComponent c, Container parent, int size, TrainControlUI tcUI, boolean edit,
        Boolean gridLines)
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
        
        COUNT_CONSTRUCTED.incrementAndGet();
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

                // A pointer can go straight off the squares onto the tools column, crossing no margin, so the square it
                // left would keep the pointer's outline
                @Override
                public void mouseExited(MouseEvent e)
                {
                    ((LayoutEditor) parent).receiveExitEvent(e, clicked);
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

            // The gray grid is put here first; the editor's restingBorder puts it back after a hover.  Both ask the
            // same rule, so the toggle reaches both (FR-006).  Off, tiles sit flush as in the viewer.
            this.setBorder(LayoutEditor.restingBorder(false,
                parent instanceof LayoutEditor && ((LayoutEditor) parent).isAutonomyMode(),
                gridLines != null ? gridLines : LayoutEditor.showGrid()));
        }
        
        // Every square on the main window reports the pointer, plain track too: the shortcuts that move a locomotive
        // from square to square need to know which is meant, and one aimed at plain track must be ignored, not acted on
        // at the last station hovered.
        if (!edit && tcUI != null)
        {
            LayoutLabel hovering = this;

            this.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    // From the label's own square, not from what is drawn on it: a station's name sits on blank space
                    // beside the platform and must stay aimable.
                    tcUI.setHoveredDiagramTile(hovering.autonomyPage,
                        hovering.squareX, hovering.squareY);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    tcUI.setHoveredDiagramTile(null, -1, -1);
                }
            });
        }

        if (this.component != null)
        {
            // Every square answers a right-click: the autonomy menu is about the place, and plain track is as much a
            // place as a turnout.
            if (!edit && !(this.component.isSwitch() || this.component.isSignal()
                    || this.component.isUncoupler() || this.component.isFeedback()
                    || this.component.isRoute() || this.component.isLink()))
            {
                this.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        openStationMenu(e);
                    }
                });
            }

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
                               // A sensor is what a station is, so right-clicking a station opens the autonomy menu.
                               if (openStationMenu(e)) return;

                               // A right-click that opened nothing must not flip the sensor: it would fake an occupancy
                               // change the layout reacts to.
                               if (javax.swing.SwingUtilities.isRightMouseButton(e)) return;

                               // A tail question waiting on the diagram takes the click before the sensor flips
                               // (FR-100).
                               if (TailCrossedPrompt.takesTheClick(LayoutLabel.this, e.getClickCount())) return;

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
                                    ((LayoutPopupUI) parent).followLink(component);
                                }
                                else
                                {
                                    tcUI.followLink(component);
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
                                if (openStationMenu(e)) return;

                                // Edit route on right-click
                                if (e.getButton() == MouseEvent.BUTTON3 && component.isRoute() && (!tcUI.getModel().getPowerState() || !tcUI.getModel().getNetworkCommState())) 
                                {
                                    javax.swing.SwingUtilities.invokeLater(() -> 
                                    {
                                        tcUI.editRoute(component.getRoute().getName());
                                    });
                                    
                                    return;
                                }
                                
                                // The window this tile is in, read now: a redraw can take the tile out of it (OB-324)
                                final java.awt.Window asked = javax.swing.SwingUtilities.getWindowAncestor(LayoutLabel.this);

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
                                            tcUI.stillShowing(asked),
                                            I18n.t("layout.ui.confirmAccessorySwitchPowerOff"),
                                            I18n.t("layout.ui.dialogPleaseConfirm"),
                                            JOptionPane.YES_NO_CANCEL_OPTION,
                                            JOptionPane.QUESTION_MESSAGE,
                                            null,
                                            options,
                                            options[0]
                                        );

                                        // Anything but the two yeses is a no: Escape or the close box answers
                                        // CLOSED_OPTION (-1), and closing a confirmation must not throw the accessory.
                                        switch (choice)
                                        {
                                            case 0: // Power on, then proceed
                                                // Done on the worker below, with the wait that follows it
                                                powerOnFirst = true;
                                                break;
                                            case 1: // Proceed with the power off
                                                break;
                                            default: // Cancel, Escape, or the close box
                                                return;
                                        }
                                    }
                                    // Asked whether or not the power was on: "power on and proceed" must not clear
                                    // protection at an occupied platform unmentioned.  Remembered, because the worker
                                    // below re-asks and must not refuse a click already accepted.
                                    boolean askedAboutProtection = false;

                                    if (tcUI.getModel().hasAutoLayout() && tcUI.getModel().isAutonomyRunning())
                                    {
                                        Collection<Accessory> activeAccs = tcUI.getModel().getAutoLayout().getActiveAccs();
                                        
                                        // The protecting-signal half: a platform's protecting signal is usually driven
                                        // by occupancy, so a route setting it green is refused and a click setting it
                                        // green by hand must be too.  Asked of the layout so this and
                                        // MarklinRoute.heldReason cannot drift apart.  Only the green direction: red is
                                        // what protection itself does.  Tested on the command, since a three-way's
                                        // `execSwitching` drives its two accessories directly, not through
                                        // `Accessory.doSwitch()`.
                                        boolean protecting =
                                            aboutToClearProtection(tcUI, c.getAccessory())
                                            || aboutToClearProtection(tcUI, c.getAccessory2());

                                        if (activeAccs.contains(c.getAccessory()) || 
                                                (c.getAccessory2() != null && activeAccs.contains(c.getAccessory2()))
                                                || protecting)
                                        {
                                            Object[] options = {
                                                I18n.t("ui.ok"),
                                                I18n.t("ui.cancel")
                                            };

                                            int choice = JOptionPane.showOptionDialog(
                                                tcUI.stillShowing(asked),
                                                protecting
                                                    ? I18n.t("layout.ui.confirmAccessoryProtecting")
                                                    : I18n.t("layout.ui.confirmAccessoryActiveRoute"),
                                                I18n.t("layout.ui.dialogPleaseConfirm"),
                                                JOptionPane.YES_NO_CANCEL_OPTION,
                                                JOptionPane.QUESTION_MESSAGE,
                                                null,
                                                options,
                                                options[0]
                                            );
                                            
                                            // As above, and more so: the accessory is part of a route running right
                                            // now.  Escape must not throw the turnout under that train.
                                            switch (choice)
                                            {
                                                case 0: // ok
                                                    break;
                                                default: // cancel, Escape, or the close box
                                                    return;
                                            }

                                            // Only when the protection question was the one answered: agreeing to throw
                                            // an accessory on an active route says nothing about a platform.
                                            askedAboutProtection = protecting;
                                        }                                
                                    }

                                    // The same question for a route tile, which has no accessory of its own
                                    // (`activeAccs.contains(null)` is false); every route this component can hold is a
                                    // MarklinRoute, the only kind that knows the autonomy graph.
                                    final org.traincontrol.marklin.MarklinRoute onTile =
                                        c.isRoute()
                                            && c.getRoute() instanceof org.traincontrol.marklin.MarklinRoute
                                        ? (org.traincontrol.marklin.MarklinRoute) c.getRoute() : null;

                                    lastClicked = System.currentTimeMillis();

                                    // Everything below blocks, so none of it belongs on the event thread: a three-way
                                    // sleeps between its two drives and turning the power on waits a further second.
                                    //
                                    // The sends and the gap between them stay together on one worker; the dialogs above
                                    // are already answered.
                                    final boolean powerOn = powerOnFirst;
                                    final boolean warnedAboutProtection = askedAboutProtection;

                                    submitSwitching(() ->
                                    {
                                        if (powerOn)
                                        {
                                            tcUI.getModel().go();

                                            if (tcUI.getModel().getNetworkCommState())
                                            {
                                                try
                                                {
                                                    // Bounded, and the switching goes ahead either way: a wait with no
                                                    // deadline would park the pool's only thread, and so every tile in
                                                    // the application.
                                                    if (!tcUI.getModel().waitForPowerState(true,
                                                        org.traincontrol.marklin.MarklinControlStation.POWER_STATE_TIMEOUT))
                                                    {
                                                        // Names the tile, which also gets the message said: the log
                                                        // drops a line identical to the one before it.
                                                        tcUI.getModel().logf(
                                                            "layout.warnPowerNotConfirmed",
                                                            component.getAddress());
                                                    }

                                                    // We need a significant delay because the power might take some time to come on
                                                    Thread.sleep(1000);
                                                }
                                                catch (InterruptedException ex)
                                                {
                                                    Thread.currentThread().interrupt();
                                                }
                                            }
                                        }

                                        // The route conflict is asked here, not before the worker: asked earlier, "turn
                                        // the power on and proceed" would run the route into a dead track with the
                                        // power still off, the case this feature exists for.
                                        if (onTile != null)
                                        {
                                            // Off this worker before anything can block on a person: submitSwitching
                                            // has one thread shared by every tile, so a dialog left standing would
                                            // freeze them all.
                                            new Thread(() ->
                                            {
                                                tcUI.routesStartHereFrom(asked);

                                                // One question, three answers: a conflict that cleared between the
                                                // check and the dialog must not come back as "override", which would
                                                // run the route with every guard off unasked.
                                                TrainControlUI.RouteConflict answer =
                                                    tcUI.askAboutRouteConflict(onTile, tcUI.stillShowing(asked), null);

                                                if (answer == TrainControlUI.RouteConflict.OVERRIDE)
                                                {
                                                    onTile.execRouteOverridingConflicts();

                                                    return;
                                                }

                                                if (answer == TrainControlUI.RouteConflict.REFUSED) return;

                                                component.execSwitching();
                                            }, ROUTE_CONFLICT_THREAD).start();

                                            return;
                                        }

                                        // And ask again, here, where the command goes: `Layout.refreshOneSignal` can
                                        // drive the same accessory from an occupancy change between the dialog and this
                                        // switching thread (a train arrives, protection sets the signal red, this
                                        // command sets it green again over an occupied platform).  Refused rather than
                                        // re-asked, since asking here would hold the one switching thread; skipped for
                                        // a click the operator already accepted.
                                        if (!warnedAboutProtection
                                            && (aboutToClearProtection(tcUI, c.getAccessory())
                                                || aboutToClearProtection(tcUI, c.getAccessory2())))
                                        {
                                            tcUI.getModel().logf("layout.warnProtectionArrived",
                                                component.getAddress());

                                            return;
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
     */
    public boolean isParentVisible()
    {
        // No parent counts as not visible, which removes the label from the accessory or feedback that holds it.  A
        // grid built for an export has a null master, and a throw here would abandon every tile after it.
        return this.parent != null && this.parent.isVisible();
    }

    /**
     * Whether this label belongs to the same window as another: used on a grid rebuild to tell a replaced generation
     * from a label of a different window showing the same page (the main window caches a page's grid, so its labels
     * are legitimately detached for a while).
     */
    public boolean sharesWindowWith(LayoutLabel other)
    {
        return other != null && other.parent == this.parent;
    }

    /**
     * Drops the labels that a newly registered one replaces.  Nothing else can: updateTiles' prune asks whether a
     * label's parent is visible, which never fails on the main window, so every grid rebuild would leave a page of dead
     * labels.
     *
     * A registered label is dropped only when all three hold: SAME SQUARE (one accessory is drawn on several squares of
     * a page, and LayoutGrid registers every label before attaching the container); SAME WINDOW (the main window caches
     * a page's grid, so its labels are detached but alive); NOT DISPLAYABLE (a replaced grid's labels have no peer).
     * The arriving label is never judged.  Where the square cannot be established the label is kept: a missed prune is
     * a slow leak, a wrong one a dead tile.
     */
    public static void forgetReplaced(java.util.Collection<LayoutLabel> registered, LayoutLabel arriving)
    {
        if (registered == null || arriving == null) return;

        for (java.util.Iterator<LayoutLabel> i = registered.iterator(); i.hasNext();)
        {
            LayoutLabel existing = i.next();

            if (existing != arriving && existing.sharesWindowWith(arriving)
                && existing.isSameSquareAs(arriving) && !existing.isDisplayable())
            {
                i.remove();
            }
        }
    }

    /**
     * Whether two labels are drawn on the same square of the same page; false whenever that cannot be established, so
     * an unknown square never matches (see forgetReplaced).
     */
    private boolean isSameSquareAs(LayoutLabel other)
    {
        if (other == null || this.component == null || other.component == null) return false;

        if (this.autonomyPage == null || other.autonomyPage == null) return false;

        return this.autonomyPage.equals(other.autonomyPage)
            && this.component.getX() == other.component.getX()
            && this.component.getY() == other.component.getY();
    }

    /** Which page this square is on; asking the main window is wrong in a popup, which shows a page of its own. */
    private String autonomyPage;

    /**
     * Where this label sits on its page.  Told to it rather than read off the component, because a label with no
     * component still has a square - those are the squares a station's name is drawn on.
     */
    public void setAutonomySquare(String page, int x, int y)
    {
        this.autonomyPage = page;
        this.squareX = x;
        this.squareY = y;
    }

    // Where this label is, for a hover to report; -1 until told, as for the spacers, which are on no square.
    private int squareX = -1;
    private int squareY = -1;
    
    /**
     * A station's own menu, from the track rather than from its caption, which may sit elsewhere or be missing.
     * Returns true when the menu was opened and the caller should do nothing else with the click.
     */
    private boolean openStationMenu(MouseEvent e)
    {
        if (e.getButton() != MouseEvent.BUTTON3 || component == null) return false;

        // A station gets the station items; every other square gets the menu without them, so a right-click anywhere
        // leads into the autonomy setup.
        final org.traincontrol.automationui.TileGraph.TileKey station =
            tcUI.autonomyStationAt(autonomyPage, component.getX(), component.getY());

        // The square itself, which the setup menu acts on whether or not anything has been made of it yet.
        final org.traincontrol.automationui.TileGraph.TileKey here =
            tcUI.autonomyTileAt(autonomyPage, component.getX(), component.getY());

        LayoutRightclickAutonomyMenu.showFor(tcUI, station, here,
            e.getComponent(), e.getX(), e.getY());

        return true;
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
                // Counted before it is submitted, so a grid asking "are the tiles ready" cannot see zero between
                // building the labels and the pool picking the work up.  Only a first draw counts: a running layout's
                // state-keyed image misses would keep the count from ever being at rest.
                if (!update) this.tcUI.tileDecodeStarted();

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
                    finally
                    {
                        // In a finally: a tile that fails to decode still has to stop being counted, or the diagram
                        // waits for it forever.
                        setImageOnEDT(update);

                        if (!update) this.tcUI.tileDecodeFinished();
                    }
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
    public static final java.util.concurrent.atomic.AtomicInteger COUNT_CONSTRUCTED =
        new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger COUNT_APPLIED =
        new java.util.concurrent.atomic.AtomicInteger();

    private void setImageOnEDT(boolean update)
    {
        COUNT_APPLIED.incrementAndGet();
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

                        // A flash that is up is given up whenever the picture is replaced: its timer holds the icon it
                        // captured and would draw the tile as it was.
                        discardFlash();

                        this.setIcon(lastIcon);

                        // Neither mark is an icon (MT-309): the train line and the gray wash over blocked track are
                        // painted in `paintCoveredMark`, so a tile flashing yellow still shows both.
                        
                        // Temporarily highlight changes when they happen from a route/CS/keyboard command
                        if (!edit && (this.component.isSignal() || this.component.isSwitch()) && hadIcon && (System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
                        {
                            // The one before it is stopped first (OB-333): nulling the field would leave the old timer
                            // to `setIcon(lastIcon)` over a later highlight, and a square changed twice inside the hold
                            // would have no yellow.
                            endAccessoryHighlight();

                            // Both run on the EDT: this block does, and a Swing Timer fires there too.
                            this.setIcon(ImageUtil.addHighlightOverlay((ImageIcon) this.getIcon()));

                            final javax.swing.Timer[] mine = new javax.swing.Timer[1];

                            mine[0] = new javax.swing.Timer(HIGHLIGHT_DURATION, (restoreEvent) ->
                            {
                                if ((System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
                                {
                                    // Straight back to the tile: the marks are painted over the icon (MT-309), so they
                                    // are not in the icon to lose.
                                    this.setIcon(lastIcon);

                                    accessoryRestores++;
                                }

                                // No longer outstanding, but only if it is still this timer: a three-way drives twice,
                                // and a second highlight may have replaced it.
                                if (accessoryHighlight == mine[0]) accessoryHighlight = null;
                            });

                            accessoryHighlight = mine[0];

                            accessoryHighlight.setRepeats(false);
                            accessoryHighlight.start();
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
                    // Its own square and the squares around it, not the whole window, which would redraw everything on
                    // every switch, signal and sensor event.  Overlapping children are drawn together (OB-172).
                    java.awt.Container around = getParent();

                    if (around != null)
                    {
                        around.repaint(getX() - getWidth(), getY() - getHeight(), 3 * getWidth(), 3 * getHeight());
                    }
                    else
                    {
                        this.repaint();
                    }
                    
                    if (this.component.isFeedback())
                    {
                        tcUI.repaintAutoLocList(true);
                    }
                }
            }
        });
    }
    
    /**
     * Whether this tile belongs to the diagram editor rather than an operating view: the editor draws autonomy from the
     * panel beside it, the operating views from the setup on disk, so one must not publish over the other.
     */
    public boolean isEditMode()
    {
        return edit;
    }

    /**
     * Flashes this tile the same yellow the diagram uses when a route or the Central Station changes something: same
     * overlay and duration, because a border would disturb the editor's grid line.
     */
    public void flashHighlight()
    {
        flashHighlight(org.traincontrol.util.ImageUtil.HIGHLIGHT, HIGHLIGHT_DURATION);
    }

    /**
     * The same flash, in a color and for a length the caller chooses: the route editor lights what a route commands in
     * yellow and what it merely checks in orange.
     */
    public void flashHighlight(java.awt.Color wash, int holdMs)
    {
        // An empty square carries an EmptyIcon, not an ImageIcon, so the cast below would throw mid-action: the menu's
        // flash sits outside its own try/catch.
        if (!(this.getIcon() instanceof ImageIcon)) return;

        // The plain icon is captured once and kept until the flash ends: capturing it on every call would let a second
        // flash record the already-highlighted icon as the one to restore.  An accessory highlight that is up is ended
        // first.
        endAccessoryHighlight();

        if (flashTimer != null && flashTimer.isRunning())
        {
            flashTimer.stop();
        }
        else
        {
            flashRestore = this.getIcon();
        }

        this.setIcon(ImageUtil.addHighlightOverlay((ImageIcon) flashRestore, wash));

        flashTimer = new javax.swing.Timer(holdMs, (event) ->
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

    /** How many accessory highlights have put their picture back, for the test. */
    private int accessoryRestores;

    /** How many accessory highlights have ended by putting the tile's picture back, since this label was made. */
    public int accessoryRestores()
    {
        return accessoryRestores;
    }

    /** The accessory highlight's own timer, so it can be ended rather than left to fire later. */
    private javax.swing.Timer accessoryHighlight;

    /**
     * Whether a flash is outstanding on this tile - a restore that will put back the picture it captured.  For
     * `core.testLayoutTiles.testTheFlashAndTheAccessoryHighlightDoNotUndoEachOther`: the picture is set through
     * `invokeLater`, so which icon shows at an instant can't be pinned, and which restore is armed can.
     *
     * @return true while a flash will still be put back
     */
    public boolean isFlashOutstanding()
    {
        return flashTimer != null;
    }

    /** Whether an accessory highlight is outstanding: its yellow wash will still be taken off by its own timer. */
    public boolean isAccessoryHighlightOutstanding()
    {
        return accessoryHighlight != null;
    }

    /**
     * Ends an accessory highlight at once, putting back the picture it was laid over; a no-op when none is up.  Public
     * for the test that drives the two highlights against each other.
     */
    public void endAccessoryHighlight()
    {
        if (accessoryHighlight == null) return;

        accessoryHighlight.stop();

        if (lastIcon != null) this.setIcon(lastIcon);

        accessoryHighlight = null;
    }

    /** Gives up a flash without putting its picture back, because something truer has just been drawn. */
    private void discardFlash()
    {
        if (flashTimer == null) return;

        flashTimer.stop();

        flashRestore = null;
        flashTimer = null;
    }

    /**
     * Ends a flash now, rather than when its timer runs out: a walk from square to square (Mass Assign Lengths, Name
     * Everything) would otherwise leave the square just left yellow for its full hold (MT-454).
     */
    public void endFlash()
    {
        if (flashTimer == null) return;

        flashTimer.stop();

        this.setIcon(flashRestore);

        flashRestore = null;
        flashTimer = null;
    }

    /**
     * Sets what autonomy is showing on this square (null for nothing), repainting if it changed.  Repaints here rather
     * than through updateImage, which returns early unless the icon name changed.
     */
    public void setAutonomyOverlay(TileOverlay overlay)
    {
        TileOverlay effective = overlay == null || overlay.isBlank() ? null : overlay;

        if (effective == null ? autonomyOverlay == null : effective.equals(autonomyOverlay)) return;

        TileOverlay was = autonomyOverlay;

        autonomyOverlay = effective;

        // Nothing is lifted for a running train: the panel paints every train after all its components (OB-159,
        // OB-259).

        // repaint() is safe from any thread; the monitor publishes from its own worker.  And the tiles around it when a
        // train comes or goes (MT-642): its icon is not clipped to this tile, and on a curve it reaches onto the next
        // ones.
        java.awt.Container around = getParent();

        if (around != null && ((was != null && was.hasTrain()) || (effective != null && effective.hasTrain())))
        {
            around.repaint(getX() - getWidth(), getY() - getHeight(), 3 * getWidth(), 3 * getHeight());
        }
        else
        {
            this.repaint();
        }
    }

    /**
     * Sets what the autonomy editor is showing on this square (directions, lengths, selection), repainting if it
     * changed; null for nothing.  Like the overlay, none of it changes the icon name.
     */
    public void setAutonomyAnnotation(org.traincontrol.automationui.TileAnnotation annotation)
    {
        org.traincontrol.automationui.TileAnnotation effective =
            annotation == null || annotation.isBlank() ? null : annotation;

        if (effective == null ? autonomyAnnotation == null
            : effective.equals(autonomyAnnotation)) return;

        // And a station that spilled, which this may take away: its spill is on the squares around.
        boolean spilled = spills(autonomyAnnotation);

        autonomyAnnotation = effective;

        // And the tiles around it where a train stands: the icon reaches past this tile.
        java.awt.Container around = getParent();

        TileOverlay train = autonomyOverlay;

        if (around != null && (spilled || train != null && train.hasTrain()))
        {
            around.repaint(getX() - getWidth(), getY() - getHeight(), 3 * getWidth(), 3 * getHeight());
        }
        else
        {
            this.repaint();
        }
    }

    /**
     * Draws the tile, then whatever autonomy says about it.  After super, so it lands over the icon and coexists with
     * the transient highlight.  Nothing is lifted over the captions or address labels (OB-159).
     */
    @Override
    protected void paintComponent(java.awt.Graphics g)
    {
        // The square itself, faintly, where the railway refuses it (MT-278): the same tile with more transparency.  On
        // the icon rather than beside it: the composite is set before `super.paintComponent` and undone after.
        // Everything autonomy draws on top is painted at full strength afterwards.
        boolean refused = !edit && square != null && tcUI != null && tcUI.isTrackBlocked(square);

        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        if (refused)
        {
            // One road of a double curve, where that is what is covered (OB-207): a double curve is one tile with two
            // roads.  Null elsewhere, where the square is the road.
            java.awt.Shape road = theRoadToFade();

            if (road != null)
            {
                // The rest of the tile at full strength first: the fade is the tile at reduced alpha, so painting only
                // the road would leave the rest unpainted.
                java.awt.Graphics2D rest = (java.awt.Graphics2D) g.create();

                try
                {
                    java.awt.geom.Area outside = new java.awt.geom.Area(
                        new java.awt.Rectangle(0, 0, getWidth(), getHeight()));

                    outside.subtract(new java.awt.geom.Area(road));

                    rest.clip(outside);

                    super.paintComponent(rest);
                }
                finally
                {
                    rest.dispose();
                }
            }

            java.awt.Graphics2D faded = (java.awt.Graphics2D) g.create();

            try
            {
                if (road != null) faded.clip(road);

                faded.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, BLOCKED_ALPHA));

                super.paintComponent(faded);
            }
            finally
            {
                faded.dispose();
            }

            paintCurvedSensorsArrowsWhole(g);
        }
        else if (annotation != null && annotation.isInGray() && flashTimer == null)
        {
            // Grayed as it is painted, so the shared icon keeps its color; a flash keeps its yellow.
            ImageUtil.inGray((java.awt.Graphics2D) g, new java.awt.Rectangle(getWidth(), getHeight()),
                art -> super.paintComponent(art));
        }
        else
        {
            super.paintComponent(g);
        }

        TileOverlay overlay = autonomyOverlay;

        // The train first, under everything autonomy draws (MT-309): the arrows and the run line are more urgent and go
        // on top.  Except under a run line, where it is drawn again over the line (FR-106, below).
        java.awt.Graphics2D mark = (java.awt.Graphics2D) g.create();

        try
        {
            paintCoveredMark(mark);
        }
        finally
        {
            mark.dispose();
        }

        if ((overlay == null || overlay.isBlank())
            && (annotation == null || annotation.isBlank())) return;

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

        try
        {
            // The static layer first, and the running one over it: the running line covers only the rails it claims,
            // and the path a train is actually taking is the more urgent.
            //
            // Told which region is already faint (OB-212): `refused` drew the art at `BLOCKED_ALPHA`, so the
            // annotation's own wash must not compound with it.  A region, not a yes/no: on a double curve one road is
            // faded.
            java.awt.Shape faded = !refused ? null
                : (theRoadToFade() == null
                    ? new java.awt.Rectangle(0, 0, getWidth(), getHeight()) : theRoadToFade());

            if (annotation != null) annotation.paint(g2, getWidth(), getHeight(), faded);

            // Told where the track runs, so a run that ends here stops on the rail rather than mid-square (OB-026).
            if (overlay != null)
            {
                // Told whether the tile is already faint (MT-375): on a square both blocked and held the two fades
                // would compound into a tone difference between neighbors.
                overlay.paint(g2, getWidth(), getHeight(),
                    annotation == null ? null : annotation.trackCentre(getWidth(), getHeight()),
                    faded);
            }

            // And the train's tail over the run line (FR-106): the tail lies on the road just driven, which the run
            // line draws as reached, so drawn first it would be covered.  Only where a run line lies, or it would lie
            // twice over itself and come out lighter.
            java.awt.Shape run = overlay == null ? null
                : overlay.runArea(getWidth(), getHeight(),
                    annotation == null ? null : annotation.trackCentre(getWidth(), getHeight()));

            if (run != null)
            {
                java.awt.Graphics2D tail = (java.awt.Graphics2D) g2.create();

                try
                {
                    tail.clip(run);

                    paintCoveredMark(tail, true);
                }
                finally
                {
                    tail.dispose();
                }
            }

            // And the badge back on top of the line (MT-076): a station is where the train is going, so it must not be
            // buried; only where an overlay could have covered it.
            if (overlay != null && annotation != null) annotation.paintBadgeOverRun(g2, getWidth(), getHeight());
        }
        finally
        {
            g2.dispose();
        }
    }
    /**
     * Redraws this tile because the train on it has arrived or gone (OB-180).  The mark is decided when a tile is
     * painted and `updateImage` repaints only when the image name changed (forcing it with `highlight` would flash
     * every signal and switch touched), so a plain repaint suffices.
     */
    public void refreshCoveredMark()
    {
        if (this.component == null || this.component.isText()) return;

        // repaint() is safe from any thread, and the answer is read inside the paint.
        this.repaint();
    }

    /**
     * The orange a train is drawn in (MT-309): opaque, and the one strongly colored line on an ordinary diagram, since
     * green, red and yellow already mean something else.  A square autonomy will not choose is gray (MT-492).  The
     * solid one of two (`TailColour.SOFT`): the line is drawn in `tailColour()`, which is see-through unless set
     * otherwise.
     */
    public static final Color TRAIN_MARK = org.traincontrol.automationui.DiagramColours.TRAIN;


    /**
     * How solid a square is drawn while the railway refuses it (MT-278): the same device as
     * `TileAnnotation.IGNORED_ALPHA` (0.62), but lower on purpose.  An ignored square is a permanent property of the
     * layout; blocked track is a fact about this minute and must be told apart at a glance.
     */
    public static final float BLOCKED_ALPHA = 0.40f;

    /**
     * An active curved sensor's red arrows, drawn again at full strength over its faded square (OB-400): they say the
     * contact reads occupied, which the fade for a parked train must not wash out.  A straight's fade with its square.
     */
    private void paintCurvedSensorsArrowsWhole(java.awt.Graphics g)
    {
        if (component == null || !component.isFeedback()
            || component.getType() == LayoutDiagramComponent.componentType.FEEDBACK
            || component.getFeedback() == null || !component.getFeedback().isSet()) return;

        Icon icon = getIcon();

        if (!(icon instanceof ImageIcon)) return;

        java.awt.image.BufferedImage arrows = ARROWS.computeIfAbsent(((ImageIcon) icon).getImage(),
            LayoutLabel::redOf);

        java.awt.Insets in = getInsets();

        g.drawImage(arrows, in.left, in.top, null);
    }

    /** The red each tile picture has, worked out once: an active sensor's art draws only its arrows in red. */
    private static final Map<Image, java.awt.image.BufferedImage> ARROWS =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** The picture's red, with the soft edge it fades into white by, and nothing else. */
    private static java.awt.image.BufferedImage redOf(Image picture)
    {
        java.awt.image.BufferedImage art = LayoutDiagramComponent.toBufferedImage(picture);

        java.awt.image.BufferedImage red = new java.awt.image.BufferedImage(art.getWidth(), art.getHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < art.getHeight(); y++)
        {
            for (int x = 0; x < art.getWidth(); x++)
            {
                int argb = art.getRGB(x, y);
                int r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;

                if (r >= 200 && r - gr > 20 && Math.abs(gr - b) <= 8) red.setRGB(x, y, argb);
            }
        }

        return red;
    }

    /**
     * One coach of a train along one road of a square (T2), as a center line, or null where the road is too short to
     * hold one.  Its ends are trimmed by half a gap, so the gaps fall exactly at the joins between squares, where a
     * dash pattern would restart at every square and leave stubs.  `a` and `b` are the road's ends.
     */
    private static java.awt.geom.Line2D coach(int[] a, int[] b, int span)
    {
        double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);

        if (len < 1) return null;

        double ux = dx / len, uy = dy / len;

        // Half a gap at each end, and the round cap's reach inside it, so the drawn coach stops half a gap short
        float band = Math.max(3f, span / 7f);
        double trim = COACH_GAP * span / 2.0 + band / 2.0;

        if (len <= 2 * trim) return null;

        return new java.awt.geom.Line2D.Double(a[0] + ux * trim, a[1] + uy * trim, b[0] - ux * trim, b[1] - uy * trim);
    }

    /** The gap between two coaches, as a share of a square. */
    public static final double COACH_GAP = 0.16;

    /** How a train's body is drawn along the track it lies on. */
    public enum TailStyle
    {
        /** A line edge to edge along each road, as since MT-309 and FR-106. */
        LINE,

        /** Whole coaches, one to a square, the gaps at the joins (T2) - see `coach`. */
        COACHES
    }

    /**
     * The style trains are drawn in: the line by default; the coaches are only ever a choice.
     */
    private static volatile TailStyle tailStyle = TailStyle.LINE;

    /** The style trains are drawn in, asked in one place so a preference can choose it later. */
    public static TailStyle tailStyle()
    {
        return tailStyle;
    }

    /** Chooses how trains are drawn from now on - for that preference, and for a test; null is the default. */
    public static void setTailStyle(TailStyle style)
    {
        tailStyle = style == null ? TailStyle.LINE : style;
    }

    /** How a train's line ends where the train ends. */
    public enum TailEnds
    {
        /** Rounded, stopped short of the square's edge so the round cap lies inside it. */
        ROUNDED,

        /** Run to the square's edge and cut off flat there. */
        SQUARE
    }

    /** How a train's line ends: rounded.  `TailEnds.SQUARE` is the line cut off flat on the square's edge. */
    private static volatile TailEnds tailEnds = TailEnds.ROUNDED;

    /** How a train's line ends, asked in ONE place as `tailStyle` and `tailColour` are. */
    public static TailEnds tailEnds()
    {
        return tailEnds;
    }

    /** Chooses how a train's line ends from now on - for a preference, and for a test; null is the default. */
    public static void setTailEnds(TailEnds ends)
    {
        tailEnds = ends == null ? TailEnds.ROUNDED : ends;
    }

    /** The color a train's body is drawn in. */
    public enum TailColour
    {
        /**
         * The old orange at 55%, the track showing through (`DiagramColours.TRAIN_SEE_THROUGH`); over a running train's
         * own dark gray line, the orange that comes out the same (`DiagramColours.TRAIN_OVER_RUN`).
         */
        SEE_THROUGH(org.traincontrol.automationui.DiagramColours.TRAIN_SEE_THROUGH,
            org.traincontrol.automationui.DiagramColours.TRAIN_OVER_RUN),

        /** A soft orange, solid (`DiagramColours.TRAIN`), the same over a run line. */
        SOFT(org.traincontrol.automationui.DiagramColours.TRAIN, org.traincontrol.automationui.DiagramColours.TRAIN);

        private final Color colour;

        private final Color overTheRun;

        TailColour(Color colour, Color overTheRun)
        {
            this.colour = colour;
            this.overTheRun = overTheRun;
        }

        /** @return the color itself */
        public Color colour()
        {
            return colour;
        }

        /** @return the color over a running train's own run line, which it comes out as `colour` does over track */
        public Color overTheRun()
        {
            return overTheRun;
        }
    }

    /** The color trains are drawn in: see-through by default, and easy to switch. */
    private static volatile TailColour tailColour = TailColour.SEE_THROUGH;

    /** The color trains are drawn in, asked in ONE place so a preference can choose it later, as `tailStyle` is. */
    public static TailColour tailColour()
    {
        return tailColour;
    }

    /** Chooses the color trains are drawn in from now on - for that preference, and for a test; null is the default. */
    public static void setTailColour(TailColour colour)
    {
        tailColour = colour == null ? TailColour.SEE_THROUGH : colour;
    }

    /**
     * Draws what a standing train has done to this square: the gray it has blocked, the orange it is lying on.  The two
     * marks say different things ("train is here" also means "track is blocked"): the wash is under the line.  One line
     * along each covered road, not a wash of the whole square (confusing on double curves), and none where the window
     * names no road.  The viewer only, not the autonomy or diagram editor.
     *
     * @param g the tile's graphics
     */
    private void paintCoveredMark(java.awt.Graphics2D g)
    {
        paintCoveredMark(g, false);
    }

    /**
     * Draws the train's line, as `paintCoveredMark(g)` does, or over a run line: in the color that comes out there as
     * the line does over plain track.
     */
    private void paintCoveredMark(java.awt.Graphics2D g, boolean overTheRun)
    {
        if (component == null || component.isText() || !trainsShown.getAsBoolean()) return;

        // The refusal is drawn in `paintComponent`, not here (MT-278): a fade must be applied to the icon as it is
        // painted.  What is left here is the train mark, at full strength.
        java.util.List<org.traincontrol.automationui.TilePorts.Route> roads = coveredRoads();

        if (roads.isEmpty()) return;

        int span = Math.min(getWidth(), getHeight());

        ImageUtil.antialias(g);

        float width = Math.max(3f, span / 7f);

        g.setStroke(new java.awt.BasicStroke(width, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

        // Whichever color the diagram is set to draw - see `tailColour`
        g.setColor(overTheRun ? tailColour().overTheRun() : tailColour().colour());

        // One shape for all of the square's roads, drawn once: a see-through color drawn road by road lays itself twice
        // where two roads meet and comes out darker there.
        java.awt.geom.Path2D.Double body = new java.awt.geom.Path2D.Double();

        for (org.traincontrol.automationui.TilePorts.Route road : roads)
        {
            int[] a = org.traincontrol.automationui.TileAnnotation.midpoint(
                road.getA(), getWidth(), getHeight());

            int[] b = org.traincontrol.automationui.TileAnnotation.midpoint(
                road.getB(), getWidth(), getHeight());

            if (a == null || b == null) continue;

            // A coach or a line edge to edge, whichever the diagram is set to draw - see `tailStyle`
            if (tailStyle() == TailStyle.COACHES)
            {
                java.awt.geom.Line2D coach = coach(a, b, span);

                if (coach != null) body.append(coach, false);
            }
            else
            {
                // Rounded where the train ends: the square clips what it draws, so a round cap at the train's last side
                // would be cut off flat.  At a side the train goes no further than, the line stops short by what its
                // cap needs; between squares it runs to the edge, since two see-through caps laid over each other would
                // show.  Or square, edge to edge, where `tailEnds` says so.
                boolean round = tailEnds() == TailEnds.ROUNDED;

                double[] from = round ? endOf(a, b, road.getA(), width / 2) : new double[] {a[0], a[1]};
                double[] to = round ? endOf(b, a, road.getB(), width / 2) : new double[] {b[0], b[1]};

                body.append(new java.awt.geom.Line2D.Double(from[0], from[1], to[0], to[1]), false);
            }
        }

        g.draw(body);
    }

    /**
     * Where the line along a road stops at one of its sides (the rounded end): at the side where the train runs on into
     * the square beside, otherwise short of it by what the round cap needs to lie inside this square (more at a slant)
     * and a pixel and a half more.  `radius` is the cap's, half the line's width.
     */
    private double[] endOf(int[] end, int[] other, org.traincontrol.automationui.TilePorts.Side side, double radius)
    {
        if (runsOnAcross(side)) return new double[] {end[0], end[1]};

        double dx = other[0] - end[0], dy = other[1] - end[1];
        double length = Math.hypot(dx, dy);

        if (length <= 0) return new double[] {end[0], end[1]};

        // How squarely the road meets the side: the cap's center has to be a radius in from the side, measured across it
        double square = side == org.traincontrol.automationui.TilePorts.Side.N
            || side == org.traincontrol.automationui.TilePorts.Side.S ? Math.abs(dy) / length : Math.abs(dx) / length;

        double inset = Math.min(length / 2, (radius + 1.5) / Math.max(square, 0.25));

        return new double[] {end[0] + dx / length * inset, end[1] + dy / length * inset};
    }

    /**
     * Whether the train's line runs on across a side into the square beside (the rounded end): that square carries a
     * covered road through the facing side.  A square this page does not draw is taken to carry it on.
     */
    private boolean runsOnAcross(org.traincontrol.automationui.TilePorts.Side side)
    {
        if (square == null || tcUI == null || side == null) return true;

        org.traincontrol.automationui.TileGraph.TileKey beside = besideOn(square, side);

        if (beside == null) return true;

        java.util.Set<org.traincontrol.automationui.TileGraph.RouteId> there = tcUI.coveredRoutesAt(beside);

        if (there.isEmpty()) return false;

        org.traincontrol.base.LayoutDiagram page = tcUI.getModel() == null ? null : tcUI.getModel().getLayout(square.getPage());

        LayoutDiagramComponent next = page == null ? null : page.getComponent(beside.getX(), beside.getY());

        if (next == null) return true;

        org.traincontrol.automationui.TilePorts.Side back = side.opposite();

        for (org.traincontrol.automationui.TilePorts.Route road : routesOf(next, there))
        {
            if (road.getA() == back || road.getB() == back) return true;
        }

        return false;
    }

    /** The square beside one across a side, on the same page, or null for no side. */
    public static org.traincontrol.automationui.TileGraph.TileKey besideOn(org.traincontrol.automationui.TileGraph.TileKey square,
        org.traincontrol.automationui.TilePorts.Side side)
    {
        return square == null ? null : square.beside(side);
    }

    /**
     * The shape to fade when only some of this square's roads are covered, or null to fade all of it.  Only where the
     * square carries more than one road (a double curve) and the train is on fewer; elsewhere the square is the road.
     * A band along the road, since the tile is an image with no region this code can name, using the geometry
     * `coveredRoads` draws the orange line with so the two describe the same roads.
     */
    private java.awt.Shape theRoadToFade()
    {
        if (component == null || tcUI == null || square == null) return null;

        if (getWidth() <= 0 || getHeight() <= 0) return null;

        // The blocked roads, not the train's (OB-208, OB-280): the fade is the gray, which is its own answer.  Asking
        // the orange here would fade the whole of a double curve the train is not on.
        java.util.Set<org.traincontrol.automationui.TileGraph.RouteId> roads = tcUI.blockedRoutesAt(square);

        if (roads.isEmpty()) return null;

        java.util.List<org.traincontrol.automationui.TilePorts.Route> covered = routesOf(roads);

        if (covered.isEmpty()) return null;

        // Every road this square has, in the state the blocked one was found in (the one the reduction walked it in).
        // A switch has one road per state and is not what this is for.
        java.util.List<org.traincontrol.automationui.TilePorts.Route> all =
            org.traincontrol.automationui.TilePorts.ports(component.getType(),
                component.getOrientation(), roads.iterator().next().getState());

        // Nothing to separate: one road, or every road is blocked.
        if (all.size() < 2 || covered.size() >= all.size()) return null;

        java.awt.geom.Area shape = new java.awt.geom.Area();

        // Wide enough to cover the drawn rail, and no wider: a band reaching the corners would fade the other road
        // where the two cross.
        float band = Math.max(6f, Math.min(getWidth(), getHeight()) / 3f);

        for (org.traincontrol.automationui.TilePorts.Route road : covered)
        {
            int[] a = org.traincontrol.automationui.TileAnnotation.midpoint(
                road.getA(), getWidth(), getHeight());

            int[] b = org.traincontrol.automationui.TileAnnotation.midpoint(
                road.getB(), getWidth(), getHeight());

            if (a == null || b == null) return null;

            shape.add(new java.awt.geom.Area(new java.awt.BasicStroke(band,
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
                .createStrokedShape(new java.awt.geom.Line2D.Double(a[0], a[1], b[0], b[1]))));
        }

        return shape.isEmpty() ? null : shape;
    }

    /** The roads of this square a standing train is lying across, as the sides they join. */
    private java.util.List<org.traincontrol.automationui.TilePorts.Route> coveredRoads()
    {
        if (edit || square == null || tcUI == null || component == null) return new java.util.ArrayList<>();

        return routesOf(tcUI.coveredRoutesAt(square));
    }

    /** Roads of this square, named by the reduction, as the sides they join: one translation for both marks. */
    private java.util.List<org.traincontrol.automationui.TilePorts.Route> routesOf(
        java.util.Set<org.traincontrol.automationui.TileGraph.RouteId> roads)
    {
        return routesOf(component, roads);
    }

    /**
     * The same, for the roads of any square: this one's, or the square beside's, which decides whether this line ends
     * rounded.
     */
    private static java.util.List<org.traincontrol.automationui.TilePorts.Route> routesOf(LayoutDiagramComponent component,
        java.util.Set<org.traincontrol.automationui.TileGraph.RouteId> roads)
    {
        java.util.List<org.traincontrol.automationui.TilePorts.Route> out =
            new java.util.ArrayList<>();

        if (component == null || roads == null) return out;

        for (org.traincontrol.automationui.TileGraph.RouteId road : roads)
        {
            // A route tile's roads are not in the port map (OB-279): it carries whatever the track beside it carries,
            // named by the sides they join, so indexing the port map found nothing.
            if (org.traincontrol.automationui.TilePorts.isTransparent(component.getType()))
            {
                org.traincontrol.automationui.TilePorts.Route through =
                    org.traincontrol.automationui.TileGraph.transparentRouteOf(road);

                if (through != null && through.getA() != through.getB()) out.add(through);

                continue;
            }

            java.util.List<org.traincontrol.automationui.TilePorts.Route> routes =
                org.traincontrol.automationui.TilePorts.ports(
                    component.getType(), component.getOrientation(), road.getState());

            // A route the port map no longer has: the diagram was edited under an older set; the mark returns at the
            // next refresh.
            if (road.getIndex() < 0 || road.getIndex() >= routes.size()) continue;

            org.traincontrol.automationui.TilePorts.Route route = routes.get(road.getIndex());

            // A stub - one side twice - is a portal, and has no line to draw.
            if (route.getA() == route.getB()) continue;

            out.add(route);
        }

        return out;
    }

    /**
     * Draws this tile's train onto the container, above the captions (OB-159): a tile is behind every station caption,
     * so the container paints its children and then asks each tile for this.  Translated to this tile and not clipped
     * to it (MT-642), since on a curve the icon reaches onto the next tiles.
     */
    public void paintTrainOverCaptions(java.awt.Graphics g)
    {
        TileOverlay overlay = autonomyOverlay;

        if (overlay == null || overlay.isBlank() || !overlay.hasTrain() || !trainsShown.getAsBoolean()) return;

        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

        try
        {
            g2.translate(getX(), getY());

            overlay.paintTrain(g2, getWidth(), getHeight(), theTrainsRoadCentre(overlay, annotation));
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * Whether this square shows no train though trains are shown: the picture is between a wipe and the next one, or
     * not yet made.  A press then may still be on a train the setup says stands here (FR-160).
     */
    public boolean trainPictureIsMissing()
    {
        TileOverlay overlay = autonomyOverlay;

        return trainsShown.getAsBoolean() && (overlay == null || overlay.isBlank() || !overlay.hasTrain());
    }

    /**
     * Whether the train `paintTrainOverCaptions` draws for this square covers a point (OB-354): a click on it can land
     * on a caption or on the next square.  The point is in this square's coordinates.
     */
    public boolean trainDrawnAt(java.awt.Point at)
    {
        TileOverlay overlay = autonomyOverlay;

        if (at == null || overlay == null || overlay.isBlank() || !overlay.hasTrain() || !trainsShown.getAsBoolean())
        {
            return false;
        }

        java.awt.Shape area = overlay.trainArea(getWidth(), getHeight(), theTrainsRoadCentre(overlay, autonomyAnnotation));

        return area != null && area.contains(at);
    }

    /**
     * What of this square's station icon spills onto the squares beside it, drawn by the diagram once every square is
     * drawn - `TileAnnotation.paintSpill`.  Answers whether anything was drawn: a spill reaching what is being
     * repainted.
     */
    public boolean paintStationSpill(java.awt.Graphics g)
    {
        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        if (!spills(annotation) || !isVisible()) return false;

        java.awt.Rectangle clip = g.getClipBounds();

        if (clip != null && !clip.intersects(spillReach())) return false;

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

        try
        {
            g2.translate(getX(), getY());

            annotation.paintSpill(g2, getWidth(), getHeight());
        }
        finally
        {
            g2.dispose();
        }

        return true;
    }

    /**
     * This square's arrows back over a neighbor's station that spilled onto it (`TileAnnotation.paintArrowsAgain`),
     * asked by the diagram once the spills are drawn.  Only where another square's spill reaches this one.
     */
    void paintArrowsOverSpill(java.awt.Graphics g, java.util.Map<LayoutLabel, java.awt.Rectangle> spilt)
    {
        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        if (annotation == null || !isVisible()) return;

        java.awt.Rectangle mine = getBounds();

        java.awt.Rectangle clip = g.getClipBounds();

        if (clip != null && !clip.intersects(mine)) return;

        boolean under = false;

        for (java.util.Map.Entry<LayoutLabel, java.awt.Rectangle> spill : spilt.entrySet())
        {
            if (spill.getKey() != this && spill.getValue().intersects(mine)) under = true;
        }

        if (!under) return;

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create(getX(), getY(), getWidth(), getHeight());

        try
        {
            annotation.paintArrowsAgain(g2, getWidth(), getHeight());
        }
        finally
        {
            g2.dispose();
        }
    }

    /**
     * And the squares around it, where this square's station spills onto them: the diagram paints a square's neighbors
     * only where asked, so repainting this square alone would leave the old spill on them.
     */
    @Override
    public void repaint(long tm, int x, int y, int width, int height)
    {
        java.awt.Container around = getParent();

        if (around != null && spills(autonomyAnnotation))
        {
            java.awt.Rectangle reach = spillReach();

            around.repaint(tm, reach.x, reach.y, reach.width, reach.height);

            return;
        }

        super.repaint(tm, x, y, width, height);
    }

    /** What this square's spill covers, in the diagram's coordinates, or null - `TileAnnotation.spillShape`. */
    java.awt.Shape spillShapeOnTheDiagram()
    {
        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        java.awt.Shape shape = annotation == null ? null : annotation.spillShape(getWidth(), getHeight());

        return shape == null ? null
            : java.awt.geom.AffineTransform.getTranslateInstance(getX(), getY()).createTransformedShape(shape);
    }

    /** Whether an annotation's station spills past its square (`TileAnnotation.spillsOverItsSquare`). */
    private static boolean spills(org.traincontrol.automationui.TileAnnotation annotation)
    {
        return annotation != null && annotation.spillsOverItsSquare();
    }

    /** As far as a spill reaches, in the diagram's coordinates: half a square past each edge - a curve's icon less. */
    java.awt.Rectangle spillReach()
    {
        return new java.awt.Rectangle(getX() - getWidth() / 2, getY() - getHeight() / 2, 2 * getWidth(), 2 * getHeight());
    }

    /**
     * The middle of the train's own road on this square, for its icon (MT-642), as x and y within the square, or null
     * where nothing says.
     */
    private int[] theTrainsRoadCentre(TileOverlay overlay, org.traincontrol.automationui.TileAnnotation annotation)
    {
        if (component != null)
        {
            try
            {
                int[] own = org.traincontrol.automationui.TileAnnotation.trackCentreOf(
                    org.traincontrol.automationui.TilePorts.ports(component.getType(), component.getOrientation(), 0),
                    overlay.trainSides(), getWidth(), getHeight());

                if (own != null) return own;
            }
            catch (RuntimeException noPorts)
            {
                // a square with no track of its own: the annotation says
            }
        }

        return annotation == null ? null : annotation.trackCentreOf(overlay.trainSides(), getWidth(), getHeight());
    }


    /** Refreshes the tile's image. */
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

    /**
     * Whether clicking this accessory would turn protection off at a platform somebody is standing at.  Both halves,
     * since either alone is the wrong question: `protectsAnOccupiedSquare` knows nothing of which way the signal is
     * going, and the aspect knows nothing of what is standing where.  The route door asks both at
     * `MarklinRoute.heldReason`.
     */
    private static boolean aboutToClearProtection(TrainControlUI tcUI, Accessory accessory)
    {
        if (accessory == null || tcUI == null || tcUI.getModel() == null) return false;

        // Asked once, not built: this is the switching thread, and Unload can clear the model between asking whether
        // there is a railway and asking for it
        final org.traincontrol.automation.Layout running = tcUI.getModel().getAutoLayoutIfLoaded();

        if (running == null) return false;

        // And only while autonomy is running, as the dialog that can authorize the click asks: with autonomy idle no
        // dialog could be shown, `warnedAboutProtection` would always be false, and the worker would refuse every click
        // that sets a protecting signal green over a square the model believes occupied.
        if (!tcUI.getModel().isAutonomyRunning()) return false;

        // A tile toggles, so the click is about to command green exactly when the accessory is not straight now.  The
        // rest of the question lives on `Layout.clearsProtection`, which the switch keyboard asks too.
        return running.clearsProtection(accessory, !accessory.isStraight());
    }
}
