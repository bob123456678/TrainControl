package org.traincontrol.gui;

import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

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
     * Runs a load that folds the running layout, keeping the destination turns the railway has made and not yet written
     * into the setup: a turn is written only while the railway is idle, so during a run every one made since it started
     * is still owed, and the fold writes where each train stands, not which way it has been turned.  Put back on
     * whatever railway the load leaves, as the carry and the rebuild put them back.
     *
     * @param load the load
     */
    void keepThePendingTurnsAcross(Runnable load)
    {
        java.util.Map<String, String[]> pendingTurns = takeThePendingTurns();

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
     * Folds what the running layout knows back into the configuration, and writes it.  A run moves locomotives, and
     * where they ended up lives in the running Layout until a fold writes it into the setup: this one (behind the reset
     * after a diagram edit and the editor doors), the save on the way out, the one `AutonomyViewerPanel.load` makes as
     * it chooses a configuration, and the one an old file's import makes before it writes.  Stopping autonomy does not
     * capture.  Each asks `isSetupNewerThanTheRunningLayout` first.  The capture happens BEFORE a page changes, while
     * the naming and the store still agree, and `renamePage` then rekeys what was captured; refused after a run (run,
     * stop, rename) it would discard every placement the run produced, and as occupancy is derived from placements
     * (`Point.isOccupied` is `currentLoc != null`, `isPathClear` never consults the s88) the rebuilt configuration
     * would put trains where they were before the run, and Start could route one into an occupied block.  Written, not
     * reconciled: see the caller in resetAutonomySession for why that matters on the editor path.
     */
    void captureRunningLayout()
    {
        captureRunningLayout(false);
    }

    /**
     * The same, told whether a railway that holds a path is folded too, each train under way written on the one point
     * it is kept at: Unload's fold, made after its Yes has stopped the trains, as a reload's is.  Every other door here
     * is refused while autonomy runs, or is the reset after one, and keeps the rule that a railway reading busy is not
     * folded.
     *
     * @param underWayToo whether to fold a railway that holds a path
     */
    void captureRunningLayout(boolean underWayToo)
    {
        if (this.ui.autonomySession() == null || this.ui.getActiveDiagramConfiguration() == null
            || this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()
            || !this.ui.getModel().getAutoLayout().isValid()
            || (!underWayToo && this.ui.getModel().getAutoLayout().isRunning()))
        {
            return;
        }

        // While a setup edit waits for a load, what the railway owns and nothing of the setup's: an edit the railway
        // could not take yet is in the configuration and not in the running layout, so folding the whole running layout
        // would write the older layout over it, and folding nothing would lose where the run left the trains and the
        // timetable it captured.  The rebuild or load that replaces the running layout clears the flag.
        try
        {
            // Each train under way on the one point it is kept at; a railway at rest keeps none
            org.traincontrol.automation.Layout folding = this.ui.getModel().getAutoLayout();

            String railway = folding.toJSON(folding.getLastPointsReached());

            // WHILE THE SETUP IS NEWER THAN THE RAILWAY, WHAT THE RAILWAY OWNS ALONE: where the trains stand, the
            // settings and the timetable - not the older railway's homes, lengths and priorities over the edit, and not
            // nothing, which lost where the run left the trains and the timetable it captured
            if (this.ui.setupEditDeclinedDuringRun)
            {
                this.ui.autonomySession().captureWhatTheRailwayOwns(railway, this.ui.getActiveDiagramConfiguration(), settingsAsBuiltFor(folding));
            }
            else this.ui.autonomySession().captureFromLayout(railway, this.ui.getActiveDiagramConfiguration(), settingsAsBuiltFor(folding));

            // Written, not reconciled: this runs because the diagram is being REPLACED, and on the editor path the pages this session
            // holds are the ones the editor mutated in place; Cancel re-reads into new page objects, so reconciling against the mutated
            // ones would delete every setting on every square the user deleted and thought better of.
            this.ui.autonomySession().saveWithoutReconciling();
        }
        catch (Exception e)
        {
            this.ui.getModel().log(e);
        }
    }

    /**
     * Writes these trains' pauses into the railway's settings as built, for a door that changed them on the railway and
     * in the setup together, as `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` writes atomic routes; only for the
     * railway the settings were read off.  **ONLY THE TRAINS THE DOOR CHANGED (OB-343)**: the settings as built are
     * what the railway was built with and the fold takes whatever the railway has that these do not, so writing the
     * railway's whole list as built would hide a pause made by the Auto tab's button (which changes a train on the
     * railway alone), and the rebuild would take the setup's older list as the word on every train.
     *
     * @param changed the trains the door has just changed, on the railway and in the setup
     */
    void notePausesAsBuilt(java.util.Collection<Locomotive> changed)
    {
        Object railway = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

        if (railway == null || railway != this.ui.settingsBuiltFor || this.ui.settingsAsBuilt == null) return;

        // sorted, as the railway writes its list, so the fold's comparison of the two lists reads them as the same
        java.util.Set<String> built = new java.util.TreeSet<>();

        org.json.JSONArray list = this.ui.settingsAsBuilt.optJSONArray(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES);

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

        if (built.isEmpty()) this.ui.settingsAsBuilt.remove(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES);
        else this.ui.settingsAsBuilt.put(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES, new org.json.JSONArray(built));
    }

    /**
     * Reads the railway just built's settings and timetable, for the folds to compare against.  Called by the
     * one door that builds a railway, straight after it.
     */
    void noteTheRailwaysSettingsAsBuilt()
    {
        org.traincontrol.automation.Layout built = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

        this.ui.settingsBuiltFor = built;
        this.ui.settingsAsBuilt = null;

        if (built == null) return;

        try
        {
            this.ui.settingsAsBuilt = org.traincontrol.automationui.AutonomySession.globalsOf(
                new org.json.JSONObject(built.toJSON()));
        }
        catch (Exception cannotRead)
        {
            this.ui.settingsAsBuilt = null;
        }
    }

    /**
     * @param railway a railway
     * @return its settings as it was built, or null where this window does not know them; the folds change it in place to
     *         what they take into the setup (OB-343)
     */
    org.json.JSONObject settingsAsBuiltFor(Object railway)
    {
        return railway != null && railway == this.ui.settingsBuiltFor ? this.ui.settingsAsBuilt : null;
    }

    /**
     * Rebuilds the running layout from what the setup now says (OB-034): what autonomyEditorClosed does at the end,
     * lifted out so an edit made from the DIAGRAM's own menu can ask for it too, as renaming a station there changes
     * what the copies are CALLED and the running layout would hold names the setup no longer knows, the caption going
     * blank.  Not while trains are moving, as rebuilding underneath a running railway is what prepareAutonomyReload
     * exists to refuse.
     */
    void rebuildRunningLayoutFromSetup()
    {
        // No placement edit is pending here, a fact about this door: the callers are `autonomyEditorClosed()` and a discard in
        // the autonomy editor (`LayoutEditor.discardAutonomyWork`), and every placement made in the editor reached the
        // running layout when it was made, so the trains to carry across are the ones a run moved.
        rebuildRunningLayoutFromSetup(false, null);
    }

    /**
     * The road a train came in on, put back on the Point it was put back on; `was` is what `whereTheTrainsAre`
     * recorded.
     */
    private static void putTheRoadBack(org.traincontrol.automation.Layout built,
        org.traincontrol.automation.Point back, String[] was)
    {
        if (was == null || was.length < 3 || was[2] == null) return;

        java.util.List<org.traincontrol.automation.Edge> road = built.roadNamed(was[2]);

        if (road != null) back.setArrivedAlong(road);
    }

    /**
     * The destination turns the railway has made and not yet written into the setup, DRAINED rather than copied as the
     * Layout is about to be thrown away; pairs with `putThePendingTurnsBack` as `whereTheTrainsAre` pairs with
     * `putTheTrainsBack`.
     */
    java.util.Map<String, String[]> takeThePendingTurns()
    {
        if (this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()) return java.util.Collections.emptyMap();

        org.traincontrol.automation.Layout layout = this.ui.getModel().getAutoLayout();

        java.util.Map<String, String[]> out = new java.util.LinkedHashMap<>();

        // WITH THE SQUARE AND FACING OF THE POINT EACH TURN WAS MADE AT, read off this railway before the load
        // replaces it: the turn goes back on the Point the rebuild gives that copy, however it is named then
        for (java.util.Map.Entry<String, String> turn : layout.takeReversalsOnArrival().entrySet())
        {
            org.traincontrol.automation.Point at = turn.getValue() == null ? null : layout.getPoint(turn.getValue());

            out.put(turn.getKey(), new String[] {turn.getValue(), at == null ? null : at.getSquare(),
                at == null ? null : at.getCopyFacing()});
        }

        return out;
    }

    /**
     * Puts those turns onto the rebuilt Layout.  `restoreReversalsOnArrival` puts each back only IF ABSENT (stated on
     * the Layout), so a train that has turned again on the new railway keeps its newer record.
     */
    void putThePendingTurnsBack(java.util.Map<String, String[]> turns)
    {
        if (turns == null || turns.isEmpty()) return;

        if (this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()) return;

        org.traincontrol.automationui.AutonomySession naming = this.ui.autonomySession();

        java.util.Map<String, String> named = new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, String[]> turn : turns.entrySet())
        {
            String[] at = turn.getValue();

            // BY THE NAME THE REBUILD GIVES THE SAME COPY; where it has none, the name recorded, owed as before
            String now = naming == null || at[0] == null ? at[0] : naming.pointNamedNow(at[0], at[1], at[2]);

            named.put(turn.getKey(), now != null ? now : at[0]);
        }

        this.ui.getModel().getAutoLayout().restoreReversalsOnArrival(named);
    }

    /**
     * Puts each train back where it stood before the rebuild (OB-183), on the Point the rebuild gives the copy it stood
     * on: by the name recorded, or where a square was renamed or its copies changed, by the name the session traces it
     * to.  A train whose square is gone is left where the rebuild put it.  The arrival side goes back too, as
     * `Point.setLocomotive` clears it and the tail blocking would switch itself off on every home change.
     * `placementsJustEdited` is the trains the setup was just given a new placement for, or null.
     */
    void putTheTrainsBack(java.util.Map<String, String[]> standing,
        java.util.Set<String> placementsJustEdited)
    {
        if (this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()) return;

        // BY THE NAMES THE REBUILD GIVES THE SAME POINTS, which the session traces
        org.traincontrol.automationui.AutonomySession naming = this.ui.autonomySession();

        java.util.Set<String> notBack = putTheTrainsBack(this.ui.getModel().getAutoLayout(), standing, this.ui.getModel()::log,
            placementsJustEdited, naming == null ? null : naming::pointNamedNow);

        recordTheTrainsNotPutBack(this.ui.getModel().getAutoLayout(), standing, notBack, naming, this.ui.getModel()::log);

        // AND THE DIAGRAM TOLD: it learned which trains stand on no Point inside the load, before this
        if (!notBack.isEmpty() && naming != null && this.ui.diagramMonitorDriver != null)
        {
            this.ui.diagramMonitorDriver.trainsOnNoPointChanged(naming);
        }
    }

    /**
     * A train the rebuild could not put back where the railway had it, taken off wherever the rebuild put it and
     * recorded in the setup (a null `setup` records nothing) where it stands, facing the way it faces: the rebuild put
     * it where the setup last had it (a square a run had moved it off) or nowhere, and the square it stands on read
     * free, so another train could be sent into it and nothing in the setup check said so.  Recorded, the build stands
     * it on no Point where no copy faces its way, and the setup check's error stops every send until it is turned,
     * moved, or the track is put back.  `notBack` is what `putTheTrainsBack` could not put back; `log` is where to say
     * so.
     */
    static void recordTheTrainsNotPutBack(org.traincontrol.automation.Layout built,
        java.util.Map<String, String[]> standing, java.util.Set<String> notBack,
        org.traincontrol.automationui.AutonomySession setup, java.util.function.Consumer<String> log)
    {
        if (built == null || standing == null || notBack == null || notBack.isEmpty()) return;

        for (String train : notBack)
        {
            String[] was = standing.get(train);

            // OFF WHEREVER THE REBUILD PUT IT: not where it stands
            for (org.traincontrol.automation.Point point : new java.util.ArrayList<>(built.getPoints()))
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

            org.traincontrol.automationui.TilePorts.Side facing = null;

            for (org.traincontrol.automationui.TilePorts.Side side : org.traincontrol.automationui.TilePorts.Side.values())
            {
                if (was.length > 4 && side.name().equals(was[4])) facing = side;
            }

            if (facing != null) setup.setFacing(square, facing);

            // AND THE SIDE IT CAME IN BY, AND ITS ROAD: without them the track put back stood it with no tail, and
            // the track its tail lies over was offered to other trains
            if (was.length > 1 && was[1] != null) setup.setArrivedFrom(square, was[1]);

            if (was.length > 2 && was[2] != null)
            {
                try
                {
                    setup.setPointProperty(square, "arrivedAlong", new org.json.JSONArray(was[2]));
                }
                catch (org.json.JSONException notARoad)
                {
                    // a road that does not read is no road: the side alone is kept
                }
            }

            if (log != null) log.accept(I18n.f("autosetup.errorTrainNotPutBack", train, square.toString()));
        }
    }

    /**
     * The same, over a layout handed in, so it can be tested without a window (MT-337): a source-shape guard on the
     * order of `whereTheTrainsAre` and the load cannot say what the two statements do to a train.
     *
     * @param built the layout the rebuild just produced
     * @param standing what `whereTheTrainsAre` recorded before it
     * @param log where to say that one train could not be put back
     */
    static java.util.Set<String> putTheTrainsBack(org.traincontrol.automation.Layout built,
        java.util.Map<String, String[]> standing, java.util.function.Consumer<String> log)
    {
        return putTheTrainsBack(built, standing, log, null);
    }

    /**
     * The same, each train and its road put back on the Point the rebuild gives the copy it stood on:
     * found by `namedNow` from the name, square and facing recorded.
     *
     * @param built the layout the rebuild just produced
     * @param standing what `whereTheTrainsAre` recorded before it
     * @param log where to say that one train could not be put back
     * @param placementsJustEdited locomotives the setup was just given a new placement for, or null
     * @param namedNow where each recorded Point is now, or null to go by the names recorded
     */
    static java.util.Set<String> putTheTrainsBack(org.traincontrol.automation.Layout built,
        java.util.Map<String, String[]> standing, java.util.function.Consumer<String> log,
        java.util.Set<String> placementsJustEdited, TrainControlUI.NamedNow namedNow)
    {
        java.util.Set<String> nowhere = new java.util.LinkedHashSet<>();

        java.util.Set<String> notBack = putTheTrainsBack(built,
            namedNow == null ? standing : namedNow(standing, namedNow, nowhere), log, placementsJustEdited);

        // AND THOSE WHOSE COPY THE REBUILD HAS NOT GOT, unless the setup was just given a newer answer for them
        for (String train : nowhere)
        {
            if (placementsJustEdited == null || !placementsJustEdited.contains(train)) notBack.add(train);
        }

        return notBack;
    }

    /**
     * What `whereTheTrainsAre` recorded in the names the rebuild gives the same Points (each Point and road by the
     * square it copied and its facing); a train whose Point the rebuild lacks is left out and stays where the rebuild
     * put it.
     */
    private static java.util.Map<String, String[]> namedNow(java.util.Map<String, String[]> standing,
        TrainControlUI.NamedNow namedNow, java.util.Set<String> nowhere)
    {
        if (standing == null) return null;

        java.util.Map<String, String[]> out = new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, String[]> was : standing.entrySet())
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

            org.json.JSONObject roadSquares = null;

            try
            {
                roadSquares = now != null && now.length > 5 && now[5] != null ? new org.json.JSONObject(now[5]) : null;
            }
            catch (org.json.JSONException notSquares)
            {
                roadSquares = null;
            }

            if (now != null && now.length > 2 && now[2] != null)
            {
                try
                {
                    org.json.JSONArray road = new org.json.JSONArray(now[2]);

                    for (int i = 0; i < road.length(); i++)
                    {
                        org.json.JSONArray step = road.optJSONArray(i);

                        for (int j = 0; step != null && j < step.length(); j++)
                        {
                            if (step.opt(j) instanceof String)
                            {
                                org.json.JSONArray of = roadSquares == null ? null
                                    : roadSquares.optJSONArray(step.getString(j));

                                String then = namedNow.pointNamedNow(step.getString(j),
                                    of == null || of.isNull(0) ? null : of.optString(0),
                                    of == null || of.isNull(1) ? null : of.optString(1));

                                if (then != null) step.put(j, then);
                            }
                        }
                    }

                    now[2] = road.toString();
                }
                catch (org.json.JSONException notARoad)
                {
                    // left as it is, and `roadNamed` finds no road in it, as before
                }
            }

            out.put(was.getKey(), now);
        }

        return out;
    }

    /**
     * The same, told which trains the setup has just been given a new placement for.  The rebuild has regenerated every
     * placement from the setup, and `standing` is where the running railway had those trains a moment ago.  Where the
     * two disagree one of them is newer, and which does not show in either: the setup is newer where somebody has just
     * placed a train in the editor, which writes `loc` into the setup and nowhere else
     * (`AutonomySession.placeLocomotive`, MT-337); the record is newer where a run moved a train after the last
     * capture, since `currentLoc` is the only place that lives (OB-183).  A rebuild is also asked for by gestures that
     * edit no placement (the diagram's right-click menu is an `AutonomyEditorPanel`, so setting a home ends in one, and
     * nothing on that path captures the running layout), so neither rule can be inferred from the fact that a rebuild
     * is happening.  This decides per TRAIN: a train named in `placementsJustEdited` (or null) keeps where the rebuild
     * put it, and every other goes back where the railway had it.  Returns the trains it could not put back where the
     * railway had them, left where the rebuild put them; `log` is where to say so.
     */
    static java.util.Set<String> putTheTrainsBack(org.traincontrol.automation.Layout built,
        java.util.Map<String, String[]> standing, java.util.function.Consumer<String> log,
        java.util.Set<String> placementsJustEdited)
    {
        java.util.Set<String> notBack = new java.util.LinkedHashSet<>();

        if (standing == null || standing.isEmpty()) return notBack;

        if (built == null) return notBack;

        for (java.util.Map.Entry<String, String[]> was : standing.entrySet())
        {
            try
            {
                org.traincontrol.automation.Point back = built.getPoint(was.getValue()[0]);

                if (back == null)
                {
                    if (placementsJustEdited == null || !placementsJustEdited.contains(was.getKey())) notBack.add(was.getKey());

                    continue;
                }

                // A STATION COPY FACING THE SAME WAY, where the recorded one is barred and the square has one
                // - a turning copy at a square trains may turn at.  The train faces the same way
                // there, and autonomy can start it.
                org.traincontrol.automation.Point twin = built.startableTwinOf(back);

                if (twin != null) back = twin;

                // The railway wins, unless this train's placement is the edit.  Adam's OB-183 ruling is the default: where a
                // train IS is a fact, and where the file thinks it is is a record.  The exception is a train the operator has just placed, whose
                // new square exists only in the setup, and it is supplied by the door that made it rather than inferred.
                if (placementsJustEdited == null || !placementsJustEdited.contains(was.getKey()))
                {
                    if (back.getCurrentLocomotive() == null
                        || !was.getKey().equals(back.getCurrentLocomotive().getName()))
                    {
                        // And only where it went: `moveLocomotive` refuses a square that is not a destination (a station demoted with the train
                        // still on it) and says so in the log, while the side and road below were written onto the square regardless.
                        // Except a barred copy of a station: a train reversed there on the throttle, or turned by the Facing menu, stands on
                        // that copy because it IS its direction; refused, the model kept it where the setup last had it, somewhere it is not.
                        // Onto its own copy even where that is no station now: it stands there
                        if (!built.moveLocomotive(was.getKey(), back.getName(), false, true, true))
                        {
                            notBack.add(was.getKey());

                            continue;
                        }
                    }

                    // The arrival side goes back with the train: `Point.setLocomotive` clears it
                    // whenever the occupant changes, so without this the tail blocking switches itself
                    // off on every rebuild - the half of OB-183 that is not the placement.
                    if (was.getValue()[1] != null) back.setArrivedFrom(was.getValue()[1]);

                    // AND THE ROAD, looked up again on this layout.  After the move above, which clears
                    // both on a change of occupant.
                    putTheRoadBack(built, back, was.getValue());

                    continue;
                }

                // Where the rebuild put it, which is where the SETUP says it is: a rebuild is asked for because the
                // setup just changed, so its answer is the newer and writing this record over it undoes the edit
                // (MT-337; Adam: "placing locomotives via the editor does not seem to work at all - nothing happens. it
                // only works if placing via the track diagram.").  `AutonomySession.placeLocomotive` writes `loc` into
                // the setup while the diagram's door writes the running layout, which `whereTheTrainsAre` reads.
                // Reached only for trains somebody chose to place.
                org.traincontrol.automation.Point now = null;

                for (org.traincontrol.automation.Point each : built.getPoints())
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
                    // Only the arrival side, and only where it did not move: `Point.setLocomotive` clears `arrivedFrom` whenever the occupant
                    // changes, which is wrong for the same train being rebuilt onto the square it was already on (the half of OB-183 that is not
                    // the placement).
                    back.setArrivedFrom(was.getValue()[1]);
                }

                // And `now == null` is an answer too: the Remove item and Clear Every Locomotive write "nowhere" into the setup and nowhere
                // else, so a rebuild that places this train on no square is carrying the edit rather than losing the train.
            }
            catch (Exception cannotPutItBack)
            {
                // ONE TRAIN, NOT THE REBUILD.  A locomotive that cannot be put back is worth saying
                // out loud - it is standing somewhere the diagram no longer has - and it is not a
                // reason to abandon the others.
                if (log != null) log.accept(cannotPutItBack.getMessage());

                notBack.add(was.getKey());
            }
        }

        return notBack;
    }

    /**
     * The same rebuild, saying so when it declines one the operator has not been warned about (`sayIfDeclined`: the
     * caller has not already warned about editing during a run).  The rebuild is coalesced into one per gesture and
     * posted an event later than the write that asked for it, so a run starting inside that event finds this gate shut
     * and the edit is thrown away, correctly, because rebuilding underneath a moving railway is what
     * `prepareAutonomyReload` exists to refuse.  It is NOT deferred to when the run ends: "the setup is the newer of
     * the two" holds only because every caller reaches this BECAUSE the setup just changed, with the running layout
     * captured upstream when the editor opened, and a run invalidates that (it moves locomotives and nothing folds
     * `currentLoc` back), so a deferred rebuild would put each train back where it started (OB-144).  The remedy is in
     * the sentence: stop the run and make the edit again, or let the next ordinary load pick it up off disk.
     * `placementsJustEdited` is the locomotives whose placement this gesture wrote into the setup, so the rebuild's
     * answer for them is the newer one; null where the gesture edited no placement, which is most of them.
     */
    void rebuildRunningLayoutFromSetup(boolean sayIfDeclined,
        java.util.Set<String> placementsJustEdited)
    {
        if (sayIfDeclined && this.ui.getActiveDiagramConfiguration() != null && this.ui.isAutonomyBusy()
            && this.ui.getAutonomyViewerPanel() != null)
        {
            this.ui.getModel().logf("autosetup.log.setupEditNotApplied");
        }

        // A declined edit is recorded so the exit does not delete it: the save on the way out folds the RUNNING layout
        // back over the configuration (`captureFromLayout` writes what the layout has and REMOVES what it does not),
        // and that layout was built before the edit.  Only from the door that carries an edit: the flag rides on
        // `sayIfDeclined`, because `autonomyEditorClosed()` reaches here on EVERY close.  `sayIfDeclined` also means
        // "the operator has not been warned"; the door it excludes does carry edits (closing the track diagram editor)
        // but cannot reach here while autonomy is busy, as no editor opens during a run (OB-047) and
        // `refuseWhileEditorOpen()` guards every door that starts one.  If that guard is ever relaxed, this flag needs
        // its own parameter.
        if (sayIfDeclined && this.ui.getActiveDiagramConfiguration() != null && this.ui.isAutonomyBusy()
            && this.ui.getAutonomyViewerPanel() != null)
        {
            this.ui.setupNewerThanTheRailway(true);
        }

        if (this.ui.getActiveDiagramConfiguration() != null && !this.ui.isAutonomyBusy()
            && this.ui.getAutonomyViewerPanel() != null)
        {
            // Without capturing the running layout's state first: every caller of this shape reaches it BECAUSE the
            // setup just changed, so an ordinary load, which folds the running layout's placements, homes and facings
            // back into the configuration, would write the stale answer over the edit that asked for the rebuild.
            // "Newer of the two" holds only because of an ORDER upstream: `openLayoutEditor` captures the running
            // layout before it constructs the editor, so by the time an edit exists the setup knows where the trains
            // are (this method cannot check that).  Where the trains ARE survives the rebuild (OB-183), so the
            // placements are carried across: PLACEMENTS ONLY, as folding the entire running layout back writes the
            // stale answer over the edit.  The one exception is named, not inferred: `placementsJustEdited` names the
            // trains this gesture wrote a placement for (MT-337), and `putTheTrainsBack` makes the exception for
            // exactly those.
            java.util.Map<String, String[]> standing = this.ui.whereTheTrainsAre();

            // And the turns nobody has managed to write down yet: `Layout.reversedOnArrival` holds the reversals the railway made at
            // a destination and has not yet drained into the setup, and lives on the Layout, which the load below replaces wholesale - so
            // every pending turn died here, silently, on any setup gesture.  That is the retry case this preserves (a turn that DECLINED
            // to write once is put back and tried again at the next idle refresh), and OB-189 through a third door.  Carried the way the
            // placements are: what the railway did is a fact, and the file is a record of it.
            java.util.Map<String, String[]> pendingTurns = takeThePendingTurns();

            // Asked, not built, as the carry asks
            final Object runningBefore = this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded();

            try
            {
                // THE RAILWAY'S SETTINGS AND TIMETABLE FIRST: the Auto tab and a run write them to the railway, and
                // this rebuilds from the setup.  Not where the trains stand: the put-back below carries those.  ONLY
                // WHAT THE RAILWAY CHANGED SINCE IT WAS BUILT, and only where this window knows how it was built, as a
                // door can have edited the setup's own settings before asking for this rebuild.
                if (runningBefore instanceof org.traincontrol.automation.Layout && this.ui.autonomySession() != null
                    && settingsAsBuiltFor(runningBefore) != null
                    && ((org.traincontrol.automation.Layout) runningBefore).isValid()
                    && !((org.traincontrol.automation.Layout) runningBefore).isRunning())
                {
                    try
                    {
                        this.ui.autonomySession().captureTheRailwaysSettings(
                            ((org.traincontrol.automation.Layout) runningBefore).toJSON(), this.ui.getActiveDiagramConfiguration(),
                            settingsAsBuiltFor(runningBefore));
                    }
                    catch (Exception cannotFold)
                    {
                        // the rebuild goes ahead on the setup's settings, as it did before this fold existed
                        this.ui.getModel().log(cannotFold);
                    }
                }

                this.ui.getAutonomyViewerPanel().load(this.ui.getActiveDiagramConfiguration(), false, false);

                // ONLY OVER A RAILWAY THE LOAD REPLACED: a load declined for a problem that stops the build -
                // a link unpaired, say - leaves the railway as it was, with its trains where they stand, while the setup's
                // naming is the edited one; put back in those names, every train whose copy the edit renamed was taken off
                // it and recorded where nothing then kept it
                if (this.ui.getModel() != null && this.ui.getModel().getAutoLayoutIfLoaded() != runningBefore)
                {
                    putTheTrainsBack(standing, placementsJustEdited);
                }
                else if (runningBefore != null && this.ui.getActiveDiagramConfiguration() != null)
                {
                    // AND THE SETUP IS THEN NEWER THAN THE RAILWAY, as after a rebuild a run declined: every
                    // fold of the railway built before the edit - opening or closing the editor, Export, New
                    // Configuration, the exit - wrote its homes, priorities, lengths and placements back over the edit
                    this.ui.setupNewerThanTheRailway(true);

                    // AND THE DIAGRAM TOLD OF THE EDIT ALL THE SAME, as the Autonomy menu's door tells it: a load
                    // that replaces the railway does so through autonomyLoadedFromDiagram, and one the build refuses did
                    // not, so a page left out, say, came back from the page list with the captions it had before
                    this.ui.autonomySetupChanged();
                }

                // And a declined edit has now been carried: the guard exists because the running layout was built BEFORE the edit, and a
                // rebuild that replaced it was built from the setup, edit included, with the trains put back where they stand - so the running
                // layout is the newer of the two again.  Left up, the guard stopped every fold for the rest of the session (OB-183 kept alive by
                // the guard).  Only when the layout was REPLACED: `load` declines without rebuilding, and a declined load has carried nothing.
                if (this.ui.setupEditDeclinedDuringRun && this.ui.getModel() != null && this.ui.getModel().hasAutoLayout()
                    && this.ui.getModel().getAutoLayout() != runningBefore && this.ui.getModel().getAutoLayout().isValid())
                {
                    this.ui.setupNewerThanTheRailway(false);
                }
            }
            finally
            {
                // IN A `finally`, because the drain above has already emptied the old map.  A load that
                // throws part way through would otherwise lose the turns as surely as the rebuild did -
                // which is the shape of the defect being fixed, arriving by the failure path.
                putThePendingTurnsBack(pendingTurns);
            }
        }

        // And both marks follow the edit (MT-278 item 3; Adam: "Make sure the shading and orange repaints on any autonomy or
        // train/track length edit."): a placement reached the marks through `updateVisiblePoints`, a train length through
        // `applyTrainLength`, a tile length through this rebuild, but everything ELSE the editor can change (a station flag, an arrival
        // side, a one-way direction, a reversal marking) changes which edges exist and so what a standing train covers.  This is the one
        // door every setup change goes through; `refreshCoveredTrack` diffs the two sets and repaints only the squares whose mark changed.
        this.ui.blockedTrackChanged();
    }
}
