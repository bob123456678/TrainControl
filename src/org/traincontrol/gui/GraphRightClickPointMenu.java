package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu when the graph UI is clicked on a point
 * @author Adam
 */
final class GraphRightClickPointMenu extends JPopupMenu
{
    JMenuItem menuItem;
    TrainControlUI tcui;

    public GraphRightClickPointMenu(TrainControlUI ui, Point p, GraphViewer parent)
    {       
        String nodeName = p.getName();
        this.tcui = ui;

        if (p.isDestination())
        {
            // Select the active locomotive
            if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty())
            {
                menuItem = new JMenuItem(I18n.f("autolayout.ui.labelEditLocomotiveAt", nodeName));
                menuItem.addActionListener(event -> 
                    {
                        GraphLocAssign edit = new GraphLocAssign(ui, p, false);

                        int dialogResult = JOptionPane.showOptionDialog(
                            (Component) parent.getSwingView(),
                            edit,
                            I18n.f("autolayout.ui.dialogEditOrAssignLocomotive", p.getName()),
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            TrainControlUI.OK_CANCEL_OPTS,
                            TrainControlUI.OK_CANCEL_OPTS[0]
                        );
                        if(dialogResult == JOptionPane.OK_OPTION)
                        {
                            edit.commitChanges();
                            ui.updateVisiblePoints();
                        }
                    }
                );    

                add(menuItem);
            }

            menuItem = new JMenuItem(
                I18n.f("autolayout.ui.menuAddLocomotiveAtNode", nodeName)
            );
            menuItem.addActionListener(event -> 
            {
                if (ui.getModel().getLocomotives().isEmpty())
                {
                    JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                        I18n.t("error.noLocs")
                    );
                }
                else
                {
                    GraphLocAssign edit = new GraphLocAssign(ui, p, true);

                    if (edit.getNumLocs() == 0)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.infoAllLocomotivesPlaced")
                        );
                    }
                    else
                    {
                        int dialogResult = JOptionPane.showOptionDialog(
                            (Component) parent.getSwingView(),
                            edit,
                            I18n.t("autolayout.ui.dialogPlaceNewLocomotive"),
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
                        }
                    }
                }
            }); 

            add(menuItem);

            if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() && ui.getActiveLoc() != null)
            {
                menuItem = new JMenuItem(
                    I18n.f("autolayout.ui.menuAssignLocomotiveToNode", ui.getActiveLoc().getName())
                );
                menuItem.setToolTipText("Control+V");
                menuItem.addActionListener(event -> 
                    {
                        ui.getModel().getAutoLayout().moveLocomotive(ui.getActiveLoc().getName(), nodeName, false);
                        ui.updateVisiblePoints();
                        ui.repaintAutoLocList(false);
                    }
                );    

                add(menuItem);
            }

            addSeparator();

            if (p.isOccupied())
            {
                menuItem = new JMenuItem(
                    I18n.f("autolayout.ui.menuRemoveLocomotiveFromNode", p.getCurrentLocomotive().getName())
                );
                menuItem.addActionListener(event -> { ui.getModel().getAutoLayout().moveLocomotive(null, nodeName, false); ui.updateVisiblePoints(); ui.repaintAutoLocList(false);});    
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.f("autolayout.ui.menuRemoveLocomotiveFromGraph", p.getCurrentLocomotive().getName())
                );
                menuItem.setToolTipText("Delete");
                menuItem.addActionListener(event -> { ui.getModel().getAutoLayout().moveLocomotive(null, nodeName, true); ui.updateVisiblePoints(); ui.repaintAutoLocList(false); });    
                add(menuItem);

                addSeparator();
            }
        }

        // Edit sensor
        menuItem = new JMenuItem(
            I18n.f(
                "autolayout.ui.menuEditS88Address",
                (p.hasS88() ? p.getS88() : I18n.t("autolayout.ui.none"))
            )
        );
        menuItem.setToolTipText("Control+S");
        menuItem.addActionListener(event -> 
            {
                parent.setS88(p);
            }
        );     

        add(menuItem);
        
        // Create a submenu for the remaining items
        JMenu submenu = new JMenu(
            I18n.t("autolayout.ui.menuEditAdvancedParameters")
        );

        if (p.isDestination())
        {
            menuItem = new JMenuItem(
                I18n.f(
                    "autolayout.ui.menuMaxTrainLength",
                    (p.getMaxTrainLength() != 0
                        ? p.getMaxTrainLength()
                        : I18n.t("autolayout.ui.any"))
                )
            );

            menuItem.addActionListener(event ->
            {
                String dialogResult = JOptionPane.showInputDialog(
                    (Component) parent.getSwingView(),
                    I18n.t("autolayout.ui.promptEnterMaxTrainLength"),
                    p.getMaxTrainLength()
                );

                if (dialogResult != null)
                {
                    try
                    {
                        int newLength = Math.abs(Integer.parseInt(dialogResult));
                        p.setMaxTrainLength(newLength);
                        ui.updatePoint(p, parent.getMainGraph());
                        ui.repaintAutoLocList(false);
                    }
                    catch (NumberFormatException e)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.errorInvalidTrainLength")
                        );
                    }
                }
            });

            submenu.add(menuItem);
        }

        // Edit Priority
        if (p.isDestination())
        {
            menuItem = new JMenuItem(
                I18n.f(
                    "autolayout.ui.menuStationPriority",
                    (p.getPriority() != 0
                        ? (p.getPriority() > 0 ? "+" : "") + p.getPriority()
                        : I18n.t("autolayout.ui.default"))
                )
            );

            menuItem.addActionListener(event ->
            {
                String dialogResult = JOptionPane.showInputDialog(
                    (Component) parent.getSwingView(),
                    I18n.f("autolayout.ui.promptEnterStationPriority", nodeName),
                    p.getPriority()
                );

                if (dialogResult != null)
                {
                    dialogResult = dialogResult.trim();

                    try
                    {
                        Integer value;
                        if (dialogResult.equals(""))
                        {
                            value = null;
                        }
                        else
                        {
                            value = Integer.valueOf(dialogResult);
                        }

                        p.setPriority(value);

                        ui.updatePoint(p, parent.getMainGraph());
                        ui.repaintAutoLocList(false);
                    }
                    catch (NumberFormatException e)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.errorInvalidPriority")
                        );
                    }
                }
            });

            submenu.add(menuItem);
        }

        // Excluded locomotives
        menuItem = new JMenuItem(
            I18n.f("autolayout.ui.menuExcludedLocomotives", p.getExcludedLocs().size())
        );
        menuItem.setToolTipText(
            I18n.f("autolayout.ui.tooltip.ExcludedLocomotives", "Control+E/U")
        );
        menuItem.addActionListener(event ->
        {
            try
            {
                GraphLocExclude edit = new GraphLocExclude(ui, p);

                int dialogResult2 = JOptionPane.showOptionDialog(
                    (Component) parent.getSwingView(),
                    edit,
                    I18n.f("autolayout.ui.dialogEditExcludedLocomotives", p.getName()),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    TrainControlUI.OK_CANCEL_OPTS,
                    TrainControlUI.OK_CANCEL_OPTS[0]
                );

                if (dialogResult2 == JOptionPane.OK_OPTION)
                {
                    p.setExcludedLocs(edit.getSelectedExcludeLocs());
                }

                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(true);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(
                    (Component) parent.getSwingView(),
                    I18n.f("autolayout.ui.errorEditPoint", e.getMessage())
                );
            }
        });

        submenu.add(menuItem);

        // Speed multiplier
        menuItem = new JMenuItem(
            I18n.f("autolayout.ui.menuSpeedMultiplier", p.getSpeedMultiplierPercent())
        );
        menuItem.addActionListener(event ->
        {
            try
            {
                Object input = JOptionPane.showInputDialog(
                    (Component) parent.getSwingView(),
                    I18n.f("autolayout.ui.promptEnterSpeedMultiplier", nodeName),
                    I18n.t("autolayout.ui.dialogSetSpeedMultiplier"),
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    p.getSpeedMultiplierPercent()
                );

                if (input != null && input instanceof String)
                {
                    p.setSpeedMultiplier(Integer.parseInt(input.toString()) * 0.01);
                }
            }
            catch (NumberFormatException ex)
            {
                JOptionPane.showMessageDialog(
                    (Component) parent.getSwingView(),
                    I18n.t("autolayout.ui.errorInvalidSpeedMultiplier"),
                    I18n.t("error.error"),
                    JOptionPane.ERROR_MESSAGE
                );
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(
                    (Component) parent.getSwingView(),
                    ex.getMessage(),
                    I18n.t("error.error"),
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });

        menuItem.setToolTipText(
            I18n.t("autolayout.ui.tooltip.SpeedMultiplier")
        );
        submenu.add(menuItem);

        add(submenu);
        
        addSeparator();

        // Allow changes because locomotives on non-stations will by design not run
        //if (!p.isDestination() || !p.isOccupied())        
        JCheckBoxMenuItem stationCheckbox = new JCheckBoxMenuItem(I18n.t("autolayout.ui.markAsStation"), p.isDestination());
        stationCheckbox.addItemListener(event ->
        {
            try
            {
                if (!p.hasS88()) parent.setS88(p);
                
                p.setDestination(stationCheckbox.isSelected());
                // Refresh UI updates
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(stationCheckbox);

        // Create "Terminus Station" checkbox
        JCheckBoxMenuItem terminusCheckbox = new JCheckBoxMenuItem(
            I18n.t("autolayout.ui.checkboxMarkTerminusStation"),
            p.isTerminus()
        );

        JCheckBoxMenuItem reversingCheckbox = new JCheckBoxMenuItem(
            I18n.t("autolayout.ui.checkboxMarkReversingPoint"),
            p.isReversing()
        );

        stationCheckbox.setToolTipText(
            I18n.t("autolayout.ui.tooltip.Station")
        );

        terminusCheckbox.setToolTipText(
            I18n.t("autolayout.ui.tooltip.TerminusStation")
        );

        reversingCheckbox.setToolTipText(
            I18n.t("autolayout.ui.tooltip.ReversingPoint")
        );

        terminusCheckbox.addItemListener(event ->
        {
            try
            {
                if (!p.hasS88()) parent.setS88(p);
                
                p.setDestination(true);
                
                if (p.isReversing())
                {
                    p.setReversing(false);
                }

                p.setTerminus(terminusCheckbox.isSelected());
                // Refresh UI updates
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(terminusCheckbox);

        // Create "Reversing Point" checkbox
        reversingCheckbox.addItemListener(event ->
        {
            try
            {
                if (p.isTerminus())
                {
                    p.setTerminus(false);
                }
                
                p.setReversing(reversingCheckbox.isSelected());
                // Refresh UI updates
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(reversingCheckbox);

        addSeparator();

        // Enable/disable point
        // Create a checkbox menu item for the active state
        JCheckBoxMenuItem activeCheckbox = new JCheckBoxMenuItem(
            I18n.t("autolayout.ui.checkboxActive"),
            p.isActive()
        );

        activeCheckbox.setToolTipText(
            I18n.t("autolayout.ui.tooltip.Active")
        );
        activeCheckbox.addItemListener(event ->
        {
            try
            {
                // Toggle the active state of the point
                p.setActive(activeCheckbox.isSelected());
                // Refresh the UI after the change
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(activeCheckbox);  

        // Add edge
        addSeparator();
        
        final String lastClickedNode = parent.getLastClickedNode();

        if (lastClickedNode != null && !lastClickedNode.equals(p.getName()))
        {
            menuItem = new JMenuItem(
                I18n.f("autolayout.ui.menuConnectToNode", lastClickedNode)
            );
            menuItem.setToolTipText(
                I18n.t("autolayout.ui.tooltip.lastClickedNode")
            );
            menuItem.addActionListener(event ->
            {
                try
                {
                    if (ui.getModel().getAutoLayout().getPoint(lastClickedNode) == null)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.errorPointDoesNotExist")
                        );
                    }
                    else
                    {
                        // Add the edge
                        ui.getModel().getAutoLayout().createEdge(nodeName, lastClickedNode);

                        Edge e = ui.getModel().getAutoLayout().getEdge(nodeName, lastClickedNode);

                        ui.addEdge(e, parent.getMainGraph());
                        ui.repaintAutoLocList(false);
                        ui.getModel().getAutoLayout().refreshUI();
                        parent.setLastClickedNode(null);
                    }
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(
                        (Component) parent.getSwingView(),
                        I18n.f("autolayout.ui.errorAddEdge", e.getMessage())
                    );
                }
            });

            add(menuItem);
        }

        menuItem = new JMenuItem(
            I18n.t("autolayout.ui.menuConnectToPoint")
        );
        menuItem.addActionListener(event ->
        {
            // Get all point names except this one
            Collection<Point> points = ui.getModel().getAutoLayout().getPoints();
            List<String> pointNames = new LinkedList<>();

            for (Point p2 : points)
            {
                pointNames.add(p2.getName());
            }

            Collections.sort(pointNames);

            // Remove self and all existing neighbors
            pointNames.remove(nodeName);

            for (Edge e2 : ui.getModel().getAutoLayout().getNeighbors(p))
            {
                pointNames.remove(e2.getEnd().getName());
            }

            if (!pointNames.isEmpty())
            {
                String dialogResult = (String) JOptionPane.showInputDialog(
                    (Component) parent.getSwingView(),
                    I18n.f("autolayout.ui.promptChooseConnectionTarget", nodeName),
                    I18n.t("autolayout.ui.dialogAddNewEdge"),
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    pointNames.toArray(),
                    pointNames.get(0)
                );

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        if (ui.getModel().getAutoLayout().getPoint(dialogResult) == null)
                        {
                            JOptionPane.showMessageDialog(
                                (Component) parent.getSwingView(),
                                I18n.t("autolayout.ui.errorPointDoesNotExist")
                            );
                        }
                        else
                        {
                            // Add the edge
                            ui.getModel().getAutoLayout().createEdge(nodeName, dialogResult);

                            Edge e = ui.getModel().getAutoLayout().getEdge(nodeName, dialogResult);

                            ui.addEdge(e, parent.getMainGraph());
                            ui.repaintAutoLocList(false);
                            ui.getModel().getAutoLayout().refreshUI();
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.errorAddEdge")
                        );
                    }
                }
            }
            else
            {
                JOptionPane.showMessageDialog(
                    (Component) parent.getSwingView(),
                    I18n.t("autolayout.ui.infoNoOtherPointsToConnect")
                );
            }
        });

        add(menuItem);
        
        if (!ui.getModel().getAutoLayout().getNeighborsAndIncoming(p).isEmpty())
        {
            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.menuEditEdge")
            );

            menuItem.addActionListener(event ->
            {
                // This will return not null if the window is visible
                if (parent.getGraphEdgeEditor() != null)
                {
                    JOptionPane.showMessageDialog(
                        (Component) parent.getSwingView(),
                        I18n.t("autolayout.ui.errorOnlyOneEdgeEditor")
                    );
                    parent.getGraphEdgeEditor().toFront();
                    parent.getGraphEdgeEditor().requestFocus();
                    return;
                }

                // Get all point names except this one
                List<String> edgeNames = new LinkedList<>();

                for (Edge e2 : ui.getModel().getAutoLayout().getNeighborsAndIncoming(p))
                {
                    edgeNames.add(e2.getName());
                }

                String dialogResult;

                // Only prompt if there are multiple outgoing edges
                if (edgeNames.size() > 1)
                {
                    dialogResult = showEdgeSelectionDialog(
                        parent,
                        edgeNames,
                        I18n.t("autolayout.ui.promptSelectEdgeToEdit")
                    );
                }
                else
                {
                    dialogResult = edgeNames.get(0);
                }

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        if (ui.getModel().getAutoLayout().getEdge(dialogResult) == null)
                        {
                            JOptionPane.showMessageDialog(
                                (Component) parent.getSwingView(),
                                I18n.t("autolayout.ui.errorEdgeDoesNotExist")
                            );
                        }
                        else
                        {
                            parent.setGraphEdgeEdit(
                                new GraphEdgeEdit(
                                    ui,
                                    parent.getMainGraph(),
                                    ui.getModel().getAutoLayout().getEdge(dialogResult)
                                )
                            );

                            // Align with main window. Graph window coordinates are not correct for some reason
                            parent.getGraphEdgeEdit().setLocation(
                                ui.getX() + (ui.getWidth() - parent.getGraphEdgeEdit().getWidth()) / 2,
                                ui.getY() + (ui.getHeight() - parent.getGraphEdgeEdit().getHeight()) / 2
                            );
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.f("autolayout.ui.errorEditEdge", e.getMessage())
                        );
                    }
                }
            });

            add(menuItem);
        }

        // Copy edge
        if (!ui.getModel().getAutoLayout().getNeighbors(p).isEmpty())
        {
            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.menuCopyOutgoingEdge")
            );
            menuItem.addActionListener(event ->
            {
                // Get all point names except this one
                List<String> edgeNames = new LinkedList<>();

                for (Edge e2 : ui.getModel().getAutoLayout().getNeighbors(p))
                {
                    edgeNames.add(e2.getName());
                }

                Collections.sort(edgeNames);

                String dialogResult;

                // Only prompt if there are multiple outgoing edges
                if (edgeNames.size() > 1)
                {
                    dialogResult = showEdgeSelectionDialog(
                        parent,
                        edgeNames,
                        I18n.t("autolayout.ui.promptSelectEdgeToCopy")
                    );
                }
                else
                {
                    dialogResult = edgeNames.get(0);
                }

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        if (ui.getModel().getAutoLayout().getEdge(dialogResult) == null)
                        {
                            JOptionPane.showMessageDialog(
                                (Component) parent.getSwingView(),
                                I18n.t("autolayout.ui.errorEdgeDoesNotExist")
                            );
                        }
                        else
                        {
                            Edge original = ui.getModel().getAutoLayout().getEdge(dialogResult);

                            ui.highlightLockedEdges(original, new LinkedList<>());

                            String[] options = {
                                I18n.t("autolayout.ui.optionStart"),
                                I18n.t("autolayout.ui.optionEnd")
                            };

                            int res = JOptionPane.showOptionDialog(
                                (Component) parent.getSwingView(),
                                I18n.t("autolayout.ui.promptChangeWhichPoint"),
                                I18n.t("autolayout.ui.dialogCopyType"),
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                options,
                                options[0]
                            );

                            // Do nothing after pressing escape
                            if (res != JOptionPane.YES_OPTION && res != JOptionPane.NO_OPTION)
                            {
                                ui.highlightLockedEdges(null, null);
                                return;
                            }

                            boolean changeEnd = (res == 1);

                            // Get all point names except this one
                            Collection<Point> points = ui.getModel().getAutoLayout().getPoints();
                            List<String> pointNames = new LinkedList<>();

                            for (Point p2 : points)
                            {
                                pointNames.add(p2.getName());
                            }

                            Collections.sort(pointNames);

                            // Remove self and all existing neighbors
                            pointNames.remove(nodeName);

                            if (changeEnd)
                            {
                                for (Edge e2 : ui.getModel().getAutoLayout().getNeighbors(p))
                                {
                                    pointNames.remove(e2.getEnd().getName());
                                }
                            }
                            else
                            {
                                // Remove current end
                                pointNames.remove(original.getEnd().getName());
                            }

                            if (!pointNames.isEmpty())
                            {
                                dialogResult = (String) JOptionPane.showInputDialog(
                                    (Component) parent.getSwingView(),
                                    I18n.f(
                                        "autolayout.ui.promptChooseConnectionTargetCopy",
                                        (changeEnd
                                            ? I18n.t("autolayout.ui.wordTo")
                                            : I18n.t("autolayout.ui.wordFrom"))
                                    ),
                                    I18n.t("autolayout.ui.dialogCopyEdge"),
                                    JOptionPane.QUESTION_MESSAGE,
                                    null,
                                    pointNames.toArray(),
                                    pointNames.get(0)
                                );

                                if (dialogResult != null && !"".equals(dialogResult))
                                {
                                    Edge e = ui.getModel().getAutoLayout().copyEdge(original, dialogResult, changeEnd);

                                    ui.addEdge(e, parent.getMainGraph());
                                    ui.repaintAutoLocList(false);
                                    ui.getModel().getAutoLayout().refreshUI();
                                }
                            }
                            else
                            {
                                throw new Exception(
                                    I18n.t("autolayout.ui.errorNoValidPointsToConnect")
                                );
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.f("autolayout.ui.errorCopyEdge", e.getMessage())
                        );
                    }

                    ui.highlightLockedEdges(null, null);
                }
            });

            add(menuItem);
        }
        
        if (p.isOccupied())
        {
            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.testconnection")
            );
            menuItem.addActionListener(event ->
            {
                // Get all point names except this one
                Collection<Point> points = ui.getModel().getAutoLayout().getPoints();
                List<String> pointNames = new LinkedList<>();

                for (Point p2 : points)
                {
                    if (p2.isDestination())
                    {
                        pointNames.add(p2.getName());
                    }
                }

                Collections.sort(pointNames);

                // Remove self
                pointNames.remove(nodeName);

                if (!pointNames.isEmpty())
                {
                    String dialogResult = (String) JOptionPane.showInputDialog(
                        (Component) parent.getSwingView(),
                        I18n.f("autolayout.ui.promptChooseConnectionTarget", nodeName),
                        I18n.t("autolayout.ui.dialogSelectPoint"),
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        pointNames.toArray(),
                        pointNames.get(0)
                    );

                    if (dialogResult != null && !"".equals(dialogResult))
                    {
                        try
                        {
                            if (ui.getModel().getAutoLayout().getPoint(dialogResult) == null)
                            {
                                JOptionPane.showMessageDialog(
                                    (Component) parent.getSwingView(),
                                    I18n.t("autolayout.ui.errorPointDoesNotExist")
                                );
                            }
                            else
                            {
                                Map<List<Edge>, String> output =
                                    ui.getModel()
                                      .getAutoLayout()
                                      .debugPath(
                                          p.getCurrentLocomotive(),
                                          p,
                                          ui.getModel().getAutoLayout().getPoint(dialogResult)
                                      );

                                StringBuilder sb = new StringBuilder();

                                // === Valid Paths ===
                                sb.append(I18n.t("autolayout.ui.labelValidPaths")).append("\n");

                                long validCount = output.entrySet()
                                    .stream()
                                    .filter(e -> e.getValue() == null)
                                    .peek(e ->
                                    {
                                        sb.append(
                                            ui.getModel().getAutoLayout().pathToString(e.getKey())
                                        ).append("\n\n");
                                    })
                                    .count();

                                if (validCount == 0)
                                {
                                    sb.append(I18n.t("autolayout.ui.labelNone")).append("\n");
                                }

                                sb.append("\n").append(I18n.t("autolayout.ui.labelInvalidPaths")).append("\n");

                                long invalidCount = output.entrySet()
                                    .stream()
                                    .filter(e -> e.getValue() != null)
                                    .peek(e ->
                                    {
                                        sb.append(
                                            ui.getModel().getAutoLayout().pathToString(e.getKey())
                                        )
                                          .append("\n")
                                          .append(I18n.t("autolayout.ui.labelReason"))
                                          .append(" ")
                                          .append(e.getValue())
                                          .append("\n\n");
                                    })
                                    .count();

                                if (invalidCount == 0)
                                {
                                    sb.append(I18n.t("autolayout.ui.labelNone")).append("\n");
                                }

                                // Prepare scrollable text area
                                JTextArea textArea = new JTextArea(sb.toString());
                                textArea.setEditable(false);
                                textArea.setLineWrap(true);
                                textArea.setWrapStyleWord(true);

                                JScrollPane scrollPane = new JScrollPane(textArea);
                                scrollPane.setPreferredSize(new Dimension(800, 400));

                                // Build label with start and end
                                String title =
                                    I18n.f(
                                        "autolayout.ui.dialogPathDebugResults",
                                        p.getName(),
                                        ui.getModel().getAutoLayout().getPoint(dialogResult).getName()
                                    );

                                // Show dialog
                                JOptionPane.showMessageDialog(
                                    ui,
                                    scrollPane,
                                    title,
                                    JOptionPane.INFORMATION_MESSAGE
                                );
                            }
                        }
                        catch (Exception e)
                        {
                            
                        }
                    }
                }
                else
                {
                    JOptionPane.showMessageDialog(
                        (Component) parent.getSwingView(),
                        I18n.t("autolayout.ui.infoNoOtherPointsToConnect")
                    );
                }
            });

            add(menuItem);
        }

        // Delete edges
        List<Edge> neighbors = ui.getModel().getAutoLayout().getNeighborsAndIncoming(p);
        if (!neighbors.isEmpty())
        {
            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.menuDeleteEdge")
            );
            menuItem.setForeground(Color.RED);
            menuItem.addActionListener(event ->
            {
                List<String> edgeNames = new LinkedList<>();

                for (Edge e2 : ui.getModel().getAutoLayout().getNeighborsAndIncoming(p))
                {
                    edgeNames.add(e2.getName());
                }

                String dialogResult;

                if (edgeNames.size() > 1)
                {
                    dialogResult = showEdgeSelectionDialog(
                        parent,
                        edgeNames,
                        I18n.t("autolayout.ui.promptSelectEdgeToDelete")
                    );
                }
                else
                {
                    dialogResult = edgeNames.get(0);
                }

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        Edge e = ui.getModel().getAutoLayout().getEdge(dialogResult);

                        List<Edge> highlight = new LinkedList<>();
                        highlight.add(e);

                        ui.highlightLockedEdges(null, highlight);

                        int confirmation = JOptionPane.showOptionDialog(
                            (Component) parent.getSwingView(),
                            I18n.f(
                                "autolayout.ui.confirmDeleteEdge",
                                e.getStart().getName(),
                                e.getEnd().getName()
                            ),
                            I18n.t("autolayout.ui.dialogEdgeDeletion"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            TrainControlUI.YES_NO_OPTS,
                            TrainControlUI.YES_NO_OPTS[0]
                        );

                        if (confirmation == JOptionPane.YES_OPTION)
                        {
                            ui.getModel().getAutoLayout().deleteEdge(e.getStart().getName(), e.getEnd().getName());
                            parent.getMainGraph().removeEdge(e.getUniqueId());
                            ui.getModel().getAutoLayout().refreshUI();
                            ui.repaintAutoLocList(false);
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.f("autolayout.ui.errorDeleteEdge", e.getMessage())
                        );
                    }

                    ui.highlightLockedEdges(null, null);
                }
            });

            add(menuItem);
        }

        // Rename option applicable to all nodes
        addSeparator();

        menuItem = new JMenuItem(
            I18n.f("autolayout.ui.menuRenamePoint", nodeName)
        );
        menuItem.addActionListener(event ->
        {
            String dialogResult = JOptionPane.showInputDialog(
                (Component) parent.getSwingView(),
                I18n.t("autolayout.ui.promptEnterNewPointName"),
                nodeName
            );

            if (dialogResult != null && !"".equals(dialogResult))
            {
                try
                {
                    if (ui.getModel().getAutoLayout().getPoint(dialogResult) != null)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.errorStationNameInUse")
                        );
                    }
                    else
                    {
                        ui.getModel().getAutoLayout().renamePoint(nodeName, dialogResult);
                        ui.repaintAutoLocList(false);
                        ui.updatePoint(p, parent.getMainGraph());
                    }
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(
                        (Component) parent.getSwingView(),
                        I18n.t("autolayout.ui.errorRenameNode")
                    );
                }
            }
        });

        add(menuItem);

        // Delete point
        addSeparator();

        menuItem = new JMenuItem(
            I18n.t("autolayout.ui.menuDeletePoint")
        );
        menuItem.setForeground(Color.RED);
        menuItem.addActionListener(event ->
        {
            int dialogResult = JOptionPane.showOptionDialog(
                (Component) parent.getSwingView(),
                I18n.f("autolayout.ui.confirmDeletePoint", nodeName),
                I18n.t("autolayout.ui.dialogPointDeletion"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[0] // default selection = "Yes"
            );

            if (dialogResult == JOptionPane.YES_OPTION)
            {
                try
                {
                    parent.setLastClickedNode(null);
                    parent.setLastHoveredNode(null);
                    ui.getModel().getAutoLayout().deletePoint(p.getName());
                    parent.getMainGraph().removeNode(p.getUniqueId());
                    ui.repaintAutoLocList(false);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(
                        (Component) parent.getSwingView(),
                        I18n.f("autolayout.ui.errorDeletePoint", e.getMessage())
                    );
                }
            }
        });   

        add(menuItem);    
    }
    
    private String showEdgeSelectionDialog(Component parent, java.util.List<String> edgeNames, String message)
    {
        JComboBox<String> edgeDropdown = new JComboBox<>(edgeNames.toArray(new String[0]));

        tcui.highlightLockedEdges(tcui.getModel().getAutoLayout().getEdge(edgeNames.get(0)), new LinkedList<>());
        
        // Add an ItemListener to detect when the user selects a different option
        edgeDropdown.addItemListener((ItemEvent e) ->
        {
            if (e.getStateChange() == ItemEvent.SELECTED)
            {
                String selectedEdge = (String) e.getItem();
                
                handleEdgeSelection(selectedEdge);
            }
        });

        // Show the JComboBox in a JOptionPane
        int result = JOptionPane.showOptionDialog(
            parent,
            edgeDropdown,
            message,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            TrainControlUI.OK_CANCEL_OPTS,
            TrainControlUI.OK_CANCEL_OPTS[0]
        );
        
        tcui.highlightLockedEdges(null, null);

        // If the user clicks "OK," retrieve the selected item
        if (result == JOptionPane.OK_OPTION)
        {
            return (String) edgeDropdown.getSelectedItem();
        }
        // Cancel or close
        else
        {
            return null;
        }
    }

    // Custom method to handle the selected edge
    private void handleEdgeSelection(String edgeName)
    {
        tcui.highlightLockedEdges(tcui.getModel().getAutoLayout().getEdge(edgeName), new LinkedList<>());        
    }
}
   