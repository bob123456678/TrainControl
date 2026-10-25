package org.traincontrol.gui.autonomy;

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
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.AutonomyViewerPanel;
import org.traincontrol.gui.TrainControlUI;
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
     * The search is synchronized on the Layout and walks the whole graph, so doing it per panel while the list is
     * rebuilt stalls the event thread and can block it on a monitor a driving thread holds.  A caller passing true
     * promises to call findPaths off the event thread and hand the result back through updateState.
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
        locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), TAB_BLUE, 1, 999));
        locStation.setBackground(TAB_BLUE);
        locStation.setForeground(new Color(255,255,255));
        
        explainOnHover();

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
     * The "there is a reason, hover me" mark: an image rather than a character, as U+24D8 has no glyph in the UI font
     * on some machines (OB-063) and a font fallback makes what the user sees depend on the machine.  Loaded once; a
     * null icon is harmless to setIcon.
     */
    private static final javax.swing.ImageIcon INFO_ICON = loadInfoIcon();

    /**
     * This tab's blue, for a card's station badge and its text: the darker blue the window's own labels are written in.
     * The track diagram's captions and badges keep their brighter blue, `StationCaption.PILL`.
     */
    static final Color TAB_BLUE = AutonomyViewerPanel.HEADING_COLOUR;

    private static javax.swing.ImageIcon loadInfoIcon()
    {
        java.net.URL url = TrainControlUI.class.getResource("resources/info.gif");

        return url == null ? null : new javax.swing.ImageIcon(url);
    }

    /**
     * The " -" suffix marking a station full autonomy will never send this locomotive to.
     *
     * Several reasons, one meaning to the operator: the station may exclude this locomotive, be switched off, be a
     * reversing station, or be marked as somewhere autonomy does not choose (a parking berth, `autoDestination:false`).
     * They share a marker rather than a symbol each: this list offers the berth because you may still choose it
     * yourself, and the dash says autonomy will not.
     *
     * Reversing NON-stations never appear here - getPossiblePaths only enumerates destinations - so
     * the full predicate is spelled out to match the rule in Layout rather than relying on that.
     * A TERMINUS THIS TRAIN CANNOT BACK INTO IS THE THIRD REASON (the rule lives in `pickPath`,
     * `hasAutonomousDestination` and `barredFromAutonomy`).
     *
     * Why this is a copy at all: `Layout.barredFromAutonomy` is the rule, but every public way in is `synchronized` on
     * the Layout and this runs on the event thread, three times per repaint, so the copy is deliberate and lock-free;
     * `testTheDashAgreesWithTheRule` keeps it honest.
     */
    private static String notChosenByAutonomy(Point p, Locomotive loc)
    {
        if (p == null) return "";

        // ALL FIVE LIMBS, in the rule's order.
        return !p.isActive()
            || (p.isReversing() && p.isDestination())
            || !p.isAutoDestination()
            || (loc != null && p.isTerminus() && !loc.isReversible())
            || p.excludes(loc) ? " -" : "";
    }

    /**
     * Refreshes the available routes shown in the UI
     * @param someLoc 
     */
    /**
     * The path search, on its own so it can be done off the event thread: getPossiblePaths is synchronized on the
     * Layout and searches it whole, once per locomotive panel, and on the EDT that stalls the interface and can block
     * on a monitor a driving thread holds.  Touches no Swing state.
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
     * The timetable's starting point for this locomotive, read when the paths were: getTimetableStartingPoint is
     * synchronized on the Layout, which `configureAndLockPath` holds across its per-command sleeps, so taking it on the
     * event thread (three reads per repaint) can freeze the interface.  Null when nothing was gathered, in which case
     * the reads happen inline.
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
            
            this.locDest.setForeground(TAB_BLUE);
            
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
            // Identity: see the note in HomeStaging on why the name comparison this replaced kept
            // compiling and always answered false.
            final boolean atAssignedHome = at != null && locomotive.equals(at.getHomeLoc());

            // Gray out locomotives on inactive points / not on the graph
            if ((layout.getLocomotiveLocation(locomotive) != null && !layout.getLocomotiveLocation(locomotive).isActive()) ||
                    layout.getLocomotiveLocation(locomotive) == null
            )
            {
                this.locName.setForeground(Color.LIGHT_GRAY);
                this.pauseButton.setVisible(false);
                
                // Gray out label
                locStation.setBackground(Color.LIGHT_GRAY);
                locStation.setForeground(Color.WHITE);
                locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), Color.LIGHT_GRAY, 1, 999));
            }
            // STANDING ON ITS HOME STATION IS A HOUSE BESIDE THE SECOND LINE, NOT A TEAL BADGE (MT-709): the badge is
            // the tab's blue as for any other station, and `markFor` below draws the house.
            else
            {
                this.locName.setForeground(Color.BLACK);
                this.pauseButton.setVisible(true);
                
                // Restore label color
                locStation.setBackground(TAB_BLUE);
                locStation.setForeground(Color.WHITE);
                locStation.setBorder(new FlatLineBorder(new Insets(0,2,0,2), TAB_BLUE, 1, 999 ));
            }
            
            this.locStation.setToolTipText("");

            // Cleared every time round, because only ONE branch below sets it.  Left standing, the
            // explanation survived into the states it does not describe: the panel would say
            // "double-click to execute", or draw a running route, while hovering the same label still
            // listed obstacles that had stopped the train several minutes earlier.
            this.noPathsNow = false;
            this.locDest.setToolTipText(null);

            // The mark goes with the tooltip it belongs to, and for the same reason: one branch sets
            // it, so every other branch has to be the one that takes it away.
            this.locDest.setIcon(null);

            // Locomotive is running - show the path and hide the list
            if (layout.getActiveLocomotives().containsKey(locomotive))
            {
                List<Point> milestones = layout.getReachedMilestones(locomotive);
                
                // Named as the diagram names them: Edge.pathToString spells out the Points, and on a derived graph a
                // station is several, so it would show the arrival-side split.  The log keeps the Point names.
                this.locDest.setText(describePath(layout.getActiveLocomotives().get(locomotive)));
                
                // Guarded, because the two maps this reads are not written under one lock: a refresh landing between
                // the driver clearing the active locomotives and the milestones finds a "running" locomotive with no
                // milestones, and the NPE would leave every later panel showing what it showed last time.
                this.locStation.setText(milestones == null || milestones.isEmpty() ? ""
                    : "@" + stationName(milestones.get(milestones.size() - 1)));
                
                this.locDest.setForeground(new Color(204, 0, 0));
                this.locAvailPaths.setVisible(false);
            }
            // Layout is in auto mode but loc is not running - show status message and hide the list
            // AND WHILE A STAGING FLOW OWNS THE RAILWAY: the double-click sends nothing then, so nothing is
            // offered to double-click
            else if (layout.isAutoRunning() || layout.isStagingInProgress())
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
                // Already searched, if somebody did it off the EDT; searching here is the fallback for callers with no
                // worker.
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
                    // With the info mark, because a tooltip nobody knows about explains nothing.
                    this.locDest.setText(I18n.t("autolayout.ui.noAvailPaths"));

                    // And WHY, on hover.  A FLAG here, and the reasons worked out when the pointer
                    // stops on the label - see the mouse listener in the constructor.
                    //
                    // Computing them here was a mistake, and an expensive one.  updateState runs on the
                    // event thread, once per panel; explainDestinations walks every candidate route to
                    // every station and takes the Layout's monitor to do it.  So placing a locomotive
                    // or loading a configuration - which rebuilds every panel with its search
                    // deliberately DEFERRED, leaving each one in exactly this branch - did that whole
                    // enumeration once per panel on the event thread, before the worker it had been
                    // deferred to even started.  That is the freeze this file's own comments say must
                    // never happen, reintroduced by the feature meant to explain it.
                    this.noPathsNow = true;
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

            // The (i) where there is a reason to hover for, and the house where the train stands at its home - both where
            // both, beside the second line
            this.locDest.setIcon(markFor(atAssignedHome && at.isActive(), this.noPathsNow));

            this.locAvailPaths.setModel(pathList);
        }
    }

    /**
     * The mark beside a card's second line: the house where its train stands at the home assigned to it (MT-709), the (i)
     * where hovering there explains something, both side by side where both, and none otherwise.
     *
     * @param atHome whether the train stands at its assigned home
     * @param whyNot whether the second line has a reason to show on hover
     * @return the mark, or null
     */
    static javax.swing.Icon markFor(boolean atHome, boolean whyNot)
    {
        if (atHome && whyNot && INFO_ICON != null) return new BothMarks(HOUSE, INFO_ICON);

        if (atHome) return HOUSE;

        return whyNot ? INFO_ICON : null;
    }

    /**
     * A little house, the (i)'s size, in the tab's blue: a roof and a body, with a door in it.
     */
    private static final javax.swing.Icon HOUSE = new javax.swing.Icon()
    {
        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();

            try
            {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
                g2.setColor(TAB_BLUE);

                java.awt.geom.Path2D roof = new java.awt.geom.Path2D.Double();

                roof.moveTo(0.5, 7);
                roof.lineTo(7, 0.5);
                roof.lineTo(13.5, 7);
                roof.closePath();

                g2.fill(roof);
                g2.fillRect(2, 6, 10, 8);

                g2.setColor(Color.WHITE);
                g2.fillRect(6, 9, 3, 5);
            }
            finally
            {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return 14;
        }

        @Override
        public int getIconHeight()
        {
            return 14;
        }
    };

    /**
     * Two marks side by side, a little apart.
     */
    private static final class BothMarks implements javax.swing.Icon
    {
        private final javax.swing.Icon first, second;

        BothMarks(javax.swing.Icon first, javax.swing.Icon second)
        {
            this.first = first;
            this.second = second;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y)
        {
            first.paintIcon(c, g, x, y + (getIconHeight() - first.getIconHeight()) / 2);
            second.paintIcon(c, g, x + first.getIconWidth() + 3, y + (getIconHeight() - second.getIconHeight()) / 2);
        }

        @Override
        public int getIconWidth()
        {
            return first.getIconWidth() + 3 + second.getIconWidth();
        }

        @Override
        public int getIconHeight()
        {
            return Math.max(first.getIconHeight(), second.getIconHeight());
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
    /**
     * Whether this panel is currently showing "no available paths", and so has something to explain.
     *
     * Written on the event thread by updateState and read there by the hover listener.
     */
    private boolean noPathsNow;

    /**
     * The whole list of stations and why each one is unavailable, in a window (FR-017).  Worked out OFF the event
     * thread: `explainDestinations` takes the Layout's monitor, which a dispatch holds across its per-command sleeps.
     * So the window opens at once saying it is working and fills in when the answer arrives; the dialog being modal
     * runs its own event pump, which delivers the invokeLater below.
     */
    private void showWhyNot()
    {
        // A styled pane rather than a plain text area, so the two headings can be bold (MT-160, MT-163).
        final javax.swing.JTextPane area = new javax.swing.JTextPane();

        area.setEditable(false);
        area.setMargin(new java.awt.Insets(6, 6, 6, 6));

        // FlatLaf derives a non-editable JTextPane's background from the panel behind it, a gray, so white is set
        // explicitly as the rest of the app does (OB-096).
        area.setBackground(Color.WHITE);

        // The look and feel's font FAMILY, at the size the rest of this application uses: FlatLaf's label font is
        // smaller than the 14 that most Segoe UI components in the gui package are built with, so taking it whole made
        // this window smaller than its neighbors (MT-160, MT-163).  The family still comes from the look and feel, so a
        // theme's typography is followed.
        java.awt.Font standard = javax.swing.UIManager.getFont("Label.font");

        if (standard == null) standard = new javax.swing.JLabel().getFont();

        area.setFont(standard.deriveFont(14f));

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(area);

        scroll.setPreferredSize(new java.awt.Dimension(580, 380));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        new Thread(() ->
        {
            final String report = whyNotReport();

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                // No guard on which locomotive this is about: the field is final and a panel belongs to one locomotive
                // for its whole life.  What is true is smaller: the window may be closed before the answer arrives, and
                // writing to a text area nobody is looking at costs nothing.
                write(area, report);
                area.setCaretPosition(0);
            });
        }).start();

        JOptionPane.showMessageDialog(javax.swing.SwingUtilities.getWindowAncestor(this), scroll,
            I18n.f("autolayout.ui.whyTitle", locomotive == null ? "" : locomotive.getName()),
            JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Puts the report into the pane, with the group headings in bold (MT-160, MT-163).  A heading is recognized by
     * position rather than by matching its text: each is on its own line immediately before an indented block, and
     * matching translated text would work in English and quietly do nothing in the other seven bundles.
     *
     * @param pane the window's text
     * @param report what whyNotReport produced
     */
    private void write(javax.swing.JTextPane pane, String report)
    {
        javax.swing.text.StyledDocument doc = pane.getStyledDocument();

        javax.swing.text.SimpleAttributeSet bold = new javax.swing.text.SimpleAttributeSet();

        javax.swing.text.StyleConstants.setBold(bold, true);

        pane.setText("");

        try
        {
            for (String line : report.split("\n", -1))
            {
                boolean heading = !line.isEmpty() && !line.startsWith("    ");

                doc.insertString(doc.getLength(), line + "\n", heading ? bold : null);
            }
        }
        catch (javax.swing.text.BadLocationException impossible)
        {
            // Inserting at getLength() cannot be out of bounds. Falling back to the plain text costs
            // the bold and nothing else, which is better than an empty window.
            pane.setText(report);
        }
    }

    /**
     * Every station, grouped by whether autonomy could ever choose it, with the reason for each; the choosable group
     * first, as it is the actionable one.  The grouping comes from the Layout rather than from matching reason strings,
     * which would be a second answer to "can autonomy pick this station" that could drift from the search's.
     * Arrival-side copies are collapsed to the station a person would recognize, and a station counts as choosable if
     * ANY of its copies is.
     *
     * @return the report, never null
     */
    private String whyNotReport()
    {
        if (layout == null || locomotive == null) return "";

        try
        {
            StringBuilder out = new StringBuilder();

            org.traincontrol.automation.Point at = layout.getLocomotiveLocation(locomotive);

            if (at != null)
            {
                out.append(I18n.f("autolayout.ui.whyStanding", locomotive.getName(), stationName(at)))
                    .append("\n");
            }

            // FOR THE TIER THIS LIST IS IN (the REG4 lead): on Manual it offers routes picked by hand, so the
            // reasons are the ones a hand-driven send meets, as the diagram's Why not Moving? gives them on Manual.
            boolean byHand = !layout.isAutoRunning();

            // Whatever stops the train itself moving comes first and on its own: a list of stations would give one
            // reason that is not about them.
            String cannotStart = layout.explainCannotStart(locomotive, byHand);

            if (cannotStart != null)
            {
                return out.append("\n").append(cannotStart).append("\n").toString();
            }

            // Both halves under ONE acquisition of the Layout monitor: asked separately, the reasons and the grouping
            // could be computed against two states, and a line would sit under the wrong heading.
            Layout.Destinations destinations = layout.explainDestinationsGrouped(locomotive, byHand);

            java.util.Map<String, String> reasons = destinations.getReasons();

            java.util.Map<String, String> choosable = new java.util.TreeMap<>();
            java.util.Map<String, String> barred = new java.util.TreeMap<>();

            for (java.util.Map.Entry<String, String> entry : reasons.entrySet())
            {
                if (entry.getValue() == null) continue;

                destinations.fileUnderStation(entry.getKey(), stationName(layout.getPoint(entry.getKey())), choosable,
                    barred);
            }

            if (choosable.isEmpty() && barred.isEmpty())
            {
                return out.append("\n").append(I18n.t("autolayout.ui.whyNothingToReport"))
                    .append("\n").toString();
            }

            // On Manual, the stations it cannot be sent to: autonomy's "could choose" is not the question (MT-434).
            appendGroup(out, I18n.f(byHand ? "autolayout.ui.whyHeaderByHand" : "autolayout.ui.whyHeaderCandidates",
                choosable.size()), choosable);
            appendGroup(out, I18n.f("autolayout.ui.whyHeaderBarred", barred.size()), barred);

            return out.toString();
        }
        catch (RuntimeException e)
        {
            return I18n.t("autolayout.ui.whyCouldNotWorkOut") + "\n\n" + e;
        }
    }

    /**
     * One headed block of "station - reason" lines, skipped entirely when the group is empty (an empty heading reads as
     * a failed load).
     */
    private void appendGroup(StringBuilder out, String heading, java.util.Map<String, String> lines)
    {
        if (lines.isEmpty()) return;

        out.append("\n").append(heading).append("\n");

        for (java.util.Map.Entry<String, String> line : lines.entrySet())
        {
            out.append("    ").append(line.getKey()).append(" - ").append(line.getValue())
                .append("\n");
        }
    }

    /**
     * Works the reasons out when the pointer stops on the destination label, and not before.
     *
     * Hovering is one panel, once, and the user asking - which is the only time this cost is worth
     * paying.  Called from the constructor.
     */
    private void explainOnHover()
    {
        this.locDest.addMouseListener(new java.awt.event.MouseAdapter()
        {
            /**
             * FR-017: the whole list, in a window that stays put and can be scrolled; a tooltip cannot be scrolled and
             * shows twelve stations then an ellipsis.
             */
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                if (noPathsNow) showWhyNot();
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e)
            {
                // A pointer that says "there is something to read here", matching the mark on the text
                locDest.setCursor(java.awt.Cursor.getPredefinedCursor(
                    noPathsNow ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));

                if (!noPathsNow)
                {
                    locDest.setToolTipText(null);

                    return;
                }

                // Worked out OFF the event thread (OB-079): explainDestinations takes the Layout's monitor, which a
                // dispatch holds across its per-command sleeps, so hovering while a train was being dispatched froze
                // the window.  The tooltip arrives a moment later, and nothing is shown meanwhile because a tooltip
                // that changes under the pointer reads as a glitch.
                new Thread(() ->
                {
                    final String why = whyNotToolTip();

                    javax.swing.SwingUtilities.invokeLater(() ->
                    {
                        // Still in the STATE the answer was about: noPathsNow changes when a train is dispatched while
                        // the reasons are worked out, and a tooltip explaining why there are no paths does not belong
                        // on a label showing one.
                        if (noPathsNow) locDest.setToolTipText(why);
                    });
                }).start();
            }
        });
    }

    private String whyNotToolTip()
    {
        if (layout == null || locomotive == null) return null;

        try
        {
            // FOR THE TIER THIS LIST IS IN (the REG4 lead): see `whyNotReport`.
            boolean byHand = !layout.isAutoRunning();

            String cannotStart = layout.explainCannotStart(locomotive, byHand);

            // WRAPPED: the sentences with a remedy run to two hundred characters, on one line otherwise.
            if (cannotStart != null) return AutonomyEditorPanel.wrapped(cannotStart);

            java.util.Map<String, String> reasons = layout.explainDestinations(locomotive, byHand);

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
     * @return the station name a reader would recognize
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

        List<List<Edge>> distinct =
            session == null ? paths : session.getStationIndex().distinctDestinations(paths);

        return whatTheOperatorMayChoose(distinct);
    }

    /**
     * Drops the destinations this train may not be sent to at all: `OB-205` put that rule in
     * `Layout.isOfferableToOperator` and only the track diagram's menu asked it.  The dash and this filter are not the
     * same question and both are wanted: the dash says autonomy will not choose this (true of a parking berth the
     * operator may pick by hand), this says you may not send this train here (a terminus it could not leave again).
     * This is the rule itself, not a copy: it runs where the path search runs, so nothing here can fall out of step,
     * unlike the lock-free `notChosenByAutonomy` beside it.
     *
     * @param paths the paths the search returned
     * @return the ones the operator may actually choose
     */
    private List<List<Edge>> whatTheOperatorMayChoose(List<List<Edge>> paths)
    {
        if (paths == null || layout == null) return paths;

        List<List<Edge>> allowed = new java.util.ArrayList<>();

        for (List<Edge> path : paths)
        {
            if (path == null || path.isEmpty()) continue;

            Point end = path.get(path.size() - 1).getEnd();

            if (end != null && !layout.isOfferableToOperator(end, locomotive)) continue;

            allowed.add(path);
        }

        return allowed;
    }

    private void locAvailPathsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_locAvailPathsMouseClicked
        
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            JList list = (JList) evt.getSource();

            if (evt.getClickCount() == 2)
            {
                // Double-click detected
                int index = list.locationToIndex(evt.getPoint());

                // locationToIndex answers with the LAST row for a click anywhere past the end of the list, which
                // stretches to fill its viewport, so a near-miss double-click in the blank space executed the last path
                // (as GraphLocExclude guards).
                if (index < 0 || !list.getCellBounds(index, index).contains(evt.getPoint())) return;

                // NOR WHILE RETURN HOME OWNS THE RAILWAY: autonomy's flag is down while it plans, and a train
                // sent by hand then spent the press on positions the railway no longer had.  A second hand send while
                // another runs stays allowed here (Adam, OB-164: "The user can rely on full autonomy or the panels to
                // send trains more clearly.").
                if (!layout.isAutoRunning() && !layout.isStagingInProgress() && !this.paths.isEmpty())
                {
                    // Read here, on the EDT, where the operator chose it: updateState reassigns this list whenever any
                    // locomotive arrives or departs, so a list that changed between click and dispatch sent this
                    // locomotive to whatever had moved into that index.
                    if (index >= this.paths.size()) return;

                    final List<Edge> chosen = this.paths.get(index);

                    // THROUGH THE ONE DOOR both hand doors send through: whether a train may be sent at all (the
                    // editor, the setup, the power), this path's standing rules, the reversal question, the last-moment
                    // question again, the atomic-routes gate and the dispatch, in that order.
                    this.parent.sendATrainByHand(this.layout, chosen, locomotive);
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

        // AND ITS ICON ON THE DIAGRAM (FR-117), gray while paused.  The train alone: this button is pressed while
        // trains run, when the setup is not edited, and the fold takes it into the setup with the rest of this tab's
        // settings.
        parent.trainsPausedChanged();
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
