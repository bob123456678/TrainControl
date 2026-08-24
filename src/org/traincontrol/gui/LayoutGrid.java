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

    /**
     * How a station caption is spelled when a train is standing on it.
     *
     * Square brackets, a name cut to LAYOUT_STATION_MAX_LENGTH, then the facing arrow - the brackets
     * being the point: a caption is about the same width whatever is in it, which is what lets
     * "[---]" and "[EN57-203 >]" sit on the same platform without the tile changing shape underneath.
     *
     * Written down here because it was written down in the running diagram only, and the autonomy
     * editor - added later, drawing the same placements - spelled it as a bare untruncated name. That
     * label was as wide as the name, so it covered the tiles either side of it, and did not look like
     * the diagram it was meant to match.
     *
     * @param name the locomotive
     * @param arrow the facing arrow, already carrying its own leading space, or "" for none
     * @return the caption
     */
    public static String stationCaption(String name, String arrow)
    {
        if (name == null) return LAYOUT_STATION_EMPTY;

        String cut = name.substring(0, Math.min(name.length(), LAYOUT_STATION_MAX_LENGTH)).trim();

        return "[" + cut + (arrow == null ? "" : arrow) + "]";
    }
    public static final int LAYOUT_ADDRESS_OPACITY = 200;

    // Component that holds the layout
    private JPanel container;

    /**
     * Whether this grid has been replaced by another on the same panel.
     *
     * A grid hides itself until its tiles have finished decoding, and shows a spinner in the meantime
     * - which means two timers and a callback holding the PANEL, still due to fire after the grid
     * that armed them has gone.  Rebuilding the diagram calls parent.removeAll(), so those three then
     * act on somebody else's grid: the grace timer adds a spinner into the middle of it, and because
     * the panel is a FlowLayout an extra component there pushes the tiles along and the last row comes
     * out short.  A half-drawn row appearing after a resize is exactly that.
     *
     * So a replaced grid is told, and its three stragglers do nothing.
     */
    private volatile boolean discarded = false;

    /**
     * The panel this grid was built into, and the window that holds the caption registry.
     *
     * Kept so that discard() can hand back the labels this grid registered.  They are registered
     * against the PANEL, which is also what LIVE is keyed by, so "the labels this grid owns" and "the
     * grid being replaced over this panel" are the same question.
     */
    private JPanel owner;

    private TrainControlUI window;

    /**
     * The caption labels THIS grid registered, so discarding it can hand back exactly those.
     *
     * Not "everything registered against the panel", which was the first attempt and was wrong in a way
     * the repaint code warns about three lines from where it is called: the main window has ONE panel
     * for every page, so forgetting by panel forgot the captions of every page there is - and a page
     * served from the grid cache registers nothing on the way back, so its captions were blank for
     * good.  That is the defect "dropping labels wholesale on a repaint" describes, reached through a
     * different door.
     */
    private final java.util.List<javax.swing.JLabel> registeredCaptions = new java.util.ArrayList<>();

    /**
     * Where a container remembers the caption labels the grid inside it registered.
     *
     * The page cache holds CONTAINERS, and when it is emptied those grids are finished with for good -
     * but a container is all the window has at that moment, so it has to be able to get from one to the
     * labels.  Hung on the container rather than kept in a map here, so it cannot outlive what it is
     * about.
     */
    public static final String CAPTIONS_REGISTERED = "TrainControl.captionsRegistered";

    /**
     * The grid currently drawn into each panel, so that building a new one can retire the old one.
     *
     * DD-B3: four places build a grid over an existing panel and three of them remembered to discard
     * the outgoing one first. The fourth was found by `174178c5`, whose comment is the finding - "both
     * other places that build a grid over an existing panel call this; this one did not" - and the
     * symptom was a spinner dropped into the middle of the page the NEW grid had just drawn.
     *
     * Being remembered at three of four call sites is what a rule looks like just before it is missed
     * at the fourth. So the rule lives here instead, where building a grid IS retiring the one it
     * replaces and there is nothing left to remember.
     *
     * Weak keys: a panel that has gone away takes its entry with it, and nothing here keeps a window
     * alive that the application has finished with.
     */
    /**
     * Weakly on BOTH sides.
     *
     * A WeakHashMap only collects an entry when nothing else reaches the key, and a LayoutGrid reaches
     * its own panel - it holds `container` and adds it to the parent. So a plain grid as the value kept
     * its own key alive and no entry was ever collected: one page retained per editor, popup or export.
     * Found in review, before it grew into anything.
     */
    private static final java.util.Map<JPanel, java.lang.ref.WeakReference<LayoutGrid>> LIVE =
        java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<JPanel, java.lang.ref.WeakReference<LayoutGrid>>());

    /**
     * @return whether this grid has been retired and should not touch its panel again
     */
    public boolean isDiscarded()
    {
        return discarded;
    }

    private javax.swing.Timer failsafe;

    private javax.swing.Timer grace;

    /**
     * Stops this grid from touching its panel again.
     *
     * Called on the OUTGOING grid before a new one is built over the same panel.  Idempotent, and
     * safe on a grid that never had a spinner.
     */
    public void discard()
    {
        discarded = true;

        // The caption labels this grid registered, handed back.
        //
        // addLayoutStation prunes LAZILY - a stale label goes only when a successor for the same square
        // with the same owner is registered - so a caption that is CLEARED left its label in the map for
        // good: nothing is registered for that square again, so no successor ever arrives.  The label is
        // still a child of the retired container, which keeps the whole previous grid reachable, every
        // tile and listener on that page with it.  The same held for every square on a page that was
        // renamed or deleted, since the key carries the page name.
        //
        // Here because this is the one moment something knows a grid is finished with.  It runs before
        // the replacement registers anything - LIVE.put and this call are the first thing the new
        // constructor does - so it cannot take the new grid's labels with it.
        // Its own labels, and only if this grid is really finished with - which the WINDOW decides,
        // because it owns the page cache and this does not.  A grid whose container is cached is coming
        // back: the cache re-attaches the container without building anything or registering anything,
        // so labels dropped here would be gone for the session and that page's captions blank.
        if (window != null) window.forgetLayoutStations(registeredCaptions, container);

        // Null only for a grid whose constructor did not reach the panel - it registers itself against
        // that panel before it builds, so a failure part way through leaves one here to be discarded by
        // the next grid over the same panel.  Nothing on that path throws today; guarded so that a
        // failure stays the failure it was rather than becoming an NPE in the recovery.
        if (container == null) return;

        // Shown, whatever state the reveal had reached.  A grid is HIDDEN while its tiles decode and
        // shown again by the reveal - which discarding cancels - so a grid discarded while it was
        // still decoding stayed hidden for good.  It may already be in the page cache, and coming
        // back to that page then showed an empty diagram until something rebuilt the cache.
        container.setVisible(true);

        if (failsafe != null) failsafe.stop();
        if (grace != null) grace.stop();
    }
    
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
        // Is THIS grid inside an editor - not "is an editor open somewhere".
        //
        // layout.getEdit() alone is the second question. It is the flag the two editors share for their
        // mutual exclusion, so while either was open every grid on screen answered yes, the viewer
        // included: it drew the editor's grey grid around its squares (Adam: "the BUG where the VIEWER
        // gets a grid is still there"), greyed its captions, dropped its hand cursors and its tooltips,
        // and attached mouse listeners that cast their parent to LayoutEditor - which the viewer is not.
        //
        // One line above already asked it this way, as a conjunction, and was right. Everything else in
        // this constructor asked the short version and was wrong in the viewer.
        final boolean inEditor = layout.getEdit() && master instanceof LayoutEditor;

        // Before anything else touches the panel: whatever was drawn here is being replaced, and a
        // replaced grid with timers still armed fires into a panel that is no longer its own.
        this.owner = parent;
        this.window = ui;

        if (parent != null)
        {
            java.lang.ref.WeakReference<LayoutGrid> was =
                LIVE.put(parent, new java.lang.ref.WeakReference<>(this));

            LayoutGrid outgoing = was == null ? null : was.get();

            if (outgoing != null && outgoing != this) outgoing.discard();
        }

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

            // The live list, so whatever this grid registers later is reachable from the container
            container.putClientProperty(CAPTIONS_REGISTERED, registeredCaptions);
            
            // Things mess up without this
            //
            // "Being edited" is a fact about the DIAGRAM and is therefore true in both windows at once
            // - the main window shares the LayoutDiagram with the editor - and it is the wrong question
            // for anything about THIS grid, which is what LAYOUT is: how this particular grid is
            // arranged.
            //
            // This used to add "that is right for clickability below, where the viewer's tiles must
            // stop routing clicks while an editor owns the page". It is not right any more and was not
            // meant to be: the line it points at passes `inEditor`, so the viewer's tiles stay
            // clickable while an editor is open, which is the whole point of asking the longer
            // question. Corrected rather than deleted because this is the only written record of why
            // the flag is shared at all (NR-7).
            //
            // MT-106: the two were the same flag, so any repaint of the viewer while an editor was
            // open re-laid the viewer the editor's way. It went unnoticed while the viewer was only
            // ever rebuilt after the editor closed; the editor now stays open across a page or mode
            // switch, and the teardown that runs on each one rebuilds it.
            //
            // So this asks whether THIS grid is in an editor, which is the question it was always
            // trying to ask.
            if (LayoutDiagram.IGNORE_PADDING || inEditor)
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

                    // Not a square, so not part of the grey grid (OB-055).
                    //
                    // Adam: "when grid is turned on, there is a grid on an extra row at the bottom, and
                    // an extra half column on the right.  the half column starts halfway down and has
                    // the same number of cells, but each is only at half height."  That is this row and
                    // this column: they hold nothing, so GridBagLayout gives them whatever is left
                    // rather than a square's worth, and the border made them visible.
                    grid[x][y].markSpacer();
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
                grid[x][y] = new LayoutLabel(c, master, size, ui, inEditor);
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

                    // Set by the autonomy editor's placed-train branch below, and read after the
                    // generic label styling, which would otherwise take the translucency back.
                    boolean standingTrain = false;

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
                    if (captioned != null && !inEditor
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

                        registeredCaptions.add(text);
                        text.setToolTipText(captionName);

                        final org.traincontrol.automationui.TileGraph.TileKey station = captioned;

                        text.addMouseListener(new MouseAdapter()
                        {
                            // The name is a component of its own, stacked on top of the square, and the
                            // keyboard's idea of what is hovered comes from the square's listener - which
                            // gets mouseEXITED the moment the pointer moves onto the name.  So Control+V
                            // over the platform worked and Control+V over the name did nothing, when the
                            // name is the larger and more obvious of the two things to aim at.
                            //
                            // Reports the STATION rather than whichever square the text happens to sit on:
                            // a caption may be drawn on blank space beside its platform, and pointing at a
                            // station's name means that station either way.
                            @Override
                            public void mouseEntered(MouseEvent e)
                            {
                                ui.setHoveredDiagramTile(station.getPage(), station.getX(), station.getY());
                            }

                            @Override
                            public void mouseExited(MouseEvent e)
                            {
                                ui.setHoveredDiagramTile(null, -1, -1);
                            }

                            @Override
                            public void mouseClicked(MouseEvent e)
                            {
                                // Double-click opens the setup at this station.
                                //
                                // The name of the train standing on a platform is the thing on the
                                // running diagram somebody points at when they want to change what is
                                // standing there - and until now pointing at it activated the
                                // locomotive and nothing else, so getting to the placement view meant
                                // finding the button for it and then finding the station again.
                                //
                                // Not while autonomy is running: the editor cannot open then, and the
                                // refusal is better said by the menu, which explains itself.  A
                                // double-click that opened a dialog saying no would be worse than one
                                // that does nothing.
                                if (e.getClickCount() == 2
                                    && javax.swing.SwingUtilities.isLeftMouseButton(e))
                                {
                                    if (!ui.isAutonomyBusy())
                                    {
                                        javax.swing.SwingUtilities.invokeLater(
                                            () -> ui.openAutonomyEditor(station));
                                    }

                                    return;
                                }

                                if (e.getButton() == MouseEvent.BUTTON3)
                                {
                                    LayoutRightclickAutonomyMenu.showFor(ui, station, station,
                                        e.getComponent(), e.getX(), e.getY());
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
                            // What the SETUP puts on this square, which is the question the editor is
                            // about.  The running diagram shows what is on the rails; here there may be
                            // no run at all, and a platform with a train assigned to it was drawing the
                            // empty placeholder - so the one view where placements are made was the one
                            // view that did not show them.
                            String placed = ui.autonomyCaptionTextAt(captioned);

                            if (placed != null)
                            {
                                text.setText(placed);

                                // The running diagram's own style for a named train: black on
                                // translucent white, so it reads over whatever tile art is underneath.
                                //
                                // Applied AFTER the generic setBackground(WHITE) below rather than
                                // here.  Set here it was overwritten by it - and because this is the
                                // one branch that also turns opacity ON, the result was the only
                                // label on the diagram painting a solid white rectangle, which is the
                                // exact thing a translucent style exists to avoid.
                                standingTrain = true;

                                labelColour = Color.BLACK;
                            }
                            else
                            {
                                // The placeholder the running diagram shows when nothing is standing
                                // there - so the square looks like what it will look like.
                                text.setText(LAYOUT_STATION_EMPTY);

                                // Greyed HERE and nowhere else.  This placeholder says only "a caption
                                // lands on this square", and in the editor it sits on top of the arrows
                                // that say which way trains may arrive - which are the thing somebody
                                // has opened the editor to look at.  Kept rather than hidden: where the
                                // captions are is worth seeing while arranging them.
                                //
                                // A NAMED train is not that: it is the answer, not a placeholder, and
                                // greying it would hide the thing the user just set.
                                labelColour = new Color(150, 150, 150);
                            }
                        }
                        else if (captioned != null)
                        {
                            // A caption the diagram cannot act on: an excluded page, or the TRACK
                            // DIAGRAM editor, where it is drawn so the square is visibly spoken for
                            // but is not that editor to change - captions are edited where autonomy
                            // is edited.
                            text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                            if (inEditor) labelColour = new Color(150, 150, 150);
                        }
                        else if (own != null && own.startsWith(LAYOUT_STATION_PREFIX))
                        {
                            // A station label on a diagram autonomy cannot act on.
                            //
                            // These come from the old autonomy, which wrote "Point:Bahnhof" straight
                            // onto the diagram, and they are still there on a layout served by the
                            // Central Station - where there is no local folder, so no setup, so nothing
                            // ever turns them into captions.  Drawn raw they showed the user an
                            // internal marker they never typed and cannot remove from that machine.
                            //
                            // The name without its marker is what they meant by it, and is what the
                            // caption would have said had autonomy been able to read it.
                            text.setText(own.substring(LAYOUT_STATION_PREFIX.length()));

                            if (autonomyEditor) labelColour = new Color(150, 150, 150);
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

                    // The one label drawn ON something rather than beside it.  Opaque, so the name
                    // reads over the tile art; translucent, so the tile art still shows through.  It
                    // has to come after the WHITE above, which is the whole reason it is down here.
                    if (standingTrain)
                    {
                        text.setOpaque(true);
                        text.setBackground(new Color(255, 255, 255, LAYOUT_STATION_OPACITY));
                    }
                    
                    // Shift on-tile labels down
                    // Current limitation if we wanted to use borders: if you have a text element and an on-tile label in the same row
                    // , they both get shifted down by the same amount.  Therefore, do this multiline hack.
                    if (c != null && !c.isText() && !layout.getEditHideText())
                    {
                        //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        gbc.gridheight = 0;

                        // A caption with a train on it is left as PLAIN text, which is the one thing
                        // that made the editor's version of this label look different from the running
                        // diagram's.  It is the same label, with the same styling; what differed was
                        // the string.
                        //
                        // On the running diagram the caption is registered EMPTY here and its text set
                        // afterwards by updateStationLabels, so it never reaches the wrap below.  The
                        // editor has no run to wait for, so it sets the text now - and picked up the
                        // wrap on the way past.  A leading <br> makes the label two lines tall and the
                        // &nbsp; stops it wrapping, so an OPAQUE label - which this is the only one of
                        // - painted its background as a block a tile tall and three wide, over
                        // whatever was beside it.
                        if (!standingTrain)
                        {
                            text.setText("<html><br>" + text.getText().replaceAll(" ", "&nbsp;") + "</html>");
                        }

                        // Show the correct cursor
                        if (c.isClickable() && !inEditor) text.setCursor(new Cursor(Cursor.HAND_CURSOR));
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
                    
                    if (!inEditor)
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
                    if (!inEditor)
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
                // Which SQUARE this label is, told to the label itself - every label, including the
                // ones with nothing drawn on them.
                //
                // Its right-click menu used to ask the main window which page was showing, which is the
                // wrong page in every popup window; and the keyboard shortcuts ask which square the
                // pointer is over, which a blank label could not answer at all.  A station's name is
                // usually drawn on blank space beside the platform, so those blanks are exactly the
                // squares somebody aims at.
                if (ui != null)
                {
                    grid[x][y].setAutonomySquare(layout.getName(), x + offsetX, y + offsetY);
                }

                if (c != null && ui != null)
                {

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

            showWhenTilesAreReady(parent, ui);
        } 
    }

    /**
     * Holds the diagram back until its track has been decoded, showing a spinner in the meantime.
     *
     * A diagram used to arrive in two stages: text labels immediately, because text needs no image,
     * then the track a second or so later as the decodes came back off the pool.  The staging is an
     * accident of how the work is split, not something the user asked to watch, and while it lasts the
     * labels appear to float on nothing.
     *
     * Only when there is actually a wait.  The second time a page is opened every tile is a cache hit,
     * whenTilesSettled runs on the next EDT pass, and swapping a spinner in and straight out again
     * would be a flicker where there had been none - so the spinner is only mounted if the tiles are
     * still not ready a moment later.
     *
     * @param parent the panel the grid was just added to
     * @param ui the window whose tile loader is doing the decoding
     */
    private void showWhenTilesAreReady(JPanel parent, TrainControlUI ui)
    {
        if (ui == null) return;

        container.setVisible(false);

        final LoadingSpinner spinner = new LoadingSpinner();

        // Sized to the space the diagram will take, so nothing jumps when the two are swapped
        spinner.setPreferredSize(new Dimension(Math.min(maxWidth, 400), Math.min(maxHeight, 400)));

        final boolean[] revealed = {false};

        final Runnable reveal = () ->
        {
            // Nothing from a grid that has been replaced.  See discard().
            if (revealed[0] || discarded) return;

            revealed[0] = true;

            parent.remove(spinner);

            container.setVisible(true);

            parent.revalidate();
            parent.repaint();
        };

        ui.whenTilesSettled(reveal);

        // And revealed anyway after a few seconds, whatever the count says.
        //
        // Hiding the diagram until a signal arrives means a signal that never arrives hides the diagram
        // for the rest of the session, and a blank window is a far worse fault than the flicker this
        // exists to remove.  Nothing known can swallow the count - the decrement is in a finally - but
        // "nothing known" is not a good enough reason to make the diagram depend on it.
        failsafe = new javax.swing.Timer(8000, e -> reveal.run());

        failsafe.setRepeats(false);
        failsafe.start();

        // A short grace period before the spinner is shown at all - see above
        grace = new javax.swing.Timer(120, e ->
        {
            if (revealed[0] || discarded) return;

            parent.add(spinner);

            parent.revalidate();
            parent.repaint();
        });

        grace.setRepeats(false);
        grace.start();
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
