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
     * The setup this assignment is being recorded into, and the railway it is happening on.
     *
     * Held rather than passed to `commitAndRecord` as they used to be: the dialog now offers the
     * ARRIVAL SIDE, which has to be worked out while the form is being built, and a second copy handed
     * in at commit time could name a different setup from the one the combo was filled from.  Two
     * parameters that can disagree are a trap, so there is one.
     */
    private final org.traincontrol.automationui.AutonomySession session;

    private final org.traincontrol.automation.Layout running;

    /**
     * Where the operator says the train's tail is, offered only where there is a choice.
     *
     * Null on a terminus and on a square with no track - see `buildArrivalSide`, which explains why a
     * combo with one entry is not a choice.
     */
    private javax.swing.JComboBox<String> arrivedFrom;

    private javax.swing.JLabel arrivedFromLabel;

    /**
     * The sides behind the combo's entries, in its own order, offset by one for the "---" row.
     */
    private java.util.List<String> arrivalSides = new java.util.ArrayList<>();

    /**
     * Whether the operator has picked a side themselves, in which case changing the locomotive must
     * not quietly pick a different one under them.
     */
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
    static String menuLabelFor(Point p)
    {
        return I18n.t(p != null && p.getCurrentLocomotive() == null
            ? "autolayout.ui.labelPlaceLocomotiveAt"
            : "autolayout.ui.labelEditLocomotiveAt");
    }

    /**
     * Creates new form GraphLocAssign
     * @param parent
     * @param p
     * @param newOnly - do we allow the selection of locomotives not currently on the graph?
     * @param session the setup being edited, or null when there is none
     * @param running the running layout, for working out where a train's tail lies
     */
    public GraphLocAssign(TrainControlUI parent, Point p, boolean newOnly,
        org.traincontrol.automationui.AutonomySession session,
        org.traincontrol.automation.Layout running)
    {
        initComponents();
        
        List<String> locs;
        if (newOnly)
        {
            locs = new LinkedList<>(parent.getModel().getLocList());
            
            for (Locomotive l : parent.getModel().getAutoLayout().getLocomotivesToRun())
            {
                locs.remove(l.getName());
            }
        }
        else
        {
            locs = new LinkedList<>();
            
            for (Locomotive l : parent.getModel().getAutoLayout().getLocomotivesToRun())
            {
                locs.add(l.getName());
            }
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
        
        if (newOnly)
        {
            this.arrivalFunc.setSelectedIndex(0);
            this.departureFunc.setSelectedIndex(0);
            this.trainLength.setSelectedIndex(0);
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
     * Commits an assignment AND records it in the setup - the whole of what accepting this dialog
     * means, in one place.
     *
     * Adam, 2026-09-07: **"there needs to be parity across the board.  Not sure why this would ever be
     * at risk."**  It was at risk because there are two doors and only one of them was ever taught the
     * second half. The editor's Place/Edit item wrote the placement and the facing into the setup; the
     * track diagram's did neither, so an assignment made from the diagram lived in the running layout
     * only and **reverted the next time a configuration was loaded**, silently.
     *
     * The fix that would have been wrong is copying the editor's eight lines into the menu. That is how
     * these two came apart in the first place, and it is this project's most repeated defect: one rule,
     * written twice, drifting. So the rule lives here, with the dialog it is about, and both doors call
     * it.
     *
     * **The heading is read BEFORE the commit**, because committing is what takes the train off the
     * square that knows which way it was pointing - and it is read for the train ARRIVING, from
     * `getLoc()`, not for whoever was standing here already. Recording the departing train's heading
     * for the arriving one is a placement that carefully preserves the wrong thing, which is worse than
     * one that preserves nothing.
     *
     * **The arrival side is written too, and the dialog is where it was answered** (Adam, 2026-09-11:
     * *"the missing arrival side should be set - either from the data, by the user, or randomly"*).
     *
     * It used to be left out, on the reasoning that a train nobody watched arrive has no honest
     * answer - but `behaviour.md` section 4 recorded that as an open defect (`REV9-B2`) and it was:
     * the diagram's drag and paste door works the side out and blocks the track behind the train,
     * while these two put an identical train down and blocked nothing. `GraphLocAssign` offers it as a
     * combo rather than a second popup on top of this one; `getArrivedFrom` answers from that combo,
     * or from the rule on a square that gave nothing to choose between.
     *
     * **After the commit, because the commit is what clears it**: `Point.setLocomotive` drops
     * `arrivedFrom` whenever the occupant changes, which is right - a different train did not come in
     * the way the last one did - and writing before would be writing into that.
     *
     * **Both stores, like the heading above it** (VAL8-A2): the walk that blocks track reads the live
     * `Point`, and the setup value only reaches it at the next rebuild. Written to one only, the
     * operator answers the question, watches nothing become blocked, and cannot tell "it does not
     * work" from "it has not been rebuilt yet".
     *
     * **One argument, since the dialog now holds the setup it was built against** (2026-09-11). The
     * combo's entries are worked out from that setup while the form is built, so a session handed in
     * separately at commit time could name a different one - two parameters that can disagree, which
     * is a trap this project has been caught by before. The point, the session and the layout all come
     * from the dialog, and there is nothing left for a caller to get wrong.
     *
     * @param edit the dialog, already dismissed with OK
     */
    public static void commitAndRecord(GraphLocAssign edit)
    {
        if (edit == null || edit.p == null) return;

        final org.traincontrol.automation.Point point = edit.p;

        final org.traincontrol.automationui.AutonomySession session = edit.session;

        final org.traincontrol.automation.Layout layout = edit.running;

        String arriving = edit.getLoc();

        org.traincontrol.automationui.TilePorts.Side heading =
            arriving == null || session == null ? null : session.facingOf(arriving, layout);

        edit.commitChanges();

        if (session == null) return;

        org.traincontrol.automationui.TileGraph.TileKey tile =
            session.getStationIndex() == null ? null
                : session.getStationIndex().squareOf(point.getName());

        if (tile == null) return;

        session.placeLocomotive(tile,
            point.getCurrentLocomotive() == null ? null : point.getCurrentLocomotive().getName());

        // After the commit this IS the arriving train, which is why the guard reads the point rather
        // than `arriving`: an assignment that did not take should not write a facing.
        if (point.getCurrentLocomotive() != null)
        {
            session.setFacing(tile,
                org.traincontrol.automationui.AutonomySession.facingAfterAPaste(
                    session.facingsFor(tile), heading, point.getName()));

            // AND WHERE ITS TAIL IS (REV9-B2, closed 2026-09-11).  See the note above: answered in the
            // dialog, written here, and into both stores because the walk that blocks track reads the
            // live Point while the setup is the half that survives a restart.
            String tail = edit.getArrivedFrom();

            session.setArrivedFrom(tile, tail);

            point.setArrivedFrom(tail);
        }

        // AND WRITTEN TO DISK (VAL9-B1).  The two writes above change the setup in memory only, and
        // "the placement is gone at the next configuration load" is the symptom REG8-B2 was filed for -
        // so extracting the door without the save would have moved the defect rather than fixed it.
        //
        // Every other door that writes the setup saves it: the paste door, the facing menu, placeFacing
        // beside this one on the same menu.  This is the door for two menus, so it is the one place
        // that has to.
        try
        {
            session.save();
        }
        catch (java.io.IOException cannotSave)
        {
            // The assignment stands either way; only the memory of it is at risk.  Logged rather than
            // shown, for the reason given at the other placement door (DR-B10).
            if (edit.parent != null && edit.parent.getModel() != null)
            {
                edit.parent.getModel().log(cannotSave);
            }
        }
    }
    /**
     * Offers the arrival side, where the square gives the operator anything to choose between.
     *
     * Adam, 2026-09-11: **"the missing arrival side should be set - either from the data, by the user,
     * or randomly"**, and then **"proceed with this combo.  Try to reuse the machinery/checks of the
     * current popup."**
     *
     * **What the tail is for.** `Layout.edgesCoveredByStandingTrains` walks back from a standing train
     * along the side it came in by and blocks that track for everything else. Recorded wrong, it
     * blocks the wrong rail and leaves the one the train is actually fouling open - which is worse
     * than recording nothing, because the picture then claims a protection that is not there. That is
     * the whole reason the popup asks instead of guessing, and the reason this combo can afford not
     * to: a starting value in a form is SEEN and can be corrected before OK, where a guess inside a
     * popup could not be.
     *
     * **Only where there is something to choose.** One side is a terminus - there is one way in and
     * the rule forces it, so a combo would be a control with a single entry pretending to be a
     * decision. None is a square with no track. In both cases nothing is shown and `getArrivedFrom`
     * answers from the rule, exactly as the popup does.
     *
     * The entries, the wording and the sides themselves all come from `ArrivalSidePrompt` - the same
     * three the popup uses. A second list assembled here would be a second author computing what that
     * class already decided, which is how this pair of doors came apart in the first place.
     */
    private void buildArrivalSide()
    {
        this.arrivalSides = ArrivalSidePrompt.choicesFor(this.running, this.p, sidesFromTheBuild());

        if (this.arrivalSides.size() < 2) return;

        // SIDES ONLY - there is deliberately no "---" here.
        //
        // Adam, 2026-09-07: **"clearing should not be possible, only setting."**  The arrived-from MENU
        // used to carry a "Not known" entry and it was taken out for the reason that applies here word
        // for word: every side the square has is offered, so a wrong answer is corrected by choosing
        // the right one, and what a blank entry really offers is a way to switch the tail blocking off
        // - a preference about the check rather than a fact about the railway.
        //
        // It is also what Adam asked for from the other side on 2026-09-11 - *"the missing arrival side
        // should be set"* - and `refreshArrivalSide` guarantees one: recorded, else derived, else the
        // first side this square has.
        List<String> entries = new ArrayList<>();

        for (String side : this.arrivalSides)
        {
            entries.add(ArrivalSidePrompt.labelFor(side));
        }

        this.arrivedFrom = new javax.swing.JComboBox<>(entries.toArray(new String[0]));
        this.arrivedFrom.setFont(new java.awt.Font("Segoe UI", 0, 14));

        // ITS OWN CAPTION, AND NOT THE MENU'S (Adam, OB-203).
        //
        // *"Change 'Train arrived from' to 'Train Arrived', and align horizontally with the other
        // labels."*  This used the menu's key, on the reasoning that a second wording of one setting is
        // free to drift from the first - and the two really do want different words. A menu item reads
        // as the start of a sentence its submenu finishes, "Train arrived from > north"; a label in a
        // column of Title Case nouns - Arrival Function, Departure Function - reads as one of those.
        //
        // So the drift argument is answered rather than ignored: they are two wordings because they are
        // two things, and each says so where it is.
        this.arrivedFromLabel = new javax.swing.JLabel(I18n.t("autolayout.ui.trainArrived"));
        this.arrivedFromLabel.setFont(new java.awt.Font("Segoe UI", 0, 14));
        this.arrivedFromLabel.setForeground(new java.awt.Color(0, 0, 115));

        // A HAND on the combo is remembered, so that picking a different locomotive - which moves the
        // suggestion, because it is worked out from THAT train's heading - does not overwrite what the
        // operator just said.
        this.arrivedFrom.addActionListener(event ->
        {
            if (!this.fillingArrivalSide) this.arrivalSideChosenByHand = true;
        });

        refreshArrivalSide();
    }

    /**
     * The build's own arrival sides for this square, where there is a setup to ask.
     *
     * Adam, 2026-09-07: pasting onto BottomMainPost "asks if the train arrived from the south or from
     * the west, rather than the north or the south."  The geometric answer names where the
     * neighbouring POINT lies, and a reduced edge may turn corners on the way; the builder splits the
     * square on the side the track actually comes in by, and that is the vocabulary the tail walk
     * compares against (OB-182).
     *
     * @return the sides, or null when there is no setup - `choicesFor` then falls back to the geometry
     */
    private java.util.List<org.traincontrol.automationui.TilePorts.Side> sidesFromTheBuild()
    {
        org.traincontrol.automationui.TileGraph.TileKey tile = square();

        // THE UNBARRED SIDES (OB-204).  A side the operator has closed is not a side a train arrived
        // by, and treating it as one is what made the suggested side alternate with the train's facing.
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
     * Starts the combo at the side this placement should be recorded with.
     *
     * **Three sources, in the order Adam gave them** - *"either from the data, by the user, or
     * randomly"*:
     *
     * - **From the data**, and this is the one that matters most: a train already standing here has an
     *   arrival side that something WATCHED it arrive by, and re-opening this dialog to change a
     *   function must not quietly replace a measured value with a derived one.  Read only when the
     *   selected locomotive is the one standing here - the recorded tail belongs to the train that is
     *   on the square, and attaching the departing train's tail to the arriving one would be a
     *   placement that carefully preserves the wrong thing.
     * - **From the rule**, which is `ArrivalSidePrompt.suggestedFor` - a terminus forced, an ordinary
     *   station taken from behind the heading.  Passed `mayReverse` as FALSE deliberately: the rule
     *   declines to guess on a square trains may turn at because a popup's guess would be invisible,
     *   and in a form it is not.  The operator sees it and can change it.
     * - **And otherwise the first side there is**, which is the "randomly" Adam allowed for - a
     *   heading nobody has set leaves the rule nothing to work from, and a side that is at least a side
     *   this square has beats a blank that records nothing.  There is no blank to fall back to in any
     *   case: see `buildArrivalSide` on why the combo offers sides only.
     *
     * Not called when the operator has already chosen: their answer outranks all three.
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
     * Puts the combo on one side without that counting as the operator's own answer.
     *
     * A side this square does not have falls back to the first one it does, which is the same
     * "randomly" `refreshArrivalSide` ends on: the combo offers sides and nothing else, so there is no
     * entry for "none of these".
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
     * The arrival side already recorded for the train standing here, if it is the one being edited.
     *
     * The setup first and the railway second, because the setup is the half that survives a restart -
     * but they are written together by every door that writes either, so this is a fallback rather
     * than a second opinion.
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
     * Which way the selected train is pointing, which is what the tail is worked out from.
     *
     * Read for the train ARRIVING, from the combo, and not for whoever is standing here already - the
     * same reason `commitAndRecord` reads the heading before it commits.
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
     * The side this placement should be recorded as having arrived from.
     *
     * The combo when there was one, and the rule when there was not - a terminus has one way in and
     * answers itself, which is exactly what the popup does with a square it would not ask about.  So
     * the caller gets an answer either way and never has to know which surface produced it.
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
     * Whether the operator marked this square as one trains may turn round at.
     *
     * Asked of the SETUP, because `canReverse` never reaches the running layout - the builder
     * expresses it by splitting the square and says so.  Same question `TrainControlUI.mayTurnHere`
     * asks for the paste door, and the same answer.
     *
     * @return true when trains may turn there
     */
    private boolean mayTurnHere()
    {
        org.traincontrol.automationui.TileGraph.TileKey tile = square();

        return this.session != null && tile != null && this.session.mayTurnTiles().contains(tile);
    }

    /**
     * This panel, with the arrival side under it, as the doors should show it.
     *
     * The form is laid out by the GUI builder to a fixed size, so the combo cannot be added INTO it
     * without editing generated code.  It goes underneath instead, in a wrapper this class builds -
     * one place, because two doors show this dialog and a wrapper assembled at each of them is the
     * same two-copies-of-one-rule that `commitAndRecord` exists to prevent.
     *
     * Returns the panel itself when there is nothing to offer, so a door can always show what this
     * gives it without asking whether the square had a choice.
     *
     * @return what to hand to `showOptionDialog`
     */
    public javax.swing.JComponent asDialogContent()
    {
        if (this.arrivedFrom == null) return this;

        // LAID OUT LIKE THE ROWS ABOVE IT (Adam, OB-203).
        //
        // The form's own rows are label at the left, field at the RIGHT, with the gap between them
        // stretching - `addComponent(label)`, `addPreferredGap(RELATED, DEFAULT_SIZE, MAX_VALUE)`,
        // `addComponent(field, PREFERRED_SIZE)`.  This row put the combo in CENTER, which stretched the
        // COMBO instead of the gap, so it started right after its label and ran the full width while
        // every field above it sat at its natural size against the right edge.  EAST is the same shape
        // as the form uses.
        final javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(this.arrivedFromLabel, java.awt.BorderLayout.WEST);
        row.add(this.arrivedFrom, java.awt.BorderLayout.EAST);

        javax.swing.JPanel wrapper = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
        wrapper.setOpaque(false);
        wrapper.add(this, java.awt.BorderLayout.CENTER);
        wrapper.add(row, java.awt.BorderLayout.SOUTH);

        // AND INSET TO THE FORM'S OWN COLUMN, measured rather than guessed.
        //
        // The row is a sibling of the form, so it spans the whole wrapper while the form's contents sit
        // inside a container gap - which left this label a few pixels to the left of every label above
        // it. The gap is the look and feel's, not a number this file may write down, so it is read off
        // a laid-out label instead and applied once the sizes are real.
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
     * Indents the arrived-from row to the form's own left and right margins (OB-203).
     *
     * Read from `arrivalFuncLabel`, which is the row directly above it: its x is the container gap the
     * look and feel chose, and the distance from `arrivalFunc`'s right edge to the form's edge is the
     * matching one on the other side.
     *
     * **Only when the numbers have changed**, because setting a border revalidates the wrapper and this
     * runs from a resize - an unconditional set would be a layout that never settles.
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

        // AND THE TAIL FOLLOWS THE TRAIN.  The suggested side is worked out from the SELECTED
        // locomotive's heading, so choosing a different one has to re-ask - otherwise the form shows
        // the side that was right for the train the operator just changed their mind about.  A side
        // they picked themselves is left alone; see `refreshArrivalSide`.
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
     * Applies all changes from the UI
     */
    public void commitChanges()
    {
        parent.getModel().getAutoLayout().moveLocomotive(getLoc(), p.getName(), false);

        parent.getModel().getLocByName(getLoc()).setReversible(isReversible());
        parent.getModel().getLocByName(getLoc()).setArrivalFunc(getArrivalFunc());
        parent.getModel().getLocByName(getLoc()).setDepartureFunc(getDepartureFunc());
        parent.getModel().getLocByName(getLoc()).setPreferredSpeed(getSpeed());
        parent.getModel().getLocByName(getLoc()).setTrainLength(getTrainLength());

        parent.getModel().getAutoLayout().applyDefaultLocCallbacks(parent.getModel().getLocByName(getLoc()));
        
        parent.repaintAutoLocList(false);  
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
