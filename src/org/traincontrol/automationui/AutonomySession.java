package org.traincontrol.automationui;

import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.Landing;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.util.I18n;

/**
 * One layout's autonomy setup, from the files on disk to the graph a train can run on.
 *
 * The whole chain in one place - store, tile graph, reduction, generated configuration - so that the
 * panels showing it can be about showing it.  Every edit goes through here, and every edit re-derives,
 * because the alternative is a screen that agrees with itself and disagrees with the railway.
 *
 * Headless on purpose: nothing here draws anything, which is what lets the behaviour be tested without
 * a screen and lets both the editor and the viewer work from the same object.
 *
 * @author Adam
 */
public class AutonomySession
{
    private final AutonomyCompanionStore store;

    private List<LayoutDiagram> pages = new ArrayList<>();
    private TileGraph graph;
    private GraphReducer reducer;

    private boolean dirty = false;

    public AutonomySession(File layoutFolder)
    {
        this.store = new AutonomyCompanionStore(layoutFolder);
    }

    public AutonomyCompanionStore getStore()
    {
        return store;
    }

    /**
     * Whether this layout can hold a setup at all - autonomy is local-layout only, because its files
     * live beside the diagram.
     * @return
     */
    public boolean isUsable()
    {
        return store.isUsable();
    }

    /**
     * Whether anybody has set autonomy up on this layout yet.
     * @return
     */
    public boolean exists()
    {
        return store.exists();
    }

    /**
     * Whether there are unsaved edits.  The editor saves on close, so this is what decides whether
     * closing needs to ask.
     * @return
     */
    public boolean isDirty()
    {
        return dirty;
    }

    /**
     * Whether a page has been renamed since these page objects were handed over.
     *
     * A rename rekeys the STORE and writes a new diagram file. It does not rename the LayoutDiagram
     * objects this session is holding, and it cannot: `saveChanges` writes a file, and the graph and
     * the reducer were derived from the old names when `open` ran. So between a rename and the next
     * `open`, this session's naming and its store disagree - the store says "1 - Main2", everything
     * derived from the pages still says "1 - Main".
     *
     * Nothing reads the naming in that window except `captureFromLayout`, which is why this exists
     * rather than a general repair: see the refusal there for what it cost.
     */
    private boolean pagesStale = false;

    /**
     * Says that a page has been renamed and these page objects no longer describe the setup.
     *
     * Called by the rename itself. Cleared by the next `open`, which is what makes it true again.
     */
    public void markPagesStale()
    {
        this.pagesStale = true;
    }

    /**
     * Reads the setup for these pages and derives everything from it.
     *
     * @param diagrams every page of the layout
     * @throws IOException if the setup exists but cannot be read
     */
    public void open(List<LayoutDiagram> diagrams) throws IOException
    {
        // These pages are current by definition: they are the ones just read from disk.
        this.pagesStale = false;

        this.pages = diagrams == null ? new ArrayList<LayoutDiagram>() : new ArrayList<>(diagrams);

        Map<String, String> pageIds = new LinkedHashMap<>();

        for (LayoutDiagram page : pages)
        {
            // A placeholder's id is NOT offered to the store (FV3-A1).  `pageIsHere` answers from this
            // map, and answering yes about a page whose contents did not load is what releases the
            // entries OB-067 holds out of memory to be written back verbatim.  The placeholder keeps
            // its id for the index writer, which is a different question - that one is "which page is
            // this", and this one is "is its track here to judge".
            if (page.isUnreadable()) continue;

            if (page.getPageId() != null) pageIds.put(page.getName(), page.getPageId());
        }

        store.setPageIds(pageIds);
        store.load();

        rebuild();

        // A ROUTE TILE'S LENGTH GOES TO THE TRACK BESIDE IT, once, as the setup is read (Adam, 2026-09-23).
        foldedRouteTiles = foldRouteTileLengths();

        if (!foldedRouteTiles.isEmpty()) rebuild();

        // Captions used to live in the layout file.  Anything still written there is brought across now,
        // once; see migrateStationLabels.  Its failures are kept for the UI to report rather than thrown,
        // because a page that could not be rewritten is not a reason to refuse to open the setup - the
        // migration simply runs again next time.
        migratedPages.clear();
        migratedCaptions = 0;

        migrationFailures = migrateStationLabels();

        forgetCaptionsOfNonStations();

        dirty = false;
    }

    /** The route tiles whose length was folded as this setup was opened, each to the square that took it. */
    private Map<TileKey, TileKey> foldedRouteTiles = new LinkedHashMap<>();

    /**
     * The route tiles whose length was folded into the track beside them when this setup was opened.
     *
     * @return route tile to the square that took its length, empty when there were none
     */
    public Map<TileKey, TileKey> getFoldedRouteTiles()
    {
        return java.util.Collections.unmodifiableMap(foldedRouteTiles);
    }

    /**
     * Moves any length a route tile holds onto the track beside it, keeping the total (Adam, 2026-09-23).
     *
     * A route tile takes no length (OB-273) - *"it just implicitly connects things as if it were a crossing"* - so
     * nothing asks for one: Mass Assign Lengths passes over it and the notices skip it.  But the room and tail sums still
     * add whatever it holds (`GraphReducer.sumLength` and `lengthOf` read every step, AUT-C4), so a unit left there is
     * counted without being anywhere the editor shows or asks about.  Five of Adam's held a length of 1 from before that
     * ruling, most likely written by a Mass Assign Lengths run that shared a piece's length over them.  Asked whether to
     * drop them or move them: *"Fold them, they were likely auto set during the mass assignment run."*  So each goes to
     * the square beside the route tile along the road it carries - plain track in preference to a switch, whose length is
     * the page's one turnout length - and the sums come out as they were.
     *
     * **Never onto a sensor square** (AUT-C4).  A sensor's length is the last place of every rail arriving at it and the
     * first thing a train standing there spends, so a unit put there lengthens every other approach to that sensor too.
     *
     * Run as the setup is opened, so it applies to any layout carrying one, and to one written again by hand.  A route
     * tile with no plain track or switch beside it keeps its length, which the sums still count.
     *
     * @return route tile to the square that took its length
     */
    public Map<TileKey, TileKey> foldRouteTileLengths()
    {
        Map<TileKey, TileKey> moved = new LinkedHashMap<>();

        if (graph == null) return moved;

        for (Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> tile
            : new LinkedHashMap<>(graph.getTiles()).entrySet())
        {
            if (tile.getValue() == null || !TilePorts.takesNoLength(tile.getValue().getType())) continue;

            int length = store.getTileLength(tile.getKey());

            if (length <= 0) continue;

            TileKey into = null;

            for (TileKey beside : graph.trackBesideARouteTile(tile.getKey()))
            {
                org.traincontrol.base.LayoutDiagramComponent track = graph.getTiles().get(beside);

                // NEVER A SENSOR (AUT-C4): its length is every approach's last place, not a share of this piece.
                if (track == null || track.isFeedback()) continue;

                if (into == null) into = beside;

                // PLAIN TRACK FIRST: a switch's length is the page's one turnout length (MAL-B1), not a share of a piece.
                if (!track.isSwitch())
                {
                    into = beside;

                    break;
                }
            }

            if (into == null) continue;

            store.setTileLength(into, Math.max(0, store.getTileLength(into)) + length);
            store.setTileLength(tile.getKey(), 0);

            moved.put(tile.getKey(), into);
        }

        return moved;
    }

    /**
     * Every caption on one page, as a snapshot something else can hold and hand back.
     *
     * **Not the editor's undo, which is what this used to say** (`VD9-C16`). That is
     * `snapshotPage`/`restorePage`, through the companion store; this method and its partner were a
     * second, parallel implementation of the same idea, and the partner - `restoreCaptionsOnPage` -
     * was deleted as uncalled by `REL-C15`.
     *
     * What is left has one caller: `LayoutEditor.forgetCaptionsOutsideThePage`, the `RC-B1` cleanup
     * that runs when a page is shrunk and drops the captions now off the edge. It wants the keys and
     * not the round trip, which is why only half of the pair survived.
     *
     * By page, because that is what the caller is about.
     *
     * @param page the page name
     * @return caption square to station square, for that page only
     */
    public Map<TileKey, TileKey> captionsOnPage(String page)
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        if (page == null) return out;

        for (Map.Entry<TileKey, TileKey> caption : store.getCaptions().entrySet())
        {
            if (page.equals(caption.getKey().getPage())) out.put(caption.getKey(), caption.getValue());
        }

        return out;
    }

    /**
     * Drops arrival restrictions naming sides the square no longer has.
     *
     * getBarredArrivals hides them from every reader, so this is only about the file: left in it, a
     * dead side comes back the day the diagram is edited back into a shape that has it, as a rule
     * nobody remembers writing.  Cleaned at save, which is when the diagram and the setup are being
     * reconciled anyway.
     */
    private void forgetArrivalsThatNoLongerExist()
    {
        for (Map.Entry<TileKey, Set<Side>> entry
            : new LinkedHashMap<>(store.getBarredArrivals()).entrySet())
        {
            // Only squares this setup can still SEE.
            //
            // The index is derived from the graph, and the graph leaves out excluded pages - so a
            // square on an excluded page has no arrival sides at all, and pruning against that would
            // read as "every side of this station has gone" and delete the restriction outright.
            // Excluding a page has to be reversible, which is the same rule the reconciliation below
            // this call exists to keep, and this would have broken it for one field only.
            //
            // The same shape catches a station fed through a link, which splits into nothing.
            if (!getStationIndex().squares().contains(entry.getKey())) continue;

            Set<Side> live = new LinkedHashSet<>(entry.getValue());

            live.retainAll(arrivalSides(entry.getKey()));

            if (live.size() != entry.getValue().size())
            {
                store.setBarredArrivals(entry.getKey(), live);
            }
        }
    }

    /**
     * Drops name plaques belonging to squares that are no longer stations.
     *
     * The rule is enforced at the setter now, but setups written before it exists carry captions for
     * squares that were demoted long ago - and one of them is what made a reversing point announce
     * itself as a station the moment a train touched it.  Cleared at open, once, so nobody has to find
     * and delete them by hand.
     *
     * Silent: there is nothing here a user could act on, and the plaque comes back the moment the
     * square is made a station again.
     */
    private void forgetCaptionsOfNonStations()
    {
        for (Map.Entry<TileKey, TileKey> caption
            : new LinkedHashMap<>(store.getCaptions()).entrySet())
        {
            if (caption.getValue() == null) continue;

            if (!store.isStation(caption.getValue())) store.setCaption(caption.getKey(), null);
        }
    }

    /**
     * The pages the caption migration could not rewrite, for whoever opened the session to report.
     */
    private List<String> migrationFailures = new ArrayList<>();

    public List<String> getMigrationFailures()
    {
        return Collections.unmodifiableList(migrationFailures);
    }

    /**
     * The pages the caption migration DID rewrite, for the same reader (RGN-B1).
     *
     * The migration edits files the user owns - a `Point:` label typed onto their own track diagram is
     * taken into the setup and emptied out of the `.cs2` - and until this it did so without a word.  A
     * user who upgraded, then went back to 2.7.4c, found their station captions gone from the diagram
     * and nothing anywhere saying why.  `LayoutDiagram.saveChanges` keeps a `.cs2.bak` the first time
     * it rewrites a page, so the names are recoverable; that is only useful to somebody who knows to
     * look.
     *
     * Failures were already reported and successes were not, which is the wrong way round for a change
     * that is one-way: a failure means it will simply run again next time.
     */
    private List<String> migratedPages = new ArrayList<>();

    public List<String> getMigratedPages()
    {
        return Collections.unmodifiableList(migratedPages);
    }

    /** How many captions the migration took over, across every page. */
    private int migratedCaptions;

    public int getMigratedCaptions()
    {
        return migratedCaptions;
    }

    /**
     * Throws away every edit made since the last save, by reading the setup back off disk.
     *
     * Without this, "exit without saving" was a promise nothing kept.  Every edit goes straight into the
     * live configuration this session hands out - there was no copy to go back to - so the discarded
     * work was still in memory afterwards, still drawn on the diagram, and written out by the next save
     * from anywhere at all: ticking a page, loading a configuration, or closing the application.
     *
     * Re-reading rather than undoing: what is on disk is by definition the last state the user agreed
     * to, and rebuilding from it cannot leave a half-reverted graph the way replaying edits backwards
     * could.  Anything already saved deliberately survives, which is what makes the Save button in the
     * editor mean something.
     *
     * Station captions ARE taken back, along with everything else.  They used to live on the track
     * diagram and be written to the layout file the moment they were set, which made them the one
     * thing this could not undo; they have been part of the setup since captions stopped being text
     * labels, so they are re-read with the rest of it and the question asked of the user no longer
     * has to make an exception of them.
     *
     * @throws IOException if the setup cannot be re-read, in which case nothing is changed
     */
    public void discardEdits() throws IOException
    {
        store.load();

        rebuild();

        dirty = false;
    }

    /**
     * Creates a setup for a layout that has none, with one configuration to put things in.
     *
     * @param configurationName what to call the first configuration
     * @throws IOException
     */
    public void initialize(String configurationName) throws IOException
    {
        // Always creates one.  This used to do nothing at all unless the store was empty, which was
        // right while it was the "set autonomy up for the first time" button and wrong the moment it
        // became "add a configuration": the second one silently did nothing, and the menu came back
        // unchanged with the old configuration still running.
        //
        // A name already in use throws, as it does for a duplicate - the caller says so.
        store.createConfiguration(
            configurationName == null || configurationName.trim().isEmpty()
                ? "Default" : configurationName.trim(),
            null);

        rebuild();
        save();
    }

    /**
     * Re-derives everything from the diagram and the stored decisions.
     *
     * Called after every edit rather than on demand.  A derivation that lagged behind an edit would show
     * the user a graph that was true a moment ago, which is worse than showing none: they would be
     * checking their work against the wrong answer.
     */
    public final void rebuild()
    {
        // The index is NOT nulled here.  Nulling opened a window - the whole expensive rebuild body
        // below - in which a feedback-thread reader (updateStationLabels) could see null and derive the
        // index itself, against the reducer this method is at that moment replacing.  That is the
        // unsynchronised derivation SA-C1 set out to remove, reopened across the rebuild instead of a
        // single field write.
        //
        // Left alone, a reader mid-rebuild sees the OLD index - immutable, built from the old reducer,
        // and safe to read - until the new one is published in one assignment at the end.
        graph = new TileGraph(pages, store.getExcludedPages());

        store.applyTo(graph);

        graph.validatePortals();

        reducer = new GraphReducer(graph, store.asAuthored());
        reducer.reduce();

        // Now, on this thread, rather than on whichever thread happens to ask first
        deriveStationIndex();
    }

    public TileGraph getGraph()
    {
        return graph;
    }

    /**
     * What came back from a legacy autonomy.json, so the caller can say what happened.
     */
    public static class LegacyImport
    {

        /**
         * How many placements had a facing chosen for them because the file did not state one (ACC-C4).
         *
         * The file states one where every edge from the train's point leaves the square by one side (REG4-A1).
         * Elsewhere a split square still needs one, so the import picks a way trains may arrive in.  It is a guess,
         * corrected in the autonomy editor and by the first real run's capture, and before ACC-C4 it was made silently
         * AND at random.
         */
        public int facingsInvented;

        /**
         * The points whose trains the file ran a way no copy of the square holds on this diagram (REG4-A1).
         *
         * The old file's directions are carried onto the diagram first (`carryTheOldDirections`), but never over one the
         * operator set, so a square can still have one copy, facing the way the train does NOT drive.  No facing the square cannot hold is
         * saved (Adam, OB-270), so the train is stood the only way the square allows - and named here, because unlike
         * a guess it is known to be the other way round.
         */
        public final List<String> facingsNotHeld = new ArrayList<>();

        /**
         * The track the old file's one-way edges set running their way, where the diagram had nothing set by the
         * operator (Adam, 2026-09-24: *"Carry the old file's directions onto the diagram - yes, to the extent
         * possible."*), against the way each now runs.
         */
        public final Map<TileGraph.DirectionKey, Direction> directionsCarried = new LinkedHashMap<>();

        /**
         * The pieces of track the old file ran one way and this diagram does not, left as the diagram has them because
         * its directions have already been set (2026-09-24).
         */
        public int directionsNotCarried = 0;
        /**
         * Names written onto a square that had none.
         */
        public int matched = 0;

        /**
         * Names left alone because the square already had one.
         */
        public int skipped = 0;

        /**
         * Locomotives put back where the old graph had them.
         */
        public int placed = 0;

        /**
         * Squares marked as turning trains round, from a terminus or a reversing point.
         */
        public int reversing = 0;

        /**
         * Priorities, speed multipliers, exclusions and switches carried over.
         */
        public int settings = 0;

        /**
         * Homes cleared because the locomotive already had one.
         *
         * A pre-rule file can name two homes for one locomotive; only one can be kept, and which one
         * is a choice the importer makes rather than the user. Counted so it can be said out loud.
         */
        public int duplicateHomes = 0;

        /**
         * Names whose sensor is not on this diagram, in the order the file gave them.
         */
        public final List<String> unmatched = new ArrayList<>();

        /**
         * Locomotives the file places that this database has never heard of.
         *
         * Not placed.  The running model refuses a placement naming a locomotive it cannot find, and
         * refuses it by invalidating the WHOLE layout - so writing these in would have produced a
         * setup that will not load, reported as a locomotive problem with nothing to connect it to
         * the import that caused it.
         */
        public final List<String> unknownLocomotives = new ArrayList<>();

        /**
         * Locomotives the file places at more than one point.  Only the first is placed.
         *
         * The running model invalidates on this too - one locomotive cannot stand in two places - and
         * an old graph that has drifted can easily name the same one twice.
         */
        public final List<String> duplicateLocomotives = new ArrayList<>();
    }

    /**
     * The active configuration's run-wide settings object, created if it is not there yet.
     *
     * The sibling of `configurationExtras`, which does the same for one square. Both answer null when
     * no configuration is active, because there is then nowhere for the setting to live.
     *
     * @return the globals object, or null when nothing is active
     */
    private org.json.JSONObject configurationGlobals()
    {
        String active = store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null) return null;

        if (!configuration.has("globals")) configuration.put("globals", new org.json.JSONObject());

        return configuration.getJSONObject("globals");
    }

    /**
     * The active configuration's own data for one square, created if this is the first thing on it.
     *
     * A placement is not a decision about the track - it is where a train happens to be standing - so
     * it belongs to a configuration and not to the shared half.  Keyed by tile, the way the rest of
     * the configuration is, so a Point renamed later keeps whatever is standing on it.
     *
     * @param tile
     * @return null when no configuration is loaded to put anything in
     */
    private org.json.JSONObject configurationExtras(TileKey tile)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null) return null;

        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        org.json.JSONObject points = configuration.getJSONObject("points");

        if (!points.has(tile.toString())) points.put(tile.toString(), new org.json.JSONObject());

        return points.getJSONObject(tile.toString());
    }

    /**
     * Brings station names, station flags and lengths across from a legacy autonomy.json.
     *
     * The graph this replaces held its points by NAME and carried the s88 each one watched.  The
     * diagram derives its points from the feedback squares themselves, so the two can be matched by
     * that s88 - it is the one thing both models agree on, and it is what makes a square a point in
     * the first place.
     *
     * This exists because the names were never derivable.  A diagram gives the track's shape; what any
     * of it is CALLED, and which squares count as stations, are decisions somebody made once and would
     * otherwise have to make again, square by square, on upgrading.
     *
     * Names already here are kept, the same rule importing a configuration follows: this fills gaps,
     * it does not overwrite somebody's work with a file's.
     *
     * Nothing is written to disk BY THIS METHOD; the only caller saves immediately afterwards
     * (`AutonomyViewerPanel.importLegacyGraph`, which calls `save()` on the next line), so in practice an import is committed as soon as it is
     * asked for.  This used to say a bad match "can still be cancelled", which described a step
     * that has never existed and would have led the next reader to add it back (ACC-C5).
     *
     * @param legacy the parsed autonomy.json
     * @return what was matched, skipped and not found
     */
    /**
     * The per-point settings an old graph holds that the build still reads, unchanged.
     *
     * Everything here is copied into the configuration verbatim and emitted verbatim: the builder
     * passes unknown extras straight through, so these need translating no more than the placement
     * did.  Listed rather than copied wholesale because the rest of a legacy point - name, station,
     * s88, terminus, reversing, x, y - is either handled deliberately above or derived now, and
     * copying those would fight the derivation.
     *
     *   priority          how strongly autonomy prefers this destination
     *   speedMultiplier   the pace trains take through it
     *   excludedLocs      the locomotives this station will not accept
     *   active            whether the railway may use the square at all.  Carried as given, except on a
     *                     station, where `false` arrives as "on, and not a destination autonomy chooses"
     *                     (REG-B1, below).
     *
     * **The build stopped ignoring `active` on a non-station on 2026-09-02** (`D24-B5`), and this line
     * used to give that as the reason for carrying it unfiltered (`DY3-C2`).  The reason now is the
     * plainer one: it is the operator's setting and the import is a migration, not an edit.
     *
     * What that changes for an imported graph is worth knowing.  Adam's own frozen legacy file carries
     * twenty-four points with `active: false`, eighteen of them stations, and each of those is now a
     * point no path may pass through OR END ON - manual routes included, because neither
     * `isPathClear`'s intermediate rule nor, since Adam's ruling of 2026-09-06, its destination rule is
     * fenced behind `isAutoRunning`.  For the six that are not stations that is a RESTORATION: at v2.8.1
     * the raw `autonomy.json` went straight into `parseAuto` and those points blocked paths then too.
     * For the eighteen stations it is not (REG-B1): v2.8.1 let a hand-picked route END on a switched-off
     * station, and sixteen of them are reversing stations the import already marks parking, where
     * `active: false` was the old way of saying "autonomy stays out, I drive in by hand".  After the
     * import nobody drives in; switching the station back on is the way past.  **Adam's ruling, 2026-09-24:** *"yes,
     * translate as on but not auto destination."*  So on a station `active: false` is written as
     * `autoDestination: false` and the switch is left on - what it meant at v2.8.1.  On any other point it is
     * carried as given, which is what the six non-stations had at v2.8.1 too.
     */
    private static final List<String> CARRIED_SETTINGS =
        Arrays.asList("priority", "speedMultiplier", "excludedLocs", "active", "maxTrainLength");

    public LegacyImport importLegacy(org.json.JSONObject legacy)
    {
        return importLegacy(legacy, null);
    }

    /**
     * Which way each imported train faces: the way the file ran it, and a guess only where the file cannot say.
     *
     * READ FROM THE FILE FIRST (REG4-A1).  The old graph had one Point per sensor and no facing field, and this used to
     * say that the file therefore cannot state a facing.  It can, for almost every point: 2.8.1 sent a train only along
     * the edges that start at its point, and never commanded a direction except to turn round, so where every one of
     * them leaves the square by one side, the train drives out that way.  Eighty-eight of the ninety edges in Adam's
     * frozen file have no reverse twin.  Guessing instead stood a train facing south at TopMainR1, whose one edge leads
     * north - a path locked south for a train whose decoder drives it north.
     *
     * AFTER THE LOOP (REG4-C1).  A legacy terminus or reversing point is marked "must turn round" further down the same
     * point's import, and a square trains must turn at has only turning copies, which face the other way from its
     * plain ones.  Asked inside the loop, a facing was checked against copies the build would no longer make.
     *
     * THE GUESS, where the file cannot say - no edge, edges both ways, or an end this diagram does not draw.  A way
     * trains may arrive in, first (REG3-C1): the build honours a recorded facing even where only a copy trains may not
     * arrive at holds it, since the copy IS the direction, so a guess of such a facing stood the imported train where
     * autonomy will not start it.  The first copy's only where the square has none.  Deterministic rather than random
     * (ACC-C4), so two imports of one file agree; counted, so the log can say how many were guessed.
     *
     * A facing the file states and no copy holds - the diagram does not let a train arrive the way it would have to -
     * is not saved (Adam, OB-270: *"we shouldn't allow an impossible facing to be saved"*): the train is stood the way
     * the square allows, and named in `facingsNotHeld` rather than counted as a guess, since it is known to be the
     * other way round from how the train drives.
     *
     * @param facingToFind the placed trains with no facing yet, by square and legacy point name
     * @param edgesLeadTo each legacy point's edges, by the square each ends on (null: not on this diagram)
     * @param pointSquares the squares the old graph had points on
     * @param result where the guesses are counted
     */
    private void findTheImportedFacings(Map<TileKey, String> facingToFind, Map<String, List<TileKey>> edgesLeadTo,
        Set<TileKey> pointSquares, LegacyImport result)
    {
        for (Map.Entry<TileKey, String> each : facingToFind.entrySet())
        {
            TileKey tile = each.getKey();

            java.util.List<Side> ways = new ArrayList<>(facingsFor(tile).values());

            Side ran = sideTheEdgesLeaveBy(tile, edgesLeadTo.get(each.getValue()), pointSquares);

            if (ran != null && ways.contains(ran))
            {
                setFacing(tile, ran);

                continue;
            }

            java.util.Set<Side> mayArrive = homeFacingsFor(tile);

            Side guess = null;

            for (Side way : ways)
            {
                if (mayArrive.contains(way))
                {
                    guess = way;

                    break;
                }
            }

            if (guess == null && !ways.isEmpty()) guess = ways.get(0);

            if (guess != null)
            {
                setFacing(tile, guess);

                if (ran != null) result.facingsNotHeld.add(each.getValue());
                else result.facingsInvented++;
            }
        }
    }

    /**
     * Sets the track each of the old file's edges ran over running the way it ran (Adam, 2026-09-24: *"Carry the old
     * file's directions onto the diagram - yes, to the extent possible."*).
     *
     * A 2.8.1 graph is one-way edges: a train on a point went only along the edges starting there.  The diagram has its
     * own defaults - plain track both ways, a switch out of its toe only - which on a fresh upgrade can forbid the very
     * way the old graph ran a train (TopMainR1Inter, reached from the north through a switch, became reachable from the
     * east only).  So each edge is followed as its facing is (`sideTheEdgesLeaveBy`: the side from which its end is the
     * next square the old graph had a point on) and the track between is set as the editor's one-way run sets it.
     *
     * To the extent possible:
     *  - track the file ran both ways is left both ways, and so is a square two edges run over opposite ways;
     *  - an edge whose end is next by more than one side, or none, or across a link, is skipped for what it cannot say;
     *  - and ONLY ONTO A DIAGRAM NOBODY HAS SET A DIRECTION ON.  A default is stored as nothing, so filling the gaps of a
     *    tuned diagram cannot tell track left running both ways on purpose from track nobody has looked at - on Adam's
     *    own railway (118 set) it made 176 pieces one-way, 170 of them plain track he runs both ways, in the setup every
     *    configuration shares.  There the pieces are only counted, for the log.
     * The facings are found afterwards, over the copies these directions make.
     *
     * @param oldEdges each edge as the squares of its two points (null for one this diagram does not draw)
     * @param pointSquares the squares the old graph had points on
     * @param result where the directions set are recorded
     */
    private void carryTheOldDirections(List<TileKey[]> oldEdges, Set<TileKey> pointSquares, LegacyImport result)
    {
        if (graph == null) return;

        Map<TileGraph.DirectionKey, Direction> wanted = new LinkedHashMap<>();

        for (TileKey[] edge : oldEdges)
        {
            TileKey from = edge[0];
            TileKey to = edge[1];

            if (from == null || to == null || from.equals(to)) continue;

            Side leaving = null;
            boolean both = false;

            for (Side side : Side.values())
            {
                if (!graph.firstStopsLeaving(from, side, pointSquares).contains(to)) continue;

                if (leaving != null) both = true;

                leaving = side;
            }

            if (leaving == null || both) continue;

            List<TileKey> path = graph.pathLeaving(from, leaving, to, pointSquares);

            if (path == null) continue;

            // As `applyOneWay` does, between the two ends only.
            for (int i = 1; i < path.size() - 1; i++)
            {
                TileKey tile = path.get(i);

                Side cameFrom = graph.sideTowardNeighbour(tile, path.get(i - 1));
                Side goingTo = graph.sideTowardNeighbour(tile, path.get(i + 1));

                if (cameFrom == null || goingTo == null) continue;

                for (Map.Entry<RouteId, Route> entry : graph.getRoutes(tile).entrySet())
                {
                    Route route = entry.getValue();

                    if (!route.touches(cameFrom) || !route.touches(goingTo)) continue;

                    TileGraph.DirectionKey key = new TileGraph.DirectionKey(tile, entry.getKey());

                    Direction way = route.getA() == goingTo ? Direction.TOWARD_A : Direction.TOWARD_B;

                    Direction had = wanted.get(key);

                    wanted.put(key, had == null || had == way ? way : Direction.BOTH);
                }
            }
        }

        // A DIAGRAM WHOSE DIRECTIONS HAVE BEEN SET is left as it is, and what the file ran differently is counted.
        boolean tuned = store.hasTileDirections();

        for (Map.Entry<TileGraph.DirectionKey, Direction> each : wanted.entrySet())
        {
            TileKey tile = each.getKey().square();
            RouteId id = each.getKey().getRouteId();

            // THE OPERATOR'S OWN IS KEPT: only a route the store holds no direction for is a gap.
            if (store.getTileDirection(tile, id) != null) continue;

            if (each.getValue() == graph.getDirection(tile, id)) continue;

            if (tuned)
            {
                result.directionsNotCarried++;

                continue;
            }

            record(tile, id, each.getValue());

            result.directionsCarried.put(each.getKey(), each.getValue());
        }

        if (!result.directionsCarried.isEmpty()) touched();
    }

    /**
     * The one side of a square that every edge from a legacy point leaves by, or null where they leave by more than
     * one, where there is none, or where one cannot be followed on this diagram (REG4-A1).
     *
     * An old edge joins a point to the next ones along the track, so the side it leaves by is the side from which its
     * end is the next square the old graph had a point on - asked of each side, not of the shortest walk, which round
     * a loop can reach the end the other way.  Followed over the track as drawn, whichever way trains may run on it
     * now: the question is where the file sent the train, not whether the diagram still lets it.  An edge that ends on
     * the same square - a station and its approach guard share a sensor - says nothing about a side; one whose end is
     * next by both sides, or by neither, cannot say.
     *
     * @param tile the square the train stands on
     * @param ends the squares the point's edges end on, null for one this diagram does not draw
     * @param pointSquares the squares the old graph had points on
     * @return the side, or null
     */
    private Side sideTheEdgesLeaveBy(TileKey tile, List<TileKey> ends, Set<TileKey> pointSquares)
    {
        if (tile == null || ends == null || graph == null) return null;

        Map<Side, Set<TileKey>> nextBy = new LinkedHashMap<>();

        for (Side side : Side.values()) nextBy.put(side, graph.firstStopsLeaving(tile, side, pointSquares));

        Side only = null;

        for (TileKey end : ends)
        {
            if (end == null) return null;

            if (end.equals(tile)) continue;

            Side side = null;

            for (Map.Entry<Side, Set<TileKey>> by : nextBy.entrySet())
            {
                if (!by.getValue().contains(end)) continue;

                if (side != null) return null;

                side = by.getKey();
            }

            if (side == null || (only != null && only != side)) return null;

            only = side;
        }

        return only;
    }

    /**
     * @param legacy the parsed autonomy.json
     * @param knownLocomotives the names the locomotive database holds, or null not to check
     * @return what was matched, placed, marked, carried and refused
     */
    public LegacyImport importLegacy(org.json.JSONObject legacy, Set<String> knownLocomotives)
    {
        LegacyImport result = new LegacyImport();

        // One locomotive stands in one place.  Tracked across the whole file rather than per point,
        // because the model's objection is global: two points naming the same locomotive invalidate
        // the layout, whichever pages they are on.
        Set<String> placedAlready = new LinkedHashSet<>();

        // And one locomotive has one HOME, for the same reason and by the same means (OB-075).
        //
        // setHome sweeps duplicates and says why - "a rule enforced at one door of two is the shape
        // this defect came from" (TD-8). This is that second door: the import writes "home" straight
        // into the configuration and never goes near setHome.
        //
        // A file written before the rule existed can legitimately name two, and both were imported.
        // Layout.rebuildHomeStations then drops one by iteration order with a log line, and the next
        // capture writes that arbitrary choice back permanently - so the user keeps a home they never
        // chose, and nothing says which had been theirs.
        //
        // First one wins, which is the file's own order and therefore at least reproducible; the rest
        // are counted so the choice can be reported rather than made silently.
        Set<String> homedAlready = new LinkedHashSet<>();

        org.json.JSONArray points = legacy.optJSONArray("points");

        if (points == null || reducer == null) return result;

        Map<Integer, TileKey> bySensor = new LinkedHashMap<>();

        // Sensors carried by more than one square, which no amount of reading the file can resolve.
        //
        // Two squares on the same s88 is ordinary - a station and its approach guard - and on a layout
        // whose pages repeat a section it happens across pages too.  The old graph names ONE point per
        // sensor, so there is no way to tell which square it meant, and putting it on whichever came
        // last would land somebody's station on the wrong page silently.  Refused and reported instead;
        // excluding the duplicating pages first is what makes the rest of the import unambiguous.
        Set<Integer> ambiguous = new LinkedHashSet<>();

        for (GraphReducer.ReducedPoint point : reducer.getPoints().values())
        {
            if (point.getS88() <= 0) continue;

            if (bySensor.containsKey(point.getS88())) ambiguous.add(point.getS88());

            bySensor.put(point.getS88(), point.getTile());
        }

        for (Integer sensor : ambiguous) bySensor.remove(sensor);

        // WHERE EACH POINT'S EDGES LEAD (REG4-A1).  A 2.8.1 graph states direction as one-way edges - a train on a
        // point goes only along the edges that start there - so they say which way a train standing there drives.
        // The squares they end on, by the same sensor match as the points; null for an end this diagram does not draw.
        Map<String, TileKey> squareOfPoint = new java.util.HashMap<>();

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null) continue;

            int sensor = point.optInt("s88", 0);

            squareOfPoint.put(point.optString("name", ""), sensor > 0 ? bySensor.get(sensor) : null);
        }

        Map<String, List<TileKey>> edgesLeadTo = new java.util.HashMap<>();

        // And each edge as the two squares it joins, for the directions it ran (Adam, 2026-09-24).
        List<TileKey[]> oldEdges = new ArrayList<>();

        org.json.JSONArray edges = legacy.optJSONArray("edges");

        for (int i = 0; edges != null && i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.optJSONObject(i);

            if (edge == null) continue;

            edgesLeadTo.computeIfAbsent(edge.optString("start", ""), start -> new ArrayList<>())
                .add(squareOfPoint.get(edge.optString("end", "")));

            oldEdges.add(new TileKey[] {squareOfPoint.get(edge.optString("start", "")),
                squareOfPoint.get(edge.optString("end", ""))});
        }

        // Where the walks from a square stop: the squares the old graph had points on (REG4-A1).
        Set<TileKey> pointSquares = new LinkedHashSet<>(squareOfPoint.values());

        pointSquares.remove(null);

        // The trains this import placed and whose facing it has still to find, by square and point name.  Decided
        // after the loop, over the finished setup (REG4-C1).
        Map<TileKey, String> facingToFind = new LinkedHashMap<>();

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null) continue;

            String name = point.optString("name", "");

            if (name.trim().isEmpty()) continue;

            int sensor = point.optInt("s88", 0);

            TileKey tile = sensor > 0 ? bySensor.get(sensor) : null;

            // A point with no sensor, or one watching a sensor this diagram does not draw.  Reported
            // rather than dropped: it is how somebody finds out that a page is missing or excluded.
            if (tile == null)
            {
                result.unmatched.add(name);
                continue;
            }

            // Before the name, and regardless of it.  A placement is about the SQUARE, so a square
            // somebody has already named should still get its locomotive back.
            org.json.JSONObject standing = point.optJSONObject("loc");

            String home = point.optString("home", "");

            boolean anySetting = false;

            for (String key : CARRIED_SETTINGS)
            {
                if (point.has(key)) anySetting = true;
            }

            if (standing != null || !home.trim().isEmpty() || anySetting)
            {
                org.json.JSONObject extras = configurationExtras(tile);

                if (extras != null)
                {
                    if (standing != null && !extras.has(AutonomyBuilder.LOCOMOTIVE))
                    {
                        String locName = standing.optString("name", "").trim();

                        // Checked here rather than left to the load.  The model answers an unknown
                        // locomotive by invalidating the whole layout, so a file naming one that has
                        // since been renamed would have produced a setup that refuses to open, with
                        // an error about a locomotive and nothing saying the import put it there.
                        if (locName.isEmpty())
                        {
                            // a placement with no name: nothing to place, nothing worth reporting
                        }
                        else if (knownLocomotives != null && !knownLocomotives.contains(locName))
                        {
                            result.unknownLocomotives.add(locName);
                        }
                        else if (!placedAlready.add(locName))
                        {
                            result.duplicateLocomotives.add(locName);
                        }
                        else
                        {
                            // Copied whole: the old graph recorded the speed, the arrival and
                            // departure functions and the train length alongside the name, and the
                            // builder reads exactly this shape back out.
                            extras.put(AutonomyBuilder.LOCOMOTIVE,
                                new org.json.JSONObject(standing.toString()));

                            // And which way it is pointing - found after the loop, over the finished
                            // setup (REG4-C1): see `findTheImportedFacings`.
                            if (getFacing(tile) == null) facingToFind.put(tile, name);

                            result.placed++;
                        }
                    }

                    if (!home.trim().isEmpty() && !extras.has("home"))
                    {
                        if (homedAlready.add(home.trim()))
                        {
                            extras.put("home", home.trim());
                        }
                        else
                        {
                            result.duplicateHomes++;
                        }
                    }

                    for (String key : CARRIED_SETTINGS)
                    {
                        // Gap-filled like everything else here, so re-running cannot undo an edit
                        // somebody made after the first import.
                        if (!point.has(key) || extras.has(key)) continue;

                        Object value = point.get(key);

                        // A ZERO CAPACITY IS NOT A CAPACITY.
                        //
                        // This loop is first-present-wins, and a legacy file legitimately puts several
                        // points on one sensor - a station and its approach guard. Twenty-five of
                        // Adam's sixty-two points carry an explicit `maxTrainLength: 0`, so whichever
                        // of the pair the file lists first decides, and a guard's zero would erase the
                        // station's real limit. Zero is what "no limit" already means, so skipping it
                        // costs nothing and lets the real value through whatever the file's order.
                        if ("maxTrainLength".equals(key) && value instanceof Number
                            && ((Number) value).intValue() <= 0)
                        {
                            continue;
                        }

                        // A SWITCHED-OFF STATION ARRIVES ON, AND NOT ONE AUTONOMY CHOOSES (REG-B1, Adam 2026-09-24:
                        // *"yes, translate as on but not auto destination."*).  At v2.8.1 it kept autonomy out and
                        // still took a train sent by hand; carried as `active: false` it is a square nothing may pass
                        // through or end on.  Gap-filled like the rest: a square that already says either is left.
                        if ("active".equals(key) && Boolean.FALSE.equals(value) && point.optBoolean("station", false))
                        {
                            if (!extras.has(AutonomyBuilder.AUTO_DESTINATION))
                            {
                                extras.put(AutonomyBuilder.AUTO_DESTINATION, Boolean.FALSE);

                                result.settings++;
                            }

                            continue;
                        }

                        // Copied rather than shared: a JSONArray handed straight over would be the same
                        // object the caller's parsed file still holds, and anything that later edited
                        // the exclusions here would edit their file's copy too.
                        if (value instanceof org.json.JSONArray)
                        {
                            value = new org.json.JSONArray(value.toString());
                        }
                        else if (value instanceof org.json.JSONObject)
                        {
                            value = new org.json.JSONObject(value.toString());
                        }

                        extras.put(key, value);

                        result.settings++;
                    }
                }
            }

            // Terminus and reversing are DERIVED now, so neither can be written down.  What the old
            // graph was recording, in both cases, is that every train arriving here turns round - and
            // that is authored as mustReverse.  Which of the two words the build then emits follows
            // from whether the square is a station, which is imported above, so one flag restores both.
            //
            //   terminus            a station that reverses on arrival.  In this file it is only ever
            //                       set on stations, which is what the model has always meant by it.
            //   reversing, plain    somewhere trains turn round that is not a destination.
            //   reversing, station  the old "reversing station", which said two things at once: it
            //                       reverses, AND autonomy never chooses it.  Those are separate now -
            //                       a terminus and a berth - so it takes the parking flag as well, or
            //                       importing would quietly turn somebody's shunting neck into a
            //                       destination trains get sent to.
            boolean turnsTrains = point.optBoolean("terminus", false)
                || point.optBoolean("reversing", false);

            // Left alone if the square already says something about reversing: this fills gaps.
            boolean alreadyMarked = getPointProperty(tile, AutonomyBuilder.MUST_REVERSE) != null
                || getPointProperty(tile, AutonomyBuilder.CAN_REVERSE) != null;

            if (turnsTrains && !alreadyMarked)
            {
                setPointFlag(tile, AutonomyBuilder.CAN_REVERSE, false);
                setPointProperty(tile, AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);

                if (point.optBoolean("reversing", false) && point.optBoolean("station", false))
                {
                    setPointFlag(tile, AutonomyBuilder.PARKING, true);
                }

                result.reversing++;
            }

            String existing = store.getPointName(tile);

            if (existing != null && !existing.trim().isEmpty())
            {
                result.skipped++;
                continue;
            }

            store.setPointName(tile, name);

            // Labelled on the station square itself.
            //
            // Every station has to be shown on the diagram - it is an error not to be - and an import
            // that named fifty of them and captioned none would have handed the user fifty errors to
            // clear by hand.  The station's own square is the one place that is always right: it
            // exists, it is on the page the reader is looking at, and it cannot land on somebody
            // else's track the way searching for nearby blank space can.
            //
            // Only where the station has no caption already, like everything else here.
            if (point.optBoolean("station", false) && captionsFor(tile).isEmpty())
            {
                store.setCaption(tile, tile);
            }

            if (point.optBoolean("station", false)) store.setStation(tile, true);

            // maxTrainLength is CARRIED, not converted (IPR-A1).
            //
            // It used to be written here as `store.setTileLength(tile, length)`, which reads a
            // station's capacity as a track's length.  They are different measurements and a legacy
            // file holds both - capacity on the points, length on the edges - so every upgrading
            // user's stations lost their limits while six squares gained lengths nobody measured.
            //
            // It goes through CARRIED_SETTINGS with the other operational properties now, which is
            // where the rest of parseAuto's own keys already go.

            result.matched++;
        }

        // THE OLD FILE'S DIRECTIONS FIRST, so the facings below are read over the copies they make.
        carryTheOldDirections(oldEdges, pointSquares, result);

        findTheImportedFacings(facingToFind, edgesLeadTo, pointSquares, result);

        // AND THE SETTINGS ABOVE THE POINTS (RGN-A1).
        //
        // A 2.7.4c autonomy.json is one object: the points and edges, and above them the whole
        // settings panel - pace, default speed, how many trains may run, whether routes are fired -
        // and the timetable.  This method read `points` and nothing else, so an upgrading user kept
        // their station names and lost every rule about how their railway RUNS.  Ten settings come
        // across on Adam's own legacy file; three more keys are deliberately left behind below, and
        // each says why where it is excluded.
        //
        // The same copy captureFromLayout already makes, asked here: everything that is not the setup
        // itself.  Gap-filled like the rest of this import, so running it twice cannot undo a setting
        // somebody changed after the first run.
        //
        // NOT the edges' lengths.  A legacy file records length per EDGE and the diagram model
        // records it per SQUARE, and which square a length belongs to when an edge spans several is a
        // question this cannot answer without guessing.  Thirty of Adam's ninety edges carry one, so
        // it is worth answering - it is left for a decision rather than migrated wrongly.
        org.json.JSONObject settings = configurationGlobals();

        if (settings != null)
        {
            for (String key : legacy.keySet())
            {
                if ("points".equals(key) || "edges".equals(key)) continue;

                // NOT THE ROUTE ACTIVATIONS, and this is the important exclusion.
                //
                // These two do not stay inside the configuration. parseAuto ends in
                // applyAutonomyRouteActivations, which walks the LIVE Central Station route database:
                // every route whose id is not in activateRouteIDs is disabled, and every route that is
                // gets enabled and executed - accessories thrown on the real railway.
                //
                // Adam's own legacy file carries `activateRoutes: true` with an EMPTY id list, so
                // carrying these across would disable every route he has, and do it again on every
                // parseAuto - which is a diagram edit or a locomotive being placed, not only a load.
                // An import that brings station names across must not switch the railway's routes off.
                //
                // REPORTED, since ACC-B2 - and this comment used to say it was not (RG4-C3).
                //
                // It read "a gap worth naming rather than papering over: the import dialog counts
                // what it matched and what it skipped, and says nothing about these".  True when it
                // was written, made false by the fix for the very finding it describes:
                // whatALegacyImportLeaves raises `autosetup.ui.leftRouteActivations`, the import
                // path logs the list, and the dialog points the user at it.
                //
                // Corrected rather than deleted, because this is the first place the next reader of
                // the skip will look, and left as it stood it invited rebuilding reporting that
                // already exists.
                if ("activateRoutes".equals(key) || "activateRouteIDs".equals(key)) continue;

                // NOT THE TIMETABLE EITHER, for the reason the edge lengths are left out: it cannot
                // be brought across without translating, and translating it is a decision.
                //
                // Measured on his file: the 36 entries name 25 points. Three carry no s88 at all, so
                // they match no square; nine more sit on sensors shared by two or three legacy points,
                // where the name that reaches the tile is whichever came first. TimetablePath.fromJSON
                // throws on the first edge it cannot resolve and drops that entry with a warning, so
                // what would arrive is a heap of log lines and a nearly empty timetable - which the
                // next captureFromLayout then writes back over these globals for good.
                //
                // Leaving it in autonomy.json, untouched and readable, loses less than that.
                if ("timetable".equals(key)) continue;

                if (settings.has(key)) continue;

                Object value = legacy.get(key);

                // Copied rather than shared, for the reason the point extras above are: a JSONArray
                // handed straight over stays the caller's, and the timetable is an array.
                if (value instanceof org.json.JSONArray)
                {
                    value = new org.json.JSONArray(value.toString());
                }
                else if (value instanceof org.json.JSONObject)
                {
                    value = new org.json.JSONObject(value.toString());
                }

                settings.put(key, value);

                result.settings++;
            }

            dirty = true;
        }

        // Derived again, once, now that the authored data has changed.
        //
        // The reduction is what the diagram draws from - the names on the squares, the station
        // markers, the captions - and it is built from a snapshot of the authored data taken when the
        // session opened.  Without this the import wrote everything correctly to the store and the
        // diagram went on showing what it knew before, so the whole thing looked to have done nothing
        // until the layout was reloaded.
        //
        // After the loop rather than inside it: the tile-by-sensor map above comes from the reduction,
        // so rebuilding mid-loop would pull it out from under the very walk that is using it.
        rebuild();

        return result;
    }

    /**
     * The address in a legacy command's accessory name, when that accessory is a signal (RG4-B1).
     *
     * A 2.8.1 command names its accessory as a display string - "Signal 116", "Switch 68" - because
     * that is what the old editor put in front of the user.  Only the signals are wanted here: a
     * switch position is reproduced from the track diagram and needs no attention, and a signal is
     * driven only where the operator pairs it.
     *
     * Anything that is not a signal, and anything whose number does not parse, is simply not a signal
     * address - this is a report, and a report that throws on a malformed old file is worse than one
     * that leaves a line out of it.
     *
     * @param accessory the legacy command's `acc` value, which may be null
     * @return the address, or null when this is not a signal
     */
    public static Integer legacySignalAddress(String accessory)
    {
        if (accessory == null) return null;

        final String trimmed = accessory.trim();

        // Case-insensitively, because the name is a display string and nothing ever guaranteed its
        // case; the space is required so that a user-named accessory beginning "Signalbox" is not
        // read as a signal.
        if (trimmed.length() < 8 || !trimmed.substring(0, 7).equalsIgnoreCase("Signal ")) return null;

        final String rest = trimmed.substring(7).trim();

        // THE DIGITS, AND WHATEVER THE PROTOCOL ADDS AFTER THEM (VLD-B1).
        //
        // This parsed the whole remainder as a number, which is right for exactly one protocol.  The
        // standardized name this program generates is `Accessory.getNameWithProtocol`, and for any
        // decoder that is not the implicit default it appends the protocol: "Signal 116 DCC".  So a
        // DCC railway got null for every signal, the address set stayed empty, and the report written
        // to stop a signal being silently left out was silent about an entire protocol.
        //
        // Adam's own five are MM2, which is why his file exercised the one case that worked - and is
        // exactly the reason a fixture built from his data cannot be the only fixture.
        int end = 0;

        while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;

        if (end == 0) return null;

        // DIGITS ONLY, so a sign is not an address (VLD-C5).  `Integer.valueOf` accepts "+116" and
        // "-5"; neither is a real accessory, and rendering "-5" into the operator's work list sends
        // them looking for a signal that cannot exist.
        //
        // What may follow the digits is a protocol token and nothing else: anything the number runs
        // straight into - "Signal 1 2", "Signal 116red" - is not a name this program writes.
        if (end < rest.length() && !Character.isWhitespace(rest.charAt(end))) return null;

        try
        {
            return Integer.valueOf(rest.substring(0, end));
        }
        catch (NumberFormatException tooLongForAnInt)
        {
            return null;
        }
    }

    /**
     * What a legacy import deliberately leaves behind, counted from the file it read (MT-257).
     *
     * Adam, 2026-09-02: **"yes, but list them in the log and mention that in the dialog."**  Until
     * now the import reported six counts of what it brought and nothing at all about the four things
     * it drops - and each of those has a reason written at the code that the operator never sees.
     *
     * Counted rather than merely named, because "the timetable was not imported" and "the 36 entries
     * of your timetable were not imported" are different sentences to somebody deciding whether to go
     * back to the old file.
     *
     * @param legacy the parsed autonomy.json
     * @return one line per thing dropped, empty when the file carried none of them
     */
    public java.util.List<String> whatALegacyImportLeaves(org.json.JSONObject legacy)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        if (legacy == null) return out;

        int withCommands = 0;
        int withLength = 0;
        int withLocks = 0;

        // Sorted by address, because the operator works down this list with the Pair Signal item
        // open, and an arbitrary order in a list of twelve is a list they lose their place in.
        java.util.SortedSet<Integer> signalAddresses = new java.util.TreeSet<>();

        if (legacy.has("edges"))
        {
            org.json.JSONArray edges = legacy.getJSONArray("edges");

            for (int i = 0; i < edges.length(); i++)
            {
                org.json.JSONObject e = edges.optJSONObject(i);

                if (e == null) continue;

                if (e.has("commands") && !e.isNull("commands")
                    && e.getJSONArray("commands").length() > 0)
                {
                    withCommands++;

                    // AND THE SIGNALS BY NAME, not just inside that count (RG4-B1).
                    //
                    // Most commands are switch positions, which the derivation reproduces from the
                    // track diagram and which nobody needs to think about.  A SIGNAL is different:
                    // the new model drives one only where it is paired with a station, and pairing is
                    // a gesture the operator has to make, by address, from the station's own menu.
                    //
                    // Measured on Adam's own file, which is why this exists: twelve signals were
                    // driven red by hand, seven were paired from memory, and the remaining five stay
                    // green after every arrival with nothing telling him which five.  The aggregate
                    // count cannot say it: it reads sixty-nine and means three.
                    //
                    // NAMED, NOT COMPARED, and it errs on the side of naming too many.  Deciding
                    // which are already paired means resolving each address to a square through the
                    // reduction - and this runs on a file that has not been imported yet, so the
                    // reduction it would need is the one the import is about to replace.  Naming a
                    // signal already paired costs a glance at a menu; omitting one costs a signal
                    // that lies about occupancy for as long as the railway runs.
                    org.json.JSONArray commands = e.getJSONArray("commands");

                    for (int c = 0; c < commands.length(); c++)
                    {
                        org.json.JSONObject command = commands.optJSONObject(c);

                        if (command == null) continue;

                        Integer address = legacySignalAddress(command.optString("acc", null));

                        if (address != null) signalAddresses.add(address);
                    }
                }

                if (e.optInt("length", 0) > 0) withLength++;

                // HAND-WRITTEN LOCKS, the fifth thing this drops and the only silent one (ACC-B1).
                //
                // A 2.8.1 edge could carry `lockedges` - "when this edge is taken, these others are
                // locked too" - and the new model derives locks from geometry instead:
                // `GraphReducer.deriveLocks` locks any two edges that occupy a shared tile.
                //
                // That reproduces most of them and cannot reproduce the conservative kind: a lock
                // between edges that share NO tile, written by hand for parallel adjacent tracks, an
                // electrical section, a clearance rule.  Those simply vanish, and the consequence is
                // two trains permitted to move at once where the file forbade it.
                //
                // Counted rather than compared, deliberately.  Deciding which of them the geometry
                // already covers means building the derived graph here, and a report that is wrong
                // in the safe direction - naming locks that turn out to be reproduced - is better
                // than one that is silent about locks that are not.  Adam's own legacy file carries
                // 116 references across 50 of its 90 edges.
                if (e.has("lockedges") && !e.isNull("lockedges")
                    && e.getJSONArray("lockedges").length() > 0)
                {
                    withLocks++;
                }
            }
        }

        if (withCommands > 0) out.add(I18n.f("autosetup.ui.leftEdgeCommands", withCommands));

        if (withLength > 0) out.add(I18n.f("autosetup.ui.leftEdgeLengths", withLength));

        if (withLocks > 0) out.add(I18n.f("autosetup.ui.leftEdgeLocks", withLocks));

        // AND THE SIGNALS BY NAME, not just inside the command count (RG4-B1).
        //
        // The count above says "69 connections carried commands" and stops there.  Most of those are
        // switch positions, which the derivation reproduces from the track diagram and which nobody
        // needs to think about.  A SIGNAL is different: the new model drives one only where it is
        // paired with a station, and pairing is a gesture the operator has to make - by address, from
        // the station's own menu.
        //
        // Measured on Adam's own file, which is why this exists: twelve signals were driven red by
        // hand, he paired seven of them from memory, and the remaining five stay green after every
        // arrival with nothing anywhere telling him which five.  The aggregate count cannot: it says
        // sixty-nine and means three.
        //
        // NAMED, NOT COMPARED, and it errs on the side of naming too many.  Deciding which of these
        // is already paired means resolving each signal's address to a square through the reduction,
        // and this method is called on a file that has not been imported yet - the reduction it would
        // need is the one the import is about to change.  A list that includes signals the operator
        // has already paired costs them a glance at a menu; a list that quietly omits one costs them
        // a signal that lies about occupancy for as long as the railway runs.
        //
        // Sorted by address, because the operator will be working down it with the Pair Signal item
        // open, and an arbitrary order in a list of twelve is a list they lose their place in.
        // Collected in the loop above rather than in a second walk of the same array (VLD-C7).
        //
        // It was written as its own loop over `legacy.getJSONArray("edges")`, with its own copy of
        // the same null and isNull guards - two loops over one array, which is the drift shape this
        // codebase keeps finding: a guard added to one and not the other.
        if (!signalAddresses.isEmpty())
        {
            StringBuilder names = new StringBuilder();

            for (Integer address : signalAddresses)
            {
                if (names.length() > 0) names.append(", ");

                names.append(address);
            }

            out.add(I18n.f("autosetup.ui.leftSignalAuthors", signalAddresses.size(), names.toString()));
        }

        // AND A FILE THAT IS NOT ACTUALLY A 2.8.1 ONE (ACC-C7).
        //
        // `detectImportFormat` calls any file whose `points` is an ARRAY a legacy graph, and a
        // configuration serialised by `Layout.toJSON` is one of those too - so exporting a modern
        // setup and importing it again comes through this door, which carries none of the fields the
        // modern format added.  No genuine 2.8.1 file has them, so the supported migration is
        // unaffected; what this catches is the round trip.
        //
        // Reported rather than refused.  A refusal would have to be certain, and "this file has a key
        // the old format never wrote" is evidence rather than proof - somebody hand-editing a legacy
        // file could produce it.  The operator can see the count and decide.
        int modern = 0;

        if (legacy.has("points") && legacy.optJSONArray("points") != null)
        {
            org.json.JSONArray points = legacy.getJSONArray("points");

            for (int i = 0; i < points.length(); i++)
            {
                org.json.JSONObject p = points.optJSONObject(i);

                if (p == null) continue;

                // `entrySignal` too (FR-096): one more key only this version writes.
                if (p.has(AutonomyBuilder.AUTO_DESTINATION) || p.has("protectingSignal")
                    || p.has("block") || p.has("entrySignal"))
                {
                    modern++;
                }
            }
        }

        if (modern > 0) out.add(I18n.f("autosetup.ui.leftModernFields", modern));

        if (legacy.has("timetable") && !legacy.isNull("timetable"))
        {
            out.add(I18n.f("autosetup.ui.leftTimetable",
                legacy.getJSONArray("timetable").length()));
        }

        if (legacy.has("activateRoutes") || legacy.has("activateRouteIDs"))
        {
            out.add(I18n.t("autosetup.ui.leftRouteActivations"));
        }

        return out;
    }

    /**
     * Brings in an exported file and derives again, so the diagram shows what arrived.
     *
     * The store's own importBundle knows nothing about the derivation - it holds authored data and
     * that is all - so a caller that went straight to it got a correct store and a screen still
     * showing what it knew before.  A configuration carries the flags that terminus, reversing and a
     * station's switch are DERIVED from, so those in particular arrived and stayed invisible.
     *
     * Here rather than in the caller because it is the same invariant every time: authored data
     * changed, so the derivation is stale.  The legacy import learned that separately.
     *
     * @param name what to call the configuration here
     * @param file the parsed export
     * @return how many shared entries were filled in
     */
    public int importBundle(String name, org.json.JSONObject file) throws java.io.IOException
    {
        int filled = store.importBundle(name, file);

        rebuild();

        return filled;
    }

    /**
     * The pages the last import described that this layout does not have, and so left out (MT-380).
     *
     * @return the page names, empty when nothing was left out
     */
    public java.util.List<String> getPagesLeftOutOfLastImport()
    {
        return store.getPagesLeftOutOfLastImport();
    }

    /**
     * Shuts any page that repeats a sensor an earlier page already carries.
     *
     * A layout whose pages draw the same track twice - an overview and a detail view of one yard, say -
     * gives two squares the same s88, and nothing downstream can tell which one a train is standing on.
     * The reduction makes a Point of each, so the same sensor becomes two destinations; a legacy import
     * cannot decide which square a name belongs to and refuses it; and the checks report the duplicate
     * on every page it appears on.
     *
     * Earliest page wins, in the order the layout lists them, because that is the one a reader thinks
     * of as the real one and the only rule that does not depend on which page happens to be open.
     * A page that is shut does NOT contribute its sensors to what counts as seen: the next page
     * repeating them is then repeating the page that is still in play, not one nobody is using.
     *
     * TWO CALLERS, AND THAT IS SETTLED (OB-130). A new configuration runs this because a starting
     * point is wanted; a legacy import runs it because a setup built before this rule existed has
     * never been held to it, and importing is the moment it first is.
     *
     * Adam, 2026-08-29, asked whether the second may shut pages he had already chosen to keep: "yes,
     * it may override." The reasoning is that a page repeating another page\u2019s sensors is not a
     * preference to be respected - it is a diagram the reduction cannot make sense of, because the
     * same s88 becomes two destinations and nothing downstream can say which square a train is on. A
     * setting made in ignorance of a rule is not a decision the rule has to honour.
     *
     * It remains a starting point rather than a policy everywhere else: the page checkboxes are still
     * there, and turning one back on is not undone by adding a configuration.
     *
     * @return the pages this shut, in the order they appear
     */
    public List<String> excludeRepeatedSensorPages()
    {
        Set<Integer> seen = new LinkedHashSet<>();

        List<String> shut = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (store.getExcludedPages().contains(page.getName())) continue;

            // AND A PAGE THE OPERATOR TURNED BACK ON IS LEFT ALONE (TST-B15, Adam 2026-09-11).
            //
            // This method has two callers and the second is a legacy import into an EXISTING setup,
            // run after the operator has had every chance to change their mind.  It only skipped pages
            // already in the excluded set, so a page it had shut and the operator had switched back on
            // looked exactly like a page nobody had ever considered - and it shut it again.
            //
            // The 2026-08-29 ruling that an import "may override" still holds for pages nobody has had
            // an opinion about, which is what it was asked about.  An explicit re-enable is an opinion.
            if (store.getPagesKeptDespiteRepeats().contains(page.getName())) continue;

            Set<Integer> here = new LinkedHashSet<>();

            boolean repeats = false;

            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || !component.isFeedback()) continue;

                int sensor = component.getRawAddress();

                if (sensor <= 0) continue;

                if (seen.contains(sensor)) repeats = true;

                here.add(sensor);
            }

            if (repeats)
            {
                store.setPageExcluded(page.getName(), true);

                shut.add(page.getName());

                continue;
            }

            seen.addAll(here);
        }

        if (!shut.isEmpty()) rebuild();

        return shut;
    }

    /**
     * Which configuration should be running once an import has finished.
     *
     * Stated here, as a rule rather than as a branch inside a button, because it was wrong in a way no
     * test could see: the caller returned early when something was already running, so an import onto
     * a working setup never reloaded - and the running layout went on describing the setup as it was
     * before, with every caption the import created drawn against a Point that did not exist.
     *
     * Whatever is already chosen wins.  Importing a configuration is not a request to switch to it,
     * and the shared half an import merges - the names, the stations, the lengths - belongs to every
     * configuration equally, so the one already running needs re-deriving whichever was imported.
     *
     * @param running what is loaded now, or null for nothing
     * @param imported the name the import was given
     * @return the configuration to load, or null when there is nothing to do
     */
    public static String configurationToLoadAfterImport(String running, String imported)
    {
        if (running != null && !running.trim().isEmpty()) return running;

        if (imported == null || imported.trim().isEmpty()) return null;

        return imported.trim();
    }

    /**
     * The kinds of file the one Import action accepts.
     */
    public static enum ImportFormat
    {
        /**
         * A configuration and the track decisions it refers to, as exportBundle writes them.
         */
        BUNDLE,

        /**
         * A configuration on its own, as exporting wrote it before bundles existed.
         */
        CONFIGURATION,

        /**
         * An autonomy.json from the graph this feature replaces.
         */
        LEGACY_GRAPH,

        /**
         * Something else entirely - a routes file, a locomotive database, a diagram.
         */
        UNKNOWN
    }

    /**
     * Works out which of them a parsed file is, so the user does not have to say.
     *
     * Each shape is identified by something only it has, not by anything as fragile as a filename:
     *
     *   BUNDLE          carries "configuration", which is the key exportBundle invented.
     *   LEGACY_GRAPH    carries "points" as an ARRAY.  The old graph was a list of Points, each with
     *                   its own name and s88; nothing else here is a list under that name.
     *   CONFIGURATION   carries "points" as an OBJECT, keyed by square.  That is the shape a
     *                   configuration has always had, and the only other thing that uses the name.
     *
     * The array-versus-object distinction is what makes this safe: the two formats that share a key
     * disagree about its type, so neither can be mistaken for the other by a file that merely happens
     * to contain the word.
     *
     * @param file the parsed file
     * @return what it is
     */
    public static ImportFormat detectImportFormat(org.json.JSONObject file)
    {
        if (file == null) return ImportFormat.UNKNOWN;

        if (file.optJSONObject(AutonomyCompanionStore.EXPORT_CONFIGURATION) != null)
        {
            return ImportFormat.BUNDLE;
        }

        // A list of Points, which is the old graph and nothing else
        if (file.optJSONArray("points") != null) return ImportFormat.LEGACY_GRAPH;

        // Keyed by square, which is a configuration
        if (file.optJSONObject("points") != null) return ImportFormat.CONFIGURATION;

        // A configuration whose points have all been removed is still a configuration, and its globals
        // are the only thing left to say so.
        if (file.optJSONObject("globals") != null) return ImportFormat.CONFIGURATION;

        // Deliberately NOT recognised: a configuration created and never used, which carries nothing
        // but its own name.  A name is not evidence - half the JSON in the world has one - and there
        // would be nothing in such a file to import anyway.

        return ImportFormat.UNKNOWN;
    }

    /**
     * Each copy of a square a train may be put down on, with the way it faces (OB-270, GUI-B1).
     *
     * `facingsFor` names every copy, and a square's copies are not all destinations: a copy trains may not arrive at is
     * built as no station, and a train stood there is one autonomy will not start - it is told that trains may not
     * arrive there facing its way (`autolayout.why.startFacingBarred`).  `copyFacing` asks this first, so that among the
     * copies facing one way it takes one trains may arrive at.  The placement doors no longer filter a train's heading
     * by it: they keep any heading a train can leave by (`departableFacingsFor`, OB-284).
     *
     * With no running layout there is nothing to ask a copy, and every copy is returned, which is what each door did
     * before.
     *
     * @param square the square
     * @param running the running layout, or null
     * @return the placeable copies by name, in the build's order
     */
    public Map<String, Side> placeableFacingsFor(TileKey square, org.traincontrol.automation.Layout running)
    {
        Map<String, Side> all = square == null ? new LinkedHashMap<String, Side>() : facingsFor(square);

        if (running == null) return all;

        Map<String, Side> out = new LinkedHashMap<>();

        for (Map.Entry<String, Side> copy : all.entrySet())
        {
            org.traincontrol.automation.Point point = running.getPoint(copy.getKey());

            if (point != null && point.isDestination()) out.put(copy.getKey(), copy.getValue());
        }

        return out;
    }

    /**
     * Each copy of a square a train standing on it could leave, with the way it faces (OB-284).
     *
     * Adam, 2026-09-24: *"this should check for barred departure directions, not arrival ones.  for barred arrival
     * directions, keep the direction.  for barred departure directions, turn it to a way trains may arrive in"*.  So
     * the doors that put a train down - the paste, the editor's Place, the Place Locomotive dialog - keep its heading
     * wherever a copy facing that way has a way out on the running layout, a copy trains may not arrive at included:
     * the copy IS the train's direction, and it is told why autonomy will not start it there.  A heading no copy can
     * leave by is not kept, and `facingAfterAPaste` then leaves the build's choice - a copy trains may arrive at.
     *
     * With no running layout there is nothing to ask a copy, and every copy is returned.
     *
     * @param square the square
     * @param running the running layout, or null
     * @return the copies with a way out by name, in the build's order
     */
    public Map<String, Side> departableFacingsFor(TileKey square, org.traincontrol.automation.Layout running)
    {
        Map<String, Side> all = square == null ? new LinkedHashMap<String, Side>() : facingsFor(square);

        if (running == null) return all;

        Map<String, Side> out = new LinkedHashMap<>();

        for (Map.Entry<String, Side> copy : all.entrySet())
        {
            org.traincontrol.automation.Point point = running.getPoint(copy.getKey());

            if (point != null && !running.getNeighbors(point).isEmpty()) out.put(copy.getKey(), copy.getValue());
        }

        return out;
    }

    /**
     * The copy of a square a train facing this way stands on - one trains may arrive at where there is one, preferring
     * one that does not turn it (MT-368, MT-394, GUI-B1, TDY2-A1).
     *
     * More than one copy can face the same way (a plain copy and its turning twin); the plain one is where a train could
     * have come to rest facing so.  A copy trains may arrive at comes first: measured on BottomMainB, the plain westbound
     * copy is no station and the only station copy facing west is the turning one - and at BottomMainPost the
     * FIRST copy facing south is one no train may arrive at, which is where the Facing door and the idle drain after a
     * turn used to put the train.
     *
     * **But where no such copy faces that way, one that faces it anyway** (TDY2-A1, GUI2-A1).  The copy IS the
     * direction: a train reversed on the throttle, or told by the Facing menu which way it really points, stood on a copy
     * facing the other way is dispatched along a route locked one way while its decoder drives it the other.  On a copy
     * autonomy cannot start from it is refused with a sentence saying why.
     *
     * @param square the square
     * @param facing the heading asked for
     * @param running the running layout
     * @return that copy on the running layout, or null when no copy of the square faces that way
     */
    public org.traincontrol.automation.Point copyFacing(TileKey square, Side facing,
        org.traincontrol.automation.Layout running)
    {
        if (square == null || facing == null || running == null) return null;

        org.traincontrol.automation.Point turning = null;

        for (Map.Entry<String, Side> copy : placeableFacingsFor(square, running).entrySet())
        {
            if (copy.getValue() != facing) continue;

            org.traincontrol.automation.Point candidate = running.getPoint(copy.getKey());

            if (candidate == null) continue;

            if (!candidate.isTerminus() && !candidate.isReversing()) return candidate;

            if (turning == null) turning = candidate;
        }

        if (turning != null) return turning;

        // NONE A TRAIN MAY ARRIVE AT FACES THAT WAY: any copy that does, the plain one first (TDY2-A1).
        for (Map.Entry<String, Side> copy : facingsFor(square).entrySet())
        {
            if (copy.getValue() != facing) continue;

            org.traincontrol.automation.Point candidate = running.getPoint(copy.getKey());

            if (candidate == null) continue;

            if (!candidate.isTerminus() && !candidate.isReversing()) return candidate;

            if (turning == null) turning = candidate;
        }

        return turning;
    }

    /**
     * The way one named locomotive faces on a square, read off the copy the running layout has it on - or null when the
     * railway does not have it there.  `facingOnTheRailway`'s question for a particular train, asked of the same index.
     *
     * @param tile the square
     * @param locomotive the locomotive
     * @param running the running layout, or null
     * @return the side, or null
     */
    private Side facingOfTrainOnTheRailway(TileKey tile, String locomotive, org.traincontrol.automation.Layout running)
    {
        if (tile == null || running == null || locomotive == null || getStationIndex() == null) return null;

        for (String name : getStationIndex().pointNamesAt(tile))
        {
            org.traincontrol.automation.Point copy = running.getPoint(name);

            if (copy == null || copy.getCurrentLocomotive() == null) continue;

            if (locomotive.equals(copy.getCurrentLocomotive().getName())) return getStationIndex().facingsAt(tile).get(name);
        }

        return null;
    }

    /**
     * Stands a locomotive on the copy of its square that faces a given way (`DIR-B3`).
     *
     * A square is several Points once it is split - one per facing a train can hold there - and the
     * copy a locomotive is on is what the runtime uses to decide where it can go next.  `facingsFor`
     * is the map from each copy's NAME to the side its train faces, which is the same map
     * `captureFromLayout` reads in the other direction.
     *
     * **Not `moveLocomotive`.** That is the placement gesture: it refuses while anything is running,
     * requires the target to be a destination - which a split copy need not be - and adds the
     * locomotive to the run list.  None of that applies here.  This locomotive is already placed and
     * already in the list; what changes is which copy of one square it stands on.
     *
     * Quiet about everything it cannot do.  A layout that was never handed over, a square whose copies
     * the running layout does not carry, a locomotive the layout does not know: each leaves the setup
     * write standing on its own, which is what happened before this existed.
     *
     * @param running the layout, or null
     * @param locomotive the locomotive's name
     * @param tile the square it stands on
     * @param facing the way it now faces
     */
    private void moveOntoFacingCopy(org.traincontrol.automation.Layout running, String locomotive,
        TileKey tile, Side facing)
    {
        if (running == null || locomotive == null || facing == null) return;

        org.traincontrol.base.Locomotive train = null;

        // A COPY FACING THAT WAY, one trains may arrive at first (GUI-B1).  This took the first copy facing that way, and
        // at BottomMainPost - which trains may turn at, with arrivals from the north barred - the first copy facing south
        // is one no train may arrive at: a train that came in from the south and turned was moved onto it by the idle
        // drain, and autonomy would not start it from there.  But never a copy facing another way (TDY2-A1): where only a
        // barred copy faces that way, that is where the train goes - `copyFacing` says why.
        org.traincontrol.automation.Point onto = copyFacing(tile, facing, running);

        org.traincontrol.automation.Point from = null;

        java.util.List<org.traincontrol.automation.Point> here = new java.util.ArrayList<>();

        for (Map.Entry<String, Side> copy : facingsFor(tile).entrySet())
        {
            org.traincontrol.automation.Point point = running.getPoint(copy.getKey());

            if (point == null) continue;

            here.add(point);

            if (point.getCurrentLocomotive() != null
                && locomotive.equals(point.getCurrentLocomotive().getName()))
            {
                train = point.getCurrentLocomotive();

                from = point;
            }
        }

        // Nowhere to put it, or it is not standing on this square in the running layout at all.
        if (onto == null || train == null) return;

        if (onto.getCurrentLocomotive() == train) return;

        // AND THE TAIL COMES WITH IT (REV9-B1).
        //
        // `Point.setLocomotive` clears `arrivedFrom` on every change of occupant - *"a different
        // occupant did not come in that way"* - and both calls below are changes of occupant, so the
        // side went out with the old copy and the new copy was never given it.  That is right for a
        // different train arriving and wrong for THIS move, which is the same train being re-stood on
        // a sibling copy of one square: behaviour.md 4 - *"turning the train round at the platform
        // does not move its tail: the carriages stay where they stopped"*.
        //
        // So exactly the train whose turn was just written lost the record of which track it is lying
        // across, and the switch it is fouling was offered to the next route.  Saved and put back, the
        // way `TrainControlUI.putTheTrainsBack` does either side of its own re-placement.
        //
        // Read from the copy it was ON, because that is the one the arrival wrote it to.
        final String tail = from == null ? null : from.getArrivedFrom();

        // AND THE ROUTE IT DROVE, for the same reason (TDR-B2).  A train that was driven here follows that
        // route back past a junction (Adam, MT-335: "Follow its last route"), and the clear below loses it
        // exactly as it loses the side.
        final java.util.List<org.traincontrol.automation.Edge> along =
            from == null ? null : from.getArrivedAlong();

        // CLEARED FIRST, so the train is never on two copies of one square at once - which is the
        // state `DIR-C3` is about and which the checks report.
        for (org.traincontrol.automation.Point point : here)
        {
            if (point.getCurrentLocomotive() == train) point.setLocomotive(null);
        }

        onto.setLocomotive(train);

        // Put back unconditionally.  This used to decline where the destination copy "already knew", on
        // the reasoning that an arrival on that copy wrote a newer value - but `setLocomotive` just above
        // cleared both fields on it, because this train was not its occupant, so there was never anything
        // newer to keep (TDR-B2).
        if (tail != null) onto.setArrivedFrom(tail);

        if (along != null) onto.setArrivedAlong(along);
    }

    /**
     * Faces a train the way it came in, because the railway has just turned it round there (REV9-A1).
     *
     * **The answer is absolute, and that is the whole point of it.**  `flipFacing` is relative - it
     * takes the facing it finds for the square and writes the other choice.  Since TDY3-A2 that is the
     * copy the railway has the train on, where it has one; it was the setup's record, and the drain in
     * `TrainControlUI.reconcileFacingWhenIdle` used it at the one moment that record cannot be
     * trusted.  behaviour.md 6a states the reason as a rule: *"a run moves trains, where they ended up
     * lives only in the running layout, and nothing writes it back to the setup when the run ends"* -
     * and `captureFromLayout` never clears `FACING`, so an arrival square carries either nothing or
     * the previous occupant's answer.  Flipping nothing wrote nothing and the turn was lost; flipping
     * the previous occupant's answer wrote the turn backwards and moved the train off the copy that
     * was right.  Adam met both as OB-190.
     *
     * What IS reliable is the side the train came in by.  behaviour.md 4: *"a train faces the way it
     * will leave; once it has been turned round the two point the same way while the carriages have
     * not moved."*  So the facing of a train that has just turned at its destination is its
     * `arrivedFrom` - which the arrival wrote onto this very Point, before turning it, from the last
     * edge of the path it drove.  Nothing has to be in sync first, and applying it a second time
     * writes the same thing again rather than undoing it.
     *
     * **Both records, as `flipFacing` does** (DIR-B3, Adam 2026-09-06: *"flipFacing should also update
     * the running layout"*).  Which copy a train stands on IS its direction, so a setup written
     * correctly and a running layout left on the old copy still offers paths for the old heading.
     *
     * **Quiet where it cannot answer, and it says so by returning null** - a Point that is not this
     * square's, a square the build has no copies for, an arrival side no copy of the square can face.
     * The caller keeps the record when this returns null, so the turn is retried on the next refresh
     * rather than forgotten.
     *
     * @param locomotive the locomotive that was turned
     * @param turnedAt the Point it was standing on when it turned, from the record the arrival made
     * @param running the running layout, or null to write the setup only
     * @return the square whose facing was written, or null when nothing was
     */
    public TileKey faceTheWayItCameIn(String locomotive, org.traincontrol.automation.Point turnedAt,
        org.traincontrol.automation.Layout running)
    {
        if (locomotive == null || turnedAt == null || getStationIndex() == null) return null;

        // STILL STANDING WHERE IT TURNED.  A hand placement, a paste or a rebuild between the arrival
        // and the drain replaces this fact with somebody's own answer, and that answer is the newer
        // one - so this writes nothing rather than turning a train the operator has just put down.
        if (turnedAt.getCurrentLocomotive() == null
            || !locomotive.equals(turnedAt.getCurrentLocomotive().getName()))
        {
            return null;
        }

        String came = turnedAt.getArrivedFrom();

        if (came == null) return null;

        Side arrived = null;

        for (Side side : Side.values())
        {
            if (side.name().equals(came)) arrived = side;
        }

        if (arrived == null) return null;

        TileKey tile = getStationIndex().squareOf(turnedAt.getName());

        if (tile == null) return null;

        // A SIDE NO COPY OF THIS SQUARE CAN FACE is the state `OB-177` taught the menu to show and
        // `flipFacing` gives up on in silence (DIR-C4).  Writing it would record a facing the build
        // cannot hold and `moveOntoFacingCopy` would find nothing to stand the train on.
        if (!facingChoices(tile).contains(arrived)) return null;

        setFacing(tile, arrived);

        moveOntoFacingCopy(running, locomotive, tile, arrived);

        return tile;
    }
    /**
     * Turns a placed locomotive round on the graph, because the railway turned it (Adam, 2026-09-06).
     *
     * Adam: **"changing the direction on the graph itself does not emit a locomotive direction
     * command.  But a locomotive direction command WILL update the direction on the graph if it does
     * not match."**
     *
     * **One way only, and that is the design.** The decoder is what the railway actually does; the
     * setup is a record of it. A record that argued back would drive trains because somebody opened a
     * menu - so `setFacing` writes nothing to the track, and this follows the track.
     *
     * **The caller decides that something changed**, not this method. A facing is a Side and a
     * direction is forwards-or-backwards; nothing maps one to the other, because "forward" is not
     * north - it is whichever way the decoder drives, and which way that points depends on how the
     * model sits on the rails. The one thing that IS knowable is that a direction command reverses
     * whatever was true before, so the window watches for the change and this performs the
     * consequence. That needs nothing stored, and so needs no migration: a railway upgraded to this
     * version behaves correctly from its first direction command.
     *
     * **What flipping means on a curve.** Not the compass opposite: a train on a curve joining north
     * to east faces one of those two, and turning it round makes it face the other. That is exactly
     * `facingChoices`, which already knows the geometry - so the new facing is "the other choice",
     * never a direction this square has no track in.
     *
     * **Left alone where the answer is not obvious**: a locomotive that is not placed, a square where
     * neither the railway nor the setup says which way it faces, and a square offering other than two
     * facings - where "the other one" does not mean anything. Those are reported by returning null
     * rather than guessed at.
     *
     * **AND THE RUNNING LAYOUT, on Adam's ruling of 2026-09-06** (`DIR-B3`): *"flipFacing should also
     * update the running layout."*
     *
     * Writing only the setup left two records of one fact with no arbiter.  A square is several
     * Points once it is split, and which copy a locomotive stands on is what decides where it can go
     * next - so the diagram's arrow flipped at once while `getPossiblePaths`, the right-click
     * destination list and `explainDestinations` all went on answering for the OLD facing, until
     * something rebuilt the layout.  Worse, `captureFromLayout` derives the facing from the copy the
     * locomotive is actually on and writes it back: opening an editor was enough to undo this
     * silently.
     *
     * Both writes are here, in one method, because that is the only arrangement in which they cannot
     * drift - which is the defect this repository keeps finding in itself.
     *
     * @param locomotive the locomotive that has just been turned
     * @param running the layout to move it on, or null to write the setup only
     * @return the square whose facing changed, or null when nothing did
     */
    public TileKey flipFacing(String locomotive, org.traincontrol.automation.Layout running)
    {
        if (locomotive == null) return null;

        // WHERE THE TRAIN IS, THEN WHERE THE SETUP THINKS IT IS (REG6-A2).
        //
        // This used to read `placedLocomotives()` alone - the SETUP - which names the square a train
        // set off from until the next `captureFromLayout` writes the arrival back.  That was harmless
        // while a direction change was followed the moment it arrived.  It stopped being harmless when
        // `ca0265f4` began deferring a reversal made DURING a run until the run ends: by then the
        // train is at the far end of its journey, the flip was aimed at the platform it left,
        // `moveOntoFacingCopy` found no train there and bailed, and `infoFacingFollowedDirection`
        // logged a correction that had not happened.
        //
        // The running layout is asked first because it is the one that knows.  The setup is still
        // walked afterwards, and still walked in full: DIR-C3 is the rule that a locomotive recorded
        // on two squares gets a follow on the square that CAN be decided rather than no follow at all,
        // and narrowing this to one square would give that back.
        java.util.List<TileKey> candidates = new java.util.ArrayList<>();

        if (running != null && getStationIndex() != null)
        {
            for (org.traincontrol.automation.Point point : running.getPoints())
            {
                if (point.getCurrentLocomotive() == null) continue;

                if (!locomotive.equals(point.getCurrentLocomotive().getName())) continue;

                TileKey where = getStationIndex().squareOf(point.getName());

                if (where != null && !candidates.contains(where)) candidates.add(where);
            }
        }

        // THE SETUP ONLY WHEN THE RAILWAY DOES NOT KNOW (REG7-A2).
        //
        // These used to be APPENDED to the running layout's answer, and the loop below skips any
        // square it cannot decide - `recorded == null` is a `continue`, not a stop.  So after a run:
        // the arrival square comes first and often has no recorded facing yet, the loop moves on, and
        // the next candidate is the square the train LEFT, which does have one.  The empty platform
        // it departed from then had its facing flipped, for a train that is not standing there, and
        // the log line said a direction had been followed.
        //
        // The setup names the departure square until the next `captureFromLayout`, so this is not an
        // exotic interleaving - it is the ordinary state of things between a run ending and the
        // editor next being opened.
        //
        // DIR-C3 is not weakened.  Its rule is that a locomotive recorded on TWO squares still gets a
        // follow on the one that CAN be decided rather than none at all, and that is about two SETUP
        // squares - which is exactly the case this still walks in full, when the railway has no
        // opinion.  What is removed is the setup overruling a railway that does.
        if (candidates.isEmpty())
        {
            for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
            {
                if (!locomotive.equals(placed.getValue())) continue;

                if (!candidates.contains(placed.getKey())) candidates.add(placed.getKey());
            }
        }

        for (final TileKey tile : candidates)
        {
            // THE WAY IT FACES ON THE RAILWAY, where it stands there (TDY3-A2, AUT3-A1): the copy it is on IS its
            // facing.  The setup's FACING is the heading it set off with until a capture writes the arrival back, so after
            // a run it is absent - and the reversal was dropped - or another train's - and the flip went the wrong way
            // while the log said it was followed.  The setup answers only where the railway does not know.
            Side recorded = facingOfTrainOnTheRailway(tile, locomotive, running);

            if (recorded == null) recorded = getFacing(tile);

            List<Side> choices = facingChoices(tile);

            // CARRY ON RATHER THAN GIVE UP (DIR-C3).
            //
            // This used to `return null` on the first square that could not be decided.  A locomotive
            // recorded on TWO squares - which the checks report, and which `captureFromLayout` and a
            // hand-edited file can both produce - therefore got no follow at all, including on the
            // square that could have been decided.  Measured: `moved=null facingAafter=null
            // facingBafter=E`, with neither square moved.
            if (recorded == null || choices.size() != 2) continue;

            // A RECORDED FACING THIS SQUARE CANNOT HOLD, which is exactly the state `OB-177` taught
            // the menu to show and this gave up on in silence (DIR-C4).  Two features shipped the same
            // day about one state, and only one of them knew it existed.
            //
            // Not flipped, because "the other one" means nothing when the recorded value is neither
            // of them - and not logged from here either: this class has no control station to log
            // through, and inventing a channel for one line would be the larger change.  The state is
            // NOT invisible: `facingsThatCannotBeHeld` reports it in the findings panel and the menu
            // shows it since `OB-177`.  What is worth knowing, and is why this is written down rather
            // than left implicit, is that the correction such a square most naturally gets - somebody
            // turning the train on the track - is the one gesture that will not take.
            if (!choices.contains(recorded)) continue;

            final Side now = choices.get(0) == recorded ? choices.get(1) : choices.get(0);

            setFacing(tile, now);

            moveOntoFacingCopy(running, locomotive, tile, now);

            return tile;
        }

        return null;
    }
    /**
     * The locomotive standing on a stored point, or null when none is (DIR-C6).
     *
     * One spelling of "a locomotive is here", so the walk that finds them and the walk that reads
     * them cannot disagree about what a blank name means.
     *
     * @param point the stored point
     * @return the name, or null
     */
    private static String namedLocomotiveOn(org.json.JSONObject point)
    {
        org.json.JSONObject standing = point == null ? null : point.optJSONObject("loc");

        if (standing == null) return null;

        String name = standing.optString("name", "");

        return name.trim().isEmpty() ? null : name;
    }

    /**
     * A square's stored point in the active configuration, or null.
     *
     * @param tile the square
     * @return the stored point
     */
    private org.json.JSONObject pointOf(TileKey tile)
    {
        String active = store.getActiveConfiguration();

        org.json.JSONObject configuration = active == null ? null : store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return null;

        return configuration.getJSONObject("points").optJSONObject(tile.toString());
    }
    /**
     * Which locomotive the active configuration records standing on each square.
     *
     * Read from the configuration rather than from the running layout on purpose: the configuration is
     * what the next build reads, so it is where a second placement can hide.  The running layout has
     * already refused to hold one twice by the time anybody could ask it.
     *
     * PAGES THE USER SWITCHED OFF ARE LEFT OUT, and leaving them in was a real fault. Every other check
     * in this class reads from the reducer, which is built with excluded pages already dropped; this
     * one reads raw JSON, so it alone could report a square that is not in play. The duplicate-
     * locomotive finding it feeds is an ERROR whose message says autonomy "refuses the whole setup" -
     * so a placement left behind on a page somebody excluded could refuse the railway and send them to
     * a page they had deliberately turned off in order to fix it.
     *
     * A placement on an excluded page is not wrong, either. Excluding a page does not move the trains
     * standing on it, and it is meant to be reversible: switch the page back on and the placement is
     * there, which is the point of keeping it.
     *
     * @return square to locomotive name, empty when nothing is loaded
     */
    private Map<TileKey, String> placedLocomotives()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        // THROUGH THE SHARED WALK, which is what it was extracted for (DIR-C6).
        //
        // `WK3-C3` folded four copies of these six lines into `tilesWhere` and said why: *"If one
        // gains a qualification - homes on excluded pages, squares the diagram no longer draws - the
        // others will not have it, and the button will act on a set the findings never mentioned."*
        // This was a fifth copy, left outside the helper written to stop there being five.
        //
        // Its own extra filter - an excluded page is not in play - stays here, because it belongs to
        // this question rather than to every walk of the map.
        for (TileKey tile : tilesWhere((key, point) -> namedLocomotiveOn(point) != null))
        {
            if (store.getExcludedPages().contains(tile.getPage())) continue;

            org.json.JSONObject point = pointOf(tile);

            if (point == null) continue;

            out.put(tile, namedLocomotiveOn(point));
        }

        return out;
    }

    public GraphReducer getReducer()
    {
        return reducer;
    }

    /**
     * What a caption looked like when it lived in the layout file, and the only thing that still reads
     * it: the one-time migration that brings those labels into the setup.  See migrateStationLabels.
     */
    public static final String STATION_LABEL_PREFIX = "Point:";

    /**
     * Every station that some square of some page is showing the name of.
     *
     * Squares, not names.  A caption points at the sensor it is about, so asking "is this station
     * labelled" no longer means matching text against text - which is what made it possible for a
     * caption to look live while naming a Point that had been renamed out from under it.
     *
     * All pages, including excluded ones: exclusion says autonomy will not route over a page, not that
     * the page has stopped being drawn, and a caption there is still on the user's screen.
     *
     * @return the sensors that have a caption somewhere
     */
    public Set<TileKey> getLabelledStationTiles()
    {
        return new LinkedHashSet<>(store.getCaptions().values());
    }

    /**
     * The station a caption on this square is about.
     *
     * @param captionTile the square the text sits on
     * @return the sensor's square, or null when nothing is captioned there
     */
    public TileKey getCaptionTarget(TileKey captionTile)
    {
        return store.getCaptionTarget(captionTile);
    }

    /**
     * Every square showing this station's name.
     *
     * @param stationTile
     * @return the caption squares, possibly none
     */
    public Set<TileKey> captionsFor(TileKey stationTile)
    {
        return store.captionsFor(stationTile);
    }

    /**
     * Every caption on the layout, as the square it is drawn on to the sensor it is about.
     * @return
     */
    public Map<TileKey, TileKey> getCaptions()
    {
        return store.getCaptions();
    }

    /**
     * Shows a station's name on a square, or stops showing it.
     *
     * @param captionTile where the text goes
     * @param stationTile the sensor it is about, or null to clear the square
     */
    public void setCaption(TileKey captionTile, TileKey stationTile)
    {
        // One station, one caption - decided HERE rather than at each door.
        //
        // There are three ways to caption a station: place it automatically, choose the square
        // yourself in the autonomy editor, and now drag the square it sits on in the track diagram
        // editor.  Only the first knew to remove the old one, so choosing a new square left the
        // station named twice on the diagram and nothing said which was current.
        //
        // Cleared before setting rather than after, so moving a caption onto a square that already
        // shows the same station is not a clear-then-set of the same entry.
        if (stationTile != null) clearCaptions(stationTile, captionTile);

        store.setCaption(captionTile, stationTile);
        touched();
    }

    /**
     * Forgets the captions belonging to a square that has just been deleted from the diagram.
     *
     * Both directions, because a square can be either end of a caption:
     *
     *   the square the text sits ON      its caption goes with it.  A caption may legitimately sit
     *                                    on blank space, so an EMPTY square keeps its caption - but a
     *                                    square somebody has just deleted is not blank, it is gone,
     *                                    and the difference is the instruction they gave.
     *   the station the text is ABOUT    every caption naming it goes.  Text pointing at track that
     *                                    no longer exists is the orphan this whole design removed.
     *
     * Without this the label outlived the track: it stayed where it was, naming nothing, with no way
     * to get rid of it - and putting any tile back on that square made it look like the new tile's
     * label, because a caption is drawn wherever its square is.
     *
     * @param tile the square being deleted
     * @return true when something was forgotten, so the caller can say so
     */
    public boolean forgetCaptionsAt(TileKey tile)
    {
        if (tile == null) return false;

        boolean any = false;

        if (store.getCaptionTarget(tile) != null)
        {
            store.setCaption(tile, null);
            any = true;
        }

        for (TileKey where : new LinkedHashSet<>(captionsFor(tile)))
        {
            store.setCaption(where, null);
            any = true;
        }

        if (any) touched();

        return any;
    }

    /**
     * Everything the setup holds, for a caller that may have to put it all back.
     *
     * @return a snapshot for restoreSetup
     */
    public org.json.JSONObject snapshotSetup()
    {
        return store.snapshotSetup();
    }

    /**
     * Notes what the setup looks like now, so an editing session that dies can be undone (OB-108).
     *
     * @return whether the note reached the disk
     */
    public boolean beginEditSession()
    {
        return store.rememberBeforeEdit(store.snapshotSetup());
    }

    /**
     * The editing session ended properly, so there is nothing to undo.
     */
    public boolean endEditSession()
    {
        return store.forgetBeforeEdit();
    }

    /**
     * Whether a pre-edit note is on disk that this build could not use.
     *
     * Asked after `revertUnfinishedEdit` returns false, which by itself does not say whether there was
     * nothing to do or something that could not be done.
     *
     * @return true when a note is there and was refused
     */
    public boolean unusableEditNote()
    {
        return store.hasUnfinishedEditNote() && store.unfinishedEdit() == null;
    }

    /**
     * Puts the setup back to before an editing session that never ended, if there was one.
     *
     * Called whenever a session is BUILT - which is at startup, and also on every page-set change,
     * diagram re-download, page combine and layout reload, because each of those throws the session
     * away and the next caller rebuilds it. The caller is responsible for not asking while an editor
     * is open; `TrainControlUI.getAutonomySession` says how and why.
     *
     * Almost always finds nothing, which is the point: the note is left behind only when the process
     * died with the layout editor open, and then disk holds a setup keyed to squares the diagram never
     * moved. The first version of this javadoc said "called once at startup", which is what made the
     * defects around it look impossible while reading it.
     *
     * @return true when something was put back
     */
    public boolean revertUnfinishedEdit()
    {
        org.json.JSONObject was = store.unfinishedEdit();

        if (was == null) return false;

        restoreSetup(was);

        store.forgetBeforeEdit();

        return true;
    }

    /**
     * Puts back a whole snapshot, and writes it out.
     *
     * @param was a snapshot from snapshotSetup
     * @return whether it reached the file
     */
    public boolean restoreSetup(org.json.JSONObject was)
    {
        if (was == null) return true;

        store.restoreSetup(was);

        rebuild();

        return saveQuietly();
    }

    /**
     * Everything the setup holds about one page, for the diagram editor's undo.
     *
     * @param page the page name
     * @return a snapshot to hand back to restorePage
     */
    public java.util.Map<String, Object> snapshotPage(String page)
    {
        return store.snapshotPage(page);
    }

    /**
     * Puts a page's setup back as it was, and rebuilds the graph from it.
     */
    public void restorePage(String page, java.util.Map<String, Object> snapshot)
    {
        if (snapshot == null) return;

        store.restorePage(page, snapshot);

        // Once - see moveTiles above
        touched();
    }

    /**
     * Writes the setup out without reconciling it against the diagram.
     *
     * For the diagram editor, which changes the setup a square at a time as tiles are moved.  Its
     * edits have to reach disk as they happen: nothing else saves this session, and the reset that
     * follows an edit to the diagram throws it away.  Reconciling is what must NOT happen here - the
     * diagram is being edited, so half of it disagrees with the setup at any given moment, and a
     * reconcile would delete everything on the half not yet caught up.
     *
     * @return whether it was written
     */
    public boolean saveQuietly()
    {
        try
        {
            saveWithoutReconciling();

            return true;
        }
        catch (java.io.IOException e)
        {
            return false;
        }
    }

    /**
     * Follows tiles being moved on the diagram, with everything written about them.
     *
     * The diagram editor calls this when it moves track.  Only the CAPTION used to follow, so a
     * station whose sensor was nudged one square kept its name floating over the new square and lost
     * everything else it was - the station designation, its facings, its arrival restrictions, its
     * length, its placement - because all of those are keyed by square and the square had changed.
     * The next reconcile then found a station on a square with no sensor and dropped it for good.
     *
     * The whole group at once: see AutonomyCompanionStore.moveTiles for why one at a time is unsafe.
     *
     * @param moves each square being vacated, and where it is going
     * @return true when anything moved
     */
    public boolean moveTiles(Map<TileKey, TileKey> moves)
    {
        return moveTiles(moves, null);
    }

    /**
     * One diagram edit: what moved, and what else was built over.
     *
     * See AutonomyCompanionStore.moveTiles for why these are one call rather than two.
     *
     * @param moves each square being vacated, and where it is going - may be null
     * @param builtOver squares whose track has been replaced - may be null
     * @return true when there was anything to do
     */
    public boolean moveTiles(Map<TileKey, TileKey> moves, java.util.Collection<TileKey> builtOver)
    {
        boolean any = builtOver != null && !builtOver.isEmpty();

        if (moves != null)
        {
            for (Map.Entry<TileKey, TileKey> move : moves.entrySet())
            {
                if (move.getKey() != null && move.getValue() != null
                    && !move.getKey().equals(move.getValue()))
                {
                    any = true;
                }
            }
        }

        if (!any) return false;

        // WHETHER ANYTHING IN THE STORE ACTUALLY CHANGED (X8V-B2).
        //
        // `any` above is only "the caller gave me something to look at", and it was what this method
        // returned.  Four call sites read the answer as "something was forgotten, so write the setup to
        // disk" - `delete` says so in as many words - so deleting a piece of plain track that no
        // station, name, length or facing had ever been written about rebuilt the graph over every page
        // and wrote every file of the setup.  `deleteSelection` does that once per picked square.
        boolean changed = store.moveTiles(moves, builtOver);

        // The graph is built from the squares, so it is now describing the old ones - and touched()
        // is what rebuilds it.  This used to call rebuild() again immediately afterwards, so every
        // move paid for two full passes: a fresh TileGraph over every page, a GraphReducer.reduce, and
        // an AutonomyBuilder naming run, twice.  Nothing between them could have changed.
        //
        // UNCONDITIONAL, BECAUSE THE REBUILD IS OWED TO THE DIAGRAM (N8-B2).
        //
        // It was made conditional on `changed` for everything except a move, on the grounds that there
        // is "nothing to rebuild from unless something was stored".  That is not so: the graph is traced
        // from the SQUARES, so building over a blank square changes it even though the store held
        // nothing about that square - measured, a placement on a blank square did not reach the tile
        // graph at all, and with this unconditional it does.  A paste, a fill, a palette drop and a
        // clear all reach here with squares that were empty.
        //
        // The cost that made it conditional is still gated, and it was always the right thing to gate:
        // what is expensive is WRITING the setup, and every caller decides that from the return value
        // below - `delete` says so in as many words.  A rebuild is memory; a write is every file of the
        // setup, in a folder under OneDrive.
        //
        // And the gesture that made even the rebuild expensive asks once now: `deleteSelection` used to
        // call this per picked square, so a rubber-band delete paid for a fresh TileGraph over every
        // page once per square.
        touched();

        return changed;
    }

    /**
     * Squares whose track has been replaced by other track.
     *
     * What was written about a square describes the tile that WAS there - a station is a particular
     * sensor, a length is a particular piece of rail - so when a different tile is written over it,
     * the setup is describing something that is gone.  Reconcile cannot catch this on its own: it
     * drops setup from squares that are now EMPTY, and one of these is not empty, it is occupied by
     * something else.
     *
     * @param tiles the squares built over
     * @return true when there was anything to forget
     */
    public boolean forgetTiles(java.util.Collection<TileKey> tiles)
    {
        return moveTiles(null, tiles);
    }

    /**
     * One tile, for the single-tile drag.
     */
    public boolean moveTile(TileKey from, TileKey to)
    {
        Map<TileKey, TileKey> one = new LinkedHashMap<>();

        one.put(from, to);

        return moveTiles(one);
    }

    /**
     * Moves a station's caption from one square to another, for the track diagram editor.
     *
     * Dragging a tile carries whatever was written on it.  Without this, rearranging a diagram meant
     * every caption on every square that moved had to be placed again by hand, which on a real layout
     * is most of the reason not to rearrange it.
     *
     * @param from the square being vacated
     * @param to where it is going
     * @return true when a caption actually moved, so the caller can say so
     */
    public boolean moveCaption(TileKey from, TileKey to)
    {
        if (from == null || to == null || from.equals(to)) return false;

        TileKey station = store.getCaptionTarget(from);

        if (station == null) return false;

        store.setCaption(from, null);

        // Through setCaption, so anything already captioning that station elsewhere - including
        // whatever was on the destination square - goes with it.
        setCaption(to, station);

        return true;
    }

    /**
     * The page a key names, or null if this session has never heard of it.
     */
    private LayoutDiagram pageOf(TileKey tile)
    {
        for (LayoutDiagram page : pages)
        {
            if (page.getName().equals(tile.getPage())) return page;
        }

        return null;
    }

    /**
     * Puts a station's name on the diagram where it will be readable, if it has no label yet.
     *
     * Where depends on how the track lies.  A square whose rails run east-west has room for the name
     * beside them, so the label goes ON it.  A square whose rails run north-south, or round a corner,
     * has the name sitting across the track instead - so it goes on the square BELOW, which is where a
     * platform name is written on a real diagram.
     *
     * Placed only when the square below is free, and never over somebody's own caption: a diagram is
     * the user's drawing before it is autonomy's data.  Where there is nowhere to put it, nothing
     * happens and the "not shown anywhere" warning still says so.
     *
     * @param tile the station
     * @return what happened, so a caller can say why nothing did
     */
    public String placeCaption(TileKey tile)
    {
        // The authored name, not the generated one.  A square marked as a station a moment ago has
        // only the coordinate the reducer invented for it, and "1 - Main 12,7" written across a track
        // plan is worse than no caption at all - the caption goes on when the station is NAMED.
        String name = store.getPointName(tile);

        if (name == null || name.trim().isEmpty()) return "autosetup.ui.labelNotNamedYet";

        // A station already shown somewhere is MOVED, not refused.
        //
        // Asking to show a name is asking for it to be here, and answering "it is already somewhere"
        // left the user to find and delete the old one first - or, once imports began captioning every
        // station on its own square, made the action do nothing at all on a freshly imported setup.
        //
        // The old ones are cleared further down, once somewhere new has actually been found, so a
        // station with nowhere to go keeps the caption it had rather than losing it to a move that
        // then failed.

        LayoutDiagram page = pageOf(tile);

        if (page == null || graph == null) return "autosetup.ui.labelNoDiagram";

        LayoutDiagramComponent here = page.getComponent(tile.getX(), tile.getY());

        if (here == null) return "autosetup.ui.labelNoDiagram";

        // Beside the platform where there is room, on it where there is not.  A caption written on the
        // sensor sits across the rails; on the plain track next to it, it sits alongside them, which is
        // where a station name goes on a real diagram.
        //
        // Below for a station lying north-south, left for one lying east-west, then the other three -
        // a preference, not a rule, because the preferred square is usually occupied by something.
        List<Side> sides = labelSides(tile);

        // First choice: connected plain track running straight through.
        for (Side side : sides)
        {
            TileKey at = neighbour(tile, side);

            if (!connects(tile, side, at)) continue;

            LayoutDiagramComponent next = page.getComponent(at.getX(), at.getY());

            // Nor a square already carrying a caption: one square, one caption
            if (next == null || next.hasLabel() || next.isFeedback()) continue;

            if (store.getCaptionTarget(at) != null) continue;

            // Straight THROUGH rather than of type STRAIGHT.  A signal or an uncoupler is a plain
            // piece of running line with a fitting on it, and beside a platform there is often
            // nothing else - insisting on the bare type found no square at all on a real layout.
            if (!runsStraightThrough(next)) continue;

            setCaption(at, tile);

            return null;
        }

        // Second choice: an empty square next to it.  Blank space beside a platform is the most readable
        // place of all; it is simply rarer than track.  Nothing is added to the diagram to hold it - the
        // caption is autonomy's, and it is drawn on whatever square it names.
        for (Side side : sides)
        {
            TileKey at = neighbour(tile, side);

            // Inside the part of the page the running diagram DRAWS, which is the box around its
            // components rather than the whole grid.  getComponent cannot tell us: it answers null both
            // for a blank square and for one off the edge, and this loop reads null as "free".  So a
            // station against an edge had its caption filed one square outside the drawn area - shown
            // in the editor, which pads the grid, and never shown on the diagram, while the "not shown
            // anywhere" warning went quiet because a caption did exist.
            if (at.getX() < page.getMinx() || at.getY() < page.getMiny()) continue;
            if (at.getX() > page.getMaxx() || at.getY() > page.getMaxy()) continue;

            if (page.getComponent(at.getX(), at.getY()) != null) continue;

            if (store.getCaptionTarget(at) != null) continue;

            setCaption(at, tile);

            return null;
        }

        // Last resort: the sensor itself.  Across the rails is not ideal, and it is still better than
        // a station with no name anywhere - which is the thing the checks complain about.
        if (!here.hasLabel())
        {
            setCaption(tile, tile);

            return null;
        }

        return "autosetup.ui.labelNoRoom";
    }

    /**
     * Forgets wherever this station was being shown, so that placing it again leaves exactly one.
     *
     * Called only once somewhere new has been found: clearing first and then failing to place would
     * answer "show this name" by removing the name that was there.
     *
     * @param station
     */
    private void clearCaptions(TileKey station, TileKey except)
    {
        for (TileKey where : new LinkedHashSet<>(captionsFor(station)))
        {
            if (where.equals(except)) continue;

            store.setCaption(where, null);
        }
    }

    /**
     * Whether leaving this square by that side actually lands on that one.
     *
     * Connected, not merely adjacent: track that happens to pass the end of a platform is not the
     * platform road, and a name on it would point at the wrong line.
     */
    private boolean connects(TileKey tile, Side side, TileKey to)
    {
        if (graph == null) return false;

        TileGraph.Landing landing = graph.landing(tile, side);

        return landing != null && to.equals(landing.getTile());
    }

    /**
     * Whether a square's track runs straight through it - one route, joining two opposite sides.
     */
    private boolean runsStraightThrough(LayoutDiagramComponent component)
    {
        List<Route> routes = TilePorts.ports(component.getType(),
            component.getOrientation(), 0);

        if (routes.size() != 1) return false;

        Route route = routes.get(0);

        return route.getA() != null && route.getB() != null
            && route.getA() == route.getB().opposite();
    }

    /**
     * The sides to try, best first: below for a station lying north-south, left for one lying
     * east-west.
     */
    private List<Side> labelSides(TileKey tile)
    {
        boolean vertical = false;

        // Asked of the GRAPH, not of the port map directly.  ports() wants a state index, and a sensor
        // that happens to be triggered reports state 1 - which is past the end of the one state a
        // feedback tile has, so the call comes back empty and the square looks like it has no track on
        // it at all.  The graph has already settled that question.
        for (Route route : graph.getRoutes(tile).values())
        {
            if (route.touches(Side.N) && route.touches(Side.S)) vertical = true;
        }

        return vertical
            ? java.util.Arrays.asList(Side.S, Side.N, Side.W, Side.E)
            : java.util.Arrays.asList(Side.W, Side.E, Side.S, Side.N);
    }

    /**
     * The square on the given side of this one.  North is up, so it is the smaller y.
     */
    private TileKey neighbour(TileKey tile, Side side)
    {
        switch (side)
        {
            case N: return new TileKey(tile.getPage(), tile.getX(), tile.getY() - 1);
            case S: return new TileKey(tile.getPage(), tile.getX(), tile.getY() + 1);
            case E: return new TileKey(tile.getPage(), tile.getX() + 1, tile.getY());
            default: return new TileKey(tile.getPage(), tile.getX() - 1, tile.getY());
        }
    }

    /**
     * Brings captions written into the diagram as "Point:<name>" labels across into the setup, once.
     *
     * They used to live in the layout file, bound to a Point by NAME, and every trouble captions had
     * came from that: a rename had to rewrite every page showing the name, a station split into several
     * Points was called none of them, and a name that no longer existed left a caption that looked live
     * and did nothing.  Adam’s sample layout carried four of those last - BottomMainCTerm and the
     * rest, left behind by the hand-written configuration this feature replaced.
     *
     * A label naming a station this setup knows becomes a caption keyed to that station’s SQUARE.
     * One naming nothing is dropped, on the author’s instruction: it points at track that does not
     * exist, and drawing it taught the reader that a caption might mean nothing.
     *
     * The setup is written BEFORE the pages are.  If the order were the other way round and a page write
     * failed, the labels would be gone from the file and the captions absent from the setup - the
     * captions would simply have been deleted.  This way a failure leaves both, and the migration runs
     * again next time and reaches the same answer.
     *
     * This is the last time autonomy writes to a layout file at all.
     *
     * @return the pages that could not be written, empty when all was well
     */
    private List<String> migrateStationLabels()
    {
        Map<LayoutDiagram, List<LayoutDiagramComponent>> found = new LinkedHashMap<>();

        boolean migrated = false;

        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || component.getLabel() == null) continue;

                if (!component.getLabel().startsWith(STATION_LABEL_PREFIX)) continue;

                if (!found.containsKey(page)) found.put(page, new ArrayList<LayoutDiagramComponent>());

                found.get(page).add(component);
            }
        }

        if (found.isEmpty()) return new ArrayList<>();

        // How many captions each page gave up, so the total can follow the writes (VD9-C20).
        Map<LayoutDiagram, Integer> takenFrom = new LinkedHashMap<>();

        for (Map.Entry<LayoutDiagram, List<LayoutDiagramComponent>> entry : found.entrySet())
        {
            for (LayoutDiagramComponent component : entry.getValue())
            {
                String name = component.getLabel().substring(STATION_LABEL_PREFIX.length());

                TileKey where = new TileKey(entry.getKey().getName(),
                    component.getX(), component.getY());

                TileKey station = tileNamed(name);

                if (station != null)
                {
                    store.setCaption(where, station);

                    migrated = true;

                    // COUNTED PER PAGE, and added to the total only once that page has been written
                    // (VD9-C20).  The count and the page list are printed in one sentence - "(N of
                    // them) ... removed from these page files: ..." - and this used to increment here,
                    // before any page was saved.  A page whose write threw put its captions inside the
                    // count while the page itself was absent from the list and its labels were still
                    // on disk, so the two halves of that sentence described different sets.
                    Integer soFar = takenFrom.get(entry.getKey());

                    takenFrom.put(entry.getKey(), soFar == null ? 1 : soFar + 1);
                }
            }
        }

        List<String> failures = new ArrayList<>();

        // Nothing named a station this setup knows, so there is nothing to migrate and nothing to
        // write.  Saving regardless created a setup file for a layout with no autonomy at all, and
        // rewrote every page that merely CONTAINED a label - and because an unrecognised label is
        // deliberately left where it is, the same pages were found and rewritten again at every
        // launch from then on.  The sample layout's orphan labels made that the shipped default.
        if (!migrated) return failures;

        try
        {
            store.save();
        }
        catch (IOException e)
        {
            // The setup could not be written, so the labels stay where they are and this runs again
            failures.add(String.valueOf(e.getMessage()));

            return failures;
        }

        for (Map.Entry<LayoutDiagram, List<LayoutDiagramComponent>> entry : found.entrySet())
        {
            boolean changed = false;

            for (LayoutDiagramComponent component : entry.getValue())
            {
                // Only the ones that became a caption.  A label naming a station this setup has never
                // heard of is left exactly where it is: stripping it would delete the only record that
                // it ever existed, on the strength of this program not recognising a name - and the
                // author's instruction to drop orphans was about not DRAWING them, not about editing
                // somebody's diagram to remove them.
                String was = component.getLabel();

                if (was == null || !was.startsWith(STATION_LABEL_PREFIX)) continue;

                if (tileNamed(was.substring(STATION_LABEL_PREFIX.length())) == null) continue;

                // Emptied, which is how a text square stops existing: the exporter does not write a TEXT
                // element with no text.  Anything the file said about that square which this program
                // cannot model is still written, so emptying it is not the same as deleting the line.
                component.setLabel("");

                changed = true;
            }

            // Only pages this actually changed.  A page holding nothing but unrecognised labels is
            // looked at and left alone; writing it would rewrite a file for no reason, and the labels
            // that caused the visit are still there to cause the next one.
            if (!changed) continue;

            try
            {
                entry.getKey().saveChanges(null, false);

                migratedPages.add(entry.getKey().getName());

                Integer took = takenFrom.get(entry.getKey());

                if (took != null) migratedCaptions += took;
            }
            catch (Exception e)
            {
                failures.add(entry.getKey().getName() + ": " + e.getMessage());
            }
        }

        return failures;
    }

    /**
     * The square of the station carrying this authored name, or null if no station does.
     */
    private TileKey tileNamed(String name)
    {
        if (name == null) return null;

        // Every named square, not only the ones the reduction has Points for.
        //
        // The reduction is built without the excluded pages, so a label naming a station on one of them
        // matched nothing - and the migration then stripped the label anyway, which is the loss twice
        // over: the caption was never recorded and the label it came from is gone.  Excluding a page has
        // to be reversible, and this is the one place that quietly was not.
        for (Map.Entry<String, String> entry : store.getPointNames().entrySet())
        {
            if (name.equals(entry.getValue()))
            {
                TileKey tile = AutonomyCompanionStore.parseTileKey(entry.getKey());

                if (tile != null) return tile;
            }
        }

        return null;
    }

    public List<LayoutDiagram> getPages()
    {
        return Collections.unmodifiableList(pages);
    }

    /**
     * The per-point keys a configuration owns: everything operational parseAuto accepts on a point.
     * Structural keys (name, station, s88, coordinates) belong to the reduction and are not here.
     *
     * Terminus and reversing are deliberately absent: both are DERIVED from the three switches at build
     * time, so the running graph carries the builder's answer rather than the user's.  Capturing one
     * would write "reversing" onto the square somebody marked "trains can turn round here", and the
     * next build would then reverse every train that passed it.
     */
    private static final List<String> POINT_OPERATIONAL_KEYS = java.util.Arrays.asList(
        "loc", "active", "maxTrainLength", "speedMultiplier",
        "priority", "home", "excludedLocs",
        // WHERE A STANDING TRAIN'S TAIL LIES, captured with the train (WK7-B1).  A side and a road written by a
        // run lived only on the running layout, so a restart put every driven train back with neither and its
        // tail blocked nothing past the platform.  Replaced like the rest - and removed where the layout no
        // longer carries them, which is a square whose train has left.
        "arrivedFrom", "arrivedAlong");

    /**
     * Squares where trains turn round whose track has no recorded length (Adam, 2026-09-01).
     *
     * "Add notices to the autonomy editor to add track lengths between stations and switches that
     * accept reversal (if any other track length is set anywhere)."
     *
     * A train reversing into a berth shorter than itself ends up standing across the switch behind it.
     * `Layout.isPathClear` refuses that now - but it can only refuse what it can measure, and
     * unmeasured track is unknown rather than short.
     *
     * **What this set is, exactly** (`SVN-B1`, corrected by `VD9-B6`). The first attempt at this
     * paragraph said the guard "walks every edge and gives up the moment one has no length" and that a
     * reversal square with no number of its own leaves it under-counting rather than blind. Both are
     * false, and the second is backwards. Traced against the two methods rather than against a summary
     * of them:
     *
     * - `Layout.measuredRoomAtTheEndOf` walks the path's **edges** backwards and **stops at the
     *   first one that crosses a switch**. Before that it needs each edge's own `getLength() > 0`; at
     *   it, `getRoomAtTheEnd() >= 0`. It never looks past the last switch, so an unmeasured stretch
     *   beyond it blinds nothing.
     * - `GraphReducer.roomAfterTheLastSwitch` is that number: one edge's **tiles**, walked backwards
     *   from the end, stopping at the first switch, summing what is measured and treating an
     *   unmeasured tile as nothing.  `-1` only when NOT ONE of them has a length, which is genuinely
     *   no information rather than a small number.  (This said "`-1` if any of them has no length"
     *   until 2026-09-08, which was Adam's rule until 2026-09-06: MON-C6.)
     * - `unmeasuredAfterTheLastSwitch` is the same walk returning the tiles, which is what this asks
     *   for. **So for the last edge the notice and the guard agree by construction.**
     *
     * **The square's own length is the first thing that stretch contains**, and asking for it is still
     * right, though no longer for the reason written here before: the `measured` flag it described was
     * deleted on 2026-09-06.  An unmeasured berth square now contributes nothing to the total rather
     * than blinding the answer, so the guard under-counts by exactly that square rather than declining
     * to judge - which is a smaller fault and still one worth a notice.
     *
     * **Two ways they still disagree, and both are narrower than "anywhere earlier":**
     *
     * - **Under-reports.** When the arriving edge crosses no switch the guard carries on to the edge
     *   before it, and this only ever looks at edges arriving at the reversal square. Nothing names
     *   the earlier one, so a notice can be cleared with the guard still blind.
     * - **Over-reports.** On an edge that crosses no switch the guard asks only that the edge's own
     *   length - a sum over its tiles - be positive, while this names every unmeasured tile on it. A
     *   square listed for that reason is worth measuring and is not blinding anything.
     *
     * Neither is worth chasing without a ruling: closing the first means walking the graph backwards
     * from every reversal square, which changes what the count means, and Adam has already accepted
     * the count as it stands ("20 warnings sounds OK").
     *
     * **The condition is his, and it is what stops this being a nag.** A railway that records no
     * lengths anywhere has decided not to model them, and every reversing square on it would be
     * listed for something nobody is trying to do. One that records some has started, and these are
     * the ones that matter most.
     *
     * **ONE ENTRY PER REVERSAL SQUARE** (OB-171).  Adam: *"warnings ... fire on many tiles along a
     * line.  Dedupe them, one per segment between a switch and a station."*  This used to return every
     * unmeasured square in the stretch behind a reversal, each of which became its own notice carrying
     * the same sentence.  The completeness is kept - the notice does not go until the whole stretch is
     * measured, because that is what the guard needs - and what is returned is the square trains turn
     * at, mapped to how many squares are still missing a length.
     *
     * @return the squares to ask about, each mapped to the number of unmeasured squares its guard
     *         needs, empty when the layout measures nothing
     */
    public java.util.Map<TileKey, Integer> reversalsWithoutLength()
    {
        java.util.Map<TileKey, Integer> out = new java.util.LinkedHashMap<>();

        if (store == null || reducer == null) return out;

        // Asked of what has been recorded, not of the graph's points: a length belongs to a SQUARE,
        // and the plain track between two sensors - the thing somebody actually measures - carries no
        // point at all. Scanning points answered "this layout measures nothing" for a layout that had
        // measured everything except its sensors.
        if (!store.measuresAnyTrack()) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            // Anywhere a train may turn round - compulsory or optional, the room behind it is the
            // same question.
            if (!isTurnAround(tile)) continue;

            java.util.Set<TileKey> missing = new java.util.LinkedHashSet<>();

            // THE REVERSAL SQUARE IS PART OF THE STRETCH, not a separate thing to ask for (MT-364).
            //
            // Adam, 2026-09-12: *"I set the track at 10,10 to length 2, so now we know that the track
            // to TunnelLongPark is 2.  But it is still asking for the tile at 10,9 (TunnelLongPark) to
            // get a length."*
            //
            // 10,9 is the reversal square, and it was added HERE - before the walk below was consulted
            // at all - so measuring the track behind it could never have silenced the notice.  The walk
            // already includes the end tile in the stretch it measures, exactly as
            // `roomAfterTheLastSwitch` does, so this line was both a second answer to the same question
            // and the stricter of the two.
            //
            // Kept ONLY where no edge arrives, which the walk cannot speak for: a square with no
            // approach has no stretch to measure, and saying nothing there would be silence about a
            // square that genuinely has no number.
            boolean anApproachExists = false;

            // AND THE TRACK BEHIND IT, BACK TO THE SWITCH (Adam's ruling 2, 2026-09-02).
            //
            // Asking for the reversal square alone left the guard unable to fire: it needs the stretch
            // that bounds the train, and a stretch with nothing measured in it answers "unknown".  So
            // somebody could clear every notice this raised and still have a guard that judged nothing
            // - which is what `D24-C7` and `TCX-B2` both said, and what he was shown when he answered
            // "20 warnings sounds OK".
            //
            // **WHY IT STILL REPORTS A PARTLY MEASURED STRETCH** (MON-C6, 2026-09-08).  The sentence
            // above used to read "returns unknown if ANY tile in it is unmeasured", which was Adam's
            // rule until 2026-09-06.  It is not any more: an unmeasured tile contributes nothing and
            // the rest still counts.
            //
            // The notice is kept because the cost simply changed shape.  A half-measured stretch no
            // longer blinds the guard - it makes it PESSIMISTIC, since the missing tiles count as zero
            // - so a train that would fit is refused rather than admitted.  That is the safe direction
            // and it is still worth telling somebody about, but it is a different sentence from the one
            // this was written for, and whether it earns its place in a list Adam has called a wall is
            // his call: it is put to him on MT-305.
            //
            // WHICH squares comes from ruling 1: the stretch after the LAST switch on the edge
            // arriving here, because that is the one the room is measured over.  Not every segment of
            // the run in, which was the question put to him and is more than the rule needs.
            for (GraphReducer.ReducedEdge arriving : reducer.getEdges())
            {
                if (!arriving.getEnd().equals(tile)) continue;

                // NOT A SIDE NO TRAIN ARRIVES BY (MT-552; Adam, 2026-09-24, of RampDown and BottomMainPost: *"they only
                // accept arrivals from one side"*).  A barred side is an approach no train uses, so nothing about its
                // track can refuse one - and the walk the rule stands for never starts from it.
                if (getBarredArrivals(tile).contains(arriving.getEntrySide())) continue;

                anApproachExists = true;

                // AN ANSWERED 0 IS NOT MISSING (Adam, 2026-09-23: "stop listing answered zeros as missing").  The
                // guard still reads it as nothing; the operator has answered it, so it is not asked for again.
                for (TileKey unmeasured : reducer.unmeasuredAfterTheLastSwitch(arriving))
                {
                    if (!store.isTileLengthAnswered(unmeasured)) missing.add(unmeasured);
                }
            }

            if (!anApproachExists && store.getTileLength(tile) <= 0 && !store.isTileLengthAnswered(tile))
            {
                missing.add(tile);
            }

            // A SET, so a square reached by two approaches is counted once - the notice says how many
            // squares need a number, and the same square twice is one square.
            if (!missing.isEmpty()) out.put(tile, missing.size());
        }

        return out;
    }

    /**
     * One piece of track to measure: the squares of a leg between two fixed points - a sensor or a switch - with neither
     * switch in it (Mass Assign Lengths).
     *
     * **Every leg, cut at switches** - Adam's ruling on review MAL-B1, 2026-09-16.  The first version asked only for the
     * track past the last switch, because the room rule stops there; but the FR-087 allowance adds whole legs back until
     * a reversal, and the tail and berth walks spend the switch square and the track before it, so on his railway every
     * leg is read.  Cutting at switches keeps every rule exact: an even share of a piece never lands on the far side of a
     * switch, where the room rule would not count it.
     *
     * **Each square is in exactly one piece.**  A sensor square that ends several legs belongs to the first piece that
     * reaches it, so no answer can be overwritten by a later one (MAL-C2).
     */
    public static final class Stretch
    {
        private final java.util.List<TileKey> tiles;
        private final TileKey from;
        private final TileKey to;

        Stretch(java.util.List<TileKey> tiles, TileKey from, TileKey to)
        {
            this.tiles = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(tiles));
            this.from = from;
            this.to = to;
        }

        /**
         * @return the squares, in the order the leg runs
         */
        public java.util.List<TileKey> getTiles()
        {
            return tiles;
        }

        /**
         * @return the fixed point at one end - a sensor square, or the switch the piece stops short of - for the prompt
         */
        public TileKey getFrom()
        {
            return from;
        }

        /**
         * @return the fixed point at the other end
         */
        public TileKey getTo()
        {
            return to;
        }

        @Override
        public String toString()
        {
            return tiles + " between " + from + " and " + to;
        }
    }

    /**
     * Every piece of track a length rule reads, on the pages autonomy uses: every leg, cut at its switches.
     *
     * **WHY EVERY LEG** (MAL-B1).  `Layout.measuredRouteIn` - the FR-087 allowance - adds each leg's whole length back from
     * a station until a reversal or a wholly unmeasured leg; `Layout.walkOneTail` and `Layout.whyABerthCannotHoldIt`
     * spend a train's length over every square of a leg, the switch included.  Measured on Adam's railway, that reach is
     * every one of its 75 legs, so the question was only how to cut them, and he ruled: at switches.
     *
     * **NOT the question `reversalsWithoutLength` asks, deliberately.**  That notice is said unprompted and asks less -
     * how loud the findings list may be is Adam's call (MT-305).  This is a tool somebody opens to measure everything.
     *
     * @return the pieces, ordered by page and position
     */
    public java.util.List<Stretch> stretchesALengthRuleReads()
    {
        java.util.List<Stretch> out = new java.util.ArrayList<>();

        if (store == null || reducer == null || getGraph() == null) return out;

        java.util.Set<TileKey> placed = new java.util.HashSet<>();

        // A SQUARE TWO LEGS RUN OVER IS CUT OUT, as a switch is (Adam, 2026-09-19, SET-B2).
        java.util.Set<TileKey> shared = sharedSquaresALengthRuleReads();

        for (java.util.List<TileKey> leg : legsOnce())
        {
            java.util.List<TileKey> piece = new java.util.ArrayList<>();
            int firstIndex = -1;

            for (int i = 0; i <= leg.size(); i++)
            {
                // A ROUTE TILE IS IN NO PIECE AND DOES NOT CUT ONE (Adam, 2026-09-23, OB-273: *"a route tile should
                // not need or accept a length.  it just implicitly connects things as if it were a crossing."*).  It
                // is passed over, so the piece runs on across it and its length is shared over the track either
                // side: his three squares and a route tile with 4 typed come out 2, 1, 1.
                if (i < leg.size() && takesNoLength(leg.get(i))) continue;

                boolean boundary = i == leg.size()
                    || isSwitchSquare(leg.get(i)) || shared.contains(leg.get(i));

                if (!boundary)
                {
                    if (firstIndex < 0) firstIndex = i;

                    // THE FIRST PIECE THAT REACHES A SQUARE KEEPS IT: a sensor square ends several legs, and if it
                    // were in each of them the second answer would overwrite the first (MAL-C2).
                    if (placed.add(leg.get(i))) piece.add(leg.get(i));

                    continue;
                }

                if (firstIndex >= 0 && !piece.isEmpty())
                {
                    TileKey from = firstIndex > 0 ? leg.get(firstIndex - 1) : leg.get(0);
                    TileKey to = i < leg.size() ? leg.get(i) : leg.get(leg.size() - 1);

                    out.add(new Stretch(piece, from, to));
                }

                piece = new java.util.ArrayList<>();
                firstIndex = -1;
            }
        }

        java.util.Collections.sort(out, (one, two) -> compareSquares(one.getTiles().get(0), two.getTiles().get(0)));

        return out;
    }

    /**
     * Every switch square on a leg autonomy uses.  A switch is in no piece - its share would sit where the room rule
     * does not count it - so switches are asked for on their own, one turnout length for a page (Adam, 2026-09-16:
     * "One length for all switches").
     *
     * @return the switch squares, ordered by page and position
     */
    public java.util.Set<TileKey> switchesALengthRuleReads()
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        if (store == null || reducer == null || getGraph() == null) return new java.util.LinkedHashSet<>(out);

        java.util.Set<TileKey> seen = new java.util.HashSet<>();

        for (java.util.List<TileKey> leg : legsOnce())
        {
            for (TileKey tile : leg)
            {
                if (isSwitchSquare(tile) && seen.add(tile)) out.add(tile);
            }
        }

        java.util.Collections.sort(out, this::compareSquares);

        return new java.util.LinkedHashSet<>(out);
    }

    /**
     * The pieces still needing a length: those whose squares add up to nothing.
     *
     * The WHOLE piece, not each square - Adam, 2026-09-06: *"It is only indeterminate if the entire logical segment has
     * length 0."*  A square inside a measured piece may rightly hold 0: a short piece drawn with several squares has
     * fewer units than squares.  The store keeps 0 and "not given" as the same thing (`setTileLength` removes a length
     * of 0), so there is no measured zero to tell apart.
     *
     * @return the pieces, ordered as `stretchesALengthRuleReads` orders them
     */
    public java.util.List<Stretch> stretchesNeedingALength()
    {
        java.util.List<Stretch> out = new java.util.ArrayList<>();

        for (Stretch stretch : stretchesALengthRuleReads())
        {
            if (needsALength(stretch)) out.add(stretch);
        }

        return out;
    }

    /**
     * The pieces still needing a length that have a square on this page - what Mass Assign Lengths walks from it.
     *
     * @param page the page being edited
     * @return the pieces
     */
    public java.util.List<Stretch> stretchesNeedingALengthOn(String page)
    {
        java.util.List<Stretch> out = new java.util.ArrayList<>();

        for (Stretch stretch : stretchesNeedingALength())
        {
            for (TileKey tile : stretch.getTiles())
            {
                if (tile.getPage() != null && tile.getPage().equals(page))
                {
                    out.add(stretch);

                    break;
                }
            }
        }

        return out;
    }

    /**
     * The switch squares on this page with no length, which Mass Assign Lengths asks for together.
     *
     * @param page the page being edited
     * @return the switches
     */
    public java.util.Set<TileKey> switchesNeedingALengthOn(String page)
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (TileKey tile : switchesALengthRuleReads())
        {
            if (tile.getPage() != null && tile.getPage().equals(page) && squareNeedsALength(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * Every square the Unmeasured Track display highlights: the squares of every piece that adds up to nothing, and
     * every switch with no length.
     *
     * @return the squares
     */
    public java.util.Set<TileKey> squaresNeedingALength()
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (Stretch stretch : stretchesNeedingALength()) out.addAll(stretch.getTiles());

        for (TileKey tile : switchesALengthRuleReads())
        {
            if (squareNeedsALength(tile)) out.add(tile);
        }

        // AND THE SQUARES TWO ROADS SHARE, which are in no piece and so would otherwise never be highlighted at all
        // although a length rule reads them on both roads (SET-B2).
        for (TileKey tile : sharedSquaresALengthRuleReads())
        {
            if (squareNeedsALength(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * The least whole length a piece can be given: 0 since OB-274, where a deliberate 0 is an answer that reads as
     * unmeasured.  It was one unit, on the reading that 0 is the same as no length at all.
     *
     * @param stretch the piece
     * @return the least whole length `assignStretchLength` accepts; a piece that already has a length, what it holds
     */
    public int leastWholeLengthOf(Stretch stretch)
    {
        if (stretch == null) return 0;

        int measured = measuredIn(stretch);

        return measured > 0 ? measured : 0;
    }

    /**
     * Shares a piece's whole length out over its squares (Adam, 2026-09-16: per stretch).
     *
     * Only a piece that adds up to nothing is written - one that has a length is measured, by the ruling above.  The
     * whole length is shared evenly; a square may get 0 where there are fewer units than squares.
     *
     * **ANY UNIT LEFT OVER GOES FIRST TO A SQUARE A TRAIN STANDS ON** (MAL-B2), which is where Adam puts the larger share
     * when he measures by hand - TunnelLongPark is 2 on its sensor square and 1 behind.  Every walk spends that square
     * first (OB-278), so where the unit goes decides only how many squares of THIS piece a short train's tail claims, and
     * no other road joins a piece between its switches.  From MAL-B2 to OB-278 the reason given was the opposite one -
     * that the square was never spent, so a unit on it pushed the tail further back - and the split did not change when
     * that stopped being true.  Past the standing squares the rest go in the order the leg runs; the room rule and the
     * FR-087 allowance read only the total, so for them the split changes nothing.
     *
     * @param stretch the piece
     * @param wholeLength its whole length, at least 1
     * @return whether it was written
     */
    public boolean assignStretchLength(Stretch stretch, int wholeLength)
    {
        if (stretch == null || wholeLength < 0 || measuredIn(stretch) > 0) return false;

        // A DELIBERATE 0 IS AN ANSWER (OB-274): every square of the piece is recorded as answered, so the walk does
        // not offer it again, and every length rule goes on reading it as unmeasured.
        if (wholeLength == 0)
        {
            for (TileKey tile : stretch.getTiles()) store.answerTileLengthZero(tile);

            touched();

            return true;
        }

        java.util.List<TileKey> order = new java.util.ArrayList<>();

        for (TileKey tile : stretch.getTiles())
        {
            if (store.isStation(tile) || isTurnAround(tile)) order.add(tile);
        }

        for (TileKey tile : stretch.getTiles())
        {
            if (!order.contains(tile)) order.add(tile);
        }

        int each = wholeLength / order.size();
        int over = wholeLength % order.size();

        for (int i = 0; i < order.size(); i++)
        {
            store.setTileLength(order.get(i), each + (i < over ? 1 : 0));
        }

        touched();

        return true;
    }

    /**
     * Gives one turnout length to every switch here that has none (Adam, 2026-09-16: "One length for all switches").
     * A switch that already has a length keeps it.
     *
     * @param switches the switch squares to give it to
     * @param length the length of one turnout, at least 1
     * @return whether it was written
     */
    public boolean assignSwitchLength(java.util.Collection<TileKey> switches, int length)
    {
        if (switches == null || switches.isEmpty() || length < 0) return false;

        boolean wrote = false;

        for (TileKey tile : switches)
        {
            if (store.getTileLength(tile) > 0) continue;

            // 0 ANSWERS IT (OB-274) - two switches back to back are his adjacent tracks - and reads as unmeasured.
            if (length == 0)
            {
                if (store.isTileLengthAnswered(tile)) continue;

                store.answerTileLengthZero(tile);
            }
            else
            {
                store.setTileLength(tile, length);
            }

            wrote = true;
        }

        if (wrote) touched();

        return wrote;
    }

    /**
     * The stations on this page that will still take a train of any length, in the order Mass Assign Max Train Lengths
     * asks about them: row by row, left to right.
     *
     * Adam, 2026-09-17: *"add a similar feature to walk stations that don't have a max length set up, so I can enter
     * it"*.  The same stations the `NO_MAX_TRAIN_LENGTH` notice lists, asked through `hasNoMaximumTrainLength` so the
     * walk and the notice cannot come to disagree about which they are - **but not behind the notice's gate**.  The
     * notice stays quiet on a railway that models no lengths, because there a list of every station is a list of things
     * that are not wrong; somebody who opens this walk has just said they are modelling them.
     *
     * @param page the page being edited
     * @return the station squares, each the square the right-click menu writes a maximum to
     */
    public java.util.List<TileKey> stationsWithoutAMaximumOn(String page)
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        if (reducer == null || store == null || page == null) return out;

        for (TileKey square : reducer.getPoints().keySet())
        {
            if (page.equals(square.getPage()) && hasNoMaximumTrainLength(square)) out.add(square);
        }

        out.sort(java.util.Comparator.comparingInt(TileKey::getY).thenComparingInt(TileKey::getX));

        return out;
    }

    /**
     * Gives a station its maximum train length, if it has none yet.
     *
     * **0 is refused**, because to the railway 0 is "any length" (`Point.validateTrainLength`) - the very setting the
     * walk exists to replace - and the store writes it as no setting at all.  A station that already has a maximum keeps
     * it, so a walk working from a list made before it began cannot overwrite one set in the meantime.
     *
     * @param station the station square
     * @param length the longest train that may stop there, at least 1
     * @return whether it was written
     */
    public boolean assignMaxTrainLength(TileKey station, int length)
    {
        if (station == null || length < 1 || !hasNoMaximumTrainLength(station)) return false;

        setPointProperty(station, "maxTrainLength", length);

        return true;
    }

    /**
     * Whether this square is a station on the running graph that will take a train of any length.
     *
     * @param square the square
     * @return true for a station with no maximum, or a maximum of 0
     */
    private boolean hasNoMaximumTrainLength(TileKey square)
    {
        if (reducer == null || store == null || !reducer.getPoints().containsKey(square) || !store.isStation(square))
        {
            return false;
        }

        Object value = getPointProperty(square, "maxTrainLength");

        return !(value instanceof Number) || ((Number) value).intValue() <= 0;
    }

    /**
     * Every leg once, whichever direction the reducer found it in: its start, its track and its end, in order, taken
     * from the direction whose key sorts first so the answer does not depend on edge order.
     */
    private java.util.List<java.util.List<TileKey>> legsOnce()
    {
        java.util.TreeMap<String, java.util.List<TileKey>> byRun = new java.util.TreeMap<>();

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            java.util.List<TileKey> line = new java.util.ArrayList<>();

            line.add(edge.getStart());

            for (GraphReducer.TileStep step : edge.getPath()) line.add(step.getTile());

            line.add(edge.getEnd());

            String key = sortedNames(line);

            String direction = String.valueOf(edge.getStart()) + " > " + edge.getEnd();

            java.util.List<TileKey> known = byRun.get(key);

            if (known == null || direction.compareTo(String.valueOf(known.get(0)) + " > " + known.get(known.size() - 1)) < 0)
            {
                byRun.put(key, line);
            }
        }

        return new java.util.ArrayList<>(byRun.values());
    }

    /**
     * The squares more than one leg runs OVER - crossings, and double curves whose both roads carry track.
     *
     * Adam, 2026-09-19, shown that such a square sat in one leg's piece and was missing from the other's:
     * *"For crossings: if its length is set, count that length once in each direction."*  That is what the reduction
     * already does - `GraphReducer.sumLength` adds every tile of every leg, so a crossing of 3 adds 3 to each road
     * over it - and it is only right if no leg's PIECE contains it, because a piece's whole length is shared over its
     * squares and would then be counted on the other road too.  So the square is cut out of every piece and asked for
     * on its own, exactly as a switch is (MAL-B1), and the two roads each measure what was typed for them plus it.
     *
     * **Two ROADS THAT SHARE METAL, not two legs.**  A crossing's roads cross: a train on one is on the other's
     * rail, which is why one length counts on both and why the square is asked for once.  A DOUBLE CURVE is the
     * opposite - its two curves never touch, which is why the reduction keys it per road (AUR-B1) - and it is here
     * only because both of its curves are measured by the one number the operator types for that square.  The two
     * rules are about different things and agree (OP2-C4).
     *
     * Every square in front of a switch is run over by each leg through that switch -
     * on the fixture behind `testEveryLegIsCutIntoPiecesAtItsSwitches`, the plain straight before the points is in
     * both legs - and such a square is ordinary track that belongs in a piece.  What makes a crossing different is
     * its geometry: `TilePorts` gives it two separate roads (`CROSSING` runs north-south and east-west, a
     * `DOUBLE_CURVE` two unconnected curves), so a train on one road passes over the other's rail.  Both halves are
     * required - the geometry, and legs actually running over it on more than one of those roads - so a double curve
     * with track on only one of its roads stays ordinary track.
     *
     * **Intermediate occurrences only.**  A sensor square ends several legs and so appears in several, which is what
     * MAL-C2's first-piece-keeps-it rule is for; it is an endpoint, never track a leg runs over (a Point tile is an
     * edge endpoint, never an intermediate square), so counting endpoints here would cut every piece at both ends.
     *
     * **Switches are not listed**, because they have their own step and are already cut out.
     *
     * @return the squares, ordered by page, row then column
     */
    public java.util.Set<TileKey> sharedSquaresALengthRuleReads()
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        if (store == null || reducer == null || getGraph() == null) return new java.util.LinkedHashSet<>(out);

        java.util.Map<TileKey, Integer> crossedBy = new java.util.LinkedHashMap<>();

        for (java.util.List<TileKey> leg : legsOnce())
        {
            // EVERY CROSSING COUNTS, INCLUDING TWO BY THE SAME LEG (SVA-C2).  A figure of eight runs one leg over
            // its own crossing twice - once on each road - and the square is then read twice by that leg's own
            // length, which is the very error this rule exists to stop.  Counting occurrences rather than legs
            // catches it; the ends are where the leg STOPS, so they are not track it runs over.
            for (int i = 1; i + 1 < leg.size(); i++) crossedBy.merge(leg.get(i), 1, Integer::sum);
        }

        for (java.util.Map.Entry<TileKey, Integer> entry : crossedBy.entrySet())
        {
            // Not a route tile with track on all four sides: it conducts like a crossing and takes no length
            // (OB-273), so it is not asked for one on its own either.
            if (entry.getValue() > 1 && !isSwitchSquare(entry.getKey())
                && !takesNoLength(entry.getKey())
                && getRoutes(entry.getKey()).size() > 1)
            {
                out.add(entry.getKey());
            }
        }

        java.util.Collections.sort(out, this::compareSquares);

        return new java.util.LinkedHashSet<>(out);
    }

    /**
     * The shared squares on this page with no length, which Mass Assign Lengths asks for together.
     *
     * @param page the page being edited
     * @return the squares
     */
    public java.util.Set<TileKey> sharedSquaresNeedingALengthOn(String page)
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (TileKey tile : sharedSquaresALengthRuleReads())
        {
            if (tile.getPage() != null && tile.getPage().equals(page) && squareNeedsALength(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * @param tile a square
     * @return whether it takes no length (`TilePorts.takesNoLength`) - a route tile
     */
    private boolean takesNoLength(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent component = getGraph().getTiles().get(tile);

        return component != null && TilePorts.takesNoLength(component.getType());
    }

    private boolean isSwitchSquare(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent component = getGraph().getTiles().get(tile);

        return component != null && component.isSwitch();
    }

    /** By page, then row, then column - pages compared as text, which is how they are named. */
    private int compareSquares(TileKey x, TileKey y)
    {
        int byPage = String.valueOf(x.getPage()).compareTo(String.valueOf(y.getPage()));

        if (byPage != 0) return byPage;
        if (x.getY() != y.getY()) return Integer.compare(x.getY(), y.getY());

        return Integer.compare(x.getX(), y.getX());
    }

    private static String sortedNames(java.util.Collection<TileKey> tiles)
    {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();

        for (TileKey tile : tiles) names.add(String.valueOf(tile));

        return names.toString();
    }

    /**
     * Whether a piece is still to be asked about: nothing in it measured, and not answered 0 on purpose (OB-274).
     *
     * A piece answered 0 has every square recorded as 0 - `assignStretchLength` writes it that way - so a square
     * added to the piece later by an edit to the diagram has no answer, and the piece is asked about again.
     *
     * @param stretch the piece
     * @return true when the walk should ask for it
     */
    public boolean needsALength(Stretch stretch)
    {
        if (stretch == null || measuredIn(stretch) > 0) return false;

        for (TileKey tile : stretch.getTiles())
        {
            if (!store.isTileLengthAnswered(tile)) return true;
        }

        return false;
    }

    /**
     * @param tile a switch or a square two roads cross
     * @return whether it is still to be asked for its length - neither measured nor answered 0 (OB-274)
     */
    private boolean squareNeedsALength(TileKey tile)
    {
        return store.getTileLength(tile) <= 0 && !store.isTileLengthAnswered(tile);
    }

    private int measuredIn(Stretch stretch)
    {
        int total = 0;

        for (TileKey tile : stretch.getTiles())
        {
            total += Math.max(0, store.getTileLength(tile));
        }

        return total;
    }

    /**
     * Squares that emit an arrival copy a train can be sent to and then never leave (Adam, 2026-09-02).
     *
     * "We need a warning for instances like the previous version of this."  He had just built
     * LowerParkingReverse and one of its copies came out with two ways in and none out - a destination
     * autonomy may choose, from which no train can ever depart.  BottomMainB had the same shape earlier
     * the same day, and cost an evening of chasing Return Home for a fault that was in the diagram.
     *
     * **The existing checks cannot see this, and it is worth saying why.**  They ask about the SQUARE -
     * `STATION_REACHES_NOTHING`, `ARRIVAL_TRAPPED` and the rest all work in TileKeys - and a square is
     * emitted as one Point per side a train can arrive by.  LowerFront is reachable, so the square
     * passes; its westbound copy has no way in at all.  A rule about copies cannot be enforced by
     * looking at squares, which is the same lesson the home rules learned separately.
     *
     * So this reads the graph as BUILT, where the copies exist, and reports the square that produced a
     * bad one.
     *
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    public java.util.Map<TileKey, String> destinationCopiesWithNoWayOut()
    {
        return badCopies(true, builtForInspection(), null);
    }

    /**
     * The same, over a graph somebody has already built (D3F-C6).
     *
     * @param built the configuration as JSON, or null to build one here
     * @param byName the builder's own tile-name map, or null to ask for one here
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    java.util.Map<TileKey, String> destinationCopiesWithNoWayOut(org.json.JSONObject built,
        java.util.Map<String, TileKey> byName)
    {
        return badCopies(true, built, byName);
    }

    /**
     * The same, for a copy nothing can ever reach - no way IN rather than no way out.
     *
     * Worth its own warning because the consequence is different and less obvious.  A train cannot be
     * SENT there, so the square looks harmless; but a train that starts there, or is left there by
     * hand, is one Return Home will not move - the staging planner deliberately never moves a
     * locomotive off a square with no way back in, because it could never undo the move.  If such a
     * copy also sits on a through route, a train parked on it blocks everything behind it and Return
     * Home can only answer that it found no arrangement.  That is exactly what LowerFront (westbound)
     * did.
     *
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    public java.util.Map<TileKey, String> destinationCopiesWithNoWayIn()
    {
        return badCopies(false, builtForInspection(), null);
    }

    /**
     * The same, over a graph somebody has already built (D3F-C6).
     *
     * @param built the configuration as JSON, or null to build one here
     * @param byName the builder's own tile-name map, or null to ask for one here
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    java.util.Map<TileKey, String> destinationCopiesWithNoWayIn(org.json.JSONObject built,
        java.util.Map<String, TileKey> byName)
    {
        return badCopies(false, built, byName);
    }

    /**
     * Reads the built configuration and finds destination copies with no way out, or none in.
     *
     * @param wantNoWayOut true for copies nothing can leave, false for copies nothing can reach
     * @return the squares, mapped to the offending copy's name
     */
    private java.util.Map<TileKey, String> badCopies(boolean wantNoWayOut,
        org.json.JSONObject built, java.util.Map<String, TileKey> named)
    {
        java.util.Map<TileKey, String> out = new java.util.LinkedHashMap<>();

        if (reducer == null) return out;

        if (built == null || !built.has("points") || !built.has("edges")) return out;

        java.util.Set<String> stations = new java.util.HashSet<>();

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject p = points.getJSONObject(i);

            if (p.optBoolean("station", false)) stations.add(p.getString("name"));
        }

        java.util.Set<String> hasOut = new java.util.HashSet<>();
        java.util.Set<String> hasIn = new java.util.HashSet<>();

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject e = edges.getJSONObject(i);

            hasOut.add(e.getString("start"));
            hasIn.add(e.getString("end"));
        }

        // THE BUILDER'S OWN MAPPING, not one taken apart by hand.
        //
        // The first version of this split the copy's name at " (" and looked the remainder up among the
        // AUTHORED names - which works only for a square somebody has named.  On an unnamed square the
        // emitted name is the tile's own, there is no authored name to match, and the check silently
        // found nothing: it reported clean on a fixture built to be broken.  `tilesByName` is what the
        // builder itself uses, so the two cannot disagree about what a Point is called.
        java.util.Map<String, TileKey> byName = named != null ? named : builder(null).tilesByName();

        java.util.Set<String> good = wantNoWayOut ? hasOut : hasIn;

        for (String copy : stations)
        {
            if (good.contains(copy)) continue;

            TileKey tile = byName.get(copy);

            if (tile == null || out.containsKey(tile)) continue;

            // ONLY WHERE THE SQUARE ITSELF IS HEALTHY.
            //
            // Where EVERY copy of a square is stuck the square is stuck, and the square-level checks
            // already say so - `checkStationUnreachable` and `checkStationReachesNothing` between them
            // named nine of the ten squares this reported on Adam's own railway.  Saying it again in
            // different words is noise, and saying it as an ERROR is worse than noise: an error
            // refuses to start autonomy at all.
            //
            // What no square-level check can see is a HEALTHY square with one trapped arrival, which
            // is the whole reason these exist.
            if (!hasAHealthySibling(copy, tile, good, stations, byName)) continue;

            out.put(tile, copy);
        }

        return out;
    }

    /**
     * Squares the build emits with an arrival that can go nowhere (DD-A7).
     *
     * A copy IS an arrival - the builder emits one Point per side a train can come in by - so a copy
     * with no outgoing edge is a train that arrived and cannot leave.  That is the same sentence the
     * walk this replaced was trying to write, and the builder had already written it.
     *
     * Every point, not only the stations: `badCopies` asks about destinations because being sent
     * somewhere and stranded is the serious case, while a plain point trains only pass through gets the
     * same finding at INFO.  `checkTrappedArrivals` makes that distinction; this only finds them.
     *
     * Empty when the setup will not build, deliberately - see `builtForInspection`.
     *
     * @param built the inspected configuration, or null
     * @param named the builder's own name-to-tile mapping
     * @return the squares
     */
    private Set<TileKey> tilesWithATrappedArrival(org.json.JSONObject built,
        java.util.Map<String, TileKey> named)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (built == null || !built.has("points") || !built.has("edges")) return out;

        java.util.Set<String> hasOut = new java.util.HashSet<>();

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            hasOut.add(edges.getJSONObject(i).getString("start"));
        }

        // The builder's own mapping, for the reason given in badCopies: taking a copy name apart by
        // hand works only on a square somebody has named, and reported clean on a broken fixture.
        java.util.Map<String, TileKey> byName = named != null ? named : builder(null).tilesByName();

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            String name = points.getJSONObject(i).getString("name");

            if (hasOut.contains(name)) continue;

            TileKey tile = byName.get(name);

            if (tile == null) continue;

            // A square the operator has said trains may turn round at is not trapped: turning IS the
            // way out, and the builder expresses that by emitting the turning copy.
            if (isTurnAround(tile)) continue;

            // AND A SQUARE THAT WAS NEVER SPLIT HAS NO ARRIVAL TO TRAP.
            //
            // A tile nothing arrives at by any side of the grid is emitted whole, and a whole tile with no way
            // out is a dead end in the track rather than a trapped arrival. Three such squares on the sample
            // layout - sensors nothing arrives at - and reporting them would tell the operator to fix
            // something that is not there.  (This said the whole tile was one reached through a link; a link
            // lands a train by a real side - AMG-C2.)
            //
            // Found by `testTheCheckerAgreesWithTheBuild`, on the change meant to make that test
            // redundant: my first version of this derivation left the filter out.
            if (arrivalSides(tile).isEmpty()) continue;

            out.add(tile);
        }

        return out;
    }

    /**
     * The built graph as JSON, or null when the setup will not build.
     *
     * FOR INSPECTION, which is what the copy checks are.  buildConfiguration() answers for a
     * configuration that has been initialised and saved; asked on a session that has not, it came back
     * with the stations and no edges at all - and on a test machine it reached the DEFAULT layout to
     * find them, which on Adam's is his real railway.  The suite wrote to that folder once before
     * one.sh's fingerprint caught it.
     *
     * A setup that will not build has louder problems than a trapped arrival, and every one of them is
     * already reported.  Saying nothing is better than saying something wrong.
     *
     * @return the configuration the graph window would draw, or null
     */
    private org.json.JSONObject builtForInspection()
    {
        try
        {
            return new org.json.JSONObject(buildConfigurationForInspection());
        }
        catch (RuntimeException malformed)
        {
            return null;
        }
    }

    /**
     * Station copies a train can leave, and from which it can never reach another station.
     *
     * Adam, 2026-09-02: **"yes, extend the check to catch that shape too."**  BottomInner on his own
     * railway was a station a train could arrive at and depart from and still never get anywhere: one
     * way in, one way out, and the way out led only to track that came back.  Both of the checks above
     * pass it, because it has an edge in each direction; it is a dead end with a siding on it.
     *
     * **Another station, not another Point.**  Track a train may pass but not stop at is not somewhere
     * it can be sent, so a station whose only way on is plain track is as stuck as one with no way on
     * at all.  That much the test demonstrates.
     *
     * **And a different SQUARE.**  Where track loops back, an arrival can reach its own turning twin,
     * and the twin is the same platform - counting that as having got somewhere would report nothing
     * on the very shape this exists for.  Defensive rather than demonstrated: it takes a loop to
     * exercise, and the fixture is a straight run.
     *
     * Copies with no way out at all are left to the check above.  They reach nothing either, by
     * definition, and reporting both would put two messages on one square saying the same thing in
     * different words.
     *
     * @return the squares, mapped to the offending copy, empty when the setup will not build
     */
    public java.util.Map<TileKey, String> destinationCopiesReachingNoStation()
    {
        return destinationCopiesReachingNoStation(builtForInspection(), null);
    }

    /**
     * The same, over a graph somebody has already built (D3F-C6).
     *
     * @param built the configuration as JSON, or null to build one here
     * @param named the builder's own tile-name map, or null to ask for one here
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    java.util.Map<TileKey, String> destinationCopiesReachingNoStation(org.json.JSONObject built,
        java.util.Map<String, TileKey> named)
    {
        java.util.Map<TileKey, String> out = new java.util.LinkedHashMap<>();

        if (reducer == null) return out;

        if (built == null || !built.has("points") || !built.has("edges")) return out;

        java.util.Set<String> stations = new java.util.HashSet<>();

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject p = points.getJSONObject(i);

            if (p.optBoolean("station", false)) stations.add(p.getString("name"));
        }

        java.util.Map<String, java.util.List<String>> onward = new java.util.HashMap<>();

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject e = edges.getJSONObject(i);

            String start = e.getString("start");

            if (!onward.containsKey(start)) onward.put(start, new java.util.ArrayList<String>());

            onward.get(start).add(e.getString("end"));
        }

        java.util.Map<String, TileKey> byName = named != null ? named : builder(null).tilesByName();

        for (String copy : stations)
        {
            // Somewhere to go at all, or this is the other check's business.
            if (!onward.containsKey(copy)) continue;

            TileKey here = byName.get(copy);

            if (here == null) continue;

            if (reachesAStationElsewhere(copy, here, onward, stations, byName)) continue;

            if (out.containsKey(here)) continue;

            // Only where a sibling copy of the same square DOES get somewhere; see badCopies for why.
            boolean healthySquare = false;

            for (String sibling : stations)
            {
                if (sibling.equals(copy) || !here.equals(byName.get(sibling))) continue;

                if (onward.containsKey(sibling)
                    && reachesAStationElsewhere(sibling, here, onward, stations, byName))
                {
                    healthySquare = true;
                    break;
                }
            }

            if (!healthySquare) continue;

            out.put(here, copy);
        }

        return out;
    }

    /**
     * Whether a walk from one copy ever arrives at a station standing on a different square.
     *
     * @param from the copy to start at
     * @param fromTile its square, which the answer has to get away from
     * @param onward the built graph's edges, by the copy they leave
     * @param stations the copies that are stations
     * @param byName which square each copy stands on
     * @return true as soon as one is found, so a long railway costs no more than a short one
     */
    private boolean reachesAStationElsewhere(String from, TileKey fromTile,
        java.util.Map<String, java.util.List<String>> onward, java.util.Set<String> stations,
        java.util.Map<String, TileKey> byName)
    {
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();

        seen.add(from);
        queue.add(from);

        while (!queue.isEmpty())
        {
            java.util.List<String> next = onward.get(queue.poll());

            if (next == null) continue;

            for (String there : next)
            {
                if (!seen.add(there)) continue;

                TileKey tile = byName.get(there);

                if (stations.contains(there) && tile != null && !tile.equals(fromTile)) return true;

                queue.add(there);
            }
        }

        return false;
    }

    /**
     * Whether some OTHER copy of the same square is not stuck the way this one is.
     *
     * @param copy the copy being judged
     * @param tile its square
     * @param good the copies that are not stuck - the ones with a way out, or with a way in
     * @param stations every copy that is a station
     * @param byName which square each copy stands on
     * @return true when the square has a healthy arrival besides this one
     */
    private boolean hasAHealthySibling(String copy, TileKey tile, java.util.Set<String> good,
        java.util.Set<String> stations, java.util.Map<String, TileKey> byName)
    {
        for (String sibling : stations)
        {
            if (sibling.equals(copy)) continue;

            if (tile.equals(byName.get(sibling)) && good.contains(sibling)) return true;
        }

        return false;
    }

    /**
     * The generated configuration, in the format the autonomy model already reads.
     * @return
     */
    public String buildConfiguration()
    {
        return builder(globals()).build();
    }

    /**
     * A builder configured from this setup.
     *
     * The configuration used to be assembled by hand at each of four call sites, and they drifted:
     * one left out the split settings and so named a railway with no copies in it, another left out
     * the barred arrivals.  Whatever a builder is asked for - the file, the naming, the coordinates -
     * it must be describing the same railway, so there is one place that says what that railway is.
     *
     * @param globals the run-wide settings, or null when only the naming is wanted
     * @return a fresh builder
     */
    public AutonomyBuilder builder(AutonomyBuilder.Globals globals)
    {
        return new AutonomyBuilder(reducer, globals)
            .withPointExtras(pointExtras())
            .withReversibleTiles(reversibleTiles())
            .withMandatoryTurns(mandatoryTurnTiles())
            .withParkingTiles(parkingTiles())
            .withBarredArrivals(barredArrivals())
            .withProtectingSignals(protectingSignalNames())
            .withEntrySignals(entrySignalNames())
            .withBlockingPoints(store.getBlockingPoints());
    }

    /**
     * Which sides each station refuses to let trains arrive by.
     *
     * @return square to barred sides, only for the squares that restrict anything
     */
    /**
     * @param station a station's square
     * @return the square of the first signal protecting it, or null
     */
    public TileKey getProtectingSignal(TileKey station)
    {
        return store.getProtectingSignal(station);
    }

    /**
     * @param station a station's square
     * @return the squares of every signal protecting it, in the order they were paired
     */
    public List<TileKey> getProtectingSignals(TileKey station)
    {
        return store.getProtectingSignals(station);
    }

    /**
     * @param station a station's square
     * @param signal the signal's square, or null to unpair
     */
    public void setProtectingSignal(TileKey station, TileKey signal)
    {
        store.setProtectingSignal(station, signal);
        touched();
    }

    /**
     * Replaces every signal protecting a station.
     *
     * @param station a station's square
     * @param signals the signals' squares; empty or null unpairs
     */
    public void setProtectingSignals(TileKey station, List<TileKey> signals)
    {
        store.setProtectingSignals(station, signals);
        touched();
    }

    /**
     * @param station a station's square
     * @return the squares of every signal guarding the way into it (FR-096)
     */
    public List<TileKey> getEntrySignals(TileKey station)
    {
        return store.getEntrySignals(station);
    }

    /**
     * Replaces every signal guarding the way into a station (FR-096).
     *
     * @param station a station's square
     * @param signals the signals' squares; empty or null unpairs
     */
    public void setEntrySignals(TileKey station, List<TileKey> signals)
    {
        store.setEntrySignals(station, signals);
        touched();
    }

    /**
     * Every square switched out of service (V31-C3).
     *
     * The same question `pointBadges` asks one square at a time - `Boolean.FALSE.equals` of the
     * `active` property - asked of the whole configuration. `FALSE.equals` rather than a negation
     * because the property is absent on almost every square, and absent means in service.
     *
     * @return the closed squares, empty when none is
     */
    public java.util.Set<TileKey> shutTiles()
    {
        // FALSE.equals rather than a negation: the property is absent on almost every square, and
        // absent means in service - as does a value that is not a boolean at all.
        return new LinkedHashSet<>(
            tilesWhere((key, point) -> Boolean.FALSE.equals(point.opt("active"))));
    }
    /**
     * Which ACCESSORIES protect each station, by name.
     *
     * The store pairs squares, because a square survives everything; the running layout commands
     * accessories, which it knows by name.  This is the join, and it is made here because only the
     * session has the diagram to read the address off.
     *
     * A pairing whose signal tile has gone, or carries no address, is left out rather than emitted as
     * something the layout would fail to find - and a station whose signals have ALL gone that way is
     * left out entirely rather than emitted with an empty list.
     *
     * @return station square to accessory names
     */
    public Map<TileKey, List<String>> protectingSignalNames()
    {
        return signalNames(store.getProtectingSignals());
    }

    /**
     * The entry-guard pairings as accessory names, for the build (FR-096) - the same join, asked of the other list.
     *
     * @return station square to accessory names
     */
    public Map<TileKey, List<String>> entrySignalNames()
    {
        return signalNames(store.getEntrySignals());
    }

    /**
     * Station square to signal squares, turned into station square to accessory names - one join for both guards.
     *
     * @param pairings the store's pairings
     * @return station square to accessory names, leaving out every signal that has gone or has no address
     */
    private Map<TileKey, List<String>> signalNames(Map<TileKey, List<TileKey>> pairings)
    {
        Map<TileKey, List<String>> out = new LinkedHashMap<>();

        if (graph == null) return out;

        for (Map.Entry<TileKey, List<TileKey>> pair : pairings.entrySet())
        {
            List<String> names = new ArrayList<>();

            for (TileKey tile : pair.getValue())
            {
                LayoutDiagramComponent signal = graph.getTiles().get(tile);

                if (signal == null || signal.getAccessory() == null) continue;

                String name = signal.getAccessory().getName();

                if (!names.contains(name)) names.add(name);
            }

            if (!names.isEmpty()) out.put(pair.getKey(), names);
        }

        return out;
    }

    public Map<TileKey, Set<Side>> barredArrivals()
    {
        return store.getBarredArrivals();
    }

    /**
     * @param tile a station's square
     * @return the sides trains may not arrive by, empty when it takes them from anywhere
     */
    public Set<Side> getBarredArrivals(TileKey tile)
    {
        Set<Side> barred = store.getBarredArrivals(tile);

        if (barred.isEmpty()) return barred;

        // Only the sides that still exist.
        //
        // A restriction is stored against a side of the square as the diagram was when it was set, and
        // the diagram moves: a tile replaced or rotated, an approach re-plumbed, and the square now
        // arrives from somewhere else entirely.  The stale side is dead in the build - there is no copy
        // for it - but it was still counted, and the count is what decides whether the menu will let
        // you shut another side.  A station could end up with every box ticked, every box disabled, and
        // nothing on screen to say why.
        barred.retainAll(arrivalSides(tile));

        return barred;
    }

    /**
     * @param tile a station's square
     * @param barred the sides trains may not arrive by
     */
    public void setBarredArrivals(TileKey tile, Set<Side> barred)
    {
        store.setBarredArrivals(tile, barred);
        touched();
    }

    /**
     * The sides a train can actually arrive at a square by.
     *
     * Read from the split rather than from the tile's ports: the split is what decides how many copies
     * a square becomes, and a side no copy exists for is not somewhere a restriction could bite.  So
     * the editor offers exactly the choices that mean something.
     *
     * @param tile
     * @return the arrival sides, in the order the build emits them
     */
    public List<Side> arrivalSides(TileKey tile)
    {
        return getStationIndex().arrivalSidesAt(tile);
    }

    /**
     * The sides a train may actually have ARRIVED at this square by (OB-204).
     *
     * `arrivalSides` is the geometry: every side a reduced edge reaches this square by.  It is the right
     * answer for the editor's own "Trains May Arrive" menu, which has to offer a barred side so it can
     * be un-barred - and the wrong answer for anything asking where a train came FROM, because a side
     * the operator has closed is a side nothing arrives by.
     *
     * **What that cost, measured on Adam's own railway.**  BottomMainA bars its arrival from E, so it has
     * one way in.  Asked of the geometry it has two, which defeats `ArrivalSidePrompt.suggestedFor`'s
     * first and best rule - *"one way in that is not the way it is pointing"* - and drops it through to
     * the compass assumption, which reads the train's facing.  The facing alternates, because the square
     * it was moved from turns every train round, so the recorded arrival side alternated with it: *"it
     * does not always show the orange tail facing west.  seems to appear about half the time."*
     *
     * Barred sides are not SUBTRACTED blindly: `getBarredArrivals` already narrows a stored set to the
     * sides the square really has, so a setting left behind by an edit cannot remove a side that exists.
     *
     * @param tile the square
     * @return the sides, in the build's own order, with the barred ones left out
     */
    public List<Side> unbarredArrivalSides(TileKey tile)
    {
        List<Side> sides = new java.util.ArrayList<>(arrivalSides(tile));

        sides.removeAll(getBarredArrivals(tile));

        // EVERY SIDE BARRED IS NOT AN ANSWER.  The editor will not let the last way in be closed, but a
        // diagram edit can arrive at it from the other side - and a placement door handed an empty list
        // falls back to the geometry, which is what it did before this method existed. Better the old
        // answer than none.
        return sides.isEmpty() ? arrivalSides(tile) : sides;
    }

    /**
     * The squares where a train may turn round, which the builder emits as several Points each.
     *
     * Two ways to be one, and they are the same physical act seen from either side of the station
     * question:
     *   - a station marked TERMINUS, which is where a train ends its run and reverses.  As a single
     *     Point it is a dead end for routing - isPathClear refuses any path with a terminus in the
     *     middle - so a through platform that some trains terminate at could not be expressed at all.
     *   - anything else marked CAN REVERSE, which is a place a train changes direction on its way
     *     somewhere else: the move that reaches a siding trailing off behind it.
     *
     * @return the marked tiles
     */
    /**
     * The name the track diagram knows a running Point by.
     *
     * A split tile is several Points - "Main 4 (eastbound, reverse)" and the rest - but only ever one
     * caption on the diagram, written before any of them existed.  Without this the label for a split
     * station never fills in: it is registered under the base name and the running Point never has it.
     *
     * @param pointName a Point of the running configuration
     * @return the base name, or the name itself when nothing was split
     */
    public String baseNameOf(String pointName)
    {
        return pointName == null ? null : getStationIndex().baseNameOf(pointName);
    }

    // Emitted Point name -> the caption on the diagram.  Cached because updateStationLabels asks on
    // every feedback event during a run, and working it out walks every point against every edge.
    // Dropped whenever the graph is rebuilt, which is the only thing that can change it.
    // volatile: the labels are updated from the feedback thread while a rebuild can drop this from the
    // event thread, and a stale reference here means a station that stops filling in until the next edit
    private volatile StationIndex stationIndex;

    /**
     * The translation between squares and the Points derived from them.
     *
     * Derived once and held until the setup changes, because it is asked for constantly - once per
     * Point on every feedback event, in places - and because deriving it twice is how the answers
     * started disagreeing.  Everything that used to build its own AutonomyBuilder to answer half of
     * this question now asks here instead.
     *
     * @return the index, never null
     */
    public StationIndex getStationIndex()
    {
        StationIndex current = stationIndex;

        // Derived here only before the first rebuild, when nothing is running and nobody else can be
        // asking.  Every other path derives it eagerly - see deriveStationIndex - because the readers
        // are not on the thread the writers are on.
        if (current == null)
        {
            current = deriveStationIndex();
        }

        return current;
    }

    /**
     * Works the index out now, rather than leaving it for whoever asks first.
     *
     * Who asks first is the problem.  The labels are updated once per Point on every feedback event,
     * from the control station's own thread, while the event thread is free to edit the configuration -
     * setPointProperty is documented as usable while autonomy runs.  Deriving lazily meant that
     * feedback thread walking the configuration's JSONObjects, which are backed by plain HashMaps, at
     * the moment the event thread was writing to them.  A volatile field publishes the REFERENCE
     * safely; it says nothing about what happens inside the derivation.
     *
     * Called from the two places that invalidate it, both on the event thread, so a reader only ever
     * sees a finished immutable index - which is what this class has claimed all along.
     *
     * @return the new index, also stored
     */
    private StationIndex deriveStationIndex()
    {
        StationIndex derived = reducer == null ? StationIndex.EMPTY : new StationIndex(builder(null));

        stationIndex = derived;

        return derived;
    }

    /**
     * Every Point of the running configuration that stands for one square of the diagram.
     *
     * @param baseName the caption on the diagram
     * @return the emitted names, in the order they were emitted
     */
    /**
     * Every copy of a station, and which way a train standing on it would be pointing.
     *
     * A square is several Points - one per side a train can arrive by - and they are not
     * interchangeable: each one can only leave the way its own facing allows.  So "put this locomotive
     * here" is not a complete instruction, and answering it by taking the first copy puts the train on
     * a Point whose only moves are the ones the split exists to forbid.  That is a train autonomy can
     * see and cannot route.
     *
     * @param tile the station's square
     * @return the name of each copy against the side its train would face, in the order the build made
     *         them, empty when there is no setup or the square is not a Point
     */
    public Map<String, Side> facingsFor(TileKey tile)
    {
        return getStationIndex().facingsAt(tile);
    }

    /**
     * Whether two Points are the same piece of track.
     *
     * A square is several Points now - one per side a train can arrive by - so a train standing at
     * BottomMainB was being offered a path to BottomMainB, the copy facing the other way being a
     * different Point.  Going there is not a journey; it is the place it is already standing.
     *
     * Asked HERE rather than of the running layout, which cannot answer it.  The obvious key there
     * is the sensor, and the sensor is wrong: a station and its approach guard legitimately share
     * one and are genuinely two places.  What makes copies copies is the SQUARE they came from, and
     * only the setup knows which square a Point was built from.
     *
     * @param a a point name from the built graph
     * @param b another
     * @return true when both were built from one square
     */
    public boolean sameSquare(String a, String b)
    {
        if (a == null || b == null) return false;

        // Identical names are the same place even when the index has never heard of either - a
        // configuration built before the last change still has Points, and they are still somewhere.
        return a.equals(b) || getStationIndex().sameSquare(a, b);
    }

    public List<String> pointNamesFor(String baseName)
    {
        return getStationIndex().pointNamesFor(baseName);
    }

    /**
     * What the generated configuration calls the Point on a square.
     *
     * The BASE name - the one a diagram caption carries and the one anything looking a Point up by
     * caption will find.  Not the authored name, which can be blank or a duplicate; uniqueNames is
     * what settles both.
     *
     * @param tile
     * @return the name, or null when the square is not a Point
     */
    /**
     * The square a running Point stands on.
     *
     * The inverse of the naming the builder does, including the split copies - "Bahnhof (eastbound)"
     * and "Bahnhof (westbound)" both answer with the one square they are copies of.  This is what lets
     * anything holding a Point find the caption showing it, without going through its name.
     *
     * @param pointName a Point of the running configuration
     * @return its square, or null if this setup has never emitted that name
     */
    public TileKey tileForPointName(String pointName)
    {
        return getStationIndex().squareOf(pointName);
    }

    public String pointNameForTile(TileKey tile)
    {
        return getStationIndex().nameOf(tile);
    }

    /**
     * What to call a square in front of the user: its authored name, then its sensor, then where it is.
     *
     * This lived in AutonomyEditorPanel, which is the window the setup is done in - and OB-112 is about
     * the diagram's own right-click menu, in the other window, which had no way to reach it. The
     * choice is a real one and worth making once: a square somebody named is called that, a sensor
     * square is known by the address printed on the diagram, and everything else has only its
     * position. Copying those three lines into the second menu is how the two would come to disagree
     * about a square while both were on screen.
     *
     * @param tile the square
     * @return a name for it, never null
     */
    public String describeTile(TileKey tile)
    {
        String named = store == null ? null : store.getPointName(tile);

        if (named != null && !named.trim().isEmpty()) return named.trim();

        org.traincontrol.base.LayoutDiagramComponent component =
            getGraph() == null ? null : getGraph().getTiles().get(tile);

        return component != null && component.isFeedback()
            ? "s88 " + component.getRawAddress() : tile.getX() + "," + tile.getY();
    }

    public Set<TileKey> reversibleTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (isTurnAround(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * The stations that are parking berths.
     * @return
     */
    public Set<TileKey> parkingTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (isParking(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * Whether trains may turn round on this square.
     *
     * Reads the older keys as well as the current one.  A setup authored before the three switches -
     * station, turn round, parking - said the same things as "terminus" and "reversing", and those are
     * now DERIVED rather than set: a configuration carrying them would otherwise quietly lose its
     * termini the first time it was rebuilt.  Nothing is rewritten on disk; the old spelling is simply
     * still understood, and the first edit to a square writes the new one.
     *
     * @param tile
     * @return
     */
    /**
     * Whether every train arriving here must turn round, rather than merely being able to.
     * @param tile
     * @return
     */
    public boolean isMustTurnAround(TileKey tile)
    {
        return Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.MUST_REVERSE));
    }

    /**
     * The squares where turning round is compulsory.
     * @return
     */
    /**
     * The squares where a run may change direction, as the path search wants them - reversible squares
     * minus the ones where turning is compulsory.
     *
     * One source, so the editor's path test and the reachability check cannot draw different turn sets
     * and then disagree about which stations connect.  It is the same computation both did inline.
     *
     * @return the may-turn set
     */
    public Set<TileKey> mayTurnTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>(reversibleTiles());
        out.removeAll(mandatoryTurnTiles());
        return out;
    }

    /**
     * The stations this editor will report as ones autonomy never chooses (V36-C4, OB-195).
     *
     * **THE RUNTIME'S RULE, in the two clauses a SQUARE can answer.**  The runtime is
     * `Layout.isSendableDestination` - `isDestination() && isActive() && isAutoDestination() &&
     * !isReversing()` - and two of those four are asked here:
     *
     *   - `isAutoDestination`, the same switch under a different name, the one the menu calls **Can Be
     *     Chosen in Full Autonomy**;
     *   - `isActive`, which the menu writes when a square is switched out of service.
     *
     * `!isReversing()` is the one that genuinely cannot be: it is a property of a COPY, and a
     * may-reverse square keeps a plain copy autonomy can choose perfectly well, so a square-level
     * answer would refuse squares the runtime accepts.  `isDestination` is `store.isStation`.
     *
     * **`isActive` was missing until 2026-09-10, and this javadoc said "Nothing else decides it"**
     * (E8-B1).  A station Adam had switched OUT OF SERVICE was reported as one autonomy will choose -
     * in the panel he opens to find out why a train is not moving.  The sentence is what made the
     * omission invisible, and the guard written to catch the divergence,
     * `core.testTheAutoTierScopeMatchesTheRuntime`, paraphrased the runtime with the same clause
     * missing, so the two agreed about a rule neither of them had.
     *
     * **It carried a second clause until 2026-09-09, and a comment claiming that clause was the
     * runtime's.**  The clause was `isMustTurnAround`, and it is not: a compulsory-turn square that
     * stops trains is emitted by `AutonomyBuilder` with `terminus:true`, and a terminus has
     * `isReversing()` false, so the runtime chooses it quite happily.  The editor promised the
     * operator that autonomy would leave such a square alone while autonomy was sending trains to it.
     *
     * Adam settled it the same day: **"narrow the notice to match the runtime - autonomy should only
     * allow a turn at a point if the 'allow in autonomy' option is checked, otherwise the train may
     * only pass through in its current direction."**  So the two markings are independent and each
     * says its own thing - *Changing Direction* says what happens when a train ARRIVES, this switch
     * says who may send one - and `docs/reference/behaviour.md` section 3 carries the ruling.
     *
     * **It cost nothing on his own railway, which is why it survived.**  Every compulsory turn he has
     * is also marked manual-only, so the surviving clause already catches all of them and the two
     * spellings could not disagree.  `core.testACompulsoryTurnIsChosenLikeAnyOtherStation` asserts on
     * `single-switch`, where they can.
     *
     * Three readers depend on the width: the magenta leg colour, the Auto tier's "reachable and never
     * chosen" notice, and `AutonomyChecks.checkReversingGoesSomewhere` - which counted any station as
     * somewhere to go, and so passed a reversing point whose only reachable station is a parking
     * berth.  Adam, 2026-09-04: *"Make it a notice."*  All three now say what the railway does.
     *
     * @return the tiles that are stations autonomy is told to leave alone
     */
    public Set<TileKey> stationsAutonomyWillNotChoose()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            // ONE CLAUSE, AND IT IS THE RUNTIME'S (Adam, 2026-09-09; OB-195).
            //
            // A second one lived here from CONF-B6 until his ruling: `isMustTurnAround`, arrived at by
            // reading `Layout.isSendableDestination`'s `!isReversing()` and spelling it that way.  The
            // two are not the same thing - a compulsory turn that stops is built as a TERMINUS, whose
            // `isReversing()` is false - so it refused a square the runtime accepts.  That is the safe
            // direction for a notice and the wrong direction for a claim about what autonomy does,
            // which is what this is.
            //
            // Nothing replaces it.  Turning round is not part of who may send a train: *"autonomy
            // should only allow a turn at a point if the 'allow in autonomy' option is checked,
            // otherwise the train may only pass through in its current direction"*, and that switch is
            // `isAutoDestination`.
            // SHUT COUNTS TOO (E8-B1).  `Layout.isSendableDestination` requires `isActive()`, and
            // that is a property of the SQUARE - the menu writes it, `shutTiles` reads it in bulk, and
            // the badge code a few thousand lines below spells this exact test.  Without it a station
            // switched out of service was reported as one autonomy will choose, which is the opposite
            // of what the operator had just told the railway.
            boolean shut = Boolean.FALSE.equals(getPointProperty(tile, "active"));

            if (store.isStation(tile) && (shut || !isAutoDestination(tile))) out.add(tile);
        }

        return out;
    }

    /**
     * The stations autonomy leaves alone that a PERSON can still drive to (E8V-B1).
     *
     * `stationsAutonomyWillNotChoose` is the runtime's rule and answers about two different things at
     * once: a station marked manual-only, which is a preference about what autonomy picks, and a
     * station switched OUT OF SERVICE, which is a fact about the railway that binds every tier.
     *
     * The magenta leg colour was asked for in Adam's own words - *"just use a different color going to
     * manual-only points"* - and a shut square is not one of those.  Nothing may be sent there at all:
     * `Layout.isPathClear` refuses a closed final point in every tier since his ruling of 2026-09-06,
     * and `Layout.isOfferableToOperator` refuses it outright, so the right-click menu never offers it.
     * Colouring it as though a hand-driven send were the remedy points at a door that is shut.
     *
     * @return the stations a hand-driven send may still reach and autonomy will not choose
     */
    public Set<TileKey> manualOnlyStations()
    {
        Set<TileKey> out = new LinkedHashSet<>(stationsAutonomyWillNotChoose());

        out.removeAll(shutTiles());

        return out;
    }

    public Set<TileKey> mandatoryTurnTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (isMustTurnAround(tile)) out.add(tile);
        }

        return out;
    }

    public boolean isTurnAround(TileKey tile)
    {
        if (isMustTurnAround(tile)) return true;

        if (Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.CAN_REVERSE))) return true;

        // a terminus was always "a station where trains turn round"
        if (Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) return true;

        // a reversing point that is NOT a station was "somewhere trains turn round on the way past"
        return Boolean.TRUE.equals(getPointProperty(tile, "reversing")) && !store.isStation(tile);
    }

    /**
     * Whether this station is a parking berth - somewhere autonomy never sends a train of its own
     * accord, and cannot route one through.
     *
     * @param tile
     * @return
     */
    public boolean isParking(TileKey tile)
    {
        if (!store.isStation(tile)) return false;

        // the switch as it is stored now: written only when it is off, like every other default
        if (Boolean.FALSE.equals(getPointProperty(tile, AutonomyBuilder.AUTO_DESTINATION))) return true;

        // what it was called for the hour this was spelt "parking"
        if (Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.PARKING))) return true;

        // and before that, a reversing STATION was the only way to say it at all
        return Boolean.TRUE.equals(getPointProperty(tile, "reversing"));
    }

    /**
     * Whether full autonomy may choose this station of its own accord.  The switch the user sees.
     * @param tile
     * @return
     */
    public boolean isAutoDestination(TileKey tile)
    {
        return !isParking(tile);
    }

    /**
     * Sets whether autonomy may choose this station, clearing the two older spellings of the same idea.
     *
     * @param tile
     * @param on
     */
    public void setAutoDestination(TileKey tile, boolean on)
    {
        setPointProperty(tile, AutonomyBuilder.AUTO_DESTINATION, on ? null : Boolean.FALSE);
        setPointProperty(tile, AutonomyBuilder.PARKING, null);
        setPointProperty(tile, "reversing", null);
    }

    /**
     * Sets one of the three switches, clearing the older spellings of the same idea so that a square
     * cannot end up saying one thing in two vocabularies.
     *
     * @param tile
     * @param key CAN_REVERSE or PARKING
     * @param on
     */
    public void setPointFlag(TileKey tile, String key, boolean on)
    {
        setPointProperty(tile, key, on ? Boolean.TRUE : null);

        // Never authored again, whichever way this went: they are derived at build time now, and one
        // left behind would keep asserting itself after the switch that set it had been turned off.
        setPointProperty(tile, "terminus", null);
        setPointProperty(tile, "reversing", null);
    }

    /**
     * The same, laid out like the track it came from, for looking at in the graph window.
     * @return
     */
    public String buildConfigurationForInspection()
    {
        List<String> pageOrder = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (!store.getExcludedPages().contains(page.getName())) pageOrder.add(page.getName());
        }

        return builder(globals()).withCoordinatesFromTiles(pageOrder).build();
    }

    /**
     * The per-point operational data of the active configuration, for the builder to merge in.
     */
    private Map<String, org.json.JSONObject> pointExtras()
    {
        Map<String, org.json.JSONObject> out = new LinkedHashMap<>();

        String active = store.getActiveConfiguration();

        if (active == null) return out;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return out;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String key : points.keySet())
        {
            out.put(key, points.getJSONObject(key));
        }

        return out;
    }

    /**
     * Lifts what the running layout knows into the active configuration - placements, homes, termini,
     * pace settings - so that what was set while trains were running is what loads next time.
     *
     * Takes the layout's own JSON rather than the layout, for two reasons: toJSON is the serialization
     * the legacy path trusted for years, so anything it captures is by definition loadable; and a
     * string can be tested without a control station.
     *
     * Keyed by tile rather than by name, so a Point renamed between sessions keeps its placements.
     * Points whose names no longer match any tile are dropped silently - they belong to track that no
     * longer exists, and carrying them forward would place a locomotive on nothing.
     *
     * @param layoutJson what the running Layout serialized to
     */
    public void captureFromLayout(String layoutJson)
    {
        captureFromLayout(layoutJson, store.getActiveConfiguration());
    }

    /**
     * The same, into a named configuration - for callers that know which configuration the running
     * layout was generated from.  The two can differ: a load that was refused partway leaves the store
     * pointing at a configuration that never ran, and capturing into it would overwrite it with another
     * configuration's state.
     *
     * @param layoutJson
     * @param configurationName which configuration this layout's state belongs to
     */
    public void captureFromLayout(String layoutJson, String configurationName)
    {
        if (layoutJson == null || reducer == null || configurationName == null) return;

        // Not after a rename (MT-135, MT-171, MT-174).
        //
        // This writes what the running Layout knows back into the configuration, keyed by tile - and it
        // works out those keys from THIS session's naming, which a rename has just made stale. The
        // store was rekeyed to the new page name; the graph, the reducer and the page objects still
        // carry the old one. So every placement was written a second time under the old name, beside
        // the correctly renamed one, and a locomotive that is in two places at once fails the whole
        // setup:
        //
        //   "TopMainR2Inter holds a locomotive that is also recorded as standing somewhere else. A
        //    locomotive can only be in one place, and autonomy refuses the whole setup while it is in
        //    two" - Adam, MT-135, with the same four squares this reproduces on the sample layout.
        //
        // Renaming back did it again under the other name, which is why undoing did not undo it.
        //
        // Refused rather than repaired, and nothing is lost by refusing. The rename has already
        // written the store through saveWithoutReconciling, so the state is on disk. What this call
        // adds is whatever the RUNNING layout knows that the store does not - and a rename is refused
        // while autonomy is running, so there is nothing in that gap. The session is about to be
        // discarded and rebuilt from the renamed pages in any case.
        if (pagesStale) return;

        org.json.JSONObject configuration = store.getConfiguration(configurationName);

        if (configuration == null) return;

        org.json.JSONObject root = new org.json.JSONObject(layoutJson);

        // name -> tile, through the same naming the builder used to generate the file
        Map<String, TileKey> tilesByName = new LinkedHashMap<>();

        AutonomyBuilder naming = builder(null);

        tilesByName.putAll(naming.tilesByName());

        // Which way a train standing on each emitted Point is pointing.  See the facing capture below.
        Map<String, TilePorts.Side> facings = naming.facingByName();

        org.json.JSONObject points = new org.json.JSONObject();

        if (root.has("points"))
        {
            for (Object o : root.getJSONArray("points"))
            {
                org.json.JSONObject point = (org.json.JSONObject) o;

                TileKey tile = tilesByName.get(point.optString("name"));

                if (tile == null) continue;

                org.json.JSONObject extras = new org.json.JSONObject();

                for (String key : POINT_OPERATIONAL_KEYS)
                {
                    if (!point.has(key) || point.isNull(key)) continue;

                    // A placement records WHICH locomotive stands here and nothing else.  Point.toJSON
                    // also writes its length, reversibility, speed and functions, and parseAuto applies
                    // those back onto the Locomotive - so capturing them made loading a configuration
                    // silently revert changes made in the locomotive UI since.  Those live in LocDB.
                    if ("loc".equals(key) && point.get(key) instanceof org.json.JSONObject)
                    {
                        org.json.JSONObject loc = point.getJSONObject(key);

                        if (!loc.has("name")) continue;

                        extras.put(key, new org.json.JSONObject().put("name", loc.getString("name")));

                        continue;
                    }

                    // A TAIL BELONGS TO THE COPY THE TRAIN STANDS ON (TLR-A2).  The build writes a square's side and
                    // road onto every copy of it, and a train leaving clears them only on its own - so read from the
                    // others, this merge put a departed train's tail back into the setup, where a restart gave it to
                    // whatever stood there next.
                    if (("arrivedFrom".equals(key) || "arrivedAlong".equals(key)) && !point.has("loc")) continue;

                    extras.put(key, point.get(key));
                }

                // Which way the train ended up pointing, learned rather than asked for.  A square is
                // several Points once it is split, and the one a locomotive is standing on says which
                // way round it is - so after autonomy has run once, nobody has to answer that question.
                TilePorts.Side facing = facings.get(point.optString("name"));

                if (facing != null && extras.has("loc"))
                {
                    extras.put(AutonomyBuilder.FACING, facing.name());
                }

                // AND THE FACING OF A HOME THE RUNNING LAYOUT HOLDS TO ITS COPY (OB-282) - one set from the running
                // diagram, which is a home on the copy the train stood on.
                if (facing != null && extras.has("home") && point.optBoolean(AutonomyBuilder.HOME_FACING_FIXED, false))
                {
                    extras.put(AutonomyBuilder.HOME_FACING, facing.name());
                }

                // An empty one is still recorded, and must be.  Skipping it meant a square the running
                // layout had NOTHING to say about never entered this map, so the merge below never ran
                // for it and never reached its `else remove` - and a locomotive that had driven away
                // from a plain sensor stayed placed there in the configuration.  The next build emitted
                // the same locomotive twice, on the square it left and the square it reached.

                // Merged, not replaced.  A split square is visited once per copy and only ONE of them
                // carries the locomotive, so putting each copy's extras in turn meant the last copy read
                // won - and if that was not the copy the train was on, the placement was lost.
                String id = tile.toString();

                org.json.JSONObject into = points.has(id)
                    ? points.getJSONObject(id) : new org.json.JSONObject();

                for (String key : extras.keySet()) into.put(key, extras.get(key));

                points.put(id, into);
            }
        }

        // Merged per point, not substituted wholesale.  The running Layout was built BEFORE any edits
        // made in the editor since, so replacing the whole object discarded them - set a terminus,
        // press Apply, exit, and it was gone.  What the running layout knows about is overwritten;
        // everything else is left alone.
        org.json.JSONObject existing = configuration.has("points")
            ? configuration.getJSONObject("points") : new org.json.JSONObject();

        for (String id : points.keySet())
        {
            org.json.JSONObject captured = points.getJSONObject(id);
            org.json.JSONObject before = existing.has(id)
                ? existing.getJSONObject(id) : new org.json.JSONObject();

            // AND THE TAIL GOES WITH THE TRAIN THAT LEFT (RGD-B1).
            //
            // The loop below replaces `loc` from the running layout, so a capture can hand a square to
            // a different locomotive - autonomy ran, the trains moved, somebody opened the editor.
            // Since WK7-B1 `arrivedFrom` and `arrivedAlong` ARE captured, from the occupied copy only (TLR-A2) -
            // and before that they were not. Either way, leaving the OLD train's side on a square whose occupant just changed is
            // the same defect the placement doors were fixed for, arriving by a different road.
            //
            // Read before the replacement, because the replacement is what makes them differ.
            //
            // REG8-A1 fixed the other half of this - the build applies the side after placing trains,
            // so the file gets the last word - and its own body said the ordering fix and this one had
            // to land together. Only the ordering did.
            String occupantWas = nameOfPlacedLocomotive(before.opt("loc"));
            String occupantNow = nameOfPlacedLocomotive(captured.opt("loc"));

            if (occupantWas != null && !occupantWas.equals(occupantNow))
            {
                before.remove("arrivedFrom");
                before.remove("arrivedAlong");
            }

            // Keys the layout can speak for are replaced - including being REMOVED when the layout no
            // longer carries them, which is how a property returned to its default is cleared.
            for (String key : POINT_OPERATIONAL_KEYS)
            {
                if (captured.has(key)) before.put(key, captured.get(key));
                else before.remove(key);
            }

            // Not one of those keys, because the running layout has no field for it: it is worked out
            // from WHICH copy of a split square the locomotive was found on.  Only ever written here,
            // never cleared - a square with no train on it still remembers which way the last one was
            // pointing, and that is the better guess for the next one.
            if (captured.has(AutonomyBuilder.FACING))
            {
                before.put(AutonomyBuilder.FACING, captured.get(AutonomyBuilder.FACING));
            }

            // AND A HOME'S FACING, where the running layout holds the home to a copy (OB-282): a home set on the
            // running diagram is set facing the way that copy faces.  Kept where the layout's home carries none - the
            // build put it on a copy by its own rule - and gone when the home is.
            if (captured.has(AutonomyBuilder.HOME_FACING))
            {
                before.put(AutonomyBuilder.HOME_FACING, captured.get(AutonomyBuilder.HOME_FACING));
            }
            else if (!before.has("home"))
            {
                before.remove(AutonomyBuilder.HOME_FACING);
            }

            existing.put(id, before);
        }

        // A square whose TILE is gone keeps nothing.  A square that is merely not a Point keeps
        // everything.
        //
        // This used to be judged against the squares that are still POINTS, and those are not the same
        // set: a sensor reduces to a Point only where track connects it to something.  Nudge a station
        // one square so that it no longer joins the run either side of it and it stops being a Point
        // while remaining perfectly present on the diagram - and the next capture deleted its
        // locomotive, its facing and its markings.  Adam found it doing exactly that, and asked for the
        // opposite: keep the placement, let the build refuse it, and have it come back when the track
        // is joined up again.
        //
        // Which is what this does now.  A disconnected station is a mistake somebody is in the middle
        // of making, not an instruction to forget the train that was standing there; the check reports
        // it, and reconnecting the track brings it back with nothing to re-enter.  Deleting the tile is
        // still deleting it - the tile is then not in the graph either.
        //
        // (Judged against squares rather than against what this capture had something to say about,
        // which is the older reasoning and still holds: a square marked "trains may turn round here"
        // and nothing else carries no operational data, so keying the prune on what was captured
        // deleted the marking the first time autonomy ran.)
        Set<String> stillThere = new LinkedHashSet<>();

        for (TileKey tile : graph.getTiles().keySet()) stillThere.add(tile.toString());

        // Which pages this reduction was even allowed to look at.  A page left out of autonomy has no
        // Points in the reduction, so judging its squares by that reduction condemns every one of them -
        // and excluding a page has to be reversible, or a page ticked off and back on has silently lost
        // its placements, its facings and its markings.
        // Taken from the layout's own pages, not from the pages the reduction happens to have Points on.
        // A page whose last sensor was deleted has no Points, so inferring the list from the reduction
        // quietly exempted it forever: its stale placements and markings could never be pruned, and if a
        // page of that name was ever added back they came back with it - a locomotive recorded as
        // standing on track it is not on.
        Set<String> pagesInPlay = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // NOR A STAND-IN FOR A PAGE THAT WOULD NOT READ (AMS-A1).  It carries the real page's name and one
            // text tile, so judged here every placement, home and priority on that page was "no longer there" -
            // the MT-135 loss `pagesSafeToJudge` and `save()` refuse since FV3-A1, by the one loop of the four
            // that did not skip it.
            if (page.isUnreadable()) continue;

            if (!store.getExcludedPages().contains(page.getName())) pagesInPlay.add(page.getName());
        }

        List<String> gone = new ArrayList<>();

        for (String id : existing.keySet())
        {
            if (stillThere.contains(id)) continue;

            TileKey tile = AutonomyCompanionStore.parseTileKey(id);

            // Unparseable, or on a page this setup is not looking at: left alone rather than judged by
            // a reduction that was never given the chance to see it.
            if (tile == null || !pagesInPlay.contains(tile.getPage())) continue;

            gone.add(id);
        }

        for (String id : gone) existing.remove(id);

        configuration.put("points", existing);

        // and the top of the file: pace, speeds, and the rest of the settings panel
        org.json.JSONObject globals = new org.json.JSONObject();

        for (String key : root.keySet())
        {
            if (!"points".equals(key) && !"edges".equals(key)) globals.put(key, root.get(key));
        }

        configuration.put("globals", globals);

        dirty = true;
    }

    /**
     * Writes one run-wide setting into the active configuration and saves.
     *
     * TARGETED, because the whole-layout route is wrong for this. captureFromLayout copies everything
     * Layout.toJSON knows - including where every train is standing - and the window's wrapper around
     * it declines to run while autonomy is running. Choosing a routing rule should not record the
     * position of every train as a side effect, and should not be quietly ignored mid-session.
     *
     * Saved immediately rather than on the next explicit save: a setting the user picked from a menu
     * and did not see written is a setting they will pick again.
     *
     * ANSWERS WHETHER IT STORED, because two of its three ways of doing nothing are silent (LE-B5).
     * A caller that deletes its own copy of a setting on the strength of having called this needs to
     * know, and one did.
     *
     * @param key the global's name, as it appears at the top level of the built configuration
     * @param value the value to store
     * @return true when the value was written and saved, false when there was no configuration to
     *         write it to
     * @throws IOException if the setup cannot be written
     */
    public boolean setGlobal(String key, Object value) throws IOException
    {
        String active = store.getActiveConfiguration();

        if (active == null) return false;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null) return false;

        if (!configuration.has("globals"))
        {
            configuration.put("globals", new org.json.JSONObject());
        }

        configuration.getJSONObject("globals").put(key, value);

        // Without reconciling, for the reason the diagram-replacement path gives: reconciling compares
        // against the pages this session holds, and nothing about this edit has touched a page.
        saveWithoutReconciling();

        return true;
    }

    /**
     * What the active configuration STORES for a global, or null (RC-A2).
     *
     * The counterpart setGlobal never had, and its absence is why a caller asked the wrong thing. The
     * question "has the configuration already answered this?" cannot be put to the live Layout: the
     * live Layout carries whatever was last pushed into it in memory, including by the caller itself,
     * so a migration that writes the value and then asks again is told yes by its own writing.
     *
     * Null covers all three ways of having no answer - no active configuration, no configuration
     * object, no such global - because a caller that has to distinguish them has a different problem.
     *
     * @param key the global's name, as it appears at the top level of the built configuration
     * @return the stored value as text, or null when nothing is stored under that name
     */
    public String getGlobal(String key)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null) return null;

        org.json.JSONObject globals = configuration.optJSONObject("globals");

        if (globals == null) return null;

        return globals.optString(key, null);
    }

    /**
     * The globals of the active configuration, which is where pace and speed settings live.
     */
    private AutonomyBuilder.Globals globals()
    {
        AutonomyBuilder.Globals globals = new AutonomyBuilder.Globals();

        String active = store.getActiveConfiguration();

        if (active == null) return globals;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("globals")) return globals;

        org.json.JSONObject stored = configuration.getJSONObject("globals");

        for (String key : stored.keySet())
        {
            globals.set(key, stored.get(key));
        }

        return globals;
    }

    /**
     * Whether a square can meaningfully be given a direction.
     *
     * False for a link.  A link's route is a stub - the same side twice - because the tile conducts
     * track on one face and a jump on the other, and a jump is not a side.  "Toward A" and "toward B"
     * therefore name the same place, and the traversal would allow both whichever was chosen: a
     * setting that silently does nothing, which is worse than no setting at all.
     *
     * Here rather than in the menu that asks it, so the rule has a name, one home, and a test.  Making
     * a link one-way is a real thing to want and needs the JUMP to carry the direction; that is on the
     * backlog, and this returns false until it exists.
     *
     * @param tile the square
     * @return true when the direction answers mean something there
     */
    public boolean canCarryDirection(TileKey tile)
    {
        if (graph == null || tile == null) return false;

        LayoutDiagramComponent component = graph.getTiles().get(tile);

        if (component == null) return false;

        if (org.traincontrol.automationui.TilePorts.hasPortal(component.getType())) return false;

        return !getRoutes(tile).isEmpty();
    }

    /**
     * The stations whose paired protecting signal no longer resolves to an accessory.
     *
     * The same walk protectingSignalNames does, keeping what it skips.  It has to skip them - a build
     * cannot emit an accessory that is not there - but skipping in silence is what leaves an operator
     * believing a platform is protected when it is not.
     */
    /**
     * The stations carrying no protecting signal at all.
     *
     * Distinct from signalsThatAreGone, which is a pairing that no longer resolves. This one is
     * "nothing was ever set", which is ordinary and worth listing rather than worth warning about -
     * a signal pairing can otherwise only be checked one station at a time.
     */
    private java.util.Set<TileKey> stationsWithNoSignal()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (graph == null) return out;

        for (TileKey tile : graph.getTiles().keySet())
        {
            if (!store.isStation(tile)) continue;

            if (store.getProtectingSignals(tile).isEmpty()) out.add(tile);
        }

        return out;
    }

    /**
     * Squares whose recorded facing is not one the square can actually hold.
     *
     * The facing says which way the train standing there points, and the build honours it by choosing
     * the copy of the split square that faces that way.  When no copy does, the builder falls back to
     * the first copy trains may arrive at (`startableCopy`) - it has to place the train somewhere - and
     * that copy may point the other way.
     * So the train quietly turns round.
     *
     * The way squares stop being able to hold a facing is that the track around them changes: move a
     * tile so that it is entered from different sides and the facings it offers change with it, while
     * the recorded one stays as it was.  Adam met exactly that - "the locomotive direction suddenly
     * changed" after moving a tile onto valid connected track.
     *
     * Reported rather than corrected, because there is no correct answer available here: the train is
     * physically pointing whichever way it is pointing, and only the operator knows.  What was missing
     * was being told at all.
     *
     * A square with NO facings is left out - that is a disconnected square, which has its own findings
     * and does not need this one on top.
     */
    private java.util.Set<TileKey> facingsThatCannotBeHeld()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            Side recorded = getFacing(placed.getKey());

            if (recorded == null) continue;

            List<Side> offered = facingChoices(placed.getKey());

            if (offered.isEmpty() || offered.contains(recorded)) continue;

            // A TRAIN THAT TURNED ROUND used to be excused here, and is now simply offered.
            //
            // The excuse was `isTurnAround(tile) && arrivalSides(tile).contains(recorded)`, and it was
            // right about the railway: on a square a train may turn round on, the facing a turning copy
            // holds IS an arrival side, so reporting it as impossible reported the one correct answer.
            // What it was wrong about is WHERE that belongs. `facingChoices` is the answer to "which
            // facings can this square hold", and it did not know this one - so the checker forgave a
            // facing the menu would not offer, and the disagreement was invisible from either side.
            //
            // OB-145 is what that cost: BottomMainC could hold two facings and was offered one, and
            // `buildFacingMenu` returns null below two, so the question disappeared from the menu.
            // `facingChoices` now includes them, which makes the test above true by construction and
            // this carve-out dead - and dead code that looks load-bearing is worse than none, because
            // the next person to touch `facingChoices` would think this still protected them.
            out.add(placed.getKey());
        }

        return out;
    }

    private java.util.Set<TileKey> signalsThatAreGone()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (graph == null) return out;

        // ANY of them, not all: a station paired to two signals of which one has gone is protected on
        // one approach and not on the other, which is exactly the state worth warning about.
        //
        // BOTH GUARDS (FR-096): an entry-guard signal that has gone is dropped from the build the same way, and the
        // way into that station is then as unguarded as a platform whose protecting signal went.
        Map<TileKey, List<TileKey>> both = new LinkedHashMap<>(store.getProtectingSignals());

        for (Map.Entry<TileKey, List<TileKey>> entry : store.getEntrySignals().entrySet())
        {
            List<TileKey> merged = new ArrayList<>(both.containsKey(entry.getKey())
                ? both.get(entry.getKey()) : java.util.Collections.<TileKey>emptyList());

            for (TileKey signal : entry.getValue()) if (!merged.contains(signal)) merged.add(signal);

            both.put(entry.getKey(), merged);
        }

        for (Map.Entry<TileKey, List<TileKey>> pair : both.entrySet())
        {
            // NOT ABOUT A PAGE THAT IS SWITCHED OFF (SVN-C6).
            //
            // `getProtectingSignals()` is the raw store, so a pairing whose station sits on an excluded
            // page finds no tile in the graph - the graph does not carry that page - and reports the
            // station's protecting signal as GONE.  The sibling check three thousand lines up got this
            // filter and this one did not.  Not reachable on Adam's setup today, where no pairing
            // crosses an excluded page; reachable the moment one does.
            if (store.getExcludedPages().contains(pair.getKey().getPage())) continue;

            // THE SIGNAL SIDE IS NOT FILTERED, and that asymmetry is the point (REL-B1).
            //
            // A pairing whose STATION is on an excluded page is not in play, so a warning about it is
            // noise - that is the filter above.  A signal on an excluded page is a different thing:
            // `protectingSignalNames` resolves signals through `graph.getTiles()`, which does not
            // carry an excluded page, so that signal really IS dropped from the built configuration
            // and the platform really IS unprotected.  Filtering here silenced a warning that was
            // true, which is the one direction this check must never fail in.
            for (TileKey tile : pair.getValue())
            {
                LayoutDiagramComponent signal = graph.getTiles().get(tile);

                if (signal == null || signal.getAccessory() == null)
                {
                    out.add(pair.getKey());
                    break;
                }
            }
        }

        return out;
    }

    /**
     * Whether every train arriving at this square is forced to turn round (OB-123).
     *
     * The question behind "may change direction here": the setting offers a choice only if a train
     * that has arrived could instead CARRY ON, and carrying on means leaving by some side other than
     * the one it came in by.
     *
     * True only when that is impossible from every direction. One arrival with somewhere to go is
     * enough to make the setting mean what it says, and the warning would then be telling the operator
     * their railway is something it is not - which is worse than staying quiet, because they would
     * change it.
     *
     * Departures are gathered the way `departureSides` gathers them, the same way the trapped-arrival
     * check twenty lines below does: it is the same question about the same square, and two spellings
     * of it would eventually disagree.
     *
     * @param graph the tile graph
     * @param reducer the reduced graph, for the edges
     * @param tile the square
     * @return whether turning is the only thing any arriving train can do
     */
    private boolean everyArrivalMustTurn(TileGraph graph, GraphReducer reducer, TileKey tile)
    {
        Set<Side> departures = new LinkedHashSet<>();

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            if (edge.getStart().equals(tile) && edge.getExitSide() != null)
            {
                departures.add(edge.getExitSide());
            }
        }

        java.util.Collection<Side> arrivals = arrivalSides(tile);

        // Nothing arrives here at all, so there is no train to be offered a choice. Left to the checks
        // that are about unreachable squares rather than reported as a pointless setting.
        if (arrivals.isEmpty()) return false;

        for (Side arrival : arrivals)
        {
            Set<Side> onwards = new LinkedHashSet<>();

            for (TileGraph.Exit exit : graph.exits(tile, arrival))
            {
                if (exit.getSide() != null && departures.contains(exit.getSide()))
                {
                    onwards.add(exit.getSide());
                }
            }

            onwards.remove(arrival);

            // One way on, from one direction, and the setting means what it says.
            if (!onwards.isEmpty()) return false;
        }

        return true;
    }

    /**
     * The squares of the active configuration whose stored point satisfies a test (WK3-C3).
     *
     * **Four methods were walking this map with four copies of the same six lines**: is there an
     * active configuration, does it have `points`, is this entry an object, does it match, parse the
     * key, keep it if it parsed.  `homeTiles` fed the findings the diagram shows, `tilesWithAHome`
     * decided what the bulk clear touches, `homesElsewhere` added one filter, and `shutTiles` - added
     * on 2026-09-05 - made a fourth.
     *
     * They have to agree.  If one gains a qualification - homes on excluded pages, squares the diagram
     * no longer draws - the others will not have it, and the button will act on a set the findings
     * never mentioned.  That is this repository's commonest defect and it had four sites here.
     *
     * The parse splits on the LAST colon, because a page name may hold one; that rule lives on the
     * store and is now called from one place rather than four.
     *
     * @param wanted asked of each square's key and its stored point
     * @return the squares that matched, in the file's own order
     */
    private java.util.List<TileKey> tilesWhere(
        java.util.function.BiPredicate<String, org.json.JSONObject> wanted)
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        String active = store.getActiveConfiguration();

        org.json.JSONObject configuration = active == null ? null : store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return out;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String key : points.keySet())
        {
            org.json.JSONObject point = points.optJSONObject(key);

            if (point == null || !wanted.test(key, point)) continue;

            TileKey tile = AutonomyCompanionStore.parseTileKey(key);

            if (tile != null) out.add(tile);
        }

        return out;
    }

    /**
     * What `homeTiles()` answers, for the test that keeps the home walks in step (WK3-C3).
     *
     * `homeTiles()` is private and feeds `check()`; this is the same answer, named so a test can ask
     * whether it matches `tilesWithAHome()` - the two that must agree, and the two that were
     * separate walks.
     *
     * @return the squares the findings treat as carrying a home
     */
    public java.util.Set<TileKey> homesForFindings()
    {
        return homeTiles();
    }

    /**
     * `homesElsewhere` under a name a test can call (WK3-C3).
     *
     * @param tile the square being asked about
     * @param locomotive the home locomotive
     * @return the other squares homing it
     */
    public java.util.List<TileKey> homesElsewhereForTest(TileKey tile, String locomotive)
    {
        return homesElsewhere(tile, locomotive);
    }
    /**
     * Whether a stored point records a home locomotive.
     *
     * One spelling of "carries a home" - a blank is none.  It has one caller; the other reads of the
     * key either ask WHICH locomotive, which is a different question, or spell this answer out where
     * they stand (DCN-C12).
     *
     * @param point the stored point
     * @return true when it names one
     */
    private static boolean carriesAHome(org.json.JSONObject point)
    {
        return !point.optString("home", "").trim().isEmpty();
    }
    /**
     * The squares an authored home locomotive lives at.
     *
     * The same answer `tilesWithAHome` gives, as a set, because this is what `check()` compares
     * against.  Delegating rather than walking again is the whole of WK3-C3: when these two drifted,
     * the bulk clear acted on track the findings never mentioned.
     *
     * @return the squares carrying a home
     */
    private java.util.Set<TileKey> homeTiles()
    {
        return new LinkedHashSet<>(tilesWithAHome());
    }

    /**
     * Everything wrong or worth knowing about the setup as it stands.
     * @return
     */
    public List<AutonomyChecks.Finding> check()
    {
        // Guarded because a panel builds its list in its constructor, and nothing yet forces open() to
        // have been called first - so an unopened session would throw out of a constructor, which is a
        // much harder failure to read than an empty list.
        if (graph == null || reducer == null) return new ArrayList<AutonomyChecks.Finding>();

        // The terminus flag lives in the configuration, so the checks are told rather than left to
        // infer it from the shape of the graph.
        Set<TileKey> termini = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) termini.add(tile);
        }

        // "May turn round here" where no arriving train could do anything else (OB-123).
        //
        // Adam, 2026-08-27: "this is wrong: the train can continue or reverse.  It should test for two
        // outgoing paths, not two incoming."  This counted ARRIVAL sides and called the setting
        // pointless below two, which asks the wrong half of the question: carrying on is a DEPARTURE,
        // so a square with one way in and two ways out offers every arriving train exactly the choice
        // the setting describes and was being told the choice did not exist.
        //
        // Per ARRIVAL rather than by counting departures, which is his rule made exact. A plain count
        // gets one case wrong: two arrivals and one departure forces a turn on the train that arrived
        // by the departure side and offers a genuine choice to the one that did not, so the setting is
        // not pointless there. It is pointless only when EVERY arrival is a forced turn - which is
        // what "every train turns round anyway" already claims.
        //
        // Arrivals still come from arrivalSides rather than a walk of our own: the walk and the door
        // differ on one case - a square an edge lands at having arrived by no side of the grid - and
        // the answer has to be the BUILD's, because the build is what the user is being told about
        // (DR-B6/DD-A7).
        Set<TileKey> pointless = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (!isTurnAround(tile) || isMustTurnAround(tile)) continue;

            if (everyArrivalMustTurn(graph, reducer, tile)) pointless.add(tile);
        }

        // Built once, up here, because the checks below are ABOUT the build (DD-A7).
        //
        // `AutonomyChecks` is a predictor, not a second authority: its whole job is to tell the
        // operator what the railway will do before they trust trains to it, so where the build can
        // answer, the build's answer is the right one and a second derivation can only be a chance to
        // disagree.  `AutonomySession.check` used to work several of these out for itself by walking
        // the reduction, which is the family DD-A7 named.
        //
        // The line is drawn by severity, and it is not arbitrary: an ERROR says the setup will not
        // build at all, so there is no build to inspect and those checks must reason about the diagram.
        // Everything softer describes a railway that WILL exist, and asks it.
        org.json.JSONObject inspected = builtForInspection();

        java.util.Map<String, TileKey> namesForInspection =
            inspected == null ? null : builder(null).tilesByName();

        // Squares a train can reach and then not leave: it arrived by one side, the only way on is back
        // out of that same side, and nobody has said trains may turn round there.
        //
        // Read off the built graph rather than walked here.  The two agreed - `testTheCheckerAgreesWith
        // TheBuild` says so - but they agreed by having been written to agree, and the walk carried
        // three careful comments about matching what the builder does, each of which is a note that the
        // two could come apart.  Now there is one answer.
        //
        // Nothing is reported when the setup will not build, which is the same rule the copy checks
        // already follow: a setup that will not build has louder problems than a trapped arrival, and
        // every one of them is already on the list.
        Set<TileKey> trapped = tilesWithATrappedArrival(inspected, namesForInspection);

        // Captions the user’s own writing is sitting on top of.
        //
        // The square belongs to the diagram - it is their drawing before it is autonomy’s data - so
        // the text wins the square and the caption is the one that goes quiet.  Worth saying rather than
        // silently losing: a station that looks captioned and shows nothing is exactly the puzzle this
        // whole rework exists to stop.
        Map<TileKey, TileKey> covered = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> caption : store.getCaptions().entrySet())
        {
            LayoutDiagram page = pageOf(caption.getKey());

            if (page == null) continue;

            LayoutDiagramComponent component =
                page.getComponent(caption.getKey().getX(), caption.getKey().getY());

            if (component == null || component.getLabel() == null) continue;

            if (!component.getLabel().trim().isEmpty()) covered.put(caption.getKey(), caption.getValue());
        }


        return AutonomyChecks.run(graph, reducer, termini, getLabelledStationTiles(), pointless,
            trapped, covered, placedLocomotives(), shutStations(),
            mayTurnTiles(), mandatoryTurnTiles(), homeTiles(), signalsThatAreGone(),
            stationsWithNoSignal(), facingsThatCannotBeHeld(),
            // The red arrows, so the findings walk the railway a train can actually use (OB-120).
            barredArrivals(),
            // And the squares switched out of service, which is a different claim: a barred side is
            // passed through and not stopped at, a closed square is not passed through at all
            // (V31-C3).
            shutTiles(),
            // The two halves of the length rule (FR-046), and the two of them disagreeing about one
            // platform (Adam, 2026-09-11).
            placedTrainsWithoutLength(), stationsWithoutMaxLength(), runInsShorterThanTheBerth(),
            // AND THE HALF-MEASURED APPROACH, which closes a berth to everything until it is finished
            // (Adam, 2026-09-19, on RTX-C2: *"we want clear warnings to the user"*).
            stationsWithAHalfMeasuredApproach(),
            // A terminus with two ways in, which is a statement that cannot be true (MT-361).
            terminiWithTwoWaysIn(),
            // Pages sharing one sensor with another, which cannot be modelled at all (OB-150).
            repeatedSensorPages(),
            // Squares trains reverse at that nobody has measured (Adam, 2026-09-01).
            reversalsWithoutLength(),
            // Stations autonomy will never choose, so a pocket holding only those still leads
            // nowhere (V36-C4).
            stationsAutonomyWillNotChoose(),
            // Copies a train could be sent to and never leave, and copies nothing can reach at all
            // (Adam, 2026-09-02).  Per COPY, which is what every other check here cannot see.
            //
            // ONE BUILD FOR THE THREE OF THEM (D3F-C6).  Each used to build the configuration for
            // itself and ask the builder for its own name map, so one `check()` was three full graph
            // builds - on paths whose own comment already reads "four full walks of the railway on the
            // event thread, every time somebody right-clicks a station".
            destinationCopiesWithNoWayOut(inspected, namesForInspection),
            destinationCopiesWithNoWayIn(inspected, namesForInspection),
            destinationCopiesReachingNoStation(inspected, namesForInspection),
            // A station's two guards as one signal, and a guard no way into its station passes (AUT-C2).
            guardsOnBothLists(), guardsOffTheWayIn(),
            // Every square unavailable while another is occupied, station or not (Adam, 2026-09-24).
            restrictionsToList());
    }

    /**
     * Every square held back while another is occupied, against the names of what it watches (Adam, 2026-09-24: *"a
     * simple info notice on restrictions (in the list for any type of station or non station)"*).
     *
     * Not on an excluded page, as the guards' notices are not: nothing there is built.
     *
     * @return the restricted squares, each against its watched squares' names
     */
    private Map<TileKey, String> restrictionsToList()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        if (store == null) return out;

        for (Map.Entry<TileKey, List<TileKey>> held : store.getBlockingPoints().entrySet())
        {
            if (store.getExcludedPages().contains(held.getKey().getPage())) continue;

            List<String> names = new ArrayList<>();

            for (TileKey watched : held.getValue())
            {
                String name = getStationIndex() == null ? null : getStationIndex().nameOf(watched);

                names.add(name == null ? String.valueOf(watched) : name);
            }

            out.put(held.getKey(), String.join(", ", names));
        }

        return out;
    }

    /**
     * Stations whose entry guard is also their exit guard, against those signals' names (AUT-C2).
     *
     * The setters refuse it; loading a setup does not go through them, so a file written before the rule, or by hand,
     * can still carry it.
     *
     * @return station square to the names of the signals on both its lists
     */
    private Map<TileKey, List<String>> guardsOnBothLists()
    {
        Map<TileKey, List<String>> out = new LinkedHashMap<>();

        if (graph == null) return out;

        Map<TileKey, List<TileKey>> exits = store.getProtectingSignals();

        for (Map.Entry<TileKey, List<TileKey>> entry : store.getEntrySignals().entrySet())
        {
            if (store.getExcludedPages().contains(entry.getKey().getPage()) || !exits.containsKey(entry.getKey())) continue;

            for (TileKey signal : entry.getValue())
            {
                if (exits.get(entry.getKey()).contains(signal))
                {
                    out.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(signalName(signal));
                }
            }
        }

        return out;
    }

    /**
     * Guards - entry or exit - that no way into their station passes, against those signals' names (AUT-C2).
     *
     * Adam, 2026-09-24: *"if the guard signal is not on a path leading to the chosen station, we can add notice to the
     * autonomy editor."*  A signal whose tile has gone is `signalsThatAreGone`'s, not this; and a station on a page left
     * out of autonomy is not in play.
     *
     * @return station square to the names of its guards off every way in
     */
    private Map<TileKey, List<String>> guardsOffTheWayIn()
    {
        Map<TileKey, List<String>> out = new LinkedHashMap<>();

        if (graph == null || reducer == null) return out;

        Map<TileKey, java.util.Set<TileKey>> guards = new LinkedHashMap<>();

        for (Map<TileKey, List<TileKey>> list : Arrays.asList(store.getProtectingSignals(), store.getEntrySignals()))
        {
            for (Map.Entry<TileKey, List<TileKey>> entry : list.entrySet())
            {
                guards.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
        }

        for (Map.Entry<TileKey, java.util.Set<TileKey>> station : guards.entrySet())
        {
            if (store.getExcludedPages().contains(station.getKey().getPage())) continue;

            for (TileKey signal : station.getValue())
            {
                if (!graph.getTiles().containsKey(signal) || onAWayInto(station.getKey(), signal)) continue;

                out.computeIfAbsent(station.getKey(), k -> new ArrayList<>()).add(signalName(signal));
            }
        }

        return out;
    }

    /**
     * Whether a signal lies on track a train reaching this station runs over since it last left a station (AUT-C2).
     *
     * Walked back along the reduced edges that arrive at the station, through the sensors between, and stopped at the
     * first station on each way back - so a signal round a loop, on some other station's approach, is not "on the way
     * in" merely because every track on a loop eventually leads everywhere.
     *
     * @param station the station's square
     * @param signal the signal's square
     * @return whether some way into the station passes it
     */
    private boolean onAWayInto(TileKey station, TileKey signal)
    {
        java.util.Deque<TileKey> toVisit = new java.util.ArrayDeque<>();
        java.util.Set<TileKey> seen = new java.util.HashSet<>();

        toVisit.add(station);
        seen.add(station);

        while (!toVisit.isEmpty())
        {
            TileKey at = toVisit.poll();

            for (GraphReducer.ReducedEdge edge : reducer.getEdges())
            {
                if (!at.equals(edge.getEnd())) continue;

                for (GraphReducer.TileStep step : edge.getPath()) if (signal.equals(step.getTile())) return true;

                GraphReducer.ReducedPoint before = reducer.getPoints().get(edge.getStart());

                if (before != null && !before.isStation() && seen.add(edge.getStart())) toVisit.add(edge.getStart());
            }
        }

        return false;
    }

    /** A signal's name as the diagram knows it, or its square's */
    private String signalName(TileKey signal)
    {
        org.traincontrol.base.LayoutDiagramComponent component = graph == null ? null : graph.getTiles().get(signal);

        if (component != null && component.getAccessory() != null) return component.getAccessory().getName();

        return String.valueOf(signal);
    }

    /**
     * How many things must be dealt with before this setup will run.
     *
     * The one definition of "broken", because there were two and they disagreed. Starting autonomy is
     * refused when the checks report any ERROR (OB-057), while the diagram strip decided what to offer
     * from hasBlockingProblems() - which asks the GRAPH only, about scissors crossings and unpaired
     * links. A setup with four unnamed stations has no blocking problem and four errors, so the strip
     * went on showing a live Start button that every press of was refused.
     *
     * Adam, OB-090: "the fix it button is not shown, rather just start autonomy when the config had
     * worked before."
     *
     * Blocking problems are counted through check() rather than added to it - AutonomyChecks copies
     * every one of them in as an ERROR finding - with hasBlockingProblems() kept in the disjunction
     * below because check() returns nothing at all when the graph has not been derived yet.
     *
     * @return the number of ERROR findings
     */
    public int errorCount()
    {
        int errors = 0;

        for (AutonomyChecks.Finding finding : check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) errors++;
        }

        return errors;
    }

    /**
     * Whether anything must be dealt with before this setup will run.
     *
     * NOT the question the LOAD door asks, and that is deliberate (SVN-B10).  Loading a configuration
     * is how somebody gets at its errors in order to fix them, so refusing to load an errored setup
     * would be a guard with no way past it.  `AutonomyViewerPanel.load` asks hasBlockingProblems()
     * instead - can this be built at all - which is the right question for opening something.
     *
     * Wider than hasBlockingProblems(): that one asks whether the graph can be BUILT, this one whether
     * the setup can be RUN, and the second is the question every affordance that offers to run it has
     * to ask. See errorCount().
     *
     * @return
     */
    public boolean hasErrors()
    {
        return hasBlockingProblems() || errorCount() > 0;
    }

    /**
     * Whether anything would stop this being built.
     * @return
     */
    public boolean hasBlockingProblems()
    {
        return graph != null && graph.hasBlockingProblems();
    }

    /**
     * How many of them there are, for the doors that put the number in a sentence (DY3-C7).
     *
     * Here rather than in a panel, because two doors say this and one of them was counting by hand:
     * the load door picked between a singular and a plural message, the start door took the singular
     * unconditionally, and with three blocking problems it told the operator that one thing had to be
     * dealt with.
     *
     * @return the number of problems that would stop the setup being built, or zero when there is no
     *         graph to ask
     */
    public int blockingProblemCount()
    {
        if (graph == null) return 0;

        int blocking = 0;

        for (TileGraph.Problem problem : graph.getProblems())
        {
            if (problem.isBlocking()) blocking++;
        }

        return blocking;
    }

    // --- editing ----------------------------------------------------------------------------------

    public void setDirection(TileKey tile, RouteId routeId, Direction direction)
    {
        record(tile, routeId, direction);
        touched();
    }

    /**
     * Records a direction without re-deriving, for callers that are about to set several.
     */
    private void record(TileKey tile, RouteId routeId, Direction direction)
    {
        graph.setDirection(tile, routeId, direction);

        // stored only when it differs from what the graph would default to, so a default never looks
        // like a decision somebody made
        store.setTileDirection(tile, routeId,
            direction == graph.defaultDirection(tile, routeId) ? null : direction);
    }

    /**
     * Applies one direction to many tiles at once.
     *
     * The reason bulk editing matters rather than being a convenience: switches default to base-to-forks,
     * so on a real layout most of the setting up is opening trailing moves, and doing that one tile at a
     * time would be the bulk of the work.
     *
     * @param tiles
     * @param direction
     */
    public void setDirection(Set<TileKey> tiles, Direction direction)
    {
        // Recorded first and re-derived once at the end.  Going through the single-tile setter would
        // rebuild the entire graph per route - forty tiles meaning forty full rebuilds on the event
        // thread, for the gesture that exists precisely because it is the common one.
        for (TileKey tile : tiles)
        {
            for (RouteId routeId : graph.getRoutes(tile).keySet())
            {
                record(tile, routeId, direction);
            }
        }

        touched();
    }

    /**
     * Applies a direction to each of one tile's routes separately, re-deriving once.
     *
     * The per-branch counterpart to the bulk setter above, and needed for the same reason: the two
     * states a junction has - trains converging on its single track, and trains leaving it - are
     * DIFFERENT Direction constants on different branches, because TOWARD_A names a route's own first
     * side and nothing makes those agree.  Setting them one at a time through the single setter would
     * rebuild the graph once per branch.
     *
     * @param tile
     * @param directions route to the direction it should take
     */
    public void setDirections(TileKey tile, Map<RouteId, Direction> directions)
    {
        for (Map.Entry<RouteId, Direction> entry : directions.entrySet())
        {
            record(tile, entry.getKey(), entry.getValue());
        }

        touched();
    }

    /**
     * Tells autonomy to use, or to ignore, a link.
     *
     * @param tile the link
     * @param disabled true to leave it out of the railway entirely
     */
    public void setPortalDisabled(TileKey tile, boolean disabled)
    {
        store.setPortalDisabled(tile, disabled);
        touched();
    }

    /**
     * Renames a station.
     *
     * Nothing else has to happen.  A caption points at the station’s SQUARE, so it follows a rename
     * without being touched - which is the whole reason captions moved out of the diagram.  This used to
     * rewrite every page showing the old name, could fail halfway, and destroyed anything on those pages
     * that the layout parser could not model.
     */
    public void setPointName(TileKey tile, String name)
    {
        // REFUSED, not stored (TDR-C11): the doors that take a typed name ask `whyNotAPointName` first and
        // say so; this is the guard behind them, so a new door cannot forget.  Names already in a setup, or
        // brought in by an import, go through the store and are left alone.
        String why = whyNotAPointName(name);

        if (why != null) throw new IllegalArgumentException(why);

        store.setPointName(tile, name);
        touched();
    }

    /**
     * Why a typed name may not be given to a square, or null when it may (TDR-C11).
     *
     * A name ending in the builder's own direction heading - "Main (eastbound)" - reads as another square
     * wherever a message names squares, because that heading is exactly what is stripped from a copy's name.
     * Adam, 2026-09-14: *"refuse and close, but make sure the words are uncommon"* - so only the builder's
     * form is refused, and exactly the form the stripping removes; `StationIndex.endsWithAnArrivalHeading`
     * says what that is (FTN-C1).
     *
     * @param name the name as typed
     * @return the sentence to show, or null
     */
    public static String whyNotAPointName(String name)
    {
        if (!StationIndex.endsWithAnArrivalHeading(name)) return null;

        return I18n.f("autosetup.ui.errorNameEndsWithAHeading", name.trim());
    }

    public void setStation(TileKey tile, boolean station)
    {
        // A SQUARE THAT IS NO LONGER A STATION REMEMBERS ITS MAXIMUM TRAIN LENGTH, and has it again when it is made a
        // station again (Adam, 2026-09-24, OB-291: *"make max train length be remembered if a station is changed to a
        // non-station, and then restored if it is changed back to a station.  don't modify the behavior of this
        // attribute: it is still to be ignored for non-stations."*).  It was taken off here (SET-C2) because the bulk
        // clear counted every square carrying one.  Ignored where it is kept: the running layout reads it only of a
        // destination (`Point.validateTrainLength`), the station menu is the only door that shows it, and every reader
        // here asks about stations - `tilesWithAMaxTrainLength` and `modelsAnyLength` included.
        store.setStation(tile, station);

        // A caption names a station, so demoting one takes its name plaque with it.
        //
        // Left behind, the plaque outlives the thing it names: the square is drawn as a plain point
        // and the label under it stays registered, so a train reversing there lights up a station
        // name for a place that is no longer a station.  Which is exactly what happened, and read as
        // the diagram contradicting itself.
        //
        // Promotion already places a caption, so the pair is symmetrical - and re-promoting gives the
        // plaque back, on the square the placer picks.
        if (!station) clearCaptions(tile, null);

        // And so does any restriction on how trains may arrive at it.  It is inert while the square is
        // not a station, so leaving it costs nothing today and everything the day somebody makes the
        // square a station again and finds it refusing trains for a reason recorded months ago.
        if (!station) store.setBarredArrivals(tile, null);

        // And the signal that protected it: a plain point is not somewhere trains are held out of.
        if (!station) store.setProtectingSignal(tile, null);

        // And its entry guard (FR-096), for the same reason: nothing arrives at a plain point.
        if (!station) store.setEntrySignals(tile, null);

        // NOT being unavailable while another square is occupied (Adam, 2026-09-24, reversing AMS-B2: "do allow
        // restrictions on non-stations, and let's not clear them when the type changes").  It stays in force on a square
        // that is not a station - the build locks every route into it against the watched square - and it is taken off
        // where it was put on, under Advanced Parameters, which every sensor square has.  The findings list names it
        // whatever the square is (UNAVAILABLE_WHILE_OCCUPIED).

        touched();
    }

    /**
     * The name of the locomotive this configuration puts on a square.
     *
     * A placement is an OBJECT - {"name": ..., "speed": ..., "arrivalFunc": ...} - because parseAuto
     * resets whatever a placement omits, so a train's length and functions have to travel with its
     * name rather than beside it.  Anything wanting only the name therefore has to unwrap it, and
     * String.valueOf on a JSONObject gives its JSON: the autonomy editor drew {"name":"EN57-203"}
     * across three tiles of somebody's railway.
     *
     * One place that knows the shape, because there were three and they did not agree.
     *
     * @param tile the square
     * @return the locomotive's name, or null when nothing is placed here
     */
    public String getLocomotiveNameAt(TileKey tile)
    {
        if (tile == null) return null;

        return nameOfPlacedLocomotive(getPointProperty(tile, "loc"));
    }

    /**
     * Whether this square is one that can hold a name.
     *
     * A name belongs to a Point - a square the reduced graph knows, which is to say one with a
     * sensor. The right-click menu has always asked this before offering Rename; Control+S did
     * not, and named plain track instead, which is MT-313: *"it works on any tile in the autonomy
     * editor, not just sensors."* A name written on track the reducer has no Point for is held by
     * nothing and read by nobody.
     *
     * @param tile the square
     * @return whether naming it means anything
     */
    public boolean canBeNamed(TileKey tile)
    {
        // THE REDUCER, which is where "is this a Point" lives.  The right-click menu has always
        // asked exactly this before offering Rename; the key asked nothing, and the two doors
        // disagreed about one question.
        return tile != null && getReducer() != null && getReducer().getPoints().containsKey(tile);
    }

    /**
     * Puts a locomotive on a point without disturbing anything else known about it.
     *
     * parseAuto RESETS whatever a placement omits - train length to zero, reversible to false, the
     * arrival and departure functions to none - so writing a bare {"name": X} over an existing
     * placement silently dropped all of them.  Anything already recorded is carried across.
     *
     * @param tile
     * @param name the locomotive, or null to clear the placement
     */
    public void placeLocomotive(TileKey tile, String name)
    {
        if (name == null)
        {
            setPointProperty(tile, "loc", null);

            // The facing described the train that was standing there, not the square.  Left behind,
            // the next locomotive placed here inherits the last one's direction without being asked.
            setPointProperty(tile, AutonomyBuilder.FACING, null);

            // AND SO DID THE ARRIVAL SIDE (IND9-A1), for the same reason and more strongly: where a
            // train's tail lies is a fact about that train and nothing else.  Left behind, parseAuto
            // reads it back onto whatever is placed here next, and the walk that blocks track behind a
            // standing train follows it - so track behind train B is refused on the strength of where
            // train A came in, silently, and it survives a save.
            //
            // The comment three lines above said this about the facing when the facing was fixed. The
            // sibling was not swept then; it is now.
            setPointProperty(tile, "arrivedFrom", null);

            // AND THE ROAD, which describes the same arrival (WK7-B1).
            setPointProperty(tile, "arrivedAlong", null);

            return;
        }

        Object existing = getPointProperty(tile, "loc");

        org.json.JSONObject loc = existing instanceof org.json.JSONObject
            ? new org.json.JSONObject(existing.toString()) : new org.json.JSONObject();

        loc.put("name", name);

        // And wherever else it was, it is not there now.
        //
        // Moving a train in the running layout takes it off the point it was on; the configuration is
        // never told, and the configuration is what the next build reads.  So a locomotive placed by
        // hand kept its old placement as well, the build emitted it at two Points, and fromJSON
        // answers that by invalidating the WHOLE layout - after which every path is refused as
        // "configuration is invalid", with nothing pointing back at the placement that caused it.
        //
        // Found in a real export: 065 001-0 DB standing at both BottomMainA and BottomMainC.
        forgetPlacementsElsewhere(tile, name, loc);

        setPointProperty(tile, "loc", loc);

        // AND A NEW OCCUPANT DOES NOT INHERIT THE OLD ONE'S TAIL (IND9-A1).
        //
        // `Point.setLocomotive` does exactly this on the running layout, and for the same reason: the
        // train arriving did not arrive the way the train leaving did.  The setup had no such rule, so
        // a square whose occupant changed through a door that does not ask - the editor's Place/Edit
        // Locomotive item is one - kept the previous train's arrival side.
        //
        // Cleared rather than guessed.  The doors that CAN work the side out write it immediately
        // after calling this, so nothing they record is lost; the doors that cannot now record nothing,
        // which is the honest answer and the one that blocks no track.
        if (!name.equals(nameOfPlacedLocomotive(existing)))
        {
            setPointProperty(tile, "arrivedFrom", null);
            setPointProperty(tile, "arrivedAlong", null);
        }
    }

    /**
     * Takes every locomotive off the setup, re-deriving the station index once (REL-C4).
     *
     * The twin of `clearEveryHome`, and it had the same cost for a worse reason: clearing one placement
     * writes TWO properties - the placement and the facing that described the train standing there - so
     * the loop that did it square by square cost 2N full builder constructions on the event thread, for
     * the gesture that exists precisely because doing it one at a time is too many.
     *
     * Through the same writer the single-square door uses, so the two cannot come to disagree about
     * what clearing a placement means; only the re-derive differs.
     *
     * @return how many squares were cleared
     */
    public int clearEveryPlacement()
    {
        java.util.List<TileKey> placed = new java.util.ArrayList<>(placedLocomotives().keySet());

        for (TileKey tile : placed)
        {
            writePointProperty(tile, "loc", null);

            // The facing described the train that was standing there, not the square - see
            // `placeLocomotive`, which is the door this mirrors.
            writePointProperty(tile, AutonomyBuilder.FACING, null);

            // And the arrival side with it, for the same reason (IND9-A1).  This loop and the single
            // -square door have drifted before, which is why each carries a note that they must agree.
            writePointProperty(tile, "arrivedFrom", null);
            writePointProperty(tile, "arrivedAlong", null);
        }

        // ONCE, at the end.  Not skipped: the split names are computed from these properties.
        deriveStationIndex();

        return placed.size();
    }

    /**
     * The locomotive named by a stored placement, or null when there is none.
     *
     * THE ONE PLACE THAT KNOWS THE SHAPE.  `getLocomotiveNameAt` is the same question asked of a
     * square rather than of a value, and it comes here; there were three readers before and they did
     * not agree.
     *
     * A placement is an OBJECT - {"name": ..., "speed": ..., "arrivalFunc": ...} - because parseAuto
     * resets whatever a placement omits, so a train's length and functions have to travel with its name
     * rather than beside it.  A BARE STRING is not a shape this program writes, but setup files get
     * edited by hand and an older autonomy.json may hold one.
     *
     * That second shape is why this is shared rather than reimplemented (RGD-C5).  This method is also
     * what the occupant-change guards ask, so answering null for a bare string made re-placing a train
     * on the square it is already standing on read as a CHANGE of occupant - and throw away an arrival
     * side that was still true, which is the exact case the guard exists to avoid.  Tail blocking then
     * goes quietly off for that square until the next arrival.
     *
     * @param placement whatever was stored under "loc"
     * @return the name, trimmed, or null
     */
    private static String nameOfPlacedLocomotive(Object placement)
    {
        if (placement == null) return null;

        String name;

        if (placement instanceof org.json.JSONObject)
        {
            // optString, not getString: a placement with no name is not this program's doing, but a
            // hand-edited file can carry one, and asking for a key that is not there throws.
            name = ((org.json.JSONObject) placement).optString("name", null);
        }
        else
        {
            // Drawing nothing on an occupied platform is the one wrong answer a label must not give.
            name = String.valueOf(placement);
        }

        if (name == null) return null;

        name = name.trim();

        return name.isEmpty() ? null : name;
    }

    /**
     * Takes a locomotive off every square except the one it is being put on.
     *
     * Whatever was recorded with it where it is leaving - speed, arrival and departure functions,
     * train length - is carried into the placement being written.  parseAuto RESETS what a placement
     * omits, which is the same reason this method preserves an existing placement above, so a move
     * that dropped them would quietly reset the train to no functions and no length.
     *
     * @param keep the square it is moving to
     * @param name the locomotive
     * @param into the placement being written, which inherits what was recorded elsewhere
     */
    private void forgetPlacementsElsewhere(TileKey keep, String name, org.json.JSONObject into)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return;

        org.json.JSONObject points = configuration.getJSONObject("points");

        boolean swept = false;

        for (String key : new LinkedHashSet<>(points.keySet()))
        {
            if (keep != null && key.equals(keep.toString())) continue;

            org.json.JSONObject extras = points.optJSONObject(key);

            if (extras == null) continue;

            org.json.JSONObject standing = extras.optJSONObject("loc");

            if (standing == null || !name.equals(standing.optString("name", null))) continue;

            for (String setting : standing.keySet())
            {
                if (!into.has(setting)) into.put(setting, standing.get(setting));
            }

            extras.remove("loc");
            extras.remove(AutonomyBuilder.FACING);

            // AND ITS TAIL (AMS-C1), as the other two placement doors clear it (IND9-A1, WK7-B1): where a train
            // came in is a fact about that train, and left here the next one placed on the square inherits it.
            extras.remove("arrivedFrom");
            extras.remove("arrivedAlong");

            swept = true;
        }

        // Once, not once per square swept: `touched` rebuilds the whole graph.
        if (swept) touched();
    }

    /**
     * The ways a train standing on this square could be pointing.
     *
     * One per copy the build emits for the square, which is what a train standing there can actually
     * be: the ONWARD side of the route it came in on, not the opposite of the side it entered by.
     * Those two agree on a straight and differ on a curve, and the simpler rule is "true by accident"
     * (MT-125, NR-7) - a train entering an N-E curve by N leaves by E, and saying it faces S describes
     * a train sitting across the rails.
     *
     * The sides come from arrivalSides - the same door the build splits on - rather than from a walk
     * of the reduced edges written out here, which is what this used to do and which is a second
     * author for one rule (DR-B6).  A square the door declines to split offers no facing at all, which
     * is right: the build emits it whole, with no facing recorded on it to offer.
     *
     * Ordered the way the builder orders its copies, because it IS the builder's order now.  The first
     * answer is NOT what a placement with no recorded facing gets: that is `startableCopy`'s choice, the
     * first copy trains may arrive at (GUI2-B1, TDY4-C1).  The order used to be a claim about two
     * methods agreeing, held up by a sentence and by `testTheCheckerAgreesWithTheBuild`; there is one
     * method now.
     *
     * @param tile
     * @return the possible facings, empty when nothing reaches the square and a single entry - which
     *         needs no asking about - when only one line does
     */
    public List<Side> facingChoices(TileKey tile)
    {
        // ASKED OF THE BUILD (DR-B6).
        //
        // This used to work the answer out for itself: the onward side of every arrival, plus the
        // arrival sides again on a square trains may turn at, in that order.  Every clause of that was
        // a sentence describing what `AutonomyBuilder` does - the comments said so, naming
        // `AutonomyBuilder.facingOf` as the thing being reproduced - and a description of another
        // method is a second author for one rule.
        //
        // `facingsFor` reads `StationIndex`, which reads `builder.facingByName()`.  So this is now the
        // build's own list of what its copies face, which is exactly the question the menu is asking:
        // a facing has to be one the build can HOLD, or `placementCopy` falls back to `startableCopy`
        // and quietly turns the train round.
        //
        // Everything the old derivation was careful about comes for free:
        //
        // - the ONWARD side rather than the opposite of the arrival, because that is what `facingOf`
        //   gives a plain copy (MT-125 - on a curve joining north to east a train entering by the north
        //   is pointing EAST, and south is a direction that square has no track in);
        // - the arrival side on a turn-around square, because the build emits a turning copy and gives
        //   it the arrival side (OB-145);
        // - the plain copy first, because `nodesFor` emits it first and `placementCopy` prefers it - a
        //   train standing where it MAY turn round has not turned round yet;
        // - nothing at all on a square the build emits whole, which has no facing to offer.
        //
        // The one thing lost is a fallback to `arrival.opposite()` where a square's track could not be
        // described.  It is not needed: if the build emitted a copy it gave that copy a facing, and if
        // it did not there is nothing to stand on.
        Set<Side> out = new java.util.LinkedHashSet<>(facingsFor(tile).values());

        return new ArrayList<>(out);
    }


    /**
     * The diagram tiles a standing train is lying across, for greying out (Adam, 2026-09-06).
     *
     * **"Locked tiles in this way should be greyed out until the train blocking it moves."**
     *
     * **The rule is not restated here, it is asked of the railway.**  `Layout` decides which edges a
     * train covers - one walk, three rulings - and this only translates that answer into the squares
     * the diagram draws.  Computing it a second time from the reducer would be quicker and would be
     * the mistake this codebase keeps making: two statements of one question that drift, which is
     * every second finding in the day's review reports.
     *
     * The join is by SQUARE.  A runtime `Edge` runs between two `Point`s, a `Point` maps back to the
     * square it was built from, and the reducer holds the tiles that lie between two squares.  Both
     * directions of a reduced edge are matched, because a covered edge is covered whichever way the
     * train came: the tail is lying across that rail either way.
     *
     * The endpoint squares are NOT included.  A train standing at a sensor already shows as standing
     * there, and greying the platform it is on would say the platform is blocked by something else.
     * Adam's ruling is about the track between: *"edges, because the points are technically
     * unoccupied"*.
     *
     * **AS FAR BACK AS THE TRAIN REACHES, AND NO FURTHER (MT-309).**
     *
     * Adam, 2026-09-08: *"after the parking completed, all of bottommaina stayed shaded, which it
     * shouldn't as I set the length of EN57-203 to 1."*
     *
     * The railway's answer is per EDGE, deliberately - a train lying across any part of a segment
     * makes the whole segment impassable, which is what a routing guard needs to know.  A DIAGRAM
     * needs something else: an edge is one hop between two sensors and can be a dozen squares long,
     * its length is the sum of the lengths assigned to those squares, and painting all of them washed
     * a whole run of track for a train one square long.  Measured on his railway: seventeen squares
     * for a train of length 1.
     *
     * So the squares are walked one at a time from where the train stands, each one paying its own
     * length, and the walk stops when the train has been used up.  What comes back is a SUBSET of
     * what the railway holds covered - which is the safe direction, and the only direction available:
     * a picture claiming more blocked track than the railway holds is the failure mode
     * `Layout.edgesCoveredByStandingTrains` was widened to avoid.  Nothing here changes what the
     * railway blocks or what any guard reading it decides.
     *
     * That is the indicator saying WHERE THE TRAIN IS rather than what is unavailable, which is what
     * Adam asked it to mean in the same message: *"we need to draw a line ... to show that the train
     * is there."*
     *
     * **What is unavailable is a different question, and it has its own answer**:
     * `tilesBlockedByStandingTrains` below, which the diagram greys while autonomy is running.  The
     * two were one method until 2026-09-08 and one mark until 2026-09-09, and separating them is what
     * lets each be right - Adam: *"'train is here' should also mean 'track is blocked' ... it's the
     * same as greying out edges, just in a different way."*
     *
     * @param running the layout, which is what knows where the trains are
     * @return the squares to draw as covered, empty when nothing is
     */
    public Set<TileKey> tilesCoveredByStandingTrains(org.traincontrol.automation.Layout running)
    {
        return routesCoveredByStandingTrains(running).keySet();
    }

    /**
     * The same answer, saying WHICH ROAD of each square the train is on (MT-309).
     *
     * Adam, 2026-09-08: *"instead of shading the entire tiles, we need to draw a line (let's say in
     * orange) to show that the train is there.  graying makes it look confusing on double curve
     * tiles."*  A double curve carries two roads that never meet, and a mark over the whole square
     * says a train is on both of them - which is the confusion he is describing.
     *
     * The road is not worked out here: `GraphReducer.TileStep` records the route each step of an edge
     * runs through, so the walk simply keeps what it already had in its hand.  Deriving it from the
     * geometry afterwards would be a second answer to a question the reduction has already answered.
     *
     * @param running the layout, which is what knows where the trains are
     * @return each covered square, with the routes of it the train is lying across
     */
    public Map<TileKey, Set<RouteId>> routesCoveredByStandingTrains(
        org.traincontrol.automation.Layout running)
    {
        Map<TileKey, Set<RouteId>> out = new LinkedHashMap<>();

        if (running == null || reducer == null || getStationIndex() == null) return out;

        // Grouped by TRAIN, because the walk below is a walk backwards from one train and the map the
        // railway hands back has thrown that away - it answers "is this edge covered", which is the
        // routing question rather than the drawing one.
        Map<org.traincontrol.base.Locomotive, Set<TileKey>> reach = new LinkedHashMap<>();

        for (Map.Entry<org.traincontrol.automation.Edge, org.traincontrol.base.Locomotive> covered
            : running.edgesCoveredByStandingTrains().entrySet())
        {
            org.traincontrol.automation.Edge edge = covered.getKey();

            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            TileKey from = getStationIndex().squareOf(edge.getStart().getName());
            TileKey to = getStationIndex().squareOf(edge.getEnd().getName());

            if (from == null || to == null) continue;

            Set<TileKey> squares = reach.get(covered.getValue());

            if (squares == null)
            {
                squares = new LinkedHashSet<>();

                reach.put(covered.getValue(), squares);
            }

            // Both ends, so the chain below can be followed by SQUARE.  The railway records each rail
            // twice, once per direction, and matching by square rather than by Edge identity makes the
            // pair one hop rather than two.
            squares.add(from);
            squares.add(to);
        }

        for (Map.Entry<org.traincontrol.base.Locomotive, Set<TileKey>> train : reach.entrySet())
        {
            walkBackFrom(running, train.getKey(), train.getValue(), out);
        }

        drawTheTrainsThatCoverNoEdge(running, reach.keySet(), out);

        return out;
    }

    /**
     * The trains the walk above cannot draw, drawn from the places the railway says they lie over (OB-290).
     *
     * The walk follows covered EDGES, and a tail that stops at a fork right behind its platform covers none.  With no
     * road to say which rail a train came in on, the railway claims the squares the rails by its side share, up to the
     * switch where they part, and stops (MT-477) - as places, which the grey is drawn from, and not as an edge.  So the
     * grey went down and the orange did not: Adam, 2026-09-24, *"75 407 DB gets no orange line at bottommaina"*, and on
     * MT-543, *"With not known, there is no orange tail."*  A train with no road is every train stood by hand, answered
     * Not known, or re-stood by a restart or an edit - none of which keeps the road it drove.
     *
     * Each claimed square is drawn along the road every edge through it agrees on.  Where they disagree - the switch
     * itself, whose legs are different roads - the square is listed and no line drawn: the train is on it, and on which
     * leg is not known.  The grey still says it is taken.
     *
     * @param running the layout
     * @param drawn the trains the walk above drew
     * @param out the squares to draw, added to
     */
    private void drawTheTrainsThatCoverNoEdge(org.traincontrol.automation.Layout running,
        Set<org.traincontrol.base.Locomotive> drawn, Map<TileKey, Set<RouteId>> out)
    {
        Set<String> claimed = new java.util.HashSet<>();

        for (Map.Entry<String, org.traincontrol.base.Locomotive> claim
            : running.placesCoveredByStandingTrains().entrySet())
        {
            if (!drawn.contains(claim.getValue())) claimed.add(claim.getKey());
        }

        if (claimed.isEmpty()) return;

        // Every road each claimed square is run along, by every edge naming the claim: a step by the route it records,
        // and the square an edge arrives at - a Point's, which is never a step - by the side the edge reaches it.
        Map<TileKey, Set<RouteId>> seen = new LinkedHashMap<>();

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            List<GraphReducer.Place> places = reducer.placesAlong(edge);

            List<GraphReducer.TileStep> path = edge.getPath();

            // One place per step, then the square the edge arrives at - `placesAlong`'s own shape.
            if (places.size() != path.size() + 1) continue;

            for (int at = 0; at < places.size(); at++)
            {
                if (!claimed.contains(places.get(at).getId())) continue;

                boolean onTheWay = at < path.size();

                TileKey tile = onTheWay ? path.get(at).getTile() : edge.getEnd();

                if (tile == null) continue;

                RouteId road = onTheWay ? path.get(at).getRouteId() : roadOfTheSensor(edge.getEnd(), edge.getStart());

                Set<RouteId> roads = seen.get(tile);

                if (roads == null)
                {
                    roads = new LinkedHashSet<>();

                    seen.put(tile, roads);
                }

                if (road != null) roads.add(road);
            }
        }

        for (Map.Entry<TileKey, Set<RouteId>> square : seen.entrySet())
        {
            Set<RouteId> roads = out.get(square.getKey());

            if (roads == null)
            {
                roads = new LinkedHashSet<>();

                out.put(square.getKey(), roads);
            }

            // ONE ROAD OR NONE: a line along two would say the train is on both legs of the switch.
            if (square.getValue().size() == 1) roads.addAll(square.getValue());
        }
    }

    /**
     * The diagram tiles standing trains have BLOCKED, for greying out (Adam, 2026-09-09).
     *
     * *"'train is here' should also mean 'track is blocked' - that is the whole point.  it's the same
     * as greying out edges, just in a different way."*  And, choosing how: *"can we just grey out the
     * tiles just like blocked edges while autonomy is running?  That plus the line, drawn and
     * refreshed carefully, should do the trick."*
     *
     * **The whole edge, which is what routing actually refuses.**  `routesCoveredByStandingTrains`
     * walks back only as far as the train reaches, because it answers "where is the train"; this one
     * answers "what may not be used", and the railway's answer to that is per EDGE - a train lying
     * across any part of a segment makes the whole segment impassable.  Between the two marks the
     * picture and the guard agree again, which they had not since the wash was removed.
     *
     * **Not restated, asked of the railway.**  `Layout.edgesCoveredByStandingTrains` decides which
     * edges a train covers - one walk, three rulings - and this only translates that answer into the
     * squares the diagram draws, through the same `pathBetween` the narrow answer uses.  Deriving it a
     * second time from the reducer would be quicker and would be the mistake this codebase keeps
     * making: two statements of one question that drift.
     *
     * **The endpoint squares are excluded**, which is `pathBetween`'s own rule and Adam's ruling about
     * the covered set: *"edges, because the points are technically unoccupied"*.  A train standing at
     * a sensor already shows as standing there, and greying the platform it is on would say the
     * platform is blocked by something else.
     *
     * WHILE AUTONOMY IS RUNNING is not decided here.  This says what is blocked; whether that is worth
     * drawing is a question about the window's state, and `TrainControlUI.refreshCoveredTrack` is
     * where it is asked.
     *
     * @param running the layout, which is what knows where the trains are
     * @return the squares to grey, empty when nothing is blocked
     */
    public Set<TileKey> tilesBlockedByStandingTrains(org.traincontrol.automation.Layout running)
    {
        return routesBlockedByStandingTrains(running).keySet();
    }

    /**
     * The same answer, saying WHICH ROAD of each square is blocked (OB-280).
     *
     * **What routing actually refuses: the squares standing trains claim.**  Adam, 2026-09-23: *"grey what routing
     * actually refuses.  if the train doesn't protrude past the switch, there should be nothing else to gray."*  So
     * the grey is `Layout.placesCoveredByStandingTrains` - the places the runtime's own tail walk claims, which is
     * what `isPathClear`'s shared-metal sweep refuses a path over (OB-207) - translated into squares.  A train that
     * fits on its berth claims that square alone (OB-278), and nothing past the switch behind it is grey.
     *
     * From OB-208 (earlier the same day) until this it was the whole of every covered EDGE, on the reasoning that a
     * path uses all of its own edges.  That is true of a path driving ALONG the covered edge, and a path doing that
     * ends at or runs through the square the train stands on, which is claimed; but a path crossing only the far
     * end of the edge is not refused, and the grey said it was.
     *
     * **Asked of the railway, not restated.**  The places come from the runtime; the reduction only says which
     * square, and which road of it, each place id names - the place ids `GraphReducer.placesAlong` built them from,
     * one per step and one for the square the edge arrives at.
     *
     * **Per road where the place is per road**: a double curve or an overpass carries two roads that never meet, and
     * its place ids name the road, so the tile fades that road and leaves the other at full strength - the band
     * `LayoutLabel.theRoadToFade` draws.  Everywhere else the place is the whole square, and it is returned with
     * no road named, which fades the whole tile: a train on one leg of a turnout blocks the other.
     *
     * @param running the layout, which is what knows where the trains are
     * @return each blocked square, with the routes of it that are blocked - none named where the whole square is
     */
    public Map<TileKey, Set<RouteId>> routesBlockedByStandingTrains(org.traincontrol.automation.Layout running)
    {
        Map<TileKey, Set<RouteId>> out = new LinkedHashMap<>();

        if (running == null || reducer == null || getStationIndex() == null) return out;

        Set<String> claimed = running.placesCoveredByStandingTrains().keySet();

        if (claimed.isEmpty()) return out;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            List<GraphReducer.Place> places = reducer.placesAlong(edge);

            List<GraphReducer.TileStep> path = edge.getPath();

            // One place per step, then the square the edge arrives at - `placesAlong`'s own shape.
            if (places.size() != path.size() + 1) continue;

            for (int at = 0; at < places.size(); at++)
            {
                String id = places.get(at).getId();

                if (!claimed.contains(id)) continue;

                boolean onTheWay = at < path.size();

                TileKey tile = onTheWay ? path.get(at).getTile() : edge.getEnd();

                Set<RouteId> roads = out.get(tile);

                if (roads == null)
                {
                    roads = new LinkedHashSet<>();

                    out.put(tile, roads);
                }

                // A PLACE THAT NAMES A ROAD blocks that road; one that names the square blocks all of it.
                if (onTheWay && id.indexOf('/') >= 0 && path.get(at).getRouteId() != null)
                {
                    roads.add(path.get(at).getRouteId());
                }
            }
        }

        return out;
    }

    /**
     * Paints one train's own length back along the track it is covering (MT-309).
     *
     * Walks square by square from where the train stands, deducting each square's assigned length,
     * and stops the moment the train has been used up.  Only squares the RAILWAY already holds
     * covered are ever reached: the chain is followed through the endpoints of the covered edges, so
     * this can only ever draw a subset of them.
     *
     * A square with no length assigned costs nothing, which is the same convention every other length
     * rule here uses - `getTileLength` answers 0 for unmeasured, and "only positive lengths are
     * determinate".  An unmeasured square is therefore drawn and the walk carries on; it cannot run
     * away, because it can only go as far as the covered edges reach.
     *
     * @param running the layout
     * @param train the locomotive
     * @param covered the endpoint squares of every edge this train covers
     * @param out the squares to draw, added to
     */
    private void walkBackFrom(org.traincontrol.automation.Layout running,
        org.traincontrol.base.Locomotive train, Set<TileKey> covered,
        Map<TileKey, Set<RouteId>> out)
    {
        if (train == null || train.getTrainLength() == null) return;

        int remaining = train.getTrainLength();

        if (remaining <= 0) return;

        // WHERE THE TRAIN IS, not which Point holds it (VD12-B1).  A locked path reserves every
        // on it and `getLocomotiveLocation` returns the first of them in iteration order - so this walk
        // used to spend the train's length from a square the train had not reached, and drew the orange
        // there.  `whereTheTrainIs` is the rule `DiagramMonitor`'s marker already followed.
        org.traincontrol.automation.Point standing = running.whereTheTrainIs(train);

        if (standing == null) return;

        TileKey at = getStationIndex().squareOf(standing.getName());

        if (at == null) return;

        Set<TileKey> walked = new LinkedHashSet<>();

        walked.add(at);

        // THE STANDING SQUARE IS SPENT FIRST, as the guard spends it (Adam, 2026-09-23, OB-278: *"the 2
        // length tile with the s88 consumes 2 units of the train"*, and *"Everywhere"*).  A train no longer
        // than the square it stands on is drawn on that square and nowhere behind it.  The orange says
        // where the train IS (OB-277); what it blocks is the grey, which is whole covered edges (OB-208)
        // and is not this walk.
        while (remaining > 0)
        {
            TileKey next = null;

            // The next covered square along, which is one of the endpoints the caller collected.  The
            // walk cannot double back: `walked` holds every square already passed, which is the same
            // guard `Layout`'s own tail walk uses against a loop of track.
            for (TileKey candidate : covered)
            {
                if (walked.contains(candidate)) continue;

                if (pathBetween(at, candidate) == null) continue;

                next = candidate;

                break;
            }

            if (next == null) return;

            // THE SENSOR SQUARES ARE DRAWN TOO (Adam, 2026-09-23, OB-277: *"when we draw orange lines,
            // they don't overlap with sensors"*, and on every sensor, occupied or not).  The square a
            // train stands on is a sensor, and so is every Point its tail lies back across - so leaving
            // them out broke the line at exactly the squares that say where the train is.  It used to be
            // deliberate - "a train standing there would be shown standing there" - and his ruling is
            // that the orange shows where the train is, the locomotive icon notwithstanding.
            //
            // The standing square is drawn once, when the first hop says which of its sides the body
            // leaves by - and then its length is spent, before any square behind it (OB-278).
            if (walked.size() == 1)
            {
                markTheSensor(out, at, next);

                remaining -= store.getTileLength(at);

                if (remaining <= 0) return;
            }

            List<GraphReducer.TileStep> between = pathBetween(at, next);

            for (GraphReducer.TileStep step : between)
            {
                Set<RouteId> roads = out.get(step.getTile());

                if (roads == null)
                {
                    roads = new LinkedHashSet<>();

                    out.put(step.getTile(), roads);
                }

                // Null on a step the reduction could not attribute to a route - a portal hop.  The
                // square is still covered; what cannot be said is which road of it.
                if (step.getRouteId() != null) roads.add(step.getRouteId());

                remaining -= store.getTileLength(step.getTile());

                if (remaining <= 0) return;
            }

            // The far end is the next square back and the body lies over it - so it is drawn (OB-277),
            // along the road that faces the track just walked, and its own length counts.
            markTheSensor(out, next, at);

            remaining -= store.getTileLength(next);

            walked.add(next);

            at = next;
        }
    }

    /**
     * The squares between two Points, in the order a train travelling from one to the other crosses
     * them, or null when the reduction knows of no edge joining them.
     *
     * Both directions are matched and the reversed one is reversed, because a covered edge is covered
     * whichever way the train came - and the ORDER is what this method exists for: the walk above
     * spends the train's length square by square, so a path handed back the wrong way round would
     * draw the far end of the segment and leave the square beside the train clear.
     *
     * The STEPS rather than the squares, because each one records which route of its square the edge
     * runs through - and that is what lets the diagram draw the road the train is on rather than the
     * whole tile (MT-309).
     *
     * @param from the square walked from
     * @param to the square walked to
     * @return the steps between, endpoints excluded, or null when they are not joined
     */
    private List<GraphReducer.TileStep> pathBetween(TileKey from, TileKey to)
    {
        if (from == null || to == null || reducer == null) return null;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            boolean sameWay = from.equals(edge.getStart()) && to.equals(edge.getEnd());
            boolean otherWay = to.equals(edge.getStart()) && from.equals(edge.getEnd());

            if (!sameWay && !otherWay) continue;

            List<GraphReducer.TileStep> steps = new ArrayList<>();

            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() == null) continue;

                // The squares at either end are where trains STAND, not track lying under one.
                if (step.getTile().equals(from) || step.getTile().equals(to)) continue;

                steps.add(step);
            }

            if (otherWay) java.util.Collections.reverse(steps);

            return steps;
        }

        return null;
    }

    /**
     * Puts a sensor square a train is lying on into the covered set, with the road of it the train is on
     * (OB-277).
     *
     * The square goes in even when the road cannot be named - a portal hop has no side on the grid - for the
     * reason `walkBackFrom` gives for a step with no route: the square is still spoken for, and the tile then
     * simply draws no line rather than a line along a rail it cannot name.
     *
     * @param out the covered set, added to
     * @param sensor the Point's square
     * @param towards the neighbouring Point's square the train's body runs on towards
     */
    private void markTheSensor(Map<TileKey, Set<RouteId>> out, TileKey sensor, TileKey towards)
    {
        Set<RouteId> roads = out.get(sensor);

        if (roads == null)
        {
            roads = new LinkedHashSet<>();

            out.put(sensor, roads);
        }

        RouteId road = roadOfTheSensor(sensor, towards);

        if (road != null) roads.add(road);
    }

    /**
     * Which road of a sensor square runs towards a neighbouring Point, or null when that cannot be said.
     *
     * **Asked of the same edge `pathBetween` walks**: the first reduced edge joining the two squares, either way
     * round, in the reducer's own order - so the line through the sensor and the line along the track beside it
     * describe one road rather than two answers that could part.  The side is where that edge leaves or reaches
     * the sensor, and the road is the one of the square's routes that uses that side.  A double curve's two arcs
     * share no side, so the answer is one road even there - which is the case MT-309 was about.
     *
     * @param sensor the Point's square
     * @param towards the neighbouring Point's square
     * @return the road, or null
     */
    private RouteId roadOfTheSensor(TileKey sensor, TileKey towards)
    {
        if (sensor == null || towards == null || reducer == null || graph == null) return null;

        Side side = null;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            if (sensor.equals(edge.getStart()) && towards.equals(edge.getEnd()))
            {
                side = edge.getExitSide();

                break;
            }

            if (towards.equals(edge.getStart()) && sensor.equals(edge.getEnd()))
            {
                side = edge.getEntrySide();

                break;
            }
        }

        if (side == null) return null;

        for (Map.Entry<RouteId, Route> road : graph.getRoutes(sensor).entrySet())
        {
            if (road.getValue().getA() == side || road.getValue().getB() == side) return road.getKey();
        }

        return null;
    }

    /**
     * Whether a train standing anywhere on this square could move at all (SPEC-B5).
     *
     * **The question is about the SQUARE, and the guard that asked it looked at one copy.**  A square
     * is several Points; `getNeighbors` on whichever one came to hand answers about that copy only, so
     * a platform with a dead copy and a live one was refused or accepted depending on which the menu
     * happened to be holding.  `LayoutRightclickAutonomyMenu.placeableCopies` says the same thing about
     * itself in a comment, and this is the second place that had to learn it.
     *
     * @param running the layout
     * @param tile the square
     * @return true when at least one copy of it has somewhere to go
     */
    public boolean canDepartFrom(org.traincontrol.automation.Layout running, TileKey tile)
    {
        if (running == null || tile == null) return false;

        for (String name : facingsFor(tile).keySet())
        {
            org.traincontrol.automation.Point point = running.getPoint(name);

            if (point == null) continue;

            java.util.List<org.traincontrol.automation.Edge> out = running.getNeighbors(point);

            if (out != null && !out.isEmpty()) return true;
        }

        return false;
    }

    /**
     * Which way a train ends up facing if it DRIVES from where it is to where it was dropped.
     *
     * Adam, 2026-09-06: **"calculating the simple bfs path from the current station to the paste
     * target using the current direction.  paste with the direction where the train ends up at the
     * destination.  if no path pick randomly from the allowed departure destinations."**
     *
     * **This is a better rule than anything the previous five attempts reached for, and the reason is
     * worth writing down.**  They all tried to derive the answer from the two squares - carry the
     * heading, keep the compass side, take the landing copy - and a railway is not a grid: which way
     * a train ends up facing after moving between two platforms is decided by the TRACK between them,
     * and by which way it was pointing when it set off.  A train that leaves southbound and comes back
     * round a loop is northbound at a station one square away.  No amount of reasoning about the two
     * endpoints recovers that; driving it does.
     *
     * The graph already encodes the direction, because facing is one-way edges here: the copy a train
     * stands on IS its heading, and an edge leads only where a train pointing that way could go.  So a
     * breadth-first walk from the copy it is on arrives at a specific COPY of the target, and that
     * copy's side is the answer.  Shortest path, because a paste is "put it there", not "take it on a
     * tour" - and the shortest is the one whose direction the operator is imagining.
     *
     * **When there is no path.**  The two squares may genuinely be unconnected in the direction the
     * train faces - it would have to reverse somewhere first, and this is a placement, not a journey.
     * The heading it already has is kept where the landing can hold it, and otherwise the first copy
     * a train could DEPART from is taken.  A copy with no way out is never chosen, because standing a
     * train somewhere it cannot move from is the "nothing moves" fault the placement menu already
     * refuses.
     *
     * That arm used to pick at RANDOM among the departable copies, on Adam's earlier answer that
     * nothing in the situation determines one so a legal one is the honest choice.  It is superseded
     * by his ruling of 2026-09-12 - *"as long as the direction isnt flipped (which it was before)"* -
     * because something does determine one: the way the train is already pointing.
     *
     * **Neither arm may answer twice differently.**  Both used to.  The walk raced
     * `Layout.getNeighbors`' deliberate shuffle and this arm rolled a die, so putting the same train
     * on the same square twice gave two railways: measured on the frozen snapshot, 2-8-4 3505 SP put
     * down on BottomMainB faced east 21 times out of 40 and west the other 19.
     * `core.testAPasteDoesNotTurnTheTrainRound` is that measurement, pinned.
     *
     * @param running the layout to walk, which is the only thing that knows the track
     * @param locomotive the train being placed
     * @param target the square it is being placed on
     * @return the side to record, or null when the target has no named copies at all
     */
    public Side facingByPath(org.traincontrol.automation.Layout running, String locomotive,
        TileKey target)
    {
        if (target == null) return null;

        Map<String, Side> copies = facingsFor(target);

        if (copies.isEmpty()) return null;

        // ONE COPY, ONE ANSWER - and no walk needed to find it.  At a terminus this is the reversal
        // Adam asks for: the end of a line holds one heading and a train put there takes it.
        if (copies.size() == 1) return copies.values().iterator().next();

        Side arrived = walkTo(running, locomotive, target, copies);

        if (arrived != null) return arrived;

        // NO WAY THERE FACING THIS WAY.  A legal heading, chosen without pretending it was derived.
        java.util.List<Side> departable = new java.util.ArrayList<>();

        for (Map.Entry<String, Side> copy : copies.entrySet())
        {
            org.traincontrol.automation.Point point =
                running == null ? null : running.getPoint(copy.getKey());

            if (point == null) continue;

            java.util.List<org.traincontrol.automation.Edge> out = running.getNeighbors(point);

            if (out != null && !out.isEmpty()) departable.add(copy.getValue());
        }

        if (departable.isEmpty()) return copies.values().iterator().next();

        // THE HEADING IT ALREADY HAS, WHERE THE LANDING CAN HOLD IT (Adam, 2026-09-12).
        //
        // *"Option 1 is fine as long as the direction isnt flipped (which it was before)."*  Option 1
        // is `facingAfterAPaste`'s rule - the heading survives - and this arm was the one place a
        // paste could still flip a train for no reason: it answered
        // `departable.get(new Random().nextInt(...))`, so a train that could not be DRIVEN to its
        // landing was turned round on about half of all attempts.
        //
        // The earlier wording of that was Adam's own - pick at random from the copies a train could
        // depart from, "the honest one: nothing in the situation determines an answer" - and today's
        // ruling supersedes it, because something in the situation does determine one: the way the
        // train is already pointing.  Only when the landing cannot hold that is there nothing to go on.
        Side already = facingOf(locomotive, running);

        if (already != null && departable.contains(already)) return already;

        // And then the first legal one rather than a random legal one.  Two pastes of the same train
        // onto the same square have to give the same railway - Adam, 2026-09-07: "in all your
        // simulations, state should never drift" - and a die rolled here is drift with nothing to
        // blame it on.
        return departable.get(0);
    }

    /**
     * The breadth-first half: from wherever this train is standing, to the nearest copy of the target
     * that a train could be STANDING on.
     *
     * **This used to answer with the first copy the walk touched, and that was a coin toss** (Adam,
     * 2026-09-12: *"When 2-8-4 is pasted, it should always face east.  Does it?"* - measured, east 21
     * times out of 40 and west the other 19).  Two things were wrong with "first touched wins", and
     * they compound:
     *
     *   - `Layout.getNeighbors` SHUFFLES.  Its own comment says why - *"Randomize order to allow for
     *     variation in paths"* - which is right for autonomy picking a journey and wrong for a
     *     question about where one square lies relative to another.  Breadth-first still measures true
     *     distances through a shuffled expansion, so the walk below runs to the end and the DISTANCES
     *     decide; only ties are left for this method to break, and it breaks them by rule.
     *   - A square trains may turn round at is two Points per arrival side - the plain copy, where a
     *     train that drove in is standing, and the turning copy, where it is after deciding to turn.
     *     Both are reached by the same edge, so they are always equally far away, and they face
     *     OPPOSITE ways.  On BottomMainB those two sat nine edges off and the shuffle chose between
     *     them.
     *
     * So the tie is broken towards the copy a train would simply be standing on.  Putting a train down
     * is not a decision to reverse it, and the turning copy IS that decision - the facing menu is
     * where an operator makes it.
     *
     * **And a compulsory turn still turns.**  Adam, 2026-09-06: *"for terminuses, they must reverse on
     * paste"*.  Where turning is compulsory the builder emits no plain copy at all, so there is
     * nothing to prefer and the turning copy is the answer - which is that reversal, falling out of
     * the same rule rather than bolted beside it.
     *
     * A turning copy is told from a plain one by `isTerminus() || isReversing()`, which is what the
     * builder writes on it (`stops ? "terminus" : "reversing"`).  That predicate cannot tell a turning
     * copy from a real terminus on its own - the confusion behind OB-205's three claims and MT-368 -
     * and it does not have to here: at a real terminus every copy answers it, so the preference finds
     * nothing to prefer and the behaviour is the one a terminus wants anyway.
     *
     * @param running the layout
     * @param locomotive the train
     * @param target the square being walked to
     * @param copies the target's copies, by name, in the order the build made them - which is what
     *        makes the last tie-break below repeatable
     * @return the side of the nearest copy that can be driven to, or null when none can be
     */
    private Side walkTo(org.traincontrol.automation.Layout running, String locomotive,
        TileKey target, Map<String, Side> copies)
    {
        if (running == null || locomotive == null) return null;

        // WHERE THE TRAIN IS, NOT WHERE IT IS MERELY RESERVED (PRV-C8).
        //
        // A locked path puts the locomotive on EVERY point of its route, so during a run this scan
        // can stop at a junction the train has not reached - and the walk then starts from the wrong
        // square, which is a wrong answer rather than a missing one.
        //
        // Nothing in the model separates a reservation from a train, so the tie-break is the
        // railway's own feedback: an occupied square has something standing on it. Where none of the
        // matches is occupied - the ordinary case, a stopped railway with a placed train - the first
        // match stands, which is what this did before.
        //
        // The doors that reach here are gestures made with the railway stopped, so this is a
        // narrowing of an already narrow case; it is done because the alternative is a silent wrong
        // answer, and because `walkTo` is what decides which way a pasted train ends up facing.
        org.traincontrol.automation.Point from = null;

        for (org.traincontrol.automation.Point point : running.getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            if (!locomotive.equals(point.getCurrentLocomotive().getName())) continue;

            if (from == null) from = point;

            if (point.isOccupied())
            {
                from = point;

                break;
            }
        }

        if (from == null) return null;

        return walkFrom(running, from, copies);
    }

    /**
     * The walk itself, from a Point named by the caller (OB-270).
     *
     * Split from `walkTo` so that a train that is not on the railway - one Control+X has taken off - can be walked
     * from the square it was cut from.  `walkTo` finds the train and hands its square here; nothing else differs.
     *
     * @param running the layout
     * @param from where the walk starts
     * @param copies the target's copies, by name, in the order the build made them
     * @return the side of the nearest copy that can be driven to, or null when none can be
     */
    private Side walkFrom(org.traincontrol.automation.Layout running, org.traincontrol.automation.Point from,
        Map<String, Side> copies)
    {
        // THE WHOLE WALK, AND THEN THE CHOICE - not the first thing touched.  Breadth-first fills
        // these in as true shortest distances whatever order the shuffle hands the edges back in, so
        // everything decided below is decided by the railway.
        Map<String, Integer> away = new LinkedHashMap<>();
        java.util.Deque<org.traincontrol.automation.Point> queue = new java.util.ArrayDeque<>();

        away.put(from.getName(), 0);
        queue.add(from);

        while (!queue.isEmpty())
        {
            org.traincontrol.automation.Point here = queue.poll();

            java.util.List<org.traincontrol.automation.Edge> out = running.getNeighbors(here);

            if (out == null) continue;

            for (org.traincontrol.automation.Edge edge : out)
            {
                org.traincontrol.automation.Point next = edge.getEnd();

                if (next == null || away.containsKey(next.getName())) continue;

                away.put(next.getName(), away.get(here.getName()) + 1);

                // REACHED, BUT NOT DRIVEN THROUGH (PRV-B1).
                //
                // A turning copy is where a train is AFTER it has turned round, so a route that
                // passes through one contains a reversal - and this method answers "where would it
                // end up if it DROVE there".  The runtime refuses such a route mid-path for the same
                // reason (`reversesAlongTheWay`), so a distance measured through one is a distance to
                // somewhere the train cannot actually get, and it could win the nearest-copy contest
                // against a copy it really can reach.
                //
                // The square is still recorded above, because arriving there and stopping is a
                // perfectly good end to the journey; what it may not be is a corner to turn.
                //
                // The square the train sets off from is expanded normally: a train already standing
                // on a turning copy faces the way that copy faces and its outgoing edges are the ones
                // it can genuinely take.  Only copies REACHED by the walk are dead ends here.
                if (next.isTerminus() || next.isReversing()) continue;

                queue.add(next);
            }
        }

        // HOW FAR THE NEAREST COPY IS.  Nothing reachable means no path, which is the caller's other
        // arm rather than an answer of its own.
        int nearest = Integer.MAX_VALUE;

        for (String name : copies.keySet())
        {
            Integer at = away.get(name);

            if (at != null && at < nearest) nearest = at;
        }

        if (nearest == Integer.MAX_VALUE) return null;

        Side turning = null;

        for (Map.Entry<String, Side> copy : copies.entrySet())
        {
            Integer at = away.get(copy.getKey());

            if (at == null || at != nearest) continue;

            org.traincontrol.automation.Point point = running.getPoint(copy.getKey());

            // STANDING BEATS TURNED ROUND, at the same distance.
            if (point != null && !point.isTerminus() && !point.isReversing()) return copy.getValue();

            // And the first turning copy is remembered in case that is all there is, which is what a
            // compulsory turn looks like from here.  First in the BUILD's order, so two runs of the
            // same paste give the same railway.
            if (turning == null) turning = copy.getValue();
        }

        return turning;
    }

    /**
     * Which way a train cut from one square would face on another, if it DROVE there (Adam, 2026-09-23, OB-270).
     *
     * *"Trains should not inadvertently change direction when pasted, so a loc going west from bottomsecondary should
     * always face east when pasted on bottommaina"*, and, asked whether a cut train keeps the heading it was cut with
     * or takes the one it would have after driving there: *"it should be east.  no train should inadvertently change
     * direction when pasted."*  The heading a train is cut with is a compass heading; the route from BottomSecondary
     * to BottomMainA loops round, so a train setting off west arrives facing east.
     *
     * `facingByPath` walks from where the train is standing, and a cut train is standing nowhere.  This walks from
     * the square it was cut from instead, and answers only where the walk found a way: null means no path, and the
     * caller keeps the heading it was cut with (MT-368) rather than an answer that pretends to be derived.
     *
     * @param running the layout
     * @param fromPoint the Point the train was standing on when it was cut
     * @param target the square it is being pasted onto
     * @return the heading it would arrive with, or null when no route reaches the square
     */
    public Side facingByPathFrom(org.traincontrol.automation.Layout running, String fromPoint, TileKey target)
    {
        if (running == null || fromPoint == null || target == null) return null;

        Map<String, Side> copies = facingsFor(target);

        if (copies.isEmpty()) return null;

        org.traincontrol.automation.Point from = running.getPoint(fromPoint);

        if (from == null) return null;

        return walkFrom(running, from, copies);
    }

    /**
     * Which way a train put down on a square should end up pointing.
     *
     * Adam, 2026-09-06: **"pasted locomotives pasted on the succeeding/preceding station to a given
     * station on the same line face into the station and away from that station, respectively"**, and
     * **"for terminuses, they must reverse on paste"**.
     *
     * **Both halves of the first sentence are one rule: the heading survives.**  A train standing at
     * the station after a reference station and pointing back down the line faces INTO it; the same
     * train with the same heading one station earlier faces AWAY from it.  Nothing about the train
     * changed between those two descriptions - only which square it stands on - so putting it down
     * must not turn it.
     *
     * **And a terminus has one direction, so a train put down there takes it.**  That is not a special
     * case bolted on; it falls out of asking what the landing can hold.  The end of a line holds one
     * heading, so the answer is that heading whatever the train was doing before, which is the
     * reversal Adam asks for.
     *
     * **Four attempts got here.**  The first three each changed the moment the direction CHANGES; the
     * defect was the moment it stops being RECORDED.  The fourth recorded the landing copy's own side,
     * which is not the train's heading at all - `StationIndex.speakerAt` says that on an empty square
     * "any copy will do", so it was copy 0, chosen arbitrarily (SPEC-A1).  The heading has to be read
     * BEFORE the move, from where the train was standing, and handed in here.
     *
     * Separated from the door so that it can be run: the door needs a window, a Central Station and a
     * drag. `testAPastedTrainKeepsItsDirection` asserts the call site is a call site, so
     * `extracted-rule-moves-the-bug-to-the-call` does not get another turn.
     *
     * @param held every copy the landing square builds to, by name, with the side each one faces
     * @param keep the heading the train had before it was moved, or null when that is not known
     * @param landedOn the copy the running layout chose - accepted and deliberately NOT used, because
     *        `StationIndex.speakerAt` says that on an empty square "any copy will do", so it is copy 0
     *        and evidence of nothing.  Recording it was SPEC-A1.  Kept in the signature so that a
     *        caller reaching for it finds this sentence rather than the idea (CONF-C2)
     * @return the side to record, or null to record nothing
     */
    public static Side facingAfterAPaste(Map<String, Side> held, Side keep, String landedOn)
    {
        // Nothing is known about this square - no copies, so no direction to record.  A guessed
        // heading is worse than none: an absent facing at least makes the menu ask.
        if (held == null || held.isEmpty()) return null;

        // ONE COPY MEANS ONE ANSWER.  At a terminus that answer IS the reversal; everywhere else it
        // is the only heading a train can have on that square, so it is right for the same reason.
        if (held.size() == 1) return held.values().iterator().next();

        // THE HEADING SURVIVES where the landing can hold it, which is Adam's rule proper.
        if (keep != null && held.containsValue(keep)) return keep;

        // Several copies, and the heading did not survive the move.  Nothing here knows which the
        // operator meant, and the copy the layout picked was picked arbitrarily - so the value is
        // cleared rather than invented, and the facing menu asks instead of showing an answer nobody
        // gave.  Recording the arbitrary copy is what SPEC-A1 was about.
        return null;
    }

    /**
     * Which way the train of this name is currently recorded as pointing, wherever it is standing.
     *
     * Read BEFORE a move, because the move is what takes it off the square that knows.
     *
     * @param locomotive the train
     * @return its recorded heading, or null if it is not placed or has none
     */
    public Side facingOf(String locomotive)
    {
        return facingOf(locomotive, null);
    }

    /**
     * The same, asking the running layout first (CONF-B1).
     *
     * **The setup names the square a train SET OFF FROM until `captureFromLayout` writes the arrival
     * back.**  `flipFacing` was fixed for exactly this an hour before this method was written, four
     * hundred lines further up the same file, and this shipped with the defect anyway - which is
     * `fix-one-site-sweep-the-siblings` almost verbatim.
     *
     * It bites when a train is run and then placed by hand: the heading read "before the move" is the
     * heading it had at the platform it left, not the one it is standing on, and the paste then
     * preserves a direction from a different part of the railway.
     *
     * @param locomotive the train
     * @param running the layout, or null when there is none to ask
     * @return its heading, or null when nothing knows
     */
    public Side facingOf(String locomotive, org.traincontrol.automation.Layout running)
    {
        if (locomotive == null) return null;

        // WHERE IT IS, from the thing that knows.
        if (running != null && getStationIndex() != null)
        {
            for (org.traincontrol.automation.Point point : running.getPoints())
            {
                if (point.getCurrentLocomotive() == null) continue;

                if (!locomotive.equals(point.getCurrentLocomotive().getName())) continue;

                TileKey where = getStationIndex().squareOf(point.getName());

                if (where == null) continue;

                // THE COPY IT IS STANDING ON, not the square's stored facing (PRW-B3).
                //
                // This asked `getFacing(where)`, which is ONE value per square - and a square is up to
                // four Points facing two ways.  So the answer was the setup's last word about that
                // platform rather than about this train: after a run, that word is the previous
                // occupant's, because `captureFromLayout` writes it when the run ends and nothing does
                // between.  A train standing on `BottomMainB (eastbound)` was reported facing west
                // whenever the file happened to say west.
                //
                // Which copy a train stands on IS its direction here - the model has no train
                // direction of its own, and one-way edges are how facing is written down - so the copy
                // is the answer and the square's value is at best a summary of it.
                Side onThisCopy = getStationIndex().facingsAt(where).get(point.getName());

                if (onThisCopy != null) return onThisCopy;

                // A copy the index has no facing for - a square with no named copies, or one built
                // whole - falls back to what the setup says about the square, which is what this
                // method always did and is right when there is only one copy to be on.
                if (getFacing(where) != null) return getFacing(where);
            }
        }

        // And the setup, which is right whenever nothing has run since the last capture.
        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            if (locomotive.equals(placed.getValue())) return getFacing(placed.getKey());
        }

        return null;
    }

    /**
     * Where to find the running layout, when there is one.
     *
     * A supplier rather than a reference, because the layout is rebuilt - `parseAuto` replaces the
     * object - and a field captured once would go stale at the first rebuild and then quietly move
     * trains on a graph nobody is looking at.
     *
     * Null until the window sets it, which is right for every headless use: a session opened by a
     * test or a migration has no railway to move anything on.
     */
    private java.util.function.Supplier<org.traincontrol.automation.Layout> runningLayout;

    /**
     * Tells this session where the running layout is, so that setup changes can reach it.
     *
     * @param source how to fetch the current layout, or null when there is none
     */
    public void setRunningLayoutSource(
        java.util.function.Supplier<org.traincontrol.automation.Layout> source)
    {
        this.runningLayout = source;
    }

    /**
     * Records a facing AND stands the train on the copy that faces that way (SPEC-B4).
     *
     * **The setup and the railway are two places, and the facing menu only ever wrote one.**  Adam's
     * ruling of 2026-09-06 was that `flipFacing` should also update the running layout; this is the
     * same statement from the other end - the operator choosing a direction by hand - and it was the
     * one `setFacing` writer left telling nobody.  Until the next build the diagram showed the new
     * direction while the train stood on the copy facing the old one.
     *
     * **The train the railway has here, and only then the setup's** (TDY4-C5).  After a run the setup still names each
     * square's pre-run occupant (behaviour.md 6a), so this turned the setup's train - found on no copy of the square,
     * so nothing moved - while the train actually standing there stayed as it was.  The setup's record is its own
     * train's, so it is written only where the railway has that train, or none.
     *
     * Silently a plain `setFacing` when no layout is running, which is every headless use.
     *
     * @param tile the square
     * @param facing the side its front faces
     */
    public void setFacingAndMove(TileKey tile, Side facing)
    {
        org.traincontrol.automation.Layout running =
            runningLayout == null ? null : runningLayout.get();

        String inTheSetup = getLocomotiveNameAt(tile);
        String onTheRailway = trainOnTheRailway(tile, running);

        if (onTheRailway == null || onTheRailway.equals(inTheSetup)) setFacing(tile, facing);

        if (running == null || facing == null) return;

        String name = onTheRailway != null ? onTheRailway : inTheSetup;

        if (name != null) moveOntoFacingCopy(running, name, tile, facing);
    }

    /**
     * The train standing on any copy of a square on the RAILWAY, or null (TDY4-C5).
     *
     * `facingOnTheRailway`'s question about the train rather than its facing: after a run the setup's placement need not
     * be the railway's, and a door acting on the train that is there asks this.
     *
     * @param square the diagram square
     * @param running the running layout, or null
     * @return the locomotive's name, or null when no copy holds one
     */
    public String trainOnTheRailway(TileKey square, org.traincontrol.automation.Layout running)
    {
        if (square == null || running == null || getStationIndex() == null) return null;

        for (String name : getStationIndex().pointNamesAt(square))
        {
            org.traincontrol.automation.Point copy = running.getPoint(name);

            if (copy != null && copy.getCurrentLocomotive() != null) return copy.getCurrentLocomotive().getName();
        }

        return null;
    }

    /**
     * Records which side a train standing here came in by, so its tail can be found.
     *
     * Kept beside the placement and the facing, in the same point properties, so it travels with them
     * through a save and a load - the round trip that had no test at all until this week.
     *
     * @param tile the square
     * @param side the compass side, or null to forget
     */
    public void setArrivedFrom(TileKey tile, String side)
    {
        setPointProperty(tile, "arrivedFrom", side);
    }

    /**
     * Records the road the tail of the train standing here lies along, beside its arrival side (WK7-B1).
     *
     * Adam, 2026-09-14: *"select the farthest sensor the tail of the train recently crossed."*  The road from that
     * sensor to this square, as `Layout.namesOfRoad` writes it - the same form the running layout saves - so it
     * travels with the placement and the side through a save, a load and a capture.
     *
     * @param tile the square
     * @param road [start, end] point-name pairs as a JSON array string, or null to forget
     */
    public void setArrivedAlong(TileKey tile, String road)
    {
        setPointProperty(tile, "arrivedAlong", road == null ? null : new org.json.JSONArray(road));
    }

    /**
     * @param tile the square
     * @return the recorded road as a JSON array string, or null
     */
    public String getArrivedAlong(TileKey tile)
    {
        Object value = getPointProperty(tile, "arrivedAlong");

        return value == null ? null : value.toString();
    }

    /**
     * @param tile the square
     * @return the recorded arrival side, or null
     */
    public String getArrivedFrom(TileKey tile)
    {
        Object value = getPointProperty(tile, "arrivedFrom");

        return value == null ? null : value.toString();
    }

    /**
     * Which way the train standing on this square is actually facing, according to the RAILWAY.
     *
     * **A square that splits is several Points, and which one a train is on IS its direction.** So this
     * is not a lookup of something somebody typed; it is a reading of where the train is.
     *
     * Adam, OB-181: **"moving a train in the track diagram editor from tunnelleftpark to bottommaina
     * showed its direction as eastbound in its station label, but westbound in the right click menu."**
     * The two disagreed because they asked different sources - and that split was made on purpose,
     * which is why it drifted rather than being noticed.
     *
     * The station label used to read the stored facing and was moved OFF it, for a good reason recorded
     * at `TrainControlUI.facingArrowOf`: the stored value is written only when somebody places a train
     * BY HAND, so the arrow appeared for a train you had placed and vanished for one autonomy had
     * driven there. The facing MENU was left reading the stored value, so from that day the two agreed
     * only while a hand-placed train had not moved.
     *
     * Null when no copy of the square holds a train - an empty square has no train whose direction this
     * could be, and the caller falls back to what the setup records, which is all there is.
     *
     * @param square the diagram square
     * @param running the running layout, which is where a train's position lives
     * @return the facing of the copy holding the train, or null
     */
    public Side facingOnTheRailway(TileKey square,
        org.traincontrol.automation.Layout running)
    {
        if (square == null || running == null || getStationIndex() == null) return null;

        for (String name : getStationIndex().pointNamesAt(square))
        {
            org.traincontrol.automation.Point copy = running.getPoint(name);

            if (copy == null || copy.getCurrentLocomotive() == null) continue;

            return getStationIndex().facingsAt(square).get(name);
        }

        return null;
    }

    /**
     * Which way the locomotive on this square is pointing, as recorded.
     *
     * @param tile
     * @return the side its front faces, or null when nobody has said and nothing has run
     */    public Side getFacing(TileKey tile)
    {
        Object value = getPointProperty(tile, AutonomyBuilder.FACING);

        if (value == null) return null;

        for (Side side : Side.values())
        {
            if (side.name().equals(value.toString())) return side;
        }

        return null;
    }

    /**
     * Records which way the locomotive on this square is pointing.
     *
     * @param tile
     * @param facing the side its front faces, or null to forget
     */
    public void setFacing(TileKey tile, Side facing)
    {
        setPointProperty(tile, AutonomyBuilder.FACING, facing == null ? null : facing.name());
    }

    /**
     * One of a Point's operational properties, in the active configuration.
     *
     * Kept per configuration rather than beside the track, because these are what a configuration IS:
     * the same railway with different rules about where trains may stand and turn.  The keys are the
     * ones parseAuto reads, so nothing has to translate them on the way out.
     *
     * @param tile
     * @param key terminus, reversing, active, maxTrainLength, speedMultiplier, priority, home,
     *        excludedLocs
     * @param value the value, or null to remove the property entirely
     */
    /**
     * Gives a square a home locomotive, taking that locomotive's home away from anywhere else.
     *
     * ONE locomotive, ONE station. The running layout has enforced this since July - setHomeLocomotive
     * clears the same locomotive from every other Point as it assigns one - and the setup-side editor,
     * which arrived a month later, wrote the property and swept nothing. So two squares could be given
     * the same home from the menu, and the next load dropped one of them by iteration order, with a log
     * line as the only notice (TD-8).
     *
     * The sweep is here rather than in the menu because it is a rule about the SETUP, and a rule
     * enforced at one door of two is the shape this defect came from.
     *
     * @param tile the square to make home
     * @param locomotive the locomotive, or null to clear this square's home
     */
    public void setHome(TileKey tile, String locomotive)
    {
        writeHome(tile, locomotive);

        deriveStationIndex();
    }

    /**
     * The same, facing the way the operator said - for a locomotive not standing on the square (OB-282).
     *
     * Adam, 2026-09-23, on a home given to a train standing elsewhere: *"prompt the user for the direction"*.  A facing
     * no copy trains may arrive at holds is not saved (*"we shouldn't allow an impossible facing to be saved"*).
     *
     * @param tile the square to make home
     * @param locomotive the locomotive, or null to clear this square's home
     * @param facing the way it should face there, or null to take the facing of the train standing there, where a train
     *        may be brought home in it (`knownHomeFacing`)
     */
    public void setHome(TileKey tile, String locomotive, Side facing)
    {
        writeHome(tile, locomotive);

        if (locomotive != null && facing != null && homeFacingsFor(tile).contains(facing))
        {
            writePointProperty(tile, AutonomyBuilder.HOME_FACING, facing.name());
        }

        deriveStationIndex();
    }

    /**
     * The ways a locomotive may be homed facing on this square (OB-282) - see `AutonomyBuilder.homeFacingsAt`.
     *
     * @param tile the square
     * @return the facings, empty on a square that is not split
     */
    public java.util.Set<Side> homeFacingsFor(TileKey tile)
    {
        return reducer == null ? java.util.Collections.<Side>emptySet() : builder(null).homeFacingsAt(tile);
    }

    /**
     * The same rule, written without re-deriving, for a caller about to make several (SEV-C2).
     *
     * `setPointProperty` re-derives the station index on every call, which is a full builder
     * construction - so a bulk gesture built out of `setHome` cost one per square, and one more per
     * home it took away from somewhere else. That is the cost the bulk doors exist to avoid, and
     * `homeEveryPlacedTrain` was paying it while its own comment said otherwise.
     *
     * The RULE stays in one place, which is the point of the split: one locomotive, one station,
     * swept here rather than at each caller. Two doors writing `home` with only one of them sweeping
     * is precisely how TD-8 arrived, and a bulk door that carried its own copy of the sweep would be
     * that again.  `setHome` is this method plus the re-derive, and `homeEveryPlacedTrain` is this
     * method in a loop plus one re-derive at the end - so there is one writer and two schedules.
     *
     * @param tile the square to make home
     * @param locomotive the locomotive, or null to clear this square's home
     */
    private void writeHome(TileKey tile, String locomotive)
    {
        if (tile == null) return;

        if (locomotive != null)
        {
            for (TileKey other : homesElsewhere(tile, locomotive))
            {
                writePointProperty(other, "home", null);
                writePointProperty(other, AutonomyBuilder.HOME_FACING, null);
            }
        }

        writePointProperty(tile, "home", locomotive);

        // AND THE WAY IT IS FACING, where it is standing here (OB-282) - the facing Return Home brings it back in.  Only a
        // facing a train may be brought home in (GUI3-C2): a train can stand facing the way trains may not arrive - reversed
        // on the throttle there - and that facing is one no Return Home can reach, so it is not saved.  A home given to a
        // train standing elsewhere is the square, whichever copy.
        Side facing = knownHomeFacing(tile, locomotive);

        writePointProperty(tile, AutonomyBuilder.HOME_FACING, facing == null ? null : facing.name());
    }

    /**
     * The facing a home set here for this locomotive would be saved with, without asking anybody - or null when only the
     * operator can say.
     *
     * @param tile the square
     * @param locomotive the locomotive being homed there
     * @return the side, or null
     */
    public Side knownHomeFacing(TileKey tile, String locomotive)
    {
        String facing = homeFacingOf(tile, locomotive);

        if (facing == null) return null;

        for (Side each : Side.values())
        {
            // ONLY ONE A TRAIN MAY BE BROUGHT HOME IN (GUI3-C2) - see `writeHome`.
            if (each.name().equals(facing)) return homeFacingsFor(tile).contains(each) ? each : null;
        }

        return null;
    }

    /**
     * The way a locomotive is facing on a square, if it is standing there (OB-282).  `knownHomeFacing` keeps it where a
     * train may be brought home in it, and that is what its home there is set with.
     *
     * @param tile the square
     * @param locomotive the locomotive being homed there, or null
     * @return the side it faces, or null when it is not standing there or nothing records its facing
     */
    private String homeFacingOf(TileKey tile, String locomotive)
    {
        if (locomotive == null) return null;

        Object loc = getPointProperty(tile, "loc");

        String standing = loc instanceof org.json.JSONObject ? ((org.json.JSONObject) loc).optString("name", null) : null;

        // FROM THE RAILWAY FIRST, as `facingOf` reads it (TDY-C2, after CONF-B1).  The setup names the heading a train
        // set off with until a capture writes the arrival back, so after a run that turned the train at this square its
        // FACING is the way it no longer faces - and Adam's rule is *"Direction it is facing when home is set"*.  The copy
        // it stands on in the running layout IS its facing.
        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        boolean onTheRailway = false;

        if (running != null && getStationIndex() != null)
        {
            for (org.traincontrol.automation.Point point : running.getPoints())
            {
                if (point.getCurrentLocomotive() == null || !locomotive.equals(point.getCurrentLocomotive().getName()))
                {
                    continue;
                }

                onTheRailway = true;

                if (!tile.equals(getStationIndex().squareOf(point.getName()))) continue;

                Side onThisCopy = facingsFor(tile).get(point.getName());

                if (onThisCopy != null) return onThisCopy.name();
            }
        }

        // STANDING ELSEWHERE ON THE RAILWAY, whatever the setup still says (TDY2-C4).  The setup names this square until
        // a capture writes the move back, and its FACING is then the heading of a train that is no longer here - so
        // where the railway knows where the train is, the setup does not answer for it, and the editor asks.
        if (onTheRailway) return null;

        if (!locomotive.equals(standing)) return null;

        Object facing = getPointProperty(tile, AutonomyBuilder.FACING);

        return facing instanceof String ? (String) facing : null;
    }

    /**
     * Takes the home off every square that has one, re-deriving the station index once (DY3-C5).
     *
     * The single-square setter is right for one square and wrong for sixty-two: every call rebuilds the
     * station index, which is a full builder construction, on the event thread.  The bulk direction
     * setter has had this shape since it was written and says why - "forty tiles meaning forty full
     * rebuilds ... for the gesture that exists precisely because it is the common one".
     *
     * Here rather than in the editor for the reason the single setter's javadoc gives: a second way of
     * clearing a home is exactly how the two doors would come to disagree later.  Both doors write the
     * same property through the same method now, and only the re-derive differs.
     *
     * @return how many squares were cleared
     */
    public int clearEveryHome()
    {
        java.util.List<TileKey> homed = tilesWithAHome();

        for (TileKey tile : homed)
        {
            writePointProperty(tile, "home", null);
            writePointProperty(tile, AutonomyBuilder.HOME_FACING, null);
        }

        // ONCE, at the end.  Not skipped: the split names are computed from these properties, and a
        // cached set of them is out of date the moment one changes.
        deriveStationIndex();

        return homed.size();
    }

    /**
     * Every STATION the active configuration gives a maximum train length other than 0, and every square with a negative
     * one, on every page.
     *
     * Adam, 2026-09-17: *"Add a right click menu open to clear all max station train lengths (grouped with the other
     * clear options)"*.  Above 0 because 0 is "any length" and is what the station has after the clear: the setup
     * writes an explicit `maxTrainLength: 0` on every destination, and counting those would offer to clear stations
     * that have nothing to lose.  Every page, as Clear All Home Locomotives and Clear All Track Lengths beside it.
     *
     * **A NEGATIVE COUNTS, because it is the one the operator most needs to be able to clear** (SET-B1, review round
     * 2026-09-19).  `Layout.fromJSON` refuses a maximum below 0 and invalidates the whole configuration, so a railway
     * carrying one has no autonomy at all until it is taken off; a clear that counted only what is above 0 greyed
     * itself and said there was nothing to clear, on the very setting that was stopping the railway loading.  The
     * editor door no longer writes one (`AutonomyEditorPanel.whyNotThisNumber`); this is how one already written -
     * from an older build, or by hand - is removed.
     *
     * @return the squares, in NO PARTICULAR ORDER, for `tilesWithAHome`'s reason
     */
    public java.util.List<TileKey> tilesWithAMaxTrainLength()
    {
        return tilesWhere((key, point) ->
        {
            if (!(point.opt("maxTrainLength") instanceof Number)) return false;

            int maximum = ((Number) point.opt("maxTrainLength")).intValue();

            // STATIONS ONLY (SET-C2), since a square that is no longer one keeps its maximum and ignores it (OB-291) -
            // but a NEGATIVE ONE WHEREVER IT IS (SET-B1): `Layout.fromJSON` refuses it on any point, so a remembered one
            // still stops the railway loading, and this is the door that takes it off.
            TileKey tile = AutonomyCompanionStore.parseTileKey(key);

            return maximum < 0 || (maximum != 0 && tile != null && store.isStation(tile));
        });
    }

    /**
     * Takes the maximum train length off every square that has one, re-deriving the station index once.
     *
     * `clearEveryHome`'s shape and for its reason: one `setPointProperty` per station re-derives the index each time.
     *
     * @return how many squares were cleared
     */
    public int clearEveryMaxTrainLength()
    {
        java.util.List<TileKey> limited = tilesWithAMaxTrainLength();

        for (TileKey tile : limited)
        {
            writePointProperty(tile, "maxTrainLength", null);
        }

        deriveStationIndex();

        return limited.size();
    }

    /**
     * Every placement the bulk doors will act on: square against locomotive (SEV-C3).
     *
     * `tilesWithALocomotive()` reads the configuration's points directly and so answers for every
     * page; this is `placedLocomotives()`, which is what the bulk doors walk and which skips pages
     * excluded from autonomy. On a layout with a page left out the two give different numbers, and a
     * menu that COUNTS one while the action walks the other tells the operator a figure the gesture
     * will not deliver - `guard-and-affordance-same-question`, which is this project's most repeated
     * defect.
     *
     * A copy, because the caller is a menu and the map behind this is rebuilt from the configuration
     * on every call anyway.
     *
     * @return the placements, by square
     */
    public Map<TileKey, String> placementsAutonomyWillWrite()
    {
        return new LinkedHashMap<>(placedLocomotives());
    }

    /**
     * Makes every placed train's current square its home, re-deriving the station index once (FR-075).
     *
     * Adam: *"to bulk tools in the autonomy editor, add an option to mass mark current train locations
     * as their homes."*
     *
     * **The same shape as `clearEveryHome` beside it**, and for its reason: `setPointProperty`
     * rebuilds the station index on every call, which is a full builder construction on the event
     * thread - so doing this through `setHome` cost one rebuild per train and another per home taken
     * away from somewhere else, for the gesture that exists because doing it by hand is too many
     * right-clicks.  It writes through `writeHome`, which is that rule without the re-derive (SEV-C2,
     * where this comment was true of the intention and not of the code).
     *
     * **ONE LOCOMOTIVE, ONE STATION still holds**, which is why this cannot simply write the property.
     * A train being homed HERE has to lose its home anywhere else, and `writeHome` is where that rule
     * lives - the loop calls it per train and this method re-derives once at the end. Writing the
     * property directly would be a second door with the sweep missing, which is exactly how TD-8
     * arrived. (`setHome` is the same `writeHome` plus an immediate re-derive, for the single-square
     * doors; naming it here as the method the loop calls was left over from before the split, and was
     * SVX-C2.)
     *
     * **HOW FRESH THE PLACEMENTS ARE, asked and answered (SEV-C4).**  This writes from the SETUP's
     * placements - the picture the editor is showing - rather than from the running layout, and the
     * question was whether those can be stale by the time the gesture is used.
     *
     * Autonomy cannot be running: `TrainControlUI.whyAutonomyEditorCannotOpen` refuses to open this
     * editor while `isAutonomyBusy()`, and the autonomy menu, the viewer and the layout editor all
     * refuse the other way round with `autolayout.errorCannotEditWhileRunning`. So no run can have
     * moved a train since the capture that opened the editor.
     *
     * What CAN move one is a hand on the throttle, which no fence in this program covers. There the
     * setup and the railway disagree, and this deliberately follows the setup: the operator is looking
     * at the editor's picture, the gesture says *"mark current train locations as their homes"*, and
     * the locations it means are the ones on the screen. Writing a position they cannot see would be
     * the surprising half of the two.
     *
     * **A square already homed to a DIFFERENT train is overwritten**, because that is what the gesture
     * says: the trains are where the operator wants them, and this records that. The count returned is
     * the number of squares this assigned, so the caller can say what happened; squares whose home was
     * already this train are included in it, because "it is already right" and "I set it" are the same
     * outcome to the operator and telling them apart would need a second number nobody asked for.
     *
     * Here rather than in the editor for the reason the single setter gives: a second way of setting a
     * home is how two doors come to disagree.
     *
     * @return how many squares were given a home
     */
    public int homeEveryPlacedTrain()
    {
        int assigned = 0;

        // `placedLocomotives` rather than `tilesWithALocomotive`, because it gives the square AND the
        // train in one pass and is the map every other reader of "who is standing where" uses.
        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            if (placed.getKey() == null || placed.getValue() == null) continue;

            writeHome(placed.getKey(), placed.getValue());

            assigned++;
        }

        // ONCE, at the end - see `clearEveryHome`.  Not skipped: the split names are computed from
        // these properties, and a cached set of them is out of date the moment one changes.
        deriveStationIndex();

        return assigned;
    }

    /**
     * Where this locomotive is already at home, if it is somewhere other than the given square.
     *
     * For the menu, which warns before moving it rather than moving it silently - the same shape as the
     * other two warnings there: the operator is told what will happen and asked.
     *
     * @param tile the square being assigned, which is not itself an answer
     * @param locomotive the locomotive
     * @return the other square that calls this locomotive home, or null
     */
    public TileKey homeElsewhere(TileKey tile, String locomotive)
    {
        java.util.List<TileKey> found = homesElsewhere(tile, locomotive);

        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Every square other than this one whose home is that locomotive.
     *
     * A list rather than one answer, because a setup written before the rule existed - or edited by
     * hand - can hold several, and taking one away while leaving the rest would move the problem
     * rather than fix it.
     */
    private java.util.List<TileKey> homesElsewhere(TileKey tile, String locomotive)
    {
        if (locomotive == null) return new java.util.ArrayList<>();

        // The same walk, with this method's own two extra questions: not the square being asked
        // about, and homed to this locomotive rather than to any.
        return tilesWhere((key, point) ->
            (tile == null || !tile.toString().equals(key))
                && locomotive.equals(point.optString("home", null)));
    }

    /**
     * Every square the active configuration records a home locomotive on.
     *
     * For the one gesture that is about all of them at once (R28-C1).  Adam, 2026-09-02, on finding
     * the 2.8.1 menu item gone: **"that option should be added back in to the autonomy editor, with a
     * confirmation."**  Clearing them one square at a time is one right-click each, and on his graph
     * that is up to sixty-two.
     *
     * @return the squares, in NO PARTICULAR ORDER, empty when nothing is homed anywhere.  This said
     *         "in the configuration's own order" and a `JSONObject` does not keep one - it is a
     *         `HashMap`, so the order is stable for a given set of keys and unrelated to the order they
     *         were written (CD3-C4).  Harmless to today's callers, which count and clear; a caller that
     *         listed these squares to a person would want to sort them first
     */
    public java.util.List<TileKey> tilesWithAHome()
    {
        return tilesWhere((key, point) -> carriesAHome(point));
    }

    /**
     * Every square the active configuration records a locomotive standing on.
     *
     * The placement twin of `tilesWithAHome`, for the bulk action Adam asked to have back (MT-257).
     *
     * @return the squares, in NO PARTICULAR ORDER - the same walk over the same `JSONObject` its twin
     *         `tilesWithAHome` was corrected about (CD3-C4, REL-C5): a `HashMap`'s iteration is stable
     *         for a given set of keys and unrelated to the order they were written.  Empty when nothing
     *         is placed anywhere
     */
    public java.util.List<TileKey> tilesWithALocomotive()
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        String active = store.getActiveConfiguration();

        org.json.JSONObject configuration = active == null ? null : store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return out;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String key : points.keySet())
        {
            org.json.JSONObject point = points.optJSONObject(key);

            if (point == null) continue;

            org.json.JSONObject loc = point.optJSONObject("loc");

            if (loc == null || loc.optString("name", "").trim().isEmpty()) continue;

            TileKey tile = AutonomyCompanionStore.parseTileKey(key);

            if (tile != null) out.add(tile);
        }

        return out;
    }

    public void setPointProperty(TileKey tile, String key, Object value)
    {
        writePointProperty(tile, key, value);

        // The split names are computed from these properties - which squares turn trains round, and
        // which are berths - so a cached set of them is out of date the moment one changes.  It was
        // dropped only on a rebuild, and this method deliberately does not rebuild, so marking a square
        // while autonomy was running left the labels looking up Point names the running graph had never
        // heard of, and that station stopped filling in until the next load.
        deriveStationIndex();
    }

    /**
     * The write on its own, for a caller that is about to make several and will re-derive once (DY3-C5).
     *
     * Deriving the station index is a full builder construction, so doing it per square turned one
     * press of "clear every home" into sixty-two of them on the event thread - for the gesture that
     * exists precisely because sixty-two right-clicks is too many.  This is the same shape the bulk
     * direction setter already had, and its comment says the same thing.
     *
     * @param tile the square
     * @param key the property
     * @param value the value, or null to remove it
     */
    private void writePointProperty(TileKey tile, String key, Object value)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null) return;

        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        org.json.JSONObject points = configuration.getJSONObject("points");

        String id = tile.toString();

        if (!points.has(id)) points.put(id, new org.json.JSONObject());

        if (value == null) points.getJSONObject(id).remove(key);
        else points.getJSONObject(id).put(key, value);

        dirty = true;
    }

    /**
     * @param tile
     * @param key
     * @return the stored value, or null when this Point has no such property
     */
    public Object getPointProperty(TileKey tile, String key)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return null;

        org.json.JSONObject points = configuration.getJSONObject("points");

        String id = tile.toString();

        if (!points.has(id)) return null;

        org.json.JSONObject point = points.getJSONObject(id);

        return point.has(key) ? point.get(key) : null;
    }

    /**
     * Sets one direction across a whole run of track, from one square to another.
     *
     * The gesture the per-tile tools could not express.  A user does not think "close the westward
     * route on eleven tiles"; they think "trains only go this way along here", and then have to work
     * out which tiles that means and which way round each one's A and B happen to be.
     *
     * The route is found ignoring directions - the point is to change them - so an already one-way run
     * can be reversed by drawing it the other way.
     *
     * @param from the square trains may leave
     * @param to the square they may travel toward
     * @return how many tiles were changed, or -1 if there is no continuous track between the two
     */
    public int setOneWayRun(TileKey from, TileKey to)
    {
        List<TileKey> path = graph.findUndirectedPath(from, to);

        if (path == null) return -1;

        return applyOneWay(path);
    }

    /**
     * Sets one direction along a path that is already known, tile by tile.
     *
     * Separate from the two-argument form because a RUN knows its own tiles, and re-deriving them from
     * its two ends with a shortest-path search is wrong wherever two chains join the same pair: on a
     * passing loop or a double-track section, both runs have the same ends, so the search returned the
     * other chain and one-wayed track the user had not touched - while the run they clicked kept its
     * old direction and its cycle stuck.
     *
     * @param path the squares in order, boundaries included
     * @return how many tiles were changed
     */
    private int applyOneWay(List<TileKey> path)
    {
        int changed = 0;

        // Only the track BETWEEN the two ends is restricted.  The ends themselves are the squares the
        // user picked out; closing a route on them would also block traffic that never enters the run.
        for (int i = 1; i < path.size() - 1; i++)
        {
            TileKey tile = path.get(i);

            Side cameFrom = graph.sideTowardNeighbour(tile, path.get(i - 1));
            Side goingTo = graph.sideTowardNeighbour(tile, path.get(i + 1));

            if (cameFrom == null || goingTo == null) continue;

            for (Map.Entry<RouteId, Route> entry : graph.getRoutes(tile).entrySet())
            {
                Route route = entry.getValue();

                if (!route.touches(cameFrom) || !route.touches(goingTo)) continue;

                record(tile, entry.getKey(),
                    route.getA() == goingTo ? Direction.TOWARD_A : Direction.TOWARD_B);

                changed++;
            }
        }

        touched();

        return changed;
    }

    public void setTileLength(TileKey tile, int length)
    {
        store.setTileLength(tile, length);
        touched();
    }

    /**
     * Answers these squares 0 on purpose, replacing any length they had, and re-derives once (Adam, 2026-09-23).
     *
     * What Segment Length's 0 means since *"no, add a clear button"*: a 0 typed there is the same answer a 0 in Mass
     * Assign Lengths is (OB-274) - kept, not offered again, and read as unmeasured by every rule.  Clearing a length is
     * `setTileLength(tile, 0)`, which removes the answer as well.
     *
     * @param tiles the squares
     */
    public void answerTileLengthsZero(java.util.Collection<TileKey> tiles)
    {
        if (tiles == null || tiles.isEmpty()) return;

        for (TileKey tile : tiles)
        {
            store.setTileLength(tile, 0);
            store.answerTileLengthZero(tile);
        }

        touched();
    }

    /**
     * Writes several squares' lengths and re-derives once (AUS-C1).
     *
     * `touched()` is a full builder construction - a new graph, a new reduction, a new station index - and the
     * single setter calls it per square, which is right for one square and wrong for a run: Segment Length on a run
     * of four paid for four of them on the event thread, and then the running layout's rebuild on top.  The two
     * bulk doors beside this one already have this shape, and `clearEveryTileLength`'s javadoc says why.
     *
     * @param lengths what each square is to measure
     */
    public void setTileLengths(java.util.Map<TileKey, Integer> lengths)
    {
        if (lengths == null || lengths.isEmpty()) return;

        for (java.util.Map.Entry<TileKey, Integer> square : lengths.entrySet())
        {
            store.setTileLength(square.getKey(), square.getValue() == null ? 0 : square.getValue());
        }

        touched();
    }

    /**
     * Every square that has a length recorded (FR-069).
     *
     * @return the measured squares
     */
    public java.util.List<TileKey> tilesWithALength()
    {
        return store.tilesWithALength();
    }

    /**
     * Forgets every length on every page, and re-derives once (FR-069).
     *
     * **The session's bulk door, not one `setTileLength` per square**, for the reason
     * `clearEveryHome` gives: every one of those calls `touched()`, which rebuilds the reducer - a
     * full builder construction on the event thread - so a loop would pay that once per measured
     * square, for the gesture that exists precisely because doing it one at a time is too many
     * right-clicks.
     *
     * @return how many squares had a length
     */
    public int clearEveryTileLength()
    {
        int cleared = store.clearEveryTileLength();

        touched();

        return cleared;
    }

    public void setPageExcluded(String page, boolean excluded)
    {
        store.setPageExcluded(page, excluded);
        touched();
    }

    public void setLinkName(TileKey tile, String name)
    {
        store.setLinkName(tile, name);
        touched();
    }

    /**
     * Pairs two portals, and switches BOTH ends on (OB-031).
     *
     * A pairing is one fact about two squares, and being switched off is a fact about one - so a pair
     * whose far end was disabled read as connected on the diagram and refused every train, with
     * nothing on the near end to say why. Adam: "when an active link from one page pairs with a link
     * on another, both must be marked as active. right now, the target doesn't have to be."
     *
     * Enabling rather than refusing the pairing. Somebody pairing two links is saying they are joined;
     * a disabled far end is a setting made earlier about a square that was not joined to anything, and
     * the newer statement is the one they mean.
     *
     * @param a one end
     * @param b the other
     */
    public void pairPortals(TileKey a, TileKey b)
    {
        store.pairPortals(a, b);

        store.setPortalDisabled(a, false);
        store.setPortalDisabled(b, false);

        touched();
    }

    public void unpairPortal(TileKey tile)
    {
        store.unpairPortal(tile);
        touched();
    }

    /**
     * One arrow per run of track between two sensors, on a square in the middle of it.
     *
     * Marking only what is RESTRICTED leaves a layout almost bare, which is right for spotting
     * decisions and wrong for the first question anybody asks: does this sensor reach that one, and
     * which way round.  This puts a single arrow on each derived connection - enough to read the flow
     * of the whole railway at a glance, without an arrow on every square.
     *
     * A pair of runs that face each other collapses into one double-headed arrow, because two arrows
     * on the same piece of track pointing opposite ways is how bidirectional track already looks.
     *
     * @return the square to mark, and what to draw there
     */
    public Map<TileKey, TileAnnotation.Mark> flowMarks()
    {
        Map<TileKey, TileAnnotation.Mark> out = new LinkedHashMap<>();

        if (reducer == null || graph == null) return out;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            List<GraphReducer.TileStep> path = edge.getPath();

            if (path.isEmpty()) continue;

            // the middle of the run, so the arrow is not crowded against either sensor
            int at = path.size() / 2;

            TileKey tile = path.get(at).getTile();

            // where a train standing here is heading next
            TileKey next = at + 1 < path.size() ? path.get(at + 1).getTile() : edge.getEnd();
            TileKey previous = at > 0 ? path.get(at - 1).getTile() : edge.getStart();

            Side toward = graph.sideTowardNeighbour(tile, next);
            Side from = graph.sideTowardNeighbour(tile, previous);

            if (toward == null || from == null) continue;

            Route route = graph.getRoutes(tile).get(path.get(at).getRouteId());

            if (route == null || !route.touches(toward) || !route.touches(from)) continue;

            Direction direction = route.getA() == toward ? Direction.TOWARD_A : Direction.TOWARD_B;

            TileAnnotation.Mark existing = out.get(tile);

            // the opposing run over the same track: one arrow with two heads, not two arrows
            out.put(tile, existing != null && existing.getDirection() != direction
                ? new TileAnnotation.Mark(route.getA(), route.getB(), Direction.BOTH)
                : new TileAnnotation.Mark(route.getA(), route.getB(), direction));
        }

        return out;
    }

    /**
     * One run of plain track: the tiles between two points, in order, and the points at either end.
     */
    public static class Run
    {
        private final TileKey start;
        private final TileKey end;
        private final List<TileKey> tiles;

        Run(TileKey start, TileKey end, List<TileKey> tiles)
        {
            this.start = start;
            this.end = end;
            this.tiles = tiles;
        }

        public TileKey getStart()
        {
            return start;
        }

        public TileKey getEnd()
        {
            return end;
        }

        /**
         * @return the tiles between the two points, in order from start to end
         */
        public List<TileKey> getTiles()
        {
            return Collections.unmodifiableList(tiles);
        }

        /**
         * The tile that speaks for this run - the first of them, as the author asked.
         */
        public TileKey getLeader()
        {
            return tiles.isEmpty() ? null : tiles.get(0);
        }
    }

    /**
     * Every run of plain track, keyed by the tile that speaks for it.
     *
     * A run of straight track has one direction, not eleven: setting it tile by tile is busywork, and
     * a run that disagrees with itself is a silent trap - the arrows look set and no train can pass.
     * So one tile in each run is the one to set, and the rest follow it.
     *
     * Computed from the DIAGRAM ALONE - tile types and which sides face which - and never from the
     * reduction.  That is the whole point: edges come and go as directions are set, so a run derived
     * from them would regroup itself every time somebody closed a route, and the greying would move
     * around under the user.  What is grey is a property of the track, not of the settings on it.
     *
     * A run tile is a piece of plain track: exactly one route through it, not a sensor, and not
     * something autonomy ignores.  Anything else - a switch, a crossing, a sensor - ends the run,
     * because each of those is a decision in its own right.
     *
     * @return leader tile to the run it leads
     */
    public Map<TileKey, Run> runs()
    {
        Map<TileKey, Run> out = new LinkedHashMap<>();

        if (graph == null) return out;

        Set<TileKey> seen = new LinkedHashSet<>();

        for (TileKey tile : graph.getTiles().keySet())
        {
            if (seen.contains(tile) || !isRunTile(tile)) continue;

            Route route = firstRoute(tile);

            if (route == null) continue;

            java.util.LinkedList<TileKey> chain = new java.util.LinkedList<>();
            chain.add(tile);
            seen.add(tile);

            // walk out of both ends until the plain track stops
            TileKey endA = walk(chain, tile, route.getA(), seen, false);
            TileKey endB = walk(chain, tile, route.getB(), seen, true);

            out.put(chain.getFirst(), new Run(endA, endB, new ArrayList<>(chain)));
        }

        return out;
    }

    /**
     * Whether a square is a piece of plain track that can belong to a run.
     */
    private boolean isRunTile(TileKey tile)
    {
        LayoutDiagramComponent component = graph.getTiles().get(tile);

        if (component == null || component.isFeedback()) return false;

        if (TilePorts.isDisqualified(component.getType())
            || TilePorts.isTransparent(component.getType())) return false;

        if (graph.getRoutes(tile).size() != 1) return false;

        // A stub - an END, a tunnel mouth, a link - has one route whose two sides are the same, so
        // walking "through" it comes straight back out the way it went in.  That made the walk report a
        // tile INSIDE the run as its boundary, leaving the tail of the run unset while the rest went
        // one-way: a run silently disagreeing with itself, which is the trap runs exist to prevent.
        Route route = firstRoute(tile);

        return route != null && route.getA() != route.getB();
    }

    /**
     * Extends a chain out of one side of a tile for as long as the track stays plain.
     *
     * @param chain collected so far
     * @param from where to start
     * @param side which way to go
     * @param seen tiles already claimed by a run
     * @param append true to add to the end of the chain, false to add to the front
     * @return the square the run stops at - a switch, a sensor, or null at the end of the track
     */
    private TileKey walk(java.util.LinkedList<TileKey> chain, TileKey from, Side side,
        Set<TileKey> seen, boolean append)
    {
        TileKey here = from;
        Side out = side;

        // bounded because a loop of plain track has no end to reach
        for (int guard = 0; guard < 1000; guard++)
        {
            Landing landing = graph.landing(here, out);

            if (landing == null) return null;

            TileKey next = landing.getTile();

            if (!isRunTile(next) || seen.contains(next)) return next;

            Route route = firstRoute(next);

            if (route == null) return next;

            if (append) chain.addLast(next); else chain.addFirst(next);

            seen.add(next);

            here = next;
            out = route.other(landing.getEntrySide());

            if (out == null) return null;
        }

        return null;
    }

    /**
     * @return every tile of every run, mapped to the tile that speaks for it
     */
    public Map<TileKey, TileKey> runLeaders()
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Run> entry : runs().entrySet())
        {
            for (TileKey tile : entry.getValue().getTiles())
            {
                out.put(tile, entry.getKey());
            }
        }

        return out;
    }

    /**
     * Sets a direction on every tile of the run a leader speaks for, in the sense the leader means.
     *
     * @param leader the tile the user set
     * @param routeId which of its routes
     * @param direction what they chose
     * @return how many tiles were changed, so a caller does not announce a change that did not happen
     */
    public int setRunDirection(TileKey leader, RouteId routeId, Direction direction)
    {
        Run run = runs().get(leader);

        // Not part of a run at all - a lone tile, or a point.  Set it and nothing else.
        if (run == null)
        {
            setDirection(leader, routeId, direction);
            return 1;
        }

        // Both ways and closed mean the same on every tile, so they go on directly.
        if (direction == Direction.BOTH || direction == Direction.NONE)
        {
            setDirection(new LinkedHashSet<>(run.getTiles()), direction);
            return run.getTiles().size();
        }

        Route route = graph.getRoutes(leader).get(routeId);

        if (route == null) return 0;

        Side toward = direction == Direction.TOWARD_A ? route.getA() : route.getB();

        Landing landing = graph.landing(leader, toward);

        // Which way along the run the user pointed.  The run's OWN tiles are walked, not a path
        // re-derived from its two ends - see applyOneWay.  The boundaries are included so the first and
        // last tiles of the run get a direction like the rest; a null boundary (track that simply stops)
        // is left out rather than making the whole thing impossible.
        List<TileKey> path = new ArrayList<>();

        if (run.getStart() != null) path.add(run.getStart());

        path.addAll(run.getTiles());

        if (run.getEnd() != null) path.add(run.getEnd());

        boolean towardEnd = landing == null
            || !run.getTiles().isEmpty() && landing.getTile().equals(nextAfter(run, leader));

        if (!towardEnd) Collections.reverse(path);

        return applyOneWay(path);
    }

    /**
     * The tile after this one along a run, or the run's far boundary when it is the last.
     */
    private TileKey nextAfter(Run run, TileKey tile)
    {
        int at = run.getTiles().indexOf(tile);

        if (at < 0) return run.getEnd();

        return at + 1 < run.getTiles().size() ? run.getTiles().get(at + 1) : run.getEnd();
    }

    /**
     * Which way each route across a square runs, as marks to draw (FR-037).
     *
     * Lifted out of the autonomy editor so that the editor and the ordinary track diagram cannot come
     * to different conclusions about which way a piece of track runs. They draw the same arrows for
     * different reasons - one is being edited, the other watched - and two spellings of "which way does
     * this go" would eventually disagree about a railway that has only one answer.
     *
     * WHAT DID NOT COME WITH IT, deliberately: every reason the editor has for staying quiet. A square
     * it is ignoring, the greying while a signal is being picked, a tile that merely follows its run,
     * and the flow-mark fallback that gives a bare layout one arrow per run. Those are about the
     * editor's own gestures and about a diagram being configured; on the running diagram they would
     * either say nothing or say something false.
     *
     * The hardware correction stays, because it is a fact about the railway rather than about either
     * window: a route the blades restrict is one-way whichever direction was authored, and a drawing
     * that showed the authored answer would be showing something a train cannot do.
     *
     * @param tile the square
     * @return one mark per route, never null, empty when the square has no routes or no graph
     */
    public List<TileAnnotation.Mark> directionMarks(TileKey tile)
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        if (graph == null || tile == null) return marks;

        for (Map.Entry<TileGraph.RouteId, TilePorts.Route> entry : getRoutes(tile).entrySet())
        {
            TilePorts.Route route = entry.getValue();

            TileGraph.Direction direction = graph.getDirection(tile, entry.getKey());

            // A route the hardware restricts is one-way whatever the user chose: the graph leaves the
            // authored direction BOTH there (see defaultDirection), but a train still cannot pass
            // against the blades, and the drawing has to say what a train can actually do.
            //
            // **AND A SHUT ROUTE DRAWS NOTHING (VD18-B1).**  This overrode the authored answer
            // whenever it was not `NONE`, and on a permanent turnout `TOWARD_A` - toward the fork - is
            // a closure rather than a direction: it permits only an entry the blades refuse.  So a
            // road the operator had shut was drawn with a green arrow showing trains running along
            // it, which is the one thing the arrows exist to say and it was saying it backwards.
            if (!TileGraph.isPassable(direction, route))
            {
                direction = TileGraph.Direction.NONE;
            }
            else if (route.getDirectedToward() != null)
            {
                direction = route.getDirectedToward() == route.getA()
                    ? TileGraph.Direction.TOWARD_A : TileGraph.Direction.TOWARD_B;
            }

            marks.add(new TileAnnotation.Mark(route.getA(), route.getB(), direction));
        }

        return marks;
    }

    /**
     * What the setup says about one square, for drawing on the ordinary track diagram.
     *
     * Also draws the one-way restriction arrows (FR-037), on tiles that are Points and on plain track
     * alike - reversed from how this method first shipped. The first attempt gated the arrows on the
     * same Point test used for the badge below, on the reasoning that direction is a Point question;
     * it is not. A restriction is a fact about TRACK - straights, curves, the squares either side of a
     * switch - and almost none of that carries a sensor, so gating on Point made the option appear to
     * do nothing at all (see the inline comment a few lines down). `directionMarks` is asked before
     * the Point test for exactly that reason, and `diagramShowsRestrictionArrows()` now defaults to
     * true, so this is the ordinary case rather than an opt-in corner.
     *
     * @param tile
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile)
    {
        if (graph == null || reducer == null) return null;

        // A link switched off is greyed here as well as in the editor.
        //
        // It was greyed only while editing, and a link is the one square whose being switched off is
        // invisible from the track alone: the tile art is the same either way, no arrow is drawn on it
        // to go missing, and the route it used to offer simply stops existing.  So a reader of the
        // running diagram had nothing to look at that said why trains never take that door.
        //
        // Before the Point test, because a link may or may not carry a sensor and the answer must not
        // depend on which.
        if (isDisabledLink(tile))
        {
            return new TileAnnotation(new ArrayList<TileAnnotation.Mark>(), -1, false, null, true);
        }

        // The one-way arrows, when the operator has asked for them (FR-037).
        //
        // BEFORE the Point test, which is where this went wrong first time: that test is about whether
        // a square deserves a BADGE, and a restriction is not a fact about sensors. It is a fact about
        // TRACK - straights, curves, the squares either side of a switch - and almost none of those
        // carry a sensor, so the option appeared to do nothing at all.
        boolean arrows = org.traincontrol.gui.TrainControlUI.diagramShowsRestrictionArrows();

        List<TileAnnotation.Mark> marks = arrows ? directionMarks(tile)
            : new ArrayList<TileAnnotation.Mark>();

        // Track that is not a Point, but is restricted.
        //
        // It still returns null when there is nothing to say, so a diagram with the option off - or a
        // railway with no restrictions on it - is exactly as bare as it was.
        if (!reducer.getPoints().containsKey(tile))
        {
            return marks.isEmpty() ? null
                : new TileAnnotation(marks, -1, false, null, false, false, false, null, arrows, null);
        }

        String name = store.getPointName(tile);

        // A plain point draws no badge on the running diagram.
        //
        // The tile is a feedback tile and looks like one, so the badge only repeated what the art
        // already said - on every sensor of the layout, which is most of them.  What a badge is FOR is
        // the things the art cannot say: this is a station, trains turn round here, autonomy is not
        // using it.  Those still draw.
        //
        // A non-station where trains turn round keeps its mark, because "trains reverse here" is a fact
        // about behaviour that nothing on the tile shows.
        //
        // AND THE THIRD ITEM ON THAT LIST HAD NO TERM IN THIS EXPRESSION (SVN-B6, D24-C9).  A square
        // switched off that is neither a station nor a turn-around drew nothing at all here, while the
        // editor - which badges every Point in the graph - drew its cross.  So the one drawing that
        // says "nothing may pass here" appeared while setting the railway up and vanished while
        // running it, which is the half the operator is looking at when a train does not arrive.
        boolean shut = Boolean.FALSE.equals(getPointProperty(tile, "active"));

        boolean worthABadge = store.isStation(tile) || isTurnAround(tile) || shut;

        // The marks worked out above, alongside whatever badge this Point earns.
        //
        // Handed over WHOLE, with `blockedOnly` below saying which of them to draw. The annotation is
        // what knows how to filter a mark down to the restricted ones, and it already does that for
        // the editor's own restrictions view - deciding it a second time here is how the two drawings
        // would come to disagree about what counts as a restriction.
        //
        // Nothing here asks whether autonomy is running. That is settled before this is ever called:
        // showStaticAutonomyLayer clears the whole layer and returns when autonomy is not showing,
        // when there is no session or graph, and when no configuration is loaded. An arrow is a
        // statement about a setup, and with no setup there is nothing to state.
        return new TileAnnotation(marks, -1, false,
            !worthABadge ? null : new TileAnnotation.Badge(
                store.isStation(tile),
                store.isStation(tile) && isTurnAround(tile),
                !store.isStation(tile) && isTurnAround(tile),
                shut || !isAutoDestination(tile),
                name != null && !name.trim().isEmpty(),
                firstRoute(tile) == null ? null : firstRoute(tile).getA(),
                firstRoute(tile) == null ? null : firstRoute(tile).getB(),
                isTurnAround(tile) && !isMustTurnAround(tile),
                shut),
            false, false, false, null,
            // RESTRICTED ONLY, which is the whole of what was asked for: "show RESTRICTION arrows".
            // A run that is open both ways is the majority of any layout and is also the default, so
            // drawing those here would put an arrow on nearly every square - which is the clutter that
            // kept directions out of this diagram to begin with.
            arrows,
            // The station ingress arrows follow the SAME switch (Adam, 2026-08-28).
            //
            // A chevron saying which way into a platform is shut is the same kind of statement as a
            // one-way arrow: both say where a train may not go, both come from this setup, and a
            // switch that turned off one and not the other would be a setting that half works.
            //
            // THE EDITOR IS NOT TOUCHED BY THIS. Its arrivals are governed by its own four-way
            // control, which has a mode whose whole point is showing every side of every station so
            // the setting can be read - that mode is why `arrivalMarks` takes an `always` flag at all.
            // The editor passes its own answer and never sees this preference.
            //
            // `false` still means what it meant: only where something is actually restricted. A
            // station that takes trains from anywhere has nothing to say, and saying it on every
            // platform would be the clutter this mark exists to avoid.
            arrows ? arrivalMarks(tile, false)
                : new ArrayList<TileAnnotation.Arrival>());
    }

    /**
     * The stations whose every way in has been barred.
     *
     * Worked out here because it needs both halves - what is barred, and how many ways in the square
     * actually has - and the split is this class's business.  A station with no arrival sides at all
     * is not shut: it is a square that never splits, which the restriction cannot touch.
     *
     * @return station square to true, only for the ones that are shut
     */
    public Map<TileKey, Boolean> shutStations()
    {
        Map<TileKey, Boolean> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Set<Side>> entry : barredArrivals().entrySet())
        {
            if (!store.isStation(entry.getKey())) continue;

            List<Side> ways = arrivalSides(entry.getKey());

            if (ways.isEmpty()) continue;

            boolean open = false;

            for (Side side : ways)
            {
                if (!entry.getValue().contains(side)) open = true;
            }

            if (!open) out.put(entry.getKey(), Boolean.TRUE);
        }

        return out;
    }

    /**
     * The arrival marks for a square, or none where there is nothing to say.
     *
     * Only stations have them: arriving somewhere is stopping there, and a square trains merely pass
     * over is not somewhere anything arrives.
     *
     * @param tile
     * @param always true to mark every arrival side of every station - which is the editor's own view
     *        of this setting - and false to mark only the stations that actually restrict something,
     *        which is what the running diagram shows.  Left on everywhere, a two-ended station would
     *        carry two marks saying "yes, trains may arrive", on every platform of the layout, to say
     *        nothing at all.
     * @return the marks, in the order the build emits the copies
     */
    public List<TileAnnotation.Arrival> arrivalMarks(TileKey tile, boolean always)
    {
        List<TileAnnotation.Arrival> out = new ArrayList<>();

        if (tile == null || !store.isStation(tile)) return out;

        Set<Side> barred = getBarredArrivals(tile);

        if (!always && barred.isEmpty()) return out;

        for (Side side : arrivalSides(tile))
        {
            out.add(new TileAnnotation.Arrival(side, !barred.contains(side)));
        }

        return out;
    }

    /**
     * Whether this square is a link that autonomy has been told to leave alone.
     *
     * Both halves have to hold: the store remembers a switched-off link by its square, and a square
     * that has since stopped being a link - the tile replaced, the page redrawn - is an ordinary piece
     * of track that would otherwise be greyed out by a setting nobody can see or clear.
     *
     * @param tile
     * @return true when the square is a link and switched off
     */
    public boolean isDisabledLink(TileKey tile)
    {
        if (graph == null || tile == null) return false;

        LayoutDiagramComponent component = graph.getTiles().get(tile);

        return component != null && TilePorts.hasPortal(component.getType())
            && store.isPortalDisabled(tile);
    }

    /**
     * The tile's first route, which is where its badge is drawn.
     */
    private Route firstRoute(TileKey tile)
    {
        Map<RouteId, Route> routes = graph == null ? null : graph.getRoutes(tile);

        return routes == null || routes.isEmpty() ? null : routes.values().iterator().next();
    }

    /**
     * Every route of a tile, so the panel can offer a switch's branches individually.
     * @param tile
     * @return
     */
    public Map<RouteId, Route> getRoutes(TileKey tile)
    {
        return graph == null ? new LinkedHashMap<RouteId, Route>() : graph.getRoutes(tile);
    }

    private void touched()
    {
        dirty = true;
        rebuild();
    }

    /**
     * Whether this session can safely judge what the diagram no longer has (DR-B10).
     *
     * The decision "a page that is not loaded must not be judged" was enforced by four separate
     * mechanisms - this one, the store's held entries, `captureFromLayout`'s pagesInPlay, and
     * `forgetArrivalsThatNoLongerExist`'s station-index membership test - all individually correct and
     * none of them named. A fifth was always coming: every pruner added to this class needs it.
     *
     * Two ways the picture can be incomplete, and they are different faults with the same remedy:
     *
     *  - a page's file did not load, so its settings look exactly like settings for track that has
     *    been deleted. `CS2File` skips a page whose file will not parse or is not there, and this
     *    layout lives in OneDrive, where an unhydrated placeholder is an ordinary Tuesday;
     *  - the numbering is suspect, meaning a renumber has happened and nothing has re-keyed the setup
     *    yet - so every entry is name-keyed to the WRONG page and "this square does not exist" is
     *    being asked about coordinates that were never on that page.
     *
     * Either way the remedy is the same and it is the one OB-068 established: save, but do not prune.
     *
     * @return true when everything the setup knows about is here and correctly numbered
     */
    public boolean pagesSafeToJudge()
    {
        java.util.Set<String> loadedNames = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // A STAND-IN FOR A PAGE THAT WOULD NOT READ IS NOT A LOADED PAGE (FV3-A1).
            //
            // `NSV-B3` put a blank placeholder in the list so that a link tile after it still resolves
            // to the right page.  It carries the missing page's NAME, and this set is compared by name -
            // so the page stopped being reported as absent, `pagesNotLoaded` came back empty, and this
            // method started answering true about a page whose contents nobody can see.
            //
            // What follows from that is the whole of MT-135: `save()` reconciles against what is on the
            // page, the placeholder holds one text tile, and every station, point name, length, facing,
            // signal pairing, caption and placement on the real page is dropped and written out.
            // Measured: the setting survives when the page is genuinely missing and when it is genuinely
            // there, and is destroyed only in between.
            //
            // The page still stands in the list, so the link fix keeps everything it gained.  It simply
            // stops claiming to be loaded, which is what it never was.
            if (page.isUnreadable()) continue;

            loadedNames.add(page.getName());
        }

        return !store.isPageNumberingSuspect() && store.pagesNotLoaded(loadedNames).isEmpty();
    }

    /**
     * Writes the setup out, and forgets what the diagram no longer has - UNLESS it cannot tell.
     *
     * Reconciled at save rather than at load, so a diagram edited between sessions is tidied at the
     * moment somebody is present to be told about it.
     *
     * A page that did not load, or numbering caught mid-renumber, means the diagram cannot say what it
     * no longer has - everything on the page that is missing looks deleted.  Nothing is pruned in that
     * state, and the Reconciliation returned reports a decline instead (DR-B10).  The two sentences
     * above were the whole of this javadoc, and read as though pruning always happens; that summary is
     * what a reader trusts without reading the sixty lines of reasoning below it (RC-C4).
     *
     * @return what reconciling found, for showing
     * @throws IOException
     */
    public AutonomyCompanionStore.Reconciliation save() throws IOException
    {
        // Every tile of EVERY page, including the excluded ones.  The graph omits excluded pages by
        // construction, so reconciling against it made every setting on such a page look like it
        // belonged to a deleted tile - and saving then destroyed the lot, permanently, with
        // re-including the page giving nothing back.  Excluding a page must be reversible.
        Set<TileKey> existing = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component != null)
                {
                    existing.add(new TileKey(page.getName(), component.getX(), component.getY()));
                }
            }
        }

        // Not while the numbering is suspect.
        //
        // The THIRD time this method has reconciled against a page set that did not represent the
        // truth, and the same fix as the other two: wait.  A renumber leaves every entry name-keyed to
        // the wrong page, so their coordinates are missing from the page they were attached to and
        // dropMissing reads that as a deleted tile.  Adam lost 19 point names and 14 stations to this
        // on 2026-08-23 - pruned by the save that was meant to tidy the diagram, and reported as a
        // routine list of squares that no longer exist.
        //
        // readShared had already detected it and AutonomyViewerPanel had already warned about it.  The
        // warning was all there was: nothing re-keys, and the save below then rewrote "pages" from the
        // current index, which is the only evidence a renumber ever happened.  So the warning could
        // fire once, and afterwards the setup looked consistent for ever while meaning something else.
        //
        // Saving still happens.  It is only the DELETING that waits - until the numbering is settled,
        // which is the point at which "this square does not exist" is a fact again.
        // A page this setup knows about that is NOT among the pages above is the same problem wearing
        // different clothes, and it took a review to see it (OB-068).
        //
        // CS2File skips a page whose file will not parse or is missing, deliberately and quietly - on a
        // layout in OneDrive an unhydrated placeholder is enough. The session then opens without it,
        // and `existing` is built only from the pages that DID load. So every name, station, direction,
        // length, signal pairing and caption on the missing page reads as track that has been deleted,
        // and is pruned and written - and the next page operation drops that page from gleisbild.cs2
        // too, orphaning its file.
        //
        // Nothing about that is a user's doing, and three of the four doors that reach this save
        // discard the report, so it happens in silence.
        java.util.Set<String> loadedNames = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // THE SAME SKIP AS `pagesSafeToJudge` AND `open`, AND IT HAS TO BE HERE TOO (FV3-A3).
            //
            // This is the third copy of this loop, and the first repair for FV3-A1 reached the other
            // two.  The consequence of missing this one is worse than missing all three, because the
            // two halves then disagree: `incomplete` comes back true from pagesSafeToJudge, so the
            // prune is correctly declined - but `absent` comes back EMPTY, because the placeholder
            // still supplies its name here, and `Reconciliation.declined(absent)` reports
            // `wasDeclined()` as false when the list is empty.
            //
            // So the save refuses to judge the page, which is right, and then tells every caller it
            // did nothing of the sort.  Three of the four doors that reach this save discard the
            // report, so the refusal would be silent in exactly the case the report exists for.
            if (page.isUnreadable()) continue;

            loadedNames.add(page.getName());
        }

        java.util.List<String> absent = store.pagesNotLoaded(loadedNames);

        boolean incomplete = !pagesSafeToJudge();

        // Not logged from here: this class has no logger, deliberately - it is the model half of the
        // setup and every message about it belongs to a window. Callers are told through the
        // Reconciliation this returns, which now carries the names (DR-B10); the important half is
        // that nothing is destroyed while the answer is non-empty.

        AutonomyCompanionStore.Reconciliation report = incomplete
            ? AutonomyCompanionStore.Reconciliation.declined(absent)
            : store.reconcile(existing);

        // Pruned only once the guard above has agreed the picture is complete (DR-B10).
        //
        // This ran as the FIRST statement of this method, before `incomplete` was computed at all. It
        // was protected by two accidents rather than by the rule it is built around - held entries
        // never reach the live map, and it skips squares whose station index does not know them - and
        // nothing at the call said so. Every pruner ever added to this class will need this guard, so
        // it is inside it now rather than beside it.
        if (!incomplete) forgetArrivalsThatNoLongerExist();

        store.save();

        dirty = false;

        rebuild();

        return report;
    }

    /**
     * Writes the setup out WITHOUT reconciling it against the diagram.
     *
     * For the paths that save because the diagram is being replaced underneath them, rather than
     * because a user pressed Save.  Reconciling compares the setup against the pages this session
     * holds - and on those paths the pages are the ones an editor mutated in place, which the user may
     * have just discarded with Cancel.  The editor works on the live LayoutDiagram objects and Cancel
     * reverts by re-reading from disk into NEW ones, so the session is left holding the edited
     * version: reconciling against it deleted the names, lengths, directions and arrival restrictions
     * of every square the user had deleted and then thought better of, permanently, with the track
     * itself coming back.
     *
     * Nothing is lost by waiting.  Reconciliation tidies a setup whose diagram has genuinely changed,
     * and the next explicit save does it with pages that are actually current - which is the moment
     * its report can be shown to somebody anyway.
     *
     * @throws IOException
     */
    public void saveWithoutReconciling() throws IOException
    {
        store.save();

        dirty = false;
    }
    /**
     * How to find out how long a train is, when somebody has told us (FR-046).
     *
     * A function rather than a map, because the length lives on the Locomotive in the model and this
     * class has never held one. A copied map would be a second record of something the model already
     * knows, and would be wrong the first time a length was edited.
     *
     * Null until the window installs it, and every check that uses it is silent while it is - a
     * session built without one simply does not raise that warning, rather than raising it about every
     * train on the railway.
     */
    private java.util.function.Function<String, Integer> trainLengths;

    /**
     * Tells this session how to look a train's length up.
     *
     * @param lengths a function from locomotive name to length, or null to stop asking
     */
    public void setTrainLengthSource(java.util.function.Function<String, Integer> lengths)
    {
        this.trainLengths = lengths;
    }

    /**
     * The squares holding a train whose length nobody has set.
     *
     * Empty when no source has been installed, which is the honest answer: not knowing a length and
     * knowing it is unset are different things, and only one of them is worth a warning.
     *
     * @return the squares, in the order the placements are held
     */
    private java.util.Set<TileKey> placedTrainsWithoutLength()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (trainLengths == null) return out;

        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            if (placed.getValue() == null || placed.getValue().trim().isEmpty()) continue;

            Integer length = trainLengths.apply(placed.getValue());

            // Zero and unset mean the same thing here, and both mean the same to the railway: a train
            // of no length is never too long for anywhere.
            if (length == null || length <= 0) out.add(placed.getKey());
        }

        return out;
    }

    /**
     * Whether this railway models lengths at all (REL-C2).
     *
     * Three ways to have started: a measured tile, a station with a maximum, or a placed locomotive
     * with a train length.  Any one of them means somebody is using the length rules, and the notices
     * about the parts they have not filled in are worth having.  None of them means they are not, and
     * a notice on every station would be a list of things that are not wrong.
     *
     * @return true when anything on this railway carries a length
     */
    private boolean modelsAnyLength()
    {
        if (store != null && store.measuresAnyTrack()) return true;

        if (reducer != null && store != null)
        {
            for (TileKey square : reducer.getPoints().keySet())
            {
                // A STATION's (OB-291): a square that is no longer one remembers its maximum and ignores it.
                if (!store.isStation(square)) continue;

                Object value = getPointProperty(square, "maxTrainLength");

                if (value instanceof Number && ((Number) value).intValue() > 0) return true;
            }
        }

        if (trainLengths != null)
        {
            for (String standing : placedLocomotives().values())
            {
                if (standing == null || standing.trim().isEmpty()) continue;

                Integer length = trainLengths.apply(standing);

                if (length != null && length > 0) return true;
            }
        }

        return false;
    }

    /**
     * The station squares that will take a train of any length.
     *
     * Stations only. A maximum on somewhere trains cannot stop decides nothing, which is the same
     * reason the setting itself moved into the station submenu (FR-046).
     *
     * **Gated on the railway modelling lengths at all** (SVN-C3, corrected by REL-C2).  Adam set that
     * condition for `reversalsWithoutLength` - *"a railway that measures nothing has decided not to
     * model lengths"* - and this check, six days older, never got it: every station on a layout that
     * models no lengths was listed, thirty of them on his own railway, on the list this file's javadoc
     * twice says is made useless by ordinary things standing beside real problems.
     *
     * **But not on TRACK lengths, which was the first version of this gate.**  `maxTrainLength` is
     * compared against the locomotive's own authored length - `Point.validateTrainLength` - and no tile
     * length enters into it, so the feature works perfectly on a railway with nothing measured.
     * Gating it on `measuresAnyTrack()` took a precondition that was true where it was written and
     * dropped its subject on the way over: somebody who authors train lengths and station maxima and
     * has never measured track got no notices at all, about the feature they are actively using.
     *
     * So the question is "does this railway model lengths": any track measured, any station with a
     * maximum, or any locomotive with a train length.
     *
     * @return the squares, empty when the layout models no lengths anywhere
     */

    private java.util.Set<TileKey> stationsWithoutMaxLength()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null || store == null) return out;

        if (!modelsAnyLength()) return out;

        for (TileKey square : reducer.getPoints().keySet())
        {
            if (hasNoMaximumTrainLength(square)) out.add(square);
        }

        return out;
    }

    /**
     * Stations whose approach is measured in part and not in whole - the state that closes a berth to every train.
     *
     * Adam, 2026-09-19, shown RTX-C2: *"as long as lengths are specified on the berth, it will work, right?  We want
     * clear warnings to the user if so, then it's fine."*  It does work: with the whole approach measured, the berth
     * walk spends real lengths and admits a train that fits.  Half measured is the trap.
     *
     * **Why half measured refuses everything.**  `Layout.whyABerthCannotHoldIt` declines to judge only when NOTHING
     * is measured, the berth's own square included (PRW-B1, and since OB-278 the berth's square counts).  One measured
     * square is enough to make it
     * judge, and it then walks backwards CLAIMING each place before spending the train's length on it - so an
     * unmeasured square, worth 0, is claimed for nothing and the train still has its whole length left when the walk
     * runs out of places.  A one-unit train is refused as surely as a nine-unit one, and the refusal quotes the road
     * rather than the hole in the measurements.  It is the refusing direction of a ruled rounding, so it is right;
     * it is just not something anybody would guess at from the outside.
     *
     * **Which state is ordinary.**  Mass Assign Lengths gives every switch on a page one length in a step of its
     * own, and MT-454's steps reach it by skipping pieces - so "switches measured, some pieces still at 0" is what
     * the tool leaves behind between sittings.  That is exactly this.
     *
     * Asked of the legs that ARRIVE at each station, which is what the berth walk reads.
     *
     * @return the station squares, each mapped to how many squares of its approach still have no length
     */
    public java.util.Map<TileKey, Integer> stationsWithAHalfMeasuredApproach()
    {
        java.util.Map<TileKey, Integer> out = new LinkedHashMap<>();

        if (reducer == null || store == null) return out;

        for (TileKey square : reducer.getPoints().keySet())
        {
            if (!store.isStation(square)) continue;

            // THE RULE EXEMPTS A STATION AUTONOMY CHOOSES (OP2-B2).  `whyABerthCannotHoldIt` returns at once for a
            // berth that `isAutoDestination` - Adam's ruling, and the default for a station - so a warning about
            // ordinary platforms would be about a refusal that never happens there.  What is left is the parking
            // berths, which is what the rule is for.
            if (isAutoDestination(square)) continue;

            // THE LONGEST TRAIN IT TAKES, where it has one (OB-288).
            Object stated = getPointProperty(square, "maxTrainLength");

            int longest = stated instanceof Number ? ((Number) stated).intValue() : 0;

            // ONE APPROACH AT A TIME (OP2-B3).  The walk judges the approach a train arrives on, so a station with
            // one approach measured throughout and another with nothing measured is not half measured on either -
            // pooling them said it was, and counted squares from legs that are not in the same walk.
            int worst = 0;

            for (GraphReducer.ReducedEdge arriving : reducer.getEdges())
            {
                if (!arriving.getEnd().equals(square)) continue;

                // NOT A SIDE NO TRAIN ARRIVES BY (MT-552; Adam, 2026-09-24, of RampDown and BottomMainPost: *"they only
                // accept arrivals from one side"*).  A barred side is an approach no train uses, so nothing about its
                // track can refuse one - and the walk the rule stands for never starts from it.
                if (getBarredArrivals(square).contains(arriving.getEntrySide())) continue;

                boolean anyMeasured = false;
                int unmeasured = 0;

                // THE PATH AND THE BERTH ITSELF.  The path is the squares between the leg's two ends, endpoints
                // excluded: the start is where a train would be coming FROM, and the end - the berth - is rail the walk
                // spends first (OB-278), which `Layout.whyABerthCannotHoldIt` counts among the measured places.  Left
                // out, a berth measured with nothing behind it - judged by the rule, and closed to every longer train
                // - was not warned about, and an unmeasured berth was one square fewer than the rule's count (TDY-B2).
                java.util.List<TileKey> squares = new java.util.ArrayList<>();

                for (GraphReducer.TileStep step : arriving.getPath()) squares.add(step.getTile());

                squares.add(square);

                for (TileKey tile : squares)
                {
                    // Not a route tile, which takes no length (OB-273): counting it made TopR1ParkLong and
                    // TopR1ParkShort read as half measured when every square that takes a length was measured.
                    if (getGraph() != null && takesNoLength(tile)) continue;

                    if (store.getTileLength(tile) > 0) anyMeasured = true;

                    // AN ANSWERED 0 IS NOT MISSING (Adam, 2026-09-23: "stop listing answered zeros as missing").
                    else if (!store.isTileLengthAnswered(tile)) unmeasured++;
                }

                // NOT WHERE THE BERTH'S MEASURED TRACK BEFORE THE SWITCH HOLDS ITS LONGEST TRAIN (OB-288; corrected on
                // MT-552).  `Layout.whyABerthCannotHoldIt` spends a train from the berth backwards, its own square first
                // (OB-278), passes a square with no length for nothing, and refuses the train only when its spending
                // reaches track another road runs over - the switch.  So a berth whose measured track before the switch
                // holds its longest train refuses none for the holes in it, and is not warned about.  Counted the walk's
                // way: a square that takes no length is passed over, a square with no length - answered 0 or not answered
                // at all - is worth nothing and passed, and the switch ends the count.  The first version of this ended it
                // at the first unanswered square, which the walk does not: Adam, 2026-09-24, of BottomMainPost - 1 on its
                // own square, nothing on 22,7, 1 each on 22,8 and 22,9 - *"trains of length 3 can hold there"*.  With no
                // longest train set, any train could reach the switch.
                if (longest > 0 && anyMeasured && unmeasured > 0)
                {
                    int held = 0;

                    for (int at = squares.size() - 1; at >= 0; at--)
                    {
                        TileKey tile = squares.get(at);

                        if (getGraph() != null && takesNoLength(tile)) continue;

                        if (getGraph() != null && isSwitchSquare(tile)) break;

                        held += Math.max(0, store.getTileLength(tile));
                    }

                    if (held >= longest) continue;
                }

                if (anyMeasured && unmeasured > worst) worst = unmeasured;
            }

            if (worst > 0) out.put(square, worst);
        }

        return out;
    }

    /**
     * Stations told they hold more train than their track measures (Adam, 2026-09-11).
     *
     * *"Let's add an autonomy editor notice that alerts the user if a run-in is shorter than the berth
     * length, that way they can decide if it makes sense or not.  for example, a station of length 4 may
     * have two segments of tracks on either side of a switch, of length 2.  that is acceptable and its
     * limitations are understood."*
     *
     * **The measured room is the guard's own number, not a second opinion.**  It is
     * `ReducedEdge.getRoomAtTheEnd()` - the track from the last switch on the arriving edge to the
     * platform, which is exactly what `Layout.measuredRoomAtTheEndOf` counts when that edge crosses a
     * switch.  A notice quoting a number the refusal would not quote is worse than no notice: the reader
     * measures the wrong stretch.
     *
     * **An edge crossing no switch is skipped unless the train turns at its far end.**  There the guard
     * carries on backwards through earlier edges, so this edge alone bounds nothing and any number taken
     * from it would be too small - except where the edge begins at a square trains turn round on, which
     * is where the walk stops under his other ruling of the same day.  `unmeasuredAfterTheLastSwitch`
     * makes the same choice and for the same reason, written out in its javadoc: asking about a whole
     * route is a nag rather than a notice.
     *
     * **Silent where either side is missing**, which is three separate cases and all three are somebody
     * else's notice or nobody's business: no stated maximum is `NO_MAX_TRAIN_LENGTH`, an unmeasured
     * stretch is `REVERSAL_NEEDS_LENGTH` where it matters most, and a railway that measures no track at
     * all has decided not to model lengths - Adam's own condition, *"a railway that measures nothing has
     * decided not to model them"*.
     *
     * @return the station squares, each mapped to {the stated maximum, the smallest measured room}
     */
    java.util.Map<TileKey, int[]> runInsShorterThanTheBerth()
    {
        java.util.Map<TileKey, int[]> out = new LinkedHashMap<>();

        if (reducer == null || store == null) return out;

        if (!store.measuresAnyTrack()) return out;

        for (TileKey square : reducer.getPoints().keySet())
        {
            if (!store.isStation(square)) continue;

            Object value = getPointProperty(square, "maxTrainLength");

            int max = value instanceof Number ? ((Number) value).intValue() : 0;

            if (max <= 0) continue;

            int worst = -1;

            for (GraphReducer.ReducedEdge arriving : reducer.getEdges())
            {
                if (!arriving.getEnd().equals(square)) continue;

                // NOT A SIDE NO TRAIN ARRIVES BY (MT-552; Adam, 2026-09-24, of RampDown and BottomMainPost: *"they only
                // accept arrivals from one side"*).  A barred side is an approach no train uses, so nothing about its
                // track can refuse one - and the walk the rule stands for never starts from it.
                if (getBarredArrivals(square).contains(arriving.getEntrySide())) continue;

                int room = arriving.getRoomAtTheEnd();

                if (room == Integer.MIN_VALUE)
                {
                    // No switch on this edge, so the guard keeps walking back - unless the train turns
                    // round where this edge starts, which is where it stops instead.
                    if (!isTurnAround(arriving.getStart())) continue;

                    room = arriving.getLength();
                }

                // UNMEASURED IS UNKNOWN, NOT SHORT - the doctrine the guard itself follows, and `-1` is
                // the reduction's word for "bounded, and nothing in it is measured".
                if (room <= 0 || room >= max) continue;

                if (worst < 0 || room < worst) worst = room;
            }

            if (worst >= 0) out.put(square, new int[] {max, worst});
        }

        return out;
    }

    /**
     * Stations every train must turn round at, reached from more than one side (MT-361).
     *
     * Adam, 2026-09-12: *"terminuses (stations that must reverse) are currently allowed to have ingress
     * from two sides.  Make this be an autonomy ERROR that the user has to fix."*
     *
     * The three conditions, and each of them keeps the rule off a railway that is correct:
     *
     *   - **must turn, not may.**  A may-turn square with two ways in is the ordinary station a train
     *     can either run through or reverse in, which is what "may" is FOR.
     *   - **a station.**  A must-turn square that is not one is emitted as a plain reversing point, and
     *     a reversing point with two ways in is a mid-layout turn-round - not a terminus at all.
     *   - **unbarred sides.**  Shutting one side with the arrows is one of the three remedies the
     *     message names, so a side already barred must not go on being counted.
     *
     * Counted as SIDES rather than as arriving edges: two edges can reach one square by the same side.
     * (This also said an arrival through a portal has no side on the grid; it arrives by a real one - AMG-C2.)
     *
     * @return the squares, mapped to how many unbarred sides reach them, empty when none do
     */
    public java.util.Map<TileKey, Integer> terminiWithTwoWaysIn()
    {
        java.util.Map<TileKey, Integer> out = new LinkedHashMap<>();

        if (reducer == null || store == null) return out;

        java.util.Map<TileKey, java.util.Set<Side>> barred = barredArrivals();

        for (TileKey tile : mandatoryTurnTiles())
        {
            if (!store.isStation(tile)) continue;

            java.util.Set<Side> sides = new LinkedHashSet<>();

            for (GraphReducer.ReducedEdge arriving : reducer.getEdges())
            {
                if (!arriving.getEnd().equals(tile)) continue;

                Side entry = arriving.getEntrySide();

                if (entry == null) continue;

                java.util.Set<Side> shut = barred.get(tile);

                if (shut != null && shut.contains(entry)) continue;

                sides.add(entry);
            }

            if (sides.size() > 1) out.put(tile, sides.size());
        }

        return out;
    }

    /**
     * Included pages that repeat an s88 another included page already uses (OB-150).
     *
     * The same walk `excludeRepeatedSensorPages` does, and deliberately so: that method decides which
     * pages to shut when a configuration is created, and this reports the pages that are open and
     * should not be. Two spellings of "repeats a sensor" would eventually disagree about which page
     * was at fault, and the answer would differ depending on whether you created the configuration or
     * switched a page back on afterwards.
     *
     * Earliest page wins, as it does there: the first page to use a sensor keeps it, and a later page
     * repeating it is the one named. That is the rule the shutting already follows, so the error names
     * the page the automatic answer would have shut.
     *
     * @return the offending page names, in the order the layout lists them
     */
    private Map<TileKey, String> repeatedSensorPages()
    {
        // KEYED BY THE SQUARE, so the finding can take the user to it (MT-223).  This was a list of
        // sentences and nothing else, which was enough while the message only named a page.
        Map<TileKey, String> repeats = new LinkedHashMap<>();

        if (pages == null) return repeats;

        // WHICH sensor, and where it was first seen (MT-223).  Adam: "the error about a duplicate
        // s88 does not specify which one is the duplicate."  The addresses were known here and thrown
        // away, leaving a page name and a hunt through forty sensors.
        Map<Integer, String> seen = new LinkedHashMap<>();

        for (LayoutDiagram page : pages)
        {
            if (store.getExcludedPages().contains(page.getName())) continue;

            Map<Integer, String> here = new LinkedHashMap<>();

            boolean repeated = false;

            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || !component.isFeedback()) continue;

                int sensor = component.getRawAddress();

                if (sensor <= 0) continue;

                if (seen.containsKey(sensor))
                {
                    // One per repeated sensor: three repeats on one page are three things to fix, and
                    // rolled into one line, fixing two of them changes nothing about the message.
                    // The square on the REPEATING page - the copy the user is being asked to do
                    // something about.  The one that had the address first is innocent, and is named
                    // in the message rather than jumped to.
                    repeats.put(new TileKey(page.getName(), component.getX(), component.getY()),
                        I18n.f("autosetup.ui.duplicateSensorSubject",
                            sensor, page.getName(), seen.get(sensor)));

                    // AND THE PAGE STOPS COUNTING, which is what excludeRepeatedSensorPages does and
                    // what this claimed to do (LE2-B8).  There a repeating page is shut and
                    // contributes nothing further; here it went on feeding its OTHER sensors into
                    // `seen`, so a third page sharing one of those was reported too - named against
                    // the very page the automatic rule would have switched off.  That is an ERROR, so
                    // it refused the whole setup over a page nothing was wrong with.
                    // FLAGGED, NOT BROKEN OUT OF (MT-223).
                    //
                    // The flag is the whole of what LE2-B8 asked for: a page that repeats anything
                    // contributes NOTHING to `seen` below, so a third page sharing one of its other
                    // sensors is not blamed against it. Stopping the scan was my own addition and it
                    // cost the rest of the page - Adam's page 3 is a zoomed view of page 1 and clashes
                    // on many sensors, and one was reported.
                    repeated = true;
                }

                here.put(sensor, page.getName());
            }

            // Only a page that is staying in contributes what it holds.
            if (!repeated) seen.putAll(here);
        }

        return repeats;
    }

}
