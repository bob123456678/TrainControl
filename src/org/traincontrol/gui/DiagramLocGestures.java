package org.traincontrol.gui;

import java.awt.event.KeyEvent;
import java.util.Map;
import javax.swing.JOptionPane;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomy.ArrivalSidePrompt;
import org.traincontrol.gui.autonomy.FacingPrompt;
import org.traincontrol.util.I18n;

/** The diagram's keyboard gestures: cutting, pasting and clearing a train on a square, and writing its turns. */
final class DiagramLocGestures
{
    private final TrainControlUI ui;

    DiagramLocGestures(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * The station on a square, for a right-click on the track itself: only while the overlay is on and only for a
     * station.  There is no page-less overload: the main window's page says nothing about a popup.
     *
     * @param onPage the page the square is actually on
     * @return the station's square, or null when there is nothing there to act on
     */
    TileGraph.TileKey autonomyStationAt(
        String onPage, int x, int y)
    {
        if (ui.autonomyOverlayToggle == null || !ui.autonomyOverlayToggle.isOverlayShown()) return null;

        AutonomySession session = ui.getAutonomySession();

        if (session == null || ui.getActiveDiagramConfiguration() == null) return null;

        Object showing = onPage != null ? onPage : ui.layoutList().getSelectedItem();

        if (showing == null) return null;

        String page = showing.toString();

        if (ui.isPageExcludedFromAutonomy(page)) return null;

        TileGraph.TileKey tile = new TileGraph.TileKey(page, x, y);

        if (!session.getStore().isStation(tile)) return null;

        return tile;
    }

    /** True while `dropByPaste` runs the paste: it never asks, and an open editor is where it comes from. */
    private boolean dropping;

    /**
     * A train dragged in the autonomy editor lands as a Control+V of it would (FR-160): the same branch, with the train
     * as the clipboard, so facing, copy, tail and road come out as a paste leaves them.  Where a paste would ask (the
     * tail's side at a square trains may turn at, the heading, the sensors the tail crossed) the drag asks nothing and
     * takes the rule's own answer.
     *
     * @return whether the train now stands on that square in the setup
     */
    boolean dropByPaste(Locomotive train, TileGraph.TileKey to)
    {
        Locomotive cutWas = ui.cutLocomotive;
        TilePorts.Side facingWas = ui.cutFacing;
        String fromWas = ui.cutFrom;
        TileGraph.TileKey hoveredWas = hoveredDiagramTile;

        ui.cutLocomotive = train;
        ui.cutFacing = null;
        ui.cutFrom = null;
        hoveredDiagramTile = to;
        dropping = true;

        TailCrossedPrompt.beQuiet(true);

        try
        {
            locomotiveGestureOnDiagram(KeyEvent.VK_V, true);
        }
        finally
        {
            TailCrossedPrompt.beQuiet(false);
            dropping = false;
            ui.cutLocomotive = cutWas;
            ui.cutFacing = facingWas;
            ui.cutFrom = fromWas;
            hoveredDiagramTile = hoveredWas;
        }

        AutonomySession session = ui.getAutonomySession();

        return session != null && train.getName().equals(session.getLocomotiveNameAt(to));
    }

    /** The diagram square under the pointer, or null: no cursor or selection exists, so this says what a key means. */
    private volatile TileGraph.TileKey hoveredDiagramTile;

    /** Sets the hovered square; page is null when the pointer has left the diagram. */
    void setHoveredDiagramTile(String page, int x, int y)
    {
        hoveredDiagramTile = page == null || x < 0 || y < 0 ? null : new TileGraph.TileKey(page, x, y);
    }

    /**
     * Moves a locomotive between squares from the keyboard: Control+X takes it off the hovered square and remembers
     * it, Control+V puts one down (what Control+X last took, else the selected locomotive), Delete takes one off
     * without remembering.  Same keys as the locomotive buttons, told apart by the pointer.  Refused while autonomy is
     * busy.  True when this handled the key, so the caller must not also act on it.
     */
    boolean locomotiveGestureOnDiagram(int keyCode, boolean controlPressed)
    {
        TileGraph.TileKey over = hoveredDiagramTile;

        // No square under the pointer, or no autonomy at all: the keys mean the buttons.
        if (over == null || !ui.getModel().hasAutoLayout()) return false;

        boolean cut = controlPressed && keyCode == KeyEvent.VK_X;
        boolean paste = controlPressed && keyCode == KeyEvent.VK_V;
        // Delete only: Backspace cycles the tabs, and this is asked first whenever the pointer rests on the diagram.
        boolean clear = !controlPressed && keyCode == KeyEvent.VK_DELETE;

        if (!cut && !paste && !clear) return false;

        // From here the key is ours: say why when refusing, as returning false would hand it to a locomotive button.
        // A route too (`aRunHoldsTheSetup`): these keys write a placement into the setup.
        if (ui.aRunHoldsTheSetup())
        {
            ui.getModel().log(ui.whyTheSetupIsHeld("layout.warnDiagramKeyWhileBusy"));

            return true;
        }

        // Nor while an editor holds the setup (OB-076): its Cancel would revert and write back this placement.
        if (!dropping && ui.isLayoutEditorOpen())
        {
            ui.getModel().logf("layout.warnDiagramKeyWhileEditorOpen");

            return true;
        }

        // The square under the pointer, or the station its LABEL is about: pointing at the name is pointing at it.
        TileGraph.TileKey aimed = over;

        Point point = ui.getAutonomyPointForTile(aimed);

        if (point == null)
        {
            AutonomySession session = ui.getAutonomySession();

            TileGraph.TileKey captioned = session == null ? null : session.getCaptionTarget(over);

            if (captioned != null)
            {
                aimed = captioned;
                point = ui.getAutonomyPointForTile(aimed);
            }
        }

        if (point == null)
        {
            // Autonomy does not know this square: worth saying over one the user believes is a station.
            ui.getModel().logf("layout.warnNoAutonomyPointHere", over.toString());

            return true;
        }

        if (paste)
        {
            Locomotive placing = ui.cutLocomotive != null ? ui.cutLocomotive : ui.getActiveLoc();

            // Nothing to put down must not fall through to the button shortcut: the user is pointing at the diagram.
            if (placing == null) return true;

            // A train is only ever placed at a station (OB-365): said before the facing and tail questions.
            AutonomySession onto = ui.getAutonomySession();

            if (onto == null || !onto.getStore().isStation(aimed))
            {
                // A dragged train is only offered a station, and never meets a dialog
                if (dropping) return true;

                JOptionPane.showMessageDialog(ui.dialogParent(), I18n.t("autolayout.ui.errorTrainOnlyAtStation"));

                return true;
            }

            // Asked of the square, not one copy: an empty square may hand back the dead-end copy while another can
            // depart.  A station no copy can leave is placed on all the same (OB-350).
            boolean anyWayOut = onto.canDepartFrom(ui.getModel().getAutoLayout(), aimed);

            // Where it would end up if it drove there, read before the move takes it off the walk's starting copy.
            ui.facingAtTheLanding = ui.getAutonomySession() == null ? null
                : ui.getAutonomySession().facingByPath(ui.getModel() == null ? null
                    : ui.getModel().getAutoLayout(), placing.getName(), aimed);

            // A cut train keeps the heading it was cut with (MT-368): Control+X took it off the railway, so the walk
            // has nowhere to start.  Only where the landing can HOLD it (as `facingAfterAPaste`), only for the
            // clipboard's train, and only while it is off the railway: `placing == this.cutLocomotive` stays true
            // while another door can put it back, so the condition is "the walk had nowhere to start".
            boolean stillLifted = ui.getModel() == null || ui.getModel().getAutoLayout() == null
                || ui.getModel().getAutoLayout().getLocomotiveLocation(placing) == null;

            // Walked from the square it was cut from, first (OB-270): the cut heading is a compass heading, and a
            // route that loops round arrives the other way.  The cut heading stands only where no route reaches it.
            TilePorts.Side walkedFromTheCut =
                placing == ui.cutLocomotive && stillLifted && ui.cutFrom != null
                    && ui.getAutonomySession() != null && ui.getModel() != null
                ? ui.getAutonomySession().facingByPathFrom(ui.getModel().getAutoLayout(), ui.cutFrom, aimed) : null;

            if (walkedFromTheCut != null)
            {
                ui.facingAtTheLanding = walkedFromTheCut;
            }
            else if (ui.cutFacing != null && placing == ui.cutLocomotive && stillLifted
                && ui.getAutonomySession() != null
                && ui.getAutonomySession().facingsFor(aimed).containsValue(ui.cutFacing))
            {
                ui.facingAtTheLanding = ui.cutFacing;
            }

            // Where its tail is, asked BEFORE anything moves: a dismissed prompt refuses the placement and leaves the
            // train on the clipboard, unlike the other two nulls `forPlacement` answers.
            ui.tailAtTheLanding = ArrivalSidePrompt.forPlacement(
                ui.getModel() == null ? null : ui.getModel().getAutoLayout(), point,
                ui.facingAtTheLanding == null ? null : ui.facingAtTheLanding.name(),
            // The build's sides, which the model reads too (OB-182): the geometric answer names where the neighboring
            // POINT lies, and a reduced run turns corners.  `Layout.entrySideOf` is what both writes and the walk read.
                mayTurnHere(aimed) && !dropping, ui.dialogParent(),
                ui.getAutonomySession() == null ? null : ui.getAutonomySession().unbarredArrivalSides(aimed));

            if (!dropping && ui.tailAtTheLanding == null && ArrivalSidePrompt.wouldAsk(
                ui.getModel() == null ? null : ui.getModel().getAutoLayout(), point,
                mayTurnHere(aimed), ui.getAutonomySession() == null
                    ? null : ui.getAutonomySession().unbarredArrivalSides(aimed)))
            {
                return true;
            }

            // A second question, the way it points (Adam: "If pasting at a 'may reverse' square, ask what direction
            // the train should face."): both headings are reachable there.  Asked before the move.
            ui.facingChosenAtTheLanding = null;

            if (mayTurnHere(aimed) && ui.getAutonomySession() != null)
            {
                java.util.Collection<TilePorts.Side> canHold = ui.getAutonomySession().facingsFor(aimed).values();

                if (!dropping && FacingPrompt.wouldAsk(canHold))
                {
                    // Named as the square: a copy's name states a heading, which is what this asks.
                    ui.facingChosenAtTheLanding = FacingPrompt.forPlacement(
                        canHold, ui.facingAtTheLanding, ui.getAutonomySession().baseNameOf(point.getName()),
                        ui.dialogParent());

                    if (ui.facingChosenAtTheLanding == null) return true;
                }
            }

            // The train goes onto the copy that faces that way (MT-394): direction is which copy a train stands on.
            if (ui.facingChosenAtTheLanding != null)
            {
                Point facingThatWay = copyFacing(aimed, ui.facingChosenAtTheLanding);

                if (facingThatWay != null) point = facingThatWay;
            }
            else
            {
                // Where nobody was asked, the heading the rule arrives at (OB-270): `point` is any copy, so the
                // train could stand on the opposite-facing copy while the setup recorded the walk's heading.
                TilePorts.Side intended =
                    AutonomySession.facingAfterAPaste(
                        placeableFacings(aimed), ui.facingAtTheLanding, point.getName());

                Point facingThatWay = !anyWayOut
                    ? ui.getAutonomySession().copyWithNoFacing(aimed, ui.getModel().getAutoLayout())
                    : copyFacing(aimed, intended);

                if (facingThatWay != null) point = facingThatWay;
            }

            // The railway's answer decides here too: `isAutonomyBusy()` does not catch a target that is not a
            // destination, and the clipboard was cleared for a move that did not happen.  What the train had on the
            // railway, before this paste moves it: the tail question's keep-or-drop is decided from these.
            Point wasOn = null;

            for (Point each : ui.getModel().getAutoLayout().getPoints())
            {
                if (each.getCurrentLocomotive() == placing) wasOn = each;
            }

            ui.roadBeforeTheLanding = wasOn == null ? null : wasOn.getArrivedAlong();
            ui.sideBeforeTheLanding = wasOn == null ? null : wasOn.getArrivedFrom();
            ui.squareBeforeTheLanding = wasOn == null || ui.getAutonomySession() == null
                || ui.getAutonomySession().getStationIndex() == null
                ? null : ui.getAutonomySession().getStationIndex().squareOf(wasOn);

            if (!ui.getModel().getAutoLayout().moveLocomotive(placing.getName(), point.getName(), false, true))
            {
                // The clipboard still holds it, so the next square accepts the same paste.
                return true;
            }

            ui.cutLocomotive = null;

            ui.cutFacing = null;

            ui.cutFrom = null;
        }
        else
        {
            // Remembered before the move, which clears the square.
            ui.cutLocomotive = cut ? point.getCurrentLocomotive() : null;

            // Control+X takes the train off the layout, so its facing is read from the railway, which alone knows
            // where a run left the trains (MT-368); where it stood is kept so the paste can walk from there (OB-270).
            ui.cutFrom = cut ? point.getName() : null;

            ui.cutFacing = !cut || ui.getAutonomySession() == null ? null
                : ui.getAutonomySession().facingOf(
                    point.getCurrentLocomotive() == null ? null
                        : point.getCurrentLocomotive().getName(),
                    ui.getModel() == null ? null : ui.getModel().getAutoLayout());

            // AND THE HEADING FROM THE LAST DRAG GOES WITH IT.  This branch clears a square rather than filling one, so
            // it leaves no heading behind, and a leftover value would be read as this placement's.
            ui.facingAtTheLanding = null;
            ui.tailAtTheLanding = null;

            ui.getModel().getAutoLayout().moveLocomotive(null, point.getName(), true);
        }

        // Into the setup too, against the square aimed at, since the setup records trains against sensors.
        rememberPlacement(point, aimed);

        ui.updateVisiblePoints();
        ui.repaintAutoLocList(false);

        return true;
    }

    /**
     * Whether the operator marked this square as one trains may turn around at.  Asked of the setup, as `canReverse`
     * never reaches the running layout (the builder expresses it by splitting the square).
     *
     * @param tile the square
     * @return true when trains may turn there
     */
    private boolean mayTurnHere(TileGraph.TileKey tile)
    {
        return ui.getAutonomySession() != null && tile != null
            && ui.getAutonomySession().mayTurnTiles().contains(tile);
    }

    /** The copy of a square a train facing this way stands on, preferring one that does not turn it; null if none. */
    private Point copyFacing(
        TileGraph.TileKey square,
        TilePorts.Side facing)
    {
        if (square == null || facing == null || ui.getAutonomySession() == null
            || ui.getModel() == null || !ui.getModel().hasAutoLayout())
        {
            return null;
        }

        // The session's rule; it falls back to a copy no train arrives at where only that one faces that way (OB-284).
        return ui.getAutonomySession().copyFacing(square, facing, ui.getModel().getAutoLayout());
    }

    /** Writes the turns made at destinations into the setup, keeping those it could not; null `session` writes none. */
    private void writeTheTurns(Layout built,
        AutonomySession session)
    {
        // Forgotten only once written, so a turn that never reached the graph is put back.
        Map<String, String> turnedRound = built.takeReversalsOnArrival();

        try
        {
            if (session != null)
            {
                for (java.util.Iterator<Map.Entry<String, String>> pending
                    = turnedRound.entrySet().iterator(); pending.hasNext();)
                {
                    Map.Entry<String, String> turned = pending.next();

                    // The way it came in, not the setup's stored facing, which is stale now (a run moves trains
                    // and writes nothing back; OB-190).  A train turned around faces the way it came in.
                    if (session.faceTheWayItCameIn(turned.getKey(),
                        built.getPoint(turned.getValue()), built) == null)
                    {
                        // Not written, so not forgotten; retrying is safe because the write is absolute.
                        continue;
                    }

                    pending.remove();
                }
            }
        }
        finally
        {
            built.restoreReversalsOnArrival(turnedRound);
        }
    }

    /**
     * Writes the turns the railway owes into the running configuration before a fold that leaves the railway during a
     * run (Stop Using Autonomy, another configuration chosen), as turns are otherwise written only while idle (OB-189).
     */
    void writeTheTurnsOwed()
    {
        if (ui.getModel() == null || !ui.getModel().hasAutoLayout()) return;

        writeTheTurns(ui.getModel().getAutoLayout(), ui.getAutonomySession());
    }

    /**
     * Levels the direction baseline whenever the railway is idle, so nothing is followed twice.
     *
     * Adam: "Reversals during the run should be ignored and not queued.  Only count reversals when nothing is running.
     * That way, there is no backlog."
     *
     * Called from the refresh, not the automation layer, which must not reach into the window; safe to repeat.
     */
    void reconcileFacingWhenIdle()
    {
        try
        {
            if (ui.getModel() == null || !ui.getModel().hasAutoLayout()) return;

            Layout built = ui.getModel().getAutoLayout();

            // ONLY WHEN NOTHING IS RUNNING, which is the whole of the rule.  A train part way through
            // a journey is MEANT to disagree with the graph: it is between two copies.
            if (built.isRunning()) return;

            // What the railway turned around at its destination is written to the graph before the baseline is leveled,
            // or a manual "no, turn" there is invisible and the next dispatch offers paths for the wrong heading.
            AutonomySession session = ui.getAutonomySession();

            writeTheTurns(built, session);

            for (Point point : built.getPoints())
            {
                Locomotive loc = point.getCurrentLocomotive();

                if (loc == null || loc.getName() == null) continue;

                // The baseline is what the window last acted on.  Bringing it level here is what stops
                // the first idle echo re-following a turn the run already made.
                ui.lastSeenDirection.put(loc.getName(), loc.goingForward());
            }
        }
        catch (Exception cannotReconcile)
        {
            // A DIAGRAM SLIGHTLY OUT OF DATE rather than a broken refresh.
            if (ui.getModel() != null) ui.getModel().log(cannotReconcile.getMessage());
        }
    }

    /**
     * Writes a square's occupant into the autonomy setup, after the layout has been changed.
     */

    private void rememberPlacement(Point point,
        TileGraph.TileKey tile)
    {
        AutonomySession session = ui.getAutonomySession();

        if (session == null) return;

        boolean setupStands = true;

        session.placeLocomotive(tile,
            point.getCurrentLocomotive() == null ? null : point.getCurrentLocomotive().getName());

        // And its heading: with no stored facing, `placementCopy` falls through to copy 0, which nobody chose.
        if (point.getCurrentLocomotive() != null)
        {
            // And its tail: a train put down by hand has no arrival to watch, so the side it came in by is worked out,
            // or asked where trains may turn.  Decided before the move and only recorded here.
            String tail = ui.tailAtTheLanding;

            // Read now, not after the tail question: a second Control+V in that wait (FR-100) overwrites these fields.
            final java.util.List<org.traincontrol.automation.Edge> roadBefore = ui.roadBeforeTheLanding;
            final boolean sameSquareAsBefore = tile.equals(ui.squareBeforeTheLanding);
            final String sideBefore = ui.sideBeforeTheLanding;
            final TilePorts.Side facingChosen = ui.facingChosenAtTheLanding;
            final TilePorts.Side facingFound = ui.facingAtTheLanding;

            // The landing copy's side is not the train's heading (read before the move into `facingAtTheLanding`).  The
            // operator's answer wins, else `facingAfterAPaste` over the copies a train may stand on (OB-270).  Written
            // before the tail question.
            session.setFacing(tile, facingChosen != null ? facingChosen
                : AutonomySession.facingAfterAPaste(
                    placeableFacings(tile), facingFound, point.getName()));

            setupStands = TailCrossedPrompt.recordTheTail(session, tile, ui.getModel().getAutoLayout(),
                point, tail, point.getCurrentLocomotive().getName(), roadBefore, sameSquareAsBefore, sideBefore, ui,
                ui, ui::noteIfTheSetupWasNotTidied);
        }

        // Not a setup the window has let go: one replaced in the wait was written by the reset that replaced it, and
        // saving it now would reconcile against pages the editor changed in place.
        if (!setupStands) return;

        try
        {
            ui.noteIfTheSetupWasNotTidied(session.save());
        }
        catch (java.io.IOException e)
        {
            // The placement stands either way; only the memory of it is at risk
            ui.getModel().log(e);
        }
    }

    /**
     * The copies of a square a train may be put down on, with their facings, by name in build order (OB-270): those
     * it could leave (OB-284), so a paste never saves a facing the train is not standing in.
     */
    private Map<String, TilePorts.Side> placeableFacings(
        TileGraph.TileKey square)
    {
        if (square == null || ui.getAutonomySession() == null || ui.getModel() == null || !ui.getModel().hasAutoLayout())
        {
            return new java.util.LinkedHashMap<>();
        }

        return ui.getAutonomySession().departableFacingsFor(square, ui.getModel().getAutoLayout());
    }
}
