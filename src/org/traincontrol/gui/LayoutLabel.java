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
     * Runs one diagram switching action off the event thread.
     *
     * execute rather than submit: submit captures whatever the action throws into a Future, and there is no Future
     * kept here to read it back from, so an escaping exception would disappear with no sign of it anywhere.  With
     * execute it reaches the default handler and prints, as it does on the event thread.
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
     * What autonomy says is happening on this square, or null.  Painted over the icon rather than baked in: the icon
     * cache is shared by every tile of a type, and the transient highlight already owns the one slot it restores from.
     * Volatile: written from the monitor's publish, read while painting.
     */
    private volatile TileOverlay autonomyOverlay;

    /**
     * Whether this square draws the trains on it, asked as it paints: the autonomy editor's Show Trains switch
     * (FR-119).
     */
    private java.util.function.BooleanSupplier trainsShown = () -> true;

    /**
     * Has this square ask the given switch whether to draw the trains on it (FR-119).
     *
     * @param shown asked as the square paints
     */
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
        // (testACaptionNeverMovesAnythingElse).  The dummy column is the space an overflowing text label is absorbed
        // into.
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

    /**
     * A square, given the editor's grid setting by the grid that makes it, asked once a build.
     *
     * @param c the component on the square, or null
     * @param parent the grid's master
     * @param size the square's size
     * @param tcUI the main window
     * @param edit whether the square is in an editor
     * @param gridLines the editor's grid setting, or null to ask it
     */
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
            // same rule, so the toggle reaches both (FR-006).  Off, tiles sit flush as in the viewer and the hover
            // outline reserves nothing.
            this.setBorder(LayoutEditor.restingBorder(false,
                parent instanceof LayoutEditor && ((LayoutEditor) parent).isAutonomyMode(),
                gridLines != null ? gridLines : LayoutEditor.showGrid()));
        }
        
        // Every square on the main window reports when the pointer is over it: the shortcuts that move a locomotive
        // from square to square need to know which is meant, and the pointer is the only cursor.  On every square, so a
        // shortcut aimed at plain track is ignored rather than acted on at the last station hovered.
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
                                    // Asked whether or not the power was on: the power-off gesture is what somebody
                                    // makes after an emergency stop, with trains standing and autonomy loaded, and
                                    // "power on and proceed" must not clear protection at an occupied platform
                                    // unmentioned.  Remembered, because the worker below re-asks and must not refuse a
                                    // click already accepted.
                                    boolean askedAboutProtection = false;

                                    if (tcUI.getModel().hasAutoLayout() && tcUI.getModel().isAutonomyRunning())
                                    {
                                        Collection<Accessory> activeAccs = tcUI.getModel().getAutoLayout().getActiveAccs();
                                        
                                        // The protecting-signal half: a platform's protecting signal is usually driven
                                        // by occupancy, not by `getActiveAccs`, so a route setting it green is refused
                                        // and clicking it green by hand must be too.  Asked of the layout so this and
                                        // MarklinRoute.heldReason cannot drift apart.  Only the green direction:
                                        // setting it red is what protection itself does.  The test is about the
                                        // command, not the method: `Accessory.doSwitch()` for a signal or lamp, but a
                                        // three-way's `execSwitching` drives its two accessories directly.  Every drive
                                        // commanded green is on an accessory whose `isStraight()` is false or was
                                        // already green, so a three-way gets no false negative and no false positive.
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
                                                tcUI,
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
                                            // now, with a train on it.  Escape must not throw the turnout under that
                                            // train.
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

                                    // The same question for a route tile.  It has no accessory of its own, so
                                    // `activeAccs.contains(null)` is false; a route sets several accessories.  Every
                                    // route this component can hold is a MarklinRoute, the only kind that knows the
                                    // autonomy graph.
                                    final org.traincontrol.marklin.MarklinRoute onTile =
                                        c.isRoute()
                                            && c.getRoute() instanceof org.traincontrol.marklin.MarklinRoute
                                        ? (org.traincontrol.marklin.MarklinRoute) c.getRoute() : null;

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
                                                    // Bounded, and the switching goes ahead either way: the power state
                                                    // is set only by the Central Station's echo, and a wait with no
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
                                        // power still off, which is the case this feature exists for (recovering a
                                        // turnout after an emergency stop).
                                        if (onTile != null)
                                        {
                                            // Off this worker before anything can block on a person: submitSwitching
                                            // has one thread shared by every tile, so a dialog left standing would
                                            // freeze them all.  The power is already on, so the handoff costs nothing.
                                            new Thread(() ->
                                            {
                                                // One question, three answers: a conflict that cleared between the
                                                // check and the dialog must not come back as "override", which would
                                                // run the route with every guard off unasked.
                                                TrainControlUI.RouteConflict answer =
                                                    tcUI.askAboutRouteConflict(onTile, tcUI, null);

                                                if (answer == TrainControlUI.RouteConflict.OVERRIDE)
                                                {
                                                    onTile.execRouteOverridingConflicts();

                                                    return;
                                                }

                                                if (answer == TrainControlUI.RouteConflict.REFUSED)
                                                {
                                                    return;
                                                }

                                                component.execSwitching();
                                            }).start();

                                            return;
                                        }

                                        // And ask again, here, where the command goes.  The question above was answered
                                        // on the event thread; this runs later on the switching thread, and
                                        // `Layout.refreshOneSignal` can drive the same accessory from an occupancy
                                        // change in that gap: a train arrives and protection sets the signal red, then
                                        // this command would set it green again over an occupied platform.  Refused
                                        // rather than re-asked, since asking from here would hold the one switching
                                        // thread.  Skipped for a click the operator was already warned about and
                                        // accepted.
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
     * @return 
     */
    public boolean isParentVisible()
    {
        // No parent counts as not visible, which removes the label from the accessory or feedback that holds it.  A
        // grid built for an export has a null master, and a throw here would abandon every tile after it.
        return this.parent != null && this.parent.isVisible();
    }

    /**
     * Whether this label belongs to the same window as another.  Used on a grid rebuild to tell a replaced generation
     * from a label of a different window showing the same page; the main window caches a page's grid, so its labels are
     * legitimately detached for a while.
     *
     * @param other
     * @return
     */
    public boolean sharesWindowWith(LayoutLabel other)
    {
        return other != null && other.parent == this.parent;
    }

    /**
     * Drops the labels that a newly registered one replaces.  Nothing else can: updateTiles' prune asks whether a
     * label's parent is visible, which on the main window (the tab strip) never fails, so every grid rebuild would
     * leave a page of dead labels for each accessory to walk on every Central Station message.
     *
     * A registered label is dropped only when all three hold: SAME SQUARE.  The device collections are keyed by device
     * and one accessory is drawn on several squares of a page; LayoutGrid registers every label before attaching the
     * container, so each arrival would otherwise evict its siblings.  SAME WINDOW.  The main window caches a page's
     * grid, so its labels are detached but alive while another page shows.  NOT DISPLAYABLE.  A replaced grid's labels
     * were removed from a realized window and have no peer.  The arriving label is never judged; it may not be attached
     * yet.
     *
     * Where the square cannot be established the label is kept: a missed prune is a slow leak, a wrong one a dead tile.
     *
     * @param registered the labels already registered with the device
     * @param arriving the label being registered now
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

    /**
     * Which page of the diagram this square is on.  Asking the main window is wrong in a popup, which shows a page of
     * its own: a right-click there would open the menu for whatever the main window had selected, or throw the sensor.
     */
    private String autonomyPage;

    /**
     * Where this label sits on its page.  Told to it rather than read off the component, because a label with no
     * component still has a square - those are the squares a station's name is drawn on.
     *
     * @param page the page
     * @param x the column
     * @param y the row
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
                // building the labels and the pool picking the work up.  Only a first draw counts, never an update: a
                // running layout's state-keyed image misses (every switch thrown, every s88 event) would keep the count
                // from ever being at rest, and a grid would wait behind its spinner until the failsafe fired.
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
                        // captured, and setting that back later would draw the tile as it was (a switch in the position
                        // it was thrown out of).  Every replacement passes here.  (The accessory highlight below skips
                        // a switch thrown by clicking its own flashed tile, and any square the route editor flashes
                        // that is not a switch or signal.)
                        discardFlash();

                        this.setIcon(lastIcon);

                        // Neither mark is an icon (MT-309): the orange train line and the gray wash over blocked track
                        // are painted in `paintCoveredMark` over whatever the icon is, so nothing swaps the icon for a
                        // grayed copy, and a tile flashing yellow still shows both.
                        
                        // Temporarily highlight changes when they happen from a route/CS/keyboard command
                        if (!edit && (this.component.isSignal() || this.component.isSwitch()) && hadIcon && (System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
                        {
                            // The one before it is stopped first (OB-333, OB-334), before this highlight's yellow goes
                            // on: nulling the field would leave the old timer counting down to `setIcon(lastIcon)` over
                            // a later highlight or flash, and ending a highlight puts the plain picture back, which
                            // after the yellow would leave a square changed twice inside the hold (autonomy does) with
                            // no yellow.
                            endAccessoryHighlight();

                            // Both of these run on the EDT: this block already does, and a Swing Timer
                            // fires there too.  The overlay and the restore used to be applied from a
                            // raw thread, mutating a Swing component off the EDT - the one place in this
                            // class that did not marshal its work.
                            this.setIcon(ImageUtil.addHighlightOverlay((ImageIcon) this.getIcon()));

                            final javax.swing.Timer[] mine = new javax.swing.Timer[1];

                            mine[0] = new javax.swing.Timer(HIGHLIGHT_DURATION, (restoreEvent) ->
                            {
                                if ((System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
                                {
                                    // Straight back to the tile, and the train comes back with it: the marks are
                                    // painted over the icon (MT-309), so they are not in the icon to lose.
                                    this.setIcon(lastIcon);

                                    // Counted so a test can see how many restores fired, telling a superseded timer
                                    // stopped from one left to go off.
                                    accessoryRestores++;
                                }

                                // No longer outstanding: the field says whether a restore is armed.  Only if it is
                                // still this timer: a three-way drives twice, and a second highlight may have replaced
                                // it.
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
                    // every switch, signal and sensor event of a run.  The diagram's panel draws overlapping children
                    // together (OB-172) and the neighborhood is redrawn as a train's icon is (MT-642).
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
     * Refreshes the tile's image
     * @param highlight
     */
    /**
     * Whether this tile belongs to the diagram editor rather than an operating view: the editor draws autonomy from the
     * panel beside it, the operating views from the setup on disk, so one must not publish over the other.
     * @return
     */
    public boolean isEditMode()
    {
        return edit;
    }

    /**
     * Flashes this tile the same yellow the diagram uses when a route or the Central Station changes something.  Same
     * overlay and duration deliberately: users already read it as "look here", and a border would disturb the tile's
     * own border, which in the editor is the grid line.
     */
    public void flashHighlight()
    {
        flashHighlight(org.traincontrol.util.ImageUtil.HIGHLIGHT, HIGHLIGHT_DURATION);
    }

    /**
     * The same flash, in a color and for a length the caller chooses: the route editor lights what a route commands in
     * the ordinary yellow and what it merely checks in orange, and holds them long enough to look from window to
     * diagram.
     *
     * @param wash the color to lay over the tile
     * @param holdMs how long to hold it
     */
    public void flashHighlight(java.awt.Color wash, int holdMs)
    {
        // An empty square carries an EmptyIcon, which is not null and not an ImageIcon, so the cast below would throw;
        // it would escape mid-action because the menu's flash sits outside its own try/catch.
        if (!(this.getIcon() instanceof ImageIcon)) return;

        // The plain icon is captured once and kept until the flash ends: capturing it on every call would let a second
        // flash record the already-highlighted icon as the one to restore, and the tile would stay yellow.  An
        // accessory highlight that is up is ended first, so what is captured is the tile's own picture.
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

    /**
     * How many accessory highlights have ended by putting the tile's picture back.
     *
     * @return the count since this label was made
     */
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

    /**
     * Whether an accessory highlight is outstanding on this tile.
     *
     * @return true while the yellow wash of a state change will still be taken off by its own timer
     */
    public boolean isAccessoryHighlightOutstanding()
    {
        return accessoryHighlight != null;
    }

    /**
     * Ends an accessory highlight at once, putting back the picture it was laid over.
     *
     * A no-op when none is up.  Public for the test that drives the two highlights against each other.
     */
    public void endAccessoryHighlight()
    {
        if (accessoryHighlight == null) return;

        accessoryHighlight.stop();

        if (lastIcon != null) this.setIcon(lastIcon);

        accessoryHighlight = null;
    }

    /**
     * Gives up a flash without putting its picture back, because something truer has just been drawn.
     */
    private void discardFlash()
    {
        if (flashTimer == null) return;

        flashTimer.stop();

        flashRestore = null;
        flashTimer = null;
    }

    /**
     * Ends a flash now, rather than when its timer runs out: a walk from square to square (Mass Assign Lengths, Name
     * Everything) would otherwise leave the square just left yellow for its full hold (MT-454).  Does nothing when no
     * flash is running.
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
     * Sets what autonomy is showing on this square, repainting if it changed.  Repaints here rather than through
     * updateImage, which returns early unless the icon name changed, and autonomy state does not change it.
     *
     * @param overlay what to show, or null for nothing
     */
    public void setAutonomyOverlay(TileOverlay overlay)
    {
        TileOverlay effective = overlay == null || overlay.isBlank() ? null : overlay;

        if (effective == null ? autonomyOverlay == null : effective.equals(autonomyOverlay)) return;

        TileOverlay was = autonomyOverlay;

        autonomyOverlay = effective;

        // Nothing lifted for a running train (OB-259): the diagram's panel paints every train after all its components
        // (OB-159), so the square keeps its place behind its captions and address label.

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
     * @return what autonomy is showing here, or null
     */
    public TileOverlay getAutonomyOverlay()
    {
        return autonomyOverlay;
    }

    /**
     * Sets what the autonomy editor is showing on this square (directions, lengths, selection), repainting if it
     * changed.  Like the overlay, none of it changes the icon name, so updateImage would show nothing.
     *
     * @param annotation what to show, or null for nothing
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

        // And the tiles around it where a train stands: the annotation can say where the icon goes, and the icon
        // reaches past this tile.
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
     * Draws the tile, then whatever autonomy is saying about it.  After super, so it lands over the icon and never
     * touches setIcon, which lets it coexist with the transient highlight.  Nothing is lifted over the captions or
     * address labels: the diagram's panel paints every train after all its components (OB-159), so a tile keeps its
     * place behind them (OB-259).
     *
     * @param g
     */
    @Override
    protected void paintComponent(java.awt.Graphics g)
    {
        // The square itself, faintly, where the railway refuses it (Adam, MT-278 item 1: "I want the shading to instead
        // be the same tile with more transparency exactly like what happens when edges are locked in autonomy mode.").
        // On the icon rather than beside it: the composite is set before `super.paintComponent` and undone after, which
        // is what the throwaway Graphics is for.  Everything autonomy draws on top is painted at full strength
        // afterwards, since those are about this instant and the fade is about the track underneath.
        boolean refused = !edit && square != null && tcUI != null && tcUI.isTrackBlocked(square);

        if (refused)
        {
            // One road of a double curve, where that is what is covered: narrowing the extent (OB-207) spares tiles the
            // train never reaches, but a double curve is one tile with two roads and the wash fades the whole square.
            // Null everywhere else: on a tile with one road the square is the road.
            java.awt.Shape road = theRoadToFade();

            if (road != null)
            {
                // The rest of the tile at full strength first: the fade is the tile drawn at reduced alpha, so painting
                // only the road would leave the rest of the opaque tile unpainted.
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
        }
        else
        {
            super.paintComponent(g);
        }

        TileOverlay overlay = autonomyOverlay;
        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        // The train first, under everything autonomy draws (MT-309): it says where a train is, while the arrows (where
        // trains may go) and the run line (where one is heading) are more urgent and go on top, as with the annotation
        // and overlay below.  Except under a run line, where it is drawn again over the line (FR-106, below).
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
            // The static layer first, and the running one over it: the running state is a line that covers only the
            // rails it claims, and where they meet the path a train is actually taking is the more urgent.
            //
            // Told whether the tile is already faint (OB-212; Adam: "keep one level of opacity, dont stack."):
            // `refused` drew the art at `BLOCKED_ALPHA`, and the annotation's own wash would compound with it.  It is
            // the region already faded, not whether any was: on a double curve `theRoadToFade` fades one road, and a
            // tile-wide "yes" would skip the wash under both roads' arrows.
            java.awt.Shape faded = !refused ? null
                : (theRoadToFade() == null
                    ? new java.awt.Rectangle(0, 0, getWidth(), getHeight()) : theRoadToFade());

            if (annotation != null) annotation.paint(g2, getWidth(), getHeight(), faded);

            // Told where the track runs, so a run that ends here stops on the rail rather than mid-square (OB-026); the
            // annotation holds the sides this tile's route uses.
            if (overlay != null)
            {
                // Told whether the tile is already faint (MT-375): on a square both blocked and held the two fades
                // would compound into a tone difference between neighbors.  Adam ruled (MT-373) that one gray covers
                // both reasons.
                overlay.paint(g2, getWidth(), getHeight(),
                    annotation == null ? null : annotation.trackCentre(getWidth(), getHeight()),
                    faded);
            }

            // And the train's tail over the run line (FR-106; Adam: "make the tail visible at all times"): a running
            // train's tail lies on the road just driven, which the run line draws as reached, so drawn first it would
            // be covered for the whole run.  Only where a run line lies: elsewhere it would lie twice over itself and
            // come out a second, lighter orange.
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

            // And the badge back on top of the line (MT-076): a station is where the train is going and the line is how
            // it gets there, so burying the landmark loses the thing being watched (Adam: "I like being able to see
            // progress - keep them on top after being reached.").  Only where there is an overlay to have covered it,
            // so a plain diagram paints it once.
            if (overlay != null && annotation != null)
            {
                annotation.paintBadgeOverRun(g2, getWidth(), getHeight());
            }
        }
        finally
        {
            g2.dispose();
        }
    }
    /**
     * Redraws this tile because the train on it has arrived or gone (OB-180).  The mark is decided when a tile is
     * painted, and moving a train changes which squares its tail lies across and nothing else about them.
     * `updateImage` cannot do this: it repaints only when the image name changed, and forcing it with `highlight` would
     * flash every signal and switch touched.  A plain repaint suffices because the marks are painted over the icon
     * (MT-309).
     */
    public void refreshCoveredMark()
    {
        if (this.component == null || this.component.isText()) return;

        // repaint() is safe from any thread, and the answer is read inside the paint.
        this.repaint();
    }

    /**
     * The orange a train is drawn in (MT-309).  Adam: *"we need to draw a line (let's say in orange) to show that the
     * train is there."*  Opaque, and the one strongly colored line on an ordinary diagram: green and red already mean
     * "trains may run this way" and "they may not", and yellow is the transient flash.
     *
     * It is also the orange for a square autonomy does not choose (Adam: *"make the orange of the lines match the
     * orange of inactive stations"*), so a diagram carrying both reads them as one color.  A parking berth is gray
     * (FR-103).
     *
     * **The solid one of two** (`TailColour.SOFT`): the line is drawn in `tailColour()`, which is see-through unless
     * set otherwise.
     */
    public static final Color TRAIN_MARK = org.traincontrol.automationui.DiagramColours.TRAIN;


    /**
     * How solid a square is drawn while the railway refuses it (Adam, MT-278 item 1): painted faintly, the same device
     * as `TileAnnotation.IGNORED_ALPHA` (0.62), but lower on purpose.  An ignored square is a permanent property of the
     * layout; blocked track is a fact about this minute among unblocked squares and must be told apart at a glance.
     */
    public static final float BLOCKED_ALPHA = 0.40f;

    /**
     * One coach of a train, along one road of a square (T2 of the look's train tails).  Whole coaches, one to a square:
     * a dash pattern restarts at every square and leaves a stub wherever a road is not a whole number of dashes long (a
     * curve's chord is about seven-tenths of a straight one).  Here every road is one coach, its ends trimmed by half a
     * gap, so the gaps fall exactly at the joins between squares.
     *
     * @param g the graphics, its color and stroke set by the caller
     * @param a one end of the road, a side's midpoint
     * @param b the other end
     * @param span the square's size
     */
    public static void paintCoach(java.awt.Graphics2D g, int[] a, int[] b, int span)
    {
        java.awt.geom.Line2D coach = coach(a, b, span);

        if (coach != null) g.draw(coach);
    }

    /** The coach `paintCoach` draws, as a center line, or null where the road is too short to hold one. */
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

        /** Whole coaches, one to a square, the gaps at the joins (T2) - see `paintCoach`. */
        COACHES
    }

    /**
     * The style trains are drawn in: the line (Adam, having seen both: "revert back to the original occupied line
     * shape").
     */
    private static volatile TailStyle tailStyle = TailStyle.LINE;

    /**
     * The style trains are drawn in, asked in one place so a preference can choose it later: every square follows on
     * its next paint.
     *
     * @return how a train's body is drawn
     */
    public static TailStyle tailStyle()
    {
        return tailStyle;
    }

    /**
     * Chooses how trains are drawn from now on - for that preference, and for a test.
     *
     * @param style the style, null for the default
     */
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

    /**
     * How a train's line ends: rounded.  `TailEnds.SQUARE` goes back to the line cut off flat on the square's edge
     * (Adam: "make it easy to revert to the old, non rounded orange train tails").
     */
    private static volatile TailEnds tailEnds = TailEnds.ROUNDED;

    /**
     * How a train's line ends, asked in ONE place as `tailStyle` and `tailColour` are.
     *
     * @return how the line ends where the train ends
     */
    public static TailEnds tailEnds()
    {
        return tailEnds;
    }

    /**
     * Chooses how a train's line ends from now on - for a preference, and for a test.
     *
     * @param ends how, null for the default
     */
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

        /**
         * @return the color itself
         */
        public Color colour()
        {
            return colour;
        }

        /**
         * @return the color over a running train's own run line, which it comes out as `colour` does over track
         */
        public Color overTheRun()
        {
            return overTheRun;
        }
    }

    /**
     * The color trains are drawn in: see-through by default, and easy to switch (Adam: "can we change to the see
     * through tail line, and make it easy to switch to that one too?").
     */
    private static volatile TailColour tailColour = TailColour.SEE_THROUGH;

    /**
     * The color trains are drawn in, asked in ONE place so a preference can choose it later, as `tailStyle` is.
     *
     * @return the color a train's body is drawn in
     */
    public static TailColour tailColour()
    {
        return tailColour;
    }

    /**
     * Chooses the color trains are drawn in from now on - for that preference, and for a test.
     *
     * @param colour the color, null for the default
     */
    public static void setTailColour(TailColour colour)
    {
        tailColour = colour == null ? TailColour.SEE_THROUGH : colour;
    }

    /**
     * Draws what a standing train has done to this square: the gray it has blocked, the orange it is lying on.  The two
     * marks say different things (Adam: *"'train is here' should also mean 'track is blocked' - that is the whole
     * point.  it's the same as greying out edges, just in a different way"*), the wash under the line.  One line along
     * each covered road, not a wash of the whole square (Adam: *"graying makes it look confusing on double curve
     * tiles"*), and none where the window names no road.  The viewer only (Adam: "don't show shading in the autonomy or
     * diagram editor, only the track diagram viewer").
     *
     * @param g the tile's graphics
     */
    private void paintCoveredMark(java.awt.Graphics2D g)
    {
        paintCoveredMark(g, false);
    }

    /**
     * Draws the train's line, as `paintCoveredMark(g)` does, or over a run line: in the color that comes out there
     * as the line does over plain track (Adam: "brighten the color of the orange train tail while running so that
     * it more closely matches the normal stopped color").
     *
     * @param g where to draw
     * @param overTheRun whether it is drawn over the run line, not under everything
     */
    private void paintCoveredMark(java.awt.Graphics2D g, boolean overTheRun)
    {
        if (component == null || component.isText() || !trainsShown.getAsBoolean()) return;

        // The refusal is drawn in `paintComponent`, not here (MT-278 item 1): a fade must be applied to the icon as it
        // is painted, which only `paintComponent` can do.  What is left here is the train mark, at full strength.
        java.util.List<org.traincontrol.automationui.TilePorts.Route> roads = coveredRoads();

        if (roads.isEmpty()) return;

        int span = Math.min(getWidth(), getHeight());

        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

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
                // Rounded where the train ends (Adam: "Can we make the end of a train (orange line) rounded, not a
                // straight jagged edge?"): the square clips what it draws, so a round cap at the train's last side
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
     * the square beside, otherwise short of it by what the round cap needs to lie inside this square (more where the
     * road meets the side at a slant) and a pixel and a half more.
     *
     * @param end the road's midpoint on that side
     * @param other its midpoint on the other side
     * @param side the side
     * @param radius the cap's radius, half the line's width
     * @return where the line stops
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
     *
     * @param side the side
     * @return whether the line goes on
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

    /**
     * The square beside one across a side, on the same page.
     *
     * @param square the square
     * @param side the side
     * @return the square beside, or null for no side
     */
    public static org.traincontrol.automationui.TileGraph.TileKey besideOn(org.traincontrol.automationui.TileGraph.TileKey square,
        org.traincontrol.automationui.TilePorts.Side side)
    {
        return square == null ? null : square.beside(side);
    }

    /**
     * The shape to fade when only some of this square's roads are covered, or null to fade all of it.  Only where the
     * square carries more than one road (a double curve: two crossing curves with no connection) and the train is on
     * fewer of them; elsewhere the square is the road.  A band along the road, since the tile is an image with no
     * region this code can name, using the geometry `coveredRoads` draws the orange line with so the two describe the
     * same roads.
     *
     * @return the region to fade, or null to fade the whole square
     */
    private java.awt.Shape theRoadToFade()
    {
        if (component == null || tcUI == null || square == null) return null;

        if (getWidth() <= 0 || getHeight() <= 0) return null;

        // The blocked roads, not the train's (OB-208): the fade is the gray, which is its own answer (Adam's ruling),
        // the places the tails claim per road (OB-280).  Asking the orange here would fade the whole of a double curve
        // the train is not on.
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

    /**
     * The roads of this square a standing train is lying across, as the sides they join.
     *
     * @return the roads, possibly none
     */
    private java.util.List<org.traincontrol.automationui.TilePorts.Route> coveredRoads()
    {
        if (edit || square == null || tcUI == null || component == null)
        {
            return new java.util.ArrayList<>();
        }

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
            // named by the sides they join, so indexing the port map found nothing and the line and band broke at every
            // route tile.
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
     * Draws this tile's train onto the container, above the captions (OB-159).  Adam: "it is a z order issue.  The
     * stations paint over the locomotives."  A tile is behind every station caption, deliberately: it is opaque, and a
     * lifted one painted a name out (OB-117).  So a third pass: the container paints its children and then asks each
     * tile for this, so the locomotive lands over both.
     *
     * Translated to this tile, since the overlay draws in tile coordinates, and not clipped to it (Adam, MT-642: "there
     * should be no clip"): on a curve the icon reaches onto the next tiles, which `setAutonomyOverlay` repaints when a
     * train comes or goes.  The center is the train's own road (`trackCentreOf`): a double curve has two.
     *
     * @param g the container's graphics
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
     * Whether the train `paintTrainOverCaptions` draws for this square covers a point (OB-354): it is drawn over the
     * captions and reaches past the tile on a curve, so a click on it can land on a caption or on the next square.
     *
     * @param at the point, in this square's coordinates
     * @return true where a train is drawn there
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
     * What of this square's station icon spills onto the squares beside it, drawn by the diagram once every square
     * is drawn (Adam: *"make the stations spill over onto adjacent tiles"*) - `TileAnnotation.paintSpill`.
     *
     * @param g the diagram's graphics
     * @return whether anything was drawn: a spill reaching what is being repainted
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
     * This square's arrows back over a neighbor's station that spilled onto it (Adam: *"make sure that the optional
     * ingress/egress arrows remain visible, especially on curves"*) - `TileAnnotation.paintArrowsAgain`, asked by
     * the diagram once the spills are drawn.  Only where another square's spill reaches this one.
     *
     * @param g the diagram's graphics
     * @param spilt the squares that spilled, and how far
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
     * And the squares around it, where this square's station spills onto them (Adam: *"make the stations spill over
     * onto adjacent tiles"*): the diagram paints a square's neighbors only where it is asked to, so repainting this
     * square alone would leave the old spill on them, a station turned gray and still blue past its edges.
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

    /**
     * What this square's spill covers, in the diagram's coordinates, or null - `TileAnnotation.spillShape`.
     *
     * @return the region, or null
     */
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
     * The middle of the train's own road on this square, for its icon (MT-642): from the square's own ports on a curve
     * or double curve (the annotation knows the roads only while the arrows are shown), a station's first road
     * otherwise; from the annotation anywhere else and where the square has no track of its own.
     *
     * @param overlay the overlay with the train
     * @param annotation the square's annotation, or null
     * @return x and y within the square, or null where nothing says
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
     *
     * @param tcUI the window, for the running layout
     * @param accessory the accessory the click would toggle, or null
     * @return true when the click would clear protection at an occupied platform
     */
    private static boolean aboutToClearProtection(TrainControlUI tcUI, Accessory accessory)
    {
        if (accessory == null || tcUI == null || tcUI.getModel() == null) return false;

        // Asked once, not built: this is the switching thread, and Unload can clear the model between asking whether
        // there is a railway and asking for it
        final org.traincontrol.automation.Layout running = tcUI.getModel().getAutoLayoutIfLoaded();

        if (running == null) return false;

        // And only while autonomy is running, as the dialog that can authorize the click asks.  With a configuration
        // loaded and autonomy idle (the ordinary state) no dialog could be shown, `warnedAboutProtection` would always
        // be false, and the worker would refuse every click that sets a protecting signal green over a square the model
        // believes occupied, even for trains driven away by hand.  The race this guards, `refreshOneSignal` driving the
        // signal from an occupancy change between the dialog and the command, needs autonomy processing occupancy.
        if (!tcUI.getModel().isAutonomyRunning()) return false;

        // A tile toggles, so the click is about to command green exactly when the accessory is not straight now.  The
        // rest of the question lives on `Layout.clearsProtection`, which the switch keyboard asks too.
        return running.clearsProtection(accessory, !accessory.isStraight());
    }
}
