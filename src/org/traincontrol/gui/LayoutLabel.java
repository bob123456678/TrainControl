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
    
    /**
     * Whether this label is one of the grid's spacers rather than a square of the diagram.
     *
     * LayoutGrid puts an empty label along its last row and column - "a dummy column at the end with
     * nothing in it to ensure long labels don't misalign things".  They are not track and they are not
     * places a tile can be drawn, so they must not wear the grey grid.
     *
     * A flag rather than "has no component": the blank squares in the MIDDLE of a layout also have no
     * component, and in the editor those are exactly the squares a user is about to draw on - they need
     * the grid more than anything else does.
     */
    private boolean spacer;

    /**
     * Says this label is one of the grid's spacers.  Called by LayoutGrid as it builds them.
     */
    void markSpacer()
    {
        this.spacer = true;

        // and take off anything already put on, since the border is set in the constructor
        this.setBorder(null);

        // AND IT KEEPS ITS ROOM.  Tried and reverted, 2026-09-02.
        //
        // Adam reported the axis numbers as "missing for the last row/col", and what has no number is
        // this cell - a tile's width of empty grid past the last real square, which is the same thing
        // to look at.  The obvious answer is to stop paying for it in pixels.
        //
        // It does not work, and the test that says so is `testACaptionNeverMovesAnythingElse`: with the
        // spacer at zero, adding one caption moved 351 other things on the diagram by eight pixels.
        // The dummy column is not merely present for GridBagLayout, it is the SPACE an overflowing text
        // label is absorbed into - which is exactly what "ensure long labels don't misalign things"
        // means, read properly.  Taking the room away puts the misalignment back.
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

            // The grey grid, which is where it actually comes from - the editor's restingBorder puts it
            // BACK after a hover, and this is what puts it there to begin with.
            //
            // Asked of the same rule, so the toggle reaches both (FR-006).  Off, it is NOTHING - tiles
            // sit flush, as they do in the viewer - and the hover outline reserves nothing either, so
            // the pointer no longer pushes the diagram along in front of it.
            this.setBorder(LayoutEditor.restingBorder(false,
                parent instanceof LayoutEditor && ((LayoutEditor) parent).isAutonomyMode(),
                LayoutEditor.showGrid()));
        }
        
        // Every square on the MAIN window reports when the pointer is over it.
        //
        // The keyboard shortcuts that move a locomotive from square to square need to know which
        // square is meant, and a diagram has no cursor and no selection - the pointer is the only
        // thing saying where the user is looking.  The graph window did the same with its hovered
        // node; this is that, on the surface that replaced it.
        //
        // On every square rather than only on sensors: a shortcut aimed at plain track has to be
        // ignored rather than acted on at whichever station was hovered last, which is what leaving
        // the old square set would do.
        if (!edit && tcUI != null)
        {
            LayoutLabel hovering = this;

            this.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    // From the label's OWN square, not from whatever is drawn on it.
                    //
                    // It used to ask the component for its coordinates and report nothing at all when
                    // there was no component - and a station's NAME is usually drawn on blank space
                    // beside the platform, because that is where there is room for it.  So the one part
                    // of a station big enough to aim at was the one part the keyboard could not see,
                    // and Control+V over the name did nothing while Control+V over the sensor worked.
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
            // Every square answers a right-click, whatever is drawn on it.
            //
            // The kinds below have listeners because they can be operated - thrown, triggered, jumped
            // through.  Plain track cannot be operated and so had no listener at all, which made a
            // right-click on most of the diagram do nothing.  The autonomy menu is about the PLACE
            // rather than about the thing standing on it, and a piece of plain track is as much a
            // place as a turnout is.
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
                               // A sensor is what a station IS, so this is the branch that matters
                               // most for the autonomy menu - and it was the one branch that did not
                               // check for it.  Every station on the diagram right-clicked to nothing.
                               if (openStationMenu(e)) return;

                               // A right-click that opened nothing is not a request to flip the
                               // sensor.  This branch acted on any button, which was harmless while
                               // right-clicking a sensor did nothing - and stopped being harmless the
                               // moment right-clicking a station became the way to work with it: with
                               // the overlay off, or the page left out, or no configuration loaded,
                               // the same gesture silently faked an occupancy change, which during a
                               // run is precisely the input the layout reacts to.
                               if (javax.swing.SwingUtilities.isRightMouseButton(e)) return;

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

                                        // Anything that is not one of the two YESES is a no.
                                        //
                                        // showOptionDialog answers CLOSED_OPTION (-1) when the dialog is
                                        // dismissed with Escape or the window's close box, and -1 fell
                                        // through `default` to the same place as "proceed". So closing
                                        // this dialog threw the accessory - the opposite of what closing
                                        // a confirmation means.
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
                                    // ASKED WHETHER OR NOT THE POWER WAS ON (V35-C2).
                                    //
                                    // This was the `else` of the dialog above, so with the power off
                                    // the conflict question was not asked at all - and the power-off
                                    // gesture is exactly the one somebody makes after an emergency
                                    // stop, with trains standing where they stopped and autonomy still
                                    // loaded.  "Turn power on and proceed" then cleared protection at
                                    // an occupied platform without the platform being mentioned.
                                    //
                                    // Two dialogs in a row in that case, which is the honest cost: the
                                    // second only appears when autonomy is running AND this accessory
                                    // actually conflicts, and each asks about something different.
                                    //
                                    // Remembered, because the worker below re-asks the protection
                                    // question and must not refuse a click the operator was already
                                    // warned about and accepted.
                                    boolean askedAboutProtection = false;

                                    if (tcUI.getModel().hasAutoLayout() && tcUI.getModel().isAutonomyRunning())
                                    {
                                        Collection<Accessory> activeAccs = tcUI.getModel().getAutoLayout().getActiveAccs();
                                        
                                        // AND THE PROTECTING-SIGNAL HALF (SVN-B16).
                                        //
                                        // `getActiveAccs` walks the config commands of active edges,
                                        // and a platform's protecting signal is usually not one of
                                        // them - it is driven by occupancy instead.  So a route
                                        // setting that signal green was refused and clicking the same
                                        // signal green by hand, on this window, was not: the same
                                        // green aspect inviting a hand-driven train into a platform
                                        // somebody is standing at.
                                        //
                                        // Asked of the layout, which is where the rule lives now, so
                                        // this and MarklinRoute.heldReason cannot drift apart.
                                        // ONLY THE GREEN DIRECTION, as the route door asks it
                                        // (WK3-B1, DY3-B1, D3F-C4).
                                        //
                                        // The aspect matters, and this omitted it.  A protecting
                                        // signal's only harmful command is the one that turns
                                        // protection OFF; setting it RED is doing exactly what the
                                        // protection mechanism itself would do, and refusing that was
                                        // "pure over-strictness" when the route door did it - found
                                        // and removed by an earlier review, with the reasoning still
                                        // written at MarklinRoute.heldReason.
                                        //
                                        // Which direction this click is about is decided before it
                                        // runs, and the test is about the COMMAND rather than about
                                        // any one method that issues it.
                                        //
                                        // A signal or a lamp goes through `Accessory.doSwitch()`,
                                        // which is `isStraight() ? turn() : straight()`.  A THREE-WAY
                                        // does not: `execSwitching` drives its two accessories
                                        // directly, in three cases.  It reaches the same place, and
                                        // the accounting is over all FOUR states rather than over the
                                        // three branches (V34-C5): every drive commanded GREEN is
                                        // either on an accessory whose `isStraight()` is false or was
                                        // green already, and the drives commanded RED - which happen
                                        // in two of the three branches - cannot clear protection,
                                        // because red is the direction protection itself commands.
                                        //
                                        // So the tile's test has neither a false negative nor a false
                                        // positive on a three-way.  What is true is that the method
                                        // named here is not the method that runs for the second limb,
                                        // and a reader checking the claim against a three-way would
                                        // find that (V32-C3).
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
                                            
                                            // As above, and this one matters more: the accessory is
                                            // part of a route that is running RIGHT NOW, with a train
                                            // on it. Escape threw the turnout under that train.
                                            switch (choice)
                                            {
                                                case 0: // ok
                                                    break;
                                                default: // cancel, Escape, or the close box
                                                    return;
                                            }

                                            // Only when the question that was answered was the
                                            // PROTECTION one.  Agreeing to throw an accessory on an
                                            // active route says nothing about a platform.
                                            askedAboutProtection = protecting;
                                        }                                
                                    }

                                    // And the same question for a ROUTE tile, which had none.
                                    //
                                    // The check above asks about the tile's OWN accessory, and a route
                                    // tile has none - `activeAccs.contains(null)` is false - so the
                                    // one door that looked guarded was not. A route sets several
                                    // accessories, so the question is about those.
                                    // getRoute() is typed as the base Route here; only a MarklinRoute
                                    // knows about the autonomy graph, and every route this component
                                    // can hold is one.
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
                                                    // Bounded, and the switching goes ahead either way.
                                                    //
                                                    // This wait used to have no deadline, and the power
                                                    // state is set only by the echo from the Central
                                                    // Station - so a station that had been switched off
                                                    // parked this worker for ever.  It is the ONLY
                                                    // thread in the switching pool, and the pool is
                                                    // shared by every tile in the application: one such
                                                    // click and no tile anywhere responded again, with
                                                    // nothing logged and nothing shown.
                                                    if (!tcUI.getModel().waitForPowerState(true,
                                                        org.traincontrol.marklin.MarklinControlStation.POWER_STATE_TIMEOUT))
                                                    {
                                                        // Naming the tile, which is worth knowing and
                                                        // is also what gets the message SAID: the log
                                                        // drops a line identical to the one before it,
                                                        // so a bare sentence would appear once and
                                                        // every later failure would be silent.
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

                                        // The route conflict is asked HERE, not before the worker
                                        // (LD-7).
                                        //
                                        // This block used to sit above, between the power-off dialog
                                        // that sets powerOnFirst and the worker that consumes it, and
                                        // it returned - so agreeing to "turn the power on and proceed"
                                        // ran the route into a dead track with the power still off,
                                        // silently. That is precisely the case this whole feature was
                                        // built for: recovering a turnout that did not take its
                                        // command, after an emergency stop.
                                        //
                                        // Asking after the power is on is also the better moment on
                                        // its own terms - it is closer to the action, so the answer is
                                        // less likely to have gone stale between the question and the
                                        // command.
                                        if (onTile != null)
                                        {
                                            // Off this worker before anything can block on a person.
                                            //
                                            // submitSwitching has ONE thread and it is shared by
                                            // every tile in the application. Asking the question here
                                            // would hold it for as long as the dialog stands
                                            // unanswered, and no tile anywhere would respond - the
                                            // same freeze the power-state wait was given a deadline
                                            // to avoid, forty lines above, differing only in that a
                                            // person ends this one.
                                            //
                                            // The power is already on by the time this runs, which is
                                            // the ordering the relocation was for, so the handoff
                                            // costs nothing.
                                            new Thread(() ->
                                            {
                                                // One question, three answers (A3).  A conflict that
                                                // cleared between the check and the dialog used to
                                                // come back as "override", and the route then ran with
                                                // every guard off without anybody being asked.
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

                                        // AND ASK AGAIN, HERE, WHERE THE COMMAND ACTUALLY GOES
                                        // (V32-C5).
                                        //
                                        // The question above was answered on the event thread; this
                                        // runs later, on the single switching thread, after a power-on
                                        // that waits a further second.  `Layout.refreshOneSignal` can
                                        // drive the same accessory from an occupancy change in that
                                        // gap.  The order that matters: the signal was green when it
                                        // was clicked, so no dialog was shown; a train then arrived
                                        // and protection set it red; and this command would have set
                                        // it green again over an occupied platform, with nobody asked
                                        // anything.
                                        //
                                        // Refused rather than re-asked.  Asking from here would hold
                                        // the one switching thread the whole application shares - the
                                        // freeze the power-state wait was given a deadline to avoid -
                                        // and the honest answer to "the situation changed under you"
                                        // is to not act and say so, leaving the operator to click
                                        // again if they still mean it.
                                        //
                                        // Skipped for a click the operator was already warned about
                                        // and accepted: they were asked about this exact accessory.
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
        // No parent at all counts as not visible, which is what gets the label REMOVED from the
        // accessory or feedback that holds it.
        //
        // A grid built for an export is given a null master - it is painted offscreen and thrown away -
        // and its labels are registered with the model like any others.  This method then NPEd on the
        // message thread, inside the loop that updates every tile: the exception was swallowed by the
        // executor's Future, and the loop abandoned every tile after it.  Exporting a picture of the
        // diagram permanently stopped tile updates for the accessories on that page.
        return this.parent != null && this.parent.isVisible();
    }

    /**
     * Whether this label belongs to the same window as another.
     *
     * Used when a grid is rebuilt, to tell "this label's generation has been replaced" from "this label
     * belongs to a different window that happens to be showing the same page".  The main window caches
     * a page's grid and re-attaches it later, so its labels are legitimately detached for a while; a
     * popup rebuilding that same page must not take them for rubbish.
     *
     * @param other
     * @return
     */
    public boolean sharesWindowWith(LayoutLabel other)
    {
        return other != null && other.parent == this.parent;
    }

    /**
     * Drops the labels that a newly registered one replaces.
     *
     * Registering a label for a square is the moment the old labels for THAT SQUARE became rubbish, so
     * it is the moment to drop them.  Nothing else can: the prune inside updateTiles asks whether a
     * label's parent is VISIBLE, and the main window's parent is the tab strip, which is visible for
     * the life of the application - so on the main window that test can never be false and nothing was
     * ever removed.  Every repaint that rebuilt the grid - a size change, an address toggle, closing
     * the editor, switching pages with the cache cold - left a whole page of dead labels registered,
     * and each accessory then walked an ever-longer list on every message from the Central Station.
     *
     * Three conditions, and the first is the one this was written without.
     *
     * SAME SQUARE.  DiagramTileRegistry keeps its labels in a map keyed by square, so within one of
     * its entries "an older label of this window" can only mean "the label this one replaces" - and
     * its own comment says so.  The three device collections are keyed by DEVICE instead, and one
     * accessory is routinely drawn on several squares of a page: the sample layout has address 162 on
     * four tiles of "3 - Top Parking" and five of "4 - Combined".  Lifting the rule without its
     * precondition therefore made each arriving label evict its own siblings, because LayoutGrid
     * registers every label in its build loop and only attaches the container afterwards, so during a
     * build NO label is displayable yet.  Three of those four signal tiles stopped updating the moment
     * the page was drawn - which is worse than the leak this was fixing, and quieter.
     *
     * SAME WINDOW.  The main window caches a page's grid and re-attaches it when the user comes back,
     * so its labels are detached - and perfectly alive - whenever another page is showing.  Judging
     * them from a popup rebuilding the same page would throw them out while they were merely put away.
     *
     * NOT DISPLAYABLE, rather than not visible: a label whose grid has been replaced has been removed
     * from a realised window and has no peer, which is what "nobody can see this" actually means.  The
     * arriving label is never judged; it may legitimately not be attached yet.
     *
     * Where the square cannot be established the label is KEPT.  A missed prune is a slow leak; a
     * wrong prune is a tile that never updates again, and of the two the leak is much the better bug.
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
     * Whether two labels are drawn on the same square of the same page.
     *
     * False whenever that cannot be established - a label with no component, or one whose page was
     * never set - so an unknown square is never treated as a match.  See forgetReplaced: keeping a
     * label that should have gone costs memory, and dropping one that should have stayed costs the
     * user a tile that stops responding.
     *
     * @param other the other label
     * @return whether both name the same square
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
     * Which page of the diagram this square is on.
     *
     * A label is otherwise told neither its page nor its coordinates, and asking the main window which
     * page is showing is the wrong question in a popup: a popup shows a page of its own, so a
     * right-click in one opened the menu belonging to whatever the MAIN window happened to have
     * selected - offering a station the user was not pointing at, or, where the main page had no
     * station at those coordinates, falling through and throwing the sensor instead.
     */
    private String autonomyPage;

    /**
     * Where this label sits on its page.
     *
     * Told to it rather than read off the component, because a label with NO component still has a
     * square - and those are the squares a station's name is drawn on.
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

    // Where this label is, for a hover to report.  -1 until it is told, which is the state of the
    // spacer labels at the edge of the grid - they are on no square and must report none.
    private int squareX = -1;
    private int squareY = -1;
    
    /**
     * A station's own menu, from the track rather than from its caption.
     *
     * The caption is a small piece of text that may be on the square below the platform, or missing
     * altogether; the sensor is the thing the user is looking at and pointing to.  Same menu either
     * way, so there is one place that knows what a station offers.
     *
     * @param e the click
     * @return true when the menu was opened, and the caller should do nothing else with the click
     */
    private boolean openStationMenu(MouseEvent e)
    {
        if (e.getButton() != MouseEvent.BUTTON3 || component == null) return false;

        // A station gets the station items; every other square gets the menu without them.
        //
        // It used to open only over a station, and a right-click anywhere else did nothing at all -
        // which meant the way into the autonomy setup was the menu bar, and the menu bar is not where
        // somebody working on their railway is looking.  They are looking at the diagram.  A square
        // that is not a station has nothing station-shaped to offer, but it can still be the place
        // this layout's setup is reached from.
        final org.traincontrol.automationui.TileGraph.TileKey station =
            tcUI.autonomyStationAt(autonomyPage, component.getX(), component.getY());

        // The square itself, which is what the setup menu acts on and is there whether or not
        // anything has been made of it yet.
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
                // Counted before it is submitted, so that a grid asking "are the tiles ready" cannot
                // see zero in the window between building the labels and the pool picking the work up.
                //
                // Only a FIRST draw is counted, never an update.  A tile's image key includes its state
                // while the diagram is not being edited, so every switch thrown and every s88 event is
                // a fresh key, a cache miss, and another decode - and autonomy throws several per path.
                // Counting those meant the count was almost never at rest on a running layout, so a
                // grid built while trains were moving waited on traffic that had nothing to do with it
                // and sat behind its spinner until the failsafe fired.  A grid only ever waits for the
                // batch it was built from.
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
                        // In a finally: a tile that fails to decode still has to stop being counted, or
                        // one unreadable image leaves the diagram waiting for it for ever.
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

                    // Null for a label built offscreen for an export - see isParentVisible
                    if (this.parent != null) this.parent.repaint();
                    
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
        flashHighlight(org.traincontrol.util.ImageUtil.HIGHLIGHT, HIGHLIGHT_DURATION);
    }

    /**
     * The same flash, in a colour and for a length the caller chooses.
     *
     * Both are the caller's because both mean something: the route editor lights what a route COMMANDS
     * in the ordinary yellow and what it merely CHECKS in orange, and holds them long enough to look
     * from the window to the diagram and back.
     *
     * @param wash the colour to lay over the tile
     * @param holdMs how long to hold it
     */
    public void flashHighlight(java.awt.Color wash, int holdMs)
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

        // A square with a train running on it comes to the front (FR-027).
        liftAboveLabels(effective != null && effective.isMoving());

        // repaint() is safe from any thread; the monitor publishes from its own worker
        this.repaint();
    }

    /**
     * Whether this tile is currently in front of the labels that are normally drawn over it.
     *
     * Remembered so the reordering happens on the two transitions and not on every publish: the
     * monitor republishes as often as the railway changes, and moving a component within its container
     * on each of those would be real work for no change.
     */
    private boolean liftedForTrain = false;

    /**
     * Puts every station caption in a container back in front of everything else.
     *
     * Public and static so the rule can be given a container and asked what it does with it -
     * `testTheTrainIconDoesNotPaintOutACaption` builds one out of plain components and checks the
     * order, which is the whole of what this has to get right and needs no railway to establish.
     *
     * @param parent the container holding the tiles and their labels
     */
    public static void keepCaptionsInFront(java.awt.Container parent)
    {
        java.awt.Component[] all = parent.getComponents();

        // BACKWARDS, so the labels keep the order they were built in (D2).
        //
        // Pushing each one to the front in turn reverses them, and `StationCaption` is not only the
        // pills - LayoutGrid builds every piece of text on a diagram as one, including the user's own
        // writing. Going the other way round leaves the first-built label at the front, which is where
        // it started.
        for (int at = all.length - 1; at >= 0; at--)
        {
            if (all[at] instanceof StationCaption) parent.setComponentZOrder(all[at], 0);
        }
    }

    /**
     * Brings this tile in front of the address labels, or puts it back behind everything - and, since
     * OB-117, hands the front straight back to the station captions so this ends up above the
     * addresses but below them, not above both.
     *
     * Adam, looking at the locomotive icon: "make sure it renders on top of the S88's.  Right now,
     * it's a coin toss."  It was not really a toss - it was fixed and wrong. The overlay is painted
     * after `super.paintComponent`, so it is reliably over this tile's own icon, and the note above
     * `paintComponent` says as much. What it also says is that the address and station labels are
     * SEPARATE components, z-ordered to the front by LayoutGrid as they are added - and no painting
     * order inside one component can reach over a sibling drawn after it. Which of the two you saw
     * depended on where the number happened to fall, which is what looked like chance.
     *
     * So the fix is where the problem is: the component order. Index 0 is painted last, which is why
     * the labels claim it; a tile with a train on it claims it for as long as the train is running and
     * gives it back afterwards. Tiles do not overlap each other, so their order among themselves means
     * nothing and the release can simply send this one to the back.
     *
     * On the event thread, because the monitor publishes from its own worker and container order is
     * not thread-safe - the repaint above is, which is why it needs no such care.
     *
     * @param lift whether this tile should be in front
     */
    private void liftAboveLabels(final boolean lift)
    {
        if (lift == liftedForTrain) return;

        liftedForTrain = lift;

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            java.awt.Container parent = getParent();

            if (parent == null) return;

            try
            {
                parent.setComponentZOrder(this, lift ? 0 : parent.getComponentCount() - 1);

                // And the station captions back above it (OB-117).
                //
                // Adam: "on route departure from 1016 as the origin station, the locomotive icon covers
                // the autonomy label with a blank white space."
                //
                // What he asked for when this lift was written was that the locomotive clear the S88
                // ADDRESS labels, and index 0 does that. It also clears the station captions, which he
                // did not ask for and which is worse than what it fixed: a tile is opaque, so a lifted
                // one does not merely draw its locomotive over a caption, it paints out every pixel of
                // the caption that lies within the square - the "blank white space", which is this
                // tile's own background.
                //
                // Swing has one ordering and no notion of layers, so "above the addresses but below
                // the captions" has to be arranged rather than declared: take the front, then hand it
                // straight back to the captions. They end up at 0..n-1 and this tile at n, which is
                // above every address label and every other tile.
                //
                // Cheap enough to do plainly: the lift is edge-triggered - it runs when a train
                // starts or stops moving on this square, not on every repaint.
                //
                // The order among the labels themselves DOES matter, which the first version of this
                // comment denied on the grounds that captions do not overlap. Pills do not; but every
                // piece of text on a diagram is a StationCaption, the user's own writing included, and
                // that can overlap a neighbour. So the loop walks backwards and the built order
                // survives.
                if (lift) keepCaptionsInFront(parent);

                // The PARENT, not this tile: the labels that were covering it have moved too, and a
                // repaint of one component cannot clean up where another one used to be.
                parent.repaint();
            }
            catch (RuntimeException e)
            {
                // The tile left its grid between the publish and this running - a page rebuilt under a
                // running layout, which happens. There is nothing to lift and nothing to report.
            }
        });
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
     * with the transient highlight.
     *
     * Do NOT read this as "captions can never be covered, so keepCaptionsInFront is redundant" - that
     * reasoning is what OB-117 was filed about, and it is only half true. Station captions are pulled
     * back in front of a lifted tile by keepCaptionsInFront (see liftAboveLabels), and stay covered
     * only for the instant between the lift and that call running. Address labels get no such rescue:
     * they are plain JLabels, not StationCaption, so keepCaptionsInFront skips them, and a lifted tile
     * paints over their text for as long as a train sits on the square - which is the "coin toss"
     * liftAboveLabels' own javadoc describes.
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
            // The static layer first, and the running one over it.
            //
            // It used to be the other way round, when running state was a wash over the whole square
            // and would have buried the arrows underneath.  It is a LINE along the track now, so it
            // covers only the rails it claims - and where the two do meet, the path a train is
            // actually taking is the more urgent of the two: the arrows say what is permitted, which
            // is worth reading while a layout is being set up rather than while it is running.
            if (annotation != null) annotation.paint(g2, getWidth(), getHeight());

            // Told where the track runs, so a run that ENDS here stops on the rail rather than in the
            // middle of the square (OB-026).  The annotation is the only thing that knows: it holds the
            // sides this tile's route uses, and it is already what places the badges.
            if (overlay != null)
            {
                overlay.paint(g2, getWidth(), getHeight(),
                    annotation == null ? null : annotation.trackCentre(getWidth(), getHeight()));
            }

            // And the badge back on top of the line (MT-076).
            //
            // The order above is about the ARROWS, and is right about them. It is wrong about the
            // station badges: a station is where the train is GOING, and the line is how it gets
            // there, so burying the landmark under the route loses the thing being watched. Adam,
            // watching a run: "I like being able to see progress - keep them on top after being
            // reached."
            //
            // Only where there is an overlay to have covered it, so an ordinary diagram still paints
            // its badge exactly once.
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
     * Draws this tile's train onto the container, above the captions (OB-159).
     *
     * Adam: "it is a z order issue.  The stations paint over the locomotives."
     *
     * The train used to be the last thing this tile painted, and a tile is behind every station
     * caption - deliberately, because it is opaque and OB-117 was about a lifted one painting a name
     * out.  So the two reports pull opposite ways and neither component can be in front of the other
     * and be right.
     *
     * The answer is not an order at all but a third pass.  The container paints its children - tiles,
     * then captions - and then asks each tile for this, so the locomotive lands over both and the name
     * is untouched everywhere the locomotive is not.
     *
     * TRANSLATED AND CLIPPED to this tile's own bounds, because the Graphics belongs to the container
     * and the overlay draws in tile coordinates.  Without the clip an icon on a small tile would spill
     * onto its neighbours, which is the one way this pass could make the diagram worse.
     *
     * @param g the container's graphics
     */
    public void paintTrainOverCaptions(java.awt.Graphics g)
    {
        TileOverlay overlay = autonomyOverlay;

        if (overlay == null || overlay.isBlank() || !overlay.hasTrain()) return;

        org.traincontrol.automationui.TileAnnotation annotation = autonomyAnnotation;

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create(getX(), getY(), getWidth(), getHeight());

        try
        {
            overlay.paintTrain(g2, getWidth(), getHeight(),
                annotation == null ? null : annotation.trackCentre(getWidth(), getHeight()));
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

    /**
     * Whether clicking this accessory would turn protection OFF at a platform somebody is standing at.
     *
     * Both halves, because either alone is the wrong question. `protectsAnOccupiedSquare` asks about
     * the SQUARE and knows nothing about which way the signal is going; the aspect asks about the
     * COMMAND and knows nothing about what is standing where. The route door asks them together at
     * `MarklinRoute.heldReason`, and this door asked only the first.
     *
     * @param tcUI the window, for the running layout
     * @param accessory the accessory the click would toggle, or null
     * @return true when the click would clear protection at an occupied platform
     */
    private static boolean aboutToClearProtection(TrainControlUI tcUI, Accessory accessory)
    {
        if (accessory == null || tcUI == null || tcUI.getModel() == null) return false;

        if (!tcUI.getModel().hasAutoLayout() || tcUI.getModel().getAutoLayout() == null) return false;

        // A tile TOGGLES, so the click is about to command green exactly when the accessory is not
        // straight now.  The rest of the question - and the reason the aspect is half of it - lives on
        // `Layout.clearsProtection`, which the switch keyboard asks too (V31-C2).
        return tcUI.getModel().getAutoLayout().clearsProtection(accessory, !accessory.isStraight());
    }
}
