package org.traincontrol.gui;

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu on the track diagram, to control autonomy
 * @author Adam
 */
final class LayoutRightclickAutonomyMenu extends JPopupMenu
{    
    public static final int MAX_PATHS = 12;

    /**
     * Kept so the actions built below can still reach them when they fire, which is long after this
     * constructor has returned.  Everything else here is local, because everything else is used while
     * the menu is being assembled.
     */
    private final TrainControlUI ui;

    private final org.traincontrol.automationui.TileGraph.TileKey station;

    private final org.traincontrol.automationui.AutonomySession session;
    
    /**
     * @param station the sensor the caption is about, or null for the diagram-wide menu.  A square
     *        rather than a name: a station is several Points now and none of them is called what the
     *        caption says, so resolving one by its text found nothing and the menu silently lost every
     *        item below the lookup.
     */
    public LayoutRightclickAutonomyMenu(TrainControlUI ui,
        org.traincontrol.automationui.TileGraph.TileKey station)
    {
        this.ui = ui;
        this.station = station;
        this.session = ui.getAutonomySession();

        JMenuItem menuItem;
        
        if (ui.getModel().hasAutoLayout())
        {     
            if (!ui.getModel().getAutoLayout().isAutoRunning())
            {
                menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStartAutonomy"));
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        ui.requestStartAutonomy();
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });

                add(menuItem);

                HomeLocomotiveMenu.addReturnHomeItem(this, ui);

                // The Point standing on that square, preferring one with a train on it
                Point current = ui.getAutonomyPointForTile(station);

                if (current != null && current.isDestination())
                {
                    // Get the locomotive at this station
                    Locomotive locomotive = current.getCurrentLocomotive();

                    // If we want to view paths, locomotive must not be running
                    if (locomotive != null && !ui.getModel().getAutoLayout().getActiveLocomotives().containsKey(locomotive))
                    {
                        List<List<Edge>> paths = ui.getModel().getAutoLayout().getPossiblePaths(locomotive, true);

                        paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));

                        if (!paths.isEmpty())
                        {
                            addSeparator();

                            // Show the locomotive name for reference
                            menuItem = new JMenuItem(locomotive.getName());
                            menuItem.setEnabled(false);
                            add(menuItem);
                        }

                        for (List<Edge> path : paths)
                        {
                            menuItem = new JMenuItem("-> " + path.get(path.size() - 1).getEnd().getName());
                            menuItem.addActionListener(event -> 
                            {
                                try
                                {
                                    // TODO there is commonality with AutoLocomotiveStatus - reuse code
                                    new Thread(() ->
                                    {
                                        if (!ui.getModel().getPowerState())
                                        {
                                            javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.powerOnToStart")));
                                        }
                                        else
                                        {
                                            ui.ensureGraphUIVisible();

                                            boolean success = ui.getModel().getAutoLayout().executePath(
                                                path, locomotive, locomotive.getPreferredSpeed(), null
                                            );

                                            if (!success)
                                            {
                                                javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.autoFailedCheckLog")));
                                            }
                                        }
                                    }).start();
                                }
                                catch (Exception e)
                                {
                                    JOptionPane.showMessageDialog(this, e.getMessage());
                                }
                            });    

                            add(menuItem);

                            if (this.getComponentCount() > MAX_PATHS + 1)
                            {
                                menuItem = new JMenuItem("...");
                                menuItem.addActionListener(event -> 
                                {
                                    try
                                    {
                                        ui.jumpToAutonomyLocTab();
                                    }
                                    catch (Exception e)
                                    {
                                        JOptionPane.showMessageDialog(this, e.getMessage());
                                    }
                                });    

                                add(menuItem);
                                break;
                            }
                        }
                    }

                    addSeparator();

                    // Station name label
                    menuItem = new JMenuItem(current.getName());
                    menuItem.setEnabled(false);
                    add(menuItem);

                    // Place a different locomotive at this station, FACING a chosen way.
                    //
                    // A square is several Points - one per side a train can arrive by - and they are
                    // not interchangeable: each copy can only leave the way its own facing allows.  So
                    // "put it here" is not a complete instruction, and this used to answer it with
                    // whichever copy came first, which put the train on a Point whose only moves are
                    // the ones the split exists to forbid.  Autonomy could see the locomotive and
                    // could not route it anywhere.
                    if (ui.getActiveLoc() != null
                        && !ui.isAutonomyBusy()
                        && !ui.getActiveLoc().equals(locomotive))
                    {
                        java.util.Map<String, org.traincontrol.automationui.TilePorts.Side> facings =
                            session == null ? new java.util.LinkedHashMap<String,
                                org.traincontrol.automationui.TilePorts.Side>()
                                : session.facingsFor(station);

                        // One copy is one answer, so there is nothing to ask
                        if (facings.size() <= 1)
                        {
                            menuItem = new JMenuItem(
                                I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName())
                            );

                            final String only = facings.isEmpty()
                                ? current.getName() : facings.keySet().iterator().next();

                            menuItem.addActionListener(event -> placeFacing(only, null));

                            add(menuItem);
                        }
                        else
                        {
                            JMenu placing = new JMenu(
                                I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName()));

                            for (java.util.Map.Entry<String,
                                org.traincontrol.automationui.TilePorts.Side> copy
                                    : facings.entrySet())
                            {
                                final String name = copy.getKey();
                                final org.traincontrol.automationui.TilePorts.Side facing =
                                    copy.getValue();

                                JMenuItem where = new JMenuItem(
                                    I18n.f("layout.ui.menuPlaceFacing", facing.toString()));

                                where.addActionListener(event -> placeFacing(name, facing));

                                placing.add(where);
                            }

                            add(placing);
                        }
                    }

                    if (current.getCurrentLocomotive() != null && !ui.isAutonomyBusy())
                    {
                        menuItem = new JMenuItem(
                            I18n.f("layout.ui.menuRemoveLocomotive", current.getCurrentLocomotive().getName())
                        );
                        menuItem.addActionListener(event ->
                        {
                            ui.getModel().getAutoLayout().moveLocomotive(
                                null,
                                current.getName(),
                                false
                            );
                            ui.repaintAutoLocList(false);
                        });

                        add(menuItem); 
                    }
                    
                    if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() && !ui.isAutonomyBusy())
                    {
                        // Edit locomotive
                        menuItem = new JMenuItem(GraphLocAssign.menuLabelFor(current));
                        menuItem.addActionListener(event -> 
                        {
                            GraphLocAssign edit = new GraphLocAssign(ui, current, false);

                            int dialogResult = JOptionPane.showOptionDialog(
                                ui,
                                edit,
                                I18n.f("autolayout.ui.dialogEditOrAssignLocomotive", current.getName()),
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                TrainControlUI.OK_CANCEL_OPTS,
                                TrainControlUI.OK_CANCEL_OPTS[0]
                            );

                            if (dialogResult == JOptionPane.OK_OPTION)
                            {
                                edit.commitChanges();
                                ui.updateVisiblePoints();
                                ui.repaintAutoLocList(false);
                            }
                        }); 

                        add(menuItem);
                    }

                    // Which locomotive belongs here, as opposed to which one happens to be here now
                    addSeparator();

                    HomeLocomotiveMenu.addStationItem(this, ui, current, ui, null,
                        ui::updateVisiblePoints);
                }
            }
            else
            {
                menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStopAutonomyGracefully"));
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        ui.requestStopAutonomy();
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });    

                add(menuItem);
            }
        }
    }

    /**
     * Puts the active locomotive on one named copy of this square, and remembers which way.
     *
     * Both halves matter.  Placing on the right copy is what lets autonomy move it NOW; recording
     * the facing is what puts it back on the same copy the next time the graph is built, which is
     * every load.  Without the second, the placement is correct until the next reload and arbitrary
     * afterwards.
     *
     * @param pointName the copy to place on
     * @param facing which way it is pointing, or null when the square has only one copy
     */
    private void placeFacing(String pointName,
        org.traincontrol.automationui.TilePorts.Side facing)
    {
        ui.getModel().getAutoLayout().moveLocomotive(
            ui.getActiveLoc().getName(), pointName, false);

        if (facing != null && session != null)
        {
            session.setFacing(station, facing);

            try
            {
                session.save();
            }
            catch (java.io.IOException e)
            {
                // The placement stands either way; only the memory of it is at risk
                if (ui.getModel().isDebug()) ui.getModel().log(String.valueOf(e.getMessage()));
            }
        }

        ui.repaintAutoLocList(false);
    }
}
   