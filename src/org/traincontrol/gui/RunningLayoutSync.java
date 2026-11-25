package org.traincontrol.gui;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The setup and the running railway kept in step: folding the railway into the setup, rebuilding it from the setup with
 * every train and pending turn carried across, and the record of the settings the railway was built with.
 *
 * Every field stays on `TrainControlUI`, and every `synchronized` declaration with it; this takes no lock of its own.
 */
final class RunningLayoutSync
{
    private final TrainControlUI ui;

    RunningLayoutSync(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Runs a load that folds the running layout, keeping the destination turns the railway made and not yet written
     * into the setup (a turn is written only while idle).  Put back on whatever railway the load leaves.
     *
     * @param load the load
     */
    void keepThePendingTurnsAcross(Runnable load)
    {
        Map<String, String[]> pendingTurns = takeThePendingTurns();

        try
        {
            load.run();
        }
        finally
        {
            putThePendingTurnsBack(pendingTurns);
        }
    }

    /**
     * Folds what the running layout knows (where a run left the locomotives) back into the configuration and writes it.
     * Callers ask `isSetupNewerThanTheRunningLayout` first; it happens BEFORE a page changes, while the naming and the
     * store agree (`renamePage` rekeys it): refused after a run, Start could route a train into an occupied block.
     * Written, not reconciled: see resetAutonomySession.
     */
    void captureRunningLayout()
    {
        Exception failed = captureRunningLayout(false);

        if (failed != null) this.ui.getModel().log(failed);
    }

    /**
     * The same, told whether a railway that holds a path is folded too, each train under way written on the one point
     * it is kept at (Unload's fold, after its Yes stopped the trains); other doors do not fold a railway reading busy.
     *
     * @param underWayToo whether to fold a railway that holds a path
     * @return what kept the fold from being written, for the caller to log or report; null when written or not needed
     */
    Exception captureRunningLayout(boolean underWayToo)
    {
        if (this.ui.autonomySession() == null || this.ui.getActiveDiagramConfiguration() == null
            || this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()
            || !this.ui.getModel().getAutoLayout().isValid()
            || (!underWayToo && this.ui.getModel().getAutoLayout().isRunning()))
        {
            return null;
        }

        // While a setup edit waits for a load, fold only what the railway owns: folding all would write the older
        // layout over the edit, folding nothing would lose where the run left the trains.  The rebuild or load clears
        // the flag.
        try
        {
            // Each train under way on the one point it is kept at; a railway at rest keeps none
            Layout folding = this.ui.getModel().getAutoLayout();

            String railway = folding.toJSON(folding.getLastPointsReached());

            // While the setup is newer, what the railway owns alone: where the trains stand, the settings, the
            // timetable.
            if (this.ui.setupEditDeclinedDuringRun)
            {
                this.ui.autonomySession().captureWhatTheRailwayOwns(railway, this.ui.getActiveDiagramConfiguration(), settingsAsBuiltFor(folding));
            }
            else this.ui.autonomySession().captureFromLayout(railway, this.ui.getActiveDiagramConfiguration(), settingsAsBuiltFor(folding));

            // Written, not reconciled: on the editor path the pages are mutated in place and Cancel re-reads new page
            // objects, so reconciling would delete the settings of every square the user deleted and restored.
            this.ui.autonomySession().saveWithoutReconciling();

            return null;
        }
        catch (Exception e)
        {
            return e;
        }
    }

    /**
     * Writes these trains' pauses into the railway's settings as built, for a door that changed them on the railway and
     * in the setup together.  Only the trains the door changed (OB-343): the whole list would hide the Auto tab's
     * pauses.
     */
    void notePausesAsBuilt(java.util.Collection<Locomotive> changed)
    {
        Object railway = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

        if (railway == null || railway != this.ui.settingsBuiltFor || this.ui.settingsAsBuilt == null) return;

        // sorted, as the railway writes its list, so the fold's comparison of the two lists reads them as the same
        Set<String> built = new java.util.TreeSet<>();

        JSONArray list = this.ui.settingsAsBuilt.optJSONArray(Layout.PAUSED_LOCOMOTIVES);

        for (int i = 0; list != null && i < list.length(); i++)
        {
            Object name = list.opt(i);

            if (name instanceof String) built.add((String) name);
        }

        for (Locomotive train : changed)
        {
            if (train == null || train.getName() == null) continue;

            if (train.isAutonomyPaused()) built.add(train.getName());
            else built.remove(train.getName());
        }

        if (built.isEmpty()) this.ui.settingsAsBuilt.remove(Layout.PAUSED_LOCOMOTIVES);
        else this.ui.settingsAsBuilt.put(Layout.PAUSED_LOCOMOTIVES, new JSONArray(built));
    }

    /**
     * Reads the settings and timetable of the railway just built, for the folds to compare against; called straight
     * after.
     */
    void noteTheRailwaysSettingsAsBuilt()
    {
        Layout built = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

        this.ui.settingsBuiltFor = built;
        this.ui.settingsAsBuilt = null;

        if (built == null) return;

        try
        {
            this.ui.settingsAsBuilt = AutonomySession.globalsOf(new JSONObject(built.toJSON()));
        }
        catch (Exception cannotRead)
        {
            this.ui.settingsAsBuilt = null;
        }
    }

    /**
     * @param railway a railway
     * @return its settings as built, or null where unknown; the folds change it in place (OB-343)
     */
    JSONObject settingsAsBuiltFor(Object railway)
    {
        return railway != null && railway == this.ui.settingsBuiltFor ? this.ui.settingsAsBuilt : null;
    }

    /**
     * Rebuilds the running layout from what the setup now says (OB-034); the diagram's own menu asks for it too.  Not
     * while trains are moving: see prepareAutonomyReload.
     */
    void rebuildRunningLayoutFromSetup()
    {
        // No placement edit is pending: every placement made in the editor already reached the running layout.
        rebuildRunningLayoutFromSetup(false, null, null);
    }

    /**
     * The road a train came in on, put back on the Point it was put back on; `was` is from `whereTheTrainsAre`.
     */
    private static void putTheRoadBack(Layout built,
        Point back, String[] was)
    {
        if (was == null || was.length < 3 || was[2] == null) return;

        java.util.List<org.traincontrol.automation.Edge> road = built.roadNamed(was[2]);

        if (road != null) back.setArrivedAlong(road);
    }

    /**
     * The destination turns the railway made and not yet written into the setup, DRAINED as the Layout is about to be
     * thrown away; pairs with `putThePendingTurnsBack`.
     */
    Map<String, String[]> takeThePendingTurns()
    {
        if (this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()) return java.util.Collections.emptyMap();

        Layout layout = this.ui.getModel().getAutoLayout();

        Map<String, String[]> out = new LinkedHashMap<>();

        // With the square and facing of the Point each turn was made at, read before the load replaces this railway.
        for (Map.Entry<String, String> turn : layout.takeReversalsOnArrival().entrySet())
        {
            Point at = turn.getValue() == null ? null : layout.getPoint(turn.getValue());

            out.put(turn.getKey(), new String[] {turn.getValue(), at == null ? null : at.getSquare(),
                at == null ? null : at.getCopyFacing()});
        }

        return out;
    }

    /**
     * Puts those turns onto the rebuilt Layout, each only IF ABSENT, so a train that has turned again keeps its record.
     */
    void putThePendingTurnsBack(Map<String, String[]> turns)
    {
        if (turns == null || turns.isEmpty()) return;

        if (this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()) return;

        AutonomySession naming = this.ui.autonomySession();

        Map<String, String> named = new LinkedHashMap<>();

        for (Map.Entry<String, String[]> turn : turns.entrySet())
        {
            String[] at = turn.getValue();

            // BY THE NAME THE REBUILD GIVES THE SAME COPY; where it has none, the name recorded
            String now = naming == null || at[0] == null ? at[0] : naming.pointNamedNow(at[0], at[1], at[2]);

            named.put(turn.getKey(), now != null ? now : at[0]);
        }

        this.ui.getModel().getAutoLayout().restoreReversalsOnArrival(named);
    }

    /**
     * Puts each train back where it stood before the rebuild (OB-183), on the Point the rebuild gives its copy; a train
     * whose square is gone stays where the rebuild put it.  `placementsJustEdited`: trains just given a placement, or
     * null.
     */
    void putTheTrainsBack(Map<String, String[]> standing,
        Set<String> placementsJustEdited)
    {
        if (this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()) return;

        // BY THE NAMES THE REBUILD GIVES THE SAME POINTS, which the session traces
        AutonomySession naming = this.ui.autonomySession();

        Set<String> notBack = putTheTrainsBack(this.ui.getModel().getAutoLayout(), standing, this.ui.getModel()::log,
            placementsJustEdited, naming == null ? null : naming::pointNamedNow);

        recordTheTrainsNotPutBack(this.ui.getModel().getAutoLayout(), standing, notBack, naming, this.ui.getModel()::log);

        // AND THE DIAGRAM TOLD: it learned which trains stand on no Point inside the load, before this
        if (!notBack.isEmpty() && naming != null && this.ui.diagramMonitorDriver != null)
        {
            this.ui.diagramMonitorDriver.trainsOnNoPointChanged(naming);
        }
    }

    /**
     * A train the rebuild could not put back is taken off wherever the rebuild put it and recorded in the setup (null
     * `setup` records nothing) where it stands and faces, so its square does not read free; where no copy faces its
     * way the setup check's error stops every send until it is turned, moved, or the track is put back.
     */
    static void recordTheTrainsNotPutBack(Layout built,
        Map<String, String[]> standing, Set<String> notBack,
        AutonomySession setup, Consumer<String> log)
    {
        if (built == null || standing == null || notBack == null || notBack.isEmpty()) return;

        for (String train : notBack)
        {
            String[] was = standing.get(train);

            // OFF WHEREVER THE REBUILD PUT IT: not where it stands
            for (Point point : new java.util.ArrayList<>(built.getPoints()))
            {
                if (point.getCurrentLocomotive() != null && train.equals(point.getCurrentLocomotive().getName()))
                {
                    built.moveLocomotive(null, point.getName(), true);
                }
            }

            org.traincontrol.automationui.TileGraph.TileKey square = was == null || was.length < 4 || was[3] == null
                ? null : org.traincontrol.automationui.AutonomyCompanionStore.parseTileKey(was[3]);

            if (setup == null || square == null) continue;

            setup.placeLocomotive(square, train);

            TilePorts.Side facing = null;

            for (TilePorts.Side side : TilePorts.Side.values())
            {
                if (was.length > 4 && side.name().equals(was[4])) facing = side;
            }

            if (facing != null) setup.setFacing(square, facing);

            // AND THE SIDE IT CAME IN BY, AND ITS ROAD: without them the track its tail lies over was offered to others
            if (was.length > 1 && was[1] != null) setup.setArrivedFrom(square, was[1]);

            if (was.length > 2 && was[2] != null)
            {
                try
                {
                    setup.setPointProperty(square, "arrivedAlong", new JSONArray(was[2]));
                }
                catch (JSONException notARoad)
                {
                    // a road that does not read is no road: the side alone is kept
                }
            }

            if (log != null) log.accept(I18n.f("autosetup.errorTrainNotPutBack", train, square.toString()));
        }
    }

    /** The same, over a layout handed in, so it can be tested without a window (MT-337). */
    static Set<String> putTheTrainsBack(Layout built,
        Map<String, String[]> standing, Consumer<String> log)
    {
        return putTheTrainsBack(built, standing, log, null);
    }

    /**
     * The same, each train and road put back on the Point `namedNow` gives its copy from the name, square and facing
     * recorded; null goes by the names recorded.
     */
    static Set<String> putTheTrainsBack(Layout built,
        Map<String, String[]> standing, Consumer<String> log,
        Set<String> placementsJustEdited, TrainControlUI.NamedNow namedNow)
    {
        Set<String> nowhere = new LinkedHashSet<>();

        Set<String> notBack = putTheTrainsBack(built,
            namedNow == null ? standing : namedNow(standing, namedNow, nowhere), log, placementsJustEdited);

        // AND THOSE WHOSE COPY THE REBUILD HAS NOT GOT, unless the setup was just given a newer answer for them
        for (String train : nowhere)
        {
            if (placementsJustEdited == null || !placementsJustEdited.contains(train)) notBack.add(train);
        }

        return notBack;
    }

    /**
     * What `whereTheTrainsAre` recorded in the names the rebuild gives the same Points; a train whose Point the rebuild
     * lacks is left out and stays where the rebuild put it.
     */
    private static Map<String, String[]> namedNow(Map<String, String[]> standing,
        TrainControlUI.NamedNow namedNow, Set<String> nowhere)
    {
        if (standing == null) return null;

        Map<String, String[]> out = new LinkedHashMap<>();

        for (Map.Entry<String, String[]> was : standing.entrySet())
        {
            String[] now = was.getValue() == null ? null : was.getValue().clone();

            if (now != null && now.length > 0 && now[0] != null)
            {
                now[0] = namedNow.pointNamedNow(now[0], now.length > 3 ? now[3] : null, now.length > 4 ? now[4] : null);

                // NO COPY OF ITS SQUARE FACING ITS WAY NOW: said, not left where the rebuild put it
                if (now[0] == null)
                {
                    nowhere.add(was.getKey());

                    continue;
                }
            }

            JSONObject roadSquares = null;

            try
            {
                roadSquares = now != null && now.length > 5 && now[5] != null ? new JSONObject(now[5]) : null;
            }
            catch (JSONException notSquares)
            {
                roadSquares = null;
            }

            if (now != null && now.length > 2 && now[2] != null)
            {
                try
                {
                    JSONArray road = new JSONArray(now[2]);

                    for (int i = 0; i < road.length(); i++)
                    {
                        JSONArray step = road.optJSONArray(i);

                        for (int j = 0; step != null && j < step.length(); j++)
                        {
                            if (step.opt(j) instanceof String)
                            {
                                JSONArray of = roadSquares == null ? null : roadSquares.optJSONArray(step.getString(j));

                                String then = namedNow.pointNamedNow(step.getString(j),
                                    of == null || of.isNull(0) ? null : of.optString(0),
                                    of == null || of.isNull(1) ? null : of.optString(1));

                                if (then != null) step.put(j, then);
                            }
                        }
                    }

                    now[2] = road.toString();
                }
                catch (JSONException notARoad)
                {
                    // left as it is, and `roadNamed` finds no road in it
                }
            }

            out.put(was.getKey(), now);
        }

        return out;
    }

    /**
     * The same, told which trains the setup was just given a new placement for (`placementsJustEdited`, or null).
     * Where the rebuilt placement and `standing` disagree, the setup wins for a just-edited train (MT-337), the record
     * otherwise (OB-183), per TRAIN.  Returns the trains it could not put back; `log` is where to say so.
     */
    static Set<String> putTheTrainsBack(Layout built,
        Map<String, String[]> standing, Consumer<String> log,
        Set<String> placementsJustEdited)
    {
        Set<String> notBack = new LinkedHashSet<>();

        if (standing == null || standing.isEmpty()) return notBack;

        if (built == null) return notBack;

        for (Map.Entry<String, String[]> was : standing.entrySet())
        {
            try
            {
                Point back = built.getPoint(was.getValue()[0]);

                if (back == null)
                {
                    if (placementsJustEdited == null || !placementsJustEdited.contains(was.getKey())) notBack.add(was.getKey());

                    continue;
                }

                // A station copy facing the same way where the recorded one is barred: autonomy can start the train
                // there.
                Point twin = built.startableTwinOf(back);

                if (twin != null) back = twin;

                // The railway wins, unless this train's placement is the edit (OB-183): where a train IS is a fact,
                // where the file thinks it is a record.  The door that placed the train supplies the exception.
                if (placementsJustEdited == null || !placementsJustEdited.contains(was.getKey()))
                {
                    if (back.getCurrentLocomotive() == null
                        || !was.getKey().equals(back.getCurrentLocomotive().getName()))
                    {
                        // And only where it went: `moveLocomotive` refuses a square that is not a destination.  Except
                        // a barred copy of a station, which a reversed or Facing-menu-turned train stands on because it
                        // IS its direction; onto its own copy even where that is no station now.
                        if (!built.moveLocomotive(was.getKey(), back.getName(), false, true, true))
                        {
                            notBack.add(was.getKey());

                            continue;
                        }
                    }

                    // The arrival side goes back with the train: `Point.setLocomotive` clears it on a change of
                    // occupant (OB-183).
                    if (was.getValue()[1] != null) back.setArrivedFrom(was.getValue()[1]);

                    // And the road, looked up again on this layout after the move, which clears both.
                    putTheRoadBack(built, back, was.getValue());

                    continue;
                }

                // Where the rebuild put it, which is where the SETUP says it is: writing this record over it would undo
                // the edit (MT-337).  Reached only for trains somebody chose to place
                // (`AutonomySession.placeLocomotive`).
                Point now = null;

                for (Point each : built.getPoints())
                {
                    if (each.getCurrentLocomotive() != null
                        && was.getKey().equals(each.getCurrentLocomotive().getName()))
                    {
                        now = each;

                        break;
                    }
                }

                // AND THE ROAD, where the train is where it was.
                if (now == back) putTheRoadBack(built, back, was.getValue());

                if (now == back && was.getValue()[1] != null)
                {
                    // Only the arrival side, and only where it did not move: `Point.setLocomotive` clears it on a new
                    // occupant.
                    back.setArrivedFrom(was.getValue()[1]);
                }

                // And `now == null` is an answer too: Remove and Clear Every Locomotive write "nowhere" into the setup,
                // so a rebuild that places this train on no square carries the edit.
            }
            catch (Exception cannotPutItBack)
            {
                // One train, not the rebuild: a locomotive that cannot be put back is said out loud, the others go on.
                if (log != null) log.accept(cannotPutItBack.getMessage());

                notBack.add(was.getKey());
            }
        }

        return notBack;
    }

    /**
     * The same rebuild, saying so (`sayIfDeclined`) when it declines one the operator was not warned about.  A run
     * starting inside the coalesced rebuild's event finds the gate shut and the edit is thrown away (OB-144): stop the
     * run and edit again.  `placementsJustEdited`: locomotives whose placement this gesture wrote, or null;
     * `standingBefore`: from a Cancel, where each train stood when the editor opened, or null.
     */
    void rebuildRunningLayoutFromSetup(boolean sayIfDeclined,
        Set<String> placementsJustEdited, Map<String, String[]> standingBefore)
    {
        if (sayIfDeclined && this.ui.getActiveDiagramConfiguration() != null && this.ui.isAutonomyBusy()
            && this.ui.getAutonomyViewerPanel() != null)
        {
            this.ui.getModel().logf(java.util.logging.Level.SEVERE, "autosetup.log.setupEditNotApplied");
        }

        // A declined edit is recorded so the exit does not delete it: the save on the way out folds the RUNNING layout
        // over the configuration, and that layout was built before the edit.  Only from the door that carries an edit
        // (it rides on `sayIfDeclined`); the excluded door cannot reach here while autonomy is busy (OB-047).
        if (sayIfDeclined && this.ui.getActiveDiagramConfiguration() != null && this.ui.isAutonomyBusy()
            && this.ui.getAutonomyViewerPanel() != null)
        {
            this.ui.setupNewerThanTheRailway(true);
        }

        if (this.ui.getActiveDiagramConfiguration() != null && !this.ui.isAutonomyBusy()
            && this.ui.getAutonomyViewerPanel() != null)
        {
            // Without capturing the running layout first: the setup just changed, so a fold would write the running
            // layout's stale answer over the edit.  Where the trains ARE survives the rebuild (OB-183): placements
            // only, except `placementsJustEdited` (MT-337).
            Map<String, String[]> standing = this.ui.whereTheTrainsAre();

            // A Cancel stands each train back where the railway had it when the editor opened; one it did not have then
            // follows the restored setup
            if (standingBefore != null)
            {
                placementsJustEdited = new java.util.HashSet<>();

                for (Map.Entry<String, String[]> was : standingBefore.entrySet())
                {
                    if (was.getValue() == null) placementsJustEdited.add(was.getKey());
                    else standing.put(was.getKey(), was.getValue());
                }
            }

            // And the turns nobody has written down yet: `Layout.reversedOnArrival` lives on the Layout the load
            // replaces; a declined turn is put back and retried at the next idle refresh (OB-189).
            Map<String, String[]> pendingTurns = takeThePendingTurns();

            // Asked, not built, as the carry asks
            final Object runningBefore = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

            try
            {
                // The railway's settings and timetable first, as the Auto tab and a run write them to the railway; only
                // what it changed since it was built, and only where this window knows how it was built.
                if (runningBefore instanceof Layout && this.ui.autonomySession() != null
                    && settingsAsBuiltFor(runningBefore) != null
                    && ((Layout) runningBefore).isValid()
                    && !((Layout) runningBefore).isRunning())
                {
                    try
                    {
                        this.ui.autonomySession().captureTheRailwaysSettings(
                            ((Layout) runningBefore).toJSON(), this.ui.getActiveDiagramConfiguration(),
                            settingsAsBuiltFor(runningBefore));
                    }
                    catch (Exception cannotFold)
                    {
                        // the rebuild goes ahead on the setup's settings
                        this.ui.getModel().log(cannotFold);
                    }
                }

                this.ui.getAutonomyViewerPanel().load(this.ui.getActiveDiagramConfiguration(), false, false);

                // Only over a railway the load replaced: a declined load leaves the old railway while the naming is the
                // edited one, so trains would be put back in those names and recorded where nothing kept them.
                if (this.ui.getModel() != null && this.ui.getModel().getAutoLayoutIfLoaded() != runningBefore)
                {
                    putTheTrainsBack(standing, placementsJustEdited);
                }
                else if (runningBefore != null && this.ui.getActiveDiagramConfiguration() != null)
                {
                    // And the setup is then newer than the railway, so no fold of the older railway writes its homes,
                    // priorities, lengths and placements back over the edit.
                    this.ui.setupNewerThanTheRailway(true);

                    // And the diagram told of the edit all the same: a refused load did not go through
                    // autonomyLoadedFromDiagram, so a page left out came back with its old captions.
                    this.ui.autonomySetupChanged();
                }

                // And a declined edit has now been carried: left up, the guard stopped every fold for the rest of the
                // session (OB-183).  Only when the layout was REPLACED.
                if (this.ui.setupEditDeclinedDuringRun && this.ui.getModel() != null && this.ui.getModel().hasAutoLayout()
                    && this.ui.getModel().getAutoLayout() != runningBefore && this.ui.getModel().getAutoLayout().isValid())
                {
                    this.ui.setupNewerThanTheRailway(false);
                }
            }
            finally
            {
                // In a `finally`: the drain above already emptied the old map, so a load that throws would lose the
                // turns.
                putThePendingTurnsBack(pendingTurns);
            }
        }

        // And both marks follow the edit (MT-278): everything else the editor can change alters which edges exist and
        // so what a standing train covers.  This is the one door every setup change goes through; `refreshCoveredTrack`
        // repaints only the squares whose mark changed.
        this.ui.blockedTrackChanged();
    }
}
