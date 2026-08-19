package org.traincontrol.gui;

import com.formdev.flatlaf.ui.FlatLineBorder;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

/**
 * Displays the status of a locomotive in autonomous operation
 */
public final class AutoLocomotiveStatus extends javax.swing.JPanel
{
    private final Locomotive locomotive;
    private List<List<Edge>> paths;
    private final Layout layout;
    private final ViewListener control;
    private final TrainControlUI parent;
    
    /**
     * Creates new form AutoLocomotiveStatus
     * @param loc
     * @param parent
     */
    public AutoLocomotiveStatus(Locomotive loc, TrainControlUI parent)
    {
        this(loc, parent, false);
    }

    /**
     * @param deferSearch true to skip the route search this normally does while being built.
     *
     * The search is synchronized on the Layout and walks the whole graph, so doing it per panel while
     * the list is rebuilt stalls the event thread and can block it on a monitor a driving thread holds.
     * A caller that passes true is promising to call findPaths off the event thread and hand the result
     * back through updateState - which is what the full list repaint does.
     */
    public AutoLocomotiveStatus(Locomotive loc, TrainControlUI parent, boolean deferSearch)
    {
        this.parent = parent;
        this.control = parent.getModel();
        this.layout = this.control.getAutoLayout();
        this.locomotive = loc;
        initComponents();
        
        this.locName.setText(locomotive.getName());
        
        // Style labels
        locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), new Color(0,0,115), 1, 999));
        locStation.setBackground(new Color(0,0,115));
        locStation.setForeground(new Color(255,255,255));
        
        // With the search deferred, the panel is drawn from what is known without walking the graph -
        // name, station, direction - and its route list is filled in when the worker returns.
        updateState(loc, null, deferSearch);
        
        Font font = new Font("Arial Unicode MS", Font.PLAIN, 12); 
        if (font.canDisplay('\u23F8'))
        {
            pauseButton.setFont(font); 
            pauseButton.setText("\u23F8");
        }
                
        this.setVisible(true);
        
        // Propagate scroll event for short lists
        availablePathScroll.addMouseWheelListener(e ->
        {
            // JScrollBar verticalBar = availablePathScroll.getVerticalScrollBar();

            // If vertical scrollbar is not visible or can't scroll further
            //boolean noScrollNeeded = !verticalBar.isVisible();
                // || (verticalBar.getMaximum() - verticalBar.getVisibleAmount() <= verticalBar.getValue() &&
                // e.getWheelRotation() > 0) || // scrolling down at bottom
                // (verticalBar.getValue() == 0 && e.getWheelRotation() < 0); // scrolling up at top

            if (!availablePathScroll.getVerticalScrollBar().isVisible())
            {
                // Forward event to parent scroll pane
                parent.getAutoLocScroll().dispatchEvent(SwingUtilities.convertMouseEvent(
                    availablePathScroll, e, parent.getAutoLocScroll()));
            }
        });
    }

    /**
     * The " -" suffix marking a station full autonomy will never send this locomotive to.
     *
     * Two reasons, one meaning to the operator.  The station may exclude this particular locomotive,
     * or it may be a reversing station: a parking berth, which autonomy no longer chooses, leaving it
     * to a route picked by hand or to "return home".  Both answer the same question,
     * which is why they share a marker rather than accumulating a symbol each: this list offers the
     * berth, because you may still choose it yourself, and the dash says autonomy will not.
     *
     * Reversing NON-stations never appear here - getPossiblePaths only enumerates destinations - so
     * the full predicate is spelled out to match the rule in Layout rather than relying on that.
     */
    private static String notChosenByAutonomy(Point p, Locomotive loc)
    {
        if (p == null) return "";

        return (p.isReversing() && p.isDestination()) || p.getExcludedLocs().contains(loc) ? " -" : "";
    }

    /**
     * Refreshes the available routes shown in the UI
     * @param someLoc 
     */
    /**
     * The path search, on its own, so it can be done off the event thread.
     *
     * getPossiblePaths is synchronized on the Layout and searches it whole, once per locomotive panel,
     * on every arrival and departure.  Run on the EDT that stalls the interface for as long as it
     * takes - and worse, it lets the EDT block on the Layout's monitor while a driving thread holds
     * it, which is a deadlock the application could not have before.
     *
     * Safe off the EDT because it touches no Swing state: it asks the layout a question and returns
     * the answer, and the caller decides what to draw.
     *
     * @return the paths worth showing, or null when the layout is busy and the list is not shown
     */
    public List<List<Edge>> findPaths()
    {
        if (layout == null || locomotive == null) return null;

        if (layout.isAutoRunning() || layout.getLocomotiveLocation(locomotive) == null) return null;

        // The legend reads come from here too, on this thread, for the reason above.
        this.gatheredTimetableStart = layout.getTimetableStartingPoint(locomotive);
        this.haveGatheredTimetableStart = true;

        return withoutGoingNowhere(layout.getPossiblePaths(locomotive, true));
    }

    /**
     * The timetable start, from the gather where there was one and from the Layout otherwise.
     */
    private Point timetableStart()
    {
        return haveGatheredTimetableStart ? gatheredTimetableStart
            : layout.getTimetableStartingPoint(locomotive);
    }

    public Locomotive getLocomotive()
    {
        return this.locomotive;
    }

    /**
     * The timetable's starting point for this locomotive, read when the paths were.
     *
     * getTimetableStartingPoint is synchronized on the Layout, and this panel reads it three times per
     * repaint.  A map lookup costs nothing, but TAKING that monitor on the event thread can cost
     * everything: configureAndLockPath holds it across its per-command sleeps, half a second to two
     * seconds per path, and the interface is frozen for as long as it does.  Moving the path search off
     * the event thread and leaving these behind would have left the stall in place and made it look
     * fixed.
     *
     * Null when nothing was gathered, in which case the reads happen inline as before.
     */
    private Point gatheredTimetableStart;

    private boolean haveGatheredTimetableStart;

    public void updateState(Locomotive someLoc)
    {
        updateState(someLoc, null, false);
    }

    /**
     * @param found paths already worked out by findPaths, or null to search here
     * @param haveFound whether {@code found} is to be used as it stands rather than searched for
     *        here.  Callers that ran findPaths pass {@code found != null}, because findPaths answers
     *        null when it did not search at all; the deferred build passes true with a null found,
     *        which is "no answer, and do not go and get one".
     */
    public void updateState(Locomotive someLoc, List<List<Edge>> found, boolean haveFound)
    {
        // We only need to update if the callback corresponding to our locomotive was fired
        if (someLoc == null || someLoc.equals(locomotive))
        {
            DefaultListModel<String> pathList = new DefaultListModel<>();
            
            this.locDest.setForeground(new Color(0, 0, 115));
            
            // Ensure consistent state
            this.pauseButton.setSelected(locomotive.isAutonomyPaused());
                     
            // Assigned to this locomotive - the same question the graph outlines a station on, and
            // deliberately not getHomeStation.
            //
            // getHomeStation answers with the positional fallback as well: with no assignments at all,
            // every placed locomotive's home is wherever it stood when the graph loaded, so the badge
            // went teal for all of them while the graph outlined nothing.  Worse, assigning one
            // locomotive a home elsewhere makes rebuildHomeStations refuse the fallback claims that
            // collide with it, so the number of teal badges changed for locomotives nobody had touched.
            //
            // Reading the point the locomotive is standing on answers only what was actually assigned,
            // matches the graph exactly, and takes no lock - this runs on the EDT.
            final Point at = layout.getLocomotiveLocation(locomotive);
            final boolean atAssignedHome = at != null && locomotive.getName().equals(at.getHomeLoc());

            // Grey out locomotives on inactive points / not on the graph
            if ((layout.getLocomotiveLocation(locomotive) != null && !layout.getLocomotiveLocation(locomotive).isActive()) ||
                    layout.getLocomotiveLocation(locomotive) == null
            )
            {
                this.locName.setForeground(Color.LIGHT_GRAY);
                this.pauseButton.setVisible(false);
                
                // Grey out label
                locStation.setBackground(Color.LIGHT_GRAY);
                locStation.setForeground(Color.WHITE);
                locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), Color.LIGHT_GRAY, 1, 999));
            }
            else if (atAssignedHome)
            {
                this.locName.setForeground(Color.BLACK);
                this.pauseButton.setVisible(true);

                // Standing on its home station, in the teal the graph outlines such a station with.
                //
                // White text like every other state of this badge.  The teal is a light colour - it was
                // picked to read against the graph's dark blue fill - so this is the lowest-contrast
                // pairing the badge has; it holds because the text is short and the badge is small, and
                // a colour of its own for this one state would read as a different kind of thing.
                locStation.setBackground(TrainControlUI.COLOR_AT_HOME);
                locStation.setForeground(Color.WHITE);
                locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), TrainControlUI.COLOR_AT_HOME, 1, 999));
            }
            else
            {
                this.locName.setForeground(Color.BLACK);
                this.pauseButton.setVisible(true);
                
                // Restore label color
                locStation.setBackground(new Color(0,0,115));
                locStation.setForeground(Color.WHITE);
                locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), new Color(0,0,115), 1, 999 ));
            }
            
            this.locStation.setToolTipText("");

            // Locomotive is running - show the path and hide the list
            if (layout.getActiveLocomotives().containsKey(locomotive))
            {
                List<Point> milestones = layout.getReachedMilestones(locomotive);
                
                // Named as the diagram names them.  Edge.pathToString spells out the Points, and on a
                // derived graph a station is several of those - so a panel reading "BottomMainA
                // (eastbound) -> Tunnel (southbound)" is telling the user about the arrival-side split,
                // which is machinery they never asked to see and cannot act on.  The log keeps the
                // Point names, where they are worth having.
                this.locDest.setText(describePath(layout.getActiveLocomotives().get(locomotive)));
                
                this.locStation.setText("@" + stationName(milestones.get(milestones.size() - 1)));
                
                this.locDest.setForeground(new Color(204, 0, 0));
                this.locAvailPaths.setVisible(false);
            }
            // Layout is in auto mode but loc is not running - show status message and hide the list
            else if (layout.isAutoRunning())
            {
                if (layout.getLocomotiveLocation(locomotive) != null)
                {
                    this.locDest.setText(I18n.t("autolayout.ui.noActivePath"));
                    this.locStation.setText("@" + stationName(layout.getLocomotiveLocation(locomotive)));
                }
                else
                {
                    this.locStation.setText("?????");
                    this.locDest.setText(I18n.t("autolayout.ui.locNotOnGraph"));
                }
                                
                this.locAvailPaths.setVisible(false);
            }
            // Layout is standing by.  Show the list.
            else
            {                
                // Already searched, if somebody did it off the EDT for us.  Searching here is the
                // fallback for the callers that have no worker to do it on, and it is the reason this
                // method still can.
                //
                // haveFound means "the caller has an answer, do not search".  It is NOT the same as
                // "the caller called findPaths": findPaths returns null when it did not search - the
                // layout was running, or the locomotive is not on the graph - and a null stamped as an
                // answer reads here as "searched, found nothing", which prints "no available paths".
                //
                // That is a race at the end of a run, and it latches.  findPaths bails because the
                // layout is running; by the time the drawing runnable reaches the event thread the run
                // has ended, so this branch is taken, and nothing repaints the list again to correct
                // it.  The callers now pass haveFound = (answer != null) for exactly that reason.
                //
                // The deferred build is the other side of it: that one has no answer AND does not want
                // one yet, which is haveFound with a null found, and it must not search.
                // true -> Only include unique starts/end pairs
                List<List<Edge>> answer = haveFound ? found
                    : withoutGoingNowhere(layout.getPossiblePaths(locomotive, true));

                this.paths = answer == null ? new java.util.LinkedList<>() : answer;
                
                if (!this.paths.isEmpty())
                {
                    this.locDest.setText(I18n.t("autolayout.ui.doubleClickExecute"));
                    this.locStation.setText("@" + stationName(layout.getLocomotiveLocation(locomotive))                     
                        + (layout.getLocomotiveLocation(locomotive).equals(timetableStart()) ? " *" : "")
                        + notChosenByAutonomy(layout.getLocomotiveLocation(locomotive), locomotive)

                    );
                }
                else if (layout.getLocomotiveLocation(locomotive) != null)
                {                    
                    this.locDest.setText(I18n.t("autolayout.ui.noAvailPaths"));

                    // And WHY, on hover.  "No available paths" is the symptom; the reason is a
                    // different thing every time - a station occupied, one switched off, an exclusion,
                    // no track at all - and until now the user had no way to find out which, because
                    // the answer was computed on every attempt and thrown away.
                    this.locDest.setToolTipText(whyNotToolTip());
                    this.locStation.setText("@" +  stationName(layout.getLocomotiveLocation(locomotive))
                        + (layout.getLocomotiveLocation(locomotive).equals(timetableStart()) ? " *" : "")
                        + notChosenByAutonomy(layout.getLocomotiveLocation(locomotive), locomotive)
                    );
                }
                else
                {
                    this.locStation.setText("?????");
                    this.locDest.setText(I18n.t("autolayout.ui.locNotOnGraph"));
                }
                
                this.locStation.setToolTipText(I18n.t("autolayout.ui.tooltip.currentLocation"));
                
                // Sort the list
                this.paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));
                
                for (List<Edge> path : this.paths)
                {
                    pathList.add(pathList.getSize(), "-> " + stationName(path.get(path.size() - 1).getEnd())
                        + (path.get(path.size() - 1).getEnd().equals(timetableStart()) ? " *" : "")
                        + notChosenByAutonomy(path.get(path.size() - 1).getEnd(), locomotive)
                    );
                    //Edge.pathToString(path));
                }
                
                this.locAvailPaths.setVisible(true);
            }

            this.locAvailPaths.setModel(pathList);
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        locName = new javax.swing.JLabel();
        locDest = new javax.swing.JLabel();
        availablePathScroll = new javax.swing.JScrollPane();
        locAvailPaths = new javax.swing.JList<>();
        locStation = new javax.swing.JLabel();
        pauseButton = new javax.swing.JToggleButton();

        setBackground(new java.awt.Color(255, 255, 255));
        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));
        setFocusable(false);
        setMaximumSize(new java.awt.Dimension(219, 223));
        setPreferredSize(new java.awt.Dimension(219, 223));

        locName.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        locName.setText("locName");
        locName.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        locName.setFocusable(false);
        locName.setMaximumSize(new java.awt.Dimension(205, 25));
        locName.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                locNameMouseClicked(evt);
            }
        });

        locDest.setFont(new java.awt.Font("Segoe UI", 1, 11)); // NOI18N
        locDest.setForeground(new java.awt.Color(0, 0, 115));
        locDest.setText("locDest");
        locDest.setFocusable(false);

        locAvailPaths.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        locAvailPaths.setFocusable(false);
        locAvailPaths.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseMoved(java.awt.event.MouseEvent evt) {
                locAvailPathsMouseMoved(evt);
            }
        });
        locAvailPaths.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                locAvailPathsMouseClicked(evt);
            }
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                locAvailPathsMouseEntered(evt);
            }
        });
        availablePathScroll.setViewportView(locAvailPaths);

        locStation.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        locStation.setForeground(new java.awt.Color(0, 0, 115));
        locStation.setText("locStation");
        locStation.setFocusable(false);

        pauseButton.setText("P");
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/traincontrol/resources/messages"); // NOI18N
        pauseButton.setToolTipText(bundle.getString("autolayout.ui.tooltip.tempPauseLoc")); // NOI18N
        pauseButton.setFocusable(false);
        pauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(availablePathScroll)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(locName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pauseButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(locStation)
                            .addComponent(locDest))
                        .addGap(0, 143, Short.MAX_VALUE)))
                .addGap(6, 6, 6))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pauseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locStation)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(locDest)
                .addGap(6, 6, 6)
                .addComponent(availablePathScroll, javax.swing.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE)
                .addGap(6, 6, 6))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Why this locomotive has nowhere to go, as a tooltip.
     *
     * Built here rather than in the panel's text, because it is a list and the line it hangs off is one
     * line - and because a user who is not asking should not have to read it.
     *
     * Station names, not Point names: the reasons come back keyed by the running graph's Points, and a
     * square is several of those, so the same reason would otherwise appear three times under names
     * the user never chose.  Only the first reason per station is kept, which is the one that would
     * have stopped it.
     */
    private String whyNotToolTip()
    {
        if (layout == null || locomotive == null) return null;

        try
        {
            String cannotStart = layout.explainCannotStart(locomotive);

            if (cannotStart != null) return cannotStart;

            java.util.Map<String, String> reasons = layout.explainDestinations(locomotive);

            java.util.Map<String, String> byStation = new java.util.LinkedHashMap<>();

            for (java.util.Map.Entry<String, String> entry : reasons.entrySet())
            {
                if (entry.getValue() == null) continue;

                String station = stationName(layout.getPoint(entry.getKey()));

                if (!byStation.containsKey(station)) byStation.put(station, entry.getValue());
            }

            if (byStation.isEmpty()) return null;

            StringBuilder out = new StringBuilder("<html>");

            int shown = 0;

            for (java.util.Map.Entry<String, String> entry : byStation.entrySet())
            {
                if (shown == 12)
                {
                    out.append("<br>...");
                    break;
                }

                if (shown > 0) out.append("<br>");

                out.append(entry.getKey()).append(": ").append(entry.getValue());
                shown++;
            }

            return out.append("</html>").toString();
        }
        catch (RuntimeException e)
        {
            // A tooltip is not worth breaking a repaint for
            return null;
        }
    }

    /**
     * A path as "from -> to", with both ends named the way the diagram names them.
     *
     * @param path the route being described, or null
     */
    private String describePath(List<Edge> path)
    {
        if (path == null || path.isEmpty()) return Edge.pathToString(path == null
            ? new java.util.LinkedList<Edge>() : path);

        if (path.get(0) == null || path.get(path.size() - 1) == null) return Edge.pathToString(path);

        return stationName(path.get(0).getStart()) + " -> "
            + stationName(path.get(path.size() - 1).getEnd());
    }

    /**
     * What to call a Point when a person is reading it.
     *
     * The built graph names the copies of a square apart - "Tunnel (northbound)" - so a running log
     * can say which one a train is on.  That is a name for the model.  Somebody watching their
     * railway did not create a northbound Tunnel and a southbound one; they created a station, and
     * being shown the internals invites them to wonder which is the real one.
     *
     * @param point a Point of the running graph
     * @return the station name a reader would recognise
     */
    private String stationName(org.traincontrol.automation.Point point)
    {
        org.traincontrol.automationui.AutonomySession session = parent.getAutonomySession();

        return session == null ? (point == null ? "" : point.getName())
            : session.getStationIndex().describe(point);
    }

    /**
     * Drops the paths that end where the train already is.
     *
     * A square is several Points, so "somewhere else" and "a different Point" stopped meaning the
     * same thing: a train at BottomMainB was offered BottomMainB, the copy facing the other way.
     * That is not a destination, it is the platform under its own wheels, and it appeared in the
     * list a user picks from.
     *
     * Filtered here rather than in the layout, which cannot tell: its only candidate key is the
     * sensor, and a station and its approach guard share one while being genuinely two places.  What
     * makes two Points one place is the square they were built from, and only the setup knows that.
     *
     * @param paths what the layout offered
     * @return the ones that actually go somewhere
     */
    private List<List<Edge>> withoutGoingNowhere(List<List<Edge>> paths)
    {
        org.traincontrol.automationui.AutonomySession session = parent.getAutonomySession();

        return session == null ? paths : session.getStationIndex().distinctDestinations(paths);
    }

    private void locAvailPathsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseClicked
        
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            JList list = (JList) evt.getSource();

            if (evt.getClickCount() == 2)
            {
                // Double-click detected
                int index = list.locationToIndex(evt.getPoint());

                // locationToIndex answers with the LAST row for a click anywhere past the end of the
                // list, and this one stretches to fill its viewport - so a near-miss double-click in
                // the blank space under a short list executed the last path.  No race needed.  The
                // sibling list in GraphLocExclude has carried this guard, with a comment naming the
                // trap, since before this class existed.
                if (index < 0 || !list.getCellBounds(index, index).contains(evt.getPoint())) return;

                if (!layout.isAutoRunning() && !this.paths.isEmpty())
                {
                    if (!this.control.getPowerState())
                    {
                        JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.powerOnToStart"));
                        return;
                    }

                    // Ensure there are no automatic routes
                    /* for (String routeName : this.control.getRouteList())
                    {
                        Route r = this.control.getRoute(routeName);

                        if (r.isEnabled())
                        {
                            this.control.log(r.toString());
                            JOptionPane.showMessageDialog(this, "Please first disable all automatic routes.");
                            return;
                        }
                    }*/

                    // Read here, on the EDT, and not again on the thread below.  updateState
                    // reassigns this list whenever any locomotive arrives or departs - dispatching one
                    // train recomputes another's paths - so a list that changed between the click and
                    // the dispatch sent this locomotive to whatever had moved into that index.  The
                    // movement was valid and locked, which is exactly why nothing reported it.
                    if (index >= this.paths.size()) return;

                    final List<Edge> chosen = this.paths.get(index);

                    new Thread(() ->
                    {
                        parent.ensureGraphUIVisible();

                        boolean success = this.layout.executePath(chosen, locomotive, locomotive.getPreferredSpeed(), null);

                        if (!success)
                        {
                            javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.autoFailedCheckLog")));
                        }
                        
                    }).start();
                }
            } 
        });
    }//GEN-LAST:event_locAvailPathsMouseClicked

    private void locAvailPathsMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseEntered
         
       
    }//GEN-LAST:event_locAvailPathsMouseEntered

    private void locAvailPathsMouseMoved(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseMoved
        
        // Show a tooltip with the path
        /*JList list = (JList) evt.getSource();

        int index = list.locationToIndex(evt.getPoint());
                
        if (index < this.paths.size() && index >= 0)
        {
            List<String> strings = new LinkedList<>();
            this.paths.get(index).forEach((e) -> {
                strings.add(e.toString());
            });
            
            list.setToolTipText("<html>" + String.join("<br>", strings) + "</html>");
        }
        else
        {
            list.setToolTipText("");
        }*/      
    }//GEN-LAST:event_locAvailPathsMouseMoved

    private void pauseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButtonActionPerformed
        locomotive.setAutonomyPaused(pauseButton.isSelected());
    }//GEN-LAST:event_pauseButtonActionPerformed

    private void locNameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locNameMouseClicked
        
        if (parent.getActiveLoc() != locomotive)
        {
            parent.mapLocToCurrentButton(locomotive.getName());
        }
    }//GEN-LAST:event_locNameMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane availablePathScroll;
    private javax.swing.JList<String> locAvailPaths;
    private javax.swing.JLabel locDest;
    private javax.swing.JLabel locName;
    private javax.swing.JLabel locStation;
    private javax.swing.JToggleButton pauseButton;
    // End of variables declaration//GEN-END:variables
}
