package org.traincontrol.gui;

import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import org.traincontrol.gui.autonomy.ArrivalSidePrompt;
import org.traincontrol.util.I18n;

/**
 *
 * @author Adam
 */
public class GraphLocAssign extends javax.swing.JPanel
{
    TrainControlUI parent;
    Point p;

    /**
     * The setup this assignment is recorded into, and the railway it happens on.  Held, not passed to
     * `commitAndRecord`: the combo is filled from this setup while the form is built, and a second copy handed in at
     * commit time could name a different one.
     */
    private final org.traincontrol.automationui.AutonomySession session;

    private final org.traincontrol.automation.Layout running;

    /**
     * Where the operator says the train's tail is, offered only where there is a choice.  Null on a terminus and on a
     * square with no track (see `buildArrivalSide`).
     */
    private javax.swing.JComboBox<String> arrivedFrom;

    private javax.swing.JLabel arrivedFromLabel;

    /** The sides behind the combo's entries, in its own order, offset by one for the "---" row. */
    private java.util.List<String> arrivalSides = new java.util.ArrayList<>();

    /** Whether the operator has picked a side by hand, so changing the locomotive must not pick another under them. */
    private boolean arrivalSideChosenByHand;

    private boolean fillingArrivalSide;
        
    public static final String NONE_LABEL = "---";

    /**
     * What the menu item that opens this dialog should be called for a given point.
     *
     * The dialog does two jobs, and the menu used to name only one of them: on an empty station it
     * offers to "Edit Locomotive" when there is nothing there to edit, which reads as though something
     * is already assigned.  Placing and editing are the same dialog, so the wording is the only thing
     * that separates them.
     *
     * Here rather than in the menus because two of them open this panel, and a label chosen twice is a
     * label that ends up different in one of them.
     *
     * @param p
     * @return
     */
    public static String menuLabelFor(Point p)
    {
        return I18n.t(p != null && p.getCurrentLocomotive() == null
            ? "autolayout.ui.labelPlaceLocomotiveAt"
            : "autolayout.ui.labelEditLocomotiveAt");
    }

    /**
     * Creates new form GraphLocAssign
     * @param parent
     * @param p
     * @param session the setup being edited, or null when there is none
     * @param running the running layout, for working out where a train's tail lies
     */
    public GraphLocAssign(TrainControlUI parent, Point p,
        org.traincontrol.automationui.AutonomySession session,
        org.traincontrol.automation.Layout running)
    {
        initComponents();

        // The locomotive menu's lengths, not a list of its own (OB-294): the form writes 0 to 20 by hand and the menu
        // offers 0 to `TrainControlUI.ROUTE_TRAIN_LENGTH_MAX`, so a longer train opened here showed the last entry and
        // OK saved it.  Replaced here rather than in the form, which is generated.
        String[] lengths = new String[TrainControlUI.ROUTE_TRAIN_LENGTH_MAX + 1];

        for (int i = 0; i < lengths.length; i++) lengths[i] = String.valueOf(i);

        this.trainLength.setModel(new javax.swing.DefaultComboBoxModel<>(lengths));
        
        // The trains autonomy runs: every door that opens this asks about a train already on the graph.
        List<String> locs = new LinkedList<>();

        for (Locomotive l : parent.getModel().getAutoLayout().getLocomotivesToRun())
        {
            locs.add(l.getName());
        }
        
        Collections.sort(locs);

        this.locAssign.setModel(new DefaultComboBoxModel(locs.toArray()));
        this.parent = parent;
        this.p = p;
        this.session = session;
        this.running = running;
                
        // Select current locomotive if possible
        if (p.getCurrentLocomotive() != null)
        {
            this.locAssign.setSelectedItem(p.getCurrentLocomotive().getName());
        }
        
        buildArrivalSide();

        updateValues();

        this.arrivalFunc.setVisible(true);
        this.arrivalFuncLabel.setVisible(true);
        this.departureFunc.setVisible(true);
        this.departureFuncLabel.setVisible(true);
        this.reversible.setVisible(true);
        this.trainLength.setVisible(true);   
        
        // Give the dropdown focus so you can filter with the keyboard
        this.locAssign.addAncestorListener(new AncestorListener()
        {
            @Override
            public void ancestorRemoved(AncestorEvent event) {}

            @Override
            public void ancestorMoved(AncestorEvent event) {}

            @Override
            public void ancestorAdded(AncestorEvent event) {
                event.getComponent().requestFocusInWindow();
            }
        });
    }
    
    /**
     * Commits an assignment and records it in the setup, the whole of what accepting this dialog means.  Adam: **"there
     * needs to be parity across the board.  Not sure why this would ever be at risk."**  The diagram's door wrote
     * neither placement nor facing into the setup, so its assignment **reverted the next time a configuration was
     * loaded**: both doors call this rather than copy it.
     *
     * The heading is read before the commit (which takes the train off the square that knows it) and for the train
     * arriving.  The arrival side is written after it, because `Point.setLocomotive` drops `arrivedFrom` when the
     * occupant changes; both stores, since the track walk reads the live `Point` and the setup only reaches it at the
     * next rebuild.  The point, session and layout all come from the dialog it was built against.
     *
     * @param edit the dialog, already dismissed with OK
     */
    public static void commitAndRecord(GraphLocAssign edit)
    {
        if (edit == null || edit.p == null) return;

        final org.traincontrol.automationui.AutonomySession session = edit.session;

        final org.traincontrol.automation.Layout layout = edit.running;

        String arriving = edit.getLoc();

        org.traincontrol.automationui.TilePorts.Side heading =
            arriving == null || session == null ? null : session.facingOf(arriving, layout);

        // The copy it will stand on, chosen by the rule the facing is recorded with, as the paste chooses it (OB-270).
        // The copy is the direction: moving the train onto whichever copy the menu was opened on and then recording its
        // old heading left a square with two copies facing opposite ways saying west while the train stood on the east
        // copy.
        if (session != null && layout != null && session.getStationIndex() != null)
        {
            org.traincontrol.automationui.TileGraph.TileKey square = session.getStationIndex().squareOf(edit.p.getName());

            if (square != null)
            {
                org.traincontrol.automationui.TilePorts.Side intended =
                    org.traincontrol.automationui.AutonomySession.facingAfterAPaste(
                        session.departableFacingsFor(square, layout), heading, edit.p.getName());

                org.traincontrol.automation.Point facingThatWay = session.copyFacing(square, intended, layout);

                if (facingThatWay != null) edit.p = facingThatWay;
            }
        }

        final org.traincontrol.automation.Point point = edit.p;

        // What the train had on the railway, before the dialog's commit moves it.
        org.traincontrol.automation.Point wasOn = null;

        if (layout != null && arriving != null)
        {
            for (org.traincontrol.automation.Point each : layout.getPoints())
            {
                if (each.getCurrentLocomotive() != null && arriving.equals(each.getCurrentLocomotive().getName()))
                {
                    wasOn = each;
                }
            }
        }

        final java.util.List<org.traincontrol.automation.Edge> roadBefore = wasOn == null ? null : wasOn.getArrivedAlong();
        final String sideBefore = wasOn == null ? null : wasOn.getArrivedFrom();
        final org.traincontrol.automationui.TileGraph.TileKey squareBefore = wasOn == null || session == null
            || session.getStationIndex() == null ? null : session.getStationIndex().squareOf(wasOn);

        // A refused placement records nothing: the railway did not put the train there, so no placement, facing, tail
        // or save.
        if (!edit.commitChanges()) return;

        if (session == null) return;

        org.traincontrol.automationui.TileGraph.TileKey tile =
            session.getStationIndex() == null ? null
                : session.getStationIndex().squareOf(point.getName());

        if (tile == null) return;

        session.placeLocomotive(tile,
            point.getCurrentLocomotive() == null ? null : point.getCurrentLocomotive().getName());

        // Whether the setup this door writes to is still the window's once its tail question is answered.
        boolean setupStands = true;

        // After the commit this is the arriving train, which is why the guard reads the point rather than `arriving`.
        if (point.getCurrentLocomotive() != null)
        {
            // Over the copies it could leave (OB-284), the rule the copy above was chosen by.
            session.setFacing(tile,
                org.traincontrol.automationui.AutonomySession.facingAfterAPaste(
                    session.departableFacingsFor(tile, layout), heading, point.getName()));

            // And where its tail is: answered in the dialog, written here, and asked how far back it reaches after this
            // dialog, owned by the main window since the dialog's panel has gone.
            setupStands = TailCrossedPrompt.recordTheTail(session, tile, layout, point, edit.getArrivedFrom(),
                point.getCurrentLocomotive().getName(), roadBefore, tile.equals(squareBefore), sideBefore, edit.parent,
                null);
        }

        // And written to disk: the two writes above change the setup in memory only, and "the placement is gone at the
        // next configuration load" is the symptom of leaving the save out.  Every other door that writes the setup
        // saves it.  Not a setup the window has let go in the wait, which the reset that replaced it wrote.
        if (!setupStands) return;

        try
        {
            session.save();
        }
        catch (java.io.IOException cannotSave)
        {
            // The assignment stands either way; only the memory of it is at risk.  Logged rather than
            // shown, for the reason given at the other placement door.
            if (edit.parent != null && edit.parent.getModel() != null)
            {
                edit.parent.getModel().log(cannotSave);
            }
        }
    }
    /**
     * Offers the arrival side, where the square gives the operator anything to choose between (Adam: **"the missing
     * arrival side should be set - either from the data, by the user, or randomly"**, then **"proceed with this combo.
     * Try to reuse the machinery/checks of the current popup."**).  `Layout.edgesCoveredByStandingTrains` walks back
     * from a standing train along the side it came in by and blocks that track, so a wrong side blocks the wrong rail;
     * a combo can afford a starting value because it is seen and corrected before OK.  Only where there is something to
     * choose: one side is a terminus and none a square with no track, and `getArrivedFrom` then answers from the rule.
     * Entries, wording and sides come from `ArrivalSidePrompt`, as the popup's do.
     */
    private void buildArrivalSide()
    {
        this.arrivalSides = ArrivalSidePrompt.choicesFor(this.running, this.p, sidesFromTheBuild());

        if (this.arrivalSides.size() < 2) return;

        // Sides only - there is deliberately no "---" here.  Adam: **"clearing should not be possible, only setting."**
        // The arrived-from menu's "Not known" entry was taken out for the reason that applies here: every side the
        // square has is offered, so a wrong answer is corrected by choosing the right one, and a blank entry only
        // offers a way to switch the tail blocking off.  `refreshArrivalSide` guarantees one: recorded, else derived,
        // else the first side.
        List<String> entries = new ArrayList<>();

        for (String side : this.arrivalSides)
        {
            entries.add(ArrivalSidePrompt.labelFor(side));
        }

        this.arrivedFrom = new javax.swing.JComboBox<>(entries.toArray(new String[0]));
        this.arrivedFrom.setFont(new java.awt.Font("Segoe UI", 0, 14));

        // Its own caption, not the menu's (Adam, OB-203): *"Change 'Train arrived from' to 'Train Arrived', and align
        // horizontally with the other labels."*  A menu item reads as the start of a sentence its submenu finishes,
        // "Train arrived from > north"; a label in a column of Title Case nouns reads as one of those, so they are two
        // wordings of two things.
        this.arrivedFromLabel = new javax.swing.JLabel(I18n.t("autolayout.ui.trainArrived"));
        this.arrivedFromLabel.setFont(new java.awt.Font("Segoe UI", 0, 14));
        // The standard's blue, not the retired one: `docs/UI-standards.md` gives minor headings 0,0,155; the 0,0,115
        // labels beside this one are in the generated block, which the standard exempts.
        this.arrivedFromLabel.setForeground(new java.awt.Color(0, 0, 155));

        // A hand on the combo is remembered, so picking a different locomotive (which moves the suggestion, worked out
        // from that train's heading) does not overwrite what the operator just said.
        this.arrivedFrom.addActionListener(event ->
        {
            if (!this.fillingArrivalSide) this.arrivalSideChosenByHand = true;
        });

        refreshArrivalSide();
    }

    /**
     * The build's own arrival sides for this square, where there is a setup to ask.  Adam: pasting onto BottomMainPost
     * "asks if the train arrived from the south or from the west, rather than the north or the south": the geometric
     * answer names where the neighboring Point lies, and a reduced edge may turn corners, while the builder splits the
     * square on the side the track actually comes in by (OB-182).
     *
     * @return the sides, or null when there is no setup - `choicesFor` then falls back to the geometry
     */
    private java.util.List<org.traincontrol.automationui.TilePorts.Side> sidesFromTheBuild()
    {
        org.traincontrol.automationui.TileGraph.TileKey tile = square();

        // The unbarred sides (OB-204): a side the operator has closed is not one a train arrived by, and treating it as
        // one made the suggested side alternate with the train's facing.
        return this.session == null || tile == null ? null : this.session.unbarredArrivalSides(tile);
    }

    /**
     * The square this point belongs to, as the setup indexes it.
     *
     * @return the square, or null when there is no setup to ask
     */
    private org.traincontrol.automationui.TileGraph.TileKey square()
    {
        return this.session == null || this.session.getStationIndex() == null
            ? null : this.session.getStationIndex().squareOf(this.p.getName());
    }

    /**
     * Starts the combo at the side this placement should be recorded with, from three sources in the order Adam gave
     * (*"either from the data, by the user, or randomly"*).  From the data: a train already standing here has an
     * arrival side something watched, which re-opening the dialog must not replace with a derived one; read only when
     * the selected locomotive is the one standing here.  From the rule, `ArrivalSidePrompt.suggestedFor`, passed
     * `mayReverse` false on purpose: it declines to guess where trains may turn because a popup's guess is invisible,
     * and in a form the operator sees it.  Otherwise the first side there is.  Not called when the operator has already
     * chosen.
     */
    private void refreshArrivalSide()
    {
        if (this.arrivedFrom == null || this.arrivalSideChosenByHand) return;

        String side = recordedHere();

        if (side == null)
        {
            side = ArrivalSidePrompt.suggestedFor(this.running, this.p, headingOfTheArrivingTrain(),
                false, sidesFromTheBuild());
        }

        if (side == null && !this.arrivalSides.isEmpty()) side = this.arrivalSides.get(0);

        select(side);
    }

    /**
     * Puts the combo on one side without that counting as the operator's own answer.  A side this square does not have
     * falls back to the first one it does, since the combo has no entry for "none of these".
     *
     * @param side the side to show
     */
    private void select(String side)
    {
        int index = side == null ? -1 : this.arrivalSides.indexOf(side);

        this.fillingArrivalSide = true;

        try
        {
            this.arrivedFrom.setSelectedIndex(index < 0 ? 0 : index);
        }
        finally
        {
            this.fillingArrivalSide = false;
        }
    }

    /**
     * The arrival side already recorded for the train standing here, if it is the one being edited.  The setup first
     * and the railway second, because the setup survives a restart; every writing door writes both, so this is a
     * fallback.
     *
     * @return the recorded side, or null when there is none or a different locomotive is selected
     */
    private String recordedHere()
    {
        if (this.p.getCurrentLocomotive() == null) return null;

        if (!this.p.getCurrentLocomotive().getName().equals(getLoc())) return null;

        org.traincontrol.automationui.TileGraph.TileKey tile = square();

        String recorded = this.session == null || tile == null
            ? null : this.session.getArrivedFrom(tile);

        return recorded != null ? recorded : this.p.getArrivedFrom();
    }

    /**
     * Which way the selected train is pointing, which is what the tail is worked out from: read for the train arriving,
     * from the combo, as `commitAndRecord` reads the heading before it commits.
     *
     * @return the heading's name, or null when there is no setup or nobody has set one
     */
    private String headingOfTheArrivingTrain()
    {
        if (this.session == null || getLoc() == null) return null;

        org.traincontrol.automationui.TilePorts.Side facing = this.session.facingOf(getLoc(), this.running);

        return facing == null ? null : facing.name();
    }

    /**
     * The side this placement should be recorded as having arrived from: the combo when there was one, else the rule (a
     * terminus has one way in), as the popup does for a square it would not ask about.
     *
     * @return the side, or null when nothing should be recorded
     */
    public String getArrivedFrom()
    {
        if (this.arrivedFrom == null)
        {
            return ArrivalSidePrompt.suggestedFor(this.running, this.p,
                headingOfTheArrivingTrain(), mayTurnHere(), sidesFromTheBuild());
        }

        int index = this.arrivedFrom.getSelectedIndex();

        return index < 0 || index >= this.arrivalSides.size() ? null : this.arrivalSides.get(index);
    }

    /**
     * Whether the operator marked this square as one trains may turn round at.  Asked of the setup, because
     * `canReverse` never reaches the running layout (the builder expresses it by splitting the square);
     * `TrainControlUI.mayTurnHere` asks the same for the paste door.
     *
     * @return true when trains may turn there
     */
    private boolean mayTurnHere()
    {
        org.traincontrol.automationui.TileGraph.TileKey tile = square();

        return this.session != null && tile != null && this.session.mayTurnTiles().contains(tile);
    }

    /**
     * This panel, with the arrival side under it, as the doors should show it.  The GUI-builder form has a fixed size,
     * so the combo goes in a wrapper built here, in one place for the two doors that show this dialog.  Returns the
     * panel itself when there is nothing to offer.
     *
     * @return what to hand to `showOptionDialog`
     */
    public javax.swing.JComponent asDialogContent()
    {
        if (this.arrivedFrom == null) return this;

        // Laid out like the rows above it (Adam, OB-203): the form's rows are label at the left and field at the right
        // with the gap stretching.  Putting the combo in CENTER stretched the combo instead of the gap; EAST is the
        // form's shape.
        final javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(this.arrivedFromLabel, java.awt.BorderLayout.WEST);
        row.add(this.arrivedFrom, java.awt.BorderLayout.EAST);

        javax.swing.JPanel wrapper = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
        wrapper.setOpaque(false);
        wrapper.add(this, java.awt.BorderLayout.CENTER);
        wrapper.add(row, java.awt.BorderLayout.SOUTH);

        // And inset to the form's own column, measured rather than guessed: the row is a sibling of the form, so it
        // spans the whole wrapper and left this label a few pixels left of every label above.  The gap is the look and
        // feel's, so it is read off a laid-out label and applied once the sizes are real.
        this.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            @Override
            public void componentResized(java.awt.event.ComponentEvent resized)
            {
                lineTheRowUpWithTheForm(row);
            }
        });

        return wrapper;
    }

    /**
     * Indents the arrived-from row to the form's own left and right margins (OB-203), read from `arrivalFuncLabel` (its
     * x is the container gap) and `arrivalFunc`'s right edge.  Only when the numbers have changed: setting a border
     * revalidates the wrapper and this runs from a resize, so an unconditional set would never settle.
     *
     * @param row the panel holding the label and the combo
     */
    private void lineTheRowUpWithTheForm(javax.swing.JPanel row)
    {
        if (this.arrivalFuncLabel == null || this.arrivalFunc == null || getWidth() <= 0) return;

        int left = this.arrivalFuncLabel.getX();

        int right = getWidth() - (this.arrivalFunc.getX() + this.arrivalFunc.getWidth());

        if (left < 0 || right < 0) return;

        javax.swing.border.Border was = row.getBorder();

        java.awt.Insets now = was == null ? null : was.getBorderInsets(row);

        if (now != null && now.left == left && now.right == right) return;

        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, left, 0, right));

        row.revalidate();
    }

    /**
     * Gets the number of locomotives selectable
     * @return
     */
    public int getNumLocs()
    {
        return this.locAssign.getModel().getSize();
    }
    
    public final void updateValues()
    {
        if (this.locAssign.getModel().getSize() > 0)
        {
            String locomotive = (String) this.locAssign.getSelectedItem();
            Locomotive loc = this.parent.getModel().getLocByName(locomotive);
            
            // Dynamically set number of selectable functions
            List<String> arrivalFuncModel= new ArrayList<>(Arrays.asList(NONE_LABEL));
            List<String> departureFuncModel= new ArrayList<>(Arrays.asList(NONE_LABEL));
            
            for (int i = 0; i < loc.getNumF(); i++)
            {
                arrivalFuncModel.add(Integer.toString(i));
                departureFuncModel.add(Integer.toString(i));
            }
            
            arrivalFunc.setModel(
                new javax.swing.DefaultComboBoxModel<>(arrivalFuncModel.toArray(new String[0])) 
            );

            departureFunc.setModel(
                new javax.swing.DefaultComboBoxModel<>(departureFuncModel.toArray(new String[0])) 
            );
            
            this.reversible.setSelected(loc.isReversible());
            
            if (loc.getArrivalFunc() != null)
            {
                this.arrivalFunc.setSelectedIndex(loc.getArrivalFunc() + 1);
            }
            else
            {
                this.arrivalFunc.setSelectedIndex(0);
            }
            
            // Items are numbers starting at 0, so we go by the max number
            if (loc.getTrainLength() < this.trainLength.getItemCount())
            {
                this.trainLength.setSelectedIndex(loc.getTrainLength());
            }
            else
            {
                this.trainLength.setSelectedIndex(this.trainLength.getItemCount() - 1);
            } 
            
            if (loc.getDepartureFunc() != null)
            {
                this.departureFunc.setSelectedIndex(loc.getDepartureFunc() + 1);
            }
            else
            {
                this.departureFunc.setSelectedIndex(0);
            }    
            
            if (loc.getPreferredSpeed() > 0)
            {
                this.speed.setValue(loc.getPreferredSpeed());
            }
            else
            {
                this.speed.setValue(this.parent.getModel().getAutoLayout().getDefaultLocSpeed());
            }
            
            updateSpeedLabel();
        }

        // And the tail follows the train: the suggested side is worked out from the selected locomotive's heading, so
        // choosing a different one has to re-ask.  A side the operator picked is left alone; see `refreshArrivalSide`.
        refreshArrivalSide();
    }
    
    public void updateSpeedLabel()
    {
        this.speedLabel.setText(
            I18n.f("autolayout.ui.labelSpeed", this.speed.getValue())
        );    
    }
    
    public boolean isReversible()
    {
        return this.reversible.isSelected();
    }
    
    public Integer getSpeed()
    {
        return this.speed.getValue();
    }
    
    public Integer getTrainLength()
    {
        return Integer.valueOf(this.trainLength.getSelectedItem().toString());
    }
    
    public Integer getDepartureFunc()
    {
        if (NONE_LABEL.equals((String) this.departureFunc.getSelectedItem()))
        {
            return null;
        }
        
        return Integer.valueOf((String) this.departureFunc.getSelectedItem());
    }
    
    public Integer getArrivalFunc()
    {
        if (NONE_LABEL.equals((String) this.arrivalFunc.getSelectedItem()))
        {
            return null;
        }
        
        return Integer.valueOf((String) this.arrivalFunc.getSelectedItem());
    }
    
    public String getLoc()
    {
        return (String) this.locAssign.getSelectedItem();
    }
    
    /**
     * Applies all changes from the UI, once the railway has accepted the placement.  Nothing when the move is refused:
     * `moveLocomotive` refuses while autonomy runs, for a point the railway does not have and for one that is no
     * station, and answers false with a log line; writing the form's settings anyway changed a train the operator read
     * as not placed.
     *
     * @return true when the locomotive was placed and its settings applied, false when the railway refused it
     */
    public boolean commitChanges()
    {
        // Onto a copy trains may not arrive at, too, where that is the heading kept (OB-284).
        if (!parent.getModel().getAutoLayout().moveLocomotive(getLoc(), p.getName(), false, true)) return false;

        parent.getModel().getLocByName(getLoc()).setReversible(isReversible());
        parent.getModel().getLocByName(getLoc()).setArrivalFunc(getArrivalFunc());
        parent.getModel().getLocByName(getLoc()).setDepartureFunc(getDepartureFunc());
        parent.getModel().getLocByName(getLoc()).setPreferredSpeed(getSpeed());
        parent.getModel().getLocByName(getLoc()).setTrainLength(getTrainLength());

        parent.getModel().getAutoLayout().applyDefaultLocCallbacks(parent.getModel().getLocByName(getLoc()));

        parent.repaintAutoLocList(false);

        return true;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        locAssign = new javax.swing.JComboBox<>();
        reversible = new javax.swing.JCheckBox();
        arrivalFunc = new javax.swing.JComboBox<>();
        arrivalFuncLabel = new javax.swing.JLabel();
        departureFuncLabel = new javax.swing.JLabel();
        departureFunc = new javax.swing.JComboBox<>();
        speedLabel = new javax.swing.JLabel();
        speed = new javax.swing.JSlider();
        departureFuncLabel1 = new javax.swing.JLabel();
        trainLength = new javax.swing.JComboBox<>();

        setMaximumSize(new java.awt.Dimension(262, 246));
        setPreferredSize(new java.awt.Dimension(262, 246));

        locAssign.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        locAssign.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        locAssign.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                locAssignActionPerformed(evt);
            }
        });

        reversible.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/traincontrol/resources/messages"); // NOI18N
        reversible.setText(bundle.getString("autolayout.ui.reversible")); // NOI18N

        arrivalFunc.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        arrivalFunc.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "---", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31" }));

        arrivalFuncLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        arrivalFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        arrivalFuncLabel.setText(bundle.getString("autolayout.ui.arrivalFunction")); // NOI18N

        departureFuncLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        departureFuncLabel.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel.setText(bundle.getString("autolayout.ui.departureFunction")); // NOI18N

        departureFunc.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        departureFunc.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "---", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31" }));

        speedLabel.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        speedLabel.setForeground(new java.awt.Color(0, 0, 115));
        speedLabel.setText(bundle.getString("ui.speed")); // NOI18N

        speed.setMinimum(1);
        speed.setMinorTickSpacing(5);
        speed.setPaintLabels(true);
        speed.setPaintTicks(true);
        speed.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                speedStateChanged(evt);
            }
        });

        departureFuncLabel1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        departureFuncLabel1.setForeground(new java.awt.Color(0, 0, 115));
        departureFuncLabel1.setText(bundle.getString("autolayout.ui.trainLength")); // NOI18N

        trainLength.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        trainLength.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20" }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(reversible)
                    .addComponent(locAssign, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(arrivalFuncLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(arrivalFunc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(speedLabel)
                    .addComponent(speed, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(departureFuncLabel)
                            .addComponent(departureFuncLabel1))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(trainLength, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(departureFunc, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(locAssign, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(reversible)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(arrivalFunc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(arrivalFuncLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(departureFuncLabel)
                    .addComponent(departureFunc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(departureFuncLabel1)
                    .addComponent(trainLength, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(speedLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(speed, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void locAssignActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_locAssignActionPerformed
       updateValues();
    }//GEN-LAST:event_locAssignActionPerformed

    private void speedStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_speedStateChanged
        updateSpeedLabel();
    }//GEN-LAST:event_speedStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> arrivalFunc;
    private javax.swing.JLabel arrivalFuncLabel;
    private javax.swing.JComboBox<String> departureFunc;
    private javax.swing.JLabel departureFuncLabel;
    private javax.swing.JLabel departureFuncLabel1;
    private javax.swing.JComboBox<String> locAssign;
    private javax.swing.JCheckBox reversible;
    private javax.swing.JSlider speed;
    private javax.swing.JLabel speedLabel;
    private javax.swing.JComboBox<String> trainLength;
    // End of variables declaration//GEN-END:variables
}
